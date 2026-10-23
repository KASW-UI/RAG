# `service/message/message-rpc` 组件级语义对照台账

> **本文件的两段结构**：本表头之后、`# 第三轮 · 组件级语义一致性验证（2026-10-23）` 之前，
> 是从 `AGENTS.md` 搬来的**历史留档**（§0 速查 + §6.6 首轮审计），**一字未删**（2026-10-23 增量合并时 `git diff --numstat` = `506 0`，零删除）；
> 最后那一节是本轮新增，按验证方法论 spec §31-b 组织。

**本轮（第三轮）元信息**

| 项 | 值 |
|---|---|
| 核对日期 | **2026-10-23** |
| 组件 | `service/message/message-rpc`（23 个组件之一，见 AGENTS.md §3.5） |
| Go 侧 | `Sea-RideTheWind_golang/service/message/rpc`（通读 15 个源文件，排除 `pb/*.pb.go`） |
| RAG 侧 | `RAG/service/message/message-rpc`（通读 22 个 main 源文件 + pom + 3 份 Flyway 迁移 + `application.yml` + imports 文件） |
| 编号命名空间 | 本轮新增 **`DRIFT-MSG-V-014` 起**。`DRIFT-MSG-001..012`（§6.6）与 `DRIFT-MSG-V-001..013`（`_cross/message-domain-wide.md` §6.7）**均已被占用，本轮一条都不复用** |
| 最终判定 | **`SEMANTICALLY NOT EQUIVALENT`** —— 新增 1 项 SEMANTIC MISMATCH + 5 项 OBSERVABLE DIFFERENCE（见 §7 与 §10） |
| 证据强度（本轮自跑） | ✅ **真 PG（Testcontainers `postgres:16-alpine`，本轮 02:03 隔离跑，EXIT=0，39/39 绿）**；✅ **真 PG 独立探针**（裸 `psql` 验证 `OFFSET -1`）；⚠️ **双侧活体对拍 = 仍缺**（`tools/difftest` 的 message runner 从未产出结果，见 `UNRESOLVED-MSG-003`） |
| 明确不覆盖 | `message-api` / `message-common` / `message-proto` 属同域其它组件；本轮只把 common 的错误码表与 proto 契约**当公理**核对，未重新审计 |

---

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


# 第三轮 · 组件级语义一致性验证（2026-10-23）

> **方法**：`semantic-verification-prompt.md` 全部 40 章节 + 本项目 8 条硬约束。
> **纪律**：Go 是唯一真源；每个 Go 事实带 `文件:行号` 并指向**消费点**；绿测试 ≠ 已验证；
> 只跑 RAG 侧探针不算证据；静态无法确证的写 `UNRESOLVED`。

---

## 1. Component Contract（§4 + §5）

```
Component:  message-rpc —— 消息域的唯一事实写入与读取点（无 HTTP 面，纯 gRPC）
Purpose:    对内提供 9 个 gRPC：通知域 5 个 + 会话域 4 个
Go 出处:    service/message/rpc/internal/server/messageserviceserver.go:26-69（9 个方法）
RAG 出处:   service/message/message-rpc/.../service/impl/MessageRpcServiceImpl.java:74-116

Public Operations（两侧逐条同序、同名）
  SendNotification / ListNotifications / MarkNotificationRead / MarkAllNotificationsRead /
  GetUnreadSummary / ListConversations / GetConversationMessages / SendChatMessage /
  MarkConversationRead

Inputs:      全部为 protobuf 消息（9 个 Req）；**无 HTTP header / 无 JWT / 无 metadata 参与鉴权**
Outputs:     全部为 protobuf 消息；错误一律走 gRPC status（`Status.Code` + description=码表文案）
State:       无进程内状态；全部状态在三张 PG 表
Side Effects: 写 message_notification / message_conversation / message_conversation_message；
              对外调用 user-rpc.GetUser、admin-rpc.GetSelf、admin-rpc.GetUserList；
              启动期注册 Nacos
Failure:     Go 裸 error 透传（grpc Unknown(2)）；RAG 非 StatusRuntimeException 兜底 INTERNAL(13)
```

> ⚠️ **`@GrpcService` 声明方法数**：Go `messageserviceserver.go` 有 9 个，`MessageRpcServiceImpl` 也有 9 个
> （`grep -c "@Override"` = 9）。两侧 public surface 一一对应，无遗漏、无多余。

---

## 2. GO-SEMANTIC-MODEL（§7 + §6 的 18 字段）

| OP | Input → Precondition → Processing → External → Result / State / Error | Timeout | Retry | Concurrency | Evidence |
|---|---|---|---|---|---|
| **OP-01 SendNotification** | `title`/`content` 先 `TrimSpace`，任一为空 → INVALID_ARGUMENT+5003；`broadcast=true` 则**覆盖** `recipientIds`，走 `admin.GetUserList` 逐页收集（page 从 1 起，某页空或 `len(list) < pageSize` 停，`uid<=0` 跳过，`seen` 去重）；空 → 5003；逐条 `snowflake.GetID()`；**一次 `CreateInBatches(items, 200)`** | 无 | 无 | 批量写由 GORM `CreateInBatches` 自带事务 | `sendnotificationlogic.go:39-79`；`helper.go:188-225`；`message.go:22-27` |
| **OP-02 ListNotifications** | `userId<=0` → 5003；`normalizeLimit(limit,20,100)`；**同一 query 先加 `is_read=false`（当 unreadOnly）再 `Count` 得 total**；再单独数 unread；`ORDER BY created_at DESC`，`if offset>0` 才 Offset、`if limit>0` 才 Limit | 无 | 无 | 无 | `listnotificationslogic.go:37-60`；`message.go:39-70` |
| **OP-03 MarkNotificationRead** | `userId<=0 \|\| notificationId<=0` → 5003；**单条** `UPDATE ... WHERE id=? AND recipient_id=?`；`RowsAffected==0` → NOT_FOUND+5203 | 无 | 无 | 归属判断与写入同一条语句，天然无 TOCTOU | `marknotificationreadlogic.go:39-54`；`message.go:72-84` |
| **OP-04 MarkAllNotificationsRead** | `userId<=0` → 5003；单条 `UPDATE ... WHERE recipient_id=? AND is_read=false`；**不校验 RowsAffected**（0 行也算成功） | 无 | 无 | 无 | `markallnotificationsreadlogic.go:37-47`；`message.go:86-91` |
| **OP-05 GetUnreadSummary** | `userId<=0` → 5003；notification 未读（**role 硬编码 USER 不适用，只按 recipient_id**）+ conversation 未读（`recipient_role=USER`）；`total = 两者相加` | 无 | 无 | 无 | `getunreadsummarylogic.go:37-60`；`message.go:29-37,140-146` |
| **OP-06 ListConversations** | `operatorId<=0 \|\| !validRole` → 5003；`normalizeLimit`；会话列表 `ORDER BY last_message_at DESC, updated_at DESC`（**两个键**）；再单独数全局未读；**逐条**组装 item：先 `peerOf`（失败 → PERMISSION_DENIED+5005）→ `CountUnreadMessagesByConversation`（失败 → `ObserveDBError("conversation","count_unread")`）→ `loadProfile`（失败兜底 `"User/Admin/Unknown {id}"`） | 无 | 无 | 无 | `listconversationslogic.go:37-71`；`message.go:114-138`；`helper.go:160-186` |
| **OP-07 GetConversationMessages** | `operatorId<=0 \|\| conversationId<=0 \|\| !validRole` → 5003；会话不存在 → NOT_FOUND+5202；非参与者 → PERMISSION_DENIED+5005；`ORDER BY created_at ASC`（**只有一个键，刻意不加 tie-breaker**） | 无 | 无 | 无 | `getconversationmessageslogic.go:39-79`；`message.go:159-174` |
| **OP-08 SendChatMessage** | `content=TrimSpace(...)`，`senderId<=0 \|\| content=="" \|\| !validRole` → 5003；<br>`conversationId>0`：查会话（不存在→5202）→ `isParticipant`（否→5005）→ `peerOf`（失败→5005）<br>`else`：`recipientId<=0 \|\| !validRole` → 5003；自聊 → INVALID_ARGUMENT+**5201**；`canonicalParticipants` 后 `FindConversationByParticipants`（未找到当 nil）<br>`conv==nil`：ADMIN→要求对端 USER，否则 5007；USER→USER 建 **PENDING**（首条 `runeLen>50` → 5006）；其它组合 → PERMISSION_DENIED+**5205**<br>`conv!=nil`：PENDING 且发送方就是 pending_sender → FAILED_PRECONDITION+5204；否则 PENDING→OPEN；UNSPECIFIED→OPEN<br>**两条写路径都在显式 `Transaction` 闭包内**（先会话后消息） | 无 | 无 | `uk_conversation_pair` 唯一索引：并发首发时**一方**唯一冲突 ⇒ **整事务回滚** | `sendchatmessagelogic.go:41-237`；`message.go:186-231`；`helper.go:52-91` |
| **OP-09 MarkConversationRead** | 校验 → 查会话（不存在→5202）→ 非参与者→5005；**单条** `UPDATE message_conversation_message ... WHERE conversation_id=? AND recipient_role=? AND recipient_id=? AND is_read=false`；**不触碰 conversation 表** | 无 | 无 | 无 | `markconversationreadlogic.go:39-64`；`message.go:176-184` |

**框架层（Go 侧，`zrpc` 隐式装配，本轮逐条核到消费点）**

| Go 事实 | 证据（消费点） | 生效条件 |
|---|---|---|
| `RpcServerConf.Timeout` 默认 **2000ms** | `go-zero@v1.10.2/zrpc/config.go:46` `json:",default=2000"`；`etc/message.yaml` 未配该键 ⇒ 消费点在 `core/mapping/unmarshaler.go` 的默认值填充 | ✅ 生效 |
| **但原生 timeout 拦截器被显式关掉** | `service/message/rpc/message.go:34` `DisableNativeRpcTimeout(&c.RpcServerConf)` 把 `conf.Timeout = 0`（`common/observability/rpc.go:19-23`）⇒ `zrpc/server.go:164-167` 的 `if c.Timeout > 0` **不成立**，`UnaryTimeoutInterceptor` 不挂 | ✅ 已确证 |
| 超时由观测拦截器接管 | `message.go:44` `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(2000ms, slowThreshold))`；`rpc.go:38` `context.WithTimeout` → 到点返回 `codes.DeadlineExceeded`（`rpc.go:68-71`） | ✅ |
| **panic 恢复拦截器默认开启** | `zrpc/server.go:131-133` `if c.Middlewares.Recover` ⇒ `UnaryRecoverInterceptor`；默认值 `true` 见 `zrpc/internal/config.go:21` `json:",default=true"`；panic → `codes.Internal` + `"panic: %v"`（`recoverinterceptor.go:39-42`） | ✅ 生效 |
| **服务端熔断默认开启** | `zrpc/server.go:157-159` + `zrpc/internal/config.go:25` `default=true` ⇒ `UnaryBreakerInterceptor`；熔断打开 → `codes.Unavailable`（`breakerinterceptor.go:46-48`） | ✅ 生效 |
| **自适应限流（CPU 阈值）默认开启** | `zrpc/server.go:160-162` `if c.CpuThreshold > 0`；阈值默认 **900** 见 `zrpc/config.go:47` ⇒ `UnarySheddingInterceptor`；拒绝时 `codes.ResourceExhausted`（`sheddinginterceptor.go:30-35`） | ✅ 生效 |
| **gRPC reflection 默认不注册** | `message.go:40-42` `if c.Mode == DevMode \|\| c.Mode == TestMode`；`ServiceConf.Mode` 默认 `"pro"`（`go-zero@v1.10.2/core/service/serviceconf.go:35` `json:",default=pro,..."`），`etc/message.yaml` **未配 Mode** ⇒ 分支不成立 | ✅ 已确证（§3.3.1：读到消费点，不是 struct 定义行） |
| DB 显式关 TLS | `internal/model/db.go:22-29` DSN 拼 `sslmode=%s`，`etc/message.yaml:34` `Mode: "disable"` ⇒ 消费点收到 `disable` | ✅ |

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（§7 / §34，与 Go 同结构）

