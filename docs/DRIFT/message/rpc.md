<!-- @move AGENTS.md L72-L101 · §0 message-rpc 3 项 P0 + 3 项新挖出缺陷 -->
### 🟡 `service/message/message-rpc`：3 项 P0 **已修**，并**新挖出 3 项「模块根本起不来」级缺陷**（2026-10-22）

> 首轮审计见 **§6.6**。审计当天即补测试 + 修复，**3 项 P0 已全部关闭（真 PG +变异测试双重验证）**，
> 但补测试的过程**又挖出 3 项此前从未被发现的缺陷 —— message-rpc 这个模块此前根本启动不了**。

| # | 缺陷 | 后果 | 状态 |
|---|---|---|---|
| **DRIFT-MSG-001** | 6 个 `@Transactional` 因 **self-invocation** 静默失效 | 事务形同虚设，新建会话失败会**残留孤儿会话行** | ✅ 已修（抽 `MessageAtomicService` 独立 Bean） |
| **DRIFT-MSG-002** | `message_conversation_message.conversation_id` **恒为 0** | 消息全写 `conversation_id=0` ⇒ **查不出历史消息、unread_count 恒 0** | ✅ 已修（两条写路径都在事务内 `setConversationId`） |
| **DRIFT-MSG-003** | `ServerInterceptor` 未挂到 server（§6.5 的 H2 原样复现） | `observability.rpc-timeout: 2s` **从未生效** | ✅ 已修（`@Import` + `GrpcServerConfigurer`） |
| **DRIFT-MSG-004** 🆕 | `NacosRegistration` 裸 `@Component`，Bean 名与 `NacosServiceRegistryAutoConfiguration` **撞车** | **上下文直接起不来**（Bean 覆盖默认禁用） | ✅ 已修（显式名 `messageRpcNacosRegistration`） |
| **DRIFT-MSG-005** 🆕 | 缺 `@MapperScan`，3 个 Mapper **注册不上** | **上下文直接起不来** | ✅ 已修 |
| **DRIFT-MSG-006** 🆕 | 缺 `spring-boot-flyway`（Boot 4 拆模块后必需），迁移脚本**从不执行** | 启动无任何 Flyway 日志、**表永不创建**，第一次打 SQL 才炸 | ✅ 已修 |

**⇒ 教训比修复更重要**：`message-rpc` 有 **21 条测试且全绿**，却**一次都没真正启动过**。
9 条旧测试全是纯 Mockito 单测（不装配上下文），`tools/difftest` 也从未纳入它
⇒ 「上下文能不能起」「迁移跑没跑」这两件事**从未被执行过一次**。
**这与 §6.6.5 记录的方法论完全吻合：缺失的不是测试数量，而是「让代码真正跑一次」这个动作。**

新增 **11 条**测试（`MessageTransactionWiringTest` 8 条真 PG + `MessageGrpcInterceptorWiringTest` 3 条），
`message-rpc` 测试数 **9 → 21**。两个变异测试实测证明新测试不是假绿（详见 §6.6.5）。
message 域同时已纳入 `tools/difftest` 双端口对拍（§6.6.6）。

**下一步建议**：先清 DDL 剩余 12 处（`check-ddl-static.py` 已就位，改完可立即验证），
再按模块逐个进 phase 补那 10 个未验证子模块。
⚠️ 其中 `users_user.extra_info` 的 `text→jsonb` **必须单独取证** —— 它涉及 Go 侧 model
到底是 `string` 还是自定义类型，不能照「RAG 用了 jsonb 所以 Go 也该用」来猜。
⚠️ **每个模块接手的第一件事应该是「先让它真的启动一次」**（最小 `@SpringBootTest` + 真容器），
而不是先写业务单测 —— message-rpc 证明了「21 条全绿」与「一次没起过」可以并存。

<!-- @move AGENTS.md L2482-L2522 · §6.2.4② NotificationEntity.isRead 运行期致命（实为 message-rpc） -->
#### ② `NotificationEntity.isRead` → 运行期致命（本轮唯一 P0，附带发现）

修 `markNotificationRead` 时为写「断言打在真实 SQL 上」的测试（§3.3），
用 `TableInfoHelper.initTableInfo` 初始化 MP 元数据后撞出来的：

```
### keySet=[content->content, ..., isRead->isRead, readAt->readAt, ...]
### NotificationEntity::isRead  →  MybatisPlusException:
        can not find lambda cache for this property [read] of entity [...NotificationEntity]
### NotificationEntity::getReadAt →  OK
```

