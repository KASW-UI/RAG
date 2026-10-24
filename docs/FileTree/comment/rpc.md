# comment-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/comment/rpc`
> 目标根包：`com.rag.comment.comment.rpc`
> 推导日期：2026-10-23
> 状态：**理论推导**。RAG 侧 `service/comment/comment-rpc` **只有 `pom.xml`、0 行 Java**（实测 `pom.xml` 仅
> `<parent>` 8 行 + `<artifactId>comment-rpc</artifactId>` 12 行，`<dependencies>` 为空）。
> 本文件不实施任何代码。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

| RAG 位置 | 实测状态 | 对本文件的约束 |
|---|---|---|
| `service/comment/comment-rpc/pom.xml` | **空 `<dependencies>`** | 缺 `comment-proto` / `message-proto` / `article-proto` / `mybatis-plus` / `postgresql` / `spring-kafka` / `spring-boot-starter-data-redis` / `grpc-server-spring-boot-starter` / `flyway` ⇒ §12.5 的文件树一行都编译不过 |
| `service/comment/comment-rpc/src/{main,test}/…` | 目录存在，`find -type f` 零命中 | 无既有类 |
| `service/comment/comment-proto/` | **已完成**，`CommentServiceGrpc.java:15` `SERVICE_NAME = "comment.CommentService"` | L1 gRPC 契约已就位；⚠️ 方法路径前缀与 Go 不同，见 §12.1 下方风险点 |
| `service/comment/pom.xml:14-19` | 4 个 `<module>` 已列全 | 不提出第 5 个模块 |

### 参照的已落地 rpc 模块（包名集合已定型）

| 参照 | 提供的一致性依据 |
|---|---|
| `service/like/like-rpc` | `@GrpcService` + `extends LikeServiceGrpc.LikeServiceImplBase` + 单一错误出口（`service/impl/LikeServiceGrpcImpl.java:46-48,54-56`）；`bootstrap/{NacosRegistration,LikeSchemaGate,LikeFactProcessLogging}`；`client/MessageRpcClient`；`config/{LikeRpcProperties,LikeKafkaTopics,Jackson2ObjectMapperConfiguration,MybatisPlusOrderingBridgeAutoConfiguration}`；`domain/*Entity`；`mapper/{*Mapper,JsonbTypeHandler}`；`metrics/{LikeRpcMetrics,LikeMqMetrics}`；`service/{LikeDomainService,LikeDomainFactService,LikeDeliveryValidator,LikeFactDispatchService,LikeAuthorityLookupService,LikeRedisKeys,LikeCursorIdGenerator}`；`resources/db/migration/V1..V6`；`application.yml:61` `spring.flyway.table: flyway_schema_history_like` |
| `service/article/article-rpc` | `bootstrap/{ArticleSchemaGate,MinioBucketBootstrap,SnowflakeBootstrap,NacosRegistration}`；`client/{ArticleObjectStorageClient,MessageRpcClient}`；`mq/ArticleReviewConsumer`（Kafka 消费者的包归属先例）；`resources/db/migration/V8..V10` |
| `service/message/message-rpc` | `@SpringBootApplication` + `@ConfigurationPropertiesScan` + `@MapperScan` + `@Import{…}` + `GrpcServerConfigurer`（`MessageRpcApplication.java:64-108`）；`bootstrap/NacosRegistration`；`config/MessageProperties`；`domain/*Entity` + `mapper/*Mapper` + `mapper/GoStylePagination`；`exception/MessageExceptions`；`metrics/MessageMetrics`；`service/{MessageDomainService,MessageAtomicService,MessageRpcSupport}`；`application.yml:58` `spring.flyway.table: flyway_schema_history_message` |
| `service/users/{user,admin}-rpc` | `mapper/JsonbTypeHandler`；`config/MybatisPlusOrderingBridgeAutoConfiguration`；多步事务抽独立 Bean 的形态（`MessageAtomicService` / `ArticleAtomicService`） |
| `service/common` | `communityfact/{Wire,Dispatcher,AuthorityHandler,ProcessLogger}`；`spring/{AuthorityHandlerFactory,RpcServerInterceptorFactory,SnowflakeBean,ObservabilityLifecycle}`；`persistence/{DeadlineQueryTimeoutInjector,SqlTimeoutStatusTranslator}`；`snowflake/Snowflake`；`exception/BusinessException` |

**包名集合已定型**：`bootstrap/` `config/` `domain/` `exception/` `mapper/` `metrics/` `service/` `service/impl/`（`client/` 亦有 3 份先例）。本文件不提出集合外的包。

### 🔴 已知先例的空白（必须先看清再推导）

| 能力 | RAG 现状 | 证据 |
|---|---|---|
| **fact dispatch 循环** | like-rpc 的落点是 **4 行骨架占位、未实现** | `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeFactDispatchScheduler.java:3` 全文即注释 |
| **fact authority HTTP 端点** | like-rpc 的落点是 **4 行骨架占位、未实现** | `…/mq/LikeAuthorityEndpointConfig.java:3-4` 全文即注释，并自记「监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector（wire 面变更，待拍板）」 |
| ⇒ 全仓**没有任何模块真的跑起来过** DC 事实投递/权威查询 | 底层库是齐的：`common/communityfact/Dispatcher.java:64,86` 的 `Dispatcher.run(...)`、`spring/AuthorityHandlerFactory.java:39-49` 的 `register(producer, token, lookup)`、`AuthorityHandler.java:95-102` 返回 `HttpServlet` | `grep -rln "Dispatcher.run\|AuthorityHandlerFactory" service/ --include=*.java` 只命中 `common` 自身与 like 的占位文件 |
| **singleflight** | ❌ 全仓零命中 | `grep -rln "singleflight\|SingleFlight" service/ --include=*.java` 无结果 |
| **Aho-Corasick** | ❌ 全仓零命中 | `grep -rln "AhoCorasick\|ahocorasick" service/ --include=*.java` 无结果 |
| **Redis MGET / SCAN / pipeline** | ⚠️ 只有 `opsForValue().set(key,"banned",ttl)` 一处使用（`users/user/user-rpc`） | 见 `FileTree/like/rpc.md` §12.1 的 Redis 先例核实结论 |

### 已确认的框架机制（Boot 4 / Spring 特有）

- Spring Boot **4.0.8** / Java **21**（根 `pom.xml:9,22`）。
- **gRPC 拦截器不自动注册**：必须显式 `@Import(RpcServerInterceptorFactory.class)` +
  `GrpcServerConfigurer` 里 `server.intercept(...)`（`MessageRpcApplication.java:104-108` 就是这条的实测记录；
  `RpcServerInterceptorFactory.java:18-23` 的注释也写明「光有 Bean 不够」）。
  ⇒ 没有这一步，gRPC span / deadline 传播 / gRPC 超时**静默消失且不报错**。
- **`@KafkaListener` 需要显式 `spring-boot-kafka`**（AGENTS.md §3.3 实测：`like-rpc` 曾因此 `NoSuchBeanDefinitionException: KafkaTemplate` 硬启动失败）。
- **Jackson 3 的 `ObjectMapper` Bean 需自行装配**（`like-rpc` / `article-rpc` 各有一个
  `config/Jackson2ObjectMapperConfiguration.java`）。
- MyBatis-Plus `@MapperScan` + **注解式原生 SQL，全仓零 XML mapper**；动态 SQL 用 `@Select("<script>…")`；
  `<>` 会破坏 XML 解析需 CDATA 孪生常量；`<where>` 不可用（基表已带 WHERE）。
- jsonb 必须 `ps.setObject(i, json, Types.OTHER)`，靠 `JsonbTypeHandler` + `@TableName(autoResultMap = true)`。
- **无 MyBatis-Plus 分页拦截器**，分页手工拼 `LIMIT/OFFSET`（先例 `mapper/GoStylePagination.java:56-69`）。
- **无 Kafka 生产端先例**：全仓只有 like-rpc / article-rpc 的 `@KafkaListener`，**没有 `KafkaTemplate` 的生产用法**
  （`like-rpc/application.yml:105-107` 的注释正是在讨论"该用同步还是异步生产"，说明生产端**尚未定型**）。
- SQL 超时必须靠 `@Import{DeadlineQueryTimeoutInjector, SqlTimeoutStatusTranslator}`
  （`MessageRpcApplication.java:79-86`），否则在途 SQL 不会被取消。

### 🔴 L1 契约风险点 1：proto `package` 归一（`SD-2026-10-23-04`）

Go `ServiceName: "pb.CommentService"`（`service/comment/rpc/pb/comment_grpc.pb.go:316`）vs
RAG `SERVICE_NAME = "comment.CommentService"`（`service/comment/comment-proto/target/generated-sources/protobuf/grpc-java/com/rag/comment/comment/proto/CommentServiceGrpc.java:15`）
⇒ 7 个方法的 `:path` 全名从 `/pb.CommentService/*` 变成 `/comment.CommentService/*`（**wire 层可见**）。
依据与已签字状态见 `docs/Intentional_Deviations/comment/proto.md:18-46`。
当前 RAG 无对端调用故无实际影响；**双侧活体对拍会直接 `UNIMPLEMENTED`**。
本文件按已签字决策推导，对拍方案列为 §12.7 `UNKNOWN`。

### 🔴 L1 契约风险点 2：业务码塞不进 `grpc-status`（平台级硬限制）

Go 全程 `errmsg.NewGrpcErr(code, msg)` = `status.Error(codes.Code(code), msg)`（`errmsg.go:33-35`），
comment 侧调用点**实测 34 处**（`grep -rn "errmsg.NewGrpcErr" rpc` 计数，按文件分布：
`create_comment_logic.go` 7 处、`delete_comment_logic.go` 9 处、`like_comment_logic.go` 5 处、
`manage_comment_logic.go` 4 处、`report_comment_logic.go` 3 处、`update_subject_state_logic.go` 6 处；
**`get_comment_logic.go` 零处**——它返回裸 error，见 §12.3）。
码值域 1001~2001 / 5001~5010 / 1012 / 1013 —— **全部 > 16**。
grpc-go 的 `codes.Code` 是 `int32` 命名类型、不做范围校验；**grpc-java 发不出越界 `grpc-status`**
（实测结论见 `service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeGrpcError.java:36-48`：
`Status.fromCodeValue(5003)` → `UNKNOWN / value=2 / desc="Unknown code 5003"`）。
⇒ **必须复用 `LikeGrpcError` 的「等价信息保全 + 载体迁移」方案**（业务码进自定义 trailer、错误类别选**不在熔断失败名单内**的标准码），
详见 `FileTree/comment/common.md` §12.5。

---

## 12.2 第二部分：Go 原始文件树

