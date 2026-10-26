<!-- @new 2026-10-24 · comment 域 api 组件首轮 Go ↔ RAG 语义对照 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# DRIFT 台账 · `service/comment/comment-api`

> **核对时间：2026-10-24**
> **真源：** `Sea-RideTheWind_golang/service/comment/api`（go-zero v1.10.2）
> **本轮性质：** 首轮**组件级**语义对照（AGENTS.md §3.5）。此前本组件**零台账**。
>
> ⚠️ **证据强度**：
> - ✅ **真实 MVC 管线**：`CommentApiContractTest` 用 `@SpringBootTest` + `@AutoConfigureMockMvc`（非 `standaloneSetup` 假绿）
> - ✅ **源码逐行对照**：Go 侧 22 个 `.go`（1,045 行）逐文件读过
> - ✅ **双侧活体对拍**（**2026-10-24 补上**）：Go 真源 + RAG **四进程同跑**，**23 步**请求矩阵逐字段差分，**复跑两轮一致** ⇒ **§4.6**
>   —— ⚠️ **覆盖面有限**：只覆盖「正常 JSON 请求 + 鉴权 + 业务码」这一面；D1 / D2 / MISMATCH-API-03 / U7 / U8 **未被触发**（§4.6.3）
>
> ⇒ ✅ 一律读作「**在该证据强度下一致**」。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | 🔴 **`SEMANTICALLY NOT EQUIVALENT`** —— ⚠️ **但这是首轮对照时的判定；3 条 MISMATCH 已全部修复**（见下）⇒ **当前不再有「必须对齐」的分歧项** |
| SEMANTIC MISMATCH | **3 条**（§2）—— ✅ **MISMATCH-API-01 / -02 / -03 全部已修**，各配守门网 + **变异验证** |
| OBSERVABLE DIFFERENCE | **5 条**（§3）—— ~~D1（**已撤销**）~~ / D2 / D3 / **D4（已修）** / **D5（已签字）** / **D6（已签字）** ⇒ **其中 4 条已闭环**（D1 撤销 / D4 修复 / D5 签字 / D6 签字），**剩余 D2 / D3 两条属 `service/common` 跨域** |
| 等价确认 | **约 40 个判定点**（§4） |
| 组件规模 | Go 1,045 行 / 22 文件 ⇒ RAG 2,838 行 / 33 类 |
| 构建 + 测试 | `mvn -o -pl service/comment/comment-rpc,service/comment/comment-api,service/comment/comment-common -am test` ⇒ **BUILD SUCCESS**；**api 45 / rpc 87 / common 27，合计 159 / 0 failures / 0 errors / 0 skipped**（**2026-10-26 复测**；⚠️ 原记「rpc 85 / 合计 157」已过期 —— 差的 2 例由别域提交 `21e4124` 加进 `comment-rpc`，**非本域新增**；api 侧 45 例不变；修复前 22 条） |
| **对拍轮** | **2026-10-24**：**双侧活体对拍**跑通（Go 18888 ↔ RAG 58888，**23 步**矩阵，**复跑两轮一致**）⇒ **20 一致 / 8 差异**。差异**只落在 3 个步骤**上：T07 / T08 **已签字偏离**，**T20 新登记**（§3.2）⇒ 见 §4.6 |

> 🟥 **MISMATCH-API-01 是「两个模块各自都对、拼起来就错」的典型**：
> `comment-rpc` 把业务码放进自定义 trailer，`comment-api` 从不读它 ——
> **两侧的 javadoc 还互相矛盾**，且两侧的绿测试各自都守着自己那一半。
>
> ✅ **本轮已修**，并用**变异测试**证明守门网不是假绿（§2.1 / §2.2 各附变异实测）。

---

## 0.1 ✅ 本轮已修（附变异验证）

| # | 修复 | 守门网 | 变异实测（回退修复 ⇒ 必须变红） |
|---|---|---|---|
| **MISMATCH-API-01** | `CommentApiSupport.rpcError` 改用 `CommentGrpcError.bizCodeFromError(err)` 取业务码（msg 仍取 `status.getDescription()`） | **新增** `CommentApiGrpcErrorWireTest`（5 例，**真 in-process gRPC server + 真 channel**，非 mock） | ✅ 回退后 **2/5 变红**：`expected: <1012> but was: <2>`、`expected: <5003> but was: <2>` —— **症状与缺陷本体精确一致** |
| **MISMATCH-API-02** | `CommentApiSupport.userId` 对 `Double` / `Float` / `BigDecimal` / `BigInteger` 一律判 **1006**（不再截断 / 回绕）；并订正了把事实说反的 javadoc | **新增** `CommentApiBehaviorTest` 3 例（真 MockMvc 管线 + 真 JWT 签名） | ✅ 回退后 **2/15 变红**：`expected:<1006> but was:<5001>` —— 截断出的错误身份会让下游报错并退化成 5001 |
| **MISMATCH-API-03** | `CommentRpcClient.commentRpc()` 叠 **2 秒** per-call deadline 上限（照抄同仓 `task-api/TaskRpcClient.java:66,105-112` 的 `min(2s, 请求剩余预算)`；预算耗尽给 1ms） | **新增** `CommentApiRpcDeadlineGateTest` 4 例（**真 in-process gRPC server**） | ✅ 回退后 **3/4 变红**，症状精确：①② 拿到 `OK`（= 根本没 deadline）、③ 实测 **3017ms**（= 用的是 3s HTTP 预算而非 2s 上限） |
| **D4**（§3.1） | `CommentJwtAuthInterceptor` 补 **`iat` 在未来** ⇒ 401（此前 jjwt 不校验 `iat` ⇒ **RAG 比 Go 松**） | **新增** `CommentJwtTokenMatrixGateTest` 11 例（真 jjwt + 真拦截器，锁住双侧探针对拍出来的 11 种 token 形态） | ✅ 删掉 `iat` 校验 ⇒ `issuedAtInTheFutureIsRejected` 变红 |

> 📌 **D4 的修复同时被活体对拍确认**：矩阵新增的 **T23-iat-in-future** 步骤在**修复后**两侧**一致**（Go 401 / RAG 401）⇒ 不只是单测绿。

> ⚠️ **变异测试是本轮新增的证据强度**：AGENTS.md §3.3 要求「绿测试能证明测试**不是假绿**」，
> 但「绿测试能证明测试的**期望值是对的**」需要另一条证据 —— 变异测试提供的是前者。
> 本轮两条修复**都做了变异实测**，故「测试守的是正确的答案」这一点也有证据。

---

## 1. HTTP 路由对照（7/7 一致）