**根因**：MP 的 `LambdaXxxWrapper` 解析 `Entity::method` 时按 JavaBean 约定剥掉
`get`/`is` 前缀再拿**属性名**查 `TableInfo.propertyMap`。
字段叫 `isRead` ⇒ 属性名 `isRead`、getter `isRead()` ⇒ lambda 解析成属性 `read` ⇒ 落空。

**影响面**：`NotificationMapper` 的 `countUnread` / `markAllReadByRecipient` / `markRead`
三处都用 `NotificationEntity::isRead` 构造条件 ⇒
**`listNotifications(unreadOnly)` / `getUnreadSummary` / `markAllNotificationsRead` /
`markNotificationRead` 四条业务路径一调即抛异常**。

**为什么长期没暴露**：message-rpc **此前没有任何一条会真正执行 SQL 的测试**，
全部是纯 Mockito mock —— mock 数据层不装配 MP 元数据，天然测不到这条（§3.3）。

**修法**：字段改名 `isRead` → `read` + `@TableField("is_read")` 显式列映射，
getter 仍为 `isRead()`。实测改后：

```
### keySet=[read->is_read, readAt->read_at, recipientId->recipient_id]
### E::isRead → (is_read = ? AND recipient_id = ?)   ✅
```

**新增防线**：`MessageMarkNotificationReadTest#entityFieldMustBeNamedReadNotIsRead`
用反射锁死字段名 + `@TableField` 值（静态引用在编译期看不出问题，只有真正解析 lambda 时才炸）。

> **这条的教训**：`@TableField` / Lambda wrapper 属于 §3.3 点名的「运行期动态装配、不报错」
> 那一类，但它比 `@Transactional` 更隐蔽 —— `@Transactional` 失效是**静默降级**，
> 而 lambda 解析失败是**立即抛异常**。也就是说：一个模块只要从来没跑过真实 SQL，
> 这类问题就<b>完全不会出现在任何测试报告里</b>。判据不是「有没有报错」，
> 而是「<b>有没有真的执行过这条 SQL</b>」。

<!-- @move AGENTS.md L3224-L3541 · §6.6 message-rpc 审计 + 修复 -->
### 6.6 `service/message/message-rpc` 语义一致性审计 + 修复（2026-10-22）

> **本节只覆盖 `message-rpc` 一个子模块**；`message-api` 的首轮实现 + 语义验证 + 修复记录在 **§0**
> （不在本节，避免两处各写一半）。与 §6.1–§6.3（`service/users`）、§6.5（`service/common`）**互不覆盖、无重叠**。

**审计方式**：Go ↔ RAG 逐行对照静态审计 + proto 契约逐字节比对。Go 侧通读 15 个源文件
（9 个 `*Logic.go` + `helper.go` + `message.go` + `data_model.go` + `db.go` + `config.go` +
`servicecontext.go` + `messageserviceserver.go` + `messageservice.go`），Java 侧通读 18 个源文件
（`MessageDomainService` / `MessageRpcSupport` / 3 个 Mapper / 3 个 Entity / 2 个 RpcClient /
`MessageProperties` / `MessageMetrics` / `NacosRegistration` / `MessageRpcServiceImpl` +
3 份 Flyway 迁移 + `application.yml` + `message.proto`）。

**审计结论：`SEMANTICALLY NOT EQUIVALENT`** —— 首轮查出 **3 项 P0 语义不一致，其中 2 项使聊天功能整体不可用**。

**修复结论（2026-10-22 当日完成）**：3 项 P0 **全部关闭**（真 PG + 变异测试双重验证）；
补测试的过程**又挖出 3 项首轮静态审计没抓到的缺陷 —— message-rpc 这个模块此前根本启动不了**。
合计 **6 项缺陷已修**，测试数 **9 → 21**，message 域已纳入 `tools/difftest` 双端口对拍。

> ⚠️ 下文 §6.6.1 保留的是**审计当时**的缺陷形态原文（不删改），
> 目的是让后人看到「注释与配置看起来完全对齐、实际机制一个都没生效」的具体样子；
> 修法与防回退用例见 §6.6.1 顶部的状态表。

---

#### 6.6.1 三项 P0（功能级失效）—— **2026-10-22 已全部关闭**

> ⚠️ 本小节记的是**审计当时**的缺陷形态，保留原文是为了让后人看到「注释与配置看起来完全对齐、
> 实际机制一个都没生效」的具体样子。**当前状态与修法见下面的状态表。**