| OP | 同结构摘要（只写与 Go 不同或需定位处） | Evidence |
|---|---|---|
| **OP-01** | `broadcast=true` 分支覆盖 recipients ✅；`AdminRpcClient.collectBroadcastRecipients` 页循环与停止条件逐行对齐 ✅；**插入按 `INSERT_BATCH_SIZE=200` 切片，全部仍在同一个 `@Transactional` 内**（Go `CreateInBatches(items,200)` 自带事务） | `MessageDomainService.java:437-476`；`AdminRpcClient.java:84-118`；`MessageAtomicService.java:175-182` |
| **OP-02** | `countByRecipient(uid,0,0,unreadOnly)` 与 `listByRecipient(...,unreadOnly,...)` 共用过滤 ✅（V-001 已修）；`listByRecipient` 用 `w.last("LIMIT n OFFSET m")` **无条件拼接**（Go 是 `if offset>0` 条件拼接）⇒ 见 `DRIFT-MSG-V-014` | `MessageDomainService.java:768-779`；`NotificationMapper.java:34-50` |
| **OP-03** | `LambdaUpdateWrapper` 单条 UPDATE + `RowsAffected` ✅（V-011 修法）；实体字段名 `read` + `@TableField("is_read")` | `NotificationMapper.java:123-136`；`NotificationEntity.java:92-93` |
| **OP-04** | 单条 UPDATE，不校验 RowsAffected ✅ | `MessageAtomicService.java:264-268` |
| **OP-05** | `countUnreadByRecipient` + `countUnreadByRecipientGlobal(uid, SenderRole.USER)` ✅ | `MessageDomainService.java:918-935` |
| **OP-06** | 排序双键已补 ✅（V-005 已修）；**逐条 item 组装时 `countUnreadMessagesByConversation` 不在 try/catch 内** ⇒ 无 `observeDbError`；`peerOf` 的失败判定被推迟到 `buildConversationItem` ⇒ 见 `DRIFT-MSG-V-018` | `MessageDomainService.java:546-561`；`MessageRpcSupport.java:370-396` |
| **OP-07** | `listByConversationPage` 排序键 `created_at` ✅（V-004 已修）；offset 同样无条件拼接 ⇒ V-014 | `ConversationMessageMapper.java:126-133` |
| **OP-08** | 9 个校验/分支逐条对齐 ✅；**两条写路径的 `msg.setConversationId(conv.getId())` 都在 `@Transactional` 独立 Bean `MessageAtomicService` 内**（DRIFT-MSG-001/002 已修）；语句序（先 UPDATE 会话后 INSERT 消息）与 Go 一致 | `MessageDomainService.java:183-388`；`MessageAtomicService.java:102-140` |
| **OP-09** | 单条 `@Update`，**不再触碰 conversation 表** ✅（V-006 已修，§6.7.7） | `MessageAtomicService.java:222-227` |

**框架层（RAG 侧）**

| 维度 | 现状 | Evidence |
|---|---|---|
| Spring 上下文 | `@SpringBootApplication`（**无** `scanBasePackages`）+ `@ConfigurationPropertiesScan` + `@MapperScan` + **`@Import` 显式引入 8 个 common Bean** | `MessageRpcApplication.java:64-87` |
| gRPC server | `@GrpcService` + devh starter 自动注册；**唯一 `ServerInterceptor`（观测三件套）经 `GrpcServerConfigurer` 显式挂上** | `MessageRpcApplication.java:104-108` |
| 超时 | `observability.rpc-timeout: 2s` → `ObservabilityProperties` → `RpcServerInterceptorFactory` → `RpcInterceptor`，到点 `close(DEADLINE_EXCEEDED)` | `application.yml:143-144`；`RpcServerInterceptorFactory.java:41-47`；`RpcInterceptor.java:148-190` |
| MyBatis-Plus 自动配置排序 | Boot 4 把 `DataSourceAutoConfiguration` 挪包 ⇒ MP 的 `@AutoConfigureAfter` 悬空；已用**空类桥**修复并在 imports 文件登记 | `MybatisPlusOrderingBridgeAutoConfiguration.java:49-52`；`META-INF/spring/…AutoConfiguration.imports:1` |
| Flyway | `spring-boot-flyway:4.0.8` 在 classpath（Boot 4 拆模块后必需）；`spring.flyway.table: flyway_schema_history_message` | `pom.xml:166-169`；`application.yml:53-58` |
| Web 容器 | **无** tomcat-embed-core / jetty / undertow / starter-web（仅 `tomcat-embed-el` 表达式库）⇒ **不会起 servlet 容器** | `mvn -pl service/message/message-rpc dependency:tree`（2026-10-23 隔离跑） |

---

## 4. Equivalence Matrix（§24 + §35）

Result 只用 5 个枚举值。Evidence 列 `[RUNTIME-PG]` = 本轮真 PG；`[RUNTIME-PG-PROBE]` = 本轮裸 `psql` 探针；
`[CODE]` = 静态源码逐行；`[JAVAP]` = 字节码/依赖树实测；`[DOC]` = 对方框架的文档化默认值。

| Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|
| 全部 9 个 | Public surface（方法数/名/序） | 9 | 9 | **EQUIVALENT** | `[CODE]` |
| 全部 9 个 | wire 契约（字段号/类型/枚举/rpc 签名） | 22 msg+enum / 9 rpc | 同 | **EQUIVALENT** | `[CODE]` 归一化 diff 空，双侧计数 22=22、9=9 |
| 全部 9 个 | 错误码与文案表（21 码 / 21 文案） | 一致 | 一致 | **EQUIVALENT** | `[CODE]` `errmsg.go:28-73` ↔ `MessageErrorCode`+`MessageErrorMessages` |
| SendNotification | 校验分支 / broadcast 覆盖 / 收集停止条件 | 逐条 | 逐条 | **EQUIVALENT** | `[CODE]` |
| SendNotification | 批量写原子性 | GORM `CreateInBatches` 自带事务 | 单 `@Transactional` 内切 200/片 | **EQUIVALENT** | `[RUNTIME-PG]` `batchInsertMustStayAtomicAcrossChunks` |
| SendNotification | 广播规模上限 | 无上限（分 200/批） | 同（已修 V-002） | **EQUIVALENT** | `[RUNTIME-PG]` 6000 条用例 |
| ListNotifications | `total` 是否跟随 `unread_only` | 是 | 是（已修 V-001） | **EQUIVALENT** | `[RUNTIME-PG]` |
| ListNotifications | **负 `offset`** | `if offset>0` ⇒ **忽略，返回第一页** | `w.last("OFFSET -1")` ⇒ PG 报错 | **SEMANTIC MISMATCH** | `[CODE]`+`[RUNTIME-PG-PROBE]` ⇒ **DRIFT-MSG-V-014** |
| ListNotifications | 排序键 | `created_at DESC` | `createdAt DESC` | **EQUIVALENT** | `[CODE]` |
| MarkNotificationRead | 归属原子性 / NotFound 语义 | 单条 UPDATE + RowsAffected==0 | 同构 | **EQUIVALENT** | `[RUNTIME-PG]` 4 条 ArgumentCaptor 用例 |
| MarkAllNotificationsRead | 0 行也成功 | 是 | 是 | **EQUIVALENT** | `[CODE]` |
| GetUnreadSummary | 两项未读口径 | notification by recipient_id / message by recipient_role=USER | 同 | **EQUIVALENT** | `[CODE]` |
| ListConversations | 排序键 | `last_message_at DESC, updated_at DESC` | 同（已修 V-005） | **EQUIVALENT** | `[RUNTIME-PG]` 并列用例 |
| ListConversations | **负 `offset`** | 忽略 | PG 报错 | **SEMANTIC MISMATCH** | 同 V-014 |
| ListConversations | item 组装的 helper 序 + 埋点 | 先 `peerOf` 判定，再 count（**带** observeDbError） | 先 count（**不带** observeDbError），再判 | **OBSERVABLE DIFFERENCE** | ⇒ **DRIFT-MSG-V-018** |
| GetConversationMessages | 排序键 | `created_at ASC`（无 tie-breaker） | 同（已修 V-004） | **EQUIVALENT** | `[RUNTIME-PG]` 反序构造用例 |
| GetConversationMessages | **负 `offset`** | 忽略 | PG 报错 | **SEMANTIC MISMATCH** | 同 V-014 |
| SendChatMessage | 9 个分支 + 错误码 | 逐条 | 逐条 | **EQUIVALENT** | `[CODE]` |
| SendChatMessage | 事务边界（2 条写路径） | 显式 `Transaction` 闭包 | 独立 Bean `@Transactional` | **EQUIVALENT** | `[RUNTIME-PG]` 8 条 + 变异测试历史记录 |
| SendChatMessage | 事务内语句序 | 先 UPDATE 会话 → 后 INSERT 消息 | 同 | **EQUIVALENT** | `[CODE]` |
| SendChatMessage | 并发首发 | `uk_conversation_pair` 冲突 ⇒ 整事务回滚 | 同 | **EQUIVALENT** | `[CODE]`+`[RUNTIME-PG]` |
| MarkConversationRead | 副作用列集合 | 只写 message 表 | 同（V-006 已修） | **EQUIVALENT** | `[RUNTIME-PG]` |
| 全部 | 时间戳 epoch | `timestamptz` + `TimeZone=Asia/Shanghai` ⇒ `.Unix()` 与时区无关 | `TIMESTAMP` + 读侧 `atZone(systemDefault)` | **EQUIVALENT**（前提：一个库只由同一时区 JVM 写） | `[RUNTIME-PG]` `notificationTimestampsMustRoundTripLosslessly`；前提见 §6.7.10 遗留缺口 |
| 全部 | 超时（scope + 触发者） | 2s，由观测拦截器独占执行 | 2s，`RpcInterceptor` | **EQUIVALENT** | `[CODE]`+`[JAVAP]` |
| 全部 | **panic 恢复** | 框架默认挂 `UnaryRecoverInterceptor` ⇒ INTERNAL + `"panic: %v"` | 无任何 recover 拦截器 | **OBSERVABLE DIFFERENCE** | ⇒ **DRIFT-MSG-V-016** |
| 全部 | **CPU 自适应限流** | `CpuThreshold=900` ⇒ 拒绝时 `ResourceExhausted` | 无 | **OBSERVABLE DIFFERENCE** | ⇒ **DRIFT-MSG-V-016** |
| 全部 | **服务端熔断** | `Breaker=true` ⇒ 拒绝时 `Unavailable` | 无 | **OBSERVABLE DIFFERENCE** | ⇒ **DRIFT-MSG-V-016** |
| 全部 | **gRPC reflection 暴露** | 默认 `Mode=pro` ⇒ **不注册** | devh `reflectionServiceEnabled` 默认 **true** ⇒ 注册 | **OBSERVABLE DIFFERENCE** | `[JAVAP]` 构造函数字节码 `iconst_1` ⇒ **DRIFT-MSG-V-015** |
| 全部 | **DB TLS** | DSN 显式 `sslmode=disable` | JDBC URL 无 `sslmode` ⇒ pgjdbc 默认 `prefer` | **OBSERVABLE DIFFERENCE** | `[CODE]`+`[DOC]` ⇒ **DRIFT-MSG-V-019** |
| 全部 | 建表机制 | `AutoMigrate` 每次启动**校验并改表** | `Flyway` 一次性迁移 + 账本 | **OBSERVABLE DIFFERENCE** | ⇒ 历史 `UNRESOLVED-MSG-005`，本轮未关闭 |
| 通知写路径 | `jsonb` 绑定 | `JSONMap.Value()` → `json.Marshal`，驱动按 jsonb 处理 | `ps.setObject(..., Types.OTHER)`（V-013 已修） | **EQUIVALENT** | `[RUNTIME-PG]` |
| 会话消息写路径 | `is_read` 列绑定 | GORM 按字段名 `is_read` | 手写 `#{isRead}`（**靠 MyBatis 字段反射兜住**） | **EQUIVALENT**（但脆弱，见 V-017） | `[RUNTIME-PG]`；⇒ **DRIFT-MSG-V-017** |
| 重试 | 有无自动重试 | 无 | 无 | **EQUIVALENT** | `[CODE]` 两侧零 retry |
| 服务发现 | 注册中心 | etcd（`Etcd.Key: message.rpc`） | Nacos（`public`/`DEFAULT_GROUP`） | **OBSERVABLE DIFFERENCE** | 历史 `UNRESOLVED-MSG-V-001`，本轮未动 |

