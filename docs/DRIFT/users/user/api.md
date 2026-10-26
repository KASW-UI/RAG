# 语义对照台账 · `service/users/user/user-api`

| | |
|---|---|
| **Go 侧** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/user/user/api`（含 `service/user/user/identity/`，该组件的一部分） |
| **RAG 侧** | `/Users/pr/Documents/java&golang/RAG/service/users/user/user-api` |
| **核对日期** | **2026-10-24（第十轮）** —— ⚠️ 原写「2026-10-23」，**已过期** |
| **判定** | **SEMANTICALLY NOT EQUIVALENT** —— **0 项 SEMANTIC MISMATCH + 3 项 OBSERVABLE DIFFERENCE + 0 项 UNRESOLVED**<br>（009② 3s 预算作用域 · 013 过载保护〔跨域〕· 015 Redis 主机名解析失败〔**2026-10-26 定性降级为「条件性成立 · 证据不足」**〕）⇒ 与 §10 §39 一致<br>⚠️ 原写「2 项 SEMANTIC MISMATCH + 7 项 OBSERVABLE DIFFERENCE + 5 项 UNRESOLVED」，那是 **2026-10-23 的状态，已过期** |
| **规模** | 9 个 HTTP 端点 / 9 个 gRPC 出向调用 / 1 个 Redis 出向 / 7 个业务码分支族 |
| **入口** | **§7 条目总索引**（015 条漂移的状态与落点，一表看完）· §10 最终判定 · §8 测试用例与守门清单 |

## 证据强度声明（本台账每条结论都带标注）

| 标记 | 含义 | 现状（2026-10-24） |
|---|---|---|
| `静态源码` | 只读了两侧源码，未运行任何一侧 | 仍占多数条目 |
| `字节码核实` | 对 Spring jar 做了 `javap` 反汇编取证（不是靠记忆/文档） | DRIFT-USERAPI-006 |
| `真 PG` | 真 PostgreSQL 参与 | 本组件无 DB 访问 → 0 条 |
| `纯 Mockito` | 数据层被 mock | 0 条（本组件无 DB） |
| `双侧活体对拍` | Go 进程与 RAG 进程同时跑 | **多条** —— 第三轮（claim 语义 17 步）· 第六轮（真 Tomcat 400 header）· 第七轮（客户端熔断 E2E）· 第八轮（四个过载机制）· 第九轮（Redis 故障注入）· **第十轮（Redis「从未连上」两形态）** |

> ⚠️ **原写「本轮没有任何一条结论来自双侧活体对拍」—— 该句是 2026-10-23 的状态，已过期。**
> ⇒ 现在本台账有**多条**结论来自双侧活体对拍（见上表与 §8 的 CASE 表）。
> ⇒ 按 `AGENTS.md` §3.3：**「静态对照」不等于「已闭环」**，反之「活体对拍」也不等于**全部**闭环 ——
> 仍 OPEN 的 3 条见 §10 的适用边界。

> ⚠️ **依赖前提（用户任务书给定，必须原样保留）**：`user-rpc` 由**另一个并行会话**审查。
> 本组件**不假设对端 rpc 是正确的**。凡结论依赖 `user-rpc` 响应语义的，一律进 §9 并注明「需合并判读」。

## 编号说明（为什么不用 `DRIFT-003/004/005/...`）

本文件此前**没有任何 `DRIFT-*` 编号条目**（只有 §0 的搬移留档 N-1/N-2 与 §6.1 表格）。
但 RAG 源码 javadoc 已在引用 users 域的**全局**编号：
`DRIFT-003`（`AvatarSelectService.java:79`）、`DRIFT-004`（`GlobalExceptionHandler.java:59,282`）、
`DRIFT-005`（`UserInfo.java:15`、`HttpMiddleware.java:543`）、`DRIFT-006`（`GlobalResponseAdvice.java:60`、`GlobalExceptionHandler.java:192`）、
`DRIFT-009`（`BearerTokenExtractor.java:14,72`）、`DRIFT-010`（同上）、
`DRIFT-016`（`GoStyleRequest.java:34`、`UserController.java:67`）、
`DRIFT-NEW-001/008/014/015/016`（`docs/DRIFT/users/_shared.md`）。

⇒ 本轮新条目**统一使用 `DRIFT-USERAPI-0xx`**，与上述全部已有编号零冲突。
（若日后收敛到全局号段，`USERAPI-00x` 是本组件的稳定映射键。）

---

## 1. Component Contract（§4 + §5）

```text
Component:   user-api —— User Center 的 HTTP 网关
Purpose:     ① 认证（登录/注销，令牌签发与拉黑）
             ② 身份解析（JWT claim → 权威 user-rpc 的活跃用户）
             ③ 用户资料读写的事务编排代理（自身不持有任何 DB）
             ④ 头像三端点的编排代理
             ⑤ 统一响应信封 {code,msg,data} + 业务埋点

Public API:  9 个 HTTP 端点（Go 9 个 / RAG 9 个，数量一致）
Dependencies:
  - user-rpc（gRPC，9 个方法）—— 唯一的业务真相
  - Redis（黑名单 EXISTS）—— 唯一的鉴权外部依赖
  - 无 DB、无 MQ、无外部 HTTP
State:       无状态。Service / Interceptor / Advice 均无可变字段
             （计数器在 Micrometer 内部，逻辑上无状态）
Side Effects: ① 登录成功时签发 JWT（无持久化）
              ② 注销把 token 写进 Redis 黑名单（副作用发生在 **user-rpc** 侧）
              ③ metrics 计数（login 5 个 + register 2 个）
              ④ OTel span / access log（HttpMiddleware）
Failure Model:
  - 输入错误 → HTTP 400 + text/plain（Go `http.Error` 形状）
  - 业务错误 → HTTP 200 + {code, 业务码, data:null}
  - 鉴权错误 → HTTP 401 + {code:1006, ...}
  - 框架错误 → 405 / 415 / 500
```

**成功意味着什么**：Go 侧成功 = `httpx.OkJson` 写出 HTTP 200 + `code==200`。
`data` 里的 `success` 布尔只出现在 delete / logout 两个端点，且**只可能是 `true`**（见 §6.11）。

---

## 2. GO-SEMANTIC-MODEL（§7）

### 2.1 路由与鉴权拓扑

```text
usercenter.go:35-41
  httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)   // 读 conf.Timeout
  server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, ...))  // 全局 Use
  handler.RegisterHandlers(server, ctx)
```

`rest/engine.go:101-116 bindRoute` 决定链序：

```text
chn := ng.buildChainWithNativeMiddlewares(...)   // Trace/Log/Prometheus/MaxConns/
                                                  // Breaker/Shedding/Timeout(已关)/Recover/
                                                  // Metrics/MaxBytes/Gunzip   ← 索引 0，最外层
chn  = ng.appendAuthHandler(fr, chn, ...)        // handler.Authorize(JWT)   ← 追加
for _, mw := range ng.middlewares { chn = chn.Append(...) }   // observability  ← 再追加
handle := chn.ThenFunc(route.Handler)            // route.Handler 内含黑名单包装
```

`rest/chain/chain.go` 的 `Then = m0(m1(...mN(h)))` ⇒ 索引 0 最外层先执行：

```text
执行序：原生中间件 → MaxBytes → … → JWT 验签 → observability(3s) → 黑名单 → handler
```

⚠️ **两个可观察推论**（本轮多处结论的根）：
1. **JWT 401 不经过 observability** ⇒ 不计入 `markHTTPResult`。
2. **黑名单在 3s 预算之内、JWT 验签在 3s 预算之外。**

### 2.2 public / protected 路径集合（`internal/handler/routes.go`）

| 路径 | Method | 中间件 |
|---|---|---|
| `/usercenter/v1/user/login` | POST | **无** |
| `/usercenter/v1/user/register` | POST | **无** |
| `/usercenter/v1/user/delete` | DELETE | WithJwt + CheckBlacklist |
| `/usercenter/v1/user/get` | GET | WithJwt + CheckBlacklist |
| `/usercenter/v1/user/avatar/history` | GET | WithJwt + CheckBlacklist |
| `/usercenter/v1/user/logout` | POST | WithJwt + CheckBlacklist |
| `/usercenter/v1/user/avatar` | POST | WithJwt + CheckBlacklist |
| `/usercenter/v1/user/avatar/select` | POST | WithJwt + CheckBlacklist |
| `/usercenter/v1/user/update` | POST | WithJwt + CheckBlacklist |

### 2.3 逐 OP 的 Go 语义模型（§6 字段）

<details><summary><b>OP-01 Login</b>（public）</summary>

```text
Operation:   POST /usercenter/v1/user/login
Input:       LoginReq{Username string `json:"username"`, Password string `json:"password"`}  ← 无 ,optional ⇒ 标量必填
Precondition: 无（public）
Trigger:     handler 调 httpx.Parse → Logic.Login
Processing:  1) Parse 失败 → httpx.ErrorCtx → 400 text/plain（见 §2.0）
             2) UserRpc.Login({Username, Password})
             3) err → metrics rpc_error；Internal→5001 / default→1015      (loginlogic.go:49-55)
             4) rpcResp.Status==1 → metrics wrong_password → 1002         (loginlogic.go:58-62)
             5) rpcResp.Status==2 → metrics user_banned → 1011           (loginlogic.go:64-68)
             6) jwt.GetToken(secret, now, AccessExpire, rpcResp.Uid)     (loginlogic.go:74)
                失败 → metrics token_error → 1006
             7) 成功 → metrics success
External:    user-rpc.Login；Redis 无
Result:      200 + {code:200, msg:"OK", data:{token:"<HS256 JWT>"}}
Timeout:     受 observability 3s 预算约束（config.go:55 `Timeout json:",default=3000"`）
Retry:       无
Concurrency: 无
```
</details>

<details><summary><b>OP-02 Register</b>（public）</summary>

```text
Input:       CreateUserReq{Username, Password 无 tag；Email `,omitempty`；Extrainfo map `,omitempty`}
             ⚠️ go-zero 只认 `,optional`（core/mapping/utils.go:18-20 optionalOption）；
                `,omitempty` 不在选项表里、被静默忽略 ⇒ Username/Password/Email **三者全必填**；
                Extrainfo 是 map ⇒ 走 unmarshaler.go 的 Map 分支、**缺席不校验**，但显式 null 报 mustn't be nil
External:    user-rpc.Register
Result:      200 + {code:200, msg:"OK", data:{uid:"<string>"}}     ← CreateUserResp.Uid `json:"uid,string"`
Error map:   Internal→5001 / AlreadyExists→1001 / default→1015      (registerlogic.go:49-56)
             ⚠️ **没有 NotFound 分支** ⇒ NotFound 落 default→1015
```
</details>

<details><summary><b>OP-03 Get</b>（protected）</summary>

```text
Input:       GetUserReq{}（零字段，handler 仍调 httpx.Parse）
Processing:  identity.ResolveUser(ctx, UserRpc)   —— getuserlogic.go:36
             5 个 sentinel + 1 个 gRPC 分支，逐条见 OP-08
Result:      200 + {code:200, msg:"OK", data:{user:{uid:"…",score:0,username,email?,extra_info?}, found:true}}
             ⚠️ UserInfo.Score `json:"score"` 无 omitempty ⇒ **恒输出 0**
                （getuserlogic.go:66-71 构造时不赋 Score）
Metrics:     不写（1:1）
```
</details>

<details><summary><b>OP-04..OP-09（update / delete / logout / avatar ×3）</b></summary>

```text
update   Input UpdateUserReq 四字段全 `,optional` ⇒ 一个都不校验
         uid ← ctx["userId"].(json.Number).Int64()      ← updateuserlogic.go:36-46 【无 uid<=0 判定】
         Error Internal→5001 / AlreadyExists→1001 / NotFound→1003 / default→1015

delete   Input DeleteUserReq{}（空 struct）
         uid ← ctx["userId"].(json.Number).Int64()      ← deleteuserlogic.go:37-47 【无 uid<=0 判定】
         Error 条件是 `err != nil || rpcResp.Success == false`
               NotFound→1003 / Internal→5001 / default→1015   ← deleteuserlogic.go:54-64
               （err==nil 且 Success==false 时 status.FromError(nil)=OK ⇒ default⇒1015）
         成功 → data:{success:true}

logout   token ← ctx["jwt_token"]（黑名单中间件写入）；nil → 1004    ← logoutlogic.go:36-40
         Error Internal→5001 / default→1015                        ← logoutlogic.go:48-53
         成功 → data:{success:true}

avatar/history  uid ← identity.ClaimedUID(ctx)【有 uid<=0 判定】
                Limit 硬编码 30（getavatarhistorylogic.go:40）
                Error NotFound→1003 / Internal→5001 / default→1015
                空 list ⇒ make([]…,0,0) ⇒ "list":[]

avatar/upload   avatar_url TrimSpace=="" → 500（uploadavatarlogic.go:40-43）
                uid ← identity.ClaimedUID(ctx)【有 uid<=0 判定】
                InvalidArgument→500 / NotFound→1003 / Internal→5001 / default→1015

avatar/select   history_id TrimSpace=="" → 500（selectavatarlogic.go:40-43）
                uid ← identity.ClaimedUID(ctx)【有 uid<=0 判定】
                InvalidArgument→500 / NotFound→**500** / Internal→5001 / default→1015
                ⚠️ NotFound→500 是 Go 刻意保留的区分（不是笔误）
```

### 2.4 identity 层的两套 uid 语义（**本轮最重要的 Go 侧事实**）

| 入口 | 取值方式 | 出处 | `uid <= 0` |
|---|---|---|---|
| `get` | `identity.ResolveUser → ClaimedUID` | `identity/resolve.go:66-79` | **拒绝**（`:75`） |
| `avatar/history` `avatar` `avatar/select` | `currentUserID → identity.ClaimedUID` | `avatarhelpers.go:11-13` | **拒绝** |
| **`delete`** | **Logic 内联** `ctx.Value("userId").(json.Number)` + `Int64()` | `deleteuserlogic.go:37-47` | **不拒绝** |
| **`update`** | **Logic 内联**，同上 | `updateuserlogic.go:36-46` | **不拒绝** |

Go 侧自己已经实测过这条：`internal/logic/user/getuserlogic_test.go:51-52`
（`{"zero", json.Number("0"), …, errmsg.ErrorTokenRuntime, 0}` 与
`{"client-value-not-jwt", int64(9123), …, errmsg.ErrorTokenRuntime, 0}`）
——但**只覆盖了 `get`**，delete/update 从未被测过，所以两套语义在 Go 侧一直并存。

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（§7 / §34，与 §2 同构）

### 3.1 路由与鉴权拓扑

```text
UserApiApplication.java:21-24  scanBasePackages = {com.rag.users.user.api, com.rag.common}
WebMvcConfig.java:56-59        registry.addInterceptor(jwtAuthInterceptor).addPathPatterns("/user/v1/**")
com.rag.common.spring.HttpMiddlewareFilter:36-37   @Component @Order(HIGHEST_PRECEDENCE + 5)  → Servlet Filter
com.rag.common.protection.GoStyleMaxBytesFilter:82-83  @Component @Order(+6)              → Servlet Filter
```

```text
执行序：全部 Servlet Filter（MaxConns +1 → Breaker +2 → CpuShedding +3 → HttpMiddleware 3s +5 → MaxBytes +6）
        → DispatcherServlet
        → JwtAuthInterceptor.preHandle（JWT → 黑名单）→ Controller → Service
```

> ⚠️ **2026-10-24 勘误**：本段原写「全部 Servlet Filter（**MaxBytes → HttpMiddleware 3s**）」，
> **顺序写反了** —— 按 `@Order` 数值，`HttpMiddlewareFilter` 是 **+5**、`GoStyleMaxBytesFilter` 是 **+6**
> ⇒ 数值小的**先执行** ⇒ 实际是 **HttpMiddleware(3s) 在 MaxBytes 之外**。
> ⇒ 由此**派生出一条新的观测面差异**（Go 侧 MaxBytes 在 observability 之外，RAG 侧在之内）
> ⇒ 已记入 `DRIFT-USERAPI-009 ②` 的补充小节。

⚠️ **与 Go 的结构性差异**：RAG 的 Filter 一律在 `DispatcherServlet` **外**，
Go 的 MaxBytes 原生中间件在 JWT **外**（同为 index 0 侧）⇒ MaxBytes↔401 关系一致 ✅；
但 **observability 在 Go 侧是 JWT 之内、RAG 侧是 JWT 之外** ⇒ 见 DRIFT-USERAPI-009。

### 3.2 逐 OP 的 RAG 语义模型

<details><summary><b>9 个端点的 RAG 侧入口</b>（`UserController.java`）</summary>

| RAG 端点 | Method | JWT | 必填校验 | Service | 对应 Go |
|---|---|---|---|---|---|
| `/user/v1/register` | POST | ✗ | `GoStyleRequest.normalize` | `UserRegisterService` | OP-02 |
| `/user/v1/login` | POST | ✗ | `GoStyleRequest.normalize` | `UserLoginService` | OP-01 |
| `/user/v1/logout` | POST | ✓ | 无 | `UserLogoutService` | OP-07 |
| `/user/v1/get` | POST | ✓ | 无（零字段 DTO） | `UserGetService` | OP-03 |
| `/user/v1/update` | POST | ✓ | 无（全 `,optional`） | `UserUpdateService` | OP-05 |
| `/user/v1/delete` | POST | ✓ | 无 | `UserDeleteService` | OP-06 |
| `/user/v1/avatar/select` | POST | ✓ | `normalize` | `AvatarSelectService` | OP-09 |
| `/user/v1/avatar` | POST | ✓ | `normalize` | `AvatarUploadService` | OP-08 |
| `/user/v1/avatar/history` | POST | ✓ | 无 | `AvatarHistoryService` | OP-04 |

public 路径集合（`JwtAuthInterceptor.java:65-67`）：
`Set.of("/user/v1/register", "/user/v1/login")` ⇒ **与 Go 的 2 条 public 集合逐条一致** ✅
（路径前缀不同属已签字偏离，见 §6.2）。

</details>

<details><summary><b>RAG 侧的 uid / token 取值</b></summary>

```text
JwtAuthInterceptor.java:146   request.setAttribute("userId", Long.valueOf(uid))
JwtAuthInterceptor.java:184   request.setAttribute("jwt_token", jwtToken)
IdentityResolver.java:97-110  claimedUID: 必须 instanceof Long 且 uid > 0，否则 ErrInvalidClaimException
UserLogoutService.java:49-54 tokenAttr==null → 1004；(String) tokenAttr
```

⚠️ **RAG 把 Go 的两套 uid 语义合并成了一套**（`claimedUID` 全部端点共用）⇒ DRIFT-USERAPI-002。

</details>

<details><summary><b>RAG 的响应出口（3 条路径逐条核对）</b></summary>

```text
成功路径：Controller 返回 DTO
        → GlobalResponseAdvice.beforeBodyWrite  包成 Response(200,"OK",body)
        → HTTP 200 + application/json

业务异常：Service 抛 BusinessException
        → GlobalExceptionHandler.handleBusiness:49-56
        → ResponseEntity(200).body(Response(code,msg,null))
        → beforeBodyWrite 判 body instanceof Response ⇒ 放行【不二次包裹】
        → HTTP 200 + application/json

鉴权异常：JwtAuthInterceptor 抛 AuthenticationException
        → handleAuthentication:73-78 → HTTP 401 + Response(1006,…)

输入错误：GoStyleRequest.normalize 抛 GoRequiredFieldException
        → handleGoRequiredField:185-189 → goStylePlainTextBadRequest:214-220
        → HTTP 400 + text/plain + nosniff
        → beforeBodyWrite 因 contentType 非 JSON 放行

框架错误：HttpMessageNotReadable / 405 / 415 / Exception
        → 400 text/plain / 405 空 / 415 空 / 500 + envelope(5001)
