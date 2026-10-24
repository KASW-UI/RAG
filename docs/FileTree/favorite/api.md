# favorite-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/favorite/api`（**22 个非测试文件**，本文件全覆盖）
> 目标根包：`com.rag.favorite.favorite.api`
> 推导日期：2026-10-23
>
> **本文件是「理论推导」，不是实施记录。** `favorite-api` 至今是**空壳**：
> `service/favorite/favorite-api/pom.xml` 只有 `<artifactId>` 与 `<properties>`，**零 `<dependencies>`**，
> `src/` 下 **0 个 `.java` 文件**。本文所有 Java 文件均为待建，不存在「已有」可言。
>
> **上游先例**：`docs/FileTree/like/api.md`（同为 like-api 空壳期写的推导文档）、`docs/FileTree/hot/api.md`。
> 格式母版的七段式编号与本文件一致。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

| RAG 位置 | 实测状态（2026-10-23） |
|---|---|
| `service/favorite/favorite-api/pom.xml:12-18` | 存在，**零生产依赖**、零测试依赖、零 build 插件 |
| `service/favorite/favorite-api/src/` | **不存在**（`find service/favorite -type f` 只有 5 个 pom + 1 个 `.proto`） |
| `service/favorite/favorite-proto/src/main/proto/favorite.proto` | ✅ 已落地。**与 Go `proto/favorite.proto` 逐行 diff 只差 option 块**（实测 `diff` 输出仅 4→5 行一处：`go_package = "."` 换成 `java_package = "com.rag.favorite.favorite.proto"` + `java_multiple_files = true`）。`package favorite` 两侧逐字一致 ⇒ **gRPC 方法路径 `/favorite.FavoriteService/*` 与 Go 完全相同，零 wire 偏离** |
| `service/favorite/pom.xml:14-19` | 已声明 4 个 `<module>`：`favorite-proto` / `favorite-api` / `favorite-rpc` / `favorite-common` ⇒ **无需新建模块** |

> ⚠️ 与 `comment` / `like` 域的差异：那两个域的 proto `package` 与 Go 不同，已留 `SD-2026-10-23-04/05` 偏离条目。**`favorite` 域没有这类偏离** —— 本文件不写、也不引用任何 proto 偏离说明。

包结构依据来自本仓已落地的 3 个 api 模块（它们包名集合完全一致，可视为已定型）：

| 参照模块 | 提供的一致性依据 |
|---|---|
| `service/users/user/user-api` | `UserApiApplication.java:21-27`（`scanBasePackages={com.rag.users.user.api, com.rag.common}` + `@EnableConfigurationProperties` + `@EnableDiscoveryClient`）；`controller/UserController.java:99-100`（**单** Controller + `@RequestMapping("/user/v1")`）；`interceptor/JwtAuthInterceptor.java:59-209`；`advice/GlobalResponseAdvice.java:41-75`；`advice/GlobalExceptionHandler.java:42-296`；`config/UserApiProperties.java:18-33`；`config/WebMvcConfig.java:25-59`；`client/UserRpcClient.java:20-29`；`metrics/UserApiMetrics.java:30-`；`service/IdentityResolver.java:94-101`（从 request attribute `"userId"` 取 UID） |
| `service/users/admin/admin-api` | 同款结构的第二份平行证明（`interceptor/AdminJwtAuthInterceptor`） |
| `service/message/message-api` | 第三份平行证明；`dto/` 内既有 `PageReq` 也有 `EmptyReq` |
| `service/article/article-api` | 第四份；`service/` 形态为 **1 个 `XxxApiSupport` + 每端点 1 个 Service** |

**包名集合已定型**：`controller/`、`dto/`、`service/`、`client/`、`config/`、`advice/`、`interceptor/`、`metrics/`。
**本文件额外提出 0 个新包**，并且**明确不建 `metrics/`**（理由见 §12.5）。

### 已有组件（跨模块可复用，均已打开核对）

| 能力 | RAG 组件（`文件:行号`） | 说明 |
|---|---|---|
| 响应信封 | `service/common/.../response/Response.java:14-18` | `{int code; String msg; Object data;}`，字段名与 Go `service/common/response/response.go:3-7` 逐字一致 |
| 业务异常 | `service/common/.../exception/BusinessException.java:26-38` | unchecked + `(int code, String msg)` |
| 鉴权失败异常 | `service/common/.../exception/AuthenticationException.java:47-52` | 语义 = **HTTP 401**（继承 `BusinessException`） |
| HTTP span / 慢调用 | `service/common/.../spring/HttpMiddlewareFilter.java` | `@Component`，靠 `scanBasePackages` 含 `com.rag.common` 才注册（`UserApiApplication.java:21-24` 已如此） |
| JWT 解析原语 | `service/users/users-common/.../jwt/JwtUtil.java` | `padToHmacKeySize` + HS256 |
| Bearer 取 token | `service/users/users-common/.../jwt/BearerTokenExtractor.java:32-60` | **两个方法**：`tokenForSignatureVerification`（验签）/ `trimBearerPrefix`（黑名单 key），刻意不合并 |
| go-zero `,string` int64 解析 | `service/users/users-common/.../http/GoStyleStringInt64.java:69-130` | 逐条等价于 Go 的 `,string`：字符串与 JSON 数字**都接受**，浮点字面量一律拒 |
| go-zero 标量强转对齐 | `service/users/users-common/.../http/GoStyleCoercion.java` | 由 `WebMvcConfig.goStyleCoercionCustomizer()`（`WebMvcConfig.java:46-49`）挂到 MVC 的 mapper 上 |
| 业务日志 | `service/common/.../logger/{Logger,LogOption,WithUserID,WithArticleID}.java` | `WithUserID.of(...)` / `WithArticleID.of(...)` |
| gRPC 下游客户端 | `service/users/user/user-api/.../client/UserRpcClient.java:20-29` | `@GrpcClient` 字段注入形态；`message-rpc/client/UserRpcClient.java:21-30` 提供构造器注入形态 |
| 错误码 → 业务码反查 | `service/message/message-common/.../errmsg/MessageGrpcError.java:195`（`bizCodeFromError`）/ `:135`（`grpcError(Status.Code,int)`） | 见 §12.4 的落点决定 |

### 已确认的框架机制

- `@RestControllerAdvice` + `ResponseBodyAdvice`（`GlobalResponseAdvice.java:42,50-75`）
- `@RestControllerAdvice` + `@ExceptionHandler`（`GlobalExceptionHandler.java:42-296`）
- `HandlerInterceptor` + `WebMvcConfigurer`（`WebMvcConfig.java:25-26,55-59`）
- `@ConfigurationProperties` + `@NestedConfigurationProperty`（`UserApiProperties.java:18,23-24,31-33`）
- `net.devh:grpc-client-spring-boot-starter` 的 `@GrpcClient`（`UserRpcClient.java:23-24`）
- Spring Data Redis `StringRedisTemplate.hasKey`（`JwtAuthInterceptor.java:203`）
- Micrometer `MeterRegistry`；Nacos 服务发现
- Spring Boot **4.0.8**（`pom.xml:9`）/ Java **21**

### 当前 Component 可以直接复用的能力