---

## 5. External Interaction & Error Flow（§8 + §10 + §11）

### 5.1 外部系统清单

| External System | Operation | Input | Result 语义 | Failure 是否阻塞 | 失败是否被误当成功 |
|---|---|---|---|---|---|
| **PostgreSQL** | SELECT / INSERT / UPDATE | 见 §2/§3 | 空集 vs 0 行 vs 异常三态**两侧都区分** | 是 | 否 |
| **user-rpc** `GetUser` | unary | `uid` | `found=false` ⇒ 兜底 `"User {id}",""` | **否**（仅展示字段） | 否（两侧都兜底） |
| **admin-rpc** `GetSelf` | unary | `uid` | `admin==nil` ⇒ 兜底 `"Admin {id}",""` | **否** | 否 |
| **admin-rpc** `GetUserList** | 分页 | `page`/`pageSize` | 空列表 ⇒ 正常停止 | **是**（广播时） | 否 |
| **Nacos** | register / deregister | 实例元数据 | — | 否（`@EventListener(ApplicationReadyEvent)`） | — |

> ⚠️ **降级方向的差异（未登记）**：`UserRpcClient.getProfile` 与 `AdminRpcClient.loadProfile` 都**吞掉所有 `RuntimeException`**
> 并兜底（`UserRpcClient.java:47-52`）。Go `loadProfile`（`helper.go:141-158`）只判 `err != nil`，行为相同 ⇒
> **EQUIVALENT**，不构成漂移。但代价是**下游故障时列表接口耗时不可控**（Go 同样如此），两端口径一致，登记为已知共同缺陷。

### 5.2 Error Flow 逐条

| Failure | Go 触发条件 | Go 最终输出 | RAG 触发条件 | RAG 最终输出 | 等价？ |
|---|---|---|---|---|---|
| 参数非法 | 9 个 Logic 的开头校验 | `INVALID_ARGUMENT` + 码表文案 | 逐条一致 | 同 | **YES** |
| 会话/通知不存在 | `errors.Is(err, gorm.ErrRecordNotFound)` | `NOT_FOUND` + 5202/5203 | `Optional.isEmpty()` / `RowsAffected==0` | 同 | **YES** |
| 非参与者 | `!isParticipant` | `PERMISSION_DENIED` + 5005 | 同 | 同 | **YES** |
| 同方连发 | PENDING 且是 pending_sender | `FAILED_PRECONDITION` + 5204 | 同 | 同 | **YES** |
| **DB / 内部故障** | 裸 GORM error | grpc `Unknown(2)` + **原始 SQL 错误文本** → 客户端兜底 `1015` | 裸 `RuntimeException` | grpc `INTERNAL(13)` + `"internal server error"`（5001）；三个 mark-* 路径显式 5008 | **NO —— 已签字保留 RAG 侧（DRIFT-MSG-V-003）** |
| **panic** | 框架 recover 拦截器 | `INTERNAL` + `"panic: %v"` | 无拦截器 | 由 grpc-java 自身处理（本轮**未取证**） | **UNKNOWN ⇒ UNRESOLVED-MSG-006** |
| 超时 | 观测拦截器 | `DEADLINE_EXCEEDED` | `RpcInterceptor` | `DEADLINE_EXCEEDED` | **YES** |
| 负 offset | 不触发（被忽略） | 正常返回第一页 | PG `OFFSET must not be negative` | `INTERNAL` + 5001 | **NO ⇒ DRIFT-MSG-V-014** |

---

## 6. 逐维度结论（§12–§21）

| 维度 | 结论 | 要点 |
|---|---|---|
| **State Semantics（§12）** | **EQUIVALENT** | 会话状态机 `UNSPECIFIED(0)→OPEN(2)` / `PENDING(1)→OPEN(2)` 在 `MessageDomainService.java:350-366` 与 `sendchatmessagelogic.go:200-215` 逐条一致；`PENDING` 时排除 pending_sender 本方（`MessageRpcSupport.canSend:359` ↔ `helper.go:90`）一致。⚠️ 注意 DDL `status ... DEFAULT 1` 与 GORM tag `default:1` **两侧同为 1（PENDING）**，一致 |
| **Cache Semantics（§13）** | **N/A** | 本组件无缓存。两端均无 Redis 依赖 |
| **Auth（§14）** | **EQUIVALENT（无鉴权）** | 两侧都不解析 JWT、不读 metadata、不校验调用方身份；身份完全由请求体里的 `sender_id`/`operator_id` 承担。这是**真源的设计**，不是遗漏 |
| **Transaction（§15）** | **EQUIVALENT** | 保护的业务操作集合逐条相同：新建=插会话+插消息；追加=更会话 7 列+插消息；批量通知=全部切片。**语句序也一致**（先 UPDATE 后 INSERT，避免加锁顺序相反）。⚠️ RAG 侧 `markConversationReadTx` / `markNotificationReadTx` / `markAllNotificationsReadTx` 各自只包**单条**语句 ⇒ 多一次 BEGIN/COMMIT，属实现差异，不改可观察行为 |
| **Concurrency（§16）** | **EQUIVALENT** | 两侧都靠 DB 侧的 `uk_conversation_pair` + 事务保证「同对用户只有一个会话」；冲突时**都整事务回滚**。两侧**都无行级悲观锁**、都无应用层互斥。雪花 ID 两侧同为 bwmarrin 布局、`%1024` 模序、**同样不保护时钟回拨**（历史 §6.6.3 已核对） |
| **Timeout（§17）** | **EQUIVALENT** | 2s，**scope 一致**（整个 handler 调用），**且两侧都只有一个执行者**（Go 显式关掉 go-zero 原生超时再交给观测层；RAG 只挂 `RpcInterceptor`）。见 §2 框架层表 |
| **Retry（§18）** | **EQUIVALENT** | 两侧零重试 |
| **Serialization（§19）** | **EQUIVALENT**（1 处脆弱） | proto 逐字节一致；`null` / `empty` / `missing` 三态在 `safeMap`/`mapString` 与 `JsonbMapTypeHandler.parse` 上都归一为「空 map 输出、null 返回 null」。⚠️ `ConversationMessageMapper.insertMsg` 的 `#{isRead}` 靠 **MyBatis 字段反射**兜住，不是靠 getter ⇒ 见 `DRIFT-MSG-V-017` |
| **Config（§20）** | **1 项 OBSERVABLE DIFFERENCE** | `List.DefaultLimit/MaxLimit`、`Broadcast.PageSize`、连接池 `100/10`、超时 `2s`、gRPC 端口 `8082` 逐条一致；**PG 的 `sslmode` 不一致** ⇒ V-019。⚠️ Go 的 `Broadcast.PageSize` 兜底 200 在 `helper.go:189-192`，RAG 在 `AdminRpcClient.java:85-87`，一致 |
| **Lifecycle（§21）** | **EQUIVALENT** | 两侧都是「注册数据访问 → 建 client → 起 server → 注册发现 → 阻塞运行 → 优雅停机」。⚠️ Go `NewServiceContext` 里 `zrpc.MustNewClient` 默认 `NonBlock=false` ⇒ 走 `WithBlock()`，下游未监听会**启动期阻塞**；RAG devh client 在 **Bean 初始化期**建 channel（历史 §6.6 注释实测）⇒ 两端都在启动期对下游有硬依赖，方向一致。差异在于 RAG 依赖 **Nacos**、Go 依赖 **etcd**（历史 `UNRESOLVED-MSG-V-001`） |

---

## 7. Semantic Drifts（§27 + §36）

### DRIFT-MSG-V-014 🆕 负 `offset` 在 RAG 侧变成数据库语法错误

**Component**: message-rpc　**Operation**: `ListNotifications` / `ListConversations` / `GetConversationMessages`
**Category**: Input Interpretation + Error Identity　**Classification**: **`SEMANTIC MISMATCH`**
**Risk**: **HIGH**　**Counterfactual（§26）**: **YES**

```
Go  internal/model/message.go:59-64 / :127-131 / :164-168
    if offset > 0 { db = db.Offset(int(offset)) }      ← 消费点：负数直接被跳过
    if limit  > 0 { db = db.Limit(int(limit)) }
  ⇒ offset=-1 时 SQL 里根本没有 OFFSET 子句 ⇒ 正常返回第一页

RAG NotificationMapper.java:48        w.last("LIMIT " + limit + " OFFSET " + offset);
    ConversationMapper.java:81        同
    ConversationMessageMapper.java:131 同
  ⇒ offset=-1 ⇒ SQL 变成 "LIMIT 20 OFFSET -1"
```

