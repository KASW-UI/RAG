<!-- @new 2026-10-23 · task 域踩坑档 · 由 `align/task` 工作树产出 -->

# `task-rpc` 踩坑记录

> 记「**为什么会这样**」，不是记「哪条不一样」。不一致本身在 [`../../DRIFT/task/rpc.md`](../../../../DRIFT/task/rpc.md)。

---

## 🔴 PITFALL-TASK-RPC-01 · `@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」，15 条真 PG 用例静默全跳

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-003`](../../../../DRIFT/task/rpc.md#-drift-task-rpc-003--15-条真-pg-用例静默全跳证据缺失)
> **核对日期**：2026-10-23　**基线**：`8422116`

### 现象

`TaskProgressUpsertParityTest` 的 **15 条全部 `skipped`**，surefire XML 里原因字面写着：

```xml
<skipped message="Docker is not available"/>
```

而 **Docker 明明可用** —— 同一次构建里另外三个真起容器的测试类全部正常执行：

| 类 | 结果 |
|---|---|
| `FavoriteSchemaParityTest` | `tests=13 skipped=0` ✅ |
| `CommentSchemaPgGateTest` | `tests=18 skipped=0` ✅ |
| `FollowRpcWiringTest` | `tests=20 skipped=0` ✅ |
| `TaskProgressUpsertParityTest` | `tests=15 skipped=15` 🔴 **静默全跳** |

**唯一差别**：只有它加了 `@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable`。

### 🔴 为什么危险（三个因素叠加）

1. **类名带 `Parity`** —— 读报告的人极容易把「有个叫 Parity 的测试类」误读成「已做 Go 对照」，而它一条都没跑；
2. **reactor 报 `BUILD SUCCESS`** —— 没有任何红灯提示你少跑了 15 条；
3. **它测的正是 AGENTS.md §3.3 点名的盲区** —— 该类自己的注释写着：
   > 「这一步不能省：验证 Flyway 是否真跑 + MyBatis-Plus 是否真产出 SqlSessionFactory
   > —— 这两条都是『编译通过 + 纯 mock 单测全绿』永远发现不了的装配问题」

### 根因（每一环都实测过，不是推测）

```
@EnabledIfDockerAvailable 在【类级别】求值 ⇒ 早于任何测试方法就要拿 Docker client
  └─► testcontainers-junit-jupiter:2.0.5 → DockerAvailableDetector.isDockerAvailable()
        └─► DockerClientFactory.instance().client()
              └─► client() 会【顺带确保 Ryuk 资源回收容器可用】
                    └─► 本机拉不到 testcontainers/ryuk:0.14.0 ⇒ ContainerFetchException
                          └─► DockerAvailableDetector 的实现是 catch (Throwable) { return false }
                                └─► 「基础设施拉镜像失败」与「Docker 真不可用」被压成同一个 false
                                      └─► 整类 disabled ⇒ 15 条一条不跑，reactor 照报 BUILD SUCCESS
