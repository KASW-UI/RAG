# task-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/task/rpc`
> 目标根包：`com.rag.task.task.rpc`
> 推导日期：2026-10-23
> 配套文档：`docs/FileTree/task/api.md`（**同域，无 `common.md`**，依据见 §12.1 末）

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

| 检查项 | 实测结果 |
|---|---|
| `service/task/task-rpc/pom.xml` | 19 行，**无 `<dependencies>` 块**（`task-rpc/pom.xml:12-18`） |
| `src/main/java` / `src/main/resources` / `src/test/java` | 存在但**为空目录** |
| Java 文件数 | **0**（`target/task-rpc-0.0.1-SNAPSHOT.jar` 已产出，为空 jar） |
| `service/task/pom.xml:14-18` `<modules>` | `task-api` / `task-rpc` / `task-proto` —— **无 `task-common`**（对比 `service/comment/pom.xml:14-19` 有 4 项含 `comment-common`） |
| `task-proto` | ✅ **已完成**：`.proto` 与 Go 侧 `proto/task.proto` 逐行一致，仅 option 块不同（Go `:4` `option go_package = "."` → RAG `:4-5` `java_package` + `java_multiple_files`）。**`package task` 两侧完全相同 ⇒ 零偏离**，`TaskServiceGrpc` 方法路径 `/task.TaskService/GetTask` 与 Go 一致 |

包结构依据来自同仓库已落地的 rpc 侧模块：

| 参照模块 | 提供的一致性依据 |
|---|---|
| `service/like/like-rpc`（**最贴近本域形态**：gRPC + Kafka + Redis + PG + 定时器，且不引 web） | `LikeRpcApplication.java:58-79` 的注解组合（`@SpringBootApplication` + `@ConfigurationPropertiesScan` + `@MapperScan` + `@EnableScheduling` + 一长串 `@Import`）；`mq/LikeUpdateKafkaListener.java:76-86`（`@KafkaListener` + `topics`/`groupId` 占位符）；`mq/LikeHotEventOutboxRelay`（`@Scheduled`）；`config/LikeKafkaTopics.java`（topic 单点定义）；`pom.xml` 的 Kafka/Redis/Flyway 依赖与逐条注释（`:104,143,146-158,198,210-226,243`） |
| `service/hot/hot-rpc` | 🔴 **次贴近**：`mq/HotRedisSnapshotStore.java:143-152` 的 `redis.executePipelined(SessionCallback)` —— **本域两条 sink 都要 pipeline HSET+EXPIRE，这是唯一现成先例**；`mq/HotMergeScheduler.java:147-148` 的 `@Scheduled(fixedRate=…)` |
| `service/message/message-rpc` | `service/impl/MessageRpcServiceImpl.java:31-60`（thin wrapper + `StatusRuntimeException` 透传 + 其它异常兜底 INTERNAL）；`application.yml:21-22`（Hikari 池 100/10）、`:58`（`spring.flyway.table: flyway_schema_history_message`）、`:126-144`（`otel.*` + `observability.rpc-timeout: 2s`） |
| `service/article/article-rpc` | `bootstrap/ArticleSchemaGate.java:46-113`（fail-fast 闸）；`config/Jackson2ObjectMapperConfiguration.java` |
| `service/users/user/user-rpc` | `bootstrap/{SnowflakeBootstrap,NacosRegistration,UserSchemaBootstrap}` |
| `service/common` | `spring/RpcServerInterceptorFactory.java:34-47`（**必须显式 `@Import` 才生效**）；`spring/ObservabilityProperties.java`；`observability/MqConsumer.java:71-119`（CONSUMER span）；`persistence/{DeadlineQueryTimeoutInjector,SqlTimeoutStatusTranslator}` |

### 已有组件（跨模块可复用）

| 组件 | 字段级核对结果 | 判定 |
|---|---|---|
| `common/spring/RpcServerInterceptorFactory`（`:34-47`） | 产出 `ServerInterceptor` Bean，读 `ObservabilityProperties.rpcTimeout` / `slowThreshold` | **REUSE（必须 `@Import`）** |
| `common/observability/MqConsumer.traceConsumer`（`:71-119`） | SpanKind=CONSUMER + attrs + 超时/系统错误标记 | **REUSE** —— 对应 Go `observability.TraceConsumer`（`service/common/observability/mq.go:12-43`），本域两条 sink 各调一次（`like_sink.go:82,133`、`article_sink.go:94,163`） |
| `common/observability/HttpMiddleware` / `RpcInterceptor` | HTTP span 对本域无用（**纯 gRPC 进程，零 HTTP 监听**）；gRPC 侧走 `RpcServerInterceptorFactory` | **部分 REUSE** |
| `common/persistence/DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator` | 按线程 Deadline 设 `setQueryTimeout`；把 `57014 query_canceled` 翻成 `DEADLINE_EXCEEDED` | **REUSE**（like-rpc 已实测必须显式 `@Import`） |
| `common/spring/{LoggerFactoryBean,SnowflakeBean}` | **本域不使用** —— Go 侧 task-rpc 无 `snowflake.GetID()` 调用（`grep` 零命中），`LikeRpcApplication.java:63-64` 的 `SnowflakeBean` 导入是为 like 的 msg_id/event_id，task 不需要 | **不复用** |
| Redis Hash/ZSet/pipeline | ✅ 现成先例：`like-rpc/service/LikeDomainService.java:767-818`（`opsForHash().multiGet/get/put`、`opsForValue()`）、`hot-rpc/mq/HotRedisSnapshotStore.java:143-152`（`executePipelined(SessionCallback)`）。依赖见 `like-rpc/pom.xml:104,126` | **REUSE 依赖 + ADAPT 代码** |
| Redis Lua | `like-rpc/service/LikeActionScript.java:143`、`hot-rpc/.../HotRedisSnapshotStore.java:75` —— **本域零 Lua**（Go 侧 task 无 `Eval`/`ScriptLoad`） | **不需要** |
| Redis Stream（`XADD`） | ⭐ **全仓零先例**：`grep -rn "opsForStream\|RedisStreamOperations" service/` **零命中**。Go 侧是 `rdb.XAdd`（`reward/producer.go:20-29`） | **UNKNOWN**（需确认 Spring Data Redis 下的等价写法与 stream 条目的序列化） |

### 已确认的框架机制

- Spring Boot **4.0.8** / Java **21**（`service/task/pom.xml:20-23`）。
- `net.devh:grpc-server-spring-boot-starter`：`@GrpcService`（`message-rpc/.../MessageRpcServiceImpl.java:25,60`）。
- MyBatis-Plus `BaseMapper` + `@TableName` + `@TableId` + `@Select` 注解式原生 SQL；**全仓零 XML mapper**；`<> ` 需 CDATA 孪生常量；`<where>` 不可用。
- **无 MyBatis-Plus 分页拦截器** ⇒ 分页手工拼 `LIMIT/OFFSET`（先例 `message-rpc/mapper/GoStylePagination.java`）—— **本域无分页，不需要**。
- jsonb + `JsonbTypeHandler` —— **本域两表无 jsonb 列，不需要**。
- Spring Kafka：**必须同时引 `spring-kafka`（底层库）与 `spring-boot-kafka`（Boot 4 拆出的自动配置模块）**，缺后者是**硬启动失败** `NoSuchBeanDefinitionException: KafkaTemplate`（`like-rpc/pom.xml:146-158` 有实测注释）。
- Boot 4 拆模块：Jackson 3 `ObjectMapper` 需 `config/Jackson2ObjectMapperConfiguration` 自行装配（`article-rpc` 与 `like-rpc` 均已实测）。
- **不引 `spring-boot-starter-web`** —— 否则纯 gRPC 进程会被拉起 web 容器（`like-rpc/pom.xml:243`）。
- Flyway（`flyway-core` + `flyway-database-postgresql` + `spring-boot-flyway`，`like-rpc/pom.xml:211,215,226`）。

### 当前 Component 可以直接复用的能力

- `RpcServerInterceptorFactory` + `ObservabilityProperties` + `DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator` + `Jackson2ObjectMapperConfiguration` + `LoggerFactoryBean` —— 6 个 `@Import`，全部有 like-rpc 实测先例。
- `MqConsumer.traceConsumer`（两条 sink 的 span）。
- `StringRedisTemplate` + `executePipelined`（Hash / Pipeline）。
- `MeterRegistry`（`management.prometheus`）—— 但本域**零指标**（见 §12.7 #4）。

### 需要进一步确认的组织约定

| 项 | 结论 |
|---|---|
| 🔴 **goka（`initGoKa.go`）在 Java 侧无对应物** | Go 的 3 个 goka processor（`:109-159`, `:161-193`, `:195-227`）用 `goka.Persist(new(codec.Int64))`（`:167,201`）把**窗口计数存进 goka 自己的表存储（RocksDB）**，那既不在 PostgreSQL 也不在 Redis。RAG 侧无任何 stream-processing 框架。⇒ 计数存储选型是**本域最大的未决问题**，见 §12.7 #1 与 UNKNOWN |
| 🔴 **`TaskCountStore` 的后端** | 候选：① PG 独立计数表（可复用现有 PG，最保守）；② Redis `HINCRBY`（最轻，但丢数据窗口与 goka 不同）；③ 保留一个专用 KV。**必须显式拍板** —— UNKNOWN |
| **consumer group 命名** | Go 有 **7 个** group（`task.yaml:23-32`），其中 `GroupFilterArticle`（`:32`）**零使用**（`initArticleFilter.go:30` 用的是 `GroupKafkaFilter`）。Java 侧是否保留全部 7 个（含未使用的）需拍板 —— **UNKNOWN（建议：只迁实际使用的 6 个，并在 yaml 里注明被跳过的那个）** |
| **`Kafka.Offset` / `Consumers` / `Processors`** | `task.yaml:34-36` 配了 `latest` / `8` / `8`，但 3 个 kq consumer **全部硬编码** `Consumers: 1, Offset: "latest", Processors: 1`（`initKafkaRaw.go:31-33`、`initUserFilter.go:38-40`、`initArticleFilter.go:38-40`）。且 `config.Config.Kafka` 结构体（`config/config.go:29-30`）**只有 `Offset` 与 `Consumers`，没有 `Processors`** ⇒ `Processors: 8` 是未知键。⇒ **配置项不迁**，并发度按硬编码值 1 表达。go-zero `conf.MustLoad` 对未知键的严格性 ⇒ UNKNOWN |
| **`LikeRedis` 段超时/池参数** | `task.yaml:42-46` 的 `DialTimeoutMs`/`ReadTimeoutMs`/`WriteTimeoutMs`/`PoolSize`/`MinIdleConns` 在 `config/config.go:32-37` 的结构体里**全部没有对应字段**；`service_context.go:25-29` 的 `redis.NewClient` 也确实只传了 `Addr`/`Password`/`DB`。⇒ **不迁**（Go 侧本来就是零超时直连） |
| **`Postgres.ConnMaxLifetimeMinutes`** | 🔴 Go 侧是**真 bug**：`service_context.go:45` `sqlDB.SetConnMaxLifetime(time.Duration(c.Postgres.ConnMaxLifetimeMinutes))` —— `time.Duration(30)` = **30 纳秒**，不是 30 分钟（漏了 `time.Minute`）。Java 侧配 `max-lifetime: 30m` 是"修正"而非"翻译"，虽方向正确但**改变了可观察行为**（连接会被 30ns 后全部重建 → 实际表现为持续重建；配 30m 则是正常值）。⇒ 需拍板，并按 §3.2 留痕。**UNKNOWN（需拍板）** |
| **`Postgres.MaxOpenConns` / `MaxIdleConns`** | `task.yaml:51-52` 都是 50，`service_context.go:43-44` 真实消费 ⇒ 落 Hikari `maximum-pool-size: 50` / `minimum-idle: 50`（先例 `message-rpc/application.yml:21-22`） |

---

## 12.2 第二部分：Go 原始文件树

