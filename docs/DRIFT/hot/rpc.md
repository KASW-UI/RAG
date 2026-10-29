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

### 6.6-bis 📌 **订正（2026-10-28 · G3）**：「Redis 连接超时 = OBSERVABLE DIFFERENCE」**在本域已不成立**

**原文保留**（不改写）：上表「Redis 连接超时」一行写的是
「go-redis `DialTimeout=5s` vs Lettuce `connectTimeout` 默认 10s（`application.yml` 未配）⇒ **OBSERVABLE DIFFERENCE（低）**」。

**📌 订正**：本域 `application.yml` **早已显式配 `connect-timeout: 5s`**
（落码 commit **`0c6295c`**，2026-10-27）⇒ ⇒ **第四轮那条「未配 ⇒ 落 Lettuce 默认 10s」的取证
对当前代码已过期** ⇒ ⇒ **本域运行期连接超时 = 5s = Go 的 `DialTimeout`** ⇒ ⇒ **该差异在本域不存在**。

⇒ ⇒ **证据（本轮复验，不是转述）**：

| # | 证据 | 内容 |
|---|---|---|
| ① | **域侧 yaml**（落码 `0c6295c`） | `service/hot/hot-rpc/src/main/resources/application.yml` ⇒ `spring.data.redis.timeout: 3s` ＋ **`connect-timeout: 5s`** |
| ② | **守门（真容器 ⇒ 断言消费点）** | `HotRpcRedisTimeoutContractTest` ⇒ **真容器**（真实 yml）⇒ 真 `LettuceConnectionFactory` ⇒ 断言 **`ClientOptions.getSocketOptions().getConnectTimeout()` == 5s** ＋ `getCommandTimeout()` == 3s ⇒ ⇒ **变异 3/3 精确变红**（`expected: 5S but was: 10S`）⇒ ⇒ **判据活着**（不是恒真）|
| ③ | **版本差订正** | G4 报的是 **Boot 4.0.3 / lettuce-core 6.1.8** ⇒ ⇒ **我树是 Boot 4.0.8 / lettuce-core 6.8.2.RELEASE**（`spring-boot-dependencies-4.0.8.pom` 的 `<lettuce.version>`）⇒ ⇒ **两个版本都验过**：lettuce `SocketOptions` 静态块 `ldc2_w long 10l → Duration.ofSeconds` ⇒ `DEFAULT_CONNECT_TIMEOUT_DURATION = 10s` ✔；Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` offset 9→31 = `getConnectTimeout()` → `SocketOptions.builder().connectTimeout(...)` ⇒ **只在属性非空时才设** ✔ ⇒ ⇒ **结论在两版本都成立 ⇒ ⇒ 是基线过期，不是版本差异** |

⇒ ⇒ **对 G4 第七封派单（R5/R6/R7）的答复**：**三条都已在各自域侧闭合**
（hot `0c6295c` ＋ `HotRpcRedisTimeoutContractTest` · comment `CommentRpcRedisTimeoutContractTest` ·
favorite `FavoriteApiRedisTimeoutContractTest`）⇒ ⇒ **不是新缺陷 ⇒ ⇒ 不重做、不重派**。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/domain/HeavyKeeper.java:289`（`return (int) Long.remainderUnsigned(h, (long) width);` ⇒ 与 Go 的 `uint64 % width` 逐条等价）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/domain/HeavyKeeperGoParityTest.java`（对拍式守门，5 例，在仓）· Go `service/hot/heavykeeper/heavykeeper.go:189`（`return int(h.Sum64() % uint64(hk.width))`）· 核对日期 2026-10-30。
> ⚠️ **本条 `###` 标题里的 🔴 = 严重度**（按本文件 `:949-959` 的图例）⇒ 修复合灭它、严重度不变。

> 📌 **2026-10-29 对齐（台账对齐 phase · W-E lane）**：本标题里的 **🔴 = 严重度**（差异本身的量级，**按 D5 原样保留**）⇒ ⇒ **当前状态 = ✅ 已修**（`Long.remainderUnsigned`；【双侧活体对拍】2025/2025 列号一致 + Top-100 集合与分数逐条一致；变异 **5/5 全红**）⇒ 权威条目见本文件下方 `## DRIFT-HOTRPC-001 ✅ 已修` 小节与「逐条处置」表。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修（上一轮修复轮已补 Jackson 2 Bean）** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/config/Jackson2ObjectMapperConfiguration.java:1`（Jackson 2 `ObjectMapper` 的 `@Bean` **在位** ⇒ Boot 4 的 Jackson 3 自动配置不再顶掉）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:70`（`jackson2ObjectMapperBeanExists`）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:158`（`hotEventConsumerIsWiredWithThisMapper`）· Go `service/common/observability/mq.go:30`（Go 侧 `encoding/json` 是语言内建、无 DI ⇒ 本条**无对应物**）· 核对日期 2026-10-30。
> ⚠️ **本条 `###` 标题里的 🔴 = 严重度 +「已实测（启动失败）」= 历史事实**（按 D5 原样保留）⇒ 当前状态看下方 `##` 收口标题与「逐条处置」表。

> 📌 **2026-10-29 对齐（台账对齐 phase · W-E lane）**：本标题里的 **🔴 = 严重度 + 「已实测（启动失败）」= 历史事实**（按 D5 原样保留）⇒ ⇒ **当前状态 = ✅ 已修**（上一轮修复轮已补 Jackson 2 Bean ⇒ `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/config/Jackson2ObjectMapperConfiguration.java` 在；本轮 `HotRpcSpringContextWiringTest` **6/6** 仍绿；**2026-10-29 复核该类文件仍在**）⇒ 权威条目见本文件下方 `## DRIFT-HOTRPC-002 ✅ 已修` 小节与「逐条处置」表。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/resources/application.yml:142-144`（`spring.task.scheduling.pool.size: 2` **在位**）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:191`（`taskSchedulerHasAtLeastTwoThreads` ⇒ 断言**调度器真实线程池大小**，消费点生效）· Go `service/hot/rpc/internal/mqs/hot_handler.go:76-77`（`go h.periodicSync(...)` + `go h.periodicMerge(...)` ⇒ 两条**独立** goroutine）· 核对日期 2026-10-30。
> ⚠️ **指针订正（行号漂移，原值保留）**：本条正文写的 RAG 侧 `HotRedisSnapshotStore.java:230` / `HotMergeScheduler.java:146` ⇒ 实测两处 `@Scheduled` 分别落在 `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotRedisSnapshotStore.java:241` 与 `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotMergeScheduler.java:148` ⇒ **语义不变**。

> 📌 **2026-10-29 对齐（台账对齐 phase · W-E lane）**：本标题里的 **🟠 = 严重度**（按 D5 原样保留）⇒ ⇒ **当前状态 = ✅ 已修**（`service/hot/hot-rpc/src/main/resources/application.yml:142-144` `spring.task.scheduling.pool.size: 2` + 装配层断言真实线程池 ≥ 2；**2026-10-29 复核该配置仍在**）⇒ 权威条目见本文件下方 `## DRIFT-HOTRPC-003 ✅ 已修` 小节与「逐条处置」表。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（不复刻）· 严重度 MEDIUM → LOW** ⇒ **现行态 = 📝 已签字（不复刻）**（未改判；**签字口径不变**）。
> 依据：Go `service/hot/rpc/internal/svc/service_context.go:17-20`（`redis.NewClient(&redis.Options{Addr, Password})` ⇒ **只给两项** ⇒ `MaxRetries` 走 go-redis v9.20.0 `options.go:393-397` 的 `switch opt.MaxRetries { case 0: opt.MaxRetries = 3 }` ⇒ **默认 3**）· RAG `service/hot/hot-rpc/src/main/resources/application.yml:23-28`（Redis 段**只**配 host/port/password/database/timeout ⇒ **无任何命令级重试配置** ⇒ Lettuce **0 次**）· 核对日期 2026-10-30。
> ⇒ ⇒ **「Spring 侧无命令级重试配置入口」仍是事实** ⇒ **维持不复刻** ⇒ 与 `docs/Intentional_Deviations/hot/rpc.md` 的 `SD-HOT-RPC-002`（✅ 仍成立）口径一致。

> 📌 **2026-10-29 对齐（台账对齐 phase · W-E lane）**：本标题里的 **🟠 = 严重度**（按 D5 原样保留）⇒ ⇒ **当前状态 = 📝 已签字（不复刻）** ⇒ **不是「未修的缺陷」**：见 `docs/Intentional_Deviations/hot/rpc.md` 的 **`SD-HOT-RPC-002`**（Lettuce 6.8.2 连 `io/lettuce/core/retry` 包都没有 ⇒ Spring 侧**无配置入口** ⇒ 对齐 Go 等于把重试搬到错误的层）⇒ 权威条目见「逐条处置」表。<br>⚠️ **另有编号订正**：`docs/DRIFT/hot/vs-common.md` 的 **`U4`**（client breaker）**原被误记为 `DRIFT-HOTRPC-004`** ⇒ 已订正 ⇒ **本条只指 Redis 重试那一条**。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **⏸ 保持不修（方向明确、幅度有界）** ⇒ **现行态 = ⏸ 保持不修**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/resources/application.yml:108`（`enable-auto-commit: false`）+ `service/hot/hot-rpc/src/main/resources/application.yml:121`（`ack-mode: record` ⇒ 每条处理完**立刻**提交）· Go `service/hot/rpc/hot.go:55-58`（`sarama.NewConfig()` ⇒ 用库默认值）+ `sarama@v1.50.2/config.go:588-589`（**消费点**：`Consumer.Offsets.AutoCommit.Enable = true` / `Interval = 1 * time.Second`）· 核对日期 2026-10-30。
> ⚠️ **指针订正（行号漂移，原值保留）**：本条正文写的 RAG 侧 `application.yml:41,54` ⇒ 实测 `enable-auto-commit: false` 在 `service/hot/hot-rpc/src/main/resources/application.yml:108`、`ack-mode: record` 在 `service/hot/hot-rpc/src/main/resources/application.yml:121` ⇒ **语义不变**（仍是「逐条提交」）。
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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（RAG 更稳的一侧）** ⇒ **现行态 = 📝 已签字（RAG 更稳的一侧）**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:118-123`（`try { return consumeMessage(...); } catch (RuntimeException e) { return e; }` ⇒ 降级成 drop、位点照常推进、进程不崩）· Go `service/common/observability/mq.go:30`（`err := fn(consumeCtx)` ⇒ **裸的，无 recover**）+ `service/hot/rpc/internal/mqs/hot_handler.go:101-111`（`ConsumeClaim` 所在 goroutine 也无 recover ⇒ panic 直接崩进程）· 核对日期 2026-10-30。
> ⇒ ⇒ **被签字描述的行为仍在** ⇒ 与 `docs/Intentional_Deviations/hot/rpc.md` 的 `SD-HOT-RPC-001`（✅ 仍成立）一致 ⇒ **维持签字**。
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
> 🔴 **2026-10-30 真实性复核（favorite+hot lane）**：原判 **⚪ 证据缺口（`@SpringBootTest` 命中数 = 0 ⇒ 没有装配层测试）** ⇒ **现行态 = ✅ 已补（装配层守门在位）**（**改判**：本条 `###` 标题里的「⚪ 证据缺口」是**历史发现**，现已闭合）。
> 依据：RAG `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:133`（`listenerIsActuallyRegisteredInRegistry` ⇒ 真取 `KafkaListenerEndpointRegistry` 并断言**真有** listener 容器 + groupId 匹配）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:43`（`@SpringBootTest` ⇒ 真装配上下文）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/mq/HotKafkaConsumerGateTest.java:114`（另一条 `@SpringBootTest` 装配层用例）· Go `service/hot/rpc/hot.go:60`（`sarama.NewConsumerGroup(...)` ⇒ Go 侧 listener 由库装配，无等价「自证」需求）· 核对日期 2026-10-30。
> ⇒ ⇒ **本条正文里「`@SpringBootTest` / `ContextConfiguration` / `ApplicationContextRunner` 命中数 = 0 ⇒ 没有装配层测试」已过期**（**原文保留，不改写**）⇒ 与本文件「逐条处置」表的 `✅ 上一轮修复轮已补装配层测试` 口径一致 ⇒ **不是行为漂移，只是证据缺口已补**。
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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 复核通过（全部仍成立）** ⇒ **现行态 = ✅ 仍成立**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotPartitionSketchRegistry.java:136-151`（`detachWithinWriteLock` ⇒ 写锁内**只取引用**、Redis 落盘在锁**外** ⇒ 没有「持写锁再取读锁」）+ `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotRedisSnapshotStore.java:219-221`（非字符串 member ⇒ `continue` ⇒ 不崩）+ `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotMergeScheduler.java:283-284`（`result == null || result == 0L` ⇒ 照抄 Go 的 `result, _`）· Go `service/hot/rpc/internal/mqs/hot_handler.go:132`（`h.hkMu.Lock()`）+ `service/hot/rpc/internal/mqs/hot_handler.go:136`（锁内调 `syncToRedis`）+ `service/hot/rpc/internal/mqs/hot_handler.go:196`（`h.hkMu.RLock()` ⇒ 写锁内再取读锁 ⇒ Go `sync.RWMutex` 不可重入 ⇒ **必然自死锁**）+ `service/hot/rpc/internal/mqs/hot_handler.go:275-278`（`item.Member.(string)` **裸断言** ⇒ 非字符串即 panic）+ `service/hot/rpc/internal/mqs/hot_handler.go:311-315`（`result, _ := renewScript.Run(...).Int()` ⇒ 错误被丢弃 ⇒ 误报「lock lost」）· 核对日期 2026-10-30。

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
| **UNR-3** | Lettuce `connectTimeout` 默认 10s vs go-redis `DialTimeout=5s` | `go-redis@v9.20.0/options.go` `init()`：`DialTimeout = 5s` | `application.yml:28` 只配 `timeout: 3s`，未配 `connect-timeout` | ✅ **已闭合（第四轮取证）**：消费点 `LettuceConnectionConfiguration.createClientOptions`（字节码：`DataRedisProperties.getConnectTimeout()` → `ifnull` ⇒ 未配置时**不调** `SocketOptions.Builder.connectTimeout`）⇒ 落 Lettuce **6.8.2** `SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION = Duration.ofSeconds(10)`（字节码 `ldc2_w 10l → Duration.ofSeconds`） | 连接阶段失败时机不同（低） | ✅ 已完成。**结论：差异确认存在（10s vs 5s），低影响，未修**<br>📌 **订正（2026-10-28 · G3）**：上面那句「**未修**」**已过期** ⇒ ⇒ 本域 yml **已显式配 `connect-timeout: 5s`**（落码 `0c6295c`，2026-10-27）⇒ ⇒ **运行期连接超时 = 5s = Go 的 `DialTimeout`** ⇒ ⇒ **该差异在本域不存在** ⇒ ⇒ **守门**：`HotRpcRedisTimeoutContractTest`（真容器 ⇒ 断言**消费点** `getSocketOptions().getConnectTimeout()` == 5s）⇒ **变异 3/3 精确变红** ⇒ ⇒ 详见 **§6.6-bis** |
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

> 🆕 **2026-10-29 对齐（台账对齐 phase · W-E lane）—— 本表的「上一轮判定」列是【历史值】，不是【当前状态】**
> 
> ⚠️ **同一个编号在本域出现三种 emoji，各有各的字段含义** ⇒ **不是**台账自相矛盾，**别当新回归去「修」**：
> | 位置 | 记的是什么 | 取值 |
> |---|---|---|
> | **上方 `###` 明细标题** | **严重度**（差异本身的量级 —— 「SEMANTIC MISMATCH · HIGH」/「BLOCKING」/「OBSERVABLE DIFFERENCE · MEDIUM」）⇒ **修复合灭它、差异本身仍在 ⇒ 严重度不变** | 🔴 / 🟠 |
> | **下方 `##` 收口标题** | **当前状态** | ✅ 已修 / ⏸ / 📝 已签字 |
> | **本表的第 2 列「上一轮判定」** | **历史值**（按 AGENTS.md D5「保留原值 + 加时间线指针」**原样保留**） | 🔴 / 🟠 / 🟡 |
> | **本表的第 3 列「本轮处置」** | **当前状态**（**权威**） | ✅ / ⏸ / 📝 |
> 
> ⇒ ⇒ **判定「某个编号现在是什么状态」时，一律以「本轮处置」列与下方 `##` 收口标题为准** ⇒ ⇒ 本表**不构成**「同一编号两种状态」。
> 
| ID | 上一轮判定 | 本轮处置 | 证据 |
|---|---|---|---|
| **DRIFT-HOTRPC-001** | 🔴 SEMANTIC MISMATCH | ✅ **已修** | 【双侧活体对拍】2025/2025 列号一致 + Top-100 集合与分数逐条一致；变异 **5/5 全红**<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/domain/HeavyKeeper.java:289`（`Long.remainderUnsigned`）· Go `service/hot/heavykeeper/heavykeeper.go:189`（`uint64 % width`）· 核对日期 2026-10-30。 |
| **DRIFT-HOTRPC-002** | 🔴 BLOCKING（起不来） | ✅ 上一轮修复轮已清（Jackson 2 Bean） | 本轮 `HotRpcSpringContextWiringTest` 6/6 仍绿<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/config/Jackson2ObjectMapperConfiguration.java:1`（Bean 在位）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:70`（守门）· 核对日期 2026-10-30。<br>⚠️ **计数订正（原值保留）**：「**6/6**」是**陈旧计数** ⇒ 实测该类现有 **8 个 `@Test`**（`jackson2ObjectMapperBeanExists` / `unknownFieldsAreIgnoredLikeGoEncodingJson` / `typeMismatchStillFails` / `kafkaListenerContainerFactoryExists` / `listenerIsActuallyRegisteredInRegistry` / `hotEventConsumerIsWiredWithThisMapper` / `grpcHealthStatusManagerBeanExists` / `taskSchedulerHasAtLeastTwoThreads`）⇒ **结论不变**（Bean 仍在 ⇒ 上下文起得来）。 |
| **DRIFT-HOTRPC-003** | 🟠 OBSERVABLE DIFFERENCE | ✅ **已修** | `spring.task.scheduling.pool.size: 2` + 装配层断言真实线程池 ≥ 2<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。依据：RAG `service/hot/hot-rpc/src/main/resources/application.yml:142-144`（`pool.size: 2` 在位）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:191`（断言真实线程池）· Go `service/hot/rpc/internal/mqs/hot_handler.go:76-77`（两条独立 goroutine）· 核对日期 2026-10-30。 |
| **DRIFT-HOTRPC-004** | 🟠 OBSERVABLE DIFFERENCE | 📝 **已签字**（不复刻） | 新证据：**Lettuce 6.8.2 连 `io/lettuce/core/retry` 包都没有** ⇒ Spring 侧**无配置入口**<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（不复刻）** ⇒ **现行态 = 📝 已签字（不复刻）· 严重度 MEDIUM → LOW**（未改判）。依据：Go `service/hot/rpc/internal/svc/service_context.go:17-20`（只给 Addr+Password）+ go-redis v9.20.0 `options.go:393-397`（`case 0: opt.MaxRetries = 3`）· RAG `service/hot/hot-rpc/src/main/resources/application.yml:23-28`（Redis 段无重试配置 ⇒ 0 次）· 核对日期 2026-10-30。 |
| **DRIFT-HOTRPC-005** | 🟡 OBSERVABLE DIFFERENCE | ⏸ **保持不修**（方向明确、幅度有界） | 上一轮结论不变<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **⏸ 保持不修** ⇒ **现行态 = ⏸ 保持不修**（未改判）。依据：RAG `service/hot/hot-rpc/src/main/resources/application.yml:108`（`enable-auto-commit: false`）+ `service/hot/hot-rpc/src/main/resources/application.yml:121`（`ack-mode: record`）· Go `service/hot/rpc/hot.go:55-58` + `sarama@v1.50.2/config.go:588-589`（AutoCommit 默认 true / 1s）· 核对日期 2026-10-30。 |
| **DRIFT-HOTRPC-006** | 🟠 OBSERVABLE DIFFERENCE | 📝 **已签字**（RAG 更稳的一侧） | 见 `docs/Intentional_Deviations/hot/rpc.md`<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（RAG 更稳的一侧）** ⇒ **现行态 = 📝 已签字（RAG 更稳的一侧）**（未改判）。依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:118-123`（`catch (RuntimeException e) { return e; }` ⇒ 降级 drop）· Go `service/common/observability/mq.go:30`（裸 `err := fn(consumeCtx)`，无 recover ⇒ 崩进程）· 核对日期 2026-10-30。 |
| **DRIFT-HOTRPC-007** | ⚪ 证据缺口 | ✅ 上一轮修复轮已补装配层测试 | 本轮复跑仍绿<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **⚪ 证据缺口** ⇒ **现行态 = ✅ 已补（装配层守门在位）**（**改判**）。依据：RAG `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:133`（`listenerIsActuallyRegisteredInRegistry`）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:43`（`@SpringBootTest`）· 核对日期 2026-10-30。 |

