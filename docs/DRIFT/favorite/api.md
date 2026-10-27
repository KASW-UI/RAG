<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# DRIFT 台账 · `service/favorite/favorite-api`

> **核对时间：2026-10-25**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/api`）
> **本轮性质**：**首轮组件级 Go ↔ RAG 逐条对照**（此前该组件台账为 **0 字节** —— 从未对照）。
> **证据强度**：✅ **真实 MVC 管线 + 真实 gRPC 拦截器装配 + 源码逐行对照**；
> 🔴 **无双侧活体对拍**（Go 侧 favorite-api 依赖 `etcd` + favorite-rpc + Redis，本轮起不来）。
> ⇒ 本台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
>
> ✅ **2026-10-25 第八轮已对本表的首轮结论做机器化复核**（只读，零代码改动）：
> ① **7 条路由契约 + 前缀 `/favorite/v1`** ⇒ 与 Go `routes.go` **逐条一致**；
> ② **响应字节层** ⇒ 已升级为**双侧活体对拍**（§2.2，探针 `tools/api-envelope-probe/favorite/`）。
> ⇒ 逐条见 [`rpc.md`](./rpc.md) §14.3。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| 落地形态 | 31 个 main 文件 / **3,116 行** / **15 个 DTO** / 7 个 Service / 1 个 Controller<br>⚠️ **核对 2026-10-25（第十轮）**：行数比首轮台账记的 2,920 多 196 行 ⇒ **全部是各轮的纯注释增量**（可执行逻辑零改动）；DTO 数首轮记的 **13 是错的**（基线 `58e8314` 就是 15 个） |
| 测试 | **8 个测试类 / 97 例全绿**（33 输入契约 + 36 MVC 管线 + **6 装配** + 4 超时 + **8 真 Tomcat 门禁** + **5 响应字节对拍** + **1 探针（自动 skip）** + **4 端口归属守门**）→ 第六轮 **+1 个测试类 / +5 例**；第十四轮收尾 **+1 个测试类 / +4 例**（`RealTomcatPortTest`）；**2026-10-26 合并当天**：防护链类 3 ⇒ **6 例**（绝对 `@Order` ⇒ Go 相对链序，**§12**） |
| 本轮发现 | **2 条 L1/L2 语义漂移（均已修）** + **2 条 javadoc 假断言（已修）** + **1 条已签字偏离** |
| 未决 | **2 条已签字**（401 body 形态 DEV-FAV-API-001 + 500 兜底 body DEV-FAV-API-002，✅ **2026-10-25 用户签字**）⇒ **本域已无 🏷 候选**<br>🔴 **1 条跨域待拍板**：② **405 响应多一个 `Content-Type`**（2026-10-25 第十四轮新发现，落点在 `service/common` ⇒ 收口在 `global`）—— ① 容器层参数上限口径**已于 2026-10-27 闭合**（共享层把容器上限抬到 10001 + 域侧动作完成 ⇒ **§14**）<br>✅ **U8（第十五轮登记的唯一一条业务路径缺口）已于第十六轮闭合**：create 成功路径 + `ensureUserExists` 五分支 + 落库等价性全部拿到双侧活体证据（15 case 全 SAME）⇒ [`rpc.md`](./rpc.md) **§25**；盘点全表 ⇒ **§24.1** ⇒ **域内「未知项」归零**（只剩 B2 一条**流程项**：V7 上线前的域外写入方人工确认）<br>🔴 **U9（第十七轮新增 · 部署形态类 · 待拍板）**：Go 生产形态有 **16062** 那个 HTTP 面，RAG 侧没有 ⇒ **§11** |
| 本台账范围 | **仅 `favorite-api`**。`favorite-common` / `favorite-rpc` / `favorite-proto` 见同目录另三份台账 |

---

## 1. L1 契约对照结果

### 1.1 路由：7 个端点，method + path + 入参通道逐条一致

Go 出处 `api/internal/handler/routes.go:16-59`（`rest.WithMiddlewares([CheckBlacklistMiddleware], …)` + `rest.WithJwt(…)` + `rest.WithPrefix("/favorite/v1")`）。

| Method | Path | 入参通道 | Go handler | RAG | 结论 |
|---|---|---|---|---|---|
| POST | `/favorite/v1/folder/create` | JSON body | `create_favorite_folder_handler.go:18` | `FavoriteController:129` | ✅ |
| DELETE | `/favorite/v1/folder/delete` | JSON body | `delete_favorite_folder_handler.go:18` | `FavoriteController:136` | ✅ |
| GET | `/favorite/v1/folder/list` | 无（**仍校验 body**） | `get_favorite_folders_handler.go:18` | `FavoriteController:143` | ✅ |
| POST | `/favorite/v1/folder/update` | JSON body | `update_favorite_folder_name_handler.go:18` | `FavoriteController:156` | ✅ |
| POST | `/favorite/v1/item/create` | JSON body | `create_favorite_handler.go:18` | `FavoriteController:167` | ✅ |
| DELETE | `/favorite/v1/item/delete` | JSON body | `delete_favorite_handler.go:18` | `FavoriteController:174` | ✅ |
| GET | `/favorite/v1/item/list` | **query string（form 通道）** | `get_favorites_by_folder_handler.go:18` | `FavoriteController:190` | ✅ |

> ⚠️ **只有 `GET /item/list` 走 form 通道** —— Go 的 `types.GetFavoritesByFolderReq` 是
> `form:"folderId,string"`，其余 6 个端点的字段全是 `json` tag。
> go-zero 的 `core/mapping` **按 tag 名决定通道**（`unmarshaler.go:569-573` 的
> `usingDifferentKeys`）⇒ 带别的通道 tag 的字段在本网段**整体跳过**。
> ⇒ RAG 侧 `GetFavoritesByFolderReq.fromForm` + `FavoriteApiSupport.goFormFirstValue` 逐条复刻 ✅

### 1.2 envelope：字段集合 + **HTTP 恒 200**

Go：`service/common/response/response.go:3-7` = `{code, msg, data}`（**三个字段都无 `omitempty`**），
7 个 handler 全部 `httpx.OkJsonCtx` ⇒ **HTTP 恒 200**，业务成败只由 body 的 `code` 表达。
RAG：`com.rag.common.response.Response` + `GlobalResponseAdvice` + `GlobalExceptionHandler` ✅

| 场景 | Go | RAG | 结论 |
|---|---|---|---|
| 成功 | `{code:200,msg:"OK",data:{…}}` | `GlobalResponseAdvice:87-88` 包 `Response(200,"OK",body)` | ✅ |
| 解析失败 | `{code:5003,msg:"参数错误",data:null}` + **HTTP 200** | `GlobalExceptionHandler#invalidParam:260-264` | ✅ |
| 业务失败 | `{code:<码>,msg:GetErrMsg(code),data:null}` + **HTTP 200** | `handleBusiness:84` | ✅ |
| Content-Type | `application/json; charset=utf-8`（**Go 无条件写**） | `GlobalResponseAdvice:62-63,81` 钉死 | ✅ |

> ⚠️ **本域与 users/admin 域相反**：那边解析失败走 `httpx.ErrorCtx` ⇒ **400 + text/plain**；
> favorite 走 `httpx.OkJsonCtx` ⇒ **200 + envelope**。RAG 侧刻意未复用 admin 的写法 ✅

### 1.3 错误码：7 个 logic 的失败分支逐条一致

Go 侧 7 个 logic **完全同构**（`extractUserID` → 调 rpc → `codeFromRPCError` → nil 兜底 → 成功）：

| 判定 | Go | RAG | 结论 |
|---|---|---|---|
| `extractUserID` 失败 | `ErrorUnauthorized`(5004) | `FavoriteApiSupport.userId:85-117` | ✅ |
| rpc 出错 | `codeFromRPCError(rpcErr)` = `favoritecommon.BizCodeFromError` | `invokeRpc:212` = `FavoriteGrpcError.bizCodeFromError` | ✅（common 侧已双侧对拍） |
| **create/get ×4**：响应为 `nil` | `rpcResp == nil` ⇒ **5001** | `support.nullResponse` | ✅（本轮修，见 §2-D2） |
| **delete/update ×3**：响应为 `nil` **或** `success=false` | `rpcResp == nil \|\| !rpcResp.Success` ⇒ **5001** | `support.nullOrUnsuccessfulResponse` | ✅（本轮修，见 §2-D2） |

### 1.4 鉴权：401 形态 + 黑名单 fail-open

| 项 | Go | RAG | 结论 |
|---|---|---|---|
| Authorization 缺失 / 空 token | 401 | `auth/FavoriteJwtSignatureVerifier.java:100-110`（**2026-10-27 起验签搬进验签器**） | ✅ |
| 验签失败（签名错/过期/格式错，**Go 不区分原因**） | 401 | `auth/FavoriteJwtSignatureVerifier.java:120-123`（catch `Throwable`） | ✅ |
| claim `userId` 原样存入 ctx | `authhandler.go:71-79` | `interceptor/FavoriteJwtAuthInterceptor.java:175` `request.setAttribute(CLAIM_ATTRIBUTE, result.claims().get(CLAIM_ATTRIBUTE))` | ✅ |
| 黑名单 key | `user:jwt_blacklist:<token>` | `interceptor/FavoriteJwtAuthInterceptor.java:114` | ✅ |
| 黑名单 token 取法 | `TrimSpace(TrimPrefix(authHeader,"Bearer "))`（**大小写敏感** + TrimSpace） | `auth/FavoriteJwtSignatureVerifier.java:128` `goTrimSpace(BearerTokenExtractor.trimBearerPrefix(authHeader))` | ✅（且验签用的大小写不敏感取法，两段不一致也原样保留） |
| **Redis 故障** | `err == nil && exists` ⇒ **fail-open 放行** | `interceptor/FavoriteJwtAuthInterceptor.java:216-231` 吞异常 ⇒ 放行 | ✅ |
| **执行顺序** | 先验签（JWT）→ 后黑名单 | `preHandle` 阶段 1（`:150-176`）→ 阶段 2（`:192-196`） | ✅ |
| **404 / 405 不经鉴权** | chain 只挂在匹配上的路由 | `interceptor/FavoriteJwtAuthInterceptor.java:141` `handler instanceof HandlerMethod` | ✅ |
| **3s 预算作用域（验签在预算之外）** | 计时器在 Authorize **之后**起算 ⇒ 验签**不计入**预算（`rest/engine.go:108` vs `:110-112`） | `auth/GoStyleJwtAuthFilter.java:95` `@Order(+6)` ⇒ 落在 `+7 HttpMiddlewareFilter`（3s 预算起算）**之外** | 🔴 → ✅ **本轮已修**（2026-10-27 · 见 §2-D5） |

> ⚠️ **`extractUserID` 的类型分支**：Go 的 `case float64: return int64(v), nil`（**截断**）
> 在 JWT 路径上**不可达**（go-zero 用 `jwt.NewParser(jwt.WithJSONNumber())` ⇒ 数字 claim 恒为 `json.Number`）。
> RAG 侧把 `Double`/`Float` 判为**失败**（⇒ 5004）⇒ 与 Go 的 `json.Number("1.5").Int64()` 失败一致 ✅

### 1.5 出参序列化：两种 int64 编码方式不同

Go `types.go:42-47`：`folderId` 带 `,string` ⇒ **JSON 字符串**；`createdAt`/`updatedAt` **不带** ⇒ **JSON 数字**。
RAG `FavoriteFolder`：`getFolderId()` 返回 `String`、`getCreatedAt()` 返回 `long` ⇒ 区分正确 ✅
（`FavoriteItem` 同理；`targetId` 恒为 string 因 Go 侧就是 `string`）