```

✅ **`@ResponseBodyAdvice` 二次包裹已修**：`GlobalResponseAdvice.java:57-59`（`null` 或 `Response` 直接放行）
+ `:70-73`（非 JSON contentType 放行）。
✅ **HTTP 状态码语义已修**：`GlobalExceptionHandler.decideStatus:294-296` 恒 200，鉴权层单独走 401，
且 `HttpMiddleware.ObservabilityResponseWriter.setStatus:560-575` 立即 propagate 到 underlying
（DRIFT-FRAMEWORK-001 的修法），`setHeader/setContentType:660-704` 同样立即 propagate。

</details>

---

## 4. Equivalence Matrix（§24 + §35）

Result 只用 5 个枚举值。Evidence 列的强度标记见文件头。

| # | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| 1 | ALL | 路由前缀 `/usercenter/v1` → `/user/v1` | `/usercenter/v1/user/*` | `/user/v1/*` | OBSERVABLE DIFFERENCE（**已签字**） | `signed-deviations.md:8`；`routes.go:29,74` vs `UserController.java:100` · 静态源码 |
| 2 | ALL | HTTP method（GET/DELETE 混用 → 全 POST） | 5 GET/DELETE | 全 POST | OBSERVABLE DIFFERENCE（**已签字**） | `signed-deviations.md:9`；`routes.go:36-70` · 静态源码 |
| 3 | ALL | public 路径集合 | login, register | register, login | **EQUIVALENT** | `routes.go:16-30` vs `JwtAuthInterceptor.java:65-67` · 静态源码 |
| 4 | ALL | protected 路径集合（7 条） | delete/get/history/logout/avatar/select/update | 同 7 条 | **EQUIVALENT**（前缀已计入 #1） | `routes.go:32-75` vs `UserController.java:154-204` · 静态源码 |
| 5 | ALL | 响应信封 `{code,msg,data}` | `response.Response`（`common/response/response.go:3-7`，`data` 无 omitempty） | `com.rag.common.response.Response`（无 `@JsonInclude`） | **EQUIVALENT** | Go `httpx.OkJson` + RAG `GlobalResponseAdvice:74` · 静态源码 |
| 6 | ALL | 业务失败的 HTTP 状态码 | 恒 200（`OkJson`） | 恒 200（`decideStatus:294-296`） | **EQUIVALENT** | `rest/httpx/responses.go:52` · 静态源码 |
| 7 | Login | `Status==1 → 1002` | `loginlogic.go:58-62` | `UserLoginService.java:101-105` | **EQUIVALENT** | 静态源码 |
| 8 | Login | `Status==2 → 1011` | `loginlogic.go:64-68` | `UserLoginService.java:108-112` | **EQUIVALENT** | 静态源码 |
| 9 | Login | JWT 签发（HS256 / claim `userId` / `exp`+`iat`） | `jwt/jwt.go:7-15` | `JwtUtil.java:12-23` | **EQUIVALENT**（条件项见 U-002） | 静态源码 |
| 10 | Register | email 必填（`,omitempty` ≠ `,optional`） | `types.go:9` | `RegisterReq.java:58` | **EQUIVALENT** | `core/mapping/utils.go:18-20`（消费点）· 静态源码 |
| 11 | Register | `extra_info` 缺席不校验 / 显式 null 报错 | `unmarshaler.go` Map 分支 | `GoStyleRequired.explicitNull:67-71` | **EQUIVALENT** | `RegisterReq.java:61` · 静态源码 |
| 12 | Register | `uid` 序列化为 **字符串** | `types.go:14 json:"uid,string"` | `RegisterResp.java:17-19` `String` | **EQUIVALENT** | 静态源码 |
| 13 | Get | 5 个 identity sentinel → 5 个业务码 | `identity/resolve.go:99-111` | `IdentityResolver.java:131-148` | **EQUIVALENT** | 静态源码 |
| 14 | Get | `status` presence（缺失 → 1015） | `resolve.go:105-107` | `IdentityResolver.java:142-144` `hasStatus()` | **EQUIVALENT** | 静态源码 |
| 15 | Get | `user.Uid != claim → 5001` | `resolve.go:102-104` | `IdentityResolver.java:138-140` | **EQUIVALENT** | 静态源码 |
| 16 | Get | `score` **恒输出 0** | `types.go:94` 无 omitempty + 不赋值 | `UserInfo.java:57` `getScore()` 兜底 0L | **EQUIVALENT** | 静态源码 |
| 17 | Get/History | `uid <= 0 → 1006` | `resolve.go:75` | `IdentityResolver.java:106-108` | **EQUIVALENT** | 静态源码 |
| 18 | **Update/Delete** | **`uid <= 0` 不拒绝，透传给 rpc** | `updateuserlogic.go:36-46` / `deleteuserlogic.go:37-47` | `IdentityResolver.claimedUID` **拒绝** | **SEMANTIC MISMATCH** | **DRIFT-USERAPI-002** · 静态源码 |
| 19 | **ALL protected** | **`userId` claim 缺失/非数字 → 200 + 1006** | `authhandler.go:65-79` 不校验 claim；Logic 报 1006 走 `OkJson` | `JwtAuthInterceptor.java:138-153` 抛异常 → **401** | **SEMANTIC MISMATCH** | **DRIFT-USERAPI-001** · 静态源码 |
| 20 | Delete | `Success==false` 且无 err → 1015 | `deleteuserlogic.go:54-64` | `UserDeleteService.java:67-70` | **EQUIVALENT** | 静态源码 |
| 21 | Update | 全字段 `,optional`；缺席 vs 空串都不校验 | `types.go:48-53` | `UpdateUserReq`（无 `assertGoRequiredFields`） | **EQUIVALENT** | 静态源码 |
| 22 | Update | `extra_info` map 透传 | `updateuserlogic.go:53` | `UserUpdateService.java:61` | **EQUIVALENT** | 静态源码 |
| 23 | Logout | 无 `jwt_token` → 1004 | `logoutlogic.go:36-40` | `UserLogoutService.java:49-53` | **EQUIVALENT** | 静态源码 |
| 24 | Logout | 成功 → `data:{success:true}` | `logoutlogic.go:55-57` | `UserLogoutService.java:65-67` | **EQUIVALENT** | 静态源码 |
| 25 | Avatar/select | `NotFound → **500**`（非 1003） | `selectavatarlogic.go:53-56` | `AvatarSelectService.java:97-101` 显式 | **EQUIVALENT** | 静态源码（DRIFT-003 已修） |
| 26 | Avatar/* | `history_id`/`avatar_url` TrimSpace 空 → 500 | `selectavatarlogic.go:40-43` / `uploadavatarlogic.go:40-43` | `AvatarSelectService.java:63-67` / `AvatarUploadService.java:65-69` | **EQUIVALENT** | 静态源码 |
| 27 | Avatar/history | `Limit` 硬编码 30 | `getavatarhistorylogic.go:40` | `AvatarHistoryService.java:48,69` | **EQUIVALENT** | 静态源码 |
| 28 | Avatar/* | 空列表 → `[]`（非 null） | `getavatarhistorylogic.go:55` `make(…,0,n)` | `AvatarHistoryService.java:80` `new ArrayList<>(0)` | **EQUIVALENT** | 静态源码 |
| 29 | Avatar/* | `history` 字段不会是 null | `avatarhelpers.go:15-27`（nil→零值 struct） | `UserApiSupport.java:62`（proto3 `getHistory()` 返默认实例，永不为 null） | **EQUIVALENT** | 静态源码（proto `users.proto:105-108`） |
| 30 | **ALL 6 个 rethrow 端点** | **gRPC code → 业务码** | 每个 Logic 自己的 switch（`default` 收窄） | `mapRPCStatusToCode:100-110` **通用**映射 | **OBSERVABLE DIFFERENCE**（条件性） | **DRIFT-USERAPI-003** · 静态源码 + 依赖 U-003 |
| 31 | **ALL** | **请求绑定面（path/query/header/body）** | `Parse` = Path + **Form** + **Headers** + Body，**但没有对应 tag 的字段被整个跳过**（`usingDifferentKeys`）⇒ 只有 json tag 的 Req **只吃 JSON body** | 仅 JSON body | ✅ **一致**（原记 OBSERVABLE DIFFERENCE，**已证伪**） | **DRIFT-USERAPI-004** 已关闭（双侧活体，`330bafb`） |
| 32 | Avatar/history | **body 是否被解析** | handler **不调** `httpx.Parse` | `@RequestBody` 会解析 | **OBSERVABLE DIFFERENCE** | **DRIFT-USERAPI-005** · 静态源码 |
| 33 | 必填缺失 | HTTP 400 + text/plain + nosniff + 尾换行 | `http.Error` 形状 | `goStylePlainTextBadRequest:214-220` | **EQUIVALENT** | 静态源码 |
| 34 | 必填缺失 | **`Content-Type` 字面量** | `text/plain; **charset=utf-8**` | `text/plain`（**无 charset**） | **OBSERVABLE DIFFERENCE** | **DRIFT-USERAPI-006** · 字节码核实 |
| 35 | 必填缺失 | **body 编码字符集** | UTF-8 | ISO-8859-1（`StringHttpMessageConverter.DEFAULT_CHARSET`） | **OBSERVABLE DIFFERENCE**（当前不可见，见 DRIFT-USERAPI-006） | **DRIFT-USERAPI-006** · 字节码核实 |
| 36 | 鉴权 | HTTP 状态码 401 | `authhandler.go:121` / `checkblacklistmiddleware.go:38` | `handleAuthentication:73-78` | **EQUIVALENT** | 静态源码 + `UserApiMvcPipelineTest` |
| 37 | 鉴权 | 401 的 **body 形状** | JWT: **空 body**；黑名单: `{code:401,msg}` | 统一完整 envelope | OBSERVABLE DIFFERENCE（**已签字 N-1**） | `signed-deviations` / `docs/DRIFT/users/user/api.md` §0 · 静态源码 |
| 38 | 鉴权 | 黑名单 key 前缀 | `user:jwt_blacklist:%s`（`checkblacklistmiddleware.go:34`） | `BLACKLIST_KEY_PREFIX`（`JwtAuthInterceptor.java:70`） | **EQUIVALENT** | 静态源码 + `UserApiMvcPipelineTest:456`（真 Redis） |
| 39 | 鉴权 | 黑名单 **fail-open**（err → 放行） | `checkblacklistmiddleware.go:36` `err == nil && exists` | `JwtAuthInterceptor.java:204-208` 吞异常→null | **EQUIVALENT** | 静态源码 |
| 40 | 鉴权 | 验签先于黑名单 | `engine.go:101-113` | `JwtAuthInterceptor.java:132-182` | **EQUIVALENT** | 静态源码 + `UserApiMvcPipelineTest:371` |
| 41 | 鉴权 | Bearer 前缀两套取法（验签大小写不敏感 / 黑名单敏感） | `golang-jwt/request/oauth2.go` vs `strings.TrimPrefix` | `BearerTokenExtractor:74-81` vs `:95-99` | **EQUIVALENT** | 静态源码 + 2 条 `UserApiMvcPipelineTest` |
| 42 | 鉴权 | 黑名单 401 的 `Content-Type` | `appliaction/json;charset=utf-8`（**Go 侧拼写错误**） | `application/json` | OBSERVABLE DIFFERENCE（有意不复刻） | **DRIFT-USERAPI-010** · 静态源码 |
| 43 | 异常 | panic / 兜底异常的响应形状 | `recoverhandler.go:17` → **500 空 body** | `handleAny:262-272` → **500 + envelope(5001)** | **OBSERVABLE DIFFERENCE** | **DRIFT-USERAPI-007** · 静态源码 |
| 44 | 过载 | Breaker / Shedding / MaxConns | `rest/config.go:12-23` 全 `default=true`；`sheddinghandler.go:40` 过载 → 503 空 body | `service/common/protection/` 四个 Go 风格 Filter（MaxConns `+1` / Breaker `+2` / CpuShedding `+3` / MaxBytes `+6`） | **机制齐备**（原记「无任何对应物」**已证伪**）；⚠️ MaxConns 闸门粒度不同（Go per-route vs RAG 全局） | **DRIFT-USERAPI-008** · 静态源码 |
| 45 | 请求体上限 | MaxBytes 1 MiB，**413 压过 401** | `rest/config.go:53` `default=1048576`；`engine.go:164` 在 JWT 之外 | `GoStyleMaxBytesFilter:84-117` `@Component @Order(+6)` | **EQUIVALENT** | 静态源码（层次等价：Filter ⊃ Interceptor） |
| 46 | 超时 | 3s 预算的**作用域** | **不覆盖** JWT 验签 | **覆盖** 验签 + 黑名单 + handler | **OBSERVABLE DIFFERENCE** | **DRIFT-USERAPI-009** · 静态源码 |
| 47 | 可观测性 | 401 是否计入 `markHTTPResult` | **否**（Authorize 在 obs 外层） | **是**（Filter 在 DispatcherServlet 外） | **OBSERVABLE DIFFERENCE** | **DRIFT-USERAPI-009** · 静态源码 |
| 48 | Metrics | login 5 + register 2 埋点 | `metrics.go` + `loginlogic.go:47,59,65,77,82` / `registerlogic.go:46,58` | `UserApiMetrics` 7 个 Counter，调用点逐条对应 | **EQUIVALENT** | 静态源码 |
| 49 | Metrics | 其余 7 个 RPC 不埋点 | 不埋 | 不埋（1:1 不补） | **EQUIVALENT** | 静态源码 |
| 50 | 配置 | JWT secret / expire | `etc/usercenter.yaml:11-12` | `application.yml:91-92`（同值 + env 覆盖） | **EQUIVALENT** | 静态源码 |
| 51 | 配置 | HTTP 超时 3000ms | `rest/config.go:55` `default=3000`（yaml 未写） | `application.yml:142-143` `observability.http-timeout: 3s` | **EQUIVALENT**（配置层） | 静态源码；✅ **运行时已验**（2026-10-24 第九/十轮，见 §6.7 · CASE-05/05b/05e/05f）<br>⚠️ 原写「运行时未验 ⇒ 记 UNKNOWN」，**已过期** |
| 52 | 配置 | gRPC 传输 | Go `zrpc` = plain | `application.yml:81` `negotiation-type: plaintext` | **EQUIVALENT** | 静态源码 + `UserApiGrpcNegotiationTest` |
| 53 | 错误码表 | code→msg 全表 | `errmsg.go:38-68` | `ErrorCode` 枚举（users-common） | **EQUIVALENT**（以 users-common 为公理，不在本组件复验） | 逐条比对 30 项常量一致 · 静态源码 |
| 54 | 错误码表 | 未知码 → "FAIL" | `errmsg.go:70-76` `codeMsg[Error]` | `ErrorCode.of` → `ERROR`（`getMsg` 500→"FAIL"） | **EQUIVALENT** | 静态源码 |
| 55 | 并发 | 本组件的并发语义 | 无状态、无写、无幂等键 | 同 | **EQUIVALENT** | 静态源码 |
| 56 | 过载 | **客户端熔断打开**时的对外形状 | `zrpc/internal/clientinterceptors/breakerinterceptor.go:13-19` → `core/breaker/breaker.go:18-19` `ErrServiceUnavailable`（**普通 error**）→ `loginlogic.go:46-56` `status.FromError`⇒`Unknown`⇒`default`⇒**1015**；`loginhandler.go` 用 `httpx.OkJson` ⇒ **HTTP 200** | 修前：`GlobalExceptionHandler` **无**该分支 ⇒ 落兜底 ⇒ **HTTP 500 + 5001**，并把**入口**熔断喂开 ⇒ 对外变成 **503 空 body** | ❌ → ✅ **已修**（2026-10-24 第七轮） | **DRIFT-USERAPI-011** · 双侧活体（真 in-process gRPC server + 真 channel） |
| 57 | 生命周期 | 启动顺序 / 关闭 | `usercenter.go:24-45` | `UserApiApplication.main` + `ObservabilityLifecycle:47` | **EQUIVALENT**（表达层） | 静态源码 |
| 58 | 并发/事务 | user-rpc 的事务边界、`FOR UPDATE`、`extra_info` jsonb、`is_current` 陈旧 | — | — | **不在本组件判定权内**（非「未知」）⇒ 结论见 `user/rpc.md`：`extra_info` 列类型 ✅ 已修（V-003，`ec33159`）· `is_current` 派生 ✅ EQUIVALENT | `UNRESOLVED-005` **已关**（2026-10-24） |
| 59 | 外部依赖 | Redis **断连**时的黑名单查询 | `checkblacklistmiddleware.go:36` `err == nil && exists` ⇒ **fail-open 放行**（实测 ~1.7s） | Lettuce 默认把断连期命令**排队** ⇒ 撞 3s HTTP 预算 ⇒ 503 | **OBSERVABLE DIFFERENCE**（已修：2026-10-24 第九轮，`REJECT_COMMANDS` ⇒ 两侧都 200） | **DRIFT-USERAPI-014** · 双侧活体对拍 |
| 60 | 外部依赖 | Redis **主机名解析失败**时的黑名单查询 | 同上（5 次拨号快速失败 ⇒ ~1.8s ⇒ **恒 200**） | 失败耗时**贴着 3s commandTimeout** ⇒ 与 3s HTTP 预算竞速 ⇒ **200/503 摇摆**（实测 3.03/2.93/3.01s） | **OBSERVABLE DIFFERENCE**（🔴 待拍板，2026-10-24 第十轮）<br>⚠️ **2026-10-26 收窄**：**仅 api 侧** —— rpc 侧经三形态直连实测**无漂移**（code 全同）<br>🔴 **2026-10-26 第十二轮订正**：**原始观测在今天的实测里复现不出来**（dns 形态 24 次探测**两侧都 200**；blackhole 形态 4 次探测**两侧都 503**）⇒ **定性降级为「条件性成立 · 证据不足」**；**候选②（connect-timeout=1s）实测会制造反向差异**（RAG 200 vs Go 503）⇒ **建议否决** | **DRIFT-USERAPI-015** · 双侧活体对拍（2026-10-24 首测 + **2026-10-26 复现尝试**） |

---

## 5. External Interaction & Error Flow（§8 + §10）

### 5.1 user-rpc（gRPC，9 个方法）

```text
External System: user-rpc
Operation:       UserService/{Login,Register,GetUser,UpdateUser,DeleteUser,
                          Logout,UploadAvatar,SelectAvatar,GetAvatarHistory}
Input:           proto message（users.proto，与 Go 侧逐条一致 —— 见 AGENTS.md §3.4）
Parameters:      与 Go 侧逐条对应（本轮逐字段核对，见 §6.1）
Expected Result: resp + nil error
Failure Result:  StatusRuntimeException
Does Failure Block Operation: 是（两侧都直接转业务码，不重试、不 fallback）
Side Effect:     Delete/Update/Logout 的状态变更全部在 user-rpc 的事务内 —— **本组件无事务**
```

⚠️ **本组件没有事务边界可对照**（§15 不适用）：`user-api` 不碰 DB。
所有写都是「一次 gRPC + 一次响应」，Go 与 RAG 的事务边界都在 user-rpc ⇒ 归 rpc 台账。

### 5.2 Redis（黑名单 EXISTS）

```text
External System: Redis
Operation:       EXISTS user:jwt_blacklist:<token>
Input:           token（黑名单取法：大小写敏感的 TrimPrefix）
Expected Result: true / false
Failure Result:  error
Semantic:        ⚠️ **error ≠ 黑名单命中**。Go 的判据是 `err == nil && exists`，
                 所以 Redis 挂了会 **fail-open 放行**（checkblacklistmiddleware.go:36）。
                 RAG `JwtAuthInterceptor.java:204-208` 把异常吞成 null，判据同构 ⇒ fail-open。
Does Failure Block Operation: ⚠️ **两侧都不「误拦」，但 RAG 会「被拖住」** ——
                 「fail-open」只保证**不拒绝请求**，**不保证请求不被延迟/打挂**：
                 · Go：Redis 失败耗时 ~1.8~1.9s（5 次拨号快速失败）⇒ **< 3s 预算 ⇒ 仍 200**；
                 · RAG：失败耗时**取决于故障形态** ⇒
                   断连 ⇒ 29ms（`REJECT_COMMANDS` 秒拒）⇒ 200；
                   端口拒连 ⇒ 0.57s ⇒ 200；
                   **主机名解析失败 ⇒ 2.93~3.03s ⇒ 贴着 3s 预算 ⇒ 200/503 摇摆**
                   ⇒ **DRIFT-USERAPI-015**（🔴 待拍板，2026-10-24 第十轮）。
                   ⚠️ **2026-10-26 第十二轮订正**：这条「RAG 200/503 摇摆」**在今天的复现尝试里没出现**
                   （dns 形态 24 次探测**两侧都 200**；blackhole 形态**两侧都 503**）⇒ 本条**定性降级为
                   「条件性成立 · 证据不足」**，**不再是「已坐实的漂移」** ⇒ 见 §7 该条的第十二轮实测。
                 ⇒ ⚠️ 本行原写「否（两侧都不阻塞）」—— **对 Go 成立，对 RAG 不成立**，
                 故改为上表的分形态表述。
Side Effect:     无（只读）
```

### 5.3 关键 ERROR FLOW 逐条

| Failure | 触发条件 | Go 最终输出 | RAG 最终输出 | 等价？ |
|---|---|---|---|---|
| 必填字段缺失 | body 缺 `username` 等 | 400 `field "x" is not set\n` text/plain | 同（文本逐字） | ✅（header 见 #34） |
| 畸形 JSON | body 不可解析 | 400 + Go 的 json 错误文本 | 400 + Spring 的文本 | ⚠️ 形状同、文本不同（**有意不对齐**，已留档） |
| JWT 验签失败 | 签名错/过期 | **401 空 body** | 401 + envelope(1006) | ⚠️ 已签字 N-1 |
| `userId` claim 缺失/非数字 | token 合法但 claim 畸形 | **200 + 1006** | **401 + 1006** | ❌ **DRIFT-USERAPI-001** |
| 黑名单命中 | token 被拉黑 | 401 + `{code:401,msg}`，Content-Type 有拼写错误 | 401 + envelope(1006)，`application/json` | ⚠️ 已签字 N-1 + DRIFT-USERAPI-010 |
| Redis 故障 | 黑名单查不通 | fail-open 放行 | fail-open 放行 | ✅（最终行为见 U-004） |
| `uid <= 0` | token 合法、`userId:0` | get/avatar 路径 → 1006；**delete/update → 透传 rpc** | 全部路径 → 1006 | ❌ **DRIFT-USERAPI-002** |
| rpc 失败 | user-rpc 报错 | 按 per-Logic switch | 多数按通用映射 | ⚠️ **DRIFT-USERAPI-003** |
| panic | 未预期异常 | 500 空 body | 500 + envelope(5001) | ⚠️ **DRIFT-USERAPI-007** |
| CPU 过载 | Shedding 触发 | 503 空 body | 无此层 | ⚠️ **DRIFT-USERAPI-008** |
| 超时 | > 3s | 503 `http.Error(httpTimeoutBody)` | 503（走 `HttpMiddleware` 的 timeout 分支） | ⚠️ 作用域不同 **DRIFT-USERAPI-009** |

---

## 6. 逐维度结论（§12–§21）

### 6.1 Serialization（§19）

- **字段名**：Go 全 snake_case（`json:"avatar_url"` 等）；RAG DTO 逐字段 `@JsonProperty`。
  ⚠️ `is_current` / `size_bytes` 是 Jackson bean 推断会搞错的两个（`isCurrent()` → `current`），
  已在 `AvatarHistoryItem.java:39-41` 显式标注 ⇒ **EQUIVALENT**。
- **`uid` 的 `,string`**：`CreateUserResp.Uid` 与 `UserInfo.Uid` 都是 `int64 json:",...,string"` ⇒
  Go 输出 JSON **字符串**。RAG `RegisterResp.getUid()` / `UserInfo.getUid()` 返回 `String` ⇒ **EQUIVALENT**。
- **`null` vs `empty` vs `missing`**：
  - `score`：Go 无 `omitempty` ⇒ 恒 `0`；RAG `getScore()` 兜底 `0L` ⇒ **恒 `0`** ✅（DRIFT-005 已修）
  - `email` / `extra_info`：Go 有 `omitempty`；RAG `@JsonInclude(NON_NULL)` + Service 只在非空时 set ✅
  - `data`：Go `Data interface{}` 无 `omitempty` ⇒ 失败时是 `"data":null`；RAG `Response.data` 无注解 ⇒ 同 ✅
  - `list`：Go `make([]…,0,n)` ⇒ `[]`；RAG `new ArrayList<>(0)` ⇒ `[]` ✅
  - `user`：`json:"user,omitempty"` 对**结构体**无效 ⇒ Go 恒输出；RAG `NON_NULL` + 成功路径恒 set ✅
- **`size_bytes` 类型**：Go `int64`；RAG `long` ✅。RAG `UploadAvatarReq.sizeBytes` 是 `Long`（可空）
  以区分「缺席」，Go 的 `,optional` 也是这个语义 ✅。

### 6.2 Configuration（§20）

| 项 | Go | RAG | 结论 |
|---|---|---|---|
| JWT secret | `etc/usercenter.yaml:11` | `application.yml:91`（同值 + env 覆盖） | EQUIVALENT |
| JWT expire | 2592000 | 2592000 | EQUIVALENT |
| HTTP 超时 | `rest/config.go:55` tag 默认 3000ms（yaml 未写） | `observability.http-timeout: 3s` | EQUIVALENT（配置层） |
| MaxBytes | `rest/config.go:53` 默认 1048576 | `GoStyleMaxBytesFilter`（`GoStyleProtectionProperties.getMaxBytes()`） | EQUIVALENT |
| gRPC 寻址 | `zrpc.MustNewClient` + etcd `user.rpc` | Nacos `discovery:///user-rpc` | OBSERVABLE DIFFERENCE（**已签字 DRIFT-NEW-001**） |
| gRPC 协商 | plain | `negotiation-type: plaintext` | EQUIVALENT（漏写会静默变 TLS，见 DRIFT-NEW-014） |
| 监听端口 | 8888 | 49900 | OBSERVABLE DIFFERENCE（**已签字**，Infrastructure.md §3） |
| ⚠️ 超时 | — | `observability.slow-threshold` **未在 user-api 的 yml 里配** | UNKNOWN（回落 `Observability.slowThreshold()` 的 env 兜底） |

### 6.3 Authentication / Authorization（§14）—— 本组件最高风险面

- **身份来源**：两侧都**只**来自 JWT claim，body / header / query 里的 uid 一律不参与 ✅
- **Claim 提取**：`userId`（非标准 claim，被 `authhandler.go:72-79` 的循环放进 ctx）✅
- ⚠️ **标准 claim 被 Go 显式忽略**：`authhandler.go:74` 的 switch 把
  `aud/exp/jti/iat/iss/nbf/sub` 全部 skip ⇒ **token 里的 `sub` 永远不会进 ctx**。
  RAG 侧 jjwt 只读 `get("userId")`，其余 claim 一概不读 ⇒ 结论一致 ✅（两边都只看 `userId`）。
- **签名算法**：HS256（Go `jwt.go:12` 显式 `jwt.SigningMethodHS256`）⚠️ 见 U-002
- **密钥 padding**：`JwtUtil.padToHmacKeySize` 存在且 public（`JwtUtil.java:44-53`），
  user 默认 secret 36B ≥ 32 ⇒ padding 对 user 是 **no-op** ⇒ 与 Go 等价 ✅（见 §11 复核 1）
- **claim 缺失 / 类型错**：❌ **DRIFT-USERAPI-001**
- **`uid <= 0`**：❌ **DRIFT-USERAPI-002**（delete/update）
- **角色/权限**：本组件**无**任何角色/权限概念，两侧都只有「验签通过与否」✅
- **资源归属**：uid 只来自 token，不来自请求体 ✅

### 6.4 Error Identity（§11）

- Go 的业务码来自 `errmsg` 常量；RAG 的来自 `users-common.ErrorCode`（逐条比对 30 项，值与文案全一致）。
- ⚠️ 但**映射路径**不同：Go 每个 Logic 一个 switch；RAG 5 个 Service 直接 `throw e` 交给
  `GlobalExceptionHandler.mapRPCStatusToCode` 的**通用**映射 ⇒ **DRIFT-USERAPI-003**。
- `AvatarSelectService` 是唯一显式写全 Go switch 的（`AvatarSelectService.java:97-101`，DRIFT-003 已修），
  其余 6 个没有。

### 6.5 State Semantics（§12）

本组件**无内部状态**。唯一跨请求状态是 Redis 黑名单（由 user-rpc 写入、本组件只读）。
⇒ 状态语义不适用；user-rpc 侧的状态机归 rpc 台账（U-005）。

### 6.6 Cache（§13）

本组件**无缓存**。黑名单是一次性 `EXISTS`，不是缓存（无 TTL、无回填、无 miss 语义）。
⇒ 不适用。

### 6.7 Timeout（§17）

| | Go | RAG |
|---|---|---|
| 来源 | `rest/config.go:55` tag 默认 3000ms | `application.yml:143` `3s` |
| 执行者 | `observability.NewHTTPMiddleware`（原生超时中间件已被 `DisableNativeRestTimeout` 关掉） | `HttpMiddleware`（Filter） |
| **作用域** | 黑名单 + handler（**不含** JWT 验签） | 验签 + 黑名单 + handler |
| 超时结果 | 503 + `http.Error(httpTimeoutBody, 503)` | 503（`writeTimeoutResponse`） |
| 客户端断连 | 499 | 499（`markClientCancelled`） |

⚠️ 作用域差异见 **DRIFT-USERAPI-009**，其后果之一（Redis 慢时的 fail-open vs 503）见 **U-004**。
✅ **运行时已验（2026-10-24 第九 / 十轮）** —— ⚠️ 此处原写「两侧都没有对 3s 超时做过活体对拍
⇒ 按 §3.3 记 **UNKNOWN**」，那是 **2026-10-23 之前**的状态，**已过期**：

| 场景 | Go | RAG | 出处 |
|---|---|---|---|
| Redis **很慢**（`CLIENT PAUSE 8000`）| **503** "Request Timeout"（3.003s）| **503** "Request Timeout"（3.018s）| CASE-05b |
| Redis **断连** | **200**（fail-open）| **200**（fail-open）| CASE-05 |
| Redis **从未连上·端口拒连** | **200**（1.79s）| **200**（0.57s）| CASE-05e |
| Redis **主机名解析失败** | **恒 200**（1.81~1.92s）| **200/503 摇摆**（2.93~3.03s）| CASE-05f ⇒ **DRIFT-USERAPI-015**<br>🔴 **2026-10-26 复现失败**：Go 侧实为**两段式**（前 3 轮 ~1.8s → 稳态 **0.10/0.07/0.002s**）；RAG 侧在今天**测不到摇摆** ⇒ **两侧同形** ⇒ 本行已**不是当前事实** |

⇒ 复现脚本：`tools/difftest/exp-difftest-redis-blacklist.sh`（断连/很慢）
与 `tools/difftest/exp-difftest-redis-neverup.sh`（从未连上·两形态）。

### 6.8 Retry（§18）

两侧**均无重试**（Go 的 `zrpc` client 与 RAG 的 devh stub 都没配 retry policy）。
⇒ **EQUIVALENT**。gRPC 失败即转业务码，不重试、不 fallback、不熔断。

### 6.9 Concurrency（§16）

本组件无共享可变状态 ⇒ **EQUIVALENT**（N/A）。见 U-005。

### 6.10 Transaction（§15）

**不适用** —— 本组件不访问 DB，无事务边界。

### 6.11 Side Effects（§22）与 `success` 字段语义复核

```text
Go：DeleteUserResp.Success / LogoutResp.Success 只在成功分支被赋 true
    （deleteuserlogic.go:67-69 / logoutlogic.go:55-57）；失败分支 resp=nil ⇒ data 为 null，
    success 字段根本不出现。
RAG：DeleteUserResp.success / LogoutResp.success 同样只在成功路径 setSuccess(true)
     （UserDeleteService.java:72-74 / UserLogoutService.java:65-67）；失败路径
     GlobalExceptionHandler 产出 Response(code,msg,null) ⇒ data 为 null。
```

⇒ **`success` 的取值方向与可见性两侧完全一致：永远只会是 `true`，且只在成功时出现。**
**AGENTS.md §1.1 记录的「success 字段语义反转」在本组件未复现**（详见 §11 复核 5）。

### 6.12 Lifecycle（§21）

Go：`metrics.InitMetrics()` → `flag.Parse` → `conf.MustLoad` → `logx.MustSetup` → `logger.Init` →
`DisableNativeRestTimeout` → `rest.MustNewServer` → `server.Use(obs)` → `svc.NewServiceContext`
（**Redis 连接 + gRPC 客户端在此建立**）→ `RegisterHandlers` → `server.Start`。
RAG：Spring 容器装配（`UserRpcClient` 的 `@GrpcClient` + `JwtAuthInterceptor` 构造时的
`Keys.hmacShaKeyFor` + `StringRedisTemplate`）全部在 `refresh()` 内完成，
`ObservabilityLifecycle:47` 注册关闭钩子。
⇒ 依赖都在开始收流量之前就绪，**EQUIVALENT**（表达层差异）。

---

## 7. Semantic Drifts（§27 + §36）

### 📇 §7 条目总索引（2026-10-24 第十轮补）

> ⚠️ **本表是 §7 的唯一入口。** 历史上有些条目**只记在附录的轮次节里**（§「第七轮」/「第八轮」），
> **没有独立的 `###` 标题** ⇒ **只扫 §7 的标题会漏掉它们** ⇒ 本表补齐。
> ⚠️ 状态一律带**核对日期**（AGENTS.md §6.3：不带日期按 `UNKNOWN` 处理）。

| 编号 | 一句话 | 状态（核对日期） | 落点 |
|---|---|---|---|
| `DRIFT-USERAPI-001` | `userId` claim 畸形：Go 走业务层 200+1006，RAG 直接 401 | ✅ 已修（2026-10-23 第三轮） | §7 |
| `DRIFT-USERAPI-002` | delete/update 的 `uid<=0` 被收窄 | ✅ 已修（2026-10-23 第三轮） | §7 |
| `DRIFT-USERAPI-003` | gRPC 错误码映射比 Go 的 per-Logic switch 宽 | ✅ 已修（2026-10-23 第四轮） | §7 |
| `DRIFT-USERAPI-004` | 请求绑定面（原断言「零字段 DTO 也会 400」）| ❌ **已证伪关闭**（2026-10-24，commit `330bafb`） | §7 |
| `DRIFT-USERAPI-005` | `/user/avatar/history` 读 body（Go 侧不读）| ✅ 已修（2026-10-23 第四轮） | §7 |
| `DRIFT-USERAPI-006` | 400 的 `Content-Type` 少 `charset` + ISO-8859-1 编码 | ✅ 已修（2026-10-24 第六轮，真 Tomcat 双侧实证） | §7 |
| `DRIFT-USERAPI-007` | panic / 兜底异常的响应形状 | ✅ 已签字保留（`SD-USERAPI-001`，2026-10-23） | §7 |
| `DRIFT-USERAPI-008` | 「RAG 侧没有任何过载保护对应物」 | ❌ 原断言**已证伪**（2026-10-24 第八轮） | §7 + 附录「第八轮」 |
| `DRIFT-USERAPI-009` | observability 的位置（Go 在鉴权之内，RAG 在之外）| ✅ **① 已签字**（`SD-USERAPI-002`，2026-10-24）／🔴 **② 仍 OPEN**（3s 预算作用域） | §7 + §9.x 改法 A |
| `DRIFT-USERAPI-010` | 黑名单 401 的 `Content-Type` 拼写错误 | ✅ 已签字（N-1，2026-10-23） | §7 |
| `DRIFT-USERAPI-011` | user-api 缺客户端熔断异常的 handler（应为 200+1015）| ✅ 已修（2026-10-24 第七轮） | ⚠️ **附录「第七轮」· 无独立 §7 条目** |
| `DRIFT-USERAPI-012` | 降载的 RT 统计口径（Go 浮点+`math.Ceil` vs RAG 整型截断）| ✅ 已修（2026-10-24 第八轮） | ⚠️ **附录「第八轮」· 无独立 §7 条目** |
| `DRIFT-USERAPI-013` | 过载保护（MaxConns 闸门粒度 + breaker 键空间 + 注释与实现矛盾）| 🔴 **待拍板**（属 `service/common` ⇒ **跨域**） | ⚠️ **附录「第八轮」· 无独立 §7 条目** |
| `DRIFT-USERAPI-014` | Redis **断连** ⇒ Lettuce 排队 ⇒ 撞 3s 预算 ⇒ 503 | ✅ 已修（2026-10-24 第九轮） | §7 |
| `DRIFT-USERAPI-015` | Redis **主机名解析失败** ⇒ 200/503 摇摆 | 🟡 **2026-10-26 第十二轮：定性降级为「条件性成立 · 证据不足」** —— 原始观测**复现不出来**；候选②实测会制造反向差异 ⇒ **建议否决候选②**；本条**不再是「选哪个修法」的拍板题** | §7（**第十二轮实测**） |

⇒ **当前 OPEN 的只有 3 条**：`009②` / `013` / `015`（与 §10 §39 的 ❌ 3 项一致）。
🔴 **2026-10-26 第十二轮**：**`015` 的定性已降级**（原始观测复现不出来 ⇒ 「条件性成立 · 证据不足」）⇒ 它**不再要求拍板「选哪个修法」**，但**仍不算闭环** ⇒ 计数仍为 3。

### DRIFT-USERAPI-001 —— `userId` claim 缺失/非数字：Go 走业务层（200+1006），RAG 在鉴权层直接 401 —— ✅ **已修（第三轮）**

```text
Component:    JwtAuthInterceptor + 9 个 Logic 的 uid 提取
Operation:    全部 7 个 protected 端点
Category:     Authentication · claim presence 语义

Go Behavior:
  authhandler.go:54-63   parser.ParseToken 成功（签名对、exp 未过）⇒ 继续，不看任何 claim
  authhandler.go:65-79   claims 里所有**非标准** claim 塞进 ctx（"userId" 在其中）
  authhandler.go:81      next.ServeHTTP ⇒ 请求**到达 Logic**
  identity/resolve.go:70-73   ClaimedUID: ctx["userId"].(json.Number) 失败 ⇒ ErrInvalidClaim
  deleteuserlogic.go:38-41    同上 ⇒ return nil, errmsg.ErrorTokenRuntime
  handler *.go:27-31           httpx.OkJson ⇒ **HTTP 200** + {"code":1006,...,"data":null}
  ⇒ 结论：**token 签名合法但 userId claim 缺失/是字符串 ⇒ 200 + 1006**

Spring Boot Behavior:
  JwtAuthInterceptor.java:138  Object claim = jws.getPayload().get("userId")
  JwtAuthInterceptor.java:139  if (claim instanceof Number) {...} else {
  JwtAuthInterceptor.java:149-152     throw new AuthenticationException(1006, …)
  GlobalExceptionHandler.java:73-78    ⇒ **HTTP 401** + {"code":1006,"msg":"TOKEN已过期","data":null}

Difference:   HTTP 状态码 200 → 401。业务码两侧都是 1006（body 形状本身已按 N-1 签字收敛）。
Semantic Impact:
  ① 按状态码判「未登录 → 刷新 token / 跳登录页」的客户端与网关，会把「令牌存在但 claim 畸形」
     误判成「令牌无效」，触发一次无意义的刷新循环。
  ② 本组件的拦截器 javadoc（`JwtAuthInterceptor.java:33,38`）把「claim 缺失/类型不对 → 401」
     写成 L1 冻结行为，但同一段 javadoc（`:44-46`）又说「uid<=0 的拒绝不在本层，Go 同样不校验 claim 取值」。
     **这两句自相矛盾**：Go 对 claim「取值」不校验，对 claim「存在性/类型」也不校验，
     两类问题都被推迟到业务层。RAG 只搬了前半句，后半句在鉴权层就提前终止了。
Risk:         HIGH —— Counterfactual（§26）：一个没读过 Go 的 Spring 开发者，
              几乎必然会写「拦截器里把不可用 claim 拒掉」，这是最自然的 Spring 写法。
Evidence:     静态源码，两侧均带 消费点 行号。
Must Preserve: 「签名合法 + claim 畸形」必须走业务层产出 200 + 1006，
               而不是变成鉴权层的 401。
Recommended Verification: 用 JwtUtil.getToken 造一个**不含 userId** / `userId` 为字符串的 token，
               双侧各打一次请求，断言 HTTP 状态码与 body 全文。
```

### DRIFT-USERAPI-002 —— delete / update 的 `uid` 合法域被收窄（Go 不拒绝 `uid <= 0`） —— ✅ **已修（第三轮）**

```text
Component:    UserDeleteService / UserUpdateService
Operation:    delete, update
Category:     Authentication · uid 提取语义

Go Behavior:
  deleteuserlogic.go:37   userId, ok := l.ctx.Value("userId").(json.Number)
  deleteuserlogic.go:42-47 uid, err := userId.Int64(); if err != nil { return 1006 }
                          ⚠️ **没有 uid <= 0 的判定** ⇒ uid=0 / 负数照常进 rpc
  updateuserlogic.go:36-46 同上
  （对照：avatarhelpers.go:11-13 → identity/resolve.go:75 的 ClaimedUID **有** uid <= 0 判定）

Spring Boot Behavior:
  UserDeleteService.java:50   IdentityResolver.claimedUID(httpRequest)
  UserUpdateService.java:51   同
  IdentityResolver.java:106-108  if (uid <= 0) throw new ErrInvalidClaimException(…)
  UserDeleteService.java:51-54  catch ⇒ BusinessException(1006)
  UserUpdateService.java:52-55  同
  ⇒ **rpc 一次都不会被调用**

Difference:   Go 内部本来就存在两套 uid 语义（get/avatar 拒绝、delete/update 不拒绝）；
              RAG 把它们统一成「一律拒绝」。
Semantic Impact:
  对一个**签名合法、userId:0 或负数**的 token 打 /user/delete：
    Go  → uid=0 进 rpc，最终业务码由 user-rpc 决定（大概率 NotFound ⇒ 1003）
    RAG → 1006，且 0 次 rpc 调用
  差异不止于返回值，还包括**是否触达下游**（可观测的下游调用次数）。
Risk:         HIGH —— 「抽一个 claimedUID 统一全端点」是最自然的重构动作，
              而 Go 的两套语义恰恰是它会「顺手抹平」的东西。
Evidence:     静态源码（Go: deleteuserlogic.go:37-47 / updateuserlogic.go:36-46 /
              identity/resolve.go:75；RAG: IdentityResolver.java:106-108 +
              UserDeleteService.java:50-54 / UserUpdateService.java:51-55）。
              ⚠️ Go 侧从未对 delete/update 的 uid<=0 写过测试（唯一那份
              getuserlogic_test.go:51-52 只覆盖 get）⇒ 本条在 Go 侧也是无网状态。
Must Preserve: 两端点各自的 uid 合法域；RAG 侧需要 delete/update 各自一条「uid=0 透传」断言。
Recommended Verification: 双侧各起真进程，用 userId=0 的 token 打 delete/update，
               比对 HTTP 状态码 + body + **rpc 调用次数**（Go 侧看 rpc 日志 / RAG 侧看 stub 计数）。
```

### DRIFT-USERAPI-003 —— gRPC 错误码通用映射比 Go 的 per-Logic switch 宽 —— ✅ **已修（第四轮；由「条件性」升级为「已确认可达」）**

```text
Component:    GlobalExceptionHandler.mapRPCStatusToCode + 6 个 Service 的 catch-rethrow
Operation:    login, register, delete, update, logout, avatar/history
Category:     Error Identity · 错误码映射

Go Behavior（每个 Logic 自己 switch，未列出的 code 落 default ⇒ 一律 1015）:
  loginlogic.go:50-55        Internal→5001, default→1015
  registerlogic.go:49-56     Internal→5001, AlreadyExists→1001, default→1015
  updateuserlogic.go:60-69   Internal→5001, AlreadyExists→1001, NotFound→1003, default→1015
  deleteuserlogic.go:57-64   NotFound→1003, Internal→5001, default→1015
  logoutlogic.go:48-53       Internal→5001, default→1015
  getavatarhistorylogic.go:45-52  NotFound→1003, Internal→5001, default→1015

Spring Boot Behavior:
  GlobalExceptionHandler.java:100-110（通用映射，无 per-Service 上下文）
    INTERNAL→5001  NOT_FOUND→1003  ALREADY_EXISTS→1001  INVALID_ARGUMENT→500  default→1015
  6 个 Service catch 后 `throw e`（UserLoginService:83-98 仅特判 INTERNAL；
  UserRegisterService:58-63 / UserDeleteService:61-64 / UserUpdateService:66-69 /
  UserLogoutService:60-63 / AvatarHistoryService:75-78 全部直接 rethrow）
  ⇒ AvatarSelectService 已显式写全 Go 的 switch（:97-101，DRIFT-003 已修），
    其余 6 个没有

Difference（逐端点列出 Go 会落 default、RAG 却单独给码的 code）:
  login            : NOT_FOUND(→1003) / ALREADY_EXISTS(→1001) / INVALID_ARGUMENT(→500)
  register         : NOT_FOUND(→1003) / INVALID_ARGUMENT(→500)
  delete           : INVALID_ARGUMENT(→500) / ALREADY_EXISTS(→1001)
  update           : INVALID_ARGUMENT(→500)
  logout           : NOT_FOUND(→1003) / ALREADY_EXISTS(→1001) / INVALID_ARGUMENT(→500)
  avatar/history   : INVALID_ARGUMENT(→500) / ALREADY_EXISTS(→1001)
  ⇒ 这些 code 在 Go 侧一律是 **1015 服务繁忙**，在 RAG 侧变成 1003 / 1001 / 500。
Semantic Impact: 分类：OBSERVABLE DIFFERENCE（条件性）。
  ⚠️ **是否真的可观察，取决于 user-rpc 实际会不会发这些 code** ⇒ 见 **UNRESOLVED-003**。
  若 user-rpc 确实发得出，本条即升级为 SEMANTIC MISMATCH。
  注意 Go 侧刻意保留的区分（select 的 NotFound→500、upload 的 NotFound→1003）
  正是「用业务码区分可枚举性」的设计意图，通用映射会把它抹掉。
Risk:         MEDIUM —— 根因与已修的 DRIFT-003 完全同形（通用映射悄悄覆盖 per-Service switch）。
Evidence:     静态源码（两侧 switch 行号已列）。
Must Preserve: 每个端点自己的 switch；通用映射只能当兜底，不能当唯一来源。
Recommended Verification: 从 user-rpc 台账取每个 RPC 实际发出的 code 集合，
               与上表逐格比对；发得出的格子全部补 per-Service 分支。
```

### DRIFT-USERAPI-004 —— 请求绑定面 —— ❌ **已证伪关闭（2026-10-24 00:0x，commit `330bafb`）**

> **本节的原始断言是错的**，它出自纯静态源码阅读、**从未跑过活体**。
> 下面是证伪过程，保留供追溯（⚠️ 别再照着原始结论去「修」）。

#### 原断言（错的）

> Go 接受 path/query/form/HTTP header，RAG 只接受 JSON body ⇒ 客户端契约面被收窄。
> `Must Preserve: Go 的完整绑定面`。

#### 错在哪：漏掉了紧挨着的那道门

原结论读了 go-zero `core/mapping/utils.go` 的 `parseKeyAndOptions` ——
它确实在「没有对应 tag」时**回落到 Go 字段名**，据此推断 form/header 通道会按字段名绑定。
**漏掉了紧挨着的 `core/mapping/unmarshaler.go:570-573`**：

```go
func usingDifferentKeys(key string, field reflect.StructField) bool {
	if len(field.Tag) > 0 {
		if _, ok := field.Tag.Lookup(key); !ok {
			return true        // ← 该字段没有 <key> tag ⇒ **整个字段被跳过**
		}
	}
	return false
}
```

⇒ **不是「按字段名回落」，而是「完全不看」**。
`processField` 首行就 `return nil`（`unmarshaler.go:569-573`）。

而 `service/user/user/api/internal/types.go` 的 Req **只有 json tag**
（`json:"username"` / `json:"password,optional"` …）⇒
**path / form / header 三个通道对它们全部惰性**；
随后的 json 通道又按 json tag 强制必填
⇒ **Go 的 user-api 只认「Content-Type 含 `application/json` + JSON body」这一种输入。**

#### Go 侧活体实证（`go run -overlay` 虚拟注入探针 + 真源 `types.LoginReq` + go-zero `httpx.Parse`）

| # | 输入 | Go 结果 |
|---|---|---|
| 1 | JSON body（CT=`application/json`） | `Username="j" Password="p"` ✅ |
| 2 | 空 body + `?username=q&password=p` | **ERR** `field "username" is not set` |
| 3 | 空 body + urlencoded form body | **ERR** `field "username" is not set` |
| 4 | header `Username`/`Password` | **ERR** `field "username" is not set` |
| 5 | query + JSON body（username 冲突） | `Username="fromJson"` ⇒ **JSON 赢** |
| 6 | JSON body 缺 password | **ERR** `field "password" is not set` |
| 7 | JSON body 但 CT 缺失 | **ERR** `field "username" is not set` |
| 8 | JSON body 但 CT=`text/plain` | **ERR** `field "username" is not set` |

**机制侧对照**：第 3/4 条用**带 `form:`/`header:` tag** 的结构体复测，
只有 json tag 写成 `,optional` **且同时**带 form/header tag 时该通道才生效
⇒ **证明机制本身可用**，是 **user-api 的结构体没带那些 tag**，不是 go-zero 的通道坏了。

#### RAG 侧真 Tomcat 实测（Tomcat 11.0.24，端口 59900）

| 输入 | RAG | 与 Go |
|---|---|---|
| query `?username=q&password=p` | 400 `field "username" is not set` | ✅ 逐字一致 |
| header `username`/`password` | 400 同上 | ✅ |
| urlencoded form body | 400 同上 | ✅ |
| CT=`text/plain` + JSON body | 400 同上（**不是 415**） | ✅ |
| CT 缺失 + JSON body | 400 同上 | ✅ |
| CT=`application/json` + 合法 body | 200 并进入下游 | ✅ |

#### 结论

**RAG 的 `@RequestBody`-only 绑定面与 Go 已经等价 ⇒ 本条不构成漂移，不修。**

**守网**：`UserApiBindingSurfaceParityTest`（5 条，`330bafb`）——
它的作用是**反向护网**：防止下一个 agent 照着本节的原始结论去「补齐 query/form/header
绑定面」，**那才是真的引入漂移**（Go 不绑、RAG 绑 ⇒ 同一请求两侧行为分叉）。

#### 遗留（本轮顺带澄清，不单独编号）

**CT≠`application/json` 时 Go 返回 400 而非 415** 这条差异，
原台账归在 `DRIFT-USERAPI-005` 第四轮（`api.md` §1540-1552），
实测 **RAG 的 login/register 也是 400**（不是 415）⇒ 该差异**只存在于当时的 avatar/history**，
且已由 DRIFT-005 的修法（整个去掉 `@RequestBody`）消掉。
**⇒ 当前 user-api 全端点在 CT 维度上与 Go 一致。**

<details><summary>原始断言全文（保留供追溯）</summary>



```text
Component:    httpx.Parse（Go） / @RequestBody（RAG）
Operation:    全部 8 个带 httpx.Parse 的端点
Category:     Serialization · 输入来源

Go Behavior:
  rest/httpx/requests.go:50-69
    Parse = ParsePath → **ParseForm** → **ParseHeaders** → ParseJsonBody
  requests.go:18,27-31   formUnmarshaler 的 tag key 是 "form"
  rest/internal/encoding/parser.go:10,12  headerUnmarshaler 的 tag key 是 "header"
  core/mapping/utils.go:271-275  parseKeyAndOptions：tag 缺失时 **回落到 Go 字段名**
  → user-api 的 9 个 Req struct **只有 json tag**（types.go），没有 form/header tag
    ⇒ form 通道的 key 是字段名 `Username`/`Password`/`Email`/`Extrainfo`/`HistoryId`/`AvatarUrl`
    ⇒ header 通道再过 textproto.CanonicalMIMEHeaderKey（大小写规范化）
  ⇒ `POST /usercenter/v1/user/login?Username=a&Password=b`（空 body）**能登录成功**
  ⇒ `POST /usercenter/v1/user/login` + header `Username: a` / `Password: b` **也能登录成功**

Spring Boot Behavior:
  UserController.java:140,148,160,168,174,183,189,196,202 —— 全部只有 @RequestBody
  ⇒ query 参数与同名 header **完全不参与**取值

Difference: 同一请求在 Go 侧成功、在 RAG 侧 400 `field "username" is not set`。
Semantic Impact: 客户端契约面收窄。若有客户端（脚本 / 网关 / 老 SDK）用 query 或 header 传凭据，
            两侧行为分叉。这属于 §19 的「输入解释」差异，不是纯实现差异。
Risk:         LOW-MEDIUM（实际是否有客户端这么做，取决于部署现状；真源未改 ⇒ 默认 RAG 侧需对齐）
Evidence:     静态源码（go-zero 消费点：requests.go:50-69、utils.go:271-275、
              rest/internal/encoding/parser.go:10-13；RAG：UserController 9 处签名）。
Must Preserve: Go 的完整绑定面，或逐条签字放弃。
Recommended Verification: 真进程各打一次 `?Username=a&Password=b`（空 body），
               断言两侧的 HTTP 状态码与 body。
```

</details>

### DRIFT-USERAPI-005 —— `/user/avatar/history` 在 Go 侧根本不读 body，RAG 侧会解析 —— ✅ **已修（第四轮，整个去掉 `@RequestBody`）**

```text
Component:    getavatarhistoryhandler.go / UserController.getAvatarHistory
Operation:    avatar/history
Category:     Serialization · 输入通道

Go Behavior:
  internal/handler/user/getavatarhistoryhandler.go:17-25
    func 内 **没有 var req / 没有 httpx.Parse**，直接 NewGetavatarhistoryLogic
    ⇒ 请求体**完全不被读取**
    ⇒ 畸形 JSON body、非 JSON Content-Type、超大 body 一律不影响该端点

Spring Boot Behavior:
  UserController.java:200-204
    @RequestBody(required = false) GetAvatarHistoryReq req
    ⇒ Spring 会按 Content-Type 尝试反序列化；畸形 JSON ⇒ HttpMessageNotReadableException
      ⇒ 400 text/plain

Difference:  发一个 `{"a":}`（Content-Type: application/json）：
  Go  → 200（body 被完全忽略，直接走 Logic）
  RAG → 400 + text/plain
Semantic Impact: OBSERVABLE DIFFERENCE。这是 9 个 handler 里**唯一**不 Parse 的一个，
            其余 8 个都 Parse（getavatarhistoryhandler.go 是唯一例外），
            所以不能用「其余 8 个都对」来推这一个也对。
Risk:         MEDIUM —— 「9 个端点统一加上 @RequestBody」是最自然的写法，
              而它恰好抹掉了唯一的例外。
Evidence:     静态源码（Go: getavatarhistoryhandler.go:17-25 全文；RAG: UserController.java:200-204）。
Must Preserve: 该端点忽略 body 的事实。
Recommended Verification: 双侧对该端点发畸形 JSON，断言状态码差异是否真实存在。
```

### DRIFT-USERAPI-006 —— 400 响应的 `Content-Type` 少 `charset` 参数，且 body 用 ISO-8859-1 编码 —— ✅ **已修（第六轮，真 Tomcat 双侧实证）**

```text
Component:    GlobalExceptionHandler.goStylePlainTextBadRequest
Operation:    全部 4 个产出 400 的分支
Category:     Serialization · 响应头与编码

Go Behavior:
  Go net/http.Error 的四件事：
    Content-Type: text/plain; charset=utf-8   ← **带 charset**
    X-Content-Type-Options: nosniff
    写入 400
    fmt.Fprintln(w, error) → 字节按 UTF-8

Spring Boot Behavior:
  GlobalExceptionHandler.java:214-220
    headers.setContentType(MediaType.TEXT_PLAIN)   ← MediaType.TEXT_PLAIN 的字面量就是 "text/plain"
    ⇒ 响应头的 Content-Type 是 **text/plain（无 charset 参数）**

  ⛔ 关键证据（字节码核实，非记忆）：
    javap -c org/springframework/mock/web/MockHttpServletResponse.class（spring-test 7.0.9）
      setContentType("text/plain")：
        MediaType.parseMediaType("text/plain").getCharset() == null
        且既不兼容 MediaType.APPLICATION_JSON 也不兼容 "application/*+json"
        ⇒ 不进入 setExplicitCharacterEncoding 分支
        ⇒ updateContentTypePropertyAndHeader() 在 characterEncodingSet==false 时
          **原样** doAddHeaderValue("Content-Type", "text/plain")
    ⇒ 也就是说 MockMvc 的 getResponse().getContentType() 拿到的就是这串字面量，
      **与真 Tomcat 是否追加 charset 无关**（真容器走 Response.setContentType，同样原样保留）。

    ⇒ 因此 UserApiGoStyleBodyContentTypeTest.java:166 的
      assertEquals("text/plain", asPlainText.getResponse().getContentType(),
                   "Go 的 http.Error 写的是 Content-Type: text/plain; charset=utf-8")
      这条断言**在结构上无法发现该差异**：它断言的是 MockMvc 存下来的字面量，
      而它自己的说明文字描述的是 Go 的字面量 —— 两者不是同一个东西。

  编码侧（字节码核实）：
    javap -c org/springframework/http/converter/StringHttpMessageConverter.class（spring-web 7.0.9）
      static { DEFAULT_CHARSET = StandardCharsets.ISO_8859_1; }
      getContentTypeCharset(text/plain 无 charset) → 既非 JSON 兼容 → 返回 getDefaultCharset()
      ⇒ **ISO-8859-1**
    ⇒ RAG 的 400 body 按 ISO-8859-1 编码，Go 按 UTF-8。

Semantic Impact:
  ① 响应头字面量可比对（文本全部兼容 ASCII 时，body 相同、只有 header 不同）。
  ② body 字节：当前 `GoRequiredFieldException` 的文案全是 ASCII ⇒ 不可见；
     但 `HttpMessageNotReadableException.getMessage()`（handleUnreadableBody:163-167
     直接把它写进 body）**可能包含非 ASCII**（例如畸形 JSON 里带中文）
     ⇒ 那种输入下 RAG 会输出乱码而 Go 不会。
Risk:         MEDIUM（① 立即可见；② 条件性）。
Evidence:     字节码核实（spring-test 7.0.9 / spring-web 7.0.9 静态块）+ 静态源码。
              ⚠️ 未做真 Tomcat 活体探针 ⇒ 线上**精确**header 值仍需 U-001 关闭。
Must Preserve: `text/plain; charset=utf-8`；以及 body 按 UTF-8 编码。
Recommended Verification: (a) 真 Tomcat 起 user-api，curl -i 打一个必填缺失请求，
               抓原始响应头；(b) 发一个含中文的畸形 JSON，比对 body 字节。
```

### DRIFT-USERAPI-007 —— panic / 兜底异常的响应形状 —— ✅ **已签字保留（`SD-USERAPI-001`，2026-10-23）**

```text
Component:    GlobalExceptionHandler.handleAny / go-zero RecoverHandler
Operation:    全部端点的异常兜底
Category:     Error Flow · 结果映射

Go Behavior:
  rest/handler/recoverhandler.go:12-23
    defer recover() → w.WriteHeader(http.StatusInternalServerError)
    ⇒ **HTTP 500 + 空 body**（无 Content-Type、无 envelope）

Spring Boot Behavior:
  GlobalExceptionHandler.java:262-272
    @ExceptionHandler(Exception.class) ⇒ ResponseEntity(500).body(Response(5001,"系统内部错误",null))
    ⇒ **HTTP 500 + 完整 envelope**

Difference:  状态码一致（500）；body 形状不同（空 vs envelope）。
Semantic Impact: OBSERVABLE DIFFERENCE。低危（异常路径本身不该发生），
            但它是「响应契约」的一部分，任何按 body 结构统一解析的客户端都会在这里踩空。
Risk:         LOW
Evidence:     静态源码（recoverhandler.go:17 vs GlobalExceptionHandler.java:270-271）。
Must Preserve: 至少要明确 500 的 body 形状是「有意统一」还是「漏对齐」。
Recommended Verification: 双侧各制造一次 panic（临时端点或测试钩子），比对原始响应。
```

### DRIFT-USERAPI-008 —— 过载保护层 —— ❌ **原断言「RAG 侧没有任何对应物」已证伪（2026-10-24 04:0x）**

> **原断言的证据基础是错的**：它写「RAG 侧 **user-api 模块内**零命中」——
> **只 grep 了本模块**，漏了 `service/common`（共享模块，`user-api` 依赖它且
> `scanBasePackages` 含 `com.rag.common`）。
> ⇒ **与 `DRIFT-USERAPI-004` 同一型错误**：读漏了跨模块的东西。
>
> ⚠️ **本条尚未完全关闭** —— 原断言不成立，但它顺带暴露了**三个真问题**（见下），
> 应拆成新条目跟踪，不要因为「原断言错了」就把整条当已解决。

#### 实证：RAG 侧四个机制**全部存在**，参数与 Go 默认值逐条对齐

| 机制 | Go 真源实配 | RAG 实现 | RAG 默认值 | 运行时证据 |
|---|---|---|---|---|
| MaxConns | `MaxConns int json:",default=10000"` | `GoStyleMaxConnsFilter` | **10000** | ✅ **已实测**：`--rag.protection.max-conns=1` + 并发 ⇒ 第 2 个请求 **503** |
| MaxBytes | `MaxBytes int64 json:",default=1048576"` | `GoStyleMaxBytesFilter` | **1048576** | ✅ 真实请求栈里在链上 |
| Breaker | `Middlewares.Breaker default=true` | `GoStyleBreakerFilter` + `GoStyleBreaker` | Go 默认阈值 | ✅ **算法逐行对照一致**（`googlebreaker.go:53-80`）；⚠️ **键空间无界**（`DRIFT-USERAPI-013 ②`）· 🔴 **2026-10-26 只读复核**：Go 的 key = **注册期 `route.Path` 模板**（基数=路由数）· RAG = **raw `getRequestURI()`**（基数=参数取值数）⇒ 且 **user-api 零路径变量 ⇒ 本组件不可观察** ⇒ 详见 §7「7.x-2」 |
| Shedding | `CpuThreshold json:",default=900"` | `GoStyleCpuSheddingFilter` + `GoStyleAdaptiveShedder` | **900** | ✅ **已实测**（2026-10-24 第八轮：`GoStyleCpuSheddingFilterTest` 过载 ⇒ **503 空 body** + 不触达下游）；⚠️ **RT 统计口径已修**（`DRIFT-USERAPI-012`） |
| Timeout | `Timeout json:",default=3000"` | `HttpMiddleware`（+5） | **3s** | ✅ 已在 `application.yml` 显式钉住 |
| gRPC client 熔断 | Go 无对应物（Go 侧只熔 HTTP 入口） | `GoStyleGrpcClientBreakerInterceptor` | — | ❌ **user-api 无接线测试**（follow-api / message-api 有） |

**中间件顺序也逐条对齐**：Go `engine.go:130-168` 的装配序是
MaxConns → Breaker → Shedding → Timeout → Recover → Metrics → MaxBytes → Gunzip；
RAG 的 `@Order` 是 MaxConns(+1) → Breaker(+2) → CpuShedding(+3) → HttpMiddleware(+5) → MaxBytes(+6)
⇒ **同序**。

⚠️ **Go 侧 `Rest:` 段根本没配**（`usercenter.yaml` 里没有），
但 go-zero 的 `RestConf` 每个阈值都带 `json:",default=…"` ⇒ 配置加载器会填默认值
⇒ **真源那三个机制是开着的**（这一点此前也被误判过）。

#### 原断言引出的三个**真**问题（应拆为新条目）

1. **MaxConns 的闸门粒度不同**：Go 是 **per-route** latch
   （`engine.go` 的 `buildChainWithNativeMiddlewares` **逐路由**调用 ⇒ 每条路由各 new 一个），
   RAG 是**全局一个**。
   `GoStyleMaxConnsFilter` 的 javadoc 已**显式留痕**为「有意的表达层收敛」，
   理由是「13 个端点的并发度下差别只在极端场景显现，而 Tomcat `max-threads=200` 早已先成瓶颈」。
   ⇒ 这是**已留痕的收敛**，不是隐藏缺口，但台账此前完全没提。
   🔴 **2026-10-24 第八轮复核：该论证自相矛盾**（理由与「保留本层的场景」互斥）
   ⇒ 已与 ② 合并为 `DRIFT-USERAPI-013`（**待决策**）⇒ **详见 §「第八轮」**。
2. **user-api 的 gRPC client breaker 是否真的被装配，未验证** ——
   `GoStyleGrpcClientBreakerInterceptor` 的 javadoc **自己就写着**
   「这正是 AGENTS.md §3.3『不能仅凭存在 {@code @Component} 就推断责任已被承担』的形态」，
   而 follow-api / message-api **有** `GoStyleGrpcClientBreakerWiringTest`，**user-api 没有**。
   ✅ **2026-10-24 第七轮已闭环** ⇒ 补齐 `GoStyleGrpcClientBreakerWiringTest` +
   `GoStyleGrpcClientBreakerE2ETest`，并**由此抓出一条真漂移**
   （`DRIFT-USERAPI-011`：user-api 缺熔断异常的 handler ⇒ 500+5001，Go 应为 200+1015）⇒
   **详见 §「2026-10-24 第七轮」**。
3. **逐机制行为等价性从未逐条对照** —— 四个机制各自的触发阈值、拒绝时的响应形状
   （Go 是 **503 + 空 body + 无 Content-Type**，已实测）、指标口径，都还没逐条比过。
   ✅ **2026-10-24 第八轮已逐条对照完成**（四个机制 × 40 格：**37 格 EQUIVALENT**、
   **1 格已修** = `DRIFT-USERAPI-012`、**2 格待决策** = `DRIFT-USERAPI-013`）
   ⇒ **详见 §「2026-10-24 第八轮」**。
   ⚠️ **连带查出 ① 与 ② 是同一件事的两面**（都关于「per-route 键空间」）⇒ 不宜分开修。

#### 结论

**⇒ 「RAG 侧没有任何对应物」不成立；「过载交给网关/容器」这个方案的前提也不成立** ——
RAG 侧**已经有**一层 Go 风格保护层，再加网关会与之**重复**，且网关的 503 带 JSON body
⇒ 反而制造出 Go 没有的 wire 差异。

⇒ **原「Must Preserve: 明确签字放弃（过载行为由上游网关/容器负责）」不再适用** ——
它基于「RAG 什么都没有」这个错误前提。


```text
Component:    user-api 的过载保护层
Operation:    全部端点在过载时的行为
Category:     Lifecycle / External Interaction · 可用性

Go Behavior:
  rest/config.go:12-23   MiddlewaresConf 十项**全部** `default=true`
  rest/engine.go:130-168 逐项装配（MaxConns → Breaker → Shedding …）
  rest/handler/sheddinghandler.go:34-41  shedder.Allow() 失败 ⇒ w.WriteHeader(503) + return
  rest/handler/maxconnshandler.go         连接数超限 ⇒ 503

Spring Boot Behavior:
  user-api 侧无任何 breaker / shedding / max-conns 组件。
  唯一相近的是 HttpMiddleware 的 3s 超时（超时后 503），但那是**单请求**超时，不是负载卸载。

Difference:  Go 在 CPU 过载 / 连接数超限时**主动丢弃**请求并立即 503（空 body）；
             RAG 只会把请求排队直到 3s 超时再 503（且 body 文本不同）。
Semantic Impact: OBSERVABLE DIFFERENCE。过载场景下的错误率、延迟分布与错误 body 都不同。
            注：这是 go-zero 的**框架默认能力**，不是本项目的业务决策 —— 真源冻结 ⇒ 默认判定 RAG 缺。
Risk:         MEDIUM（低频但影响面大；且验收环境的压测通常压不出来）。
Evidence:     静态源码（rest/config.go:12-23、engine.go:145-153、sheddinghandler.go:34-41；
              RAG 侧 user-api 模块内零命中）。
Must Preserve: 或明确签字放弃（并写清「过载行为由上游网关/容器负责」）。
Recommended Verification: CPU 打满 / 连接数打满条件下双侧压测，比对 503 比例与响应体。
```

### DRIFT-USERAPI-009 —— observability 的位置：Go 在鉴权之内，RAG 在鉴权之外 —— ✅ **① 已签字（2026-10-24）⇒ `SD-USERAPI-002`**

> **本条签字只覆盖 ①（可观测性）。** ②（3s 超时作用域）**不在签字范围内** ——
> 那是行为差异且条件性（只在 Redis 也慢时可观察）⇒ 仍挂 `UNRESOLVED-004`
> （⚠️ **2026-10-24：该条已于第九轮闭环** ⇒ 实测「Redis 慢」时**两侧都是 503**，
> 即 ② 的**实际后果与原推断不同**；但 ② 本身作为「超时作用域」差异**依然成立**）。
> 签字档 ⇒ [`docs/Intentional_Deviations/users/user/api.md`](../../../Intentional_Deviations/users/user/api.md) 的 `SD-USERAPI-002`。
> ⚠️ 审查时看到「401 进了 access log」变红，**先确认是不是这一条**，不要当新回归去「修」。

```text
Component:    HttpMiddleware(Filter) vs go-zero server.Use 中间件
Operation:    全部端点的 401 路径 + 超时作用域
Category:     Lifecycle · 观测面 + Timeout scope

Go Behavior:
  rest/engine.go:101-113 bindRoute：
    chn = buildChainWithNativeMiddlewares(...)   // 索引 0，最外层
    chn = appendAuthHandler(...)                 // JWT
    for _, mw := range ng.middlewares { chn = chn.Append(...) }   // observability，再往内
    handle = chn.ThenFunc(route.Handler)
  chain.Then = m0(m1(...mN(h)))，索引 0 先执行
  ⇒ 执行序：… → **JWT 验签** → **observability(3s)** → 黑名单 → handler
  ⇒ ① handler.Authorize 的 401 **不经过** observability ⇒ 不进 markHTTPResult
    ② 3s 预算**不覆盖** JWT 验签

Spring Boot Behavior:
  HttpMiddlewareFilter.java:36-37  @Component @Order(HIGHEST_PRECEDENCE+5) ⇒ **Servlet Filter**
  ⇒ Servlet 的**所有** Filter 都在 DispatcherServlet 外，HandlerInterceptor 在内
  ⇒ 执行序：**MaxBytes → HttpMiddleware(3s)** → DispatcherServlet → **JwtAuthInterceptor** → Controller
  ⇒ ① 401 **计入** markHTTPResult
    ② 3s 预算**覆盖**验签 + 黑名单 + handler

Difference:
  ① 可观测性：Go 的「JWT 验签失败」请求不出现在 access log / span / HTTP 指标里；RAG 会出现。
     ⇒ 鉴权失败率的看板两边口径不同。
  ② 超时作用域：见 §6.7 表。潜在后果见 UNRESOLVED-004（Redis 慢时 Go fail-open，RAG 可能 503）。
     ⚠️ **2026-10-24 更新**：第九轮实测「Redis 慢」时**两侧都是 503 "Request Timeout"**（Go 3.003s / RAG 3.018s）
     ⇒ 这条**具体后果**已被证伪；但「超时作用域」这个**差异本身**依然成立（属本条的 ②，未被签字覆盖）。
Semantic Impact: OBSERVABLE DIFFERENCE（观测面）+ Timeout scope 差异（行为面，条件性）。
Risk:         MEDIUM。① 是永远可见的差异；② 是「只有在 Redis 也慢时才可见」的差异。
Evidence:     静态源码（Go: engine.go:101-113 + chain.go 的 Then 语义；
              RAG: HttpMiddlewareFilter.java:36-37 + WebMvcConfig.java:56-59）。
Must Preserve: 或明确签字接受「RAG 的观测面更大」（这通常是好事，但要写下来）。
                  ⇒ ✅ 2026-10-24 已签字接受（SD-USERAPI-002）
Recommended Verification: 真进程各打一次无 token 请求，比对 access log / /actuator 指标里是否出现。
                          ⇒ ⚠️ 本轮**未做**（无 metrics 后端）⇒ 签字依据是**中间件顺序的结构性证据**
                            （HttpMiddlewareFilter.java:36-37 的 @Order vs WebMvcConfig.java:56-59 的 addInterceptor），
                            而非运行时指标比对。
```

### 9.x `DRIFT-USERAPI-009 ②` 的修法推导（2026-10-24 · 逐行核实 Go 链序）

> ⚠️ **本节只做推导与取证，代码与签字均未动**（等用户拍板）。
> 推导结论：**② 的修法可以只落在 user 域内，不必动 `service/common`。**

#### 1. Go 的完整链序（逐行核实过 `go-zero@v1.10.2`）

| 环节 | 出处 | 位置 |
|---|---|---|
| 原生中间件（Trace/Log/Prom/MaxConns/Breaker/Shedding/Timeout/Recover/Metrics/MaxBytes/Gunzip） | `rest/engine.go:130-171` `buildChainWithNativeMiddlewares` | **最外层** |
| JWT 验签 | `rest/engine.go:74` `chn.Append(handler.Authorize(...))` | 原生**之内** |
| observability(3s) | `rest/engine.go:110-111` `for ... ng.middlewares { chn = chn.Append(...) }` | JWT **之内** |
| 黑名单 | `rest/server.go:222-234` `WithMiddleware` 把中间件**套在 `route.Handler` 外面** ⇒ route 级 | **最内层** |

- `chain.go` 的 `Append` 文档明写「adding the specified middlewares as the **last** ones in the request flow」
  ⇒ 越 `Append` 越靠内；
- `rest/server.go:73-76` `AddRoutes`：`rest.WithMiddlewares([CheckBlacklistMiddleware], routes...)`
  （`routes.go:33-34`）+ `rest.WithJwt(...)`（`routes.go:73`）；
- ⚠️ `rest/engine.go:154-156` 的原生 `TimeoutHandler` 被 `observability.DisableNativeRestTimeout`
  关掉了（`service/user/user/api/usercenter.go:35`）⇒ 3s **只**由 observability 提供。

⇒ **Go 执行序：原生 → JWT 验签 → observability(3s) → 黑名单 → handler**
⇒ **JWT 在 3s 预算之外；黑名单在 3s 预算之内。**

⚠️ **注册点决定了一个重要事实**：`usercenter.go:37` 的
`server.Use(observability.NewHTTPMiddleware(...))` 是**每个 api 服务各自注册**的
（`admincenter.go:36` 同款）⇒ **Go 的链序是「逐服务」决定的，不是全局常量。**

#### 2. RAG 现状（与 Go 的差别只在 JWT 那一段）

| 环节 | 位置 | 在 3s 预算内？ |
|---|---|---|
| `HttpMiddlewareFilter`（3s） | `service/common/.../spring/HttpMiddlewareFilter.java:37` `@Order(Ordered.HIGHEST_PRECEDENCE + 5)`（Servlet Filter ⇒ DispatcherServlet **外**） | — |
| `JwtAuthInterceptor`（验签 + 黑名单） | `service/users/user/user-api/.../config/WebMvcConfig.java:56-59` `registry.addInterceptor(jwtAuthInterceptor)`（HandlerInterceptor ⇒ DispatcherServlet **内**） | **验签被覆盖**（❌ 与 Go 相反）· 黑名单在内（✅ 与 Go 一致） |

#### 3. 改法 A（**落在 user 域内**，不动 `service/common`）

把 **JWT 验签**从 `JwtAuthInterceptor` 拆出来，做成一个
**`@Order` 序号更小**（比 `HttpMiddlewareFilter` 更外层，如 `HIGHEST_PRECEDENCE + 4`）的 Filter；
**黑名单检查仍留在 interceptor** 里：

```
[JWT filter] → [3s HttpMiddleware] → [blacklist interceptor] → handler
```

⇒ **与 Go 的 [JWT] → [3s] → [blacklist] → handler 逐项一致** ✓
⇒ 改动文件全部落在 `service/users/user/user-api/...` ⇒ **是 user 域内的改动**。

#### 4. ⚠️ 三个连带项（**每一个都需要用户拍板** —— ② 已于 2026-10-24 拍板）

| # | 连带项 | 依据 | 后果 |
|---|---|---|---|
| ① | **①（可观测性）会自动对齐** | Go 的 JWT 401 在 observability **之外** ⇒ 不进 access log/span/指标；改法 A 后 RAG 的 JWT 401 同样在 `HttpMiddlewareFilter` **之外** ⇒ 不进 `markHTTPResult` | ⇒ **① 从「已签字偏离」变成「已对齐」** ⇒ `SD-USERAPI-002` 的签字内容**不再适用**，需复核销号。⚠️ 黑名单 401 两侧都在 observability **内** ⇒ **不受影响** |
| ② | **会推翻 N-1 的一部分** | Go 的 JWT 失败是 `rest/handler/authhandler.go:121` `writer.WriteHeader(http.StatusUnauthorized)` ⇒ **401 + 空 body**；RAG 现在是 `GlobalExceptionHandler` 转的 **401 + envelope(1006)**，而 **N-1 签的正是「RAG 统一成完整 envelope」** | 改法 A 把验签挪到 filter 后，异常**不再被 `@ControllerAdvice` 捕获**（`@ExceptionHandler` 只在 DispatcherServlet 内生效）⇒ filter 必须自己写响应 ⇒ **二选一**：照 Go 写空 body（推翻 N-1 的一半）/ 在 filter 里手写 envelope（保留 N-1）<br>✅ **已拍板（2026-10-24 用户）：选 envelope** ⇒ **N-1 / `SD-USERAPI-003` 保持不变**；改法 A 若实施，JWT 401 **仍写完整 envelope** ⇒ 改法 A **不会**让 RAG 的两处 401 形态分裂 |
| ③ | **推广是跨域的** | Go 逐服务 `server.Use` ⇒ **改法 A 才是对 Go 更忠实的做法**（每个服务对齐自己的 Go 侧），而非共享层一刀切 | RAG 有 10 个 api 服务，只改 user-api ⇒ **中间态是服务间不一致**（user-api 的 JWT 不计入 3s 预算，其余 9 个仍计入）⇒ 需 like/follow/comment/favorite/article/hot/message/task/admin **各域各改各的** |

> ✅ **连带项② 已关闭**（2026-10-24 用户拍板：接受 envelope）⇒
> **改法 A 现在只剩 ①（自动对齐 ⇒ `SD-USERAPI-002` 需复核销号）与 ③（跨域推广）**。
> ⇒ 换句话说：**改法 A 的「签名档阻力」已清零**，剩下的是「销号流程」与「推广节奏」。

#### 5. 为什么不推荐「改法 B（共享层一刀切）」

改 `service/common` 的 `HttpMiddlewareFilter`（位置或语义）可一次性对齐 10 个服务，
但 ⚠️ **与 Go 的「逐服务链序」模型不符** —— Go 每个服务自己 `server.Use`，
共享层一刀切会让 RAG 的链序**不再对应任何一个 Go 服务的链序**。
⇒ 若最终要统一，**应逐服务对齐各自的 Go 侧**，而不是在共享层定一个全局顺序。

#### 6. 边界结论

- ✅ **user 域内能独立完成**：改法 A 的代码全部落在 `service/users/user/user-api`；
- ❌ **user 域内完成不了**：全仓一致（需另外 9 个域各自推广）+
  `SD-USERAPI-002` 与 N-1 **两份签字档**要改；
- ⚠️ **签字档的连带比代码改动更需要先拍板**。

#### 7. 🆕 补充：另一条**独立**的观测面差异 —— MaxBytes 相对 observability 的位置（2026-10-24）

> ⚠️ 本条**不是** ② 的一部分，而是**同一族（观测面）的另一条**，
> 在做 009 复核时**顺带查出**，此前台账**没有记录**。

| | Go | RAG |
|---|---|---|
| MaxBytes 相对 observability(3s) | **在之外**（`rest/engine.go:164` 是 native middleware，而 observability 是 `server.Use` 的 user middleware ⇒ 后者更靠内） | **在之内**（`GoStyleMaxBytesFilter` `@Order(+6)` 晚于 `HttpMiddlewareFilter` `@Order(+5)` ⇒ 数值小的先执行 ⇒ HttpMiddleware 在外） |
| 可观察后果 | 超限 413 **不计入** access log / span / HTTP 指标 | 超限 413 **计入** |
| 证据强度 | **静态源码**（`engine.go:130-171` + `chain.go` 的 Append 语义） | **静态源码**（两个 `@Order` 数值） |

⚠️ **运行时不可观测**（如实声明）：3s 预算挂在 **worker 线程**的 ThreadLocal 上
（`GoStyleRequestDeadline.begin(deadlineNanos)`，`HttpMiddleware.java:263`），
**不是** request 属性 ⇒ 请求侧探不到；
且观测面需要 metrics 后端才能看，**本仓无 metrics 后端**（同 `SD-USERAPI-002` 的同一限制）
⇒ 本条**只能**是结构证据，**不冒充运行时结论**。

⇒ **方向与 `SD-USERAPI-002` 一致**（RAG 多记）⇒ **若签字，建议并入 `SD-USERAPI-002` 的范围说明**
（该条现只写了「401 计入」，应扩为「**鉴权失败与 413 拒绝都计入**」）。
⚠️ **本条未签字**，等用户拍板。

⇒ **守网锚点**：`UserApiProtectionMvcTest#maxBytesIsOrderedInsideHttpMiddleware`
（断言两个 Filter 的 `@Order` 相对大小，并**在 javadoc 里自陈这是结构证据而非运行时观测**）。

### DRIFT-USERAPI-010 —— 黑名单 401 的 `Content-Type` 拼写错误，RAG 有意不复刻 —— ✅ **已签字（`SD-2026-10-23-01/02/03` 的 N-1）**

```text
Component:    CheckBlacklistMiddleware
Operation:    全部 protected 端点的黑名单 401
Category:     Serialization · 响应头

Go Behavior:
  checkblacklistmiddleware.go:37
    w.Header().Set("Content-Type", "appliaction/json;charset=utf-8")
    ⚠️ **Go 侧拼写错误**：application 少了一个 c，写成了 appliaction。
  :38-39  w.WriteHeader(401) + 写 {"code":401, "msg":"登录已失效,请重新登陆"}

Spring Boot Behavior:
  走统一 envelope（handleAuthentication:73-78）⇒ Content-Type: application/json

Difference:  响应头字面量不同 + body 形状不同。
Semantic Impact: OBSERVABLE DIFFERENCE（有意）。Go 的 Content-Type 是无效 MIME，
            客户端按它解析必然失败 —— 这不是「要保留的语义」，是 Go 侧的笔误。
            body 形状的收敛已由 §0 的 **N-1** 签字；本条只补记「Content-Type 也不复刻」，
            让 N-1 的覆盖范围从 body 扩到 header。
Risk:         LOW
Evidence:     静态源码（checkblacklistmiddleware.go:37-39）。
Must Preserve: 不复刻拼写错误。若要求逐字复刻，需在拦截器里为黑名单路径单写响应。
Recommended Verification: 无需验证（属已决策项），但建议在 N-1 的「关闭条件」里补上这一句。
```

### DRIFT-USERAPI-014 —— Redis **断连**时 Lettuce 把命令**排队**等重连 ⇒ 撞 HTTP 3s 预算 ⇒ 503（Go 是 fail-open 200） —— ✅ **已修（2026-10-24 第九轮）**

```text
Component:    JwtAuthInterceptor（黑名单 EXISTS）/ Lettuce 客户端配置
Operation:    全部 protected 端点在 Redis 不可用时的响应
Category:     State Semantics · Failure Semantics（可用性语义）

Go Behavior:
  checkblacklistmiddleware.go:35  exists, err := m.Redis.ExistsCtx(r.Context(), blackListKey)
  checkblacklistmiddleware.go:36  if err == nil && exists { …401… }
    ⇒ **err != nil 时不拦截 ⇒ fail-open 放行**
  为什么 err 会「快速」变成非 nil：
    go-zero@v1.10.2 core/stores/redis/redisclientmanager.go:14  maxRetries = 3
    go-zero@v1.10.2 core/stores/redis/redisclientmanager.go:32-40
      red.NewClient(&red.Options{Addr, …, MaxRetries: maxRetries, MinIdleConns: idleConns})
      ⚠️ **没有**设 DialTimeout / ReadTimeout ⇒ 用 go-redis v9 默认值
    ⇒ 连接被拒时**每次重试都立即失败** ⇒ 整体快速失败（实测 ~1.7s，仍在 3s 预算内）
  Go 的 HTTP 预算：go-zero@v1.10.2 rest/config.go:55  Timeout int64 `json:",default=3000"`

Spring Boot Behavior（修前）:
  Lettuce 默认把**断连期间**的命令**挂起排队**等重连
    ⇒ 根因（行号指向**消费点**，AGENTS.md §3.3.1）：
      io.lettuce.core.protocol.DefaultEndpoint:149
        this.rejectCommandsWhileDisconnected = isRejectCommand(clientOptions)
      io.lettuce.core.protocol.DefaultEndpoint:837-852  isRejectCommand(...)
        case DEFAULT: if (!autoReconnect) return true; return false;
        ⇒ DEFAULT + autoReconnect=true ⇒ **不拒 ⇒ 排队**
      org.springframework.boot.data.redis.autoconfigure.LettuceConnectionConfiguration:209-230
        createClientOptions(...) ⇒ **从不设** disconnectedBehavior
        ⇒ 用 Lettuce 默认 DEFAULT（ClientOptions.java:61 DEFAULT_DISCONNECTED_BEHAVIOR）
  ⇒ 排队到 spring.data.redis.timeout: 3s 的 commandTimeout（实测 3.02s）
  ⇒ 而 HTTP 预算 observability.http-timeout: 3s **先耗尽** ⇒ **503 "Request Timeout"**

Difference:  同一个 Redis 断连故障，两侧的**对外形状**不同：
            Go = HTTP 200（fail-open 放行到业务层）；RAG = HTTP 503 "Request Timeout"。
Semantic Impact: OBSERVABLE DIFFERENCE（**可用性语义分叉**）。
            ⚠️ 不是「异常映射不同」，是「**失败耗时 vs 请求预算**」——
            两侧 HTTP 超时都是 3000ms 且**已对齐**，差别只在 Redis 失败要花多久。
Risk:         HIGH（Redis 抖动 ⇒ RAG 直接 503，而 Go 仍放行 ⇒ 鉴权层变成故障放大器）
Evidence:     **双侧活体对拍**（真进程 + 真 PG + 真 Redis，2026-10-24，`tools/difftest/exp-difftest-redis-blacklist.sh`）
              修前 Go 200/1.72s vs RAG 503/3.02s；修后 Go 200/1.72s vs RAG 200/0.03s
              ⇒ **修后两侧同形**。
Must Preserve: 「Redis 不可用 ⇒ fail-open 放行」这条语义（Go 的 `err == nil && exists`）。
              ⚠️ 但**不要**把它改成「Redis 不可用 ⇒ 503」——那是反向漂移。
Recommended Verification: `UserApiRedisDisconnectedFastFailTest`（2 条 · 真容器 + 真故障注入）
              + 变异验证（`REJECT_COMMANDS` ⇒ `DEFAULT`）⇒ 两条都红，耗时那条实测 **3017ms**。
```

#### 🆕 本条的**修法覆盖边界**（2026-10-24 第十轮补）

`REJECT_COMMANDS` 只在 endpoint **已经处于 disconnected 态**时生效 ⇒ 它覆盖的是
「**连上之后断连**」。**「从头就没连上过」**是另一条路径（命令要等连接建立）⇒ 另行实测：

| 形态 | Go | RAG | 等价 |
|---|---|---|---|
| 端口拒连（`127.0.0.1:<死端口>`） | 200 / 1.79s（稳态 1.79~1.80s） | 200 / 0.57s（稳态 0.06~0.07s） | ✅ 同形状 |
| **主机名解析失败**（`redis`） | 200 / 1.81~1.92s（**稳定**） | **503/200 摇摆**（3.03s / 2.93s / 3.01s） | ❌ **⇒ `DRIFT-USERAPI-015`** |

⇒ 即：**「端口拒连」形态已被本条的修法覆盖**（两侧同形状）；
⇒ 但**「主机名解析失败」形态没被覆盖**，且它**正是生产默认值**（`${REDIS_ADDR:redis}`）。

---

### DRIFT-USERAPI-015 —— Redis **主机名解析不出来**（生产默认形态）：Go 恒 200 fail-open，RAG 在 **200/503 之间摇摆** —— 🔴 **2026-10-24 第十轮新发现 · 2026-10-26 第十二轮：复现条件被查穿 ⇒ 定性降级为「条件性成立 · 证据不足」**

> 🔴 **2026-10-26 第十二轮结论先行（下面整段原始记录保留供追溯，但**已不是当前事实**）**：
> **本条的原始观测（Go 1.8~1.9s 恒 200 vs RAG 2.93~3.03s 摇摆）在 2026-10-26 的实测里复现不出来**；
> 而**唯一能确定性复现的「卡住」形态下，两侧都是 503（同形，不是漂移）**；
> **候选②（`connect-timeout=1s`）实测会把 RAG 改成 200、Go 仍是 503 ⇒ 制造反向差异 ⇒ 建议否决**。
> ⇒ **本条不再是「选哪个修法」的拍板题，而是「要不要让 RAG 比 Go 更 fail-open」的取舍题**（详见下方「第十二轮实测」）。

> ✅ **2026-10-26 第十一轮：影响面已收窄 —— 本条只存在于 **api 侧**，rpc 侧经实测**无漂移**。**
> ⇒ 三形态（拒连 / 解析失败 / **挂起**=命令超时路径）× 双侧 **gRPC 直连**实测：
> 两侧 gRPC **code 全部相同**（`Internal`/`Internal`；`Internal`/`Internal`；`DeadlineExceeded`/`DeadlineExceeded`）
> ⇒ ⇒ **015 不存在于 user-rpc** ⇒ 修法**只需落在 api 侧**（与 §10 的跨域归属结论一致）。
> ⚠️ 唯一的不一致是 description（`context deadline exceeded` vs `rpc timeout after 2000ms`）⇒ **正是 `DRIFT-USERRPC-V-007`（已销账 · 待合回 `do-my-own-gh`；📌 合回与否已拍板 2026-10-26：暂不合回 ⇒ 主干仍见旧串）**，**不是新漂移**。
> ⇒ 同轮测出**另一条**观测面差异（RAG 业务日志说「Redis 写入异常」而客户端看到的是「超时」）⇒ 另立 **`DRIFT-USERRPC-V-012`**，在 [`user/rpc.md`](./rpc.md)。
> ⇒ 逐形态数据与复现入口 ⇒ [`user/rpc.md` 的「2026-10-26 第十一轮」](./rpc.md)。

```text
Component:    JwtAuthInterceptor（黑名单 EXISTS）/ Lettuce 的连接建立与超时设置
Operation:    全部 protected 端点在「Redis 地址**解析不出来**」时的响应
Category:     State Semantics · Failure Semantics（可用性语义）+ **不确定性**

⚠️ 本条的来源是**一次误诊**（2026-10-24 第十轮，写守网测试时撞出来的）：
  测试环境**没有 Redis** ⇒ 首个受保护请求 3.31s ⇒ 撞 3s HTTP 预算 ⇒ 503
  ⇒ 表面看与已修的 `DRIFT-USERAPI-014` 同型（「Redis 不可用 ⇒ RAG 503 / Go 200」）
  ⇒ **但那次误诊把「原因」也搞错了**：3.31s 不是「Lettuce 排队等重连」造成的，
  而是**测试机上 `redis` 这个主机名解析不出来**（= 生产默认值）。
  ⇒ 本条是**顺着这次误诊**、用双侧活体对拍**重新测**出来的**真漂移**。

Go Behavior（实测，2026-10-24 19:17，真进程 + 真 PG + 真 DNS 失败）：
  go-zero@v1.10.2 core/stores/redis/redisclientmanager.go:32-40
    red.NewClient(&red.Options{Addr: r.Addr, …, MaxRetries: 3, MinIdleConns: 8})
    ⚠️ **不设** DialTimeout / ReadTimeout ⇒ 用 go-redis v9 默认值
  实测日志：
    redis: connection pool: failed to dial after 5 attempts:
          dial tcp: lookup redis: no such host
    [REDIS] slowcall on executing: exists user:jwt_blacklist:… duration: 1802.2ms
  ⇒ **5 次拨号全部快速失败**（DNS NXDOMAIN 是同步返回的）⇒ 失败耗时 ≈ 1.8~1.9s
  ⇒ < Go 的 HTTP 预算 3s（rest/config.go:55 Timeout int64 `json:",default=3000"`）
  ⇒ 进业务层 ⇒ checkblacklistmiddleware.go:36 `if err == nil && exists` 判 false
  ⇒ **fail-open 放行 ⇒ HTTP 200**（三轮**全部** 200，1.81/1.90/1.92s，**稳定**）

Spring Boot Behavior（实测，同一轮）：
  application.yml:15-18
    host: ${REDIS_ADDR:redis}      ⇒ **默认就是主机名**（docker-compose 服务名）
    timeout: 3s                    ⇒ **命令超时 = HTTP 预算**（observability.http-timeout: 3s）
  ⇒ DNS 解析失败时 Lettuce 的命令要等连接建立 ⇒ 失败耗时**贴着 3s commandTimeout**
  ⇒ **竞速**：commandTimeout 先到 ⇒ HttpMiddleware 的 3s 预算先耗尽 ⇒ **503 "Request Timeout"**；
    预算先到之前失败 ⇒ fail-open ⇒ **200**
  ⇒ 实测三轮：**503 / 200 / 503**（3.027s / 2.934s / 3.012s）——**同输入不同输出**
  ⇒ 实测日志：`JwtAuthInterceptor : Redis blacklist check failed : Unable to connect to Redis` ×3

Difference:  同一个「Redis 地址解析不出来」的故障：
            Go = **稳定** 200 fail-open（1.8~1.9s）
            RAG = **不稳定** 200/503 摇摆（2.93~3.03s，恰好卡在 3s 预算边缘）
Semantic Impact: OBSERVABLE DIFFERENCE（可用性语义分叉 + **不确定性**）
            ⚠️ 比 DRIFT-USERAPI-014 更严重：014 是「稳定地错」，这条是「**时对时错**」
            ⇒ 客户端无法据响应判断 Redis 是否故障 ⇒ 监控上表现为「随机 5xx」
Risk:         MEDIUM-HIGH（触发条件罕见但真实：compose 里 Redis 服务被移除 /
            网络命名空间异常 / Redis 容器重建期间 DNS 短暂不可解析）
Evidence:     **双侧活体对拍**（真进程 + 真 PG + 真 DNS 失败，2026-10-24 19:15 与 19:17 两轮，
              `tools/difftest/exp-difftest-redis-neverup.sh`，每形态 3 轮 × 双侧）
Must Preserve: 「Redis 不可用 ⇒ **fail-open 放行**」（Go 的 `err == nil && exists`），
              且必须是**稳定**的 200，而不是「有时 200 有时 503」。
              ⚠️ **不要**改成「Redis 不可用 ⇒ 稳定 503」——那是反向漂移。
```

#### 第十二轮实测（2026-10-26）—— **先验候选② + 查穿复现条件**

> **触发**：Jason 指示「继续」⇒ 本域**不催拍板**，先把「要不要改」变成「改了会怎样」。
> **工具**：新建 `tools/difftest/exp-userapi-redis-dns-connect-timeout.sh`（**本域资产；未改任何共享脚本**）
> ⇒ 三个变体（Go api / RAG api 现配置 / RAG api +`spring.data.redis.connect-timeout=1s`）× 两种形态（`FORM=dns` / `FORM=blackhole`）
> ⇒ **每轮都打双侧**；**每个变体各用各自的新主机名**（负控见下）。

**形态一 · `FORM=dns`（主机名解析失败 · 名义条件）—— 3 轮 × 8 探测 = 24 次**

| 变体 | code | 耗时（s） |
|---|---|---|
| Go api | **200 ×8** | 1.802 / 1.762 / 1.771 / 0.096 / 0.107 / 0.096 / 0.003 / 0.003 |
| RAG api（现配置） | **200 ×8** | 0.562 / 0.108 / 0.107 / 0.094 / 0.109 / 0.772 / 0.089 / 0.080 |
| RAG api +connect-timeout=1s | **200 ×8** | 0.932 / 0.106 / 0.098 / 0.095 / **2.359** / 0.189 / 0.120 / 1.206 |

⇒ **两侧同形（都 200 fail-open）⇒ 本形态下无漂移** ⇒ **原始那条「RAG 200/503 摇摆」复现不出来**。
⇒ ⚠️ **一次失败的先验（负控价值）**：第一轮先验时三个变体**共用 `redis` 这一个主机名** ⇒ 全部 200 ⇒ **无区分度** ⇒ 事后查明是**本机 DNS 负缓存**（该名字此前已被反复解析失败过 ⇒ 解析立即失败 ⇒ 根本没走到「命令排队等 commandTimeout」）。
⇒ **修正**：每个变体改用**全新随机主机名**（保证都是「首次解析、无负缓存」）⇒ 复跑结果同上。
⇒ 🔴 **Go 侧顺带订正**：Go 的失败耗时**不是「稳定 1.8s」**，而是**两段式** —— 前 3 轮 ~1.75~1.80s（5 次拨号快速失败）⇒ 之后连接池记住失败 ⇒ **0.10 / 0.07 / 0.002s**。
⇒ ⇒ 原记录「三轮全部 200，1.81/1.90/1.92s，**稳定**」里的「稳定」是**误读**（只测了 3 轮、没测到稳态）⇒ **本条已订正**（与 `exp-difftest-redis-neverup.sh` 脚本头「⚠️ 必须分次探测：首个请求 ≠ 稳态」一致 —— 当年那条纪律没被应用到**Go 侧**的结论上）。

**形态二 · `FORM=blackhole`（连接建立确定性卡住 · IP 直指「SYN 被丢」的地址）—— 4 探测**

| 变体 | code | 耗时（s） |
|---|---|---|
| Go api | **503 ×4** | 3.002 / 3.002 / 3.001 / 3.002 |
| RAG api（现配置） | **503 ×4** | 3.010 / 3.003 / 3.002 / 3.002 |
| RAG api +connect-timeout=1s | **200 ×4** | 1.711 / 1.012 / 1.011 / 1.012 |

⇒ **两侧都是 503（3s 撞 HTTP 预算）⇒ 同形 ⇒ 这个形态不是漂移**（RAG 的 3s 与 Go 的 3s 是同一个机制：各自的客户端等不到 Redis ⇒ 预算耗尽）。
⇒ 🔴 **候选②在这里是「把一致改坏」**：它把 RAG 变成 200 fail-open，而 Go 仍是 503 fail-closed ⇒ **制造一条反向的新差异** ⇒ **候选②建议否决**。
⇒ ⚠️ 同时它**证明候选②确实生效**（1.71 → 1.01s）：`spring.data.redis.connect-timeout` 被 Boot 消费到 `SocketOptions.connectTimeout`
　　（消费点：`LettuceConnectionConfiguration.createClientOptions()` → `SocketOptions.builder().connectTimeout(...)`；`spring.data.redis.timeout` 走的是另一条路 → `commandTimeout(...)`）
　　⇒ **属性名不是臆造**（§3.3.1：读消费点，不看配置名）。但**生效 ≠ 该做**。

**⇒ 成立条件必须补全（原记录漏了一个维度）**

原始数据（RAG 3.03 / 2.93 / 3.01s）要求：**Java 侧的连接建立/DNS 解析卡住 ≥3s，而 Go 侧在 3s 内拿到确定失败**。
⇒ 今天的实测里**两侧的解析都快（~0.1~0.36s）** ⇒ 该条件不成立 ⇒ **原始观测无法在当前环境重建**。
⇒ ⚠️ **因此不得宣布「015 不成立」** —— 只能记「**当前环境不可复现，且原始记录的环境条件未写全**」。
⇒ 🔴 **风险窗口的真实形状**：若生产上 DNS 真卡住 3s+，**RAG 与 Go 都是 503（同形、可接受）**；真正的风险窗口是「**Go 快、RAG 慢**」的那一段，而它需要**两侧 DNS 行为不同**的环境条件（本机今天不具备）⇒ ⇒ **本条定性为「条件性成立 · 证据不足」**：既不按「已修」销账，也**不据此改配置**。

#### 四个候选修法的最新口径（2026-10-26 实测后）

| # | 改法 | 口径 |
|---|---|---|
| ① | 把 `spring.data.redis.timeout`（**命令超时**）从 3s 压到 1s | 🔴 **仍不可取**：Go 的 go-redis 默认 `ReadTimeout=3s`，与 HTTP 预算**同刻** ⇒ 已对齐的 CASE-05b（Redis 很慢 ⇒ 两侧都 503）正是靠这层「巧合」成立的；压到 1s ⇒ RAG 200 / Go 503 ⇒ **把一条已对齐的 CASE 改成新的漂移** |
| ② | 显式设 `spring.data.redis.connect-timeout=1s` | 🔴 **建议否决**（原写「可能管不住 DNS 解析耗时，必须实测验证」—— 实测结论：**它管的是 TCP connect、确实生效**；但在唯一可确定性复现的卡住形态下**制造反向差异** RAG 200 / Go 503 ⇒ **离 Go 更远**） |
| ③ | 给 Lettuce 换**带超时**的 DNS resolver | 🟡 仍是**理论上最对症**的那个（掐 DNS 那一段）⇒ 但同样会把 RAG 推向 200 ⇒ **与 Go 的 503 分叉** ⇒ **「RAG 在 Redis 卡住时比 Go 更可用」是业务取舍，不是对齐问题** ⇒ 若要 ⇒ 走**签字档（有意偏离）** |
| ④ | 在 `JwtAuthInterceptor` 的黑名单查询上单独设更短超时 | 🟡 同 ③：改的是 fail-open/fail-closed 的**取舍** ⇒ 同样应走签字档，而不是伪装成「对齐 Go」 |

⇒ **给拍板方的建议**：**015 已经不是「选哪个修法」的技术题**，而是「**要不要让 RAG 在 Redis 卡住时比 Go 更 fail-open**」的**取舍题**：
· **不改** ⇒ RAG 与 Go **同形**（都 503）⇒ **默认推荐**（AGENTS §1.1：差异只允许出现在表达层）；
· **改** ⇒ 明确承认「RAG 在 Redis 卡住时更可用」⇒ 登记为**有意偏离**（`docs/Intentional_Deviations/users/user/api.md`）⇒ 而**不是**当「对齐 Go」来做。

---

## 8. Test Cases（§29 + §30）

⚠️ **本节的 CASE 是「应当成立的语义对」，不是「已跑过的测试」。**
初版（2026-10-23）全部为静态推导；此后各轮陆续补上双侧活体对拍
（第三轮 claim 语义 / 第六轮真 Tomcat 400 header / 第七轮客户端熔断 E2E /
第八轮四个过载机制 / **第九轮 Redis 故障注入**）。
标 ✅ 的表示两侧已确认一致（静态或活体，见「证据强度声明」）；标 ❌ 的对应 §7 的 DRIFT 条目；
标 ? 的进 §9。**§9 现已清空**（001~005 全部闭环）。

| CASE | 场景 | Go 期望 | RAG 期望 | 等价 | 备注 |
|---|---|---|---|---|---|
| CASE-01 正常成功 | 合法 body + 合法 token | 200 + `{code:200,msg:OK,data:…}` | 同 | ✅ | |
| CASE-02 空输入（零字段 DTO） | `{}` 打 `/get` | 200（零必填） | 200 | ✅ | |
| CASE-02b 空输入（必填 DTO） | `{}` 打 `/login` | 400 `field "username" is not set\n` | 同 | ✅ | 文本逐字对齐 |
| CASE-02c 空输入（真空 body） | Content-Length 0 打 `/login` | 400 同上 | 400 同上 | ✅ | |
| CASE-03 不存在 | token 合法、用户已删 | 1003 @200 | 1003 @200 | ✅ | |
| CASE-04 重复调用 | 同一 `/get` 调两次 | 两次一致，无副作用 | 同 | ✅ | |
| CASE-05 外部依赖失败（Redis **断连**） | 黑名单查询报错（Redis 优雅断开） | **200**（fail-open 放行，耗时 ~1.7s） | **200**（fail-open 放行，耗时 ~0.03s） | ✅ **已对齐** | **DRIFT-USERAPI-014 已修**（活体对拍实测） |
| CASE-05b 外部依赖失败（Redis **很慢**） | Redis 连着但不回包（`CLIENT PAUSE`） | **503** "Request Timeout"（3.003s） | **503** "Request Timeout"（3.018s） | ✅ **完全一致** | 两侧 HTTP 预算都是 3s ⇒ 同时到点 |
| CASE-05c 黑名单命中（Redis 正常） | token 在黑名单 | **401** `{code:401,msg}` | **401** envelope(1006) | ⚠️ 已签字 N-1 | **对照组**：证明黑名单机制真在跑（否则 CASE-05 的 200 是假绿） |
| CASE-05d 黑名单命中（Redis 断连） | token 在黑名单 + Redis 断 | **200**（fail-open，**不是** 401） | **200**（fail-open，**不是** 401） | ✅ **已对齐** | fail-open 的**语义**证据：故障时不得误拦 |
| CASE-05e 外部依赖失败（Redis **从未连上**·端口拒连） | Redis 地址指向**没人监听**的端口 | **200**（fail-open，1.79s，**稳定**） | **200**（fail-open，首轮 0.57s / 稳态 0.06s） | ✅ **已对齐** | 第十轮双侧活体 ⇒ **DRIFT-USERAPI-014 的修法覆盖该形态** |
| CASE-05f 外部依赖失败（Redis **主机名解析失败**） | `redis` 主机名解析不出来（**生产默认形态**） | **200**（fail-open，1.81~1.92s，**稳定**） | **503 / 200 / 503**（3.03 / 2.93 / 3.01s，**摇摆**） | ❌ | **DRIFT-USERAPI-015**（第十轮新发现）<br>🔴 **2026-10-26 订正**：① Go 侧**不是「稳定 1.8s」**，而是**两段式**（前 3 轮 ~1.75~1.80s → 连接池记住失败后 **0.10 / 0.07 / 0.002s**）⇒ 原「三轮 1.81/1.90/1.92s 稳定」里的「稳定」是**误读**（只测了 3 轮、没测到稳态）<br>② **RAG 侧的「摇摆」在 2026-10-26 复现不出来**（dns 形态 24 次探测两侧都 200；blackhole 形态两侧都 503）⇒ 本条**定性降级为「条件性成立 · 证据不足」**，**不再按「已对齐的漂移」处理**，见 §7 该条 |
| CASE-06 超时 | handler 睡 > 3s | 503 | 503 | ✅ 状态码；⚠️ 作用域 | DRIFT-USERAPI-009 |
| CASE-07 Retry | rpc 失败 | 不重试 | 不重试 | ✅ | |
| CASE-08/09 Cache | — | N/A | N/A | — | 本组件无缓存 |
| CASE-10 权限失败（无 token） | protected 端点不带 Authorization | 401 **空 body** | 401 + envelope(1006) | ⚠️ 已签字 N-1 | |
| CASE-10b 权限失败（签名错） | 带错签名 | 401 空 body | 401 + envelope(1006) | ⚠️ 已签字 N-1 | |
| CASE-10c **权限失败（claim 畸形）** | 合法签名、无 `userId` claim | **200 + 1006** | **401 + 1006** | ❌ | **DRIFT-USERAPI-001** |
| CASE-10d 黑名单命中 | token 在黑名单 | 401 + `{code:401,msg}` | 401 + envelope(1006) | ⚠️ 已签字 N-1 | Content-Type 见 DRIFT-USERAPI-010 |
| CASE-10e **`uid <= 0`（delete）** | 合法签名、`userId:0` | 透传 rpc（0 不被拒） | 1006，0 次 rpc | ❌ | **DRIFT-USERAPI-002** |
| CASE-10f **`uid <= 0`（get）** | 同上打 `/get` | 1006 @200 | 1006 @200 | ✅ | |
| CASE-11 并发调用 | 同 uid 并发 10 次 `/get` | 10 次一致，无锁 | 同 | ✅ | 本组件无状态 |
| CASE-12 重复调用（注销） | 同 token 注销两次 | 第一次成功；第二次取决于 user-rpc | 同 | ? | **依赖 user-rpc**（U-003） |
| CASE-13 部分操作成功 | — | N/A | N/A | — | 本组件无多步写 |
| CASE-14 事务回滚 | — | N/A | N/A | — | 本组件无事务 |
| CASE-15 序列化边界（`uid` 字符串） | 注册成功 | `"uid":"123…"`（字符串） | `"uid":"123…"`（字符串） | ✅ | `,string` 已对齐 |
| CASE-15b 序列化边界（`score`） | `/get` 成功 | `"score":0` 恒存在 | `"score":0` 恒存在 | ✅ | |
| CASE-15c **序列化边界（400 header）** | 必填缺失 | `Content-Type: text/plain; charset=utf-8` | `Content-Type: text/plain` | ❌ | **DRIFT-USERAPI-006** |
| CASE-15d **序列化边界（绑定面）** | `?Username=a&Password=b` 空 body | **400 `field "username" is not set`** | 400 `field "username" is not set` | **YES** | 原判 ❌，**已证伪更正**（`330bafb`） |
| CASE-15e **序列化边界（history 的 body）** | `/avatar/history` 发畸形 JSON | 200 | 400 | ❌ | **DRIFT-USERAPI-005** |

### 8.1 守门清单（第十轮新增 · 本组件的**服务接线层**回归网）

| 守门 | 覆盖什么 | 为什么归 user 域（不是跨域） |
|---|---|---|
| `UserApiProtectionMvcTest`（**6 条**） | ① 四个保护 Filter **真的注册进本模块上下文**（`scanBasePackages` 含 `com.rag.common` ⇒ 零装配代码 ⇒ **没有任何编译期/启动期信号**告诉使用者它生效了）<br>② **结构证据**：MaxBytes 的 `@Order` 排在 HttpMiddleware(3s) 之后 ⇒ 413 落在 3s 预算**之内**（Go 侧在预算之外 ⇒ 观测面差异）<br>③ **行为**：超限 ⇒ **413 空 body 且压过 401**；未超限无 token ⇒ 仍 401；带合法 token ⇒ 走业务逻辑 200<br>④ MaxConns 闸门满 ⇒ 并发第 2 个请求 **503 空 body**（Go 是裸 503） | 共享层（`service/common`）负责实现，而「**本服务有没有把共享层扫进来**」取决于**各服务自己的** `scanBasePackages` ⇒ **天然是域级义务**。<br>先例：like-api / message-api / task-api / article-api **都写了** ⇒ **user-api 是当时唯一没写的那一个**（2026-10-24 台账复核查出） |

**变异验证（4 个，全部按预期）** —— 判据按 AGENTS.md §3.3：**编译失败不算红 / 上下文加载失败不算红 / 断言红才算**。

| 变异 | 期望 | 实测 |
|---|---|---|
| M1 摘掉 `GoStyleMaxBytesFilter` 的 `@Component` | 红 | ✅ 红 3 条：`oversizedBodyIsRejectedBeforeAuth` **413 → 401**；另 2 条 `NoSuchBeanDefinition` |
| M2 摘掉 `GoStyleMaxConnsFilter` 的 `@Component` | 红 | ✅ 红 2 条：`maxConnsGateRejectsSecondConcurrentRequest` 裸 503 → **"Request Timeout"**；另 1 条 `NoSuchBeanDefinition` |
| M3 `GoStyleMaxBytesFilter` 的 `@Order(+6)` → `LOWEST_PRECEDENCE` | **绿**（阴性对照） | ✅ 绿 6/6 —— ⚠️ **这次是我变异选错方向**：`LOWEST_PRECEDENCE` 把 MaxBytes 排得**更内层**，**仍在 3s 预算之内** ⇒ 语义未破 ⇒ 该绿 |
| M4 `GoStyleMaxBytesFilter` 的 `@Order(+6)` → `HIGHEST_PRECEDENCE`（挪到预算**之外**） | 红 | ✅ 红 1 条：`maxBytesIsOrderedInsideHttpMiddleware`（`@Order(-2147483648) 应大于 @Order(-2147483643)`）<br>⚠️ **行为断言（413 压过 401）仍绿** ⇒ 正好印证 `GoStyleMaxBytesFilter` javadoc 的结论：「413 压过 401」靠的是**它是 Filter** 而不是 `@Order` ⇒ **只有结构断言能守住「在 3s 预算之内」这条** |

⚠️ **本类第一次写成时是红的，且红得极具误导性**（"user RPC is nil"）⇒ 根因是**测试环境没有 Redis**，
不是 mock 失效 ⇒ 详见 `docs/Pitfalls/users/_shared.md` 的 **PIT-USERS-SHARED-018**。

### 8.2 本组件（`user-api`）的**销账提交清单**（结构化 · 2026-10-26 补）

> **为什么要补这张表**：本域此前的销账提交**散落在条目标题里**，没有结构化登记
> —— **12 个条目里只有 D-004 带 SHA**，其余 11 个「已修 / 已关闭 / 已签字」条目都**没有**，
> 签字档（`SD-USERAPI-001/002/003`）**也一个 SHA 都没有**
> ⇒ 机器复核工具的「登记的哈希必须存在 / 必须在祖先链上」这类判据**对本域无从下手**。
> ⇒ 本表把「**条目 ↔ 销账方式 ↔ 销账提交**」变成可解析的结构，
> 供 `global` 域的 `DOMAIN_CFG['users']` 直接消费
> （判据规格 ⇒ [`users/_shared.md`](../../_shared.md) 的「**可机检不变量**」一节；
> 同构的 rpc 侧表 ⇒ [`user/rpc.md` §8.1](./rpc.md)）。
>
> ⚠️ **每个 SHA 都逐个 `git show --stat` 核过主题与文件面**（PIT-USERS-SHARED-020），**不靠标题推断**。

| 条目 | 销账方式 | 销账提交（SHA） | 提交主题（逐字） | 备注 |
|---|---|---|---|---|
| **D-001** | 代码修复 | `9ae121d` | `fix(users/user): 撤销 V-001 反向判定 —— Go 侧 NULL status 是 fail-open 而非 fail-closed` | 同 commit 新增 `IdentityResolver.java`、改 `JwtAuthInterceptor.java` ⇒ **一条 commit 兼销 V-001 + D-001 + D-002**（工具若按「一 commit 一行」判会漏） |
| **D-002** | 代码修复 | `9ae121d` | 同上 | 同 commit 改 `UserDeleteService.java` / `UserUpdateService.java`（`uid <= 0` 合法域） |
| **D-003** | 代码修复 | `bfb55ad` | `align(users/user): user-api 关闭 UNRESOLVED-002、定性并修 DRIFT-003` | gRPC 错误码通用映射收窄 |
| **D-004** | 证伪关闭（**无代码修复**）| `330bafb` | `test(users/user): 锁死「user-api 绑定面只有 JSON body」——DRIFT-USERAPI-004 证伪后的守门` | 关闭靠**实测证伪** + 守门测试 |
| **D-005** | 代码修复 | `98fbd13` | `align(users/user): user-api 修 DRIFT-005（avatar/history 不读 body）` | 去掉 `@RequestBody` |
| **D-006** | 代码修复 | `c79d113` | `align(users/user): 修 DRIFT-USERAPI-006（400 响应 Content-Type 补 charset，body 按 UTF-8 写出）` | — |
| **D-007** | ✅ **签字保留**（非缺陷）| `97585cb` | `align(users/user): DRIFT-007 用户签字 ⇒ 收编为 SD-USERAPI-001` | 签字档 `docs/Intentional_Deviations/users/user/api.md` 的 `SD-USERAPI-001`（**该节原先无 SHA ⇒ 本表补上**） |
| **D-008** | 证伪关闭（原断言被推翻）| `c69d735` | `docs(users/user): API-008 原断言「RAG 侧没有任何对应物」证伪（漏 grep service/common）` | 原断言错在**只 grep 了本域**，漏 `service/common` |
| **D-009** | ✅ **① 签字**（② 仍成立）| `e4b9f7c` | `docs(users): API-004 证伪关闭 + API-009/COMMON-005 签字落档（SD-USERAPI-002 / SD-USERSCOMMON-001）` | 签字档 `SD-USERAPI-002`（**原先无 SHA ⇒ 本表补上**）；⚠️ 签字**只覆盖 ①** |
| **D-010** | ✅ **签字保留** | `e4b9f7c` | 同上 | 归 `SD-2026-10-23-01/02/03` 的 **N-1**（401 统一 envelope）⇒ 签字档 `SD-USERAPI-001` + `SD-USERAPI-003` |
| **D-011** | 代码修复 | `9e009e5` | `修 DRIFT-USERAPI-011：user-api 缺熔断异常映射 ⇒ 500+5001（Go 应为 200+1015）` | — |
| **D-012** | 代码修复 | `2178fb4` | `修 DRIFT-USERAPI-012：降载 RT 统计口径（Go 是浮点毫秒+ceil，RAG 是整型截断+至少记 1）` | — |
| **D-014** | 代码修复 | `ea8f01e` | `fix(users/user): 修 DRIFT-USERAPI-014（Redis 断连时 Lettuce 排队 ⇒ 撞 HTTP 3s 预算 ⇒ 503）⇒ 闭环 UNRESOLVED-004` | 顺带闭环 `UNRESOLVED-004` |
| **D-015** | 🔴 **未修** | — | — | 待拍板（**仅 api 侧**；rpc 侧经三形态直连实测无漂移 ⇒ 见 [`user/rpc.md` 第十一轮](./rpc.md)） |

⚠️ **「销账方式」这一列不能省**：本域的销账**不是一种形态** —— 「代码修复」（D-001/D-002/D-003/D-005/D-006/D-011/D-012/D-014）
· 「证伪关闭」（D-004/D-008）· 「签字保留」（D-007/D-009①/D-010）· 「未修」（D-015）
⇒ **只有区分开，机器才能把 §4 矩阵的分类计数与条目逐条对账**（不变量 I1）。

---

## 9. Unresolved（§37）—— **本节已于 2026-10-24 第九轮清空**（001~005 全部闭环）

> ⚠️ **「§9 清空」≠「组件已闭环」** ——
> §9 记的是「**还不知道的事**」，清空只说明「没有未知的了」。
> 组件仍因 **2 条未签字的 OBSERVABLE DIFFERENCE**（`DRIFT-USERAPI-009②` / `DRIFT-USERAPI-013`）
> 而**不能**判 `SEMANTICALLY EQUIVALENT` ⇒ 见 §10。

### UNRESOLVED-001 —— 真 Tomcat 下 400 响应的精确 `Content-Type` 与 body 字节 —— ✅ **第六轮已关（2026-10-24）**

> **答案（真 Tomcat 活体）**：真 Tomcat 11.0.24 下实测
> `Content-Type: text/plain;charset=UTF-8` + `X-Content-Type-Options: nosniff` + 400，
> body 与 Go 逐字相同。
> ⇒ 走查门脚本 `tools/difftest/check-plaintext400-headers.sh` ⇒ **EXIT=0**；
> Go 侧另用 stdlib `http.Error` 探针补齐双侧证据。
> ⇒ **残余差异只有 Content-Type 的字面形态**（分号后空格 + charset 大小写），
> 按 RFC 9110 两者都不显著 ⇒ 属「表述差异」，登记不修。
> 详细证据 ⇒ 本文件第六轮 §「DRIFT-USERAPI-006」小节。

<details><summary>原始提问（保留供追溯）</summary>

```text
Question:    真实容器返回的 400 响应，Content-Type 到底是 "text/plain" 还是
             "text/plain; charset=utf-8"？body 按什么编码落字节？
Go Evidence: net/http.Error 固定写 "text/plain; charset=utf-8"。
RAG Evidence: GlobalExceptionHandler.java:216 setContentType(MediaType.TEXT_PLAIN)。
Why Unknown: 本轮只能证明「传给 setContentType 的字面量没有 charset」。
             「容器/转换器会不会再补」属于运行期行为，MockMvc 结构上测不到
             （UserApiGoStyleBodyContentTypeTest:166 断言的就是 MockMvc 的字段）。
             已用 javap 排除了 MockMvc 自己加 charset 的可能（spring-test 7.0.9），
             但**真 Tomcat** 的 Response.setContentType 与 StringHttpMessageConverter 的
             交互未做活体验证。
Impact:      响应头可比对性；含非 ASCII 的错误文案的正确性（见 DRIFT-USERAPI-006 的 ②）。
```

> ⚠️ 第 ② 项（body 含非 ASCII 时会不会乱码）**实测不成立**：
> Jackson 2.21 的 `StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` 默认关闭，
> 异常文案里原始输入是 `Source: REDACTED` ⇒ 非 ASCII 进不了 body。
> 该前提已被 `UserApiGoStyleBodyContentTypeTest#unreadableBodyMessageDoesNotLeakRawSource` 钉住。
</details>
Required Confirmation: 真 Tomcat 起 user-api，curl -i 打 /user/v1/login（空 body）
             与一个含中文的畸形 JSON，抓原始响应头与 body 字节。
```

### UNRESOLVED-002 —— jjwt `signWith` 按 key 长度选算法，可能与 Go 的 HS256 分叉 —— ✅ **第四轮已关（2026-10-23）**

> ✅ **关闭记录**：见本文件「2026-10-23 第四轮：§9 `UNRESOLVED-002` **已关闭**（并**部分证伪**其原文断言）」一节
> —— 答案不是「两侧会不会分叉」，而是「**RAG 已显式钉住 HS256** ⇒ 永不跨阈值分叉」。

```text
Question:    若 user-api 的 JWT secret 换成 ≥64 字节，RAG 签出的 token 还会是 HS256 吗？
Go Evidence: service/user/common/jwt/jwt.go:12 **显式** jwt.SigningMethodHS256 —— 恒 HS256。
RAG Evidence: JwtUtil.java:21 `.signWith(key)`，不传 alg ⇒ jjwt 按 key 长度自动选
             （<32B HS256，32~47B HS256，≥48B HS512/384）。
Why Unknown: 当前默认 secret 是 36 字节 UUID（application.yml:91）⇒ 两侧都是 HS256，一致。
             但 RAG 的选算法规则与 Go 的显式指定**不是同一条规则**，一旦密钥长度跨过阈值就会分叉，
             且**两侧的 token 将互相验不过**。静态分析无法判定运维是否会改这个值。
Impact:      一旦触发 ⇒ user-api 签的 token 在 Go 侧（及任何按 HS256 验签的组件）全部验不过。
Required Confirmation: 确认 secret 是否有变更计划；若有，则必须显式传 alg 并加跨侧验签用例。
```

### UNRESOLVED-003 —— user-rpc 实际发出的 gRPC code 集合（**需与 user-rpc 审计合并判读**） —— ✅ **第四轮已关（2026-10-23）**

> ✅ **关闭记录**：见本文件「2026-10-23 第四轮：`DRIFT-USERAPI-003` 定性完成并已修」一节
> —— 枚举两侧 code 全量交叉，**21 格中 20 格已一致**，唯一漂移格已修（`DRIFT-USERAPI-003`）。

```text
Question:    user-rpc 对 login / register / delete / update / logout / avatar-history
             这 6 个 RPC，实际会返回哪些 gRPC code？
Go Evidence: 6 个 Logic 的 switch 只列了 Internal / AlreadyExists / NotFound 三种（见 §7 DRIFT-USERAPI-003 的表）。
RAG Evidence: GlobalExceptionHandler.mapRPCStatusToCode 覆盖 5 种码。
Why Unknown: ⚠️ **本组件不假设对端 rpc 是正确的**（用户任务书给定）。
             是否可观察完全取决于 user-rpc 会不会发 INVALID_ARGUMENT / NOT_FOUND / ALREADY_EXISTS
             落在 Go switch 的 default 格子里。本组件**无权也不应**替 user-rpc 下结论。
Impact:      决定 DRIFT-USERAPI-003 保持 OBSERVABLE DIFFERENCE 还是升级为 SEMANTIC MISMATCH。
Required Confirmation: 与 user-rpc 审计会话合并判读 —— 逐个 RPC 对照其 logic 的
             status.Error(...) 调用点，列出实际 code 集合，再与 §7 的表逐格比对。
```

### UNRESOLVED-004 —— Redis 慢/断连时两侧的最终可观察行为 —— ✅ **第九轮已关（2026-10-24）**

```text
Question:    Redis 不可用（不是报错，而是慢到超过预算）时，两侧分别返回什么？
Go Evidence: checkblacklistmiddleware.go:36 判据 `err == nil && exists` ⇒ 出错即放行（fail-open）。
             该查询在 observability 的 3s 预算**之内**（黑名单由 WithMiddleware 包进 route.Handler）。
RAG Evidence: JwtAuthInterceptor.java:204-208 异常吞成 null ⇒ 同样 fail-open。
             但整个拦截器在 HttpMiddleware 的 3s 预算**之内**（DRIFT-USERAPI-009）。
             Redis 客户端超时由 application.yml:18 `timeout: 3s` 控制。
Why Unknown: 若 Lettuce 的命令超时与 3s 预算同量级，谁先触发取决于线程调度；
             RAG 可能先被 obs 预算打断成 **503**，而 Go 会 fail-open **放行**。
             这需要真 Redis（人为加延迟/断连）才能判定，静态不可定。
Impact:      Redis 故障时的可用性语义分叉（放行 vs 503）。
Required Confirmation: 双侧真 Redis 故障注入（iptables DROP 或 redis-cli CLIENT PAUSE）
             + 一个被拉黑的合法 token，比对最终状态码。
```

**✅ 关闭记录（2026-10-24 第九轮 · 双侧活体对拍）** —— 全文见本文件末尾「第九轮」一节。

| 场景 | Go | RAG（修前） | RAG（修后） | 判定 |
|---|---|---|---|---|
| Redis 断连 · 有效 token | 200 / 1.716s | **503** / 3.02s | **200** / 0.029s | ✅ 修后同形 |
| Redis 断连 · **被拉黑** token | 200 / 1.726s | — | **200** / 0.017s | ✅ 两侧都 fail-open 放行（**不是** 401 误拦） |
| Redis 正常 · 被拉黑 token（对照组） | **401** | — | **401** | ✅ 证明黑名单机制真在跑 |
| Redis 很慢（`CLIENT PAUSE`） | 503 / 3.003s | — | **503** / 3.018s | ✅ 完全一致（body 都是 "Request Timeout"） |

⇒ **原假设被证实**：修前 RAG 确实被 3s 预算先打断成 503（`DRIFT-USERAPI-014`），
现已通过 Lettuce `DisconnectedBehavior.REJECT_COMMANDS` 修复，且**「连着但不回包」那一类未被改动**
（仍是两侧一致的 503）。

### UNRESOLVED-005 —— user-rpc 的事务/并发语义不在本组件职责内 —— ✅ **已关（2026-10-24）**

> ✅ **关闭记录**：本条**从来就不是「待验」，而是「不在本组件判定权内」**
> ⇒ 处置方式是**引另一组件的台账**：`docs/DRIFT/users/user/rpc.md`
> （该组件 2026-10-24 已完成事务/并发/DDL 的逐项核对，结论见其 §7 / §8 / §9）。
> ⇒ 本组件**不因「引了台账」而变成已对齐**，只因「这条不再是未知」而关闭。

```text
Question:    delete / update / select 的事务边界、`extra_info` jsonb 写入、
             `is_current` 派生字段是否实时、`FOR UPDATE` 行锁 —— 这些全在 user-rpc。
Go Evidence: 本组件 0 处 DB 访问（grep 零命中），无法提供对照面。
RAG Evidence: 同上；UserRpcClient.java:18 自己也写着「Phase 2b 填肉阶段：可加 retry /
             circuit breaker / deadline propagation（DRIFT-018）」—— 即这三项本组件**明确未实现**。
Why Unknown: 超出本组件组件边界（§2 strict scope）。
             ⚠️ AGENTS.md §3.5 要求** rpc 必须先于 api **，本条正是该排序约束的体现：
             在 user-rpc 判读完成前，§4 矩阵第 **58** 行（并发/事务 · user-rpc 事务边界）必须留 UNKNOWN。
Impact:      §4 矩阵第 18/20/22 行（delete/update 的 rpc 响应语义）最终定性取决于 rpc 侧。
Required Confirmation: 引用 `docs/DRIFT/users/user/rpc.md`（另一会话产出）后回填本节。
```

---

## 10. 最终判定（§39 · 18 项 checklist + 三值结论）

### §39 逐项

| # | 检查项 | 结论 | 依据 / 缺口 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致 | ❌ | **DRIFT-USERAPI-009②**（3s 超时作用域）+ **DRIFT-USERAPI-013**（过载保护，待拍板）+ **DRIFT-USERAPI-015**（Redis 主机名解析失败 ⇒ 200/503 摇摆 · **2026-10-24 第十轮新发现** · 🔴 **2026-10-26 降级为「条件性成立 · 证据不足」：复现不出来**）。⚠️ 此前写的 001/002/004 **均已修/已证伪关闭** |
| 2 | 正常路径是否一致 | ✅ | §4 #5,6,7,8,9,12,16,21,22,24 |
| 3 | 边界输入是否一致 | ✅ | ~~DRIFT-USERAPI-004~~（**已证伪关闭**）· 005（已修）· 006（已修） |
| 4 | NotFound / Empty / Null 是否一致 | ✅ | §4 #28,29；`list`→`[]`、`history` 不会 null、`data`→null 一致 |
| 5 | Error meaning 是否一致 | ✅ | ~~DRIFT-USERAPI-003~~（**第四轮已修**）；§4 #7,8,13,14,15,25 |
| 6 | Error propagation 是否一致 | ✅ | ~~DRIFT-USERAPI-001~~（**第三轮已修**：畸形 claim 走业务层 200+1006） |
| 7 | External dependency failure 是否一致 | ❌ | **第九轮双侧活体实测**：Redis **断连** ⇒ 两侧都 200（fail-open 放行）；Redis **很慢** ⇒ 两侧都 503 "Request Timeout"。⇒ **DRIFT-USERAPI-014 已修**。⚠️ **第十轮补测**：Redis **从未连上·端口拒连** ⇒ 两侧都 200（014 修法已覆盖）；但 Redis **主机名解析失败**（**生产默认形态**）⇒ Go **恒 200** / RAG **200·503 摇摆** ⇒ **DRIFT-USERAPI-015（2026-10-24 第十轮新发现）**<br>✅ **第十一轮收窄**：**rpc 侧**经三形态 × 双侧 gRPC 直连实测**无漂移**（code 全同）⇒ **015 只在 api 侧**<br>🔴 **第十二轮（2026-10-26）复现尝试**：**015 的原始观测没复现出来** —— dns 形态 24 次探测**两侧都 200**；blackhole 形态 4 次探测**两侧都 503**（同形）⇒ **015 降级为「条件性成立 · 证据不足」**；**候选②（connect-timeout=1s）实测会制造反向差异**（RAG 200 / Go 503）⇒ **建议否决** |
| 8 | Cache hit / miss 是否一致 | — | **N/A**：本组件无缓存 |
| 9 | Transaction boundary 是否一致 | — | **N/A**：本组件无 DB；~~见 U-005~~（U-005 **已关**） |
| 10 | State transition 是否一致 | ⚠️ | 本组件无状态（N/A 性质的等价）；user-rpc 侧 U-005 已关 |
| 11 | Side effects 是否一致 | ✅ | §6.11 `success` 字段逐条对齐；metrics 7 个 Counter 对齐（§4 #48,49） |
| 12 | Ordering 是否一致 | ✅ | 验签 → 黑名单 → handler 两侧同序（§4 #40）；⚠️ Filter/Interceptor 层次差异见 DRIFT-USERAPI-009 |
| 13 | Retry 是否一致 | ✅ | 两侧均无重试（§6.8） |
| 14 | Timeout scope 是否一致 | ❌ | **DRIFT-USERAPI-009**（Go 不含验签、RAG 含）—— ⚠️ 签字 `SD-USERAPI-002` **只覆盖 ①（可观测性）**，②（作用域）**不在签字范围** |
| 15 | Concurrency semantics 是否一致 | ✅ | 本组件无状态无写（N/A 性质的等价）；U-005 已关 |
| 16 | Authentication / Authorization 是否一致 | ✅ | ~~DRIFT-USERAPI-001/002~~（**第三轮已修**）；集合/前缀/fail-open/顺序一致（§4 #37-42） |
| 17 | Serialization semantics 是否一致 | ✅ | ~~004~~（已证伪）/ 005（已修）/ 006（已修）；✅ `uid,string` / `score` / `data:null` 一致 |
| 18 | Lifecycle 是否一致 | ⚠️ | 启动顺序等价（§6.12）；❌ 过载能力 **DRIFT-USERAPI-013**（待拍板） |

**统计**：✅ 11 项 / ❌ 3 项 / ⚠️ 2 项 / — 2 项（N/A）

### 结论

```text
SEMANTICALLY NOT EQUIVALENT
```

- **SEMANTIC MISMATCH：0 条** —— ⚠️ 此前记的 2 条（`DRIFT-USERAPI-001/002`）**第三轮均已修**
- **OBSERVABLE DIFFERENCE：3 条** —— `DRIFT-USERAPI-009②`（3s 超时作用域 · 签字只覆盖 ①）
  · `DRIFT-USERAPI-013`（过载保护 · **待拍板**，属共享层）
  · `DRIFT-USERAPI-015`（Redis **主机名解析失败** ⇒ Go 恒 200 / RAG **200·503 摇摆** · **2026-10-24 第十轮新发现** · 🔴 **2026-10-26 降级为「条件性成立 · 证据不足」**：原始观测**复现不出来**，候选②会制造反向差异 ⇒ 详见 §7）
- **UNRESOLVED：0 条** ⇒ **§9 已清空**（001~005 全部闭环：001/005 真 Tomcat、002/003 第四轮、
  004 第九轮双侧活体、005 引 user-rpc 台账）
- **双侧活体对拍证据：多条** —— ⚠️ 此前写「0 条（全部为静态源码）」是**过期状态**：
  第三轮（claim 语义 17 步）、第六轮（真 Tomcat 400 header）、第七轮（客户端熔断 E2E）、
  第八轮（四个过载机制逐条对照）、第九轮（Redis 故障注入）、
  **第十轮（Redis「从未连上」两形态 × 3 轮 × 双侧）** **都已是双侧活体**
- **已签字的有意偏离**：N-1（鉴权失败 body 形状）· `SD-USERAPI-001`（panic 兜底形状）·
  `SD-USERAPI-002`①（observability 位置）· `SD-2026-10-23-01/02/03`（proto package 归一 等）

> ⚠️ **结论的适用边界**：**不是**「已闭环」——
> 仍有 **3 条未签字的 OBSERVABLE DIFFERENCE**（009② / 013 / **015**）挂着，
> 且 `DRIFT-USERAPI-014` 的**跨域推广**（其余 api 服务有同款漂移）尚未做。
> ⇒ 组件**不能**判 `SEMANTICALLY EQUIVALENT`。
> ⚠️ **2026-10-26 第十一轮补充**：另新增 **1 条 user-rpc 侧的观测面差异 `DRIFT-USERRPC-V-012`**
> （超时时的业务日志错误归因）⇒ **未签字的 OBSERVABLE DIFFERENCE 共 4 条**（009② / 013 / 015 / **V-012**），
> 且 **V-012 与 `global` 域的 E38 第②层同型**（在途命令不随客户端 deadline 取消）⇒ 修法口径宜与 global/admin 域一起定。

### 🆕 跨域归属清单（2026-10-24 第十轮 · 「剩下的漂移是不是都在别的域」）

> 用户提问：「现在是否都是跨域的漂移？」
> ⇒ **不是。** 3 条里 **2 条落在 user 域内可独立闭环**，只有 1 条属共享层。
> ⚠️ **2026-10-26 第十一轮**：加入 user-rpc 侧的 **`DRIFT-USERRPC-V-012`** 后 ⇒ **域内可闭环 3 条**（009② / 015 / V-012），**真正跨域仍只有 1 条**（013）⇒ **结论方向不变，且更清楚**。
> ⚠️ 判据（§5.3）：**能不能只改本域文件就闭环**，而不是「像不像大问题」。
> ⚠️ **2026-10-26 第十一轮更新**：加入 V-012 后 ⇒ **域内可闭环 3 条**（009② / 015 / **V-012**），
> **真正跨域仍只有 1 条**（`DRIFT-USERAPI-013`）⇒ **结论方向不变，且更清楚了**。

| 条目 | 形态 | 归属 | 只改本域文件能否闭环 |
|---|---|---|---|
| `DRIFT-USERAPI-009②` | 3s 预算的作用域（Go 不含验签、RAG 含） | **改法 A 落在 `service/users/user/user-api`**（`service/common` 不动） | ✅ **能**（域内）<br>⚠️ 但「全仓一致」要 9 个域各改各的（Go 是逐服务 `server.Use`）⇒ **推广跨域，落地不跨域** |
| `DRIFT-USERAPI-015` | Redis 主机名解析失败 ⇒ 200/503 摇摆 | 主修法 = 改 **user-api 自己的** `application.yml` | ✅ **能**（域内）<br>⚠️ 同款配置另见 `admin-api`（跨子域）与 `favorite-api`（跨域）<br>✅ **2026-10-26 第十一轮收窄**：**rpc 侧实测无漂移**（三形态 × 双侧 gRPC 直连，code 全同）⇒ **本条的修法只需落在 api 侧**，不必连带改 rpc<br>🔴 **2026-10-26 第十二轮**：**原始观测复现不出来**（dns 24 次探测两侧都 200；blackhole 两侧都 503）⇒ **定性降级为「条件性成立 · 证据不足」**；**候选②实测制造反向差异**（RAG 200 vs Go 503）⇒ **建议否决** ⇒ **本条已不是「跨域归属」问题，是「要不要让 RAG 更 fail-open」的取舍题**（若要 ⇒ 走签字档） |
| `DRIFT-USERAPI-013` | 过载保护（breaker 键空间 + 注释与实现矛盾） | **`service/common`**（11 个 protection 类） | ❌ **不能** ⇒ **这才是唯一真正跨域的一条**<br>✅ **2026-10-26 只读复核已完成**（§7 的「7.x-2」）：两侧 key 的**逐字**来源已钉死（Go = 注册期 `route.Path` 模板 / RAG = raw `getRequestURI()`）· RAG 那条「无界风险为零」的 javadoc **是错误的安全保证** · **可观察面在 `article-api`**（user-api 的 9 条路由零路径变量 ⇒ 本组件不可观察）· 方案 B 的**硬约束**（404 取不到模板 ⇒ 不得用 raw URI 兜底）⇒ **落点确认在 global 写权内 ⇒ 本域只报告** |
| **`DRIFT-USERRPC-V-012`**（2026-10-26 第十一轮新增） | 超时时的**业务日志错误归因**：RAG 记「Redis 写入异常」而客户端收到「超时」（Go 一致） | 落点在 **user-rpc 自己的** `UserDomainService.logout`（Redis 写没绑 gRPC deadline） | ✅ **能**（域内）<br>⚠️ 但**修法口径**与 `global` 域 **E38 第②层**（在途命令不随客户端 deadline 取消）**同型** ⇒ 宜与 global / admin 域**一起定**，别各修各的 |

**⇒ 结论**：users/user 域**不是**「只剩跨域的活」。
**域内可独立闭环 3 条（009② / 015 / V-012）**，其中 015 是 **2026-10-24 第十轮新发现**、修法只有一行配置，
V-012 是 **2026-10-26 第十一轮新发现**（观测面，落点在 user-rpc 自己的代码）；
⇒ 唯一必须等共享层的只有 013（且它当前**未签字**、属「待拍板」而非「已确认漂移」）。
⇒ **2026-10-26 第十一轮**：**015 的 rpc 侧因预算结构不同（gRPC 2s < Redis 3s）而不命中** ⇒ 015 只在 api 侧。

**⚠️ 015 的成立条件不是「用了 Redis 就有」，而是「Redis 命令超时 ≥ HTTP 预算」**（实测全量）：

| api 服务 | Redis 命令超时 | HTTP 预算 | 015 是否命中 |
|---|---|---|---|
| **user-api**（本域） | 3s | 3s | ✅ **命中**（等值 ⇒ 竞速 ⇒ 200/503 摇摆）|
| `admin-api`（users/admin 子域） | 3s | 3s | ✅ 命中（同款配置）|
| `favorite-api`（favorite 域） | 3s | 3s | ✅ 命中（同款配置）|
| `follow-api`（follow 域） | **2s** | 3s | ❌ 不命中（2s < 3s ⇒ 提前失败 ⇒ fail-open 200）|

⇒ ⚠️ 推论：**修 015 之前先定「Redis 命令超时与 HTTP 预算的关系」**，
否则每个域的修法会各不相同（follow-api 已经用 2s「意外躲开」了）。
⚠️ 且 follow-api 的 2s 与 Go 的 go-redis 默认 `ReadTimeout=3s` **不同** ⇒
**那本身可能是一条尚未审的差异**（本次未审，登记为待办）。

> **📌 2026-10-26 补齐 Go 侧的具体数值（从 modcache 逐条核实，不靠记忆）**
> `go-redis@v9.20.0/options.go` 的 `Options.init()` 默认值：
>
> | 项 | 默认值 | 出处 |
> |---|---|---|
> | `DialTimeout` | **5s** | `options.go:339-341` |
> | `DialerRetries` | **5** | `options.go:342-344` |
> | `DialerRetryTimeout` | **100ms** | `options.go:345-347` |
> | `ReadTimeout` | **3s** | `options.go:369-372`（`case 0: opt.ReadTimeout = 3 * time.Second`）|
>
> ⇒ 三个直接可用的结论：
> 1. **Go 的「拨号重试 5 次」有出处** —— `DialerRetries=5` ⇒ 日志里的 `failed to dial after 5 attempts` 正是它
>    ⇒ **≈1.7~1.8s 的失败延迟** = 5 次拨号尝试（DNS 解析 + 100ms 重试间隔）⇒ **不是「某次超时」，是「重试链」**。
> 2. **两侧的 Redis 超时值其实是对齐的** —— RAG 的 `spring.data.redis.timeout: 3s` **=** go-redis 的 `ReadTimeout` 默认 **3s**
>    ⇒ ⇒ **015 的根因不是「超时值配错了」**。
>    ⚠️ **真正的结构差在「失败路径」**（不是超时值）：
>    - **Go**：拨号失败**立即返回错误**（`MaxRetries=3` + 每次重试都立即失败）⇒ **~1.8s** 就失败 ⇒ 落在 3s HTTP 预算内 ⇒ **fail-open 200**；
>    - **RAG**：Lettuce 在连接建立期间把命令**挂起**等重连，等满 **commandTimeout 3s** 才失败 ⇒ **~3.0s** ⇒ **与 3s HTTP 预算竞速** ⇒ 200/503 摇摆。
>    ⇒ 机制出处：`service/users/user/user-api/src/main/java/com/rag/users/user/api/config/RedisDisconnectedBehaviorConfig.java` 的**类 javadoc**
>    （Go 出处 = `redisclientmanager.go:27-46`；已做过变异验证：把 `REJECT_COMMANDS` 改回 `DEFAULT` ⇒ 耗时那条实测 3017ms、异常链 `RedisCommandTimeoutException`）。
> 3. ⇒ **`follow-api` 的 2s 比 Go 的 3s 早 1s 失败** ⇒ 若该域 HTTP 预算 ≥ 3s，
>    **RAG 会比 Go 更早返回**（RAG 2s 拿 200 fail-open，Go 还在等到 3s）⇒ **失败耗时与到达形状都不同**
>    ⇒ 这就是「尚未审的差异」的**具体机制**，已随本条一并登记 ⇒ **follow 域审计时可直接照此核对**。

---

## 附录 A：搬移留档（原样保留，未改动一个字节）

> 以下三段是从 `AGENTS.md` 搬来的历史留档。
> **本轮不对它们做任何「顺手修正」** —— 新结论一律走 §7 的新编号，或就地标注「2026-10-23 复核」。

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

---

## 附录 B：2026-10-23 本轮对 §6.1 十条 + N-1/N-2 的复核（**只复核 user-api 相关的部分**）

> 复核方法：对每一条重新取当前源码证据，**不采信「已修」的既有措辞**（`AGENTS.md` §3.3 纪律）。
> 非 user-api 归属的条目（admin-rpc / admin-api / users-common 内部）标注「不属本组件」，**不改判**。

| §6.1 / N 条 | 是否属 user-api | **2026-10-23 复核结论** | 证据（消费点行号） |
|---|---|---|---|
| **1 `@ResponseBodyAdvice` 二次包裹** | ✅ 是 | **已修，且两条防线都在** | `GlobalResponseAdvice.java:57-59`（`body == null \|\| body instanceof Response` ⇒ 放行）+ `:70-73`（非 JSON contentType ⇒ 放行）。回归网：`ResponseEnvelopeAdviceTest` 2 条（Web 层）+ `UserApiMvcPipelineTest` 的 400 用例（真 MVC + 真 filter）。⚠️ 保留意见见 P-3 |
| **5 `SelectAvatar` 的 `is_current` 陈旧** | ❌ **不属本组件** | **本组件无此职责，判定 EQUIVALENT / N/A** | user-api 只做透传：`AvatarSelectService.java:107` → `UserApiSupport.fromProto:61-71` 逐字段复制，**不重算 `is_current`**。陈旧若存在必在 user-rpc ⇒ 归 `user/rpc.md`（另一会话）。⚠️ 不要因为这条在 §6.1 里列着就认为 user-api 有缺陷 |
| **7 `JwtUtil` zero-pad 被删** | ✅ 是（跨 users-common） | **已修** | `JwtUtil.padToHmacKeySize` 存在且 **public**（`JwtUtil.java:44-53`），签发端 `JwtUtil.java:16` 与验签端 `JwtAuthInterceptor.java:85` **共用同一份**。对 user 侧：默认 secret 36 字节 UUID ≥ 32 ⇒ `padToHmacKeySize` 是 **no-op** ⇒ 与 Go（golang-jwt 不校验 key 长度）等价。⚠️ 新增条件项见 U-002 |
| **8 登录 `UNAUTHENTICATED` 未映射** | ⚠️ 部分 | **user-api 侧未复现；但下挂着 DRIFT-USERAPI-003** | `UserLoginService.java:89-97` 只对 `INTERNAL` 特判，其余一律 `default → 1015`，与 Go `loginlogic.go:50-55` **逐条一致** ✅。但 `UNAUTHENTICATED` 恰好落在「两侧都给 1015」的交集里，所以对齐了；若 user-rpc 改发别的码，通用映射就会分叉（U-003） |
| **9 uid 解析失败静默转 0** | ✅ 是 | **已修，且本轮发现了它的残留面** | 已修证据：`IdentityResolver.java:102-108` 对 `!(v instanceof Long)` 与 `uid <= 0` **都抛 `ErrInvalidClaimException`**，无静默转换 ✅。⚠️ **但 Go 侧本来就允许 delete/update 透传 `uid<=0`**，RAG 把这条也一并拒了 ⇒ **DRIFT-USERAPI-002**。换句话说：这条历史回归的修法**过度收紧了一个端点组** |
| **10 HTTP 状态码语义变更** | ✅ 是 | **已修，且经真 MVC 管线验证** | `GlobalExceptionHandler.decideStatus:294-296` 恒 200；鉴权层走 `handleAuthentication:73-78` ⇒ 401；transport 层 `HttpMiddleware.java:560-575 setStatus` / `:660-704 setHeader,setContentType` **立即 propagate**（DRIFT-FRAMEWORK-001 的修法在位）。回归网：`UserApiMvcPipelineTest`（`@SpringBootTest` + `@AutoConfigureMockMvc` + 真 Testcontainers Redis）4 条 401 路径。⚠️ 401 的 **body 形状**仍是 N-1 签字偏离 |
| **10' DRIFT-FRAMEWORK-001** | ✅ 是 | **已修**（`HttpMiddleware.java:560-575`） | `setStatus` / `sendError(int)` / `sendError(int,String)` 三个重载全部立即 `((HttpServletResponse) this.original).…`；`setHeader` / `addHeader` / `setContentType` 同构（`:660-704`）。`HttpMiddlewareTest.non200StatusSurvivesFlushBufferCommit` 锁住时序 |
| **N-1 鉴权失败 body 形状** | ✅ 是 | **仍然成立（未变）**，但本轮**扩大了它的覆盖范围** | 事实复核通过：`service/user` 全树 `grep WithUnauthorizedCallback` **零命中** ⇒ `engine.unauthorizedCallback` 为 nil ⇒ `authhandler.go:116-121` 只写 401、**不写 body** ⇒ Go 的 JWT 401 确为空 body。新增：**DRIFT-USERAPI-010** 把 N-1 的范围从 body 扩到 Content-Type 头 |
| **N-2 Go 中文乱码** | ❌ **不属本组件** | 不改判 | 9 处乱码全在 `user/rpc/internal/logic/`，**没有一处在 user-api**。user-api 只按 `status.Code` 分支、不透传 `status.Message` ⇒ 本轮 6 条 rethrow 端点也只取 `getStatus().getCode()` |
| **§6.1 的 2 / 3 / 4 / 6（admin-rpc 事务 / 邀请码 FOR UPDATE / extra_info jsonb / UpdateSelf 空 email）** | ❌ **不属本组件** | 不改判 | 全部发生在 admin-rpc 的 SQL 层，user-api 零 DB 访问 |
---

## 2026-10-23 第三轮：双侧活体对拍 + 守门测试（claim 语义两条已修）

> 本节把本组件的证据强度从 **「静态源码」** 抬到 **「Go 进程 ↔ RAG 进程 双侧活体对拍」**
> —— 上一节 §「证据强度声明」里写着「**本轮没有任何一条结论来自双侧活体对拍**」，
> 本节**推翻该声明**（只推翻这一点，同节其余 UNRESOLVED 仍然有效，见 §6）。
> 本节同时**修掉了 §7 的两条 SEMANTIC MISMATCH（DRIFT-USERAPI-001 / -002）**。

### 0. 元信息与证据强度（对照上一节的声明）

| 项 | 值 |
|---|---|
| 核对日期 | **2026-10-23**（21:10–21:15 实跑对拍；21:31 重建 jar 后复跑） |
| 组件 | `service/users/user/user-api`（本节）· 对端 `user-rpc` 见 [`rpc.md`](./rpc.md) 同名节 |
| **证据强度（本节新增部分）** | ✅ **双侧活体对拍**（Go api 进程 :19188 ↔ RAG api 进程 :60900，各自接真 PG/真 Redis，逐字比对 body + 状态码） |
| 对拍 harness | 与 `rpc.md` 同一套隔离环境（`userprobe-postgres`:56432 / `userprobe-redis`:57379），理由见 `rpc.md` §0（**并行 admin 会话占用共享端口/容器，不碰**） |
| 探针 | `/tmp/userprobe/probe_auth.py`（claim 语义，5 个 case 逐字比对两侧 status + body）· `/tmp/userprobe/diff.py --scenario user`（17 步全场景） |
| 基线（修复前） | user-api **69**/0/0/0 |
| 终态（修复后） | user-api **76**/0/0/0（新增守门 7 条，**skipped = 0**） |
| 冻结仓库 | Go 二进制从冻结仓 `go build`，配置用 `tools/difftest/conf/go/*.yaml` 的**副本** ⇒ 冻结仓一个字节未动 |