---

## DRIFT-HOTRPC-001 ✅ 已修 · SEMANTIC MISMATCH · HIGH
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/domain/HeavyKeeper.java:289`（`Long.remainderUnsigned`）+ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/domain/HeavyKeeperGoParityTest.java`（守门，5 例）· Go `service/hot/heavykeeper/heavykeeper.go:189` · 核对日期 2026-10-30。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/resources/application.yml:142-144` + `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcSpringContextWiringTest.java:191` · Go `service/hot/rpc/internal/mqs/hot_handler.go:76-77` · 核对日期 2026-10-30。
> ⚠️ **指针订正（行号漂移，原值保留）**：RAG 侧两处 `@Scheduled` 实测在 `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotRedisSnapshotStore.java:241` 与 `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotMergeScheduler.java:148`。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（不复刻）· MEDIUM → LOW** ⇒ **现行态 = 📝 已签字（不复刻）**（未改判；签字口径不变）。
> 依据：Go `service/hot/rpc/internal/svc/service_context.go:17-20`（`redis.NewClient(&redis.Options{Addr, Password})`）+ go-redis v9.20.0 `options.go:393-397`（`switch opt.MaxRetries { case 0: opt.MaxRetries = 3 }`）· RAG `service/hot/hot-rpc/src/main/resources/application.yml:23-28`（Redis 段无任何命令级重试配置 ⇒ Lettuce **0 次**）· 核对日期 2026-10-30。
> ⇒ ⇒ **「复刻 Go 的 3 次重试在当前依赖栈下不可达」这一结论仍成立** ⇒ 维持不复刻 ⇒ 与 `docs/Intentional_Deviations/hot/rpc.md` 的 `SD-HOT-RPC-002`（✅ 仍成立）一致。
> ⚠️ **形态订正（原值保留）**：本条正文把 go-redis 的默认值写成 `if opt.MaxRetries == 0 { opt.MaxRetries = 3 }` ⇒ 实测 v9.20.0 的 `options.go:393-397` 是 **`switch`**（`case -1: 0` / `case 0: 3`）⇒ **语义逐字相同**（「未设置 ⇒ 默认 3」），只是**代码形态**表述不准。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（RAG 更稳的一侧）** ⇒ **现行态 = 📝 已签字（RAG 更稳的一侧）**（未改判）。
> 依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:118-123`（`catch (RuntimeException e) { return e; }` ⇒ 降级成 drop、位点推进、分区继续消费）· Go `service/common/observability/mq.go:30`（**裸** `err := fn(consumeCtx)` ⇒ 无 recover）+ `service/hot/rpc/internal/mqs/hot_handler.go:101-111`（claim goroutine 亦无 recover ⇒ panic 直接崩进程）· 核对日期 2026-10-30。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **⚪ 台账/注释写反（非行为漂移）· 已实测证伪** ⇒ **现行态 = ⚪ 台账/注释写反（非行为漂移）· 仍被证伪**（未改判）。
> 依据：Go `service/hot/rpc/internal/mqs/hot_handler.go:227-234`（改名脚本首行 `if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end`）+ `service/hot/rpc/internal/mqs/hot_handler.go:235`（`keys := []string{shadowKey, partitionKey}` ⇒ **`KEYS[1] = shadow`**）· RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotRedisSnapshotStore.java:167-168`（`redis.execute(PARTITION_RENAME_SCRIPT, List.of(shadowKey, partitionKey))` ⇒ 同样 **`KEYS[1] = shadow`** ⇒ **与 Go 一致**）+ 守门 `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/mq/HotRedisLuaGateTest.java:285`（`firstSnapshotIsNotLost`，在仓）· 核对日期 2026-10-30。
> ⇒ ⇒ **「守卫查的是 shadow（不是目标分片 key）⇒ 首次快照不会丢」这个结论两侧都成立** ⇒ **生产逻辑本来就对** ⇒ 本条**不是行为漂移**，维持 ⚪（台账/注释已修正的那一格）。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（保持现状）** ⇒ **现行态 = 📝 已签字（保持现状）**（未改判；本条**原无指针**，本轮补上）。
> 依据：RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:98-100`（`@KafkaListener(topics=…, groupId=…, containerFactory=…)` ⇒ **无 `concurrency` 属性** ⇒ 取默认 **1**）· Go `service/hot/rpc/internal/mqs/hot_handler.go:101-111`（`ConsumeClaim` 是 **claim 级**循环）+ `service/hot/rpc/internal/mqs/hot_handler.go:105-107`（claim 内的消费调用）⇒ sarama **每分区一个 goroutine** ⇒ 单实例持 N 分区时 Go 并行 N 路、RAG 串行 1 路（3 副本部署下每 JVM 恰好 1 分区 ⇒ **等价**）· 核对日期 2026-10-30。
> ⇒ ⇒ **被签字描述的行为仍在** ⇒ 与 `docs/Intentional_Deviations/hot/rpc.md` 的 `SD-HOT-RPC-003`（✅ 仍成立）一致 ⇒ **维持签字**（残留风险「单实例部署」仍挂着，未变）。

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
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **📝 已签字（保持 + 补健康检查）** ⇒ **现行态 = 📝 已签字（保持 + 补健康检查）**（未改判）。
> 依据：Go `service/hot/rpc/hot.go:74`（`serviceGroup.Start()` ⇒ 注册失败即 `panic` ⇒ 进程退出）· RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/bootstrap/NacosRegistration.java:118-126`（`catch (NacosException | RuntimeException e)` ⇒ 只 `LOG.error` + `markHealth(ServingStatus.NOT_SERVING)` ⇒ **进程照常存活**）+ `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/bootstrap/NacosRegistration.java:165`（`markHealth(...)`）+ 守门 `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/bootstrap/NacosRegistrationHealthGateTest.java`（走**真实 gRPC health 协议**，在仓）· 核对日期 2026-10-30。
> ⚠️ **指针订正（行号漂移，原值保留）**：本条正文写的 RAG 侧 `NacosRegistration.java:92-94` ⇒ 实测注册失败的 catch 在 `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/bootstrap/NacosRegistration.java:118-126`（`:92-94` 现在是 `resolveLocalIp()` 的 try 块）⇒ **语义不变**（仍是「只 LOG.error + 进程存活」）。

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
| **DRIFT-HOTRPC-004** | 严重度 **MEDIUM → LOW**（**签字口径不变**：Lettuce 无重试入口仍是事实） | 已就地加复核批注<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **MEDIUM → LOW** ⇒ **现行态 = LOW**（未改判；签字口径不变）。依据：Go `service/hot/rpc/internal/svc/service_context.go:17-20` + go-redis v9.20.0 `options.go:393-397`（`case 0: opt.MaxRetries = 3` ⇒ 默认 **3**）· RAG `service/hot/hot-rpc/src/main/resources/application.yml:23-28`（Redis 段无命令级重试配置 ⇒ **0 次**）· 核对日期 2026-10-30。 |
| **SD-HOT-RPC-002** | 可观察影响清单**新增**一条（Go 的「实际成功却记 fail」） | 已就地加复核批注<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **新增一条可观察影响** ⇒ **现行态 = 仍然成立（差异未变）**（未改判）。依据：Go `service/hot/rpc/internal/mqs/hot_handler.go:235-238`（`result, err := renameScript.Run(...).Int()`；`if err != nil || result == 0` ⇒ `redisSyncTotal.Inc("fail")`）—— **脚本首行 `EXISTS KEYS[1]==0 ⇒ return 0` 在 `service/hot/rpc/internal/mqs/hot_handler.go:228`** ⇒ Go 重试（3 次）时若首次 RENAME 已成功但**回复丢失**，重试再执行 ⇒ `EXISTS shadow == 0` ⇒ 返回 0 ⇒ **实际 RENAME 已成功却记 `fail`**（指标误计）⇒ RAG 侧 Lettuce **无重试** ⇒ 不产生该形态 ⇒ **该条可观察影响仍在**；另 `docs/Intentional_Deviations/hot/rpc.md` 的 `SD-HOT-RPC-002` 条目（`:98-106`）**确实带这条第五轮批注** ⇒ 「已就地加复核批注」这句**成立** · 核对日期 2026-10-30。 <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-envelope-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:17:43 · 30s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✓ Go 侧输出与 expected.txt 逐字节一致（sha256=c5dc8761884acfa0…） ⏐ 2026-10-30T03:17:54.779+08:00  INFO 47811 --- [favorite-api] [.0-21300-exec-2] c.r.common.observability.HttpMiddleware  : [HTTP] 1xx 探针行数预算用尽（cap=4）⇒ 本请求不再写探针字节（客户。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
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
| 组件判定 | `NOT EQUIVALENT`（域外三条 D21/D22/D23 + D14/D25/D27） | 🟥 **2026-10-26 订正**：这句**已过期** —— **D21/D22/D23 第十三轮已落地 10/10** · **D14 已实现**（共享层 `HttpMiddleware.java` 的 `resp.setHeader("X-Content-Type-Options", "nosniff")` ⇒ **锚点 = 调用形态**，**行号以合并后的树为准** —— G1/G3/G4 三树当前均为 **728**）· **D25 已闭合**（T-13）· **D27 已签字为有意偏离**（`SD-2026-10-25-01`）⇒ **这 6 条不再是「域外未闭合项」** ⇒ 本条**不是**域内漂移，是**装配缺口** |
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
| 整域回归（**本轮 · D10 落码后 · 2026-10-27**） | ✅ **四域 12 模块 = 922 条**（article-common 15 / article-api 99 / article-rpc 155 / comment-common 27 / comment-api 52 / **comment-rpc 119**（= 旧 116 ＋ D10 新增 3） / favorite-common 29 / favorite-api 105（skipped 1） / favorite-rpc 114（skipped 3） / hot-common 14 / hot-api 96 / hot-rpc 97）⇒ **0 failures / 0 errors / 4 skipped** · **10:17 min** · BUILD SUCCESS<br>口径：`mvn -o --fail-at-end -pl <12 模块> -am test`；基线 `deeeb8d` + 本轮文档改动；核对日期 **2026-10-27**；两口径（模块汇总行 ≡ 逐类明细行）**逐模块一致**<br>⚠️ **上一行的 919 保留原值不改写**（那是基线 `eb4d1d2` 的口径）⇒ ⇒ **两个数不在同一条基线上 ⇒ 不可相加**；本轮除 comment-rpc 外**其余 11 个模块逐条不变** ⇒ ⇒ 差额 **+3 全部来自 comment-rpc**（D10 新增 3 条守门，commit `deeeb8d`） |
> 🔴 **2026-10-27 · 订正注记（承 G1 的 ③：「0 failures」必须带**跑次与时间戳**，否则下轮会被当现状）**：本行的 **919 / 922 条 ＋ 0 failures / 0 errors** 属于**它标注的那个跑次**（**919 = D10 落码前** · **922 = D10 落码后**）⇒ ⇒ **两个数都不是口径差，是不同跑次** ⇒ **当前数 = 922**（G1 侧「域内 12 模块」口径正确）⇒ ⇒ **但当前不是全绿**。<br>**当前实测（2026-10-27 17:21 · G3 树 · 域内 12 模块 · 129 份 surefire XML 逐文件求和）**：合计 **922 条 / skipped 4** ⇒ **与上表 922 一致** ⇒ ⇒ **但有 3 份报告带失败/错误**：<br>① `17:21:19` `FavoriteApiRealTomcatGateTest`（**3 errors**）＋ ② `17:21:40` `FavoriteApiEnvelopeParityTest`（**1 error**）⇒ 根因 = `java.net.ProtocolException: Too many interim responses received: 9 > 8`（**JDK HttpClient 对 Tomcat 多次 100-Continue 的上限**）⇒ ⇒ **根因在共享层 `cd893d1` 抬容器上限到 10001**（Tomcat 不再在 2048 拒 ⇒ 继续读大 body）⇒ ⇒ **不是域内问题** ⇒ 已转派 G4；<br>③ `13:08:29` `CommentRpcRuntimeBootGateTest`（**1 error** · `realFatJarProcessBootsAndListens`）⇒ ⇒ **就绪探测 flake 族**（article-rpc / comment-rpc / like-rpc 同款）⇒ ⚠️ **与 G1 域那款「连接预算」根因不同 ⇒ 分开记，不并成「同款 flake」**。<br>⇒ ⇒ **读本表的人以本注记为准**：上表的绿是**那一跑次**的绿，**当前域内 12 模块有 5 条红**（favorite 4 条 ＋ comment-rpc 1 条）。

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
- ✅ **前提已坐实（本轮自核 · 补出处）**：上文说的「`rpc-timeout: 2s`」指的是**服务端**超时（不是客户端封顶），
  Go 侧出处 = go-zero v1.10.2 `zrpc/config.go:46`
  `RpcServerConf.Timeout int64 \`json:",default=2000"\`` ⇒ **服务端**默认 2000ms；
  ⚠️ **`:31` 那个 `Timeout` 是 `RpcClientConf.Timeout`（客户端）** ⇒ **两个字段默认同值但语义不同** ⇒ 别混。
  ⇒ Go 侧 `service/hot/rpc/etc/hot.yaml` **零顶层 `Timeout` 键** ⇒ 走默认 **2000ms = 2s** ⇒ 两侧同为 2s。
  ⇒ **真源侧消费点（唯一超时执行者）**：`service/hot/rpc/hot.go:32` `DisableNativeRpcTimeout(&c.RpcServerConf)`
  ＋ `service/hot/rpc/hot.go:42` `NewUnaryServerInterceptor(rpcTimeout, ...)` ⇒ **框架自带超时被关掉、由真源自建接管**。
  ⇒ RAG 侧等价物 = `observability.rpc-timeout` ⇒ 消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:80-81`
  ⇒ **已在 docs/DRIFT/hot/vs-common.md 的 A1 行结案（服务端一致）**。
  ⚠️ **因此**：上文「断连 ⇒ 命令排队到 `commandTimeout: 3s` ≫ `rpc-timeout: 2s` ⇒ **RPC 先超时**」里的 2s，
  指的就是**这个服务端预算** ⇒ **不是**客户端 2s 封顶那一条 ⇒ 别拿客户端那条来解释服务端先超时。

### 附录 · 📌 **订正注记（2026-10-27 · G3 · 20:15）：上面「🔴 待验」的**机制格已验完**（新增 §十二）

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上文「🔴 **待验**」**原样保留** ⇒ 读本域历史结论的人**以两块并列为准**。

- **已验的格**：**「共享层那份 `REJECT_COMMANDS` 在本域真的生效吗」** ⇒ ✅ **已验**（§十二：仓内守门 2 条 ＋ **活体 A/B** 实测 **A=1ms / B=3007ms** ＋ **Go 腿 1669–1808ms**）
- **仍未验的格**：**「断连时客户端（api 侧）到底看到什么」** ⇒ 仍 🔴 **待验**（那是 **gRPC 响应层**的问题：断连 ⇒ 命令是**毫秒级拒单**（不是排 3s）⇒ ⇒ **RPC 不再撞 2s 服务端超时** ⇒ ⇒ 客户端看到的**不再是 1015「服务繁忙」**，而是域内的业务错误 ⇒ **但这条要真发一次 gRPC 调用才测得到** ⇒ **未做**）
- ⇒ ⚠️ **上文的「修法落点 = 共享层」这句现在有了实测支撑**：共享层那份**确实**在 hot-rpc 里生效（无域内副本 ⇒ `size == 1` ＋ bean 名对得上）⇒ ⇒ **hot-rpc 零域侧改动**这条结论成立 ✅。


---

### 附录 · 📌 订正注记（2026-10-27 · G3）：G4 **第八次**全量口径（**保留上文第七次原值不改写**）

**① G4 第八次全量回归口径**（来源 G4 commit `23d1c2a` §4.10.11）：

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| 结果 | **BUILD SUCCESS / EXIT=0** |
| 规模 | **52 模块 · 2461 条**（29 个有测试的模块**逐模块求和**，不按行序 tail） |
| 计数 | **failures 0 · errors 0 · skipped 4** |
| 耗时 | **20:21 min** |
| 相对上次 | 第七次 2457 ⇒ **+4** |
| 双侧探针 | `run-parity.sh` **EXIT=0**（56 例）＋ `run-rpc-timeout-parity.sh` **EXIT=0**（3 例） |
| 三门禁 ＋ 台账三门禁 | **0/0/0 ＋ 0/0/0** |

⚠️ **「+4」的归因是【推断】**：依据 = G4 `23d1c2a` 的 §4.10.9 记「FB-04（A9）落码 … **守门 4 条** / 变异 4-4」⇒ **条数吻合**；
（同轮的 §4.10.10 是 **U+FFFD 纯注释**自修 ⇒ 不产生测试条数。）

⚠️ **口径边界（不可相加）**：**2461 是 G4 树的口径**（基线含 G4 单 4 的 `0c72969`）⇒
**本域的 919 条是 G3 树的口径**（4 域 × 3 组件）⇒ **两个数不在同一条基线上 ⇒ 不可相加**；
合并后总数会变 ⇒ **波末收口回归才是最终口径**。

⚠️ **本块是「加注记」，不是「改原值」**：上文第七次口径（2457 条 / 20:28 min / `5564c27`）**原样保留** ⇒
读本域历史结论的人**以两块并列为准** ⇒ ⇒ **不要把第八次误读成「第七次作废」**。

---

## 十、🔴 Redis **连接池形态**漂移（Go **有池** vs RAG **无池**）—— 2026-10-27 登记（G2 挖出 · 本域**独立复核** · **只登记不落码**）

> **公共证据链（Boot 4.0.8 字节码判据 · classpath 探测 · 处置理由 · 复开触发条件）见 [`favorite/api.md` §15](../favorite/api.md#15-🔴-redis-连接池形态漂移go-有池-vs-rag-无池-2026-10-27-登记g2-挖出--本域独立复核--只登记不落码)** ⇒ 本节**只记本域特有**的部分。

### 十.1 本域结论

| 项 | Go 真源 | RAG（本树 HEAD `18aa012`） | 判定 |
|---|---|---|---|
| **连接池形态** | ✅ **有池** | ❌ **无池**（Lettuce **单连接共享**） | 🔴 **形态漂移** |
| 池容量 | `PoolSize = 10 × GOMAXPROCS(0)`（**运行时值**） | — | ⛔ 不可 1:1 |
| 最小空闲连接 | **= 0**（本域走**直连**路径，**不是** go-zero） | — | ⛔ 不可 1:1 |
| **证据强度** | **L1** | **L3**（字节码 + `dependency:tree` 实测） | ⚠️ 不是 L4（无活体并发对拍） |
| 核对日期 | 2026-10-27 | 2026-10-27 | — |

### 十.2 本域特有证据

1. **Go 侧（直连 go-redis，**不走** go-zero）**：`service/hot/rpc/internal/svc/service_context.go:17-19` `redis.NewClient(&redis.Options{Addr: c.RedisConf.Host, Password: c.RedisConf.Pass})` ⇒ **只设了 Addr + Password** ⇒ `PoolSize` 未设 ⇒ go-redis v9.20.0 `options.go:350-351` ⇒ **`PoolSize = 10 × GOMAXPROCS` 生效**；`MinIdleConns` 未设 ⇒ **= 0**。
   ⚠️ **与 `favorite-api` 的差别**：favorite-api 走 go-zero `MustNewRedis` ⇒ `MinIdleConns = 8`（`redisclientmanager.go:15`）；**hot-rpc 直连 ⇒ = 0** ⇒ ⇒ **Go 侧两个组件的最小空闲连接本来就不一样** ⇒ ⇒ 「RAG 侧该抄哪个」**没有单一答案** ⇒ 又一条「不可逐值对齐」的理由。
2. **Go yaml**：`service/hot/rpc/etc/hot.yaml:19-21` `RedisConf: {Host, Type}` ⇒ **无池字段**。
3. **RAG 侧**：`service/hot/hot-rpc/src/main/resources/application.yml` 的 `spring.data.redis` 段 ⇒ **无 `lettuce` 子段**（实测 grep 0 命中）⇒ `Pool.enabled = null`。
4. **classpath 实测**：`mvn -B -o -pl service/hot/hot-rpc -am dependency:tree -Dincludes=org.apache.commons:commons-pool2` ⇒ EXIT=0 ⇒ **0 命中** ⇒ `COMMONS_POOL2_AVAILABLE = false` ⇒ ⇒ **无池**。
5. **共享层不碰池**：共享层 `GoStyleRedisClientAutoConfiguration` 的**唯一** builder 调用是 `disconnectedBehavior(REJECT_COMMANDS)`（合并后已树内重验）⇒ **不设 `pool.*`** ⇒ ⇒ **池形态完全由域内 yml + 域内 classpath 决定** ⇒ ⇒ **域侧可自修**（不需要派 G4）。

### 十.3 🔴 可观察性：**本域是三个用 Redis 的域里最强的**

- hot-rpc 是**纯 gRPC 进程（无 HTTP 预算兜底）**；
- 且 Redis 路径就是 Top-K 读（`get_hot_articles_logic.go:43` `ZRevRangeWithScores`）⇒ ⇒ 单连接串行化会**直接**体现在 gRPC 调用耗时上；
- 与本域已修的「Redis TCP 连接超时」那条（[`vs-common.md` 的 **H-8**](vs-common.md)）**叠在同一路径上** ⇒ ⇒ 若将来做并发对拍，**池形态会与连接超时两个因素纠缠** ⇒ ⇒ **复开时必须分变量**（先固定连接超时，再动池）。

### 十.4 处置

**只登记，不落码**（理由与复开触发条件见 [`favorite/api.md` §15.3](../favorite/api.md#153-处置只登记不落码写死复开触发条件)）⇒ 本域补充一条：
⚠️ **本域同时承载「命令重试」已登记差异**（go-redis `MaxRetries = 3` vs Lettuce **无重试**，见 [`vs-common.md`](vs-common.md) 的 `DRIFT-HOTRPC-004`）⇒ **改池形态会同时改这两项**（有池 ⇒ 并发下失败重试的分布完全变样）⇒ ⇒ **复开时必须与重试那条一起验**。

### 十.5 📌 **订正注记（2026-10-27 · G3）：本条已复开并落码** —— 上文「只登记，不落码」是**当时**的结论，**原样保留不改写**

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上文 §十.1（池形态 = 无池）与 §十.4（只登记不落码）**原样保留** ⇒
读本域历史结论的人**以两块并列为准** ⇒ ⇒ **不要把 §十一 误读成「§十 作废」**。

- **复开理由**：命中 [`favorite/api.md` §15.3](../favorite/api.md#153-处置只登记不落码写死复开触发条件) 写死的**复开触发条件第 ② 条**
  —— 「**任何**模块的 classpath 上**新增** `commons-pool2` ⇒ 池会**静默打开** ⇒ 必须复验」⇒
  **本轮 G3 自己在 `hot-rpc` 加了 `commons-pool2`** ⇒ ⇒ **触发条件由本域自己命中** ⇒ **必须复验**（不是「顺便修」）。
- **§十.3 提的那条「复开时必须与重试那条一起验」的约束**：本轮**未能**与重试那条一起做活体验证
  （`DRIFT-HOTRPC-004` 的重试面需要**真 Redis 断连** ⇒ ⚠️ **原记的阻塞理由「机器级 Docker 端口转发故障」已于 2026-10-27 19:2x 实测不成立** ⇒ ⇒ **现在只差「排期跑一次断连对拍」**，不是环境问题）⇒
  ⇒ **本轮只验「池形态 + 池参数 + 池耗尽等待上限」三个位** ⇒ **重试分布那条仍挂起** ⇒ 详见 §十一.5。
- **落码与守门** ⇒ 见 **§十一**。

---

## 十一、✅ Redis **连接池形态**漂移 —— **已落码**（2026-10-27 · G3 · 复开自 §十）

> **本节登记编号 = `DRIFT-HOTRPC-011`**（承接 §十 的「只登记」条目，本轮由「登记」推进到「已修」）。
> ⚠️ **§十 原样保留不改写** ⇒ 读本域历史结论的人**以 §十 + §十一 两块并列为准**。

### 十一.1 一句话

**Go 有池、RAG 无池 ⇒ 已修**（加 `commons-pool2` ＋ `lettuce.pool.enabled: true`）⇒ 守门 **4 条** ＋ **变异 5 条全部精确转红** ⇒
🔴 **顺带查出并修掉一个「光加池反而比 Go 更糟」的陷阱：池耗尽时的等待上限（Go 4s vs Lettuce 默认「无限等」）**。

### 十一.2 落码清单（逐个 `文件:行号`）

| # | 文件 | 位置 | 改了什么 |
|---|---|---|---|
| 1 | `service/hot/hot-rpc/pom.xml` | `:117-120` | **新增** `org.apache.commons:commons-pool2`（**不写 `<version>`** ⇒ 由 `spring-boot-dependencies` BOM 管理）⇒ 实测解析到 **2.12.1** |
| 2 | `service/hot/hot-rpc/src/main/resources/application.yml` | `:89-95` | **新增** `spring.data.redis.lettuce.pool`：`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0` / **`max-wait: 4s`** |
| 3 | `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/config/HotRpcRedisPoolContractTest.java` | 新增，**218 行** | 守门 **4 条**（读**真装配**，不读 yml 文本） |

⚠️ **注释里写的是 Boot 4 的**真实**装配点**（不是照抄别处的行号）⇒
Boot 4.0.8 的 `LettuceConnectionConfiguration` **没有** `getPoolConfig` 方法（实测 `javap` 方法清单里没有）⇒
池参数实际在**内部类** `LettuceConnectionConfiguration$PoolBuilderFactory.getPoolConfig` 里建 ⇒
⇒ **本仓多处台账引用的「`LettuceConnectionConfiguration:267-271`」是 Boot 2/3 的旧路径**，在 Boot 4.0.8 上**对不上** ⇒ 已在本域台账按**类名 + 方法名**引用。

### 十一.3 取值逐项对着 Go 的真值（不拿「现有 RAG 代码」当基准 · AGENTS.md §3.1）

| 语义 | Go 真值（消费点） | Lettuce / Boot 4 | 本仓取值 | 对齐度 |
|---|---|---|---|---|
| 有没有池 | ✅ 有 —— `service/hot/rpc/internal/svc/service_context.go:17-20` `redis.NewClient(&redis.Options{Addr, Password})` ⇒ `PoolSize` 未设 ⇒ `go-redis@v9.20.0/options.go:350-351` `PoolSize = 10 * GOMAXPROCS(0)` | `isPoolEnabled` ⇒ `enabled ?? COMMONS_POOL2_AVAILABLE` | `enabled: true` ＋ pom 里有 `commons-pool2` | ✅ **形态对齐**（本条是主体） |
| 总量上限 | 🟡 Go 的 `MaxActiveConns` **未设** ⇒ `options.go:230-235`「When zero, there is no limit」⇒ **Go 无总量硬上限**；Go 的 `PoolSize` = 10×核数（本机 100）⇒ **机器相关，不可 1:1** | `maxTotal` | `64` | 🟡 **数量级**对齐（性能面残留，已登记） |
| 最少空闲 | ✅ `MinIdleConns` **未设** ⇒ `options.go:218-222` **无 default 赋值** ⇒ **0** | `minIdle` | `0` | ✅ **精确对齐** |
| 最多空闲 | 🟡 `MaxIdleConns` **未设** ⇒ `options.go:224-228` **无 default** ⇒ **0**；消费点 `internal/pool/pool.go:1257` `if p.cfg.MaxIdleConns == 0 || …` ⇒ **0 = 无上限** | `maxIdle` | `16` | 🟡 表达层取值（**比 Go 更严** ⇒ 只影响空闲连接回收，**不影响命令结果**） |
| **池耗尽等待** | ✅ `PoolTimeout` 未设 ⇒ `options.go:380-386` `PoolTimeout = ReadTimeout + 1s`；ReadTimeout = 3s ⇒ **4s**；消费点 `internal/pool/pool.go:1113` `p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)` ⇒ **4s 后报 `redis: connection pool timeout`** | `maxWait` | **`4s`** | ✅ **语义对齐**（异常类型不同，耗时口径一致） |

⚠️ **不要拿 `like-rpc` / `comment-rpc` 的 `64/16/4` 当基准**：like 域的 Go 走 **go-zero 的 redis**
（`go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:15` `idleConns = 8` ⇒ `MinIdleConns = 8`），
本域的 Go 走 **go-redis 直连**（`MinIdleConns = 0`）⇒ **两个域的 Go 客户端本就不同** ⇒ 池参数**不必同族**。

#### 📌 订正注记（2026-10-27 · G3 · 21:1x）：**`PoolSize` 是「基准连接数」，不是「连接数硬上限」**（G1 lane 提出 · 本域**独立读源码复核后确认**）

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上表**结论一个字未改**（本域原表的「总量上限」行**本来就写对了**）⇒ 本轮只是把**口径**写得更准。

- **G1 的订正（原始出处）**：**别把「Go 最多 `10×核数` 条连接」写进台账** ⇒ ⇒ 那会把**基准值**当成**上限**。
- **本域独立复核（go-redis v9.20.0 `options.go:200-206` 字段注释原文）**：
  `// PoolSize is the base number of socket connections.`
  ＋ `// If there is not enough connections in the pool, new connections will be allocated in excess of PoolSize,`
  ＋ `// you can limit it through MaxActiveConns`
  ⇒ ⇒ **池不够时连接数会「超出 `PoolSize`」** ⇒ ⇒ **真正的总量上限是 `MaxActiveConns`**
  ⇒ ⇒ 本域 Go 侧 `MaxActiveConns` **未设** ⇒ **Go 无总量硬上限** ✅（上表「总量上限」行**原本就写对了**）。
