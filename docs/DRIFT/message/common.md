<!-- @new 2026-10-23 22:0x–23:0x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `service/message/message-common` 组件级语义对照台账（首轮）

> **核对时间：2026-10-23 22:30–22:55**
> **基线 commit**：`8422116`（`align/message` 工作树，自 `do-my-own-gh` 切出）
> **本文件回答**：message 域的第三个组件 `message-common` 对齐到什么程度、这一轮改了什么、还差什么。
>
> ⚠️ **本组件此前是「零台账 + 零测试」**：
> `docs/DRIFT/_cross/component-coverage-matrix.md` 把它列进「其余两个 common」，
> 标注 🔴 零测试 + 无专属台账；而 `message-api` 的
> `MessageApiSupport#rpcError` javadoc 却写着「message-common **已有 focused tests**」——
> **一句实测为假的断言**（`src/test` 目录当时根本不存在），本轮已修（见 §4）。

## 1. Component Contract（由源码确认，不从类名推断）

| 项 | 值 |
|---|---|
| **Go 真源** | `Sea-RideTheWind_golang/service/message/common/errmsg.go`（**133 行，全文已逐行读**） |
| **RAG** | `service/message/message-common`，3 个源文件 / 453 行 |
| **文件对应** | `errmsg.go` 的四段 ↔ `MessageErrorCode` / `MessageErrorMessages` / `MessageGrpcError`（含内嵌 `CodeError`） |
| **被谁依赖** | `message-api`（`MessageApiSupport#rpcError`）、`message-rpc` |
| **Go 侧唯一消费者** | `service/message/api/internal/logic/shared/helper.go:31`（`RPCError`）；**`service/message/rpc` 全域零调用** |

⚠️ 注意 §3.5 的一条纪律在本组件上体现得特别直接：
Go 的 `message/common` 只有 **1 个文件**，`message/rpc` **一次都不 import 它** ——
rpc 侧的错误码是自己在 logic 里用的常量。RAG 把这层做成**独立模块并让 rpc 也依赖它**，
属于**表达层**组织方式（§4.1 的 HUMAN_ONLY 目标结构），不构成行为差异，前提是常量值逐条相同（§2 已验）。

## 2. 逐条对照（Go `errmsg.go` → RAG）

### 2.1 错误码常量（`:27-49`）—— ✅ 20/20 一致

`MessageErrorCode` 枚举 20 项与 Go `const` 块逐条数值相同
（200 / 500 / 1015 / 5001-5008 / 5101-5102 / 5201-5207）。
证据：`MessageErrorMessagesTest#errorCodesMatchGoConstants`（含「恰好 20 条」的数量守恒断言）。

### 2.2 `codeMsg` 表（`:51-73`）—— ✅ 20/20 文案逐字节一致

证据：`MessageErrorMessagesTest#messagesMatchGoTable` + `unknownCodeFallsBackToFail`
（未知码回 `"FAIL"` 而非 `null`，与 `GetErrMsg` 的 `codeMsg[Error]` 兜底一致）。

### 2.3 `GetErrMsg`（`:88-93`）—— ✅ 一致

`MessageErrorMessages#getMsg`：查不到 ⇒ 回 `500` 的 msg（`"FAIL"`）。

### 2.4 `GRPCError`（`:95-97`）—— ✅ 一致

`MessageGrpcError#grpcError` = `status.code + withDescription(getMsg(bizCode))`。
往返验证：`MessageGrpcErrorTest#grpcErrorRoundTripsBackToBizCode`。

### 2.5 `bizCodeByMsg` 反查表（`:75-86`）—— ✅ 一致（含一个前提）

Go 的反查表构造是 `for code, msg := range codeMsg` + first-write-wins。
⚠️ **Go 的 map 遍历顺序随机** ⇒ 一旦两条 code 共用同一 msg，Go 的反查结果就是**不确定**的。
RAG 用 `putIfAbsent` 遍历 `HashMap`，顺序同样不确定 —— **行为等价**。
当前 20 条 msg 全部唯一（`allMessagesAreUnique` 钉住），所以两侧都是 1:1。

### 2.6 `BizCodeFromError`（`:99-132`）—— 🔴 本轮修了两处，见 §3

## 3. 本轮修复

### ✅ FIX-MSG-COMMON-001 —— status 错误只认「最外层」，Go 认「整条 cause 链」

**分类**：`SEMANTIC MISMATCH`（错误码映射）　**Risk**：HIGH（错误分类错，会误导客户端重试逻辑）

