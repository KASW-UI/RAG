<!-- @move AGENTS.md L828-L927 · §0 HTTP 超时取消语义（池加界 + deadline 透传） -->
### ✅ HTTP 超时的**取消语义**对齐（DRIFT-001 取消语义部分），并**推翻了我自己写下的一个前提**（2026-10-22）

> 用户拍板：**① 池加界 + deadline 透传，两者都做；② 接受「超时计入 client breaker 失败」**（与 Go `codes.Acceptable` 一致）。
> 本节记录落地过程，以及过程中被实测**两次推翻**的推断 —— 那两条比修复本身更值得留档。

#### Go 侧的机制（逐句）

```go
rest/handler/timeouthandler.go:67-68
    ctx, cancelCtx := context.WithTimeout(r.Context(), h.dt)
    r = r.WithContext(ctx)                      // ← deadline 被【传进业务】
rest/handler/timeouthandler.go:79-89
    go func() { h.handler.ServeHTTP(tw, r); close(done) }()
    select { case <-done: ... case <-ctx.Done(): 写 503; tw.timedOut = true }
```

⇒ 「超时后不 kill goroutine」是**假象**：handler 函数还在跑，但它手上的 ctx 已到期，
所有 **ctx-aware** 的下游 I/O 立即中止。真正可观察的语义是**客户端 status code = `DEADLINE_EXCEEDED`**。

#### 🔴 被实测推翻①：「grpc-java 的阻塞 stub 不可中断」——**错的**

我最初把漂移写成「`Future.cancel(true)` 无法中断阻塞 gRPC 调用，worker 会被钉死」。
**变异测试直接推翻**：摘掉 deadline 传播后，调用并没有挂死，而是以 **`CANCELLED`** 收场。
⇒ 建立 `DeadlineCancellationSemanticsTest`（真 in-process gRPC server + 真阻塞 stub）逐条测出真相：

| 机制 | 客户端看到 | 服务端 handler | 在 `codes.Acceptable` 失败名单里？ | breaker 记为 |
|---|---|---|---|---|
| Go ctx 到期 | `DEADLINE_EXCEEDED` | 自己查 ctx 后退出 | **是** | 失败 |
| Java `Future.cancel(true)` | **`CANCELLED`** | **被线程中断** | **否** | **成功** |
| 客户端取消本身 | — | **不会**被杀掉（除非它自己查 ctx） | — | — |

⚠️ **第三条也是被实测纠正的**：我原以为「客户端取消 ⇒ 下游被中止」。实测服务端 handler
**照常在跑**；只有当 handler 像 Go 那样自己查 ctx（`Context.isCancelled()`）时才会退出。
⇒ 写进测试的方式也随之改了：服务端 handler 改成**像 Go 那样查 ctx**，
并且用 **call id 按调用隔离**记录状态（原用全局标志，被上一个用例残留的 handler 污染，实测偶发红）。

#### 🔴 真正的漂移比原判断**严重得多**：超时对熔断器**不可见**

补 deadline 之前，超时走 interrupt 路径 ⇒ `CANCELLED` ⇒ **不在** Go 的六个失败 code 里
⇒ breaker 把它当**成功** ⇒ **下游变慢对 client breaker 完全不可见，熔断器永远不会因超时打开**。
这才是 deadline 透传的实际收益，也正是用户选择「接受计入失败」的实质价值
（在补 deadline 之前，`DEADLINE_EXCEEDED` 这条判据是「按 Go 写好但**从未被触发**」的静态断言）。
已由 `GoStyleGrpcClientBreakerInterceptorTest#timeoutPathsDifferForTheBreaker` 双向钉死。

#### 落地：Step 1 池加界

`HttpMiddleware.HTTP_EXECUTOR` 由 `Executors.newCachedThreadPool`（`maxPoolSize == Integer.MAX_VALUE`）
改为 `ThreadPoolExecutor(0, 512, SynchronousQueue, AbortPolicy)`：

