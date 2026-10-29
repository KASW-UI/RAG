# errmsg-probe · `message` 域：Go ↔ Java 错误码契约双侧对拍

> AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在 **message 域错误码契约层**的落地实现。
> 定位是【回归守门】：任何人改了 message 的码值 / 文案 / 未知码回落语义 / wire 形态 /
> 业务码反查语义，本脚本立刻 **exit 1**。

---

## 一、真源与被测

| 项 | 值 |
|---|---|
| **Go 真源** | `Sea-RideTheWind_golang/service/message/common/errmsg.go`（package `common`，133 行，**冻结只读**） |
| **真源 sha256** | `e7c19922dea374454db9692abc86146777fa7222b5d8239c20c903da875ab730`（`run-parity.sh` 每次跑都会重新算并与复制件比对，**不一致 ⇒ exit 2**） |
| **RAG 被测模块** | `service/message/message-common`（mvn `-pl` 路径） |
| **RAG 被测类** | `com.rag.message.message.common.errmsg.MessageErrorCode`（枚举 20 码，`:25-90`）<br>`…MessageErrorMessages`（码→文案表 20 条 + `getMsg`，`:31-92`；`MSG_BY_CODE` `:48`，**包级可见**）<br>`…MessageGrpcError`（gRPC 双向转换 + 内嵌 `CodeError`，`:45-326`；`CodeError` `:78`、`newCode` `:112`、`newCodeMsg` `:120`、`grpcError` `:135`、`BIZ_CODE_BY_MSG` `:185`、`bizCodeFromError` `:217`、`extractStatus` `:300`、`mapStatusCodeToBizCode` `:255`） |

⚠️ **真源路径以实际代码为准**：message 域是 `service/message/common/errmsg.go`（**没有** `errmsg/` 子目录）。

⚠️ **Java 探针放在 `com.rag.message.message.common.errmsg` 包内**（不是默认包）：
`MessageErrorMessages.MSG_BY_CODE` 是**包级可见**（对应 Go 的包级私有 `var codeMsg`）
⇒ 同包才能读它做「枚举码集合 ≡ 文案表键集合」的自检。

---