```
service/task/rpc/
├── task.go                                        # 入口：4 个 goroutine + zrpc server + reflection + unary interceptor
├── etc/task.yaml                                  # Etcd{Kafka×6 topic, group×7} + LikeRedis + Postgres
├── taskservice/task_service.go                    # goctl 生成的 gRPC 客户端桩（纯生成物）
└── internal/
    ├── config/config.go                           # RpcServerConf + PointsRpc/UserRpc(死) + Kafka + LikeRedis + Postgres
    ├── svc/service_context.go                     # redis.NewClient + gorm.Open + 池 + AutoMigrate(2 表)
    ├── server/task_service_server.go              # 1 个 RPC → logic 的 thin wrapper
    ├── logic/get_task_logic.go                    # Redis HGetAll → 回源 PG → 回填 → 与 AllTaskDefs 合并
    ├── Model/taskModel.go                         # TaskDef(注释) + AllTaskDefs(硬编码 2 条) + TaskProgress + UserLikeCount
    ├── Init/
    │   ├── initKafkaRaw.go                        # no-op 探针 consumer（task.logs.raw / group consumer31）
    │   ├── initGoKa.go                            # 3 个 goka processor：扇出 → 用户计数 → 文章计数
    │   ├── initUserFilter.go                      # sink.Start + kq consumer（filtered.User / group filterUser）
    │   ├── initArticleFilter.go                   # sink.Start + kq consumer（filtered.Article / group kafkaFilter）
    │   └── initReward.go                          # ★ 全文件注释掉的死代码
    ├── sink/
    │   ├── like_sink.go                           # ★ 点赞进度写入（taskID 1902, target 5）+ user_like_count
    │   └── article_sink.go                        # ★ 文章获赞进度写入（taskID 190, target 3）
    └── reward/
        ├── type.go                                # RedisEvent + NewEvent（reward_id = uid + taskID，加法！）
        ├── producer.go                            # ★ 活的：XADD stream:task:reward
        └── worker.go                              # ★ 全文件注释掉的死代码
```

共 **18 个文件**（17 个 `.go` + 1 个 `.yaml`），与"rpc 18 个"的清单一致。`rpc/pb/**` 是 protoc 生成物，排除。

---

## 12.3 第三部分：Go 文件职责分析

### 🔴 本域最关键的判断：进度写入逻辑在哪里？—— **答案：存在，但不在 gRPC 服务里**

| 问题 | 结论与证据 |
|---|---|
| `GetTask` RPC 会不会写进度？ | **不会。** `internal/logic/get_task_logic.go:40-104` 全文只做「读 Redis → 回源 PG → 回填 Redis 缓存 → 与任务定义合并」。它对 PG **只有一条 `SELECT`**（`:53-57`），**零 INSERT/UPDATE**。它唯一的写是缓存回填（`:73-76` 的 pipeline `HSet`+`Expire`、`:78` 的 `Expire`），且这两处写的都是**读缓存**，不是进度 |
| 那进度是谁写的？ | **两条 Kafka sink**：`internal/sink/like_sink.go`（`:295-326` 的 `updateDoingTaskProgress`、`:366-385` 的 `markTaskDoneInDB`、`:406-422` 的 `upsertChunk`）与 `internal/sink/article_sink.go`（`:385-419`、`:462-491`）。它们由 `task.go:33-34` 的 `go Init.StartTaskKafkaUserFilter(ctx)` / `go Init.StartTaskKafkaArticleFilter(ctx)` 启动，入口在 `initUserFilter.go:32-33` / `initArticleFilter.go:32-33` 的 `sink.NewXxxConsumer(svcCtx.Rdb, svcCtx.Gdb)` + `consumer.Start(ctx)` |
| 数据从哪来？ | Kafka：`task.logs.filtered.User`（key=用户 id，value=裸计数字符串）→ `like_sink.Consume`（`like_sink.go:81-108`）；`task.logs.filtered.Article`（key=文章 id，value=`{"user_id":…,"cur":…}` JSON）→ `article_sink.Consume`（`article_sink.go:93-124`）。这两个 topic 的**生产者是 like 域**（`Sea-RideTheWind_golang/service/like/rpc/etc/like.yaml:92,99`），中间还有本域自己的 goka 层（见下） |
| ⇒ Java 侧需要 domain/mapper 吗？ | **需要**，但理由与直觉不同：**不是为了 gRPC 读路径**（读路径只需 1 条 SELECT），而是为了 **sink 写路径**（`ON CONFLICT DO UPDATE` 的两条 upsert + `user_like_count` 的批量 upsert）。且 **gRPC 读路径与 sink 写路径共享同一张 `task_progress` 表**，所以 Mapper 必须存在且两侧共用 |

### `rpc/task.go`（49 行）

| 行 | 代码 | 语义 |
|---|---|---|
| `:26-27` | `conf.MustLoad(*configFile, &c)` | |
| `:28` | `rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)` | `service/common/observability/rpc.go:19-23`：取值后把 `conf.Timeout = 0`，关掉 go-zero 原生超时 |
| `:29` | `ctx := svc.NewServiceContext(c)` | Redis + PG + AutoMigrate，失败 `panic` |
| `:31-34` | `go Init.StartTaskKafkaRaw(ctx)` / `StartTaskGoKa` / `StartTaskKafkaUserFilter` / `StartTaskKafkaArticleFilter` | **4 个后台 goroutine**，与 gRPC server 生命周期同进程 |
| `:35` | `//go Init.StartInitReward(ctx)` | ⭐ **注释掉** ⇒ reward worker 永不启动（与 `initReward.go` 全文件注释、`reward/worker.go` 全文件注释一致） |
| `:37-43` | `zrpc.MustNewServer(c.RpcServerConf, func(grpcServer){ RegisterTaskServiceServer(...); if DevMode\|\|TestMode { reflection.Register } })` | 1 个 service、1 个 RPC |
| `:44` | `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))` | 🔴 `DisableNativeRpcTimeout` 读的是 `zrpc.RpcServerConf.Timeout`，而 `task.yaml` **没有 `Timeout` 键** ⇒ 取 go-zero 默认值（`zrpc/config.go:46` = 2000ms，与 `message-rpc/application.yml:142-144` 的注释同款取证）⇒ **rpc 侧超时 2s**，落 `observability.rpc-timeout: 2s` |
| `:45,48` | `defer s.Stop()` + `s.Start()` | |

- **L3**：gRPC interceptor **不会自动注册** —— Java 侧必须 `GrpcServerConfigurer.intercept(...)`（`LikeRpcApplication.java` 的 `server.intercept(...)` 同款，见 §6.3 问题 3）。
- **L4**：4 个 goroutine → Spring 的 listener container + `@Scheduled` + `@PostConstruct`，由容器生命周期统一管理。

### `rpc/etc/task.yaml`（53 行）

- `:1-2` `Name: task.rpc` / `ListenOn: 0.0.0.0:9005`（Etcd 注册的 key 来自 `zrpc.RpcServerConf`，`:3-6` `Etcd.Key: task.rpc`）。
- `:14-36` `Kafka`：**6 个 topic**（`InTopic: task.logs.raw`、`RawUserTopic: task.logs.raw.User`、`RawArticleTopic: task.logs.raw.Article`、`OutUserTopic: task.logs.filtered.User`、`OutArticleTopic: task.logs.filtered.Article`）+ **7 个 consumer group** + `Offset: latest` / `Consumers: 8` / `Processors: 8`（后三者被硬编码覆盖，见 §12.1）。
- `:38-47` `LikeRedis{Addr: redis:6379, Pass:"", DB:0, DialTimeoutMs:500, ReadTimeoutMs:1000, WriteTimeoutMs:1000, PoolSize:200, MinIdleConns:20, consumerName: task.reward.test}` —— ⭐ **段名叫 `LikeRedis`**（历史遗留，本服务里没有 like），且只有 `Addr`/`Pass`/`DB`/`ConsumerName` 四个字段被真正消费（`config/config.go:32-37`），`ConsumerName` 只被死代码 `reward` 用。
- `:49-53` `Postgres{Dsn: host=${POSTGRES_ADDR} … TimeZone=Asia/Shanghai, MaxOpenConns:50, MaxIdleConns:50, ConnMaxLifetimeMinutes:30}`。
- 🔴 **`TimeZone=Asia/Shanghai` 是 DSN 的一部分**，而 `task_progress.done_at` / `created_at` / `updated_at` 是 `time.Time`（`taskModel.go:34-36`）。⇒ Java 侧连接串必须保留同一时区参数，否则时间戳语义漂移。这条与 AGENTS.md §0 第 2 条记录的 `TIMESTAMP⇒TIMESTAMPTZ` 问题是同类。

### `rpc/taskservice/task_service.go`（39 行）

goctl 生成物：`:16-19` 三个类型别名、`:21-23` `TaskService` 接口（**1 个方法** `GetTask`）、`:25-34` `defaultTaskService`、`:36-39` `GetTask` 转发。
⇒ **纯生成物，不迁移**。Java 侧由 `task-proto` 的 `protobuf-maven-plugin` 生成 `TaskServiceGrpc.TaskServiceBlockingStub`（实测产物已在 `task-proto/target/generated-sources/protobuf/grpc-java/`）。

### `rpc/internal/config/config.go`（44 行）

`Config = zrpc.RpcServerConf + PointsRpc + UserRpc + Kafka{Brokers,5 topic,5 group,GroupFilterUser/Article,GroupGoKa{,User,Article},Offset,Consumers} + LikeRedis{Addr,Pass,DB,ConsumerName} + Postgres{Dsn,MaxOpenConns,MaxIdleConns,ConnMaxLifetimeMinutes}`（`:7-44`）。

- 🔴 **`PointsRpc`（`:9`）与 `UserRpc`（`:10`）是死配置**：`service_context.go:4,8,18-19,31-32,55-56` 五处把它们全部注释掉，唯一消费者是注释掉的 `reward/worker.go:18,73,77,83`。⇒ **不建 `client/` 包**。
- `config.Config.Kafka` **没有 `Processors` 字段**（`:29-30` 只有 `Offset` + `Consumers`）⇒ `task.yaml:36` 的 `Processors: 8` 是未知键。
- `LikeRedis` **没有**任何 timeout/pool 字段（`:32-37`）⇒ `task.yaml:42-46` 的 5 个参数是未知键。

### `rpc/internal/svc/service_context.go`（58 行）

| 行 | 代码 | 语义 / 问题 |
|---|---|---|
| `:16-22` | `ServiceContext{ Config; /*PointsClient; UserClient*/ Rdb *redis.Client; Gdb *gorm.DB }` | 2 个活跃依赖 |
| `:25-29` | `redis.NewClient(&redis.Options{Addr, Password, DB})` | **无任何超时/池参数**（对应 §12.1 的 `LikeRedis` 未知键） |
| `:34-37` | `gorm.Open(postgres.Open(c.Postgres.Dsn), &gorm.Config{})`；失败 `panic(err)` | DSN 含 `TimeZone=Asia/Shanghai` |
| `:39-42` | `gdb.DB()`；失败 `panic(err)` | |
| `:43-44` | `SetMaxOpenConns(50)` / `SetMaxIdleConns(50)` | ⇒ Hikari 50/50 |
| `:45` | `SetConnMaxLifetime(time.Duration(c.Postgres.ConnMaxLifetimeMinutes))` | 🔴 **Go bug**：缺 `time.Minute` ⇒ 30 **纳秒** |
| `:47-49` | `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})`；失败 `panic` | ⭐ **只建 2 张表**：`task_progress` + `user_like_count`。**`task_def` 不建** |
| `:51-57` | return | |

- **L3**：Go 是 `AutoMigrate`（幂等自愈，缺表自动建），**不是** fail-fast 校验。⇒ Java 侧用 Flyway 迁移（rpc 模块的既有惯例，`message/like/article` 三家都用），而不是 article-rpc 那种 `SchemaGate`。
- **L3**：三处 `panic` ⇒ **进程起不来**。Java 侧的等价是「Flyway 迁移失败 ⇒ 启动失败」+「数据源初始连接失败 ⇒ 启动失败」，语义等价。
- **⚠️ `AutoMigrate` 由 GORM 生成 DDL**，本域**没有手写 migration 目录** ⇒ RAG 侧 Flyway 的 `V1/V2` 需要**从零写**这两张表的 DDL，且列类型/索引/默认值要按 GORM 的映射反推。这是 §6.3 意义上的**新增推导**，标注 UNKNOWN（需与 Go 侧真 PG 上的实际 DDL 对拍）。

### `rpc/internal/Model/taskModel.go`（51 行）

| 行 | 内容 |
|---|---|
| `:6-14` | ⭐ `TaskDef` 结构体 + `TableName() = "task_def"` **整段被注释掉** ⇒ **全仓不存在 `task_def` 表**（`grep -rn "task_def" rpc/` 只命中这一行注释）。**任务定义是硬编码常量，不是数据库表** |
| `:15-20` | 注释掉的那份之外**没有**活的 `TaskDef` 定义 —— 活的是 `:22-25` 的字面量 |
| `:22-25` | ⭐ `AllTaskDefs = []TaskDef{ {190, "文章获赞任务", "有一篇文章点赞大于3", 3}, {1902, "点赞任务", "给文章点赞5次", 5} }` —— **任务定义的唯一真源是这段 Go 字面量** |
| `:28-41` | `TaskProgress{UserID int64 PK, TaskID int64 PK, Status string, Progress int64, Target int64, DoneAt *time.Time, CreatedAt, UpdatedAt}` + `TableName() = "task_progress"` —— **复合主键 (user_id, task_id)** |
| `:43-50` | `UserLikeCount{UserID string PK, LikeCount int64, UpdatedAt}` + `TableName() = "user_like_count"` —— ⭐ **`user_id` 是 `string`**（与 `task_progress.user_id` 的 `int64` 不同！） |