> 🔴 **§「证据强度声明」的三处更正**（上一节写于本轮之前）：
> 1. 「`双侧活体对拍` **0 条**」→ 本节起 **5 条 claim 语义结论**来自双侧活体对拍（`probe_auth.py` 实跑）。
> 2. 「`真 PG` **本组件无 DB 访问 → 0 条**」→ **该判断本身仍然成立**（user-api 确实零 DB 访问），
>    但它**不等于**本组件无法做活体对拍 —— 对拍时 RAG api 连的是**真 user-rpc 进程**，
>    后端落的是**真 PG**。见 §6 的诚实边界。
> 3. 「纯 Mockito **0 条**」→ 本轮新增的守门类替换了 gRPC 边界（`@MockitoBean UserRpcClient`），
>    故**新类的证据强度是「真 MVC + 真 Redis + mock gRPC 边界」**，不是「纯 Mockito」
>    （纯 Mockito 单测 = 数据层与框架层全 mock，本类两者都不是）。**这条是措辞更正，不是结论反转。**

### 1. DRIFT-USERAPI-001 ✅ 已修 —— 畸形 claim 必须是 **200 + 1006**，不是 401

**双侧活体对拍实测（修复前，2026-10-23 21:12）**：

| case | Go | RAG（修复前） | RAG（修复后） |
|---|---|---|---|
| claim 缺失（无 `userId`） | `200 {"code":1006,…}` | ❌ `401 {"code":1006,…}` | ✅ `200 {"code":1006,…}` |
| claim 是字符串 | `200 {"code":1006,…}` | ❌ `401 {"code":1006,…}` | ✅ `200 {"code":1006,…}` |