| 能力 | 依据 | 本模块用在哪 |
|---|---|---|
| `Response` 信封 | `common/response/Response.java` | 全部 7 个端点 |
| `BusinessException(int,String)` | `common/exception/BusinessException.java:30-34` | uid 解析失败、rpc 错误码透出 |
| `AuthenticationException` | `common/exception/AuthenticationException.java:49-51` | JWT 验签失败 → HTTP 401 |
| HTTP span / 慢调用 | `common/spring/HttpMiddlewareFilter`（`@Component` 全局） | 7 个端点，零新增代码 |
| `@GrpcClient` 服务发现 | `users/user/user-api/client/UserRpcClient.java:23` | `favorite-rpc` 唯一下游 |
| Redis blacklist 查询 | `JwtAuthInterceptor.java:199-209`（fail-open 语义逐条对齐） | `interceptor/FavoriteJwtAuthInterceptor` |
| `GoStyleStringInt64` / `BearerTokenExtractor` / `JwtUtil` | 上表三行 | DTO 的 `,string` 解析、Bearer 提取、HMAC key padding |

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **路由前缀** | ✅ **无需偏离**。Go 为 `rest.WithPrefix("/favorite/v1")`（`routes.go:58`），与 RAG users 的 `/user/v1`、`/admin/v1` 惯例一致；7 条路由**全部** `WithJwt`（`routes.go:57`）⇒ **public 路径集为空集** |
| **HTTP 方法非全 POST** | ⚠️ 与 `like-api`（6 条全 POST，`like/api.md` §12.1）**不同**：favorite 有 2 条 `DELETE` + 2 条 `GET`（`routes.go:26,31,46,51`）。`@DeleteMapping` / `@GetMapping` 是 Spring 的一等公民，直接映射即可，**无契约风险** |
| **解析失败的 HTTP 状态码** | 🔴 **本文件最硬的差异点**。favorite 的 7 个 handler **自己吞掉** `httpx.Parse` 的错误并写 `httpx.OkJsonCtx(... code=5003 ...)`（如 `create_favorite_folder_handler.go:18-25`）⇒ **HTTP 200 + `{code:5003,msg:"参数错误",data:null}`**。而 `user-api` 的 `GlobalExceptionHandler.java:163-167` 把 `HttpMessageNotReadableException` 处理成 **HTTP 400 + text/plain**。**照抄 user-api 的 advice 会引入一处可观察差异**，详见 §12.7 不通过项 #1 |
| **`AccessExpire` 是死配置** | Go 侧 `routes.go:57` 的 `rest.WithJwt(secret)` **只吃 secret**，全仓无任何读取 `AccessExpire` 的点 ⇒ `favorite.yaml:12` 的 `2592000` 对 api 侧无效（只有签发方 `user-rpc` 需要它）。RAG 侧**照抄即可但不得据此加 TTL 逻辑** |
| **`DevServer` 段** | `favorite.yaml:25-32` 有 16063 端口的 `/metrics` + `/healthz` + pprof，但 `internal/config/config.go:9-17` **没有对应字段**。go-zero `conf.MustLoad` 对未知键的严格程度取决于 go-zero 自身实现（不在本仓库）⇒ `UNKNOWN`。RAG 侧后果明确：**由 Actuator 端点覆盖**（先例 `like-rpc/application.yml:197-206`） |
| **Redis 只服务 blacklist** | `service_context.go:20` 的 `redisDB` **唯一**消费者是 `middleware.NewCheckBlacklistMiddleware`（`:25`）。api 侧**无任何业务 Redis 读写** ⇒ pom 不需要 `commons-pool2`（对比 `like-rpc/pom.xml:124-127` 的教训） |
| **api 侧无指标** | Go `service/favorite/api/` 下**没有** `internal/metrics/`（实测目录清单）。⇒ **不建 `metrics/` 包**，见 §12.5 |
| **JWT 与 user-api 同一密钥域** | `favorite.yaml:11` 的 `AccessSecret` 与 `user/user/api/etc/usercenter.yaml:11` **逐字相同** ⇒ favorite-api 验的就是 user-api 签发的那把钥匙。⇒ `users-common/jwt/*` **不是"相似先例"，是同一密钥域的同一实现**，REUSE 无争议 |

---

## 12.2 第二部分：Go 原始文件树

```
service/favorite/api/                                    （22 个非测试文件）
├── favorite.go                                    38 行  进程装配
├── etc/favorite.yaml                              38 行  Port 8890 / UserAuth / BizRedis / Telemetry / DevServer 16063 / FavoriteRpc
└── internal/
    ├── config/config.go                           17 行  Config{RestConf, UserAuth, FavoriteRpc, BizRedis}
    ├── svc/service_context.go                     27 行  Redis + FavoriteRpc + CheckBlacklistMiddleware
    ├── handler/
    │   ├── routes.go                              60 行  7 条路由 + WithMiddlewares + WithJwt + WithPrefix
    │   └── favorite/*.go                     7×35 行   7 个逐字同质的薄 handler
    ├── logic/favorite/
    │   ├── helper.go                              46 行  extractUserID / userLogOption / articleLogOption / codeFromRPCError
    │   ├── create_favorite_folder_logic.go        66 行
    │   ├── create_favorite_logic.go               73 行
    │   ├── delete_favorite_folder_logic.go        66 行
    │   ├── delete_favorite_logic.go               66 行
    │   ├── get_favorite_folders_logic.go          70 行
    │   ├── get_favorites_by_folder_logic.go       79 行
    │   └── update_favorite_folder_name_logic.go   68 行
    ├── middleware/
    │   └── checkblacklist_middleware.go           51 行  ★ favorite 域**独有**（comment/follow 无此目录）
    └── types/types.go                             81 行  9 组 DTO + 2 个共享 item
```

**依赖文件（同域，跨组件共用）**：`service/favorite/common/errmsg.go`（138 行）—— 完整分析见 `FileTree/favorite/common.md`，本文件只引用其对外可达部分。

---

## 12.3 第三部分：Go 文件职责分析

### `favorite.go`（进程装配）

- `:25` `logx.MustSetup(c.Log)`；`:26` `logger.Init(c.Name)`
- `:28` `observability.DisableNativeRestTimeout(&c.RestConf)` —— 主动关掉 go-zero 原生 REST 超时
- `:29` `rest.MustNewServer(c.RestConf)`；`:30` `server.Use(observability.NewHTTPMiddleware(...))`
- `:33-34` `svc.NewServiceContext(c)` + `handler.RegisterHandlers(server, ctx)`

🔴 **本文件没有 `httpx.SetOkHandler` / `httpx.SetErrorHandler`**（与 `like/api/likecenter.go:41-61`、`users/user/api/usercenter.go` 不同）。
⇒ 响应信封**逐个 handler 手写**：`httpx.OkJsonCtx(w, r, &response.Response{Code: code, Msg: GetErrMsg(code), Data: resp})`（`create_favorite_folder_handler.go:29-33`）。
⇒ **任何解析/路由/框架级错误都落到 go-zero 默认的 `httpx.Error` 语义**，而不是自定义信封。

- **L1 冻结**：7 个端点的业务响应 HTTP 状态码**恒 200**；失败只体现在 body 的 `code`。
- **L4**：Spring 侧 = `advice/GlobalResponseAdvice`（成功包信封）+ `advice/GlobalExceptionHandler`（失败直接产出 `Response`），且必须**只包一层**（`GlobalResponseAdvice.java:57-59` 的 `body == null || body instanceof Response` 放行）。

### `etc/favorite.yaml`

`:3` `Port: 8890`；`:10-12` `UserAuth{AccessSecret: ae0536f9-…-5a19ed505da0, AccessExpire: 2592000}`；
`:14-17` `BizRedis{Host: redis:6379, Type: node, Pass: ""}`；`:19-23` `Telemetry{Name: favorite-api}`；
`:25-32` `DevServer{Enabled: true, Port: 16063, MetricsPath: /metrics, HealthPath: /healthz, EnablePprof: true}`；
`:34-38` `FavoriteRpc.Etcd.Key: favorite.rpc`。

### `internal/config/config.go`

`Config = rest.RestConf + UserAuth + FavoriteRpc zrpc.RpcClientConf + BizRedis redis.RedisConf`（`:9-17`）。
**没有** Telemetry / DevServer / Log 字段 —— 它们靠 `rest.RestConf` 内嵌的 `service.ServiceConf` 承载或被忽略（`UNKNOWN`，见 §12.1）。

### `internal/svc/service_context.go`

`NewServiceContext` 三件事：`redis.MustNewRedis(c.BizRedis)`（`:20`）、`favoriteservice.NewFavoriteService(zrpc.MustNewClient(c.FavoriteRpc))`（`:24`）、`middleware.NewCheckBlacklistMiddleware(redisDB).Handle`（`:25`）。
**无 DB、无 Kafka、无 metrics** ⇒ favorite-api 是**纯转发网关 + 一个 blacklist 中间件**。

### `internal/handler/routes.go`

一个 `AddRoutes` 块，7 条路由，外层 `rest.WithMiddlewares([]rest.Middleware{serverCtx.CheckBlacklistMiddleware}, routes...)`（`:17-18`），装饰器 `rest.WithJwt(secret)`（`:57`）+ `rest.WithPrefix("/favorite/v1")`（`:58`）。

| Method | Path | 行号 |
|---|---|---|
| POST | `/folder/create` | `:21-22` |
| DELETE | `/folder/delete` | `:26-27` |
| GET | `/folder/list` | `:31-32` |
| POST | `/folder/update` | `:36-37` |
| POST | `/item/create` | `:41-42` |
| DELETE | `/item/delete` | `:46-47` |
| GET | `/item/list` | `:51-52` |

- **L1**：无 public 路由；无 `WithTimeout`；无 maxBytes；**无 CORS 配置**。
- **L3 顺序**：Go 的 `rest.WithJwt` 在 middleware chain 的**外层**（索引 0 先执行），`CheckBlacklistMiddleware` 被 `WithMiddleware` 包进 `route.Handler`（`ThenFunc` 的参数，比 chain 任何元素都内层）。
  ⇒ **JWT 验签先于 blacklist 查询**；验签失败时 blacklist **从不被查**，也不为畸形 token 产生任何 Redis 读。
  证据与 RAG 侧对齐先例：`users-common/jwt/BearerTokenExtractor.java:26-31` 的类注释、`JwtAuthInterceptor.java:170-174`。