**实测（本轮裸 `psql` 探针，`postgres:16-alpine`）**：

```
SELECT id FROM m LIMIT 20 OFFSET 0;   -- 5 rows
SELECT id FROM m LIMIT 20 OFFSET -1;   -- ERROR:  OFFSET must not be negative
```

**Why It Matters / Observable Impact**：`offset` 在 proto 里是**有符号 `int32`**
（`message.proto:50 / :118 / :146`），任何客户端都能发出负数。
Go 返回 **SUCCESS + 第一页**；RAG 抛 `BadSqlGrammarException` ⇒ `MessageRpcServiceImpl.invoke:133-139`
兜底成 `INTERNAL` + `"internal server error"` ⇒ 客户端拿到 **5001**。
**这不是「参数非法」，是「基础设施故障」** —— 告警会把它记成 DB 故障。

**必保性质**：分页参数非法 ⇒ 明确的 `INVALID_ARGUMENT`，或与 Go 一致地忽略；**不得**降级成 DB 错误。

**Evidence**: `[CODE]` `message.go:59-64,127-131,164-168` ↔ 三个 Mapper 的 `w.last(...)`；
`[RUNTIME-PG-PROBE]` 本轮 `psql` 探针输出；`[CODE]` `message.proto:50,118,146` 字段类型。
**为什么 39 条测试没抓到**：无任何用例构造负 `offset` —— 与 §6.7.5 纪律 A 同源，
分页断言只覆盖了正常值与 `total`。

---

### DRIFT-MSG-V-015 🆕 gRPC server reflection 的暴露面两侧不对称

**Component**: message-rpc　**Operation**: 全部（服务级能力）
**Category**: Lifecycle / Attack Surface　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **MEDIUM**（信息暴露 + 运维能力不一致）

```
Go  message.go:40-42
      if c.Mode == service.DevMode || c.Mode == service.TestMode { reflection.Register(grpcServer) }
    Mode 默认值来自 go-zero@v1.10.2 core/service/serviceconf.go:35
      Mode string `json:",default=pro,options=dev|test|rt|pre|pro"`
    etc/message.yaml **未配 Mode** ⇒ Mode="pro" ⇒ 分支不成立 ⇒ **真源不暴露 reflection**

RAG application.yml:75-76
      # Go 端 reflection 在 DevMode/TestMode 启用，Java 端由 devh starter 默认注册 reflection
      ← 这句注释**是错的**
    application.yml 未配 grpc.server.reflection-service-enabled
    net.devh GrpcServerProperties 构造函数字节码（L109-L111）：
      aload_0 / iconst_1 / putfield reflectionServiceEnabled:Z      ← 默认 true
```

**Observable Impact**：RAG 侧 `grpcurl -plaintext localhost:8082 list` 能列出全部 9 个方法并下载 proto 描述符；
Go 侧同一命令连不上。**同时**：注释给了错误的安全感（§3.3「注释说了假话」的又一例）。

**Evidence**: `[JAVAP]` `GrpcServerProperties.class` 构造函数字节码；`[CODE]` Go 侧消费点 + `serviceconf.go:35` 默认值 tag。

---

### DRIFT-MSG-V-016 🆕 go-zero 框架级三个 unary 拦截器在 RAG 侧全部缺失

**Component**: message-rpc　**Operation**: 全部
**Category**: Resilience / Error Identity　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **MEDIUM-HIGH**（过载与崩溃时的可观察行为不同）

| 拦截器 | Go 侧装配点（消费点） | 默认值来源 | 拒绝/异常时的可观察行为 | RAG 侧 |
|---|---|---|---|---|
| panic 恢复 | `zrpc/server.go:131-133` | `zrpc/internal/config.go:21` `Recover json:",default=true"` | `INTERNAL` + `"panic: %v"`（`recoverinterceptor.go:39-42`），**进程存活** | ❌ 无 |
| 服务端熔断 | `zrpc/server.go:157-159` | `config.go:25` `Breaker json:",default=true"` | `UNAVAILABLE`（`breakerinterceptor.go:46-48`） | ❌ 无 |
| CPU 自适应限流 | `zrpc/server.go:160-162` | `zrpc/config.go:47` `CpuThreshold json:",default=900"` | `RESOURCE_EXHAUSTED`（`sheddinginterceptor.go:30-35`） | ❌ 无 |

**⚠️ 特别值得记的一点**：`message.go:34` 的 `DisableNativeRpcTimeout` 只把 `Timeout` 清零，
**`CpuThreshold` / `Middlewares.*` 全部原样保留** ⇒ 这三个拦截器在真源上是**生效**的。
审到这里容易误以为「Go 的中间件都被关了」—— **只关了超时一个**。

**Observable Impact**：同一台机器 CPU 打满时，Go 会以 `RESOURCE_EXHAUSTED` 主动拒流并保住进程；
RAG 会继续把请求打进 DB，直到 Hikari 池耗尽 / 超时。panic 时 Go 记日志后返回 `INTERNAL`，
RAG 的行为取决于 grpc-java 自身（**本轮未取证**，见 `UNRESOLVED-MSG-006`）。

**Evidence**: `[CODE]` go-zero v1.10.2 三个文件的具体行；`[CODE]` `message.go:34-44` 证明只清了 Timeout。
**Counterfactual = YES**：Spring 侧开发者通常只会挂「一个观测拦截器」，
不会想到要在纯 gRPC 服务里补 recover / 限流 / 熔断三件套。

---

### DRIFT-MSG-V-017 🆕 `ConversationMessageEntity.isRead` 未按 `NotificationEntity` 的教训改名

**Component**: message-rpc　**Operation**: `ConversationMessageMapper.markAllReadByRecipient`（**当前不可达**）
**Category**: 潜伏缺陷　**Classification**: **`OBSERVABLE DIFFERENCE`（latent，今天无行为差）**
**Risk**: **HIGH（一旦接线即发作）**

```
NotificationEntity.java:92-93     @TableField("is_read")  private boolean read;   ← 已改名（2026-10-21）
                                   getter isRead() / setter setRead()  ⇒ MP lambda 命中

ConversationMessageEntity.java:42  private boolean isRead;                          ← **没改名**
                                   :64-65  isRead() / setRead()
ConversationMessageMapper.java:82-84
    w.eq(ConversationMessageEntity::isRead, false)
      .set(ConversationMessageEntity::isRead, true)
    ⇒ MP 解析出属性名 "read"，而 TableInfo.propertyMap 的 key 是 "isRead"
      ⇒ 抛 MybatisPlusException: can not find lambda cache for this property [read]
```

**今天为什么没炸**：该方法**无任何调用方**
（`grep -rn markAllReadByRecipient service/message/message-rpc/src/main` 只命中定义与
`NotificationMapper` 的同名方法；生产路径走的是手写 `@Update` 的
`markReadByConversationAndRecipient`）。

**为什么仍然要登记**：
1. 这是 §6.2.4② 已经**付出运行期代价**换来的教训，同一模块内却只改了一半；
2. 同文件里还有一个同类残留：`ConversationMessageMapper.listByConversation`（`:30-41`）
   **仍按 `id ASC` 排序** —— 正是 V-004 修掉的那个错排序键，只因是死代码而没被同步；
3. 任何人接线（补 `MarkAllConversationsRead`、或改走 lambda wrapper）都会立刻踩中。

**为什么 `insertMsg` 里的 `#{isRead}` 现在还能跑**：MyBatis 的 `Reflector` 构造顺序是
`addGetMethods → addSetMethods → addFields`，`addFields` 会把**字段名**补进属性表；
本实体字段名恰是 `isRead`（与 `NotificationEntity` 改名后的情况相反），所以字段反射兜住了它。
**这正是 V-011 的「同一个坑、反向触发条件」** —— 值得留档。

**Evidence**: `[CODE]` 两个实体的字段/访问器 + Mapper 的 lambda 引用 + `grep` 零调用方；
判据来自 §6.2.4② 的**实测输出**（`keySet=[isRead->isRead, ...]` ⇒ `::isRead` ⇒ FAILED `[read]`）。

---

### DRIFT-MSG-V-018 🆕 `ListConversations` 组装 item 时少一次埋点、helper 序也不同

**Component**: message-rpc　**Operation**: `ListConversations`
**Category**: Observability + Internal Ordering　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **LOW-MEDIUM**

```
Go  helper.go:160-186  buildConversationItem
      peerOf(...)                       !ok → PERMISSION_DENIED + 5005     ← 先判
      CountUnreadMessagesByConversation(...) err != nil
            → metrics.ObserveDBError("conversation", "count_unread")     ← 有埋点
            → return nil, err
      loadProfile(...)

RAG MessageDomainService.java:547-560
      long unread = conversationMapper.countUnreadMessagesByConversation(...);   ← 无 try/catch、无埋点
      PeerResult peer = MessageRpcSupport.peerOf(...);
      profile = (peer.role()==USER) ? userRpcClient... : adminRpcClient...
      itemDtos.add(MessageRpcSupport.buildConversationItem(...));                ← 这里才判 !found
```

**Observable Impact**：
1. `message_rpc_db_error_total{action="conversation", type="count_unread"}` 在这条路径上**永远不增长**
   —— 运维看板会以为「从没有 count_unread 失败」，而 Go 侧这条指标是会长的；
2. `peerOf` 失败时的**可观察结果一致**（都是 `PERMISSION_DENIED` + 5005），
   但 RAG 会**多打一次 COUNT** 才拒绝。当前不可达（列表已按参与者过滤，`peerOf` 不会失败），
   登记为「顺序差异 + 不可达」，与 V-017 同级。

**Evidence**: `[CODE]` 两侧逐行；`[CODE]` `MessageMetrics.java:130-137`（埋点确实存在，只是这条路径没调）。

---

### DRIFT-MSG-V-019 🆕 PostgreSQL 连接的 `sslmode` 两侧不一致

**Component**: message-rpc　**Operation**: 全部（数据访问层）
**Category**: Configuration Semantics　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **LOW-MEDIUM**（安全向）

```
Go  internal/model/db.go:22-29
      dsn = "host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai"
                                                        ^^^^^^^ 来自 conf.Mode
    etc/message.yaml:34   Mode: "disable"     ⇒ sslmode=disable，**明文，永不尝试 TLS**

RAG application.yml:16
      url: jdbc:postgresql://…/${POSTGRES_DB:first_db}      ← URL 里没有 sslmode
    ⇒ pgjdbc 使用文档化默认值 sslmode=prefer：先试 TLS，失败再回落明文
```

**Observable Impact**：对当前**未开 TLS 的 PG 容器**，两边最终都是明文 ⇒ 行为一致。
但一旦 PG 侧启用 TLS：Go **仍拒绝 TLS**（连不上），RAG **自动升级为加密连接**。
两侧对「同一份配置」的解读不同，属于 §20 的配置语义差。
**Evidence 强度说明**：Go 侧 `[CODE]`（消费点 + yaml）；RAG 侧 `[DOC]`（pgjdbc 文档化默认值），
**未在开了 TLS 的 PG 上实测** ⇒ 见 `UNRESOLVED-MSG-007`。