**Go 出处（消费点，非声明点）**：
- `github.com/zeromicro/go-zero/rest/handler/authhandler.go:54-81` ——
  只有 `ParseToken` 失败（`:56-62`）与 `!tok.Valid`（含 `exp` 过期，`:64`）两条会走 401 分支；
  `:65-79` 把**所有非标准 claim**（含 `userId`）塞进 `ctx.Values`，`:81` 直接 `next.ServeHTTP`
  ⇒ **claim 的存在性与类型在鉴权层从不校验**。
- `service/user/user/identity/resolve.go:70-73` ——
  `claim, ok := ctx.Value("userId").(json.Number)`；`!ok` ⇒ `ErrInvalidClaim`。
- `service/user/user/api/internal/logic/user/deleteuserlogic.go:37-41` ——
  同样的 type assertion，失败即 `return nil, errmsg.ErrorTokenRuntime`。
- `service/user/user/api/internal/handler/user/deleteuserhandler.go:27` ——
  `httpx.OkJson(w, &response.Response{...})` ⇒ **HTTP 200**（不是 401）。

**RAG 修法**（`interceptor/JwtAuthInterceptor.java:153-195`）：
claim 不是 `Number` 时**只 `log.warn`（`:194`）、不写 `userId` attribute、不抛异常**，
继续走黑名单检查（`:220`），把判定交给业务层。
⚠️ 注意：**黑名单检查没有因此被跳过** —— 这与 Go 一致（Go 的黑名单中间件在 JWT 之后独立跑，
拿不到 `userId` 时用别的键；本实现的 `:220` 用 `claimUsable ? uid : "n/a"` 表达同一件事）。

