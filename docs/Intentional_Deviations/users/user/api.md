# 有意偏离 · `service/users/user/user-api`

> **这里是「决策」，不是缺陷。** 与 [`../../DRIFT/users/user/api.md`](../../DRIFT/users/user/api.md) 的分工：
> DRIFT 记「需要对齐的差异」，本文件记「**决定不对齐**的差异」。
>
> ⚠️ **读之前先记住两条**：
> 1. 本文件里的条目**不必重复质疑**，但也**不得「顺手修正回旧行为」**。
> 2. 审查时**先来这里确认**，再决定某条红色差异是不是回归。

---

## SD-USERAPI-001（2026-10-23 用户签字）—— 未捕获异常的 500 响应**带完整 envelope**，不复刻 Go 的空 body

| 项 | 值 |
|---|---|
| 对应 DRIFT | `DRIFT-USERAPI-007` |
| 签字日期 | **2026-10-23** |
| 决定 | **保留 RAG 现状**（HTTP 500 + envelope），**不对齐 Go 的空 body** |
| 性质 | ① 有意偏离 —— RAG 侧保留更多信息，不是「忘了做」 |
| 守门网 | `PanicFallbackResponseShapeTest`（2 条，`standaloneSetup` + 真 advice，**零容器 / 0.6 秒**） |

### Go 侧事实（消费点，非声明点）

`go-zero@v1.10.2/rest/handler/recoverhandler.go:12-23` 全文：

```go
func RecoverHandler(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if result := recover(); result != nil {
				internal.Error(r, fmt.Sprintf("%v\n%s", result, debug.Stack()))
				w.WriteHeader(http.StatusInternalServerError)   // ← 只写状态码
			}                                                  // ← 全函数没有任何 w.Write(...)
		}()
		next.ServeHTTP(w, r)
	})
}
```

⇒ Go 侧：**HTTP 500 + 空 body**（连 `Content-Type` 都不是这个 handler 设的）。

### RAG 侧事实

`GlobalExceptionHandler#handleAny:262-272`
⇒ **HTTP 500 + `{"code":5001,"msg":"系统内部错误","data":null}`**。

**状态码两侧一致（500），差异只在 body 形状。**

### 为什么签字保留 envelope（不改成空 body）

1. **原 DRIFT 自己的论证反对 Go 这一侧**：
   `DRIFT-USERAPI-007` 的 `Must Preserve` 栏原文写着
   > 「任何按 body 结构统一解析的客户端都会在这里**踩空**」。

   ⇒ **Go 的空 body 正是「让客户端踩空」的那一版**。对齐它 = 主动降低信息量。
2. **已有同款先例**：项目对 `401` 的 body 形状做过完全一样的决策并签字
   （`SD-2026-10-23-01/02/03` 同批中的 **N-1**，见 [`../_INDEX.md`](../_INDEX.md)）。
   ⇒ 同一类响应形状问题，不应在本域出现相反的处置。
3. **HTTP 500 的语义**：500 意味着「服务端没能正常处理完」。
   带一个**可识别的业务码 5001**，客户端才能区分「系统内部错误」与
   「网关/代理返回的 500」；空 body 做不到这一点。

### 守门网的作用已经反转（这点要特别说明）

`PanicFallbackResponseShapeTest` 的作用**不是**「守住正确」，
而是**守住「不漂」** —— 防止这个已签字的行为在后续重构里被无声改掉。

| # | 用例 | 若被误改会怎样 |
|---|---|---|
| 1 | 未捕获异常 → 500 + `application/json` + `{code:5001,msg:系统内部错误}` | 偏离消失（body 变空） |
| 2 | **对照组**：正常端点仍 200 + envelope | 有人为了对齐 500 而乱动 advice，把 200 路径一起改坏 |

⚠️ 用例 2 的必要性来自 `DRIFT-006` 的历史教训：
修 400 文本时曾出现「为对齐一种状态码而污染另一种」的形态。

### 效力边界（越界即失效）

- 本条**只覆盖「未捕获异常（Go 的 panic 兜底）」这一条路径**的 500 body 形状；
- **不覆盖** `DRIFT-USERAPI-006`（400 的 `Content-Type` 少 charset）——
  那条**未签字**，仍是待对齐项；
- **不覆盖** `DRIFT-USERAPI-010`（黑名单 401 的 `Content-Type` 拼写错误）——
  那条已由 **N-1** 签字「不复刻拼写错误」，属另一条偏离，不要与本条混谈。

