# like-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/like/rpc`（含嵌套 `internal/mq` 与 `cmd/*` 三个独立进程）
> 目标根包：`com.rag.like.like.rpc`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/like/like-rpc` **只有 `pom.xml`，零 Java 文件**；`service/like/like-rpc/pom.xml` **没有任何 `<dependencies>`**。`service/like/` 整棵树实测只有 4 个 pom + `like-proto/src/main/proto/like.proto`。该 proto 与 Go `proto/like.proto` 逐行比对：**字段号、message 名、enum 值全部一致**，只多了两行 `option java_package = "com.rag.like.like.proto"` 与 `option java_multiple_files = true`。即 **L1 wire 契约已就位，0 漂移**。

包结构依据来自同仓库已落地的 rpc 侧模块：

| 参照模块 | 提供的一致性依据 |
|---|---|
| `message/message-rpc`（19 个真实 Java 文件） | `@GrpcService` 落在 `service/impl/MessageRpcServiceImpl.java`；`client/{UserRpcClient,AdminRpcClient}`；`config/MessageProperties`；`bootstrap/NacosRegistration`；`domain/*Entity` + `mapper/*Mapper`；`exception/MessageExceptions`；`metrics/MessageMetrics`；`resources/db/migration/V1..V3` |
| `users/user/user-rpc` | `bootstrap/SnowflakeBootstrap`；`config/MybatisPlusOrderingBridgeAutoConfiguration`；`mapper/JsonbTypeHandler`；`mapper-underscore-to-camel-case` + `id-type: ASSIGN_ID`（`application.yaml`） |
| `users/admin/admin-rpc` | 多步事务抽独立 Bean 的形态（`AdminCreateAtomicService`）；`StringRedisTemplate` 注入（`AdminDomainService.java:90`） |
| `service/common` | `communityfact/{Wire,Dispatcher,AuthorityHandler,ProcessLogger}`；`observability/{MqConsumer,Observability,RpcInterceptor}`；`spring/{AuthorityHandlerFactory,RpcServerInterceptorFactory,SnowflakeBean,HttpMiddlewareFilter}`；`snowflake/Snowflake`；`logger/Logger` |

### 已有组件（跨模块可复用）

**逐条核对 `service/common` 的 communityfact 四个文件后，结论是「信封 REUSE、payload NEW、编排 REUSE」三层：**

| 组件 | 字段级核对结果 | 判定 |
|---|---|---|
| `Wire.Event`（`Wire.java:87-99`） | `event_id/event_type/schema_version/producer/aggregate_id/aggregate_version/operation_id/occurred_at/payload` —— 与 Go `communityfact/wire.go:38-48` 的 `Event` struct **逐字段逐 tag 一致**（含 `AggregateVersion` 为 `long` 而非 `Long`） | **REUSE** |
| `Wire.TechnicalReceipt`（`Wire.java:102-112`） | 与 Go `wire.go:50-58` 七字段一致 | **REUSE** |
| `Wire.SubjectRef` / `Wire.AuthorityFact`（`Wire.java:116-132`） | `issuer/subject_id`；`event/subject_ref/predecessor_event_id(NON_EMPTY)/technical_receipt/source_event_hash` —— 与 Go 一致 | **REUSE** |
| `Wire.strictDecode` / `canonicalHash` / `validateReceipt` / `validHash` / `parseRTWSubject` | 与 Go `wire.go` 对应函数 1:1（JCS + SHA-256 hex、`DisallowUnknownFields` + 拒绝尾随 JSON） | **REUSE** |
| `Wire.DeliveryStatus`（`Wire.java:37-61`） | `0/1/2/3` 与 Go `DeliveryPending/Accepted/Failed/Blocked` 一致 | **REUSE** |
| `Wire.DCEventSender.create`（`Wire.java:157-172`） | endpoint 必须是 `/v1/events`、token ≥ 32 —— 与 Go `NewDCEventSender` 一致 | **REUSE** |
| `Wire.ERR_FROZEN_ENVELOPE_BLOCKED` / `ERR_AUTHORITY_UNAVAILABLE`（`Wire.java:66-72`） | **是预先 new 出来的单例 `RuntimeException`**，靠 `instanceof` 或 message 相等识别（`Dispatcher.java:267-268`、`AuthorityHandler.java:68-77`），而非 Go 的 `errors.Is` | **REUSE（附实现注意）** |
| `Dispatcher.run(...)`（`Dispatcher.java:86-208`） | 批次 16、1s 节奏、单次 10s deadline、`once` 分支、`DispatcherHandle.stop()` —— 与 Go `communityfact/process.go:20-62` 逐条对齐 | **REUSE** |
| `AuthorityHandler.create(...)`（`AuthorityHandler.java:95-102`） | 返回 `HttpServlet`（Go 返回 `http.Handler`），路径 `/internal/v1/community/facts`、Bearer 恒定时间比较、重复 header/query 严格拒绝 | **REUSE** |
| `AuthorityHandlerFactory.register(...)`（`AuthorityHandlerFactory.java:39-49`） | `ServletRegistrationBean` 注册到固定路径 | **REUSE** |
| `ProcessLogger.newProcessLogger / installLocalTracing / ProcessMetadata.validate` | 与 Go `communityfact/logging.go` 对齐；logger 名由 `service.env.component` 拼成，**同一 JVM 可实例化多份互不串扰**（`ProcessLogger.java:114-117`） | **REUSE（合并后仍保真）** |
| `observability/MqConsumer.traceConsumer`（`MqConsumer.java:71-119`） | SpanKind=CONSUMER + attrs + 超时/系统错误标记 | **REUSE** |
| `observability/RpcInterceptor` + `spring/RpcServerInterceptorFactory`（`:25-37`） | **不能假定自动注册** —— 见下 | **ADAPT** |
| **`likeFact` payload**（Go `domain_fact.go:54-74`，19 字段） | `schema_version/event_id/event_type/producer/aggregate_id/aggregate_version/operation_id/subject_ref/target_type/target_id/target_revision/revision_status/operation/source_ref/old_state/new_state/event_time/occurred_at/available_at` | **NEW** —— `Wire` 没有任何 like 特有 payload 类型 |
| **`common/Snowflake`**（`Snowflake.java:40` `EPOCH = 1288834974657L`） | **不可用于 `generateCursorID`**：Go like 的游标是 `(unixMilli << 22) \| (nodeId<<12) \| seq`，读侧用 `score >> 22` 直接还原**裸 Unix 毫秒**；`common/Snowflake` 减了 epoch，还原出的时间戳会差 15 年 | **禁止复用（见下）** |

**Redis 先例核实结果**：`StringRedisTemplate` **确实**在 `users/user/user-rpc/UserDomainService.java:85` 注入，但全仓只有一处使用 —— `bizRedis.opsForValue().set(key,"banned",ttl)`（`:610`）；`admin-rpc/AdminDomainService.java:169` 同款。**没有任何 Hash / ZSet / Lua `EVAL` 先例**。like-rpc 需要的 6 类 Redis 操作全部无先例。

**Kafka 先例核实结果**：全仓无 `spring-kafka` 依赖、无 listener 容器、无 `KafkaTemplate`。`observability/MqConsumer` 只提供 span 包装（`MqConsumer.java:71`）。**Go 的 `kq`（go-queue）producer/consumer 在 RAG 侧零对应物。**

### 已确认的框架机制

- `net.devh:grpc-server-spring-boot-starter`：`@GrpcService` / `@GrpcClient`
- MyBatis-Plus `BaseMapper` / `@TableName` / `@TableId` / `@TableField(typeHandler=…)`（**jsonb 列必须挂 handler —— `LikeDomainFactOutbox.payload` / `delivery_envelope` 是 jsonb**）
- Spring `@ConfigurationProperties`；Spring Cloud Alibaba Nacos
- Flyway（`spring-boot-flyway` + `flyway-database-postgresql`，见 `user-rpc/pom.xml` 注释：只引 `flyway-core` 会**完全惰性**）
- Micrometer + `MeterRegistry`
- **无 Kafka、无 Spring Data Redis Hash/ZSet/Lua、无 Web（like-rpc 在 Go 侧是纯 gRPC 进程，零 HTTP 监听）**

### 当前 Component 可以直接复用的能力

- `common/communityfact/Wire`（信封 + 校验 + DC sender）
- `common/communityfact/Dispatcher`（1s/批次16/10s 循环，返回 `DispatcherHandle` 供 `@PreDestroy`）
- `common/communityfact/AuthorityHandler` + `spring/AuthorityHandlerFactory`（Servlet 注册）
- `common/communityfact/ProcessLogger`（进程级 OBS-r3 JSON 日志 + 本地 OTel）
- `common/observability/{RpcInterceptor,MqConsumer,Observability}`
- `common/logger/Logger`、`common/exception/BusinessException`
- `common/spring/SnowflakeBean`（仅用于 `KafkaLikeMsg.msgId` 与 outbox `event_id`，**不用于游标**）

### 需要进一步确认的组织约定

**【关键推导】四个 Go `main` 进程 → 一个 Spring Boot JVM**

Go 侧 `service/like/rpc` 下实测有 **4 个 `main` 包**（不是 3 个）：

| Go 进程 | 入口 | 注册了什么 | 读写 |
|---|---|---|---|
| P1 gRPC server | `like.go` | `pb.RegisterLikeServiceServer`（6 RPC） | Redis 读写、DB 读、Kafka 生产 ×3、MessageRpc |
| P2 MQ worker | `internal/mq/mq.go` | `kq` consumer（group `like-update-group`）+ 1s outbox relay | DB 读写 ×5 表、Kafka 生产（`article-hot-events`） |
| P3 fact dispatch | `cmd/fact-dispatch/main.go` | **零服务注册**，只有 `communityfact.RunDispatcher` 循环 | DB 读 + DC HTTP 出网 |
| P4 fact authority | `cmd/fact-authority/main.go` | **零服务注册**，只有 1 个 `HttpServlet` | DB 读 + HTTP 入 |

**结论：P3 与 P4 必须落成 `like-rpc` 内的 Spring 组件，而不是独立 deployable。** 依据（不是"看起来能合并"，而是代码里可验证的约束）：

1. **P3/P4 不注册任何 gRPC 服务**（`fact-dispatch/main.go:76` 只调 `RunDispatcher`；`fact-authority/main.go:71-80` 只 `NewAuthorityHandler` + `net.Listen`）。它们没有对外的强类型契约，只有一个 `@Scheduled`-等价的循环和一个 Servlet。
2. **P3/P4 与 P2 共用同一张 PostgreSQL**：`cmd/*` 读 `os.Getenv("LIKE_DATABASE_URL")`，P2 读 `c.DB.DataSource`，两者都指向 `like_record` / `like_consume_inbox` / `like_outbox_event` / `like_domain_fact_outbox` / `like_fact_stream` 五张表（`model/db.go:15` 的 `AutoMigrate` 与 `cmd/*` 的 SQL 完全一致）。跨进程没有额外好处。
3. **P1 与 P2 是 Kafka 生产/消费对**（`like.yaml:66` 生产 `like_promotion_topic`；`mq.yaml:8` 消费同一 topic）。合并后 Kafka 往返**仍然是真实的**（同进程不等于同管道），consumer group id `like-update-group` 保存在配置里，**横向扩容语义不变**。
4. **`Dispatcher` 与 `AuthorityHandler` 已经是纯库**（`Dispatcher.java:64` 静态工厂、`AuthorityHandler.java:95` 返回 Servlet），它们本来就是设计给"宿主进程"用的——Go 侧 `fact-dispatch` 的注释明写 "keeps retry cadence outside the business RPC process"（`communityfact/process.go:18-19`），这句是 Go 的**架构意图**而非技术约束。

**合并后的部署拓扑后果（必须显式承认，不得当作"纯收益"）：**

| 维度 | Go（4 进程） | Java（1 JVM） | 后果 |
|---|---|---|---|
| 部署单元 | 4 | 1 | 失去独立扩缩容：dispatch 的资源需求现在与 gRPC 争抢同一堆内存/CPU |
| 多副本下的 dispatcher | 独立进程，可与 rpc 副本数解耦 | rpc 扩到 N 副本 → N 个 dispatcher 同时跑 | **安全**：`fact_delivery.go:63-69` 的 `FOR UPDATE SKIP LOCKED` + `NOT EXISTS predecessor` 本身就是为多副本设计的；但吞吐上限从"1 dispatcher 进程"变成"每 JVM 16 并发 × N 副本" |
| `-once` 单次模式（`fact-dispatch/main.go:25`） | 命令行 flag，适合 cron/k8s CronJob | 需降级为配置项 `like.fact.dispatch.once` | **UNKNOWN**：是保留 CronJob 部署形态还是改为常驻循环，需拍板 |
| authority HTTP 监听地址 | 独立 `LIKE_FACT_AUTHORITY_LISTEN` | like-rpc **原本零 HTTP 端口**，要新增 | **wire 面变更**：必须决定是复用 gRPC 端口旁的独立 connector 还是 management port。**UNKNOWN，需拍板** |
| 进程级 OBS 日志身份 | 4 个独立 `service` 值（`like.rpc` / `like-mq` / `rtw-like-fact-dispatch` / `rtw-like-fact-authority`） | 1 个 SLF4J 树 | **可保真**：`ProcessLogger.newProcessLogger` 的 logger 名由 `service.env.component` 拼（`ProcessLogger.java:114-117`），同一 JVM 可建 4 份独立 appender 写不同流。前提是**不用 SLF4J 全局 MDC**（该文件已明确说明 MDC 会跨线程插值） |
| 数据库连接池 | 每进程独立 | 共享一个 HikariCP | P3 的 dispatch 事务 `Begin → HTTP 出网 → Commit`（`fact_delivery.go:57-120`）**持有行锁跨网络 I/O**，与 gRPC 请求共池时，故障域耦合加剧 |
| Flyway 归属 | `AutoMigrate` 无状态，5 张表互不冲突 | 有状态账本 | ✅ 已按 AGENTS.md §6.3（2026-10-21）定案：单一归属 + 全局唯一版本号。like 的 5 张表**独占**，归 like-rpc。⚠️ 但 like 另有 `cmd/fact-authority` 独立 HTTP 端口待定（见同表下一行） |

| 其他待确认项 | 状态 |
|---|---|
| `common/Snowflake` 能否用于 Redis ZSet 游标 | **不能**（epoch 不一致）。必须在 like-rpc 内自实现 `(unixMilli<<22)\|(nodeId<<12)\|seq`，并在 §3.2 标注 Go 出处 |
| Redis 基础设施 | Go 用 `go-zero/core/stores/redis`；RAG 用 Spring Data Redis（Lettuce）。Hash/ZSet/Lua 在 Spring 侧有 API，但 **`ZrevrangebyscoreWithScoresAndLimit` 这种带 min/max/limit 的变体不是标准 `ZSetOperations` 方法**，需 `RedisCallback` 直发 —— **UNKNOWN**：需确认 Lettuce 下的等价写法 |
| `like-rpc` 是否需要 Web 端口 | 由上面 authority 的监听决策决定，**UNKNOWN** |
| like 的 5 张表迁移归属 | **UNKNOWN**（§6.3） |

---

## 12.2 第二部分：Go 原始文件树

```
service/like/rpc/
├── like.go                                    # P1 入口：gRPC server + 关原生超时 + unary interceptor
├── etc/like.yaml                              # Storage{RocksDB,Redis,Postgres} / KafkaConf / HotSpot / AntiBrush / MessageRpc / 3×Pusher
├── likeservice/likeservice.go                 # 生成的 gRPC 客户端桩（api 侧 svc 用它）
├── cmd/
│   ├── fact-authority/main.go                 # P4 入口：env 校验 + AuthorityHandler servlet + 5s 优雅停机
│   └── fact-dispatch/main.go                  # P3 入口：env 校验 + DCEventSender + RunDispatcher(-once)
├── internal/
│   ├── config/config.go                       # 8 个配置段（其中 RocksDB/HotSpot/AntiBrush/KafkaPusher 零消费）
│   ├── svc/servicecontext.go                  # Redis + GORM + 3 个 kq Pusher + LikeModel + MessageRpc
│   ├── server/likeserviceserver.go            # 6 个 RPC → logic 的 thin wrapper
│   ├── logic/
│   │   ├── likeactionlogic.go                 # 自写雪花游标 + 68 行 Lua + Kafka + 3 个 article 分支 + MessageRpc
│   │   ├── getlikecountlogic.go               # HMGET 批量 + 降级回源 + 回填缓存
│   │   ├── getlikestatelogic.go               # 逐 id HGET（N+1）+ 零值回填防穿透
│   │   ├── getuserlikelistlogic.go            # ZREVRANGEBYSCORE + 游标分页 + 回源重建 ZSet
│   │   ├── gettargetlikerlistlogic.go         # 同上（对称实现）
│   │   └── getusertotallikelogic.go           # GET + 命中续期 + 回源 Setex
│   ├── metrics/metrics.go                     # like-rpc_action_total + like_rpc_query_cache_total（Subsystem 名不一致）
│   ├── types/types.go                         # KafkaLikeMsg / ArticleHotEvent / TaskArticleProgressMsg
│   └── model/
│       ├── db.go                              # InitDB + AutoMigrate(5 实体) + log.Fatalln
│       ├── data_model.go                      # LikeRecord / LikeConsumeInbox / LikeOutboxEvent 三个 GORM 实体
│       ├── like.go                            # LikeRecordModel 接口 8 方法 + ProcessLikeMessageBatch + applyLikeMessage
│       ├── like_message_hash.go               # 7 字段 canonical JSON → sha256 hex（消息语义指纹）
│       ├── like_consume_inbox.go              # FindByMsgID / CreateTx / MarkDoneTx
│       ├── like_outbox.go                     # FetchPending(retry<3) / MarkSent / MarkFailed
│       ├── domain_fact.go                     # LikeDomainFactOutbox + LikeFactStream + appendLikeFact（版本分配 + 双信封）
│       ├── fact_delivery.go                   # validLikeDelivery + DispatchFactOnce（SKIP LOCKED + 前序屏障）
│       ├── fact_authority.go                  # AuthoritativeFact（链路重放校验）+ validLikeTransition
│       └── migrations/
│           ├── 001_like_domain_fact_outbox.sql   # last_operation_id + payload_hash + fact outbox 基础 4 列
│           ├── 002_like_dc_wire.sql              # aggregate_id/fact_version/delivery_envelope + like_fact_stream + 2 索引 + NOT VALID CHECK
│           └── 003_like_fact_delivery.sql         # 投递元数据 7 列 + 索引 + 不可变触发器
└── internal/mq/                               # P2 独立进程（嵌套完整 Go 工程）
    ├── mq.go                                  # kq consumer + OutboxRelayService(1s ticker, batch=100)
    ├── etc/mq.yaml                            # like_promotion_topic / group like-update-group / 8 consumers / offset first
    └── internal/
        ├── config/config.go                   # ServiceConf + Kafka + HotEventPusherConf + DB
        ├── svc/servicecontext.go              # GORM + 3 个 model + HotEventPusher
        ├── metrics/metrics.go                 # like_mq_consume_total{action,result}
        └── mqs/
            ├── likemq.go                      # Consume：解析→校验→建 outbox→单事务提交 inbox+record+fact
            └── like_outbox_sender.go          # SendPending：逐条推送 + 单条失败不中断批次
```

---

## 12.3 第三部分：Go 文件职责分析

### `like.go`（P1）
`InitDB` → `svc.NewServiceContext` → `zrpc.MustNewServer` 注册 `LikeServiceServer` → `AddUnaryInterceptors(observability.NewUnaryServerInterceptor(...))`。DevMode/TestMode 下额外注册 reflection（`:37-39`）。
- **L3**：`DisableNativeRpcTimeout` 关掉 go-zero 原生超时，timeout 统一由 interceptor 管。Java 侧 `RpcServerInterceptorFactory` 用 `Duration.ZERO`（不限时）——**仅在它真的被注册时才成立**。
  > ⚠️ **2026-10-20 更正**（依据 `FileTree/common.md` §12.7 #1）：`RpcServerInterceptorFactory` 是 `com.rag.common.spring` 下的 `@Component`，**只有当应用扫描 `com.rag.common` 或显式 `@Import` 它时才会进容器**。实测 `user-rpc`（`@Import` 只含 3 个 bean）与 `admin-rpc`（无 `@Import`、不扫 common）**都没有注册**，因此当前既无 gRPC span 也无 gRPC 超时。like-rpc 必须**显式 `@Import`** 才能拿到，不能沿用「已全局注册」的说法。
  > ⚠️ 若 like-rpc 也坚持不扫 `com.rag.common`（为避开 `HttpMiddlewareFilter` 的 servlet 依赖），则需先按 `common.md` §12.5 拆出 `common/spring/grpc/`，再只导入 grpc 侧装配。

### `etc/like.yaml` + `internal/config/config.go`
实测**只有 `Storage.Redis.CacheTTL` 被任何代码读取**（`getusertotallikelogic.go:56,73`）。其余全是死配置：`RocksDB`（整套 KV 存储从未打开）、`Redis.HotspotTTL`、`Redis.BatchSize`、`Postgres.SyncBatchSize/SyncInterval`（"异步冷备"RocksDB→PG 的整条链路无实现）、`HotSpot{Detector,AntiAvalanche}`（阈值 1000/60s、防雪崩队列 10000/并发 1000/超时 1000ms —— **全无消费点**）、`AntiBrush{RateLimit,UserLimit}`（IP 60s/60 次、日 1000 次、分钟 60 次 —— **全无消费点**）、`KafkaPusher`（`like_update_topic`）、`TaskUserPusherConf`、`TaskArticlePusherConf`、`MessageRpc`。
- **L2 结论**：`client_ip` / `weight` / `promotion_id` 只被原样写进 `KafkaLikeMsg`，**防刷与推广结算在本服务内没有任何执行逻辑**。Java 侧不应"补全"防刷——那是新增行为。
- 唯一实际生效的 3 个 Kafka producer：`KafkaPusher`（`like_promotion_topic`，无条件构造）、`TaskUserPusher`、`TaskArticlePusher`（后两者经 `newOptionalPusher` 在 brokers/topic 为空时返回 `nil`，调用点全部 nil-check）。

### `internal/svc/servicecontext.go`
`redis.MustNewRedis` + `gorm.Open(postgres)`（失败 `panic("PostgreSQL连接失败")`）+ 连接池 `MaxIdle=10 / MaxOpen=100` + `kq.NewPusher × 3` + `MessageRpc`。
- **L3**：`MaxOpen=100` 与 `message-rpc/application.yml` 的 `hikari.maximum-pool-size: 100` / `minimum-idle: 10` 完全一致 —— 这是 RAG 已有的等价表达，可直接复用。

### `internal/server/likeserviceserver.go`
6 个方法纯转发到 `logic.New*Logic(ctx, s.svcCtx)`。Java 侧对应 `service/impl/LikeServiceGrpcImpl`（`MessageRpcServiceImpl.java:36-44` 已证明该形态）。

### `likeservice/likeservice.go`
goctl 生成的客户端桩：7 个 `pb.*` 类型别名 + 6 个方法签名 + `defaultLikeService`（每次调用现场 `pb.NewLikeServiceClient(m.cli.Conn())`）。**纯生成物，不迁移** —— Java 侧由 `like-proto` 的 `protobuf-maven-plugin` 生成 `LikeServiceGrpc.LikeServiceBlockingStub`，api 侧 `client/LikeRpcClient` 持有它。

### `internal/types/types.go`
3 个 Kafka payload，全是 producer/consumer 共享契约，**无任何方法**：
- `KafkaLikeMsg{msg_id,user_id,target_type,target_id,author_id,state,is_first,created_at}` —— `msg_id` 是 `snowflake.GetID()` 的十进制字符串（P1 生产 → P2 消费，并被当作 `last_operation_id` 的单调性依据）
- `ArticleHotEvent{article_id,type,user_id(string!),timestamp,is_first}` —— **注意 `user_id` 在这里是 `string`**，与 `KafkaLikeMsg.UserId(int64)` 不同；P2 构造时用 `fmt.Sprintf("%d", ...)`
- `TaskArticleProgressMsg{user_id(string),cur(int64)}` —— P1 直接推给 task 系统的进度回报

### `internal/model/*_test.go` 与 `internal/mq/internal/mqs/likemq_test.go`（4 个）
全部**依赖真实 PostgreSQL**，无 `RTW_LIKE_*_DSN` 环境变量时 `t.Skip` —— 即 Go 侧这批事实流水线测试**默认不跑**。它们锁定的行为必须转成 RAG 侧测试：
- `domain_fact_test.go`：重投幂等、4 次状态迁移产出 4 条 fact 且版本严格 `1..4`、乱序消息（`msgID=99` < `last_operation_id=105`）被当 no-op、legacy 行拒绝出 fact、`uk_like_fact_stream` 冲突保护
- `likemq_test.go`：**`TestConsumerAcknowledgesOnlyCommittedDomainFact`** —— 预置冲突的 `rtw.like.1000` 行，断言首次 `Consume` 返回 error（**不 ack**）、inbox 与 record 均为 0 行；删除冲突行后连续两次 `Consume` 均成功，且只落 1 条 fact、inbox `status==1`
- `fact_delivery_logging_test.go`：投递日志必须落在**注入的** logger 上，且含 `like.fact_delivery.started` / `.accepted` 与 `duration_ms`
- `fact_process_fixture_test.go`：跨进程夹具（ready/release 文件 + 4 分钟等待），为 P3/P4 的验收供数

- **L3**：`likemq_test.go` 证明的是**真实 PG 事务语义**（冲突行导致整个 `ProcessLikeMessageBatch` 失败 → 回滚）。RAG 侧必须配同等强度的集成测试，**纯 Mockito mock 测不出来**（AGENTS.md §3.3）。

### `internal/logic/likeactionlogic.go`（最复杂）
1. **自写雪花游标 `generateCursorID()`（`:38-54`）**：`nodeId` 硬编码 1，`sequence % 4096`，互斥锁，返回 `(now<<22)|(nodeId<<12)|sequence`。Go 注释明写动机：ZSet score 的高 41 位就是时间戳，要与 `>> 22` 的读侧配套，**不能用 `common/snowflake`**（后者 epoch 是 2010-11-04，还原出的时间戳对不上前端）。
2. **68 行 Lua（`:56-122`）** 6 个 KEY：`like_count:{type}` / `like_state:{type}:{id}` / `user_like_list:{uid}:{type}` / `target_liker_list:{type}:{id}` / `dislike_count:{type}` / `user_total_like:{authorId}`。返回码：`0`=同状态重复（幂等空转）、`2`=首次点赞（`action==1 && current_state==0`）、`1`=其它状态变更。四个分支各自处理 `HINCRBY/ZADD/ZREM/INCR/DECR`，且**每个减法都有 `> 0` 前置守卫**（Lua `:79`/`:86`/`:89`/`:100`/`:103`/`:113`）。
3. **入参校验（`:169-173`）**：`TargetId=="" || TargetType=="" || UserId<=0` → grpc `2001` + 指标 `invalid_param`。
4. **雪花 ID 失败（`:217-221`）**：**只打指标 `snowflake_error`，`msgId` 保持 0 不中断** → `KafkaLikeMsg.MsgId="0"` → 下游 `likemq.go:93` 的 `operationID <= 0` 判定命中 `empty_msg_id` → **静默 drop（return nil）**。这是一条**静默丢失**链路，必须原样保留。
5. **Kafka 推送失败（`:240-250`）**：只 `logger.LogBusinessErr` + 指标 `kafka_error`，**不重试、不落盘**（Go 注释自称"目前先个重度告警日志"）。响应仍返回 `Success: true`。**best-effort 语义，必须保留**。
6. **响应计数回读（`:254-282`）**：从 Redis `HGET` 两个 key；解析失败只打 `ErrorTypeTransfer` 日志、计数留 0；读失败打 `redis_read_error`。**读失败不返回错误**，响应照常返回。
7. **三个 `article` 专属分支（`:284-301`，`strings.EqualFold`）**：
   - `currentArticleLikeTotal`：`max(redis, DB)`，DB 出错则返回 redis 值
   - `syncTaskProgress`：`TaskUserPusher` 推 `key=userId, value=总赞数（纯数字字符串）`；`TaskArticlePusher` 推 `key=targetId, value={"user_id","cur"}`；三者失败均只记日志
   - `notifyArticleLiked`：`authorId>0 && userId != authorId` 才发 `MessageRpc.SendNotification(ARTICLE_LIKED, title="文章收获新点赞", content="用户 %d 点赞了你的文章。", extra={target_type,target_id,liker_id,author_id})`
8. `activeLikeState = 1`（`:124-126`）—— **声明后零引用，死常量**。

### `internal/logic/getlikecountlogic.go`
- `TargetIds` 空 → grpc `2001`
- 两个 `HMGET`（`like_count:{type}` / `dislike_count:{type}`），**Redis 出错只记 `redis_error` 指标与日志并继续**（`:50-61`），返回 nil slice 后全部 id 进 `missingIds` → 自动降级查 DB。Go 文件末尾注释明确称其为"缓存容灾降级"
- 回源 `GetBatchLikeCount` → **逐 id `HSET` 回填**；回填失败只记 `ErrorRedisUpdate` 日志
- `likeStr==""` 才算 miss；`dislikeStr==""` 归一为 `"0"`；`ParseInt` 错误被忽略（计数为 0）
- **L2 陷阱**：`redis_error` 指标的 `Add(float64(len(TargetIds)))` 按 **id 条数**累加，而 `hit/miss` 也按条数 —— 与其它指标按调用次数累加的口径不同
- **L2 陷阱**：`Storage.Redis.BatchSize=1000` 配置**零消费点**，真实批量无上限 —— api 的 `target_ids` 可以是任意长度

### `internal/logic/getlikestatelogic.go`
- 逐 id `HGET like_state:{type}:{id} {uid}`（`:52`）—— **N+1，不是批量**
- miss → `GetUserBatchLikeState` → 回填 **`strconv.Itoa(0)` 零值**（`:95`）。Go 注释（`:87-90`）明说这是**防缓存穿透**的刻意设计
- Redis 出错只打指标与日志；DB 出错 → grpc `5003`

### `internal/logic/getuserlikelistlogic.go` / `gettargetlikerlistlogic.go`
对称实现，共 12 条同构规则：
- `limit = (PageSize<=0 || PageSize>100) ? 10 : PageSize`（硬编码，**不读配置**）
- `fetchLimit = limit + 1`
- `maxScore = Cursor>0 ? Cursor-1 : MaxInt64`
- `ZREVRANGEBYSCORE key 0 maxScore WITHSCORES LIMIT 0 fetchLimit`
- Redis 命中 → 直接返回，`nextCursor = 最后一条 score`，**不查 DB、不续期**
- `len(list)==0` → 回源 DB；`nextCursor = timestamp << 22`
- **脏数据跳过**：`create_time == 0` → 打日志 + `continue`（该条既不入 list 也不入 ZSet）
- 回源后 `if len(dbResults)>0 → EXPIRE 86400*7`（**只在有 DB 结果时续期**）
- `isEnd = (取到数 > limit)`；`if isEnd → nextCursor = 0`
- 两处差异：user 版回源后写 ZSet 的 score 用 `timestamp<<22`、读侧同样 `>>22` 还原；target 版多一个 `TargetLikerListResult` 的 `UserId` 字段；DB 出错 → grpc `5003` **致命**（与 Redis 错误的"吞掉"不对称）

### `internal/logic/getusertotallikelogic.go`
`UserId<=0` → `2001`；`GET user_total_like:{uid}`；**命中时 `EXPIRE` 续期 `CacheTTL`（滑动过期）**；miss → DB → `SETEX`。Redis 读错 → 指标 + 日志后**继续走 miss 分支**；`SETEX` 失败只记日志。

### `internal/model/data_model.go`
- `LikeRecord`：`uk_user_target(user_id,target_type,target_id)`、`idx_user_list(user_id,updated_at)`、`idx_target_list(target_type,target_id)`、`idx_author`、`idx` on `deleted_at`；`last_operation_id bigint not null default 0`；`state smallint default 0`；**软删**
- `LikeConsumeInbox`：`msg_id varchar(64)` PK + `payload_hash varchar(64)` + `idx_topic_consumer(topic,consumer)` + `status`（0 处理中 / 1 已完成）
- `LikeOutboxEvent`：`event_id` PK + **`uk_event_key`**（业务幂等键）+ `payload json` + `status`（0/1/2）+ `retry_count`

### `internal/model/like.go`
`LikeRecordModel` 接口 8 方法。核心是 `ProcessLikeMessageBatch`（单事务循环）与 `applyLikeMessage`：
1. **载荷校验**：`Inbox/Record` 非 nil、`UserID>0`、`TargetType/TargetID` 非空、`State ∈ [1,4]`，否则 `invalid like message payload` → **整批失败**
2. `operationID = ParseInt(Inbox.MsgId)`，`err != nil || <=0` → `invalid like operation id`
3. `likeMessageHash(p)`（7 字段 sha256）写入 `Inbox.PayloadHash`
4. `INSERT inbox ON CONFLICT DO NOTHING`；`RowsAffected==0` → **`FOR UPDATE` 重读**：hash 不同 → `reused with conflicting payload`（毒丸消息，永久失败）；`Status==1` → `continue`（**已完成，跳过**）
5. `applyLikeMessage`：`INSERT seed ON CONFLICT DO NOTHING` → `Unscoped() + FOR UPDATE` 取当前行 → **软删行报错 `like record is soft deleted`** → **legacy 状态守卫 `state<0 || state>2 || (state==2 && last_operation_id==0)` → 报 `requires reconciliation`**（历史 `state==2` 的含义不明，禁止生成新 fact）→ `operationID <= last_operation_id` → `markLikeMessageDone` 后返回（**乱序/重投 no-op**）
6. 状态机：`1→1`、`2 且 old==1 → 0`、`3→2`、`4 且 old==2 → 0`（**unlike 不会撤销 dislike**）
7. `newState != oldState` → `appendLikeFact`；且 `oldState==0 && newState==1` 时额外插 `like_outbox_event`
8. `markLikeMessageDone`

- **L3 幂等资产**：`like_consume_inbox` + `payload_hash` + `last_operation_id` 三者构成"消息级幂等 + 语义冲突检测 + 操作级单调性"三层防线。**任何"交给框架去重"的说法都是回归**。

### `internal/model/like_message_hash.go`
对 7 字段（`user_id/target_type/target_id/author_id/action/occurred_at/is_first`）按 struct 顺序 marshal → sha256 → hex。字段顺序即哈希稳定性来源，Java 侧必须用**同样的字段顺序 + 同样的 JSON key**。

### `internal/model/domain_fact.go`
- `LikeDomainFactOutbox`：**append-only**（不可变）；`AggregateID`/`FactVersion` 组成 `uk_like_fact_stream`；`delivery_envelope jsonb` 是**事务内冻结的 DC 信封**
- `LikeFactStream(aggregate_id PK, version bigint CHECK 1..9007199254740991)`
- `likeDeliveryEnvelope`（9 字段）与 `likeFact`（19 字段，见 §12.1 核对表）
- `appendLikeFact`（`:76-138`）五条硬约束：
  1. `eventID = "rtw.like." + Inbox.MsgId`
  2. **legacy 守卫**：查 `payload->>'subject_ref' AND target_type AND target_id AND delivery_envelope IS NULL` 计数，`>0` → `has unversioned legacy facts` → 拒绝
  3. `INSERT INTO like_fact_stream ... ON CONFLICT DO UPDATE SET version = version + 1 RETURNING` —— **聚合版本在此分配**
  4. DC 双精度范围守卫 `1 ≤ version ≤ 9007199254740991`
  5. **业务 `aggregate_id`（`{type}/{id}`，给 H09 消费方）与 `streamID`（`like-state/{uid}/{type}/{id}`，版本流水）分离** —— 这是全设计里最容易在迁移中被"简化"掉的一点

### `internal/model/fact_delivery.go`
`DispatchFactOnce` 的事务边界与两道分支：
- `Begin` → `SELECT ... FOR UPDATE SKIP LOCKED` 单行（`delivery_status IN (0,2) AND delivery_envelope IS NOT NULL`）
- **前序屏障**：`NOT EXISTS(同 aggregate_id 且 fact_version 更小 且 delivery_status <> ACCEPTED)` —— 保证**每个聚合严格按版本顺序投递**
- **冻结信封校验失败** → 置 `BLOCKED`（3）+ **提交** + 返回 `ErrFrozenEnvelopeBlocked` → **终止重试**（人工迁移），日志 `manual_migration_required`
- `sender.Send` 失败或 receipt 不匹配 → `FAILED`（2）+ `retry_count+1` + **提交** + 返回 err → **可重试**
- 成功 → `ACCEPTED`（1）+ `technical_receipt_id/input_hash/offset/received_at` + `delivered_at` + 提交
- **L3 关键**：事务从 `Begin`（`:57`）一直持有到 HTTP 出网（`:92`）之后才 `Commit`（`:118`）——**行锁跨网络 I/O**。这是 Go 的真实行为，Java 侧用 `@Transactional` 包裹等价代码会得到同样语义，但必须用**独立 Bean**（§3.3 self-invocation 陷阱）
- **L2**：整个 `tx` 内**没有重试上限**——`retry_count` 只累加，`FetchPending` 不按它过滤（与 `like_outbox.go:31` 的 `retry_count < 3` 形成对照）

### `internal/model/fact_authority.go`
`AuthoritativeFact(producer, eventID)` 是一套**全链路重放校验**（12 道关卡）：
1. `producer != "rtw.like-mq"` 或 `len(eventID)>128` → `ErrAuthorityUnavailable`
2. 按 `event_id` 取行，行数 ≠ 1 → unavailable
3. `deliveredLikeAuthority`：要求 `delivery_status==ACCEPTED` + `delivered_at` + `technical_received_at` + `receipt_id` 非空 + `technical_offset >= 1` + `technical_input_hash` 是 64 位 hex + `validLikeDelivery` 通过
4. payload 自洽：`eventID == "rtw.like."+operationID`、`aggregateID == targetType+"/"+targetID`、`sourceRef == eventID`、`revision` 与 `revision_status` 一致（`nil ⟺ "unknown"`）
5. `CanonicalHash(delivery_envelope) == technical_input_hash`
6. 取出该聚合全部行按 `(fact_version, event_id)` 排序，**版本必须严格连续 1..N**
7. 逐条重放：`current.oldState == 上一条的 newState`、`subject_ref`/`target_type`/`target_id`/`target_revision` 与目标一致、`validLikeTransition(operation, old, new)` 成立
8. `oldState != 0` 的事件必须能找到 `PredecessorEventID`
9. 反查 `like_record`：当前 `state` 必须等于最后一条 fact 的 `newState`，且 `last_operation_id >= operationID`，且 `record.user_id == subjectID`

任何一关不过 → 全部收敛为 `ErrAuthorityUnavailable` → HTTP 404。
- **L2**：`subjectIDFromRef` 用 `strconv.ParseInt(subject.SubjectID)` 且**丢弃 error** —— `parseRTWSubject` 已保证是合法正整数，所以安全。

### `internal/model/like_outbox.go` / `like_consume_inbox.go`
outbox 状态机：`status IN (0,2) AND retry_count < 3`（**硬编码 3**，不是配置）→ `ORDER BY created_at ASC` → `MarkSent` / `MarkFailed(retry_count+1)`。inbox 三方法（注意 `FindByMsgID` 对 not-found 返回 `(nil, nil)` 而不是 error）。

### `internal/model/db.go`
`AutoMigrate(LikeRecord, LikeConsumeInbox, LikeOutboxEvent, LikeDomainFactOutbox, LikeFactStream)`；连接失败 / 迁移失败 → `log.Fatalln`（进程退出）。

### `internal/model/migrations/001_like_domain_fact_outbox.sql`
`like_record.last_operation_id bigint NOT NULL DEFAULT 0`；`like_consume_inbox.payload_hash varchar(64) NOT NULL DEFAULT ''`；建 `like_domain_fact_outbox(event_id PK, event_type, payload jsonb, created_at)`。
- **L2**：注释明写"旧 `last_operation_id=0` 的行保留为 legacy，含义不明的历史 `state=2` **不回填**成新 domain fact"——这正是 `fact_authority.go` 那个 legacy 守卫的由来。

### `internal/model/migrations/002_like_dc_wire.sql`
加 3 列（`aggregate_id`/`fact_version`/`delivery_envelope`）；建 `like_fact_stream`（PK + `CHECK version BETWEEN 1 AND 9007199254740991`）；`uk_like_fact_stream(aggregate_id, fact_version)`；**部分索引** `idx_like_legacy_fact_subject_target ... WHERE delivery_envelope IS NULL`；**`NOT VALID` CHECK `like_new_fact_has_dc_wire`** —— 只约束**新行**，存量行不做校验（避免迁移失败）。
- **L3 陷阱**：`NOT VALID` 意味着存量行可以永久保持 `NULL`；而 `appendLikeFact` 的 legacy 计数守卫会因此**永久拒绝**该 (subject, target) 的新 fact。这是 Go 的显式设计，**不能"顺手"改成 VALID**。

### `internal/model/migrations/003_like_fact_delivery.sql`
加 7 列投递元数据；`idx_like_fact_delivery(delivery_status, created_at, event_id)`；建 `reject_like_domain_fact_identity_change()` 触发器 —— **`BEFORE UPDATE` 上禁止修改 `event_id/event_type/payload/aggregate_id/fact_version/delivery_envelope` 六列**，`RAISE EXCEPTION 'like fact envelope is immutable'`。
- **L3 关键**：这是**数据库层**的不可变约束。Java 侧若把投递状态更新写成"整行 update 覆盖"（MyBatis-Plus `updateById` 默认非空字段更新虽安全，但若误用"先查后全量保存"）就会撞触发器。必须配一条真跑 SQL 的测试（AGENTS.md §3.3）。

### `internal/mq/mq.go`（P2）
`kq.MustNewQueue(c.Kafka, NewLikeUpdateService(...))` + 自建 `OutboxRelayService`（1s ticker → `SendPending(100)`），两者加进同一个 `service.ServiceGroup` 保证同启同停。

### `internal/mq/internal/mqs/likemq.go`
`Consume(ctx, key, val)` 包在 `observability.TraceConsumer(ctx,"like-mq","LikeUpdateService.Consume", likeMessageAttrs(...))` 里：
1. `json.Unmarshal` 失败 → 指标 `receive/json_error` + **`return nil`（静默 drop）**
2. 字段校验（`operationID>0 && UserId>0 && TargetType/TargetId 非空 && State ∈[1,4]`）失败 → 指标 `receive/empty_msg_id` + **`return nil`（静默 drop）**
3. `buildFirstLikeOutbox`：仅 `State==1 && IsFirst`；`eventKey = first_like:{uid}:{type}:{id}`；`eventType="article_hot_like"`；`aggregateID=TargetID`；payload 是 `ArticleHotEvent{article_id,type:"like",user_id,timestamp,is_first}`；雪花失败 → **指标 + `return err`（重试）**
4. `ProcessLikeMessageBatch` 失败 → 指标 `receive/tx_error` + **`return err`（重试，Kafka 不 ack）**
5. 成功 → 指标 `receive/committed`
- **L2**：**drop 与 retry 的分界**是设计出来的：解析层/校验层坏消息 drop（毒丸不进 DLQ 会无限重试），持久层失败 retry（不能丢）。Go 注释 `:126` 明写"Kafka 只能在 inbox、record、domain fact 全部提交后才能 ack"。这与 article-rpc 的消费者同款契约，必须保留。
- **L3**：`Topic: "like-topic"` / `Consumer: "like_update_service"` 是**硬编码字符串**，与 `mq.yaml` 的真实 topic `like_promotion_topic` **不一致**（只写进 inbox 表的元数据列，不影响消费）。

### `internal/mq/internal/mqs/like_outbox_sender.go`
`FetchPending(limit)` → 逐条 `HotEventPusher.Push(payload)` → 失败 `MarkFailed` + **`continue`（批次隔离）**；成功 `MarkSent`。
- **L2**：整个循环**只在 fetch 失败时返回 error**；单条推送失败**永远返回 nil**（`mq.go:39-41` 因此不会打 `ErrorKafkaPush` 日志 —— 那条日志是死代码）。

### `internal/mq/internal/svc/servicecontext.go` / `internal/mq/internal/config/config.go` / `etc/mq.yaml`
`HotEventPusher` 无条件构造（topic `article-hot-events`，与 `hot/rpc/etc/hot.yaml:26` 一致）。`mq.yaml` 的 `Consumers: 8` 是 kq 的并发度配置，Java 侧对应 listener container 的并发数。

### `cmd/fact-dispatch/main.go`（P3）
`ProcessMetadata{Service:"rtw-like-fact-dispatch", Component:"community"}` → `NewProcessLogger(os.Stderr, meta)` → `InstallLocalTracing()` → `meta.Validate()` → `InstallFrameworkLogger(logger)` → `run()`。`run` 校验 `LIKE_DATABASE_URL` / `DC_PLATFORM_EVENT_URL` / `DC_PLATFORM_SERVICE_TOKEN(≥32)`，`PingContext` 后 `NewDCEventSender(endpoint, token, &http.Client{Timeout: 8s})` → `RunDispatcher(ctx, NewLikeFactModel(db), sender, logger, once)`。SIGINT/SIGTERM → ctx cancel。
- **L3**：DC HTTP 客户端超时 **8s**，而 `Dispatcher` 的单次 deadline 是 **10s** —— 二者不一致是刻意的（HTTP 超时先触发 → 归类为可重试）。

### `cmd/fact-authority/main.go`（P4）
`LIKE_DATABASE_URL` / `LIKE_FACT_AUTHORITY_LISTEN` / `LIKE_FACT_AUTHORITY_TOKEN(≥32)`；`NewAuthorityHandler("rtw.like-mq", token, model.NewLikeFactModel(db).AuthoritativeFact, logger)`；`http.Server{ReadHeaderTimeout: 3s, WriteTimeout: 5s}`；5s 优雅停机。
- **L2**：`producer` 硬编码 `"rtw.like-mq"`，与 `fact_delivery.go:17` 的 `likeFactProducer` 常量同值。

### `internal/metrics/metrics.go` + `internal/mq/internal/metrics/metrics.go`
- `like-rpc_action_total{target_type,action,result}`（**`Subsystem:"like-rpc"`，带连字符**）
- `like_rpc_query_cache_total{target_type,result}`（**`Subsystem:"like_rpc"`，带下划线**）—— 同一文件里两个 subsystem 名不一致，**Prometheus 指标名分别为 `like-rpc_action_total` 与 `like_rpc_query_cache_total`**，迁移时**不能"统一"**
- `like_mq_consume_total{action,result}`
- 三处 `InitMetrics()` **全仓零调用** → 三个指标在 Go 侧均未注册到 default registry

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| P1 `like.go` 进程装配 | `LikeRpcApplication` | ABSORB | Spring Boot 启动（`MessageRpcApplication.java:19-24`） |
| P1 `like.go:41` unary interceptor | `spring/RpcServerInterceptorFactory` | ADAPT | ⚠️ `@Component` ≠ 自动注册；需在 `LikeRpcApplication` 显式 `@Import`（见 §12.1 更正） |
| P2 `mq.go` ServiceGroup 同启同停 | Spring 容器生命周期 | ABSORB | listener container + `@Scheduled` 由容器统一管理 |
| P3 `fact-dispatch/main.go` 循环 | `mq/LikeFactDispatchScheduler`（`@Scheduled`）调 `common/communityfact/Dispatcher.run` | ADAPT | Go 注释的"业务 RPC 进程之外"是意图非约束；无 gRPC 注册面（`main.go:76`） |
| P3 `RunDispatcher` 本体 | `common/communityfact/Dispatcher.run(ctx, dispatcherImpl, sender, logger, once, false)` | REUSE | 批次16/1s/10s 逐条对齐（`Dispatcher.java:86-208` vs `process.go:20-62`） |
| P3 `DispatcherHandle` 生命周期 | `@PreDestroy` 调 `handle.stop()` | ADAPT | `Dispatcher.java:46-63` 注释已给出 Spring 化路径 |
| P3 `DCEventSender` | `Wire.DCEventSender.create(endpoint, token, HttpClient.newBuilder().connectTimeout(...))` | REUSE | `Wire.java:157-172`；8s 超时保留 |
| P3/P4 `ProcessMetadata` + `newProcessLogger` | `ProcessLogger.newProcessLogger(stream, meta)`（3 份） | REUSE | logger 名含 service+env+component，同 JVM 多实例不串扰（`ProcessLogger.java:114-117`） |
| P4 `NewAuthorityHandler` servlet | `AuthorityHandlerFactory.register("rtw.like-mq", token, likeFactModel::authoritativeFact)` | REUSE | `AuthorityHandlerFactory.java:39-49`；路径 `/internal/v1/community/facts` |
| P4 独立 `net.Listen` | **UNKNOWN** —— 需给 like-rpc 新增 HTTP connector 或 management port | ADAPT | wire 面变更，需拍板 |
| `config/config.go` + `like.yaml` | `config/LikeRpcProperties`（仅活跃段）+ `application.yml` | ADAPT | 死配置段**不迁**（见下） |
| `svc/servicecontext.go` Redis | `StringRedisTemplate`（Hash/ZSet/Lua 见 `service/LikeRedisKeys` + `LikeActionScript`） | ADAPT | `StringRedisTemplate` 有先例（`UserDomainService.java:85`），但**只有 opsForValue 用例**；Hash/ZSet/Lua 无先例 |
| `svc` GORM + 池 10/100 | `application.yml` Hikari `maximum-pool-size:100 / minimum-idle:10` | ADAPT | 与 `message-rpc/application.yml` 完全一致 |
| `svc` 3 个 kq Pusher | `KafkaTemplate` + `@ConfigurationProperties` | NEW | Spring Kafka 自动配置；`Topic` 值逐字保留 |
| `svc` `MessageRpc` | `client/MessageRpcClient` | NEW | `message-rpc/client/` 先例（`MessageRpcApplication` 侧已 `@GrpcClient`） |
| `server/likeserviceserver.go` | `service/impl/LikeServiceGrpcImpl`（6 override） | ADAPT | 分发由 starter 完成 |
| `logic/*` 6 个 | `service/LikeDomainService`（6 方法） | MERGE | 同一变化原因（点赞域）、同一依赖边界（同 users 的"9 合 1"决策） |
| `likeactionlogic.go` `generateCursorID` | `service/LikeCursorIdGenerator` | NEW | **不能用 `common/Snowflake`**（epoch 不一致会破坏 `>>22`）；需独立文件 + §3.2 标注 Go 出处 |
| `likeactionlogic.go` 68 行 Lua | `service/LikeActionScript`（`DefaultRedisScript`） | NEW | Spring Redis 无 Lua 脚本先例；脚本文本逐字保留 |
| `likeactionlogic.go` 三段 article 分支 | `LikeDomainService` + `mq/LikeTaskProgressPublisher` | SPLIT | 通知与任务进度推送是**独立的出网副作用**，与主用例分离便于失败隔离 |
| `likeactionlogic.go` 通知 | `client/MessageRpcClient.sendArticleLiked(...)` | ADAPT | 参数逐字保留 |
| `logic/*` gRPC 错误 | `like-common` 的 `LikeGrpcError.grpcError(bizCode, msg)` | ADAPT | `status.Error(codes.Code(x), msg)` 的等价 |
| `metrics/metrics.go` | `metrics/LikeRpcMetrics` | ADAPT | 两个 subsystem 名（含连字符那个）**原样保留** |
| `types/types.go` | `mq/LikeEventContracts`（3 个 Kafka payload） | NEW | producer/consumer 共享契约 |
| `model/db.go` AutoMigrate | Flyway `V1..V5__*.sql` | ADAPT | 迁移机制替换；`log.Fatalln` → Flyway 启动失败 |
| `model/data_model.go` 3 实体 | 3 个 `domain/*Entity` | SPLIT | 表用途不同（业务状态 / 幂等 inbox / 业务 outbox） |
| `model/domain_fact.go` 2 实体 | 2 个 `domain/*Entity` | SPLIT | fact outbox（不可变）vs version 流水表 |
| `model/data_model.go` + `domain_fact.go` jsonb 列 | `mapper/JsonbTypeHandler` | ADAPT | 复用 `user-rpc/mapper/JsonbTypeHandler` 同款；**注意 §3.3：`LambdaUpdateWrapper.set()` 会丢 typeHandler** |
| `model/like.go` 查询方法 | `mapper/LikeRecordMapper` | ADAPT | `BaseMapper` + `@Select`；8 个方法含 `GROUP BY target_id,state` 聚合查询 |
| `model/like.go` `ProcessLikeMessageBatch` | `service/LikeMessageIngestService`（**独立 `@Service` Bean**） | SPLIT | **§3.3 self-invocation**：`applyLikeMessage` 是内部调用，`@Transactional` 挂在外层无效。必须拆 Bean |
| `model/like.go` `FOR UPDATE` | Mapper `@Select` + `... FOR UPDATE` | ADAPT | 与 `admin-rpc` 邀请码行锁同款 |
| `model/like_message_hash.go` | `service/LikeMessageDigest` | NEW | sha256 字段顺序即稳定性 |
| `model/like_consume_inbox.go` / `like_outbox.go` | 2 个 `mapper/*Mapper` | SPLIT | 两套幂等状态机 |
| `model/domain_fact.go` `appendLikeFact` | `service/LikeDomainFactService`（独立 Bean） | SPLIT | 与 ingest 同事务，需同 Bean 内自调用 —— **Go 侧本就在同一 tx 内，Java 侧必须靠"同一 Bean 内调用 + 事务在最外层方法"或全部下沉到一个 Bean** |
| `model/fact_delivery.go` `DispatchFactOnce` | `service/LikeFactDispatchService`（独立 Bean，`@Transactional`） | ADAPT | 事务跨 HTTP I/O，必须独立 Bean |
| `model/fact_delivery.go` `validLikeDelivery` | `service/LikeDeliveryValidator` | NEW | 19 条交叉校验，独立可测 |
| `model/fact_authority.go` `AuthoritativeFact` | `service/LikeAuthorityLookupService` | NEW | 12 道重放校验，是 `AuthorityLookup` 的实现 |
| `migrations/001-003` | `V1__create_like_record.sql` … `V5__*.sql` | ADAPT | DDL 逐条迁移，含触发器与 `NOT VALID` CHECK |
| `mqs/likemq.go` `Consume` | `mq/LikeUpdateKafkaListener` | NEW | 消息入口，独立错误契约（drop vs retry）、独立事务 |
| `mqs/likemq.go` span | `common/observability/MqConsumer.traceConsumer` | REUSE | 对应 Go `observability.TraceConsumer` |
| `mqs/likemq.go` `buildFirstLikeOutbox` | `service/LikeFirstLikeOutboxFactory` | NEW | `eventKey/eventType/aggregateID` 是跨服务契约 |
| `mqs/like_outbox_sender.go` | `mq/LikeHotEventOutboxRelay`（`@Scheduled(fixedDelay=1s)`） | NEW | 单条失败不中断批次 |
| `mq/internal/metrics` | `metrics/LikeMqMetrics` | ADAPT | `like_mq_consume_total` |
| `cmd/*` 的 `PingContext` fail-fast | Spring `spring.datasource` 初始连接 + `bootstrap/LikeSchemaGate` | ADAPT | 缺表/缺触发器必须 fail-fast |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/like/like/rpc/
├── LikeRpcApplication.java
├── bootstrap/
│   ├── NacosRegistration.java
│   ├── LikeSchemaGate.java                    # 校验 fact 不可变触发器 + 5 张表存在，否则 fail-fast
│   └── LikeFactProcessLogging.java            # 3 份 ProcessLogger（rpc / dispatch / authority）
├── config/
│   ├── LikeRpcProperties.java                 # 只绑活跃段：Redis TTL、Kafka 3 topic、MessageRpc
│   └── LikeKafkaTopics.java                   # 5 个 topic 名 + 2 个 consumer group 的 L1 常量
├── domain/
│   ├── LikeRecordEntity.java                  # @TableName("like_record")；软删 last_operation_id
│   ├── LikeConsumeInboxEntity.java           # @TableName("like_consume_inbox")
│   ├── LikeOutboxEventEntity.java            # @TableName("like_outbox_event")；payload jsonb
│   ├── LikeDomainFactOutboxEntity.java       # @TableName("like_domain_fact_outbox")；payload/delivery_envelope jsonb
│   └── LikeFactStreamEntity.java             # @TableName("like_fact_stream")
├── mapper/
│   ├── LikeRecordMapper.java                 # 8 方法（含 GROUP BY 聚合 + FOR UPDATE + UNSCOPED）
│   ├── LikeConsumeInboxMapper.java
│   ├── LikeOutboxEventMapper.java            # FetchPending(retry<3) / MarkSent / MarkFailed
│   ├── LikeDomainFactOutboxMapper.java       # SKIP LOCKED 取件 + 前序屏障 NOT EXISTS + 状态迁移
│   ├── LikeFactStreamMapper.java             # ON CONFLICT DO UPDATE ... RETURNING version
│   └── JsonbTypeHandler.java
├── client/
│   └── MessageRpcClient.java                 # @GrpcClient("message-rpc")，ARTICLE_LIKED 通知
├── exception/
│   └── LikeExceptions.java                   # 业务异常聚合（输入错误 / 幂等冲突 / legacy 不可调和）
├── metrics/
│   ├── LikeRpcMetrics.java                   # like-rpc_action_total + like_rpc_query_cache_total
│   └── LikeMqMetrics.java                    # like_mq_consume_total
├── mq/
│   ├── LikeEventContracts.java               # KafkaLikeMsg / ArticleHotEvent / TaskArticleProgressMsg
│   ├── LikeUpdateKafkaListener.java          # drop vs retry 的错误契约
│   ├── LikeHotEventOutboxRelay.java          # @Scheduled 1s / batch 100
│   ├── LikeFactDispatchScheduler.java        # 驱动 common Dispatcher（@PreDestroy stop）
│   └── LikeAuthorityEndpointConfig.java      # ServletRegistrationBean 装配（producer/token/lookup）
└── service/
    ├── LikeDomainService.java                # 6 个 gRPC 用例
    ├── LikeCursorIdGenerator.java            # (unixMilli<<22)|(nodeId<<12)|seq  ← 禁用 common/Snowflake
    ├── LikeActionScript.java                 # 68 行 Lua 的 DefaultRedisScript
    ├── LikeRedisKeys.java                    # 6 个 key 模板的唯一定义处
    ├── LikeMessageDigest.java                # 7 字段 sha256
    ├── LikeMessageIngestService.java         # 独立 @Service Bean（事务代理边界）
    ├── LikeDomainFactService.java            # appendLikeFact + 版本分配 + 双信封
    ├── LikeDeliveryValidator.java            # validLikeDelivery 交叉校验
    ├── LikeFactDispatchService.java          # DispatchFactOnce（独立 @Service Bean）
    ├── LikeAuthorityLookupService.java       # AuthoritativeFact（12 道重放校验）
    ├── LikeTaskProgressPublisher.java        # 2 个可选 pusher 的推送 + 吞错
    └── impl/
        └── LikeServiceGrpcImpl.java
```

### 新增包说明（§3.3 规则 8）

`mq/` 是**新增包**，理由：
1. **职责无法归入现有包** —— `service/` 承载被 gRPC 调用的业务用例，`service/impl/` 承载 gRPC 接口实现，`domain/`+`mapper/` 承载持久化。三个 Kafka 入口（`like-update-consumer` / `article-hot-events` relay / `community-fact dispatch`）既非 HTTP 也非 gRPC 入口，它们有独立生命周期（listener container / scheduler）、**独立的错误契约**（解析坏消息必须 drop、持久化失败必须 retry，两者不可混同）、独立的幂等校验、独立的事务边界。
2. **目标项目实际依据** —— RAG 全仓**无 `spring-kafka` 依赖、无 listener 容器**；`service/common/observability/MqConsumer.java:71` 只提供 span 包装。引入 Spring Kafka 后，listener 的 ack 模式、并发度（Go `mq.yaml` 的 `Consumers: 8`）、错误处理策略都是必须显式声明的独立关注点。
3. **不因 Go 有同名文件而新增** —— Go 的目录名是 `mqs`（`internal/mq/internal/mqs`），此处是 `mq`；若沿用 Go 的 `handler/logic/repository/middleware/servicecontext` 等目录则属违规，本包不属于该类。
4. `bootstrap/LikeFactProcessLogging` 的必要性同理：Go 的 3 个进程各有独立 `slog` 输出流；合并成 1 个 JVM 后，必须显式建 3 份 `ProcessLogger` 才能保住 `service` 字段的可观测性（否则三个角色在日志里无法区分）。这是**合并拓扑带来的新职责**，不是照搬 Go 文件名。

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `LikeRpcApplication` | 入口；`scanBasePackages={…, com.rag.common}`；`@ConfigurationPropertiesScan`；**是否开 `@EnableScheduling` 取决于是否采纳 P2/P3 合并** | `like.go` + `mq.go` + `cmd/*` | 框架约定 |
| `bootstrap/NacosRegistration` | 服务注册 | `like.go` zrpc 注册 | 与 users 同款 |
| `bootstrap/LikeSchemaGate` | 校验 `like_domain_fact_identity_immutable` 触发器与 5 张表存在，否则 fail-fast | `db.go:15-18` `log.Fatalln` + `migrations/003` | **L3 语义**：Go 侧缺触发器/缺表直接进程退出；静默降级会让"冻结信封不可变"变成可写 |
| `bootstrap/LikeFactProcessLogging` | 3 份 `ProcessLogger`（`like-rpc` / `rtw-like-fact-dispatch` / `rtw-like-fact-authority`） | `cmd/*` 的 `NewProcessLogger` | **合并拓扑的新职责**：保住三角色日志身份隔离 |
| `config/LikeRpcProperties` | `redis.cache-ttl` + 3 个 Kafka topic + grpc client 地址 | `config.go` 活跃段 | 配置独立关注点；死配置段不迁 |
| `config/LikeKafkaTopics` | `like_promotion_topic` / `article-hot-events` / `task.logs.filtered.User` / `task.logs.filtered.Article` / `like-update-group` | 分散在 `like.yaml` + `mq.yaml` + `types.go` | topic 名是**跨服务 wire 契约**，必须单点定义 |
| `domain/LikeRecordEntity` | `@TableName("like_record")`；5 个索引；软删 | `data_model.go:9-21` | ORM 适配 |
| `domain/LikeConsumeInboxEntity` | `@TableName("like_consume_inbox")` | `data_model.go:29-37` | **幂等资产**，独立实体 |
| `domain/LikeOutboxEventEntity` | `@TableName("like_outbox_event")`；`payload` jsonb + TypeHandler；`uk_event_key` | `data_model.go:43-53` | 业务 outbox（推 hot 系统），与 fact outbox 是**两张完全不同的表** |
| `domain/LikeDomainFactOutboxEntity` | `@TableName("like_domain_fact_outbox")`；两个 jsonb 列 + TypeHandler；14 列 | `domain_fact.go:14-29` | **append-only + 投递元数据可变**，混合语义需在 Entity 上显式标注 |
| `domain/LikeFactStreamEntity` | `@TableName("like_fact_stream")` | `domain_fact.go:35-38` | 版本流水表 |
| `mapper/LikeRecordMapper` | `countTotalLike` / `countUserActiveLike` / `countTargetActiveLike` / `batchCounts(GROUP BY target_id,state)` / `batchStates` / `userLikeList(ORDER BY id DESC, id<cursor)` / `targetLikerList` / **`selectForUpdate`（含 `deleted_at IS NULL` 与 legacy 守卫）** | `like.go` 8 方法 | 聚合查询与行锁是独立 SQL 资产 |
| `mapper/LikeOutboxEventMapper` | `fetchPending(retry_count < 3)` / `markSent` / `markFailed(retry_count+1)` | `like_outbox.go` | outbox 状态机独立 |
| `mapper/LikeDomainFactOutboxMapper` | **`claimOneForDelivery`**（`FOR UPDATE SKIP LOCKED` + `NOT EXISTS predecessor`）+ `markAccepted` / `markFailed` / `markBlocked` | `fact_delivery.go:63-115` | 取件 SQL 是并发正确性的核心，独立成 mapper 便于直接审 SQL |
| `mapper/LikeFactStreamMapper` | `upsertReturningVersion`（`ON CONFLICT DO UPDATE SET version=version+1 RETURNING`） | `domain_fact.go:112-116` | 版本原子分配必须是单条 SQL，不能拆成 select+update |
| `mapper/JsonbTypeHandler` | 4 个 jsonb 列 | Go struct tag | **`wrapper.set(Map)` 会丢 handler（§3.3）**：投递状态更新必须走 `@Update` 注解 SQL 或 `updateById` 显式字段 |
| `client/MessageRpcClient` | `@GrpcClient("message-rpc")`；仅 `sendArticleLiked` 一个调用点 | `svc.MessageRpc` + `notifyArticleLiked` | 外部服务客户端 |
| `exception/LikeExceptions` | 输入错误 / 幂等键冲突 / 载荷复用冲突 / 软删行 / legacy 待调和 | `like.go:153,174,201,204` | 与 `MessageExceptions` 同款 |
| `metrics/LikeRpcMetrics` | `like-rpc_action_total{target_type,action,result}`（**连字符**）+ `like_rpc_query_cache_total{target_type,result}` | `metrics.go` | **两个 subsystem 名不一致是 Go 事实，不得统一** |
| `metrics/LikeMqMetrics` | `like_mq_consume_total{action,result}` | `mq/internal/metrics` | 独立变化原因 |
| `mq/LikeEventContracts` | 3 个 Kafka payload + JSON 字段名 | `types.go` | producer/consumer 共享契约，单点定义 |
| `mq/LikeUpdateKafkaListener` | unmarshal→校验→建 outbox→单事务提交；**drop vs retry** | `likemq.go:83-134` | 消息入口，错误契约与 gRPC 完全不同 |
| `mq/LikeHotEventOutboxRelay` | `@Scheduled(fixedDelay=1000)`；`SendPending(100)`；单条失败不中断批次 | `like_outbox_sender.go` + `mq.go:31-43` | 定时 worker |
| `mq/LikeFactDispatchScheduler` | 建 `Wire.Sender` + 调 `Dispatcher.run(..., once=false, registerShutdownHook=false)`；`@PreDestroy` → `handle.stop()` | `cmd/fact-dispatch` + `process.go` | 合并后的编排器；**不注册任何对外服务** |
| `mq/LikeAuthorityEndpointConfig` | `AuthorityHandlerFactory.register(...)` | `cmd/fact-authority` | 合并后的 Servlet 装配 |
| `service/LikeDomainService` | 6 个用例；缓存读 → 回源 → 回填 | `logic/*` 6 个 | 同一变化原因（点赞域）+ 同一依赖边界（同 users 决策） |
| `service/LikeCursorIdGenerator` | `(unixMilli<<22)\|(nodeId<<12)\|seq` | `likeactionlogic.go:26-54` | **必须独立且禁止复用 `common/Snowflake`**：epoch 不一致会让 `score>>22` 还原出的时间戳偏差 15 年，直接破坏前端点赞时间 |
| `service/LikeActionScript` | 68 行 Lua | `likeactionlogic.go:56-122` | 原子性的唯一载体；**脚本文本属 L1 冻结**，不可拆成多次往返 |
| `service/LikeRedisKeys` | 6 个 key 模板 | 4 个 logic 里的 `fmt.Sprintf` | key 格式是运维/排障契约，散落 4 处易漂移 |
| `service/LikeMessageDigest` | 7 字段 canonical JSON → sha256 | `like_message_hash.go` | 字段顺序 = 哈希稳定性，独立可测 |
| `service/LikeMessageIngestService` | inbox 幂等 + 状态机 + fact + outbox，**单事务** | `like.go:148-241` | **§3.3 self-invocation**：`applyLikeMessage` 是内部调用，必须拆成独立 Bean 才能让 `@Transactional` 经代理生效 |
| `service/LikeDomainFactService` | 版本分配 + 双信封构造 + legacy 守卫 | `domain_fact.go:76-138` | 与 ingest 共享事务边界，但规则集独立可测 |
| `service/LikeDeliveryValidator` | 19 条 envelope/payload 交叉校验 | `fact_delivery.go:23-50` | 投递与权威读两条路径共用，独立才能保证"两处校验不会漂移" |
| `service/LikeFactDispatchService` | `dispatchFactOnce`：`SKIP LOCKED` 取件 → 校验 → 发送 → 结算 | `fact_delivery.go:52-125` | **独立 `@Service` Bean**：事务跨 HTTP I/O |
| `service/LikeAuthorityLookupService` | `authoritativeFact` 12 道重放校验 | `fact_authority.go:53-119` | 实现 `AuthorityLookup` 接口；是 authority 端点的全部业务 |
| `service/LikeTaskProgressPublisher` | 2 个可选 pusher；**nil 安全 + 吞错** | `likeactionlogic.go:311-343` | 出网副作用可独立降级/复用 |
| `service/impl/LikeServiceGrpcImpl` | `@GrpcService`，6 override | `server/likeserviceserver.go` | gRPC 契约边界 |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `like.go` | `LikeRpcApplication` + 容器 | ABSORB | 进程装配由框架承担 |
| `like.go:41` unary interceptor | `spring/RpcServerInterceptorFactory` | ADAPT | ⚠️ 当前未注册，需显式 `@Import`（见 §12.1 更正） |
| `like.go:30` 禁原生超时 | — | — | grpc starter 无等价默认超时，无代码可落 |
| `etc/like.yaml`（Redis TTL / Kafka topic / MessageRpc） | `config/LikeRpcProperties` + `application.yml` | ADAPT | 绑定机制换 Spring |
| `etc/like.yaml`（RocksDB / HotSpot / AntiBrush / KafkaPusher / Sync*） | — | — | **Go 侧零消费点，不迁移**（非 ABSORB，是死配置） |
| `config/config.go` | `config/LikeRpcProperties` | ADAPT | 只迁活跃段 |
| `svc/servicecontext.go`（Redis） | `StringRedisTemplate` | ADAPT | 有先例但仅 opsForValue；Hash/ZSet/Lua 见下 |
| `svc/servicecontext.go`（GORM + 池） | Hikari `application.yml` | ADAPT | 参数 10/100 与 message-rpc 一致 |
| `svc/servicecontext.go`（3× kq.Pusher） | Spring `KafkaTemplate` | NEW | RAG 首次引入 spring-kafka |
| `svc/servicecontext.go`（`newOptionalPusher` nil 语义） | `LikeTaskProgressPublisher` 的 null 检查 | ADAPT | 可选性是行为（无 broker 时不推送） |
| `svc/servicecontext.go`（MessageRpc） | `client/MessageRpcClient` | NEW | 外部服务客户端 |
| `likeservice/likeservice.go` | protobuf-maven-plugin 生成的 stub | ABSORB | 生成物，不手写 |
| `server/likeserviceserver.go` | `service/impl/LikeServiceGrpcImpl` | ADAPT | 分发由 starter 做 |
| `logic/*.go`（6 个） | `service/LikeDomainService`（6 方法） | MERGE | 同 users "9 合 1" 决策 |
| `likeactionlogic.go`（游标雪花） | `service/LikeCursorIdGenerator` | NEW | 禁用 `common/Snowflake`（epoch 不一致） |
| `likeactionlogic.go`（Lua） | `service/LikeActionScript` | NEW | 脚本即原子性契约 |
| `likeactionlogic.go`（key 拼接） | `service/LikeRedisKeys` | MERGE | 4 个 logic 的重复片段 |
| `likeactionlogic.go`（`notifyArticleLiked`） | `LikeDomainService` + `client/MessageRpcClient` | SPLIT | 出网副作用分离 |
| `likeactionlogic.go`（`syncTaskProgress`） | `service/LikeTaskProgressPublisher` | SPLIT | 同上 |
| `likeactionlogic.go:124` `activeLikeState` | — | — | 死常量，不迁 |
| `logic/getlikecountlogic.go` | `LikeDomainService.getLikeCount` | MERGE | 同上 |
| `logic/getlikestatelogic.go` | `LikeDomainService.getLikeState` | MERGE | 同上 |
| `logic/getuserlikelistlogic.go` | `LikeDomainService.getUserLikeList` | MERGE | 同上 |
| `logic/gettargetlikerlistlogic.go` | `LikeDomainService.getTargetLikerList` | MERGE | 同上 |
| `logic/getusertotallikelogic.go` | `LikeDomainService.getUserTotalLike` | MERGE | 同上 |
| `types/types.go` | `mq/LikeEventContracts` | NEW | 共享契约载体 |
| `metrics/metrics.go` | `metrics/LikeRpcMetrics` | ADAPT | 2 个指标名（含连字符那个）原样保留 |
| `model/db.go`（AutoMigrate） | Flyway `V1..V5` | ADAPT | 迁移机制替换 |
| `model/db.go`（Fatalln） | `bootstrap/LikeSchemaGate` | ADAPT | fail-fast 语义保留 |
| `model/data_model.go`（3 实体） | 3 个 `domain/*Entity` | SPLIT | 表用途不同 |
| `model/domain_fact.go`（2 实体） | 2 个 `domain/*Entity` | SPLIT | 事实 outbox vs 版本流水 |
| `model/data_model.go`（jsonb 列） | `mapper/JsonbTypeHandler` | ADAPT | ORM 适配；§3.3 约束见 `LikeDomainFactOutboxMapper` |
| `model/like.go`（查询方法） | `mapper/LikeRecordMapper` | ADAPT | 聚合查询 + 行锁 |
| `model/like.go`（`ProcessLikeMessageBatch` + `applyLikeMessage`） | `service/LikeMessageIngestService` | SPLIT | **独立 Bean 才能让 `@Transactional` 生效（§3.3）** |
| `model/like_message_hash.go` | `service/LikeMessageDigest` | NEW | 幂等指纹载体 |
| `model/like_consume_inbox.go` | `mapper/LikeConsumeInboxMapper` | ADAPT | 幂等资产保留 |
| `model/like_outbox.go` | `mapper/LikeOutboxEventMapper` | ADAPT | 状态机保留 |
| `model/domain_fact.go`（`appendLikeFact`） | `service/LikeDomainFactService` | SPLIT | 规则集独立可测 |
| `model/fact_delivery.go`（`validLikeDelivery`） | `service/LikeDeliveryValidator` | NEW | 投递/权威两处共用，杜绝漂移 |
| `model/fact_delivery.go`（`DispatchFactOnce`） | `service/LikeFactDispatchService` + `common/Dispatcher` | SPLIT | 取件事务与调度节奏分离 |
| `model/fact_authority.go`（`AuthoritativeFact`） | `service/LikeAuthorityLookupService` | NEW | `AuthorityLookup` 实现 |
| `model/fact_authority.go`（`deliveredLikeAuthority`） | 同上（私有方法） | MERGE | 与主校验同文件 |
| `migrations/001_*.sql` | `V1__create_like_record.sql` + `V2__create_like_consume_inbox.sql` + `V3__create_like_outbox_event.sql` + `V4__create_like_domain_fact_outbox.sql` | SPLIT | 5 表各自一份，符合 RAG 既有 `V1/V2/V3` 一表一文件的惯例 |
| `migrations/002_*.sql` | `V4__create_like_domain_fact_outbox.sql` 续 + `V5__create_like_fact_stream.sql` | SPLIT | 同上 |
| `migrations/003_*.sql` | `V6__create_like_fact_delivery.sql` | ADAPT | 含触发器函数与 `CREATE TRIGGER` |
| `internal/mq/mq.go`（kq consumer） | `mq/LikeUpdateKafkaListener` | NEW | listener container |
| `internal/mq/mq.go`（1s relay ticker） | `mq/LikeHotEventOutboxRelay` | ADAPT | ticker → `@Scheduled` |
| `internal/mq/internal/mqs/likemq.go`（`Consume`） | `mq/LikeUpdateKafkaListener` | NEW | drop/retry 契约 |
| `internal/mq/internal/mqs/likemq.go`（span） | `common/observability/MqConsumer` | REUSE | 对应 Go `observability.TraceConsumer` |
| `internal/mq/internal/mqs/likemq.go`（`buildFirstLikeOutbox`） | `LikeMessageIngestService` 内联 | MERGE | 与消息契约同生命周期 |
| `internal/mq/internal/mqs/like_outbox_sender.go` | `mq/LikeHotEventOutboxRelay` | NEW | 批次隔离语义保留 |
| `internal/mq/internal/svc/servicecontext.go` | DI 装配 | ABSORB | Spring 容器承担 |
| `internal/mq/internal/config/config.go` + `etc/mq.yaml` | `config/LikeRpcProperties` + `LikeKafkaTopics` | MERGE | 与 P1 配置合并（同库同表） |
| `internal/mq/internal/metrics/metrics.go` | `metrics/LikeMqMetrics` | ADAPT | 指标名保留 |
| `cmd/fact-dispatch/main.go` | `mq/LikeFactDispatchScheduler` | NEW | 合并为组件，理由见 §12.1 |
| `cmd/fact-authority/main.go` | `mq/LikeAuthorityEndpointConfig` | NEW | 合并为组件；监听地址 `UNKNOWN` |
| `cmd/*`（`ProcessLogger` × 2） | `bootstrap/LikeFactProcessLogging` | SPLIT | 合并后需显式建 3 份保住日志身份 |
| `cmd/*`（env 校验） | Spring properties 绑定 + fail-fast | ADAPT | token ≥32 等约束保留 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 6 个 RPC 及全字段；5 张表 + 全部索引/唯一键/CHECK；5 个 Kafka topic + 2 个 consumer group；6 个 Redis key 模板；游标位布局 `(t<<22)\|(node<<12)\|seq`；`event_id`/`event_type`/`schema_version`/`producer`/`operation_id`/`aggregate_id`/`stream_id`/`predecessor_event_id` 格式；3 个 Kafka payload 字段 |
| L2 业务语义有承载位置 | ✅ 幂等三层（inbox+payload_hash / last_operation_id 单调 / `uk_event_key`）；状态机 4 分支与"unlike 不撤销 dislike"；legacy `state==2` 不可调和守卫；软删行报错；毒丸消息 hash 冲突；first-like outbox 条件；3 个 `article` 专属分支；滑动 TTL；防穿透零值回填；脏数据 `create_time==0` 跳过；`isEnd/nextCursor` 规则；游标 `maxScore` 规则 |
| L3 技术语义有承载方案 | ✅ 事务边界 3 处（ingest 单事务 / dispatch 事务跨 HTTP I/O / 版本原子分配）→ **3 个独立 Bean**；`FOR UPDATE` / `FOR UPDATE SKIP LOCKED` / `ON CONFLICT DO NOTHING` / `ON CONFLICT DO UPDATE RETURNING` / 部分索引 + `NOT VALID` CHECK / 不可变触发器 → Mapper `@Select` + Flyway；调度节奏 → `Dispatcher.run` 原样复用 |
| 业务分支与异常路径已纳入 | ✅ **静默 drop vs 可重试**的四处分界；snowflake 失败导致 `msg_id=0` 的静默丢失链路；Kafka 推送失败 best-effort；Redis 错误吞掉 vs DB 错误致命的不对称；`status=3 BLOCKED` 终止重试 vs `status=2 FAILED` 可重试；batch 内单条失败不中断 |
| 框架吸收有机制依据 | ✅ gRPC server/interceptor、`KafkaTemplate`、`@Scheduled`、Flyway、Hikari、Actuator —— 均指出实际机制或标注"RAG 无先例需首次引入" |
| 复用组件已明确（逐字段核对） | ✅ `Wire.{Event,TechnicalReceipt,SubjectRef,AuthorityFact,strictDecode,canonicalHash,validateReceipt,parseRTWSubject,DCEventSender,DeliveryStatus}`、`Dispatcher`、`AuthorityHandler`+`AuthorityHandlerFactory`、`ProcessLogger`、`MqConsumer`、`RpcInterceptor`；**`likeFact` payload 判定 NEW、`common/Snowflake` 判定禁用** |
| 新增文件有职责依据 | ✅ `LikeCursorIdGenerator`（epoch 冲突）、`LikeActionScript`（原子性契约）、`MessageIngestService`/`FactDispatchService`（代理边界）、`LikeDeliveryValidator`（两处共用防漂移）、`AuthorityLookupService`、`LikeSchemaGate`（fail-fast）、`LikeFactProcessLogging`（合并拓扑新增）、`LikeTaskProgressPublisher`（出网副作用）—— 均非"Go 有同名文件" |
| 合并有组织依据 | ✅ 6 logic → `LikeDomainService`（同 users 决策）；4 个 Go `main` → 1 个 Spring Boot 应用 + 4 个组件（P2 consumer、P2 relay、P3 dispatch、P4 authority，同库同表 + 无 gRPC 注册面，§12.1 给出四条依据） |
| 拆分可解释 | ✅ 业务/持久化/outbox/fact/投递/权威读六层分责；3 张 outbox/inbox 表各自成 Mapper；5 表各自一份 Flyway |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Logic.java` / `Repository.java` / `DataModel.java` / `ServiceContext.java` / `Server.java` / `Types.java` / `metrics` 之外的 `Metrics.java` |
| 未把未实现能力说成现有机制 | ✅ `like-rpc` 空骨架 + 空依赖已在 §12.1 声明；spring-kafka / Hash-ZSet-Lua / HTTP 端口三项均标注"无先例/UNKNOWN" |
| 两文件拓扑结论一致 | ✅ 与 `FileTree/like/api.md` §12.1 末行同一结论：api 不参与合并争议，只共用新建的 `like-common` maven 模块（rpc 侧同一模块、同一组错误码数值） |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **合并后 like-rpc 必须新增 HTTP 端口** | Go 的 P1 是纯 gRPC 进程，`AuthorityHandler` 在独立 P4 上监听 `LIKE_FACT_AUTHORITY_LISTEN`。合并成 1 个 JVM 后要么给 like-rpc 加 HTTP connector，要么挂 management port —— **wire 面新增，需拍板** |
| 2 | **`-once` 单次投递模式无处安放** | `fact-dispatch/main.go:25` 的 flag 是为 CronJob / 手工排障准备的。常驻 `@Scheduled` 无法覆盖该用法 |
| 3 | **Flyway 迁移归属未定** | like-rpc 的 V1..V6 与 users(`user-rpc` V1/V2、`admin-rpc` V3)、message-rpc(V1..V3) 抢同一 `flyway_schema_history` 的版本号。Go 侧 `AutoMigrate` 无此问题。**AGENTS.md §6.3 问题 1，本文件不自行拍板** |
| 4 | **两个 Prometheus subsystem 名不一致** | `metrics.go:11` 用 `"like-rpc"`（连字符），`:17` 用 `"like_rpc"`（下划线）。Prometheus 指标名中连字符不合法，实际导出名可能被清洗 —— 迁移时"修正"会变更指标契约 |
| 5 | **三个 `InitMetrics()` 零调用** | 实测全仓零调用点 → Go 侧三个指标实际未注册、未暴露。Micrometer 自动注册会把它们变成可见指标（与 api 侧同类问题） |
| 6 | **Redis 死配置对应死能力** | `HotSpot.AntiAvalanche`（防雪崩队列）与 `AntiBrush`（IP/账号频控）在 Go 侧**完全没有实现**。Java 侧若"补全"= 新增行为；若照迁配置= 搬死代码。本文件选择**不迁配置、不实现逻辑**，需确认 |
| 7 | **死配置 `RocksDB` + `Postgres.Sync*` 的意图** | yaml 注释描述了"RocksDB 热存 + 300s 批量冷备到 PG"的完整设计，但代码里从未打开 RocksDB、也从未实现同步任务。`like_record` 事实上只有 PG 一份 |
| 8 | **`article-hot-events` 无实际消费者** | like-mq 往该 topic 推 `article_hot_like`，但下游 `hot/rpc/internal/logic/like_article_logic.go:42-45` 的 `LikeArticle` 仍返回 `Unimplemented`。该 topic 与 `like_outbox_event` 表目前是**只写不读** |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| like 5 张表的 Flyway 归属 | 与 users/message 同 schema 的版本号冲突（AGENTS.md §6.3 问题 1），**需用户拍板** |
| authority servlet 的监听地址与端口 | 合并后 like-rpc 是否开 HTTP 端口、开在哪个端口 |
| `Dispatcher.run` 的 `registerShutdownHook` 取值 | Go 的 `RunDispatcher` 由 `ctx.Done()` 驱动；RAG 版默认 `false`（推荐 `@PreDestroy`）。合并后用哪个，需确认 |
| `ZREVRANGEBYSCORE key 0 maxScore WITHSCORES LIMIT 0 n` 的 Spring 侧等价写法 | 非 `ZSetOperations` 标准方法，可能需 `RedisCallback`；`Expire` 与 `Zadd` 的返回值语义也需逐一核对 |
| Lua `EvalCtx` 的返回值类型处理 | Go 侧显式处理 `int64` / `int` 两种返回类型（`likeactionlogic.go:203-208`）。Lettuce 返回 `Long`；需确认无第三种类型 |
| `common/Snowflake` 是否应被 like 复用 | 结论是**不能**用于游标；但 `KafkaLikeMsg.msgId` 与 outbox `event_id` 用它是可以的 —— 两处 ID 的位布局差异是否影响 `msg_id` 的单调性保证（`operationID <= last_operation_id` 的比较前提），需与 rpc 侧一并确认 |
| `likeRecord.deleted_at` 的软删索引 | Go 用 `gorm.DeletedAt` + `index`；MyBatis-Plus 需 `@TableLogic` 或手写 `deleted_at IS NULL`，两者的 `Unscoped()` 取件语义不同 |
| `technical_offset >= 1` 与 DC 契约 | `fact_authority.go:14` 要求 offset ≥ 1；RAG 的 `Wire.validateReceipt` 同样要求 `offset >= 1`（`Wire.java:309`）。两侧一致，但 DC 侧真实返回是否满足未验证 |
| `RTW_LIKE_FACT_SHARED_READY_FILE` 验收夹具（`fact_process_fixture_test.go`） | 依赖 `community_fact_acceptance.sh` 与跨进程协作；RAG 单 JVM 下是否仍需要该夹具，未定 |