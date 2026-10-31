<!-- @new 2026-10-24 · comment 域 common 组件首轮 Go ↔ RAG 语义对照 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# DRIFT 台账 · `service/comment/comment-common`

> **核对时间：2026-10-24**
> **真源：** `Sea-RideTheWind_golang/service/comment/common/errmsg/errmsg.go`（103 行）
> **本轮性质：** 首轮**组件级**语义对照（AGENTS.md §3.5）。此前本组件**零台账**。
>
> ⚠️ **证据强度**：
> - ✅ **真构建 + 真测试**：`mvn -o -pl service/comment/comment-common -am test` ⇒ **27 / 0 failures / 0 errors / 0 skipped**（本轮实测）
> - ✅ **源码逐行对照**：Go 侧 1 个文件（103 行）逐行读过
> - ✅ **双侧活体对拍**（**2026-10-24 补上**）：错误码链路被 23 步矩阵**端到端覆盖** ——
>   业务码 1012 / 1013 / 1008 / 2001 在**两侧进程同时在跑**时逐条一致（见 [`api.md`](./api.md) §4.6.1）
>
> ⇒ ✅ 一律读作「**在该证据强度下一致**」。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | ✅ **`SEMANTICALLY EQUIVALENT`**（错误码契约层） |
| SEMANTIC MISMATCH | **0 条** |
| OBSERVABLE DIFFERENCE | **1 条**（§3） |
| 等价确认 | **约 25 个判定点**（§2） |
| 组件规模 | Go 103 行 / 1 文件 ⇒ RAG 537 行 / 4 类 |
| 构建 + 测试 | **BUILD SUCCESS**，**27 / 0 / 0 / 0 skipped**（本轮实测）｜ 🟢 **错误码契约的双侧活体对拍 ＋ 变异验证** ⇒ 见 **§6** |

> ✅ **本组件是 comment 域唯一「逐条对齐」无漂移的组件**（错误码契约层）。
> ⚠️ **但它有一处「自身正确、组合后失效」的问题** —— 见 §3-D1 与
> [`docs/DRIFT/comment/api.md`](./api.md) 的 MISMATCH-API-01。

---

## 1. 落地形态

| Go 侧 | RAG 侧 |
|---|---|
| `common/errmsg/errmsg.go`：**1 个文件**，`CodeError` + 27 个 const + 27 条 `codeMsg` + `GetErrMsg` | **4 个类**：`CommentErrorCode`（枚举）+ `CommentErrorMessages`（查表）+ `CommentCodeError`（载体）+ `CommentGrpcError`（gRPC 出口） |

拆分理由：Go 的 1 个文件承担 4 个职责（码值常量 / 文案表 / 错误载体 / gRPC 出口），
RAG 侧按职责拆开是**表达层**重组，行为层逐条对齐（§2）。

---

## 2. 等价确认（逐条）

### 2.1 码值契约（27 条）

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 码值**总数 = 27** | `errmsg.go:37-65` const 块逐条数 | `CommentErrorCode.java` 枚举 27 个 | ✅ |
| 码值**逐条相等** | `errmsg.go:38-64` | `CommentErrorCode.java:33-104` | ✅ 全量核对，无一不同 |
| **声明顺序 = Go 的书写顺序** | `errmsg.go:38-64` | `CommentErrorCode.java:33-104` | ✅ 由 `CommentErrorCodeContractTest#everyCodeMatchesGoOneByOne` 锁死 |
| 通用段（200 / 500 / 1015） | `errmsg.go:38-40` | `:33-37` | ✅ |
| 系统与基础设施段（5001–5010） | `errmsg.go:41-50` | `:42-61` | ✅ |
| 用户/令牌段（1001–1011） | `errmsg.go:51-61` | `:65-88` | ✅ |
| comment 专属段（1012 / 1013） | `errmsg.go:62-63` | `:93-97` | ✅ 两码**未与 like/message 域合并** |
| 输入校验段（2001） | `errmsg.go:64` | `:104` | ✅ |