| Go path + method | RAG path + method | ✅ |
|---|---|---|
| POST `/comment/v1/comment/create`（`routes.go:19-21`） | POST `/comment/v1/comment/create`（`CommentController.java:96`） | ✅ |
| POST `/comment/v1/comment/delete`（`routes.go:24-26`） | POST `/comment/v1/comment/delete`（`:104`） | ✅ |
| POST `/comment/v1/comment/like`（`routes.go:29-31`） | POST `/comment/v1/comment/like`（`:112`） | ✅ |
| POST `/comment/v1/comment/list`（`routes.go:34-36`） | POST `/comment/v1/comment/list`（`:126`） | ✅（**不是** `/comment/get`） |
| POST `/comment/v1/comment/manage`（`routes.go:39-41`） | POST `/comment/v1/comment/manage`（`:133`） | ✅ |
| POST `/comment/v1/comment/report`（`routes.go:44-46`） | POST `/comment/v1/comment/report`（`:141`） | ✅ |
| POST `/comment/v1/subject/state`（`routes.go:49-51`） | POST `/comment/v1/subject/state`（`:149`） | ✅ |

公共前缀 `/comment/v1`（Go `routes.go:55` `rest.WithPrefix` ⇒ RAG `CommentController.java:68`）✅ · 7 条**全 POST** ✅ · 无路径参数、无尾斜杠 ✅

---

## 2. 🔴 SEMANTIC MISMATCH（3 条）

### MISMATCH-API-01 · 🔴 **下游业务错误码在 HTTP 出口丢失**（本轮最严重）· ✅ **已修**

| 项 | 值 |
|---|---|
| **状态** | ✅ **已修（2026-10-24）** + 守门网 + 变异验证（见 §0.1） |
| **Go 事实** | comment-rpc 用 `errmsg.NewGrpcErr(code, msg)`（`service/comment/common/errmsg/errmsg.go:33-35` `status.Error(codes.Code(code), msg)`）⇒ **业务码原样进 `grpc-status`** ⇒ api 侧 `createcommentlogic.go:52-55` 等 6 处的 `int(st.Code())` **原样取回** 5003 / 1012 / 1013 / 1008 / 2001 |
| **RAG 现状** | comment-rpc 侧 `CommentServiceGrpcImpl.java:118` → `CommentGrpcError.grpcError(bizCode, msg)` ⇒ 业务码进**自定义 trailer** `rag-comment-biz-code`（`CommentGrpcError.java:72`、`:130`），而 `grpc-status` 只能是 `UNKNOWN(2)` / `INVALID_ARGUMENT(3)`（同文件 `:105-110`）。<br>comment-api 侧 `CommentApiSupport.java:139-145` 只读 `status.getCode().value()` ⇒ **从不读 trailer** |
| **证据** | 全模块 grep `BIZ_CODE_TRAILER` / `trailersFromThrowable`：**只有 `comment-common` 自己的 `bizCodeFromError`（`CommentGrpcError.java:186-196`）与它的测试在读**；`comment-api` **零命中** |
| **可观察后果** | 客户端拿到的 `envelope.code`：<br>· Go = `5003` / `1012` / `1013` / `1008` / `2001`<br>· **RAG = `2`（UNKNOWN）或 `3`（2001 时）**<br>`msg`（description）两边一致 ⇒ **只有码错**，正是「客户端按 code 分流就会错」的那类缺陷 |
| **两侧注释互相矛盾** | `CommentServiceGrpcImpl.java:124` 明写「comment-api 侧从 trailer 取业务码、从 description 取原文」；`CommentApiSupport.java:120-137` 明写「本方法**刻意不**使用 `bizCodeFromError`」—— **两句话互斥** |
| **现有绿测试为什么没抓到** | `CommentApiBehaviorTest#createMapsGrpcCodeVerbatim` 用 **mock** 抛 `Status.NOT_FOUND`，测的是「Go 侧 rpc 形态」；**测不到 RAG↔RAG 的 trailer 路径** ⇒ 属于 `docs/Pitfalls/_cross/testing-pitfalls.md` 记的「绿测试给错误答案发通行证」 |
| **修法** | `CommentApiSupport.rpcError` 优先读 `CommentGrpcError.BIZ_CODE_TRAILER`（`Status.trailersFromThrowable`），无 trailer 时再取 `status.getCode().value()`。同时订正两侧相互矛盾的注释，并补一条**真 comment-rpc 驱动**（非 mock）的守门测试 |
| **✅ 本轮修法（2026-10-24）** | 照抄**同仓已有正确范式** `like-api/.../LikeApiSupport.java:179-186`：<br>① 先判「是不是 status 错误」（Go 的 `ok`）⇒ 不是则 **5001 + 「系统内部错误」**（**不是** `bizCodeFromError` 的 1015）；<br>② 是则 `int bizCode = CommentGrpcError.bizCodeFromError(err)`（三条线索：降级 description → trailer → 标准码原值）；<br>③ `msg` 取 `status.getDescription()`，**不查表**。<br>**同时订正了两处相互矛盾的注释**：`CommentApiSupport#rpcError`（原写「刻意不使用 bizCodeFromError」）与 `comment-rpc/.../CommentServiceGrpcImpl.java:124`（原写「comment-api 侧从 trailer 取业务码」）。 |
| **✅ 守门网** | **新增** `comment-api/src/test/java/.../CommentApiGrpcErrorWireTest.java`（5 例）—— **真 in-process gRPC server + 真 channel**（`grpc-inprocess` test 依赖已加），锁住三种对端形态：<br>· **RAG-rpc** ⇒ 1012（trailer 路径）<br>· **Go-rpc（灰度）** ⇒ 5003（降级 description 路径）<br>· **标准码** ⇒ 5（原值路径）<br>· 非 status 错误 ⇒ 5001 |

> ⚠️ **不要用 `bizCodeFromError` 整体替换**：它的第一优先级是「降级 description 捞码」
> （`CommentGrpcError.java:181`、`:209-222`），那是**灰度期 Go-rpc 对端**的兼容路径。
> RAG↔RAG 场景只需读 trailer。

### MISMATCH-API-02 · JWT 数值 claim **截断 / 回绕**而不是报错 · ✅ **已修**