🔴 **同一张 `task_progress` 表在 Go 侧有 3 个几乎重复的结构体定义**：`Model.TaskProgress`（`taskModel.go:28-37`）、`sink.UserTaskProgress`（`like_sink.go:36-42`）、`sink.ArticleLikeTaskProgress`（`article_sink.go:33-45`）。
三者的差异**只有 4 处**，且都值得记录：
1. `ArticleLikeTaskProgress` 把 `ArticleID` **注释掉**（`article_sink.go:36,317,470,481`）⇒ **每 (user, task190) 只有一行**，per-article 粒度丢失。
2. `ArticleLikeTaskProgress` 把 `DoneAt`/`CreatedAt`/`UpdatedAt` **注释掉**（`article_sink.go:42-44`）⇒ 但它的 SQL 又写了 `updated_at = now()`（`:412,488`）与 `done_at = COALESCE(...)`（`:487`）⇒ **依赖 DB 侧列存在**。
3. `ArticleLikeTaskProgress` 无 `TableName()`……**有**（`:47-49`，返回 `task_progress`）。
4. `like_sink.UserLikeCount`（`:17-25`）与 `Model.UserLikeCount`（`taskModel.go:43-50`）是**同一张表的重复定义**。

⇒ Java 侧**必须收敛成 1 个 Entity**，否则会出现 3 份映射漂移。

### `rpc/internal/logic/get_task_logic.go`（140 行）—— 唯一的 gRPC 用例

| 行 | 代码 | 语义 |
|---|---|---|
| `:34-38` | 注释掉的空实现 | 死代码 |
| `:43-45` | `uid := in.GetUserId(); if uid <= 0 { return nil, status.Error(codes.InvalidArgument, "user_id invalid") }` | ⭐ **唯一的业务错误**：gRPC **标准码 3** + 字面量 `"user_id invalid"`。**没有业务码** |
| `:48-49` | `pkey := buildUserProgressKey(uid)`; `pm, rerr := l.svcCtx.Rdb.HGetAll(ctx, pkey).Result()` | key = `task:progress:{uid}` |
| `:51` | `if rerr != nil \|\| len(pm) == 0 {` | ⭐ **Redis 出错与缓存未命中走同一条回源路径**（Redis 错不返回错误，降级查库） |
| `:53-57` | `Gdb.Model(&TaskProgress{}).Select("user_id, task_id, status, progress, target").Where("user_id = ?", uid).Find(&rows)` | 5 列、**单用户**、无分页 |
| `:58-60` | `if err != nil { return nil, err }` | ⭐ **DB 错误原样 `return err`**（非 gRPC status）⇒ grpc 框架包成 `codes.Unknown` ⇒ api 侧 `errcode/grpc.go:31-32` 映射成 **HTTP 500** |
| `:62-71` | 把每行 `packProgress(status, progress, target)` 打进 `pm[taskID]` | |
| `:73-76` | `pipe.HSet(ctx, pkey, kv)` + `pipe.Expire(ctx, pkey, taskProgressTTL())`；`_, _ = pipe.Exec(ctx)` | ⭐ **回填失败完全忽略**（显式 `_ =`） |
| `:78` | `_ = l.svcCtx.Rdb.Expire(ctx, pkey, 30*time.Second).Err()` | ⭐ 空结果分支：对**不存在的 key** 发 `EXPIRE` ⇒ Redis 返回 0、**不建 key** ⇒ **负缓存实际上不存在**，下个请求仍会回源 |
| `:83-101` | 遍历 `Model.AllTaskDefs`，命中则取 `progress`，否则 `0`；`required := d.RequiredProgress` | ⭐ **每个任务定义都返回一条**（未开始 → `completion_progress = 0`） |
| `:86-87` | 注释明写「required_progress 来自任务定义（更合理，不要用 progress 表的 target 覆盖 defs）」 | **`target` 列永不进响应** |
| `:89-92` | `_, p, _ := unpackProgress(packed)` | ⭐ **`status` 与 `target` 被显式丢弃**（注释：`status/target 目前 proto 用不上`） |
| `:94-100` | 构造 `__.Task{Name, Desc, TaskId, CompletionProgress: progress, RequiredProgress: required}` | |
| `:106-108` | `buildUserProgressKey(uid) = "task:progress:" + uid` | 🔴 见下方"key 拓扑" |
| `:110-112` | `taskProgressTTL() = 15 * time.Minute` | |
| `:114-118` | `packProgress = status + "\|" + progress + "\|" + target` | 注释：`// status 不要包含 '\|'` |
| `:120-129` | `unpackProgress`：按 `\|` split，**`len(parts) != 3` 直接返回全零** | ⭐ 容错极弱：任何格式漂移都静默变成 `progress=0` |
| `:131-139` | `parseInt64Default(s, def)`：`""` 或 `ParseInt` 失败都回退 `def`（此处 `def=0`） | |

#### 🔴 Redis key 拓扑（本域最容易被"照抄"带偏的一张图）

| key 模板 | 写方 | 读方 | `DEL` 方 |
|---|---|---|---|
| `task:progress:{uid}` | ⭐ **只有读路径自己回填**（`get_task_logic.go:73-75` HSet+Expire） | 读路径（`get_task_logic.go:49` HGetAll） | 两条 sink（`like_sink.go:329,332-334`、`article_sink.go:422`）—— **写得对**：失效的是读缓存 |
| `task:progress:{uid}:1902` | like_sink（`like_sink.go:214-221` 初始化 5 字段、`:345-351` 置 done） | **无** | 无 |
| `task:progress:190:{uid}:{articleID}` | article_sink（`article_sink.go:295-306` 初始化、`:436-445` 置 done） | **无** | 无 |
| `task:init:{uid}:1902`（90d） | like_sink（`:186-188` SetNX） | **无** | 无 |
| `task:inits:190:{uid}:{articleID}`（24h） | article_sink（`:255-264` SetNX） | **无** | 无 |
| `task:done:{uid}:1902`（90d） | like_sink（`:338-339` SetNX） | **无** | 无 |
| `task:done:190:{uid}`（120d） | article_sink（`:427-428` SetNX） | **无** | 无 |
| `like:total:{uid}`（90d） | like_sink（`:284` Set） | **无** | 无 |
| `article:like:cnt:{articleID}`（120d） | article_sink（`:368-369` Set） | **无** | 无 |
| `stream:task:reward` | reward/producer（`producer.go:20-29` XADD） | **无**（`worker.go` 全注释、`task.go:35` 启动语句也注释） | 无 |

⇒ **除 `task:progress:{uid}` 外，其余 9 个 key 在 Go 侧全部「只写不读」**（`article_sink.go:291` 的注释自己说了其中一个用途："初始化一个记录，方便redisslnsight观测"）。`user_like_count` 表同理：`grep -rn "UserLikeCount" rpc/` 只命中定义与写入，**无任何读方**。

**⇒ Java 侧必须照抄这个"写了没人读"的拓扑吗？** 本文件按原样保留（它们是运维/排障资产，且删掉会改变 Redis 侧可观察状态），但**必须在 §3.2 显式留痕**，不得被后人当作"无用代码"清理。

### `rpc/internal/server/task_service_server.go`（29 行）

`:15-18` `TaskServiceServer{svcCtx; __.UnimplementedTaskServiceServer}`（⚠️ 字段名是 goctl 生成的 `__` 别名残留）、`:20-24` 构造、`:26-29` `GetTask` 转发到 `logic.NewGetTaskLogic(ctx, s.svcCtx).GetTask(in)`。
⇒ Java 侧对应 `service/impl/TaskServiceGrpcImpl`（`MessageRpcServiceImpl.java:31-60` 已证明该形态）。

### `rpc/internal/Init/initKafkaRaw.go`（44 行）

- `:11` 注释：「这个是用来测试哪一步的kafka死掉了」
- `:12-18` `PrintConsumer.Consume` **直接 `return nil`**（唯一的 log 调用 `:16` 也是注释掉的）⇒ **纯 no-op**
- `:20-43` `kq.MustNewQueue({Brokers, Topic: InTopic, Group: GroupKafkaRaw, Consumers: 1, Offset: "latest", Processors: 1}, PrintConsumer{})` + `proc.AddShutdownListener{q.Stop(); cancel()}` + `q.Start()` + `<-ctx.Done()`

⇒ 它的**唯一作用**是：连上 broker、以 group `task.rpc.consumer31` 加入 `task.logs.raw`、证明链路存活、丢弃全部消息。
⚠️ 这**不是**空组件（有真实 consumer group 与外部依赖），但也**不是**业务逻辑。保留还是删除需拍板 —— 见 §12.7 #6。

### `rpc/internal/Init/initGoKa.go`（227 行）—— 本域最大的架构缺口

- `:23-32` `LogEvent{ts, service, event_id, user_id, trace_id, article_id, msg}` —— filebeat 写入的 JSON 结构（`service/task/filebeat.yml:8-20`：`decode_json_fields` + `output.kafka{topic: "task.logs.raw", key: "%{[user_id]}"}`）
- `:34-37` `TaskArticleEvent{user_id string, cur int64}` —— 输出到 `filtered.Article` 的 JSON（与 like 域的 `TaskArticleProgressMsg` **同形**，见 `service/like/rpc/internal/types/types.go`）
- `:39-43` `DLQEvent` —— **零使用**（死类型）
- `:45-58` 包级变量 `topicDLQ = "raw-logs.dlq"`、`countTable = "like_counts"` + 注释「找个时间补一下DLQ」⇒ **零消费点**
- `:60-70` `process`：解析 `LogEvent`（失败只 `log.Println` 不返回错误），然后 `ctx.Emit(Stream(rawTopicTaskUser), logEvent.UserID, raw)` + `ctx.Emit(Stream(rawTopicTaskArticle), logEvent.ArticleID, raw)`
- `:72-82` `processUserCount`：`cur = ctx.Value()+1`（**无 key 则从 0 起**）→ `ctx.Emit(Stream(outTopicTaskUser), ctx.Key(), cur)` —— value 是 **int64**
- `:84-107` `processArticleCount`：同样计数，但 value 是 `TaskArticleEvent{UserID: logEvent.UserID, Cur: cur}` 的 **JSON**
- `:109-159` `StartTaskGoKa`（**group 1**，输入 `task.logs.raw` → 2 个输出流）+ `:147-148` `go startTaskUserGoKa` + `go startTaskArticleGoKa`
- `:161-193` `startTaskUserGoKa`（**group 2**：输入 `task.logs.raw.User` → `goka.Persist(new(codec.Int64))` + 输出 `task.logs.filtered.User`）
- `:195-227` `startTaskArticleCountGoKa`（**group 3**：输入 `task.logs.raw.Article` → `goka.Persist(Int64)` + 输出 `task.logs.filtered.Article`，codec 为 `Bytes`）
- `:131,173,207` 三处 `cfg.Consumer.Offsets.Initial = sarama.OffsetOldest`（⭐ **与 kq consumer 的 `"latest"` 不同**：goka 从最老消息开始读）
- `:134-136,174-176,208-210` 三处 `tmCfg.Stream.Replication = 1` + `Table.Replication = 1`，注释「修复topic复制因子rfactor=2的问题」
- `:143-145,152-153,183-185,217-219` 四处 `log.Fatal` ⇒ 失败即进程退出

🔴 **`goka.Persist` 的语义是「按 group 维护一张本地 KV 表（RocksDB），值随 key 累加」**。这既不在 PG 也不在 Redis，且**没有 TTL、没有过期清理**。⇒ Java 侧必须显式选一个后端，这是本域**无法从 Go 代码自动推导**的部分。UNKNOWN。

### `rpc/internal/Init/initUserFilter.go`（59 行）

`:24` `log.Println("start task kafka filter")`；`:32-33` `consumer := sink.NewUserLikeSinkConsumer(svcCtx.Rdb, svcCtx.Gdb); consumer.Start(ctx)`；`:44-51` `kq.MustNewQueue({Brokers, Topic: OutUserTopic, Group: GroupFilterUser, Consumers:1, Offset:"latest", Processors:1}, consumer)`；`:53-58` shutdown + Start + `<-ctx.Done()`。
`:13-20` 与 `:35-42` 是两段注释掉的旧实现。

### `rpc/internal/Init/initArticleFilter.go`（50 行）

与上同构，差异：`:29` `Topic: OutArticleTopic`、`:30` `Group: GroupKafkaFilter`（⚠️ **不是** `GroupFilterArticle`）、`:32-33` `sink.NewArticleLikeSinkConsumer`。

### `rpc/internal/Init/initReward.go`（21 行）★ 死代码

`:3-21` **全文件被 `/* */` 注释**（含 `reward.NewWorker(rdb, svc.PointsClient, consumerName)` 与被注释的 `reclaimer`）。配合 `task.go:35` 的注释启动语句 ⇒ **reward worker 永不运行**。不迁移。

