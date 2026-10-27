<!-- @move AGENTS.md L2443-L2481 · §6.2.4① Go 对空 body 的三段取证（admin-api） -->
### 6.2.4 本轮（2026-10-21 第二批）取证与附带发现

#### ① Go 对「空 body」的三段取证（支撑 admin-api 13 端点 `required=false`）

不是推断，是逐段读源码得到：

| # | 出处 | 事实 |
|---|---|---|
| 1 | `service/user/admin/api/internal/handler/admin/*.go` | 13 个 handler 一律 `if err := httpx.Parse(r, &req); err != nil`，`req` 是包级 `var req types.XxxReq` |
| 2 | go-zero v1.10.2 `rest/httpx/requests.go:153` `withJsonBody` | `r.ContentLength > 0 && ContentType 含 application/json`。空 body ⇒ false ⇒ `ParseJsonBody` 走 `mapping.UnmarshalJsonMap(nil, v)`，对 struct 是 **no-op、不报错** |
| 3 | admin `api/internal/types/types.go` | 13 个 Req **零个** `Validate()` 方法、**零个** `validate:` tag；且 admin 服务**从未**调 `httpx.SetValidator` ⇒ `getValidator()` 返回 nil ⇒ `httpx.Parse` 的校验环节整体不生效 |

**结论**：Go 对空 body 的 POST 一律 **HTTP 200 + 业务码**，req 停在**零值 struct**。
以 `loginlogic.go:37-56` 为例：零值 username/password **直接透传给 rpc**，
由 rpc 判 `NotFound → 1003` / `Unauthenticated → ErrorLoginWrong`（= **1002**，不是 1008）。

**RAG 处置**：13 端点全改 `@RequestBody(required = false)` +
Controller 侧 `if (req == null) req = new XxxReq()`（对齐 Go 的零值 struct，服务层因此不会 NPE）。

> **user 侧同样做了独立取证**（2026-10-21，**未套用 admin 的结论** ——
> §3.4.3 N-3 明确警告过「admin 侧取参机制不同，不可跨模块套用结论」）：
> `internal/handler/user/*.go` 9 个 handler 一律 `var req types.XxxReq` + `httpx.Parse`；
> `types.go` 8 个 Req、**零个** `validate:` tag、**零个** `Validate()`；
> 整个 user 服务**从未**调 `httpx.SetValidator`。结论与 admin 侧一致 ⇒
> user-api **9 个端点全部**改为 `required=false` + Controller 侧 `null → new XxxReq()`。
> 落点验证：`loginlogic.go` 的 switch 是 `Internal → ErrorServerCommon` / `default → CodeServerBusy`，
> 零值 username/password **直接透传给 rpc**（`UserLoginService` 同样对 null 字段兜底成 `""`）。
>
> **⚠️ 2026-10-21 二次更正（DRIFT-NEW-016）**：本段原写「新增 2 条真管线断言
> `emptyBodyOnLoginDoesNotReturn400` / `emptyBodyOnUpdateDoesNotReturn400`」并断言
> 零值 LoginReq **带着空 username/password 抵达 rpc**。该结论**已被实测证伪**：
> Go 的 `UnmarshalJsonMap(nil, v)` 收的是**有类型的 nil map**，在 `unmarshaler.go:988`
> 的 type switch 里命中 `case map[string]any`，照常遍历字段 ⇒ 真空 body 返回
> `400 field "username" is not set`，**零值 req 到不了 rpc**。
> 该条已改名 `emptyBodyOnLoginIs400NotPassedThrough` 并反转断言；
> `emptyBodyOnUpdateDoesNotReturn400` 因 `user.update` 四字段全 `,optional` **不反转**，
> 改名 `emptyBodyOnUpdateStillPassesThroughBecauseAllFieldsAreOptional`，留作对照组
> ——同一个「空 body」在两个端点一个 400 一个 200，差别只在 DTO 的 tag。

---

<!-- ═══════════════════════════════════════════════════════════════════════ -->
<!-- 本文件自 2026-10-23 起承担完整台账职责（spec §31-b）。以上为历史条目，原样保留。 -->
<!-- ═══════════════════════════════════════════════════════════════════════ -->

# admin-api 语义对照台账

**核对日期**：2026-10-23　　**对照方向**：Go（真源）→ Spring Boot（RAG）

| | |
|---|---|
| **Go 侧** | `Sea-RideTheWind_golang/service/user/admin/api`（**34 个文件** = 33 个 `.go` + 1 个 yaml：1 main + 1 config + **14 handler**（13 个 `handler/admin/*.go` + **1 个 `handler/routes.go`**）+ 13 logic + 1 middleware + 1 metrics + 1 svc + 1 types + 1 yaml）<br>〔**2026-10-24 复核订正**：原写「36 个文件 / 13 handler」——36 是错的（分解相加只有 34），且 `routes.go` 不是 handler 却被漏计〕 |
| **RAG 侧** | `RAG/service/users/admin/admin-api`（**52 个 main + 13 个 test** 文件）<br>〔**2026-10-24 复核订正**：原写「43 main + 10 test」〕 |
| **外部依赖（不在本组件范围）** | Go `service/user/common/{cryptx,errmsg,jwt}`、`service/common/{logger,observability,response}`；RAG `service/common`（`HttpMiddleware` / `Response` / `BusinessException`）、`service/users/users-common`（`ErrorCode` / `GoStyle*` / `JwtUtil`） |
| **⚠️ 明确排除** | Go `service/user/rpc/internal/logic/` 的 **7 个孤儿 `_test.go`**（全仓零引用）；Go `service/user/admin/rpc`（属 `admin/rpc.md`，由另一会话审查） |

**编号约定**：本组件用组件级前缀 **`DRIFT-ADMINAPI-0xx`**。
理由：`DRIFT-0xx` / `DRIFT-NEW-0xx` 在 `users/_shared.md` 与 `admin/_shared.md` 中**已被占用**
（域内最大为 `DRIFT-024`），而这两个文件本会话**不得修改**，继续追加会造成并发写冲突。
组件级前缀是本仓既有惯例（`DRIFT-LIKEAPI-*` / `DRIFT-MSGAPI-*`）。
若上游要求域内统一编号，可整体重命名，无内容依赖。

## 证据强度声明（本轮必读）

本轮是**纯源码级对照**，每一侧都逐行读过；涉及框架行为时读到的是**库源码的消费点**
（go-zero `v1.10.2`、golang-jwt `v4.5.2`、Jackson 3），而不是声明点。

| 标签 | 含义 | 本轮是否产出 |
|---|---|---|
| `静态源码·双侧` | 两侧源码 + 依赖库源码逐行对照得出 | ✅ 本轮绝大多数条目 |
| `双侧活体对拍` | 两套真实进程同时跑同一请求并比对 | ❌ **本轮未跑**（引用既有成果时单独标注「非本轮」） |
| `真 PG` | 真实 PostgreSQL 参与 | ❌ 不适用（api 层无 SQL） |
| `真容器 + MockMvc` | 真 Spring 上下文 + 真 Filter/Interceptor/advice，但**非真 Tomcat** | ❌ **本轮未跑**（引用既有 `AdminApiMvcPipelineTest` 等时标注） |
| `真 Tomcat` | 真实 servlet 容器 | ❌ 本轮未跑；且 **admin-api 当前无此守门**（见 §9 UNRESOLVED-002） |

> ⚠️ **引用他人已跑的证据不等于本轮验证**。本文件凡出现「已由 X 验证」，均指**既有台账记录的
> 2026-10-21/22 成果**，本轮未复跑。按 AGENTS.md 硬约束 1，这些结论不得升级为「本轮已闭环」。

---

## 1. Component Contract

### 1.1 Purpose

Admin Center 的 **HTTP 网关**。它自身**不持有任何业务状态、不直接访问数据库**，
只做四件事：解析请求 → 从 JWT / 黑名单解析身份 → 转发 gRPC 给 `admin-rpc` → 把结果包成统一 envelope。

### 1.2 Public Operations（13 个）

| # | Go 路径（`routes.go:16-95`） | Go method | RAG 路径 | RAG method | 鉴权 |
|---|---|---|---|---|---|
| OP-01 | `/admincenter/v1/admin/create` | POST | `/admin/v1/create` | POST | public |
| OP-02 | `/admincenter/v1/admin/login` | POST | `/admin/v1/login` | POST | public |
| OP-03 | `/admincenter/v1/admin/banuser` | POST | `/admin/v1/banuser` | POST | JWT |
| OP-04 | `/admincenter/v1/admin/delete` | **DELETE** | `/admin/v1/delete` | POST | JWT |
| OP-05 | `/admincenter/v1/admin/invite/create` | POST | `/admin/v1/invite/create` | POST | JWT |
| OP-06 | `/admincenter/v1/admin/getself` | **GET** | `/admin/v1/getself` | POST | JWT |
| OP-07 | `/admincenter/v1/admin/getuser` | **GET** | `/admin/v1/getuser` | POST | JWT |
| OP-08 | `/admincenter/v1/admin/getuserlist` | **GET** | `/admin/v1/getuserlist` | POST | JWT |
| OP-09 | `/admincenter/v1/admin/logout` | POST | `/admin/v1/logout` | POST | JWT |
| OP-10 | `/admincenter/v1/admin/resetuserpassword` | POST | `/admin/v1/resetuserpassword` | POST | JWT |
| OP-11 | `/admincenter/v1/admin/unbanuser` | POST | `/admin/v1/unbanuser` | POST | JWT |
| OP-12 | `/admincenter/v1/admin/update` | POST | `/admin/v1/update` | POST | JWT |
| OP-13 | `/admincenter/v1/admin/updateuser` | POST | `/admin/v1/updateuser` | POST | JWT |

⚠️ **路径前缀 / method / 参数通道三条一起偏**，已在 `users/_shared.md` §3.4.1 **A-03** 留档为有意偏离。
本轮复核结论：**A-03 描述与当前实现一致**，无需新增条目。

### 1.3 ⚠️ 组件计数的一处纠正（AGENTS.md §4.2 与任务简报的「2 client」不成立）

| 断言 | 实测 | 证据 |
|---|---|---|
| AGENTS.md §4.2 Phase 4：「admin-api：13 Service + **2 client**（admin-rpc + user-rpc）」 | ❌ **只有 1 个 client** | Go 侧 `service/user/admin/api` 全树 grep `user/rpc` → **零命中**；RAG 侧 grep `@GrpcClient` → 只有 `admin-api/.../client/AdminRpcClient.java:142` 的 `@GrpcClient("admin-rpc")` |
| — | ⚠️ 残留资产 | `admin-api/src/main/resources/application.yml:70-75` 有一段**死的** `grpc.client.user-rpc`；`pom.xml:81-84` 的注释写着「连 admin-rpc + user-rpc」 |

**影响**：本组件**没有**第二个 gRPC 依赖，因此**不存在**「admin-api → user-rpc」的跨组件行为耦合。
本轮 §7 的所有条目里，凡涉及 admin-rpc 响应语义的都标了「需与 `admin/rpc.md` 合并判读」；
凡以为要合并 user-rpc 的**都删掉了**（`AGENTS.md` §4.2 需上游修正，本组件不改）。

### 1.4 Inputs / Outputs / State / Side Effects / Failure Model

| 维度 | 事实 |
|---|---|
| **Inputs** | JSON body（11 个）+ query string（Go 侧 `getuser` / `getuserlist`）+ `Authorization` 头 |
| **Outputs** | `{code:int, msg:string, data:<any>}`（Go `common/response/response.go:3-7`） |
| **State** | **无**。13 个 Logic 全部是 `(req) → (resp, code)` 纯函数式；无缓存、无会话、无可变单例 |
| **Side Effects** | ① gRPC 出向调用；② Redis `EXISTS`（黑名单）；③ Prometheus counter 自增 |
| **Failure Model** | 分三族，**互不重叠**：<br>① **HTTP 400 + text/plain**（请求解析层，`httpx.Parse` 失败）<br>② **HTTP 200 + envelope{code≠200}**（业务层，13 个 Logic 的 `(nil, code)`）<br>③ **HTTP 401**（鉴权层，两处短路）<br>**注意 Go 侧不存在「HTTP 4xx/5xx 表达业务失败」这一族** |

---

## 2. GO-SEMANTIC-MODEL

### 2.1 请求管线（按真实执行顺序，非声明顺序）

```
[server.Use(observability.NewHTTPMiddleware)]              ← admincenter.go:36，走 server.Use
        ↓  （构造期：WithMiddleware 已把 CheckBlacklist 包进 route.Handler）
[chain: ... → Recover → Trace → Prometheus → Authorize(JWT)]  ← engine.go:101-113 appendAuthHandler
        ↓
[CheckBlacklistMiddleware]                               ← routes.go:34 rest.WithMiddlewares
        ↓
[handler: httpx.Parse → NewXxxLogic → Xxx(req) → httpx.OkJson]
```

**关键取证（三段，指向消费点）**：

| 断言 | 出处（消费点） |
|---|---|
| `Authorize` 在 `ng.middlewares` **之前** append ⇒ JWT 比 observability **更外层** | go-zero `rest/engine.go:108-112`：`chn = ng.appendAuthHandler(...)` 后才 `for _, middleware := range ng.middlewares { chn = chn.Append(...) }` |
| chain 索引 0 最外层、先执行 | `go-zero@v1.10.2/rest/chain/chain.go` 的 `Then` = `m0(m1(...(mN(h))))` |
| **黑名单不在 chain 里**，它是 `ThenFunc` 的**参数** ⇒ 比 chain 任何元素都内层 | go-zero `rest/server.go:222-235` `WithMiddleware`：`Handler: middleware(route.Handler)` —— 在 `routes.go` 构造期就完成，早于 `bindRoute` |
| ⇒ **真实顺序 = Authorize(JWT) → observability → CheckBlacklist → handler** | 三段合成 |

### 2.2 鉴权两层

**层 1 — `rest.WithJwt`（`go-zero@v1.10.2/rest/handler/authhandler.go:45-84`）**

```
Authorization 头缺失/空            → unauthorized() → HTTP 401，body 空
token 前缀不匹配（大小写敏感 ToUpper 比较）→ 原样返回整串去验签 → 必然失败 → HTTP 401
签名错 / 过期                       → HTTP 401，body 空
claims 注入 ctx：for k,v := range claims { switch k { case "aud","exp","jti","iat","iss","nbf","sub": 跳过
                                                default: ctx = WithValue(ctx, k, v) } }
```

⇒ **ctx 里的 key 是 JWT claim 的名字**。admin 侧签发的 token 声明只有 3 个
（`service/user/common/jwt/jwt.go:8-14`：`exp` / `iat` / `userId`），
过滤掉 `exp` / `iat` 后 ⇒ **ctx 实际只有 `userId` 一个 key**（`json.Number`，因为
`go-zero@v1.10.2/rest/token/tokenparser.go:122-124` `newParser()` = `jwt.NewParser(jwt.WithJSONNumber())`）。
**ctx 里没有 `uid`。** 这是 §7 DRIFT-ADMINAPI-001 的全部依据。

**层 2 — `CheckBlacklistMiddleware`（`internal/middleware/checkblacklistmiddleware.go:22-44`）**

```
authHeader == ""                 → next(w,r) 直接放行（不写 ctx）
TrimPrefix(authHeader,"Bearer ")（**大小写敏感**）→ 空则放行
Redis.ExistsCtx("admin:jwt_blacklist:" + token)
   err != nil                    → 放行（**fail-open**）
   exists && err == nil          → 401 + Content-Type "appliaction/json;charset=utf-8"（Go 拼写错误）
                                   + body {"code":401,"msg":"登录已失效,请重新登陆"}
ctx = WithValue(ctx,"jwt_token", token)   ← 写的是 TrimPrefix 结果
```

⚠️ **Go 自身两层取法不同**：验签用大小写不敏感的 `ToUpper` 比较，黑名单用大小写敏感的
`TrimPrefix`。⇒ `"bearer eyJ..."` 在 Go 里验签用 `eyJ...`、黑名单查 `bearer eyJ...`。

### 2.3 逐 OP 语义（13 个，只写差异点与关键证据）

| OP | 输入来源 | 身份来源 | gRPC 方法 | Go 的 code switch（消费点） | 成功响应 |
|---|---|---|---|---|---|
| OP-01 create | JSON body | — | `CreateAdmin` | `AlreadyExists→1001 / PermissionDenied→1016 / Internal→5001 / default→1015`（`createlogic.go:47-56`） | `{uid:"<string>"}` |
| OP-02 login | JSON body | — | `Login` | `NotFound→1003 / Internal→5001 / Unauthenticated→1002 / default→1015`（`loginlogic.go:48-57`） | `{token}` |
| OP-03 banuser | JSON body | **`ctx["uid"]`** | `BanUser` | `NotFound→1003 / Internal→5001 / default→1015`（`banuserlogic.go:50-57`） | `{success: rpcResp.Success}` |
| OP-04 delete | JSON body | **`ctx["uid"]`** | `DeleteUser` | 同上（`deletelogic.go:49-56`） | `{success: rpcResp.Success}` |
| OP-05 invite/create | 空 struct | `ctx["userId"]`（**缺失 ⇒ 1006，且不发 RPC**） | `CreateInviteCode` | `NotFound→1003 / Internal→5001 / default→1015`（`createinvitelogic.go:56-63`） | `{invite_code, expires_at}` |
| OP-06 getself | 空 struct | `ctx["userId"]`（缺失 ⇒ 1006） | `GetSelf` | `NotFound→1003 / Internal→5001 / default→1015`（`getselflogic.go:53-60`） | `{admin:{...}}`，**无任何一致性校验** |
| OP-07 getuser | **form/query `uid`** | — | `GetUser` | `NotFound→1003 / Internal→5001 / default→1015`（`getuserlogic.go:42-49`） | `{user, found:true}`（`found` 在 **api 层**写死） |
| OP-08 getuserlist | **form/query `page,page_size,keyword`** | — | `GetUserList` | `Internal→5001 / default→1015`（`getuserlistlogic.go:44-49`） | `{list, total}`；**空结果 ⇒ `"list":null`**（nil slice + `json:"list"` 无 omitempty） |
| OP-09 logout | 空 struct | `ctx["jwt_token"]`（**缺失 ⇒ 1004，不发 RPC**） | `Logout` | `Internal→5001 / default→1015`（`logoutlogic.go:48-53`） | `{success:true}`（写死） |
| OP-10 resetuserpassword | JSON body | **`ctx["uid"]`** | `ResetUserPassword` | `NotFound→1003 / **Internal→5003** / default→1015`（`resetuserpasswordlogic.go:50-57`） | `{success: rpcResp.Success}` |
| OP-11 unbanuser | JSON body | **`ctx["uid"]`** | `UnBanUser` | `NotFound→1003 / **Internal→5003** / default→1015`（`unbanuserlogic.go:50-57`） | `{success: rpcResp.Success}` |
| OP-12 update | JSON body | `ctx["userId"]`（缺失 ⇒ 1006） | `UpdateSelf` | `AlreadyExists→1001 / NotFound→1003 / **Internal→5003** / default→1015`（`updatelogic.go:58-67`） | `{success:true, admin}`（`success` 写死） |
| OP-13 updateuser | JSON body | **`ctx["uid"]`** | `UpdateUser` | `AlreadyExists→1001 / NotFound→1003 / Internal→5001 / default→1015`（`updateuserlogic.go:54-63`） | `{success:true, user}`（`success` 写死） |

⚠️ **Go 自身的三处不一致（真源如此，RAG 必须逐字跟随，不得「顺手统一」）**：

1. `Internal` 的映射：`update` / `resetuserpassword` / `unbanuser` → **5003**「查询数据库失败」，
   其余 10 个端点 → **5001**「系统内部错误」。
2. 身份来源：6 个端点读 `ctx["userId"]`（真实 uid），5 个读 `ctx["uid"]`（**恒 0**）。
3. `success` 的来源：OP-03/04/10/11 **透传** `rpcResp.Success`；OP-09/12/13 **写死 true**。

### 2.4 请求解析层（`httpx.Parse` → `go-zero@v1.10.2/core/mapping`）

```
ParsePath → ParseForm → ParseHeaders → ParseJsonBody → validator
ParseJsonBody: ContentLength > 0 && ContentType 含 "application/json"  ⇒  UnmarshalJsonReader
               否则                                                 ⇒  UnmarshalJsonMap(nil, v)
```

**必填判据**（`go-zero@v1.10.2/core/mapping/unmarshaler.go:918-969` `processNamedFieldWithoutValue` 的**消费点**）：

```
1. opts.getDefault() 命中（tag 含 ",default="）→ 填默认值、return，不报错
2. Array / Map / Slice  → processFieldNotFromString，**不报错**
3. 其他标量 且 !opts.optional() → newInitError ⇒ field "xxx" is not set
```

⚠️ `parseOption`（`go-zero@v1.10.2/core/mapping/utils.go`）**只认 `optional` / `optional,type=...`**，
**`,omitempty` 被静默忽略** ⇒ `json:"email,omitempty"` 仍是**必填**。

⚠️ **form 通道额外丢空值**（`go-zero@v1.10.2/rest/httpx/util.go:37-43` `GetFormValues`：`if len(v) == 0 { continue }`）
⇒ `keyword=`（空串）退化成「字段缺席」⇒ 400。json 通道的空串是合法值。

### 2.5 输出层

- 13 个 handler **全部**走 `httpx.OkJson` ⇒ **业务失败也是 HTTP 200**（`internal/handler/admin/*handler.go` 逐个核对，无一例外）
- `httpx.Parse` 失败走 `httpx.ErrorCtx` ⇒ admin 服务**从未**调 `httpx.SetErrorHandler`
  （`service/user` 全树 grep 零命中）⇒ `doHandleError` 的 `handler == nil` 分支 ⇒
  **`http.Error(w, err.Error(), 400)`** ⇒ `400` + `Content-Type: text/plain; charset=utf-8`
  + `X-Content-Type-Options: nosniff` + body 带**尾随换行**
- 超时：`observability/http.go:80-88` ⇒ 3s 超时写 `http.Error(w,"Request Timeout",503)`；客户端断连写 499

---

## 3. SPRINGBOOT-SEMANTIC-MODEL

### 3.1 请求管线

```
[GoStyleTraceparentFilter(+3)] → [GoStyleMaxConnsFilter(+4)] → [GoStyleBreakerFilter(+5)]
        → [GoStyleCpuSheddingFilter(+6)] → [GoStyleJwtAuthFilter(+6) · admin-api 域内]
        → [HttpMiddlewareFilter(+7) · 3s 预算起算] → [GoStyleMaxBytesFilter(+8)]
        → DispatcherServlet
        ↓
[AdminJwtAuthInterceptor.preHandle]  ← WebMvcConfig:60-62 注册到 /admin/v1/**
        ↓
[AdminController 方法]（GoStyleRequest.normalize）
        ↓
[13 个 Service] → [AdminRpcClient.adminRpc()] / [StringRedisTemplate.hasKey]
        ↓
[异常时：GlobalExceptionHandler] → [正常时：GlobalResponseAdvice 包 envelope]
```

⚠️ **共享层槽位实测值（2026-10-27 重算，勿信旧行号）**：
`+3 Traceparent` · `+4 MaxConns` · `+5 Breaker` · `+6 CpuShedding` · **`+7 HttpMiddleware（3s 预算起算）`** · `+8 MaxBytes`
⇒ 域内 Filter 的可用窗口是 **`[+4,+6]`**（须 ≥ +3 才在 SERVER span 内、须 ≤ +6 才出 3s 预算）
⇒ 落 **`+6`**（与 `GoStyleCpuSheddingFilter` **并列**；两者都不写响应 ⇒ 先后不改变可观察面）。
⚠️ `HttpMiddlewareFilter` **不是**最外层（`+3/+4/+5/+6` 在它外面），但那几个**不计时**。

⚠️ **与 Go 的顺序差异只剩「遥测面」一半**：Go 的 observability(3s) 在 **JWT 内层** ⇒ 验签**不**计入 3s 预算；
RAG 侧验签已搬进 `GoStyleJwtAuthFilter(+6)` ⇒ 同样**在预算之外** ⇒ **② 超时范围已闭环**；
但 **① 遥测面**（401 请求仍产生观测数据）**未闭环**。见 DRIFT-ADMINAPI-006。

### 3.2 鉴权（`GoStyleJwtAuthFilter` + `AdminJwtAuthInterceptor`）

| 阶段 | 行为 | 与 Go |
|---|---|---|
| public 路径 | `AdminJwtAuthPaths.isPublic(uri)` ⇒ 直接 return（`AdminJwtAuthInterceptor:101-103`；集合定义 `AdminJwtAuthPaths:44-47`） | ✅ 一致（集合逐条相同） |
| 阶段 1 验签 | 缺 `Authorization` ⇒ 401；`tokenForSignatureVerification` 剥前缀（大小写不敏感）；jjwt 验签；`userId` claim **必须是 Number**，否则 401（`AdminJwtSignatureVerifier:73-129`，claim 判定 `:101-113`） | ⚠️ 比 Go **多一道** claim 存在性/类型检查（DRIFT-ADMINAPI-003）⇒ **已签字偏离 SD-2026-10-23-01，属预期差异** |
| 阶段 1 的**执行位置** | 验签在 **`GoStyleJwtAuthFilter`（`@Order(+6)`）** 里 ⇒ 在 `HttpMiddlewareFilter(+7)` 的 3s 预算**之外** | ✅ **已闭环**（2026-10-27）——原 DRIFT-ADMINAPI-006 的 ② 半，见下条 |
| 阶段 2 黑名单 | `trimBearerPrefix`（大小写敏感）；`hasKey(prefix+token)`；异常吞成 `null` ⇒ **fail-open**（`AdminJwtAuthInterceptor:172-178` + `:197-207`） | ✅ 一致（含「两段取法不同」这个 Go 自身的不一致）；**仍在预算之内** ⇒ 与 Go 一致（Go 的黑名单是 route 级中间件 ⇒ 最内层） |
| attribute 写入 | 成功时写 `userId`（Long，`:159`）与 `jwt_token`（String，`:182`） | ✅ 一致 |

⚠️ **Go 从不写 `uid` attribute，RAG 也没有** —— 但两端的**读取方**不同，见 §4 矩阵。

⚠️ **异常对象由 Filter 产出、interceptor 原样重抛**（`AdminJwtAuthInterceptor:152-153`）
⇒ `GlobalExceptionHandler` 的 `@ExceptionHandler` 仍管得到 ⇒ **401 + 完整信封的形状逐字不变**（M-05 不受影响）。

### 3.3 逐 OP 语义（只写与 Go 的差异点）