| 项 | 值 |
|---|---|
| **状态** | ✅ **已修（2026-10-24）** + 守门网 + 变异验证（见 §0.1） |
| **Go 事实** | go-zero v1.10.2 `rest/token/tokenparser.go:123` `jwt.NewParser(jwt.WithJSONNumber())` ⇒ 数值 claim 是 **`json.Number`**；`rest/handler/authhandler.go:72-79` 把**所有非标准 claim 原样**注入 ctx ⇒ `helper.go:17-18` `case json.Number: return value.Int64()`<br>⇒ `json.Number.Int64()` = `strconv.ParseInt` ⇒ **`"1.5"` 与越界值都报错** ⇒ logic 走 `errmsg.NewErrCode(ErrorTokenRuntime)` = **1006** |
| **RAG 现状** | `CommentApiSupport.java:88-93` `Double.longValue()` **截断**（1.5 ⇒ 1）、`:82-85` `BigInteger.longValue()` **回绕**（⇒ `-9223372036854775808`）—— **均不报错** |
| **可观察后果** | 非整数 / 越界的数值 claim：Go 返 **200 + `{code:1006, msg:"TOKEN已过期"}`**；RAG **静默产出错误身份**并以该身份调 rpc ⇒ 身份被篡改，属越权面 |
| **附带缺陷** | `CommentApiSupport.java:59-64` 的 javadoc 断言「golang-jwt **没有**用 `UseNumber()`」—— **与 `tokenparser.go:123` 相反**。这正是 AGENTS.md §3.3.2 点名的「以『Go: …』措辞写的断言本身是高危项」 |
| **修法** | 数值 claim 走严格 int64 解析，失败 ⇒ `tokenRuntime()`（1006）；**不得**截断或回绕。同时订正该段 javadoc |
| **✅ 本轮修法（2026-10-24）** | `CommentApiSupport.userId`：`Double` / `Float` / `BigDecimal` / `BigInteger` **一律判 1006**。理由：Go 的 `json.Number.Int64()` 解析的是 **JSON 字面量原文** ⇒ JSON 浮点字面量（原文必带 `.` 或 `e`）与越界整数**必然**解析失败，**没有例外**。<br>`Integer` / `Long` / `Short` / `Byte`（必在 int64 范围内）与 `String`（走 `Long.parseLong`，与 Go 的 `strconv.ParseInt` 同规则）保持原样。<br>**并订正了 `CommentApiSupport.java:59-64` 把事实说反的 javadoc**（原写「golang-jwt 没有用 `UseNumber()`」）。 |
| **✅ 守门网** | **新增** `CommentApiBehaviorTest` 3 例（真 MockMvc 管线 + 真 jjwt 签名）：<br>· 浮点 claim `1.5` ⇒ `{code:1006, msg:"TOKEN已过期"}`，且**不得**调 rpc<br>· 越界 claim `9223372036854775808` ⇒ 同上<br>· 字符串 claim `"31337"` ⇒ 精确解析成 31337 并下发 rpc |

### MISMATCH-API-03 · gRPC per-call deadline 缺 **2 秒**上限 · ✅ **已修**

| 项 | 值 |
|---|---|
| **Go 事实** | go-zero v1.10.2 `zrpc/config.go:31` `Timeout int64 \`json:",default=2000"\`` ⇒ **2000ms**；`zrpc/internal/client.go:115-116` 装 `TimeoutInterceptor(timeout)`；`etc/commentservice.yaml:21-23` 的 `CommentRpc` **未覆盖** `Timeout` ⇒ 生效值 = **2s** |
| **RAG 现状** | `CommentRpcClient.java:46-48` 只套 `GoStyleRequestDeadline.applyTo(...)` ⇒ 取自 **HTTP 预算 3s**（`application.yml:140` `http-timeout: 3s`）⇒ **有效 deadline = 3s，无 2s 上限** |
| **可观察后果** | 下游耗时落在 **2.0–3.0s**：Go 返 `DEADLINE_EXCEEDED`（`envelope.code = 4`），**RAG 成功**。<br>且 Go 侧该超时**计入熔断失败**（`zrpc/internal/codes/accept.go` 把 DeadlineExceeded 列为失败）⇒ 两侧熔断统计口径也不同 |
| **✅ 已修** | **2026-10-24**。`CommentRpcClient` 照抄**同仓既有正确范式** `task-api/.../TaskRpcClient.java:66,105-112`：`DEFAULT_CALL_TIMEOUT_MILLIS = 2000L` + `min(2s, 请求剩余预算)` + 预算耗尽给 1ms。⚠️ **不能只套 `applyTo`** —— 它在**没有** HTTP 预算时**原样返回 stub**（不挂 deadline），而 Go 的 timeout 中间件是**永远装着**的（`zrpc/internal/config.go:15` `Timeout bool \`json:",default=true"\``）⇒ Go 的下游调用**永远**有 2s 上限，RAG 侧也必须如此 |
| **🧪 守门网** | **新增** `CommentApiRpcDeadlineGateTest`（**4 例，真 in-process gRPC server**）：① 无 HTTP 预算 ⇒ 必须 `DEADLINE_EXCEEDED`（**结局码**就能区分「有上限」与「没上限」）；② HTTP 预算 30s ⇒ 必须 `DEADLINE_EXCEEDED`（2s 上限压过 30s 预算）；③ 生产形态（3s 预算）⇒ 必须 `DEADLINE_EXCEEDED` **且耗时 < 2.6s**（证明是 2s 上限而非 3s 预算）；④ 预算耗尽 ⇒ **立刻**失败（< 500ms） |
| **🧬 变异实测** | 把 `commentRpc()` 改回 `GoStyleRequestDeadline.applyTo(...)`（= 修复前）⇒ **3/4 变红**，症状精确：①② 拿到 `OK`（= 根本没 deadline），③ 实测 **3017ms**（= 用的 3s 预算）⇒ 守门网不是假绿 |

---

## 3. OBSERVABLE DIFFERENCE（6 条）