| # | 缺陷 | 修法 | 防回退用例 | 变异测试 |
|---|---|---|---|---|
| **DRIFT-MSG-001** | 6 个 `@Transactional` self-invocation 失效 | 新增独立 Bean `MessageAtomicService`，6 个方法全部搬过去；`MessageDomainService` 跨 Bean 调用 | `rollbackConversationWhenMessageInsertFails`（真 PG）+ `domainServiceMustNotDeclareTransactionalMethods` / `atomicServiceMustOwnTheTransactionalMethods`（结构断言，不依赖 Docker） | ✅ 摘掉 `@Transactional` ⇒ `expected: <0> but was: <1>`（孤儿会话行真的残留） |
| **DRIFT-MSG-002** | `conversation_id` 恒为 0 | 两条写路径都在**事务内** `msg.setConversationId(conv.getId())`，与 Go `message.go:186`/`:196` 的闭包内赋值逐行对齐 | `conversationIdMustBePropagatedToResponseAndRow`、`firstMessageMustBeFindableByConversationId`、`appendedMessageMustAlsoCarryConversationId` | ✅ 删掉 `createOrSendMessageTx` 的赋值 ⇒ `expected: <...883> but was: <0>` |
| **DRIFT-MSG-003** | 拦截器未挂到 server | `@Import({RpcServerInterceptorFactory, ObservabilityProperties, ObservabilityLifecycle, …})` + `GrpcServerConfigurer` Bean | `MessageGrpcInterceptorWiringTest` 3 条 | — |

##### ⚠️⚠️ 由补测试**新挖出**的 3 项：message-rpc 这个模块此前**根本启动不了**（2026-10-22）

这三项**不在首轮静态审计的台账里** —— 它们是「把上下文真正装配起来」时才暴露的。
**这也是本轮最重要的产出**：静态逐行审计 + 21 条全绿的纯单测，都没能发现它们。

| # | 缺陷 | 触发条件与证据 | 修法 |
|---|---|---|---|
| **DRIFT-MSG-004** | `NacosRegistration` 用裸 `@Component`，默认 Bean 名 `nacosRegistration` 与 `spring-cloud-starter-alibaba-nacos-discovery` 的 `NacosServiceRegistryAutoConfiguration` **同名撞车** | 上下文启动即失败：<br>`The bean 'nacosRegistration', defined in ... NacosServiceRegistryAutoConfiguration.class, could not be registered. A bean with that name has already been defined in .../NacosRegistration.class and overriding is disabled.`<br>（Spring Boot 2.1+ 默认 `allow-bean-definition-overriding=false`） | `@Component("messageRpcNacosRegistration")`。**另两个模块早就这么写了**（`userRpcNacosRegistration` / `adminRpcNacosRegistration`），只有 message-rpc 漏了 |
| **DRIFT-MSG-005** | 缺 `@MapperScan`，3 个 Mapper 注册不上 | `No qualifying bean of type '…mapper.NotificationMapper' available` ⇒ `UnsatisfiedDependencyException: messageAtomicService`。**同款问题 `UserRpcApplication` 已踩过**，其 `@MapperScan` 注释写着「代码一直没加 → Mapper 注册不上」 | `@MapperScan("com.rag.message.message.rpc.mapper")` |
| **DRIFT-MSG-006** | 缺 `spring-boot-flyway`，Flyway 自动配置**从未加载** | **启动日志里连一行 Flyway 都没有**（不是报错，是根本没被加载）⇒ 三张 `message_*` 表永不创建 ⇒ 第一次打 SQL 才炸：`relation "message_conversation_message" does not exist`。Spring Boot 4 把自动配置拆成独立模块，`flyway-core` 只提供引擎、不提供自动配置 | 补 `org.springframework.boot:spring-boot-flyway`（版本由 `spring-boot-starter-parent` 管理）。修复后实测：`Successfully applied 3 migrations to schema "public", now at version v3` |

##### ⚠️ DRIFT-MSG-001：6 个 `@Transactional` 因 self-invocation 全部静默失效

**分类：`SEMANTIC MISMATCH`　业务可接受性：`UNACCEPTABLE`　风险：HIGH**

Go 侧是**显式**事务：

```go
// service/message/rpc/internal/model/message.go:186
func (m *MessageModel) CreateConversationWithMessageTx(...) error {
    return m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
        if err := tx.Create(conversation).Error; err != nil { return err }
        message.ConversationID = conversation.ID
        return tx.Create(message).Error
    })
}
```

Java 侧 6 个 `@Transactional` 方法全部挂在 `MessageDomainService` 本类上，且**全部由同类私有方法
`doXxx` 通过 `this.` 调用**：

```
MessageRpcServiceImpl.invoke(→ domain.sendChatMessage(req))     ← 走代理，但该方法本身无 @Transactional
    └→ doSendChatMessage(req)                                    ← 目标对象内部
         └→ this.createConversationWithMessageTx(conv, msg)     ← 不经代理 ⇒ @Transactional 静默失效
              ├→ conversationMapper.insertConv(conv)   ← 独立 auto-commit
              └→ messageMapper.insertMsg(msg)         ← 独立 auto-commit
```