---

### 7.x 复核：对历史结论的就地修正（AGENTS.md §6 / §6.7）

| 历史结论 | 复核结果（2026-10-23） |
|---|---|
| `AGENTS.md §6.3 问题 3`：**「`message-rpc` 归一到显式 `@Import` 仍未做（现在能跑属撞运气）」** | 🔴 **原结论已过期，修正为：已完成。** `MessageRpcApplication.java:64` 是**裸** `@SpringBootApplication`（**已无** `scanBasePackages = "com.rag.common"`），`:67-87` 是 8 项显式 `@Import`，`:104-108` 是 `GrpcServerConfigurer`。⚠️ **该条仍写在 `AGENTS.md` 里，属他文件，本轮无权改 —— 需由持有者同步修正，否则后人仍会以为 message-rpc 是「撞运气」** |
| `UNRESOLVED-MSG-004`：`@MapperScan` / `spring-boot-flyway` / Bean 名 三项**无点名式防回退用例** | ✅ **复核为仍然成立**。`grep -rn "MapperScan\|nacosRegistration\|Flyway\|flyway" service/message/message-rpc/src/test/` 命中 **0 条**。三项目前只被 `MessageTransactionWiringTest` 的「上下文能起」**间接**锁住 —— 红了但红的原因不点名 |
| `UNRESOLVED-MSG-003`：`tools/difftest` 的 12 档**从未实际执行** | ✅ **复核为仍然成立**。`tools/difftest/` 下只有 `exp-difftest-grpc-errors-message.sh` 与 `grpc-message-dual-port-runner.java`（均 2026-10-22 12:17），**没有任何结果产物目录** |
| `UNRESOLVED-MSG-005`：`AutoMigrate`（每次启动校验表结构）vs `Flyway`（一次性迁移） | ✅ 仍成立，且本轮**部分加重**：`V2__create_message_conversation.sql:27-29` 用的是**裸 `ALTER TABLE … ADD CONSTRAINT`**，**没有 `IF NOT EXISTS`**。在一个「已被 GORM `AutoMigrate` 建过 `message_conversation`」的库上，这条迁移**会失败**。这条在本轮实测里没暴露，是因为 Testcontainers 每次都是**全新空库** |
| `DRIFT-MSG-012`（部署面：无 servlet 容器 vs 引了 web starter） | 🟡 **本轮可降级**。`mvn -pl service/message/message-rpc dependency:tree` 实测：无 `tomcat-embed-core` / `jetty` / `undertow` / `spring-boot-starter-web`，只有 `tomcat-embed-el`（纯表达式库）⇒ **不会起 servlet 容器**。剩余差异仅为 classpath 上多一个 `spring-boot-web-server`（为满足 nacos-discovery 对 `WebServerInitializedEvent` 的反射引用而必需）。**建议把 `UNKNOWN` 改为「已确证无行为影响」** |
| `MybatisPlusOrderingBridgeAutoConfiguration`（Boot 4 自动配置排序桥） | 🆕 **此前两轮台账均未提及**，但代码已存在且已生效。核实 imports 文件存在且只有这一行 ⇒ 排序边成立。**登记为已确认的基础设施事实，不计漂移** |
| go-zero 版本 | 台账多处写 `v1.10.2`；`go.mod:17` 确为 `v1.10.2`，本轮取证用的是 `~/go/pkg/mod/.../go-zero@v1.10.2` ✅ 一致 |

---

## 8. Test Cases（§29 + §30）

### 8.1 本轮**已执行**的验证（真 PG / 真容器）

| ID | 输入 / 环境 | 观察 | 结果 |
|---|---|---|---|
| **CASE-R01** | `mvn -o -pl service/message/message-rpc -am test`（**单模块隔离**），2026-10-23 02:02~02:03 | 6 个测试类 / **39 例** 全部通过，`EXIT=0` | ✅ `[RUNTIME-PG]` |
| **CASE-R02** | 同上，`MessageTransactionWiringTest` 是 `@SpringBootTest(classes=MessageRpcApplication.class)` + Testcontainers PG16 | **上下文真的起来了**：Mapper Bean 注入成功、`@Transactional` 真回滚、真实 SQL 执行 | ✅ |
| **CASE-R03** | 同一份 surefire 日志 | `Creating Schema History table "public"."flyway_schema_history_message"` + `Successfully applied 3 migrations to schema "public", now at version v3` ⇒ **`spring.flyway.table` 在运行时确实生效** | ✅ **关闭 AGENTS.md §6.3 问题 4 对本模块的 UNKNOWN** |
| **CASE-R04** | 裸 `psql` @ `postgres:16-alpine` | `LIMIT 20 OFFSET 0` → 5 rows；`LIMIT 20 OFFSET -1` → `ERROR: OFFSET must not be negative` | ✅ **V-014 的判定性证据** |
| **CASE-R05** | `javap -c GrpcServerProperties` | 构造函数字节码 `iconst_1 → putfield reflectionServiceEnabled` ⇒ 默认 **true** | ✅ **V-015 的判定性证据** |
| **CASE-R06** | `mvn -pl service/message/message-rpc dependency:tree` | 无任何 servlet 容器 jar | ✅ **DRIFT-MSG-012 可降级** |
| **CASE-R07** | proto 归一化 diff（剥 option / 注释 / 空白） | 输出为空；`msg+enum` 双侧 22=22、`rpc` 双侧 9=9 | ✅ |
| **CASE-R08** | 错误码/文案表双侧对读 | 21 码 + 21 文案逐条 1:1，含 `1015 "service busy"` 与未知码 fallback `"FAIL"` | ✅ |
| **CASE-R09** | `go-zero@v1.10.2` 源码读到**消费点** | `zrpc/config.go:46` 默认 2000ms；`serviceconf.go:35` 默认 `pro`；`zrpc/server.go:131-167` 三拦截器装配条件 | ✅ **满足 §3.3.1「必须读到消费 tag 的控制流」** |

### 8.2 关键用例的覆盖判定（哪些路径**仍然零覆盖**）

| 路径 | 覆盖状态 | 影响 |
|---|---|---|
| `sendNotification` **端到端 happy path**（走真 gRPC 端口） | ❌ 零覆盖 | `insertBatch` 只在 `MessageSemanticEquivalenceTest` 里被直接调过，从未经 gRPC 层 |
| `sendChatMessage` 走真 gRPC 端口 | ❌ 零覆盖 | 同上 |
| **负 `offset`** | ❌ 零覆盖 | ⇒ **V-014 能长期存在** |
| `listConversations` 分页翻页（`offset>0`） | ❌ 零覆盖 | |
| `loadProfile` 兜底三分支 | ❌ 零覆盖 | |
| 并发首发（`uk_conversation_pair` 冲突） | ❌ 零覆盖 | |
| panic / 过载 / 熔断 | ❌ 零覆盖且 RAG 侧**无对应机制** | ⇒ V-016 |
| 双端口对拍（12 档） | ❌ 脚本就位、**从未执行** | ⇒ `UNRESOLVED-MSG-003` |

---

## 9. Unresolved（§37 —— 本节不得为空才允许下结论）

| 编号 | 问题 | 为何无法判定 | 需要什么 |
|---|---|---|---|
| **UNRESOLVED-MSG-003**（沿用，**复核仍成立**） | `tools/difftest` 的 message 域 12 档对拍是否真的一致 | 脚本与 runner 都在，但**没有任何结果产物**；且两侧栈要同时起 | 起双栈跑 `./tools/difftest/exp-difftest-grpc-errors-message.sh`，留结果产物 |
| **UNRESOLVED-MSG-004**（沿用，**复核仍成立**） | 三项「起不来」缺陷是否有**点名式**防回退 | 现存用例只间接锁住「上下文能起」 | 拆一条 `MessageContextWiringTest`，分别断言 `@MapperScan` 值、`NacosRegistration` 的 Bean 名、`spring-boot-flyway` 在 classpath |
| **UNRESOLVED-MSG-005**（沿用，**复核后加重**） | 已存在的非 Flyway 建表的库上能否启动 | `V2` 的 `ALTER TABLE … ADD CONSTRAINT` 无 `IF NOT EXISTS`；本轮实测用的是全新空库 | 在一个由 GORM `AutoMigrate` 建过表的库上起一次 message-rpc |
| **UNRESOLVED-MSG-V-001**（沿用，跨轮次） | Nacos namespace/group 不一致 | 需 user/admin 侧同轮次结论 | 两侧 namespace/group 对齐后再判 |
| **UNRESOLVED-MSG-006** 🆕 | RAG 侧 handler 抛 `Error`/panic 时客户端到底看到什么 | grpc-java 的兜底行为本轮**未取证**；RAG 侧也没有 recover 拦截器可比 | 起真 gRPC 端口，让 handler 抛一个非 `RuntimeException` 的 `Error`，用 `grpcurl` 看 `Status.Code` 与 `description` |
| **UNRESOLVED-MSG-007** 🆕 | pgjdbc `sslmode` 默认值对**行为**的实际影响 | 只做了文档层取证，未在**开了 TLS 的 PG** 上跑 | 起一个 `sslmode=require` 的 PG，用当前 JDBC URL 连一次 |
| **UNRESOLVED-MSG-008** 🆕 | `Flyway` 独立账本在**三个模块同库**下真的不撞 | 本轮只证了 **message-rpc 单模块**建出 `flyway_schema_history_message`；`like-rpc` / `article-rpc` 未同时启动 | 三模块同时连同一个 `first_db`，`\dt` 确认三本账 + 各自 3 条 `success` |
| **UNRESOLVED-MSG-009** 🆕 | 时间列 `TIMESTAMP`（非 `TIMESTAMPTZ`）在**多实例/多时区**下是否仍自洽 | 读侧修法的前提是「一个库只由同一时区 JVM 写」（§6.7.10 已留档） | 补一条 `V4` 迁移 + 双实例时区对拍 |

---