> ⚠️ **空集合是 `[]` 不是 `null`**：Go 的 `make([]types.FavoriteFolder, 0, len(...))`（`get_favorite_folders_logic.go:58`）
> 与 `make([]types.FavoriteItem, 0, …)`（`get_favorites_by_folder_logic.go:64`）**无条件 make**
> ⇒ RAG 侧 `new ArrayList<>(size)` + DTO **不加** `@JsonInclude(NON_NULL)` ✅

### 1.6 容器三旋钮 + 请求保护层

| 项 | Go | RAG | 结论 |
|---|---|---|---|
| `relaxed-query-chars` | Go 的 `net/http` 不校验 query 里的 `[` `]` | `application.yml:65` | ✅ **运行证据**（真 Tomcat 门禁） |
| `max-parameter-count`（**容器层**） | Go 的 `net/http` **不限参数个数**；Go 的真实上限是 `net/url` 的 `defaultMaxParams = 10000`（Go 1.25.11 `src/net/url/url.go:1001`） | **yaml** `:66` = **2048**（**一字未动**）<br>**运行期 = 10001**（共享层 `GoStyleTomcatParameterLimitCustomizer` **无条件**抬升，G4 落码 `cd893d1`） | ✅ **运行期已对齐**（容器不再比 Go 更早拒 ⇒ **判定权交还应用层**）⇒ ⚠️ **别只看 yaml**（AGENTS.md §3.3.1：yaml ≠ 运行期值）⇒ 残余见 §3 与 §14 |
| `maxFormParamCount`（**应用层**） | `httpx/util.go:15` = **2048**，数**非空值**、计数器 `n` **跨所有 key 共享** | `FavoriteApiSupport#goFormFirstValue`（Go 逐字复刻） | ✅ 源码级一致；🔴 **2026-10-27 订正**：容器上限抬到 10001 后，这条判据**成为唯一判定者** ⇒ 「在真 Tomcat 栈上不可达」**已不成立** ⇒ 见 §2-D4 与 §14 |
| `max-http-request-header-size` | `DefaultMaxHeaderBytes(1<<20) + 4096` = **1028KB** | `:67` | ✅ **运行证据**（真 Tomcat 门禁） |
| HTTP 超时 | `rest/config.go:55` `Timeout default=3000` | `observability.http-timeout: 3s` | ✅ |
| **MaxConns / MaxBytes / CPU 降载 / 熔断（服务端）** | `rest/config.go:52-56` + `zrpc/internal/config.go:14-15` | `GoStyleProtectionProperties` 默认值 = Go 默认值 | ✅ **运行证据**（`FavoriteApiProtectionWiringTest` + 真 Tomcat 门禁） |
| **client 熔断** | `zrpc/internal/client.go:112-113` | `GoStyleGrpcClientBreakerInterceptor` | ✅ **运行证据**（本轮修，见 §2-D3） |

---

## 2. 🔴 本轮修复

### D1 · 🔴 **L2 语义漂移**：缺 zrpc client 的 2s 超时（真漂移，可观察）

| | 内容 |
|---|---|
| **Go 行为** | `zrpc/config.go:29` `RpcClientConf.Timeout int64 json:",default=2000"`；`zrpc/internal/client.go:115-116` 在 `c.middlewares.Timeout`（`zrpc/internal/config.go:15` `default=true`）时装 `clientinterceptors.TimeoutInterceptor(timeout)`。`etc/favorite.yaml:34-37` 的 `FavoriteRpc` 段**没有**覆盖 `Timeout` ⇒ 生效 **2000ms** |
| **RAG 修复前** | `favoriteRpc()` 只套 `GoStyleRequestDeadline.applyTo(...)`（HTTP ctx 预算 3s）⇒ **没有 client 层 2s** |
| **可观察差异** | 下游挂死时：Go 是 **2s 先到** ⇒ rpc error ⇒ api 返回 **HTTP 200 + 业务码 1015**；RAG 修复前一路顶到 HTTP 超时 ⇒ **503 "Request Timeout"**（且状态码与 body 不自洽） |
| **修复** | `FavoriteRpcClient.capByClientTimeout` —— 把 deadline 压到 `min(ctx 预算, 2s)`；**无 ctx 预算时也要套 2s**（Go 的客户端超时是**拦截器级别**的，与有没有 HTTP 超时中间件无关） |
| **回归网** | `FavoriteRpcClientTimeoutTest`（4 例）：用**真实 in-process channel** 断言压完后的 deadline ≤ 2s / 保留更短的 ctx 预算 / 无 ctx 预算时也套 —— 验**行为**不验数字 |

> ⚠️ 顺带对齐了 follow / task / hot / article / users 域的既有做法（同一份 `capByClientTimeout` 形态）。

### D2 · 🔴 **javadoc 假断言**（AGENTS.md §3.3.2）：Go 的两条分支被说成一条

| | 内容 |
|---|---|
| **原断言** | `FavoriteApiSupport#nullOrUnsuccessfulResponse` 的注释写「⚠️ **注意 Go 把「nil」与「success=false」合并成同一条 5001 分支**，本实现用同一条出口保持一致」 |
| **实测** | **与 Go 源码矛盾**。Go 是**两条独立分支**：<br>· create/get ×4：`if rpcResp == nil { … "favorite rpc returned nil response" … }`<br>· delete/update ×3：`if rpcResp == nil \|\| !rpcResp.Success { … "favorite rpc returned unsuccessful response" … }`<br>⇒ 判定不同、**措辞也不同** |
| **危害** | ① 一条**错误**的「Go: …」断言会让后续 review 把合并实现当成「已对齐」；② 服务端业务日志少掉 Go 侧存在的一条措辞 |
| **修复** | 拆成 `nullResponse`（4 个调用点）与 `nullOrUnsuccessfulResponse`（3 个调用点），**措辞逐条对齐 Go** |

> ⚠️ **这类错误正是 §3.3.2 点名的形态**：注释写得越完整、越像「已核对」，越容易把 review 带偏。

### D3 · 🔴 **javadoc 假断言**：client 熔断「尚未接入」

| | 内容 |
|---|---|
| **原断言** | `FavoriteRpcClient` 的类注释写「⚠️ **client 熔断：Go 侧有且默认开，Java 侧尚未接入（已登记的已知偏离）**」，并给出一段「为什么现在不补」的论证 |
| **实测** | **两处皆假**：① **熔断早已生效** —— `GoStyleGrpcClientBreakerInterceptor` 带 `@GrpcGlobalClientInterceptor`，被 `FavoriteApiApplication` 的 `scanBasePackages`（含 `com.rag.common`）扫到即自动注册；② 「已登记」是假 —— `docs/Intentional_Deviations/favorite/` 三个文件**至今 0 字节** |
| **修复** | 改正断言 + 补「装配证据」一节（指向运行证据） |
| **回归网** | `FavoriteApiProtectionWiringTest`（**6 例**）：读 starter 的 `GlobalClientInterceptorRegistry` 断言拦截器**真的在里面**（只断言「Bean 存在」不够 —— 证明不了 channel factory 会不会消费它，§3.3）；断言保护层默认值 == Go 默认值；断言 4 个 filter 的**相对** `@Order` 与 Go 链序一致（🔴 **2026-10-26 合并当天**：原先断言的是**绝对**值 `+1/+2/+3/+6` ⇒ 被 `global` 第十九轮的合法后移打断 ⇒ 改为 Go 真源承载语义的相对链序 ⇒ **§12**） |

> ⚠️ **「已接入」不等于「已验证」**：本轮正是用**运行证据**（而不是读注解）把它从「假断言」变成「已验证」。

### D4 · 🔴 **L1 语义漂移**：容器的「参数过多」异常绕过异常处理器 ⇒ HTTP 500 + 5001（Go 是 200 + 5003）

| | 内容 |
|---|---|
| **Go 行为** | `httpx.Parse` → `httpx.GetFormValues`（`rest/httpx/util.go:37-53`）在非空值超过 `maxFormParamCount` 时返回 `fmt.Errorf("too many form values, …")` ⇒ `httpx.Parse` 失败 ⇒ handler 走 **`httpx.OkJsonCtx`**（`get_favorites_by_folder_handler.go:18-26`）⇒ **HTTP 200 + `{code:5003, msg:"参数错误", data:null}`**（码值 `errmsg.go:16`、文案 `errmsg.go:51`） |
| **RAG 修复前** | Tomcat 的 `server.tomcat.max-parameter-count=2048` **先**命中 ⇒ 抛 `org.apache.tomcat.util.http.InvalidParameterException` ⇒ ⚠️ **它不是 `IllegalArgumentException` 的子类** ⇒ **绕过** `GlobalExceptionHandler#handleIllegalArgument` ⇒ 落到 `handleAny` 兜底 ⇒ **HTTP 500 + `{code:5001, msg:"系统内部错误"}`** |
| **可观察差异** | **HTTP 状态码不同**（Go 200 / RAG **500**）+ **业务码不同**（5003 / 5001）⇒ **L1 级**。且 Go 的 api 层**根本不存在 500 出口**（7 个 handler 全部 `OkJsonCtx`）⇒ RAG 这个 500 是**本域独有**的形态 |
| **修复** | `FavoriteApiSupport#parameterMap` —— **只包住 `request.getParameterMap()` 这一行**，把容器层的 `RuntimeException` 转成 `IllegalArgumentException` ⇒ 复用既有 `handleIllegalArgument` ⇒ 对外统一 **HTTP 200 + 5003**。⚠️ 刻意 catch `RuntimeException` 而非 Tomcat 的类：避免把编译期依赖绑到 `org.apache.tomcat.util.http`（非公开 API 面） |
| **回归网** | `FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError`（真 Tomcat `RANDOM_PORT` + 真容器上限触发） |
| **变异证据** | 去掉 `parameterMap` 的包装 ⇒ **2 例转红**（实测 HTTP 500 + `{code:5001}`）⇒ 该测试**不是假绿** |

> ⚠️ **附带发现（写在这里，因为它改写了对 §1.6 的读法）**：
> 变异时**「2049 个非空值」那条也一起红了** ⇒ 说明在真 Tomcat 栈里，
> **容器层永远先于应用层拒** ⇒ `FavoriteApiSupport#goFormFirstValue` 里那道
> Go 逐字复刻的 2048 判据**在真栈上不可达**（与 hot 域 `check-tomcat-limits.py`
> 脚本头部写的「RAG 自己实现的 2048 判据在 GET query 上不可达」同源）。
> ⇒ 它只有在容器层上限被放宽时才生效 ⇒ 见 §3 的跨域条目。

### D5 · 🔴 **L1 语义漂移**：JWT 验签被计入 3s 预算（Go 的计时器在 Authorize **之后**起算 ⇒ 验签在预算**之外**）