| # | 判定点 | Go 侧事实 | RAG 侧现状 | 性质 |
|---|---|---|---|---|
| **D1** | ~~非 `application/json` 的 Content-Type~~ ⇒ 🟢 **撤销（2026-10-24 活体实测：原结论是误判）** | `httpx/requests.go:153-154` `withJsonBody` 假 ⇒ `UnmarshalJsonMap(nil, v)` ⇒ 必填缺失 ⇒ **200 + `{code:5001}`** | **实测同样是 200 + `{code:5001}`** ⇒ **两侧一致** | 🟢 **撤销**：静态结论说「RAG ⇒ 415」**是错的**（RAG 实际落在 `HttpMessageNotReadableException("Required request body is missing")` 分支）⇒ 详见 §3.3 |
| **D2** | Bearer 前缀剥离的**长度边界** | `golang-jwt/jwt/v4/request/oauth2.go:8-14`：`len(tok) > 6 && ToUpper(tok[0:7]) == "BEARER "` | `users-common/.../BearerTokenExtractor.java:74-81`：判据 `length() > 7` | 长度恰为 7 的头（`"Bearer "`）：Go 剥成空串→解析失败→**401**；RAG 保留整串→解析失败→**401**。**结局相同**，仅错误文本不同 ⇒ 当前不可观察，可对齐为 `> 6` 以消除隐患 |
| **D3** | 熔断**隔离键** | `zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18` `path.Join(cc.Target(), method)` | `common/protection/GoStyleGrpcClientBreakerInterceptor.java:193` `method.getFullMethodName()`（**无 target 前缀**） | 单 channel 下不可观察；将来多 target 会把计数合并。⚠️ 该类在 `service/common` 下，**改动影响多域** ⇒ 只登记不改 |
| **D4** | **`iat` 在未来**的 token | `golang-jwt/jwt/v4@v4.5.2/map_claims.go:124-141` `MapClaims.Valid()` → `:63-85` `VerifyIssuedAt` → `claims.go:250-255` `verifyIat`：**`now >= iat` 才有效**（**无时钟偏移容忍**）⇒ **拒绝** | **修复前** jjwt 0.12.6 **不校验 iat** ⇒ **接受** | ✅ **已修**（2026-10-24，详见 §3.1）—— 修复前 **RAG 比 Go 松**（安全面更宽） |
| **D5** | **HS384 / HS512** 的 token | `jwt/v4/hmac.go:49-74` `SigningMethodHMAC.Verify` **不限制 alg**；go-zero 的 `rest/token/tokenparser.go:127-129` `newParser()` 也**没有** `WithValidMethods` ⇒ **接受** | jjwt 的 `verifyWith(key)` 把 key **绑死**在一个算法上（`Keys.hmacShaKeyFor` 对 32 字节 ⇒ `HmacSHA256`）⇒ 头写 HS384/HS512 ⇒ **`WeakKeyException`** ⇒ **拒绝** | ✅ **已签字偏离**（2026-10-24 用户 Jason，`SD-2026-10-24-CAND-07`）—— **RAG 更严一侧** ⇒ 代码不动 |
| **D6** | ✅ **已签字偏离**（2026-10-24 用户 Jason）—— **保护层的覆盖范围**：未注册路径上的 **MaxBytes** | go-zero v1.10.2 `rest/engine.go:105` —— chain 只在 `bindRoute` 内拼装 ⇒ **未注册的路径根本不经 MaxBytes** ⇒ 服务端**从不返回 413**（客户端表现为 `Broken pipe`） | 4 个保护是**全局 servlet Filter** ⇒ **未注册路径也经过 MaxBytes** ⇒ **413** | ✅ **已签字**（`docs/Intentional_Deviations/comment/api.md` 的 `SD-2026-10-24-CAND-08`）⇒ **RAG 是更严/更安全的一侧** ⇒ 代码不动 ⇒ 详见 §3.2 |

### 3.1 ✅ D4 已修：`iat` 在未来的 token ⇒ 401（此前 RAG 接受、Go 拒绝）

| 项 | 值 |
|---|---|
| **Go 事实** | `map_claims.go:124-141` `MapClaims.Valid()` → `:63-85` `VerifyIssuedAt(now, false)`：<br>· `iat` **缺失** ⇒ `!req` ⇒ **有效**<br>· `iat` 是数字 ⇒ `verifyIat` ⇒ **`now >= iat` 才有效**<br>· `iat` 是**其它类型** ⇒ `return false` ⇒ **拒绝**<br>`claims.go:250-255` `verifyIat`：**无时钟偏移容忍** |
| **jjwt 侧** | 0.12.6 **只校验 `exp` / `nbf`，不校验 `iat`** ⇒ 修复前 RAG **接受**「iat 在未来」的 token |
| **可观察后果** | 修复前：一个「签发时间在未来」的 token 在 **Go 侧 401**、在 **RAG 侧通过鉴权** ⇒ **RAG 的鉴权比 Go 松** ⇒ 按 AGENTS.md §3「差异默认判 RAG 错」⇒ **必须修** |
| **✅ 已修** | **2026-10-24**。`CommentJwtAuthInterceptor.preHandle` 在 `parseSignedClaims` 之后补 `iat` 校验：`claims.getIssuedAt() != null && issuedAt.toInstant().isAfter(Instant.now())` ⇒ **401**（Go 走 `authhandler.go` 的 `unauthorized()`）。⚠️ **精度对齐**：Go 比较的是**秒**，jjwt 的 `getIssuedAt()` 是毫秒精度但 token 里的 `iat` 是整秒 ⇒ 毫秒部分恒为 0 ⇒ 与 Go 的秒级比较**等价** |
| **🧪 守门网** | **新增** `CommentJwtTokenMatrixGateTest`（**11 例**：真 jjwt 0.12.6 + 真 `CommentJwtAuthInterceptor`）—— 锁住**双侧探针对拍出来的 11 种 token 形态**的接受/拒绝矩阵 |
| **🧬 变异实测** | 把拦截器里的 `iat` 校验整段删掉 ⇒ `issuedAtInTheFutureIsRejected` 变红 ⇒ 守门网不是假绿 |

> 📌 **本轮用「双侧探针」把 U7 变成了活体结论**：
> Go 侧探针（`/tmp/jwtprobe`，走 go-zero 的 `rest/token.TokenParser`）
> 与 Java 侧探针（走本拦截器的真实代码路径）读**同一批字节完全相同的 token** 逐条对拍。
> ⇒ 「缺 `exp` 两侧都接受」（此前是未知项）、「`iat` 在未来 Go 拒 RAG 收」（**已修**）、
> 「HS384/HS512 Go 接受 RAG 拒」（**已签字偏离** `SD-2026-10-24-CAND-07`）三条全部落定。
> ⇒ 顺带**证明**了 `JwtUtil.padToHmacKeySize` 的 zero-pad 是**空操作**：
> HMAC 本来就把短 key 零填充到块长（64 字节）⇒ 短 secret 与 pad 后的 secret **恒等**
> ⇒ 「jjwt 要求 key ≥ 32 字节」这个运行环境约束**不产生行为差异**（守门网里有对应的等价性断言）。

### 3.2 ✅ D6 **已签字偏离**：**保护层的覆盖范围**不同 —— 未注册路径上 Go **从不**返回 413，RAG 返回 413