- **证据** `[CODE]`：全仓 `exposeProxy` / `AopContext` / `EnableAspectJAutoProxy(exposeProxy=true)`
  **零命中**（`grep -rn` 覆盖 `service/**/*.java` + `*.yml`）。
  受影响方法 6 个：`createConversationWithMessageTx:385` / `createOrSendMessageTx:399` /
  `insertNotificationsTx:509` / `markConversationReadTx:759` / `markNotificationReadTx:901` /
  `markAllNotificationsReadTx:953`。
- **本仓库已有同款教训** `[CODE]`：`service/users/admin/admin-rpc` 的 `AdminCreateAtomicService`
  **正是为修这个问题而抽出的独立 Bean**，并留有防回退断言
  （`AdminUpdateSelfAndCreateAdminRegressionTest:169`：「4 步事务必须独立成 Bean，否则同类自调用会让
  `@Transactional` 失效」）。`message-rpc` **没有做**这件事，直接复现了 AGENTS.md §1.1 列出的 11 处回归中的第一处
  （`CreateAdmin` 事务 self-invocation 失效）。
- **可观察影响**：
  1. 新建会话时若 `insertMsg` 失败 → Go 全部回滚，**Java 残留孤儿会话行**且对调用方报 INTERNAL；
  2. `createOrSendMessageTx` 若 update 失败 → 已插入的 message 行残留；
  3. **并发同对用户首发**：两边都有 `uk_conversation_pair` 唯一索引，但只有 Go 的唯一约束冲突会
     连带回滚整个事务；Java 侧第一个 INSERT 成功、第二个冲突时 `@Transactional` 已失效 ⇒ 会话行无法回滚。

##### 🔴 DRIFT-MSG-002：`message_conversation_message.conversation_id` 恒为 0

**分类：`SEMANTIC MISMATCH`　业务可接受性：`UNACCEPTABLE`　风险：HIGH**

```
Go:  CreateConversationWithMessageTx:  message.ConversationID = conversation.ID   ← tx 内设置
     CreateOrSendMessageTx:          message.ConversationID = conversation.ID   ← tx 内设置
Java: ConversationMessageEntity.conversationId 是 primitive long（未设置 ⇒ 0）
      insertMsg 的 VALUES 绑 #{conversationId}
      doSendChatMessage 构造 messageItem 时只设了 id/senderId/senderRole/recipientId/
      recipientRole/content/read/createdAt —— 唯独漏了 conversationId
```

- **证据** `[CODE]`：`grep -rn "setConversationId" message-rpc/src/main/java/` 共 5 处命中，
  **全部是 protobuf builder**（`SendChatMessageResp.newBuilder()` × 3、`MessageRpcSupport.buildConversationItem`
  × 1、`toMessageItem` 读 getter × 1），**实体 setter 零调用**。
- **可观察影响**：所有消息写成 `conversation_id = 0`，于是
  - `GetConversationMessages(conv)` 按 conversation_id 过滤 → **永远返回空列表**
  - `CountUnreadMessagesByConversation` → 恒 0 ⇒ 会话列表 `unread_count` 恒 0
  - `MarkConversationRead` → 更新 0 行
  - 响应中 `message.conversation_id` 也返回 0
- **为何长期未暴露** `[CODE]`：`message-rpc` 现有测试仅 2 个类 9 条，**全部是纯 Mockito 单测**，
  不装配 Spring 上下文、不执行 SQL（§3.3 纪律）。9 个 RPC 端到端路径**零覆盖**。

##### 🔴 DRIFT-MSG-003：gRPC 2000ms 超时从未生效（`ServerInterceptor` 未挂到 server）

**分类：`SEMANTIC MISMATCH`　业务可接受性：`UNACCEPTABLE`　风险：HIGH**

```
Go:  zrpc RpcServerConf.Timeout 未配 → go-zero 默认 2000ms（zrpc/config.go:46）→ 服务端强制 deadline
Java: application.yml:130  observability.rpc-timeout: 2s          ← 配了
      → ObservabilityProperties（common/spring，@Component + @ConfigurationProperties("observability")）  ← 绑得上
      → RpcServerInterceptorFactory 产出 ServerInterceptor Bean
         （因 MessageRpcApplication 的 scanBasePackages 含 "com.rag.common"，该 Bean 大概率存在）
      → ✗ devh grpc-server-spring-boot-starter 3.1.0.RELEASE 从不自动注册 ServerInterceptor Bean
      → ✗ message-rpc 全模块零处 GrpcServerConfigurer / builder.intercept(...)
      ⇒ 拦截器从未进入 ServerBuilder，deadline / span / DeadlineState 全部永不执行
```

