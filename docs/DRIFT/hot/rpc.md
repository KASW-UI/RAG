<!-- @move AGENTS.md L1392-L1443 · §0 hot-rpc 首次实现 -->

> 🟥 **2026-10-24 第十一轮后回填（本文件头部此前停在第一/第二轮口径）**：
> 本文件下半部分各轮的记录**一条未删**（历史快照价值），但**头部与若干跨轮表格**一度严重过期
> ——例如「剩余未闭合项集中在真 Redis / 真 Kafka 集成测试」「topic/group/offset 未做自动化断言」
> 「Redis keyspace 无 IT」这几条，在写下时全部为真，**到第十一轮已全部被推翻**。
> ⇒ 当前状态一律以**文件末尾「第十一轮」一节** + 本条回填为准。

**归属**：`align/hot` 工作树产出（`RAG.worktrees/hot`）；基线 = `do-my-own-gh` @ `8422116` 之后的分支点。
**核对日期**：2026-10-24（**第十一轮**：D1 双侧活体对拍闭合 + U7 闭合 + hot 域 U9 系统变异 25/25）
**当前证据强度**（按 AGENTS.md §3.3 口径 · 截至第十一轮）：

| 档 | 已取得 | 说明 |
|---|---|---|
| ✅ **双侧活体对拍（完整栈）** | **10 组 top_k 逐字节一致** | 真 Kafka（1 broker / 3 分区）+ **2 个真 Redis**（Go 23979 / RAG 23980，因两侧 key 硬编码）+ 真 gRPC（Go 23983 / RAG 23984）+ **自造栅栏**（两侧 group-id 不同 ⇒ 进度天然不对称）⇒ sha256 逐条相同 |
| ✅ **真 Redis IT** | **12 例**（`HotRedisLuaGateTest`） | 4 段 Lua 逐段行为、**两个 TTL**、`RENAME` 原子性、`EXISTS` 守卫、生产 `flush()` / `loadInto()` / 合并求和路径 |
| ✅ **真 Kafka IT** | **4 例**（`HotKafkaConsumerGateTest`） | drop 契约、位点推进、零重试 handler 的**实际效果**、RoundRobin 的**实际分配** |
| ✅ **装配层** | listener 真进容器 registry + group-id 实测 | `HotRpcSpringContextWiringTest` 断言容器**实际 groupId** = `article-hot-group`（Go `KqConsumerConf.Group`） |
| ✅ **变异验证** | hot-rpc **10/10** + hot-common **4/4** | 第十一轮系统变异；**查出并修补 2 处真实守门缺口**（合并 >100 截断层 / 桶冲突 uint32 衰减概率） |
| ✅ **契约测试** | **86 条**（基线 84 + 第十一轮新增 2） | 全绿 |

### ✅ `service/hot/hot-rpc`：已从「13 个 12 行空骨架」落地，Go ↔ Java **双侧对拍逐字节一致**（2026-10-22 晚）

> **§0「从未验证」清单里 `hot` 一项的第二块收口**（第一块是 `hot-common`）。
> ⚠️ 本次是**首次实现**，不是「已存在实现的审计」—— 接手时该模块 13 个 `.java` **全部只有 12 行 javadoc**，
> `mvn compile` 却产出 `.class` ⇒ **「编译绿」与「零行实现」可以并存**（§6.6.5 方法论的又一实例）。
> 详细推导见 **`FileTree/hot/rpc.md`**；完整落地记录见 **§6.8**。

#### 🔴 落地前先撞上两个「编译期就暴露、但此前无人发现」的硬阻塞

| # | 阻塞 | 为什么一直没被发现 |
|---|---|---|
| 1 | **`hot-proto` 的 pom 没有 `protobuf-maven-plugin`** ⇒ `hot.proto` **从未被编译成 Java**，`HotServiceGrpc` 根本不存在 | 13 个骨架类全部没有 `extends …ImplBase`，所以「不引用生成类」就能编译通过 |
| 2 | **Spring Boot 4 把 Kafka 自动配置整体搬出了 `spring-boot-autoconfigure`**（实测该 jar 内 Kafka 类数 = **0**），改由独立模块 `spring-boot-kafka` 提供；只引 `spring-kafka` ⇒ **没有 `ConsumerFactory` Bean、没有 `@KafkaListener` 后置处理器** | 空骨架里 `@KafkaListener` 所在的 `HotEventConsumer` 是 12 行，注解根本不存在 |

⚠️ 第 2 条是**全仓级别**的：`service/like/like-rpc/pom.xml:120` 同样只引了 `spring-kafka`，
**有同款问题**（本次未动，不在 scope）。⇒ 见 **§3.3.4**。

#### 🔴 Go 真源本身有 3 个缺陷（**不是 RAG 的漂移**，是「照抄会出事」）

| # | Go 位置 | 缺陷 | 本次处置 |
|---|---|---|---|
| **G-HOT-001** | `hot_handler.go:132` `hkMu.Lock()` → `:136` `syncToRedis` → `:196` `hkMu.RLock()` | Go `sync.RWMutex` **不可重入** ⇒ **第一次 rebalance 撤销分区即必然自死锁**，`defer Unlock` 永不执行，`delete` 从不执行，**分片快照从未落盘过** | **有意偏离**：改用 Go 另外两个调用点已有的「锁内取引用 → 锁外 Redis I/O」形态，**需签字** |
| **G-HOT-002** | `hot_handler.go:370` `scoreMap[z.Member.(string)] += …` | **裸类型断言**，member 非字符串直接 panic；发生在 `periodicMerge` goroutine，**不在 sarama 的 recover 范围内 ⇒ 崩进程**（同文件 `:275` 与 `get_hot_articles_logic.go:55` 都有 `, ok` 保护，三处不一致） | **有意偏离**：改为跳过 |
| **G-HOT-003** | `hot_handler.go:311` / `:408` `result, _ := …Int()` | Redis **脚本执行失败**也被报成「lock lost」，排障指向错误方向 | **照抄**（§15 不擅自修复 Go 行为），已在注释与测试里留痕 |

⚠️ **G-HOT-001 是本轮最重要的发现，且是「1:1 直译会把它悄悄抹掉」的那一类**：
Java 的 `ReentrantReadWriteLock` **允许**写锁持有者再取读锁 ⇒ 按 `FileTree/hot/rpc.md §12.5` 的规划直译，
Go 的死锁会变成「能正常跑」，两套实现行为不一致且**零告警**。
⇒ 这条**不能靠 review 结构发现**，只能靠「先复现 Go 的行为」再决定怎么写。

#### 验证：4 层（口径：**隔离跑**，非全仓）

| 层 | 手段 | 结果 |
|---|---|---|
| **双侧对拍** | `go run` 跑真源 `sea-try-go/service/hot/heavykeeper`（探针放 `/tmp`，**不入 Go 仓**）+ Java 探针同输入 | **逐字节一致**（含中文键 UTF-8、weight=0 早退、TopK 顺序与计数） |

> 🟥 **2026-10-23 复核：原结论修正为「在 width=100000 / 6 个 key 的无冲突口径下逐字节一致」，不可外推。**
> 该轮探针（`/tmp/hkparity/go/main.go` 原文留存）刻意取 `Width: 100000`，注释自陈
> 「width 取很大值避免桶冲突 ⇒ 不触发概率衰减分支」⇒ **它根本没有走到 `hash()` 影响结果的那条路**。
> 本轮用生产口径 `Width:1000 / Depth:5 / TopK:100` 灌 400 个 key 重做双侧对拍，
> **100 条榜单里 16 条与 Go 不同** ⇒ 见 **`DRIFT-HOTRPC-001`**。
> ⇒ 「逐字节一致」是**一个输入区间**的结论，不是组件级结论（AGENTS.md §0「勿凭本表下结论」的第 N 次复现）。
| **契约测试** | **46 条** / 8 个类（`HeavyKeeperTest` 5 / `HeavyKeeperUint32OverflowTest` 4 / `HotEventConsumerDropTest` 12 / `HotPartitionSketchRegistryDeadlockTest` 6 / `HotRevokeFlushOutsideLockTest` 1 / `HotRankingQueryServiceTest` 9 / `InteractionStubServiceTest` 4 / `HotServiceGrpcImplErrorMappingTest` 5） | 全绿 |
| **变异验证** | **10/10 全部变红**：无符号比较（`maxCount` / 最小堆比较器 ×2）、指标 Inc 与 hkMap 查找次序、`UNKNOWN→INTERNAL`、去掉 TopK 归一、`scoreToCount→round`、指标名漂移、revoke 不移除分区、落盘挪回写锁内、坏消息不再返回 error | 守门有效，**非假绿** |
| **编译** | `hot-proto` / `hot-common` / `hot-rpc` / `hot-api` 四模块 `install` | BUILD SUCCESS |

⚠️ **变异验证第一轮抓到一个真实 bug**（不是测试问题）：
`HeavyKeeper.hash()` 写 `h & 0xFFFFFFFFFFFFFFFFL` 想做无符号掩码，但该字面量位模式全 1（即 `-1L`），
**掩码等于什么都没做** ⇒ FNV 结果最高位为 1 时取模得负数 ⇒ `ArrayIndexOutOfBounds`。
⚠️ **它是被「永不外抛」契约吞掉的**：消费者把异常降级成返回值 ⇒ 表现为**事件静默丢弃、热榜永不更新、启动零报错**。
⇒ 这是 §3.3 那条纪律的加强版：**「异常被降级处理」会同时遮蔽下游 bug**。

> 🟥 **2026-10-23 复核：这条修复「只修了一半」，且修法本身是错的。**
> 原结论把它当作已闭环的 bug；本轮双侧对拍证明：改成
> `int remainder = (int)(h % width); return remainder < 0 ? remainder + width : remainder;`
> （`HeavyKeeper.java:261-262`）**在 `h < 0`（FNV-64 最高位为 1，约 **49.5%** 的 key）时给出错误的列号**：
> `(-a) mod w + w` 等于 `Euclidean(-a, w)`，而 Go 要的是 `Euclidean(2^64 - a, w)`，
> 两者差 `2^64 mod w`（w=1000 时是 616，w=7 时是 2，**永不为 0**）。
> ⇒ 不崩、不越界、**静默算错**，所以既有 46 条绿测试与 10/10 变异一个都抓不到。
> ⇒ 详见 **`DRIFT-HOTRPC-001`**（含实测：Top-100 里 16 条与 Go 不同）。
> ⚠️ 顺带修正：修复**不能**用 `Math.floorMod(h, width)` —— 本轮实测它与原写法结果**完全相同**（同样错），
> 唯一正确的是 `Long.remainderUnsigned(h, (long) width)`（实测 0/51 列号不一致、Top-100 集合完全一致）。

#### ⚠️ 四条边界（**不得越读成「hot 域已完成」**）

1. **真 Redis / 真 Kafka 的集成验证** —— ✅ **第四轮已补齐**（2026-10-23）：`HotRedisLuaGateTest`（12 例，真 `redis:7-alpine`）+ `HotKafkaConsumerGateTest`（4 例，真 `apache/kafka:3.7.2`）。
   ⚠️ **但覆盖仍有边界**：Go 侧 `MaxRetries=3` 的**实际重试面**（需 `CLIENT PAUSE` 制造半程失败）**仍未实测** ⇒ UNR-1 只闭合了 RAG 侧。
   ⚠️ IT 还**查出两条新问题**（都是台账原先写反 / 写漏的）：`EXISTS` 守卫语义被写反（DRIFT-HOTRPC-008）、容器并发度与 Go「每分区一个 goroutine」不等价（DRIFT-HOTRPC-009）。
2. **`/actuator/prometheus` 无暴露路径** —— 纯 gRPC 进程不起 web 容器（与 `user-rpc` 同款取舍 `management.server.port: -1`），而 Go 的 `prometheus.StartAgent` 在 19091/19092/19093 是**真起 HTTP** 的。L1 抓取端点暂缺。
3. **gRPC 端口未经分配** —— `Infrastructure.md §3` 只给 hot 预占 18897(HTTP)。本次暂沿用 Go 的 `8083`（8081=admin-rpc / 8082=message-rpc / 8083 空闲），走 `HOT_RPC_GRPC_PORT` 环境变量。
4. **`hot-api` 仍是 12 行空骨架**（13 个文件），`hot-rpc` 的事实**没有 HTTP 消费方**。

> 🟥 **2026-10-23 复核：第 2 条的推理链有一环被实测推翻，结论本身仍然成立但理由要换。**
> 「不起 web 容器」原本只是**推断**（pom 注释自陈「本服务无 WebApplicationType」）。
> 本轮用 `mvn dependency:tree` 实测：classpath 上**没有** `jakarta.servlet`、没有 `tomcat-embed-core`、
> `spring-boot-web-server:4.0.8` 只带 `spring-boot` + `spring-web` ⇒ `deduceWebApplicationType()` 必然得到 `NONE`
> ⇒ 结论成立，且现在是**实测**而非推断。Actuator 侧 `ManagementServer*` 全部 `@ConditionalOnWebApplication`
> ⇒ `management.server.port: -1` 在 NONE 下是**空配置**，不报错也不生效（不是「禁用了 management server」）。

<!-- @move AGENTS.md L3901-L4024 · §6.8 hot-rpc 首次实现 L1~L3 -->
### 6.8 `service/hot/hot-rpc` 首次实现：Go `rpc` + `heavykeeper` → Spring Boot（2026-10-22 晚）

> **性质与其他 §6.x 不同**：§6.6 / §6.7 都是「审计已存在的 RAG 实现」；
> 本节是**首次从零实现**一个 Component。接手时的状态是 **13 个 `.java` 全部只有 12 行 javadoc**
> （`public class X {}`），`target/classes` 里却已有 `.class` ⇒ **`mvn compile` 全绿而行为为零**。
> ⚠️ 这是 §6.6.5 那条方法论（「缺失的不是测试数量，而是让代码真正跑一次这个动作」）的**最强实例**：
> 一个模块可以「编译成功 + 有 class 文件 + 完全没有任何实现」。

#### 6.8.1 Go Component 的真实边界（先追踪，后落笔）

Go 侧范围：`service/hot/rpc/`（`hot.go` 75 行 + `etc/`×3 + `internal/{config,svc,server,logic×5,mqs×2,metrics}`）
\+ 强依赖 `service/hot/heavykeeper/heavykeeper.go`（210 行）。

**执行路径**（两条，互相独立）：

```
① 查询面：GetHotArticles → ZREVRANGE hot:articles 0 k-1 WITHSCORES → uint32(score)
② 消费面：sarama ConsumerGroup → TraceConsumer span → 3 类校验 → eventConsumeTotal.Inc
           → hk.Add → 每 100 条异步 syncToRedis → 30s periodicSync → 10s periodicMerge + 分布式锁
```

**依赖追踪的边界判断**：`KqPusher`（建了零调用）、`RedisConf.Type`（`svc` 只用 `Addr`+`Password`）、
`KqConsumerConf.Name`（hot 未读）、`metrics.InitMetrics`（全仓 grep 确认零调用点）
—— 四项**在 Go 侧本身就是死配置/死代码**，**刻意不迁**（不给死代码建结构）。
⚠️ 与 `hot-common` 那条「like-rpc 的 258 行 `LikeGrpcError` 是反面样本」同源判据。

#### 6.8.2 L1 外部契约（逐项对照，均有测试锁死）

| 契约 | Go 出处 | Java 落点 | 锁定测试 |
|---|---|---|---|
| 5 个 gRPC 方法 + proto | `hot.proto` / `hot_service_server.go` | `HotServiceGrpcImpl`（`@GrpcService`） | `HotServiceGrpcImplErrorMappingTest` |
| 3×`UNIMPLEMENTED` + 1×`success=true` | `like:44` / `coin:36` / `share:36` / `comment:30` | `InteractionStubService` | `InteractionStubServiceTest`（含「恰好 3:1」的计数断言） |
| **裸 error → `UNKNOWN`** | `get_hot_articles_logic.go:50` 的 `return nil, err` | `invoke()` 的兜底分支 | 同上（`redisFailureMapsToUnknownNotInternal`） |
| `TopK` 归一 `≤0→20` / `>100→100` | `:35-40` | `HotRankingQueryService` | `HotRankingQueryServiceTest`（verify 读取范围 `0,19` / `0,99`） |
| `uint32(z.Score)` **向零截断** | `:62` | `HeavyKeeper.scoreToCount` | `HeavyKeeperUint32OverflowTest` |
| `redis.Nil` → 空列表不报错 | `:45-48` | Spring 返空集合，天然等价 | `missingKeyReturnsEmptyListWithoutError` |
| topic / group / offset | `hot.yaml`（三份一致：`Topic: article-hot-events` / `Group: article-hot-group`） | `spring.kafka.*`（`hot-rpc.kafka.topic: article-hot-events` / `spring.kafka.consumer.group-id: article-hot-group` / `auto-offset-reset: latest`） | 🟥 **已补断言（第十一轮）**：**group-id** 由 `HotRpcSpringContextWiringTest` 断言容器**实际 groupId** = `article-hot-group`（不是断言配置项字符串）；**offset**（Go `sarama.OffsetNewest` ⇒ `latest`）由 `HotKafkaConsumerGateTest` 的真实 Kafka 行为覆盖；**topic** 由第十一轮 D1 双侧活体对拍覆盖（两侧消费**同一个** `article-hot-events` topic 的同一批消息，消息被两侧都消费到 ⇒ topic 字面量若不一致则 RAG 侧收不到） |
| Redis keyspace 5 个 + 两个 TTL | `hot_handler.go:208,209,390,25` | `HotRedisSnapshotStore` / `HotMergeScheduler` | 🟥 **已补 IT（第四轮）**：`HotRedisLuaGateTest`（**12 例，真 Redis**）覆盖 4 段 Lua 逐段行为、`RENAME` 原子性、`EXISTS` 守卫、**两个 TTL 不同**（分片 300 硬编码 vs 大 key = `Interaction.TTL`）、生产 `flush()` / `loadInto()` / 合并求和路径 ⇒ 原文「无 IT」**已过期** |
| 指标名 `article_hot_{kafka_consume,redis_sync}_total` | `hot_handler.go:30-45` | `HotRpcMetrics` | 变异 M9（改名字即 3 条红） |

⚠️ **`UNKNOWN` 不是 `INTERNAL`，这条极易被 message-rpc 的模板带偏**：
message-rpc 的兜底统一 `INTERNAL`，因为它把业务错误预先包成了 `StatusRuntimeException`；
hot-rpc 的 `GetHotArticles` 返回的是**裸 error** ⇒ grpc-go `status.FromError` 返回 `ok=false` ⇒ `UNKNOWN`。

#### 6.8.3 L2 业务语义

| 语义 | Go 出处 | 说明 |
|---|---|---|
| **指标 Inc 早于 `hkMap` 查找** | `:168` → `:171` | 分区未持有时**仍然计数**，只是不入 sketch。⚠️ 变异 M4 证明这条被锁死 |
| 无条件 `MarkMessage` ⇒ 坏消息永久 drop | `:108` | listener 永不外抛 + `DefaultErrorHandler` 零重试 + String payload 手动解析（三重） |
| 全局计数器 `% syncEvery`（**非每分区**） | `:179` | 分区越多单分区快照越稀疏 |
| 权重按 **`Name`** 建 map，`Code` 丢弃 | `hot.go:47-50` | `code` 字段**保留在配置里**但不参与分发（`hot2/hot3.yaml` 的唯一结构差异就是它的缺失） |
| 回灌是**新估计**不是原值 | `:279` | sketch 桶计数与真实次数无逆关系 |
| 合并是**有损的二次有损** | `:356-415` | 每片 Top-100 之和再取 100 ⇒ 每片都排 101 名的文章永不上榜 |

#### 6.8.4 L3 行为相关技术语义（本组件**无 DB / 无事务 / 无鉴权**）

| 项 | 结论 |
|---|---|
| MQ 失败处理 | drop 而非 retry（理由见 6.8.3） |
| 分区分配 | `RoundRobinAssignor`（Go `NewBalanceStrategyRoundRobin`）；⚠️ Spring 默认是 `RangeAssignor`，**改了会改变分区归属进而改变 `hkMap` 布局** |
| gRPC deadline | `observability.rpc-timeout: 2s`（Go `zrpc` 默认 2000ms，`DisableNativeRpcTimeout` 转移给观测拦截器） |
| concurrency | Go 的 `go func()` 扇出 → Spring `TaskExecutor`（`applicationTaskExecutor`）；**锁策略见 6.8.5** |
| error propagation | 校验失败 → 作为 span error 记录 → 在 listener 层丢弃 |

#### 6.8.5 🔴 一处**有意偏离 Go**：撤销路径的锁升级（G-HOT-001）

Go 形态：`Lock()` → 内部函数 `RLock()` ⇒ `sync.RWMutex` 不可重入 ⇒ **必然自死锁**（已用最小 Go 程序复现：
`revoke` 打印「write lock held」后再无输出，随后 `consumeMessage` 的 `RLock` 也拿不到 ⇒ 整个 `HotHandler` 永久冻结）。

**为什么不能照抄**：Java 的 `ReentrantReadWriteLock` **允许**写锁持有者再取读锁
⇒ 1:1 直译会把死锁**悄悄变成能跑**，两套行为不一致且零告警；而刻意在 Java 里写死锁会让 L2 语义（rebalance 落盘）彻底落空。

**采用**：Go 另外两个调用点（`periodicSync:250-255`、`consumeMessage:170-172`）已有的正确形态 ——
**锁内取引用 → 锁外做 Redis I/O**。`HotPartitionSketchRegistry.detach` 在写锁内原子移除并返回引用，
由调用方在锁外落盘。

⚠️ **该偏离的可验证形式不是「会不会卡住」**（Java 锁允许降级，卡不住），
**而是「锁的持有时长」**：用 latch 卡住 `flush` 制造慢 Redis，断言读者仍能立即返回
⇒ `HotRevokeFlushOutsideLockTest`（变异 M8' 证明：落盘挪回写锁内即变红）。

⚠️ **本条需用户签字**；若要求「连缺陷一起复刻」，改回 `Lock` 内落盘即可，但那样 L2 的 rebalance 落盘语义不再成立。

#### 6.8.6 证据边界（**五条，不得越读**）

1. **`hot-proto` 的 protoc 插件是本次新加的** —— 此前 `hot.proto` 从未被编译成 Java，`HotServiceGrpc` 从未存在。
2. **探针不入 Go 仓** —— 对拍探针在 `/tmp`（`go.mod` 用 `replace` 指向真源），Go 仓零改动。
3. **隔离跑，非全仓口径** —— §〇 已警告本机有并发 Maven；本节数字均来自
   `mvn -o -pl service/hot/{hot-proto,hot-common,hot-rpc,hot-api} -am install`。
4. **`like-rpc` 有同款缺依赖问题**（只引 `spring-kafka`、无 `spring-boot-kafka`），**本次未动**。
5. **真 Redis / 真 Kafka IT** —— ✅ **第四轮补齐**（2026-10-23）：真 `redis:7-alpine` + 真 `apache/kafka:3.7.2`，
   `HotRedisLuaGateTest`（12 例）+ `HotKafkaConsumerGateTest`（4 例），全部默认跑、不 skip。
   ⚠️ 本条边界**不再适用于第四轮**；第四轮的剩余边界见 §9 的 UNR-1（Go 侧重试面）与新增的 DRIFT-HOTRPC-008 / -009。

#### 6.8.7 本轮确立的通用纪律

**① 「注解存在」≠「机制生效」，Boot 4 的模块化迁移让这一点更隐蔽（⇒ 已写入 §3.3.4）**
`@KafkaListener` 在缺 `spring-boot-kafka` 时被**完全静默忽略**：不报错、不启动失败、容器照常起来。
这与 §3.3 已有的「`@Component` ≠ 自动注册」同型，但**更难发现**——后者至少类 Bean 不存在，
前者连类都不存在。⇒ **凡用 `@KafkaListener`，必须有一条「listener 真的在容器 registry 里」的测试。**