| OP | RAG 出处 | 与 Go 的关系 |
|---|---|---|
| OP-02 login | `AdminLoginService:60-79`：`INTERNAL→5001 / NOT_FOUND→1003 / UNAUTHENTICATED→1002 / default→1015` | ✅ **逐条一致** |
| OP-01 create | `AdminCreateService:59-68`：`PERMISSION_DENIED→1016 / ALREADY_EXISTS→1001 / INTERNAL→5001 / default→1015` | ✅ 一致 |
| OP-03/04/10/11 | 各 Service 自带完整 switch | ✅ code 一致；❌ **身份来源不同**（DRIFT-ADMINAPI-001）；✅ `success` 透传一致 |
| OP-05 invite | `AdminCreateInviteCodeService:44-49` `claimedUID` + `inviteTokenInvalid` 埋点 | ✅ 埋点一致；❌ 身份层多一道 `uid<=0`（DRIFT-ADMINAPI-001） |
| OP-06 getself | `AdminGetSelfService:42` 走 `IdentityResolver.resolveAdmin` | ❌ **多两道 Go 没有的校验**（DRIFT-ADMINAPI-002）；❌ 兜底映射不同（DRIFT-ADMINAPI-008） |
| OP-07 getuser | `AdminGetUserService:67` `setFound(true)`；`:68-70` `if (hasUser())` 才 set | ✅ `found=true` 一致；❌ user 缺失形态不一致（DRIFT-ADMINAPI-005） |
| OP-08 getuserlist | `AdminListUserService:72-79` 空结果置 null；`pageOrDefault()` 只在 null 时兜底 | ✅ 与 Go 的 nil slice / tag default 语义一致；⚠️ `keyword=""` 被接受（Go form 通道 400）——已在 `GetUserListReq:30-36` 留档 |
| OP-09 logout | `AdminLogoutService:39-43` 读 `jwt_token` attribute，null ⇒ 1004 | ✅ 一致 |
| OP-12 update | `AdminUpdateSelfService:74-86`：`ALREADY_EXISTS→1001 / NOT_FOUND→1003 / INTERNAL→5003 / default→1015` | ✅ 一致；❌ 身份层多一道 `uid<=0` |
| OP-13 updateuser | `AdminUpdateUserService:74-83`：`INTERNAL→5001 / ALREADY_EXISTS→1001 / NOT_FOUND→1003 / default→1015`；`:93` 写死 `success=true` | ✅ code 一致；❌ 身份来源不同 |

### 3.4 输出层（**`@ResponseBodyAdvice` 的两条守卫**）

`GlobalResponseAdvice.beforeBodyWrite` 有两道独立守卫（缺一即二次包裹）：

| # | 守卫 | 覆盖的响应族 |
|---|---|---|
| G1 | `body == null \|\| body instanceof Response`（`:39-41`） | `GlobalExceptionHandler` 返回的 `ResponseEntity<Response>`（**HTTP 400 之外的全部错误族**） |
| G2 | `selectedContentType != null && !APPLICATION_JSON.isCompatibleWith(...)` ⇒ 原样透传（`:52-55`） | 400 text/plain、405、415 |

⇒ **成功路径与异常路径均已覆盖**：成功 = DTO → 包一层；异常 = `Response` → G1 短路；400 = 非 JSON → G2 短路。

---

## 4. Equivalence Matrix

Result 只用 5 个枚举值。Evidence 列的强度标签见文件头声明。

| # | Operation | Semantic Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|---|---|
| M-01 | ALL | 响应信封形状 | `{code,msg,data}` | `{code,msg,data}` | EQUIVALENT | `response.go:3-7` vs `Response.java:14-18`（无 `@JsonInclude` ⇒ `data:null` 恒出现） |
| M-02 | ALL | 业务失败的 HTTP 状态 | 恒 **200** | 恒 **200** | EQUIVALENT | 13 个 handler 全走 `httpx.OkJson`；`GlobalExceptionHandler:231-233` `decideStatus` 无条件 `OK` |
| M-03 | ALL | 请求解析失败的响应 | `400` + text/plain + nosniff + 尾换行 | `400` + text/plain + nosniff + 尾换行（**MockMvc 层**） | EQUIVALENT（**真实 Tomcat 未验**） | `doHandleError` 的 `http.Error` vs `GlobalExceptionHandler:176-182`；**缺口见 UNRESOLVED-002** |
| M-04 | ALL | 401 状态码 | 401（两处短路） | 401 | EQUIVALENT | `authhandler.go:106-121` / `checkblacklistmiddleware.go:38` vs `handleAuthentication:66-68` |
| M-05 | 鉴权 | 验签失败 / 缺头的 401 **body** | **空 body** | 完整 envelope `{code:1006}` | OBSERVABLE DIFFERENCE | 已在 `AuthenticationException:20-39` 与 `_shared.md` N-1 留档（**已签字**） |
| M-06 | 鉴权 | 黑名单 401 的**业务码** | **`code:401`** + `msg:"登录已失效,请重新登陆"` + `Content-Type: appliaction/json`（Go 拼写错误） | **`code:1006`** + `msg:"TOKEN已过期"` + `application/json` | **OBSERVABLE DIFFERENCE** | → **DRIFT-ADMINAPI-004** |
| M-07 | 鉴权 | 签名有效但 `userId` claim 缺失/非数字 | **200 + `{code:1006}`** | **401 + `{code:1006}`** | **OBSERVABLE DIFFERENCE（已签字 SD-01）** | → **DRIFT-ADMINAPI-003** ⇒ **预期差异，对拍必红，不是回归** |
| M-08 | 鉴权 | public 路径集合 | `{create, login}` | `{create, login}` | EQUIVALENT | `routes.go:16-30` vs `AdminJwtAuthPaths:44-47` |
| M-09 | 鉴权 | 中间件顺序（验签 → 黑名单） | Authorize → observability → CheckBlacklist | CheckBlacklist 在 JWT **之后** | EQUIVALENT | 三段取证见 §2.1；`AdminApiMvcPipelineTest` 有 4 条断言（非本轮） |
| M-10 | 鉴权 | 黑名单 fail-open | `err != nil` ⇒ 放行 | catch ⇒ `exists=null` ⇒ 放行 | EQUIVALENT | `checkblacklistmiddleware.go:35-36` vs `isBlacklisted:221-231` |
| M-11 | 鉴权 | Bearer 前缀两段取法 | 验签大小写不敏感 / 黑名单大小写敏感 | `BearerTokenExtractor` 两个方法 | EQUIVALENT | `oauth2.go:7-12` + `TrimPrefix` vs `BearerTokenExtractor:74-99` |
| M-12 | OP-03/04/10/11/13 | **operatorId 来源** | `ctx["uid"]` ⇒ **恒 0**，且**不校验 claim** | `claimedUID`（要求 `userId` 为 Long 且 > 0） | **SEMANTIC MISMATCH** | → **DRIFT-ADMINAPI-001**；N-4 只签了「不恒为 0」那一半 |
| M-13 | OP-05/06/12 | 身份来源 | `ctx["userId"]`，**无 `uid>0` 检查** | `claimedUID`，多一道 `uid<=0 ⇒ 1006` | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-001（同源） |
| M-14 | OP-06 | 响应前的一致性校验 | **无** | 3 道（空 Admin / uid 不匹配 / admin not found） | **SEMANTIC MISMATCH** | → **DRIFT-ADMINAPI-002** |
| M-15 | OP-06 | gRPC 兜底映射 | 单一 `default ⇒ 1015` | 通用表：`ALREADY_EXISTS⇒1001 / PERMISSION_DENIED⇒1016 / UNAUTHENTICATED⇒1006 / INVALID_ARGUMENT⇒500` | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-008（**唯一**能走到通用表的路径） |
| M-16 | OP-07 | `found` | api 层写死 `true` | `setFound(true)` | EQUIVALENT | `getuserlogic.go:59` vs `AdminGetUserService:67` |
| M-17 | OP-07/13 | `user` 字段缺失时的输出 | **恒输出零值 user 对象**（`omitempty` 对 struct 无效） | **字段整个消失** | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-005 |
| M-18 | OP-08 | 空结果的 `list` | `"list": null` | `"list": null` | EQUIVALENT | `getuserlistlogic.go:51-62` + `types.go:63` vs `AdminListUserService:72-79` |
| M-19 | OP-08 | `page` / `page_size` 缺省 | 只在**缺席**时填 1 / 20；显式 `0` 原样透传 | 同（`Long` 区分 null / 0） | EQUIVALENT | `unmarshaler.go:918` `opts.getDefault()` vs `pageOrDefault():110-112` |
| M-20 | OP-08 | `keyword=""` | **400**（form 通道丢空值） | **接受**（json 通道） | OBSERVABLE DIFFERENCE | 已在 `GetUserListReq:30-36` 留档（**A-03 的后果，已签字**） |
| M-21 | OP-07/08 | 参数通道 | **query string** | **JSON body** | OBSERVABLE DIFFERENCE | A-03（**已签字**）；`diff.py:352-353` 按端声明 method/通道 |
| M-22 | ALL | method | 4 个 GET + 1 个 DELETE + 8 个 POST | 全部 POST | OBSERVABLE DIFFERENCE | A-03（**已签字**） |
| M-23 | ALL | 路径前缀 | `/admincenter/v1` | `/admin/v1` | OBSERVABLE DIFFERENCE | A-03（**已签字**） |
| M-24 | OP-01/03/05/06/10/11/12 | `Internal` 映射 | 5003（update / reset / unban）/ 5001（其余） | 同 | EQUIVALENT | `GlobalExceptionHandler` 各 Service 逐一核对；`AdminSemanticDriftApiRegressionTest` 6 条（非本轮） |
| M-25 | OP-09 | `success` | 写死 `true` | 写死 `true` | EQUIVALENT | `logoutlogic.go:55-57` vs `AdminLogoutService:60` |
| M-26 | OP-03/04/10/11 | `success` | **透传** `rpcResp.Success` | **透传** | EQUIVALENT | 逐条核对；⚠️ 依赖 admin-rpc 会否设该字段 → §9 |
| M-27 | OP-12/13 | `success` | 写死 `true`（不透传 proto3 默认 false） | 写死 `true` | EQUIVALENT | `updatelogic.go:71` / `updateuserlogic.go:68` |
| M-28 | OP-02 | JWT 签发 | `exp = iat + AccessExpire`，`iat` 为当前秒 | 同 | EQUIVALENT | `jwt.go:7-14` vs `JwtUtil.getToken:12-23` |
| M-29 | OP-02 | HMAC key 长度 | 15 字节原串 | 32 字节 zero-pad | OBSERVABLE DIFFERENCE | **N-5（已签字）**：两侧 token 互不认 |
| M-30 | ALL | 序列化：`uid` 转字符串 | `json:",string"` | `getUid()` 返回 `String` | EQUIVALENT | `types.go:7` 等 vs DTO 各处 |
| M-31 | OP-06/12 | 序列化：`AdminInfo.email` | **恒输出**（无 omitempty） | 总是 `setEmail("")` | EQUIVALENT | A-02；`AdminApiSupport:81` |
| M-32 | OP-07/08/13 | 序列化：`UserInfo.email` | 空则**省略**（有 omitempty） | 空则**省略** | EQUIVALENT | `types.go:137` vs `AdminApiSupport:105` |
| M-33 | ALL | 序列化：`extra_info` | 空 map 省略 | 空 map 省略 | EQUIVALENT | `omitempty` on map；`AdminApiSupport:82,106` |
| M-34 | ALL | 序列化：显式 `null` vs 缺席（map 字段） | `null⇒400 mustn't be nil` / 缺席⇒通过 | 同（`extraInfoPresent` 标志） | EQUIVALENT | R2 已修；`GoStyleRequired.explicitNull:67-71` |
| M-35 | ALL | 标量强转 / 浮点截断 | number/bool→String **拒**；float→int **拒**（含 `1.0`） | 同（`GoStyleCoercion` 全局生效） | EQUIVALENT | R1 已修；⚠️ Jackson 3 类级 coercion 实测无效 ⇒ 只能全局（`GoStyleCoercion` 类注释「第三个坑」） |
| M-36 | ALL | 超时时长 | **3000ms**（yaml 未写，来自 `go-zero@v1.10.2/rest/config.go:55` 的 `json:",default=3000"`） | `observability.http-timeout: 3s` | EQUIVALENT | **消费点**：`TimeoutFromMillis(conf.Timeout)`；`application.yml:131-132` |
| M-37 | ALL | 超时的**包含范围** | 计时器在 Authorize **之后**起算 ⇒ **JWT 验签不计入** | 计时器在 `HttpMiddlewareFilter`(**+7**) 起算；验签在 `GoStyleJwtAuthFilter`(**+6**) ⇒ 同样**不计入** | **EQUIVALENT**（**2026-10-27 已闭环**） | → DRIFT-ADMINAPI-006 的 **② 半已修**：验签搬出预算 ⇒ 见 §12.2。黑名单 Redis 两侧都在预算**之内**（Go = route 级中间件最内层；RAG = interceptor 段）⇒ 本行**整体**等价 |
| M-38 | ALL | 超时响应 | `503` + `text/plain; charset=utf-8` + `nosniff` + 尾换行 | `503` + `text/plain; charset=utf-8` + 尾换行，**无 nosniff** | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-007（**代码在 `service/common`，非本组件文件**）<br>〔**2026-10-24 完整性复核补记 ①**：上表两边写的都是**源码字面量**；**线上字节** Tomcat 会把 `Content-Type` 重渲染成 `text/plain;charset=utf-8`（**分号后无空格**）⇒ 与 Go 的 `text/plain; charset=utf-8`（**有空格**）**逐字节不同**（归一化后相同）。**499/503 都走这条**；400 的同款残差已记在 §UNRESOLVED-002<br>〔**2026-10-24 完整性复核补记 ②**：**nosniff 已被 `align/message` 分支跨域修掉**（`HttpMiddleware.writeTimeoutResponse` 加了 `setHeader("X-Content-Type-Options","nosniff")`）⇒ **未合并前本条仍成立，合并后本条自动消失** —— 见 ADMINAPI-007 的处置栏〕 |
| M-39 | ALL | 中间件外层顺序 / 遥测面 | 签名无效的请求：**Sea 侧 `markHTTPResult` 观测数据不产生**（`Authorize` 短路 ⇒ 内层中间件不执行）；但 go-zero native `TraceHandler` 在**链首**，**仍产生 OTel server span** | 会产生（401 的观测数据与 span 都产生） | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-006 的 **① 半**（**2026-10-27 复核：原写「完全不产生 observability span」口径过宽；验签搬到 +6 后 **span 侧仍等价**（`Traceparent(+3)` 在其外层），但**观测数据侧仍不等价**）） |
| M-40 | 全部外部调用 | Retry | **无** | **无** | EQUIVALENT | 两侧 `application.yml` / `admincenter.yaml` 均无重试配置 |
| M-41 | 启动 | 下游依赖不可达 | `zrpc.MustNewClient` **启动即失败**（进程不启动） | Nacos + devh 懒连接，**进程能起**、每请求失败 | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-009 |
| M-42 | 全部 | 并发语义 | 无状态，goroutine-per-request | 无状态，**但**多一个 worker-pool 饱和 → `setStatus(503)` 无 body 分支 | UNKNOWN | Go 侧无对应分支；见 UNRESOLVED-004 |
| M-43 | 全部 | Prometheus 指标 | `admin_api_intercept_total{path,result}`：**2 个 label 名**（`path` / `result`）、**16 个组合**（login 3 + banuser 2 + unbanuser 2 + delete 2 + resetuserpassword 2 + updateuser 2 + invite/create 3） | Micrometer 同名 Counter + 同 2 个 tag，**16 个组合逐条一致**；但 exposition 上**多一个 `application` label** | IMPLEMENTATION DIFFERENCE | 〔**2026-10-24 复核订正**：原写「17 个具名 label」——**label 名是 2 个、组合数是 16**；且原注「`_total` 命名归一化未实测」已被 UNRESOLVED-005 ② 关掉（实测同名）。**剩下的真实差异是 RAG 多一个 `application` tag**（`application.yml` 的 `management.metrics.tags`）〕 |
| M-44 | ALL | 事务边界 | api 层无事务 | api 层无事务 | EQUIVALENT | 13 个 logic / 13 个 Service 均无事务注解 |

---

## 5. External Interaction & Error Flow

### 5.1 出向交互（4 个）

| 外部系统 | Operation | Input | Result 语义 | 失败是否阻塞 | Side Effect |
|---|---|---|---|---|---|
| **admin-rpc** (gRPC) | 13 个 admin RPC | proto 请求 | `OK` / `StatusRuntimeException` | **阻塞**（BlockingStub，无 deadline、无 retry） | 业务副作用由 rpc 承担 |
| **Redis** | `EXISTS admin:jwt_blacklist:<token>` | token 串 | `true` / `false` / **异常** | **不阻塞（fail-open）** | 无 |
| **Prometheus** | counter.inc() | 固定 tag | — | **会阻塞/可失败**（未捕获） | 无 |
| **OTel** | span | — | — | 否 | 无 |

⚠️ **gRPC 无 deadline**：Go 的 `zrpc` client 与 RAG 的 devh BlockingStub **都未设 deadline**。
真正的总时限来自外层 3s（M-36）。两侧一致。

### 5.2 错误流三族对照

```
族① 请求解析失败
  Go   : httpx.Parse err → httpx.ErrorCtx → http.Error(400) → text/plain
  RAG  : Jackson err → HttpMessageNotReadableException → 400 text/plain
         GoRequiredFieldException      → 400 text/plain
  等价 : 形状等价（状态码 + Content-Type + nosniff + 尾换行）；**文本内容**来自不同框架
         ⇒ 已在 DRIFT-006 确立「形状对齐、文本不对齐」为有意边界

族② 业务失败
  Go   : logic return (nil, code) → httpx.OkJson{code,msg,data:null} → HTTP 200
  RAG  : Service throw BusinessException(code) → handleBusiness → ResponseEntity(200, Response)
  等价 : ✅ 逐条一致（见 M-24；三个 5003/九个 5001 的不对称已逐条复刻）

族③ 鉴权失败
  Go   : Authorize 401 空 body  /  CheckBlacklist 401 {code:401,...}
  RAG  : AuthenticationException → 401 {code:1006,msg,data:null}
  等价 : ❌ 状态码一致，**业务码与 msg 与 Content-Type 三处不一致** → DRIFT-ADMINAPI-004
         且「签名有效但 claim 无效」这条分支两侧根本不在同一层 → DRIFT-ADMINAPI-003
```

---

## 6. 逐维度结论

| 维度 | 结论 | 依据 |
|---|---|---|
| **State**（§12） | EQUIVALENT | api 层两侧都无状态；`IdentityResolver` / `AdminApiSupport` 均无实例字段 |
| **Cache**（§13） | N/A（无缓存层）；Redis 仅用于黑名单 EXISTS，语义已对齐 | M-10 |
| **Auth**（§14） | **NOT EQUIVALENT** | DRIFT-ADMINAPI-001/002/003/004 |
| **Transaction**（§15） | N/A（api 层无事务） | M-44 |
| **Concurrency**（§16） | 基本等价 + 1 个 RAG 独有分支 | M-42 / UNRESOLVED-004 |
| **Timeout**（§17） | 时长等价、**范围已等价**（2026-10-27：验签移出 3s 预算） | M-36 / M-37 / DRIFT-ADMINAPI-006 |
| **Retry**（§18） | EQUIVALENT（两侧都无） | M-40 |
| **Serialization**（§19） | 大部分等价，2 处 OBSERVABLE | M-30~M-35 / DRIFT-ADMINAPI-005 / M-20 |
| **Config**（§20） | EQUIVALENT | secret/expire/port 逐项对齐 `application.yml:80-83,46-52` |
| **Lifecycle**（§21） | NOT EQUIVALENT（启动就绪语义） | DRIFT-ADMINAPI-009 |

---

## 7. Semantic Drifts

> 本轮新增 10 条（`DRIFT-ADMINAPI-001…010`）。
> 分类口径：SEMANTIC MISMATCH 3 条、OBSERVABLE DIFFERENCE 6 条、非运行时语义 1 条（#010，文档漂移）。

---

### DRIFT-ADMINAPI-001 · 5 个端点的身份解析层：Go 恒 `OperatorId=0` 且不校验 claim，RAG 会因 claim 无效而拒绝

- **Component**：admin-api / OP-03 banuser、OP-04 delete、OP-10 resetuserpassword、OP-11 unbanuser、OP-13 updateuser
- **Category**：Authentication / Identity Source
- **Classification**：**SEMANTIC MISMATCH**　**Risk**：HIGH
- **Go Behavior**：
  ```
  banuserlogic.go:36-39   var adminUid int64
                         if uidNumber, ok := l.ctx.Value("uid").(json.Number); ok { adminUid, _ = uidNumber.Int64() }
  deletelogic.go:36-39 / unbanuserlogic.go:37-40 / resetuserpasswordlogic.go:37-40 / updateuserlogic.go:36-39  ← 同款
  ```
  `uid` 取不到时**不报错**，静默保持 `adminUid = 0`，并把这个 0 当作 `OperatorId` 发给 admin-rpc。
- **为什么 `uid` 永远取不到（三段取证，全部指向消费点）**：
  1. `service/user/common/jwt/jwt.go:8-11` —— admin 侧唯一发 token 的地方，只写 `exp` / `iat` / `userId` 三个 claim；
  2. go-zero `rest/handler/authhandler.go:72-78` —— `for k, v := range claims` 逐个 `context.WithValue(ctx, k, v)`，
     **key 就是 claim 名**；`jwt.go` 没有 `uid` claim ⇒ ctx 里不会有 `uid` 这个 key；
  3. 同一段 switch 过滤掉 `aud/exp/jti/iat/iss/nbf/sub` ⇒ ctx 里实际**只有 `userId`**（值类型 `json.Number`，
     因为 `go-zero@v1.10.2/rest/token/tokenparser.go:123` `newParser()` = `jwt.NewParser(jwt.WithJSONNumber())`）。
- **RAG Behavior**：这 5 个 Service 都调 `IdentityResolver.claimedUID`（`IdentityResolver.java:93-106`）：
  ```java
  Object v = request.getAttribute("userId");
  if (!(v instanceof Long)) throw new ErrInvalidClaimException(...);   // 缺失 / 类型不对 ⇒ 拒绝
  long uid = (Long) v;
  if (uid <= 0) throw new ErrInvalidClaimException(...);                // ≤0 ⇒ 拒绝
  ```
  catch 后一律 `BusinessException(ERROR_TOKEN_RUNTIME)` ⇒ **HTTP 200 + `{code:1006}`，且完全不发 RPC**。
- **Difference**：
  | 输入 | Go | RAG |
  |---|---|---|
  | 正常登录 token（`userId` 为正） | `OperatorId = 0` | `OperatorId = <真实 uid>` |
  | 签名有效但 `userId` claim 缺失 | 照常发 RPC，`OperatorId = 0` | `{code:1006}`，**不发 RPC** |
  | `userId = 0` 或负数 | 照常发 RPC，`OperatorId = 0`（或负数） | `{code:1006}`，**不发 RPC** |
- **Why It Matters**：Go 侧这 5 条路径**不存在 claim 有效性分支**；RAG 多了一条拒绝路径 + 一个真实 operatorId。
- **与 N-4 的关系（必须一起看，否则会漏）**：`admin/_shared.md` N-4 签的是「**`operator_id` 不恒为 0**」
  （影响面 = OTel span 的 `audit.operator_id`）。**N-4 没有覆盖本条的后两行** ——
  「Go 不校验 claim / RAG 会因 claim 无效而 1006」是**另一条**，它改变的是
  **是否发出 RPC**，而不只是审计字段。**不能拿 N-4 的签字当作本条已签字。**
- **Evidence strength**：`静态源码·双侧`（三段均读到库源码的**写入侧与消费侧**）
- **Recommended Verification**：真 PG + 双端活体对拍 —— 用一个**签名有效但 `userId` 缺失/为 0** 的 token
  分别打两侧的 `/admin/banuser`，比对「是否发出 RPC」（admin-rpc 侧看访问日志）与 HTTP 响应。
  ⚠️ **本轮未跑**。
- **需合并判读**：`admin/rpc.md`（这 5 个方法的 `operatorId` 在下游是否参与任何判定）。

---

### DRIFT-ADMINAPI-002 · `getself` 多出 Go 没有的两道身份一致性校验

- **Component**：admin-api / OP-06 getself
- **Category**：Result Semantics / Extra Validation
- **Classification**：**SEMANTIC MISMATCH**　**Risk**：MEDIUM
- **Go Behavior**：`getselflogic.go:62-69` 拿到 `rpcResp.Admin` **直接**构造响应，零校验。
  若 admin-rpc 返回未 set 的 `Admin`，Go 输出
  `{"code":200,"msg":"OK","data":{"admin":{"uid":"0","username":"","email":""}}}`。
- **RAG Behavior**：`IdentityResolver.resolveAdmin`（`IdentityResolver.java:128-137`）多两道：
  ```java
  if (admin == null || admin.equals(AdminInfo.getDefaultInstance())) throw new ErrAdminNotFoundException(...); // ⇒ 1003
  if (admin.getUid() != uid)                              throw new ErrIdentityMismatchException(...);       // ⇒ 5001
  ```
- **Difference**：Go `200 + 零值 admin` ⇢ RAG `1003`；Go `200 + uid 不一致的 admin` ⇢ RAG `5001`。
- **Why It Matters**：这是典型的**「更严格的 Java 写法」**（§26 COUNTERFACTUAL ⇒ HIGH-RISK DRIFT 形态）：
  一个普通 Spring 开发者会自然地加上这两道一致性检查，而 Go 侧根本没有这个层。
- **Evidence strength**：`静态源码·双侧`
- **Recommended Verification**：取决于 admin-rpc 是否可能返回空 `Admin` 或 uid 不一致
  ⇒ 见 §9 UNRESOLVED-001。**在该点确定前不得改任一侧。**

> 🟢 **2026-10-27 降级订正（§2 结论回填）**：UNRESOLVED-001 已由 admin-rpc 侧答复 ⇒
> **「admin-rpc 会不会返回未 set 的 `Admin`」＝ 不会**（Go `getselflogic.go:44-51` 总是构造 `Admin: &pb.AdminInfo{…}`；
> RAG `AdminServiceGrpcImpl.toProtoAdmin` 同样总是 set）⇒
> ⇒ **本条的前提不可达 ⇒ 实际无可观察差异** ⇒
> **降级为「实现差异（防御性加固）」**，**不改实现**（那两道校验留着 —— 它们是防御性的，只是前提已被证伪）。
> ⇒ ⚠️ **分类字段保留原值 `SEMANTIC MISMATCH` 不改写**（本条正文是 2026-10-22 的原始判定，
> 订正以本注记形式留痕）⇒ **读本条时以本注记为准**。
> ⇒ 证据强度：前提不可达这一点有**双侧活体对拍**背书（gRPC 18 用例的 payload 归一化含 `hasUser` / `hasAdmin`）
> ⇒ **不是静态推导**。