### `internal/middleware/checkblacklist_middleware.go`（favorite 域独有，逐段）

| 行号 | 行为 |
|---|---|
| `:24-28` | `Authorization` 头为空 ⇒ **直接 `next`**（不写 `jwt_token`） |
| `:30` | `token = strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))` —— **TrimSpace + 大小写敏感 TrimPrefix** |
| `:31-34` | token 为空 ⇒ **直接 `next`**（不写 `jwt_token`） |
| `:36` | key = `fmt.Sprintf("user:jwt_blacklist:%s", token)` |
| `:37` | `exists, err := m.Redis.ExistsCtx(ctx, key)` |
| `:38-46` | `err == nil && exists` ⇒ `Content-Type: application/json;charset=utf-8` + **HTTP 401** + body `{"code":5004,"msg":"未登录或登录已失效"}` |
| `:48-49` | 否则 `ctx["jwt_token"] = token` 后 `next` |

- **L2 关键语义（极易被"修好"）**：① 缺头/空 token 是 **fail-open**；② Redis 故障（`err != nil`）也是 **fail-open**（条件为 false）；③ 401 的 body **只有 `code` 与 `msg` 两个字段，没有 `data`**；④ 用的码是 **favorite 自己的 `ErrorUnauthorized = 5004`**，不是 users 侧的 401。
  对照：`AuthenticationException.java:24-39` 的表格记录了 users 侧两处 401 的 body 形状差异 —— favorite 是**第三种形状**，必须单独记。
- **L3**：本中间件只写 `jwt_token`，**不写 `userId`**。`userId` 由 `rest.WithJwt` 写。两者互不覆盖。

### `internal/handler/favorite/*.go`（7 个，逐字同质）

以 `create_favorite_folder_handler.go` 为例，35 行：

| 行号 | 内容 |
|---|---|
| `:17-18` | `var req types.XxxReq` + `httpx.Parse(r, &req)` |
| `:18-25` | 解析失败 ⇒ `httpx.OkJsonCtx(... &response.Response{Code: ErrorInvalidParam, Msg: GetErrMsg(ErrorInvalidParam), Data: nil})` ⇒ **HTTP 200 + code 5003** |
| `:27-28` | `NewXxxLogic(...)` + `resp, code := l.Xxx(&req)` |
| `:29-33` | `httpx.OkJsonCtx(... &response.Response{Code: code, Msg: GetErrMsg(code), Data: resp})` |

**7 个 handler 之间逐字零差异**（只有类型名与方法名不同）。逐个登记，确保无一遗漏：

| handler 文件 | 行数 | 对应 logic | 响应 DTO |
|---|---|---|---|
| `create_favorite_folder_handler.go` | 35 | `CreateFavoriteFolderLogic` | `CreateFavoriteFolderResp` |
| `create_favorite_handler.go` | 35 | `CreateFavoriteLogic` | `CreateFavoriteResp` |
| `delete_favorite_folder_handler.go` | 35 | `DeleteFavoriteFolderLogic` | `DeleteFavoriteFolderResp` |
| `delete_favorite_handler.go` | 35 | `DeleteFavoriteLogic` | `DeleteFavoriteResp` |
| `get_favorite_folders_handler.go` | 35 | `GetFavoriteFoldersLogic` | `GetFavoriteFoldersResp` |
| `get_favorites_by_folder_handler.go` | 35 | `GetFavoritesByFolderLogic` | `GetFavoritesByFolderResp` |
| `update_favorite_folder_name_handler.go` | 35 | `UpdateFavoriteFolderNameLogic` | `UpdateFavoriteFolderNameResp` |

7 个文件的 `:18-25`（解析失败 ⇒ `5003`）与 `:29-33`（成功信封）行号完全一致。
🔴 `:18-25` 这一段是 **favorite 与 `user-api` / `like-api` 的语义分水岭**：后两者的 handler 用 `httpx.ErrorCtx`，落到 go-zero 默认的 **HTTP 400 + text/plain**；favorite 自己吞掉并回 **HTTP 200 + 业务码**。

### `internal/logic/favorite/helper.go`

- `extractUserID(ctx)`（`:14-34`）：从 `ctx.Value("userId")` 取值，**5 个类型分支** —— `json.Number` → `Int64()`；`string` → `strconv.ParseInt`；`int64` / `int` / `float64` → 直接转换；`default` → 报错。
  `nil` 单独报错（`:16-18`）。⚠️ **`json.Number` 分支是主路径**：go-zero `rest.WithJwt` 写入 ctx 的就是 `json.Number`。
- `userLogOption`（`:36-38`）/ `articleLogOption`（`:40-42`）：`strings.TrimSpace` 后包成日志选项。
- `codeFromRPCError`（`:44-46`）：`return favoritecommon.BizCodeFromError(err)` —— **唯一的 rpc 错误码入口**。

### 7 个 logic 的共同骨架与差异

共同骨架（以 `create_favorite_folder_logic.go` 为例）：起 span `FavoriteAPI.Xxx`（`:33-34`）→ `extractUserID` 失败即 `ErrorUnauthorized(5004)`（`:36-40`）→ 写 span 属性 → 调 rpc（`:47-56`）→ nil 守卫 → 成功日志 → 返回 `(resp, Success=200)`。

| logic | rpc 调用 | 失败守卫 | 响应投影 | 行号 |
|---|---|---|---|---|
| `create_favorite_folder_logic.go` | `CreateFavoriteFolder(userId,name)` | `rpcResp == nil` ⇒ 5001 | `{FolderId: rpcResp.FolderId}` | `:47-65` |
| `create_favorite_logic.go` | `CreateFavorite(userId,folderId,targetId,targetType,title,cover)` | `rpcResp == nil` ⇒ 5001 | `{FavoriteId: rpcResp.FavoriteId}` | `:50-72` |
| `delete_favorite_folder_logic.go` | `DeleteFavoriteFolder` | `rpcResp == nil \|\| !rpcResp.Success` ⇒ 5001 | `{Success: true}` **硬编码** | `:47-65` |
| `delete_favorite_logic.go` | `DeleteFavorite` | `rpcResp == nil \|\| !rpcResp.Success` ⇒ 5001 | `{Success: true}` **硬编码** | `:47-65` |
| `update_favorite_folder_name_logic.go` | `UpdateFavoriteFolderName` | `rpcResp == nil \|\| !rpcResp.Success` ⇒ 5001 | `{Success: true}` **硬编码** | `:48-67` |
| `get_favorite_folders_logic.go` | `GetFavoriteFolders(userId)` | `rpcResp == nil` ⇒ 5001 | 逐项投影 `FolderId/Name/CreatedAt/UpdatedAt` | `:44-69` |
| `get_favorites_by_folder_logic.go` | `GetFavoritesByFolder(userId,folderId)` | `rpcResp == nil` ⇒ 5001 | 逐项投影 7 字段 | `:47-78` |

必须 1:1 保留的 5 条 L2/L3 语义：

1. **`Success: true` 硬编码**（`delete_favorite_folder_logic.go:65`、`delete_favorite_logic.go:65`、`update_favorite_folder_name_logic.go:67`）—— 响应里的 `success` **永远是 true**，失败路径在 `err != nil` 分支就 return 了，压根到不了这里。Java 侧**绝不能**把 `success` 改成"业务结果标志"（AGENTS.md §1.1 列的 users 侧 `success` 语义反转回归的同型陷阱）。
2. **`!rpcResp.Success` 守卫只存在于 3 个写操作**，两个 create 与两个 list **只有 `nil` 守卫**。这是显式的鉴权/校验不对称，不对称本身是语义。
3. **`rpcResp == nil` ⇒ `ErrorServerCommon(5001)`**，不是把下游错误透传。7 个 logic 全同。
4. **proto → DTO 的投影是白名单**：`favoritepb.FavoriteFolder` 有 5 个字段（含 `user_id`），HTTP DTO `types.FavoriteFolder` **只有 4 个，丢弃 `user_id`**（`types.go:42-47` vs `proto/favorite.proto:6-12`）。`FavoriteItem` 同理：proto 8 字段 → HTTP 7 字段，丢弃 `user_id`（`types.go:49-57`）。
   ⇒ **`user_id` 永不出 api 边界**，这是可观察的字段缺失，必须在 Java DTO 里同样不出现。
