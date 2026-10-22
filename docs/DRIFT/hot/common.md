<!-- @move AGENTS.md L1343-L1391 · §0 hot-common 双侧对拍 -->
### ✅ `service/hot/hot-common`：已落地，Go ↔ Java 双侧对拍 **sha256 逐字节一致**（2026-10-22）

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
| **编译** | `hot-common` / `hot-api` / `hot-rpc` 三模块 `test-compile` | 全通过 |

⚠️ 其中「**漏迁一个码**」是在**生产代码编译期**就失败（`HotErrorMessages` 引用了已删除的枚举常量），不是断言失败 —— 最强的一档。
⚠️ 本仓已有的反面样本：**`like` 域的 Go 源根本没有 `BizCodeFromError`，却建了 258 行的 `LikeGrpcError`** —— 那份超建不要照抄。

#### ⚠️ 三处边界（不得越读成「hot 域已完成」）

1. **有意不迁的 4 个符号不是遗漏** —— grep 验证它们在 `service/hot` 全域**零调用点**、hot-rpc 对本文件**零引用** ⇒ **它们在 Go 侧本身就是死代码**，迁过来只会制造无调用方的结构。`BizCodeFromError` 的**判定顺序**已完整留档在 `FileTree/hot/common.md §12.3`；日后 hot 若接上 gRPC 消费方，**照该顺序**补 `HotGrpcError`（照 `MessageGrpcError` 的形状），不要临时另写一套。
2. **`CodeError.Error()` 的 `"ErrCode:%d, Errmsg:%s"` 未复刻** —— 已查实两侧可观察面：HTTP envelope **完全一致**（Go 是**逐字段复制** `codeErr.Code`/`codeErr.Msg`，不是 `Error()` 整串）；**仅**日志 `error_reason` 自由文本少一段冗余前缀，而码值在两侧都另有结构化 `error_code` 字段承载（Go `logger.go:152` / Java `Logger.java:277`）⇒ 语义信息不丢，属表达层，非 L1。
3. **消费侧尚未接线** —— 全仓目前**无任何代码**引用这两个类；hot-api 的 `GlobalExceptionHandler` / `GlobalResponseAdvice` 仍是空骨架（属 `service/hot/api` 另一个 Component）。接线等价式：`new BusinessException(HotErrorCode.X.getCode(), HotErrorMessages.getMsg(code))` ≡ Go 的 `hotcommon.NewErrCode(...)`。

⚠️ **`hot-common` 没有 `HUMAN_ONLY.MD`，且不应补** —— 全仓仅 4 份、全在 `service/users/` 下；`-common` 模块族（users-common / message-common / article-common / like-common）**从来没有过**这份文档。

**下一步（hot 域）**：① hot-api 的 advice 与 service 层落地时会首次真正调用本码表，届时**必须补一条 MVC 管线测试断言 envelope 的 code/msg**，否则「库侧对拍通过」仍不能证明 HTTP 边界等价；② hot-rpc 零引用本模块，**刻意不依赖**，改判时需同步其 pom。