### 2.2 文案表（27 条）

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 文案**逐条相等** | `errmsg.go:68-94` `codeMsg` | `CommentErrorMessages.java:43-74` | ✅ 全量核对 |
| `ErrorServerCommon` = 「系统内部错误」 | `:71` | `:47` | ✅ |
| `ErrorDbSelect` = 「**查询**数据库失败」 | `:73` | `:49` | ✅ 注意：logic 层传的是「DB查询失败」，**两者不同**，两侧都保留了这种差值 |
| `ErrorSubjectNotExist` = 「评论区不存在」 | `:92` | `:64`（见 `CommentErrorMessages`） | ✅ |
| `ErrorCommentNotExist` = 「评论不存在」 | `:93` | `:65` | ✅ |
| `ErrorInputWrong` = 「输入有误」 | `:94` | `:66` | ✅ |
| **写入顺序 = Go 的 `codeMsg` 书写顺序**（`LinkedHashMap`） | `errmsg.go:68-94` | `CommentErrorMessages.java:40-` `LinkedHashMap` | ✅ 便于逐行 review |

### 2.3 未知码回落

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 未知码回落到 **`"FAIL"`**（不是空串、不是码本身） | `errmsg.go:97-103` `if !ok { return codeMsg[Error] }` | `CommentErrorMessages.getMsg(int)` | ✅ |
| 回落值必须是 `"FAIL"` 而非 `"系统内部错误"` | `errmsg.go:70` `Error: "FAIL"` | `:44` `ERROR → "FAIL"` | ✅ 由 `CommentErrorMessagesContractTest` 锁死 |
| 回退**可观察**的理由 | comment-api 的 `commentservice.go:51-58` ErrorHandler 在错误**不是** `*CodeError` 时用 `GetErrMsg(ErrorServerCommon)` 兜底 | `CommentGlobalExceptionHandler` 对应分支 | ✅ 机制对齐 |

### 2.4 `CodeError` 载体

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| JSON 只有 `code` / `msg` **两个字段** | `errmsg.go:10-13` `Code int \`json:"code"\`` / `Msg string \`json:"msg"\`` | `CommentCodeError.java:92-100` 两个 `@JsonProperty` | ✅ |
| **`Error()` 是方法不是字段** ⇒ 不出现在 JSON 里 | `errmsg.go:15-17` | `CommentCodeError.java:46-47` `@JsonAutoDetect(getterVisibility = NONE, isGetterVisibility = NONE)` 关掉继承的 getter | ✅ **不多吐** `message`/`cause`/`stackTrace` |
| `Error()` 格式 = `ErrCode:%d, Errmsg:%s` | `errmsg.go:16` | `CommentCodeError.java:60` | ✅ 逐字一致（运维 grep 依赖） |
| `NewErrCode(code)` ⇒ msg **走查表** | `errmsg.go:19-24` | `CommentCodeError.newCode(int)` `:66-68` | ✅ |
| `NewErrCodeMsg(code, msg)` ⇒ msg **用传入值** | `errmsg.go:26-31` | `CommentCodeError.newCodeMsg(int, String)` `:71-73` | ✅ |
| **`errors.As` 沿 `Unwrap()` 链回溯** | Go `errors.As` 语义 | `CommentCodeError.findIn(Throwable)` `:83-90` 逐链回溯 | ✅ **api 层必须用它**，不能用 `catch (CommentCodeError e)` —— 后者会把包过一层的业务错误误降级成 5001 |

### 2.5 gRPC 出口

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| `status.Error(codes.Code(code), msg)` ⇒ **业务码原样进 `grpc-status`** | `errmsg.go:33-35` | `CommentGrpcError.grpcError(int, String)` `:128-134` | ⚠️ **见 §3-D1** —— RAG 侧改成 trailer 了 |
| 未知码回落到 `Error`（500）的文案 | `errmsg.go:99-101` | `CommentErrorMessages.getMsg` | ✅ |

---

## 3. OBSERVABLE DIFFERENCE（1 条）

### D1 · 业务码的**传输通道**不同：Go 走 `grpc-status`，RAG 走自定义 trailer

