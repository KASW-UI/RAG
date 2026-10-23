# comment-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/comment/api`
> 目标根包：`com.rag.comment.comment.api`
> 推导日期：2026-10-23
> 状态：**理论推导**。RAG 侧 `service/comment/comment-api` 目前**只有 `pom.xml`、0 行 Java**，
> 本文件的每一项「RAG 已有 X」断言都给出实际打开过的路径与行号；本文件本身不实施任何代码。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

| RAG 位置 | 实测状态 | 对本文件的约束 |
|---|---|---|
| `service/comment/comment-api/pom.xml` | **存在，但 `<dependencies>` 为空**（实测只有 `<parent>` 8 行 + `<artifactId>comment-api</artifactId>` 12 行） | 缺 `comment-proto` / `spring-boot-starter-web` / `common` / `grpc-client-spring-boot-starter` ⇒ §12.5 的文件树**一行都编译不过**，实现前必须先补 pom |
| `service/comment/comment-api/src/{main,test}/{java,resources}` | 目录存在，`find -type f` **零命中** | 无任何既有类可复用，也**不存在**「已经这样组织过」的先例 |
| `service/comment/comment-proto/` | **已完成**：`src/main/proto/comment.proto` + `pom.xml:73` 的 `protobuf-maven-plugin`；实测产出 `com/rag/comment/comment/proto/CommentServiceGrpc.java` 等 | L1 wire 契约（7 个 RPC + 4 个 enum + 11 个 message）已就位，见下方 proto 偏离 |
| `service/comment/comment-common/pom.xml` | **空依赖、0 行 Java** | `comment` 域的 errmsg 落点，见 `FileTree/comment/common.md` |
| `service/comment/pom.xml:14-19` | 已列 `comment-proto` / `comment-api` / `comment-rpc` / `comment-common` **四个 `<module>`** | 模块拓扑**已定**，本文件不提出第 5 个模块 |

### 参照的已落地 api 模块（包名集合已定型）

| 参照 | 提供的一致性依据 |
|---|---|
| `service/users/user/user-api` | `@RestController` + `@RequestMapping("/user/v1")`（`controller/UserController.java:42-53`）；`advice/GlobalResponseAdvice`（`:57` 的 `body instanceof Response` 放行、`:70-73` 的非 JSON 透传）；`advice/GlobalExceptionHandler`（`:49-56` 业务异常、`:82-93` gRPC、`:163-167` 400 text/plain）；`interceptor/JwtAuthInterceptor`（`:65-67` public 集、`:138-153` claim `userId` 必须 `instanceof Number`、`:146` 写 request attribute）；`config/WebMvcConfig.java:57-58` 的 `addPathPatterns("/user/v1/**")`；`config/UserApiProperties.java:18,23-24`；`client/UserRpcClient.java:23-24` 的 `@GrpcClient` 字段注入；`service/UserApiSupport.java:34` 的 `@Component` helper |
| `service/users/admin/admin-api`、`service/message/message-api`、`service/article/article-api` | 三份平行证明：api 层包名集合 `{advice, client, config, controller, dto, interceptor, metrics, service}` 已稳定 |
| `service/common` | `response/Response.java:14-18`（`{int code; String msg; Object data;}`）；`exception/BusinessException.java:26,30-33`（unchecked + `(int, String)`）；`spring/HttpMiddlewareFilter.java:36-38`（`@Component` + `Filter`，HTTP span 全局挂载）；`http/GoStyleJsonBodyConverter*.java`、`http/GoStyleRouterErrorResponses.java`（go-zero 解析层对齐组件） |

**本文件不提出集合外的任何包**，且**不为不存在的职责建空包**：
Go 侧 `comment/api/` **没有 `internal/metrics/`**（实测 `find` 21 个文件无 metrics），
因此本模块**不推导 `metrics/` 包**（见 §12.7 不通过项 #6）。

### 已有组件（跨模块可复用，逐条给出打开过的路径）

- **`Response` 信封 + 统一 advice**：`com.rag.common.response.Response`（`Response.java:14-18`）+
  `GlobalResponseAdvice`（`:74` 成功时 `new Response(200, "OK", body)`）。
  ⚠️ `GlobalResponseAdvice.java:57` 的 `body == null || body instanceof Response` 放行**必须保留**
  —— 否则错误响应会变成 `{"code":200,...}`（该文件 `:30-39` 已把它锁成回归用例）。
- **`BusinessException(int, String)`**：`BusinessException.java:30-33`，
  与 Go `errmsg.CodeError{Code,Msg}`（`service/comment/common/errmsg/errmsg.go:10-13`）形状同构。
- **gRPC 下游客户端模式**：`client/UserRpcClient.java:23-24`。
- **HTTP span / 超时**：`spring/HttpMiddlewareFilter.java:36-38`（`@Component`，api 侧零新增代码）。
- **JWT 校验算法与密钥 padding**：`users-common` 的 `JwtUtil.padToHmacKeySize`（被
  `JwtAuthInterceptor.java:85` 调用），jjwt 强制 ≥32 字节。

### 已确认的框架机制

- Spring Boot **4.0.8** / Java **21**（根 `pom.xml:9,22`）——Boot 4 下 `Jackson2ObjectMapperBuilderCustomizer`
  已随自动配置模块化消失（`WebMvcConfig.java:33-45` 的注释即实测记录），coercion 只能在
  `JsonMapperBuilderCustomizer` 上配。
- `net.devh:grpc-client-spring-boot-starter` 的 `@GrpcClient` + Nacos（`UserRpcClient.java:13-14`）。
- `@RestControllerAdvice` + `ResponseBodyAdvice` / `@ExceptionHandler`。
- `HandlerInterceptor` + `WebMvcConfigurer`（`WebMvcConfig.java:55-59`）。
- `@ConfigurationProperties` + `@NestedConfigurationProperty`（`UserApiProperties.java:18,23`）。
- Micrometer（**本模块不需要**——Go 侧无指标）。
- **无 WebFlux、无 Spring Security、无 Redis 依赖（comment-api 的 Go 侧无 blacklist）**。

### 🔴 L1 契约风险点 1：proto `package` 归一（`SD-2026-10-23-04`）

| 侧 | 常量 | 位置 | gRPC 方法全名 |
|---|---|---|---|
| Go | `ServiceName: "pb.CommentService"` | `service/comment/rpc/pb/comment_grpc.pb.go:316` | `/pb.CommentService/CreateComment` |
| RAG | `SERVICE_NAME = "comment.CommentService"` | `service/comment/comment-proto/target/generated-sources/protobuf/grpc-java/com/rag/comment/comment/proto/CommentServiceGrpc.java:15` | `/comment.CommentService/CreateComment` |

依据：`docs/Intentional_Deviations/comment/proto.md:18-31`；`.proto` 本体
`service/comment/comment-proto/src/main/proto/comment.proto:3`（`package comment;`）vs
`Sea-RideTheWind_golang/proto/comment.proto:4`（`package pb;`）。