**② 「异常被降级处理」会同时遮蔽下游 bug**
`HotEventConsumer` 必须吞掉一切（drop 契约），而正是这条契约把 `HeavyKeeper.hash()` 的
`ArrayIndexOutOfBounds` 变成了「事件静默丢弃 + 热榜永不更新 + 启动零报错」。
⇒ **凡实现「永不外抛」的入口，必须同时给被调方配一条能暴露异常的独立测试**
（本轮靠 `HeavyKeeperTest` / `HeavyKeeperUint32OverflowTest` 达成）。

**③ 变异验证第一轮会有假绿，必须逐条归因**（10 个变异里 3 个首轮未变红）：

| 未捕获 | 归因 | 处置 |
|---|---|---|
| 最小堆比较器改有符号 | **测试设计缺陷**：最终闸门走的是**直接**无符号比较，绕过了比较器 | 重设输入（TopK=2 + 权重落在两值之间），round 3 已捕获 |
| 坏消息不再返回 error | 变异打在了**错误的层**（内部 barrier 拆不掉） | 顺带发现**真实语义漂移**：span 被误标成成功 ⇒ 重构 + 补断言 |
| 落盘挪回锁内 | Java 锁允许降级 ⇒ **结构上不可能触发死锁** | 改用「锁持有时长」作为判据（见 6.8.5） |

⇒ **「变异未变红」本身是一条结论**，要么测试设计错、要么该变异在本语言下不可达，
**不能默认当作「守门有效」**。

**④ `uint32` 移植到 Java 的两个必查点**（本组件唯一的有损算法，两处都踩过）：
① **加/减**两语言都自然回绕（两补码）⇒ 不会错；② **比较**必须是 `Integer.compareUnsigned`
/ `Integer.toUnsignedLong` ⇒ 写 `>` / `<` 就与 Go 分岔。
⚠️ 同理 `long` 的无符号取模**不能**写 `h & 0xFFFFFFFFFFFFFFFFL`（该字面量位模式全 1，即 `-1L`，掩码无效）。

---

<!-- ====================================================================================
     ↓↓↓ 以下为 2026-10-23「Go ↔ Spring Boot 语义一致性验证」轮次新增（spec §31-b 结构）
     ↑ 上方 L1–L203 是 2026-10-22 首次实现的历史记录，一条未删未改（仅就地加了复核批注）
     ==================================================================================== -->

# `hot-rpc` 语义对照台账

> **范围**：Go `service/hot/rpc/`（+ 强依赖 `service/hot/heavykeeper/`） ↔ RAG `service/hot/hot-rpc/`。
> **核对日期**：2026-10-23（第二轮验证；第一轮 = 2026-10-22 首次实现记录，见本文件上半部分）。
> ⚠️ **本行的「核对日期」只覆盖第二轮** —— 后续第三~六轮与第十一轮的结论见本文件对应章节与文件头部快照。
> **方法论**：`.agents/specs/semantic-verification-prompt.md` 全部 40 章 + 本项目 8 条硬约束。
> **只读边界**：本轮**未修改任何源码**，只改本文件与 `docs/Pitfalls/hot/rpc.md`。
> 探针全部落在 `/tmp/hk-verify-20261023/`（Go 侧 `go.mod` 用 `replace` 指向真源，**Go 仓零改动**）。

## 证据强度声明（本文件每条结论都带其中一个）

| 标记 | 含义 | 本轮是否取得 |
|---|---|---|
| 【双侧活体对拍】 | Go 真源与 RAG **两侧同时运行**、同输入比对输出 | ✅ 是（HeavyKeeper 8 组输入） |
| 【RAG 运行证据】 | RAG 进程**真的启动过**，日志 / 运行时对象为证 | ✅ 是（含一次**启动失败**，见 DRIFT-HOTRPC-002） |
| 【静态源码 + 依赖树】 | 只读源码 + `mvn dependency:tree` + jar/源码反查，**未运行** | ✅ 是 |
| 【纯 Mockito / 手 new 单测】 | 单测级证据，**不装配 Spring 上下文、不执行 SQL/Kafka/Redis** | ✅ 是（既有 46 条全部属此类） |
| 【真 Redis / 真 Kafka IT】 | 真实中间件集成测试 | ✅ **是**（第四轮取得：真 Redis 12 例 + 真 Kafka 4 例，全部默认跑、均配变异验证） |

⚠️ **第一～三轮没有取得任何一条【真 Redis / 真 Kafka IT】**；
**第四轮（2026-10-23）已取得**，证据形态：
- `HotRedisLuaGateTest`（12 例，真 `redis:7-alpine`）：4 段 Lua 逐段行为、两个 TTL、`RENAME` 原子性、
  `EXISTS` 守卫、生产 `flush()` / `loadInto()` / 合并求和路径；
- `HotKafkaConsumerGateTest`（4 例，真 `apache/kafka:3.7.2`）：drop 契约、位点推进、
  零重试 handler 的**实际效果**、RoundRobin 的**实际分配**；
- 两条 IT 均配**变异验证**（改回错法必须变红），详见 §9 与 `docs/Pitfalls/hot/rpc.md`
  P-HOTRPC-09 / P-HOTRPC-10。

---

## 1. Component Contract（§4 + §5）

| 项 | Go（真源） | RAG | 证据 |
|---|---|---|---|
| Purpose | 消费 Kafka 互动事件 → HeavyKeeper 有损 Top-K sketch → 周期落 Redis 分片 → 跨分片合并出 `hot:articles` → gRPC 暴露 Top-N 查询 | 同 | 静态源码 |
| Public Operations | 5 个 gRPC：`GetHotArticles` / `LikeArticle` / `CommentArticle` / `CoinArticle` / `ShareArticle` | 同（`HotServiceGrpcImpl`） | proto diff 逐条一致 + 静态源码 |
| Inputs | `int32 top_k`；Kafka value = `{"article_id","type",…}` | 同 | proto / 静态 |
| Outputs | `repeated HotArticleItem{article_id, uint32 hot_score}`；4 个互动方法 3×`UNIMPLEMENTED` + 1×`success=true` | 同 | 静态 + 纯单测 |
| Dependencies | Redis（读+写+Lua）、Kafka consumer group、etcd/zrpc、Prometheus、OTel | Redis(Lettuce)、Kafka(spring-kafka + **spring-boot-kafka**)、Nacos、Micrometer、OTel | dependency:tree |
| State | 进程内：`hkMap[int]*HeavyKeeper`、`counter atomic.Int64`、`lockCancel`；进程外：Redis 5 个 key | 同构（`HotPartitionSketchRegistry` / `AtomicLong` / 世代号） | 静态源码 |
| Side Effects | 写 5 个 Redis key；推进 Kafka 位点；注册 etcd；起 19091/19092/19093 指标 HTTP | 写同 5 个 key；推进位点；注册 Nacos；**指标无暴露端点**（既有边界 #2） | 静态 + 实测依赖树 |
| Failure Model | 消费端**永不外抛**、坏消息永久 drop；Redis 失败只 `Inc("fail")` 后 return | 逐条照抄且有测试锁死 | 纯单测 + 静态 |
| 🆕 **组件当前是否可运行** | ✅ `go run` 可跑 | ❌ **启动即失败**（缺 `ObjectMapper` Bean） | 【RAG 运行证据】 |

**一句话契约**：`hot-rpc` 是一份**统计型、有损、最终一致**的读模型。
它**没有事务、没有鉴权、没有 DB、没有 HTTP**；全部行为落在「Redis keyspace + Kafka 位点 + gRPC 响应」三处可观察面上。

---

## 2. GO-SEMANTIC-MODEL（§7 / §33）

### OP-G1 `GetHotArticles`
- **Input**：`in.TopK`（proto `int32`，可为负）
- **Processing**：`:34-40` 归一（`k<=0→20`，`k>100→100`）→ `:43` `ZREVRANGE hot:articles 0 k-1 WITHSCORES`
- **Branch**：`:45` `err == redis.Nil` ⇒ 返回**空 items、不报错**；`:50` 其它 err ⇒ **裸 error 上抛**
- **Result**：`:55-59` member 非 string 跳过；`:62` `uint32(z.Score)` **向零截断**
- **External**：Redis 单命令 ZREVRANGE
- **Error**：裸 `error` ⇒ grpc-go `status.FromError` 返回 `ok=false` ⇒ **`codes.Unknown`**
- **Timeout / Retry / Concurrency**：无本地状态；Redis 超时/重试见 §6.6 / §6.7；
  `zrpc` 侧 2s deadline 由观测拦截器施加（`hot.go:32,42`）
- **Evidence**：`get_hot_articles_logic.go:34,38,43,45-48,50,55-59,62`

### OP-G2 `LikeArticle` / `CoinArticle` / `ShareArticle`
- `status.Error(codes.Unimplemented, "<X> not implemented yet")`，request 全部忽略
- `like_article_logic.go:44` / `coin_article_logic.go:36` / `share_article_logic.go:36`

### OP-G3 `CommentArticle`
- `return &pb.CommentArticleResponse{Success: true}, nil` —— **唯一不对称的一个**
- `comment_article_logic.go:30`

### OP-G4 消费面 `ConsumeClaim → consumeMessage`
1. `TraceConsumer(span)` 包住（`:105`）
2. `json.Unmarshal`（`:152`）失败 ⇒ 返回 err
3. `ArticleID == ""`（`:157`）⇒ 返回 err
4. `weights[type]` 缺失或 `<=0`（`:162`）⇒ 返回 err
5. **`:168` `eventConsumeTotal.Inc(type)`**
6. **`:170-172` `RLock` 查 `hkMap`**；不存在 ⇒ `:174` **静默 return nil**
7. `:177` `hk.Add(articleID, weight)`
8. `:179` **全局** `counter % syncEvery == 0` ⇒ `:180` `go syncToRedis(...)`
9. `:108` **无条件** `MarkMessage`（在 `TraceConsumer` 返回之后、不看 err）

### OP-G5 分区生命周期
- `Setup → OnPartitionsAssigned`（`:113-126`）：持写锁；已存在 `continue`；建 sketch 后**持锁** `loadFromRedis`
- `Cleanup → OnPartitionsRevoked`（`:128-140`）：**持写锁 → 调 `syncToRedis`（内部 RLock）⇒ 必然自死锁**（G-HOT-001）

### OP-G6 快照 / 合并
- `syncToRedis`（`:195-242`）：`TopK` 空则不写 → pipeline `DEL shadow` + `ZADD shadow` → Lua `EXISTS` 守卫 + `RENAME` + **`EXPIRE 300`（硬编码）**
- `loadFromRedis`（`:265-283`）：`ZREVRANGE p{i} 0 -1`，score **当新权重回灌**（二次有损）
- `periodicSync`（`:244-263`）：**30s** ticker，每分区一个 goroutine
- `periodicMerge`（`:338-354`）：**10s** ticker；`SETNX lock TTL 10s` → 3s 看门狗续期 → 合并 → compare-and-`DEL`
- `mergePartitions`（`:356-415`）：pipeline 读 `p0..p{N-1}` 各 top-1000（`0..999`）→ 按 member 求和 →
  `sort.Slice` → 截断 100 → 写 shadow → Lua `GET "hot:merge:lock"` 比对 instanceID → `RENAME` → `EXPIRE TTL(3600)`
- **两个 goroutine 互相独立**（`:76-77`），Go 调度器天然并行

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（§7 / §34）

结构与 §2 **逐字段对齐**（这是 spec §34 的目的：不因两边文档结构不同而漏维度）。

| OP | Java 落点 | 关键行 |
|---|---|---|
| OP-G1 | `HotRankingQueryService.getHotArticles` + `HotServiceGrpcImpl.invoke` | `HotRankingQueryService.java:59-91`、`HotServiceGrpcImpl.java:109-123` |
| OP-G2/G3 | `InteractionStubService` | `InteractionStubService.java:42-64` |
| OP-G4 | `HotEventConsumer.onMessage → consumeMessage` | `HotEventConsumer.java:100-196` |
| OP-G5 | `HotKafkaConfig.rebalanceListener` + `HotEventConsumer.onPartitionsAssigned/Revoked` + `HotPartitionSketchRegistry` | `HotKafkaConfig.java:93-115`、`HotPartitionSketchRegistry.java:86-128` |
| OP-G6 | `HotRedisSnapshotStore`（快照）+ `HotMergeScheduler`（合并） | `HotRedisSnapshotStore.java:122-237`、`HotMergeScheduler.java:146-288` |

**与 Go 的结构性对应与偏离**

| 项 | Go | Java | 判定 |
|---|---|---|---|
| 消费入口 | sarama `ConsumeClaim` 循环 | `@KafkaListener`（String payload + 手动 `ObjectMapper`） | 实现差异 |
| 位点 | `:108` 无条件 `MarkMessage` | `enable-auto-commit:false` + `ack-mode:record` + `DefaultErrorHandler(FixedBackOff(0,0))` + `setAckAfterHandle(true)` + listener 永不外抛 | 语义等价，**提交节奏不同**（DRIFT-HOTRPC-005） |
| 分区分配 | `sarama.NewBalanceStrategyRoundRobin()`（`hot.go:57`） | `partition.assignment.strategy=RoundRobinAssignor`（`application.yml:49`） | 等价【**真 Kafka IT**：3 分区 2 消费者 ⇒ 实测 `[0,2]/[1]`；变异改 Range ⇒ 变红】 |
| 分区回调 | `Setup / Cleanup` | `ConsumerRebalanceListener`（`HotKafkaConfig.java:72`） | 等价 |
| 撤销落盘 | 持写锁再取读锁（**自死锁**，G-HOT-001） | `detach()` 锁内摘引用 + 锁外 `flush` | **有意偏离**，见 §7 SD-1 |
| 定时任务 | 两条独立 goroutine（`:76-77`） | 两条 `@Scheduled`，**共用单线程调度器** | **DRIFT-HOTRPC-003** |
| Redis 客户端 | go-redis（**MaxRetries 默认 3**） | Lettuce（**无重试**） | **DRIFT-HOTRPC-004** |
| panic | claim goroutine **无 recover** ⇒ 崩进程 | `catch (RuntimeException)` ⇒ 降级 drop | **DRIFT-HOTRPC-006** |
| 🔴 Bean 装配 | — | `ObjectMapper` Bean 缺失 ⇒ **上下文起不来** | **DRIFT-HOTRPC-002** |
| 🔴 sketch 桶位 | `hash()` = `uint64 % width` | `hash()` 无符号取模仿真**写错** | **DRIFT-HOTRPC-001** |

---

## 4. Equivalence Matrix（§24 + §35）

`Result` 只用 5 个枚举值（§35 规定）。

| # | Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|---|
| 1 | GetHotArticles | TopK 归一 `<=0→20` | `:35-37` | `HotRankingQueryService.java:61-63` | EQUIVALENT | 纯 Mockito 单测 |
| 2 | GetHotArticles | TopK 归一 `>100→100` | `:38-40` | `:64-66` | EQUIVALENT | 纯 Mockito 单测 |
| 3 | GetHotArticles | 读取 key 与范围 | `:43` | `:69` | EQUIVALENT | 纯 Mockito 单测 |
| 4 | GetHotArticles | key 不存在 → 空列表不报错 | `:45-48` | `:70-72` | EQUIVALENT | 纯 Mockito 单测 |
| 5 | GetHotArticles | 其它 Redis 错误 → 裸 error → **UNKNOWN** | `:50` | `HotServiceGrpcImpl.java:118` | EQUIVALENT | 纯 Mockito 单测 |
| 6 | GetHotArticles | `hot_score` 向零截断 | `:62` | `HeavyKeeper.java:276-278` | EQUIVALENT | 纯 Mockito 单测 |
| 7 | GetHotArticles | member 非字符串跳过 | `:55-59` | `:77-81` | EQUIVALENT | 纯 Mockito 单测 |
| 8 | GetHotArticles | **端到端可被调用** | 服务在跑 | **进程起不来** | SEMANTIC MISMATCH | 【RAG 运行证据】→ DRIFT-HOTRPC-002 |
| 9 | Like/Coin/Share | `UNIMPLEMENTED` + 描述逐字 | `like:44`/`coin:36`/`share:36` | `InteractionStubService.java:43,48,53` | EQUIVALENT | 纯 Mockito 单测 |
| 10 | CommentArticle | `success=true`（不对称） | `comment:30` | `:63` | EQUIVALENT | 纯 Mockito 单测 |
| 11 | Consume | JSON 解析失败 → drop、位点推进 | `:152-155` + `:108` | `:156-159` + `:97-128` | EQUIVALENT | 纯 Mockito 单测（**无容器**） |
| 12 | Consume | `article_id` 空 → drop | `:157-160` | `:161-164` | EQUIVALENT | 纯 Mockito 单测 |
| 13 | Consume | 未知 / 零权重 type → drop | `:162-166` | `:166-172` | EQUIVALENT | 纯 Mockito 单测 |
| 14 | Consume | **指标 Inc 早于 hkMap 查找** | `:168` → `:170` | `:175` → `:177` | EQUIVALENT | 纯 Mockito 单测 + 变异 M4 |
| 15 | Consume | 未知字段被忽略 | `encoding/json` 默认 | `@JsonIgnoreProperties`（`ArticleHotEvent.java:22`） | EQUIVALENT | 纯 Mockito 单测 |
| 16 | Consume | 全局（非每分区）`% syncEvery` | `:179` | `:185-189` | EQUIVALENT | 静态源码 |
| 17 | Consume | span 把三类坏消息标成 error | `:105` `TraceConsumer` | `:115-127` + `MqConsumer.java:110-117` | EQUIVALENT | 纯 Mockito 单测 |
| 18 | Consume | 单条 >100ms 慢日志 | `:143-149` | `:57,191-194` | EQUIVALENT | 静态源码 |
| 19 | Consume | 消费 panic 的后果 | **claim goroutine 无 recover ⇒ 崩进程** | 吞掉并 drop | OBSERVABLE DIFFERENCE | 【静态源码 + sarama 源码】→ DRIFT-HOTRPC-006 |
| 20 | Consume | **listener 真进容器 registry** | — | **无装配层测试** | UNKNOWN | 【静态源码】→ DRIFT-HOTRPC-007 |
| 21 | 分区 | assign 幂等、不重复回灌 | `:118-120` | `HotPartitionSketchRegistry.java:91-93` | EQUIVALENT | 纯 Mockito 单测 |
| 22 | 分区 | revoke 后不再持有 | `:137`（死锁，永不执行） | `:118` | OBSERVABLE DIFFERENCE | 有意偏离 SD-1；纯 Mockito 单测 |
| 23 | 分区 | 撤销时锁的持有时长 | 永久持锁（自死锁） | 锁内只摘引用 | OBSERVABLE DIFFERENCE | 有意偏离 SD-1；纯 Mockito 单测（`HotRevokeFlushOutsideLockTest`） |
| 24 | 快照 | 分片 key 模板 | `:208-209` | `HotRedisSnapshotStore.java:54,57` | EQUIVALENT | 静态源码 |
| 25 | 快照 | `EXISTS` 守卫（查 **`KEYS[1]=shadow`** ⇒ **首次快照不会丢**） | `:228-230` | `:75-77` | EQUIVALENT | **真 Redis IT**（`HotRedisLuaGateTest#firstSnapshotIsNotLost`）⚠️ **本行原写「首次快照必丢」，是台账写反了** ⇒ DRIFT-HOTRPC-008 |
| 26 | 快照 | 分片 TTL **硬编码 300**（≠ 配置 TTL） | `:232` | `:63,79` | EQUIVALENT | **真 Redis IT**（实测 TTL=300；变异 300→3600 ⇒ 2 例变红） |
| 27 | 快照 | 大 key TTL = 配置 `Interaction.TTL` | `:410` | `HotMergeScheduler.java:279` | EQUIVALENT | **真 Redis IT**（实测 TTL=3600） |
| 28 | 快照 | `TopK` 空则不写（旧 key 陈旧） | `:204-206` | `:127-130` | EQUIVALENT | 静态源码 |
| 29 | 快照 | 失败只 `Inc("fail")` 不抛 | `:220-224,236-241` | `:151-155,161-171` | EQUIVALENT | 静态源码 |
| 30 | 快照 | 周期 **30s** | `:245` | `HotRedisSnapshotStore.java:66,230` | EQUIVALENT | 静态源码 |
| 31 | 回灌 | `ZREVRANGE p{i} 0 -1`，score 当新权重 | `:268-279` | `HotRedisSnapshotStore.java:195-216` | EQUIVALENT | 静态源码 |
| 32 | 合并 | 每片读 `0..999`（1000 条） | `:362` | `HotMergeScheduler.java:70,248` | EQUIVALENT | 静态源码 |
| 33 | 合并 | 按 member 求和、排序、截断 100 | `:366-385` | `:264-268` | EQUIVALENT | 静态源码 |
| 34 | 合并 | 空结果直接 return（连 shadow 都不写） | `:386-388` | `:269-272` | EQUIVALENT | 静态源码 |
| 35 | 合并 | Lua 用**字面量** `"hot:merge:lock"` | `:401` | `:98` | EQUIVALENT | 静态源码 |
| 36 | 合并 | 读阶段错误被忽略（`pipe.Exec` 返回值都没接） | `:364,368` | `:259-262,293-295` | EQUIVALENT | 静态源码 |
| 37 | 合并 | Lua 报错被误报成「lock lost」 | `:408` `result,_ :=` | `:280-287` | EQUIVALENT | 缺陷照抄（G-HOT-003）；静态源码 |
| 38 | 合并 | `z.Member.(string)` 裸断言 panic | `:370` | 跳过（`:305-307`） | IMPLEMENTATION DIFFERENCE | 等价性论证见 SD-2；静态源码 |
| 39 | 合并 | 抢不到锁 → 跳过本轮 | `:345-347` `continue` | `HotMergeScheduler.java:148-150` | EQUIVALENT | 静态源码 |
| 40 | 合并 | 看门狗 3s 续期、丢失即退出 | `:297-321` | `:187-212` | EQUIVALENT | 静态源码 |
| 41 | 合并 | 周期 **10s** | `:339` | `HotMergeScheduler.java:67,146` | EQUIVALENT | 静态源码 |
| 42 | 定时 | **两条任务是否互相独立** | 两条 goroutine | **单线程调度器（pool=1）** | **OBSERVABLE DIFFERENCE** | 【Boot 4 源码】→ DRIFT-HOTRPC-003 |
| 43 | sketch | FNV-1a 32 指纹 | `heavykeeper.go:178-182` | `HeavyKeeper.java:230-239` | EQUIVALENT | 【双侧活体对拍】17/17 一致 |
| 44 | sketch | FNV-1a 64 + **无符号取模** | `:185-190` | `:247-263` | SEMANTIC MISMATCH | 【双侧活体对拍】→ DRIFT-HOTRPC-001 |
| 45 | sketch | `uint32` 加 / 减自然回绕 | `:94,103` | `:134,143` | EQUIVALENT | 【双侧活体对拍】 |
| 46 | sketch | 无符号比较（`maxCount` / 堆 / 冲突分支） | `:108,155` | `:139,148,211` | EQUIVALENT | 【双侧活体对拍】+ 纯单测 |
| 47 | sketch | `decay^count` 概率让位 | `:97-105` | `:137-145` | EQUIVALENT | 概率算法，断言强度同 Go 自带测试；纯 Mockito 单测 |
| 48 | sketch | `TopK` 并列项次序不稳定 | `:126-128` | `:172` | EQUIVALENT | 【双侧活体对拍】并列项两侧顺序都不同 |
| 49 | sketch | `count==0` 不进 TopK | `:134-136` | `:191-193` | EQUIVALENT | 【双侧活体对拍】weight=0 组两侧同为空 |
| 50 | sketch | Top-K 淘汰门槛（`>`堆顶才换） | `:153-162` | `:210-216` | EQUIVALENT | 【双侧活体对拍】 |
| 51 | 配置 | 权重按 `Name` 建 map、`Code` 丢弃 | `hot.go:47-50` | `HotEventConsumer.java:84-88` | EQUIVALENT | 纯 Mockito 单测 |
| 52 | 配置 | 死配置不迁（`RedisConf.Type` / `KqPusher` / `KqConsumerConf.Name`） | `service_context.go:17-20` | `HotRpcProperties` 无对应字段 | EQUIVALENT | 静态源码 |
| 53 | 配置 | gRPC deadline **2000ms** | `zrpc/config.go:46` `default=2000`（`hot.yaml` 未写） | `application.yml:122` `rpc-timeout: 2s` | EQUIVALENT | 【静态源码 + 默认值取证】 |
| 54 | 配置 | reflection 仅 Dev/Test 模式注册 | `hot.go:38-40`（三份 yaml 均无 `Mode` ⇒ pro） | `application.yml:74` `false` | EQUIVALENT | 静态源码 |
| 55 | 配置 | Redis 命令超时 + **重试** | go-redis `Read/Write=3s`、`Dial=5s`、**`MaxRetries=3`** | `timeout: 3s`、**无重试** | **OBSERVABLE DIFFERENCE** | 【静态源码 + 库默认】→ DRIFT-HOTRPC-004 |
| 56 | 配置 | `Prometheus.Port` / `Telemetry` 的对外表现 | `hot{1,2,3}.yaml` 起真 HTTP 19091/19092/19093 | `management.*` 在 NONE web 下**是空配置** | **OBSERVABLE DIFFERENCE** | 实测依赖树 + Boot 4 源码 |
| 57 | 指标 | Prometheus 指标名逐字一致 | `hot_handler.go:30-45` | `HotRpcMetrics.java:37,40` | EQUIVALENT | 【RAG 运行证据】实跑 scrape，3 行名字与标签逐字一致 |
| 58 | 指标 | `type` 标签只在权重校验后才出现 | `:168` | `HotRpcMetrics.java:54-57` | EQUIVALENT | 纯 Mockito 单测 |
| 59 | 生命周期 | **能否启动** | ✅ | ❌ | SEMANTIC MISMATCH | 【RAG 运行证据】→ DRIFT-HOTRPC-002 |
| 60 | 生命周期 | 进程关闭时后台任务停止 | 两条 goroutine **永不退出**（`context.Background()`） | 随容器关闭 | IMPLEMENTATION DIFFERENCE | 更正确，类注释已标有意收敛；静态源码 |

