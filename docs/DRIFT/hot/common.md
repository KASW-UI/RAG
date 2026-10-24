<!-- @move AGENTS.md L1343-L1391 · §0 hot-common 双侧对拍 -->

> 🟥 **2026-10-24 第十一轮后回填（归属 + 核对日期 + 变异口径）**：
> 本文件是 hot 域**唯一没有「未闭合差异」的组件**，但**它的头部一度严重过期**：
> 核对日期停在第四轮、变异验证写「7/7」（**第十一轮又补了 4 个** ⇒ 实为 **11/11**）、
> 且**从未标注产出归属**（AGENTS.md §5.3 要求每份域内台账自证归属）。
> ⇒ 本次只补头部与证据强度口径；**正文各轮的原始记录一条未删**。

**归属**：`align/hot` 工作树产出（`RAG.worktrees/hot`）；基线 = `do-my-own-gh` @ `8422116` 之后的分支点。
**核对日期**：2026-10-24（**第十一轮**：hot 域 U9 系统变异扩展到本组件，+4 个变异点）
**当前状态**：✅ **双侧对拍 sha256 逐字节一致**（`f23fe570…`）· 14 条契约测试全绿 ·
**变异验证 11/11 全部变红**（第四轮 7 个 + 第十一轮 4 个）· **无未闭合差异**

### ✅ `service/hot/hot-common`：已落地，Go ↔ Java 双侧对拍 **sha256 逐字节一致**（2026-10-22）

> **⚠️ 复核批注（2026-10-24，第四轮）**：本文件的三处边界里，**边界 #2 与边界 #3 的原结论都已被推翻** ——
> #2 原写「仅日志少段前缀，属表达层，非 L1」⇒ 实测是**日志面整体不等**（级别 + 字段集 + 字段值），**已修**；
> #3 原写「消费侧尚未接线、advice 仍是空骨架」⇒ 实测**早已接线**，是台账没回填，**已更正**。
> ⇒ 本文件**没有**「未闭合的差异」，只有「已修的差异」与「库层差异（待签字）」。
>
> 🟥 **2026-10-24 第十一轮复核**：上面那句「没有未闭合的差异」**在第十一轮仍然成立**
> （第十一轮只是把系统变异扩展到本组件，**未产出新差异**）⇒ 但它的**效力范围仅限本组件**：
> ⚠️ 「库层差异（待签字）」那条（下游 gRPC 错误的 `error_reason` 形态）**已在第六轮签字**
> （`SD-HOT-API-005`，用户拍板「不复刻」）⇒ 它登记在 **hot-api** 档，不是本组件的未闭合项。

> **hot 域唯一的非 api/rpc 组件**，也是 §0「从未验证」清单里 `hot` 一项本次**部分收口**的部分。
> Go 真源只有 1 个文件（`errmsg.go`，121 行），是纯值定义 + 纯函数库：
> **无 DB / 无 RPC / 无 MQ / 无鉴权 / 无事务 / 无缓存** ⇒ L3 技术语义在本组件上**全部为空**，风险集中在 L1。
> 详细推导见 **`FileTree/hot/common.md`**（`api.md` / `rpc.md` 两处已按其要求同步）。

#### 落地了什么：只有 2 个类

| Go 来源 | Java 落点 | 关系 |
|---|---|---|
| 11 个 const（`errmsg.go:33-46`） | `hot-common/errmsg/HotErrorCode`（enum，**声明顺序 = Go 书写顺序**） | ADAPT |
| `codeMsg` 表（`:48-61`） | `hot-common/errmsg/HotErrorMessages`（**包级私有** `LinkedHashMap`） | ADAPT |
| `GetErrMsg`（`:76-82`） | `HotErrorMessages.getMsg(int)`，未知码回退 `"FAIL"` | ADAPT |
| `CodeError` + `NewErrCode` | **REUSE** `com.rag.common.exception.BusinessException(int, String)` | REUSE |
| `NewErrCodeMsg` / `GRPCError` / `BizCodeFromError` / `bizCodeByMsg` | **有意不迁**（零调用点，见下方边界） | — |

