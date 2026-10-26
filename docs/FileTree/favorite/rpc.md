# favorite-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/favorite/rpc`（**41 个非测试文件** = 31 个运行时文件 + **10 个验收资产**，本文件全覆盖）
> 目标根包：`com.rag.favorite.favorite.rpc`
> 推导日期：2026-10-23
>
> **本文件是「理论推导」，不是实施记录。** `favorite-rpc` 至今是**空壳**：
> `service/favorite/favorite-rpc/pom.xml` 只有 `<artifactId>` 与 `<properties>`，**零 `<dependencies>`**，零 Java 文件。
> `favorite.go` 是 **Go 侧唯一的 gRPC 进程入口**，另有 **3 个 `cmd/*` 独立进程**（事实投递 / 权威读 / legacy 预检）。
>
> **格式母版**：`docs/FileTree/like/rpc.md`（同样处理了「多个 Go main → 单 JVM」的合并推导）。
> **与 like-rpc 的最大差异**：like 有 Kafka，**favorite 完全没有**；like 的事实投递走 `internal/mq` 子进程，favorite 的三个 `cmd/*` 都不注册任何 gRPC 方法。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

| RAG 位置 | 实测状态（2026-10-23） |
|---|---|
| `service/favorite/favorite-rpc/pom.xml:12-18` | 存在，**零依赖**、零 build 插件 |
| `service/favorite/favorite-rpc/src/` | **不存在** |
| `service/favorite/favorite-proto/src/main/proto/favorite.proto` | ✅ 已落地。与 Go `proto/favorite.proto` **只差 option 块**（`go_package = "."` → `java_package` + `java_multiple_files`）。`package favorite` 两侧逐字一致 ⇒ `FavoriteServiceGrpc` 方法路径 `/favorite.FavoriteService/*` 与 Go 相同，**零 wire 偏离**。7 个 rpc 方法（`:100-106`）与 `server/favorite_service_server.go:26-59` 逐条对应 |
| `service/favorite/favorite-common/` | 空壳（0 个 `.java`），详见 `FileTree/favorite/common.md` |
| `service/favorite/pom.xml:14-19` | 已声明 4 个 `<module>` ⇒ **不新增 maven 模块** |

包结构依据来自本仓已落地的 rpc 侧模块：

| 参照模块 | 提供的一致性依据 |
|---|---|
| `service/like/like-rpc`（31 个 Java 文件） | `LikeRpcApplication.java:58-97`（`@SpringBootApplication` + `@ConfigurationPropertiesScan` + `@MapperScan` + `@EnableScheduling` + `@Import` 七件套 + **`GrpcServerConfigurer` 显式 `intercept()`**）；`bootstrap/{NacosRegistration,LikeSchemaGate,LikeFactProcessLogging}.java`；`domain/*Entity`；`mapper/*Mapper`（`@Select` 注解式原生 SQL，全仓**零 XML mapper**）；`mapper/JsonbTypeHandler.java:29-40`；`client/MessageRpcClient.java`；`exception/LikeExceptions.java`；`metrics/LikeRpcMetrics.java`；`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/impl/LikeServiceGrpcImpl.java:46-56`（**本行唯一写成全路径的一项** —— 其余同格条目是相对 `com/rag/like/like/rpc/` 包根的简写，而 `service/...` 开头会与**仓根相对路径**撞形 ⇒ 写成全路径才可被机检解析）；`mq/{LikeHotEventOutboxRelay,LikeFactDispatchScheduler,LikeAuthorityEndpointConfig}.java` |
| `service/message/message-rpc` | `MessageExceptions.java:16-30`（rpc 侧异常聚合形态）；`client/UserRpcClient.java:21-30`（rpc 侧 `@GrpcClient` 构造器注入）；`MessageRpcApplication` 的 `@Import` 清单与 `GrpcServerConfigurer` 缺失史（`:20-59` 的 javadoc 是本仓最完整的「五个静默失效点」清单） |
| `service/users/user/user-rpc` / `admin/admin-rpc` | `mapper/JsonbTypeHandler`；`map-underscore-to-camel-case` + `id-type: ASSIGN_ID`；多步事务抽独立 Bean 的形态 |
| `service/common` | `communityfact/{Wire,Dispatcher,AuthorityHandler,ProcessLogger}.java`；`spring/{AuthorityHandlerFactory,RpcServerInterceptorFactory,SnowflakeBean,HttpMiddlewareFilter}.java`；`observability/RpcInterceptor`；`persistence/{DeadlineQueryTimeoutInjector,SqlTimeoutStatusTranslator}`；`logger/{FatalError,Logger,WithUserID,WithArticleID}`；`snowflake/Snowflake` |

### 已有组件（跨模块可复用）—— **逐条打开核对，不是仅凭包名推断**

| RAG 组件（`文件:行号`） | 与 favorite 的字段级核对 | 判定 |
|---|---|---|
| `communityfact/Wire.java:120-132` `Event` | 9 字段 `event_id/event_type/schema_version/producer/aggregate_id/aggregate_version/operation_id/occurred_at/payload` 与 Go `favorite_delivery.go:26-36` `FavoriteWireEvent` **逐字段逐 tag 一致** | **REUSE** |
| `Wire.java:135-145` `TechnicalReceipt` | 7 字段与 Go `:38-46` 一致 | **REUSE** |
| `Wire.java:37-61` `DeliveryStatus` | `0/1/2/3` 与 Go `favorite_fact.go:15-20`（`FavoriteFactPending/Sent/Failed/Blocked`）一致 | **REUSE** |
| `Wire.java:323-336` `canonicalHash` | JCS → SHA-256 → hex，与 Go `favorite_authority.go:179-186` `favoriteJCSHash` 同 | **REUSE** |
| `Wire.java:342-358` `validateReceipt` | 与 Go `favorite_delivery.go:118-126` `validFavoriteReceipt` 同（eventId/producer/`accepted`/receiptId/hash 正则/offset≥1/RFC3339） | **REUSE** |
| `Wire.java:298-317` `strictDecode` | `DisallowUnknownFields` + 拒尾随 JSON，与 Go `favorite_authority.go:60-70` `strictFavoriteJSON` 同 | **REUSE** |
| `Wire.java:175-292` `DCEventSender` | `create()` 校验 endpoint scheme/host/path=`/v1/events` + token≥32；`send()` 只发 `Idempotency-Key: event_id` + `Authorization: Bearer`；非 200/201 抛 `DataCenterAcceptanceRejectedException`；读 body 上限 64KB —— 与 Go `favorite_delivery.go:54-92` 一致 | **REUSE** |
| `Wire.java:74-92` `FrozenEnvelopeBlockedException` + `ERR_FROZEN_ENVELOPE_BLOCKED` | 对应 Go `favorite_delivery.go:22` `ErrFavoriteFactBlocked`（类型识别，非文案识别） | **REUSE** |
| `Dispatcher.java:36-44,64-67,228-271` | 批次 **16**、节奏 **1s**、单次 **10s deadline**、`once` 分支、`DispatcherHandle.stop()` —— 与 Go `cmd/fact-dispatch/main.go:57-89` 逐条对齐（`:67` ticker 1s、`:70` `for i := 0; i < 16; i++`、`:71` `WithTimeout(10s)`） | **REUSE** |
| `AuthorityHandler.java:48-50,96-108,131-175` | `AuthorityLookup` 函数式接口 + `create(producer, token≥32, lookup)` + 恒定时间比较 + 重复 header 拒绝 | ❌ **favorite 不 REUSE** —— 共享层的 `AuthorityLookup` 对应 Go `service/common/communityfact/authority_http.go:18` 的函数类型（`:23` `NewAuthorityHandler`）；Go 侧**只有 like / comment 在用**（`service/like/rpc/cmd/fact-authority/main.go:71` · `service/comment/rpc/cmd/fact-authority/main.go:71`），**`service/favorite/` 对 `AuthorityLookup` / `NewAuthorityHandler` 零引用** ⇒ favorite 走**自持 path 参数 servlet**（见下方「wire 形态冲突」） |
| `spring/AuthorityHandlerFactory.java:30,39-49` | 固定路径 `/internal/v1/community/facts`（`:30` `AUTHORITY_PATH`），query 取参 | ❌ **favorite 不采用**（该 query 形态是 like / comment 的契约）⇒ 走下方自持 path 形态；**见下方冲突** |
| `communityfact/ProcessLogger.java` | 进程级 OBS-r3 JSON 日志 + 本地 OTel；logger 名由 `service.env.component` 拼，同 JVM 可多份 | **REUSE** |
| `spring/SnowflakeBean.java:28-31,52` + `snowflake/Snowflake.java:182` | 对应 Go `snowflake.GetID()`（`create_favorite_folder_logic.go:89`、`create_favorite_logic.go:136`） | **REUSE**（只用于 folder/favorite ID，**不用于游标** —— favorite 无游标） |
| `logger/WithUserID.java:21-41` / `WithArticleID.java:14-35` | 对应 Go `logic/helper.go:48-54` | **REUSE** |
| `logger/FatalError.java` | 「抛出让异常冒泡出 main → JVM 非零退出」，正是 Go `favorite.go:34` `os.Exit(2)` 的等价物 | **REUSE**（见 §12.5 `bootstrap/FavoriteSubjectRefGate`） |
| `spring/RpcServerInterceptorFactory.java` + `observability/RpcInterceptor.java` | 对应 Go `favorite.go:49` `AddUnaryInterceptors(observability.NewUnaryServerInterceptor(...))` | **ADAPT** —— devh starter **不自动注册**，必须配 `GrpcServerConfigurer`（`LikeRpcApplication.java:94-97` 的注释） |
| `persistence/{DeadlineQueryTimeoutInjector,SqlTimeoutStatusTranslator}` | SQL 超时取消 + `57014` → `DEADLINE_EXCEEDED` | **REUSE** |

#### ✅ wire 形态冲突：authority 端点（favorite 已按 Go 原样落地，见 §12.7 不通过项 #1）

Go favorite 有**自己的** authority handler（`internal/authority/http.go`），形态是 **path 参数**：

```
GET /internal/v1/favorite/facts/{producer}/{event_id}     (:32)
GET /internal/v2/favorite/facts/{producer}/{event_id}     (:63)
```

RAG 全仓的 `AuthorityHandlerFactory` 是**另一个** handler，形态是 **query 参数 + 固定路径**：

```
GET /internal/v1/community/facts?producer=…&event_id=…   (AuthorityHandlerFactory.java:30)
```

核对依据：Go 侧的通用 handler 在 `service/common/communityfact/authority_http.go:28,50`（`mux.HandleFunc("GET /internal/v1/community/facts"…)` + `r.URL.Query()["producer"]`），favorite **没有**用它，而是自己写了 path 参数版。

⚠️ 实证（**2026-10-26 复核：原写两条均已过期**）：原写「`grep -rn "ServletRegistrationBean" --include=*.java service/`（排除 `service/common`）**零命中**」与「**RAG 侧从未真正装配过任何一个 authority 端点**」——
① `ServletRegistrationBean` 现有命中 **favorite-rpc 7 处**（`fact/FavoriteAuthorityEndpointConfig.java`：`:15` import + `:160` / `:166` / `:170` / `:176` / `:179` / `:183`）+ **comment-rpc 2 处**（`bootstrap/CommentAuthorityEndpointConfig.java`）；
② **favorite 已落地自己的端点**：`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/fact/FavoriteAuthorityEndpointConfig.java`（585 行）按 Go 原样写 path 参数 v1/v2 servlet（`V1_PREFIX` :124 / `V2_PREFIX` :125 / `FavoriteAuthorityServlet` :188），整类 `@ConditionalOnProperty(prefix = "favorite.fact.authority", name = "enabled", havingValue = "true")`（:118）⇒ **默认关**（`service/favorite/favorite-rpc/src/main/resources/application.yml:200` `enabled: false`）⇒ 默认仍不新增任何 HTTP 端口。
✅ **仍然为真的那一条**：`like-rpc/mq/LikeAuthorityEndpointConfig.java` 至今是一个 4 行的 `[骨架占位 · 未实现]` 文件，第 3 行写着「⚠️ 监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector（wire 面变更，待拍板）」。
⇒ **favorite 的决策已落地（= 选项 ② 自持 servlet）**；剩下的只有「要不要给纯 gRPC 进程开 web connector」这一项**部署形态变更**（台账 §5-U1）。见 §12.7 不通过项 #1。

#### 🔴 `Wire.AuthorityFact` 的 `subject_ref` 是 **v2 形状**

`Wire.java:147-152` 的 `SubjectRef` 只有 `{issuer, subject_id}` 两个字段；`Wire.java:371-387` 的 `parseRTWSubject` 解析 `"rtw.identity/platform/{uid}"` 后**把它压平成 v2 形状**返回。

而 Go favorite 的 v1 权威响应里 `subject_ref` 是 **三字段** `{authority_id:"rtw.identity", tenant_id:"platform", subject_id:"1001"}`（`favorite_authority.go:106-112` `FavoriteSubjectRef`，`FACT_AUTHORITY_ACCEPTANCE.md:33,45` 的样例逐字一致）。

⇒ **`/internal/v1/favorite/facts/...` 的响应体不能用 `Wire.AuthorityFact`**，必须自持一个 v1 类型。`/internal/v2/...` 才可以用。

### 已确认的框架机制

- `@GrpcService` / `@GrpcClient`（net.devh 3.1.0.RELEASE）
- MyBatis-Plus：`@MapperScan` + `@TableName` / `@TableId` / `@TableField(typeHandler=…)`；**注解式原生 SQL，全仓零 XML mapper**（`LikeDomainFactOutboxMapper.java:37-43,53-59,89-90` 是范式）；`<>` 需 CDATA 孪生常量；`<where>` 不可用
- jsonb 必须走 `JsonbTypeHandler`（`LikeDomainFactOutboxEntity` 的 payload/delivery_envelope 是 jsonb）+ `@TableName(autoResultMap = true)` + `ps.setObject(i, json, Types.OTHER)`（`JsonbTypeHandler.java:35-40`）
- **无 MyBatis-Plus 分页拦截器** —— favorite **也不需要**（`GetFavoriteFolders` / `GetFavoritesByFolder` 全量返回，无 `LIMIT`）
- gRPC 拦截器须 `GrpcServerConfigurer` 显式 `intercept()`，**不自动注册**（`LikeRpcApplication.java:83-97`）
- `@Transactional` **self-invocation 静默失效**（§3.3）—— Go 的 `InsertFavorite` / `DeleteFavoriteByFavoriteId` / `DeleteFolderCascade` 都是 model 内部方法调用，Java 侧必须抽独立 Bean
- Boot 4：`spring-boot-flyway` 必需（`like-rpc/pom.xml:217-227`）；Jackson 2 `ObjectMapper` 需自行装配（`config/Jackson2ObjectMapperConfiguration.java`）
- Nacos 服务发现；Micrometer + `MeterRegistry`
- **favorite 无 Kafka** ⇒ 不引 `spring-kafka` / `spring-boot-kafka`，也**不需要** commons-pool2 的 Kafka 侧教训（Redis 侧 favorite 也不需要）

### 当前 Component 可以直接复用的能力

- `common/communityfact/Wire`（信封 + 回执校验 + JCS hash + DC sender）
- `common/communityfact/Dispatcher`（1s / 批次 16 / 10s / once）
- `common/communityfact/ProcessLogger`（进程级 OBS-r3 日志 + 本地 OTel）
- `common/spring/{AuthorityHandlerFactory（形态待定）,RpcServerInterceptorFactory,SnowflakeBean}`
- `common/observability/{RpcInterceptor,Observability}`
- `common/persistence/{DeadlineQueryTimeoutInjector,SqlTimeoutStatusTranslator}`
- `common/logger/{Logger,WithUserID,WithArticleID,FatalError}`
- `common/exception/BusinessException`（api 侧）；rpc 侧错误一律经 `favorite-common` 的 gRPC 转换
- `users/user/user-proto`（`GetUserReq{uid}` / `GetUserResp{user, found}` / `UserInfo.status` **optional**）—— `users.proto:20-27,17`
- `article/article-proto`（`GetArticleRequest{article_id, incr_view, public_only, requester_id}` / `Article.ext_info` / `ArticleStatus.PUBLISHED=2`）—— `article.proto:55-58,35,8-15`
- `message/message-common/MessageGrpcError.grpcError(Status.Code,int)`（`:135`）—— **签名与 favorite 的 `GRPCError(grpcCode, bizCode)` 完全一致**，是 5 个已落地 `-common` 里唯一方向正确的先例

### 需要进一步确认的组织约定

**【关键推导】4 个 Go `main` → 1 个 Spring Boot JVM**

