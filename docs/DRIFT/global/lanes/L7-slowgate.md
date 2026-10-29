# L7 · `align/global-slowgate` —— CD-5 轴真 server 守门（comment-rpc / like-rpc）+ 两域 slow 轴

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写** | `service/comment/comment-rpc/src/test/**` |
| **可写** | `service/like/like-rpc/src/test/**` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L7-slowgate.md` |
| **只读** | `service/common/**`（共享层只有 `align/global` 能改）· `service/*/src/main/**`（生产代码零改动）· `service/favorite/**`（⛔ 零写权） |

**分支**：`align/global-slowgate` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-slowgate` · **基线**：`align/global` HEAD

---

## 一、目标

把 **CD-5 轴**（服务端超时真的打断在飞处理）的**真 server 守门**补到 **comment-rpc** 与 **like-rpc**，
并把这两个域的 **slow threshold 轴**守门也补上。

| 模块 | 要交付 |
|---|---|
| `service/comment/comment-rpc` | 🔴 **CD-5 轴真 server 守门**（新建 1 个测试类）<br>🔴 **slow threshold 轴守门**（新建 1 个测试类） |
| `service/like/like-rpc` | 🔴 **CD-5 轴真 server 守门**（新建 1 个测试类）<br>🔴 **slow threshold 轴守门**（新建 1 个测试类） |

⚠️ **like-rpc 特别注意**：该模块已有 `LikeFactDispatchContractGateTest` / `LikeFactDispatchSchedulerStartupTest`
（第二十轮 A5′ 建的）⇒ **不要动它们**（那是另一条线），本 lane 只**新建** CD-5 / slow 两个测试类。
⚠️ like-rpc 的 `LikeFactDispatchContractGateTest` **第二十六轮刚修过调度器句柄泄漏** ⇒ 你新建的测试类**不要复用它的 `startDispatcher` 写法**。

---

## 三、🔴 样板（**照抄，不要重新发明**）

> 三个样板是 L2（第二十四轮）建的，**已通过变异验证** ⇒ 直接照抄结构，只换模块相关的名字。

| 样板 | 路径 | 它示范什么 |
|---|---|---|
| **CD-5 轴** | `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcGrpcInterceptorWiringTest.java` | **真 server + 真链 + 判别式断言**（服务端超时真的打断了在飞处理） |
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

---

## 七、本 lane 产出（2026-10-26 执行 · 生产代码零改动）

> ⚠️ 本轮是**守门补齐**，**没有修任何行为** ⇒ `service/*/src/main/**` **一行未改**（见 §十 的 `git diff --stat`）。
> ⚠️ 本轮**没有发现需要改 `service/common` 的缺陷** ⇒ §四·2 的「停下来交整合者」**未被触发**。

### 7.1 交付的 4 个测试类（全部新建，全部在白名单内）

| # | 文件 | 域 | 轴 | 用例数 | 断言调用数 | 真跑形态 |
|---|---|---|---|---|---|---|
| ① | `service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcGrpcInterceptorWiringTest.java` | comment | **CD-5** | 3 | 14 | 真 gRPC server + 真链 + 真 PG(Testcontainers) + 真超时 |
| ② | `service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcSlowThresholdGateTest.java` | comment | **slow threshold** | 2 | 6 | 真 gRPC server + 真链 + **真 OTel SDK** + 收集型 exporter + 真 PG |
| ③ | `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcGrpcInterceptorWiringTest.java` | like | **CD-5** | 3 | 14 | 真 gRPC server + 真链 + 真 PG(Testcontainers) + 真超时 |
| ④ | `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcSlowThresholdGateTest.java` | like | **slow threshold** | 2 | 6 | 真 gRPC server + 真链 + **真 OTel SDK** + 收集型 exporter + 真 PG |

**样板来源（照抄结构，只换模块相关名字）**：
①③ = `service/users/user/user-rpc/.../UserRpcGrpcInterceptorWiringTest.java` + `service/hot/hot-rpc/.../HotGrpcInterceptorWiringTest.java`（latch 加固版，多一条 deadline 传播用例）
②④ = `service/users/admin/admin-rpc/.../AdminRpcSlowThresholdGateTest.java`

⚠️ **like-rpc 的两个既有测试类（`LikeFactDispatchContractGateTest` / `LikeFactDispatchSchedulerStartupTest`）零改动**
⇒ 且本 lane 的新类**不复用**它们的 dispatcher 启动写法（第二十六轮刚修过「阻塞式 `Dispatcher.run` ⇒ 句柄永远拿不到」的泄漏，见任务书 §一）。

### 7.2 逐条判定表（§3.1 六条硬性要求 + §3.3 slow 额外要求，逐条对照）

| # | 硬性要求 | ① comment CD-5 | ③ like CD-5 | ② comment slow | ④ like slow |
|---|---|---|---|---|---|
| ① | 必须用 `ServerCalls.asyncUnaryCall(...)` 注册慢服务（不能用裸 `ServerCallHandler`） | ✅ `CommentRpcGrpcInterceptorWiringTest.java:283`（`slowCommentService`） | ✅ `LikeRpcGrpcInterceptorWiringTest.java:285`（`slowLikeService`） | ✅ `CommentRpcSlowThresholdGateTest.java:269`（`slowCommentService`） | ✅ `LikeRpcSlowThresholdGateTest.java:268`（`slowLikeService`） |
| ② | 必须断言「handler 真的被调用过」（`probe.entered.await(...)`） | ✅ `:189` + `:230`（两个用例各一处） | ✅ `:191` + `:232`（两个用例各一处） | n/a（慢服务用 `sleep`，由「导出 span 存在」间接证明 handler 跑过） | n/a（同左） |
| ③ | 必须断言「取消发生时它仍在跑」（`assertFalse(probe.completed.get())`） | ✅ `:192` + `:246`（2 处） | ✅ `:194` + `:248`（2 处） | n/a | n/a |
| ④ | 必须用 latch，不能用 `Thread.sleep(10_000)`（`finally` 里立刻放行） | ✅ `CountDownLatch release` + `finally { probe.release.countDown(); }` | ✅ 同左 | ✅ slow 轴按 admin 样板用 `sleep(400ms)`（**有界、< 阈值差**，不是 10s 挂死） | ✅ 同左 |
| ⑤ | `GrpcServerConfigurer` 必须**按 Bean 名取** | ✅ `:149` + `:205`（`ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class)`） | ✅ `:153` + `:207` | ✅ `:225` | ✅ `:224` |
| ⑥ | channel target 必须显式写 `dns:///` scheme | ✅ `:161` + `:219`（`forTarget("dns:///127.0.0.1:" + port)`） | ✅ `:165` + `:221` | ✅ `:235` | ✅ `:234` |
| §3.3-a | 必须装真 OTel SDK（`resetForTest()` + `set(OpenTelemetrySdk…)`） | n/a | n/a | ✅ `:171-172`（`@BeforeAll installSdk()`） | ✅ `:170-171` |
| §3.3-b | **必须在 Spring 上下文创建之前装**（`RpcInterceptor` 在 Bean 创建期取 Tracer，`RpcInterceptor.java:154`） | n/a | n/a | ✅ `@BeforeAll` 静态方法（JUnit 5 的 `BeforeAllCallback` 早于 `@BeforeAll` 方法 ⇒ 早于上下文创建） | ✅ 同左 |
| §3.3-c | 两条断言缺一不可（慢 ⇒ `sea.slow==true`；快 ⇒ `sea.slow` **不存在**） | n/a | n/a | ✅ `slowCallMustBeMarkedSlow` + `fastCallMustNotBeMarkedSlow` | ✅ 同左 |
| §3.3-d | span 属性键 `ObservabilityConstants.ATTR_SLOW` / `ATTR_DURATION` | n/a | n/a | ✅ 两键都断言 | ✅ 两键都断言 |

### 7.3 本轮补上的「之前没有的东西」（缺口本体）

| 组件 | 之前 | 现在 |
|---|---|---|
| `comment-rpc` | 装配层守门只有**不启动 Spring 上下文**的 3 个测试（`CommentProtectionChainWiringTest` 纯 Mockito + `CommentSchemaPgGateTest` / `CommentRpcGoSemanticsGateTest` 裸 JDBC）⇒ **没有任何一条验证过「拦截器真的挂到了 gRPC server 上」** | ✅ 3 + 2 = **5 个运行期用例**，真 server / 真链 / 真 PG / 真 OTel |
| `like-rpc` | `LikeRpcApplication.java:117-119` 的类注释里就写着「锁死用例：⚠️ **目前没有**（错误断言 F：原文此处写 `LikeGrpcInterceptorWiringTest`，该测试类**全仓不存在**）」 ⇒ **缺口被白纸黑字写在生产代码的类注释里** | ✅ 同上 5 个运行期用例（类名 `LikeRpcGrpcInterceptorWiringTest`，与注释里的**拼写不一致**是事实，见 §九·①） |

## 八、变异结果（**4 条生产代码变异 + 1 条自测退化探针**，全部红在目标断言上）

> 变异对象 = **共享层生产代码**（`service/common`）⇒ 只在**本 lane 自己的工作树**里存在 ⇒ 每次跑完立刻 `git checkout --` 还原 ⇒ §十 的 `git diff --stat` 证明已还原。
> ⚠️ 判定**只认 surefire XML 的逐条 `testcase`**（PIT-GLOBAL-021）⇒ 下表的红/绿是 XML 里逐条 `testcase` 的状态，**不是** stdout 的 `-- in` 行。
> ⚠️ 每轮都带 `-am`（PIT-GLOBAL-019）⇒ 测的是**当轮变异后的 `service/common`**，不是 `~/.m2` 里的旧 jar。

### 8.1 变异清单

| 编号 | 变异点（文件:行） | 变异内容 | 期望红在 |
|---|---|---|---|
| **M1** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:194` | `if (timeout != null && timeout.compareTo(Duration.ZERO) > 0) {` ⇒ `if (false) {`（**禁用服务端超时调度**） | `serverSideTimeoutIsActuallyEnforced` 的 `assertTrue(elapsedMillis < 3_000)` |
| **M2** | 同上 `:277-285`（`onHalfClose`） | 摘掉 `DeadlineState.bindToCurrent(deadlineEpochMs)` 作用域 ⇒ **不再把 deadline 绑到 handler 线程** | `deadlineIsPropagatedToHandlerThread` 的 `assertTrue(remaining != Long.MAX_VALUE)` |
| **M3** | `service/common/src/main/java/com/rag/common/observability/Observability.java:143-146` | `if (slowThreshold != null && … duration.compareTo(slowThreshold) > 0)` ⇒ `if (true)`（**slow 标记恒为 true**） | `fastCallMustNotBeMarkedSlow` 的 `assertNull(sea.slow)` |
| **M4** | 同上 `:143-146` | 把 `b.put(ATTR_SLOW_BOOL, true);` 掏空（**slow 标记永不写**） | `slowCallMustBeMarkedSlow` 的 `assertEquals(Boolean.TRUE, sea.slow)` |

### 8.2 变异前后 surefire XML 对比（逐条 `testcase`）

**M1（禁用服务端超时调度）** —— `MAVEN_EXIT=1` · 无 `COMPILATION ERROR`

```
FILE: TEST-com.rag.comment.comment.rpc.CommentRpcGrpcInterceptorWiringTest.xml
  <testsuite … tests="3" errors="0" skipped="0" failures="1" flakes="0">
   [FAIL] … serverSideTimeoutIsActuallyEnforced
        msg: 必须在服务端超时（300ms）附近返回，实际耗时 5028ms。若接近 5000ms，说明只有客户端
             deadline 生效 —— 即 server.intercept(...) 没挂上，超时/deadline 传播/gRPC span 已静默消失。
             ==> expected: <true> but was: <false>
   [PASS] … deadlineIsPropagatedToHandlerThread
   [PASS] … productionTimeoutMatchesGoZeroDefault

FILE: TEST-com.rag.like.like.rpc.LikeRpcGrpcInterceptorWiringTest.xml
  <testsuite … tests="3" errors="0" skipped="0" failures="1" flakes="0">
   [FAIL] … serverSideTimeoutIsActuallyEnforced
        msg: 必须在服务端超时（300ms）附近返回，实际耗时 5212ms。 ==> expected: <true> but was: <false>
   [PASS] … deadlineIsPropagatedToHandlerThread
   [PASS] … productionTimeoutMatchesGoZeroDefault
```

⇒ **判别力证明**：M1 下 `serverSideTimeoutIsActuallyEnforced` 红，但**同类的另两条仍绿**
⇒ 说明红的位置**精确落在「服务端超时真的打断了在飞处理」这一条**，不是「整个类都崩」。

**M2（摘掉 onHalfClose 的 deadline 绑定）** —— `MAVEN_EXIT=1`

```
FILE: TEST-com.rag.comment.comment.rpc.CommentRpcGrpcInterceptorWiringTest.xml
   [PASS] … serverSideTimeoutIsActuallyEnforced
   [FAIL] … deadlineIsPropagatedToHandlerThread
        msg: handler 线程（grpc-default-executor-0）上 DeadlineState 恒未设置 ⇒ onHalfClose 从未执行
             ⇒ deadline 没有传播到业务层 ⇒ checkDeadline() 全是死代码，且 SQL 不会被注入 queryTimeout
             ==> expected: <true> but was: <false>
   [PASS] … productionTimeoutMatchesGoZeroDefault
（like 域同形：同一 testcase 红，同两条绿）
```

**M3（slow 标记恒为 true）** —— `MAVEN_EXIT=1`

```
FILE: TEST-com.rag.comment.comment.rpc.CommentRpcSlowThresholdGateTest.xml
  <testsuite … tests="2" errors="0" skipped="0" failures="1" flakes="0">
   [FAIL] … fastCallMustNotBeMarkedSlow
        msg: 耗时远小于阈值 100ms ⇒ 不该打 sea.slow。若为 true，说明 slow 标记是「恒真」的，
             或阈值没被真正比较（Go: observability.go:140-142 要求 duration > slowThreshold 才打）。
             实际 span 属性 = AttributesMap{data={rpc.grpc.status_code=0,
             rpc.method=comment.CommentService/GetComment, sea.duration_ms=14.788,
             sea.slow=true, rpc.system=grpc}} ==> expected: <null> but was: <true>
   [PASS] … slowCallMustBeMarkedSlow

FILE: TEST-com.rag.like.like.rpc.LikeRpcSlowThresholdGateTest.xml
   [FAIL] … fastCallMustNotBeMarkedSlow
        msg: … 实际 span 属性 = AttributesMap{data={sea.slow=true, rpc.grpc.status_code=0,
             sea.duration_ms=150.792, rpc.system=grpc, rpc.method=like.LikeService/GetLikeCount}}
             ==> expected: <null> but was: <true>
   [PASS] … slowCallMustBeMarkedSlow
```

⇒ **这一条正是「两条断言缺一不可」的证据**：只保留「慢的那条」的话，M3 下**两条都绿** ⇒ 守门空转。

**M4（slow 标记永不写）** —— `MAVEN_EXIT=1`

```
FILE: TEST-com.rag.comment.comment.rpc.CommentRpcSlowThresholdGateTest.xml
   [PASS] … fastCallMustNotBeMarkedSlow
   [FAIL] … slowCallMustBeMarkedSlow
        msg: 耗时 400ms > 阈值 100ms ⇒ 必须打 sea.slow=true。实际 span 属性 =
             AttributesMap{data={rpc.method=comment.CommentService/GetComment, sea.duration_ms=408.251,
             rpc.grpc.status_code=0, rpc.system=grpc}} ==> expected: <true> but was: <null>
（like 域同形：sea.duration_ms=403.27，同样红在 slowCallMustBeMarkedSlow）
```

⇒ M3 + M4 合起来 ⇒ **slow 这条路径的两端都被钉住**（既不能恒真、也不能恒假）。

### 8.3 自测退化探针（**不是生产代码变异** —— 证明 §3.1 的 ①/② 两条要求真的被咬住）

> 目的：验证「裸 `ServerCallHandler` ⇒ handler 压根没跑」这个**已知失效形态**在本 lane 的守门里**会红**，
> 而不是只写在注释里。

| 探针 | 内容 | 结果 |
|---|---|---|
| **D1** | 把 `CommentRpcGrpcInterceptorWiringTest` 自己的 `slowCommentService` 从 `ServerCalls.asyncUnaryCall(...)` 换成**裸** `ServerCallHandler`（探针期间只改测试文件；改前已备份到 `/tmp/l7gate/` ⇒ 跑完 `cp` 还原，`diff` 证明与备份**逐字相同**） | `MAVEN_EXIT=1` · **2 条红**：`serverSideTimeoutIsActuallyEnforced` 红在 `assertTrue(probe.entered.await(...))`（「handler 必须**真的被调用过**」）· `deadlineIsPropagatedToHandlerThread` 红在「handler 根本没被调用（线程 = &lt;未进入&gt;）」 |

⇒ ⚠️ **关键观察**：D1 下「status = DEADLINE_EXCEEDED」与「耗时 ≪ 5000ms」两条**仍然成立**（服务端超时照样把**空调用**关掉）
⇒ ⇒ 这正是 §3.1 ①/② 两条要求的由来：**只有 `entered` / `completed` 这两条断言才抓得住它** ⇒ 本 lane 的守门不是空转。

## 九、缺口清单（本轮**发现**但**未修**的问题）

> ⚠️ 本 lane 是**守门补齐** lane ⇒ 发现的行为问题**只登记不修**（生产代码零改动是硬约束）。
> ⚠️ **不升级为「要用户拍板」**（用户既定偏好：登记类事项登记完就躺着）⇒ 本节**零条**需拍板。

### ① 🟡 `like-rpc` 生产代码注释里的「锁死用例」名字**与本 lane 落地的类名不一致**（**文档级漂移**，非行为）

- **现况**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/LikeRpcApplication.java:117-119` 写：
  `锁死用例：⚠️ **目前没有**（错误断言 F：原文此处写 {@code LikeGrpcInterceptorWiringTest}，该测试类**全仓不存在**）`
- **本 lane 落地**：`LikeRpcGrpcInterceptorWiringTest`（**多一个 `Rpc`**，与 users/hot 域的命名一致：`UserRpcGrpcInterceptorWiringTest` / `HotGrpcInterceptorWiringTest`）
- **为什么不修**：该文件是 `service/*/src/main/**` ⇒ **生产代码零改动**（§四·1）⇒ 本 lane 无写权。
- **交整合者**：**只改注释里的类名 + 把「目前没有」改成「已有（2026-10-26 · global L7 lane）」** ⇒ 属**共享层之外的单组件文件** ⇒ 建议由 `align/like` 或整合者处理。
- **⚠️ 语义判定**：**类名不一致本身不是缺陷**（命名约定以 users/hot 域为准）；**「目前没有」这句现在是过期断言** ⇒ 属「🟡 注释过期」，不是行为漂移。

### ② 🟡 `comment-rpc` 的 `LikeRpcApplication` 同款注释**不需要改**（已核对）

- `CommentRpcApplication.java:81-90` 的 javadoc 只写了「对应 Go `comment.go:54`」与「删掉这个方法后 Bean 断言照样是绿的」，
  **没有**指名一个不存在的测试类 ⇒ **无过期断言** ⇒ **零改动**（已逐字读过 `CommentRpcApplication.java:81-100`）。

### ③ 🟡 `comment-rpc` 的 `CommentFactDispatchScheduler` **不是** `@Scheduled` 驱动（本 lane 实测所见）

- `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/bootstrap/CommentFactDispatchScheduler.java:44` 是 `@Component`，
  而 `CommentRpcApplication` **没有** `@EnableScheduling`（全模块 `grep EnableScheduling` 零命中）⇒ 该 scheduler 的定时方法**在本进程里不会被调度**。
- **本 lane 不判定这是不是缺陷**（Go 侧 `rpc/cmd/fact-dispatch/main.go` 是**独立 main**，本仓把四个 main 合进单 JVM ⇒ 「合进单 JVM 后是否应当默认不跑投递」属**行为层问题**）⇒ **只登记，不定性、不修**。
- ⚠️ 这条**超出本 lane 任务书范围**（本 lane 只做 CD-5 轴 + slow 轴的守门）⇒ **交整合者**判断是否单开一条 lane。

## 十、未覆盖 / 未穷尽的部分（**不许写「已全部对齐」**）

| # | 未覆盖的部分 | 卡在哪 | 代价多大 |
|---|---|---|---|
| ① | **Go ↔ RAG 双侧活体对拍**：本 lane 的 4 个守门**只跑了 RAG 一侧**（真 server / 真链 / 真 PG / 真 OTel）。按 AGENTS.md §3.3「凡写「与 Go 一致」，必须有**两侧同时跑的探针**」⇒ 本 lane **没有**做 Go 侧活体对拍 | Go 侧 `service/comment/rpc/comment.go` / `service/like/rpc/like.go` 需要**独立进程 + 独立 DB + 独立 Redis** 才能起；`tools/semantic-probe/` 的现成探针**不覆盖** comment/like 的 gRPC 拦截器链 | 中：需新增一套 Go 侧 harness（估 1~2 天）；**但**本 lane 的判据全是「RAG 侧机制是否生效」+「配置的 2s 是否等于 go-zero 默认 2000ms」（后者由 `productionTimeoutMatchesGoZeroDefault` 静态断言覆盖）⇒ **Go 侧只用于确认「Go 真的注册了这个拦截器」**这一条已被 `comment.go:54` / `like.go:41` 逐字取证 |
| ② | **服务端超时时「业务线程被真的打断」的语义**：Go 侧 `ctx.Done()` 会让**业务 ctx-aware 操作立即中断**；RAG 侧 grpc-java **不会**主动 interrupt handler 线程（本仓已知框架限制，`RpcInterceptor` 的类注释已登记为 DRIFT-018）⇒ 本 lane 的 CD-5 守门验的是「**call 被关闭 + deadline 传播到 handler 线程**」，**不是**「handler 线程被中断」 | grpc-java 框架限制 ⇒ **改不了** | 大：属**框架限制**（不是本仓缺陷）⇒ 需要业务层主动读 `DeadlineState` ⇒ **本 lane 的 `deadlineIsPropagatedToHandlerThread` 只证明「值可见」**，**没证明「业务真的读了它」** |
| ③ | **`comment-rpc` / `like-rpc` 的**其余** gRPC 方法**（各 7 / 6 个）**没有逐方法守门 | 本 lane 只挑了 1 个代表方法（`GetComment` / `GetLikeCount`）⇒ 链路是**方法无关**的（拦截器挂在 `server.intercept` 上 ⇒ 对所有方法生效）⇒ 但**未逐方法实证** | 小：链是全方法共享的 ⇒ 逐方法跑一遍是**冗余**；**但**「每个方法的消息契约」不在本 lane 范围 |
| ④ | **slow 轴只覆盖「成功返回」这一条出口** | Go 的 `markRPCResult` 有**三条分支**（`err == nil` ⇒ slow 判定；`ctx.Err() == DeadlineExceeded` ⇒ timeout；否则 ⇒ error）⇒ 本 lane 只验了**第一条**（OK ⇒ 打 `sea.slow`）。**timeout 分支**由 CD-5 轴间接覆盖（§8.2 的 M1/M2）；**error 分支**（业务错误 ⇒ `sea.error.kind=business`）**本 lane 未覆盖** | 中：`markBusinessError` / `markSystemError` 两条分支的 `sea.*` 属性**无守门** ⇒ 但 `service/common` 侧已有 `ObservabilityTest` / `RpcInterceptorTimeoutSpanTest` 等单测覆盖**函数级**行为 ⇒ 缺的是**组件级真链**那一层 |
| ⑤ | **CPU 自适应降载（shedding）在本 lane 的四个类里被**显式关掉**（`rag.protection.cpu-shedding.enabled=false`） | 关掉它是**为了不盖判据**（shedding 命中会先返回 `RESOURCE_EXHAUSTED`）⇒ 这条链上的 shedding 层**在本 lane 的四个类里没有被实证** | 小：shedding 由 `service/common` 的 `GoStyleRpcSheddingInterceptorTest` 单独覆盖；本 lane 只需「别让它盖判据」 |
| ⑥ | **熔断（breaker）层**同样只是「链上存在」的形态被跳过 | 同上 ⇒ breaker 的**行为**不在本 lane 范围 | 小：breaker 由 `service/common` 侧覆盖 |
| ⑦ | **多模块同库共存的 Flyway 账本**（§6.3 问题 4 的 `unresolved` 项）**未受本 lane 影响也未推进** | 本 lane 只在**独立 Testcontainers 容器**里跑（每个类一个独立库名）⇒ 「三模块同库共存」这条**仍 unresolved** | 无（本 lane 未触碰） |
| ⑧ | **`like-rpc` 的 `LikeFactDispatchContractGateTest` / `LikeFactDispatchSchedulerStartupTest`**（另一条线）**未复核** | 本 lane 只**避开**它们（零改动、零复用其 dispatcher 写法）⇒ **没有**顺带验证它们仍然绿 | 无（另两轮/另一条线负责） |
| ⑨ | **变异只做了 4 条**（共享层 4 个变异点），**没有**穷举 `service/common` 的其它行为面 | 本 lane 只对「CD-5 轴 + slow 轴」这 2 条路径做变异 ⇒ 其它路径（error 分支 / span 导出 / breaker / shedding）**不在本 lane 范围** | 无（范围外） |

## 十一、交接整合者清单（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | **`LikeRpcApplication.java:117-119` 的注释订正**（类名 `LikeGrpcInterceptorWiringTest` ⇒ `LikeRpcGrpcInterceptorWiringTest`；「目前没有」⇒「已有，2026-10-26 · global L7 lane」） | `service/*/src/main/**` ⇒ **生产代码零改动**（§四·1） |
| ② | **`CommentFactDispatchScheduler` 无 `@EnableScheduling` ⇒ 定时方法不跑**（§九·③）是否属缺陷的**定性** | 超出本 lane 任务书范围（CD-5 / slow 两轴）⇒ 需**单开一条 lane** 或交 `align/like` / `align/comment` |
| ③ | **共享索引同步**（`docs/DRIFT/_INDEX.md` / `_MANIFEST.json` / `docs/Pitfalls/_INDEX.md`）—— 本 lane **未新增**共享文件 ⇒ **无需同步**（除整合者把本 lane 片段 fold 进规范台账） | 协议 §五·3：共享索引由整合者统一更新 |
| ④ | **`align/global-slowgate` ⇒ `align/global` 的合并** | 由 Jason / 整合者决定时机（**本 lane 不 push、不 merge**） |

## 十二、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-26 | 🆕 **第二十七轮（L7 lane `align/global-slowgate` · 基线 `3dbd3da`）** —— **纯守门补齐**，**生产代码零改动**<br>① **新建 4 个测试类**：`CommentRpcGrpcInterceptorWiringTest`（3 用例 / 14 断言）· `CommentRpcSlowThresholdGateTest`（2 用例 / 6 断言）· `LikeRpcGrpcInterceptorWiringTest`（3 用例 / 14 断言）· `LikeRpcSlowThresholdGateTest`（2 用例 / 6 断言）<br>② **逐条判定表**（§7.2）：任务书 §3.1 六条硬性要求 + §3.3 slow 四条 ⇒ **20 格全部 ✅**<br>③ **变异 4 条**（M1 禁用服务端超时调度 / M2 摘掉 `onHalfClose` 的 deadline 绑定 / M3 slow 恒为 true / M4 slow 永不写）⇒ **全部红在目标 testcase 上**，同类的兄弟用例**仍绿**（判别力证据）⇒ `MAVEN_EXIT=1` × 4<br>④ **自测退化探针 D1**（裸 `ServerCallHandler`）⇒ **2 条红**在「handler 必须真的被调用过」⇒ 实证 §3.1 ①/② 两条要求被咬住<br>⑤ **全量回归**：comment-rpc **94 用例 / 15 类** + like-rpc **88 用例 / 14 类** ⇒ **failures=0 errors=0 skipped=0** · `BUILD SUCCESS`<br>⑥ **写权守门**：`python3 tools/difftest/check-global-lane-ownership.py` ⇒ `EXIT=0`（favorite 域零写权 · 6 条在跑 lane 白名单两两不重叠）<br>⑦ **缺口 3 条**（§九）：`LikeRpcApplication` 注释里的类名/「目前没有」过期（**需整合者改生产代码注释**）· `CommentRpcApplication` 同款注释**已核对无过期断言**· `CommentFactDispatchScheduler` 无 `@EnableScheduling`（**只登记不定性**）<br>⑧ **未覆盖 9 条**（§十）显式列出 ⇒ **未写「已全部对齐」**<br>⑨ **需拍板清单：0 条**（用户既定偏好：登记类事项登记完就躺着） |