⚠️ **落点是 `hot-common` 而非 `hot-api` 的 `service` 包**，三条互相独立的依据：`hot-api/pom.xml` 早已声明对本模块的依赖、`service/hot/pom.xml` 早已把 `hot-common` 列为 `<module>`、§12.5 推荐 + 四域口径统一（users/message/article 三域均用独立 `-common`）。**类数（2）与落点是两个独立决定**——类数由「码 / 文案是否分离」定，落点由上面的三条定。

#### 三条硬约束（错一条就是客户端可见的错码）

1. **hot 必须自持码表，禁止 REUSE 任何既有 `ErrorCode`** —— hot 的 5002/5003/5004 是 `record not found` / `invalid parameter` / `unauthorized`（**英文**），而 users 与 article 的同号码是「更新数据库失败 / 查询数据库失败 / 数据库插入失败·文件上传失败」（**中文**）⇒ **三方两两冲突**，不存在「取一个通用码表」的选项。
2. **与 message 域「巧合相同」不构成复用许可** —— 两域前 11 个码值文案**逐条相同**（同源模板），但它们是两个独立域：message 日后加码或改文案不得波及 hot，反之亦然。
3. **`GetErrMsg` 的未知码回退 `"FAIL"` 本身属 L1** —— Go `httpx.SetErrorHandler`（`hotcenter.go:53-66`）的错误分支正依赖它产出 `code=5001 / msg="internal server error"` ⇒ **不能返回 `null` 或空串**，也不是返回 code 的字符串。

⚠️ 另有一条**前置条件**被测试钉死：**11 条文案必须两两互异**。Go 的 `bizCodeByMsg` 靠「先到先得」处理重复文案，而 Go map 遍历顺序随机 ⇒ 一旦有两条同文案，反查就变成真随机。当前互异让该分支不可达，这是 Go 侧的正确性前提，不是 Go 的缺陷。

#### 验证：4 层，且都不是「绿了就信」

| 层 | 手段 | 结果 |
|---|---|---|
| **双侧对拍** | `go run` 直接跑真源 `sea-try-go/service/hot/common`（探针放 `/tmp`，**不入 Go 仓**）+ Java 探针输出同格式 dump（11 已知码 + 12 未知码） | **sha256 逐字节一致** `f23fe570…` |
| **契约测试** | 14 条（码值 / 顺序 / 唯一性 / 总数 / 文案 / 双射 / 表内顺序 / 未知码回退 / 文案互异 / 英文语种 / 跨域守卫 / 表不可变） | 全绿 |
| **变异验证** | **7/7 全部变红**：回退改 `null`、码值 5002→5009、文案改中文、枚举重排、擅自加码、漏迁码、文案表漏一条 | 守门有效，**非假绿** |
| **变异验证（第十一轮扩展）** | **+4 个 ⇒ 合计 11/11 全部变红**：`HC01` 成功文案 `"OK"`→`"ok"`、`HC02` 未知码回落 `"FAIL"`→`""`、`HC03` 码值 5001→5002、`HC04` 码值 1015→1016 | 守门有效，**非假绿**；本组件**仍无未闭合差异** |
| **编译** | `hot-common` / `hot-api` / `hot-rpc` 三模块 `test-compile` | 全通过 |

⚠️ 其中「**漏迁一个码**」是在**生产代码编译期**就失败（`HotErrorMessages` 引用了已删除的枚举常量），不是断言失败 —— 最强的一档。
⚠️ 本仓已有的反面样本：**`like` 域的 Go 源根本没有 `BizCodeFromError`，却建了 258 行的 `LikeGrpcError`** —— 那份超建不要照抄。

#### ⚠️ 三处边界（不得越读成「hot 域已完成」）

