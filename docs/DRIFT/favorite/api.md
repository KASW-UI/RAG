<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# DRIFT 台账 · `service/favorite/favorite-api`

> **核对时间：2026-10-25**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/api`）
> **本轮性质**：**首轮组件级 Go ↔ RAG 逐条对照**（此前该组件台账为 **0 字节** —— 从未对照）。
> **证据强度**：✅ **真实 MVC 管线 + 真实 gRPC 拦截器装配 + 源码逐行对照**；
> 🔴 **无双侧活体对拍**（Go 侧 favorite-api 依赖 `etcd` + favorite-rpc + Redis，本轮起不来）。
> ⇒ 本台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| 落地形态 | 31 个 main 文件 / 2,920 行 / 13 个 DTO / 7 个 Service / 1 个 Controller |
| 测试 | **6 个测试类 / 89 例全绿**（33 输入契约 + 36 MVC 管线 + 3 装配 + 4 超时 + **8 真 Tomcat 门禁** + **5 响应字节对拍**）→ 第五轮 **+1 个测试类 / +5 例** |
| 本轮发现 | **2 条 L1/L2 语义漂移（均已修）** + **2 条 javadoc 假断言（已修）** + **1 条已签字偏离** |
| 未决 | **1 条已签字**（401 body 形态，✅ 2026-10-25 用户签字）+ **1 条 OBS**（500 兜底 body）+ **1 条跨域待拍板**（容器层参数上限口径 ⇒ 收口在 `global`） |
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
| Authorization 缺失 / 空 token | 401 | `FavoriteJwtAuthInterceptor:154-165` | ✅ |
| 验签失败（签名错/过期/格式错，**Go 不区分原因**） | 401 | `:167-178`（catch `Throwable`） | ✅ |
| claim `userId` 原样存入 ctx | `authhandler.go:71-79` | `request.setAttribute("userId", claims.get("userId"))` | ✅ |
| 黑名单 key | `user:jwt_blacklist:<token>` | `BLACKLIST_KEY_PREFIX:110` | ✅ |
| 黑名单 token 取法 | `TrimSpace(TrimPrefix(authHeader,"Bearer "))`（**大小写敏感** + TrimSpace） | `:197` `goTrimSpace(BearerTokenExtractor.trimBearerPrefix(authHeader))` | ✅（且验签用的大小写不敏感取法，两段不一致也原样保留） |
| **Redis 故障** | `err == nil && exists` ⇒ **fail-open 放行** | `isBlacklisted:220-230` 吞异常 ⇒ 放行 | ✅ |
| **执行顺序** | 先验签（JWT）→ 后黑名单 | `preHandle` 阶段 1 → 阶段 2 | ✅ |
| **404 / 405 不经鉴权** | chain 只挂在匹配上的路由 | `handler instanceof HandlerMethod` | ✅ |

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
| `max-parameter-count`（**容器层**） | Go 的 `net/http` **不限参数个数**；Go 的真实上限是 `net/url` 的 `defaultMaxParams = 10000`（Go 1.25.11 `src/net/url/url.go:1001`） | `:66` = **2048** | ⚠️ **值与口径都不同**（Tomcat 数**总「值」数含空值** / Go 数**非空值数**）⇒ 残余窗口见 §3 |
| `maxFormParamCount`（**应用层**） | `httpx/util.go:15` = **2048**，数**非空值**、计数器 `n` **跨所有 key 共享** | `FavoriteApiSupport#goFormFirstValue`（Go 逐字复刻） | ✅ 源码级一致；⚠️ **在真 Tomcat 栈上不可达**（容器在第 2049 个就先拒了）⇒ 见 §2-D4 |
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
| **回归网** | `FavoriteApiProtectionWiringTest`（3 例）：读 starter 的 `GlobalClientInterceptorRegistry` 断言拦截器**真的在里面**（只断言「Bean 存在」不够 —— 证明不了 channel factory 会不会消费它，§3.3）；断言保护层默认值 == Go 默认值；断言 4 个 filter 的 `@Order` 与 Go 链序一致 |

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

### 2.1 🧪 变异验证表（`FavoriteApiRealTomcatGateTest` 8 例 · 防假绿）

> 目的：证明这 8 条断言**不是假绿** —— 每一行都是「把实现/配置改坏 ⇒ 对应断言必须转红」（§3.3）。
> ⚠️ 全部变异都在本地临时施加、**立即还原**（已用 `git status` 复核**零残留**）。

