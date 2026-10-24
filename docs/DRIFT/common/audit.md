<!-- @move AGENTS.md L3125-L3223 · §6.5 service/common 语义一致性审计与修复 -->
### 6.5 `service/common` 语义一致性审计与修复（2026-10-21）

只读审计（Go ↔ RAG 逐行对照，Go 侧 8 个非测试源文件全部通读原文）结论 **SEMANTICALLY NOT EQUIVALENT**，共 20 项偏差。修复在分支 `row/common-fix-semantic-drift`（issue #71186341，spec `.agents/specs/common-semantic-drift-fix.md`）。

**消费者现状（决定了修复优先级）**：

| | Go 侧被谁用 | RAG 侧被谁用 |
|---|---|---|
| communityfact | 11 个服务 | **0** |
| observability | 全部 13 个服务 | 1（user-rpc，且只 import 了 `DeadlineState` 类型） |
| logger | 全部服务 | `LoggerFactoryBean` ← user-rpc 12 处 |
| snowflake | 全部服务 | user-rpc / admin-rpc / message-rpc |

**已修 21 项**（5 项 HIGH 全部处理）：

| 组 | 内容 |
|---|---|
| HIGH | ① HTTP 超时整体失效（新增 `httpTimeout`/`rpcTimeout` 配置，5 个 yaml 落 3s/2s）② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）**← ⚠️ 此条结论已被推翻，见下方 H2 勘误小节**③ `logFatal` 不终止进程（新增 `FatalError` + users 两处 advice 显式 rethrow）④ 499 分支不可达（改为写响应）⑤ 超时启用后业务跑在无请求上下文的线程（补 `RequestContextHolder`/MDC/OTel Context） |
| MEDIUM | `isTimeoutError` 漏判 HTTP 超时、`RecordError` 空指针、`biz_msg` 丢 code、span 重复打标、span 二次 `end()`、`DeadlineState` 设在调度线程而非 handler 线程、`MqConsumer` 吞 panic、`shutdown()` 无调用者、`call_chain` 过滤失效、Snowflake 双份生成状态、admin-rpc `SnowflakeBean` 未装配 |
| LOW | 时钟回拨按 bwmarrin v0.3.0 **真实算法**（无回拨保护，继承 Go 风险并留档）、进程日志 KV 一律 `toString()` 破坏 JSON schema、timestamp 精度与尾零裁剪、trace 字段跨线程丢失、charset 默认编码、64KB 截断多读、`getFuncName` 闭包分支死代码 |

**顺带修掉的潜伏缺陷**（都是本轮改动触发才暴露的）：

- **`logback-classic` 1.6.3 与 `logback-core` 1.5.38 版本错配** → 任何真正启动的 Spring Boot 应用都会在 `LoggingApplicationListener` 抛 `AbstractMethodError: PatternLayout.getDefaultConverterMap()`。此前能长期潜伏，正因为测试全是纯单元测试。已改为交由父 pom 统一管理。
- `ObservabilityProperties` 同时带 `@Component` + `@ConfigurationProperties`，与 `@EnableConfigurationProperties` 并存会产生**两个同名类型 Bean**。本轮统一走 `@Import` 路径（与 rpc 模块一致），测试也照抄该路径。

**新增测试（§3.3 要求：必须真正装配 Spring 上下文）**：

`service/common` 现有 78 个测试（原 75 + 新增），其中 `ObservabilityWiringTest` 是**全仓第一个 `@SpringBootTest`** —— H1/H2 这类「机制没接上」的缺陷按定义无法被纯单测捕获。新增回归用例：`clientDisconnectReturns499`、`workerThreadInheritsRequestContext`、`isTimeoutErrorDetectsHttpClientTimeouts`、`wireAuthorityUnavailableSentinelMapsTo404`、`callChainFilterActuallyRemovesFrameworkFrames`、`logFatalThrowsFatalErrorInsteadOfOnExit`×2。

验证：`mvn clean verify` 全仓 9 模块 BUILD SUCCESS（common 78 / admin-rpc 24 / admin-api 46 / user-rpc 46 / user-api 30 / users-common 3 / message-rpc 待复核）。

> **⚠️ 上面这行是 2026-10-21 那轮的快照，数字已过时**。截至 issue #71187874 之后的最新值：
> `common 97 / admin-rpc 53 / admin-api 97 / user-rpc 84 / user-api 63 / users-common 11 / message-rpc 10`，
> **全仓 415 条、0 failures**。**但更要紧的不是数字变化，而是本节那句「H1/H2 这类机制没接上的缺陷
> 按定义无法被纯单测捕获」** —— `ObservabilityWiringTest` 恰恰就是纯装配测试，
> 而它**漏掉了 H2**：断言 `ctx.getBean(ServerInterceptor.class)` 非空是对的、也是绿的，
> 但 starter 根本不会用这个 Bean。**装配对了 ≠ 机制接上了**，见本节的 H2 勘误小节。