```
service/comment/rpc/                                # 实测 46 个文件：40 个 .go + 6 个非 .go
├── comment.go                         # P1 入口：关原生超时 + InitMetrics + :9091 promhttp + kq consumer + gRPC server + unary interceptor
├── etc/comment.yaml                   # ListenOn 9001 / DB / KqPusher / KqConsumer / BizRedis / MessageRpc / ArticleRpc
├── COMMUNITY_FACTS_ACCEPTANCE.md      # 文档（非代码）
├── test-community-postgres.sh         # 测试脚本（非代码）
├── commentservice/comment_service.go  # goctl 生成的 gRPC 客户端桩（7 方法）—— 生成物，不迁移
├── pb/*.pb.go                         # protoc 生成物（2 个）—— 不迁移
├── cmd/
│   ├── fact-authority/main.go         # P2 入口：env 校验 + AuthorityHandler servlet + 3s/5s 超时 + 优雅停机
│   └── fact-dispatch/main.go          # P3 入口：env 校验 + DCEventSender(8s) + RunDispatcher(-once)
├── common/types/kqueue.go             # CommentKafkaMsg（11 字段 JSON 契约，producer/consumer 共用）
└── internal/
    ├── config/config.go               # RpcServerConf + DB + KqPusherConf + BizRedis + KqConsumerConf + MessageRpc + ArticleRpc
    ├── svc/service_context.go         # InitDB + CommentModel / InitRedis + CommentCache / kq Pusher / SensitiveFilter / 2 个 rpc client
    ├── server/comment_service_server.go   # 7 个 RPC → logic 的 thin wrapper
    ├── logic/
    │   ├── create_comment_logic.go    # 校验 2 条 + resolveOwnerID + 雪花 ID + Kafka 推送（**不写库**）
    │   ├── delete_comment_logic.go    # 查评论/查评论区/鉴权 + DeleteCommentTx + 2 次缓存失效
    │   ├── get_comment_logic.go       # **1s 超时** + loadSubject + 两级缓存分页读 + list_size gauge
    │   ├── like_comment_logic.go      # GetOwnerId（事务外）+ LikeCommentTx + 缓存失效
    │   ├── manage_comment_logic.go    # actionType → (bitOffset,isSet) + ManageCommentAttribute（**无事务**）
    │   ├── report_comment_logic.go    # 构造 ReportRecord + InsertReport（**无事务**）
    │   ├── update_subject_state_logic.go  # 查评论区 + 鉴权 + UpdateSubjectState（**无事务**）
    │   └── subject_fallback.go        # targetType=="article" 时向 article-rpc 取 ownerId
    ├── metrics/metrics.go             # 5 个 prometheus 向量 + InitMetrics（**注销 go/process collector**）
    ├── trace/trace.go                 # InitOTel —— **全仓零调用点**（grep 实测）
    ├── utils/sensitive.go             # SensitiveFilter：Aho-Corasick 多模式匹配
    ├── cache/
    │   ├── redis.go                   # InitRedis：3s 超时 Ping，失败 log.Fatalln
    │   ├── keys.go                    # 4 个 key 构造函数
    │   ├── comment_cache.go           # CommentCache struct（rdb + singleflight.Group）
    │   ├── comment_subject_cache.go   # GetSubjectWithCache（双检 + singleflight + 回填）+ Get/Set
    │   ├── comment_ids_page_cache.go  # GetReplyIDsPageCache（双检 + singleflight + 回填）+ preloadNextReplyIDsPage（**零调用点**）
    │   ├── comment_index_cache.go     # GetCommentIndexCache（MGET + miss 回源 + pipeline 回填 + 按输入序重排）
    │   ├── comment_content_cache.go   # BatchGetContentCache（同上，TTL 24h）
    │   └── comment_invalidation_cache.go # InvalidateTargetCaches（SCAN+DEL）+ DeleteCommentIndexCache + deleteKeys（去重 + 每批 100）
    ├── mqs/audit_consumer.go          # Kafka consumer：敏感词判定 → InsertCommentTx → 失效缓存 → 通知
    └── model/
        ├── db.go                      # InitDB + AutoMigrate（**6 张表，不含 report_record**）+ 失败 log.Fatalln
        ├── vars.go                    # 3 个 sentinel error
        ├── data_model.go              # 6 个 GORM 实体 + ReplySort + GetReplyIDsPageReq
        ├── comment.go                 # CommentModel 669 行：13 个方法（3 个事务 + 缓存回源 + upsert）
        ├── domain_fact.go             # CommentDomainFactOutbox / CommentFactStream / commentFact / appendCommentFact
        ├── fact_delivery.go           # DispatchFactOnce（SKIP LOCKED + 前序屏障 + 回执校验）+ validCommentDelivery
        ├── fact_authority.go          # AuthoritativeFact + authoritativeCommentInteraction（链路重放校验）
        ├── domain_fact_test.go        # 真 PG，无 DSN 时 t.Skip —— 默认不跑
        ├── fact_delivery_logging_test.go
        ├── fact_process_fixture_test.go
        └── migrations/
            ├── 001_comment_domain_fact_outbox.sql   # fact outbox 基础 4 列 + comment_like 全表
            ├── 002_comment_dc_wire.sql               # +3 列 / comment_fact_stream / 2 索引 / NOT VALID CHECK
            └── 003_comment_fact_delivery.sql         # +投递元数据 7 列 / 复合索引 / 不可变触发器
```

**文件数核对**：Go 侧非测试、非生成物 **35 个 `.go` + 1 个 `etc/comment.yaml` = 36 个**；
另有 3 个 `_test.go`、2 个 `pb/*.pb.go`、3 个 `.sql`、`COMMUNITY_FACTS_ACCEPTANCE.md`、`test-community-postgres.sh`。
本文件 §12.3 覆盖全部 36 个 + 3 个 `.sql`；3 个 `_test.go` 与 2 个生成物在 §12.3 末尾单独说明。

---

## 12.3 第三部分：Go 文件职责分析

### `comment.go`（P1 进程装配，59 行）

- `:33` `observability.DisableNativeRpcTimeout(&c.RpcServerConf)` → `:34` `svc.NewServiceContext(c)` → `:36` `metrics.InitMetrics(&c)`
- `:38-41` **独立 goroutine 起 `:9091` 的 `promhttp.Handler()`**（用 `http.DefaultServeMux`）
- `:43-45` `kq.MustNewQueue(c.KqConsumerConf, mqs.NewAuditConsumer(bgCtx, ctx))` + `go queue.Start()` + `defer queue.Stop()`
  ⇒ **审计消费者与 gRPC server 同进程**（8 个并发消费者，group `comment-audit-group`，topic `comment-audit-topic`，offset `last`）
- `:47-53` `zrpc.MustNewServer` + `RegisterCommentServiceServer`；`:50-52` 仅 `DevMode`/`TestMode` 注册 reflection
- `:54` `AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, SlowThreshold()))`

**L3 拓扑事实：3 个 `main` 包（`:1`、fact-authority `:23`、fact-dispatch `:23`）共用同一张 PG 与同一个 Redis**
（`cmd/*` 读 `os.Getenv("COMMENT_DATABASE_URL")`，P1 读 `c.DB.DataSource`；`model/db.go:15` 的 AutoMigrate
与 `cmd/*` 的 SQL 完全一致）⇒ 与 `FileTree/like/rpc.md` §12.1 相同的结论：**P2/P3 落成 `comment-rpc` 内的 Spring 组件，不做独立 deployable**。

### `internal/metrics/metrics.go`（5 个向量 + 一个副作用）

| Go 变量 | Prometheus 名 | 类型 | labels | 记录点 |
|---|---|---|---|---|
| `CommentRequestCounterMetric:10-14` | `comment_request_total` | Counter | `[module, action, result]` | 7 个 logic 的 defer |
| `CommentRequestSecondsCounterMetric:16-20` | `comment_request_seconds_counter` | **Counter**（名字像 Histogram，实际是累加秒数的 Counter） | `[module, action]` | 同上 |
| `CommentListSizeGaugeMetric:22-26` | `comment_list_size` | Gauge | `[module, action]` | `get_comment_logic.go:146-148` |
| `CommentPostgresErrorCounterMetric:28-32` | `comment_postgres_error_total` | Counter | `[module, action, type]` | `model/comment.go` 与 5 个 cache 文件共 30+ 处 |
| `CommentRedisErrorCounterMetric:34-38` | `comment_redis_error_total` | Counter | `[module, action, type]` | 5 个 cache 文件 |

- 🔴 **`InitMetrics(&c)`（`:41-49`）先 `prometheus.Unregister(NewGoCollector())` 与 `Unregister(NewProcessCollector())`**
  ⇒ Go 进程**不暴露** `go_*` / `process_*` 指标。Java/Micrometer 默认暴露 `jvm_*` / `process_*`
  ⇒ **要么照做（主动 disable），要么接受新增可观测面**。本文件按 Go 原样推导为「显式关闭」并在 §12.7 标注为待拍板。
- 🔴 **`result` 标签的取值集合 7 个 logic 不一致**：
  - 6 个 logic 显式维护 `result ∈ {ok, biz_fail, sys_fail}`（`create:39,64,86`；`delete:35,59,74`；`like:33,46,61,84`；`manage:35,59,82,97`；`report:35,42,62,88`；`update:35,61,77,86,103`）
  - **`get_comment_logic.go:35-38` 只有 `{ok, sys_fail}`**（由命名返回值 `err != null` 推导，从不设 `biz_fail`）
- 🔴 **`module` 标签不统一**：6 个 logic 用 `"comment_rpc"`；`get_comment_logic.go:147` 的 list_size 用 **`"comment_list"`**。
- `InitMetrics` **在 `comment.go:36` 被真的调用**（与 like 域的 `InitMetrics` 零调用点不同——见 `FileTree/like/api.md` §12.7 #2）。

### `internal/trace/trace.go` —— 🔴 **整个文件是死代码**

`:16` `InitOTel(serviceName, endpoint)`。**实测全仓零调用点**（`grep -rn "InitOTel" .` 只命中定义行）。
⇒ 7 个 logic 里的 `otel.Tracer("comment-rpc").Start(...)`（`create:41-42`、`delete:37-38`、`manage:37-38`、
`report:37-38`、`update:37-38`）与 `otel.Tracer("comment.rpc")`（`get_comment_logic.go:52-53`）拿到的都是 **no-op tracer**：
**Go 侧这批手工 span 从未真正导出**（注意两个 tracer 名还不一样：`comment-rpc` vs `comment.rpc`）。
⇒ RAG 侧若把 `RpcServerInterceptorFactory` 正确 `@Import` + `intercept()`（§12.1 已确认这是**必须**的），
会产生**真实的 gRPC span**——这是**新增可观测面**，必须显式承认，不能当成"对齐"。

### `internal/logic/create_comment_logic.go`（157 行）—— 🔴 **本文件最反直觉的一处**

调用链：校验 → `resolveOwnerID` → `snowflake.GetID()` → 构造 `CommentKafkaMsg` → `json.Marshal` → `KqPusherClient.Push` → **直接返回**。

- `:63-68` `TargetId == "" || TargetType == ""` → `NewGrpcErr(2001, "目标类型和ID不能为空")`
- `:70-75` `Content == ""` → `NewGrpcErr(2001, "评论内容不能为空")`
- `:77-91` `resolveOwnerID` 失败：`ErrorSubjectNotFound` → `NewGrpcErr(1012, "评论区不存在")`；其他 → `NewGrpcErr(5003, "DB查询失败")`
- `:93-100` 雪花 ID 失败 → `NewGrpcErr(5006, "雪花算法生成ID出错")`
- `:102-115` 构造消息：`CreateTime: now.Unix()`（**Unix 秒**）、`Attribute: 0`（硬编码，proto 里也没这字段）、`OwnerId: ownerID`
- `:117-124` `json.Marshal` 失败 → `NewGrpcErr(5007, "Json序列化失败")`
- `:126-132` `Push` 失败 → `NewGrpcErr(5009, "Kafka推送失败")`
- `:136-140` 返回 `{Id: commentID, CreatedAt: now.Format(time.DateTime), SubjectCount: 0}`

⇒ **两条 L1 冻结**：
1. **`CreateComment` 从不写库**。评论要等 Kafka 消费者（`audit_consumer.go`）提交事务后才出现在列表里。
2. **`SubjectCount` 恒为 `0`**（`:139` 硬编码），尽管它是「操作后评论区的总评论数」（`comment.proto:131`）。
3. `time.DateTime` = `"2006-01-02 15:04:05"`（**本地时区、无时区后缀**）。

`resolveOwnerID`（`:143-156`）：先查本地 `subject` 表；`ErrorSubjectNotFound` 才走 `resolveSubjectFallback`（article-rpc）。
⚠️ **`:153-155` 出错时返回的是原始 `err`（即 `ErrorSubjectNotFound`）而不是 `fallbackErr`** ——
因为 `resolveSubjectFallback` 也只会返回 `ErrorSubjectNotFound`，当前**结果等价**，但这段代码的意图不显式。

### `internal/logic/get_comment_logic.go`（178 行）

- `:34-39` `TrimSpace(TargetType/TargetId) == ""` → **裸 `error`**（未包装成 sentinel），直接上抛
- `:49` 🔴 **`context.WithTimeout(l.ctx, time.Second)` —— 整条 GetComment 链路只有 1 秒预算**（含缓存读 + 2 次 DB 回源 + 组装）
- `:56-67` `page<=0→1`、`pageSize<=0→20`、`pageSize>100→100`、`offset=(page-1)*pageSize`
- `:78-81` `sortType = ReplySortHot`（`SortType==0`）否则 `ReplySortTime`
- `:83-88` `loadSubject`（缓存 → DB → article 回落）
- `:91-104` `GetReplyIDsPageCache`；`:107-120` `GetCommentIndexCache` + `BatchGetContentCache`
- `:122-143` 🔴 **按下标配对 `indexRows[i]` 与 `contentRows[i]`**，`:123-125` 只防越界不防错位
  ⇒ 若某条评论有 index 行但**缺 content 行**，`contentRows` 会整体前移 ⇒ **内容与统计串位**（latent bug，见 §12.7 #5）
