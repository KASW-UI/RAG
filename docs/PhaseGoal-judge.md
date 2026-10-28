# 阶段一终点指标（Phase 1 End-State Judge）

> **一句话**：把 `docs/PhasedGoal.md` 与 `docs/twoStage.md` 的**语言描述**落成**可判定的矩阵** ——
> 每个微服务 / 组件**复刻到什么程度**才算阶段一完成，**用什么命令判定**，**今天到哪一步了**。
>
> **核对时间：2026-10-28**（基线 commit `7ec6fc6`）
> **真源**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`（冻结，只读）

---

## 〇、这份文件是什么、不是什么

| 是 | 不是 |
|---|---|
| 阶段一**终点指标**的唯一定义处（组件级 + 域级 + 跨组件三层） | 第三份验收标准 —— **准入线仍以 `docs/PhasedGoal.md` §二 / `docs/twoStage.md` §2.1 为准**（两者逐字相同） |
| 把「合格」翻译成**数字 + 判定命令** | 新的评分体系 —— **评分规则仍以 `docs/twoStage.md` §8 为准**（E0–E5 证据分级） |
| 给 lane 执行者用的**验收清单**（阶段一只做本域） | 已完成清单 —— **进度仍以各域台账为准**（`docs/DRIFT/<域>/<组件>.md`） |

**本文件自身受三条纪律约束**（与 AGENTS.md 一致）：

1. **一条合法指标 = 对象 + 数字 + 判定命令。** 没有命令的句子不是指标，是愿望。
2. **每个断言带核对日期。** 无日期按 `UNKNOWN` 处理（AGENTS.md §6.2）。
3. **「已改」≠「已验证」** ⇒ 复刻度（R，覆盖面）与证据强度（E，可信度）**分开记，不互相推导**。

---

## 一、三套刻度

### 1.1 复刻度 R（覆盖面 —— 「复刻到什么程度」）

| 级 | 名称 | 判据（**全部满足**才够这一级） |
|---|---|---|
| **R0** | 范围外 | Go 侧无对应件 ⇒ 本仓不实现（`knowledge` / `points` / `security` 三域） |
| **R1** | 骨架 | 有端点 / 方法，但业务逻辑是桩、硬编码成功或返回模拟数据 |
| **R2** | **契约面已复刻** | ① wire 契约（HTTP 路径+方法集合 / gRPC 方法集合 / proto 字段号 / DB 列）**逐条对齐**；② **有守门可复算**（脚本或测试能自动判定）；③ 业务分支**未**逐条对照 |
| **R3** | **语义面已复刻** | R2 **＋** ④ Go 侧每个业务决策点有 `文件:行号` 出处；⑤ RAG 侧有**自动化测试断言该行为**；⑥ 台账未决项**已清零** |
| **R4** | **运行面已复刻** | R3 **＋** ⑦ **双侧同时运行**的探针对拍通过（比的是可观察行为，不是「类存在」）；⑧ 有**变异验证**记录（改实现 ⇒ 探针必红） |

> ⚠️ **R2 仍是「看起来对」**，R3 是「读过 Go 源码确认对」，R4 是「两侧同时跑出来一样」。
> 阶段一终点 = **核心组件至少 R3**，**对外契约面至少 R2**（见 §5.2）。

### 1.2 证据强度 E（可信度）—— 直接引用 `docs/twoStage.md` §8.1，**不重定义**

| 级 | 名称 | 含义 |
|---|---|---|
| E0 | 无证据 | 尚未找到实现或尚未完成检查 |
| E1 | 声明证据 | 仅文档 / 注释 / README / 配置声明 |
| E2 | 源码证据 | 找到具体实现，未证明正确 |
| E3 | 测试证据 | 有可定位的测试**及实际执行结果** |
| E4 | 运行证据 | 有实际运行验证 / 指标 / 故障演练结果 |
| E5 | 生产运行证据 | 可追溯的真实运行数据、事故记录、SLO |

> ⚠️ **R 与 E 不可互相推导**：R4 的组件可能只有 E4（无生产环境）；反过来 E3 的组件可能只有 R2（测试只断言契约、不断言语义）。

### 1.3 组件状态

| 状态 | 含义 | 对阶段一的影响 |
|---|---|---|
| ✅ **达标** | 达到该组件的必达终点指标，且复刻度 ≥ R2 | 计入通过 |
| 🟡 **契约达标 / 语义待补** | 契约面已复刻，语义面或运行面未达终点 | 登记为差异项或暂缓项（§5.4） |
| 🔴 **未达标** | 未达终点指标 | **A 类准入阻断项** |
| ⚫ **受阻** | 环境缺前置条件（本机无 Kafka / Redis / Nacos / etcd） | **不得写成「已修」**，也**不得写成「未修」** |
| ⬜ **豁免** | 有明确理由的不适用 | D 类，须写明理由 |

### 1.4 指标书写规则

| 反例（不是指标） | 正例（是指标） |
|---|---|
| 「错误码要和 Go 一致」 | 「错误码码值 + 文案 + 未知码回落**逐字节一致**，判定：`GO_REPO=… bash tools/errmsg-probe/run-parity.sh` ⇒ EXIT=0」 |
| 「测试要覆盖失败路径」 | 「每个端点**至少 1 条**失败路径测试（参数非法 / 权限不足 / 下游不可用），判定：模块级 `mvn test` ⇒ surefire XML 中该类 `failures=0` 且 `tests>0`」 |
| 「要能做双侧对拍」 | 「**两侧同时运行**同一请求矩阵，比响应字节；判定：`bash tools/<probe>/run-parity.sh` ⇒ EXIT=0，且**变异后 EXIT≠0**」 |

---

## 二、矩阵 A：38 个组件（＋ 1 行共享层 `service/common`）

### A-1 定位与规模（39 行 = 38 组件 ＋ 1 共享层 · 2026-10-28 实测）

> `main 文件/LOC` = `src/main/java` 下 `.java` 文件数 / 行数；`tests` = **surefire `TEST-*.xml` 逐 `testsuite` 属性求和**（权威读法；不是 maven 汇总行）。

| 域 | 组件 | Go 原件（真源） | RAG 实现 | main/LOC | tests |
|---|---|---|---|---|---|
| `article` | `article-api` | `service/article/api/internal/handler/routes.go` + `internal/logic/`（7 handler） | `service/article/article-api/`（`controller/ArticleController.java`） | 43 / 5,355 | 99 |
| `article` | `article-rpc` | `service/article/rpc/internal/logic/` + `internal/svc/`（6 rpc） | `service/article/article-rpc/`（`impl/ArticleServiceGrpcImpl.java`） | 39 / 8,683 | 183 |
| `article` | `article-common` | `service/article/common/errmsg/errmsg.go`（45 行） | `service/article/article-common/…/errmsg/ErrorCode.java` | 1 / 257 | 15 |
| `article` | `article-proto` | `proto/article.proto` | `service/article/article-proto/src/main/proto/article.proto` | —（生成） | 0 |
| `comment` | `comment-api` | `service/comment/api/internal/handler/routes.go` + `internal/logic/`（7） | `service/comment/comment-api/`（`controller/CommentController.java`） | 37 / 3,392 | 52 |
| `comment` | `comment-rpc` | `service/comment/rpc/internal/logic/`（7） | `service/comment/comment-rpc/`（`impl/CommentServiceGrpcImpl.java`） | 43 / 8,656 | 138 |
| `comment` | `comment-common` | `service/comment/common/errmsg/errmsg.go` | `service/comment/comment-common/…/errmsg/`（4 类） | 4 / 567 | 27 |
| `comment` | `comment-proto` | `proto/comment.proto`（245 行） | `service/comment/comment-proto/src/main/proto/comment.proto` | —（生成） | 0 |
| `favorite` | `favorite-api` | `service/favorite/api/internal/handler/routes.go` + `internal/logic/`（7） | `service/favorite/favorite-api/`（`controller/`） | 36 / 3,799 | 131 |
| `favorite` | `favorite-rpc` | `service/favorite/rpc/internal/logic/`（7） | `service/favorite/favorite-rpc/`（`impl/FavoriteServiceGrpcImpl.java`） | 36 / 7,950 | 131 |
| `favorite` | `favorite-common` | `service/favorite/common/errmsg.go` | `service/favorite/favorite-common/…/errmsg/`（4 类） | 4 / 875 | 29 |
| `favorite` | `favorite-proto` | `proto/favorite.proto`（107 行） | `service/favorite/favorite-proto/src/main/proto/favorite.proto` | —（生成） | 0 |
| `follow` | `follow-api` | `service/follow/api/internal/handler/routes.go` + `internal/logic/`（8） | `service/follow/follow-api/`（`controller/FollowController.java`） | 31 / 3,511 | 74 |
| `follow` | `follow-rpc` | `service/follow/rpc/internal/logic/`（8） | `service/follow/follow-rpc/`（`impl/FollowServiceGrpcImpl.java`） | 16 / 2,677 | 83 |
| `follow` | `follow-common` | `service/follow/common/errmsg.go` | `service/follow/follow-common/…/errmsg/`（4 类） | 4 / 562 | 37 |
| `follow` | `follow-proto` | `proto/follow.proto`（52 行） | `service/follow/follow-proto/src/main/proto/follow.proto` | —（生成） | 0 |
| `hot` | `hot-api` | `service/hot/api/internal/handler/routes.go`（1） | `service/hot/hot-api/`（`controller/HotArticleController.java`） | 17 / 2,452 | 96 |
| `hot` | `hot-rpc` | `service/hot/rpc/internal/logic/`（5） | `service/hot/hot-rpc/`（`impl/HotServiceGrpcImpl.java`） | 15 / 2,535 | 114 |
| `hot` | `hot-common` | `service/hot/common/errmsg.go`（121 行） | `service/hot/hot-common/…/errmsg/`（2 类） | 2 / 172 | 14 |
| `hot` | `hot-proto` | `proto/hot.proto` | `service/hot/hot-proto/src/main/proto/hot.proto` | —（生成） | 0 |
| `like` | `like-api` | `service/like/api/internal/handler/routes.go` + `internal/logic/`（6） | `service/like/like-api/`（`controller/LikeController.java`） | 37 / 3,390 | 94 |
| `like` | `like-rpc` | `service/like/rpc/internal/logic/`（6） | `service/like/like-rpc/`（`impl/LikeServiceGrpcImpl.java`） | 42 / 6,978 | 129 |
| `like` | `like-common` | `service/like/common/errmsg/errmsg.go` | `service/like/like-common/…/errmsg/`（4 类） | 4 / 515 | 18 |
| `like` | `like-proto` | `proto/like.proto` | `service/like/like-proto/src/main/proto/like.proto` | —（生成） | 0 |
| `message` | `message-api` | `service/message/api/internal/handler/routes.go` + `internal/logic/`（13） | `service/message/message-api/`（`controller/MessageController.java` + `MessageAdminController.java`） | 49 / 4,870 | 107 |
| `message` | `message-rpc` | `service/message/rpc/internal/logic/`（9） | `service/message/message-rpc/`（`impl/MessageRpcServiceImpl.java`） | 19 / 3,815 | 96 |
| `message` | `message-common` | `service/message/common/errmsg/errmsg.go` | `service/message/message-common/…/errmsg/`（3 类） | 3 / 526 | 21 |
| `message` | `message-proto` | `proto/message.proto` | `service/message/message-proto/src/main/proto/message.proto` | —（生成） | 0 |
| `task` | `task-api` | `service/task/api/internal/handler/routes.go`（1） | `service/task/task-api/`（`controller/TaskController.java`） | 11 / 1,496 | 102 |
| `task` | `task-rpc` | `service/task/rpc/internal/logic/`（1） | `service/task/task-rpc/`（`impl/TaskServiceGrpcImpl.java`） | 28 / 4,310 | 134 |
| `task` | `task-proto` | `proto/task.proto` | `service/task/task-proto/src/main/proto/task.proto` | —（生成） | 0 |
| `users` | `user-api` | `service/user/user/api/internal/handler/routes.go` + `internal/logic/`（9） | `service/users/user/user-api/`（`controller/UserController.java`） | 43 / 3,407 | 127 |
| `users` | `user-rpc` | `service/user/user/rpc/internal/logic/`（9） | `service/users/user/user-rpc/`（`impl/UserServiceGrpcImpl.java`） | 15 / 2,735 | 162 |
| `users` | `admin-api` | `service/user/admin/api/internal/handler/routes.go` + `internal/logic/`（13） | `service/users/admin/admin-api/`（`controller/AdminController.java`） | 56 / 3,854 | 149 |
| `users` | `admin-rpc` | `service/user/admin/rpc/internal/logic/`（13） | `service/users/admin/admin-rpc/`（`AdminServiceGrpcImpl.java`） | 20 / 3,560 | 122 |
| `users` | `users-common` | `service/user/common/{cryptx,errmsg,jwt}`（3 文件） | `service/users/users-common/`（11 类：`JwtUtil` / `ErrorCode` / `SchemaInitializer` / `GoStyle*` 等） | 11 / 1,037 | 39 |
| `users` | `user-proto` | `proto/user.proto` | `service/users/user/user-proto/src/main/proto/users.proto`（**已改名**） | —（生成） | 0 |
| `users` | `admin-proto` | `proto/admin.proto` | `service/users/admin/admin-proto/src/main/proto/admin.proto` | —（生成） | 0 |
| **共享层** | `service/common` | `service/common/`（5 包：`communityfact` / `logger` / `observability` / `response` / `snowflake`） | `service/common/`（73 类） | 73 / 14,804 | 337 |

> ⚠️ **口径说明**：
> - **38 组件 = 10 api + 10 rpc + 8 common + 10 proto**（AGENTS.md §3.5 口径，**本轮实测自洽**）。
>   `task` 域**无 `-common`**（Go 侧同样没有，**不是遗漏**）⇒ `task` 计 3 个组件、`users` 计 7 个（user/admin 两套）。
> - **`service/common`（共享层）不在 38 之内** ⇒ 下表它是**第 39 行**，已单独标注 ⇒ **按行数数会多数 1 行**。
> - `main/LOC` 口径 = `src/main/java` 下 `.java` 的**文件数 / 逐文件 `splitlines()` 行数**（**不是** `cat | wc -l` —— 后者少计无尾换行的文件）。
> - `*-proto` 的 `main/LOC = —`：代码由 protoc 生成，不手写（AGENTS.md §3.4）。
> - **`user.proto` → `users.proto` 的改名**是 AGENTS.md §3.4 记录的**表达层归一**（10 个 proto 中仅此一个改名）。

### A-2 终点指标与当前判定（39 行 = 38 组件 ＋ 1 共享层 · 2026-10-28 实测）

> **通用终点指标**（下表按组件类型引用，不重复展开）：
>
> - **【API 型】** ① 端点集合：Go `routes.go` 的 `(method, path)` **归一化后**（`:id` ↔ `{id}`）与 RAG 方法级映射**集合相等**；② JWT 保护端点集合与 Go `rest.WithJwt` 段**逐条一致**；③ 每端点 **≥1** 条断言**业务结果**（非仅状态码）的测试；④ 每端点 **≥1** 条**失败路径**测试（参数非法 / 权限不足 / 下游不可用）；⑤ 失败路径**不返回误导性成功**。
> - **【RPC 型】** ① Go proto 的 `rpc` 方法集合 **≡** RAG `*ImplBase` 子类覆写方法集合（大小写归一后）；② 每方法 **≥1** 条测试；③ 每个**写路径**证明事务边界（回滚）或幂等；④ 下游超时 / 下游不可用**有明确行为且有断言**；⑤ 业务码经 gRPC 传输后**可还原**（`description` 逐字节保真）。
> - **【COMMON 型】** ① Go 侧该 `<域>-common` **每个文件**都有对应 RAG 类；② 错误码**码值 + 文案 + 未知码回落**逐字节一致；③ 业务码经 gRPC 传输后可还原。
> - **【PROTO 型】** ① `.proto` 剥 `option` / 空白后**行为层零差异**；② message 名 / 字段名 / 字段号 / rpc 方法**逐条一致**；③ `optional` presence 语义逐条核对；④ 字段号**无硬编码覆盖**；⑤ 消费点构造方式与 Go 一致；⑥ wire 层字节对拍。

| 域 | 组件 | 必达终点指标（可判定） | 判定命令 | R | E | 当前状态（2026-10-28） |
|---|---|---|---|---|---|---|
| `article` | `article-api` | 【API 型】7 端点（4 个 JWT）集合相等 **＋** 每端点 ≥1 业务断言 ＋ 每端点 ≥1 失败路径 | `bash tools/difftest/check-proto-static.sh`（proto 面）＋ 模块级 `mvn -pl service/article/article-api -am test` | **R2** | E3 | 🟡 **契约达标 / 语义待补** —— 台账判 `SEMANTICALLY NOT EQUIVALENT`（第十二轮，不变）；**无双侧活体对拍**（Go 侧需 etcd，不可拉起） |
| `article` | `article-rpc` | 【RPC 型】6 rpc 方法全覆盖 ＋ 写路径事务/幂等证明 ＋ MQ 事件契约冻结 | 同上 ＋ `python3 tools/difftest/check-grpc-interceptor-wiring.py` | **R2** | E3 | 🟡 **契约达标 / 语义待补** —— 13 条已改；**S-5 阻塞于 security 域迁移**；MQ 事件契约未冻结；U-2 真并发用例未写 |
| `article` | `article-common` | 【COMMON 型】45 行 → 1 类；码值/文案/回落**逐字节一致** | `GO_REPO=… bash tools/errmsg-probe/run-parity.sh` ⇒ EXIT=0 | **R4** | E4 | ✅ **达标** —— sha256 逐字节一致（15 码 + 回落） |
| `article` | `article-proto` | 【PROTO 型】6 项全过 ＋ `optional` 逐个核对 | `bash tools/difftest/check-proto-static.sh`（**不覆盖 article** ⇒ 需人工 diff） | **R3** | E3 | 🟡 **契约达标 / 语义待补** —— 「✅ 一致」成立，但**消费方式核对是源码级，未做 wire 活体对拍**；**无落仓 proto 静态守门** |
| `comment` | `comment-api` | 【API 型】7 端点（7 JWT）集合相等 ＋ 每端点 ≥1 失败路径 | `bash tools/difftest/comment/run_diff.sh` ⇒ 23 步矩阵 | **R4** | E4 | ✅ **达标** —— 3 条 MISMATCH 全修；活体 23 步**复跑两轮一致**（20 一致 / 8 差异，其中 2 已签字、1 新登记 T20 已签字） |
| `comment` | `comment-rpc` | 【RPC 型】7 rpc 全覆盖 ＋ 写路径事务证明 | `bash tools/difftest/comment/run_diff.sh` ＋ 模块级 test | **R3** | E3 | 🟡 **契约达标 / 语义待补** —— 7 条 MISMATCH 全修（各配真 PG 守门 + 变异）；**§3 又有 2 条真漂移 D8/D9**（落点 `service/common` ⇒ global 单写权，**待修法拍板**） |
| `comment` | `comment-common` | 【COMMON 型】码值/文案/回落逐字节一致 | 域内契约测试（`CommentErrorCodeContractTest`） | **R3** | E3 | ✅ **达标** —— 0 条 MISMATCH |
| `comment` | `comment-proto` | 【PROTO 型】6 项全过 ＋ wire 字节对拍 | `python3 tools/difftest/comment/check-proto-parity.py` | **R4** | E4 | ✅ **达标** —— 245 行行为层零差异 ＋ **双侧活体 wire 对拍**；跨栈路径前缀差异 = **已签字** `SD-2026-10-23-04`（只增别名 `PbCommentServiceAlias`，已闭环） |
| `favorite` | `favorite-api` | 【API 型】7 端点（7 JWT）集合相等 ＋ 每端点 ≥1 失败路径 | `bash tools/api-envelope-probe/favorite/run.sh` ＋ `bash tools/api-full-parity-probe/favorite/run.sh` | **R4** | E4 | ✅ **达标** —— 7 路由契约逐条一致；响应字节层已升级为**双侧活体对拍**；域内「未知项」归零 |
| `favorite` | `favorite-rpc` | 【RPC 型】7 rpc 全覆盖 ＋ 写路径事务/幂等 ＋ 熔断生效 | `bash tools/grpc-contract-probe/favorite/run.sh` ＋ `bash tools/ddl-parity-probe/favorite/run.sh` | **R4** | E4 | ✅ **达标** —— 6 项双侧活体对拍（错误码 / path / 指标 / v2 体形 / **protobuf wire 字节** / DDL 逐列）；变异验证 M1/M2 + 4 例转红 |
| `favorite` | `favorite-common` | 【COMMON 型】24 码 ＋ 回落 ＋ gRPC 可还原 | `bash tools/errmsg-probe/favorite/run-parity.sh` ⇒ EXIT=0（108 行逐字节） | **R4** | E4 | ✅ **达标** —— 本仓**第一个**「§3.3 两侧同时跑的探针」落地域 |
| `favorite` | `favorite-proto` | 【PROTO 型】6 项全过 ＋ wire 字节对拍 | `bash tools/proto-wire-probe/favorite/run.sh`（6 组消息、**两个不同运行时**） | **R4** | E4 | ✅ **达标** —— 107/108 行（仅 option 块差 1 行）逐条同序；16 个 message 逐条一致 |
| `follow` | `follow-api` | 【API 型】8 端点（8 JWT）集合相等 ＋ 每端点 ≥1 失败路径 | ⚠️ **无落仓入口**（脚本在 `/tmp/probe.sh` 等 3 个，**未进版本控制**） | **R4\*** | E4\* | 🟡 **契约达标 / 语义待补** —— 台账记「52/52 逐字节一致」，但**探针不可复算** ⇒ 见 §7 G-1 |
| `follow` | `follow-rpc` | 【RPC 型】8 rpc 全覆盖 ＋ 写路径事务 ＋ 熔断形状 | 同上 ⚠️ **无落仓入口** | **R4\*** | E4\* | 🟡 **契约达标 / 语义待补** —— 同上；并发 12×2 轮探针亦在 `/tmp` |
| `follow` | `follow-common` | 【COMMON 型】16 码 ＋ 文案逐条一致 | ⚠️ **无落仓入口** | **R3** | E3 | 🟡 契约面逐条一致（16 码 16 文案零漂移），但**无双侧探针** |
| `follow` | `follow-proto` | 【PROTO 型】6 项全过 | ⚠️ **无落仓 proto 守门**（`check-proto-static.sh` 不覆盖 follow） | **R3** | E2 | 🟡 **契约达标 / 语义待补** —— 52/53 行行为层零差异（仅 option）；**消费方式已验**但无守门网 ⇒ 漂了不会红 |
| `hot` | `hot-api` | 【API 型】1 端点（0 JWT）＋ Tomcat 三旋钮与 Go 上下限**逐个值**一致 | `python3 tools/difftest/check-tomcat-limits.py`（静态 10/10）＋ `bash tools/json-body-probe/run-parity.sh` | **R4** | E4 | ✅ **达标** —— D21/D22/D23 已闭合；真 Tomcat 活体：值个数翻转 n 两侧都 2049、请求行翻转长度两侧都 1052536 字节（差 0）；**残留 `DRIFT-HOTAPI-030`**（容器输入上限语义）已由共享层修 |
| `hot` | `hot-rpc` | 【RPC 型】5 rpc 全覆盖 ＋ Kafka 端到端 ＋ 拦截器真接线 | `python3 tools/difftest/check-grpc-interceptor-wiring.py` ⇒ EXIT=0（10/10）＋ `bash tools/difftest/run-hot-rpc-difftest.sh` | **R4** | E4 | ✅ **达标** —— 真 Kafka + 2×真 Redis + 真 gRPC；`DRIFT-COMMON-004` 已修（拦截器真挂上 server） |
| `hot` | `hot-common` | 【COMMON 型】121 行 → 2 类；码值/文案逐字节一致 | 域内契约测试（14 条全绿 ＋ 变异 11/11 全红） | **R4** | E4 | ✅ **达标** —— 双侧对拍 sha256 逐字节一致（`f23fe570…`） |
| `hot` | `hot-proto` | 【PROTO 型】6 项全过 | ⚠️ 无落仓 proto 守门（`hot-stub-server.java` 引用生成码但**不是契约守门**） | **R2** | E2 | 🟡 **契约达标 / 语义待补** —— §3.4 复制忠实成立；**消费方式未验**（§3.4 效力边界第 1 条） |
| `like` | `like-api` | 【API 型】6 端点（6 JWT）集合相等 ＋ 每端点 ≥1 失败路径 | ⚠️ **无落仓入口**（`/tmp/likeprobe/*` **三个文件已消失**） | **R3** | E3\* | 🟡 **契约达标 / 语义待补** —— 台账记 5 条 `SEMANTIC MISMATCH`（DRIFT-012/013/014/015/016）**已修**，但**复算证据不可复算** ⇒ 见 §7 G-2 |
| `like` | `like-rpc` | 【RPC 型】6 rpc 全覆盖 ＋ 事实投递链路（跨三仓） | ⚠️ **无落仓入口** | **R3** | E3 | 🟡 契约面覆盖完整；事实投递链路已落地（第二十轮 A5′，守门 22 条） |
| `like` | `like-common` | 【COMMON 型】码值/文案/回落一致 | ⚠️ 无专属落仓入口；`check-ledger-selfcheck.py` 覆盖 | **R3** | E3 | 🟡 契约面逐条一致；**无专属台账**（`docs/DRIFT/like/common.md` 不存在） |
| `like` | `like-proto` | 【PROTO 型】6 项全过 | ⚠️ **无落仓 proto 守门** | **R3** | E2 | 🟡 复制忠实成立 ＋ **消费方式已验**；`package` 归一 = **已签字** `SD-2026-10-23-05`（跨栈失效面 = 当前不存在） |
| `message` | `message-api` | 【API 型】13 端点（13 JWT）集合相等 ＋ 每端点 ≥1 失败路径 | `bash tools/semantic-probe/run-parity.sh` ＋ `bash tools/difftest/run.sh` | **R4** | E4 | ✅ **达标** —— 解析语义双侧对拍（含**签字偏离白名单**机制，防「守门人去修绿」）；`affected` 字符串形态 = **已签字** `SD-2026-10-23-03` |
| `message` | `message-rpc` | 【RPC 型】9 rpc 全覆盖 ＋ 写路径事务 ＋ 失败路径 | `bash tools/difftest/run.sh`（含 message-rpc 双端口 runner）＋ `run-message-ddl-parity.sh` | **R3** | E3 | 🟡 契约面全覆盖；台账记 1 条 MISMATCH + 5 OBS |
| `message` | `message-common` | 【COMMON 型】码值/文案/回落一致 | 域内契约测试 | **R3** | E3 | ✅ **达标** —— 逐条等价（`FIX-MSG-COMMON-002` 已修） |
| `message` | `message-proto` | 【PROTO 型】6 项全过 | `bash tools/difftest/grpc-message-dual-port-runner.java` 路径 | **R3** | E3 | ✅ **达标** —— 文件零差异 ＋ 消费方式已验 |
| `task` | `task-api` | 【API 型】1 端点（0 JWT）＋ 参数非法路径 | `bash tools/difftest/run-task-http-parity.sh` | **R4** | E4 | ✅ **达标** —— 首轮 2 条漂移已修 |
| `task` | `task-rpc` | 【RPC 型】1 rpc 全覆盖 ＋ Kafka 端到端 ＋ 跨栈 xlang | `bash tools/difftest/run-task-grpc-xlang.sh` ＋ `run-task-kafka-e2e.sh` ＋ `run-task-offset-oldest.sh` | **R4** | E4 | ✅ **达标** —— 首轮 5 条漂移全修并复核；**真 Kafka 端到端** ＋ **HTTP 双侧对拍** |
| `task` | `task-proto` | 【PROTO 型】6 项全过 | `bash tools/difftest/check-proto-static.sh`（已扩到 task）⇒ EXIT=0 | **R3** | E3 | ✅ **达标** —— 消费方式首次核对未发现差异；**已有持续守门** |
| `users` | `user-api` | 【API 型】9 端点（7 JWT）集合相等 ＋ 每端点 ≥1 失败路径 | `bash tools/difftest/run.sh` ＋ `python3 tools/difftest/matrix.py` | **R4** | E4 | ✅ **达标** —— 9 端点逐条对拍（方法/参数通道按真源 `types.go` 自动解析）；`uid` 统一拒绝 = **已签字** `SD-2026-10-23-02` |
| `users` | `user-rpc` | 【RPC 型】9 rpc 全覆盖 ＋ 写路径事务 ＋ JWT 探针 | `bash tools/difftest/grpc-user9-dual-port-runner.java` ＋ `run.sh` | **R4** | E4 | ✅ **达标** —— 真 PG ＋ 9 rpc 逐条；`/tmp/goprobe/probejwt` 三腿全中 |
| `users` | `admin-api` | 【API 型】13 端点（11 JWT）集合相等 ＋ 每端点 ≥1 失败路径 | `bash tools/difftest/run.sh` ＋ `matrix.py` | **R4** | E4 | ✅ **达标** —— `OperatorId` 严格校验 = **已签字** `SD-2026-10-23-01`（RAG 更严一侧） |
| `users` | `admin-rpc` | 【RPC 型】13 rpc 全覆盖 ＋ 写路径事务 | `bash tools/difftest/GrpcAdminDualPortRunner.java` ＋ `run.sh` | **R4** | E4 | ✅ **达标** —— 13 rpc 逐条；DDL 半边已对齐（4 张表时间列全 `TIMESTAMPTZ`、类型 0 差异） |
| `users` | `users-common` | 【COMMON 型】Go 3 文件 → RAG 11 类；JWT/密码/错误码/请求绑定**双侧活体** | `bash tools/semantic-probe/run-parity.sh` ＋ `bash tools/breaker-probe/run-parity.sh` | **R4** | E4 | ✅ **达标** —— 本仓**第一个**双侧活体对拍落地域（Go 进程 ↔ JVM 进程逐字节） |
| `users` | `user-proto` | 【PROTO 型】6 项全过 | `bash tools/difftest/check-proto-static.sh` ⇒ user 段 EXIT=0 | **R3** | E3 | ✅ **达标** —— 9 rpc 逐条；改名 = 表达层归一（§3.4） |
| `users` | `admin-proto` | 【PROTO 型】6 项全过 | 同上 ⇒ admin 段 EXIT=0 | **R3** | E3 | ✅ **达标** —— 13 rpc 逐条 |
| 跨服务 | `service/common` | 【COMMON 型】Go 5 包 → RAG 73 类；**每个可比包**逐行对齐；熔断/deadline/响应体三面双侧活体 | `bash tools/breaker-probe/run-parity.sh` ＋ `bash tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh` ＋ `…/rpc-timeout/run-rpc-timeout-parity.sh` | **R2** | E3 | 🔴 **未达标** —— **5 个可比包 3 个 NOT EQUIVALENT**（communityfact / logger / observability / snowflake；仅 `response` 逐条等价）⇒ **27 条编号未修** ＋ 5 项未编号 ＋ 9 项交接；**G4 共享层单写权** |

> **图例**：R4\* / E4\* = **台账声称的等级，但复算入口未落仓** ⇒ 标 `\*` 表示「**声明的，不是可复算的**」（见 §7）。
> ⚠️ **行数**：本表 **39 行 = 38 组件 ＋ 1 行共享层 `service/common`** ⇒ 统计组件数时**减 1**。

---

## 三、矩阵 B：10 张域级终点指标卡

> **域级聚合规则**：域内**全部**组件达标 ⇒ 域达标；任一组件 🔴 ⇒ 域 🔴；其余 ⇒ 🟡。
> 例外：`task` 域无 `-common` ⇒ **3 个组件**参与聚合；`users` 域 **7 个**。

### B-1 `article`（4 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 7 端点（4 JWT）+ 每端点 ≥1 业务断言 + ≥1 失败路径 | 🟡 |
| rpc | 6 rpc + 写路径事务/幂等 + MQ 事件契约冻结 + 真并发用例 | 🟡 |
| common | 45 行 → 1 类，逐字节一致 | ✅ |
| proto | 6 项 + wire 活体 | 🟡 |
| **域聚合** | 4/4 达标 | **🟡 3 达标 1 未达标 ⇒ 域未达标**（api / rpc / proto 三层未达终点） |

**域级硬门（必须清零才放行）**：① `S-5`（阻塞于 security 域迁移，**范围外** ⇒ 走 D 类豁免登记）；② MQ 事件契约冻结；③ U-2 真并发用例。

### B-2 `comment`（4 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 7 端点（7 JWT）+ 23 步双侧矩阵 | ✅ |
| rpc | 7 rpc + 写路径事务 + **D8/D9 两条真漂移** | 🟡 |
| common | 码值/文案/回落 | ✅ |
| proto | 6 项 + wire 活体 | ✅ |
| **域聚合** | 4/4 | **🟡** —— D8/D9 落点 `service/common` ⇒ **不在本域权限内** ⇒ 走 G4 反馈单 |

### B-3 `favorite`（4 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 7 端点（7 JWT）+ 响应字节双侧对拍 | ✅ |
| rpc | 7 rpc + 熔断 + DDL 逐列 | ✅ |
| common | 24 码 + 回落 + gRPC 可还原 | ✅ |
| proto | 6 项 + wire 字节（两运行时） | ✅ |
| **域聚合** | 4/4 | **✅ 达标** —— 本仓**唯一 4/4 全绿**的域，**14 个落仓探针** |

### B-4 `follow`（4 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 8 端点（8 JWT）+ 52 步逐字节 | 🟡\*（活体已做，**入口未落仓**） |
| rpc | 8 rpc + 熔断形状（12 并发 ×2 轮） | 🟡\* |
| common | 16 码 16 文案 | 🟡 |
| proto | 6 项 | 🟡（**无守门网**） |
| **域聚合** | 4/4 | **🟡** —— 全部卡在**同一个根因**：探针未落仓 ⇒ 结论不可复算 |

### B-5 `hot`（4 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 1 端点 + Tomcat 三旋钮上下限逐值一致 | ✅ |
| rpc | 5 rpc + 真 Kafka + 拦截器真接线 | ✅ |
| common | 121 行 → 2 类，逐字节 | ✅ |
| proto | 6 项 | 🟡（消费方式未验） |
| **域聚合** | 4/4 | **🟡** —— 只差 proto 层消费方式核对 |

### B-6 `like`（4 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 6 端点（6 JWT）+ ≥1 失败路径 | 🟡\*（探针已消失） |
| rpc | 6 rpc + 事实投递链路 | 🟡 |
| common | 码值/文案/回落 | 🟡（**无专属台账**） |
| proto | 6 项 | 🟡（**无守门网**） |
| **域聚合** | 4/4 | **🟡** —— 5 条 SEMANTIC MISMATCH 已修，但**复算证据已不可复算** |

### B-7 `message`（4 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 13 端点（13 JWT）+ 解析语义双侧 | ✅ |
| rpc | 9 rpc + 写路径事务 + 失败路径 | 🟡 |
| common | 码值/文案/回落 | ✅ |
| proto | 6 项 | ✅ |
| **域聚合** | 4/4 | **🟡** |

### B-8 `task`（3 组件 · 无 `-common`）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| api | 1 端点（0 JWT）+ 参数非法路径 | ✅ |
| rpc | 1 rpc + 真 Kafka 端到端 + 跨栈 xlang | ✅ |
| proto | 6 项 + 持续守门 | ✅ |
| **域聚合** | 3/3 | **✅ 达标** —— 本仓**唯一 3/3 全绿**的域 |

### B-9 `users`（7 组件）

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| user-api | 9 端点（7 JWT）+ 逐条对拍 | ✅ |
| user-rpc | 9 rpc + 真 PG + JWT 三腿 | ✅ |
| admin-api | 13 端点（11 JWT）+ 逐条对拍 | ✅ |
| admin-rpc | 13 rpc + DDL 半边 | ✅ |
| users-common | Go 3 文件 → 11 类 + 双侧活体 | ✅ |
| user-proto | 6 项 | ✅ |
| admin-proto | 6 项 | ✅ |
| **域聚合** | 7/7 | **✅ 达标** —— **唯一 7/7 全绿的域** |

> ⚠️ **注意**：`users` 域 ✅ **不等于** 全仓 ✅ —— users 的端点路径前缀与方法**是已签字偏离**（`/usercenter/v1/user/*` → `/user/v1/*`，method 统一 POST），**不是逐条相等**。

### B-10 共享层 `service/common`（**不在 38 组件之内**，但必须单列）

> ⚠️ **口径澄清（本轮实测订正）**：
> - **38 组件 = 10 api + 10 rpc + 8 common + 10 proto**（AGENTS.md §3.5 口径，**实测自洽**）：
>   api = 9 域各 1 + `user-api` + `admin-api`；rpc 同理；common = article/comment/favorite/follow/hot/like/message/`users-common` 共 8（**`task` 无 `-common`**，Go 侧同样没有）；proto = 9 域各 1 + `user-proto` + `admin-proto`。
> - **`service/common` 是跨服务共享层**（AGENTS.md §5.3f：写权归 G4），**不是第 11 个微服务域、也不在 38 之内**。
> - ⚠️ **源台账的口径陷阱**：`docs/DRIFT/_cross/component-coverage-matrix.md` 的当前态表**有 39 行** ——
>   38 个组件 **＋ 1 行 `跨服务 / service/common`**。标题写「38 组件」是对的 ⇒ **按行数数会多数 1 行**。
>   ⇒ 本文件 §二 A-1/A-2 的 `service/common` 行**单独标为「共享层」**，不计入 38。

| 层 | 必达终点指标 | 当前 |
|---|---|---|
| `service/common` | Go 5 包 → RAG 73 类；**每个可比包逐行对齐**；熔断/deadline/响应体三面双侧活体 | 🔴 **5 包中 3 个 NOT EQUIVALENT · 27 条编号未修** |

---

## 四、矩阵 C：跨组件不变量（12 条）

> **跨组件不变量 = 不归属任何单个组件、但影响多个域的约束**（AGENTS.md §3.5 的 `global` 收口域）。
> ⇒ **本层不过 ⇒ 域层全绿也不算数。**

| # | 不变量 | 涉及组件 | 必达终点指标 | 判定命令 | 当前（2026-10-28） |
|---|---|---|---|---|---|
| **X-1** | **共享 Flyway 账本隔离** | `message-rpc`↔`like-rpc`↔`article-rpc`↔`favorite-rpc`↔`comment-rpc`↔`follow-rpc`↔`task-rpc` | 每模块独立账本表 `flyway_schema_history_<模块>`；**同库三进程并发**不撞号 | `python3 tools/difftest/check-flyway-ledger.py` ⇒ EXIT=0 **＋** `bash tools/difftest/check-flyway-concurrent.sh` | ✅ **已闭环**（配置层 + 真 PG 三进程并发同库，L4）<br>⚠️ `favorite-rpc` 缺 `baseline-version: 0` ⇒ 只记账 |
| **X-2** | **DDL 双侧活体逐列** | 9 域（favorite 只建库、hot N/A） | 类型/长度 **0 差异**；可空性、DEFAULT、索引逐项一致或**已签字保留** | `python3 tools/difftest/check-ddl-static.py --domain <域>`（**裸跑只查 users 域**） | 🟡 **类型/长度 9 域 0 差异**；users 可空性 1 + DEFAULT 16 残留（**刻意保留**）· comment 2 条**仅 RAG 有** · hot **N/A**（两侧 0 张表） |
| **X-3** | **gRPC 拦截器真接线** | 10 个 rpc 模块 | 每个 rpc 模块的拦截器**真挂上 server**（不是「Bean 存在」） | `python3 tools/difftest/check-grpc-interceptor-wiring.py` ⇒ EXIT=0 | ✅ **10/10 接线完整** |
| **X-4** | **api 容器上限三旋钮** | **全部 10 个 api** | `relaxed-query-chars` / `max-parameter-count` / `max-http-request-header-size` 与 Go 上下限**逐值**一致 | `python3 tools/difftest/check-tomcat-limits.py` ⇒ EXIT=0 ＋ 真 Tomcat 活体 | ✅ **10/10**（`DRIFT-HOTAPI-030` 已由共享层修：容器上限抬到 10001） |
| **X-5** | **产物可执行性** | 10 个 api + 10 个 rpc | 每个 fat jar **可执行**（含 `Main-Class`）；`discovery://` 三斜杠 | `bash tools/difftest/check-jar-executable.sh` ⇒ EXIT=0 | ✅ **10/10 可执行**（两斜杠与缺 `Main-Class` 两批已修） |
| **X-6** | **deadline 分层预算与传播** | 10 个 rpc + 共享层 | 下游调用 deadline = `min(客户端 deadline, 服务端 timeout)`；SQL 取消时刻 == deadline 时刻 | `python3 tools/difftest/check-deadline-verdict.py` ＋ `check-deadline-downstream.py` ⇒ EXIT=0 ＋ `bash tools/difftest/run-deadline-gate.sh` | ✅ **双侧活体探针 PASS**（`tools/semantic-probe/rpc-timeout/`）＋ **变异 2 条全红** |
| **X-7** | **错误码契约（跨域）** | 9 个 `-common` + `users-common` | 码值 + 文案 + 未知码回落**逐字节一致**；业务码经 gRPC **可还原** | `GO_REPO=… bash tools/errmsg-probe/run-parity.sh`（**article**）＋ `bash tools/errmsg-probe/favorite/run-parity.sh`（**favorite**） | 🟡 **只覆盖 2/10 域的双侧逐字节对拍**；其余 8 域只有**域内契约测试**（证据强度不同） |
| **X-8** | **HTTP 路径 / 方法契约** | 全部 10 个 api | (method, path) 归一化后**集合相等**；JWT 保护集合相等 | 本文件 A-2 各域 API 型指标 | 🟡 **8/10 域逐条相等**；`users`/`admin` 两域 = **已签字偏离**（前缀 + method） |
| **X-9** | **gRPC 方法覆盖** | 10 个 rpc | Go proto `rpc` 集合 ≡ RAG `*ImplBase` 覆写集合 | 同上 RPC 型指标 | ✅ **10/10 域逐条覆盖 · Go 71 ≡ RAG 71** |
| **X-10** | **proto 契约 + 消费方式** | 10 个 proto | 行为层零差异 ＋ `optional` presence ＋ 无字段号硬编码 ＋ 消费点一致 | `bash tools/difftest/check-proto-static.sh`（**只覆盖 user/admin/task**）＋ `python3 tools/difftest/comment/check-proto-parity.py` | 🟡 **落仓静态守门只覆盖 4/10**（user · admin · task · comment）；`follow` / `like` **无任何守门网** ⇒ 漂了不会红 |
| **X-11** | **敏感配置链** | 10 个 api + 2 个 rpc + compose | 密钥**无明文默认值**；缺变量 ⇒ **启动失败**（非空签名密钥） | `docker compose config` ⇒ 渲染出非空 secret **且** 缺变量时启动失败 | 🔴 **未达标** —— 10 处 yaml 带明文默认值（含 `like-api:62` 裸字面量）；compose `:146`/`:193` 缺 `:?` ⇒ 不设变量**空签名密钥起栈**（E4 已实测） |
| **X-12** | **构建 / 配置 / 启动说明** | 根级 | README ≥ 1 屏且能照着构建启动；`.env.example` 存在；compose fail-closed | 人工 + `docker compose config` | 🔴 **未达标** —— README **21 字节**；无 `.env.example`；`docs/FileTree/Infrastructure.md` 描述的是**旧架构** |

---

## 五、聚合判定规则

### 5.1 组件 → 域

```
域状态 = MIN(域内各组件状态)
其中 MIN 序：✅ 达标 > 🟡 契约达标/语义待补 > ⚫ 受阻 > 🔴 未达标 > ⬜ 豁免（豁免不参与 MIN）
```

### 5.2 域 → 全仓

**阶段一通过的三条硬条件（全部满足才算通过）**：

| 条件 | 判据 | 当前 |
|---|---|---|
| **C-1 核心域达标** | 9 个微服务域中**核心 5 域**（`favorite` `task` `users` `hot` `message`）**全部 ✅ 达标** | 🟡 3/5（favorite ✅ · task ✅ · users ✅；hot 🟡 · message 🟡） |
| **C-2 无 🔴 组件** | 全仓 **0** 个 🔴 未达标组件（`service/common` 当前 🔴 ⇒ **不满足**） | 🔴 |
| **C-3 跨组件不变量** | 矩阵 C 的 12 条中，**X-1~X-10 无 🔴**（X-11/X-12 归 P0 准入，见下） | 🟡 X-11 🔴 · X-12 🔴（P0 #5 / #8） |

### 5.3 全仓 → 阶段 1 结论（对齐 `docs/twoStage.md` §5.6 的四种）

| 结论 | 触发条件 | 当前 |
|---|---|---|
| **通过** | C-1 ∧ C-2 ∧ C-3 ∧ P0 九条全满足 | ❌ 不满足 |
| **有条件通过** | 存在**明确、有限**的例外（已签字偏离 / 已登记暂缓项 / 环境受阻），且**不破坏核心正确性、安全性、数据完整性** | ❌ 不满足（X-11 密钥链**属安全** ⇒ 不属「有限例外」） |
| **未通过** | 存在破坏核心正确性 / 安全 / 数据完整性的未修项 | ❌ 不满足 |
| **受阻** | 缺必要环境或证据 | ⚪ 部分受阻（§7 G-6） |

### 5.4 与 P0 九条的映射（哪些矩阵行支撑哪条）

| P0 | 内容 | 支撑本文件哪几行 | 当前 |
|---|---|---|---|
| 1 | 核心业务链路真实运行 | B-1~B-10 全部 ＋ X-9 | ✅ |
| 2 | 参数校验 / 业务规则 / 认证 / 权限有效 | X-8 ＋ 各域 API 型「≥1 失败路径」 | ✅（`SD-2026-10-23-01/02` 属**更严一侧**，已签字） |
| 3 | 数据正确持久化 / 事务边界合理 | X-1 ＋ X-2 ＋ 各域 RPC 型「写路径事务/幂等」 | 🟡（X-2 users 可空性 + DEFAULT 残留） |
| 4 | 失败路径明确，不误导性成功 | 各域 API/RPC 型「失败路径有断言」 | ✅ |
| 5 | **能按实际说明构建、配置、启动** | **X-12** ＋ 根级 README / `.env.example` | 🔴 **不满足**（README 21 字节） |
| 6 | 核心业务有可执行自动化测试且断言真实业务结果 | A-1 的 `tests` 列（**2860 条 / 29 模块**，独立重算一致）＋ 各域「断言业务结果」 | ✅ |
| 7 | 模块职责清晰，可追踪核心调用链 | A-1 的 Go 原件 ↔ RAG 实现两列 | ✅ |
| 8 | **关键配置和敏感信息无明显不安全处理** | **X-11** | 🔴 **不满足**（空签名密钥可起栈，E4 实测） |
| 9 | 已知缺陷 / 不可验证能力 / 运行前提**明确记录** | 本文件 §7 ＋ 各域台账 ＋ `docs/Intentional_Deviations/`（**48 条已签字 + 18 条未签字候选**） | ✅ |

⇒ **P0 九条：7 满足 / 2 不满足（#5、#8）/ 0 受阻**。

### 5.5 差异与暂缓项的登记口径（`twoStage.md` §5.3）

| 归类 | 判据 | 落点 |
|---|---|---|
| 要「修回去」 | 与 Go 不同且应一致 | `docs/DRIFT/<域>/<组件>.md` |
| 不要「修回去」 | 有意为之，已签字 | `docs/Intentional_Deviations/<域>/<组件>.md` |
| 阶段 1 暂缓 | 阶段 2 补齐，**必须写明**「阶段 2 恢复方案 + 测试场景 + 通过标准」 | `docs/DRIFT/global/`（收口域） |

> ⚠️ **暂缓 ≠ 豁免**：若暂缓项会破坏核心业务正确性、造成严重数据风险或形成不可接受的安全缺口 ⇒ **不得靠登记获得阶段 1 通过资格**（`twoStage.md` §5.3 末段）。

---

## 六、当前快照（2026-10-28 实测）

### 6.1 全量回归（基线 commit `7ec6fc6`）

| 项 | 值 | 来源 |
|---|---|---|
| 命令 | `mvn -o -B -DargLine=-Xmx2g clean test` | `docs/DRIFT/global/_README.md:214-217` |
| 结果 | **BUILD SUCCESS** · **52 模块** · **42:41 min** | 同上 |
| 测试 | **2860 条 / 0 失败 / 0 错误 / 4 跳过** | 同上 |
| surefire XML | **442 份** | 同上 |
| **独立重算** | 我用 442 份 `TEST-*.xml` 逐 `testsuite` 属性求和 = **2860**，与台账**逐字一致** | 本次实测 |

> ⚠️ **口径**：`clean` 必须带 —— 不带 `clean` 时 stale 产物会同时伪造「当前的红」和「当前的条数」（Spring `AutoConfiguration.imports` 引用已删类 ⇒ 整族 `@SpringBootTest` 上下文起不来）。

### 6.2 逐模块测试条数（29 个有测试的模块 · XML 求和）

| 模块 | 条数 | 模块 | 条数 | 模块 | 条数 |
|---|---|---|---|---|---|
| `service/common` | **337** | `service/article/article-rpc` | 183 | `service/users/user/user-rpc` | 162 |
| `service/comment/comment-rpc` | 138 | `service/favorite/favorite-api` | 131 | `service/favorite/favorite-rpc` | 131 |
| `service/task/task-rpc` | 134 | `service/users/admin/admin-api` | 149 | `service/users/admin/admin-rpc` | 122 |
| `service/users/user/user-api` | 127 | `service/like/like-rpc` | 129 | `service/follow/follow-rpc` | 83 |
| `service/article/article-api` | 99 | `service/message/message-api` | 107 | `service/task/task-api` | 102 |
| `service/hot/hot-rpc` | 114 | `service/hot/hot-api` | 96 | `service/message/message-rpc` | 96 |
| `service/like/like-api` | 94 | `service/follow/follow-api` | 74 | `service/comment/comment-api` | 52 |
| `service/follow/follow-common` | 37 | `service/users/users-common` | 39 | `service/favorite/favorite-common` | 29 |
| `service/comment/comment-common` | 27 | `service/message/message-common` | 21 | `service/like/like-common` | 18 |
| `service/article/article-common` | 15 | `service/hot/hot-common` | 14 | | |
| **合计** | **2860**（29 模块） | | | | |

### 6.3 守门快照（19 项 · 2026-10-28 实测）

| 退出码 | 守门 |
|---|---|
| **EXIT=0（16 项）** | `check-cross-domain-holds` · `check-deadline-downstream` · `check-deadline-verdict` · `check-flyway-ledger` · `check-global-lane-ownership` · `check-go-json-body-predicate` · `check-grpc-interceptor-wiring` · `check-grpc-runtime-classpath` · `check-no-nul-bytes` · `check-tomcat-limits` · `check-wave-coupling` · `check-wave-lane-ownership` · `check-wave-role-model` · `check-ledger-count` · `check-ledger-selfcheck` · `check-ledger-tables` · `check-agents-budget` |
| **EXIT=1（1 项）** | `check-ddl-static`（裸跑只查 users 域 ⇒ 9 差异；**类型/长度 0 差异**） |
| **EXIT=2（1 项）** | `check-merge-tree-content` —— **用法错**（需 `ref` 参数，合并期工具）⇒ **不是守门缺口** |

**DDL 逐域（`--domain <域>`）**：`article` 0 · `follow` 0 · `like` 0 · `task` 0 ⇒ EXIT=0；`users` 9 · `favorite` 2 · `message` 6 ⇒ EXIT=1；`comment` 2（**仅 RAG 有** `report_record.user_id` / `target_type`）⇒ EXIT=2；`hot` ⇒ **EXIT=3（N/A，两侧 0 张表）**。

### 6.4 对外契约面快照

| 项 | Go | RAG | 结论 |
|---|---|---|---|
| api 端点总数 | **72** | **72** | ✅ 逐域相等 |
| 其中受 JWT 保护 | **63** | — | ✅ 逐域一致（`article` 4/7 · `comment` 7/7 · `favorite` 7/7 · `follow` 8/8 · `hot` 0/1 · `like` 6/6 · `message` 13/13 · `task` 0/1 · `user` 7/9 · `admin` 11/13） |
| 归一化后路径+方法**集合相等**的域 | — | — | ✅ **8/10**（`article` `comment` `favorite` `follow` `hot` `like` `message` `task`） |
| 不等的 2 域 | `users` · `admin` | 同 | 🟡 **已签字偏离**：前缀 `/usercenter/v1/user/*`→`/user/v1/*`、`/admincenter/v1/admin/*`→`/admin/v1/*` ＋ method 统一 POST |
| gRPC 方法 | **71** | **71** | ✅ **10/10 域逐条覆盖**（admin 13 · user 9 · message 9 · follow 8 · comment 7 · favorite 7 · article 6 · like 6 · hot 5 · task 1） |

### 6.5 探针可复算性快照（🔴 本轮新发现的关键缺口）

| 域 | 落仓的双侧对拍入口 | 可复算？ |
|---|---|---|
| `favorite` | **13 个**（`tools/{api-envelope,api-full-parity,grpc-contract,proto-wire,ddl-parity,metrics,authority-live,authority-path,sql-statement}-probe/favorite/run.sh` ＋ `errmsg-probe/favorite/run-parity.sh` ＋ 3 个审计脚本） | ✅ **最全** |
| `comment` | `tools/difftest/comment/run_diff.sh` + `diff_comment.py` + `check-proto-parity.py` | ✅ |
| `users` | `tools/difftest/run.sh` + `diff.py` + `matrix.py` + `grpc-user9-dual-port-runner.java` | ✅ |
| `message` | `tools/semantic-probe/run-parity.sh` + `tools/difftest/run.sh`（含 message-rpc 双端口） | ✅ |
| `hot` | `tools/json-body-probe/run-parity.sh` + `tools/difftest/run-hot*.sh` | ✅ |
| `task` | `tools/difftest/run-task-{http-parity,grpc-xlang,kafka-e2e,offset-oldest}.sh` + `parity-task.py` | ✅ |
| `article` | `tools/errmsg-probe/run-parity.sh`（**只覆盖 `article-common` 的错误码**） | 🟡 **部分** —— api / rpc 层**无落仓入口** |
| `service/common` | `tools/breaker-probe/run-parity.sh` + `tools/semantic-probe/{deadline-downstream,rpc-timeout}` | ✅ |
| **`follow`** | **0 个** | 🔴 **无** —— 探针在 `/tmp/probe.sh` `/tmp/probe_data.sh` `/tmp/probe_ext.sh` `/tmp/u3d_probe.sh` `/tmp/u3c_probe.sh`（**未进版本控制**） |
| **`like`** | **0 个** | 🔴 **无** —— 探针在 `/tmp/likeprobe/{main.go,RagProbe.java,NullElem.java}` ⇒ **三个文件已消失** |

> ⚠️ **台账引用的 `/tmp` 脚本共 61 个**，本次实测 **13 个已消失**（含 `like` 域全部 3 个、`users` 域 `/tmp/userprobe/*` 3 个、`service/common` 的 `/tmp/commonprobe/*` 3 个、`/tmp/hkparity/*`、`/tmp/hk-verify-20261023/*`、`/tmp/eiprobe/zzprobe/main.go`、`/tmp/task-difftest2/probe/RedisPipelineProbe.java`）。
> ⇒ **「双侧活体对拍」这个证据强度，在 `follow` 与 `like` 两域目前是「声明的，不是可复算的」。**

---

## 七、证据缺口清单（阶段 1 收口前必须清掉或明确登记为暂缓）

| 编号 | 缺口 | 影响 | 证据强度 | 处置方向 | 是否阻断阶段一 |
|---|---|---|---|---|---|
| **G-1** | **`follow` 域无落仓双侧对拍入口** —— 台账「52/52 逐字节一致」依赖 `/tmp` 下 5 个脚本，**未进版本控制** | 52 步结论**不可复算** | 声明 E4\* | 把探针固化进 `tools/difftest/follow/`（follow 台账 §748 已自行提出同一方向） | 🟡 **否**（若登记为暂缓并写明阶段 2 恢复方案） |
| **G-2** | **`like` 域无落仓双侧对拍入口，且 `/tmp/likeprobe/*` 三个文件已消失** | 5 条 `SEMANTIC MISMATCH`（DRIFT-012~016）的**复算证据已不可复算** | 声明 E3\* | 重建探针并落仓；**已修 ≠ 已验证** | 🟡 **否**（同上；但**必须先复核修复仍在**，不能凭台账断言） |
| **G-3** | **`article` 域 api / rpc 层无落仓活体对拍入口**（只有 common 的错误码探针） | 两个 🔴/🟡 组件的结论无法升级 | E3 | Go 侧需 etcd ⇒ 属**环境受阻** ⚫，登记为暂缓 | ⚫ 受阻 |
| **G-4** | **proto 落仓静态守门只覆盖 4/10**（user · admin · task · comment）；`follow` / `like` **连静态守门都没有** | proto 漂了**不会红** | E2 | 把 `check-proto-static.sh` 扩到 10 域 | 🟡 **否** |
| **G-5** | **错误码双侧逐字节对拍只覆盖 2/10 域**（article + favorite）；其余 8 域只有域内契约测试 | 证据强度不齐 | E3 | 按域补 `errmsg-probe/<域>/run-parity.sh` | 🟡 **否** |
| **G-6** | **运行证据缺口** —— 本机无 Kafka / Redis / Nacos / etcd ⇒ 服务发现故障、实例下线、优雅停机**无运行时行为证据**；无生产环境 ⇒ **L4 不可凭源码给** | 阶段 2 的前提，不是阶段 1 的 | E0 | 阶段 2 补 | ⚫ 受阻（阶段 1 不要求） |
| **G-7** | **密钥链 E4 实测**：10 处 yaml 带明文默认值（`like-api:62` 是裸字面量）；compose `:146`/`:193` 缺 `:?` ⇒ 不设变量 ⇒ **空签名密钥起栈** | **P0 #8 不满足** | E4 | 主配置 `${VAR}` 无默认值 + compose `:?` + `.env.example` + 根 pom surefire 注入测试值 | 🔴 **是** |
| **G-8** | **README 21 字节 + 无 `.env.example` + `Infrastructure.md` 描述旧架构** | **P0 #5 不满足** | E1 | 补 README 与 `.env.example` | 🔴 **是** |
| **G-9** | **DDL 残留** —— users 可空性 1 + `DEFAULT CURRENT_TIMESTAMP` 16 处（**刻意保留**）· comment 2 条**仅 RAG 有** · hot **N/A** | 类型/长度 **0 差异** ⇒ 非行为差异 | E3 | 已签字保留的走 D 类；comment 2 条须登记 | 🟡 **否** |
| **G-10** | **10 个 proto 中 8 个无回归网**（§3.4 效力边界） | 同 G-4 | E2 | 同 G-4 | 🟡 **否** |
| **G-11** | **共享索引过期** —— `docs/DRIFT/_INDEX.md` 仍写「favorite `api`/`common`/`proto` 仍零台账」，实测三份台账**均已有内容**（161 KB / 10 KB / 15 KB）；`component-coverage-matrix.md` 的当前态表**有 39 行**（38 组件 ＋ 1 行共享层），标题写「38 组件」⇒ **按行数数会多数 1 行** | 读索引/按行数数会误判现状 | E1 | 合并收口执行者更新共享索引（`docs/DRIFT/_INDEX.md` 属 **SHARED 面**）；行数口径按 §二 A-1 说明 | 🟡 **否** |

---

## 八、命令清单（全部可复算）

```bash
# ── 全量回归（基线推进后必须带 clean）──────────────────────────────
mvn -o -B -DargLine=-Xmx2g clean test

# ── 逐模块条数（权威读法 = surefire XML 求和，不是 maven 汇总行）──
python3 - <<'PY'
import glob,re
tot=0
for f in glob.glob('service/**/target/surefire-reports/TEST-*.xml',recursive=True):
    m=re.search(r'<testsuite[^>]*\btests="(\d+)"',open(f,encoding='utf-8',errors='replace').read(4000))
    if m: tot+=int(m.group(1))