| 项 | 值 |
|---|---|
| **Go 事实** | `errmsg.go:33-35` `NewGrpcErr(code, msg)` = `status.Error(codes.Code(code), msg)` ⇒ 业务码（1012/1013/1008/5003/2001…）**原样写进 `grpc-status`** ⇒ api 侧 `int(st.Code())` 字面取回 |
| **RAG 现状（⚠️ 本行是**修复前**的形态，2026-10-24 已修 ⇒ **别把它读成「仍断」** ⇒ 见下一行）** | `CommentGrpcError.java:105-110` `mapToGrpcCode` 只把 2001 映到 `INVALID_ARGUMENT(3)`，其余全部 `UNKNOWN(2)`；业务码改放 **自定义 trailer** `rag-comment-biz-code`（`:72`、`:130`） |
| **为何仍判「等价」** | `CommentGrpcError.bizCodeFromError`（`:168-200`）**能**从 trailer 无损取回业务码 ⇒ 契约**在 comment-common 内部是闭合的**。`CommentGrpcErrorTest.java:112-114` 已有断言锁住这一点 |
| **🟢 通道现状（2026-10-24 更新）** | **已修复并活体验证**：MISMATCH-API-01 已修 —— `comment-api` 的 `CommentApiSupport.rpcError` 改为调用 `bizCodeFromError` ⇒ **通道在真实链路上已闭合**；对拍矩阵实测 **1012 / 1013 / 1008 / 2001 在两侧进程同时在跑时逐条一致**（见 [`api.md`](./api.md) §4.6.1）⇒ **本条不再是「断的通道」** |
| **残留面（仅跨栈）** | ⚠️ **方向不对称**：**Go-rpc → RAG-api** 的业务码**能**捞回（grpc-java 降级成 `UNKNOWN + "Unknown code 1012"` ⇒ 第①优先级捞文本）；**RAG-rpc → Go-api** 的业务码**传不过去**（Go 只读 grpc-status，看不见 trailer）⇒ 详见 [`proto.md`](./proto.md) §3.1 |
| **归属** | **缺陷记在 `comment-api`**（[`docs/DRIFT/comment/api.md`](./api.md) 的 MISMATCH-API-01，**已修**），本条只登记「通道不同」这一事实 |
| **风险提示** | `CommentGrpcError.java:150-162` 的 javadoc 论证「标准码走原值而不是语义映射，因为 Go 是字面搬运」—— 这段论证**只对 Go-rpc 对端成立**；对 **RAG-rpc 对端**，Go 的「字面搬运」对象是**业务码**（因为 Go 侧业务码就在 `grpc-status` 里）⇒ 两边的「字面搬运」实际搬的不是同一个东西 |

---

## 4. 未决 / 待办

| # | 事项 | 类型 |
|---|---|---|
| **U1** | ~~D1 的修复点不在本组件~~ ⇒ ✅ **已闭环**（2026-10-24：修复在 `comment-api` 的 `CommentApiSupport.rpcError`，**已完成**；本组件**无需改代码**） | ✅ 跨组件闭环 |
| **U2** | ~~`CommentGrpcError.java` 那段「若未来 comment-api 引入熔断器」的注释已过时~~ | ✅ **已订正**（2026-10-24）：熔断**早已引入**（`GoStyleGrpcClientBreakerInterceptor` + `rag.protection.breaker.enabled: true`），且**两侧失败名单逐条一致**（Go `codes/accept.go` 的 6 个码 = RAG `isAcceptable` 的 6 个码）⇒ 业务错误**不计入**熔断失败 ⇒ 原文担心的「Java 侧更容易打开熔断」**不成立**；并补上「该一致性是当前成立、不是结构保证」的提醒 |
| **U3** | ~~`CommentGrpcError.java` 的「字面搬运」论证需补「对 RAG-rpc 对端应读 trailer」~~ | ✅ **已订正**（2026-10-24）：补上「该论证**只对 Go-rpc 对端成立**」+ **方向不对称**表（RAG-rpc 对端必须先读 trailer，否则业务码整个丢掉） |