- `:139` `CreatedAt: indexRow.CreatedAt.Format("2006-01-02 15:04:05")` —— **本地时区**，而 `domain_fact.go:96` 的 fact 用 `.UTC()`
- `:141` `Children: nil` ⇒ api 侧的递归投影代码**当前不可达**
- `:146-148` `comment_list_size{module="comment_list", action="GetComment"}`
- `:163-177` `loadSubject`：`ErrorSubjectNotFound` 时走 article 回落，**并把零计数回落结果写进 Redis 5 分钟**
  （`:176` `SetSubjectCache(ctx, targetID, &subject, 5*time.Minute)`）⇒ **新建评论区的计数会被缓存成 0 达 5 分钟**

### `internal/logic/delete_comment_logic.go`（136 行）

`:58-63` 参数校验 → `:65-79` `FindOneCommentById` → `:81-95` `FindOneSubjectByTarget` →
`:97-102` **鉴权（事务外预检）**：`in.UserId != comment.UserId && != subject.OwnerId` → `1008` →
`:104-121` `DeleteCommentTx`（**事务内再鉴权一次**，见 `model/comment.go:282-284`）→
`:122-127` 两次缓存失效，**失败只打日志不返回**（`:123,126`）→ `:131-135` `{Success: true, SubjectTotalCount: remainCount}`。
`Success` 在 rpc 侧也是硬编码 `true`（`:132`）。

### `internal/logic/like_comment_logic.go`（98 行）

`:45-50` `CommentId == 0` → 2001；`:52-65` `GetOwnerId`（**事务外**）：`ErrorSubjectNotFound` → 1012，
其他 → 🔴 **`NewGrpcErr(errmsg.ErrorInputWrong, "DB更新失败")`** —— 日志记的是 `ErrorDbInsert(5004)`，
**但发出去的是 2001**（`:62-63`，记与发不一致）；`:67-88` `LikeCommentTx`；
`:89-91` 缓存失效失败只打日志；`:94-96` `{Success: true}`（**`LikeCount` 字段不返回**——proto 有这个字段但 rpc 不填）。

### `internal/logic/manage_comment_logic.go` / `report_comment_logic.go` / `update_subject_state_logic.go`

- `manage:68-86` `ManageType` → `(bitOffset, isSet)`：`PIN/UNPIN → 2`，`FEATURE/UNFEATURE → 3`，`default → 2001`。
  `manage:88` `ManageCommentAttribute` **无事务**；`default` 之外的 5 个分支值在 Go 的 switch 里**不可达**（api 只透传）
- `report:69-76` 构造 `ReportRecord`（`Reason: int32(in.Reason)`）；`report:79` `InsertReport`（**无事务**）；
  🔴 `InsertReport` 用的是 `clause.OnConflict{DoNothing: true}` 且**未指定 Columns** ⇒ 撞**任何**唯一约束都静默成功
  （`model/comment.go:243`）⇒ **重复举报返回 `success: true`**
- `update:60-65` 参数校验；`:68-82` 查评论区；`:85-90` **鉴权：`in.UserId != subject.OwnerId` → 1008**（只允许 up 主，无 admin 分支）；
  `:93-107` `UpdateSubjectState`（**无事务**）

### `internal/logic/subject_fallback.go`（39 行）

`:13-15` `!strings.EqualFold(TrimSpace(targetType), "article") || articleRPC == nil` → `ErrorSubjectNotFound`；
`:17-20` `GetArticle({ArticleId: targetID, IncrView: false})`；`:21-23` 任一 nil → `ErrorSubjectNotFound`；
`:25-28` `ParseInt(TrimSpace(resp.Article.AuthorId))` 失败或 `<= 0` → `ErrorSubjectNotFound`；
`:30-38` 返回 `{TargetType: targetType（**未 trim 的原值**）, TotalCount: 0, RootCount: 0, State: 0, Attribute: 0, OwnerId: ownerID}`。

### `internal/mqs/audit_consumer.go`（132 行）

`:34` `observability.TraceConsumer(ctx, "comment-rpc", "AuditConsumer.Consume", attrs, fn)`。
**四条出口的方向必须逐条保留**：

| 分支 | 行号 | Go 返回 | 后果 | Java 侧方向 |
|---|---|---|---|---|
| JSON 解析失败 | `:36-39` | `nil` | **丢弃**、提交位点 | 正常 `return` |
| `InsertCommentTx` 失败 | `:48-51` | `err` | **重投**、不提交 | 抛异常 |
| 缓存失效失败 | `:52-59` | 只打日志 | 不影响位点 | 只打日志 |
| 成功 | `:66` | `nil` | 提交 | 正常返回 |

`:41-46` 敏感词判定：`status = 1`（审核中）否则 `0`；`:61-63` **只有 `status == 0` 才发通知**。
`:79-120` `notifyComment`：`recipient = msg.OwnerId`；`ParentId != 0` → 查父评论、`recipient = parent.UserId`、
`kind = COMMENT_REPLY`；`:97-99` `recipient <= 0 || recipient == msg.UserId` → **不发**；
`:101-116` `SendNotification({recipientIds:[recipient], broadcast:false, senderId, senderRole:USER, kind, title, content, extra{5 键}})`。
`:122-132` `previewComment`：trim → 空则 `"(no content)"` → 超 40 rune 则前 40 + `"..."`。

### `internal/utils/sensitive.go`（43 行）

`:14-18` 空词库 → 打警告 + 返回空机；`:19-27` 逐词 `ToLower` 后建 AC 自动机（**构建失败 `log.Fatalf` 直接退出进程**）；
`:33-42` `Match`：文本 `ToLower` → `MultiPatternSearch(runes, false)` → 命中返回 `(true, 第一个匹配词)`。
词库来自 `service_context.go:29` **`blackwords := []string{"傻逼", "我草"}`（硬编码，不来自配置）**。

### `internal/svc/service_context.go`（39 行）

`:26` `model2.InitDB` → `:27` `cache2.InitRedis` → `:28` `kq.NewPusher(brokers, topic)` →
`:29` 硬编码黑名单 → `:32-37` 组装 `CommentModel` / `CommentCache` / `SensitiveFilter` / `MessageRpc` / `ArticleRpc`。
**无 snowflake 依赖**（雪花是 `snowflake.GetID()` 进程级单例，见 `create:93`）。

### `internal/model/db.go`（20 行）+ `vars.go`（9 行）

`db.go:15` `AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{}, &CommentDomainFactOutbox{}, &CommentFactStream{})`
⇒ **6 张表**。🔴 **`ReportRecord` 不在 AutoMigrate 列表里** ⇒ `report_record` 表在 Go 侧**从不被自动建出**
（`InsertReport` 会直接失败）。见 §12.7 #4。
`vars.go:5-9` 三个 sentinel：`ErrorSubjectNotFound` / `ErrorCommentNotFound` / `ErrorCommentForbidden`。

### `internal/model/data_model.go`（101 行）

6 个 GORM 实体 + `ReplySort{time,hot}` + `GetReplyIDsPageReq`（7 字段，含 `OnlyNormal`）。

| 表 | 主键 | 关键约束（Go tag） |
|---|---|---|
| `subject`（`:5-20`） | `id` autoIncrement | `target_type`+`target_id` **复合唯一** `uniqueIndex:idx_target`；`total_count`/`root_count`/`state`/`attribute` 均 `not null default 0` |
| `comment_index`（`:22-40`） | `id` **不 autoIncrement**（雪花） | 复合索引 `idx_target_root_time(target_type, target_id, root_id, created_at)` |
| `comment_content`（`:42-53`） | `comment_id` **不 autoIncrement** | `meta` 是 **`type:jsonb`** |
| `report_record`（`:55-68`） | `id` autoIncrement | 复合唯一 `idx_user_comment_report(user_id, comment_id)`；`detail` varchar(255) |
| `comment_like`（`:87-101`） | `id` autoIncrement | 复合唯一 `idx_user_comment_like(user_id, comment_id)`；`state` int32 无 tag |
| `comment_domain_fact_outbox` | 见 `domain_fact.go:15-32` | `aggregate_id`+`fact_version` 复合唯一 |
| `comment_fact_stream` | `aggregate_id` | `version` bigint CHECK |

### `internal/model/comment.go`（669 行，CommentModel 13 个方法）

**3 个事务方法**（`Transaction(func(tx))` 显式开）：

1. **`InsertCommentTx:28-155`**（唯一调用方 = Kafka 消费者）
   - `:29-32` `subjectRef(msg.UserId)`（uid<=0 报错）；`:33-35` 三项参数校验
   - `:39-60` **幂等探针**：按 id 查 `comment_index`；命中则比 5 个身份字段（不一致 → `reused with conflicting identity`）+
     比 content（不一致 → `reused with conflicting content`）；**全一致则 `return nil`（no-op）**
   - `:62-63` `createTime = time.Unix(msg.CreateTime, 0)`（**秒**）+ `normalizeCommentMeta`
   - `:66-77` 插 `comment_content` → `:80-96` 插 `comment_index`
   - `:99-123` **upsert `subject`**：`ON CONFLICT (target_type, target_id) DO UPDATE SET total_count = "subject"."total_count" + 1`，
     `RootId == 0` 时再加 `root_count + 1`（⚠️ `DoUpdates` **只设这两列**，`owner_id` 只在 INSERT 时写）
   - `:126-141` `ParentId != 0` → 父评论 `reply_count + 1`；且 `msg.UserId == msg.OwnerId` 时额外 `attribute | (1 << 1)`；
     `RowsAffected == 0` → `ErrorCommentNotFound`（**整事务回滚**）
   - `:143-153` `appendCommentFact`（`EventType: "community.comment.created"`、`Operation: "create"`、
     `SourceRef: "rtw.comment/<id>"`、`EventID: "rtw.comment.<id>.created"`）
2. **`DeleteCommentTx:252-353`**
   - `:258-266` `SELECT ... FOR UPDATE` 取评论；`ErrRecordNotFound` → `ErrorCommentNotFound`
   - `:267-269` 评论的 `target_type`/`target_id` 必须与入参一致，否则 `ErrorCommentNotFound`
   - `:273-281` 查 `subject`
   - `:282-284` **事务内再鉴权**：`userId != comment.UserId && userId != sub.OwnerId` → `ErrorCommentForbidden`
   - `:287-341` **`if comment.State != 2`**（只有首次删除才做）：`state = 2`、`subject.total_count -= 1`
     （`RootId == 0` 再 `root_count -= 1`）、`ParentId != 0` 时父评论 `reply_count -= 1`
     （`RowsAffected == 0` → `ErrorCommentNotFound`）、追加 `community.comment.deleted` / `retract` fact
   - `:344-347` 重读 `subject` 取 `remainCount`
   ⇒ **二次删除幂等**：不重复减计数、不重复发 fact，返回同样的 `remainCount`
3. **`LikeCommentTx:472-598`**
   - `:484-487` 先用 `ON CONFLICT DO NOTHING` **播种一行 `state=0`** 作为锁锚点（注释明写"首次并发也要有锚点"）
   - `:489-495` `SELECT ... FOR UPDATE` 取 `comment_like`
   - `:499-510` 取 `comment_index`；**`comment.State == 2`（已删除）也返回 `ErrorCommentNotFound`**
   - `:516-549` 状态机：`1/2/3/4` 四种动作；**已经是目标态时 `return nil` 提前退出（因此不产生 fact）**
   - `:552-558` 更新 `comment_like.state`
   - `:561-583` 更新 `comment_index`：`like_count`/`dislike_count` 差量；且 `userId == ownerId` 时
     `attribute | 1`（newState==1）或 `attribute & ~1`
   - `:586-596` 追加 `community.comment.interaction` fact，`EventID = "rtw.comment.interaction." + uuid`

**其余 10 个非事务方法**：
`ManageCommentAttribute:157-182`（`attribute | ?` / `attribute & ~?`，`RowsAffected==0` → `ErrorCommentNotFound`）、
`UpdateSubjectState:184-201`、`GetOwnerId:203-220`、`normalizeCommentMeta:222-240`、
`InsertReport:242-250`、`GetReplyIDsByPage:355-416`、`BatchGetReplyContent:422-470`、
`BatchGetReplyIndexByIDs:600-631`、`FindOneCommentById:633-650`、`FindOneSubjectByTarget:652-668`。

