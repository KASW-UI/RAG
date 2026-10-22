<!-- @move AGENTS.md L1889-L1924 · §3.4.2 service/users 语义审计后两条新增留档 -->
#### 3.4.2 `service/users` 语义审计后的两条新增留档（2026-10-21）

Go → Spring Boot 语义一致性审计 `service/users/user`（9 RPC + 9 HTTP 端点 + `users-common`）产出
**3 项 SEMANTIC MISMATCH + 8 项 OBSERVABLE DIFFERENCE + 6 项 UNRESOLVED**，已修 11 处 + 新增 22 条回归测试。
以下两条**是修复后仍然存在的有意偏离**：

| # | 偏离 | 内容 | 理由 | 关闭条件 |
|---|---|---|---|---|
| **N-1** | **鉴权失败的响应体形状** | Go 侧两处 401 的 body **互不相同**：`rest.WithJwt` → `authhandler.go:106 unauthorized()` 写 **HTTP 401 + 空 body**；`CheckBlacklistMiddleware` → `checkblacklistmiddleware.go:38` 写 **HTTP 401 + `{"code":401,"msg":"登录已失效,请重新登陆"}`**（无 `data`）。RAG 两侧统一成 **HTTP 401 + 完整 `{code,msg,data}`** | **用户 2026-10-21 拍板**。HTTP **状态码 401 已与 Go 对齐**（这是本次修掉的 SEMANTIC MISMATCH）；body 统一属于表达层收敛——Go 自身两处不一致，RAG 不复制这种不一致。业务码仍是 `ERROR_TOKEN_RUNTIME(1006)` | 若要求逐字复刻 Go 的两种 body，则需在拦截器里区分「JWT 验签失败」与「黑名单命中」两条路径分别写 body |
| **N-2** | **Go 真源的中文错误消息乱码** | Go `user/rpc/internal/logic/` 下 `uploadavatarlogic.go` / `selectavatarlogic.go` / `getavatarhistorylogic.go` 共 **9 处** `status.Error(...)` 的中文 message 是 **GBK 双重编码乱码**（`鐢ㄦ埛涓嶅瓨鍦?`，尾字节已丢、还原不出「用户不存在」）；而同模块 `loginlogic.go` / `registerlogic.go` / `getuserlogic.go` / `updateuserlogic.go` / `deleteuserlogic.go` 是正常 UTF-8。RAG 写的是正确中文 | 取证：`"鐢熸垚澶辫触".encode('gbk').decode('utf-8') == "生成失败"`。Go 已冻结（§2 不接受写入），无法在真源侧修；照抄乱码会让日志与 gRPC status message 对人类不可读。**用户 2026-10-21 拍板：RAG 保留正确中文**，沿用 §3.4.1 A-2（`Logger.isFiltered`）的既有先例。影响面仅 gRPC status message——Go api 层只按 `status.Code` 分支、不透传 message，**不进 HTTP body** | 若日后在 Go 侧修正编码，需同步 RAG 的 9 条 message |

> **这两条的共同教训**：N-1 的修复前状态是「HTTP 200 + 业务码 1006」，而支撑它的注释写着
> 「401 只由鉴权层的 Servlet Filter 写出，不经过本处理器」——**本工程根本没有这样的 Filter**，
> 鉴权是 `HandlerInterceptor` 抛异常、必然经过该处理器。
> 这是一条**引用了不存在的机制**来论证 idiomatization 的典型，
> 与 §3.2「无出处 idiomatization」同类，但更隐蔽：它有出处、只是出处是假的。
> 新的防线是 `AuthenticationException`：**层次由类型承载，不靠业务码推断**
> （1006 在鉴权层与 Service 层都会出现，按码分流必然搅在一起）。
> 锁定用例：`user-api` 的 `AuthenticationHttpStatusTest`（3 条，含「同码不同型 → 不同状态码」）。

