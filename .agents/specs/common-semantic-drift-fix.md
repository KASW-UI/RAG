# spec: service/common Go↔RAG 语义偏差修复

- issue: #71186341
- 分支: `row/common-fix-semantic-drift`
- worktree: `/Users/pr/Documents/java&golang/RAG.worktrees/row-common-fix-semantic-drift`
- 基准: `Sea-RideTheWind_golang/service/common`（冻结，只读）
- 审计日期: 2026-10-20

## 0. 目标值取证（不是拍脑袋）

Go 侧 `service/user/{user,admin}/{api,rpc}/etc/*.yaml` **均未配置 `Timeout`**。实际生效值来自 go-zero v1.10.2 结构体默认值：

| 字段 | 定义位置 | 默认值 |
|---|---|---|
| `rest.RestConf.Timeout` | `rest/config.go:55` `json:",default=3000"` | **3000ms** |
| `zrpc.RpcServerConf.Timeout` | `zrpc/config.go:46` `json:",default=2000"` | **2000ms** |

即 Go 实际跑的是 **HTTP 3s / gRPC 2s**。RAG 侧修复目标值据此锁定，**不得**另选数值。

slowThreshold：RAG 全仓无 `observability:` 配置块 → `ObservabilityProperties.slowThreshold` 恒 null → 回退 `Observability.slowThreshold()`（env → 500ms）。与 Go 的 `SlowThreshold()` 兜底一致，**不改**。

## 1. 修复清单

### HIGH

| ID | 文件 | 修复 |
|---|---|---|
| H1 | `ObservabilityProperties` / `HttpMiddlewareFilter:52` | 新增 `httpTimeout` / `rpcTimeout` 字段，yaml 落 `3s` / `2s`；移除硬编码 `Duration.ZERO` |
| H2 | `UserRpcApplication:39` / `AdminRpcApplication` | `@Import` 补 `RpcServerInterceptorFactory` + `ObservabilityProperties`；admin-rpc 另补 `SnowflakeBean`/`SnowflakeProperties`/`LoggerFactoryBean` |
| H3 | `Logger.doLogFatal` / `LoggerFactoryBean.logFatal` / `GlobalExceptionHandler` | 新增 `FatalError`；两处 logFatal 改抛 `FatalError`；users 模块 advice 显式 rethrow |
| H4 | `HttpMiddleware:203-220` | 499 分支改为写响应（`text/plain` + body + 换行），不再抛 `ServletException` |
| H5 | `HttpMiddleware` 超时分支 | worker 线程补齐 `RequestContextHolder` + `MDC` + OTel `Context`；轮询换 `CountDownLatch.await(timeout)` |

### MEDIUM / LOW