⚠️ `normalizeCommentMeta:222-240`：trim → 空则 `"{}"` → **合法 JSON 原样返回** → 否则包成 `{"raw":"<原文>"}`。

### `internal/cache/*`（6 文件，Redis 读穿 + 失效）

| 能力 | 文件:行 | TTL | 关键性质 |
|---|---|---|---|
| 客户端 | `redis.go:12-28` | — | `Addr=Host`（**单节点，无 Pass**）；`Ping` 3s 超时，失败 `log.Fatalln` |
| key 构造 | `keys.go:5-20` | — | 🔴 `SubjectKey(subjectID)` **只用 subjectID、不含 targetType** ⇒ 跨类型同 ID 会撞 key |
| subject 读穿 | `comment_subject_cache.go:16-74` | 5min（`:14`） | 三段：读缓存 → `singleflight` 内双检 → 回源 DB → 回填；`:52-55` **DB 回源失败也记 `CommentRedisErrorCounterMetric{type="db_fallback"}`**（指标名与实际错误来源不符） |
| 分页 id 读穿 | `comment_ids_page_cache.go:16-89` | 3min（`:14`） | key 含 `offset`+`limit` ⇒ **每页一个 key**；`:34-44` `redis.Nil` 不记指标、真异常才记 |
| 预取下一页 | `comment_ids_page_cache.go:91-138` | — | 🔴 **零调用点**（`grep -rn preloadNextReplyIDsPage` 只命中定义）⇒ 死代码 |
| index 批量读 | `comment_index_cache.go:14-119` | 10min（`:12`） | `MGET` → miss 回源 → `pipeline` 回填（失败不阻断）→ **按输入 ids 顺序重排** |
| content 批量读 | `comment_content_cache.go:14-122` | 24h（`:12`） | 同上；`:97-107` `pipeline` 回填 |
| 目标缓存失效 | `comment_invalidation_cache.go:10-35` | — | 🔴 只删 `comment:subject:<id>` + `SCAN comment:index:<type>:<id>:*`；**不删 `comment:content:*`**（内容不可变，24h TTL 自然过期） |
| 单条 index 失效 | `comment_invalidation_cache.go:37-54` | — | 只删 `comment:index:item:<id>`；`id <= 0` 跳过 |
| 批量 DEL | `comment_invalidation_cache.go:56-83` | — | 去重 + 空串过滤 + 每批 100 |

### `internal/model/domain_fact.go`（146 行）

`CommentDomainFactOutbox:15-32`（19 列，`payload`/`delivery_envelope` 是 jsonb）+ `CommentFactStream:36-39` +
`commentDeliveryEnvelope:43-53` + `commentFact:55-80`（**26 字段**）+ `appendCommentFact:89-142`：
- `:90-92` 四个必填字段任一为空 → `incomplete comment fact`
- `:93-98` `EventTime` 为零则取 `now.UTC()`；`OccurredAt = EventTime`；`AvailableAt = now.UTC()`
- `:99-102` 固定 `SchemaVersion="rtw.community-fact.v1"`、`Producer="rtw.comment-rpc"`、`RevisionStatus="unknown"`、`SearchEvidence=false`
- `:107-114` 🔴 **legacy 守卫**：`payload->>'aggregate_id' = ? AND delivery_envelope IS NULL` 的行数 > 0 ⇒ 拒绝出 fact
- `:116-120` `INSERT INTO comment_fact_stream ... ON CONFLICT (aggregate_id) DO UPDATE SET version = comment_fact_stream.version + 1 RETURNING`
- `:121-123` 版本必须在 `[1, 9007199254740991]`
- `:137-141` **同一份 payload 字符串**既进 `payload` 列又内嵌进信封（投递侧要按 JCS 比哈希，两份必须同源）

### `internal/model/fact_delivery.go`（135 行）

`validCommentDelivery:20-48`（逐字段比对 + `StrictDecode` + 两次 `CanonicalHash` 必须相等）
+ `DispatchFactOnce:53-127`：
- `:58-62` 手动 `Begin` + `defer Rollback`
- `:64-70` `FOR UPDATE SKIP LOCKED` + `delivery_status IN (0,2) AND delivery_envelope IS NOT NULL`
  + **`NOT EXISTS` 前序屏障**（同一 `aggregate_id` 下更小 `fact_version` 且 `delivery_status != 1` 的行必须不存在）
  + `ORDER BY created_at, event_id` 取 1 行
- `:78-90` 信封非法 → 置 `delivery_status = 3 (Blocked)`、**提交**、返回 `ErrFrozenEnvelopeBlocked`
- `:93-110` 发送失败或回执不匹配 → `delivery_status = 2` + `retry_count + 1`、提交、返回错误
- `:112-126` 成功 → 写 6 列回执 + `delivered_at`、提交

### `internal/model/fact_authority.go`（214 行）

`deliveredCommentAuthority:12-40`（9 项前置校验 + `ParseRTWSubject` + 信封哈希必须等于 `technical_input_hash`）
+ `validCommentRevision:42-47` + `AuthoritativeFact:51-130`：
按 `event_type` 分三支：`created`（校验 `content_count == 1`、已删则必须有恰好 1 条 retract fact）、
`deleted`（必须有已投递且 offset 更大、字段一致的 `created` 前序）、`interaction`（重放整条状态链）。
任一不满足 → `ErrAuthorityUnavailable`。**这是链路重放校验，不是普通查询。**

### `cmd/fact-authority/main.go`（96 行）与 `cmd/fact-dispatch/main.go`（78 行）

- 两者都做 env 校验：`fact-authority:54-56`（`COMMENT_FACT_AUTHORITY_TOKEN` **长度 ≥ 32**）；
  `fact-dispatch:56-58`（`DC_PLATFORM_SERVICE_TOKEN` **长度 ≥ 32**、8s HTTP client）
- `fact-authority:79-80` `ReadHeaderTimeout: 3s` / `WriteTimeout: 5s`；`:92-94` 5s 优雅停机
- `fact-dispatch:26` `-once` 单次模式（适合 CronJob）
- `:71` / `:77` 都用 `model.NewCommentModel(db)` 重新构造一个**不带缓存、不带 Kafka、不带 rpc client** 的模型实例

### 3 个 `migrations/*.sql`（DDL 真源，Go 侧手工维护）

| 文件 | 内容 |
|---|---|
| `001:3-8` | `comment_domain_fact_outbox` 基础 4 列（`event_id varchar(128) PK`、`event_type varchar(64)`、`payload jsonb`、`created_at timestamptz DEFAULT CURRENT_TIMESTAMP`） |
| `001:12-22` | `comment_like` 全表：`id bigint GENERATED BY DEFAULT AS IDENTITY PK`、`state **integer** NOT NULL DEFAULT 0`、`CONSTRAINT idx_user_comment_like UNIQUE (user_id, comment_id)` |
| `002:3-6` | `+ aggregate_id varchar(128)` / `+ fact_version bigint` / `+ delivery_envelope jsonb`（均 `IF NOT EXISTS`） |
| `002:8-11` | `comment_fact_stream(aggregate_id varchar(128) PK, version bigint NOT NULL CHECK (version BETWEEN 1 AND 9007199254740991))` |
| `002:13-17` | 唯一索引 `uk_comment_fact_stream(aggregate_id, fact_version)` + **部分索引** `((payload->>'aggregate_id')) WHERE delivery_envelope IS NULL` |
| `002:21-27` | 🔴 **`DO $$ … $$` 块**加 `NOT VALID` CHECK `comment_new_fact_has_dc_wire` |
| `003:3-10` | `+ delivery_status **smallint** NOT NULL DEFAULT 0`、`+ retry_count **integer** NOT NULL DEFAULT 0`、`technical_receipt_id varchar(128) DEFAULT ''`、`technical_input_hash char(64) DEFAULT ''`、`technical_offset bigint DEFAULT 0`、两个 `timestamptz` |
| `003:12-13` | 复合索引 `(delivery_status, created_at, event_id)` |
| `003:15-32` | 不可变触发器 `comment_fact_identity_immutable`（BEFORE UPDATE，6 个身份列任一变化即 `RAISE EXCEPTION 'comment fact envelope is immutable'`） |

⚠️ **列类型自相矛盾**：`003` 写 `smallint`/`integer`，但 `domain_fact.go:22-23` 的 `DeliveryStatus int32` /
`RetryCount int32` **没有 `type` tag** ⇒ GORM 的 `AutoMigrate`（`db.go:15`）会建成 **bigint**。
`like-rpc` 已经为同款问题踩过（`service/like/like-rpc/src/main/resources/db/migration/V4__create_like_domain_fact_outbox.sql:13-15,25-26`
明确选 BIGINT 并写明理由）。⇒ comment 的 RAG DDL **必须选一个并显式记录**，见 §12.7 #3。

### 3 个 `_test.go`（默认不跑，必须转成 RAG 测试）

三者**全部依赖真实 PostgreSQL**，无 DSN 时 `t.Skip`：
- `domain_fact_test.go`（256 行）：重投幂等、4 次状态迁移产出 4 条 fact 且版本严格 `1..4`、
  乱序消息（`msgID < last_operation_id`）被当 no-op、legacy 行拒绝出 fact、`uk_comment_fact_stream` 冲突保护
- `fact_delivery_logging_test.go`（73 行）：投递日志必须落在**注入的** logger 上，且含
  `comment.fact_delivery.started` / `.accepted` 与 `duration_ms`
- `fact_process_fixture_test.go`（89 行）：跨进程夹具（ready/release 文件 + 4 分钟等待），为 P2/P3 供数

⚠️ **`likemq_test.go` 那种「事务回滚 → 不 ack」的强度验证在 comment 侧没有对等物**，
但 `audit_consumer.go` 的四条出口必须配**真 PG 集成测试**（AGENTS.md §3.3：纯 Mockito 测不出事务与位点语义）。

### `commentservice/comment_service.go` + `pb/*.pb.go`（生成物，不迁移）