## 二、怎么跑（复现命令）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-a
bash tools/errmsg-probe/message/run-parity.sh    # ⇒ exit 0
bash tools/errmsg-probe/message/mutate-parity.sh  # ⇒ exit 0（内部 4 条变异全部精确转红）
```

退出码：`0` = 双侧逐字节一致 · `1` = 有真漂移 · `2` = 环境问题（输入不可见 / 探针跑不起来）。

Go 真源默认**向上逐层查找**（worktree 在 `RAG.worktrees/<lane>/`，深 2 层 ⇒ 写死「同级兄弟」会指错）；
可用 `GO_REPO=/path/to/Sea-RideTheWind_golang` 覆盖。

依赖：`mvn`（离线 `-o`）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache（需含 grpc v1.81.1）。
**不需要** PG / Kafka / Redis / 任何容器。

---

## 三、对拍了哪几层

输出格式（两侧必须逐字节相同）：

```
CONST|<code>                                          20 条
TABLE|<code>|<msg>                                    20 条
FALLBACK|<code>|<msg>                                 17 条
CODEERR|<code>|<errText>                              20 条
WIRE|<bizCode>|<grpcCode>|<desc>                      20 条
WIREX|<grpcCode>|<bizCode>|<grpcCode>|<desc>          24 条（8 个 grpc 码 × 3 个业务码）
BIZCODE|<case>|<code>                                 23 条
OBS|<case>|<value>                                     3 条
```

| 层 | 判据 | Go 出处（**消费点**） | RAG 出处 |
|---|---|---|---|
| **① CONST** | `const` 块登记的码值集合逐条相等 | `errmsg.go:27-49` | `MessageErrorCode.java:28-90` |
| **② TABLE** | 每个码的文案逐字节相等（本域**整表英文**） | `errmsg.go:51-73` `codeMsg` + `:88-93` `GetErrMsg` | `MessageErrorMessages.java:53-74` + `:86-92` |
| **③ FALLBACK** | 未登记码 ⇒ 两侧**同样**回落到 `"FAIL"`（码值 + 文案都比） | `errmsg.go:88-93` `if msg, ok := codeMsg[code]; ok { return msg }; return codeMsg[Error]` | `MessageErrorMessages.java:86-92` |
| **④ CODEERR** | `NewErrCode(code)` 的 `Error()` 文本逐字节相等 | `errmsg.go:15-17` + `:19-21` | `MessageGrpcError.java:86` + `:112-114` |
| **⑤ WIRE** | 每个业务码经 `GRPCError` 构造出的 wire 形态：**业务码在 description、不在 grpc-status** | `errmsg.go:95-97` `status.Error(grpcCode, GetErrMsg(bizCode))` | `MessageGrpcError.java:135-139` |
| **⑥ WIREX** | 8 个 grpc 码 × 3 个业务码的 wire 矩阵（grpc 码**原样透传** + description = 表文案） | `errmsg.go:95-97` | `MessageGrpcError.java:135-139` |
| **⑦ BIZCODE** | `BizCodeFromError` 的还原矩阵 23 例（含「文案反查优先于 gRPC 码类别」） | `errmsg.go:99-132` | `MessageGrpcError.java:217-248` + `:255-272` |
| **⑧ OBS** | 3 条**必然不同**的实现层差异（见 §五） | `errmsg.go:99-132` | `MessageGrpcError.java:217-248` |

⚠️ **gRPC 码名归一**：Go 的 `grpcodes.Code.String()` 是 CamelCase（`InvalidArgument`），
Java 的 `Status.Code.name()` 是 UPPER_SNAKE（`INVALID_ARGUMENT`）⇒ 两侧统一到
**grpc 官方 canonical 形态**（小写 snake_case，module cache
`google.golang.org/grpc@v1.81.1/codes/code_string.go` 的 `canonicalString`）才可比。

### ⚠️ message 域与 follow 域**故意不同**的一条语义（本探针专门钉它）

| 域 | Go 的判定顺序 | RAG 的判定顺序 |
|---|---|---|
| **message** | `errmsg.go:104-106`：**先** `err.(*CodeError)` → **后** `status.FromError(err)` | `MessageGrpcError.java:230`：`err instanceof CodeError` **在先** ⇒ 照 Go 排 |
| **follow** | `errmsg.go:106-112`：**先** `status.FromError(err)` → **后** `err.(*CodeError)` | `FollowGrpcError.java:161-169`：**先** `resolveStatus` ⇒ 照 Go 排 |

⇒ **两个域的探针是分开的、各自照各自的真源排**（把 message 的顺序改成 follow 那样 ⇒ 本探针立刻转红）。
⇒ 这条顺序在 message 域是 `DRIFT-MSG-COMMON-002` 修过的（`docs/DRIFT/message/common.md` §3），
修的理由写进了生产 javadoc（`MessageGrpcError.java:222-229`：「照 Go 排，别再对调」）。

---

## 四、CASES（17 个回落样本）为什么这么选

⚠️ 原则：**必须是本域未登记、但在别的域真实存在**的码 ⇒ 才测得到「回落」这条语义。

| 样本 | 在别的域是什么 | 为什么选它 |
|---|---|---|
| `0` / `-1` / `1` / `999` | 通用边界 | 经典哨兵值 + 非业务码 |
| `1001` | comment / users：用户名已存在 | 跨域撞车（低号段） |
| `1012` / `1013` | comment：评论区不存在 / 评论不存在 | 跨域撞车，**紧邻** message 的 1015 |
| `2001` | comment：输入有误 | 跨域撞车（另一个号段） |
| `3001` | users：积分余量不足 · like：构建 outbox 失败 | 跨域撞车 |
| `5009` / `5010` | comment：Kafka 消息发送失败 / 延时消息发送失败 | 跨域撞车，**紧邻** message 的 5008 ⇒ 上边界外侧 |
| `5012` | hot / users 号段 | 号段边界 |
| `5208` | message **自己**的相邻空位（5201-5207 已登记） | 本域内部的边界样本：域内相邻码最容易漏登记 |
| `5301` / `5304` | favorite：收藏记录不存在 / 收藏暂不可撤回 | 跨域撞车 |
| `9999` | 无归属的高位码 | 「随便一个未登记码」 |
| `2147483647` / `-2147483648` | int32 边界 | ⚠️ **Go 的 `int` 是 64 位、Java 的 `int` 是 32 位** ⇒ 只用两侧都放得下的值 |

⚠️ 本域已登记集合 = `{200, 500, 1015, 5001~5008, 5101, 5102, 5201~5207}` ⇒ 上表**没有一个**落在里面。

---

## 五、OBS 段（3 条 · 两侧**必然不同**的已知不可达差异）

⚠️ OBS 段的语义是「**断言差异仍然存在**」：
若哪天两侧变得一致 ⇒ 脚本 **exit 2** 提示「差异已消失，请重新评估并更新台账」。

| case | Go 值 | Java 值 | 为什么两侧必然不同 | 出处 |
|---|---|---|---|---|
| `obs_ok_status` | `200` | `5001` | Go 的 `status.Error(codes.OK, msg)` **返回 nil**（grpc-go `internal/status/status.go`：`func (s *Status) Err() error { if s.Code() == codes.OK { return nil } }`）⇒ `BizCodeFromError(nil)` ⇒ `Success`(200)；Java 侧 `Status.fromCode(OK)...asException()` 造出来的是一个 **code=OK 的非 nil `StatusException`** ⇒ 走反查/兜底 ⇒ 5001。**「OK 状态不是错误」是 grpc-go 的 API 形态，grpc-java 没有对应的塌缩** | Go `errmsg.go:100-102`；Java `MessageGrpcError.java:218-220` |
| `obs_custom_grpcstatus` | `5101` | `1015` | grpc-go 的 `status.FromError` 认「**顶层实现了 `GRPCStatus() *Status` 的任意 error**」（module cache `google.golang.org/grpc@v1.81.1/status/status.go` 的 `type grpcstatus interface{ GRPCStatus() *Status }` 顶层断言）⇒ 文案反查命中 ⇒ 5101；Java 侧 `extractStatus` **只认** `StatusException` / `StatusRuntimeException` 两个具体类 ⇒ 认不出 ⇒ 兜底 1015 | Go `errmsg.go:108`；Java `MessageGrpcError.java:300-314`（`extractStatus` 的两个 `instanceof`） |
| `obs_wrapped_outer_msg` | `5002` | `5101` | Go 的 `fmt.Errorf("%w")` 的 `Error()` = `"外层文本: 内层文本"`，内层是 status error 时内层文本恒为 `"rpc error: code = ... desc = ..."` ⇒ **文案反查必然落空** ⇒ switch 兜底 5002；Java 的 `Throwable#getMessage()` **只有外层文本** ⇒ 反查**能命中** ⇒ 5101。**这是 Go 的 `fmt.Errorf` 文本格式 vs Java 的 `getMessage()` 的结构性差异** | Go `errmsg.go:108` + grpc-go `status/status.go` 的 `p.Message = err.Error()`；Java `MessageGrpcError.java:236-239` + `:320-323`（**生产 javadoc 已自登记为「忠实复刻 `p.Message = err.Error()`」的代价**） |

