下面 8 段按你给的 4 个维度（负责什么 / 应该放什么 / 不应该放什么 / 容易混淆的区别）逐个说明。每条都基于刚才读到的真实源码，最后一段给出"我准备写的类应该放哪"的速查表。

---

### 1. `advice/`

**负责什么**：HTTP 层"出方向"的横切处理。在 Controller 执行完之后拦截，统一把返回值包成 `{code, msg, data}`（`GlobalResponseAdvice` 实现 `ResponseBodyAdvice`）、把异常翻译成同一个 envelope + HTTP 状态码（`GlobalExceptionHandler` 用 `@ExceptionHandler`）。当前项目里所有正常响应包装和所有异常翻译都发生在这里，Controller 自身完全不碰 envelope。

**应该放什么**：用 `@RestControllerAdvice` 注解的类，承担以下任一职责：
- 用 `ResponseBodyAdvice` 包响应（`GlobalResponseAdvice` 这种）
- 用 `@ExceptionHandler` 把异常 → 业务码 + HTTP 状态码（`GlobalExceptionHandler` 这种）
- 后面新增的全局参数校验翻译 / 兜底 `IllegalArgumentException` 之类也可以加在这里

**不应该放什么**：
- 业务用例（→ `service`）
- 进入请求前的拦截 / 鉴权（→ `interceptor`）
- 注册 interceptor 本身（→ `config`）

**容易混淆的区别 — `advice` vs `interceptor`**：
- **触发时机不同**：interceptor 跑在 Controller 之前/之后、`preHandle` 在 `DispatcherServlet` 派发到 handler 之前；advice 跑在 Controller 之后、Spring MVC 把返回值写进 response body 之前/异常被抛出时
- **方向不同**：interceptor 解决"请求能不能进"（鉴权、写 attribute）；advice 解决"出去的东西长什么样 / 异常怎么翻译"
- **机制不同**：interceptor 实现 `HandlerInterceptor`；advice 实现 `ResponseBodyAdvice`（包装）或 `@ExceptionHandler`（异常）
- 两者会协作：interceptor 里抛 `BusinessException` → advice 里 `@ExceptionHandler(BusinessException.class)` 接住翻译

---

### 2. `config/`

**负责什么**：Spring 配置类的家。两类东西：
- 把 `application.yml` 里 `user-api.*` 段绑成 Java 对象（`UserApiProperties`，`@ConfigurationProperties(prefix="user-api")`）
- 注册 Web 层组件到 Spring MVC 生命周期（`WebMvcConfig` 实现 `WebMvcConfigurer.addInterceptors(...)`，把 `JwtAuthInterceptor` 挂到 `/user/v1/**`）

**应该放什么**：
- `@ConfigurationProperties` 类 —— 任何 `application.yml` 里 user-api.* 段都要有一个对应的 Properties 类
- `@Configuration` 类 —— 实现 `WebMvcConfigurer`（注册 interceptor、消息转换器、CORS）、`@Bean` 工厂方法

**不应该放什么**：
- 任何"运行期干活的逻辑"——这里只声明"谁要挂到哪"，具体怎么做在 interceptor/advice 里
- 业务用例（→ `service`）
- 持有 stub 的容器（→ `client`）

**容易混淆的区别 — `config` vs `interceptor`**：
- **config 是"注册"**：写"interceptor 要拦截什么路径、Properties 字段对应 yaml 哪个 key"；它是声明式的，方法体大多是一行 `registry.addInterceptor(...)`
- **interceptor 是"干活"**：写"token 怎么解析、blacklist 怎么查、userId 怎么塞进 attribute"；它是命令式的、运行期执行的
- 两者通过 DI 协作：`WebMvcConfig` 构造器注入 `JwtAuthInterceptor`，但 `JwtAuthInterceptor` 本身不住在 config/，而住在 interceptor/

---

### 3. `dto/`

**负责什么**：HTTP 边界的 POJO。Controller 入参（`@RequestBody` 接收的 `XxxReq`）和出参（Controller 方法返回的 `XxxResp`），加上被多处复用的嵌套 DTO（`UserInfo`、`AvatarHistoryItem`）。**只有字段、getter/setter、Jackson 注解**，没有任何业务方法。

