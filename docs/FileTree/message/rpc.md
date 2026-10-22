# message-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/message/rpc`
> 目标根包：`com.rag.message.message.rpc`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

`message-rpc` 在 RAG 中**已有 19 个 Java 实现文件**（覆盖 9 个 RPC + 3 张表 + 2 个下游 stub + 配置），实际包结构如下：

| 包 | 实际文件 | 职责 |
|---|---|---|
| （根） | `MessageRpcApplication` | `@SpringBootApplication(scanBasePackages=..., "com.rag.common")` 入口 |
| `bootstrap/` | `NacosRegistration` | `ApplicationReadyEvent` 调 Nacos `registerInstance`；`@PreDestroy` 反注册 |
| `client/` | `AdminRpcClient`、`UserRpcClient` | 两个 `@GrpcClient` stub 的 facade：`loadProfile(role, id)` 和 `collectBroadcastRecipients(pageSize)` |
| `config/` | `MessageProperties` | `@ConfigurationProperties(prefix="message")`：`list{defaultLimit=20, maxLimit=100}` + `broadcast{pageSize=200}` |
| `domain/` | `ConversationEntity`、`ConversationMessageEntity`、`NotificationEntity` | 3 张表的 MyBatis-Plus `@TableName` 实体 |
| `exception/` | `MessageExceptions` | 聚合内部类：`DbSelectException` / `DbUpdateException` / `TransactionRollbackException` / `DatabaseException` |
| `mapper/` | `ConversationMapper`、`ConversationMessageMapper`、`NotificationMapper` | 三个 `@Mapper` 接口，每个 `extends BaseMapper<Entity>` + 业务默认方法 |
| `metrics/` | `MessageMetrics` | `@Component`，注入 `MeterRegistry`；5 个 Counter/Timer（含 type 标签维度） |
| `service/` | `MessageDomainService`、`MessageRpcSupport` | 9 个用例聚合 + helper 工具（含 CanonicalPair/PeerResult record） |
| `service/impl/` | `MessageRpcServiceImpl` | `@GrpcService extends MessageServiceGrpc.MessageServiceImplBase` 9 个 thin override |

`message-common`（独立模块）已有 3 个文件：`MessageErrorCode`（enum，21 个常量）、`MessageErrorMessages`（code→msg 静态 Map）、`MessageGrpcError`（双向转换 + `CodeError` 异常 + `BIZ_CODE_BY_MSG` 反查表）。

`resources/db/migration` 已有 3 个 Flyway 脚本：`V1__create_message_notification.sql`、`V2__create_message_conversation.sql`、`V3__create_message_conversation_message.sql`。

### 已有组件（跨模块可复用）

| 组件 | 位置 | 承担 |
|---|---|---|
| `com.rag.common.response.Response` | `service/common` | HTTP 信封（message-rpc 是 gRPC 进程，不直接消费 —— 但 api 侧用） |
| `com.rag.common.exception.BusinessException` | `service/common` | 业务异常基类（message-rpc 主要抛出 `StatusRuntimeException` 而不是 `BusinessException`，因 gRPC 进程无 HTTP envelope） |
| `com.rag.common.snowflake.Snowflake` + `SnowflakeBean` | `service/common` | 64 位雪花 ID 生成（已经过 Spring DI 路径接管） |
| `com.rag.common.observability.*` | `service/common` | gRPC 可观测性拦截器 `RpcInterceptor`，被 `spring/RpcServerInterceptorFactory` 全局注册 |
| `com.rag.common.spring.LoggerFactoryBean` | `service/common` | 注入 `Tracer` / `Logger` |
| `com.rag.common.spring.SnowflakeProperties` | `service/common` | Snowflake workerId 外部化配置 |
| `com.rag.message.message.common.errmsg.*` | `message-common` | 业务错误码 + msg 表 + gRPC 错误双向转换 |

### 已确认的框架机制

- `net.devh:grpc-server-spring-boot-starter`：`@GrpcService` 自动注册为 gRPC server handler；`@GrpcClient("user-rpc")`/`@GrpcClient("admin-rpc")` 自动注入 BlockingStub
- MyBatis-Plus `@TableName` + `BaseMapper<Entity>` + `@TableId(type=ASSIGN_ID)` + `LambdaQueryWrapper`/`LambdaUpdateWrapper`
- Spring `@ConfigurationProperties` + `@ConfigurationPropertiesScan`
- `@Transactional`（AOP 代理）事务
- MyBatis-Plus 内置 `JacksonTypeHandler`（NotificationEntity 的 `extra` 字段 jsonb 绑定）
- Flyway `V*__create_*.sql` 迁移脚本（**有状态账本 vs Go 无状态 `AutoMigrate`**）
- Spring Cloud Alibaba Nacos Discovery：`bootstrap/NacosRegistration` 手动 register/deregister（不依赖 `spring-cloud-starter-alibaba-nacos-discovery` 自带的 web 启动方式）
- Micrometer `MeterRegistry` + `Counter`/`Timer`，命名 1:1 对齐 Go `prometheus.NewCounterVec`

### 当前 Component 可以直接复用的能力

- **snowflake ID 生成**：`SnowflakeBean` 已存在于 service/common；message-rpc 的 `MessageDomainService.nextSnowflakeId()` 调 `Snowflake.nextId()` 经静态委托调 Bean
- **指标底座**：Micrometer `MeterRegistry` 自动注入；`MessageMetrics` 只承载 message 特有指标（`message_rpc_*` namespace）
- **gRPC 可观测性拦截器**：`RpcServerInterceptorFactory` **确实已注册** —— `MessageRpcApplication.java:19` 的 `scanBasePackages` 含 `com.rag.common`。✅ 全仓唯一注册成功的 rpc 模块（`user-rpc` / `admin-rpc` 均未注册，见 `FileTree/common.md` §12.7 #1）。service 层不重复
- ⚠️ **但属撞运气**：message-rpc 能跑是因为它**恰好**有 `spring-boot-starter-web-server` 兜住了被 `@ComponentScan` 递归拖进来的 `HttpMiddlewareFilter`（`MessageRpcApplication:19` 扫了 `com.rag.common`）。**已定案**：与其余 rpc 模块一并归一到**显式 `@Import`**，不再扫 `com.rag.common`（`common.md` §12.7 #1）
- **gRPC code ↔ biz code 双向转换**：`MessageGrpcError.grpcError(grpcCode, bizCode)` + `bizCodeFromError(Throwable)` 已在 `message-common` 落地
- **JsonbTypeHandler 行为**：`JacksonTypeHandler` 内置，无需手写
- **Nacos 注册**：RAG 端走显式 `NacosRegistration`（不依赖 `WebServerInitializedEvent`，因 message-rpc 无 web 容器）

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| Flyway 迁移归属 | ✅ 已按 AGENTS.md §6.3（2026-10-21）定案：单一归属 + 全局唯一版本号。message 的 3 张表独占 → 归 message-rpc。⚠️ **注意原记录有误**：它们不是「不同 schema」，而是**同一个 `first_db` 的同一张 `flyway_schema_history`** —— message 的 V1/V2/V3 与 user-rpc(V1/V2)、admin-rpc(V1/V2) **实冲突**。定案后 message-rpc 迁到 V5-V7 |
| `MessageRpcSupport` 是否需拆分 | `UNKNOWN` —— 当前 1 个工具类聚合全部 helper（response factory + normalize + participant logic + util）；如果未来加入「admin 发发到 user 的反向通知」等更多角色，现有 `Notification` 实体可能需要 split。**当前**无独立边界依据 |
| `MessageExceptions` 与 `MessageGrpcError.CodeError` 的边界 | `UNKNOWN` —— 当前 RAG 端两套异常并存：`MessageExceptions` 4 类给 gRPC 路径内部数据层 try/catch 用，`MessageGrpcError.CodeError` 给 api/外部用。是否需整合 —— 待 audit 后回填 |

---

## 12.2 第二部分：Go 原始文件树

```
service/message/rpc/
├── message.go                                      # main 入口：flag、config、metrics、etcd register、gRPC server start
├── messageservice/messageservice.go                # goctl 生成的 client stub（UserRpc 端用）
├── etc/message.yaml                                # 配置：Postgres + UserRpc + AdminRpc + List/Broadcast
├── internal/
│   ├── config/config.go                            # Config：zrpc.RpcServerConf + Postgres + UserRpc/AdminRpc + List/Broadcast
│   ├── svc/servicecontext.go                       # ServiceContext：Config + MessageModel + UserRpc + AdminRpc
│   ├── server/messageserviceserver.go              # goctl 生成的 MessageServiceServer 9 方法分发
│   ├── logic/
│   │   ├── sendchatmessagelogic.go                 # SendChatMessage（最复杂：conv status 状态机 + PENDING 单方连发拦截）
│   │   ├── sendnotificationlogic.go                # SendNotification（broadcast=true → admin.GetUserList 拉所有 user_id）
│   │   ├── listconversationslogic.go               # ListConversations（peer profile + unread count）
│   │   ├── getconversationmessageslogic.go         # GetConversationMessages
│   │   ├── markconversationreadlogic.go            # MarkConversationRead
│   │   ├── listnotificationslogic.go               # ListNotifications（按 unreadOnly 过滤）
│   │   ├── marknotificationreadlogic.go            # MarkNotificationRead（单条 → NOT_FOUND if 不属于 user）
│   │   ├── markallnotificationsreadlogic.go        # MarkAllNotificationsRead
│   │   ├── getunreadsummarylogic.go                # GetUnreadSummary（notification_unread + conversation_unread）
│   │   └── helper.go                               # successBaseResp / validRole / normalizeLimit / canonicalParticipants / isParticipant / peerOf / canSend / loadProfile / buildConversationItem / collectBroadcastRecipients / dedupeRecipients / newNotificationRecord / notificationKindLabel / errWithBizCode / toNotificationItem / toMessageItem / safeStatusBiz
│   ├── metrics/metrics.go                          # 5 个 prometheus Counter/HistogramVec：request_total / request_duration_seconds / notification_action_total / chat_action_total / db_error_total
│   └── model/
│       ├── data_model.go                           # Notification / Conversation / ConversationMessage struct + JSONMap driver.Valuer/scanner
│       ├── db.go                                   # InitDB / AutoMigrate 3 张表
│       └── message.go                              # MessageModel：CRUD + 4 个事务（CreateConversationWithMessageTx / CreateOrSendMessageTx / MarkAllNotifications 等）+ TrimPreview
└── pb/                                             # 生成物，message.pb.go + message_grpc.pb.go
```