---

### DRIFT-ADMINAPI-003 · 签名有效但 `userId` claim 缺失/非数字：Go 200+1006，RAG 401+1006

> ✅ **2026-10-24 用户拍板：本条不是「待修漂移」，而是 SD-2026-10-23-01 的可观察后果。**
> **零代码改动**，只订正归类。
>
> **为什么它已被签字覆盖（本条此前一直标「未经签字」，那个判定是错的）**：
> `docs/Intentional_Deviations/users/admin/_shared.md` 的 **N-4 条目末尾**写得很明确：
>
> > 「Go『恒 0 + **不校验 claim**』的**校验缺失**那半边，
> > 已由 **SD-2026-10-23-01** 单独签字（RAG 选择严格校验）。**N-4 只管取值，不覆盖校验。**」
>
> 而 SD-2026-10-23-01 原文（`_cross/signed-deviations.md`）写的是：
>
> | 项 | 内容 |
> |---|---|
> | **Go 行为** | 操作者 id 恒为 `0`，且**不校验** JWT 里有没有对应 claim |
> | **RAG 行为** | 从 JWT claim 取操作者并**严格校验**，缺失即拒绝 |
> | **签字结论** | **保留 RAG 的严格校验**。Go 的「恒 0 + 不校验」是安全缺口而不是业务语义 —— 复刻它等于把一个已知弱点也搬过来 |
> | **⚠️ 代价** | 两侧**可观察行为确实不同**：Go 接受而 RAG 拒绝的请求存在。任何跨实现对拍在这条上都会红，**属预期差异，不是回归** |
> | **⚠️ 不得做的事** | 不要为了让对拍变绿而给 RAG 放宽校验。若日后 Go 侧补上校验，则应反向收敛 RAG，而不是保持差异 |
>
> ⇒ **本条正是「Go 接受而 RAG 拒绝的请求」的一个具体实例** ⇒ 在签字范围内。
> ⇒ 对拍看到这条红，**先确认是不是 SD-01**，不要当新回归去「修」。
>
> **2026-10-24 用户明确拍板**：不撤销 SD-01，不改代码，保留 RAG 的严格校验。
>
> ---
>
> **以下为原始审计记录（保留原文）**：
- **Component**：admin-api / OP-05、OP-06、OP-12 的鉴权失败分支
- **Category**：Error Identity / HTTP Status
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：MEDIUM（业务码相同，状态码不同）
- **Go Behavior**：`Authorize`（`authhandler.go:65-69`）只要求 `tok.Claims.(jwt.MapClaims)` 转换成功，
  **不检查任何 claim 的存在性或取值** ⇒ 一个「签名有效但无 `userId`」的 token 能穿过鉴权，
  然后由 Logic 自己发现：`getselflogic.go:36-40` / `updatelogic.go:36-40` / `createinvitelogic.go:35-40`
  返回 `nil, errmsg.ErrorTokenRuntime` ⇒ `httpx.OkJson` ⇒ **HTTP 200 + `{code:1006}`**。
- **RAG Behavior**：`AdminJwtSignatureVerifier:101-113`（**2026-10-27 起**该判据随验签搬进 `auth/` 包；
  修前位置为 `AdminJwtAuthInterceptor:165-175`）在**鉴权层**就判 `userId` claim：
  ```java
  Object claim = jws.getPayload().get("userId");
  if (claim instanceof Number) { ... } else { throw new AuthenticationException(1006, ...); }
  ```
  ⇒ `GlobalExceptionHandler:60-69` ⇒ **HTTP 401**。
- **Difference**：同一输入，两侧业务码都是 `1006`，但 **HTTP 状态码 200 vs 401**。
  客户端 SDK 若按状态码分流，这一条会走不同分支。
- **Why It Matters**：`AGENTS.md` §1.1 的 11 处历史回归里就有「HTTP 状态码语义变更」。
  这次不是「全改成 200」，而是**反向的一处收紧**，同样未经签字。
- **Evidence strength**：`静态源码·双侧`
- **Recommended Verification**：构造一个「用正确 secret 签、但 payload 无 `userId`」的 token，两侧各打一次
  `/admin/v1/getself`。⚠️ 本轮未跑。

---

### DRIFT-ADMINAPI-004 · 黑名单 401 的业务码从 Go 的 `401` 变成 RAG 的 `1006`

- **Component**：admin-api / 全部 11 个受保护端点的鉴权失败
- **Category**：Error Identity / Serialization
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：MEDIUM
- **Go Behavior**（`checkblacklistmiddleware.go:34-41`）：
  ```go
  w.Header().Set("Content-Type", "appliaction/json;charset=utf-8")   // ← Go 侧拼写错误
  w.WriteHeader(http.StatusUnauthorized)
  w.Write([]byte(`{"code":401,"msg":"登录已失效,请重新登陆"}`))      // ← 无 data 字段
  ```
- **RAG Behavior**：`GlobalExceptionHandler.handleAuthentication:66-68` ⇒
  `401` + `{"code":1006,"msg":"TOKEN已过期","data":null}` + `application/json`。
- **Difference（3 处）**：① 业务码 `401` → `1006`；② `msg` 完全不同；
  ③ `Content-Type` 从 `appliaction/json`（Go 的错别字）变成合规的 `application/json`。
- **⚠️ 留档口径与实际输出不一致**：`AuthenticationException` 的 javadoc 只声明
  「**两处都是 401，这是必须对齐的部分；两处 body 形状不同是 Go 自身的不一致，本端统一成完整 envelope**」。
  它**只覆盖了「形状」与「状态码」，从未声明「业务码 401 → 1006」**。
  按 `AGENTS.md` §3.3.2，**声明只覆盖一部分、实际改变了另一部分** ⇒ 属于未签字的语义改变。
- **Counterfactual**：一个普通 Spring 开发者会把两种鉴权失败统一成同一个异常类型 + 同一个业务码 ——
  这正是 §26 说的「看起来完全合理」的漂移形态。
- **Evidence strength**：`静态源码·双侧`
- **Recommended Verification**：把真实 token 写进 Redis 的 `admin:jwt_blacklist:<token>`，
  两侧各打一次，比较**响应体的 `code` 字段**。⚠️ 本轮未跑。

---

### DRIFT-ADMINAPI-005 · 下游未 set `user` 时：Go 恒输出零值 user 对象，RAG 让字段整个消失

- **Component**：admin-api / OP-07 getuser、OP-13 updateuser
- **Category**：Serialization / Null vs Missing
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：LOW-MEDIUM
- **Go Behavior**：
  - `types.go:72` `User UserInfo \`json:"user,omitempty"\``（getuser）、`types.go:130` `User UserInfo \`json:"user"\``（updateuser）；
  - ⚠️ **`encoding/json` 的 `omitempty` 对 struct 类型不生效**（只对 false/0/nil 指针/接口/空 array/slice/map/string 生效）
    ⇒ 两侧 Go **都恒输出** `user` 字段；updateuser 无 omitempty 更显然。
  - `getuserlogic.go:52-58` 无条件构造 `types.UserInfo{...}`，下游没给就是零值。
- **RAG Behavior**：
  ```java
  // AdminGetUserService:68-70 / AdminUpdateUserService:94-97
  if (rpcResp.hasUser()) { resp.setUser(support.fromProto(rpcResp.getUser())); }
  ```
  配 `GetUserResp.user` / `UpdateUserResp.user` 上的 `@JsonInclude(NON_NULL)`
  ⇒ 条件不成立时**字段整个消失**。
- **Difference**：Go `{"user":{"uid":"0","username":"","status":0}}` ⇢ RAG（无 `user` 键）。
- **Why It Matters**：这正是 spec §19 点名的 `null` vs `empty` vs `missing` 跨语言偏差；
  与 §3.3.3 的「`@TableField(typeHandler)`」不同类，但机理相同——**tag/注解名 ≠ 实际语义**。
- **Evidence strength**：`静态源码·双侧`（`omitempty` 对 struct 无效这一条是 Go 语言层事实，
  **本轮未跑探针实证**，一并记入 §9 UNRESOLVED-003）
- **触发条件依赖** admin-rpc 是否会返回未 set 的 `user` ⇒ §9 UNRESOLVED-001。

> 🟢 **2026-10-27 降级订正（§2 结论回填）**：UNRESOLVED-001 已由 admin-rpc 侧答复 ⇒
> **「`GetUser` / `UpdateUser` 会不会返回未 set 的 `user`」＝ 不会**
> （Go `getuserlogic.go:42-50` / `updateuserlogic.go:106-113` 总是构造 `User: &pb.UserInfo{…}`；
> RAG `toProtoUser` 同样总是 set）⇒
> ⇒ **本条的前提不可达 ⇒ 实际无可观察差异** ⇒
> **降级为「实现差异（防御性加固）」**，**不改实现**
> （⚠️ **不要删** `if (rpcResp.hasUser())` 那个守卫 —— 它是防御性的，只是它的「前提」已被证伪）。
> ⇒ ⚠️ **分类字段保留原值 `OBSERVABLE DIFFERENCE` 不改写**（本条正文是 2026-10-22 的原始判定，
> 订正以本注记形式留痕）⇒ **读本条时以本注记为准**。
> ⇒ 证据强度：前提不可达这一点有**双侧活体对拍**背书（gRPC 18 用例的 payload 归一化含 `hasUser` / `hasAdmin`）
> ⇒ **不是静态推导**。
> ⇒ 🟡 **连带**：`UNRESOLVED-003`（Go 的 `omitempty` 对 struct 无效这一条）已于 2026-10-24 **单独关闭**
> ⇒ 那个语言层事实本身是**已证**的 ⇒ **本条不再是「前提待定」，而是「前提不可达」**。

---

### DRIFT-ADMINAPI-006 · 中间件外层顺序不同 ⇒ 401 请求的遥测面与 3s 预算包含范围都不同

> **状态（2026-10-27）**：本条含**两个可分离的子项** ——
> **②「超时范围」已修**（验签搬进 `GoStyleJwtAuthFilter(+6)` ⇒ 移出 3s 预算 ⇒ 与 Go 一致）；
> **①「遥测面」仍开放**（401 请求仍产生 Sea 侧不产生的观测数据）。
> ⇒ **不要按「整条已修/整条未修」读本条** ⇒ 逐半读 M-37 / M-39。

- **Component**：admin-api / 全部端点
- **Category**：Lifecycle / Timeout Scope / Observability
- **Classification**：OBSERVABLE DIFFERENCE（**① 仍成立**）　**Risk**：LOW-MEDIUM
- **Go Behavior**：`admincenter.go:34` `httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)`
  → `admincenter.go:36` `server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, ...))`
  ⇒ 进入 `ng.middlewares`，在 `engine.go:110-112` 里**排在 `appendAuthHandler` 之后** append
  ⇒ 位置在 **JWT 内层**、黑名单外层。
- **RAG Behavior（2026-10-27 起）**：`HttpMiddlewareFilter:37` `@Order(Ordered.HIGHEST_PRECEDENCE + **7**)` 是 **servlet Filter**，
  包住整个 `DispatcherServlet` ⇒ 在 `AdminJwtAuthInterceptor`（`WebMvcConfig:60-62`）**外层**；
  **验签已搬进域内 `auth/GoStyleJwtAuthFilter.java:89` `@Order(+6)`** ⇒ **在 3s 预算之外**。
  ⚠️ 共享层槽位实测值见 §3.1（注：`HttpMiddlewareFilter` 的 `@Order` 原写 `+5`，**已过期** ⇒ 实测为 `+7`）。
- **Difference**：
  1. **遥测面（仍成立）**：一个签名无效的请求，Go 在 `Authorize` 就短路 ⇒ **Sea 侧 `markHTTPResult` 的观测数据不产生**
     （`observability/http.go:48`/`:78` 都不执行）；但 go-zero native `TraceHandler`（`go-zero@v1.10.2/rest/handler/tracehandler.go:26-60`）
     在**链首**、用 `tracer.Start` 真实创建 **OTel server span** ⇒ **401 也会产生 span**。
     RAG 侧 `HttpMiddlewareFilter(+7)` 在**验签 Filter(+6) 之后**执行 ⇒ 401 的观测数据**仍产生**；
     ⚠️ **span 侧已等价**（`GoStyleTraceparentFilter(+3)` 在验签 Filter 之外 ⇒ 401 同样产生 SERVER span）。
  2. **超时范围（✅ 已修，2026-10-27）**：Go 的 3s 计时器在 Authorize **之后**起算（JWT 验签耗时**不计入**）；
     RAG 修前 3s 在 JWT 拦截器**外层**起算 ⇒ 验签计入预算。
     **修后**验签在 `GoStyleJwtAuthFilter(+6)`、`HttpMiddlewareFilter(+7)` 在其**内层** ⇒ **验签不计入**预算 ⇒ 与 Go 一致。
     ⚠️ **黑名单 Redis EXISTS 两侧都计入**（Go 侧黑名单被 `WithMiddleware` 直接包在 `route.Handler` 上、
     在计时器**内层**；RAG 侧在拦截器阶段 2、也在计时器内层）⇒ **差异原本就仅在 JWT 验签这一段**。
- **Evidence strength**：`静态源码·双侧`（`engine.go:110-112` 与 `@Order` 行号均已核对）⇒ **② 已升到 L3（真启动）**，见 §12.2
- **Note**：不影响任何业务判定，只影响遥测与极端超时边界。**① 不列为待办**，仅留档。

> #### ⚠️ 2026-10-24 复核补记：**「Go 的 3s 从哪起算」有个必须绕开的陷阱**
>
> 上文 Go Behavior 只写了 `server.Use(...)` 的位置，**没写 native Timeout 已被关掉** ——
> 补上这一环，结论才站得住（否则会读成「计时器在 Authorize 外层 ⇒ 差异不成立」）：
>
> | 环节 | 出处 | 作用 |
> |---|---|---|
> | ① 关掉 native Timeout | `admincenter.go:34` → `observability/http.go:30-34`：`conf.Middlewares.Timeout = false` | 必须在 `admincenter.go:35` `rest.MustNewServer(c.RestConf)` **之前**调用（值拷贝 ⇒ 先改后传才生效） |
> | ② native Timeout 不进链 | `go-zero@v1.10.2/rest/engine.go:154-155`：`if ng.conf.Middlewares.Timeout { chn = chn.Append(handler.TimeoutHandler(...)) }` | 条件为 false ⇒ **`TimeoutHandler` 不在链上** ⇒ native 那条链（Trace→…→Shedding→**Timeout**→Recover）**不参与**计时 |
> | ③ 真正的计时器 | `observability/http.go:36-92` `NewHTTPMiddleware`（`context.WithTimeout`） | 经 `server.Use` 进 `ng.middlewares` |
> | ④ 挂载点 | `go-zero@v1.10.2/rest/engine.go:110-112`：`for _, middleware := range ng.middlewares { chn = chn.Append(...) }` | `chain.Append` **接在末尾**（`go-zero@v1.10.2/rest/chain/chain.go:43-45`：请求流 m1→m2→m3→m4，**列表首 = 最外层**）⇒ `ng.middlewares` 比 `Authorize`（`engine.go:70-84` append）**更内层** |
> | ⑤ 黑名单在哪 | `go-zero@v1.10.2/rest/server.go:222-235` `WithMiddleware`：`Handler: middleware(route.Handler)` | 直接包在 **route.Handler** 上 ⇒ **最内层**，在计时器内 |
>
> ⇒ **最终 Go 链（外→内）**：Trace → Log → Prometheus → MaxConns → Breaker → Shedding → Recover → Metrics →
> **Authorize(JWT)** → **observability(3s 计时)** → **黑名单** → handler
> ⇒ **RAG 链（外→内，实测 2026-10-27）**：Traceparent(+3) → MaxConns(+4) → Breaker(+5) → CpuShedding(+6) →
> **验签 Filter(+6)** → **observability/3s(+7)** → MaxBytes(+8) →
> DispatcherServlet → **Authorize 结果消费 + 黑名单** → controller
>
> ⚠️ RAG 侧的 `HttpMiddlewareFilter` **不是**最外层（**+3/+4/+5/+6** 在它外面），但那些**不计时** ⇒ 预算范围结论不变。
> **结论（2026-10-27 更新）：② 超时范围已修（验签搬到 +6 ⇒ 出预算）；① 遥测面仍成立。**
>
> **方法论**：`*WithNative*` / `Disable*` 这类**关掉框架自带机制**的调用，
> 是「读框架源码推中间件顺序」时最容易漏掉的一环 ——
> 漏了它，就会把「框架原生 Timeout 在 Authorize 外层」误当成生效，
> 从而把一个**真实存在的差异**误判成「不成立」。
> ⇒ 判定某条框架机制是否生效，必须读到**消费该开关的那段控制流**（AGENTS.md §3.3.1 第 1 条）。

---

### DRIFT-ADMINAPI-007 · 503 超时响应缺 `X-Content-Type-Options: nosniff`

- **Component**：admin-api / 超时与客户端断连分支（**实现落在 `service/common`**）
- **Category**：Serialization / Response Headers
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：LOW
- **Go Behavior**：`observability/http.go:88` `http.Error(w, httpTimeoutBody, statusCode)`
  ⇒ Go 标准库 `net/http.Error`（**本机 Go 1.25.11 `src/net/http/server.go:2337-2355`**）实际做 **5 件事**：
  ① `h.Del("Content-Length")` ② `h.Set("Content-Type", "text/plain; charset=utf-8")`
  ③ `h.Set("X-Content-Type-Options", "nosniff")` ④ `w.WriteHeader(code)` ⑤ `fmt.Fprintln(w, error)`（补尾部 `\n`）。
- **RAG Behavior**：`HttpMiddleware.writeTimeoutResponse:419-425` 做 **4 件事**：
  `setStatus` + `setContentType("text/plain; charset=utf-8")` + 写 `body` + 写 `'\n'`（+ `flush`），
  **没有 nosniff**。
- **Difference**：503/499 响应少一个 `X-Content-Type-Options` 头。
  ⚠️ 另两处**不可观察**：Go 的 ① `Del("Content-Length")` 在 servlet 下由容器自行计算（无对应可观察差异）；
  ⑤ 的「尾部 `\n`」两侧**都产出**（RAG 分两次 `write`，字节序列相同）。
- **🔴 2026-10-24 复核订正 —— 根因不是「实现忘了加 header」，而是「注释把 Go 行为数错了」**：
  本条原注释写的是 Go 做「**三件事**」、RAG 做「**两件**」，**两个计数都不对**
  （Go 实为 5 件、RAG 实为 4 件；原文列举的 4 项里把 `WriteHeader` 与「写 body」合并计数了）。
  ⇒ **错误的不是代码，是那句描述 Go 行为的注释** ——
  它少算了 `Del("Content-Length")` 与 `WriteHeader`，让人以为「三件事」已经覆盖了 nosniff 之外的语义，
  从而把「缺 nosniff」读成「RAG 少做了一件」，而实际是**两侧动作数不同、且 RAG 恰好少了 ③ nosniff**。
  ⇒ 教训：**「N 件事」这种计数必须对着源码逐行数一遍**，注释里的数字一旦错了，
  根因判断会整体偏一格（AGENTS.md §3.3.2 同源风险）。
- **⚠️ 归属**：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`
  **不在本组件的修改范围内**（本会话只允许改两个台账文件）⇒ **只留档，不实施**。
  影响面是**所有 api 组件**，不是 admin-api 独有。
- **Evidence strength**：`静态源码·双侧`

> #### ⚠️ 2026-10-24 完整性复核补记：**本条已被别的域分支跨域修掉（未合并）**
>
> `align/message` 分支在 `HttpMiddleware.writeTimeoutResponse` 里补了
> `resp.setHeader("X-Content-Type-Options", "nosniff")`（+ 一段说明为什么 Tomcat 吃掉分号后空格），
> 并把它纳入 `exp-http-headers-message.sh` 的对拍。
> ⇒ **合并后本条的 nosniff 部分自动消失**（`service/common` 是共享模块，改一处所有 api 组件受益）。
>
> ⇒ **处置纪律**：
> 1. **合并前**：本条仍成立（RAG 侧确实缺 nosniff）；
> 2. **合并后**：**不要再当新回归去「修」**，也不要从台账里删 —— 应当改成「已由 `align/message` 跨域修复」并标注提交；
> 3. **本域不重复改**（§5.3：工作树只写自己域）⇒ 本轮**零代码改动**。
>
> #### ⚠️ 同批复记：**503/499 的 `Content-Type` 还有一条「空格残差」，本条原先没记**
>
> Go 的 `http.Error` 逐字写死 `text/plain; charset=utf-8`（**分号后有空格**）；
> RAG 的 `writeTimeoutResponse` 源码写的也是同一个字符串（**逐字相同**），
> 但 **Tomcat 会把它重渲染成 `text/plain;charset=utf-8`（无空格）** ⇒ **线上字节不同**。
> ⇒ 400 的同款残差已在 §UNRESOLVED-002 实测并记录（归一化后一致、逐字节不一致），
> **503/499 这条本轮补上**（证据：同一 Tomcat 的同一条重渲染路径 + `align/message` 分支在
> **同一个 `writeTimeoutResponse` 方法**上的实测结论）。
> ⚠️ **归类**：这是**框架限制**（Tomcat 的 `setContentType` 会解析成 `MediaType` 再序列化），
> **不是 RAG 写错了** ⇒ 与 Go 的 `text/plain; charset=utf-8` **无法逐字节一致**，
> 任何写法都产出不了那个空格 ⇒ 归「框架限制」，**不必修**。

---

### DRIFT-ADMINAPI-008 · `getself` 的 gRPC 兜底映射与 Go 的 per-endpoint switch 不一致

- **Component**：admin-api / OP-06 getself
- **Category**：Error Mapping
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：LOW（触发概率低）
- **前提**：**13 个 Service 里只有 `AdminGetSelfService` 会把 `StatusRuntimeException` 抛给 advice**
  （其余 12 个都自带完整 switch 并转成 `BusinessException`）⇒ 通用表
  `GlobalExceptionHandler.mapRPCStatusToCode:84-92` 的**唯一可达路径就是 getself**。
- **Go Behavior**（`getselflogic.go:52-60`）：`NotFound→1003 / Internal→5001 / **default→1015**`。
- **RAG Behavior**：NOT_FOUND 被 `resolveAdmin` 转成 `ErrAdminNotFound` ⇒ 1003（✅ 一致）；
  INTERNAL 被 rethrow ⇒ 通用表 5001（✅ 一致）；
  但 **`ALREADY_EXISTS→1001` / `PERMISSION_DENIED→1016` / `UNAUTHENTICATED→1006` / `INVALID_ARGUMENT→500`**，
  Go 全部落 `default→1015`。
- **Difference**：4 种 gRPC code 的业务码不同。
- **Evidence strength**：`静态源码·双侧`
- **需合并判读**：admin-rpc 的 `GetSelf` 实际会抛哪几种 code（§9 UNRESOLVED-001）。
  **在 admin-rpc 侧未确认前不得改通用表**（改通用表会波及 12 个已对齐的端点）。

> #### ⚠️ 2026-10-24 完整性复核补记：本条之外还有**第 5 种** code，且它是 panic 路径上真实会出现的
>
> 上文列的 4 种是**业务异常**路径。**还有 `Status.UNKNOWN`**：
> admin-rpc **没有 recover 拦截器** ⇒ handler panic 时由 grpc-java 兜底，
> 产出的是 **`UNKNOWN` + `"Application error processing RPC"`**（不是 Go 的 `INTERNAL` + `"panic: …"`）
> ⇒ 落进 `mapRPCStatusToCode` 的 **default** ⇒ **业务码 1015**；
> 而 Go 侧 `codes.Internal` ⇒ `errmsg.ErrorServerCommon` ⇒ **5001**。
>
> ⇒ **同一个 panic，两侧业务码 5001 vs 1015。**
> ⇒ 完整证据链（含 Go 侧 7 个拦截器 vs RAG 侧 1 个的对照）⇒
> [`DRIFT-ARPC-009`](./rpc.md)（在 `rpc.md` 的「2026-10-24 完整性复核轮」§1）。
> ⚠️ `align/message` 分支已修（recover 精确复刻 Go 的 `INTERNAL + "panic: "`）⇒ **合并后自动消失**。

> 🟢 **2026-10-27 降级订正（§2 结论回填）**：UNRESOLVED-001 已由 admin-rpc 侧答复 ⇒
> **`GetSelf` 实际只会抛 `NOT_FOUND` 与 `INTERNAL`**
> （Go `getselflogic.go:36-41` 只有这两个分支；RAG `AdminDomainService.getSelf:243-258` 同）
> ⇒ ⇒ **那 4 种 code（`ALREADY_EXISTS` / `PERMISSION_DENIED` / `UNAUTHENTICATED` / `INVALID_ARGUMENT`）不可达**
> ⇒ 通用表 `mapRPCStatusToCode` 的那 4 行**永远走不到**
> ⇒ ⇒ **本条（4 种业务 code 那部分）的前提不可达 ⇒ 实际无可观察差异** ⇒
> **降级为「实现差异（防御性加固）」**，**不改实现**
> （⚠️ 通用表本身**不要动** —— 它被 12 个已对齐的端点共用）。
> ⇒ ⚠️ **分类字段保留原值 `OBSERVABLE DIFFERENCE` 不改写**（本条正文是 2026-10-22 的原始判定，
> 订正以本注记形式留痕）⇒ **读本条时以本注记为准**。
> ⇒ 🔴 **但第 5 种 code（`UNKNOWN`，panic 路径）不在本次降级范围内** ——
> 它**确实可达**，且是**另一条**（`DRIFT-ARPC-009`，在 `rpc.md`）⇒ **本降级不销它**。

---

### DRIFT-ADMINAPI-009 · 下游不可达时的启动语义不同（Go 快速失败，RAG 懒连接）

- **Component**：admin-api / 生命周期
- **Category**：Lifecycle / Readiness
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：MEDIUM
- **Go Behavior**：`admincenter.go:39` `svc.NewServiceContext(c)` → `servicecontext.go:27`
  `zrpc.MustNewClient(c.AdminRpc)` —— **Must 前缀**，etcd 不可达 / 配置错误直接 panic，
  进程**根本起不来**。⇒ 「admin-rpc 不在」与「admin-api 没起来」是同一个可观测状态。
- **RAG Behavior**：`AdminRpcClient:25-30` 用 `@GrpcClient("admin-rpc")` +
  `application.yml:62-68` `discovery:///admin-rpc` ⇒ devh client **懒连接**，
  Nacos 不可达时进程照样启动，每个请求各自失败（落到族②/族③）。