**统计**：60 行中 EQUIVALENT 46 · IMPLEMENTATION DIFFERENCE 3 · OBSERVABLE DIFFERENCE 8 · SEMANTIC MISMATCH 4（3 行） · UNKNOWN 1。
**支撑它们的独立 DRIFT 条目 = 7 条**（见 §7），多条 DRIFT 会同时点亮多行矩阵。

---

## 5. External Interaction & Error Flow（§8 + §10）

### 5.1 External System: Redis（5 个 key）

| Operation | Go | Java | 参数一致 | 结果影响分支 | 失败是否阻断 |
|---|---|---|---|---|---|
| `ZREVRANGE hot:articles 0 k-1` | `:43` | `HotRankingQueryService.java:69` | ✅ | ✅ | **是**（→UNKNOWN） |
| `ZREVRANGE hot:articles:p{i} 0 -1` | `:268` | `HotRedisSnapshotStore.java:195` | ✅ | ✅ | 否（`:270` log 后 return） |
| pipeline `DEL + ZADD :shadow` | `:217-219` | `:142-150` | ✅ | ✅ | 否（`Inc("fail")` 后 return） |
| Lua `EXISTS / RENAME / EXPIRE 300` | `:227-234` | `HotRedisSnapshotStore.java:74-81` | ✅（逐字） | ✅ | 否 |
| Lua `GET lock / RENAME / EXPIRE TTL` | `:400-407` | `HotMergeScheduler.java:97-104` | ✅（**锁名是字面量**） | ✅ | 否（只打日志） |
| `SETNX lock EX 10` | `:286` | `HotMergeScheduler.java:162-163` | ✅ | ✅ | 否（`continue`） |
| Lua compare-and-`EXPIRE` | `:301-306` | `:76-81` | ✅ | ✅ | 否（`result,_` ⇒ 误报 lock lost） |
| Lua compare-and-`DEL` | `:329-334` | `:84-89` | ✅ | ✅ | 否 |

⚠️ **「Redis 连接失败」与「key 不存在」必须分开**：Go 在 `GetHotArticles` 上正是这么做的
（`:45` 判 `redis.Nil`、`:50` 其余上抛）。Java 侧 Spring Data Redis 对不存在的 key 返回空集合、
对连接失败抛 `DataAccessException` ⇒ **两个分支天然分离**，语义一致。**无「把失败当 miss」的漂移。**

### 5.2 Error Flow（每类 failure 的 Go / Java 最终输出）

| Failure | Go 最终输出 | Java 最终输出 | 等价 |
|---|---|---|---|
| Redis 读失败（查询面） | `codes.Unknown` | `Status.UNKNOWN` | ✅ |
| Redis 读写失败（快照面） | `Inc("fail")` + return | `redisSyncFail()` + return | ✅ |
| Redis 脚本失败（合并面） | `[Merge] lock lost, abort` | 同（照抄 `result,_`） | ✅（缺陷照抄） |
| Redis 脚本失败（看门狗） | `[Lock] lost lock` + return | 同 | ✅ |
| 三类坏 Kafka 消息 | span error + 位点推进 + drop | 同 | ✅ |
| `hk.Add` 内部 panic | **进程崩溃** | 吞掉并 drop | ❌ → DRIFT-HOTRPC-006 |
| 未持有分区 | 静默 return nil | 静默 return null | ✅ |
| 抢不到合并锁 | 本轮跳过 | 本轮跳过 | ✅ |
| `@KafkaListener` 的容器错误 | — | `DefaultErrorHandler(FixedBackOff(0,0))` + `ackAfterHandle` | UNKNOWN（无容器证据） |

### 5.3 Result Semantics（§9：Success / NotFound / Empty / Null / Default / Failure / Timeout）

| 概念 | Go | Java | 等价 |
|---|---|---|---|
| key 不存在（查询面） | `Items: []`，**无 error** | `items: []`，**无 error** | ✅ |
| `TopK` 为空（快照面） | 不写，旧 key 陈旧 | 同 | ✅ |
| `mergePartitions` 结果空 | 连 shadow 都不写 | 同 | ✅ |
| 分区未持有 | 返回 nil（= 正常成功） | 返回 null | ✅ |
| 3 个互动方法 | `UNIMPLEMENTED` | `UNIMPLEMENTED` | ✅ |
| `CommentArticle` | `success=true` | `success=true` | ✅ |

---

## 6. 逐维度结论（§12–§21）

### 6.1 State Semantics（§12）
- `MISS(hkMap 无该分区) → 已计数 → 不入 sketch`：两侧一致（矩阵 14）。
- 初始状态：Go 启动时 `hkMap` 为空，直到第一次 rebalance；Java 相同。
- **⚠️ 分区状态在 Go 上永远走不到「被 revoke」那一步（G-HOT-001 死锁），Java 能走到。**
  ⇒ 状态机不是同一个：Go 的 `OnPartitionsAssigned` 一旦执行过，第一次 `Cleanup` 就把进程冻住；
  Java 的状态机完整且可反复循环。**这是 G-HOT-001 有意偏离的直接后果，不是新缺陷**（已签字，见 SD-1）。

### 6.2 Cache Semantics（§13）
- 本组件**无进程内缓存**；Redis 既是存储也是快照通道。
- 「miss → 查询 → 回写」的对应物是 **`loadFromRedis` 的有损回灌**：sketch 桶计数与真实次数无可逆关系（`:279` 用 score 当新权重）。
- 两侧一致。**不得**改成「精确恢复」——那是行为变更。
- ⚠️ **两个 TTL 不可统一**：分片 300s 硬编码、大 key 3600s 配置值（矩阵 26 / 27）。

### 6.3 Auth / Authorization（§14）
- **本组件两侧都无鉴权。** Go `hot.yaml` 的 `Auth` 走 `zrpc.RpcServerConf.Auth`（默认 false，三份 yaml 未写）；
  Java 无任何安全依赖（dependency:tree 实测无 `spring-security`，`spring-security-crypto` 是 nacos 的传递依赖）。
- ⇒ EQUIVALENT（N/A）。**上游是否有网关兜底属跨组件不变量，不在本轮范围。**

### 6.4 Transaction Semantics（§15）
- **本组件无事务**，两侧都没有。Redis pipeline **不是事务**（Go `pipe.Exec` / Java `executePipelined` 都不包 MULTI）。
- `RENAME` 的原子性由 Redis 单命令保证，两侧都靠它做「影子榜 → 正式榜」的原子切换。
- ⇒ EQUIVALENT（N/A）。**不要给 `flush` 加 `@Transactional`，那会改变失败语义。**

### 6.5 Concurrency Semantics（§16）
- 分区内 sketch：Go `hk.mu` / Java `ReentrantLock` —— **两侧都串行** ✅
- `hkMap`：Go `RWMutex` / Java `ReentrantReadWriteLock` —— **Go 不可重入、Java 可降级**，差异已由 G-HOT-001 登记（有意偏离）。
- `counter`：Go `atomic.Int64` / Java `AtomicLong` ✅
- 消费路径：`go syncToRedis` / `taskExecutor.execute` —— 都是「提交即返回、不等待」 ✅
- **⚠️ 定时任务的并行度不同** ⇒ DRIFT-HOTRPC-003。
- **⚠️ panic 的后果不同** ⇒ DRIFT-HOTRPC-006。

### 6.6 Timeout Semantics（§17）

| 项 | Go | Java | 判定 |
|---|---|---|---|
| gRPC deadline | `zrpc` 默认 2000ms（`zrpc/config.go:46`，`hot.yaml` 未写 ⇒ 走 struct default） | `observability.rpc-timeout: 2s` → `RpcServerInterceptorFactory` | EQUIVALENT（**默认值取证**：读到 `json:",default=2000"` 的消费点 `hot.go:32`） |
| Redis 读 / 写超时 | go-redis `ReadTimeout=WriteTimeout=3s`（`Options.init()` 默认） | `spring.data.redis.timeout: 3s` | EQUIVALENT |
| Redis 连接超时 | go-redis `DialTimeout=5s` | Lettuce `connectTimeout` 默认 10s（`application.yml` 未配） | **OBSERVABLE DIFFERENCE（低）**，见 UNR-3 |
| Redis 重试 | **`MaxRetries` 默认 3** | 无 | **OBSERVABLE DIFFERENCE** → DRIFT-HOTRPC-004 |
| Kafka 消费 | 无单条超时 | 同 | EQUIVALENT |
| 看门狗 / 合并 | 10s tick、3s 续期 | 同 | EQUIVALENT |
| `OnPartitionsRevoked` 的 500ms context | `hot_handler.go:129` `context.WithTimeout(…, 500ms)` | **无**（`detach` 不做 I/O） | 随 SD-1 一并处置 |

### 6.7 Retry Semantics（§18）

| 项 | Go | Java | 判定 |
|---|---|---|---|
| Kafka 坏消息 | **0 重试**（`:108` 无条件 `MarkMessage`） | `FixedBackOff(0L,0L)` + `ackAfterHandle(true)` + listener 永不外抛 | EQUIVALENT（**零重试是刻意的**，`HotKafkaConfig.java:33-42` 有完整论证） |
| Redis 命令 | **最多 3 次**（go-redis 默认 `MaxRetries=3`） | **0 次** | **OBSERVABLE DIFFERENCE** → DRIFT-HOTRPC-004 |
| 合并锁 | 无退避、不排队（`continue`） | 同 | EQUIVALENT |

### 6.8 Serialization Semantics（§19）

| 项 | Go | Java | 判定 |
|---|---|---|---|
| Kafka payload | `json.Unmarshal`（`types.go` 两个字段） | Jackson 2 `ObjectMapper.readValue`（`ArticleHotEvent` record） | EQUIVALENT |
| 未知字段 | 忽略（`encoding/json` 默认） | 忽略（`@JsonIgnoreProperties(ignoreUnknown=true)`，`ArticleHotEvent.java:22`） | EQUIVALENT（**硬要求**：生产端 `producer.go:18-23` 发 4 字段） |
| null vs missing | `article_id` 缺失 ⇒ `""` ⇒ 触发 `:157` 分支 | `null` ⇒ `:161` 的 `null \|\| isEmpty()` | EQUIVALENT |
| protobuf | `hot.proto` 逐字段一致（剥离 option/注释/缩进/空白后 `diff` 为空） | 同 | EQUIVALENT（**注意**：`AGENTS.md §3.4` 把 `hot` 列在「只有一次性实测、无回归网」档 ⇒ UNR-8） |
| `uint32` ↔ float64 score | `float64(uint32)` / `uint32(float64)` | `Integer.toUnsignedLong` / `(int)(long)` | EQUIVALENT【双侧活体对拍】 |
| **FNV 哈希与桶位** | `fnv` + `uint64 % width` | 自实现 FNV + **有符号 long 取模（错）** | **SEMANTIC MISMATCH** → DRIFT-HOTRPC-001 |

### 6.9 Config Semantic Equivalence（§20）
见矩阵 51–56。两处值得点名：
- **默认值藏在 struct tag 里**：`SyncEvery=100` / `TTL=3600` / `PartitionCount=3`（`config.go:30-32` 的 `json:",default=…"`）
  与 `zrpc` 的 `Timeout=2000`。三份 yaml 全部**写死**了这些值 ⇒ 两侧一致，
  但取证必须读 `config.go` 与 `hot.go:32` 这两个**消费点**，不能只贴 `hot.yaml`（§3.3.1）。
- **`PartitionCount` 与 topic 实际分区数必须相等**（`hot_handler.go:361-363` 只读 `p0..p{N-1}`）。
  两侧都保留了这个约束、也都在配置注释里留痕。**Go 侧同样如此 ⇒ 不是 RAG 的缺陷。**

### 6.10 Lifecycle Semantic Equivalence（§21）
- Go：`hot.go:32` 读配置 → `:33` 建 Redis client → `:35` `zrpc.MustNewServer`（**注册 etcd**）→ `:60` 建 consumer group
  → `:65-71` 起消费 goroutine → `:74` `serviceGroup.Start()` 阻塞。⚠️ **消费 goroutine 在 `Start()` 之前就起了。**
- Java：`HotRpcApplication` → Bean 装配 → gRPC server 随 `@GrpcService` 自动起
  → `NacosRegistration` 在 `ApplicationReadyEvent` 注册 → `@Scheduled` 开始。
  ⚠️ `NacosRegistration.java:92-94` **捕获 `NacosException` 后只打日志**，Nacos 不可用时进程照常起来（Go 侧是否 `Fatal` 见 UNR-7）。
- **🔴 致命项：Java 根本走不到上面任何一步** —— 见 DRIFT-HOTRPC-002。

---

## 7. Semantic Drifts（§27 + §36）

> 编号从本域**未占用的最大编号继续**（本文件历史只有 `G-HOT-001/002/003`，无任何 `DRIFT-*`）⇒ 从 `DRIFT-HOTRPC-001` 起。

### DRIFT-HOTRPC-001 🔴 SEMANTIC MISMATCH · HIGH · 已实测
- **Component**：`HeavyKeeper`（`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/domain/HeavyKeeper.java`）
- **Operation**：`hash(key, seed)` → `Add` 的桶定位
- **Category**：数值语义 / 无符号算术移植
- **Go Behavior**：`heavykeeper.go:189` `return int(h.Sum64() % uint64(hk.width))`
  —— `Sum64()` 是**无符号** 64 位，结果与「对 2^64 取余」一致。
- **Java Behavior**：`HeavyKeeper.java:261-262`
  ```java
  int remainder = (int) (h % width);
  return remainder < 0 ? remainder + width : remainder;
  ```
  Java 的 `%` 是**向零截断**，且 `h` 是有符号 `long`。当 FNV-64 最高位为 1 时 `h < 0`，
  该式算的是 `Euclidean(-a, width)`，而 Go 要的是 `Euclidean(2^64 - a, width)`；
  两者相差 `2^64 mod width`（width=1000 → **616**，width=7 → **2**，**永不为 0**）。
- **Observable Impact（已实测）**：生产口径 `Width:1000 / Depth:5 / TopK:100`，灌 400 个 key 后
  **Go 与 RAG 的 Top-100 榜单有 16 条不同**（各 16 条只出现在一侧，84 条重合，分数全同）。
  实测受影响 key 比例 **49.5%**（5000 个样本里 2473 个 FNV-64 最高位为 1）。
  ⇒ **热榜内容与真源不一致**，而热榜是本组件唯一的对外业务产物。
- **根因定位过程（值得当反面教材记）**：按直觉改成 `Math.floorMod(h, width)` ⇒ **实测结果与原写法完全相同（仍然错）**，
  因为 `floorMod` 同样作用在有符号 `h` 上。**唯一正确的是 `Long.remainderUnsigned(h, (long) width)`**。
- **Must Preserve**：`hash(key,seed) ∈ [0,width)` 且**逐 key 等于 Go 的 `uint64 % width`**。
- **Recommended Verification（已做完）**：把 `HeavyKeeper.hash` 换成 `Long.remainderUnsigned` 后
  ① 51/51 个 (key,seed) 的列号与 Go 逐条一致；② 生产口径 Top-100 **集合完全一致**（仅并列项顺序不同，
  两侧都由 map 遍历序 + 非稳定排序决定，不构成差异）。
- **Recommended Fix**：一行 —— `return (int) Long.remainderUnsigned(h, (long) width);`
  并补一条**对拍式守门测试**（不是行为测试）：把 Go 侧算出的 `hash(key,seed)` 值表固化成 fixture。
- **Why It Matters**：不崩、不越界、**静默算错** ⇒ 46 条绿测试、10/10 变异验证**全部无法捕获**
  （`HeavyKeeperTest` 只断言相对次序，从不断言桶位）。这是「绿测试 ≠ 已验证」的又一实例。
- **Evidence**：【双侧活体对拍】Go `go run`（`/tmp/hk-verify-20261023/go3/main.go`，`replace` 指向真源）
  vs Java 生产 `HeavyKeeper`（`/tmp/hk-verify-20261023/java/HeavyKeeperProbe3.java`）；
  列号逐条比对用**反射调用生产的 private `hash` / `fingerprint`**；
  根因确认用 `fix2`（floorMod，错）与 `fix3`（`remainderUnsigned`，对）两份 /tmp 副本。

### DRIFT-HOTRPC-002 🔴 SEMANTIC MISMATCH · BLOCKING · 已实测（启动失败）
- **Component**：进程 / Bean 装配
- **Operation**：`SpringApplication.run` → 上下文启动
- **Category**：框架依赖缺失 / Boot 4 模块化
- **Go Behavior**：无对应物（Go 侧 `encoding/json` 是语言内建，无 DI）。
- **Java Behavior**：`HotEventConsumer.java:59,72,77` 构造注入 `com.fasterxml.jackson.databind.ObjectMapper`，
  而 **Boot 4.0.8 已把 Jackson 自动配置搬出 `spring-boot-autoconfigure`**（实测该 jar 内 Kafka 类数 = 0 的同款结论对 Jackson 同样成立：
  `JacksonAutoConfiguration` 不在 `spring-boot-autoconfigure` 内，而在独立模块 `spring-boot-jackson`），
  且它装配出来的是 **Jackson 3 的 `tools.jackson.databind.json.JsonMapper`**
  （`JacksonAutoConfiguration` 上是 `@ConditionalOnClass(JsonMapper.class)`，`@Bean` 返回 `JsonMapper`），
  **不是 Jackson 2 的 `ObjectMapper`**。`hot-rpc/pom.xml` 既没有 `spring-boot-jackson`，也没有 `spring-boot-starter-json`
  （classpath 上的 Jackson 2 只有从 `com.rag:common` 传递来的 `jackson-databind:2.21.5`，**只有 jar，没有 Bean**）。
- **Difference**：上下文在 `hotEventConsumer` 这一个 Bean 上就失败。
- **Observable Impact**：**进程根本起不来** ⇒ Kafka listener 从未注册、30s/10s 定时任务从未跑过、
  gRPC server 从未监听、Nacos 从未注册 ⇒ **组件当前的可观察行为为零**。
- **实测日志**（探针跑在 `/tmp`，仓库源码零改动）：
  ```
  UnsatisfiedDependencyException: Error creating bean with name 'hotEventConsumer' …
    No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'
  APPLICATION FAILED TO START
  ```
  ⚠️ **补强证据**：把 `spring-boot-jackson` 加到**探针 classpath** 上重跑，**仍然同一个失败**
  ⇒ 证明「加 `spring-boot-jackson` 依赖」**不是**这个缺口的解法（它给的是 Jackson 3）。
- **Likely Cause**：与已登记的 `spring-boot-kafka` 同款（Boot 4 拆模块），
  但**上一轮没被发现，因为没人启动过这个进程**——`AGENTS.md §0` 那句「每个模块接手的第一件事是先让它真的启动一次」。
- **Must Preserve**：进程能起；且 JSON 解析仍应是「坏消息在 listener 内被吞」而不是「在反序列化器里抛」。
- **Recommended Fix**：`article-rpc` 已有现成正确范式 ——
  `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/Jackson2ObjectMapperConfiguration.java`
  （显式 `@Bean ObjectMapper`，且显式关掉 `FAIL_ON_UNKNOWN_PROPERTIES`）。hot-rpc 应照抄该模式。
- **Cross-component（同款缺口，不在本轮 scope）**：
  `like-rpc` 有 6 处构造注入同一个 `ObjectMapper`（`LikeUpdateKafkaListener` / `LikeDomainService` /
  `LikeMessageDigest` / `LikeDomainFactService` / `LikeTaskProgressPublisher` / `GoCompatibleJson`），
  **同样没有该 Bean** ⇒ 该组件的上下文同样起不来。**必须进 issue。**
- **Evidence**：【RAG 运行证据】真实启动进程两次（含补 `spring-boot-jackson` 的对照组）均失败；
  【静态源码 + 依赖树】`mvn dependency:tree` + `spring-boot-jackson-4.0.8.jar` 内 `JacksonAutoConfiguration` 源码。

### DRIFT-HOTRPC-003 🟠 OBSERVABLE DIFFERENCE · MEDIUM · 已取证（Boot 4 默认值）
- **Component**：定时任务调度
- **Operation**：`periodicSync`（30s） / `periodicMerge`（10s）
- **Go Behavior**：`hot_handler.go:76-77` `go h.periodicSync(...)` + `go h.periodicMerge(...)`
  —— **两条独立 goroutine**，各自的 ticker 互不影响。
- **Java Behavior**：`HotRedisSnapshotStore.java:230` 与 `HotMergeScheduler.java:146` 都是 `@Scheduled(fixedRate=…)`，
  共用 Boot 的 `taskScheduler`。`TaskSchedulingProperties$Pool.size` 的**默认值是 `1`**
  （Boot 4.0.8 源码 `spring-boot-autoconfigure-4.0.8-sources.jar` → `TaskSchedulingProperties.java`，`private int size = 1;`），
  而 `hot-rpc/application.yml` **没有配 `spring.task.scheduling.pool.size`**。