## 10. 最终判定（§39 的 18 项 checklist）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 两边 public behavior 是否一致 | ✅ 9 个 RPC 方法数/名/序一一对应 |
| 2 | 正常路径是否一致 | ⚠️ 正常路径一致；**异常输入路径不一致**（负 offset，见 V-014） |
| 3 | 边界输入是否一致 | ❌ **负 `offset` 不一致**（V-014） |
| 4 | NotFound / Empty / Null 是否一致 | ✅ 会话/通知不存在、extra 为空、时间为 null 三态逐条一致 |
| 5 | Error meaning 是否一致 | ✅ 业务错误码 21/21 一致；❌ DB 故障码已签字不一致（V-003） |
| 6 | Error propagation 是否一致 | ⚠️ 业务错误一致；DB 故障的传播形态已签字不同（V-003）；panic 传播 `UNRESOLVED-MSG-006` |
| 7 | External dependency failure 是否一致 | ⚠️ user/admin 失败兜底一致；**下游故障时两侧都无超时兜底**（共同缺陷，非漂移） |
| 8 | Cache hit / miss 是否一致 | N/A（本组件无缓存） |
| 9 | Transaction boundary 是否一致 | ✅ 保护的操作集合与语句序都一致（真 PG 8 条 + 历史变异测试） |
| 10 | State transition 是否一致 | ✅ 会话状态机 + `canSend` 逐条一致 |
| 11 | Side effects 是否一致 | ❌ **Go 多三层防护副作用**（recover / breaker / shedding），见 V-016 |
| 12 | Ordering 是否一致 | ✅ 三处排序键双侧一致（V-004 / V-005 已修） |
| 13 | Retry 是否一致 | ✅ 两侧都无重试 |
| 14 | Timeout scope 是否一致 | ✅ 2s、整个 handler、单一执行者 |
| 15 | Concurrency semantics 是否一致 | ✅ 都靠 DB 唯一索引 + 事务，无应用层锁 |
| 16 | Authentication / Authorization 是否一致 | ✅ 两侧都**不做**鉴权（真源设计，非遗漏） |
| 17 | Serialization semantics 是否一致 | ⚠️ 结果一致；但 `#{isRead}` 靠字段反射兜住，脆弱（V-017） |
| 18 | Lifecycle 是否一致 | ❌ reflection 暴露面不对称（V-015）；服务发现后端不同（etcd vs Nacos，历史 UNRESOLVED-MSG-V-001） |

### **最终判定：`SEMANTICALLY NOT EQUIVALENT`**

**理由（三条各自独立成立）**

1. **存在会改变行为的差异**：`DRIFT-MSG-V-014`（负 `offset` ⇒ Go 成功返回第一页 / RAG 报 5001 INTERNAL）。
   按 §39 的判定规则，只要存在一条会改变行为的差异即判 `NOT EQUIVALENT`。
2. **存在会改变系统应如何行为的差异**：`DRIFT-MSG-V-016` —— 真源在过载/崩溃时有明确的降级契约
   （`RESOURCE_EXHAUSTED` / `UNAVAILABLE` / `INTERNAL` + 进程存活），RAG 侧**没有任何对应机制**。
   这属于「系统应该如何行为」而非「实现风格」。
3. **证据不足以判 EQUIVALENT**：双端口活体对拍从未执行（`UNRESOLVED-MSG-003`），
   `UNRESOLVED-MSG-004/005/006/007/008/009` 六项未关闭。按 §39「证据不足 ⇒ UNRESOLVED」的规则，
   本轮也**不能**下 `UNRESOLVED` 结论 —— 因为 §7 的两条判定依据是**已取证的静态 + 活体探针**，
   不是猜测。

**本轮对「能否真的启动并生效」的直接回答**

| 问题 | 结论 | 证据强度 |
|---|---|---|
| 上下文能不能起来 | ✅ **能**。`@SpringBootTest(classes = MessageRpcApplication.class)` + 真 PG 容器，39 例全绿 | **真 PG（本轮自跑）** |
| Mapper 能不能注册 | ✅ 能（`@MapperScan` 在 `MessageRpcApplication.java:66`，否则上下文起不来） | 真 PG |
| 迁移会不会执行 | ✅ 会，且账本表名按配置建出 `flyway_schema_history_message` | 真 PG（启动日志原文） |
| gRPC 拦截器会不会生效 | ✅ 会挂上（`GrpcServerConfigurer` + starter 会 apply Configurer —— 后半句属 starter 契约，`UNRESOLVED-MSG-002` 仍未做端到端对拍） | 结构断言 + 单测 |
| **生产形态（真实 Nacos / 真实 etcd 对端 / 真实 PG）** | ⚠️ **未验证**。测试用的全是 `static://127.0.0.1:1` + `server-addr=127.0.0.1:1` 覆盖 | — |
| `@Import` 归一化 | ✅ **已完成**（与 `AGENTS.md §6.3 问题 3` 的记载相反，该条需由持有者修正） | 静态源码 |
| Flyway 账本**单模块运行时** | ✅ **已确证生效**（关闭该项 UNKNOWN） | 真 PG 启动日志 |
| Flyway 账本**三模块同库** | ⚠️ 未验证 ⇒ `UNRESOLVED-MSG-008` | — |

---

<!-- @fix 2026-10-23 第二轮：DRIFT-MSG-V-014 + DRIFT-MSG-V-017 已修 -->

## 2026-10-23 第二轮：DRIFT-MSG-V-014 / -017 已修

> message-rpc 当前：**49 tests / 0 failures / 0 errors**，BUILD SUCCESS（原 39 + 分页 7 + isRead 3）。

### ✅ FIX-MSG-014 —— 负 `offset` 不再变成 `OFFSET must not be negative`

**Go 出处（消费点，三处逐字同构）**：

```go
// internal/model/message.go:59-64 / :127-131 / :164-168
if offset > 0 { db = db.Offset(int(offset)) }   ← offset = -1 ⇒ 整段被跳过
if limit  > 0 { db = db.Limit(int(limit))   }   ← limit  =  0 ⇒ 整段被跳过
```

修复前三个 Mapper 都是 `w.last("LIMIT " + limit + " OFFSET " + offset)` **无条件拼接**
⇒ `OFFSET -1` ⇒ PG 报 `OFFSET must not be negative`
⇒ 被 `MessageRpcServiceImpl` 兜底成 `INTERNAL` + "internal server error" ⇒ 客户端拿 **5001**。

**危害不在返回值错，而在分类错了**：参数非法被降级成基础设施故障，监控会记成 DB 故障。

**修法**：新增 `mapper/GoStylePagination.limitOffset(limit, offset)` 统一生成尾子句，
三个 Mapper 改为 `if (!tail.isEmpty()) w.last(tail)`。

⚠️ **`limit <= 0` 时 Go 是「不加 LIMIT」⇒ 不限行数，不是「LIMIT 0 返回空页」**。
这看着像缺陷，但它是 Go 的实际行为，照原样复刻（§3.2 不「顺手改好」）。

**安全边界**：`limit`/`offset` 都是原始 `int`，字符串拼接**类型安全**，**不构成 SQL 注入面**。
已在 helper 的 javadoc 里写明：若将来把签名改成 `String`，这一行就从「类型安全」变成「注入面」。

**未改的一处（刻意）**：`ConversationMessageMapper#listByConversation`（带 `beforeMessageId` cursor）
在 Go 侧**无对应函数**，且**当前零调用方**（死代码）⇒ 不改（改了就是「顺手改好」）。

**证据**：`MessagePaginationNegativeOffsetWiringTest`（7/7 绿，真 PG）：

- 3 条路径各一条负 offset 用例（Notification / Conversation / …）
- `limit=0`、`limit=-1` 两条：**不加 LIMIT ⇒ 返回全部 5 行**，而不是「LIMIT 0 返回空页」
- `normalPaginationUnchanged` 对照组：`limit=2 offset=1` ⇒ 顺序逐条一致
- **`allNegativeCombinationsAcrossAllThreePaths`**：**守恒断言**，7 组负值/正常值组合
  逐组断言「行数 == Go 语义的可执行表述」（§3.3.3「凡是脚本批量改 N 处，
  必须配一条覆盖全部 N 处的守恒断言」）
- `negativeOffsetIsNotRejectedAsInvalidArgument` —— **显式钉住「不改成 INVALID_ARGUMENT」**：
  本模块**没有**参数校验出口，加一个就是新增 Go 不存在的拒绝路径（§3.2）

### ✅ FIX-MSG-017 —— `isRead` 字段名（潜伏缺陷，今天无可观察差异）

**⚠️ 先说清楚性质**：`markAllReadByRecipient` **无调用方**，生产路径走的是手写
`@Update` 的 `markReadByConversationAndRecipient` ⇒ 本条修复**不改变今天任何可观察行为**，
只是拆掉「一旦有人接线就抛 `can not find lambda cache for this property [read]`」那颗雷。

**修法照抄同模块 `NotificationEntity`（2026-10-21 已付过运行期代价）**：
字段 `isRead` → `read` + `@TableField("is_read")`，**getter/setter 签名保持
`isRead()` / `setRead(boolean)` 不变**（改签名会波及 `MessageRpcSupport:130/149`，
与本缺陷无关）。

#### 🔴 连带修复：手写 `@Insert` 里的 `#{isRead}` **必须一起改**（本轮实测踩到）

改完字段名后，**5 条既有测试立刻红**：
```
ReflectionException: There is no getter for property named 'isRead'
  in ConversationMessageMapper.insertMsg-Inline
```

根因：**手写 `@Insert` 的 `#{…}` 走 OGNL，按 JavaBeans 规则解析属性名** ——
`isRead()` 这个 getter 对应的属性是 `read`，属性 `isRead` 并不存在。
`callbacks/create.go` 那套 `TableInfo.propertyMap` 完全不参与。

⇒ 同一个字段名，**两套解析规则**：

| 写法 | 解析依据 | 本例取值 |
|---|---|---|
| `ConversationMessageEntity::isRead`（MP lambda） | `TableInfo.propertyMap`，key = **字段名** | 字段必须是 `read` |
| `#{isRead}`（手写 `@Insert` OGNL） | JavaBeans **属性名**（由 getter 定） | 必须写 `#{read}` |

⇒ **改字段名必须分别验证这两条路**，改一处不保证另一处还对。
`insertMsg` 的占位符已改为 `#{read}`（`ConversationMessageMapper:152`）。

**证据**：`MessageIsReadLambdaWiringTest`（3/3 绿，真 PG）——
**主动调用那个死方法**，让 lambda 在真 PG 上真跑一遍（不执行就永远不会暴露这类纯静态缺陷）：

- `markAllReadByRecipientLambdaResolves` —— 不抛异常 + 恰好命中 3 行
- `lambdaResolvesToSnakeCaseColumn` —— 断言解析出的**列名**是 `is_read`；
  顺带显式钉住「属性名 `read` / getter 名 `isRead`」这对不一致正是缺陷成因本身
- `readPathMapsIsRead` —— 对照：读路径 `getIsRead()` 能正确还原（不是恒 false）

### 元教训（值得单列）

1. **同一个字段名，MP lambda 与手写 OGNL 是两套解析规则。** 改字段名时，
   「跑一下全量测试」是唯一能同时覆盖两者的办法——
   本轮若只跑那个新写的守门测试，5 条既有测试的红色就看不见了。
2. **「潜伏缺陷」不等于「不用修」，但也不等于「修了有收益」。**
   本条的收益是拆雷，**必须如实写明今天无可观察差异**，否则会被当成行为修复来汇报。

---

<!-- @fix 2026-10-23 22:5x–23:0x 第四轮 · V-015 / V-019 已修 · 新增 V-020~V-023（双侧活体 DDL 对拍） · 基线 8422116 -->

## 2026-10-23 第四轮：配置侧两条关闭 + **首次双侧活体 DDL 对拍**

