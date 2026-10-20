# hot-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/hot/rpc`
> 目标根包：`com.rag.hot.hot.rpc`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/hot/hot-rpc/pom.xml` 只有 26 行，**除 parent 与 artifactId 外零依赖**，`src/` 不存在 → 零 Java 文件。`service/hot/hot-proto/` 已存在，proto 与 Go 逐字相同（仅 `go_package` → `java_package="com.rag.hot.hot.proto"`）。包结构依据来自同仓库已落地模块：

| 参照模块（已打开的文件） | 提供的一致性依据 |
|---|---|
| `message/message-rpc/MessageRpcApplication.java` | `@SpringBootApplication(scanBasePackages={"…rpc","com.rag.common"})` + `@ConfigurationPropertiesScan(basePackages="…rpc.config")` |
| `message/message-rpc/bootstrap/NacosRegistration.java` | `ApplicationReadyEvent` 后手动 `NacosServiceManager.registerInstance` + `@PreDestroy` 反注册；**注释明确「纯 gRPC 服务不带 web」，故不走 nacos-discovery 自动注册** |
| `message/message-rpc/config/MessageProperties.java` | `@Component @ConfigurationProperties(prefix="message")` + **静态内嵌类**承载分段配置（`List` / `Broadcast`），Go 的 `RpcServerConf/Postgres/RpcClientConf` 交给 Spring 属性 |
| `message/message-rpc/service/impl/MessageRpcServiceImpl.java` | `@GrpcService` + 继承生成 `…ImplBase` + `invoke(Supplier)` 统一把异常转成 `StatusRuntimeException` |
| `message/message-rpc/metrics/MessageMetrics.java` | Micrometer `Counter` + 静态指标名常量；**指标名与 Go `prometheus.SubSystem` 逐字对齐**，注释写明「Prometheus 抓取端不需要改配置」 |
| `message/message-rpc/client/UserRpcClient.java` | `@GrpcClient("user-rpc") BlockingStub` facade（**本模块无下游 gRPC，仅作形态参照**） |
| `common/spring/RpcServerInterceptorFactory.java` | `@Bean ServerInterceptor` → devh 自动注册；timeout 默认 `Duration.ZERO`（不限时） |
| `common/spring/ObservabilityProperties.java` | `@Component @ConfigurationProperties("observability")`，**当前只有 `slowThreshold` 一个字段** |
| `common/observability/MqConsumer.java` | `traceConsumer(parent, tracerName, spanName, Map<String,Object> attrs, Function<Context,Throwable>)`；只做 span 包装，**不提供消费框架** |
| `common/logger/Logger.java` / `WithArticleID.java` | `init/logBusinessErr/logInfo/logFatal` 静态入口 + `WithArticleID.of(id)` |
| `users/user/user-rpc/pom.xml` | 已声明 `spring-boot-starter-data-redis`（第 165 行）、`grpc-server-spring-boot-starter`、`micrometer-registry-prometheus`、Nacos |

### 已确认的框架机制

- `net.devh:grpc-server-spring-boot-starter` 的 `@GrpcService` / `@GrpcClient`
- Spring Data Redis（Lettuce）：**RAG 已有先例**（`user-rpc` / `admin-rpc` 的 pom + `StringRedisTemplate`）。hot 需要的是 `ZSetOperations` 与 Lua（`RedisTemplate.execute(DefaultRedisScript, …)`），`StringRedisTemplate` 继承 `RedisTemplate<String,String>` 因此 `opsForZSet()` **开箱可用**，**无需新框架**
- `Infrastructure.md` §5.6 已写明决策：**Kafka 客户端层 Java 用 `spring-kafka` 替换 Go 端 `sarama`，服务端不动**。**但 `spring-kafka` 在 RAG 全仓零引用**（grep `kafka` 仅命中 `MqConsumer.java` 的 javadoc 与 users-common `ErrorCode` 的枚举名），**需首次引入**
- Micrometer + Actuator Prometheus
- `common/observability/MqConsumer` 的 span 包装

### 当前 Component 可以直接复用的能力

- **gRPC server span**：`common/spring/RpcServerInterceptorFactory` —— 需显式 `@Import` 才能生效 ⚠️ `@Component` ≠ 自动注册：需 `scanBasePackages` 含 `com.rag.common` 或显式 `@Import`。实测 `user-rpc` / `admin-rpc` 均**未注册**（见 `FileTree/common.md` §12.7 #1）。
- **Redis 客户端**：`spring-boot-starter-data-redis` 的 `StringRedisTemplate`（ZSet + Lua 子集），`REUSE`（**注意纠偏：任务描述称「Redis 无 RAG 先例」——实测不成立，users 域已在用 Redis，只是没用过 ZSet/Lua**）
- **MQ span 包装**：`common/observability/MqConsumer.traceConsumer`，对应 Go `observability.TraceConsumer`（`hot_handler.go:105`）
- **业务日志**：`common/logger/Logger`
- **不需要**：`common/snowflake`（hot-rpc 零 ID 生成）、`common/response/Response`（gRPC 不走 HTTP 信封）、`common/communityfact/*`

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **端口** | Go 三份配置分别 `8083/8084/8085`（同一 consumer group 的 3 个副本）。`Infrastructure.md` §3 只给 `hot` 预占 18897（HTTP），**hot-rpc 的 gRPC 端口未分配**。Java 侧形态应为「同一部署 ×3 副本」，端口取自 `GrpcServerProperties` 的环境变量 |
| **`heavykeeper` 的落点** | RAG 无任何先例。**结论：必须作为 hot-rpc 的新组件移植**（理由见 §12.3 + §12.5），落点 `domain/HeavyKeeper.java`。**hot-api 不得持有副本**——grep 验证 Go 侧只有 `rpc/internal/config` 与 `rpc/internal/mqs` 引用它 |
| **错误码** | hot-rpc **零引用** `service/hot/common/errmsg.go`（grep 验证），因此**不引入错误码常量类**，也不与 `FileTree/hot/api.md` 共用 `hot-common` 模块 |
| **`interaction` 4 个 RPC** | proto 已冻结（RAG 的 `hot.proto` 与 Go 逐字相同），4 个方法**必须存在**，但其中 3 个返回 `UNIMPLEMENTED`、1 个返回 `success=true` —— 这个不对称必须保留 |
| **Flyway / MyBatis-Plus** | 本模块无任何数据库表（`hot-rpc` 只有一个 Redis keyspace），**不引入 Mapper / Entity / Flyway** |

---

## 12.2 第二部分：Go 原始文件树

```
service/hot/rpc/
├── hot.go                                    # 入口：gRPC server + sarama consumer group + 2 个后台 goroutine
├── etc/
│   ├── hot.yaml                              # ListenOn 8083 / Prometheus 19091 / Weights 带 Code
│   ├── hot2.yaml                             # ListenOn 8084 / Prometheus 19092 / Weights 缺 Code
│   └── hot3.yaml                             # ListenOn 8085 / Prometheus 19093 / Weights 缺 Code
├── internal/
│   ├── config/config.go                      # RpcServerConf + RedisConf + Kq*Conf + HeavyKeeper + Interaction
│   ├── svc/service_context.go                # redis client + kq.Pusher（零调用）
│   ├── server/hot_service_server.go          # 5 个 gRPC 方法 → logic
│   ├── logic/
│   │   ├── get_hot_articles_logic.go         # 读 Redis ZSET hot:articles，TopK 20/100
│   │   ├── like_article_logic.go             # UNIMPLEMENTED
│   │   ├── comment_article_logic.go          # success=true（与另外 3 个不对称）
│   │   ├── coin_article_logic.go             # UNIMPLEMENTED
│   │   └── share_article_logic.go            # UNIMPLEMENTED
│   ├── mqs/
│   │   ├── types.go                          # ArticleHotEvent{article_id, type}
│   │   └── hot_handler.go                    # sarama ConsumerGroupHandler + HeavyKeeper 生命周期 + Redis 快照 + 分布式锁
│   └── metrics/metrics.go                    # promhttp on :9091（零调用点）
└── pb/                                       # proto/hot.proto 的生成物（hot.pb.go / hot_grpc.pb.go）

强依赖（不在 rpc 目录下，但只被 rpc 引用）：
service/hot/heavykeeper/heavykeeper.go        # 带衰减的 Top-K sketch（FNV 指纹 + depth×width 桶表 + 最小堆）
service/hot/heavykeeper/heavykeeper_test.go

不迁移（测试脚手架，非运行期组件）：
service/hot/test/cmd/loadtest/loadtest.go     # 10 并发 sarama producer + 延迟统计
service/hot/test/cmd/producer/producer.go     # 单 producer 灌 5 文章 × 4 类型
service/hot/test/scripts/{start-cluster,start-single,check_redis}.sh
```

**关于三份 etc 配置**（`hot.yaml` / `hot2.yaml` / `hot3.yaml`）：三者共享**同一个 etcd 注册 key `hot.rpc`**、**同一个 Kafka consumer group `article-hot-group`**、**同一份 topic `article-hot-events`**、**同一套 `HeavyKeeper` / `Interaction` 参数**，只差 `ListenOn`（8083/8084/8085）与 `Prometheus.Port`（19091/19092/19093）。它们是同一个 consumer group 的 **3 个副本**，靠 Kafka rebalance 把 3 个分区各自分给一个实例；每个实例内部是「每分区一个 sketch」，rebalance 时把该分区的 sketch 落盘到 Redis 并在重新获得分区时从 Redis 回灌。
**唯一实质差异**：`hot2/hot3.yaml` 的 `Weights` 少了 `Code:` 字段 → 这两个配置下 `IndexWeight.Code` 全为 0。因为 `hot.go:47-50` 是**按 `Name` 建 map 的、`Code` 从未被读取**，所以今天无害；但它是三份配置之间的真实漂移，移植时必须保留 `Code` 字段（否则配置结构与 Go 不一致，将来按 Code 分发时无据可依）。

---

## 12.3 第三部分：Go 文件职责分析

### `hot.go`
进程编排，四个决策点：
1. `rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)` → `zrpc.RpcServerConf.Timeout` 的 json 默认值是 **2000ms**，`c.SetUp()` 前把它清 0，于是 **2 秒服务端 deadline 由 observability 拦截器施加**（Go `service/common/observability/rpc.go:25-40`，timeout>0 才会包 `context.WithTimeout`）。
2. reflection **只在 `Mode` 为 dev/test 时注册**；三份 yaml 都没写 `Mode`，默认 `pro` → **生产不暴露 reflection**。
3. 权重表**按 `Name` 建 map**（`hot.go:47-50`），`Code` 被丢弃。
4. **原生 sarama consumer group**（不是 go-queue 的 `kq.NewConsumer`）：`Version=V2_6_0_0`、`Rebalance.Strategy=RoundRobin`、`Consumer.Offsets.Initial=OffsetNewest`；`for { cg.Consume(...) }` **无退避的无限重试**，`Consume` 返回错误只打日志。**新 group 从最新位点开始 → 历史事件不回放**（L1 语义）。`sarama.NewConsumerGroup` 失败直接 `panic`。
- **L2**：`service.ServiceGroup` 里**只有 gRPC server**；consumer 是裸 `go func`，**不在 group 内** → 进程收到信号时 consumer 不受 `serviceGroup.Start()` 的同启同停约束。

### `etc/hot*.yaml`（三份）
`RedisConf{Host: redis:6379, Type: node}`（无密码）；`KqPusherConf{Topic: article-hot-events}`（`svc` 建了 pusher 但**零调用**）；`KqConsumerConf{Name/Brokers/Topic/Group}`（`Name` 未被 hot 读取）；`HeavyKeeper{Width:1000, Depth:5, Decay:0.9, TopK:100}`；`Interaction{SyncEvery:100, TTL:3600, PartitionCount:3, Weights[like=1, comment=2, coin=3, share=4]}`。
- **L2 硬约束**：`PartitionCount: 3` 必须等于 topic 的实际分区数。`mergePartitions` 只读 `p0..p2`；若 topic 分区数 >3，`p3…` 的快照照写不误但**永远不参与合并** → 那部分文章不会出现在 `hot:articles` 里。
- **L2**：`TTL: 3600` **只用于合并后的大 key `hot:articles`**；分片 key `hot:articles:p{i}` 的 EXPIRE 是**硬编码 300 秒**（`hot_handler.go:232`）。

### `internal/config/config.go`
`Config = RpcServerConf + RedisConf{Host,Type(default node),Pass} + KqPusherConf + KqConsumerConf + HeavyKeeper + Interaction{SyncEvery(100),TTL(3600),PartitionCount(3),Weights}`。
- **L2**：`RedisConf.Type` 是**死配置**——`svc/service_context.go:17-20` 只用了 `Addr` 和 `Password`，`Type` 从未被读取。

### `internal/svc/service_context.go`
`redis.NewClient(&redis.Options{Addr, Password})`（**不设 DB / 连接池 / 超时 / TLS**）+ `kq.NewPusher(brokers, topic)`。
- **L2**：`KqPusher` **零调用点**（唯一提及是 `like_article_logic.go:41` 的注释示例）。真源已死，**不迁移**。

### `internal/logic/get_hot_articles_logic.go`
`k<=0 → 20`、`k>100 → 100`；`ZREVRANGE hot:articles 0 k-1 WITHSCORES`。
- **L2 三条不对称**：① `redis.Nil`（key 不存在）→ **返回空 items、不报错**；② 其他 Redis 错误 → **原样返回 err**（不是业务码，落到 gRPC `UNKNOWN`）；③ member 非字符串 → `Slowf` 后**跳过**。
- **L2**：`HotScore: uint32(z.Score)` —— Redis score 是 float64，这里**向零截断**，且 `k=100` 时截断后与原排序可能并列。

### `internal/logic/{like,coin,share}_article_logic.go`
三个文件都只 `return nil, status.Error(codes.Unimplemented, "… not implemented yet")`。注释写明未来要「更新 PG 计数 + 发布 `ArticleHotEvent`」。

### `internal/logic/comment_article_logic.go`
**唯一不同的那个**：`return &pb.CommentArticleResponse{Success: true}, nil`。注释说明方案 A 由评论系统自行推 Kafka，本 RPC 仅作同步备选。
- **L2**：4 个方法里 3 个 `UNIMPLEMENTED`、1 个 `success=true`。**若 Java 侧「统一风格」把它们都改成 Unimplemented（或都改成 success），就是行为变更**。必须逐个照抄。

### `internal/mqs/types.go`
`ArticleHotEvent{ArticleID string "article_id"; Type string "type"}`，**只有 2 个字段**。测试脚手架 `test/cmd/producer/producer.go:18-23` 实际发的是 **4 字段**（多 `user_id` / `timestamp`）——Go `json.Unmarshal` 默认忽略未知字段，所以生产侧多发字段不会炸。

### `internal/mqs/hot_handler.go`（本组件的核心）
**结构**：`HotHandler{svcCtx, instanceID="hostname-pid", topic, hkMap map[int]*HeavyKeeper, hkMu RWMutex, counter atomic.Int64, syncEvery, hotTTL, weights, lockCancel}`。**构造函数里直接 `go periodicSync` + `go periodicMerge`**（`hot_handler.go:76-77`）。

**消费路径**（`ConsumeClaim:101-111`）：
- 每条消息包一层 `observability.TraceConsumer`，属性含 `messaging.kafka.partition` 与 `messaging.message.key`。
- **`sess.MarkMessage(msg, "")` 无条件执行**——即使 `consumeMessage` 返回错误，offset 也会被标记 → **反序列化失败 / 缺 article_id / 未知 type 三类坏消息被静默丢弃，永不重试**（L2 关键）。
- `consumeMessage:142-183`：unmarshal 失败 → 返回 err（仍被 mark）；`ArticleID==""` → 返回 err；`weights[type]` 缺失或 `<=0` → 返回 err；**校验全过之后**才 `eventConsumeTotal.Inc(type)`；分区不在 `hkMap` → **静默 return nil**（事件被接受、被 mark、**既不计数也不入 sketch**）。
- 触发快照的条件是 **全局计数器** `h.counter.Add(1) % h.syncEvery == 0`（`syncEvery=100`）→ **不是每分区计数**，分区越多单分区快照越稀疏。

**分区生命周期**（`OnPartitionsAssigned/Revoked:113-140`）：
- 获得分区：建 sketch + `loadFromRedis`（`ZREVRANGE hot:articles:p{i} 0 -1`，把**合并后的累计分数当作权重 `hk.Add(id, uint32(score))` 灌进一个全新 sketch**）。已有则 `continue`（防重复回灌）。
- 撤销分区：`syncToRedis` 后 `delete`。**持写锁 + 只有 500ms 超时**的 ctx → Redis 慢时快照会被截断而实例已被移出。

**分片快照**（`syncToRedis:195-242`）：`TopK()` 为空 → **不写**（旧 key 保持陈旧）；否则 pipeline `DEL shadow` + `ZADD shadow`，再跑 Lua：`if EXISTS partitionKey == 0 then return 0 end` → `RENAME shadow→partitionKey` → `EXPIRE partitionKey 300`。
- **L2 两个坑**：① `EXISTS` 守卫意味着**某个分片的首次快照会被丢弃**（目标 key 还不存在）；② EXPIRE 是 **300s 硬编码**，与配置里的 `TTL: 3600` 无关 → **空闲超过 5 分钟的分片 key 会消失**，之后 rebalance 回灌读到空。
- 失败路径：`redisSyncTotal.Inc("fail")` 后 return，**不抛出**。

**周期同步**（`periodicSync:244-263`）：**30s 硬编码 ticker**，对每个已持有分区起一个 goroutine 同步。

**合并**（`periodicMerge:338-354` + `mergePartitions:356-415`）：**10s 硬编码 ticker**；先抢分布式锁 `hot:merge:lock`（`SETNX` + TTL 10s，value = `instanceID`；看门狗每 **3s** 用 compare-and-`EXPIRE` 的 Lua 续期；失败 `continue`）；抢到后：
- pipeline 读 `PartitionCount` 个 `hot:articles:p{i}` 各 `ZREVRANGE 0 999`（**每片最多 1000 条**），`Exec` 的**错误被忽略**（`cmds, _`）；
- 按 member **求和** → 按分数降序 → **截断到 100**；
- pipeline `DEL hot:articles:shadow` + `ZADD`，**`Exec` 的错误同样被忽略**；
- Lua：`if GET hot:merge:lock ~= ARGV[1] return 0` → `RENAME shadow→hot:articles` → `EXPIRE hot:articles hotTTL(3600)`；`result==0` 时打日志 `[Merge] lock lost, abort`。
- **L2**：`result, _ := …Int()` 把错误丢掉 → **Redis 脚本执行失败（而非真丢锁）也会被报成「lock lost」**，排障时会指向错误方向。
- **L2 语义代价**：全局 Top-K 是「每片 Top-100 之和再取 100」。一个在每个分片都排 101 名的文章**永远不会出现**；而某片 Top-100 的分数又被当作精确值参与求和。**这是有损 sketch 的二次有损**，不是精确全局 Top-K。

### `internal/server/hot_service_server.go`
5 个方法逐个 `NewXxxLogic(ctx, svcCtx)` 后转发。纯分发，无逻辑。

### `internal/metrics/metrics.go`
`InitMetrics(cfg)` 起一个 `http.Handle("/metrics")` 的 `:9091` 服务。**grep 全仓：该函数零调用点** —— dead code；且端口与 yaml 的 `Prometheus.Port`（19091/19092/19093）无关。真正生效的是 zrpc 启动时 `c.SetUp()` → `prometheus.StartAgent(sc.Prometheus)` 起的 agent，那才是 `article_hot_kafka_consume_total` / `article_hot_redis_sync_total` 两个 `core/metric` 计数的暴露端点。

### `service/hot/heavykeeper/heavykeeper.go`（强依赖 · 必须移植）
带衰减的 heavy-hitters sketch：
- 结构：`width × depth` 的 uint32 桶表 `{fingerprint, count}` + 一个 `map[string]uint32` 的 Top-K + 一个按 count 升序的最小堆，`sync.Mutex` 全局串行化。
- 指纹：`FNV-1a 32`（`fingerprint`）；桶下标：`FNV-1a 64` 追加一个 seed 字节后 `% width`（`hash(key, row)`）。
- `Add(key, weight)` 三分支：**空桶** → 占位并置 `count=weight`；**指纹相同** → `count += weight`；**冲突** → 以概率 `decay^count` 决定「抢过来（`count<=weight` 时同时换指纹）」还是「减 `weight`」。随后取 `maxCount`（只统计指纹匹配行）更新 Top-K。
- Top-K 维护：已在其中 → 覆盖 count 并 **`rebuildHeap()` 全量重建 O(k)**；未满 → 直接入；已满 → 与堆顶比，超出才淘汰。**注释自陈**「TopK 100 时重建是微秒级；扩到万级应改成 key→heapIndex + heap.Fix」。
- **L2 必须精确复刻的三点**：① `count` 是 `uint32`，`b.count += weight` **允许溢出回绕**（Java 必须用 `int` 的二进制补码语义，不能用 `long`）；② `rand.Float64()` 属于概率算法，**测试只能断言相对顺序不能断言精确计数**（Go 的 `_test.go` 正是如此）；③ `TopK()` 返回值按 count 降序，**同 count 之间的顺序不稳定**（来自 Go map 遍历顺序 + 非稳定排序）。
- **L2 与 Redis 的耦合**：`loadFromRedis` 把**外部累计分数当权重** `Add` 进一个空 sketch（`hot_handler.go:279`）。这是「sketch 可从序列化状态恢复」的**错误假设**——sketch 的桶计数与真实次数之间没有可逆关系，回灌得到的是一个**新估计**，不是原值。移植时必须保留这个行为（它决定了重平衡后热度的连续性手感），同时在 javadoc 里写明这是 Go 的既有语义。
- **为什么必须移植**：RAG 全仓没有任何等价物。替换方案（Redis ZADD 直接计数 / Spring Data 的任何现成组件）都会改变行为——ZADD 是**精确计数**（无衰减、无有损），会让停止更新的老文章永久霸榜；`Spring Boot` 也没有 heavy-hitters 的现成实现。**只能逐行移植算法**。
- **`heavykeeper/heavykeeper_test.go` 的覆盖与缺口**：3 条测试分别断言「Top1 是加得最多的」「加权后 A>B」「`TopK=10` 时长度 ≤10」，**全部只断言相对顺序与容量，不断言精确计数**——这正是概率算法唯一可靠的断言方式。缺口：未覆盖 `uint32` 溢出回绕、未覆盖 `loadFromRedis` 的回灌语义、未覆盖 `decay` 概率分支。Java 侧的三条测试（§12.5）就是补这三个缺口。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `hot.go` 进程装配 | `HotRpcApplication`（含 `@EnableScheduling`） | ABSORB | 容器承担同启同停 |
| `hot.go` gRPC server + reflection | `service/impl/HotServiceGrpcImpl` `@GrpcService` | ADAPT | devh 默认注册 reflection（Go 仅 dev/test 注册 —— **已知放宽**，与 `message-rpc/application.yml` 同款留档） |
| `hot.go` 2s 服务端 deadline | `common/spring/RpcServerInterceptorFactory` 现有实现**恒 `Duration.ZERO`** | UNKNOWN | `ObservabilityProperties` 无 rpc timeout 字段，需先扩公共模块；与 `hot/api.md` 同一缺口 |
| `hot.go` `logger.Init` | `common/logger/Logger.init("hot.rpc")` | REUSE | — |
| `hot.go` 权重表按 Name 建 map | `mq/HotEventConsumer` 构造时从配置建 `Map<String,Integer>` | ADAPT | `Code` 字段仍保留在配置里（对齐 3 份 yaml），但不参与分发 |
| `hot.go` etcd 注册 `hot.rpc` | `bootstrap/NacosRegistration`（`SERVICE_NAME="hot-rpc"`） | REUSE | 与 message-rpc/user-rpc 同款 |
| `hot.go` sarama `Offsets.Initial=Newest` | `spring.kafka.consumer.auto-offset-reset=latest` | ADAPT | **L1 语义**：新 group 不回放历史 |
| `hot.go` RoundRobin rebalance | `spring.kafka.consumer.properties.partition.assignment.strategy=RoundRobinAssignor` | ADAPT | Spring 默认 `RangeAssignor` 会改变分区归属，进而改变 `hkMap` 布局 |
| `hot.go` 无限重试 `cg.Consume` | `DefaultErrorHandler` + `ContainerProperties` | ADAPT | 由 listener container 的 rebalance 循环接管；Go 的「无退避紧循环」在 Java 侧不需要显式复刻 |
| `hot.go` 2 个裸 goroutine | `@Scheduled(fixedRate=30s/10s)` | ADAPT | timer → `@Scheduled` |
| `hot.go` 3 份配置 = 3 副本 | `application.yml` + 环境变量（`grpc.server.port`） | ADAPT | 副本数属部署，不属代码 |
| `config/config.go` | `config/HotRpcProperties`（内嵌 `HeavyKeeper` / `Interaction` / `Weight`） | ADAPT | 对齐 `MessageProperties` 的内嵌类写法；`RpcServerConf`/Redis 交给 Spring 属性 |
| `svc` redis client | `StringRedisTemplate`（`opsForZSet` + `execute(RedisScript)`） | REUSE | RAG 已有 Spring Data Redis 先例；Go 只用 `Addr`+`Password` |
| `svc` `KqPusher` | — | — | 零调用点，**不迁移**（Go 侧死配置） |
| `svc` `RedisConf.Type` | — | — | Go 侧从未被读取，死配置 |
| `server` 5 方法分发 | `service/impl/HotServiceGrpcImpl` | ADAPT | — |
| `logic/get_hot_articles_logic.go` | `service/HotRankingQueryService` | MERGE | 唯一有逻辑的 rpc 方法 |
| `logic` 的 `redis.Nil` → 空 items | `HotRankingQueryService` 显式判空 | ADAPT | **不能**让 Redis 异常冒泡（Spring Data 抛 `RedisSystemException`，与 go-redis 的 `redis.Nil` 语义不同） |
| `logic` 的 `uint32(score)` 截断 | `(int) scoreDouble` | ADAPT | 必须向零截断，不能 `Math.round` |
| 4 个 interaction logic | `service/InteractionStubService` | MERGE | 3×UNIMPLEMENTED + 1×success=true 的不对称集中一处便于 review |
| `mqs/types.go` | `mq/ArticleHotEvent`（record，`@JsonIgnoreProperties(ignoreUnknown=true)`） | ADAPT | **必须忽略未知字段**：生产侧实际多发 `user_id`/`timestamp`（`test/cmd/producer.go:18-23`） |
| `hot_handler` 的 sketch map + RWMutex | `mq/HotPartitionSketchRegistry` | SPLIT | 拥有 rebalance 生命周期（assign→回灌 / revoke→落盘→丢弃）与「每分区一个 sketch」不变量 |
| `hot_handler` 的 4 条 Lua + keyspace | `mq/HotRedisSnapshotStore` | SPLIT | 最易漂移的面：shadow/RENAME 原子性、`EXISTS` 守卫、300s vs TTL 两个不同过期时间、锁的 CAS |
| `hot_handler` 的锁 + 合并算术 + 10s tick | `mq/HotMergeScheduler` | SPLIT | 分布式锁生命周期与合并求和是独立关注点 |
| `hot_handler` 的消费 + 校验 + 计数 | `mq/HotEventConsumer` `@KafkaListener` | NEW | 消息入口，错误契约（永远不抛）与 Go 一致 |
| `hot_handler` 的 span 包装 | `common/observability/MqConsumer.traceConsumer` | REUSE | 对应 `observability.TraceConsumer` |
| `heavykeeper.go` 全部算法 | `domain/HeavyKeeper` | NEW | **必须移植**，RAG 无等价物（§12.3 论证） |
| `heavykeeper.Config` | `config/HotRpcProperties.HeavyKeeper`（`@NestedConfigurationProperty`） | ADAPT | Spring 绑定替代值对象 |
| `heavykeeper.Item` | `HeavyKeeper.Item` 嵌套 record | MERGE | 只被 `TopK()` 返回，内嵌避免单行文件 |
| `metrics/metrics.go`（`InitMetrics`） | — | — | 零调用点，不迁移 |
| 两个 `core/metric` 计数器 | `metrics/HotRpcMetrics` | ADAPT | `article_hot_kafka_consume_total{type}` / `article_hot_redis_sync_total{status}`，经 Actuator Prometheus 暴露 |
| gRPC span | `common/spring/RpcServerInterceptorFactory` | **ADAPT** | 需显式 `@Import`（见 §12.1 更正） |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/hot/hot/rpc/
├── HotRpcApplication.java
├── bootstrap/
│   └── NacosRegistration.java
├── config/
│   └── HotRpcProperties.java            # @ConfigurationProperties("hot-rpc") + HeavyKeeper / Interaction / Weight 内嵌
├── domain/
│   └── HeavyKeeper.java                 # sketch 算法 + 嵌套 Item record
├── metrics/
│   └── HotRpcMetrics.java
├── mq/
│   ├── ArticleHotEvent.java
│   ├── HotEventConsumer.java            # @KafkaListener：校验 → 计数 → registry.add → 触发快照
│   ├── HotPartitionSketchRegistry.java   # partitionId → HeavyKeeper，assign/revoke，回灌
│   ├── HotRedisSnapshotStore.java       # 全部 Redis keyspace + 4 段 Lua + @Scheduled 30s
│   └── HotMergeScheduler.java           # @Scheduled 10s + 分布式锁 + 跨分片求和合并
└── service/
    ├── HotRankingQueryService.java      # GetHotArticles
    ├── InteractionStubService.java      # 4 个互动方法的既定行为（含不对称）
    └── impl/
        └── HotServiceGrpcImpl.java
```

### 新增包说明（§3.3 规则 8）

`mq/` 是**新增包**，理由：
1. **职责无法归入现有包** —— `service/` 承载业务用例（被 gRPC 调用）、`service/impl/` 承载 gRPC 契约边界；Kafka 消费有独立的生命周期（listener container）、独立的错误契约（**所有失败都必须被吞掉**，否则会触发重试，改变 Go 的 drop 语义）、独立的 rebalance 回调语义，以及两条各自独立的定时任务。
2. **目标项目实际依据** —— RAG 无 Spring Kafka 先例；`common/observability/MqConsumer` 只提供 span 包装，不提供消费框架。引入后 listener 的 ack 模式、并发度、分区分配策略、重试策略都是需要显式声明的独立关注点。
3. **不因 Go 有同名文件而新增** —— Go 的目录名是 `mqs`，此处是 `mq`；未沿用 Go 的 `handler/logic/middleware/servicecontext` 任何一类目录。
4. **与 `FileTree/article/rpc.md` 保持同一口径**：article 的 `mq/` 是 Kafka 消费者的落点，本模块的 `mq/` 是同一个角色的落点。

**`domain/` 的用法说明**：`message-rpc` / `user-rpc` 的 `domain/` 目前放的是 `@TableName` 实体；hot-rpc **没有任何数据库表**，唯一的领域模型就是这个 sketch。放进 `domain/` 是因为它是「热度排序」这个领域概念的内存表示，不是基础设施、不是 DTO、不是用例。**不额外开 `sketch/` 子包**——为一个文件再开一层包没有收益。

**刻意不建的包/文件及理由**：

| 未建 | 理由 |
|---|---|
| `mapper/` | 无数据库访问 |
| `exception/` | Go 侧这些路径**全部只 log 不 throw**（`syncToRedis` 失败 → metric + return；`mergePartitions` 失败 → log）。新建异常类会把「吞错」变成「抛出」，是行为变更 |
| `client/` | hot-rpc 无下游 gRPC 客户端 |
| `config/HeavyKeeperProperties.java` 等独立文件 | 按 `MessageProperties` 先例用静态内嵌类，避免 3 个各只有几个字段的文件 |

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `HotRpcApplication` | 入口；`scanBasePackages` 含 `com.rag.common`；`@EnableScheduling` | `hot.go` main | `@EnableScheduling` 是 30s/10s 两条定时任务的显式开关 |
| `bootstrap/NacosRegistration` | `ApplicationReadyEvent` 注册 + `@PreDestroy` 反注册 | `zrpc.MustNewServer` 的 etcd 注册 | 与 message-rpc/user-rpc 同款；纯 gRPC 进程不引入 nacos-discovery 自动注册（会顺带拉起 web 容器） |
| `config/HotRpcProperties` | `HeavyKeeper{width,depth,decay,topK}` + `Interaction{syncEvery,ttl,partitionCount,weights[]}`（权重含 `code` 字段但只按 `name` 分发） | `config/config.go` + `heavykeeper.Config` | 分段配置独立变化；`RpcServerConf` 与 Redis 交给 Spring 属性 |
| `domain/HeavyKeeper` | `add(key, weight)` 三分支 + 冲突衰减 + Top-K 最小堆；`topK()` 降序返回 `List<Item>`；`uint32` 回绕语义 | `heavykeeper/heavykeeper.go` 全部 | **必须独立**：它是本组件唯一的领域算法，可脱离 Spring/Redis 单测；javadoc 需标注 Go 出处 + 三条必须精确复刻的语义（uint32 回绕 / 概率不可断言精确值 / 同 count 顺序不稳定）+ `rebuildHeap` 的已知 O(k) 成本 |
| `metrics/HotRpcMetrics` | 2 个 Micrometer counter | `hot_handler.go:30-45` 的两个 `metric.CounterVec` | 指标名与标签值属 L1（Prometheus 维度） |
| `mq/ArticleHotEvent` | `record(String articleId, String type)` + `@JsonIgnoreProperties(ignoreUnknown=true)` | `mqs/types.go` | 消息契约；**忽略未知字段是硬要求**（生产侧多发 2 个字段） |
| `mq/HotEventConsumer` | `@KafkaListener`：反序列化 → 3 类校验 → `consumeTotal` → `registry.add(partition,id,weight)` → 全局计数器 %100 触发快照；**任何异常都不外抛** | `hot_handler.go` 的 `ConsumeClaim` + `consumeMessage` + `Setup`/`Cleanup` 的 assign/revoke 触发 | 消息入口；**「永不外抛」是 Go 无条件 `MarkMessage` 的等价物**，必须写在类注释首行并配测试 |
| `mq/HotPartitionSketchRegistry` | `Map<Integer, HeavyKeeper>` + `ReentrantReadWriteLock`；assign 时建 sketch 并 `loadFromRedis` 回灌（已有则跳过）；revoke 时落盘后移除 | `hkMap` / `OnPartitionsAssigned` / `OnPartitionsRevoked` / `loadFromRedis` | 拥有 rebalance 生命周期与「每分区一个 sketch」不变量；回灌的**有损语义**在这里注释 |
| `mq/HotRedisSnapshotStore` | 分片快照（shadow + `EXISTS` 守卫 + `RENAME` + `EXPIRE 300`）；回灌读；`@Scheduled(fixedRate=30s)` 全分区扫描；4 段 Lua 常量 | `syncToRedis` / `loadFromRedis` / `periodicSync` | 全部 Redis keyspace 与 Lua 集中一处，是最易漂移面；两个过期时间（300s 与 `hotTTL`）必须并列注释，否则必被「统一」掉 |
| `mq/HotMergeScheduler` | `@Scheduled(fixedRate=10s)`；`SETNX` 抢锁（TTL 10s）+ 3s 看门狗 CAS 续期 + CAS 释放；读 N 片各 top-1000 → 求和 → 降序 → 截 100 → shadow → CAS `RENAME` 到 `hot:articles` + `EXPIRE hotTTL` | `periodicMerge` / `acquireLockWithWatchDog` / `watchDog` / `releaseLock` / `mergePartitions` | 分布式锁与合并算术是两个独立关注点；**「Redis 脚本报错被误报成 lock lost」这一 Go 缺陷要照抄并注释**，不能顺手改成抛异常 |
| `service/HotRankingQueryService` | TopK 归一（≤0→20、>100→100）→ `ZREVRANGE hot:articles 0 k-1` → 空 key → 空列表 → 非字符串 member 跳过 → `HotScore` 向零截断 | `logic/get_hot_articles_logic.go` | 唯一有逻辑的 rpc 方法 |
| `service/InteractionStubService` | like/coin/share → `Status.UNIMPLEMENTED`；comment → `success=true` | 4 个 `*_logic.go` | 把**不对称**集中到一处，让 review 能一眼看到「3 个未实现、1 个返回成功」；将来 4 个方法整体替换时改一个文件 |
| `service/impl/HotServiceGrpcImpl` | `@GrpcService`，5 个 override，`invoke(Supplier)` 统一兜底 | `server/hot_service_server.go` | gRPC 契约边界 |

### 必配测试（§3.3）

| 测试 | 触发什么 |
|---|---|
| `HeavyKeeperTest` | 基本 Add / 权重 Add / TopK 容量上限；**只断言相对顺序**（对齐 Go `_test.go` 的断言强度） |
| `HeavyKeeperOverflowTest` | `count += weight` 溢出回绕（`uint32` 语义），防止被改成 `long` |
| `HeavyKeeperLoadFromRedisTest` | 回灌是「新估计」而非原值——把该行为锁死，避免将来被「修正」成精确计数 |
| `HotEventConsumerDropTest` | 坏 JSON / 空 article_id / 未知 type 三类**都不外抛**，且 offset 仍被提交（等价 Go 的无条件 `MarkMessage`） |
| `HotRedisSnapshotStoreIT`（真实 Redis / Testcontainers） | shadow→`RENAME` 原子性；`EXISTS` 守卫导致首次快照被丢；`EXPIRE 300` 与 `hotTTL` 是两个值。**纯 Mockito mock 不算**（§3.3：不执行真实命令路径） |
| `HotMergeSchedulerIT` | 抢锁 / 续期 / CAS 释放；合并求和与截断 100；**丢锁时不写** |
| `HotRpcWireContractTest` | `HotServiceGrpc` 5 个方法签名（proto 已冻结） |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `hot.go` | `HotRpcApplication` | ABSORB | 进程装配 |
| `hot.go`（etcd 注册） | `bootstrap/NacosRegistration` | REUSE | 与 message-rpc 同款 |
| `hot.go`（reflection，dev/test 才开） | devh starter 默认开启 | ADAPT | 已知放宽，与 `message-rpc/application.yml` 留档一致 |
| `hot.go`（2s 服务端 deadline） | `RpcServerInterceptorFactory` 恒 `ZERO` | UNKNOWN | 公共模块缺 rpc timeout 属性 |
| `hot.go`（权重表按 Name 建 map） | `HotEventConsumer` 构造 | ADAPT | — |
| `hot.go`（sarama `OffsetNewest`） | `auto-offset-reset=latest` | ADAPT | L1 语义保留 |
| `hot.go`（RoundRobin） | `RoundRobinAssignor` | ADAPT | 改变分区归属即改变行为 |
| `hot.go`（无限重试 `Consume`） | listener container 的 rebalance 循环 | ADAPT | 不显式复刻紧循环 |
| `hot.go`（2 个裸 goroutine ticker） | `@Scheduled` 30s / 10s | ADAPT | 间隔与「每次每分区起一个 goroutine」的并发形态保留 |
| `hot.go`（consumer 不在 ServiceGroup 内） | Spring 容器统一生命周期 | ADAPT | **表达层收敛**：Go 侧 consumer 不受优雅停机约束，Java 侧会随容器关闭——需在注释里标注为有意差异 |
| `etc/hot.yaml` | `application.yml` + `NacosRegistration` | ADAPT | — |
| `etc/hot2.yaml` / `hot3.yaml` | **不作为三份文件** → 同一部署 ×3 副本 | MERGE | 差异只有端口；`Code` 字段的缺失在配置模型里保留字段本身来吸收 |
| `config/config.go`（RpcServerConf / RedisConf） | `application.yml` 的 `grpc.server.*` / `spring.data.redis.*` | ABSORB | Spring 原生属性 |
| `config/config.go`（HeavyKeeper / Interaction / Weights） | `config/HotRpcProperties` | ADAPT | 含仍不参与分发的 `code` 字段 |
| `svc`（redis client） | `StringRedisTemplate` | REUSE | RAG 已有先例；ZSet + Lua 子集开箱可用 |
| `svc`（`KqPusher`） | — | — | 零调用点，不迁移（Go 侧死配置，非 ABSORB） |
| `svc`（`RedisConf.Type`） | — | — | Go 侧从未被读取，死配置 |
| `server/hot_service_server.go` | `service/impl/HotServiceGrpcImpl` | ADAPT | 分发由 starter 完成 |
| `logic/get_hot_articles_logic.go` | `service/HotRankingQueryService` | MERGE | 唯一有逻辑的方法 |
| `logic/like_article_logic.go` + `coin` + `share` | `InteractionStubService`（3 个方法） | MERGE | 3 个同质 `UNIMPLEMENTED` |
| `logic/comment_article_logic.go` | `InteractionStubService.commentArticle` | MERGE | **返回值与另外 3 个不同**，独立方法保留差异 |
| `mqs/types.go` | `mq/ArticleHotEvent` | ADAPT | 加 `ignoreUnknown` |
| `mqs/hot_handler.go`（消费 + 校验 + 计数） | `mq/HotEventConsumer` | SPLIT | 消息入口独立 |
| `mqs/hot_handler.go`（`hkMap` + assign/revoke + 回灌） | `mq/HotPartitionSketchRegistry` | SPLIT | rebalance 生命周期独立 |
| `mqs/hot_handler.go`（`syncToRedis` / `loadFromRedis` / `periodicSync` / 4 段 Lua） | `mq/HotRedisSnapshotStore` | SPLIT | keyspace + 原子性语义独立 |
| `mqs/hot_handler.go`（锁 + 看门狗 + `mergePartitions`） | `mq/HotMergeScheduler` | SPLIT | 分布式锁与合并算术独立 |
| `mqs/hot_handler.go`（`observability.TraceConsumer`） | `common/observability/MqConsumer` | REUSE | 属性用 `Map<String,Object>` |
| `mqs/hot_handler.go` 的两个 `core/metric` | `metrics/HotRpcMetrics` | ADAPT | Micrometer + Actuator |
| `metrics/metrics.go`（`InitMetrics`，零调用） | — | — | Go 侧死代码，不迁移（非 ABSORB） |
| `heavykeeper/heavykeeper.go` | `domain/HeavyKeeper` | NEW | **RAG 无先例，必须移植** |
| `heavykeeper/heavykeeper.go`（`Config`） | `config/HotRpcProperties.HeavyKeeper` | ADAPT | Spring 绑定 |
| `heavykeeper/heavykeeper.go`（`Item`） | `HeavyKeeper.Item` 嵌套 record | MERGE | 只被 `topK()` 返回 |
| `heavykeeper/heavykeeper_test.go` | `HeavyKeeperTest` / `HeavyKeeperOverflowTest` / `HeavyKeeperLoadFromRedisTest` | SPLIT | 按 §3.3 拆成 3 条，覆盖 Go 单条测试未覆盖的溢出与回灌语义 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 5 个 gRPC 方法与完整 proto 字段号；`article-hot-events` topic；consumer group `article-hot-group`；Redis key `hot:articles` / `hot:articles:p{i}` / `…:shadow` / `hot:merge:lock`；`HeavyKeeper` 四参数与 `Interaction` 四个参数及其默认值 |
| L2 业务语义有承载位置 | ✅ 按分区一个 sketch、rebalance 时回灌/落盘、TopK 截断 100、合并求和、`auto-offset-reset=latest`、`OffsetNewest` 不回放、`EXISTS` 守卫导致首次快照被丢、300s 与 `hotTTL` 两个过期时间、4 个互动方法的返回值不对称 |
| L3 技术语义有承载方案 | ✅ 分布式锁（`SETNX` + CAS 续期 + CAS 释放，TTL 10s / 续期 3s）、合并节流 10s、快照节流 30s 或每 100 事件、限流的 Lua 原子性、`uint32` 回绕 |
| 业务分支与异常路径已纳入 | ✅ unmarshal 失败 / 空 `article_id` / 未知或零权重 type / 分区不在 `hkMap`；`redis.Nil` vs 其它 Redis 错误；非字符串 member；脚本 `result==0` |
| 框架吸收有机制依据 | ✅ 进程装配→Spring Boot；etcd→Nacos；sarama→`spring-kafka`（`Infrastructure.md` §5.6 已决策）；`core/metric`→Micrometer；ticker→`@Scheduled`；`kq.Pusher`→无（死代码） |
| 复用组件已明确 | ✅ `RpcServerInterceptorFactory`、`MqConsumer`、`Logger`、`StringRedisTemplate`、`NacosRegistration` 形态 |
| 新增文件有职责依据 | ✅ `mq/*`（消息入口生命周期与错误契约）、`domain/HeavyKeeper`（唯一领域算法，需隔离单测）、锁/快照/registry 三个切面——均非「Go 有同名文件」 |
| 合并有组织依据 | ✅ 3 个同质 `UNIMPLEMENTED` logic 合 1；`Config` 合 1 |
| 拆分可解释 | ✅ 消费 / sketch 生命周期 / Redis keyspace / 合并调度 四者变化原因互不相同 |
| 无「为对应 Go 文件而建」的文件 | ✅ 未创建 `Server.java` / `ServiceContext.java` / `Types.java` / `Metrics.java` / `Config.java`；`exception/`、`mapper/`、`client/` 三个看似该有的包被**显式论证为不需要** |
| 未把未实现能力说成现有机制 | ✅ `hot-rpc` 现状是「零 Java + 零依赖」；Spring Kafka 标注为「需首次引入」；2s deadline 缺口标为问题 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **`hot-rpc/pom.xml` 零依赖** | 连 `grpc-server-spring-boot-starter` 都没有。需补 `grpc-server-spring-boot-starter`、`spring-kafka`、`spring-boot-starter-data-redis`、`hot-proto`、`common`、Actuator + Prometheus |
| 2 | **Spring Kafka 首次引入，无版本基线** | RAG 全仓零引用，父 pom 无 `spring-kafka` 版本管理。需先定版本并确认与 Spring Boot 4.0.8 / Kafka client 的兼容矩阵；`spring-kafka` 的 BOM 不在 `spring-cloud-dependencies` 里 |
| 3 | **2 秒服务端 deadline 无处安放** | Go `RpcServerConf.Timeout` 默认 2000ms，被 `DisableNativeRpcTimeout` 转移给 observability 拦截器。RAG 的 `RpcServerInterceptorFactory` 硬编码 `Duration.ZERO`（不限时）。`ObservabilityProperties` 只有 `slowThreshold`。**与 `hot/api.md` 的 3s HTTP 超时是同一个公共模块缺口**，需协调 `service/common` 补 timeout 属性 |
| 4 | **优雅停机语义被"顺手修好"** | Go 的 consumer 是裸 goroutine，**不在 `service.ServiceGroup` 内**，收到信号时不参与同启同停；Spring 的 listener container 会随容器关闭。若不标注，这是一处无声的语义变更 |
| 5 | **坏消息 drop 语义极易被"修好"** | Go 无条件 `MarkMessage` 导致三类坏消息永久丢失。Spring Kafka 默认 `DefaultErrorHandler` 会 seek + 重试。**必须显式关闭重试**（吞异常或 `AckMode` + 无 error handler），否则一条坏消息会卡住分区 |
| 6 | **`PartitionCount` 与真实分区数必须一致** | 配置写死 3。topic 分区数 >3 时 `p3…` 的快照照写不合并，那些文章永远不出现在 `hot:articles`。Go 侧同样有该问题，**不修**，但需在配置注释里写明这个约束 |
| 7 | **有损 sketch 的二次有损被"修好"** | 回灌把合并分数当权重灌进空 sketch；合并又对各片 Top-100 求和。两处都不是精确 Top-K。若实现时换成「Redis ZADD 精确计数」，热榜语义会完全改变（老文章永霸榜） |
| 8 | **`mergePartitions` 的错误被误报成 `lock lost`** | `result, _ := …Int()` 丢弃 Redis 脚本错误。照抄并注释；不得改成抛异常 |
| 9 | **`interaction` 的 4 个方法值不值得留** | proto 已冻结，方法必须存在。但 3 个 `UNIMPLEMENTED` + 1 个 `success=true` 的不对称在未来接入 like/comment/coin/share 时会全部改写。需确认是否直接保留桩实现 |
| 10 | **`hot-rpc` 是否需要 HTTP 端点** | Go 的 gRPC server 顺带起 Prometheus agent。RAG 的 gRPC 进程加 Actuator 需 `spring-boot-starter-web`（message-rpc 就引了 `spring-boot-web-server`）。需确认是否接受为拿 `/actuator/prometheus` 而引入 web 容器 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| **端口与副本数** | `Infrastructure.md` §3 未给 hot-rpc 分配 gRPC 端口。Go 的 8083/8084/8085 在 Java 侧应变成 3 副本共用一个端口 + 环境变量 |
| **`kafka-go`（kq.Pusher）已被 Spring Kafka 覆盖** | Go 侧 producer 用 `segmentio/kafka-go`（Snappy + LeastBytes balancer + chunk executor），consumer 用 `sarama` —— **两个不同的客户端**。真源里 pusher 是死的，落地时只需 `spring-kafka` 的 producer 能力，用于将来 like/comment 等服务发事件时的分区键选择（Go 示例代码未指定 key）——**是否会沿用 Kafka 默认分区器**需拍板 |
| **`Topic` 的实际分区数** | 生产环境的 `article-hot-events` 分区数未知，直接决定 `PartitionCount: 3` 是否正确 |
| **`Type` 标签的取值域** | `eventConsumeTotal` 的 `type` 标签与 `weights` 的 key 同源；配置里没有的 type 会被拒绝并 drop，所以标签取值域 = 配置里 `Name` 的集合 |
| **`slowThreshold` 与 `syncEvery` 的关系** | 消费侧只对 `>100ms` 的单条消息打慢日志（`hot_handler.go:146`），与全局 `SlowThreshold`（500ms 默认）无关。Java 侧是否保留这个 100ms 的局部阈值需确认 |
| **`interaction` 方法的未来语义** | like/coin/share 的注释要求「更新 PG 计数 + 发 Kafka」，但 PG 更新属于 article 域还是 hot 域、是否走 gRPC，Go 侧只写了 TODO |
| **`code` 字段的最终用途** | `IndexWeight.Code` 在 Go 侧零读取，但 `hot2/hot3.yaml` 的缺失说明它曾被规划过。是否保留该字段属 wire 外的配置契约 |