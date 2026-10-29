# `users-common` 域 **库面**双侧活体对拍探针

> **落点**：`tools/api-full-parity-probe/users-common/`
> **补的是** `docs/PhaseGoal-judge.md` §九 **G-4** ——「`users-common` 的 176 条双侧活体证据在
> `/tmp/commonprobe/**`，**已消失** ⇒ 不可复算」。
> ⇒ 本目录把那批证据**落进仓库**，让 `users-common` 的 R 档从「**声明**」变成「**可复算**」。

## 目的

`service/users/users-common` 是 **users 域的共享库**（被 user-rpc / admin-rpc / user-api /
admin-api **四方共用**，且 `BearerTokenExtractor` 被 **8 个外域 api** 引用 ⇒ 第二个共享层，
AGENTS.md §5.3f）。它同时是**错误码表 / 请求绑定规则 / JWT 编解码 / 建表执行器**的唯一基准。

台账 `docs/DRIFT/users/common.md` §0 声称「176 条，**全部双侧活体实测**」，
但支撑它的探针在 `/tmp/commonprobe/**` ⇒ **今天既跑不出来、也没了载体**
⇒ 按 AGENTS.md §3.3 的口径，那是「**声明的结论，不是可复算的**」。

本探针让**两侧同时跑**（Go 真源进程 ＋ JVM 进程）⇒ 同一批输入 ⇒ **逐字节**比对，
并把期望值冻结在 `expected.txt` ⇒ 每次跑都必须同时满足 **Go == RAG == expected.txt**。

## 用法

```bash
bash tools/api-full-parity-probe/users-common/run.sh          # EXIT=0 = 双侧逐字节一致
bash tools/api-full-parity-probe/users-common/mutate-parity.sh # 变异验证（M1~M4 ⇒ 必须精确转红）
```

退出码：`0` = 逐字节一致 · `1` = 有差异 · `2` = 环境问题 / 真源漂移 / 依赖缺失。

可用 env 覆盖：`GO_REPO`（真源路径，默认 `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`）·
`PROBE_PG_MODE`（`auto` / `docker` / `local`）· `LOCAL_PG_*`（宿主 PG）· `GO_PG_PORT` / `RAG_PG_PORT`。

## 两侧各跑什么（**库面**，不是某个 api）

| 组 | Go 真源（**只读**） | RAG（**真实类**，不重写） | 条数 |
|---|---|---|---|
| `errmsg` | `service/user/common/errmsg/errmsg.go` | `ErrorCode` | 39 |
| `jwt` 形状 | `service/user/common/jwt/jwt.go` | `JwtUtil` | 11 密钥档 × 6 |
| `jwt` 规范化签名 | 同上 | 同上 | 11 |
| `jwt` **交叉验签** | golang-jwt v4（真源链路） | jjwt（**真 api 侧路径**） | 6 档 × 2 向 = 12 |
| `cryptx` | `service/user/common/cryptx/password.go` | `PasswordUtil` | 14 + 1 形状 + 1 交叉 |
| `bearer` 两套取法 | golang-jwt `request/oauth2.go` + `strings.TrimPrefix` | `BearerTokenExtractor` | 11 × 2 = 22 |
| `,string` int64 | go-zero `core/mapping` + `core/jsonx` | `GoStyleStringInt64` + `GoStyleRequired` | 17 |
| 必填校验 | go-zero `core/mapping` | `GoStyleRequired` | 4 |
| **DDL 逐列** | **GORM `AutoMigrate`**（真源 `internal/model`） | **`SchemaInitializer.applyAll`**（四份脚本） | 33 列 + 表清单 1 |
| **播种读回** | GORM `Create` / `First` | JDBC 读回 | 21 |

> ⚠️ **`users-common` 是个库 ⇒ 探针打的是「库面」**：
> `errmsg` / `jwt` / `cryptx` 三包（Go 侧 `service/user/common/**` 的**全部** 3 个文件）
> ＋ Go 侧**没有同名包**的两个超集（`http/*` 的对照面在 go-zero 库里、`ddl/*` 的对照面在 GORM 里）。