⚠️ **这三条全部是「构造出来的不可达形态」**：
message 域的真实链路恒走「顶层直接命中」分支 ⇒ **不构成行为漂移**，但必须钉死。

---

## 六、白名单（已签字偏离）

⚠️ **message 域是三个域里唯一有已签字的错误码相关偏离的** ⇒ 逐条列出，并写清**它对本探针到底意味着什么**。

| 编号 | 为什么它是签字偏离 | 出处 | 本探针的处置 |
|---|---|---|---|
| **`SD-2026-10-24-03`**<br>嵌在 cause 链里的 `CodeError`：Go `errors.As` **穿透** / RAG `instanceof` **不穿透** | 2026-10-24 用户拍板「签字留档，不改代码」。**类别 = 错误传播（latent）**。签字的那处差异在 **api 的全局错误处理器**：Go `service/message/api/messagecenter.go:57` 的 `errors.As`（穿透）↔ RAG `message-api` 的 `MessageApiSupport.java:140`（走 `bizCodeFromError`，只认最外层） | `docs/Intentional_Deviations/message/rpc.md:62-74`（尤其 `:71` 的「① `CodeError` 支 …… **两侧一致 ✓**；② `status` 支 …… **两侧一致 ✓**」） | **不豁免** —— 签字的那处差异**不在**本探针覆盖的 `message-common` 层，而在 `message-api` 层。SD 自己已明确记载 **message-common 的两支两侧都一致** ⇒ 本探针**反而用 `code_error_wrapped` 用例把它钉死**（两侧都判 1015）：若将来有人把 `bizCodeFromError` 改成穿透 ⇒ 本探针**立即转红**。⚠️ **列入本表是为了让 reviewer 知道「错误码相关已签字偏离存在、且不产生探针豁免需求」**，不是把它当成一条被放过的差异 |
| **`DRIFT-MSG-V-003` 收窄后保留的那一半**<br>envelope **不回显原始错误文本**（信息泄露防护） | 原签字「DB/内部故障业务码 Go `1015` → RAG `5001`/`5008`」于 2026-10-24 第八轮**收窄**：业务码那一半**已作废**（两侧都是 1015，已对齐），**只剩「RAG 的 envelope 只回码表文案（`service busy`）、不回显 gRPC description 原文（SQL 错误文本）」这一半继续签字** | `docs/Intentional_Deviations/message/rpc.md:20-26` + `:155-172`（`:159` 明确「业务码这一半**作废**」） | **不涉及、不豁免** —— 组件是 **`message-api` 的 envelope 装配**（Go `service/message/api/internal/logic/shared/helper.go:27-36` 的 `RPCError` 把 `st.Message()` 原文塞进 envelope），**不在 `message-common`**。本探针的 WIRE 层验的是 message-common **自己**的 `GRPCError` 构造（description = 码表文案）⇒ 两侧一致 ✓；⚠️ 探针**不覆盖** api 层的 envelope 装配 ⇒ 该签字项在本探针里**没有对应面** |