- **Difference**：**单线程调度器 ⇒ 合并慢会推迟快照**。
  `mergePartitions` 一次要 pipeline 读 N 个分片 + 写 shadow + 跑 Lua，Redis 命令超时 3s（矩阵 55）
  ⇒ 最坏情况合并可占用调度线程数秒，而 `periodicSync` 的 30s tick 只能排队。
- **Observable Impact**：Redis 抖动期间，**分片快照的实际间隔可能显著大于 30s**，直接影响 rebalance 回灌时效
  ⇒ 分片 key 的 300s TTL 更容易被吃掉。Go 侧不会发生。
- **Must Preserve**：两条周期任务的执行互不阻塞。
- **Recommended Fix**：`spring.task.scheduling.pool.size: 2`（或显式声明一个 `ThreadPoolTaskScheduler`）。
- **Counterfactual（§26）**：一个正常 Spring 开发者**几乎必然**会默认「`@Scheduled` 之间是并发的」
  ⇒ **HIGH-RISK DRIFT**。
- **Evidence**：【静态源码 + Boot 4 源码】默认值来自 `TaskSchedulingProperties` 源码行 `private int size = 1;`，
  而非文档或记忆（§3.3.1）。

### DRIFT-HOTRPC-004 🟠 OBSERVABLE DIFFERENCE · MEDIUM · 已取证
- **Component**：Redis 访问
- **Operation**：全部 Redis 命令
- **Go Behavior**：`svc/service_context.go:17-20` `redis.NewClient(&redis.Options{Addr, Password})` —— **只给两项**，
  其余全走 go-redis v9.20.0 `Options.init()` 的默认值，其中 **`MaxRetries = 3`**
  （`go-redis@v9.20.0/options.go` 的 `init()`：`if opt.MaxRetries == 0 { opt.MaxRetries = 3 }`）。
  ⇒ 网络错误 / 超时会**自动重试最多 3 次**，**不报错、不进 `fail` 分支**。
- **Java Behavior**：`spring-boot-starter-data-redis`（Lettuce 6.8.2）**默认不重试**，
  `application.yml` 也没配任何 `RetryTemplate`。
- **Difference**：瞬时 Redis 故障下，Go 会自愈；RAG 会立刻落 `article_hot_redis_sync_total{status="fail"}` 并丢弃该次快照。
- **Observable Impact**：
  ① `redis_sync_total{status}` 的值**不可跨实现直接比较**；
  ② 弱网下 RAG 的热榜刷新率**低于** Go；
  ③ 查询面 Go 重试 3 次仍失败才返回 `UNKNOWN`，RAG 第一次失败就返回 `UNKNOWN`
  ⇒ **客户端看到的失败时机不同**。
- **Must Preserve**：失败时机与指标口径。**不建议**为了让 RAG「更健壮」去加 `RetryTemplate`——
  那会让它比 Go 更健壮，同样是漂移。
- **Recommended Fix**：两条路，**必须选一条并记账**：(a) 显式配 Lettuce 关闭重试（= 现状）并把口径写进台账；
  (b) 配成 3 次并接受口径变化。**建议 (a)**——按 §5.5「RAG 以 Go 为准」，现状是「RAG 错」，
  但至少要**显式化**，而不是留成偶然。
- **Evidence**：【静态源码 + 库默认】go-redis `options.go` `init()` 消费点；
  Lettuce 侧由 dependency:tree（`io.lettuce:lettuce-core:6.8.2.RELEASE`）+ 未配 retry 推出。
  ✅ **第四轮补充（2026-10-23）**：RAG 侧的「**无重试**」已在**真 Redis** 上确认
  （`HotRedisLuaGateTest` 跑通 4 段 Lua / pipeline / TTL 全路径，无任何隐式重试痕迹）；
  ⚠️ **Go 侧 `MaxRetries=3` 的实际重试面仍未实测**（需要 `CLIENT PAUSE` 制造半程失败）
  ⇒ UNR-1 只闭合了 RAG 侧，Go 侧重试面保持 `unresolved`。

### DRIFT-HOTRPC-005 🟡 OBSERVABLE DIFFERENCE · LOW · 已取证
- **Component**：Kafka 位点提交
- **Operation**：`ConsumeClaim` 的位点推进
- **Go Behavior**：`hot.go:55-58` 用的是 `sarama.NewConfig()` 默认值：
  `Consumer.Offsets.AutoCommit.Enable = true`、`AutoCommit.Interval = 1 * time.Second`
  （`sarama@v1.50.2/config.go:588-589`，**消费点**），配合 `:108` 的 `sess.MarkMessage(msg, "")`。
  ⇒ **位点每 1 秒批量提交一次**。
- **Java Behavior**：`enable-auto-commit: false` + `ack-mode: record`（`application.yml:41,54`）⇒ **每条处理完立刻提交**。
- **Difference**：「处理完两条消息 → 进程随即被 kill」的场景下，
  **Go 会重投最多 1 秒内已处理的 Kafka 消息，RAG 不会**。
- **Observable Impact**：`hk.Add` **不是幂等**（同一事件计两次权）
  ⇒ **Go 在崩溃恢复后会重复计入，RAG 不会** ⇒ 崩溃前后热榜分数分布不同。
- **Must Preserve**：这是一个**方向明确、幅度有界**的差异（Java 更接近 exactly-once）。
  它不是 RAG 的「回归」，但**不是等价**，必须登记而不是假装没有。
- **Recommended Fix**：**不改**。强行把 Go 也改成逐条提交 = 反向修改真源语义（禁止）。
  保留登记即可。
- **Evidence**：【静态源码 + 库默认】`sarama@v1.50.2/config.go:588-589`。

### DRIFT-HOTRPC-006 🟠 OBSERVABLE DIFFERENCE · MEDIUM · 已取证
- **Component**：消费面异常处理
- **Operation**：`consumeMessage` 内部抛出非预期异常
- **Go Behavior**：`TraceConsumer`（`observability/mq.go:30`）是**裸的 `err := fn(consumeCtx)`，没有 recover**；
  而 sarama 的 claim 消费 goroutine **也没有 recover** ——
  实测 `sarama@v1.50.2` 全模块 `recover()` 只出现在 `interceptors.go:27,37` 与 `utils.go:44`，
  **`consumer_group.go` 里一处都没有**；`newConsumerGroupSession` 起的 goroutine 直接调 `sess.consume(...)` → `handler.ConsumeClaim(...)`。
  ⇒ **panic 直接崩进程**。
- **Java Behavior**：`HotEventConsumer.java:117-123`
  ```java
  try { return consumeMessage(partitionId, value); }
  catch (RuntimeException e) { return e; }   // 降级成返回值
  ```
  ⇒ **span 被标成 error、位点推进、事件丢弃，分区继续消费**。
- **Difference**：同一段逻辑崩不崩。
- **Observable Impact**：sketch 层异常下，**Go 会重启并重投，RAG 会静默丢事件**。
  与 DRIFT-HOTRPC-005 叠加 ⇒ 同一场景下两侧热榜**必然不同**。
- **Likely Cause**：Java 侧的 catch 是**刻意**的（类注释第 120 行明写「非预期异常也降级成返回值」），
  但**注释里没有写「Go 侧其实会崩」** ⇒ 这是**未经签字的有意偏离**，不是疏忽。
- **Must Preserve**：先决定要不要签字。若签字（认为「崩进程」不是想要的行为），
  应同时在类注释里写明「Go 侧此处无 recover、会崩进程，本条为有意偏离」。
- **Related**：这一条正是第一轮「`ArrayIndexOutOfBounds` 被 drop 契约吞掉」的**放大器**——
  **正因为 Java 侧吞异常，DRIFT-HOTRPC-001 这种「静默算错」才完全没有症状。**
- **Evidence**：【静态源码】Go `observability/mq.go:30` + sarama `consumer_group.go` 全模块 `recover()` 检索为空。

### DRIFT-HOTRPC-007 ⚪ 证据缺口（非行为漂移）· HIGH
- **Component**：`@KafkaListener` 装配层
- **Finding**：`AGENTS.md §3.3.3` 强制「凡用 `@KafkaListener`，必须有一条**装配层**测试断言 listener 真的进了容器的 registry」。
  实测：`hot-rpc/src/test` 下 **8 个测试类全部是纯 JUnit + Mockito / 手 new**，
  **`@SpringBootTest` / `ContextConfiguration` / `ApplicationContextRunner` 命中数 = 0**
  ⇒ **没有装配层测试**。
- **好消息（须记录，避免被误判为坏消息）**：**Kafka 依赖本身是齐的**。
  `hot-rpc/pom.xml:115-118` 声明了 `org.springframework.boot:spring-boot-kafka`，
  `mvn dependency:tree` 实测在 compile classpath 上，
  且 `spring-boot-kafka-4.0.8.jar` 内确实含 `KafkaAutoConfiguration` 与
  `ConcurrentKafkaListenerContainerFactoryConfigurer`（包名 `org.springframework.boot.kafka.autoconfigure`，
  与 `HotKafkaConfig.java:12` 的 import 精确匹配），同时 `spring-boot-autoconfigure-4.0.8.jar` 内 kafka 条目数实测 **0**
  （**印证** §3.3.3 的历史结论）。
  ⇒ **「上一轮修好了 Kafka 依赖」这件事本身是对的**；问题在于**没有任何测试守住它**。
- **Why It Matters**：一旦有人「清理 pom」，`@KafkaListener` 会**静默失效且启动不报错**，
  而现有 46 条绿测试**全部照样全绿**。这正是 `AGENTS.md §0` 点名的「绿测试不是质量指标」。
- **Recommended Verification**：加一条 `@SpringBootTest`（避免真连 Kafka：mock `ConsumerFactory`
  或用 `spring.kafka.listener.auto-startup=false`），断言 `KafkaListenerEndpointRegistry` 里
  存在 `hotEventConsumer.onMessage`，且 topic / group 占位符解析正确。
- **Evidence**：【静态源码】`grep -rl "SpringBootTest|ContextConfiguration|ApplicationContextRunner" src/test/` → 无命中。

---

### SD-1 / SD-2 / SD-3：第一轮已签字的有意偏离（本轮复核，全部仍成立）

| ID | 内容 | 复核结论 |
|---|---|---|
| **G-HOT-001 / SD-1** | 撤销路径的锁升级（Go 自死锁 → Java 锁外落盘） | ✅ **复核通过**。Go `hot_handler.go:132` 写锁 → `:136` 调 `syncToRedis` → `:196` 取读锁；Go `sync.RWMutex` 不可重入 ⇒ 必然自死锁。Java `ReentrantReadWriteLock` 允许降级 ⇒ 直译会把死锁悄悄变「能跑」。偏离形态正确，`HotRevokeFlushOutsideLockTest` 的判据（锁持有时长）也是对的。状态机差异记在 §6.1。 |
| **G-HOT-002 / SD-2** | `z.Member.(string)` 裸断言 panic → 跳过 | ✅ **复核通过，且等级可下调**：go-redis 读 ZSet 的 member **恒为 string**（Redis 协议里 ZSET member 就是字符串字节）⇒ 该 panic 在真实环境不可达 ⇒ Java 的「跳过」与 Go 的「不发生」**等价**，属 IMPLEMENTATION DIFFERENCE 而非漂移。 |
| **G-HOT-003 / SD-3** | Redis 脚本报错被误报成「lock lost」 | ✅ **复核通过**，Java `HotMergeScheduler.java:280-287` 确实照抄（连 `catch` 分支也打 `lock lost`）。 |

---

## 8. Test Cases（§29 + §30）

### 8.1 已有的 46 条（**全部属【纯 Mockito / 手 new】，无一条装配上下文**）
分布见本文件上半部分 L37 的表。**它们锁死的语义是真的**（drop 契约、指标次序、TopK 归一、UNKNOWN 映射、`uint32` 回绕），
但**不能**证明：(a) 进程能起（已证伪：DRIFT-HOTRPC-002）；(b) listener 进了容器（DRIFT-HOTRPC-007）；
(c) sketch 的桶位正确（本轮才第一次被证伪：DRIFT-HOTRPC-001）。

### 8.2 本轮新增的 CASE（双侧活体对拍 + 进程实跑）

| CASE | Input | Environment | Go | Spring Boot | Expected Semantic Result | Equivalent | Evidence |
|---|---|---|---|---|---|---|---|
| CASE-HK-01 | 6 key / w=1,3,1,100,0,7；`width=100000 depth=5 decay=0.9 topK=100` | 无碰撞 | `article-4=500 / article-3=91 / article-1=37 / article-2=33 / 文章-中文键=7` | 同（逐字节） | TopK 降序、weight=0 不入榜 | **YES** | 双侧活体对拍 |
| CASE-HK-02 | 17 key（含 `""`、中文、emoji）；`width=7 depth=3 decay=0 topK=4` | 必冲突、确定性 | 4 条 | 4 条（**集合相同、并列序不同**） | 冲突分支可跑、不崩、TopK=4 截断 | **YES** | 双侧活体对拍 |
| CASE-HK-03 | 同上 17 key；`topK=100` | 同上 | **12 条** | **11 条**（Go 多一条 `""=3`） | 集合应一致 | **NO** 🔴 | 双侧活体对拍 → DRIFT-HOTRPC-001 |
| CASE-HK-04 | 400 key，`width=1000 depth=5 decay=0 topK=100` | **生产口径** | 100 条 | 100 条，**各 16 条只在一侧** | 集合应完全一致 | **NO** 🔴 | 双侧活体对拍 → DRIFT-HOTRPC-001 |
| CASE-HK-05 | 修正为 `Long.remainderUnsigned` 后重跑 CASE-HK-04 | 同 | 100 条 | 100 条，**集合完全一致（仅Go=0, 仅Java=0）** | 一致 | **YES** | 双侧活体对拍（根因确认） |
| CASE-HK-06 | 修正后逐条比对列号 | 同上 | 51/51 | 51/51 一致 | 列号逐条一致 | **YES** | 反射调用生产 private `hash` |
| CASE-HK-07 | 2 key，weight=`0x80000000` | `width=100000` | `overflow-a/b = 2147483648` | 同 | `uint32` 溢出回绕 | **YES** | 双侧活体对拍 |
| CASE-HK-08 | 2 key，weight=`0` | 同 | 空 | 空 | `count==0` 不入 TopK | **YES** | 双侧活体对拍 |
| CASE-BOOT-01 | 启动 `HotRpcApplication`（原样） | 无 Redis/Kafka/Nacos | — | **FAILED TO START**（缺 `ObjectMapper`） | 能起 | **NO** 🔴 | RAG 运行证据 → DRIFT-HOTRPC-002 |
| CASE-BOOT-02 | 同上 + 探针 classpath 追加 `spring-boot-jackson` | 同 | — | **仍 FAILED**（同一缺 Bean） | 证明「加依赖」不是解法 | — | RAG 运行证据 |
| CASE-CFG-01 | `PrometheusMeterRegistry` 实跑 `scrape()` | 纯内存 | 指标名 `article_hot_kafka_consume_total` / `article_hot_redis_sync_total` | 同（逐字节，含标签值） | 名字一致 | **YES** | RAG 运行证据 |
| CASE-CFG-02 | `TaskSchedulingProperties$Pool.size` 源码取值 | — | 两条 goroutine | **默认 1** | 两任务独立 | **NO** 🟠 | Boot 4 源码 → DRIFT-HOTRPC-003 |
| CASE-CFG-03 | proto diff（剥离 option / 注释 / 缩进 / 空行） | — | `proto/hot.proto` | `hot-proto/src/main/proto/hot.proto` | **diff 为空** | **YES** | 静态 |
| CASE-CFG-04 | dependency tree 有无 servlet / tomcat | — | 无 web | **无 `jakarta.servlet`、无 `tomcat-embed-core`** ⇒ `deduceWebApplicationType()=NONE` | 不起 web 容器 | **YES** | 实测依赖树（**把既有边界 #2 的推断升级为实测**） |

### 8.3 §30 的 15 个标准 CASE 在本组件的适用性
`CASE-01 正常` ✅(HK-01) · `CASE-02 空输入` ✅(HK-08) · `CASE-03 不存在` ✅(Redis Nil 分支，单测) ·
`CASE-04 重复` ✅(重复 assign，单测) · `CASE-05 依赖失败` ✅(**第四轮：真 Redis IT**，`HotRedisLuaGateTest`) · `CASE-06 超时` ⚠️(仅库默认取证) ·
`CASE-07 Retry` ✅(**第四轮：真 Kafka IT**，零重试 handler 的实际效果 + `noRetries` 消费点断言) · `CASE-08/09 Cache Hit/Miss` N/A（本组件无缓存层） ·
`CASE-10 权限` N/A（两侧都无鉴权） · `CASE-11 并发` ⚠️(锁测试有，**但掩盖了 G-HOT-001 的真实后果**) ·
`CASE-12 重复调用` ✅(assign 幂等) · `CASE-13 部分成功` ✅(pipeline 失败后 return) ·
`CASE-14 事务回滚` N/A（两侧都无事务） · `CASE-15 序列化边界` ✅(未知字段 / 坏 JSON / null / uint32 溢出)

---

## 9. Unresolved（§37）——**本节非空，故不得给 `EQUIVALENT`**

| ID | Question | Go Evidence | Java Evidence | Why Unknown | Impact | Required Confirmation |
|---|---|---|---|---|---|---|
| **UNR-1** | go-redis `MaxRetries=3` 的**实际重试面**：哪些命令会重试、pipeline 内部失败重试会不会留下半成品（`DEL` 成功但 `ZADD` 失败 ⇒ shadow key 残留）？ | `go-redis@v9.20.0/options.go` `init()` + `shouldRetry` | Lettuce 无重试；`flush` 用 `executePipelined` | ✅ **已闭合（第五轮）**：**重试面已逐条钉到依赖源码**（`options.go:393-398` / `redis.go:842,1002,1114-1119,1130-1201,1204-1227` / `error.go:84-153`）；**pipeline 重试 = 整组重发**（`pipelineProcessCmds` 每次 `writeCmds` 全量）；**「shadow 半成品」不可达**（DEL 先于 ZADD ⇒ 撞不出 WRONGTYPE；网络错 ⇒ 幂等整组重放 ⇒ 终态一致）。⚠️ 台账原定的验证手段被**证伪**：`CLIENT PAUSE` 只延迟不失败 | **重试面结案为「Go 重试的终态不可观察」** ⇒ DRIFT-HOTRPC-004 严重度 MEDIUM→**LOW**；并**反向查出** Go 会重发**非幂等**的 rename 脚本 ⇒ 可能「实际成功却记 fail」 | ✅ 已完成（第五轮）。**证据强度：真 Redis 实测 + 依赖源码逐行**（⚠️ **不是**双侧活体对拍 —— 真源冻结且无法注入网络故障） |
| **UNR-2** | 4 段 Lua 的**逐条行为**（`EXISTS` 守卫 / `RENAME` 原子性 / `EXPIRE` 与 `RENAME` 的先后） | `hot_handler.go:227-234,301-306,329-334,400-407` | `HotRedisSnapshotStore.java:74-81`、`HotMergeScheduler.java:76-104` | ✅ **已闭合（第四轮）**：真 Redis IT 逐条跑通 4 段 Lua，`DefaultRedisScript` 的 `resultType=Long` 映射已验证；**并证伪了台账对 `EXISTS` 守卫的描述** | 若映射出错，`result==0` 分支判定会翻转 ⇒ 快照 / 合并行为改变 | ✅ 已完成（`HotRedisLuaGateTest`，12 例 + 变异验证） |
| **UNR-3** | Lettuce `connectTimeout` 默认 10s vs go-redis `DialTimeout=5s` | `go-redis@v9.20.0/options.go` `init()`：`DialTimeout = 5s` | `application.yml:28` 只配 `timeout: 3s`，未配 `connect-timeout` | ✅ **已闭合（第四轮取证）**：消费点 `LettuceConnectionConfiguration.createClientOptions`（字节码：`DataRedisProperties.getConnectTimeout()` → `ifnull` ⇒ 未配置时**不调** `SocketOptions.Builder.connectTimeout`）⇒ 落 Lettuce **6.8.2** `SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION = Duration.ofSeconds(10)`（字节码 `ldc2_w 10l → Duration.ofSeconds`） | 连接阶段失败时机不同（低） | ✅ 已完成。**结论：差异确认存在（10s vs 5s），低影响，未修** |
| **UNR-4** | `RoundRobinAssignor` 配置**是否真的落到 consumer**（Boot 4 的 `KafkaProperties.Consumer.buildConsumerProperties()` 是否仍消费 `spring.kafka.consumer.properties.*`） | `hot.go:57` `NewBalanceStrategyRoundRobin` | `application.yml:49` | ✅ **已闭合（第四轮）**：真 Kafka IT 用**生产 `ConsumerFactory`** 建 2 个消费者 + 3 分区 topic ⇒ 实测分配 `[0,2]/[1]`（RoundRobin 形状）；**变异**把策略改成 `RangeAssignor` ⇒ 用例立刻变红（实际 `[0,1]/[2]`）⇒ 证明断言对策略敏感、不是恒真 | 分配策略改变 ⇒ `hkMap` 布局与热榜分布改变 | ✅ 已完成（`HotKafkaConsumerGateTest#productionConsumerFactoryReallyUsesRoundRobinAssignor`） |
| **UNR-5** | `@KafkaListener` 若能起来，**容器并发度**是否等价于 Go 的「每分区一个 goroutine」 | sarama 每分区一个 goroutine（`consumer_group.go` 的 `consume()` 每 claim 一个 goroutine） | `@KafkaListener` 默认 `concurrency=1`（实测：单容器线程持有全部 3 个分区 ⇒ 串行消费） | ✅ **已回答（第四轮）**，**但结论不是「等价」**：单实例部署下 Go 并行消费 3 个分区、RAG 串行消费 ⇒ **不等价**；在「同一部署 × 3 副本」（3 JVM 同组、每 JVM 恰好 1 个分区）下才等价 ⇒ 登记为 **DRIFT-HOTRPC-009** | 影响跨分区的消费并行度（同分区内的顺序保证两侧一致） | ✅ 已回答；**是否把 `concurrency` 调成 `partition-count` 需单独决策**（会改并发语义，本轮未擅改） |
| **UNR-6** | 三份 yaml 的**真实差异**到底是什么 | `etc/hot{1,2,3}.yaml` | `application.yml` 收敛为一份 + 环境变量 | ✅ **已闭合（第四轮复核）**：实测 `diff` —— 差异**不止两项**，还有 **`Interaction.Weights[].Code` 字段的缺失**（hot2/hot3 的 4 条权重都没有 `Code`，hot.yaml 有）；`ListenOn` 8083/8084/8085、`Prometheus.Port` 19091/19092/19093 | —— | 历史结论「只差 `ListenOn` 与 `Prometheus.Port`」**不成立** | 若照历史结论收敛，会把 `Code` 字段当噪声丢掉 ⇒ RAG 侧「保留 code 字段」的做法**反而是对的**（与 `application.yml` 注释、`HotRpcProperties.Weight.code` 一致） | ✅ 已完成（`diff etc/hot.yaml etc/hot2.yaml` / `hot3.yaml`） |
| **UNR-7** | Go 的 `zrpc` 在注册 etcd 失败时是否 `Fatal`（⇒ 进程退出） | `hot.go:35` `zrpc.MustNewServer` | `NacosRegistration.java:92-94` 只 log | ✅ **已闭合（第四轮取证）**：Go 侧「**首次注册失败 ⇒ panic 退出**」，链路 = `zrpc/server.go:88-93`（`Start()` 里 `panic(err)`）→ `zrpc/internal/rpcpubserver.go:48-53`（`keepAliveServer.Start` 先跑 `registerEtcd()`，失败即 return error）→ `core/discov/publisher.go` 的 `KeepAlive→doRegister→GetConn`（etcd 连不上即 error）。⚠️ **运行期**掉线则只 `logc.Errorf` + 重试，**不退出** | 注册失败时两侧存活行为不同 | ✅ 已完成 ⇒ 登记为 **DRIFT-HOTRPC-010** |
| **UNR-8** | `AGENTS.md §3.4` 把 `hot` 列在「只有一次性实测、**无回归网**」档 | proto diff 本轮重做仍为空 | 同 | 🚫 **用户 2026-10-24 决定：不做** —— 「proto 是复制粘贴过来的，不用测」 | proto 可能随时被改坏而无人知晓（**用户已接受该风险**） | 🚫 **关闭**。⚠️ 附带发现：`tools/difftest/check-proto-static.sh` 现在**只覆盖 users 域**，且**即使发现差异也退出 0**（`verdict()` 的 `else` 分支最后一条是 `cat`，恒成功）⇒ 它目前**不能直接当 CI 门禁用**；若日后别的域要用它，需先修退出码 |