## 真源冻结 ⇒ 只改「副本」

Go 侧用的是**真源真正在用的包**（`service/user/common/**`、`internal/model`）；
但探针源码**不能写进真源仓** ⇒ 用 `go build -overlay=<json>` 把它**虚拟挂进**真源模块树：

| 探针 | 虚拟挂载点 |
|---|---|
| `go/main.go` | `<GO_REPO>/service/user/user/rpc/cmd/userscommonprobe/main.go` |
| `go/admin_main.go` | `<GO_REPO>/service/user/admin/rpc/cmd/userscommonprobe/main.go` |

> 🔴 **为什么必须挂在 `rpc/` 之下**：Go 的 `internal` 规则按**导入路径**判定 ⇒
> 只有 `service/user/{user,admin}/rpc/...` 下的包才 import 得到 `internal/model`
> ⇒ 用户表与 admin 表**必须两个探针**（`internal` 前缀不同，import 路径互斥）。

> 🔴 **脚本第 0/1/7 步各断言一次**：`git status --porcelain -- service/user` 必须为空
> ⇒ **冻结的真源一个字都没被改**（跑前 / 构建后 / 跑后）。

## 两侧各自的 Postgres（不互相污染）

| 侧 | 库 | 表从哪来 |
|---|---|---|
| Go | 一个**独立库** | **真源的 GORM `AutoMigrate`**（`model.InitDB`） |
| RAG | 另一个**独立库** | **它自己的 `SchemaInitializer.applyAll`**（四份 DDL 脚本） |

> `PROBE_PG_MODE=auto` ⇒ docker 可用就用**专属容器**（前缀 `r2uc`、端口段 **18111-18115**）；
> docker 不可用则回落**宿主本地 PG**（建两个独立库 `r2uc_go_probe` / `r2uc_rag_probe`）。
> ⇒ **两侧都播种同一行**（同一 uid / username / email / bcrypt hash / status / score / extra_info）
> ⇒ **不是「两边都空」那种无信息量的对拍**。

## `expected.txt` 冻结的是「三条腿」

```
CASE|<case_id>|<side>|<value>          side ∈ BOTH / GO / RAG
REG|<case_id>|<go_value>|<rag_value>    已登记的两侧不同（两侧值都被钉住）
```

`compare.py` 的判定**故意很严**：

1. `CASE|BOTH` ⇒ 两侧的值**都**必须等于冻结值；
2. `CASE|GO` / `CASE|RAG` ⇒ 只有该侧产出，且值必须相等；
3. `REG` ⇒ 两侧的值分别等于各自的冻结值（**允许两侧不同**，但**两侧都要被钉住**）；
4. 探针产出但 `expected.txt` 没登记的 id ⇒ **失败**（防「新 case 悄悄溜过去」）；
5. `expected.txt` 登记了但探针没产出的 id ⇒ **失败**（防「期望值与实现脱钩」）。

## 实测结果（2026-10-29 · `align/r2-users-common`）

```
两侧逐字节相同的 case : 218
单侧专属的 case       : 25
已登记的两侧不同       : 17
失败                  : 0
✅ Go == RAG == expected.txt（逐字节）
```

**17 条已登记的两侧不同**（每条都对应台账 `docs/DRIFT/users/common.md` 的一条已登记条目）：

