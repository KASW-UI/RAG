# follow-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/follow/rpc`
> RAG 模块：`service/follow/follow-rpc`
> 目标根包：`com.rag.follow.follow.rpc`
> 推导日期：2026-10-23
> 文档性质：**理论推导**。RAG 侧 `follow-rpc` 当前**只有 `pom.xml`、0 行 Java**。
>
> **本文件覆盖 Go 侧全部 20 个文件（rpc 目录），已逐个读完。**
> **排除** `rpc/pb/`（protoc 生成物，Go 侧 2 个文件）；其 Java 对应物 `follow-proto` **已落地**（实测产出 11 个 java + 1 个 `FollowServiceGrpc`，落包 `com.rag.follow.follow.proto`）。

---

## 12.1 第一部分：目标 Java 项目组织依据

### RAG 当前文件树（事实快照，2026-10-23 实测）

```
service/follow/follow-rpc/
└── pom.xml                        # 19 行，无 <dependencies>、无 <build>
```

`service/follow/pom.xml:12` 已把 `follow-rpc` 列为 `<module>` ⇒ **模块骨架已在位，只是空的**。

### 现有包（实际检查到）

`follow-rpc` 自身零 Java 文件 ⇒ 包结构由已落地兄弟模块推导：

| 参照模块 | RAG 位置（实测） | 提供的一致性依据 |
|---|---|---|
| **`message/message-rpc`（最贴近）** | `message/message-rpc/src/main/java/com/rag/message/message/rpc/` | 19 个文件 + 3 个 Flyway 脚本：`bootstrap/NacosRegistration`、`client/{UserRpcClient,AdminRpcClient}`、`config/{MessageProperties,MybatisPlusOrderingBridgeAutoConfiguration}`、`domain/*Entity`、`exception/MessageExceptions`、`mapper/{*Mapper,GoStylePagination,JsonbMapTypeHandler}`、`metrics/MessageMetrics`、`service/{MessageDomainService,MessageAtomicService,MessageRpcSupport}`、`service/impl/MessageRpcServiceImpl`（**9 个 RPC 的 `@GrpcService` thin wrapper**） |
| `like/like-rpc` | `like/like-rpc/src/main/java/com/rag/like/like/rpc/` | 26 个文件 + 6 个 Flyway 脚本；`bootstrap/{LikeSchemaGate,NacosRegistration}`；`config/LikeKafkaTopics` |
| `users/user/user-rpc` | `users/user/user-rpc/src/main/java/com/rag/users/user/rpc/` | `bootstrap/{SnowflakeBootstrap,UserSchemaBootstrap,NacosRegistration}`；`service/{UserDomainService,AvatarDomainService}` + `service/impl/UserServiceGrpcImpl`（**9 个 RPC**）；**无** `MybatisPlusOrderingBridge`（它当时还没遇到 Boot 4 那个坑） |
| `hot/hot-rpc` | `hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/` | 15 个文件；`bootstrap/NacosRegistration`；**无** mapper（纯 Redis）⇒ follow 的 mapper 形态参考 message / like |
| `service/common` | `service/common/src/main/java/com/rag/common/` | `spring/{RpcServerInterceptorFactory,ObservabilityProperties,ObservabilityLifecycle,SnowflakeBean,SnowflakeProperties,LoggerFactoryBean}`、`persistence/{DeadlineQueryTimeoutInjector,SqlTimeoutStatusTranslator}`、`observability/*`、`logger/*`、`exception/*` |
| `users/users-common` | `users/users-common/src/main/java/com/rag/users/common/ddl/SchemaInitializer.java:53-` | 幂等重放式 DDL 执行器（follow **不采用**，理由见 §12.7 #3） |

**⇒ rpc 侧包名集合已定型**：`bootstrap/ client/ config/ domain/ exception/ mapper/ metrics/ service/ service/impl/`。
本文件**不提出集合外的任何包**。

### 已有组件（跨模块可复用）

| 组件 | RAG 位置 | 判定 |
|---|---|---|
| `RpcServerInterceptorFactory` | `service/common/…/spring/RpcServerInterceptorFactory.java:40-` | **REUSE + ADAPT**：⚠️ 见下方「🔴 拦截器不自动注册」 |
| `ObservabilityProperties` / `ObservabilityLifecycle` | `service/common/…/spring/ObservabilityProperties.java`、`ObservabilityLifecycle.java` | **REUSE**：承载 `observability.rpc-timeout` |
| `DeadlineQueryTimeoutInjector` / `SqlTimeoutStatusTranslator` | `service/common/…/persistence/` | **REUSE**：Go `ctx cancel` 的等价物（pgjdbc 不响应线程 interrupt，必须靠 `setQueryTimeout` 发 CancelRequest） |
| `LoggerFactoryBean` / `Logger` / `WithUserID` | `service/common/…/spring/LoggerFactoryBean.java`、`logger/*` | **REUSE**：替代 Go 的 `logger.Init` / `logger.LogBusinessErr` / `logger.LogInfo` |
| `GoStyleRequestDeadline` | `service/common/…/observability/GoStyleRequestDeadline.java` | **REUSE**：`ensureUserExists` 调 user-rpc 时套 deadline |
| `FollowErrorCode` / `FollowErrorMessages` / `FollowGrpcError` / `FollowCodeError` | `follow-common/errmsg/*`（待实施，见 `FileTree/follow/common.md`） | **REUSE** |
| `GetUserReq` / `GetUserResp` / `UserServiceGrpc` | `users/user/user-proto`（**已生成**，实测 `GetUserResp.java:50` 有 `hasUser()`） | **REUSE**：与 `message-rpc` 的 `UserRpcClient.java:4,26-28,42-44` 同款 |

🔴 **拦截器不自动注册（三处说法不一致，本文件采用唯一被实测验证的一种）**：
- `service/common/…/spring/RpcServerInterceptorFactory.java:14-16` 的 javadoc 声称 starter **会自动**注册；
- 同一文件 `:18-23` 又说必须 `@Import`；
- `message/message-rpc/…/MessageRpcApplication.java:104-108` 给出了**第三种、也是唯一有实测证据**的做法：
  `GrpcServerConfigurer` Bean + `server.intercept(rpcUnaryServerInterceptor)`。
⇒ **follow-rpc 采用 `MessageRpcApplication` 的写法**；`@Import(RpcServerInterceptorFactory.class)` 与 `GrpcServerConfigurer` **两者都要**，缺任一则 gRPC span / deadline 传播 / 超时**全部静默失效**（AGENTS.md §3.3）。

### 已确认的框架机制

- `net.devh:grpc-server-spring-boot-starter` 的 `@GrpcService` + `GrpcServerProperties`（`MessageRpcServiceImpl.java:25,62-63`）
- `net.devh:grpc-client-spring-boot-starter` 的 `@GrpcClient`（`UserRpcClient.java:26-28`）
- MyBatis-Plus `BaseMapper` / `@TableName` / `@TableId` / `@Select`（`ConversationMapper.java:26-27,41-52`）
- **无 MyBatis-Plus 分页拦截器** ⇒ 分页手工拼 `LIMIT`/`OFFSET`（`GoStylePagination.java:43-69`）
- **无 XML mapper**（全仓零 XML）⇒ 注解式原生 SQL
- `@ConfigurationProperties`；Spring Cloud Alibaba Nacos（**手动注册**，不自动）
- Flyway（`spring-boot-flyway` + `flyway-database-postgresql`）
- Micrometer + `MeterRegistry`
- **无 Kafka**（follow-rpc 零消息生产/消费）
- **无 Web**（follow-rpc 在 Go 侧是纯 gRPC 进程，零 HTTP 监听）
- **无 jsonb 列** ⇒ 本域**不需要** `JsonbTypeHandler` / `JsonbMapTypeHandler`

### 当前 Component 可以直接复用的能力

| 能力 | 依据 | 本模块用在哪 |
|---|---|---|
| `Response` 不适用（rpc 走 gRPC status） | — | — |
| `GoStylePagination.limitOffset` | `message-rpc/…/GoStylePagination.java:56-69` | 🔴 **包私有（`final class`，无 `public`）** ⇒ **不可直接复用**，见 §12.7 #4 |
| `MessageRpcServiceImpl` 的 `invoke()` 模板 | `MessageRpcServiceImpl.java:125`（声明）/ `:44`（javadoc 说明「转发 StatusRuntimeException、其它兜底 INTERNAL + ErrorServerCommon」） | `FollowServiceGrpcImpl` 的错误兜底结构 |
| `MybatisPlusOrderingBridgeAutoConfiguration` | `message-rpc/…/config/`（**Boot 4 专用 workaround**，`:17-27` 记录了「不生效 → mapper 报 `Property 'sqlSessionFactory' …`」） | 🔴 **follow-rpc 必须新建**（同样用 MP + DataSource） |

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **主键策略** | 🔴 **与 RAG 惯例相反，必须显式处理**。Go `data_model.go:6` 是 `ID uint64 \`gorm:"primaryKey"\``，**无 `autoIncrement`、无 snowflake**，且 `CreateFollow`（`follow.go:24`）不设 ID ⇒ GORM 省略该列、DB 侧序列赋值（**`bigserial`**）。而 RAG 的 `application.yml` 惯例是 `mybatis-plus.global-config.db-config.id-type: ASSIGN_ID`（雪花，`message-rpc/application.yml:32`）⇒ **follow-rpc 必须改 `IdType.AUTO` 或在 `@TableId` 上显式写**，否则会引入一个 Go 侧不存在的 ID 生成器 |
| **`update_time` 的推进** | ⚠️ Go 侧只有 INSERT / DELETE，**没有任何 UPDATE** ⇒ `autoUpdateTime` 只在 insert 时写入，**此后永不推进**。这与 users 域的 `admin-rpc` 缺陷（AGENTS.md §0 users 行）**同类但方向相反**：那里是「该推进的没推进」，这里是「Go 本来就不推进」。⇒ RAG 侧照抄，**不得**加 `@TableField(fill = UPDATE)` |
| **列表排序缺次级键** | 🔴 Go 侧 4 处 `Order("create_time desc")`（`model/follow.go:81,104,127,150,170,186`）**都没有 tiebreaker**。`create_time` 由 DB 在插入瞬间生成 ⇒ 批量插入/同事务插入会产生并列 ⇒ **翻页时列表项可能重复或跳动**。同类问题在 message 域已被记录为 DRIFT-MSG-V-005（`ConversationMapper.java:59-68`）。⚠️ **「修正」它是新增行为**（会固定一个 Go 侧不存在的顺序），本文件按 Go 保留并标注 |
| **DDL 机制：Flyway vs `SchemaInitializer`** | ✅ **推导建议：Flyway**（`V1`/`V2` + **独立账本表名** `flyway_schema_history_follow`）。判据：① Go 侧 `AutoMigrate` 只由 **follow-rpc 一个模块**执行（`svc/servicecontext.go:18`），**无跨组件建表交叉**（这是 users 域被迫改用 `SchemaInitializer` 的原因，见 `SchemaInitializer.java:16-24`）；② 域内主导形态是 Flyway（message / like / article 三域）。⚠️ 账本表名**必须**独立（AGENTS.md §6.3 问题 4：follow 与 message / like 同库 `first_db`、同 schema `public`） |
| **`Postgres.Mode: "disable"`** | `follow.yaml:35` 的 `Mode` 在 Go 侧被拼进 DSN 的 **`sslmode`**（`db.go:22,28`）⇒ 实际是 `sslmode=disable`（非 TLS 连接）。**字段名有误导性**，实施时须按 `sslmode` 理解 |
| **DB 时区** | `db.go:22` 的 DSN 含 **`TimeZone=Asia/Shanghai`** ⇒ 会话时区固定为 +08。RAG 侧 `jdbc:postgresql://` 需显式带 `?options=-c%20TimeZone%3DAsia%2FShanghai` 或在 JDBC 连接参数里设 `TimeZone`，**否则 JVM 默认时区不同 ⇒ `create_time` 的字面量解释不同**（与 AGENTS.md §0 记录的 `TIMESTAMP⇒TIMESTAMPTZ` 漂移同源） |
| **无 schema gate** | Go `db.go:33,43` 用 `log.Fatalf` 表达「连不上库 / 迁移失败 ⇒ 进程退出」。Flyway 迁移失败**本身就 fail-fast** ⇒ **不需要** `LikeSchemaGate` 那样的 `bootstrap` 校验类（like 需要它是因为要验触发器，而 follow 无触发器/CHECK） |