```

最小探针（裸 `java`，非 surefire 环境）实测：

```java
DockerClientFactory.instance().client();
// ❌ org.testcontainers.containers.ContainerFetchException :
//    Can't get Docker image: RemoteDockerImage(imageName=testcontainers/ryuk:0.14.0)
```

**为什么别的 Testcontainers 类没事**：它们不加这个注解，容器是**懒启动**的 ⇒ 判定阶段不触发 Ryuk。
`task-rpc` 是**全仓唯一漏配 `RYUK_DISABLED` 的容器模块**
（`article-rpc` / `comment-rpc` / `favorite-api` / `favorite-rpc` / `follow-rpc` /
`like-rpc` / `message-rpc` / `admin-api` / `admin-rpc` / `user-api` / `user-rpc` 全都配了）。

### 修法（照同仓既有写法，不重新设计）

1. `task-rpc/pom.xml` 补 surefire `environmentVariables` 注入
   `<TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>`，
   与 `favorite-rpc` / `follow-rpc` **同款**；
   ⚠️ **不要换写法**：各模块 pom 内已有注释说明「`testcontainers.properties` 与 `-D` 均无效」——
   Testcontainers 2.x 的 `ResourceReaper` **只读环境变量**；
2. 删除 `TaskProgressUpsertParityTest` 的 `@EnabledIfDockerAvailable`
   —— Ryuk 已禁用时该注解再无作用，留着只会继续掩盖失败。

**实测**：`tests=15 skipped=0`（此前 `skipped=15`）；全模块 `Tests run: 81, Skipped: 0`。

### 🔴 元教训（比这个 bug 本身更重要）

1. **`@EnabledIfDockerAvailable` 的 `message` 不可信** —— 它把两类完全不同的失败压成同一句话，
   **不要拿它当诊断依据**（本例的真因是「拉不到 ryuk 镜像」，不是「没有 Docker」）。
2. **新模块加 `@Testcontainers` 时若漏配 Ryuk，会静默 disable 而不是报错** ——
   且**恰好是唯一负责真 PG 对照的那个类**。
3. **「跑绿」必须额外断言 `skipped 数 = 0`**。「`tests` 全绿」**不构成**「真 PG 已验」的证据 ——
   上一条教训是 H2 假绿（`message-rpc` 的 `,string` 漂移被绿测试锁死），
   这一条是**假跳**：测试根本没跑，报告却比跑绿还干净。

---

## 🔴 PITFALL-TASK-RPC-02 · relaxed binding 键名对不上 ⇒ 配置键被静默丢弃

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-002`](../../../../DRIFT/task/rpc.md#-drift-task-rpc-002--taskdefinitions-的四个-ttl-键是死配置)

### 现象

`application.yml` 写着 `init-ttl: 90d`，改它**不报错、也不生效**。

### 根因

Spring Boot 的 relaxed binding 要求「配置键去掉分隔符 == JavaBean 属性名去掉分隔符」：

| yaml | 字段 | 规范化后 |
|---|---|---|
| `init-ttl` | `initTtlDays` | `initttl` vs `initttldays` ❌ |
| `init-ttl` | `initTtl`（修好后） | `initttl` vs `initttl` ✅ |

而 `@ConfigurationProperties` 的 `ignoreUnknownFields` 默认 `true`
⇒ **对不上的键被静默丢弃**。这是 AGENTS.md §3.3.1「配置里没写 ≠ 没开」的**加强版**：
这里是「**配置里写了也不生效**」。

### 为什么当时没人发现

- **默认值恰好等于 Go 的常量**：90d / 24h / 120d 三条默认值都正确 ⇒ 行为看不出问题；
- yaml 上方还写着「提到 yaml 只是为了让改这个值必须显式可见」——
  **注释在描述一个不存在的能力**（§3.2 的「无出处 idiomatization」：回答不了「这段比 Go 多做了什么」）。

### 抓它的方法（可复用）

**不要靠读代码判断**，写一个探针，且**必须带阳性对照**：

```java
// ✅ 阳性对照：键名对得上的键必须绑得上（否则结论不能归因于键名）
when(ops.increment(anyString())).thenAnswer(...)   // redis-pipe-max → 1234 生效
// ❌ 目标键：init-ttl → 7d 覆盖后实测仍是默认值
```

⚠️ 没有阳性对照时，「断言通过」可能只是因为**探针根本没绑上任何东西**（空过）。
本次实况：阳性对照 ✅ 通过、`init-ttl` ❌ 失败 ⇒ 结论才成立。

### 修法

字段类型 `long` → `java.time.Duration`，键名与 yaml 逐字对应；默认值保持 Go 常量。
配 `TaskSinkPropertiesBindingTest` 长期守着「生效值 == Go 常量」+「配置面是活的」两条。

---

## ⚠️ PITFALL-TASK-RPC-03 · 注释描述的是「代码没实现的契约」

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-001`](../../../../DRIFT/task/rpc.md#-drift-task-rpc-001--taskcountstore-内存单例--两个-goka-group-串号--重启归零)

`TaskCountStore` 的 javadoc 原文写着：

> 「**每个消费组各持有一份**：Go 侧三个 group 是三个独立的 goka processor，存储互不共享。」

而类里只有**一个字段** `private final Map<String, Long> counters`，被两个 consumer 共用。

⚠️ **注释描述的是应该有的设计，不是代码的实际行为**。
这类注释比没有注释更危险：它让 review 的人**以为已经核对过**。

**判据**：凡是「Go 出处」型断言（§3.2 三段式、§3.3.2 的 `Go: …` 措辞），
都必须有一条**能在运行时失败**的测试兜底。注释里的「已经对上了」不算证据。

---

## 相关文件

| 关系 | 文件 |
|---|---|
| 本域 DRIFT | [`../../DRIFT/task/rpc.md`](../../../../DRIFT/task/rpc.md) · [`api.md`](../../../../DRIFT/task/api.md) |
| 全仓共性踩坑 | [`../../Pitfalls/_cross/testing-pitfalls.md`](../../_cross/testing-pitfalls.md) · [`../../Pitfalls/_cross/framework-mechanism-evidence.md`](../../_cross/framework-mechanism-evidence.md) |

---

## 🔴 PITFALL-TASK-RPC-04 · 探针自己也会撒谎：Python `urllib` 会自动补 `Content-Type`

> 场景：2026-10-23 `task` 域双侧活体对拍（见 [`../../DRIFT/task/api.md`](../../../../DRIFT/task/api.md) §二·五）

### 现象

第一版对拍脚本想测「POST **不带** `Content-Type`」，结果两侧都返回 200，**看起来一致**。
换成 `curl -H 'Content-Type:'` 复验才发现：RAG 侧在 `application/x-www-form-urlencoded`
下**返回 400**，Go 返回 200 —— 一条真实差异差点被放过。

### 根因

```python
req = urllib.request.Request(url, data=b'{"id":1001}', method="POST")
# 没有显式 add_header("Content-Type")
# ⇒ urllib **自动**补上 Content-Type: application/x-www-form-urlencoded
```

⇒ 测的其实是「带 form-urlencoded」，不是「不带 Content-Type」。**用例名与被测行为不符。**

### 为什么会漏掉

脚本里明明写了 `content_type=None` 分支「不发这个头」，
但 HTTP 客户端在**更下面一层**补上了 —— 探针断言的是「我没设这个 header」，
而线上真正发生的是「客户端帮我设了一个我没预期的 header」。

### 元教训

1. **探针必须对「我以为由我控制的东西」做端到端验证** ——
   用 `urllib` 这类会自动补默认值的客户端时，要么显式覆盖，要么用 `curl` 这种可控的。
2. **和 `positive control` 是同一条纪律的两面**：
   - 阳性对照回答「探针能不能**看见**差异」（见 PITFALL-TASK-RPC-02）；
   - 本条回答「探针是不是在**测你以为在测的东西**」。
   两者都要做，缺一个结论就不可信。
3. ⚠️ 这与 `tools/difftest/README.md §4.1`（把 404 当就绪）同源：
   **「探针返回了东西」不等于「探针测的是那件事」。**

---

## ⚠️ PITFALL-TASK-RPC-05 · 对拍「红了 42/43」可能全是 header 格式，别淹没真正的差异

> 场景：2026-10-23 `task` 域对拍首跑：`MATCH 1 / DIFF 42`

### 现象

43 条用例里 42 条标红，逐条看下去会发现：
**40 条只差 `Content-Type` 的 charset 参数与分号后空格，2 条差状态码，body 43/43 逐字节一致。**

- Go：`application/json; charset=utf-8`（分号后**有空格**）
- RAG：`application/json`（无 charset）/ `text/plain;charset=utf-8`（**无空格**）

### 教训

1. **对拍的「红」必须分类再看**：状态码 / 响应体 / 业务 header / 纯格式，
   只有前三类值得开单子；最后一类是**全域共性**（别的 api 域也一样），
   单独修会造成「本域与其它域不一致」的新问题。
2. `tools/difftest/diff.py` 里的 `Normalizer` 就是干这个的 ——
   **先把 header 归一，再看还剩什么**。没有这一步，42 条噪声会把 2 条真差异埋掉。
3. ⚠️ 反过来也成立：**不要因为「大部分红的是格式」就草率收工** ——
   本轮那 2 条状态码差异恰恰就是从这堆噪声里筛出来的。

---

## 🔴 PITFALL-TASK-RPC-06 · Go 的 `Pipeline()` 不是事务，Spring 的 `executePipelined` 也不开 MULTI —— **两边都理解错了，于是整条链路静默死掉**

> 场景：2026-10-23 Kafka 端到端对拍首跑。**整个 task 消息处理链路此前是死的**，而 81 条测试全绿。

### 现象

RAG 侧日志（4 次）：

```
io.lettuce.core.RedisCommandExecutionException: ERR EXEC without MULTI
  at io.lettuce.core.internal.Future.awaitAll(Futures.java:213)
  at org.springframework.data.redis.connection.lettuce.LettuceConnection.closePipeline(LettuceConnection.java:596)
Caused by: com.rag.task.task.rpc.service.RedisPipelines.exec(RedisPipelines.java:48)
  at UserLikeProgressSink.pipelineSetNx(UserLikeProgressSink.java:322)
```

### 根因（三方语义对不上）

| | 事实 | 出处 |
|---|---|---|
| Go 的 `Pipeline()` | **不是事务**，只是批量 flush | go-redis v9.20.0 `redis.go:1508-1513`（`exec: pipelineExecer(processPipelineHook)`）；`redis.go:1521-1528` 的 `TxPipeline()` 才 `wrapMultiExec`；`pipeline.go:18-20` 注释原文 *"Pipeline is not a transaction"* |
| Go 的 sink | 用的是 `c.rdb.Pipeline()` | `like_sink.go:167`、`like_sink.go:210` |
| Spring 的 `executePipelined` | **不开 MULTI**（只置标志位） | `LettuceConnection.java:567-575` `openPipeline()` |
| Spring 的 `exec()` | pipeline 模式下**直接发 EXEC** | `LettuceConnection.java:688-719` ⇒ 服务端回 `ERR EXEC without MULTI` |
| Spring 的框架契约 | 回调**必须返回 null** | `RedisOperations.java:118-128` javadoc；`RedisTemplate.java:464-467` 强制抛异常 |

⇒ RAG 在 `executePipelined` 的回调里**多调了一次 `operations.exec()``，
把「Go 的批量 flush」误当成「MULTI/EXEC 事务」⇒ 服务端直接拒绝。

### 为什么杀伤面这么大

`ERR EXEC without MULTI` 抛在 `lazyInitTaskIfNeeded` 的**第一步 SETNX** 上：

```
SETNX 抛 ⇒ 穿出 lazyInit ⇒ 穿出 flushOnce ⇒ 被 flushTick catch ⇒ **整轮 flush 中止**
```

⇒ `like:total:*`、`task_progress`、任务完成、奖励事件**全部不发生**。
**组件「跑着」「健康」「测试全绿」，但核心功能一行都没执行过。**

### 教训

1. **「两边的同名概念」不保证语义相同**：`Pipeline()` 在 Go 与 Spring 里都叫 pipeline，
   但 Go 的**不是**事务、Spring 的**也不开** MULTI —— 真正的对应物是
   Spring 的 `executePipelined`（且**回调必须返回 null**）。
2. **框架的 javadoc 契约要当约束读**，不是当建议读。
   `RedisOperations.java:118-128` 已经把「must return null」写在脸上。
3. **「出错 ⇒ 抛」与「出错 ⇒ 吞」在 Go 里逐点不同**，不能统一处理：
   `like_sink.go:223-225` 抛，`get_task_logic.go:73-76` 吞（注释写着「回填失败不影响返回」）。
   统一成一种 ⇒ 终态分叉（见 DRIFT-TASK-RPC-005）。

---

## 🔴 PITFALL-TASK-RPC-07 · 81 条测试**零真 Redis 覆盖** ⇒「跑绿」证明不了 Redis 路径

### 现象

`RedisPipelines.exec` 里那个让整条链路死掉的 `operations.exec()`，
在 81 条测试全绿的情况下长期存在。拆开看这 81 条覆盖了什么：

| 测试类 | 条数 | 碰 Redis 了吗 |
|---|---|---|
| `TaskSinkConsumeTest` | 19 | ❌ 把 `StringRedisTemplate` **整个 mock 掉**，只验内存 delta |
| `TaskProgressUpsertParityTest` | 15 | ❌ 只验 PG，Redis 指着 `127.0.0.1:1`（不可达） |
| `TaskEventParsingTest` | 21 | ❌ 纯解析 |
| `TaskDomainServiceTest` | 19 | ❌ mock `StringRedisTemplate`，且**连方法名都断言了** |
| 其余 | 7 | ❌ |

⇒ **没有任何一条测试让 Redis pipeline 真的跑过一条命令。**

### 教训

1. **「有 Redis 依赖」≠「有 Redis 覆盖」**：要数的是
   **有几条用例让真 Redis 收到过命令**，不是 pom 里有没有 `spring-boot-starter-data-redis`。
2. **mock 掉的东西不会告诉你它坏没坏**。本例中 mock 反而**固化**了错误的机制
   （`verify(redis).execute(cap.capture())` 断言的正是那个 bug 机制）。
3. ⇒ 修的时候要**同时**改实现和那 3 处锁错机制的断言，并说清为什么改。
4. **变异测试是验「守门测试不是假绿」的唯一硬手段**：
   本轮把 `exec()` 退回 `operations.exec()` 后，9 条里 **5 条报错**（含两条端到端 sink 用例），
   证明守门测试真的能抓住它。

---

## ⚠️ PITFALL-TASK-RPC-08 · Redis 的 `SET` 是**类型无关**的 ⇒ 断言会「因为错误的原因而通过」

> 场景：2026-10-23 写真 Redis 守门测试时踩到。

### 现象

想造一个「命令级错误」，用「对 hash 类型的 key 做 `SET`」当错误源。
结果**不抛异常** —— 而且**非 pipeline 的对照组也不抛**。

### 根因

Redis 的 `SET` **会覆盖任何类型的 key**（它是类型无关的写入）。
WRONGTYPE 只发生在「用某类型的读/改命令去动另一种类型的 key」上
（对 string key 做 `HGET` 才是 WRONGTYPE）。

### 为什么危险

这条断言当时**通过了** —— 但它通过的原因是「错误源根本没触发错误」，
**不是**因为「实现正确」。⇒ 这是 AGENTS.md §3.3 说的
「绿测试可能是错误答案的守卫」的又一个变体。

### 教训

1. **阳性对照的对照组也要有**：本例是「非 pipeline 同样操作也不抛」暴露了前提错误。
   ⇒ 写错误路径的断言时，**必须先确认这个错误在直连模式下也会发生**。
2. 正确做法：对 **string** 类型的 key 做 `HGET`（已写进
   `RedisPipelinesRealRedisGateTest.execPropagatesCommandError` 的注释里）。

---

## 🔴 PITFALL-TASK-RPC-09 · 删 Kafka topic **不会**删 `__consumer_offsets` 里的已提交位点

### 现象

为了重置对拍状态，删掉全部 topic 再重建、再灌同样的 7 条消息：

```
task.logs.raw          task.logs.raw:0:7   ← 消息在
task.logs.raw.User     task.logs.raw.User:0:0   ← 路由处理器一条都没发出
```

RAG 侧却完全正常（`rag.task.logs.raw.User:0:7`）。

### 根因

- 已提交位点存在 `__consumer_offsets`，键是 **group + topic + partition**。
  **topic 同名 ⇒ 位点还在**（哪怕 topic 已删重建）。
- Go 的 goka 用 `cfg.Consumer.Offsets.Initial = sarama.OffsetOldest`
  （`initGoKa.go:134-135` 等三处），但 `OffsetOldest` **只在没有已提交位点时生效**。
  有旧位点（比如 9）就从 9 开始 ⇒ 新 topic 只有 7 条（offset 0-6）⇒ **整段被跳过**。
- RAG 侧侥幸没事：`@KafkaListener` 用 `auto-offset-reset=latest`，
  而消息是**启动之后**才灌的 ⇒ 起点就是队尾 ⇒ 全部收到。

⇒ 一侧全收、一侧全不收 —— **典型的「假差异」**，会让人误判成 RAG 的 bug。

### 附带坑：goka 的本地 LevelDB 缓存

goka 自己会报：

```
kafka tells us there's no message in the topic, but our cache has one.
The table might be gone. Try to delete your local cache!
Topic task.rpc.consumer71-table, partition 0, hwm 0, local offset 23
```

缓存路径 = `/private/tmp/goka/processor/<group>/<topic>.<partition>`（goka `storage/builders.go:18-21`）。
⇒ **删 topic 必须连本地缓存一起清**（用 `mv` 挪走，不要动整个 `/private/tmp/goka`——
别的会话可能也在用）。

### 教训

1. **Kafka 的「重置」= topic + consumer group 位点 + 客户端本地缓存，三样都要清**。
   最省事、最不会漏的做法：**重建整个 Kafka 容器**（本轮就这么干的）。
2. **不对称的重置会造出假差异** ⇒ 两侧的重置必须**同样彻底**。

---

## ⚠️ PITFALL-TASK-RPC-10 · 容器内 Kafka CLI 必须用 **INTERNAL** 监听器

### 现象

`kafka-topics.sh --bootstrap-server localhost:9092` 在容器内**一直重连**：

```
WARN [AdminClient clientId=adminclient-1] Connection to node 1 (/127.0.0.1:59992) could not be established.
```

`kafka-get-offsets.sh` 则直接 `ClassNotFoundException: kafka.tools.GetOffsetShell`（包名已变）。

### 根因

容器 env：

```
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:59992,INTERNAL://localhost:19092
```

`127.0.0.1:59992` 是**宿主机**的端口映射。容器内的客户端 bootstrap 到 `localhost:9092` 后，
broker 回头告知 `127.0.0.1:59992` ⇒ 在容器自己的 netns 里这个端口**没人监听** ⇒ 永远连不上。

⇒ 容器内要用 `--bootstrap-server localhost:19092`（INTERNAL 监听器，advertised 为 `localhost:19092`）。
**宿主机上的客户端不受影响**（宿主机视角 `59992 → 容器 9092` 是通的）——
所以「服务能连上、CLI 连不上」是**预期**的，不是环境坏了。

### 教训

1. **`advertised.listeners` 决定的是「客户端拿到什么地址」**，不是「broker 监听什么」。
   同理适用于数据库、gRPC 等任何有 advertised 地址的中间件。
2. 新版 `apache/kafka` 镜像里 `kafka.tools.GetOffsetShell` 已改名 ⇒ 用
   **`kafka-get-offsets.sh`**；`--property` 也已 deprecated ⇒ 用 `--formatter-property`。
3. ⚠️ **「offset 查询失败」不等于「offset 是 0」**。本轮最初就是
   `GetOffsetShell` 报错 ⇒ 输出为空 ⇒ 被误读成「消息没灌进去」，
   实际 offset 是 16。**测量手段失败时，先怀疑测量手段。**