⚠️ **其余 message 域的签字条目一律不进白名单**（逐条理由，不做「整域豁免」）：
`SD-2026-10-24-01`（两个索引的**名字**）· `-02`（直方图**桶边界**）· `-04`（6 个时间列的 `DEFAULT CURRENT_TIMESTAMP`）·
`SD-2026-10-23-06`（`idx_notification_recipient_read` 的**列数**）· `-08`（CPU 采样 **fail-open**）
—— **全部与错误码契约层无关**。

⚠️ §五 的 3 条 OBS 是**跨语言 / 平台 API 形态差异**，登记位置是
`docs/DRIFT/message/common.md`（§3 FIX-MSG-COMMON-001 的「`p.Message = err.Error()`」复刻说明）
+ `MessageGrpcError#extractStatus` 的 javadoc（`:277-294`），
**不是** `Intentional_Deviations` 里的签字偏离 ⇒ 走 **OBS 段**（单列 + 差异消失即 exit 2），
**不混进**「已签字白名单」。

---

## 七、变异测试（证明本探针**不是假绿**）

```bash
bash tools/errmsg-probe/message/mutate-parity.sh
```

⚠️ **变异只发生在临时副本上，仓内 `service/**` 与 `tools/errmsg-probe/message/**` 一个字都没改**
（与 `tools/errmsg-probe/hot/mutate-parity.sh` 的「原地改生产代码再还原」不同 ——
原地改即使还原也留下窗口期，且本 lane 的写权不允许改 `service/**`）。