**可证伪性**：把 `:154` 的分支改回抛 `AuthenticationException` ⇒ 守门用例 1 / 2 立刻红。

### 2. DRIFT-USERAPI-002 ✅ 已修 —— delete / update 的 `uid<=0` 必须**透传**

**双侧活体对拍实测（修复前，2026-10-23 21:12）**：

| case | Go | RAG（修复前） | RAG（修复后） |
|---|---|---|---|
| `userId=0` → `delete` | `200 {"code":1003,"msg":"用户不存在"}` | ❌ `200 {"code":1006,…}` | ✅ `200 {"code":1003,…}` |
| `userId=0` → `update` | `200 {"code":1003,"msg":"用户不存在"}` | ❌ `200 {"code":1006,…}` | ✅ `200 {"code":1003,…}` |
| `userId=0` → `get` | `200 {"code":1006,…}` | ✅ `200 {"code":1006,…}`（本就一致） | ✅ 不变 |
| `userId=0` → `avatar/history` | `200 {"code":1006,…}` | ✅ `200 {"code":1006,…}`（本就一致） | ✅ 不变 |

**Go 出处（消费点）**：
- `service/user/user/api/internal/logic/user/deleteuserlogic.go:37-47` ——
  `type assertion 失败 ⇒ 1006`；`userId.Int64()` 之后**直接 `rpcReq := &pb.DeleteUserReq{Uid: uid}`**，
  **全函数无 `uid <= 0` 判定**。