### `rpc/internal/sink/like_sink.go`（423 行）★ 点赞进度写入

| 行 | 内容 | 语义 |
|---|---|---|
| `:17-25` | `UserLikeCount`（`UserID string` PK）+ `TableName() = "user_like_count"` | 与 `Model.UserLikeCount` 重复 |
| `:27-29` | `func (UserTaskProgress) TableName() string { return "task_progress" }` | 方法挂在**另一个**类型上（goctl 残留），仍生效 |
| `:31-34` | `const taskID = int64(1902); target = 5` | ⭐ 与 `AllTaskDefs` 的 `{1902, "点赞任务", …, 5}` 一致 |
| `:44-57` | 结构体：`rdb`,`gdb`,`mu`,`delta map[int64]int64`,`flushEvery=1s`,`redisPipeMax=5000`,`pgBatchMax=2000`,`rewardProduct`,`flushCh chan struct{}`（**容量 1，但全仓无任何 `c.flushCh <- struct{}{}` 写入** ⇒ 死字段） | 常量全部硬编码，**不从配置读** |
| `:64-75` | `NewUserLikeSinkConsumer`：`delta` 预分配 `1<<16`；`rewardProduct: reward.NewProduct(rdb)` | |
| `:77-79` | `Start(ctx) { go c.loop(ctx) }` | |
| `:81-108` | `Consume`：`TraceConsumer("task-rpc","LikeSinkConsumer.Consume", taskMessageAttrs("user_like_sink", key), …)`；`:83-86` key `ParseInt` 失败 **返回 error（会重试）**；`:87-89` `userID <= 0` → `return nil`（丢弃）；`:91-96` `val` 空则 `total=1`，否则 `ParseInt` 失败**也当 1**；`:97-99` `total <= 0` → 丢弃；`:101-105` ⭐ **max-wins**：`if !ok \|\| total > current { delta[uid] = total }` —— **只增不减，且不因更小的值回退** | 语义：**累计点赞数的最新值** |
| `:110-126` | `loop`：1s ticker；`ctx.Done()` 时**先 `flushOnce(context.Background())` 再 return** | 关停不丢数据 |
| `:128-145` | `flushOnce`：`swap()` → 空则 nil → `TraceConsumer(…,"LikeSinkConsumer.Flush", taskFlushAttrs(...))` → 顺序 `lazyInitTaskIfNeeded` → `flushRedis` → `flushPostgres` | **三步有序，任一步失败即 return err（数据留在本次 batch 中被丢弃 —— 无重试）** |
| `:147-154` | `taskMessageAttrs`：4 个 messaging.* attribute | |
| `:165-241` | `lazyInitTaskIfNeeded`：`SetNX "task:init:{uid}:1902" "1" 90d`（分批 5000）→ 收集 `ok==true` 的新用户 → `HSet "task:progress:{uid}:1902" {status:"doing",progress:0,target:5,createAt:now,updateAt:now}` → `Create(&records)` 带 `OnConflict{Columns:[user_id,task_id], DoNothing:true}` | ⭐ **只在 SetNX 首次成功时插 DB 行** ⇒ 已存在的行不会被刷新 |
| `:243-254` | `swap`：取出并**清空** `delta` | |
| `:256-293` | `flushRedis`：`Set "like:total:{uid}" total 90d`（分批 5000）；每批 `Exec` **成功后**才遍历 batch 调 `completeLikeGT5`（`total >= 5`）或 `updateDoingTaskProgress` | ⭐ **进度推进发生在 pipeline Exec 成功之后**；失败只 `return err` |
| `:295-326` | `updateDoingTaskProgress`：`progress` 夹到 `[0,5]`；`Create` 带 `OnConflict{Columns:[user_id,task_id], DoUpdates:{status: CASE WHEN task_progress.status='done' THEN task_progress.status ELSE EXCLUDED.status END, progress: GREATEST(task_progress.progress, EXCLUDED.progress), target: 5, updated_at: now()}}`；成功后 `invalidateTaskCache` | ⭐⭐ **单调 + done 不可回退**：两条 DB 级不变量 |
| `:328-334` | `invalidateTaskCache` = `DEL "task:progress:{uid}"` | ⭐ **写路径确实失效读缓存** |
| `:336-364` | `completeLikeGT5`：`:339-342` `SetNX "task:done:{uid}:1902"`（90d），`!ok` 直接 return；`:346-351` `HSet "task:progress:{uid}:1902" {status:"done",doneAt,progress:5,updateAt}`；`:355-358` `reward.NewEvent(uid, taskID, 5)` + `Enqueue`（失败即 return err，**后续 DB 更新不做**）；`:359-362` `markTaskDoneInDB`（失败 return err）；`:363` invalidate | 注释明写「这里建议记录日志即可，不要让整个 sink 挂掉」 |
| `:366-385` | `markTaskDoneInDB`：`Create` + `OnConflict{DoUpdates:{status:"done",progress:5,target:5,done_at:COALESCE(task_progress.done_at, now()),updated_at:now()}}` | ⭐ `done_at` **只写一次**（COALESCE） |
| `:387-404` | `flushPostgres`：把 batch 转成 `row` 列表，按 `pgBatchMax=2000` 分块 `upsertChunk` | |
| `:406-422` | `upsertChunk`：`Create(&records)` + `OnConflict{Columns:[user_id], DoUpdates:{like_count: GREATEST(user_like_count.like_count, EXCLUDED.like_count), updated_at: now()}}` | **单调** |

### `rpc/internal/sink/article_sink.go`（491 行）★ 文章获赞进度写入

| 行 | 内容 | 语义 |
|---|---|---|
| `:18-31` | 常量：`articleTaskID=190`、`articleTarget=3`、`articleDoneTTL=90d+30d=120d`、`articleInitTTL=24h`、`articleFlushEvery=1s`、`redisPipeMaxDefault=5000`、`pgBatchMaxDefault=2000`、`reconZKeyPrefix="task:recon:active:"`、`ownerHKey="article:owner"` | 后两个 ⭐ **只在死代码 `markReconCandidates`（`:195-228`）里用**，而它的调用点在 `:169` 是注释 |
| `:33-49` | `ArticleLikeTaskProgress`（**`ArticleID` 注释掉 `:36`**；`DoneAt/CreatedAt/UpdatedAt` 注释掉 `:42-44`）+ `TableName() = "task_progress"` | |
| `:66-74` | `articleAgg{UserID, total}` + `articleVal{user_id string, cur int64}` | |
| `:76-87` | `NewArticleLikeSinkConsumer`（`rewardProduct: reward.NewProduct(rdb)`） | |
| `:93-124` | `Consume`：`:95-97` key 空丢弃；`:98-101` `ParseInt` 失败 → **return err（重试）**；`:102-105` `parseVal` 失败/`userID==0`/`d==0` → 丢弃；`:106-121` ⭐ **max-wins，但按 articleID 聚合**，且 `d > cur.total` 时**同时更新 UserID** | |
| `:126-139` | `parseVal`：`v.Cur == 0` 时**归一为 1**（`:132-134`）；`UserID` `ParseInt` 失败当 0 | |
| `:141-156` | `loop`（与 like 版逐字同构） | |
| `:158-181` | `flushOnce`：`lazyInitTaskIfNeeded` → ⭐ `FlushRedis`（失败 `logx.Errorf` + return err）→ ⭐ **`FlushPostgres` 的调用被注释掉（`:176-178`）** | 🔴 **article sink 不写 PG 的"普通进度更新"以外的任何东西**；它只在 `completeArticleLike` 里写 done 行 |
| `:195-228` | `markReconCandidates` —— **死代码**（唯一调用点 `:169` 被注释） | 不迁移 |
| `:230-328` | `lazyInitTaskIfNeeded`：`SetNX "task:inits:190:{uid}:{articleID}" "1" 24h` → `HSet "task:progress:190:{uid}:{articleID}" {status:"doing",progress:0,target:3,createAt,updateAt}` → `Create` + `OnConflict{DoNothing}` | ⭐ **init key 的 TTL 只有 24h，而 like 版是 90d** ⇒ 每 24h 会重复触发一次初始化 |
| `:330-383` | `FlushRedis`：`Set "article:like:cnt:{articleID}" total 120d`（分批 5000）→ 成功后遍历：`total >= 3` → `completeArticleLike`，否则 `updateDoingTaskProgress` | |
| `:385-419` | `updateDoingTaskProgress`：**SQL 与 like 版逐字相同**（只有常量不同） | |
| `:421-423` | `invalidateTaskCache` = `DEL "task:progress:{userID}"` | |
| `:425-456` | `completeArticleLike`：`:428-431` `SetNX "task:done:190:{uid}" 120d`；`:436-445` `HSet "task:progress:190:{uid}:{articleID}" {status:"done",doneAt,progress:3,updateAt}`；`:447-451` ⭐ **`reward.NewEvent(strconv.Itoa(int(userID)), strconv.Itoa(articleTaskID), 5)` —— 奖励分值是 5，而 target 是 3**；`:452-454` `markTaskDoneInDB`；`:455` invalidate | |
| `:458-460` | `FlushPostgres` —— **整段注释** | 死代码 |
| `:462-491` | `markTaskDoneInDB`：SQL 与 like 版同形（`done_at` COALESCE、评论掉了 articleID 相关的 on-conflict 列） | |

### `rpc/internal/reward/type.go`（31 行）

- `:8-11` `StreamKey = "stream:task:reward"`、`GroupName = "task-reward-group"`
- `:13-19` `RedisEvent{RewardID int64 //幂等ID, UID, TaskID, Ts, AddScore}`
- `:21-31` ⭐ `NewEvent(_uid, _taskID string, addScore int64)`：`RewardID: uid + taskID` —— ⭐⭐ **是算术加法，不是字符串拼接**。`uid=23, taskID=1902 → 1925`；`uid=1902, taskID=23 → 1925` —— **天然碰撞**。注释把它标成「幂等ID」但它并不幂等。这是 Go 侧真实行为（`like_sink.go:355` 与 `article_sink.go:447` 都经它），必须原样保留并标注

### `rpc/internal/reward/producer.go`（31 行）★ 活的

- `:9-17` `Product{rdb}` + `NewProduct(rdb)`
- `:19-31` `Enqueue(ctx, ev)`：`rdb.XAdd(StreamKey, Values: {reward_id, uid, task_id, ts, score})`
- ⭐ 写入的字段名是 **`score`**（`:27`），而消费端 `worker.go:120` 读的是 **`add_score`** —— 字段名不匹配，但 `worker.go` 全注释 ⇒ 该不匹配不可观察

### `rpc/internal/reward/worker.go`（123 行）★ 死代码