---

## 12.3 第三部分：Go 文件职责分析

### `message.go`
- **主要职责**：进程入口。flag、config.MustLoad、`metrics.InitMetrics()`、`logger.Init(c.Name)`、`observability.DisableNativeRpcTimeout`、构造 `ServiceContext`、用 `zrpc.MustNewServer` 启动 gRPC server。
- **关键调用关系**：`conf.MustLoad(*configFile, &c)` → `logx.MustSetup(c.Log)` → `logger.Init(c.Name)` → `observability.DisableNativeRpcTimeout(&c.RpcServerConf)` → `svc.NewServiceContext(c)` → `server.NewMessageServiceServer(ctx)` → `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(...))` → `s.Start()`。
- **业务语义**：无（纯装配）；含 1 项关键语义：`Mode == DevMode || TestMode` 时启用 reflection。
- **可替换**：Spring `@SpringBootApplication` + `@ConfigurationPropertiesScan` + `@GrpcService` 自动注册 + `NacosRegistration`（手写 Nacos 注册替代 etcd）—— **所有职责由框架吸收**。

### `internal/config/config.go`
- **主要职责**：配置结构体（嵌入 `zrpc.RpcServerConf` + `Postgres` + `UserRpc` + `AdminRpc` + `List{DefaultLimit, MaxLimit}` + `Broadcast{PageSize}`）。
- **业务语义**：无。
- **可替换**：Spring `@ConfigurationProperties(prefix="message")` + `MessageProperties.java` 1:1 字段对应：`list.defaultLimit=20`、`list.maxLimit=100`、`broadcast.pageSize=200`。**字段语义 1:1**。

### `internal/svc/servicecontext.go`
- **主要职责**：依赖持有 —— `Config`、`MessageModel`、`UserRpc`、`AdminRpc`。**关键动作**：`snowflake.Init()` + `model.InitDB(...)` + `userservice.NewUserService(zrpc.MustNewClient(c.UserRpc))`。
- **业务语义**：`UserRpc` / `AdminRpc` 是**跨服务 gRPC client**，不可内联到 model。
- **可替换**：Spring DI（构造器注入）。`UserRpc`/`AdminRpc` → `@GrpcClient("user-rpc") UserServiceBlockingStub` + `@GrpcClient("admin-rpc") AdminServiceBlockingStub`（devh starter 接管 channel pool）。**Snowflake init**走 `SnowflakeBean` 路径。

### `internal/server/messageserviceserver.go`
- **主要职责**：gRPC server 方法分发（goctl 生成的 9 个方法 override 转 `logic.NewXxxLogic(ctx, svcCtx)`）。
- **业务语义**：无（纯分发）。
- **可替换**：`@GrpcService extends MessageServiceGrpc.MessageServiceImplBase`，9 个 override 转 `domain.Xxx(req)`。**9 个方法必须显式写**（参数转换 + 指标转发 + 异常映射）。

### `logic/helper.go`
- **主要职责**：跨 9 个 logic 共享的纯函数集：
  - 响应工厂：`successBaseResp`、`toNotificationItem`、`toMessageItem`、`mapString`
  - 校验/归一化：`validRole`、`normalizeLimit`、`trimContent`、`runeLen`、`canonicalParticipants`、`isParticipant`、`peerOf`、`canSend`、`notificationKindLabel`
  - 参与者规范化：`canonicalParticipants`(role1, id1, role2, id2) → `(r1, i1, r2, i2)` 按 (role asc, id asc) 排序，让 `uk_conversation_pair` 唯一索引找到同一对话对
  - 错误工厂：`errWithBizCode(grpcCode, bizCode)` 调 `messagecommon.GRPCError(grpcCode, bizCode)`
  - peer profile：`loadProfile(role, id)` → USER 调 `UserRpc.GetUser`、ADMIN 调 `AdminRpc.GetSelf`，失败兜底 `"User {id}"` / `"Admin {id}"`
  - 构建 `ConversationItem`：`buildConversationItem(conv, operatorRole, operatorID)` → 取 peer、调 `CountUnreadMessagesByConversation`、`loadProfile(peerRole, peerID)`，返回 `pb.ConversationItem`
  - 广播收件人：`collectBroadcastRecipients(ctx, svcCtx)` → 调 `AdminRpc.GetUserList` 分页去重拉取所有 user_id
  - 工具：`dedupeRecipients(ids)` 去重保留顺序、`newNotificationRecord(...)` 用 snowflake 分配 ID 构造 `Notification`、`safeStatusBiz(err, notFoundCode)` 把 `ErrRecordNotFound` 包成 `errWithBizCode(codes.NotFound, notFoundCode)` 后透传
- **业务语义（关键）**：
  - **`canonicalParticipants`**：该函数实现**唯一性归一化**。`uk_conversation_pair` 联合唯一索引 `(p1_role, p1_id, p2_role, p2_id)` 要求两端的顺序与角色一致；callers 不归一化的话，`(USER 1, USER 2)` 与 `(USER 2, USER 1)` 会变成两条不同的 conv 行
  - **`canSend(conv, role, id)`**：PENDING 状态下只有「非 pending_sender 的一方」能发，OPEN 全员可发
  - **`peerOf`**：找不到 peer → 返回 `(UNSPECIFIED, 0, false)`，调用方再抛 `PermissionDenied`
  - **`loadProfile`**：**profile 失败不阻塞业务**（兜底字符串），这是显式的容错决策，不是遗漏
- **可替换的机制**：文件划分可合并（Go 一文件一文件 ≠ Java 必须一文件）。Java 端把 helper 收口在 `service/MessageRpcSupport`（已落地）：
  - 静态方法（无外部依赖）：`successBaseResp` / `toNotificationItem` / `toMessageItem` / `validRole` / `normalizeLimit` / `trimContent` / `runeLen` / `canonicalParticipants` / `isParticipant` / `peerOf` / `canSend` / `notificationKindLabel` / `dedupeRecipients` / `trimPreview` / `newNotificationRecord` / `grpcRuntimeError` / `buildConversationItem`（**注意**：buildConversationItem 在 Go 中含 `loadProfile` 外部 stub 调用，Java 已拆出，由 `DomainService` 在调用前先 `client.loadProfile` 后再传 `name/email` 给 `buildConversationItem` 静态方法）
  - **外部 stub 依赖**（`loadProfile` / `collectBroadcastRecipients`）已迁到 `client/UserRpcClient`、`client/AdminRpcClient`，避免工具类持有 gRPC client

### `logic/sendchatmessagelogic.go`（最复杂）
- **主要职责**：单一入口处理 4 类会话创建场景，全部沿 `(status, conv, sender)` 三轴：
  1. **content 校验**：`trim + senderId>0 + validRole(senderRole)` → 否则 `INVALID_ARGUMENT + ErrorInvalidParam (5003)`
  2. **按 `ConversationId>0` 区分**：
     - **指定 convId**：`FindConversationByID`，不存在 → `NOT_FOUND + ErrorConversationMiss (5202)`；非参与者 → `PERMISSION_DENIED + ErrorForbidden (5005)`；`peerOf` 找不到对端 → 同样 5005
     - **不指定 convId**：`canonicalParticipants` → `FindConversationByParticipants` 找现有会话语义；如果 `ErrRecordNotFound` 被吞（**这是 1:1 行为**，conv == nil 表示要新建）
  3. **conv == null 分支**：
     - ADMIN 发起 → 直接 OPEN（要求 `recipient_role == USER`，否则 `INVALID_ARGUMENT + ErrorUnsupportedRole (5207)`）
     - USER 发起 → PENDING（首条 `runeLen(content) > 50` → `INVALID_ARGUMENT + ErrorFirstMsgTooLong (5206)`；非 USER 对 USER 创建 → `PERMISSION_DENIED + ErrorAdminOnlyInit (5205)`）
     - 两个分支都走 `CreateConversationWithMessageTx` 事务
  4. **conv 存在分支**（append）：
     - PENDING 状态下如果当前 sender 就是 `pending_sender`（同方连发） → `FAILED_PRECONDITION + ErrorChatPending (5204)`
     - PENDING → OPEN 切换（清掉 pending_sender）；UNSPECIFIED 状态视作 OPEN
     - 走 `CreateOrSendMessageTx` 事务