| 项 | 值 |
|---|---|
| **Go 事实** | `rest/engine.go:105` —— `chn = ng.buildChainWithNativeMiddlewares(fr, route, metrics)` **写在 `bindRoute` 内部** ⇒ **只有被注册的路由**才会拿到 chain；<br>`rest/engine.go:163-165` —— `if ng.conf.Middlewares.MaxBytes { chn = chn.Append(handler.MaxBytesHandler(ng.checkedMaxBytes(fr.maxBytes))) }` ⇒ MaxBytes 是**挂在该路由 chain 上**的 per-route native middleware；<br>`rest/config.go:21` —— `MaxBytes bool \`json:",default=true"\`` ⇒ **默认开**；`rest/config.go:53` —— `MaxBytes int64 \`json:",default=1048576"\`` ⇒ **默认 1 MiB**；<br>⚠️ `service/comment/api/etc/commentservice.yaml` **未覆盖** `Middlewares` / `MaxBytes` ⇒ 生效值 = **1 MiB** |
| **RAG 事实** | `service/common/protection/GoStyleMaxBytesFilter.java:82-84` —— `@Component` + `@Order(HIGHEST_PRECEDENCE + 6)` + `implements Filter` ⇒ **全局 servlet Filter**（不是 per-route）；<br>`:112` —— `response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)` ⇒ **413 空 body** |
| **🟢 活体证据** | **T20**（2026-10-24 对拍，**复跑两轮一致**）：向**未注册路径**发 **1.5 MB** body ⇒<br>· **Go** = `TRANSPORT_ERROR: URLError(BrokenPipeError(32, 'Broken pipe'))` ⇒ **服务端从未返回 413**（该路径不经 MaxBytes；且 Go 在读完整个请求体之前就断了连接）<br>· **RAG** = **HTTP 413**（空 body） |
| **影响面** | ⚠️ **仅限「未注册路径」**。**已注册路径**上两侧一致：Go 的 per-route MaxBytes 生效 ⇒ 413；RAG 的全局 Filter 生效 ⇒ 413（413 压过 401 的语义两侧也一致）。<br>⇒ **T19（405 错方法）两侧一致**（矩阵实测）—— 说明差异只出现在**连路由都没匹配上**的场景 |
| **方向判断** | 🔴 **RAG 是更安全的一侧**（Go 侧未注册路径**不限 body 大小** ⇒ 无界读 ⇒ 服务端资源耗尽面更大）。⚠️ 但按 AGENTS.md §3「差异默认判 RAG 错」，**未签字前它仍是 DRIFT，不是缺陷** |
| **⚠️ 为什么本域改不了** | 修法必然落在 `service/common/protection` 的 4 个保护 Filter 上 —— 该目录**六域共用**，且 `align/common` 分支正在并发改动 ⇒ **本工作树不可动**（共享面禁令） |
| **可选修法** | ① **把 MaxBytes 从 Filter 改成 HandlerInterceptor**（interceptor 只对**匹配到 handler** 的请求生效 ⇒ 未注册路径自然不经它 ⇒ 与 Go 一致），但要保住「413 压过 401」的顺序 ⇒ 需与 `CommentJwtAuthInterceptor` 比 `@Order` ⇒ **仍是共享面改动**；<br>② **签字为有意偏离**（RAG 更严/更安全的一侧，与 `SD-2026-10-23-01/02/03` 同性质）⇒ 本域**零改动** |
| **当前处置** | ✅ **已签字**（2026-10-24，用户 Jason）⇒ 登记为 `SD-2026-10-24-CAND-08`（**RAG 更严/更安全的一侧**）⇒ **代码零改动**；⚠️ **对拍再看到「Go=transport error / RAG=413」不得当成新回归去「修」回 Go 侧行为** |

> ✅ **签字结论（2026-10-24，用户 Jason）**：**接受本偏离** —— RAG 是**更严/更安全的一侧**（Go 侧未注册路径**无界读**）
> ⇒ 登记为 `SD-2026-10-24-CAND-08`（`docs/Intentional_Deviations/comment/api.md`）⇒ **代码零改动** ⇒ **不改** `service/common` 的共享 Filter。
> ⚠️ **别把它当成新回归**：T20 是**2026-10-24 本轮对拍新引入的矩阵步骤**，
> 在此之前 17 步矩阵**没有请求过未注册路径** ⇒ 这条差异**此前从未被观察到**，
> 但它**不是本轮代码改动引入的**（RAG 的全局 Filter 是既有实现，Go 的 per-route 语义也是既有事实）⇒
> **归因是「覆盖面被补上了」，不是「行为被改坏了」。**

### 3.3 🟢 D1 撤销：非 JSON 的 Content-Type ⇒ **两侧都是 200 + `{code:5001}`**

| 项 | 值 |
|---|---|
| **原结论** | 「Go 侧 200 + `{code:5001}` vs RAG 侧 **415** ⇒ 状态码不同」⇒ 登记为 OBSERVABLE DIFFERENCE |
| **原结论的依据** | `CommentGlobalExceptionHandler.java:203` 的 javadoc：「Java 侧由 Spring 的消息转换器选择直接判 415」 |
| **🟢 复核结论** | 🟢 **原结论不成立** —— **两侧都是 200 + `{code:5001}`** ⇒ **不是差异** ⇒ **撤销** |
| **复核日期** | 2026-10-24（双侧活体对拍 **T21** + 6 组补充探针） |
| **实测矩阵** | Content-Type ∈ {`application/json`, `text/plain`, `application/octet-stream`, 无 Content-Type} × body ∈ {完整合法 JSON, `{}`, 1 字节, 空} ⇒ **每一组都是两侧 200 + `{code:5001}`**（`application/json` + 完整合法 body 才是 200 + 数据） |
| **RAG 侧的真实分支** | 应用自己的日志为证：`unreadable request body : Required request body is missing: ...` ⇒ 落在 `CommentGlobalExceptionHandler.handleUnreadableBody`（`HttpMessageNotReadableException`）⇒ **不是** `handleMediaTypeNotSupported` |
| **判别实验** | `application/json` + 1 字节 body ⇒ Spring 报 **`JSON parse error: Unrecognized token 'x'`**（body **被读了**）<br>`text/plain` + 1 字节 body ⇒ 报 **`Required request body is missing`**（body **读不出字节**）⇒ **判别点是 Content-Type** |
| **⚠️ 机制未查实** | Spring 7 + Tomcat 11 组合下，**非 JSON Content-Type 时 servlet input stream 读不出字节** ⇒ 具体成因未定位（已排除：本域代码无人调 `getParameter` / `getReader` / `getInputStream`；`GoStyleMaxBytesFilter` 只读 `getContentLengthLong()`，不消费 body）。⇒ **可观察行为已定，机制待查** ⇒ 记为 `U10` |
| **连带动作** | ✅ **已订正** `CommentGlobalExceptionHandler.java` 的 javadoc（原断言「Java 侧直接判 415」**是错的**）—— §3.2「先改/删错误断言」；
| | ✅ **对应的签字候选 `SD-2026-10-24-CAND-04` 已作废**（其前提「RAG ⇒ 415」不成立 ⇒ 差异不存在）—— 见 `docs/Intentional_Deviations/comment/api.md` |

> ⚠️ **元教训**：这条差异**从立项到撤销，全程没有一次活体证据** ——
> 立项靠的是「读 Spring 的异常处理类 + 推断」，撤销靠的是「两侧同时跑」。
> ⇒ 归入 `docs/Pitfalls/comment/api.md` 的 PIT-COMMENT-API-03。

---

## 4. 等价确认（钉住）