---

## 5. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/DRIFT/comment/api.md`](./api.md) | D1 的缺陷落点 |
| [`docs/DRIFT/comment/rpc.md`](./rpc.md) | `comment-rpc` 的独立台账 |
| [`docs/DRIFT/comment/proto.md`](./proto.md) | `comment-proto` 的独立台账 |
---

## 6. 错误码契约的双侧活体对拍 ＋ 变异验证（2026-10-31 · G3 第二十二轮）

| 项 | 值 |
|---|---|
| **探针** | `tools/errmsg-probe/comment/{run-parity.sh,mutate-parity.sh,go/,java/,README.md}`（**域内落码** ⇒ `git ls-files` 命中 ⇒ 在版本控制内、现在可复算） |
| **对拍面** | Go 真源 `service/comment/common/errmsg/errmsg.go`（package `errmsg` · 103 行 · **只读**）↔ RAG `service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/{CommentErrorCode,CommentErrorMessages,CommentCodeError,CommentGrpcError}.java` ⇒ 覆盖 **码值 27 条 / 文案 27 条 / 未知码回落 / wire 形态 / 业务码反查矩阵** |
| **本轮实测（2026-10-31 · G3 第二十二轮 · 独立复跑）** | `bash tools/errmsg-probe/comment/run-parity.sh` ⇒ **EXIT=0** —— Go sha256 = Java sha256 = **`514e3a7106ea6ffc4bf8d60d285ccdb8decb0829729a922c0416362ccaf73e3e`**（**137 行** = CONST + TABLE + FALLBACK + CODEERR + CODEERRMSG + WIRE）⇒ **双侧逐字节一致**；⚠️ **OBS 段 3 条仍在登记状态**（`obs_wire_grpcstatus_1012` / `2001` / `5003` = 两侧**必然不同**的已知不可达差异 ⇒ 单列不参与逐字节对拍 ⇒ 见 §3-D1） |
| **变异验证（探针非假绿）** | `bash tools/errmsg-probe/comment/mutate-parity.sh` ⇒ **EXIT=0** —— **M1** 变异 **Go 侧复制件**（造「假真源仓」⇒ 文案 5003 加一个句号）⇒ 探针 **exit 1**；**M2** 变异 **Java 侧源码副本**（生产源码零改动）⇒ **exit 1**；**M3** **输入根探针硬失败**（`GOMODCACHE` 指空目录）⇒ **exit 2**（**不得静默降级成「这条不可核验」**）；**M4** 还原后 **exit 0** ⇒ ⇒ **四段全部精确转红 + 还原后转绿** ⇒ **探针非假绿** |
| **复现命令** | `bash tools/errmsg-probe/comment/run-parity.sh`（⇒ `EXIT=0`）<br>`GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/comment/run-parity.sh`（⇒ `EXIT=0`）<br>`GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/comment/mutate-parity.sh`（⇒ `EXIT=0`） |
| **证据强度** | 🟢 **L4（双侧同时跑）** ⇒ ⇒ judge §1.1 判据 **⑦⑧ 齐**（双侧对拍通过 ＋ 在版本控制内可复算 ＋ 有变异验证记录）⇒ ⇒ **`comment-common` 的 R 判定 R3 → R4**（2026-10-31 · G3 第二十二轮 ⇒ judge **§三 矩阵 A-2** 的 `comment-common` 行已回填） |
| **⚠️ 效力边界（三条）** | ① **变异只在副本上做**（M1 改 Go 侧复制件 / M2 改 Java 侧源码副本）⇒ **生产源码零改动** ⇒ 并发跑批时不会读到坏代码；② **OBS 段 3 条**是**已知不可达差异** ⇒ 本探针**不判它们红**（而差异「消失」时反而报错 ⇒ 逼人更新台账，而不是让它静默漂移）；③ **不覆盖 gRPC 帧 / HTTP2 帧**（需两侧同时起服务）⇒ ⇒ **本探针覆盖的是「错误码契约层」，不是 §3-D1 的「通道差异」** |