5. **`title` / `cover` 直传，api 侧不规范化**。`create_favorite_logic.go:55-56` 把 `req.Title` / `req.Cover` **原样**传给 rpc（注意 span 属性用的是 `strings.TrimSpace(req.TargetId)`（`:46`），但**传给 rpc 的是未 Trim 的 `req.TargetId`**（`:53`）—— 日志与实参不一致是 Go 事实，不要顺手"统一"）。

### `internal/types/types.go`

9 组 Req/Resp + 2 个共享 item。逐条关键点：

| 字段 | tag | 行号 | 影响 |
|---|---|---|---|
| `CreateFavoriteFolderResp.FolderId` | `json:"folderId,string"` | `:11` | **响应里 int64 以 JSON 字符串输出** |
| `CreateFavoriteResp.FavoriteId` | `json:"favoriteId,string"` | `:23` | 同上 |
| `DeleteFavoriteFolderReq.FolderId` | `json:"folderId,string"` | `:27` | **入参**：字符串与 JSON 数字都接受 |
| `DeleteFavoriteReq.FavoriteId` | `json:"favoriteId,string"` | `:35` | 同上 |
| `CreateFavoriteReq.FolderId` | `json:"folderId,string"` | `:15` | 同上 |
| `CreateFavoriteReq.Title/Cover` | `json:"title,optional"` / `"cover,optional"` | `:18-19` | goctl `optional` = 非必填 |
| `GetFavoritesByFolderReq.FolderId` | **`form:"folderId,string"`** | `:67` | ⚠️ **form 而非 json** —— 走 query string |
| `FavoriteFolder.FolderId` / `FavoriteItem.FavoriteId` / `FavoriteItem.FolderId` | `,string` | `:43,50-51` | 响应里是字符串 |
| `FavoriteFolder.CreatedAt/UpdatedAt`、`FavoriteItem.CreatedAt` | 裸 `int64` | `:45-46,56` | **不带 `,string`** —— 输出 JSON 数字 |
| `GetFavoriteFoldersReq` | 空 struct | `:59-60` | 零字段 |

- **零个 `validate:` tag、零个 `Validate()` 方法**，`favorite.go` 也**从未**调 `httpx.SetValidator` ⇒ go-zero 自己的 validation 框架不介入，只剩 mapping 的 tag 必填与 `,string` 解析。
  证据先例：`users/user/user-api/controller/UserController.java:74-77` 的三段取证（同一 goctl 生成规则）。
- **没有分页**：`GetFavoriteFoldersResp` / `GetFavoritesByFolderResp` 都是全量数组，无 `page`/`pageSize` 字段 ⇒ RAG 侧**不需要** `GoStylePagination`，也**不需要**任何分页拦截器。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `favorite.go` 进程装配 | `FavoriteApiApplication` | ABSORB | Spring Boot 启动（`UserApiApplication.java:21-27` 同款） |
| `favorite.go:28` 禁用原生 REST 超时 | 不做（框架无对应默认值） | ABSORB | Spring MVC 无等价默认超时，语义自动满足 |
| `favorite.go:30` HTTP span | `common/spring/HttpMiddlewareFilter` | REUSE | 已 `@Component`，靠 `scanBasePackages` 含 `com.rag.common` 注册 |
| 无 `httpx.SetOkHandler`（`:1-38` 全文） | `advice/GlobalResponseAdvice` | ADAPT | 语义等价：Go 侧成功也写 `Msg: GetErrMsg(200) == "OK"`（`errmsg.go:44`），与 advice 的 `Response(200,"OK",body)`（`GlobalResponseAdvice.java:74`）逐字相同 |
| 无 `httpx.SetErrorHandler` + 7 个 handler 手写信封 | `advice/GlobalExceptionHandler` | ADAPT | `BusinessException(code,msg)` → `Response(code,msg,null)`，HTTP 200（`GlobalExceptionHandler.java:49-56,294-296`） |
| 🔴 7 个 handler `:18-25` 的解析失败分支 | `GlobalExceptionHandler.handleUnreadableBody` **必须覆写** | ADAPT | Go 返回 **HTTP 200 + code 5003**；user-api 的同款 handler 返回 **400 text/plain**（`GlobalExceptionHandler.java:163-167`）⇒ 不能照抄，见 §12.7 #1 |
| 7 个 handler `:27-33` 的成功/业务失败信封 | `GlobalResponseAdvice` + `BusinessException` | ADAPT | 业务失败由 Service 抛 `BusinessException(code, FavoriteErrorMessages.getMsg(code))` |
| `config/config.go:11-14` + `favorite.yaml:10-12` UserAuth | `config/FavoriteApiProperties` | ADAPT | `@ConfigurationProperties("favorite-api")` + `@NestedConfigurationProperty`（`UserApiProperties.java:18,23-24`） |
| `favorite.yaml:12` AccessExpire | **不映射** | — | api 侧不签发 token，`rest.WithJwt` 只吃 secret（`routes.go:57`）；映射了会误导人以为本模块管 TTL |
| `favorite.yaml:3` Port 8890 | `application.yml` `server.port` | ADAPT | 配置替代 |
| `favorite.yaml:14-17` BizRedis | `application.yml` `spring.data.redis.*` | ADAPT | `Type: node` 是单节点，Lettuce 默认单节点，**不要配 cluster**（先例 `like-rpc/application.yml:63-81`） |
| `favorite.yaml:25-32` DevServer 16063 | `management.endpoints.*`（Actuator） | ADAPT | Go 侧 `Config` 无该字段（`config.go:9-17`），16063 无 Spring 对应物；`UNKNOWN` 见 §12.7 |
| `svc/service_context.go:24` FavoriteRpc | `client/FavoriteRpcClient` | ADAPT | `@GrpcClient("favorite-rpc")`（`UserRpcClient.java:23-24`） |
| `svc/service_context.go:20,25` Redis + blacklist | `interceptor/FavoriteJwtAuthInterceptor` 注入 `StringRedisTemplate` | ADAPT | 依赖注入替代显式构造 |
| `svc/service_context.go` 整体（持有 Config + 两个依赖） | Spring Bean 图 | ABSORB | `@ConfigurationProperties` + 构造器注入（`message-rpc/client/UserRpcClient.java:28` 是 rpc 侧构造器注入先例，api 侧用字段注入 `UserRpcClient.java:23-24`） |
| `routes.go:58` `WithPrefix("/favorite/v1")` | `@RequestMapping("/favorite/v1")` | ADAPT | **Go 契约本就与 RAG 惯例一致，无偏离** |
| `routes.go:57` `WithJwt`（7/7 全覆盖） | `interceptor/FavoriteJwtAuthInterceptor` + `config/WebMvcConfig` | ADAPT | **public 集为空集**；`addPathPatterns("/favorite/v1/**")`（`WebMvcConfig.java:57-58`） |
| `routes.go:17-18` `WithMiddlewares([CheckBlacklist])` | **并入同一个** `FavoriteJwtAuthInterceptor.preHandle` 的第二阶段 | MERGE | Go 的两个中间件都在鉴权链上、共享同一 header 与同一 token；拆两个 Spring Bean 只会让"谁先执行"变成配置问题。`JwtAuthInterceptor.java:107-189` 已是同款合并形态（阶段 1 验签 → 阶段 2 黑名单） |
| `routes.go` 无 `WithTimeout` / 无 maxBytes / 无 CORS | 不配置 | ABSORB | Go 无此装饰器；favorite 也没有文件上传端点 |
| 7 个 handler | `controller/FavoriteController`（**单一**） | MERGE | 逐字同质（各 35 行，只有类型名不同）；且全仓 4 个 api 模块都是单 Controller（`UserController.java:99-100`、`ArticleController`） |
| `types.go` 5 组 JSON Req | `dto/*Req` | SPLIT | 请求契约独立；`folderId,string` 等必须用 `GoStyleStringInt64` 接管（`GoStyleStringInt64.java:83-125`） |
| `types.go:59-60` `GetFavoriteFoldersReq{}`（空） | **不建** | — | 零字段 ⇒ `@GetMapping("/folder/list")` 无参可取。建一个空 DTO 就是"为对应 Go 文件而建文件"（§14.1） |
| `types.go:66-68` `GetFavoritesByFolderReq`（**form** tag） | **不建**，Controller 直接 `@RequestParam` | — | 唯一字段来自 query string，`GoStyleStringInt64.parse(String, "folderId")` 即等价（`GoStyleStringInt64.java:87-96`）；1 字段的 holder 类不构成独立职责 |
| `types.go` 4 组 Resp + `FavoriteFolder` + `FavoriteItem` | `dto/*Resp` + `dto/{FavoriteFolder,FavoriteItem}` | SPLIT | 响应契约与 `,string` 序列化独立于请求；2 个 item 跨 Resp 复用，单列 |
| 5 个 logic 的 `extractUserID` 模板 | `service/FavoriteApiSupport.extractUserId` | MERGE | 7 个 logic 逐字同构（`helper.go:14-34`）；与 `IdentityResolver.claimedUID`（`:94-101`）同定位 |
| `helper.go:36-42` 日志选项 | `common/logger/{WithUserID,WithArticleID}` | REUSE | 逐字对齐；`WithArticleID` 只在 `create_favorite_logic.go:61,67,71` 出现 |
| `helper.go:44-46` `codeFromRPCError` | `service/FavoriteApiSupport.bizCodeFrom` → `favorite-common` 的 `FavoriteGrpcError.bizCodeFromError` | REUSE | 与 `MessageGrpcError.java:195` 同款。⚠️ **落点选在 api 层而非 advice**：Go 侧每个 logic **自己 catch、自己打业务日志**，再把码作为返回值传出；错误**从不**以 `StatusRuntimeException` 形态逃到 handler 层 ⇒ advice 里不需要 `handleRPC` |
| 5 个 logic 的 `codeFromRPCError` 调用点 | 7 个 Service 内联 | KEEP | 逐条保留在原位置，理由同上 |
| 5 个 logic 的 nil/`!Success` 守卫 | 各 Service 内联 | KEEP | 守卫**不对称**是显式语义（§12.3 差异 2） |
| `delete_*` / `update_*` 的 `Success: true` 硬编码 | 各 Service 内联 | KEEP | 见 §12.3 差异 1 |
| 2 个 list logic 的 proto→DTO 投影 | `service/FavoriteApiSupport.fromProto`（2 个重载） | MERGE | 与 `UserApiSupport.fromProto`（`:61,77`）同定位；丢弃 `user_id` 的白名单语义也收口在这里 |
| `types.go:11,23,43,50-51` 响应侧 `,string` | **DTO 字段声明为 `String`**，投影时 `Long.toString(...)` | ADAPT | 见下方「` ,string` 响应的落地方式」 |
| `types.go:45-46,56` 裸 `int64` 时间戳 | `long` 字段 | KEEP | 不带 `,string`，输出 JSON 数字 |
| `types.go:7,17,18,19,54,55,52,53` 普通 string | `String` 字段 | KEEP | `optional` 不映射成校验注解（Go 侧无 validator） |
| api 侧无 `internal/metrics/` | **不建 `metrics/` 包** | — | §12.3 已确认 Go 侧无该目录；favorite-api 无任何业务指标 ⇒ 空包即 §14.4 禁止的"为填满目录而建" |