> **N-1 的二次坍塌（2026-10-21 补）**：§6.1 第 10 项「HTTP 状态码语义变更」修了
> 「`GlobalExceptionHandler` 拿不到 401」这一层。但还有一层被跳过：
> **`HttpMiddleware$ObservabilityResponseWriter.setStatus` 的 status 没 propagate 到
> `MockHttpServletResponse`**——下游 `outputMessage.flush()` 触发 `flushBuffer()` 把底层
> `setCommitted(true)`，再走 `HttpMiddleware.flushTo` 的 `dst.setStatus(401)` 就被守卫吞掉。
> **`AuthenticationHttpStatusTest` 用的是 `standaloneSetup`**，不挂 `HttpMiddleware` filter；
> 这就是它能在 user-api 写完时全绿、而 admin-api 实测却出 HTTP 200 的根因——两层一起错时
> 单独那一层全绿就够骗过 CI。直到 `AdminApiMvcPipelineTest`（真 MVC + 真 filter）才把它们联起来。
> 锁定用例（覆盖本批发现的 DRIFT-FRAMEWORK-001）：`admin-api` 的 `AdminApiMvcPipelineTest`
> 4 条 401 路径用例（`blacklistCheckedBeforeSignatureVerification` /
> `invalidSignatureAfterBlacklistMiss` / `missingHeaderSkipsBlacklistLookup` / `emptyBearerTokenRejected`）。
> 修复方法：`HttpMiddleware$ObservabilityResponseWriter.setStatus` / `sendError`
> 立即 propagate 到 underlying（用 `(HttpServletResponse) this.original` 绕过
> javac 对 inner class `getResponse()` 的回退到 `ServletResponse` 的怪题），
> 见 §6.2.2 DRIFT-FRAMEWORK-001 行。

<!-- @move AGENTS.md L2387-L2409 · §6.1 service/users 行为一致性审计已完成 -->
### 6.1 service/users：行为一致性审计已完成（本次）

Go ↔ RAG 对照审计 `service/users` 全部 5 个子模块（22 个 RPC / 22 个 HTTP 端点），**结论：修复前不具备验收条件**。

**已修复（10 项，全部有回归测试锁定）**：

| # | 问题 | 类别 |
|---|---|---|
| 1 | `@ResponseBodyAdvice` 二次包裹 `@ExceptionHandler` 的 `ResponseEntity` → **全部错误响应顶层 `code` 恒为 200** | 实测确认的致命缺陷 |
| 2 | `CreateAdmin` 4 步事务 self-invocation → `@Transactional` 静默失效 | 安全（邀请码可并发消费） |
| 3 | `CreateAdmin` 邀请码查询缺 `FOR UPDATE`（注释写了锁、SQL 没有） | 安全 |
| 4 | `UpdateUser` 写 `extra_info` 走 `wrapper.set(Map)` → jsonb 被绑成 varchar | 功能失败 |
| 5 | `SelectAvatar` 响应 `is_current` 返回更新前的旧值 | 响应自相矛盾 |
| 6 | `UpdateSelf` 空 email 把列写成 SQL NULL → 只改密码会丢邮箱 | 数据丢失 |
| 7 | `JwtUtil` zero-pad 被删 → admin 默认密钥（15B）签发必抛 `WeakKeyException` | 启动阻断 |
| 8 | 登录 `UNAUTHENTICATED` 未映射 → 密码错报成 1015「服务繁忙」 | 用户可见回退 |
| 9 | uid 解析失败静默转 `0` → 坏请求报成 1003「用户不存在」 | 错误分类丢失 |
| 10 | HTTP 状态码语义变更（业务码 1004/1006/1009 → 401；框架错误 → 500） | wire 契约 |
| 10' | §6.2.2 **DRIFT-FRAMEWORK-001** —— 第 10 项的修复在 admin-api 侧**实际上未生效**：`HttpMiddleware$ObservabilityResponseWriter.setStatus` 只更新缓存字段，等下游 `outputMessage.flush()` 把底层 `MockHttpServletResponse.flushBuffer` 提交后，`flushTo` 的 `dst.setStatus(401)` 才会被 `!isCommitted()` 守卫吞掉 → 任何 `@ExceptionHandler → ResponseEntity.status(...)` 在运行期都退化为 HTTP 200。这是同一份代码里本批 §6.2.3 A-U0 admin-api 侧补验时发现的 | 实测确认的根因；原表第 10 项已修，但缺少「transport 层 status 必须 propagate 到 underlying」的链路。本批**新增**；该 bug 同时破坏 user-api 的 N-1「推到 401」——`AuthenticationHttpStatusTest` 看似通过是因为它走的是 `standaloneSetup`，不挂 `HttpMiddleware` filter；用 `AdminApiMvcPipelineTest`（真 MVC 管线）才能复现 |

新增 **19 个回归测试**（含 2 个 `ResponseEnvelopeAdviceTest` 把 Web 层纳入回归网——此前 RAG 无任何 `@WebMvcTest`/`MockMvc`，这是这批缺陷能长期潜伏的根因）。
验证：`mvn -pl <5 modules> -am test` + `package` 全绿（users-common 3 / service-common 69 / admin-rpc 24 / admin-api 46 / user-rpc 46 / user-api 30）。

