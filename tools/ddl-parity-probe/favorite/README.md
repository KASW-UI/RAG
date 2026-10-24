# DDL 双侧活体对拍探针 · favorite 域

> **目的**：把「favorite 域的 DDL 是否两侧一致」从**源码级推理**升级为**双侧活体对拍**。
> **状态**：2026-10-25 第七轮落地 ⇒ **EXIT=0**，并**查出 2 条真漂移**（见下）。

---

## 为什么以前没做

| 缺口 | 说明 |
|---|---|
| `tools/difftest/check-ddl-static.py` **没有 favorite 预设** | 它只有 `users` / `task` / `message` **三个**域 ⇒ favorite 的 DDL **从未被逐列对照过** |
| `FavoriteSchemaParityTest` 只**抽查** 3 项 | `target_id` 是 varchar / 时间列是 timestamptz / `favorite_item` 无 `update_time` ⇒ 其余列的类型、长度、可空性、DEFAULT **全都没比过** |
| Go 侧 schema 的**来源**没人说清 | `InitDB` 只跑 GORM `AutoMigrate`；那 4 个 `.sql` **没有任何 Go 代码执行** ⇒ 它们是**运维手工执行**的 ⇒ 目标 schema = **AutoMigrate ∪ 4 个手写 SQL**，且**顺序会影响结果** |

---

## 两侧怎么建 schema

| | Go 侧 | RAG 侧 |
|---|---|---|
| 代码 | **真源** `service/favorite/rpc/internal/model/`（**逐字节复制 + sha256 校验**） | favorite-rpc 的 **真 Flyway** 迁移脚本 `V1..V7` |
| 关键调用 | **真源** `InitDB()`（AutoMigrate + `migrateFavoriteTargetIDToString`） | Spring Boot autoconfig 的 Flyway |
| 库 | 真 PG（`postgres:16-alpine`，容器） | 同一个 PG 的**另一个 database** |
| 依赖版本 | **整体继承真源 `go.mod` / `go.sum`**（gorm v1.31.1 / driver/postgres v1.6.0） | 无（SQL 脚本） |

⇒ 两侧**不共用**同一份代码，但**共用同一份 dump 查询**（`sql/dump.sql` + `sql/names.sql`）
⇒ 否则**查询本身的差异**会伪装成 schema 差异（AGENTS.md §3.3）。

---

## 🔴 两个执行顺序都跑（这是本探针最关键的发现）

| 场景 | 顺序 | 结果 |
|---|---|---|
| **sql-first** | 存量老表 → 运维跑 `001..004` → 应用后起 | ✅ **判定基准**（真源文档 `DELIVERY_ACCEPTANCE.md:7` 指定的顺序） |
| **app-first** | 应用先起（AutoMigrate 建出 `favorite_fact_outbox`）→ 运维后跑 `001..004` | ⚠️ `001` 的 `CREATE TABLE IF NOT EXISTS` 变成 **no-op** |

**app-first 与 sql-first 产出的 schema 不一样**，差两处：

1. `CHECK (aggregate_version IN (1,2))` —— 它在 Go 的 `001` 里是**内联在 CREATE TABLE 里**的
   ⇒ `CREATE TABLE` 变 no-op ⇒ 这条 CHECK **建不出来**。
   ⚠️ **但 `001` 里的部分索引 `favorite_fact_outbox_pending` 是独立语句** ⇒ 它**照样建出来**
   ⇒ （这一点是**实测**纠正的，先前的源码级推理猜错了。）
2. `favorite_fact_outbox` 的**列顺序**不同（AutoMigrate 按 struct 序，`001` 按 DDL 序）。

⇒ **执行顺序是行为的一部分**。RAG 侧与 **sql-first** 一致 ⇒ 与真源文档指定的部署顺序一致。

---

## 判定口径

dump 输出两类行：

| 行 | 内容 | 判定 |
|---|---|---|
| `S\|table\|kind\|payload` | **语义行**：列属性 / 索引语义 / 约束语义 / 触发器语义 / 函数体哈希 / 表 | 两侧必须**逐行一致** ⇒ 不一致 = **真漂移**（exit 1） |
| `N\|table\|kind\|name\|payload` | **命名行**：对象名（payload 是配对键） | 名字不同但语义相同 = **命名层差异** ⇒ 进 OBS 段冻结 |

`expected.txt` 两段：

- `MAIN|<sha256>` —— 两侧**共同**的语义内容指纹 ⇒ 变了就是漂移。
- `OBS\t…` —— **已知且已登记**的差异 ⇒ 断言「差异**仍然存在**且**没变样**」
  ⇒ 哪天差异消失，探针 **exit 1** 逼人重评，而不是让它静默漂移。

---

## 本轮查出的两条真漂移

| # | 漂移 | 机理 | 修法 |
|---|---|---|---|
| **1** | `favorite_fact_outbox.created_at` / `updated_at` 的**时钟源不同** | Go 的 struct 字面量**不设**这两字段，靠 tag `autoCreateTime` / `autoUpdateTime` ⇒ **GORM 在应用层赋值**（Go 进程时钟）；RAG 修前**不绑** ⇒ 落到 `DEFAULT now()`（**PG 服务器时钟**）<br>⇒ 且 `markBlocked` / `markFailed` **根本不刷** `updated_at`，而 Go 的 GORM **每次 UPDATE 都刷** | `insertFact` / `markBlocked` / `markFailed` / `markAccepted` 显式绑应用层时钟 + 新增 `V7` 去掉两列的 `DEFAULT now()` |
| **2** | RAG 侧 `favorite_item` **多一道** `target_revision scope` CHECK | Go 只在 `favorite_legacy_fact_marker` 上有同语义的 CHECK；RAG 的 `V6` 把它**额外下沉**到了 `favorite_item` ⇒ **RAG 更严** | 登记为有意偏离（`DEV-FAV-RPC-007`） |

---

## 用法

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/favorite
bash tools/ddl-parity-probe/favorite/run.sh
echo $?   # 0 = 一致；1 = 有漂移；2 = 环境问题
```

**前置**：Docker（起 `postgres:16-alpine`）、Go toolchain、本地 module cache 有 `gorm`。
**可覆盖**：`GO_REPO=<path>` 指定 Go 真源仓。

> ⚠️ 探针**没有**挂进 `mvn test`（它要 Go toolchain + 真 PG，进 CI 等于给流水线引入新依赖）——
> 与 `errmsg-probe` 同款纪律。
> ⇒ **RAG 侧那一半**由 `FavoriteDdlParityTest`（`mvn test` 内，真 Flyway + 真 PG）守着，
> **两侧共用同一份 `expected.txt`** ⇒ 不会各说一半。

---

## 文件

| 文件 | 作用 |
|---|---|
| `run.sh` | 起 PG → 建三个库 → 跑两侧 → 分类比对 → 与 `expected.txt` 比对 |
| `go/main.go` | Go 侧驱动（**探针代码**，非真源复制件） |
| `go/model/` | **真源 model 包的逐字节复制件**（8 个 `.go` + 4 个 `.sql`，sha256 校验） |
| `sql/dump.sql` | **语义行**查询（两侧共用） |
| `sql/names.sql` | **命名行**查询（两侧共用） |
| `expected.txt` | 冻结期望（MAIN 指纹 + 13 条 OBS） |
| `out/` | 三份原始 dump 留档（`go_sql-first.txt` / `go_app-first.txt` / `rag.txt`） |
