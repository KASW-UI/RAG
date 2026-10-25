# L8 · `align/global-slowgate2` —— slow threshold 轴守门推广（user-rpc / message-rpc / hot-rpc）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写** | `service/users/user/user-rpc/src/test/**` |
| **可写** | `service/message/message-rpc/src/test/**` |
| **可写** | `service/hot/hot-rpc/src/test/**` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L8-slowgate2.md` |
| **只读** | `service/common/**`（共享层只有 `align/global` 能改）· `service/*/src/main/**`（生产代码零改动）· `service/favorite/**`（⛔ 零写权） |

**分支**：`align/global-slowgate2` · **工作树**：`../RAG.worktrees/global-slowgate2` · **基线**：`align/global` HEAD

---

## 一、目标

把 **slow threshold 轴**的守门从 admin-rpc 这**一个样板**推广到 **user-rpc / message-rpc / hot-rpc** 三个组件
（L2 的缺口②：这 9 个 rpc 组件里只有 admin-rpc 有逐域证据，其余 8 个没有）。

| 模块 | 要交付 |
|---|---|
| `service/users/user/user-rpc` | 🔴 **slow threshold 轴守门**（新建 1 个测试类，照 admin-rpc 那份改） |
| `service/message/message-rpc` | 🔴 **slow threshold 轴守门**（新建 1 个测试类） |
| `service/hot/hot-rpc` | 🔴 **slow threshold 轴守门**（新建 1 个测试类） |

⚠️ **user-rpc / message-rpc 已经有 X-5 轴的守门**（L2 建的 `UserRpcGrpcInterceptorWiringTest` / `MessageRpcTimeoutGateTest`）
⇒ **不要动它们** ⇒ 本 lane 只**新建** slow 轴的测试类。
⚠️ **hot-rpc 的 `HotGrpcInterceptorWiringTest` 已被 L2 加固过**（latch 取代 sleep）⇒ 同样**不要动**。

**这个 lane 的价值**：slow 判定只在共享层 `RpcInterceptor` **一处**实现 ⇒ 一处样板可覆盖大部分风险，
但**不等于**逐域已验 ⇒ 本 lane 把「逐域证据」补齐 ⇒ 每域都要能证明「本域的链真的走到了 slow 分支」。

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

---

## 七、台账（第二十七轮 · L8 · slowgate2 交付）

> 本节是本 lane 的**台账片段**（协议见 [`_PROTOCOL.md`](_PROTOCOL.md)）⇒ **自足**，不依赖
> `docs/DRIFT/global/` 的 4 份规范台账文件。
> ⚠️ 本 lane **不产出「要用户拍板」的问题**（用户既定偏好：登记类事项登记完就躺着）。
> ⚠️ **favorite 域零改动**（域零写权，§四·3）。

### 7.1 交付物清单（**只新建** 3 个测试类，零改动既有守门）

| # | 模块 | 测试类 | 路径 |
|---|---|---|---|
| ① | `service/users/user/user-rpc` | `UserRpcSlowThresholdGateTest` | `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcSlowThresholdGateTest.java` |
| ② | `service/message/message-rpc` | `MessageRpcSlowThresholdGateTest` | `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageRpcSlowThresholdGateTest.java` |
| ③ | `service/hot/hot-rpc` | `HotRpcSlowThresholdGateTest` | `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSlowThresholdGateTest.java` |

**断言条数**：每个类 **11 处 `assert*` 调用点** ⇒ 一次全量跑 **15 次断言执行**
（慢用例 9 次 + 快用例 6 次；`assertFalse` 只在慢用例里执行）。

| 断言 | 位置 | 守的是什么 |
|---|---|---|
| `assertEquals(Boolean.TRUE, sea.slow)` | 慢用例 | **慢 ⇒ 必须打 `sea.slow=true`** |
| `assertNotNull(sea.duration_ms)` | 慢用例 | Go `observability.go:138` 无条件打 `AttrDuration` |
| `assertTrue(durationMs ≥ 300-50)` | 慢用例 | **比较用的必须是真实耗时**，不是凭空来的标记 |
| `assertNull(sea.slow)` | 快用例 | **快 ⇒ 必须没有 `sea.slow`** ⇒ 证明是「真比较」不是「恒真」 |
| `assertTrue(probe.entered.await(...))` | 公共 | ② **handler 真的被调用过** |
| `assertFalse(probe.completed.get())` | 慢用例 | ③ **调用在飞时 handler 仍在跑** |
| `assertTrue(probe.completed.get())` | 公共 | 响应回来时 handler 已跑完（时序自洽） |
| `assertNotNull(configurer)` | 公共 | ⑤ `rpcInterceptorConfigurer` Bean 存在 |
| `assertNotNull(resp)` | 公共 | 调用**成功**（Go：slow 判定只在 `err == nil` 时走） |
| `assertTrue(found.isPresent())` | 公共 | 必须导出**名为** `service/method` 的 span（span 名 = 方法全名） |

### 7.2 逐条判定表（任务书 §3.1 六条硬性要求 + §3.3 slow 轴要求）

| # | 要求 | ① user-rpc | ② message-rpc | ③ hot-rpc |
|---|---|---|---|---|
| ① | `ServerCalls.asyncUnaryCall(...)` 注册慢服务（**非**裸 `ServerCallHandler`） | ✅ `slowUserService` | ✅ `slowMessageService` | ✅ `slowHotService` |
| ② | 断言「handler 真的被调用过」（`probe.entered.await`） | ✅ `assertTrue(probe.entered.await(10, SECONDS))` | ✅ 同左 | ✅ 同左 |
| ③ | 断言「取消/在飞发生时它仍在跑」（`assertFalse(probe.completed.get())`） | ✅ 慢用例内 | ✅ 慢用例内 | ✅ 慢用例内 |
| ④ | 用 latch，**非** `Thread.sleep(10_000)`（`finally` 立刻放行） | ✅ `probe.release.await(holdMillis, …)` + `finally` countDown | ✅ 同左 | ✅ 同左 |
| ⑤ | `GrpcServerConfigurer` **按 Bean 名取** | ✅ `ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class)` | ✅ 同左 | ✅ 同左 |
| ⑥ | channel target 显式写 `dns:///` scheme | ✅ `NettyChannelBuilder.forTarget("dns:///127.0.0.1:" + port)` | ✅ 同左 | ✅ 同左 |
| §3.3-1 | 真 OTel SDK（`resetForTest()` + `set(OpenTelemetrySdk…)`），**`@BeforeAll`** 装（早于上下文创建） | ✅ | ✅ | ✅ |
| §3.3-2 | 慢的那条：`sea.slow == true` | ✅ | ✅ | ✅ |
| §3.3-3 | 快的那条：`sea.slow` **不存在** | ✅ | ✅ | ✅ |
| §3.3-4 | span 属性键用 `ObservabilityConstants.ATTR_SLOW` / `ATTR_DURATION` | ✅ | ✅ | ✅ |
| §3.2 | 真 server + 真链 + 真 span（**非** Mockito mock） | ✅ 真 gRPC server + 容器里的 `rpcInterceptorConfigurer` | ✅ 同左 | ✅ 同左 |
| §3.2 | 真 PG | ✅ Testcontainers `user_rpc_slow_threshold_it` | ✅ Testcontainers `message_rpc_slow_threshold_it` | ➖ **不需要**（hot-rpc 无 `DataSource`） |
| §3.2 | CPU 自适应降载关掉（否则 shedding 先返回 `RESOURCE_EXHAUSTED` 盖掉判据） | ✅ `rag.protection.cpu-shedding.enabled=false` | ✅ 同左 | ✅ 同左 |

**被测方法全名**（span 名 = `MethodDescriptor#getFullMethodName()`，**无前导斜杠**）：
`user.UserService/GetUser` · `message.MessageService/MarkNotificationRead` · `hot.HotService/GetHotArticles`。

### 7.3 变异结果（**9 次跑测**：3 模块 × 3 变异）

> 变异对象 = **共享层生产代码**（`service/common`）⇒ 只在变异期间存在，跑完即 `git checkout --` 还原。
> 三道闸（PIT-GLOBAL-018）：跑前删旧 `TEST-*.xml` + 看 maven 退出码 + grep `COMPILATION ERROR`。
> 判定**只认 surefire XML**（PIT-GLOBAL-021）⇒ 逐条 `testcase`。

| # | 模块 | 变异点（`service/common`） | 预期红在 | **实际红在（surefire XML 逐条）** | maven 退出码 | `COMPILATION ERROR` |
|---|---|---|---|---|---|---|
| 1 | hot-rpc | **A** 恒真：`markDurationAndSlow` 无条件打 `sea.slow` | `fastCallMustNotBeMarkedSlow` | ✅ `fastCallMustNotBeMarkedSlow` = **FAILURE**（`sea.slow=true`, `duration=26.5ms`）· `slowCallMustBeMarkedSlow` = PASS | **1** | no |
| 2 | hot-rpc | **B** 恒假：删掉打 `sea.slow` 那一行 | `slowCallMustBeMarkedSlow` | ✅ `slowCallMustBeMarkedSlow` = **FAILURE**（`expected: <true> but was: <null>`, `duration=313.1ms`）· 快用例 = PASS | **1** | no |
| 3 | hot-rpc | **C** 忽略本域配置：`RpcServerInterceptorFactory` 一律走 `Observability.slowThreshold()`（500ms 兜底） | `slowCallMustBeMarkedSlow` | ✅ `slowCallMustBeMarkedSlow` = **FAILURE**（`expected: <true> but was: <null>`, `duration=301.552ms`）· 快用例 = PASS | **1** | no |
| 4 | user-rpc | **A** 恒真 | `fastCallMustNotBeMarkedSlow` | ✅ `fastCallMustNotBeMarkedSlow` = **FAILURE**（`sea.slow=true`, `duration=15.95ms`）· 慢用例 = PASS | **1** | no |
| 5 | user-rpc | **B** 恒假 | `slowCallMustBeMarkedSlow` | ✅ `slowCallMustBeMarkedSlow` = **FAILURE**（`expected: <true> but was: <null>`, `duration=314.858ms`）· 快用例 = PASS | **1** | no |
| 6 | user-rpc | **C** 忽略本域配置 | `slowCallMustBeMarkedSlow` | ✅ `slowCallMustBeMarkedSlow` = **FAILURE**（`expected: <true> but was: <null>`, `duration=308.326ms`）· 快用例 = PASS | **1** | no |
| 7 | message-rpc | **A** 恒真 | `fastCallMustNotBeMarkedSlow` | ✅ `fastCallMustNotBeMarkedSlow` = **FAILURE**（`sea.slow=true`）· 慢用例 = PASS | **1** | no |
| 8 | message-rpc | **B** 恒假 | `slowCallMustBeMarkedSlow` | ✅ `slowCallMustBeMarkedSlow` = **FAILURE**（`expected: <true> but was: <null>`, `duration=303.031ms`）· 快用例 = PASS | **1** | no |
| 9 | message-rpc | **C** 忽略本域配置 | `slowCallMustBeMarkedSlow` | ✅ `slowCallMustBeMarkedSlow` = **FAILURE**（`expected: <true> but was: <null>`, `duration=310.417ms`）· 快用例 = PASS | **1** | no |

⇒ **9/9 变异全部转红，且每次都红在目标断言上**（不是红在更早的断言上 ⇒ PIT-GLOBAL-018 ② 不适用）。
⇒ 每次都**恰好 1 条** testcase 红、另 1 条 PASS ⇒ 守门**非空转**。

**基线（未变异）**：`mvn -o test -pl <模块> -am -Dtest='<类>'` ⇒ 三域各 **2 tests / 0 failures / 0 errors / 0 skipped**。

### 7.4 🟢 一条**新发现**：slow 阈值的「配置被忽略」是一条**可静默发生**的退化

- **现象**：`RpcServerInterceptorFactory` 一旦不读 `props.getSlowThreshold()`，就会回退到
  `Observability.slowThreshold()` ⇒ **500ms**（Go 的 `DefaultSlowThreshold`，`observability.go:21`）。
- **为什么危险**：**任何只看「有没有 `sea.slow`」的守门都抓不住它** ——
  只要慢调用的耗时 ≥ 500ms，兜底阈值也会判 slow ⇒ 断言照样绿。
- **本 lane 的应对**：把「慢调用占用时长」**钉在 300ms**（**必须 &lt; 500ms**）⇒
  300ms &gt; 200ms（本域配置）但 300ms &lt; 500ms（兜底）⇒
  **配置生效 ⇒ 判 slow；配置被忽略 ⇒ 不判 slow ⇒ 红**。
  ⇒ 这正是上表第 3/6/9 行（MUT-C）的变异所证明的。
- **性质**：**不是行为漂移**（生产代码当前是对的），而是**「守门会假绿」的取值陷阱** ⇒
  已写成三份守门 javadoc 里的**硬约束**（改占用时长前必须重读那条约束）。
- ⚠️ 同一条约束对 **admin-rpc 那份样板**同样成立（100ms 阈值 + 400ms 占用，400 &lt; 500）⇒ 两份守门口径一致。

### 7.5 与样板（admin-rpc）的两处**有意分歧**（都不是行为改动）

| # | 分歧 | admin-rpc 样板 | 本 lane 三份 | 为什么 |
|---|---|---|---|---|
| 1 | 造耗时的方式 | `Thread.sleep(sleepMillis)` | **latch + 带超时 await** | ③（`assertFalse(probe.completed.get())`）在 sleep 方案下**写不出来**（等到断言时 handler 早跑完了）⇒ 必须 latch ⇒ `finally` 里能**立刻**放行 |
| 2 | 阈值 / 占用 | 100ms / 400ms | 200ms / 300ms | 两侧各留 100ms 余量（见 §7.4）⇒ 「配置被忽略」这条退化才抓得住 |

⇒ 样板**结构**未改：真 server · 真链（`rpcInterceptorConfigurer`）· 真 OTel SDK + 收集型 exporter · 两条断言（慢=true / 快=无）。

### 7.6 缺口清单（**未覆盖 / 未穷尽** —— 本 lane 明确没做到的部分）

| # | 未覆盖的部分 | 卡在哪 | 代价估算 |
|---|---|---|---|
| ① | **超时轴（X-5）本 lane 完全没做** | 三域已有守门（`UserRpcGrpcInterceptorWiringTest` / `MessageRpcTimeoutGateTest` / `HotGrpcInterceptorWiringTest`）⇒ 任务书要求**不要动** | — （已由既有守门覆盖） |
| ② | **`sea.timeout` 与 `sea.slow` 互斥**（Go `rpc.go:86-89`：只有 `err == nil` 才走 slow 判定） | 本 lane 两个用例**都走 OK 分支** ⇒ 「超时的调用**不**带 `sea.slow`」**没有**逐域证据 | **低-中**：在同一模块加第 2 个 `@SpringBootTest` 类（`rpc-timeout=50ms` + 占用 300ms ⇒ 服务端超时 ⇒ 断言 span 上**无** `sea.slow`）⇒ 每域多一次上下文启动（≈ 40~60s） |
| ③ | **`RpcInterceptor.handle(...)` 这条非 gRPC Server 的入口** | 它走同一个 `markRpcResult` ⇒ 共享层同一处实现 ⇒ 本 lane 的三个 gRPC 守门**不能**证明它被走到 | **低**：直接调用 `handle(...)` 即可（不需真 server）⇒ 但它**不在** grpc server 链上 ⇒ 属**共享层**维度 ⇒ 归 `global/common-tails`，**不归本 lane** |
| ④ | **env var 兜底路径**（`SEA_OBSERVABILITY_SLOW_THRESHOLD[_MS]`） | 本 lane 走的是 Spring 配置路径（`observability.slow-threshold`）⇒ env 兜底**没有**逐域证据 | **低**：同一条守门加一个 `SEA_OBSERVABILITY_SLOW_THRESHOLD` 环境变量的 `@SpringBootTest` 变体即可（env 变量在 surefire 侧需另配） |
| ⑤ | **HTTP 侧的 slow 判定**（`HttpMiddlewareFilter`） | 本 lane 只做 gRPC；HTTP 侧在 **api** 组件 ⇒ 不在本 lane 白名单 | 归 api 域 / `global` 收口 |
| ⑥ | **逐方法穷尽** | 每域只验了**一个**方法（user=`GetUser` / message=`MarkNotificationRead` / hot=`GetHotArticles`）⇒ 证明的是「**本域的链**走到了 slow 分支」，**不等于**该域每个 RPC 方法都验过 | **中**：user 9 个 / message 9 个 / hot 4 个 = 22 个方法 ⇒ 需按方法参数化注册慢服务（每方法 ≈ 1.5s ⇒ 单域 ≈ 30s），但每个方法的请求消息要按 proto 构造 ⇒ 实现成本中等。**本 lane 未做**（任务书只要求「本域的链真的走到了 slow 分支」） |
| ⑦ | **并发形态下的 span 归属** | exporter 是**全局静态** + `clear()` 后按 span 名取第一个 ⇒ 同 JVM 内并行跑同类用例会串扰 ⇒ 本类是**顺序**执行 | **低**：改成按 `rpc.method` + 时间窗 / trace id 取 ⇒ 十几行 |
| ⑧ | **生产默认阈值（500ms）下的正向判据** | 本 lane 把阈值压到 200ms ⇒ 「阈值 = 500ms 时真慢调用会被标记」这一形态**未直接**验 | **低**：加一条 600ms 占用的用例（但那样会**丢掉** §7.4 的「配置被忽略」判别 ⇒ 需**另起一个类**用 600ms 占用） |
| ⑨ | **剩下 6 个 rpc 组件**（article / comment / favorite / follow / like / task） | 由 L6 / L7 / L9 覆盖；`favorite` 域由别的 lane 处理 ⇒ **本 lane 零触碰** | — |

### 7.7 「零改动」自证

| 纪律 | 自证方式 | 结果 |
|---|---|---|
| 生产代码零改动 | `git diff --stat` | **无 `src/main/**` 条目**（只有 3 个 `src/test/**` 新文件） |
| `service/common` 零改动 | 9 次变异全部 `git checkout --` 还原后 `git diff --stat` | **空** |
| favorite 零改动 | `git status --porcelain` | **无 `service/favorite/**`、无 `docs/*/favorite/**`** |
| 白名单 | `python3 tools/difftest/check-global-lane-ownership.py` | **EXIT=0**（6 条在跑 lane 写权两两不重叠 · 10 个共享文件无人拥有 · favorite 零写权） |
| 变异只在隔离工作树 | 变异脚本 `/tmp/L8_mutate.py` 只操作**本 lane 工作树**的 `service/common`（PIT-GLOBAL-020）⇒ 不影响其它 lane 的工作树 | ✅ |
| 三道闸 | 每次跑测前删旧 `TEST-*.xml`（闸 1）+ 记录 maven 退出码（闸 2）+ grep `COMPILATION ERROR`（闸 3） | ✅ 9/9 次 `maven_exit=1` + `COMPILATION ERROR=no` |
| 判定只认 surefire XML | 逐条 `testcase` 解析（PIT-GLOBAL-021） | ✅（**注**：首轮 9 次里 user-rpc 的 3 次因脚本 glob 漏了 `service/users/user/user-rpc/` 这层目录而**没抓到 XML** ⇒ 已修 glob **重跑 3 次**并重新取证） |

### 7.8 结论（一句话）

**slow 判定这一个轴，user-rpc / message-rpc / hot-rpc 三域各自都拿到了「逐域证据」** ——
每个域都能证明「**本域的链真的走到了 slow 分支**」，且 3 个变异（恒真 / 恒假 / 忽略本域配置）
在每个域都**红在目标断言上** ⇒ 这三份守门**非空转**。
⚠️ **但这不等于「已全部对齐」**：超时轴（X-5）本 lane 没做、slow 与 timeout 的互斥关系、
`handle(...)` 入口、env 兜底、逐方法穷尽、并发形态 —— 见 §7.6。