> **核对时间**：2026-10-23 22:45–23:00　**基线 commit**：`8422116`（`align/message` 工作树）
> **本轮新增的证据形态**：**Go GORM AutoMigrate 真跑一次** vs **RAG 三个 Flyway 迁移真跑一次**，
> 同一个 `postgres:16-alpine`，逐行 diff（列类型/可空/默认/索引/约束）。
> ⚠️ 这是 message 域**第一次**拿到 DDL 的活体证据 ——
> AGENTS.md §0 一直记着「索引 / 唯一约束 / NOT NULL / DEFAULT **完全 NOT CHECKED**」，
> 而 message 域之前连 `tools/difftest/check-ddl-static.py` 都跑不了
> （它要求一个已装好 Go 侧 schema 的 `difftest-postgres` 容器，而 Go 真源起不来，依赖 `etcd:2379`）。

### 探针做法（可复现）

不需要起整个 Go 服务 —— 真源的建表行为全部来自
`service/message/rpc/internal/model/db.go:42` 的
`db.AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})`。
⇒ 用**真源同一个** `go.mod`（`gorm.io/driver/postgres v1.6.0` / `gorm.io/gorm v1.31.1`）写一个最小程序，
struct 与 `data_model.go` 逐字段逐 tag 一致，对真 PG 跑一次 AutoMigrate，
再用 `information_schema.columns` + `pg_indexes` + `pg_constraint` 三张系统表把 schema 打出。

### ✅ FIX-MSG-V-015 —— reflection 暴露面（配置层已对齐 + 装配层已验）

| | Go | RAG（修复前） |
|---|---|---|
| 判定点 | `message.go:40-42`：`if c.Mode == service.DevMode \|\| c.Mode == service.TestMode` | 无判定，直接用 starter 默认 |
| 取值来源 | `zrpc.RpcServerConf.Mode` 的 tag `json:",default=pro,…"`（go-zero@v1.10.2 `core/service/serviceconf.go:35`） | `GrpcServerProperties.reflectionServiceEnabled` |
| 实际值 | `etc/message.yaml` **未配 Mode** ⇒ `pro` ⇒ **不注册** | starter metadata `defaultValue=True` ⇒ **注册** |

⇒ `application.yml` 显式加 `grpc.server.reflection-service-enabled: false`，
并**删掉那句错误的注释**（「Java 端由 devh starter 默认注册 reflection」把 starter 默认值当成了与 Go 一致）。

**证据**（新增 `MessageGrpcReflectionExposureWiringTest`，**真 Spring 上下文 + 真 PG 容器**）：

1. `GrpcServerProperties#isReflectionServiceEnabled() == false` —— 证明 yml 那行**真的绑定到了配置对象**；
2. `Environment#getProperty("grpc.server.reflection-service-enabled") == "false"` ——
   证明这一行**确实被解析**，而不是「starter 恰好不是默认值」凑巧关掉的。

⚠️ **证据边界（§6.2 约定 2）**：「reflection 服务真的没被注册进 ServerBuilder」属于 starter 内部行为，
需起真端口 + `grpcurl list` 才能证 ⇒ **本轮未做端到端验证**。
⇒ 本条结论读作「**配置层 + 绑定层已对齐，端到端未验**」。

### ✅ FIX-MSG-V-019 —— PostgreSQL 连接参数（并**新挖出一条台账没记的**）

Go DSN（`db.go:21-29`）实际是：

```go
"host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai"
//                                  ↑ 来自 etc/message.yaml:34 的 Mode: "disable"
//                                                        ↑ 来自 db.go:22 字面量
```

| 参数 | 修复前 RAG | 本轮修复后 |
|---|---|---|
| `sslmode` | URL 里没有 ⇒ pgjdbc 默认 `prefer`（先试 TLS 再回落明文） | `?sslmode=disable`（**永不尝试 TLS**，与 Go 同） |
| `TimeZone` | 🔴 **完全没有** | `options=-c%20TimeZone%3DAsia%2FShanghai` |

⚠️ **`TimeZone` 是本轮新发现的**，原 V-019 只写了 `sslmode`。
落地方式不能写 `?TimeZone=Asia/Shanghai` —— **pgjdbc 没有这个连接属性**
（实测 `PGProperty` 里只有 `OPTIONS`/`ASSUME_MIN_SERVER_VERSION` 等，无 TimeZone），
必须走 `options=-c …` 透传。
**为什么它要紧**：本域时间列当前是 `TIMESTAMP`（**无时区**，见 V-020），
会话时区直接决定读出来的是哪一刻 ⇒ Go 把会话钉在 Asia/Shanghai，RAG 却吃 PG 服务器默认值。

### ✅ V-020 —— **8 个时间列：`timestamptz` vs `timestamp`（外加 NOT NULL 与 DEFAULT 也不同）** · **2026-10-23 已修**，详见下方「第五轮」；残留：6 列 `DEFAULT CURRENT_TIMESTAMP`（刻意保留）

**Go 侧证据（消费点）**：`gorm.io/driver/postgres@v1.6.0/postgres.go:251-255`

```go
case schema.Time:
    if field.Precision > 0 { return fmt.Sprintf("timestamptz(%d)", field.Precision) }
    return "timestamptz"          // ← 真源 go.mod:27 钉的就是 v1.6.0
```

⇒ Go 的 `time.Time` 字段**全部**建成 `timestamp with time zone`。

**双侧活体 diff（`postgres:16-alpine`，同一镜像）**：

| 列 | Go | RAG |
|---|---|---|
| `message_notification.read_at` | `with time zone` / 可空 | `without time zone` / 可空 |
| `message_notification.created_at` | `with time zone` / **可空** / 无默认 | `without time zone` / **`NOT NULL`** / **`DEFAULT CURRENT_TIMESTAMP`** |
| `message_notification.updated_at` | `with time zone` / **可空** / 无默认 | 同上 |
| `message_conversation.last_message_at` | `with time zone` / NOT NULL / 无默认 | `without time zone` / NOT NULL / **`DEFAULT CURRENT_TIMESTAMP`** |
| `message_conversation.created_at` / `updated_at` | `with time zone` / **可空** / 无默认 | `without time zone` / NOT NULL / DEFAULT |
| `message_conversation_message.read_at` | `with time zone` / 可空 | `without time zone` / 可空 |
| `message_conversation_message.created_at` | `with time zone` / **可空** / 无默认 | `without time zone` / NOT NULL / DEFAULT |

⚠️ **三处不同，不止类型**：`类型`（timestamptz / timestamp）+ `可空性`（Go 的 created_at 可空！RAG 是 NOT NULL）
+ `默认值`（Go 没有，RAG 有 `CURRENT_TIMESTAMP`）。
这三者**互相咬合**：改成 `timestamptz` 后，读路径的 epoch 换算必须同步，
否则会**多修一次错**（§6.7.10 记的「读侧修法的前提是一个库只由同一时区 JVM 写」）。
⇒ **2026-10-23 已按 users 域规格修复**（用户拍板「照 users 域规格单开，配真 PG 双侧对拍」）：
新增 `V5__message_time_columns_timestamptz.sql` + 实体 8 字段 `LocalDateTime⇒OffsetDateTime`
+ 读路径去掉 `systemDefault()` 假设 + 真 PG 守门。详见下方「第五轮」一节。
⚠️ **口径更正**：本节初稿写的是「9 个时间列」，**实测是 8 列**
（`message_notification` 3 + `message_conversation` 3 + `message_conversation_message` 2）。
同口径更正：`DROP NOT NULL` 的是 **5** 列，不是 6；`DEFAULT` 残差是 **6** 列，不是 7。

### 🔴 V-021 —— 两个索引的**名字**不同（列相同）· **仍 OPEN**，本轮未改

```
Go : idx_message_conversation_last_message_at ON (last_message_at)
RAG: idx_conversation_last_message_at        ON (last_message_at)
Go : idx_message_conversation_updated_at      ON (updated_at)
RAG: idx_conversation_updated_at             ON (updated_at)
```

根因：Go 的 `LastMessageAt` / `UpdatedAt` 用的是**无名** `index` tag，GORM 按
`idx_<表名>_<列名>` 生成 ⇒ 表名 `message_conversation` 被拼进去；
RAG 手写迁移时按「读起来更短」命名。
⇒ 行为等价（同一列的等价索引），但 `pg_indexes` / `\d` / 任何引用索引名的运维脚本都会看到差别。
**本轮只登记，未改**（改名 = `DROP INDEX` + `CREATE INDEX`，需评估线上是否有对象依赖旧名）。

### ✅ V-022 —— `idx_notification_recipient_read` 的**列不同**（Go 只有 1 列）· **已签字**，见 `SD-2026-10-23-06`

```
Go : idx_notification_recipient_read ON (is_read)                     ← 1 列
RAG: idx_notification_recipient_read ON (recipient_id, is_read)        ← 2 列
```

根因：Go 的 tag 里 `recipient_id` 挂的是**另一个**索引
（`index:idx_notification_recipient_time,priority:1`），
而 `is_read` 用 `index:idx_notification_recipient_read,priority:2` ——
该索引**没有 priority:1 的成员**，GORM 就只建一列。
RAG 按索引名的字面意图（"recipient + read"）建了两列。

⚠️ **这一条要当心**：RAG 的版本其实**更贴合** `ListNotifications` 的
`WHERE recipient_id=? AND is_read=false` 过滤；但按 §3.1 真源是唯一基准，这仍是漂移。
（同域的 `idx_message_recipient_read` 两侧都是 3 列，**一致**。）

✅ **2026-10-23 用户已拍板：保留 RAG 现状，不回改。**
⇒ 本条**已从 DRIFT 转入签字档**：
[`SD-2026-10-23-06`](../../Intentional_Deviations/message/rpc.md)。
**本文档保留原始差异记录，不因签字而删除**（与 `_cross/signed-deviations.md` 的处置方式一致）。
⚠️ 读法变更：**看到这条 DDL 对拍为红时，先确认是不是 SD-06，再下结论。**

### ✅ FIX-MSG-V-023 —— `uk_conversation_pair`：约束 ⇒ 索引，并顺带关掉 `UNRESOLVED-MSG-005`

**双侧活体证据**：

```
Go   pg_indexes  : CREATE UNIQUE INDEX uk_conversation_pair ON public.message_conversation
                   USING btree (participant1_role, participant1_id, participant2_role, participant2_id)
     pg_constraint: 无 uk_conversation_pair 行
RAG  pg_indexes  : （无独立行，被约束背着的索引不单列）
     pg_constraint: message_conversation|uk_conversation_pair|u|UNIQUE (…)      ← 唯一**约束**
```

⇒ Go 的 `uniqueIndex:` tag 建的是**唯一索引**，RAG 的 `ADD CONSTRAINT … UNIQUE` 建的是**唯一约束**。