| # | 变异 | 预期转红 | 实测 |
|---|---|---|---|
| **MUT-A** | 去掉 `parameterMap` 的异常归一（回到裸 `request.getParameterMap()`） | `containerParameterLimitIsSurfacedAsGoParseError` + `nonEmptyFormValueCountOverLimitIsRejected` | ✅ **2 例转红** —— 实测 HTTP **500** + `{code:5001}`（即修复前的形态） |
| **MUT-B** | `rag.protection.max-bytes` 1024 → 8192 | `oversizedBodyYieldsBare413` + `oversizedBodyBeatsAuth` | ✅ **2 例转红**（413 ⇒ 401） |
| **MUT-C** | `server.tomcat.max-parameter-count` 2048 → 1000 | `nonEmptyFormValueCountAtLimitIsPassedThrough` | ✅ **1 例转红**（2048 个值被容器提前拒 ⇒ 5003） |
| **MUT-D** | `server.max-http-request-header-size` 1028KB → 8KB | `longRequestLineIsAccepted` + 3 条带长 query 的 | ✅ **4 例转红**（Tomcat 400 HTML 错误页） |
| **MUT-E** | `server.tomcat.relaxed-query-chars` → 错值 | `bracketNotationIsAcceptedLikeGo` | ✅ **1 例转红**（Tomcat 400 HTML 错误页） |

> ⚠️ **MUT-E 顺带撞出一条绑定细节**（值得单独记）：用**测试属性**把
> `server.tomcat.relaxed-query-chars` 覆盖成**空串**时，**断言不转红** ——
> ⇒ 空串在 Boot 的宽松绑定下**没有绑成空列表**，容器仍用 yml 里的 `["[", "]"]`
> ⇒ 换成**错值**才测得出来。
> ⇒ **「配了」与「被采用了」是两件事**（§3.3 的老教训，此处又撞一次）：
> 静态守门脚本（`check-tomcat-limits.py`）查的是**配置文本**，它**证明不了**容器采用了它；
> 只有真 Tomcat 的行为断言 + 变异转红才能。

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

### 🔁 跨域待拍板 · 容器层参数上限的**口径差**（残余窗口，收口在 `global`）

| | 内容 |
|---|---|
| **事实（2026-10-25 真 Tomcat 实测，4 组判别用例）** | Tomcat 的 `maxParameterCount` 数的是**总参数「值」个数（含空值）**；Go 的 `maxFormParamCount` 数的是**非空值个数**（`httpx/util.go:44-45` 的 `if len(v) == 0 { continue }` 空值不计数），且计数器 `n` **跨所有 key 共享** |
| **判别用例（每组都带一个合法非空 `folderId` ⇒ 应用层永不拒、字段必在场 ⇒ 结果只可能来自 Tomcat）** | 总「值」=2048（名字 1，非空 1）⇒ **放行**；总「值」=2049（名字 1，非空 1）⇒ **拒**；总「值」=2049（名字 2，非空 1）⇒ **拒**；总「值」=3000（名字 3000，非空 1）⇒ **拒** ⇒ 判定「**数值不数名**」 |
| **残余窗口（精确）** | **总「值」数 2049~10000 且非空值数 ≤2048** ⇒ Go **放行**（200 + 业务结果）/ RAG **拒**（200 + 5003） |
| **归属** | 🔴 **跨域**，与 hot 域的 `DRIFT-HOTAPI-030` 是**同一格**（hot 域记的正是「总参数数 2049~10000 且非空值 ≤2048」）⇒ **不在 favorite 域单方面改**：改 `server.tomcat.max-parameter-count` 会让**全仓**守门脚本 `tools/difftest/check-tomcat-limits.py` 变红 ⇒ 收口在 `global` |
| **给 `global` 的一条具体输入（本轮新增）** | Go 侧 `net/url` 的**真实**上限是 `defaultMaxParams = 10000`（Go 1.25.11 `src/net/url/url.go:1001`，`urlParamsWithinMax` 判 `params <= defaultMaxParams`）⇒ **对已经逐字复刻了应用层判据的 api 模块**（favorite 有：`FavoriteApiSupport#goFormFirstValue`），把容器层上限配成 **10000** 就能让这条轴**完全 Go 等价**（总「值」数 >10000 时两侧都是 `net/url` 解析失败 ⇒ 5003）；而对**没有**应用层判据的模块，**2048 才是对的**（那 7 个模块的 2048 判据只存在于容器层）⇒ ⇒ **旋钮值应按「有没有应用层判据」分档，而不是全仓一刀切 2048** ⚠️ **这是输入，不是结论** —— 是否采纳由 `global` 拍板 |

> ⚠️ 本域**没有**为此新建 `DRIFT-FAV-API-xxx` 条目：同一格已由 hot 域登记为
> `DRIFT-HOTAPI-030`（AGENTS.md §3.5 把「Tomcat 三旋钮」列为 `global/cross-domain` 的
> 跨组件不变量）⇒ **重复登记会造成两条台账各说一半、合并时对不上**。

---

## 4. 证据边界（明确未验的部分）