- **业务语义（关键）**：
  - **`runeLen > 50`** 校验只在「首次发起的 USER→USER 会话」成立；ADMIN→USER 跳过这条限制（admin 直接 OPEN，无需长度校验）
  - **`ErrorRecordNotFound` 吞错**（line 87-90）：conv 不存在不报错而是继续走新建分支 —— 这是 sendchat 独特的「find or create」模式
  - **同方连发拦截**：PENDING 状态下 pending_sender 不能再发（避免 pending_sender 自己跟自己对话升级）
- **L4 差异点**（Java 已落地）：业务分支顺序由 `MessageDomainService.doSendChatMessage()` 按 line-by-line 翻译；error code 映射由 `MessageRpcSupport.grpcRuntimeError(grpcCode, bizCode)` 完成。**注意**：Java 显式判断 `convOpt == null || convOpt.isEmpty()`，Go 用 `conversation == nil`（gORM 未找到 → 返回 nil + ErrRecordNotFound）—— 等价语义但条件写法略不同

### `logic/sendnotificationlogic.go`
- **主要职责**：admin 域发通知（点对点 + 广播双模式）。
  1. `title/content` trim 后非空 → 否则 `INVALID_ARGUMENT + ErrorInvalidParam (5003)`
  2. `recipients = dedupeRecipients(in.RecipientIds)` 去重
  3. **`broadcast=true`**：覆盖之前 recipients，调 `collectBroadcastRecipients(ctx, svcCtx)` 从 admin `GetUserList` 分页拉所有 user_id（pageSize 兜底 200）；失败 → 透传错误（无单独 error code 映射）
  4. `recipients` 为空 → `INVALID_ARGUMENT + ErrorInvalidParam (5003)`
  5. 逐个用 `newNotificationRecord` 构造 Notification（snowflake 分配 ID）
  6. `CreateNotifications` 批量插入（Go 端用 `CreateInBatches(items, 200)`，Java 用 `<script>` foreach 多行 VALUES）
- **业务语义（关键）**：
  - **`broadcast=true` 覆盖**之前的 RecipientIds（注意：不是合并，是覆盖）。recipients 来自 admin 域的 user 表
  - **`affects` 返回**：Go 端 `Affected: int64(len(items))` —— 与「recipients 去重后数量」相等
- **可替换**：Java `MessageDomainService.doSendNotification()` 已落地。`collectBroadcastRecipients` 已迁到 `AdminRpcClient.collectBroadcastRecipients(pageSize)`。

### `logic/listconversationslogic.go`
- **主要职责**：按 `operator` 角色查会话列表（含 unread count + peer profile）。
  1. `OperatorId > 0 && validRole(operatorRole)` 校验
  2. `normalizeLimit(Limit, DefaultLimit, MaxLimit)` 截断
  3. `MessageModel.ListConversations(ctx, role, id, offset, limit)`，按 `(p1_role, p1_id) OR (p2_role, p2_id)` 查，`ORDER BY last_message_at desc, updated_at desc`（**注意**：Go 端双字段排序做 tie-breaker，RAG 端只 sort by last_message_at —— 见 §12.7 #4）
  4. `MessageModel.CountUnreadConversationMessages(ctx, role, id)` 拿 operator 全局未读
  5. 逐个 conv 调 `buildConversationItem`（包含 peer profile + 单会话 unread + can_send）
- **业务语义**：`buildConversationItem` 内部调 `CountUnreadMessagesByConversation`（单会话未读）+ `loadProfile`（peer profile）—— 失败兜底字符串不阻塞
- **可替换**：Java `MessageDomainService.doListConversations()` 已落地。

### `logic/getconversationmessageslogic.go`
- **主要职责**：按 conversationId 拉历史消息（user-side 视角）。
  1. `OperatorId > 0 && conversationId > 0 && validRole(operatorRole)`
  2. `FindConversationByID` → `NOT_FOUND + ErrorConversationMiss (5202)`
  3. `isParticipant` 检查 → 否则 `PERMISSION_DENIED + ErrorForbidden (5005)`
  4. `normalizeLimit` 后 `ListConversationMessages` 按 conversation_id 拉，`ORDER BY created_at asc`
  5. 返回 `items + status + canSend + pendingSenderId + pendingSenderRole`
- **业务语义**：返回 `Status` / `canSend` / `pendingSender*` 三字段是为了前端 UI 渲染：PENDING + can_send=false 表示「现在不能发，得等对方」
- **可替换**：Java `MessageDomainService.doGetConversationMessages()` 已落地。

### `logic/markconversationreadlogic.go`
- **主要职责**：标已读（用户侧）。
  1. `OperatorId > 0 && conversationId > 0 && validRole(operatorRole)`
  2. `FindConversationByID` → `NOT_FOUND + ErrorConversationMiss (5202)`
  3. `isParticipant` 检查 → 否则 `PERMISSION_DENIED + ErrorForbidden (5005)`
  4. `MessageModel.MarkConversationRead(ctx, conversationID, role, id)`：UPDATE `message_conversation_message` SET `is_read=true, read_at=now` WHERE `conversation_id=? AND recipient_role=? AND recipient_id=? AND is_read=false`
- **业务语义**：只标 recipient 自己的未读，不会影响其他参与者；Go 端不返回行数（无论更新了几条都返回 success）
- **可替换**：Java `MessageDomainService.doMarkConversationRead()` 已落地。事务在 `markConversationReadTx(conversationId, recipientId, recipientRole, readAt)`：
  - `messageMapper.markReadByConversationAndRecipient(...)` + `conversationMapper.touchUpdatedAt(...)`
  - 行为对照 Go：在同一个事务里同时 UPDATE message 表和 conversation.updated_at

### `logic/listnotificationslogic.go`
- **主要职责**：recipient 拉通知列表（含 `total` + `unread_count`）。
  1. `UserId > 0`
  2. `normalizeLimit`
  3. `ListNotifications(recipientID, offset, limit, unreadOnly)` 一次性返回 `items + total + unread`
- **业务语义**：与 article 等 list 操作不同 —— 这里的 total + unread + items 是**同一次 query 链上的 3 个计数**，未读永远 = `unreadOnly=false` 的全量 unread count（**不受 unreadOnly 过滤影响**）
- **可替换**：Java `MessageDomainService.doListNotifications()` 已落地；**注意**：Java 端用 3 个独立 query（`listByRecipient` + `selectCount` + `countUnreadByRecipient`）替换了 Go 端的复合方法。**Go 是单事务 3 个 count，Java 是 3 次独立查询**（事务边界放宽）。在 message_service 并发量不大时不会出可见差异，但属 minor —— 见 §12.7 #5

### `logic/marknotificationreadlogic.go`
- **主要职责**：单条通知标已读，含权限检查（不能标别人的通知）。
  1. `UserId > 0 && notificationId > 0`
  2. `MessageModel.MarkNotificationRead(ctx, recipientID, notificationID)`：UPDATE WHERE `id=? AND recipient_id=?` + `is_read=true`；**如果 RowsAffected == 0 → 返回 ErrRecordNotFound**
  3. `errors.Is(err, model.ErrRecordNotFound)` → `NOT_FOUND + ErrorNotificationMiss (5203)`
  4. 其它 error → 透传 + `ObserveDBError("notification", "mark_read")`
- **业务语义（关键）**：单条权限检查通过「UPDATE WHERE id + recipient_id」做（不是先 SELECT 再 UPDATE），这样天然防 race condition；RAG 端 `notificationMapper.markRead` 走 `updateById` 不带条件 —— **见 §12.7 #2 关于 RAG 行为回归**

### `logic/markallnotificationsreadlogic.go`
- **主要职责**：批量标已读（recipient 维度）。
  1. `UserId > 0`
  2. `MessageModel.MarkAllNotificationsRead(ctx, recipientID)`：UPDATE WHERE `recipient_id=? AND is_read=false`，**不区分 role/kind**
  3. err → 透传 + `ObserveDBError("notification", "mark_all_read")`
- **业务语义**：所有未读通知一律标已读。Go 端 prototype `MarkAllNotificationsReq` 只有 `user_id`，**没有 role/kind 过滤**。
- **可替换**：Java `MessageDomainService.doMarkAllNotificationsRead()` + `markAllNotificationsReadTx(recipientId, senderRole, kind, readAt)`。**注意**：RAG 端 `markAllReadByRecipient` 形参带 `senderRole / kind` 实际用 0/0（永远不过滤）—— 见 §12.7 #3

### `logic/getunreadsummarylogic.go`
- **主要职责**：拿 recipient 的「通知未读」+「会话未读」汇总。
  1. `UserId > 0`
  2. `MessageModel.CountNotifications(ctx, userID, true)` → `notification_unread`
  3. `MessageModel.CountUnreadConversationMessages(ctx, USER, userID)` → `conversation_unread`
  4. 返回 `total_unread = notification_unread + conversation_unread`