### 4.1 请求体 / 参数绑定

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 6 个 DTO / **28 个字段**的 `json` tag | `api/internal/types/types.go:22-29/37-41/48-55/62-67/74-79/85-91/107-111` | `dto/*.java` 的 `@JsonProperty` | ✅ **28/28 逐条一致** |
| **tag 无 `,optional` ⇒ 必填** | go-zero `core/mapping/unmarshaler.go:967-969` `default: if !opts.optional() { return newInitError(fullName) }`；`:1198-1200` ⇒ `field %q is not set` | `GoStyleRequired.field(...)`（只判 null） | ✅ 缺字段 / 显式 null 均 ⇒ 200 + 5001 |
| **包装类型 vs 基本类型**（presence 语义） | Go 不区分「缺席」与「零值」；缺席 = 报错 | 全用 `Long`/`Integer` 包装 + `assertGoRequiredFields()` | ✅ |
| 空串**合法** | `core/mapping/unmarshaler.go:643-661` `typeKind == valueKind` ⇒ 写 `""` **不报错** | `GoStyleRequired` 只判 null | ✅ `content:""` 两侧均 200 |
| 字符串 → 整数字段**拒绝** | `unmarshaler.go:643-661` `typeKind != valueKind` ⇒ mismatch | `CommentGoStyleCoercion.java:72-73` `LogicalType.Integer` + `CoercionInputShape.String` → `Fail` | ✅ `{"root_id":"123"}` 两侧均 5001 |
| 浮点 → 整数字段**拒绝** | `unmarshaler.go:664-684` → `strconv.ParseInt("1.0")` 失败 | `GoStyleCoercion.java:120` `disable(ACCEPT_FLOAT_AS_INT)` | ✅ `1.0` / `1.5` 均 5001 |
| 数字 → 字符串字段**拒绝** | 同上 mismatch | `GoStyleCoercion.java:123-126` `LogicalType.Textual` + Integer/Float/Boolean → Fail | ✅ |
| 本域有无 `,string` 字段 | **零个** | `CommentGoStyleCoercion.java:14-19` 已识别并据此叠加规则 | ✅ |
| 未知字段（如 body 里的 `user_id`） | go-zero `mapping` 忽略未在 tag 中出现的 key | Jackson 默认忽略 | ✅ body 无法指定操作人 |

### 4.2 响应 envelope

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 成功信封 | `commentservice.go:39-45` → `response.Response{Code:200, Msg:"OK", Data:v}`（`service/common/response/response.go:3-7`，三字段 `code`/`msg`/`data`） | `CommentGlobalResponseAdvice.java:68-69` + `Response.java:14-18` | ✅ 字段序一致 |
| `data` 缺失时**仍出现**（`"data":null`） | Go `Data interface{}` 无 `omitempty` | `Response.java` **未加** `@JsonInclude` ⇒ 默认 ALWAYS | ✅ |
| 错误信封：**HTTP 恒 200** | `commentservice.go:47-60` | `CommentGlobalExceptionHandler.java:100-104` | ✅ |
| 错误信封的 `code`/`msg` 来源 | `errors.As(err, &e)` 命中 ⇒ `e.Code`/`e.Msg`；否则 5001 + 「系统内部错误」 | `handleBusiness` / `handleAny` | ✅ |
| HTML 转义 | go-zero `core/jsonx/json.go:12-30` `SetEscapeHTML(false)` + 去尾换行 | Jackson 默认不转义 HTML、无尾换行 | ✅ **字节级等价** |
| 只包一层（已是 `Response` 就放行） | Go OkHandler 直接产出信封 | `CommentGlobalResponseAdvice.java:61-63` | ✅ |

### 4.3 JWT / 鉴权

| 维度 | Go 侧 | RAG 侧 | ✅ |
|---|---|---|---|
| 保护范围 | `routes.go:54` **单段** `rest.WithJwt` ⇒ **7/7 全保护** | `WebMvcConfig.java:73-76` `/comment/v1/**` ⇒ 7/7 | ✅ |
| public 路径 | **无**（含只读的 `/comment/list`） | **无** | ✅ |
| 密钥 | `etc/commentservice.yaml:6` `ae0536f9-…505da0` | `application.yml:64` 同值（可 env 覆盖） | ✅ |
| claim 名 | `helper.go:11` `ctx.Value("userId")` | `CommentJwtAuthInterceptor.java:66` `CLAIM_ATTRIBUTE = "userId"` | ✅ |
| userId 来源 | **仅 JWT**（6 个 Req 均无 `user_id` 字段） | **仅 JWT** | ✅ body 无法指定操作人 |
| 缺失 / 非法 token ⇒ **401 + 空 body** | `authhandler.go:106-121` `WriteHeader(401)`，不写字节 | `CommentJwtAuthInterceptor.java:90-93` → `CommentGlobalExceptionHandler.java:79-83` `ResponseEntity.status(401).build()` | ✅ |
| **不校验 claim 取值** | `authhandler.go:71-79` 只验签、原样透传 | `CommentJwtAuthInterceptor.java:46-51` 明确不校验 | ✅ claim 缺失 ⇒ **200 + 1006** 而非 401 |
| `/comment/list` **不取身份** | `getcommentlogic.go` 全篇无 `extractUserID` | `CommentController.java:127` 不传 `HttpServletRequest` | ✅ 与其余 6 个端点的不对称被保留 |
| 鉴权 vs 路由匹配顺序 | go-zero `rest/engine.go:101-116`：未注册路径不进 auth | `CommentJwtAuthInterceptor.java:85-87` 只在 `HandlerMethod` 上鉴权 | ✅ 404 不被遮蔽成 401 |
| `uid <= 0` **不拒绝** | Go 的 `extractUserID` 不判 | `CommentApiSupport.userId` 也不判 | ✅ uid=0 原样透传给 rpc |
| 身份失败 ⇒ 1006 | 6 处 logic 首行 | `CommentApiSupport.java:100-107/166-169` | ✅ |
| 校验顺序 | 6 个 logic：**先身份、后 rpc**；`GetComment`：**先入参、后 rpc、无身份** | 对应 6 个 Service + `GetCommentService` | ✅ 错误码优先级一致 |

### 4.4 gRPC client

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 同步阻塞 | api→rpc 无 goroutine / errgroup | `CommentRpcClient.java:42-43` `BlockingStub` | ✅ |
| **无 retry** | `zrpc/config.go:24-35` 的 `RpcClientConf` **没有** `Retries` 字段 | 无 retry | ✅ |
| **熔断已存在**（🔴 **更正既有台账**） | `zrpc/internal/config.go:14` `Breaker bool \`json:",default=true"\`` ⇒ **默认开**；`zrpc/internal/client.go:112-113` 装 `BreakerInterceptor`；`commentservice.yaml` 未配 `Middlewares` ⇒ 生效 | RAG 侧**有**：`CommentApiApplication.java:68-71` 的 `scanBasePackages` 含 `com.rag.common` ⇒ 扫到 `common/protection/GoStyleGrpcClientBreakerInterceptor.java`（`@Component` + `@GrpcGlobalClientInterceptor`）；`application.yml:88-89` `rag.protection.breaker.enabled: true` | ✅ |
| 熔断的失败名单 | `zrpc/internal/codes/accept.go`：仅 DeadlineExceeded/Internal/Unavailable/DataLoss/Unimplemented/ResourceExhausted 算失败 | `GoStyleGrpcClientBreakerInterceptor.java:242-251` `isAcceptable` **6 个 code 逐条一致** | ✅ 业务错误**都不计入失败** ⇒ 与 Go 一致 |
| 熔断错误的码 | Go：熔断错误是普通 error ⇒ `status.FromError` ok=false ⇒ **5001** | `CommentGlobalExceptionHandler.java:146-150` → 5001 | ✅ |
| 调用参数逐条 | 6 个 logic 的 `newBuilder()` 字段 | 对应 6 个 Service | ✅ |
| open enum 原样透传 | `managecommentlogic.go:46` `pb.ManageType(req.ActionType)` 等纯转换 | `setActionTypeValue` / `setReasonValue` / `setStateValue` | ✅ 未定义值（99/98）**不被静默改写成 0** |