⇒ **`client/CommentRpcClient` 拨出的 `:path` 前缀与 Go 侧不同**（wire 层可见，HTTP/2 头）。
当前 RAG 侧无任何对端调用 ⇒ 无实际影响；**但一旦做双侧活体对拍，必然 `UNIMPLEMENTED`**。
处理要求（三选一，实现时必须显式写进 §3.2 留痕）：
① RAG 侧 proto `package` 改回 `pb`（**违反已签字偏离，不得擅自改**）；
② 对拍时 Go 侧同时注册 `comment.CommentService` 别名；
③ 在 `CommentRpcClient` 侧用自定义 `MethodDescriptor` 重写 `fullMethodName`。
本文件按**已签字决策**（保持 `/comment.`）推导，并把对拍方案列为 §12.7 的 `UNKNOWN`。

### 🔴 L1 契约风险点 2：业务码塞不进 `grpc-status`（平台级硬限制）

Go rpc 侧全程用 `errmsg.NewGrpcErr(code, msg)` = `status.Error(codes.Code(code), msg)`
（`errmsg.go:33-35`；comment 侧调用点见 `rpc/internal/logic/create_comment_logic.go:66,73,82,89,98,122,130`），
业务码取值域为 1001~2001 / 5001~5010 / 1012 / 1013 —— **全部 > 16**。
grpc-go 的 `codes.Code` 是 `int32` 命名类型、**不做范围校验**，服务端写 trailer 时逐字输出
`grpc-status: 2001`。

**grpc-java 做不到**：`Status` 只认 17 个标准码，`Status.fromCodeValue(2001)` 产出
`UNKNOWN / value=2 / desc="Unknown code 2001"`（实测结论见
`service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeGrpcError.java:36-48`）。
⇒ **api 侧那条 `int(st.Code())` 的「原样透传」在 Java 里无法复刻**。

先例已经把这道题解完了：`LikeGrpcError.java:78-82` 用自定义 trailer
`rag-like-biz-code`（ASCII marshaller）承载业务码，`:110-115` 把业务码映射到**不在熔断失败名单内**的
标准码（`ERROR_INPUT_WRONG → INVALID_ARGUMENT`，其余 → `UNKNOWN`），
`:53-64` 明确写下「净差异 = 仅 `grpc-status` 字段数值不可复原」。
⇒ comment 域**必须复用同一形状**（`CommentGrpcError`，详见 `FileTree/comment/common.md`），
且 `CommentGlobalExceptionHandler` **不能**照抄 `GlobalExceptionHandler.mapRPCStatusToCode`
（`user-api/advice/GlobalExceptionHandler.java:100-110` 是**按标准码 switch 映射**，对 comment 是错的）。

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **路由前缀** | 🔴 Go 是 `rest.WithPrefix("/comment/v1")`（`routes.go:55`）+ 路径本身已含 `/comment/`（`routes.go:20,25,30,35,40,45`）⇒ **实际路径里 `comment` 出现两次**：`/comment/v1/comment/create`、`/comment/v1/comment/delete`、`/comment/v1/comment/like`、`/comment/v1/comment/list`、`/comment/v1/comment/manage`、`/comment/v1/comment/report`；第 7 条是 `/comment/v1/subject/state`（`routes.go:50`，**不含**第二个 `comment`）。本文件**按 Go 原样保留重复段**（§3.2：不得"顺手改好"），但**是否修正需拍板** |
| **public 路径集** | ✅ **空集**。`routes.go:54` 的 `rest.WithJwt` 装饰整个 `AddRoutes` 块（唯一一块），7/7 全部要 JWT。⇒ `CommentJwtAuthInterceptor.PUBLIC_PATHS = Set.of()` |
| **JWT 密钥** | ✅ 与 `like` / `message` / `article` 的 api **完全同值** `ae0536f9-6450-4606-8e13-5a19ed505da0`（实测 `comment/api/etc/commentservice.yaml:6`、`like/api/etc/likecenter.yaml:6`、`message/api/etc/messagecenter.yaml:11`、`article/api/etc/article-api.yaml:27`）⇒ 与 user-api 签发的那把钥匙是同一把，**必须同密钥**才能验签通过 |
| **Redis blacklist** | 🔴 Go 侧**没有** `CheckBlacklistMiddleware`（`routes.go` 全文只有 `WithJwt` 一个装饰器）。若 Java 侧照抄 `JwtAuthInterceptor.java:199-209` 的 blacklist 分支 = **新增行为**。本文件**默认不引入**，列为 §12.7 待拍板 |
| **`Telemetry` / `Log` 配置段** | 🔴 `commentservice.yaml:9-18` 有 `Log{ServiceName:commentcenter,Mode:file,Path:...}` 与 `Telemetry{Name:comment-api,Endpoint:jaeger:4317,...}`，但 `internal/config/config.go:11-18` 的 `Config` 结构体**只有 3 个段**（`rest.RestConf` + `UserAuth` + `CommentRpc`）⇒ 未知键在 `conf.MustLoad` 下是报错还是被忽略，取决于 go-zero 库实现（**不在本仓库**）⇒ `UNKNOWN`。RAG 侧后果明确：`Telemetry.Name` → `otel.service.name`；日志落盘路径无对应物 |
| **`NonBlock: true`** | `commentservice.yaml:23`。go-zero 客户端非阻塞（连接在后台建立）。Java 侧对应 `grpc.client.comment-rpc.enable-keep-alive` 一类参数，**无严格等价开关** ⇒ 记为表达层适配，不建独立组件 |
| **无 DevServer / 无 `/metrics` 端口** | ✅ 与 like-api 不同：`commentservice.yaml` **没有** `DevServer` 段，comment-api 也不监听任何 metrics 端口（对比 `comment/rpc/comment.go:38-41` 的 9091，那是 rpc 侧）。⇒ 本模块无 metrics 相关职责 |

---

## 12.2 第二部分：Go 原始文件树

```
service/comment/api/                                     # 实测 21 个文件 = 20 个 .go + 1 个 yaml
├── commentservice.go                       # 入口：关原生超时 + HTTP span + Ok/Error envelope + 建 ctx + 注册路由
├── etc/commentservice.yaml                 # Name/Host/Port 8888/UserAuth/Telemetry/Log/CommentRpc(NonBlock)
└── internal/
    ├── config/config.go                    # Config = rest.RestConf + UserAuth{AccessSecret,AccessExpire} + CommentRpc
    ├── svc/servicecontext.go               # 唯一依赖：zrpc.MustNewClient(CommentRpc) → CommentServiceClient
    ├── handler/
    │   ├── routes.go                       # 1 个 AddRoutes 块、7 条 POST、WithJwt(全量) + WithPrefix("/comment/v1")
    │   └── comment/*.go                    # 7 个 handler，**逐字节同构**（实测 md5 归一后 7 个完全相同）
    │       ├── createcommenthandler.go
    │       ├── deletecommenthandler.go
    │       ├── getcommenthandler.go
    │       ├── likecommenthandler.go
    │       ├── managecommenthandler.go
    │       ├── reportcommenthandler.go
    │       └── updatesubjectstatehandler.go
    ├── logic/comment/
    │   ├── helper.go                       # extractUserID(ctx)：6 种类型分支 + 2 类失败
    │   ├── createcommentlogic.go           # uid + 转发 + SubjectCount 恒 0
    │   ├── deletecommentlogic.go           # uid + 转发 + Success 硬编码 true
    │   ├── getcommentlogic.go              # **无 uid**；参数兜底 4 条 + 递归 proto→DTO 投影
    │   ├── likecommentlogic.go             # uid + 转发 + Success 透传
    │   ├── managecommentlogic.go           # uid + 转发 + ManageType 窄化
    │   ├── reportcommentlogic.go           # uid + 转发 + ReportReason 窄化
    │   └── updatesubjectstatelogic.go      # uid + 转发 + SubjectState 窄化
    └── types/types.go                      # 15 个 DTO（7 Req + 7 Resp + CommentItem + SubjectInfo）

shared（被 api 与 rpc 共用，见 FileTree/comment/common.md）:
service/comment/common/errmsg/errmsg.go     # 24 个常量 + 24 条文案表 + CodeError + 3 个工厂
```