| Go 进程 | 入口 | 注册了什么 | 读写 |
|---|---|---|---|
| P1 gRPC server | `favorite.go:26-56` | `pb.RegisterFavoriteServiceServer`（7 RPC，`favorite.proto:100-106`） | DB 读写 4 张表、UserRpc、ArticleRpc |
| P2 fact-dispatch | `cmd/fact-dispatch/main.go:22-32` | **零服务注册**，只跑 `store.DispatchFavoriteFactOnce` 循环 | DB 读 + DC HTTP 出网 |
| P3 fact-authority | `cmd/fact-authority/main.go:21-29` | **零服务注册**，只起 1 个 `http.Server`（`:56-57`） | DB 读 + HTTP 入 |
| P4 legacy-preflight | `cmd/fact-legacy-preflight/main.go:18-54` | **零服务注册**，只跑一次只读报表 | DB 只读 |

**结论：P2 / P3 / P4 必须落成 `favorite-rpc` 内的 Spring 组件，而不是独立 deployable。** 依据（代码里可验证，不是"看起来能合并"）：

1. **P2/P3/P4 不注册任何 gRPC 方法**（`favorite.go:43` 只注册 `FavoriteServiceServer`；三个 `cmd/*` 一个 `@GrpcService` 都没有）。它们没有对外的强类型契约，只有一个循环 / 一个 Servlet / 一次报表。
2. **三者与 P1 共用同一张 PostgreSQL**：P1 走 `c.Postgres`（`service_context.go:20-28`），P2/P3/P4 走 `os.Getenv("FAVORITE_DATABASE_URL")`（`fact-dispatch/main.go:35`、`fact-authority/main.go:32`、`fact-legacy-preflight/main.go:20`）。四者读写的都是 `favorite_folder` / `favorite_item` / `favorite_fact_outbox` / `favorite_legacy_fact_marker` 四张表。
3. **`Dispatcher` 与 `AuthorityHandler` 在 RAG 侧本来就是纯库**（`Dispatcher.java:64` 静态工厂、`AuthorityHandler.java:96` 返回 `HttpServlet`），设计给"宿主进程"用。Go 侧把 dispatch 拆成独立进程的理由写在 `fact-dispatch/main.go:2`：「It is a separate process so favorite RPC availability does not depend on DC」—— 这是**架构意图**，不是技术约束。
4. **P4 是纯只读**（`favorite_legacy_preflight.go:30-96` 全程 `ReadOnly: true` 事务，`never mutates` 见 `:27-28` 注释）⇒ 合并无写风险。

**合并后的部署拓扑后果（必须显式承认，不得当作"纯收益"）：**

| 维度 | Go（4 进程） | Java（1 JVM） | 后果 |
|---|---|---|---|
| 部署单元 | 4 | 1 | 失去独立扩缩容：dispatch 的资源需求与 gRPC 争同一堆内存/CPU |
| 多副本下的 dispatcher | 独立进程，可与 rpc 副本数解耦 | rpc 扩到 N 副本 → N 个 dispatcher | **安全**：`favorite_delivery.go:141-147` 的 `FOR UPDATE SKIP LOCKED` + `aggregate_version=1 OR EXISTS(前驱已 status=1)` 本就是为多副本设计的；但吞吐上限从"1 进程"变成"每 JVM 16 × N 副本" |
| `-once` 单次模式（`fact-dispatch/main.go:23,57-66`） | 命令行 flag，适合 cron / k8s CronJob | 需降级为配置项 | **UNKNOWN**：保留 CronJob 部署形态还是改为常驻循环，需拍板 |
| authority HTTP 监听地址 | 独立 `FAVORITE_AUTHORITY_LISTEN`（`fact-authority/main.go:33`） | favorite-rpc **原本零 HTTP 端口**，要新增 | **wire 面变更**：新增 web connector 还是 management port？**UNKNOWN，需拍板** |
| legacy-preflight 的 **exit code 语义**（`fact-legacy-preflight/main.go:24,30,38,50`：`exit 2` = 未裁决 / `exit 1` = 读取失败 / `exit 0` = clear） | CLI 退出码，CI 直接判 | Spring 进程内**没有退出码** | ⚠️ 必须显式设计：要么保留独立 CLI 形态（`ApplicationRunner` + 显式 `System.exit`），要么改为"打结构化报告 + 由人工判读"。**UNKNOWN，需拍板** |
| 进程级 OBS 日志身份 | 4 个独立 `service` 值（`rtw-favorite-fact-dispatch` / `-fact-authority` / `-legacy-preflight` / gRPC 走 `c.Name`） | 1 个 SLF4J 树 | **可保真**：`ProcessLogger` 的 logger 名由 `service.env.component` 拼，同 JVM 可建多份独立 appender。前提是**不用 SLF4J 全局 MDC** |
| 数据库连接池 | 每进程独立 | 共享一个 HikariCP | P2 的 dispatch 事务 `Begin → HTTP 出网 → Commit`（`favorite_delivery.go:135-195`）**持有行锁跨网络 I/O**，与 gRPC 共池时故障域耦合加剧 |
| Flyway 归属 | `AutoMigrate`（`db.go:33`）无状态 | 有状态账本 | ✅ 按 AGENTS.md §6.3 问题 4（2026-10-23）：favorite-rpc / message-rpc / like-rpc / article-rpc 同连 `first_db`、同 schema `public`、V1–V4 直接撞号 ⇒ **必须配 `spring.flyway.table: flyway_schema_history_favorite`**。先例：`like-rpc/application.yml:56-61` |

| 其他待确认项 | 状态 |
|---|---|
| `AuthorityHandlerFactory` 的 wire 形态 | **UNKNOWN**（见上方冲突说明 + §12.7 #1）。三条路：① 用通用 handler（接受 path/query 偏离，须登记 Intentional_Deviation）；② 写 favorite 专用 servlet（保住 path 参数，但 Web 全仓零先例）；③ 不实现 authority 端点（P3 不迁，**须签字**） |
| `Wire.AuthorityFact` v1 形状 | 确定：**必须自持 v1 类型**（`Wire.SubjectRef` 只有 2 字段，Go v1 是 3 字段）。不是 UNKNOWN |
| `favorite_fact_outbox` 的 DDL | Go 侧 `db.go:33` 的 `AutoMigrate` **只覆盖 3 个实体**（`FavoriteFolder` / `FavoriteItem` / `FavoriteFactOutbox`），**不含** `FavoriteLegacyFactMarker`（见 `favorite_fact.go:28-39`）。⇒ **DDL 事实源有两处**：AutoMigrate + 4 个手写 SQL。逐条见 §12.3 |
| `Success(200)` 在 rpc 侧的使用 | grep 实测：**rpc 侧零使用**（唯一使用点在 api 侧的 7 个 logic 返回值）。`favorite-common` 仍须保留该常量（`errmsg.go:9`），但 rpc 侧不需要 |

---

## 12.2 第二部分：Go 原始文件树

```
service/favorite/rpc/                                              （41 个非测试文件）
│
├── 【运行时 31 个】
├── favorite.go                                     63 行  gRPC server main + **v2 启动拒绝门禁**
├── etc/favorite.yaml                               47 行  ListenOn 8082 / Postgres / Telemetry / DevServer 16062 / Etcd / UserRpc / ArticleRpc
├── favoriteservice/favorite_service.go              88 行  goctl 生成的**客户端** stub（7 方法）
├── cmd/
│   ├── fact-dispatch/main.go                       90 行  P2 事实投递（1s / 批次 16 / -once）
│   ├── fact-authority/main.go                      71 行  P3 权威读（私有 HTTP）
│   └── fact-legacy-preflight/main.go               54 行  P4 legacy 只读预检（exit 2 = 未裁决）
└── internal/
    ├── config/config.go                            19 行  Config{RpcServerConf, Postgres, UserRpc, ArticleRpc, SubjectRefV2Facts}
    ├── svc/service_context.go                      40 行  FavoriteModel + UserRpc + ArticleRpc（+ v2 option）
    ├── server/favorite_service_server.go            59 行  7 个方法，每个 2 行：new logic + 转发
    ├── metrics/metrics.go                          72 行  4 个 CounterVec + 3 个 Observe 函数
    ├── authority/http.go                          100 行  ★ favorite 自持的 v1/v2 权威读 HTTP handler
    ├── logic/
    │   ├── helper.go                              202 行  ★ 标签常量 + normalize + toProto + ensureUserExists + resolveArticleSnapshot + articleCover
    │   ├── create_favorite_folder_logic.go        118 行
    │   ├── create_favorite_logic.go               170 行  最复杂：文件夹归属 + 文章公开修订冻结 + 事实写入
    │   ├── delete_favorite_folder_logic.go         96 行
    │   ├── delete_favorite_logic.go                96 行
    │   ├── get_favorite_folders_logic.go           70 行
    │   ├── get_favorites_by_folder_logic.go        98 行
    │   └── update_favorite_folder_name_logic.go    127 行
    └── model/
        ├── vars.go                                 7 行  ErrorNotFound sentinel
        ├── data_model.go                          35 行  FavoriteFolder / FavoriteItem 两张表
        ├── db.go                                  64 行  InitDB + AutoMigrate(3) + target_id bigint→varchar
        ├── favorite.go                            118 行  FavoriteModel 8 方法（7 读 + InsertFolder）
        ├── favorite_fact.go                      378 行  ★ outbox + legacy marker + assert schema + 3 个事务写
        ├── favorite_delivery.go                   202 行  ★ wire event / receipt / DC sender / DispatchOnce
        ├── favorite_authority.go                 282 行  ★ 严格 JSON / JCS hash / 权威事实校验
        ├── favorite_legacy_preflight.go           96 行  只读 RR 快照报表
        ├── 001_favorite_fact_outbox.sql           15 行  建表 + CHECK + UNIQUE + 部分索引
        ├── 002_favorite_fact_delivery.sql         25 行  5 个回执列 + **不可变触发器**
        ├── 003_favorite_target_revision.sql        5 行  favorite_item.target_revision
        └── 004_favorite_legacy_fact_marker.sql    27 行  legacy 标记表 + **不可变触发器**
│
└── 【验收资产 10 个 · 非运行时 · 见 §12.5 处置结论】
    ├── DELIVERY_ACCEPTANCE.md                     23 行
    ├── FACT_AUTHORITY_ACCEPTANCE.md               77 行
    ├── FAVORITE_FACT_ACCEPTANCE.md                13 行
    ├── FAVORITE_FULL_CHAIN_ACCEPTANCE.md          21 行
    ├── FAVORITE_REVISION_ACCEPTANCE.md            14 行
    ├── FAVORITE_SUBJECTREF_V2_ACCEPTANCE.md       37 行
    ├── TWO_USER_COVERAGE_ACCEPTANCE.md             7 行
    ├── acceptance.sh                              29 行  自建隔离 PG16 → go test -race → go vet
    ├── acceptance-dc.sh                           73 行  另拉起**真实 DataCenter `cmd/platform`**
    └── acceptance-full.sh                         16 行  另拉起 **BTW worker**（跨仓）
```

> ⚠️ `rpc/pb/*.pb.go`（1064 + 349 行）是 **protoc 生成物**，按 §12.2「只列与行为/职责/调用链有关的文件」排除。对应的 RAG 侧生成物在 `favorite-proto`（已落地）。

---

## 12.3 第三部分：Go 文件职责分析

### `favorite.go`（P1 进程装配）

- 🔴 **`:31-35` v2 启动拒绝门禁**：`if c.SubjectRefV2Facts && c.Mode != service.DevMode && c.Mode != service.TestMode { … os.Exit(2) }`
  **发生在 `logx.MustSetup`（`:36`）与 `NewServiceContext`（`:37`）之前** ⇒ 进程在**打开数据库之前**就退出。
  ⇒ 这是 `rpc.bootstrap` 的职责，不是 config 的职责。Spring 侧落点见 §12.5 `bootstrap/FavoriteSubjectRefGate`（用 `common/logger/FatalError` 抛，让异常冒泡出 `main`）。
- `:39` `metrics.InitMetrics(&c)`；`:40` `observability.DisableNativeRpcTimeout(&c.RpcServerConf)`；`:49` `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))`
- `:42-48` `zrpc.MustNewServer` + `favoritepb.RegisterFavoriteServiceServer`；**reflection 仅在 DevMode/TestMode 注册**（`:45-47`）
- `:52-54` 启动日志带 `subject_ref_version`（由 `:58-63` 的 `favoriteSubjectVersion` 产出 `"v1"` / `"v2"`）

### `etc/favorite.yaml` + `internal/config/config.go`

`ListenOn: 0.0.0.0:8082`（`yaml:2`）；`Postgres{Host: postgres, Port: 5432, User: admin, Password: ${POSTGRES_PASSWORD}, DBName: first_db, Mode: disable}`（`yaml:9-15`）；`DevServer` 16062（`yaml:23-30`）；`Etcd.Key: favorite.rpc`（`yaml:32-35`）；`UserRpc` / `ArticleRpc`（`yaml:37-47`）。
`config.go:5-19` 的 `Config` = `zrpc.RpcServerConf` + `Postgres` + `UserRpc` + `ArticleRpc` + **`SubjectRefV2Facts bool json:",optional"`**（`:17-18`，注释明写 "Omitted is v1"）。
⚠️ `Mode` 来自 `zrpc.RpcServerConf` 内嵌的 `service.ServiceConf`；**不在本仓库** ⇒ `UNKNOWN`。
⚠️ `Password` 用 `${POSTGRES_PASSWORD}` 占位 ⇒ 实际值来自环境变量，`UNKNOWN`。

### `internal/svc/service_context.go`

`ServiceContext{Config, FavoriteModel, UserRpc, ArticleRpc}`（`:12-17`）。`NewServiceContext` 三件事：`model.InitDB`（`:28`）、按 `c.SubjectRefV2Facts` 追加 `model.WithSubjectRefV2Facts()` option（`:29-32`）、两个 `zrpc.MustNewClient`（`:37-38`）。
**无 Redis、无 Kafka** ⇒ favorite-rpc 是 **纯 gRPC + PG + 2 个下游 gRPC + 1 个外部 HTTP（DC）**。

### `favoriteservice/favorite_service.go`

goctl 生成的**客户端** stub：`FavoriteService` 接口 7 方法（`:34-42`）+ `defaultFavoriteService` 持 `zrpc.Client`（`:44-47`），每个方法 `__.NewFavoriteServiceClient(m.cli.Conn())` 后转发。
⇒ 对应 RAG 的 `@GrpcClient("favorite-rpc")`，落点在 `favorite-api/client/`，**不在 favorite-rpc 内**。

### `internal/server/favorite_service_server.go`

7 个方法（`:26-59`），每个**只有 2 行**：`logic.NewXxxLogic(ctx, s.svcCtx)` + 转发。**零业务逻辑。**
⇒ 对应 `service/impl/FavoriteServiceGrpcImpl`，但因为 Java 的 `FavoriteServiceImplBase` 是 `StreamObserver` 形态，异常转换点就落在这个类里（先例 `LikeServiceGrpcImpl.java:46-56` 的「错误出口唯一转换点」）。

### `internal/metrics/metrics.go`

| 指标 | Go 定义 | labels | 记录点 |
|---|---|---|---|
| `favorite_request_total` | `:14-19` CounterVec | `module, action, result` | `ObserveRPC`（`:56-64`） |
| `favorite_request_seconds_counter` | `:22-27` CounterVec | `module, action` | `ObserveRPC`（`:63`，`Add(time.Since(started).Seconds())`） |
| `favorite_ops_total` | `:30-35` CounterVec | `module, action, result` | `ObserveOp`（`:66-68`） |
| `favorite_db_error_total` | `:38-43` CounterVec | `module, action, type` | `ObserveDBError`（`:70-72`） |

`InitMetrics`（`:46-54`）**注销 Go / process collector 再注册 4 个**。

⚠️ **三个必须原样保留的怪癖**：

1. **`favorite_request_seconds_counter` 是 Counter 不是 Histogram/Timer**（Go `:22-27` 用 `CounterVec` + `Add(seconds)`）。Micrometer 的 `Counter` 在 Prometheus 侧会**自动追加 `_total`** ⇒ 输出名变成 `favorite_request_seconds_counter_total`。这是指标名漂移，须在 §3.2 留痕。
2. 🔴 **21 处错误返回把 `result` 记成 `success`**。每个 logic 头部是 `defer func(){ metrics.ObserveRPC(module, action, started, err) }()`（如 `create_favorite_folder_logic.go:38-41`），读的是**命名返回值 `err`**；而 **21 处 `return nil, favoritecommon.GRPCError(...)` 没有先赋值给 `err`**（grep 实证，逐条见下），⇒ `err == nil` ⇒ `ObserveRPC` 里 `result = "success"`（`:57-60`）。
   受影响的 21 处（文件:行号）：
   `create_favorite_folder_logic.go:86,94,108,112`；`create_favorite_logic.go:92,133,141,160,164`；`delete_favorite_folder_logic.go:71,86,90`；`delete_favorite_logic.go:71,86,90`；`get_favorite_folders_logic.go:59`；`get_favorites_by_folder_logic.go:71,87`；`update_favorite_folder_name_logic.go:81,108,117,121`。
   对照：另有 23 处 `err = favoritecommon.GRPCError(...)` **有**赋值，计数正确。