#### `,string` 响应的落地方式（**刻意不用 Jackson serializer**，需在 §3.2 留痕）

Go 的 `json:"folderId,string"` 作用于 `int64` 时，`encoding/json` 输出的是 **JSON 字符串** `"123"`。
两种 Java 落地方式：

| 方案 | 结论 |
|---|---|
| `Long` 字段 + `@JsonSerialize(using = ToStringSerializer.class)` | ❌ **不采用**。Spring Boot 4 的 MVC 默认走 Jackson 3（`WebMvcConfig.java:36-39` 的注释明写 `Jackson2ObjectMapperBuilderCustomizer` 已随模块化消失），`ToStringSerializer` 的包名与 Jackson 2/3 不同；而本仓内部序列化统一用 Jackson 2 的 `com.fasterxml.jackson`（根 `pom.xml:73-74`）。⇒ 该写法会把版本差异暴露成运行时 `ClassNotFound`。**UNKNOWN：Boot 4 MVC 的 `@JsonSerialize` 究竟解析哪个包，需实测。** |
| **`String` 字段 + 投影处 `Long.toString(...)`** | ✅ **采用**。字节级等价于 Go 输出，且零框架版本依赖。投影点是 `FavoriteApiSupport.fromProto` 与 2 个 create Service 的返回值构造，**只有 3 处**。⚠️ 落地后必须在 DTO javadoc 写明"此字段是 String 是为了对齐 Go 的 `,string`，不要改成 Long" |

---

## 12.5 第五部分：理论 Java 文件树

```
service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/
├── FavoriteApiApplication.java
├── advice/
│   ├── GlobalResponseAdvice.java
│   └── GlobalExceptionHandler.java
├── client/
│   └── FavoriteRpcClient.java
├── config/
│   ├── FavoriteApiProperties.java
│   └── WebMvcConfig.java
├── controller/
│   └── FavoriteController.java
├── dto/
│   ├── CreateFavoriteFolderReq.java
│   ├── CreateFavoriteFolderResp.java
│   ├── CreateFavoriteReq.java
│   ├── CreateFavoriteResp.java
│   ├── DeleteFavoriteFolderReq.java
│   ├── DeleteFavoriteFolderResp.java
│   ├── DeleteFavoriteReq.java
│   ├── DeleteFavoriteResp.java
│   ├── FavoriteFolder.java
│   ├── FavoriteItem.java
│   ├── GetFavoriteFoldersResp.java
│   ├── GetFavoritesByFolderResp.java
│   ├── UpdateFavoriteFolderNameReq.java
│   └── UpdateFavoriteFolderNameResp.java
├── interceptor/
│   └── FavoriteJwtAuthInterceptor.java
└── service/
    ├── FavoriteApiSupport.java
    ├── FavoriteCreateFolderService.java
    ├── FavoriteDeleteFolderService.java
    ├── FavoriteListFoldersService.java
    ├── FavoriteUpdateFolderNameService.java
    ├── FavoriteCreateItemService.java
    ├── FavoriteDeleteItemService.java
    └── FavoriteListItemsService.java

service/favorite/favorite-api/src/main/resources/
└── application.yml          # server.port=8890 / favorite-api.user-auth.* / spring.data.redis.* /
                             # grpc.client.favorite-rpc / management.endpoints.*
```

**共 24 个 Java 文件**（Go 侧 22 个非测试文件 → 24 个 Java 文件，其中 2 个是 Go 侧没有但职责必需的：`WebMvcConfig` 的 `JsonMapperBuilderCustomizer` 分支与 `FavoriteApiSupport`；而 Go 侧的 `svc/service_context.go`、`config/config.go`、`favorite.go` 三个文件被框架吸收）。

### 不建的目录（逐条给依据，§14.4）