---

## 12.3 第三部分：Go 文件职责分析

### `commentservice.go`（进程装配，67 行）

- `:31` `conf.MustLoad` → `:32` `logger.Init(c.Name)` → `:34` `observability.DisableNativeRestTimeout(&c.RestConf)`
  → `:35` `rest.MustNewServer` → `:36` `server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, SlowThreshold()))`
  → `:37` `defer server.Stop()`
- `:39-45` **`SetOkHandler`**：成功一律 `{Code: errmsg.Success(200), Msg: GetErrMsg(200)="OK", Data: v}`。
- `:47-60` **`SetErrorHandler`**：
  - `:48-49` 默认 `code = errmsg.ErrorServerCommon(5001)`、`msg = GetErrMsg(5001) = "系统内部错误"`
  - `:50-54` `errors.As(err, &e)` 成功 → 用 `e.Code` / `e.Msg`
  - `:55` **两分支都返回 `http.StatusOK`**，`Data: nil`
  ⇒ **L1 冻结：所有响应的 HTTP 状态码恒 200**，业务成败只体现在 body 的 `code`。
- `:62-63` `svc.NewServiceContext(c)` + `handler.RegisterHandlers`。

**L4**：Spring 侧 = `CommentGlobalResponseAdvice`（成功包信封）+ `CommentGlobalExceptionHandler`（失败直接产出 `Response`）。
⚠️ 但**不能**照抄 `user-api/advice/GlobalExceptionHandler.java:294-296` 的「无条件 200」就完事——
那是因为 users 侧所有错误都已是 `BusinessException`；comment 侧有一类错误**不是** `CodeError`（见下）。

### 🔴 `internal/handler/routes.go`（L1 主契约，57 行）

7 条**全部 POST**（`:19,24,29,34,39,44,49`）：

| # | 行号 | `Path` | 叠加前缀后的完整路径 | 装饰器 |
|---|---|---|---|---|
| 1 | `:19-21` | `/comment/create` | `/comment/v1/comment/create` | `WithJwt` + `WithPrefix`（`:54-55`） |
| 2 | `:24-26` | `/comment/delete` | `/comment/v1/comment/delete` | 同上 |
| 3 | `:29-31` | `/comment/like` | `/comment/v1/comment/like` | 同上 |
| 4 | `:34-36` | `/comment/list` | `/comment/v1/comment/list` | 同上 |
| 5 | `:39-41` | `/comment/manage` | `/comment/v1/comment/manage` | 同上 |
| 6 | `:44-46` | `/comment/report` | `/comment/v1/comment/report` | 同上 |
| 7 | `:49-51` | `/subject/state` | `/comment/v1/subject/state` | 同上 |

- **无** `WithTimeout`、**无** `WithCustomCors`、**无** maxBytes、**无** 公开路由。
- `AddRoutes` 只有一个块 ⇒ `WithJwt` 覆盖 7/7，**public 集为空集**。

### `internal/handler/comment/*.go`（7 个，各 32 行）

**实测逐字节同构**：把 RPC 名归一后 7 个文件的 md5 **完全相同**（`0b1988395c00a6a1f2538f4ba4b1e9e9`）。
统一形状：`httpx.Parse(r,&req)` → 失败则 `httpx.ErrorCtx` 直接返回（`:19-22`）→
`New*Logic(ctx, svcCtx)` → 调 logic → `err != nil ? ErrorCtx : OkJsonCtx`（`:26-30`）。
- handler **不做任何校验**：畸形 JSON 由 `httpx.Parse` 报错。因为 `commentservice.go:47` 已注册错误处理器，
  这类错误**不会**落到 go-zero 默认的 `http.Error(400)`，而是走 envelope ⇒ **HTTP 200 + 业务码**。
- ⇒ 7 个 handler **无独立依赖边界、无独立生命周期、无独立契约** ⇒ 合并为 1 个 Controller 有充分依据（§7.2）。

### `internal/logic/comment/helper.go`（30 行，`extractUserID`）

`:11` `ctx.Value("userId")`；`:12-14` nil → `error("ctx userId is nil")`；
`:16-29` 类型 switch：`json.Number → Int64()`（`:18`，非整数字符串会报错）/ `string → ParseInt`（`:20`）/
`int64 → 原样`（`:22`）/ `int → int64`（`:24`）/ `float64 → int64(value)`（`:26`，**静默截断**）/
`default → error("ctx userId has unexpected type %T")`（`:28`）。

**消费方实测**：6 个 logic 调它（`create:35`、`delete:35`、`like:35`、`manage:35`、`report:35`、
`updateSubjectState:35`），**`getcommentlogic.go` 不调**。

**L2 关键**：`getcommentlogic.go` 不提取 uid ⇒ **7 个端点里有 1 个（`/comment/list`）在 api 层不做身份绑定**。
即使 JWT 解析出了 `userId`，该端点也从不把它写进 rpc 请求（`GetCommentReq` proto 里**也没有** `user_id` 字段，
见 `proto/comment.proto:134-147`）。这不是缺陷，是显式设计。

### 6 个「uid + 转发」logic 的统一模板

以 `createcommentlogic.go` 为例，`delete/like/manage/report/updateSubjectState` 逐条同构：

1. `:35-39` `extractUserID` 失败 → `logger.LogBusinessErr(ErrorTokenRuntime=1006, ...)` → `return nil, errmsg.NewErrCode(1006)`
   ⇒ **失败响应 = `{code:1006, msg:"TOKEN已过期", data:null}` + HTTP 200**
   （文案取自 `common/errmsg/errmsg.go:86` `ErrorTokenRuntime: "TOKEN已过期"`）。
   ⚠️ 注意：文案是**「TOKEN已过期」**，与 `jwt_auth.go` 401 的语义不同层，这里是「context 里没有可解析的 userId」。