---

## 12.2 第二部分：Go 原始文件树

```
service/follow/rpc/                                   # 共 20 个文件（不含 pb/）
├── follow.go                            # 入口：InitMetrics + 禁原生超时 + MustNewServer + 注册 + reflection(dev/test) + unary interceptor
├── etc/
│   └── follow.yaml                      # Name/ListenOn 8082/Etcd follow.rpc/Log/Telemetry/DevServer 16065/Postgres/UserRpc/List/Recommend
├── followservice/
│   └── followservice.go                 # 8 个方法的客户端接口（api 侧消费；rpc 侧自身不用）
└── internal/
    ├── config/config.go                 # RpcServerConf + Postgres + UserRpc + List + Recommend
    ├── logic/                           # 8 个 logic + 1 个 helper
    │   ├── helper.go                    # validateRelationReq / normalizeListReq / 3 个成功工厂 / ensureUserExists / toSet / mapKeys / scoreWeight / clamp*Depth / clampFanout
    │   ├── followlogic.go               # validate → ensureUserExists(target) → ExistsAnyBlock → CreateFollow
    │   ├── unfollowlogic.go             # validate → DeleteFollow
    │   ├── blocklogic.go                # validate → ensureUserExists(target) → CreateBlockAndCleanup
    │   ├── unblocklogic.go              # validate → DeleteBlock
    │   ├── getfollowlistlogic.go        # normalize → ensureUserExists(user) → ListFollowTargets
    │   ├── getfollowerlistlogic.go      # normalize → ensureUserExists(user) → ListFollowerUsers
    │   ├── getblocklistlogic.go         # normalize → ensureUserExists(user) → ListBlockTargets
    │   └── getrecommendationslogic.go   # normalize → ensureUserExists(user) → 3 次取集合 → 2~3 层 BFS 展开 → 打分 → 排序 → 分页
    ├── metrics/metrics.go               # 5 个指标 + InitMetrics（**本次真的被调用了**）
    ├── model/
    │   ├── data_model.go                # FollowRelation(follow_relation) + BlockRelation(block_relation)
    │   ├── db.go                        # InitDB：DSN(TimeZone=Asia/Shanghai, sslmode) + 池 10/100 + AutoMigrate + 2 处 Fatalf
    │   ├── follow.go                    # 10 个持久化方法
    │   └── vars.go                      # ErrorNotFound（🔴 零调用点）
    ├── server/
    │   └── followserviceserver.go       # 8 个方法的 gRPC 分发
    └── svc/servicecontext.go            # InitDB + FollowModel + UserRpc
```

**排除**：`rpc/pb/{follow.pb.go,follow_grpc.pb.go}` 是 protoc 生成物。其 Java 对应物 `follow-proto`
**已落地并实测产出**（`follow-proto/target/generated-sources/protobuf/` 下 11 个 message java + `grpc-java/…/FollowServiceGrpc.java`）。

---

## 12.3 第三部分：Go 文件职责分析

### `follow.go`（进程装配）
7 件事：
- `metrics.InitMetrics()`（`:28`）—— 🔴 **真的被调用** ⇒ 5 个 rpc 指标确实注册并被 Prometheus 抓取
  （与 like 域 `like.go:28` 的同类结论一致，但与 `likecenter.go` 的「零调用」不同）
- `conf.MustLoad` + `logx.MustSetup` + `logger.Init(c.Name)`（`:31-33`）
- `observability.DisableNativeRpcTimeout(&c.RpcServerConf)`（`:34`）
- `svc.NewServiceContext(c)`（`:35`）
- `zrpc.MustNewServer(..., RegisterFollowServiceServer)`（`:37-43`）；
  **reflection 只在 `DevMode`/`TestMode` 注册**（`:40-42`）
- `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, SlowThreshold()))`（`:44`）
- 🔴 **无 `AddStreamInterceptors`**、**无 `Etcd` 显式调用**（go-zero 在 `MustNewServer` 内部注册）

### `etc/follow.yaml`
`Name: follow.rpc`（`:1`）、`ListenOn: 0.0.0.0:8082`（`:2`）、`Etcd.Key: follow.rpc`（`:4-7`）、
`Log{ServiceName: follow-rpc, Path: …/log/follow-rpc}`（`:9-12`）、`Telemetry{Name: follow-rpc, …}`（`:14-18`）、
`DevServer{Port: 16065}`（`:20-27`）、
`Postgres{Host: postgres, Port: "5432", User: admin, Password: ${POSTGRES_PASSWORD}, DBName: first_db, Mode: disable}`（`:29-35`）、
`UserRpc.Etcd.Key: user.rpc`（`:37-41`）、
**`List{DefaultLimit: 20, MaxLimit: 100}`（`:43-45`）**、
**`Recommend{DefaultLimit: 20, MaxLimit: 50, MaxDepth: 3, MaxFanout: 50}`（`:47-51`）**。

⚠️ **后两段是本域独有的活跃配置**（message 域也有 `List`，但**没有 `Recommend`**）⇒ `List` 与 `Recommend`
必须**分别绑定**（`getFollowList`/`getFollowerList`/`getBlockList` 用 List；`getRecommendations` 用 Recommend）。

### `internal/config/config.go`
`PostgresConf`（`:5-12`）、`ListConf`（`:14-17`，`json:",default=20"` / `",default=100"`）、
`RecommendConf`（`:19-24`，`default=20/50/3/50`）、
`Config = RpcServerConf + Postgres + UserRpc + List + Recommend`（`:26-32`）。**5 段，全部有真实消费者。**

### `internal/svc/servicecontext.go`
三件事：`model.InitDB(...)`（`:18-25`）、`model.NewFollowModel(db)`（`:29`）、
`userservice.NewUserService(zrpc.MustNewClient(c.UserRpc))`（`:30`）。
⇒ **无 Redis、无 Kafka、无 Snowflake**（对比 like-rpc 有 Redis + Kafka + Lua + communityfact）。

### `followservice/followservice.go`
`FollowService` 接口 **8 个方法**（`:24-33`）：`Follow` / `Unfollow` / `Block` / `Unblock`（收 `RelationReq` → `BaseResp`）；
`GetFollowList` / `GetBlockList` / `GetFollowerList`（收 `ListReq` → `UserListResp`）；`GetRecommendations`（收 `ListReq` → `RecommendResp`）。
类型别名 6 个（`:16-22`，含 **`RecommendResp_RecommendUser`**）。
🔴 **rpc 侧自身不使用它**（它是被 api 消费的客户端）。⇒ Java 侧对应物只有生成物，**无手写类**。

### `internal/server/followserviceserver.go`
8 个方法（`:26-63`），每个都是 `l := logic.New*Logic(ctx, s.svcCtx); return l.X(in)` —— **纯机械分发，零业务**。
⇒ Java 侧对应物是 `@GrpcService` 的 thin wrapper（与 `MessageRpcServiceImpl.java:73-` 同款）。

### `internal/metrics/metrics.go` —— **5 个指标，`Subsystem: "follow_rpc"`**

| Go 变量 | 类型 | 导出名 | labels | 行号 |
|---|---|---|---|---|
| `RPCRequestCounter` | CounterVec | `follow_rpc_request_total` | `[method, result]` | `:13-17` |
| `RPCRequestDuration` | HistogramVec（`DefBuckets`） | `follow_rpc_request_duration_seconds` | `[method]` | `:19-24` |
| `RelationActionCounter` | CounterVec | `follow_rpc_relation_action_total` | `[action, result]` | `:26-30` |
| `DBErrorCounter` | CounterVec | `follow_rpc_db_error_total` | `[action, type]` | `:32-36` |
| `RecommendationSizeGauge` | Gauge（**非 Vec，无 label**） | `follow_rpc_recommendation_size` | — | `:38-42` |

- `InitMetrics()`（`:45-53`）`sync.Once` + `MustRegister` ×5 —— 🔴 **真的被调用**（`follow.go:28`）
- `ObserveRPC(method, started, err)`（`:55-63`）：`result` ∈ {`success`,`fail`}
- `ObserveRelation(action, err)`（`:65-72`）—— **只在 4 个 action logic 调用**（各 `:35`）
- `ObserveDBError(action, type)`（`:74-76`）
- `ObserveRecommendationSize(size)`（`:78-80`）—— **`Set` 而非 `Inc`**，最后一次请求的规模（3 个调用点）