**顺带证伪了台账里的一条修法方向**：原 `UNRESOLVED-MSG-005` 建议「给 `V2` 的
`ALTER TABLE … ADD CONSTRAINT` 加 `IF NOT EXISTS`」——
🔴 **PostgreSQL 16 不支持这个语法**（真容器实测：`ERROR: syntax error at or near "NOT"`）。
所以那条路是**死的**，只能改成 `CREATE UNIQUE INDEX IF NOT EXISTS`（PG 支持）。

⇒ 新增 `V4__conversation_pair_unique_index.sql`：
`ALTER TABLE … DROP CONSTRAINT IF EXISTS` + `CREATE UNIQUE INDEX IF NOT EXISTS`。
**没有改 V2 的正文** —— 改已应用迁移会撞 Flyway 校验和（§6.3 决策 1 的推论）。

**幂等性实测**：V1→V2→V3→V4 连跑两遍，**8 次执行全部 `exit=0`**，
且第二遍跑完后 schema 与第一遍一致（V4 把 V2 重建的约束又降回索引）。

⚠️ **诚实边界**：这**不能**说「V2 的非幂等被修好了」——
Flyway 本身每个迁移只跑一次，生产上不会重放。
本条真正修掉的是：(a) 对象类型与 Go 一致；(b) **人工重放 / 外部工具建过同名列时**不再炸。
`UNRESOLVED-MSG-005` 的原始问题（「已被 GORM AutoMigrate 建过表的库上能否启动」）
仍然只在「外部工具恰好用了同名**约束**」时才成立；GORM 建的是**索引**，
所以 V2 的 `ADD CONSTRAINT` 在 GORM 建过的库上**本来就能跑**。

### 仍然开放（本轮核实，未改）

| 编号 | 状态 | 本轮核实结论 |
|---|---|---|
| `DRIFT-MSG-V-016`（go-zero 三个 unary 拦截器：recover / breaker / CPU shedding 全缺） | 🔴 **OPEN** | 确认仍缺。`MessageGrpcInterceptorWiringTest` 验的是 `DRIFT-MSG-003` 的**观测**拦截器（超时/trace），**不是**这三个。三者各自的 Go 出处与默认值已在台账 §7 列全（`zrpc/internal/config.go:21/25`、`zrpc/config.go:47`）。**属架构级新增（要给纯 gRPC 服务补三件套框架能力），不在本轮「对齐既有行为」的范围内**，留待下一轮单独决策 |
| `DRIFT-MSG-V-017`（`isRead` 字段名） | ✅ 已修（上一轮），**本轮复核仍绿** | `MessageIsReadLambdaWiringTest` 3/3 绿、skipped=0 |
| `DRIFT-MSG-V-018`（`ListConversations` 少一次 `count_unread` 埋点） | 🔴 **OPEN** | 确认仍缺。**副作用已加重**：本轮把时间列与会话时区都摊开（V-019/V-020）后可以确定，该路径当前**不可达**（列表已按参与者过滤，`peerOf` 不会失败），所以它今天只表现为「一条永远不会增长的指标」。修它的收益低于风险，**建议与 V-016 一起决策** |
| `UNRESOLVED-MSG-004`（三项「起不来」缺陷无点名式防回退） | 🔴 仍成立 | 本轮新增的 `MessageGrpcReflectionExposureWiringTest` 是**配置绑定**层的点名式用例，可作为该条「点名式守门」的样板；`@MapperScan` / `NacosRegistration` Bean 名两项目前仍未点名 |

### 🟡 附带观察（不改，只记）

`MessageTransactionWiringTest` 带 `@EnabledIfDockerAvailable` ——
即 `docs/DRIFT/_cross/component-coverage-matrix.md` 点名的那个反模式。
**本模块不受其害**（`message-rpc` 是有 `RYUK_DISABLED` surefire 注入的模块之一，
本轮实测 `skipped=0`），但该注解**理应删掉**：它唯一的作用是把两类失败压成同一个
`Docker is not available`，而 message-rpc 并不需要它。
**本轮未删**（它现在无害，删它属于清理而非对齐，且会让本轮的测试计数变化混在语义修复里）。

### 本轮实测数据

```
message-rpc：51 tests（原 49 + 新增 2）/ 0 failures / 0 errors / 0 skipped   BUILD SUCCESS
              ← skipped=0 是硬要求：§状态更正#5 规定「绿灯必须额外断言 skipped 数 = 0」
message-api：84 tests / 0 failures / 0 errors / 0 skipped
message-common：21 tests（本轮从 0 建起）/ 0 failures / 0 errors / 0 skipped
semantic-probe：56 例 → 一致 54，不一致 0，已签字偏离 2                      EXIT=0
DDL 对拍：Go AutoMigrate 55 行 vs RAG V1~V4，**归一化后剩余 23 行差异**
          （= V-020 时间列 8 列 × 3 维 + V-021 索引名 2 + V-022 索引列 1 + 表头/格式噪声）
```

---

<!-- @fix 2026-10-23 23:2x 第五轮 · V-020 已修（照 users 域规格） · V-016 按用户指示推迟 -->

## 2026-10-23 第五轮：`DRIFT-MSG-V-020` 已修（照 users 域规格单开）

> **用户指示**：「v020 照 users 域的规格单开，配真 PG 双侧对拍，别混在语义修复里」
> ＋「先把 v020 解决再来探讨 v016」
> **规格来源**：`docs/DRIFT/users/_shared.md:718-724`（users 域同款处置），本轮**照抄那一轮的规格**，未重新设计。

### 改了什么（三处必须同提交，否则更糟 —— users 域的原话）

| # | 改了什么 | 文件 | 守门 |
|---|---|---|---|
| 1 | 新增 `V5`：8 列 `TYPE TIMESTAMPTZ USING … AT TIME ZONE 'Asia/Shanghai'` + 5 列 `DROP NOT NULL` | `message-rpc/…/db/migration/V5__message_time_columns_timestamptz.sql` | `MessageRpcTimeColumnParityTest`（真 PG 查 `information_schema`） |
| 2 | 实体 **8 个字段** `LocalDateTime ⇒ OffsetDateTime` | `NotificationEntity` / `ConversationEntity` / `ConversationMessageEntity` | 同上（反射断言声明类型） |
| 3 | 读路径去掉「猜时区」：`toEpochSecond(LocalDateTime)` → `toEpochSecond(OffsetDateTime)` | `MessageRpcSupport` | `MessageRpcSupportTimeZoneTest`（7 例，含**时区无关性**断言） |

**⚠️ 为什么是 `V5` 而不是改 `V1`~`V3`**：message 用 **Flyway**，改已应用迁移会撞校验和
（users 域那些是 `SchemaInitializer` 重放文件，可以原地改 —— **外形像不等于机制像**）。
新鲜库照样是「V1~V3 建 TIMESTAMP → V5 立刻转成 TIMESTAMPTZ」，终态一致。

**⚠️ 照抄 users 规格时必须换掉时区参数**：users 用 `'UTC'`，**message 必须用 `'Asia/Shanghai'`**。
转换语义 = 「把**当初写入时用的那个时区**下的墙钟重新解释成瞬时」，
message 的写入侧是 `LocalDateTime.now()`（JVM 本地墙钟）、部署时区 = Asia/Shanghai、
Go DSN 也钉 `Asia/Shanghai`（`db.go:22`）⇒ 只能按 `Asia/Shanghai` 解释。

**⚠️ users 踩过的坑一并继承**：裸 `ALTER` 在重放时**会累积平移整列**
（`timestamptz --AT TIME ZONE 'X'--> timestamp --按会话 TZ 铸回--> 偏移`），
所以必须 `SET TIME ZONE 'Asia/Shanghai'; … RESET TIME ZONE;` 包夹，使两条路径都恒等。

### 真正买到的东西

```
修复前：TIMESTAMP（无时区墙钟）→ 必须**猜一个时区**才能算瞬时 → 多出一条不变量
        「同一个库只能由同一时区的 JVM 写读」
修复后：TIMESTAMPTZ → 值自带 offset → OffsetDateTime.toEpochSecond() 直接给瞬时
        → **该不变量消失**，与 Go 的 time.Time.Unix() 是同一语义
```

⇒ `MessageRpcSupportTimeZoneTest` 的断言因此**被整体重写**（不是跟着改）：
旧断言写的是「墙钟必须按 JVM 本地时区解释」（那是给「猜」这个动作写守门），
新断言是**更强**的性质 —— **把 JVM 默认时区换成 UTC / Asia/Shanghai / America/New_York，
epoch 必须完全相同**，并附一条反事实用例证明「把墙钟当 UTC」会差整整 8 小时
（符号方向与 `DRIFT-MSG-V-007` 记录的「RAG 比 Go 晚 8 小时」同向）。

### 残留（刻意保留，非静默放过）

| 残差 | 数量 | 为什么不动 |
|---|---|---|
| `DEFAULT CURRENT_TIMESTAMP` | **6 列** | Go 侧无默认。删除只对「省略该列的 INSERT」有意义，而两侧 INSERT 都**显式绑定**这些列（`ConversationMapper#insertConv` 的 VALUES 列表）⇒ 默认永不触发。为一个不可观测维度冒「有人依赖了裸 SQL 默认」的风险不划算 |

### 不可逆的信息损失（必须知情）

历史 `timestamp` 值**不携带时区**，转换只能按「当初写入时的 JVM 时区 = Asia/Shanghai」这一个假设解释。
若生产库中存在**其它时区写入**的墙钟，那部分**事后无法分辨**，转换结果必然错。
本轮无法从库内区分（数据里没有这个信息）⇒ 若该风险存在，需人工核对。

### 🔴 本轮自己踩的两个坑（都是测试代码的错，不是实现的错）

1. **`OffsetDateTime.equals()` 比的是「瞬时 **且** offset」**，不是瞬时。
   刻意写 `+08:00`、连接会话时区是 UTC ⇒ 读回必然是 `Z` 表示 ⇒ `equals` 必假。
   ⇒ 判瞬时必须用 `isEqual()`。
2. **列数数错**：初稿按「9 列」写，实测是 **8 列**；连带 `DROP NOT NULL` 是 **5** 列不是 6、
   `DEFAULT` 残差是 **6** 列不是 7。台账四处口径已一并订正。
   ⚠️ 这正是 §6.2 约定 2 的价值：**没有核对日期/实测口径的断言一律按 `UNKNOWN` 处理**。

### 实测数据

```
双侧活体 DDL 对拍（Go AutoMigrate vs RAG V1~V5）：
  归一化差异 23 行 → **19 行**；剩余 = 6 列 DEFAULT（刻意保留）+ 2 个索引名（V-021）
                      + 1 个索引列（V-022，已签字）
  **类型与可空性：完全一致（0 差异）**
迁移幂等：V1~V5 连跑两遍，10 次执行全部 exit=0
message-rpc：54 tests（原 52 + 新增 2 条真 PG 列级守门）/ 0 failures / 0 errors / 0 skipped
```

### 按用户指示推迟：`DRIFT-MSG-V-016`

「先把 v020 解决再来探讨 v016」⇒ 本轮**未动** V-016（三个 go-zero 框架拦截器）。
其分析、Go 出处与三条拆解建议已备好，等下一轮单独决策。
