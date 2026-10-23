<!-- @new 2026-10-23 · task 域组件级对照（rpc 先于 api）· 由 `align/task` 工作树产出 -->

# `task-rpc` 组件级语义对照台账

> **基线 commit**：`8422116`（`do-my-own-gh`）
> **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/task`，分支 `align/task`
> **核对方式**：Go 侧逐行读完 `service/task/rpc/` 全部源码（2,250 行，排除 `pb/` 生成代码）；
> RAG 侧逐行读完 `service/task/task-rpc/src/main/` 全部 29 个类 + pom + application.yml + 两份 DDL。
> **Go 真源**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`，go-zero `v1.10.2`、goka `v1.1.16`（版本已核 `go.mod`）。
> **本文件属主**：`align/task` 工作树。共享索引（`docs/DRIFT/_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`）**未触碰**，按 `docs/Worktree.md` §3 留给合并后统一更新。

---

## 〇、结论

| 项 | 结论 |
|---|---|
| 语义等价性 | ✅ **首轮为 🔴 NOT EQUIVALENT（5 条漂移），现已全部修复并复核**（2026-10-24：Kafka 端到端 + HTTP 双侧对拍均 0 DIFF）⚠️ 效力边界见 §五 |
| 已修 | **6 条**（DRIFT-TASK-RPC-001 / 002 / 003 / 004 / 005 / **006**），每条配守门测试 |
| 未修 | **0 条语义漂移**；另有 2 条**已签字偏离**（`SD-2026-10-23-TASK-01` 与 `SD-2026-10-24-TASK-01`，见 §二） |
| 证据强度 | ✅✅✅ **Kafka 端到端活体对拍**（2026-10-23 23:4x，真 Kafka + 真 Redis + 真 PG，两侧同批消息比最终态，见 §四·三）· ✅✅✅ **Kafka 畸形消息活体对拍**（2026-10-24，五个用例，见 §四·五·二）· ✅✅ **gRPC 双向跨语言对拍**（2026-10-24，Java 客户端 + **Go 客户端**各打两端，见 §四·五·一）· ✅ 真启动 + 真 PG 16 容器 |
| DDL 对拍 | ✅ **GORM AutoMigrate 产物 vs Flyway 产物逐列一致**（本轮实测，见 §四·二） |
| ⚠️ 仍未覆盖 | ⓿ **「Kafka 写入 → task-api 读到」这条跨域闭环**未做活体对拍 —— 但两侧的读写 Redis key **本来就不一致**（Go 侧也读不到自己写的缓存），⇒ **不是 RAG 的缺陷**，见 §五 第 7 条；① ⚠️ **goka 的「表 topic」config（`cleanup.policy` / `retention.ms`）未做逐项对照** —— 但已实测「compact 不按时间删」且 Go 侧**不创建** topic（§四·七）⇒ 两侧**都不管** topic 生命周期 ⇒ 一致 |
| 测试 | **90 tests / 0 failures / 0 errors / 0 skipped**（其中 15 条真 PG、**9 条真 Redis**） |

---

## 一、已修（5 条）

### 🔴 DRIFT-TASK-RPC-001 · `TaskCountStore` 内存单例 ⇒ 两个 goka group 串号 + 重启归零

**性质**：SEMANTIC MISMATCH（行为层，两处）

**Go 依据**（goka `v1.1.16`，本条结论不依赖任何推测，全部读到消费点）：

| 事实 | 出处 |
|---|---|
| 默认存储 = **按 group 名分目录的 LevelDB 落盘** | `storage/builders.go:8-17` `DefaultBuilder(path)` → `leveldb.OpenFile(filepath.Join(path, "<topic>.<partition>"))` |
| 该默认值的注入点 | `processor.go:82` `WithStorageBuilder(storage.DefaultBuilder(DefaultProcessorStoragePath(gg.Group())))` |
| 未注入即报错（不会悄悄退化成内存） | `options.go:361-363` `if opt.builders.storage == nil { return fmt.Errorf("StorageBuilder not set") }` |
| Go task **两次独立 NewProcessor** | `initGoKa.go:178-181`（用户路）、`:212-215`（文章路） |
| 计数语义本体 | `initGoKa.go:75-81` / `:92-97`：`cur := ctx.Value(); cur++; ctx.SetValue(cur)` |

**RAG 修复前的状态**：`TaskCountStore` 是单个 `@Component` + 单张 `ConcurrentHashMap`，被 `TaskUserCountConsumer` 与 `TaskArticleCountConsumer` 共用。

⇒ 两处不等价：

1. **跨 group 串号**。用户路 `task.rpc.consumer61` 的 key 是 `user_id`，文章路 `task.rpc.consumer71` 的 key 是 `article_id`
   （`TaskRawLogRouter.route` 分别以 `event.userId()` / `event.articleId()` 为键发出）。
   两个 id 空间会相交：一旦出现 `user_id == "190"` 且 `article_id == "190"`，两条链路的计数互相污染，
   之后 emit 出去的 `cur` 全部错误 ⇒ 下游两个 sink 的达标判定跟着错。
   ⚠️ **该类 javadoc 原本写着「每个消费组各持有一份」—— 注释描述的是代码没有实现的契约**
   （AGENTS.md §3.3.2 的典型：断言无出处/无证据）。
2. **重启归零**。纯进程内存 ⇒ 重启后所有计数器从 0 起算，而 Go 是 LevelDB 落盘、跨重启连续。
   原 javadoc 的论证「重启后从 0 重新累加，与 goka 从文件恢复相比只是重复计数，
   而下游用的是 max(旧值,新值) 与 SETNX 完成幂等，**重复计数不会造成业务错误**」——
   🔴 **经逐条核对，该论证不成立**：
   - `like:total:<uid>` 是**普通 SET**（`UserLikeProgressSink.pipelineSetLikeTotal`），**不是 GREATEST**
     ⇒ 重启后被**更小的值覆盖**；
   - 达标判定 `total >= target(5)` 读的是**计数器**而不是 DB
     ⇒ 用户必须重新攒够 5 次任务才会完成；
   - 仅 `task:done:<uid>:<taskId>` 的 SETNX（90 天 TTL）不受影响，已完成任务不会被重复发奖。

**修法**：`TaskCountStore` 改为 Redis `INCR`，**按 group id 命名空间隔离**：
`task:goka:count:<groupId>:<messageKey>`。两个 consumer 分别传 `topics.getGroupGokaUser()` /
`getGroupGokaArticle()`，与 goka「按 group 名分目录」一一对应。

- **为什么选 Redis 而不是本地文件**：goka 的**权威副本是 Kafka compacted topic `<group>-table`**，
  本地 LevelDB 只是缓存（`goka doc.go` / `storage` 包注释：「a persistent cache for a topic table」）。
  Redis `INCR` 在**原子 / 跨重启 / 跨实例共享**三点上都更贴近那张表，且 Redis 已是本模块硬依赖
  （`application.yml` 的 `spring.data.redis` 对应 Go `LikeRedis`）。
- **失败语义**：Redis 不可用时**抛异常**让位点不提交（`AckMode.RECORD` + `setAckAfterHandle(false)`），
  对应 Go 侧 goka store 写失败时的退避重投；**不**吞掉异常返回 1
  —— 那会把「计数没加上」伪装成「加了一次」，比丢数据更难排查。
- **新 key 是 RAG 侧实现产物**：Go 侧不存在这个 Redis key。刻意**不复用** `TaskRedisKeys` 的
  `task:progress / task:init / task:done / task:inits / like:total` 任何前缀，避免被误读成 Go 也有的业务键。

**守门测试**：`TaskSinkConsumeTest#countStoreIsolatesGroups`
—— 同一个 id 字符串 `"190"` 分别在两个 group 里累加，必须各自从 1 起（修复前会串成 1,2,3,4）。

---

### 🔴 DRIFT-TASK-RPC-002 · `task.definitions` 的四个 TTL 键是**死配置**

**性质**：配置面失效（行为当前正确，但「配了不管事」）

**Go 依据**：TTL 在 Go 侧是**编译期常量**，无配置面
（`like_sink.go:47,188,284,339` 全部 `90*24h`；`article_sink.go:22` `articleDoneTTL = 90*24h+30*24h`；
`article_sink.go:23` `articleInitTTL = 24h`）。

**RAG 修复前的状态**：`application.yml` 把这些常量提到了配置面，并写明理由
（「提到 yaml 只是为了让『改这个值』必须显式可见，而不是藏在代码魔数里」，`application.yml:184-185`）。
但键名与字段名对不上：

| yaml 键 | Java 字段（修复前） | relaxed binding |
|---|---|---|
| `task.definitions.like.init-ttl` | `initTtlDays: long` | `initttl` ≠ `initttldays` ❌ |
| `task.definitions.article.init-ttl` | `initTtlDays: long` | ❌ |
| `task.definitions.article.done-ttl` | `doneTtlDays: long` | ❌ |

且 `@ConfigurationProperties` 的 `ignoreUnknownFields` 默认 `true`
⇒ **对不上的键被静默丢弃，不报错也不生效**。AGENTS.md §3.3.1 那类
「配置面看起来在管事、实际没在管事」。

**实测证据**（`TaskSinkPropertiesBindingTest`，含**阳性对照**）：

```
Tests run: 3, Failures: 1
  ✅ 阳性对照  redis-pipe-max=1234 → 生效        ⇒ 探针有识别能力，不是空过
  ✅ 断言1    生效值 == Go 常量（90d/24h/120d/5000/2000/1000） ⇒ 当前行为正确
  ❌ 断言2    init-ttl=7d 覆盖后实测 PT24H（默认值）⇒ 该键没绑上
```

**修法**：字段类型 `long` → `java.time.Duration`，键名与 yaml 逐字对应
（`init-ttl` / `done-ttl`），默认值仍为 Go 常量。
不是把 Go 的常量变成可配漂移，而是让**已声明的配置面真正生效**。

**守门测试**：`TaskSinkPropertiesBindingTest`
（断言1 = 生效值必须等于 Go 常量；断言2 = 配置面必须是活的；阳性对照 = 键名对得上的键必须绑得上）。

> ⚠️ **备选方案（未采纳，留档）**：另一种做法是**删掉这四个死键、保持常量在代码里**，
> 那更贴近 Go（Go 本来就没有配置面）。未采纳的理由是 `application.yml` 里的那句声明是作者的**明确设计意图**，
> 单方面删掉属于推翻决策；且「配置面可改」与「默认值 == Go 常量」并存时，风险由断言1 兜住。