**8 个 `method` 字面量（PascalCase）**：`"Follow"` / `"Unfollow"` / `"Block"` / `"Unblock"` /
`"GetFollowList"` / `"GetFollowerList"` / `"GetBlockList"` / `"GetRecommendations"` —— 8/8 与 proto 的 rpc 名逐字一致 ✅

**4 个 `action` 字面量（`ObserveRelation`，小写无下划线）**：`"follow"` / `"unfollow"` / `"block"` / `"unblock"`

**12 个 `ObserveDBError(action, type)` 参数对**（`action` 这一列**混用了两种命名风格**）：

| `action` | `type` | 调用点 |
|---|---|---|
| `follow` | `exists_any_block` | `logic/followlogic.go:50` |
| `follow` | `create_follow` | `logic/followlogic.go:61` |
| `unfollow` | `delete_follow` | `logic/unfollowlogic.go:44` |
| `block` | `create_block_and_cleanup` | `logic/blocklogic.go:49` |
| `unblock` | `delete_block` | `logic/unblocklogic.go:44` |
| `get_follow_list` | `list_follow_targets` | `logic/getfollowlistlogic.go:50` |
| `get_follower_list` | `list_follower_users` | `logic/getfollowerlistlogic.go:50` |
| `get_block_list` | `list_block_targets` | `logic/getblocklistlogic.go:50` |
| `get_recommendations` | `list_direct_follows` | `logic/getrecommendationslogic.go:58` |
| `get_recommendations` | `list_block_targets` | `logic/getrecommendationslogic.go:69` |
| `get_recommendations` | `list_blocker_users` | `logic/getrecommendationslogic.go:76` |
| `get_recommendations` | `list_layer_follow_targets` | `logic/getrecommendationslogic.go:113` |

⚠️ **同一列里 4 个 action 用小写、4 个 list 用 snake_case** —— 这是 Go 事实，**改它属于指标契约变更**，按 Go 原样保留。

### `internal/logic/helper.go`（11 个符号，本域的规则集心脏）

| 函数 | 行号 | 语义（逐条） |
|---|---|---|
| `recommendCandidate` struct | `:18-21` | `TargetID int64` / `Score int32`（中间结构，不出 wire） |
| `userLogOption` | `:23-25` | 日志上下文 |
| **`validateRelationReq(in *pb.RelationReq)`** | `:27-35` | 3 条判定：`in==nil \|\| UserId<=0 \|\| TargetId<=0` → `GRPCError(InvalidArgument, ErrorInvalidParam=5003)`；`UserId==TargetId` → `GRPCError(InvalidArgument, ErrorSelfRelation=5201)`；否则 nil。**只被 4 个 action logic 调用** |
| **`normalizeListReq(defaultLimit, maxLimit int32, in *pb.ListReq)`** | `:37-56` | `in==nil \|\| UserId<=0` → `GRPCError(InvalidArgument, 5003)`；**`offset<0 → 0`**（`:43-45`）；**`limit<=0 → defaultLimit`**（`:48-50`）；`maxLimit>0 && limit>maxLimit → maxLimit`（`:51-53`）。返回 `(userId, offset, limit, err)` |
| `successBaseResp()` | `:58-63` | `BaseResp{Code: 200, Msg: GetErrMsg(200)="OK"}` |
| `successUserListResp(ids)` | `:65-71` | `UserListResp{Code:200, Msg:"OK", UserIds: ids}` |
| `successRecommendResp(users)` | `:73-79` | `RecommendResp{Code:200, Msg:"OK", Users: users}` |
| **`ensureUserExists(ctx, svcCtx, userID)`** | `:81-94` | 调 `UserRpc.GetUser`；`status.FromError` 成功且 `code==NotFound` → `GRPCError(NotFound, ErrorUserNotFound=5101)`；其它 status 错误 → `GRPCError(Internal, ErrorServerCommon=5001)`；`resp==nil \|\| !resp.Found \|\| resp.User==nil` → `GRPCError(NotFound, 5101)` |
| `toSet(ids)` | `:96-102` | `[]int64 → map[int64]struct{}` |
| `mapKeys(set)` | `:104-113` | map keys → **升序排序**的 slice |
| **`scoreWeight(maxDepth, depth)`** | `:115-128` | `maxDepth<2 → 3`；`depth<2 → 2`；`weight=(maxDepth-depth+1)*10`；`weight<10 → 10`。⇒ 配置 `MaxDepth=3` 时：**depth 2 每命中 +20、depth 3 每命中 +10** |
| **`clampRecommendDepth(depth)`** | `:130-138` | `<2 → 3`；`>5 → 5`。⇒ 配置 `MaxDepth=3` 时不变 |
| **`clampFanout(fanout)`** | `:140-148` | `<=0 → 50`；`>200 → 200`。⇒ 配置 `MaxFanout=50` 时不变 |

⚠️ **`normalizeListReq` 已经把 offset 夹到 ≥0、limit 夹到 ≥1** ⇒ 下游 `model` 层的 `if offset > 0` / `if limit > 0`
守卫（`model/follow.go:82-87` 等）在 follow 域**几乎恒为真**（只有 `offset == 0` 时跳过 OFFSET）。
⇒ 这与 message 域的踩坑不同：message 的 offset **未经夹取**直达 SQL，负值触发 `OFFSET must not be negative`
（`GoStylePagination.java:10-20` 记录了全过程）。**follow 的夹取发生在 service 层，比 mapper 层早一层**。

### 4 个 action logic
| logic | 步骤（行号） | `ObserveRelation` action |
|---|---|---|
| `followlogic.go` | `ObserveRPC("Follow")`（`:34`）+ `ObserveRelation("follow")`（`:35`）→ `validateRelationReq`（`:38-41`）→ `ensureUserExists(**TargetId**)`（`:43-46`）→ **`ExistsAnyBlock(user,target)`**（`:48-53`，DB 错 → `GRPCError(Internal, ErrorDbSelect=5007)` + `ObserveDBError("follow","exists_any_block")`）→ **命中则 `GRPCError(PermissionDenied, ErrorRelationBlocked=5202)`**（`:54-58`）→ `CreateFollow`（`:60-64`，错 → 5008 + `("follow","create_follow")`）→ `successBaseResp()`（`:67`） | `follow` |
| `unfollowlogic.go` | `ObserveRPC("Unfollow")`+`ObserveRelation("unfollow")`（`:34-35`）→ `validateRelationReq`（`:38-41`）→ **`DeleteFollow`（`:43-47`，错 → 5008）** → `successBaseResp()`（`:50`）。🔴 **不做 ensureUserExists、不检查块关系** | `unfollow` |
| `blocklogic.go` | `ObserveRPC("Block")`+`ObserveRelation("block")`（`:34-35`）→ `validateRelationReq`（`:38-41`）→ `ensureUserExists(**TargetId**)`（`:43-46`）→ **`CreateBlockAndCleanup`（`:48-52`，错 → 5008）** → `successBaseResp()`（`:55`） | `block` |
| `unblocklogic.go` | `ObserveRPC("Unblock")`+`ObserveRelation("unblock")`（`:34-35`）→ `validateRelationReq`（`:38-41`）→ **`DeleteBlock`（`:43-47`，错 → 5008）** → `successBaseResp()`（`:50`）。🔴 **不做 ensureUserExists** | `unblock` |

**🔴 4 条必须逐条保留的语义**：
1. **`ExistsAnyBlock` 是双向检查**：`model/follow.go:67-71` 的条件是
   `(user_id=? AND target_id=?) OR (user_id=? AND target_id=?)`，参数为 `(userID, targetID, targetID, userID)`
   ⇒ **「我拉黑了他」或「他拉黑了我」任一成立就拒绝关注**。单向检查会是实质漂移。
2. **`Block` 的清理也是双向的**：`model/follow.go:45-51` 删除
   `(user_id=me AND target_id=him) OR (user_id=him AND target_id=me)` 两条关注记录。
   ⇒ **拉黑会单向解除关注**（`follow_relation` 是**单向边**，删两条等于删两个方向的边）。
3. **`CreateFollow` / `CreateBlockAndCleanup` 是幂等的**：`clause.OnConflict{Columns: [user_id,target_id], DoNothing: true}`
   （`model/follow.go:20-23` 与 `:36-39`）⇒ 重复关注 / 重复拉黑**返回成功且不报错**。
   ⇒ Java 侧**不得**改成 `DO UPDATE`，否则重复关注会推进 `update_time`（Go 侧不会）。
4. **`Unfollow` / `Unblock` 对不存在的行也返回成功**（`Delete` 影响 0 行不算错）⇒
   🔴 这就是 `ErrorFollowNotFound`(5203) / `ErrorBlockNotFound`(5204) 成为**死码**的原因（见 `FileTree/follow/common.md`）。

### 4 个 list logic（形状高度同构）
| logic | `normalizeListReq` 用的配置 | `ensureUserExists` 的对象 | 持久化方法 | `ObserveDBError` |
|---|---|---|---|---|
| `getfollowlistlogic.go` | **`List`**（20/100，`:37`） | `userID`（`:43`） | `ListFollowTargets`（`:48`） | `("get_follow_list","list_follow_targets")`（`:50`） |
| `getfollowerlistlogic.go` | **`List`**（`:37`） | `userID`（`:43`） | `ListFollowerUsers`（`:48`） | `("get_follower_list","list_follower_users")`（`:50`） |
| `getblocklistlogic.go` | **`List`**（`:37`） | `userID`（`:43`） | `ListBlockTargets`（`:48`） | `("get_block_list","list_block_targets")`（`:50`） |
| `getrecommendationslogic.go` | 🔴 **`Recommend`**（20/50，`:38-42`） | `userID`（`:48`） | 3 次取集合 + BFS 的 N 次 `ListAllFollowTargets` | 4 组（见上表） |

5 条共同骨架：`ObserveRPC`（各 `:34`）→ `normalizeListReq` 失败（各 `:38-41`，**无 `ObserveRelation`**）→
`ensureUserExists` 失败（各 `:43-46`）→ 持久化失败 → `GRPCError(Internal, ErrorDbSelect=5007)`（各 `:49-52`）→ `successUserListResp` / `successRecommendResp`。