| | 内容 |
|---|---|
| **Go 行为** | `service/favorite/api/favorite.go:28-30`：`httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)` ＋ `server.Use(observability.NewHTTPMiddleware(...))` ⇒ **3s 预算只由 observability 提供**（native Timeout 已关）。<br>go-zero v1.10.2 `rest/engine.go:101-116` `bindRoute`：`chn = ng.appendAuthHandler(fr, chn, verifier)`（**JWT 验签**，`:108`）→ **再** `for _, mw := range ng.middlewares { chn = chn.Append(...) }`（`server.Use` 的 observability，`:110-112`）⇒ `chain.Append` = **更靠内** ⇒ ⇒ **执行序：原生中间件 → JWT 验签 → observability(3s 计时) → 黑名单 → handler** ⇒ ⇒ **3s 计时器在 Authorize 之后起算 ⇒ 验签耗时不计入预算**。<br>⚠️ `server.Use` 虽是**全局**中间件，但 go-zero 把它 append 在 Authorize **内侧** ⇒ **「全局」≠「最外层」**（**读源码才敢下这个结论**，不能凭注册方式推链序）。 |
| **RAG 修复前** | 验签内联在 `FavoriteJwtAuthInterceptor.preHandle`（`HandlerInterceptor` ⇒ **DispatcherServlet 内**）⇒ 排在 `HttpMiddlewareFilter`（`@Order(+7)`，`service/common/.../spring/HttpMiddlewareFilter.java:37`，**3s 预算在此起算**）**之后** ⇒ ⇒ **验签被计入 3s 预算 ⇒ 与 Go 相反** |
| **可观察差异** | 验签耗时（HMAC 验签 + claim 解析）被算进 3s 预算 ⇒ **Go 放行而 RAG 可能 503** 的窗口只存在于「验签耗时占掉预算」的那部分 ⇒ 属 **L1 级**（链序差异，非阈值差异） |
| **修复** | 验签搬进**域内** `favorite-api/.../auth/GoStyleJwtAuthFilter`（`@Order(+6)` Servlet Filter ⇒ 落在 `+3 Traceparent` **之内**、`+7 HttpMiddlewareFilter` **之外**）⇒ 401 仍由 `FavoriteJwtAuthInterceptor` 抛（**失败原样重抛异常对象** ⇒ `@ExceptionHandler` 仍管得到 ⇒ **401 三字段信封逐字不变**）；**黑名单留在 interceptor**（Go 侧是 route 级中间件 ⇒ 在预算**之内** ⇒ 现状本就一致 ⇒ **没动**） |
| **回归网** | `FavoriteApiJwtVerificationScopeTest`（**7 例** · 真 Spring 上下文 ＋ **容器装配层**断言 `ServletContextInitializerBeans` 的真实顺序 ＋ 行为层断言「interceptor 消费的是 Filter 的结果」） |
| **变异证据** | **3/3 精确变红**（见 §2.3）⇒ 该测试**不是假绿** |

> ⚠️ **三处本域特有差异已逐条保留（不「顺手统一」）**：
> ① **401 body 仍是三字段信封**（已签字 `DEV-FAV-API-001` **不受影响**）；
> ② **黑名单两套取法**（验签用大小写不敏感 strip / 黑名单用大小写敏感 `TrimPrefix` ＋ Go 语义 `TrimSpace`）**原样保留**；
> ③ **未注册路径仍 404**（`handler instanceof HandlerMethod` 判据**原样保留** ⇒ 不被鉴权遮蔽）。

### 2.1 🧪 变异验证表（`FavoriteApiRealTomcatGateTest` 8 例 · 防假绿）

> 目的：证明这 8 条断言**不是假绿** —— 每一行都是「把实现/配置改坏 ⇒ 对应断言必须转红」（§3.3）。
> ⚠️ 全部变异都在本地临时施加、**立即还原**（已用 `git status` 复核**零残留**）。

| # | 变异 | 预期转红 | 实测 |
|---|---|---|---|
| **MUT-A** | 去掉 `parameterMap` 的异常归一（回到裸 `request.getParameterMap()`） | `containerParameterLimitIsSurfacedAsGoParseError` + `nonEmptyFormValueCountOverLimitIsRejected` | ✅ **2 例转红** —— 实测 HTTP **500** + `{code:5001}`（即修复前的形态）<br>🔴 **2026-10-27 合并后已失效**（实测 **0 例转红**）⇒ 容器不再抛「参数过多」⇒ 这层归一永不触发 ⇒ 见 §14.2 |
| **MUT-B** | `rag.protection.max-bytes` 1024 → 8192 | `oversizedBodyYieldsBare413` + `oversizedBodyBeatsAuth` | ✅ **2 例转红**（413 ⇒ 401） |
| **MUT-C** | `server.tomcat.max-parameter-count` 2048 → 1000 | `nonEmptyFormValueCountAtLimitIsPassedThrough` | ✅ **1 例转红**（2048 个值被容器提前拒 ⇒ 5003）<br>🔴 **2026-10-27 合并后已失效**（实测 **0 例转红**）⇒ yaml **不再决定运行期值**（共享层无条件抬到 10001）⇒ 见 §14.2 |
| **MUT-D** | `server.max-http-request-header-size` 1028KB → 8KB | `longRequestLineIsAccepted` + 3 条带长 query 的 | ✅ **4 例转红**（Tomcat 400 HTML 错误页） |
| **MUT-E** | `server.tomcat.relaxed-query-chars` → 错值 | `bracketNotationIsAcceptedLikeGo` | ✅ **1 例转红**（Tomcat 400 HTML 错误页）<br>🔴 **2026-10-27 复验**：**8 例 error**（错值在**上下文启动期**就被拒 ⇒ 整类起不来）⇒ ⇒ `bracketNotationIsAcceptedLikeGo` **仍在其中**（该断言仍转红）⇒ 但**波及面**从 1 例扩到 8 例 ⇒ ⇒ **按实测改记，不沿用旧值** |
| **MUT-F** | 域内判据从「数非空值」退化成「数总值」（删 `goFormFirstValue` 里的 `if (value == null \|\| value.isEmpty()) { continue; }`） | `containerParameterLimitIsSurfacedAsGoParseError` | ✅ **1 例转红**（body 变 `{code:5003}`）⇒ **2026-10-27 新增**：它**替掉**已失效的 MUT-A / MUT-C 对该断言的守护 ⇒ 见 §14.2 |

> ⚠️ **MUT-E 顺带撞出一条绑定细节**（值得单独记）：用**测试属性**把
> `server.tomcat.relaxed-query-chars` 覆盖成**空串**时，**断言不转红** ——
> ⇒ 空串在 Boot 的宽松绑定下**没有绑成空列表**，容器仍用 yml 里的 `["[", "]"]`
> ⇒ 换成**错值**才测得出来。
> ⇒ **「配了」与「被采用了」是两件事**（§3.3 的老教训，此处又撞一次）：
> 静态守门脚本（`check-tomcat-limits.py`）查的是**配置文本**，它**证明不了**容器采用了它；
> 只有真 Tomcat 的行为断言 + 变异转红才能。

> 🔴 **2026-10-27 合并后复验全表**（共享层把容器上限抬到 10001 ⇒ **容器层不再当判定权**
> ⇒ ⇒ **两条变异当场失效**）：实测 ⇒ **MUT-A 0 红**（容器不再抛「参数过多」⇒ `parameterMap`
> 的归一永不触发）· **MUT-C 0 红**（yaml 不再决定运行期值）· **MUT-B 2 红**（不变）·
> **MUT-D 4 红**（不变）· **MUT-E 8 error**（旧记录 1 红 ⇒ 已按实测改记）·
> **MUT-F 1 红**（新增，替掉 MUT-A/MUT-C 对该断言的守护）
> ⇒ ⇒ **没有任何一条断言失去专属变异** —— 失效的变异 = 假绿的开始（AGENTS.md §3.3）。

### 2.2 ✅ 本轮补上的证据缺口：响应字节的**双侧活体对拍**（新探针）

> 🔴 **这一节推翻了本台账此前的一条结论**，并把两条 🟡 源码级判定升级为**实测**。

**新工具**：`tools/api-envelope-probe/favorite/`（Go 侧用**真 go-zero v1.10.2 库** +
**从 Go 真源逐字节复制**的 `response.go` / `types.go` / `errmsg.go`；Java 侧用**真实**的
`GlobalResponseAdvice` / `com.rag.common.response.Response` / favorite-api 的 `dto/*` +
**Spring 容器里那个** `ObjectMapper` + **真 HTTP**）⇒ **EXIT=0**。
⇒ 冻结的 `expected.txt` 是**两侧共用的唯一真源** ⇒ 不会「各说一半」。

| # | 此前结论 | 本轮实测 | 状态 |
|---|---|---|---|
| **1** | 「Go 侧「非空值 >2048」时的逐字节响应」= **🟡 源码级**（只读过 `httpx.OkJsonCtx` 的形状） | Go 侧用**真的** `httpx.Parse`（`maxFormParamCount = 2048` 判据真生效）⇒ 2049 个非空值 ⇒ **200 + `{code:5003,msg:"参数错误",data:null}`**；RAG 侧走**真 HTTP**（真 Tomcat + 真 filter 链）⇒ **逐字节相同** | ✅ **活体对拍** |
| **2** | 「两种 int64 编码方式」（§1.5）= **读源码判定** | 实测：`folderId` ⇒ `"9007199254740993"`（**字符串**，2^53+1 不丢精度）、`createdAt` ⇒ `1700000000123`（**数字**） | ✅ **实测** |
| **3** | 「go-zero 的 `,optional` 不是 `omitempty`」= **读源码判定** | 实测：`title`/`cover` 为空时**仍然出现**（`""`），不是 null、也不是消失 | ✅ **实测** |
| **4** | 🔴 「Go 的 `RecoverHandler` 写 **500 + text/plain**（`http.Error`）」 | **原结论错误**：go-zero v1.10.2 的 `rest/handler/recoverhandler.go:17` 是 **`w.WriteHeader(500)`** ⇒ **空 body、无 Content-Type** | 🔴 **已改正**（见 §3） |

> ⚠️ **第 4 条的连带影响**：`RecoverHandler` 是 go-zero 的**默认中间件**
> （`rest/engine.go:158`，`conf.Middlewares.Recover` 为真时挂上）⇒
> **「Go 的 logic 不会 panic ⇒ 该分支不可达」是未验证的假设，不是事实** ⇒
> 该分支**确实可达** ⇒ 两侧形态不同就是**一条真差异** ⇒ 已登记（DEV-FAV-API-002）。

> 📌 **一处归一化（不是漂移）**：Go 写 `application/json; charset=utf-8`（分号后**有**空格），
> Spring 渲染成 `application/json;charset=utf-8`（**无**空格）⇒ HTTP 媒体类型参数分隔符
> 两侧的空白是**可选**（RFC 9110 §5.6.2 的 OWS）⇒ 任何解析器都会归一化 ⇒ **无客户端能分辨**
> ⇒ 属**表达层**差异 ⇒ 探针**只对 Content-Type 这一个字段**做 OWS 归一，**body 仍逐字节比较**。

> ⚠️ **「读源码判定」为什么不够**：本轮两条 🟡（第 2、3 条）此前都是**读源码判定的**，
> 结论**恰好正确** —— 但**正确不代表方法可靠**：`encoding/json` 会**忽略**它不认识的 tag 选项
> （`optional`），这一点只有**真的序列化一次**才看得见 ⇒ AGENTS.md §3.3「不能只靠声明推断」。

---

### 2.3 🧪 变异验证表（`FavoriteApiJwtVerificationScopeTest` 7 例 · 2026-10-27 · 防假绿）

> 目的：证明 §2-D5 的守门**不是假绿** —— 每一行都是「把实现改坏 ⇒ 对应断言必须转红」。
> ⚠️ 全部变异都在本地临时施加、**立即还原**（已用 `git status` ＋ `grep` 复核**零残留**）。

| # | 变异 | 预期转红 | 实测 |
|---|---|---|---|
| **MUT-F** | `GoStyleJwtAuthFilter` 的 `@Order(+6)` → `+8`（跑到 3s 预算之后） | 「容器装配出的链里，JWT Filter 排在 3s 预算 Filter **之前**」 | ✅ **1 例转红**（其余 6 例仍绿 ⇒ **精确命中**） |
| **MUT-G** | 让 Filter 在验签失败时**自己写 401 并短路**（违反「绝不写响应」不变式） | 「413 压过 401」＋「401 信封形状不变」＋「未注册路径仍 404」 | ✅ **3 例转红** —— 实测 `413 ⇒ 401`、**无 `$.code`**、`404 ⇒ 401`（⇒ **「Filter 绝不写响应」这条不变式被守门真的咬住**） |
| **MUT-H** | 摘掉 Filter 的 `@Component`（不进容器） | 「interceptor 消费的是 Filter 的结果（=filter）」 | ✅ **1 failure + 2 errors** —— 来源标记由 `filter` 变 **`inline`**（⇒ **兜底分支真会生效**，不是死代码）＋ bean 不存在 |