---

### 🔴 DRIFT-TASK-RPC-003 · 15 条真 PG 用例静默全跳（证据缺失）

**性质**：不是行为漂移，是**证据链断裂** —— 本域唯一能证明「Flyway 真跑 + MyBatis-Plus 真产出
SqlSessionFactory + `ON CONFLICT`/`GREATEST`/`COALESCE` 真按 PG 语义执行」的 15 条用例，
**一条都没跑**，而 reactor 报 `BUILD SUCCESS`。

根因（实测非推测）与修法详见 [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md)。
一句话：`@EnabledIfDockerAvailable` 类级求值要拿 Docker client ⇒ 触发拉 Ryuk 镜像 ⇒
拉不到抛 `ContainerFetchException` ⇒ 被 `DockerAvailableDetector` 的 `catch (Throwable) → false`
压成「Docker is not available」⇒ 整类 disable。`task-rpc` 是**全仓唯一漏配
`TESTCONTAINERS_RYUK_DISABLED` 的容器模块**。

修法照同仓既有写法（`favorite-rpc` / `follow-rpc` 同款 surefire `environmentVariables` 注入）
+ 删除该注解。实测：`tests=15 skipped=0`（此前 `skipped=15`）。

---

### 🔴 DRIFT-TASK-RPC-004 · `RedisPipelines` 把 Go 的 pipeline 当成 MULTI/EXEC 事务 ⇒ **整条消息消费链路是死的**

**性质**：**行为漂移 + 静默失效**。不是「差一点」，是 RAG 侧的 task 消息处理链路**根本不工作**。

**发现方式**：2026-10-23 Kafka 端到端对拍（真 Kafka + 真 Redis + 真 PG，两侧同时灌同一批 raw 日志）。
⚠️ 这个缺陷**不可能**被此前的任何手段发现 —— 详见末尾「为什么 81 条测试全绿」。

#### 症状

RAG 侧日志出现 4 次（生产实测栈）：

```
io.lettuce.core.RedisCommandExecutionException: ERR EXEC without MULTI
  at io.lettuce.core.internal.Future.awaitAll(Futures.java:213)
  at org.springframework.data.redis.connection.lettuce.LettuceConnection.closePipeline(LettuceConnection.java:596)
Caused by: com.rag.task.task.rpc.service.RedisPipelines.exec(RedisPipelines.java:48)
  at com.rag.task.task.rpc.mq.UserLikeProgressSink.pipelineSetNx(UserLikeProgressSink.java:322)
  at com.rag.task.task.rpc.mq.UserLikeProgressSink.lazyInitTaskIfNeeded(UserLikeProgressSink.java:269)
  at com.rag.task.task.rpc.mq.UserLikeProgressSink.lambda$flushOnce$1(UserLikeProgressSink.java:223)
  at com.rag.common.observability.MqConsumer.traceConsumer(MqConsumer.java:107)
```

#### 根因：Go 的 `Pipeline()` **不是事务**，而 RAG 多发了一个 `EXEC`

| | Go（真源） | RAG（修复前） |
|---|---|---|
| 取 pipeline | `pipe := c.rdb.Pipeline()`（`like_sink.go:167`、`like_sink.go:210`） | `redis.executePipelined(new SessionCallback(){...})` |
| 收尾 | `pipe.Exec(ctx)` | 回调里**再调** `operations.exec()` |
| 是否 MULTI/EXEC | **否** | 是（且 pipeline 模式压根没发过 MULTI） |

Go 侧出处（go-redis v9.20.0，**逐条核过**）：

- `redis.go:1508-1513` `Client.Pipeline()` 的 `exec` 是 `pipelineExecer(c.processPipelineHook)` ⇒ **不包 MULTI/EXEC**；
- `redis.go:1521-1528` `Client.TxPipeline()` 才调 `wrapMultiExec(ctx, cmds)` —— **这才是事务**；
- `pipeline.go:18-20` 源码注释原文：*"Pay attention, that **Pipeline is not a transaction**"*。

Spring 侧出处（spring-data-redis 4.0.7）：

- `LettuceConnection.java:567-575` `openPipeline()` **只置标志位，不发 MULTI**；
- `LettuceConnection.java:688-719` `exec()` 在 `isPipelined()` 分支里直接向服务端发 `EXEC` ⇒ 服务端回 `ERR EXEC without MULTI`；
- `RedisOperations.java:118-128` `executePipelined(SessionCallback)` 的 javadoc 明文：
  *"the callback **cannot** return a non-null value"*、*"SessionCallback#execute(RedisOperations) itself **must return null**"*；
- `RedisTemplate.java:464-467` 框架会**强制**抛 `InvalidDataAccessApiUsageException("Callback cannot return a non-null value…")`。

⇒ 修复前那个 `operations.exec()` **同时**违反了框架契约**和** Go 语义。

#### 🔴 真实杀伤面（不是「报个错」那么轻）

`ERR EXEC without MULTI` 抛在 `lazyInitTaskIfNeeded` 的**第一步 SETNX** 上：

```
SETNX task:init:<uid>:1902  ⇒ 抛
  ⇒ 穿出 lazyInitTaskIfNeeded
  ⇒ 穿出 flushOnce
  ⇒ 被 flushTick catch
  ⇒ **整轮 flush 中止**
```

⇒ `like:total:*`、`task_progress` 写入、任务完成（`task:done:*`）、奖励事件（`stream:task:reward`）
**全部不发生**。也就是说：**RAG 的 task 消息处理链路此前实际是死的** ——
Go 侧同一批消息能正常落库，RAG 侧一条不落。

#### 修法

`RedisPipelines` 两个 helper 统一改成 `executePipelined` + 回调内**不调 `exec()`、返回 `null`**：

- `exec(...)`：**出错抛出**（对应 Go 的 `return err`）；
- `execQuietly(...)`：**吞掉错误**（对应 Go 的 `_, _ = pipe.Exec(ctx)`）。

⚠️ `execQuietly` 修复前用的是 `redis.execute(SessionCallback)` —— 那是**非 pipeline** 路径，
命令**逐条立即执行**，既不批量、又多发一条 `EXEC`。现在统一走 `executePipelined`。

**运行证据**（真 Redis 探针 + 变异测试，见 §四·四）：

| 项 | 修复前 | 修复后 |
|---|---|---|
| `executePipelined` 结果 | 抛 `RedisPipelineException` | `[true,true,true]`，元素类型 `java.lang.Boolean` |
| SETNX 幂等 | 第二次仍抛 | 第二次 `[false,false,false]` |
| 批量性 | 逐条 1719 ms | pipeline 37 ms（**~42x**） |

#### 🔴 为什么 81 条测试全绿

**没有任何一条测试用真 Redis 跑过 pipeline 路径**：

- `TaskSinkConsumeTest` 把 `StringRedisTemplate` 整个 mock 掉，只验内存 delta ⇒ **根本不碰 Redis**；
- `TaskProgressUpsertParityTest` 只验 PG，Redis 指着 `127.0.0.1:1`（不可达）。

⇒ 这是 AGENTS.md §3.3「跑绿 ≠ 对」的又一实例，且比 `article-rpc` 那次更隐蔽：
**测试全绿、编译通过、上下文能启动，但组件的核心功能一行都没执行过**。

**修法**：新增 `RedisPipelinesRealRedisGateTest`（真 Redis 容器，9 条用例，含阳性对照），
并用**变异测试**验证它不是假绿 —— 把 `exec()` 退回 `operations.exec()` 后 **9 条里 5 条报错**。

---

### 🔴 DRIFT-TASK-RPC-005 · `pipelineHSet` 吞掉 Redis 错误，而 Go 是**向上抛**（终态分叉）

**性质**：行为漂移。与 005 同源（都在 Redis pipeline 错误处理上），但**方向相反**，单独开单。

| 路径 | Go（真源） | RAG（修复前） |
|---|---|---|
| `lazyInit` 的 HSET | `like_sink.go:223-225` `if _, err := pipe2.Exec(ctx); err != nil { return err }`<br>`article_sink.go:308-310` 同款 ⇒ **抛** | `RedisPipelines.execQuietly` ⇒ **吞** |
| `GetTask` 的缓存回填 | `get_task_logic.go:73-76` `_, _ = pipe.Exec(ctx) // 回填失败不影响返回` ⇒ **吞** | `execQuietly` ⇒ 吞 ✅ **本来就对** |

**后果**（用户路径 HSET 失败时）：

- Go：`lazyInitTaskIfNeeded` 抛 ⇒ `flushOnce`（`like_sink.go:134-142`）**直接 return** ⇒
  `flushRedis` / `flushPostgres` **都不执行** ⇒ 最终态：**Redis 无进度 Hash、PG 无初始行**；
- RAG（修复前）：吞掉错误 ⇒ 继续走 `progressWriter.lazyInit(records)` ⇒
  最终态：**PG 有初始行、Redis 进度 Hash 缺失**。

⇒ **两侧终态分叉**，且 RAG 侧多写了一半数据。

**修法**：`UserLikeProgressSink.pipelineHSet` 与 `ArticleLikeProgressSink` 的 HSET 改用
`RedisPipelines.exec`（抛），只有 `TaskDomainService.fillProgressCache` 保留 `execQuietly`（吞）——
因为它对应的 Go `get_task_logic.go:76` 明确写着「回填失败不影响返回」。

⚠️ 这条**不是**「统一风格」：用错吞/抛就会分叉，所以两个 helper 的契约写进了 javadoc。

### 🔴 DRIFT-TASK-RPC-006 · per-listener 的 offset-reset 写成了**连字符** ⇒ Kafka 直接忽略 ⇒ **earliest 从未生效**

**性质**：SEMANTIC MISMATCH（行为层，且**上一版台账记的是「配置意图一致」** ⇒ 结论本身就是错的）

**这是 2026-10-24 活体验证「goka 的 OffsetOldest」时查出来的**（§四·八）。