### `getrecommendationslogic.go` —— **本域最复杂的单方法（152 行）**
逐段拆解（**这是 Java 侧最需要独立承载的一块**）：
```
:38-42  normalizeListReq(Recommend.DefaultLimit=20, Recommend.MaxLimit=50, in)  ← 与其余 3 个 list 用不同配置
:48-51  ensureUserExists(userID)
:53     fanout := clampFanout(Recommend.MaxFanout=50)      ⇒ 50
:54     depth  := clampRecommendDepth(Recommend.MaxDepth=3) ⇒ 3
:56-61  directFollows := ListAllFollowTargets(userID, fanout)   ← ORDER BY create_time DESC LIMIT 50
:62-65  if len(directFollows)==0 { ObserveRecommendationSize(0); return successRecommendResp(nil) }   ← 🔴 提前返回，跳过 3 次查询
:67-72  blockedTargets := ListAllBlockTargets(userID)     ← 无 LIMIT
:74-79  blockerUsers   := ListBlockerUsers(userID)        ← 无 LIMIT
:81-83  directSet / blockedSet / blockerSet := toSet(...)
:85-99  currentLayer = directFollows 过滤（剔除 blockedSet、blockerSet、层内去重）
:101-105 expanded = {userID} ∪ currentLayer
:107     scores := map[int64]int32{}
:108-145 for currentDepth := 2; currentDepth <= depth && len(currentLayer)>0; currentDepth++ {
             nextLayerSet := {}
             for _, sourceUserID := range currentLayer {                       ← 🔴 N+1 查询
                 targets, err := ListAllFollowTargets(sourceUserID, fanout)     ← :111，err → 5007 + ("get_recommendations","list_layer_follow_targets")
                 for _, candidateID := range targets {
                     if candidateID == userID { continue }                      // :119-121
                     if blockedSet[candidateID] { continue }                    // :122-124
                     if blockerSet[candidateID] { continue }                    // :125-127
                     if directSet[candidateID] { continue }                    // :128-130
                     scores[candidateID] += scoreWeight(depth, currentDepth)   // :132  ← 累加，不覆盖
                     if currentDepth < depth && !expanded[candidateID] { nextLayerSet[candidateID]=… }  // :133-137
                 }
             }
             currentLayer = mapKeys(nextLayerSet)        // :141  ← 升序排序，决定同层内的查询顺序
             for _, c := range currentLayer { expanded[c] = struct{}{} }   // :142-144
         }
:147-156 candidates = {c : scores[c] > 0}    ← 🔴 score<=0 的被丢弃
:158-163 sort: score 降序，score 相同则 targetId 升序
:165-169 start := int(offset); if start >= len(candidates) { gauge(0); return successRecommendResp(nil) }
:171-174 end := start + int(limit); if end > len(candidates) { end = len(candidates) }
:176-182 users = candidates[start:end] → RecommendResp_RecommendUser{TargetId, MutualScore}
:184-186 ObserveRecommendationSize(len(users)) + LogInfo → successRecommendResp(users)
```

**🔴 7 条必须逐条保留的语义**：
1. **「互相关注」不计分**：`directSet[candidateID]` 被跳过（`:128-130`）⇒ 只推「二跳及以外」的人。
2. **`expanded` 只挡「再次展开」，不挡「再次计分」**（`:133-137` vs `:132`）⇒ 同一候选可在多层被反复加分。
3. **双向黑名单都挡**：`blockedSet`（我拉的）+ `blockerSet`（拉我的）两个集合都过滤（`:122-127`），与 `ExistsAnyBlock` 的双向语义一致。
4. **`userID` 自身永不被推荐**（`:119-121`）。
5. **推荐结果不受 `directSet` 之外的分页影响**：`offset`/`limit` 只作用在**排序后**的候选切片上（`:165-177`），
   且 `start >= len` 时**返回空列表而非报错**（`:166-169`）。
6. **打分深度从 2 起，不是从 1 起**（`:108`）。配置 `MaxDepth=3` ⇒ 循环跑 `currentDepth ∈ {2, 3}` **两轮**。
7. 🔴 **`ObserveRecommendationSize` 是 Gauge 的 `Set`**：4 个可能的最后一次值（0 / 0 / `len(users)`），
   而不是累计。早期返回（`:63`、`:167`）都记 0 ⇒ **「推荐结果为空」与「没有关注任何人」在指标上不可区分**（Go 事实）。

⚠️ **N+1 查询**：`:111` 在 `for sourceUserID := range currentLayer` 内。配置 `MaxDepth=3` + `MaxFanout=50`
⇒ 最坏 **1（direct）+ 1（blocked）+ 1（blocker）+ 50 + 50 = 103 次 DB 往返**。
⚠️ **无缓存、无批量查询、无预取** ⇒ Java 侧照抄（改成批量 IN 查询是**新增优化**，属行为变更面，需拍板）。

### `internal/model/data_model.go` —— **2 个实体**
```go
type FollowRelation struct {                                   // :5-11
    ID         uint64    `gorm:"primaryKey"`                    // :6  🔴 无 autoIncrement、无 snowflake
    UserId     int64     `gorm:"column:user_id;not null;index;uniqueIndex:uk_follow_pair"`   // :7
    TargetId   int64     `gorm:"column:target_id;not null;index;uniqueIndex:uk_follow_pair"` // :8
    CreateTime time.Time `gorm:"column:create_time;autoCreateTime"`   // :9
    UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`   // :10
}
func (FollowRelation) TableName() string { return "follow_relation" }  // :13-15