---

## 10. 最终判定（§39 · 18 项 checklist）

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致？ | ❌ **否** | **DRIFT-HOTRPC-001**：Top-100 有 16/100 与 Go 不同 |
| 2 | 正常路径是否一致？ | ⚠️ **无法确认** | DRIFT-HOTRPC-002 ⇒ 进程从未跑起来过；001 已证伪「一致」 |
| 3 | 边界输入是否一致？ | ⚠️ **部分** | 空 / 未知字段 / 零权重 / `topK` 负数一致；sketch 桶位不一致 |
| 4 | NotFound / Empty / Null 是否一致？ | ✅ 一致 | 矩阵 4/28/34；`redis.Nil` 与 Spring 空集合语义等价 |
| 5 | Error meaning 是否一致？ | ⚠️ **部分** | 查询面 UNKNOWN 一致；**panic 面不一致**（006）；Redis 重试面不一致（004） |
| 6 | Error propagation 是否一致？ | ⚠️ **部分** | 同上；另外 listener 的容器错误处理**无运行证据**（007） |
| 7 | External dependency failure 是否一致？ | ❌ **否** | 004（重试）+ 005（位点提交节奏） |
| 8 | Cache hit / miss 是否一致？ | ✅ N/A 一致 | 本组件无缓存层 |
| 9 | Transaction boundary 是否一致？ | ✅ N/A 一致 | 两侧都无事务 |
| 10 | State transition 是否一致？ | ⚠️ **部分** | 消费侧一致；分区状态机因 SD-1 不同（Go 走不到 revoke） |
| 11 | Side effects 是否一致？ | ⚠️ **部分** | Redis keyspace 一致；Kafka 位点窗口不同；指标**无暴露端点** |
| 12 | Ordering 是否一致？ | ✅ 一致 | proto message / 字段号逐条一致；TopK 并列项两侧都不承诺次序 |
| 13 | Retry 是否一致？ | ❌ **否** | Kafka 侧一致（都 0 重试）；**Redis 侧 Go 3 次 / RAG 0 次**（004）—— ⚠️ **第五轮复核**：Go 的重试在 shadow 写入路径上**终态不可观察** ⇒ 004 降级 **LOW**；但反向查出 Go 会重发**非幂等**脚本 ⇒ 可能「实际成功却记 fail」 |
| 14 | Timeout scope 是否一致？ | ⚠️ **部分** | gRPC deadline 2s 一致；Redis 命令 3s 一致；**连接超时 5s vs 10s**（UNR-3） |
| 15 | Concurrency semantics 是否一致？ | ❌ **否** | 003（单线程调度器）+ SD-1（锁）+ 006（panic） |
| 16 | Authentication / Authorization 是否一致？ | ✅ N/A 一致 | 两侧都无鉴权（实测依赖树无安全组件） |
| 17 | Serialization semantics 是否一致？ | ❌ **否** | JSON 层一致；但 **DRIFT-HOTRPC-001 就在哈希 / 桶位这一层**；proto 无回归网（UNR-8） |
| 18 | Lifecycle 是否一致？ | ❌ **否** | **DRIFT-HOTRPC-002：RAG 进程启动失败** |

### 判定

```text
SEMANTICALLY NOT EQUIVALENT
```

**判定依据（三条，任一即足以否定）**：
1. **DRIFT-HOTRPC-001** —— 核心算法的桶位在约 **49.5%** 的 key 上与真源不同，
   双侧活体对拍实测 **Top-100 榜单 16 条不同**。热榜是本组件唯一的对外产物。
2. **DRIFT-HOTRPC-002** —— 进程**启动失败**，当前可观察行为为零。
   §39 第 2 项「正常路径是否一致」因此**无法确认**（比「不一致」更糟）。
3. **DRIFT-HOTRPC-003 / 004 / 005 / 006** —— 4 条 OBSERVABLE DIFFERENCE
   落在并发度、外部依赖失败、位点副作用、异常路径四个维度上。

**⚠️ 不得被下列事实误导**：
- 「46 条绿测试全绿」「10/10 变异全变红」「proto 逐字节一致」「指标名逐字节一致」
  —— 这四项**全部仍然成立**，且**全部与本次判定不矛盾**。它们分别锁住了：
  drop 契约与指标次序、`uint32` 比较路径、wire contract、指标名。
  它们**没有**覆盖：桶位哈希、进程启动、装配层、Redis 重试、位点提交节奏。
- 本轮**没有推翻**第一轮的 SD-1 / SD-2 / SD-3 三条有意偏离 —— 复核后仍成立（SD-2 还可降级）。

**收口本判定需要的最小动作（按依赖排序）**：
1. 修 `DRIFT-HOTRPC-002`（照抄 `article-rpc` 的 `Jackson2ObjectMapperConfiguration`）⇒ **先让进程能起**；
2. 起一次进程 + 补 `DRIFT-HOTRPC-007` 要求的装配层测试 ⇒ 把「注解存在」变成「机制生效」；
3. 修 `DRIFT-HOTRPC-001`（`Long.remainderUnsigned`）+ 补一条**对拍 fixture 测试**（不是行为测试）；
4. 决定 `DRIFT-HOTRPC-004` 的口径（建议显式化现状）、`DRIFT-HOTRPC-006` 要不要签字；
5. 起真 Redis + 真 Kafka 做 IT，消掉 UNR-1 / 2 / 4 / 5。
   ✅ **第四轮已做**：UNR-2 / 3 / 4 **已闭合**；UNR-5 **已回答且结论为「非等价」**（⇒ DRIFT-HOTRPC-009）。
   ✅ **第五轮已做**：UNR-1 **已闭合**（Go 侧重试面钉到 go-redis v9.20.0 依赖源码 + 真 Redis 实测；
   **「shadow 半成品」不可达**；⚠️ 台账原定的 `CLIENT PAUSE` 手段**被证伪** ⇒ 该子问以**源码级结论**结案，
   **明确不越读为「已实测」**）⇒ **本条的剩余动作已清零**。

### ⚠️ §10 复核批注（2026-10-24 第十一轮 · D1 活体对拍后）

> ⚠️ **本表写于第一轮**。下列条目的**依据已被后续轮次推翻或升级**，
> **不得继续按第一轮的状态引用**。逐条订正：

| # | 原结论 | 订正后 | 依据 |
|---|---|---|---|
| 1 | ❌ 否（001：Top-100 有 16/100 不同） | ✅ **一致**（限本轮覆盖面） | **DRIFT-HOTRPC-001 已修**（`Long.remainderUnsigned`）+ **本轮双侧活体对拍**：10 组 top_k **逐字节一致**（sha256 相同）、分数与「事件权重和」的独立预测逐条吻合 |
| 2 | ⚠️ 无法确认（002：进程从未跑起来过） | ✅ **已确认** | **DRIFT-HOTRPC-002 已修**；**本轮 RAG hot-rpc 真进程**（真 Kafka + 真 Redis）完整跑通「消费 → sketch → 快照 → 合并 → 大 key → gRPC」全链路 |
| 3 | ⚠️ 部分（sketch 桶位不一致） | ✅ **桶位已一致** | 001 修复后 `HeavyKeeperGoParityTest` 0/405 桶位不一致；本轮活体对拍再次印证（两侧 TopK 集合与分数逐条相同） |
| 17 | ❌ 否（001 在哈希/桶位层） | ✅ **一致**（proto 仍无回归网） | 001 已修；⚠️ **UNR-8 仍然成立**（proto 无回归网，**用户已决定不做**）⇒ 本项的「proto 无回归网」部分**不变** |
| 18 | ❌ 否（002：RAG 进程启动失败） | ✅ **能启动** | 002 已修；本轮真进程启动并完成全链路（并由 `run-hot-rpc-difftest.sh` 第 5 步**断言关键类已加载**） |

⇒ **总判定维持 `SEMANTICALLY NOT EQUIVALENT`**，但**理由已经换过**：
原先倚重的 001 / 002 两条**已不成立**，现在倚重的是
**003（调度并发度）/ 004（Redis 重试）/ 005（位点提交节奏）/ 006（panic 语义）/ 007（listener 装配）**
以及 **UNR-5 已回答的「非等价」结论**（DRIFT-HOTRPC-009）。
⚠️ **不可读成「hot-rpc 已等价」**：本轮只把**正常路径**与**TopK 语义**做成了活体级证据，
**失败路径 / 并发路径 / 重试面**仍无活体证据。


---

<!-- ====================================================================================
     ↓↓↓ 第三轮：2026-10-23 23:0x「对齐修复轮」（align/hot 分支）
     ↑ 上方 L1~L750 是 2026-10-22 首次实现 + 2026-10-23 第二轮组件级验证的历史记录，
       历史条目与编号一条未删（仅就地加了复核批注）
     ==================================================================================== -->

# 第三轮：修复轮（2026-10-23）

> **范围**：`service/hot/hot-rpc`（+ 强依赖 `service/hot/heavykeeper/`）。
> **分支**：`align/hot`（worktree `../RAG.worktrees/hot`），**只改 hot 域文件**。
> **本轮性质**：对第二轮台账里**已取证但未修**的条目动手；修法一律「先复现 → 再改 → 再验」。
> **只读边界**：**Go 真源仓库零改动**（探针在 `/tmp`，`go.mod` 用 `replace` 指向真源的**逐字节副本**）。

## 本轮一句话

**第二轮的 `SEMANTICALLY NOT EQUIVALENT` 判定里，最重的一条（DRIFT-HOTRPC-001）已修并双侧活体对拍通过；
另修一条并发语义漂移（-003）；两条需要「决策」的（-004 / -006）已签字。**
⇒ 本组件**核心算法层**已对齐真源；**剩余未闭合项集中在「真 Redis / 真 Kafka 集成测试」**。
🟥 **2026-10-24 第十一轮订正：这一句已过期** —— **第四轮**已取得真 Redis（12 例）+ 真 Kafka（4 例）IT，
**第十一轮**又取得**完整双栈对拍**（真 Kafka + 2×真 Redis + 真 gRPC + 栅栏 ⇒ 10 组 top_k 逐字节一致）
⇒ 集成测试这条路**已走完**，不再是剩余项。当前 hot-rpc 的未闭合项只剩**域外**的：
`DRIFT-HOTRPC-005`（Kafka 位点提交节奏，⏸ 登记不修）+ `DRIFT-HOTRPC-007`（装配层证据缺口，已补测试）
+ 已签字的 004 / 006 / 009 / 010。

🟥 **2026-10-26 第二十八轮订正**：本节下面「域外」的表述**已过期**（写于 2026-10-24）——
hot 域台账多处说「剩余未闭合项全部落在域外（D14/D25/D27 + D21/D22/D23）」，
**逐条核对后这 6 条现已全部闭合**（D27 转为**已签字的有意偏离**）⇒ 详见
[`hot/api.md`](./api.md) 的「2026-10-26 第二十八轮订正」表。
⇒ **hot 域三组件的域内未闭合项清单为空**（本轮 global 盘点未发现 hot 域的新未闭合项）。


## 逐条处置

| ID | 上一轮判定 | 本轮处置 | 证据 |
|---|---|---|---|
| **DRIFT-HOTRPC-001** | 🔴 SEMANTIC MISMATCH | ✅ **已修** | 【双侧活体对拍】2025/2025 列号一致 + Top-100 集合与分数逐条一致；变异 **5/5 全红** |
| **DRIFT-HOTRPC-002** | 🔴 BLOCKING（起不来） | ✅ 上一轮修复轮已清（Jackson 2 Bean） | 本轮 `HotRpcSpringContextWiringTest` 6/6 仍绿 |
| **DRIFT-HOTRPC-003** | 🟠 OBSERVABLE DIFFERENCE | ✅ **已修** | `spring.task.scheduling.pool.size: 2` + 装配层断言真实线程池 ≥ 2 |
| **DRIFT-HOTRPC-004** | 🟠 OBSERVABLE DIFFERENCE | 📝 **已签字**（不复刻） | 新证据：**Lettuce 6.8.2 连 `io/lettuce/core/retry` 包都没有** ⇒ Spring 侧**无配置入口** |
| **DRIFT-HOTRPC-005** | 🟡 OBSERVABLE DIFFERENCE | ⏸ **保持不修**（方向明确、幅度有界） | 上一轮结论不变 |
| **DRIFT-HOTRPC-006** | 🟠 OBSERVABLE DIFFERENCE | 📝 **已签字**（RAG 更稳的一侧） | 见 `docs/Intentional_Deviations/hot/rpc.md` |
| **DRIFT-HOTRPC-007** | ⚪ 证据缺口 | ✅ 上一轮修复轮已补装配层测试 | 本轮复跑仍绿 |

---

## DRIFT-HOTRPC-001 ✅ 已修 · SEMANTIC MISMATCH · HIGH

- **Component**：`HeavyKeeper`（`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/domain/HeavyKeeper.java`）
- **Operation**：`hash(key, seed)` → `Add` 的桶定位
- **根因（不变）**：Go 的 `uint64 % width`（`heavykeeper.go:189`）被写成 Java 的**有符号**取模 + 补正。
- **修法（一行）**：
  ```java
  // 旧（错）：算的是 Euclidean(-a, width)，与 Go 的 Euclidean(2^64 - a, width) 恒差 2^64 mod width
  int remainder = (int) (h % width);
  return remainder < 0 ? remainder + width : remainder;
  // 新（对）：正除数下的无符号余数
  return (int) Long.remainderUnsigned(h, (long) width);
  ```
  ⚠️ `Math.floorMod` **同样错**（实测与旧写法输出完全一致），已在类注释里把这两条错路钉死。

### 本轮证据（比上一轮更强：换了 fixture 与口径）

| 手段 | 结果 |
|---|---|
| **双侧活体对拍（修正前）** | Go 侧用真源 `heavykeeper.go` 的**逐字节副本**（sha256 `2fef39e7a8e35a19e6774fbfb1ed360e138dd73b5baef3090e58a2bbebd4309e`）+ 最小导出探针；Java 侧直接调用**生产类**。生产口径 `width=1000/depth=5/topK=100`、405 个 key（含空串、中文、emoji、300 字符长串）⇒ **173/405 个 key 的列号与 Go 不同**，Top-100 榜单**集合不一致**（`article-58/59` 仅 Go 有，`article-112/113` 仅 RAG 有） |
| **双侧活体对拍（修正后）** | 同输入 ⇒ **0/405 列号不一致**；Top-100 **集合一致、分数逐条一致** |
| **对拍 fixture 守门测试** | 新增 `HeavyKeeperGoParityTest`（5 例）：3 组列号表（width=1000/7/100000 × 17 个 key × 每 seed 一列）+ 2 组榜单（重碰撞 width=7；生产口径变权 400 key） |
| **变异验证** | 把实现改回旧写法 ⇒ **5/5 全部变红**（列号表 3 条 + 榜单 2 条）⇒ **守门有效，非假绿** |
| **既有 46 条测试** | 全绿未受影响（`HeavyKeeperTest` 只断言相对次序，本就不覆盖桶位） |

### 变更文件

- `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/domain/HeavyKeeper.java`（`hash()` + 类注释补 Go 出处与两条错路）
- `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/domain/HeavyKeeperGoParityTest.java`（新增，5 例）

---

## DRIFT-HOTRPC-003 ✅ 已修 · OBSERVABLE DIFFERENCE · MEDIUM

- **Go 形态**：`hot_handler.go:76-77` 两条**独立 goroutine**（`go h.periodicSync(...)` + `go h.periodicMerge(...)`），各自的 ticker 互不影响。
- **RAG 原形态**：两条 `@Scheduled` 共用 Boot 的 `taskScheduler`，而 Boot 4.0.8 的
  `TaskSchedulingProperties$Pool` 默认 **size = 1**。
- **本轮取证（消费点，不只是声明点）**：
  - 默认值：`TaskSchedulingProperties$Pool.<init>` 字节码 = `iconst_1; putfield size`；
  - 属性前缀：`spring.task.scheduling`（常量池字符串）；
  - **消费点**：`TaskSchedulingConfigurations$ThreadPoolTaskSchedulerBuilderConfiguration.threadPoolTaskSchedulerBuilder`
    → `getPool().getSize()` → `ThreadPoolTaskSchedulerBuilder.poolSize(int)`。
- **修法**：`application.yml` 加 `spring.task.scheduling.pool.size: 2`。
- **守门测试**：`HotRpcSpringContextWiringTest` 新增 `taskSchedulerHasAtLeastTwoThreads`
  ⇒ 断言的是**调度器真实的线程池大小**（消费点生效），**不是**「yaml 里写了 2」。

---

## DRIFT-HOTRPC-004 📝 已签字（不复刻）· OBSERVABLE DIFFERENCE · ~~MEDIUM~~ → **LOW**（2026-10-24 第五轮复核下调）

- **Go**：`svc/service_context.go:17-20` 只给 `Addr`+`Password` ⇒ `MaxRetries` 走 go-redis v9.20.0
  `Options.init()` 的默认 **3**（`if opt.MaxRetries == 0 { opt.MaxRetries = 3 }`）⇒ 网络错误/超时会**自动重试最多 3 次**。
- **RAG**：Lettuce **0 次**重试。
- **⚠️ 本轮新增的关键取证（决定为什么「不复刻」而不是「没空复刻」）**：
  - Spring Data Redis **4.0.7** 的 `connection/lettuce` 包内 **`RetryPolicy` 引用数 = 0**
    ⇒ **Spring 侧没有暴露 Lettuce 命令级重试策略的配置入口**；
  - Lettuce **6.8.2** 的 jar 内**根本没有 `io/lettuce/core/retry` 包**
    （顶层包清单：`annotations/api/cluster/codec/.../protocol/resource/search/sentinel/support`），
    只有 `protocol/DefaultEndpoint$RetryListener` ⇒ 命令级重试策略**不是 Lettuce 6.8 的 API 面**。
  ⇒ ⇒ **「配成 3 次」在当前依赖栈下不可达**；要在应用层自己写重试，就等于把重试放到
  **错误的层**（会改变 drop 语义、指标口径与失败时机）⇒ 那比现状更坏。
- **签字结论**：**不复刻 Go 的 Redis 命令重试**，登记为有意偏离
  ⇒ [`docs/Intentional_Deviations/hot/rpc.md`](../../Intentional_Deviations/hot/rpc.md) `SD-HOT-RPC-002`。
- **遗留口径提醒**：`article_hot_redis_sync_total{status}` 的值**不可跨实现直接比较**；
  弱网下 RAG 的热榜刷新率**低于** Go。

### ⚠️ 第五轮复核批注（2026-10-24 · UNR-1 闭合后）

**签字口径不变**（Lettuce 无重试入口仍是事实 ⇒ 仍然不复刻），但**严重度 MEDIUM → LOW**，并**新增一条反向差异**。

| | 第五轮之前的认知 | 第五轮实测 / 取证后的认知 |
|---|---|---|
| Go 的 3 次重试影响多大 | 「网络错误/超时会自动重试最多 3 次」⇒ 暗示 RAG 会丢写 | **在 shadow 写入这条路径上「终态不可观察」**：pipeline 重试是**整组重发**（`redis.go:1204-1227`），`DEL`+`ZADD` **幂等** ⇒ 重放后终态与一次成功**完全一致** |
| RAG 少一次重试 ⇒ 会丢数据 | 担心 RAG 丢写 | **丢不了**：shadow 是从**内存 sketch** 派生的，下一个 30s tick（`HotRedisSnapshotStore.periodicSync()`，Go `hot_handler.go:244-263`）会**重新 flush** ⇒ **自愈** |
| Go 侧独有的 shadow 半成品 | UNR-1 的核心担忧 | **不存在**：两侧 pipeline 命令序相同（DEL 先于 ZADD）⇒ `ZADD` 撞不出 `WRONGTYPE`；网络错 ⇒ 幂等整组重放 |
| ⚠️ 反向差异（**新查出**） | 未知 | Go 会重发**非幂等**的 rename 脚本 ⇒ 若首次已执行成功但回复丢失，重试再执行时 `EXISTS shadow == 0` ⇒ **返回 0** ⇒ Go `hot_handler.go:236-238` **记 `fail`** ⇒ **实际成功却记 fail**（指标误计）。RAG 无重试 ⇒ **不产生**该形态 |