- **业务语义（关键）**：`conversation_unread` 强制用 `SenderRole_USER`（普通 user 收件箱）；如果 admin 调用此 RPC，传 `SenderRole_ADMIN` 会得到不同结果（`CountUnreadConversationMessages` 按 role 过滤）
- **可替换**：Java `MessageDomainService.doGetUnreadSummary()` 已落地。

### `model/data_model.go`
- **主要职责**：3 个 struct + `JSONMap{map[string]string}` 实现 GORM 的 `driver.Valuer` / `sql.Scanner` 实现 jsonb 自定义类型。
  - `Notification{ID, RecipientID, SenderID, SenderRole, Kind, Title, Content, IsRead, ReadAt*, Extra JSONMap, CreatedAt, UpdatedAt}`
  - `Conversation{ID, Participant1Role/ID, Participant2Role/ID, Status, PendingSenderRole/ID, CreatedByRole/ID, LastMessageID, LastMessagePreview, LastMessageAt, CreatedAt, UpdatedAt}`
  - `ConversationMessage{ID, ConversationID, SenderID/Role, RecipientID/Role, Content, IsRead, ReadAt*, CreatedAt}`
- **业务语义（关键）**：
  - **`gorm:"primaryKey;autoIncrement:false"`** —— ID 由 caller 提供（即 snowflake），非 PG 自增
  - **`gorm:"column:..."`** 全 snake_case；字段命名与 `MessageErrorMessages` 一一对应
  - **JSONMap**：把 `map[string]string` 编解码为 PG `jsonb`（`json.Marshal` 写 / `json.Unmarshal` 读）。Java 端由 `JacksonTypeHandler` 内置
  - **`Conversation.LastMessageAt`** 有 `@gorm:"index"`，是 `ListConversations ORDER BY last_message_at desc` 的支撑索引
  - **`Conversation.UpdatedAt`** 有 `@gorm:"index"`，是 Go 端 `Order(... "updated_at" desc)` tie-breaker 的支撑索引（RAG 端未用，见 §12.7 #4）
  - **`ConversationMessage.created_at`** 加入复合索引 `idx_message_conversation_time(conversation_id, created_at)`
- **L1 字段 1:1 必须保留**；RAG 端 3 个 Entity 已落地（@TableName + @TableId(ASSIGN_ID) + @TableField）。

### `model/db.go`
- **主要职责**：连接 PG（`gorm.Open(postgres.Open(dsn))`）+ `SetMaxIdleConns(10)` / `SetMaxOpenConns(100)` + `AutoMigrate(&Notification, &Conversation, &ConversationMessage)`。
- **业务语义**：AutoMigrate **无状态**（每次启动比对 schema 后 diff 执行），不写 migration 历史；Flyway **有状态账本**（`flyway_schema_history` 表 + 校验和）。**两者不可同时启用**。
- **可替换**：Spring Boot 走 `spring.datasource.url + postgresql driver + mybatis-plus-spring-boot3-starter`；Flyway `V1/V2/V3__create_*.sql` 替代 AutoMigrate。

### `model/message.go`
- **主要职责**：`MessageModel` 结构 + 11 个方法：
  - 单条 CRUD：`CreateNotifications`（batch size 200）、`CountNotifications(unreadOnly)`、`ListNotifications(recipientID, offset, limit, unreadOnly)`（同时返回 items + total + unread）、`MarkNotificationRead(recipientID, notificationID)`（UPDATE WHERE id+recipient_id，RowsAffected==0 → ErrRecordNotFound）、`MarkAllNotificationsRead(recipientID)`
  - Conversation CRUD：`FindConversationByID`、`FindConversationByParticipants`（4 字段精确匹配）、`ListConversations(role, id, offset, limit)`（p1 OR p2）、`CountUnreadConversationMessages(role, id)`、`CountUnreadMessagesByConversation(conversationID, role, id)`
  - 消息 CRUD：`ListConversationMessages(conversationID, offset, limit)`、`MarkConversationRead(conversationID, role, id)`
  - 事务：`CreateConversationWithMessageTx`、`CreateOrSendMessageTx`
  - 工具：`TrimPreview(content)` — `[]rune(content)[:80]` 截断（**注意**：Go 用 **rune 80**，RAG 用 **String.length() 255**，见 §12.7 #1）
- **业务语义（关键）**：
  - **`MarkNotificationRead`** 用单 UPDATE 兼顾权限 + 已读：WHERE `id=? AND recipient_id=?`，所以"标自己的通知"和"挡住别人标自己的通知"是同一条 SQL 实现。RAG 端拆为先 SELECT 校验、再 UPDATE —— **race window** 期间 SELECT 检查通过但 row 被删会导致 UPDATE 失败
  - **`CreateConversationWithMessageTx`**：在事务里 conversation INSERT 成功后**用生成 ID 回填 message.ConversationID**（`message.ConversationID = conversation.ID`）—— 但 Java 端已经在 caller 处把 conversation.getId() 传给 message 实体，无需此赋值
  - **`CreateOrSendMessageTx`**：先 `now := message.CreatedAt; if zero { now = time.Now() }`，然后 conversation.UPDATE 把 last_message_* 同步，再 message.INSERT。Java 端 caller 把 `LocalDateTime.now()` 提前构造好，事务内不再重新取

### `metrics/metrics.go`
- **主要职责**：5 个 prometheus collector + 4 个 `Observe*` 函数：
  - `RPCRequestCounter{Subsystem: "message_rpc", Name: "request_total", Labels: [method, result]}`
  - `RPCRequestDuration{Subsystem: "message_rpc", Name: "request_duration_seconds", Labels: [method]}`
  - `NotificationActionCounter{Subsystem: "message_rpc", Name: "notification_action_total", Labels: [kind, result]}`
  - `ChatActionCounter{Subsystem: "message_rpc", Name: "chat_action_total", Labels: [action, result]}`
  - `DBErrorCounter{Subsystem: "message_rpc", Name: "db_error_total", Labels: [action, type]}`
- **触发点**：
  - `ObserveRPC(method, started, err)`：每个 RPC 入口 `defer` 调
  - `ObserveNotification(kind, err)`：`SendNotification` end
  - `ObserveChat(action, err)`：`SendChatMessage` end（`action="send"`）
  - `ObserveDBError(action, typ)`：DB 出错分支按动作类型分别埋点（`chat find_conversation` / `chat find_by_pair` / `chat create_conversation` 等 14+ 种 type）