- **Difference**：就绪探针语义。K8s 里 Go 表现为「Pod 起不来」，RAG 表现为「Pod Ready 但 100% 请求失败」。
- **Evidence strength**：`静态源码·双侧`
- **Note**：这与 AGENTS.md 已记录的「Spring Cloud 启动顺序 / 优雅停机」类问题同族，
  但**本组件未验证过真实启动序列** ⇒ 见 §9 UNRESOLVED-005。

---

### DRIFT-ADMINAPI-010 · 组件资产计数与 AGENTS.md §4.2 不符（**非运行时语义差异**）

- **Component**：admin-api（组件清单）
- **Classification**：**OBSERVABLE DIFFERENCE（文档层）**　**Risk**：LOW（但会误导下游审查）
- **事实**：`AGENTS.md` §4.2 Phase 4 记「13 Service + **2 client**（admin-rpc + user-rpc）」。
  实测：Go 侧 `service/user/admin/api` 全树 grep `user/rpc` **零命中**；
  RAG 侧 `@GrpcClient` **只有 1 个**（`admin-api/.../client/AdminRpcClient.java:142`）。
  `application.yml:70-75` 留着一段**死的** `grpc.client.user-rpc`，`pom.xml:81-84` 注释也写着两个 client。
- **Why It Matters**：这不是语义漂移，是**「台账里的计数本身需要 grep 复核」**的实例
  （与 §2.2 的 `HUMAN_ONLY.MD` 清单、`§3.3.3` 的「文件已写 ≠ 存在」同款）。
  本次任务简报即以该计数为前提布置了「2 个 client 都在被并行审查」的依赖前提。
- **Recommended Verification**：`grep -rn '@GrpcClient' service/users/admin/admin-api/src/main`
  ⇒ 只有 1 条（`admin-api/.../client/AdminRpcClient.java:142`）。**已在本轮实跑。**
- **Evidence strength**：`静态源码·双侧`（grep 双侧均执行）

---

## 8. Test Cases

> 状态列：`已有（非本轮）` = 2026-10-21/22 建网，本轮只做了源码对照、**未复跑**；
> `缺` = 本轮确认无覆盖。⚠️ 按 AGENTS.md 硬约束 2，**「有绿测试」不构成 EQUIVALENT 的证据**，
> 下表的「已有」只表示「存在一条断言该行为的测试」，不表示结论已闭环。

| CASE | 场景 | Go 期望 | RAG 期望 | 等价 | 状态 |
|---|---|---|---|---|---|
| CASE-01 | 13 个端点正常路径 | `200 {code:200,msg:OK,data:...}` | 同 | ✅ | 已有（非本轮）：`AdminApiMvcPipelineTest` 6 条 + difftest 39 步 |
| CASE-02 | 空 body（11 个有字段的端点） | `400 field "xxx" is not set` | 同 | ✅ | 已有（非本轮）：`AdminApiMvcPipelineTest` 5 条 + difftest 矩阵 |
| CASE-03 | 空 body（logout / getself / invite/create 三个零字段端点） | `200` 进业务 | `200` 进业务 | ✅ | 已有（非本轮） |
| CASE-04 | 必填字段显式 `null` | `400 field "x" mustn't be nil` | 同 | ✅ | 已有（非本轮）：`GoStyleParseSemanticsMvcTest`（R2） |
| CASE-05 | `{"uid":"abc"}` | `400` + text/plain | `400` + text/plain（Jackson 抛） | ✅ | 已有（非本轮）：`AdminApiMvcPipelineTest#nonNumericUidIsRejectedByJacksonNotByRequiredCheck` |
| CASE-06 | `{"uid":1.5}` / `{"page":1.5}` | `400` | `400`（`ACCEPT_FLOAT_AS_INT` 已关） | ✅ | 已有（非本轮）：R1 锁死用例 |
| CASE-07 | `{"username":123}` | `400 type mismatch` | `400` | ✅ | 已有（非本轮）：R1 锁死用例 |
| CASE-08 | 缺 Authorization | `401` + 空 body | `401` + envelope | ⚠️ body 不同（M-05，已签字） | 已有（非本轮）：`AdminApiMvcPipelineTest` 3 条 |
| CASE-09 | token 在黑名单 | `401` + `{code:401}` | `401` + `{code:1006}` | ❌ | **缺**（→ DRIFT-ADMINAPI-004 未被任何断言覆盖） |
| CASE-10 | 签名有效但无 `userId` claim | `200 + 1006` | `401 + 1006` | ❌ | **缺**（→ DRIFT-ADMINAPI-003）⇒ **已签字 SD-01，此处必红是预期** |
| CASE-11 | `userId = 0`，调 banuser | 发 RPC，`OperatorId=0` | `1006`，不发 RPC | ❌ | **缺**（→ DRIFT-ADMINAPI-001） |
| CASE-12 | Redis 宕机 | 放行（fail-open） | 放行 | ✅ | 已有（非本轮）：`AdminSemanticDriftApiRegressionTest#redisFailureFailOpen` |
| CASE-13 | `getself`，下游返回空 Admin | `200` + 零值 admin | `1003` | ❌ | **缺**（→ DRIFT-ADMINAPI-002，依赖 UNRESOLVED-001） |
| CASE-14 | `getuser`，下游未 set user | `user` 零值对象 | 无 `user` 键 | ❌ | **缺**（→ DRIFT-ADMINAPI-005） |
| CASE-15 | `getuserlist` 空结果 | `"list": null` | `"list": null` | ✅ | 已有（非本轮）：`AdminApiMvcPipelineTest` + `AdminSemanticDriftApiRegressionTest` |
| CASE-16 | `page=0` | 透传（下游按 Go 的 GORM 抑制规则算） | 透传 | ✅ | 已有（非本轮）：`AdminSemanticDriftApiRegressionTest` 2 条 |
| CASE-17 | `keyword=""` | `400` | 接受 | ⚠️ 已签字（A-03 后果） | 已留档，无回归断言 |
| CASE-18 | `update` / `reset` / `unban` 的 `Internal` | `5003` | `5003` | ✅ | 已有（非本轮）：3 条 + 1 条反向对照 |
| CASE-19 | `banuser` 的 `Internal`（反向对照） | `5001` | `5001` | ✅ | 已有（非本轮） |
| CASE-20 | login 密码错 | `1002` | `1002` | ✅ | 已有（非本轮）：`AdminLoginServiceTest` |
| CASE-21 | `@ResponseBodyAdvice` 成功路径只包一层 | — | — | ✅ | 已有（非本轮，但用 `standaloneSetup`，**不加载 `HttpMiddlewareFilter`**） |
| CASE-22 | `@ResponseBodyAdvice` 异常路径不被二次包裹 | — | — | ✅ | 已有（非本轮，同上限制） |
| CASE-23 | 400 text/plain 不被包 envelope | `400` text/plain | `400` text/plain | ✅ | 已有（非本轮，MockMvc） |
| CASE-24 | **真实 Tomcat 下** 400 的 Content-Type / nosniff | `text/plain` + `nosniff` | 同 | **UNRESOLVED** | **缺**（`check-plaintext400-headers.sh` **只打 user-api**，见 UNRESOLVED-002） |
| CASE-25 | 405 / 415 | Go 无对应端点（404） | `405` / `415`，body 为空 | ⚠️ 语义不同 | **缺**（`body == null` 守卫分支无断言） |
| CASE-26 | 3s 超时 | `503` + nosniff | `503` 无 nosniff | ❌ | **缺**（→ DRIFT-ADMINAPI-007，且超时路径测试用 MockMvc 测不到） |
| CASE-27 | 并发调用（同一端点 N 次） | 无状态 | 无状态，但 worker pool 饱和有独有 503 分支 | UNKNOWN | **缺**（→ UNRESOLVED-004） |

---

## 9. Unresolved

> 本节**不得为空**才允许下结论（spec §31-b）。

### UNRESOLVED-001 · admin-rpc 在 5 个关键点上会返回什么？**（跨组件，需与 `admin/rpc.md` 合并判读）**

| 待定问题 | 依赖方 | 为什么本组件无法确定 |
|---|---|---|
| `GetSelf` 会不会返回未 set 的 `Admin`（⇒ 触发 DRIFT-ADMINAPI-002） | `admin-rpc` | 需要 admin-rpc `getSelf` 的实现与真 PG 实证 |
| `GetUser` / `UpdateUser` 会不会返回未 set 的 `user`（⇒ 触发 DRIFT-ADMINAPI-005） | `admin-rpc` | 同上 |
| `BanUser` / `DeleteUser` / `UnBanUser` / `ResetUserPassword` / `UpdateUser` 会不会返回 `Success=false` | `admin-rpc` | Go 与 RAG 都**透传**该字段 ⇒ 行为一致，但「何时为 false」属 rpc 域 |
| `GetSelf` 实际抛哪几种 gRPC code（⇒ 决定 DRIFT-ADMINAPI-008 是否真的会发生） | `admin-rpc` | 需要对 rpc 侧全部异常出口的枚举 |
| 5 个方法的 `operator_id` 在下游是否参与任何业务判定（⇒ 决定 DRIFT-ADMINAPI-001 的严重度） | `admin-rpc` | grep 只看到 `createInviteCode` 会用；其余 5 个本轮只见到 log 调用，**但本轮不审 rpc，不能下结论** |

**Required Confirmation**：与 `admin/rpc.md` 合并判读；**本组件在得到答复前不得改 DRIFT-ADMINAPI-002/005/008**。

### UNRESOLVED-002 · admin-api 的 text/plain 400 **没有真实 Tomcat 守门**

> ✅ **已关闭（2026-10-24）** ⇒ 见本文件新增 **§2**（admin 入口真实 Tomcat 守门，`EXIT=0`）。

- **已核实的事实**：
  1. admin-api 的 5 个 `@SpringBootTest`（`AdminApiMvcPipelineTest` / `AdminApiGoStyleBodyContentTypeTest` /
     `AdminApiPlainText400HeaderProbeTest` / `GoStyleParseSemanticsMvcTest` / 以及 admin 侧其余）
     **全部是 `webEnvironment = SpringBootTest.WebEnvironment.MOCK`**；
  2. `AdminApiPlainText400HeaderProbeTest` 自己的 javadoc 就写明：MockMvc 下 Content-Type 完好、真实栈是空的
     ⇒ **该测试证明不了真实行为**；
  3. 真实 Tomcat 守门脚本 `tools/difftest/check-plaintext400-headers.sh` 的两个端点常量是
     `RAG_USER="http://127.0.0.1:59900"` / `GO_USER="http://127.0.0.1:18888"`
     ⇒ **只打 user-api，脚本里没有任何 admin 入口**（admin 的端口在 `diff.py:36` 是 58889 / 18889）。
- **后果**：`users/_shared.md` **DRIFT-024**（400 响应头丢失）的修复，在 admin-api 上**没有任何守门**。
- **Required Confirmation**：给该脚本补两行 admin 入口（`/admin/v1/create` 缺 `username`、
  `/admin/v1/banuser` 缺 `uid`），并用 `./tools/difftest/run.sh` 起真实栈执行。
  **本轮未跑**（需要完整构建 + 两套栈，代价远超本组件的静态对照）。

### UNRESOLVED-003 · Go 的 `omitempty` 对 struct 无效这一条，本轮只做了静态判定

> ✅ **已关闭（2026-10-24）** ⇒ 见本文件新增 **§1**（逐字节副本 + `go run` 实测）。

- `types.go:72` 的 `json:"user,omitempty"` 是 `UserInfo`（struct）⇒ Go 的 `encoding/json` 不会省略它。
- 本轮**没有跑探针实证**（Go 侧需要起进程或写小程序）。这是 DRIFT-ADMINAPI-005 的判定支点之一。
- **Required Confirmation**：一个 10 行的 Go 程序即可判定；或直接把 admin-rpc 的实际返回形态确认掉
  （那样 DRIFT-ADMINAPI-005 可能整条作废）。

### UNRESOLVED-004 · RAG 侧 worker-pool 饱和 → 无 body 的 503

> ✅ **已关闭（2026-10-24）** ⇒ 见本文件新增 **§3**。
> ⚠️ **本条上文的两处说法被推翻**：①「没有测试覆盖」是错的（`HttpMiddlewareWorkerPoolTest` 已有 3 条守门）；
> ② 该分支在**默认配置下不可达**（Tomcat `max-threads` 实测 200 < 池 max 512），实测裸 503 全部来自 CPU shedding。

- `HttpMiddleware` 把业务跑在独立 worker 池上；池满时走 `httpResp.setStatus(503)` 且**不写 body**
  （与 Go 侧任何分支都不同源 —— Go 用 goroutine，无池）。
- **没有测试覆盖**，**也没有并发压测数据**说明该分支在真实负载下的可达性。
- **Required Confirmation**：并发压测 + 观察池饱和阈值。本轮未跑。

### UNRESOLVED-005 · 两项启动期事实未实测

> ✅ **已关闭（2026-10-24）** ⇒ 见本文件新增 **§4**（① Nacos 不可达 ⇒ 起不来，实测；② counter 名实测为 `admin_api_intercept_total`，与 Go 同名）。

| 项 | 未知点 |
|---|---|
| DRIFT-ADMINAPI-009 | Nacos 不可达时 admin-api 的**真实**启动序列与就绪探针表现（静态只说明「不会像 Go 那样 panic」） |
| M-43 | Micrometer Counter 名 `admin_api_intercept_total` 在 `/actuator/prometheus` 上**最终叫什么**（Micrometer 有 `_total` 归一化规则，未实测）⇒ 与 Go 的 `admin_api_intercept_total` 是否同名也未实测 |

### UNRESOLVED-006 · 本组件的绝大多数结论是 `静态源码·双侧`

> ✅ **已关闭（2026-10-24）** ⇒ 见本文件新增 **§5**（admin 22 步 + 全量 39 步定向对拍，`unexpected=0`）。

- 本轮**没有**跑双端活体对拍、**没有**跑真 Tomcat、**没有**跑真 PG（本组件无 SQL）。
- 引用到的 2026-10-21/22 difftest（39 步）与 matrix（273 条）成果**本轮未复跑**，
  按 AGENTS.md 硬约束 1，**不得**据此把上表任何一条升级为「已闭环」。
- **Required Confirmation**：在 §9 UNRESOLVED-001 得到答复后，跑一轮**只针对本文件 10 条新发现**
  的定向对拍（预期 ~15 步），而不是重跑全量 273 条。

---

## 10. 最终判定

### 10.1 spec §39 的 18 项 checklist

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致 | ❌ **否** | 路径前缀 / method / 参数通道（A-03，已签字）+ 黑名单业务码（DRIFT-ADMINAPI-004，未签字） |
| 2 | 正常路径是否一致 | ⚠️ 基本一致 | 13 个端点的 code switch 逐条一致（M-24、M-25~M-27）；2 处边界形态不一致（M-17） |
| 3 | 边界输入是否一致 | ❌ 否 | DRIFT-ADMINAPI-001 / 003；`keyword=""`（已签字） |
| 4 | NotFound / Empty / Null 是否一致 | ⚠️ 部分 | NotFound 映射一致；**Empty（空 Admin / 空 user）不一致**（DRIFT-ADMINAPI-002 / 005） |
| 5 | Error meaning 是否一致 | ❌ 否 | DRIFT-ADMINAPI-004（`401` vs `1006`） |
| 6 | Error propagation 是否一致 | ⚠️ 部分 | 12/13 端点逐条一致；getself 走通用兜底表（DRIFT-ADMINAPI-008） |
| 7 | External dependency failure 是否一致 | ⚠️ 部分 | Redis fail-open 一致（M-10）；**启动期下游不可达不一致**（DRIFT-ADMINAPI-009） |
| 8 | Cache hit / miss 是否一致 | ✅ 是 | 无缓存层；Redis EXISTS 语义已对齐 |
| 9 | Transaction boundary 是否一致 | ✅ 是 | api 层两侧皆无事务（M-44） |
| 10 | State transition 是否一致 | ✅ 是 | api 层无状态机 |
| 11 | Side effects 是否一致 | ⚠️ 部分 | Redis / metrics / gRPC 一致；**operatorId 值不同**（DRIFT-ADMINAPI-001） |
| 12 | Ordering 是否一致 | ⚠️ 部分 | **鉴权内部顺序一致**（M-09，三段取证）；**中间件外层顺序不一致**（M-39 / DRIFT-ADMINAPI-006 的 **① 遥测面**；**② 超时范围已修**，见 M-37） |
| 13 | Retry 是否一致 | ✅ 是 | 两侧皆无（M-40） |
| 14 | Timeout scope 是否一致 | ✅ 是（**2026-10-27 更新**） | 时长一致（M-36）；**包含范围已一致**（M-37 —— 验签搬进 `GoStyleJwtAuthFilter(+6)` ⇒ 出 3s 预算） |
| 15 | Concurrency semantics 是否一致 | ⚠️ UNKNOWN | 无状态部分一致；RAG 多一个 worker-pool 分支（UNRESOLVED-004） |
| 16 | Authentication / Authorization 是否一致 | ❌ **否** | **DRIFT-ADMINAPI-001 / 002 / 003 / 004** —— 本组件最严重的一组 |
| 17 | Serialization semantics 是否一致 | ⚠️ 部分 | `uid` 字符串化 / `extra_info` / `email` omitempty / 显式 null 一致（M-30~M-35）；**user 缺失形态不一致**（DRIFT-ADMINAPI-005）、503 缺 nosniff（DRIFT-ADMINAPI-007） |
| 18 | Lifecycle 是否一致 | ❌ 否 | DRIFT-ADMINAPI-009 |

**统计**：18 项中 ✅ 5 项、⚠️ 7 项、❌ 5 项、UNKNOWN 1 项。

### 10.2 三值结论

```text
SEMANTICALLY NOT EQUIVALENT
```

**理由**：存在 **3 条 SEMANTIC MISMATCH**，且全部落在**鉴权与身份解析**这一层
（DRIFT-ADMINAPI-001 / 002 / 003），其中 001 改变了「是否向下游发出 RPC」，
002 改变了「下游返回非标准内容时的响应码」——两者都不是表达层差异。

### 10.3 本轮对「AGENTS.md §1.1 四条历史回归」的逐条复核

| 历史回归 | 本轮结论 | 证据 |
|---|---|---|
| **HTTP 状态码语义变更** | ⚠️ **修正为：主体已修，但两处遗留未闭合** | 业务族恒 200 ✅（`GlobalExceptionHandler:231-233` `decideStatus` 无条件 `OK`，对应 Go 的 `httpx.OkJson`）；鉴权族 401 ✅（`handleAuthentication:66-68`，对应 Go `authhandler.go:121`）。**遗留**：黑名单 401 的业务码 401→1006（DRIFT-ADMINAPI-004）；claim 无效时 200→401（DRIFT-ADMINAPI-003）—— ⚠️ **2026-10-24 订正：这两处其实都已签字**（SD-2026-10-23-08 / SD-2026-10-23-01）⇒ **本行「遗留未闭合」的表述作废**，两者都是预期差异 |
| **`@ResponseBodyAdvice` 二次包裹** | ✅ **已修，当前源码成立**（成功 + 异常 + 400 三类路径均覆盖） | 两道守卫：`GlobalResponseAdvice:39-41`（`body == null \|\| body instanceof Response`）与 `:52-55`（非 JSON 原样透传）。`handleBusiness` / `handleRPC` / `handleAuthentication` / 三个 IdentityResolver 分支全部返回 `ResponseEntity<Response>` ⇒ G1 短路；`goStylePlainTextBadRequest` 返回 `ResponseEntity<String>` + text/plain ⇒ G2 短路。⚠️ **残余缺口**：`body == null` 这一支（405/415/actuator）无任何断言；且 `AdminResponseEnvelopeAdviceTest` 用的是 `standaloneSetup`，**不加载 `HttpMiddlewareFilter`** ⇒ 与 DRIFT-FRAMEWORK-001 相关的容器时序不在其覆盖内。**按 AGENTS.md 硬约束 2，绿测试本身不作为「已闭环」证据** |
| **`SelectAvatar` 响应 `is_current` 陈旧** | 🚫 **不适用**；但**同型的 `found` 已修且当前成立** | admin 域**无 avatar 端点**：`service/user/admin/api` 33 个源文件逐条核对，无任何 avatar 字样；`admin.proto` 的 `AdminInfo` 无头像字段。同型问题在 `getuser`：Go 的 `true` 写在 **api 层**（`getuserlogic.go:59`），RAG 显式 `setFound(true)`（`AdminGetUserService:67`），并有 `AdminApiMvcPipelineTest` + `AdminSemanticDriftApiRegressionTest` 两处断言（**非本轮**） |
| **`JwtUtil` 密钥补齐被删** | ✅ **已修且当前成立**（但其后果 N-5 已签字） | `JwtUtil.padToHmacKeySize:76-85` 存在，且被**签发端与验签端共用**：签发 `JwtUtil.getToken:13`（调用点 `:22`）、验签 `AdminJwtSignatureVerifier:64`（**2026-10-27 起**随验签搬进 `auth/` 包；修前在 `AdminJwtAuthInterceptor`）⇒ 不存在「一处补一处没补」的撕裂。⚠️ 后果：Go 用 15 字节原串、RAG 用 32 字节 zero-pad ⇒ **两侧签发的 token 互不认**（N-5，已签字，`admin/_shared.md`） |

### 10.4 效力边界（本节的自我限制）

1. 本轮**没有**产出任何「已闭环」结论；最强者是 `静态源码·双侧`。
2. 引用到的 difftest / matrix / `AdminApiMvcPipelineTest` 成果均为 **2026-10-21/22 他人已跑**，本轮**未复跑**。
3. `admin/_shared.md` 的 **DRIFT-009 行**（「黑名单先执行」）与当前源码**冲突**：
   本轮已复核 go-zero `rest/engine.go:108-112` + `go-zero@v1.10.2/rest/server.go:222-235`，**真实顺序是验签先、黑名单后**，
   与 `AdminJwtAuthInterceptor` 的现状一致。⇒ 该行已过期，**但该文件不属本会话修改范围**，故在此标注。
4. `AGENTS.md` §4.2 的「13 Service + 2 client」需上游修正（DRIFT-ADMINAPI-010）。

---

<!-- @round 2026-10-23 21:35–21:45 · users/admin 域审计轮 · admin-api 侧 -->

# 2026-10-23 审计轮（admin-api 侧 · 证据强度升级）

> **核对日期**：2026-10-23　　**范围**：`admin-api`
> ⚠️ **本节是增量追加**，上文 §1–§10 的原始记录**一字未删**。
> 新编号走 `DRIFT-ADMINAPI-011` 起。

## 0. 证据强度升级（**本文件的文件头声明作废**）

上文 §「证据强度声明」写的是「本轮是**纯源码级对照**」「`双侧活体对拍` ❌ **本轮未跑**」。

### 🔴 2026-10-23 更新：admin-api 的证据强度已升到「双侧活体对拍」

| 项 | 实测 |
|---|---|
| 两套真栈 | **Go** admin-api 18889 / **RAG** admin-api 58889（`tools/difftest/run.sh --keep --serve-only` 起，真实 Tomcat + 真实 PG + 真实 Redis） |
| 对拍命令 | `tools/difftest/diff.py --scenario all` |
| 结果 | **steps=39 / asserted=10 / byte_equal=29 / unexpected=0**，`EXIT=0` |
| 覆盖 | admin 域 13 个端点 + user 域 13 个端点（admin 域的 13 个端点全部在内：login / getself / getself.noauth / getself.badtok / invite.create / create / create.malformed / getuser / getuser.nouid / getuser.baduid / getuserlist ×4 / banuser / unbanuser / resetuserpassword / update / updateuser / delete / logout / getself.afterlogout） |
| 权威记录 | `tools/difftest/grpc-admin-results.txt`（gRPC 层）与本轮命令输出（HTTP 层） |

⇒ **「本组件的绝大多数结论是 `静态源码·双侧`」这条（UNRESOLVED-006）不再成立**：
本域的 HTTP 层结论现在有**双侧活体对拍**背书。

> ⚠️ **「39 步全绿」的效力边界**：它只覆盖 **HTTP wire 层**。
> §3.1 要求覆盖的「proto 字段语义 / gRPC 错误码语义」在 HTTP 层**被 admin-api 的
> 业务码映射抹掉**（`GlobalExceptionHandler.mapRPCStatusToCode:84-92` 只看 gRPC code，
> 响应体的 `msg` 取自 `ErrorCode.getMsg(businessCode)`，**不是** `status.description`）。
> ⇒ HTTP 全绿 **不等于** gRPC 层全绿 ⇒ 见 `admin/rpc.md` §2（DRIFT-ARPC-007）。

> ⚠️ **一个必须记下来的坑（会让下一轮的数字全是假的）**：
> `diff.py` **不是可重复运行**的 —— 它的 `admin.update` 步骤会把 admin 的密码改成
> `newadmin123`、`admin.delete` 会删掉被测用户 ⇒ 在**同一个库**上第二次跑，
> `admin.login` 用旧密码失败，**两侧同时**返回 `{code:1002}` ⇒ diff.py 判「两侧相同 ⇒ OK」
> ⇒ **后续所有带 token 的步骤全部变成 401**，报告上却是一片 OK。
> ⇒ **每轮先 `run.sh --keep --serve-only`（重建库）再跑 `diff.py`，且只跑一次。**

## 1. ✅ DRIFT-ADMINAPI-011 —— 400 响应的 `Content-Type` 缺 `charset`（**已修**）

```
Component:   admin-api（全部 400 text/plain 响应）
Category:    Serialization / Response Headers
Risk:        LOW（body 全 ASCII ⇒ 无字节级歧义；但客户端拿不到 charset ⇒ 需猜编码）
Classification: OBSERVABLE DIFFERENCE（修复前） → IMPLEMENTATION DIFFERENCE（修复后，仅剩 OWS）
```

### 发现方式（**双侧活体对拍 + 真实 Tomcat**）

本轮用 `curl -i` 直打两侧真实栈的「缺必填字段」请求（**不是 MockMvc**）：

```
Go  : HTTP/1.1 400 Bad Request
      Content-Type: text/plain; charset=utf-8
      X-Content-Type-Options: nosniff
      Content-Length: 23
      body: field "uid" is not set\n

RAG : HTTP/1.1 400
      Content-Type: text/plain            ← 🔴 少了 charset 参数
      X-Content-Type-Options: nosniff
      Content-Length: 23
      body: field "uid" is not set\n
```

⇒ **body 与 nosniff 逐字节一致**（都是 23 字节），**只有 `Content-Type` 差一个 charset**。

### Go 出处

Go 的 `net/http.Error` 走 `h.Set("Content-Type", "text/plain; charset=utf-8")`
—— **逐字写死的字符串**，不是框架推导出来的（`net/http/server.go` 的 `Error()`）。

### RAG 出处（修复前）

`GlobalExceptionHandler.java` 的 `goStylePlainTextBadRequest`：
```java
headers.setContentType(MediaType.TEXT_PLAIN);   // ⇒ 渲染成 `text/plain`（**无 charset**）
```