```
Go  errmsg.go:108   st, ok := status.FromError(err)
    └─ grpc-go v1.81.1 status/status.go 的 FromError：
         if gs, ok := err.(grpcstatus); ok { … return grpcStatus, true }   ← 直接命中
         var gs grpcstatus
         if errors.As(err, &gs) {                                          ← **沿 cause 链解包**
             p := gs.GRPCStatus().Proto()
             p.Message = err.Error()                                       ← 且用**外层**文本覆盖 message
             return status.FromProto(p), true
         }
         return New(codes.Unknown, err.Error()), false

RAG（修复前）MessageGrpcError#extractStatus
     if (err instanceof StatusException)     …
     if (err instanceof StatusRuntimeException) …
     return null;                                ← **只看最外层，不解包**
```

**实测后果（修复前）**：

| 输入 | Go | RAG（修复前） |
|---|---|---|
| `NOT_FOUND + "notification not found"` 直接抛 | **5203**（description 反查命中） | 5203 |
| 同一条被 `CompletionException` 包一层 | **5002**（message 被外层文本覆盖 ⇒ 反查落空 ⇒ 按 code class 兜底） | 🔴 **1015** |
| 被 `ExecutionException` / `RuntimeException` 包一层 | 5002 | 🔴 **1015** |
| 包两层 | 5002 | 🔴 **1015** |

**为什么 RAG 侧今天可能没炸**：Go 侧 blocking client 直接返回带 `GRPCStatus()` 的 error，
`errors.As` 第一段就命中，所以真源永远走「直接命中」分支；
而 RAG 走的是 **gRPC-Java blocking stub**，正常路径也是直接抛 `StatusRuntimeException`。
**但**：`CompletionException`（异步 stub / `ListenableFuture`）、Spring `@Async`、
AOP 异常包装、自定义 retry/熔断装饰器 —— 任何一层都会让 RAG 的码**静默退化成 1015**，
而 Go 仍然是 5002/5003/5005。1015 的语义是「服务繁忙」，**它会诱导客户端重试**，
与「参数非法 5003」「无权限 5005」是完全不同的处置。

**修法**（`MessageGrpcError#extractStatus`）：沿 `getCause()` 链向上找，
并**忠实复刻 `p.Message = err.Error()` 这一步** —— 用 `FoundStatus(wrapped)` 记录
「是直接命中还是解包命中」，解包时 description 取**外层** `err.getMessage()`。
不复刻这一步，解包路径会比 Go 多命中一层反查（码可能从 5002 变成 5203）。

**证据**：`MessageGrpcErrorTest`
- `wrappedStatusErrorIsStillMapped`（4 种包装 + 直接命中的对照组）
- `wrappedStatusUsesOuterMessageForReverseLookup`
- `wrappedOuterMessageCanStillHitReverseTable`（单独摘出「外层文本恰好等于表内 msg」，
  用 `INTERNAL` + 外层 `"record not found"` 让「反查命中(5002)」与「code class 兜底(5001)」分道扬镳）

> 📌 **本轮自己踩了一次**：这条用例最初写的是「包装后仍得 5203」，跑出来是 5002。
> **是断言错了，不是实现错了** —— Go 在解包路径上就是会覆盖 message。
> 已按 Go 的实际行为改断言，并在注释里写明「同一个错误，直接抛与包一层，业务码不同」
> 是 Go 的行为、不是 RAG 的自由度。

### ✅ FIX-MSG-COMMON-002 —— `BizCodeFromError` 的判定顺序与 Go 相反

```
Go  errmsg.go:104-106   if codeErr, ok := err.(*CodeError); ok { return codeErr.Code }   ← 先 CodeError
                       st, ok := status.FromError(err)                                    ← 后 status

RAG（修复前）            先 extractStatus → 后 instanceof CodeError                        ← 顺序对调
```

**今天不可观察**（Java 单继承：`CodeError` 不可能是 `StatusException`），
但**原注释给的理由是错的**：「status 也可能裹着业务码，所以先看 status」——Go 里没有这个逻辑。
写反的顺序属于「依赖巧合的代码」：一旦 `CodeError` 开始携带 status 载荷（例如从 gRPC trailers 取码），
两侧就会分叉。已按 Go 的顺序改，并把原注释的错误理由写进新注释（避免后人再「优化」回去）。

**证据**：`MessageGrpcErrorTest#codeErrorReturnsItsOwnCode` + 类的顺序注释。

### ✅ FIX-MSG-COMMON-003 —— api 层重复实现了第二份 status 提取

`message-api` 的 `MessageApiSupport#statusDescription` 原本自己写了一份
`instanceof StatusRuntimeException / StatusException`（与 common 里那份**逻辑相同但独立**）。
这正是 §3.3.3「同一件事写两份，迟早分叉」的形态，而且分叉的症状很隐蔽：
**码对了、msg 错了**（因为 msg 走的是另一条路径），端到端测试极难发现。

已改为委派 `MessageGrpcError#statusMessage(Throwable)`（**全仓只剩一份实现**），
并删掉两条随之失效的 import。