| 组 | 条数 | 登记在哪 |
|---|---|---|
| `errmsg.1017` / `1018` 文案 | 2 | `DRIFT-USERSCOMMON-001`（已销号：两侧均不可观察） |
| JWT header / payload **键序** | 2 | `DRIFT-USERSCOMMON-002` / `-003`（判为不修：JSON 键序无语义） |
| bcrypt `$1$` / `$2x$` 前缀 | 2 | `SD-USERSCOMMON-001`（已签字：RAG 保留 fail-closed） |
| **`Bearer `(7 字符) 的剥前缀** | 1 | **`DRIFT-USERSCOMMON-006`（本探针新发现 · 潜伏 · wire 不可观察）** |
| DDL：`users_user.status` 可空性 | 1 | AGENTS.md §0「仍未修」 |
| DDL：`create_time`/`update_time` 的 `DEFAULT CURRENT_TIMESTAMP` | 8 | AGENTS.md §0「刻意保留」/ `DRIFT-USERRPC-V-011` |
| DDL：**表名约定** | 1 | 台账 A8 ④「表名 `users`→`users_user` ✅ 签字」 |

### 🆕 本探针新抓到的一条：`DRIFT-USERSCOMMON-006`

**现象**：`BearerTokenExtractor.tokenForSignatureVerification("Bearer ")` 返回 `"Bearer "`，
Go 侧返回 `""`（剥成空串）。

- Go 出处：`golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:8-14`
  `if len(tok) > 6 && strings.ToUpper(tok[0:7]) == "BEARER " { return tok[7:], nil }`
  ⇒ 判据是 **`len(tok) > 6`**（即 ≥7）⇒ `"Bearer "`（正好 7 字符）**会被剥成空串**。
- RAG 现状：`service/users/users-common/src/main/java/com/rag/users/common/jwt/BearerTokenExtractor.java:74-81`
  `if (authHeader.length() > BEARER_PREFIX.length() && …)` ⇒ `BEARER_PREFIX.length()` = **7**
  ⇒ 判据变成 **`length() > 7`** ⇒ `"Bearer "`（正好 7 字符）**不剥** ⇒ **差一**。
- 🔴 **该类自己的 javadoc（同文件 `:62-71`）写的就是 Go 的语义**（「`Bearer `(7 字符) 会被剥成空串」）
  ⇒ **代码与自己的注释矛盾**。
- **影响面**：8 个外域 api 的验签路径都调它；但 `"Bearer "` 这种畸形头在两侧**都落到 401**
  （RAG：`token` 非空 ⇒ 解析失败 ⇒ 401；Go：token 为空 ⇒ 解析失败 ⇒ 401）
  ⇒ **wire 上不可观察** ⇒ 归为**潜伏差异**（与 `DRIFT-USERSCOMMON-002/003` 同类）。
- **处置**：⏸️ **不改** —— `service/users/users-common/**` 是 **G4 的写权面**（AGENTS.md §5.3f）
  ⇒ 已按「反馈单 7 项」登记并转派 G4。**建议修法**：`length() > BEARER_PREFIX.length()`
  ⇒ `length() >= BEARER_PREFIX.length()`（即 Go 的 `len(tok) > 6`）。