- **证据** `[CODE]`：`grep -rn "GrpcServerConfigurer\|ServerInterceptor\|intercept"` 在
  `message-rpc/src/main/java/` **零命中**。对照 `UserRpcApplication:104` 有
  `@Bean rpcInterceptorConfigurer(...)`，且 `GrpcInterceptorWiringTest` 用
  `verify(builder).intercept(same(interceptor))` 锁死。
- **本条就是 §6.5「H2 勘误小节」在 message-rpc 上的原样复现** —— 该小节已明确记载
  「装配对了 ≠ 机制接上了」，且唯一注册入口是 `GrpcServerConfigurer`。
  **message-rpc 既没有 `GrpcServerConfigurer`，也没有任何等价 wiring 测试。**
- **附带失效**：`DeadlineState` 永不绑定 ⇒ 任何依赖它的超时守卫与
  `setQueryTimeout` 取消在途 SQL 均无效 ⇒ 慢查询无上界。
- ⚠️ **本条属 §3.3 新增纪律「断言 Bean 存在 ≠ 断言机制生效」的第二个实例**。
  第一个实例是 §6.5 的 H2（`ObservabilityWiringTest#grpcInterceptorBeanExists` 结构性假绿）。

---

#### 6.6.2 P1 / P2 台账

| # | 维度 | Go | Java | 分类 | 业务可接受性 |
|---|---|---|---|---|---|
| **DRIFT-MSG-004** | `listNotifications(unread_only=true)` 的 `total` | `ListNotifications` 用已含 `unread_only` 过滤的 query 做 `Count(&total)` ⇒ **未读数** | `selectCount(QueryWrapper.eq("recipient_id", uid))` ⇒ **漏了 unread_only** = 全部数 | `SEMANTIC MISMATCH` | `UNACCEPTABLE` |
| **DRIFT-MSG-005** | 数据库错误的错误身份 | 裸 GORM error → grpc `Unknown` → 客户端 `BizCodeFromError` 兜底 ⇒ **1015** | 读路径裸 RuntimeException → `invoke` 兜底 `INTERNAL`+"internal server error" ⇒ **5001**；三个 mark-* 写路径显式包 `INTERNAL`+"database update failed" ⇒ **5008** | `SEMANTIC MISMATCH` | `UNKNOWN`（无契约规定应对外报哪个码） |
| **DRIFT-MSG-006** | `markConversationRead` 副作用列 | 只 UPDATE message 表 | 额外 `conversationMapper.touchUpdatedAt(conversationId, readAt)` | `OBSERVABLE DIFFERENCE` | `UNKNOWN` |
| **DRIFT-MSG-007** | `trimContent` 空白集 | `strings.TrimSpace`（Unicode，含 U+00A0/U+3000） | `String.trim()`（仅 ≤ U+0020） | `SEMANTIC MISMATCH`（窄） | `UNKNOWN` |
| **DRIFT-MSG-008** | `ListConversations` 排序 | `Order("last_message_at desc").Order("updated_at desc")` | 仅 `orderByDesc(getLastMessageAt)` | `OBSERVABLE DIFFERENCE` | `UNKNOWN` |
| **DRIFT-MSG-009** | `GetConversationMessages` 排序键 | `created_at ASC` | `id ASC`（雪花单调 ⇒ 实践等价，非严格等价） | `OBSERVABLE DIFFERENCE` | `ACCEPTABLE` |
| **DRIFT-MSG-010** | 时间戳 epoch | DSN 钉 `TimeZone=Asia/Shanghai` + GORM→`timestamptz`（存绝对时刻） | Flyway DDL `TIMESTAMP`（无时区）+ `LocalDateTime` + `toEpochSecond(ZoneOffset.UTC)` | `OBSERVABLE DIFFERENCE` | **`UNKNOWN`**（取决于 JVM 运行时区，见 UNRESOLVED-MSG-001） |
| **DRIFT-MSG-011** | 广播批量插入分片 | `CreateInBatches(items, 200)` 会按 200 分片 | 单条 `INSERT ... VALUES (...),(...)` 全量拼接 | `OBSERVABLE DIFFERENCE`（容量边界） | `UNKNOWN` |
| **DRIFT-MSG-012** | 部署面 | 无 servlet 容器 | `pom.xml` 引 `spring-boot-web-server`，与 `NacosRegistration` javadoc 自述「本服务不带 web」矛盾 | `OBSERVABLE DIFFERENCE` | `UNKNOWN` |

**DRIFT-MSG-007 值得展开**（它是 DRIFT-011 的同类形态但影响的是**校验分支**而非展示）：