1. **有意不迁的 4 个符号不是遗漏** —— grep 验证它们在 `service/hot` 全域**零调用点**、hot-rpc 对本文件**零引用** ⇒ **它们在 Go 侧本身就是死代码**，迁过来只会制造无调用方的结构。`BizCodeFromError` 的**判定顺序**已完整留档在 `FileTree/hot/common.md §12.3`；日后 hot 若接上 gRPC 消费方，**照该顺序**补 `HotGrpcError`（照 `MessageGrpcError` 的形状），不要临时另写一套。
2. ~~**`CodeError.Error()` 的 `"ErrCode:%d, Errmsg:%s"` 未复刻**~~ —— ✅ **2026-10-24 第四轮：已修**，
   且**原定性被推翻**（原文写「仅日志少段冗余前缀，属表达层，非 L1」——**不准确**）。
   - **复核结论**：Go 侧「hot-rpc 返回空响应」那条日志（`getHotArticlesLogic.go:72-73`）记的是
     **`*CodeError`** ⇒ `error_reason` = `"ErrCode:5001, Errmsg:internal server error"`（`errmsg.go:15-17`）；
     而 RAG 侧当时记的是 **`logInfo`** ⇒ **级别（ERROR vs INFO）、字段集（9 个 vs 0 个）、字段值**三处都不一样。
     ⇒ 这**不是**「少段前缀」，是**日志面整体不等**。
   - **修法**：新增 `HotCodeError`（`getMessage()` 覆写为 Go 的 `Error()` 整串，另开 `getBizMsg()` 给信封）
     + `GlobalExceptionHandler.bizMsgOf` 拆两个面 + nil 响应分支改 `logBusinessErr`。
   - **守门 + 变异**：`HotCodeErrorLogParityTest`（3 例，**真触发**日志链路）+ 3 条变异全红。
   - ⇒ 详见 [`../../DRIFT/hot/api.md`](api.md) 的 **DRIFT-HOTAPI-016**。
   - ⚠️ **同族但未修**的一条：下游 gRPC 调用失败时的 `error_reason` 形态是**各自 gRPC 库**决定的
     （grpc-go `"rpc error: code = Unavailable desc = …"` vs grpc-java `"UNAVAILABLE: …"`）
     ⇒ **库层**差异，hot 域不该手拼 ⇒ 登记为 [`../../DRIFT/hot/api.md`](api.md) 的 **DRIFT-HOTAPI-017**（待签字）。
   - ⚠️ **HTTP envelope 仍完全一致**（Go 是**逐字段复制** `codeErr.Code`/`codeErr.Msg`，不是 `Error()` 整串）
     ⇒ 修日志**不得**动信封，这条已由 `HotCodeErrorLogParityTest#envelopeKeepsPlainBusinessMessage` 钉死。
3. ~~**消费侧尚未接线**~~ —— ✅ **2026-10-24 复核：这条已过时，事实相反**。
   实测（`grep -rn "HotErrorCode\|HotErrorMessages" service/hot/`）：**hot-api 已经全面接线**——
   `advice/GlobalExceptionHandler.java`（`serverCommon()` / `envelope()` 两处取码表）、
   `advice/GlobalResponseAdvice.java`（成功信封取 `SUCCESS` 码 + 文案）、
   `form/GoFormValues.java`（query 解析失败构造 `BusinessException`）。
   ⚠️ **本条原记「hot-api 的 advice 仍是空骨架」「全仓无任何代码引用这两个类」是错的**，
   与 `DRIFT-HOTAPI-012`（2048 上限早已实现）是**同一类错**（台账没回填），已就地更正。
   - **接线等价式（已落地）**：`new BusinessException(HotErrorCode.X.getCode(), HotErrorMessages.getMsg(code))`
     ≡ Go 的 `hotcommon.NewErrCode(...)`（`errmsg.go:19-24`：`Code=code`、`Msg=GetErrMsg(code)`）。
   - **MVC 管线层已钉住 envelope 的 code/msg**（原「下一步①」的要求已满足）：
     `HotApiMvcPipelineTest` 的 `successEnvelopeIsGoShaped` / `errorEnvelopeIsNotDoubleWrapped` /
     `queryParseFailureMapsToTwoHundredWithFiveThousandAndOne` 三条**走真实 MVC 管线**断言信封；
     `HotArticleQueryServiceTest` 另断言异常侧的 `code` / `message` 取自本码表。

⚠️ **`hot-common` 没有 `HUMAN_ONLY.MD`，且不应补** —— 全仓仅 4 份、全在 `service/users/` 下；`-common` 模块族（users-common / message-common / article-common / like-common）**从来没有过**这份文档。

**下一步（hot 域）**：① ~~hot-api 的 advice 与 service 层落地时会首次真正调用本码表，届时**必须补一条 MVC 管线测试断言 envelope 的 code/msg**~~ → ✅ **2026-10-24 已完成**（接线 + MVC 管线断言都已落地，见上方边界 #3）；② hot-rpc 零引用本模块，**刻意不依赖**，改判时需同步其 pom（**状态不变，仍成立**）。

