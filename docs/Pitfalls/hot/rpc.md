# `hot-rpc` 踩坑台账（§31-a：这里记「为什么会踩」，不记差异本身）

> **范围**：`service/hot/hot-rpc`。**核对日期**：2026-10-24（第五轮 UNR-1 闭合）。
> 差异本体在 [`../../DRIFT/hot/rpc.md`](../../DRIFT/hot/rpc.md)（`DRIFT-HOTRPC-001..010` + `G-HOT-001/002/003`）。
> 本文件只回答四件事：**症状像什么 · 真实原因（框架/语言机制）· 为什么测试抓不到 · 下次怎么防**。
> 累计 **12 条坑**（P-HOTRPC-01..12）。

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
---

## P-HOTRPC-07 🔴 私有语义位（桶位）**行为测试永远测不出**，只能把真源算出的值固化成 fixture

**现象**
`HeavyKeeper` 的 46 条测试全绿、10/10 变异全红，
而**生产口径 Top-100 有 16 条与 Go 不同**（各 16 条只在一侧）。

**真实原因**
`hash(key, seed)` 算出来的**列号**是一个**私有语义位**：
它不体现在任何公开返回值上，只通过「桶冲突 → 计数分布 → 榜单」**间接**体现。
而既有测试只断言**相对次序**（谁在谁前面）、**容量上限**、**存活**这类**序关系**，
**从不断言绝对列号** ⇒ 桶位整体平移（甚至换列）**完全不可见**。
⚠️ 更狠的是：错法「有符号取模 + 补正」保证结果**一定落在 `[0, width)`**
⇒ **不崩、不越界、零告警**，连「异常被降级处理」那条遮蔽机制都看不到它。

**为什么 fixture 化能守住、行为测试守不住**
行为测试问的是「**相对关系对不对**」；桶位错时**相对关系大体仍然成立**
（只是分到了别的桶），所以它照样绿。
fixture 问的是「**绝对值对不对**」—— 把 Go 真源在同一输入下算出的列号**逐条写进测试**，
错一个就红。

**怎么防**
1. 凡是「私有但决定输出」的语义位（哈希列号、桶位、槽位、位偏移、随机种子序），
   一律用**对拍 fixture**，不用行为测试。
2. fixture 的生成方式必须**可复现且不污染真源**：
   把真源文件**逐字节复制**到临时目录（记录 sha256），只追加一个**最小导出**，
   再 `go run` 探针；**Go 仓零改动**。
3. fixture 挑输入要**覆盖边界**：空串、中文、emoji、超长串、必冲突的窄 width。
4. **必须做一次变异**：把实现改回错法，确认 fixture **每一条都红** ——
   否则你不知道 fixture 到底守没守住。

---

## P-HOTRPC-08 🟠 库**不给你配置面**时，「不复刻」也要取证到「不可达」，不能只写「没空改」

**现象**
DRIFT-HOTRPC-004（Go 的 Redis 命令重试 3 次 vs RAG 0 次）被前一轮写成
「建议显式化现状」—— 听起来像「RAG 现状也可以，只是没写清楚」。

**真实原因（本轮补齐的取证）**
不是「没空改」，是**改不了**：
- Spring Data Redis **4.0.7** 的 `connection/lettuce` 包内 **`RetryPolicy` 引用数 = 0**
  ⇒ **Spring 侧根本没有暴露 Lettuce 命令级重试策略的配置入口**；
- Lettuce **6.8.2** 的 jar 里**连 `io/lettuce/core/retry` 包都没有**
  （顶层包只有 `annotations/api/cluster/codec/…/protocol/resource/search/sentinel/support`）
  ⇒ 命令级重试策略**不是 Lettuce 6.8 的 API 面**。
⇒ 「配成 3 次」在当前依赖栈下**不可达**；要在应用层自己写重试，就是把重试放到**错误的层**
（会改变 drop 语义、指标口径、失败时机）⇒ 那比现状更坏。

**为什么值得单独记**
「决定不复刻」和「**证明**不能复刻」是两件事。
只写前者 ⇒ 下一个人会以为只是没做；写清后者 ⇒ 这条差异就**永久关闭**了，
除非换依赖栈。

**怎么防**
1. 凡是要签字为「不复刻」的差异，**必须回答「为什么不能复刻」**，
   并把答案落到**可核验的证据形态**（依赖树 / jar 内类清单 / 字节码），
   而不是「框架不支持」这种口头结论。
2. 顺手把「**换一个依赖栈能不能对齐**」也写上（本次：换掉 Lettuce 或改用 Lettuce 原生 API 才可能），
   这决定它是一条**死条目**还是一条**待办**。

## P-HOTRPC-09 🔴 静态读 Lua 脚本会把 `KEYS` 绑定读反 —— **台账写错比代码写错更危险**

**现象**
`docs/DRIFT/hot/rpc.md` 的 Equivalence Matrix 第 25 行原本写着：

> `EXISTS` 守卫（**首次快照必丢**） | Go `:228-230` | Java `:75-77` | EQUIVALENT | 静态源码

