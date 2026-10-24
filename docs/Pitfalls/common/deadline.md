<!-- @new 2026-10-24 · align/common 工作树产出 · 基线 commit 8422116 -->

# deadline 链上的断点：每一环都得真的接上

> **产出方**：`align/common` 工作树，基线 commit **`8422116`**。
> 逐条漂移：见 [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md)。
> 同域另一份：见 [`../../DRIFT/common/deadline.md`](../../DRIFT/common/deadline.md)（HTTP 侧取消语义，本文件是**服务端**侧）。

---

## 坑 1：机制在共享库里写好了，消费方没接线 ⇒ 整条链静默失效

### 现象

`service/common` 里有两处依赖「gRPC 服务端 deadline 已到达」的机制：

| 机制 | 位置 | 依赖什么 |
|---|---|---|
| `DeadlineState`（ThreadLocal） | `observability/GoStyleRequestDeadline.java` | handler 线程上被 `RpcInterceptor` 的 `onHalfClose` 绑上 deadline |
| `DeadlineQueryTimeoutInjector` | `persistence/DeadlineQueryTimeoutInjector.java` | 按 `DeadlineState.remainingMillis()` 给每条 SQL 设 `setQueryTimeout` |

⇒ 二者**都以「gRPC 服务端拦截器已挂到 server 上」为前提**。

而 `hot-rpc`（2026-10-24 实测）**没有** `@Bean GrpcServerConfigurer`
（`HotRpcApplication.java:44-45` 只有 `@Import(RpcServerInterceptorFactory.class)`）：

```
① @Import(RpcServerInterceptorFactory)  ✅  Bean 存在
② @Bean GrpcServerConfigurer            ❌  缺  框架从不用它
```

⇒ `interceptCall` / `onHalfClose` **一次都没执行** ⇒ handler 线程上 `DeadlineState` 恒未设置。

### 后果（逐环）

| 环 | 状态 |
|---|---|
| gRPC span | ❌ 无 |
| gRPC 服务端超时 | ❌ 无（客户端只能等 api 层 `http-timeout`） |
| `DeadlineState.remainingMillis()` | 恒为 `Long.MAX_VALUE` |
| 业务层 `checkDeadline()` 守卫 | ❌ **全是死代码** |
| `DeadlineQueryTimeoutInjector` 给 SQL 设 `queryTimeout` | ❌ **不起作用**（拿不到 deadline） |
| 客户端观察到的超时 | 退化成 api 层 3s（Go 侧是 rpc 层 2s） |

### 为什么它能长期潜伏

1. **不报错**：Bean 存在、容器启动正常、测试全绿；
2. **热区自证**：`service/common` 的测试全是纯单测，
   `ObservabilityWiringTest` 那 4 条又**只断言 Bean 存在** ⇒ 抓不到「框架不用它」；
3. **一半的域确实接对了**（9/10），于是「大家都是这样」的错觉成立。

### 纪律

> **「共享组件提供机制」与「消费方接上机制」是两件事，缺一即静默失效。**
>
> 检查手段必须是**断言运行时的可观察后果**，不是「Bean 在不在」：
> - 弱：断言 `ctx.getBean(ServerInterceptor.class) != null`（H2 存在与不存在都通过）
> - 强：断言 `verify(builder).intercept(...)` 真被调用
> - 更强：断言客户端真的在 ≈2s 拿到 `{"code":1015,"msg":"服务繁忙"}`
>
> 并且**每一个消费方都要各自有一条** —— 共享库测得再好也证明不了消费方接上了。

### 根治

[`tools/difftest/check-grpc-interceptor-wiring.py`](../../../tools/difftest/check-grpc-interceptor-wiring.py)
（2026-10-24 新增）：逐个 rpc 进程断言 ① 与 ② **同时具备**，当前 `EXIT=1`，精确指向 `hot-rpc`。

⇒ **凡是「每个消费方都要做 X」的契约，都该有一条可跑的检查**，
而不是让人去数 grep 命中几个文件。

---

## 坑 2：写下来的警告不防自己

`docs/DRIFT/common/deadline.md` 里已经写着一句自我总结：

> 「那条 javadoc 警告是真的，而且**写下来不防自己**。」