### 4.5 业务逻辑（7 个 logic）

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| `GetCommentResp.subject.owner_id` **恒 0** | `getcommentlogic.go:91` `OwnerId: 0`（丢弃 proto 的 `owner_id=7`） | `GetCommentService.java:134` `setOwnerId(0L)` | ✅ 未擅自「修好」 |
| `comment` / `children` **恒 `[]`** | `getcommentlogic.go:79/97/124/128` `make([]...,0)` | `GetCommentService.java:112/167` | ✅ |
| `subject` 缺席 ⇒ 全零对象 | `getcommentlogic.go:83` + `types.go:59` 值类型 | `GetCommentService.java:122` + `dto/SubjectInfo.java:37-56` 基本类型 | ✅ |
| `state` 降成数值 | `getcommentlogic.go:89/121` `int32(...GetState())` | `getStateValue()` | ✅ |
| `DeleteComment.success` 是**字面量 true**（不读 rpc 的 `success`） | `deletecommentlogic.go:58` | `DeleteCommentService.java:58` | ✅ |
| 其余 4 个 `success` 取 rpc 值 | `like/manage/report/updatesubjectstate` 4 处 | 对应 4 个 Service | ✅ |
| 5 处入参兜底（`sort∉{0,1}⇒0` / `page≤0⇒1` / `size≤0⇒20` / `size>50⇒50` / `rootId<0⇒0`） | `getcommentlogic.go:42-60` | `GetCommentService.java:91-96` | ✅ 反直觉的 `sort_type=0 ⇒ 热度` 被保留 |
| 校验只判空、**不回写 trim** | `getcommentlogic.go:34/37` 只有 `TrimSpace(...)==""`，无赋值 | `GetCommentService.java:79-88` | ✅ 带空格的 `target_type` 原样下发 |
| `GetComment` 的**裸抛**（不经 `FromError`）⇒ 兜底 5001 | `getcommentlogic.go:72-75` | `GetCommentService.java:99-107` 不 catch | ✅ 6 个端点给业务码、`GetComment` 给 5001，**两侧同样不对称** |

---

## 4.6 🟢 双侧活体对拍（2026-10-24）—— api 层被**真正跑过**的部分

> 基建见 [`rpc.md` §4.6.1](./rpc.md)。本节只记 **api 层**的结论。

### 4.6.1 🟢 MISMATCH-API-01 的修复**被活体验证**（此前只有单测证据）

对拍矩阵走过的路径里，有多条会触发 **rpc 侧返回业务码**（1013 / 2001 / 1008 / 1012）。
**修复前**这些码在 HTTP 出口会退化成 gRPC 状态码（2 / 5001）；**修复后逐条与 Go 一致**：

| 场景 | Go | RAG |
|---|---|---|
| 点赞不存在的评论 | `code=1013` `评论不存在` | `code=1013` `评论不存在` ✅ |
| 空评论内容 | `code=2001` `评论内容不能为空` | `code=2001` `评论内容不能为空` ✅ |
| 越权改 subject state | `code=1008` `非up主无法评论` | `code=1008` `非up主无法评论` ✅ |
| 缺 Authorization 头 | **HTTP 401** | **HTTP 401** ✅ |
| 坏 JWT（`not.a.jwt`） | **HTTP 401** | **HTTP 401** ✅ |

⇒ **业务码跨进程传递链路的活体证据**（本仓自建 trailer 方案）在**双侧同时在跑**时成立。
⚠️ 对拍用的 JWT 是**自签 HS256**（`diff_comment.py` 用同一份 secret 签）⇒ **只覆盖 HS256**。

### 4.6.2 🔴 api 侧起不来（**本仓自己引入的**）

`NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`
⇒ 根因与修法见 [`rpc.md` §4.6.2 P-3](./rpc.md) 与 `docs/Pitfalls/comment/rpc.md` PIT-COMMENT-RPC-09。
⇒ **单测完全测不出来**（test 作用域那份依赖还在）。

### 4.6.3 🟢 矩阵**扩到 23 步**（2026-10-24 第二轮）—— 未覆盖清单已清空大半

**第一轮 17 步**跑完后，本台账 §5 的 U7 / U8 两项仍是「静态结论」⇒ 第二轮把矩阵**扩到 23 步**，
新增的 6 步**全部瞄准「此前未触发」的判定点**：

| 新步骤 | 瞄什么 | 结果 |
|---|---|---|
| **T18** `404-unregistered` | 未注册路径的**路由匹配与鉴权顺序** | ✅ **一致**（两侧都 404；**不是**被遮蔽成 401）⇒ 证实 §4.3 的「鉴权 vs 路由匹配顺序」判定点 |
| **T19** `405-wrong-method` | 已注册路径 + 错方法 | ✅ **一致** |
| **T20** `404-oversized-body`（1.5 MB） | **U8：保护层的覆盖范围** | 🔴 **差异**（Go=broken pipe / RAG=**413**）⇒ 登记为 **D6**（§3.2） |
| **T21** `415-content-type` | **D1：非 JSON 的 Content-Type** | ✅ **一致**（两侧都 **200 + `{code:5001}`**）⇒ **D1 撤销**（详见 §3.3） |
| **T22** `bearer-length-7` | **D2：Bearer 剥离长度边界** | ✅ **一致**（两侧都 401）⇒ 证实 D2「当前不可观察」的判断 |
| **T23** `iat-in-future` | **D4：`iat` 在未来的 token** | ✅ **一致**（两侧都 401）⇒ **D4 的修复被活体确认** |

> 📌 **T21 把一条「已登记的漂移」直接推翻了** ⇒ 这正是 AGENTS.md §3.3「必须配**两侧同时跑的探针**」的意义：
> **静态推导会被推翻。** D1 的原结论（RAG ⇒ 415）来自 `CommentGlobalExceptionHandler.java` 自己的 javadoc，
> 而那段 javadoc **本身就是错的** ⇒ 已一并订正（§3.3）。

⇒ **本轮对拍把 api 层的「响应码 / envelope / 鉴权」这一面钉死了**，
**D1 已撤销**（实测两侧一致）、**D2 / D3 / D4 / D5 全部有活体或明确结论**（D6 是唯一的新差异，且已登记）。

---