2. 构造 `pb.XxxReq`：把 uid 注入 `user_id`，其余字段 1:1 直传。
3. 调 `CommentCli.Xxx(ctx, rpcReq)`。
4. **错误分支（6 个 logic 逐字相同）**：
   ```go
   st, ok := status.FromError(err)
   if ok { return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message()) }   // ← 业务码原样回填
   logger.LogBusinessErr(l.ctx, errmsg.ErrorServerCommon, err)
   return nil, errmsg.NewErrCode(errmsg.ErrorServerCommon)                    // ← 5001 兜底
   ```
   例：`create:51-58`、`delete:48-55`、`like:49-56`、`manage:50-57`、`report:52-59`、`updateSubjectState:48-55`。
5. 成功分支做 proto→DTO 投影。

### `internal/logic/comment/getcommentlogic.go`（135 行）—— **api 侧唯一的异类**

1. `:34-36` `strings.TrimSpace(req.TargetType) == ""` → `status.Error(codes.InvalidArgument, "target_type is required")`
2. `:37-39` `target_id` 同款
3. `:42-44` `sortType != 0 && != 1` → **回退成 0**
4. `:47-49` `page <= 0` → 1；`:50-52` `pageSize <= 0` → 20；`:53-55` `pageSize > 50` → **50**
5. `:58-60` `rootId < 0` → **0**
6. `:64-71` 调 rpc（6 字段直传）
7. `:72-75` **`return nil, err`** —— 直接把 rpc 的错误原样上抛，**不做 `status.FromError` 解包**
8. `:78-80` `Comment: make([]types.CommentItem, 0)`（⇒ 空列表序列化成 `[]` 而不是 `null`）
9. `:83-93` subject 投影；**`:91` `OwnerId: 0` 硬编码**（不读 `GetOwnerId()`）
   —— 与 rpc 侧一致：rpc 的 `pb.SubjectInfo` 构造（`rpc/internal/logic/get_comment_logic.go:152-159`）也没设 `owner_id`，两端都是 0。
   ⇒ **`SubjectInfo` 永远是 7 个零值字段的对象，不会是 `null`**（`types.go:59` 是值类型不是指针）。
10. `:106-135` `convertPbCommentItemToAPI` **递归**映射 `Children`；`:124` 与 `:128` 都 `make([]CommentItem, 0)`
    ⇒ `children` 字段恒为 `[]`。
    ⚠️ 但 rpc 侧 `get_comment_logic.go:141` 恒 `Children: nil` ⇒ **递归分支在当前链路上永不触发**，属于死但必须保留的代码。

**🔴 这个异类的可观察后果（api 侧最容易被实现错的一处）**：
`:35/:38/:74` 抛出的都是 **gRPC `status.Error`，不是 `*errmsg.CodeError`**。
`commentservice.go:51` 的 `errors.As(err, &e)` 对它们**返回 false**
⇒ 全部落到 `:48-49` 的兜底 ⇒ 客户端看到的是 **`{code:5001, msg:"系统内部错误"}`，HTTP 200**。
也就是说：`/comment/list` 的 `target_type` 为空，**不会**得到 2001，也不会得到 gRPC 的 `InvalidArgument(3)`，
而是 **5001**。这与另外 6 个端点「业务码原样透传」的行为**完全不同**。
Java 侧若照抄 `user-api` 的 `mapRPCStatusToCode`（`GlobalExceptionHandler.java:100-110`），
`INVALID_ARGUMENT` 会被映成 `500` —— 同样错。

### 6 个 logic 的响应投影差异（必须逐条保留）

| logic | `Success` 字段来源 | 行号 |
|---|---|---|
| `createcommentlogic` | 无 `success` 字段；`SubjectCount` 直接取 `rpcResp.SubjectCount`（**rpc 侧硬编码 0**，`rpc/internal/logic/create_comment_logic.go:139`） | `create:60-64` |
| `deletecommentlogic` | 🔴 **`Success: true` 硬编码**（不读 rpc） | `delete:57-60` |
| `likecommentlogic` | `Success: rpcResp.Success`（透传） | `like:58-61` |
| `managecommentlogic` | `Success: rpcResp.Success`（透传） | `manage:59-61` |
| `reportcommentlogic` | `Success: rpcResp.Success`（透传） | `report:61-63` |
| `updatesubjectstatelogic` | `Success: rpcResp.Success`（透传） | `update:57-59` |

⇒ **`DeleteCommentResp.success` 恒为 `true`，其余四个恒等于 rpc 回值**。这是显式的不对称，不是笔误。

### 3 处无校验的 enum 窄化

- `manage:46` `ActionType: pb.ManageType(req.ActionType)` —— `int32 → enum`，**无校验**；
  api DTO 是 `ActionType int32`（`types.go:78`），proto 是 `ManageType`（`comment.proto:194-202`）。
  非法值（如 `7`）会**原样穿过 api**，由 rpc 侧 `manage_comment_logic.go:68-86` 的 `default` 分支拒成 2001。
- `report:46` `Reason: pb.ReportReason(req.Reason)` —— 同款（`types.go:89` `Reason int32`）。
- `update:44` `State: pb.SubjectState(req.State)` —— 同款（`types.go:110` `State int32`）。

⚠️ proto3 里这三个枚举的 0 值分别是 `UNKNOWN` / `REASON_UNKNOWN` / `SUBJECT_OPEN`，
**Java 生成代码对未知数字值用 `UNRECOGNIZED`**（不是抛异常），直接透传即可，**不要**在 api 层加枚举白名单校验。

### `internal/types/types.go`（115 行）

15 个 DTO。**全部 tag 是裸 `json:"..."`**——实测**零个** `optional`、**零个** `options=`、
**零个** `,string`。
⇒ 与 like 域相反（`like/api/internal/types/types.go:59-60` 有 `options=1|2|3|4` 与 `,string`），
**comment 的 `int64` 在 JSON 里是数字字面量，不是字符串**。DTO 不得用 `Long` + `@JsonFormat(shape=STRING)`。
- `CommentItem.Children`（`:19`）与 `SubjectInfo`（`:97-105`）被多个 Resp 复用。
- `GetCommentResp.Subject` 是**值类型**（`:59`）⇒ 永不为 `null`。

### `internal/svc/servicecontext.go`（24 行）

`:19` `zrpc.MustNewClient(c.CommentRpc)` → `:22` `commentpb.NewCommentServiceClient(conn.Conn())`。
**无 Redis、无 DB、无 Kafka** ⇒ comment-api 是**纯转发网关**，与 like-api 同型。

### `internal/config/config.go` + `etc/commentservice.yaml`

