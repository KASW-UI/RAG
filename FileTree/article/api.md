# article-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/article/api`
> 目标根包：`com.rag.article.article.api`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/article/article-api` **只有 `pom.xml`，无 Java 代码**。包结构依据来自同仓库已落地的 `users/{user-api,admin-api}` 与 `message/message-api`：

| 参照 | 提供的一致性依据 |
|---|---|
| `users/user/user-api` | `@RestController` + `@RequestMapping("/user/v1")`；`advice/{GlobalResponseAdvice,GlobalExceptionHandler}`；`interceptor/JwtAuthInterceptor`；`config/{UserApiProperties,WebMvcConfig}`；`client/UserRpcClient`；`metrics/UserApiMetrics`；`service/UserApiSupport`（归一化 + proto→DTO 投影）；`service/IdentityResolver` |
| `message/message-rpc/client/` | `rpc/` 下游客户端的 `client/` 定位 |

### 已确认的框架机制

- `@RestControllerAdvice` + `ResponseBodyAdvice`（`com.rag.common.response.Response` 信封）
- `HandlerInterceptor` + `WebMvcConfigurer`（JWT + Redis blacklist）
- `@ConfigurationProperties`
- `net.devh:grpc-server-spring-boot-starter` 的 `@GrpcClient`
- Micrometer；`common/observability/HttpMiddlewareFilter`（已 `@Component` 全局注册）

### 当前 Component 可以直接复用的能力

- **JWT 鉴权**：RAG 的 `JwtAuthInterceptor` 已实现「public 路径前缀放行 + Redis blacklist + fail-open 故障策略」，且 article-api 的 public/user 分组与 users 形态一致 → 可参照复用同一模式（**但需独立 Bean**，因 user 与 admin 各有一份）
- **`Response` 信封 + advice**：`common/response/Response.java` + `advice/GlobalResponseAdvice` 组合已定型
- **`common/snowflake`**：本模块无 ID 生成需求
- **`common/observability`**：HTTP span 由全局 Filter 提供，无需自建

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **路由前缀** | Go 为 `/v1/article`（`routes.go` 用 `rest.WithPrefix("/v1")`）；RAG 的 users 已偏离为 `/user/v1`、`/admin/v1`（AGENTS.md §3.4 留档）。**article 尚无决策**。本文件按 **Go 契约 `/v1/article`** 推导，若要跟随 RAG 惯例改为 `/article/v1` 属已留档类别的 wire 变更，**需用户拍板** |
| HTTP method | Go 为 POST/PUT/DELETE/GET 混合；RAG 的 users 统一 POST（AGENTS.md §3.4 留档）。本文件**保留 Go 的混合 method** |
| `SecurityRpc` 客户端 | Go 侧已 wire 但 api **零调用** → 不迁移 |

---

## 12.2 第二部分：Go 原始文件树

```
service/article/api/
├── article.go                                   # 入口：REST server + CORS + observability
├── internal/
│   ├── config/config.go                          # RestConf + Auth + ArticleRpc + SecurityRpc + UserRpc
│   ├── svc/service_context.go                    # 3 个下游 gRPC client
│   ├── handler/
│   │   ├── routes.go                             # 3 个 AddRoutes 块（JWT / public / upload）
│   │   └── article/*.go                          # 7 个薄 handler
│   ├── logic/
│   │   ├── helper.go                             # uid 提取 + 作者名补全 + 防御性类型强转
│   │   ├── create_article_logic.go                # 70s 超时 + gRPC code 映射
│   │   ├── get_article_logic.go                  # PublicOnly=true + 非 PUBLISHED → 1002
│   │   ├── get_author_article_logic.go           # RequesterId=uid + 作者不匹配 → 1006
│   │   ├── list_articles_logic.go                # PublicOnly=true + 任一非 PUBLISHED → 整包 5001
│   │   ├── update_article_logic.go               # 两阶段 + optional 指针语义
│   │   ├── delete_article_logic.go               # 两阶段（先 GetArticle 校验作者）
│   │   └── upload_image_logic.go                 # multipart 读取 + 转发（**非信封**）
│   ├── types/types.go                            # HTTP DTO
│   └── metrics/metrics.go                        # api 侧指标（api 侧实际未见触发点）
```

---

## 12.3 第三部分：Go 文件职责分析

### `handler/routes.go`（3 个 AddRoutes 块）
| 块 | 路由 | 中间件 |
|---|---|---|
| 1 | `POST /v1/article`、`PUT /v1/article/:id`、`DELETE /v1/article/:id`、`GET /v1/me/article/:id` | `rest.WithJwt` + `rest.WithTimeout(70s)` |
| 2 | `GET /v1/article/:id`、`GET /v1/articles` | 无（public） |
| 3 | `POST /v1/upload` | `rest.WithMaxBytes(10 MiB)` |

- **L1**：三块的分组、前缀、method、70s 超时、10MiB 上限
- **L4**：Spring `@RequestMapping` + 拦截器路径前缀 + `spring.servlet.multipart.max-file-size`

### `handler/article/*.go`（7 个）
统一模式：`httpx.Parse` → `New*Logic` → `httpx.OkJson`。**唯一例外**是 `upload_image_handler.go` —— **不走信封**，直接 `httpx.OkJsonCtx`。
- **L2**：handler 本身**不做校验**（如 `ListArticles` 的畸形 `page` 原样进 `normalizePagination`）

### `logic/helper.go`
三件事：
1. `extractCurrentUserID(ctx)`：从 ctx 取 `userId`，缺失/空 → `ErrorUnauthorized`
2. `resolveAuthorName`：调 `UserRpc.GetUser`，用 `resp.Found` + `resp.User.Username`；**失败一律吞掉**，回退 `"用户 <id>"` / `"未知作者"`
3. 防御性强转：接受 `json.Number` / `string` / default fmt，做 `trim` + 空判

- **L2**：**作者名补全失败绝不影响文章响应** —— 这是显式的容错设计，不是遗漏

### `logic/create_article_logic.go`
`ArticleRpc.CreateArticle` 带 **70s 调用超时**（`zrpc.WithCallTimeout(70s)`）。gRPC code → api 错误码映射：

| gRPC code | api 错误码 | 附加 |
|---|---|---|
| `AlreadyExists` | `ErrorArticleExist` 1001 | — |
| `FailedPrecondition` | `ErrorArticlePublishFailed` 1004 | **携带 article_id** |
| `DeadlineExceeded` | `ErrorArticlePublishing` 1003 | **携带 article_id** |
| `Internal` | `ErrorServerCommon` 5001 | — |
| default | `CodeServerBusy` 1015 | — |

- **L2**：`extractArticleIDFromStatusMessage` 用 `LastIndex(":")` —— **以冒号结尾的 status message 携带 article id**，这是 1003/1004 能返回 id 的机制

### `logic/get_article_logic.go`（公开详情）
`GetArticle(PublicOnly=true)` → 补作者名。**防御分支**：若 RPC 返回的 `status != PUBLISHED`，api 侧返回 `ErrorArticleNone` 1002（防止旧 RPC 泄漏未发布内容）。
- **字段映射**：`MarkdownContent` → 响应字段 `Content`

### `logic/get_author_article_logic.go`（作者详情）
`GetArticle(RequesterId=<jwt uid>)`，**不设 `PublicOnly`**。api 侧**也**做作者比对（不匹配 → 1006）—— 与 rpc 侧重复，是显式的向后兼容双检（代码注释已说明）。

### `logic/list_articles_logic.go`
`ListArticles(PublicOnly=true)` → 补作者名。
- **L2 关键**：若返回列表中**任一** item 的 `status != PUBLISHED` → **整个响应拒绝**并返回 `ErrorServerCommon` 5001，**不是静默跳过该 item**

### `logic/update_article_logic.go`（两阶段）
`GetArticle`（`IncrView=false` + `RequesterId`）→ 作者比对 → `UpdateArticle`。
- **L2**：optional 字段仅在非空时设置（`Title` / `Brief` / `MarkdownContent` / `CoverImageUrl` / `ManualTypeTag`）；`Status` 仅在 `!= 0` 时设置

### `logic/delete_article_logic.go`（两阶段）
`GetArticle`（`IncrView=false` + `RequesterId`）→ 作者比对（**不匹配直接 1006，不发 RPC**）→ `DeleteArticle`。

### `logic/upload_image_logic.go`
读 multipart 字节 → `ArticleRpc.UploadFile` → **直接返回 err，不包信封**。
- **L2**：`header.Filename` 原样转发；**本层不做 content-type 嗅探**（在 rpc 侧按扩展名做）；读失败 → `ErrorMinioUpload` 5004

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `article.go` 进程装配 | `ArticleApiApplication` | ABSORB | Spring Boot 启动 |
| `article.go` CORS | `config/WebMvcConfig.addCorsMappings` | ADAPT | 机制换 Spring MVC，语义一致 |
| `config/config.go` | `config/ArticleApiProperties` | ADAPT | RestConf + Auth + 3 个 RpcClientConf 绑定 |
| `svc/service_context.go` 3 client | `client/{ArticleRpcClient,UserRpcClient}` | NEW | `SecurityRpc` 零调用 → **不迁移** |
| `routes.go` 块 1（JWT） | `interceptor/JwtAuthInterceptor` + `@RequestMapping` | NEW | 需独立 Bean（不能复用 user-api 的实例） |
| `routes.go` 块 2（public） | 拦截器的 public 前缀放行 | ADAPT | 与 `users` 的 public 前缀机制同款 |
| `routes.go` 块 3（10MiB） | `application.yml` `spring.servlet.multipart.max-file-size` | ADAPT | 配置替代 API |
| `routes.go` 70s 超时 | `ClientInterceptor` / stub deadline | ADAPT | **仅 create 路径**，见下 |
| 7 个 handler | `controller/ArticleController`（单一） | MERGE | 7 个薄 handler 同质（parse→logic→envelope），无独立边界依据 |
| `types/types.go` Req DTO | `dto/*Req` | SPLIT | 按 HTTP 契约与校验要求分 |
| `types/types.go` Resp DTO | `dto/*Resp` + `dto/ArticleCard` | SPLIT | 同上 |
| envelope 包装 | `advice/GlobalResponseAdvice` | NEW | RAG users 同款模式 |
| 异常 → 错误码 | `advice/GlobalExceptionHandler` + `service/ArticleErrorCodes` | NEW | — |
| `helper.go` uid 提取 | `service/ArticleApiSupport.extractUserId` | NEW | 与 `users/user-api/UserApiSupport` 同款 |
| `helper.go` 作者名补全（**吞错**） | `service/ArticleAuthorResolver` | SPLIT | **独立文件**：容错策略是业务决策，且需调 `UserRpcClient`，不属于 DTO 投影 |
| `helper.go` proto → DTO 投影 | `service/ArticleApiSupport.fromProto` | MERGE | 与 uid 提取同属边界适配 |
| `helper.go` 防御性强转 | `ArticleApiSupport` | MERGE | 同上 |
| `create_article_logic.go` gRPC 映射 | `service/ArticleCreateService` + `advice/GlobalExceptionHandler` | SPLIT | 映射表是业务规则；异常→码的落点在 handler |
| `create_article_logic.go` 70s 超时 | `ArticleCreateService` 内 stub deadline | ADAPT | 保留 |
| `create_article_logic.go` status message 取 id | `ArticleCreateService.extractArticleId` | ADAPT | `LastIndex(":")` 语义保留 |
| `get_article_logic.go` | `service/ArticleGetService` | MERGE | — |
| `get_author_article_logic.go` | 同上（双检保留） | MERGE | 两端点共用一个 service，**但两个方法** |
| `list_articles_logic.go` 整包拒绝 | `service/ArticleListService` | MERGE | — |
| `update_article_logic.go` 两阶段 | `service/ArticleUpdateService` | MERGE | — |
| `delete_article_logic.go` 两阶段 | `service/ArticleDeleteService` | MERGE | — |
| `upload_image_logic.go` | `service/ArticleUploadService` | MERGE | **非信封响应**需在 advice 排除 |
| api 指标 | `metrics/ArticleApiMetrics` | ADAPT | 保留触发点 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/article/article/api/
├── ArticleApiApplication.java
├── advice/
│   ├── GlobalResponseAdvice.java
│   └── GlobalExceptionHandler.java
├── config/
│   ├── ArticleApiProperties.java
│   └── WebMvcConfig.java
├── client/
│   ├── ArticleRpcClient.java
│   └── UserRpcClient.java
├── controller/
│   └── ArticleController.java
├── dto/
│   ├── CreateArticleReq.java
│   ├── CreateArticleResp.java
│   ├── GetArticleReq.java
│   ├── GetArticleResp.java
│   ├── ListArticlesReq.java
│   ├── ListArticlesResp.java
│   ├── UpdateArticleReq.java
│   ├── UpdateArticleResp.java
│   ├── DeleteArticleReq.java
│   ├── DeleteArticleResp.java
│   └── ArticleCard.java
├── interceptor/
│   └── JwtAuthInterceptor.java
├── metrics/
│   └── ArticleApiMetrics.java
└── service/
    ├── ArticleApiSupport.java
    ├── ArticleAuthorResolver.java
    ├── ArticleErrorCodes.java
    ├── ArticleCreateService.java
    ├── ArticleGetService.java
    ├── ArticleListService.java
    ├── ArticleUpdateService.java
    ├── ArticleDeleteService.java
    └── ArticleUploadService.java
```

### 逐文件说明（要点）

| 文件 | 主要职责 | 为什么独立 |
|---|---|---|
| `ArticleApiApplication` | 入口 | 框架约定 |
| `advice/GlobalResponseAdvice` | `Response` 信封包装；**必须排除 `/upload`**（Go 侧该端点不走信封） | 与 users 同款；但有 article 特有的排除项 |
| `advice/GlobalExceptionHandler` | `StatusRuntimeException` → api 错误码映射表；框架级错误 400/405/415 | 同上 |
| `config/ArticleApiProperties` | `Auth{AccessSecret,AccessExpire}` + 下游端点 | 配置独立关注点 |
| `config/WebMvcConfig` | 注册拦截器 + CORS | 与 users 同款 |
| `client/ArticleRpcClient` | `@GrpcClient("article-rpc")` | 外部服务客户端 |
| `client/UserRpcClient` | `@GrpcClient("user-rpc")`，仅供作者名补全 | 同上 |
| `controller/ArticleController` | 7 个端点：4 个 JWT + 2 个 public + 1 个 multipart | **7 个 Go handler 不拆 7 个 Controller**：它们同质、无独立生命周期、无独立依赖边界；拆开只会制造 7 个单方法类 |
| `dto/*` | 请求/响应契约；`ArticleCard` 为共享的完整文章视图 | 请求与响应契约不同（`optional` 字段 vs 全量），分开 |
| `interceptor/JwtAuthInterceptor` | JWT + Redis blacklist + public 前缀放行 | **需独立实例**：不能复用 `user-api` 的 Bean（密钥、public 路径、租户都不同） |
| `metrics/ArticleApiMetrics` | api 侧指标 | 独立变化原因 |
| `service/ArticleApiSupport` | uid 提取 + proto→DTO 投影 + 防御性强转 | 与 `users/UserApiSupport` 同款定位 |
| `service/ArticleAuthorResolver` | 调 `UserRpc.GetUser` 补作者名，**失败一律回退不抛** | **必须独立**："补全失败不影响文章响应"是显式容错决策；混进 `ArticleApiSupport` 会让它看起来像可选的投影工具 |
| `service/ArticleErrorCodes` | 1001/1002/1003/1004/1006/1015/5001/5004 常量 | **L1 冻结值**，集中一处便于对照 Go |
| `service/ArticleCreateService` | 转发 + 70s 超时 + gRPC 码映射 + status message 取 id | 单端点用例，独立变化原因 |
| `service/ArticleGetService` | 公开详情 + 作者详情**两个方法** | 两个端点共用一个 service（同为读），但**保留双检**语义 |
| `service/ArticleListService` | 列表 + 整包拒绝规则 | 同上 |
| `service/ArticleUpdateService` | 两阶段 + optional 指针语义 | 独立变化原因 |
| `service/ArticleDeleteService` | 两阶段 + 不匹配不发 RPC | 独立变化原因 |
| `service/ArticleUploadService` | multipart 读取 + 转发；**返回非信封** | 契约特殊（无信封），必须能单独表达 |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `article.go` | `ArticleApiApplication` | ABSORB | 进程装配 |
| `article.go`（CORS） | `WebMvcConfig` | ADAPT | 机制换 Spring MVC |
| `config/config.go` | `config/ArticleApiProperties` | ADAPT | 绑定机制换 Spring |
| `svc/service_context.go`（Article/User） | `client/{ArticleRpcClient,UserRpcClient}` | NEW | 外部服务客户端 |
| `svc/service_context.go`（SecurityRpc） | — | — | 零调用点，**不迁移**（Go 侧死配置） |
| `routes.go` 块 1 | `interceptor/JwtAuthInterceptor` | NEW | 独立 Bean（密钥/路径不同） |
| `routes.go` 块 2 | 拦截器 public 前缀 | ADAPT | 复用 users 机制 |
| `routes.go` 块 3（10MiB） | `application.yml` | ADAPT | 配置替代 API |
| `routes.go`（70s 超时） | `ArticleCreateService` stub deadline | ADAPT | 仅 create 路径 |
| `handler/article/*.go`（7 个） | `controller/ArticleController`（单一） | MERGE | 同质薄适配层，无独立边界依据 |
| `types/types.go`（Req） | `dto/*Req` | SPLIT | 契约与校验独立 |
| `types/types.go`（Resp） | `dto/*Resp` + `dto/ArticleCard` | SPLIT | 同上 |
| `httpx.OkJson` / `SetOkHandler` | `advice/GlobalResponseAdvice` | ADAPT | Spring advice 等价 |
| 各 logic 的错误返回 | `advice/GlobalExceptionHandler` | ADAPT | — |
| `errmsg` 常量 | `service/ArticleErrorCodes` | REUSE | 值属 L1，集中在业务模块内（Go 亦有 `article/common/errmsg`） |
| `logic/helper.go`（uid + 投影 + 强转） | `service/ArticleApiSupport` | MERGE | 同属边界适配 |
| `logic/helper.go`（作者名补全 + 吞错） | `service/ArticleAuthorResolver` | SPLIT | 容错策略是业务决策 |
| `logic/create_article_logic.go` | `service/ArticleCreateService` | SPLIT | 业务规则与异常→码的落点分离 |
| `logic/get_article_logic.go` | `service/ArticleGetService` | MERGE | 与作者详情同 service 两方法 |
| `logic/get_author_article_logic.go` | `service/ArticleGetService` | MERGE | 同上 |
| `logic/list_articles_logic.go` | `service/ArticleListService` | MERGE | — |
| `logic/update_article_logic.go` | `service/ArticleUpdateService` | MERGE | — |
| `logic/delete_article_logic.go` | `service/ArticleDeleteService` | MERGE | — |
| `logic/upload_image_logic.go` | `service/ArticleUploadService` + advice 排除项 | SPLIT | 非信封契约需单独表达 |
| `metrics/metrics.go` | `metrics/ArticleApiMetrics` | ADAPT | Micrometer |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 7 条路由的 method/path/中间件分组/70s/10MiB；全部错误码 1001/1002/1003/1004/1006/1015/5001/5004；`optional` 字段可选性 |
| L2 业务语义有承载位置 | ✅ gRPC 码映射表、status message 取 id、公开路径 `PublicOnly=true`、非 PUBLISHED→1002、列表整包拒绝→5001、两阶段作者校验（不匹配不发 RPC）、optional 指针语义 |
| L3 技术语义有承载方案 | ✅ 70s 调用超时；两阶段读写顺序；作者名补全的**吞错**策略 |
| 业务分支与异常路径已纳入 | ✅ 5 条 gRPC 码分支；`/upload` 读失败 5004；框架错误 |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；CORS → `WebMvcConfig`；10MiB → 配置 |
| 复用组件已明确 | ✅ `common/response/Response`、`common/observability`（HTTP span）、`common/snowflake`（无直接需求，仅列备查） |
| 新增文件有职责依据 | ✅ 拦截器（独立密钥）、`ArticleAuthorResolver`（容错策略）、`ArticleErrorCodes`（L1 冻结值）—— 均非"Go 有同名文件" |
| 合并有组织依据 | ✅ 7 handler → 1 Controller（同质薄适配层）；两阶段用例各自独立而非堆进一个大 Service |
| 拆分可解释 | ✅ 作者名补全独立、错误码独立、上传非信封独立 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `Types.java` / `ServiceContext.java` |
| 未把未实现能力说成现有机制 | ✅ RAG `article-api` 现为空骨架已在 §12.1 声明 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **路由前缀与 method 约定未拍板** | Go 为 `/v1/article` + GET/POST/PUT/DELETE 混合；RAG 的 users 已偏离为 `/user/v1` + 统一 POST（AGENTS.md §3.4 留档）。本文件按 Go 契约推导；是否跟随 RAG 惯例需用户确认（属 wire 契约变更） |
| 2 | **`article_status_total` 等指标在 api 侧无触发点** | Go 的 `api/internal/metrics/metrics.go` 已定义但 article-api 无任何调用点；是否迁移需拍板 |
| 3 | **`update_article_logic.go:90` 编译疑点（Go 侧）** | `__.ArticleStatus(req.Status)` 使用生成包别名但该文件未 import。迁移 `Status` 字段映射前需确认 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `SecurityRpc` 的 api 侧用途 | 已 wire 零调用；可能是预留给上传前的图片预检 |
| `related_game_id` | 贯穿 proto/api/logic，但 rpc 侧不按它过滤 |