## 探针本身踩过的坑（写下来免得下一个人再踩一遍）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | `sed -e 's|^CASE\|c.errmsg.200\|BOTH\|OK$|...'` → `bad flag in substitute command: 'B'` | 期望值里含 `\|`（字段分隔符）⇒ 与 sed 的分隔符打架 | 改用 `python3` 做定点替换 |
| 2 | Go 侧连库报 `FATAL: database "pr" does not exist`（明明 `--pg-db=r2uc_go_probe`） | **pgconn 的 DSN 解析**：`password=`（**空值**）会把**下一个 `key=value` 整段吃进密码** ⇒ 实测 `password= dbname=x` ⇒ `Database=""`、`Password="dbname=x"` | 密码为空时**填一个占位值**（trust 认证下不影响）。⚠️ **真源自己有同样的隐患**：`service/user/user/rpc/internal/model/db.go:21-27` 无条件写 `password=%s` |
| 3 | Go 侧 import 不到 `service/user/admin/rpc/internal/model` | Go 的 `internal` 规则按**导入路径**判定 ⇒ `user/rpc` 下的包 import 不到 `admin/rpc/internal/...` | 拆成**两个** overlay 探针（`go/main.go` + `go/admin_main.go`） |
| 4 | 两个 Go 探针并发跑 ⇒ GORM 建表竞态 | 真源的 admin `AutoMigrate`（`service/user/admin/rpc/internal/model/db.go:15`）**也含 `&User{}`** ⇒ 会和 user 侧争同一张 `users` 表 | **串行**：user 探针先跑，admin 探针后跑 |
| 5 | `RAG_PIDS[@]: unbound variable` | macOS 自带 **bash 3.2**：`set -u` 下展开**空数组**报错 | 用 `${A[@]+"${A[@]}"}` 形式 |
| 6 | `rm -rf` 被回收站守卫拦下（命令整体被拒） | 本机的 trash guard | 只用 `rm -f` 删单文件；临时目录留给系统清理 |
| 7 | 期望值里塞 `|` ⇒ 解析器按 `\|` 切 | `expected.txt` 用 `\|` 分隔 ⇒ **value 里允许出现 `\|`** ⇒ 用 `split("\|", 3)`（maxsplit）解析 | `compare.py` 用 maxsplit |
| 8 | `c.ddl.col.*` 两边永远不等（序列名不同） | 表名约定不同（`users` vs `users_user`）⇒ 序列名随之不同 | 归一化：`nextval('…'::regclass)` → `nextval(...)`；表名差异单独用 `c.ddl.tables` 一条记录 |
| 9 | `column_default` 的字面量大小写两侧不同（`false` vs `FALSE`） | PG 原样存 DDL 里的字面量 | 归一化时统一小写（只影响大小写，不影响语义） |
| 10 | 交叉验签用固定 `iat` ⇒ 两侧都报「已过期」⇒ 签名根本没被比 | 过期时间戳会**掩盖真正的判据**（`docs/Pitfalls/users/common.md` 记过同一坑） | 交叉验签用 `now` 作 `iat`；只有「原始字节 / 规范化签名」那几条用固定 `iat`（它们不比验签） |
| 11 | docker daemon 没起 ⇒ 探针整个跑不起来 | 本机 docker 随时可能停 | `PROBE_PG_MODE=auto` ⇒ docker 不可用时回落宿主本地 PG（**两个独立库**，等价隔离） |

## 变异验证（`mutate-parity.sh`）

AGENTS.md §3.3.3：新增的每个探针都必须配一个**能真正触发它**的变异，否则无法排除「假绿」。

> 🔴 **本脚本不写共享层源码，也不写真源仓**：
> 「打实现」的变异全部用**影子化**（shadowing）——
> RAG 侧把目标类的**副本**编译到临时目录并放在 classpath **最前面**；
> Go 侧用 `go build -overlay` 把真源的某个文件替换成**变异副本**。
> ⇒ 仓内源码（共享层与真源）**一个字节都没动**（脚本末尾各断言一次 `git status`）。
> ⇒ 连 `expected.txt` 都不改：走 `EXPECTED_FILE` 钩子指向临时副本。

| 变异 | 手法 | 期望转红的断言 |
|---|---|---|
| **M1** | 冻结值 `c.errmsg.200`：`OK` → `Ok`（**改临时副本**，仓内文件不动） | `c.errmsg.200` |
| **M2** | RAG 侧 `JwtUtil` **影子副本**：不再钉死 HS256（复现 `DRIFT-USERSCOMMON-004` 的历史形态） | `c.jwt.alg.48` / `c.jwt.siglen.48` |
| **M3** | Go 侧真源 `jwt.go` 的 **overlay 影子副本**：HS256 → HS384 | `c.jwt.alg.15` |
| **M4** | RAG 侧 `GoStyleStringInt64` **影子副本**：不再接受 JSON 数字字面量 | `c.int64.1` |

⇒ **M1** 证明「冻结值真的在参与判定」（不是「自己跟自己比」）；
⇒ **M2/M3** 证明「真源或 RAG 实现一旦漂移，探针立刻红」；
⇒ **M4** 证明「`,string` int64 的判定有牙齿」（Go 接受 JSON 数字、RAG 也必须接受）。
