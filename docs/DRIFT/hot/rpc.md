<!-- @move AGENTS.md L1392-L1443 · §0 hot-rpc 首次实现 -->
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
| **契约测试** | **46 条** / 8 个类（`HeavyKeeperTest` 5 / `HeavyKeeperUint32OverflowTest` 4 / `HotEventConsumerDropTest` 12 / `HotPartitionSketchRegistryDeadlockTest` 6 / `HotRevokeFlushOutsideLockTest` 1 / `HotRankingQueryServiceTest` 9 / `InteractionStubServiceTest` 4 / `HotServiceGrpcImplErrorMappingTest` 5） | 全绿 |
| **变异验证** | **10/10 全部变红**：无符号比较（`maxCount` / 最小堆比较器 ×2）、指标 Inc 与 hkMap 查找次序、`UNKNOWN→INTERNAL`、去掉 TopK 归一、`scoreToCount→round`、指标名漂移、revoke 不移除分区、落盘挪回写锁内、坏消息不再返回 error | 守门有效，**非假绿** |
| **编译** | `hot-proto` / `hot-common` / `hot-rpc` / `hot-api` 四模块 `install` | BUILD SUCCESS |

⚠️ **变异验证第一轮抓到一个真实 bug**（不是测试问题）：
`HeavyKeeper.hash()` 写 `h & 0xFFFFFFFFFFFFFFFFL` 想做无符号掩码，但该字面量位模式全 1（即 `-1L`），
**掩码等于什么都没做** ⇒ FNV 结果最高位为 1 时取模得负数 ⇒ `ArrayIndexOutOfBounds`。
⚠️ **它是被「永不外抛」契约吞掉的**：消费者把异常降级成返回值 ⇒ 表现为**事件静默丢弃、热榜永不更新、启动零报错**。
⇒ 这是 §3.3 那条纪律的加强版：**「异常被降级处理」会同时遮蔽下游 bug**。

#### ⚠️ 四条边界（**不得越读成「hot 域已完成」**）

1. **无真 Redis / 真 Kafka 的集成验证** —— 4 段 Lua、两个 TTL、`RENAME` 原子性、`EXISTS` 守卫、RoundRobin 分配、零重试 handler 目前**只有代码级证据**。§6.8 #5。
2. **`/actuator/prometheus` 无暴露路径** —— 纯 gRPC 进程不起 web 容器（与 `user-rpc` 同款取舍 `management.server.port: -1`），而 Go 的 `prometheus.StartAgent` 在 19091/19092/19093 是**真起 HTTP** 的。L1 抓取端点暂缺。
3. **gRPC 端口未经分配** —— `Infrastructure.md §3` 只给 hot 预占 18897(HTTP)。本次暂沿用 Go 的 `8083`（8081=admin-rpc / 8082=message-rpc / 8083 空闲），走 `HOT_RPC_GRPC_PORT` 环境变量。
4. **`hot-api` 仍是 12 行空骨架**（13 个文件），`hot-rpc` 的事实**没有 HTTP 消费方**。

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
| topic / group / offset | `hot.yaml` | `spring.kafka.*` | 配置文件（未做自动化断言） |
| Redis keyspace 5 个 + 两个 TTL | `hot_handler.go:208,209,390,25` | `HotRedisSnapshotStore` / `HotMergeScheduler` | ⚠️ **无 IT**，见 6.8.6 #5 |
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
5. **无真 Redis / 真 Kafka IT** —— 4 段 Lua、`EXISTS` 守卫、`RENAME` 原子性、两个 TTL、
   RoundRobin 分配、零重试 handler 全部只有代码级证据。需 Docker 环境补 IT。

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