| Go 侧有 / 常被照抄的东西 | 本文件结论 | 依据 |
|---|---|---|
| `metrics/` | **不建** | Go `service/favorite/api/` 下无 `internal/metrics/`（§12.2 目录清单）。unlike `like-api`（Go 有 `internal/metrics/metrics.go`）与 `user-api`（Go 有 `UserApiInterceptCount`） |
| `handler/` / `logic/` / `svc/` / `middleware/` / `types/` | **一律不建** | §3.3 规则 5 明文禁止 Go 风格目录；职责分别落到 `controller/` / `service/` / （框架吸收）/ `interceptor/` / `dto/` |
| `dto/GetFavoriteFoldersReq.java` | **不建** | 零字段的空类 |
| `dto/GetFavoritesByFolderReq.java` | **不建** | 1 字段的 query-param holder，投影/解析已由 `GoStyleStringInt64` 承担 |
| `dto/GetFavoritesByFolderResp` 之外的 `*Req` | 保留 5 个 | JSON body 契约独立 |

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `FavoriteApiApplication` | 入口；`scanBasePackages={com.rag.favorite.favorite.api, com.rag.common}`；`@EnableConfigurationProperties`；`@EnableDiscoveryClient` | `favorite.go:20-37` | 框架约定 |
| `advice/GlobalResponseAdvice` | `beforeBodyWrite` 包 `Response(200,"OK",body)`；**必须对 `body instanceof Response` 与 `null` 放行**；非 JSON Content-Type 原样透传 | 7 个 handler `:29-33` 的成功信封 | 与 users 同款；二次包裹是已实证缺陷（`GlobalResponseAdvice.java:30-39`） |
| `advice/GlobalExceptionHandler` | ① `BusinessException` → `Response(code,msg,null)` HTTP 200；② `AuthenticationException` → **401**；③ 🔴 **`HttpMessageNotReadableException` → HTTP 200 + `Response(5003,"参数错误",null)`**（**覆写 user-api 的 400 text/plain**）；④ 其余兜底 5001 | 7 个 handler `:18-25` + 5 个 logic 的错误分支 | ③ 是本文件的核心差异点，独立成类才留得下痕迹 |
| `client/FavoriteRpcClient` | `@GrpcClient("favorite-rpc")` 阻塞 stub | `service_context.go:24` | 外部服务客户端，`client/` 有先例 |
| `config/FavoriteApiProperties` | `user-auth.access-secret`（**不映射 `access-expire`**） | `config.go:11-14` + `favorite.yaml:10-12` | 配置独立关注点 |
| `config/WebMvcConfig` | ① `addInterceptor(FavoriteJwtAuthInterceptor).addPathPatterns("/favorite/v1/**")`；② `JsonMapperBuilderCustomizer` 挂 `GoStyleCoercion` | `routes.go:57-58`；`WebMvcConfig.java:46-59` 同款 | 与 users 同款；② 不写则 `,string` 之外的标量强转与 Go 不一致 |
| `controller/FavoriteController` | 7 个映射：`@PostMapping("/folder/create")`、`@DeleteMapping("/folder/delete")`、`@GetMapping("/folder/list")`、`@PostMapping("/folder/update")`、`@PostMapping("/item/create")`、`@DeleteMapping("/item/delete")`、`@GetMapping("/item/list")`；`@RequestBody(required=false)`（6 个）+ `@RequestParam(value="folderId", required=false)`（1 个） | 7 个 handler | **7 个 Go handler 不拆 7 个 Controller**：逐字同质（各 35 行）；且全仓 4 个 api 模块都是单 Controller |
| `dto/*Req`（5 个） | `folderId,string` / `favoriteId,string` 用 `GoStyleStringInt64`；**不引入 `@NotNull` 等校验注解** | `types.go:14-20,26-29,34-37,74-77` | 请求契约与 `,string` 宽松解析独立于响应 |
| `dto/*Resp`（6 个）+ `FavoriteFolder` + `FavoriteItem` | `folderId` / `favoriteId` **声明为 `String`**（投影时 `Long.toString`）；`createdAt/updatedAt` 为 `long`；**无 `user_id` 字段** | `types.go:10-12,22-24,30-32,38-40,42-47,49-57,62-64,70-72,79-81` | 响应契约与序列化形态独立；2 个 item 跨 Resp 复用 |
| `interceptor/FavoriteJwtAuthInterceptor` | 阶段 1 JWT 验签 → 写 `request.setAttribute("userId", Long)`；阶段 2 blacklist（**fail-open ×2**）→ 写 `jwt_token`；失败抛 `AuthenticationException` | `rest.WithJwt`（`routes.go:57`）+ `checkblacklist_middleware.go:22-50` **合并** | **必须独立 Bean**：密钥与 public 集与 user-api 相同但**独立配置**；两个中间件职责同属鉴权链，合并才不会让"谁先执行"变成配置问题 |
| `service/FavoriteApiSupport` | `extractUserId(request)` + `fromProto(FavoriteFolder)` + `fromProto(FavoriteItem)` + `bizCodeFrom(Throwable)` + 日志选项构造 | `logic/helper.go:14-46` + 2 个 list logic 的投影（`:58-66`、`:64-75`） | 与 `UserApiSupport`（`:35`）/`UserApiSupport.java:61,77` 同一形态；跨 7 个 Service 复用的片段收口处 |
| `service/FavoriteCreateFolderService` | uid 提取 → `CreateFavoriteFolder` → nil 守卫 5001 → 返回 `CreateFavoriteFolderResp(Long.toString(folderId))` | `create_favorite_folder_logic.go:36-65` | 与其余 6 个 api 侧 api 模块一致（`article-api/service/` 每端点 1 个） |
| `service/FavoriteUpdateFolderNameService` | 同上 + **额外** `!rpcResp.Success` 守卫 + `Success: true` 硬编码 | `update_favorite_folder_name_logic.go:36-67` | 同上 |
| `service/FavoriteListFoldersService` | uid 提取 → `GetFavoriteFolders` → nil 守卫 → **丢弃 `user_id`** 的 4 字段投影 | `get_favorite_folders_logic.go:36-69` | 同上 |
| `service/FavoriteDeleteFolderService` | 同上 + `!Success` 守卫 + `Success: true` 硬编码 | `delete_favorite_folder_logic.go:36-65` | 同上 |
| `service/FavoriteCreateItemService` | uid 提取 → `CreateFavorite`（**`targetId` 不 Trim**）→ nil 守卫 | `create_favorite_logic.go:37-72` | 同上 |
| `service/FavoriteDeleteItemService` | 同上 + `!Success` 守卫 + `Success: true` 硬编码 | `delete_favorite_logic.go:36-65` | 同上 |
| `service/FavoriteListItemsService` | uid 提取 → `GetFavoritesByFolder` → nil 守卫 → 7 字段投影 | `get_favorites_by_folder_logic.go:36-78` | 同上 |
| `resources/application.yml` | `server.port: 8890`；`favorite-api.user-auth.access-secret`；`spring.data.redis.*`（单节点）；`grpc.client.favorite-rpc.address: discovery:///favorite-rpc`；`management.endpoints.web.exposure.include: health,info,prometheus`；`otel.service.name: favorite-api` | `favorite.yaml` 全部**活跃段** | 配置替代 |

### 必配测试（§3.3「凡引入新的框架机制，必须配一条能真正触发该机制的测试」）

纯 Mockito 单测**不算** —— 它不执行 JSON 解析、不装配 Spring 上下文，天然测不到 §12.3 的解析失败差异与 `,string` 序列化。