⇒ **降级理由**：① Go 的重试在终态上不可观察；② RAG 侧失败后果自愈；
③ 残留差异只剩「网络抖动瞬间的行为」与「非幂等脚本被重发的指标误计」⇒ 都是**低频、低影响**。
⚠️ **证据强度**：真 Redis 实测 + go-redis v9.20.0 依赖源码逐行；
**不是**双侧活体对拍（真源冻结，无法注入网络故障）⇒ 详见[第五轮章节](#第五轮unr-1-闭合轮go-侧重试面取证)。

---

## DRIFT-HOTRPC-006 📝 已签字（RAG 更稳的一侧）· OBSERVABLE DIFFERENCE · MEDIUM

- **Go**：`observability/mq.go:30` 的 `TraceConsumer` 是裸 `err := fn(...)`（**无 recover**），
  且 sarama 的 claim goroutine **也没有 recover** ⇒ 消费路径 panic ⇒ **崩进程**。
- **RAG**：`HotEventConsumer.java:116-124` 把 `RuntimeException` 降级成返回值 ⇒ span 标 error、
  位点推进、事件丢弃、**分区继续消费**。
- **签字结论**：**保留 RAG 的降级行为**（崩进程不是想要的语义），但**必须留痕**
  ⇒ `HotEventConsumer` 的类注释已补「Go 侧此处无 recover、会崩进程，本条为有意偏离」
  ⇒ [`docs/Intentional_Deviations/hot/rpc.md`](../../Intentional_Deviations/hot/rpc.md) `SD-HOT-RPC-001`。
- ⚠️ **与 001 的耦合**：正因为这里吞异常，001 那种「静默算错」才完全没有症状 ——
  ⇒ 两条必须**一起**看，不能只修算法不修降级。

---

## ⚠️ 本轮**未**闭合的项（不得读成「hot-rpc 已完成」）

| 项 | 状态 | 说明 |
|---|---|---|
| **真 Redis / 真 Kafka 集成测试** | ✅ **第四轮已闭合** | 真 `redis:7-alpine`（12 例）+ 真 `apache/kafka:3.7.2`（4 例），全部默认跑、不 skip，均配变异验证。✅ **残留已清**：Go 侧 `MaxRetries=3` 的实际重试面（UNR-1）**第五轮已闭合** |
| **UNR-1（Go 侧重试面 / shadow 半成品）** | ✅ **第五轮已闭合** | Go 重试面已逐条钉到 go-redis v9.20.0 源码；**「shadow 半成品」不可达**（两侧 DEL 先于 ZADD + 幂等整组重发）；反向查出「Go 重发**非幂等** rename 脚本 ⇒ 实际成功却记 fail」⇒ **DRIFT-HOTRPC-004 降级 LOW**。⚠️ 台账原定的 `CLIENT PAUSE` 验证手段**被证伪**（只延迟、不能失败） |
| **`HotRankingQueryService` 的 Redis 读取分支仍只有 Mockito 单测** | ✅ **第六轮已闭合** | ⚠️ **2026-10-24 订正**：本行原文写的是「Go 侧 `?page` 之外的入口面」⇒ 那是 **api 域**的 query 解析面（已由 D11/D13 等覆盖并修完），**串台到本台账**，故删除；保留的**唯一属 rpc 域**的未知项（查询面从 Redis 读 `hot:articles`）**第六轮已用真 Redis 闭合** ⇒ 新增 `HotRankingQueryServiceRedisGateTest`（7 例，真 `redis:7-alpine`）+ 2 条变异全红 |
| **proto 回归网** | 🚫 **用户决定不做**（2026-10-24） | 「proto 是复制粘贴过来的，不用测」⇒ 关闭。⚠️ 附带发现：现有 `check-proto-static.sh` **只覆盖 users 域**且**恒退出 0**，不能直接当 CI 门禁 |
| **DRIFT-HOTRPC-009（容器并发度）** | 📝 已签字 | 用户 2026-10-24 拍板保持现状 ⇒ `SD-HOT-RPC-003`；实际「×3 副本」部署下等价。⚠️ **单实例部署下不等价** ⇒ 残留风险 |
| **DRIFT-HOTRPC-010（注册失败存活语义）** | 📝 已签字 **+ 已补健康检查** | 用户 2026-10-24 拍板「保持 + 签字 + 补 NOT_READY」⇒ `SD-HOT-RPC-004`；配套实现与守门测试已落地 |
| **DRIFT-HOTRPC-005（Kafka 位点节奏）** | ⏸ 保持不修 | Go 每 1s 批量提交、RAG 逐条提交 ⇒ 崩溃恢复时 Go 会重复计入。方向明确、幅度有界，**登记不修** |

---

<!-- ====================================================================================
     ↓↓↓ 第四轮：2026-10-24 00:0x「真中间件集成测试轮」（align/hot 分支）
     ↑ 上方第三轮及更早的历史条目与编号一条未删（仅就地加了复核批注）
     ==================================================================================== -->

# 第四轮：真 Redis / 真 Kafka 集成测试轮（2026-10-23 夜 ~ 2026-10-24 凌晨）

> **范围**：`service/hot/hot-rpc`（测试 + 两处生产注释修正）；**只改 hot 域文件**。
> **分支**：`align/hot`（worktree `../RAG.worktrees/hot`）。
> **本轮性质**：把第二轮台账里**只有静态源码证据**的六项语义位（4 段 Lua / 两个 TTL /
> `EXISTS` 守卫 / `RENAME` 原子性 / RoundRobin 实际分配 / 零重试 handler 实际效果）
> **升级为真中间件证据**，并按 §3.3 纪律给每条配**变异验证**。
> **只读边界**：**Go 真源仓库零改动**。

## 本轮一句话

**UNR-2 / 3 / 4 已闭合，UNR-1 部分闭合，UNR-5 已回答且结论为「非等价」；
同时真 Redis 证伪了台账对 `EXISTS` 守卫的描述（⇒ DRIFT-HOTRPC-008）。**

## 产出

| 文件 | 性质 | 规模 |
|---|---|---|
| `HotRedisLuaGateTest.java` | **新增** 真 Redis 守门测试 | 12 例 |
| `HotKafkaConsumerGateTest.java` | **新增** 真 Kafka 守门测试 | 4 例 |
| `HotRedisSnapshotStore.java` | **改** 生产注释（`EXISTS` 守卫语义修正 + 170 行残留措辞） | 3 处 |
| `HotMergeScheduler.java` | **改** `LOCK_TTL_SECONDS` 可见性（private → 包可见，供测试引用） | 1 行 |
| `hot-rpc/pom.xml` | **改** testcontainers 依赖 + surefire `TESTCONTAINERS_RYUK_DISABLED` | — |

## 逐条处置

| 原 UNR | 处置 | 证据 |
|---|---|---|
| **UNR-2**（4 段 Lua 逐条行为） | ✅ 闭合 | 真 Redis 逐段跑通；`resultType=Long` 映射已验；TTL 300 vs 3600 实测 |
| **UNR-3**（Lettuce connectTimeout） | ✅ 闭合 | 消费点字节码 + Lettuce 6.8.2 `DEFAULT_CONNECT_TIMEOUT_DURATION=10s` |
| **UNR-4**（RoundRobin 是否落到 consumer） | ✅ 闭合 | 真 Kafka 实测 `[0,2]/[1]`；变异改 Range ⇒ 变红 |
| **UNR-1**（go-redis `MaxRetries=3` 的实际重试面） | ⚠️ 部分闭合 | RAG 侧「无重试」已实测；**Go 侧仍需 `CLIENT PAUSE` 半程失败对拍** |
| **UNR-5**（容器并发度 vs 每分区一个 goroutine） | ✅ 已回答，**结论非等价** | 实测 `concurrency=1`；⇒ 登记 DRIFT-HOTRPC-009 |
| **UNR-6**（三份 yaml 的真实差异） | ✅ 闭合，**且历史结论被推翻** | 实测 diff：除 `ListenOn` / `Prometheus.Port` 外还有 `Weights[].Code` 字段缺失 ⇒ 「只差两项」不成立 |
| **UNR-7**（Go 注册失败是否 Fatal） | ✅ 闭合，**结论：会退出** | go-zero `zrpc/server.go:88-93` + `rpcpubserver.go:48-53` + `discov/publisher.go`；⇒ 登记 DRIFT-HOTRPC-010 |
| **UNR-8**（proto 回归网） | 🚫 **用户决定不做** | 「proto 是复制粘贴过来的，不用测」（2026-10-24）；⇒ 关闭，**风险由用户接受** |

## 变异验证（三条，全部按预期变红）

| # | 变异 | 预期红 | 实测 |
|---|---|---|---|
| **M1** | `application.yml` 的 `partition.assignment.strategy` 改 `RangeAssignor` | RoundRobin 分配用例 | ✅ 变红：`expected: <[2, 0]> but was: <[0, 1]>` |
| **M2** | `HotKafkaConfig.zeroRetryErrorHandler()` 改 `FixedBackOff(0,9)` | 零重试用例 + 装配层用例 | ✅ 变红 2 条：`expected: <1> but was: <7>`（被重投 7 次）、`noRetries expected: <true> but was: <false>` |
| **M3** | `HotEventConsumer` 去掉「缺 `article_id` 就 drop」校验 | drop 契约用例 | ✅ 变红：`expected: <1.0> but was: <2.0>`（坏消息被计数） |
| **M-redis-1** | 分片 TTL 300 → 3600 | TTL 用例 | ✅ 2 例变红 |
| **M-redis-2** | `EXISTS` 守卫改查 `KEYS[2]` | 守卫用例 | ✅ 2 例变红 |

> ⚠️ **M3 第一轮没红** —— 暴露的是**断言自身的观察窗口有洞**（轮询撞上中间态），
> 不是生产代码对。修法见 `docs/Pitfalls/hot/rpc.md` **P-HOTRPC-10**。

---

## DRIFT-HOTRPC-008 ⚪ 台账/注释写反（**非行为漂移**）· 已实测证伪

- **Component**：Redis 快照（`EXISTS` 守卫）
- **Go Behavior**：`hot_handler.go:227-234` 的改名脚本首行 `if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end`，
  调用处 `keys := []string{shadowKey, partitionKey}` ⇒ **`KEYS[1] = shadow`**；
  而 shadow 在同一次 pipeline 里刚被 `ZADD` 写入 ⇒ **守卫恒为 1** ⇒ **首次快照不会被丢**。
- **RAG Behavior**：**生产代码本来就与 Go 一致**（同样传 `List.of(shadowKey, partitionKey)`）。
  ⚠️ **错的是本台账与生产代码注释**：两者都写成「守卫检查目标分片 key ⇒ 首次快照必丢」。
- **证伪证据（真 Redis）**：`HotRedisLuaGateTest#firstSnapshotIsNotLost` —— 先 `DEL`+`ZADD` 写 shadow
  再跑改名脚本 ⇒ 返回 `1`、分区 key 正常落地；
  **变异**：把守卫改成检查 `KEYS[2]` ⇒ 该用例立刻变红 ⇒ 证明用例确实在测「守卫查哪个 key」。
- **处置**：① 修正 `HotRedisSnapshotStore` 的三处注释；② 修正本台账 Equivalence Matrix 第 25 行；
  ③ **不改生产逻辑**（它本来就是对的）。
- ⚠️ **为什么这条值得单列**：**台账的错误结论会主动制造回归** ——
  照着旧台账去「修」生产代码，会把本来正确的实现改坏。
  ⇒ 教训见 `docs/Pitfalls/hot/rpc.md` **P-HOTRPC-09**。

---

## DRIFT-HOTRPC-009 📝 已签字（保持现状）· OBSERVABLE DIFFERENCE · LOW · 已实测（**部署相关**）

- **Component**：Kafka 消费并发度
- **Go Behavior**：`sarama` 的 `consumerGroupSession.consume()` **每个 partition claim 起一个 goroutine**
  ⇒ 单进程持有 N 个分区时，**N 个分区并行消费**。
- **RAG Behavior**：`@KafkaListener` 默认 `concurrency=1` ⇒ **单消费者线程串行轮询它持有的全部分区**。
- **实测**：真 Kafka IT 中生产容器（concurrency=1）**独占了全部 3 个分区**
  ⇒ `HotPartitionSketchRegistry.size() == 3` 且事件处理是串行的。
- **差异何时可见**：
  - **单实例部署**（1 个 JVM 持 3 个分区）⇒ Go 并行 3 路、RAG 串行 1 路 ⇒ **不等价**；
  - **实际部署「同一部署 × 3 副本」**（3 个 JVM 同一个消费组，RoundRobin ⇒ 每 JVM 恰好 1 个分区）
    ⇒ 每 JVM 1 线程 1 分区 ⇒ **等价**。
- **同分区内顺序保证**：两侧都是分区有序 ⇒ **一致**。
- **影响**：低（吞吐 / 跨分区并行度；不改热榜内容语义）。
- **处置**：📝 **已签字（用户 2026-10-24 拍板「保持 + 签字」）** —— 改 `concurrency` 会**改变并发语义**
  （且 `concurrency` 是静态值、分区数可随部署变化）；**实际「×3 副本」部署下两者本就等价** ⇒ 改不到实际行为。
  ⇒ 登记为 [`docs/Intentional_Deviations/hot/rpc.md`](../../Intentional_Deviations/hot/rpc.md) **SD-HOT-RPC-003**。
  ⚠️ **残留风险**：若出现**单实例部署**（或副本数 < 分区数），并行度差异会显现 ⇒ 届时重新评估。

---

## DRIFT-HOTRPC-010 📝 已签字（保持 + 补健康检查）· OBSERVABLE DIFFERENCE · MEDIUM · 已取证（**存活语义**）

- **Component**：服务注册（etcd / Nacos）
- **Go Behavior**：**首次注册失败 ⇒ 进程 panic 退出**。链路（全部已读到**消费点**）：
  1. `hot.go:74` `serviceGroup.Start()` → `zrpc/server.go:88-93`
     ```go
     func (rs *RpcServer) Start() {
         if err := rs.server.Start(rs.register); err != nil {
             logx.Error(err)
             panic(err)          // ← 进程退出
         }
     }
     ```
  2. `zrpc/internal/rpcpubserver.go:48-53` —— `keepAliveServer.Start` **先**跑 `registerEtcd()`，失败即 return error；
  3. `core/discov/publisher.go` —— `KeepAlive()` → `doRegister()` → `internal.GetRegistry().GetConn(endpoints)`
     ⇒ **etcd 连不上即 error**（注册发生在 `Start()`，**不是** `MustNewServer` 构造期）。
  ⚠️ **边界**：`MustNewServer` 构造期只做 `NewRpcPubServer`（**不连 etcd**）⇒ 连接失败**不在构造期**暴露。
  ⚠️ **运行期**掉线走 `doKeepAlive()` ⇒ `logc.Errorf` + 重试，**不退出** ⇒ 只有「首次注册失败」才 panic。
- **RAG Behavior**：`NacosRegistration.java:92-94` —— `registerInstance` 抛 `NacosException` 时
  **只 `LOG.error`**，进程**照常存活并继续服务**。
- **Difference**：注册中心不可用时，Go 的 hot-rpc **起不来**（对外表现为「该副本不存在」），
  RAG 的 hot-rpc **照常起来**（对外表现为「该副本存在但注册不上」⇒ 客户端按 Nacos 列表找它会失败）。
- **Observable Impact**：中等 —— **不是「更健壮」，而是「假活」**：进程在、端口在、但服务发现里没有它；
  若上游按「端口可连」判断健康，会误判为可用。
- **处置**：📝 **已签字（用户 2026-10-24 拍板「保持 + 签字 + 补『注册失败 ⇒ 健康检查 NOT_READY』」）**
  ⇒ 选 (b)：**进程存活 + 健康检查报 NOT_SERVING**。
  ⇒ 登记为 [`docs/Intentional_Deviations/hot/rpc.md`](../../Intentional_Deviations/hot/rpc.md) **SD-HOT-RPC-004**。
  ✅ **配套实现已同批落地**：`NacosRegistration.markHealth(...)` →
  `HealthStatusManager.setStatus(SERVICE_NAME_ALL_SERVICES, NOT_SERVING)`（**grpc 标准 health service，与 Go 同构**）。
  守门测试 `NacosRegistrationHealthGateTest`（2 例，走**真实 gRPC health 协议**查询）；
  变异验证：抽掉 `markHealth(NOT_SERVING)` ⇒ 变红（`expected: <NOT_SERVING> but was: <SERVING>`）。
- **Evidence**：【静态源码 + 依赖源码逐行】go-zero `v1.10.2`
  `zrpc/server.go:88-93` / `zrpc/internal/rpcpubserver.go:48-53` / `core/discov/publisher.go` 的 `KeepAlive→doRegister→GetConn`。
- ⚠️ **与 SD-HOT-RPC-001 的同构性**：「Go 会崩、RAG 降级存活」这同一个取舍，已经在**消费侧**签过一次字
  （`SD-HOT-RPC-001` 消费异常降级 drop）⇒ **本条建议按同一口径签字**，而不是各写各的。

---

<!-- ====================================================================================
     ↓↓↓ 第五轮：2026-10-24「UNR-1 闭合轮」（align/hot 分支）
     ↑ 上方第四轮及更早的历史条目与编号一条未删（仅就地加了复核批注）
     ==================================================================================== -->

# 第五轮：UNR-1 闭合轮（Go 侧重试面取证）

> **范围**：`docs/`（本台账 + `docs/Pitfalls/hot/rpc.md` + `docs/Intentional_Deviations/hot/rpc.md`）；
> **探针**在 `/tmp/redis-retry-probe/`（**Go 真源仓零改动**，真源 `tracked diff` 为空）。
> **分支**：`align/hot`（worktree `../RAG.worktrees/hot`）。
> **本轮性质**：把第四轮唯一剩下的 UNR-1 做掉 —— ⚠️ **但不是用第四轮台账指定的方法**（那个方法被实测证伪）。
>
> **⚠️ 证据强度声明**：**真 Redis 实测 + go-redis v9.20.0 依赖源码逐行**。
> **不是**「双侧活体对拍」—— 真源冻结且**无法注入网络故障** ⇒ 该子问以**源码级结论**结案，
> **明确不越读为「已实测」**（§3.3 证据强度分层）。

## 本轮一句话

**UNR-1 闭合：Go 的 3 次重试在 shadow 写入这条路径上「终态不可观察」；
UNR-1 担心的「Go 侧独有的 shadow 半成品」不存在；
⚠️ 但反向查出 Go 会重发「非幂等」的 rename 脚本 ⇒ 可能「实际 RENAME 成功却记 fail」
⇒ DRIFT-HOTRPC-004 严重度 MEDIUM → LOW（签字口径不变）。**

## 一、Go 侧重试面（依赖源码逐行钉死）

### 1.1 重试次数

| 事实 | 出处 |
|---|---|
| `MaxRetries` 默认 **3**（`-1` ⇒ 0） | `options.go:393-398`；Go 侧 `svc/service_context.go:17-20` **只给** `Addr`+`Password` ⇒ 走默认 |
| 单命令尝试次数 = `MaxRetries+1` = **4** | `redis.go:842` `for attempt := 0; attempt <= c.opt.MaxRetries` |
| pipeline 尝试次数 = 同上 | `redis.go:1143`（同一个 `for`） |

### 1.2 哪些错误会重试（`shouldRetry`，`error.go:84-153`）

| 错误类别 | pipeline 路径 | 单命令路径 | 出处 |
|---|---|---|---|
| `io.EOF` / `io.ErrUnexpectedEOF` | ✅ 重试 | ✅ 重试 | `error.go:90-92` |
| dial 失败（`net.OpError{Op:"dial"}`） | ✅ 重试 | ✅ 重试 | `error.go:97-100` |
| 连接池超时（`pool.ErrPoolTimeout`） | ✅ 重试 | ✅ 重试 | `error.go:108-111` |
| `context.Canceled` / `context.DeadlineExceeded` | ❌ **不重试** | ❌ **不重试** | `error.go:103-105`（**优先于**超时判定） |
| 带 timeout flag 的错误 | ✅ **重试** | ⚠️ 写超时 ⇒ 重试；读超时 ⇒ **命令自带 readTimeout 时不重试** | pipeline：`redis.go:1171` 传字面量 `true`；单命令：`redis.go:971`（写）/ `redis.go:992-996`（读）/ `redis.go:1002` |
| `MaxClients` / `LOADING` / `READONLY` / `MasterDown` / `ClusterDown` / `TryAgain` / `NoReplicas` | ✅ 重试 | ✅ 重试 | `error.go:122-142` |
| 其余 Redis 逻辑错误（`WRONGTYPE` / `NOPERM` / …） | ❌ **不重试** | ❌ **不重试** | `shouldRetry` 无命中 ⇒ `false` |

> ⚠️ **本轮订正第四轮的一处错记**：第四轮台账写「单命令路径传 `c.opt.RetryTimeout`」——
> **在 v9.20.0 里不成立**：`options.go` 全文检索 `RetryTimeout` **只命中 `DialerRetryTimeout`**
> ⇒ v9.20.0 **没有** `Options.RetryTimeout` 字段。
> 单命令的「timeout flag 是否重试」由 `cmd.readTimeout()` 是否为 `nil` 决定（`redis.go:992-996`）。
> ⇒ 这正是 §3.3.1 的老坑：**「配置里没写」≠「没这个概念」** —— 先读到**消费点**再下结论。

### 1.3 重试粒度 = **整组**（pipeline）

调用链（全部读到**消费点**）：

```text
Client.Pipeline()                      redis.go:1508-1514   exec = c.processPipelineHook
  └─ processPipeline                   redis.go:1114-1119
       └─ generalProcessPipeline(..., c.pipelineProcessCmds, "PIPELINE")   redis.go:1130
            └─ pipelineProcessCmds     redis.go:1204-1227
                 ├─ cn.WithWriter(...) → writeCmds(wr, cmds)   ← ★ 每次重试都写全量
                 └─ cn.WithReader(...) → pipelineReadCmds(...)
```

⇒ **每次重试都把整组命令重新写一遍** ⇒ **`DEL` + `ZADD` 一起重放**。

> ✅ **官方注释同证**（`pipeline.go:19-21`，v9.20.0 原文）：
> 「Redis client has retransmission logic in case of timeouts,
> **pipeline can be retransmitted and commands can be executed more then once**.」

### 1.4 pipeline 内的错误落点

- `pipelineReadCmds`（`redis.go:1229-1244`）：**网络错误** ⇒ `setCmdsErr` + `return err`（整组失败）；
  **Redis 逻辑错误** ⇒ 只落在 `cmds[i].Err()`，循环继续（后续命令照常执行）；
- `Exec` 返回的是 `cmds[0].Err()`（`redis.go:1243` 的 `return cmds[0].Err()`）
  ⇒ ⚠️ **若第一个命令（`DEL`）出逻辑错，`Exec` 会返回该错**；
  若只有后面的 `ZADD` 出逻辑错，`Exec` 返回 `nil`，错误只在 `cmds[1].Err()` 里。
  ⚠️ Go 真源 `hot_handler.go:219-224` **只判 `err != nil`** ⇒ **`ZADD` 的逻辑错会被 Go 漏检**（当成成功）。
  ⇒ RAG 侧 `HotRedisSnapshotStore.java:150-163` 同样只判 `executePipelined` 是否抛异常 ⇒ **两侧同构**，不是漂移。

## 二、「shadow 半成品」不可达（两侧同构）

- 两侧的 pipeline **命令序完全相同**：
  - Go `service/hot/rpc/internal/mqs/hot_handler.go:216-219`：`pipe.Del(ctx, shadowKey)` → `pipe.ZAdd(ctx, shadowKey, zMembers...)`；
  - RAG `HotRedisSnapshotStore.java:150-158`：`operations.delete(shadowKey)` → `operations.opsForZSet().add(shadowKey, tuples)`。
- ⇒ **DEL 先于 ZADD** ⇒ `ZADD` **不可能**撞 `WRONGTYPE`（DEL 已把 string 型 shadow 删掉）⇒ **逻辑错误路径不产生半成品**。
- 唯一能让「DEL 生效但 ZADD 未生效」的失败 = **网络层错误** ⇒ 触发**整组重发** ⇒
  `DEL`+`ZADD` **幂等** ⇒ 重放后终态与「一次成功」**完全一致**。
- **真 Redis 实测**（`redis:7-alpine` @ 宿主端口 16379，探针 `/tmp/redis-retry-probe/`；
  真源 pipeline 序列用 sha256 `8e82333a1a2405bd9cd0fa78d79361042036435035825f8a41da3be7f667fa85` 绑定）：

  | 组 | `MaxRetries` | `Exec` err | `cmds[i].Err()` | shadow 终态 |
  |---|---|---|---|---|
  | A | **3** | `''` | `['', '']` | **zset / 2 members** |
  | B | **0** | `''` | `['', '']` | **zset / 2 members** |

  ⇒ **两组终态逐项相同** ⇒ **重试开关对可观察终态无影响**。
- **Redis 逻辑错误**（如 ACL 只禁 DEL）⇒ 两侧**都不重试**，但那时 DEL 也失败 ⇒
  shadow **原样保留**（陈旧但完整，**不是半成品**）。
- ⇒ ✅ **UNR-1 担心的「Go 侧独有的 shadow 残留」不存在**。

> ⚠️ **台账措辞的方向要修正**：原写「`DEL` 成功但 `ZADD` 失败 ⇒ shadow key **残留**」——
> **方向反了**：DEL 成功 ⇒ 旧数据已清空 ⇒ shadow 是**不存在**（不是残留）；
> 真正会「残留旧 member」的形态是 **DEL 失败**（如 ACL 只禁 DEL）而 ZADD 成功 ⇒ 新旧 member 混在一个 zset 里。

## 三、⚠️ 反向发现：Go 会重发**非幂等**的 rename 脚本 ⇒ 可能「实际成功却记 fail」

- rename 脚本是**单命令**（`EVALSHA`），走 `redis.go:830/842` 的单命令路径 ⇒ **同样会被重发**。
- 脚本**非幂等**（`hot_handler.go:227-234`）：
  ```lua
  if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
  redis.call('RENAME', KEYS[1], KEYS[2])
  redis.call('EXPIRE', KEYS[2], 300)
  return 1
  ```
  第一次执行成功 ⇒ shadow 已被 RENAME 走 ⇒ **重试再执行时 `EXISTS shadow == 0` ⇒ 返回 0**。
- Go `hot_handler.go:236-238` 判 `result == 0` ⇒ **`redisSyncTotal.Inc("fail")`**
  ⇒ **实际 RENAME 已成功，指标却记 fail**。
- **RAG 不产生该形态**：Lettuce 无重试 ⇒ 脚本只执行一次 ⇒ `result==0` 只在 shadow **真的**不存在时出现
  （`HotRedisSnapshotStore.java:169-175`）。
- ⇒ ⇒ **DRIFT-HOTRPC-004 是双向差异**：

  | 方向 | 现象 | 触发条件 |
  |---|---|---|
  | **Go 更稳** | 网络错误 ⇒ 自动重试 ⇒ 写入更可能最终成功 | 网络层错误（EOF / dial / 池超时 / 带 flag 的超时） |
  | **Go 更不稳** | 非幂等脚本被重发 ⇒ **实际成功却记 fail**（指标误计） | 恰在「命令已执行、回复丢失」的窗口 |

## 四、⚠️ 验证手段被**证伪**（这本身是产出）

第四轮台账给 UNR-1 指定的手段是「起 Go 真源 + 真 Redis，用 `CLIENT PAUSE` 制造半程失败」。
**实测：这条路径不成立。**

| 手段 | 实测结果 | 结论 |
|---|---|---|
| `CLIENT PAUSE <ms> WRITE` | 只暂停**其它客户端**的写命令；**发起方自己**照常执行 | ❌ 证伪 |
| `CLIENT PAUSE <ms> ALL` | **只延迟执行，客户端不超时** ⇒ 造不出失败 | ❌ **证伪** |
| `CLIENT KILL <id>` | go-redis **连接池透明重拨** + pipeline 落到池中另一条连接 ⇒ `Exec err=''`、终态完整 | ❌ 证伪 |

⇒ **「命令已执行、回复丢失」这个窗口，无法用 Redis 服务端命令稳定制造**；
要观测到它需要**网络层注入**（iptables / toxiproxy）或改真源加钩子 ——
**真源冻结，不做**（§15：不擅自改 Go 行为）⇒ 该子问以**源码级结论**结案，**明确标注不越读为「已实测」**。

⇒ 教训见 `docs/Pitfalls/hot/rpc.md` **P-HOTRPC-11**。

## 五、对既有条目的影响

| 条目 | 影响 | 处置 |
|---|---|---|
| **DRIFT-HOTRPC-004** | 严重度 **MEDIUM → LOW**（**签字口径不变**：Lettuce 无重试入口仍是事实） | 已就地加复核批注 |
| **SD-HOT-RPC-002** | 可观察影响清单**新增**一条（Go 的「实际成功却记 fail」） | 已就地加复核批注 |
| **UNR-1** | ✅ **闭合** | 本节 |

**降级理由**：
1. Go 的 3 次重试在 shadow 写入路径上**终态不可观察**（幂等 + 整组重发）；
2. RAG 侧失败的后果**自愈** —— shadow 本来就是从**内存 sketch** 派生的，
   下一个 30s tick（`HotRedisSnapshotStore.periodicSync()`，Go `hot_handler.go:244-263`）会**重新 flush**，
   不会留下持久损伤；
3. 残留差异只剩「网络抖动瞬间的行为」与「非幂等脚本被重发的指标误计」⇒ 都是**低频、低影响**。

## 六、探针位置（可复现）

| 项 | 路径 |
|---|---|
| 探针工程 | `/tmp/redis-retry-probe/`（`main.go` + `go.mod` + `go.sum` + 二进制 `unr1probe`） |
| 构建命令 | `cd /tmp/redis-retry-probe && GOFLAGS=-mod=mod GOPROXY=off GOSUMDB=off GOPRIVATE='*' go build -o unr1probe .` |
| 真源 pipeline 序列（含 sha256 绑定） | `/tmp/unr1-pipeline-src.txt`（取自 `hot_handler.go:216-219`） |
| 最近一次输出 | `/tmp/unr1-result.jsonl` |
| Redis 容器 | `docker run -d --name hot-unr1-redis -p 16379:6379 redis:7-alpine` |

⚠️ **复现提示**：探针复刻真源 pipeline 序列时用 **sha256 绑定真源原文**，
避免「复刻本身漂移」—— 这是本轮唯一能防「拿自己的复刻当真源」的手段。

---

<!-- ====================================================================================
     ↓↓↓ 第六轮：2026-10-24「查询面真 Redis 轮」（hot 域第六轮，align/hot 分支）
     ↑ 上方第五轮及更早的历史条目与编号一条未删
     ==================================================================================== -->

# 第六轮：查询面真 Redis 轮（2026-10-24）

> **范围**：`service/hot/hot-rpc`（**只加测试**，生产代码**零改动**）。
> **分支**：`align/hot`（worktree `../RAG.worktrees/hot`）。
> **本轮性质**：把未闭合表里**最后一条 rpc 域未知项**做掉 ——
> 「`HotRankingQueryService` 的 Redis 读取分支仍只有 Mockito 单测」。
> **只读边界**：**Go 真源仓库零改动**。

## 本轮一句话

**查询面已上真 Redis：7 例全绿 ⇒ 闭区间 / 降序 / 同分 tie-break / float64 截断 四条语义位全部实测确认；
2 条变异全红 ⇒ 守门非假绿。本轮生产代码零改动 —— 查出来的不是漂移，是「本来就对，现在有证据了」。**

## 产出

| 文件 | 性质 | 规模 |
|---|---|---|
| `HotRankingQueryServiceRedisGateTest.java` | **新增** 真 Redis 守门测试 | 7 例 |

## 一、为什么这条必须上真 Redis（mock 测不了什么）

| 语义位 | mock 为什么测不出 |
|---|---|
| **rank 闭区间**（Go `:43` 的 `0, k-1` ⇒ 取 **k** 条不是 k+1） | mock 返回什么就是什么，区间语义不在 mock 里 |
| **降序** | mock 不经服务端排序 |
| **同分 tie-break** | ZSET 同分按 member **字典序升序**排 rank ⇒ `ZREVRANGE` 取**降序**；且这同时验「Spring 返回的 `Set` **是否保序**」⇒ 若 `deserializeTupleValues` 给的是 `HashSet`，顺序会丢 ⇒ **热榜顺序与 Go 不同** |
| **float64 → uint32 向零截断**（Go `:62` 的 `uint32(z.Score)`） | mock 直接给 `Double`，不经 float64 存储 |

## 二、命令层取证：两侧发的是**同一个** Redis 命令

| 侧 | 出处 | 命令 |
|---|---|---|
| Go | go-redis v9.20.0 `ZRevRangeWithScores` | `ZREVRANGE key start stop WITHSCORES` |
| RAG | Lettuce 6.8.2 `RedisCommandBuilder.zrevrangeWithScores`（**消费点**）| `createCommand(ZREVRANGE, …, args.add(start).add(stop).add(WITHSCORES))` |
| RAG | Spring Data Redis 4.0.7 `DefaultZSetOperations.reverseRangeWithScores` | `connection.zRevRangeWithScores(rawKey, start, end)` |

⇒ 服务端返回的字节应当相同；本轮验的是**框架层有没有在中间动手脚**。

## 三、实测结果（真 `redis:7-alpine`，7 例全绿）

| 用例 | 验的语义位 | 结果 |
|---|---|---|
| `rankRangeIsClosedInterval` | k=3 ⇒ **恰好 3** 条（不是 4） | ✅ |
| `resultsAreSortedByScoreDescending` | 按 score 降序（不是插入序） | ✅ |
| `tieBreakIsLexicographicDescending` | 同分 ⇒ member 字典序**降序**（`c, b, a`）⇒ **同时证明 Spring 的 `Set` 保序** | ✅ |
| `floatScoreIsTruncatedTowardZero` | `3.7 ⇒ 3`（round 会给 4）、`2.2 ⇒ 2` | ✅ |
| `negativeScoreIsUndefinedOnGoSide` | 负 score ⇒ RAG 取确定值（-1）；**Go 侧 `uint32(负数)` 是未定义行为 ⇒ 不做等价判定** | ✅（记录） |
| `missingKeyReturnsEmptyListWithoutError` | key 不存在 ⇒ 空列表、不抛 | ✅ |
| `topKIsNormalized` | k≤0 ⇒ 20；k>100 ⇒ 100；k=100 ⇒ 100 | ✅ |

## 四、变异验证（2 条，全部按预期变红）

| # | 变异 | 预期红 | 实测 |
|---|---|---|---|
| **M-R1** | `reverseRangeWithScores(key, 0, k - 1)` ⇒ `0, k`（区间上界写错） | 闭区间 + TopK 归一 | ✅ 2 条红：`Expected size: 3 but was: 4`、`Expected size: 20 but was: 21` |
| **M-R2** | `HeavyKeeper.scoreToCount(score)` ⇒ `(int) Math.round(score)` | 截断用例 | ✅ 1 条红：`expected: 3 but was: 4` |

⇒ 两条分别锁住「取几条」与「怎么取整」⇒ **守门非假绿**。

## 五、⚠️ 顺带查出三条 Go 侧**不可达分支**（不是 RAG 缺陷，但值得留档）

1. **Go 的 `err == redis.Nil` 分支（`get_hot_articles_logic.go:45-48`）在 `ZRevRangeWithScores` 上不可达** ——
   `redis.Nil` 是 **bulk string** 命令（`GET` 之类）在 key 不存在时的哨兵；
   而 **zset range 命令**对不存在的 key 返回**空数组 + nil error** ⇒ 走不到 `:45-48`，
   实际是走 `:53` 的循环（results 为空）⇒ 返回 `Items: []`。
   ⇒ **RAG 侧不需要、也不应该有**对应的 nil 哨兵分支 ⇒ 现状**正确**。
2. **member 非字符串分支（`:55-59`）在真 Redis 上不可达** ——
   Redis 协议里 ZSET member 就是**字符串字节** ⇒ 与已签字的 `SD-2` 同构 ⇒ **真 Redis IT 无法验这条**（不是没验，是不可达）。
3. **负 score 时 Go 的 `uint32(z.Score)` 是未定义行为** ——
   Go spec：float64 → uint32 超出范围时结果 **implementation-specific** ⇒
   **无法用「以 Go 为准」判定 RAG 取什么值才算对** ⇒ 本轮只钉住「RAG 取了一个确定值」，
   等价性记 **UNKNOWN**（不擅自签「等价」，也不擅自签「不等价」）。

---

# 第十一轮：D1 活体对拍闭合 + U7 闭合 + U9 系统变异（2026-10-24）

## 本轮一句话

hot-rpc 的**双侧活体对拍**首次真正跑通：**真 Kafka + 2×真 Redis + 真 gRPC**，
两侧对**同一批 300 条事件**产出的 `GetHotArticles` 响应 **10 组 top_k 全部逐字节一致**
（sha256 相同）⇒ **DRIFT-HOTRPC-001 / 002 / 003 从「源码级已修」升级为「活体级已证」**；
**U7 闭合**（TopK 的解释 + 降序）；**U9 系统变异**在 hot-rpc(84 条) + hot-common(14 条) 上
查出 **3 处真实守门缺口**并补上 2 条守门测试。

## 一、D1：双侧活体对拍（本轮主产出）

### 1.1 编排

| 环节 | 做法 | 出处 |
|---|---|---|
| Kafka | **1 个** `apache/kafka:3.7.2`，topic `article-hot-events` **3 分区** | 两侧用**不同 group-id** 消费**同一批消息**（Go `article-hot-group` / RAG `rag-hot-group`）—— 这正是要验的东西 |
| Redis | **2 个** `redis:7-alpine`（Go:23979 / RAG:23980） | Redis key 两侧**都硬编码**（`hot:articles` / `hot:articles:p{i}` / `hot:articles:shadow` / `hot:merge:lock`）⇒ 共用一个必然互相覆盖 |
| 事件 | 固定内容、固定顺序、**无桶冲突**的 300 条（24 篇文章 × 4 种 type） | 无冲突 ⇒ HeavyKeeper 的**概率衰减分支不可达** ⇒ 两侧随机数序列不同也**不影响**可比性（`tools/difftest/hot_rpc_events.py`） |
| 栅栏 | 轮询**两侧** Redis 的 `hot:articles`，直到**两侧都含全部 24 条**且**连续两轮内容稳定** | 消费进度天然不对称 ⇒ 不设栅栏比的是「合并时刻」而不是「合并结果」 |
| 比对 | **自定义 Marshaller** 直接取两侧的**原始 wire 字节** | 不经 JSON / 结构化中间层 ⇒ 字段序、null vs []、uint32 截断都逃不掉 |

### 1.2 结果：10 组 top_k **全部逐字节一致**

| top_k | 取回条数 | wire 字节（go / rag） | sha256[0:16] | 结论 |
|---|---|---|---|---|
| 0 | 20 | 260 / 260 | `e967fbe5ee97c000` | ✅ 一致 |
| 1 | 1 | 13 / 13 | `206925817bfc0212` | ✅ 一致 |
| 5 | 5 | 65 / 65 | `3a5c52da7637937f` | ✅ 一致 |
| 20 | 20 | 260 / 260 | `e967fbe5ee97c000` | ✅ 一致 |
| 100 | 24 | 312 / 312 | `caef16291a43e12e` | ✅ 一致 |
| 101 | 24 | 312 / 312 | `caef16291a43e12e` | ✅ 一致 |
| 200 | 24 | 312 / 312 | `caef16291a43e12e` | ✅ 一致 |
| 1000 | 24 | 312 / 312 | `caef16291a43e12e` | ✅ 一致 |
| -1 | 20 | 260 / 260 | `e967fbe5ee97c000` | ✅ 一致 |
| -100 | 20 | 260 / 260 | `e967fbe5ee97c000` | ✅ 一致 |

⇒ **`RESULT: ALL top_k BYTE-IDENTICAL between Go and RAG`**；两侧 `descending=true`。
⇒ 栅栏 `rc=0`；冻结的 Go 仓库 tracked diff 为空（对拍纪律 1 守住）。

### 1.3 分数与「独立预测」吻合

事件生成器在**不跑任何 Java/Go 代码**的前提下，按 `yaml Interaction.Weights`
（like=1 / comment=2 / coin=3 / share=4）算出每篇文章的**期望权重和**与**期望排序**，
写进 `manifest.json`。实测两侧的分数**逐条等于**该预测：

```
art-000=60  art-001=59  art-002=57  art-003=54  art-004=50 ...
```

⇒ 这是一条**不依赖被测实现**的第三方断言：既证明两侧一致，也证明两侧都等于「事件权重和」。

### 1.4 证据强度

| 手段 | 覆盖到 | 覆盖不到 |
|---|---|---|
| **本轮双侧活体对拍** | 消费 → sketch → 快照 → 合并 → 大 key → gRPC 读 → wire 字节 | ① **只有 24 篇文章**（未触到 100 条截断）；② 无桶冲突 ⇒ **概率衰减分支未跑到**；③ 单实例（未触到多副本分片） |

## 二、U7 闭合：TopK=100 被 hot-rpc 如何解释？items 是否按热度降序？

| 问题 | 答案 | 证据 |
|---|---|---|
| `top_k` 被 clamp 到多少？ | **上限 100 / 下限回落 20** | `top_k=100/101/200/1000` 两侧都返回**全部 24 条**（本轮只有 24 篇，够不到 100）；`top_k=0/-1/-100` 两侧都返回 **20 条** ⇒ k≤0 ⇒ 回落默认 20 ✓（`get_hot_articles_logic.go:35-40`） |
| items 是否按热度降序？ | **是** | 两侧 `descending=true`（10 组 top_k 全部）；`hot:articles` 是 Redis **ZSET** ⇒ 按 score 降序天然成立 |
| `k` 到底怎么解释？ | 是「取前 k 条」的**上界**，`k≤0` 回落 **20** | 与 `hot.proto` 注释「默认 20，最大 100」一致 |

⇒ **U7 闭合**（本轮由 `docs/DRIFT/hot/api.md` 的 U7 行同步）。

## 三、U9 系统变异（hot-rpc 10 点 + hot-common 4 点）

| 模块 | 变异点 | 结果 |
|---|---|---|
| **hot-rpc**（基线 **84** 条） | R01 Top-K 排序的 uint32 比较 / R02 零计数准入 / **R03 衰减概率的 uint32 取指数** / R04 TopK 上限 / R05 TopK 下界 / R06 零权重事件 / R07 分片 TTL / **R08 合并截断长度** / **R09 合并排序方向** / R10 gRPC 错误映射 | **7/10 被抓到；3 处「没变红」（R03/R08/R09）** |
| **hot-common**（基线 **14** 条） | HC01 成功文案 / HC02 未知码回落 / HC03 码值 5001 / HC04 码值 1015 | **4/4 全部被抓到** |

⇒ 合计 **11/14 被守门抓到**。三处「没变红」**不是假绿**，而是**真实守门缺口**（下节）。

## 四、三处「没变红」的定性（按 P22 先证伪 no-op）

P22 要求：判「守门缺口」之前必须先证伪「变异本身是 no-op」。三条的结论：

| 变异 | no-op 证伪 | 定性 |
|---|---|---|
| **R03** `Math.pow(decay, unsignedToDouble(counts[row][col]))` → `Math.pow(decay, counts[row][col])` | `unsignedToDouble(x) = (double) Integer.toUnsignedLong(x)` ⇒ 对 **x ∈ [0, 2^31)** 与 `(double) x` **完全相等** ⇒ 变异只在 **count ≥ 2^31** 时才分岔。而 count 可达 2^31（`count += weight` 回绕 ⇒ 位模式为负） | 🔴 **真实缺口**：不是 no-op，但**现有用例的 count 永远达不到 2^31** ⇒ 需专门构造 |
| **R08** `MERGED_LIMIT = 100` → `101` | 截断分支**可达**，但**既有用例的分片条目最多 4 条** ⇒ **永远走不到截断** | 🔴 **真实缺口**：截断层**完全没有测试** |
| **R09** `merged.sort(降序)` → `升序` | 单独看：`writeShadow` 写的是 Redis **ZSET** ⇒ **内容与插入顺序无关** ⇒ 对最终产物**无影响**。**但**与 R08 耦合时：升序 + 截断 100 ⇒ 留下的是**分数最低**的 100 条 ⇒ **可观察** | 🔴 **真实缺口**：与 R08 **同一个未测路径** ⇒ 一条测试可同时抓住两者 |

⇒ **结论：3 处缺口同源于「>100 条的合并截断层从未被测过」+「uint32 溢出的衰减分支从未被测过」。**

## 五、补上的 2 条守门测试（并做变异验证）

| 测试 | 位置 | 锁住 | 构造要点 |
|---|---|---|---|
| `mergeTruncatesToThe100HighestScores` | `HotRedisLuaGateTest`（**真 Redis**） | **R08 + R09** | 分片放 **150 条**（分数 1..150）⇒ 断言结果**恰好 100 条** **且最低分 = 51**（⇒ 留的是**最高**的一批）。⚠️ 只断言「条数 = 100」**抓不到** R09（排序反了条数仍对）⇒ 必须断言**边界分数** |
| `decayProbabilityUsesUnsignedCount` | `HeavyKeeperUint32OverflowTest` | **R03** | `width=1` ⇒ 所有 key 落同一桶 ⇒ 必然冲突；`add("a", 2e9)` ×2 ⇒ count 位模式为负；`add("b",5)` 走冲突分支；再 `add("a",1)` 把桶里的计数**回写进 TopK**（`updateTopK` 只在同一 key 再次加入时更新已存在条目 ⇒ 否则衰减效果在 `topK()` 上看不见）。<br>**确定性来自**：无符号 `decay^4e9 = 0` ⇒ `nextDouble() < 0` **恒假** ⇒ 不衰减；有符号 `decay^(-2.9e8) = +Inf` ⇒ `nextDouble() < +Inf` **恒真** ⇒ 必然衰减 ⇒ 两条路径**完全相反且都不依赖随机数** |

### 变异验证（三条，**全部按预期变红** ⇒ 守门非假绿）

| 变异 | 预期红 | 实测 |
|---|---|---|
| **R03** `Math.pow(decay, unsignedToDouble(counts[row][col]))` ⇒ `Math.pow(decay, counts[row][col])` | `decayProbabilityUsesUnsignedCount` | ✅ **1 条红**（102s）：`HeavyKeeperUint32OverflowTest#decayProbabilityUsesUnsignedCount` |
| **R08** `MERGED_LIMIT = 100` ⇒ `101` | `mergeTruncatesToThe100HighestScores` | ✅ **1 条红**（134s）：`HotRedisLuaGateTest#mergeTruncatesToThe100HighestScores` |
| **R09** `merged.sort(降序)` ⇒ `升序` | `mergeTruncatesToThe100HighestScores` | ✅ **1 条红**（90s）：`HotRedisLuaGateTest#mergeTruncatesToThe100HighestScores` |

⇒ 补测后 hot-rpc 基线 **86 条**（84 + 新增 2），**三个原本「没变红」的变异点现已 10/10 全被抓住**；
连同 hot-common 的 4/4 ⇒ **hot 域三个模块合计 24/24 变异点全部有守门**。
🟥 **2026-10-24 订正：这个 24/24 是本轮写下时的数** —— 同日 hot-api 侧又补了 **M11**（补全预算的绝对 deadline，
因 U5-OPEN-1 闭合而新增）⇒ **hot 域合计应为 25/25**（hot-api 11 + hot-rpc 10 + hot-common 4）。
⇒ 以 `docs/DRIFT/hot/api.md` 的 **25/25** 口径为准。

## 六、连带查出的 3 条**编排级**踩坑（不是产品缺陷，但会伪装成产品缺陷）

⇒ 详见 [`docs/Pitfalls/hot/rpc.md`](../../Pitfalls/hot/rpc.md) 的 **P-HOTRPC-13 / 14 / 15**：

1. **P-HOTRPC-13**：清 Redis 必须在旧进程**真死之后** ⇒ 否则旧进程把旧 sketch 写回来 ⇒ 新进程回灌 ⇒ **每个文章热度恰好 ×2**（实测 60 vs 120），而**条目集合 / 顺序 / 条数全对** ⇒ 极易误读成「权重配置错」。
2. **P-HOTRPC-14**：用**残缺的编译产物**启动 ⇒ `HotMergeScheduler` / `HotServiceGrpcImpl` **根本没被加载**（`jcmd <pid> GC.class_histogram` 可查）⇒ 合并器未装配、热榜恒空、**零日志** ⇒ 一度被误判成「合并逻辑有漂移」。
3. **P-HOTRPC-15**：Kafka `console-producer` 的 `parse.key` 是 **reader 属性**（`--property`），用 `--producer-property` 或 JVM 系统属性都**静默无效** ⇒ 整行进 value、key=null ⇒ 消费者全丢，且**全部消息落进同一分区**。

## 七、对「最终判定」表的影响（⚠️ 该表是第一轮的旧状态）

`docs/DRIFT/hot/rpc.md` §10 的 18 项 checklist 与其下的「判定依据」**写于第一轮**，
其中 item 1 / 2 / 18 的依据已被后续轮次**推翻**（001/002/003 均已修），
而本轮 D1 又把「进程能否跑起来」「正常路径」从**静态推断**升级为**活体实测**。
⇒ **§10 逐条订正见本轮末尾的「§10 复核批注」。**

---

# 第十二轮：认领并修复 `DRIFT-COMMON-004` —— hot-rpc 的 gRPC 拦截器从未挂到 server 上（2026-10-25）

> **本轮性质**：**域内修复**（不是纯台账核对）。基线 = `do-my-own-gh` @ `46363ce`。
> **真源**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`（冻结，只读）。
> **核对日期**：2026-10-25（真实执行：`mvn -o -pl service/hot/hot-rpc test` + 两个守门脚本 + **两轮变异**）。

## 本轮一句话

**hot 域此前三份台账（`hot/api.md` / `hot/rpc.md` / `hot/common.md`）对这条缺口「零提及」** ——
它只被记录在**别的域**的台账里（`docs/DRIFT/common/alignment-2026-10-23.md` 的 `DRIFT-COMMON-004`，
该处明确判「归属 `align/hot`，本文件只登记证据，不越界修改」）。
⇒ **本轮由 hot 域正式认领并修掉。**

## 一、缺口本体（H2 形态在 hot 域复发）

| | |
|---|---|
| **编号** | `DRIFT-COMMON-004`（common 域登记 ⇒ **本轮由 hot 域认领**） |
| **Go 出处** | `service/hot/rpc/hot.go:42`：`s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))`<br>另 `hot.go:31` `s := zrpc.MustNewServer(c.RpcServerConf, …)` ⇒ Go 侧由 zrpc **框架级**补上 Trace → Recover → Stat → Prometheus → Breaker → Shedding（消费点 `go-zero/zrpc/server.go:144-163`） |
| **RAG 落点（修前）** | `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/HotRpcApplication.java` —— 有 `@Import(RpcServerInterceptorFactory.class)` ✅，**全文无 `GrpcServerConfigurer`**、**无 `server.intercept(...)`** |
| **机制** | ① 齐了（Bean 存在）② 缺（框架不用它）⇒ **正是 H2 的原始形态**（与 2026-10-23 在 `user-rpc` / `admin-rpc` 上判定的形态完全同款） |
| **可观察面（全部静默）** | ① `interceptCall` / `onHalfClose` **从未执行** ⇒ gRPC 无 span；② **无 deadline 传播** ⇒ handler 线程上 `DeadlineState` 恒未设置 ⇒ 业务层 `checkDeadline()` 全是死代码；③ `DeadlineQueryTimeoutInjector` 因拿不到 `DeadlineState` 而**不给 SQL 设 `queryTimeout`**；④ gRPC 本身无超时（客户端只能等 api 层 `http-timeout`） |
| **额外证据** | 🔴 hot-rpc **此前无任何 interceptor 接线测试**（其余 9 个 rpc 模块都有 `*GrpcInterceptorWiringTest` 一类）⇒ **缺测试 + 缺接线，绿灯不构成任何保证** |
| **实测守门** | `tools/difftest/check-grpc-interceptor-wiring.py` 修前 **`EXIT=1`**，精确指向 `service/hot/hot-rpc`；修后 **`EXIT=0`**（10/10 rpc 进程接线完整） |

## 二、修法

```java
@Bean
public GrpcServerConfigurer rpcInterceptorConfigurer(ServerInterceptor rpcUnaryServerInterceptor,
        GoStyleProtectionProperties protectionProps) {
    return server -> server.intercept(
            GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps));
}
```

外加 `@Import(GoStyleProtectionConfiguration.class)`（`GoStyleProtectionProperties` 的注册载体 ——
`com.rag.common` 已不被 rpc 进程全包扫描，见 §6.3 问题 3）。

**为什么走共享的链而不是就地拼**（DRIFT-MSG-V-026）：熔断 / 降载在 Go 侧是 **zrpc 框架级**能力
（每个 rpc server 都自带）⇒ 链的定义点收回共享模块 `com.rag.common.protection`，各组件只负责「挂上去」。
⇒ hot-rpc 至此与其余 9 个 rpc 组件**接法完全一致**。

## 三、守门测试（新增 `HotGrpcInterceptorWiringTest`，3 条）

> **判据设计**：断言的是**运行时后果**，不是「Bean 存在」（§3.3）。

| 用例 | 断言什么 | 怎么区分 |
|---|---|---|
| `serverSideTimeoutIsActuallyEnforced` | 服务端超时真的把请求掐断 | 起**真 gRPC server**，挂上容器里那个 Configurer，注册「睡 10 秒」的服务，客户端给 **5s** deadline<br>⇒ 接上：约 **300ms** 返回 `DEADLINE_EXCEEDED`；没接上：等满 **5000ms** 由客户端 deadline 超时<br>**两者 status 码都是 `DEADLINE_EXCEEDED` ⇒ 必须再断言耗时才能区分** |
| `deadlineIsPropagatedToHandlerThread` | deadline **传播**到 handler 线程（`onHalfClose` 那一半） | 在 handler 内部读 `DeadlineState.remainingMillis()`：未接上恒为 `Long.MAX_VALUE`，接上则 ≤ 服务端超时 |
| `productionTimeoutMatchesGoZeroDefault` | 生产 `application.yml` 的 `observability.rpc-timeout: 2s` | Go 侧 `hot.yaml` 未配 Timeout ⇒ 走 go-zero `zrpc/config.go` 的 `default=2000ms` |

⚠️ 两条行为用例的**timeout 压到 300ms**、客户端 deadline 给 5s ⇒ 两者差距足够大、不受机器负载影响。
⚠️ 测试里**关掉 CPU 自适应降载**（`rag.protection.cpu-shedding.enabled=false`）：否则高负载机器上
链上的 shedding 层会先返回 `RESOURCE_EXHAUSTED`，把「超时是否生效」这个判据盖掉 ⇒ 变成 flaky。
**这是测试专用覆盖，不改生产默认值**（默认 `true` 是对齐 Go `zrpc` 的 `Middlewares.Shedding default=true`）。

## 四、变异验证（**两档**，全部按预期变红 ⇒ 守门非假绿）

| # | 变异 | 结果 |
|---|---|---|
| **MUT-1** | 整个 `@Bean GrpcServerConfigurer` 注释掉 | ✅ **2 条转红**（`NoSuchBeanDefinition: rpcInterceptorConfigurer`）—— 只证明「Bean 缺失会被抓到」，**较弱** |
| **MUT-2** | **Bean 保留，但方法体改成 `return server -> { };`**（即**不调 `server.intercept(...)`**） | ✅ **2 条转红，且都是行为断言**：<br>① `serverSideTimeoutIsActuallyEnforced` —— 实际耗时 **5014ms**（只剩客户端 deadline），断言要求 < 3000ms<br>② `deadlineIsPropagatedToHandlerThread` —— handler 线程（`grpc-default-executor-1`）上 `DeadlineState` 恒未设置<br>⇒ **MUT-2 才是 H2 的精确形态**（Bean 在、starter 不用它）⇒ 守门网抓的确实是**机制没生效**，不是「Bean 不存在」 |

⚠️ **MUT-2 才是有意义的那一档** —— 若只做 MUT-1，守门网仍可能「只学会了断言 Bean 存在」。

## 五、🔴 连带查出：裸 `ServerCallHandler` 注册到 unary 方法上**根本不会被 dispatch**

**实测（2026-10-25，写本守门测试时踩到）**：

| 写法 | 方法体是否执行 |
|---|---|
| 自己写一个裸 `ServerCallHandler`，`ServerServiceDefinition.builder(...).addMethod(md, handler)` | ❌ **一次都不执行**（`onHalfClose` 不被派发给它） |
| `io.grpc.stub.ServerCalls.asyncUnaryCall((req, obs) -> { … })` | ✅ 正常执行（走 `ServerCalls.UnaryServerCallListener.onHalfClose()`） |

**为什么这条特别危险**：**服务端超时照样会生效**（超时由拦截器的调度器把 call 关掉，**与 handler 有没有跑无关**）
⇒ 所以「超时是否生效」这类断言在**裸 handler 写法下依然是绿的** ——
⇒ 这类断言**证明不了「handler 真的被调用过」**。

⚠️ **同款写法在别处**：[`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskGrpcInterceptorWiringTest.java`](../../../service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskGrpcInterceptorWiringTest.java)
的 `slowTaskService()` 用的正是裸 `ServerCallHandler` ⇒ **它的「睡 10 秒的服务」实际从未睡过**
⇒ 该用例的断言（`elapsed < 3000`）**依然有效地证明了「服务端超时生效」**（这一条它确实证明了），
但**证明不了「handler 被调用过」**、也**证明不了「deadline 传播到了 handler 线程」**。
⇒ 归属 `task` 域，本轮**不越界修改**；建议该域把裸 handler 换成 `ServerCalls.asyncUnaryCall`
并补一条「handler 被调用过」的断言。

## 六、连带查出的第二件事：6 个 api 模块的 jar **不可执行**（跨域，已修）

`tools/difftest/check-jar-executable.sh` 修前 **`EXIT=1`**：
`article-api` / `comment-api` / `follow-api` / `hot-api` / `like-api` / `task-api` **产物全部没有 `Main-Class`**
⇒ `java -jar` 直接报「中没有主清单属性」⇒ **这 6 个模块从来没被打成过可部署产物**。
根因：pom 里**没有 `<build>` 段** ⇒ 无 `spring-boot-maven-plugin` 的 `repackage` ⇒ 只出普通 library jar。
详见 [`docs/DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-030`（登记方在 message 域）。

## 七、测试与守门

| 项 | 结果 |
|---|---|
| `mvn -o -pl service/hot/hot-rpc test` | ✅ **89 tests / 0 failures / 0 errors / 0 skipped**（原 86 ⇒ **+3**） |
| `tools/difftest/check-grpc-interceptor-wiring.py` | ✅ **`EXIT=0`**（修前 `EXIT=1`）—— 10/10 rpc 进程接线完整 |
| `tools/difftest/check-jar-executable.sh` | ✅ **`EXIT=0`**（修前 `EXIT=1`）—— 10/10 api 产物可执行 |
| 变异验证 | ✅ **MUT-1 / MUT-2 两档全部按预期变红** |
| ⚠️ **全量 `mvn -o -T1C test` 首轮** | 🔴 **在 hot-rpc 中断**：`HotKafkaConsumerGateTest` 的 Kafka 容器 **启动超时**（`Timed out waiting for log output matching '.*Transitioning from RECOVERY to RUNNING.*'`）⇒ 后续模块 `SKIPPED`。<br>**归因**：**并行下的 Testcontainers 资源竞争**（机器上还挂着 12+ 个历史探针容器），**不是本轮改动** —— 单跑 `mvn -o -pl service/hot/hot-rpc test` **89 / 0 / 0 / 0 全绿**（`HotKafkaConsumerGateTest` 4 条照常跑）。<br>⇒ 续跑用**串行** `mvn -o -rf :hot-rpc test` 补齐。<br>⚠️ **这条与 `docs/Pitfalls/_cross/` 里的「容器起不来 ≠ 产品缺陷」是同一类**，见下方 §九 |

## 九、⚠️ 一条**编排级**观察：全量并行跑时，Testcontainers 容器可能起不来（不是产品缺陷）

**现象**：全量 `mvn -o -T1C test` 在 `hot-rpc` 中断，错误是
`Container startup failed for image apache/kafka:3.7.2` ⇒
`Timed out waiting for log output matching '.*Transitioning from RECOVERY to RUNNING.*'`
⇒ reactor 报 `BUILD FAILURE`，**下游模块全部 `SKIPPED`**。

**归因（已实测排除本轮改动）**：
- 单跑 `mvn -o -pl service/hot/hot-rpc test` ⇒ **89 / 0 / 0 / 0 全绿**，
  `HotKafkaConsumerGateTest` 的 **4 条照常跑**（19.91s，容器正常起来）⇒ **不是代码问题**；
- 失败那次的容器是**起来了但没在超时内切到 RUNNING** ⇒ 典型的**并行资源竞争**
  （`-T1C` 同时拉起多个 PG + Kafka 容器；且机器上还挂着 12+ 个历史探针容器未回收）。

⇒ **判据**：遇到「容器起不来」先**单模块复跑**再定性 —— 别把它当成回归。
⚠️ 这与 `docs/Pitfalls/hot/rpc.md` 的 **P-HOTRPC-13 / 14 / 15** 属同一类「**编排级坑会伪装成产品缺陷**」。

## 八、对「组件判定」的影响

`docs/DRIFT/hot/rpc.md` §10 的组件判定**不变**（本轮修的是**装配缺口**，不是行为语义差异），
但**证据强度**上升一档：

| 项 | 修前 | 修后 |
|---|---|---|
| gRPC 拦截器是否真挂上 server | ❌ **根本没挂**（H2） | ✅ **已挂**，且有**行为级**守门 + 两档变异 |
| gRPC deadline 传播 | ❌ **恒不生效**（`DeadlineState` 从未设置） | ✅ **已生效**，行为级断言钉住 |
| SQL `queryTimeout` 注入 | ❌ 因拿不到 `DeadlineState` 而**不注入** | ✅ 随之恢复 |
| 组件判定 | `NOT EQUIVALENT`（域外三条 D21/D22/D23 + D14/D25/D27） | 🟥 **2026-10-26 订正**：这句**已过期** —— **D21/D22/D23 第十三轮已落地 10/10** · **D14 已实现**（`HttpMiddleware.java:692` 的 nosniff）· **D25 已闭合**（T-13）· **D27 已签字为有意偏离**（`SD-2026-10-25-01`）⇒ **这 6 条不再是「域外未闭合项」** ⇒ 本条**不是**域内漂移，是**装配缺口** |
---

## 九、🔴 Redis **TCP 连接超时**漂移（Go `DialTimeout` **5s** vs Lettuce 默认 **10s**）—— 2026-10-27 已修

### 9.1 漂移事实（修前）

| 项 | Go（真源，唯一基准） | RAG 修前 | RAG 修后 |
|---|---|---|---|
| **TCP 连接**超时<br>（`DialTimeout` / `SocketOptions.connectTimeout`） | **5s** | 🔴 **10s** | ✅ **5s** |
| 命令超时<br>（`Read/WriteTimeout` / `commandTimeout`） | **3s** | ✅ 3s | ✅ 3s |

**Go 出处（消费点，不是声明点）**：

```
service/hot/rpc/internal/svc/service_context.go:17-19
    rds := redis.NewClient(&redis.Options{Addr: c.RedisConf.Host, Password: c.RedisConf.Pass})
    ← **只设了 Addr + Password** ⇒ DialTimeout 未设
  → go-redis v9.20.0 options.go:338-340
    if opt.DialTimeout == 0 { opt.DialTimeout = 5 * time.Second }
    ⇒ DialTimeout = **5s**
    （ReadTimeout/WriteTimeout 同理走 options.go:364-379 ⇒ 3s）