**应该放什么**：
- 每个用例的请求体 / 响应体 DTO（命名严格按用例：`RegisterReq` / `RegisterResp` / `LoginReq` / ...）
- 嵌套/共享的 DTO（`UserInfo` 给 GetUserResp / UpdateUserResp 共用，`AvatarHistoryItem` 给 GetAvatarHistoryResp 列表用）
- DTO 上的 Jackson 注解：`@JsonProperty("extra_info")` 改 snake_case、`@JsonInclude(NON_NULL)` 处理 optional、`getUid()` 返回 String 模拟 Go 的 `json:"uid,string"`

**不应该放什么**：
- proto 类（`com.rag.users.user.proto.*`，由 protobuf 生成，不属于 dto）
- 数据库实体（user-api 不直连 DB，没有 entity）
- 任何带业务方法、调用下游的代码（→ `service`）

**容易混淆的区别 — `dto` vs 其他业务代码**：
- **vs service 的 helper**：service 里 `UserApiSupport.fromProto(proto) → DTO` 是"proto → DTO 转换"，住在 service（因为它要做转换逻辑）；DTO 自己只是目的地
- **vs controller**：DTO 是数据；Controller 是"接 DTO、调 Service、返回 DTO"的方法体。Controller 不应该持业务字段
- **vs proto**：proto 是 RPC wire 类型（`com.rag.users.user.proto.UserInfo`），由 `.proto` 生成；DTO 是 HTTP wire 类型（`com.rag.users.user.api.dto.UserInfo`）。同名是有意为之、刻意分开（参见 `UserApiSupport` 的注释，专门解释了为什么不能用 import alias）

---

### 4. `metrics/`

**负责什么**：HTTP 层业务指标。当前只有一个 `UserApiMetrics`（`@Component` + Micrometer Counter），7 个 Counter（5 个 login 分支 + 2 个 register 分支），指标名 `user_api_intercept_total{path=..., result=...}` 1:1 对齐 Go 端。Service 在 success/error 路径上调 `counter.increment()`。

**应该放什么**：
- `@Component` 类，构造器注入 `MeterRegistry`，把每个 Counter / Gauge / Timer 在构造时一次性注册好，字段暴露 getter 供 Service 调用
- 命名严格按 Go 端 `user_api_intercept_total`，tag 也是 `path` + `result`

**不应该放什么**：
- actuator 暴露端点的配置（→ `config` 或 `application.yml`）
- 业务用例（→ `service`）

---

### 5. `client/`

**负责什么**：下游 gRPC stub 的持有容器。`UserRpcClient`（`@Component`）持有 `UserServiceBlockingStub`，由 devh `@GrpcClient("user-rpc")` 通过 Nacos discovery 注入，所有 Service 都通过它调下游 user-rpc。**自己不调下游，只暴露 stub**。

**应该放什么**：
- 一个下游模块对应一个 Client 类（user-rpc → `UserRpcClient`；以后如果有别的下游就再加一个）
- `@Component` + 一个字段 + getter，返回 BlockingStub / FutureStub / ReactiveStub
- 后续的 retry / circuit breaker / deadline propagation 等横切也可以挂在这里

**不应该放什么**：
- 任何业务编排 / 状态码映射（→ `service`，那是 UserRegisterService 的事）
- DTO（→ `dto`）

**容易混淆的区别 — `client` vs `service`**：
- **client 是"stub 容器"**：一个字段、一个 getter；Nacos / 连接池 / 重试归这里
- **service 是"用例编排"**：拿到 stub 之后做参数构造、StatusRuntimeException 翻译、metrics 自增
- 协作模式：9 个 Service 都注入同一个 `UserRpcClient`，client 不感知有 9 个 Service；Service 不重复持有 stub

---

### 6. `controller/`

**负责什么**：HTTP 入口。当前只有 `UserController`（`@RestController` + `@RequestMapping("/user/v1")`），9 个 `@PostMapping` 一一对应 9 个 Service，方法体就一行：`return userXxxService.xxx(req, httpRequest);`。**不做业务、不做校验、不做响应包装、不做异常翻译**——envelope 由 advice 包装，异常由 advice 翻译。

**应该放什么**：
- `@RestController` 类，一个模块一个（或按业务域拆）
- `@RequestMapping` + `@PostMapping` / `@GetMapping` 等注解
- 方法签名模式：`XxxResp handler(@RequestBody XxxReq req, HttpServletRequest req)` → 一行委托给 Service

**不应该放什么**：
- 业务逻辑（→ `service`）
- 鉴权 / token 解析（→ `interceptor`）
- 响应包装 / 异常翻译（→ `advice`）
- 校验注解（→ `dto` 上的 `@Valid`）

