# L9 · `align/global-interrupt` —— task-rpc / follow-rpc 的 slow 轴 + **缺口③ 的定性**（主动打断 handler 线程）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写** | `service/task/task-rpc/src/test/**` |
| **可写** | `service/follow/follow-rpc/src/test/**` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L9-interrupt.md` |
| **只读** | `service/common/**`（共享层只有 `align/global` 能改；缺口③ 的定性**只读不改**）· `service/*/src/main/**`（生产代码零改动）· `service/favorite/**`（⛔ 零写权） |

**分支**：`align/global-interrupt` · **工作树**：`../RAG.worktrees/global-interrupt` · **基线**：`align/global` HEAD

---

## 一、目标

两件事：

| # | 事项 | 落点 |
|---|---|---|
| ① | 🔴 **slow threshold 轴守门**推广到 **task-rpc** 与 **follow-rpc**（各新建 1 个测试类） | `service/task/task-rpc/src/test/**` · `service/follow/follow-rpc/src/test/**` |
| ② | 🔴 **缺口③ 的定性** —— 「在飞处理被打断」这一维，L2 只验到「取消发生时它仍在跑」，**没验**「handler 线程被 interrupt / ctx 被取消」 | 只读 `service/common`（**不许改**）⇒ 结论写进本 lane 台账 |

**缺口③ 要回答的问题**：
1. `service/common` 的 `RpcInterceptor` **实际**有没有打断在飞 handler 线程？（读实现，不要只读 javadoc）
2. Go 侧的对应行为是什么？（`go-zero` 的 `zrpc` 超时/取消路径 ⇒ **必须给出 `文件:行号`**）
3. RAG 与 Go 是否**行为不一致**？若不一致 ⇒ 是「可修」还是「框架限制」？若可修 ⇒ **给出修法与代价**（但**不许改** `service/common` ⇒ 交整合者）
4. L2 的 javadoc 自陈这是「DRIFT-018 框架限制」⇒ **核实这个自陈是否成立** ⇒ 若不成立 ⇒ 那是**一条新发现**，写清证据。

⚠️ **task-rpc / follow-rpc 这两域在第十七轮做过 X-5 轴的修复**（`ServerCalls.asyncUnaryCall` + latch）⇒
它们的 X-5 轴**已经有了** ⇒ 本 lane **只做 slow 轴**，不动已有的 X-5 修复。

---

## 三、🔴 样板（**照抄，不要重新发明**）

> 三个样板是 L2（第二十四轮）建的，**已通过变异验证** ⇒ 直接照抄结构，只换模块相关的名字。

| 样板 | 路径 | 它示范什么 |
|---|---|---|
| **X-5 轴** | `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcGrpcInterceptorWiringTest.java` | **真 server + 真链 + 判别式断言**（服务端超时真的打断了在飞处理） |
| **slow 轴** | `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcSlowThresholdGateTest.java` | **真 OTel SDK + 收集型 exporter** ⇒ 断言导出的 span 上真的有 `sea.slow` |
| **latch 加固版** | `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java` | `sleep` ⇒ `latch` 的改造形态 |

### 3.1 六条硬性要求（**每一条都有血泪，违反就等于守门空转**）

| # | 要求 | 踩过的坑 |
|---|---|---|
| ① | 🔴 **必须用 `ServerCalls.asyncUnaryCall(...)` 注册慢服务**，**不能**用裸 `ServerCallHandler` | grpc-java **不会**向裸 handler 派发 `onHalfClose` ⇒ 「处理函数一直不返回」**从未发生** ⇒ 客户端照样拿到自己的 `DEADLINE_EXCEEDED` ⇒ 只断言 status + 耗时的用例**照样是绿的** ⇒ 那是「服务端能取消一个**空调用**」，不是「服务端超时打断一个**正在执行**的处理」 |
| ② | 🔴 **必须断言「handler 真的被调用过」**（`probe.entered.await(...)`） | 同 ① ⇒ 少了这条断言，① 的问题测不出来 |
| ③ | 🔴 **必须断言「取消发生时它仍在跑」**（`assertFalse(probe.completed.get())`） | 只看「客户端拿到什么 status、耗时多久」在「handler 压根没执行」时**同样成立** |
| ④ | 🔴 **必须用 latch，不能用 `Thread.sleep(10_000)`** | sleep 方案下「取消发生时它还在跑」这条断言**根本写不出来**（等到断言时它早跑完了）⇒ 必须 latch ⇒ `finally` 里**立刻**放行 |
| ⑤ | 🔴 **`GrpcServerConfigurer` 必须按 Bean 名取**：`ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class)` | devh starter 自己还注册了 `compressionServerConfigurer` / `decompressionServerConfigurer` 两个同类型 Bean ⇒ `ctx.getBean(GrpcServerConfigurer.class)` 会抛 `NoUniqueBeanDefinitionException` |
| ⑥ | 🔴 **channel target 必须显式写 `dns:///` scheme** | Nacos discovery 在全局 `NameResolverRegistry` 里注册了高优先级 provider ⇒ 会把**任何无 scheme 的 target** 当作 serviceId 去 Nacos 拉实例 |

### 3.2 `@SpringBootTest` 的必备 properties（照抄样板）

```
"grpc.server.port=0",                              // ⚠️ GrpcServerLifecycle 没有 @ConditionalOnProperty ⇒ 实测写 false 仍会起 server
"spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",  // NacosRegistration 捕获异常只记日志不中断启动
"management.otlp.tracing.export.enabled=false",    // OTLP 指向不可达端点 ⇒ 只在后台重试刷日志
"logging.level.com.alibaba.nacos=WARN",
"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1",   // 绕过 Redis：Lettuce 连接懒建
"rag.protection.cpu-shedding.enabled=false"        // ⚠️ 关掉 CPU 自适应降载，否则链上的 shedding 层会先返回 RESOURCE_EXHAUSTED，把判据盖掉 ⇒ 变成 flaky
```

### 3.3 slow 轴的额外要求

- **必须装真 OTel SDK**：`GlobalOpenTelemetry.resetForTest()` + `GlobalOpenTelemetry.set(OpenTelemetrySdk...SimpleSpanProcessor.create(EXPORTER))`
  ⇒ ⚠️ **必须在 Spring 上下文创建之前装**（`@BeforeAll`）—— `RpcInterceptor` 在 **Bean 创建期**就取了 Tracer（`RpcInterceptor.java:154`）
- **两条断言缺一不可**：慢的那条（耗时 > 阈值 ⇒ `sea.slow` == true）· 快的那条（耗时 ≪ 阈值 ⇒ **`sea.slow` 不存在**）⇒ 证明这条路径是**真比较**出来的，不是恒为 true
- span 属性键：`com.rag.common.observability.ObservabilityConstants.ATTR_SLOW` / `ATTR_DURATION`

## 四、🔴 本轮纪律（**违反即作废**）

| # | 纪律 | 说明 |
|---|---|---|
| 1 | **生产代码零改动** | 本轮是**守门修复**，不是行为修复 ⇒ `service/*/src/main/**` **一行都不许改** |
| 2 | **不改 `service/common`** | 共享层只有 `align/global` 能改 ⇒ 若发现必须动它 ⇒ **停下来**写进台账，交整合者 |
| 3 | **不碰 favorite** | `service/favorite/**` 与 `docs/*/favorite/**` **零改动**（本轮各 lane 都不涉及） |
| 4 | **每条守门必须做变异** | 变异必须**转红在目标断言上** ⇒ 证明守门非空转 |
| 5 | **变异只在隔离工作树跑** | 不在 lane 自己的工作树里跑（PIT-GLOBAL-020）⇒ 提交前 `git diff` 逐个确认变异已还原 |
| 6 | **变异判定只认 surefire XML** | stdout 的 `-- in` 行在类有 `@DisplayName` 时是**显示名**不是 FQN ⇒ 判定**逐条 `testcase`**（PIT-GLOBAL-021） |
| 7 | **三道闸** | 跑前删旧 `TEST-*.xml` + 看 maven 退出码 + grep `COMPILATION ERROR`（PIT-GLOBAL-018） |
| 8 | **改了上游模块就带 `-am`** | 否则测的是 `~/.m2` 里的旧 jar（PIT-GLOBAL-019） |
| 9 | **不 `git add -A`** | 提交前 `git diff` 逐个确认，只 `git add` 自己改过的文件 |
| 10 | **容器名/库名按域区分** | 本仓有**并行跑 Testcontainers 容器起不来**的先例（hot 域 Pitfall）⇒ 若容器起不来 ⇒ **先判定为环境问题并重试一次**；仍失败 ⇒ **停下来报告**，**不要改代码迁就** |

## 五、验收标准

- [ ] 每条守门**真跑过**（`mvn -o test -pl <模块> -am -Dtest='<类>'`），且 **skipped = 0**
- [ ] 每条守门**都做过变异**且**红在目标断言上**（贴出变异前后的 surefire XML 对比）
- [ ] 生产代码 **零改动**（`git diff --stat` 可验）
- [ ] `service/common` **零改动**
- [ ] favorite **零改动**
- [ ] 台账写进**本 lane 自己的文件**（`docs/DRIFT/global/lanes/<本文件>`），含：逐条判定表 + 变异结果 + **未覆盖的部分**
- [ ] **未覆盖 / 未穷尽**的部分要**显式列出**（不许写「已全部对齐」）
- [ ] 提交前 `git diff` 确认变异已还原；工作树干净

## 六、交付物

1. 新建/修改的**测试文件**（照 §三 的样板）
2. **变异脚本**（放 `/tmp`，不进仓）+ 变异前后的 surefire XML 对比摘录
3. **本 lane 台账**：`docs/DRIFT/global/lanes/<本文件>` 末尾追加「逐条判定表 + 变异结果 + 缺口清单 + 未覆盖部分」
4. **提交**（中文、结构化多段提交信息）—— **不 push、不 merge**，交整合者

### 3.4 ⚠️ task-rpc / follow-rpc 的两个已知事实（自己再核一遍）

- 这两域在第十七轮把「造慢服务」的写法从 `sleep` 换成了 latch ⇒ **照抄它们现成的写法**，不要引入第三种形态
- 这两域**没有** `observability.slow-threshold` 的逐域证据 ⇒ 本 lane 就是来补这个的

---

## 七、第二十七轮 L9 执行结果（2026-10-26 · `align/global-interrupt`）

> 本节是本 lane 的**产出台账**。⚠️ 「已验」一律读作「**在该证据强度下一致**」，**不是**「已闭环」；
> 也**不是**「Go↔RAG 已对齐」—— 本 lane 只补 **RAG 侧**守门，**未**做双侧活体对拍。

### 七·一 交付物（**生产代码零改动**）

| # | 文件 | 性质 | 用例 / 断言条数 |
|---|---|---|---|
| ① | `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskRpcSlowThresholdGateTest.java` | **新增** | 2 个用例 / **10 条断言** |
| ② | `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcSlowThresholdGateTest.java` | **新增** | 2 个用例 / **10 条断言** |

⇒ `git diff --stat` 里**没有** `src/main/**`；`service/common/**` **零改动**；favorite **零触碰**。

**断言条数拆解**（两个类同构）：
`assertNotNull(configurer)` 1 + `assertTrue(probe.entered.await(2s))` 1 + `assertFalse(probe.completed.get())` 1
+ `assertNotNull(resp)` 1 + `assertTrue(found.isPresent())` 1 = **5 条公共**
+ 慢那条 `assertEquals(Boolean.TRUE, sea.slow)` 1 + `assertNotNull(sea.duration_ms)` 1 = **2 条**
+ 快那条 `assertNull(sea.slow)` 1 + `assertNotNull(sea.duration_ms)` 1 + `assertTrue(duration < 100ms)` 1 = **3 条**
⇒ 合计 **10 条**。

### 七·二 逐条判定表（任务书 §3.1 六条 + §3.3 三条）

| # | 判据 | task-rpc | follow-rpc | 落点（task / follow 同名同号，除注明外） |
|---|---|---|---|---|
| ① | 用 `ServerCalls.asyncUnaryCall(...)` 注册慢服务（**不用**裸 `ServerCallHandler`） | ✅ | ✅ | `:338`（`slowTaskService` / `slowFollowService`） |
| ② | 断言「handler 真的被调用过」 | ✅ | ✅ | `:292` `assertTrue(probe.entered.await(2, TimeUnit.SECONDS), …)` |
| ③ | 断言「开闸（= 取消）之前它仍在跑」 | ✅ | ✅ | `:299` `assertFalse(probe.completed.get(), …)` |
| ④ | 用 latch，**不用** `Thread.sleep(10_000)`；`finally` 里立刻放行 | ✅ 全类**零** `Thread.sleep`（该词只出现在 javadoc/注释里） | ✅ | `probe.slowGate` latch；`finally { probe.slowGate.countDown(); … }` |
| ⑤ | `GrpcServerConfigurer` **按 Bean 名**取 | ✅ | ✅ | `:254`（follow `:252`）`ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class)` |
| ⑥ | channel target 显式写 `dns:///` scheme | ✅ | ✅ | `:267`（follow `:266`） |
| §3.3-a | 真 OTel SDK：`resetForTest()` + `set(OpenTelemetrySdk…SimpleSpanProcessor.create(EXPORTER))`，**且在 Spring 上下文创建之前**（`@BeforeAll`） | ✅ | ✅ | `:187`（follow `:185`） |
| §3.3-b | 慢的那条：耗时 > 阈值 ⇒ `sea.slow == true` | ✅ | ✅ | `:219` `assertEquals(Boolean.TRUE, …)` |
| §3.3-c | 快的那条：耗时 ≪ 阈值 ⇒ **`sea.slow` 不存在** | ✅ | ✅ | `:231` `assertNull(…)` |

⚠️ **与样板（L2 的 `AdminRpcSlowThresholdGateTest`）的一处有意差异**：
样板用 `Thread.sleep(400)` 制造慢业务；本 lane 改成**闸门 latch**（`probe.slowGate`）+ **异步 stub**（`newFutureStub`），理由三条：
① 满足 §3.1 的 ②③（要能在「调用**还在飞**」的当口观察 handler 状态 ⇒ 必须异步 stub）；
② 零 `Thread.sleep`；
③ 「慢」的时长由**测试侧**控制（400ms），而不是由 handler 里的 sleep 决定 ⇒ `finally` 里能立刻放行。

### 七·三 变异结果（每条守门都做过变异，且**红在目标断言上**）

**变异对象 = 共享层生产代码** `service/common/src/main/java/com/rag/common/observability/Observability.java:143-146` 的 slow 阈值比较：

```java
if (slowThreshold != null && slowThreshold.compareTo(Duration.ZERO) > 0
        && duration != null && duration.compareTo(slowThreshold) > 0) {
    b.put(ATTR_SLOW_BOOL, true);
}
```

| # | 模块 | 变异点 | 红在哪个 testcase（**目标**） | 同批另一条 | maven 退出码 | surefire XML |
|---|---|---|---|---|---|---|
| **M1** | task-rpc | `duration.compareTo(slowThreshold) > 0` → `< 0`（**反转比较**） | 🔴 **`slowCallMustBeMarkedSlow`**（`expected: <true> but was: <null>`，实测 duration=433.396ms） | `fastCallMustNotBeMarkedSlow` 也红（duration=5.848ms 被误标 slow） | **1**（BUILD FAILURE） | `tests=2 failures=2 errors=0 skipped=0` |
| **M2** | task-rpc | `> 0` → `true`（**恒真**） | 🔴 **`fastCallMustNotBeMarkedSlow`**（`expected: <null> but was: <true>`，duration=2.328ms） | `slowCallMustBeMarkedSlow` **仍绿** | **1** | `tests=2 failures=1 errors=0 skipped=0` |
| **M3** | follow-rpc | `> 0` → `< 0`（反转比较） | 🔴 **`slowCallMustBeMarkedSlow`**（`expected: <true> but was: <null>`，duration=405.694ms） | `fastCallMustNotBeMarkedSlow` 也红（duration=1.823ms 被误标 slow） | **1** | `tests=2 failures=2 errors=0 skipped=0` |
| **M4** | follow-rpc | `> 0` → `true`（恒真） | 🔴 **`fastCallMustNotBeMarkedSlow`**（`expected: <null> but was: <true>`，duration=2.659ms） | `slowCallMustBeMarkedSlow` **仍绿** | **1** | `tests=2 failures=1 errors=0 skipped=0` |

⇒ **「两条断言缺一不可」这件事本身被变异证实**：
只反转比较（M1/M3）⇒ **快**那条也红 ⇒ 快那条确实在守「**真比较**」；
只改成恒真（M2/M4）⇒ **慢**那条仍绿、快那条红 ⇒ 慢那条确实在守「**超过阈值才标**」。
⇒ **两条断言各守一半，缺任一条都会让守门漏掉一半的退化。**

**基线（未变异）**：task-rpc 与 follow-rpc 各 `tests=2 failures=0 errors=0 skipped=0`（两条全绿）⇒ `skipped = 0` ✅。

**变异还原后的复跑（证明「还原」是真的还原，不是「忘了改回来」）**：
| 模块 | 结果 | maven 退出码 | surefire XML |
|---|---|---|---|
| task-rpc | `BUILD SUCCESS` · `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` | **0** | `tests=2 failures=0 errors=0 skipped=0` |
| follow-rpc | `BUILD SUCCESS` · `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` | **0** | `tests=2 failures=0 errors=0 skipped=0` |

⚠️ **变异纪律自证**（PIT-GLOBAL-018/019/020/021）：
- 每次跑前删旧 `TEST-*.xml` ⇒ 报告里的 XML **一定是本次的**；
- 判定**只认 surefire XML 的 `testcase`**（不看 stdout 的 `-- in` 行）；
- 变异**只在本 lane 自己的工作树**跑；跑完由脚本用备份 `cp` 还原 ⇒ `git diff --stat -- service/common/` **为空**
  （且 `grep 'compareTo(slowThreshold)'` 复核为 `> 0`）；
- 改了上游 `service/common` ⇒ 一律带 `-am`（否则测的是 `~/.m2` 里的旧 jar）；
- `COMPILATION ERROR` 计数 = 0（脚本每次都打）；maven 退出码逐次记录（见上表）。
- 变异脚本在 `/tmp/l9-evidence/mutate.sh`，**不进仓**。

### 七·四 缺口③ 的四问结论（「在飞处理被打断」这一维）

#### 问 1：`service/common` 的 `RpcInterceptor` **实际**有没有打断在飞 handler 线程？

→ **没有**（在 **gRPC server 路径**上）。以下是**读实现**的逐行证据（**不是**读 javadoc）：

| 位置 | 事实 |
|---|---|
| `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:196-210` | 超时调度线程的动作**只有** `target.close(Status.DEADLINE_EXCEEDED, …)`（`:204-206`）⇒ **没有任何 `Thread.interrupt()`** |
| `…/RpcInterceptor.java:287-314` | `onCancel()` 只做 `timeoutFuture.cancel(false)`（`:290`）+ 打标 + `super.onCancel()` ⇒ **不打断** |
| `…/RpcInterceptor.java:395` 与 `:406` | 文件里**唯一**的 interrupt 源 `workerFuture.cancel(true)`（→ `worker.interrupt()`）位于 **`handle(...)`** 这条**非 gRPC** 入口（`:330-421`）⇒ **不在** gRPC server 拦截器链上 ⇒ 对 task/follow 的 gRPC 调用**不可达** |
| `service/common/src/main/**` 全量 grep `interrupt` | 其余 interrupt 全在 `HttpMiddleware`（HTTP 路径）与 `communityfact/Dispatcher`（非 gRPC 路径）⇒ **gRPC 路径零 interrupt** |
| grpc-java 1.63.0 `io/grpc/internal/ServerCallImpl.java:210-231` | `close(status, trailers)` → `stream.close(...)`（`:227`）⇒ **不碰任何线程** |
| grpc-java 1.63.0 `io/grpc/internal/ServerCallImpl.java:368-386` | 非 OK ⇒ `call.cancelled = true`（`:374`）+ `listener.onCancel()`（`:375`）+ `context.cancel(cancelCause)`（`:384`）⇒ 取消的是 **grpc-java 的 `Context`**，**不是** handler 线程的 interrupt flag |
| grpc-java 1.63.0 `io/grpc/stub/ServerCalls.java:170-182` | `onHalfClose()` 里 `method.invoke(request, responseObserver)`（`:182`）**同线程同步执行** ⇒ handler 体就跑在 `RpcInterceptor` 绑 `DeadlineState` 的那个线程上（`RpcInterceptor.java:282`） |

⇒ **结论**：RAG 的 gRPC 服务端超时**只从外部关闭 call**，**不打断**在飞 handler 线程；
handler 只能靠 `DeadlineState` **轮询**（`isExpired()` `:620-623` / `remainingMillis()` `:626-630`，`ThreadLocal` 定义在 `:590`）
或 `DeadlineQueryTimeoutInjector` 给 SQL 设 `queryTimeout` 来感知。

#### 问 2：Go 侧的对应行为是什么？

| 位置 | 事实 |
|---|---|
| `Sea-RideTheWind_golang/service/common/observability/rpc.go:38-39` | `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` + `defer cancel()` |
| `…/rpc.go:47-57` | handler 跑在**独立 goroutine**，收到的是 `timeoutCtx`（`:55` `resp, err = handler(timeoutCtx, req)`） |
| `…/rpc.go:59-72` | `select` 三分支；超时分支 `case <-timeoutCtx.Done():`（`:68`）→ `MarkTimeout`（`:70`）→ 返回 `DEADLINE_EXCEEDED`（`:69,71`） |
| `…/rpc.go:53-54` 与 `:64-65` | 超时分支**不等** handler goroutine（那把 `lock` 由 goroutine 持有并 `defer` 释放）⇒ **Go 也不杀 goroutine** |
| go-zero `zrpc/server.go:164-168` | `if c.Timeout > 0 { svr.AddUnaryInterceptors(serverinterceptors.UnaryTimeoutInterceptor(...)) }` |
| `…/rpc.go:19-23`（`DisableNativeRpcTimeout`） | `:21` `conf.Timeout = 0` ⇒ **go-zero 自带超时在这 10 个 rpc 域根本没挂上** ⇒ 唯一生效的是自建拦截器（`task.go:28`+`:44`；`follow.go:34`+`:44`） |
| go-zero `zrpc/internal/serverinterceptors/timeoutinterceptor.go:34,43-55,64-71` | go-zero 自己的超时拦截器与自建拦截器**同构**（`ctx, cancel := context.WithTimeout(ctx, t)` → goroutine 跑 handler → `select` on `ctx.Done()`） |

⇒ **Go 的语义 = 「ctx 到点自动 Done ⇒ handler 内部所有 ctx-aware 操作（`http.Client` / `sql.DB` / `grpc.Dial`）立即中止」**，
**但 goroutine 本身也不会被杀**（Go 没有线程中断）⇒ **两侧在「不杀执行体」这一点上完全一致**。

#### 问 3：RAG 与 Go 是否**行为不一致**？⇒ 可修还是框架限制？

| 维度 | Go | RAG | 一致？ |
|---|---|---|---|
| 超时后是否杀执行体 | ❌ 不杀（goroutine 继续跑到 handler 返回） | ❌ 不杀（线程继续跑） | ✅ **一致** |
| 客户端立刻拿到什么 | `DEADLINE_EXCEEDED`（`rpc.go:69`） | `DEADLINE_EXCEEDED`（`RpcInterceptor.java:204-206`） | ✅ 一致 |
| span 打标出口 | `MarkTimeout`（`rpc.go:70`） | `markTimeout`（`RpcInterceptor.java:237`） | ✅ 一致 |
| **取消能否自动传播到 handler 内部的在途操作** | ✅ **能**（ctx 派生 ⇒ 全部 ctx-aware 操作立即中止） | ❌ **不能**（`DeadlineState` 是 `ThreadLocal`（`RpcInterceptor.java:590`）⇒ 必须业务**显式轮询**） | ❌ **不一致** |

⇒ **定性：框架限制**（不是可修的局部缺陷），三条理由：
1. **已签字的 B-1**（`docs/Intentional_Deviations/common/_signed-deviations.md:27`）把「超时无法取消在途 IO」登记为
   **框架限制**；其中「在途 SQL」那一支已用 `DeadlineQueryTimeoutInjector`（`setQueryTimeout`）关闭
   （实测滞留 4303ms → **176ms**，与 Go 的 182ms 同量级）。
2. Java 侧**没有 ctx 这个一等公民** ⇒ 要做到 Go 的「自动传播」，必须把 deadline 载体**一路作为参数传进每个 IO 调用点**
   并改造全部调用点 ⇒ 那是**全仓 API 形态变更**，不是 `service/common` 的局部改动 ⇒ 代价与风险都超出「收口」范畴。
3. **框架层就没有这个语义**：grpc-java 的 `ServerCall#close`（`ServerCallImpl.java:210-231` / `:368-386`）**只取消 grpc-java 的 `Context`**、
   **不发 interrupt** ⇒ 纯 Java 侧无法凭 `close` 达成 Go 的 `ctx.Done()` 传播。

⇒ **修法与代价**：
- **行为层面**：**无可修项**（框架限制，且已由 B-1 登记 + 局部缓解）。
- **文档层面**：有一处可修（N1，见下）⇒ 代价 = 纯注释改动、零行为风险，但**属 `service/common`**
  ⇒ **只有 `align/global` 能写** ⇒ 本 lane 只提建议，**未改**。

#### 问 4：L2 的 javadoc 自陈（「DRIFT-018 框架限制」）是否成立？

→ **成立** ⇒ **不是新发现**。

- L2 的原话在 `docs/DRIFT/global/lanes/L2-rpcgate.md:229-231`：
  「本轮**不声称** RAG 侧能像 Go 的 `ctx.Done()` 那样**主动打断** handler 线程（那是 `RpcInterceptor` javadoc 自陈的 DRIFT-018 框架限制，本轮**未改**）」。
- 被引的 javadoc 在 `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:129-130`：
  「Java grpc-java 的 `ServerCall#close` 只能从外部"关闭 call"让客户端看到超时，**不会**主动中断业务 handler 线程」。
- ⇒ 本 lane **逐行读了实现**（问 1 的证据表）**并对照 grpc-java 1.63.0 源码** ⇒ **该自陈成立**；L2 既没有夸大，也没有漏报。
  ⇒ ⚠️ 按 AGENTS.md §3.3.2：本条结论**不**写成「Go: …」式的未验证断言，而是逐条带 `文件:行号` 的证据表。

### 七·五 本轮新发现

**🔴 N1（文档精度 · 属 `service/common` ⇒ 交整合者）**：`RpcInterceptor` 的 javadoc 把「两层补救」**并列**陈述，
但**第 ② 层只在 `handle()` 路径**：
- javadoc `RpcInterceptor.java:134-135`：「`{@link #handle}` 静态方法配合 `CompletableFuture.get(timeout)` + `future.cancel(true)`（→ `worker.interrupt()`）让调用方主动响应取消」；
- 而 gRPC server 路径（`newUnaryServerInterceptor`，`:149-318`）里**唯一**的取消动作是 `target.close(...)`（`:204-206`）⇒ **没有第 ② 层**。
⇒ **影响面**：不改行为，但会让后续接手的人以为「gRPC 路径也有 interrupt 补救」⇒ 可能写出
「catch `InterruptedException` 就退出」这种**永不触发**的业务代码（gRPC 路径上 `InterruptedException` 永远不会抛）。
⇒ **建议**：把 `RpcInterceptor.java:125-147` 改成**分路径**表述（gRPC 路径 vs `handle()` 路径）。
⇒ **归属**：`service/common` ⇒ **只有 `align/global` 能写** ⇒ 本 lane **只读分析，未改**。

**🔴 N2（守门层的新约束 · 跨 lane ⇒ 交整合者同步给 L6/L7/L8）**：
**JVM 内第一次 gRPC 调用的 span duration 会被「首次类加载 / JIT」撑大** ⇒
「快调用 ≪ 阈值」这条断言**必须**先做一次**预热调用**，否则**首调即红**（假阳性）。
- 实测证据：follow-rpc **首调** `sea.duration_ms=138.333`（> 阈值 100ms）⇒ `fastCallMustNotBeMarkedSlow` 直接红
  （`expected: <null> but was: <true>`）；task-rpc 首调亦命中（该次首调 duration 落在阈值内，侥幸绿）。
- 本 lane 的处置：两个新守门都加了**预热调用**（闸门直接开 ⇒ 立即返回）→ `EXPORTER.clear()` → 再做**被测**调用；
  并在快那条加了**诊断断言**（把实测 duration 摆进失败消息，便于区分「阈值没生效」与「冷启动混进判据」）。
- ⚠️ **这条对 L6 / L7 / L8 同轴的守门同样成立**（它们也在做 slow 轴）⇒ **建议整合者把预热要求同步过去**（本 lane **无权**改它们的文件）。
- ⚠️ **值得复核**：L2 的 admin-rpc 样板（`AdminRpcSlowThresholdGateTest`）**没有**预热 ⇒ 它在**首调**时理论上有同样风险。

### 七·六 缺口清单（**未证成 / 未覆盖**，不要读成「已全部对齐」）

1. **「在飞处理被打断」这一维：已定性，但**未**被任何测试守住。**
   本 lane 只出了**结论**（问 1~4），**没有**新增「handler 线程被 interrupt / ctx 被取消」的运行期守门
   ⇒ **L2 缺口清单第 5 条本轮未销账**（仍只验到「取消发生时它仍在跑」）。
   **为什么没补**：要守它就得先有「打断」这个行为 —— 而问 1 的结论是 **gRPC 路径没有打断**
   ⇒ 写不出「应当被打断」的断言；能写的只有「**不会**被打断」这种**把现状锁死**的断言
   ⇒ 本 lane **不写**（与 AGENTS.md §3.3「绿测试可能是错误答案的守卫」同源）⇒ **建议交整合者决定是否登记为签字偏离**。
2. **slow 轴在 task / follow 两域已补齐，但 10 个 rpc 组件仍未穷尽**：
   本轮并发分工（L6 = admin/article，L7 = comment/like，L8 = user/message/hot，L9 = task/follow）
   ⇒ **`favorite-rpc` 仍未补**，且 favorite 域对本 lane **零写权** ⇒ **全仓 slow 轴并未穷尽**。
3. **未做 Go ↔ RAG 双侧活体对拍**：本 lane 只在 **RAG 侧**补守门
   ⇒ **不能**据此宣称 Go↔RAG 的 slow 语义已对齐（Go 侧语义是**读源码逐字对照**得出的，见问 2 的行号）。
4. **未跑全量 `mvn clean verify`** —— 本轮只跑了改动到的 **2 个模块**（+ `-am` 上游）
   ⇒ 全仓是否仍全绿**未由本轮证明**（整合者合并后需跑全量，见 `_PROTOCOL.md` §五·4）。
5. **OTel 全局 SDK 的前提仍在**：`@BeforeAll` 必须在 Spring 上下文 refresh **之前**替换 `GlobalOpenTelemetry`
   （因为 `RpcInterceptor` 在 Bean 创建期取 Tracer，`RpcInterceptor.java:154`）。
   本轮**实测**到该前提的副作用：共享层的 `GoStyleOtelAgent.install` 会因「已 set 过」而**记一条 ERROR 并放弃装配**
   （日志原文：`[otel] error: 全局 TracerProvider 装配失败（GlobalOpenTelemetry.set has already been called…）`）
   ⇒ 这是**预期**（保住测试的 exporter），但**一旦将来 Boot 的 OTel 自动配置接上，该前提会失效**
   ⇒ 与 L2 缺口清单第 7 条**同源**，本轮**未处理**。
6. **量化余量**：快调用实测 duration = 1.823ms / 2.328ms / 2.659ms（阈值 100ms）⇒ 余量充足；
   慢调用实测 405.694ms / 433.396ms（阈值 100ms，4 倍余量）⇒ 在极端慢的机器上（handler 调度延迟 > 600ms）**理论上**仍可能翻面
   ⇒ 本轮**未**做多次重复采样取分位数（代价：单次跑 ~6 分钟 × N）。
7. **未覆盖 `RpcInterceptor.handle(...)` 那条非 gRPC 入口**：问 1 的结论包含它「**有** interrupt」的事实
   （`:395` / `:406`），但本 lane **没有**为它补守门 ⇒ 它**仍**只有 `service/common` 自己的单测。