| 测试 | 触发什么 | 锁死什么 |
|---|---|---|
| `FavoriteApiMvcPipelineTest`（`@SpringBootTest` + `MockMvc`，真 `FavoriteApiApplication`） | 7 条路由的 method+path、`@RequestBody`/`@RequestParam` 绑定、`AuthenticationException` → 401 | 路由表（`routes.go:21-52`）逐条 |
| `FavoriteApiEnvelopeContractTest`（同上 + 真 PG 无关，只测序列化） | `folderId` 输出为 **JSON 字符串**、`createdAt` 为 JSON 数字、`data` 单层不双包 | `types.go:11,23,43,45-46,50-51,56` |
| `FavoriteParseFailureEnvelopeTest` | 发畸形 JSON / 类型错误 body | **HTTP 200 + `{code:5003,msg:"参数错误",data:null}`**（7 个 handler `:18-25`）—— 🔴 这条就是防"照抄 user-api 的 400"的那道锁 |
| `FavoriteApiBlacklistParityTest`（真 Redis 或 Testcontainers Redis） | 缺头 / 空 token / Redis 故障 / 命中黑名单 四态 | `checkblacklist_middleware.go:24-46` 的两条 fail-open + 401 body 只有 2 字段 |
| `FavoriteApiGoParityTest`（**双侧探针**：Go 侧直接跑 `httptest` 打 Go handler，RAG 侧打真 MVC） | 同一请求体两侧的 `code`/`msg`/`data` 逐字节 | §3.3 强制两条 2 —— 只跑 RAG 的探针只能证明"代码写成了这样"，证明不了"Go 是这样" |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `favorite.go` | `FavoriteApiApplication` | ABSORB | 进程装配由框架承担 |
| `favorite.go:28` 禁用原生超时 | — | — | Spring MVC 无等价默认超时，**无代码可落**（不是 ABSORB，是"无对应物"） |
| `favorite.go:30` HTTP span | `common/spring/HttpMiddlewareFilter` | REUSE | `@Component`，靠 `scanBasePackages` 含 `com.rag.common` 注册 |
| 无 `SetOkHandler` + 7 个 handler 成功信封 | `advice/GlobalResponseAdvice` | ADAPT | 语义等价（`GetErrMsg(200)=="OK"`） |
| 无 `SetErrorHandler` + 7 个 handler 业务失败信封 | `advice/GlobalExceptionHandler` + `common/exception/BusinessException` | ADAPT | 业务码由 Service 抛出，HTTP 恒 200 |
| 🔴 7 个 handler `:18-25` 解析失败 | `GlobalExceptionHandler.handleUnreadableBody`（**覆写 400**） | ADAPT | Go = HTTP 200 + 5003 |
| `internal/config/config.go:11-14` UserAuth | `config/FavoriteApiProperties.UserAuth` + `application.yml` | ADAPT | 绑定机制换 Spring |
| `internal/config/config.go:16` BizRedis | `application.yml` `spring.data.redis.*` | ADAPT | 同上 |
| `internal/config/config.go:15` FavoriteRpc | `client/FavoriteRpcClient` | ADAPT | `zrpc.MustNewClient` → `@GrpcClient` |
| `etc/favorite.yaml:3` Port 8890 | `application.yml` `server.port` | ADAPT | 配置替代 |
| `etc/favorite.yaml:12` AccessExpire | **不映射** | — | api 侧无读取点（`rest.WithJwt` 只吃 secret） |
| `etc/favorite.yaml:25-32` DevServer 16063 | `management.endpoints.*` | ADAPT | Go `Config` 无对应字段；**UNKNOWN** 见 §12.7 |
| `etc/favorite.yaml:19-23` Telemetry | `otel.service.name: favorite-api` | ADAPT | Actuator/OTel 覆盖 |
| `svc/service_context.go` 整体 | Spring Bean 图 | ABSORB | 依赖持有由容器承担 |
| `routes.go:58` `WithPrefix("/favorite/v1")` | `@RequestMapping("/favorite/v1")` | ADAPT | 契约本就一致，无偏离 |
| `routes.go:57` `WithJwt`（7/7） | `interceptor/FavoriteJwtAuthInterceptor` 阶段 1 | ADAPT | 独立 Bean；public 集为空集 |
| `routes.go:17-18` `WithMiddlewares([CheckBlacklist])` | `interceptor/FavoriteJwtAuthInterceptor` 阶段 2 | MERGE | 与验签共享 header/token/Redis，同一鉴权链 |
| `routes.go` 无 `WithTimeout` / maxBytes / CORS | 不配置 | ABSORB | Go 无此装饰器 |
| `handler/favorite/*.go`（7 个） | `controller/FavoriteController`（单一） | MERGE | 逐字同质（各 35 行）；全仓 api 侧均为单 Controller |
| `handler/*.go` 的 7 份 envelope 代码 | `advice/GlobalResponseAdvice` + `GlobalExceptionHandler` | SPLIT | 一份响应构造拆到两个 Spring 组件 |
| `types/types.go`（5 组 JSON Req） | `dto/*Req`（5 个） | SPLIT | 契约与 `,string` 宽松解析独立 |
| `types/types.go:59-60` 空 Req | **不建** | — | 零字段空类 |
| `types/types.go:66-68` form Req | **不建**（`@RequestParam` + `GoStyleStringInt64`） | — | 1 字段 holder 类不构成独立职责 |
| `types/types.go`（6 组 Resp + 2 item） | `dto/*Resp` + `dto/{FavoriteFolder,FavoriteItem}` | SPLIT | 响应契约与序列化形态独立；2 个 item 跨 Resp 复用 |
| `types.go` 的 `,string`（入参） | `GoStyleStringInt64`（REUSE） | REUSE | 逐条等价于 Go 的 `,string` 宽松解析 |
| `types.go` 的 `,string`（出参） | `String` 字段 + `Long.toString` | ADAPT | 规避 Jackson 2/3 包名歧义，见 §12.4 |
| `types.go` 的 `,optional` | **不映射校验注解** | — | Go 侧零 validator（`types.go` 全文 + 无 `SetValidator`） |
| `logic/helper.go:14-34` `extractUserID` | `service/FavoriteApiSupport.extractUserId` | MERGE | 7 个 logic 逐字同构 |
| `logic/helper.go:36-42` 日志选项 | `common/logger/{WithUserID,WithArticleID}` | REUSE | 逐字对齐 |
| `logic/helper.go:44-46` `codeFromRPCError` | `favorite-common` 的 `FavoriteGrpcError.bizCodeFromError` | REUSE | 与 `MessageGrpcError.java:195` 同款 |
| `logic/create_favorite_folder_logic.go` | `service/FavoriteCreateFolderService` | MERGE | 7 logic → 7 单端点 service（与 article-api/message-api 的 api 侧形态一致） |
| `logic/create_favorite_logic.go` | `service/FavoriteCreateItemService` | MERGE | 同上 |
| `logic/delete_favorite_folder_logic.go` | `service/FavoriteDeleteFolderService` | MERGE | 同上 |
| `logic/delete_favorite_logic.go` | `service/FavoriteDeleteItemService` | MERGE | 同上 |
| `logic/get_favorite_folders_logic.go` | `service/FavoriteListFoldersService` | MERGE | 同上 |
| `logic/get_favorites_by_folder_logic.go` | `service/FavoriteListItemsService` | MERGE | 同上 |
| `logic/update_favorite_folder_name_logic.go` | `service/FavoriteUpdateFolderNameService` | MERGE | 同上 |
| 2 个 list logic 的 proto→DTO 投影 | `service/FavoriteApiSupport.fromProto` | MERGE | 跨 2 个 Service 复用的白名单投影 |
| 3 个写操作的 `Success: true` 硬编码 | 各 Service 内联 | KEEP | L1 冻结 |
| `common/errmsg.go`（api 侧所需 4 码 + `GetErrMsg` + `BizCodeFromError`） | `favorite-common` 的 `FavoriteErrorCode` / `FavoriteErrorMessages` / `FavoriteGrpcError` | ADAPT | 详见 `FileTree/favorite/common.md` |
| —— | `service/favorite/favorite-api/pom.xml` | **需补依赖** | 现状零依赖。至少需：`favorite-proto`、`favorite-common`、`users-common`、`common`、`spring-boot-starter-web`、`spring-boot-starter-data-redis`、`grpc-client-spring-boot-starter`、`jjwt-{api,impl,jackson}`、`spring-boot-starter-actuator`、`micrometer-registry-prometheus`、`spring-cloud-starter-alibaba-nacos-discovery`、`opentelemetry-exporter-otlp`、test scope 的 `spring-boot-starter-webmvc-test` |
| —— | **不建** `metrics/` 包 | — | Go api 侧无 `internal/metrics/` |
| —— | **不建** `handler/` `logic/` `svc/` `middleware/` `types/` | — | §3.3 规则 5 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 7 条路由（method + path + 前缀）逐条录于 §12.3；全量 `WithJwt`、public 集为空集；`folderId/favoriteId,string` 的入参与出参两侧；`form` tag；`optional`；HTTP 状态码恒 200（业务路径）；成功信封 `{code:200,msg:"OK",data:…}` |
| L2 业务语义有承载位置 | ✅ uid 提取的 5 类型分支 + nil 分支；`nil` 守卫 vs `nil‖!Success` 守卫的**不对称**；`Success:true` 硬编码；proto→DTO 丢弃 `user_id`；`targetId` 直传不 Trim（与 span 属性的 Trim 不一致）；`bizCodeByMsg` 优先于 gRPC code switch 的反查顺序（见 `common.md` §12.3） |
| L3 技术语义有承载方案 | ✅ 两个中间件的**执行顺序**（JWT 先、blacklist 后）已在 `FavoriteJwtAuthInterceptor` 的两阶段结构中固定；blacklist 的两条 fail-open；401 body 只有 2 字段；Redis 故障不阻塞业务 |
| 业务分支与异常路径已纳入 | ✅ 7 个 logic × 5 类分支（uid 失败 / rpc 错误 / nil 响应 / `!Success` / 成功）逐条列举；handler 的解析失败分支单列 |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；`DisableNativeRestTimeout` 无对应物；`service_context.go` → Bean 图；无超时装饰器 → 不加；无 multipart → 不配 limit；无 CORS → 不配 |
| 复用组件已明确 | ✅ `Response`、`BusinessException`、`AuthenticationException`、`HttpMiddlewareFilter`、`JwtUtil`、`BearerTokenExtractor`、`GoStyleStringInt64`、`GoStyleCoercion`、`WithUserID`、`WithArticleID`、`@GrpcClient`、单层包裹放行先例 |
| 新增文件有职责依据 | ✅ `FavoriteJwtAuthInterceptor`（鉴权链 + Redis）、`FavoriteApiSupport`（跨 7 Service 复用的片段）、`FavoriteApiProperties`（配置）、7 个单端点 Service（与 article-api / message-api / like-api 的 api 侧形态一致）—— **均非"Go 有同名文件"** |
| 合并有组织依据 | ✅ 7 handler → 1 Controller（逐字同质 + 全仓 api 侧单 Controller 惯例）；`WithJwt` + `CheckBlacklistMiddleware` → 1 个 Interceptor 的两阶段（同 header / 同 token / 同 Redis / 顺序敏感）；7 logic → 7 单端点 Service（与既有 3 个 api 模块一致） |
| 拆分可解释 | ✅ Req / Resp 分离；2 个共享 item 单列；响应 envelope 拆到两个 Spring advice |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `Types.java` / `ServiceContext.java` / `Metrics.java` / `Repository.java` / `Middleware.java`；**主动不建**空 Req、form Req 与整个 `metrics/` 包 |
| 未把未实现能力说成现有机制 | ✅ `favorite-api` 的空壳 + 空依赖已在 §12.1 声明；本文件所有"复用"均给出实际打开过的路径与行号 |
| 不为"完整"建空组件 | ✅ 4 条不建结论逐条给依据（§12.5 表） |
| 上下游口径一致 | ✅ 与 `FileTree/favorite/rpc.md` 的同一结论：RAG 侧 **4 个组件**（`favorite-proto` + api + rpc + common）已全部在 `service/favorite/pom.xml:14-19` 声明，本文件**不新增 maven 模块** |

