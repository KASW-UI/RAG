# 签字偏离台账 · `service/users/admin`（本域共享）

> **本文件是 admin 域「明知与 Go 不同、经用户签字批准」的权威清单。**
> 建立于 2026-10-23 —— 此前这些条目**只存在于** `docs/DRIFT/users/admin/_shared.md`，
> 而 `docs/Intentional_Deviations/users/**` 下 6 个文件**全是 0 字节空占位**
> ⇒ 下一个 agent 按 `docs/Intentional_Deviations/_INDEX.md` 检索会**查不到**
> ⇒ 2026-10-23 的 admin-api 审计轮因此误判「DRIFT-ADMINAPI-001 / -004 未签字」。
>
> **读法**：审查本域差异时，先查本文件；命中即**不再重复质疑，也不得「顺手修正回旧行为」**。
> 未命中 ⇒ 该差异仍按 §5.5「默认判 RAG 错」处理。
> 分组件的逐条细节 ⇒ [`rpc.md`](./rpc.md) / [`api.md`](./api.md)。

## 跨域通用签字（HTTP path / method / 表名）

三条由 `docs/Intentional_Deviations/_cross/signed-deviations.md` §3.4 统管，
**适用于本域全部组件**，此处只留指针与 admin 域的具体取值：

| 偏离 | Go 真源取值 | RAG 取值 | 理由 |
|---|---|---|---|
| HTTP path 前缀 | `/admincenter/v1/admin/*` | `/admin/v1/*` | HUMAN_ONLY.MD 决策；路由不属行为语义，但属 wire 契约变更 |
| HTTP method | GET / POST 混合 | 统一 POST | 同上 |
| 表名 | `admins` / `admin_invites`（`data_model.go:16-18` / `:31-33`） | `admin_admin` / `admin_invite` | 同族约定（`users→users_user`）。⚠️ **两侧物理上不共用 schema**，因此对拍只能落在 HTTP wire 层 |

## 本域逐条签字