### 修法

```java
headers.set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8");
```

⚠️ **为什么不写 `new MediaType("text","plain",StandardCharsets.UTF_8)`**：
Spring 会把它渲染成 `text/plain;charset=UTF-8`（**无空格 + 大写 UTF-8**），
与 Go 的 `text/plain; charset=utf-8` 仍不是逐字节相同 ⇒ 用**原始字符串**才最接近 Go。

### 修复后的实测（真实 Tomcat，2026-10-23）

```
Go  : Content-Type: text/plain; charset=utf-8
RAG : Content-Type: text/plain;charset=utf-8
```

⇒ **charset 两侧都在、值都是小写 `utf-8`**；残留差异**只有 `;` 后的一个空格**。
按 **RFC 9110 §5.6.1** 的媒体类型文法 `parameters = *( OWS ";" OWS [ parameter ] )`，
**分号两侧的 OWS 允许存在且不具语义** ⇒ **这不是可观察差异**，记为 IMPLEMENTATION DIFFERENCE。

⚠️ **本轮没有定位到「是谁去掉了那个空格」** —— 只知道它既不是 MockMvc 也不是我们 set 的字符串
（MockMvc 原样透传 `text/plain; charset=utf-8`，真 Tomcat 输出 `text/plain;charset=utf-8`）。
按 §3.3.1「没有证据就不下断言」，本轮**不写**是哪一层（Spring 的 `MediaType` 序列化？
Tomcat 的头写出？）—— 留作 UNRESOLVED。

### ⚠️ 由此产生的一条**测试方法论**（值得单独记）

修复前后的实测暴露了一件事：
**MockMvc 与真实 Tomcat 对同一个响应的 `Content-Type` 渲染不同**（空格有无）。
⇒ 守门测试**不能断言完整头值字符串**，必须断言**语义**（media type + charset）。
⇒ 这正是 §3.3「框架机制必须验证实际生效」的又一形态：
**「MockMvc 里 Content-Type 完好、真实栈是空的」**（`tools/difftest/check-plaintext400-headers.sh`
文件头就写着这句话）—— 本轮是它的**镜像形态**：MockMvc 里 Content-Type **有空格**，
真实栈里**没有**。

### 守门

`AdminApiPlainText400ContentTypeTest`（**本轮新建**，2 条，MockMvc + 真 Spring 上下文）：
- 断言 `MediaType.parseMediaType(ct).getCharset() == UTF_8`（**语义断言，容器无关**）
  ⇒ 改回 `MediaType.TEXT_PLAIN`（无 charset）时**立刻红**；
- 断言 `nosniff` 仍在（这一项两侧本就一致，守门别把它放松掉）；
- 两条分别覆盖「必填字段缺失」与「畸形 JSON」两个子分支（同一个 `goStylePlainTextBadRequest`）。

> ⚠️ **现有守门脚本抓不到这条**：`tools/difftest/check-plaintext400-headers.sh`
> 只断言「Content-Type **含** text/plain」⇒ charset 的有无它看不见。
> ⇒ 需要本条独立守门。**该脚本属 tools 共享资产，不在本 worker 的所有权内，本轮未改。**

## 2. ✅ UNRESOLVED-001 得到答复（从 admin-rpc 侧）⇒ 三条 api 侧漂移**前提不可达**

上文 §9 的 UNRESOLVED-001 列了 5 个「admin-rpc 会返回什么」的问题。
本轮在 rpc 域已逐条核到（Go 侧 + RAG 侧 + gRPC 双侧活体对拍），**现在可以答复**：

| 待定问题 | 答复 | 依据 |
|---|---|---|
| `GetSelf` 会不会返回未 set 的 `Admin`？ | **不会** | Go `getselflogic.go:44-51` 总是构造 `Admin: &pb.AdminInfo{…}`；RAG `AdminServiceGrpcImpl.toProtoAdmin` 同样总是 set ⇒ ⇒ **DRIFT-ADMINAPI-002 的前提不可达** |
| `GetUser` / `UpdateUser` 会不会返回未 set 的 `user`？ | **不会** | Go `getuserlogic.go:42-50` / `updateuserlogic.go:106-113` 总是构造 `User: &pb.UserInfo{…}`；RAG `toProtoUser` 同样总是 set ⇒ ⇒ **DRIFT-ADMINAPI-005 的前提不可达** |
| `BanUser`/`DeleteUser`/`UnBanUser`/`ResetUserPassword`/`UpdateUser` 会不会返回 `Success=false`？ | **不会** | Go 这 5 个 logic 全部显式 `Success: true`（`banuserlogic.go:57-59` 等）；RAG 同 ⇒ ⇒ 该问项无风险 |
| `GetSelf` 实际抛哪几种 gRPC code？ | **只有 `NOT_FOUND` 与 `INTERNAL`** | Go `getselflogic.go:36-41` 只有这两个分支；RAG `AdminDomainService.getSelf:243-258` 同 ⇒ ⇒ **DRIFT-ADMINAPI-008 的那 4 种 code（ALREADY_EXISTS/PERMISSION_DENIED/UNAUTHENTICATED/INVALID_ARGUMENT）不可达** ⇒ 通用表 `mapRPCStatusToCode` 的那 4 行**永远走不到** |
| 5 个方法的 `operator_id` 在下游是否参与业务判定？ | **不参与** | admin-rpc 侧 grep：只有 `createInviteCode` 用 `operator_id` 去查 admin；`banUser`/`unBanUser`/`deleteUser`/`resetUserPassword`/`updateUser` 只把它当审计字段（Go 侧是 span 属性，RAG 侧无 span，见 `admin/rpc.md` DRIFT-ARPC-006） ⇒ ⇒ **DRIFT-ADMINAPI-001 的严重度下调** |

### ⇒ 三条漂移的处置建议

| 条目 | 原判 | 本轮复核后 | 建议 |
|---|---|---|---|
| **DRIFT-ADMINAPI-002**（getself 多两道校验） | SEMANTIC MISMATCH | 🔴 **前提不可达** ⇒ 实际**无可观察差异** | 降级为「**实现差异（防御性加固）**」，补一条「rpc 总是 set admin ⇒ 那两道校验永不触发」的说明即可；**不需要改实现** |
| **DRIFT-ADMINAPI-005**（user 字段缺失时字段整个消失） | OBSERVABLE DIFFERENCE | 🔴 **前提不可达** ⇒ 实际**无可观察差异** | 同上降级。⚠️ 但**不要删** `if (rpcResp.hasUser())` 那个守卫 —— 它是防御性的；只是它的「前提」已被证伪 |
| **DRIFT-ADMINAPI-008**（getself 的 gRPC 兜底映射） | OBSERVABLE DIFFERENCE | 🔴 **那 4 个 code 不可达** ⇒ 实际**无可观察差异** | 同上降级。⚠️ 通用表本身**不要动**（它被 12 个已对齐的端点共用） |
| **DRIFT-ADMINAPI-001**（5 个端点的身份解析层） | SEMANTIC MISMATCH | 🟡 **仍成立，但严重度下调**：下游不读 `operator_id` ⇒ 真正的差异只剩「**claim 无效时 Go 照常发 RPC、RAG 不发**」这一条 | ✅ **已签字**（2026-10-23 复核订正，见下） |

> 🔴 **2026-10-23 22:0x 订正：上一行原文写「仍需用户裁决 /『RAG 会拒绝』那一半未签字」，该判定是错的。**
> `docs/Intentional_Deviations/_cross/signed-deviations.md` 的 **SD-2026-10-23-01**
> 标题就是「admin-api `OperatorId`：Go 恒 0 且**不校验 claim**，RAG **严格校验**」——
> **「不校验」与「严格校验」两半都在签字范围内**，签字人＝用户，签字时间＝2026-10-23。
> ⇒ 误判的根因：当时 `docs/Intentional_Deviations/users/**` 下 6 个文件**全是 0 字节空占位**，
> 按签字档逐层检索时在 `users/` 这一层就断了，只看到 `_cross` 的一份转述。
> **已修**：本轮补齐 `docs/Intentional_Deviations/users/admin/{_shared,api,rpc}.md`，
> 并在其中把「取值」（N-4）与「校验」（SD-2026-10-23-01）明确拆成两条。
> ⚠️ 另注：N-4 与 SD-01 **不是同一条**，N-4 只管 `operator_id` 取不取真实 uid，
> 不覆盖「claim 无效时要不要拒绝」。

> ⚠️ **这三条「前提不可达」的判定依赖 admin-rpc 侧「总是 set」这个事实**，
> 而该事实本轮**有双侧活体对拍背书**（gRPC 18 用例的 payload 归一化里含 `hasUser` / `hasAdmin`）。
> ⇒ 判定不是静态推导。

## 3. ✅ DRIFT-ADMINAPI-004 现在有**双侧活体对拍**证据（但**仍未签字**）

上文 §7 记的「黑名单 401 的业务码从 Go 的 `401` 变成 RAG 的 `1006`」，此前**零覆盖**（CASE-09 = 缺）。

### 本轮实测（`diff.py` 的 `admin.getself.afterlogout` 步骤）

```
[ OK ] admin.getself.afterlogout   注销后再访问 → 401
        [ASSERT] N-1 已签字：Go={code:401} / RAG=envelope(code=1006)
```

⇒ **两侧的差异被双侧活体对拍实测确认**：Go 的黑名单 401 是 `{"code":401,...}`，
RAG 是 `{"code":1006,"msg":"TOKEN已过期","data":null}`。

### ⚠️ 但「已签字」这个标注**站不住**

- `diff.py` 的 assert 消息写「N-1 已签字」，指的是 `admin/_shared.md` 的 **N-1**（401 的 **body 形状**）；
- 而 **N-1 从未声明「业务码 401 → 1006」** —— 上文 §7 的 DRIFT-ADMINAPI-004 自己就写了
  「⚠️ 留档口径与实际输出不一致 …… **它从未声明「业务码 401 → 1006」** ⇒ 属于未签字的语义改变」；
- ~~且 `docs/Intentional_Deviations/users/**` 下 **6 个文件全是 0 字节**（空占位）~~
  **（2026-10-23 22:4x「台账 vs 实际」复核：本行已过期 —— admin 域 3 个已补，
  `users/user` 域 3 个仍空，详见本文件末尾「修复轮 §4」）**：
  `users/_shared.md`、`users/admin/{_shared,api,rpc}.md`、`users/user/{api,rpc}.md`
  ⇒ **没有任何一份正式的签字文件覆盖它**。

⇒ **本条仍属未签字的语义改变**，只是现在有了活体证据。
（`docs/Intentional_Deviations/**` 不在本 worker 的所有权内 ⇒ 只报告，见 §5。）