| 路径 | Go（真源） | RAG（修复前） |
|---|---|---|
| goka 路由组 | `cfg.Consumer.Offsets.Initial = sarama.OffsetOldest`（`initGoKa.go:131`）**真实生效** | `@KafkaListener(properties = "auto-offset-reset=earliest")` ⇒ **Kafka 认不出这个键 ⇒ 忽略** ⇒ 实际是全局的 `latest` |
| goka 用户计数 | `sarama.OffsetOldest`（`initGoKa.go:173`） | 同上 |
| goka 文章计数 | `sarama.OffsetOldest`（`initGoKa.go:207`） | 同上 |
| kq 诊断消费者 | 硬编码 `Offset: "latest"`（`initKafkaRaw.go:32`） | `properties = "auto-offset-reset=latest"` ⇒ **同样被忽略** ⇒ 但**碰巧对**（全局默认恰好也是 latest） |
| kq 用户 sink | 硬编码 `Offset: "latest"`（`initUserFilter.go:47`） | 同上（碰巧对） |
| kq 文章 sink | 硬编码 `Offset: "latest"`（`initArticleFilter.go:40`） | 同上（碰巧对） |

**根因（实测证据）**：

```
# RAG 侧 consumer 的 config dump（真 Kafka）：
auto.offset.reset = latest
AbstractConfig : These configurations '[auto-offset-reset]' were supplied but are not used yet.
```

- ⚠️ **Kafka 的合法配置名是点分的 `auto.offset.reset`**（`ConsumerConfig.AUTO_OFFSET_RESET_CONFIG`）。
- ⚠️ **`@KafkaListener(properties = ...)` 不走 Spring Boot 的松散绑定** ⇒ 字符串**原样**传给 Kafka
  ⇒ 连字符形式 `auto-offset-reset` 落进 `ConfigDef.parse` 的 **unused** 分支
  ⇒ **只打一条 info 日志，然后被彻底忽略**。
- ⇒ **同一个语义、两条完全不同的生效路径**：
  - 全局的 `spring.kafka.consumer.auto-offset-reset: latest` **生效**（Boot 松散绑定会转成点分形式）；
  - per-listener 的 `properties = "auto-offset-reset=earliest"` **不生效**。

**后果**：**「consumer group 首次消费、尚无已提交位点」**这一场景下
—— 也就是「服务重启后 group 位点被清空」「新环境首次接入」「运维重置位点」——
**RAG 从队尾开始（历史消息永远不参与计数），Go 从队头开始** ⇒ 行为分叉。

**为什么此前 326 条测试全绿也抓不到**：
⚠️ **所有既有对拍都是「先起消费者、后灌消息」** ⇒ 那时 `latest` 与 `earliest`
**没有可观察差别** ⇒ 绿测试是**假绿**。
⇒ 只有「**先灌消息、再起消费者**」才暴露它 —— 那正是
[`tools/difftest/run-task-offset-oldest.sh`](../../../tools/difftest/run-task-offset-oldest.sh) 做的事。

**修法**：六个 listener 的 `properties` 全部改成**点分**的 `auto.offset.reset`：
goka 三处 `=earliest`、kq 三处 `=latest`。
⚠️ kq 三处虽「碰巧对」，也必须改：否则将来把全局默认改成 earliest，这三处会**静默**变成 earliest。

**守门**：
1. [`TaskKafkaOffsetResetContractTest`](../../../../service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mq/TaskKafkaOffsetResetContractTest.java)
   （5 条）：**阳性对照**用 Kafka 自己的 `ConsumerConfig` 证明「连字符形式确实不被采纳」
   （加了连字符键 vs 完全不给这个键，效果**完全一样**）
   + 六个 listener 的精确断言 + **通用断言**：所有 per-listener property 的键名
   **都必须是 `ConsumerConfig.configNames()` 里认识的合法名** ⇒ 以后写错任何键名都会被立刻抓住。
   ✅ **变异测试已做**：把任一 listener 改回连字符 ⇒ **2 条变红**（报的正是「不是 Kafka 的合法配置名」）⇒ 非假绿。
2. **运行证据**：`run-task-offset-oldest.sh` 的 A2/A4 —— 修复前 FAIL（`<absent>`），修复后 PASS（`5`）⇒ 见 §四·八。

---

## 二、签字偏离与有意偏离

| 编号 | 内容 | 落点 |
|---|---|---|
| `max-lifetime: 1800000` | Go `service_context.go:45` 写 `SetConnMaxLifetime(time.Duration(c.ConnMaxLifetimeMinutes))` —— **漏乘 `* time.Minute`**，生效值是 30 **纳秒**。RAG 落 30 分钟 | 已在本轮登记为 [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md) 的 `SD-2026-10-23-TASK-01` |
| 奖励流**只写不读** | Go `reward.Worker` 与 `Init.StartInitReward` **整个文件是注释**、`task.go:35` 调用也被注释 ⇒ 线上 `stream:task:reward` 只堆积无消费者。RAG **刻意不补消费者**（会引入对 points 域的依赖，而 points 不在范围内） | 与 Go 一致，非偏离；但记录在此以免后人「顺手补上」 |
| 奖励事件字段名两端对不上 | Go producer 写 `"score"`（`reward/producer.go:27`）、commented-out 的 consumer 读 `"add_score"`（`reward/worker.go:120`）。RAG **照抄 producer 的 `score`** | 与 Go producer 一致；consumer 在 Go 侧本就不存在 |

---

## 三、逐条对照结论（已核为一致的部分）

> 本节是「查完发现没毛病」的记录，**同样需要证据**，避免下一轮重复查。
> 每条的「✅」含义是：**在本节标注的证据强度下，两侧行为一致**，不是「已闭环」。

### `GetTask`（`get_task_logic.go` ↔ `TaskDomainService`）

| 行为点 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| `uid <= 0` → `InvalidArgument("user_id invalid")` | `get_task_logic.go:43-45` | `TaskDomainService.getTask:90-94` | ✅ 文案逐字一致 |
| 缓存 key `task:progress:<uid>` | `get_task_logic.go:106-108` | `TaskRedisKeys.userProgressCache` | ✅ |
| Redis 报错**也算未命中**（回源） | `get_task_logic.go:51` `rerr != nil \|\| len(pm)==0` | `TaskDomainService.readProgressCache:161-167` catch → 返回 null | ✅ |
| 回填失败**不影响返回** | `get_task_logic.go:76,78` 错误全丢弃 | `fillProgressCache` / `expireQuietly` 均吞异常 | ✅ |
| DB 无行时对**不存在的 key** 设 30s TTL = no-op | `get_task_logic.go:78` | `EMPTY_PROGRESS_CACHE_TTL` + 注释显式标注「不要改写成空标记防穿透」 | ✅ **保留 Go 的无效行为** |
| 回源 SELECT 列集 | `get_task_logic.go:55` `user_id, task_id, status, progress, target` | `TaskProgressMapper.selectByUserId` 逐列一致 | ✅ 无 status 过滤 |
| 打包 `status\|progress\|target` | `get_task_logic.go:115-118` | `packProgress` | ✅ |
| 解析保留**尾部空串** | `strings.Split` 语义 | `split("\\|", -1)` + 显式注释说明为何不能用默认 split | ✅ |
| 段数 ≠ 3 ⇒ 0 | `get_task_logic.go:122-124` | `unpackProgressValue` | ✅ |
| `required_progress` 取自**任务定义**而非 DB `target` | `get_task_logic.go:86-87` 注释 + `:87` | `TaskDefs` + `TaskDomainService:126` | ✅ |
| 两条任务定义（190/1902, 3/5, 中文名与 desc） | `model/taskModel.go:22-25` | `TaskDefs.ALL_TASK_DEFS` | ✅ 逐字一致 |

### 点赞 sink（`like_sink.go` ↔ `UserLikeProgressSink`）

| 行为点 | Go 出处 | 结论 |
|---|---|---|
| key 解析 `ParseInt(key,10,**63**)` | `like_sink.go:83` | ✅ RAG 显式补 `[-2^62, 2^62-1]` 范围检查并在注释里说明「Go 的 bitSize 63 比 int64 窄一半」 |
| `userID <= 0` ⇒ 正常返回（提交位点） | `like_sink.go:87-89` | ✅ |
| val 解析失败**保留默认 1**（不是丢弃） | `like_sink.go:91-96` | ✅ 注释点名「这是本 sink 最容易被写错的一处」 |
| `delta[uid] = max` | `like_sink.go:102` | ✅ `delta.merge(..., Math::max)` |
| flush 分块阈值 **`n > redisPipeMax`**（注意是 `>` 不是 `>=`） | `like_sink.go:286` | ✅ RAG 保留 `>` |
| `exec()` 遍历**整批**而非当前分块 | `like_sink.go:270` `for uid, total := range batch` | ✅ RAG 保留该怪癖（`advanceProgress(batch)`） |
| `like:total:<uid>` 普通 SET + 90d | `like_sink.go:284` | ✅ |
| init key `task:init:<uid>:<taskId>`（**init 单数**、uid 在前）+ 90d | `like_sink.go:186,188` | ✅ |
| done key `task:done:<uid>:<taskId>`（**uid 在前**）+ 90d | `like_sink.go:338` | ✅ |
| 进度 key `task:progress:<uid>:<taskId>` | `like_sink.go:214` | ✅ |
| `!ok`（SETNX 已存在）⇒ 静默返回，后续三步全跳过 | `like_sink.go:340-342` | ✅ |
| **Enqueue 失败 ⇒ `markTaskDoneInDB` 与缓存失效都不执行** | `like_sink.go:356-362` | ✅ RAG 让异常上抛、由 `advanceProgress` 捕获 |
| `user_like_count.user_id` 是 **TEXT**（`task_progress.user_id` 是 BIGINT） | `like_sink.go:18` vs `taskModel.go:29` | ✅ DDL 显式保留该不一致并注明「不要顺手统一」 |
| GREATEST 单调 upsert | `like_sink.go:415-422` | ✅ |
| 缓存失效 DEL `task:progress:<uid>` | `like_sink.go:332-334` | ✅ |

### 文章 sink（`article_sink.go` ↔ `ArticleLikeProgressSink`）