3. 🔴 **两处 `module` label 写错**：`create_favorite_logic.go:89` 与 `get_favorites_by_folder_logic.go:68` 都是 `metrics.ObserveDBError(folderModule, "select", "db")` —— 位于 **item** 流程里（同一函数的 `ObserveOp` 都用 `itemModule`），label 却用 `folderModule`。
⇒ 三条都**按 Go 原样保留**并标注，**不修正**（修正 = 指标契约变更，需拍板）。

### `internal/logic/helper.go`（202 行，本域最密的单文件）

| 段 | 行号 | 内容 | 归属判断 |
|---|---|---|---|
| 标签常量 | `:26-40` | `folderModule="favorite_folder"`、`folderCreate/Delete/List/UpdateName`、`itemModule="favorite_item"`、`itemCreate/Delete/ListByFolder`、`resultSuccess/resultFail` | 散落在 8 处调用点，**单点定义**的必要性最强 |
| `articleSnapshot` struct | `:42-46` | `Title / Cover / RevisionID` | 只服务 `resolveArticleSnapshot` |
| `userLogOption` / `articleLogOption` | `:48-54` | 日志选项 | REUSE `common/logger/*` |
| `normalizeFolderName` | `:56-58` | `strings.TrimSpace` | — |
| `normalizeTargetType` | `:60-62` | `strings.ToLower(strings.TrimSpace(...))` | 🔴 **`target_type` 被强制小写**；DB 唯一键 `uk_folder_target` 建在规范化后的值上 |
| `toProtoFolder` / `toProtoItem` | `:64-85` | model → pb，`CreateTime.UnixMilli()` / `UpdateTime.UnixMilli()` | **Unix 毫秒**，不是秒 |
| `isUniqueViolation` | `:87-90` | `errors.As(err, &pgErr) && pgErr.Code == "23505"` | 🔴 **只认 PG SQLSTATE 23505**，Java 侧要读 `SQLException.getSQLState()` |
| `ensureUserExists` | `:92-124` | 见下 | 独立职责 |
| `resolveArticleSnapshot` | `:126-178` | 见下 | 独立职责 |
| `articleCover` | `:180-202` | `cover_image_url`，否则按 `["cover","cover_url","coverUrl","cover_image_url","coverImageUrl"]` 顺序取 `ext_info` | 只服务上一条 |

**`ensureUserExists`（`:92-124`）的五条分支** —— 这是跨域身份校验，**每条都有独立 gRPC code**：

| 条件 | 行号 | 输出 |
|---|---|---|
| `GetUser` 报错且 `st.Code()==NotFound` | `:103-106` | `GRPCError(NotFound, ErrorUserNotFound=5101)` |
| `GetUser` 报错（其他 code） | `:107` | `GRPCError(Internal, ErrorServerCommon=5001)` |
| `resp == nil \|\| !resp.Found \|\| resp.User == nil` | `:109-112` | `GRPCError(NotFound, 5101)` |
| `resp.User.Uid != userID \|\| resp.User.Status == nil` | `:113-116` | `GRPCError(**Unavailable**, 5001)` —— 🔴 故意用 `Unavailable` 而不是 `Internal` |
| `resp.User.GetStatus() != 0` | `:117-120` | `GRPCError(PermissionDenied, ErrorForbidden=5005)` |

调用请求固定为 `GetUserReq{Uid: userID}`（`:98`）。`resp.User.Status` 是 **`optional int64`**（RAG `users.proto:17`）⇒ Go 的 `Status == nil` 是 presence 检查，Java 侧必须用 `hasStatus()` 而不是 `getStatus() != 0`。这正是 AGENTS.md §0 `users` 域 2026-10-23 那条「`Login` 的 `status IS NULL` fail-open」的同型陷阱。

**`resolveArticleSnapshot`（`:126-178`）** —— 文章**公开修订冻结**，是 favorite 最核心的业务规则：

1. 请求固定 `GetArticle{ArticleId: TrimSpace(targetID), IncrView: false, PublicOnly: true}`（`:130,133-137`）。🔴 **`IncrView: false`**（不涨阅读量）与 **`PublicOnly: true`** 都是 L1。
2. 报错 `NotFound` ⇒ `GRPCError(NotFound, ErrorNotFound=5002)`；其他 ⇒ `GRPCError(Internal, 5001)`（`:142-146`）
3. `resp == nil \|\| resp.Article == nil` ⇒ `GRPCError(NotFound, 5002)`（`:148-151`）
4. 🔴 `article.Id != normalizedTargetID \|\| article.Status != PUBLISHED` ⇒ `GRPCError(**Unavailable**, 5001)`（`:153-156`）
5. 修订解析（`:157-170`）：
   - `revisionID = TrimSpace(extInfo["published_revision_id"])`
   - 若非空：必须满足 `strings.HasPrefix(revisionID, articleID + ":r")` **且** 去掉前缀后是合法 uint64 **且** `number != 0` **且** `extInfo["publication_gap"] == ""` ⇒ 否则 `GRPCError(Unavailable, 5001)`
   - 若为空：必须 `extInfo["publication_gap"] == "legacy_revision_missing"` ⇒ 否则 `GRPCError(Unavailable, 5001)`
6. 返回 `Title: TrimSpace(article.Title)`、`Cover: articleCover(article)`、`RevisionID`（`:173-177`）

⚠️ 修订号来自 **`ext_info` map 的字符串约定**（`<articleId>:r<N>`），**不是 proto 字段**。RAG 侧 `article.proto:35` 的 `map<string,string> ext_info = 16` 已就位，但 **`published_revision_id` / `publication_gap` 这两个 key 的写入方是 article-rpc**，其 RAG 实现状态 **本文件未取证 ⇒ `UNKNOWN`**（见 §12.7 U2）。

### 7 个 logic 的业务规则

| logic | 参数校验 | 依赖调用 | 归属校验 | 写操作 | 业务码 |
|---|---|---|---|---|---|
| `create_favorite_folder_logic.go:37-118` | `userId<=0` ⇒ `InvalidArgument/5003`（`:51-57`）；`normalizeFolderName(name)==""` ⇒ `InvalidArgument/5202`（`:59-66`） | `ensureUserExists`（`:68-73`） | 重名预查（`:75-87`）：命中 ⇒ `AlreadyExists/5203`；非 `ErrorNotFound` 的 DB 错误 ⇒ `Internal/5007` | `snowflake.GetID()`（`:89-95`，失败 ⇒ `Internal/5009`）+ `InsertFolder`（`:102-113`，23505 ⇒ `AlreadyExists/5203`，否则 `Internal/5008`） | 200 |
| `create_favorite_logic.go:38-170` | `userId<=0 \|\| folderId<=0` ⇒ `5003`（`:54-60`）；`normalizeTargetType` 后 `targetId=="" \|\| targetType==""` ⇒ `InvalidArgument/5303`（`:62-70`） | `ensureUserExists`（`:72-77`） | 文件夹存在（`:79-93`，`ErrorNotFound` ⇒ `NotFound/5201`）+ **`folder.UserId != userId` ⇒ `PermissionDenied/5005`**（`:94-100`）；`targetType == "article"` 时 `resolveArticleSnapshot`（`:105-120`）；重查重名（`:122-134`） | `snowflake`（`:136-142`）+ `InsertFavorite`（`:154-165`，23505 ⇒ `AlreadyExists/5302`，否则 `Internal/5008`） | 200 |
| `update_favorite_folder_name_logic.go:36-127` | `userId<=0 \|\| folderId<=0` ⇒ `5003`；name 空 ⇒ `5202` | — | 存在（`:68-82`）+ 归属（`:83-89`）；**`folder.Name == name` ⇒ 直接成功返回、不写库**（`:91-95`）；换名冲突检查（`:97-109`，`existing.FolderId != folderId` 才算冲突） | `UpdateFolderNameByFolderId`（`:111-122`，23505 ⇒ `AlreadyExists/5203`，否则 `Internal/5008`） | 200 |
| `delete_favorite_folder_logic.go:36-96` | `userId<=0 \|\| folderId<=0` ⇒ `5003` | — | 存在（`:58-72`）+ 归属（`:73-79`） | `DeleteFolderCascade`（`:81-91`）：`ErrFavoriteFactMigrationBlocked` ⇒ **`FailedPrecondition/5304`**，否则 `Internal/5008` | 200 |
| `delete_favorite_logic.go:36-96` | `userId<=0 \|\| favoriteId<=0` ⇒ `5003` | — | 存在（`:58-72`）+ 归属（`:73-79`） | `DeleteFavoriteByFavoriteId`（`:81-91`）：`ErrFavoriteFactMigrationBlocked` ⇒ **`FailedPrecondition/5304`**，否则 `Internal/5008` | 200 |
| `get_favorite_folders_logic.go:34-70` | `userId<=0` ⇒ `5003`（`:45-51`） | — | — | — | 200 |
| `get_favorites_by_folder_logic.go:36-98` | `userId<=0 \|\| folderId<=0` ⇒ `5003`（`:50-56`） | — | 存在（`:58-72`）+ 归属（`:73-79`） | — | 200 |

🔴 **`FailedPrecondition` 是 7 个 logic 里唯一一个非常规 code**（其余只用 `InvalidArgument` / `NotFound` / `AlreadyExists` / `PermissionDenied` / `Unavailable` / `Internal`）。

### `internal/model/*` —— 持久化与事实链路

| 文件 | 关键内容 |
|---|---|
| `vars.go:5-7` | `ErrorNotFound = errors.New("record not found")` —— GORM `ErrRecordNotFound` 的**归一化哨兵**。全 model 的 `if err == gorm.ErrRecordNotFound { return nil, ErrorNotFound }` 都收敛到它 |
| `data_model.go:5-16` | `FavoriteFolder` → 表 `favorite_folder`；`id` 自增主键 + `folder_id` unique + `(user_id,name)` 复合唯一 `uk_user_folder_name` + `create_time`/`update_time`（`autoCreateTime`/`autoUpdateTime`） |
| `data_model.go:18-35` | `FavoriteItem` → 表 `favorite_item`；`favorite_id` unique + `(folder_id,target_id,target_type)` 复合唯一 `uk_folder_target` + `target_id varchar(255)` + `target_type varchar(50)` + **`TargetRevision *string varchar(96)`（可空，注释 `:25-26` 明写「Nil means the source had no provable published revision … do not backfill legacy rows」）** + `title varchar(255)` + `cover varchar(500)` + **只有 `create_time`，没有 `update_time`** |
| `db.go:20-41` | `InitDB`：DSN 带 **`TimeZone=Asia/Shanghai`**（`:21-28`）；`AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{})`（`:33`）—— 🔴 **只有 3 个实体，`FavoriteLegacyFactMarker` 不在内**；随后 `migrateFavoriteTargetIDToString`（`:37-39`） |
| `db.go:43-63` | `target_id` 列类型探测：查 `information_schema`，若不是 `character varying`/`text` 则 `ALTER TABLE favorite_item ALTER COLUMN target_id TYPE varchar(255) USING target_id::varchar` |
| `favorite.go:9-31` | `FavoriteModel{conn, subjectRefV2Facts}` + `FavoriteModelOption`（`:14-21`，`WithSubjectRefV2Facts` 的注释明写「only affects newly created favorites … A retract always inherits the schema frozen in that favorite's assertion」） |
| `favorite.go:33-118` | 8 方法：`InsertFolder` / `FindFoldersByUserId`（`ORDER BY create_time desc`，`:41`）/ `FindFolderByFolderId` / `FindFolderByUserIdAndName` / `UpdateFolderNameByFolderId` / `FindFavoritesByFolderId`（`ORDER BY create_time desc`，`:86`）/ `FindFavoriteByFavoriteId` / `FindFavoriteByFolderTarget`。🔴 **全部无 `LIMIT`**（无分页） |
| `favorite_fact.go:15-20` | 4 个状态常量（`0/1/2/3`） |
| `favorite_fact.go:22-23` | `ErrFavoriteOwnerMismatch` / `ErrFavoriteFactMigrationBlocked` |
| `favorite_fact.go:28-39` | `FavoriteLegacyFactMarker` 实体 → 表 `favorite_legacy_fact_marker` |
| `favorite_fact.go:44-58` | `favoriteLegacySnapshotHash`：对 `{favorite_id,user_id,folder_id}`（**十进制字符串**）+ `{target_type,target_id,target_revision}`（`NULL` 写 `null`）做 `json.Marshal` → JCS → SHA-256。🔴 **不含 title/cover**（可变） |
| `favorite_fact.go:60-76` | `FavoriteFactOutbox` 实体（13 列） |
| `favorite_fact.go:78-89` | `FavoriteSubjectRef`（**v1 三字段**）/ `FavoriteSubjectRefV2`（**v2 两字段**，注释 `:84-85` 明写「The legacy fixed platform slot is deliberately absent」） |
| `favorite_fact.go:91-133` | `favoriteFactPayload`（12 字段）/ `favoriteFactPayloadV2`（同 12 字段，`subject_ref` 换 v2）/ `favoriteEvent`（外层 9 字段） |
| `favorite_fact.go:135-162` / `:164-198` | `favoriteOutbox` / `favoriteOutboxV2`：6 条前置断言（ID>0、target 非空、version∈{1,2}、operation∈{assert,retract}、`target_revision != nil ⇒ target_type=="article" 且非空`）；`event_id = fmt.Sprintf("favorite.%d.v%d", favoriteId, version)`；`EventType = "rtw.favorite." + operation`；`Producer = "rtw.community.favorite"`；`source_ref = "rtw.favorite/<id>"`；时间 `now.UTC().Format(RFC3339Nano)` |
| `favorite_fact.go:200-210` | `newFavoriteOutbox` 按 schema 分派（`default` ⇒ `errors.New("unsupported favorite fact schema")`） |
| `favorite_fact.go:212-265` | 🔴 **`favoriteAssertionSchema`** —— 断言版本解析，本域最难的 54 行：先 `SELECT ... FOR UPDATE` 找 `aggregate_version=1`；**找不到**则查 legacy marker 并逐字段比对（7 项 + hash 正则 + `approval_ref != ""`）+ 校验无孤儿 `aggregate_version=2`（`:235-242`）⇒ 任一不符返回 `ErrFavoriteFactMigrationBlocked`；**找到了**则 strict-decode 并逐条校验 `event_id / aggregate_version / event_type / aggregate_id / schema_version ∈ {1,2} / validFavoriteDelivery / payload.subject / folder_id / target_type / target_id / target_revision`（`:245-263`）⇒ 不符同样返回 `ErrFavoriteFactMigrationBlocked` |
| `favorite_fact.go:270-296` | 🔴 **`InsertFavorite`** —— 单事务：`SELECT folder FOR UPDATE` → 归属核对（不符 ⇒ `ErrFavoriteOwnerMismatch`）→ `CREATE item` → 建 assert outbox（`schema = m.subjectRefV2Facts ? 2 : 1`，`:286-289`）→ `CREATE outbox`。注释 `:267-269` 明写「commits the existing favorite ID and its business fact in the same owner database」 |
| `favorite_fact.go:300-332` | 🔴 **`DeleteFavoriteByFavoriteId`** —— 单事务：`SELECT item FOR UPDATE` → 归属核对 → `favoriteAssertionSchema` → **legacy ⇒ 只删业务行、**不造无前驱撤回**（`:320-322`）**；非 legacy ⇒ 建 `v2 retract` outbox（**继承建立事件的 schema**）→ 删业务行 |
| `favorite_fact.go:336-378` | 🔴 **`DeleteFolderCascade`** —— 单事务：`SELECT folder FOR UPDATE` → 归属核对 → `SELECT items FOR UPDATE ORDER BY favorite_id` → **逐行** `favoriteAssertionSchema`（legacy ⇒ `continue` 不产事件；否则建 `v2 retract`）→ 删全部 items → 删 folder。注释 `:334-335` 明写「Deleting an empty folder emits none; a retry cannot fabricate a second business transition」 |
| `favorite_delivery.go:20` | `favoriteReceiptHash = ^[a-f0-9]{64}$` |
| `favorite_delivery.go:26-46` | `FavoriteWireEvent`（9 字段，`payload` 是 `json.RawMessage`）/ `FavoriteTechnicalReceipt`（7 字段） |
| `favorite_delivery.go:48-92` | `FavoriteFactSender` 接口 + `FavoriteDCEventSender`：只发**原字节** + `Idempotency-Key: event_id` + `Authorization: Bearer <token>`；只接受 200/201；响应体上限 `64<<10` |
| `favorite_delivery.go:94-116` | `validFavoriteDelivery`：12 条交叉校验（event_id / producer / event_type ∈ {assert,retract} / schema_version ∈ {1,2} / aggregate_id / aggregate_version / operation_id / payload 是非空对象 / `occurred_at` 可解析 / payload 12 字段 / `favorite_id`+`folder_id` 可解析 / `subject_ref` 合法） |
| `favorite_delivery.go:118-126` | `validFavoriteReceipt`（见 §12.1 `Wire.validateReceipt`） |
| `favorite_delivery.go:131-202` | 🔴 **`DispatchFavoriteFactOnce`** —— `Begin` → `SELECT ... FOR UPDATE SKIP LOCKED WHERE status IN (0,2) AND (aggregate_version=1 OR EXISTS(前驱 aggregate_version=1 AND status=1)) ORDER BY created_at,event_id LIMIT 1`（`:141-147`）⇒ **`err == ErrRecordNotFound` 返回 `(false, nil)`**（队列空，`:148-150`）⇒ strict-decode + `validFavoriteDelivery` 失败 ⇒ **置 `status=3 blocked` 并 commit**，返回 `ErrFavoriteFactBlocked`（`:155-167`）⇒ 发送 + `validFavoriteReceipt` 失败 ⇒ `status=2` + `retry_count+1` 并 commit（`:171-187`）⇒ 成功 ⇒ `status=1` + 5 个回执列 + `delivered_at`（`:188-196`） |
| `favorite_authority.go:19-20` | `favoriteProducer = "rtw.community.favorite"` / `maxExactJSONInteger = 9007199254740991` |
| `favorite_authority.go:60-70` | `strictFavoriteJSON`（REUSE `Wire.strictDecode`） |
| `favorite_authority.go:74-94` | 🔴 `parseAuthorityID`：接受 JSON 字符串或裸数字；必须 `ParseInt` 成功、`id > 0`、**`FormatInt(id,10) == text`（禁止前导零/正号等非规范形式）**；**裸数字还必须 `id <= 2^53-1`**（`maxExactJSONInteger`）⇒ 否则 `false` |
| `favorite_authority.go:105-124` | `favoriteSubjectV1(schema, raw)`：schema 1 ⇒ 必须是 `{authority_id:"rtw.identity", tenant_id:"platform"}` + `subject_id` 是规范正整数；schema 2 ⇒ `{issuer:"rtw.identity"}` + `subject_id` 规范正整数；再把 v2 **归一化成 v1 三元组** |
| `favorite_authority.go:126-177` | `authoritativeFavoriteRow`：17 道校验（`status==1` + `delivered_at`/`technical_received_at` 非空 + `receipt_id != ""` + `offset >= 1` + hash 正则 ⇒ `validFavoriteDelivery` ⇒ payload ⇒ `source_ref` / `event_time` / `available_at` 与 `occurred_at` 三者逐字相等 ⇒ operation 与 `aggregate_version` 匹配 ⇒ **`favoriteJCSHash(payload) == technical_input_hash`** ⇒ 构造 `FavoriteAuthorityFact`） |
| `favorite_authority.go:179-186` | `favoriteJCSHash`（REUSE `Wire.canonicalHash`） |
| `favorite_authority.go:191-204` | `AuthoritativeFavoriteFact`（期望 schema 1）/ `AuthoritativeFavoriteFactV2`（期望 schema 2） |
| `favorite_authority.go:206-282` | 🔴 `authoritativeFavoriteFact`：入口校验（`producer != favoriteProducer` / `eventID == ""` / `len > 128` / `strings.ContainsAny(eventID, "/\\?&#")` ⇒ `ErrFavoriteFactUnavailable`）→ 查 outbox 行 → `authoritativeFavoriteRow` → **schema 必须匹配** → `aggregate_version != 2` 时**独立核对活业务行的 owner/target/revision**（`:224-240`）；`== 2` 时要求活业务行**不存在**（`:253-260`）且**存在同 ID 的 v1 前驱**且**前驱 DC offset 严格更小**且 subject/target/folder/source_ref/revision 全部一致（`:261-280`） |
| `favorite_legacy_preflight.go:14-25` | `FavoriteLegacyPreflightReport{contract, missing_assert, approved_legacy, blocked, orphan_retracts}` + `Clear()`（`contract=="rtw.favorite.legacy-preflight.v1" && missing==approved && blocked==0 && orphan==0`） |
| `favorite_legacy_preflight.go:30-96` | 🔴 **只读** `REPEATABLE READ READ ONLY` 事务；一条 `LEFT JOIN` 拉出「无 v1 断言」的行 + 逐行比对 marker（12 项）→ 分 `Blocked` / `ApprovedLegacy`；再单独统计孤儿 `aggregate_version=2`（`:86-91`） |