---

### 7. `interceptor/`

**负责什么**：HTTP 层"入方向"的横切处理。当前只有 `JwtAuthInterceptor`（`HandlerInterceptor` 风格 `@Component`），做三件事：解析 `Authorization Bearer` → jjwt 验签 → Redis blacklist 校验，通过后把 `userId` / `jwt_token` 写入 `request.setAttribute(...)`，下游 Service 直接 `request.getAttribute("userId")` 读。

**应该放什么**：
- 实现 `HandlerInterceptor` 的 `@Component` 类
- `preHandle` / `postHandle` / `afterCompletion` 中的横切逻辑
- 把上下文塞进 `request.setAttribute(...)`，下游从 attribute 读

**不应该放什么**：
- 业务用例（→ `service`）
- 全局异常翻译（interceptor 抛 `BusinessException` 让 advice 接住翻译；翻译本身不住这里）
- 拦截器注册（→ `config` 的 `WebMvcConfig`）

**容易混淆的区别 — `interceptor` vs `advice`**：见第 1 节。简言之：interceptor 是"请求能不能进"，advice 是"出去长什么样 / 异常怎么翻"。

---

### 8. `service/`

**负责什么**：HTTP ↔ RPC 边界 + 业务用例编排。当前 9 个用例 Service（与 Go 端 9 个 `*logic.go` 一一对应）+ 2 个跨用例组件：
- `UserApiSupport`（`@Component`）：helper 聚合 —— `extractUserId` / `codeFromRPCError` / `fromProto` 转换 / `normalizeAvatarUrl` 等
- `IdentityResolver`（`@Component`）：身份契约校验 —— 5 个 sentinel exception + 调 user-rpc getUser 验证 status=0

每个用例 Service 做：DTO ↔ proto 转换、调下游、捕获 `StatusRuntimeException` 翻译业务码、调 `metrics.xxx().increment()`。

**应该放什么**：
- `@Service` 注解的用例类，一个用例一个（命名按用例：`UserRegisterService` / `AvatarHistoryService` 这种）
- 跨 Service 共用的 helper（`@Component`，如 `UserApiSupport`）
- 跨 Service 共用的契约解析器（`@Component`，如 `IdentityResolver`）
- 例外：跨用例组件也允许用 `@Component` 而不是 `@Service`

**不应该放什么**：
- 控制器职责（→ `controller`）
- DTO 类（→ `dto`）
- 全局横切（→ `advice` / `interceptor`）
- 持有 stub 的容器（→ `client`）

**容易混淆的区别 — `service` vs `client`**：见第 5 节。`UserRegisterService` 注入 `UserRpcClient`，自己构造 proto、自调用、自翻译、自打 metrics；`UserRpcClient` 自己什么都不调。

---

### 速查表：我准备写的类应该放哪？

| 我要写的类长这样 | 放哪 | 关键注解 / 形态 |
|---|---|---|
| HTTP 请求/响应的入参/出参 POJO | `dto/` | 纯 POJO + Jackson 注解 |
| `@RestController` 接收 HTTP 请求 | `controller/` | `@RestController` + `@PostMapping`，方法体只委托 |
| 单个业务用例的编排（DTO↔proto + 调下游 + 翻译 + metrics） | `service/` | `@Service`，一个用例一个 |
| 跨多个用例共用的 helper / 契约解析 | `service/` | `@Component`（如 `UserApiSupport`、`IdentityResolver`） |
| 持有一个下游模块的 RPC stub | `client/` | `@Component` + `@GrpcClient("下游名")` |
| 全局响应包装 / 全局异常翻译 | `advice/` | `@RestControllerAdvice` + `ResponseBodyAdvice` / `@ExceptionHandler` |
| 进入 Controller 前的横切（鉴权 / 限流 / 审计） | `interceptor/` | `HandlerInterceptor` + `@Component` |
| 注册 interceptor 到路径 / 绑定 yaml 配置 | `config/` | `@Configuration` 实现 `WebMvcConfigurer` / `@ConfigurationProperties` |
| 注册 Micrometer 指标的容器 | `metrics/` | `@Component` + `MeterRegistry` |

判断口诀：**横切看方向（入→interceptor、出→advice）、编排看边界（HTTP→service）、壳子看注解（POJO→dto、stub→client、声明→config）**。