| 行为点 | Go 出处 | 结论 |
|---|---|---|
| `key == ""` ⇒ 正常返回 | `article_sink.go:95-97` | ✅ |
| `articleID == 0` ⇒ **返回 nil（提交位点、安静丢弃）**，且发生在 `parseVal` **之前** | `article_sink.go:99-101` | ✅ 注释点名「漏掉这步会让 key=0 的消息挂到 `delta[0]` 上」 |
| `parseVal`：`cur == 0 ⇒ cur = 1` | `article_sink.go:132-134` | ✅ |
| 类型错误 ⇒ **整条 Unmarshal 失败**（不是取零值） | `article_sink.go:131` | ✅ RAG 先判类型再取值 |
| delta 取 max，**相等时保留旧 user_id** | `article_sink.go:116-120` | ✅ |
| init key `task:inits:<taskId>:<uid>:<articleId>`（**inits 复数、taskId 在前、带 articleId**）+ 24h | `article_sink.go:255-258` | ✅ |
| done key `task:done:<taskId>:<uid>`（**与点赞路段序相反**）+ 120d | `article_sink.go:427-428` | ✅ |
| 进度 key `task:progress:<taskId>:<uid>:<articleId>` | `article_sink.go:436-439` | ✅ |
| **奖励分值传 5**（不是该任务的 target=3） | `article_sink.go:447` | ✅ 注释显式标注「刻意不联动」 |
| HSet 错误**被丢弃**（点赞路是检查后 return） | `article_sink.go:440` `_, _ =` | ✅ RAG try/catch 只记日志 |
| `FlushPostgres` **整段被注释** ⇒ 文章路不写 `user_like_count` | `article_sink.go:176-178` | ✅ RAG `flushOnce` 只有 lazyInit + flushRedis |
| flush 阈值 `n >= redisPipeMax`（与点赞路 `>` 相反） | `article_sink.go:376` | ✅ |
| `FlushRedis` 过滤 `UserID == 0 \|\| total <= 0`（点赞路**没有**这个过滤） | `article_sink.go:365-367` | ✅ 两侧的不对称都保留 |

### Kafka 拓扑（`Init/*.go` ↔ `mq/*`）

| 组 | Go | RAG | offset |
|---|---|---|---|
| 诊断消费者（什么都不做） | `initKafkaRaw.go` group `consumer31`, topic `task.logs.raw` | `TaskRawDiagnosticConsumer` | `latest` ✅ |
| goka 主路由 | `initGoKa.go:123-128` group `consumer41` | `TaskRawLogRouter` | **`earliest`** ✅ |
| goka 用户计数 | `:164-169` group `consumer61` | `TaskUserCountConsumer` | **`earliest`** ✅ |
| goka 文章计数 | `:196-203` group `consumer71` | `TaskArticleCountConsumer` | **`earliest`** ✅ |
| 点赞 sink | `initUserFilter.go` group `consumer81` | `UserLikeProgressSink` | `latest` ✅ |
| 文章 sink | `initArticleFilter.go` group = **`GroupKafkaFilter`** = `consumer101`（**不是** `GroupFilterArticle`） | `ArticleLikeProgressSink` | `latest` ✅ |

- ✅ goka 三处 `OffsetOldest` 与 kq 三处 `"latest"` **方向相反**，RAG 逐 listener 用 `properties` 覆盖，与 Go 一致。
- ✅ `GroupFilterArticle`（`consumer91`）在 Go 侧**无任何读取点**，RAG 只保留配置面、不建 listener。
- ✅ yaml 的 `Offset/Consumers/Processors` 三个字段在 Go 侧**从未被读取**（`Init/*.go` 全部硬编码 `Consumers:1, Processors:1`），
  RAG 刻意不映射，并在注释里写明「映射了会让人误以为并发度是 8」。