- **L1**：metric 名称 + labels 命名属 L1，**Prometheus 抓取端配置不改**。RAG `MessageMetrics` 已落地 1:1 命名（用 Micrometer 替换 prom client）。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `message.go` 进程装配 | `MessageRpcApplication` + Spring 容器 | ABSORB | `@SpringBootApplication` 覆盖 flag/config/server 组装；starter 自动注册 `@GrpcService` |
| `message.go` etcd 注册 | `bootstrap/NacosRegistration` | ADAPT | 服务发现机制从 etcd 换 Nacos；**纯外部发现协议，非行为语义** |
| `config/config.go` 配置绑定 | `config/MessageProperties` | ADAPT | `@ConfigurationProperties(prefix="message")` 1:1 字段映射；`zrpc`/`Postgres`/`UserRpc`/`AdminRpc` 由 Spring application.yml 接管，本类只承载 `List` + `Broadcast` 业务配置 |
| `servicecontext.go` 依赖持有 | Spring DI（构造器注入） | ABSORB | 无额外业务职责 |
| `servicecontext.go` UserRpc / AdminRpc 客户端 | `client/UserRpcClient` + `client/AdminRpcClient`（`@GrpcClient` 注入 BlockingStub + 方法 facade） | NEW | 不是简单 ABSORB：把 `loadProfile + collectBroadcastRecipients` 单独抽出成 Client 类，让 `DomainService` 不直接持有 stub —— 借鉴 `users/UserRpcClient` 同款模式 |
| `server/messageserviceserver.go` 9 方法分发 | `service/impl/MessageRpcServiceImpl` 9 个 override | ADAPT | 分发由 starter 完成，但 override 体（参数转换 + 异常透传 + INTERNAL 兜底）需显式写 |
| `helper.go` static helper（无依赖） | `service/MessageRpcSupport`（static 方法集合 + CanonicalPair/PeerResult record） | MERGE | Java 包成 1 个 `@Component` 类是因为没有 package-level function；与 Go helper.go 1:1 |
| `helper.go` `loadProfile(role, id)` | `client/UserRpcClient.loadProfile()` + `client/AdminRpcClient.loadProfile()` | SPLIT | profile 失败兜底 + 跨 RPC stub 调用 —— 与「工具类持 RPC stub」反模式相悖，必须独立 Client |
| `helper.go` `collectBroadcastRecipients` | `client/AdminRpcClient.collectBroadcastRecipients(pageSize)` | SPLIT | 同上 |
| `logic/sendchatmessagelogic.go` | `MessageDomainService.sendChatMessage` + `doSendChatMessage` + `createConversationWithMessageTx` + `createOrSendMessageTx` | SPLIT | 业务归 service，事务边界归同 service 的 `@Transactional` 方法；但 self-invocation 风险存在（§12.7 #6） |
| `logic/sendnotificationlogic.go` | `MessageDomainService.sendNotification` + `doSendNotification` + `insertNotificationsTx` | SPLIT | 同上 |
| `logic/listconversationslogic.go` | `MessageDomainService.listConversations` + `doListConversations` | MERGE | 单用例，单方法 |
| `logic/getconversationmessageslogic.go` | `MessageDomainService.getConversationMessages` + `doGetConversationMessages` | MERGE | 同上 |
| `logic/markconversationreadlogic.go` | `MessageDomainService.markConversationRead` + `doMarkConversationRead` + `markConversationReadTx` | SPLIT | 业务 + 事务 |
| `logic/listnotificationslogic.go` | `MessageDomainService.listNotifications` + `doListNotifications` | MERGE | — |
| `logic/marknotificationreadlogic.go` | `MessageDomainService.markNotificationRead` + `doMarkNotificationRead` + `markNotificationReadTx` | SPLIT | 业务 + 事务 |
| `logic/markallnotificationsreadlogic.go` | `MessageDomainService.markAllNotificationsRead` + `doMarkAllNotificationsRead` + `markAllNotificationsReadTx` | SPLIT | 业务 + 事务 |
| `logic/getunreadsummarylogic.go` | `MessageDomainService.getUnreadSummary` + `doGetUnreadSummary` | MERGE | — |
| `data_model.go` `Notification` struct | `domain/NotificationEntity` | ADAPT | `@TableName("message_notification")` + `@TableId(type=ASSIGN_ID)`；列/约束 1:1 |
| `data_model.go` `Conversation` struct | `domain/ConversationEntity` | ADAPT | 同上 |
| `data_model.go` `ConversationMessage` struct | `domain/ConversationMessageEntity` | ADAPT | 同上 |
| `data_model.go` `JSONMap` | `NotificationEntity.extra` + `NotificationMapper.insertBatch` 的 `JacksonTypeHandler` | REUSE | MyBatis-Plus 内置，无需手写 |
| `db.go` `AutoMigrate` | Flyway `V1__create_message_notification.sql` + `V2__create_message_conversation.sql` + `V3__create_message_conversation_message.sql` | ADAPT | 有状态账本 vs 无状态迁移；**V 号段归属待用户拍板**（与 user-rpc / admin-rpc 共享 `flyway_schema_history`） |
| `message.go` TrimPreview（`[]rune[:80]`） | `MessageRpcSupport.trimPreview`（`String.length()[:255]`） | **DIVERGE** | **Java 用了 255 字节（与列宽相等）而非 80 runes** —— 见 §12.7 #1 |
| `message.go` ListConversations ORDER BY last_message_at desc, updated_at desc | `ConversationMapper.listByParticipant` 的 `orderByDesc(lastMessageAt)` | **DIVERGE** | **RAG 端漏掉 updated_at tie-breaker** —— 见 §12.7 #4 |
| `message.go` ListNotifications（事务 3 count） | `notificationMapper.listByRecipient` + `selectCount` + `countUnreadByRecipient`（3 次独立查询） | **DIVERGE** | **RAG 端放宽事务边界** —— 见 §12.7 #5 |
| `message.go` MarkNotificationRead（UPDATE WHERE id+recipient_id） | `NotificationMapper.markRead`（先 `selectById` 校验，再用 `updateById`） | **DIVERGE** | **RAG 端用先 SELECT 后 UPDATE 拆权限和已读校验**，破坏 race-condition safety —— 见 §12.7 #2 |
| `message.go` MarkAllNotificationsRead（recipient only） | `NotificationMapper.markAllReadByRecipient(recipientId, senderRole, kind, readAt)` | ADAPT | 接口签名多了 role/kind 形参，**实际仍走过滤 `eq(recipientId)` 一条**；RAG 端 caller 传 0,0 |
| `model/messsage.go` CreateNotifications（`CreateInBatches(items, 200)`） | `NotificationMapper.insertBatch`（MyBatis `<script>` + foreach 多行 VALUES） | ADAPT | Java 用单 SQL 多行 VALUES 替代 Go 端分批 |
| `metrics/metrics.go` | `metrics/MessageMetrics` | ADAPT | Micrometer 替代 prometheus client_golang；5 个 metric 命名 + label 1:1 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/message/message/rpc/
├── MessageRpcApplication.java                        # 入口；scanBasePackages=本模块 + com.rag.common
├── bootstrap/
│   └── NacosRegistration.java                        # ApplicationReadyEvent → NacosServiceManager.registerInstance
├── client/
│   ├── UserRpcClient.java                            # @GrpcClient("user-rpc") → loadProfile(uid) 兜底 "User {id}"
│   └── AdminRpcClient.java                           # @GrpcClient("admin-rpc") → loadProfile(role,id) + collectBroadcastRecipients(pageSize)
├── config/
│   └── MessageProperties.java                        # @ConfigurationProperties(prefix="message")：list + broadcast
├── domain/
│   ├── ConversationEntity.java                       # @TableName("message_conversation")
│   ├── ConversationMessageEntity.java                # @TableName("message_conversation_message")
│   └── NotificationEntity.java                       # @TableName("message_notification") + @TableField(typeHandler=JacksonTypeHandler) on extra
├── exception/
│   └── MessageExceptions.java                        # 4 内部异常类：DbSelect/DbUpdate/TransactionRollback/DatabaseException
├── mapper/
│   ├── ConversationMapper.java                       # @Mapper extends BaseMapper<ConversationEntity>
│   ├── ConversationMessageMapper.java                # @Mapper extends BaseMapper<ConversationMessageEntity>
│   └── NotificationMapper.java                       # @Mapper extends BaseMapper<NotificationEntity> + insertBatch + markRead + markAllReadByRecipient
├── metrics/
│   └── MessageMetrics.java                           # @Component + MeterRegistry：message_rpc_request_total/duration，notification_action/chat_action/db_error
└── service/
    ├── MessageDomainService.java                     # 9 用例聚合 + 5 事务方法 + 1 nextSnowflakeId 辅助
    ├── MessageRpcSupport.java                        # 静态 helper 聚合（response factory / normalize / participant logic / util）+ CanonicalPair/PeerResult record
    └── impl/
        └── MessageRpcServiceImpl.java                # @GrpcService extends MessageServiceGrpc.MessageServiceImplBase：9 个 thin override

# message-common 独立模块
message-common/.../errmsg/
├── MessageErrorCode.java                             # enum 21 常量
├── MessageErrorMessages.java                         # 静态 Map<Integer,String> + getMsg
└── MessageGrpcError.java                             # CodeError + grpcError + bizCodeFromError + BIZ_CODE_BY_MSG