- `service/user/user/api/internal/logic/user/updateuserlogic.go:36-46` —— 同构，无 `uid<=0` 判定。
- **对照组**：`service/user/user/identity/resolve.go:66-79` 的 `ClaimedUID` ——
  `:75` 是 `if err != nil || uid <= 0 { return 0, ErrInvalidClaim }`，**有** `uid<=0` 判定。
  ⇒ Go 侧 `get` / `avatar*` 走 `ClaimedUID`（拒），`delete` / `update` **不走**（放行）。
  **两套语义并存是 Go 的事实，不是笔误。**

**RAG 修法**：新增 `IdentityResolver.claimedUIDAllowNonPositive`（`service/IdentityResolver.java:160-172`，
只在 `:169` 去掉 `uid<=0` 判定，type assertion 失败仍抛）；
`UserDeleteService.java:58` 与 `UserUpdateService.java:59` 改用它；
**其余端点继续用 `claimedUID`（`:110` 保留 `uid<=0` 判定）** —— 即**不**统一抹平 Go 的两套语义。

**可证伪性**：把 delete / update 改回 `claimedUID` ⇒ 用例 3 / 4 立刻红（rpc 不再被调用，1003 变 1006）；
把 `get` 改用 `claimedUIDAllowNonPositive` ⇒ 用例 5 立刻红。

### 3. 🔴 对拍暴露的**第三层差异**：差异不止于返回值，还包括**是否触达下游**

本轮特意核对了 **rpc 调用次数**（Go 侧看 rpc 进程日志，RAG 侧看 client 调用计数）：
修复前 `userId=0` 打 delete / update 时，**RAG 一次 rpc 都没发**（在 api 层就被拒了），
Go 则**照发**（`deleteuserlogic.go:47` 构造 `pb.DeleteUserReq{Uid: 0}`）。
⇒ 一个只对比 HTTP 响应的探针**会漏掉这一层**。守门测试因此对 mock stub 做了
`ArgumentCaptor` 断言（`UserApiClaimSemanticsParityTest.java:172`、`:192`），
**锁住的是「rpc 被调了一次且入参 uid=0」，不是「返回了 1003」**。

### 4. 全场景对拍（`diff.py --scenario user`，17 步）

修复后复跑（2026-10-23 21:32，V-010 修复后的新 jar）：

```text
steps=17  asserted=5  byte_equal=12  unexpected=0
```

- 5 条 `asserted` = 已签字偏离，**逐条断言两侧 body**（断言不通过照样算 DIFF）：
  N-1 三条（无 token / 坏 token / 注销后再访问）+ DRIFT-006 两条（畸形 JSON 的 400）。
- 12 条 `byte_equal` = 归一化后**逐字节相等**（uid / history id / 时间戳按 §`diff.py` 的占位规则归一）。
- ⚠️ **首跑出现 3 条 unexpected（`uid` / `history[].id` 未归一）**，根因是**两侧库里都残留了上一轮的
  `dt_user1`** ⇒ `user.register` 直接返 1001 ⇒ `capture` 没抓到 uid ⇒ 归一化器拿不到别名。
  清库后归零。**这不是 RAG 的缺陷，但它是「探针自身的可证伪性」的一个实例：探针也会假红。**
- ⚠️ **该工具测不到本轮修的两条**：`diff.py` 的 case 集里没有畸形 claim / `uid=0` 的 token
  ⇒ 必须用 `probe_auth.py`。同 `rpc.md` §0 对 `create_time` 的说明（`TS_KEYS` 无条件抹平）。

### 5. 守门测试清单（`UserApiClaimSemanticsParityTest`，7 条，**skipped = 0**）

| # | 用例 | 锁住什么 | 变异后 |
|---|---|---|---|
| 1 | claim 缺失 → 200 + 1006 | DRIFT-USERAPI-001 | 变 401 |
| 2 | claim 是字符串 → 200 + 1006 | DRIFT-USERAPI-001 | 变 401 |
| 3 | `userId=0` → delete **透传 rpc**（`ArgumentCaptor` 验 uid=0） | DRIFT-USERAPI-002 | 1006 / 不调 rpc |
| 4 | `userId=0` → update **透传 rpc**（同上） | DRIFT-USERAPI-002 | 1006 / 不调 rpc |
| 5 | `userId=0` → get → 1006 且 **rpc 零调用**（对照组） | Go 的两套语义 | get 放行 uid=0 |
| 6 | `userId=0` → avatar/history → 1006 且 rpc 零调用（对照组） | Go 的两套语义 | 同上 |
| 7 | 签名无效 → 仍然 401 | N-1 未被误伤 | 401 变 200 |

> 用例 5 / 6 是**对照组**，作用是防止「为了让 -002 通过，把 `claimedUID` 的判定也删掉」
> —— 那是修 A 顺手弄坏 B 的最自然路径。**没有对照组的定向修复等于没修。**

**证据强度（诚实标注）**：`@SpringBootTest` + `@AutoConfigureMockMvc`（**真 MVC 管线**：
真 `HttpMiddleware` filter + 真 `JwtAuthInterceptor` + 真 advice，**不是** `standaloneSetup`）
+ **真 Redis**（Testcontainers，黑名单路径必须真连）。
**唯一被替换的是 gRPC 边界**（`@MockitoBean UserRpcClient`，`:123-124`）。
⚠️ **user-api 自身零 DB 访问 ⇒「真 PG 守门」对本组件不适用**（不是漏做，是不适用）。
⇒ **本类是回归守门，不是一致性证据**；一致性证据是 §1/§2 的双侧活体对拍。

### 6. 本轮的诚实边界（什么仍然没验）

1. **§9 的 UNRESOLVED-002（jjwt `signWith` 按 key 长度选算法）仍 unresolved** ——
   本轮造 token 时用的是与 `application.yml` 相同的 36 字节 UUID 密钥（≥32 ⇒ HS256），
   **没有覆盖短密钥分支**，不能据此宣布已对齐。
   ⚠️ **2026-10-24 更正（下方原句是第三轮时点的状态）**：该条**第四轮已关闭** ——
   答案不是「两侧会不会分叉」，而是「**RAG 已显式钉住 HS256** ⇒ 永不跨阈值分叉」。
2. **§9 的 UNRESOLVED-004（Redis 慢 / 断连）仍未验** ——
   `UNRESOLVED-USERRPC-004②`（Redis 启动 fail-fast）与本条是同一件事的两端，本轮都没做。
   ⚠️ **2026-10-24 更正（下方原句是第三轮时点的状态）**：
   两端**都已做** —— rpc 侧见 `docs/DRIFT/users/user/rpc.md` §9.3（**证伪**了「Go 启动期 fail-fast」）；
   api 侧见本文件**第九轮**（双侧活体对拍 + 真 Redis 故障注入）⇒ **`UNRESOLVED-004` 已关**。
3. **DRIFT-USERAPI-003 ~ 010 一条都没动** —— 本轮范围只锁 claim 语义两条。
   其中 003（gRPC 错误码通用映射）与 `user-rpc` 的行为强耦合，
   **若 `user-rpc` 后续改了错误码集合，本条必须重判**（§9 UNRESOLVED-003 仍是合并判读项）。
4. **`HttpMiddleware` / 401 的 body 形状（N-1）本轮未重新取证** ——
   `diff.py` 的 5 条 assert 已覆盖「两侧 body 不同但都在预期集合内」，**不等于**逐字节确认过。
5. **DRIFT-USERAPI-005（`avatar/history` 读不读 body）本轮未验** ——
   `diff.py` 只打了 `{}` 空 body，**没打带 body 的请求** ⇒ 该条仍静态源码级。

### 7. 复核后**不改判**的既有结论

| 条目 | 本轮复核 | 依据 |
|---|---|---|
| 附录 B 第 1 条（`@ResponseBodyAdvice` 二次包裹） | ✅ 仍成立 | 21:12 起的 7 条守门用例全部经**真 MVC 管线**跑通，`body == null \|\| body instanceof Response` 的放行分支在位（否则 200 + 1006 会被二次包裹） |
| 附录 B 第 10' 条（`DRIFT-FRAMEWORK-001` transport status propagate） | ✅ 仍成立 | 用例 7 实测签名无效 → **HTTP 401**（不是 200）⇒ `setStatus` 的 propagate 链路在位 |
| 附录 B 第 7 条（`JwtUtil` zero-pad） | ✅ 仍成立 | 7 条用例的 token 用**同一份** `JwtUtil` 签 + `JwtAuthInterceptor` 验，36 字节密钥下 `padToHmacKeySize` 是 no-op |
| 附录 B 第 8 条（登录 `UNAUTHENTICATED` 映射） | ✅ 仍成立 | `diff.py` 的 `user.login.wrongpw` 步实测两侧均 1002 |
| §9 UNRESOLVED-003（需与 user-rpc 合并判读） | ⏳ **仍 unresolved** | 本轮 `user-rpc` 侧未改错误码集合，故本条**不能**因本轮而关闭<br>⚠️ **2026-10-24 更正**：本行是**第五轮时点**的状态；该条**第四轮就已关闭**（枚举两侧 code 全量交叉 ⇒ 21 格中 20 格一致，唯一漂移格已修 ⇒ `DRIFT-USERAPI-003` 已修），见本文件「第四轮」一节 |
---

<!-- @fix 2026-10-23 第四轮：UNRESOLVED-002 已由 users-common 组件级对照实测关闭 -->

## 2026-10-23 第四轮：§9 `UNRESOLVED-002` **已关闭**（并**部分证伪**其原文断言）

> 本节只关闭**本组件自身的**那条未决项。归属在 `users-common` 的证据全部在
> [`../common.md`](./../common.md)（首轮组件级对照，176 条双侧活体）。

### 1. 结论：分叉**真实存在**，但原文的「后果」断言**被实测证伪**

**原文断言**（`api.md` §9 UNRESOLVED-002，2026-10-23 02:xx 写下）：

> 「一旦触发 ⇒ user-api 签的 token 在 Go 侧（及任何按 HS256 验签的组件）**全部验不过**。」

**实测**（`users-common` 第四轮，双侧活体，6 档密钥 × 双向 12 次交叉验签）：

```
RAG 验 Go 签的： 15B / 32B / 36B / 47B / 48B / 64B   → 全部 VERIFIED
Go 验 RAG 签的： 15B / 32B / 36B / 47B / 48B / 64B   → 全部 VERIFIED
```

⇒ **Go ↔ RAG 互验不会被打断。** 原因：两侧库**都按 token header 的 `alg` 选 MAC，
且都不做算法白名单**（golang-jwt v4 `Parse` 直接 `jwt.GetSigningMethod(alg)`；
jjwt 0.12 同样取 header 的 alg）。⇒ 各自签、各自验，反而通过。

### 2. 分叉本身：已量化，并**已修**

`JwtUtil.getToken` 此前是 `signWith(key)` 单参重载 ⇒ jjwt 照 `SecretKeySpec` 的 JCA 算法名选签名算法：

| 密钥长度 | Go `alg` | RAG `alg`（修前） | 签名长度 |
|---|---|---|---|
| 15 / 16 / 31 / 32 / 36 / 47 | HS256 | HS256 | 43 |
| **48 / 63** | HS256 | **HS384** | 64 |
| **64 / 65 / 128** | HS256 | **HS512** | 86 |

**已修**：显式钉 `SignatureAlgorithm.HS256`，且**自己构造** `SecretKeySpec(..., "HmacSHA256")`
（只传 alg 参数不够 —— `Keys.hmacShaKeyFor` 返回的 key 算法名才是 jjwt 真正采信的来源）。
修后 **11 档全部与 Go 一致**。守门网 `JwtUtilParityTest` 22 条，变异验证 **9 条变红**。
详见 [`../common.md`](./../common.md) §3 `DRIFT-USERSCOMMON-004` 与 §5。

### 3. 真正残留的风险（比原文窄，但非零）

原文的「任何按 HS256 验签的组件」需要拆成两类：

| 消费者类型 | 是否受影响 | 实测 |
|---|---|---|
| Go ↔ RAG 互验（两侧都认 header 的 alg） | ❌ 不受影响 | 12/12 VERIFIED |
| **固定 alg 白名单的消费者**（RFC 8725 §3.2 推荐姿态） | 🔴 **受影响** | 48B → `REJECTED: unexpected signing method HS384`；64B → `REJECTED: unexpected signing method HS512` |

**当前是否已触发：否。** 实测两侧配置的密钥：
- user：`application.yml:91` / Go `usercenter.yaml:11` ⇒ `ae0536f9-…505da0` = **36 字节**
- admin：`application.yml:82` / Go `admincenter.yaml:11` ⇒ `yU-hCm)2*?NQmWG` = **15 字节**

⇒ 均 < 48 字节。**触发条件 = 密钥加长到 ≥48 字节**，修完后该条件已不再成立。

### 4. 附带关闭的一个连带误解：`padToHmacKeySize` 的补零

原文与 `JwtUtil` 旧 javadoc 都暗示「补零会破坏与 Go 的互通」⇒ 实测**不成立**：
HMAC 自身对短于分组尺寸（64 字节）的密钥零填充（RFC 2104），
15 字节原密钥与补零到 32 字节的密钥算出**逐字节相同**的 MAC。
已在 `JwtUtil.padToHmacKeySize` 的 javadoc 写清机理 + 三条边界条件，并加逐字节断言。

### 5. 本节对 §6 / §10 的影响

- §6.3 / §10 第 16 项的 `UNRESOLVED-002` 依赖项**已消除**；
- ⚠️ **§7 的 `DRIFT-USERAPI-010` 等条目不受本节影响** —— 本节只关闭「alg 选算法」这一条，
  其余 OBSERVABLE DIFFERENCE 状态一律以各自条目为准（§0 的纪律：**「已改」≠「已验证」**）。

---

<!-- @fix 2026-10-23 第四轮：DRIFT-USERAPI-003 定性完成（由「条件性」升级为「已确认可达」）+ 已修 -->

## 2026-10-23 第四轮：`DRIFT-USERAPI-003` 定性完成并已修（**不再是「条件性」**）

> 本节回答了 §7 与 §9 `UNRESOLVED-003` 一起悬着的问题：
> **user-rpc 到底会不会发出那些 code**。做法不是推断，而是
> ① 枚举 RAG user-rpc **实际抛出**的 gRPC code 全量，② 逐个 Logic 取 Go api 侧 switch 原文，③ 两张表交叉。

### 1. 两侧事实（都是枚举，不是推断）

**RAG user-rpc 实际抛出的 code（`UserDomainService` 按方法归组，grep 全量）**：

| 操作 | 实际抛出的 code |
|---|---|
| `login` | `INTERNAL` |
| `register` | `ALREADY_EXISTS` · `INTERNAL` |
| `getUser` | `INTERNAL` · `NOT_FOUND` |
| `updateUser` | `ALREADY_EXISTS` · `INTERNAL` · `NOT_FOUND` |
| `deleteUser` | `INTERNAL` · `NOT_FOUND` |
| `selectAvatar` | `INTERNAL` · `INVALID_ARGUMENT` · `NOT_FOUND` |
| `uploadAvatar` | `INTERNAL` · `INVALID_ARGUMENT` · `NOT_FOUND` |
| `getAvatarHistory` | `INTERNAL` · `NOT_FOUND` |
| `logout` | `INTERNAL` · **`INVALID_ARGUMENT`** |

**Go api 侧每个 Logic 的 switch 原文**（`user/api/internal/logic/user/*.go`，逐个 sed 取出）：

| Logic | Internal | AlreadyExists | NotFound | InvalidArgument | default |
|---|---|---|---|---|---|
| `loginlogic` | 5001 | →1015 | →1015 | →1015 | 1015 |
| `registerlogic` | 5001 | 1001 | →1015 | →1015 | 1015 |
| `updateuserlogic` | 5001 | 1001 | 1003 | →1015 | 1015 |
| `deleteuserlogic` | 5001 | →1015 | 1003 | →1015 | 1015 |
| `logoutlogic` | 5001 | →1015 | →1015 | **→1015** | 1015 |
| `getuserlogic` | 5001 | →1015 | 1003 | →1015 | 1015 |
| `getavatarhistorylogic` | 5001 | →1015 | 1003 | →1015 | 1015 |
| `selectavatarlogic` | 5001 | →1015 | **500** | **500** | 1015 |
| `uploadavatarlogic` | 5001 | →1015 | 1003 | **500** | 1015 |

### 2. 交叉结果：**21 个「实际发生的格子」里，20 格已一致，1 格漂移**

| 操作 | RAG 实际 code | Go 结果 | 修前 RAG 结果 | 判定 |
|---|---|---|---|---|
| login | INTERNAL | 5001 | 5001 | ✅ |
| register | ALREADY_EXISTS / INTERNAL | 1001 / 5001 | 1001 / 5001 | ✅ |
| getUser | NOT_FOUND / INTERNAL | 1003 / 5001 | 1003 / 5001 | ✅ |
| updateUser | ALREADY_EXISTS / NOT_FOUND / INTERNAL | 1001 / 1003 / 5001 | 同 | ✅ |
| deleteUser | NOT_FOUND / INTERNAL | 1003 / 5001 | 同 | ✅ |
| selectAvatar | INVALID_ARGUMENT / NOT_FOUND / INTERNAL | **500 / 500** / 5001 | 500 / **1003** / 5001 | ✅（`AvatarSelectService:98-99` 已显式修，见旧 DRIFT-003） |
| uploadAvatar | INVALID_ARGUMENT / NOT_FOUND / INTERNAL | **500** / 1003 / 5001 | 同 | ✅ |
| getAvatarHistory | NOT_FOUND / INTERNAL | 1003 / 5001 | 同 | ✅ |
| **logout** | **INVALID_ARGUMENT** / INTERNAL | **1015** / 5001 | **500** / 5001 | 🔴 **漂移（本轮修）** |

⇒ **`UNRESOLVED-003` 就此关闭**：答案不是「user-rpc 会不会发 INVALID_ARGUMENT」，
而是「**会，而且恰好落在 logout 这一格**」。

### 3. 根因与修法

- **根因**：与旧 `DRIFT-003`（select 头像）**完全同形** —— `UserLogoutService:60-62`
  只做 `catch (StatusRuntimeException e) { throw e; }`，把映射甩给
  `GlobalExceptionHandler.mapRPCStatusToCode`，而那个通用映射有 `INVALID_ARGUMENT → 500` 这一格，
  Go 的 logout switch **没有**。
- 🔴 **最讽刺的一点**：这个类的 javadoc `:26-28` **自己就写着** Go 的正确映射
  （「`其它（默认） → CodeServerBusy (1015)`」），**只是代码没照做**。
  ⇒ §3.2「注释不能替代实现」的又一個实例 —— 且证明**注释写对了也不能当证据**。
- **修法**：在 `UserLogoutService` 内显式还原 Go 的两格 switch（Internal→5001，其余→1015），
  与 `AvatarSelectService:98-99` 同一手法。

### 4. 这条漂移是**真实可达**的（不是理论风险）

链路：`POST /user/v1/logout` 带一个合法但 token 为空的上下文
→ user-rpc `UserDomainService.java:768` 抛 `Status.INVALID_ARGUMENT "Token不能为空"`
（Go 对应 `user/rpc/internal/logic/logoutlogic.go:33`）→ api 层映射分歧。
⇒ 修前 Go 返回 **1015 服务繁忙**，RAG 返回 **500 系统内部错误**。

### 5. 守门网与变异验证（§3.3：绿测试必须是错误答案的守卫）

**两层守门，因为只有一层是不够的**：

| 层 | 测试 | 覆盖 | 证据强度 |
|---|---|---|---|
| Service 层 | `UserLogoutErrorCodeParityTest`（**新建 4 条**） | Internal→5001；INVALID_ARGUMENT→1015；NOT_FOUND/ALREADY_EXISTS/UNAVAILABLE/DEADLINE_EXCEEDED/UNKNOWN→1015；**对照组** Internal 不得被一起映成 1015 | mock gRPC 边界 |
| **端到端 MVC 层** | `UserApiClaimSemanticsParityTest.logout_rpcInvalidArgument_isServerBusyNotInternalError` + `…logout_rpcInternal_staysInternalError`（**新增 2 条**） | 真 `MockMvc` + **真 `GlobalExceptionHandler`** + 真 Redis，断言 **HTTP body 的 `$.code`** | 真 MVC 管线 |

⚠️ **为什么必须有 MVC 层那条**：Service 层护网在变异时红的方式是
「抛出的异常类型不对（`StatusRuntimeException` vs `BusinessException`）」，
**它分辨不出最终是 1015 还是 500** —— 而后者才是本条漂移的全部内容。
⇒ 补 MVC 层后，变异实测红得**精准**：

```
java.lang.AssertionError: JSON path "$.code" expected:<1015> but was:<500>
```

**这行报错就是这条漂移的实测值**（1015 vs 500），已逐条记入本节。

### 6. 本轮回归

| 模块 | 结果 |
|---|---|
| `user-api` | **82** tests / 0 failures / 0 errors / **skipped = 0**（原 76 + 新 6） |
| 对照：Service 层单独跑 | 4/4；MVC 层单独跑 9/9 |

---

<!-- @fix 2026-10-23 第四轮：DRIFT-USERAPI-005 已修（并实测出 Content-Type 415 这条新维度） -->

## 2026-10-23 第四轮：`DRIFT-USERAPI-005` 已修（`avatar/history` 不读 body）

### 1. Go 出处复核（全文 26 行，逐行）

`service/user/user/api/internal/handler/user/getavatarhistoryhandler.go:16-25`：

```go
func GetavatarhistoryHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		l := user.NewGetavatarhistorylogic(r.Context(), svcCtx)
		resp, code := l.Getavatarhistory()          // ← 无 var req、无 httpx.Parse
		httpx.OkJson(w, &response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp})
	}
}
```

⇒ 请求体**从头到尾没被读过**。参数另有出处
（`getavatarhistorylogic.go:32` uid 取自 JWT、`:40` limit **硬编码 30**）
⇒ **这个端点根本不需要任何入参 DTO**。

### 2. 修法（三处，缺一不可）

| # | 改动 | 为什么 |
|---|---|---|
| 1 | `UserController#getAvatarHistory` **去掉 `@RequestBody`**，签名只留 `HttpServletRequest` | Spring 不再有理由去反序列化 body ⇒ 畸形 JSON / 非法 Content-Type 一律不再影响该端点 |
| 2 | `AvatarHistoryService#getAvatarHistory` 去掉 `GetAvatarHistoryReq req` 参数 | 该参数**在方法体里一次都没被用过**（uid 走 JWT、limit 走常量）—— 留着它就等于留着一次无意义的 body 绑定 |
| 3 | **删除** `dto/GetAvatarHistoryReq.java`（空类，删除后已零消费者） | §3.4.1 D-2：零消费者的代码不能算已对齐 |

### 3. 🆕 变异验证顺带实测出**一条原台账没记录的新维度**

原台账只预测了「畸形 JSON → Go 200 / RAG 400」。变异实测（把 body 绑回去）：

| 输入 | Go | 修前 RAG | **变异实测** |
|---|---|---|---|
| `Content-Type: application/json` + `{"a":}` | 200 | 400 | `Status expected:<200> but was:<400>` |
| `Content-Type: text/plain` + 任意文本 | 200 | 400 | **`Status expected:<200> but was:<415>`** |

⇒ **多出一条原台账没写到的差异：RAG 会返回 `415 Unsupported Media Type`**，
而 Go 侧**连 Content-Type 都不看**（handler 根本不碰 request body）。
这是「@RequestBody 绑定」比「畸形 JSON」更宽的伤害面 —— **Content-Type 也成了契约**。
⇒ 修法 1（整个去掉 `@RequestBody`）把两个维度一起消掉；只加「容错解析」会漏掉 415。

### 4. 守门网：`AvatarHistoryIgnoresBodyTest`（**新建 4 条 · 零容器依赖 · 4.7 秒**）

| # | 用例 | 变异后 |
|---|---|---|
| 1 | 畸形 JSON → 200 + `$.code=200` | 🔴 变 400 |
| 2 | `text/plain` + 非 JSON → 200 | 🔴 变 **415** |
| 3 | 合法 JSON 带未知字段 → 200 且 Service 被调 | ⚪ 仍绿（**不具判别力**，见下） |
| 4 | **对照组**：login 同样畸形 body 必须 400 | ⚪ 仍绿 |

⚠️ **诚实标注**：4 条里**只有 1、2 两条对本条漂移有判别力**。
第 3 条（合法 JSON 带未知字段）在绑定与否两种实现下都是 200，它防的是另一类回归
（body 里塞 `uid`/`limit` 会不会被误采纳），**不能算作本条的变异证据**。

**对照组 4 的作用**：防止「为了让 avatar/history 通过，把 body 解析整体关掉」
—— 那会把一条漂移修成九条（其余 8 个端点在 Go 侧确实 Parse）。

### 5. 为什么这层护网用 `standaloneSetup` 而不是 `@SpringBootTest`（**本轮的一个方法论收获**）

这条漂移**只关乎「Spring 会不会去解析 body」**，与鉴权/黑名单无关。
原本沿用 `UserApiClaimSemanticsParityTest` 的真 Redis 全上下文装配，
结果**本轮连撞 3 次** `ContainerLaunch Container startup failed for image redis:7-alpine`。

**真因**（`docker info` + `docker stats` 实测，非猜测）：
本机 Docker Desktop 只分配 **7.653 GiB**，而当前跑着约 30 个容器，
其中 Kafka ×3（943 + 802 + 770 MiB）、Nacos ×2（943 + 708 MiB）、
Neo4j 399 MiB、Grafana 362 MiB …… 已吃掉大半 ⇒ 小容器也分不到。

⇒ **让「绑定语义」的护网依赖一个 30 MB 容器，等于把基础设施抖动当成代码信号。**
改用 `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(真 advice)`：
仍走**真 DispatcherServlet 的参数解析 + 真 advice**（Controller 与 advice 都是真实实现，
只 mock Service），但**零容器、零 Spring 上下文、4.7 秒**。

⚠️ **本类不覆盖真 Tomcat 下 `Content-Type` 的 charset 行为** —— 那是 `UNRESOLVED-001`，
**第六轮已用 `tools/difftest/check-plaintext400-headers.sh` 在真 Tomcat 下关闭**。
**两件事不要混**：本条讲「绑不绑 body」，`UNRESOLVED-001` 讲「绑了之后响应头长什么样」。
⇒ 本类（零容器）守「绑不绑」，真容器脚本守「响应头长什么样」，**两层并存、互补**。

端到端层的那条（真 Redis + 真 MVC 全上下文）保留，两层并存：
**绑定层稳定、端到端层完整**。

### 6. 本轮回归

| 模块 | 结果 |
|---|---|
| `user-api` | **88** tests / 0 failures / 0 errors / **skipped = 0**（82 + 新 6） |
| 切片层单独跑 | 4/4，**零容器**，4.7s |

---

<!-- @fix 2026-10-23 第四轮：DRIFT-007 定性为「待签字偏离」并加护网 · DRIFT-009 执行链逐行追完 -->

## 2026-10-23 第四轮（补）：`DRIFT-007` 定性 · `DRIFT-009` 执行链核实

### 1. `DRIFT-USERAPI-007` —— ✅ **已签字保留（`SD-USERAPI-001`，2026-10-23）**，行为不改，护网已钉住

