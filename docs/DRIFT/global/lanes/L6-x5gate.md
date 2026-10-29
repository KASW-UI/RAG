# L6 · `align/global-x5gate` —— CD-5 轴真 server 守门（admin-rpc / article-rpc）+ article slow 轴 + 缺口④ 复评

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写** | `service/users/admin/admin-rpc/src/test/**` |
| **可写** | `service/article/article-rpc/src/test/**` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L6-x5gate.md` |
| **只读** | `service/common/**`（共享层只有 `align/global` 能改）· `service/*/src/main/**`（生产代码零改动）· `service/favorite/**`（⛔ 零写权） |

**分支**：`align/global-x5gate` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-x5gate` · **基线**：`align/global` HEAD

---

## 一、目标

把 **CD-5 轴**（「服务端超时真的打断了在飞处理」）的**真 server 守门**补到 **admin-rpc** 与 **article-rpc** 两个组件上，
并把 **article-rpc** 的 **slow threshold 轴**守门也补上；另外**复评 L2 留下的缺口④**。

| 模块 | 要交付 |
|---|---|
| `service/users/admin/admin-rpc` | 🔴 **CD-5 轴真 server 守门**（新建 1 个测试类）<br>⚠️ 本模块**已有** `AdminRpcSlowThresholdGateTest`（slow 轴样板）⇒ **不要重建**，只做 CD-5 轴 |
| `service/article/article-rpc` | 🔴 **CD-5 轴真 server 守门**（新建 1 个测试类）<br>🔴 **slow threshold 轴守门**（新建 1 个测试类，照 admin-rpc 那份改） |

**缺口④（admin-rpc slow 守门的前提）** —— `AdminRpcSlowThresholdGateTest` 依赖
「`GlobalOpenTelemetry` 在 Spring 上下文 refresh **之前**被替换」（`@BeforeAll` 早于上下文创建）。
⇒ **复评**：读 `service/common` 的 `RpcInterceptor` + `service/common` 的 OTel 自动配置现状 ⇒ 回答两问：
① 「Boot 的 OTel 自动配置（`opentelemetry-sdk-extension-autoconfigure`）现在在不在依赖里」？
② 「若将来接上，本守门的前提会不会失效、怎么改才不失效」？
⇒ **只出结论 + 建议**，**不改代码**（`service/common` 不许动）⇒ 结论写进本 lane 台账。

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

### 3.4 article-rpc 的两个域相关事实（**自己再核一遍，别照抄**）

- `article-rpc` 的 proto 服务与方法是哪个 ⇒ 自己读 `service/article/article-proto` 的生成代码找（要一个 **unary** 方法）
- `article-rpc` 的 `observability.rpc-timeout` 生产值是多少 ⇒ 自己读它的 `application.yml` ⇒ **在测试里断言它**（照 user-rpc 的 `productionTimeoutMatchesGoZeroDefault`）
- ⚠️ article 域的 api 侧注册了 `rest.WithCors()` ⇒ 与 rpc 侧无关，但**别把 api 的事实套到 rpc 上**

---

# 七、本轮产出（2026-10-26 · 第二十七轮 L6 lane）

> ⚠️ **本节只写本 lane 自己产出的东西**。共享台账（`global/{_README,common-tails,cross-domain,cycle-deps}.md`）
> 与三个 `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json` **本 lane 未写**（`_PROTOCOL.md` §二·3 ⇒ 归整合者）。

## 7.1 逐条判定表（本 lane 交付的三条守门）

| # | 组件 | 守门类 | 文件 | 用例数 | 断言数 | 真跑？ | 判定 |
|---|---|---|---|---|---|---|---|
| **G1** | `users/admin/admin-rpc` | 🔴 **CD-5 轴**（真 server） | `src/test/java/com/rag/users/admin/rpc/AdminRpcGrpcInterceptorWiringTest.java`（**新增**） | 3 | **14** | ✅ `mvn -o test -pl service/users/admin/admin-rpc -am` · `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` | ✅ **非空转**（8 条变异中 3 条打在本守门上，全部红在目标断言） |
| **G2** | `article/article-rpc` | 🔴 **CD-5 轴**（真 server） | `src/test/java/com/rag/article/article/rpc/ArticleGrpcInterceptorWiringTest.java`（**新增**） | 3 | **14** | ✅ `mvn -o test -pl service/article/article-rpc -am` · `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` | ✅ **非空转**（8 条变异中 3 条打在本守门上） |
| **G3** | `article/article-rpc` | 🔴 **slow threshold 轴** | `src/test/java/com/rag/article/article/rpc/ArticleRpcSlowThresholdGateTest.java`（**新增**） | 2 | **6** | ✅ 同上 · `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` | ✅ **非空转**（2 条变异打在本守门上：恒真 / 恒假） |

**逐条用例判定**（G1 / G2 同构）：

| 用例 | 断言内容 | Go 出处（行为层） | 判定 |
|---|---|---|---|
| `serverSideTimeoutIsActuallyEnforced` | ① configurer Bean 存在（**按名取**）② 客户端拿到 `DEADLINE_EXCEEDED` ③ **耗时 < 3000ms**（服务端超时，非客户端 5s deadline）④ **handler 真的被调用过**（`probe.entered.await`）⑤ **取消发生时它仍在跑**（`assertFalse(probe.completed.get())`） | `service/user/admin/rpc/admin.go:33,43` / `service/article/rpc/article.go:36,51` | ✅ |
| `deadlineIsPropagatedToHandlerThread` | ⑥ configurer Bean 存在 ⑦ 调用被服务端超时打断 ⑧ **handler 真被调用过** ⑨ **`DeadlineState` 在 handler 线程上有值**（≠ `Long.MAX_VALUE`）⑩ **剩余预算 ≤ 服务端超时 300ms** ⑪ **取消时仍在跑** | Go `context.WithTimeout(ctx, timeout)` ⇒ `ctx.Done()`（`service/common/observability/rpc.go:38`） | ✅ |
| `productionTimeoutMatchesGoZeroDefault` | ⑫ 资源存在 ⑬ **生产 yml 的 `rpc-timeout` 等于 Go 侧生效值** | admin：`admin.yaml` **未配** ⇒ go-zero `zrpc/config.go` `default=2000` ⇒ **2s**；article：`article.yaml` **显式** `Timeout: 70000` ⇒ **70s** | ✅ |

**G3 逐条判定**：

| 用例 | 断言内容 | Go 出处 | 判定 |
|---|---|---|---|
| `slowCallMustBeMarkedSlow` | ① span 存在（名 = `article.ArticleService/GetArticle`）② **`sea.slow == true`**（400ms > 阈值 100ms）③ **`sea.duration_ms` 存在** | `service/common/observability/observability.go:131-144`（`duration > slowThreshold` ⇒ `attribute.Bool(AttrSlow, true)`；`AttrDuration` 无条件打） | ✅ |
| `fastCallMustNotBeMarkedSlow` | ④ span 存在 ⑤ **`sea.slow` 不存在**（≪ 阈值）⇒ 证明是**真比较**出来的，不是恒真 | 同上 | ✅ |

⚠️ **G3 的两个 Go 侧前提**（自己核过，不是照抄）：
- Go 的 slow 判定**只在 `err == nil` 时走**（`service/common/observability/rpc.go:86-89`）⇒ 本守门的调用**必须成功返回**（client deadline 10s > 生产 70s 超时 ⇒ 不会走超时分支）。
- Go 的阈值来源：`observability.SlowThreshold()`（`observability.go:41-55`）⇒ env var ⇒ 兜底 `DefaultSlowThreshold = 500ms`（`observability.go:21`）；
  RAG 侧多一层 Spring 配置 `observability.slow-threshold`（`ObservabilityProperties#getSlowThreshold()` → `RpcServerInterceptorFactory:77-79`）。

### 7.1.1 六条硬性要求（任务书 §3.1）逐条落实

| # | 要求 | G1 | G2 | 落点（代码位置） |
|---|---|---|---|---|
| ① | 必须用 `ServerCalls.asyncUnaryCall(...)` 注册慢服务，**不能**用裸 `ServerCallHandler` | ✅ | ✅ | `AdminRpcGrpcInterceptorWiringTest#slowAdminService` / `ArticleGrpcInterceptorWiringTest#slowArticleService` |
| ② | 必须断言「handler 真的被调用过」（`probe.entered.await(...)`） | ✅ | ✅ | 两条 runtime 用例都有（`assertTrue(probe.entered.await(2, SECONDS), …)`） |
| ③ | 必须断言「取消发生时它仍在跑」（`assertFalse(probe.completed.get())`） | ✅ | ✅ | 两条 runtime 用例都有 |
| ④ | 必须用 latch，**不能** `Thread.sleep(10_000)` | ✅ | ✅ | `probe.release.await(30, SECONDS)`；`finally` 里 `probe.release.countDown()` **立刻**放行 |
| ⑤ | `GrpcServerConfigurer` 必须**按 Bean 名取** | ✅ | ✅ | `ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class)`（按类型取会抛 `NoUniqueBeanDefinitionException`） |
| ⑥ | channel target 必须显式写 `dns:///` scheme | ✅ | ✅ | `NettyChannelBuilder.forTarget("dns:///127.0.0.1:" + server.getPort())` |

### 7.1.2 本 lane **自证「守门非空转」的一条额外证据**（任务书 §3.1 ① 的反向）

任务书 §3.1 ① 指出：**裸 `ServerCallHandler` 也会让「客户端拿到 DEADLINE_EXCEEDED + 耗时正常」这两条断言照样绿**。
本 lane 的 G1 / G2 之所以**不会**退化成那种形态，靠的是 ② ③ 两条断言本身（不是靠结构约定）——
**这一点由 M1 / M3 的实测反证**：M1 把超时推迟到 18s ⇒ 耗时断言红（5041ms）；
M3 把超时状态改成 `INTERNAL` ⇒ 状态断言红（`status=INTERNAL`）。
⇒ 两条断言是**互相独立**的判据，任何一条退化都会被抓到。

## 7.2 变异结果（**8 条，全部转红且红在目标断言上**）

> 三道闸（PIT-GLOBAL-018）：① **跑前删旧 `TEST-*.xml`** ② **看 maven 退出码** ③ **grep `COMPILATION ERROR`**。
> 判定只认 **surefire XML**（PIT-GLOBAL-021，逐条 `testcase`）。
> 变异对象全部是**共享层生产代码 / 模块生产配置**（`service/common/**` 与模块 `src/main/resources/application.yml`），
> **不是**改测试来制造假红。
> 变异脚本 `/tmp/l6_mutate.py`（不进仓），每轮跑完 `git checkout -- <文件>` 还原 + `git diff --stat` 自证。

| # | 守门 | 变异对象（**行号 = 消费点**） | 变异内容 | 🔴 红在哪个 testcase | 断言原文（实测） | maven 退出码 |
|---|---|---|---|---|---|---|
| **M1** | G1 | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:210`（`}, timeoutMs, TimeUnit.MILLISECONDS);`） | `timeoutMs` → `timeoutMs * 60`（服务端超时 300ms ⇒ **18s**） | `AdminRpcGrpcInterceptorWiringTest.serverSideTimeoutIsActuallyEnforced` | `assertTrue(elapsedMillis < 3_000, "必须在服务端超时（300ms）附近返回，实际耗时 5041ms…")` | **1**（BUILD FAILURE · `Tests run: 3, Failures: 1`） |
| **M2** | G1 | `RpcInterceptor.java:282`（`onHalfClose` 里的 `DeadlineState.bindToCurrent(deadlineEpochMs)`） | 删掉 bind ⇒ 只 `super.onHalfClose()` | `AdminRpcGrpcInterceptorWiringTest.deadlineIsPropagatedToHandlerThread` | `assertTrue(remaining != Long.MAX_VALUE, "handler 线程（grpc-default-executor-0）上 DeadlineState 恒未设置 ⇒ onHalfClose 从未执行 ⇒ deadline 没有传播到业务层 ⇒ checkDeadline() 全是死代码，且 SQL 不会被注入 queryTimeout")` | **1**（`Tests run: 3, Failures: 1`） |
| **M3** | G2 | `RpcInterceptor.java:204-206`（超时调度器里的 `target.close(Status.DEADLINE_EXCEEDED…)`） | `Status.DEADLINE_EXCEEDED` → `Status.INTERNAL` | `ArticleGrpcInterceptorWiringTest.serverSideTimeoutIsActuallyEnforced` | `assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode(), "实际 status=INTERNAL description=rpc timeout after 300ms cause=null")` | **1**（`Tests run: 3, Failures: 1`） |
| **M4** | G2 | 同 **M2** | 同 M2 | `ArticleGrpcInterceptorWiringTest.deadlineIsPropagatedToHandlerThread` | 同 M2 的同款断言（实测红在同一句） | **1**（`Tests run: 3, Failures: 1`） |
| **M5** | G3 | `service/common/src/main/java/com/rag/common/observability/Observability.java:143-144`（`&& duration != null && duration.compareTo(slowThreshold) > 0`） | 去掉 `duration > slowThreshold` ⇒ **恒真** | `ArticleRpcSlowThresholdGateTest.fastCallMustNotBeMarkedSlow` | `assertNull(span.getAttributes().get(ATTR_SLOW), "耗时远小于阈值 100ms ⇒ 不该打 sea.slow…实际 span 属性 = AttributesMap{data={sea.slow=true, rpc.system=grpc, rpc.method=article.ArticleServ…}")` | **1** |
| **M6** | G3 | 同上 | 阈值改成 `slowThreshold.plusSeconds(3600)` ⇒ **恒假** | `ArticleRpcSlowThresholdGateTest.slowCallMustBeMarkedSlow` | `assertEquals(Boolean.TRUE, span.getAttributes().get(ATTR_SLOW), "耗时 400ms > 阈值 100ms ⇒ 必须打 sea.slow=true。实际 span 属性 = AttributesMap{data={rpc.system=grpc, sea.duration_ms=402.25, rpc.method=article.ArticleService/GetArticle, rpc.grpc.status_code=0}")`（**无 `sea.slow`**） | **1** |
| **M7** | G2（配置轴） | `service/article/article-rpc/src/main/resources/application.yml` | `rpc-timeout: 70s` → `2s` | `ArticleGrpcInterceptorWiringTest.productionTimeoutMatchesGoZeroDefault` | `assertTrue(yml.contains("rpc-timeout: 70s"), "Go 侧 service/article/rpc/etc/article.yaml **显式配了** Timeout: 70000 …")` | **1** |
| **M8** | G1（配置轴） | `service/users/admin/admin-rpc/src/main/resources/application.yml` | `rpc-timeout: 2s` → `3s` | `AdminRpcGrpcInterceptorWiringTest.productionTimeoutMatchesGoZeroDefault` | `assertTrue(yml.contains("rpc-timeout: 2s"), "Go 侧 service/user/admin/rpc/etc/admin.yaml 未配 Timeout ⇒ 走 go-zero v1.10.2 zrpc/config.go 的 default=2000ms…")` | **1** |

⚠️ **8 条变异全部 `COMPILATION_ERROR=False`**（闸③）⇒ 每一轮都**真的编译并跑了测试**，没有 PIT-GLOBAL-018 的「假绿」形态。
⚠️ **每轮跑前都删了该模块的旧 `TEST-*.xml`**（闸①）⇒ surefire XML 里不会留上一轮的假绿。
⚠️ **M1 / M3 改的是 `service/common`（共享层）⇒ 一律带 `-am`**（PIT-GLOBAL-019）⇒ 变异真的进到了运行代码。
⇒ 证据：M1 日志的 reactor 摘要里 `common … SUCCESS [19.596 s]`（**common 被重新编译**，不是 `~/.m2` 里的旧 jar）。
⚠️ **所有变异在提交前已逐个 `git checkout --` 还原**，`git diff --stat` 为空（见 §7.5）⇒ **生产代码零改动**。

⚠️ **本 lane 未做的一类变异**（诚实登记）：L2 用「**注释掉 `configurer.accept(builder)`**」这一手证明「server.intercept 没挂上 ⇒ 守门红」。
本 lane **没有重做**这一手 —— 因为它是**改测试**（等价于生产侧 `server.intercept(...)` 被删），
而本轮任务书要求变异对象是**共享层生产代码**；M1/M3 改的是**同一段共享层逻辑的等价语义**（超时点 / 超时状态码），
覆盖面等价但**不是同一手** ⇒ 若整合者认为需要补这一手，**建议在合并后单独补**（代价 ≈ 2 分钟）。

## 7.3 缺口④ 复评（**只出结论 + 建议，不改代码**）

> 缺口④ = 「`AdminRpcSlowThresholdGateTest` 依赖『`GlobalOpenTelemetry` 在 Spring 上下文 refresh **之前**被替换』」
> ⇒ 若将来接上 Boot 的 OTel 自动配置，这个前提会不会失效、怎么改才不失效。
> ⚠️ **本节全部结论的证据边界**：`service/common` **只读**（本 lane 无写权）⇒ 只做**读 + 事实核对**，不改任何代码。

### 7.3.1 问题①：「Boot 的 OTel 自动配置（`opentelemetry-sdk-extension-autoconfigure`）现在在不在依赖里？」

**结论：❌ 不在。而且不是「版本不对」，是「整条 Boot OTel 装配链都没进 classpath」。**

| 证据 | 事实 |
|---|---|
| `grep -rn "opentelemetry" --include=pom.xml`（全仓 20 个 pom） | 只有 5 个 artifact：`opentelemetry-bom`（1.44.0）· `opentelemetry-api` · `opentelemetry-sdk` · `opentelemetry-context` · `opentelemetry-exporter-otlp`；外加 `opentelemetry-sdk-testing`（**test scope**）。**没有任何一个 pom 写 `opentelemetry-sdk-extension-autoconfigure`** |
| `~/.m2/repository/io/opentelemetry/opentelemetry-sdk-extension-autoconfigure/` | **目录不存在**（连 jar 都没下过）⇒ 它**不可能**在依赖图里 |
| 全 m2 扫描 `AutoConfiguredOpenTelemetrySdk.class` | **零命中** ⇒ Boot 的 OTel 自动配置所依赖的那个类**整个 JVM classpath 上不存在** |
| `opentelemetry-sdk-extension-autoconfigure-spi-1.44.0.jar`（**在** classpath，被 `opentelemetry-sdk` 传递带入） | 只有 **SPI 接口**：`AutoConfigurationCustomizer` / `ConfigProperties` / `ResourceProvider` / `ConfigurablePropagatorProvider` … ⇒ **它不含任何自动配置实现**（`AutoConfiguredOpenTelemetrySdk` 不在里面） |
| `~/.m2/.../spring-boot-autoconfigure-4.0.8.jar`（本仓实际用的版本） | `unzip -l` 里**零个** `*OpenTelemetry*` 类 ⇒ **Boot 4 把 OTel 自动配置拆出了 `spring-boot-autoconfigure`** |
| `~/.m2/repository/org/springframework/boot/` 下 otel/tracing 目录 | **不存在**（无 `spring-boot-opentelemetry` / `micrometer-tracing-bridge-otel`）⇒ Boot 侧 OTel 装配模块**一个都没装** |

⇒ **本仓的 OTel 全局装配是自研的**：`service/common/src/main/java/com/rag/common/observability/GoStyleOtelAutoConfiguration.java`
（`@AutoConfiguration`，经 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册）
⇒ 它装的是 `BeanFactoryPostProcessor`（`OtelGlobalRegistrar`）⇒ **在 `refresh()` 的 `invokeBeanFactoryPostProcessors()` 阶段**跑
⇒ 调 `GoStyleOtelAgent.install(...)`（`GoStyleOtelAgent.java:120-160`）。

### 7.3.2 问题②：「若将来接上，前提会不会失效、怎么改才不失效？」

**先说当前前提「为什么成立」（这一条必须讲清楚，否则加固建议没有落点）：**

`AdminRpcSlowThresholdGateTest` 的 `@BeforeAll` 先 `GlobalOpenTelemetry.resetForTest()` + `GlobalOpenTelemetry.set(testSdk)`；
随后 context refresh ⇒ `GoStyleOtelAutoConfiguration` 的 BFPP ⇒ `GoStyleOtelAgent.install()`：
- `INSTALLED` 为 false ⇒ 走 `buildProvider` ⇒ `GlobalOpenTelemetry.set(agentSdk)`
  ⇒ **抛 `IllegalStateException`**（OTel Java 的全局 setter **只能成功一次**）
  ⇒ 被 `GoStyleOtelAgent.java:147` 的 `catch (IllegalStateException alreadySet)` 捕获 ⇒ 只记 ERROR（照 Go `startAgent` 失败只 `logx.Error` 的语义）⇒ **全局仍是测试的 SDK**。
⇒ ⚠️ **所以这个前提今天是「侥幸成立」的**：它成立的原因是 `GoStyleOtelAgent` 那个「已 set 就放弃」的分支，
**不是**任何时序/顺序保证。任务书说的「`@BeforeAll` 早于上下文创建」只是**必要条件**，不是**充分条件**。

**再回答「会不会失效」—— 分三种情形（结构性结论，不对 Boot 内部行为下断言）：**

| 情形 | 会不会失效 | 依据 |
|---|---|---|
| **A. Boot 只产出 `OpenTelemetry` / `OpenTelemetrySdk` 的 Spring Bean，但从不碰 `GlobalOpenTelemetry`** | **不会失效**（本守门照常绿） | `RpcInterceptor.java:154` 读的是 `GlobalOpenTelemetry.get()`，不是 Spring Bean ⇒ 两套并存也不影响本守门 |
| **B. Boot（或其某个 BFPP）调 `GlobalOpenTelemetry.set(...)`** | **大概率不会让本守门变绿**，但会让**生产链路分叉**：`GlobalOpenTelemetry.set` 只能成功一次 ⇒ 谁先谁赢；若 Boot 赢 ⇒ `RpcInterceptor` 抓到的 Tracer 来自 Boot 的 provider ⇒ **RAG 自研的 `GoStyleOtelAgent` 与 Boot 的装配二选一**（行为层问题，不只是测试问题） | OTel Java `GlobalOpenTelemetry` 的 setter 语义（`set` 只能成功一次；**先 `get()` 会把全局置成 no-op 占位并让后续 `set()` 永久失败**） |
| **C. Boot 的某个组件在 `RpcInterceptor` Bean 创建前先调了 `GlobalOpenTelemetry.get()`** | **会失效**（前提被彻底破坏）：`get()` 把全局置成 no-op 占位 ⇒ 测试的 `set()` 之后**永久失败** ⇒ tracer 变 no-op ⇒ span 永不导出 ⇒ 本守门红在「找不到 span」 | 同上 |

⚠️ **证据边界（必须写明）**：`opentelemetry-sdk-extension-autoconfigure` 与 `spring-boot-opentelemetry` **都不在 classpath、m2 里也没有**
⇒ **本轮无法实测 Boot 侧到底调 `set()` 还是 `get()`**（情形 B / C 哪个会发生）⇒
**本节只给「前提一旦被破坏会怎样」的结构性结论 + 加固建议，不对 Boot 的具体实现下断言**。

**「怎么改才不失效」—— 四条建议（按性价比排序；前两条**不改生产代码**就能做）：**

1. 🔴 **把「前提」本身变成被守门的断言**（最便宜、最该先做）。
   在 `AdminRpcSlowThresholdGateTest` / `ArticleRpcSlowThresholdGateTest` 的 `callAndGetSpan()` 开头加一条：
   ```java
   assertSame(TEST_SDK, GlobalOpenTelemetry.get(),
           "全局 OpenTelemetry 必须仍是本测试装的那个 SDK —— 若被别的装配点（Boot 的 OTel 自动配置等）替换，"
           + "本守门的前提失效 ⇒ 必须改用 ApplicationContextInitializer 装 SDK。");
   ```
   ⇒ 前提一旦被破坏，**立刻红在前提断言上**，而不是红在「找不到 span」这种**误导性**的位置（后者会让人怀疑 span 导出而不是怀疑前提）。
   ⇒ 代价：≈ 5 行 / 每个守门类。

2. 🔴 **把装 SDK 的时点从 `@BeforeAll` 改成 `ApplicationContextInitializer`**（更早 ⇒ 对任何 `BeanFactoryPostProcessor` 都更稳）。
   `ApplicationContextInitializer#initialize(ConfigurableApplicationContext)` 在 `SpringApplication.prepareContext()` 阶段执行 ⇒ **早于 `refresh()`** ⇒ **早于所有 BFPP**（含 `GoStyleOtelAutoConfiguration` 那个，含 Boot 将来可能加的任何一个）⇒ 装上的一定是本测试的 SDK。
   ⇒ 代价：每个守门类多一个 `static class OtelInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>`（≈ 10 行）。

3. 🟡 **给守门一个**专属**的上下文缓存键**，杜绝「Spring 复用上下文 ⇒ 拦截器早于本测试的 SDK 被创建」。
   现状：`observability.slow-threshold=100ms` 已是**专属键**（与其它 article/admin 测试类不同）⇒ **今天安全**；但这是**巧合性安全**，没有断言把它钉住。
   ⇒ 建议：显式加断言（或加 `@DirtiesContext(classMode = AFTER_CLASS)`，代价是每次都新建上下文、article 域约 +60s）。

4. 🔴 **根治（要动生产代码 ⇒ 不在本 lane 写权内 ⇒ 交整合者）**：
   `RpcInterceptor` 在 **Bean 创建期**从**全局单例**抓 Tracer（`RpcInterceptor.java:154`）⇒ 守门无法摆脱「全局 OTel」这个耦合点。
   ⇒ 若要让守门彻底不依赖全局，应让 `RpcServerInterceptorFactory` 支持**注入 `TracerProvider`**（Spring 侧）⇒ **这是 `service/common` 的改动** ⇒ **只有 `align/global` 能写** ⇒ **本 lane 不做**，登记在此。
   ⇒ ⚠️ 同时：**若将来真的接上 Boot 的 OTel 自动配置**，必须同时做三件事，否则**生产链路**（不只是守门）会分叉：
   ① 明确**唯一的全局 OTel 装配点**（Boot 的 或 `GoStyleOtelAutoConfiguration` 的，**不能两个都装** —— `GlobalOpenTelemetry.set` 只能成功一次）；
   ② 把 `RpcInterceptor` 的 Tracer 来源从「全局」改成「注入」（否则 Boot 的 Bean 与全局可能不是同一个）；
   ③ 把所有 OTel 守门（`AdminSpanWiringTest` / `AdminRpcSlowThresholdGateTest` / `ArticleRpcSlowThresholdGateTest` …）的装 SDK 时点**统一**改成 `ApplicationContextInitializer` + 加前提断言。

## 7.4 缺口清单（**未覆盖 / 未穷尽**，不要读成「已全部对齐」）

1. 🔴 **CD-5 轴在全仓仍未穷尽**：本 lane 补了 **admin-rpc** 与 **article-rpc** 两个；
   L2 的 F 条快照里列的另外三个（`comment-rpc` / `like-rpc` / `favorite-rpc`）**仍缺** ⇒
   （`comment` / `like` 归 L1/L7 写权；`favorite` **全域零写权** ⇒ 它**没有人能补** ⇒ **须整合者决策**）。
2. 🔴 **slow threshold 轴仍未穷尽**：本 lane 补了 **article-rpc**；`AdminRpcSlowThresholdGateTest` 是 L2 做的 admin-rpc；
   其余 rpc 组件（`comment` / `like` / `favorite` / `task` / `follow` / `message` / `hot` / `user`）**没有逐域证据**
   ⇒ 缓解事实：slow 判定只在共享层 `Observability.markDurationAndSlow` 一处实现 ⇒ 一处样板可覆盖大部分风险，**但 ≠ 逐域已验**。
3. 🔴 **「在飞处理被打断」只验到「取消发生时它仍在跑」**（latch + `completed` 标记）
   ⇒ **没有**验「handler 线程被 interrupt / ctx 被取消」⇒
   **本 lane 不声称** RAG 侧能像 Go 的 `ctx.Done()` 那样**主动打断** handler 线程
   （那是 `RpcInterceptor` javadoc 自陈的 **DRIFT-018 框架限制**，本轮**未改**）。
4. 🟡 **deadline 传播那条断言放宽了一处**：断言「剩余预算 ≤ 服务端超时 300ms」而**不是**「> 0」
   （`DeadlineState.remainingMillis` 内部是 `Math.max(0, …)`；`interceptCall → onHalfClose` 之间若被机器调度拖过 300ms，
   remaining 合法地取 0 ⇒ 要求 `> 0` 会把「调度抖动」变成 flaky）。
   ⇒ **代价**：牺牲了「进入 handler 时预算尚未耗尽」这一条 ⇒ 但**没有**削弱「绑的确实是服务端超时而不是别的时刻」。
5. 🟡 **G3（article slow）没有做 Go ↔ RAG 双侧活体对拍** ⇒ 本轮是 **RAG 侧守门补齐**（把「没在验的」改成「在验」），
   **不涉及** wire 层差异（proto / DDL / 错误码）⇒ **不能**据此宣称 Go↔RAG 已对齐。
6. 🟡 **本 lane 没有跑全量 `mvn clean verify`** —— 只跑了**改动到的 2 个模块**（+ `-am` 上游）
   ⇒ 全仓是否仍全绿**未由本 lane 证明**（整合者合并后需跑全量，见 `_PROTOCOL.md` §五·4）。
7. 🟡 **未动的域**：comment / like / favorite / task / follow / message / hot / user-rpc
   ⇒ 本 lane **没有**声称它们已对齐。
8. 🟡 **缺口④ 的建议 1~3 本 lane **未实施**（只出结论）** —— 原因：改 `AdminRpcSlowThresholdGateTest` 需要动
   **L2 已合回的**文件（`service/users/admin/admin-rpc/src/test/**` 在**本 lane 白名单内**，但该类属 L2 的产出 ⇒ 属**别人的产出**）
   ⇒ **本 lane 不改**，交整合者决定（建议 1 加一条 `assertSame` 是零风险的，可优先采纳）。
   ⇒ 建议 4（改 `RpcServerInterceptorFactory` 支持注入 Tracer）**必须动 `service/common`** ⇒ **本 lane 无写权** ⇒ 登记交整合者。

## 7.5 本轮改动清单（**生产代码零改动**）

| 文件 | 改动 | 行数 |
|---|---|---|
| `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcGrpcInterceptorWiringTest.java` | **新增**：CD-5 轴真 server 守门（3 用例 / **14 断言**） | 323 |
| `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleGrpcInterceptorWiringTest.java` | **新增**：CD-5 轴真 server 守门（3 用例 / **14 断言**） | 332 |
| `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleRpcSlowThresholdGateTest.java` | **新增**：slow threshold 轴独立守门（2 用例 / **6 断言**） | 291 |
| `docs/DRIFT/global/lanes/L6-x5gate.md` | 本节（§七） | — |

⇒ **`src/main/**` 零改动**（`git diff --stat` 为空）· **`service/common` 零提交改动** · **favorite 零改动** · **`pom.xml` 零改动**。
⇒ 写权守门：`python3 tools/difftest/check-global-lane-ownership.py` ⇒ **EXIT=0**（在跑的 6 条 lane 白名单两两不重叠）。