`commentservice` 是 goctl 生成的客户端桩（`:33-41` 接口、`:48-52` 工厂、7 个方法每次调用现场 `NewCommentServiceClient`）。
Java 侧由 `comment-proto` 的 `protobuf-maven-plugin` 生成 `CommentServiceGrpc.CommentServiceBlockingStub`，
api 侧 `client/CommentRpcClient` 持有它、rpc 侧 `service/impl/CommentServiceGrpcImpl` 继承 `CommentServiceImplBase`。
**不建任何对应 Java 文件**（§14.4）。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `comment.go:27-59` 进程装配 | `CommentRpcApplication` | ABSORB | Spring Boot 启动；`@Import` + `GrpcServerConfigurer` 见下 |
| `comment.go:33` `DisableNativeRpcTimeout` | `RpcServerInterceptorFactory` + `observability.rpc-timeout` | ADAPT | 语义由 gRPC interceptor 承担，**必须真的 `intercept()`**（`MessageRpcApplication.java:104-108`） |
| `comment.go:54` `AddUnaryInterceptors` | 同上 | REUSE | `RpcServerInterceptorFactory.java:40-47` 产出 `ServerInterceptor` Bean |
| `comment.go:36` `InitMetrics` 的注销副作用 | `metrics/CommentRpcMetrics` + Actuator 配置 | ADAPT | Go 注销 `go_*`/`process_*`；Micrometer 默认暴露 ⇒ 需显式关闭（§12.7 #2） |
| `comment.go:38-41` `:9091` promhttp | Actuator `management.endpoints.web.exposure` + 独立 management port | UNKNOWN | comment-rpc **原本零 HTTP 端口**；见 §12.7 #6 |
| `comment.go:47-52` reflection（Dev/Test） | devh starter 默认注册 | ABSORB | devh 默认即注册 reflection（`message-rpc/application.yml:75` 已记录该结论） |
| `etc/comment.yaml:2,3-6` `ListenOn`+Etcd | `application.yml` `grpc.server.port` + `bootstrap/NacosRegistration` | ADAPT | Etcd → Nacos，先例 `message-rpc/bootstrap/NacosRegistration.java:19-49` |
| `config/config.go:9-22` `KqPusherConf`/`KqConsumerConf` | `config/CommentRpcProperties` + `config/CommentKafkaTopics` | ADAPT | 生产端**全仓无先例**；topic 用 `KafkaTopics` 常量类是 like-rpc 先例 |
| `config/config.go:11-13` `DB` | `application.yml` `spring.datasource` + Hikari | ADAPT | Go 未设连接池上限 ⇒ RAG 侧沿用 `message-rpc` 的 `maximum-pool-size:100 / minimum-idle:10`（该域对应 Go 的 `MaxOpen=100`，comment 侧 Go 无此设置 ⇒ 取值待拍板） |
| `config/config.go:18` `BizRedis` | `application.yml` `spring.data.redis` | ADAPT | Lettuce 单节点，先例 `like-rpc/application.yml:63-73` |
| `svc/service_context.go:26-38` 依赖持有 | Spring 构造注入 | ABSORB | 全部依赖声明式注入，不建 `ServiceContext` 类 |
| `svc/service_context.go:29` 硬编码黑名单 | `service/CommentSensitiveFilter` | ADAPT | 词库**不是配置**，但 Java 侧建议外提到 `@ConfigurationProperties`（§3.2 留痕） |
| `svc/service_context.go:28` `kq.NewPusher` | `service/CommentAuditPublisher`（内持 `KafkaTemplate`） | NEW | 生产端必须显式选同步/异步语义（Go `Push` 是**同步阻塞**写），这个决定需要一个归属点 |
| `commentservice/comment_service.go` 客户端桩 | `comment-proto` 生成的 `CommentServiceBlockingStub` | ABSORB | 生成物，不迁移（§14.4） |
| `server/comment_service_server.go:25-57` 7 个 thin wrapper | `service/impl/CommentServiceGrpcImpl` | MERGE | Go 侧 server 层零逻辑；Java 侧一个类 7 个方法（`LikeServiceGrpcImpl.java:46-56` 同型） |
| 7 个 logic 的指标 defer | `metrics/CommentRpcMetrics` + 每方法首尾两行 | SPLIT | 埋点**横切**，放独立 metrics 类（`MessageMetrics.java:41-42` 同型） |
| 7 个 logic 的手工 span | **不迁移** | — | `trace/trace.go` 零调用点 ⇒ Go 侧是 no-op tracer（§12.3） |
| `logic/create_comment_logic.go:34-141` | `service/CommentDomainService.createComment` | KEEP | 单用例，与 users/article 的 per-method DomainService 同形 |
| `logic/delete_comment_logic.go:33-135` | `service/CommentDomainService.deleteComment` | KEEP | 同上（鉴权在事务外 + 事务内各一次） |
| `logic/get_comment_logic.go:32-177` | `service/CommentDomainService.getComment` | KEEP | 同上（1s 超时 + 缓存读穿 + list_size gauge） |
| `logic/like_comment_logic.go:31-97` | `service/CommentDomainService.likeComment` | KEEP | 同上 |
| `logic/manage_comment_logic.go:33-108` | `service/CommentDomainService.manageComment` | KEEP | 同上 |
| `logic/report_comment_logic.go:33-100` | `service/CommentDomainService.reportComment` | KEEP | 同上 |
| `logic/update_subject_state_logic.go:33-114` | `service/CommentDomainService.updateSubjectState` | KEEP | 同上 |
| `model/comment.go` 的 3 个事务方法 | `service/CommentTransactionalService` | **SPLIT** | 事务边界**逐方法不同**（`GetComment` 不开事务、`LikeComment` 的 `GetOwnerId` 在事务外、`DeleteComment` 的鉴权在事务内外各一次）⇒ 不能把 `@Transactional` 挂在 DomainService 上 |
| `model/comment.go` 的 10 个非事务方法 | `mapper/*Mapper` | ADAPT | MyBatis-Plus `@Select`/`@Update` 注解式 SQL；`RowsAffected == 0 → sentinel` 的语义逐条保留 |
| `model/comment.go:111-123` `ON CONFLICT DO UPDATE` 计数自增 | `mapper/SubjectMapper.upsertCommentSubject` | ADAPT | 必须逐字写 `total_count = subject.total_count + 1`（PG ON CONFLICT 里 `subject.` 前缀指向既有行） |
| `model/comment.go:157-182` 位运算更新 | `mapper/CommentIndexMapper.setAttributeBit` | ADAPT | `attribute | ?` / `attribute & ~?` |
| `model/comment.go:242-250` `ON CONFLICT DO NOTHING` | `mapper/ReportRecordMapper.insertIgnoreConflict` | ADAPT | ⚠️ **不指定 Columns** 才能复现「撞任何唯一约束都静默成功」 |
| `model/comment.go:258` / `:489` `FOR UPDATE` | `mapper/*Mapper` 的 `@Select("... FOR UPDATE")` | ADAPT | 行级悲观锁必须在 SQL 里显式写 |
| `model/comment.go:116-120` `INSERT..ON CONFLICT..RETURNING` | `mapper/CommentFactStreamMapper.nextVersion` | ADAPT | MyBatis-Plus `@Select` 不能带 `RETURNING` ⇒ 需 `@Options`/自定义方法；实现方式待验证 |
| `model/comment.go:222-240` `normalizeCommentMeta` | `service/CommentMetaNormalizer` 或 `CommentDomainService` 私有 | KEEP | 与插入强绑定，单调用方 |
| `model/vars.go:5-9` 3 个 sentinel | `exception/CommentExceptions`（4 个嵌套类） | ADAPT | 先例 `MessageExceptions.java:16,26-53`；**必须按业务含义分 4 类而非 1 类**（`ErrorSubjectNotFound`/`ErrorCommentNotFound`/`ErrorCommentForbidden`/DB 错误各自映射到不同 gRPC 业务码） |
| `model/data_model.go` 6 个实体 | `domain/*Entity` | ADAPT | `@TableName` + `@TableId`；jsonb 列挂 `JsonbTypeHandler`（`comment_content.meta`、`comment_domain_fact_outbox.payload/delivery_envelope`） |
| `model/db.go:15` `AutoMigrate(6 表)` + `log.Fatalln` | `resources/db/migration/V1..V8` + `bootstrap/CommentSchemaGate` | ADAPT | Go 的「建表失败即退出」在 Java 侧变成 Flyway + 启动校验（`LikeSchemaGate.java:12-37` 是同款先例） |
| `cache/*` 6 文件的读穿与失效 | `service/CommentCacheService` | MERGE | 6 个 Go 文件是**同一个 `CommentCache` struct 的方法集**（`comment_cache.go:9-18`），不是 6 个组件 |
| `cache/keys.go:5-20` 4 个 key 函数 | `service/CommentRedisKeys` | KEEP | key 格式是**缓存契约**，独立变化原因；先例 `like-rpc/service/LikeRedisKeys.java` |
| `cache/*.go` 的 `singleflight.Group` | 🔴 **无 RAG 对应物** | UNKNOWN | 全仓零命中。替代方案（`ReentrantLock`+双检 / 自建 CAS）**都会改变并发语义**，需拍板（§12.7 #7） |
| `utils/sensitive.go` 的 Aho-Corasick | `service/CommentSensitiveFilter` | ADAPT | 词库只有 2 个词；`contains` 扫描与 AC 机在「是否命中」上等价，但**首个命中词的选取顺序**可能不同（§12.7 #8） |
| `common/types/kqueue.go:3-15` `CommentKafkaMsg` | `service/CommentAuditMessage`（record） | ADAPT | producer/consumer 共享契约，非 HTTP DTO ⇒ 落 `service/` |
| `mqs/audit_consumer.go:33-68` `Consume` | `service/CommentAuditListener`（`@KafkaListener`） | ADAPT | 🔴 包归属见 §12.7 #9；四条出口的方向必须逐条保留 |
| `mqs/audit_consumer.go:79-132` 通知与预览 | `service/CommentAuditListener` 私有方法 | KEEP | 与消费入口强绑定；`previewComment` 的 40-rune 截断是 L1 |
| `mqs/audit_consumer.go:70-77` consumer span | `common/observability/MqConsumer.traceConsumer` | REUSE | `MqConsumer.java:71-119`；⚠️ Go 侧**真的接了**（`:34` 有调用），与 `trace/trace.go` 的死代码不同 |
| `model/domain_fact.go:89-142` `appendCommentFact` | `service/CommentDomainFactService` | ADAPT | 先例 `like-rpc/service/LikeDomainFactService.java:31-47` |
| `model/fact_delivery.go:20-48` `validCommentDelivery` | `service/CommentDeliveryValidator` | ADAPT | 先例 `like-rpc/service/LikeDeliveryValidator.java` |
| `model/fact_delivery.go:53-127` `DispatchFactOnce` | `service/CommentFactDeliveryService` | ADAPT | 先例 `like-rpc/service/LikeFactDispatchService.java` |
| `model/fact_authority.go:51-187` `AuthoritativeFact` | `service/CommentAuthorityLookupService`（实现 `AuthorityHandler.AuthorityLookup`） | ADAPT | 先例 `like-rpc/service/LikeAuthorityLookupService.java`；`AuthorityHandlerFactory.java:39-49` 只提供注册，**lookup 是业务特定的**（该文件 `:21-22` 明写） |
| `cmd/fact-dispatch/main.go:52-77` P3 进程 | `bootstrap/CommentFactDispatchScheduler`（驱动 `Dispatcher.run`） | ADAPT | 🔴 like 侧的同名落点是**未实现骨架**；`Dispatcher.run` 返回 `DispatcherHandle` 供 `@PreDestroy`（`Dispatcher.java:228,255`） |
| `cmd/fact-authority/main.go:50-95` P2 进程 | `bootstrap/CommentAuthorityEndpointConfig` | ADAPT | 🔴 like 侧同名落点**未实现且自记端口待拍板**；`AuthorityHandlerFactory.AUTHORITY_PATH = "/internal/v1/community/facts"` |
| `cmd/*` 的 `Token.length() >= 32` 校验 | 落到 `bootstrap/CommentSchemaGate` 或独立启动校验 | UNKNOWN | Go 侧是进程级 env 硬校验（失败退出）；Java 侧放不放、失败行为如何，需拍板 |
| `cmd/fact-dispatch/main.go:26` `-once` | 配置项 `comment.fact.dispatch.once` | UNKNOWN | 与 `FileTree/like/rpc.md` §12.1 的同一问题：是否保留 CronJob 部署形态 |
| 3 个 `migrations/*.sql` | `resources/db/migration/V6..V8` | ADAPT | 逐列照抄；🔴 列类型分歧见 §12.7 #3；`DO $$` 块见 §12.7 #3 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/comment/comment/rpc/
├── CommentRpcApplication.java
├── bootstrap/
│   ├── NacosRegistration.java              # 注册到 Nacos（替代 Go 的 etcd）
│   ├── CommentSchemaGate.java               # 启动后校验 7 张表确实存在（替代 AutoMigrate 的 log.Fatalln）
│   ├── CommentFactDispatchScheduler.java    # P3：驱动 common Dispatcher 循环，@PreDestroy 停
│   └── CommentAuthorityEndpointConfig.java  # P2：注册 /internal/v1/community/facts Servlet
├── client/
│   ├── MessageRpcClient.java               # @GrpcClient("message-rpc") SendNotification
│   └── ArticleRpcClient.java               # @GrpcClient("article-rpc") GetArticle
├── config/
│   ├── CommentRpcProperties.java           # DB / Redis / Kafka / MessageRpc / ArticleRpc / fact dispatch / authority
│   ├── CommentKafkaTopics.java             # comment-audit-topic 常量类（避免 SpEL 取 Bean，见 like 的 EL1058E 教训）
│   └── Jackson2ObjectMapperConfiguration.java  # Boot 4 下显式提供 ObjectMapper Bean
├── domain/
│   ├── SubjectEntity.java
│   ├── CommentIndexEntity.java
│   ├── CommentContentEntity.java           # meta 是 jsonb → 挂 JsonbTypeHandler + autoResultMap
│   ├── CommentLikeEntity.java
│   ├── ReportRecordEntity.java
│   ├── CommentDomainFactOutboxEntity.java  # payload / delivery_envelope 是 jsonb
│   └── CommentFactStreamEntity.java
├── exception/
│   └── CommentExceptions.java              # SubjectNotFound / CommentNotFound / CommentForbidden / DbError
├── mapper/
│   ├── SubjectMapper.java
│   ├── CommentIndexMapper.java
│   ├── CommentContentMapper.java
│   ├── CommentLikeMapper.java
│   ├── ReportRecordMapper.java
│   ├── CommentDomainFactOutboxMapper.java
│   ├── CommentFactStreamMapper.java
│   └── JsonbTypeHandler.java
├── metrics/
│   └── CommentRpcMetrics.java              # 5 个 Micrometer 指标 + result/module 标签的 3 处不一致
└── service/
    ├── CommentDomainService.java           # 7 个 RPC 用例（非事务）
    ├── CommentTransactionalService.java    # insertCommentTx / deleteCommentTx / likeCommentTx（@Transactional）
    ├── CommentAuditMessage.java            # CommentKafkaMsg 的 11 字段契约（record）
    ├── CommentAuditPublisher.java          # Kafka 生产端（同步/异步语义的决策点）
    ├── CommentAuditListener.java           # @KafkaListener：敏感词 → InsertCommentTx → 失效 → 通知
    ├── CommentSensitiveFilter.java         # 2 词敏感词判定
    ├── CommentCacheService.java            # Redis 读穿（subject / ids-page / index / content）+ 失效
    ├── CommentRedisKeys.java               # 4 个 key 构造（缓存契约）
    ├── CommentDomainFactService.java       # appendCommentFact（26 字段 payload + 9 字段信封 + legacy 守卫）
    ├── CommentDeliveryValidator.java       # validCommentDelivery
    ├── CommentFactDeliveryService.java     # DispatchFactOnce（SKIP LOCKED + 前序屏障 + 回执）
    ├── CommentAuthorityLookupService.java  # AuthoritativeFact（链路重放校验）
    └── impl/
        └── CommentServiceGrpcImpl.java     # @GrpcService，7 个方法，单一错误出口