| ID | 文件 | 修复 |
|---|---|---|
| M1 | `Observability.isTimeoutError` | 补 `HttpTimeoutException` / `HttpConnectTimeoutException` / `InterruptedIOException`（Go `net.Error.Timeout()` 的 Java 映射） |
| M2 | `Observability.markSystemError` | err==null 时也 record（Go `RecordError(errors.New(msg))` 无条件调用） |
| M3 | `Observability.markSystemError/markTimeout` | msg 取值改为 `getMessage()` 原样（null → `""`），对齐 Go `err.Error()` |
| M4 | `RpcInterceptor:218,389` | biz_msg 补 `rpc error: code = X desc = Y` 前缀，对齐 Go `err.Error()` |
| M5 | `HttpMiddleware.markHttpResult` | 删掉直接 `span.setAttribute` 的重复打标（attrs 已随 Mark* 传入） |
| M6 | `RpcInterceptor.wrappedCall.close` | `AtomicBoolean` 守卫，防超时 close 后业务再 close 导致 `span.end()` 两次 |
| M7 | `RpcInterceptor` DeadlineState | 改在 **handler 线程**（包装 listener 的 `onHalfClose`）绑定 deadline，而非调度线程 |
| M8 | `MqConsumer.traceConsumer` | 删掉 `catch (Throwable)`，让 panic 传播（对齐 Go 无 recover） |
| M9 | `HttpMiddleware.shutdown` / `RpcInterceptor.shutdown` | 新增 `ObservabilityLifecycle` `@Component` + `@PreDestroy` |
| M10 | `Logger.isFiltered` / `getFuncName` | 过滤改用 declaringClass 全名匹配包前缀；闭包分支按 Go 两分支差异化 |
| M11 | `Snowflake` / `SnowflakeBean` | 合并为**单份**生成状态（对齐 Go 进程内一个 node） |
| M12 | `Snowflake.nextIdInternal` | 时钟回拨按 bwmarrin v0.3.0 真实算法（**无保护**），javadoc 留档继承风险 |
| M13 | `AuthorityHandler` | `isAuthorityUnavailable` 同时识别 `Wire.ERR_AUTHORITY_UNAVAILABLE` |
| M14 | `Dispatcher:104` | dispatch span 补 `.setParent(ctx)` |
| M15 | `Dispatcher:159` | `duration_ms` 改 nanos/1e6 亚毫秒 |
| M16 | `Dispatcher.deliveryErrorCode` | 去掉 message 相等匹配，只认 sentinel 类型（对齐 `errors.Is`） |
| M17 | `ProcessLogAppender:195-197` | KV 值保留原始类型，不再一律 `toString()` |
| M18 | `ProcessLogger:56-57` | timestamp 改纳秒 + 尾零裁剪（对齐 Go `.999999999`） |
| M19 | `ProcessLogger.traceContextAttrs` | `Context.current()` 失效时回退读 MDC |
| M20 | `AuthorityHandler:120,158` | `getBytes()` → `StandardCharsets.UTF_8` |
| M21 | `Wire.DCEventSender.send` | 2xx 分支不再 `discardUpTo(MAX_VALUE)`，对齐 Go `io.LimitReader` 只读 64KB |

## 2. logFatal 语义等价域（用户拍板）

Go：`Errorw` → `os.Exit(1)`，**永远终止**。
RAG 修复后：输出同样日志 → 抛 `FatalError` → 仅在**无人 catch** 时终止（Spring `main` 未捕获异常 → 非零退出码）。

等价域收窄原因：保留 Spring graceful shutdown（`@PreDestroy` / graceful stop）不被 `System.exit` 绕过。
**残余差异**：`catch (FatalError e) {}` 后进程仍存活。由 users 侧 `GlobalExceptionHandler` 显式 rethrow 兜住已注册路径；未注册 advice 的裸 `@RestController` 需自行注意。

## 3. 测试要求（§3.3）

现有 9 个测试类**全是纯单元测试**，无 `@SpringBootTest` / `@WebMvcTest` / `@SpringBootTest(webEnvironment=RANDOM_PORT)`——按定义测不到 H1/H2 这类「机制没接上」的缺陷。本次必须补：

| 测试 | 触发机制 |
|---|---|
| `ObservabilityWiringTest` | `@SpringBootTest` 验证 `HttpMiddlewareFilter` / `RpcServerInterceptorFactory` 是真 Bean 且 timeout=配置值 |
| `RpcInterceptorRegistrationTest` | 模拟 user-rpc 的 `@Import` 列表，断言 `ServerInterceptor` Bean 存在 |
| `HttpTimeoutContextPropagationTest` | 断言 worker 线程上 `RequestContextHolder.getRequestAttributes()` 非空 |
| `FatalErrorNotSwallowedTest` | 断言 advice 对 `FatalError` rethrow 而非转 envelope |
| 499 可达用例 | 补 `HttpMiddlewareTest` 缺失的 499 分支断言 |

## 4. 明确不改（已评估）

- communityfact 的 `CanonicalHash` 换 JCS 库：两个库均声明 RFC 8785，替换无证据支撑；改为补 RFC 8785 官方测试向量对拍。
- `InstallFrameworkLogger` 无 Spring 等价钩子，保持删除（已留档）。
- HTTP path / method / 表名等 §3.4 已留档项。