### 不通过项

| # | 问题 | 详情 | 状态 |
|---|---|---|---|
| **1** | 🔴 **解析失败的 HTTP 状态码与 RAG api 侧惯例冲突** | Go 侧 7 个 handler **自己**吞掉 `httpx.Parse` 的错误并写 `httpx.OkJsonCtx(... 5003 ...)`（`create_favorite_folder_handler.go:18-25`，7 个文件同款）⇒ **HTTP 200 + `{code:5003,msg:"参数错误",data:null}`**。而 `user-api` 的 `GlobalExceptionHandler.java:163-167` 把 `HttpMessageNotReadableException` 处理成 **HTTP 400 + text/plain**，是 AGENTS.md §6.1 #1 记录的"go-zero 未注册 SetErrorHandler 时才会出现的形态"。⇒ **本模块的 `GlobalExceptionHandler` 必须覆写这一分支**，否则一处照抄就制造可观察差异 | ⏸ 待实施时落地，§12.5 已把它写进 `GlobalExceptionHandler` 的职责，§12.5「必配测试」有专门的 `FavoriteParseFailureEnvelopeTest` 锁死 |
| **2** | 🔴 **`favorite-api/pom.xml` 零依赖** | 现状无 `favorite-proto`、`favorite-common`、`users-common`、`common`、web starter、Redis starter、`jjwt`、grpc-client、nacos。**一行上面列出的包结构都编译不过**。⚠️ `spring-boot-starter-data-redis` 若配了 `lettuce.pool` 还需 `commons-pool2`（`like-rpc/pom.xml:106-127` 的实测教训）；**favorite 建议不配 pool**，从而不需要该依赖 | ⏸ §12.6 末行已列出完整依赖清单 |
| **3** | ⚠️ **`favorite.yaml` 有 5 段、`config.go` 只有 3 个字段** | `favorite.yaml:19-32` 的 `Telemetry` / `DevServer` 在 `config.go:9-17` 里**没有对应字段**。go-zero `conf.MustLoad` 对未知键是报错还是忽略，取决于 go-zero 库实现（**不在本仓库**）⇒ 无法从本仓取证 | **UNKNOWN** |
| **4** | ⚠️ **`AccessExpire: 2592000` 是死配置** | `routes.go:57` 的 `rest.WithJwt(secret)` 只接受 secret；favorite-api **不签发任何 token** ⇒ 该字段在 api 侧零读取点。RAG 侧照抄进 `FavoriteApiProperties` 是无害的，但**绝不能据此实现任何 TTL 逻辑**（Go 没有） | 已在 §12.4/§12.6 明确"不映射" |
| **5** | ⚠️ **`/item/list` 的取参方式在畸形调用下不可完全等价** | `types.go:67` 用 `form:` tag。go-zero `httpx.Parse` 的分支是 `withJsonBody`（`r.ContentLength > 0 && Content-Type 含 application/json`）⇒ **一个带 JSON body 的 `/item/list` 请求在 Go 侧会走 JSON 分支**，从而忽略 `form` tag。Spring 侧 `@RequestParam` 永远只读 query ⇒ 该情形下行为不同。**但该路由注册为 GET**（`routes.go:51`），正常客户端不会带 body ⇒ 实际不可达 | **UNKNOWN**（Go 侧 `httpx.Parse` 源码不在本仓库，只有 `UserController.java:71-72` 的引用） |
| **6** | ⚠️ **blacklist 401 的 body 形状是第三种** | Go favorite 输出 `{"code":5004,"msg":"未登录或登录已失效"}`（`checkblacklist_middleware.go:41-44`，**无 `data`**）；`AuthenticationException.java:24-39` 记录的 users 侧是 `{"code":401,"msg":"登录已失效,请重新登陆"}`。两者都无 `data`，但 `code` 不同（5004 vs 401）。RAG 的 `GlobalExceptionHandler.java:73-78` 统一产出完整 envelope `{code,msg,data:null}` ⇒ **data 字段是新增的** | 属 §3.2 需留痕的表达层差异；建议在实施时同步登记进 `docs/Intentional_Deviations/` |
| **7** | ⚠️ **`user_id` 永不出 api 边界** | proto 有 `user_id`（`favorite.proto:8,17`），HTTP DTO 没有（`types.go:42-47,49-57`）。这是**可观察的字段缺失**。若实施时"顺手补上"就是新增字段 | 已在 §12.3 差异 4 与 §12.5 DTO 行显式标注"无 `user_id` 字段" |

### UNKNOWN 清单

| # | 项 | 说明 | 影响面 |
|---|---|---|---|
| U1 | go-zero `conf.MustLoad` 对 `favorite.yaml` 未知键（`Log` / `Telemetry` / `DevServer`）的处理 | 库实现在 `github.com/zeromicro/go-zero`（**不在本仓库**），本仓只有 `UserController.java:71-72` 对 `httpx.Parse` 的引用级证据 | 决定 Go 进程能否启动；**RAG 侧无影响**（Actuator 已覆盖），但影响 §12.3 的"Go 现状是什么"这一前提 |
| U2 | Boot 4 MVC 的 `@JsonSerialize` 究竟解析 Jackson 2 还是 Jackson 3 的 `JsonSerializer` | `WebMvcConfig.java:36-39` 只说 Jackson 3 的 coercion 只能在 builder 上配，**没有**说 `@JsonSerialize` 的解析规则；本仓内部序列化统一 Jackson 2（根 `pom.xml:73-74`） | 决定 `,string` 能否用 serializer 落地。本文件已用 `String` 字段方案绕开，**但若日后有人改成 Long+serializer 必须先实测** |
| U3 | `/item/list` 在「GET + JSON body」下 Go 走哪条解析分支 | `httpx.Parse` 源码不在本仓库 | 该请求形态在实践中不可达（路由是 GET）；若为 `UNKNOWN`，则这条不可达性论证本身也带 `UNKNOWN` |
| U4 | 客户端是否存在 favorite-api 的实际流量与调用形态（是否有客户端用 `DELETE` + body） | RAG 仓库内无 favorite 的调用方（`@GrpcClient` 清单里只有 message/user/article/like/hot/admin） | 决定 §12.5 的 `@RequestBody` on `@DeleteMapping` 是否需要额外兼容 `@RequestParam` 形态 |
| U5 | `BizRedis.Pass: ""` 在 RAG 侧是否需要显式配置空密码 | Go `favorite.yaml:17` 是空串；`like-rpc/application.yml:73` 用 `${REDIS_PASSWORD:}` 表达同一件事 | 仅配置面，无行为差异 |
| U6 | go-zero `rest.WithJwt` 写入 ctx 的 `userId` 类型在**所有**路径下都是 `json.Number` | `helper.go:20-33` 的 5 个分支说明"防御性兼容"，主路径是 `json.Number`（`:21-22`）；但 Java 侧 `JwtAuthInterceptor.java:146` 写的是 `Long.valueOf(uid)` ⇒ **RAG 侧 ctx 里恒为 `Long`，Go 侧的 5 分支在 Java 侧只有 1 条可达** | 不是差异（Java 侧只需 1 条），但**必须确认**：`extractUserId` 不应照抄 5 分支，而应直接读 `Long` + 声明 `UNKNOWN` 若出现非 Long |

---

## 附：本文件与其它文档的关系

| 文档 | 关系 |
|---|---|
| `FileTree/favorite/rpc.md` | 下游对端。api 的 7 个端点 1:1 映射到 rpc 的 7 个 gRPC 方法（`favorite.proto:100-106`）；api 的 `bizCodeFrom` 依赖 rpc 侧用 `favorite-common` 的 `GRPCError` 抛错 |
| `FileTree/favorite/common.md` | api 的错误码来源。`favorite.yaml:11` 的密钥、7 个 handler 的 `GetErrMsg`、5 个 logic 的 `BizCodeFromError` 三处依赖它 |
| `docs/Intentional_Deviations/comment/proto.md`、`like/proto.md` | **不适用于 favorite**。favorite 的 proto `package` 两侧逐字一致，零 wire 偏离 |
| `FileTree/like/api.md` | 格式与 api 侧包结构母版。**三处不同**：like 的 7 个路由全 POST 而 favorite 混有 DELETE/GET；like 有 `metrics/` 而 favorite 没有；like 的 handler 用 `httpx.ErrorCtx`（400）而 favorite 自己回 200 |