```

**RAG 侧为什么「不写就错」**（Spring Boot **4.0.8**，L3 字节码实测）：

| 事实 | 证据 |
|---|---|
| `spring.data.redis.connect-timeout` **非空时**才设 `SocketOptions.connectTimeout` | `LettuceConnectionConfiguration.createClientOptions` 字节码 offset 9→31：`getConnectTimeout()` → `SocketOptions.builder().connectTimeout(...)` → `ClientOptions.Builder.socketOptions(...)` |
| **不写 ⇒ 整段不设** ⇒ 落回 Lettuce 默认 | lettuce-core 6.8.2 `SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10`（秒） |
| `spring.data.redis.timeout` 只喂**命令**超时，**不再**兼管连接超时 | 同一自动配置里 `getTimeout()` → `LettuceClientConfigurationBuilder.commandTimeout(...)` ⇒ **Boot 2/3 才是同一个属性** |
| 该属性确实存在（不是臆造的名字） | `spring-boot-data-redis-4.0.8.jar` 的 `spring-configuration-metadata.json`：`spring.data.redis.connect-timeout`（defaultValue = None） |

⇒ **修前漂移 = 2 倍**。

> 🔴 **本条在 hot-rpc 的可观察性最强**：hot-rpc 是**纯 gRPC 进程**（**无 HTTP 预算兜底**），
> 且它的 Redis 路径就是 `get_hot_articles_logic.go:43` 的
> `RedisClient.ZRevRangeWithScores(...)`（Top-K 读）⇒ 连接超时 10s vs 5s
> **直接**体现在 gRPC 调用耗时上。

### 9.2 修法与守门

| 项 | 内容 |
|---|---|
| 修法 | `application.yml` 的 `spring.data.redis` 下显式加 `connect-timeout: 5s`（**域内文件 ⇒ 域侧自修**，不依赖共享层） |
| 为什么共享层不参与 | `service/common` 的 `GoStyleRedisClientAutoConfiguration` **只**设 `disconnectedBehavior(REJECT_COMMANDS)`、**不碰** `socketOptions` / `timeoutOptions`（其 javadoc「为什么不动命令超时」段已写明）⇒ 连接超时**完全由本域 yml 决定** ⇒ **不装副本、不重复收编** |
| 守门 | 新增 1 个契约守门（**1 例**）—— 走**整条真实链路**：`ConfigDataApplicationContextInitializer` 加载本模块**真实的** `application.yml` → Boot **真实** `DataRedisAutoConfiguration`（它 `@Import` 了 `LettuceConnectionConfiguration`）→ 真 `LettuceConnectionFactory` → 断言**消费点** `getClientConfiguration().getClientOptions().get().getSocketOptions().getConnectTimeout()` 与 `getClientConfiguration().getCommandTimeout()` |
| 为什么断言「消费点」而不是「yml 里有这个键」 | 后者挡不住「属性名写错 ⇒ Spring 静默用默认值」（§3.3.1） |

### 🧪 变异验证（防假绿）

| 步骤 | 结果 |
|---|---|
| 本轮跑守门 | ✅ **1/1 绿** |
| **变异**：删掉 `connect-timeout: 5s` 一行（其余不动） | 🔴 **精确转红**：报「**expected: 5S but was: 10S**」⇒ 守门**真的**在读 Lettuce 的 socket 连接超时，不是恒绿 |
| 还原 | ✅ **1/1 绿** · `sha256` 与变异前备份**逐字一致** |
| 整域回归 | ✅ **四域 12 模块 = 919 条**（article-common 15 / article-api 99 / article-rpc 155 / comment-common 27 / comment-api 52 / comment-rpc 116 / favorite-common 29 / favorite-api 105 / favorite-rpc 114 / hot-common 14 / hot-api 96 / hot-rpc 97）⇒ **0 failures / 0 errors / 4 skipped** · 9:44 min · BUILD SUCCESS<br>口径：`mvn -o --fail-at-end -pl <12 模块> -am test`；基线 `eb4d1d2` + 本轮改动；核对日期 **2026-10-27**；两口径（模块汇总行求和 ≡ 逐类明细行求和）**逐模块一致** |

**证据强度**：L3 静态实测（Boot 4.0.8 **字节码** + Lettuce 6.8.2 常量 + go-redis v9.20.0 源码）
＋ L4 **真容器**装配断言（`ApplicationContextRunner` + Boot 真实 `DataRedisAutoConfiguration`）。
---

### 附录 · 📌 G4 侧共享层基线与影响面订正（2026-10-27 · 来自 G4 第七次全量回归）

**① G4 第七次全量回归口径**（**共享层基线** ⇒ 本域下次合并 G4 分支时的对照物）：

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| 结果 | **BUILD SUCCESS / MVN_EXIT=0** |
| 规模 | **52 模块 · 2457 条**（29 个有测试的模块**逐模块求和**，不按行序 tail） |
| 计数 | **failures 0 · errors 0 · skipped 4** |
| 耗时 | **20:28 min** |
| 相对上次 | 第五次 2448 ⇒ **+9**（本轮新增 3×3 条守门：关闭路径 / 遮蔽契约 / 池自愈） |
| 双侧探针 | `run-parity.sh` **EXIT=0**（56 例：一致 54 / 不一致 0 / 已签字偏离 2）＋ `run-rpc-timeout-parity.sh` **EXIT=0**（3 例） |
| 三门禁 | `wave-lane-ownership` / `wave-role-model` / `wave-coupling` ⇒ **0/0/0** |
| 来源 | G4 commit `5564c27`（基线含单 4 的 `a1a2e05`） |

**② 🟡 影响面订正注记（**保留原值不改写** + 加注记）**：
G4 在共享层 `docs/DRIFT/global/vs-common.md` §2 的 **A2 行**逐个 rpc **自核**（`@GrpcClient` 计数 ＋
**六种出向方式**交叉核：`newBlockingStub` / `newFutureStub` / `newStub` / `ManagedChannel` /
`RestTemplate` / `WebClient`）后给出**全局**口径：

| 口径 | 内容 |
|---|---|
| **全局**（G4 自核） | **可观测 6 个**（article-rpc · comment-rpc · favorite-rpc · like-rpc · message-rpc · follow-rpc）＋ **N/A 4 个**（**hot-rpc** · task-rpc · user-rpc · admin-rpc）⇒ **6/10 可观测 ＋ 4/10 N/A** |
| **本域面**（G3 自核） | **3/4**（article-rpc · comment-rpc · favorite-rpc 有出向面；**hot-rpc = 0 ⇒ N/A**） |

⇒ ⚠️ **本域面 ≠ 全局面**：**「3/4」只覆盖本域四个 rpc 组件** ⇒ **不得被读成全局口径**。
⇒ **可复用判据**：「**共享层缺位**」是**全域事实**，「**本域受害**」是**逐域事实** ⇒
写影响面必须**逐域实测**（并用**多种检索方式交叉核**），且**不得把域内结论外推**。

**③ G4-4 反馈单已闭合**（本域 2026-10-27 登记的共享层 U+FFFD）：
G4 commit `0c72969`「修 `service/common` 两处 UTF-8 替换字符（U+FFFD）⇒ 纯注释，零行为变化」
⇒ **本域的反馈单已落、零行为变化** ⇒ **无需再跟进**。
---

### 附录 · 📌 待验：rpc 侧 Redis 断连的**对外后果与 api 侧不同**（2026-10-27 · G1 通报 ＋ 本域自核）

- **G1 的形态**（users 域已修并立了 2 例守门）：rpc 侧 Redis 断连 ⇒ 命令**排队到 `commandTimeout: 3s`**
  ≫ rpc 组件的 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 **1015「服务繁忙」**；
  而 Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」` ⇒ ⇒ **api 侧与 rpc 侧后果不同**（503 vs RPC 撞超时）
  ⇒ ⇒ **共享层收编时两侧要分别验，别只验 api 侧**。