生产代码 `HotRedisSnapshotStore` 里那段 Lua **本身是对的**，但**台账与代码注释都把守卫语义写反了**：
- 脚本第一行是 `if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end`；
- 调用处传的是 `List.of(shadowKey, partitionKey)` ⇒ **`KEYS[1] = shadow`**；
- 而 shadow 在同一次 pipeline 里刚被 `ZADD` 写入 ⇒ **守卫恒为 1** ⇒ **首次快照不会被丢**。

台账之所以写反，是**只盯着脚本里的 `KEYS[1]` 字面量，没把它和调用处的实参顺序一起读**。

**真实 Redis 实测（2026-10-23，`HotRedisLuaGateTest#firstSnapshotIsNotLost`）**
- 先 `DEL` + `ZADD` 写 shadow，再跑改名脚本 ⇒ 返回 `1`，分区 key 正常落地；
- 变异验证：把守卫改成检查 `KEYS[2]`（即目标分片 key）⇒ 该用例立刻变红
  ⇒ 证明用例真的在测「守卫查哪个 key」，不是恒真断言。

**为什么值得单独记**
- 如果照着旧台账去「修」生产代码，会把**本来正确的实现改坏**（把守卫改到 `KEYS[2]` 才是真 bug）；
  ⇒ **台账的错误结论会主动制造回归**。
- 这正是 §3.1 说的「不要拿现有代码/注释当对照基准」的另一面：
  **注释与台账本身也是会被写错的中间产物**，不能拿来当「另一侧基准」。

**怎么防**
1. 凡是要断言「守卫 / CAS / 条件分支命中哪个 key、哪个值」的语义位，
   **必须有真中间件**：Lua 的 `KEYS[i]` 绑定、CAS 的比较对象，
   **静态读代码永远可能把 i 读错，而读错不报任何错**。
2. 每条这样的断言都要配**变异验证**（把绑定改错 ⇒ 用例必须变红），
   否则无法区分「守卫恒真」与「守卫生效」。
3. 台账里凡是**结论性措辞**（「必丢」「恒为」「总是」），
   都要能指到一个**可复现的观测点**；指不到就降级成「未验证」。

## P-HOTRPC-10 🟠 「等条件成立」≠「等终态」—— 轮询式断言会撞上**中间态**

**现象**
`HotKafkaConsumerGateTest` 第一版的 drop 契约断言写成：

```java
awaitCondition(() -> counterCount("like") == likeBefore + 1, "正常消息没进消费指标");
```

变异验证把生产代码的「缺 `article_id` 就 drop」校验去掉后，**用例仍然是绿的**。

**真实原因**
4 条消息是**逐条**被同一个消费者线程处理的，而断言每 100ms 轮询一次：
1. 第 1 条（畸形 JSON）→ drop，不计数；
2. 第 2 条（缺 `article_id`）→ **变异后不再 drop ⇒ 计数 +1**；
3. 第 3 条（未知 type）→ drop，不计数；
4. 第 4 条（正常）→ 计数 +1。

⇒ 断言在第 2 条与第 4 条之间**恰好观察到 `+1`** ⇒ 提前放行 ⇒ **假绿**。
生产代码是对的，**是断言的观察窗口有洞**。

**怎么防**
1. 涉及「累计量」的断言，**必须先等一个「全部处理完」的终态信号，再取终值**。
   本例的终态信号是**已提交位点到达 `offsetBefore + 4`** ——
   位点是「处理完才提交」的，所以位点到位 ⇒ 计数已是终值。
2. 轮询断言只适合「单调递增到目标」；**不适合「中途可能经过目标值」**的场景。
3. 变异验证是发现这类洞的**唯一可靠手段**：一条「本该红」的变异如果没红，
   先怀疑断言，别急着怀疑生产代码。

## P-HOTRPC-11 🟠「指定的验证手段」本身要先自证可用 —— `CLIENT PAUSE` 只延迟、**不能**制造失败

**现象**
第四轮台账给 UNR-1（Go 侧 go-redis 重试面）指定的验证手段是：

> 「起 Go 真源 + 真 Redis，用 `CLIENT PAUSE` 制造半程失败，观察两侧 keyspace」

**实测：这条路径根本造不出失败。**
- `CLIENT PAUSE <ms> WRITE`：只暂停**其它客户端**的写命令；**发起方自己**照常执行；
- `CLIENT PAUSE <ms> ALL`：暂停所有客户端，但**只是排队延迟** ——
  客户端的 socket 超时计时器照跑，命令在暂停结束后才被处理 ⇒ 暂停时长 < 超时 ⇒ **客户端不报错**。

⇒ 按台账写的做法跑多轮，探针每次拿到的都是 `Exec err=''`、终态完整 ——
**看起来像「结论：重试对终态无影响」，其实只是「手段没生效」。**
这两者长得极像，结论方向却完全相反。

**真实原因**
目标窗口是「**命令已在服务端执行、但回复在回程丢了**」，
而 `CLIENT PAUSE` 是 Redis 的**执行调度**开关，不是**故障注入**开关 ——
它改变的是「什么时候执行」，不是「会不会失败」。