### 关闭条件

本条**不设自动关闭条件** —— 它是长期决策。
若将来要撤销（即改为对齐 Go 的空 body），必须同时：
1. 改 `GlobalExceptionHandler#handleAny` 使其返回 null body；
2. 让 `GlobalResponseAdvice` 对该响应透传（否则会被再包成 envelope）；
3. **改写** `PanicFallbackResponseShapeTest` 的期望值 —— 否则它会红；
4. 在本文件追加一条「撤销记录」，保留历史。
---

## SD-USERAPI-002（2026-10-24 用户签字）—— 接受「RAG 的观测面比 Go 大」：401 计入 access log / span / HTTP 指标

| 项 | 值 |
|---|---|
| 对应 DRIFT | `DRIFT-USERAPI-009`（的 **① 可观测性**部分） |
| 签字日期 | **2026-10-24** |
| 决定 | **保留 RAG 现状**（401 进入观测面），**不对齐 Go 的「401 不进观测面」** |
| 性质 | ① 有意偏离 —— RAG 侧**多**记录，不是「忘了做」；方向是**更可观测**，不是更严 |
| 影响面 | **仅观测面**（access log / span / HTTP 指标口径）；**不改变任何响应字节或状态码** |

### Go 侧事实（消费点，非声明点）

`go-zero@v1.10.2/rest/engine.go:101-113` `bindRoute`：

```go
chn = buildChainWithNativeMiddlewares(...)          // 索引 0，最外层
chn = appendAuthHandler(...)                        // JWT
for _, mw := range ng.middlewares { chn = chn.Append(...) }   // observability，再往内
handle = chn.ThenFunc(route.Handler)
```

`chain.Then = m0(m1(...mN(h)))`，索引 0 先执行
⇒ 执行序：**JWT 验签 → observability(3s) → 黑名单 → handler**。

⇒ ① handler 侧 `Authorize` 产生的 **401 不经过 observability**
　 ⇒ **不进 `markHTTPResult`**（`service/common/.../http.go:99-113` 的 Go 版）
⇒ ② 3s 超时预算**不覆盖** JWT 验签。

### RAG 侧事实