2026-10-24 再次验证了这句话：**同一批产物里**，
[`observability.md`](./observability.md) 坑 4 写明了「截断的输出不能支撑否定断言」，
而**我自己在同一天的台账里**正是靠一份被截断的 grep 输出，得出了「10 个 rpc 模块全部有」的假结论。

⇒ **纪律**：**写进坑档 ≠ 不再犯。**
唯一有效的机制是**把结论变成可复跑的检查**（脚本 / 测试），
让「我下次会不会忘」不再影响正确性。

⇒ 推论：**凡是「我下次会注意」的约定，都应该配一条自动检查。**

---

## 坑 3：同一条链的两端，分别被记在两个地方

「deadline」在本仓有**三个互不相干**的载体，容易串：

| 载体 | 方向 | 位置 | 治理方 |
|---|---|---|---|
| HTTP 客户端超时取消 | api → rpc（出向） | `observability/GoStyleRequestDeadline` + `HttpMiddleware` 的池加界 | `service/common`（`docs/DRIFT/common/deadline.md`） |
| gRPC 服务端 deadline 传播 | rpc 服务端（入向） | `observability/RpcInterceptor` 的 `onHalfClose` → `DeadlineState` | `service/common` + **每个 rpc 消费方** |
| SQL 查询超时 | 业务层 → JDBC | `persistence/DeadlineQueryTimeoutInjector` | `service/common`（**依赖上面第 2 行**） |

⇒ 第 3 行**站在第 2 行上面**：第 2 行断了，第 3 行就静默失效。
⇒ **改第 2 行时必须同时问「第 3 行还成立吗」**；
**只验第 3 行（有没有设 queryTimeout）会漏掉第 2 行已断**。

⚠️ 命名也易混：`docs/DRIFT/common/deadline.md` 讲的是**第 1 行**，
本文件讲的是**第 2、3 行** —— 两者标题都叫 deadline，内容不重叠。

---

## 坑 4：同一个组件里的两个 deadline 载体，**时基不同** 🔴

2026-10-24 复验时才发现的（`service/common` 内部）：

| 载体 | 方向 | 存的量 | 时基 | 精度 |
|---|---|---|---|---|
| `GoStyleRequestDeadline` | **出向**（HTTP→gRPC 客户端） | `ThreadLocal<Long>` **纳秒** | `System.nanoTime()`（**单调钟**） | **纳秒** ✅ |
| `DeadlineState` | **入向**（gRPC 服务端 handler） | `ThreadLocal<Long>` **epoch 毫秒** | `System.currentTimeMillis()`（**墙钟**） | **毫秒** 🔴 |

⇒ Go 侧 `context.WithTimeout` 存的是 `time.Time` ⇒ **纳秒精度 + 单调时钟读数**，
`time.Until(d)` 走单调读数。**两侧都对齐的是「出向」那一行**。

### 为什么「同一组件」这件事本身就该触发怀疑

两个载体**不共享实现**（一个存 nanos、一个存 epoch-millis），
所以：

> **「出向已对齐」推不出「入向也对齐」。**
> 更何况它们连**时基**都不同 —— 一个抗 NTP 跳变，一个不抗。

⇒ `DeadlineState.isExpired()`（`RpcInterceptor.java:602-605`）用墙钟比较：
NTP 把墙钟**往前跳** ⇒ 提前判过期；**往后跳** ⇒ 该超时的不超时。
Go 侧不受此影响（单调读数）。

### 纪律

> **同一个组件里出现第二个「时间/预算」载体时，必须逐个问：时基是哪种？精度是哪种？**
> 不要因为「另一个已经对齐了」就默认这个也对齐了。
>
> 判据：**找 Go 侧的对应读法**（`ctx.Deadline()` / `time.Until()`），
> 看它用的是**单调读数**还是**墙钟**、精度是纳秒还是毫秒 —— 然后逐条对。

⚠️ 这条已被**别的域签字**了（`align/like` 的 `SD-2026-10-23-09`，
标注为「共享组件 `GoStyleRequestDeadline` 的公开 API 只暴露毫秒」），
但**签字描述只对了一半** —— `GoStyleRequestDeadline` 内部是**纳秒**，
真正只有毫秒的是 `DeadlineState`。⇒ 见
[`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md) 的 DRIFT-COMMON-005。

⇒ **教训二**：**下游域给共享组件的限制签字时，根因域的台账必须同时落一条。**
否则根因域复审会当新发现重提，而下游的签字理由可能建立在**不准确的描述**上。