`:3-123` **全文件注释**（`XGroupCreateMkStream` + `XReadGroup` + `UserClient.GetUser` + `points.AddPoints` + `XAck` + `XDel`）。
⇒ 结论：`stream:task:reward` 被写入但**没有任何消费者组**（`EnsureGroup` 只在被注释的代码里）⇒ **stream 无界增长**。这是 Go 的现状，Java 侧照抄 or 修正需拍板 —— UNKNOWN。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `task.go:23-48` 进程装配 | `TaskRpcApplication` | ABSORB | Spring Boot 启动（`LikeRpcApplication.java:58-79`） |
| `task.go:44` unary interceptor | `common/spring/RpcServerInterceptorFactory` + `GrpcServerConfigurer.intercept(...)` | ADAPT | ⚠️ `@Component` ≠ 自动注册（`RpcServerInterceptorFactory.java:18-23`）；必须显式 `@Import` + 显式 `intercept()` |
| `task.go:28` + `zrpc/config.go:46` 默认 `Timeout=2000` | `application.yml` `observability.rpc-timeout: 2s` | ADAPT | 同 `message-rpc/application.yml:142-144` |
| `task.go:40-42` Dev/Test 反射 | devh starter 默认行为 | ABSORB | `message-rpc/application.yml:75` 已记录同款结论 |
| `task.go:31-34` 4 个 goroutine | Spring 容器管理的 listener container + `@Scheduled` | ABSORB | 生命周期由容器统一（同 `like/rpc.md` 的 P2 处理） |
| `task.go:35` + `initReward.go` 注释启动 | — | — | **死代码，不迁移**（非 ABSORB） |
| `etc/task.yaml:1-6` Etcd 注册 | `bootstrap/NacosRegistration` | ADAPT | `message-rpc`/`user-rpc` 均有同款 |
| `etc/task.yaml:14-21` 6 个 topic | `config/TaskKafkaTopics` | NEW | topic 名是**跨服务 wire 契约**（like 域生产其中 2 个，`like/rpc/etc/like.yaml:92,99`），必须单点定义（先例 `like-rpc/config/LikeKafkaTopics.java`） |
| `etc/task.yaml:23-32` 7 个 consumer group | `config/TaskKafkaTopics` | SPLIT | 6 个实际使用 + 1 个零使用（`GroupFilterArticle`），后者不迁并留注 |
| `etc/task.yaml:34-36` `Offset`/`Consumers`/`Processors` | — | — | **被硬编码覆盖**（`initKafkaRaw.go:31-33` 等），Java 侧用 listener 并发度 1 表达；`Processors` 连结构体字段都没有 |
| `etc/task.yaml:38-41` `LikeRedis{Addr,Pass,DB}` | `application.yml` `spring.data.redis.*` | ADAPT | 段名 `LikeRedis` 是历史遗留，语义是本域的 Redis |
| `etc/task.yaml:42-46` Redis 超时/池 | — | — | **结构体无对应字段，不迁** |
| `etc/task.yaml:47` `consumerName` | — | — | 只被死代码 `reward` 用 |
| `etc/task.yaml:51-52` `MaxOpenConns/MaxIdleConns=50` | `application.yml` Hikari `maximum-pool-size:50 / minimum-idle:50` | ADAPT | `service_context.go:43-44` 真实消费；先例 `message-rpc/application.yml:21-22` |
| `etc/task.yaml:53` `ConnMaxLifetimeMinutes:30` | `application.yml` Hikari `max-lifetime` | **UNKNOWN** | Go 是 bug（`:45` 漏 `time.Minute` ⇒ 30ns）。配 30m 是修正，需拍板 + §3.2 留痕 |
| `etc/task.yaml:50` DSN `TimeZone=Asia/Shanghai` | `spring.datasource.url` 保留同一参数 | ADAPT | `task_progress` 的时间列语义依赖它 |
| `config/config.go:9-10` `PointsRpc`/`UserRpc` | **不建 `client/` 包** | — | 死配置（`service_context.go` 五处注释） |
| `service_context.go:25-29` `redis.NewClient` | `StringRedisTemplate`（Boot 自动配置） | ABSORB | 依赖见 `like-rpc/pom.xml:104,126` |
| `service_context.go:34-42` gorm + 两处 `panic` | Spring `spring.datasource` 初始连接失败 | ADAPT | 失败即启动失败，语义等价 |
| `service_context.go:47-49` `AutoMigrate(2 表)` | Flyway `V1__create_task_progress.sql` + `V2__create_user_like_count.sql` | ADAPT | rpc 模块既有惯例（message/like/article 三家）；⚠️ DDL 需按 GORM 映射反推 —— UNKNOWN（需与 Go 真 PG 对拍） |
| `taskModel.go:6-14` 注释的 `TaskDef` / `task_def` 表 | **不建 task_def 表、不建 TaskDef Entity** | — | 全仓不存在该表 |
| `taskModel.go:22-25` `AllTaskDefs`（2 条字面量） | `service/TaskDefs` | NEW | ⭐ 它是任务定义的**唯一真源**；且 `articleTaskID/target`（`article_sink.go:20-21`）与 `taskID/target`（`like_sink.go:32-33`）在 Go 侧是**三处重复的常量**，必须收口 |
| `taskModel.go:28-41` `TaskProgress` | `domain/TaskProgressEntity` | ADAPT | ⭐ 收敛 3 份重复 GORM 结构体为 1 个（`taskModel.go:28-37` / `like_sink.go:36-42` / `article_sink.go:33-45`） |
| `taskModel.go:43-50` `UserLikeCount`（`user_id` 是 string） | `domain/UserLikeCountEntity` | ADAPT | ⭐ `user_id` 是 **String** 而非 Long —— 与 `task_progress.user_id` 类型不同 |
| `like_sink.go:17-25` 重复的 `UserLikeCount` | 同上 | MERGE | 同一张表的重复定义 |
| `get_task_logic.go:43-45` `uid <= 0` → `codes.InvalidArgument` | `service/impl/TaskServiceGrpcImpl` 内 `Status.INVALID_ARGUMENT.withDescription("user_id invalid").asRuntimeException()` | ADAPT | ⭐ Go 用的是 **gRPC 标准码**，不是业务码 ⇒ **不建 `exception/` 包、不建 errmsg** |
| `get_task_logic.go:48-49` Redis `HGetAll` | `service/TaskDomainService` + `StringRedisTemplate.opsForHash().entries(key)` | ADAPT | `LikeDomainService.java:767-786` 同款 |
| `get_task_logic.go:51` Redis 错/未命中同路径回源 | `TaskDomainService` 的降级分支 | KEEP | L2 语义：Redis 错**不**返回错误 |
| `get_task_logic.go:53-57` PG 单用户 5 列查询 | `mapper/TaskProgressMapper.selectByUserId` | ADAPT | `@Select` 注解式原生 SQL；`<>` 无、`IN` 无 ⇒ 无 CDATA 需求 |
| `get_task_logic.go:58-60` DB 错原样返回 | `TaskServiceGrpcImpl` 兜底 `Status.INTERNAL` | ADAPT | Go 由 grpc 框架包成 `Unknown`；Java 侧须显式 `INTERNAL`（`MessageRpcServiceImpl.java:41-44` 同款） |
| `get_task_logic.go:73-76` pipeline `HSet`+`Expire`，失败忽略 | `redis.executePipelined(SessionCallback)` | ADAPT | ⭐ 唯一先例 `hot-rpc/mq/HotRedisSnapshotStore.java:143-152` |
| `get_task_logic.go:78` 空结果分支 `Expire 30s` | `TaskDomainService` 内同样对不存在 key 发 `EXPIRE` | KEEP | ⭐ 它是**无效操作**（不建 key），但必须照抄（保持可观察状态一致） |
| `get_task_logic.go:83-101` 遍历 `AllTaskDefs` 合并 | `service/TaskDomainService` + `service/TaskDefs` | MERGE | 同一变化原因（读模型） |
| `get_task_logic.go:86-87` `required` 取自 defs 不用 target | `TaskDefs` | KEEP | L2：DB 的 `target` 列永不进响应 |
| `get_task_logic.go:90` 丢弃 `status`/`target` | `TaskDomainService`（只取 progress） | KEEP | proto 无对应字段 |
| `get_task_logic.go:110-112` 15 分钟 TTL | `TaskDomainService` 常量 | KEEP | L3 |
| `get_task_logic.go:114-129` `pack`/`unpack`（`\|` 分隔、必须恰好 3 段） | `TaskDomainService` 私有方法 | MERGE | ⭐ **单一消费者**（只有读路径回填会用 pack），不值得单列（§14.4） |
| `get_task_logic.go:131-139` `parseInt64Default` | 同上（私有） | MERGE | 同上 |
| `service_context.go` 的 key 构造（散落 4 文件共 12 个模板） | `service/TaskRedisKeys` | NEW | ⭐ 跨 4 个 Go 文件的 key 契约集中定义；命名相近但**语义不同**（见 §12.3 key 拓扑），散落极易写错 |
| `server/task_service_server.go:26-29` | `service/impl/TaskServiceGrpcImpl` | ADAPT | 分发由 starter 完成 |
| `taskservice/task_service.go` | protobuf-maven-plugin 生成的 stub | ABSORB | 生成物，实测已在 `task-proto/target/generated-sources/` |
| `initKafkaRaw.go:20-43` no-op 探针 consumer | `mq/TaskKafkaLivenessProbe` | **UNKNOWN** | 有真实 consumer group 与外部依赖（不是空组件），但业务价值为零。保留 or 删除需拍板 |
| `initGoKa.go:23-32` `LogEvent` | `mq/TaskLogEvent` | NEW | filebeat 写、Kafka 传的 JSON 契约（`service/task/filebeat.yml:8-20`） |
| `initGoKa.go:34-37` `TaskArticleEvent` | `mq/TaskArticleProgressEvent` | NEW | 生产者/消费者共享契约（消费者 = 本域 article sink） |
| `initGoKa.go:39-43` `DLQEvent` / `:45-58` `topicDLQ`/`countTable` | — | — | 零使用死代码，不迁 |
| `initGoKa.go:60-70` `process` 扇出 | `mq/TaskRawLogRouter` | NEW | 1 个 topic → 2 个 topic 的无状态扇出 |
| `initGoKa.go:72-82` `processUserCount` | `mq/TaskUserCountConsumer` | NEW | 按 key 累加 + 输出 int64 |
| `initGoKa.go:84-107` `processArticleCount` | `mq/TaskArticleCountConsumer` | NEW | 按 key 累加 + 输出 JSON |
| `initGoKa.go:167,201` `goka.Persist(Int64)` | `mq/TaskCountStore` | **UNKNOWN** | 🔴 **本域最大缺口**：goka 的本地表存储无 Java 对应物，后端选型需拍板 |
| `initGoKa.go:131,173,207` `OffsetOldest` | `spring.kafka.consumer.auto-offset-reset: earliest`（仅这 3 个 listener） | ADAPT | 与 kq consumer 的 `"latest"` 不同 ⇒ 分开配 |
| `initGoKa.go:134-136` `Replication = 1` | 部署侧（broker 配置） | — | 不在应用代码内 |
| `initGoKa.go:143-145,152-153` 四处 `log.Fatal` | listener 容器启动失败即失败 | ADAPT | 语义等价 |
| `initUserFilter.go:22-58` sink + kq | `mq/UserLikeProgressSink`（`@KafkaListener`） | ADAPT | `like/rpc.md` 已论证 `mq/` 是合法新增包（§3.3 规则 8） |
| `initArticleFilter.go:22-49` sink + kq | `mq/ArticleLikeProgressSink`（`@KafkaListener`） | ADAPT | 同上 |
| `like_sink.go:81-108` `Consume`（max-wins + 三态丢弃） | `mq/UserLikeProgressSink.consume` | KEEP | 独立错误契约（key 解析失败→重试 / uid<=0→丢弃 / total<=0→丢弃） |
| `like_sink.go:110-126` 1s loop + 关停 flush | `@Scheduled(fixedRate=1000)` + `@PreDestroy` flush | ADAPT | `HotMergeScheduler.java:147-148` 先例；`LikeRpcApplication.java:61` 需 `@EnableScheduling` |
| `like_sink.go:128-145` 三步有序 flush | 两个 sink 各自实现 | KEEP | 错误契约与 article 版不同（article 的 `FlushPostgres` 被注释） |
| `like_sink.go:165-241` `lazyInitTaskIfNeeded` | `service/TaskProgressWriter.lazyInit` | SPLIT | 与 Kafka 入口分离（可独立测），与 `article_sink.go:230-328` 共享 |
| `like_sink.go:243-254` `swap` | 两个 sink 各自的私有 `ConcurrentHashMap` 清空 | KEEP | 无需独立文件 |
| `like_sink.go:256-293` `flushRedis` + `:284` `like:total:` | `service/TaskRedisKeys` + sink | ADAPT | key 由 `TaskRedisKeys` 提供 |
| `like_sink.go:295-326` / `article_sink.go:385-419` 两条**逐字相同**的 upsert | `service/TaskProgressWriter.upsertDoing` | **MERGE** | ⭐ 两段 SQL 只有常量不同（`taskID`/`target`），同一变化原因、同一依赖边界 |
| `like_sink.go:366-385` / `article_sink.go:462-491` 两条同形的 done upsert | `service/TaskProgressWriter.upsertDone` | **MERGE** | 同上 |
| `like_sink.go:387-404,406-422` `flushPostgres` + `upsertChunk` | `service/TaskProgressWriter.upsertLikeCounts` + `mapper/UserLikeCountMapper` | SPLIT | 分块 2000 的批量 upsert 独立成 mapper 方法 |
| `like_sink.go:82,133` / `article_sink.go:94,163` `TraceConsumer` | `common/observability/MqConsumer.traceConsumer` | REUSE | 4 个调用点 |
| `reward/type.go:8-11` `StreamKey` | `service/TaskRewardPublisher` 常量 | MERGE | 唯一使用者 |
| `reward/type.go:13-19,21-31` `RedisEvent` + `NewEvent`（`reward_id = uid + taskID`） | `service/TaskRewardPublisher`（私有 record + 构造） | MERGE | ⭐ **加法幂等 ID 必须逐字保留**，单消费者不值得独立文件 |
| `reward/producer.go:19-31` `XAdd` | `service/TaskRewardPublisher.enqueue` | ADAPT | ⚠️ Spring Data Redis 的 `opsForStream().add(...)` 全仓零先例 ⇒ UNKNOWN |
| `reward/worker.go` 全注释 / `initReward.go` 全注释 | **不迁移** | — | 死代码。后果：`stream:task:reward` 无消费者组、无界增长 —— 保留 or 修正需拍板 |
| 本域**零业务错误码** | **不建 `exception/` 包、不建 errmsg** | — | §12.1 末第 3 条 |
| 本域**零 metrics** | **不建 `metrics/` 包** | — | Go 侧 `service/task/rpc/` 全树无 metric 定义（`like/rpc.md` §12.3 列了 like 的 `internal/metrics`，task 没有） |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/task/task/rpc/
├── TaskRpcApplication.java                # @SpringBootApplication + @ConfigurationPropertiesScan + @MapperScan
│                                          #   + @EnableScheduling + @Import×6 + GrpcServerConfigurer.intercept(...)
├── bootstrap/
│   └── NacosRegistration.java             # 服务注册（Go: zrpc.MustNewServer 的 Etcd 注册）
├── config/
│   └── TaskKafkaTopics.java               # 6 topic + 6 group + 2 TTL 常量（跨服务 wire 契约单点定义）
├── domain/
│   ├── TaskProgressEntity.java            # @TableName("task_progress")；复合主键 (user_id, task_id)
│   └── UserLikeCountEntity.java           # @TableName("user_like_count")；user_id 是 String！
├── mapper/
│   ├── TaskProgressMapper.java            # selectByUserId + upsertDoing + upsertDone（ON CONFLICT 单条 SQL）
│   └── UserLikeCountMapper.java           # upsertLikeCounts（GREATEST 单调）
├── service/
│   ├── TaskDefs.java                      # 任务定义单点（taskId 190/1902 + name + desc + requiredProgress）
│   ├── TaskRedisKeys.java                 # 10 个 key 模板的唯一定义处
│   ├── TaskDomainService.java             # getTask：Redis 读穿 → PG 回源 → 回填 → 与 TaskDefs 合并
│   ├── TaskProgressWriter.java            # 两条 sink 共享的 lazyInit / upsertDoing / upsertDone
│   ├── TaskRewardPublisher.java           # Redis Stream XADD（reward_id = uid + taskID）
│   └── impl/
│       └── TaskServiceGrpcImpl.java       # @GrpcService，1 个 override
├── mq/
│   ├── TaskLogEvent.java                  # filebeat → task.logs.raw 的 JSON 契约
│   ├── TaskArticleProgressEvent.java      # {user_id, cur} JSON 契约
│   ├── TaskCountStore.java                # ⚠️ 替代 goka.Persist 的计数存储（后端 UNKNOWN）
│   ├── TaskRawLogRouter.java              # task.logs.raw → raw.User + raw.Article 扇出
│   ├── TaskUserCountConsumer.java         # raw.User → filtered.User（裸计数）
│   ├── TaskArticleCountConsumer.java      # raw.Article → filtered.Article（JSON）
│   ├── UserLikeProgressSink.java          # filtered.User → task_progress(1902) + user_like_count
│   └── ArticleLikeProgressSink.java       # filtered.Article → task_progress(190)
└── resources/
    ├── application.yml                    # 池 50/50、grpc.server.port、kafka 6 topic、redis、otel、rpc-timeout 2s
    └── db/migration/
        ├── V1__create_task_progress.sql
        └── V2__create_user_like_count.sql