> ⚠️ **MUT-G 是本轮最关键的一条**：它证明「Filter 绝不写响应」不是注释里的一句口号 ——
> 一旦有人图省事让 Filter 直接写 401，**「413 压过 401」与「未注册路径仍 404」两条语义会当场变红**。

## 3. ⚠️ 未决 / 已签字偏离

### DEV-FAV-API-001（✅ **已于 2026-10-25 签字**）· 401 响应的 body 形态

| | Go | RAG |
|---|---|---|
| 验签失败 | `authhandler.go:106-122` ⇒ **401 + 空 body** | 401 + `{"code":5004,"msg":"未登录或登录已失效","data":null}` |
| 黑名单命中 | `checkblacklist_middleware.go:39-45` ⇒ 401 + **两字段**（无 `data`） | 同上（三字段） |

⇒ **HTTP 状态码 401 与 code 5004 都对齐**，差的是 body 的字段集合 ⇒ 表达层差异。
⇒ 登记在 [`../../Intentional_Deviations/favorite/api.md`](../../Intentional_Deviations/favorite/api.md)，
✅ **2026-10-25 用户签字**（「保留现状，登记为签字偏离」）。
⇒ ⚠️ 签字时一并明确：**follow 域选「Go 逐字」、本域选「三字段 envelope」**，两域做法不一致，
但**状态码与业务码完全一致** ⇒ **按现状签，不改任何一方**；若将来要统一口径，**需同时动两个域并重新征签**。

> ⚠️ 此前两个类（`GlobalExceptionHandler` / `FavoriteJwtAuthInterceptor`）都把这称为
> 「属 AGENTS.md **§3.4** 已登记的表达层差异」—— **§3.4 是 proto 复制那一节**，与本条无关 ⇒ 已改正。

### OBS · 500 兜底的 body 形态（🔴 **本轮已改正 Go 侧事实**，并登记为有意偏离）

Go 侧 **500 + 空 body**（真库 `rest/handler/recoverhandler.go:17` 是 `w.WriteHeader(500)`），
RAG 侧 **500 + JSON envelope**（`{code:5001,…}`）。

⚠️ **本台账此前写的是「Go 写 500 + text/plain（`http.Error`）」—— 那是错的**，
已由 `tools/api-envelope-probe/favorite/` 用**真 go-zero 库**实测改正（见 §2.2 第 4 条）。

⇒ 另：此前记的「favorite 的 Go logic **不会 panic** ⇒ 该分支实际不可达 ⇒ 记 OBS，**不构成行为漂移**」
也**不成立** —— `RecoverHandler` 是 go-zero 的**默认中间件**（`rest/engine.go:158`）⇒
一旦 handler panic 就会走到 ⇒ **该分支可达** ⇒ 两侧形态不同是**一条真差异**。

⇒ **已登记**：`docs/Intentional_Deviations/favorite/api.md` 的 **DEV-FAV-API-002**。
⇒ **不修**：RAG 侧给了可解析的 envelope（Go 侧是空 body），且与 users / admin / comment 域一致
⇒ 改成空 body 只会让客户端在 500 时拿不到任何信息。

### 🔁 跨域待拍板 · 容器层参数上限的**口径差**（残余窗口，收口在 `global`）—— 🔴 **2026-10-27 已闭合**（域侧动作已完成，见 §14）

| | 内容 |
|---|---|
| **🔴 订正（2026-10-27）** | 本格**已闭合**：共享层把容器上限**无条件**抬到 **10001**（G4 落码 `cd893d1`，类 `GoStyleTomcatParameterLimitCustomizer`）⇒ ⇒ 容器不再比 Go 更早拒 ⇒ ⇒ **「总「值」数 2049~10000 且非空值数 ≤2048」那一格：Go 放行 / RAG 也放行** ⇒ ⇒ 域侧动作（把域内守门从「必须落 5003」改成 Go 语义「放行 ⇒ 进业务层」）**已完成**（§14）<br>⇒ ⇒ **下面各行保留原文**（历史事实 + 当时的推理，不改写）⇒ 其中「残余窗口（精确）」那一行**已被本行订正**<br>⚠️ **仍在的下一格（不假装闭合）**：总「值」数**超过 Go 的 10000 段上限**时，Go 拒（400 + `net/url` 原文），而 favorite 域**没有**实现这道段数上限（域内判据只数**非空值**；共享层的段数闸门因 favorite = `OFF`（域自捕获）而**不装**）⇒ ⇒ 该区间两侧不一致 ⇒ 归 `DRIFT-HOTAPI-030` 的**后续格** ⇒ 本轮**不覆盖** |
| **事实（2026-10-25 真 Tomcat 实测，4 组判别用例）** | Tomcat 的 `maxParameterCount` 数的是**总参数「值」个数（含空值）**；Go 的 `maxFormParamCount` 数的是**非空值个数**（`httpx/util.go:44-45` 的 `if len(v) == 0 { continue }` 空值不计数），且计数器 `n` **跨所有 key 共享** |
| **判别用例（每组都带一个合法非空 `folderId` ⇒ 应用层永不拒、字段必在场 ⇒ 结果只可能来自 Tomcat）** | 总「值」=2048（名字 1，非空 1）⇒ **放行**；总「值」=2049（名字 1，非空 1）⇒ **拒**；总「值」=2049（名字 2，非空 1）⇒ **拒**；总「值」=3000（名字 3000，非空 1）⇒ **拒** ⇒ 判定「**数值不数名**」 |
| **残余窗口（精确）** | **总「值」数 2049~10000 且非空值数 ≤2048** ⇒ Go **放行**（200 + 业务结果）/ RAG **拒**（200 + 5003） |
| **归属** | 🔴 **跨域**，与 hot 域的 `DRIFT-HOTAPI-030` 是**同一格**（hot 域记的正是「总参数数 2049~10000 且非空值 ≤2048」）⇒ **不在 favorite 域单方面改**：改 `server.tomcat.max-parameter-count` 会让**全仓**守门脚本 `tools/difftest/check-tomcat-limits.py` 变红 ⇒ 收口在 `global`<br>⚠️ **第七轮已查清「为什么会变红」的机制**（不是「脚本恰好这么写」）：该脚本的 `EXPECTED_MAX_PARAM_COUNT = 2048` 是**硬编码常量**（`tools/difftest/check-tomcat-limits.py:105`）⇒ **它对全部 api 模块用同一个值，不支持分档** ⇒ ⇒ favorite 域单方面配 10000 **必然**打红它 ⇒ 要分档**必须先改这个共享脚本** ⇒ 又一条「属共享面、favorite 只读」的证据 |
| **给 `global` 的一条具体输入（本轮新增）** | Go 侧 `net/url` 的**真实**上限是 `defaultMaxParams = 10000`（Go 1.25.11 `src/net/url/url.go:1001`，`urlParamsWithinMax` 判 `params <= defaultMaxParams`）⇒ **对已经逐字复刻了应用层判据的 api 模块**（favorite 有：`FavoriteApiSupport#goFormFirstValue`），把容器层上限配成 **10000** 就能让这条轴**完全 Go 等价**（总「值」数 >10000 时两侧都是 `net/url` 解析失败 ⇒ 5003）；而对**没有**应用层判据的模块，**2048 才是对的**（那 7 个模块的 2048 判据只存在于容器层）⇒ ⇒ **旋钮值应按「有没有应用层判据」分档，而不是全仓一刀切 2048** ⚠️ **这是输入，不是结论** —— 是否采纳由 `global` 拍板 |

> ⚠️ 本域**没有**为此新建 `DRIFT-FAV-API-xxx` 条目：同一格已由 hot 域登记为
> `DRIFT-HOTAPI-030`（AGENTS.md §3.5 把「Tomcat 三旋钮」列为 `global/cross-domain` 的
> 跨组件不变量）⇒ **重复登记会造成两条台账各说一半、合并时对不上**。

### 🔁 跨域待拍板 · **405 响应多一个 `Content-Type`**（2026-10-25 第十四轮新发现，收口在 `global`）

| | 内容 |
|---|---|
| **事实（api 整域活体对拍实测）** | Go 侧 405 是**裸状态码**：`status=405` · **无 body** · **无 Content-Type**（go-zero `router/patrouter.go:79-82`：`w.Header().Set("Allow", allows)` + `w.WriteHeader(405)`，既不 `fmt.Fprintln` 也不 `http.Error`）；<br>RAG 侧 405：`status=405` · **body 同为空** · **但多一项 `Content-Type: application/json;charset=utf-8`** |
| **落点** | `service/common` 的 `GoStyleRouterErrorResponses.methodNotAllowed` —— 它的 javadoc **明写**「刻意返回 `ResponseEntity<Void>`（body == null）：**容器不会补 Content-Type**」⇒ **实测把这句话证伪了**：Spring 仍会为响应选出一个 MediaType 并写进响应头 |
| **为什么本域不能改** | `service/common/**` 是**共享面**（AGENTS.md §5.3）⇒ favorite 域**只读** ⇒ 只登记 |
| **影响面** | **全仓所有 api 模块**都走这一个类 ⇒ 不止 favorite |
| **探针里怎么记** | `SAME_BODY` 档（status 与 body 两侧全等，**只**允许 Content-Type 不同）⇒ 差异被**量化 + 冻结**，但没被「顺手修掉」 |
| **证据** | 探针 `tools/api-full-parity-probe/favorite/`（EXIT=0）的 `r02_method_not_allowed_post` / `r03_method_not_allowed_get` 两条 ⇒ 逐条值见 `expected.txt` |

> ⚠️ **同类风险**：`GoStyleRouterErrorResponses` 的 `Allow` 头顺序在 Go 侧**不确定**
> （go-zero `patrouter.go:101-117` 遍历 Go map）⇒ 本域路由里 `/folder/list` 同时注册了 GET 与 POST
> ⇒ **Go 的 `Allow` 顺序可能每次不同** ⇒ 探针**刻意不比 `Allow` 的值**（只比 key 集合）
> ⇒ ⚠️ 这是 Go 自身的不稳定，**不是** RAG 的漂移 ⇒ 登记在 `service/common` 的类 javadoc 里。

---

## 4. 证据边界（明确未验的部分）

