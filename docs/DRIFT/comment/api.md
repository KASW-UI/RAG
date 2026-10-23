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
> - 🔴 **无双侧活体对拍** —— Go 侧 api 依赖 `comment-rpc` + `etcd` + 真 PG，起不来
>
> ⇒ ✅ 一律读作「**在该证据强度下一致**」。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | 🔴 **`SEMANTICALLY NOT EQUIVALENT`**（其中 2 条已修，见下） |
| SEMANTIC MISMATCH | **3 条**（§2）—— **MISMATCH-API-01 / -02 已修并配变异验证**；**-03 未修** |
| OBSERVABLE DIFFERENCE | **3 条**（§3） |
| 等价确认 | **约 40 个判定点**（§4） |
| 组件规模 | Go 1,045 行 / 22 文件 ⇒ RAG 2,838 行 / 33 类 |
| 构建 + 测试 | `mvn -o -pl service/comment/comment-api -am test` ⇒ **BUILD SUCCESS**，**30 / 0 failures / 0 errors / 0 skipped**（本轮实测；修复前 22 条） |

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

### MISMATCH-API-03 · gRPC per-call deadline 缺 **2 秒**上限

| 项 | 值 |
|---|---|
| **Go 事实** | go-zero v1.10.2 `zrpc/config.go:31` `Timeout int64 \`json:",default=2000"\`` ⇒ **2000ms**；`zrpc/internal/client.go:115-116` 装 `TimeoutInterceptor(timeout)`；`etc/commentservice.yaml:21-23` 的 `CommentRpc` **未覆盖** `Timeout` ⇒ 生效值 = **2s** |
| **RAG 现状** | `CommentRpcClient.java:46-48` 只套 `GoStyleRequestDeadline.applyTo(...)` ⇒ 取自 **HTTP 预算 3s**（`application.yml:140` `http-timeout: 3s`）⇒ **有效 deadline = 3s，无 2s 上限** |
| **可观察后果** | 下游耗时落在 **2.0–3.0s**：Go 返 `DEADLINE_EXCEEDED`（`envelope.code = 4`），**RAG 成功**。<br>且 Go 侧该超时**计入熔断失败**（`zrpc/internal/codes/accept.go` 把 DeadlineExceeded 列为失败）⇒ 两侧熔断统计口径也不同 |
| **修法** | 在 `CommentRpcClient.commentRpc()` 上叠一层 2s 上限（如 `withDeadlineAfter(min(remaining, 2000ms))`），或补 `comment.comment-rpc.timeout: 2000` 配置并在此消费 |

---

## 3. OBSERVABLE DIFFERENCE（3 条）

| # | 判定点 | Go 侧事实 | RAG 侧现状 | 性质 |
|---|---|---|---|---|
| **D1** | 非 `application/json` 的 Content-Type | `httpx/requests.go:153-154` `withJsonBody` 假 ⇒ `UnmarshalJsonMap(nil, v)` ⇒ 必填缺失 ⇒ **200 + `{code:5001}`** | `CommentGlobalExceptionHandler.java:208-212` ⇒ **415** | 状态码不同。RAG 侧 javadoc **已自登记**为 OBSERVABLE DIFFERENCE ⇒ 不是本轮新增 |
| **D2** | Bearer 前缀剥离的**长度边界** | `golang-jwt/jwt/v4/request/oauth2.go:8-14`：`len(tok) > 6 && ToUpper(tok[0:7]) == "BEARER "` | `users-common/.../BearerTokenExtractor.java:74-81`：判据 `length() > 7` | 长度恰为 7 的头（`"Bearer "`）：Go 剥成空串→解析失败→**401**；RAG 保留整串→解析失败→**401**。**结局相同**，仅错误文本不同 ⇒ 当前不可观察，可对齐为 `> 6` 以消除隐患 |
| **D3** | 熔断**隔离键** | `zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18` `path.Join(cc.Target(), method)` | `common/protection/GoStyleGrpcClientBreakerInterceptor.java:193` `method.getFullMethodName()`（**无 target 前缀**） | 单 channel 下不可观察；将来多 target 会把计数合并。⚠️ 该类在 `service/common` 下，**改动影响多域** ⇒ 只登记不改 |

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

## 5. 未决 / 待办

| # | 事项 | 类型 |
|---|---|---|
| **U1** | ~~MISMATCH-API-01 的修复 + 真链路守门测试~~ | ✅ **已完成**（见 §0.1 / §2） |
| **U2** | ~~MISMATCH-API-02 的严格解析 + 订正错误断言~~ | ✅ **已完成**（见 §0.1 / §2） |
| **U3** | MISMATCH-API-03 的 2s deadline 上限 | 🔴 **未修** —— 需在 `CommentRpcClient.commentRpc()` 上叠 `withDeadlineAfter(min(remaining, 2000ms))`，或补 `comment.comment-rpc.timeout: 2000` 配置并在此消费 |
| **U4** | **既有台账的过期结论**：`docs/DRIFT/_cross/new-domains-2026-10-23.md:43-45` 记「已登记未修：gRPC client 熔断缺失（Go `zrpc` `Breaker` 默认 `true`）」—— **实测 RAG 侧熔断已存在**（`application.yml:88-89` + `GoStyleGrpcClientBreakerInterceptor`）⇒ **该结论作废**。⚠️ `_cross/` 是共享文件，本工作树不碰，**合并后由主会话更正** | 🔴 需更正共享台账 |
| **U5** | `CommentGrpcError.java:97-103` 那段「若未来 comment-api 引入熔断器」的注释**已过时**（熔断早已引入） | ⚠️ 待订正（属 `comment-common`，本工作树可改） |
| **U6** | `CommentGrpcError.java:150-162` 的「字面搬运」论证需补一句「对 RAG-rpc 对端应读 trailer」 | ⚠️ 待订正（属 `comment-common`）—— ⚠️ 本轮已**订正 `CommentApiSupport` 侧**，`comment-common` 侧尚未动 |
| **U7** | jjwt 对**缺 `exp` claim** 的 token 的接受性 vs Go `jwt.MapClaims.Valid()`；jjwt 是否接受 HS384/HS512 | ⚠️ 待活体对拍 |
| **U8** | Go 的 `MaxBytes`/`MaxConns`/`Breaker`/`Shedding` 是 **per-route native middleware**（`rest/engine.go:103-108`），RAG 的 4 个是**全局 servlet Filter** ⇒ 未注册路径（404/405）上保护层覆盖范围不同 | ⚠️ OBSERVABLE（latent） |

---

## 6. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/DRIFT/comment/rpc.md`](./rpc.md) | `comment-rpc` 的独立台账 —— **MISMATCH-API-01 的对端就在那里** |
| [`docs/Intentional_Deviations/comment/api.md`](../../Intentional_Deviations/comment/api.md) | 本台账的签字偏离落点 |
| [`docs/Pitfalls/comment/api.md`](../../Pitfalls/comment/api.md) | 本台账的踩坑落点 |
