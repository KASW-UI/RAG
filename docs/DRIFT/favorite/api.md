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
| 测试 | 2 个测试类 / **69 例全绿**（33 输入契约 + 36 MVC 管线）→ 本轮 **+2 个测试类 / +6 例** |
| 本轮发现 | **1 条 L2 语义漂移（已修）** + **2 条 javadoc 假断言（已修）** + **1 条待签字偏离** |
| 未决 | **1 条待签字**（401 body 形态）+ **1 条 OBS**（500 兜底 body） |
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
| `relaxed-query-chars` | Go 的 `net/http` 不校验 query 里的 `[` `]` | `application.yml:65` | ✅ |
| `max-parameter-count` | `httpx/util.go:15` `maxFormParamCount=2048` | `:66` | ✅ |
| `max-http-request-header-size` | `DefaultMaxHeaderBytes(1<<20) + 4096` = **1028KB** | `:67` | ✅ |
| HTTP 超时 | `rest/config.go:55` `Timeout default=3000` | `observability.http-timeout: 3s` | ✅ |
| **MaxConns / MaxBytes / CPU 降载 / 熔断（服务端）** | `rest/config.go:52-56` + `zrpc/internal/config.go:14-15` | `GoStyleProtectionProperties` 默认值 = Go 默认值 | ✅ **运行证据**（`FavoriteApiProtectionWiringTest`） |
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

---

## 3. ⚠️ 未决 / 待签字

### DEV-FAV-API-001（**待签字**）· 401 响应的 body 形态

| | Go | RAG |
|---|---|---|
| 验签失败 | `authhandler.go:106-122` ⇒ **401 + 空 body** | 401 + `{"code":5004,"msg":"未登录或登录已失效","data":null}` |
| 黑名单命中 | `checkblacklist_middleware.go:39-45` ⇒ 401 + **两字段**（无 `data`） | 同上（三字段） |

⇒ **HTTP 状态码 401 与 code 5004 都对齐**，差的是 body 的字段集合 ⇒ 表达层差异。
⇒ 登记在 [`../../Intentional_Deviations/favorite/api.md`](../../Intentional_Deviations/favorite/api.md)，**待签字**。

> ⚠️ 此前两个类（`GlobalExceptionHandler` / `FavoriteJwtAuthInterceptor`）都把这称为
> 「属 AGENTS.md **§3.4** 已登记的表达层差异」—— **§3.4 是 proto 复制那一节**，与本条无关 ⇒ 已改正。

### OBS · 500 兜底的 body 形态

Go 的 `RecoverHandler` 写 **500 + text/plain**（`http.Error`）；RAG 的 `handleAny:243-252` 写
**500 + JSON envelope**（`{code:5001,…}`）。
⇒ favorite 的 Go logic **不会 panic** ⇒ 该分支实际不可达 ⇒ 记 **OBS**，不构成行为漂移。

---

## 4. 证据边界（明确未验的部分）

| 项 | 状态 | 说明 |
|---|---|---|
| 双侧活体对拍（真 Go favorite-api vs RAG） | 🔴 **未做** | Go 侧依赖 `etcd` + favorite-rpc + Redis，本轮起不来 ⇒ 全部结论是「源码级 + RAG 侧运行证据」 |
| **servlet filter 链真生效** | 🔴 **未验** | `FavoriteApiProtectionWiringTest` 断言的是「Bean 存在 + `@Order` 一致」，**不是**「真 Tomcat 的 chain 里跑了它」—— MockMvc **不走** filter chain（§3.3 记录过的坑，`check-plaintext400-headers.sh` 就是为它而生的）⇒ 需真容器验证 |
| 真实 gRPC 链路上的 description 保真 | 🔴 **未验** | common 侧探针验的是「构造出的 Status 对象」，不是「真 channel 传输后的字节」 |
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

# 全量测试（favorite-api 75 例：33 输入契约 + 36 MVC 管线 + 3 装配 + 4 超时）
mvn -o -pl service/favorite/favorite-api test

# 单类
mvn -o -pl service/favorite/favorite-api test -Dtest='FavoriteApiProtectionWiringTest'
mvn -o -pl service/favorite/favorite-api test -Dtest='FavoriteRpcClientTimeoutTest'
```

> ⚠️ `FavoriteApiMvcPipelineTest` 走的是 **MockMvc** ⇒ **不覆盖 servlet filter 链**（见 §4）。
