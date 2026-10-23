# `hot-rpc` 踩坑台账（§31-a：这里记「为什么会踩」，不记差异本身）

> **范围**：`service/hot/hot-rpc`。**核对日期**：2026-10-23（第二轮语义验证）。
> 差异本体在 [`../../DRIFT/hot/rpc.md`](../../DRIFT/hot/rpc.md)（`DRIFT-HOTRPC-001..007` + `G-HOT-001/002/003`）。
> 本文件只回答四件事：**症状像什么 · 真实原因（框架/语言机制）· 为什么测试抓不到 · 下次怎么防**。
> 本轮共 **6 条坑**（P-HOTRPC-01..06）。

---

## P-HOTRPC-01 🔴「不崩、不越界、静默算错」：Go 的 `uint64 % w` 在 Java 里没有等价写法

**现象**：所有测试全绿、所有变异被捕获、proto 逐字节一致、指标名逐字节一致 ——
但双侧活体对拍一跑，**生产口径 Top-100 里有 16 条与 Go 不同**（各 16 条只在一侧）。

**真实原因**：
Go 的 `h.Sum64() % uint64(hk.width)` 里 `Sum64()` 是**无符号** 64 位。
Java 的 `long` 是**有符号**的，FNV-1a 结果最高位为 1 时 `h < 0`，于是

```java
(int)(h % width)                      // Java：向零截断 ⇒ 结果在 (-width, 0]
remainder < 0 ? remainder + width : remainder   // 补正一次
```

补正后得到的是 `Euclidean(-a, width)`，而 Go 要的是 `Euclidean(2^64 - a, width)`，
**两者恒差 `2^64 mod width`**（w=1000 → 616；w=7 → 2；**永远不为 0**）。
结果**一定落在 `[0, width)`** ⇒ 永不越界、永不崩、**日志零告警**。

⚠️ **本轮还踩了第二层**：`Math.floorMod(h, (long) width)` **同样错**（实测输出与原写法完全一致），
因为 `floorMod` 也作用在有符号 `h` 上。唯一正确的是 **`Long.remainderUnsigned(h, (long) width)`**。
⇒ **「无符号取模」在 Java 里只有一个正确入口：`Long.remainderUnsigned`。**
（`Integer` / `Long` 的 `toUnsignedXxx` 只解决**数值**，不解决**取模**。）

**为什么测试抓不到**：
- `HeavyKeeperTest` 只断言**相对次序**（谁比谁大），**从不断言桶位**；
- 出错的结果仍然满足「降序」「长度 ≤ k」「分数正确」，只是**成员集合**不同；
- 10/10 变异全是「改比较、改次序、改 TopK 门槛」，**没有一个变异碰过 `hash()` 本身**。

**留下的守门网**：本轮补的 `CASE-HK-04/05/06`（生产口径 400 key 的集合比对 + 51 条列号 fixture）。

**下次怎么防**：
1. **凡是跨语言移植 `uint32/uint64` 运算，逐个 API 过一遍这张表**：
   | 语义 | Go | Java 正确写法 | 常见错法 |
   |---|---|---|---|
   | 数值 | `float64(u32)` | `Integer.toUnsignedLong(u)` | 直接 `(double) intValue` |
   | 比较 | `a > b`（u32） | `Integer.compareUnsigned(a,b)` | `a > b` |
   | 相减 | `b.count -= weight`（自然回绕） | `int -= int`（两补码自然回绕）✅ | 转成 long 再减 |
   | 取模 | `h % uint64(w)` | `Long.remainderUnsigned(h, w)` | `%` + `+w` 补正、`floorMod`、`& 0xFFFF…L` 掩码 |
   | 位模式 | `h.Sum64()` | `h`（long 位模式相同） | — |
2. **对拍要在「有碰撞」的参数下做**。上一轮探针取 `width=100000` 并自陈「避免桶冲突」
   ⇒ 它**结构上不可能**触发 `hash()` 的影响 ⇒ 「逐字节一致」只覆盖了无冲突区间。
   ⇒ **凡是为了确定性而放宽参数，都要问一句：放宽掉的那段行为还算不算被验证了？**
3. **把 Go 侧的中间值固化成 fixture**，不要只比对最终输出。最终输出（榜单）一乱就分不清是哪一层错的。

---

## P-HOTRPC-02 🔴 Boot 4 把 Jackson 自动配置也搬走了 —— 而且**加官方依赖并不能解决它**