> **本条已收编进 [`../../Intentional_Deviations/users/user/api.md`](api.md)
> （`SD-USERAPI-001`）** —— 那里是「决策」，本节是「取证与论证」。审查时**先看签字档**。

**Go 出处（本轮回原文复核，全文 23 行）**：
`go-zero@v1.10.2/rest/handler/recoverhandler.go:12-23`

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

⇒ Go 侧确认：**HTTP 500 + 空 body**（连 `Content-Type` 都不是这个 handler 设的）。
RAG 侧 `GlobalExceptionHandler#handleAny:262-272` ⇒ **HTTP 500 + 完整 envelope**。
**状态码一致，body 形状不同** —— 与原台账记载一致。

**为什么不改成对齐 Go（这需要你签字）**：

| 论点 | 内容 |
|---|---|
| 原台账自己写了 | 「任何按 body 结构统一解析的客户端都会在这里**踩空**」 |
| 推论 | Go 的空 body 正是「让客户端踩空」的那一版 |
| 修法代价 | 要对齐就得让 `handleAny` **返回 null body**，同时 `GlobalResponseAdvice` 必须对该响应透传 —— 与 `DRIFT-006`（400 文本）同类改动，但这次是**主动降低**信息量 |
| 已有先例 | 项目对 `401` 的 body 形状做过同款决策并签字（`N-1`，见 §0 与 `Intentional_Deviations`） |

⚠️ **本轮没有擅自改，也没有擅自签字**：
- 代码保持原样；
- 新增 `PanicFallbackResponseShapeTest`（2 条）**把「已签字保留」的行为钉死**，
  防止它在无人察觉时漂走（这正是 §3.3「绿测试必须是错误答案的守卫」的反面用法：
  **当一个行为被有意偏离时，护网的作用从「守正确」变成「守不漂」**）。

**守门网**：

| # | 用例 | 作用 |
|---|---|---|
| 1 | 未捕获异常 → 500 + `application/json` + `{code:5001, msg:系统内部错误}` | 钉住已签字行为 |
| 2 | **对照组**：正常端点仍 200 + envelope | 防「为了让 500 更像 Go 而动 advice，把 200 路径一起改坏」—— 即 `DRIFT-006` 修 400 时踩过的形态 |

零容器依赖（`standaloneSetup` + 真 advice），**0.6 秒**。

**⇒ 用户已于 2026-10-23 拍板：签字保留 envelope**，收编为 `SD-USERAPI-001`。
（对齐 Go 的空 body 这一选项被否 —— 理由见签字档。）

### 2. `DRIFT-USERAPI-009` —— 执行链已**逐行追完**（原台账为「静态断言」，本轮升级为「完整链路 + 行号」）

**Go 侧装配链（五个文件、全部读到原文）**：

| # | 位置 | 事实 |
|---|---|---|
| 1 | `handler/routes.go:32-75` | protected 路由经 `rest.WithMiddlewares([]rest.Middleware{serverCtx.CheckBlacklistMiddleware}, routes...)` 包装；同处 `:73` `rest.WithJwt(...)` |
| 2 | `rest/server.go:221-235` `WithMiddleware` | `Handler: middleware(route.Handler)` ⇒ **黑名单包在 `route.Handler` 里面** |
| 3 | `rest/engine.go:101-113` `bindRoute` | 顺序：① `buildChainWithNativeMiddlewares` ② `appendAuthHandler` ③ `for _, mw := range ng.middlewares { chn = chn.Append(...) }` |
| 4 | `rest/engine.go:70-84` `appendAuthHandler` | `chn = chn.Append(handler.Authorize(...))` ⇒ **JWT 用 Append，不是 Prepend** |
| 5 | `user/api/usercenter.go:37` | `server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, ...))` ⇒ observability 进 `ng.middlewares` |
| 6 | `rest/chain/chain.go:81-91` `Then` | `for i := range { h = c.middlewares[len-1-i](h) }` ⇒ **索引 0 最外层、先执行** |

**⇒ Go 的最终执行序（本轮逐环推出）**：

```
[native: trace / log / recover / prometheus]
  → JWT Authorize                      ← engine.go:74 Append
    → observability (3s 预算)          ← usercenter.go:37 Append，在 JWT 之后
      → CheckBlacklist                  ← server.go:229 包在 route.Handler 里
        → route handler logic
```

**⇒ 三条断言的核实结果**：

| 原台账断言 | 核实 | 依据 |
|---|---|---|
| ① JWT 验签失败**不经过** observability ⇒ 401 不进 `markHTTPResult` | ✅ **成立** | JWT 索引在 observability **外层**，先执行且直接短路 |
| ② 3s 预算**不覆盖** JWT 验签 | ✅ **成立** | 同上 |
| （原台账未写）**黑名单在 3s 预算之内** | 🆕 **两侧相同** | Go：黑名单在 `route.Handler` 内、在 observability 内层；RAG：黑名单在 `JwtAuthInterceptor` 内、在 `HttpMiddlewareFilter` 内 |

⇒ **第 3 条修正了一个此前没人点明的等式**：黑名单那次 Redis 调用**两侧都被 3s 预算覆盖**。
这让 `UNRESOLVED-004`（Redis 慢时 Go fail-open / RAG 503）的**担忧面显著缩小** ——
两侧在同一位置做同一次 Redis 调用、都受同一份预算约束 ⇒ 不会因为「位置差」而分叉。

⚠️ **但 `UNRESOLVED-004` 仍不能就此关闭**：要判「慢到超过预算时谁先触发」，
仍需台账要求的真 Redis 故障注入（`CLIENT PAUSE` / `iptables DROP`）。
本节**只收窄了假设面，没有替代那个实验**。⇒ 保持 `unresolved`。

> ⚠️ **2026-10-24 更新**：上方这句是**第六轮时点**的判断。
> 第**九轮**已把那个实验真做掉（`CLIENT PAUSE` + 断连，双侧并行探测）
> ⇒ **`UNRESOLVED-004` 已关**，结论见本文件末尾「第九轮」一节。

**为什么 ①② 至今无法用运行证据关闭**：它们的可观察产物是 **span 属性 / access log / HTTP 指标**，
本仓没有接入任何 metrics 后端 ⇒ 无法在测试里断言。
⚠️ 按 §3.3「不得凭注解/配置断定机制生效」，本轮**不写**「①②已验证」，
只把静态链路从「断言」升级为「五个文件逐行可追的推导」。

**顺带记录**：RAG 侧 `HttpMiddleware#parseHttpEnvelope` 处已有一处
「DRIFT-009 修复：用 Jackson 替代手写 regex」的注释 —— 说明上一轮已修过该组件内
与本条同号的另一个子问题（envelope 解析），**与本条的①②无关，不要混为一谈**。

### 3. 本轮回归

| 模块 | 结果 |
|---|---|
| `user-api` | 新增 `PanicFallbackResponseShapeTest` 2 条全绿（0.6s，零容器） |

---

<!-- @fix 2026-10-23 第五轮：§9 UNRESOLVED-005 关闭（引用 rpc.md）+ 全量状态复核 -->

## 2026-10-23 第五轮：§9 `Unresolved` 表状态复核

### 1. `UNRESOLVED-005` ✅ **关闭** —— 它要的「合并判读」对象已经答完了

原文写的是：

> 「超出本组件边界（§2 strict scope）。**Required Confirmation：引用 `docs/DRIFT/users/user/rpc.md`（另一会话产出）后回填本节。**」

⇒ 这条从写下那天起就**只等一次引用**。而 `user-rpc` 台账的 §39 checklist 已经答完：

| 本组件问的 | rpc 侧的答案 | 出处 |
|---|---|---|
| delete/update/select 的事务边界 | ✅ **一致** —— 4/5 步原子性两侧相同，`[PG]` **真回滚实证**，**无 self-invocation** | `rpc.md` §39 第 9 项 |
| `extra_info` jsonb 写入 | ⚠️ 不一致（V-003，跨域待裁决） | `rpc.md` V-003 |
| `is_current` 派生字段是否实时 | ✅ 头像 current 状态机一致 | `rpc.md` §39 第 10 项 |
| `FOR UPDATE` 行锁 | ✅ **两侧都无悲观锁** ⇒ TOCTOU 窗口集合**相同**（含共有缺陷） | `rpc.md` §39 第 15 项 |

⇒ **本条关闭。** 唯一遗留的 `extra_info` 那半，已由 `V-003` 独立承载，不因本条关闭而消失。

### 2. 复核汇总（§9 五条的当前状态）

| # | 状态 | 依据 |
|---|---|---|
| `UNRESOLVED-001`（真 Tomcat 400 的 `Content-Type` 与 body 字节） | ✅ **第六轮已关** | 真 Tomcat 11.0.24 起 user-api（端口 59900），跑 `tools/difftest/check-plaintext400-headers.sh` ⇒ **EXIT=0**；Go 侧用 stdlib `http.Error` 探针补齐双侧证据。实测 `Content-Type=text/plain;charset=UTF-8` + `nosniff` + 400，body 逐字相同 ⇒ 残余只有 Content-Type 的**字面形态**（表述差异）。⚠️ 本行此前一直写「仍 open」，属台账脱节，第六轮复核更正 |
| `UNRESOLVED-002`（jjwt 选算法） | ✅ **本轮已关** | 见本文件第四轮 §1–§3（含对原断言的证伪） |
| `UNRESOLVED-003`（需与 user-rpc 合并判读的 code 集合） | ✅ **本轮已关** | 见本文件第四轮 §1–§2：枚举两侧 code 全量交叉，21 格中 20 格已一致，唯一漂移格已修 |
| `UNRESOLVED-004`（Redis 慢/断连） | ✅ **第九轮已关（2026-10-24）** | **双侧活体对拍**（真进程 + 真 PG + 真 Redis，harness `tools/difftest/exp-difftest-redis-blacklist.sh`）⇒ 修前 Go 200/1.72s vs RAG **503**/3.02s（**原假设被证实**）⇒ 已修（`DRIFT-USERAPI-014`，Lettuce `REJECT_COMMANDS`）⇒ 修后 Go 200/1.72s vs RAG 200/0.03s；「Redis 很慢」两侧**都** 503 "Request Timeout"（Go 3.003s / RAG 3.018s，完全一致）。⚠️ 本行此前写「仍 open / 需真 Redis 故障注入」，是**过期状态** |
| `UNRESOLVED-005`（rpc 事务/并发语义） | ✅ **本轮已关** | 见上 |

⇒ **api 侧：5 条 UNRESOLVED 全部已关**（001/005 真 Tomcat、002/003 第四轮、004 第九轮）。
⇒ ⚠️ 上表「`UNRESOLVED-001` 仍 open」是**第五轮时点的状态**，第六轮已关（本表已更正）。
⇒ ⚠️ **UNRESOLVED 清空 ≠ 组件已闭环**：仍有 2 条未签字的 OBSERVABLE DIFFERENCE
（`DRIFT-USERAPI-009②` / `DRIFT-USERAPI-013`）⇒ 结论仍是 `SEMANTICALLY NOT EQUIVALENT`。

### 3. ⚠️ 复核中再次确认的文档病灶

`rpc.md` 台账里 **`UserRpcLoginNullStatusFailClosedTest` 这个类名在仓内不存在**
（实际是 `UserRpcLoginNullStatusReadsAsZeroTest`，8 条不是 5 条）。
`api.md` 的 §9 `UNRESOLVED-001` 曾以它作为「关闭条件」的指引 ⇒ **该指引当前指向一个不存在的类**，
按它去复核的人会直接扑空。已在 `rpc.md` 第五轮更正。

---

<!-- @fix 2026-10-24 第六轮：API-006 已修（真 Tomcat 双侧实证）· API-004 证伪关闭 · API-009 已签字 -->

## 2026-10-24 第六轮：API-006 修复 · API-004 证伪 · API-009 签字

| 项 | 结果 |
|---|---|
| **DRIFT-USERAPI-006** | ✅ **已修**（`c79d113`）—— 400 响应 Content-Type 补 charset + body 按 UTF-8 写出 |
| **DRIFT-USERAPI-004** | ❌ **证伪关闭**（`330bafb`）—— Go 的 user-api 其实**只**吃 JSON body，RAG 已等价 |
| **DRIFT-USERAPI-009** | ✅ **已签字**（`SD-USERAPI-002`）—— 接受「RAG 的观测面更大」 |
| **DRIFT-USERSCOMMON-005** | ✅ **已签字**（`SD-USERSCOMMON-001`）—— 保留 bcrypt 的 fail-closed |

### 变异验证（能编译 + 测试变红，非构建红）

| # | 变异 | 结果 |
|---|---|---|
| **M4** | `GlobalExceptionHandler` 去掉 charset（退回修法之前） | 7 tests / **2 failures**：`expected: <UTF-8> but was: <null>` |
| **M5** | 给 `login` 加 `@RequestParam` query 绑定（注入点在必填校验**之后**） | 5 tests / **1 failure**：`expected: <fromJson> but was: <fromQuery>` |
| **M5b** | 同上，但注入点在必填校验**之前**（更贴近真实写法） | 5 tests / **3 failures**，含 `queryParamsAreNotBound` 的 `expected: <400> but was: <500>` |

⚠️ **M5 与 M5b 的差别值得记一笔**：M5 只被「JSON 赢」那条抓住，
`queryParamsAreNotBound` **没红** —— 因为 query 值在 `GoStyleRequest.normalize`
（内含必填校验）**之后**注入，必填异常先抛 ⇒ 变异「看起来没生效」。
⇒ **变异的位置必须贴近真实写法，否则会得出「这条护网没用」的错误结论。**
M5b 把注入点前移后，`queryParamsAreNotBound` 立刻变红 ⇒ 该护网**确实覆盖**了这个场景。

### 本轮的一条方法论教训

**API-004 的原断言是纯静态源码阅读得出的，从未跑过活体**，
而它读漏的是**紧挨着的那道门**（`usingDifferentKeys`）——
一处「A 说的对、但 A 的前提被 B 否掉」的典型。
⇒ 印证 AGENTS.md §3.3：**「配置/注解存在」与「机制生效」之间隔着一次运行**，
**源码阅读得出的「机制会怎样」同样可能是错的**。
本轮把它变成活体结论的路径是：`go run -overlay` 虚拟注入探针 +
真源自己的 `types.LoginReq` + go-zero 自己的 `httpx.Parse`。

### 回归

users-common · user-rpc · user-api **96**（+6：5 条绑定面 + 1 条 UTF-8 前提守门），
全部 **0 failures / 0 errors**。

## 2026-10-24 第七轮：客户端熔断接线测试落地 ⇒ **抓出一条真漂移**（`DRIFT-USERAPI-011`）

| 项 | 结果 |
|---|---|
| **DRIFT-USERAPI-011** | ✅ **已修** —— user-api **缺** `@ExceptionHandler(GoStyleCircuitOpenException.class)`：客户端熔断打开时 RAG 返回 **HTTP 500 + 业务码 5001**，Go 应为 **HTTP 200 + 业务码 1015** |
| **DRIFT-USERAPI-008 ②** | ✅ **已闭环** —— user-api 补齐 `GoStyleGrpcClientBreakerWiringTest`（接线层）+ `GoStyleGrpcClientBreakerE2ETest`（端到端层） |
| **DRIFT-USERAPI-008 ③** | 🔶 **部分推进** —— 「客户端熔断」「入口 Breaker」两条已逐条对照（见 §5）；MaxConns / MaxBytes / CpuShedding 三条**仍待办** |

### 1. 起因：一条**从未被测过**的机制，藏着一个**二阶**漂移

`DRIFT-USERAPI-008` 的第 2 个真问题记的是「user-api 的 gRPC client breaker 是否真的被装配，未验证」。
本轮补测试 ⇒ **首跑就红** —— 但红的**不是「装配」**（装配本来就是好的），
而是**装配之后的错误映射**：熔断异常被兜底分支吃成了 **HTTP 500 + 业务码 5001**，
再被**入口**侧熔断放大成 **HTTP 503 + 空 body**。

⇒ **这正是 §3.3 要防的形态**：一条「配了、注解在、类注释写得很全」的机制，
**没有任何一行代码断言它的错误语义** ⇒ 它可以错得悄无声息。
⇒ 也再次印证 **§3.3.1**：读到「拦截器 @GrpcGlobalClientInterceptor + @Component」
**不能推出**「它的异常会被正确映射」—— 那是**另一段代码**的事。

### 2. Go 侧事实（三段装配 + 错误码路径，全部 `文件:行号`）

**① 这层在 Go 侧是开着的**（三段缺一不可）：

| 段 | 出处 | 内容 |
|---|---|---|
| 默认开启 | `zrpc/internal/config.go:14` | `Breaker bool \`json:",default=true"\`` |
| 装配 | `zrpc/internal/client.go:112-113` | `if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }` |
| 实现 | `zrpc/internal/clientinterceptors/breakerinterceptor.go:13-19` | `breaker.DoWithAcceptableCtx(ctx, breakerName, fn, codes.Acceptable)` |

**② 熔断打开时返回的是「普通 error」，不是 gRPC status**：

| 出处 | 内容 |
|---|---|
| `core/breaker/breaker.go:18-19` | `var ErrServiceUnavailable = errors.New("circuit breaker is open")` |
| `core/breaker/googlebreaker.go:73-75` | `if b.proba.TrueOnProba(dropRatio) { return ErrServiceUnavailable }` |
| `core/breaker/breaker.go:155-163` | `DoWithAcceptableCtx` → `DoWithAcceptable` → `throttle.doReq(req, nil, acceptable)`（**fallback = nil ⇒ 原样返回该 error**） |

**③ 业务码落点 = `default` ⇒ 1015**（本域 **9 个 logic 全部如此**）：

| 出处 | 内容 |
|---|---|
| `service/user/user/api/internal/logic/user/loginlogic.go:46-56` | `st, _ := status.FromError(err)` → `case codes.Internal: 5001` / `default: errmsg.CodeServerBusy` |
| 同上（其余 8 个 logic） | `selectavatar / getuser / logout / uploadavatar / deleteuser / updateuser / getavatarhistory / register` —— **逐个核对，无一个显式列 `codes.Unknown`** ⇒ 一律落 `default` |
| `service/user/common/errmsg/errmsg.go:6` | `CodeServerBusy = 1015` |
| `service/user/common/errmsg/errmsg.go:41` | `CodeServerBusy: "服务繁忙"` |

⚠️ `status.FromError` 对**非 status 错误**返回 `codes.Unknown` ⇒ `Unknown` 不在任何 `case` 里
⇒ **落 `default` ⇒ 1015**。

**④ HTTP 状态码恒 200**：

| 出处 | 内容 |
|---|---|
| `service/user/user/api/internal/handler/user/loginhandler.go` | `httpx.OkJson(w, &response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp})` —— **`OkJson`** ⇒ 业务码多少都是 **HTTP 200 + envelope** |

⇒ **Go 的完整对外形状：HTTP 200 + `{"code":1015,"msg":"服务繁忙","data":null}`**。

### 3. RAG 侧事实：它为什么掉进了兜底分支

| 位置 | 内容 |
|---|---|
| `service/common/.../protection/GoStyleGrpcClientBreakerInterceptor.java:200-206` | 拒绝时 `throw new GoStyleCircuitOpenException("circuit breaker is open")` —— **刻意不是 `StatusRuntimeException`**（否则 msg 会变成 `"circuit breaker is open"`，与 Go 的 `"服务繁忙"` 不同） |
| `service/users/user/user-api/.../service/UserLoginService.java`（修前） | `catch (StatusRuntimeException e)` —— **只捕 gRPC status** ⇒ 熔断异常**穿透** |
| `service/users/user/user-api/.../advice/GlobalExceptionHandler.java`（修前） | **没有** `@ExceptionHandler(GoStyleCircuitOpenException.class)` ⇒ 落 `handleAny` ⇒ **HTTP 500 + 业务码 5001** |

⇒ **与 Go 的差**：业务码 **5001 vs 1015**、HTTP **500 vs 200**。

> 📌 同仓另外 4 个域**早就有**这一支：`comment-api` / `follow-api` / `message-api` / `task-api`
> 全部有 `@ExceptionHandler(GoStyleCircuitOpenException.class)`。
> ⇒ **user-api 是唯一的漏网** ⇒ 这条漂移是**漏抄**出来的，不是设计分歧。

### 4. 二阶后果：HTTP 500 把**入口**熔断也喂开了

`GoStyleBreakerFilter` 的成败判据是 `capturedStatus < 500`（对齐 Go `breakerhandler.go:33`）：

| 步骤 | 发生什么 |
|---|---|
| ① 客户端熔断打开 | 抛 `GoStyleCircuitOpenException` → `handleAny` → **HTTP 500** |
| ② 入口 breaker 记失败 | `capturedStatus = 500` ⇒ `>= 500` ⇒ `markFailure()`（Go 侧**不会**：Go 的业务错误是 HTTP 200） |
| ③ 入口 breaker 打开 | ⇒ `response.setStatus(503)` + **空 body** |

⇒ 对外最终形态变成 **HTTP 503 + 空 body**（连 envelope 都没有），
与 Go 的 **200 + 1015** 在 wire 上**完全不同**。

🔴 **这是本条漂移在首跑 E2E 里的现场证据**：首跑的实际响应就是
`expected: <200> but was: <503>`、`实际 body = `（空）。
⚠️ 若只读代码、只查「熔断异常映射」，**会误判成「入口熔断提前短路了」**——
真相是「入口熔断是被 500 喂开的」。**顺序不同 ⇒ 修法不同。**

### 5. 修法（三处，缺一不可）

| # | 文件 | 改动 | 依据 |
|---|---|---|---|
| **1** | `user-api/advice/GlobalExceptionHandler.java` | 新增 `@ExceptionHandler(GoStyleCircuitOpenException.class)` ⇒ **HTTP 200 + envelope(1015, "服务繁忙")** | Go `loginlogic.go:53-54` 的 `default` 分支 + `loginhandler.go` 的 `OkJson` |
| **2** | `user-api/service/UserLoginService.java` | 新增 `catch (GoStyleCircuitOpenException e)` ⇒ **计数 `loginRpcError` + rethrow** | Go `loginlogic.go:46-48` 的 `err != nil` **捕获一切** ⇒ 熔断错误也算 `rpc_error` |
| **3** | `user-api/service/UserRegisterService.java` | 同上（`registerRpcError`） | Go `registerlogic.go:45-47` |

⚠️ **为什么 1 和 2/3 都要做**：
- 只做 1 ⇒ 业务码对了，但 **Go 会递增的 `rpc_error` 计数器在 RAG 静默不增** ⇒ 监控看板失真；
- 只做 2/3 ⇒ 指标对了，但**业务码仍是 5001**（异常仍落兜底分支）。
⇒ 2/3 只负责**计数**，业务码映射统一由 1 负责（与 `UserRegisterService` 既有写法
「catch → 计数 → rethrow → advice 映射」完全同款）。

### 6. 守网（两条测试，**新建**）

| 测试 | 条数 | 断言什么 |
|---|---|---|
| `GoStyleGrpcClientBreakerWiringTest` | **2** | ① 拦截器出现在 starter 的 `GlobalClientInterceptorRegistry` 里（**不是**「Bean 存在」）② 默认配置下熔断是**开启**的 |
| `GoStyleGrpcClientBreakerE2ETest` | **1**（含 4 段断言） | 真 in-process gRPC server + 真 channel + 真拦截器：① 首次调用落到下游 ⇒ **5001 + HTTP 200** ② 熔断打开后**下游不再被触达**（计数增长 40 → ≤2）③ 熔断打开时**业务码 1015 + HTTP 200** ④ 熔断打开时 **`rpc_error` 计数递增** |

> 📌 **② 用「下游触达次数」而不是「业务码」当判据**，是为了与「错误码怎么映射」**正交** ——
> 即使有人改了错误码映射，②仍然有效；③才负责盯错误码。
> 📌 **③ 为什么不能「随手发一个请求断言 1015」**：Go 的 googlebreaker 有 **force-pass** 保底，
> 熔断打开期间**每约 1s 会放一个请求真的打到下游**，那个请求拿到的是 5001 ⇒ 那样写是 flaky。
> 详见 §8。

### 7. 变异验证（能编译 + 测试变红，非构建红）

| # | 变异 | 结果 |
|---|---|---|
| **M6** | 删掉 `handleCircuitOpen` 整个方法（退回修法之前） | 🔴 **RED** —— `Tests run: 1, Failures: 1`：`熔断打开后每个请求都必须是 Go 的 1015…`，`下游总调用次数=41`（说明熔断确实打开了，只是**没有 1015**） |
| **M7** | `handleCircuitOpen` 的业务码 1015 → 5001（**只**错码值，状态码仍 200） | 🔴 **RED** —— 同上断言变红 ⇒ **码值这一半有独立护网**（不是靠「整体挂掉」才红） |
| **M8** | `handleCircuitOpen` 的 HTTP 状态 200 → 500（**只**错状态码，业务码仍 1015） | 🔴 **RED** —— 挂在**状态码**那条断言上：`实际 body = {"code":1015…}` ⇒ **状态码这一半也有独立护网** |
| **M9** | 删掉 `UserLoginService` 的 `catch (GoStyleCircuitOpenException)`（指标不再递增） | 🔴 **RED** —— 挂在**指标**那条断言上：`实际增量=0.0` ⇒ **指标这一半有独立护网** |

⚠️ **M7 / M8 的意义**：它们分别只错**一半**，却各自被**不同的断言**抓住
⇒ 证明这条护网**不是「要么全对要么全挂」的粗网**，而是**三条独立语义各有一道网**：
业务码 / HTTP 状态码 / 指标计数。
⚠️ **M6 的 `下游总调用次数=41`** 是一条**反向证据**：
熔断**确实打开**了（41 次里只触达了 41 次=全部失败后被短路），
红的原因**只**是错误映射 —— 这把「装配没生效」与「映射错了」两种可能**区分开**了。
（§3.3.1：读源码得出的「机制会怎样」可能是错的；这次是**运行数据**把它劈开的。）

### 8. 附带结论：Go 的 `googlebreaker` 与 RAG 的 `GoStyleBreaker` **逐行一致**

这是核对 force-pass 时的**意外收获**，写下来是因为它**极易被「顺手改好」**。

`core/breaker/googlebreaker.go:53-80` 与 RAG `GoStyleBreaker.accept()` 的判定顺序**逐条一致**：

```go
dropRatio := (float64(history.total-protection) - weightedAccepts) / float64(history.total+1)
if dropRatio <= 0 { return nil }          // ← 早退，**不更新 lastPass**
lastPass := b.lastPass.Load()
if lastPass > 0 && timex.Since(lastPass) > forcePassDuration { b.lastPass.Set(now); return nil }
dropRatio *= float64(buckets-history.workingBuckets) / buckets
if b.proba.TrueOnProba(dropRatio) { return ErrServiceUnavailable }
b.lastPass.Set(timex.Now())
```

⇒ **force-pass 分支要求 `lastPass > 0`，而 `lastPass` 只在「概率<i>没</i>命中」那条路上被更新**
⇒ 在**随机源被钉死**（本测试钉成 `0.0`，以保证拒绝是确定性的）的场景下，
**force-pass 永不触发** ⇒ 熔断打开的形态是「下游一次都不被触达」，
**不是**「每秒一个探测请求」。

⚠️ **为什么必须记下来**：
1. 我这次测试的**首版判据就是按「每秒一个探测请求」写的** ⇒ 拿到一个**假红**
   （「未能观测到 force-pass」）⇒ 若不是回 Go 源码逐行核对，
   极可能把「测试写错」误判成「实现有 bug」，或者反过来去「修」一个 Go 根本没这么做的行为。
2. 这条一旦被「顺手改好」（例如把早退也更新 `lastPass`），熔断的**恢复行为**就变了
   （从「10s 窗口老化」变成「1s force-pass 恢复」）—— 那是**新增行为**，不是对齐。

### 9. 回归

users-common **33** · user-rpc **122** · user-api **99**（+3：2 条接线 + 1 条端到端），
全部 **0 failures / 0 errors**，`BUILD SUCCESS`。

⚠️ **本轮新增的 3 条测试里有 2 条是「接线层」** ——
它们证明的是「拦截器进了 starter 的 registry」与「默认开关是开的」，
**不证明**任何行为；行为由那条端到端测试负责。
⇒ 分层写清楚，避免「有测试」被读成「测过了」。

## 2026-10-24 第八轮：四个过载保护机制逐条 Go ↔ RAG 对照（`DRIFT-USERAPI-008 ③`）

| 项 | 结果 |
|---|---|
| **DRIFT-USERAPI-008 ③** | ✅ **逐条对照完成** —— 四个机制 × 8~11 个维度共 **40 格**：**37 格 EQUIVALENT**、**1 格已修**、**2 格待决策** |
| **DRIFT-USERAPI-012**（新） | ✅ **已修** —— 降载的 **RT 统计口径**：Go 是「**浮点**毫秒 + `math.Ceil`」，RAG 是「**整型截断** + 至少记 1」⇒ 系统性少记约 1ms ⇒ `minRt` 偏小 ⇒ **在途上限偏低 ⇒ RAG 比 Go 更早降载** |
| **DRIFT-USERAPI-013**（新） | 🔴 **待决策** —— ① MaxConns 闸门粒度（Go **per-route** vs RAG **全局一个**）② breaker 按**请求 URI** 建 key ⇒ **未匹配路径也会建** ⇒ 键空间**无界**（Go 只在**路由注册时**建 ⇒ 有界） |

### 1. 对照的**范围**与**证据强度**

| 项 | 值 |
|---|---|
| 对照面 | `service/common/src/main/java/com/rag/common/protection/`（4 个 Filter + 2 个算法类 + 配置类） |
| Go 对照面 | go-zero v1.10.2：`rest/config.go`、`rest/engine.go`、`rest/handler/{maxconnshandler,breakerhandler,sheddinghandler,maxbyteshandler}.go`、`core/breaker/{googlebreaker,breaker}.go`、`core/load/adaptiveshedder.go`、`core/collection/rollingwindow.go`、`zrpc/internal/{config,client}.go`、`zrpc/internal/clientinterceptors/breakerinterceptor.go`、`zrpc/internal/codes/accept.go`、`core/stat/{metrics,usage}.go` |
| 证据强度 | ✅ **源码逐行对照**（两侧全部读原文，非凭记忆）+ 🆕 **运行时证据**（Shedding 层本轮首次拿到） |
| ⚠️ **证据边界** | 本轮**没有**做「双侧活体压测对拍」—— 四个机制都是**过载路径**，真源那侧要复现同一条过载曲线需要独占 CPU；⇒ 本轮结论的性质是「**源码逐行等价性**」，不是「同输入同输出」的实测 |

### 2. 逐条对照 · MaxConns

| 维度 | Go | RAG | 判定 |
|---|---|---|---|
| 阈值来源 | `rest/config.go:52` `MaxConns int json:",default=10000"` | `GoStyleProtectionProperties.maxConns = 10000` | ✅ |
| yaml 是否显式配置 | ❌ 未写（走 tag 默认） | ❌ 未写 | ✅ |
| **闸门粒度** | **per-route**：`engine.go` 的 `buildChainWithNativeMiddlewares` **逐路由**调用 ⇒ 每条路由各 new 一个 `syncx.NewLimit(n)` | **全局一个** `Semaphore` | ❌ **DRIFT-013 ①** |
| 获取方式 | `latch.TryBorrow()`（非阻塞） | `latch.tryAcquire()`（非阻塞） | ✅ |
| 归还 | `defer latch.Return()` | `finally latch.release()` | ✅ |
| `<=0` 语义 | no-op（`maxconnshandler.go:13-17`） | no-op（`latch == null`） | ✅ |
| 拒绝形状 | `w.WriteHeader(503)` ⇒ **503 空 body、无 Content-Type** | `setStatus(503)` ⇒ 同 | ✅（已实测） |
| 拒绝时是否触达下游 | 否 | 否 | ✅ |

### 3. 逐条对照 · MaxBytes

| 维度 | Go | RAG | 判定 |
|---|---|---|---|
| 阈值来源 | `rest/config.go:53` `MaxBytes int64 json:",default=1048576"` | `maxBytes = 1048576L` | ✅ |
| 判据 | `r.ContentLength > n`（`maxbyteshandler.go:19`） | `getContentLengthLong() > maxBytes` | ✅ |
| **chunked 绕过** | `ContentLength == -1` ⇒ `-1 > n` 恒 false ⇒ **放行** | 同样 `-1` ⇒ **放行** | ✅（**刻意不修**：修正它＝新增行为＝漂移） |
| `<=0` 语义 | no-op | no-op | ✅ |
| 拒绝形状 | `w.WriteHeader(413)` ⇒ **413 空 body** | `setStatus(413)` ⇒ 同 | ✅ |
| 相对鉴权的次序 | native middleware ⇒ **在 JWT 之外**（413 压过 401） | Filter ⊃ Interceptor ⇒ 同 | ✅ |
| 运行时证据 | ⚠️ 静态 | ⚠️ 静态 —— ⚠️ **2026-10-24 复核更正**：本行原写「真容器由 `check-protection-layer.sh` 守」，**该脚本在仓内不存在**（复核脚本实测）⇒ **MaxBytes 在 `service/common` 没有专用测试类**（`GoStyleMaxBytesFilterTest` 不存在）；仅被 5 个**别的域**的 MVC 测试间接断言 413（`MessageApiProtectionMvcTest` / `MessageApiProtectionRealTomcatTest` / `LikeApiProtectionMvcTest` / `ArticleApiRouteAuthBoundaryTest` / `TaskApiWiringTest`）。⚠️ **user-api 侧自身零守网** | ⚠️ |

### 4. 逐条对照 · Breaker（入口）