### 4 个 SQL 文件（DDL 事实源之二）

| 文件 | 内容 | 是否被 `InitDB` 覆盖 |
|---|---|---|
| `001_favorite_fact_outbox.sql:3-13` | 建表：`event_id varchar(128) PK`、`favorite_id bigint`、`aggregate_version bigint **CHECK IN (1,2)**`、`payload jsonb`、`status smallint DEFAULT 0`、`retry_count int DEFAULT 0`、2 个 `timestamptz`、`UNIQUE (favorite_id, aggregate_version)` | 表 + 列 ✔；🔴 **`CHECK (aggregate_version IN (1,2))` 不在 struct tag 里 ⇒ AutoMigrate 不创建** |
| `001:14-15` | 部分索引 `favorite_fact_outbox_pending ON (status, created_at) WHERE status IN (0,2)` | ❌ AutoMigrate 不创建 |
| `002:3-8` | 加 5 列 `technical_receipt_id` / `technical_input_hash` / `technical_offset` / `technical_received_at` / `delivered_at`（均 `IF NOT EXISTS`） | ✔ AutoMigrate 会补列（struct `:67-71`） |
| `002:10-25` | 🔴 **`reject_favorite_fact_identity_change()` + `favorite_fact_identity_immutable` BEFORE UPDATE 触发器**（禁止改 `event_id`/`favorite_id`/`aggregate_version`/`payload`） | ❌ **AutoMigrate 绝不创建触发器** |
| `003:4-5` | `favorite_item.target_revision varchar(96)`（可空，不回填） | ✔ AutoMigrate 会补列 |
| `004:5-16` | `favorite_legacy_fact_marker` 表：7 列 + 5 个 `CHECK` + `CHECK (target_revision IS NULL OR (target_type='article' AND target_revision<>''))` | ❌ **AutoMigrate 不覆盖该实体**（`db.go:33` 只 AutoMigrate 3 个） |
| `004:18-27` | 🔴 `reject_favorite_legacy_marker_change()` + `favorite_legacy_marker_immutable` BEFORE UPDATE **OR DELETE** 触发器 | ❌ 同上 |

**由上表推出的三条硬结论**：

1. 🔴 **`favorite_legacy_fact_marker` 表只由 004 创建**。未跑 004 的库上，`favoriteAssertionSchema` 走 legacy 分支时（`favorite_fact.go:220-226`）会拿到 PG 的 `relation "favorite_legacy_fact_marker" does not exist` —— 该错误**不是** `gorm.ErrRecordNotFound` ⇒ 走 `:224-225` 的 `return 0, false, err` ⇒ `DeleteFavorite` / `DeleteFolderCascade` 收到普通 DB 错误 ⇒ `delete_favorite_logic.go:84-90` 的 `errors.Is(ErrFavoriteFactMigrationBlocked)` 为假 ⇒ 输出 **`Internal/5008`**，而不是设计中的 `FailedPrecondition/5304`。
   ⚠️ 注意：**只有「该 favorite 没有 v1 断言」的行才会触发**（`favorite_fact.go:214-215` 先查 v1，找到就不进 legacy 分支）。新建收藏有 v1 断言 ⇒ 不受影响。
2. 🔴 **两个不可变触发器（002 / 004）只能由人工执行 SQL 得到**。缺 `favorite_fact_identity_immutable` ⇒ "冻结信封不可变"从硬约束退化为约定，dispatcher 仍会正常发事件 ⇒ **静默降级**。这正是 `like-rpc/bootstrap/LikeSchemaGate.java:12-34` 存在的理由（把「迁移到底跑没跑」变成启动时可见）。
3. ⚠️ `favorite_fact_outbox` 的 `CHECK (aggregate_version IN (1,2))` 与部分索引只有 001 提供。AGENTS.md §0 第 2 条已记录 `like-rpc` 的同型缺陷（列类型 `BIGINT⇒integer`、丢失 CHECK/触发器/部分索引共 5 项，靠新增 V6 补回）⇒ **favorite 的 RAG 迁移必须一次性把这 5 项全建出来，不能只搬 struct tag**。

### `internal/authority/http.go`（favorite 自持的权威读）

- `NewHandler(reader, token)`（`:27-30`）：`reader == nil || len(token) < 32` ⇒ 启动即失败
- **v1**（`:32-62`）：`GET /internal/v1/favorite/facts/{producer}/{event_id}`；`Cache-Control: no-store`（`:33`）；`Authorization` 头**必须恰好 1 个**且 `Bearer ` 前缀 + `subtle.ConstantTimeCompare`（`:34-39`）；**`r.URL.RawQuery != ""` ⇒ 404**（`:40-43`）；`context.WithTimeout(3s)`（`:44`）；`ErrFavoriteFactUnavailable` ⇒ 404；其他 error ⇒ 503；成功 ⇒ `application/json` + `json.NewEncoder.Encode(fact)`
- **v2**（`:63-98`）：`GET /internal/v2/favorite/facts/{producer}/{event_id}`；同款鉴权；🔴 **额外要求 `reader` 实现 `ReaderV2`**，否则 404（`:75-79`）
- 注释 `:25-26` 明写「private-service only … every response is uncached and never includes article content」

### `cmd/fact-dispatch/main.go`（P2）

`:23` `-once` flag；`:35-40` 三个环境变量缺一即 `errors.New("favorite delivery configuration incomplete")`；`:41-54` 连 PG + `PingContext`；`:55-56` `NewFavoriteModel(db)` + `NewFavoriteDCEventSender(endpoint, token, &http.Client{Timeout: 8s})`；`:57-66` `-once` 分支（单次 `WithTimeout(10s)`）；`:67-89` 常驻分支（**ticker 1s**，每轮内层 `for i := 0; i < 16; i++` 逐条 `DispatchFavoriteFactOnce`，**单条失败即 `break` 跳出内层**，`:74-78`）。

### `cmd/fact-authority/main.go`（P3）

`:32-37` 三个环境变量（`FAVORITE_DATABASE_URL` / `FAVORITE_AUTHORITY_LISTEN` / `FAVORITE_AUTHORITY_TOKEN` **≥32 字节**）缺一即退出；`:47-50` `authority.NewHandler(model.NewFavoriteModel(db), token)`（**注意：这里构造的 model 不带 v2 option** ⇒ 默认 v1）；`:56-57` `http.Server{ReadHeaderTimeout: 3s, WriteTimeout: 5s}`；`:60-70` 优雅关闭（5s）。

### `cmd/fact-legacy-preflight/main.go`（P4）

`:20-25` `FAVORITE_DATABASE_URL` 缺失 ⇒ `exit 2`；`:26-31` 开库失败 ⇒ `exit 1`；`:32-34` `WithTimeout(2min)` + `NewFavoriteModel(db).FavoriteLegacyPreflight(ctx)`（**同样不带 v2 option**）；`:40-44` 报告写 stdout，失败 ⇒ `exit 1`；`:45-51` 🔴 **`!report.Clear()` ⇒ 告警 + `exit 2`**；`:52-53` clear ⇒ `exit 0`。
注释 `:16-17` 明写「Read-only migration gate for operators. It neither auto-approves old rows nor changes frozen Outbox data; a blocked report exits 2.」

### 🔴 10 个验收资产的处置

**结论：7 个 `*_ACCEPTANCE.md` + 3 个 `acceptance*.sh` 全部「不迁入 favorite-rpc 运行时路径」，归类为「人工验收资产」。** 逐条依据：

| 资产 | 是否运行时 | 处置 | 依据 |
|---|---|---|---|
| `DELIVERY_ACCEPTANCE.md` | ❌ 纯文档 | **不迁** | 无代码引用（`grep` 全仓零命中）。其描述的行为**全部已在 `model/favorite_delivery.go` + `cmd/fact-dispatch/main.go` 中实现**并已被本文件分析 |
| `FACT_AUTHORITY_ACCEPTANCE.md` | ❌ | **不迁** | 同上；行为在 `model/favorite_authority.go` + `internal/authority/http.go` |
| `FAVORITE_FACT_ACCEPTANCE.md` | ❌ | **不迁** | 同上；行为在 `model/favorite_fact.go:270-378` |
| `FAVORITE_FULL_CHAIN_ACCEPTANCE.md` | ❌ | **不迁** | 同上 |
| `FAVORITE_REVISION_ACCEPTANCE.md` | ❌ | **不迁** | 同上；行为在 `logic/helper.go:126-178` |
| `FAVORITE_SUBJECTREF_V2_ACCEPTANCE.md` | ❌ | **不迁** | 同上；行为在 `favorite.go:31-35` + `favorite_fact.go:164-198` |
| `TWO_USER_COVERAGE_ACCEPTANCE.md` | ❌ | **不迁** | 同上 |
| `acceptance.sh` | ❌ bash | **不迁（不可等价迁移）** | 依赖 `pg_ctl`/`initdb`（PG16 二进制）+ `go test -race` + `go vet`。RAG 侧等价物是 **Testcontainers PostgreSQL + Maven surefire**（先例 `like-rpc/pom.xml:277-286`） |
| `acceptance-dc.sh` | ❌ bash | **不迁（不可等价迁移）** | 额外要求 `SEA_DC_PLATFORM_ROOT` 指向**另一个仓库的独立检出**并 `go build ./cmd/platform`（`:49`）。**DataCenter 不在本项目范围内** ⇒ 该脚本在 RAG 侧没有等价物 |
| `acceptance-full.sh` | ❌ bash | **不迁（不可等价迁移）** | 额外要求 `SEA_BTW_WORKER_ROOT`（`:5`）+ BTW 仓的 `cmd/worker/favorite_acceptance.sh`（`:7`）+ RAG 侧的共享 fixture 测试（`:8`）。**跨三仓**，RAG 侧无等价物 |