- **更精确的说法（本轮补）**：`PoolSize` 的真正作用是**「信号量容量」** ——
  `internal/pool/pool.go:421` `semaphore: internal.NewFastSemaphore(opt.PoolSize)`
  ⇒ ⇒ **池耗尽的上限 = `PoolSize` 这个信号量容量**
  ⇒ ⇒ `pool.go:1098` `func (p *ConnPool) waitTurn(ctx context.Context) error` ⇒ 抢不到就
  `pool.go:1113` `p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)`
  ⇒ ⇒ `pool.go:1124` `case ErrPoolTimeout:` ⇒ **错误身份 = `redis: connection pool timeout`**
  ⇒ ⇒ **并发命令数 > `PoolSize` ⇒ 必然进等待 ⇒ 上限 4s**（与上表「池耗尽等待」行**一致**）。
⇒ **证据强度**：**L1**（两侧源码逐行）⇒ ⇒ **未做并发压测 ⇒ 不写 L4**。

### 十一.4 🔴 本轮最有价值的副产物：**光加池会引入比 Go 更糟的行为**（`max-wait` 不可省）

⚠️ **这是本轮新查出的、全仓此前无人登记的一条**（实测：`grep -rn "max-wait\|PoolTimeout\|无限等" docs/DRIFT/` ⇒ **零命中**）。