## 5. 未决 / 待办

| # | 事项 | 类型 |
|---|---|---|
| **U1** | ~~MISMATCH-API-01 的修复 + 真链路守门测试~~ | ✅ **已完成**（见 §0.1 / §2） |
| **U2** | ~~MISMATCH-API-02 的严格解析 + 订正错误断言~~ | ✅ **已完成**（见 §0.1 / §2） |
| **U3** | ~~MISMATCH-API-03 的 2s deadline 上限~~ | ✅ **已完成**（2026-10-24）：照抄 `task-api/TaskRpcClient.java:66,105-112` 的 `min(2s, 剩余预算)`；守门网 `CommentApiRpcDeadlineGateTest` 4 例（真 in-process gRPC server）+ 变异 **3/4 精确变红** ⇒ 见 §2 |
| **U4** | **既有台账的过期结论**：`docs/DRIFT/_cross/new-domains-2026-10-23.md:43-45` 记「已登记未修：gRPC client 熔断缺失（Go `zrpc` `Breaker` 默认 `true`）」—— **实测 RAG 侧熔断已存在**（`application.yml:88-89` + `GoStyleGrpcClientBreakerInterceptor`）⇒ **该结论作废**。⚠️ `_cross/` 是共享文件，本工作树不碰，**合并后由主会话更正** | 🔴 需更正共享台账 |
| **U5** | ~~`CommentGrpcError.java` 那段「若未来 comment-api 引入熔断器」的注释已过时~~ | ✅ **已订正**（2026-10-24，**属 `comment-common`，本工作树可改**）—— 熔断早已引入且两侧失败名单一致 ⇒ 见 [common.md](common.md) §4-U2 |
| **U6** | ~~`CommentGrpcError.java` 的「字面搬运」论证需补「对 RAG-rpc 对端应读 trailer」~~ | ✅ **已订正**（2026-10-24，**属 `comment-common`**）—— 补上「该论证只对 Go-rpc 对端成立」+ 方向不对称表 ⇒ 见 [common.md](common.md) §4-U3 |
| **U7** | ~~jjwt 对**缺 `exp` claim** 的 token 的接受性 vs Go `jwt.MapClaims.Valid()`；jjwt 是否接受 HS384/HS512~~ | ✅ **已完成**（2026-10-24）：**双侧探针**（11 个字节完全相同的 token，Go 走 go-zero `rest/token.TokenParser`、Java 走本拦截器真实路径）⇒「缺 `exp` 两侧都接受」「`iat` 在未来 Go 拒 RAG 收」（**已修**）「HS384/HS512 Go 接受 RAG 拒」（**待签字**）三条全部落定 ⇒ 见 §3.1 |
| **U8** | ~~Go 的 `MaxBytes`/`MaxConns`/`Breaker`/`Shedding` 是 per-route native middleware，RAG 的 4 个是全局 servlet Filter ⇒ 保护层覆盖范围不同~~ | ✅ **已闭环**（2026-10-24 活体实测 **T20** ⇒ 差异真实存在 ⇒ 登记为 **D6** ⇒ **用户已签字** `SD-2026-10-24-CAND-08`：**接受 RAG 更严/更安全的一侧** ⇒ **代码零改动**，不改 `service/common` 的共享 Filter） |
| **U9** | ~~D1（非 JSON Content-Type ⇒ RAG 415）~~ | 🟢 **撤销**（2026-10-24 活体实测：两侧**都是** 200 + `{code:5001}`）⇒ 并已订正 `CommentGlobalExceptionHandler.java` 里那条**错误的 javadoc 断言** ⇒ 见 §3.3 |
| **U10** | D1 的**机制**未查实：Spring 7 + Tomcat 11 组合下，**非 JSON Content-Type 时 servlet input stream 读不出字节**（⇒ Spring 判「body 缺失」）。已排除：本域代码无人调 `getParameter`/`getReader`/`getInputStream`；`GoStyleMaxBytesFilter` 只读 `getContentLengthLong()` 不消费 body | ⚠️ 机制待查（**可观察行为已定**，不影响结论） |
| **U11** | ~~D6 的候选修法会波及六域~~ | ✅ **作废**（2026-10-24 用户签字 **CAND-08** ⇒ 保留全局 Filter ⇒ **不需要改** ⇒ 跨域风险不再存在）。⚠️ 若将来 `align/common` 仍要动保护层覆盖范围，**必须**重估六域并**重新征签** |

---

## 6. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/DRIFT/comment/rpc.md`](./rpc.md) | `comment-rpc` 的独立台账 —— **MISMATCH-API-01 的对端就在那里** |
| [`docs/Intentional_Deviations/comment/api.md`](../../Intentional_Deviations/comment/api.md) | 本台账的签字偏离落点 |
| [`docs/Pitfalls/comment/api.md`](../../Pitfalls/comment/api.md) | 本台账的踩坑落点 |

---

## 2026-10-25 补记：`DRIFT-MSG-V-030` 的**域内部分**已修 —— 本模块产物现在是可执行 jar

> **登记方在 message 域**（[`docs/DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-030`）——
> 它是 V-028 的同款缺陷，但**修法落在本模块自己的 `pom.xml`** ⇒ 本域已修。

| 项 | 修前 | 修后 |
|---|---|---|
| 产物 | **52 KB** library jar，**无 `Main-Class`** | **68 MB** Spring Boot fat jar，`Main-Class: org.springframework.boot.loader.launch.JarLauncher` |
| `java -jar` | 「中没有主清单属性」 | 可启动（外部依赖齐备的前提下） |
| pom | **无 `<build>` 段** | 补 `<build>` 段 + `spring-boot-maven-plugin`（版本由根 pom 的 `spring-boot-starter-parent` 继承，不写 `<version>`） |

**修法**：照 `service/message/message-api/pom.xml` 的 V-028 同款写法，在本模块 pom 补
`<build><plugins><plugin>spring-boot-maven-plugin</plugin></plugins></build>`。
启动类 `CommentApiApplication` 唯一且带 `main` 方法 ⇒ **不显式写 `<mainClass>`**（少一处可能写错的配置）。

⚠️ **本次只证明「产物带 `Main-Class` 且是可执行 fat jar」**，**没有**真起进程验证启动成功
（会撞 Nacos / Redis / Kafka / gRPC 等外部依赖 ⇒ 起不来不等于产物有问题）。

⚠️ **「`mvn package` 成功」不算数** —— 本项目至今的每一项验证都跑在容器 / 测试环境里，
**从来没有碰过 jar 本身**（这正是 §3.3「编译通过 ≠ 产物可运行」的形态）。
⇒ **判据是守门脚本**：`tools/difftest/check-jar-executable.sh`
（逐个 api 模块解包 `META-INF/MANIFEST.MF` 看 `Main-Class`）
⇒ **2026-10-25 实测 `EXIT=0`**（修前 `EXIT=1`）。