- ✅ goka 三个 processor 用 `codec.Int64` 编**十进制 ASCII**（`goka/codec/codec.go:56`）⇒ value 用 `StringDeserializer` 而非 ByteArray，有注释佐证。
- ✅ `kq` 无重试循环 ⇒ RAG 的 `DefaultErrorHandler` 显式 `FixedBackOff(1000, 1)`（maxAttempts=1）+ `ackAfterHandle(false)` + `seekAfterError(false)`。
- 🔴 **`goka` 的 `OffsetOldest` 只作用于「首次消费无位点」的 group** —— 本条**已做活体验证**（2026-10-24，见 §四·八），
  并且**验证过程中查出 RAG 侧一条真漂移**（`auto-offset-reset` 写成了连字符 ⇒ Kafka 直接忽略 ⇒ 实际是 `latest`），
  ⇒ 已修（[DRIFT-TASK-RPC-006](#🔴-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)）。

### DDL（`AutoMigrate` ↔ Flyway）

- ✅ `task_progress`：`user_id/task_id BIGINT NOT NULL` 复合主键；`status/progress/target` 可空；
  `done_at/created_at/updated_at` **TIMESTAMPTZ**（GORM 在 PG 上把 `time.Time` 映射成 `timestamptz`，不是 `timestamp`）。
- ✅ `user_like_count`：`user_id TEXT NOT NULL` 主键（**与 task_progress 的 BIGINT 不同**，Go 侧本就不一致）。
- ✅ **不加 CHECK、不加额外索引** —— GORM `AutoMigrate` 不会为无 tag 的列生成 CHECK，加了就变成 RAG 侧独有的约束。
- ✅ `TaskDef` 表**不存在**（Go 侧它是代码常量 `model/taskModel.go:22-25`，没进 `AutoMigrate`）。
- ✅ 以上均由真 PG 容器用例 `columnTypesMatchGo` / `compositePrimaryKey` / `flywayCreatedTables` 实测。

---

## 四、真启动证据（§3.3「先让它真的启动一次」）

> 来源：`TaskProgressUpsertParityTest` 的 surefire `system-out`（真 PostgreSQL 16 容器 + 完整 Spring 上下文）。
> **不是**「编译通过」，**不是**「纯 mock 单测全绿」。

| 证据 | 实测输出 |
|---|---|
| 独立 Flyway 账本真建出来 | `Creating Schema History table "public"."flyway_schema_history_task"` |
| **V1 真跑了**（`baseline-version: 0` 修复有效） | `Migrating schema "public" to version "1 - create task progress"` |
| V2 真跑了 | `Migrating schema "public" to version "2 - create user like count"` |
| gRPC server 真监听 | `gRPC Server started, listening on address: 0.0.0.0, port: 34430` |
| 两张表真在位 | `TaskSchemaGate: all 2 task tables present` |
| Kafka listener 真装配（6 个 group 全部在起） | `consumer41/61/71/81/101/31` 的 client 日志 |

> ✅ 这同时为 AGENTS.md §6.3 问题 4 补上 task-rpc 的**运行时**证据：
> `spring.flyway.table: flyway_schema_history_task` 确实生效，与 message-rpc / like-rpc 的账本两两不同。
> ⚠️ **「三模块同库共存」仍未闭环**（本条只证明单模块启动自洽）。

---

## 五、证据边界（必须读，否则会高估本台账）

> 本节是「哪些结论有多硬」的诚实标注。**任何一条 ✅ 都要连同本节一起读**。

1. ✅ **HTTP wire 层双侧活体对拍**（2026-10-23 22:5x）：Go `task-rpc:19005` + `task-api:18886`，
   RAG `task-rpc:59005` + `task-api:58886`，共用 PG 容器 / Redis / Kafka，43 条请求矩阵。
   覆盖：**Redis HGETALL → PG 回源 → packProgress/unpackProgress → 任务定义合并 → gRPC 序列化 → HTTP 响应**。
2. ✅ **gRPC 单组件对拍**（2026-10-23 23:0x）：同 stub 连两个端口，比 **code + description + 载荷**，
   7 条用例全一致 —— 含 **3 组 HTTP 面永远走不到的 `INVALID_ARGUMENT`**（`description` 逐字节为
   `user_id invalid`）。⇒ HTTP 面被抹掉的 gRPC 层语义已单独验过。
   ✅✅ **跨语言方向也补齐了**（2026-10-24，见 §四·五·一）：新增 **Go 客户端 → 两端服务端**
   （真源自己的生成桩、真源零写入），7/7 一致 ⇒ grpc-go ↔ grpc-java 的**双向**互操作都验过
   （原先只有「Java 客户端 → Go 服务端」这一个跨语言方向）。
3. ✅✅ **Kafka 消息链路端到端对拍**（2026-10-23 23:4x）：真 Kafka + 真 Redis + 真 PG，
   同一批 7 条 raw 日志灌两侧 in-topic，比对 **Redis 全 key + 奖励流 + PG 两表 + goka 计数值**
   ⇒ **完全一致**（§四·三）。
   ✅ **畸形消息也已做活体对拍**（2026-10-24，见 §四·五·二）：`LogEvent` 解析失败 / `uid <= 0` /
   `total <= 0` / key 解析失败 / 毒丸之后再来一条合法消息 —— **五个用例两侧行为完全一致**。
   ⇒ 此前「只有源码级对照」的那条缺口**已关闭**。
   ⚠️ 但覆盖面**仍只到「读路径 + 消息消费路径」**：未覆盖**「Kafka 写入 → task-api 读到」这条闭环**
   （见本节第 7 条）—— 那条受限于 Go 侧读写 Redis key 本来就不一致，**不是 RAG 的缺陷**。
4. ✅ **DDL 逐列活体对拍**（§四·二）：GORM `AutoMigrate` 产物 vs Flyway 产物，
   类型 / 可空性 / 复合主键**逐项一致**；仅**主键约束名**不同（`ON CONFLICT` 走列，行为无差异）。
   ⚠️ 索引 / 唯一约束 / CHECK：两侧**一致地没有**（不是漏检）。
5. ⚠️ **Redis 侧现已是「真 Redis 活体」**（DRIFT-TASK-RPC-001 的 `INCR` 路径已由
   §四·三 的端到端对拍 + §四·四 的真 Redis 守门测试双重覆盖）。
   ⚠️ 但 `RedisPipelines` 的**事务/非事务语义**只验到「行为等价于 Go 的非事务 pipeline」，
   **未**验「与 Go 的 `TxPipeline` 等价」—— 因为 Go 侧**根本没用** `TxPipeline`（这是结论，不是缺口）。
6. ⚠️ `service/common`（被 task-rpc 依赖）的结论来自它自己的台账，本轮**未重审**。
7. ⚠️ **Go 侧未跑 `task-api`**：Kafka 对拍只到 `task-rpc`。`task-api` 侧的
   `GetTask` 读路径已由 HTTP 对拍覆盖，但**「Kafka 写入 → task-api 读到」这条闭环未做活体对拍**
   （两侧的 key 形状不一致这件事见 `TaskRedisKeys` 类注释，是 Go 侧既有的读写 key 不一致，**不改**）。
8. ✅ **「首次消费无位点」这一分支已单独活体验证**（2026-10-24，见 §四·八）：
   全新 group + **预灌消息在消费者启动前** ⇒ 两侧的 goka 路都读到队头（计数 = 5、group 位点 = 7），
   两侧的 kq 路都跳过预灌消息（阴性对照）⇒ 且**验证过程中查出并修掉了一条真漂移**
   （[DRIFT-TASK-RPC-006](#🔴-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)）。
   ⚠️ **这条证据只覆盖「无位点」这一条分支**；「有位点」时两侧都是「从已提交位点继续」（Spring 的
   `AckMode.RECORD` ↔ Go 的 `CommitMessages`），已由 Kafka 端到端对拍（§四·三）与畸形消息对拍（§四·五·二）覆盖。
9. ✅ **goka 表的「保留策略」已用实测定案**（2026-10-24，见 §四·七）：
   `cleanup.policy=compact` 单独使用时 **Kafka 不按时间删**（带阳性对照的对照实验）
   ⇒ Go 的计数**永不丢失** ⇒ RAG 的 `task:goka:count:*` **不设 TTL 就是等价实现**。

---

## 六、本轮引入的新风险（明写，不藏）

| 风险 | 说明 | 处置建议 |
|---|---|---|
| Redis 变成计数的硬依赖 | 此前计数是纯内存，Redis 挂了只影响进度缓存（会降级回源）；**现在 Redis 挂了会阻塞 goka 计数组**（位点不提交、消息堆积） | 已在类注释写明取舍。若运维不接受，把 `TaskCountStore` 换成本地文件 store（更贴 goka 的 LevelDB）即可，其余代码不用动 |
| Redis key 无 TTL、无上限 | 🔴 **原表述的前提是错的**（2026-10-24 实测推翻，见 §四·七·二）：<br>Go 侧的计数表**不随 Kafka retention 滚动** —— goka 的表 topic 只设了 `cleanup.policy=compact`、**没有 `retention.ms`**，且 `compact` 单独使用时 Kafka **不按时间删**（实测）。<br>⇒ **这不是与 Go 的漂移，而是与 Go 一致**：Go 的权威存储是本地 LevelDB（无 TTL、task 域从不调 `Delete`）⇒ **计数永不丢失**。<br>⚠️ 但「Redis 内存无上限」本身仍是真的运维事实（RAG 侧是内存，Go 侧是磁盘） | **不加 TTL** —— 加了会造成**业务错误**（计数到期归零 ⇒ `like:total` 是普通 SET 不是 GREATEST ⇒ 被更小的值覆盖），并制造一条与 Go 的新漂移（§四·七·二）。<br>⇒ 改为「**用真 Redis 守门测试把「不设 TTL」锁成契约**」<br>（`TaskCountStoreRetentionContractTest`，4 条，含阳性对照）。<br>⚠️ 若将来确实要内存有界，**必须先改 Go 侧**或同时改 `like:total` 的写法 ⇒ 那已是行为变更，不在本域单方面做 |
| 新增了一条 gRPC→Redis 的同步依赖 | 每条 raw 日志一次 `INCR` RTT | 与 Go 的「每条消息一次本地 DB 写 + 定期 Kafka 表 emit」量级相当，判断为可接受 |

---

## 七、待办

1. ~~**Kafka 链路的畸形消息活体对拍**~~ ⇒ **2026-10-24 已做**（见 §四·五·二）：
   五个用例（`LogEvent` 解析失败 / `uid <= 0` / `total <= 0` / key 解析失败 / 毒丸后再来一条合法消息）
   **两侧行为完全一致**，`EXIT=0`。
   ⚠️ 顺带**推翻了本仓 javadoc 里一条错误断言**（「Go 会被 kafka-go 的自动提交抵消」）——
   kafka-go 用 `FetchMessage` 时自动提交不生效（`reader.go:811-812`），活体实测也证实
   ⇒ 已改写为源码 + 活体双出处的说法。
2. **「Kafka 写入 → task-api 读到」的闭环对拍**：本轮 Kafka 对拍只到 `task-rpc`；
   `task-api` 的读路径（`task:progress:<uid>`，与 sink 写的 `task:progress:<uid>:<taskId>` **不是同一个 key**）
   只由 HTTP 对拍间接覆盖。这是 Go 侧既有的读写 key 不一致，**不要「顺手统一」**。
3. ~~`task:goka:count:*` 的保留策略（见 §六）—— **无 TTL、只增不删**~~
   ⇒ **2026-10-24 关闭**（见 §四·七）：**结论是「不加 TTL」**。
   ⚠️ 原待办的**前提是错的**（「goka 的表会随 Kafka retention 滚动」）：
   goka 的表 topic 只设 `cleanup.policy=compact`、**没有 `retention.ms`**，
   而 compact 单独使用时 **Kafka 不按时间删**（带阳性对照的对照实验，§四·七·二）⇒
   **Go 的计数表 key 永不按时间过期**（权威存储是本地 LevelDB，默认无 TTL 且从不调 `Delete`）
   ⇒ **RAG 不设 TTL 就是等价实现**，加了反而会造成业务错误（计数归零 ⇒ `like:total` 被更小的值覆盖）。
   ⇒ 改为「**用真 Redis 守门测试把「不设 TTL」锁成契约**」
   （`TaskCountStoreRetentionContractTest`，4 条，含阳性对照 + ✅ 变异测试已做）。
4. ~~`tools/difftest/check-ddl-static.py` 扩到 task 域~~ ⇒ **2026-10-24 已做**：
   脚本改成 `--domain users|task` 参数化；task 域实测
   **11 列逐字一致、类型/长度 0 差异、NOT NULL/DEFAULT 0 差异**，`EXIT=0`。
   ⚠️ 顺带**补上了此前标为 NOT CHECKED 的 NOT NULL / DEFAULT 对照**
   （users 域的台账数字未受影响，那一节是新增的）。
   ⚠️ 另需排除 **Flyway 迁移账本表**（`flyway_schema_history_task`）——
   它是 RAG 侧独有的工具产物，不是业务表，不排除会被判成「列集合不一致」。
5. ~~`tools/difftest/check-proto-static.sh` 扩到 `task`~~ ⇒ **2026-10-24 已做**：
   task 域实测 `EXIT=0`（`task.proto ↔ task.proto` 逐字节一致）。
   ⚠️ task 两侧的 `package task;` **逐字相同** ⇒ **不需要**像 `like` 域那样额外排除 package 行。
   ⚠️ 顺带把该脚本的**退出码**从「恒 0」改成「有差异返回 1」⇒ 现在能当门禁用。
6. **`tools/difftest/parity-task-kafka.py` 纳入 CI**：目前它依赖手工起的 `taskprobe-*` 基建，
   没法在普通 `mvn test` 里跑。可考虑用 Testcontainers 起 Kafka（`apache/kafka` KRaft 镜像）自动化。
   ⚠️ **2026-10-24 已补上另一半**：HTTP 侧现在有一键编排
   [`tools/difftest/run-task-http-parity.sh`](../../../tools/difftest/run-task-http-parity.sh)
   （原先 HTTP 侧只有手工步骤，Kafka 侧才有一键脚本）⇒ 两边的「一条命令复跑」齐了，
   但**都还依赖手工起的基建**，进 CI 仍需 Testcontainers。
7. ⚠️ **`run-task-offset-oldest.sh` 与 `run-task-kafka-e2e.sh` 的关系**：
   前者是**专门为「无位点分支」设计**的（顺序与 E2E **相反**：先灌消息、再起消费者），
   后者验的是「正常消费路径」（先起消费者、再灌消息）。
   ⇒ **两者都要保留**，不要用前者替代后者
   （前者**不**验畸形消息、**不**验 DDL、**不**验 PG 表）。
   ⇒ ⚠️ 前者还会**重建 `taskprobe-*` 容器** ⇒ 会丢弃该容器上的既有数据。
8. ⚠️ **本分支改了 4 个多域共享工具**（合并时留意）：
   `check-proto-static.sh`（退出码语义）、`check-ddl-static.py`（参数化）、
   `check-plaintext400-headers.sh`（加 task 段）、`run.sh`（2 处 bug 修复），
   外加**新增** `docker-compose.task-kafka.yml` 的 `name: taskprobe`（修跨域误删）。
   ⇒ 清单见 [`./_README.md`](./_README.md) 的「共享工具改动清单」。

---

## 八、相关文件

| 关系 | 文件 |
|---|---|
| 本域 api 台账 | [`api.md`](./api.md) |
| 本域 proto 台账 | [`proto.md`](./proto.md) |
| 踩坑（Ryuk 静默 skip / 死配置） | [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md) |
| 有意偏离 | [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md) |
| 四域排序与本域定位 | [`../_cross/component-coverage-matrix.md`](../_cross/component-coverage-matrix.md) |

---

## 四·二、DDL 活体对拍：GORM `AutoMigrate` 产物 vs Flyway 产物（2026-10-23 实测）

**这一节直接关闭了上一版台账 §五 的第 3 条证据缺口。**

做法：两侧各连同一个 PG 容器的**不同库**（`task_go_db` 走 Go 的 `AutoMigrate`，
`task_rag_db` 走 RAG 的 Flyway），然后 `\d` 逐列读真实建表结果再对拍。

| 表 | 列 | Go `AutoMigrate` | RAG `Flyway` | 结论 |
|---|---|---|---|---|
| `task_progress` | `user_id` | `bigint not null` | `bigint not null` | ✅ |
| | `task_id` | `bigint not null` | `bigint not null` | ✅ |
| | `status` | `text`（可空） | `text`（可空） | ✅ |
| | `progress` | `bigint` | `bigint` | ✅ |
| | `target` | `bigint` | `bigint` | ✅ |
| | `done_at` | **`timestamp with time zone`** | **`timestamp with time zone`** | ✅ |
| | `created_at` | `timestamp with time zone` | `timestamp with time zone` | ✅ |
| | `updated_at` | `timestamp with time zone` | `timestamp with time zone` | ✅ |
| | 主键 | `PRIMARY KEY (user_id, task_id)` | `PRIMARY KEY (user_id, task_id)` | ✅ |
| | 约束名 | `task_progress_pkey`（GORM 默认） | `pk_task_progress`（显式命名） | ⚠️ 仅**名字**不同 |

| 表 | 列 | Go | RAG | 结论 |
|---|---|---|---|---|
| `user_like_count` | `user_id` | `text not null` | `text not null` | ✅ |
| | `like_count` | `bigint` | `bigint` | ✅ |
| | `updated_at` | `timestamp with time zone` | `timestamp with time zone` | ✅ |
| | 主键 | `PRIMARY KEY (user_id)` | `PRIMARY KEY (user_id)` | ✅ |

**结论**：✅ **列类型 / 可空性 / 主键构成逐项一致**。
⚠️ 主键**约束名**不同，但 `ON CONFLICT (user_id, task_id)` 走的是**列**而不是约束名 ⇒ 行为无差异。
⇒ 这**印证**了台账里「`time.Time ⇒ TIMESTAMPTZ`」「`string ⇒ TEXT`」「无 tag ⇒ 可空」的判断。

⚠️ 索引 / 唯一约束 / CHECK：两侧**一致地没有**（Go 的 `AutoMigrate` 不为无 tag 列生成 CHECK，
RAG 的迁移脚本刻意不加）⇒ 这一项**已闭合**，**不是「漏检」**。

---

## 四·三、Kafka 端到端对拍：两侧灌同一批 raw 日志，比最终态（2026-10-23 23:4x 实测）

**这一节关闭了上一版台账 §五 的最后一条证据缺口**（上一版原文：「Kafka 消息链路只验到装配，未做端到端消息对拍」）。

### 做法

真 Kafka（KRaft 容器）+ 真 Redis + 真 PG，两侧**完全隔离**地跑同一套栈：

| | Go 真源侧 | RAG 侧 |
|---|---|---|
| Kafka topic | `task.logs.raw*` / `task.logs.filtered.*` | `rag.task.logs.raw*` / `rag.task.logs.filtered.*` |
| consumer group | `task.rpc.consumer31/41/61/71/81/101` | `rag.rpc.consumer31/41/61/71/81/101` |
| Redis | `taskprobe-redis:56380` **db0** | 同容器 **db1** |
| PG | `task_go_db`（GORM `AutoMigrate` 建表） | `task_rag_db`（Flyway 建表） |

同一批 7 条 raw 日志（`LogEvent` JSON，Go `initGoKa.go:19-27` 的字段）**同时**灌两侧的 in-topic：

```
user 5001 × 5 条（article 9001）→ 恰好达到 target=5 ⇒ 走「完成」分支
user 5002 × 2 条（article 9002）→ 未达标 ⇒ 走「doing」分支
```

覆盖了 **用户点赞路 + 文章路** 的两条完整链路（raw → goka 路由 → goka 计数 → filtered → kq → sink → Redis/PG）。

### 结果

**10 个 topic 消息数逐个相等**（各 7 条），两侧消费者都认领了分区，Go 侧 goka 无错误。

| 对拍项 | Go | RAG | 结论 |
|---|---|---|---|
| Redis `like:total:5001` / `:5002` | `5` / `2` | `5` / `2` | ✅ |
| Redis `task:init:5001:1902` / `:5002:1902` | `1` / `1`，TTL 90d | 同 | ✅ |
| Redis `task:inits:190:5001:9001` / `:5002:9002` | `1` / `1`，TTL 24h | 同 | ✅ |
| Redis `task:progress:*`（4 个 Hash） | status/progress/target 全同 | 同 | ✅ |
| Redis `task:done:5001:1902`（90d）/ `task:done:190:5001`（120d） | `1` / `1` | 同 | ✅ |
| Redis `article:like:cnt:9001` / `:9002`（120d） | `5` / `2` | 同 | ✅ |
| Redis `stream:task:reward` | 2 条，字段集 `{reward_id,uid,task_id,ts,score}` | 同 | ✅ **连 `reward_id` 都逐个相同**（1902→`6903`、190→`5191`） |
| PG `task_progress` | 4 行 | 4 行，字段全同 | ✅ |
| PG `user_like_count` | `5001`→5、`5002`→2 | 同 | ✅ |
| goka 计数值（用户路 / 文章路） | compacted table topic 最后值 `5001`→5、`5002`→2、`9001`→5、`9002`→2 | Redis `task:goka:count:*` 同值 | ✅ |

⇒ **✅ 两侧最终可观测状态完全一致**（脚本：`tools/difftest/parity-task-kafka.py`，`EXIT=0`）。

### 三处**必须归一化**的差异（否则全是噪声）

1. **时间戳**：两侧是**两套独立进程**，写入时刻天然不同（实测差 4 秒）。
   Redis Hash 的 `createAt`/`updateAt`/`doneAt` 与奖励流的 `ts` ⇒ 只比「在不在」。
2. **奖励流顺序**：两侧都是**遍历 map**（Go `like_sink.go:270` `for uid, total := range batch`
   —— Go 的 map 序**随机化**；RAG 是 `HashMap` 序）⇒ 事件顺序**两侧都不是契约**，按集合比。
   实测 Go 先 1902 后 190，RAG 先 190 后 1902。
3. **`task:goka:count:*` 是 RAG 独有**：RAG 的 goka 计数在 **Redis**（DRIFT-TASK-RPC-001 的修复），
   Go 的在 **Kafka compacted table topic**（`task.rpc.consumer*-table`）⇒ 两侧 key 集合**必然不等**。
   ⇒ 这几个 key 单列，**拿 RAG 的值去对 Go 的 table topic**（真正该比的是计数值 —— 实测一致）。

### 怎么复现（全部已入仓，一条命令）

```bash
./tools/difftest/run-task-kafka-e2e.sh          # 全流程：编 Go + 编 RAG + 起基建 + 灌消息 + 比对 + 收摊
KEEP_UP=1 ./tools/difftest/run-task-kafka-e2e.sh # 跑完保留服务与容器，便于手查
```

| 文件 | 作用 |
|---|---|
| [`tools/difftest/run-task-kafka-e2e.sh`](../../../tools/difftest/run-task-kafka-e2e.sh) | **编排入口**（把本轮踩过的 6 个坑全部固化在注释里） |
| [`tools/difftest/docker-compose.task-kafka.yml`](../../../tools/difftest/docker-compose.task-kafka.yml) | task 域专用基建（postgres + redis + **kafka** KRaft） |
| [`tools/difftest/initdb-task.sql`](../../../tools/difftest/initdb-task.sql) | 补建 `task_rag_db`（`task_go_db` 由容器 `POSTGRES_DB` 建） |
| [`tools/difftest/parity-task-kafka.py`](../../../tools/difftest/parity-task-kafka.py) | 最终态比对（Redis 全 key + 奖励流 + PG 两表 + goka 计数值） |

⚠️ 脚本里固化了本轮实测出来的**几个反直觉点**，改动前先读它的注释：
① 容器内 Kafka CLI 必须用 **INTERNAL 监听器 `localhost:19092`**；
② 重置 Kafka 必须**重建容器**（删 topic 不删 `__consumer_offsets` 里的已提交位点）；
③ 还必须移走 goka 的**本地 LevelDB 缓存**；
④ 灌消息前必须等**两侧 12 个 consumer group 全部注册** ——
Go 的 kq 消费者 `Offset` 硬编码 `latest`（`initUserFilter.go:47`），
灌早了它会从队尾之后开始 ⇒ **一条都收不到**，
表现极具迷惑性（goka 三段全通、RAG 全通，只有 Go 的 `like:total` 为 0）。

### 🔴 这一轮顺带抓出并修掉了两条真缺陷

对拍不是「跑一遍确认一致」而已 —— 它**抓出了两个此前无人发现的致命缺陷**：
[DRIFT-TASK-RPC-004](#🔴-drift-task-rpc-004--redispipelines-把-go-的-pipeline-当成-multiexec-事务--整条消息消费链路是死的)（整条消息消费链路是死的）
与 [DRIFT-TASK-RPC-005](#🔴-drift-task-rpc-005--pipelinehset-吞掉-redis-错误而-go-是向上抛终态分叉)（吞/抛方向相反导致终态分叉）。

---

## 四·四、RedisPipelines 修复的取证链（探针 + 变异测试）

**为什么单独一节**：AGENTS.md §3.3 要求「机制生效」必须有**运行证据**，
且 §3.3 的变体要求「探针自己也要被验证」。本节把两样都摆出来。

### 探针（真 Redis，带阳性对照）

`/tmp/task-difftest2/probe/RedisPipelineProbe.java`（一次性取证脚本，不入仓）：

| 探针 | 内容 | 结果 |
|---|---|---|
| **P1 阳性对照** | 把**修复前的写法原样**跑一遍（`executePipelined` 内调 `operations.exec()`） | ✅ 确实抛，异常链 = `RedisPipelineException` → `RedisCommandExecutionException: ERR EXEC without MULTI`（与生产实测栈同源） |
| P2 | 修复后的 `executePipelined`，SETNX × 3 | ✅ `[true,true,true]`，元素类型 `java.lang.Boolean`；再来一次 `[false,false,false]`（SETNX 幂等）；TTL 已设 |
| P3 | HSET + EXPIRE 批量路径 | ✅ 字段与 TTL 都写入 |
| P4 | `execQuietly` 候选 | ✅ 不抛异常、数据真落地 |
| P5 | 批量性取证 | ✅ 逐条 `execute` **1574 ms** vs `executePipelined` **37 ms**（**~42x**）⇒ 确实是批量 |

⚠️ P1 的断言一开始写成「顶层 message 含 `EXEC without MULTI`」而**失败** ——
顶层是 Spring 包装的 `RedisPipelineException`，真正的原因在 **cause 链**里。
⇒ 必须沿 cause 链断言，否则阳性对照会变成「假失败」。

### 变异测试（证明守门测试不是假绿）

把 `RedisPipelines.exec` 故意退回 `return operations.exec();` 后重跑
`RedisPipelinesRealRedisGateTest`：**9 条里 5 条报错**（含两条端到端 sink 用例）：

```
execReturnsOrderedBooleans / execResultElementsArePlainBooleans /
execActuallyWritesKeysWithTtl / userSinkFlushOnceWritesEverything /
userSinkCompletesWhenTargetReached
```

⇒ 守门测试**确实能抓住**这个缺陷。恢复修复后 `tests=9 failures=0 skipped=0`。

### 守门测试落点

`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mq/RedisPipelinesRealRedisGateTest.java`
—— 真 Redis 容器（`redis:7-alpine`）+ 真实 `LettuceConnectionFactory`，9 条用例，
其中两条**直接驱动真实的 `UserLikeProgressSink`**（`onMessage` → `flushOnce`），
断言 `task:init:*` / `task:progress:*` / `like:total:*` / `task:done:*` 全部落地。

⚠️ 配套改了 `TaskDomainServiceTest` 的 3 处打桩点（`execute` → `executePipelined`）。
那 3 处**锁的是旧机制**（而旧机制正是 bug 本身），必须一起改；
但它们真正要守的契约（回填命中的 **key** 与 **TTL**）保持不变。

---

---

## 四·五、跨语言 gRPC 互调 + Kafka 畸形消息活体对拍（2026-10-24 实测）

**这一节关闭了此前两条「只有源码级对照」的缺口**（原 §七 待办第 1 条与 §五 证据边界第 2/3 条）。

### 四·五·一、gRPC **跨语言**互调：Go 客户端 → 两端服务端

**此前只覆盖了一个方向**：仓库里的
[`tools/difftest/GrpcTaskDualPortRunner.java`](../../../tools/difftest/GrpcTaskDualPortRunner.java)
是 **Java 客户端**打两侧（`RAG client → Go server` + `RAG client → RAG server`）
⇒ **缺**「Go 客户端 → RAG 服务端」这一方向。

**为什么这不是同一件事**：两个方向的互操作风险不同 ——
grpc-go 与 grpc-java 的 wire 行为（metadata / 压缩 / deadline / error 传播）、
go-zero 的 zrpc 客户端能不能把 RAG 端的错误码与描述**原样**带回来。

**做法**：在**冻结真源之外**写一个 Go 客户端
（[`tools/difftest/grpcxlang/main.go`](../../../tools/difftest/grpcxlang/main.go)），
用 `go.mod` 的 `replace` 引用真源**自己生成**的桩（`sea-try-go/service/task/rpc/pb`）
⇒ 真源**零写入**，而且用的是真源的桩（不是手抄的 proto）。
⚠️ 真源的 pb 包名是 goctl 的占位符 `package __` ⇒ import 必须带别名（真源自己也这么写）。

**结果（2026-10-24 实测，`EXIT=0`）**：

| 用例 | Go client → Go server | Go client → **RAG** server | 判定 |
|---|---|---|---|
| `uid=0` | `InvalidArgument` / `"user_id invalid"` | `InvalidArgument` / `"user_id invalid"` | ✅ code+description 逐字节一致 |
| `uid=-1` | 同上 | 同上 | ✅ |
| `uid=MinInt64` | 同上 | 同上 | ✅ |
| `uid=MaxInt64` | `OK` | `OK` | ✅ 载荷逐字段一致 |
| `uid=1` | `OK` | `OK` | ✅ |
| `uid=1001` | `OK` | `OK` | ✅ |
| `uid=1003` | `OK` | `OK` | ✅ |

⇒ **7/7 一致，`CODE_MISMATCH=0`、`DESCRIPTION_DIFF=0`、`载荷不一致=0`**。
⇒ 与 Java 版 runner 的 uid 矩阵、载荷渲染格式**完全同款** ⇒ 两份报告可直接并读。

**复现**：
```bash
./tools/difftest/run-task-grpc-xlang.sh     # 需两侧 task-rpc 已起
```

### 四·五·二、Kafka **畸形消息**活体对拍（五个用例）

**此前只有源码级对照**，而源码级对照有个致命盲区：
**「Go 声明的契约」与「Go 实际的行为」不是一回事** ——
本节就是为了把这类差异**量化**出来。

**做法**：往两侧**同一批**注入「坏消息」，比对可观察后果
（consumer group 的 committed offset + Redis 有没有写出副作用）。
脚本：[`tools/difftest/run-task-kafka-malformed.sh`](../../../tools/difftest/run-task-kafka-malformed.sh)。

| 用例 | 注入 | Go 期望（出处） | 实测（Go / RAG） | 判定 |
|---|---|---|---|---|
| **A** `uid <= 0` | filtered.User key=`0` | `return nil` ⇒ **提交位点**、跳过（`like_sink.go:87-89`） | 位点都推进 / `like:total:0` 都不存在 | ✅ |
| **B** `total <= 0` | filtered.User key=`6002` value=`0` | `return nil` ⇒ **提交位点**、跳过（`like_sink.go:97-99`） | 位点都推进 / `like:total:6002` 都不存在 | ✅ |
| **C** `key` 解析失败 | filtered.User key=`not-a-number` | `return err` ⇒ **不提交**（`like_sink.go:83-85`） | 两侧 committed 都**停在基线**（3 → 2s 后 3 → 10s 后 3） | ✅ |
| **D** `LogEvent` 解析失败 | in-topic value=`{not json` | `log.Println(err)` 后**照样 Emit** ⇒ 提交 + 转发（`initGoKa.go:63-69`） | in-topic 位点都推进；raw.User 各 **+1** 条、**key 为空串**、**value 是 raw 原文** | ✅ |
| **E** 毒丸后再来一条合法消息 | filtered.User key=`6003` value=`2` | 两侧都**继续处理下一条**，提交时**跨过**失败那条 | Go 基线 3 → **5**；RAG 基线 3 → **5**；`like:total:6003` 两侧都写出 | ✅ |

⇒ **五个用例两侧行为完全一致，`EXIT=0`**。

#### 🔴 本轮**推翻了一条写在本仓 javadoc 里的断言**（AGENTS.md §3.3.2 的高危项）

`TaskRpcApplication#noCommitErrorHandler` 的 javadoc 此前写着：

> Go 用的 segmentio/kafka-go Reader 带 `CommitInterval: 1s`（`go-queue/kq/queue.go:125,151`），
> 它会周期性自动提交已 fetch 的位点，因此 Go 那句「error ⇒ 不 CommitMessages」
> 在实践中**会被自动提交抵消**（约 1 秒后照样提交）。
> Java 侧 … 是**严格更强**的「失败不提交」。

**这条是错的**，两处独立证据：

1. **kafka-go 自己的文档**（segmentio/kafka-go@v0.4.47 `reader.go:811-812`）：
   > `FetchMessage does not commit offsets automatically when using consumer groups.
   > Use CommitMessages to commit the offset.`

   而 kq 用的**正是** `FetchMessage`（`go-queue/kq/queue.go:264`）
   ⇒ `CommitInterval` 的自动提交路径**根本不生效**。
2. **本节的活体实测**（用例 C）：注入 key 解析失败的消息后，
   **两侧**的 committed offset 都不推进（基线 3，2s 后 3，10s 后 3）
   ⇒ Go 侧**并没有**「被自动提交抵消」。

⇒ **两侧的真实差异只有一处**：失败的那一刻**都**不提交位点，
但**都**会继续处理下一条；下一条成功时提交会**跨过**失败那条
⇒ 失败那条实质被跳过（运行期不重投）。
⇒ 已把该 javadoc 改写为**实测证实的**说法（源码 + 活体双出处）。

#### ⚠️ 本节的副作用（必须知道再跑）

用例 C 会往 `filtered.User` 投「毒丸」（key 解析失败那条），
该分区的消费者会一直重投 ⇒ **跑完两侧栈都需要重启**才能恢复正常。
脚本结尾已写明；重启用 `./tools/difftest/run-task-http-parity.sh`（会重建容器与栈）。

#### ⚠️ 顺带修掉了编排脚本的一个真坑

`run-task-http-parity.sh` 此前 `pkill` 之后只 `sleep 2` ⇒
**JVM 的优雅关闭（Spring close + Hikari 关池 + Kafka 消费者下线）实测要 3~4 秒**
⇒ 新进程 `BindException: Address already in use` ⇒ 整轮对拍白跑，
而症状是「api 一直 503」，很容易误判成应用问题。
⇒ 已改成**轮询等端口释放 + 等端口开始监听**，且进程起不来时**立刻失败并打日志尾部**。

---

## 四·七、goka 表的「保留策略」到底怎么算（2026-10-24 实测）

> **这一节推翻本台账上一版的一条结论**（§六 曾写「goka 的表会随 Kafka retention 滚动」）。
> 结论变了，但**不是**「RAG 有缺陷要修」——恰恰相反：**RAG 现在的做法就是对齐的**。
> ⇒ 这也是本仓 §3.1「不要拿现有 RAG 代码或注释当对照基准」的另一个样本：
> **两边的台账都可能写错，只能靠实测定案。**

### 四·七·一、Go 侧 goka **不创建** topic —— 缺 topic 直接 `log.Fatal`

**这是本轮第一个实测结论**，它决定了「RAG 侧要不要也去管 topic 生命周期」这个问题。

| 步骤 | 出处 | 事实 |
|---|---|---|
| goka 准备 topic 的**第一步** | `goka@v1.1.16 processor.go:1012` → `ensureCopartitioned(tm, gg.copartitioned().Topics())` | 先检查**输入 topic 是否存在** |
| topic 不存在时 | `goka@v1.1.16 processor.go:1035-1040` `ensureCopartitioned` → `tm.Partitions(topic)` 失败即 `return 0, fmt.Errorf("Error fetching partitions for topic %s: %v", ...)` | **直接返回错误** |
| `prepareTopics` 因此中断 | `processor.go:1012-1014` | **`EnsureStreamExists` 根本没被调用** |
| `NewProcessor` 失败 | `initGoKa.go:138-145` | **`log.Fatal(err)` ⇒ 进程退出** |

**实测**（2026-10-24，真 Kafka 容器，干净 broker，**不预先建 topic**）：

```
2026/10/24 04:58:59 start task kafka filter
2026/10/24 04:58:59 Error fetching partitions for topic task.logs.raw: requested topic was not found
⇒ 进程退出（pgrep 查不到 go-task-rpc）
```

⇒ **Go 侧 goka 的「建 topic」代码路径是死的**：它要求**所有输入 topic 已被外部预先创建**，
自己只做「**校验**」（`EnsureStreamExists` 的 config 比对），且 config 不匹配时
**`handleConfigMismatch` 的 default 分支直接 `return nil`、连日志都不打**
（`topic_manager.go:158-167` ⇒ `MismatchBehavior=Ignore`）。

⇒ **两侧的 topic 生命周期都由部署侧（运维 / 脚本）负责，组件本身不管。**
⇒ ⇒ **RAG 侧「不建、不改 topic」是与 Go 一致的做法**；
本轮一度准备新增的「RAG 侧用 `KafkaAdmin` 建/校准 topic」**因此取消** ——
那会制造一条与 Go 的**结构性**不一致（Go 不建，RAG 建）。

> ⚠️ **附带发现的一条真实差异**（已登记为有意偏离）：
> Go 侧 **topic 缺失 ⇒ 进程 `log.Fatal` 退出**；RAG 侧 **topic 缺失 ⇒ listener 持续重试、
> 服务照常起**。见 [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md)。

### 四·七·二、`cleanup.policy=compact` **不按时间删** —— 决定性对照实验

**要回答的问题**：Go 的计数表有一份 Kafka compacted topic 副本
（`<group>-table`），它的 config 只设了 `cleanup.policy=compact`、**没有 `retention.ms`**
（`goka@v1.1.16 topic_manager.go:317-326`）。
那么 **compact 策略下 `retention.ms` 会不会把 key 删掉**？
（Kafka 的按时间删除是**按 segment 粒度**的 ⇒ 实验必须让 segment 先滚动。）

**实验**（真 Kafka KRaft，5 个 topic，每个写入都**先验证 `offset > 0`**）：

| topic | `cleanup.policy` | `retention.ms` | `segment.ms` | 45 秒后读回 | 说明 |
|---|---|---|---|---|---|
| `probe.y_short` | **compact** | **1000** | 1000 | **1 条（存活）** | ⭐ **核心**：compact 单独使用**不按时间删** |
| `probe.z_delete` | delete | 1000 | 1000 | **0 条（被删）** | ✅ **阳性对照**：同参数下 delete **确实**被删 ⇒ 机制有效、探针有区分力 |
| `probe.w_default` | delete | 默认 604800000 | 默认 | 1 条（存活） | ✅ 阳性对照 |
| `probe.x_infinite` | compact | -1（无限） | 1000 | 1 条（存活） | ✅ |
| `probe.v_noseg` | compact | 1000 | **默认（不滚动）** | 1 条（存活） | ✅ **segment 粒度对照**：证明 `y_short` 的存活不是因为 segment 没滚动 |

⇒ **`cleanup.policy=compact` 单独使用时，Kafka 不启用按时间删除** ⇒
Go 的 compacted table topic 的 key **不按时间过期**。

### 四·七·三、结论：RAG 的 `task:goka:count:*` **不设 TTL 才是对齐**

把两侧的**权威存储**摆在一起看：

| | Go | RAG |
|---|---|---|
| 权威的计数存储 | **本地 LevelDB**（`goka storage/builders.go:18-26` `leveldb.OpenFile(fp, nil)` ⇒ 默认**无 TTL**） | **Redis**（`TaskCountStore` 的 `INCR`） |
| 何时删 key | `PartitionTable.Delete`（`partition_table.go:622`）**在 task 域从未被调用**（两个 processor 只有 `ctx.Value()`/`ctx.SetValue()`，`initGoKa.go:75-81` / `:92-97`）⇒ **永不删** | **永不删**（无 TTL） |
| Kafka 副本 | compacted topic `<group>-table`，`cleanup.policy=compact`、无 `retention.ms` ⇒ **不按时间过期** | **不存在**（计数直接落 Redis） |
| 副本有人消费吗 | ❌ **全仓无任何读取点**（唯一写它的是 goka 自己） | — |

⇒ **Go 的计数永不丢失；RAG 的计数也永不丢失 ⇒ 等价。**
⇒ ⇒ **给 `task:goka:count:*` 加 TTL 会制造一条与 Go 的新漂移**，而且**造成业务错误**：
计数到期归零 ⇒ `like:total:<uid>` 是**普通 SET**（不是 GREATEST，
`UserLikeProgressSink.pipelineSetLikeTotal`）⇒ 被**更小的值覆盖**；
达标判定 `total >= target` 读的是计数器 ⇒ 用户得重新攒够次数。

⇒ **本轮的交付不是「加 TTL」，而是「把『不设 TTL』锁成契约」**：
新增真 Redis 守门测试
[`TaskCountStoreRetentionContractTest`](../../../../service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mq/TaskCountStoreRetentionContractTest.java)
（4 条，含阳性对照：先证明「读 TTL」这个探针能区分「有 TTL / 无 TTL」，
再断言计数 key 的 `TTL == -1`）⇒ **后人若好心加 TTL，测试立刻变红。**

#### ⚠️ 附带查明的一个 Kafka 事实（顺带解释了 Go 侧一个细节）

Kafka 的 **compacted topic 强制要求消息带 key**：

```
org.apache.kafka.common.InvalidRecordException:
  Compacted topic cannot accept message without key in topic partition probe.x_infinite-0
```

⇒ Go 侧 goka 一直带 `ctx.Key()` emit（`initGoKa.go:81` / `:106`）才写得进去；
⇒ 也从侧面印证 compacted topic 存的是「**key → 最新值**」，与 RAG「一个 key 一个值」同构。


---

## 四·八、goka `OffsetOldest` 活体验证：**查出并修掉一条真漂移**（2026-10-24 实测）

> 这一节关闭了本台账 §三 末条那条「只核到配置意图一致，**未做活体验证**」的缺口。
> ⚠️ **而且验证本身查出了一条真漂移** —— 台账上一版写的「与 Go 一致」**是错的**。
> 脚本：[`tools/difftest/run-task-offset-oldest.sh`](../../../tools/difftest/run-task-offset-oldest.sh)。

### 四·八·一、为什么必须换一种对拍顺序

⚠️ **本域此前所有 Kafka 对拍都是「先起消费者、后灌消息」**
（E2E、畸形消息、HTTP 双侧全是这个顺序）⇒
那种顺序下 `auto.offset.reset=earliest` 与 `=latest`
**没有任何可观察差别**（消息都产生在消费者 join **之后**）
⇒ **「earliest 与 Go 一致」这个结论在所有既有对拍里都是恒真的** —— 它**没被验证过**，只是**被顺序掩盖了**。

⇒ 本次改成的顺序：

```
阶段 B：① 建 topic → ② **预灌消息** → ③ 起消费者（全新 group ⇒ 无已提交位点）
阶段 C：④ 断言（earliest 正向 + latest 阴性对照 + 阳性对照）
阶段 D：⑤ **消费者就位之后**再投递 ⇒ 端到端断言（无竞态）
```

### 四·八·二、结果：**8 条断言全过，失败项 0**

| # | 观测点 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| A1 | Go `task.rpc.consumer61-table`（compacted table topic）key=`5001` 的最后值 | `5` | `5` | ✅ Go goka 用户路 **earliest 生效** |
| **A2** | **RAG Redis db1 `task:goka:count:rag.rpc.consumer61:5001`** | `5` | **`5`** | ✅ **RAG goka 用户路 earliest 生效**（修复前是 `<absent>`） |
| A3 | Go `task.rpc.consumer71-table` key=`9001` | `5` | `5` | ✅ Go 文章路 earliest 生效 |
| **A4** | **RAG Redis db1 `task:goka:count:rag.rpc.consumer71:9001`** | `5` | **`5`** | ✅ **RAG 文章路 earliest 生效**（修复前是 `<absent>`） |
| A5 | 两侧 `like:total:7777`（**预灌**在 filtered.User 的 kq 输入上） | **两侧都不存在** | Go `<absent>` / RAG `<absent>` | ✅ **阴性对照**：kq 的 `latest` 确实跳过了预灌消息 ⇒ 探针**有区分力** |
| A6 | 两侧 `like:total:5001`（端到端） | `10` | Go `10` / RAG `10` | ✅ **预灌 5 + 后灌 5 = 10** ⇒ **预灌那批确实被计入了** |
| A7 | 两侧 `like:total:5002` | `4` | Go `4` / RAG `4` | ✅ 同上（预灌 2 + 后灌 2） |
| A8 | 两侧 `like:total:8888`（**启动后**投递到 filtered.User） | `42` | Go `42` / RAG `42` | ✅ **阳性对照**：kq 路本身正常 ⇒ A5 的「不存在」不是因为 kq 坏了 |

⇒ **A6 的 `10` 是最有说服力的一条**：它是「**预灌的 5 条也被计入了**」的端到端铁证
—— 若 earliest 没生效，计数只会是 5。

⇒ **consumer group 位点**（阴性对照的直接证据）：

| group | CURRENT-OFFSET | LOG-END-OFFSET | 说明 |
|---|---|---|---|
| `task.rpc.consumer61`（Go） | **7** | 7 | ✅ 读了全部 7 条预灌消息 |
| `rag.rpc.consumer61`（RAG） | **7** | 7 | ✅ 同上（**修复前是 0**） |

### 四·八·三、🔴 查出的真漂移：per-listener 的 `auto-offset-reset` **从未生效**

**在「修复前」跑同一套脚本**，结果是 **A1/A3 PASS、A2/A4 FAIL**（`<absent>`）：

```
✓ PASS  [A1] Go goka 用户路 … 实测=5
✗ FAIL  [A2] RAG goka 用户路 … 实测=<absent>
✓ PASS  [A3] Go goka 文章路 … 实测=5
✗ FAIL  [A4] RAG goka 文章路 … 实测=<absent>
```

⇒ **Go 从队头读、RAG 从队尾读** ⇒ 行为分叉。
⇒ 根因与修法见 [DRIFT-TASK-RPC-006](#🔴-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)。

### 四·八·四、⚠️ 本脚本固化了两个「环境级」坑

1. **端口不能写死**：2026-10-24 实测，**user 域的并发会话**的 `go-user-rpc` 正占着 **19005**
   ⇒ 本脚本的 Go 侧直接 `bind: address already in use` 退出。
   ⇒ 脚本改为**自动挑空闲端口**（从 19005 / 59005 往后找），并生成一份改过 `ListenOn` 的 Go 配置副本。
   ⇒ ⚠️ **绝不 kill 别人的进程** —— 那是别的域的基建。
2. **compose 必须带 `-p taskprobe`**：见 §四·七·一 与
   [`docs/Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md) 的 `PITFALL-TASK-RPC-20`。

### 四·八·五、复现

```bash
./tools/difftest/run-task-offset-oldest.sh          # 完整跑一遍
KEEP_UP=1 ./tools/difftest/run-task-offset-oldest.sh # 跑完保留服务与容器
```

⚠️ 脚本会**重建 `taskprobe-*` 三个容器**（⇒ 该容器上的既有数据会被丢弃），
并**只**停自己起的两个进程（按 `$WORK` 目录路径匹配 ⇒ 不会误杀别的域的同名进程）。