| 项 | 状态 | 说明 |
|---|---|---|
| 双侧活体对拍（真 Go favorite-api vs RAG） | ✅ **已验**（2026-10-25 **第十四轮**） | 探针 [`tools/api-full-parity-probe/favorite/`](../../../tools/api-full-parity-probe/favorite/README.md) ⇒ **EXIT=0**：<br>· **32 个 case**（鉴权 6 / 路由 3 / 解析 7 / 业务 7 / 只读 4 / 响应头面 1 / 写入 3 / 写入生效验证 1）<br>· **24 条逐字节全等**（含 int64 以字符串出参、时间戳、空集合 `[]`）<br>· 业务组 **7 条**穿过 **HTTP → gRPC → description 反查 → 业务码 → envelope** 全链 ⇒ **逐字节一致**<br>· 1 条 SAME_CODE（黑名单命中，业务码两侧都是 5004）/ 4 条 SAME_STATUS（Go 的 401 是**空 body** ⇒ 取不到业务码；形态差异**已签字** DEV-FAV-API-001）/ 2 条 SAME_BODY（405 的 **Content-Type** 不同 ⇒ **新发现的跨域漂移**）/ 1 条 OBS（Go 多 `traceparent`）<br>⇒ **两个模块都是 `java -jar` 真进程**（⇒ 顺带查出 2 个启动阻塞，见 [`rpc.md`](./rpc.md) §22.3） |
| **servlet filter 链真生效** | ✅ **已验**（2026-10-25 第三轮） | `FavoriteApiRealTomcatGateTest`（**8 例**，`webEnvironment = RANDOM_PORT` ⇒ **真 Tomcat**）：MaxBytes → **413 + 空 body + 无 Content-Type**、413 **压过** 401、未超限仍 401、三旋钮各自生效 ⇒ **真容器的 chain 里真的跑了它**（不再只是「Bean 存在 + `@Order` 一致」） |
| **容器层参数上限的精确口径** | ✅ **已验** | 4 组判别用例（真 Tomcat）⇒ Tomcat 数**总「值」数（含空值）**、Go 数**非空值数** ⇒ 残余窗口见 §3<br>🔴 **2026-10-27 订正**：容器上限已由共享层抬到 **10001**（**运行期**值）⇒ ⇒ §3 那条「残余窗口」**已闭合**（§14）⇒ ⇒ 域内 Go 口径判据**成为唯一判定者** |
| **Go 侧「非空值 >2048」时的逐字节响应** | ✅ **已验**（2026-10-25 第六轮，**双侧活体对拍**） | Go 侧用**真** `httpx.Parse`（`maxFormParamCount=2048` 真生效）⇒ 2049 个非空值 ⇒ **200 + `{code:5003,msg:"参数错误",data:null}`**；RAG 侧走**真 HTTP** ⇒ **逐字节相同** ⇒ 探针 `tools/api-envelope-probe/favorite/`（EXIT=0）⇒ 见 §2.2 第 1 条 |
| 真实 gRPC 链路上的 description 保真 | ✅ **已验**（2026-10-25 第四轮） | `FavoriteGrpcErrorWireFidelityTest`（**4 例**，`favorite-common`，**真 in-process gRPC**）⇒ description 与 `FavoriteErrorMessages.getMsg(码)` **逐字节相同**、经 `bizCodeFromError` **能还原回原业务码**（含唯一只能靠文案反查的 **5304**）、标准 gRPC 码在链路上保真 ⇒ **变异**（去掉 `withDescription`）⇒ **3 例转红** ⇒ 不是假绿 |
| Go 侧 `httpx.Parse` 的入参解析矩阵 | ✅ **已验** | RAG 侧的 DTO 注释记录了 go-zero v1.10.2 真源探针的实测矩阵（缺席/null/类型不符/空串/重复参数/key 大小写），并由 `FavoriteApiInputContractTest`（33 例）守住 |
| `goFormFirstValue` 的两段语义 | ✅ **已验** | 「先滤空值、再取首值」逐条复刻（`FavoriteApiSupport:327-350`），重复参数 `?folderId=123&folderId=456` ⇒ Go 123 / Spring 逗号拼接会失败 —— 两者已刻意分开 |
| `GET /folder/list` 的空 struct body 校验 | ✅ **已验** | Go `httpx.Parse` 无条件跑 `ParseJsonBody` ⇒ 非法 body ⇒ 5003；RAG 用 `assertGoEmptyStructBody` 复刻（`FavoriteController:235-264`） |

---

## 5. 复现命令

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot   # align/g3-article-comment-favorite-hot 工作树

# 🔴 必须带 -am：不带会用 ~/.m2 里的旧共享层 jar 跑 ⇒ 可能假红（见 vs-common.md §5 的「本轮踩坑」）
mvn -o -pl service/favorite/favorite-proto,service/favorite/favorite-common,\
service/favorite/favorite-rpc,service/favorite/favorite-api -am install

# 全量测试（favorite-api 104 例 = 原 97 ＋ 本轮新增 7 例「3s 预算作用域」守门；
#   97 = 33 输入契约 + 36 MVC 管线 + 6 装配 + 4 超时 + 8 真 Tomcat 门禁 + 5 响应字节对拍 + 1 探针（skip）+ 4 端口归属守门）
mvn -o -pl service/favorite/favorite-api -am test

# 单类
mvn -o -pl service/favorite/favorite-api -am test -Dtest='FavoriteApiProtectionWiringTest'
mvn -o -pl service/favorite/favorite-api -am test -Dtest='FavoriteRpcClientTimeoutTest'
mvn -o -pl service/favorite/favorite-api -am test -Dtest='FavoriteApiRealTomcatGateTest'   # 真 Tomcat
mvn -o -pl service/favorite/favorite-api -am test -Dtest='FavoriteApiEnvelopeParityTest'     # 响应字节对拍
mvn -o -pl service/favorite/favorite-api -am test -Dtest='FavoriteApiJwtVerificationScopeTest'  # §2-D5：3s 预算作用域（7 例）