| 项 | 状态 | 说明 |
|---|---|---|
| 双侧活体对拍（真 Go favorite-api vs RAG） | 🔴 **未做** | Go 侧依赖 `etcd` + favorite-rpc + Redis，本轮起不来 ⇒ 全部结论是「源码级 + RAG 侧运行证据」 |
| **servlet filter 链真生效** | ✅ **已验**（2026-10-25 第三轮） | `FavoriteApiRealTomcatGateTest`（**8 例**，`webEnvironment = RANDOM_PORT` ⇒ **真 Tomcat**）：MaxBytes → **413 + 空 body + 无 Content-Type**、413 **压过** 401、未超限仍 401、三旋钮各自生效 ⇒ **真容器的 chain 里真的跑了它**（不再只是「Bean 存在 + `@Order` 一致」） |
| **容器层参数上限的精确口径** | ✅ **已验** | 4 组判别用例（真 Tomcat）⇒ Tomcat 数**总「值」数（含空值）**、Go 数**非空值数** ⇒ 残余窗口见 §3 |
| **Go 侧「非空值 >2048」时的逐字节响应** | ✅ **已验**（2026-10-25 第五轮，**双侧活体对拍**） | Go 侧用**真** `httpx.Parse`（`maxFormParamCount=2048` 真生效）⇒ 2049 个非空值 ⇒ **200 + `{code:5003,msg:"参数错误",data:null}`**；RAG 侧走**真 HTTP** ⇒ **逐字节相同** ⇒ 探针 `tools/api-envelope-probe/favorite/`（EXIT=0）⇒ 见 §2.2 第 1 条 |
| 真实 gRPC 链路上的 description 保真 | ✅ **已验**（2026-10-25 第四轮） | `FavoriteGrpcErrorWireFidelityTest`（**4 例**，`favorite-common`，**真 in-process gRPC**）⇒ description 与 `FavoriteErrorMessages.getMsg(码)` **逐字节相同**、经 `bizCodeFromError` **能还原回原业务码**（含唯一只能靠文案反查的 **5304**）、标准 gRPC 码在链路上保真 ⇒ **变异**（去掉 `withDescription`）⇒ **3 例转红** ⇒ 不是假绿 |
| Go 侧 `httpx.Parse` 的入参解析矩阵 | ✅ **已验** | RAG 侧的 DTO 注释记录了 go-zero v1.10.2 真源探针的实测矩阵（缺席/null/类型不符/空串/重复参数/key 大小写），并由 `FavoriteApiInputContractTest`（33 例）守住 |
| `goFormFirstValue` 的两段语义 | ✅ **已验** | 「先滤空值、再取首值」逐条复刻（`FavoriteApiSupport:327-350`），重复参数 `?folderId=123&folderId=456` ⇒ Go 123 / Spring 逗号拼接会失败 —— 两者已刻意分开 |
| `GET /folder/list` 的空 struct body 校验 | ✅ **已验** | Go `httpx.Parse` 无条件跑 `ParseJsonBody` ⇒ 非法 body ⇒ 5003；RAG 用 `assertGoEmptyStructBody` 复刻（`FavoriteController:234-263`） |

---

## 5. 复现命令

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/favorite   # align/favorite 工作树

# 全组件编译 + 打包
mvn -o -pl service/favorite/favorite-proto,service/favorite/favorite-common,\
service/favorite/favorite-rpc,service/favorite/favorite-api -am install

# 全量测试（favorite-api 89 例：33 输入契约 + 36 MVC 管线 + 3 装配 + 4 超时 + 8 真 Tomcat 门禁 + 5 响应字节对拍）
mvn -o -pl service/favorite/favorite-api test

# 单类
mvn -o -pl service/favorite/favorite-api test -Dtest='FavoriteApiProtectionWiringTest'
mvn -o -pl service/favorite/favorite-api test -Dtest='FavoriteRpcClientTimeoutTest'
mvn -o -pl service/favorite/favorite-api test -Dtest='FavoriteApiRealTomcatGateTest'   # 真 Tomcat
mvn -o -pl service/favorite/favorite-api test -Dtest='FavoriteApiEnvelopeParityTest'     # 响应字节对拍

# 🔴 响应字节的双侧活体对拍（Go 真 go-zero 库 vs RAG 真 advice + 真 HTTP）
bash tools/api-envelope-probe/favorite/run.sh
echo $?   # 0 = 10 组 CASE + 1 组 OBS 全部符合冻结期望
```

> ⚠️ `FavoriteApiMvcPipelineTest` 走的是 **MockMvc** ⇒ **不覆盖 servlet filter 链**。
> ⇒ filter 链与容器三旋钮由 `FavoriteApiRealTomcatGateTest`（`RANDOM_PORT`）负责，
> **两者缺一不可**（§3.3：机制要验「生效」，不只是「配了」）。