⚠️ **但「不迁」不等于「可以丢」**：这 10 个资产是 favorite 域**唯一的回归网来源**。它们覆盖的 13 组行为断言必须在 RAG 侧转成 JUnit + Testcontainers 集成测试，否则迁完之后 favorite 的事实链路将**零回归网**。清单见 §12.5「必配测试」。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `favorite.go` 进程装配 | `FavoriteRpcApplication` | ABSORB | Spring Boot 启动（`LikeRpcApplication.java:58-79` 同款） |
| 🔴 `favorite.go:31-35` v2 启动拒绝门禁 | `bootstrap/FavoriteSubjectRefGate` + `common/logger/FatalError` | ADAPT | Go `os.Exit(2)` 在**开库之前**；Java 无退出码语义，用 `ApplicationRunner` 抛 `FatalError` 让异常冒泡出 `main`（`FatalError` 的 javadoc 明写这就是它的设计场景） |
| `favorite.go:45-47` reflection（仅 dev/test） | 不配置（devh 默认开启 reflection） | ADAPT | 先例 `like-rpc/application.yml:127-128` 已作同样处理并留注释 |
| `favorite.go:40,49` 禁用原生超时 + unary interceptor | `common/spring/RpcServerInterceptorFactory` + **`GrpcServerConfigurer` Bean** | ADAPT | devh **不自动注册**；照抄 `LikeRpcApplication.java:94-97`，否则超时/deadline/span 全部静默消失而 Bean 断言照样绿 |
| `favorite.go:52-54` 启动日志（含 `subject_ref_version`） | `bootstrap/FavoriteSubjectRefGate` 的启动日志 | ADAPT | 与 v2 门禁同处一并输出，避免多一个类 |
| `config/config.go:7-14` Postgres | `application.yml` `spring.datasource.*` | ADAPT | 配置替代 |
| 🔴 `db.go:21` DSN `TimeZone=Asia/Shanghai` | `application.yml` 的连接参数 | ADAPT | 与 users 域 `TIMESTAMP⇒TIMESTAMPTZ`（AGENTS.md §0 users 行）是**同一时区风险面**；favorite 的 `create_time/update_time` 由 Go 侧写、`UnixMilli()` 读出 ⇒ 时区错误会整体平移 |
| `config/config.go:15-16` UserRpc / ArticleRpc | `client/UserRpcClient` + `client/ArticleRpcClient` | ADAPT | `@GrpcClient`（rpc 侧构造器注入先例 `message-rpc/client/UserRpcClient.java:28`） |
| `config/config.go:18` `SubjectRefV2Facts` | `config/FavoriteRpcProperties.subjectRefV2Facts` + `spring.application.active_profiles` | ADAPT | `Mode` 的 dev/test 判定落 profile；⚠️ Go `Mode` 取值域**不在本仓库** ⇒ `UNKNOWN` |
| `svc/service_context.go:28` `InitDB` | Flyway V1..V4 + `bootstrap/FavoriteSchemaGate` | ADAPT | `AutoMigrate`（`db.go:33`）只覆盖 3 个实体（见 §12.3 的三张表）⇒ 必须显式 SQL；`FavoriteSchemaGate` 把「触发器/CHECK 建没建」变成启动可见 |
| `svc/service_context.go:36` `NewFavoriteModel(db, opts...)` | Spring 注入 Mapper + `FavoriteFactOptions`（构造期装配） | ADAPT | Go 的 functional option（`favorite.go:14-21`）是配置态；Java 侧在 `@Configuration` 里按 property 装配 |
| `favoriteservice/favorite_service.go` | `favorite-api/client/FavoriteRpcClient`（**不在本模块**） | ADAPT | 客户端 stub 属于消费侧 |
| `server/favorite_service_server.go:26-59` | `service/impl/FavoriteServiceGrpcImpl` | KEEP | gRPC 契约边界；Go 的 server 层零逻辑，Java 侧也只做「转发 + 唯一错误出口」（`LikeServiceGrpcImpl.java:38-45`） |
| `metrics/metrics.go:14-43` 4 个 CounterVec | `metrics/FavoriteRpcMetrics` | ADAPT | Micrometer Counter 1:1 复刻名与 labels；⚠️ `_total` 追加与 3 处 label 缺陷见 §12.3 |
| `metrics/metrics.go:46-54` `InitMetrics` | **不迁移**（不注销 Go/process collector） | — | Micrometer 自动注册；「注销 JVM collector」是 Go client 的行为，Java 侧无对应物 |
| `metrics/metrics.go:56-64` `ObserveRPC` | `FavoriteRpcMetrics.observeRpc(module, action, started, err)` | ADAPT | 🔴 必须在 Java 侧**显式传入最终错误**（见 §12.4 的 `result` 语义说明） |
| `logic/helper.go:26-40` 标签常量 | `config/FavoriteRpcLabels` 或 `FavoriteRpcMetrics` 内的 `public static final` | MERGE | 8 个文件、30+ 处调用点，散落必然漂移；单点定义是 `like-rpc/config/LikeKafkaTopics.java` 的同款判据 |
| `logic/helper.go:48-54` 日志选项 | `common/logger/{WithUserID,WithArticleID}` | REUSE | 逐字对齐 |
| `logic/helper.go:56-58` `normalizeFolderName` | `service/FavoriteRpcSupport.normalizeFolderName` | MERGE | 7 个 logic 共享 |
| `logic/helper.go:60-62` `normalizeTargetType` | `service/FavoriteRpcSupport.normalizeTargetType` | MERGE | 同上；🔴 小写化是 L2 语义 |
| `logic/helper.go:64-85` `toProtoFolder` / `toProtoItem` | `service/FavoriteRpcSupport.toProto*` | MERGE | 2 个 list logic 共享；`UnixMilli()` 不可写成 `getSeconds()` |
| `logic/helper.go:87-90` `isUniqueViolation` | `service/FavoriteRpcSupport.isUniqueViolation(SQLException)` | ADAPT | Go 认 PG SQLSTATE `23505`；Java 侧读 `SQLException.getSQLState()`，**不能**用 `SQLIntegrityConstraintViolationException`（会把 23503/23514 也吞进来） |
| 🔴 `logic/helper.go:92-124` `ensureUserExists` | `service/FavoriteUserGuard.ensureExists(userId)` | NEW | 跨域身份校验，5 条分支各自独立 gRPC code；被 2 个 logic 调用但**与业务逻辑无关**，独立才能独立测 |
| 🔴 `logic/helper.go:126-178` `resolveArticleSnapshot` + `:180-202` `articleCover` | `service/FavoriteArticleSnapshotResolver.resolve(targetId)` | NEW | 公开修订冻结是 favorite 最核心的业务规则（12 条判定）；`articleCover` 只服务它 ⇒ 同类合并 |
| 7 个 logic 的参数校验 / 归属校验 / 业务码 | `service/FavoriteDomainService`（7 个方法） | MERGE | 与 users 域 `UserDomainService` 形态一致（AGENTS.md §4.1 已锁定的决策）；`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeDomainService.java` 亦同 |
| 7 个 logic 的 `metrics.ObserveOp` | 各 logic 内联 `FavoriteRpcMetrics.observeOp(...)` | KEEP | label 常量已单点定义（上一行），调用点保留在原位 |
| 🔴 `model/favorite.go:33-118` 8 个只读/单写方法 | `mapper/FavoriteFolderMapper` + `mapper/FavoriteItemMapper` | SPLIT | AGENTS.md §4.1 已锁定「删除 DataModel，按实体拆成多个 Mapper」；`InsertFolder` 归 `FavoriteFolderMapper` |
| `model/vars.go:5-7` `ErrorNotFound` | `exception/FavoriteExceptions.NotFoundException` + Mapper 层归一化 | ADAPT | GORM `ErrRecordNotFound` 的归一化哨兵；Java 侧需在每个 `@Select` 的取不到分支显式抛（先例 `MessageExceptions.java:26-28`） |
| `model/data_model.go:5-16` `FavoriteFolder` | `domain/FavoriteFolderEntity` | ADAPT | `@TableName("favorite_folder")` + `@TableId(type=INPUT)`（`Id` 是 GORM 自增代理键，业务不写） |
| `model/data_model.go:18-35` `FavoriteItem` | `domain/FavoriteItemEntity` | ADAPT | 🔴 **`target_revision` 必须可空**（`String` 包装或 `@TableField` + null 处理）；**无 `update_time`** |
| `model/favorite_fact.go:60-76` `FavoriteFactOutbox` | `domain/FavoriteFactOutboxEntity` | ADAPT | 13 列；`payload` 是 **jsonb** ⇒ 挂 `JsonbTypeHandler` + `@TableName(autoResultMap=true)`；🔴 **刻意保持 String 不映射 POJO**（先例 `LikeDomainFactOutboxEntity.java:38-48` 的同款论证：冻结信封要按原始字节做 JCS hash 比对） |
| `model/favorite_fact.go:28-39` `FavoriteLegacyFactMarker` | `domain/FavoriteLegacyFactMarkerEntity` | ADAPT | 可空 `target_revision`；🔴 该表只由 004 建（见 §12.3） |
| 🔴 `model/favorite_fact.go:270-296/300-332/336-378` 三个事务写 | `service/FavoriteFactWriteService`（**独立 `@Service` Bean**） | SPLIT | §3.3：**自调用会让 `@Transactional` 静默失效**。必须从 `FavoriteDomainService` 外部调用；先例 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeMessageIngestService.java` |
| `model/favorite_fact.go:212-265` `favoriteAssertionSchema` | `service/FavoriteAssertionSchemaResolver.resolve(tx, item)` | NEW | 54 行、两个分支（legacy / 冻结事件）、8 项 marker 比对 + 12 项事件比对。**必须独立**：它被 2 个事务路径共用，抽出来才能保证「两处校验不会漂移」（`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeDeliveryValidator.java` 的同款判据） |
| `model/favorite_fact.go:135-198` 两个 outbox 构造器 + `:200-210` 分派 | `service/FavoriteFactEnvelopeFactory.build(item, version, operation, schema)` | NEW | 🔴 **v1 与 v2 的 payload 只有 `subject_ref` 不同**，其余 11 字段逐字相同 ⇒ 必须单点构造，不能在两个事务里各写一遍 |
| `model/favorite_fact.go:44-58` `favoriteLegacySnapshotHash` | `service/FavoriteFactEnvelopeFactory.legacySnapshotHash(item)` | MERGE | 与上面同类（JSON 构造 + JCS hash），共用 `Wire.canonicalHash` |
| `model/favorite_delivery.go:26-46` `FavoriteWireEvent` / `TechnicalReceipt` | `communityfact/Wire.Event` / `Wire.TechnicalReceipt` | REUSE | §12.1 已逐字段核对 |
| `model/favorite_delivery.go:54-92` `FavoriteDCEventSender` | `communityfact/Wire.DCEventSender` | REUSE | §12.1 已逐条核对（含 64KB 上限与 200/201 白名单） |
| `model/favorite_delivery.go:94-116` `validFavoriteDelivery` | `service/FavoriteDeliveryValidator.validDelivery(row, event)` | NEW | 12 条交叉校验，**投递路径与权威读路径共用** ⇒ 独立才不漂移 |
| `model/favorite_delivery.go:118-126` `validFavoriteReceipt` | `communityfact/Wire.validateReceipt` | REUSE | §12.1 已核对 |
| 🔴 `model/favorite_delivery.go:131-202` `DispatchFavoriteFactOnce` | `service/FavoriteFactDispatchService.dispatchOnce(sender)`（**独立 `@Service` Bean**） | NEW | 事务跨 HTTP I/O；先例 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeFactDispatchService.java` |
| `model/favorite_delivery.go:20` hash 正则 | `communityfact/Wire.validHash` | REUSE | §12.1 已核对 |
| `model/favorite_delivery.go:22` `ErrFavoriteFactBlocked` | `Wire.ERR_FROZEN_ENVELOPE_BLOCKED` | REUSE | 按类型识别（`Wire.java:63-73` 记录了 M16 修复的教训：不看文案） |
| `cmd/fact-dispatch/main.go:57-89` dispatcher 循环 | `fact/FavoriteFactDispatchScheduler` + `Dispatcher.run(...)` | REUSE | §12.1 已核对（批次 16 / 1s / 10s / once）；外层调度编排 NEW |
| `cmd/fact-authority/main.go:47-50` handler 装配 | `fact/FavoriteAuthorityEndpointConfig` | UNKNOWN | 🔴 wire 形态待拍板，见 §12.7 #1 |
| `cmd/fact-authority/main.go:56-57` `ReadHeaderTimeout 3s` / `WriteTimeout 5s` | Servlet 容器配置 | UNKNOWN | 依赖「给纯 gRPC 进程加 web connector」的形态决策 |
| `cmd/fact-legacy-preflight/main.go:18-54` | `fact/FavoriteLegacyPreflightRunner` | UNKNOWN | 🔴 **exit code 语义无对应物**，形态待拍板 |
| `model/favorite_legacy_preflight.go:14-96` 报表 | `domain/FavoriteLegacyPreflightReport`（**不建 Mapper**）+ Runner | NEW | 报表只有一条 `LEFT JOIN` SQL；`Clear()` 判定逻辑必须 1:1 |
| `model/favorite_authority.go:60-70` `strictFavoriteJSON` | `communityfact/Wire.strictDecode` | REUSE | §12.1 已核对 |
| `model/favorite_authority.go:179-186` `favoriteJCSHash` | `communityfact/Wire.canonicalHash` | REUSE | §12.1 已核对 |
| `model/favorite_authority.go:74-94` `parseAuthorityID` | `service/FavoriteAuthorityLookupService.parseAuthorityId(JsonNode)` | NEW | 🔴 **不能用 `Wire.parseRTWSubject`** —— 后者只接受 `"rtw.identity/platform/{uid}"` 字符串形态，而 favorite 要处理**裸 JSON 数字**且要拒绝非规范十进制与 >2^53 的裸数字 |
| `model/favorite_authority.go:105-124` `favoriteSubjectV1` | `FavoriteAuthorityLookupService.subjectV1(schema, node)` | NEW | v1 三字段 / v2 两字段的归一化，**必须自持** |
| `model/favorite_authority.go:126-177` `authoritativeFavoriteRow` | `service/FavoriteAuthorityLookupService.authoritativeRow(row)` | NEW | 17 道校验 |
| `model/favorite_authority.go:191-204` 两个公开入口 | `FavoriteAuthorityLookupService`（对应 Go `internal/authority/http.go:17` `Reader` / `:21` `ReaderV2`） | NEW | 供 favorite **自持 servlet**（`fact/FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet` :188）调用 ⇒ 🔴 **不实现** `communityfact/AuthorityHandler.java:48` 的 `AuthorityLookup`（那是 like / comment 的契约） |
| 🔴 `model/favorite_authority.go:27-43` `FavoriteAuthorityFact` / `V2` | `domain/FavoriteAuthorityFact`（**v1 三字段 `subject_ref`**）+ 复用 `Wire.AuthorityFact`（v2） | NEW + REUSE | §12.1「`Wire.AuthorityFact` 是 v2 形状」的直接后果 |
| `internal/authority/http.go:32-98` | `fact/FavoriteAuthorityEndpointConfig` 或 favorite 自持 servlet | UNKNOWN | 同上 |
| 4 个 `.sql` | `resources/db/migration/V1..V4` | ADAPT | ⚠️ **必须一次性建全 5 项非-struct-tag 资产**（`CHECK` ×6、部分索引 ×1、不可变触发器 ×2、legacy 表），否则静默降级（§12.3 三条硬结论） |
| `db.go:43-63` `target_id` 类型修复 | **不迁移**（Java 侧由 Flyway 直接建 `varchar(255)`） | — | Go 侧那段是为「已有 bigint 列的旧库」准备的一次性修复；RAG 侧新库无需 |
| 7 个 `*_ACCEPTANCE.md` | **不迁**（人工验收资产） | — | 见 §12.3 末表；行为断言转 §12.5「必配测试」 |
| 3 个 `acceptance*.sh` | **不迁**（不可等价迁移） | — | 依赖 PG16 二进制 + `go test` + **跨仓 DataCenter / BTW 检出** |
| —— | `service/favorite/favorite-rpc/pom.xml` | **需补依赖** | 至少：`favorite-proto`、`favorite-common`、`user-proto`、`article-proto`、`common`、`spring-boot-starter`、`spring-boot-web-server`（Nacos 反射依赖）、`grpc-server-spring-boot-starter`、`grpc-client-spring-boot-starter`、`mybatis-plus-spring-boot3-starter`、`postgresql`、`flyway-core`+`flyway-database-postgresql`+**`spring-boot-flyway`**、`spring-boot-starter-actuator`、`micrometer-registry-prometheus`、`spring-cloud-starter-alibaba-nacos-discovery`、`opentelemetry-exporter-otlp`、test scope 的 `spring-boot-starter-test` + `testcontainers-postgresql` + `testcontainers-junit-jupiter` + `grpc-inprocess` |

#### 🔴 `ObserveRPC` 的 `result` 语义：Go 缺陷如何在 Java 侧表达（需拍板）

Go 用「命名返回值 `err` + `defer` 读取」实现 `result` 标签，**21 处错误返回忘了赋值 ⇒ 被记成 `success`**（§12.3 怪癖 2）。

| 方案 | 结论 |
|---|---|
| 在 Java 侧**照抄缺陷**（错误路径传 `null`） | ❌ **不采用**。Java 没有命名返回值机制，要"照抄"就得在 21 个分支里手写 `observeRpc(..., null)`，等于把一个 Go 的**编码疏忽**固化成 21 处刻意的错误标注，维护成本极高且必然被后人"修好" |
| 在 Java 侧**统一按真实结果计数** | ✅ **推荐**。即 `FavoriteDomainService` 的每个方法用 try/catch 或 `Supplier` 包装：成功记 `success`、任何异常记 `fail`。⇒ **这是一处有意的指标行为变更，必须在 §3.2 留痕并请用户签字**（不能默默改） |
| 保持 Go 原样 | ⏸ 若用户要求零偏离，则需在每个方法里显式重述 21 处「记 success」的分支 —— **可行但可维护性极差**，需签字 |

⚠️ **本文件不替用户决定**，只把三条路与后果写清楚。同理适用于怪癖 3（两处 `module` label 写错）：推荐按 Go 原样保留（改 label 代价低、可逆），但同样需签字。

---

## 12.5 第五部分：理论 Java 文件树