```

**共 21 个 Java 文件 + 3 个资源文件。**

### 新增包说明（§3.3 规则 8）

`mq/` 是**新增包**，理由（与 `docs/FileTree/like/rpc.md` 的同款论证，但本域的 8 个 Kafka 入口更极端）：

1. **职责无法归入现有包** —— `service/` 承载被 gRPC 调用的用例与持久化编排；`service/impl/` 承载 gRPC 接口实现。本域有 **5 个业务相关的 Kafka 入口**（router / 2 个计数 / 2 条 sink）+ 1 个探针，它们**既非 HTTP 也非 gRPC 入口**，且各自有**独立的错误契约**（`like_sink.go:84` 的 key 解析失败必须重试 vs `:87-89` 的 uid<=0 必须丢弃；`article_sink.go:99-101` 的 `ParseInt` 失败返回 err vs `:95-97` 的空 key 静默丢弃 —— **同一份 `articleVal` JSON 解析失败反而是丢弃**）、独立的幂等键空间、独立的生命周期。
2. **目标项目实际依据** —— RAG 已有 `like-rpc/mq/`（5 个类）与 `article-rpc/mq/`（4 个类）两套先例，`hot-rpc/mq/` 第三套。
3. **不因 Go 有同名文件而新增** —— Go 的目录名是 `Init` 与 `sink`（两处都是 Go 风格名），此处是 `mq`；不沿用 `handler/logic/repository/middleware/servicecontext` 等任何 Go 风格目录。

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 | 依赖 |
|---|---|---|---|---|
| `TaskRpcApplication` | 入口；`@ConfigurationPropertiesScan("com.rag.task.task.rpc.config")` + `@MapperScan("…rpc.mapper")` + `@EnableScheduling` + `@Import{RpcServerInterceptorFactory, ObservabilityProperties, DeadlineQueryTimeoutInjector, SqlTimeoutStatusTranslator, Jackson2ObjectMapperConfiguration, LoggerFactoryBean}` + `GrpcServerConfigurer` 显式 `intercept(...)` | `task.go:23-48` | 框架约定 | `task-proto`、`common` |
| `bootstrap/NacosRegistration` | 服务注册 | `task.go:37` 的 Etcd 注册 | 与 `message-rpc`/`user-rpc` 同款 | — |
| `config/TaskKafkaTopics` | 6 个 topic 名 + 6 个 consumer group + `TASK_PROGRESS_TTL=15m` + 两个 sink 的 batch 常量 | `task.yaml:14-32` + 散落的硬编码常量 | topic/group 名是**跨服务契约**（like 域生产其中 2 个） | — |
| `domain/TaskProgressEntity` | `@TableName("task_progress")`；`user_id`/`task_id` **复合主键**；`done_at` 为 nullable 时间列 | `taskModel.go:28-37` | 收敛 3 份重复 GORM 结构体 | — |
| `domain/UserLikeCountEntity` | `@TableName("user_like_count")`；**`user_id` 是 `String`** | `taskModel.go:43-50` + `like_sink.go:17-25` | 类型与上表不同 | — |
| `mapper/TaskProgressMapper` | `selectByUserId(long)`（5 列）；`upsertDoing`（`CASE WHEN status='done' …` + `GREATEST` + `now()`）；`upsertDone`（`done_at = COALESCE(…, now())`） | `get_task_logic.go:53-57`、`like_sink.go:311-321,375-384`、`article_sink.go:401-416,477-490` | ⭐ 三条 upsert 语义各不相同且都是**并发正确性的核心**，必须落在能直接审 SQL 的位置（AGENTS.md §3.3） | — |
| `mapper/UserLikeCountMapper` | 批量 upsert（`GREATEST(user_like_count.like_count, EXCLUDED.like_count)`） | `like_sink.go:406-422` | 单调约束必须在 SQL 里 | — |
| `service/TaskDefs` | 2 条任务定义的**唯一真源**（`taskId`/`name`/`desc`/`requiredProgress`），并被读模型与两条 sink 的常量共用 | `taskModel.go:22-25` + `like_sink.go:32-33` + `article_sink.go:20-21` | ⭐ Go 侧把同一组常量写在 3 个文件里，改一处忘另一处就会静默错位 | — |
| `service/TaskRedisKeys` | 10 个 key 模板（读缓存 / per-task 观测 / init 幂等 / done 幂等 / 计数器 / owner） | `get_task_logic.go:107`、`like_sink.go:186,214,284,333,338`、`article_sink.go:196,216,255,295,368,427` | ⭐ 命名高度相似但**语义不同**（`task:progress:{uid}` vs `task:progress:{uid}:1902` vs `task:progress:190:{uid}:{aid}`），散落 4 文件极易写错 | — |
| `service/TaskDomainService` | `getTask(long uid)`：Redis `HGetAll` → miss/err 则 PG 回源 → pipeline `HSet`+`Expire` 回填 → 遍历 `TaskDefs` 合并；私有 `packProgress`/`unpackProgress`/`parseInt64Default` | `get_task_logic.go:40-139` | 本域唯一的 gRPC 业务用例 | `mapper/TaskProgressMapper`、`TaskRedisKeys`、`TaskDefs` |
| `service/TaskProgressWriter` | `lazyInit(taskId, target, uids/rows)` + `upsertDoing(...)` + `upsertDone(...)` + `invalidateReadCache(uid)` | `like_sink.go:165-241,295-334,366-385` + `article_sink.go:230-328,385-423,462-491` | ⭐ 两条 sink 的 SQL 与幂等键**逐字相同**（仅常量不同），合并后杜绝"改一处漏一处" | `mapper/*Mapper`、`TaskRedisKeys` |
| `service/TaskRewardPublisher` | `enqueue(uid, taskId, addScore)`：`XADD stream:task:reward {reward_id, uid, task_id, ts, score}`，`reward_id = uid + taskID`（**算术加法**） | `reward/type.go:8-31`、`reward/producer.go:19-31` | ⭐ 出网副作用可独立降级；且 `reward_id` 的加法语义必须单点可见 | `StringRedisTemplate`（⚠️ `opsForStream` 全仓零先例） |
| `service/impl/TaskServiceGrpcImpl` | `@GrpcService`；`getTask`：`uid <= 0` → `Status.INVALID_ARGUMENT.withDescription("user_id invalid")`；其余异常兜底 `INTERNAL` | `server/task_service_server.go:26-29`、`get_task_logic.go:43-45` | gRPC 契约边界 | `TaskDomainService` |
| `mq/TaskLogEvent` | `{ts, service, event_id, user_id, trace_id, article_id, msg}` | `initGoKa.go:23-32` + `filebeat.yml:8-20` | 跨进程 JSON 契约单点定义 | — |
| `mq/TaskArticleProgressEvent` | `{user_id, cur}` | `initGoKa.go:34-37` | 同上；且与 like 域的 `TaskArticleProgressMsg` 同形 | — |
| `mq/TaskCountStore` | 按 (stream, key) 维护单调计数；**替代 `goka.Persist`** | `initGoKa.go:167,201` | ⭐ 本域唯一的架构缺口，需要独立接口以便后端可替换（§12.7 #1） | ⚠️ 后端 UNKNOWN |
| `mq/TaskRawLogRouter` | 消费 `task.logs.raw` → 解析 `LogEvent` → 产出到 `raw.User`(key=user_id) 与 `raw.Article`(key=article_id) | `initGoKa.go:60-70` | 无状态扇出，与计数/落库分离 | `TaskLogEvent`、`TaskKafkaTopics` |
| `mq/TaskUserCountConsumer` | 消费 `raw.User` → 计数 +1 → 产出 `filtered.User`（value=裸计数） | `initGoKa.go:72-82` | 与 article 版输出编码不同 | `TaskCountStore` |
| `mq/TaskArticleCountConsumer` | 消费 `raw.Article` → 解析 `LogEvent` 取 `user_id` → 计数 +1 → 产出 `filtered.Article`（value=JSON） | `initGoKa.go:84-107` | 同上 | `TaskCountStore`、`TaskArticleProgressEvent` |
| `mq/UserLikeProgressSink` | 消费 `filtered.User` → max-wins delta → `@Scheduled(1s)` 三步 flush → `TaskProgressWriter` + `TaskRewardPublisher`；`@PreDestroy` 最后一次 flush | `initUserFilter.go:22-58` + `like_sink.go:77-423` | 独立的错误契约（重试 vs 丢弃三条分支） | `TaskProgressWriter`、`TaskRewardPublisher` |
| `mq/ArticleLikeProgressSink` | 消费 `filtered.Article` → 按 articleID max-wins → `@Scheduled(1s)` → `TaskProgressWriter` + `TaskRewardPublisher`（**addScore=5 而 target=3**） | `initArticleFilter.go:22-49` + `article_sink.go:89-491` | 与 like 版错误契约不同（`parseVal` 失败是**丢弃**不是重试；`FlushPostgres` 在 Go 侧是死代码） | 同上 |
| `resources/db/migration/V1,V2` | 2 张表的 DDL | `service_context.go:47-49` 的 `AutoMigrate` 目标 | rpc 模块既有惯例 | ⚠️ DDL 需按 GORM 映射反推并与 Go 真 PG 对拍（UNKNOWN） |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `rpc/task.go` | `TaskRpcApplication` | ABSORB | 进程装配由框架承担 |
| `rpc/task.go:44` unary interceptor | `common/spring/RpcServerInterceptorFactory` + 显式 `intercept()` | ADAPT | ⚠️ 不 `@Import` + 不 `intercept()` 就静默失效 |
| `rpc/task.go:28` + `zrpc/config.go:46` 的 2000ms 默认 | `application.yml` `observability.rpc-timeout: 2s` | ADAPT | tag 默认推出的值，Java 侧必须显式写 |
| `rpc/task.go:40-42` reflection | devh starter 默认 | ABSORB | — |
| `rpc/task.go:31-34` 4 个 goroutine | listener container + `@Scheduled` | ABSORB | 容器生命周期统一管理 |
| `rpc/task.go:35` | — | — | 注释掉的启动语句，不迁 |
| `rpc/etc/task.yaml:1-6`（Name/Etcd） | `spring.application.name` + `bootstrap/NacosRegistration` | ADAPT | — |
| `rpc/etc/task.yaml:14-21` 6 topic | `config/TaskKafkaTopics` | NEW | 跨服务 wire 契约 |
| `rpc/etc/task.yaml:23-32` 7 group | `config/TaskKafkaTopics`（6 个） | SPLIT | `GroupFilterArticle` 零使用，不迁并留注 |
| `rpc/etc/task.yaml:34-36` Offset/Consumers/Processors | — | — | 被硬编码覆盖；`Processors` 连字段都没有 |
| `rpc/etc/task.yaml:38-41` LikeRedis | `spring.data.redis.*` | ADAPT | 段名历史遗留，语义是本域 Redis |
| `rpc/etc/task.yaml:42-47` Redis 超时/池/consumerName | — | — | 无对应字段，不迁 |
| `rpc/etc/task.yaml:51-52` 池 50/50 | Hikari `maximum-pool-size:50 / minimum-idle:50` | ADAPT | 先例 `message-rpc/application.yml:21-22` |
| `rpc/etc/task.yaml:53` ConnMaxLifetimeMinutes | Hikari `max-lifetime` | **UNKNOWN** | Go 是 30 **纳秒**的 bug |
| `rpc/etc/task.yaml:50` DSN TimeZone | `spring.datasource.url` | ADAPT | 时间列语义依赖 |
| `rpc/internal/config/config.go:11-31` Kafka 段 | `config/TaskKafkaTopics` | ADAPT | — |
| `rpc/internal/config/config.go:32-37` LikeRedis 段 | `spring.data.redis.*` | ADAPT | — |
| `rpc/internal/config/config.go:38-43` Postgres 段 | `application.yml` 数据源 + Hikari | ADAPT | — |
| `rpc/internal/config/config.go:9-10` PointsRpc/UserRpc | — | — | 死配置，不迁（⇒ 无 `client/` 包） |
| `rpc/internal/svc/service_context.go:25-29` Redis client | `StringRedisTemplate` | ABSORB | Boot 自动配置 |
| `rpc/internal/svc/service_context.go:34-42` gorm + panic | 数据源初始连接失败 | ADAPT | 语义等价（都起不来） |
| `rpc/internal/svc/service_context.go:43-44` 池 50/50 | Hikari | ADAPT | — |
| `rpc/internal/svc/service_context.go:45` `SetConnMaxLifetime` | Hikari `max-lifetime` | **UNKNOWN** | Go bug，需拍板 |
| `rpc/internal/svc/service_context.go:47-49` `AutoMigrate` | Flyway `V1`+`V2` | ADAPT | ⚠️ DDL 反推 UNKNOWN |
| `rpc/internal/Model/taskModel.go:6-14` 注释的 TaskDef | — | — | `task_def` 表不存在 |
| `rpc/internal/Model/taskModel.go:22-25` `AllTaskDefs` | `service/TaskDefs` | NEW | 3 处重复常量的收口点 |
| `rpc/internal/Model/taskModel.go:28-37` `TaskProgress` | `domain/TaskProgressEntity` | ADAPT | ORM 适配 |
| `rpc/internal/Model/taskModel.go:43-50` `UserLikeCount` | `domain/UserLikeCountEntity` | ADAPT | `user_id` 是 String |
| `rpc/internal/sink/like_sink.go:17-25` 重复 `UserLikeCount` | 同上 | MERGE | 同表重复定义 |
| `rpc/internal/logic/get_task_logic.go:43-45` 参数校验 | `TaskServiceGrpcImpl` 内 `Status.INVALID_ARGUMENT` | ADAPT | gRPC **标准码**，不建 exception 包 |
| `get_task_logic.go:48-49,51` Redis 读穿 | `service/TaskDomainService` | KEEP | 唯一用例 |
| `get_task_logic.go:53-57` PG 回源 | `mapper/TaskProgressMapper.selectByUserId` | ADAPT | `@Select` 原生 SQL |
| `get_task_logic.go:58-60` DB 错误透传 | `TaskServiceGrpcImpl` 兜底 `INTERNAL` | ADAPT | — |
| `get_task_logic.go:73-76` pipeline 回填 | `redis.executePipelined(SessionCallback)` | ADAPT | 先例 `HotRedisSnapshotStore.java:143-152` |
| `get_task_logic.go:78` 空结果 `Expire 30s` | `TaskDomainService` 同款无效操作 | KEEP | 保持可观察状态一致 |
| `get_task_logic.go:83-101` 与 defs 合并 | `TaskDomainService` + `TaskDefs` | MERGE | 同一变化原因 |
| `get_task_logic.go:106-112` key 与 TTL | `TaskRedisKeys` + `TaskKafkaTopics` | SPLIT | key 与 TTL 是两类契约 |
| `get_task_logic.go:114-139` pack/unpack/parse | `TaskDomainService` 私有 | MERGE | 单一消费者，不单列 |
| `rpc/internal/server/task_service_server.go:15-29` | `service/impl/TaskServiceGrpcImpl` | ADAPT | 分发由 starter 做 |
| `rpc/taskservice/task_service.go` | protobuf-maven-plugin 生成物 | ABSORB | 不手写 |
| `Init/initKafkaRaw.go:12-18` `PrintConsumer` | `mq/TaskKafkaLivenessProbe` | **UNKNOWN** | 非空组件但业务价值为零 |
| `Init/initKafkaRaw.go:20-43` kq 队列 | `TaskKafkaLivenessProbe` 的 `@KafkaListener` | ADAPT | 并发度 1、`latest` |
| `Init/initGoKa.go:23-32` `LogEvent` | `mq/TaskLogEvent` | NEW | 跨进程契约 |
| `Init/initGoKa.go:34-37` `TaskArticleEvent` | `mq/TaskArticleProgressEvent` | NEW | 同上 |
| `Init/initGoKa.go:39-43,45-58` DLQEvent/topicDLQ/countTable | — | — | 零使用死代码 |
| `Init/initGoKa.go:60-70` `process` | `mq/TaskRawLogRouter` | NEW | 无状态扇出 |
| `Init/initGoKa.go:72-82` `processUserCount` | `mq/TaskUserCountConsumer` | NEW | — |
| `Init/initGoKa.go:84-107` `processArticleCount` | `mq/TaskArticleCountConsumer` | NEW | — |
| `Init/initGoKa.go:167,201` `goka.Persist` | `mq/TaskCountStore` | **UNKNOWN** | 🔴 本域最大缺口 |
| `Init/initGoKa.go:131,173,207` `OffsetOldest` | `spring.kafka.consumer.auto-offset-reset: earliest`（仅这 3 个） | ADAPT | 与 kq 的 `latest` 不同 |
| `Init/initGoKa.go:134-136` Replication=1 | — | — | broker 侧，不在应用代码 |
| `Init/initGoKa.go:143-145,152-153` `log.Fatal` | 容器启动失败 | ADAPT | — |
| `Init/initUserFilter.go:22-58` | `mq/UserLikeProgressSink` | ADAPT | `@KafkaListener` + `@Scheduled` |
| `Init/initArticleFilter.go:22-49` | `mq/ArticleLikeProgressSink` | ADAPT | 同上 |
| `Init/initUserFilter.go:13-20,35-42` 注释段 | — | — | 旧实现，不迁 |
| `Init/initArticleFilter.go:13-20` 注释段 | — | — | 同上 |
| `Init/initReward.go`（全注释） | — | — | 死代码 |
| `sink/like_sink.go:81-108` `Consume` | `UserLikeProgressSink.consume` | KEEP | 三态错误契约 |
| `sink/like_sink.go:110-126` loop | `@Scheduled(fixedRate=1000)` + `@PreDestroy` | ADAPT | — |
| `sink/like_sink.go:128-145` `flushOnce` | 两个 sink 各自实现 | KEEP | 与 article 版错误契约不同 |
| `sink/like_sink.go:147-163` attrs | `common/observability/MqConsumer` | REUSE | 对应 Go `TraceConsumer` |
| `sink/like_sink.go:165-241` `lazyInit` | `service/TaskProgressWriter.lazyInit` | **MERGE** | 与 article 版共用 |
| `sink/like_sink.go:243-254` `swap` | sink 私有 map | KEEP | — |
| `sink/like_sink.go:256-293` `flushRedis` | sink + `TaskRedisKeys` | ADAPT | — |
| `sink/like_sink.go:295-326` `updateDoingTaskProgress` | `service/TaskProgressWriter.upsertDoing` | **MERGE** | ⭐ 与 `article_sink.go:385-419` SQL 逐字相同 |
| `sink/like_sink.go:328-334` `invalidateTaskCache` | `TaskProgressWriter.invalidateReadCache` | MERGE | 同上 |
| `sink/like_sink.go:336-364` `completeLikeGT5` | `UserLikeProgressSink` + `TaskProgressWriter` + `TaskRewardPublisher` | SPLIT | 幂等门 / 进度落库 / 出网三件事分离 |
| `sink/like_sink.go:366-385` `markTaskDoneInDB` | `TaskProgressWriter.upsertDone` | **MERGE** | ⭐ 与 `article_sink.go:462-491` 同形 |
| `sink/like_sink.go:387-404` `flushPostgres` | `TaskProgressWriter` | SPLIT | 分块 2000 |
| `sink/like_sink.go:406-422` `upsertChunk` | `mapper/UserLikeCountMapper` | ADAPT | — |
| `sink/article_sink.go:18-31` 常量 | `service/TaskDefs` + `config/TaskKafkaTopics` | MERGE | 与 `taskModel.go:22-25` / `like_sink.go:32-33` 收口 |
| `sink/article_sink.go:28,30` `reconZKeyPrefix`/`ownerHKey` | — | — | 只被死代码 `markReconCandidates` 用 |
| `sink/article_sink.go:33-49` `ArticleLikeTaskProgress` | `domain/TaskProgressEntity` | MERGE | 与前两个 GORM 结构体收敛 |
| `sink/article_sink.go:71-74` `articleVal` | `mq/TaskArticleProgressEvent` | NEW | 契约 |
| `sink/article_sink.go:76-91` 构造 + `Start` | `@PostConstruct` / `@Scheduled` | ADAPT | — |
| `sink/article_sink.go:93-124` `Consume` | `ArticleLikeProgressSink.consume` | KEEP | 独立的错误契约 |
| `sink/article_sink.go:126-139` `parseVal`（`Cur==0`→1） | `ArticleLikeProgressSink` 私有 | MERGE | 单一消费者 |
| `sink/article_sink.go:141-156` loop | `@Scheduled` + `@PreDestroy` | ADAPT | — |
| `sink/article_sink.go:158-181` `flushOnce` | `ArticleLikeProgressSink` | KEEP | **`FlushPostgres` 在 Go 侧是死代码** ⇒ 不迁 |
| `sink/article_sink.go:195-228` `markReconCandidates` | — | — | 唯一调用点被注释 |
| `sink/article_sink.go:230-328` `lazyInit` | `TaskProgressWriter.lazyInit` | **MERGE** | 与 like 版共用；⚠️ init key 模板与 TTL 都不同（`task:inits:190:{uid}:{aid}` / 24h） |
| `sink/article_sink.go:330-383` `FlushRedis` | `ArticleLikeProgressSink` + `TaskRedisKeys` | ADAPT | — |
| `sink/article_sink.go:385-419` `updateDoingTaskProgress` | `TaskProgressWriter.upsertDoing` | **MERGE** | 与 like 版 SQL 逐字相同 |
| `sink/article_sink.go:421-423` `invalidateTaskCache` | `TaskProgressWriter.invalidateReadCache` | MERGE | 同上 |
| `sink/article_sink.go:425-456` `completeArticleLike` | `ArticleLikeProgressSink` + writer + publisher | SPLIT | ⚠️ `addScore=5` 而 `target=3` |
| `sink/article_sink.go:458-460` 注释的 `FlushPostgres` | — | — | 死代码 |
| `sink/article_sink.go:462-491` `markTaskDoneInDB` | `TaskProgressWriter.upsertDone` | **MERGE** | 与 like 版同形 |
| `reward/type.go:8-11` `StreamKey` | `service/TaskRewardPublisher` 常量 | MERGE | 唯一使用者 |
| `reward/type.go:13-31` `RedisEvent`+`NewEvent` | `TaskRewardPublisher` 私有 | MERGE | ⭐ `reward_id = uid + taskID` 加法必须逐字保留 |
| `reward/producer.go:19-31` `XAdd` | `TaskRewardPublisher.enqueue` | ADAPT | ⚠️ `opsForStream` 零先例 |
| `reward/worker.go`（全注释） | — | — | 死代码 ⇒ stream 无消费者组、无界增长（UNKNOWN） |
| 本域零 metrics | — （不建 `metrics/`） | — | Go 侧无此职责 |
| 本域零业务错误码 | — （不建 `exception/`） | — | Go 侧无此职责 |
| `service/common/observability/rpc.go:19-23,25` | `RpcServerInterceptorFactory` | REUSE | Go `rpc.go:25` 的等价物 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ `TaskService.GetTask(GetTaskReq{user_id}) → GetTaskResp{task[]}`；**响应每个任务定义必返回一条**（未开始 `completion_progress=0`）；`required_progress` 取自定义而非 DB；`status`/`target` 不进 wire；`package task` 零偏离 ⇒ 方法路径 `/task.TaskService/GetTask` |
| L2 业务语义有承载位置 | ✅ uid ≤ 0 拒绝；Redis 错降级回源（不报错）；`AllTaskDefs` 合并；`progress` 单调（`GREATEST`）；`done` 不可回退（`CASE WHEN status='done'`）；`done_at` 只写一次（`COALESCE`）；init/done 的 `SetNX` 单次门；article 的 `Cur==0 → 1` 归一；`Consume` 的「重试 vs 丢弃」三分支 |
| L3 技术语义有承载方案 | ✅ 2s gRPC 超时；15 分钟读缓存 TTL；pipeline 回填失败忽略；`DEL` 失效写路径正确；关停前最后一次 flush；空结果分支的无效 `EXPIRE`；`AutoMigrate` → Flyway + 启动失败；Redis Stream 无消费者组 |
| 业务分支与异常路径已纳入 | ✅ `Consume` 的 6 条丢弃/重试分支（`like_sink.go:84,87,92-96,97`；`article_sink.go:95,99,103`）；三步 flush 的任一步失败；`completeLikeGT5` 的 `SetNX` 未命中早退；`reward.Enqueue` 失败即中止后续 DB 更新 |
| 框架吸收有机制依据 | ✅ 进程装配→Spring Boot；4 个 goroutine→容器生命周期；Redis/GORM 客户端→Boot 自动配置 |
| 复用组件已明确 | ✅ `RpcServerInterceptorFactory`、`ObservabilityProperties`、`DeadlineQueryTimeoutInjector`、`SqlTimeoutStatusTranslator`、`Jackson2ObjectMapperConfiguration`、`LoggerFactoryBean`、`MqConsumer.traceConsumer`、`StringRedisTemplate` + `executePipelined`、`NacosRegistration` |
| 新增文件有职责依据 | ✅ 21 个文件逐个有 Go 出处；**没有一个是"Go 有同名文件所以建"** |
| 合并有组织依据 | ✅ `like_sink.go:295-326` ↔ `article_sink.go:385-419`（SQL 逐字相同）与 `like_sink.go:366-385` ↔ `article_sink.go:462-491`（同形）合并进 `TaskProgressWriter`；3 个 GORM 结构体收敛成 1 个 Entity；`pack/unpack` 合入 `TaskDomainService` |
| 拆分可解释 | ✅ Kafka 入口（`mq/`）与持久化编排（`service/`）分离；topic 名单点定义；key 模板单点定义 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Init*.java` / `Sink*.java` / `ServiceContext.java` / `Logic.java` / `Server.java` / `Types.java` |
| 未把未实现能力说成现有机制 | ✅ `task-rpc` 现为空 pom + 空 src 已在 §12.1 声明；`opsForStream` 明确标为"零先例 UNKNOWN"而非"已有能力" |
| **进度写入逻辑已明确回答** | ✅ **存在，但不在 gRPC 服务里**：由两条 Kafka sink 写入（`like_sink.go:295-326,366-385`、`article_sink.go:385-419,462-491`），入口 `task.go:33-34`；`GetTask`（`get_task_logic.go:40-104`）对 PG 只有 1 条 SELECT。⇒ Java 侧**需要** domain/mapper，但理由是 sink 写路径而非 gRPC 读路径 |

### 不通过 / 待处理项

| # | 问题 | 详情 |
|---|---|---|
| 1 | 🔴 **`goka.Persist` 无 Java 对应物（最大缺口）** | `initGoKa.go:167,201` 用 goka 自己的表存储（RocksDB）做窗口累加，既不在 PG 也不在 Redis，无 TTL。RAG 无 stream-processing 框架。**必须拍板后端**（PG 计数表 / Redis `HINCRBY` / 其它），否则 §12.5 的 `TaskCountStore` 落不了地 |
| 2 | 🔴 **`SetConnMaxLifetime` 的 Go bug** | `service_context.go:45` `time.Duration(30)` = 30 **纳秒**。Java 侧配 `max-lifetime: 30m` 是**修正**（Go 侧连接会被持续重建）。方向正确但改变可观察行为 ⇒ 需拍板 + §3.2 留痕 |
| 3 | 🔴 **Flyway DDL 需从零反推** | Go 侧是 `AutoMigrate`（`service_context.go:47-49`），**没有手写 migration 目录**。2 张表的列类型/索引/默认值要按 GORM tag 反推，**必须与 Go 真 PG 上的实际 DDL 对拍**才能定案（AGENTS.md §3.1：不得凭 GORM tag 猜 DDL） |
| 4 | ⚠️ **本域零 metrics ⇒ 不建 `metrics/` 包** | Go 侧 `service/task/rpc/` 全树无 metric 定义（`like/rpc.md` §12.3 列出了 like 的 `internal/metrics/metrics.go`，task 没有）。但 6 个 listener 的消费失败/丢弃分支**在 Go 侧完全不可观测**。保持现状 or 补指标需拍板（补 = 新增可观察面） |
| 5 | ⚠️ **Redis Stream 的 `opsForStream` 全仓零先例** | `producer.go:20-29` 的 `XAdd` 在 Spring Data Redis 下的等价写法（`opsForStream().add(Record)`）未经本项目验证。⇒ UNKNOWN |
| 6 | ⚠️ **`TaskKafkaLivenessProbe` 是"看着像空组件、实际不是"** | `initKafkaRaw.go:12-18` 的 `Consume` 直接 `return nil`，唯一作用是连 broker + 加入 group `task.rpc.consumer31` + 丢弃全部消息。§14.4 禁止空组件，但它有真实外部依赖与真实副作用。**保留 or 删除需拍板**（删除 = 少一个诊断手段） |
| 7 | ⚠️ **`reward/worker.go` 全注释 ⇒ stream 无界增长** | `stream:task:task:reward` 被 `producer.go:20-29` 写入，但 `EnsureGroup` 只在被注释的 `worker.go:39-44` 里，**没有消费者组、没有 TTL、没有 MAXLEN 裁剪**。照抄 or 加消费者 or 加裁剪需拍板。另注意 `producer.go:27` 写 `score` 而 `worker.go:120` 读 `add_score`（字段名不匹配，但消费端是死代码故不可观察） |
| 8 | ⚠️ **`reward_id = uid + taskID` 是算术加法，必然碰撞** | `reward/type.go:25`。`uid=23,taskID=1902` 与 `uid=1902,taskID=23` 都得到 1925。注释把它标成「幂等ID」但它并不幂等。必须原样保留（改了就是改语义），但需在 §3.2 显式留痕 |
| 9 | ⚠️ **10 个 Redis key 中 9 个"只写不读"** | 见 §12.3 的 key 拓扑。`article_sink.go:291` 的注释自陈其中一个用途是「方便 redisslnsight 观测」。**不得被后人当死代码清理**，但需在 §3.2 留痕说明它们是运维资产 |
| 10 | ⚠️ **`task_progress` 的 article 粒度已丢失** | `article_sink.go:36,317,470,481` 把 `ArticleID` 全部注释掉 ⇒ 每 (user, task 190) 只有一行，per-article 粒度不存在。Java 侧 Entity 必须复刻这个"缺失"，**不能"顺手"补上 article_id 列** |
| 11 | ⚠️ **`task-rpc/pom.xml` 无任何依赖** | 缺 `task-proto`、`common`、`spring-boot-starter`、`spring-kafka` **+ `spring-boot-kafka`**（Boot 4 拆模块，缺后者是**硬启动失败** `NoSuchBeanDefinitionException: KafkaTemplate`，`like-rpc/pom.xml:146-158` 有实测注释）、`spring-boot-starter-data-redis` + `commons-pool2`、`mybatis-plus-spring-boot3-starter`、`postgresql`、`flyway-core` + `flyway-database-postgresql` + `spring-boot-flyway`、`grpc-server-spring-boot-starter`、`spring-boot-starter-actuator` + `micrometer-registry-prometheus`、`spring-cloud-starter-alibaba-nacos-discovery`。**且必须不引 `spring-boot-starter-web`**（`like-rpc/pom.xml:243`） |
| 12 | ⚠️ **`GroupFilterArticle`（`task.yaml:32`）零使用** | `initArticleFilter.go:30` 用的是 `GroupKafkaFilter`。⇒ Java 侧只迁 6 个 group，`task.yaml` 里要注明被跳过的那个，避免后人以为漏了 |
| 13 | ⚠️ **`goka` 三个 processor 是 `OffsetOldest`，kq 三个是 `latest`** | `initGoKa.go:131,173,207` vs `initKafkaRaw.go:32`/`initUserFilter.go:39`/`initArticleFilter.go:39`。⇒ Java 侧 `auto-offset-reset` **不能全局设一个值**，必须分 listener 配 |
| 14 | ⚠️ **`article_sink` 的 `flushOnce` 不调 `FlushPostgres`** | `article_sink.go:176-178` 被注释 ⇒ article 任务**只有完成时才写 PG**，`doing` 期间的进度只落在 Redis 的观测 hash 里。⇒ 照抄，否则 article 的「进行中」进度在 Redis 缓存失效后会显示为 0 |

### 关于「本域没有 `common`」的依据（§12.1 末的兑现）

与 `api.md` 同结论，四条依据在 rpc 侧同样成立：

1. **Go 侧 `service/task/` 全树零 `common/` 目录**（`find task -type d` 共 18 个目录，无一叫 `common`）；而同级 9 域中 8 域都有。
2. **Go 侧 task 零业务错误码**：`grep -rn "errmsg" service/task/` **零命中**。rpc 侧唯一的错误表达是 `get_task_logic.go:44` 的 `status.Error(codes.InvalidArgument, "user_id invalid")` —— **gRPC 标准码 3**，不是业务码；DB 错误是 `return nil, err`（`:59`）让 grpc 框架包成 `Unknown`。
3. **错误码的承载位置（本文件的答案）**：**不建 `exception/` 包，也不建 errmsg**。
   - gRPC 侧：`service/impl/TaskServiceGrpcImpl` 内直接 `Status.INVALID_ARGUMENT.withDescription("user_id invalid").asRuntimeException()`（`get_task_logic.go:44` 的逐字等价），兜底 `Status.INTERNAL`（对应 Go 让框架包 `Unknown`）。先例 `message-rpc/.../MessageRpcServiceImpl.java:41-44,60`。
   - api 侧：`advice/GlobalExceptionHandler` 把 `StatusRuntimeException` 映射成 HTTP 状态码（`api.md` §12.4）。
   - Kafka 侧：三条 sink 的「重试 vs 丢弃」由 **listener 的异常契约**承载，不用异常类型表达（`like/rpc.md` 已确立的同款纪律）。
   - 若将来需要业务码，参照 `like-common` 的 `errmsg/` 四件套新建 `task-common`；**当前无任何需求依据，不建**。
4. **RAG 侧已按此结论落地**：`service/task/pom.xml:14-18` 三项 modules，无 `task-common`；而 `service/comment/pom.xml:14-19` 有 `comment-common` 且目录存在。

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `TaskCountStore` 的后端（替代 `goka.Persist`） | PG 计数表 / Redis `HINCRBY` / 其它，需拍板；影响是否新增第 3 张表与是否新增 Redis 数据类型 |
| 两张表的真实 DDL | Go 侧只有 `AutoMigrate`，无 migration 目录；列类型/索引/默认值必须与 Go 真 PG 对拍才能定案 |
| `ConnMaxLifetimeMinutes` 的 Go bug 如何对齐 | 配 30m（修正）还是保留 30ns 语义（不可行/无意义） |
| `opsForStream()` 的 XADD 等价写法 | Spring Data Redis 侧本项目零先例 |
| `SetConnMaxLifetime` / `AutoMigrate` 之外是否有别的配置被静默吞掉 | `config.go` 结构体缺 `Processors` + 5 个 Redis 参数 ⇒ go-zero `conf.MustLoad` 对未知键的严格性未实测 |
| 是否补 Kafka 消费指标 | Go 侧零指标；补 = 新增可观察面 |
| `TaskKafkaLivenessProbe` 保留 or 删除 | 非空组件但业务价值为零 |
| `stream:task:reward` 的处置 | 照抄（无界增长）/ 加消费者组 / 加 MAXLEN 裁剪 |
| api→rpc 寻址：`127.0.0.1:9005` 还是 `discovery:///task-rpc` | 与 `api.md` §12.1 同一决策，两份文件须一致 |
| `/taskcenter/v1` 前缀是否签字留档 | 与 `api.md` §12.1 同一决策 |