`service/common/src/main/java/com/rag/common/spring/HttpMiddlewareFilter.java:36-37`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)     // ← Servlet Filter，在 DispatcherServlet 之外
public class HttpMiddlewareFilter implements Filter {
```

`JwtAuthInterceptor` 是 `HandlerInterceptor`（`WebMvcConfig.java:56-59`），
**在 DispatcherServlet 之内** ⇒ 执行序：

**Filter(MaxConns +1 → Breaker +2 → CpuShedding +3 → HttpMiddleware 3s +5 → MaxBytes +6) → DispatcherServlet → JwtAuthInterceptor → Controller**

> ⚠️ **2026-10-24 勘误**：本行原写「Filter(**MaxBytes → HttpMiddleware 3s**)」，
> **顺序写反了** —— 按 `@Order` 数值，`HttpMiddlewareFilter` 是 `+5`、`GoStyleMaxBytesFilter` 是 `+6`
> ⇒ 数值小的**先执行** ⇒ 实际是 **HttpMiddleware(3s) 在 MaxBytes 之外**。
> ⇒ 本条 ① 的结论（401 计入观测面）**不受影响**（401 由 DispatcherServlet 内的拦截器产生，两种顺序下都在观测面之内）。
> ⇒ 但「MaxBytes 相对观测面的位置」由此变成一条**独立的新差异** ⇒ 见 `SD-USERAPI-003` 与 `docs/DRIFT/users/user/api.md` 的 `DRIFT-USERAPI-009 ②` 补充。

⇒ ① **401 计入** `markHttpResult`（`HttpMiddleware.java:466`）
⇒ ② 3s 预算**覆盖**验签 + 黑名单 + handler。

### 为什么签字接受（不改成 Go 的观测面）

1. **差异方向是「多记」而非「少记」。** 两侧的**响应字节与状态码完全一致**，
   分叉的只是「这条请求进不进看板」。RAG 把**鉴权失败**也计入了 HTTP 指标，
   这在运维上是**想要**的：没有 token 的请求风暴正是最需要被看见的一类流量。
2. **Go 的「401 不进观测面」是 go-zero 的中间件排列副产物，不是设计意图。**
   它源于 `appendAuthHandler` 把 JWT 放在了 observability **外层**这一行代码顺序，
   而不是某条「鉴权失败不记录」的规定。
   ⇒ 对齐它意味着**主动把一批故障流量从监控里抹掉**。
3. **本条不改任何客户端可见行为** ⇒ 签字的代价为零。

### ⚠️ 效力边界（本条**不覆盖**什么）

1. **不覆盖 `DRIFT-USERAPI-009` 的 ② 超时作用域**：Go 的 3s 预算不覆盖 JWT 验签，
   RAG 的覆盖。这**不是观测面差异，是行为差异**，
   且**只有在 Redis 也慢的时候才可观察** ⇒ 仍挂在
   [`UNRESOLVED-004`](../../DRIFT/users/user/api.md)（需真 Redis 故障注入才能判定）。
   ⇒ **不要因为签了本条就把它当成已解决。**
2. **不覆盖 `DRIFT-USERAPI-008`**（go-zero 的 Breaker / Shedding / MaxConns 无对应物）——
   那条**尚未签字**，仍是待决项。
3. **不覆盖 `DRIFT-USERAPI-004`** —— 那条已于同日在
   [`../../DRIFT/users/user/api.md`](../../DRIFT/users/user/api.md) **被证伪关闭**
   （Go 的 user-api 其实**只**吃 JSON body，RAG 已等价），
   与本条无关，不要混谈。

### 守门网

本条**不设自动关闭条件**（观测面差异无法用单测钉住——它取决于中间件顺序，不取决于返回值）。
但有两处**结构性证据**可查：

| 证据 | 位置 |
|---|---|
| 观测面在 DispatcherServlet **之外** | `HttpMiddlewareFilter.java:36-37`（`@Order(HIGHEST_PRECEDENCE+5)`） |
| 鉴权在 DispatcherServlet **之内** | `WebMvcConfig.java:56-59`（`addInterceptor`） |

⇒ **若有人把 `JwtAuthInterceptor` 改成 Filter（或把 `HttpMiddlewareFilter` 的 order 挪到
`DispatcherServlet` 之后），本条的「差异存在」前提就变了** ⇒ 必须回来看这条。

### 关闭条件

本条**不设自动关闭条件** —— 它是长期决策。
若将来要撤销（即让 401 不进观测面），必须同时：
1. 把 `JwtAuthInterceptor` 从 `HandlerInterceptor` 改为 `Filter` 并置于 `HttpMiddlewareFilter` **之外**
   （或反过来调整 order），**并确认响应字节与状态码不变**；
2. 在本文件追加一条「撤销记录」，保留历史。

---

## SD-USERAPI-003（2026-10-24 用户签字）—— **JWT 验签失败的 401 保持完整 envelope**，不复刻 Go 的空 body

| 项 | 值 |
|---|---|
| 对应 DRIFT | `DRIFT-USERAPI-009` ② 的**连带项**（N-1 的延伸与明确化） |
| 签字日期 | **2026-10-24** |
| 适用范围 | **现状**（JWT 验签在 `@ControllerAdvice` 内）**＋ 改法 A 落地后**（JWT 验签移到 Filter）——<br>⚠️ 后者是 **2026-10-24 用户补充拍板**：「009 接受 envelope」⇒ 见下方「适用范围扩展」 |
| 决定 | **保留 RAG 现状**：JWT 验签失败 ⇒ **HTTP 401 + 完整 envelope** `{code,msg,data}`（业务码 1006 `TOKEN已过期`），**不复刻 Go 的「401 + 空 body」** |
| 性质 | 有意偏离 —— 方向是「**统一**」而非「**分裂**」（见「为什么签字」第 2 条） |
| 影响面 | **仅响应体形状**；**HTTP 状态码（401）与 Content-Type 不变**；不涉及任何业务路径 |

### Go 侧事实（消费点，非声明点）

`go-zero@v1.10.2/rest/handler/authhandler.go:106-122` `unauthorized(...)`：

```go
// let callback go first, to make sure we respond with user-defined HTTP header
if callback != nil { callback(writer, r, err) }
// if user not setting HTTP header, we set header with 401
writer.WriteHeader(http.StatusUnauthorized)
```

⇒ `service/user/user/api/usercenter.go` **没有**注册 `rest.WithUnauthorizedCallback`
⇒ callback 为 nil ⇒ **只有一行 `WriteHeader(401)` ⇒ body 为空**。

### RAG 侧事实

`service/users/user/user-api/.../advice/GlobalExceptionHandler.java:73-78`
⇒ `AuthenticationException` ⇒ **HTTP 401** + `{code:1006,msg:"TOKEN已过期",data:null}`。

### 为什么签字接受（不复刻空 body）

1. **与 N-1 同源**：N-1（用户 2026-10-21 已签字）已把「Go 侧两处 401 的 body **互不相同**」
   （`WithJwt` → 空 body；`CheckBlacklistMiddleware` → `{"code":401,"msg":…}`）
   收敛成 RAG 侧的**统一 envelope**。
   ⇒ 本条把这条决策**在 JWT 路径上明确化**，避免将来实施 `DRIFT-USERAPI-009` 改法 A 时被当成「顺手改回」。
2. **改法 A 一旦落地，写空 body 反而会让 RAG 自己分裂**：
   改法 A 把 JWT 验签挪到 Servlet Filter ⇒ 异常**不再被 `@ControllerAdvice` 捕获**
   （`@ExceptionHandler` 只在 `DispatcherServlet` 内生效）⇒ Filter 要自己写响应。
   ⇒ 若此时写空 body ⇒ **RAG 的两处 401 会变成「JWT 空 body + 黑名单 envelope」**
   ⇒ **比现在更不一致**，且要为此在 Filter 里另写一套响应拼装。
   ⇒ 写 envelope ⇒ 与黑名单 401 **形态一致**，且 `GlobalExceptionHandler` 的 4 个 401 分支
   （JWT 验签失败 / claim 畸形 / 黑名单命中 / uid 非法）**共用同一套形状**。
3. **不影响任何「保护」语义**：401 之后请求**一律不放行**（拦截器直接终止链），
   差别只在**响应体长什么样**，不涉及鉴权强度。
4. ⚠️ **本条不覆盖 `DRIFT-USERAPI-009` ② 的「3s 超时作用域」差异** ——
   那是**行为差异**（超时预算覆盖范围），本条只签**响应体形状**。
   ⇒ ② 仍挂 `DRIFT-USERAPI-009`，**不因本条而关闭**。

### 🆕 适用范围扩展（2026-10-24 用户补充拍板：「009 接受 envelope」）

`docs/DRIFT/users/user/api.md` 的 `DRIFT-USERAPI-009 ②` 有一条**改法 A**
（把 JWT 验签从 `JwtAuthInterceptor` 拆成更外层的 Filter，改动全落在 user 域内）。
该改法落地后，JWT 401 会由 **Filter** 直接写出，**不再经过 `@ControllerAdvice`**
⇒ 「body 写空（复刻 Go）还是写 envelope（保留 N-1）」就变成一个**必须先拍板**的问题。

> ✅ **用户 2026-10-24 拍板：接受 envelope。**
> ⇒ **本条的适用范围从「现状」扩展到「改法 A 落地后」**：
> 无论 JWT 验签在 advice 内（现状）还是在 Filter 内（改法 A），
> **401 一律写完整 envelope** ⇒ **N-1 与本条都不因改法 A 而动摇**。

⇒ 连带效果：**`DRIFT-USERAPI-009 ②` 的三个连带项里，②（会推翻 N-1 的一半）已关闭**
⇒ 剩下 ①（① 可观测性会自动对齐 ⇒ `SD-USERAPI-002` 需复核销号）与 ③（推广跨域）
⇒ 即：**改法 A 的「签字档阻力」已清零**，剩下的是销号流程与推广节奏。

### 与 `SD-USERAPI-002` 的关系（两条各管一面，别混）

| 签字 | 管的是 | 方向 |
|---|---|---|
| `SD-USERAPI-002` | **观测面**：401 是否进 access log / span / HTTP 指标 | RAG **多**记 |
| **`SD-USERAPI-003`（本条）** | **响应体形状**：JWT 401 的 body | RAG **统一**成 envelope |

### 关闭条件

本条**不设自动关闭条件** —— 它是长期决策。
若将来要撤销（即 JWT 401 改回空 body），必须同时：
1. 在 JWT 路径上单独写响应（**不能**依赖 `@ControllerAdvice`），
2. 改 `GlobalExceptionHandler` 里对应分支（否则 advice 仍会包 envelope），
3. **改写**鉴权类守门测试的期望值（`AuthenticationHttpStatusTest` 等）—— 否则它们会红，
4. 在本文件追加一条「撤销记录」，保留历史。