- ⚠️ **必须仍是 `SynchronousQueue`** —— 与旧形状一致（提交即交接、不排队）。
  换成 `LinkedBlockingQueue` 会引入「排队」语义，超时计时就从「进入业务」推迟到「开始处理」，**那是新行为**。
- ⚠️ **不用 `CallerRunsPolicy`** —— 它把业务搬回容器线程，等于超时保护整条失效。
  拒绝时回 **`maxconnshandler.go:12-36` 的裸 503**：有状态码、**无 body、无 Content-Type**，
  且**刻意与「超时 503」不同**（超时那条走 `writeTimeoutResponse`，有 body）——
  两者可观察含义不同：容量不足是「现在别来」，超时是「来不及了」。
- `512 ≈ 2.5 × Tomcat 默认 max-threads(200)`，使正常流量永不触发拒绝。
  ⚠️ 是**常量不是配置项**（本类是 static 工具、Spring 装配前就初始化，挂配置只会得到
  「看起来能配、实际改了没反应」的面板）。

#### 落地：Step 2 deadline 透传

新增 `com.rag.common.observability.GoStyleRequestDeadline`（ThreadLocal 载体 + `applyTo(stub)`）：

- **在 worker 线程上 `begin`**（不是容器线程 —— 容器线程的 ThreadLocal 对 worker **不可见**，最容易写错处），
  **在同一个 `finally` 里 `clear`**（否则线程池复用会串味）。
- 预算**在 `submit` 之前**起算（Go 也是在起 goroutine 之前 `WithTimeout`）。
- 预算耗尽时给 **1 纳秒**而不是「无 deadline」—— 否则会发起一个没有 deadline 的慢调用，正是要修的那个漂移。
- 接入点选在 **`LikeRpcClient#likeRpc()` / `MessageRpcClient#messageRpc()` 这两个 getter**：
  所有调用点都经由它们，改一处即全覆盖；漏一处就是「有的有 deadline、有的没有」的不一致。
  ⚠️ **不要把返回值缓存到字段上** —— 会把「第一次调用时的预算」固化下来。

⚠️ **`DEADLINE_EXCEEDED` 与外层超时同时到期是 Go 本身就有的竞态**
（Go 的 `select` 在 `done` 与 `ctx.Done()` 之间也是随机选）⇒ 测试**不能**断言「外层不双写」。
改为：服务端睡 30s、外层超时 1s ⇒ **唯一**能让调用结束的就是 deadline 本身，
再用 latch 等内层收场。这样断言不再依赖先后顺序。

#### 变异实证（每次变异与还原都 `mvn clean`）

| 变异 | 结果 |
|---|---|
| 摘掉 worker 里的 `GoStyleRequestDeadline.begin(...)` | e2e 转红：内层不再是 `DEADLINE_EXCEEDED` |
| `MAX_HTTP_WORKERS` → `Integer.MAX_VALUE` | **2 条转红**，其中饱和测试直接报「打满 544 个任务后必须出现拒绝，否则池其实无界」 |

#### 🔴 附带教训：我自己写的测试踩了自己警告的坑

`GoStyleRequestDeadline` 的 javadoc 警告「ThreadLocal 会串味」。
**紧接着** `DeadlineCancellationSemanticsTest` 因为漏写 `@AfterEach` 清 deadline，
把 `GoStyleRequestDeadlineTest#returnsStubUnchangedWithoutDeadline` 搞红
（surefire 所有测试类共用同一条线程）。
⇒ 那条 javadoc 警告是真的，而且**写下来不防自己**。

#### 实测口径

| 模块 | 结果 | 变化 |
|---|---|---|
| `service/common` | **146** | 134 + 12（池上界 3 + deadline 4 + 取证 4 + breaker 判据 1） |
| `service/like/like-api` | **55** | 不变（接线改动，无新增断言） |
| `service/message/message-api` | **73** | 不变 |
| `admin-api` / `user-api` / `hot-api` | **113 / 63 / BUILD SUCCESS** | 不变 |