**为什么危险**
1. **「手段失效」会被误读成「被测对象无此行为」** ⇒ 写出一份**方向完全相反**的台账结论；
2. 更糟的是：手段失效时探针**通常不报错**，只会**安静地给出「无异常」** ——
   没有异常日志、没有非零退出码，只有一个看起来很干净的阴性结果。

**怎么防**
1. **验证手段本身要先自证**：制造失败的探针，必须先跑一遍**阳性对照** ——
   即「**不带被测代码**时，这条手段确实能造出失败」。
   本例的阳性对照应该是「`CLIENT PAUSE` 之后，另一个客户端的写**应该**被延迟/超时」；
   阳性对照不通过 ⇒ 手段无效，**不许**拿它的阴性结果写结论。
2. 制造网络层失败要走**网络层工具**（iptables / toxiproxy / 网络命名空间故障注入），
   **不要**指望 Redis 的管理命令能替代。
3. ⚠️ **`CLIENT KILL` 也不行**：go-redis **连接池会透明重拨**，
   被 kill 的那条连接 ≠ 后续 pipeline 实际用的那条 ⇒ 实测 `Exec err=''`、终态完整。
4. **兜底纪律**：当目标窗口**无法在不改真源的前提下制造**时，
   就**明确写「源码级结论，未实测」**，并把「未实测」写进结论本身 ——
   **不要用一个跑通的探针，去给一条从未被真正触发过的路径背书**。
   （本轮 UNR-1 即按此结案：证据强度 = 真 Redis 实测 + 依赖源码逐行，**不是**双侧活体对拍。）

**留下的产出**
不只是一条「没测成」的记录，而是**两条真结论**：
① Go 的 pipeline 重试是**整组重发**（`redis.go:1204-1227` 每次 `writeCmds` 全量）⇒ `DEL`+`ZADD` 幂等重放；
② ⚠️ 反向查出 **Go 会重发非幂等的 rename 脚本** ⇒ 可能「实际 RENAME 成功却记 fail」。
⇒ 详见 [`../../DRIFT/hot/rpc.md`](../../DRIFT/hot/rpc.md) 第五轮章节。

## P-HOTRPC-12 🟠 返回 `Set` 的 API —— **mock 测不出「保序」**，而框架可能给你 `HashSet`

**现象**
`HotRankingQueryService` 的查询面此前只有 Mockito 单测，全部绿。
补上真 Redis IT 后**一条都没红** —— 但这不是「本来就对」的证据，
而是「**mock 根本没测到点上**」的证据。

**真实原因**
生产代码的读法是：

```java
Set<ZSetOperations.TypedTuple<String>> results =
        redis.opsForZSet().reverseRangeWithScores(HOT_ARTICLES_KEY, 0, k - 1);
```

⚠️ 这个 API 返回的是 **`Set`**，而**顺序是它的全部语义**：
热榜靠 `Set` 的迭代序决定顺序，**并列项的先后也在这个序里**。
Mockito 给什么就是什么 ⇒ **「顺序对不对」这件事，mock 一个字都测不出来**。

而真 Redis 上这一层有三个**框架行为**在中间：
1. **Redis 服务端**：`ZREVRANGE` 按 score 降序、同分按 member 字典序；
2. **Lettuce**：`ZREVRANGE key start stop WITHSCORES`（**消费点**在 `RedisCommandBuilder`）；
3. **Spring Data Redis**：`deserializeTupleValues` 把 RESP2 数组转成 Java `Set`
   ⇒ **它给的是哪种 `Set`？** 若是 `HashSet`，**顺序当场就丢了**，而且**不报任何错**。

**为什么危险**
「mock 全绿」在这里长得和「行为正确」**一模一样**。
而且这个坑有**两个变体**，都能悄悄错：
- **顺序丢失**：框架给 `HashSet` ⇒ 热榜顺序与 Go 不同 ⇒ **纯 bug，无症状**；
- **区间/取整搞错**：`0, k-1` 写成 `0, k` ⇒ 多取一条；`uint32(score)` 写成 `Math.round` ⇒ 分数变
  ⇒ 这两条 mock **也**测不出（mock 不经服务端排序、不经 float64 存储）。

**怎么防**
1. **凡 API 返回 `Set` / `Map` 而顺序有语义 ⇒ 必须上真中间件**，
   并**显式断言顺序**（`containsExactly`，不是 `containsExactlyInAnyOrder`）。
2. 断言要覆盖**并列项**（同分）—— 那才是顺序最容易丢的地方。
3. 真中间件 IT 配**定向变异**：`0,k-1 ⇒ 0,k`、`scoreToCount ⇒ Math.round`
   ⇒ 变红才证明断言真的在测那个语义（本轮实测：M-R1 变红 2 条、M-R2 变红 1 条）。
4. ⚠️ **不是所有语义位都能上真 Redis 验**：ZSET member **恒是字符串字节**
   ⇒ 「member 非字符串」分支**在真 Redis 上不可达**（不是没验，是不可达）
   ⇒ 只能靠源码取证，并登记为「真 Redis 无法覆盖」。