```
content = "\u00A0"（NBSP）
Go:  TrimSpace → ""            → content == "" → InvalidArgument(5003)，不写库
Java: String.trim() → "\u00A0"  → 非空 → 通过校验 → 写入一条 content=NBSP 的消息
      且 runeLen("\u00A0") = 1，参与「首条 ≤ 50 rune」判定
```

> `MessageRpcSupport.trimPreview` 的 javadoc 已声明「Java trim 只去 ≤ ' '，是有意保留的表达层差异」，
> 但 **`trimContent` 没有同等声明，且它喂的是 `content.isEmpty()` 这个校验分支** —— 后果比 preview 严重。

---

#### 6.6.3 已确认等价的高价值项（勿重复核查）

以下维度经逐行对照**确认无漂移**，记录在此以免下一轮重复核查：

| 维度 | 证据 |
|---|---|
| **proto 契约逐字节一致** | 两份 `message.proto` 除 `option go_package` / `java_package`+`java_multiple_files` 外**完全相同**：19 个 message、3 个枚举（`NotificationKind` / `SenderRole` / `ConversationStatus`）、9 个 service 方法的字段号与类型全同 |
| **错误码与文案表** | 18 个业务码（200/500/1015/5001-5008/5101-5102/5201-5207）+ 22 条文案 1:1；`msg→code` 反查表排除 200/500 的逻辑一致；未知码 fallback `"FAIL"` 一致 |
| **9 个 RPC 的参数校验分支** | `userId>0` / `operatorId>0 && conversationId>0 && validRole` / `senderId>0 && content!="" && validRole` 等逐条一致 |
| **`normalizeLimit` 与配置值** | `≤0→default`、`maxLimit>0` 截断；配置 `default 20 / max 100 / pageSize 200` 与 Go `etc/message.yaml` 一致 |
| **四个会话权限函数** | `canonicalParticipants` / `isParticipant` / `peerOf` / `canSend` 逐条一致，**含 `PENDING` 时排除 pending_sender 本方这条易漏规则** |
| **`runeLen`** | `codePointCount(0,len)` ↔ `len([]rune(s))`，等价 |
| **`trimPreview`** | 上限 **80 rune**（非 255）且按码点边界切（`offsetByCodePoints`），不产生孤立代理对；5 条测试锁死。**本模块做得最好的对齐点** |
| **`markNotificationRead`** | 已修成与 Go 同构的单条 `UPDATE ... WHERE id=? AND recipient_id=?` + `RowsAffected==0 → NotFound`；有 `ArgumentCaptor` 抓真实 SQL 片段的测试（符合 §3.3） |
| **会话状态机** | `PENDING→OPEN` / pending_sender 连发拦截 `FailedPrecondition(5204)` / `UNSPECIFIED→OPEN` |
| **`createOrSendMessageTx` 更新字段** | 7 个字段（status / pending_sender_role / pending_sender_id / last_message_id / last_message_preview / last_message_at / updated_at）齐全 |
| **雪花 ID** | `EPOCH/NodeBits/StepBits` 位布局、`%1024` 模序等价性、**时钟回拨不保护**这一 bwmarrin v0.3.0 原生性质均如实复刻 |
| **连接池** | Hikari `maximum-pool-size:100` / `minimum-idle:10` ↔ Go `SetMaxOpenConns(100)` / `SetMaxIdleConns(10)` |
| **指标** | 5 个指标名（`message_rpc_*`）、标签维度、`result=success/fail` 语义一致 |
| **`loadProfile` 兜底** | `"User {id}"` / `"Admin {id}"` / `"Unknown {id}"` 三态一致 |
| **表名** | 三张表 `message_notification` / `message_conversation` / `message_conversation_message` 两端**同名**，未发生 §3.4 表名偏离 |

---

#### 6.6.4 证据边界台账（message-rpc，**2026-10-22 补测试后更新**）

| 项 | 审计时（2026-10-22 上午） | **现在** |
|---|---|---|
| 测试总量 | 9 条 / 2 个类 | **21 条 / 4 个类** |
| 纯 Mockito 单测 | 9 条 | 9 条（保留） |
| **真 PG 集成测试** | **0** | **8 条**（`MessageTransactionWiringTest`，Testcontainers + `postgres:16-alpine`） |
| **wiring 测试** | **0** | **3 条**（`MessageGrpcInterceptorWiringTest`） |
| 端到端对拍 | 未纳入 `tools/difftest` | **已纳入**（§6.6.6） |
| P0 的证据类型 | `[CODE]` 静态分析 + 仓库内同款教训佐证 | **`[RUNTIME]` 真 PG 实测 + 变异测试双向验证** |