```
service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/
├── FavoriteRpcApplication.java
├── bootstrap/
│   ├── FavoriteNacosRegistration.java            # @Component("favoriteRpcNacosRegistration")，显式 Bean 名避撞
│   ├── FavoriteSchemaGate.java                   # ★ 启动校验：2 触发器 + 6 CHECK + 1 部分索引 + 4 表存在
│   ├── FavoriteSubjectRefGate.java               # ★ 复刻 favorite.go:31-35 的 exit(2) 门禁 + 启动日志
│   └── FavoriteFactProcessLogging.java           # 4 份 ProcessLogger（rpc / dispatch / authority / preflight）
├── client/
│   ├── UserRpcClient.java                        # @GrpcClient("user-rpc")
│   └── ArticleRpcClient.java                     # @GrpcClient("article-rpc")
├── config/
│   ├── FavoriteRpcProperties.java                # postgres / subjectRefV2Facts / fact.dispatch.* / fact.authority.*
│   └── FavoriteRpcLabels.java                    # module×6 + action×7 + result×2 + db action×4 + db type×2
├── domain/
│   ├── FavoriteFolderEntity.java                 # @TableName("favorite_folder")
│   ├── FavoriteItemEntity.java                   # @TableName("favorite_item")；target_revision 可空
│   ├── FavoriteFactOutboxEntity.java             # @TableName("favorite_fact_outbox")；payload jsonb
│   ├── FavoriteLegacyFactMarkerEntity.java       # @TableName("favorite_legacy_fact_marker")
│   └── FavoriteAuthorityFact.java                # ★ v1 形状（subject_ref 三字段）
├── mapper/
│   ├── FavoriteFolderMapper.java                 # 5 方法（insertFolder / findByUserId / findByFolderId / findByUserIdAndName / updateName）
│   ├── FavoriteItemMapper.java                   # 4 方法（findByFolderId / findByFavoriteId / findByFolderTarget / deleteByFavoriteId / deleteByFolderId）
│   ├── FavoriteFactOutboxMapper.java             # ★ takeOneForDelivery（SKIP LOCKED + 前驱屏障）/ markBlocked / markFailed / markAccepted / countOrphanRetracts
│   ├── FavoriteLegacyFactMarkerMapper.java       # findByFavoriteId（legacy 分支）
│   └── JsonbTypeHandler.java
├── exception/
│   └── FavoriteExceptions.java                   # NotFound / OwnerMismatch / FactMigrationBlocked / FactBlocked / FactUnavailable
├── metrics/
│   └── FavoriteRpcMetrics.java                   # 4 个 Counter + observeRpc/observeOp/observeDbError
├── fact/                                          # ← 新增包（见下方说明）
│   ├── FavoriteFactDispatchScheduler.java         # @PreDestroy → DispatcherHandle.stop()
│   ├── FavoriteAuthorityEndpointConfig.java       # ★ wire 形态待拍板（§12.7 #1）
│   └── FavoriteLegacyPreflightRunner.java         # ★ exit code 语义待拍板
└── service/
    ├── FavoriteDomainService.java                 # 7 个 gRPC 用例
    ├── FavoriteRpcSupport.java                    # normalize×2 + toProto×2 + isUniqueViolation + 日志选项
    ├── FavoriteUserGuard.java                     # ensureExists（5 分支）
    ├── FavoriteArticleSnapshotResolver.java       # resolve + articleCover（12 条判定）
    ├── FavoriteFactWriteService.java              # ★ 独立 Bean：InsertFavorite / DeleteFavoriteByFavoriteId / DeleteFolderCascade
    ├── FavoriteFactEnvelopeFactory.java           # v1/v2 outbox 构造 + legacySnapshotHash
    ├── FavoriteAssertionSchemaResolver.java       # ★ 54 行双分支断言解析
    ├── FavoriteDeliveryValidator.java             # validDelivery（12 条交叉校验）
    ├── FavoriteFactDispatchService.java           # ★ 独立 Bean：dispatchOnce（事务跨 HTTP I/O）
    ├── FavoriteAuthorityLookupService.java        # parseAuthorityId + subjectV1 + authoritativeRow（17 道校验）
    └── impl/
        └── FavoriteServiceGrpcImpl.java           # @GrpcService，7 override，唯一错误出口

service/favorite/favorite-rpc/src/main/resources/
├── application.yml                                # server 无 web / datasource / flyway.table=favorite / grpc / nacos / management
└── db/migration/
    ├── V1__create_favorite_folder.sql             # favorite_folder（结构来自 data_model.go:5-16）
    ├── V2__create_favorite_item.sql               # favorite_item（含 target_revision varchar(96)）
    ├── V3__create_favorite_fact_outbox.sql        # ★ CHECK(aggregate_version IN (1,2)) + UNIQUE + 部分索引
    ├── V4__create_favorite_legacy_fact_marker.sql # ★ 5 CHECK + 复合 CHECK + 不可变触发器
    ├── V5__add_favorite_fact_delivery.sql         # ★ 5 回执列 + favorite_fact_identity_immutable 触发器
    └── V6__align_favorite_schema_with_go.sql       # 幂等补齐：对齐 Go 的 6 CHECK / 1 部分索引 / 2 触发器（先例 like-rpc V6）
```

**共 32 个 Java 文件 + 6 个迁移脚本。**

> ⚠️ **V1..V6 的编号与 Go 的 `001..004` 故意不同**：Go 的 4 个 SQL 只覆盖部分对象，`AutoMigrate`（`db.go:33`）覆盖其余部分，两者交错。Java 侧必须**按最终 schema 重新切分**，且 AGENTS.md §6.3 问题 4 要求 `spring.flyway.table: flyway_schema_history_favorite`（与 message/like/article 的 V1 撞号）。V6 的存在是为了在 V1..V5 落地后**幂等补齐所有 Go 侧非-struct-tag 资产**（先例 `like-rpc/resources/db/migration/V6__align_like_domain_fact_outbox_with_go.sql`，AGENTS.md §0 like 行记载的 5 项补回）。

### 新增包说明（§3.3 规则 8）：`fact/`

`fact/` 是**新增包**，四条依据：

1. **职责无法归入现有包** —— `service/` 承载被 gRPC 调用的业务用例，`service/impl/` 承载 gRPC 实现，`domain/`+`mapper/` 承载持久化。三个入口（dispatcher 循环 / authority Servlet / preflight 报表）**既非 gRPC 方法也非业务用例**，它们有独立生命周期（`@Scheduled` / Servlet 容器 / 一次性 runner）、**独立的启用条件**（三个环境变量，任一缺失即整段不启动）、**独立的错误契约**（preflight 的"阻断"不是异常而是报表内容）。
2. **目标项目实际依据** —— `like-rpc/mq/` 是同类先例（`LikeHotEventOutboxRelay` / `LikeFactDispatchScheduler` / `LikeAuthorityEndpointConfig`），本文件沿用其「新包 + 说明理由」的判据。
3. 🔴 **但本文件建议用 `fact/` 而不是照抄 `mq/`** —— 名字不同有具体依据：**favorite 域零 Kafka**（`rpc/internal/` 下无 `mq/`，`pom` 也不需要 `spring-kafka`），而 `mq/` 这个名字在 like-rpc 里成立是因为 Go 侧确实有 `internal/mq/internal/mqs/likemq.go`。给一个没有消息队列的域建 `mq/` 包会误导读者。
   ⚠️ **代价**：与 `like-rpc/mq/` 口径不一致。⇒ **这是一个需要拍板的口径选择**（`fact/` 更贴切 / `mq/` 与 like 一致），见 §12.7 不通过项 #4。
4. **不因 Go 有同名文件而新增** —— Go 的这三个 `cmd/*` 是**三个 `package main`**，不是 `fact` 目录；本包的存在理由是"三个独立进程合并进单 JVM 后需要新的编排层"，与 like-rpc §12.5 的同款论证。

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `FavoriteRpcApplication` | `@SpringBootApplication` + `@ConfigurationPropertiesScan` + `@MapperScan` + `@Import`（SnowflakeBean/LoggerFactoryBean/RpcServerInterceptorFactory/Observability*/Deadline*/SqlTimeout*/Jackson2*）+ **`GrpcServerConfigurer`** | `favorite.go:26-56` | **五个不写就静默失效/起不来的装配点**，逐条见 `LikeRpcApplication.java:36-56` |
| `bootstrap/FavoriteNacosRegistration` | 显式注册 `favorite-rpc` | `favorite.go:42-48` 的 etcd 注册 | ⚠️ Bean 名必须显式（`NacosRegistration.java:29-33` 的 DRIFT-MSG-004） |
| `bootstrap/FavoriteSchemaGate` | 🔴 校验 **2 个不可变触发器 + `CHECK (aggregate_version IN (1,2))` + 部分索引 `favorite_fact_outbox_pending` + 4 张表** | `db.go:33` 的 `log.Fatalln` + 001/002/004 的人工 SQL | **L3 语义**：缺触发器/CHECK ⇒ 冻结信封可写、非法版本号可入库，且**全部静默**。Go 侧缺这些是运维问题；RAG 侧缺这些是 Flyway 没跑 ⇒ 必须变成启动可见（`LikeSchemaGate.java:17-34` 的同款论证） |
| `bootstrap/FavoriteSubjectRefGate` | 🔴 `subjectRefV2Facts && 非 dev/test profile` ⇒ 抛 `FatalError`（等价 Go `:31-35` 的 `exit(2)`）；同时打 `subject_ref_version=v1\|v2` | `favorite.go:31-35` + `:52-54` + `:58-63` | **L3 硬门禁**：Go 明写「在打开数据库前退出」；Java 侧必须同样在容器 ready 之前拒绝 |
| `bootstrap/FavoriteFactProcessLogging` | 4 份 `ProcessLogger` | 4 个 `cmd/*` + P1 的 `slog` | 合并拓扑的新职责（保住四角色日志身份） |
| `client/UserRpcClient` | `getUser(uid)` | `svc:37` | 外部服务客户端 |
| `client/ArticleRpcClient` | `getArticle(articleId, incrView=false, publicOnly=true)` | `svc:38` | 同上；`public_only` 契约见 `article.proto:58` |
| `config/FavoriteRpcProperties` | `subjectRefV2Facts` + `fact.dispatch.{enabled,dcEndpoint,dcToken,dcTimeout,once}` + `fact.authority.{enabled,token}` | `config.go:18` + 3 个 `cmd/*` 的环境变量 | 配置独立关注点 |
| `config/FavoriteRpcLabels` | 6 个 module + 7 个 action + 2 个 result + 4 个 db action + 2 个 db type | `helper.go:26-40` | **30+ 处调用点的单点定义**；散落必漂移 |
| `domain/FavoriteFolderEntity` | 5 列 + 自增代理键 | `data_model.go:5-16` | ORM 适配 |
| `domain/FavoriteItemEntity` | 8 列；**`target_revision` 可空**；**无 `update_time`** | `data_model.go:18-35` | ORM 适配 |
| `domain/FavoriteFactOutboxEntity` | 13 列；`payload` jsonb + TypeHandler | `favorite_fact.go:60-76` | 🔴 **payload 刻意保持 String**（JCS hash 需原始字节，先例 `LikeDomainFactOutboxEntity.java:38-48`） |
| `domain/FavoriteLegacyFactMarkerEntity` | 7 列 | `favorite_fact.go:28-39` | 独立实体（独立表 + 独立触发器） |
| `domain/FavoriteAuthorityFact` | 🔔 `subject_ref` **三字段** v1 形状 + `predecessor_event_id` | `favorite_authority.go:27-33` | 🔴 **不能用 `Wire.AuthorityFact`**（`Wire.java:147-152` 只有 2 字段） |
| `mapper/FavoriteFolderMapper` | 5 方法；`findByUserId` 带 `ORDER BY create_time DESC` | `favorite.go:33-80` | 注解式原生 SQL |
| `mapper/FavoriteItemMapper` | 4 方法；`findByFolderId` 带 `ORDER BY create_time DESC` | `favorite.go:82-118` | 同上 |
| `mapper/FavoriteFactOutboxMapper` | 🔴 `takeOneForDelivery`（`FOR UPDATE SKIP LOCKED` + `aggregate_version=1 OR EXISTS(前驱 status=1)` + `ORDER BY created_at,event_id` + `LIMIT 1`）/ `markBlocked` / `markFailed`（`retry_count+1`）/ `markAccepted`（6 列）/ `countOrphanRetracts` | `favorite_delivery.go:141-196`、`favorite_fact.go:235-242` | **并发正确性的核心**，独立成 mapper 才便于直接审 SQL（`LikeDomainFactOutboxMapper.java:66-88` 的同款判据） |
| `mapper/FavoriteLegacyFactMarkerMapper` | `findByFavoriteId` | `favorite_fact.go:220-226` | 只服务 legacy 分支 |
| `mapper/JsonbTypeHandler` | jsonb 列 ↔ String | `favorite_fact.go:64` | 先例 `like-rpc/mapper/JsonbTypeHandler.java` |
| `exception/FavoriteExceptions` | NotFound / OwnerMismatch / FactMigrationBlocked / FactBlocked / FactUnavailable | `vars.go:6` + `favorite_fact.go:22-23` + `favorite_delivery.go:22` + `favorite_authority.go:22` | 与 `MessageExceptions.java:16-30` 同款；5 个哨兵对应 5 条互不相同的错误路径 |
| `metrics/FavoriteRpcMetrics` | 4 个 Counter；`observeRpc` / `observeOp` / `observeDbError` | `metrics.go:14-72` | ⚠️ `_total` 追加 + 2 处 `module` label 缺陷按 Go 原样保留（§12.3 怪癖 1/3） |
| `fact/FavoriteFactDispatchScheduler` | 建 `Wire.DCEventSender` + `Dispatcher.run(..., once, registerShutdownHook=false)`；`@PreDestroy` → `stop()` | `fact-dispatch/main.go:22-89` | 合并后的编排器，**不注册任何对外服务** |
| `fact/FavoriteAuthorityEndpointConfig` | Servlet 装配（producer/token/lookup） | `fact-authority/main.go:47-50` + `authority/http.go:27-30` | 🔴 **形态待拍板** |
| `fact/FavoriteLegacyPreflightRunner` | 跑只读报表 + 结构化日志 | `fact-legacy-preflight/main.go:18-54` | 🔴 **exit code 语义待拍板** |
| `service/FavoriteDomainService` | 7 个用例；参数校验 → 依赖 → 归属 → 写 | 7 个 logic | 与 users/article 域 `XxxDomainService` 形态一致（AGENTS.md §4.1 已锁定） |
| `service/FavoriteRpcSupport` | `normalizeFolderName` / `normalizeTargetType` / `toProtoFolder` / `toProtoItem` / `isUniqueViolation` / 日志选项 | `helper.go:48-90` | 跨 7 个 logic 的共享片段 |
| `service/FavoriteUserGuard` | 5 条分支的跨域身份校验 | `helper.go:92-124` | **跨域且独立可测**；`hasStatus()` presence 语义是独立风险点 |
| `service/FavoriteArticleSnapshotResolver` | 公开修订冻结 12 条判定 + `articleCover` 5 键回退 | `helper.go:126-202` | favorite 最核心业务规则，独立才能逐条测 |
| 🔴 `service/FavoriteFactWriteService` | 3 个事务方法 | `favorite_fact.go:270-378` | **§3.3 self-invocation**：`@Transactional` 必须经代理 ⇒ 独立 Bean（先例 `LikeMessageIngestService`） |
| `service/FavoriteFactEnvelopeFactory` | v1/v2 outbox 构造 + 6 条前置断言 + `legacySnapshotHash` | `favorite_fact.go:44-58,135-210` | v1/v2 只差 `subject_ref`，**必须单点构造** |
| `service/FavoriteAssertionSchemaResolver` | 双分支断言解析（54 行） | `favorite_fact.go:212-265` | 被 2 个事务路径共用，独立才不漂移 |
| `service/FavoriteDeliveryValidator` | 12 条交叉校验 | `favorite_delivery.go:94-116` | 投递与权威读共用 |
| 🔴 `service/FavoriteFactDispatchService` | `dispatchOnce`：`SKIP LOCKED` 取件 → 校验 → 发送 → 结算 | `favorite_delivery.go:131-202` | **独立 `@Service` Bean**：事务跨 HTTP I/O |
| `service/FavoriteAuthorityLookupService` | `parseAuthorityId` + `subjectV1` + `authoritativeRow` + 两个公开入口 | `favorite_authority.go:74-282` | 对应 Go `internal/authority/http.go:17,21` 的 `Reader` / `ReaderV2` 契约；是 authority 端点的全部业务。<br>🔴 **不实现** `communityfact/AuthorityHandler.java:48` 的 `AuthorityLookup` —— Go 侧 `AuthorityLookup` 声明在 `service/common/communityfact/authority_http.go:18`（`:23` `NewAuthorityHandler`），**`service/favorite/` 零引用**（like / comment 在用）⇒ 锚点文件 `favorite_authority.go` 里本来就没有这个符号 |
| `service/impl/FavoriteServiceGrpcImpl` | `@GrpcService`，7 override；异常 → `FavoriteGrpcError.grpcError(code,msg)`；兜底 5001 | `server/favorite_service_server.go:26-59` | gRPC 契约边界 + **唯一错误出口**（`LikeServiceGrpcImpl.java:38-45`） |
| `resources/application.yml` | `spring.application.name: favorite-rpc`；`spring.datasource.*`；`spring.flyway.table: flyway_schema_history_favorite`；`grpc.server.port: 8082`；`grpc.client.{user-rpc,article-rpc}.address: discovery://…`；`management.endpoints.*`；`otel.service.name: favorite-rpc`；`observability.rpc-timeout: 2s` | `etc/favorite.yaml` 全部**活跃段** | 配置替代 |