| # | 组件 | 偏离 | 签字时间 | 细节 |
|---|---|---|---|---|
| **N-1** | `admin-api` | 鉴权失败的响应体形状统一为 `{code,msg,data}`，且**业务码为 `1006`** | 2026-10-21 | 见下 |
| **N-4** | `admin-api` | 5 个端点的 `operator_id` **不恒为 0**，传真实 uid | 2026-10-21 | 见下 |
| **N-5** | `admin-api` | JWT HMAC 密钥 zero-pad 到 32 字节 | 2026-10-21 | 见下 |
| **SD-2026-10-23-01** | `admin-api` | 身份解析**严格校验** JWT claim（Go 恒 0 且不校验） | 2026-10-23 | 见 [`_cross/signed-deviations.md`](../../_cross/signed-deviations.md#sd-2026-10-23-01--admin-api-operatoridgo-恒-0-且不校验-claimrag-严格校验--状态核对日期-2026-10-29✅-仍成立) ｜ 📌 **索引行**（总表目录行 · 正本见本行「落点」列 ⇒ **不独立计数**） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效（核对日期 2026-10-29）** ⇒ **现行态 = ✅ 仍成立（维持）**（**被签字描述的行为仍在**）。依据：**RAG** `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/service/IdentityResolver.java:102-103`（`if (uid <= 0) throw new ErrInvalidClaimException(MSG_INVALID_CLAIM);` ⇒ **严格校验 claim**）+ `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/service/IdentityResolver.java:135-136`（`if (admin.getUid() != uid) throw new ErrIdentityMismatchException(MSG_IDENTITY_MISMATCH);`）· **Go** `service/user/admin/rpc/internal/logic/banuserlogic.go:38-39`（`span.SetAttributes(attribute.Int64("audit.operator_id", in.OperatorId), …)` ⇒ `OperatorId` 直接取自请求、**全程无 claim 校验**）⇒ **两侧差异仍在 ⇒ 签字有效**。⚠️ **指针订正**：原文写的 `:100-103`（**含上一段 `}` 的行，行号偏大**）与 `:133-136`（**`:133` 实为 `admin == null` 那一支**）⇒ **现行精确锚点 = `:102-103` / `:135-136`**（原文不删）。核对日期 2026-10-30。|
| **SD-2026-10-23-06** | `admin-rpc` | `resetUserPassword` 保留空 `default-password` 守卫 | 2026-10-23 | [`rpc.md`](./rpc.md) ｜ 📌 **索引行**（总表目录行 · 正本见本行「落点」列 ⇒ **不独立计数**） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效（核对日期 2026-10-29）** ⇒ **现行态 = ✅ 仍成立（维持）**（**指针与原文逐字相符，无漂移**）。依据：**RAG** `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:1150-1151`（`String defaultPassword = properties.getSystem().getDefaultPassword();` + `if (defaultPassword == null || defaultPassword.isEmpty()) {` ⇒ **空值守卫在位**）+ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:1171`（`throw Status.INTERNAL.withDescription("密码加密失败").asRuntimeException();` ⇒ 守卫命中后的出口）· **Go** `service/user/admin/rpc/internal/logic/resetuserpasswordlogic.go:55-56`（`var password string` + `password, err = cryptx.PasswordEncrypt(l.svcCtx.Config.System.DefaultPassword)` ⇒ **无任何空值判断** ⇒ 空串照进 bcrypt）⇒ **两侧差异仍在 ⇒ 签字有效**。核对日期 2026-10-30。|
| **SD-2026-10-23-07** | `admin-rpc` | gRPC `status.description` 保留正常中文，不复刻 Go 乱码 | 2026-10-23 | [`rpc.md`](./rpc.md) ｜ 📌 **索引行**（总表目录行 · 正本见本行「落点」列 ⇒ **不独立计数**） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效（核对日期 2026-10-29）** ⇒ **现行态 = ✅ 仍成立（维持）**，但 ⚠️ **原文的「4 文件 12 处」计数已过期 ⇒ 本轮订正为「4 文件 21 处」**。依据：**RAG** `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:212`（`throw Status.NOT_FOUND.withDescription("用户不存在")` ⇒ **正常中文**）+ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:296` + `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:332` + `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:347`（`throw Status.ALREADY_EXISTS.withDescription("该用户名已被使用")`）⇒ **未复刻乱码**。**Go（2026-10-30 逐文件 `status.Error` 全量计数）**：`service/user/admin/rpc/internal/logic/getselflogic.go` = **2 处**（`:37` `鐢ㄦ埛涓嶅瓨鍦?` · `:40` `DB鏌ヨ鍞辫触`）· `service/user/admin/rpc/internal/logic/updateselflogic.go` = **8 处**（`:37`/`:40`/`:47`/`:51`/`:63`/`:77`/`:83`/`:86`）· `service/user/admin/rpc/internal/logic/createinvitecodelogic.go` = **5 处**（`:55`/`:60`/`:70`/`:94`/`:101`）· `service/user/admin/rpc/internal/logic/createadminlogic.go` = **6 处**（`:50`/`:59`/`:67`/`:73`/`:99`/`:119`）⇒ **合计 21 处**（原文写的 1/3/3/5 = 12 处 **已不成立** ⇒ 计数订正，**签字结论不变**）。核对日期 2026-10-30。|
| **SD-2026-10-23-08** | `admin-api` | 黑名单 401 的**业务码**为 `1006`（Go 是 `401`） | 2026-10-23 | [`api.md`](./api.md) ｜ 📌 **索引行**（总表目录行 · 正本见本行「落点」列 ⇒ **不独立计数**） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效（核对日期 2026-10-29）** ⇒ **现行态 = ✅ 仍成立（维持）**，但 ⚠️ **Go 侧原指针是裸文件名（无目录）⇒ 审计工具解析不到 ⇒ 本轮补全全路径**。依据：**RAG** `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/interceptor/AdminJwtAuthInterceptor.java:175-177`（黑名单命中 ⇒ `throw new AuthenticationException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), …)` ⇒ **业务码 1006**）+ `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/advice/GlobalExceptionHandler.java:59-68`（`@ExceptionHandler(AuthenticationException.class)` ⇒ `response.setStatus(401)` + `new Response(e.getCode(), e.getMessage(), null)` ⇒ **HTTP 401 + 业务码 1006**）· **Go** `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:38-39`（`w.WriteHeader(http.StatusUnauthorized)` + `w.Write` 写出 `{"code":401,"msg":"登录已失效,请重新登陆"}`） ⇒ **业务码 401**）⇒ **两侧业务码差异仍在 ⇒ 签字有效**。⚠️ 指针订正：原写 `checkblacklistmiddleware.go:38`（无目录、且 `:38` 只是 WriteHeader 行）⇒ 现行 = `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:38-39`。核对日期 2026-10-30。|

### 🆕 2026-10-29 重核（台账对齐轮 · 逐条核「被签字描述的那个行为现在还存在吗」）

> **判据**（`docs/Intentional_Deviations/_INDEX.md:34`）：「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效** ⇒ 不计入计数」。
> **本轮方法**：每条**回 Go 真源 + 回 RAG 源码两侧**各看一遍 ⇒ 只看本档原文不算核过。

| # | 组件 | 被签字描述的行为 | 2026-10-29 结论 | 证据（两侧） |
|---|---|---|---|---|
| **N-1** | `admin-api` | 鉴权失败统一成 401 + 完整 envelope（业务码 `1006`） | ✅ **仍存在 ⇒ 签字有效** | RAG：`service/users/admin/admin-api/.../advice/GlobalExceptionHandler.java:59-70`（`@ExceptionHandler(AuthenticationException.class)` ⇒ `ResponseEntity.status(401).body(new Response(e.getCode(), e.getMessage(), null))`）· Go：`go-zero@v1.10.2/rest/handler/authhandler.go:107-122`（`unauthorized()` 只 `WriteHeader(401)`、**空 body**）+ `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:38`（`{"code":401,…}`，**无 `data`**）⇒ 两侧 body 形状确实不同，RAG 统一为 envelope |
| **N-4** | `admin-api` | 5 个端点的 `operator_id` **不恒为 0**，传真实 uid | ✅ **仍存在 ⇒ 签字有效** | RAG：`AdminBanUserService.java:37`+`:45`、`AdminCreateInviteCodeService.java:44`+`:52`、`AdminDeleteUserService.java:37`+`:45`（均 `IdentityResolver.claimedUID(httpRequest)` ⇒ `setOperatorId(operatorId)`）· Go：`service/user/admin/rpc/internal/logic/banuserlogic.go:36-39` 等读 `ctx["uid"]`，而 go-zero 写 ctx 的 key 是 **claim 名 `userId`**（`service/user/common/jwt/jwt.go:8-11` 只签 `userId`）⇒ 恒 0 |
| **N-5** | `admin-api` | JWT HMAC 密钥 zero-pad 到 32 字节 | ✅ **仍存在 ⇒ 签字有效** | RAG：`service/users/admin/admin-api/.../auth/AdminJwtSignatureVerifier.java:64` = `Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(properties.getAdminAuth().getAccessSecret()))` · Go：真源 `service/user/common/jwt/jwt.go` 用 15 字节原始 secret 参与 HS256（jjwt 0.12 强制 key ≥256 bit ⇒ RAG 必须 pad）⇒ 「Go 签的 token 在 RAG 验不过」这一代价仍在 |
| **SD-2026-10-23-01** | `admin-api` | 身份解析**严格校验** JWT claim（Go 恒 0 且不校验） | ✅ **仍存在 ⇒ 签字有效** | RAG：`service/users/admin/admin-api/.../service/IdentityResolver.java:100-103`（`uid <= 0` ⇒ `ErrInvalidClaimException`）+ `:133-136`（`admin.getUid() != uid` ⇒ `ErrIdentityMismatchException`）· Go：`service/user/admin/rpc/internal/logic/banuserlogic.go:36-39` 取不到即恒 0、**不校验** ⇒ 差异仍在 ｜⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效** ⇒ **现行态 = ✅ 仍成立（指针订正：`IdentityResolver.java:102-103` + `:135-136`）**。逐条依据见本文件「逐条结论表」 |
| **SD-2026-10-23-06** | `admin-rpc` | `resetUserPassword` 保留空 `default-password` 守卫 | ✅ **仍存在 ⇒ 签字有效** | RAG：`service/users/admin/admin-rpc/.../service/AdminDomainService.java:1150-1151`（`defaultPassword == null \|\| isEmpty()` ⇒ 抛 INTERNAL「密码加密失败」，`:1171`）· Go：`service/user/admin/rpc/internal/logic/resetuserpasswordlogic.go:55-56` 无空值判断 ⇒ 空串交给 bcrypt ⇒ 成功 ｜⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效** ⇒ **现行态 = ✅ 仍成立**。逐条依据见本文件「逐条结论表」 |
| **SD-2026-10-23-07** | `admin-rpc` | gRPC `status.description` 保留正常中文，不复刻 Go 乱码字节 | ✅ **仍存在 ⇒ 签字有效** | Go（真源 4 文件 **12 处**，2026-10-29 逐字节复核）：`service/user/admin/rpc/internal/logic/getselflogic.go`（1 处 `鐢ㄦ埛涓嶅瓨鍦?`）· `updateselflogic.go`（3 处）· `createinvitecodelogic.go`（3 处）· `createadminlogic.go`（5 处）· RAG：`service/users/admin/admin-rpc/.../service/AdminDomainService.java:212`/`:296`/`:332`/`:347` 仍是正常中文（「用户不存在」「该用户名已被使用」）⇒ **乱码仍在 Go 侧、中文仍在 RAG 侧** ｜⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效** ⇒ **现行态 = ✅ 仍成立（口径订正：Go 乱码 **12 处 ⇒ 21 处**）**。逐条依据见本文件「逐条结论表」 |
| **SD-2026-10-23-08** | `admin-api` | 黑名单 401 的**业务码**为 `1006`（Go 是 `401`） | ✅ **仍存在 ⇒ 签字有效** | RAG：同 N-1 的 `GlobalExceptionHandler.java:59-70`（`e.getCode()` 取 `ErrorCode.ERROR_TOKEN_RUNTIME` = 1006）· Go：`checkblacklistmiddleware.go:38` 写 `{"code":401,"msg":"登录已失效,请重新登陆"}` ⇒ 业务码差异仍在 ｜⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 仍存在 ⇒ 签字有效** ⇒ **现行态 = ✅ 仍成立（指针订正：裸文件名 ⇒ 补全路径）**。逐条依据见本文件「逐条结论表」 |

> ⚠️ **本轮未发现任何一条「签字描述的行为已不存在」** ⇒ **本域 7 条签字全部有效**，**不需从计数中剔除任何一条**。

#### 撞号检查（同一 `SD-` 编号在本域被引用多处）

| 编号 | 本域内出现处 | 事实 | 本轮处置 |
|---|---|---|---|
| `SD-2026-10-23-01` | 本文件「本域逐条签字」表 + [`api.md`](./api.md) 独立小节 | **同一处偏离的两个落点**（本文件只留指针，`api.md` 留全文）⇒ **不是「指向多条不同偏离」** | 给**全文落点**（`api.md`）加唯一别名 **`SD-2026-10-23-01@users`**；本表内的指针注明「同一条，别当两条」<br>⚠️ **2026-10-30 真实性复核（users lane）**：本行是**落点说明行**（非独立偏离）⇒ 复核结论随**正本行**（同表 `SD-2026-10-23-01` 签字行）⇒ **维持 ✅ 仍成立**。可复算指针（补本行 PTRLESS 缺口）：`service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/service/IdentityResolver.java:102-103` + `service/user/admin/rpc/internal/logic/banuserlogic.go:38-39`。核对日期 2026-10-30。|
| `SD-2026-10-23-06` | 本文件表 + [`rpc.md`](./rpc.md) | 同上 | `rpc.md` 侧加 **`SD-2026-10-23-06@users`**<br>⚠️ **2026-10-30 真实性复核（users lane）**：本行是**落点说明行**（非独立偏离）⇒ 复核结论随**正本行**（同表 `SD-2026-10-23-06` 签字行）⇒ **维持 ✅ 仍成立**。可复算指针（补本行 PTRLESS 缺口）：`service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:1150-1151` + `service/user/admin/rpc/internal/logic/resetuserpasswordlogic.go:55-56`。核对日期 2026-10-30。|
| `SD-2026-10-23-07` | 本文件表 + [`rpc.md`](./rpc.md) | 同上 | `rpc.md` 侧加 **`SD-2026-10-23-07@users`**<br>⚠️ **2026-10-30 真实性复核（users lane）**：本行是**落点说明行**（非独立偏离）⇒ 复核结论随**正本行**（同表 `SD-2026-10-23-07` 签字行）⇒ **维持 ✅ 仍成立**。可复算指针（补本行 PTRLESS 缺口）：`service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:347` + `service/user/admin/rpc/internal/logic/createadminlogic.go:50`。核对日期 2026-10-30。|
| `SD-2026-10-23-08` | 本文件表 + [`api.md`](./api.md) | 同上 | `api.md` 侧加 **`SD-2026-10-23-08@users`** |

> ⚠️ **另注**：`N-1` / `N-4` / `N-5` **不是 `SD-` 编号**，但它们在 `docs/DRIFT/users/admin/_shared.md` 与本档**两处**都有登记
> ⇒ 同样是「同一处偏离的两个落点」，**不是两条不同偏离**；本轮以**本档为签字档权威面**（`docs/DRIFT/**` 记差异、`docs/Intentional_Deviations/**` 记决策，§7.2）。

### N-1 · 鉴权失败的响应体形状（2026-10-21 用户拍板） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

Go 侧两处 401 的 body **互不相同**：
- `rest.WithJwt` → `authhandler.go:106 unauthorized()` 写 **HTTP 401 + 空 body**
- `CheckBlacklistMiddleware` → `checkblacklistmiddleware.go:38` 写
  **HTTP 401 + `{"code":401,"msg":"登录已失效,请重新登陆"}`**（无 `data`）

RAG 两侧统一成 **HTTP 401 + 完整 `{code,msg,data}`**，**业务码仍是 `1006`**。

> 🔴 **这条同时签了 body 形状与业务码**。原文写明「业务码仍是 `ERROR_TOKEN_RUNTIME(1006)`」——
> ⇒ 黑名单路径的「Go `401` / RAG `1006`」**早在 N-1 就在签字范围内**，
> 不是新发现。2026-10-23 的 api 审计轮曾判定「N-1 从未声明业务码变更」，
> **该判定是错的**（去 `docs/Intentional_Deviations/users/**` 查不到 N-1，
> 因为那些文件当时全是 0 字节 —— 这正是本文件的建立原因）。
> 另见 **SD-2026-10-23-08**，把黑名单这一条单独复述并补上活体对拍证据。

**关闭条件**：若要求逐字复刻 Go 的两种 body，需在拦截器里区分
「JWT 验签失败」与「黑名单命中」两条路径分别写 body。

### N-4 · `operator_id` 不恒为 0（2026-10-21 用户拍板） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

Go 的 `updateuser` / `banuser` / `unbanuser` / `delete` / `resetuserpassword` 读 `ctx["uid"]`，
而 `rest.WithJwt` 写入 ctx 的 key 是 JWT claim 名 **`userId`** ⇒ 永远取不到
⇒ `OperatorId` 恒为 **0**。RAG 五个端点都传真实 uid。

**理由**：该字段**不参与任何业务判定**（2026-10-23 复核：admin-rpc 侧只有 `createInviteCode`
真的拿 `operator_id` 去查 admin；其余 5 个只当审计字段），只写进 OTel span 的
`audit.operator_id` 属性；恒 0 会让审计日志失去操作人信息。

**⚠️ 边界**：Go「恒 0 + 不校验 claim」的**校验缺失**那半边，
已由 **SD-2026-10-23-01** 单独签字（RAG 选择严格校验）。N-4 只管**取值**，不覆盖校验。

**关闭条件**：若要求审计 span 与 Go 逐字一致，需在这 5 个 Service 里刻意丢弃 token 中的 uid。

### N-5 · JWT HMAC 密钥长度（2026-10-21 用户拍板） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

Go 用 15 字节原始 secret 参与 HS256；RAG 用 `JwtUtil.padToHmacKeySize` zero-pad 到 32 字节。
jjwt 0.12 强制 key ≥256 bit。

**⚠️ 代价**：RAG 内部签发与验签共用同一份 padded key、自洽；
但 **Go 签的 token 在 RAG 验不过，反之亦然** —— 两套部署各自闭环。
与 `user-api` 同款。

**关闭条件**：若需跨部署互认 token，必须统一 key 长度 —— 要么改 Go 侧 secret 到 ≥32 字节，
要么 RAG 放弃 padding（jjwt 会抛 `WeakKeyException`）。

## 本域**已撤销**的签字（留档，防后人重犯 N-3/N-6 的错）

| # | 原偏离 | 撤销原因 |
|---|---|---|
| **N-3** | 「`uid=0` 被归类为业务不存在」 | **证伪**：RAG 代码里**没有** uid=0 拦截。实测 RAG 与 Go 三态逐条相同（不传→400 / `uid=abc`→400 / `uid=0`→`200 {code:1003}`）⇒ 不是偏离 ｜ **状态（核对日期 2026-10-29）：⛔ 已失效·已撤销** |
| **N-6** | 「uid 格式非法时 body 形状 ≠ envelope」 | **证伪**：Jackson 在反序列化阶段就抛错，压根到不了 `parseUid`。两侧都是 `400 + text/plain` ⇒ 不是偏离 ｜ **状态（核对日期 2026-10-29）：⛔ 已失效·已撤销** |

> **这两条都经过签字、都有防回退测试、javadoc 出处写得很完整，而它们描述的行为从未存在过。**
> ⇒ **元教训**（已写进 `docs/Pitfalls/users/`）：任何留档声称「RAG 的行为是 X」时，
> **都必须有一条断言 X 的运行测试**；该测试不存在或从未跑过 ⇒ 它只是推测，不得当作事实引用。
> 区分「代码里写了什么」与「运行时真的发生什么」—— 前者 grep 得到，后者只有真进程能证明。

> ⚠️ **2026-10-29 真实性复核（补规范状态字段时**当场回代码**复验 · 原值保留）**：N-3 / N-6 两条的**撤销前提当场成立**
> ⇒ **维持撤销**、**不计入签字计数** ⇒ **现行态 = ⛔ 已失效·已撤销**。
> - **N-3**（`uid=0` 被归类为业务不存在）：RAG 侧 `service/users/admin/admin-api/src/main/java/**` 全目录实测**无 uid=0 拦截**
>   —— `AdminApiSupport.java:83-96` 的 `parseUid` 只在 `uidStr` 为空 / 非数字时抛 1017，而 `Long.parseLong("0")` **正常返回 0**
>   ⇒ `uid=0` 直接进 rpc ⇒ 落 `1003 用户不存在` ⇒ **RAG 与 Go 同为 `uid=0 → 200 {code:1003}`**
>   ⇒ 被签字描述的行为**两侧相同** ⇒ **不是偏离**。
> - **N-6**（uid 格式非法时 body 形状 ≠ envelope）：RAG 侧 uid 是 **`Long`** 字段（`GetUserReq.java:26-27`）
>   ⇒ `uid=abc` 在 **Jackson 反序列化阶段**就抛 `InvalidFormatException` ⇒ 落到 `GlobalExceptionHandler.java:121-125`
>   的 `400 + text/plain` ⇒ **到不了**业务层的 `parseUid` ⇒ 两侧 body 形状**无分叉** ⇒ **不是偏离**。
> - ⚠️ **登记一条「代码注释已过期」（本轮**不改代码**，只登记）**：`GetUserReq.java:20-21` 与 `GlobalExceptionHandler.java:138-140`
>   的 javadoc 仍写「uid=0 → 1017 请求参数错误」⇒ 与上面核出的**现行行为**（uid=0 → `1003 用户不存在`）不符
>   ⇒ **下次 grep 到这两处注释的人，不要据此复活 N-3**。

### 🆕 2026-10-29 重核 · 已撤销的两条（N-3 / N-6）

> **判据**：撤销的那个行为**现在**是不是真的没了 ⇒ 若它**又出现了**，撤销必须**回滚**（否则等于「悄悄复活」）。

| # | 撤销时声称的行为 | 2026-10-29 复核 | 结论 |
|---|---|---|---|
| **N-3** | 「`uid=0` 被归类为业务不存在」 | RAG 侧 `service/users/admin/admin-api/src/main/java/**` 全目录 `grep -rnE 'uid == 0\|uid==0\|getUid\(\) == 0'` = **0 命中** ⇒ **没有 uid=0 拦截** | ✅ **撤销仍然成立**（该行为依旧不存在）⇒ 维持撤销，**不计入签字计数** |
| **N-6** | 「uid 格式非法时 body 形状 ≠ envelope」 | RAG 侧 uid 的解析在 Jackson 反序列化阶段就失败 ⇒ 非法 uid **到不了**业务层的 `parseUid` ⇒ 两侧都是 `400 + text/plain`（Go：`rest/httpx/requests.go` 的 `ParseJsonBody`；RAG：`service/common/.../http/GoStyleFormValues.java`）⇒ body 形状**没有**分叉 | ✅ **撤销仍然成立** ⇒ 维持撤销，**不计入签字计数** |