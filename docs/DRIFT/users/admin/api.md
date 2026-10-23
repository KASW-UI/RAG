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
| **Go 侧** | `Sea-RideTheWind_golang/service/user/admin/api`（36 个文件：1 main + 1 config + 13 handler + 13 logic + 1 middleware + 1 metrics + 1 svc + 1 types + 1 yaml） |
| **RAG 侧** | `RAG/service/users/admin/admin-api`（43 个 main + 10 个 test 文件） |
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
| AGENTS.md §4.2 Phase 4：「admin-api：13 Service + **2 client**（admin-rpc + user-rpc）」 | ❌ **只有 1 个 client** | Go 侧 `service/user/admin/api` 全树 grep `user/rpc` → **零命中**；RAG 侧 grep `@GrpcClient` → 只有 `AdminRpcClient.java:25` 的 `@GrpcClient("admin-rpc")` |
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
| chain 索引 0 最外层、先执行 | `rest/chain/chain.go` 的 `Then` = `m0(m1(...(mN(h))))` |
| **黑名单不在 chain 里**，它是 `ThenFunc` 的**参数** ⇒ 比 chain 任何元素都内层 | go-zero `rest/server.go:222-233` `WithMiddleware`：`Handler: middleware(route.Handler)` —— 在 `routes.go` 构造期就完成，早于 `bindRoute` |
| ⇒ **真实顺序 = Authorize(JWT) → observability → CheckBlacklist → handler** | 三段合成 |

### 2.2 鉴权两层

**层 1 — `rest.WithJwt`（`rest/handler/authhandler.go:45-84`）**

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
`rest/token/tokenparser.go:122-124` `newParser()` = `jwt.NewParser(jwt.WithJSONNumber())`）。
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

### 2.4 请求解析层（`httpx.Parse` → `core/mapping`）

```
ParsePath → ParseForm → ParseHeaders → ParseJsonBody → validator
ParseJsonBody: ContentLength > 0 && ContentType 含 "application/json"  ⇒  UnmarshalJsonReader
               否则                                                 ⇒  UnmarshalJsonMap(nil, v)
```

**必填判据**（`core/mapping/unmarshaler.go:918-969` `processNamedFieldWithoutValue` 的**消费点**）：

```
1. opts.getDefault() 命中（tag 含 ",default="）→ 填默认值、return，不报错
2. Array / Map / Slice  → processFieldNotFromString，**不报错**
3. 其他标量 且 !opts.optional() → newInitError ⇒ field "xxx" is not set
```

⚠️ `parseOption`（`core/mapping/utils.go`）**只认 `optional` / `optional,type=...`**，
**`,omitempty` 被静默忽略** ⇒ `json:"email,omitempty"` 仍是**必填**。

⚠️ **form 通道额外丢空值**（`rest/httpx/util.go:37-43` `GetFormValues`：`if len(v) == 0 { continue }`）
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
[HttpMiddlewareFilter]   ← @Order(HIGHEST_PRECEDENCE+5) servlet Filter，包住整个 DispatcherServlet
        ↓