# 🔴 响应字节的双侧活体对拍（Go 真 go-zero 库 vs RAG 真 advice + 真 HTTP）
bash tools/api-envelope-probe/favorite/run.sh
echo $?   # 0 = 10 组 CASE + 1 组 OBS 全部符合冻结期望
```

> ⚠️ `FavoriteApiMvcPipelineTest` 走的是 **MockMvc** ⇒ **不覆盖 servlet filter 链**。
> ⇒ filter 链与容器三旋钮由 `FavoriteApiRealTomcatGateTest`（`RANDOM_PORT`）负责，
> **两者缺一不可**（§3.3：机制要验「生效」，不只是「配了」）。
> ⚠️ **2026-10-27 补**：§2-D5 的守门 `FavoriteApiJwtVerificationScopeTest` 走 **MockMvc**
> ⇒ **它自己也不覆盖真 Tomcat 的 filter 链** ⇒ 故它断言的是**容器装配结果**
> （`ServletContextInitializerBeans` 的排序输出）＋**行为层**（interceptor 消费的是 Filter 的结果），
> 而**真 Tomcat 的响应级语义**仍由 `FavoriteApiRealTomcatGateTest` 兜底（413 压过 401 等）
> ⇒ **两道网职责不同，缺一不可**。

---

## 6. 2026-10-25 第十轮（`align/favorite` 工作树 · 承 `a72a6f2`）

### 6.1 本轮只改注释，零行为改动

| 项 | 结果 |
|---|---|
| 改了什么 | **12 条内联注释**里的 Go 断言补上 `文件:行号`（形态 A 10 + 形态 B 2；口径见 `rpc.md` §16.2） |
| 可执行逻辑改动 | **零**（机器复核：`git diff -U0 -- '*.java'` 去掉注释行后无剩余） |
| 全量测试 | **89 例全绿**（6 个测试类），**BUILD SUCCESS** ⚠️ **此行是第十二轮口径** ⇒ **最新见 §0**（8 个测试类 / 94 例） |
| 审计器 | `python3 tools/assertion-audit/favorite/check-assertion-sources.py` ⇒ **EXIT=0** |

### 6.2 台账体检（本档）

| 项 | 原值 | 现值 | 说明 |
|---|---|---|---|
| **落地形态 · 行数** | 2,920 | **3,116** | +196 行**全部是各轮的纯注释增量**（可执行逻辑零改动） |
| **落地形态 · DTO 数** | **13** | **15** | ⚠️ **13 是从一开始就写错了** —— 基线 `58e8314` 就是 15 个 DTO |
| **RAG 侧行号锚点位移** | `isBlacklisted:220-230` / `FavoriteController:234-263` | **`221-231`** / **`235-264`** | ⚠️ **本轮改注释 ⇒ 行号位移 ⇒ 台账自己的锚点过期** ⇒ 已修（详见 `rpc.md` §16.8 + 坑档 **PIT-FAV-RPC-011**） |

⇒ 按 AGENTS.md §6 的「状态表纪律」补上**核对日期**（2026-10-25）与增量性质。

### 6.3 本档 §3 的「未决 / 已签字偏离」状态（核对 2026-10-25）

| 项 | 状态 |
|---|---|
| **401 body 形态** | ✅ **已签字**（2026-10-25，用户 Jason）—— 不是缺陷 |
| **DEV-FAV-API-002（500 兜底 body）** | ✅ **已签字**（2026-10-25，用户 Jason：保留现状，登记为签字偏离）—— **不是缺陷**；⚠️ **不得「顺手修回 Go 行为」** |
| **容器层参数上限口径** | 🔴 **跨域待拍板** ⇒ 收口在 `global`（与 hot 域 `DRIFT-HOTAPI-030` 同一格）—— **本域不动** |

### 6.4 Go 侧 `api/internal/svc/service_context.go` 的落点（第十轮补记）

Go 侧的 api 依赖装配在 `service/favorite/api/internal/svc/service_context.go`：

```go
redisDB := redis.MustNewRedis(c.BizRedis)      // ← 黑名单用的 Redis
FavoriteRpc: favoriteservice.NewFavoriteService(zrpc.MustNewClient(c.FavoriteRpc)),
CheckBlacklistMiddleware: middleware.NewCheckBlacklistMiddleware(redisDB).Handle,
```

⇒ **本轮补记**（此前台账只对照了黑名单的**行为**，没点名它的**装配出处**）：

| 装配项 | Go | RAG | 状态 |
|---|---|---|---|
| **Redis 黑名单** | `redis.MustNewRedis(c.BizRedis)` + `NewCheckBlacklistMiddleware` | `FavoriteJwtAuthInterceptor` 的 `isBlacklisted`（`:220-230`） | ✅ 行为已在 §1.4 逐条对照（key 前缀 / fail-open / token 取法） |
| **favorite-rpc 客户端** | `zrpc.MustNewClient(c.FavoriteRpc)` | `FavoriteRpcClient` | ✅ 契约在 `proto.md`（7 个 RPC） |

⚠️ **注意 Go 用的是 `Must*`（配置错 ⇒ 启动即 panic）**，RAG 侧是 Spring 的构造注入 ⇒
失败形态不同（Go = 进程起不来；RAG = 容器起不来）⇒ 属于**表达层**，
但**两侧都是「启动即失败」** ⇒ 行为等价，已在 §1.4 的结论中覆盖。

⇒ 本轮新增工具 `tools/ledger-lint/favorite/check-ledger-selfcheck.py`
（Go 源文件覆盖闭合检查）初次跑出的 6 个「未落点」里，本文件是其中之一 ⇒ 现已补记。

---

## 7. 2026-10-25 第十一轮（`align/favorite` 工作树 · 承 `a2fcbc4`）—— **DEV-FAV-API-002 签字**

> 本轮**无代码改动**：只把 🏷 候选转成 ✅ 已签字，并同步各处状态引用。

| 条目 | 签字前 | 签字后 | 附带的前置动作 |
|---|---|---|---|
| **DEV-FAV-API-002**（500 兜底的 body 形态） | 🏷 候选，建议签 | ✅ **已签字** | 客户端若在 500 时**靠 body 区分成因** ⇒ RAG 侧多给了信息 ⇒ 客户端行为**分叉**（RAG 更宽，不是更窄）⇒ 需告知客户端方；「5xx ⇒ 不解析 body」的客户端**两侧都安全** ⇒ 无需改动 |

⇒ **favorite 域现无 🏷 候选**（API 域两条均已签字）。

⚠️ **共享索引未动**（AGENTS.md §5.3 共享面禁写）⇒ **合并后需主会话统一更新**：
`docs/Intentional_Deviations/_INDEX.md` 的已签字条数 **49 → 58**（含本条 API-002）。

---

## 8. 2026-10-25 第十四轮收尾 —— **探针健壮性加固**（零行为改动 · 0 条新漂移）

> 本节**只记指针**：细节在 [`rpc.md`](rpc.md) 的 **§23**（探针基建缺陷，不属于 api 组件的语义漂移）。

| 项 | 值 |
|---|---|
| 影响到的 api 侧探针 | `tools/api-full-parity-probe/favorite/`（**api 整域双侧活体对拍**，32 case） |
| 改了什么 | 探针的 **PG 就绪等待**：40s→**180s**、**探 TCP**（不是 unix socket）、没就绪**重建容器重试**、容器退出即放弃；顺带 Redis 等待 30s→**60s** |
| 对**被测代码**的影响 | **零** —— `service/favorite/**` 本轮**一行没动**；**0 条新漂移** |
| 为什么改 | 第十四轮连跑时 `authority-live` 探针**假红**（单跑绿 / 连跑红）⇒ 根因是**探针自己的等待逻辑**，不是被测代码 ⇒ 元教训 **PIT-FAV-RPC-020** |
| 新守门网 | `tools/probe-gate/favorite/check-pg-readiness.sh`（**EXIT=0**；变异验证：改坏一份 ⇒ 守门网**只**报那一份红） |
| 本轮复跑 | 本域 **9 个探针串行连跑全 EXIT=0**（含 api-full-parity 82s） |

⚠️ **共享索引未动**（AGENTS.md §5.3 共享面禁写）⇒ **合并后需主会话统一更新**：
`docs/Pitfalls/_INDEX.md` 增 **PIT-FAV-RPC-020**；`docs/DRIFT/_INDEX.md` 的 favorite 探针清单加 `probe-gate`。

---

## 9. 2026-10-25 第十四轮收尾 —— 🔴 **真 Tomcat 测试的「端口归属」**（`RANDOM_PORT` 会被别的会话抢号）

> 本节记的是**测试设施缺陷**，**不是** Go ↔ RAG 语义漂移 ⇒ **0 条新漂移**、**产品代码零改动**。
> 完整经过与判据 ⇒ [`docs/Pitfalls/favorite/api.md`](../../Pitfalls/favorite/api.md) 的 **PIT-FAV-API-008**。

### 9.1 现象

全量测试里 `FavoriteApiRealTomcatGateTest` **8 例全 ERROR**（`HTTP/1.1 header parser received no bytes`），
但**单跑绿**、**同 JVM 里后一个 Tomcat 也绿**、**前 20 分钟的同一次全量跑也绿**
⇒ 同 commit、同顺序，**绿↔红翻转**。

### 9.2 根因（**本机实测实证**）

| 环节 | 实测事实 |
|---|---|
| 端口来源 | `RANDOM_PORT` ⇒ OS 从**临时端口段 49152–65535** 挑 —— **Docker 随机发布端口就在这一段** |
| 占用者 | 别的会话的 `cdifftest-postgres` 已连续 **26 小时**占着 `127.0.0.1:55440`（`docker ps` 实测） |
| socket 语义 | `127.0.0.1:P` 与 `0.0.0.0:P` **可共存**（SO_REUSEADDR）⇒ **Tomcat 绑 `0.0.0.0:55440` 照样启动成功** |
| 路由 | 客户端连 `127.0.0.1:P` ⇒ **命中更具体的那个**（实测：specific-127.0.0.1，不是 wildcard-0.0.0.0） |
| 结果 | 请求打到 **PostgreSQL** ⇒ 协议不匹配 ⇒ **零字节即关** ⇒ `EOFException` |

⇒ **Tomcat 确实起来了、端口号确实是我们挑的、日志一切正常** —— 只有客户端连的地址被别人截胡。

### 9.3 修法（**零产品代码改动**，只动测试设施）

| 项 | 改前 | 改后 |
|---|---|---|
| 端口来源 | `RANDOM_PORT`（OS 临时端口段） | **自选专用端口段 21300–21399**（避开临时端口段 ⇒ 与 Docker 发布端口不同段） |
| 端口自检 | 无 | **双绑定自检**：`0.0.0.0:P` **且** `127.0.0.1:P` 都要能绑 ⇒ 任何第三方持有者都被排除 |
| 测试类配置 | `webEnvironment = RANDOM_PORT` | `DEFINED_PORT` + `@DynamicPropertySource`（⚠️ **两个都要探**：只探 `127.0.0.1` 会漏掉「上一个类的 Tomcat 还占着通配地址」⇒ 本轮落地时**实测踩到**） |
| 失败信息 | 裸 `IOException` | 包装成「端口 N 上读到了 0 字节…；该端口的监听者=[lsof 输出]」 |
| 探针侧（**同源**） | `tools/{api-full-parity,grpc-contract}-probe` 用 `bind(("127.0.0.1",0))` | 同样改成**专用端口段 21400–21599 + 双绑定自检**（⚠️ 探针侧更危险：`wait_port` 只验「连得上」⇒ **连到别人的服务也算就绪** ⇒ 探针结论**整个不可信**） |

新增支撑类：`service/favorite/favorite-api/src/test/java/com/rag/favorite/favorite/api/support/RealTomcatPort.java`

### 9.4 验证

| 项 | 结果 |
|---|---|
| `FavoriteApiRealTomcatGateTest` + `FavoriteApiEnvelopeParityTest` | ✅ **8/8 + 5/5 全绿**，且**两个 Tomcat 各拿各的端口**（21300 / 21301 ⇒ 双绑定自检确实避开了上一个类） |
| 新增守门测试 `RealTomcatPortTest` | ✅ **4/4 全绿**（**与 Spring 测试同 JVM 跑** ⇒ 证明它不依赖「谁还活着」） |
| `tools/probe-gate/favorite/check-port-ownership.sh`（新） | ✅ **EXIT=0**（2 个探针 × 4 场景 = 8 项全过） |
| 端口分配逻辑单测（自写） | ✅ 连续取 5 个**互不相同**；人为占住 `127.0.0.1:21400` 后**跳过它** ⇒ 双绑定自检有效 |
| 本域全量测试 | ✅ **BUILD SUCCESS** —— `favorite-api` **94 例**（含 1 skip）· `favorite-rpc` 84 例（含 1 skip）· `favorite-common` 29 例 · `service/common` 174 例 · `users-common` 33 例 ⇒ **全绿，0 失败 0 错误** |

⚠️ **守门测试自己也被环境左右过一次（已修，值得记）**：
第一版守门测试写的是「**占住端口段的第一个端口** ⇒ 断言 `pick()` 绕开它」
⇒ **单独跑绿、全量套件里红**（`BindException: Address already in use`）
⇒ 因为**同一个 JVM 里上一个 Spring 测试类的 Tomcat 还活着**（Spring 缓存测试上下文）
⇒ 它占的**本来就是**端口段第一个端口 ⇒ 测试想占的那个**早就被占着**
⇒ ⇒ 改成：判定逻辑 `RealTomcatPort.isFree(int)` **包级可见** + 守门测试用**独立脚手架端口段 21950–21999**。
⇒ 通用判据：**「守门网在单跑与全量里结论不同」⇒ 先怀疑守门网自己依赖了共享状态。**

### 9.5 ⚠️ 推广面（**归 `global` 收口域**，本域只写自己这份）

**2026-10-25 第十五轮复核后的实测口径**（只算**真用**的 ⇒ **剥掉注释**再数 ——
本域那两个只在 javadoc 里提到它的**支撑类**因此不计）：

⇒ **修完之后**：**7 个域 / 14 个测试类**还在用 `RANDOM_PORT`（**favorite 域已归零** ⇒ 本轮已修）。

> 🔴🔴 **2026-10-26 合并当天订正（第三十轮）**：本行原写「**6 个域 / 10 个测试类**」
> ⇒ **合并 `global` 的成果后变成 7 个域 / 14 个测试类** ——
> 🔴 **新增的那个域是 `common`（共享层本身）**，4 个测试类全部来自 `global` 的共享层改动
> （`GoStyleFormLimitEnvelopeGateTest` / `GoStyleJsonBodyRawHeaderTomcatTest` /
> `GoStyleJsonContentTypeCharsetTomcatTest` / `GoStyleParamLimitGateTest`）
> ⇒ ⇒ **不是本域的改动** ⇒ ⇒ 这条计数**是全仓事实**，**别的域一合并就会变**
> ⇒ ⇒ `tools/ledger-lint/favorite/check-ledger-vs-reality.py` 会**自动**把它抓出来（它每次都按**当前仓库**重算）⇒ ⇒ **不用靠人记得改**。
> ⇒ ⇒ 顺带一条观察：那 4 个在**共享层**里 ⇒ ⇒ 「需要一份**共享**的端口选取实现」这个选项
> ⇒ 对它们**已经是既成事实**（共享层自己就在用 `RANDOM_PORT`）⇒ ⇒ 落点二选一的问题**缩小到其余各域**。

| 域 | 个数 | 文件 |
|---|---|---|
| `message` | **3** | `MessageApiProtectionRealTomcatTest` / `MessageApiTimeoutRealTomcatTest` / `GoStyleGrpcClientBreakerE2ETest`（⚠️ `MessageApiProtectionMvcTest` 的 `RANDOM_PORT` 只出现在**注释**里 ⇒ 不算） |
| `favorite` | **0** | ✅ **本轮已修** ⇒ 改成 `DEFINED_PORT` + 自选端口段 ⇒ **现在 0 个** |
| `hot` | 2 | `HotApiRealTomcatGateTest` / `GoStyleGrpcClientBreakerE2ETest` |
| `article` | 2 | `ArticleApiFormParamLimitTest` / `ArticleApiAssemblyWiringTest` |
| `users/user` | 1 | `GoStyleGrpcClientBreakerE2ETest` |
| `task` | 1 | `TaskApiContentTypeTest` |
| `like` | 1 | `LikeApiRouterErrorRealTomcatTest` |
| **`common`（共享层本身）** | **4** | 🆕 **合并当天新增**（`global` 的共享层改动）—— `GoStyleFormLimitEnvelopeGateTest` / `GoStyleJsonBodyRawHeaderTomcatTest` / `GoStyleJsonContentTypeCharsetTomcatTest` / `GoStyleParamLimitGateTest` |

⇒ **同款暴露面全仓都在** ⇒ 需要一份**共享**的端口选取实现
⇒ 落点二选一（**本域不拍板**）：`service/common` 的 test-jar，**或**各域各自复制本域这份
⇒ **本域只写自己这份**（`service/common/**` 是共享面，本域不碰）。

---

## 10. 2026-10-25 第十五轮 —— 📌 **域内「可修漂移 / 未知项」盘点**（本档只留指针）

> 盘点全表在 [`rpc.md`](./rpc.md) **§24.1**（四类：A 已修/已签字 · B 域内可补 · C 跨域 · D 库级不做）。
> **本档相关的两条**：

| 项 | 内容 |
|---|---|
| **本域的「可修漂移」** | **0 条** —— 本档 §2 的 4 处（L2×2 + javadoc 假断言×2 + L1×1）已修；2 条已签字（DEV-FAV-API-001/002）⇒ **无 🏷 候选** |
| **U8** | ✅ **已闭（第十六轮）** —— 本档 §4 的「双侧活体对拍（真 Go favorite-api vs RAG）」那 32 个 case **保持不变**（**create 仍只有失败形态**：`b01`/`b02` = `downstream_absent` ⇒ 5001，**这一层不需要动**：api 面要证明的是「下游不在时 API 怎么表现」）；<br>成功路径改由 **rpc 域**的 gRPC 契约探针**第二轮**覆盖（create_folder / create_item 真成功 + `ensureUserExists` 五分支 + 落库等价性 ⇒ **15 case 全 SAME**）⇒ 详见 [`rpc.md`](./rpc.md) **§25** |
| **跨域 2 条** | ① 容器层参数上限口径 ② 405 多一个 `Content-Type` ⇒ 收口在 `global`（本档 §3） |

⇒ **台账数字过期问题的守门网** ⇒ 新工具
[`tools/ledger-lint/favorite/check-ledger-vs-reality.py`](../../../tools/ledger-lint/favorite/check-ledger-vs-reality.py)
（**从台账正文抽断言** ⇄ **独立从仓库算实际值** ⇒ 逐条比对）
⇒ 详见 [`rpc.md`](./rpc.md) §24.3。

## 11. 2026-10-26 第十七轮 —— 📌 **生产启动形态**（本档只留指针）

> 本轮全部内容在 [`rpc.md`](./rpc.md) **§26**（favorite-rpc 侧）。
> **本档（favorite-api）只受影响一处指针**，不改任何 api 侧代码。

| 项 | 内容 |
|---|---|
| **本轮做了什么** | 🔴 **3 个生产启动阻塞级缺陷已修**（全在 `favorite-rpc`）：① gRPC client 地址**两斜杠** ⇒ 启动期抛 `IllegalArgumentException`；② `web-application-type` 缺失 ⇒ Boot 判定 SERVLET 而无 servlet 容器 ⇒ **启动即死**；③ gRPC **reflection 暴露面**漏关 ⇒ 比真源多暴露一整个反射面 ⇒ **3 个守门测试类 / 14 例** + **变异验证 4 条** + 🆕 探针 `tools/prod-startup-probe/favorite/`（**A/B/C** ⇒ EXIT=0） |
| **api 侧有没有受影响** | ✅ **没有** —— 本轮改动全部落在 `favorite-rpc` 的 `application.yml` / `pom.xml` 注释 + `favorite-rpc` 的 3 个新测试类<br>⇒ **api 的 94 例、rpc 的 99 例、common 的 29 例全绿不变**；`tools/api-full-parity-probe/favorite/run.sh` **复跑仍 EXIT=0** |
| **新增域内未决项** | 🔴 **U9**（部署形态类 · 待拍板）—— Go 生产形态有 **16062** 那个 HTTP 面（`/metrics` `/healthz` pprof），RAG 侧 `web-application-type: none` ⇒ **没有** ⇒ 要么 RAG 侧也开（**部署形态变更**），要么**签字接受** ⇒ 详见 [`rpc.md`](./rpc.md) **§26.8** |
| **跨域（只登记）** | 其余 **5 个** rpc 模块（`article-rpc` / `comment-rpc` / `follow-rpc` / `like-rpc` / `task-rpc`）**同样漏配** `grpc.server.reflection-service-enabled` ⇒ 收口在 `global` ⇒ 逐条 Go 侧判据见 [`rpc.md`](./rpc.md) **§26.4** |
| **⚠️ 连带翻案** | 第十四轮 §22.3 的「启动阻塞 #1 已修」是**半修**（只改了**探针启动参数**，生产 yaml 一行没改）⇒ 坑档 **PIT-FAV-RPC-026** ⇒ 详见 [`rpc.md`](./rpc.md) **§26.5** |

---

## 12. 2026-10-26 合并当天（第三十一轮）—— 🔴 **本域守门网被「合法改动」打断** ⇒ 断言口径从「绝对 `@Order`」改成「Go 相对链序」

> **触发**：2026-10-26 把 `align/favorite` 合进 `do-my-own-gh`（`global` 的合并 `4f839f9` **已先落地**）
> ⇒ 合并树全量 `mvn test` 在 **`favorite-api`** 红：
> ```
> FavoriteApiProtectionWiringTest.serverSideProtectionFiltersAreBeansInGoChainOrder:128
>   MaxConns 应最先 ==> expected: <-2147483647> but was: <-2147483644>
> ```
> ⇒ `-2147483647 = Ordered.HIGHEST_PRECEDENCE + 1`（旧值）· `-2147483644 = + 4`（新值）。
>
> ⚠️ **本条是「合并」这一步本身的产出** —— 本分支（`align/favorite`）自始至终全绿，
> **只有把 global 的合并拿进来才红** ⇒ 这正是「**跑绿 ≠ 对**」的又一次实证（AGENTS.md §0）。

### 12.1 核证结论：🔴 **不是行为回归，是本域测试的跨域耦合**

| 项 | 事实 | 证据 |
|---|---|---|
| **谁改的** | `global` 第十九轮（T-8）把**保护层四件套整体后移**：`@Order` `+1..+6` ⇒ `+4..+8` | 🔴 **共享层（`service/common`）的写权在 `global` 域** ⇒ 本域**只记 `@Order` 的值**，不写行号（行号随分支不同）：`GoStyleMaxConnsFilter` = `+4` · `GoStyleBreakerFilter` = `+5` · `GoStyleCpuSheddingFilter` = `+6` · `GoStyleMaxBytesFilter` = `+8`（**主干 `4f839f9` 的值**） |
| **为什么合法** | 为了把 `GoStyleTraceparentFilter` 放到「CORS 之后、MaxConns 之前」（Go 侧 Trace 是**链内第一个**）⇒ 被 MaxConns 拒成 503 的响应**也带** traceparent | 同上（**共享层，`global` 域**）：`GoStyleTraceparentFilter` = `+3`（主干 `4f839f9` 的值）· Go 真源：go-zero v1.10.2 `rest/engine.go:135`（`chn.Append(handler.TraceHandler(...`）<br>⚠️ **该类在本分支（`align/favorite`）尚不存在** ⇒ 故**不写仓内路径**（AGENTS.md 域内纪律：跨分支的文件不写成仓内路径） |
| **相对顺序变了吗** | ✅ **没变** —— MaxConns → Breaker → Shedding → MaxBytes 原样保留 | 主干 `4f839f9` 四个值 `+4/+5/+6/+8` 严格递增 |
| **Go 语义一致吗** | ✅ **一致** —— Go 真源的四个保护中间件相对次序就是 MaxConns → Breaker → Shedding → MaxBytes | go-zero v1.10.2 `rest/engine.go:130-168`（`buildChainWithNativeMiddlewares`）：`MaxConns` `:146` → `Breaker` `:149` → `Shedding` `:152` → `MaxBytes` `:164` |
| **那为什么红** | 🔴 **我的测试断言的是「绝对数值」**（`+1/+2/+3/+6`）⇒ 共享层合法调整**排版** ⇒ 本域守门网**假红** | 改前断言在 `service/favorite/favorite-api/src/test/java/com/rag/favorite/favorite/api/FavoriteApiProtectionWiringTest.java` 的 `serverSideProtectionFiltersAreBeansInGoChainOrder` 方法内（**本分支 `align/favorite` 的 `:128-131`**） |

> 🟢 **结论**：两侧的 Go 语义**完全一致**，**没有新漂移** ⇒ 本轮**零行为改动**。

### 12.2 判据归属（本域 vs `global`）

| 不变量 | 归属 | 理由 |
|---|---|---|
| 四个保护 filter 的**相对**次序 == Go 的链序 | ✅ **本域（`favorite-api`）** | 它决定 favorite 请求「**先被谁拒**」（MaxConns 503 / Breaker 500 / Shedding 503 / MaxBytes 413）⇒ **本域可观察** ⇒ 本域守门网应当锁它 |
| 四个 filter 整块相对 Trace / Log / Prometheus / 共享中间件（`HttpMiddlewareFilter`）的**绝对位置** | 🔴 **`global`（共享层）** | 改法落在 `service/common` ⇒ AGENTS.md §3.5「**修法落在共享层 ⇒ 收口域 = `global`**」⇒ **已回执 `global` 域**，请其在**所有者**侧加门禁 |

### 12.3 修法：**只锁 Go 真源承载语义的那部分**

| 项 | 改前 | 改后 |
|---|---|---|
| 断言口径 | `assertEquals(HIGHEST_PRECEDENCE + 1/2/3/6, …)` —— **绝对值** | `assertTrue(orderOf(a) < orderOf(b), …)` —— **相对链序**（3 个独立测试方法，**一条断言一个方法**，PIT-FAV-RPC-036） |
| 锚点 | 无 Go 出处（只写「Go 的中间件顺序」四个字） | 每条断言带 go-zero **消费点**行号：`engine.go:146/149/152/164`（§3.3.1 要求「行号指向消费点而非声明点」⇒ 引用的是 `chn.Append(...)` 那几行，**不是**上面的 `if` 行） |
| 失败信息 | 只说「MaxConns 应最先」，**不携带实际值** | 带**实际值 + Go 出处**：例如「实际 MaxConns=-2147483646 Breaker=-2147483647」 |
| 用例数 | 3 例 | **6 例**（`serverSideProtectionFiltersAreBeans` + 3 条链序） |

### 12.4 变异自证（**证明新守门不是假绿**）

| 步骤 | 结果 |
|---|---|
| 本分支（旧共享层 `+1/+2/+3/+6`）跑守门 | ✅ **6/6 绿** |
| **变异**：把共享层 MaxConns / Breaker 的 `@Order` 对调（`+1` ⇄ `+2`） | 🔴 **精确转红**：**只有** `maxConnsRunsBeforeBreaker` 一条红，报「实际 MaxConns=-2147483646 Breaker=-2147483647」⇒ **不是恒绿** |
| 还原共享层（`git checkout -- service/common/`） | ✅ **6/6 绿** · `git status` 只剩本域 1 个测试文件 |
| 合并树（global 的毫秒版共享层 + `+4/+5/+6/+8`） | ✅ **6/6 绿**（⇒ 新断言在**两侧**都成立） |

⇒ 🟢 **教训已入坑档** ⇒ [`docs/Pitfalls/favorite/api.md`](../../Pitfalls/favorite/api.md) **PIT-FAV-API-009**。

---

## 13. 🔴 D6 · Redis **TCP 连接超时**漂移（Go `DialTimeout` **5s** vs Lettuce 默认 **10s**）—— 2026-10-27 已修

### 13.1 漂移事实（修前）

| 项 | Go（真源，唯一基准） | RAG 修前 | RAG 修后 |
|---|---|---|---|
| **TCP 连接**超时<br>（`DialTimeout` / `SocketOptions.connectTimeout`） | **5s** | 🔴 **10s** | ✅ **5s** |
| 命令超时<br>（`Read/WriteTimeout` / `commandTimeout`） | **3s** | ✅ 3s | ✅ 3s |

**Go 出处（消费点，不是声明点）**：

```
service/favorite/api/internal/svc/service_context.go:20
    redisDB := redis.MustNewRedis(c.BizRedis)
  → go-zero v1.10.2 core/stores/redis/redisclientmanager.go:32-40
    red.NewClient(&red.Options{Addr, Username, Password, DB, MaxRetries, MinIdleConns, TLSConfig})
    ⇒ **没有设置 DialTimeout**
  → go-redis v9.20.0 options.go:338-340
    if opt.DialTimeout == 0 { opt.DialTimeout = 5 * time.Second }
    ⇒ DialTimeout = **5s**
    （ReadTimeout/WriteTimeout 同理走 options.go:364-379 ⇒ 3s）
```

**RAG 侧为什么「不写就错」**（Spring Boot **4.0.8**，L3 字节码实测）：

| 事实 | 证据 |
|---|---|
| `spring.data.redis.connect-timeout` **非空时**才设 `SocketOptions.connectTimeout` | `LettuceConnectionConfiguration.createClientOptions` 字节码 offset 9→31：`getConnectTimeout()` → `SocketOptions.builder().connectTimeout(...)` → `ClientOptions.Builder.socketOptions(...)` |
| **不写 ⇒ 整段不设** ⇒ 落回 Lettuce 默认 | lettuce-core 6.8.2 `SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10`（秒） |
| `spring.data.redis.timeout` 只喂**命令**超时，**不再**兼管连接超时 | 同一自动配置里 `getTimeout()` → `LettuceClientConfigurationBuilder.commandTimeout(...)` ⇒ **Boot 2/3 才是同一个属性** |
| 该属性确实存在（不是臆造的名字） | `spring-boot-data-redis-4.0.8.jar` 的 `spring-configuration-metadata.json`：`spring.data.redis.connect-timeout`（defaultValue = None） |

⇒ **修前漂移 = 2 倍**（Redis 不可达时 RAG 要等 10s，Go 只等 5s）。

> ⚠️ 本条**只影响连接建立阶段**；命令超时两侧本就一致（3s）。
> ⚠️ favorite-api 的黑名单查询（`FavoriteJwtAuthInterceptor` → `redisTemplate.hasKey`）
> **在 3s HTTP 预算之内** ⇒ 连接超时 5s vs 10s 的差异会被预算**先截断** ⇒
> **本条在 favorite-api 的可观察影响有限**；真正裸露的是**没有 HTTP 预算的 rpc 组件**
> （comment-rpc / hot-rpc）⇒ 那两条才是本轮的主要收益面。

### 13.2 修法与守门

| 项 | 内容 |
|---|---|
| 修法 | `application.yml` 的 `spring.data.redis` 下显式加 `connect-timeout: 5s`（**域内文件 ⇒ 域侧自修**，不依赖共享层） |
| 为什么共享层不参与 | `service/common` 的 `GoStyleRedisClientAutoConfiguration` **只**设 `disconnectedBehavior(REJECT_COMMANDS)`、**不碰** `socketOptions` / `timeoutOptions`（其 javadoc「为什么不动命令超时」段已写明）⇒ 连接超时**完全由本域 yml 决定** ⇒ **不装副本、不重复收编**。<br>⚠️ **引用出处说明（2026-10-27 补 · 防止后人按本树复核时判「引用的文件不存在」）**：该类**不在本树**（实测 `find service/common/src/main/java -iname "*redis*"` ⇒ **零命中**；G4 的 `2f12236` 不在我树 HEAD 历史）⇒ ⇒ 上面的结论是**照 G4 树实测**的（跨树指针）⇒ ⇒ **本树读者要复核这条，须先合入共享层**（见 [`favorite/vs-common.md`](vs-common.md) §4.2 与 [`comment/vs-common.md`](../../DRIFT/comment/vs-common.md) §4.3 的选项 D） |
| 守门 | 新增 1 个契约守门（**1 例**）—— 走**整条真实链路**：`ConfigDataApplicationContextInitializer` 加载本模块**真实的** `application.yml` → Boot **真实** `DataRedisAutoConfiguration`（它 `@Import` 了 `LettuceConnectionConfiguration`）→ 真 `LettuceConnectionFactory` → 断言**消费点** `getClientConfiguration().getClientOptions().get().getSocketOptions().getConnectTimeout()` 与 `getClientConfiguration().getCommandTimeout()` |
| 为什么断言「消费点」而不是「yml 里有这个键」 | 后者挡不住「属性名写错 ⇒ Spring 静默用默认值」（§3.3.1） |

### 🧪 变异验证（防假绿）

| 步骤 | 结果 |
|---|---|
| 本轮跑守门 | ✅ **1/1 绿** |
| **变异**：删掉 `connect-timeout: 5s` 一行（其余不动） | 🔴 **精确转红**：报「**expected: 5S but was: 10S**」⇒ 守门**真的**在读 Lettuce 的 socket 连接超时，不是恒绿 |
| 还原 | ✅ **1/1 绿** · `sha256` 与变异前备份**逐字一致** |
| 整域回归 | ✅ **四域 12 模块 = 919 条**（article-common 15 / article-api 99 / article-rpc 155 / comment-common 27 / comment-api 52 / comment-rpc 116 / favorite-common 29 / favorite-api 105 / favorite-rpc 114 / hot-common 14 / hot-api 96 / hot-rpc 97）⇒ **0 failures / 0 errors / 4 skipped** · 9:44 min · BUILD SUCCESS<br>口径：`mvn -o --fail-at-end -pl <12 模块> -am test`；基线 `eb4d1d2` + 本轮改动；核对日期 **2026-10-27**；两口径（模块汇总行求和 ≡ 逐类明细行求和）**逐模块一致** |

**证据强度**：L3 静态实测（Boot 4.0.8 **字节码** + Lettuce 6.8.2 常量 + go-redis v9.20.0 源码）
＋ L4 **真容器**装配断言（`ApplicationContextRunner` + Boot 真实 `DataRedisAutoConfiguration`）。

---

## 14. 2026-10-27 第十八轮 —— 🔴 **共享层合并（选项 D）** + 容器上限那格的**域侧动作**

### 14.1 为什么这一轮必须做（起因链）

| 环节 | 事实 |
|---|---|
| **G4 的共享层落码** | `cd893d1`（类 `GoStyleTomcatParameterLimitCustomizer`）把「容器上限的抬升」与「闸门的有无」**解耦** ⇒ 容器上限**无条件**抬到 **10001**（= Go `net/url` 段数上限 10000 + 1）⇒ ⇒ **判定权交还应用层** |
| **favorite 域的运行期变化** | 本域 `mode` 未声明 ⇒ 缺省 `OFF` ⇒ 但**上限照抬** ⇒ 运行期上限 **2048 → 10001** ⇒ ⇒ **跨域可观察行为变更**：总「值」数 **2049~10000 且非空值 ≤2048** 那一格，Go **放行**、RAG 由「拒（200 + 5003）」变成「放行」⇒ ⇒ 旧漂移 `DRIFT-HOTAPI-030` 的这一格**闭合** |
| **域内那条红** | `FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError` 断言「**必须**落 `{code:5003}`」⇒ 而 5003 **恰恰是 RAG 独有的形态**（Go 放行）⇒ ⇒ **那条绿测试当时守的是一条漂移**（AGENTS.md §3.3：「绿测试可能是『错误答案的守卫』」） |
| **G4 的书面要求** | `docs/DRIFT/global/vs-common.md` §4.10.25.4：「**域侧动作（G3）** … **本层不代改（域侧写权）** ⇒ ⇒ **须在 G3 那一笔 merge 里一起改**（否则留下半截）」⇒ ⇒ 本轮就是那一笔 merge |
| **合并方式** | **选项 D**：`git merge --no-ff --no-commit align/g4-service-common`（起始 `cec7131`，落点 `cd893d1`）⇒ ⇒ **共享层改动与本域改动进同一笔 merge commit**（不留半截） |
| **冲突面（实测，非推断）** | 以 merge-base `a282012` 为界：本树 **77** 个改动文件 ∩ G4 树 **52** 个改动文件 = **空集** ⇒ ⇒ **无文本冲突** |

### 14.2 域侧改了什么（都在本域写权面内）

| 项 | 内容 |
|---|---|
| **断言改写** | `containerParameterLimitIsSurfacedAsGoParseError`：从「必须含 `code:5003`」改为 **Go 语义** —— ① HTTP **200**；② **不含** `code:5003`；③ **含** `code:1015`（证明**真的进了业务层**）；④ **不是** Tomcat HTML 错误页 |
| **为什么 1015 是 Go 的形态（不是测试环境的巧合）** | Go：`favorite/common/errmsg.go:113-116`（普通 error ⇒ `status.FromError` 的 `ok=false` ⇒ `CodeServerBusy = 1015`，:12 ＋ 文案「服务繁忙」:46）＋ `:135-136`（gRPC `Unavailable` 也落 `default` ⇒ **同样 1015**）；<br>RAG：`FavoriteGrpcError#bizCodeFromError`（非 gRPC status ⇒ 1015；`Unavailable` 不在白名单 ⇒ `default` ⇒ 1015）⇒ ⇒ **两侧同码同形** |
| **方法名故意保留旧名** | `…IsSurfacedAsGoParseError` ⇒ 为了**不打断** `global/vs-common.md` §4.10.25.4 的引用 ⇒ ⇒ **以 DisplayName 与断言为准**（已在方法 javadoc 里写明） |
| **类 javadoc** | 「⚠️ 两个上限的口径不同」那节**保留原文**（2026-10-25 的历史实测）＋ 加**订正注记**（容器层不再当判定权）⇒ **不改写历史**（AGENTS.md 纪律） |
| **变异守护换血** | 新增 **MUT-F**（域内判据从「数非空值」退化成「数总值」）⇒ ✅ **1 例转红** ⇒ 它**替掉**已失效的 MUT-A / MUT-C 对该断言的守护（两者实测 **0 红**）⇒ ⇒ 坑档 ⇒ [`PIT-FAV-API-010`](../../Pitfalls/favorite/api.md) |

### 14.3 实测（可复算）

| 项 | 值 |
|---|---|
| 守门（合并后 + 断言改写） | ✅ **8/8 绿**（`FavoriteApiRealTomcatGateTest`） |
| **变异 MUT-F** | ✅ **1 例转红**（失败消息点名「不得落 5003」，实际 body = `{"code":5003,…}`）⇒ 其余 7 条仍绿 ⇒ **精确变红** |
| 变异全表复验 | MUT-A **0 红** · MUT-C **0 红** · MUT-B **2 红** · MUT-D **4 红** · MUT-E **8 error** · MUT-F **1 红** ⇒ ⇒ **无断言失去专属变异** |
| 整域回归 | **见 §14.4** |

### 14.4 🔴 整域回归：**被机器级 Docker 端口转发故障阻塞**（不是代码问题，已用探针证伪）

| 项 | 值 |
|---|---|
| **已完成部分（全绿）** | `users-common` **33** · **`common` 324**（**合并进来的共享层本体**，61 个测试类）· `article-common` **15** ⇒ 合计 **372 条 / 0 failures / 0 errors**（两口径：模块汇总行求和 ≡ 逐类明细行求和；类数 83）⇒ ⇒ **共享层随合并进来后自身 324 条全绿** |
| **卡住的位置** | `article-rpc` 的 `ArticleTransactionWiringTest`（**Testcontainers 起 PG**）⇒ 线程栈显示主线程停在 `org.postgresql.core.v3.ConnectionFactoryImpl.doAuthentication`（**读不到 PG 的 auth 包**）⇒ 容器日志显示 PG 本身 **「ready to accept connections」** ⇒ ⇒ **不是代码、不是容器内服务** |
| **判据（探针，非推断）** | ① `docker exec ddl-favorite-pg psql …` ⇒ **容器内连得上**（只报角色不存在，不是连接失败）⇒ **容器内服务正常**；② 宿主机 socket 探针：绝大多数容器的映射口 **「TCP 连上但 3~5s 读不到任何字节」**（PG/Redis/Kafka/Nacos/minio/… 全中），而 `Sea-user-rpc` / `milvus` / `jaeger` / `neo4j` 四个**正常**（前者收到 32 字节，后三者立即 0 字节 EOF）⇒ ⇒ **宿主 → 容器的转发路径对多数容器失效**，与本轮改动**无关** |
| **处置** | 🔴 停掉卡住的跑批 + 清掉残留容器 ⇒ **不做跨 lane 的破坏性处置**（重启 Docker Desktop 会连带干掉 G1/G2/G4 各自跑了 30+ 小时的 `ddl-*-pg` 等容器 ⇒ **不擅自做**）⇒ ⇒ **全域 12 模块的完整回归待 Docker 恢复后补跑**（基线：合并前 **922 条**全绿，2026-10-27 09:44 min） |
| **证据强度** | 本条 = **L3 环境级实测**（socket 探针 + 容器内直连 + 线程栈），**不**据此声称「合并后全域已验」 |