| # | 变异 | 手法 | 观测 |
|---|---|---|---|
| **M1** | **Go 侧复制件**文案漂移 | 造一个「假真源仓」（`go.mod`/`go.sum` 原样 + **变异版** `errmsg.go`），用 `GO_REPO` 指过去 | ✅ 精确转红（exit 1） |
| **M2** | **Java 侧源码副本**文案漂移 | 复制模块源码到临时目录 → 改文案 → `javac` 编副本 → `PROBE_JAVA_CLASSES` 指过去 | ✅ 精确转红（exit 1） |
| **M3** | **输入根探针**硬失败 | `GOMODCACHE` 指向空目录 | ✅ 精确转红（exit 2 · 不得静默降级） |
| **M4** | 还原后 | 真源 + 生产 classpath | ✅ 重新转绿（exit 0） |

### M1 · Go 侧复制件（实测输出）

变异内容：`"database query failed"` → `"database query failed."`

```
变异前 sha256（真源）   = e7c19922dea374454db9692abc86146777fa7222b5d8239c20c903da875ab730
变异后 sha256（副本）   = 57232973c7e19268b6248a2b7f87952dbc9429b9068959988f04b6774688aad1
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-TABLE|5007|database query failed.
+TABLE|5007|database query failed
-CODEERR|5007|ErrCode:5007, Errmsg:database query failed.
+CODEERR|5007|ErrCode:5007, Errmsg:database query failed
-WIRE|5007|internal|database query failed.
+WIRE|5007|internal|database query failed
```

### M2 · Java 侧源码副本（实测输出）

```
变异前 sha256（生产源码） = 4ca42a9aa80b9c7c4290b52dc8d71d77489cd23b629c586dc24a74f32b9e8807
变异后 sha256（临时副本） = 5113c25386a56b466d55ad8238c543643217b475a3cad919c5ec3036d8855c2b
⚠️ 生产源码未被触碰（变异只发生在 <临时目录>/mut-src）
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-TABLE|5007|database query failed
+TABLE|5007|database query failed.
-CODEERR|5007|ErrCode:5007, Errmsg:database query failed
+CODEERR|5007|ErrCode:5007, Errmsg:database query failed.
-WIRE|5007|internal|database query failed
+WIRE|5007|internal|database query failed.
```

---

## 八、已知边界（**不覆盖**什么）

1. **只覆盖 `message-common` 的错误码契约层**。HTTP envelope 形状、gRPC 服务名、DDL 均**不覆盖**
   ⇒ §六 的两条签字偏离（`SD-2026-10-24-03` 的 api 层那一半、V-003 的 envelope 那一半）
   **在本探针里没有对应面**。
2. **只覆盖「两侧同时跑」的静态对照** ⇒ 证据强度是 **L1/L2**，**不是**双侧活体对拍。
   「跑绿」应读作「**在上述证据强度下一致**」，不是「已闭环」。
   （`docs/DRIFT/message/common.md` §4 声明的证据强度也是「JVM 内单元测试 + Go 源码逐行 + grpc-go 源码逐行」，
   **不是**双侧活体对拍 —— 本探针把这个缺口补到了**可复算的仓内载体**上。）
3. **OBS 段的三条是「不可达形态」**（见 §五 末段）⇒ 不构成行为漂移，但已钉死。
4. **探针不校验「RAG 生产代码是否真的调用了 `grpcError`/`bizCodeFromError`**」** ——
   它验的是**契约层**（这两个方法本身的行为），调用点覆盖度由各域台账负责。
5. **`PROBE_JAVA_CLASSES` 是变异测试专用的注入点**（`run-parity.sh` 会把它原样打印出来），
   默认走**生产编译产物** `service/message/message-common/target/classes`。
6. ⚠️ **Go 侧 `message/rpc` 全域零调用 `service/message/common`**（`docs/DRIFT/message/common.md` §1）
   ⇒ 本探针验的是**共享契约组件**，不是某条 RPC 链路的实测。