[AdminJwtAuthInterceptor.preHandle]  ← WebMvcConfig:60-63 注册到 /admin/v1/**
        ↓
[AdminController 方法]（GoStyleRequest.normalize）
        ↓
[13 个 Service] → [AdminRpcClient.adminRpc()] / [StringRedisTemplate.hasKey]
        ↓
[异常时：GlobalExceptionHandler] → [正常时：GlobalResponseAdvice 包 envelope]
```

⚠️ **与 Go 的顺序差异是结构性的**：Go 的 observability 中间件在 **JWT 内层**，
RAG 的 Filter 在 **JWT 外层**。见 DRIFT-ADMINAPI-006。

### 3.2 鉴权（`AdminJwtAuthInterceptor`）

| 阶段 | 行为 | 与 Go |
|---|---|---|
| public 路径 | `PUBLIC_PATHS.contains(request.getRequestURI())` ⇒ 直接 return（`:98-100`） | ✅ 一致（集合逐条相同） |
| 阶段 1 验签 | 缺 `Authorization` ⇒ 401；`tokenForSignatureVerification` 剥前缀（大小写不敏感）；jjwt 验签；`userId` claim **必须是 Number**，否则 401（`:133-183`） | ⚠️ 比 Go **多一道** claim 存在性/类型检查（DRIFT-ADMINAPI-003） |
| 阶段 2 黑名单 | `trimBearerPrefix`（大小写敏感）；`hasKey(prefix+token)`；异常吞成 `null` ⇒ **fail-open**（`:196-231`） | ✅ 一致（含「两段取法不同」这个 Go 自身的不一致） |
| attribute 写入 | 成功时写 `userId`（Long）与 `jwt_token`（String） | ✅ 一致 |

⚠️ **Go 从不写 `uid` attribute，RAG 也没有** —— 但两端的**读取方**不同，见 §4 矩阵。

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
| M-07 | 鉴权 | 签名有效但 `userId` claim 缺失/非数字 | **200 + `{code:1006}`** | **401 + `{code:1006}`** | **OBSERVABLE DIFFERENCE** | → **DRIFT-ADMINAPI-003** |
| M-08 | 鉴权 | public 路径集合 | `{create, login}` | `{create, login}` | EQUIVALENT | `routes.go:16-30` vs `AdminJwtAuthInterceptor:70-72` |
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
| M-36 | ALL | 超时时长 | **3000ms**（yaml 未写，来自 `rest/config.go:55` 的 `json:",default=3000"`） | `observability.http-timeout: 3s` | EQUIVALENT | **消费点**：`TimeoutFromMillis(conf.Timeout)`；`application.yml:131-132` |
| M-37 | ALL | 超时的**包含范围** | 计时器在 Authorize **之后**起算 | 计时器在 **最外层 Filter**，含验签与 Redis | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-006 |
| M-38 | ALL | 超时响应 | `503` + `text/plain; charset=utf-8` + `nosniff` + 尾换行 | `503` + `text/plain; charset=utf-8` + 尾换行，**无 nosniff** | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-007（**代码在 `service/common`，非本组件文件**） |
| M-39 | ALL | 中间件外层顺序 / 遥测面 | 签名无效的请求**不产生** observability span | 会产生 | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-006 |
| M-40 | 全部外部调用 | Retry | **无** | **无** | EQUIVALENT | 两侧 `application.yml` / `admincenter.yaml` 均无重试配置 |
| M-41 | 启动 | 下游依赖不可达 | `zrpc.MustNewClient` **启动即失败**（进程不启动） | Nacos + devh 懒连接，**进程能起**、每请求失败 | OBSERVABLE DIFFERENCE | → DRIFT-ADMINAPI-009 |
| M-42 | 全部 | 并发语义 | 无状态，goroutine-per-request | 无状态，**但**多一个 worker-pool 饱和 → `setStatus(503)` 无 body 分支 | UNKNOWN | Go 侧无对应分支；见 UNRESOLVED-004 |
| M-43 | 全部 | Prometheus 指标 | `admin_api_intercept_total{path,result}`，17 个具名 label | Micrometer 同名 Counter + 同 2 个 tag | IMPLEMENTATION DIFFERENCE | Micrometer 的 `_total` 命名归一化未实测 → UNRESOLVED-005 |
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
| **Timeout**（§17） | 时长等价、**范围不等价** | M-36 / M-37 / DRIFT-ADMINAPI-006 |
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
     因为 `rest/token/tokenparser.go:123` `newParser()` = `jwt.NewParser(jwt.WithJSONNumber())`）。
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

---

### DRIFT-ADMINAPI-003 · 签名有效但 `userId` claim 缺失/非数字：Go 200+1006，RAG 401+1006

- **Component**：admin-api / OP-05、OP-06、OP-12 的鉴权失败分支
- **Category**：Error Identity / HTTP Status
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：MEDIUM（业务码相同，状态码不同）
- **Go Behavior**：`Authorize`（`authhandler.go:65-69`）只要求 `tok.Claims.(jwt.MapClaims)` 转换成功，
  **不检查任何 claim 的存在性或取值** ⇒ 一个「签名有效但无 `userId`」的 token 能穿过鉴权，
  然后由 Logic 自己发现：`getselflogic.go:36-40` / `updatelogic.go:36-40` / `createinvitelogic.go:35-40`
  返回 `nil, errmsg.ErrorTokenRuntime` ⇒ `httpx.OkJson` ⇒ **HTTP 200 + `{code:1006}`**。
- **RAG Behavior**：`AdminJwtAuthInterceptor:165-175` 在**鉴权层**就判 `userId` claim：
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

---

### DRIFT-ADMINAPI-006 · 中间件外层顺序不同 ⇒ 401 请求的遥测面与 3s 预算包含范围都不同

- **Component**：admin-api / 全部端点
- **Category**：Lifecycle / Timeout Scope / Observability
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：LOW-MEDIUM
- **Go Behavior**：`admincenter.go:36` `server.Use(observability.NewHTTPMiddleware(...))`
  ⇒ 进入 `ng.middlewares`，在 `engine.go:108-112` 里**排在 `appendAuthHandler` 之后** append
  ⇒ 位置在 **JWT 内层**、黑名单外层。
- **RAG Behavior**：`HttpMiddlewareFilter:37` `@Order(Ordered.HIGHEST_PRECEDENCE + 5)` 是 **servlet Filter**，
  包住整个 `DispatcherServlet` ⇒ 在 `AdminJwtAuthInterceptor`（`WebMvcConfig:60-63`）**外层**。
- **Difference**：
  1. **遥测面**：一个签名无效的请求，Go 在 `Authorize` 就短路 ⇒ **完全不产生 observability span**；
     RAG 会先跑完 Filter（建 span、记 401）才被拦截器拒。
  2. **超时范围**：Go 的 3s 计时器在 Authorize **之后**起算（JWT 验签耗时**不计入**）；
     RAG 的 3s 在最外层起算 ⇒ **验签 + Redis EXISTS 都计入**预算。
- **Evidence strength**：`静态源码·双侧`（`engine.go:108-112` 与 `@Order` 行号均已核对）
- **Note**：不影响任何业务判定，只影响遥测与极端超时边界。**不列为待办**，仅留档。

---

### DRIFT-ADMINAPI-007 · 503 超时响应缺 `X-Content-Type-Options: nosniff`

- **Component**：admin-api / 超时与客户端断连分支（**实现落在 `service/common`**）
- **Category**：Serialization / Response Headers
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：LOW
- **Go Behavior**：`observability/http.go:88` `http.Error(w, httpTimeoutBody, statusCode)`
  ⇒ Go 的 `net/http.Error` 做**三件事**：写状态码、设 `Content-Type: text/plain; charset=utf-8`、
  设 `X-Content-Type-Options: nosniff`，再写 `body + "\n"`。
- **RAG Behavior**：`HttpMiddleware.writeTimeoutResponse:419-425` 只做**两件**：
  `setStatus` + `setContentType` + 写 `body + "\n"`，**没有 nosniff**。
- **Difference**：503/499 响应少一个 `X-Content-Type-Options` 头。
- **⚠️ 归属**：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`
  **不在本组件的修改范围内**（本会话只允许改两个台账文件）⇒ **只留档，不实施**。
  影响面是**所有 api 组件**，不是 admin-api 独有。
- **Evidence strength**：`静态源码·双侧`

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
  RAG 侧 `@GrpcClient` **只有 1 个**（`AdminRpcClient.java:25`）。
  `application.yml:70-75` 留着一段**死的** `grpc.client.user-rpc`，`pom.xml:81-84` 注释也写着两个 client。
- **Why It Matters**：这不是语义漂移，是**「台账里的计数本身需要 grep 复核」**的实例
  （与 §2.2 的 `HUMAN_ONLY.MD` 清单、`§3.3.3` 的「文件已写 ≠ 存在」同款）。
  本次任务简报即以该计数为前提布置了「2 个 client 都在被并行审查」的依赖前提。
- **Recommended Verification**：`grep -rn '@GrpcClient' service/users/admin/admin-api/src/main`
  ⇒ 只有 1 条（`AdminRpcClient.java:25`）。**已在本轮实跑。**
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
| CASE-10 | 签名有效但无 `userId` claim | `200 + 1006` | `401 + 1006` | ❌ | **缺**（→ DRIFT-ADMINAPI-003） |
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

- `types.go:72` 的 `json:"user,omitempty"` 是 `UserInfo`（struct）⇒ Go 的 `encoding/json` 不会省略它。
- 本轮**没有跑探针实证**（Go 侧需要起进程或写小程序）。这是 DRIFT-ADMINAPI-005 的判定支点之一。
- **Required Confirmation**：一个 10 行的 Go 程序即可判定；或直接把 admin-rpc 的实际返回形态确认掉
  （那样 DRIFT-ADMINAPI-005 可能整条作废）。

### UNRESOLVED-004 · RAG 侧 worker-pool 饱和 → 无 body 的 503

- `HttpMiddleware` 把业务跑在独立 worker 池上；池满时走 `httpResp.setStatus(503)` 且**不写 body**
  （与 Go 侧任何分支都不同源 —— Go 用 goroutine，无池）。
- **没有测试覆盖**，**也没有并发压测数据**说明该分支在真实负载下的可达性。
- **Required Confirmation**：并发压测 + 观察池饱和阈值。本轮未跑。

### UNRESOLVED-005 · 两项启动期事实未实测

| 项 | 未知点 |
|---|---|
| DRIFT-ADMINAPI-009 | Nacos 不可达时 admin-api 的**真实**启动序列与就绪探针表现（静态只说明「不会像 Go 那样 panic」） |
| M-43 | Micrometer Counter 名 `admin_api_intercept_total` 在 `/actuator/prometheus` 上**最终叫什么**（Micrometer 有 `_total` 归一化规则，未实测）⇒ 与 Go 的 `admin_api_intercept_total` 是否同名也未实测 |

### UNRESOLVED-006 · 本组件的绝大多数结论是 `静态源码·双侧`

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
| 12 | Ordering 是否一致 | ⚠️ 部分 | **鉴权内部顺序一致**（M-09，三段取证）；**中间件外层顺序不一致**（M-39 / DRIFT-ADMINAPI-006） |
| 13 | Retry 是否一致 | ✅ 是 | 两侧皆无（M-40） |
| 14 | Timeout scope 是否一致 | ❌ 否 | 时长一致（M-36）；**包含范围不同**（M-37 / DRIFT-ADMINAPI-006） |
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
| **HTTP 状态码语义变更** | ⚠️ **修正为：主体已修，但两处遗留未闭合** | 业务族恒 200 ✅（`GlobalExceptionHandler:231-233` `decideStatus` 无条件 `OK`，对应 Go 的 `httpx.OkJson`）；鉴权族 401 ✅（`handleAuthentication:66-68`，对应 Go `authhandler.go:121`）。**遗留**：黑名单 401 的业务码 401→1006（DRIFT-ADMINAPI-004）；claim 无效时 200→401（DRIFT-ADMINAPI-003） |
| **`@ResponseBodyAdvice` 二次包裹** | ✅ **已修，当前源码成立**（成功 + 异常 + 400 三类路径均覆盖） | 两道守卫：`GlobalResponseAdvice:39-41`（`body == null \|\| body instanceof Response`）与 `:52-55`（非 JSON 原样透传）。`handleBusiness` / `handleRPC` / `handleAuthentication` / 三个 IdentityResolver 分支全部返回 `ResponseEntity<Response>` ⇒ G1 短路；`goStylePlainTextBadRequest` 返回 `ResponseEntity<String>` + text/plain ⇒ G2 短路。⚠️ **残余缺口**：`body == null` 这一支（405/415/actuator）无任何断言；且 `AdminResponseEnvelopeAdviceTest` 用的是 `standaloneSetup`，**不加载 `HttpMiddlewareFilter`** ⇒ 与 DRIFT-FRAMEWORK-001 相关的容器时序不在其覆盖内。**按 AGENTS.md 硬约束 2，绿测试本身不作为「已闭环」证据** |
| **`SelectAvatar` 响应 `is_current` 陈旧** | 🚫 **不适用**；但**同型的 `found` 已修且当前成立** | admin 域**无 avatar 端点**：`service/user/admin/api` 33 个源文件逐条核对，无任何 avatar 字样；`admin.proto` 的 `AdminInfo` 无头像字段。同型问题在 `getuser`：Go 的 `true` 写在 **api 层**（`getuserlogic.go:59`），RAG 显式 `setFound(true)`（`AdminGetUserService:67`），并有 `AdminApiMvcPipelineTest` + `AdminSemanticDriftApiRegressionTest` 两处断言（**非本轮**） |
| **`JwtUtil` 密钥补齐被删** | ✅ **已修且当前成立**（但其后果 N-5 已签字） | `JwtUtil.padToHmacKeySize:44-53` 存在，且被**签发端与验签端共用**：签发 `JwtUtil.getToken:16`、验签 `AdminJwtAuthInterceptor:90` ⇒ 不存在「一处补一处没补」的撕裂。⚠️ 后果：Go 用 15 字节原串、RAG 用 32 字节 zero-pad ⇒ **两侧签发的 token 互不认**（N-5，已签字，`admin/_shared.md`） |

### 10.4 效力边界（本节的自我限制）

1. 本轮**没有**产出任何「已闭环」结论；最强者是 `静态源码·双侧`。
2. 引用到的 difftest / matrix / `AdminApiMvcPipelineTest` 成果均为 **2026-10-21/22 他人已跑**，本轮**未复跑**。
3. `admin/_shared.md` 的 **DRIFT-009 行**（「黑名单先执行」）与当前源码**冲突**：
   本轮已复核 go-zero `rest/engine.go:108-112` + `rest/server.go:222-233`，**真实顺序是验签先、黑名单后**，
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
| **A-1** | 🔴 **`user-api` 有**同款 charset 缺失**：`user-api/.../advice/GlobalExceptionHandler.java:216` 也是 `headers.setContentType(MediaType.TEXT_PLAIN)` ⇒ 400 的 `Content-Type` 同样没有 charset | 与本文件 §1 同型（同一段 javadoc 都写着「Go 的 `net/http.Error` 设 `text/plain; charset=utf-8`」，但代码没做） | 归 users/user owner。⚠️ `tools/difftest/check-plaintext400-headers.sh` **只打 user-api** ⇒ 修 user-api 时顺手把该脚本的断言加严（断言 charset 存在）才是根治 |
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