- **本域自核 ⇒ 同款形态成立**：
  - **comment-rpc**：Redis 调用在 **gRPC 请求路径**（`CommentCacheService` 的 `opsForValue()` 读写）⇒ 断连 ⇒ 排队到 3s
    > `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 与 Go 的快速失败**后果不同** ⇒ 🔴 **待验**。
  - **hot-rpc**：`get_hot_articles_logic.go:43` 的 `ZRevRangeWithScores` 在请求路径 ⇒ 同款 ⇒ 🔴 **待验**。
  - ⚠️ **`mqs/hot_handler.go` 的 MQ 消费路径不属这一族**（ctx = Kafka 消费 ctx / `context.Background()`）⇒ **不修**。
- **修法落点 = 共享层**（`service/common/.../redis/GoStyleRedisClientAutoConfiguration` 的 `REJECT_COMMANDS`）
  ⇒ ⇒ **本域不装副本**（域内副本会**遮蔽**共享层那份 ⇒ **收编根本没发生**）
  ⇒ ⇒ **等 G4 合进我树后**，按 G2 的配方做**断连活体对拍**（先连上 → 静默等待且**期间不发命令** → 外部 `docker stop`
  ⇒ 量第一条命令挂多久）⇒ **本域零域侧改动**；⚠️ 但**「两侧后果分别验」要落进验的清单**，不能只验 api 侧。
- ✅ **本域无遮蔽风险（已自核）**：`grep -rn "LettuceClientOptionsBuilderCustomizer|RedisDisconnectedBehaviorConfig|DisconnectedBehavior"`
  在 `service/{favorite,comment,hot,article}` 的**源码**里 ⇒ **0 命中** ⇒ 共享层那份**必被创建**
  （三模块都引 `spring-boot-starter-data-redis` ⇒ `@ConditionalOnClass` 命中）⇒ **零域侧动作** ✅