**仍然存在的证据缺口（勿当成已验证）**：

| # | 缺口 | 影响 |
|---|---|---|
| **UNRESOLVED-MSG-001** | JVM 运行时区未知 | DRIFT-MSG-010 时间戳 epoch 是否整体偏移仍未定级。查部署环境 `TZ` / `-Duser.timezone`，或两端各发一次请求比对 `created_at` |
| **UNRESOLVED-MSG-002** | `ServerInterceptor` 挂载只有 `verify(builder).intercept(...)` 这层证据 | 「starter 会在 build 阶段 apply 所有 Configurer」属 starter 自身契约。端到端层面由 §6.6.6 的双端口对拍 + `exp-b1-timeout-cancel.sh` 兜底 |
| **UNRESOLVED-MSG-003** | 双端口对拍的 12 档**尚未实际执行过** | 脚本已就位且 `javac` 通过、`bash -n` 通过，但**未跑过完整两套栈**（需 `run.sh --keep --serve-only` 起栈）。首次执行前不要把它当作已验证 |
| **UNRESOLVED-MSG-004** | DRIFT-MSG-004/005/006 未各配独立防回退用例 | 它们目前只被 `MessageTransactionWiringTest` 的「上下文能起来」间接锁住。若有人删掉 `@MapperScan` / `spring-boot-flyway` / Bean 名，测试会红，但**红的原因不是断言点名了它**，而是连带失败 —— 建议后续拆出 `MessageContextWiringTest` 逐条点名 |
| **UNRESOLVED-MSG-005** | `AutoMigrate`（Go 每次启动校验表结构）vs `Flyway`（Java 一次性迁移后不再校验）的存量表漂移 | 需确认是否存在已存在的非 Flyway 建表的库 |

---

#### 6.6.5 ⚠️ 本轮的方法论产出（比发现本身更重要）

三项 P0 **全部位于「注释写得很完整、代码看起来已对齐、实际机制未生效」的位置**：

| 位置 | 注释/配置声称 | 实际 |
|---|---|---|
| `createConversationWithMessageTx:385` | 顶部类注释白纸黑字列出 6 个事务方法「放本类上 `@Transactional`」 | 全部 self-invocation，注解形同虚设 |
| `application.yml:130` | `observability.rpc-timeout: 2s` + 三行注释解释「取 2s 对齐 go-zero 默认」 | 拦截器从未挂上，超时从未执行 |
| `doSendChatMessage:243-251` | `messageItem` 逐字段 setter 齐全，注释标注每个字段对应的 Go gorm tag | 独缺 `conversationId`，而代码里另有 3 处 `setConversationId` 在 builder 上，肉眼扫过会误判为已覆盖 |

> **这三条印证并扩展了 §3.3 的现有纪律**：本项目已知的静默失效有四类
> （`@Transactional` self-invocation / `@ResponseBodyAdvice` 二次包裹 / MP `typeHandler` 丢字段类型 /
> 命令行属性前缀写错），**现在可确认还要加上第五类**：
> **实体必填字段在构造点漏赋值，而同一字段名在别处以 builder 形式出现，掩盖了缺口。**
>
> 与前三类的区别：前四类都是「注解/配置写了但没生效」，**这一类是「普通代码少写了一行」**，
> 按理说最容易被发现 —— 但它恰好躲过了本模块唯一的那类测试（纯 Mockito 单测不执行 SQL，
> 看不出 `conversation_id` 写成 0）。
>
> ⇒ **推论**：§3.3「必须配一条能真正触发该机制的测试」这条纪律，其适用范围应从
> 「框架机制」扩展到**「任何跨进程边界的字段赋值」**。判断标准不是「有没有注解」，
> 而是「这条测试如果为真，能不能观察到该字段在下游的真实取值」。

##### ⚠️⚠️ 元教训二（2026-10-22 修复阶段，比元教训一更值钱）：**变异测试抓出了「我自己的新测试也是假绿」**

修复阶段按 §3.3 的要求做了变异测试，方式是**把刚修好的代码改回缺陷态，确认新测试变红**。
第一刀就翻车了：

```
变异：删掉 createOrSendMessageTx 里的 msg.setConversationId(conv.getId())
结果：8 条用例全部照常通过 ⇒ 该分支的 conversation_id 缺陷本来漏网
```

原因是新写的 7 条用例**全部只走「会话不存在 → 新建」这条 `createConversationWithMessageTx` 路径**，
`createOrSendMessageTx`（已有会话追加消息）**一条都没覆盖**。
补上 `appendedMessageMustAlsoCarryConversationId`（对端回复才能走到该分支）后，
同一刀变异立刻变红：`expected: <2113123504948088833> but was: <0>`。