1. **Go 侧池耗尽有上限**：`go-redis@v9.20.0/internal/pool/pool.go:1113`
   `err := p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)`
   ⇒ `PoolTimeout` 未设 ⇒ `options.go:380-386` `PoolTimeout = ReadTimeout + 1s` ⇒ ReadTimeout = 3s ⇒ **4s**
   ⇒ ⇒ 池耗尽 ⇒ **4s 后失败**（`ErrPoolTimeout`，`pool.go:59-60`）。
2. **RAG 侧不配 `max-wait` ⇒ 无限等**：commons-pool2 2.12.1 `BaseObjectPoolConfig:59,68`
   `DEFAULT_MAX_WAIT_MILLIS = -1L` / `DEFAULT_MAX_WAIT = Duration.ofMillis(-1L)`
   ⇒ 消费点 `GenericObjectPool.java:306`
   `p = negativeDuration ? idleObjects.takeFirst() : idleObjects.pollFirst(remainingWaitDuration)`
   ⇒ **负值 ⇒ `takeFirst()` ⇒ 阻塞到有人还连接为止（无上限）**。
3. 🔴 **Lettuce 的 3s 命令超时救不了场**：`CommandExpiryWriter.write(command)` 才起算计时器
   （实测 lettuce-core **6.8.2** 字节码），而**借连接发生在写命令之前**
   （`RedisTemplate.execute` → `getConnection()` → `borrowObject()` → 之后才 `write`）
   ⇒ ⇒ 池耗尽时 **3s 命令超时根本没开始** ⇒ ⇒ **无限挂起**。
4. ⇒ ⇒ **落码必须同时配 `max-wait: 4s`**（`LettuceConnectionConfiguration$PoolBuilderFactory.getPoolConfig` ⇒
   `setMaxWait(getMaxWait())`，**只在 `maxWait != null` 时才设** ⇒ 不配 ⇒ 落 commons-pool2 自己的 -1）。
5. ⚠️ **变异实测坐实**：删掉 `max-wait` ⇒ 守门读到 **`-0.001S`**（＝ -1ms ＝ commons-pool2 的 -1 默认值＝无限等）⇒
   **不是推断，是量出来的**。

### 十一.5 守门 + 变异验证（**5 条全部精确转红** ⇒ 守门非假绿）

**守门**：`HotRpcRedisPoolContractTest`（**4 条**，零 Docker / 零 Testcontainers / 零真 Redis）——
起**真** `ApplicationContextRunner` ＋ Boot **真** `DataRedisAutoConfiguration` ⇒ 拿**真** `LettuceConnectionFactory`
⇒ 断言**真装配出来**的 `LettucePoolingClientConfiguration.getPoolConfig()`：

| # | 断言 | 读的消费点 |
|---|---|---|
| ① | 池必须**启用** | `getClientConfiguration()` 是 `LettucePoolingClientConfiguration`（未启用时装配出的是普通 `LettuceClientConfiguration`＝共享单连接） |
| ② | `maxTotal=64` / `maxIdle=16` / `minIdle=0` | Boot 4 `PoolBuilderFactory.getPoolConfig` ⇒ `setMaxTotal/setMaxIdle/setMinIdle` |
| ③ | **`maxWait=4s`** | 同上 ⇒ `setMaxWait` |
| ④ | **阳性对照**：commons-pool2 自己的默认 `maxWait` = **-1ms** | `new GenericObjectPoolConfig().getMaxWaitDuration()` ⇒ 证明 ③ 有识别能力 |

**变异（5 条 · 每条都跑过「还原 + sha256 校验」再复跑）**：

| 变异 | 手法 | 结果 |
|---|---|---|
| **MUT-A** | yml `pool.enabled: true` → `false` | ✅ **3 红**（① ＋ ② ＋ ③，全部红在「池必须已启用」这条前置断言上 ⇒ **红点精确**） |
| **MUT-B** | yml `pool.max-active: 64` → `65` | ✅ **1 红**（`expected: 64 but was: 65`） |
| **MUT-C** | yml `pool.min-idle: 0` → `1` | ✅ **1 红**（`expected: 0 but was: 1`） |
| **MUT-D** | yml 删掉 `pool.max-wait` | ✅ **1 红**（`expected: 4S but was: -0.001S` ⇒ **实测到「无限等」**） |
| **MUT-E** | pom 抽掉 `commons-pool2` | ✅ **1 红**（**`NoClassDefFoundError: org/apache/commons/pool2/impl/GenericObjectPoolConfig`** ⇒ **启动期直接失败**）<br>⚠️ **手法说明**：守门类**直接引用** `GenericObjectPoolConfig` ⇒ 抽掉依赖后**连编译都过不了** ⇒ 为拿到**运行时**证据，本轮把守门类临时移开、换上一条**不引用** `commons-pool2` 的临时探针（只断言「上下文能起来」）⇒ ⇒ **实测到 `NoClassDefFoundError`** ⇒ 探针已删、还原已校验 |

🔴 **一个必须记下的「口径」**：MUT-E 的**编译失败**本身就是「`commons-pool2` 不可省」的**最强证据**
（守门把依赖变成 load-bearing）⇒ ⇒ **不要**把「编译不过」当成变异失败的 excuses。

### 十一.6 证据强度与边界（**不得越读**）

| 项 | 值 |
|---|---|
| **证据强度** | **L3** —— Go 侧 **L1 逐行对照**（`service_context.go:17-20` ＋ go-redis v9.20.0 源码/字节码）＋ RAG 侧 **真装配运行证据**（Boot 4.0.8 / commons-pool2 2.12.1 / lettuce-core 6.8.2 字节码 ＋ 真上下文断言 ＋ 5 条变异） |
| ⚠️ **不是 L4** | **Go 侧没有同时跑的探针** ⇒ **不是「双侧活体对拍」** ⇒ 按本仓口径只能写 **L3** |
| ⚠️ **本轮未覆盖** | ① **并发活体对拍**（p99 口径）—— ⚠️ **原记的阻塞理由「机器级 Docker 端口转发故障」已于 2026-10-27 19:2x 实测不成立**（见 §十一.8 的四个探针）⇒ ⇒ **阻塞前提已消失** ⇒ **本条现在只差「Go 侧同时跑的探针」**（G1 已通报，其域那半可复跑）；② **`DRIFT-HOTRPC-004` 的重试分布**（§十.3 要求「复开时必须与重试那条一起验」⇒ 本轮**只验了三个位**）⇒ **两条仍挂起** |
| ⚠️ **守门不证明** | 不证明 SQL / DDL / 事务（hot-rpc 无库）· 不证明 Kafka 消费 · 不证明并发下的实际吞吐 |

### 十一.7 跨域影响面（本轮实测，**已派单**）

| 组件 | 落点 | 原状 | 结论 |
|---|---|---|---|
| **`hot-rpc`** | 本域 | 无池 | ✅ **已修**（本节） |
| **`comment-rpc`** | 本域（G3） | 有池（`commons-pool2` 已有）· `min-idle: 4` ❌ · **缺 `max-wait`** ❌ | 🔴 **同机制 ⇒ 本域自修**（见 `comment/rpc.md`） |
| **`task-rpc`** | G2 | 有池 · **缺 `max-wait`** ❌ | 🟡 **G2 域** ⇒ 已通报 |
| **`like-rpc`** | G2 | 有池 · `min-idle: 4` ❌（Go 侧 go-zero ⇒ 应 **8**）· **缺 `max-wait`** ❌ | 🟡 **G2 域** ⇒ 已通报 |
| **`favorite-api`** | 本域（G3） | 无池（Go 走 go-zero ⇒ `MinIdleConns = 8`）· 无 `max-wait` | 🟡 **未落码**（favorite-api 的池需求与本域不同 ⇒ 单独排期） |

⚠️ **「`max-wait` 缺失」这条是**全仓级**的共性缺陷**（凡已开池的组件都有）⇒ ⇒ 已作为**跨域发现**通报 G4（登记进 `global` 台账 · `docs/DRIFT/global/**` 是 G4 写权面 ⇒ 本域不写）。

### 十一.8 回归、门禁与环境实测（**可复算**）

**① 回归（2026-10-27 19:4x 重跑）**

| 模块 | tests | failures | errors | skipped | 口径 |
|---|---|---|---|---|---|
| **`hot-rpc`** | **101** | **0** | **0** | **0** | surefire XML 求和 ≡ maven 模块汇总行；**20 份 XML ≡ 20 个 test 阶段测试类 ⇒ 0 孤儿报告**（`HotRpcFatJarArtifactGateIT` 绑 `verify` 阶段 ⇒ 本阶段不产 XML） |
| `service/common`（共享层，本轮 `-am` 连带） | 324 | 0 | 0 | 0 | 61 份 XML ≡ 61 个测试类 |
| **`comment-rpc`**（同机制，G3 另一域） | **123** | **0** | **0** | **0** | 22 份 XML ≡ 22 个测试类；基线 119 ＋ 新守门 4 = 123 |

**② 🔴 首轮全量回归的 4 条 failures —— 已隔离复跑定性为「瞬态」**

- 首轮（2026-10-27 19:10–19:20）：**101 条 / 4 failures**，4 条**全部**在 `HotKafkaConsumerGateTest.awaitContainerJoinedGroup`
  （失败原文「生产 listener 容器没拿到全部分区（实际 0/3）」；日志直指 broker 端点 `localhost:64802` 连不上）。
- **隔离复跑该类**（19:3x）：**4 条 / 0 failures / 0 errors / 0 skipped ⇒ BUILD SUCCESS（27.81 s）**。
- **重跑全量**（19:4x）：**101 条 / 0 / 0 / 0 ⇒ BUILD SUCCESS**。
- ⇒ ⇒ **定性 = 并发跑批时的容器/端口争抢**，**与本轮 Redis 池改动无因果关系**
  （本轮改动只碰了 `hot-rpc` 的 pom / Redis 配置 / 一条新测试类，**不涉及 Kafka**）
  ⇒ ⚠️ **教训**：遇到「全量跑红但单类跑绿」⇒ **先隔离复跑再定性**，别急着改代码（改错方向的代价高于多跑一次）。

**③ 🔴 环境实测：此前记的「机器级 Docker 宿主→容器端口转发故障」**前提**现已不成立**（4 个探针全通）

| 探针 | 对象 | 结果 |
|---|---|---|
| ① 新建容器 | `redis:7-alpine` 发布 `16399:6379` | 宿主 `PING` ⇒ **`+PONG`**（0.03 s） |
| ② **42 小时前**的容器 | `ddl-comment-pg` `127.0.0.1:55566` | PG `SSLRequest` ⇒ **有应答**（0.01 s） |
| ③ **23 小时前**的容器 | `difftest5k-redis` `127.0.0.1:61379` | **`+PONG`** |
| ④ **3 天前**的容器 | `uudifftest-redis:56382` / `cdifftest-redis:56395` | **`+PONG`** |

⇒ ⇒ **转发链路现在是通的** ⇒ ⇒ **本档里所有「被机器级 Docker 转发故障阻塞」的搁置项，阻塞前提已消失**
⇒ ⇒ **已通报 G1 / G2 / G4**（三边各自那半现在可以复跑）。
⚠️ **本域自己仍挂着的只剩一条**：并发活体对拍差「**Go 侧同时跑的探针**」（不是 Docker 问题了）⇒ 见 §十一.6。

**④ 门禁与字节级扫描（2026-10-27）**