`Config` 只有 3 段（`config.go:11-18`），yaml 却有 6 段（多出 `Log` / `Telemetry`）。
⇒ §12.1 的 `UNKNOWN` 条目。RAG 侧 `CommentApiProperties` 只需承接 `UserAuth` 与 `CommentRpc` 寻址。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `commentservice.go:31,35,62,66` 进程装配 | `CommentApiApplication` | ABSORB | Spring Boot 启动（`user-api/UserApiApplication.java:21-31` 同型） |
| `commentservice.go:34` `DisableNativeRestTimeout` | 不做（框架无等价默认值） | ABSORB | 语义自动满足；超时改由 `HttpMiddlewareFilter` 的 `httpTimeout` 承载 |
| `commentservice.go:36` HTTP span | `common/spring/HttpMiddlewareFilter` | REUSE | `HttpMiddlewareFilter.java:36-38` 已 `@Component` 全局注册，本模块零新增 |
| `commentservice.go:39-45` `SetOkHandler` | `advice/CommentGlobalResponseAdvice` | ADAPT | `beforeBodyWrite` 包 `new Response(200,"OK",body)`；**必须对 `Response` 与 `null` 放行**（`user-api` 同款 `GlobalResponseAdvice.java:57`） |
| `commentservice.go:47-60` `SetErrorHandler` 的 `errors.As` 分支 | `advice/CommentGlobalExceptionHandler` + `common/exception/BusinessException` | ADAPT | `CodeError` → `BusinessException(code,msg)`；**HTTP 必须恒 200** |
| `commentservice.go:48-49` 的 5001 兜底 | 同上的兜底 `@ExceptionHandler` | ADAPT | 非 `BusinessException` / 非 status 的异常 → `Response(5001, "系统内部错误", null)` |
| `commentservice.go:50-54` + 6 个 logic 的 `status.FromError` 原样透传 | `advice/CommentGlobalExceptionHandler.handleRpc` + `common/errmsg/CommentGrpcError` 的 trailer | **ADAPT（带平台限制）** | 🔴 grpc-java 发不出 `grpc-status: 2001`（`LikeGrpcError.java:36-48` 实测）；须按 trailer 方案重建。**禁止**照抄 `user-api` 的 `mapRPCStatusToCode`（`GlobalExceptionHandler.java:100-110` 是按标准码 switch，对 comment 是错的） |
| `getcommentlogic.go:35,38,74` 的非 `CodeError` 错误 | 兜底分支 → 5001 | ADAPT | 必须保留「这三个错误最终呈现 5001」的现状，不得"改善"成 2001/3 |
| `config/config.go:11-18` `UserAuth` | `config/CommentApiProperties` | ADAPT | `@ConfigurationProperties("comment-api")` + `@NestedConfigurationProperty`（`UserApiProperties.java:18,23-24`） |
| `config/config.go:17` `CommentRpc` | `application.yml` 的 `grpc.client.comment-rpc.address` | ADAPT | 客户端寻址属配置，不进 Properties 类 |
| `svc/servicecontext.go:19-22` `CommentCli` | `client/CommentRpcClient` | ADAPT | `@GrpcClient("comment-rpc")` 字段注入（`UserRpcClient.java:23-24`） |
| `routes.go:54` `WithJwt`（7/7） | `interceptor/CommentJwtAuthInterceptor` + `config/WebMvcConfig` | ADAPT | **必须独立 Bean**：密钥虽然同值，但 public 集与 user-api 不同（这里是空集） |
| `routes.go:55` `WithPrefix("/comment/v1")` | `@RequestMapping("/comment/v1")` | ADAPT | 与 RAG `/user/v1`、`/admin/v1` 惯例同形；但**段内路径仍带 `/comment/`**（§12.7 #1） |
| `routes.go` 无 `WithTimeout` / 无 CORS / 无 maxBytes | 不加对应配置 | ABSORB | Go 侧无此装饰器，加了就是新增行为 |
| `handler/comment/*.go`（7 个） | `controller/CommentController`（单一） | **MERGE** | 实测 7 个文件归一后 md5 完全相同 ⇒ 同质、无独立边界（§7.2 判据 1/2/3 全中） |
| `types/types.go`（7 个 Req） | `dto/*Req` | SPLIT | 请求契约独立；**无校验 tag**，故 DTO 不带 `@NotNull`/`@Min` |
| `types/types.go`（7 个 Resp + `CommentItem` + `SubjectInfo`） | `dto/*Resp` + `dto/{CommentItem,SubjectInfo}` | SPLIT | 两个共享结构跨多个 Resp 复用；`SubjectInfo` 的值/空语义与 `CommentItem.children` 的 `[]` 语义必须各自可表达 |
| `helper.go:10-30` `extractUserID`（6 个消费方） | `service/CommentApiSupport.extractUserId` | MERGE | 6 个 service 共用同一段类型 switch，重复 6 份必然漂移（与 `user-api` 的 `UserApiSupport.java:34` 同形态） |
| 6 个 logic 的 gRPC 调用与错误上抛 | 各自 `service/XxxService` | MERGE | 每个端点一个单端点用例（与 `user-api` 的 9 个 per-endpoint service、`like/api.md` 的 6 个同形） |
| `getcommentlogic.go:42-60` 四条兜底 | `service/GetCommentService` 内部 | KEEP | 与该端点强绑定，不抽公共 |
| `getcommentlogic.go:106-135` `convertPbCommentItemToAPI` | `service/GetCommentService` 的私有方法 | KEEP | **仅 1 个调用方**（`getcommentlogic.go:99`）⇒ 按 §14.4 不塞进公共 Support |
| `getcommentlogic.go:83-93` subject 投影（含 `OwnerId:0`） | `service/GetCommentService` | KEEP | 同上 |
| 6 个 logic 的 uid 失败 → 1006 | `CommentApiSupport.extractUserId` 抛 `BusinessException(1006, "TOKEN已过期")` | ADAPT | `BusinessException.java:30-33` 形状同构 |
| Go 侧**无** `internal/metrics/` | **不创建 `metrics/` 包** | — | 按 §14.4「不得为了完整性创建空组件」。Go 侧 21 个文件实测无 metrics 目录 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/comment/comment/api/
├── CommentApiApplication.java
├── advice/
│   ├── CommentGlobalResponseAdvice.java
│   └── CommentGlobalExceptionHandler.java
├── client/
│   └── CommentRpcClient.java
├── config/
│   ├── CommentApiProperties.java
│   └── WebMvcConfig.java
├── controller/
│   └── CommentController.java
├── dto/
│   ├── CreateCommentReq.java
│   ├── CreateCommentResp.java
│   ├── DeleteCommentReq.java
│   ├── DeleteCommentResp.java
│   ├── GetCommentReq.java
│   ├── GetCommentResp.java
│   ├── LikeCommentReq.java
│   ├── LikeCommentResp.java
│   ├── ManageCommentReq.java
│   ├── ManageCommentResp.java
│   ├── ReportCommentReq.java
│   ├── ReportCommentResp.java
│   ├── UpdateSubjectStateReq.java
│   ├── UpdateSubjectStateResp.java
│   ├── CommentItem.java
│   └── SubjectInfo.java
├── interceptor/
│   └── CommentJwtAuthInterceptor.java
└── service/
    ├── CommentApiSupport.java
    ├── CreateCommentService.java
    ├── DeleteCommentService.java
    ├── GetCommentService.java
    ├── LikeCommentService.java
    ├── ManageCommentService.java
    ├── ReportCommentService.java
    └── UpdateSubjectStateService.java