第二刀（摘掉 `@Transactional`）同样被抓住，而且是**两道网同时变红**：
真 PG 语义测试报 `expected: <0> but was: <1>`（孤儿会话行真的残留），
结构断言报 `expected: [6 个方法] but was: [5 个方法]`。

> **这一条比「发现 3 项 P0」本身更重要**，因为它说的是：
> **「我刚补的测试覆盖了我以为覆盖的路径」这件事，会真实发生，且默认发生。**
>
> 具体机制值得记下来：一个模块的写路径有 N 条（新建 / 追加 / 批量 / 更新），
> 而测试往往只挑「最容易构造输入」的那条（此处是「发一条新消息」），
> 于是**恰好最需要验证的那条路径最容易被跳过** —— 因为它需要多一次调用才能到达
> （新建后再让对端回一条）。
>
> ⇒ **新增纪律**：写事务 / 状态机相关的测试时，
> **「变异测试」不是可选的收尾，而是必须项**；且变异点要覆盖
> **每一条独立的写路径**，而不只是「刚改的那一行」。
> 判据：把每条写路径分别破坏一次，测试必须**每条都红**。
> 只红一条 ⇒ 其余路径仍是假绿。
>
> 与 §3.3「断言 Bean 存在 ≠ 断言机制生效」并列，两者合起来是同一条元规则：
> **任何测试都必须先证明「它在缺陷存在时会红」，再相信它的绿。**

#### 6.6.6 message 域纳入 `tools/difftest`（2026-10-22）

**为什么必须单开一套**：既有 `diff.py` 是 **HTTP wire 层**对拍，走 api 入口。
`message-rpc` 是**纯 gRPC、没有 HTTP 面** ⇒ 现有 harness 完全覆盖不到它。
沿用 user 域 `grpc-dual-port-runner.java` 的范式：**一个 Java 进程、两个 ManagedChannel、
同一 stub 双端口打**，比对 `Status.getCode()` 与 happy path 响应体。

| 文件 | 作用 |
|---|---|
| `tools/difftest/conf/go/message-rpc.yaml` | Go 真源 yaml 的**副本**（Etcd 整块移除、`Password` 写字面值、下游改 `Endpoints` 直连、端口 18082） |
| `tools/difftest/grpc-message-dual-port-runner.java` | 12 档断言：11 档 status code + 1 组 happy path 响应体 |
| `tools/difftest/exp-difftest-grpc-errors-message.sh` | 编排：起栈 → 编译 → 跑 → 存结果 |
| `tools/difftest/run.sh` | **增量**接入：构建/启动两端 message-rpc + 6 条 `assert_tables`；**users 域逻辑一字未改** |

**12 档的 expected 全部逐条核对自冻结的 Go 真源**（每档在 runner 注释里标了 `file:line`）：
INVALID_ARGUMENT ×6（`listNotifications` / `getUnreadSummary` / `markNotificationRead` /
`markAllNotificationsRead` / `sendChatMessage` 空 content / 首条 51 rune）、
NOT_FOUND ×2、`PERMISSION_DENIED` ×1（`USER → ADMIN` 且无会话 ⇒ `ErrorAdminOnlyInit`）、
自聊 `ErrorSelfChat` 计入 INVALID_ARGUMENT，
外加 happy path 比对 `conversation_id` / `message.conversation_id` / `status` / `can_send`。

**端口与库**（两侧对称，避开本机占用）：

| | Go | RAG |
|---|---|---|
| message-rpc | **18082** | **58210** |
| 库 | `go_db`（GORM `AutoMigrate` 建表） | `rag_db`（Flyway 建表） |
| 下游寻址 | `Endpoints: ["127.0.0.1:19004"]` / `["127.0.0.1:18081"]` | `--grpc.client.*.address=static://127.0.0.1:…` |

> ⚠️ **`UNRESOLVED-MSG-003`：这套 harness 目前只验证到「能编译、语法正确」，尚未实际跑过完整两套栈。**
> 首次执行前不要把它计入「已验证」。执行方式：
> ```
> ./tools/difftest/exp-difftest-grpc-errors-message.sh          # 自动起栈
> ./tools/difftest/exp-difftest-grpc-errors-message.sh --stacks-up
> ```
> ⚠️ 另：Go 的 `NewServiceContext` 里 `zrpc.MustNewClient(c.UserRpc)` 默认 `NonBlock=false`
> ⇒ 走 `grpc.WithBlock()`，**下游没监听会一直阻塞拨号**。
> 所以 `run.sh` 里 message-rpc 必须排在 user-rpc / admin-rpc 之后启动（已加 `sleep`）。

---