# resources/db/migration/
├── V1__create_message_notification.sql               # message_notification + 2 索引
├── V2__create_message_conversation.sql               # message_conversation + uk + 4 索引
└── V3__create_message_conversation_message.sql       # message_conversation_message + 2 索引
```

### 逐文件说明

**`MessageRpcApplication.java`**
- 所属包：`com.rag.message.message.rpc`
- 主要职责：入口；`@SpringBootApplication(scanBasePackages={"com.rag.message.message.rpc", "com.rag.common"})` + `@ConfigurationPropertiesScan(basePackages="com.rag.message.message.rpc.config")`
- 承载 Go 职责：`message.go` 的进程装配（**ABSORB**）
- 依赖：`config/MessageProperties`、`mapper/*`
- 已在 RAG 中 ✅

**`bootstrap/NacosRegistration.java`**
- 主要职责：手动注册到 Nacos（不复用 `spring-cloud-starter-alibaba-nacos-discovery` 默认 web 路径，因为 message-rpc 纯 gRPC 不带 web 容器）
- 承载 Go 职责：`message.go` 中 `EtcdRegister` / `Start()`
- 已在 RAG 中 ✅
- **L4 idiomatization**：通过 `@EventListener(ApplicationReadyEvent)` 调 `NacosServiceManager.getNamingService().registerInstance(SERVICE_NAME, group, instance)`；`@PreDestroy` 反注册。`SERVICE_NAME="message-rpc"`、`group="DEFAULT_GROUP"`、IP 通过 `NetworkInterface` 解析（参见 `resolveLocalIp()` 静态方法）。**语义等价**：Nacos discovery 与 Go etcd 等价（同属服务发现），不影响 RPC wire。

**`client/UserRpcClient.java`**
- 主要职责：`@GrpcClient("user-rpc") UserServiceBlockingStub` facade；`getProfile(uid)` + `loadProfile(role, id)`（后者按 role 分发）
- 承载 Go 职责：`servicecontext.go.UserRpc` + `helper.go.loadProfile(role=USER, id)`
- 已在 RAG 中 ✅
- 关键行为：`getProfile(uid <= 0)` → 兜底 `["User 0", ""]`；RPC 失败 / `resp.getFound()=false` → 同样兜底。**与 Go 端 1:1 容错策略**（profile 仅展示用，失败不阻塞业务）

**`client/AdminRpcClient.java`**
- 主要职责：`@GrpcClient("admin-rpc") AdminServiceBlockingStub` facade；`loadProfile(role, id)` + `collectBroadcastRecipients(pageSize)`
- 承载 Go 职责：`servicecontext.go.AdminRpc` + `helper.go.loadProfile(role=ADMIN, id)` + `helper.go.collectBroadcastRecipients`
- 已在 RAG 中 ✅
- `collectBroadcastRecipients` 关键行为：pageSize `<= 0` 兜底 200；用 `LinkedHashSet` 去重保留插入顺序；空页停止；最后一页 `resp.list.size() < pageSize` 停止；RPC 失败 → 抛 `MessageExceptions.DbSelectException`

**`config/MessageProperties.java`**
- 主要职责：`@ConfigurationProperties(prefix="message")` + `@Component`；2 个嵌套 `List{defaultLimit, maxLimit}` + `Broadcast{pageSize}` + getter/setter
- 承载 Go 职责：`config/config.go.List` + `config/config.go.Broadcast`
- 已在 RAG 中 ✅

**`domain/ConversationEntity.java`**
- 主要职责：`@TableName("message_conversation")` 实体；12 字段（id/participant1_role/id/participant2_role/id/status/pending_sender_role/id/created_by_role/id/last_message_id/preview/at/created_at/updated_at）
- 承载 Go 职责：`model/data_model.go.Conversation` struct + gorm tag
- 已在 RAG 中 ✅
- **L1 1:1 字段**：列名 snake_case → camelCase（`map-underscore-to-camel-case` 启用）；`V2__create_message_conversation.sql` 定义 `participant1_role/participant1_id/.../last_message_preview` + 5 索引

**`domain/ConversationMessageEntity.java`**
- 主要职责：`@TableName("message_conversation_message")` 实体；9 字段
- 承载 Go 职责：`model/data_model.go.ConversationMessage`
- 已在 RAG 中 ✅
- 注意：`@TableField(fill = INSERT)` on `createdAt`（不再有 `updatedAt` —— Go 端 `autoUpdateTime` 在 PG 上无对应，因无业务分支触发 UPDATE，**与 Go 行为一致**）

**`domain/NotificationEntity.java`**
- 主要职责：`@TableName("message_notification")` 实体；11 字段 + `extra` 字段用 `JacksonTypeHandler`
- 承载 Go 职责：`model/data_model.go.Notification` + `JSONMap`
- 已在 RAG 中 ✅
- 关键 L4：`@TableField(typeHandler = JacksonTypeHandler.class)` on `extra` 字段（Map<String,String> ↔ PGobject jsonb）。**注意**：仅对 MyBatis-Plus 自动生成的 SQL 生效；`LambdaUpdateWrapper.set("extra", Map)` 会丢 typeHandler —— RAG 端 `insertBatch` 已在 `<script>` 内显式声明 typeHandler，但 `markRead/updateById` 走默认 `BaseMapper.updateById` 时**只更新 `extra` 默认 `null`** —— 需要确认没有 `extra` UPDATE 路径

**`exception/MessageExceptions.java`**
- 主要职责：4 个 static 嵌套异常类（DbSelect/DbUpdate/TransactionRollback/DatabaseException），供 DomainService 在 catch (RuntimeException) 时统一包
- 承载 Go 职责：Go 端 GORM 错误透传（无显式异常类）；Java 端为上层 `MessageDomainService` 的 `try { mapper.foo() } catch (RuntimeException e) { metrics.observeDbError(action, type); throw e; }` 提供 unchecked 异常载体
- 已在 RAG 中 ✅
- **L4 推导**：这是"RAG 端为了 Java checked/unchecked 模型引入的纯框架习惯"，**不是直接对应 Go 文件**。新增的合理性来自 Java 无 `error` interface ⇒ 需 unreachable unchecked exception

**`mapper/ConversationMapper.java`**
- 主要职责：`@Mapper extends BaseMapper<ConversationEntity>`；default 方法 `findByIdOpt`/`findByPair`/`listByParticipant`/`updateLastMessage`/`touchUpdatedAt`/`countByParticipant`；`@Insert`/`@Select`/`@Update` 注解方法
- 承载 Go 职责：`model/message.go` 中 conversation 相关方法：`FindConversationByID` / `FindConversationByParticipants` / `ListConversations`（顺序见 §12.7 #4 tie-breaker）/ `MarkConversationRead` 的 `conversation.updated_at` 切分
- 已在 RAG 中 ✅
- **关键 L1**：`findByPair` 的 `@Select` 必须包含 `WHERE participant1_role=? AND participant1_id=? AND participant2_role=? AND participant2_id=?`（这是 `uk_conversation_pair` 唯一索引发挥作用的精确查询）；`updateLastMessage` 的 UPDATE 字段必须**一次性更新全部 5 个字段**（last_message_id/preview/at/status/pending_sender_*/updated_at），**不能让 `markConversationRead` 触发此 UPDATE**（MarkConversationRead 只 `touchUpdatedAt`，不更新 last_message_* —— RAG 分工正确）

**`mapper/ConversationMessageMapper.java`**
- 主要职责：`@Mapper extends BaseMapper<ConversationMessageEntity>`；default 方法 `listByConversation` (cursor)/ `markReadByConversationAndRecipient` / `markAllReadByRecipient` / `listByConversationPage` (offset+limit) / `countUnreadByRecipient` / `countUnreadByRecipientGlobal`；`@Insert`/`@Select`/`@Update`
- 承载 Go 职责：`ListConversationMessages`（offset/limit）/ `MarkConversationRead`（count unread）/ `CountUnreadMessagesByConversation` / `CountUnreadConversationMessages`
- 已在 RAG 中 ✅

**`mapper/NotificationMapper.java`**
- 主要职责：`@Mapper extends BaseMapper<NotificationEntity>`；default `listByRecipient` / `countUnreadByRecipient` / `markRead(id, readAt)` / `markAllReadByRecipient`；`@Insert` `insertBatch(items)` 用 `<script>` foreach
- 承载 Go 职责：`ListNotifications` / `CountNotifications` / `MarkNotificationRead` / `MarkAllNotificationsRead` / `CreateNotifications`
- 已在 RAG 中 ✅
- **L1 关键**：`insertBatch` 必须用 `#{item.extra, typeHandler=JacksonTypeHandler}` 显式指定 typeHandler —— 否则 wrapper 路径会丢失 jsonb 绑定（AGENTS.md §3.3 教训）

**`metrics/MessageMetrics.java`**
- 主要职责：`@Component` + `MeterRegistry`；5 个 metric 命名 + 与 Go `prometheus.NewCounterVec` 等价
- 承载 Go 职责：`metrics/metrics.go` 全部
- 已在 RAG 中 ✅
- **L1 命名冻结**：`message_rpc_request_total{method, result}` / `message_rpc_request_duration_seconds{method}` / `message_rpc_notification_action_total{kind, result}` / `message_rpc_chat_action_total{action, result}` / `message_rpc_db_error_total{action, type}`

**`service/MessageDomainService.java`**
- 主要职责：`@Service`，9 个用例方法（`sendChatMessage` / `sendNotification` / `listConversations` / `getConversationMessages` / `markConversationRead` / `listNotifications` / `markNotificationRead` / `markAllNotificationsRead` / `getUnreadSummary`）+ 5 个事务方法（`createConversationWithMessageTx` / `createOrSendMessageTx` / `markConversationReadTx` / `markNotificationReadTx` / `markAllNotificationsReadTx` / `insertNotificationsTx`）+ 1 个 `nextSnowflakeId()`
- 承载 Go 职责：`logic/*.go` 全部 9 个 + 5 个 `*Tx` 事务
- 已在 RAG 中 ✅
- **L4 关键**：每个用例方法用 try/finally 包装 `metrics.observeRpc(method, started, err)`；`sendChatMessage` 额外 `observeChat("send", err)`；`sendNotification` 额外 `observeNotification(kindLabel, err)`。**与 Go 端 `defer func() { metrics.ObserveXXX(...) }()` 1:1**

**`service/MessageRpcSupport.java`**
- 主要职责：跨 RPC 共享 helper（response factory / 校验 / 参与者规范化 / 工具）。`@Component` 让 Spring 注入到 DomainService
- 承载 Go 职责：`helper.go` 全部 static helper（除 `loadProfile` / `collectBroadcastRecipients` 已迁到 client）
- 已在 RAG 中 ✅
- 内置 record：`CanonicalPair(SenderRole role1, long id1, SenderRole role2, long id2)` + `PeerResult(SenderRole role, long id, boolean found)`，与 Go 多返回值 `func canonicalParticipants(...) (SenderRole, int64, SenderRole, int64)` 1:1
- **⚠️ `trimPreview(content)` 的 byte limit 错误**（255 字符 vs Go 80 runes —— 见 §12.7 #1）