| 项 | 结果 |
|---|---|
| `tools/check-ledger-tables.py` | **EXIT=0** |
| `tools/check-ledger-selfcheck.py` | **EXIT=0** |
| `tools/check-ledger-count.py` | **EXIT=1** —— 索引称 315 / 实测 326（**差 +11**）⇒ 索引在 `docs/DRIFT/_INDEX.md`（**合并收口执行者写权面**）⇒ **合并后统一 `--fix`**（本轮 +11 来自本域台账新增的编号式条目：hot §十一 / §十一.8 / favorite §15.6-15.7 / comment §十一 / 坑档 P-HOTRPC-21~23 + PIT-COMMENT-RPC-15） |
| `tools/difftest/check-wave-lane-ownership.py` | **EXIT=0** |
| `tools/difftest/check-wave-coupling.py` | **EXIT=0** |
| `tools/difftest/check-wave-role-model.py` | **EXIT=0** |
| `tools/ledger-lint/favorite/{tables,selfcheck,vs-reality}` | 0 / **1** / **1** ⇒ **与本轮改动前完全相同**（selfcheck 的 5 条 = `api.md:691` / `vs-common.md:44,86,172` / `rpc.md:3643`，**全在本轮 diff 之外**；`vs-reality` 的 1 条 = 「共享面零改动」，**结构性、已归因**）⇒ ⇒ **本轮零新增门禁级问题** |
| U+FFFD 字节级扫描 | 本轮改动文件 **0 命中** · 本域台账＋坑档全量（**33 份**，已剔 `target/`）**0 命中** ⇒ **0 个 U+FFFD 字符** |

---

## 十二、✅ Redis **断连活体对拍**（共享层的 `REJECT_COMMANDS` 在本域**真的生效**）—— 2026-10-27 · G3

### 十二.1 一句话

**共享层那份 `REJECT_COMMANDS` 在 hot-rpc 里真的生效** ⇒ 断连后第一条命令 **1 ms 失败**（不是排队到 3s 命令超时）
⇒ **Go 腿同场景 1669–1808 ms（4 轮）** ⇒ ⇒ **两侧都是「快速失败」，语义等价**；
⇒ 对照组（Lettuce 默认 `DEFAULT`）**3007 ms** ⇒ ⇒ **机制是承重的**（不是「反正都快」）。

### 十二.2 三段判据（守门 = `HotRedisDisconnectFastRejectGateTest`，**2 条**，零 Testcontainers / 零真 PG）

| 判据 | 内容 | 为什么这么判 |
|---|---|---|
| **①** | 共享层那份 customizer **在场且没被域内副本遮蔽**：`getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` 的 **size == 1** ＋ bean 名含 `goStyleRedisClientOptionsCustomizer` | 共享层那份带 `@ConditionalOnMissingBean` ⇒ **域内若自己声明一个，共享层就退让** ⇒ 那时 size 可能是 1 但不是共享层那个 ⇒ **size == 1 ＋ 名字对得上两条合起来**才证明「用的就是共享层那份」 |
| **②** | 断连行为**消费点** = `connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()` == `REJECT_COMMANDS` | **不是读配置项**（那是「声明」）、也不是读 `ClientConfiguration` ⇒ 必须读**真在跑的客户端**的选项 |
| **③** | **活体 A/B**：真 Redis 容器 ⇒ 连上 ⇒ PING ⇒ **静默等待 1s（期间不发任何命令）** ⇒ 外部 `docker stop`（**优雅 FIN**，不是 kill 的 RST）⇒ 量**第一条命令**的耗时 | **A 组** = 共享层产出的那套 ClientOptions；**B 组** = 同一套**只**把断连行为换成 Lettuce 默认 `DEFAULT` ⇒ ⇒ **A 快 / B 排队到命令超时** ⇒ ⇒ 唯一差别就是那条机制 |

### 十二.3 实测数字（**2026-10-27 20:0x–20:1x · 本轮现跑**）

| 侧 | 场景 | 实测 | 判读 |
|---|---|---|---|
| **Go 腿**（真源 go-redis v9.20.0 ＋ **hot 域真源 `Options`** = `service/hot/rpc/internal/svc/service_context.go:17-20` 的 `{Addr, Password}`） | 断连后第一条命令 | **1669 / 1808 / 1710 / 1771 ms**（**4 轮**） | **快速失败**（go-redis 的 `MaxRetries` 默认 3 ＋ 重试退避 ⇒ 拒连重试若干次后失败）⇒ 与 G2 台账记的 **1694 ms** 同量级 ✅；err = `dial tcp … connect: connection refused` |
| **Go 腿**（comment 域真源 `Options` = `{Addr}`，**同一支探针换参数跑**） | 同上 | **1708 / 1776 / 1741 / 1753 ms**（**4 轮**） | 同量级 ⇒ ⇒ **两套真源 `Options` 的断连行为一致** |
| **RAG A 组**（共享层 `REJECT_COMMANDS`） | 同上 | **1 ms** | **毫秒级拒单** ⇒ 不排队 ⇒ **绝不撞 3s 命令超时** |
| **RAG B 组**（Lettuce 默认 `DEFAULT`） | 同上 | **3007 ms** | **排队到 3s 命令超时** ⇒ ⇒ 这是**对照组** |

⇒ **A/B 差 = 3006 ms** ⇒ ⇒ **机制是承重的**。
⇒ **两侧对比**：Go **~1.7 s** vs RAG(A) **1 ms** ⇒ ⇒ **量级不同（Go 的重试退避 vs Lettuce 立即拒单），
但语义相同：都是「快速失败」，都不是「挂到命令超时」** ⇒ ⇒ ⇒ **Go 的 `MaxRetries=3` 重试面**另属他档（**Lettuce 无重试**）⇒ **本条不覆盖那一条**（不混）。

### 十二.4 ⚠️ 口径边界（三条，逐条读）

1. 🔴 **Go 腿是「本轮现跑的一次性探针」，源文件不在仓内** ⇒ ⇒ **不可从仓复现**
   ⇒ ⇒ **证据强度**：RAG 腿 = **L4（仓内可复现的活体 A/B）**；Go 腿 = **L4-一次性（实测但仓外）**
   ⇒ ⇒ ⇒ **本轴整体不能写成「仓内可复现的双侧活体对拍」** ⇒ ⇒ 要长期可复现 ⇒ **需把 Go 探针入 `tools/`**
   （**`tools/**` 是 G4 写权面** ⇒ 已派单给 G4）。
   ⇒ **探针的等价规格**（可据此重建，**不依赖原探针文件**）：
   - **真源 `Options`**：`service/hot/rpc/internal/svc/service_context.go:17-20` 的 `{Addr, Password}`
     （comment 域 = `service/comment/rpc/internal/cache/redis.go:17-19` 的 `{Addr}`）
   - **其余全部走 go-redis v9.20.0 库默认**（`ReadTimeout 3s` / `DialTimeout 5s` /
     `PoolSize 10×GOMAXPROCS` / `PoolTimeout = ReadTimeout+1s`）
   - **时序**：**连上 → PING → 静默 1000ms（期间不发任何命令）→ `docker stop`（SIGTERM ⇒ 优雅 FIN）→ 量第一条命令**
   - **编排**：每组**各自**取一个空闲端口（不能复用同一个：容器还在占着）⇒ 起 `redis:7-alpine` ⇒ 等就绪 ⇒ 跑探针 ⇒ `docker rm -f` 收尾
2. **RAG 腿测的是「池里有空闲连接」这一形态**（本域现在**有池**）⇒ **不是**「池耗尽」那形态
   ⇒ ⇒ 两者**必须分开判**（池耗尽那形态由 §十一 的 `max-wait` 那条覆盖）⇒ ⚠️ **别把两条混为一谈**。
3. **不外推**：like / task / comment / users / admin 各域的域上下文不同 ⇒ customizer 数量可能不同
   ⇒ 行为可能不同 ⇒ ⇒ **每个域自己跑一遍**才是证据（**G3 的 comment 域自己跑了一份** ⇒ **两域互不代替**）。

### 十二.5 回归与门禁（**2026-10-27 20:23 · 本轮现跑**）