##### ⚠️⚠️ 勘误：**H2 当时并未真正修好**（2026-10-21，issue #71187874 期间实测发现）

本节「已修 21 项」里的 **② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）**← ⚠️ 此条结论已被推翻，见下方 H2 勘误小节**** ——**结论是错的**。
`@Import(RpcServerInterceptorFactory.class)` 只让 `ServerInterceptor` **Bean 存在**，
而 **grpc-spring-boot-starter 3.1.0.RELEASE 从不自动把它挂到 server 上**：

```
javap GrpcServerFactoryAutoConfiguration
  → inProcessGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer, List<GrpcServerConfigurer>)
                                             ↑ 只认 Configurer，没有任何 ServerInterceptor 形参
javap GrpcServerConfigurer → extends Consumer<ServerBuilder<?>>
javap GrpcServerProperties | grep -i intercept → （无字段）
```

唯一注册入口是 `GrpcServerConfigurer` —— 在 `accept(ServerBuilder)` 里调 `server.intercept(...)`。
已在 `UserRpcApplication` / `AdminRpcApplication` 各补一个 `@Bean GrpcServerConfigurer`。

**代价远不止「少了几个 span」**：`interceptCall` / `onHalfClose` 从未执行 ⇒

1. gRPC **无 deadline 传播** ⇒ handler 线程上 `DeadlineState` 恒未设置
   （实测 `DeadlineState.remainingMillis() == Long.MAX_VALUE`）；
2. ⇒ `UserDomainService` 的 11 处 + `AdminDomainService` 的 13 处 **`checkDeadline()` 全部失效**；
3. ⇒ B-1 的 `setQueryTimeout` 注入无从谈起（它就挂在 `DeadlineState` 上）；
4. gRPC **无超时**（客户端只能等 api 层 `http-timeout` 3s）⇒ 超时响应体退化成
   `text/plain "Request Timeout"`，与 Go 的 `{"code":1015}` 不一致。

**实测对照（`exp-b1-timeout-cancel.sh`，修复前 → 修复后）**：

| | 修复前 | 修复后 | Go |
|---|---|---|---|
| 客户端返回 | 3170ms（= api 的 3s） | **2223ms**（= rpc 的 2s） | 2208ms |
| 响应体 | `text/plain "Request Timeout"` | **`{"code":1015,"msg":"服务繁忙"}`** | 同左 |
| PG 查询滞留 | 4303ms | **176ms** | 182ms |

> **为什么 `ObservabilityWiringTest` 没抓到**：`grpcInterceptorBeanExists` 断言的是
> `ctx.getBean(ServerInterceptor.class)` 非空 —— 断言的是**Bean 存在**，
> 而缺陷是**框架没用它**。这类断言在任何实现下都成立，属**结构性假绿**。
>
> **由此新增一条纪律（补在 §3.3）**：**断言「Bean 存在」≠ 断言「机制生效」。**
> 凡依赖框架自动注册的机制（starter 的自动装配、auto-configuration、拦截器链），
> 测试必须断言**运行时的可观测后果**（本例：客户端是否 ≈2s 返回 1015），
> 或至少断言「该对象确实进入了框架的接管点」（本例：`verify(builder).intercept(...)`）。
> 锁死用例：`GrpcInterceptorWiringTest`（2 条）。

**残留差异**不在本节重复维护，统一见 **§3.4.1**（按「有意偏离 / 框架限制 / 表述差异 / UNRESOLVED」四类分列，每条带关闭条件）。

其中本轮新增、值得单独强调的一条：**B-1 超时无法取消在途 IO**。
> ⚠️ **本行原写「按 Go 的 3s/2s 打开超时后转为当前生效」—— 该描述本身也是错的**（2026-10-21 补）。
> 配置确实绑上了（实测 `newUnaryServerInterceptor` 收到 `timeout=PT2S`），
> 但因为同一个 H2 缺陷（gRPC `ServerInterceptor` 从未挂到 server 上），
> **`interceptCall` / `onHalfClose` 一次都没执行过** ⇒ 超时**从未生效**，
> 客户端只能等 api 层 `http-timeout` 3s。
> ⇒ 结论：这条「潜伏 → 生效」的叙述，以及本节对 H1 的「已修」判断，**都建立在一个从未接通的链路上**。
⚠️ 本行末句「`user-rpc` 业务侧一处都未主动响应取消」**已被 §3.4.4 的 DRIFT-018 登记推翻**
（`user-rpc` 实有 11 处、`admin-rpc` 已补到 13 处 `checkDeadline()`），
且 2026-10-21 的机制实验证明 **`checkDeadline` 对在途 IO 无效、缓解手段是 `setQueryTimeout`** ——
完整结论与数据见 §3.4.1「B-1 补记」。

另一条：Snowflake 时钟回拨**照搬 bwmarrin v0.3.0 的无保护行为**，继承其「回拨可能撞 ID」的风险。RAG 不擅自加保护（加了即与 Go 不等价），若要加须单独立项。