**`service/impl/MessageRpcServiceImpl.java`**
- 主要职责：`@GrpcService extends MessageServiceGrpc.MessageServiceImplBase`；9 个 override 通过 `invoke(Supplier, StreamObserver)` 转发；`invoke` 内 catch `StatusRuntimeException` 直接转发 + 兜底 `RuntimeException` → `INTERNAL + ErrorServerCommon (5001)`
- 承载 Go 职责：`server/messageserviceserver.go`（goctl 生成的分发器）+ rpc 层异常→error 转换
- 已在 RAG 中 ✅
- **L4 关键**：`invoke` 模板与 `users/UserServiceGrpcImpl` 同款 —— **跨 rpc 共用统一行为**

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `message.go` | `MessageRpcApplication` + Spring 容器 + `bootstrap/NacosRegistration` | ABSORB | 进程装配 + 服务注册全由框架/手写 Nacos |
| `messageservice/messageservice.go` | （生成物）`message-proto` 的 `MessageServiceGrpc.MessageServiceBlockingStub` | REUSE | RAG 端的 client consumer 通过 `@GrpcClient("message-rpc")` 注入（同款 stub） |
| `internal/config/config.go` | `config/MessageProperties` + `application.yml` 的 `spring.datasource.*` / `grpc.server.port` / `grpc.client.user-rpc.*` / `grpc.client.admin-rpc.*` | ADAPT | 字段 1:1；zrpc + Postgres + RpcClientConf 由 Spring 全套接管，MessageProperties 只承载 List/Broadcast 业务字段 |
| `internal/svc/servicecontext.go` | Spring DI | ABSORB | 纯依赖持有 |
| `internal/svc/servicecontext.go`（UserRpc 客户端） | `client/UserRpcClient` | NEW | devh starter 注入 stub 后做 facade + 兜底 |
| `internal/svc/servicecontext.go`（AdminRpc 客户端） | `client/AdminRpcClient` | NEW | 同上，且包含 broadcast 分页拉取 |
| `internal/server/messageserviceserver.go` | `service/impl/MessageRpcServiceImpl` | ADAPT | 9 个 override 显式写 + invoke 异常转发 |
| `internal/logic/helper.go`（static helper） | `service/MessageRpcSupport` | MERGE | Java 无 package-level function，收口 |
| `internal/logic/helper.go`（`loadProfile` USER） | `client/UserRpcClient.loadProfile` | SPLIT | 工具类持 stub 反模式 → Client |
| `internal/logic/helper.go`（`loadProfile` ADMIN） | `client/AdminRpcClient.loadProfile` | SPLIT | 同上 |
| `internal/logic/helper.go`（`collectBroadcastRecipients`） | `client/AdminRpcClient.collectBroadcastRecipients` | SPLIT | 同上 |
| `internal/logic/helper.go`（`buildConversationItem`） | `MessageRpcSupport.buildConversationItem`（去掉 stub 调用，由 DomainService 在外部把 peer profile 准备好） | SPLIT | 工具类不持 stub |
| `internal/logic/helper.go`（`errWithBizCode`） | `MessageRpcSupport.grpcRuntimeError(grpcCode, bizCode)` | ADAPT | 行为等价；只换包名（method 不变） |
| `internal/logic/helper.go`（`notificationKindLabel`、`dedupeRecipients`、`newNotificationRecord`、`validRole`、`normalizeLimit`、`trimContent`、`runeLen`、`canonicalParticipants`、`isParticipant`、`peerOf`、`canSend`、`successBaseResp`、`toNotificationItem`、`toMessageItem`、`safeStatusBiz`） | `MessageRpcSupport` 静态方法 + CanonicalPair / PeerResult record | MERGE | 1:1 静态方法集中 |
| `internal/logic/sendchatmessagelogic.go` | `MessageDomainService.sendChatMessage` + `doSendChatMessage` + 2 个 `@Transactional` 方法 | MERGE | 单用例合并到 9 合 1 service（grpc-java 框架约束） |
| `internal/logic/sendnotificationlogic.go` | `MessageDomainService.sendNotification` + `doSendNotification` + `insertNotificationsTx` | MERGE | 同上 |
| `internal/logic/listconversationslogic.go` | `MessageDomainService.listConversations` + `doListConversations` | MERGE | — |
| `internal/logic/getconversationmessageslogic.go` | `MessageDomainService.getConversationMessages` + `doGetConversationMessages` | MERGE | — |
| `internal/logic/markconversationreadlogic.go` | `MessageDomainService.markConversationRead` + `doMarkConversationRead` + `markConversationReadTx` | SPLIT | 业务 + 事务方法 |
| `internal/logic/listnotificationslogic.go` | `MessageDomainService.listNotifications` + `doListNotifications` | MERGE | — |
| `internal/logic/marknotificationreadlogic.go` | `MessageDomainService.markNotificationRead` + `doMarkNotificationRead` + `markNotificationReadTx` | SPLIT | 业务 + 事务 |
| `internal/logic/markallnotificationsreadlogic.go` | `MessageDomainService.markAllNotificationsRead` + `doMarkAllNotificationsRead` + `markAllNotificationsReadTx` | SPLIT | 业务 + 事务 |
| `internal/logic/getunreadsummarylogic.go` | `MessageDomainService.getUnreadSummary` + `doGetUnreadSummary` | MERGE | — |
| `internal/model/data_model.go`（Notification struct） | `domain/NotificationEntity` | ADAPT | JPA/ORM 换 MyBatis-Plus |
| `internal/model/data_model.go`（Conversation struct） | `domain/ConversationEntity` | ADAPT | 同上 |
| `internal/model/data_model.go`（ConversationMessage struct） | `domain/ConversationMessageEntity` | ADAPT | 同上 |
| `internal/model/data_model.go`（JSONMap） | `NotificationEntity.extra` 的 `JacksonTypeHandler`（MP 内置） | REUSE | 不写自定义 typeHandler |
| `internal/model/db.go`（`InitDB`） | `application.yml` 的 `spring.datasource.*` + `mybatis-plus-spring-boot3-starter` | ADAPT | HikariCP 替代 GORM pool；连接参数 1:1 |
| `internal/model/db.go`（`AutoMigrate(3 models)`） | Flyway `V5/V6/V7__create_*.sql` | ADAPT | 有状态账本 vs 无状态迁移；**归属已定**（AGENTS.md §6.3），3 张表独占归 message-rpc |
| `internal/model/message.go`（11 个方法） | `mapper/{ConversationMapper, ConversationMessageMapper, NotificationMapper}` 各自对应的 default + `@Insert/@Select/@Update` 方法 | SPLIT | 按实体拆 Mapper（HUMAN_ONLY.MD 决策） |
| `internal/model/message.go`（`CreateInBatches(items, 200)`） | `NotificationMapper.insertBatch`（`@Insert` + `<script>` foreach） | ADAPT | 行为等价（一 SQL 多行 VALUES） |
| `internal/model/message.go`（`TrimPreview`，**runes 80**） | `MessageRpcSupport.trimPreview`，**String.length() 255** | **DIVERGE** | **Java 端偏离 Go，需修**；参见 §12.7 #1 |
| `internal/metrics/metrics.go` | `metrics/MessageMetrics` | ADAPT | Micrometer 替换 prom client；命名 1:1 |