| 项 | 结果 |
|---|---|
| **hot-rpc 全量** | **103 / 0 failures / 0 errors / 0 skipped** ⇒ **BUILD SUCCESS**（exit 0，Finished at `2026-10-27T20:23:37+08:00`）⇒ 基线 **101** ＋ 本轮新守门 **2** = **103** ✅ |
| **hot-rpc surefire 复核** | **21 份 XML**（基线 20 ＋ 新增 1 个测试类）⇒ **求和 103/0/0/0** ⇒ **与 maven 汇总行一致** ⇒ **0 孤儿报告** |
| **新守门自身** | `HotRedisDisconnectFastRejectGateTest` = **2 / 0 / 0 / 0**，**Skipped = 0** ⇒ ⇒ **docker 路径真跑到了**（不是被 `assumeTrue` 跳过）⇒ 耗时 **64.03 s** |
| **comment-rpc 全量**（同轮） | **125 / 0 / 0 / 0** ⇒ 基线 123 ＋ 2 = 125 ✅（详见 [`comment/rpc.md` §十二](../comment/rpc.md#十二✅-redis-断连活体对拍共享层的-reject_commands-在本域真的生效-2026-10-27--g3)） |
| **service/common 全量**（同轮 reactor） | **324 / 0 / 0 / 0** ⇒ **三模块合计 552 / 0 红** |
| **本轮改动文件** | `HotRedisDisconnectFastRejectGateTest.java`（新增 303 行 / 2 条）· 本档两处段落 |
| **门禁（本轮终检）** | `tools/check-ledger-tables` **EXIT=0** · `tools/check-ledger-selfcheck` **EXIT=0** · `tools/check-ledger-count` **EXIT=1**（索引称 **315** / 实测 **328** ⇒ 差 **+13** ⇒ 索引在 `docs/DRIFT/_INDEX.md` = **合并收口执行者写权面** ⇒ **合并后统一 `--fix`**；⚠️ 相对 §十一.8 记的 **326（+11）**，本轮 **+2** 来自两域各增的 **§十二** ⇒ ⇒ **旧值保留不改写**）〔📌 **2026-10-27 订正**：**索引路径写错了** ⇒ 被改的是 **`docs/Pitfalls/_INDEX.md`**（不是 `docs/DRIFT/_INDEX.md`）⇒ **根因与闭合动作详见 §十二.6** ⇒ **原值保留不改写**〕· `tools/check-doc-links` **EXIT=0**（本轮**实修 18 处**失效锚点 ⇒ 全部改用门禁自己的 `slug()` **实算值**）· `tools/difftest/check-wave-{lane-ownership,coupling,role-model}` **全 0** · `tools/ledger-lint/favorite/{tables,selfcheck,vs-reality}` = **0 / 1 / 1** ⇒ **与本轮改动前完全相同**（selfcheck 仍 **5 条**，**全在本轮 diff 之外**；vs-reality 那 1 条 = 「共享面零改动」，**结构性**）⇒ ⇒ **本轮零新增门禁级问题** |
| **U+FFFD 字节级扫描** | 本轮改动文件 **8 份全部 0 命中** · 本域台账＋坑档全量（**33 份**，已剔 `target/`）**0 命中** |
| **全仓 `check-*.py` 巡检** | **18 个脚本 ⇒ 17 个 EXIT=0 / 1 个 EXIT=1** ⇒ 那 1 个是 `tools/difftest/check-ddl-static.py` ⇒ **是 `users` 域的 9 处既知差异**（`users_user.status` 可空性 1 处 ＋ `DEFAULT CURRENT_TIMESTAMP` 残留 8 处）⇒ ⇒ **不在本域** ⇒ ⇒ **与本轮改动无因果关系** |

### 十二.6 📌 台账计数差 **+13** 的根因定位（2026-10-27 · G3）

**一句话**：**+13 不是漂移** ⇒ ⇒ 它是「**本 lane 在共享索引最后一次更新之后新增了 13 条编号式坑位**」⇒ ⇒ **两棵树各自的数字都是对的**（G4 树 **315** / G3 树 **328**）⇒ ⇒ **合并后跑一次 `--fix`（315 → 328）即可闭合** ⇒ ⇒ **任何一边都不需要改代码**。

#### ① 可复算的判据（三条，逐条给出命令与实测值）

| 判据 | 命令 | 实测 | 结论 |
|---|---|---|---|
| **基线处本来就 PASS** | `git merge-base <G4 HEAD> <G3 HEAD>` = **`cd893d1`** ⇒ `git archive cd893d1 docs/Pitfalls` 解到临时目录 ⇒ 用**同一口径**复算 | **315 条** ＋ 索引那行写着 **315**（`docs/Pitfalls/_INDEX.md:117`）⇒ ⇒ **守门 PASS** | ⇒ ⇒ **基线无漂移** ⇒ ⇒ 漂移量 **13 全部产生于本 lane 的提交** |
| **本 lane 的树** | `python3 tools/check-ledger-count.py`（我树 HEAD `0d5691a`） | 索引 **315** / 实测 **328** ⇒ **差 +13** ⇒ EXIT=1 | ⇒ ⇒ 这就是「差 +13」的**全部来源** |
| **差集方向（决定性）** | 逐文件逐条比对两树的编号式标题（脚本按 `tools/check-ledger-count.py` 的 `HEADING_RE` + `TOKEN_RE` **同口径**跑） | **G4 有、G3 无 = 0 条** ⇒ ⇒ **纯增、无减、无改写** | ⇒ ⇒ **不存在「有人删条目 / 编号撞号 / 改写旧条目」** ⇒ ⇒ **两树不是「谁错了」，是「谁还没合并」** |

⚠️ **为什么两树会差**：G4 树**不含本 lane 的任何提交**（`0d5691a` / `50ccd00` / `a20282f` / `3249ef7` / `5361863` 全部 `git merge-base --is-ancestor` = **否**）⇒ ⇒ G4 树看的是**本 lane 落码前**的快照 ⇒ ⇒ **315 在 G4 树里是对的，328 在我树里也是对的** ⇒ ⇒ **两边都不该「修」**。

#### ② 那 13 条是谁（逐条给 `文件:行号` ⇒ 可复算）

| 文件 | 行号 | 条目号 | 合计 |
|---|---|---|---|
| `docs/Pitfalls/comment/rpc.md` | `614` / `673` / `707` | `PIT-COMMENT-RPC-14` / `-15` / `-16` | **3** |
| `docs/Pitfalls/favorite/api.md` | `433` / `490` / `545` / `597` | `PIT-FAV-API-010` / `-011` / `-012` / `-013` | **4** |
| `docs/Pitfalls/favorite/rpc.md` | `1787` / `1836` | `PIT-FAV-RPC-038` / `-039` | **2** |
| `docs/Pitfalls/hot/rpc.md` | `688` / `724` / `747` / `765` | `P-HOTRPC-21` / `-22` / `-23` / `-24` | **4** |
| | | **合计** | **13** ✅ |

⇒ ⇒ **13 条全部落在本 lane 的写权面内**（`docs/Pitfalls/{comment,favorite,hot}/**`）⇒ ⇒ **没有一条落在别人域**。
ℹ️ **「编号前缀族」那一行没有漂移**（守门只报了条目数那一项）⇒ ⇒ 这 13 条**全部落在已存在的 4 个前缀族里** ⇒ ⇒ 前缀集合没变。

#### ③ 🔴 顺带订正：**索引的路径之前写错了**

- 守门工具 `tools/check-ledger-count.py` 写的是 **`INDEX = PITFALLS / "_INDEX.md"`**（`PITFALLS = REPO / "docs/Pitfalls"`）⇒ ⇒ **被改的那个索引是 `docs/Pitfalls/_INDEX.md`**（实测：该文件 `:117` 行就是 `| **全域坑位条目** | **315 条** …`）⇒ ⇒ **`docs/DRIFT/_INDEX.md` 里没有这一行**（实测 `grep "全域坑位条目"` **只命中** `docs/Pitfalls/_INDEX.md`）。
- ⇒ ⇒ **本档 §十二.5 门禁行 与 [`comment/rpc.md`](../comment/rpc.md) §十二 门禁行里写的「索引在 `docs/DRIFT/_INDEX.md`」是错的** ⇒ ⇒ **两处均已订正为 `docs/Pitfalls/_INDEX.md`**（**原文保留 + 📌 注记**，不改写历史断言）。
  ℹ️ [`favorite/rpc.md`](../favorite/rpc.md) §15.6 的 ② 块**当时就写对了**（`docs/Pitfalls/_INDEX.md`）⇒ ⇒ **那一处不动**。

#### ④ 归属与收口动作（谁在什么时候做什么）

| 动作 | 谁 | 何时 | 判据 |
|---|---|---|---|
| **跑 `python3 tools/check-ledger-count.py --fix`**（把 `docs/Pitfalls/_INDEX.md` 那行改成实测值 ⇒ **315 → 328**） | **合并方 / 合并收口执行者**（`docs/Pitfalls/_INDEX.md` 是共享索引 ⇒ AGENTS.md §5.3b「合并后统一更新」） | **合并后** | 跑完 `check-ledger-count.py` ⇒ **EXIT=0** |
| 📌 **订正（2026-10-28 · G3）**：**实测目标值已从 328 变成 330** | **同上（合并方 / 合并收口执行者）** | **合并后** | 本 lane 在 §十二.6 定位（+13）之后**又新增了 2 条编号式条目** ⇒ 315 + 13 + 2 = **330**（实测 `check-ledger-count.py` ⇒ 索引 315 / 实测 **330** ⇒ 差 **+15**）：① `docs/Pitfalls/article/rpc.md` 的 **`PF-ARTICLE-RPC-016`**（Testcontainers PG 宿主口错位 ⇒ `EOFException` 的环境 flake 真签名 · 2026-10-28 据 G1 lane 通报）② `docs/DRIFT/article/vs-common.md` 的 **`G4-9`** 派单行（编号式 ⇒ 也计入条目数）⇒ ⇒ **原文的 328 保留不改写**，**当前实测 = 330** ⇒ ⇒ **任何一边都不改代码、不改索引** |
| ⚠️ **不要各 lane 各自 `--fix`** | 各 lane | — | 同一行会被多个分支改 ⇒ ⇒ **合并必撞** ⇒ ⇒ **只在合并后统一跑一次** |
| **本 lane 需要做的** | **无**（**代码零改动**、**索引零改动**） | — | 我树当前 EXIT=1 是**预期内**的 |

⇒ ⇒ **结论：+13 已闭环，不需要任何一方改代码；它只在合并那一刻需要一个 `--fix`。**

### 十二.7 📌 外部阻塞：**G4 会话已因 Token Plan 用量上限停住**（2026-10-28 00:02:17 实测）

⚠️ **这不是「回执还没写」，是「对方会话根本不会处理队列消息」** ⇒ ⇒ **跨 lane 事项不能只靠等**。

**判据（可复算）**：`mavis session get mvs_e8063ac67c654150ab7797b8116ef0a1`
⇒ `status.type = "started"` 但 `status.message` = **「已达到 Token Plan 用量上限：请升级 Token Plan 套餐或购买积分补充用量。 (2056)」**
· `errorCode = 42212` · `errorSource = "agent-runtime"` · `updatedAt = 1793116937346`（= **2026-10-28T00:02:17+08:00**）。

⇒ ⇒ **影响面**：本 lane 发给 G4 的**队列位 55** 那封（4 件收口信 ＋ 计数结论）**当前无人处理** ⇒ ⇒ **同一波次的 G1 / G2 发给 G4 的信同样在排队**。

**本 lane 因此挂起的待办（逐条，标清「谁才能解」）**：

| 待办 | 卡在哪 | 谁能解 |
|---|---|---|
| article 域 gRPC 测试段号（预留池 `22200–22599` **仍未分配** ⇒ **本域不擅自占预留段**）| global 段表是 **G4 写权面** | **G4**（会话恢复后）|
| 共享层 `MAX_PARAMETER_COUNT` `10001 → 10000`（差一格那一行）| `service/common/**` = **G4 唯一写者** | **G4** |
| 合并共享层后重跑全量（`15cf93a` 改了生产代码 ⇒ 本域基线失效）| 需要 G4 的共享层提交先落 | **G4** |
| 合并后统一 `python3 tools/check-ledger-count.py --fix`（**315 → 330**）| `docs/Pitfalls/_INDEX.md` = **合并收口执行者写权面** | **合并收口执行者** |
| comment C-5（tools 探针骨架）| `tools/**` = **G4 写权面**（本来就该它做）| **G4** |

⇒ ⇒ **纪律（本轮新增，两条）**：
1. 🔴 **「已送达」≠「对方会回」** ⇒ ⇒ 跨 lane 事项**不能只靠等** ⇒ ⇒ **能在域侧自修的先自修**
   （本轮 **favorite-api 落池**就是这一类：Go 侧走 go-zero ⇒ `MinIdleConns = 8` ⇒ 域内自修，**不需要等 G4**）；
2. 🔴 **「等」必须写成带可核条件的**（判据 = 某条命令能跑通），**不要**写成静态断言
   （见 §十二.4 口径边界 ① 的同款教训）⇒ ⇒ 本节就是那个条件的落点。

—— G3（2026-10-28 00:0x · `align/g3-article-comment-favorite-hot` · HEAD `cb70876` + 本提交）

---

### 十二.9 🔴 `tools/redis-disconnect-probe/` 的**完整落码规格**（2026-10-28 · G3 · 自包含，**照此即可落码**）

> **为什么这一节存在**：G4 回执说「`tools/redis-disconnect-probe/` 派单**只拿到摘要，规格全文未到 ⇒ 不凭摘要落码**」
> ⇒ ⇒ 🔴 **规格不该活在聊天里**（聊天会截断、会过期、会被略读）⇒ ⇒ **落进仓内**才是可交付的形态
> ⇒ ⇒ **G4 落码时不必再等我的信，直接照本节落** ⇒ ⇒ 本节 = 规格全文（含可直接复制的 Go 探针源码）。
> ⚠️ **落点 `tools/redis-disconnect-probe/` = G4 写权面** ⇒ ⇒ **G3 只写规格、不建目录**（⛔ 越权）。

#### 12.9.1 目的

把「Redis 连接被服务端优雅关闭（FIN）后，客户端**第一条命令**多久失败」从**一次性仓外探针**升级为**仓内可复现的双侧活体对拍**
⇒ ⇒ 补上 §十二.4 口径边界 ① 记的那个缺口（「Go 腿 = L4-一次性（实测但仓外）⇒ **不可从仓复现**」）。

#### 12.9.2 Go 腿：Options **按真源逐字取**，其余**全走库默认**

| 模式 | 真源出处（**消费点**）| 逐字取的字段 | 其余 |
|---|---|---|---|
| `hot` | `service/hot/rpc/internal/svc/service_context.go:17-20` | `redis.NewClient(&redis.Options{Addr, Password})` ⇒ **只 2 个** | **库默认** |
| `comment` | `service/comment/rpc/internal/cache/redis.go:17-19` | `redis.NewClient(&redis.Options{Addr})` ⇒ **只 1 个**（Password 留空）| **库默认** |
| `zeroway`（like / favorite 等走 go-zero 的域）| `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40` | `MaxRetries: maxRetries(3)` + `MinIdleConns: idleConns(8)` | **库默认** |

⇒ ⇒ **「库默认」= go-redis v9.20.0 的默认值**：`ReadTimeout 3s`（`options.go:364-379`）· `DialTimeout 5s`（`options.go:338-340`）·
`PoolSize = 10 × GOMAXPROCS`（`options.go:350-351`）· `PoolTimeout = ReadTimeout + 1s`（`options.go:380-386`）
⇒ ⇒ **不要自己发明任何字段**。

#### 12.9.3 Go 探针源码（**可直接复制**；G3 原型在 `/tmp/g3_go_redis_disconnect/`，**/tmp 会清 ⇒ 以本节为准**）

```go
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"time"

	"github.com/redis/go-redis/v9"
)

// 用途：测「Redis 连接被服务端优雅关闭（FIN）后，客户端第一条命令多久失败」。
// ⚠️ Options 按**真源**逐字取（见台账 §12.9.2），其余全部走 go-redis v9.20.0 库默认。
func main() {
	addr := flag.String("addr", "", "redis host:port")
	container := flag.String("container", "", "docker container name（用于 docker stop）")
	mode := flag.String("mode", "hot", "hot | comment | zeroway")
	password := flag.String("password", "", "redis password")
	wait := flag.Int("wait-ms", 1000, "静默等待毫秒（期间不发任何命令）")
	flag.Parse()

	if *addr == "" || *container == "" {
		fmt.Println("PROBE-FAIL missing -addr/-container")
		os.Exit(2)
	}

	opts := &redis.Options{Addr: *addr}
	switch *mode {
	case "hot":
		opts.Password = *password
	case "comment":
		// 真源只设了 Addr ⇒ Password 留空
	case "zeroway":
		opts.MaxRetries = 3
		opts.MinIdleConns = 8
	default:
		fmt.Println("PROBE-FAIL unknown -mode")
		os.Exit(2)
	}

	client := redis.NewClient(opts)
	ctx := context.Background()

	// ① 连上 + PING（测的必须是「断连」而不是「连不上」）
	if err := client.Ping(ctx).Err(); err != nil {
		fmt.Println("PROBE-FAIL ping:", err)
		os.Exit(2)
	}
	fmt.Println("PROBE-READY")

	// ② 静默等待：**期间不发任何命令**（发了会掩盖「断连后第一条命令」的排队窗口）
	time.Sleep(time.Duration(*wait) * time.Millisecond)

	// ③ 外部优雅关闭（docker stop = SIGTERM ⇒ redis 优雅退出 ⇒ 客户端看到 FIN，不是 RST）
	out, err := exec.Command("docker", "stop", *container).CombinedOutput()
	fmt.Printf("PROBE-DOCKER-STOP err=%v out=%s", err, string(out))

	// ④ 断连后第一条命令 ⇒ 量它多久失败
	t0 := time.Now()
	cmdErr := client.Get(ctx, "g3-probe-key").Err()
	elapsed := time.Since(t0)
	fmt.Printf("PROBE-RESULT mode=%s first_command_ms=%d err=%v\n", *mode, elapsed.Milliseconds(), cmdErr)

	_ = client.Close()
}
```

**依赖（`go.mod` / `go.sum`）** —— ⚠️ **不要写死版本、不要 `go mod tidy`**
（tidy 会去解析**依赖的测试依赖**（go-zero → yaml.v2 → check.v1 → rogpeppe），那些未必在本地 module cache 里 ⇒ tidy 必然失败，而探针根本不需要它们）。
⇒ ⇒ **沿用真源的 `go.mod` / `go.sum` 整体继承**（改 module 名）+ 只 import 真正用到的包 ⇒ 版本集与 hash 与真源逐条一致。
⇒ ⇒ 探针以 `CGO_ENABLED=0` 跑（纯 Go，本机无可用 cgo 工具链 ⇒ 必须关掉）+ `GOPROXY=off` + `GOTOOLCHAIN=local`。
⇒ ⇒ 本原型用到的版本（**核对用**）：`go-redis/v9 v9.20.0` + `xxhash/v2 v2.3.0` + `go-rendezvous v0.0.0-20200823014737-9f7001d12a5f` + `go.uber.org/atomic v1.11.0`；`go 1.25`。

#### 12.9.4 时序（**关键：期间不发任何命令**，否则掩盖排队窗口）

1. 连上 → `PING`（**必须成功** ⇒ 测的是「断连」不是「连不上」）
2. 静默等待 **1000 ms**（`--wait-ms`，**期间不发任何命令**）
3. 外部 `docker stop <container>`（**SIGTERM ⇒ 优雅 FIN**，**不是** RST）
4. 量**第一条命令**的失败耗时

#### 12.9.5 编排

每组**各自**取一个空闲端口（**不能复用同一个** —— 容器还在占着）⇒ 起 `redis:7-alpine` ⇒ 等就绪 ⇒ 跑探针 ⇒ `docker rm -f` 收尾。

#### 12.9.6 Java 腿：A/B 对照（**域内已有守门，工具层要做的是「把 Go 腿拉进来」**）

| 组 | 配置 | 实测（hot 域 · 2026-10-27 20:0x）|
|---|---|---|
| **A** | 共享层产出的那套 ClientOptions（`REJECT_COMMANDS`）| **1 ms** |
| **B** | 同一套**只**把断连行为换成 Lettuce 默认 `DEFAULT` | **3007 ms**（排队到 `commandTimeout: 3s`）|

⇒ ⇒ **A/B 差 3006 ms** ⇒ ⇒ **机制是承重的**。
⇒ ⇒ **消费点（不是读配置项）**：`connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()`
⇒ ⇒ **运行期生效判据**：`getBeansOfType(LettuceClientOptionsBuilderCustomizer.class).size() == 1` ＋ bean 名含 `goStyleRedisClientOptionsCustomizer` ⇒ ⇒ **无域内副本遮蔽**。
⚠️ **共享层已有更上一层的守门**（G4 实测）：`service/common/src/test/java/com/rag/common/redis/GoStyleRedisClientOptionsGateTest.java:50/:61/:71`（在场 + 没被遮蔽 + 不覆盖运维 `timeoutOptions`）＋ `GoStyleRedisClientShadowingGateTest.java`（源文件扫描）⇒ ⇒ **本工具**只补「**Go 腿**」这一半 ⇒ ⇒ **不造第二条 customizer 守门**（已与 G4 达成一致）。

#### 12.9.7 Go 腿实测基线（G3 已跑，**可作为期望值**）

| 模式 | 4 轮 `first_command_ms` | 判读 |
|---|---|---|
| `hot`（真源 `{Addr, Password}`）| **1669 / 1808 / 1710 / 1771 ms** | **快速失败**（go-redis `MaxRetries` 默认 3 ＋ 重试退避 ⇒ 拒连重试若干次后失败）⇒ err = `dial tcp … connect: connection refused` |
| `comment`（真源 `{Addr}`，**同一支探针换参数跑**）| **1708 / 1776 / 1741 / 1753 ms** | 同量级 ⇒ ⇒ **两套真源 `Options` 的断连行为一致** |

⇒ ⇒ **两侧语义相同（都「快速失败」），量级不同**（Go 的重试退避 vs Lettuce 立即拒单）
⇒ ⇒ **「Go 的 `MaxRetries=3` 重试面」另属他档**（Lettuce 无重试）⇒ ⇒ **本工具不覆盖那一条**（不混）。

#### 12.9.8 输出格式与退出码

| 标记 | 含义 |
|---|---|
| `PROBE-READY` | 已连上且 PING 成功（**这一步不过 ⇒ 测的是「连不上」⇒ 无效**）|
| `PROBE-DOCKER-STOP err=… out=…` | 外部优雅关闭的结果 |
| `PROBE-RESULT mode=… first_command_ms=… err=…` | **第一条命令的失败耗时**（判读用的就是这个数）|
| `PROBE-FAIL …` | 环境/参数问题 |

⇒ ⇒ **工具退出码**：`0` = 双侧都「快速失败」且语义一致；`1` = 不一致；`2` = 环境问题 / 真源漂移（**照 `tools/api-envelope-probe/favorite/run.sh` 的三段式：Go 腿输出 → 与冻结的 `expected.txt` 逐字节 diff → 再跑 Java 腿**）。

#### 12.9.9 与域内既有守门的关系（**不重复造**）

- 域内已有：`HotRedisDisconnectFastRejectGateTest`（hot · 2 条）· `CommentRedisDisconnectFastRejectGateTest`（comment · 2 条）—— ⇒ 它们守的是 **RAG 腿**；
- 本工具守的是 **Go 腿 ＋ 双侧对照** ⇒ ⇒ **不同轴**；
- ⚠️ **不覆盖**：① 池耗尽那形态（由各域 `max-wait` 那条覆盖）② 「断连时 gRPC 响应层客户端看到什么」（要真发一次 gRPC 调用）③ 每个域**自己**跑一遍（**不外推** —— like / task / comment / users / admin 各域的域上下文不同 ⇒ customizer 数量可能不同）。

—— G3（2026-10-28 · `align/g3-article-comment-favorite-hot`）

### 📌 订正注记 ①（2026-10-28 03:2x）—— **§12.9.3 的规格源码有一处真缺陷**：`GET` 的判据是**死的**（**原文保留不改写**）

> ⚠️ §12.9.3 那段「**可直接复制**」的 Go 源码里，第 ④ 步是
> `cmdErr := client.Get(ctx, "g3-probe-key").Err()` ⇒ ⇒ **本域按它落码后，变异 M2 把它打了出来**：
> ⇒ ⇒ **`GET` 一个不存在的 key，在连接完全健康时返回的是 `redis: nil`**（go-redis 的「key 不存在」哨兵）
> ⇒ ⇒ **`err != nil` 恒真** ⇒ ⇒ **分不清「连着但 key 不存在」与「断了」**
> ⇒ ⇒ ⇒ **「第一条命令失败了」这个判据是死的** ⇒ ⇒ 且耗时也被误判（健康时 ~4 ms ⇒ 落进 `fast-fail` 桶）
> ⇒ ⇒ ⇒ ⇒ **⇒ 那种形态下 `expected.txt` 的 diff 永远绿 ⇒ ⇒ 守门形同虚设。**
> ⇒ ⇒ **修法**：第 ④ 步改用 **`PING`**（健康 ⇒ `PONG, nil`（`err == nil`）· 断连 ⇒ `err != nil`）
> ⇒ ⇒ ⇒ **与**初始连通性检查用**同一个命令** ⇒ ⇒ 前后对称、判据自洽。
> ⇒ ⇒ ⇒ 另加一档 `class=not-disconnected`（`err == nil` ⇒ **断连根本没发生**）⇒ ⇒ 这档 ⇒ `PROBE-FAIL` ＋ 退出码 2。

### 📌 订正注记 ②（2026-10-28 03:2x）—— **§12.9.1 记的那个缺口「Go 腿不可从仓复现」⇒ 已闭合**

> **§12.9.1 的原文**：「把「Redis 连接被服务端优雅关闭（FIN）后，客户端**第一条命令**多久失败」从**一次性仓外探针**升级为**仓内可复现的双侧活体对拍**」
> ⇒ ⇒ **原缺口**（§十二.4 口径边界 ①）：「Go 腿 = L4-一次性（实测但**仓外**）⇒ **不可从仓复现**」⇒ ⇒ **本轮闭合**。

| 项 | 内容 |
|---|---|
| **落点（仓内路径）** | `service/hot/hot-rpc/src/test/probe/redis-disconnect/`（`main.go` · `run.sh` · `expected.txt` · `README.md` · `go.mod` · `go.sum`）|
| **为什么落在域侧** | `tools/**` 是 **G4 的写权面** ⇒ ⇒ 本域**不擅自写** ⇒ ⇒ 落在**域侧**（`service/hot/**` = 本域写权面）⇒ ⇒ **仓内可复现**这个目标照样达成 |
| **⚠️ 归口（两份实现不要同时存在）** | ⇒ ⇒ 若 G4 后续落 `tools/redis-disconnect-probe/` ⇒ ⇒ **二选一**（G4 那份改成**指向域内这份**的薄壳，或反过来把域内这份降级为引用）⇒ ⇒ **不许两份各跑各的** ⇒ ⇒ 否则同一条结论有两个来源 ⇒ ⇒ 漂移面 |
| **`go.mod` / `go.sum` 继承** | **与真源整体继承**（只改 module 名）⇒ ⇒ `go.mod` 与真源的差异 = **只有 module 名那一行**；`go.sum` **逐字节一致** ✔ ⇒ ⇒ 版本集与 hash 与真源逐条相同 ⇒ ⇒ **未 `go mod tidy`**（tidy 会去解析依赖的测试依赖 ⇒ 必然失败）|
| **构建** | `CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local` ⇒ ⇒ **离线可构建** ✔（本机 go1.25.11）|

#### 三模式实测基线（2026-10-28 03:2x · **仓内可复现**）

| 模式 | 真源 Options 出处 | `first_command_ms` | 归一化结论 | 与 `expected.txt` |
|---|---|---|---|---|
| `hot` | `service/hot/rpc/internal/svc/service_context.go:17-20`（`{Addr, Password}`）| **1809** | `class=fast-fail err_present=true` | ✅ **逐字节一致** |
| `comment` | `service/comment/rpc/internal/cache/redis.go:17-19`（`{Addr}`）| **1679** | `class=fast-fail err_present=true` | ✅ **逐字节一致** |
| `zeroway` | `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40`（`{MaxRetries:3, MinIdleConns:8}`）| **1704** | `class=fast-fail err_present=true` | ✅ **逐字节一致**（**首次补齐 zeroway 档**）|

⇒ ⇒ **与 §12.9.7 的一次性基线同量级**（hot 1669/1808/1710/1771 · comment 1708/1776/1741/1753）⇒ ⇒ **结论未变**

#### Java 腿（RAG 侧）实测（2026-10-28 03:1x）⇒ ⇒ **三段式跑通 ⇒ 双侧活体对拍闭环**

| 腿 | 命令 | 结果 |
|---|---|---|
| **Go 腿（hot）** | `run.sh --mode hot` | `first_command_ms=1696` · `class=fast-fail` ⇒ ✅ 与 `expected.txt` 逐字节一致 |
| **Go 腿（comment）** | `run.sh --mode comment` | `first_command_ms=1679` · `class=fast-fail` ⇒ ✅ 逐字节一致 |
| **RAG 腿（hot）** | `mvn -pl service/hot/hot-rpc -am test -Dtest=HotRedisDisconnectFastRejectGateTest` | **Tests run: 2, Failures: 0, Errors: 0, Skipped: 0** ⇒ BUILD SUCCESS |
| **RAG 腿（comment）** | `mvn -pl service/comment/comment-rpc -am test -Dtest=CommentRedisDisconnectFastRejectGateTest` | **Tests run: 2, Failures: 0, Errors: 0, Skipped: 0** ⇒ BUILD SUCCESS |

⇒ ⇒ **两侧语义一致**（都「快速失败」：Go ~1.7 s 走重试退避 / RAG **1 ms** 立即拒单）⇒ ⇒ **AGENTS.md §3.3「两侧同时跑的探针」满足** ⇒ ⇒ 可写 **L4（双侧）**
⇒ ⇒ 🔴 **但毫秒数差 3 个数量级** ⇒ ⇒ **只主张「语义一致」，不主张「毫秒一致」**

⇒ ⇒ ⚠️ **毫秒数是观测量不是判据** ⇒ ⇒ 判据是**归一化结论行**（`class` / `err_present`）⇒ ⇒ 可逐字节 diff
⇒ ⇒ **阈值口径**：`fast-fail` / `timeout` 的分界取 **2500 ms**（落在 Go 实测上界 1809 与 Lettuce `commandTimeout: 3s` 的 3007 ms **之间**）

#### 变异验证（2 轮 · 每轮只翻一个变量）

| 变异 | 预期 | 实测 |
|---|---|---|
| **M1** 阈值 2500 → 100 | 快速失败被判成 `timeout` ⇒ diff 红 | ✔ 红在 `class=timeout`（exit 1）|
| **M2** `docker stop` 换成 no-op（**仍编译**）| 断连没发生 ⇒ `not-disconnected` ⇒ diff 红 | ✔ 红在 `class=not-disconnected err_present=false`（exit 1）|
| 基线复跑 | 还原后必须回绿 | ✔ exit 0 · `class=fast-fail err_present=true` |

⇒ ⇒ **2/2**：每轮**编译通过** ＋ **红在目标处** ＋ 还原后 **sha256 逐字节一致**。
⇒ ⇒ ⚠️ **M2 的第一版作废**（把 `exec` 改成未使用 import ⇒ **编译失败**）⇒ ⇒ 按纪律「**编译失败不算「守门识别该退化」的证据**」⇒ ⇒ 改成仍编译的 no-op 形态后重跑才算数。

#### 证据强度（**读的时候分层**）

| 事项 | 强度 | 说明 |
|---|---|---|
| 「Go 腿在断连后**快速失败**，且三模式一致」| **L4（仓内可复现）** | 探针 + 冻结基线 + 变异 2 轮都在仓内 ⇒ ⇒ **§十二.4 口径边界 ① 的缺口已闭合** |
| 「RAG 腿 1 ms 立即拒单」| **L4**（域内既有守门）| `HotRedisDisconnectFastRejectGateTest` / `CommentRedisDisconnectFastRejectGateTest` ⇒ **本探针不重复造** |
| 「两侧**语义**一致（都快速失败）」| **L4（双侧）** | 两腿都在跑 ⇒ ⇒ 满足 AGENTS.md §3.3「两侧同时跑的探针」⇒ ⇒ 可写 L4 |
| 「两侧**毫秒数**一致」| 🔴 **不成立** | Go ~1700 ms vs RAG 1 ms ⇒ ⇒ **量级差 3 个数量级** ⇒ ⇒ 本条**只主张语义一致** |


---

## 🔴 **阈值类结论的「共享层依赖」声明**（2026-10-27 · 按 G2 [3] 的要求做的重核）

⚠️ **凡本档写了「**运行期上限 / 阈值 / 池形态**」类结论，必须标「依赖共享层哪个版本」** ⇒ ⇒ **共享层一改 ⇒ 那条要重核**（重核，不是重写）。

| 本档结论 | 依赖面 | 当前实测（2026-10-27 重核） | 共享层一改会怎样 |
|---|---|---|---|
| **§十 的 Redis 池形态 = 无池**（hot-rpc） | **依赖共享层的 dependency 状态**（`isPoolEnabled` 在 `enabled == null` 时**回落到「classpath 上有没有 `commons-pool2`」**） | ✅ 共享层 `service/common` 的 classpath 上 `commons-pool2` **零命中**（`mvn -B -o -pl service/common dependency:tree -Dincludes=org.apache.commons:commons-pool2` ⇒ BUILD SUCCESS，无该行）⇒ ⇒ **无池结论成立** | 共享层依赖树一变 ⇒ ⇒ **池形态静默翻转** ⇒ ⇒ **§十 要重核** |
| **Redis connect-timeout = 5s** | **不依赖**共享层（共享层只设 `disconnectedBehavior(REJECT_COMMANDS)`） | ✅ 域内 yml 显式配 5s | 共享层开始碰 `socketOptions` 时才变 |
| **rpc 服务端超时 = 2s** | 域内 `observability.rpc-timeout` ＋ 共享层的 `RpcServerInterceptorFactory` | ✅ 2s | 共享层改拦截器链时要重核 |
| **🆕 §十一 的 Redis 池形态 = 有池 ＋ 池参数 64/16/0 ＋ `maxWait=4s`**（hot-rpc） | **仍然依赖共享层的 dependency 状态** —— ⚠️ **但依赖方向变了**：`enabled` 已被**显式配成 `true`** ⇒ ⇒ **不再回落到 `COMMONS_POOL2_AVAILABLE`** ⇒ ⇒ **共享层依赖树变化不再能翻转本域的池形态**；**真正的 load-bearing 依赖变成「域内 pom 里那条 `commons-pool2`」** | ✅ **2026-10-27 实测**：`enabled: true` ＋ 域内 pom 显式声明 `commons-pool2` ⇒ **池启用**（守门 ① 实测）⇒ ⚠️ **抽掉 pom 那条依赖 ⇒ 启动期 `NoClassDefFoundError`**（MUT-E 实测）⇒ ⇒ **域内 pom 少这条 ⇒ 组件起不来** | 🔴 **共享层**：`GoStyleRedisClientAutoConfiguration` **只设 `disconnectedBehavior(REJECT_COMMANDS)`**（不碰 `pool.*` / 不碰 `timeoutOptions`）⇒ ⇒ **共享层本轮不动也不会翻转本域结论**；<br>🔴 **域内 pom**：**删掉 `commons-pool2` ⇒ 启动期直接失败**（不是「静默变无池」）⇒ ⇒ **这正是想要的形状**（[`favorite/api.md` §15.4](../favorite/api.md#154-🔴-陷阱lettucepoolenabled-没写--池没开本条最有价值的副产物) 的陷阱「classpath 一变池就静默翻转」在本域**已被消除**） |

| **🆕 §十二 的断连行为 = `REJECT_COMMANDS`**（hot-rpc） | **完全依赖共享层那份 `GoStyleRedisClientAutoConfiguration`**（域内**零副本** ⇒ 共享层那份带 `@ConditionalOnMissingBean` ⇒ **域内一旦自己声明一个，共享层就退让**） | ✅ **2026-10-27 20:1x 实测**：`getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` **size == 1** ＋ bean 名含 `goStyleRedisClientOptionsCustomizer` ⇒ **无域内副本遮蔽**；**消费点** `connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()` == `REJECT_COMMANDS`；**活体 A/B** = **A(REJECT_COMMANDS) 1 ms** vs **B(DEFAULT) 3007 ms** ⇒ 差 **3006 ms** | 🔴 **共享层若删掉/改名那份 customizer** ⇒ ⇒ 本域**静默回落**到 Lettuce 默认 `DEFAULT` ⇒ ⇒ 断连从 **1 ms** 变成 **~3005 ms**（排队到命令超时）⇒ ⇒ **重新撞 2s 服务端超时** ⇒ ⇒ **§十二 必须重核**；<br>ℹ️ **域内侧无风险**：本域**零副本** ⇒ 域内改代码**不会**翻转这条 ⇒ ⇒ **这条的写权全在共享层** |

> 📌 **订正注记（2026-10-27 · G3）**：上表**第一行**（「§十 的 Redis 池形态 = 无池」）是**落码前**的重核结论 ⇒ **原样保留不改写** ⇒
> ⇒ 读本域历史结论的人**以「第一行 + 🆕 行」并列为准** ⇒ ⇒ **不要**把 🆕 行误读成「第一行作废」⇒
> ⇒ 落码后的权威口径 = **§十一**（含守门与变异证据）。

⇒ **共享的判据明细表在 [`favorite/api.md` §17](../favorite/api.md#17-🔴-阈值类结论的共享层依赖声明2026-10-27--按-g2-3-的要求做的重核)（同款机制，三域共用一条判据）** ⇒ ⇒ 改一处要三域一起重核。

## 📌 指针 · 🔴🟢 **Testcontainers 宿主口钉口**（本域已落码 · 2026-10-28）

⇒ ⇒ **canonical 全文在** [`docs/DRIFT/article/rpc.md`](../article/rpc.md) 的「**二十三、🔴🟢 Testcontainers 宿主口钉口**」一节
⇒ ⇒ 本域那一段：模块 `hot/hot-rpc` · 域段 `22940–22969` · 本模块宿主口段 `22940–22959` · 脚手架段 `22960–22969` · **3** 个 Redis 个容器
⇒ ⇒ 落点：`service/hot/hot-rpc/src/test/java/.../support/RealContainerHostPort.java` ＋ 同名 `RealContainerHostPortTest.java`（守门 **5** 条）
⇒ ⇒ **与 Go 侧的一致性：N/A**（测试基础设施，不涉及运行时行为）
⇒ ⇒ 📌 **补齐（2026-10-28）**：本域的 **redis-disconnect 探针**用**裸 `docker run`** 起 Redis ⇒ ⇒ 它**不在** Testcontainers 的守门特征里 ⇒ ⇒ 原先落在「起容器必须钉口」的**盲区** ⇒ ⇒ 本轮已把它钉到登记口 `HOT_RD_REJECT_GATE=22943` / `HOT_RD_DEFAULT_GATE=22944` ⇒ ⇒ **并把守门形态扩到能识别裸 docker + `pinned(...)`** ⇒ ⇒ **缺口被机械堵住** ⇒ ⇒ 详见 canonical 的 **§二十三.4-bis**。

## 📌 快照 · 🔴 **G4 共享层合并（`ae218df`）后的本域回归**（2026-10-28 · G3）

**合并**：option D（**单一 merge 源 · 不 cherry-pick**）⇒ `git merge --no-ff align/g4-service-common`
（G4 @ `188e995`）⇒ ⇒ **`git merge-tree --write-tree` 预演 0 冲突** ⇒ ⇒
**G4 未触碰本域任何路径**（`git diff --stat` 空）⇒ ⇒ **本域代码零改动 ⇒ ⇒ 本域结论不受影响**。

**合并后本域 3 模块回归**（`mvn -B -o --fail-at-end -am test -pl <3 模块>`，`-DargLine=-Xmx2g`）：

| 模块 | 条数 |
|---|---|
| `service/hot/hot-rpc` | 108 |
| `service/hot/hot-api` | 96 |
| `service/hot/hot-common` | 14 |
| **本域小计** | **218** |

**全量口径**（12 域模块 ＋ `-am` 带进来的共享层）：**1387 条 / 0 failures / 0 errors / 4 skipped**
· BUILD SUCCESS · **15:30 min**（2026-10-28）
⇒ ⇒ **拆账（可复算）**：`service/common` **333** ＋ `service/users/users-common` **39** ＋ **四域合计 1015**
⇒ ⇒ **1015 + 333 + 39 = 1387**（逐字相等 ⇒ 口径自洽）。
⚠️ **聚合口径的两个坑**（别拿 reactor 求和当收口口径）：① `-am` 会把**依赖闭包**一起带进 reactor
（本次实测 **23** 个模块 ⇒ ⇒ 聚合前必须**写死目标模块清单**）② maven 的 `Tests run:` 有**两种行**
（模块级汇总行 vs 每类用例行 `… -- in com.rag.Foo`）⇒ ⇒ 过滤必须用 `-- in ` ⇒ ⇒ 否则**重复计数**
（2122 = 1061 × 2 就是这么来的）。

**门禁快照（20 条）**：**canonical 在** [`docs/DRIFT/article/rpc.md`](../article/rpc.md) 的
「**二十四、🔴 G4 共享层合并后的门禁快照（20 条）**」一节 ⇒ ⇒ **15 绿 / 4 红 / 1 用法错（EXIT=2，不是红项），四条红都不在本域**。

**🔴 `MAX_PARAMETER_COUNT = 10001` 是【设计值】，不是待修的漂移** ⇒ ⇒
**「等共享层把它改成 10000」的期待作废** ⇒ ⇒ canonical 论证见
[`docs/DRIFT/favorite/api.md`](../favorite/api.md) **§21.3**。

## 📌 坑位 · 🔴 **共享层的 otel 守门有 3 s 轮询窗口 ⇒ 并发跑批时会假红，且会「打断整个 reactor」**（2026-10-28 · G3）

**现象**：跑 `mvn -pl service/hot/hot-rpc -am test` 时 ⇒ ⇒ **reactor 在 `service/common` 就停了**
⇒ ⇒ **hot-rpc 根本没跑到** ⇒ ⇒ 报错：

```
[ERROR] GoStyleOtelAgentGateTest.shutdownFlushesPendingSpans -- Time elapsed: 3.099 s <<< FAILURE!
java.lang.AssertionError: 条件在 3000ms 内未成立
	at ...GoStyleOtelAgentGateTest.awaitTrue(GoStyleOtelAgentGateTest.java:203)
```

⇒ ⇒ `service/common` 当时的汇总 = **333 / 1 failure**。

**判定（不靠猜）**：**单跑该类** ⇒ `mvn -pl service/common test -Dtest=GoStyleOtelAgentGateTest`
⇒ ⇒ **3 条 / 0 failures / 0 errors**（2026-10-28 · 同树同 HEAD）⇒ ⇒ **是时序 flake，不是真红**
⇒ ⇒ 成因 = 该守门用 **3000 ms 轮询窗口**等「span 被 flush 出去」⇒ ⇒ **并发跑批时窗口没赶上**。

**⇒⇒ 两条纪律（本域自留）**：

1. 🔴 **跨模块回归一律带 `--fail-at-end`** ⇒ ⇒ 否则**一条 flake 就能让下游模块整片没跑到**
   ⇒ ⇒ 而「下游没跑到」在报告里会被误读成「下游没测」⇒ ⇒ **本轮我就被这个坑了一次**
   （⇒ 本轮 12 模块那轮**本来就带了** `--fail-at-end` ⇒ ⇒ 1387 那个口径**有效**）；
2. 🔴 **共享层的一条 flake ⇒ 不要在域侧「修」它** ⇒ ⇒ `service/common/**` 是 **G4 的写权面**
   ⇒ ⇒ **只登记 + 通知** ⇒ ⇒ 本条已随回函报给 G4（建议：窗口放宽，或接受它会打断 reactor）。

**归口**：⛔ `service/common` = 共享层 ⇒ **G3 不改**（AGENTS.md §5.3c）⇒ ⇒ 只登记 + 上报。
**证据强度**：L4（真跑出来 ＋ 单跑复验）· 核对日期 **2026-10-28**。

## 十三、🟢🔴 **gRPC 真进程端口迁出 OS 临时段**（**10 处**：`grpc.server.port=0` ×6 ＋ `forPort(0)` ×4 ⇒ 本域专用段 `22500–22579`）· 2026-10-28 10:5x · G3

**与 article 域同款（[`article/rpc.md` §二十三.7](../article/rpc.md)）：治的是「被测进程自己 listen 的 gRPC 口」，不是「容器宿主口」。**

#### 起因（域侧自修 · **不等派单**）

`docs/DRIFT/hot/vs-common.md` 的 **A5 行**已登记：本域测试面有 **6 处** `@SpringBootTest` 写 `"grpc.server.port=0"` ⇒ ⇒ 落 **OS 临时段 49152–65535**。
⚠️ **这一段与 Docker 随机发布容器端口同池** ⇒ ⇒ 别的会话的容器先占住 ⇒ ⇒ `EADDRINUSE` ⇒ ⇒ **长得像「被测行为坏了」，实为环境撞口**。
⇒ ⇒ **实证（G2 lane 2026-10-28）**：`BindException: Address already in use (/0.0.0.0:49992)` ⇒ ⇒ 49992 = 本机 `Sea-kafka` 容器**发布的端口**（`Up 7 天`）⇒ ⇒ **不是被测代码的错**（同段另有 `Sea-redis` 发布 49380）。
⇒ ⇒ global 段表 §4.10.19.3 **早就把 `22500–22599` 预分配给 hot 域**（状态「本层预分配 · **域侧未落码**」）⇒ ⇒ **本项本来就是域侧自己的活**（AGENTS.md §5.3d「域侧能自修的域侧修」）。

#### 落码（10 处全迁 ＋ 1 个机制类 ＋ 1 份守门）

| 项 | 值 |
|---|---|
| 机制类 | **新增** `service/hot/hot-rpc/src/test/java/.../support/RealGrpcPort.java`（`FIRST=22500` / `LAST=22579` ＋ **双地址 bind 自检** `0.0.0.0` ＋ `127.0.0.1` ＋ `holders(port)` 诊断）|
| 守门 | **新增** `support/RealGrpcPortTest.java` ⇒ **6 条** = 选取逻辑 **4 条** ＋ **源扫 2 条** |
| 迁 ① | **6 处** `@SpringBootTest(properties=…)` 的 `"grpc.server.port=0"` ⇒ 换成本段固定口 |
| 迁 ② | **4 处** `ServerBuilder.forPort(0)` ⇒ `forPort(RealGrpcPort.pick())`（`HotGrpcInterceptorWiringTest` 2 ＋ `HotRpcSlowThresholdGateTest` 2）|

#### 🔴 端口分配的单位是「**Spring 上下文**」，不是「测试类」

`NacosRegistrationHealthGateTest` ＋ `HotRpcSpringContextWiringTest` **同用 `22500`** ⇒ ⇒ 理由**不是**「省口」，而是：
**Spring 的 context cache key 包含 `properties` 的值** ⇒ ⇒ 两个类的 properties **逐条相同** ⇒ ⇒ 迁移前（都是 `0`）它们**共享同一个 Spring 上下文**、**只有一个** gRPC server ⇒ ⇒ 迁移后若给它们**不同**的固定口 ⇒ properties 就不相同 ⇒ ⇒ **反而把共享拆成两个上下文**（= **行为变化**）⇒ ⇒ **必须同口才能保持「共享关系零变化」**。
⇒ ⇒ 其余 4 个类的 properties 各不相同 ⇒ ⇒ 各自独占一个口：`22501`（`HotGrpcInterceptorWiringTest`）· `22502`（`HotRedisDisconnectFastRejectGateTest`）· `22503`（`HotRpcSlowThresholdGateTest`）· `22504`（`mq/HotKafkaConsumerGateTest`）。
⇒ ⇒ ⚠️ **对比：article 域用的是另一种形态** —— article 的 7 个类**共用同一个 `@DynamicPropertySource` 方法**扩展 ⇒ ⇒ properties 逐条相同 ⇒ ⇒ **同样保持共享**（两种做法，同一条纪律）。

#### 段内再切一刀（与 article / comment 域同款约定）

| 段 | 用途 |
|---|---|
| `22500–22579`（80 口） | **真进程 gRPC server 端口** |
| `22580–22599`（20 口） | **守门脚手架段**（造「已被占」样本）|

⇒ ⇒ **必须不相交的理由**：全量套件里**上一个测试类的 gRPC server 还活着**（Spring 缓存测试上下文）⇒ ⇒ 若守门去占**生产段里的**口 ⇒ 就可能撞上那个还活着的 server ⇒ ⇒ 守门被环境左右 ⇒ ⇒ **守门段与生产段零重叠**。
⇒ ⇒ 与本域**容器宿主口段 `22940–22969`**（§十二）**零重叠** ⇒ ⇒ 与 global 段表 §4.10.19.3 逐条对齐。

#### 🔴 守门最后两条是「源扫」—— 才是真正**堵住缺口**的部分

| 守门 | 断言 |
|---|---|
| 源扫 ① | 本模块测试源码里**不得**再出现 5 种「OS 盲选口」形态：`grpc.server.port=0`（属性）· `--grpc.server.port=0`（进程参数）· `forPort(0)` · `new ServerSocket(0)` · `freePort(` |
| 源扫 ② | **守恒**：凡是提到 `grpc.server.port` / `ServerBuilder.forPort` 的测试类 ⇒ **必须引用 `RealGrpcPort`** ⇒ ⇒ 「指定了端口却没走本域机制」⇒ 红 |

⚠️ **守门自身的两个坑（本轮踩到并已修）**：
① **禁止清单必须用拼接构造**（如 `"forPort(" + "0"`）⇒ ⇒ 否则 `stripComments`（**不动字符串字面量**）⇒ 守门**扫到自己** ⇒ ⇒ **假红**；
② **判据要能区分「代码」与「散文」** ⇒ ⇒ 只认**属性字面量形态**（带引号 + 逗号）⇒ ⇒ 否则守门会把自己写的**说明注释**判成违规。
⚠️ **守门必须自带「排除自己」规则** + **换名假样本** + **判据自检**三件套（否则「守门扫描型守门」类 ⇒ 必假红）。

#### 证据强度与边界

- **L3**（源码扫描：迁后本域 `grpc.server.port=0` / `forPort(0)` / `new ServerSocket(0)` **各 0 处**）**＋ L4**（本轮真回归，见下）。
- ⚠️ **TOCTOU 窗口诚实标注**：`pick()` 只能**压低**撞口概率，**不能归零**（探测与真正 bind 之间有窗口）⇒ ⇒ **「本段独占给本域」的意义就在这里**（别的域/别的会话不占这一段）。
- ⚠️ **本节只治「端口从哪来」，不改「测什么」** ⇒ ⇒ 用例语义零变化。

#### 本轮回归（4 模块 · `--fail-at-end`）

| 项 | 值 |
|---|---|
| 命令 | `mvn -o -B --fail-at-end -pl service/article/article-rpc,service/hot/hot-rpc,service/comment/comment-rpc,service/favorite/favorite-api -am clean test -DargLine="-Xmx2g"` |
| 树 / HEAD | `align/g3-article-comment-favorite-hot` @ 本轮工作区（2026-10-28 12:1x ~ 12:4x）|
| **第 1 跑（12:1x）** | `service/common` **333 / 0 / 0 / 0** ✅ · `article-rpc` **183 / 0 / 0 / 0** ✅ · `favorite-api` **131 / 0 / 0 / 1** ✅（1 skip）· `comment-rpc` **138 / 0 / 0 / 0** ✅ · 🔴 **`hot-rpc` FAILURE（1 条）= 本域新守门的守恒判据过严** ⇒ ⇒ **红的是判据、不是被测代码** ⇒ ⇒ 判据已改（见 [`docs/Pitfalls/hot/rpc.md` P-HOTRPC-25](../../Pitfalls/hot/rpc.md)）|
| **第 2 跑（12:40）** | `service/common` **333 / 0 / 0 / 0** ✅ · `article-rpc` **183 / 0 / 0 / 0** ✅（⇒ ⇒ **新判据在 article 域真跑绿** ⇒ ⇒ 判据改动本身已验）· 🔴 `hot-rpc` / `comment-rpc` **编译失败**（判据改动时我误插常量 ⇒ 重复定义 / 缺失常量）⇒ ⇒ **已修** ⇒ ⇒ **12:4x 编译验证**：`mvn -o -B --fail-at-end -pl … -am test-compile` ⇒ **EXIT=0** |
| 🔴 **诚实边界** | **`hot-rpc` / `comment-rpc` 的「新守门判据」目前只到「编译通过」**，**测试未重跑**（Jason 2026-10-28 插队指令：**不起任何全量 / 域级回归**）⇒ ⇒ **不得据此宣布「hot / comment 守门已绿」** ⇒ ⇒ **待下一轮回归补上** |

#### 🟡 **口径订正（2026-10-28 · G3）**：G2 报的本域数字与本树实测**不一致**

⇒ ⇒ **G2 报的「hot = 5 处」** vs **本树实测「6 处」**（`NacosRegistrationHealthGateTest` · `HotRpcSpringContextWiringTest` · `HotGrpcInterceptorWiringTest` · `HotRedisDisconnectFastRejectGateTest` · `HotRpcSlowThresholdGateTest` · `mq/HotKafkaConsumerGateTest`）
⇒ ⇒ ⇒ **两边数的是不同树**（G2 数的树里 hot 域没有 `HotKafkaConsumerGateTest` 那处 ⇒ 或该树尚未含最新改动）⇒ ⇒ **不是谁数错，是「比的不是同一份」** ⇒ ⇒ 迁移**前**应以**本树实测**为准，迁移**后**以「各形态 = 0」为准（口径见本节「源扫」）。
⇒ ⇒ ⛔ **本条只登记差异，不回退任何一方数字**（回退会掩盖「两棵树不同」这个事实本身）。

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W7

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| G-HOT-001 | D | P3 | 否 | Go `service/hot/rpc/internal/mqs/hot_handler.go:132`（`OnPartitionsRevoked` 内 `h.hkMu.Lock()`）→ `:136`（`h.syncToRedis(ctx, pid)`）→ `:196`（`syncToRedis` 内 `h.hkMu.RLock()`）⇒ Go `sync.RWMutex` **不可重入** ⇒ **必然自死锁**（`defer Unlock` 永不执行 ⇒ `delete` 从不执行 ⇒ 分片快照从未落盘）。RAG `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:216-217`（`registry.detach(partitions)` 后**锁外** `detached.forEach(store::flush)`）＋ `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotPartitionSketchRegistry.java:113-127`（写锁内原子摘引用并返回，**落盘由调用方在锁外完成**；`:136-149` 的 `detachWithinWriteLock` = 变异靶子）⇒ ⇒ 两侧行为**确实不同**（差异仍成立），但已由 `docs/Intentional_Deviations/hot/rpc.md` 的 **`SD-1`（✅ 仍成立，2026-10-22 签字）** 明写「照抄会复现 Go 的死锁；Java `ReentrantReadWriteLock` 允许降级 ⇒ 直译会把死锁悄悄变成能跑」⇒ **D**：**决策不是缺陷**，且 Go 侧该路径实际不可达 ⇒ 无客户端可见后果，只留痕 | 2026-10-30 |