**现象**：把 `HotRpcApplication` 真的启动一次 ⇒
```
APPLICATION FAILED TO START
Parameter 0 of constructor in …HotEventConsumer required a bean of type
'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

**真实原因**：Boot 4.0.8 的模块化拆分**不只影响 Kafka，也影响 Jackson**：
- `JacksonAutoConfiguration` 已不在 `spring-boot-autoconfigure` 里，搬到了独立模块 **`spring-boot-jackson`**；
- 更狠的是它装配出来的是 **Jackson 3**（`tools.jackson.databind.json.JsonMapper`，
  类上就是 `@ConditionalOnClass(JsonMapper.class)`），**不是 Jackson 2 的
  `com.fasterxml.jackson.databind.ObjectMapper`**；
- 本仓库的 Jackson 2 是从 `com.rag:common` **传递**进来的 —— **只有 jar，没有 Bean**。

⚠️ **本轮实测过的反直觉结论**：把 `spring-boot-jackson` 加到 classpath 上重跑，**仍然同一个失败**。
⇒ **「缺自动配置就加对应的 starter」这条反射在这里是错的** ——
加对了也只是引入一份用不上的 Jackson 3 自动配置，**并不会**产生 Jackson 2 的 Bean。

**为什么测试抓不到**：46 条测试**全是 `new HotEventConsumer(new ObjectMapper(), …)`** ——
单测自己 new 了一个 `ObjectMapper` 进去，于是**永远不会**发现容器里没有这个 Bean。
**纯 Mockito / 手 new 的测试对「Bean 能不能被装配出来」零敏感。**

**留下的守门网**：无（本轮只发现了，没修）。
仓库内**已有正确范式**：`service/article/article-rpc/.../config/Jackson2ObjectMapperConfiguration.java`
——显式 `@Bean ObjectMapper`，并显式关掉 `FAIL_ON_UNKNOWN_PROPERTIES`。
⚠️ 同款缺口：`like-rpc` 有 6 处构造注入同一个 `ObjectMapper`，**同样起不来**。

**下次怎么防**：
1. **每个新组件接手的第一件事：`java -cp … 启动一次`**，不是 `mvn compile`，也不是 `mvn test`。
   `AGENTS.md §0` 已经写过这条，`message-rpc`（21 条全绿、一次没起过）与本组件是同一个剧本。
2. **Boot 4 拆出去的模块清单要单独维护**，因为「classpath 上没有那个类」和「有那个 jar」是两件事：
   `spring-boot-kafka`（上一轮补对了）、`spring-boot-jackson`（本轮，又一个）。
   ⚠️ 判据仍是 §3.3.1：**看 `@ConditionalOnClass` 与 `@Bean` 的返回类型**，不要看依赖名。
3. **单测里 `new ObjectMapper()` 是最危险的注解级幻觉之一** ——
   它让「Bean 缺失」这类问题在整个测试周期里保持沉默。

---

## P-HOTRPC-03 🟠 Boot 4 的 `@Scheduled` 默认**单线程** —— 而 Go 侧是两个 goroutine

**现象**：代码里两条 `@Scheduled(fixedRate=…)`（30s 快照 + 10s 合并）看起来完全并行；
Go 侧也确实是两个 goroutine。**实际上 Java 侧共用一个只有 1 个线程的调度器。**

**真实原因**：`spring.task.scheduling.pool.size` 的**默认值是 `1`**
（Boot 4.0.8 源码 `TaskSchedulingProperties.java`：`private int size = 1;`），
而 `hot-rpc/application.yml` 没配这个键 ⇒ `taskScheduler` 是 `ThreadPoolTaskScheduler(poolSize=1)`
⇒ **合并慢就推迟快照**，Redis 抖动时 `periodicSync` 的实际间隔可能远超 30s。

**为什么测试抓不到**：契约测试全是对**单个方法**的直接调用，没有任何一条断言
「两条定时任务能同时跑」或「快照间隔 = 30s」。

**下次怎么防**：
1. **凡是把 Go 的多条 goroutine 定时任务搬成 `@Scheduled`，默认它们是串行的** ——
   在 `application.yml` 里显式写 `spring.task.scheduling.pool.size`，别依赖默认。
2. 取证要落到**默认值源码行**（`private int size = 1;`），不是文档、不是记忆（§3.3.1）。

---

## P-HOTRPC-04 🟠 库默认值里的**重试**，两侧可以差 3 倍而没人看得出来

**现象**：Redis 客户端的 `new` 语句两侧几乎一样（`Addr` + `Password` / `host` + `timeout`），
看起来完全对齐。**但 Go 默认重试 3 次，Java 默认 0 次。**

**真实原因**：go-redis `Options.init()`：`if opt.MaxRetries == 0 { opt.MaxRetries = 3 }`
（消费点，不是声明点）。配置里**根本没写**这个参数 —— 它藏在库里。
Lettuce 侧没有对应默认。

**为什么测试抓不到**：失败路径的两侧**最终输出都是「这次快照没成」**，
差别只在「试了几次」和「指标涨了几次」—— 没有一条测试断言尝试次数。

**下次怎么防**：
1. **`redis.NewClient(&Options{...})` / `RedisConnectionFactory` 这类「只填必要项」的调用，
   必须把「其余全部走库默认值」当成一次显式决策**（§3.3.1 第 1 条）。
   把 `DialTimeout / ReadTimeout / WriteTimeout / PoolSize / MaxRetries` 五个默认值抄进台账。
2. **失败路径要断言「尝试次数」，而不只是断言「失败」**。

---

## P-HOTRPC-05 🟡 客户端默认值里的**位点提交节奏**，会改变崩溃后的数据

**现象**：`enable-auto-commit: false` + `ack-mode: record` 看起来是「精确对齐 Go 的
『消费完就 MarkMessage』」。但 Go 的 MarkMessage 之后还有一层：`AutoCommit.Interval = 1s` 的批量提交。

**真实原因**：`sarama@v1.50.2/config.go:588-589`（**消费点**）默认
`AutoCommit.Enable=true` + `AutoCommit.Interval=1s`。Go 侧每次 rebalance 前只保证「最近 1 秒的位点可能没提交」。

**为什么抓不到**：要复现必须「处理完 → 立刻 kill」，且 `hk.Add` 非幂等才有可见后果。

**下次怎么防**：**对齐客户端配置时，除了 `enable-auto-commit` 还要看「提交粒度」**：
Kafka 侧 Go 是「标脏 + 每秒刷盘」，Java RECORD 是「每条刷盘」。这不是对错问题，
但**必须登记成 OBSERVABLE DIFFERENCE**，不能因为「都提交了」就判 EQUIVALENT。

---

## P-HOTRPC-06 🟠 消费侧的「永不外抛」契约，会**同时吞掉三类问题**

**现象**：三样东西叠在一起，让本轮的两条硬缺陷**完全没有症状**：
1. `HotEventConsumer` 吞掉一切 `RuntimeException`（drop 契约）；
2. 容器配了 `DefaultErrorHandler(FixedBackOff(0,0))`（零重试，位点照常推进）；
3. 本组件**进程根本起不来**（P-HOTRPC-02）。

⇒ 症状只有一句：**「启动静默失败，热榜永不更新，日志零告警」。**

**真实原因**：Go 侧的对应路径**没有 recover**
（`observability/mq.go:30` 是裸的 `err := fn(consumeCtx)`；sarama 的 claim goroutine 全模块无 `recover`）
⇒ Go 会**崩进程**并重投；Java 会**吞掉并继续**。
⚠️ Java 的 catch 是刻意的，但**类注释里没写「Go 侧会崩」** ⇒ 这是一条**未经签字的有意偏离**。

**为什么抓不到**：契约测试断言的正是「不抛异常」——
它把「吞掉」当成了正确契约，而**从未断言「Go 在同样输入下会怎么做」**（无 `Go: …` 行的探针，也就没法发现分歧）。

**留下的守门网**（第一轮已建，本轮确认有效）：
「凡实现『永不外抛』的入口，必须同时给被调方配一条能暴露异常的独立测试」——
`HeavyKeeperTest` / `HeavyKeeperUint32OverflowTest` 就是这条网。
⚠️ **但它只能抓崩溃类缺陷，抓不到「静默算错」类缺陷**（P-HOTRPC-01 就是靠对拍抓的）。

**下次怎么防**：
1. **「入口永不外抛」必须配一个对拍探针，而不是只配一个「不抛」的测试。**
   「不抛」只证明实现自洽，不证明与真源一致。
2. **每一处 `catch (Exception) { return; }` 都要在注释里写清「Go 侧此处会发生什么」** ——
   写不出来，说明这条偏离**还没被签字**。
3. **任何「静默降级」的契约，都必须配套一条对拍 fixture 测试**（对 P-HOTRPC-01 那类算错缺陷，
   只有 fixture 能抓）。

---

## 本轮三条跨组件教训（超出 hot-rpc 范围，但同款结构已在别处出现）

| 教训 | 同款位置 | 建议 |
|---|---|---|
| Boot 4 拆模块导致 Bean 缺失 ⇒ 上下文起不来 | **`like-rpc` 6 处注入 Jackson2 `ObjectMapper`** | 进 issue；修法照抄 `article-rpc/Jackson2ObjectMapperConfiguration` |
| 纯手 new 单测对「Bean 能不能装配出来」零敏感 | 全部 8 个 rpc 模块的测试布局 | 每模块至少一条 `@SpringBootTest` 上下文能起来 |
| `@KafkaListener` 缺 `spring-boot-kafka` 会**静默失效** | 上一轮已修 `hot-rpc`；`like-rpc` **同类风险未查** | 补装配层测试（`KafkaListenerEndpointRegistry` 断言），别只查 pom |