print("TOTAL",tot)
PY

# ── 守门全跑（退出码先落变量再打印）────────────────────────────────
for g in tools/difftest/check-*.py tools/check-ledger-*.py tools/check-agents-budget.py; do
  out=$(python3 "$g" 2>&1); rc=$?
  printf "%-52s EXIT=%s\n" "$(basename "$g")" "$rc"
done

# ── DDL 逐域（裸跑只查 users 域！）────────────────────────────────
python3 tools/difftest/check-ddl-static.py --domain <域>     # 另有 --indexes

# ── 双侧对拍（按域）─────────────────────────────────────────────
GO_REPO="/Users/pr/Documents/java&golang/Sea-RideTheWind_golang" bash tools/errmsg-probe/run-parity.sh   # article 错误码
bash tools/errmsg-probe/favorite/run-parity.sh                 # favorite 错误码
bash tools/semantic-probe/run-parity.sh                        # message 解析语义
bash tools/json-body-probe/run-parity.sh                        # hot JSON body
bash tools/breaker-probe/run-parity.sh                          # service/common 熔断
bash tools/difftest/comment/run_diff.sh                         # comment 23 步矩阵
bash tools/difftest/run.sh                                     # users + message 双侧
bash tools/api-envelope-probe/favorite/run.sh                  # favorite 响应字节
bash tools/proto-wire-probe/favorite/run.sh                    # favorite wire 字节

# ── 域测试必须带 -am（否则解析 ~/.m2 过期 com.rag:common ⇒ 假红）──
mvn -B -o -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end -pl <mods> -am test
```

---

## 九、本文件与上游文件的关系（一句话版）

```
docs/PhasedGoal.md  §二  ─┐
                          ├─→ 准入线（语言描述）──→ 本文件 §一~§五（可判定矩阵）──→ §五.3 四种结论
docs/twoStage.md    §2.1 ─┘                                        ↑
                                                                   │
docs/twoStage.md    §8.1（E0–E5）───────────────────────────────→ §1.2
docs/twoStage.md    §5.3（差异与暂缓）─────────────────────────→ §5.5
docs/twoStage.md    §9  （故障矩阵）────────────────────────────→ 本文件不覆盖（阶段 2）
docs/twoStage.md    §10 （阶段归属 A/B/C/D）────────────────────→ §5.4 / §7 的「是否阻断」列
```

> ⚠️ **本文件不替代任何一份上游文件。** 若本文件与 `docs/PhasedGoal.md` / `docs/twoStage.md` 冲突，
> **以上游为准**（准入线以 `PhasedGoal.md` §二 / `twoStage.md` §2.1 为准），并把冲突登记为一条待修项。