### 必配测试（§3.3）

| 测试 | 触发什么 | 锁死什么 |
|---|---|---|
| `FavoriteRpcSpringContextWiringTest`（`@SpringBootTest`，真容器） | `@MapperScan` / `@Import` 七件套 / **`GrpcServerConfigurer` 真的把 interceptor 挂上了** | AGENTS.md §3.3.3「`getBean(ServerInterceptor.class)` 绿 ≠ 生效」；先例 `LikeRpcSpringContextWiringTest` |
| `FavoriteSubjectRefGateTest` | `subjectRefV2Facts=true` + prod profile | 容器**起不来**（等价 Go `exit(2)`）；dev/test profile 起得来 |
| `FavoriteSchemaParityTest`（真 PG，Testcontainers） | 逐条断言 `information_schema` / `pg_trigger` / `pg_indexes` | 🔴 **2 触发器 + `CHECK (aggregate_version IN (1,2))` + 部分索引 + 4 表**。先例 `LikeDomainFactSchemaParityTest` |
| `FavoriteGoStyleParityTest`（**双侧探针**） | 同一 SQL / 同一输入，Go 侧跑真 `FavoriteModel`，RAG 侧跑真 Mapper | §3.3 强制两条 2：`favorite_id` 十进制字符串编码、UTC 毫秒时间戳、JCS hash |
| `FavoriteFactTransactionTest`（真 PG） | 注入 outbox 写失败 | 业务行**同事务回滚**（`FAVORITE_FACT_ACCEPTANCE.md:7`） |
| `FavoriteFactCascadeTest`（真 PG） | 删文件夹 | 逐条 retract + 空夹不产事件 + 混合 legacy/新行原子提交（`:7`、`:16`） |
| `FavoriteLegacyMarkerTest`（真 PG） | 7 种 legacy 场景 | 无标记阻断(5304) / 标记旧行无事件删除 / 混合级联原子 / 错误 UID 拒绝 / 有建立坏体不得走标记 / 孤儿撤回不得用标记绕过 / `preflight` `exit2→0`（`FAVORITE_SUBJECTREF_V2_ACCEPTANCE.md:16,28`） |
| `FavoriteAuthorityParityTest`（真 PG） | v1/v2 读口 | 未接纳 404 / 接纳后 200 且 hash+receipt 对齐 / 未知或错 producer / 无 token / 篡改 / 跨 owner 全拒；v1 读口**不返回 v2 主体**、v2 读口**不返回 v1 主体**（`FACT_AUTHORITY_ACCEPTANCE.md:73`、`FAVORITE_SUBJECTREF_V2_ACCEPTANCE.md:28`） |
| `FavoriteHighSnowflakeIdTest`（真 PG） | `favorite_id > 2^53` | 十进制字符串编码 + `parseAuthorityId` 拒绝裸超 2^53 数字 + 旧超限数字事件被标 `blocked` 且不改体重投（`FACT_AUTHORITY_ACCEPTANCE.md:7,68`） |
| `FavoriteDeliveryOnceTest`（真 PG） | 并发 2 个 dispatcher | `SKIP LOCKED` 各取各的；**锁住 assert 时 retract 不得跳锁抢 offset**（`DELIVERY_ACCEPTANCE.md:9`） |
| `FavoriteArticleRevisionFreezeTest`（真 PG + 假 article-rpc） | r1 收藏 → 发布 r2 → 同夹重复收藏 | 重复收藏 `AlreadyExists` 且 v1 payload 不变；旧 ID 撤回仍 r1；新夹收藏得 r2（`FAVORITE_REVISION_ACCEPTANCE.md:7,13`） |
| `FavoriteMetricsLabelContractTest` | 4 个指标的 name + labels | 🔴 锁死 §12.3 怪癖 1/3 的现状（`_total` 追加、2 处 `module` 缺陷），避免后人"顺手修好" |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `favorite.go` | `FavoriteRpcApplication` | ABSORB | 进程装配由框架承担 |
| 🔴 `favorite.go:31-35` v2 启动拒绝 | `bootstrap/FavoriteSubjectRefGate` + `FatalError` | ADAPT | `os.Exit(2)` → 抛异常冒泡出 main |
| `favorite.go:45-47` reflection（dev/test） | 不配置 | ADAPT | devh 默认开启 |
| `favorite.go:40,49` 超时 + unary interceptor | `RpcServerInterceptorFactory` + `GrpcServerConfigurer` | ADAPT | **不写 `GrpcServerConfigurer` 就静默失效** |
| `favorite.go:52-54,58-63` 启动日志 | `FavoriteSubjectRefGate` | MERGE | 与 v2 门禁同处一并输出 |
| `etc/favorite.yaml:2` ListenOn 8082 | `application.yml` `grpc.server.port` | ADAPT | 配置替代 |
| `etc/favorite.yaml:9-15` Postgres | `application.yml` `spring.datasource.*` | ADAPT | 同上 |
| 🔴 `db.go:21` `TimeZone=Asia/Shanghai` | 连接参数 | ADAPT | 与 users 域 TIMESTAMPTZ 是同一时区风险面 |
| `etc/favorite.yaml:23-30` DevServer 16062 | `management.endpoints.*` | ADAPT | Go `Config` 无对应字段（`config.go:5-19`）⇒ **UNKNOWN** |
| `etc/favorite.yaml:37-47` UserRpc / ArticleRpc | `client/{UserRpcClient,ArticleRpcClient}` | ADAPT | `zrpc.MustNewClient` → `@GrpcClient` |
| `config/config.go:18` `SubjectRefV2Facts` | `config/FavoriteRpcProperties` | ADAPT | 配置绑定 |
| `config/config.go` 的 `Mode` | `spring.profiles.active` | UNKNOWN | Go `Mode` 取值域不在本仓库 |
| `svc/service_context.go`（整体） | Spring Bean 图 | ABSORB | 依赖持有由容器承担 |
| `svc/service_context.go:29-32` v2 option | `config/FavoriteRpcProperties` + 构造期装配 | ADAPT | functional option → 配置态 |
| `favoriteservice/favorite_service.go` | `favorite-api/client/FavoriteRpcClient` | ADAPT | 客户端 stub 属消费侧，**不在本模块** |
| `server/favorite_service_server.go:26-59` | `service/impl/FavoriteServiceGrpcImpl` | KEEP | gRPC 契约边界 |
| `metrics/metrics.go:14-43` 4 CounterVec | `metrics/FavoriteRpcMetrics` | ADAPT | Micrometer 1:1 复刻名与 labels |
| `metrics/metrics.go:46-54` `InitMetrics` | **不迁移** | — | 注销 Go/process collector 是 Go client 行为 |
| `metrics/metrics.go:56-72` 3 个 Observe | `FavoriteRpcMetrics` 3 方法 | ADAPT | 🔴 `result` 语义待拍板（§12.4 末节） |
| `logic/helper.go:26-40` 标签常量 | `config/FavoriteRpcLabels` | MERGE | 30+ 调用点单点定义 |
| `logic/helper.go:48-54` 日志选项 | `common/logger/{WithUserID,WithArticleID}` | REUSE | 逐字对齐 |
| `logic/helper.go:56-62` normalize×2 | `service/FavoriteRpcSupport` | MERGE | 7 个 logic 共享 |
| `logic/helper.go:64-85` toProto×2 | `service/FavoriteRpcSupport` | MERGE | 2 个 list logic 共享 |
| `logic/helper.go:87-90` `isUniqueViolation` | `FavoriteRpcSupport` | ADAPT | PG SQLSTATE `23505` → `getSQLState()` |
| `logic/helper.go:92-124` `ensureUserExists` | `service/FavoriteUserGuard` | NEW | 5 分支独立 gRPC code；跨域 |
| `logic/helper.go:126-202` `resolveArticleSnapshot` + `articleCover` | `service/FavoriteArticleSnapshotResolver` | NEW | 12 条判定；favorite 最核心业务规则 |
| `logic/create_favorite_folder_logic.go` | `FavoriteDomainService.createFavoriteFolder` | MERGE | 7 logic → 1 domain service（users 域先例） |
| `logic/create_favorite_logic.go` | `FavoriteDomainService.createFavorite` | MERGE | 同上 |
| `logic/update_favorite_folder_name_logic.go` | `FavoriteDomainService.updateFavoriteFolderName` | MERGE | 同上 |
| `logic/delete_favorite_folder_logic.go` | `FavoriteDomainService.deleteFavoriteFolder` | MERGE | 同上 |
| `logic/delete_favorite_logic.go` | `FavoriteDomainService.deleteFavorite` | MERGE | 同上 |
| `logic/get_favorite_folders_logic.go` | `FavoriteDomainService.getFavoriteFolders` | MERGE | 同上 |
| `logic/get_favorites_by_folder_logic.go` | `FavoriteDomainService.getFavoritesByFolder` | MERGE | 同上 |
| 7 logic 的业务码 / gRPC code 选择 | 各 domain service 方法内联 | KEEP | L1 冻结（含 `FailedPrecondition/5304`、`Unavailable` 的非标准用法） |
| `model/vars.go:5-7` `ErrorNotFound` | `exception/FavoriteExceptions.NotFound` | ADAPT | GORM 哨兵 → 显式异常 |
| `model/data_model.go:5-16` `FavoriteFolder` | `domain/FavoriteFolderEntity` + `mapper/FavoriteFolderMapper` | SPLIT | AGENTS.md §4.1 锁定的「按实体拆 Mapper」 |
| `model/data_model.go:18-35` `FavoriteItem` | `domain/FavoriteItemEntity` + `mapper/FavoriteItemMapper` | SPLIT | 同上 |
| `model/favorite.go:33-118` 8 方法 | 上述 2 个 Mapper | SPLIT | 一个 Go struct → Entity + Mapper 两个 Java 组件 |
| `model/db.go:20-41` `InitDB` / `AutoMigrate` | Flyway V1..V5 + `FavoriteSchemaGate` | ADAPT | AutoMigrate 只覆盖 3 实体 ⇒ 必须显式 SQL |
| `model/db.go:43-63` `target_id` 类型修复 | **不迁移** | — | 一次性旧库修复；新库由 Flyway 直接建 `varchar(255)` |
| `model/favorite_fact.go:15-23` 状态常量 + 2 哨兵 | `Wire.DeliveryStatus` + `FavoriteExceptions` | REUSE + NEW | 状态值 REUSE；哨兵 NEW |
| `model/favorite_fact.go:28-39` `FavoriteLegacyFactMarker` | `domain/FavoriteLegacyFactMarkerEntity` + `mapper/…Mapper` | SPLIT | ORM 适配 |
| `model/favorite_fact.go:44-58` `legacySnapshotHash` | `FavoriteFactEnvelopeFactory` | MERGE | 与 outbox 构造同类 |
| `model/favorite_fact.go:60-76` `FavoriteFactOutbox` | `domain/FavoriteFactOutboxEntity` + `mapper/…Mapper` + `JsonbTypeHandler` | SPLIT | ORM + 持久化分离 |
| `model/favorite_fact.go:78-89` SubjectRef / V2 | `FavoriteAuthorityFact`（v1）+ `Wire.SubjectRef`（v2） | NEW + REUSE | v1 必须自持 |
| `model/favorite_fact.go:91-133` payload v1/v2 + event | `FavoriteFactEnvelopeFactory` | NEW | JSON 构造单点 |
| `model/favorite_fact.go:135-210` 两个 outbox 构造器 + 分派 | `FavoriteFactEnvelopeFactory.build` | MERGE | 三函数合一 |
| 🔴 `model/favorite_fact.go:212-265` `favoriteAssertionSchema` | `service/FavoriteAssertionSchemaResolver` | NEW | 双分支 54 行，被 2 路径共用 |
| 🔴 `model/favorite_fact.go:270-296` `InsertFavorite` | `service/FavoriteFactWriteService.insertFavorite` | SPLIT | **§3.3 self-invocation** → 独立 Bean |
| 🔴 `model/favorite_fact.go:300-332` `DeleteFavoriteByFavoriteId` | `FavoriteFactWriteService.deleteFavoriteByFavoriteId` | SPLIT | 同上 |
| 🔴 `model/favorite_fact.go:336-378` `DeleteFolderCascade` | `FavoriteFactWriteService.deleteFolderCascade` | SPLIT | 同上 |
| `model/favorite_delivery.go:20` hash 正则 | `Wire.validHash` | REUSE | 逐字一致 |
| `model/favorite_delivery.go:22` `ErrFavoriteFactBlocked` | `Wire.ERR_FROZEN_ENVELOPE_BLOCKED` | REUSE | 按类型识别 |
| `model/favorite_delivery.go:26-46` 2 个 wire struct | `Wire.Event` / `Wire.TechnicalReceipt` | REUSE | §12.1 已逐字段核对 |
| `model/favorite_delivery.go:48-92` Sender + DC sender | `Wire.DCEventSender` | REUSE | §12.1 已逐条核对 |
| `model/favorite_delivery.go:94-116` `validFavoriteDelivery` | `service/FavoriteDeliveryValidator` | NEW | 12 条交叉校验，投递与权威读共用 |
| `model/favorite_delivery.go:118-126` `validFavoriteReceipt` | `Wire.validateReceipt` | REUSE | §12.1 已核对 |
| 🔴 `model/favorite_delivery.go:131-202` `DispatchFavoriteFactOnce` | `service/FavoriteFactDispatchService` | NEW | 事务跨 HTTP I/O → 独立 Bean |
| `model/favorite_delivery.go:141-147` 取件 SQL | `mapper/FavoriteFactOutboxMapper.takeOneForDelivery` | SPLIT | SQL 独立可审 |
| `model/favorite_delivery.go:156-194` 3 次状态更新 | `mapper/FavoriteFactOutboxMapper.{markBlocked,markFailed,markAccepted}` | SPLIT | 状态机独立 |
| `model/favorite_authority.go:19-22` 常量 + 哨兵 | `config/FavoriteRpcLabels` / `FavoriteExceptions` | MERGE | — |
| `model/favorite_authority.go:27-43` 2 个 authority fact 类型 | `domain/FavoriteAuthorityFact`（v1）+ `Wire.AuthorityFact`（v2） | NEW + REUSE | 🔴 v1 三字段 |
| `model/favorite_authority.go:45-58` `authorityPayload` | `FavoriteAuthorityLookupService` 内的私有 POJO | NEW | 仅内部校验用 |
| `model/favorite_authority.go:60-70` `strictFavoriteJSON` | `Wire.strictDecode` | REUSE | 逐条一致 |
| `model/favorite_authority.go:74-94` `parseAuthorityID` | `FavoriteAuthorityLookupService.parseAuthorityId` | NEW | 🔴 裸数字 + 非规范十进制 + 2^53 上限 |
| `model/favorite_authority.go:96-101` `sameFavoriteRevision` | `FavoriteAuthorityLookupService` | MERGE | 与同类 |
| `model/favorite_authority.go:105-124` `favoriteSubjectV1` | `FavoriteAuthorityLookupService.subjectV1` | MERGE | 同类 |
| `model/favorite_authority.go:126-177` `authoritativeFavoriteRow` | `FavoriteAuthorityLookupService.authoritativeRow` | NEW | 17 道校验 |
| `model/favorite_authority.go:179-186` `favoriteJCSHash` | `Wire.canonicalHash` | REUSE | 逐条一致 |
| `model/favorite_authority.go:191-282` 3 个入口 | `FavoriteAuthorityLookupService` | MERGE | 2 公开 + 1 私有 |
| `internal/authority/http.go:27-98` | `fact/FavoriteAuthorityEndpointConfig` 或专用 servlet | **UNKNOWN** | 🔴 wire 形态待拍板 |
| `cmd/fact-dispatch/main.go:34-89` | `fact/FavoriteFactDispatchScheduler` + `Dispatcher.run` | REUSE + NEW | 循环 REUSE、编排 NEW |
| `cmd/fact-authority/main.go:31-70` | `fact/FavoriteAuthorityEndpointConfig` | UNKNOWN | 监听地址形态待定 |
| `cmd/fact-legacy-preflight/main.go:18-54` | `fact/FavoriteLegacyPreflightRunner` | UNKNOWN | exit code 语义待定 |
| `model/favorite_legacy_preflight.go:14-25` 报表 + `Clear()` | `domain/FavoriteLegacyPreflightReport` | NEW | 判定逻辑 1:1 |
| `model/favorite_legacy_preflight.go:30-96` 只读查询 | `mapper`（一条 `LEFT JOIN`）+ Runner | SPLIT | SQL 与编排分离 |
| `001_favorite_fact_outbox.sql` | `V3__create_favorite_fact_outbox.sql` | ADAPT | 🔴 **CHECK + 部分索引必须建出** |
| `002_favorite_fact_delivery.sql` | `V5__add_favorite_fact_delivery.sql` | ADAPT | 🔴 **不可变触发器只能靠 SQL** |
| `003_favorite_target_revision.sql` | `V2__create_favorite_item.sql`（含列） | ADAPT | 与建表合并 |
| `004_favorite_legacy_fact_marker.sql` | `V4__create_favorite_legacy_fact_marker.sql` | ADAPT | 🔴 5 CHECK + 复合 CHECK + 不可变触发器 |
| —— | `V6__align_favorite_schema_with_go.sql` | **NEW** | 幂等补齐 Go 侧全部非-struct-tag 资产（先例 `like-rpc V6`） |
| 7 个 `*_ACCEPTANCE.md` | **不迁**（人工验收资产） | — | 见 §12.3 末表；行为断言转 §12.5「必配测试」 |
| 3 个 `acceptance*.sh` | **不迁**（不可等价迁移） | — | 依赖 PG16 二进制 + `go test` + 跨仓 DataCenter / BTW |
| —— | `service/favorite/favorite-rpc/pom.xml` | **需补依赖** | 现状零依赖；完整清单见 §12.4 末行 |
| —— | `application.yml` 的 `spring.flyway.table: flyway_schema_history_favorite` | **必配** | AGENTS.md §6.3 问题 4：与 message/like/article 的 V1–V4 撞号 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 7 个 gRPC 方法（`favorite.proto:100-106` ↔ `server:26-59`）；全部 proto 字段；`/internal/v1|v2/favorite/facts/{producer}/{event_id}` 的响应形状；`Clear()` 报表的 5 个字段与 `contract` 字符串 |
| L2 业务语义有承载位置 | ✅ 5 分支 `ensureUserExists`；12 条 `resolveArticleSnapshot` 判定（含 `publication_gap` 两条分支、`2^53` 上限、`normalizeTargetType` 小写化）；文件夹归属 3 处；「同名不改库直接成功」；`uk_folder_target` 重查；`FailedPrecondition/5304`；`Unavailable` 的非标准用法 |
| L3 技术语义有承载方案 | ✅ 3 个事务写（`FOR UPDATE` 锁顺序、`ORDER BY favorite_id`、同事务提交/回滚、legacy 只删行不造事件）；`SKIP LOCKED` + 前驱屏障；「事务跨 HTTP I/O」的独立 Bean；payload 保持原始字节；只读 RR 事务；v2 启动门禁 |
| 业务分支与异常路径已纳入 | ✅ 7 logic × 全部失败分支逐条列举；`DispatchFavoriteFactOnce` 的 3 条出口（队列空 / blocked / failed / 成功）；`authoritativeFavoriteFact` 的 4 条出口 |
| 框架吸收有机制依据 | ✅ 进程装配、依赖持有、配置绑定、事务代理（**但只对独立 Bean 生效**） |
| 复用组件已明确 | ✅ `Wire`（7 项）、`Dispatcher`、`ProcessLogger`、`RpcServerInterceptorFactory`、`SnowflakeBean`、`Logger`/`WithUserID`/`WithArticleID`、`FatalError`、`Deadline*`、`JsonbTypeHandler`、`user-proto`、`article-proto`、`MessageGrpcError` 的 `grpcError(Status.Code,int)` |
| 新增文件有职责依据 | ✅ `FavoriteSubjectRefGate`（启动硬门禁）、`FavoriteSchemaGate`（L3 资产可见性）、`FavoriteUserGuard`（跨域身份）、`FavoriteArticleSnapshotResolver`（核心业务规则）、`FavoriteFactWriteService`（§3.3 代理边界）、`FavoriteFactEnvelopeFactory`（v1/v2 单点构造）、`FavoriteAssertionSchemaResolver`（双分支共用）、`FavoriteDeliveryValidator`（两路径共用）、`FavoriteFactDispatchService`（事务跨 I/O）、`FavoriteAuthorityLookupService`、3 个 `fact/*` 编排器 —— **均非"Go 有同名文件"** |
| 合并有组织依据 | ✅ `helper.go` 202 行按 6 类职责拆成 5 个 Java 组件（每类都有独立的复用面或变化原因）；3 个 `cmd/*` 合并进单 JVM（4 条代码级依据已列） |
| 拆分可解释 | ✅ `model/` 的 8 个 Go 文件 → 4 Entity + 4 Mapper（AGENTS.md §4.1 已锁定）；Go 的 `favorite.go` model → Mapper + Support |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Model.java` / `Repository.java` / `ServiceContext.java` / `Logic.java` / `Server.java`；`ServiceContext` 由 Bean 图吸收；`InitMetrics` 的注销 collector 行为不迁 |
| 未把未实现能力说成现有机制 | ✅ `favorite-rpc` 空壳 + 空依赖已在 §12.1 声明；**`AuthorityHandlerFactory` 的 wire 形态冲突**如实写出；⚠️ **`ServletRegistrationBean` 全仓零使用** 与 **「RAG 侧从未真正装配过任何一个 authority 端点」** 两条**当时为真、2026-10-26 起已过期**（favorite 7 处 + comment 2 处；favorite 已落地自持 path 参数端点）⇒ 已就地订正；**`LikeAuthorityEndpointConfig` 仍是骨架**仍为真 |
| 不为"完整"建空组件 | ✅ `metrics/InitMetrics` 不建；`favorite_rpc` 无 Kafka ⇒ 不引 Kafka 依赖 |

### 不通过项

| # | 问题 | 详情 | 状态 |
|---|---|---|---|
| **1** | 🔴 **authority 端点的 wire 形态三方冲突，必须拍板** | Go favorite 自持 path 参数版 `/internal/v1\|v2/favorite/facts/{producer}/{event_id}`（`internal/authority/http.go:32,63`，且 v2 要求 `reader` 实现 `ReaderV2`，`:75-79`）；RAG 侧的通用实现是 `AuthorityHandlerFactory` 的 `/internal/v1/community/facts?producer=&event_id=`（`AuthorityHandlerFactory.java:30`）。⚠️ 原写「实测全仓 `ServletRegistrationBean` 零使用（排除 `service/common`）」「**RAG 侧从未真正装配过任何一个 authority 端点**」—— **2026-10-26 复核已过期**（favorite 7 处 + comment 2 处）；`like-rpc/mq/LikeAuthorityEndpointConfig.java` 仍是 4 行骨架，注释明写「监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector（wire 面变更，待拍板）」⇒ **这一句仍为真** | ✅ **已按选项 ② 落地**（2026-10-26 复核）：favorite 自持 path 参数 servlet（`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/fact/FavoriteAuthorityEndpointConfig.java`，`@ConditionalOnProperty` 默认关 ⇒ 默认不新增 HTTP 端口）⇒ **wire 形态冲突已消**。<br>🔴 **剩下的只有「要不要给纯 gRPC 进程开 web connector」这一项部署形态变更**（台账 §5-U1，未拍板）—— 它决定的是**端点开不开**，不是**形态对不对** |
| **2** | 🔴 **`favorite-rpc/pom.xml` 零依赖** | 无 `favorite-proto`、`favorite-common`、`user-proto`、`article-proto`、`common`、`grpc-server`、`mybatis-plus`、`postgresql`、**`spring-boot-flyway`**、`nacos`、testcontainers。⚠️ 缺 `spring-boot-flyway` 是**静默失效**（`like-rpc/pom.xml:217-227`：启动日志里连一行 Flyway 都没有 ⇒ 4 张表永不创建 ⇒ 第一次真执行 SQL 才炸） | ⏸ §12.4 末行已列完整清单 |
| **3** | 🔴 **Go 侧 DDL 有两处事实源，RAG 迁移极易漏** | `AutoMigrate`（`db.go:33`）只覆盖 3 个实体；`CHECK (aggregate_version IN (1,2))`（`001:6`）、部分索引（`001:14-15`）、**2 个不可变触发器**（`002:22-25`、`004:24-27`）、`favorite_legacy_fact_marker` 表 + 5 个 `CHECK`（`004:5-16`）**全部只能靠手写 SQL**。⇒ 漏掉的后果分两类：① 触发器/CHECK 缺失 ⇒ **静默降级**（冻结信封可写）；② legacy 表缺失 ⇒ `DeleteFavorite` 在 legacy 行上返回 `5008` 而非设计的 `5304`（推理链见 §12.3 硬结论 1） | ⏸ 已在 §12.5 用 `FavoriteSchemaGate` + `V6` + `FavoriteSchemaParityTest` 三重覆盖 |
| **4** | ⚠️ **`fact/` vs `mq/` 的包名口径** | `like-rpc` 用 `mq/`（其 Go 侧确有 `internal/mq/internal/mqs/likemq.go`）；**favorite 零 Kafka**，建 `mq/` 会误导。本文件建议 `fact/`，代价是与 like 口径不一致 | **UNKNOWN**，需拍板 |
| **5** | ⚠️ **`preflight` 的 exit code 语义无对应物** | Go：`exit 2` = 未裁决 / `exit 1` = 读取失败 / `exit 0` = clear（`fact-legacy-preflight/main.go:24,30,38,50`），CI 可直接判。Spring 进程内**没有退出码** | **UNKNOWN**：① 保留独立 CLI 形态（`ApplicationRunner` + 显式退出，⚠️ 与 `FatalError` 的设计意图冲突）；② 改为结构化报表 + 人工判读（**改变了发布门禁的性质**）；③ 做成独立的 `favorite-preflight` 工具模块 |
| **6** | ⚠️ **`ObserveRPC` 的 `result` 标签在 Go 侧有 21 处系统性错误** | §12.3 怪癖 2。Java 侧要么照抄（21 处手写标注，可维护性极差）、要么按真实结果计数（**一处有意变更，需签字**） | **UNKNOWN**，需拍板（§12.4 末节给了两条路与后果） |
| **7** | ⚠️ **两处 `module` label 写错** | `create_favorite_logic.go:89` 与 `get_favorites_by_folder_logic.go:68` 在 item 流程里用了 `folderModule` | ⏸ 本文件按 Go 原样保留（label 改动代价低、可逆），但**需签字**；`FavoriteMetricsLabelContractTest` 会把现状锁死 |
| **8** | ⚠️ **`favorite_request_seconds_counter` 的指标名会漂移** | Go 用 `CounterVec` 且 client_golang **不追加** `_total`；Micrometer 的 `Counter` **会追加** ⇒ 输出 `favorite_request_seconds_counter_total` | ⏸ 属指标面差异，非 API 契约；建议保留并登记 |
| **9** | ⚠️ **10 个验收资产不可迁移 ⇒ 回归网必须重建** | 3 个 `.sh` 依赖 PG16 二进制 + `go test -race` + **跨仓 DataCenter（`acceptance-dc.sh:5,49`）与 BTW（`acceptance-full.sh:5,7`）检出** ⇒ RAG 侧无等价物。7 个 `.md` 记录的 13 组行为断言若不转成 JUnit，迁完之后 favorite 的事实链路**零回归网** | ⏸ §12.5「必配测试」已列 12 个测试类承接 |
| **10** | ⚠️ **favorite-rpc 是纯 gRPC 进程，合并 P3 需要新增 web 端口** | Go 的 `cmd/fact-authority` 有独立监听地址（`fact-authority/main.go:33`）；favorite-rpc 本身 `ListenOn: 0.0.0.0:8082` 只有一个 gRPC 端口（`etc/favorite.yaml:2`） | **UNKNOWN**，与 #1 同源；且与 like-rpc 完全同型（`like/rpc.md` §12.1 已把同一问题记为 UNKNOWN） |

### UNKNOWN 清单

| # | 项 | 说明 | 影响面 |
|---|---|---|---|
| U1 | authority 端点的 wire 形态 | Go path 参数 vs RAG `AuthorityHandlerFactory` query 参数；RAG 侧零先例（见 #1） | 决定 BTW/Binder 的调用方式能否复用；**是 wire 面变更** |
| U2 | `published_revision_id` / `publication_gap` 两个 `ext_info` key 的**写入方** | favorite 只**读**（`helper.go:158,163,167`）；写入方是 article-rpc 的公开投影。**本文件未取证 RAG 侧 article-rpc 是否已写这两个 key** ⇒ `UNKNOWN` | 🔴 若 article-rpc 未写 ⇒ RAG 的 `CreateFavorite("article")` 会**永远**返回 `Unavailable/5001`，favorite 的文章收藏功能整体不可用 |
| U3 | Go `zrpc.RpcServerConf.Mode` 的取值域 | 决定 `SubjectRefV2Facts` 门禁的 dev/test 判定；`service.Mode` 在 `github.com/zeromicro/go-zero`（**不在本仓库**） | 决定 `FavoriteSubjectRefGate` 的 profile 映射规则 |
| U4 | `POSTGRES_PASSWORD` 的实际值 | `etc/favorite.yaml:13` 是 `${…}` 占位 | 配置面 |
| U5 | Go `dev.Dialect` 的实际列类型推断结果 | `favorite_delivery.go:98` 用 `fmt.Sprint(row.FavoriteID)` 拼 `aggregate_id`，而 payload 里存的是 `strconv.FormatInt` 的字符串。两者**当前恒等**，但这是 Go 的 GORM 行为；Java 侧必须显式用同一个 `strconv.FormatInt` 语义构造 `aggregate_id`（**不能**依赖驱动的默认渲染） | 🔴 若 Java 侧让 `aggregate_id` 走 `Long.toString()` 而 payload 走别的路径，两者会不一致 ⇒ `validFavoriteDelivery` 必失败 |
| U6 | `ext_info` 是 `jsonb` 还是 `text`，以及 favorite 侧读取路径 | favorite 只读 article-rpc 的**响应** map（`helper.go:157`），不直接碰 DB ⇒ 本文件未取证 | 低 |
| U7 | RAG 侧 article-rpc 的 `GetArticle(public_only=true)` 是否**已实现**公开投影（只返回 PUBLISHED 且填 `ext_info`） | `article.proto:58` 有 `public_only` 字段，favorite 依赖它的**行为**（不是字段）。**本文件未打开 `service/article/article-rpc` 的实现** ⇒ `UNKNOWN` | 🔴 同 U2 |
| U8 | `like-rpc/mq/LikeAuthorityEndpointConfig` 的骨架状态是否会先于 favorite 落地 | 若 like 先落地并定了形态，favorite 应对齐 | 影响 U1 的决策时序 |

---

## 附：本文件与其它文档的关系

| 文档 | 关系 |
|---|---|
| `FileTree/favorite/api.md` | 上游调用方。api 的 7 个端点 1:1 映射到本文件的 7 个 gRPC 方法 |
| `FileTree/favorite/common.md` | 错误码与 gRPC 转换来源。本文件 rpc 侧实测使用 **17 个码**（`Success` 零使用；`ErrorUserExists` / `ErrorPasswordInvalid` / `ErrorTokenInvalid` / `ErrorTokenExpired` / `ErrorCodeServerBusy` / `ErrorAlreadyExist` / `ErrorUnauthorized` 仅经 `BizCodeFromError` 的 switch 间接可达） |
| `FileTree/like/rpc.md` | **最贴近的先例**：同样有 4 个 Go main → 单 JVM 的合并推导、同样有 `fact-dispatch` / `fact-authority`。三处差异：① like 有 Kafka，favorite 没有 ⇒ 包名建议不同（#4）；② like 的 authority 端点至今未落地，favorite 是第一个要面对的；③ like 的 dispatch 走 `internal/mq` 子进程，favorite 的三个 `cmd/*` 都不注册 gRPC 方法 |
| `docs/DRIFT/like/rpc.md` | like-rpc 的 DDL 5 项补回（`V6__align_…_with_go.sql`）是本文件 `V6__align_favorite_schema_with_go.sql` 的直接先例 |
| `docs/Intentional_Deviations/` | 若采纳 #1 的方案①、或 #6 的方案②，**必须**在此登记新条目 |