resources/
├── application.yml                          # spring.flyway.table: flyway_schema_history_comment
└── db/migration/
    ├── V1__create_comment_subject.sql
    ├── V2__create_comment_index.sql
    ├── V3__create_comment_content.sql
    ├── V4__create_comment_like.sql
    ├── V5__create_comment_report_record.sql
    ├── V6__create_comment_domain_fact_outbox.sql
    ├── V7__create_comment_fact_stream.sql
    └── V8__align_comment_fact_delivery_with_go.sql
```

**共 33 个 Java 文件**（1 入口 + 4 bootstrap + 2 client + 3 config + 7 domain + 1 exception + 8 mapper + 1 metrics + 12 service + 1 impl）。
Go 侧 36 个文件 ⇒ Java 33 个，**但不是「一比一」**：6 个 cache 文件合成 1 个、8 个 logic 合成 2 个 service、
2 个 `cmd/*` 合成 2 个 bootstrap 组件、3 个 `migrations/*.sql` 合成 3 个 Flyway 脚本而 3 张业务表拆成 5 个脚本。

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `CommentRpcApplication` | `@SpringBootApplication`（只扫本模块）+ `@ConfigurationPropertiesScan` + `@MapperScan` + `@Import{SnowflakeBean, LoggerFactoryBean, RpcServerInterceptorFactory, ObservabilityProperties, ObservabilityLifecycle, DeadlineQueryTimeoutInjector, SqlTimeoutStatusTranslator}` + `GrpcServerConfigurer` | `comment.go:27-59` | 框架约定。🔴 **`@MapperScan` 与 `server.intercept(...)` 少一行都会静默失效**（`MessageRpcApplication.java:47-62,99-103`） |
| `bootstrap/NacosRegistration` | `ApplicationReadyEvent` 后 `registerInstance("comment-rpc", DEFAULT_GROUP, ...)`，`@PreDestroy` 反注册；**Bean 名必须显式指定** | `comment.go:47-53` 的 etcd 注册 | 三份 rpc 先例（message/like/article） |
| `bootstrap/CommentSchemaGate` | `ApplicationReadyEvent` 后用 `JdbcTemplate` 校验 7 张表存在；**只 `LOG.error` 不阻止启动** | `model/db.go:15-18` | Java 侧引入了 Go 没有的新失败模式（Flyway 引擎在但自动配置没加载），先例 `LikeSchemaGate.java:17-34` |
| `bootstrap/CommentFactDispatchScheduler` | `@PostConstruct` 调 `Dispatcher.run(ctx, factDeliveryService::dispatchOnce, dcSender, logger, once)`，`@PreDestroy` 调 `handle.stop()` | `cmd/fact-dispatch/main.go:52-77` | 启动期初始化的循环，需要 `DispatcherHandle` 生命周期管理。🔴 like 侧同名文件**是未实现骨架**，本文件按推导给出职责，实施时需先确认 `Dispatcher.run` 的签名 |
| `bootstrap/CommentAuthorityEndpointConfig` | `@Bean ServletRegistrationBean` = `AuthorityHandlerFactory.register("rtw.comment-rpc", token, lookup)` | `cmd/fact-authority/main.go:50-95` | 启动期注册。🔴 **端口待拍板**（like 侧自记为「wire 面变更」） |
| `client/MessageRpcClient` | `@GrpcClient("message-rpc")` → `SendNotification` | `svc/service_context.go:36` + `audit_consumer.go:101` | 下游 rpc 客户端，三份先例 |
| `client/ArticleRpcClient` | `@GrpcClient("article-rpc")` → `GetArticle(articleId, incrView=false)` | `svc/service_context.go:37` + `subject_fallback.go:17` | 同上。⚠️ RAG 的 `GetArticleRequest` 多一个 `public_only` 与 `requester_id` 字段（`article-proto/article.proto:55-60`），**必须显式置默认值**才与 Go 等价 |
| `config/CommentRpcProperties` | `spring.datasource` / `spring.data.redis` 之外的业务配置：kafka topic、fact dispatch 的 `once`、authority 的 `token`/`port` | `config/config.go:9-22` | 配置绑定 |
| `config/CommentKafkaTopics` | `commentAuditTopic()` 常量 | `comment.yaml:26,32` | 先例 `like-rpc/config/LikeKafkaTopics.java`。⚠️ `@KafkaListener` 里必须用 `${}` 占位符而非 `#{@bean}`（`LikeUpdateKafkaListener.java:77-80` 记录的 EL1058E 教训） |
| `config/Jackson2ObjectMapperConfiguration` | 显式装配 `ObjectMapper` Bean | — | Boot 4 下 Jackson 3 的 `ObjectMapper` 不再自动提供（`hot-rpc` / `like-rpc` 曾因此**硬启动失败**） |
| `domain/*Entity`（7 个） | 6 张业务表 + fact outbox/stream 的 `@TableName` 实体 | `model/data_model.go` + `model/domain_fact.go:15-39` | 每个实体对应一张真表；两个 jsonb 字段必须挂 `JsonbTypeHandler` + `@TableName(autoResultMap = true)` |
| `exception/CommentExceptions` | 4 个嵌套 unchecked 异常：`SubjectNotFound` / `CommentNotFound` / `CommentForbidden` / `DbError` | `model/vars.go:5-9` + 各方法返回的裸 DB 错误 | 先例 `MessageExceptions.java:16,26-53`。**必须分 4 类**：三者映射到 1012 / 1013 / 1008 三个不同业务码，DB 错误映射到 5003/5002/5001 |
| `mapper/*Mapper`（7 个） | 全部持久化操作，含 3 个事务方法里的手写 SQL | `model/comment.go` 的 13 个方法 | 每表一个 Mapper；动态 SQL 用 `@Select("<script>")`，`<>` 需 CDATA 孪生常量 |
| `mapper/JsonbTypeHandler` | `ps.setObject(i, json, Types.OTHER)` | `comment_content.meta` 等 jsonb 列 | 三份先例（message / like / article 各一份） |
| `metrics/CommentRpcMetrics` | 5 个指标 + `observeRpc(action, startNanos, resultLabel)` + `setListSize` + `observePostgresError` + `observeRedisError` | `internal/metrics/metrics.go:9-38` + 各调用点 | 埋点横切，独立变化原因（指标名/tags 是契约）。⚠️ 必须复刻 `result` 标签集合的**不一致**与 `module="comment_list"` 的特例 |
| `service/CommentDomainService` | 7 个 RPC 用例（无 `@Transactional`） | 7 个 `internal/logic/*.go` | 单用例聚合；先例 `MessageDomainService` / `ArticleDomainService` / `LikeDomainService` |
| `service/CommentTransactionalService` | 3 个 `@Transactional` 方法 | `model/comment.go:28,252,472` 的三个 `Transaction(...)` | **必须独立**：事务边界逐方法不同，挂在 DomainService 上会把 `GetComment` 的只读路径也拖进事务 |
| `service/CommentAuditMessage` | 11 字段 record | `common/types/kqueue.go:3-15` | producer/consumer 共享契约 |
| `service/CommentAuditPublisher` | `KafkaTemplate.send(topic, json)`，**同步/异步语义在此决定** | `svc/service_context.go:28` + `create_comment_logic.go:126` | Go `Push` 是同步阻塞写；Java 异步 send 的失败语义完全不同，这个决定需要归属点 |
| `service/CommentAuditListener` | `@KafkaListener`；4 条出口（解析失败 return / 事务失败 throw / 失效失败只记日志 / 成功 return）；`status==0` 才发通知 | `mqs/audit_consumer.go:33-132` | 消费入口 + 通知编排 |
| `service/CommentSensitiveFilter` | 2 词匹配 | `utils/sensitive.go:33-42` + `service_context.go:29` | 独立变化原因（词库） |
| `service/CommentCacheService` | 4 类读穿 + 2 类失效 | `cache/*.go` 的 12 个方法 | Go 侧就是**同一个 struct 的方法集** |
| `service/CommentRedisKeys` | 4 个 key 构造 | `cache/keys.go:5-20` | key 格式是缓存契约（🔴 `SubjectKey` 漏 targetType 这个缺陷必须原样保留） |
| `service/CommentDomainFactService` | `appendCommentFact`：26 字段 payload + 9 字段信封 + legacy 守卫 + 版本取号 | `model/domain_fact.go:89-142` | 先例 `LikeDomainFactService.java:31-47` |
| `service/CommentDeliveryValidator` | `validCommentDelivery` | `model/fact_delivery.go:20-48` | 先例 `like-rpc/service/LikeDeliveryValidator.java` |
| `service/CommentFactDeliveryService` | `dispatchOnce` | `model/fact_delivery.go:53-127` | 先例 `like-rpc/service/LikeFactDispatchService.java` |
| `service/CommentAuthorityLookupService` | 实现 `AuthorityHandler.AuthorityLookup`，三分支链路重放校验 | `model/fact_authority.go:51-187` | 先例 `like-rpc/service/LikeAuthorityLookupService.java` |
| `service/impl/CommentServiceGrpcImpl` | `@GrpcService` + `extends CommentServiceGrpc.CommentServiceImplBase`，7 个方法，单一 `invoke(...)` 错误出口 | `server/comment_service_server.go:25-57` + 7 个 logic 的错误返回 | 先例 `LikeServiceGrpcImpl.java:46-56`。🔴 错误出口必须走 `CommentGrpcError`（不能直接 `throw`） |
| `db/migration/V1..V5` | 5 张业务表 | `AutoMigrate` 的 6 张 + `report_record` | 🔴 Go 侧 `report_record` **从不被 AutoMigrate**（§12.7 #4），RAG 是否补建需拍板 |
| `db/migration/V6..V8` | fact outbox / fact stream / 投递元数据 + 索引 + 触发器 | `migrations/001..003.sql` | 逐列照抄；🔴 列类型与 `DO $$` 块见 §12.7 #3 |
| `application.yml` | 含 `spring.flyway.table: flyway_schema_history_comment` | `etc/comment.yaml` | 🔴 **必须独立账本表名**：这是第 4 个连 `first_db`/`public` 的模块（先例 `message-rpc/application.yml:58`、`like-rpc/application.yml:61`），按 AGENTS.md §6.3 问题 4 不加就撞号 |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `comment.go:27-59` | `CommentRpcApplication` | ABSORB | 进程装配由框架承担 |
| `comment.go:33,54` 超时 + unary interceptor | `@Import(RpcServerInterceptorFactory)` + `GrpcServerConfigurer` | ADAPT | **必须真的 `intercept()`**，否则 span/超时静默失效 |
| `comment.go:36` `InitMetrics` | `metrics/CommentRpcMetrics` + Actuator | ADAPT | Micrometer 自动注册；Go 的 `Unregister(GoCollector)` 副作用需显式处理 |
| `comment.go:38-41` `:9091` promhttp | Actuator + management port | UNKNOWN | rpc 进程新增 HTTP 端口，wire 面变更，待拍板 |
| `comment.go:43-45` `kq` queue | Spring Kafka listener container | ADAPT | 消费组/topic/offset 落 `application.yml` |
| `comment.go:47-52` server 注册 + reflection | `@GrpcService` + devh 默认 reflection | ABSORB | 反射能力由 starter 提供 |
| `etc/comment.yaml:2` `ListenOn 9001` | `application.yml` `grpc.server.port` | ADAPT | 配置替代 |
| `etc/comment.yaml:3-6` Etcd | `bootstrap/NacosRegistration` | ADAPT | Etcd → Nacos，三份先例 |
| `etc/comment.yaml:8-9` `DB` | `spring.datasource` | ADAPT | Go 未设池上限，RAG 取值待拍板 |
| `etc/comment.yaml:23-35` KqPusher/KqConsumer | `config/CommentKafkaTopics` + `application.yml` | ADAPT | 生产端无 RAG 先例，同步/异步语义待拍板 |
| `etc/comment.yaml:37-40` `BizRedis` | `spring.data.redis` | ADAPT | Lettuce 单节点 |
| `etc/comment.yaml:42-52` MessageRpc/ArticleRpc | `application.yml` `grpc.client.*` + `client/*` | ADAPT | 服务发现寻址 |
| `config/config.go:9-22` | `config/CommentRpcProperties` | ADAPT | 绑定机制换 Spring |
| `svc/service_context.go:26-38` | Spring 构造注入 | ABSORB | 不建 `ServiceContext` |
| `svc/service_context.go:28` `kq.NewPusher` | `service/CommentAuditPublisher` | NEW | 生产端语义需要归属点 |
| `svc/service_context.go:29` 黑名单 | `service/CommentSensitiveFilter` | ADAPT | 词库硬编码在 Go 侧；建议外提到配置并留痕 |
| `commentservice/comment_service.go` | `comment-proto` 生成的 stub | ABSORB | 生成物，§14.4 |
| `pb/comment.pb.go`、`pb/comment_grpc.pb.go` | `comment-proto` 生成物 | ABSORB | 生成物，§14.4 |
| `server/comment_service_server.go:25-57` | `service/impl/CommentServiceGrpcImpl` | MERGE | Go 侧 7 个 thin wrapper 合为一个 `@GrpcService` |
| `logic/create_comment_logic.go:34-157` | `service/CommentDomainService#createComment` | KEEP | 单用例（含 resolveOwnerID 私有方法） |
| `logic/delete_comment_logic.go:33-135` | `service/CommentDomainService#deleteComment` | KEEP | 单用例 |
| `logic/get_comment_logic.go:32-177` | `service/CommentDomainService#getComment` | KEEP | 单用例（1s 超时 + loadSubject 私有方法） |
| `logic/like_comment_logic.go:31-97` | `service/CommentDomainService#likeComment` | KEEP | 单用例 |
| `logic/manage_comment_logic.go:33-108` | `service/CommentDomainService#manageComment` | KEEP | 单用例 |
| `logic/report_comment_logic.go:33-100` | `service/CommentDomainService#reportComment` | KEEP | 单用例 |
| `logic/update_subject_state_logic.go:33-114` | `service/CommentDomainService#updateSubjectState` | KEEP | 单用例 |
| `logic/subject_fallback.go:12-38` | `CommentDomainService` 私有 + `client/ArticleRpcClient` | SPLIT | 回落逻辑服务内，跨服务调用由 client 承担 |
| `logic/*` 的 `metrics` defer | `metrics/CommentRpcMetrics` | SPLIT | 埋点横切，独立变化原因 |
| `logic/*` 的 `otel.Tracer` span | **不迁移** | — | `trace/trace.go` 零调用点 ⇒ Go 侧是 no-op |
| `model/comment.go:28-155` `InsertCommentTx` | `service/CommentTransactionalService#insertCommentTx` + `mapper/*` | SPLIT | 事务边界独立 |
| `model/comment.go:252-353` `DeleteCommentTx` | `service/CommentTransactionalService#deleteCommentTx` | SPLIT | 同上 |
| `model/comment.go:472-598` `LikeCommentTx` | `service/CommentTransactionalService#likeCommentTx` | SPLIT | 同上 |
| `model/comment.go:157-182,184-201,203-220` | `mapper/CommentIndexMapper` + `mapper/SubjectMapper` | ADAPT | 单条 UPDATE，`RowsAffected==0 → sentinel` 逐条保留 |
| `model/comment.go:242-250` `InsertReport` | `mapper/ReportRecordMapper` | ADAPT | `ON CONFLICT DO NOTHING` 不带 Columns |
| `model/comment.go:355-416` `GetReplyIDsByPage` | `mapper/CommentIndexMapper` | ADAPT | 排序分支 + `Limit>100→100` + 5 条参数校验 |
| `model/comment.go:422-470,600-631` 两个 BatchGet | `mapper/CommentContentMapper` + `mapper/CommentIndexMapper` | SPLIT | 两个不同表 |
| `model/comment.go:633-668` 两个 FindOne | `mapper/*Mapper` | ADAPT | `ErrRecordNotFound → sentinel` |
| `model/comment.go:222-240` `normalizeCommentMeta` | `CommentTransactionalService` 私有 | KEEP | 单调用方 |
| `model/comment.go:111-123` `ON CONFLICT DO UPDATE` | `mapper/SubjectMapper#upsertCommentSubject` | ADAPT | `total_count = subject.total_count + 1` 必须逐字 |
| `model/comment.go:116-120` `INSERT..RETURNING` | `mapper/CommentFactStreamMapper#nextVersion` | ADAPT | ⚠️ `@Select` 不能带 `RETURNING`，实现方式待验证 |
| `model/db.go:10-19` `InitDB` | `db/migration/V1..V8` + `bootstrap/CommentSchemaGate` | SPLIT | 建表与校验拆两处（先例 `LikeSchemaGate`） |
| `model/vars.go:5-9` 3 sentinel | `exception/CommentExceptions` | ADAPT | 分 4 类（3 sentinel + DB 兜底） |
| `model/data_model.go:5-101` 6 实体 + 2 值类型 | `domain/*Entity` + `service/CommentAuditMessage` | SPLIT | 实体与值对象分开；`ReplySort`/`GetReplyIDsPageReq` 是方法参数，不是实体 |
| `model/domain_fact.go:15-39` 2 实体 | `domain/CommentDomainFactOutboxEntity` + `domain/CommentFactStreamEntity` | SPLIT | 每表一类 |
| `model/domain_fact.go:55-80` `commentFact` 26 字段 | `CommentDomainFactService` 私有 | KEEP | 只在 fact 链路内使用，不进 domain |
| `model/domain_fact.go:89-142` `appendCommentFact` | `service/CommentDomainFactService` | ADAPT | 先例 `LikeDomainFactService` |
| `model/domain_fact.go:82-87` `subjectRef` | `CommentDomainFactService` 私有 | KEEP | `"rtw.identity/platform/" + uid` |
| `model/fact_delivery.go:20-48` `validCommentDelivery` | `service/CommentDeliveryValidator` | ADAPT | 先例 `like-rpc/service/LikeDeliveryValidator.java` |
| `model/fact_delivery.go:53-127` `DispatchFactOnce` | `service/CommentFactDeliveryService` | ADAPT | 先例 `LikeFactDispatchService` |
| `model/fact_delivery.go:129-134` `decodeCommentDelivery` | `CommentDeliveryValidator` 私有 | MERGE | 单调用方 |
| `model/fact_authority.go:12-40,42-47,51-187,189-209,211-213` | `service/CommentAuthorityLookupService` | MERGE | 6 个函数同属一条链路重放校验，单组件内聚 |
| `cmd/fact-dispatch/main.go:52-77` | `bootstrap/CommentFactDispatchScheduler` | ADAPT | 合并 Go P3；like 侧同名是未实现骨架 |
| `cmd/fact-authority/main.go:50-95` | `bootstrap/CommentAuthorityEndpointConfig` | ADAPT | 合并 Go P2；**端口待拍板** |
| `cmd/*` 的 env 校验（token≥32 / dsn / endpoint） | 落到 `bootstrap/CommentSchemaGate` 或启动校验 | UNKNOWN | 失败行为（退出 vs 只告警）未定 |
| `cmd/fact-dispatch/main.go:26` `-once` | `comment.fact.dispatch.once` | UNKNOWN | CronJob 部署形态是否保留 |
| `metrics/metrics.go:9-38` 5 个向量 | `metrics/CommentRpcMetrics` | ADAPT | Micrometer 1:1 复刻指标名/tags |
| `metrics/metrics.go:41-49` `InitMetrics` 的 Unregister | Actuator 配置 | ADAPT | Go 注销 `go_*`/`process_*`，Micrometer 需显式关闭 |
| `trace/trace.go` 全文件 | **不迁移** | — | 零调用点，no-op tracer |
| `utils/sensitive.go:10-42` | `service/CommentSensitiveFilter` | ADAPT | AC 机 → 2 词扫描；命中词顺序待确认 |
| `cache/redis.go:12-28` `InitRedis` | `spring.data.redis` + Lettuce | ADAPT | 3s Ping 失败 `log.Fatalln` → Java 启动失败（等价） |
| `cache/keys.go:5-20` | `service/CommentRedisKeys` | KEEP | key 是缓存契约 |
| `cache/comment_cache.go:9-18` struct | `service/CommentCacheService` | ABSORB | 依赖持有由构造注入承担 |
| `cache/comment_subject_cache.go:16-132` | `CommentCacheService` | MERGE | 同一 struct 的方法集 |
| `cache/comment_ids_page_cache.go:16-89` | `CommentCacheService` | MERGE | 同上 |
| `cache/comment_ids_page_cache.go:91-138` `preloadNextReplyIDsPage` | **不迁移** | — | 零调用点，§14.4 禁止为死代码预留结构 |
| `cache/comment_index_cache.go:14-119` | `CommentCacheService` | MERGE | 同上 |
| `cache/comment_content_cache.go:14-122` | `CommentCacheService` | MERGE | 同上 |
| `cache/comment_invalidation_cache.go:10-83` | `CommentCacheService` | MERGE | 同上 |
| `cache/*.go` 的 `singleflight.Group` | 🔴 **无对应物** | UNKNOWN | 全仓零命中；替代方案都会改变并发语义 |
| `common/types/kqueue.go:3-15` | `service/CommentAuditMessage` | ADAPT | 生产/消费共享契约 |
| `mqs/audit_consumer.go:33-68` `Consume` | `service/CommentAuditListener` | ADAPT | 4 条出口方向逐条保留 |
| `mqs/audit_consumer.go:70-77` `commentMessageAttrs` | `common/observability/MqConsumer.traceConsumer` | REUSE | `MqConsumer.java:71-119` |
| `mqs/audit_consumer.go:79-120` `notifyComment` | `CommentAuditListener` 私有 | KEEP | 单调用方 |
| `mqs/audit_consumer.go:122-132` `previewComment` | `CommentAuditListener` 私有 | KEEP | 40-rune 截断是 L1 |
| `migrations/001_comment_domain_fact_outbox.sql` | `db/migration/V6` + `V4` | SPLIT | 一个脚本含两张表，RAG 按表拆 |
| `migrations/002_comment_dc_wire.sql` | `db/migration/V6`（`ALTER`）+ `V7` | SPLIT | 新表独立脚本 |
| `migrations/003_comment_fact_delivery.sql` | `db/migration/V8` | ADAPT | 🔴 列类型分歧见 §12.7 #3 |
| `domain_fact_test.go` 等 3 个 `_test.go` | RAG 侧真 PG 集成测试 | — | Go 侧无 DSN 时 `t.Skip` ⇒ 默认不跑；RAG 侧必须**默认跑** |
| `COMMUNITY_FACTS_ACCEPTANCE.md`、`test-community-postgres.sh` | 不迁移 | — | 文档与脚本，属 Go 侧验收流程 |
| —— | `service/comment/comment-rpc/pom.xml` | **需先补齐** | 12 项依赖全缺 |
| —— | `application.yml` 的 `spring.flyway.table` | **必须新增** | 第 4 个共享 `first_db` 的模块，不加就撞号 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 7 个 RPC（`server/comment_service_server.go:25-57`）；5 个指标名 + label 集合（`metrics/metrics.go:9-38`）；7 张表结构 + 3 个索引 + 1 个 CHECK + 1 个触发器（`data_model.go`、`migrations/001..003`）；**34 处 `NewGrpcErr` 的 (码, 文案) 逐条录入 §12.3**；`CommentKafkaMsg` 11 字段（`kqueue.go:3-15`）；`commentFact` 26 字段（`domain_fact.go:55-80`）；`created_at` 格式 `"2006-01-02 15:04:05"`（`create:138`、`get:139`） |
| L2 业务语义有承载位置 | ✅ `CreateComment` **不写库**（`create:126-140`）；`SubjectCount` 恒 0（`create:139`）；`GetComment` 的 1s 超时（`get:49`）；敏感词只改 `status` 不改内容（`audit:41-46`）；只 `status==0` 发通知（`audit:61-63`）；给自己发通知被抑制（`audit:97-99`）；删除幂等（`comment.go:287`）；点赞 no-op 不产 fact（`comment.go:518,528,534,542`）；**无事务的 3 个方法**（`comment.go:157,184,242`）；`update_subject_state` 只允许 up 主（`update:85`）；article 回落只认 `targetType=="article"` 且 `authorId>0`（`subject_fallback.go:13,26`） |
| L3 技术语义有承载方案 | ✅ 3 个事务边界已拆到 `CommentTransactionalService`；`FOR UPDATE`（`comment.go:258,489`）与 `SKIP LOCKED`（`fact_delivery.go:64`）需显式写 SQL；Kafka 4 条出口方向（`audit_consumer.go:36-51`）；Redis 失效失败**只打日志不阻断**（`delete:122-127`、`like:89-91`、`audit:52-59`）；pipeline 回填失败不阻断（`comment_index_cache.go:103`、`comment_content_cache.go:106`）；SQL 超时需 `@Import{DeadlineQueryTimeoutInjector, SqlTimeoutStatusTranslator}` |
| 业务分支与异常路径已纳入 | ✅ 6 个 logic 的 `result ∈ {ok,biz_fail,sys_fail}` + `GetComment` 只有 2 值；`likecommentlogic.go:62-63` 的「日志记 5004、发出 2001」不一致；`subject_fallback` 出错返回原始 err 而非 fallbackErr；`InsertReport` 的静默吞冲突 |
| 框架吸收有机制依据 | ✅ 进程装配、依赖注入、`Etcd→Nacos`、`reflection`、`ServiceContext`、`CommentCache` struct、`kq queue` 生命周期 —— 每项都指出实际承担机制 |
| 复用组件已明确 | ✅ `Wire` / `Dispatcher` / `AuthorityHandler` / `AuthorityHandlerFactory` / `MqConsumer` / `SnowflakeBean` / `RpcServerInterceptorFactory` / `DeadlineQueryTimeoutInjector` / `JsonbTypeHandler` / `GoStylePagination` —— 每项给出打开过的路径行号 |
| 新增文件有职责依据 | ✅ `CommentTransactionalService`（事务边界逐方法不同）、`CommentRedisKeys`（key 是契约）、`CommentAuditPublisher`（生产端语义）、`CommentSchemaGate`（Flyway 新失败模式）、`CommentSensitiveFilter`（词库）—— 均非「Go 有同名文件」 |
| 合并有组织依据 | ✅ 6 个 cache 文件 → 1 个（Go 侧本就是同一 struct）；8 个 logic → 2 个 service（按事务边界切）；3 个 sql → 3 个 Flyway 脚本（按表拆） |
| 拆分可解释 | ✅ 事务方法从 DomainService 拆出；两个 jsonb 实体独立；fact 的 5 个职责（append / validate / dispatch / authority / decode）各归一个组件 |
| 无「为对应 Go 文件而建」的文件 | ✅ 未创建 `CommentCache.java` / `AuditConsumer.java` / `CommentModel.java` / `ServiceContext.java` / `Dispatcher.java`；`preloadNextReplyIDsPage`、`trace/trace.go` 明确**不迁移** |
| 未把未实现能力说成现有机制 | ✅ `like-rpc` 的 `LikeFactDispatchScheduler.java:3` 与 `LikeAuthorityEndpointConfig.java:3-4` 是**未实现骨架**这一点已在 §12.1 明写；本文件对 P2/P3 的推导标注为「待验证」而非「已有先例」 |
| 与 api 侧拓扑结论一致 | ✅ 与 `FileTree/comment/api.md` §12.7 同一结论：3 个 Go `main` → 1 个 JVM；`comment-common` 是唯一共用模块；proto `package` 偏离对两侧都成立 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **业务码塞不进 `grpc-status`** | 见 §12.1 风险点 2。**34 处** `NewGrpcErr` 的码值全部 > 16，grpc-java 无法在线路上表达。必须复用 `LikeGrpcError` 的 trailer 方案。**实现前阻塞项** |
| 2 | **Go 主动注销了 `go_*`/`process_*` 指标** | `metrics/metrics.go:42-43`。Micrometer 默认暴露 `jvm_*`/`process_*` ⇒ 照搬就是新增可观测面。**保留 Go 现状还是接受增强，需拍板** |
| 3 | **DDL 列类型自相矛盾 + `DO $$` 块** | `003:4,5` 写 `smallint`/`integer`，但 `domain_fact.go:22-23` 的 int32 无 type tag ⇒ `AutoMigrate`（`db.go:15`）建成 **bigint**。`like-rpc` 已为同款问题选 BIGINT 并写明理由（`V4__create_like_domain_fact_outbox.sql:13-15,25-26`）。另 `002:21-27` 的 `DO $$ … $$` 块在 `ScriptUtils` 下会炸（RAG 已有实测记录，见 AGENTS.md §0 item 2）。**两者都需拍板** |
| 4 | **`report_record` 表在 Go 侧从不被建出** | `db.go:15` 的 AutoMigrate 列表里**没有 `ReportRecord`**，而 `data_model.go:66-68` 定义了它的 `TableName()`。⇒ `ReportComment` 在全新部署上必然失败。RAG 是否补建这张表 = **偏离 Go 现状**，需拍板 |
| 5 | **`GetComment` 的 index/content 配对可能错位** | `get_comment_logic.go:122-143` 按下标配对，`:123-125` 只防越界。若某条评论缺 `comment_content` 行（DB 数据异常），`contentRows` 整体前移 ⇒ **内容与统计串位**。本文件按 Go 原样推导；**是否修正需拍板** |
| 6 | **rpc 进程新增 HTTP 端口（wire 面变更）** | 两处：① `comment.go:38-41` 的 `:9091` promhttp；② `cmd/fact-authority` 的 `COMMENT_FACT_AUTHORITY_LISTEN`。RAG 现有 rpc 模块（message/like/article）**都没有**为这两件事新增端口的先例（message 的 `application.yml:105-114` 配了 Actuator 但模块无 web starter）。**需拍板** |
| 7 | **`singleflight` 无 RAG 对应物** | `cache/comment_subject_cache.go:39,49` 与 `comment_ids_page_cache.go:49,124`。全仓零命中。替代方案（`ReentrantLock` + 双检 / 自建 CAS / 直接去掉）**都会改变并发语义**（穿透量、DB 回源次数）。**需拍板** |
| 8 | **Aho-Corasick 无 RAG 对应物** | `utils/sensitive.go:11,38`。词库只有 2 个词（`service_context.go:29`），`contains` 扫描在「是否命中」上等价；但 `Match` 返回的是 `terms[0].Word`（`sensitive.go:40`）—— 多个词同时命中时的**选取顺序**在 AC 机（按模式串长度优先）与朴素扫描（按词库顺序）下可能不同。该词只进日志（`audit_consumer.go:44`），**当前不可观察**，但需确认 |
| 9 | **Kafka 消费者的包归属** | `like-rpc` 放在 `mq/`（`mq/LikeUpdateKafkaListener.java:54-55`），`article-rpc` 也是（`mq/ArticleReviewConsumer.java:1`）—— **两份先例**。本文件按规范 §3.2 的包表把消费者放 `service/`，理由是 §3.2 把 `service` 定义为「业务逻辑和**服务相关组件**」。**两份先例与规范包表不一致，需拍板** |
| 10 | **`GetComment` 的 `result` 标签集合与其他 6 个不同** | `get_comment_logic.go:35-38` 只有 `{ok, sys_fail}`。Micrometer 的 Counter 标签是注册时固定的 ⇒ 若用同一套 tag 定义，**`biz_fail` 永不出现**。保留 Go 原样还是统一，需拍板 |
| 11 | **`comment-rpc/pom.xml` 无任何依赖** | 12 项全缺（见 §12.5）。**实现前必须先补** |
| 12 | **3 个 Go 测试文件默认不跑** | 三者都 `t.Skip`（无 DSN 时）。RAG 侧若照抄为「无 DSN 跳过」，就重蹈 Go 侧的覆辙——必须默认跑真 PG |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| **proto `package` 归一后的对拍方案** | `SD-2026-10-23-04` 决定保持 `/comment.CommentService/*`。双侧对拍时必须在「Go 侧加服务名别名」与「Java 侧重写 `fullMethodName`」之间选一个。**两者本文件都未拍板** |
| `INSERT..ON CONFLICT..RETURNING` 在 MyBatis-Plus 下的写法 | `domain_fact.go:116-118` 需要「原子取号并拿回结果」。`@Select` 不支持 `RETURNING`；`@Insert` + `select` 会破坏原子性。**需在实现期验证可行写法**（候选：`SqlSession` 手写 `select ... for update` + update，或 `JdbcTemplate`） |
| GORM `Scan(&scalar)` 在零行时的行为 | `GetOwnerId`（`comment.go:204-217`）依赖 `RowsAffected == 0` 判 sentinel，但 GORM 对「dest 是标量而非 struct」的错误返回属于库行为（不在本仓库）。Java 侧必须用「查不到行 ⇒ `SubjectNotFound`」显式映射，但**是否有 Go 侧更早返回 `ErrRecordNotFound` 的分支**未确认 |
| `subject` 等表的**时间列类型** | `data_model.go:14-15,34-35,47-48,63` 的 `time.Time` 在 GORM postgres 驱动下建成 `timestamp` 还是 `timestamptz` 取决于版本。RAG 侧若建成 `timestamptz` 而 Go 侧是 `timestamp`，则 `get_comment_logic.go:139` 的本地时区格式化会产生**一个时区差**（AGENTS.md §0 item 2 已在 message/users 域记录过同类问题） |
| Hikari 连接池取值 | Go 侧 comment-rpc **未设** `MaxIdleConns`/`MaxOpenConns`（`service_context.go` 无相关调用），RAG 侧取 `message-rpc` 的 100/10 是否合适未定 |
| Kafka 生产的同步/异步语义 | Go `kq.Pusher.Push` 是**同步阻塞**写（`create:126`）。`like-rpc/application.yml:105-107` 明确记录「Java `KafkaTemplate.send` 是异步」并选了 `acks=1 + 异步 + 失败回调日志」，但那是 like 的选择。comment 侧 `CreateComment` 的**响应时间是否包含 Kafka 往返**属于 L1，未拍板 |
| `:9091` 与 `COMMENT_FACT_AUTHORITY_LISTEN` 的端口方案 | 与 §12.7 #6 同源，见下 |
| **`FactDispatcher` 的 `-once` 部署形态** | 是否保留 CronJob（配置项 `once: true` + 每次起 JVM）还是改为常驻循环，未拍板 |
| `audit_consumer` 的并发度 8 的语义 | `comment.yaml:34` `Consumers: 8` ⇒ Go 侧同进程 8 个并发 `Consume`。Spring Kafka 的 `concurrency: 8` 是**等价**的，但两者的「同分区串行 / 跨分区并行」边界不同，未做活体验证 |
| article-rpc 的 `GetArticle` 多出的两个字段 | RAG 的 `GetArticleRequest` 有 `public_only`（`article.proto:58`）与 `requester_id`（`:59`），Go 侧调用只传 `article_id` + `incr_view=false`（`subject_fallback.go:17-20`）。`public_only=false` + `requester_id=""` 是否与 Go 的「不传」**完全等价**（尤其对 owner 判定路径），未活体验证 |