resources/application.yml            # server.port=8888、comment-api.user-auth.*、grpc.client.comment-rpc
```

**共 25 个 Java 文件**（1 入口 + 2 advice + 1 client + 2 config + 1 controller + 16 dto + 1 interceptor + 8 service）。
**不创建** `metrics/`（Go 侧无此职责）、**不创建** `bootstrap/`（api 侧既有约定里无此包）。

> 对比 Go 的 21 个文件：Java 25 个文件不是"多算了"，而是**一个 Go 文件被拆到多个职责位置**
> （`types.go` → 16 个 DTO；`routes.go` → controller + interceptor + config 三个落点），
> 同时**一个 Go 文件被吸收**（`servicecontext.go` → `@GrpcClient` 字段注入）。
> 这正是 §14.1 说的「文件数量不是正确性指标」。

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `CommentApiApplication` | 入口，`scanBasePackages = {com.rag.comment.comment.api, com.rag.common}`，`@EnableConfigurationProperties`，`@EnableDiscoveryClient` | `commentservice.go:27-66` | 框架约定（`UserApiApplication.java:21-31` 同型）。⚠️ api 侧**必须**扫 `com.rag.common`（为了 `HttpMiddlewareFilter`）；这与 rpc 侧的禁令相反 |
| `advice/CommentGlobalResponseAdvice` | `beforeBodyWrite` 包 `Response(200,"OK",body)`；**必须对 `body == null \|\| body instanceof Response` 放行**；非 JSON contentType 原样透传 | `commentservice.go:39-45` | 二次包裹是**已实证缺陷**（`user-api/GlobalResponseAdvice.java:30-39` 把该场景锁成回归用例） |
| `advice/CommentGlobalExceptionHandler` | ① `BusinessException` → `Response(code,msg,null)` + **HTTP 200**；② `StatusRuntimeException` → **从 `CommentGrpcError` 的 trailer 取业务码**，取不到则兜底 5001；③ `HttpMessageNotReadable` → 400 + `text/plain`；④ 405/415 走框架级；⑤ 兜底 `Exception` → 5001 | `commentservice.go:47-60` + 6 个 logic 的错误分支 + `getcommentlogic.go:35,38,74` | 错误转换点是单一出口，7 个 service 不各自造轮子 |
| `client/CommentRpcClient` | `@GrpcClient("comment-rpc")` 阻塞 stub | `svc/servicecontext.go:19-22` | 外部服务客户端，`client/` 有三份先例。⚠️ 见 §12.1 风险点 1：`/comment.CommentService/*` 与 Go 的 `/pb.CommentService/*` 前缀不同 |
| `config/CommentApiProperties` | `user-auth.access-secret` / `access-expire` | `config/config.go:13-16` + `commentservice.yaml:5-7` | 配置独立关注点 |
| `config/WebMvcConfig` | ① `addInterceptor(CommentJwtAuthInterceptor).addPathPatterns("/comment/v1/**")`；② 注册 `GoStyleCoercion` 的 `JsonMapperBuilderCustomizer` | `routes.go:54-55` | 与 users 同款。②的必要性见下 |
| `controller/CommentController` | `@RequestMapping("/comment/v1")` + **7 个 `@PostMapping`**：`/comment/create`、`/comment/delete`、`/comment/like`、`/comment/list`、`/comment/manage`、`/comment/report`、`/subject/state` | 7 个 handler + `routes.go` | **7 个 Go handler 不拆 7 个 Controller**：实测逐字节同质，拆开只制造 7 个单方法类。⚠️ 前 6 个方法的路径里**必须带第二个 `/comment/`** |
| `dto/*Req`（7 个） | 请求契约。**不带任何 Bean Validation 注解**（Go 侧 `types.go` 零校验 tag）；`int64` 用 `Long` 且**序列化为 JSON 数字** | `types.go:22-29,37-41,48-55,62-67,74-79,85-91,107-111` | 请求契约与响应不同 |
| `dto/*Resp` + `CommentItem` + `SubjectInfo` | 响应契约。`DeleteCommentResp.success` 恒 `true`；`GetCommentResp.subject` 是**值类型**，永不为 `null`；`CommentItem.children` 恒 `[]` | `types.go:6-20,31-35,43-46,57-60,69-72,81-83,93-95,97-105,113-115` | 两个共享结构跨 Resp 复用 |
| `interceptor/CommentJwtAuthInterceptor` | JWT 验签（HS256，密钥 `ae0536f9-…`，jjwt `padToHmacKeySize`）→ `request.setAttribute("userId", Long)`；**`PUBLIC_PATHS = Set.of()`** | `rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)`（`routes.go:54`） | **必须独立 Bean**（即使密钥同值，public 集与 user-api 的两元素集不同）。claim 必须是 `Number`，否则 401（`JwtAuthInterceptor.java:138-153` 同款） |
| `service/CommentApiSupport` | `extractUserId(HttpServletRequest)`：从 request attribute 取 uid，失败抛 `BusinessException(1006,"TOKEN已过期")` | `helper.go:10-30` + 6 个 logic 的 `:35-39` | **6 个消费方**的同款类型 switch，合并在一处才不会各自演化 |
| `service/CreateCommentService` | uid + 转发 + `SubjectCount` 透传 | `createcommentlogic.go:34-64` | 单端点用例，Req/Resp 契约独立演进 |
| `service/DeleteCommentService` | uid + 转发 + **`Success` 硬编码 `true`** | `deletecommentlogic.go:34-60` | 同上 |
| `service/GetCommentService` | **无 uid**；4 条参数兜底；调 rpc（错误原样上抛）；递归 `convertPbCommentItemToAPI`；subject 投影（`OwnerId: 0`） | `getcommentlogic.go:33-135` | 与其余 6 个 service 分开的**硬依据**：本端点在 api 层不做身份绑定，且错误路径完全不同（最终呈现 5001） |
| `service/LikeCommentService` | uid + 转发 + `Success` 透传 | `likecommentlogic.go:34-61` | 单端点用例 |
| `service/ManageCommentService` | uid + 转发 + `ManageType` 无校验窄化 | `managecommentlogic.go:34-61` | 同上 |
| `service/ReportCommentService` | uid + 转发 + `ReportReason` 无校验窄化 | `reportcommentlogic.go:34-63` | 同上 |
| `service/UpdateSubjectStateService` | uid + 转发 + `SubjectState` 无校验窄化 | `updatesubjectstatelogic.go:34-59` | 同上 |

### 🔴 `config/WebMvcConfig` ② 的必要性（Boot 4 的实测结论）

Go 侧的 `httpx.Parse` 用 go-zero 的 `mapping.UnmarshalJsonMap` 反序列化，对**标量强转**与**浮点截断**有自己的一套规则
（`user-api/controller/UserController.java:83-88` 记录了实测经过）。
Boot 4 的 Jackson 3 **只能在 builder 上配 coercion**（`user-api/config/WebMvcConfig.java:33-45`：
`Jackson2ObjectMapperBuilderCustomizer` 已随自动配置模块化消失、`ObjectMapper` 上没有 `coercionConfigFor`）。
⇒ api 模块必须自带 `JsonMapperBuilderCustomizer`（`WebMvcConfig.java:46-49` 已给出 `GoStyleCoercion::applyTo` 的现成写法），
否则 `CommentItem.content` 之类字段的解析行为会与 Go 不同。

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `commentservice.go:27-66` | `CommentApiApplication` | ABSORB | 进程装配由框架承担 |
| `commentservice.go:34` 禁用原生 REST 超时 | — | ABSORB | Spring MVC 无等价默认超时，语义自动满足；超时由 `HttpMiddlewareFilter` 的 `httpTimeout` 承载 |
| `commentservice.go:36` HTTP span | `common/spring/HttpMiddlewareFilter` | REUSE | `HttpMiddlewareFilter.java:36-38` 已 `@Component`，api 零新增 |
| `commentservice.go:39-45` `SetOkHandler` | `advice/CommentGlobalResponseAdvice` | ADAPT | `{200,"OK",data}` 语义等价；须放行 `Response`/`null` |
| `commentservice.go:47-60` `SetErrorHandler` | `advice/CommentGlobalExceptionHandler` | ADAPT | 机制换 Spring，但**两分支 HTTP 状态码都必须保持 200** |
| `commentservice.go:48-49` 5001 兜底 | 同上兜底 `@ExceptionHandler` | ADAPT | 非业务异常 → `Response(5001,"系统内部错误",null)` |
| `internal/config/config.go:13-16` `UserAuth` | `config/CommentApiProperties` | ADAPT | 绑定机制换 Spring |
| `internal/config/config.go:17` `CommentRpc` | `application.yml` `grpc.client.comment-rpc` | ADAPT | 客户端寻址属配置 |
| `etc/commentservice.yaml:3` `Port: 8888` | `application.yml` `server.port` | ADAPT | 配置替代 |
| `etc/commentservice.yaml:5-7` `UserAuth` | `CommentApiProperties.UserAuth` | ADAPT | 密钥值是跨域共享契约，**不可改** |
| `etc/commentservice.yaml:9-18` `Log`/`Telemetry` | `otel.service.name: comment-api`（`application.yml`） | ADAPT | Go `Config` 无对应字段，行为细节 `UNKNOWN`（§12.7 #5） |
| `etc/commentservice.yaml:21-23` `CommentRpc(NonBlock)` | `grpc.client.comment-rpc.*` | ADAPT | go-zero `NonBlock` 在 Java 侧无严格等价开关，不建独立组件 |
| `svc/servicecontext.go:19-22` | `client/CommentRpcClient` | ADAPT | `zrpc.MustNewClient` → `@GrpcClient`；⚠️ 方法路径前缀差异见 §12.1 风险点 1 |
| `routes.go:55` `WithPrefix` | `@RequestMapping("/comment/v1")` | ADAPT | 前缀一致；但段内路径重复 `comment` 需拍板 |
| `routes.go:54` `WithJwt`（7/7） | `interceptor/CommentJwtAuthInterceptor` + `config/WebMvcConfig:①` | ADAPT | 独立 Bean（public 集为空） |
| `routes.go` 无 `WithTimeout`/CORS/maxBytes | 不加 | ABSORB | Go 无此装饰器，加了=新增行为 |
| `handler/comment/createcommenthandler.go` | `controller/CommentController#createComment` | MERGE | 7 handler 归一后 md5 相同 |
| `handler/comment/deletecommenthandler.go` | `controller/CommentController#deleteComment` | MERGE | 同上 |
| `handler/comment/getcommenthandler.go` | `controller/CommentController#getComment` | MERGE | 同上 |
| `handler/comment/likecommenthandler.go` | `controller/CommentController#likeComment` | MERGE | 同上 |
| `handler/comment/managecommenthandler.go` | `controller/CommentController#manageComment` | MERGE | 同上 |
| `handler/comment/reportcommenthandler.go` | `controller/CommentController#reportComment` | MERGE | 同上 |
| `handler/comment/updatesubjectstatehandler.go` | `controller/CommentController#updateSubjectState` | MERGE | 同上 |
| `types/types.go`（7 Req） | `dto/*Req` | SPLIT | 请求契约 + 无校验这一事实都要各自可表达 |
| `types/types.go`（7 Resp） | `dto/*Resp` | SPLIT | 响应契约（`success` 语义各端点不同） |
| `types/types.go:6-20` `CommentItem` | `dto/CommentItem` | SPLIT | 跨 `GetCommentResp` 复用；`children` 恒 `[]` 需独立表达 |
| `types/types.go:97-105` `SubjectInfo` | `dto/SubjectInfo` | SPLIT | 被 `GetCommentResp` 复用；值类型 vs `null` 的语义需独立表达 |
| `logic/comment/helper.go:10-30` | `service/CommentApiSupport.extractUserId` | MERGE | 6 个消费方的同款类型 switch |
| `logic/comment/createcommentlogic.go:34-64` | `service/CreateCommentService` | MERGE | 单端点用例 |
| `logic/comment/deletecommentlogic.go:34-60` | `service/DeleteCommentService` | MERGE | 单端点用例（`Success` 硬编码 true） |
| `logic/comment/getcommentlogic.go:33-135` | `service/GetCommentService` | MERGE | 单端点用例（**无 uid**、错误路径不同） |
| `logic/comment/likecommentlogic.go:34-61` | `service/LikeCommentService` | MERGE | 单端点用例 |
| `logic/comment/managecommentlogic.go:34-61` | `service/ManageCommentService` | MERGE | 单端点用例 |
| `logic/comment/reportcommentlogic.go:34-63` | `service/ReportCommentService` | MERGE | 单端点用例 |
| `logic/comment/updatesubjectstatelogic.go:34-59` | `service/UpdateSubjectStateService` | MERGE | 单端点用例 |
| 6 个 logic 的 `status.FromError` 透传（`:51-58` 等） | `CommentGlobalExceptionHandler.handleRpc` + `CommentGrpcError` trailer | ADAPT | 🔴 grpc-java 发不出越界 `grpc-status`，须用 trailer 重建（`LikeGrpcError.java:36-64` 已验证可行） |
| `getcommentlogic.go:35,38,74` 非 `CodeError` 错误 | 兜底分支 → 5001 | ADAPT | 保持现状；不得"改善"成 2001 |
| `getcommentlogic.go:106-135` `convertPbCommentItemToAPI` | `GetCommentService` 私有方法 | KEEP | 仅 1 个调用方，不进公共 Support |
| `getcommentlogic.go:42-60` 四条参数兜底 | `GetCommentService` 内部 | KEEP | 与该端点强绑定 |
| Go 侧无 metrics | **不建 `metrics/`** | — | §14.4 禁止为完整性创建空组件 |
| —— | `service/comment/comment-api/pom.xml` | **需先补齐** | 现为空 `<dependencies>`；缺 `comment-proto` / `spring-boot-starter-web` / `common` / `grpc-client-spring-boot-starter` ⇒ 本文件树一行都编译不过 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 7 条 `POST`（`routes.go:19,24,29,34,39,44,49`）；前缀 `/comment/v1`（`:55`）；**7/7 全量 `WithJwt`、public 集为空**（`:54`）；成功信封 `{code:200,msg:"OK",data:…}`（`commentservice.go:39-45`）；失败信封 `{code,msg,data:null}` + **HTTP 恒 200**（`:55`）；`int64` 为 JSON 数字（`types.go` 零 `,string` tag）；`GetCommentResp.subject` 恒非 null；`CommentItem.children` 恒 `[]` |
| L2 业务语义有承载位置 | ✅ uid 提取的 6 分支类型 switch + 2 类失败（`helper.go:11-29`）；uid 失败 → 1006「TOKEN已过期」（6 个 logic `:35-39`）；**`/comment/list` 不做身份绑定**（`getcommentlogic.go` 无 `extractUserID`）；`GetComment` 的 4 条参数兜底（`:42-60`）；3 处无校验 enum 窄化（`manage:46`/`report:46`/`update:44`）；`DeleteCommentResp.success` 恒 true 而其余四个透传（`delete:58` vs `like:59`/`manage:60`/`report:62`/`update:58`）；`OwnerId` 恒 0（`getcommentlogic.go:91`） |
| L3 技术语义有承载方案 | ✅ gRPC 业务码往返链路已识别，并已标注平台硬限制与 trailer 方案；HTTP 恒 200 与 `Response` 单层包裹（`user-api/GlobalResponseAdvice.java:30-39` 的回归锁）已写入；`httpx.Parse` 失败走 envelope 而非 400 已对齐（因 `commentservice.go:47` 注册了错误处理器） |
| 业务分支与异常路径已纳入 | ✅ 6 个 logic 的 `status.FromError` 双分支；`getcommentlogic` 的 3 条非 `CodeError` 出口；`extractUserID` 的 nil / 类型不符 / `Int64()` 失败三类；`httpx.Parse` 失败 |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；`DisableNativeRestTimeout` → 无对应物（显式声明）；无 `WithTimeout`/CORS/maxBytes → 不配置；Redis blacklist → **不引入**（Go 无） |
| 复用组件已明确 | ✅ `Response`、`BusinessException`、`HttpMiddlewareFilter`、`GoStyleCoercion`、`JwtUtil.padToHmacKeySize`、`comment-proto` 生成的 stub —— 每项均给出实际打开过的路径行号 |
| 新增文件有职责依据 | ✅ `CommentJwtAuthInterceptor`（public 集为空这一显式事实需要独立 Bean）；`CommentApiSupport`（6 个消费方的重复片段）；`CommentController`（7 个同质 handler）；7 个 per-endpoint service（各自 Req/Resp 契约独立演进）—— 均**不是**「Go 里有同名文件」 |
| 合并有组织依据 | ✅ 7 handler → 1 Controller（实测 md5 相同）；7 个重复的 `extractUserID` → 1 个 Support；其余合并只在「单端点用例」这一组织维度内 |
| 拆分可解释 | ✅ `types.go` 的 15 个 DTO 拆为 16 个 Java 类（`CommentItem`/`SubjectInfo` 被复用，其余一对一）；`routes.go` 拆到 controller/interceptor/config 三个落点 |
| 无「为对应 Go 文件而建」的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `Types.java` / `ServiceContext.java` / `Metrics.java` |
| 未把未实现能力说成现有机制 | ✅ `comment-api` 现为空依赖 + 0 行 Java 已在 §12.1 声明；本文件所有"复用"都指向**其他域已落地**的路径；`comment-common` 的类在 `FileTree/comment/common.md` 推导，本文件只引用名字 |
| 两文件拓扑结论一致 | ✅ 与 `FileTree/comment/rpc.md` §12.1 一致：api 不参与「多进程合并」争议；`comment-common` 是唯一的第三方模块，且由 api 与 rpc **共用**（Go `service/comment/common/errmsg` 同时被两侧 import） |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **路径里 `comment` 出现两次** | `routes.go:55` 的 `WithPrefix("/comment/v1")` 叠加 `routes.go:20,25,30,35,40,45` 已含 `/comment/` 的路径 ⇒ `/comment/v1/comment/create` 等 6 条。RAG 惯例是 `/user/v1/<动作>`（`user-api/UserController.java:44-52`）。本文件**按 Go 原样保留**；**是否修正需拍板**（改了就动了 L1 契约） |
| 2 | **业务码塞不进 `grpc-status`** | 见 §12.1 风险点 2。6 个 logic 的 `int(st.Code())` 在 grpc-java 上无法复刻。已有 `LikeGrpcError` 的 trailer 方案可复用，但 comment 侧需自建（Go 的码表不同）。**这是实现前必须先解决的阻塞项** |
| 3 | **`/comment/list` 的错误被压成 5001** | `getcommentlogic.go:35,38,74` 抛的不是 `CodeError`，`commentservice.go:51` 的 `errors.As` 对其返回 false ⇒ 客户端看到 5001。这**可能是 Go 侧缺陷**，但本文件按现状保留；**是否修正需拍板** |
| 4 | **Redis blacklist 是 Java 侧可选新增** | Go comment-api 只有 `rest.WithJwt`（`routes.go:54`），**无** blacklist 中间件。照抄 `JwtAuthInterceptor.java:199-209` = 新增行为。本文件默认不引入 |
| 5 | **`Log` / `Telemetry` 段在 Go `Config` 里无字段** | `config.go:11-18` 只声明 3 段，yaml 有 6 段。`conf.MustLoad` 对未知键的严格性在 go-zero 库内（不在本仓库）⇒ `UNKNOWN` |
| 6 | **`comment-api/pom.xml` 无任何依赖** | 缺 `comment-proto`、`spring-boot-starter-web`、`common`、`grpc-client-spring-boot-starter`、`lombok`。**实现前必须先补 pom** |
| 7 | **`metrics/` 是既有约定但本模块不需要** | `user-api` / `like-api` 都有 `metrics/`；Go comment-api **零指标**（21 个文件实测无 metrics 目录）。本文件**不建该包**（§14.4）。若将来补埋点，是**新增可观测面**，不是迁移 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| **proto `package` 归一后的对拍方案** | `SD-2026-10-23-04` 决定保持 `/comment.CommentService/*`。一旦要求双侧活体对拍，必须在「Go 侧加服务名别名」与「Java 侧重写 `fullMethodName`」之间选一个。**两种方案本文件都未拍板** |
| `Log` / `Telemetry` 段在 `conf.MustLoad` 下的行为 | 未知键报错还是忽略，取决于 go-zero 库实现（不在本仓库） |
| `httpx.Parse` 对「真空 body」的行为 | Go 侧零必填 tag 时真空 body 是否报错：user 域实测为报错（`user-api/UserController.java:79-90` 的勘误记录），但**comment 域的 types 结构未单独实测**。直接影响 `@RequestBody(required = ?)` 的选择 |
| `NonBlock: true` 的 Java 等价物 | go-zero 客户端的后台连接建立，在 `grpc.client.*` 里没有一一对应的开关。是否需要显式配置连接建立策略，未定 |
| `/comment/list` 的 `children` 递归分支 | 当前链路 rpc 恒返 `nil`（`get_comment_logic.go:141`），递归代码不可达。**若将来 rpc 侧改为填充子评论，api 侧这段必须已经正确**——但当前无法用活体对拍验证 |