| 维度 | Go | RAG | 判定 |
|---|---|---|---|
| 隔离单位 | **per-route**，`breakerhandler.go:19` 名字 = `method://path` | **per-route**，`method://URI` | ✅（粒度同；键的**取值**不同见下） |
| **键的取值** | `route.Path` = **去掉组前缀**的路径（如 `user/login`） | `request.getRequestURI()` = **完整 URI**（如 `/usercenter/v1/user/login`） | ⚠️ 本域 9 条路由**无重名** ⇒ 等价；若将来两个前缀下出现**同名子路径**，Go 会**共用**一个 breaker 而 RAG 会**各建**一个 |
| **算法** | `core/breaker/googlebreaker.go:53-80` | `GoStyleBreaker.accept()` | ✅ **逐行一致**（含「`dropRatio <= 0` 的早退**不**更新 `lastPass`」这个极易被顺手改好的细节） |
| 常量 | window 10s / 40 桶（250ms）/ k 1.5 / minK 1.1 / protection 5 / forcePass 1s | 同 | ✅ |
| 拒绝形状 | `w.WriteHeader(503)` ⇒ 503 空 body | `setStatus(503)` ⇒ 同 | ✅ |
| 成败判据 | `cw.Code < 500` ⇒ `promise.Accept()` | `capturedStatus < 500` ⇒ `markSuccess()` | ✅ |
| 窗口 | `IgnoreCurrentBucket`（忽略当前桶） | `completedSlots()` 跳过 `currentSlot` | ✅ |
| **键空间** | **有界**：只在**路由注册时**建 breaker（`engine.go`） | **无界**：每个**请求 URI** 都 `computeIfAbsent` 建一个 ⇒ **未匹配路径（404）也会建** | ❌ **DRIFT-013 ②** |
| 运行时证据 | ⚠️ 静态 | ⚠️ 静态（算法本身有 `GoStyleBreakerTest` 8 条） | ⚠️ |

### 5. 逐条对照 · CpuShedding

| 维度 | Go | RAG | 判定 |
|---|---|---|---|
| 共享范围 | **全局一个**（`engine.go` 的 `newEngine` 里建一个 shedder，`getShedder` 直接取它） | 全局一个 | ✅ |
| 阈值 | `rest/config.go:56` `CpuThreshold default=900,range=[0:1000)` | `cpuThreshold = 900`，越界夹到 `[0,1000)` | ✅（越界处理是表达层加固：Go 在**配置加载期**报错拒绝启动，Java 不夹会变成「永不降载」的静默失效） |
| CPU 口径 | **整机**（`stat.CpuUsage()` ← cgroup / `/proc/stat`） | `getSystemCpuLoad()` 整机 | ✅ |
| 读不到 CPU | `CpuUsage()` 返回 0 ⇒ `0 >= 900` false ⇒ **不过载** | `-1` ⇒ fail-open | ✅ 同向 |
| 拒绝条件 | `systemOverloaded() \|\| stillHot()` **且** `highThru()` | 同 | ✅ |
| `highThru` | `avgFlying > cap && flying > cap` | 同 | ✅ |
| `maxFlight` | `max(maxPass * minRt * windowScale, 1)`，`windowScale = 1s/100ms/1000 = 0.01` | 同 | ✅ |
| `overloadFactor` | `clamp((1000-cpu)/(1000-threshold), 0.1, 1)` | 同 | ✅ |
| `promise.Fail` 判据 | `cw.Code == 503` ⇒ `Fail()` | `capturedStatus == 503` ⇒ `fail()` | ✅（Go 看 `==503`、breaker 看 `<500`，两处**刻意不同**，都照搬了） |
| **`Fail` 不记 RT/QPS** | `promise.Fail()` 只 `addFlying(-1)` | 同 | ✅ |
| **RT 统计口径** | `rt := float64(timex.Since(p.start)) / float64(time.Millisecond)` ⇒ **`math.Ceil`（向上取整）** | **整型截断**（`/ 1_000_000L`）+ `Math.max(1L, ·)` | ❌ **DRIFT-012**（已修） |
| 运行时证据 | ⚠️ 静态 | ✅ **本轮首次**（`GoStyleCpuSheddingFilterTest`：过载 ⇒ **503 空 body** + 不触达下游） | ✅ |

### 6. `DRIFT-USERAPI-012`（已修）：降载的 RT 统计口径

**Go 侧**（`core/load/adaptiveshedder.go:284-289`）：

```go
func (p *promise) Pass() {
	rt := float64(timex.Since(p.start)) / float64(time.Millisecond)   // ← float64 毫秒
	p.shedder.addFlying(-1)
	p.shedder.rtCounter.Add(int64(math.Ceil(rt)))                    // ← 向上取整
	p.shedder.passCounter.Add(1)
}
```

**RAG 侧（修前）**：

```java
long rtMs = (System.nanoTime() - startNanos) / 1_000_000L;   // ← 整型截断
...
rtSlots[currentSlot][0] += Math.max(1L, (long) Math.ceil(rtMs));   // ← 至少记 1
```

⇒ **系统性少记最多 1ms** ⇒ `minRt()` 偏小 ⇒
`maxFlight = maxPass * minRt * 0.01` 偏小 ⇒ **在途上限偏低** ⇒
**RAG 比 Go 更早降载**（在同一份真实负载下，RAG 会比 Go 多丢一批请求）。

⚠️ **为什么这个差别很容易被漏掉**：它**不改变任何一次请求的输出**，
只改变「在什么时候开始降载」—— 属于**纯行为**差异，且**只在过载路径上可见**。
⇒ 与 `DRIFT-USERAPI-008` 原先那三条（MaxConns 粒度 / 接线未验 / 未逐条对照）是**同一族的盲区**。

**修法（两处，缺一不可）**：

| # | 位置 | 改动 |
|---|---|---|
| 1 | `GoStyleCpuSheddingFilter.elapsedRtMs(startNanos, endNanos)` | 整型除法 → **浮点**除法 `/ 1_000_000.0D` |
| 2 | `GoStyleAdaptiveShedder.pass(double rtMs)` | `Math.max(1L, ceil(rt))` → **`(long) Math.ceil(rt)`**（**无下限**：Go 的 `Add(0)` 是合法的 —— `Count` 照样 +1，只是 `Sum` 不加） |

⚠️ **第 2 处的「去掉下限」不是笔误**：Go 侧 `Bucket.Add(v)` 是
`Sum += v; Count++` ⇒ `v == 0` 时 **`Sum` 不动、`Count` 照加**。
RAG 原先的 `Math.max(1L, ·)` 是在**替整型截断擦屁股**，掩盖了第 1 处的根因；
⇒ 修第 1 处（浮点）+ 去掉下限，才是**逐字**对齐。

**守网（新建 4 条）**：

| 测试 | 条数 | 断言 |
|---|---|---|
| `GoStyleAdaptiveShedderTest`（扩） | **+3** | `pass(1.2)` ⇒ 记 **2**（不是整型截断的 1）；`pass(0.0)` ⇒ 记 **0**（不是「至少记 1」）；`pass(2.0)` ⇒ 记 **2** |
| `GoStyleCpuSheddingFilterTest`（新建） | **2** | ① `elapsedRtMs` 是**浮点**毫秒（`1.2ms` 原样保留、`0.999999ms` 不被截断成 0）② **过载 ⇒ 裸 503 空 body + 不触达下游** |

⚠️ **为什么需要 `recordedRtSum()` 这个测试接缝**：
`minRt()` 只统计**已完成**的桶（Go 的 `IgnoreCurrentBucket` 同语义），
所以「**刚**记进去的 RT」**当场读不出来**：
断言它要么靠 sleep 等桶翻页（CI 上可能不稳），
要么只能经 `minRt()` 间接反推 —— 而**恰恰是「0ms 记 0 还是记 1」这种差别会被反推吃掉**
（`minRt` 取的是桶内**平均**）。
⇒ 加一个只读接缝，把口径**逐条**钉死。

### 7. `DRIFT-USERAPI-013`（待决策）：per-route 闸门 + 键空间

**① MaxConns 闸门粒度**：Go 是 **per-route**，RAG 是**全局一个**。

⚠️ **原 javadoc 的论证有一处自相矛盾**（值得记一笔）：
它说「两种口径的差别只在单个端点打满 10000 且另一端点也接近 10000 时才显现，
而那时 Tomcat 的 `max-threads=200` 早已先成为瓶颈」——
**前半句是对的，后半句与「保留本层的理由」冲突**：
本层存在的理由恰恰是「运维把 `max-threads` 调到万级做压测时」，
**在那正是两种口径分道扬镳的场景**：
Go 允许**每路由** 10000（9 条路由 ⇒ 合计 90000），RAG 只允许**全局** 10000。

**② breaker 的键空间无界**：`GoStyleBreakerFilter.breakerFor(request)` 对
**每一个请求 URI** 都 `computeIfAbsent` 建一个 breaker ——
包括**未匹配的路径**（Filter 跑在 `DispatcherServlet` **外面**，此时还不知道路由匹不匹配）⇒
一个 404 扫描器就能让这个 map **无界增长**（每个 breaker 含 40 个 Bucket ≈ 1.6 KB）。
Go 侧 `breaker.NewBreaker` 在**路由注册时**调用 ⇒ **有界**。

⚠️ **这两条是同一件事的两面**：正因为「按请求 URI 建键」不可行（②），
把 MaxConns 也改成 per-route（①）就会**再造一个无界 map**。

⇒ **因此本轮不擅自改**，需要一次设计决策。三个候选：

| 方案 | 做法 | 代价 / 风险 |
|---|---|---|
| **A. 键空间有界化** | 给 breaker/latch 的 map 设**上限**（如 4096），超限后回落到一个**共享的溢出 breaker** | 引入「溢出」这一新状态（Go 没有）；但**最省事**、不改现有键的语义 |
| **B. 只为已注册路由建键** | 从 Spring 的 `HandlerMapping` 取**匹配后的路由模板**当键（而不是原始 URI） | Filter 在 DispatcherServlet 外面 ⇒ 要提前跑一次映射（**每请求两次匹配**）；有副作用风险 |
| **C. 两条都签成有意偏离** | 承认「RAG 的过载闸门比 Go 更严」并签字 | 需要用户签字；且**键空间无界**这一条**不宜签字**（它是健壮性问题，不是口径分歧） |

⇒ **建议：A（键空间有界化）+ MaxConns 改 per-route**，但这是**共享层（影响全部 10 个 api 服务）**的设计变更 ⇒ 交由用户拍板。

#### 7.x-2 2026-10-26 只读复核：**键语义钉死** + **可观察面收窄**（本轮不实施）

> **触发**：Jason 指示「继续」⇒ 把本域**最后一条真正跨域**的 `DRIFT-USERAPI-013` 也做一次只读复核，
> 好让它的拍板同样有**逐字证据**。**结论：本条的前提全部成立，且比原记录更精确；修法落点确认在 `service/common`（global 单写权）⇒ 本域只报告不动代码。**

**① 两侧的 key 到底由什么拼成（逐字）**

| | key 的拼法 | 语义 | 基数 |
|---|---|---|---|
| **Go** | `rest/engine.go:149` → `handler.BreakerHandler(route.Method, route.Path, metrics)` ⇒ `rest/handler/breakerhandler.go:19` `breaker.NewBreaker(breaker.WithName(strings.Join([]string{method, path}, breakerSeparator)))`，`breakerSeparator = "://"`（`:15`） | `route.Path` = **注册期路由模板**（如 `/user/avatar/history/:id`）⇒ **同一路由的所有请求共用一个 breaker** | **= 路由数**（有界）|
| **RAG** | `GoStyleBreakerFilter.routeKey` → `request.getMethod() + "://" + request.getRequestURI()` | `getRequestURI()` = **原始请求 URI（含具体参数值）** ⇒ **每个不同的取值一个 breaker** | **= 参数取值数**（无界）|

⇒ **分隔符两侧一致（都是 `://`）**；**差别只在「path 那一段是模板还是原值」**。
⇒ ⚠️ 台账原写「Go 侧 `breaker.NewBreaker` 在**路由注册时**调用 ⇒ **有界**」—— 方向正确，**但没写清「注册时传的是 route.Path（模板）」** ⇒ 这才是两侧的真正语义差。

**② RAG 侧那条 javadoc 是**错误的安全保证**（PIT-USERS-SHARED-017 形态）**

`GoStyleBreakerFilter` 的类注释原文写着：
> 「**为什么 breaker 实例不做无界缓存**：key 由 method+path 构成，基数等于路由数（本模块 13 条，含未匹配路径不会建 key），**无界增长的现实风险为零**」

⇒ **三处都与实现矛盾**：① key 里的 path 是 **raw URI**、不是路由模板 ⇒ 基数 ≠ 路由数；
② 「未匹配路径不会建 key」**错** —— Filter 跑在 `DispatcherServlet` **外面**，此时还不知道路由匹不匹配 ⇒ **未匹配路径照样建 key**；
③ 「风险为零」因此**不成立**。
⇒ 🔴 ⇒ 修法落地时**必须一并改掉这条注释**（否则下一个读代码的人会拿它当依据）—— 它在 `service/common` ⇒ **global 域一并改**。

**③ 可观察面收窄（这条的「跨域」形态比原记录更精确）**

- **user-api 的 9 条路由**（`UserController.java`：`/register` `/login` `/logout` `/get` `/update` `/delete` `/avatar/select` `/avatar` `/avatar/history`）⇒ **全是固定路径，零路径变量**
  ⇒ ⇒ 在 user-api 上 **raw URI ≡ route pattern** ⇒ ⇒ **013② 在本组件上根本不可观察**（这也解释了它为何一直只是「登记」而非「实测」）；
- 全仓 10 个 api 服务里**只有 `article-api` 有路径变量**（4 处，如 `@GetMapping("/article/{id}")`）
  ⇒ ⇒ **013② 的可观察面在 article 域**，而**修法落点在共享层 `service/common`**（影响全部 10 个 api）
  ⇒ ⇒ **三重跨域**：跨域（users → article）· 跨微服务（落点 `service/common`）· **共享层单写权（只有 global 能写）**。

**④ gRPC 客户端熔断那一侧：两侧是对齐的**（别把它混进来）

RAG `GoStyleGrpcClientBreakerInterceptor` 的键 = **fullMethodName**（`breakers.computeIfAbsent(fullMethodName, …)`）；
Go 侧 `zrpc/internal/clientinterceptors/breakerinterceptor.go` 的键 = `path.Join(cc.Target(), method)` ⇒ **同样是「per 目标 + per 方法」**。
⇒ ⇒ **013② 的问题只在 HTTP 层**（§8「第八轮」表里那行「Go 侧无对应物」说的是**Go 的 user-api 没有客户端熔断**，是另一件事，别混谈）。

**⑤ 修法边界（给 global 落地时的一条硬约束）**

若采纳方案 B（用路由模板当键）：**未匹配任何 handler 的请求（404）拿不到路由模板** ⇒
⇒ Go 侧此时**根本不会进 `BreakerHandler`**（未注册路由没有 breaker）⇒ ⇒ **RAG 侧也不应建 breaker**（或归并到一个固定键）
⇒ ⇒ **不能用 raw URI 兜底**，否则 **404 扫描器照样能把 map 撑爆** ⇒ **方案 B 必须同时处理「取不到模板」这条路径**。

#### 7.x 归属澄清与 4 处补充（2026-10-24 · 用户提问触发的复核）

**① 本条属于 `service/common`（共享层），**不**属于 `users-common`。**

| 模块 | 内容 | 含过载保护吗 |
|---|---|---|
| `service/common/src/main/java/com/rag/common/protection/` | 11 个类：`GoStyleMaxConnsFilter` / `GoStyleMaxBytesFilter` / `GoStyleBreakerFilter` / `GoStyleCpuSheddingFilter` / `GoStyleBreaker` / `GoStyleAdaptiveShedder` / `GoStyleGrpcClientBreakerInterceptor` / `GoStyleProtectionConfiguration` / `GoStyleProtectionProperties` / `GoStyleCircuitOpenException` / `GoStyleStatusCapturingResponse` | ✅ **全部在这** |
| `service/users/users-common/` | `JwtUtil` / `SchemaInitializer` / `PasswordUtil` / `GoStyleRequired*` / `ErrorCode` 等 9 个文件 | ❌ **完全不含** |

⇒ ① 要改 `GoStyleMaxConnsFilter`、② 要改 `GoStyleBreakerFilter`，**两处都在 `service/common`** ⇒ 一次改动影响全部 10 个 api 服务。
⇒ **台账落点建议**：`docs/DRIFT/common/protection.md`（共享层自己的档）。
⚠️ **该文件当前一条漂移条目都没有**（只有一行「保护层已下沉到 `service/common`」的迁移说明）
⇒ **共享层的行为却只由某个业务域的单侧台账记着** ⇒ 这正是 AGENTS.md §3.5
「少审共享件 = 拿一面没人校过的镜子照自己」的形态。
⚠️ **本轮未写该文件** —— 它属 `common` 域会话的工作面（§5.3：只写自己域的文件）。

**② ⚠️ 代码注释给了一个与实现矛盾的安全保证（2026-10-24 第八轮新发现）**

`service/common/src/main/java/com/rag/common/protection/GoStyleBreakerFilter.java:69-71` 写着：

> 「为什么 breaker 实例不做无界缓存：key 由 method+path 构成，基数等于路由数
> （本模块 13 条，**含未匹配路径不会建 key**），无界增长的现实风险为零。」

但同文件 `:86` `breakerFor()` 是 `breakers.computeIfAbsent(routeKey(request), GoStyleBreaker::new)`，
而 `:109` `doFilter` 对**每一个到达 filter 的请求**都调它 ⇒ **未匹配路径照样建 key**。
⇒ **注释与实现直接矛盾** ⇒ 而且注释给的是**错误的安全保证**
（⇒ 审阅者读注释会以为「键空间天然有界」而漏掉本条）。
⚠️ 上文第 ② 段的判断（未匹配路径也建 key）是**对的**；错的是**代码注释**。

**③ ⚠️ 键基数比「路由数」大得多（2026-10-24 第八轮新发现）**

- RAG 的键是 `routeKey()`（`:90-92`）= `method + "://" + request.getRequestURI()`
  ⇒ **实际具体路径**；
- Go 的键是 `breakerhandler.go:20` `breaker.WithName(strings.Join([]string{method, path}, "://"))`
  ⇒ **注册路由模板**，且 breaker 在**路由注册时**一次性建好 ⇒ 键数 = 路由数。
- ⇒ 同一批路由下，RAG 的键基数 = **实际出现过的不同具体路径数**，Go 的 = **注册路由数**。
- ⚠️ **放大器**：`article-api` 有 `@GetMapping("/article/{id}")`（`ArticleController.java:166,179`）
  这类**路径参数路由** ⇒ **一个 Go 路由对应 N 个 RAG 键**。

**④ ⚠️ 没有回归网（2026-10-24 第八轮新发现）**

`GoStyleBreakerFilter.trackedRoutes()`（`:95-97`，专为观测键基数而写的测试钩子）
在**整个测试目录零引用** ⇒ 键空间无界**没有任何回归网**。

**⑤ 本条在其它域的记录情况（2026-10-24 第八轮新发现 —— 这就是「单侧」的具体形态）**

| 域 | MaxConns 粒度（①） | breaker 键空间（②） |
|---|---|---|
| `users/user` | ✅ 已记（本条） | ✅ 已记（本条） |
| `message` | ✅ 已记（`docs/DRIFT/message/api.md:196`，定性为「刻意的表达层差异（已在 javadoc 留痕，非隐藏缺口）」） | ❌ **没记** |
| `like` | ❌ **判 EQUIVALENT**（`docs/DRIFT/like/api.md:305` 只比了参数值 10000==10000） | ❌ **没记** |
| `hot` | ❌ **判 EQUIVALENT（静态）**（`docs/DRIFT/hot/api.md:447`） | ❌ **没记** |
| `article` | ⏳ 挂在 unresolved（`docs/DRIFT/article/api.md:711`，未结论） | ❌ **没记** |

⇒ **① 其实已有共识口径**（message 域已定性 + `GoStyleMaxConnsFilter.java:58-66` 已留痕）
⇒ **① 缺的只是「签字」**（进 `docs/Intentional_Deviations/`），
**真正需要设计决策的是 ②**。

### 8. 本轮的一条方法论收获

**「四个机制都有实现」不等于「四个机制都被对照过」。**
本轮逐条读下来发现：**37 格 EQUIVALENT**，但那 37 格里**每一格都曾是「没人读过」的状态**；
而真正的差异（`DRIFT-012`）恰恰落在一个**没人会去看**的地方 ——
`pass()` 里那个 `Math.max(1L, ·)`，它读起来像一行无害的防御性代码。
⇒ **印证 §3.3.1**：**读到「有一行代码处理了这件事」不能推出「它处理的方式与 Go 相同」**。

### 9. 变异验证（能编译 + 测试变红，非构建红）

| # | 变异 | 结果 |
|---|---|---|
| **M10** | `pass()` 恢复「至少记 1」的下限（`Math.max(1L, ceil(rt))`） | 🔴 **RED** —— `GoStyleAdaptiveShedderTest` **Failures=1**：`expected: <0> but was: <1>`（红在「RT=0 记 0」那条） |
| **M11** | `pass()` 改成整型截断（`(long) rtMs`） | 🔴 **RED** —— `GoStyleAdaptiveShedderTest` **Failures=1**：`expected: <2> but was: <1>`（红在「1.2ms 记 2」那条） |
| **M12** | `elapsedRtMs` 改成整型除法（`/ 1_000_000L`） | 🔴 **RED** —— `GoStyleCpuSheddingFilterTest` **Failures=1**：`expected: <1.2> but was: <1.0>` |

⇒ 复原后回归：两个测试类 **Failures 合计 = 0**，**报告齐全**。

⚠️ **M10 / M11 / M12 各自红在「对应那一条」断言上**（不是「整类挂掉」）⇒
证明这三道网是**三条独立语义各有一道网**：
「RT=0 记 0」「1.2ms 记 2」「filter 侧是浮点」。

⚠️ **本轮变异脚本本身栽了两次坑**（详见
[`docs/Pitfalls/users/_shared.md` 的 `PIT-USERS-SHARED-006`）：
① `-Dtest=A+B`（surefire 只认逗号）⇒ **测试一次没跑**，报告缺失却被判成「红」；
② 修好命令后，判定又在**合并字符串**里找 `"Failures: 0"` ⇒ 通过的那个类仍带 `Failures: 0`
⇒ 永远判成「不红」。
⇒ **两次都是「自动判定给了错答案」，而两次的救命动作都是「往下读断言消息原文」。**
（第一版的脚本输出「RED ✅」且三次消息内容完全相同 —— 那条重复本身就是破绽。）

### 10. 回归

| 模块 | 测试数 | failures | errors |
|---|---|---|---|
| `service/common` | **151**（21 个测试类；**+5** = 3 条 RT 口径 + 2 条 shedding） | **0** | **0** |
| `service/users/users-common` | **33** | **0** | **0** |
| `service/users/user/user-rpc` | **122** | **0** | **0** |
| `service/users/user/user-api` | **99** | **0** | **0** |

⚠️ **本轮改的是 `service/common`（共享层，影响全部 10 个 api 服务）**，
所以回归必须**跨模块**跑 —— 只跑 `service/common` 自己的测试**不足以**证明
下游 api 服务没被带崩（它们才是使用者）。
⚠️ 另：`service/common` 自己的测试数是 **151**（21 个测试类），
不是本节早先草稿里写的 92 —— 92 只是 `protection` + 几个相邻类的子集，
**以本表为准**。


---

## 2026-10-24 第九轮：Redis 断连行为对齐（`DRIFT-USERAPI-014`）· §9 `UNRESOLVED-004` **闭环**

### 0. 元信息与证据强度

| 项 | 值 |
|---|---|
| 触发 | §9 `UNRESOLVED-004`（Redis 慢 / 断连时的最终可观察行为） |
| 证据强度 | **双侧活体对拍**（真进程 + 真 PG + 真 Redis 容器，**自带容器 / 自建库 / 自带端口**） |
| harness | `tools/difftest/exp-difftest-redis-blacklist.sh`（本轮新建） |
| 守门网 | `UserApiRedisDisconnectedFastFailTest`（2 条 · 真容器 + 真故障注入 + 变异验证） |
| 结论 | **原假设被证实**（RAG 修前确实是 503）⇒ 已修 ⇒ 修后两侧同形 |

### 1. 实测数据（三种 Redis 状态 × 两侧 × 两个 token）

harness 一次性造出**两组 token**：
- **有效 token**：登录拿到；
- **被拉黑 token**：先 `logout` 拉黑（两侧的 rpc logout 都写 `user:jwt_blacklist:<token>`），
  再登录一次拿新的。

⚠️ **为什么必须有「被拉黑 token」这一组**（本轮最大的方法论收获）：
只用有效 token 探测时，「两侧都返回 200」有**两种**可能 ——
(a) 真的 fail-open（要证明的）；(b) **两侧根本没在查黑名单**（假绿）。
⇒ 加了对照组：**Redis 正常 + 被拉黑 token ⇒ 两侧必须 401**，
这才证明黑名单机制真在跑；**Redis 断连 + 被拉黑 token ⇒ 两侧必须 200**（fail-open 放行，不是 401 误拦）。

| 场景 | Go | RAG（修前） | RAG（修后） | 判定 |
|---|---|---|---|---|
| ①a Redis 正常 · 有效 token | 200 / 0.004s | 200 / 0.930s | 200 | ✅ |
| ①b Redis 正常 · **被拉黑** token | **401** / 0.0015s | **401** / 0.0095s | **401** | ✅ **对照组成立** |
| ②a Redis 断连 · 有效 token | 200 / 1.716s | **503** / 3.02s | **200** / 0.029s | 🔴→✅ |
| ②b Redis 断连 · **被拉黑** token | **200** / 1.726s | — | **200** / 0.017s | ✅ 两侧都 fail-open |
| 暖机 · 被拉黑 token | 401 / 0.083s | — | 401 / 0.095s | ✅ 两侧**真重连上了** |
| ③ Redis 很慢（`CLIENT PAUSE 8000`） | **503** / 3.003s | — | **503** / 3.018s | ✅ 完全一致 |

⚠️ **①b 的两侧 body 不同（Go `{"code":401,"msg":…}` vs RAG `{"code":1006,…,"data":null}`）
是 §0 的 **N-1 已签字偏离**，不是新漂移** —— Go 自己两处 401 的 body 就不一致，RAG 统一成 envelope。

⚠️ **暖机必须用「被拉黑 token」**：用有效 token 只能拿到 200，
而「断连秒拒」也是 200 ⇒ **区分不了「连着」与「断着」**（本轮首版脚本就栽在这里）。

### 2. 根因（行号指向**消费点** —— AGENTS.md §3.3.1）

不是「异常映射不同」，是「**失败耗时 vs 请求预算**」：

| 环节 | Go | RAG（修前） |
|---|---|---|
| HTTP 超时预算 | go-zero `rest/config.go:55` `Timeout int64 json:",default=3000"` | `observability.http-timeout: 3s`（**已对齐**） |
| Redis 失败耗时 | go-redis `MaxRetries=3` + **不设** DialTimeout/ReadTimeout ⇒ 连接被拒时**每次重试立即失败** ⇒ ~1.7s | Lettuce **断连期间排队** ⇒ 挂到 `commandTimeout(3s)` ⇒ 3.02s |
| 谁先到 | Redis 先失败（1.7s < 3s）⇒ 进业务层 ⇒ fail-open **200** | Redis 与 HTTP 预算**同刻** ⇒ HTTP 先被打断 ⇒ **503** |

RAG 侧的完整机制链（**消费点**）：
- `io.lettuce.core.protocol.DefaultEndpoint:149`
  `this.rejectCommandsWhileDisconnected = isRejectCommand(clientOptions)`
- `io.lettuce.core.protocol.DefaultEndpoint:837-852` `isRejectCommand(...)`
  ⇒ `DEFAULT` + `autoReconnect=true` ⇒ **不拒 ⇒ 排队**
- `org.springframework.boot.data.redis.autoconfigure.LettuceConnectionConfiguration:209-230`
  `createClientOptions(...)` ⇒ **从不设** `disconnectedBehavior`
  ⇒ 用 Lettuce 默认 `DEFAULT`（`ClientOptions.java:61`）

### 3. 修法

`RedisDisconnectedBehaviorConfig`（新建，`service/users/user/user-api`）
⇒ `LettuceClientOptionsBuilderCustomizer` 设
`ClientOptions.DisconnectedBehavior.REJECT_COMMANDS`
⇒ 命中 `DefaultEndpoint:314`（`Currently not connected. Commands are rejected.`）
与 `DefaultEndpoint:692`（断连瞬间取消排队命令）⇒ 失败耗时 ~1ms ⇒ 进业务层 ⇒ fail-open 200。

⚠️ **Boot 4 的包名变了**：`LettuceClientOptionsBuilderCustomizer` 在
`org.springframework.boot.data.redis.autoconfigure`（**不在** `spring-boot-autoconfigure` 里 ——
那是 `spring-boot-data-redis` 模块）。

⚠️ **只改「断连」，不改「连着但不回包」**：后者两侧都应 503
（实测 Go 3.003s / RAG 3.018s，**完全一致**）⇒ 改成 200 反而是**反向漂移**。

⚠️ **「首次建连期间会不会被误拒」已实测排除**：Redis 恢复后 RAG 立刻拿到 **401**（黑名单命中）
⇒ 说明重连后命令照常下发，`REJECT_COMMANDS` 只作用于「已断连」态。

### 4. 守门网与**变异验证**（§3.3：绿测试必须是错误答案的守卫）

`UserApiRedisDisconnectedFastFailTest`（2 条）：
1. 读**运行时真正在用的** `ClientOptions`（`StatefulConnection.getOptions()`，不是 factory 上的副本）
   ⇒ 必须是 `REJECT_COMMANDS`；且 Redis 可达时 `hasKey` 往返成功。
2. **真故障注入** ⇒ 断连后 `hasKey` 必须在 **<1500ms** 内失败。

⚠️⚠️ **第一版守门网是假守卫，变异验证当场抓出来了**：
第一版用 `container.stop()` 注入故障 ⇒ 变异（`REJECT_COMMANDS` ⇒ `DEFAULT`）时测试**照样绿**。
用时间曲线探针实测到 Lettuce 层的异常原文才发现：

| 配置 | Lettuce 层异常 |
|---|---|
| `REJECT_COMMANDS` | `RedisException: Currently not connected. Commands are rejected.` |
| `DEFAULT` | `RedisException: java.net.SocketException: Connection reset` |

⇒ `container.stop()` 让容器被**强杀** ⇒ 连接收到 **RST** ⇒ Lettuce 往 socket 写命令时**立刻失败**
⇒ **根本走不到** `disconnectedBehavior` 那个分支 ⇒ 两种配置的耗时都是 1~13ms ⇒ **分不出**。
而 live 的 `docker stop` 让 Redis **优雅退出** ⇒ 连接收到 **FIN**
⇒ Lettuce 进入 **DISCONNECTED（正在重连）** 状态 ⇒ 命令被**排队** —— 这才是 `DEFAULT` 会挂 3s 的路径。

⇒ **守门网改用可控 TCP 透传代理**（`GracefulDisconnectProxy`）：
故障时**先关监听口**（⇒ 重连被拒）+ **再对已建立的连接半关闭**（⇒ 客户端收到 FIN）
⇒ 精确复刻 live 的断连形态。

**变异验证结果**（`REJECT_COMMANDS` ⇒ `DEFAULT`）：

| 用例 | 结果 |
|---|---|
| 用例 1 | ❌ 红 —— `expected: <REJECT_COMMANDS> but was: <DEFAULT>` |
| 用例 2 | ❌ 红 —— **实测耗时 3017ms**（预算 1500ms），异常链 `QueryTimeoutException: Redis command timed out <- RedisCommandTimeoutException: Command timed out after 3 second(s)` |

⇒ **3017ms 与 live 实测的 3020ms 吻合** ⇒ 这条守卫真的在盯「快速失败」，
不是「Bean 存在」那种假断言。两条都红在**对应那一条**断言上（不是整类挂掉、不是上下文加载失败）。

### 5. 本轮**未**修的项与理由

| ID | 内容 | 本轮结论 | 为什么不动 |
|---|---|---|---|
| `DRIFT-USERAPI-013` | 过载保护：MaxConns 闸门粒度（Go per-route vs RAG 全局一个）+ breaker 按 URI 建 key ⇒ 键空间无界 | 仍待拍板 | 属**共享层**（影响全部 10 个 api 服务），不在本组件单侧判定权内 |
| `DRIFT-USERAPI-014` 的**跨域推广** | `admin-api` 等其余 api 服务有**同款**黑名单中间件 ⇒ 大概率有同款漂移 | 🔴 **待推广** | `RedisDisconnectedBehaviorConfig` 现在只装在 user-api；**建议上移到 `service/common`** ⇒ 跨域改动，需拍板 |
| `DRIFT-USERAPI-009②` | 3s 超时作用域（observability 在鉴权之外） | 仍成立 | 属已签字 `SD-USERAPI-002` 的**未覆盖部分**（签字只覆盖 ① 可观测性） |

### 6. 回归

| 模块 | 测试数 | failures | errors |
|---|---|---|---|
| `service/common` | **151** | **0** | **0** |
| `service/users/users-common` | **33** | **0** | **0** |
| `service/users/user/user-rpc` | **125** | **0** | **0** |
| `service/users/user/user-api` | **101**（99 + **2** 本轮新增） | **0** | **0** |

⚠️ **数测试数时踩了一个坑**：直接对 `target/surefire-reports/*.xml` 求和会**多算** ——
里面有**上一轮遗留的陈旧报告**（本轮实测：`TmpRedisTimingProbe.xml`（已删的临时探针，1 条）
与 `UserRpcJsonbAndAvatarProjectionTest.xml`（Oct 23 的旧报告，4 条））
⇒ 求和得 102 / 129，**与 mvn 自己的输出（101 / 125）对不上**。
⇒ **以 mvn 输出的 `Tests run:` 为准**（详见 `docs/Pitfalls/users/_shared.md`）。

⚠️ **本轮改了 user-api 的 Redis 客户端行为**，
所以除 user-api 自身外，还要确认 `service/common` / `users-common` / `user-rpc` 没被带崩。