### ✅ FIX-MSG-COMMON-004 —— javadoc 里一句实测为假的断言

`MessageApiSupport#rpcError` 的 javadoc 原写「Go 与 RAG 共用同一张表与同一套规则
（**message-common 已有 focused tests**）」。实测：当时 `message-common/src/test` **不存在**，
而 `pom.xml` 里已经声明了 `junit-jupiter` + `jackson-databind` 的 test scope 依赖
（注释里写着「focused test：MessageErrorCode / MessageErrorMessages / MessageGrpcError 的契约等价测试」）
—— **pom 承诺的测试一个都没写**。属 §3.3.2 点名的「以断言口气写的假事实」。
现已补齐 21 例，并把注释改成不再引用「测试是否存在」当论据。

## 4. 测试（本组件从 0 → 21）

| 类 | 例数 | 覆盖 |
|---|---|---|
| `MessageErrorMessagesTest` | 5 | 常量逐条 / 文案逐条 / 未知码回落 / msg 唯一性（反查前提）/ `getCode` 签名 |
| `MessageGrpcErrorTest` | 16 | null 兜底 / `CodeError` 分支 / 反查命中 / 六条 code class 映射 / 未列 code 兜底 / 两种 status 异常 / **4 种包装解包** / `p.Message` 覆盖 / `grpcError` 双向 |

运行：`mvn -o -pl service/message/message-common test` ⇒ **21 tests / 0 failures / 0 errors / 0 skipped**。

⚠️ **证据强度声明**：本组件的对照证据是 **JVM 内单元测试 + Go 源码逐行 + grpc-go v1.81.1 源码逐行**，
**不是**双侧活体对拍（Go 真源整体起不来，依赖 `etcd:2379`）。
⇒ 表中的「一致」应读作「在上述证据强度下一致」，不是已闭环（§6.2 约定 2）。

## 5. 仍开放（不在本组件范围内，但由本组件决定）

| 编号 | 问题 | 归谁 |
|---|---|---|
| `UNRESOLVED-MSGAPI-001` ✅ **已关闭（2026-10-24 第十轮）** | ~~`message-api` 台账问「message-rpc 的业务错误只用哪三个 gRPC code」~~ —— **已答且已闭环**：<br>① **静态**：`MessageGrpcError#mapStatusCodeToBizCode` 的 switch 与 Go `service/message/common/errmsg.go:117-132` 逐条一致（`InvalidArgument`/`Unauthenticated`/`PermissionDenied`/`AlreadyExists`/`NotFound`/`Internal` + `default=1015`）<br>② **双侧活体**（2026-10-24 第八轮）：真起两套 message-rpc（端口 20082 / 60210）跑 12 档对拍 ⇒ **11 条 status 码 + happy path 全部逐条一致，EXIT=0**（产物 `tools/difftest/message-grpc-results.txt`）<br>③ **兜底码专项**（第九轮）：`MessageRpcUnknownStatusWiringTest`（4 例，真 gRPC）⇒ DB/未知异常两侧都归 `UNKNOWN`<br>⇒ 对应的 api 侧 `UNRESOLVED-001` 同步关闭（见 [`api.md`](api.md) §9） | ↔ message-rpc |
| `UNRESOLVED-MSG-006` | RAG handler 抛非 `RuntimeException` 的 `Error` 时客户端看到什么 —— 与本组件的 status 构造有关但**不属本组件** | ↔ message-rpc |
| — | Go 的 `CodeError` 有 `json:"code"` / `json:"msg"` 标签，RAG 用 `@JsonProperty` 对齐了字段名，但**没有任何测试断言序列化结果**（`CodeError` 从未被 Jackson 序列化过） | 低优先，见 §6 |

## 6. 建议（下一刀）

1. **补一条 `CodeError` 的 Jackson 序列化断言** —— pom 已经引了 test scope 的 `jackson-databind`，
   注释也写了「验证 `@JsonProperty` 实际生效」，但没写。属 §3.3.3「注解存在 ≠ 机制生效」的同类，
   只是当前 `CodeError` 没有真实序列化路径 ⇒ 优先级低，**先登记不做**。
2. **`bizCodeByMsg` 反查表缺「重复 msg」的显式决策** —— Go 侧靠 map 随机序解决（不确定），
   RAG 侧靠 `putIfAbsent`（也不确定）。两侧「同样不确定」在 msg 唯一时等价；
   一旦有人加重复 msg，两侧会**各自随机**。建议在两边都加一条**启动期断言：msg 必须唯一**，
   把「不确定」变成「启动即失败」。本轮只在 RAG 侧加了测试（`allMessagesAreUnique`），
   Go 侧无法改（冻结）⇒ 该项已登记为「已知边界」。