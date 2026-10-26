# SQL 语句层 · 双侧活体对拍探针（favorite 域）

> 补上 favorite 域**四层验证**里的最后一层。
>
> | 层 | 覆盖什么 | 工具 |
> |---|---|---|
> | ① DDL 层 | 表结构 / 类型 / 约束 / 索引 | `tools/ddl-parity-probe/favorite/` |
> | ② 错误码契约层 | 码值 / 文案 / 未知码 | `tools/errmsg-probe/` |
> | ③ 事务语义层 | 三笔事务写「同生共死」 | `FavoriteFactTransactionTest` + 第二轮落库等价性 |
> | ④ **SQL 语句层**（本探针） | **锁 / 事务边界 / WHERE / 写集合 / 投影 / 排序** | 本目录 |

## 为什么还需要第 ④ 层

**落库等价性只证伪「结果一样」，证不了「过程一样」。**

少一句 `FOR UPDATE`、多一次全表扫、事务边界切得不一样、INSERT 少绑一列 ——
这些在**单次运行**里都可能看不出差别，却会在**并发**下变成死锁或脏写，
或者（本次实测到的）让**对外响应**里的时间戳变成 0。

## 取证方式：PG 服务端日志（**零侵入**）

| 候选 | 为什么不用 |
|---|---|
| Go 侧 GORM logger | 真源**冻结**，不能改它的配置 |
| RAG 侧 MyBatis 日志级别 | 改它就**改变了被测面** |
| **PG 服务端日志** | ✅ 两侧都**一行不改**，且记的是**真发出去的那条 SQL** |

附带好处：`BEGIN` / `COMMIT` / `ROLLBACK` 与语句在**同一条时间线上** ⇒ 事务边界可判。

### 两侧怎么分流

| 侧 | 手段 | 出处 |
|---|---|---|
| Go | `PGAPPNAME=go_side` 环境变量 | 真源 DSN 硬编码在 `internal/model/db.go:21-28`，**没有** `application_name` 字段；pgx v5 把 `PGAPPNAME` 映射成 `application_name`（`pgconn/config.go:456`） |
| RAG | JDBC URL 挂 `ApplicationName=rag_side` | pgjdbc 连接属性 |
| 驱动自己 | 独立连接 `ApplicationName=probe_marker` | 发 marker 用 |

⚠️ `PGAPPNAME` **只加在启动 Go server 的那一条命令上** ——
export 到整个脚本的话，psql 灌种子 / schemaprep 建表也会被标成 `go_side` ⇒ 污染 Go 桶。

### 怎么按用例切段

每个用例调用前，驱动用一条**独立连接**执行：

```sql
SELECT 'SQLMARK::<side>::<case-id>::<seq>'
```

服务端日志里出现这条可识别语句 ⇒ 解析时按 marker 切段 ⇒ **逐用例**比对两侧的语句序列。

## 实测记录：PG 16 的 `log_statement=all` 到底记什么

**全部来自实测，不是「PG 大概会这么记」。**

| 协议 | 日志形态 | 证据 |
|---|---|---|
| simple（psql / 客户端插值） | `LOG:  statement: <sql>` | psql 执行 `INSERT` |
| extended（pgx / pgjdbc） | `LOG:  execute <名字>: <sql>` | pgx 连跑 3 次 ⇒ **3 行** execute |
| extended 的参数值 | **独立**一行 `DETAIL:  parameters: $1 = '9001'` | 同上 |
| **parse / bind** | **不存在**（PG 16 实测 0 条） | ⇒ 一条语句恰好一行 ⇒ **不会重复计数** |
| 多行 SQL | **跨行**（原样保留换行） | `SELECT a,\n b\n FROM t` 实测跨 4 行 |

⇒ 解析规则：**带时间戳前缀的行 = 一条新记录；无前缀的行 = 上一条的续行**。

## 比对的是「语义签名」，不是 SQL 文本

GORM 与手写 SQL 的生成差异是**库级噪声**（占位符风格、`SELECT *` vs 显式列、
GORM 给 `First` 偷偷加的 `ORDER BY <pk>`、GORM 的 `RETURNING "id"`）⇒ 逐条归一到：

```
(语句类别, 主表, 写入列集, WHERE 列集, 锁子句, ORDER BY, LIMIT/OFFSET, JOIN 表集, 投影)
```

差异分三档：

| 档 | 含义 |
|---|---|
| `SEMANTIC` | **语义差异，必须人工核证**（可能是真缺陷） |
| `ORM-GENERIC` | 库级生成差异（已知、可解释、不改） |
| `EQUIVALENT` | 形式不同但**已证明等价**（如 WHERE 钉住唯一键时的 `ORDER BY <pk>`） |

### 三类必须剥离的噪声（**两侧对称**）

1. `-- …` —— GORM 的连接 ping
2. `SHOW …` —— pgjdbc 的 `SHOW TRANSACTION ISOLATION LEVEL`
3. `SET application_name = …` —— pgjdbc 的连接参数回显

外加：**只包一条语句的 `BEGIN/COMMIT`** —— GORM 默认给 Create/Update/Delete 套一层事务
（`SkipDefaultTransaction` 默认 false）⇒ 单语句事务与自动提交**原子性等价** ⇒ 归 `ORM-GENERIC`。
⚠️ **包了两条以上的事务不能剥** —— 那才是真正的原子性边界。

## 用法

```bash
bash tools/sql-statement-probe/favorite/run.sh
GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/sql-statement-probe/favorite/run.sh
KEEP_TMP=1 bash tools/sql-statement-probe/favorite/run.sh   # 保留 pg.log / mvn.log
```

退出码：`0` = 两侧语句层语义一致；`1` = 有**语义**差异或 L1 响应不同；`2` = 环境问题 / 真源漂移。

产物：

- `out/report.md` —— 逐用例对照 + 差异明细 + 原始 SQL 附录
- `out/extracted.json` —— 抽取出的逐用例语句序列（可离线复算）

## 比对器的自证（`--mutation-test`）

```bash
python3 tools/sql-statement-probe/favorite/compare.py --mutation-test out/extracted.json
```

| 变异 | 期望 |
|---|---|
| 抹掉一侧的 `FOR UPDATE` | 必须报 `lock` 维度的语义差异 |
| 抹掉一侧 INSERT 的一列 | 必须报 `write_cols` 维度 |
| 给一侧多加一句 | 必须报 `statement_count` |
| 改一侧 WHERE 的列名 | 必须报 `where_cols` 维度 |
| 🔴 **把 `SELECT *` 换成等价显式列清单** | **不得**报语义差异（阴性对照：比对器不能「见差异就报」） |

## 复用件

本探针**不复制**一份真源文件，而是复用同域 `tools/grpc-contract-probe/favorite/go/` 里
**已逐字节校验**的复制件（含探针专属的 `schemaprep` / `upstreamstub` 两个 cmd），
并在每次运行时**重新做 sha256 校验** ⇒ 真源漂移会被当场抓住。

⚠️ 本探针**不新增**任何 Go 代码 ⇒ 复制件清单与那个探针**完全一致**。