type BlockRelation struct { … uk_block_pair … }                // :17-23
func (BlockRelation) TableName() string { return "block_relation" }    // :25-27
```
两表**结构完全同构**，仅表名与唯一索引名不同（`uk_follow_pair` / `uk_block_pair`）。
⚠️ **GORM 的 DDL 生成结果不在本仓库内** ⇒ 列类型（`bigserial` / `timestamptz`）与索引命名属**推导**，见 §12.7 UNKNOWN。
⚠️ **`autoUpdateTime` 在 follow 域永不生效**（无任何 UPDATE 语句，见 §12.1）。

### `internal/model/db.go`
- DSN（`:21-29`）：`host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai`
  ⇒ ⚠️ **`Mode` 字段被当作 `sslmode` 使用**（`follow.yaml:35` 写的是 `"disable"`）
- `gorm.Open` 失败 ⇒ `log.Fatalf`（`:32-34`）
- 连接池 `SetMaxIdleConns(10)` / `SetMaxOpenConns(100)`（`:38-39`）—— 与 RAG 的 `application.yml` 惯例**同值**（`message-rpc/application.yml:21-22`）
- `db.AutoMigrate(&FollowRelation{}, &BlockRelation{})` 失败 ⇒ `log.Fatalf`（`:42-44`）

### `internal/model/follow.go` —— **10 个持久化方法**
| 方法 | 行号 | SQL 语义 | 事务 |
|---|---|---|---|
| `CreateFollow` | `:18-25` | `INSERT … ON CONFLICT (user_id,target_id) DO NOTHING` | 无 |
| `DeleteFollow` | `:27-31` | `DELETE FROM follow_relation WHERE user_id=? AND target_id=?` | 无 |
| **`CreateBlockAndCleanup`** | `:33-54` | **事务内两步**：① `INSERT INTO block_relation … ON CONFLICT (user_id,target_id) DO NOTHING`；② `DELETE FROM follow_relation WHERE (user_id=? AND target_id=?) OR (user_id=? AND target_id=?)` | ✅ **`Transaction(...)`（`:34`）** |
| `DeleteBlock` | `:56-60` | `DELETE FROM block_relation WHERE user_id=? AND target_id=?` | 无 |
| **`ExistsAnyBlock`** | `:62-75` | `SELECT COUNT(*) FROM block_relation WHERE (user_id=? AND target_id=?) OR (user_id=? AND target_id=?)` → `count > 0` | 无 |
| `ListFollowTargets` | `:77-98` | `SELECT … WHERE user_id=? ORDER BY create_time DESC` [`OFFSET` if>0] [`LIMIT` if>0] → 投影 `TargetId` | 无 |
| `ListFollowerUsers` | `:100-121` | `WHERE target_id=? ORDER BY create_time DESC` [offset/limit] → 投影 `UserId` | 无 |
| `ListBlockTargets` | `:123-144` | `WHERE user_id=? ORDER BY create_time DESC` [offset/limit] → 投影 `TargetId` | 无 |
| `ListAllFollowTargets` | `:146-164` | `WHERE user_id=? ORDER BY create_time DESC` [`LIMIT` if>0] → 投影 `TargetId`。**无 offset** | 无 |
| `ListAllBlockTargets` | `:166-180` | `WHERE user_id=? ORDER BY create_time DESC`，**无 offset / 无 limit** → 投影 `TargetId` | 无 |
| `ListBlockerUsers` | `:182-196` | `WHERE target_id=? ORDER BY create_time DESC`，**无 offset / 无 limit** → 投影 `UserId` | 无 |

⚠️ **`Order("create_time desc")` 6 处、全部无次级键**（`:81,104,127,150,170,186`）。
⚠️ **3 个 `ListAll*` 系列不受分页约束**（无 offset），其中 2 个连 limit 都没有 ⇒ 关注数 / 黑名单数极大时
推荐算法的复杂度无上界（Go 事实）。
⚠️ **`ExistsAnyBlock` 用 `COUNT(*)` 而非 `EXISTS`** ⇒ SQL 语义等价但代价不同；Java 侧用 `SELECT 1 … LIMIT 1`
是**优化**而非等价改写，需要说明（本文件建议照抄 `COUNT(*)` 以保持行为零风险，或用 `EXISTS` 并明确声明）。

### `internal/model/vars.go`
`ErrorNotFound = errors.New("record not found")`（`:5-7`）——
🔴 **实测零调用点**（`grep -rn 'ErrorNotFound' rpc/` 只命中定义本身）⇒ **死代码，不迁移**。
且它与 `followcommon.ErrorNotFound`（5002）**同名不同物**（前者是 `error`，后者是 `int`）⇒ 迁移时若照名建类会引入歧义。

### `follow.proto` 与 `follow-proto`（已落地，零偏离）
- Go `proto/follow.proto:3` `package follow;` ⇔ RAG `follow-proto/src/main/proto/follow.proto:3` `package follow;` —— **两侧完全一致**
- 实测 `diff` 结果：**仅 3 处差异** —— `:5` `option go_package = "./pb"` → `:5-6` `option java_package = "com.rag.follow.follow.proto"` + `java_multiple_files = true`；另加 `:35-36` 两行缩进由 4 空格改 2 空格。
  ⇒ **message 名 / 字段名 / 字段号 / 类型 / service 名 / rpc 名 全部逐字一致，L1 wire 契约 0 漂移。**
  ⇒ ⚠️ **不适用** `comment` / `like` 两域的「proto `package` 归一」偏离说明（那两域 Go 侧是遗留名 `pb`）。
- 实测生成物：`FollowServiceGrpc.java:15` `SERVICE_NAME = "follow.FollowService"` ⇒ 方法路径 `/follow.FollowService/Follow` 等，**与 Go 侧完全一致**。
- 🔴 **`RecommendResp.RecommendUser` 是嵌套 message**：实测 `RecommendResp.java:63` 是
  `public static final class RecommendUser` ⇒ **`java_multiple_files = true` 不把嵌套 message 提升为顶层文件**
  ⇒ Java 侧引用形式是 **`RecommendResp.RecommendUser`**，且生成目录里**没有** `RecommendUser.java`。
  这直接影响 Java 代码的 import 与可读性，实施时必须按嵌套类写。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `follow.go` 进程装配 | `FollowRpcApplication` | ABSORB | Spring Boot 启动 |
| `follow.go:28` `InitMetrics()` | Micrometer 声明即注册 | ABSORB | 🔴 **真的被调用了** ⇒ 自动注册是对齐 |
| `follow.go:31-33` conf/log/logger | `@ConfigurationPropertiesScan` + `LoggerFactoryBean` | ABSORB | 框架承担 |
| `follow.go:34` 禁原生超时 | 不做 | ABSORB | grpc starter 无等价默认超时；实际超时值由 `observability.rpc-timeout` 承载 |
| `follow.go:37-43` `MustNewServer` + 注册 | `@GrpcService` + devh starter | ABSORB | 生成 stub 自动注册 |
| `follow.go:40-42` reflection（dev/test） | devh starter 默认行为 | ABSORB | `message-rpc/application.yml:75-76` 已记录同一判断 |
| `follow.go:44` unary interceptor | `spring/RpcServerInterceptorFactory` + **`GrpcServerConfigurer`** | REUSE + ADAPT | 🔴 **两者都要**：只 `@Import` 不够（`MessageRpcApplication.java:99-102` 的实测记录） |
| `follow.go:35` `NewServiceContext` | Spring 容器 | ABSORB | 依赖注入 |
| `svc/servicecontext.go:18-25` `InitDB` | Hikari + Flyway | ADAPT | `application.yml` 承载连接池（10/100 与 Go 同值） |
| `svc/servicecontext.go:29` `NewFollowModel` | `@MapperScan` + 2 个 Mapper Bean | ADAPT | 🔴 **必须加 `@MapperScan`**，否则 mapper 注册不上（`MessageRpcApplication.java:47-58`） |
| `svc/servicecontext.go:30` `UserRpc` | `client/UserRpcClient` | ADAPT | `@GrpcClient("user-rpc")` |
| `followservice/followservice.go` 8 方法 | 生成的 `FollowServiceBlockingStub` | ABSORB | 🔴 **rpc 侧自身不使用**（仅 api 消费）⇒ 无手写类 |
| `server/followserviceserver.go` 8 方法 | `service/impl/FollowServiceGrpcImpl` | ADAPT | thin wrapper + 错误兜底 |
| `config/config.go:5-12` `Postgres` | `application.yml` `spring.datasource.*` | ADAPT | 纯连接参数 |
| `config/config.go:14-17` `List` | `config/FollowRpcProperties.List` | ADAPT | 只 3 个 list logic 用 |
| `config/config.go:19-24` `Recommend` | `config/FollowRpcProperties.Recommend` | ADAPT | **只有 `GetRecommendations` 用** ⇒ 必须独立段 |
| `model/db.go:33,43` `log.Fatalf` | Flyway 迁移失败即启动失败 | ABSORB | 🔴 **不需要 `FollowSchemaGate`**（like 需要它是因为要验触发器，follow 无触发器/CHECK） |
| `model/db.go:38-39` 池 10/100 | `application.yml` `hikari` | ADAPT | 与 `message-rpc/application.yml:21-22` 同值 |
| `model/db.go:22` `TimeZone=Asia/Shanghai` | JDBC URL 的 `options` 参数 | ADAPT | ⚠️ 不配则 JVM 时区漂移（见 §12.1） |
| `model/db.go:42` `AutoMigrate` | Flyway `V1`/`V2` | ADAPT | 幂等重放 → 版本化脚本 |
| `model/data_model.go:5-15` `FollowRelation` | `domain/FollowRelationEntity` | ADAPT | `@TableName("follow_relation")` |
| `model/data_model.go:17-27` `BlockRelation` | `domain/BlockRelationEntity` | ADAPT | `@TableName("block_relation")` |
| `model/data_model.go:6` 无 snowflake 的自增主键 | `@TableId(type = IdType.AUTO)` | ADAPT | 🔴 **必须显式覆盖** `application.yml` 的 `ASSIGN_ID` 惯例 |
| `model/data_model.go:9-10` `autoCreateTime`/`autoUpdateTime` | **只配 `FieldFill.INSERT`**，不配 UPDATE | ADAPT | ⚠️ follow 无 UPDATE ⇒ 加 UPDATE 就是新增行为 |
| `model/follow.go:18-25` `CreateFollow` | `FollowRelationMapper.insertIgnoreDuplicate` | ADAPT | `ON CONFLICT DO NOTHING`，**不得**改 `DO UPDATE` |
| `model/follow.go:27-31` `DeleteFollow` | `FollowRelationMapper.deleteByPair` | ADAPT | 单条件 DELETE |
| `model/follow.go:33-54` `CreateBlockAndCleanup` | `mapper` 两个方法 + `@Transactional` | SPLIT | 🔴 **独立 `@Service` Bean** 才能让 `@Transactional` 经代理生效（§3.3 self-invocation） |
| `model/follow.go:56-60` `DeleteBlock` | `BlockRelationMapper.deleteByPair` | ADAPT | 同 `DeleteFollow` |
| `model/follow.go:62-75` `ExistsAnyBlock` | `BlockRelationMapper.existsAnyDirection` | ADAPT | 🔴 **双向 OR 条件**，单向是实质漂移 |
| `model/follow.go:77-98` `ListFollowTargets` | `FollowRelationMapper.listTargets(userId, offset, limit)` | ADAPT | `ORDER BY create_time DESC` 无次键（保留 Go 行为） |
| `model/follow.go:100-121` `ListFollowerUsers` | `FollowRelationMapper.listFollowers(targetId, offset, limit)` | ADAPT | 同上，条件列换 `target_id` |
| `model/follow.go:123-144` `ListBlockTargets` | `BlockRelationMapper.listTargets(userId, offset, limit)` | ADAPT | 同上 |
| `model/follow.go:146-164` `ListAllFollowTargets` | `FollowRelationMapper.listAllTargets(userId, limit)` | ADAPT | **无 offset** |
| `model/follow.go:166-180` `ListAllBlockTargets` | `BlockRelationMapper.listAllTargets(userId)` | ADAPT | **无 offset 无 limit** |
| `model/follow.go:182-196` `ListBlockerUsers` | `BlockRelationMapper.listBlockers(targetId)` | ADAPT | 同上 |
| 6 处 `if offset>0` / `if limit>0` 分段守卫 | `mapper/GoStyleLimitOffset`（**新建**，非 REUSE） | ADAPT | 🔴 `GoStylePagination` 是**包私有**，见 §12.7 #4 |
| `model/vars.go:5-7` `ErrorNotFound` | **不迁移** | — | 🔴 实测零调用点，且与 `followcommon.ErrorNotFound`(5002) 同名不同物 |
| `logic/helper.go:27-35` `validateRelationReq` | `service/FollowRpcSupport.validateRelationReq` | MERGE | 与 `normalizeListReq` 同为「入参归一 + gRPC 错误」规则集 |
| `logic/helper.go:37-56` `normalizeListReq` | `service/FollowRpcSupport.normalizeListReq` | MERGE | 同上 |
| `logic/helper.go:58-63,65-71,73-79` 3 个成功工厂 | `FollowRpcSupport.successBaseResp / successUserListResp / successRecommendResp` | MERGE | 同一规则集，3 个变体 |
| `logic/helper.go:81-94` `ensureUserExists` | `service/FollowUserExistenceService` | SPLIT | 🔴 **独立 Bean**：含 gRPC 出网调用与独立的错误映射规则（NotFound vs Internal），与纯归一规则不同源 |
| `logic/helper.go:81-94` 的 `resp.User == nil` | `!resp.hasUser()` | ADAPT | 🔴 Java protobuf 的 `getUser()` **永不返回 null**，必须用 `hasUser()` |
| `logic/helper.go:96-102` `toSet` / `:104-113` `mapKeys` | `service/FollowRecommendationService` 私有 | MERGE | 仅推荐算法一个消费者 |
| `logic/helper.go:115-128` `scoreWeight` | `FollowRecommendationService.scoreWeight` | MERGE | 同上 |
| `logic/helper.go:130-138` `clampRecommendDepth` / `:140-148` `clampFanout` | `FollowRecommendationService.clamp*` | MERGE | 同上 |
| 8 个 logic | `service/FollowDomainService`（8 方法） | MERGE | 与 users「9 合 1」、like「6 合 1」、message「9 合 1」决策一致 |
| `logic/followlogic.go` 的双向黑名单检查 | `FollowDomainService.follow` | ADAPT | `existsAnyDirection` |
| `logic/followlogic.go:54-58` PermissionDenied/5202 | `FollowGrpcError.grpcError(PERMISSION_DENIED, 5202)` | ADAPT | Go 用的是标准 `codes.PermissionDenied`，**不是**业务码塞 status |
| `logic/blocklogic.go` 的事务 | `FollowDomainService.block` + `@Transactional` | ADAPT | 见下「独立 Bean」 |
| `logic/getrecommendationslogic.go` 全文 | `service/FollowRecommendationService` | SPLIT | 152 行、含 BFS + 打分 + 排序 + 分页 + 3 次提前返回 ⇒ **独立变化原因**（调参只动这一个类） |
| `metrics/metrics.go:13-42` 5 个指标 | `metrics/FollowRpcMetrics` | ADAPT | 指标名与 tags 1:1 |
| `metrics/metrics.go:78-80` Gauge `Set` | `FollowRpcMetrics.observeRecommendationSize` | ADAPT | `Set` 而非 `Inc` |
| `metrics/metrics.go:65-72` `ObserveRelation` | 同上 `observeRelation` | ADAPT | **只 4 个 action 用** |
| `metrics/metrics.go:74-76` `ObserveDBError` | 同上 `observeDbError` | ADAPT | 12 组 `(action,type)` 字面量 |
| `metrics/metrics.go:45-53` `InitMetrics` | Micrometer 自动注册 | ABSORB | Go 侧确实调用了它 |
| `common/errmsg`（rpc 侧所需子集） | `follow-common/errmsg/*` | REUSE | 见 `FileTree/follow/common.md` |
| —— | `com.rag.common.persistence.{DeadlineQueryTimeoutInjector,SqlTimeoutStatusTranslator}` | REUSE | 与 `MessageRpcApplication.java:79-86` 同款 |
| —— | `MybatisPlusOrderingBridgeAutoConfiguration`（**新建**） | NEW | 🔴 Boot 4 排序 workaround，否则 mapper 直接不可用 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/follow/follow/rpc/
├── FollowRpcApplication.java
├── bootstrap/
│   └── NacosRegistration.java
├── client/
│   └── UserRpcClient.java
├── config/
│   ├── FollowRpcProperties.java
│   └── MybatisPlusOrderingBridgeAutoConfiguration.java
├── domain/
│   ├── FollowRelationEntity.java
│   └── BlockRelationEntity.java
├── mapper/
│   ├── FollowRelationMapper.java
│   ├── BlockRelationMapper.java
│   └── GoStyleLimitOffset.java
├── metrics/
│   └── FollowRpcMetrics.java
└── service/
    ├── FollowDomainService.java
    ├── FollowRpcSupport.java
    ├── FollowUserExistenceService.java
    ├── FollowRecommendationService.java
    ├── FollowBlockTransactionService.java
    └── impl/
        └── FollowServiceGrpcImpl.java

resources/
├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── application.yml
└── db/migration/
    ├── V1__create_follow_relation.sql
    └── V2__create_block_relation.sql
```

**16 个文件**（1 入口 + 1 bootstrap + 1 client + 2 config + 2 domain + 3 mapper + 1 metrics + 5 service + 1 impl）
+ 1 imports 文件 + 1 yml + 2 Flyway 脚本。
Go 侧 20 个文件 → Java 侧 16 个：**合并 9 个 logic 为 3 个 Service（+1 支持类 +1 impl），新增 5 个有明确职责依据的文件。**

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `FollowRpcApplication` | `@SpringBootApplication`（**不扫 `com.rag.common`**）+ `@MapperScan` + `@Import{...}` + **`GrpcServerConfigurer`** | `follow.go` 进程装配 | 框架约定。⚠️ 三处都是**防回退断言**，删任一行都会静默失效 |
| `bootstrap/NacosRegistration` | `ApplicationReadyEvent` 后 `registerInstance("follow-rpc", …)`，`@PreDestroy` 反注册 | `follow.yaml:1,4-7` 的 etcd 注册 | 4 个 rpc 模块的共同先例（message / like / user / admin 均有）；⚠️ 必须**手动**注册（自动注册会把 web 容器拉起来，见 `NacosRegistration.java:31-38`） |
| `client/UserRpcClient` | `@GrpcClient("user-rpc")` 阻塞 stub；`exists(uid)` 方法返回 `boolean`（把 `GetUserResp` 三态判断收到一处） | `svc/servicecontext.go:30` + `helper.go:81-94` 的调用 | 外部服务客户端，`client/` 有先例 |
| `config/FollowRpcProperties` | `list.{default-limit,max-limit}` + `recommend.{default-limit,max-limit,max-depth,max-fanout}` | `config.go:14-24` + `follow.yaml:43-51` | 🔴 **`List` 与 `Recommend` 必须分两段**（3 个 list 与 1 个 recommend 用不同值） |
| `config/MybatisPlusOrderingBridgeAutoConfiguration` | 空类，仅声明 `after = DataSourceAutoConfiguration` | 无 Go 来源 | 🔴 **Boot 4 排序 workaround**：不建则 MP 自动配置不生效、mapper 报 `Property 'sqlSessionFactory' …`。**必须同时写进 `AutoConfiguration.imports`** |
| `domain/FollowRelationEntity` | `@TableName("follow_relation")`；`@TableId(type = IdType.AUTO)`；`createTime` / `updateTime` **只配 INSERT fill** | `data_model.go:5-15` | ORM 适配 |
| `domain/BlockRelationEntity` | `@TableName("block_relation")`；同上 | `data_model.go:17-27` | 表用途不同（拉黑是**访问控制**，关注是**社交图**） |
| `mapper/FollowRelationMapper` | `insertIgnoreDuplicate`（`ON CONFLICT DO NOTHING`）/ `deleteByPair` / `listTargets(userId, offset, limit)` / `listFollowers(targetId, offset, limit)` / `listAllTargets(userId, limit)` | `model/follow.go:18-31,77-121,146-164` | 5 个持久化操作 + 索引语义 |
| `mapper/BlockRelationMapper` | `insertIgnoreDuplicate` / `deleteByPair` / `existsAnyDirection`（**双向 OR**）/ `listTargets` / `listAllTargets` / `listBlockers` | `model/follow.go:33-75,123-144,166-196` | 同上；`existsAnyDirection` 是并发正确性的核心 |
| `mapper/GoStyleLimitOffset` | 生成 `LIMIT n OFFSET m` 尾子句（**两段各自有条件**） | 6 处 `if offset>0` / `if limit>0` | 🔴 **新建而非 REUSE**：`message-rpc` 的 `GoStylePagination` 是 `final class`（无 `public`，`GoStylePagination.java:43`）⇒ **跨模块不可见** |
| `metrics/FollowRpcMetrics` | 5 个指标 + 4 个 observe 方法 | `metrics/metrics.go:13-80` | 独立变化原因（指标名 / labels / Gauge 语义） |
| `service/FollowRpcSupport` | `validateRelationReq` / `normalizeListReq` / 3 个成功工厂 | `logic/helper.go:27-79` | 「入参归一 + gRPC 错误构造」的规则集，被 8 个用例共用 |
| `service/FollowUserExistenceService` | `ensureUserExists(uid)`；⚠️ 用 `hasUser()` 而非 `getUser() != null` | `logic/helper.go:81-94` | 🔴 **独立 Bean**：含出网调用 + 独立错误映射（NotFound→5101 / 其它→5001），与纯归一规则不同源，且可独立 mock / 降级 |
| `service/FollowRecommendationService` | 152 行推荐算法：3 次取集合 + BFS + 打分 + 排序 + 分页 + 3 处提前返回 + Gauge | `logic/getrecommendationslogic.go` **全文** | 🔴 **独立变化原因**：调参（depth / fanout / weight）只动这一个类；152 行塞进 `FollowDomainService` 会让它同时承担「4 个写用例 + 4 个读用例 + 一个图算法」三种关注点 |
| `service/FollowBlockTransactionService` | `@Transactional` 的 `block(userId, targetId)`：insert block + 双向删 follow | `model/follow.go:33-54` | 🔴 **独立 Bean 才能让 `@Transactional` 经代理生效**（§3.3：内部调用不走代理 ⇒ 事务静默失效，两步变成两步独立提交） |
| `service/FollowDomainService` | 8 个用例方法（4 写 + 4 读） | `logic/*.go` 8 个 | 与 users「9 合 1」/like「6 合 1」/message「9 合 1」决策一致（同一变化原因：follow 域） |
| `service/impl/FollowServiceGrpcImpl` | `@GrpcService`，8 个 override + `invoke()` 兜底 | `server/followserviceserver.go:26-63` | gRPC 契约边界 |

### 关于「不建哪些东西」的三条明确结论

| 不建 | 理由 |
|---|---|
| **`bootstrap/FollowSchemaGate`** | like 需要它是因为要验 `like_domain_fact_identity_immutable` 触发器等「表存在≠约束存在」的漂移（`FileTree/like/rpc.md:471`）。**follow 的两张表无触发器、无 CHECK、无部分索引**；Go 的 fail-fast 语义（`db.go:33,43`）已由 Flyway「迁移失败即启动失败」等价满足 ⇒ 不建 |
| **`exception/FollowExceptions`** | Go 侧**零自定义异常类型**：所有错误都是 `status.Error(grpcCode, msg)`（`GRPCError`，19 处）。RAG 侧同理用 `FollowGrpcError.grpcError(...)` 构造 ⇒ 没有任何需要 Java 异常类承载的业务异常。message 域的 `MessageExceptions` 是为「Go 直接透传 GORM error」补的载体，**follow 的错误全部显式映射过**，不适用 |
| **`service/FollowCursorIdGenerator` / Redis keys / Kafka 相关的一切** | follow-rpc **无 Redis、无 Kafka、无 Snowflake**（`svc/servicecontext.go` 只有 3 个字段）⇒ 照抄 like-rpc 会凭空造出 Go 侧不存在的职责 |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `follow.go` | `FollowRpcApplication` | ABSORB | 进程装配由框架承担 |
| `follow.go:28` `InitMetrics()` | Micrometer 声明即注册 | ABSORB | 🔴 Go 侧确实调用了它 |
| `follow.go:31-33` conf/log/logger | `@ConfigurationPropertiesScan` + `LoggerFactoryBean` | ABSORB | 框架承担 |
| `follow.go:34` 禁原生超时 | — | — | grpc starter 无等价默认超时，无代码可落 |
| `follow.go:37-43` server + 注册 | `@GrpcService` + devh starter | ABSORB | 框架承担 |
| `follow.go:40-42` reflection（dev/test） | devh starter 默认 | ABSORB | 同 `message-rpc/application.yml:75-76` 的判断 |
| `follow.go:44` unary interceptor | `RpcServerInterceptorFactory` + `GrpcServerConfigurer` | REUSE + ADAPT | 🔴 两者都要，缺任一静默失效 |
| `svc/servicecontext.go:35` `NewServiceContext` | Spring 容器 | ABSORB | DI |
| `etc/follow.yaml:2` ListenOn 8082 | `application.yml` `grpc.server.port` | ADAPT | 配置替代 |
| `etc/follow.yaml:1,4-7` Name / Etcd | `spring.application.name` + `bootstrap/NacosRegistration` | ADAPT | `follow.rpc` → `follow-rpc` |
| `etc/follow.yaml:9-12` Log | `logging.*` | ADAPT | 同上 |
| `etc/follow.yaml:14-18` Telemetry | `otel.*` | ADAPT | `Telemetry.Name: follow-rpc` → `otel.service.name` |
| `etc/follow.yaml:20-27` DevServer 16065 | — | — | Go `Config` 无对应字段（**UNKNOWN**） |
| `etc/follow.yaml:29-35` Postgres | `spring.datasource.*` + `hikari` | ADAPT | ⚠️ `Mode` 实际是 `sslmode`；池 10/100 与 Go 同值 |
| `etc/follow.yaml:37-41` UserRpc | `grpc.client.user-rpc.address` | ADAPT | `user.rpc` → Nacos `user-rpc` |
| `etc/follow.yaml:43-45` List 20/100 | `config/FollowRpcProperties.List` | ADAPT | 3 个 list logic |
| `etc/follow.yaml:47-51` Recommend 20/50/3/50 | `config/FollowRpcProperties.Recommend` | ADAPT | **只有 `GetRecommendations` 用** ⇒ 独立段 |
| `config/config.go` | `config/FollowRpcProperties` | ADAPT | 只迁活跃段 |
| `svc/servicecontext.go:18-25` `InitDB` | Hikari + Flyway | ADAPT | 连接管理换 Spring |
| `svc/servicecontext.go:29` `NewFollowModel` | `@MapperScan` + 2 个 Mapper | ADAPT | 🔴 `@MapperScan` 必填 |
| `svc/servicecontext.go:30` `UserRpc` | `client/UserRpcClient` | ADAPT | `@GrpcClient` |
| `followservice/followservice.go` 8 方法 | 生成的 stub | ABSORB | 🔴 **rpc 侧自身不用**，仅 api 消费 |
| `server/followserviceserver.go` 8 方法 | `service/impl/FollowServiceGrpcImpl` | ADAPT | 分发由 starter 做 |
| `logic/*.go`（8 个） | `service/FollowDomainService`（8 方法） | MERGE | 同 users / like / message 的「N 合 1」决策 |
| `logic/followlogic.go`（双向黑名单） | `FollowDomainService.follow` | ADAPT | `existsAnyDirection` |
| `logic/followlogic.go:54-58` | `FollowGrpcError.grpcError(PERMISSION_DENIED, 5202)` | ADAPT | 标准 gRPC code + 码表文案 |
| `logic/unfollowlogic.go` | `FollowDomainService.unfollow` | MERGE | 同上 |
| `logic/blocklogic.go` | `FollowDomainService.block` → `FollowBlockTransactionService` | SPLIT | 🔴 事务需独立 Bean |
| `logic/unblocklogic.go` | `FollowDomainService.unblock` | MERGE | 同上 |
| `logic/getfollowlistlogic.go` | `FollowDomainService.getFollowList` | MERGE | 同上 |
| `logic/getfollowerlistlogic.go` | `FollowDomainService.getFollowerList` | MERGE | 同上 |
| `logic/getblocklistlogic.go` | `FollowDomainService.getBlockList` | MERGE | 同上 |
| `logic/getrecommendationslogic.go`（全文） | `service/FollowRecommendationService` | SPLIT | 152 行图算法，独立变化原因 |
| `logic/helper.go:27-35` `validateRelationReq` | `service/FollowRpcSupport` | MERGE | 与 `normalizeListReq` 同规则集 |
| `logic/helper.go:37-56` `normalizeListReq` | `service/FollowRpcSupport` | MERGE | 同上 |
| `logic/helper.go:58-79` 3 个成功工厂 | `FollowRpcSupport` | MERGE | 同一规则集的 3 个变体 |
| `logic/helper.go:81-94` `ensureUserExists` | `service/FollowUserExistenceService` | SPLIT | 出网 + 独立错误映射 |
| `logic/helper.go:81-94` 的 `resp.User == nil` | `!resp.hasUser()` | ADAPT | 🔴 Java protobuf 的 `getUser()` 永不返回 null |
| `logic/helper.go:96-113` `toSet` / `mapKeys` | `FollowRecommendationService` 私有 | MERGE | 仅推荐一个消费者 |
| `logic/helper.go:115-148` `scoreWeight` / `clamp*` | `FollowRecommendationService` 私有 | MERGE | 同上 |
| `metrics/metrics.go:13-42` 5 个指标 | `metrics/FollowRpcMetrics` | ADAPT | 名与 tags 1:1 |
| `metrics/metrics.go:55-80` 4 个 observe | 同上 | ADAPT | Gauge 用 `Set` |
| `metrics/metrics.go:45-53` `InitMetrics` | Micrometer 自动注册 | ABSORB | Go 侧确实调用了它 |
| `model/data_model.go:5-15` `FollowRelation` | `domain/FollowRelationEntity` | ADAPT | ORM 适配 |
| `model/data_model.go:17-27` `BlockRelation` | `domain/BlockRelationEntity` | ADAPT | 同上 |
| `model/data_model.go:6` 自增主键 | `@TableId(type = IdType.AUTO)` | ADAPT | 🔴 必须覆盖 `ASSIGN_ID` 惯例 |
| `model/data_model.go:9-10` 时间 fill | **只配 INSERT** | ADAPT | follow 无 UPDATE ⇒ 加 UPDATE 是新增行为 |
| `model/db.go:42-44` `AutoMigrate` | Flyway `V1`/`V2` | ADAPT | 幂等重放 → 版本化 |
| `model/db.go:33,43` `log.Fatalf` | Flyway 失败即启动失败 | ABSORB | ⇒ **不建 `FollowSchemaGate`** |
| `model/db.go:38-39` 池参数 | `application.yml` `hikari` | ADAPT | 同值 |
| `model/db.go:22` `TimeZone=Asia/Shanghai` | JDBC URL `options` | ADAPT | 不配则时区漂移 |
| `model/follow.go:18-25` `CreateFollow` | `FollowRelationMapper.insertIgnoreDuplicate` | ADAPT | `DO NOTHING`，**不得**改 `DO UPDATE` |
| `model/follow.go:27-31` `DeleteFollow` | `FollowRelationMapper.deleteByPair` | ADAPT | — |
| `model/follow.go:33-54` `CreateBlockAndCleanup` | `mapper` 两方法 + `FollowBlockTransactionService` | SPLIT | 🔴 事务需独立 Bean |
| `model/follow.go:56-60` `DeleteBlock` | `BlockRelationMapper.deleteByPair` | ADAPT | — |
| `model/follow.go:62-75` `ExistsAnyBlock` | `BlockRelationMapper.existsAnyDirection` | ADAPT | 🔴 双向 OR |
| `model/follow.go:77-98` `ListFollowTargets` | `FollowRelationMapper.listTargets` | ADAPT | 排序无次键（保留 Go 行为） |
| `model/follow.go:100-121` `ListFollowerUsers` | `FollowRelationMapper.listFollowers` | ADAPT | 同上 |
| `model/follow.go:123-144` `ListBlockTargets` | `BlockRelationMapper.listTargets` | ADAPT | 同上 |
| `model/follow.go:146-164` `ListAllFollowTargets` | `FollowRelationMapper.listAllTargets` | ADAPT | **无 offset** |
| `model/follow.go:166-180` `ListAllBlockTargets` | `BlockRelationMapper.listAllTargets` | ADAPT | **无 offset 无 limit** |
| `model/follow.go:182-196` `ListBlockerUsers` | `BlockRelationMapper.listBlockers` | ADAPT | 同上 |
| 6 处 `if offset>0` / `if limit>0` | `mapper/GoStyleLimitOffset` | NEW | `GoStylePagination` 包私有，不可复用 |
| `model/vars.go:5-7` `ErrorNotFound` | **不迁移** | — | 🔴 实测零调用点 + 与 `followcommon` 同名不同物 |
| `proto/follow.proto` | `follow-proto`（**已落地，零偏离**） | REUSE | `package follow;` 两侧一致；`SERVICE_NAME = "follow.FollowService"` |
| `RecommendResp.RecommendUser` 嵌套 message | `com.rag.follow.follow.proto.RecommendResp.RecommendUser` | ABSORB | 已实测是**静态嵌套类**，`java_multiple_files` 不提升嵌套 message |
| —— | `AutoConfiguration.imports`（1 行） | NEW | 桥接类必须登记，否则排序边不建立 |
| —— | `DeadlineQueryTimeoutInjector` / `SqlTimeoutStatusTranslator` | REUSE | 与 `MessageRpcApplication.java:79-86` 同款 |
| —— | `pom.xml`（现为 19 行空壳） | ADAPT | 需补 `follow-proto` / `user-proto` / `follow-common` / `common` / grpc server+client / mybatis-plus / postgresql / flyway / nacos / otlp / 测试栈 |
| —— | 必配测试：`FollowRpcContainerWiringTest`（**必须真启动一次**，断言 `@MapperScan` + `server.intercept(...)` + `@Import` 三行）、`FollowBlockTransactionWiringTest`（**真 PG**，断言两步同事务回滚）、`FollowRecommendationParityTest`（**真 PG + 固定数据集**，断言 BFS 打分与排序逐条一致）、`FollowPaginationGoStyleTest`（断言 `offset=0` 时不产 OFFSET）、`FollowRosterDbErrorCodeTest`（断言 12 组 `ObserveDBError` 的 action/type 字面量） | NEW | AGENTS.md §3.3：纯 Mockito 单测不算 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 8 个 RPC 及全字段（`RelationReq`/`BaseResp`/`ListReq`/`UserListResp`/`RecommendResp`）；`SERVICE_NAME = "follow.FollowService"`（实测 `FollowServiceGrpc.java:15`）；2 张表的列 / 唯一索引 / 普通索引；5 个指标名 + 12 组 `ObserveDBError` 的 action/type + 8 个 `method` 字面量；配置 6 个数值（List 20/100、Recommend 20/50/3/50）；`RecommendResp.RecommendUser` 嵌套结构对 Java 生成类的影响 |
| L2 业务语义有承载位置 | ✅ `validateRelationReq` 的 3 条判定；`normalizeListReq` 的 4 条夹取；`ensureUserExists` 的 3 分支（NotFound / 其它 status / 未找到）；**`ExistsAnyBlock` 与 `CreateBlockAndCleanup` 的双向语义**；`ON CONFLICT DO NOTHING` 的幂等；`Unfollow`/`Unblock` 对不存在行返回成功；`scoreWeight`/`clamp*` 的数值；推荐的 7 条规则（互关不计分 / `expanded` 只挡展开不挡计分 / 双向黑名单 / 不推自己 / offset-limit 只切排序后 / 从 depth 2 起 / 3 处提前返回） |
| L3 技术语义有承载方案 | ✅ 事务边界 1 处（`CreateBlockAndCleanup`）→ **独立 `@Service` Bean**；`ON CONFLICT DO NOTHING` → `@Insert` 原生 SQL；条件式 `LIMIT`/`OFFSET` → `GoStyleLimitOffset`；SQL 超时取消 → `DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator`；gRPC 超时 / span / deadline → `RpcServerInterceptorFactory` + `GrpcServerConfigurer`；**`ORDER BY create_time DESC` 无次键的翻页不确定性按 Go 保留并标注** |
| 业务分支与异常路径已纳入 | ✅ DB 错误分 `ErrorDbSelect`(5007) 与 `ErrorDbUpdate`(5008) 两类、逐个调用点映射；`PermissionDenied`/5202；`NotFound`/5101；`InvalidArgument`/5003 与 5201；推荐的 3 处提前返回；「重复关注/重复拉黑不算错」 |
| 框架吸收有机制依据 | ✅ gRPC server / reflection / DI / Hikari / Flyway / Actuator / Micrometer 均指出实际机制；`followservice` 转发层由生成 stub 承担 |
| 复用组件已明确 | ✅ `RpcServerInterceptorFactory`、`ObservabilityProperties/Lifecycle`、`DeadlineQueryTimeoutInjector`、`SqlTimeoutStatusTranslator`、`LoggerFactoryBean`、`GoStyleRequestDeadline`、`GoStylePagination`（**判定为不可复用**）、`follow-common/errmsg/*`、`users/user-proto` 的 `GetUserReq/Resp/UserServiceGrpc` |
| 新增文件有职责依据 | ✅ `MybatisPlusOrderingBridgeAutoConfiguration`（Boot 4 排序，否则 mapper 不可用）、`GoStyleLimitOffset`（`GoStylePagination` 包私有）、`FollowBlockTransactionService`（事务代理边界）、`FollowRecommendationService`（152 行图算法）、`FollowUserExistenceService`（出网 + 独立错误映射）、`NacosRegistration`（4 个 rpc 模块的共同先例）—— 均非「Go 有同名文件」 |
| 合并有组织依据 | ✅ 8 logic → `FollowDomainService`（同 users / like / message 决策）；helper 的 8 个纯函数 → `FollowRpcSupport` + 2 个独立 Bean（按「有无出网 / 有无事务代理边界」切分） |
| 拆分可解释 | ✅ 图算法从 DomainService 拆出；事务用例从 DomainService 拆出；存在性检查从 Support 拆出 |
| 无「为对应 Go 文件而建」的文件 | ✅ 未创建 `Logic.java` / `Model.java` / `Repository.java` / `ServiceContext.java` / `Server.java` / `Vars.java`；`model/vars.go` 的死码不迁移；`followservice/` 不建手写类 |
| 未把未实现能力说成现有机制 | ✅ `follow-rpc` 空壳 + 空依赖已在 §12.1 声明；`AutoConfiguration.imports` 与桥接类是**待实施**，不是既成事实 |
| 未把 Go 缺陷误当需求 | ✅ `ORDER BY` 无次键、`N+1` 查询、`ExistsAnyBlock` 用 `COUNT(*)`、`update_time` 永不推进 —— 均标注为 Go 事实并说明「修正即为新增行为」 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | 🔴 **`follow-rpc/pom.xml` 零依赖** | 缺 `follow-proto`、`user-proto`（`ensureUserExists` 需要）、`follow-common`、`common`、`grpc-server-spring-boot-starter`、`grpc-client-spring-boot-starter`、`mybatis-plus-spring-boot3-starter`、`postgresql`、`flyway-core` + `flyway-database-postgresql` + `spring-boot-flyway`、`nacos-discovery`、`micrometer-registry-prometheus`、`opentelemetry-exporter-otlp`、`spring-boot-starter-actuator`、测试栈。**任一缺失的后果分两类**：编译不过（可发现）／Bean 静默缺失（不可发现，如 `spring-boot-flyway` 缺失会让 Flyway **完全惰性**，`users/user/user-rpc/pom.xml:142` 的注释「⚠️ 必须**连 spring-boot-flyway 一起删**，不能只删 flyway-core」已记录） |
| 2 | 🔴 **Flyway 账本表名必须独立** | follow-rpc 与 message-rpc / like-rpc / article-rpc 连同一个 `first_db`、同 schema `public`。若沿用默认 `flyway_schema_history`，四方的 V1/V2 会互相撞号且内容不同 ⇒ 后启动者把自己的迁移判为「已应用」而静默跳过。⇒ `application.yml` 必须写 `spring.flyway.table: flyway_schema_history_follow`（AGENTS.md §6.3 问题 4）。⚠️ 该改法**只保证配置层不撞**，「运行时真的按它建表」尚未在真 PG 上多模块对启动验证过 |
| 3 | ⚠️ **DDL 机制选型（Flyway vs `SchemaInitializer`）尚未拍板** | 本文件选 Flyway（理由见 §12.1）。但 Go 用的是 `AutoMigrate`（幂等重放），与 `users-common/ddl/SchemaInitializer` 的形态**更接近**。选 Flyway 的代价：日后若 follow 需要 `ALTER`，必须引入版本化流程；选 `SchemaInitializer` 的代价：与域内 3 个 Flyway 先例不一致。**需拍板** |
| 4 | 🔴 **`GoStylePagination` 是包私有，跨模块不可复用** | `message/message-rpc/.../mapper/GoStylePagination.java:43` 声明为 `final class`（无 `public`）⇒ follow-rpc **无法 import**。两个选项：(a) 在 follow-rpc 内新建同名副本（本文件的选择，代价是第 2 份实现，DRIFT-010 同类风险）；(b) 上提到 `com.rag.common.persistence`（更干净，但**跨域改动，超出 follow 迁移范围**）。⇒ 本文件按 (a)，并**必须**用守门测试锁住行为；⚠️ 更稳妥的是在实施时顺带把 (b) 提上 issue |
| 5 | ⚠️ **`FollowRpcProperties` 的默认值不能依赖 struct tag** | Go 侧 `ListConf` / `RecommendConf` 用 `json:",default=20"` 等 struct tag（`config.go:15-23`），而 `follow.yaml:43-51` **全部显式写了值** ⇒ 当前两边一致。但 `yaml` 一旦缺某键，Go 会套 tag 默认值而 Java 会用 `int` 的 0 ⇒ `max-limit=0` 会让 `normalizeListReq` 的 `maxLimit > 0` 判定失效（`helper.go:51`）⇒ **上限钳制被静默关闭**。⇒ Java 侧必须给这 6 个字段显式默认值 |
| 6 | ⚠️ **`ExistsAnyBlock` 的 `COUNT(*)` 是否保留** | Go 用 `COUNT(*)` 再 `count > 0`。Java 侧用 `SELECT EXISTS(...)` 或 `SELECT 1 … LIMIT 1` 语义等价但代价不同。本文件建议**照抄 `COUNT(*)`** 以做到零行为风险；若改用 `EXISTS`，必须在 §3.2 留痕说明「Go 未使用 EXISTS」 |
| 7 | ⚠️ **`FollowSchemaGate` 不建的决定依赖「follow 两表无触发器/CHECK」** | 该结论基于 `data_model.go:6-10` 的 gorm tag 只有 `primaryKey` / `not null` / `index` / `uniqueIndex` / `autoCreateTime` / `autoUpdateTime`。若日后给 follow 加 CHECK 约束或触发器，**必须补一个 Gate 类**，否则「约束缺失 ⇒ 静默可写」会成为新的漂移来源 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| **GORM `AutoMigrate` 生成的确切 DDL** | `gorm.io/driver/postgres` **不在本仓库**。`id` 是 `bigserial` 还是 `int8` + sequence？`create_time` / `update_time` 是 `timestamptz` 还是 `timestamp`？索引名是 `idx_follow_relation_user_id` 还是别的？`ON CONFLICT (user_id,target_id)` 依赖的**唯一约束/唯一索引**是否存在且顺序一致？⇒ **必须在实施时用真 PG 对拍 Go 侧 `AutoMigrate` 的产物**，不能凭 GORM 记忆写脚本（AGENTS.md §3.3.1：struct tag 只是声明点，不是消费点） |
| **`Postgres.Mode: "disable"` 的确切语义** | 它被拼进 DSN 的 `sslmode` 位置（`db.go:22,28`），即 `sslmode=disable`。字段名与语义不符，容易被误读成「迁移模式」。RAG 侧 JDBC URL 不需要 `sslmode`，但若将来 PG 启用 TLS，此字段会**静默失效** |
| **`TimeZone=Asia/Shanghai` 对 `create_time` 排序的影响** | `timestamptz` 的排序与时区无关，但 **`CURRENT_TIMESTAMP` 的字面量表示**与会话时区有关。若 RAG 侧未设会话时区，同一批插入的 `create_time` 可能落在不同微秒 ⇒ **加剧 §12.1 记录的「无次级键」并列问题**。真值需真 PG 探针 |
| **JVM 时区与 `timestamptz` 映射** | `OffsetDateTime` vs `LocalDateTime` 的选择会影响读取精度。与 AGENTS.md §0 记录的 users 域 `TIMESTAMP⇒TIMESTAMPTZ` 修复同源，但 follow 两表**尚未有真值基线** ⇒ 实施时需与 users-rpc 的 `UserRpcUsersUserTimestamptzParityTest` 同款守门 |
| **Flyway 账本表名改动后的运行时行为** | AGENTS.md §6.3 问题 4 已声明「配置层已修、运行时 `UNKNOWN`」。follow 是第四个使用者，需实测四个模块对同一 PG 对启动，确认四本账互不干扰 |
| **推荐算法的 N+1 是否要批量化** | 最坏 103 次 DB 往返（§12.3）。改成批量 `IN` 查询是**新增优化**，会改变 SQL 形态、可观测的 DB 调用次数、以及「同层内按升序查询」的顺序语义。⇒ 是否优化需拍板；本文件按 Go 保留 |
| **`ListAllBlockTargets` / `ListBlockerUsers` 无上限** | 黑名单规模无上界。若某用户拉黑 10 万人，推荐请求会拉 10 万行进内存。Go 行为如此；**是否要加保护性上限需拍板**（加了就与 Go 不一致） |
| **reflection 的 dev/test 判定** | Go 侧 `c.Mode == DevMode \|\| TestMode`（`follow.go:40`），`follow.yaml` **未配置** `Mode` ⇒ 走 go-zero `RpcServerConf.Mode` 的 tag 默认值（`zrpc/config.go` 不在本仓库）⇒ **UNKNOWN**。RAG 侧 devh starter 默认注册 reflection，行为可能与 Go 的「默认不注册」不一致 |