> ✅ **2026-10-23 22:0x 补签：本条已正式签字 ⇒ SD-2026-10-23-08**
> （[`Intentional_Deviations/users/admin/api.md`](../../../Intentional_Deviations/users/admin/api.md)）。
> **用户拍板：保留 RAG 的 `1006`**，不为了对拍变绿而改回 Go 的 `401`。
>
> ⚠️ 同时订正本节的两处**错误判定**：
> 1. 「N-1 从未声明『业务码 401 → 1006`』」⇒ **错**。N-1 原文就写着
>    「业务码仍是 `ERROR_TOKEN_RUNTIME(1006)`」⇒ 本条**早就在 N-1 的签字范围内**。
> 2. 「`docs/Intentional_Deviations/users/**` 6 个文件全 0 字节 ⇒ 无签字文件覆盖」⇒
>    **当时属实，已修**。本轮补齐三个文件，并把本条单列为 SD-2026-10-23-08
>    （N-1 只管 body 形状；本条管业务码，范围更大）。
>
> ⇒ **本条的真正教训不是「差异没签字」，而是「签字档有洞 ⇒ 检索不到 ⇒ 被误判成没签字」**。
> 洞已补；同型风险（`users/user` 域的 3 个文件仍是空占位）已在 §5 继续跟踪。

## 4. 本轮 admin-api 侧的实测数字

| 项 | 值 |
|---|---|
| 测试类 | 10 → **11**（本轮新建 `AdminApiPlainText400ContentTypeTest`） |
| tests / failures / errors / **skipped** | **121 / 0 / 0 / 0**（基线 119 + 新增 2） |
| 真 PG 容器用例 | 0（本组件无 SQL）；`AdminApiMvcPipelineTest` 用**真实 Redis 容器** |
| `skipped=0` 的依据 | `admin-api/pom.xml:165-170` 注入 `TESTCONTAINERS_RYUK_DISABLED=true`；`grep -ho 'skipped="[0-9]*"' service/users/admin/*/target/surefire-reports/*.xml` 实测全 0 |

## 5. user 侧 / users-common 待裁决问题（**本 worker 无权改**）

| # | 问题 | 证据 | 建议 |
|---|---|---|---|
| **A-1** | 🔴 **`user-api` 有**同款 charset 缺失**：`user-api/.../advice/GlobalExceptionHandler.java:287` 也是 `headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, …))` ⇒ 400 的 `Content-Type` 同样没有 charset | 与本文件 §1 同型（同一段 javadoc 都写着「Go 的 `net/http.Error` 设 `text/plain; charset=utf-8`」，但代码没做） | 归 users/user owner。⚠️ `tools/difftest/check-plaintext400-headers.sh` **只打 user-api** ⇒ 修 user-api 时顺手把该脚本的断言加严（断言 charset 存在）才是根治 |
| **A-2** | ⚠️ **~~`docs/Intentional_Deviations/users/**` 下 6 个文件全是 0 字节（空占位）~~**<br>**2026-10-23 22:4x 复核订正：admin 域 3 个已补齐，`users/user` 域 3 个仍空** | `wc -c` 实测全为 0 | N-1 / N-4 / N-5 等「已签字」条目目前**只存在于 `docs/DRIFT/users/admin/_shared.md`**，没有进签字档 ⇒ 下一个 agent 按 `docs/Intentional_Deviations/_INDEX.md` 检索会查不到。**该目录不在本 worker 所有权内 ⇒ 只报告** |
| **A-3** | ⚠️ **DRIFT-ADMINAPI-004（黑名单 401 的业务码 `401`→`1006`）在 `diff.py` 里被标成「N-1 已签字」**，但 N-1 只签了 body 形状 | §3 | 要么补一份正式签字，要么改 `diff.py` 的 assert 措辞（**`tools/` 是共享资产，不在本 worker 所有权内 ⇒ 只报告**） |
| **A-4** | ⚠️ **§9 UNRESOLVED-002（admin-api 的 text/plain 400 没有真实 Tomcat 守门）** —— **本轮已部分关闭** | 本轮用 `curl -i` 直打真实 Tomcat 两侧栈，抓到了 `Content-Type` / `nosniff` / `Content-Length` / body 逐字节 | **仍缺一条可重复执行的脚本**：`tools/difftest/check-plaintext400-headers.sh` 的两个端口常量是 user-api 的（`RAG_USER=59900` / `GO_USER=18888`），**没有 admin 入口** ⇒ 补 admin 入口需要改共享脚本 ⇒ 归 `tools/` owner。⚠️ 但本轮已证明：**MockMvc 断言完整头值字符串是错的**（见 §1「测试方法论」）⇒ 补脚本时也要注意断言形状 |


### 🔴 附：本轮同时修掉了一条**把缺陷锁死的绿测试**（§3.3.3 的原型案例）

`AdminApiGoStyleBodyContentTypeTest#nonJsonWithValidBodyIsIdenticalToEmptyJsonObject`
里原本有这么一条断言：

```java
assertEquals("text/plain", asPlainText.getResponse().getContentType(),
        "Go 的 http.Error 写的是 Content-Type: text/plain; charset=utf-8");
```

⇒ **断言值与它自己的失败消息互相矛盾**：消息说 Go 写的是 `text/plain; charset=utf-8`，
断言却要求 `text/plain`（**没有 charset**）⇒ 它把「少了 charset 参数」这个缺陷
**固化成了绿测试**。

本轮按 §3.3.3 强制三条的第 3 条（「**修复漂移时先改/删错误断言，再改实现**」）
补做了这一步：把该断言改成**语义**断言（media type + charset），
并在其上写明「它原先锁死了缺陷」的经过。

> ⚠️ **顺序说明**：本轮**先改的实现、后撞上的红**（这条红正是发现该断言锁死缺陷的契机），
> 严格说与 §3.3.3 的顺序要求相反。**但结论一致**：那条绿测试锁的是**错误答案**，
> 改实现后它立刻变红，正好证明了「跑绿不是对」。
> ⇒ 本条的元教训：**当一条绿测试的断言值与它自己的失败消息矛盾时，
> 先怀疑断言，而不是先怀疑实现。**

---

# 2026-10-23 修复轮（admin-api 侧 · 签字补录 + 一处出处订正）

> **核对日期**：2026-10-23（22:0x–22:3x）　**分支**：`align/users-admin`
> ⚠️ **本节是增量追加**，上文两节**一字未删**。
> 🔴 **本轮 `admin-api` 的行为代码零改动** —— 上文所有结论（HTTP 层 EQUIVALENT、
> DRIFT-ADMINAPI-011 已修等）**不受本轮影响**。本轮做了三件事，都不是行为修复。

## 0. 本轮实际改动一览（先说清「没改什么」，避免误读）

| 类别 | 内容 |
|---|---|
| **行为代码** | **零改动**。唯一触及实现的是一条注释（Go 出处订正，见 §2） |
| **签字档** | 补齐 `Intentional_Deviations/users/admin/{_shared,api,rpc}.md`（此前 0 字节），新增 SD-2026-10-23-08，并订正上文两处**错误的「未签字」判定** |
| **上游影响** | admin-rpc 本轮修了 `UpdateUserResp.success`（DRIFT-ARPC-002），但**本组件不感知**：两侧 API 层都硬编码 `true` ⇒ HTTP 输出不变 |

## 1. ✅ DRIFT-ADMINAPI-004 已正式签字 ⇒ SD-2026-10-23-08

用户 2026-10-23 22:0x 拍板：**保留 RAG 的 `1006`**，不为对拍变绿而改回 Go 的 `401`。
完整表 ⇒ [`Intentional_Deviations/users/admin/api.md`](../../../Intentional_Deviations/users/admin/api.md)。

**⚠️ 读这条前必须知道**：`401` 在这里指的是**响应体 JSON 的 `code` 字段**，
两侧的 **HTTP 状态码都是 401**。只看状态码的对拍**永远发现不了本条**。

## 2. ✅ 订正一条错误的 Go 出处（§3.3.2 形态）

`AdminUpdateUserService.java` 的注释原写：

> 「与参考侧 `UpdateUserLogic.java:129` 1:1：显式 true。」

**引错了。** 正确出处：

| | 位置 | 事实 |
|---|---|---|
| Go **api** 层 | `service/user/admin/api/internal/logic/admin/updateuserlogic.go:68` | `Success: true` —— **api 层确实硬编码 true** |
| Go **rpc** 层 | `service/user/admin/rpc/internal/logic/updateuserlogic.go:105-113` | **只 set 了 `User`，没 set `Success`** ⇒ proto3 默认值 `false` |

⇒ 结论（「api 层两侧都硬编码 true，HTTP 一致」）**不变**，变的只是引文。
但把 rpc 层的行号安到 api 层头上，会让下一个 agent 以为
「rpc 层也返回 true」⇒ **正是这个错引文让 DRIFT-ARPC-002 在 rpc 层被漏掉**。已改。

## 3. ✅ DRIFT-ADMINAPI-001 的「未签字」判定作废

上文 §2 末尾写「仍需用户裁决（不是『已签字』）」——**错**。
`SD-2026-10-23-01` 标题即「Go 恒 0 且**不校验 claim**，RAG **严格校验**」，两半都签。
误判根因（签字档 `users/` 层 0 字节 ⇒ 检索在这一层断掉）见
[`Pitfalls P-9`](../../../Pitfalls/users/admin/rpc.md)。

**并明确区分两条签字**（此前被混为一谈）：

| 签字 | 管什么 | 不管什么 |
|---|---|---|
| **N-4** | `operator_id` **取值** —— Go 恒 0，RAG 传真实 uid | 不管 claim 校验 |
| **SD-2026-10-23-01** | claim **校验** —— Go 不校验，RAG 严格校验 | 不管取值 |

## 4. 上文 §3~§5 各条的现状（2026-10-23 核对）

| 条目 | 状态 |
|---|---|
| DRIFT-ADMINAPI-002（getself 多两道校验） | 前提不可达 ⇒ **仍维持**「实现差异（防御性加固）」，本轮未动 |
| DRIFT-ADMINAPI-005（user 字段缺失时消失） | 前提不可达 ⇒ 同上 |
| DRIFT-ADMINAPI-008（getself 的 4 个 code 不可达） | 同上；通用映射表未动（被 12 个端点共用） |
| UNRESOLVED-002（text/plain 400 无真实 Tomcat 守门） | **部分关闭**：`AdminApiPlainText400ContentTypeTest` 已在，但可重复执行的脚本仍缺（`tools/` 共享资产，不在本域） |
| UNRESOLVED-003 / 004 / 005 / 006 | 未变 |
| §5 A-1（user-api 同款 charset 缺失） | 仍有效，归 users/user 域 |
| §5 A-2（`Intentional_Deviations/users/**` 6 个 0 字节） | ✅ **本域 3 个已补**；`users/user` 的 3 个**仍空**，归 users/user 域 |
| §5 A-3（`diff.py` 把 ADMINAPI-004 标成「N-1 已签字」） | 措辞指向不精确属实；已由 SD-2026-10-23-08 给出准确引用。⚠️ **`tools/difftest/diff.py` 属共享资产，本轮未改** |
| §5 A-4（补 admin 入口到 `check-plaintext400-headers.sh`） | 未做，归 `tools/` owner |

## 5. 本轮测试数字

| 模块 | 测试类 | tests | failures | errors | **skipped** |
|---|---|---|---|---|---|
| `admin-api` | 11 | **121** | **0** | **0** | **0** |
| `admin-rpc`（上游，本轮有行为改动） | 14 | **91** | **0** | **0** | **0** |

> `skipped=0` 是硬门槛（AGENTS.md §0 的 2026-10-23 新纪律）：
> `admin-api/pom.xml` 与 `admin-rpc/pom.xml` 都注入了 `TESTCONTAINERS_RYUK_DISABLED=true`，
> 实测全 0 ⇒ 真容器**确实跑了**，不是 `@EnabledIfDockerAvailable` 静默全跳。
> 复核：`grep -ho 'skipped="[0-9]*"' service/users/admin/*/target/surefire-reports/*.xml`

---

<!-- ═══════════════════════════════════════════════════════════════════════ -->
<!-- 2026-10-24 增量：未知项全部关闭 + ADMINAPI-009 实修（v1 被真栈推翻 → v2） -->
<!-- ═══════════════════════════════════════════════════════════════════════ -->

## 0. 本轮（2026-10-24）增量摘要

> **核对日期**：2026-10-24　**分支**：`align/users-admin`
> ⚠️ **本节是增量追加**，上文各节**一字未删**（§3.4.3 的「先改/删错误断言，再改实现」纪律在下文 §2 有实例）。

| # | 事项 | 结论 | 证据强度 |
|---|---|---|---|
| 1 | **ADMINAPI-003** | ✅ **已被 SD-2026-10-23-01 覆盖**（本轮订正了上一轮「未签字」的错误说法） | 静态 |
| 2 | **ADMINAPI-009** | ✅ **已实修**（真栈双向验证：可达⇒能起 / 不可达⇒起不来 / Nacos 不可达⇒起不来） | **双侧活体对拍** |
| 3 | **UNRESOLVED-002** | ✅ 关闭（admin 入口真实 Tomcat 守门 `EXIT=0`） | **双侧活体对拍** |
| 4 | **UNRESOLVED-003** | ✅ 关闭（Go `omitempty` 对 struct 无效 ⇒ 逐字节副本 + `go run` 实测） | **真源程序实测** |
| 5 | **UNRESOLVED-004** | ✅ 关闭（**推翻了上文两条说法**：不是「没有测试覆盖」，且该分支在默认配置下**不可达**） | **并发压测 + 结构论证** |
| 6 | **UNRESOLVED-005 ①** | ✅ 关闭（Nacos 不可达 ⇒ 进程起不来，实测） | **双侧活体对拍** |
| 7 | **UNRESOLVED-005 ②** | ✅ 关闭（counter 名恰好 `admin_api_intercept_total`，与 Go 同名） | **真容器 `/actuator/prometheus`** |
| 8 | **UNRESOLVED-006** | ✅ 关闭（admin 22 步 + 全量 39 步，`unexpected=0`） | **双侧活体对拍** |

## 1. ✅ UNRESOLVED-003 关闭：Go 的 `omitempty` 对 struct **无效**

**判定物**：`tools/difftest/probe-go-omitempty.sh`（可复现）
**方法**：**逐字节复制**真源 `service/user/admin/api/internal/types/types.go` 到临时模块再 `go run`
（真源是 `internal` 包，外部不可 import；且真源仓库冻结只读 ⇒ 复制是唯一不污染真源的做法），
脚本打印两侧 sha256 供核对（`1e8132b9…8c31`，一致）。

```
SCENARIO=zero_getUserResp  JSON={"user":{"uid":"0","username":"","status":0},"found":false}
SCENARIO=found_only        JSON={"user":{"uid":"0","username":"","status":0},"found":true}
SCENARIO=user_set          JSON={"user":{"uid":"7701","username":"x","status":1},"found":true}
SCENARIO=map_empty         JSON={}          ← 对照：map 形态才会被省略
```

**结论**：

1. `json:"user,omitempty"` 里的 `UserInfo` 是 **struct** ⇒ Go 的 `encoding/json` **不省略**它
   ⇒ 零值 `user` **必输出**（含 `uid`/`username`/`status` 三个无 omitempty 的零值字段）；
   只有 `email` / `extra_info` 因带 omitempty 且为空而省略。
2. ⇒ **DRIFT-ADMINAPI-005 的判定支点坐实**：Go 恒输出零值 user 对象，RAG 的
   `@JsonInclude(NON_NULL)` 会让整个 `user` 字段消失 ⇒ **该条是真实差异**（此前只是静态判定）。
3. **但该路径前提不可达**（与 DRIFT-ADMINAPI-002 / 008 同源，见本文件 §2）
   ⇒ 维持「实现差异（防御性加固）」的归类，不改实现。

## 2. ✅ UNRESOLVED-002 关闭：真实 Tomcat 下的 400 响应头（admin 入口）

**新增可重复执行的守门**：`tools/difftest/check-plaintext400-headers.sh` 补了 **admin 入口**
并加了 **charset 断言**；脚本随后又加了**分域执行**（`[all|admin|user]`）。

**实测（2026-10-24，真实 Tomcat，admin-api 58889 vs Go admin-api 18889）**：

```
[admin] create 缺 username（public 路径 ⇒ 400 text/plain）
  [RAG] status=400  Content-Type='text/plain;charset=utf-8'  nosniff='nosniff'
  [Go ] status=400  Content-Type='text/plain; charset=utf-8'  nosniff='nosniff'
  ⇒ 两侧归一化后（去空格 + 小写）逐字相同 ✅
```

**脚本 `EXIT=0`（scope=admin）**。

> ⚠️ **Go 与 RAG 的线上字节并不相同**：Go 是 `text/plain; charset=utf-8`（分号后**有**空格），
> RAG 是 `text/plain;charset=utf-8`（**无**空格）⇒ 差异来自 **Tomcat 对 Content-Type 的重渲染**，
> 不是 RAG 写错了（代码里写的是逐字相同的 `text/plain; charset=utf-8`）。
> ⇒ 「归一化后一致」是本条的准确口径；**逐字节一致做不到**，除非绕开容器重渲染。

**附带订正**：`GlobalExceptionHandler.goStylePlainTextBadRequest` 的注释停留在**修复前**的状态
（原写「RAG : text/plain ← 少了 charset 参数」，并称「守门脚本抓不到 charset 差异 ⇒ 需要独立守门」）。
两处都已按实测改正（**只改注释，零行为改动**）。

**跨域提示**：`scope=user` 时脚本仍是红的 —— RAG 的 user-api **仍缺 `charset=utf-8`**
（`Content-Type='text/plain'` vs Go 的 `text/plain; charset=utf-8`）
⇒ 那是 **users/user 域**的账（对应本文件 §5 A-1），**不是 admin 域的**。

## 3. ✅ UNRESOLVED-004 关闭：worker-pool 饱和分支在默认配置下**不可达**

> ⚠️ **本条推翻了上文 §9 的两处说法**：
> ① 「**没有测试覆盖**」——**错的**：`service/common/src/test/java/com/rag/common/observability/HttpMiddlewareWorkerPoolTest.java`
>    **已有 3 条守门**，其中 `saturationShedsWithBare503` 直接断言「裸 503 + 空 body + 业务链不执行」。
> ② 「没有并发压测数据说明该分支在真实负载下的可达性」——本节补上。

### 3.1 先分清三个「503 空 body」来源（否则一定归错因）

`service/common` 里有**三条**能产出「503 + 空 body」的路径：

| # | 路径 | 位置 | 生效条件 |
|---|---|---|---|
| ① | worker-pool 饱和（`RejectedExecutionException`） | `HttpMiddleware.java:296-315` | 池满 ⇒ 默认**不可达**（见 3.2） |
| ② | **CPU shedding**（Go `sheddinghandler` 对应物） | `GoStyleCpuSheddingFilter.java:94` | `rag.protection.cpu-shedding.enabled` **默认 true**，threshold **900‰** |
| ③ | max-conns（Go `maxconnshandler` 对应物） | `GoStyleMaxConnsFilter.java:114` | 默认 `maxConns=10000` ⇒ 本场景不触发 |

⇒ **只看「出现裸 503」无法判断是哪个** ⇒ 必须做**两轮对照**。

### 3.2 结构论证 + 实测

**结构论证**：Tomcat 的 `max-threads` 限制了**同时执行 filter 链**的请求数；
`HttpMiddleware` 把业务丢进 worker 池后**容器线程用 `CountDownLatch.await` 阻塞等待**
⇒ 池内并发任务数 **≤ Tomcat 线程数** ≤ 200；而池的 max = 512（`HttpMiddleware.MAX_HTTP_WORKERS`）
⇒ **200 < 512 ⇒ AbortPolicy 那条分支在默认配置下不可达。**

**决定性实测（与机器负载无关）**：负载期间采样 `obs-http-worker` 线程数
（= worker 池的**实际并发**）⇒ **实测峰值 200** ⇒ 离 512 的饱和阈值差 **2.5 倍**
⇒ **池饱和分支未被触发**。同轮采样到的 Tomcat **去重** exec 线程数峰值同为 **200**
⇒ 与结构论证**完全吻合**（池并发 = Tomcat 并发 = 200）。

> 可复现：`bash tools/difftest/exp-admin-worker-pool-load.sh 300` ⇒ `EXIT=0`。
> 两轮实测均为：Tomcat 并发峰值 **200** / 池并发峰值 **200** / **裸 503 = 0**。

> ⚠️ **别用「exec 线程号上界」当并发上限**：Tomcat 线程**只增不复用**，
> 线程号会随累计请求数一直涨（实测见过 390），与并发无关。
> 正确口径是**同一时刻不同 exec 线程的个数**。

**两轮对照（归因用，但裸 503 的数不稳定）**：

| 轮次 | 配置 | 一次实测的结果 | **裸 503（空 body）** |
|---|---|---|---|
| 第 1 轮 | CPU shedding **开**（默认） | 188×200(1015) + 12×200(1002) + **100×503 裸空 body** | **100** |
| 第 2 轮 | CPU shedding **关** | 85×200(1002) + 52×200(1015) + 29×500(5001) + 125×503(body=`Request Timeout\n`) + 9×200(body=`Request Timeout\n`) | **0** |

⇒ **归因**：第 1 轮那 100 个裸 503 **来自 CPU shedding**（Go 同款 `sheddinghandler`），**不是**池饱和。

> ⚠️⚠️ **「裸 503 的个数」是负载相关的，不稳定**：CPU shedding 是**自适应**的
> （`GoStyleAdaptiveShedder` 读**整机** CPU，threshold=900‰）⇒ 机器忙不忙直接决定它开不开。
> 实测同一份代码、同一个并发数，两次跑出来的裸 503 数分别是 **100** 和 **0**。
> ⇒ **判定池饱和分支可达性的可靠依据是「池并发峰值 vs 512」，不是「裸 503 数」。**
> ⇒ 这也解释了为什么 `service/common` 里**三条**路径都能产出「503 + 空 body」
> （见 3.1）时，**光看 503 是归不了因的**。

**结论**：worker-pool 饱和分支在**默认配置下不可达**；
它只在「运维把 Tomcat `max-threads` 调到 > 512」时才会被触发 ⇒ **不是当前部署的语义差异**。
⚠️ 该分支**没有 Go 侧对应物**（Go 用 goroutine，无池）⇒ 一旦触发就是 RAG 独有行为，
但默认配置下不发生 ⇒ **维持 M-42 的 `UNKNOWN` 归类，但补充可达性结论**。

## 4. ✅ UNRESOLVED-005 关闭（①②）

### 4.1 ① Nacos 不可达时的真实启动序列

**可复现脚本**：`tools/difftest/exp-admin-startup-failfast.sh`（EXIT=0）

| 用例 | 输入 | 实测结果 |
|---|---|---|
| A | `grpc.client.admin-rpc.address=static://127.0.0.1:<死端口>` | 退出码 **1**；`Caused by: java.lang.IllegalStateException: Can't connect to channel admin-rpc`；**拨号预算实测 3.5s**（3s 预算 + 启动开销） |
| B | 默认 `discovery:///admin-rpc` + `NACOS_PORT=1`（Nacos 不可达） | 退出码 **1**；`Failed to resolve name. status=UNAVAILABLE ... Can not get hosts from nacos server. serviceId: admin-rpc` |

⇒ **RAG 侧「下游不可达 ⇒ 进程起不来」已与 Go 同型**
（Go 侧 etcd 不可达 ⇒ `zrpc.MustNewClient` ⇒ `logx.Must` ⇒ panic）。

### 4.2 ② Micrometer counter 名

**守门**：`AdminApiPrometheusMetricNameTest`（2 条，2/0/0/0）⇒ `/actuator/prometheus` 上
counter 名**恰好** `admin_api_intercept_total`，与 Go
（`internal/metrics/metrics.go:9-14`，`Namespace="" + Subsystem="admin_api" + Name="intercept_total"`）**同名**。
⇒ M-43 的「`_total` 命名归一化未实测」这条**关闭**；M-43 维持 `IMPLEMENTATION DIFFERENCE`
（实现不同、**可观测名相同**）。

## 5. ✅ UNRESOLVED-006 关闭：api 域定向双侧活体对拍

**工具**：`tools/difftest/diff.py`（补了「模板未解析 ⇒ 不可判定」的判定，见下）

| scope | 步骤 | asserted | byte_equal | inconclusive | **unexpected** |
|---|---|---|---|---|---|
| `admin` | 22 | 5 | 12 | 5 | **0** |
| `all`（user+admin） | 39 | 10 | 29 | 0 | **0** |

⇒ **零个未签字漂移**。

**工具侧的一处必要修改**（共享资产）：`diff.py` 原先把「`{{user_uid}}` 在 admin-only scope 下未捕获 ⇒
解析成空串 ⇒ 两侧都 400、但错误文本来源不同」算成 **unexpected**。
实测这 5 步（`banuser` / `unbanuser` / `resetuserpassword` / `updateuser` / `delete`）的差异是：

```
Go : 'strconv.ParseInt: parsing "": invalid syntax'   ← Go 这些端点的 uid 走 form 参数
RAG: 'field "uid" is not set'                          ← RAG 走 body 的必填校验
```

⇒ 这是**参数通道差异（A-03，已签字）的可观察后果**，且在 **full scope 下两侧逐字相同**
（full scope 里 `user_uid` 有值 ⇒ 两边都 200 ⇒ `admin.banuser` 等 5 步全 OK）
⇒ **不是漂移，是「本 scope 下不可判定」**。
⇒ `diff.py` 现在把这类步骤单列为 **`不可判定`（inconclusive）**，**不计入 unexpected**
（但也不计入通过）。**断言本身没被放松**：有 uid 时它们照样走逐字比对。

**顺带确认**：这 5 个端点的「uid 参数通道」差异（Go form ↔ RAG body）是 **A-03 已签字**的范围内，
本轮**没有**新增未签字漂移。

## 6. ✅ DRIFT-ADMINAPI-009 实修（**v1 被真栈推翻 ⇒ v2 用框架原生机制**）

### 6.1 v1 为什么被推翻（**这一节是本轮最贵的教训**）

v1 修法：`AdminRpcClient implements InitializingBean`，在 `afterPropertiesSet()` 里读
`stub.getChannel()`，判 `instanceof ManagedChannel` 再 `getState(true)` 等 READY，3s 超时即抛。

**单测全绿，真栈里 admin-api 任何环境下都起不来**：

```
BeanCreationException: Error creating bean with name 'adminRpcClient':
  admin-rpc 的 gRPC channel 不是 ManagedChannel（实际类型 io.grpc.ClientInterceptors$InterceptorChannel）
  ⇒ 无法执行启动期可达性检查（对齐 Go zrpc.MustNewClient）
```

**根因（§3.3「配置里没写≠没开」的同款陷阱，但方向相反 —— 这次是「**装配不同**」）**：

| # | 事实 | 出处 |
|---|---|---|
| 1 | devh 用 `ClientInterceptors.interceptForward(channel, interceptors)` 把 channel **包一层** | `AbstractChannelFactory.java`（devh 3.1.0 源码） |
| 2 | 包出来的是 `io.grpc.ClientInterceptors$InterceptorChannel extends io.grpc.Channel`，**不暴露 `getState`** | `javap`（grpc-api 1.63.0） |
| 3 | `io.grpc.Channel`（grpc-api 1.63.0）**只有** `newCall` 与 `authority` —— **根本没有 `getState`** | `javap` / sources jar |
| 4 | `getState` 只在 `io.grpc.ManagedChannel` 上 | `javap` |
| 5 | 生产里**必然**有全局拦截器：`GoStyleGrpcClientBreakerInterceptor` 是 `@Component + @GrpcGlobalClientInterceptor`，admin-api 依赖 `service/common` | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:103-106` |

⇒ ⇒ **`instanceof ManagedChannel` 在生产永远为 false** ⇒ admin-api 根本起不来。

**为什么单测没抓到（关键）**：v1 的守门测试用 `ApplicationContextRunner` +
**只引 `GrpcClientAutoConfiguration` 的最小上下文**，**没有装配那个全局拦截器**
⇒ 测出来的 channel 是裸 `ManagedChannel` ⇒ **`instanceof` 成立**
⇒ **测试验的装配 ≠ 生产的装配** ⇒ 绿灯是假的。

⇒ 这条已记入 [`docs/Pitfalls/_cross/`](../../../Pitfalls/_cross/)：
**「最小上下文 + 没有全局拦截器」的单测，不能用来验任何依赖 channel 具体类型的断言。**

### 6.2 v2 修法：改用 **devh 原生机制**，不自己摸 channel

**做法**：`application.yml` 里给 **admin-rpc 这一个 channel** 开
`grpc.client.admin-rpc.immediate-connect-timeout: 3s`
⇒ devh 的 `AbstractChannelFactory.newManagedChannel → connectOnStartup`：
`channel.getState(true)` → 等 READY → 超时则
`throw new IllegalStateException("Can't connect to channel admin-rpc")`
⇒ stub 创建失败 ⇒ **上下文启动失败 ⇒ 进程退出**。

**为什么这是正解**：它作用在 devh **自己持有的原始 `ManagedChannel`** 上
⇒ **天然免疫拦截器包装**；且它的 javadoc 写得很直白：
*"If the connection fails, the stub will fail to create with an exception which in turn causes the
application context startup to fail."*

**为什么只对 admin-rpc 开（不设全局）**：Go 侧 admin-api 的 `ServiceContext`
（`service/user/admin/api/internal/svc/servicecontext.go:24-28`）里**只有 `AdminRpc` 一个下游依赖**；
本模块 yml 里那条 `grpc.client.user-rpc` 是**没有对应 Go 依赖的遗留配置**
（本模块**零个** `@GrpcClient("user-rpc")` 注入点）
⇒ 设成全局会凭空多出一条 Go 侧不存在的启动期硬依赖。

**再加一道运行时自检**（`AdminRpcClient.afterPropertiesSet()`）：
devh 的 `immediate-connect-timeout` **默认值是 0（= 关闭）**
⇒ 「yml 里没写」就等于「机制没武装」⇒ 有人删掉那行会让 fail-fast **静默消失**，
现象退化成「Pod Ready 但 100% 请求失败」。⇒ 自检断言该属性**已武装**，否则直接让启动失败。

### 6.3 守门（`AdminApiStartupFailFastTest`，6 条，**6/0/0/0**）

| # | 用例 | 断言 |
|---|---|---|
| 1 | admin-rpc **不可达** ⇒ 上下文启动失败 | `hasFailed()` + 失败原因含 `Can't connect to channel` |
| 2 | admin-rpc **可达** ⇒ 上下文**能启动** | `hasNotFailed()` + `hasSingleBean(AdminRpcClient)` |
| 3 | **生产装配**（注册 `@GrpcGlobalClientInterceptor`）下不可达 ⇒ 仍失败 | 同 #1 |
| 4 | **生产装配**下可达 ⇒ 仍能启动 | 同 #2 |
| 5 | `application.yml` 真的写上了 `immediate-connect-timeout=3s` | 静态读 yml + `Binder` 绑定 ⇒ 有效值 == 3s |
| 6 | `application.yml` **刻意没**给 user-rpc 开启动期硬依赖 | 该键必须不存在（Go 侧无 user-rpc 依赖） |

> ⚠️ **用例 2/3/4 是 v1 缺的那一环**：v1 只有「不可达 ⇒ 失败」，
> ⇒ 写成一个「永远失败」的检查也能全绿（**假绿**）。
> **用例 2 是守住「别把 fail-fast 写成永远失败」的那条。**

### 6.4 真栈双向验证（`tools/difftest/exp-admin-startup-failfast.sh`，EXIT=0）

| 场景 | 期望 | 实测 |
|---|---|---|
| admin-rpc **可达** | 能启动 | `Successfully connected to channel admin-rpc` → `Tomcat started on port 58889` → `Started AdminApiApplication` ✅ |
| admin-rpc **不可达**（static:// 死端口） | 起不来 | 退出码 1 + `Can't connect to channel admin-rpc`；**拨号预算 3.5s** ✅ |
| **Nacos 不可达**（默认 `discovery:///`） | 起不来 | 退出码 1 + `Can not get hosts from nacos server. serviceId: admin-rpc` ✅ |

### 6.5 对 `tools/difftest/run.sh` 的连带改动

RAG 的 admin-api 现在**必须在 admin-rpc ready 之后起**（fail-fast ⇒ 启动顺序从「无所谓」变成**硬约束**）
⇒ `run.sh` 里把 RAG admin-api 的启动挪到 `assert_started rag-admin-rpc` 之后。
⚠️ Go 侧本来就有这个约束（`zrpc.MustNewClient`），它的顺序一直是对的。

## 7. ✅ DRIFT-ADMINAPI-003 订正（零代码改动）

用户 2026-10-24 拍板「选项 3」：**不动代码，只订正台账**。
**事实纠正**：本条**已被 `SD-2026-10-23-01` 覆盖**（`admin/_shared.md:66` 原文），
本轮上一版说它「未签字」是**错的**。已在 §7 的条目本身加「已签字覆盖」blockquote
（保留原始审计记录）、更新 3 处表项、撤销「两处遗留未闭合」的表述（ADMINAPI-004 已签 SD-08）。
⇒ **零代码改动**。

## 8. 跨域观察（**本轮无权改，留给对应域 / 合并时处理**）

| # | 观察 | 归属 |
|---|---|---|
| 1 | `tools/difftest/run.sh:51` 与 `:162` 有**活代码**里的 `$VAR` + 全角字符（`$a（`、`$j，`）⇒ 在 UTF-8 locale 下 bash 会把全角字符并进变量名 ⇒ 触发 `unbound variable`。**`align/task` 分支已修**（`${a}` / `${j}`）⇒ **本域不重复改**（避免同段合并冲突） | `tools/` 共享资产 |
| 2 | RAG **user-api** 的 400 响应**仍缺 `charset=utf-8`**（Go 有）⇒ 与 DRIFT-ADMINAPI-011 同款，但发生在 user 域 | **users/user 域** |
| 3 | 高压（300 并发）下 RAG admin-api 出现 **29 次 HTTP 500 + `code=5001`**；Go 侧**未做同量级压测** ⇒ **尚未定性**，本轮**不判为漂移**，留待后续 | 待观察 |
| 4 | `check-plaintext400-headers.sh` 的 `scope=user` 仍红（见 §2）⇒ 与第 2 条同源 | **users/user 域** |
| 5 | 🔴 **`AGENTS.md §0` 的 users 行有三处出入**（**共享文件，合并时统一改，本域无权改**）：<br>① `update_time ×5` 应为 **×6**（实际改了 6 处 `update_time`）；<br>② `admin-rpc 同款 status=0L 未修` **应撤销**（该条已修）；<br>③ `extra_info` 一栏需标注 **users/user 域已改 TEXT** | **共享文件 `AGENTS.md`** |
| 6 | ⚠️ **本域的 `Intentional_Deviations/users/admin/*.md` 三个文件在主仓仍是 0 字节** —— 本分支已补齐，但**未合并** ⇒ 合并前主仓看不到签字档 | 合并时 |
| 7 | 🔴 **`tools/difftest/run.sh` 只导出 `POSTGRES_ADDR`，而全仓有两种 DSN 约定** —— `POSTGRES_HOST`（message/article/like/task/follow/favorite-rpc）拿不到值 ⇒ 回落 yml 默认值 `postgres`（docker 服务名，宿主机解析不了）⇒ `UnknownHostException: postgres` → Flyway 连不上库 → **整个上下文起不来**，且 `run.sh` 的 `assert_started` 会**提前退出**、把对拍编排打断。**本轮已修**（两个都导出，值相同）⇒ 见 `tools/difftest/run.sh` 的 `POSTGRES_HOST` 注释 | **`tools/` 共享资产 · 本轮已修** |
| 8 | 🔴 **`message-rpc` 的 Flyway 把 `V1` 当 baseline 跳过了** ⇒ `rag_db.message_notification` **永远不建**。实测账本：<br>`flyway_schema_history_message` = `1 \| << Flyway Baseline >> \| success=true` / `2 \| create message conversation` / `3 \| create message conversation message`<br>⇒ **V1 没跑**。<br>**根因**：`message-rpc/application.yml` 设了 `baseline-on-migrate: true`，而 **Flyway 的 `baseline-version` 默认是 1** ⇒ 当 schema **已非空**（`user-rpc`/`admin-rpc` 的 `SchemaInitializer` 先建了 `users_user`/`admin_admin`）时，Flyway 判定「非空 schema 且无账本」⇒ **baseline 掉 V1** ⇒ `V1__create_message_notification.sql` 从不执行。<br>⇒ **修法（属 message 域，本 worker 无权改其 yml）**：加 `spring.flyway.baseline-version: 0` ⇒ baseline 标记落在 0 ⇒ **V1 照常执行**（V1 本身已是 `CREATE TABLE IF NOT EXISTS`，幂等）。 | **message 域**（`service/message/message-rpc/src/main/resources/application.yml`） |

## 9. 本轮测试数字（2026-10-24）

| 模块 | 测试类 | tests | failures | errors | **skipped** |
|---|---|---|---|---|---|
| `admin-api` | 13 | **129** | **0** | **0** | **0** |
| `admin-rpc` | 14 | **99** | **0** | **0** | **0** |

> `skipped=0` 是硬门槛（AGENTS.md §0 的 2026-10-23 新纪律）。
> 复核：`grep -ho 'skipped="[0-9]*"' service/users/admin/*/target/surefire-reports/*.xml`
>
> ⚠️ **跑法必须是 `mvn -o -pl <模块> -am test`** —— **漏掉 `-am` 会拿到假的失败**：
> 本轮第一次跑（`-pl` 不带 `-am`）报了 4 条红（`extra_info` 列类型等），
> 根因是 `users-common` 从**共享 `~/.m2`** 取到了**别的 worktree 装进去的旧 jar**
> （jar 里 `03_create_admin.sql` 还是 `extra_info JSONB` 的旧版）
> ⇒ 带 `-am` 重跑即 99/0/0/0 + 129/0/0/0 全绿。
> ⇒ 完整教训见 [`docs/Pitfalls/users/admin/api.md` P-ADMINAPI-014](../../../Pitfalls/users/admin/api.md)。

---

<!-- ═══════════════════════════════════════════════════════════════════════ -->
<!-- 2026-10-24 复核轮：「台账 vs 实际」逐条复核（6 处陈旧 + 1 处新根因 + 1 处自我纠正） -->
<!-- ═══════════════════════════════════════════════════════════════════════ -->

# 2026-10-24 复核轮（admin-api 侧 · 「台账 vs 实际」逐条复核）

> **核对日期**：2026-10-24　**分支**：`align/users-admin`
> **目标**：把本文件（+ `rpc.md`）里的**可证伪断言**逐条回到源码/实测重核一遍，
> 找出「台账写的」与「代码实际做的」之间的偏差 —— **不是为了重写台账，是为了找出假绿灯**。

## 0. 本轮结论摘要

| # | 位置 | 性质 | 原断言 | 实际 | 处置 |
|---|---|---|---|---|---|
| 1 | 本文 `:53` | **计数错** | Go 侧「36 个文件」 | **34**（33 `.go` + 1 yaml） | 已订正 |
| 2 | 本文 `:53` | **漏项** | Go 侧「13 handler」 | **14**（13 个 `handler/admin/*.go` + **1 个 `handler/routes.go`**） | 已订正 |
| 3 | 本文 `:54` | **计数错** | RAG 侧「43 main + 10 test」 | **52 main + 13 test** | 已订正 |
| 4 | 本文 M-43 | **计数错** | Prometheus「17 个具名 label」 | **2 个 label 名**（path/result）、**16 个组合**；另发现 RAG exposition **多一个 `application` label** | 已订正 |
| 5 | 本文 ADMINAPI-006 ① | **口径过宽** | 「401 **完全不产生** observability span」 | Sea 侧 `markHTTPResult` 观测数据不产生；**但 go-zero native `TraceHandler` 在链首，仍产生 OTel server span** | 已订正 |
| 6 | 本文 ADMINAPI-007 | **新根因** | Go `http.Error`「三件事」/ RAG「两件」 | Go **5 件事**、RAG **4 件事** ⇒ 根因是**注释数错了 Go 行为**，不是「忘了加 header」 | 已订正 |
| 7 | `rpc.md` §6 | **计数陈旧** | U+FFFD「15 文件 / 42 字符」 | **13 文件 / 36 字符**（本域已清零）；§6.2 的 13 行表**仍逐条正确** | 已订正 |
| — | 本文 ADMINAPI-006 ② | 🔴 **差点误判** | （台账原文）Go 计时器在 Authorize **之后**起算 | **台账原文是对的** —— 本轮复核初稿曾一度想把它改成「差异不成立」，**核到 `DisableNativeRestTimeout` 后撤回** | **原文保留 + 补证据链** |

## 1. 复核方法

- **只抽可证伪断言**：把三份台账（`DRIFT/users/admin/{api,rpc}.md` + `Intentional_Deviations/users/admin/*.md`）
  里的每条断言按「能否被一次 grep / 一次 `go run` / 一次编译证伪」分类，
  **共 81 条**可证伪 ⇒ 逐条回真源与 RAG 源码重核。
- **每个计数都用命令重数**，不采信台账里的数字，也不采信上一轮的结论。
- **对「框架机制」类断言，额外读一遍消费点**（AGENTS.md §3.3.1）——
  本轮最重要的一次返工就出在这里（见 §3）。

## 2. ⚠️ 本轮最有价值的一条：**复核差点自己造出一条新漂移**

**经过**：初稿认为 ADMINAPI-006 ② 说反了 ——
理由是 go-zero `rest/engine.go` 的 native `TimeoutHandler` 在 `buildChainWithNativeMiddlewares` 里
**排在 `Authorize` 之前**（更外层）⇒ 「JWT 验签计入预算」⇒ 两侧一致 ⇒ 差异不成立。

**核到第 4 环才发现：native Timeout 根本没进链。**

`admincenter.go:34` 调了 `observability.DisableNativeRestTimeout(&c.RestConf)`，
它把 `conf.Middlewares.Timeout` 置 **false**（`observability/http.go:32`），
而 `go-zero@v1.10.2/rest/engine.go:154-155` 的条件 `if ng.conf.Middlewares.Timeout` 因此为假 ⇒ **`TimeoutHandler` 不在链上**。
真正计时的 3s 是 `NewHTTPMiddleware`（经 `server.Use` ⇒ `ng.middlewares` ⇒ 在 `engine.go:110-112`
**最后** append ⇒ 比 `Authorize` **更内层**）。

⇒ **台账原文「Go 的 3s 计时器在 Authorize 之后起算（JWT 验签不计入）」是正确的**，
差异**真实存在**（只在 JWT 验签这一段）。**初稿的「差异不成立」已撤回。**

> 🔴 **教训（已记入 Pitfalls：P-ADMINAPI-016）**：
> 「关掉框架自带机制」的调用（`Disable*` / `With*` 开关）是**读框架源码推中间件顺序时最容易漏掉的一环**。
> 漏了它 ⇒ 把「框架原生 Timeout 在 Authorize 外层」误当成生效 ⇒ 把**真实存在的差异**误判成「不成立」。
> ⇒ **上一条错误结论如果写进台账，就是台账自己造出了一条新漂移** —— 台账的错误比代码的错误更难发现，
> 因为它是「看起来有证据的错」。
> ⇒ 判据：**读消费开关的那段控制流，不是读声明**（AGENTS.md §3.3.1 第 1 条）。
> 完整证据链（5 环）见上文 ADMINAPI-006 的「2026-10-24 复核补记」。

## 3. 复核通过、**未动**的断言（避免下轮重复核）

| 组 | 条目 | 结论 |
|---|---|---|
| ARPC-001~008 | 8 条 | 全部与代码一致（含 `update_time` **6** 处、Go `updateuserlogic.go:105-113` 不 set Success ⇒ RAG 已去掉 `setSuccess(true)`、空 `default-password` 守卫 `:1075-1076`、DDL `extra_info` TEXT + 6 个 `timestamptz`、SchemaInitializer 重放 4 脚本、**RAG 8 处 `AdminSpans.traced(` ↔ Go 8 处 `tracer.Start`**、Go `errmsg.go` 字面量确为乱码、`= 0L` 与 Go 一致） |
| H-1~H-3 | 3 条 | 独立 Bean + `@Transactional`；`.last("FOR UPDATE")` 在 `AdminCreateAtomicService.java:119`（台账原文写 `116-120`，**范围正确**，含 119）；pom 里 flyway 只剩注释 |
| 签字偏离 | 7 条 | 全部一致（SD-01 `claim instanceof Number`、SD-06、SD-07、SD-08 `ERROR_TOKEN_RUNTIME(1006)`、N-1 401 envelope、N-4 `IdentityResolver.claimedUID`、N-5 `padToHmacKeySize`） |
| 前提不可达 | 3 条 | ADMINAPI-002 / 005 / 008（`getSelf`/`getUser` 找不到抛 `Status.NOT_FOUND`） |
| 契约 | 2 条 | 黑名单 key 两侧逐字节一致（`admin:jwt_blacklist:<token>`）；proto 13 RPC 逐字节一致（`check-proto-static.sh` EXIT=0） |

## 4. 本轮顺带改的代码（**1 处，纯注释**）

- `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/metrics/AdminApiMetrics.java`
  - javadoc「**17 个**具名 Counter」→「**16 个**」（并在注释里写明「2 个 label 名 / 16 个组合」的区别）。
  - 补一段：RAG exposition **多一个 `application` label**（`management.metrics.tags`），Go 侧没有。

> ⚠️ **只改注释，零行为改动** ⇒ 不需要重跑测试门禁（见 §6）。

## 5. 跨域待办（**数字更新**，仍开放）

| 项 | 归属 | 本轮复核后的状态 |
|---|---|---|
| `users_user.extra_info` 的 `CAST(... AS jsonb)` 仍在 `AdminDomainService.java:962` | users 域 | 🟢 **2026-10-27 订正：已关闭** —— 那句 CAST **已被删除**（global B10 `ff80214`，2026-10-25；`AdminDomainService.java:1033` 现为 `wrapper.setSql("extra_info = {0}", …)` ⇒ **text 绑定**）⇒ 本条**不再开放** |
| user-api 的 400 仍用 `headers.setContentType(MediaType.TEXT_PLAIN)`（缺 charset） | users/user 域 | 仍开放 |
| message-rpc 的 Flyway `baseline-on-migrate: true` 且无 `baseline-version` ⇒ V1 被 baseline 吞掉 | message 域 | 仍开放（修法：加 `baseline-version: 0`） |
| `AGENTS.md §0` users 行三处出入（`update_time ×5`→**×6**、撤销「`status=0L` 未修」、`extra_info` 标注 users/user 域已改） | **共享文件** | 仍开放，合并时统一改 |

## 6. 本轮是否需要重跑测试

**不需要。** 本轮改动 = 2 个台账文件 + 1 个 javadoc（零行为改动）
⇒ 无编译期/运行期影响 ⇒ **不构成 §3.3.3 的「改了实现要配守门」情形**。

⚠️ 但**上一轮的实现改动**（ADMINAPI-009 v2 等）已有守门网：
`mvn -o -pl service/users/admin/admin-api,service/users/admin/admin-rpc -am test`
⇒ **admin-api 129/0/0/0、admin-rpc 99/0/0/0，skipped=0**（2026-10-24 实测，见上文 §9）。

---

<!-- ═══════════════════════════════════════════════════════════════════════ -->
<!-- 2026-10-24 完整性复核轮：「台账是否最新、最全」体检 -->
<!-- ═══════════════════════════════════════════════════════════════════════ -->

# 2026-10-24 完整性复核轮（admin 域 · 「台账是否最新、最全」体检）

> **核对日期**：2026-10-24　**分支**：`align/users-admin`
> **问题**：「台账是不是当前代码的真实全貌？」
> ⇒ 分三个维度查：**① 覆盖完整性**（代码里有没有行为点台账没写）
> **② 时效性**（台账写的「已修/未修」现在还成立吗）
> **③ 内部一致性**（链接、锚点、行号引用是否还指得对）

## 0. 结论摘要

| 维度 | 结论 |
|---|---|
| **① 覆盖完整性** | ✅ HTTP 端点 **13/13**、gRPC RPC **13/13**、共用 DDL（`03`/`04`）、`admin-proto`（13 rpc 逐字节）、7 条签字偏离 —— **全部有记录**。<br>🔴 **但缺一整类维度**：**admin-rpc 的 gRPC 拦截器链**（Go 7 个 vs RAG 1 个）⇒ 已补 **DRIFT-ARPC-009**（见 `rpc.md`） |
| **② 时效性** | 🔴 **DRIFT-ADMINAPI-007 的 nosniff 已被 `align/message` 分支跨域修掉（未合并）** ⇒ 本条合并后自动消失 ⇒ 已加处置纪律<br>🔴 **M-38 缺一条 charset 空格残差**（503/499 的线上字节与 Go 不同）⇒ 已补<br>🔴 **签字档 3 处链接失效** ⇒ 已修（见 §3） |
| **③ 内部一致性** | ✅ 行号引用逐条校准（`engine.go:154-155`、`server.go:222-235` 等）<br>🔴 **上一轮复核自己引入了一处误判**（H-2 行号）⇒ 已订正（见 §4） |

## 1. 跨分支影响体检（**合并时会发生什么**）

本域台账的若干断言**依赖共享模块的代码**，而这些共享文件正被别的域分支改动。
⇒ 逐个查了「别的域分支碰了什么」，结论：

| 共享文件 | 改动方 | 是否影响本域断言 |
|---|---|---|
| `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java` | `align/message` | 🔴 **影响** —— 补了 `nosniff` ⇒ **ADMINAPI-007 合并后自动消失**（已加处置栏） |
| `service/common/src/main/java/com/rag/common/protection/GoStyleCpuSheddingFilter.java` | `align/users-user` | ✅ **不影响** —— 只新增静态辅助方法 `elapsedRtMs`，**`@Order` 未变** ⇒ M-37 / M-39 的 filter 顺序断言仍成立 |
| `service/users/users-common/src/main/java/com/rag/users/common/jwt/JwtUtil.java` | `align/users-user` | ⚠️ **不影响 N-5 的结论** —— 改的是 `signWith` 的实现（`Keys.hmacShaKeyFor` → 显式 `SecretKeySpec(..., "HmacSHA256")` + `SignatureAlgorithm`），**`padToHmacKeySize` 本身未动** ⇒「Go 签的 token 在 RAG 验不过」仍成立。**但**：该改动顺带修掉了「密钥 ≥48 字节时 `header.alg` 漂移到 HS384/HS512」的问题 ⇒ **N-5 签字档引用的代码已变**，合并后应复核一次 |
| `service/users/users-common/db/01_create_user.sql` | `align/users-user` | ✅ 不影响（属 users/user 域；admin-rpc 只**读** `users_user`） |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/AdminRpcApplication.java` | `align/message` | 🔴 **影响** —— 给 admin-rpc 补挂熔断/降载链 ⇒ **DRIFT-ARPC-009 合并后自动消失**（详见 `rpc.md`） |

> ⚠️ **另一条需要合并时留意**：别的域分支**动了我域的测试文件**
> （`align/message` 新增 `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminProtectionChainWiringTest.java`）。
> ⇒ 合并时该文件**新增**，与本分支无冲突；但**归属上它属于 admin-rpc（我域）**，
> 其结论（熔断/降载链是否真挂上）**应记进本域台账** ⇒ 本轮已记（`rpc.md` 的 DRIFT-ARPC-009）。

## 2. 判定为「已记录、无需动」的项（避免下轮重复查）

| 项 | 台账位置 |
|---|---|
| 表名 `admins`/`admin_invites`（Go）vs `admin_admin`/`admin_invite`（RAG） | `rpc.md:1289` + `Intentional_Deviations/users/admin/_shared.md:22`（**族内约定**，非缺陷；且标注「两侧物理上不共用 schema」） |
| 400 响应的 `Content-Type` 分号后空格残差 | 本文件 §UNRESOLVED-002 段（**实测**：归一化后一致、逐字节不一致） |
| 13 个 HTTP 端点 / 13 个 gRPC RPC 逐条覆盖 | §4 Equivalence Matrix |
| `admin-proto` 13 rpc 逐字节一致 | `_shared.md:164` + `rpc.md:1066`（`check-proto-static.sh` EXIT=0） |

## 3. 本轮修掉的 3 处失效链接（**签字档**）

`docs/Intentional_Deviations/users/admin/{api,rpc}.md` 里指向本域 DRIFT 台账的相对路径**少了一级**：

| 文件 | 原（失效） | 改成 |
|---|---|---|
| `Intentional_Deviations/users/admin/api.md` | `../../DRIFT/users/admin/api.md` | `../../../DRIFT/users/admin/api.md` |
| `Intentional_Deviations/users/admin/rpc.md`（2 处） | `../../DRIFT/users/admin/rpc.md` | `../../../DRIFT/users/admin/rpc.md` |

> 从 `docs/Intentional_Deviations/users/admin/` 出发，`..`→`users/`、`../..`→`Intentional_Deviations/`、
> `../../..`→`docs/` ⇒ 目标在 **`docs/DRIFT/...`** ⇒ 必须三级。
> ⚠️ 这类链接**平时点不出来就没人发现** —— 它不影响台账正确性，但会让「顺着签字档找证据」的人扑空。

## 4. 🔴 本轮订正了**上一轮复核自己引入的误判**

上一轮（复核轮）在 §3 的表里写了：
> 「`.last("FOR UPDATE")` 实际在 `AdminCreateAtomicService.java:120-123`（台账写 116-120，**行号微偏**）」

**这是错的**：`.last("FOR UPDATE")` 在 **119** 行，**落在台账原文 `116-120` 范围内** ⇒ **台账原文是对的**，
是我的「订正」把一处本来正确的引用改错了。

⇒ **与 P-ADMINAPI-016 同源**：复核时如果预设「台账总该有几条是错的」，就会制造出「假订正」。
⇒ 本轮已在 §3 的表里改回，并在此留痕（**不静默改掉**）。

## 5. 只报告、不改（**共享文件 / 跨域**，本域无权动）

| # | 事项 | 归属 |
|---|---|---|
| 1 | `docs/DRIFT/_INDEX.md` 的证据强度行仍写「`admin-api` 的上限是**静态源码**」⇒ **已过期**：admin-api 已拿到**双侧活体对拍**（§UNRESOLVED-006 关闭，admin 22 步 + 全量 39 步，`unexpected=0`） | **共享索引** |
| 2 | `docs/DRIFT/_INDEX.md` 记「`admin-proto` **无专属台账文件**」⇒ 事实层已覆盖（`_shared.md` + `rpc.md`），但索引口径与事实层不一致 | **共享索引** |
| 3 | `AGENTS.md §0` users 行三处出入（`update_time ×5`→**×6**、撤销「`status=0L` 未修」、`extra_info` 标注 users/user 域已改） | **共享文件 `AGENTS.md`** |
| 4 | `Intentional_Deviations/users/admin/_shared.md` 的 **N-5** 引用的 `JwtUtil` 已被 `align/users-user` 改动（padding 未变，但签名实现变了）⇒ 合并后应复核一次 | **本域**（下轮复核） |
| 5 | `align/message` 在 `service/common` 补的 nosniff **顺带带出一个新事实**：Tomcat 的 `Content-Type` 重渲染会吃掉分号后空格 ⇒ **所有 api 组件**的 400/499/503 都有这条残差（⇒ 归「框架限制」） | **跨域**（已在 M-38 / ADMINAPI-007 记） |

## 6. 本轮改动清单

| 文件 | 改动 | 性质 |
|---|---|---|
| `docs/DRIFT/users/admin/api.md` | M-38 补 charset 残差 + nosniff 处置栏；ADMINAPI-007 补处置栏与残差；§3 表订正 H-2 误判；新增本章节 | 台账 |
| `docs/DRIFT/users/admin/rpc.md` | 新增 **DRIFT-ARPC-009**（gRPC 拦截器链缺口）+ 完整性复核轮章节 | 台账 |
| `docs/Intentional_Deviations/users/admin/api.md` / `rpc.md` | 修 3 处失效链接 | 台账 |
| `docs/Pitfalls/users/admin/api.md` | 追加 **P-ADMINAPI-017**（复核的「假订正」风险） | 踩坑档 |

**本轮零代码改动** ⇒ 不需要重跑 admin-api 门禁（上次全量门禁见 §9：admin-api **129/0/0/0**、admin-rpc **99/0/0/0** ⇒ ⚠️ 后者已被本轮更新为 **103/0/0/0**，见 [`rpc.md`](rpc.md) §5.4）。

---

# 2026-10-26 global 第五轮通知消化（admin-api 侧：路径引用清零 + 一条跨域结论）

> **本轮零代码改动** ⇒ 不需要重跑 admin-api 门禁（上次：admin-api **129/0/0/0**、`skipped=0`）。
> ⚠️ **但本域的 admin-rpc 门禁本轮有更新**（新增 2 条守卫 ⇒ **103/0/0/0**）⇒ 详见 [`rpc.md`](rpc.md) §5。
> **详细推导与证据链在 [`rpc.md`](rpc.md) 的「global 第五轮通知消化」章节**（本节只记 admin-api 侧的净变更）。

## 1. 🟢 第 1 层报的 7 处路径引用已展开为完整路径（**🔴 根因判断已订正**）

旧版共享工具 `tools/check-ledger-selfcheck.py --domain users-admin` 的**第 1 层**
（「台账引用的仓库内路径是否存在」）报 **33 处 MISSING**，其中 **7 处在本文件**。

🔴 **订正（2026-10-26，users/user 域提醒 ⇒ 本域复核）**：我原先写的根因是
「**缩写式路径对工具永久不可解析 ⇒ 第 1 层对它永远瞎 ⇒ 路径写错也没机制能发现**」
⇒ **这句在 global 的 `35a7b8e` 之后不成立** —— 新版第 1 层给简写加了去噪
（**N8** 省略写法 / **N9** 裸文件名 / **N10** 短路径 / **N11** Go 侧路径 / **N12** 仓外引用）
⇒ **简写被正确识别为「简写」并放过，不再报成「缺失」** ⇒ **那 33 处是旧版工具的口径问题**。
⇒ 且本轮 33 处**逐条解析全部唯一命中真实文件** ⇒ **零「真缺失」** ⇒ **本域路径本来就都指向真实文件**。

⇒ **仍然展开的理由（已改写）**：① **展开 = 从「不被检查」变成「被检查」**（缩写式只靠去噪规则放过，工具**不验证**它）
② 人和工具都能直达。⚠️ **仍不违反「共享层引用用符号名」**（P-15）：那条针对**行号**，完整路径不漂移
⇒ **行号 → 符号名 / 路径 → 完整路径**。

⇒ **新版工具复测（2026-10-26）**：**层 0~4 全部通过**；第 1 层「**台账引用路径 73 个 ⇒ 缺失 0 个**」；
第 4 层「**引用不悬空: 168 条**」。
⚠️ 但工具自己写明：第 2/3 层对 `users-admin` 域 **「不适用 —— 不是『通过』，是『没查』」** ⇒ **不得拿「工具全绿」当「已复核」**。
⇒ 完整经过与元教训（**本域第 3 次「跨域销账通知要自己核」**）⇒ 详见 [`rpc.md`](rpc.md) §8。

## 2. 一条**跨域结论**（admin-api 侧受益，但成因在 `admin-rpc`）

global **E47**（业务层把「SQL 被 deadline 取消」改写/吞掉 ⇒ 事务 / 指标 / wire 三重口径失真）
⇒ 🟢 **admin 域不命中**：admin-rpc 的 **30 处**宽 catch **全部 1:1 配对** `catch (StatusRuntimeException e) { throw e; }`
⇒ `DEADLINE_EXCEEDED`（由 `SqlTimeoutStatusTranslator` 抛的 `io.grpc.StatusRuntimeException`）**原样透传**
⇒ **admin-api 侧收到的 wire 与 Go 一致**（本域无待办）。
⚠️ **但这不等于 admin 域「超时语义已闭环」** —— 真正的残余是**非 SQL 路径**
（admin `logout` 的 Redis 写不绑 deadline ⇒ ⇒ `DRIFT-ARPC-010`），
且 `Δ_end` 的**修后值待实测**（探针未进编排 ⇒ global **E48**）。
⇒ 详见 [`rpc.md`](rpc.md) 同轮章节 + [`docs/Pitfalls/users/admin/rpc.md`](../../../Pitfalls/users/admin/rpc.md) 的 **P-16**。

## 3. 本轮改动清单（api.md 侧）

| 文件 | 改动 | 性质 |
|---|---|---|
| `docs/DRIFT/users/admin/api.md` | 7 处路径展开 + 本章节 | 台账 |
| `docs/DRIFT/users/admin/rpc.md` | 6 处断言订正 + 1 个新章节 + 33 处路径展开 | 台账 |
| `docs/Pitfalls/users/admin/rpc.md` | 追加 **P-16**（仪器口径 ≠ 归因） | 踩坑档 |

**零代码改动** ⇒ 不需要重跑测试门禁。

---

## 12. 🆕 `DRIFT-ADMINAPI-006 ②` 的**实施记录**（2026-10-27 · G1 lane · 跨服务推广）

> **元信息**：落码分支 `align/g1-users-follow`（基线 `a282012` = `do-my-own-gh`）·
> **写权内**（全部落 `service/users/admin/admin-api/**`）·
> **证据强度**：**双侧活体成立，但两侧判据强度不对称** —— ⚠️ **不笼统写「L4」**（据 G2 回执的建议 · 2026-10-27 订正）：
> **Go 腿 = L4 活体 · 直接判据**（G2 建的探针 `/tmp/goprobe/probejwt/main.go`，**本域已独立复跑**，三腿全中 ⇒ 见 `user/api.md` §9.y.0）·
> **RAG 腿 = L3 真 Tomcat · 间接判据**（**真 Tomcat**，10 条探针：行为层 401/413/404 的真实响应形状 ＋ 装配层 Filter 链实际排序 —— **RAG 侧没有等价的 ctx-deadline 观测点** ⇒ 只能由行为与装配反推）＋ **L2 装配层守门**
> ⇒ ⇒ **合并判据强度按弱侧记 = L3（弱侧口径）**；**只有 Go 腿单独是 L4** ⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」。
> ⚠️ **两次独立运行**（Go 腿与 RAG 腿不在同一次编排里）⇒ 比的是**属性**（「验签是否计入 3s 预算」），**不是逐字节 diff**。
> ⚠️ **未跑「同一套请求矩阵逐字节对拍」** ⇒ 按 AGENTS.md §0，读作「**在该证据强度下一致**」。
>
> ⚠️ **本条不是新差异，是「推广」** —— 差异本体早已登记（`DRIFT-ADMINAPI-006` 的 ② 半），
> 本轮把它**修掉**；① 半（遥测面）**未动**。

### 12.1 Go 侧链序（逐行核实 · 真源 `service/user/admin/api/`）

```
service/user/admin/api/admincenter.go:34  httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)
service/user/admin/api/admincenter.go:36  server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, ...))
                                          ⇒ observability 是**唯一**的 user middleware
go-zero@v1.10.2/rest/engine.go:101-113     bindRoute：
                                            chn = buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
                                            chn = appendAuthHandler(fr, chn, verifier)   // ← JWT 验签
                                            for _, mw := range ng.middlewares { ... }    // ← observability(3s) 在 JWT **之内**
                                            handle = chn.ThenFunc(route.Handler)
go-zero@v1.10.2/rest/chain/chain.go:38-43  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
go-zero@v1.10.2/rest/server.go:222-233     WithMiddleware ⇒ middleware(route.Handler) ⇒ 黑名单最内层
⇒ 执行序：原生中间件 → **Authorize(JWT)** → **observability(3s 计时)** → **黑名单** → handler
⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **JWT 验签耗时不计入 3s 预算**
```

⚠️ **前提不能漏**：Go 的 native `TimeoutHandler` 被 `DisableNativeRestTimeout` 关掉了 ⇒ 3s **只**由 observability 提供
⇒ 「验签在预算之外」才有意义（否则 native 那条链会让计时器回到 Authorize 外层）。
⇒ Go 侧 `admin/api/internal/handler/routes.go:32-34,93` 的第二段 AddRoutes 与 user / follow **完全同构**。

### 12.2 落点窗口与共享层槽位（`service/common` **只读** · 2026-10-27 重算）

| `@Order` | Filter | 作用 | 出处 |
|---|---|---|---|
| **+3** | `GoStyleTraceparentFilter` | 起 SERVER span | `GoStyleTraceparentFilter.java:190` |
| **+4** | `GoStyleMaxConnsFilter` | 连接数闸门 | `GoStyleMaxConnsFilter.java:96` |
| **+5** | `GoStyleBreakerFilter` | 熔断 | `GoStyleBreakerFilter.java:77` |
| **+6** | `GoStyleCpuSheddingFilter` | CPU 降载 | `GoStyleCpuSheddingFilter.java:63` |
| **+7** | `HttpMiddlewareFilter` | **3s 预算起算点** | `HttpMiddlewareFilter.java:37` |
| **+8** | `GoStyleMaxBytesFilter` | 请求体上限 | `GoStyleMaxBytesFilter.java:83` |

⇒ **合法窗口 `[+3,+6]`**（≥+3 才在 SERVER span 内；≤+6 才出 3s 预算）—— 四个槽位（+3/+4/+5/+6）**全被共享层占满**
⇒ 只能**并列** ⇒ 落 **+6**（与 `GoStyleCpuSheddingFilter` 并列）。
⇒ **并列无害**：本 Filter **绝不写响应** ⇒ 与并列者的先后**不改变任何响应**。

> ⚠️ **本轮又踩了一次「台账里的 @Order 过期」** —— 本文件 §7 的 006 条原文写 `HttpMiddlewareFilter` 是 `+5`，
> **实测是 `+7`** ⇒ 照抄旧数字会把「验签该落在哪一档」算错。
> ⇒ 已订正（§3.1 / §7 的 006 条 / 本节）。

### 12.3 ✅ 实际落地的形态：**「Filter 只验签、interceptor 抛异常」**

```
[Traceparent +3] → [MaxConns +4] → [Breaker +5] → [CpuShedding +6] → [★ JWT 验签 +6]
   → [3s 预算 +7] → [MaxBytes +8] → DispatcherServlet → [AdminJwtAuthInterceptor：抛 401 + 黑名单] → Controller
```

| 新增/改动 | 落点 | 职责 |
|---|---|---|
| `auth/AdminJwtAuthPaths.java` | admin-api | public/protected 判据（**Filter 与 interceptor 共用同一份** ⇒ 两个缝隙同时消失） |
| `auth/AdminJwtVerificationResult.java` | admin-api | 验签结果载体（**失败带「异常对象」而非标志位** ⇒ `@ExceptionHandler` 仍管得到 ⇒ 401 信封形状逐字不变） |
| `auth/AdminJwtSignatureVerifier.java` | admin-api | 验签本体（**只验签，不写响应、不查 Redis**；密钥构造 `JwtUtil.padToHmacKeySize` 也一并搬进来） |
| `auth/GoStyleJwtAuthFilter.java` | admin-api | `@Order(+6)`：**只验签、只写 request attribute、绝不写响应** |
| `interceptor/AdminJwtAuthInterceptor.java`（改） | admin-api | 消费 Filter 的验签结果 → 失败**原样重抛** → 黑名单检查（**留在预算内**，与 Go 一致） |

⇒ **② 闭环**：验签发生在 3s 预算**之外** ⇒ 与 Go 的「计时器在 Authorize 之后起算」一致 ⇒ **M-37 由 OBSERVABLE DIFFERENCE 改判 EQUIVALENT**。
⇒ **① 不变**：401 仍由 interceptor 抛 ⇒ 仍在 `HttpMiddlewareFilter` **之内** ⇒ 仍计入观测数据 ⇒ **M-39 仍为 OBSERVABLE DIFFERENCE**。
⇒ ⚠️ ⇒ **本域签字档不受影响**（`SD-2026-10-23-08` / `SD-2026-10-23-01` 继续有效；本轮**刻意不**制造销号需求 —— 签字档不在 G1 写权内）。

### 12.4 🔴 承重不变式：**`GoStyleJwtAuthFilter` 绝不写响应**

- 401 **只能**由 interceptor 抛（`@ExceptionHandler` 仍管得到 ⇒ N-1 / M-05 的信封形状不变），
  且发生在**所有 Filter 之后** ⇒ MaxBytes 的 **413 仍压过 401**（Go 侧 MaxBytes 是 native middleware、排在 Authorize **之外** ⇒ Go 也是 413 压过 401）。
- ⇒ ⚠️ **改本类时若要写响应，必须同时重做「413 压过 401」的语义论证。**

### 12.5 守门 + 变异验证（**绿测试必须是错误答案的守卫**）

守门：`AdminApiJwtVerificationScopeTest`（**6 例**，新建）——
① Filter 已注册 · ② **容器装配出的链**里 JWT Filter 排在 3s 预算 Filter **之前**
（断言的是 `ServletContextInitializerBeans` 的**装配结果**，不是两个 `@Order` 数字）·
③ interceptor 消费的是 Filter 的结果（`request().attribute(...) = "filter"`）· ④ **401 信封形状不变** ·
⑤ **超限 body ⇒ 413**（压过 401）· ⑥ 路径判据自检（login / create 是 public，其余受保护）。

| # | 变异 | 期望变红的守门 | 实测 |
|---|---|---|---|
| 1 | JWT Filter 的 `@Order` +6 → **+8** | ② 装配顺序 | ✅ 红 |
| 2 | Filter **自己写 401** | ⑤ 超限 body | ✅ 红（`expected:<413> but was:<401>`）⇒ **实证「413 压过 401」被打破** |
| 3 | 摘掉 Filter 的 `@Component` | ③ interceptor 消费 filter 结果 | ✅ 红（`expected:<filter> but was:<inline>`） |

⇒ **3/3 变红** ⇒ 守门**不是假绿**。
⇒ ⚠️ 连带项：`AdminSemanticDriftApiRegressionTest` 有 **8 处** `new AdminJwtAuthInterceptor(properties, redis)`
⇒ 已改为 `new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis)`（走**兜底 inline 分支**，与修前**逐字一致**）
⇒ 该类已在 javadoc 注明「本类现走兜底分支、生产路径由新守门覆盖」。

### 12.6 ✅ 真启动复验（**真 Tomcat** · 2026-10-27 · L3 活体）

环境：真 PG `127.0.0.1:56432/g1probe` + 真 Redis `57379` + 真 Nacos `8848`（`G1_GROUP`）+
`user-rpc`/`admin-rpc`/`follow-rpc`/`admin-api`/`follow-api` **五个进程全部真起**（admin-api 7.275s · follow-api 6.998s）。

| # | 探针 | 实测 | 判定 |
|---|---|---|---|
| 1 | admin 无 token（`/admin/v1/getself`） | **HTTP 401** + `{"code":1006,"msg":"TOKEN已过期","data":null}` | ✅ admin 侧 401 = **完整信封**（不是空 body） |
| 2 | admin 合法 token | **HTTP 200** + `{"code":1003,"msg":"用户不存在"}` | ✅ **不是 401** ⇒ 验签通过、进到业务层 |
| 3 | admin 坏签名 | **HTTP 401** + 同一信封 | ✅ |
| 4 | admin 超限 body + 无 token | **HTTP 413** + **空 body** | ✅ **413 压过 401**（响应头层，MockMvc 测不到） |
| 5 | admin public（`/admin/v1/login`） | **HTTP 200** | ✅ public 放行 |
| 6 | follow 无 token（`/follow/v1/follow/list`） | **HTTP 401** + **空 body** | ✅ follow 域特有的 **401 空 body** 形状逐字不变 |
| 7 | follow 合法 token | **HTTP 200** + `{"code":5101,"msg":"user not found"}` | ✅ **不是 401** ⇒ 验签通过、进到业务层 |
| 8 | follow 坏签名 | **HTTP 401** + 空 body | ✅ |
| 9 | follow 超限 body + 无 token | **HTTP 413** + 空 body | ✅ 同上不变式 |
| 10 | follow 未注册路径 | **HTTP 404** + `text/plain` `404 page not found` | ✅ **不被鉴权遮蔽** ⇒ `handler instanceof HandlerMethod` 判据保留 |

⇒ **10/10 符合预期**；日志里除**预期内**的 `AuthenticationException`（admin 1006 / follow 5004）与业务错误码外无异常。

⇒ **6 组件回归**：`admin-api` **135** · `admin-rpc` **115** · `user-api` **118** · `user-rpc` **137** · `follow-api` **63** · `follow-rpc` **78`
⇒ **646 条全绿**（`BUILD SUCCESS`，5:48）。
⚠️ **上一轮那次「回归」不算数** —— 当时命令末尾接了 `| head -40`，`head` 先退出 ⇒ maven 收到 **SIGPIPE 被掐断**
⇒ 退出码取到的是 `head` 的 0（**管道退出码取的是最后一个命令**）⇒ 看着像绿，实际 user-rpc 有一条
`UserRpcGrpcInterceptorWiringTest` 报 `UNAVAILABLE`（掐断的残留）⇒ **重跑（写文件、不接管道）后 137/0/0 全绿**。

### 12.7 ⚠️ 残余差异（如实登记，**不冒充已对齐**）

1. **JWT 验签与过载保护的相对次序**：Go 的 Authorize 排在 MaxConns / Breaker / Shedding **之后**
   ⇒ 过载被拒的请求**不做**验签；RAG 的验签 Filter 与 `CpuShedding`(+6) **并列**
   ⇒ 并列落在 Shedding 之前时**多做一次无用验签**。
   ⇒ **不改变任何响应**（只多一次 HMAC 计算）⇒ **只在过载时可见、只影响 CPU、wire 面零影响**。
2. **`@Order` 并列的确定性**：+6 已被 `GoStyleCpuSheddingFilter` 占用 ⇒ Spring 对**同值**按 bean 定义注册顺序稳定排序
   ⇒ 本轮实测装配结果满足「JWT 在预算之前」（+6 **严格小于** +7 ⇒ 与并列者的先后**无关**）
   ⇒ ⇒ **该并列在本语义下无害**（因为验签 Filter 不写响应）。
3. **①（401 计入观测面）仍在** ⇒ M-39 仍为 OBSERVABLE DIFFERENCE ⇒ 本条**不销号**。
4. **admin 侧畸形 claim 仍是 401**（`AdminJwtSignatureVerifier:101-113`），而 user 侧按 `DRIFT-USERAPI-001` 改成「不拒绝、交给 identity 层」
   ⇒ ⚠️ **两域刻意不统一**（本轮**不**趁机跨域「修好」）。

### 12.8 🟡 跨服务推广状态（2026-10-27）

| 服务 | 属哪个 lane | 本轮状态 |
|---|---|---|
| `user-api` | **G1** | ✅ 已修（上一轮提交 `49c298e`） |
| `admin-api` | **G1** | ✅ **本轮已修** |
| `follow-api` | **G1** | ✅ **本轮已修**（`DRIFT-FOLLOW-010`，见 [`follow/api.md`](../../follow/api.md)） |
| `like-api` / `message-api` | **G2** | 🟡 待推广（配方见 [`user/api.md` §9.y §7](../user/api.md)） |
| ~~`task-api`~~ | ~~G2~~ | ❌ **N/A · 本条不适用**（**2026-10-27 由 G2 指出 · 本域已独立核实成立**）—— Go 侧 `service/task/api/internal/handler/routes.go` **零 `rest.WithJwt`**（实测 `grep -c WithJwt` = **0**、**1** 个 `AddRoutes` 块、唯一端点 `POST /taskcenter/v1/task/get`）；RAG 侧 `service/task/task-api` **无 `WebMvcConfig`、无任何 `*Jwt*`/`*Auth*`/`*Interceptor*` 类**（`TaskController.java:35` 的 `WithJwt` 只出现在**注释**里，说明 Go 侧刻意不加）⇒ **两侧都没有验签环节 ⇒ 不存在 3s 预算作用域漂移** |
| `article-api` / `comment-api` / `favorite-api` | **G3** | 🟡 待推广（同上） |
| ~~`hot-api`~~ | ~~G3~~ | ❌ **N/A · 本条不适用**（**2026-10-27 由 G3 指出 · 本域已独立核实成立**）—— Go 侧 `service/hot/api/internal/handler/routes.go` **零 `rest.WithJwt`**（实测 `grep -c WithJwt` = **0**，且只有 **1** 个 `AddRoutes` 块、唯一端点 `GET /hot/v1/articles`）⇒ Go 的 hot-api **根本没有验签环节**；RAG 侧 `service/hot/hot-api` **无 `WebMvcConfig`、无任何 `*Jwt*`/`*Auth*` 类**（`HotArticleController.java:31` 的 `rest.WithJwt` 只出现在**注释里**，说明 Go 侧刻意不加）⇒ **两侧都没有「验签」这个环节 ⇒ 不存在 3s 预算作用域漂移**。<br>⚠️ **别把「RAG 没有鉴权」当漂移** —— 热榜本就该匿名读。 |

⇒ **推广前必查每个域自己的两个数**：该域 api 的 `WebMvcConfig` 注册的路径前缀 ⇒ 决定 `JwtAuthPaths` 的前缀常量；
以及该域的 **public 路径集合** 与 **401 body 形状**（admin/user = 完整信封；follow = 空 body —— **不要统一**）。
⇒ **🆕 G3 三域的逐域注记（2026-10-27 G3 回执，本域已独立核过 Go 侧链序与 hot-api 的 N/A 结论）**：
| 域 | 401 body | 黑名单 | 受保护路径判据 | 验收成本 |
|---|---|---|---|---|
| `favorite-api` | 三字段信封（已签字 `DEV-FAV-API-001`） | 有（Redis） | 全保护（Go 单块带 `WithJwt`） | 🟢 **最低**（已有真 Tomcat 守门 `FavoriteApiRealTomcatGateTest`） |
| `comment-api` | **空 body** | 无 | 全保护 | 🟡 需新写真 Tomcat 用例（无真 Tomcat 测试 ⇒ 测不到「413 压过 401」这层） |
| `article-api` | **空 body** | 无 | interceptor 内 `PROTECTED_ROUTES`（4 条，已 EQUIVALENT） | 🟡 同上（比 favorite 贵） |

⇒ **🆕 G2 两域的逐域注记（2026-10-27 G2 回执）** —— **抄配方前必须逐域自己核**：
| 域 | 401 body | public 路径 | ⚠️ 配方要改的地方 |
|---|---|---|---|
| `like-api` | （G2 域内口径） | **无 public 路径**（Go 6 条路由 **100% 带 `WithJwt`**） | Filter 的「是否 protected」判据**就是前缀本身** ⇒ 不需要像 user 域那样拆 public/protected 两张表 |
| `message-api` | （G2 域内口径） | **无 public 路径**（Go 13 条路由 100% 带 `WithJwt`） | 🔴 **有两把 key**：Go `routes.go` **两段** `AddRoutes` —— 8 条 `/message/v1/**` 用 **UserAuth** secret、5 条 `/message/v1/admin/**` 用 **AdminAuth** secret ⇒ Filter 里**必须按路径分派 key** ⇒ 直接抄「一把 key」的写法会**验错**（拿 admin token 打 user 端点 / 反之 ⇒ 两侧都 401） |

### 12.9 🟡 **守门缺口**（非漂移）：admin-api 的 Redis 超时口径**本就对**，但**零守门**

> 🆕 **2026-10-27 复核**（给 follow / user 两个 api 对齐 Redis 超时时顺手查出）。
> ⚠️ **本条不是 DRIFT** —— admin-api 的两个值**本来就与 Go 逐字一致** ⇒ **不占 DRIFT 编号**。

| 项 | 值 | Go 侧对应 | 状态 |
|---|---|---|---|
| `spring.data.redis.timeout` | **3s** | go-redis v9 `options.go` init() 的 `ReadTimeout = 3s` | ✅ 已对 |
| `spring.data.redis.connect-timeout` | **5s** | go-redis v9 `options.go` init() 的 `DialTimeout = 5s` | ✅ 已对 |

⚠️ **但零守门** ⇒ 任何人改 `application.yml` 都不会被发现 ⇒ 同族三域（user / admin / follow）
当时是**三种口径**（user = 3s 但缺 connect-timeout · admin = 3s+5s · follow = 2s 且缺 connect-timeout）⇒ **正是「无守门」的代价**。

⇒ **本轮补**：`AdminApiRedisTimeoutConfigTest`（**2 例**，新建）——
① yml 里 `timeout=3s` ＋ `connect-timeout=5s` 逐字断言 ·
② **用 Spring 真实的 `Binder` 走一遍 relaxed binding**，断言 `DataRedisProperties.getConnectTimeout()==5s`
与 `getTimeout()==3s`（⇒ 证明属性名写错时 Spring **静默用默认值**，那正是要挡的静默失效）。
⇒ ⚠️ **admin-api 的 `application.yml` 本轮零改动**（值本就对）⇒ 本条**只加守门**。
⇒ **同款守门在 admin-rpc 侧早已有**（`AdminRedisTimeoutConfigTest`）⇒ 本条是把它**补到 api 侧**。
⇒ **变异验证 2/2 变红**：把 `connect-timeout` 改成 `3s` ⇒ 两条都红
（`expected:<5s> but was:<3s>` / `expected:<PT5S> but was:<PT3S>`）。
⇒ **回归**：**6 组件 BUILD SUCCESS**（`admin-api` **137** = 135 + 本轮 2 例 · `admin-rpc` **115** · `user-api` **120** · `user-rpc` **137** · `follow-api` **65** · `follow-rpc` **78** ⇒ **652 条全绿**）。

### 12.10 🟢 **socket 层那一截已收口**（2026-10-27 · G1 lane 续轮）

> **本节把 §12.9 那句「仍不证明 socket 层」补上。**

| 层 | 断言什么 | 守门 | 强度 |
|---|---|---|---|
| ① 文本层 | yml 里 `timeout: 3s` ＋ `connect-timeout: 5s` 逐字 | `AdminApiRedisTimeoutConfigTest` | L1 静态 |
| ② 绑定层 | Spring 真实 `Binder` ⇒ `DataRedisProperties.getConnectTimeout()==5s` | 同上 | L2 |
| ③ **消费层** | Lettuce 客户端 `ClientOptions.socketOptions.connectTimeout == 5s` ＋ **socket 层实测** | 🆕 **`AdminApiRedisConnectTimeoutSocketTest`** | **L3 真容器** |

**③ 的做法**（照 user-api 同款）：
- 把 `spring.data.redis.host` 指到 **黑洞 IP `192.0.2.1`**（RFC 5737 TEST-NET-1 ⇒ TCP SYN 永不回包）
  ＋ **把 `timeout` 覆盖成 30s**（**为的是隔离出 connect 超时** —— Lettuce 的连接是**异步**的，
  不覆盖的话**先到期的永远是 3s 的命令超时**，测出来的时长没有判别力）；
- ⇒ **实测失败耗时 5169ms**（落在 `[4000ms, 9500ms)` —— 该区间**只**容得下 5s：
  ≈10s = Lettuce 默认值 ⇒ 属性没生效 · ≈3s = 回落到 timeout · ≈75s = TCP 系统默认）。
- ⚠️ **黑洞可用性是环境相关的** ⇒ 某些网络直接 `no route to host`（几毫秒就失败）
  ⇒ 时长断言用 `assumeTrue(elapsedMs >= 1000, …)` 跳过，**但 ①（配置消费点）永远生效**。

⇒ **变异验证 3/3 变红**（本轮）：admin 摘掉 `connect-timeout` ⇒ `expected:<PT5S> but was:<PT10S>`
· user 改 `3s` ⇒ `expected:<PT5S> but was:<PT3S>` · follow 摘掉 ⇒ `expected:<PT5S> but was:<PT10S>`。

> 🔴 **2026-10-27 订正（字节码实测 · 保留原值不改写）**：上面几处「**≈3s = 回落到 timeout**」的机制说法**是错的**。
> Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` 的**字节码**是：
> ```java
> Duration connectTimeout = getProperties().getConnectTimeout();   // :9
> if (connectTimeout != null) {                                     // :16
>     builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build());
> }
> ```
> ⇒ **null ⇒ 整段不执行** ⇒ 用 Lettuce 自己的默认
> `io.lettuce.core.SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION` = **10s**
> （`javap -constants` 实测：`DEFAULT_CONNECT_TIMEOUT = 10l`）
> ⇒ **不配 ⇒ 10s，不是 3s**。
> ⇒ **本域的 `connect-timeout: 5s` 配得对，修法与结论不变**；但**漂移方向的表述要改**（10s vs 5s，不是 3s vs 5s）。
> ⇒ ⚠️ 本轮**变异实测已经印证**：摘掉 `connect-timeout` ⇒ 客户端读数 `PT10S`（**不是** PT3S）
> ⇒ ⇒ 同一轮实测与本条订正**互相印证**（旧文写的是 PT3S，实测是 PT10S）。
> ⇒ 同款订正已加在 `admin-api` / `admin-rpc` 的 yml 注释与 4 个守门 javadoc 里（**保留原值不改写**）。
> ⇒ 详见同轮的 `DRIFT-ARPC-013`（admin-rpc 侧：**断连排队**那条真漂移）。

---

## 13. 🆕 `DRIFT-ADMINAPI-012` · Redis 断连 ⇒ 命令挂满 3s ⇒ **503**（Go 是 fail-open 200）—— **已修**（2026-10-27 · G1 lane）

> **元信息**：落码分支 `align/g1-users-follow` · **写权内**（`service/users/admin/admin-api/**`）·
> **证据强度**：🟡 **RAG 腿 = L3 真容器活体**（真 Redis 容器 ＋ **可控 TCP 代理制造 FIN**）
> ＋ **L1 Go 侧源码**（⚠️ **不是 L4 双侧活体** —— Go 腿的活体对拍只在 **user-api 域**做过，见 `user/api.md` 的 `DRIFT-USERAPI-014`；
> admin 域的 Go 腿是**源码逐行对照**，不是活体 ⇒ 按 AGENTS.md §0 记「在该证据强度下一致」，**不冒充 L4**）。

### 13.1 前提核实（**两侧逐行，不是「同族大概」**）

| 面 | Go（真源） | RAG | 结论 |
|---|---|---|---|
| 黑名单判据 | `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:35-36`<br>`exists, err := m.Redis.ExistsCtx(...)` → `if err == nil && exists { 401 }` | `AdminJwtAuthInterceptor.isBlacklisted:197-203`<br>`redisTemplate.hasKey(...)` → `catch (Throwable t)` | ⇒ **两侧都是「Redis 挂了 ⇒ err≠nil / 异常 ⇒ 判据为 false ⇒ 放行」**（fail-open）⇒ **目标形状一致** |
| Redis 失败耗时 | go-zero `core/stores/redis/redisclientmanager.go:32-39` 建 `red.Options` **不传任何 timeout** ＋ `maxRetries = 3`（`:18`）⇒ 连接被拒/被关时**每次重试立即失败** ⇒ **快速失败** | Boot 4 `LettuceConnectionConfiguration:209-230` **从不设** `disconnectedBehavior` ⇒ Lettuce 默认 `DEFAULT`（`DefaultEndpoint:149` / `:837-852`：`DEFAULT + autoReconnect=true ⇒ 命令排队等重连`）⇒ 挂到 `commandTimeout` | ⇒ **唯一的差别是「失败要花多久」** |
| HTTP 预算 | go-zero `rest/config.go:55` `Timeout int64 json:",default=3000"` | `application.yml` 的 `observability.http-timeout: 3s` | ⇒ **两侧同为 3s**（已对齐） |

### 13.2 漂移形态与**修前实测**

**故障形态必须是 FIN（优雅断开），不是 RST** —— 这是本条最贵的一课：
`container.stop()` 让容器被**强杀** ⇒ 连接收到 **RST** ⇒ Lettuce 往 socket 写命令时**立刻失败**
⇒ **根本走不到** `disconnectedBehavior` 分支 ⇒ 两种配置的耗时都是 1~13ms ⇒ **分不出**（2026-10-24 在 user-api 域实测踩出来）。
⇒ 线上真实形态是 `docker stop` ⇒ Redis **优雅退出** ⇒ **FIN** ⇒ Lettuce 进入 DISCONNECTED（正在重连）⇒ **命令被排队**。
⇒ 本轮用**可控 TCP 透传代理**精确复刻：**先关监听口**（重连被拒）＋ **再对已建立连接半关闭**（发 FIN）。

**修前实测（admin-api 本域，L3 真容器）**：

| 项 | 修前实测 | 修后实测 |
|---|---|---|
| Lettuce 断连策略 | `DEFAULT`（`expected: <REJECT_COMMANDS> but was: <DEFAULT>`） | `REJECT_COMMANDS` |
| 断连后 `hasKey` 耗时 | **3039ms**（变异复测 **3013ms**）⇒ 挂到 `commandTimeout: 3s` | **1ms** |
| 异常链 | `QueryTimeoutException: Redis command timed out` ← `RedisCommandTimeoutException: Command timed out after 3 second(s)` | `RedisException: Currently not connected. Commands are rejected.` |
| **对外形状** | ⇒ 撞 `observability.http-timeout: 3s` ⇒ **503 `Request Timeout`** | ⇒ 远小于预算 ⇒ 进业务层 ⇒ **fail-open 200**（与 Go 同形） |

### 13.3 修法：**域内副本**（🔴 **不写共享层**）

新增 `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/config/RedisDisconnectedBehaviorConfig.java`
—— 把 Lettuce 的断连行为设成 `ClientOptions.DisconnectedBehavior.REJECT_COMMANDS`。

⚠️ **为什么是域内副本而不是共享层**：
- 该类**本来就在 user-api 域内**（`service/users/user/user-api/.../config/`）⇒ **不是共享层文件**
  ⇒ 复制到 admin-api **完全在 G1 写权内**（`service/users/{user,admin}/**`）
  ⇒ **不需要等 G4、不阻塞本波**（AGENTS.md §5.3e「**域侧能自修的域侧修**」）；
- **共享层收编**（三份副本并成一个自动配置）是**可选的后续收敛** ⇒ 已作为反馈单派给 G4
  ⇒ **G4 落码时应当删除这三份域内副本**，避免双份配置。
- ⚠️ **本类只改「断连」这一种故障，不改「连着但不回包」**：后者两侧**都是 503**
  （Go 的 `ReadTimeout` 默认 3s ⇒ 同样撞预算；RAG 同样等满 `timeout: 3s`）
  ⇒ **两类故障分别对齐**，没有把其中一类改成另一类。

### 13.4 守门（**绿测试必须是错误答案的守卫**）

| 守门 | 例数 | 断言面 |
|---|---|---|
| 🆕 `AdminApiRedisDisconnectedFastFailTest` | **2** | ① 断连策略真被装配（读**运行时真正在用**的那份 `ClientOptions`）＋ Redis 可达时 `hasKey` 真通 · ② **真故障注入**（FIN）⇒ 断连后 `hasKey` **<1500ms** 失败（预算落在修前 3039ms 与修后 1ms 之间，**两侧都判得开**） |
| 🆕 `AdminApiRedisConnectTimeoutSocketTest` | **1** | 见 §12.10（socket 层 5s 连接超时） |

⇒ **变异验证 5/5 变红**（本轮全部新增守门）：
① admin 摘 `connect-timeout` ⇒ `expected:<PT5S> but was:<PT10S>`
② user `connect-timeout` 改 `3s` ⇒ `expected:<PT5S> but was:<PT3S>`
③ follow 摘 `connect-timeout` ⇒ `expected:<PT5S> but was:<PT10S>`
④ admin `REJECT_COMMANDS` → `DEFAULT` ⇒ 两条红，耗时实测 **3013ms**
⑤ follow `REJECT_COMMANDS` → `DEFAULT` ⇒ 两条红，耗时实测 **3019ms**
⇒ ⚠️ **变异脚本改的是本域 yml / java 文件，改完用 md5 校验还原**（本轮踩过「备份命名撞车 ⇒ 还原错文件」的坑）。

⇒ **回归（2026-10-27 本轮收口）**：**6 组件 BUILD SUCCESS**，逐模块取自 `target/surefire-reports/TEST-*.xml`（**按模块聚合，不是按行序 tail**）：

| 组件 | 上轮 | 本轮 | 增量 |
|---|---|---|---|
| `user-api` | 120 | **121** | +1（`UserApiRedisConnectTimeoutSocketTest`） |
| `user-rpc` | 137 | **137** | — |
| `admin-api` | 137 | **140** | +3（socket 1 ＋ 断连 2） |
| `admin-rpc` | 115 | **115** | — |
| `follow-api` | 65 | **68** | +3（socket 1 ＋ 断连 2） |
| `follow-rpc` | 78 | **78** | — |
| **合计** | **652** | **659** | **+7** |

⇒ **守门 5/5 EXIT=0**：`check-wave-lane-ownership` · `check-wave-role-model` · `check-wave-coupling`
· `check-ledger-count` · `check-doc-links`。
⇒ ⚠️ **本轮回归中的一次偶发（留痕，不掩盖）**：第一次 6 组件回归里
`FollowApiStartupFailFastTest.startsWithGlobalClientInterceptor` **红过一次**
（cause = devh `connectOnStartup` 的「Can't connect to channel follow-rpc」⇒ **本地 channel 3s 内没到 READY**），
⇒ **该测试与本轮改动无耦合面**（它测的是 gRPC 拨号，本轮改的是 Redis）。
⇒ **归因取证（2026-10-27 · 用 surefire 报告的 mtime 还原并发时间线）**：
| 时段 | 谁在跑 | 证据 |
|---|---|---|
| 05:25~05:45 | **四棵工作树全有 surefire 报告产出** ⇒ **4 个 maven 并发** | `g1` 16 份 · `g2` 81 份 · `g3` 29 份 · `g4` **256** 份 |
| 05:30~05:46（**逐分钟都有**） | **G4 的全量回归全程在跑** | ⇒ **与我第一次回归的窗口 05:32:16~05:38:02 完全重叠** |
| 05:40:17 / 05:41:09 / 05:42:02 | 我单独重跑 follow-api **3 次 ⇒ 全绿**（**此时 G4 仍在跑**） | ⇒ **并发是「可疑诱因」但不是充分条件** |
| 05:42~05:50:52 | 我第二次 6 组件回归 ⇒ **EXIT=0 · 659 全绿** | ⇒ 复现不出来 |
⇒ **结论（诚实口径）**：**诱因高度可疑 = 并发 lane 回归的资源争抢**（与 G4 同窗口那次 `EXIT=137` 的 OOM-kill **同源**）
⇒ **未做受控复现**（要复现得故意再开 4 个并发回归，代价过高）⇒ 记为「**偶发 · 诱因高度可疑 · 未受控复现**」，
**不写成「已修」**；⇒ **但也不是本轮改动引入**（改动面 = Redis，红的是 gRPC 拨号；变异 5/5 变红证明守门有效；第二次全量 EXIT=0；同窗口 3 次单独重跑全绿）。
⇒ **⇒ 落到一条通用纪律**（AGENTS.md §4.1「大回归错峰」不是形式主义）：**多 lane 并发时看到「本地连接/拨号超时」类偶发，先查别的 lane 的 maven 进程列表，别当代码问题**。

### 13.5 效力边界（如实登记，**不夸大**）

1. **不是 L4**：Go 腿是**源码逐行对照**，不是本域活体对拍 ⇒ 记 **L3 + L1**；
   （Go 腿的活体证据在 **user-api 域**有：2026-10-24 的 `200 / 1.72s` 对拍）
2. **只覆盖「断连（FIN）」**：RST 形态（容器被强杀）**测不出**、也不构成漂移（那条路径两侧都快速失败）
3. **只覆盖黑名单读路径**：admin-api 的 Redis **只有黑名单这一处用途**（`AdminJwtAuthInterceptor:201`）
   ⇒ 本条覆盖了 admin-api 的**全部** Redis 故障面
4. **域内副本是过渡形态**：G4 收编后要回来删副本（见 §13.3）