> **kafka/dependency/孤儿说明**：Go 端 `logic/` 下无 `_test.go`（与 user-rpc 不同，message-rpc 不存在孤儿测试目录）。`pb/` 是生成物，`messageservice/messageservice.go` 是 client stub —— **两端都已被 RAG 通过 devh starter 接管**。

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 9 个 gRPC 方法、JWT claim name（message-api `userId`）、表名 `message_*`、3 类索引（uk_conversation_pair + 单/复合索引）、枚举值（NotificationKind / SenderRole / ConversationStatus）、错误码 21 常量、SQL 字面值（`gorm:"column:..."`） |
| L2 业务语义有承载位置 | ✅ 4 类会话创建分支（spec vs unspec × ADMIN/USER）、PENDING 单方连发拦截、首条 PENDING 50 字符限制、`canonicalParticipants` 唯一性归一化、`loadProfile` 失败兜底字符串、broadcast 覆盖 recipients 列表、`MarkNotificationRead` 权限拦截通过 UPDATE WHERE |
| L3 技术语义有承载方案 | ✅ 事务 → `@Transactional`（+ 6 个 `*Tx` 方法）；jsonb 绑定 → `JacksonTypeHandler`；snowflake → `Snowflake.nextId()` 静态委托；OTEL → `GlobalOpenTelemetry.getTracer("message-rpc")` |
| 业务分支与异常路径已纳入 | ✅ 14+ 种 `db_error` `type` 已逐个对齐（find_conversation/find_by_pair/create_conversation/create_pending_conversation/append_message/broadcast_user_list/insert/insert_batch/list/count_unread/list_messages/find/mark_read/mark_all_read/mark_read_tx/mark_all_read_tx/notification/conversation/create_conversation_with_message/create_or_send_message） |
| 框架吸收有机制依据 | ✅ `message.go` → Spring Boot 启动；`servicecontext.go` → Spring DI；`server/messageserviceserver.go` → `@GrpcService` 自动注册；etcd → Nacos 显式注册 |
| 复用组件已明确 | ✅ `SnowflakeBean`、`RpcInterceptor`、`MeterRegistry`（OTEL/observability 跳过，message-rpc 不直接 HTTP） |
| 新增文件有职责依据 | ✅ `NacosRegistration`（无 web 容器的 Nacos 注册模式）、`MessageExceptions`（unchecked 异常载体）、`UserRpcClient` / `AdminRpcClient`（stub facade 模式） —— 均非"Go 有同名文件" |
| 合并有组织依据 | ✅ 9 logic → 1 `MessageDomainService`：grpc-java 框架约束（一个 service 一个 Impl 类）+ 同一变化原因 + HUMAN_ONLY.MD 锁定 |
| 拆分可解释 | ✅ `loadProfile` / `collectBroadcastRecipients` 拆 Client（外部依赖）；事务方法从业务方法拆出（`@Transactional` 边界）；helper 收口（包成类） |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Logic.java` / `Repository.java` / `DataModel.java` / `ServiceContext.java` / `Server.java` / `Types.java` |
| 未把未实现能力说成现有机制 | ✅ Flyway 归属已按 AGENTS.md §6.3 定案（不再悬空）；`ServiceContext` 拆分标 `UNKNOWN`；`CodeError` 与 `MessageExceptions` 边界标 `UNKNOWN` |

### 不通过项（必须显式指出）

| # | 问题 | 详情 |
|---|---|---|
| 1 | **`MessageRpcSupport.trimPreview` 偏离 Go（**80 runes  vs 255 chars**）** | Go 端 `model/message.go:233-239`：`TrimPreview(content)` 用 `[]rune(content)[:80]` —— **80 个 codepoints**。RAG 端 `MessageRpcSupport.java:316-325`：`trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255)` —— **255 个 UTF-16 code unit**。两类语义差异：(a) 截断长度 80 vs 255（**主要差异**：含中英混合内容 > 80 chars 时 Go 截断，Java 不截断）；(b) 度量方式 runes vs code units —— **非 BMP 字符（emoji、扩展汉字）下 Go 记 1 个 rune，Java 记 2 个 code unit**，所以 255 的上限在 RAG 侧实际只能装进约 127 个 emoji；更糟的是 `substring(0, 255)` 会在代理对中间切断，产出**落单代理项**，而 Go 的 rune 切片结构上不可能切开一个字符。**RAG 错**，行为偏离 Go。修法：按 code point 计数并用 `offsetByCodePoints` 截断（`codePointCount(0, len) <= 80 ? s : s.substring(0, s.offsetByCodePoints(0, 80))`），禁止 `substring(int,int)` 直接切 UTF-16。<br>**根因**：`255` 来自列定义 `data_model.go:61` 的 `varchar(255)`，而不是 Go 的业务规则 —— 典型的「照 schema 翻译而不是照逻辑翻译」。改完后 80 runes 的截断结果对 255 列宽天然安全，两个值不需要对齐 |
| 2 | **`markNotificationRead` 拆 SELECT + UPDATE，破坏 race-condition safety** | Go 端 `model/message.go:72-83` 走单 UPDATE：`UPDATE WHERE id=? AND recipient_id=?`，**未匹配上 RowsAffected=0 → `ErrRecordNotFound`**。权限检查和已读更新在同一原子操作。RAG 端 `NotificationMapper.markRead` 走 `updateById`（不带条件），而 `MessageDomainService.doMarkNotificationRead` 在事务外先 `selectById` 检查 `recipientId`，**SELECT 与 UPDATE 不在同一事务，违反 race safety**：SELECT 后、UPDATE 前的微小窗口内若 row 被另一事务 DELETE/转移，会让 UPDATE 抛 `optimistic lock` / `0 rows affected` 类异常，且因事务在 `markNotificationReadTx` 内部，异常不会被 catch → 返回 `INTERNAL + ErrorServerCommon (5001)`，而 Go 端干净返回 `NOT_FOUND + ErrorNotificationMiss (5203)`。**修复方向**：把 RAG 的两步拆回归单 UPDATE（用 `LambdaUpdateWrapper.eq(NotificationEntity::getId, id).eq(NotificationEntity::getRecipientId, recipientId).set(... isRead=true).set(... readAt, readAt)`），由 0 rows affected 映射回 NOT_FOUND + 5203 |
| 3 | **`markAllReadByRecipient` 接口签名带 `senderRole, kind` 实际永远传 0,0** | Go 端 `model/message.go:86-91` 与 proto `MarkAllNotificationsReq{user_id}` 都只有 userId；RAG 端 `NotificationMapper.markAllReadByRecipient(recipientId, senderRole, kind, readAt)` 与 `markAllNotificationsReadTx` 都带 role/kind 形参。`MessageDomainService.doMarkAllNotificationsRead` 调用时固定传 `0, 0`（来自 `doMarkAllNotificationsRead` 的硬编码）。**形参冗余不构成行为回归**（因为永远 0 = 不过滤），但代码层面让读者误以为 MarkAllNotifications 支持 role/kind 过滤，属于**"未来扩展即回归"** 的陷阱。**修复方向**：mapper 接口与事务方法去掉形参，与 Go proto 1:1 |
| 4 | **`ListConversations` 漏 `updated_at` tie-breaker 排序** | Go 端 `model/message.go:114-138`：`query.Order("last_message_at desc").Order("updated_at desc")`（双排序）。RAG 端 `ConversationMapper.listByParticipant`：`orderByDesc(ConversationEntity::getLastMessageAt)`，只有单字段。**行为差异**：当两条会话 last_message_at 精确相等（同一秒内产生），Go 端按 updated_at 降序排出稳定顺序，RAG 端依赖 PG 默认顺序（无保证）。`idx_conversation_updated_at` 索引已建，但 RAG 端未使用。**修复方向**：在 `listByParticipant` 加 `.orderByDesc(ConversationEntity::getUpdatedAt)` 作为 tie-breaker（与 Go 1:1） |
| 5 | **`listNotifications` 从单次 query 拆为 3 次独立查询** | Go 端 `model/message.go:39-70`：`ListNotifications` 在**同一连接**返回 `items + total + unread`（3 个 count/SELECT 共享 PG 连接 + 同事务快照）。RAG 端 `MessageDomainService.doListNotifications`：`listByRecipient()` + `selectCount(...)` + `countUnreadByRecipient(...)` 共 3 次独立查询（不在同一事务，PG 各自独立快照）。**行为差异**：3 次查询在并发场景下可能各自看到不同时间点（虽然 message_notification 是 append-only / 不存在 UPDATE，被并发改动的概率低）。**当前并发不强**时不会出可见差异，但**严格 1:1 应在事务内**。修法：用 `TransactionTemplate` 或 `@Transactional(readOnly=true)` 包住 3 次查询 |
| 6 | **6 个 `@Transactional` 方法有 self-invocation 风险** | `MessageDomainService` 上 `createConversationWithMessageTx` / `createOrSendMessageTx` / `markConversationReadTx` / `markNotificationReadTx` / `markAllNotificationsReadTx` / `insertNotificationsTx` 6 个 `@Transactional` 方法，**全部由同 Bean 内部的 `doXxx` 调用**（如 `doSendChatMessage` → `createConversationWithMessageTx`）。按 AGENTS.md §3.3 规则，self-invocation 不经 AOP 代理 → `@Transactional` 静默失效 → 事务边界不存在。**修复方向**：抽出独立 `@Service MessageAtomicTransactionService` Bean 持有这些 `*Tx` 方法（与 admin-rpc 同款模式），由 DomainService 跨 Bean 调用。当前 RAG **未落地**，无集成测试可证 |
| 7 | **`insertConv` / `insertMsg` / `insertBatch` 走 `@Transactional` 主路径但 mapper default 方法不走同一事务** | `@Transactional` 在 `MessageDomainService.*Tx`，mapper default 方法没有 `@Transactional`。**这正好就是为什么 §6 要抽独立 Bean**：让 mapper 调用和事务边界在同一代理链上。当前所有 6 个 `*Tx` 在同一个 `@Service` 内被 `do*` 调用 → 事务注解失效（重申 #6） |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| Flyway V 号段归属 | `message-rpc` 用 V1/V2/V3；`user-rpc` 与 `admin-rpc` 同样占 V1/V2/V3。可能抢同一 `flyway_schema_history` 表的版本号（取决于 schema 是否隔离）。**未拍板**（AGENTS.md §6.3） |
| `MessageRpcSupport` 是否需拆分 | 当前聚合全部 static helper。随着 message 域复杂化（可能加 admin→user 反向通知），`Notification` 实体或将分裂。**当前无独立边界依据** |
| `MessageExceptions` 与 `MessageGrpcError.CodeError` 边界 | RAG 端两套 unchecked 异常并存：`MessageExceptions` 4 类给 gRPC 内部 data layer catch，`MessageGrpcError.CodeError` 给 api 外部。是否合并为一套 —— 未拍板 |
| `MessageExceptions.DatabaseException` 是否被使用 | 当前 `MessageDomainService` `catch (RuntimeException e)` 后只 `metrics.observeDbError(...) + throw e`，**没有把 RuntimeException 转 `DatabaseException`**。`DatabaseException` 类是否需要保留 —— UNKNOWN |
| `message-rpc` 的 `MessageProperties` 是否要扩 `Auth` 段 | 当前 message-rpc 不签发 JWT（auth 是 api 侧的事）；但未来如果加 webhook / 第三方回调 token 可能需要。**未拍板** |
| `Conversation` / `Notification` 是否需要 `createdByRole` / `createdById` 软删 | 当前表没有 deleted_at，但 Go 端 `Conversation.CreatedByRole/ID` 已记录创建者；将来 audit 需要时未必保留。**未拍板** |
| `MessageRpcServiceImpl.invoke` 是否需要在 catch (StatusRuntimeException) 时记录 metric | Go 端 `server/messageserviceserver.go` 没有任何业务指标埋点；Java `invoke` 当前只 catch + forward，**未额外加 metric**。如要监测 invoke 自身失败率，引入。注意：失败率已由 `metrics.observeRpc(method, started, err)` 在 DomainService 层埋点 → 不要重复埋 |
| `message-rpc` 的 9 个 RPC 是否需要 gRPC deadline | Go 端没有显式 deadline 设置；Java 端 gRPC 默认 0 = 无超时。是否引入 deadline —— UNKNOWN |
