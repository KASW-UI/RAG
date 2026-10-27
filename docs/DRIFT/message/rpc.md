# `service/message/message-rpc` 组件级语义对照台账

> 🟥 **当前态指针（2026-10-25 台账校准轮回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（头部「本轮（第三轮）元信息」停在第三轮，而正文已到**第十轮**（双侧活体 DDL 对拍）+ 2026-10-25 的 `V-030` 关闭）⇒ **当前状态一律以文件末尾「2026-10-24 第十轮（双侧活体 DDL 对拍）+ 末节 `DRIFT-MSG-V-030` 已关闭（2026-10-25）」一节为准**。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**

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
Go:  zrpc RpcServerConf.Timeout 未配 → go-zero 默认 2000ms（go-zero@v1.10.2/zrpc/config.go:46）→ 服务端强制 deadline
Java: application.yml:208  observability.rpc-timeout: 2s          ← 配了
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
| **错误码与文案表** | 20 个业务码（200/500/1015/5001-5008/5101-5102/5201-5207）+ 20 条文案 1:1；`msg→code` 反查表排除 200/500 的逻辑一致；未知码 fallback `"FAIL"` 一致 |
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

> ⚠️ **2026-10-24 第十一轮 · 整表已被推翻** —— 下面 5 行是 **2026-10-23 第四轮**的缺口清单，
> **五项现已全部关闭** ⇒ 这张表整体只作历史留档，**当前状态见 §9 的 Unresolved 表**。

| # | 缺口 | 影响 |
|---|---|---|
| **UNRESOLVED-MSG-001** ✅ 已关闭（第十一轮） | JVM 运行时区未知 | ⇒ 已被 `V-019`（JDBC URL 钉 `TimeZone=Asia/Shanghai`）+ `V-020`（8 列改 `TIMESTAMPTZ`）+ 第十轮活体对拍（两侧 `data_type` 0 差异）取代 |
| **UNRESOLVED-MSG-002** ✅ 已关闭（第十一轮） | `ServerInterceptor` 挂载只有 `verify(builder).intercept(...)` 这层证据 | ⇒ 第八轮真双栈 12 档对拍（EXIT=0）提供端到端证据；第九轮 8 个 rpc 组件的装配层断言补齐 |
| **UNRESOLVED-MSG-003** ✅ 已关闭（第八轮） | 双端口对拍的 12 档**尚未实际执行过** | ⇒ 真起两套栈跑过，EXIT=0，产物 `tools/difftest/message-grpc-results.txt` |
| **UNRESOLVED-MSG-004** ✅ 已关闭（第九轮） | DRIFT-MSG-004/005/006 未各配独立防回退用例 | ⇒ 新增 `MessageContextWiringTest`（4 例，**逐条点名**）+ `MessageApiJarExecutableWiringTest`（2 例）⇒ 详见下方 Unresolved 表 |
| **UNRESOLVED-MSG-005** ✅ 已关闭（第四轮，V-023） | `AutoMigrate`（Go 每次启动校验表结构）vs `Flyway`（Java 一次性迁移后不再校验）的存量表漂移 | ⇒ 原问题前提不成立（GORM 建的是索引）；V4 已把约束降回索引，幂等实测 8 次 `exit=0` |

---

#### 6.6.5 ⚠️ 本轮的方法论产出（比发现本身更重要）

三项 P0 **全部位于「注释写得很完整、代码看起来已对齐、实际机制未生效」的位置**：

| 位置 | 注释/配置声称 | 实际 |
|---|---|---|
| `createConversationWithMessageTx:385` | 顶部类注释白纸黑字列出 6 个事务方法「放本类上 `@Transactional`」 | 全部 self-invocation，注解形同虚设 |
| `application.yml:208` | `observability.rpc-timeout: 2s` + 三行注释解释「取 2s 对齐 go-zero 默认」 | 拦截器从未挂上，超时从未执行 |
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
| `RpcServerConf.Timeout` 默认 **2000ms** | `go-zero@v1.10.2/zrpc/config.go:46` `json:",default=2000"`；`etc/message.yaml` 未配该键 ⇒ 消费点在 `go-zero@v1.10.2/core/mapping/unmarshaler.go` 的默认值填充 | ✅ 生效 |
| **但原生 timeout 拦截器被显式关掉** | `service/message/rpc/message.go:34` `DisableNativeRpcTimeout(&c.RpcServerConf)` 把 `conf.Timeout = 0`（`common/observability/rpc.go:19-23`）⇒ `go-zero@v1.10.2/zrpc/server.go:164-167` 的 `if c.Timeout > 0` **不成立**，`UnaryTimeoutInterceptor` 不挂 | ✅ 已确证 |
| 超时由观测拦截器接管 | `message.go:44` `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(2000ms, slowThreshold))`；`rpc.go:38` `context.WithTimeout` → 到点返回 `codes.DeadlineExceeded`（`rpc.go:68-71`） | ✅ |
| **panic 恢复拦截器默认开启** | `go-zero@v1.10.2/zrpc/server.go:131-133` `if c.Middlewares.Recover` ⇒ `UnaryRecoverInterceptor`；默认值 `true` 见 `go-zero@v1.10.2/zrpc/internal/config.go:21` `json:",default=true"`；panic → `codes.Internal` + `"panic: %v"`（`recoverinterceptor.go:39-42`） | ✅ 生效 |
| **服务端熔断默认开启** | `go-zero@v1.10.2/zrpc/server.go:157-159` + `go-zero@v1.10.2/zrpc/internal/config.go:25` `default=true` ⇒ `UnaryBreakerInterceptor`；熔断打开 → `codes.Unavailable`（`breakerinterceptor.go:46-48`） | ✅ 生效 |
| **自适应限流（CPU 阈值）默认开启** | `go-zero@v1.10.2/zrpc/server.go:160-162` `if c.CpuThreshold > 0`；阈值默认 **900** 见 `go-zero@v1.10.2/zrpc/config.go:47` ⇒ `UnarySheddingInterceptor`；拒绝时 `codes.ResourceExhausted`（`sheddinginterceptor.go:30-35`） | ✅ 生效 |
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
| gRPC server | `@GrpcService` + devh starter 自动注册；**唯一 `ServerInterceptor`（观测三件套）经 `GrpcServerConfigurer` 显式挂上** | `MessageRpcApplication.java:115-117` |
| 超时 | `observability.rpc-timeout: 2s`（`application.yml:207-208`）→ `ObservabilityProperties` → `RpcServerInterceptorFactory` → `RpcInterceptor`，到点 `close(DEADLINE_EXCEEDED)` | `application.yml:143-144`；`RpcServerInterceptorFactory.java:41-47`；`RpcInterceptor.java:148-190` |
| MyBatis-Plus 自动配置排序 | Boot 4 把 `DataSourceAutoConfiguration` 挪包 ⇒ MP 的 `@AutoConfigureAfter` 悬空；已用**空类桥**修复并在 imports 文件登记 | `MybatisPlusOrderingBridgeAutoConfiguration.java:49-52`；`META-INF/spring/…AutoConfiguration.imports:1` | ⚠️ **2026-10-27 第十七轮订正**：**该空类桥与本模块的 `imports` 文件已于本轮删除**（域侧收编到共享层）⇒ ⇒ **本行的「已用空类桥修复并在 imports 文件登记」已不是本模块的实现** ⇒ ⇒ 现在由**共享层**那份（`service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` + 共享层 `META-INF/spring/…AutoConfiguration.imports`）承担 ⇒ ⇒ **问题与修法同源，承载者已换** ⇒ ⇒ **原引用行号 `MybatisPlusOrderingBridgeAutoConfiguration.java:49-52` / `…imports:1` 已失效（文件已不存在）⇒ 引用前须重新定位到共享层那份** |
| Flyway | `spring-boot-flyway:4.0.8` 在 classpath（Boot 4 拆模块后必需）；`spring.flyway.table: flyway_schema_history_message` | `pom.xml:166-169`；`application.yml:64-105`（`flyway:` 段：`baseline-version: 0` / `locations` / `table`） |
| Web 容器 | **无** tomcat-embed-core / jetty / undertow / starter-web（仅 `tomcat-embed-el` 表达式库）⇒ **不会起 servlet 容器** | `mvn -pl service/message/message-rpc dependency:tree`（2026-10-23 隔离跑） |

---

## 4. Equivalence Matrix（§24 + §35）

> ⚠️ **2026-10-24 快照声明（重要）**：本节与 §5 是 **2026-10-22 首轮对照的快照**，
> **不是当前状态**。此后有多条漂移已修 / 已签字，**表里对应行的 Result 列已过期**：
>
> | 行 | 快照写的 | **当前事实** |
> |---|---|---|
> | `ListNotifications` / `ListConversations` / `GetConversationMessages` 的「负 `offset`」 | `SEMANTIC MISMATCH` | ✅ **已修**（2026-10-23 第二轮，`GoStylePagination.limitOffset`）⇒ 见 `DRIFT-MSG-V-014` |
> | 会话消息写路径 `is_read` 列绑定 | `EQUIVALENT`（但脆弱） | ✅ **已修**（2026-10-23 第二轮，`isRead` 改名）⇒ 见 `DRIFT-MSG-V-017` |
> | 框架级三拦截器（recover / breaker / shedding） | RAG 侧**全缺** | ✅ **已修**（2026-10-23 recover / 2026-10-24 breaker+shedding）⇒ 见 `DRIFT-MSG-V-016` |
> | 熔断失败判据 | — | 🆕 **新挖出分岔**（Go `UNKNOWN` vs RAG `INTERNAL`）⇒ 见 `DRIFT-MSG-V-024` |
>
> ⇒ **判断某一条的现状，一律回 §7 的台账正文，不要引用本表的 Result 列**（与 `AGENTS.md §0` 同条纪律）。

Result 只用 5 个枚举值。Evidence 列 `[RUNTIME-PG]` = 本轮真 PG；`[RUNTIME-PG-PROBE]` = 本轮裸 `psql` 探针；
`[CODE]` = 静态源码逐行；`[JAVAP]` = 字节码/依赖树实测；`[DOC]` = 对方框架的文档化默认值。

| Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|
| 全部 9 个 | Public surface（方法数/名/序） | 9 | 9 | **EQUIVALENT** | `[CODE]` |
| 全部 9 个 | wire 契约（字段号/类型/枚举/rpc 签名） | 22 msg+enum / 9 rpc | 同 | **EQUIVALENT** | `[CODE]` 归一化 diff 空，双侧计数 22=22、9=9 |
| 全部 9 个 | 错误码与文案表（20 码 / 20 文案） | 一致 | 一致 | **EQUIVALENT** | `[CODE]` `errmsg.go:28-73` ↔ `MessageErrorCode`+`MessageErrorMessages` |
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

> ⚠️ **2026-10-24 快照声明**：本节同为 **2026-10-22 首轮对照的快照**，**不是当前状态**。
> 至少「负 `offset`」那一行已过期（已修，见 §4 的快照声明与 `DRIFT-MSG-V-014`）；
> 且**错误流现在多了三段**（`recover` → `breaker` → `shedding`），见 `DRIFT-MSG-V-016` 与本文件「第七轮」一节。

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

### ✅ DRIFT-MSG-V-014 ✅ **已修（2026-10-23 第二轮）** 负 `offset` 在 RAG 侧变成数据库语法错误

> **⚠️ 2026-10-24 状态更正 #8**：本节正文（下方）描述的是**修复前**的状态。
> 该条已由 2026-10-23 第二轮修复（新增 `mapper/GoStylePagination.limitOffset(limit, offset)` 统一生成尾子句，
> 三个 Mapper 改为 `if (!tail.isEmpty()) w.last(tail)`），守门为 `MessagePaginationNegativeOffsetWiringTest`
> （7 例，真 PG，含 `limit=0` / `limit=-1` 两条「不加 LIMIT ⇒ 返回全部」的用例）⇒ **本条不再是差异**。
> 详见下方「2026-10-23 第二轮：`DRIFT-MSG-V-014` / `-017` 已修」一节。
> ⚠️ **口径提醒**：`limit <= 0` 时 Go 是「不加 LIMIT ⇒ 不限行数」，**不是**「LIMIT 0 返回空页」⇒ 复刻时**刻意保留**（§3.2）。

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

### ✅ DRIFT-MSG-V-015 ✅ **已修（2026-10-23 第四轮）** gRPC server reflection 的暴露面两侧不对称

> **⚠️ 2026-10-24 状态更正**：标题原无状态标记，**实为已修**——
> `application.yml:139` 已钉 `reflection-service-enabled: false`（对齐 Go `pro` 模式下不注册 reflection），
> 且**装配层已验**。详见下方「✅ FIX-MSG-V-015」。

**Component**: message-rpc　**Operation**: 全部（服务级能力）
**Category**: Lifecycle / Attack Surface　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **MEDIUM**（信息暴露 + 运维能力不一致）

```
Go  message.go:40-42
      if c.Mode == service.DevMode || c.Mode == service.TestMode { reflection.Register(grpcServer) }
    Mode 默认值来自 go-zero@v1.10.2/core/service/serviceconf.go:35
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

### ✅ DRIFT-MSG-V-016 ✅ **已修（2026-10-23 recover / 2026-10-24 breaker+shedding）** go-zero 框架级三个 unary 拦截器在 RAG 侧全部缺失

**Component**: message-rpc　**Operation**: 全部
**Category**: Resilience / Error Identity　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **MEDIUM-HIGH**（过载与崩溃时的可观察行为不同）

> **⚠️ 处置状态（2026-10-24 更新）**：
> **① recover 半边 ✅ 已修**（2026-10-23 23:5x，用户指示「做recover」）——
> 新增 `service/common/.../observability/GoStyleRecoverInterceptor.java` +
> `MessageRpcApplication#recoverThen` 显式串链 + `MessageRpcRecoverInterceptorWiringTest`（5 例，**真 in-process gRPC**）。
> **② breaker / CPU shedding ✅ 已修**（2026-10-24）——
> 新增 `GoStyleRpcBreakerInterceptor` / `GoStyleRpcSheddingInterceptor` / `GoStyleRpcFailurePolicy` /
> `GoStyleRpcProtectionChain`（**全在共享模块 `service/common`**），链序 `recover → breaker → shedding → business`。
> ⇒ **本条三个拦截器已全部落地**，不再是 OPEN。
> ⚠️ **但带出两条新的尾巴**：见 [`DRIFT-MSG-V-024`](#3-✅-drift-msg-v-024已修db--未知异常走-unknown与-go-的熔断判据对齐)（DB 异常两侧 status 不同 ⇒ 熔断判定分岔）
> 与 [`DRIFT-MSG-V-026`](#3-✅-drift-msg-v-026-已修8-个-rpc-组件全部接入熔断--降载链)（**截至 2026-10-24 第九轮，其余 8 个带 gRPC server 的 rpc 组件已全部接入**本链；
> 当时只有 `message-rpc` 接入 ⇒ 跨组件不变量未闭合 ⇒ 已在第九轮闭合）。
>
> ⚠️ **2026-10-24 更正**：本节上一版把「其余 9 个 rpc 组件未接入」写成了 `SD-2026-10-23-10` ——
> **该编号不存在**（本域签字档只到 `SD-2026-10-23-08`）⇒ 已改指 `DRIFT-MSG-V-026`。

| 拦截器 | Go 侧装配点（消费点） | 默认值来源 | 拒绝/异常时的可观察行为 | RAG 侧（2026-10-24 后） |
|---|---|---|---|---|
| panic 恢复 | `go-zero@v1.10.2/zrpc/server.go:131-133` | `go-zero@v1.10.2/zrpc/internal/config.go:21` `Recover json:",default=true"` | `INTERNAL` + `"panic: %v"`（`recoverinterceptor.go:39-42`），**进程存活** | ✅ **已修**（`GoStyleRecoverInterceptor`；5 例真触发 + 3 轮变异验证） |
| 服务端熔断 | `go-zero@v1.10.2/zrpc/server.go:157-159` | `config.go:25` `Breaker json:",default=true"` | `UNAVAILABLE` + `"circuit breaker is open"`（`breakerinterceptor.go:46-48`、`go-zero@v1.10.2/core/breaker/breaker.go:19`） | ✅ **已修**（`GoStyleRpcBreakerInterceptor`；含 message-rpc 真 gRPC 端到端 2 例） |
| CPU 自适应限流 | `go-zero@v1.10.2/zrpc/server.go:160-162` | `go-zero@v1.10.2/zrpc/config.go:47` `CpuThreshold json:",default=900"` | `RESOURCE_EXHAUSTED` + `"service overloaded"`（`sheddinginterceptor.go:30-35`、`go-zero@v1.10.2/core/load/adaptiveshedder.go:34`） | ✅ **已修**（`GoStyleRpcSheddingInterceptor`；阈值沿用 Go 默认 900，CPU 采样 fail-open 已签字 `SD-2026-10-23-08`） |

**⚠️ 特别值得记的一点**：`message.go:34` 的 `DisableNativeRpcTimeout` 只把 `Timeout` 清零，
**`CpuThreshold` / `Middlewares.*` 全部原样保留** ⇒ 这三个拦截器在真源上是**生效**的。
审到这里容易误以为「Go 的中间件都被关了」—— **只关了超时一个**。

**Observable Impact**：
- **panic（已对齐）**：Go 记日志后返回 `INTERNAL` + `"panic: %v"`；RAG 现在**完全一致**，
  且 `UNRESOLVED-MSG-006`（RAG 抛 Error 时客户端看到什么）**已关闭** —— 实测也是 `INTERNAL`。
- **过载（2026-10-24 起已对齐）**：同一台机器 CPU 打满时，Go 以 `RESOURCE_EXHAUSTED` 主动拒流并保住进程；
  RAG 现在**同样**以 `RESOURCE_EXHAUSTED` + `"service overloaded"` 拒流。
  熔断侧同理：Go 开路时返回 `UNAVAILABLE` + `"circuit breaker is open"`，RAG 现在**逐字一致**
  （真 in-process gRPC 端到端 2 例已验：连续失败跨桶 ⇒ 客户端见该 status；熔断拒绝时 handler 不再执行）。
  ⚠️ **但当时对齐只在 `message-rpc` 一个组件上成立** ⇒ 其余 rpc 组件见 `DRIFT-MSG-V-026`。
> ✅ **2026-10-24 第九轮**：8 个组件已全部接入共享链（`hot-rpc` 是 Kafka 架构、不适用）。

**Evidence**: `[CODE]` go-zero v1.10.2 三个文件的具体行；`[CODE]` `message.go:34-44` 证明只清了 Timeout。
**Counterfactual = YES**：Spring 侧开发者通常只会挂「一个观测拦截器」，
不会想到要在纯 gRPC 服务里补 recover / 限流 / 熔断三件套。

#### V-016（recover 半边）已修的落地明细（2026-10-23 23:5x）

| # | 改了什么 | 文件 | 守门 |
|---|---|---|---|
| 1 | 新增 `GoStyleRecoverInterceptor implements ServerInterceptor`：包 `startCall` / `onMessage` / **`onHalfClose`** / `onReady`；`onComplete`/`onCancel` **刻意不兜** | `service/common/.../observability/GoStyleRecoverInterceptor.java` | `MessageRpcRecoverInterceptorWiringTest`（5 例，真 in-process gRPC） |
| 2 | 输出 `Status.INTERNAL` + description 前缀 `"panic: "`；**复刻 Go `%v`**（有 message 用 message、无则 `toString()` ⇒ 不带 Java 类型名） | 同上 | 同上第 1/2/4 例 |
| 3 | 装配改为 `server -> server.intercept(recoverThen(businessInterceptor))`；`recoverThen` 是**链顺序唯一定义点**（显式手工串，不依赖 `ServerBuilder#intercept` 的多次调用顺序） | `MessageRpcApplication.java` | `MessageGrpcInterceptorWiringTest`（链里真的能调到业务 interceptor）+ `MessageRpcRecoverInterceptorWiringTest#recoverWrapsTheInnerInterceptor` |

**⚠️ 三个最容易踩的点（都已在测试里钉死）**：

1. **不包 `onHalfClose` 等于没做**。grpc-java 的 unary handler 是在 `onHalfClose()` 里执行的
   （`ServerCalls.java:170` `method.invoke(request, responseObserver)`），
   不是在 `interceptCall` 里。⇒ 只包 `startCall` 的话，**用户 handler 里的 panic 一条都兜不住**，
   而 `interceptCall` 照样被调用、Bean 照样存在 —— 正是 §3.3「断言 Bean 存在 ≠ 机制生效」的反面教材。
   **变异验证实测**：摘掉 `onHalfClose` 兜底 ⇒ 5 例中 **2 例转红**（`expected: <INTERNAL> but was: <UNKNOWN>`）。
2. **`onComplete`/`onCancel` 刻意不兜**：那是**清理路径**（状态已定），Go 的 `defer recover` 也管不到；
   更重要的是此时 `ServerCall` 多半已 close，再 close 一次会抛 `IllegalStateException`。
3. **只 close 一次**：用**每次 `interceptCall` 一个局部 `AtomicBoolean`** 做守卫
   （⚠️ 2026-10-24 已改：初版用「静态 `WeakHashMap<ServerCall, AtomicBoolean>`」——
   那会在**共享模块**里留一份静态可变状态，且 `Collections.synchronizedMap` 的
   `computeIfAbsent` **并不原子**（`WeakHashMap` 未覆写它）⇒ 并发下可能双双放行 ⇒ 重复 close）。
   现在**无共享状态、无泄漏、无竞态**。

**⚠️ `Status.withCause(t)` 的安全性已核实**：grpc-java **1.63.0** 的
`Status.withCause` 实现是 `new Status(this.code, this.description, cause)` ——
**保留原 code，不从 cause 反推**（`grpc-api-1.63.0-sources.jar` `io/grpc/Status.java`）。
⇒ panic 的 cause 即使是 `StatusException` 也**不会**把 `INTERNAL` 改掉，
与 Go `status.Errorf(codes.Internal, "panic: %v", r)` 的「**恒定 INTERNAL**」一致。

**Evidence**: `[TEST]` `MessageRpcRecoverInterceptorWiringTest` 5 例 / 0 failures / 0 errors / 0 skipped；
`[MUTATION]` 3 轮变异全部命中（摘 `onHalfClose` → 2 红；改前缀 `panic: `→`boom: ` → 2 红；
不复刻 Go `%v` → 1 红）；
`[CODE]` grpc-java `ServerCalls.java:394`（`onCompleted()` 才 `call.close(Status.OK)`）；
`[CODE]` `grpc-api-1.63.0` `Status.withCause` 保留 code。
**Counterfactual = YES**：`recover` 挂在**内层**同样能兜住业务 handler 的 panic，
但兜不住**业务观测拦截器自己**的 panic —— Go 侧 recover 在最外层（`go-zero@v1.10.2/zrpc/internal/server.go:58`）。

---

### ✅ DRIFT-MSG-V-024 ✅ **已修（2026-10-24 第八轮）** DB / 未知异常：Go 保留 `UNKNOWN`，RAG 转成 `INTERNAL` ⇒ **熔断器会把它放大**

> **⚠️ 2026-10-24 处置**：本条的**结论是对的**（Go 的 DB 故障确实不触发熔断），
> 且**已实施修复**：rpc 侧兜底 `Status.INTERNAL` ⇒ **`Status.UNKNOWN`**、description 留空
> ⇒ 熔断判定与 Go 一致；api 侧业务码从 5001 变 **1015**（与 Go 的 `CodeServerBusy` 一致），
> 同时**不**回显原始错误文本（`DRIFT-MSG-V-003` 的签字保留）。
> 守门 `MessageRpcUnknownStatusWiringTest`（4 例，真 gRPC）+ 变异验证 3/4 转红。
> 详见下方「第八轮」第 3 节。

**Component**: message-rpc　**Operation**: 全部（任何非业务异常的失败路径）
**Category**: Error Identity　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **MEDIUM**（今天已可观察；**接上熔断器后放大成"会不会开路"的差别**）
**发现时间**: 2026-10-24（实施 V-016 breaker/shedding 时**顺带挖出**）

| 侧 | 行为 | 证据 |
|---|---|---|
| **Go** | DB 失败时**原样返回普通 error**（不是 status error）⇒ grpc-go 的 `status.FromError` 对非 status 错误返回 **`codes.Unknown`** ⇒ 客户端看到 **UNKNOWN + 原始错误文本** | `service/message/rpc/internal/logic/getconversationmessageslogic.go:48`（`metrics.ObserveDBError(...)` 后 `return nil, err`，**没包**）；`grpc-go v1.81.1` `status.FromError` → `New(codes.Unknown, err.Error())` |
| **RAG** | `MessageRpcServiceImpl.invoke` 把 `RuntimeException`（含 MyBatis 的 `DataAccessException`）统一转成 **`Status.INTERNAL`** + description「服务内部错误」⇒ 客户端看到 **INTERNAL** | `MessageRpcServiceImpl.java:133-140`；`MessageDomainService` 里的 DB 异常是 `throw e;` **原样重抛**（不转） |

**🔴 为什么这条现在很要紧：它决定熔断器算不算失败**

Go 的熔断判据 `codes.Acceptable(err)` 基于 `status.Code(err)`：

| 失败类型 | Go 的 `status.Code` | Go 的熔断判定 | RAG 的 close status | RAG 的熔断判定 | 一致？ |
|---|---|---|---|---|---|
| 业务错误（`INVALID_ARGUMENT`/`NOT_FOUND`/`PERMISSION_DENIED`） | 同码 | **不算失败** | 同码 | **不算失败** | ✅ |
| ctx / DB 超时 | `DEADLINE_EXCEEDED` | 算失败 | `DEADLINE_EXCEEDED` | 算失败 | ✅ |
| **DB 失败 / 未知异常** | **`UNKNOWN`**（普通 error） | **不算失败** ⚠️ | **`INTERNAL`** | **算失败** ⚠️ | ❌ |

⇒ **Go 的 DB 挂了不会触发熔断；RAG 的 DB 挂了会。**
⇒ 也就是：**RAG 的熔断器会比 Go 灵敏得多**（Go 只对「明确的 5xx 类 status 错误」熔断，
RAG 会对**任何**非业务异常熔断）。

**⚠️ 这条不是 breaker 实现引入的** —— 它**本来就在**（客户端可见的 status 码就不同），
**breaker 只是把它放大成"会不会开路"**。

**为什么不能顺手改掉**：改 `invoke()` 的兜底码会**改变客户端可见的行为**
（`INTERNAL` → `UNKNOWN` ⇒ api 侧 `mapStatusCodeToBizCode` 的落点也变），
而 api 侧的错误码映射**已在 `DRIFT-MSGAPI-*` 系列里逐条对齐过** ⇒ 牵一发动全身。

**Evidence**: `[CODE]` Go `getconversationmessageslogic.go:48` / `helper.go:169,203,249`（**19 处** `ObserveDBError` 后原样返回）；
`[CODE]` Go `core/stat/...` 的 `status.Code` 对非 status 错误返回 `codes.Unknown`；
`[CODE]` RAG `MessageRpcServiceImpl.java:133-140`；
`[CODE]` RAG `MessageDomainService` 的 `catch (RuntimeException e) { throw e; }`（不转码）。
**Counterfactual = YES**：把 RAG 的兜底码从 `INTERNAL` 改成 `UNKNOWN` ⇒ 熔断判定即与 Go 一致，
但客户端可见码会变 ⇒ **需要连同 api 侧一起评估**。

---

### ✅ DRIFT-MSG-V-025 ✅ **已修（2026-10-24 第九轮）** 熔断 / 降载的**指标**在 RAG 侧没有对应物

> **⚠️ 2026-10-24 口径更正（重要）**：原记写「Go 侧可以在 `/metrics` 里看到『这个 rpc 降载丢了多少』
> 『熔断拒了多少』」—— **这句是错的**，实测 Go 源码后已改写 ⇒ 详见下方「第九轮 · V-025 已修」。

**Component**: message-rpc（实现落在共享模块 `service/common/.../protection/`）
**Category**: Observability　**Classification**: **`OBSERVABLE DIFFERENCE`（非 wire 契约）**
**Risk**: **LOW**（不影响请求语义；影响的是「能不能看见它在工作」）

| 侧 | 有哪些量 |
|---|---|
| **Go** | `go-zero@v1.10.2/zrpc/internal/serverinterceptors/sheddinginterceptor.go`：`sheddingStat.IncrementTotal()` / `IncrementDrop()` / `IncrementPass()`（`go-zero@v1.10.2/core/load/sheddingstat.go`）+ `metrics.AddDrop()`；熔断侧 `core/breaker` 的 `markDrop()` / `markFailure()` 各自带 stat 计数 |
| **RAG** | `GoStyleAdaptiveShedder`（复刻自 `adaptiveshedder.go`）**只复刻了判据**（`allow()` / `pass()` / `fail()`），**没有那三个计数**；`GoStyleBreaker` 的 `markDrop()` / `markFailure()` 也**只更新桶计数，不对外暴露 stat** |

**⇒ 后果**：Go 侧可以在 `/metrics` 里看到「这个 rpc 降载丢了多少」「熔断拒了多少」，
RAG 侧**看不到** ⇒ 熔断/降载**上线后无法确认它在工作**（而这恰恰是 §3.3 最需要的那类证据）。

**⚠️ 为什么不随本轮一起补**：指标是**观测面**，不是行为面；
补它要决定「往哪暴露」（Micrometer？沿用本仓已有的 `metrics` 包？），
与 Go 的 `prometheus` 客户端**不是同一套** ⇒ 属于产品能力，不是「对齐 Go」。

**Evidence**: `[CODE]` Go `go-zero@v1.10.2/core/load/sheddingstat.go:37/42/47`（三个计数）；
`[CODE]` Go `go-zero@v1.10.2/zrpc/internal/serverinterceptors/sheddinginterceptor.go:24/28/29/41`（四处调用）；
`[CODE]` RAG `GoStyleAdaptiveShedder.java` 无任何计数字段（实测 grep）。
**Counterfactual = NO**（不影响任何请求的行为）—— 但**影响可运维性**。

---

### DRIFT-MSG-V-029 🆕 **跨组件待办**：`like-rpc` 有与 V-027 同款的 Flyway baseline 缺陷（`article-rpc` 经核实**不受影响**，已从本条剔除）

**Component**: `like-rpc`（**别的域，本轮不改**）
**Category**: DDL / Lifecycle　**Classification**: **`SEMANTIC MISMATCH`（运行期缺表）**
**Risk**: **HIGH**

> ⚠️ **2026-10-24 实测更正**：本条最初写的是「`like-rpc` / `article-rpc` **都**有同款缺陷，
> 且分类为「服务起不来」⇒ **两处都错**，已按实测改写。详见下方「更正记录」。

#### 现状（实测）

**`like-rpc` —— 确有缺陷**：`application.yml` 与 message-rpc 同款写法
（`baseline-on-migrate: true` + 独立账本 `flyway_schema_history_like` + 连同一个 `first_db` / `public` schema）
⇒ 在共享库里，**先启动者留下的非空 schema** 会让 Flyway 在 **version 1** 处打 baseline
⇒ **`V1__create_like_record.sql` 被整条跳过** ⇒ `like_record` 表永远建不出来。

⚠️ **但严重度不是「起不来」**：实测 `V2`~`V6` **没有任何一处引用 `like_record`**
（`grep -nE "REFERENCES|like_record"` 全部为空，V2 里那处只是注释）
⇒ V2..V6 全部执行成功 ⇒ **`like-rpc` 能正常启动**
⇒ 缺陷在**运行期**才暴露：第一次 like 写入 / 查询时找不到 `like_record` 表。
（与 message-rpc 的区别：message-rpc 的 **V5** ALTER 那 8 个时间列引用 `message_notification`
⇒ V5 失败 ⇒ **启动直接失败**。）

**`article-rpc` —— 不受影响**（原判断有误，已剔除）：它的迁移**从 V8 起步**，没有 V1 可跳：
```
V8__create_article.sql
V9__create_article_sync_outbox.sql
V10__create_article_community_revision.sql
```
⇒ baseline 落在 version 1 ⇒ **V8..V10 全部照常执行** ⇒ 无缺陷。

#### 为什么「非空 schema」这个前提对 like-rpc 也成立

实测全仓**只有 users 域**有 `SchemaInitializer`（`service/users/users-common/.../ddl/SchemaInitializer.java`）
⇒ 由 user-rpc / admin-rpc 先启动时建表 ⇒ `first_db` 的 `public` schema 非空。
（各 api 模块 `Application` 类里出现的 `SchemaInitializer` 字样**只是排除扫描的注释**，不是它们自己在建表。）

#### 修法

与 message-rpc **完全相同**：加 `spring.flyway.baseline-version: 0`
⇒ baseline 标记打在 version 0 ⇒ V1..V6 全部执行 ⇒ `like_record` 建得出来。
⚠️ 属**别的域**，按域间纪律**本工作树不得改** ⇒ 交给 like 域自己的 session。

⚠️ **连带提醒**：V-027 的守门手法（`@DynamicPropertySource` 里先建一张无关表把 schema 弄成非空）
可以直接照抄给 like 域 —— 否则它会像 message-rpc 一样「测试全绿但运行期缺表」。

#### 更正记录（2026-10-24，提交 `b43c4a1` 之前自查发现）

| 项 | 原写 | 实测后 | 依据 |
|---|---|---|---|
| 受影响组件 | `like-rpc` + `article-rpc` | **只有 `like-rpc`** | `ls service/article/article-rpc/src/main/resources/db/migration/` ⇒ 只有 V8/V9/V10 |
| 严重度 | 「服务起不来」 | **运行期缺表**（服务能起） | like-rpc V2..V6 不引用 `like_record`（grep 实测） |

**Evidence**: `[CODE]` `like-rpc/application.yml`：`baseline-on-migrate: true` + `table: flyway_schema_history_like`
+ URL `.../${POSTGRES_DB:first_db}`；`ls` 实测迁移为 V1..V6；`grep` 实测 V2..V6 不引用 `like_record`；
`ls` 实测 article-rpc 迁移为 V8..V10；`grep -rln SchemaInitializer` 实测**只有 users 域**有。
`[RUNTIME-PG]` message-rpc 侧的实测样本见 `DRIFT-MSG-V-027`。
**Counterfactual = YES**：把 `baseline-on-migrate` 去掉的人不会想到它会**顶掉 V1**。
⚠️ **本条的严重度未经 like 域真 PG 复现**（本工作树不碰别域代码）⇒
⇒ like 域接手时应先跑一次真 PG 复现再照抄修法。

#### 📦 交接给 like 域的执行清单（2026-10-24 用户拍板：**留给 like 域自己的 session**）

> 本工作树**没有**改 like 域的任何代码，也没有在 like 域的台账里写任何东西
> ⇒ 下面这份清单是**自包含**的，like 域的 session 照着做即可，不需要回看本工作树的上下文。

**① 复现（先证明缺陷存在，别直接照抄修法）**

```bash
# 起一个「schema 已非空」的 PG：users 域的 user-rpc / admin-rpc 先建表即可
#   （全仓只有 users 域有 SchemaInitializer —— 实测 grep -rln SchemaInitializer）
# 然后起 like-rpc，应观察到：
#   - flyway_schema_history_like 的首行是 `<< Flyway Baseline >>`，且 **version = 1**
#   - `like_record` 表 **不存在**（V1 被整条跳过）
#   - V2..V6 都是 success（它们不引用 like_record）
#   - like-rpc **能正常启动**（与 message-rpc 不同：message-rpc 的 V5 引用了 V1 的表 ⇒ 起不来）
#   - 第一次 like 写入 / 查询 ⇒ relation "like_record" does not exist
```

**② 修法（一行配置）** —— `service/like/like-rpc/src/main/resources/application.yml`：

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 0      # ← 加这一行（理由见 message-rpc 的 application.yml 同处注释）
```

> ⚠️ **不要**改成 `baseline-on-migrate: false` ——
> 那会让「非空 schema 且无账本」**直接抛异常退出**（`Found non-empty schema(s) … but no schema history table`），
> 比现在更糟（起不来且无修复路径）。

**③ 守门测试（照抄 message-rpc 的 `MessageFlywaySharedSchemaWiringTest`）**

关键手法：**在 `@DynamicPropertySource` 里先建一张无关表，把 schema 弄成非空**，
让 Spring 上下文启动（Flyway 才跑）。
⚠️ 放 `@BeforeEach` **无效** —— 那跑在上下文**之后**，Flyway 早就迁移完了，
⇒ 结果是「测试全绿但生产起不来/缺表」。

断言三项：
1. `like_record` 表存在；
2. 账本里 `<< Flyway Baseline >>` 行的 **version 必须是 0**；
   （⚠️ **不是**「不许有 BASELINE 行」—— `baseline-on-migrate=true` 时 Flyway **一定**会写这一行；
   第一版 message-rpc 的守门就是写成「不许有」⇒ 连修好后的状态也判红 ⇒ 被自己的测试当场抓出来。）
3. V1..V6 **全是 SQL 行**（不是 Baseline 行）。

**④ 变异验证**：把 `baseline-version: 0` 删掉 ⇒ 守门测试**必须转红** ⇒ 再改回来。

**⑤ 收尾**：在 like 域自己的台账（`docs/DRIFT/like/rpc.md`）里把这条记为已修，
**不要**回改本文件（message 域的台账由 message 域自己维护）。


---

### ✅ DRIFT-MSG-V-026 ✅ **已修（2026-10-24 第九轮）** **其余 9 个 rpc 组件尚未接入**熔断 / 降载链（跨组件不变量未闭合）

> **⚠️ 2026-10-24 状态更新**：本轮**全部 8 个**带 gRPC server 的 rpc 组件已接入共享链
> （`hot-rpc` 是 Kafka 架构、无 `GrpcServerConfigurer`，不适用）⇒ 跨组件不变量**已闭合**。
> ⇒ 逐组件的兜底码口径核对（**这是本条真正的前置条件**）见下方「第九轮 · V-026 已修」。

**Component**: `article-rpc` / `comment-rpc` / `favorite-rpc` / `follow-rpc` / `hot-rpc` /
`like-rpc` / `task-rpc` / `user-rpc` / `admin-rpc`　**（本轮全部接入，除 `hot-rpc` 不适用）**
**Category**: 跨组件不变量　**Classification**: **`OBSERVABLE DIFFERENCE`（局部对齐，全域未对齐）**
**Risk**: **MEDIUM**

**现状**：本轮把链的定义点做进了**共享模块** `service/common/.../protection/GoStyleRpcProtectionChain`，
且**只**在 `message-rpc` 的 `rpcInterceptorConfigurer` 里调用它。
⇒ 其余 9 个 rpc 组件的链**仍然是** `server -> server.intercept(rpcUnaryServerInterceptor)`
（实测 8 个组件形状完全一致，`hot-rpc` 根本没有 `GrpcServerConfigurer` —— 它是 Kafka 架构，不涉及）。

**⇒ 这是一条 §3.5 意义上的「跨组件不变量」**：熔断/降载在 Go 侧是 **zrpc 框架级**能力，
**每个 rpc server 都有**；RAG 侧现在只有 message 域有。

**⚠️ 为什么本轮不一起改**：那 9 个组件属于**其它域**，
按 `docs/Worktree.md` §三「工作树内只写自己域的台账文件」与**域间不互相改代码**的纪律，
message 工作树**不得**去改 `article-rpc` / `like-rpc` 等别的域的文件。
⇒ 正确做法：**各域自己的 session 接入**（每个组件只需改 1 行 + 加 1 个 `@Import`）。

**接入时要注意（已在本轮踩过/验证过的）**：
1. rpc 模块**刻意不扫 `com.rag.common`** ⇒ 必须显式 `@Import(GoStyleProtectionConfiguration.class)`，
   否则 `GoStyleProtectionProperties` Bean 不存在（且**不报错**，只是链里没有 breaker/shedding）。
2. `rpcInterceptorConfigurer` 的签名要从 1 个参数变成 2 个（多一个 properties）⇒ 该组件的
   `GrpcInterceptorWiringTest` 同步改。

**Evidence**: `[CODE]` 实测 8 个 rpc 组件的装配形状（`server -> server.intercept(rpcUnaryServerInterceptor)`）；
`[CODE]` `hot-rpc` 无 `GrpcServerConfigurer`（grep 实证）；
`[CODE]` `MessageRpcApplication` 本轮的改法即各域照抄的样板。
**Counterfactual = YES**：Spring 侧开发者不会想到「保护层要在每个 rpc 组件各接一次」。

---

### ✅ DRIFT-MSG-V-017 ✅ **已修（2026-10-23 第二轮）** `ConversationMessageEntity.isRead` 未按 `NotificationEntity` 的教训改名

> **⚠️ 2026-10-24 状态更正 #9**：本节正文（下方）描述的是**修复前**的状态。
> 该条已由 2026-10-23 第二轮修复（`ConversationMessageEntity` 的 `isRead` 字段改名 + getter/setter 同步）⇒ **本条不再是差异**。
> 详见下方「2026-10-23 第二轮：`DRIFT-MSG-V-014` / `-017` 已修」一节。

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

### ✅ DRIFT-MSG-V-018 ✅ **已修（2026-10-24 第九轮）** `ListConversations` 组装 item 时少一次埋点、helper 序也不同

> **⚠️ 2026-10-24 状态更新**：原记「修它的收益低于风险，建议与 V-016 一起决策」——
> 本轮 V-016 已落地，**本条随之修掉**（修法与风险判断见下方「第九轮 · V-018 已修」）。

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

### ✅ DRIFT-MSG-V-019 ✅ **已修（2026-10-23 第四轮）** PostgreSQL 连接的 `sslmode` 两侧不一致

> **⚠️ 2026-10-24 状态更正**：标题原无状态标记，**实为已修**——
> JDBC URL 已显式带 `sslmode=disable`（对齐 Go `etc/message.yaml` 的 `Mode: "disable"` ⇒ Go **永不尝试 TLS**）。
> 详见下方「✅ FIX-MSG-V-019」。

**Component**: message-rpc　**Operation**: 全部（数据访问层）
**Category**: Configuration Semantics　**Classification**: **`OBSERVABLE DIFFERENCE`**
**Risk**: **LOW-MEDIUM**（安全向）

```
Go  internal/model/db.go:22-29
      dsn = "host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai"
                                                        ^^^^^^^ 来自 conf.Mode
    etc/message.yaml:34   Mode: "disable"     ⇒ sslmode=disable，**明文，永不尝试 TLS**

RAG application.yml:27
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

> ✅ **2026-10-24 第九轮已关闭**（本行是**当时**的复核结论）：`MessageContextWiringTest` 已把四项逐条点名 ⇒ 详见 Unresolved 表。
| `UNRESOLVED-MSG-003`：`tools/difftest` 的 12 档**从未实际执行** | ✅ **复核为仍然成立**。`tools/difftest/` 下只有 `exp-difftest-grpc-errors-message.sh` 与 `grpc-message-dual-port-runner.java`（均 2026-10-22 12:17），**没有任何结果产物目录** |
| `UNRESOLVED-MSG-005`：`AutoMigrate`（每次启动校验表结构）vs `Flyway`（一次性迁移） | ✅ 仍成立，且本轮**部分加重**：`V2__create_message_conversation.sql:27-29` 用的是**裸 `ALTER TABLE … ADD CONSTRAINT`**，**没有 `IF NOT EXISTS`**。在一个「已被 GORM `AutoMigrate` 建过 `message_conversation`」的库上，这条迁移**会失败**。这条在本轮实测里没暴露，是因为 Testcontainers 每次都是**全新空库** |
| `DRIFT-MSG-012`（部署面：无 servlet 容器 vs 引了 web starter） | 🟡 **本轮可降级**。`mvn -pl service/message/message-rpc dependency:tree` 实测：无 `tomcat-embed-core` / `jetty` / `undertow` / `spring-boot-starter-web`，只有 `tomcat-embed-el`（纯表达式库）⇒ **不会起 servlet 容器**。剩余差异仅为 classpath 上多一个 `spring-boot-web-server`（为满足 nacos-discovery 对 `WebServerInitializedEvent` 的反射引用而必需）。**建议把 `UNKNOWN` 改为「已确证无行为影响」** |
| `MybatisPlusOrderingBridgeAutoConfiguration`（Boot 4 自动配置排序桥） | 🆕 **此前两轮台账均未提及**，但代码已存在且已生效。核实 imports 文件存在且只有这一行 ⇒ 排序边成立。**登记为已确认的基础设施事实，不计漂移** | ⚠️ **2026-10-27 第十七轮订正**：**本类已于本轮删除**（域侧收编到共享层）⇒ ⇒ 「代码已存在且已生效」这句**只在本轮之前成立** ⇒ ⇒ 现在**本组件不再持有该类** ⇒ ⇒ 排序边由**共享层**那份（`com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration` + 共享层 `META-INF/spring/…AutoConfiguration.imports`）承担 ⇒ ⇒ **本行应读作「已移出本组件」** |
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
| **CASE-R08** | 错误码/文案表双侧对读 | 20 码 + 20 文案逐条 1:1，含 `1015 "service busy"` 与未知码 fallback `"FAIL"` | ✅ |
| **CASE-R09** | `go-zero@v1.10.2` 源码读到**消费点** | `go-zero@v1.10.2/zrpc/config.go:46` 默认 2000ms；`serviceconf.go:35` 默认 `pro`；`go-zero@v1.10.2/zrpc/server.go:131-167` 三拦截器装配条件 | ✅ **满足 §3.3.1「必须读到消费 tag 的控制流」** |

### 8.2 关键用例的覆盖判定（哪些路径**仍然零覆盖**）

| 路径 | 覆盖状态 | 影响 |
|---|---|---|
| `sendNotification` **端到端 happy path**（走真 gRPC 端口） | ❌ 零覆盖 | `insertBatch` 只在 `MessageSemanticEquivalenceTest` 里被直接调过，从未经 gRPC 层 |
| `sendChatMessage` 走真 gRPC 端口 | ❌ 零覆盖 | 同上 |
| **负 `offset`** | ✅ **已覆盖**（2026-10-23 第二轮，`MessagePaginationNegativeOffsetWiringTest` 7 例，真 PG） | ⇒ **V-014 已修**，不再「能长期存在」 |
| `listConversations` 分页翻页（`offset>0`） | ❌ 零覆盖 | |
| `loadProfile` 兜底三分支 | ❌ 零覆盖 | |
| 并发首发（`uk_conversation_pair` 冲突） | ❌ 零覆盖 | |
| panic / 过载 / 熔断 | ❌ 零覆盖且 RAG 侧**无对应机制** | ⇒ V-016 |
| 双端口对拍（12 档） | ❌ 脚本就位、**从未执行** | ⇒ `UNRESOLVED-MSG-003` |

---

## 9. Unresolved（§37 —— 本节不得为空才允许下结论）

| 编号 | 问题 | 为何无法判定 | 需要什么 |
|---|---|---|---|
| **UNRESOLVED-MSG-003** ✅ **已关闭（2026-10-24 第八轮）** | ~~`tools/difftest` 的 message 域 12 档对拍是否真的一致~~ | —— | ✅ **已关闭**：真起 Go + RAG 两套栈（2026-10-24，端口段 20082 / 60210），跑 `exp-difftest-grpc-errors-message.sh` ⇒ **11 条 status 码 + happy path 全部逐条一致，EXIT=0**；结果产物 `tools/difftest/message-grpc-results.txt`。<br>⚠️ **关闭它的前提是先修掉 3 个「起不来」的东西**：V-027（Flyway baseline 跳过 V1）、V-028（message-api jar 不可执行）、H5（`POSTGRES_HOST` 占位符不一致）⇒ **「脚本就位」≠「跑得起来」** |
| **UNRESOLVED-MSG-004** ✅ **已关闭（2026-10-24 第九轮）** | ~~DRIFT-MSG-004/005/006 未各配独立防回退用例~~ | —— | ✅ **已关闭**：新增 `MessageContextWiringTest`（4 例，**逐条点名**）：`@MapperScan` 的包名 / `NacosRegistration` 的 Bean 名 / `FlywayAutoConfiguration` 类可加载 / `baseline-version: 0` + 独立账本表名；另新增 `MessageApiJarExecutableWiringTest`（2 例，V-028 的点名式防回退） |
| **UNRESOLVED-MSG-005** ✅ **已关闭（2026-10-23 第四轮，FIX-MSG-V-023）** | ~~已存在的非 Flyway 建表的库上能否启动~~ | —— | ✅ 已关闭：**原问题的前提不成立** —— GORM `AutoMigrate` 建的是**索引**，V2 的 `ADD CONSTRAINT` 在 GORM 建过的库上**本来就能跑**；且 V4 已把约束降回索引（`DROP CONSTRAINT IF EXISTS` + `CREATE UNIQUE INDEX IF NOT EXISTS`），幂等实测 8 次执行全 `exit=0`。⚠️ **残留角落**：仅当外部工具恰好用了同名**约束**时才成立 |
| **UNRESOLVED-MSG-V-001**（沿用，跨轮次） | Nacos namespace/group 不一致 | 需 user/admin 侧同轮次结论 | 两侧 namespace/group 对齐后再判 |
| **UNRESOLVED-MSG-006** ✅ **已关闭（2026-10-23 第六轮）** | ~~RAG 侧 handler 抛 `Error`/panic 时客户端到底看到什么~~ | —— | ✅ 已关闭：`GoStyleRecoverInterceptor` 落地后，抛 `Error`（非 `RuntimeException`）也被兜住 ⇒ 客户端见 `INTERNAL` + description 以 `"panic: "` 开头，与 Go 的 `recoverinterceptor.go:39-42` 一致（`MessageRpcRecoverInterceptorWiringTest` 5 例，真 in-process gRPC） |
| **UNRESOLVED-MSG-007** ✅ **前提消失（2026-10-23 第四轮，FIX-MSG-V-019）** | ~~pgjdbc `sslmode` 默认值对**行为**的实际影响~~ | —— | ✅ **不再是未知项**：JDBC URL 现在**显式**钉 `?sslmode=disable`（对齐 Go `etc/message.yaml` 的 `Mode: "disable"`）⇒ **不再依赖 pgjdbc 的文档化默认值 `prefer`** ⇒ 两侧都是「永不尝试 TLS」。⚠️ 口径：原问法是「默认 `prefer` 在开了 TLS 的 PG 上会怎样」，那已不是当前配置 |
| **UNRESOLVED-MSG-001** ✅ **已关闭（2026-10-24 第十轮补记）** | ~~JVM 运行时区未知 ⇒ `DRIFT-MSG-010` 时间戳 epoch 是否整体偏移仍未定级~~ | —— | ✅ **已关闭**（三条各自独立成立）：<br>① **2026-10-22 用户确认部署时区 = Asia/Shanghai** ⇒ `DRIFT-MSG-V-007` 的条件化结论确定（实测方向是「RAG 比 Go **晚** 8 小时」，不是「早」—— 方向不能靠推理）；<br>② **V-019** 把 JDBC URL 显式钉上 `options=-c TimeZone=Asia/Shanghai` ⇒ 不再依赖宿主机时区；<br>③ **V-020** 把 8 个时间列改成 `TIMESTAMPTZ` ⇒ **第十轮活体对拍实测两侧列类型逐字一致**（`data_type` 0 差异）⇒ 数据**自描述瞬时**，不再依赖「一个库只由同一时区 JVM 写」 |
| **UNRESOLVED-MSG-002** ✅ **已关闭（2026-10-24 第十轮补记）** | ~~`ServerInterceptor` 挂载只有 `verify(builder).intercept(...)` 这层证据~~ | —— | ✅ **已关闭**：<br>① **第八轮真双栈 12 档对拍**（EXIT=0）提供**端到端**证据 —— 拦截器链上的行为（熔断码 / shedding 码 / recover 的 `INTERNAL` + `"panic: "` 前缀）在**真实 gRPC 往返**上逐条一致；<br>② **第九轮 V-026** 给 8 个 rpc 组件补了装配层断言（装配层断言「业务 interceptor 真被调到」+「拿到的是**包装过的** call」）⇒ 装配层证据也齐了<br>⚠️ **2026-10-24 第十一轮口径更正**：具体是 **7 个 rpc 组件各新增 `*ProtectionChainWiringTest`（article / comment / favorite / follow / like / task / admin，各 2 例）+ `user-rpc` **复用并改造**既有的 `GrpcInterceptorWiringTest`（2 例）** ⇒ 合计 **8 组件 16 例**（不是「8 个组件各新增一个测试类」） |
| **UNRESOLVED-MSG-008** 🆕 | `Flyway` 独立账本在**三个模块同库**下真的不撞 | 本轮只证了 **message-rpc 单模块**建出 `flyway_schema_history_message`；`like-rpc` / `article-rpc` 未同时启动 | 三模块同时连同一个 `first_db`，`\dt` 确认三本账 + 各自 3 条 `success`<br>⚠️ **本工作树不能做**（需 `like-rpc` / `article-rpc` 同启 ⇒ **跨域**） |
| **UNRESOLVED-MSG-009** ✅ **已关闭（2026-10-24 第十轮，用户拍板）** | ~~时间列 `TIMESTAMP`（非 `TIMESTAMPTZ`）在**多实例/多时区**下是否仍自洽~~ | —— | ✅ **关闭依据**：<br>① **原问法的前提已消失** —— 它建议的「补一条 `V4` 迁移」**已由 `V5__message_time_columns_timestamptz.sql` 做完**（8 列全部 `TIMESTAMPTZ`）；<br>② **第十轮双侧活体对拍实测**：两侧 `data_type` **0 差异**（全部 `timestamp with time zone`）⇒ 数据**自描述瞬时**，不再依赖「一个库只由同一时区 JVM 写」；<br>③ **剩下的不是代码问题** ——「同一个库被不同时区的 JVM 写」属**部署口径**，无代码可修 ⇒ 转为部署约束（见下）<br>⚠️ **关闭口径**：关闭的是「**代码侧**是否存在不自洽」这个未知项；**部署侧**的约束仍然成立，只是不再是一条待办 |

---

## 10. 最终判定（§39 的 18 项 checklist）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 两边 public behavior 是否一致 | ✅ 9 个 RPC 方法数/名/序一一对应 |
| 2 | 正常路径是否一致 | ✅ 正常路径一致（负 offset 的异常输入路径已于 2026-10-23 第二轮修好，见 V-014） |
| 3 | 边界输入是否一致 | ✅ **负 `offset` 已一致**（V-014 于 2026-10-23 第二轮修好：Go 忽略、RAG 现在也忽略 ⇒ 返回第一页） |
| 4 | NotFound / Empty / Null 是否一致 | ✅ 会话/通知不存在、extra 为空、时间为 null 三态逐条一致 |
| 5 | Error meaning 是否一致 | ✅ 业务错误码 20/20 一致；❌ DB 故障码已签字不一致（V-003） |
| 6 | Error propagation 是否一致 | ⚠️ 业务错误一致；DB 故障的传播形态已签字不同（V-003）；panic 传播 ✅ 已关闭（`UNRESOLVED-MSG-006`，recover 落地） |
| 7 | External dependency failure 是否一致 | ⚠️ user/admin 失败兜底一致；**下游故障时两侧都无超时兜底**（共同缺陷，非漂移） |
| 8 | Cache hit / miss 是否一致 | N/A（本组件无缓存） |
| 9 | Transaction boundary 是否一致 | ✅ 保护的操作集合与语句序都一致（真 PG 8 条 + 历史变异测试） |
| 10 | State transition 是否一致 | ✅ 会话状态机 + `canSend` 逐条一致 |
| 11 | Side effects 是否一致 | ✅ **组件内已对齐**（recover / breaker / shedding 三件套，2026-10-24 落地，V-016）；⚠️ **其余 9 个 rpc 组件未接入**（V-026） |
| 12 | Ordering 是否一致 | ✅ 三处排序键双侧一致（V-004 / V-005 已修） |
| 13 | Retry 是否一致 | ✅ 两侧都无重试 |
| 14 | Timeout scope 是否一致 | ✅ 2s、整个 handler、单一执行者 |
| 15 | Concurrency semantics 是否一致 | ✅ 都靠 DB 唯一索引 + 事务，无应用层锁 |
| 16 | Authentication / Authorization 是否一致 | ✅ 两侧都**不做**鉴权（真源设计，非遗漏） |
| 17 | Serialization semantics 是否一致 | ✅ 结果一致（V-017 的 `isRead` 字段名已于 2026-10-23 第二轮修好） |
| 18 | Lifecycle 是否一致 | ✅ **reflection 暴露面已对齐**（V-015 已修：`application.yml:139` 钉 `reflection-service-enabled: false` + `MessageGrpcReflectionExposureWiringTest` 2 例）<br>⚠️ 服务发现后端仍不同（etcd vs Nacos）⇒ `UNRESOLVED-MSG-V-001`，**跨域**（user/admin 域） |

### **最终判定：`SEMANTICALLY NOT EQUIVALENT`**

> ⚠️ **2026-10-24 第十一轮 · 判定重述** —— 下面三条理由是 **2026-10-23 第四轮的判定原文**，
> 其中**两条已被本文件后续章节推翻**（`V-024` 已修、`UNRESOLVED` 大半已关）⇒
> **不要再照着下面的旧理由去估工作量**。当前有效判定见本节末尾的「**🔁 重述后的判定**」。

**理由（三条各自独立成立）** —— ⚠️ 以下为 **2026-10-23 第四轮原文**：

1. **存在会改变行为的差异**：`DRIFT-MSG-V-024`（DB 异常的 status 分岔，见理由 2）。
   按 §39 的判定规则，只要存在一条会改变行为的差异即判 `NOT EQUIVALENT`。
   ⚠️ **2026-10-24 更正**：上一版此条写的是 `DRIFT-MSG-V-014`（负 offset）——
   该条已由 **2026-10-23 第二轮**修好（真 PG 守门 7 例）⇒ 不能再当差异用。
   ⚠️ **2026-10-24 第十轮再更正**：本条原本还并列了 `DRIFT-MSG-V-021`（两个索引的**名字**不同、列相同），
   但**索引名不同根本不改变任何行为**（同一列的等价索引 ⇒ 查询能力 / 唯一性 / 性能完全相同），
   且该条已于第十轮**签字**（`SD-2026-10-24-01`）⇒ **从「会改变行为」的证据里撤掉**。
   ⇒ 现在这一条**只剩 `V-024` 一个支撑**。
   ⚠️ **2026-10-24 第十一轮再更正**：`V-024` **已于第八轮修好**（rpc 兜底码 `INTERNAL` → `UNKNOWN`，
   `MessageRpcServiceImpl.java:166`；守门 `MessageRpcUnknownStatusWiringTest` 4 例真 gRPC）
   ⇒ **理由 1 现在没有支撑了**。
2. **存在会改变系统应如何行为的差异**：`DRIFT-MSG-V-024`（2026-10-24 新挖出）——
   Go 侧 DB / 未知异常**原样返回** ⇒ 客户端见 `UNKNOWN`、**不计熔断失败**；
   RAG 侧 `MessageRpcServiceImpl.invoke:133-140` 把 `RuntimeException` 转 `INTERNAL` ⇒ **计熔断失败**
   ⇒ 同一个 DB 故障，两侧的**熔断开路决策会分岔**。这属于「系统应该如何行为」而非「实现风格」。
   ⚠️ **上一版此条写的是 V-016**（「RAG 侧没有任何对应机制」）—— 该结论已被 2026-10-24 的落地推翻，
   **理由已换成 V-024**（V-016 在 message-rpc 组件内已对齐）。
   ⚠️ **2026-10-24 第十一轮再更正**：`V-024` **已修** ⇒ 这段描述的「RAG 转 `INTERNAL`」**已不存在**
   ⇒ **理由 2 也没有支撑了**。
3. **证据不足以判 EQUIVALENT**：双端口活体对拍从未执行（`UNRESOLVED-MSG-003`），
   `UNRESOLVED-MSG-004/005/007/008/009` 五项未关闭（`UNRESOLVED-MSG-006` 已于 2026-10-23 关闭）。
   按 §39「证据不足 ⇒ UNRESOLVED」的规则，本轮也**不能**下 `UNRESOLVED` 结论 —— 因为 §7 的两条判定依据是**已取证的静态 + 活体探针**，
   不是猜测。
   ⚠️ **2026-10-24 第十一轮再更正**：这五项里 **`-003` / `-004` / `-005` / `-007` / `-009` 均已关闭**
   （`-003` 第八轮活体对拍 EXIT=0、`-004` 第九轮点名式守门、`-005` 第四轮 V-023、`-007` 第四轮 V-019、
   `-009` 第十一轮）⇒ **只剩 `-008`（三模块同库三账本，跨域）未关**。

#### 🔁 重述后的判定（2026-10-24 第十一轮 · **当前有效**）

| 判据 | 状态 |
|---|---|
| **组件内代码行为是否一致** | ✅ **一致** —— 38 项组件级对照里，**所有会改变行为的差异都已修或已签字**；`V-024`（status 分岔）、`V-018`、`V-020`、`V-021`、`V-025`、`V-026`、`V-027`、`V-028`、`V-031` 全部闭合 |
| **剩余差异的构成** | ① **已签字的有意偏离 9 条**（`docs/Intentional_Deviations/message/rpc.md`）—— 是决策不是缺陷<br>② **共享面三件响应头差异**（`MSGAPI-010` charset / `-011` reason phrase / `-013` Traceparent）—— 用户已拍板「保持登记，后续修」<br>③ **跨域待办**：`V-029`（like 域）、`V-030`（6 个 api 域）、`UNRESOLVED-MSG-V-001`（Nacos，user/admin 域）、`UNRESOLVED-MSG-008`（三模块同库） |
| **最终判定** | ⚠️ **仍写 `SEMANTICALLY NOT EQUIVALENT`，但支撑理由已全部换过** ——<br>不再是「代码行为不一致」（那部分已闭合），而是「**跨组件不变量未闭合**（`UNRESOLVED-MSG-008`）+ **共享面三项待修**」<br>⇒ 按 §39，**签字偏离不计入 NOT EQUIVALENT** ⇒ 严格说应读作「**组件内 EQUIVALENT，跨组件不变量 UNRESOLVED**」 |
| **本轮实测（2026-10-24 19:50）** | `service/common` 168 / `message-common` 21 / `message-rpc` 76 / `message-api` 88 ⇒ **合计 353 例，0 failures / 0 errors / 0 skipped，BUILD SUCCESS（5:09）** |
| ⚠️ **不要** | 不要照抄上面那三条旧理由去估工作量 —— 它们描述的是 **2026-10-23 第四轮**的状态 |

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
| 取值来源 | `zrpc.RpcServerConf.Mode` 的 tag `json:",default=pro,…"`（`go-zero@v1.10.2/core/service/serviceconf.go:35`） | `GrpcServerProperties.reflectionServiceEnabled` |
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

### ✅ DRIFT-MSG-V-021 —— 两个索引的**名字**不同（列相同）· **已签字**（2026-10-24 第十轮），见 `SD-2026-10-24-01`

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

> ✅ **2026-10-24 第十轮：用户拍板「签字保留 RAG 短名，不改名」**
> ⇒ 本条已从 **DRIFT** 转入签字档：[`SD-2026-10-24-01`](../../Intentional_Deviations/message/rpc.md)。
> ⚠️ **读法变更**：看到这条 DDL 对拍为红时，先确认是不是 `SD-2026-10-24-01`，**不要**当新回归去「修」。
>
> ⚠️ **本轮同时拿到了活体证据**（首次，不再是静态推断）：
> 两侧各建一个真库（Go 跑**真源自己的** `AutoMigrate`，RAG 按序执行 V1~V5），逐条 dump `pg_indexes` ⇒
> 这两个索引的 **`cols` 与 `unique` 两侧完全一致**，且**全域 12 个索引里只有这 2 个名字不同**。
> 复现：`bash tools/difftest/run-message-ddl-parity.sh`
> （详见本文件末尾「第十轮」小节）。

### ✅ DRIFT-MSG-V-022 —— `idx_notification_recipient_read` 的**列不同**（Go 只有 1 列）· **已签字**，见 `SD-2026-10-23-06`

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

> ⚠️ **2026-10-24 第十轮加的前向指针 —— 本表是 2026-10-23 第四轮的快照，下表三行的状态此后又变了**：
> | 行 | 表里写的 | **现在的实际状态** |
> |---|---|---|
> | `DRIFT-MSG-V-018` | 🔴 OPEN | ✅ **已修（第九轮）** —— `MessageListConversationsParityTest` 4 例 + 变异 1/4 红 |
> | `UNRESOLVED-MSG-004` | 🔴 仍成立 | ✅ **已关闭（第九轮）** —— `MessageContextWiringTest` 4 例逐条点名 |
> | `DRIFT-MSG-V-016` | 🟡 部分已处置 | ✅ **已全处置（第九轮）** —— breaker + shedding 已落地（`SD-07` 同时撤销） |
>
> ⇒ **下面这张表的「状态」列读作「2026-10-23 第四轮当时的判断」，不是当前状态。**

| 编号 | 状态 | 本轮核实结论 |
|---|---|---|
| `DRIFT-MSG-V-016`（go-zero 三个 unary 拦截器：recover / breaker / CPU shedding） | 🟡 **部分已处置**（2026-10-23 23:5x） | **recover ✅ 已修**（`GoStyleRecoverInterceptor` + `recoverThen` 显式串链；5 例真 in-process gRPC + 3 轮变异验证；顺带关闭 `UNRESOLVED-MSG-006`）；**breaker / CPU shedding ⏸️ 签字挂起**（`SD-2026-10-23-07`，用户指示「breaker/shedding 先放着」）⇒ **不再是 OPEN，但也不是「三条全齐」** |
| `DRIFT-MSG-V-017`（`isRead` 字段名） | ✅ 已修（上一轮），**本轮复核仍绿** | `MessageIsReadLambdaWiringTest` 3/3 绿、skipped=0 |
| `DRIFT-MSG-V-018`（`ListConversations` 少一次 `count_unread` 埋点） | 🔴 **OPEN** | 确认仍缺。**副作用已加重**：本轮把时间列与会话时区都摊开（V-019/V-020）后可以确定，该路径当前**不可达**（列表已按参与者过滤，`peerOf` 不会失败），所以它今天只表现为「一条永远不会增长的指标」。修它的收益低于风险，**建议与 V-016 一起决策** |
| `UNRESOLVED-MSG-004`（三项「起不来」缺陷无点名式防回退） | 🔴 仍成立 | 本轮新增的 `MessageGrpcReflectionExposureWiringTest` 是**配置绑定**层的点名式用例，可作为该条「点名式守门」的样板；`@MapperScan` / `NacosRegistration` Bean 名两项目前仍未点名 |
| `UNRESOLVED-MSG-006`（「RAG 抛 Error 时客户端看到什么」未取证） | ✅ **已关闭**（2026-10-23 23:5x） | recover 落地后**顺带关掉**：实测 `throw new StackOverflowError()`（`Error` 而非 `Exception`）也被兜住 ⇒ 客户端看到 `INTERNAL` + `"panic: "` 前缀（`MessageRpcRecoverInterceptorWiringTest#nonRuntimeExceptionErrorIsAlsoRecovered`）。⚠️ 关闭口径 = **在 recover 已装配的前提下**；若哪天 recover 被摘掉，这条要重新打开 |

### 🟡 附带观察（不改，只记）

`MessageTransactionWiringTest` 带 `@EnabledIfDockerAvailable` ——
即 `docs/DRIFT/_cross/component-coverage-matrix.md` 点名的那个反模式。
**本模块不受其害**（`message-rpc` 是有 `RYUK_DISABLED` surefire 注入的模块之一，
本轮实测 `skipped=0`），但该注解**理应删掉**：它唯一的作用是把两类失败压成同一个
`Docker is not available`，而 message-rpc 并不需要它。
**本轮未删**（它现在无害，删它属于清理而非对齐，且会让本轮的测试计数变化混在语义修复里）。

### 本轮实测数据

> ⚠️ **2026-10-23 23:5x 状态更正 #6**：下面这两个数字**之前是过期的**，本轮实测后已改。
> 原记录「message-rpc：51 tests（原 49 + 新增 2）」与「DDL 对拍 V1~V4 / 23 行差异」**都停留在 V-020 那一轮之前**：
> ① V-020 那轮实际还改了 `MessageRpcSupportTimeZoneTest`（6 → 7 例，**+1**），台账漏记 ⇒ 真实基线是 **54** 而非 51；
> ② DDL 对拍在 `V5` 落地后**类型与可空性已 0 差异**，差异从 23 行降到 **19 行**。
> ⇒ **教训**（与 §6.2 同源）：测试计数与对拍数字是**会随每轮改动漂移的实测量**，
> **引用时必须带核对日期 + 当场实测**，不能沿用上一轮的数。

```
message-rpc：54（V-020 轮后基线）→ 59（recover）→ 61（breaker）→ 68（本轮 +3 Flyway 守门 +4 V-024 守门）
              → 68 tests / 0 failures / 0 errors / 0 skipped   BUILD SUCCESS
              ← skipped=0 是硬要求：§状态更正#5 规定「绿灯必须额外断言 skipped 数 = 0」
message-api：84 → 86（本轮 +2 503 超时取证）/ 0 failures / 0 errors / 0 skipped
message-common：21 tests / 0 failures / 0 errors / 0 skipped
service/common：162 tests / 0 failures / 0 errors / 0 skipped
              ← GoStyleRecoverInterceptor / 4 个 protection 薄壳所在模块（本轮改了 HttpMiddleware 的 nosniff）
semantic-probe：56 例 → 一致 54，不一致 0，已签字偏离 2                      EXIT=0
breaker-probe（**新增**）：A 组 17 条判据**逐条一致** / B 组冷启动前缀**逐字一致**（prefix=AAAAA）
                        / C 组拒绝率统计比对（go=0.2500 / rag=0.2000，差 0.05 ≤ 容差 0.15）  EXIT=0
                        ⇒ C 组是**健全性检查**（Go 随机源不可播种 + 按墙钟分桶），
                          **真正的逐条对拍是 A 组与 B 组**
**双端口活体对拍（2026-10-24 新增，本仓 message 域首次）**：
  difftest 12 档 gRPC 对拍 ⇒ 11 条 status 码 + happy path **全部一致**      EXIT=0
  真容器响应头 dump ⇒ 6 条路径 × 两侧全量头（产物 /tmp/difftest-msg-headers/）
  3s 超时 503 ⇒ 503 + text/plain;charset=utf-8 + nosniff + "Request Timeout\n"
  ⇒ **UNRESOLVED-MSG-003（rpc）/ -003、-004（api）已关闭**
DDL 对拍：Go AutoMigrate 55 行 vs RAG **V1~V5**，**归一化后剩余 19 行差异**
          = 6 列 DEFAULT（RAG 有 / Go 无，各占 6 行）12
          + V-021 索引名 2 处（Go 带 message_ 前缀）4
          + V-022 索引列数（已签字）2
          + 表头噪声（### COLUMNS）1
          ⇒ **类型与可空性 0 差异**（V-020 的目标已达成）
```

> ⚠️ **2026-10-24 状态更正 #7**：上一版这里写「message-rpc：51 → 59」，
> 依据是 V-020 轮的旧基线 51；**真实基线是 54**（V-020 轮自己漏记了 `MessageRpcSupportTimeZoneTest` 的 +1）。
> ⇒ 本版已按**本轮当场实测**重填：**328 例**（common 162 + message-common 21 + message-rpc 61 + message-api 84），
> **0 failures / 0 errors / 0 skipped**，BUILD SUCCESS。

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

### 按用户指示推迟：`DRIFT-MSG-V-016`（**已于下一轮部分落地**）

> **V-020 那轮**：「先把 v020 解决再来探讨 v016」⇒ 该轮**未动** V-016（三个 go-zero 框架拦截器），
> 只把分析、Go 出处与三条拆解建议备好，等下一轮单独决策。
>
> **2026-10-23 23:4x**：方案成形（本文件「`DRIFT-MSG-V-016` 处置方案」一节）。
> **2026-10-23 23:5x**：用户拍板「做recover，breaker/shedding先放着」⇒
> **recover 半边已实施**（见上文「V-016（recover 半边）已修的落地明细」），
> **breaker / shedding 转为签字挂起**（`SD-2026-10-23-07`）。
> ⇒ 本小节保留为历史脉络，**当前状态看上面 V-016 主节**。

---

<!-- @plan 2026-10-23 23:4x · V-016 处置方案（待用户拍板，尚未实施） -->

## `DRIFT-MSG-V-016` 处置方案（三条拆开决，因为成本与风险差一个数量级）

> ⚠️ **2026-10-24 第十一轮前向指针 —— 本节是 2026-10-23 的「方案稿」，下面那句「待决策 / 一行代码都还没改」已经过期**：
> **V-016 现已全部落地** ——
> · **recover 半边**（2026-10-23）：`GoStyleRecoverInterceptor` + `recoverThen` 显式串链，5 例真 in-process gRPC + 3 轮变异验证
> · **breaker / shedding 半边**（2026-10-24 第九轮）：**8 个 rpc 组件全部接入** `GoStyleRpcProtectionChain`（9 个 rpc 组件，含 message-rpc 自身；`hot-rpc` 是 Kafka 架构不适用），
>   守门 16 例（7 个 `*ProtectionChainWiringTest` 各 2 例 + `user-rpc` 的 `GrpcInterceptorWiringTest` 2 例）
> · 连带：`SD-2026-10-23-07`（breaker/shedding 的「挂起式签字」）已于同日**撤销**；`SD-2026-10-23-08`（CPU fail-open）成为生效中的签字
>
> ⇒ **本节读作「当初的方案与决策过程」**，当前状态看上面 V-016 主节与 `docs/Intentional_Deviations/message/rpc.md`。

> **状态：待决策。本节只是方案，一行代码都还没改。**
> ⚠️（↑ 这句话是 2026-10-23 的原文，**现已过期** —— 上面那段指针为准。）
> 与上一节的 `DRIFT-MSG-V-015/019/020/023` 不同 —— 那几条是「对齐既有行为」，
> 这条是「**新增框架能力**」，两者的决策性质不一样。

### 现状（两侧事实，均已读到消费点）

```
Go  go-zero@v1.10.2/zrpc/server.go:144-163 setupUnaryInterceptors —— 按配置逐个挂：
      c.Middlewares.Trace      default=true   → UnaryTracingInterceptor
      c.Middlewares.Recover    default=true   → UnaryRecoverInterceptor
      c.Middlewares.Stat       default=true
      c.Middlewares.Prometheus default=true
      c.Middlewares.Breaker    default=true   → UnaryBreakerInterceptor
      c.CpuThreshold > 0       default=900    → UnarySheddingInterceptor
      c.Timeout > 0            default=2000   → UnaryTimeoutInterceptor  ← 被 Disable 清零
    默认值出处：go-zero@v1.10.2/zrpc/internal/config.go:19-26（Middlewares 五个都是 default=true）
                go-zero@v1.10.2/zrpc/config.go:46-47（Timeout 2000 / CpuThreshold 900, range=[0:1000)）
    etc/message.yaml 一个都没配 ⇒ 全取默认
    ⚠️ service/common/observability/rpc.go:19-23 的 DisableNativeRpcTimeout **只清 Timeout 一个字段**
       ⇒ recover / breaker / shedding 在真源上是**生效**的

RAG message-rpc 只有一个自研的观测拦截器（超时 + trace），由
     MessageRpcApplication#rpcInterceptorConfigurer（:106-108）挂上
     ⇒ recover / breaker / shedding **三个全缺**
```

### 拆解一：recover —— **建议本轮就做**

| 项 | 内容 |
|---|---|
| **Go 行为** | `recoverinterceptor.go:34-41`：`if r := recover(); r != nil` → `status.Errorf(codes.Internal, "panic: %v", r)`。**进程存活** |
| **实现** | `service/common` 新增 `GoStyleRecoverInterceptor implements ServerInterceptor`（放 common，与既有 `RpcServerInterceptorFactory` 同源，**10 个 rpc + 10 个 api = 20 个组件**都能用）→ catch `Throwable` → `Status.INTERNAL.withDescription("panic: " + r).asRuntimeException()` |
| **必须逐字对齐的点** | description 前缀必须是 `"panic: "`（Go 的 `%v` 对 `error` 打印 `err.Error()`）—— **wire 可见**，`message-api` 的 `bizCodeFromError` 会把 `INTERNAL` 映射成 5001，与 Go 一致 |
| **装配顺序（易错）** | grpc 拦截器按注册顺序**由外到内**执行。go-zero 的注册序是 Trace→**Recover**→…→Breaker→Shedding⇒ **Recover 必须在 Breaker/Shedding 的外层**，否则它们的 panic 漏出去。Java 侧 `server.intercept(a, b, …)` 同理 ⇒ 现有的 `rpcInterceptorConfigurer` 要改成按序接多个 |
| **测试（能真触发）** | 真 in-process gRPC server + 一个必 panic 的 handler ⇒ 断言 ① 客户端 `Status.Code == INTERNAL` ② description 以 `"panic: "` 开头 ③ 进程存活、能继续服务下一个请求。**顺带关闭 `UNRESOLVED-MSG-006`**（那一条至今「未取证 RAG 抛 Error 时客户端看到什么」） |
| **成本 / 风险** | 1 个类 + 1 处装配改动 + 2~3 例测试。**低**。唯一要小心的是别让它吞掉业务异常（只接 `Throwable` 里非 `StatusException` 的那些） |

### 拆解二：breaker —— **建议签字挂起，不建议现在补**

**核心理由：go-zero 的服务端 breaker 不是经典熔断器。** `go-zero@v1.10.2/core/breaker/googlebreaker.go`：

```go
window  = time.Second * 10
buckets = 40
minK    = 1.1
// accept(): w = k - (k-minK)*failingBuckets/buckets  ⇒ 阈值随失败历史**自适应**
// 再叠加 proba.NewProba() 的**概率性丢弃**
```

⇒ 它实际上是「由失败历史驱动的**自适应概率限流**」，而非常见的「错误率 > 50% ⇒ 开路 N 秒」。

> #### 🔴🔴 状态更正 #7（2026-10-23 23:5x）：**本节上一版的核心判断是错的，已被推翻**
>
> 上一版写的是：
> > 「Java 侧要复刻**只能引 `resilience4j`**（**本仓当前零熔断依赖**，已 grep 全仓确认），
> >  而 resilience4j 是**固定阈值 + 固定开路时长** ⇒ **算法不同、参数不可能逐字对齐**」
>
> **这句话两处都错**，且是**事实性错误**（不是措辞问题）：
>
> | 上一版的说法 | 实测事实 | 证据 |
> |---|---|---|
> | 「本仓零熔断依赖 ⇒ 没有等价实现，只能引 resilience4j」 | **仓里已有一份 go-zero `googlebreaker.go` 的逐行 Java 复刻**，且**带 8 例测试** | `service/common/.../protection/GoStyleBreaker.java`（278 行）+ `GoStyleBreakerTest`（8 例，**实测绿**）。类注释逐字列了 `window=10s / buckets=40 / k=1.5 / minK=1.1 / protection=5 / forcePassDuration=1s`（`googlebreaker.go:12-21`）与 `accept()` 的**同构运算顺序**，还专门标出「**最容易被顺手改好的三处语义**」（概率性拒绝 / force-pass 保底 / 只算已完成桶）全部保持 Go 原样 |
> | 「算法不同、参数不可能逐字对齐」 | **算法就是同一套**（自适应概率丢弃），参数也逐个照抄 | 同上；`GoStyleAdaptiveShedder.java`（373 行）同理复刻了 `go-zero@v1.10.2/core/load/adaptiveshedder.go` 的**判据部分**（`shouldDrop()` 的三条件、flying β=0.9、cool-off 1s、cpuMax 1000 等），并带 10 例测试 |
>
> ⇒ **真实成本不是「引新依赖 + 算法不等价」，而是「各包一层 `ServerInterceptor` 薄壳」**。
> ⚠️ **教训**（值得单独立一条 Pitfall）：**grep 依赖清单 ≠ grep 实现**。
> 上一版只查了 `pom.xml` 里有没有 resilience4j，就断言「无等价物」——
> **而本仓的策略恰恰是「不引依赖、自己逐行复刻」**（`service/common/protection` 整个包都是这么来的）。
> ⇒ **判「有没有等价物」必须 grep 实现，不是 grep 依赖。**

**那么补 zrpc 侧还差什么（已核到消费点）**：

| 项 | Go 侧事实 | 仓里现成的部分 | 还差什么 |
|---|---|---|---|
| **隔离单位** | `breakerinterceptor.go:34` `breakerName := info.FullMethod` ⇒ **按 gRPC 全方法名** | `GoStyleBreaker(String name)` 本身与 key 无关；`GoStyleBreakerFilter`（REST 侧）用 `METHOD://path`（`go-zero@v1.10.2/rest/handler/breakerhandler.go:21`） | 薄壳要自己维护 `Map<String, GoStyleBreaker>`，key 换成 `call.getMethodDescriptor().getFullMethodName()` |
| **失败判据** | `breakerinterceptor.go:53-58` `serverSideAcceptable`：`DeadlineExceeded` 与 `ErrServiceUnavailable` ⇒ **不算失败**；其余交给 `codes.Acceptable(err)`（只有 5xx 才算） | `GoStyleBreaker` 只提供 `accept()`/`markSuccess()`/`markFailure()`，**判据在调用方** | 薄壳要实现 `serverSideAcceptable` |
| **拒绝时的 wire 契约** | `breakerinterceptor.go:45-48` + `go-zero@v1.10.2/core/breaker/breaker.go:19` ⇒ `UNAVAILABLE` + description **`"circuit breaker is open"`** | — | 薄壳要映射 |
| **类可见性** | — | `GoStyleBreaker` 是 **`final class`（包私有）** | 薄壳**必须放在 `com.rag.common.protection` 包内**，否则要改可见性（改可见性 = 动共享模块的公共面，风险更高） |
| **⚠️ 连锁风险（仍然成立，且比上一版说得更具体）** | — | `DRIFT-MSGAPI-004` 已给 api 侧加了**客户端**熔断（`GoStyleGrpcClientBreakerInterceptor`） | 服务端一旦开始吐 `UNAVAILABLE`，api 侧客户端熔断可能被打开 ⇒ **两条必须一起设计**；单独补服务端会引入一条**没人设计过的新失效模式**（客户端刚因熔断拒流、服务端也拒流 ⇒ 双侧重试放大） |

⇒ **修正后的成本判断**：breaker 侧 ≈ 1 个 ~100 行的 `ServerInterceptor` 薄壳（无新依赖、算法已对齐），
**但仍不建议本轮做** —— 理由**不是**成本高，而是上面最后那条**连锁风险**：
客户端熔断已在 api 侧就位，服务端熔断必须与之一起设计，否则会造出一条新的失效模式。
⇒ 这属于**跨组件不变量**（§3.5），单组件内闭环不了。

### 拆解三：CPU shedding —— **建议签字挂起**

| 项 | 内容 |
|---|---|
| **Go 行为** | `load.AdaptiveShedder`（CPU 采样 + BBR 式判定），阈值 `CpuThreshold default=900`（千分比，`range=[0:1000)`）⇒ 拒绝时 `sheddinginterceptor.go:33` 返回 `RESOURCE_EXHAUSTED` + description **`"service overloaded"`**（`go-zero@v1.10.2/core/load/adaptiveshedder.go:34`） |
| **⚠️ 上一版说「grpc-java 没有 CPU 自适应限流 ⇒ 难」—— 也要更正一半** | **仓里已复刻了判据**：`GoStyleAdaptiveShedder`（373 行，`allow()`/`pass(rtMs)`/`fail()`），且**刻意保留**了 Go 的三条件语义（`systemOverloaded() \|\| stillHot()` **且** `highThru()`）—— 单看 CPU 的简化移植会在「CPU 高但在途很少」时误丢请求 |
| **仍然难在哪** | **不是算法**，而是**装配语义**：① zrpc 侧是**每 server 一个全局 shedder**（`go-zero@v1.10.2/zrpc/server.go:161` `load.NewAdaptiveShedder(...)`），与 REST 侧一致；② 但 **zrpc 侧 `promise.Fail()` 的判据是 `context.DeadlineExceeded`**（`sheddinginterceptor.go:38-42`），而 **REST 侧是「响应码 == 503」**（`go-zero@v1.10.2/rest/handler/sheddinghandler.go:45`）—— **两者刻意不同**，不能照抄 Filter 的判据 |
| **风险** | 阈值配错会**主动丢真实流量**，而且这类误判在生产上极难归因 |
| **判断** | 这属于**运维策略**（要多严的降载），不该由「对齐 Go」顺手带进来 ⇒ 若要补，单独一轮 + 阈值由用户定 |

### 无论做不做都该做（便宜，本轮就该做）

1. **把方案写进本节**（已完成）—— 否则「为什么没补」只活在对话里，后人会当成漏做；
2. **登记签字偏离**（若 breaker/shedding 决定不补）：写明「真源过载/批量失败时会 `UNAVAILABLE` / `RESOURCE_EXHAUSTED` 主动拒流并保住进程，
   RAG 会继续把请求打进 PG 直到池耗尽」—— 这样后人不会把它当新 bug 去「顺手修」
   （这正是 `SD-01/02/03` 与 `SD-06` 的做法）。

### 拍板结果（2026-10-23 23:5x，用户指示）

> **用户原话**：「做recover，breaker/shedding先放着」

| 问题 | 上一版的建议 | 用户拍板 | 落地 |
|---|---|---|---|
| 1. recover 现在做吗？ | 是（成本低、无歧义、顺带关掉 `UNRESOLVED-MSG-006`） | ✅ **做** | 已实施：见本文件「V-016（recover 半边）已修的落地明细」 |
| 2. breaker / shedding 签字挂起？ | 是（若不签，将来必然有人「顺手补上」） | ⏸️ **「先放着」** —— 按**签字挂起**处理 | 登记为 [`SD-2026-10-23-07`](../../Intentional_Deviations/message/rpc.md)（含「重新评估的前置条件」） |
| 3. 若补 breaker 用什么？ | resilience4j vs 自己实现 | ⏸️ **本轮不决定** | 已被状态更正 #7 改变：**不需要 resilience4j**，用仓里已有的 `GoStyleBreaker` 复刻版 + 薄壳即可 |

**⚠️ 关于问题 2 的解读边界（请用户确认）**：用户说的是「**先放着**」，
本轮**按「挂起 = 签字」处理**（理由：签字档的作用正是「阻止后人顺手修」，
而这两条最容易被顺手补 —— breaker 看上去只差一个类）。
若用户本意是「只搁置、不算签字」，把 `SD-2026-10-23-07` 降回 `DRIFT OPEN` 即可，
**代码与测试都不受影响**（本轮对 breaker/shedding 一行代码没动）。

---

<!-- @fix 2026-10-23 23:5x 第六轮 · V-016 recover 半边已修 · breaker/shedding 转签字挂起 -->

## 2026-10-23 第六轮：`DRIFT-MSG-V-016` recover 半边已修（breaker/shedding 签字挂起）

> **用户指示**：「做recover，breaker/shedding先放着」
> ⇒ 本轮**只做 recover**，breaker / shedding **一行代码没动**，转签字档 `SD-2026-10-23-07`。

### 改了什么（3 处，同提交）

| # | 改了什么 | 文件 | 守门 |
|---|---|---|---|
| 1 | 新增 `GoStyleRecoverInterceptor implements ServerInterceptor` | `service/common/src/main/java/com/rag/common/observability/GoStyleRecoverInterceptor.java`（**新增 233 行**） | `MessageRpcRecoverInterceptorWiringTest`（5 例） |
| 2 | 装配改为 `server -> server.intercept(recoverThen(businessInterceptor))`；`recoverThen` 为 **public static** 的**链顺序唯一定义点** | `MessageRpcApplication.java`（改） | `MessageGrpcInterceptorWiringTest`（链里真能调到业务 interceptor）+ `…#recoverWrapsTheInnerInterceptor`（顺序） |
| 3 | 老断言从「交给 builder 的就是那一个 Bean」升级为「**链里真的会被调用**」 | `MessageGrpcInterceptorWiringTest.java`（改） | 见上 |

### 语义对齐的三处硬点（全部有断言）

1. **`INTERNAL` + `"panic: "` 前缀**（Go `recoverinterceptor.go:39` `status.Errorf(codes.Internal, "panic: %v", r)`）—— **wire 可见**。
   `message-api` 的 `bizCodeFromError` 会把 `INTERNAL` 映射成 5001，与 Go 一致。
2. **复刻 Go `%v` 的取值语义**：Go 的 `panic("boom")` 打印 `boom`（**不带类型名**），
   Java 直接 `toString()` 会变成 `java.lang.IllegalStateException: boom` ⇒ **wire 可见差异**，已钉死。
3. **必须包 `onHalfClose`**：grpc-java 的 unary handler 是在 `onHalfClose()` 里跑的
   （`ServerCalls.java:170` `method.invoke(request, responseObserver)`），不是在 `interceptCall` 里。

### 变异验证（3 轮，全部命中 ⇒ 不是假绿）

| 变异 | 改了什么 | 结果 |
|---|---|---|
| MUTATION-1 | 摘掉 `onHalfClose` 的兜底（模拟「只包 startCall」的经典错法） | **2/5 转红**：`expected: <INTERNAL> but was: <UNKNOWN>` |
| MUTATION-2 | description 前缀 `"panic: "` → `"boom: "` | **2/5 转红**：`description 必须以 "panic: " 开头，实际=boom: 测试用 panic` |
| MUTATION-3 | 不复刻 Go `%v`（直接 `t.toString()`） | **1/5 转红**：`Go 的 panic 值不带类型名；RAG 直接 toString() 会多出类型名` |

### ⚠️ 本轮踩的坑（已记入 `docs/Pitfalls/message/rpc.md`）

**grpc-java 的 unary 响应必须显式 `observer.onCompleted()`** —— `ServerCalls.java:394`
`onCompleted()` 才 `call.close(Status.OK)`，只 `onNext` 的话 call 永不关闭 ⇒
**客户端 blocking stub 永久挂起**。
本类第一版就漏了这一句 ⇒ 整轮 surefire **挂死 4 分钟**（不是失败，是挂住）。
⇒ 已在测试里加两道护栏：① stub 上 10s deadline（会抛 `DEADLINE_EXCEEDED`，失败而非挂死）；
② 类级 `@Timeout(60s)`。
**教训**：**写 in-process gRPC 测试时，service impl 必须 `onNext` + `onCompleted` 成对出现** ——
生成的 stub 只保证「方法体」不保证「响应完成」。

### 本轮连带的状态变更

| 条目 | 变更 |
|---|---|
| `DRIFT-MSG-V-016` | 🟡 OPEN → **部分已处置**（recover ✅ / breaker+shedding ⏸️ 签字挂起） |
| `UNRESOLVED-MSG-006` | 🔴 未取证 → ✅ **已关闭**（`Error` 也被兜住 ⇒ `INTERNAL` + `"panic: "` 前缀） |
| `SD-2026-10-23-07` | 🆕 新增（breaker / CPU shedding 挂起） |
| 台账实测数字 | 🔴 **两处过期值已更正**（message-rpc 51 → 54；DDL 差异 23 行 → 19 行），详见上文「状态更正 #6」 |

### ⚠️ 跨域风险提示（本轮新增共享模块代码）

`GoStyleRecoverInterceptor` 落在 **`service/common`** —— 该模块被 **10 个 rpc + 10 个 api = 20 个组件**依赖
（2026-10-24 实测 `pom.xml` 统计；⚠️ **此前写的「16 个 rpc 组件」是错的**，已在三处更正），
按 `docs/Worktree.md` §三 它属于**共享面**。其它域的并发会话也可能同时改 `service/common`
⇒ **合并时需人工确认无冲突**。本轮对 `service/common` 的改动**只有「新增一个类」**，
未改动任何既有文件 ⇒ 冲突面仅限新增文件名。


---

<!-- @fix 2026-10-24 第七轮 · V-016 breaker/shedding 半边已落地（自实现 + service/common 共享面） -->

## 2026-10-24 第七轮：`DRIFT-MSG-V-016` breaker / shedding 半边已落地

> **用户指示序列**（本轮）：
> ① 「我们先讨论 breaker/shedding，能不能用多个 java 组件来表达」
> ② 「只能自实现不能用通用 java 组件吗，比如 Sentinel 的滑动窗口 + 自写概率层」
> ③ 「所以你是说自实现而不是用成品来组装？」
> ④ 「那你要怎么自实现 breaker/shedding」
> ⑤ 「1、你的 common 是 service/common 还是 message/common；2、接受 fail-open」
>
> ⇒ **结论**：**自实现 + 薄壳**，放在 **`service/common`**（不是 `message-common`），
> 失败判据**只取 Go 的第二层**（6 码白名单），CPU 采样 **fail-open**（用户明示接受）。

### 1. 为什么是「自实现」而不是「成品组装」（三条实测结论）

用户第 ②③ 问的是「能不能用通用 java 组件」。本轮**实测了三个候选**，结论是**都不能用**：

| 候选 | 实测/查证结果 | 结论 |
|---|---|---|
| **Sentinel 1.8.6** | `javap` 反编译：`AbstractCircuitBreaker` / `ExceptionCircuitBreaker` 是**确定性阈值 + 全拒 + 半开**；`SimpleErrorCounter` 只记 `errorCount/totalCount`（无滑动窗口）；`LeapArray` 是**抽象类**且 `list()` **含当前桶** | 🔴 **与 Go 语义相反**。Go 是「按失败历史**自适应**的阈值 + **概率性**丢弃」，Sentinel 是「错误率超阈值 ⇒ 全拒」。用 Sentinel = 把 Go 的概率丢弃换成确定性全拒 ⇒ 拒绝面**扩大** |
| **Hystrix** | 10 桶 × 1s、每桶记成功/失败/超时/拒绝、`allowSingleTest` —— 结构最像 | 🔴 但阈值**固定**（`errorThresholdPercentage=50`）⇒ Go **没有**「错误率 > 50% 就开路」这条规则 ⇒ **算法不等价**；且 **2018-11 进维护模式、2020-12 归档退役**（后继 Resilience4j） |
| **resilience4j** | 上一版台账曾断言「本仓零依赖 ⇒ 只能引它」，已被**状态更正 #7** 推翻 | 🔴 同上：固定阈值 + 固定开路时长 ⇒ 与 Go 的自适应概率丢弃**不等价**；且本仓策略就是「不引依赖、自己逐行复刻」 |

> ⚠️ **判「有没有等价物」必须 grep 实现，不是 grep 依赖** —— 已立为 `PITFALL-MSG-R-013`。
> 本仓 `service/common/protection` 已有 go-zero `googlebreaker.go` 与 `adaptiveshedder.go` 的**逐行 Java 复刻**
> （`GoStyleBreaker` 278 行 / 8 例、`GoStyleAdaptiveShedder` 373 行 / 10 例）⇒ **只差一层 `ServerInterceptor` 薄壳**。

### 2. 改了什么（4 个新类 + 1 个既有类 + 1 处装配）

| # | 文件 | 内容 | 守门 |
|---|---|---|---|
| 1 | `service/common/.../protection/GoStyleRpcFailurePolicy.java`（新） | **失败判据**：Go `serverSideAcceptable` 的第二层（6 码白名单） | `GoStyleRpcFailurePolicyTest`（4 例） |
| 2 | `service/common/.../protection/GoStyleRpcBreakerInterceptor.java`（新） | 观察点 = `ServerCall.close(status, trailers)`；隔离 key = `getFullMethodName()`；拒绝 ⇒ `UNAVAILABLE + "circuit breaker is open"` | `GoStyleRpcBreakerInterceptorTest`（5 例）+ `MessageRpcBreakerWiringTest`（2 例，真 in-process gRPC） |
| 3 | `service/common/.../protection/GoStyleRpcSheddingInterceptor.java`（新） | **全局一个** shedder；拒绝 ⇒ `RESOURCE_EXHAUSTED + "service overloaded"`；`DEADLINE_EXCEEDED ⇒ fail()`，否则 `pass(rtMs)` | `GoStyleRpcSheddingInterceptorTest`（4 例） |
| 4 | `service/common/.../protection/GoStyleRpcProtectionChain.java`（新） | **链顺序唯一定义点**：`recover → breaker → shedding → business`；`recoverThen` 从 message-rpc **上提到 common** | `GoStyleRpcProtectionChainTest`（3 例） |
| 5 | `service/common/.../protection/GoStyleBreaker.java`（改） | 新增**包私有** `long historyTotal()`（排障/测试用，只算**已完成**桶） | 同上 |
| 6 | `service/message/message-rpc/.../MessageRpcApplication.java`（改） | `@Import` 加 `GoStyleProtectionConfiguration.class`；`rpcInterceptorConfigurer` 改用 `GoStyleRpcProtectionChain.build(...)`；**删除**本地 `recoverThen` | `MessageGrpcInterceptorWiringTest`（断言从「同一个对象」升级为「业务被调到 + **call 被包装了**」） |

### 3. 语义对齐的硬点（每条都有断言）

1. **失败判据只看 close status，不深查 cause 链** ——
   Go 的 `serverSideAcceptable` 是**两层**：① `errorx.In(err, context.DeadlineExceeded, breaker.ErrServiceUnavailable)`（**深查** `errors.Is`）
   → ② `codes.Acceptable(err)`（**浅查** `status.Code(err)`，6 码白名单）。
   本仓**只保留 ②**，三条论证（详见 `GoStyleRpcFailurePolicy` 类注释）：
   ① `context.DeadlineExceeded` 在 Java 侧最终体现为 `close(DEADLINE_EXCEEDED)` ⇒ ② 已覆盖；
   ② `breaker.ErrServiceUnavailable` 在 Java 侧被 rpc 层 `invoke()` 统一转 `INTERNAL` ⇒ ② 已覆盖；
   ③ **grpc-java 的 `ServerCall.Listener` 没有 `onError(Throwable)`**（那是 `StreamObserver` 的方法），
   `ServerCall` 也**没有** `getDeadline()` ⇒ **想做深查也没有输入**。
   ⚠️ 副作用：`Status.withCause` **保留原 code**、不从 cause 反推 ⇒ 与上述压缩一致。

2. **隔离 key = gRPC 全方法名** —— Go `breakerinterceptor.go:34` `breakerName := info.FullMethod`；
   RAG 用 `call.getMethodDescriptor().getFullMethodName()`（**不是** REST 侧那套 `METHOD://path`）。

3. **拒绝时的 wire 契约逐字对齐** —— breaker ⇒ `UNAVAILABLE` + description `"circuit breaker is open"`；
   shedding ⇒ `RESOURCE_EXHAUSTED` + description `"service overloaded"`（Go `go-zero@v1.10.2/core/load/adaptiveshedder.go:34`）。

4. **shedding 的 promise 判据** —— Go `sheddinginterceptor.go:38-42` 用 **`context.DeadlineExceeded`** 判 `promise.Fail()`；
   RAG 用 `DEADLINE_EXCEEDED` status 判 `fail()`。**message-rpc 业务代码从未显式造 `DEADLINE_EXCEEDED`**
   ⇒ 两侧等价。⚠️ 拒绝路径**不结算 promise**（与 Go 的 `promise` 不结算同形）。

5. **拒绝必须关「本层包装过的 call」** —— 链序 Breaker→Shedding 要求错误冒泡到外层，
   所以 shedding 拒绝时关的是**自己包过的那层 call**，不是原始 call ⇒ `PITFALL-MSG-R-015`。

6. **默认开关对齐 Go 的 `default=true`** —— Go `go-zero@v1.10.2/zrpc/internal/config.go:21/25` `Middlewares.Breaker default=true`；
   `go-zero@v1.10.2/zrpc/config.go:47` `CpuThreshold default=900` ⇒ RAG `GoStyleProtectionProperties` 同默认 ⇒ **默认开启**。

### 4. 双侧对拍探针（新增 `tools/breaker-probe/`）

| 组 | 做什么 | 结果 |
|---|---|---|
| **A** | 17 条**判据**逐条对拍（Go 侧直接调真源 `zrpc/internal/codes.Acceptable`） | ✅ **逐条一致** |
| **B** | **冷启动保护前缀**（`forcePass` 保底） | ✅ **逐字一致**（`prefix=AAAAA`） |
| **C** | **拒绝率**统计比对 | ✅ go=0.2500 / rag=0.2000（差 0.05 ≤ 容差 0.15） |

> ⚠️ **脚本第 2 步会从 module cache 抽取真源 `go-zero@v1.10.2/zrpc/internal/codes/accept.go` 逐字比对副本**，不一致即 `exit 1`
> ⇒ 探针**不能用改过的 Go 源码自证**（已实测通过）。
>
> ⚠️ **C 组是「健全性检查」而非「等价性证明」**：Go 的随机源 `mathx.NewProba()` =
> `rand.New(rand.NewSource(time.Now().UnixNano()))` ⇒ **不可播种、不可复现**；
> 且算法**按墙钟分桶** ⇒ 两侧执行速度不同就会漂移。
> ⇒ **真正的逐条对拍是 A 组与 B 组**；C 组只用来证明「两侧都会拒流、且量级相近」。

### 5. 🔴 本轮**新挖出**的三条漂移（不是实现错，是 Go 与 RAG 的真实分岔）

> ⚠️ **2026-10-24 第十轮加的前向指针 —— 本表是 2026-10-23 第五轮的快照**：
> `DRIFT-MSG-V-024` / `V-025` / `V-026` **三条现已全部处理完毕** ——
> `V-024` ✅ 已修（第八轮，rpc 兜底码 `INTERNAL`→`UNKNOWN`）、
> `V-025` ✅ 已修（第九轮，`GoStyleSheddingStat` 三计数 + 每分钟统计日志）、
> `V-026` ✅ 已修（第九轮，**8 个 rpc 组件全部接入**熔断/降载链）。
> ⇒ **下面「🔴 OPEN」读作「2026-10-23 第五轮当时的判断」，不是当前状态。**

| 条目 | 内容 | 状态 |
|---|---|---|
| **`DRIFT-MSG-V-024`** | **DB / 未知异常：Go 保留 `UNKNOWN`，RAG 转成 `INTERNAL`** ⇒ **熔断判定分岔**。Go 侧 `getconversationmessageslogic.go:48` 等 **19 处** `ObserveDBError` 后**原样返回普通 error** ⇒ 客户端见 `UNKNOWN`、**不计熔断失败**；而 RAG 的 `MessageRpcServiceImpl.invoke:133-140` 把 `RuntimeException` 转 `INTERNAL` ⇒ **计熔断失败** | 🔴 **OPEN**（**本轮未改**：会改客户端可见码、牵动 api 侧映射） |
| **`DRIFT-MSG-V-025`** | **指标缺口**：`GoStyleAdaptiveShedder` **无** Go `SheddingStat` 的 total / drop / pass 三计数 | 🔴 **OPEN** |
| **`DRIFT-MSG-V-026`** | **其余 9 个 rpc 组件尚未接入**本链（域间不变量未闭合） | 🔴 **OPEN**（**本轮不改**：域间纪律） |

> ✅ **2026-10-24 第九轮更正**：本表记录的是**第七轮当时**的状态。
> ⇒ **V-024 / V-025 / V-026 三条已全部修掉**（见下方「第九轮」大节），
> 其中 V-026 是**跨域改动**（改了 8 个 rpc 组件）⇒ 合并时需人工确认。

### 6. 本轮连带的状态变更

| 条目 | 变更 |
|---|---|
| `DRIFT-MSG-V-016` | 🟡 部分已处置 → ✅ **三个拦截器全部落地**（recover / breaker / shedding） |
| `SD-2026-10-23-07` | 🗄️ **已撤销**（2026-10-24）—— 它的内容是「本轮不实施（挂起式签字）」，现已实施 ⇒ 留着会**骗后人以为没做**。⚠️ 同时**更正它当年的「连锁风险」结论**：Go 本来就有「客户端熔断 + 服务端拒流」的放大效应，**不是 RAG 引入的新失效模式** |
| `SD-2026-10-23-08` | 🆕 新增（**CPU 采样读不到时 fail-open，不降载**）—— 用户明示「接受 fail-open」 |
| `PITFALL-MSG-R-014/015/016` | 🆕 新增 3 条（链不能透传 `next` / 拒绝须关本层包装过的 call / 被 recover 兜住的异常让链路看起来「没被调用」） |

### 7. ⚠️ 共享面冲突提示（本轮改了**既有**共享类）

`service/common` 被 **10 个 rpc + 10 个 api = 20 个组件**依赖（2026-10-24 实测 `pom.xml` 统计），
按 `docs/Worktree.md` §三 它属于**共享面**。
⚠️ **本轮与上一轮不同**：上一轮只「新增一个类」，**本轮改了既有类 `GoStyleBreaker`（加 `historyTotal()`）**
⇒ 合并时**必须人工确认** `service/common` 无冲突（其它域的并发会话可能同时改该模块）。

### 8. 实测数据（本轮当场实测，2026-10-24）

```
service/common ：162 tests / 0 failures / 0 errors / 0 skipped   BUILD SUCCESS
message-common ： 21 tests / 0 failures / 0 errors / 0 skipped
message-rpc    ： 61 tests / 0 failures / 0 errors / 0 skipped   BUILD SUCCESS
message-api    ： 84 tests / 0 failures / 0 errors / 0 skipped
⇒ 合计 328 例，0 failures / 0 errors / 0 skipped
semantic-probe ：56 例 → 一致 54，不一致 0，已签字偏离 2         EXIT=0
breaker-probe  ：A 组 17 条逐条一致 / B 组 prefix=AAAAA / C 组 go=0.25 rag=0.20   EXIT=0
```

---

<!-- @fix 2026-10-24 第八轮 · 真双栈对拍首次跑通 · 连带修掉 2 个「根本起不来」级缺陷 -->

## 2026-10-24 第八轮：**真双栈对拍首次跑通** + 连带挖出 2 个「根本起不来」级缺陷

> **用户指示**：「起真双栈对拍，一次关 4 项未知（difftest 12 档 + 响应头 dump + 503 超时取证 + MSGAPI-010）」
> ⇒ 本轮**第一次**真的把 Go ↔ RAG 两套栈同时起起来并对拍。

### 0. 一句话结论

| 交付项 | 结果 |
|---|---|
| **12 档 gRPC 双端口对拍** | ✅ **11 条 status 码 + happy path 全部逐条一致**（EXIT=0）⇒ **关闭 `UNRESOLVED-MSG-003`** |
| **真容器响应头 dump** | ✅ 4 条路径 × 两侧全量响应头取证 ⇒ **关闭 api `UNRESOLVED-003`**；**`DRIFT-MSGAPI-010` 在真容器级确认** |
| **3s 超时 503 取证** | ✅ 真 Tomcat + 慢 stub ⇒ **关闭 api `UNRESOLVED-004`**；顺带**修掉**一条真实差异（缺 `nosniff`） |
| **附带挖出** | 🔴 **2 个「根本起不来」级缺陷**（Flyway baseline 跳过 V1 / message-api jar 不可执行） |

---

### 1. 🔴 `DRIFT-MSG-V-027`（新挖出 · 已修）：**Flyway 的 baseline 把 V1 整条跳过 ⇒ `message_notification` 永远建不出来**

**Component**: `message-rpc`　**Operation**: 启动（全部 RPC）
**Category**: DDL / Lifecycle　**Classification**: **`SEMANTIC MISMATCH`（服务起不来）**
**Risk**: **HIGH（生产阻断）**　**发现时间**: 2026-10-24（起真栈时暴露）

```
现象（真 PG 16 + 真 jar 实测）：
  message-rpc 与 user-rpc / admin-rpc 连**同一个库**（生产 first_db / 对拍 rag_db）
  ⇒ user-rpc / admin-rpc 用 SchemaInitializer 幂等重放（无账本），**它们先启动**
  ⇒ public schema 已经**非空**（users_user / admin_admin …）
  ⇒ message-rpc 启动时 Flyway 看到「非空 schema + 没有自己的账本」
  ⇒ 因 baseline-on-migrate=true ⇒ **在 V1 处打 baseline 标记**
  ⇒ **V1 的 SQL 从未执行** ⇒ message_notification 表建不出来
  ⇒ V5（ALTER 那 8 个时间列）报 `relation "message_notification" does not exist"
  ⇒ **服务起不来**

账本实况（修复前）：
  installed_rank | version |             description             | type
  ----------------+---------+------------------------------------+-------
                1 | 1       | << Flyway Baseline >>               | BASELINE
                2 | 2       | create message conversation         | SQL
                3 | 3       | create message conversation message | SQL
                4 | 4       | conversation pair unique index      | SQL
```

**为什么 Testcontainers 永远测不出来**：`MessageTransactionWiringTest` 每个用例都是**全新空库** + 只有 message-rpc 一个进程
⇒ schema 是空的 ⇒ baseline 根本不触发 ⇒ V1 正常执行
⇒ **「测试全绿」与「生产拓扑下根本起不来」同时成立**（§3.3 的经典形态）。

**修法**：`spring.flyway.baseline-version: 0` ⇒ baseline 标记打在 **version 0**（V1 之下）
⇒ **V1..V5 五条全部真实执行** ⇒ `message_notification` 建出 ⇒ `Started MessageRpcApplication in 4.09 seconds`。

**为什么不是关掉 `baseline-on-migrate`**：`baseline-on-migrate=false` 时，非空 schema 且无账本
⇒ Flyway **直接抛异常退出**（"Found non-empty schema(s) … but no schema history table"）⇒ 更糟（起不来且无修复路径）。

**守门**：`MessageFlywaySharedSchemaWiringTest`（3 例，真 PG）
- 关键手法：在 `@DynamicPropertySource` 里**先建一张无关表** ⇒ 把 schema 弄成非空
  （放 `@BeforeEach` 无效 —— 那跑在上下文之后，Flyway 早就迁移完了）
- 断言：`message_notification` 存在 / 账本的 BASELINE 行版本必须是 **0** / V1..V5 全是 SQL 行
- **变异验证**：去掉 `baseline-version: 0` ⇒ **3/3 转红**（Context 起不来）⇒ 不是假绿

⚠️ **口径纠正（实测）**：`baseline-on-migrate=true` 时 Flyway **一定会**写一行 BASELINE
⇒ 断言口径不是「不允许有 BASELINE 行」，而是「**BASELINE 行的版本必须是 0**」。
（第一版把断言写成「不允许出现 BASELINE 行」⇒ 连修好后的状态也判红 ⇒ 被本测试当场抓出来。）

> ⚠️ **跨域影响（不在本轮范围）**：同一套写法（独立账本 + `baseline-on-migrate=true` + 共享 schema）
> 在 **`like-rpc`** 上**同样成立** ⇒ 它的迁移从 V1 起步 ⇒ V1 会被顶掉 ⇒ `like_record` 建不出来。
> ⚠️ **`article-rpc` 经实测不受影响**（迁移从 V8 起步，没有 V1 可跳）⇒ 已从本条剔除。
> ⇒ 登记为跨组件待办，见 [`DRIFT-MSG-V-029`](#drift-msg-v-029-🆕-跨组件待办like-rpc-有与-v-027-同款的-flyway-baseline-缺陷article-rpc-经核实不受影响已从本条剔除)。

---

### 2. 🔴 `DRIFT-MSG-V-028`（新挖出 · 已修）：**`message-api` 的 jar 根本不可执行**

**Component**: `message-api`　**Operation**: 打包 / 部署
**Category**: Build / Delivery　**Classification**: **`SEMANTIC MISMATCH`（产物不可运行）**
**Risk**: **HIGH（部署阻断）**

```
service/message/message-api/pom.xml **完全没有 <build> 段**
⇒ 没有 spring-boot-maven-plugin 的 repackage
⇒ 打出来的是**普通 library jar**：实测 76 KB（而 message-rpc 的可执行 jar 是 69 MB）
⇒ `java -jar message-api-*.jar` 直接报：「中没有主清单属性」（no main manifest attribute）
⇒ **message-api 从来没被打成过可部署产物**
   （此前所有验证都跑在容器里 @SpringBootTest，从没跑过真 jar）
```

**修法**：补 `<build>` 段（`spring-boot-maven-plugin` + 与 user-api 同款 surefire Ryuk 关闭段）。
⇒ 实测产物 **71 MB**，`Main-Class: org.springframework.boot.loader.launch.JarLauncher`、
`Start-Class: com.rag.message.message.api.MessageApiApplication` ⇒ **真 jar 起得来**。

⚠️ **这正是 §3.3「编译通过 ≠ 产物可运行」的形态**：`mvn package` 成功、测试全绿，但产物根本起不来。
⚠️ **只有 message-api 漏了**：`message-rpc` 的 pom 早就有这一段（`:230-234`）⇒ rpc 侧不受影响。

---

### 3. ✅ `DRIFT-MSG-V-024`（已修）：DB / 未知异常走 `UNKNOWN`（与 Go 的熔断判据对齐）

**改动**：`MessageRpcServiceImpl#invoke` 的兜底 `Status.INTERNAL` ⇒ **`Status.UNKNOWN`**，
且 **description 留空**。

**为什么 description 留空、而不是抄 Go 的原始错误文本**：
Go 的 api `shared.RPCError` 会把 gRPC description **原文**塞进 HTTP envelope 的 msg
⇒ Go 客户端能直接看到 SQL 错误文本（**信息泄露**）。
本仓 `DRIFT-MSG-V-003` 已就这一点**签字保留 RAG 侧**（envelope 只回码表文案）
⇒ 若把原始文本抄过来，等于在**签字之外**又引入一次泄露。
⇒ description 留空 ⇒ api 侧走「反查未命中 ⇒ 按 status code class 兜底」那一支
⇒ **业务码 1015 与 Go 一致**，且 envelope 的 msg 由码表给出（不泄露）。

**守门**：`MessageRpcUnknownStatusWiringTest`（4 例，真 gRPC）
- DB 异常 ⇒ 客户端看到 **UNKNOWN**（不是 INTERNAL）
- description 必须为 **null**（原始文本不得进 wire）
- api 侧业务码必须 **1015**（Go 的 `CodeServerBusy`）
- 业务错误（`StatusRuntimeException`）仍然原样转发
**变异验证**：改回 `INTERNAL + "internal server error"` ⇒ **3/4 转红**（UNKNOWN→INTERNAL、1015→5001、description null→非 null）

---

### 4. ✅ `UNRESOLVED-MSG-003`（已关闭）：12 档 gRPC 双端口对拍

```
== message 域 Go ↔ RAG 双端口对拍 ==
  go  message-rpc = 127.0.0.1:20082
  rag message-rpc = 127.0.0.1:60210

  ✓ INVALID_ARGUMENT: listNotifications(userId=0)
  ✓ INVALID_ARGUMENT: getUnreadSummary(userId=0)
  ✓ INVALID_ARGUMENT: markNotificationRead(uid=1, nid=0)
  ✓ INVALID_ARGUMENT: markAllNotificationsRead(userId=0)
  ✓ NOT_FOUND: markNotificationRead(ghost notificationId)
  ✓ INVALID_ARGUMENT: getConversationMessages(conversationId=0)
  ✓ NOT_FOUND: getConversationMessages(ghost conversationId)
  ✓ INVALID_ARGUMENT: sendChatMessage(content="")
  ✓ INVALID_ARGUMENT: sendChatMessage(self-chat USER 1 → USER 1)
  ✓ PERMISSION_DENIED: sendChatMessage(USER 1 → ADMIN 2, no conversation)
  ✓ INVALID_ARGUMENT: sendChatMessage(first message 51 runes > 50)
  ✓   └─ conversation_id 非 0 / message.conversation_id == conversation_id / status == PENDING / can_send == false

exit code: 0 (0 = message 域双端口等价 / 1 = 至少一档不符)
```

⇒ **证据强度**：这是本仓 message 域**第一次**真正的**双侧活体对拍**
（此前只有「源码级对照」与「单侧真 PG」两档）⇒ §0 的「证据强度分层」可把 message 域上调一档。

---

### 5. 本轮为了让对拍能跑起来，**顺带修掉的 harness 缺陷**（`tools/difftest/`）

| # | 缺陷 | 影响 | 修法 |
|---|---|---|---|
| H1 | **Go 侧 message-api 从没被起过** | message-api 的 HTTP 面**从来没有**双侧对拍过 | 新增 `conf/go/message-api.yaml`（Etcd → Endpoints 直连、端口独占 18892）+ run.sh 里构建/启动/就绪校验 |
| H2 | **容器名与端口全写死** | 多域并发对齐时，第二份实例会 `docker compose down -v` **删掉别人正在用的 PG 数据卷** | 全部参数化：`DIFTEST_PORT_OFFSET` / `DIFTEST_CONTAINER_PREFIX` / `DIFTEST_PG_PORT` / `DIFTEST_REDIS_PORT`；默认值保持不变 ⇒ **老调用方零改动** |
| H3 | **端口无预检** | 端口被别的容器占着时，症状出现在 docker compose 里（`port is already allocated`），看着像自己配置写错 | 起栈前逐端口预检，被占就报出**是谁**占的（容器名 / 进程名）并退出 |
| H4 | **端口越界无校验** | Redis 56379+10000=66379 > 65535 ⇒ compose 报 `invalid hostPort`；RAG 59210+10000=69210 ⇒ Java 栈深处的 `port out of range`（**看半天才看得出是端口算错**） | 全部端口统一校验，越界就带着可操作的提示立刻退出 |
| H5 | **`POSTGRES_ADDR` 与 `POSTGRES_HOST` 占位符不一致** | 全仓 jdbc url 里 **7 个 rpc 模块用 `POSTGRES_HOST`**（message/article/comment/favorite/follow/like/task），admin-rpc 用 `POSTGRES_ADDR`；而 harness 只导出 `POSTGRES_ADDR` ⇒ **这 7 个模块在对拍环境里都起不来**（`UnknownHostException: postgres`） | 两个名字**都导出**（同值）⇒ **这 7 个模块现在都能被对拍了** |
| H6 | `set -euo pipefail` + 命令替换里的 `lsof` | `lsof` 无匹配时退出码 **1** ⇒ `pipefail` 让命令替换返回非零 ⇒ `set -e` **静默杀掉整个脚本**（一个字都不报错） | 用 `{ …; } \|\| true` 兜住整条管道 |

⚠️ **H5 的意义远超本轮**：它意味着 **「message 域从没被 difftest 覆盖过」这件事的根因就在这里** ——
不是没人写脚本，是**脚本起不来**。

---

### 6. 本轮实测数据（2026-10-24 当场实测）

```
【双侧活体对拍（本仓 message 域首次）】
  12 档 gRPC 对拍：11 条 status 码 + happy path ⇒ 全部一致            EXIT=0
  真容器响应头 dump：4 条路径 × 两侧全量响应头 ⇒ 逐条比对（见 api 台账）
  3s 超时 503：真 Tomcat + 慢 stub ⇒ 503 + text/plain;charset=utf-8
                                    + nosniff + "Request Timeout\n"

【测试】2026-10-24 当场实测
  service/common ：162 tests / 0 failures / 0 errors / 0 skipped
  message-common ： 21 tests / 0 failures / 0 errors / 0 skipped
  message-rpc    ： 68 tests（61 + 3 Flyway 守门 + 4 V-024 守门）/ 0 failures / 0 errors / 0 skipped
  message-api    ： 86 tests（84 + 2 503 超时取证）/ 0 failures / 0 errors / 0 skipped
  ⇒ 合计 **337 例**，0 failures / 0 errors / 0 skipped，BUILD SUCCESS

【变异验证（本轮新增）】
  Flyway：去掉 baseline-version: 0            ⇒ 3/3 转红
  V-024：改回 INTERNAL + "internal server error" ⇒ 3/4 转红
  nosniff：删掉 setHeader("X-Content-Type-Options","nosniff")
                                          ⇒ 1/2 转红
            （AssertionFailedError: expected: <nosniff> but was: <null>）
            ⇒ 证明这条守门**不是假绿**

【全仓校验】2026-10-24 当场实测
  mvn -o -B -DskipTests package ⇒ 38 个模块全部 SUCCESS（42.9s，BUILD SUCCESS）
  ⇒ 本轮对**共享面** `service/common` 的改动（nosniff）**未破坏其余 9 个域的编译**
```

> ⚠️ **2026-10-24 补记（本条在提交前自查时又挖出一条坑）**
>
> 复核「注释改动有没有改坏 YAML」时，跑 `mvn -pl service/message/message-rpc test`（**没带 `-am`**）
> ⇒ 几乎所有 `@SpringBootTest` 用例报 `NoClassDefFoundError: GoStyleRpcProtectionChain`
> ⇒ **看着像「我刚改坏了」，其实不是**：那个类在本工作树源码里**确实存在**，
> 但 `~/.m2` 里的 `common-0.0.1-SNAPSHOT.jar` 被**主仓**（`do-my-own-gh`）那次 `mvn install` **覆盖**了
> ⇒ 主仓那版 `service/common` **没有**这个类（主仓 11 个 protection 类 vs 工作树 12 个）。
> ⇒ 带 `-am` 重跑 ⇒ 全绿。
> ⇒ **教训**：多工作树共用一个 `~/.m2` ⇒ SNAPSHOT 是「最后写入者赢」的共享可变状态
> ⇒ **凡验证行为，必须从本工作树源码构建** ⇒ 单模块测试一律带 `-am`。
> ⇒ 完整经过 ⇒ [`PITFALL-MSG-R-021`](../../Pitfalls/message/rpc.md)。


---

### 7. ⚠️ 本轮改动落在**共享面**（`service/common`）

- `HttpMiddleware#writeTimeoutResponse` **补 `X-Content-Type-Options: nosniff`**（Go 的 `http.Error` 会写）
  ⇒ 影响**全部 10 个 api**（共享模块）⇒ 合并时需人工确认。
- ⚠️ 另：Go 侧 `net/http` 的 `http.Error` 还会 `h.Del("Content-Length")` ⇒ chunked；
  RAG 侧同样是 chunked ⇒ **这一项一致**。

---

## 2026-10-24 第九轮：**V-018 / V-025 / V-026 三件全做** + 挖出 **6 个 api 模块的同款「产物不可执行」**缺陷

> **用户指示（本轮）**：
> ① 共享层的两件（`MSGAPI-010` charset / `MSGAPI-011` reason phrase）**不做**，保持登记在台账；
> ② `like-rpc` 的同款 Flyway baseline 缺陷（V-029）**留给 like 域自己的 session**；
> ③ 域内剩余三件（V-018 / V-025 / V-026）**全做**；
> ④ 方向：**继续深挖 message 域**。

---

### 1. ✅ `DRIFT-MSG-V-018` 已修：`ListConversations` 的判定顺序 + `count_unread` 埋点

**Go 出处（消费点）** —— `service/message/rpc/internal/logic/helper.go:160-187`：

```go
func buildConversationItem(ctx, svcCtx, conv, operatorRole, operatorID) (*pb.ConversationItem, error) {
    peerRole, peerID, ok := peerOf(&conv, operatorRole, operatorID)
    if !ok {
        return nil, status.Error(codes.PermissionDenied, messagecommon.GetErrMsg(messagecommon.ErrorForbidden))
    }                                                       // ← ① 先判，短路
    unread, err := svcCtx.MessageModel.CountUnreadMessagesByConversation(ctx, conv.ID, int32(operatorRole), operatorID)
    if err != nil {
        metrics.ObserveDBError("conversation", "count_unread")   // ← ② 有埋点
        return nil, err                                        // ← 原样返回裸 error
    }
    name, email := loadProfile(ctx, svcCtx, peerRole, peerID)   // ← ③ 最后才取 profile
    ...
}
```

**改之前 RAG 的两处偏差**（`MessageDomainService#doListConversations`）：

| # | 偏差 | 可观察后果 |
|---|---|---|
| a | **顺序反了**：先 `count_unread` 再 `peerOf` | peerOf 失败时**白查一次 DB**（Go 不会）；且那次查询失败**不产生任何埋点** |
| b | **少了埋点**：`count_unread` 抛异常时**没有** `observeDbError("conversation","count_unread")` | `message_rpc_db_error_total{action="conversation",type="count_unread"}` 在这条路径上**永不增长** ⇒ 看板会误以为「从没发生过 count_unread 失败」 |

**改法**：把 `peerOf` 判定提到 `count_unread` **之前**（`!found` ⇒ `PERMISSION_DENIED` + `5005`，
与 Go 的 `ErrorForbidden = 5005` 一致），并给 `count_unread` 补上 try/catch + 埋点 + 原样上抛。

⚠️ **可观察结果只变了一处**：peerOf 失败时的**错误码不变**（仍是 `PERMISSION_DENIED` + 5005），
只是**不再白查一次 DB**；`count_unread` 失败时**多了一个指标**。

**守门**：`MessageListConversationsParityTest`（4 例，纯 mock 单测）
- ① peerOf 失败 ⇒ 抛 `PERMISSION_DENIED`，且 `countUnreadMessagesByConversation` **从未被调用**，
  且**不**产生任何 `observeDbError`；
- ② count_unread 失败 ⇒ `observeDbError("conversation","count_unread")` **必须被调用**，且异常**原样上抛**；
- ③ count_unread 成功 ⇒ **不**产生该埋点；
- ④ `ERROR_FORBIDDEN == 5005`（与 Go `errmsg.go:36` 对齐）。

**变异验证**：删掉 `metrics.observeDbError("conversation","count_unread")` ⇒ **1/4 转红** ⇒ 不是假绿。

---

### 2. ✅ `DRIFT-MSG-V-025` 已修：降载的三个计数 + 每分钟统计日志（**含一条口径更正**）

#### 🔴 口径更正：原记「Go 侧可以在 `/metrics` 里看到」是**错的**

实测 Go 源码（go-zero v1.10.2）后确认：

| Go 侧的东西 | 实际暴露方式 | 出处 |
|---|---|---|
| 降载的 `total` / `pass` / `drop` | **每分钟一行的日志**（`logx.Statf`）⇒ **不在 Prometheus 里** | `go-zero@v1.10.2/core/load/sheddingstat.go:52-70` |
| 熔断的 `markDrop` / `markFailure` | 写进 `b.stat`（`collection.RollingWindow`）⇒ **熔断算法自己的状态**，**根本没有对外暴露** | `go-zero@v1.10.2/core/breaker/googlebreaker.go:121-130` |

⇒ 所以本条的**真实缺口**不是「RAG 少了 Prometheus 指标」，而是：
**RAG 侧连 Go 有的那行日志都没有** ⇒ 降载在不在工作、丢了多少，**从任何地方都看不出来**。

#### 改法（1:1 照 Go，**零依赖变更**）

- 新增 `service/common/.../protection/GoStyleSheddingStat.java`
  ⇒ 三个 `AtomicLong`（`total` / `pass` / `drop`）+ `snapshot()` + `reset()` +
  **逐字照抄** Go 的日志格式：
  `(rpc) shedding_stat [1m], cpu: 123, total: 1, pass: 1, drop: 0`
  `(rpc) shedding_stat_drop [1m], cpu: 123, total: 1, pass: 1, drop: 1`
  （`drop == 0` 用 `shedding_stat`，`drop > 0` 用 `shedding_stat_drop` —— Go 的两支）
- `GoStyleRpcSheddingInterceptor` 按 Go 的**精确位置**接上三个计数：
  `IncrementTotal` 在 `allow()` **之前**（每个请求都 +1）、
  `IncrementDrop` 在 `allow()` 失败时、
  `IncrementPass` 与 `promise.Pass()` **同一支**（ctx 超时那一支只 `Fail`，**不**计 pass）。
- 进程级单例（Go 的 `var sheddingStat *load.SheddingStat` + `ensureSheddingStat()`）
  + 守护线程每分钟上报一次（Go 的 `go st.run()`）。

#### ⚠️ 刻意**不**引入 Micrometer

- Go 侧**没有** Prometheus 指标 ⇒ 本仓若凭空加一个 `rag_protection_shedding_total`
  那是**发明**一个 Go 没有的能力，与「Go 是唯一基准」相悖（§3.2）；
- 且 `service/common` 是 20 个组件共享的依赖树，给它加 micrometer 属于**跨域影响面**，
  而用户本轮已明确「共享层不做多余改动」。

#### 顺带的口径修正（`GoStyleProtectionProperties`）

`GoStyleCpuReader`（新）把 CPU 采样从 `GoStyleAdaptiveShedder` 里抽出来，
让「降载判据」与「统计日志」用**同一份口径**（默认 `SYSTEM` = 整机千分比 = Go 的 `stat.CpuUsage()`）。

#### 守门

`GoStyleSheddingStatTest`（5 例）+ 变异验证（删掉 `stat.incrementDrop()` ⇒ **1/5 转红**）。

---

### 3. ✅ `DRIFT-MSG-V-026` 已修：**8 个 rpc 组件全部接入**熔断 / 降载链

#### 🔴 关键发现：**挂 breaker 有一个逐组件的前置条件**（差点就做错了）

breaker 的「失败判据」是 **status code**（Go `go-zero@v1.10.2/zrpc/internal/codes/accept.go:9-17` 的 6 码白名单）。
⇒ **组件的兜底码若与 Go 不一致，挂上 breaker 反而会把既有漂移激活成新回归**。

⇒ 因此本轮**逐组件核了两侧**（Go 出处 = 消费点）：

| rpc 组件 | Go 的 DB / 内部错误码 | RAG 的 | 一致？ | 挂 breaker 后是否与 Go 同开路？ |
|---|---|---|---|---|
| `message` | 裸 error ⇒ `UNKNOWN`（`logic/**` 19 处原样返回） | `UNKNOWN`（V-024 已对齐） | ✅ | ✅ 两侧都**不**熔断 |
| `article` | `status.Error(codes.Internal, …)`（`delete_article_logic.go:71` 等） | `UNKNOWN`（`ArticleServiceGrpcImpl:146/150`） | ❌ | ⚠️ **RAG 熔断更少**（差异**本已存在**：RAG 此前根本没 breaker）⇒ 挂上**不会更差** |
| `favorite` | `GRPCError(codes.Internal, …)` | `INTERNAL` | ✅ | ✅ 两侧都熔断 |
| `follow` | `GRPCError(codes.Internal, …)` | `INTERNAL` | ✅ | ✅ 两侧都熔断 |
| `comment` | `NewGrpcErr(ErrorDbSelect=5003, …)` ⇒ **码值越界** ⇒ `UNKNOWN` | `UNKNOWN` | ✅ | ✅ 两侧都**不**熔断 |
| `like` | 同上（`5003` 越界） | `UNKNOWN` | ✅ | ✅ 两侧都**不**熔断 |
| `task` | `return nil, err` ⇒ `UNKNOWN` | `UNKNOWN` | ✅ | ✅ 两侧都**不**熔断 |
| `user` | `status.Error(codes.Internal, …)` | `INTERNAL` | ✅ | ✅ 两侧都熔断 |
| `admin` | `status.Error(codes.Internal, …)`（`resetuserpasswordlogic.go:53` 等） | `INTERNAL` | ✅ | ✅ 两侧都熔断 |

**8/9 一致**；唯一不一致的 `article` 是**既有漂移**（属 article 域），且挂 breaker 只让 RAG **熔断更少**、
**不会更差** ⇒ 不阻塞本条。

⚠️ **另一个被核掉疑**：PG 超时（SQLState 57014）在 RAG 侧被 `SqlTimeoutStatusTranslator` 翻成
`DEADLINE_EXCEEDED`（在 6 码白名单里 ⇒ 会熔断），而 Go 侧 DB 超时是 `context.DeadlineExceeded`
⇒ `status.FromContextError` ⇒ **也是 `DEADLINE_EXCEEDED`** ⇒ **两侧一致**，不构成漂移。

#### 改法（每个组件两处）

1. `@Import` 列表加 `GoStyleProtectionConfiguration`
   （它是 `GoStyleProtectionProperties` 这个 `@ConfigurationProperties` 类的**唯一注册点** ——
   它**不会**被组件扫描自动注册，缺了它 ⇒ 上下文起不来）；
2. `rpcInterceptorConfigurer` 改成走共享链：
   `server -> server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps))`
   （链序 `recover → breaker → shedding → 业务观测拦截器`，定义点在共享模块，
   **不在 10 个组件里各写一遍**）。

#### 守门（**逐组件**，AGENTS.md §3.5 的组件级粒度）

新增 7 个 `*ProtectionChainWiringTest`（article / comment / favorite / follow / like / task / admin）
+ 更新 `user-rpc` 的 `GrpcInterceptorWiringTest` ⇒ **共 8 个组件、16 例**。

每个组件断言两件事：
- ① `@Import` 列表含 `GoStyleProtectionConfiguration`（反射）；
- ② 交给 ServerBuilder 的那条链**真跑一遍**后，业务 interceptor **确实被调到**，
  且拿到的 `call` **不是**原始 call ⇒ **证明链上真的有 breaker/shedding 包装层**。

⚠️ **断言不能是「同一个对象」**：链上有包装层时业务 interceptor 收到的是**包装后的 call**
⇒ `same(call)` 必然不成立 ⇒ 改成「被调到」+「拿到的是包装过的 call」，**比原来更强**
（能区分「业务 interceptor 在链里」与「链被拼反 / 没挂上」）。

**变异验证**：把 `article-rpc` 改回裸 `server.intercept(rpcUnaryServerInterceptor)` ⇒ **1/2 转红**。

#### ⚠️ 合并冲突面提示（本轮改了**别的域的代码**）

本轮为 V-026 改了 8 个 rpc 组件的 `Application.java` + 1 个既有测试 + 新增 7 个测试文件。
这些属于**其它域**的代码 ⇒ **合并时需人工确认**（尤其 `user-rpc` 的既有测试被改了断言形状）。

---

### 4. 🔴 `DRIFT-MSG-V-030`（新挖出 · **跨域待办**）：**6 个 api 模块的 jar 根本不可执行**

**这是 V-028 的同款缺陷，但不在 message 域。**

**实测（真实产物，2026-10-24，全仓 `mvn package` 之后解包 MANIFEST）**：

| 模块 | 产物大小 | `Main-Class` |
|---|---|---|
| `article-api` | **68 KB** | ❌ 无 |
| `comment-api` | **52 KB** | ❌ 无 |
| `follow-api` | **56 KB** | ❌ 无 |
| `hot-api` | **32 KB** | ❌ 无 |
| `like-api` | **48 KB** | ❌ 无 |
| `task-api` | **24 KB** | ❌ 无 |
| `favorite-api` / `admin-api` / `user-api` / `message-api` | 68–80 MB | ✅ `JarLauncher` |

⇒ 那 6 个模块**从来没被打成过可部署产物** ⇒ `java -jar` 报「中没有主清单属性」。

**⚠️ 为什么它能潜伏这么久**：这三轮的每一项验证（测试全绿、`mvn package` 成功、上下文能起、
双侧对拍通过）**都发生在容器 / 测试环境里**，**从来没有碰过 jar 本身**。

**修法**：照 message-api 的 V-028，在该模块 pom 补 `<build>` 段（含 `spring-boot-maven-plugin`）。
⚠️ 补完**必须重跑本轮新增的守门脚本**确认 `Main-Class` 变成 `JarLauncher` ——
只看「`mvn package` 成功」不算数。

**新增守门脚本**：`tools/difftest/check-jar-executable.sh`
⇒ 逐个 api 模块解包 MANIFEST 看 `Main-Class`，任一缺失即 `EXIT=1`。
（2026-10-24 当场实测：**EXIT=1**，6 个模块不合格 ⇒ 门是有效的。）

**⚠️ 归属**：属**别的域**，按域间纪律**本工作树不改它们的 pom** ⇒ 登记为跨域待办。
（与 V-029 同款处理：登记 + 交接，不越界。）

---

### 5. ✅ `UNRESOLVED-MSG-004` 已关闭：四项「起不来」缺陷的**点名式**防回退

原问题：「`@MapperScan` / `spring-boot-flyway` / Bean 名 三项**无点名式防回退用例**」
⇒ 此前只被 `MessageTransactionWiringTest` 的「上下文能起」**间接**锁住
⇒ 红了但**红的原因不点名**（事后翻日志才知道是哪一条）。

**新增 `MessageContextWiringTest`（4 例，逐条点名）**：

| # | 断言 | 删掉后的症状 |
|---|---|---|
| ① | `@MapperScan("com.rag.message.message.rpc.mapper")` | 3 个 Mapper 注册不上 ⇒ `UnsatisfiedDependencyException: messageAtomicService` |
| ② | `NacosRegistration` 的 Bean 名 == `messageRpcNacosRegistration` | 与 `NacosServiceRegistryAutoConfiguration` 的默认名 `nacosRegistration` 撞车 ⇒ 上下文启动即失败 |
| ③ | `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration` **类可加载** | Boot 4 拆模块后缺 `spring-boot-flyway` ⇒ **启动日志里连一行 Flyway 都没有** ⇒ 表永不创建 |
| ④ | `application.yml` 含 `baseline-version: 0` + `flyway_schema_history_message` | 共享库里 V1 被整条跳过 ⇒ **服务起不来**（V-027） |

⚠️ **③ 的判据踩过一个坑**：第一版去读 `spring-boot-autoconfigure` 那份
`AutoConfiguration.imports` 找 `FlywayAutoConfiguration` ⇒ **必然红** ——
因为 Boot 4 把 Flyway 自动配置拆到了**独立模块** `spring-boot-flyway`，
它带的是**自己那份**同名资源，而 `ClassLoader.getResource()` 只返回**第一份**。
⇒ 改成**直接断言类可加载**（`Class.forName(..., false, loader)`）才是可靠判据。

**另新增** `MessageApiJarExecutableWiringTest`（2 例）⇒ V-028 的点名式防回退
（断言 pom 的 `<build><plugins>` 里真有 `spring-boot-maven-plugin`）。
⚠️ 这条断言**第一版也被自己的注释打败**：pom 的说明性注释里就写着 `<build>` 与
`spring-boot-maven-plugin` ⇒ 裸 `indexOf("<build>")` 先撞到**注释里那一次** ⇒ 断言必红
⇒ 修法：**先剥掉 XML 注释再取下标**。

---

### 6. 本轮实测数据（2026-10-24 当场实测）

```
【clean build 全量回归（12 个模块，10:56 min，BUILD SUCCESS）】
  service/common        ：168 tests / 0 failures / 0 errors / 0 skipped   （162 → 168，+6）
  message-common        ： 21 tests / 0 / 0 / 0
  message-rpc           ： 76 tests / 0 / 0 / 0   （68 → 76，+4 V-018 +4 点名式防回退）
  message-api           ： 88 tests / 0 / 0 / 0   （86 → 88，+2 V-028 点名式防回退）
  article-rpc           ：107 tests / 0 / 0 / 0
  comment-rpc           ：  2 tests / 0 / 0 / 0   （+2 V-026 接线）
  favorite-rpc          ：  2 tests / 0 / 0 / 0   （+2）
  follow-rpc            ：  2 tests / 0 / 0 / 0   （+2）
  like-rpc              ： 48 tests / 0 / 0 / 0   （+2）
  task-rpc              ：  2 tests / 0 / 0 / 0   （+2）
  users/user-rpc        ：116 tests / 0 / 0 / 0
  users/admin-rpc       ： 90 tests / 0 / 0 / 0
  ⇒ 合计 **722 例**，0 failures / 0 errors / **0 skipped**

【变异验证（本轮 3 条，全部有效 —— 改坏即红）】
  V-018：删掉 observeDbError 埋点              ⇒ 1/4 转红
  V-025：删掉 stat.incrementDrop()             ⇒ 1/6 转红
  V-026：article-rpc 改回裸 intercept          ⇒ 1/2 转红

【新守门脚本】
  tools/difftest/check-jar-executable.sh ⇒ EXIT=1（6 个 api 模块的 jar 不可执行）
```

> ⚠️ **本轮又踩出一条坑（已入档 `PITFALL-MSG-R-025`）**：
> 变异验证脚本用 `shutil.copy2` 还原源码 ⇒ **保留了旧 mtime** ⇒
> `maven-compiler-plugin` 的增量检查判定「class 已是最新」⇒ **跳过重编译**
> ⇒ 后续跑的是**变异过的 class** ⇒ 守门报「drop 计数是 0」，而源码其实是对的。
> ⇒ 排查路径：单独跑绿 ⇒ 整类跑绿 ⇒ `mvn clean test` 绿 ⇒ 锁定「编译产物过期」。
> ⇒ 修法：还原用 `shutil.copyfile` + `os.utime(path, None)`；
> ⇒ 且**任何「改坏 / 还原」之后都必须 clean 或显式刷 mtime**，否则后面所有绿灯都不可信。
> ⇒ 上面的三条变异结果是在**修好还原逻辑之后重跑**的。

---

### 7. ⚠️ 本轮改动落在**共享面 / 别的域**

- `service/common`：新增 `GoStyleSheddingStat` / `GoStyleCpuReader`，
  改 `GoStyleRpcSheddingInterceptor`（三个计数）、`GoStyleAdaptiveShedder`（CPU 采样抽出）。
  ⇒ **不引入任何新依赖**（未加 micrometer）⇒ 对 20 个组件的依赖树**零影响**。
- **别的域的代码**（V-026）：8 个 rpc 组件的 `Application.java` + `user-rpc` 的一个既有测试。
  ⇒ 合并时需人工确认。

---

## 2026-10-24 第十轮：**双侧活体 DDL 对拍**（关掉 `UNRESOLVED-MSG-V-003`）+ 三条签字偏离

> **用户指示**：「A5：起真 PG 跑 `check-ddl-static.py`，核对 DDL 索引/唯一约束/NOT NULL/DEFAULT」
> ⇒ 本轮**没改任何业务代码**，产出是 **1 个新守门脚本 + 1 条活体证据链 + 3 条签字偏离 + 1 条新登记的漂移**。

### 1. 🔴 为什么这道题**必须**起真库（不能读 DDL 文件判）

`UNRESOLVED-MSG-V-003`（`docs/DRIFT/_cross/message-domain-wide.md` §6.7.4）原问法是
「DDL 的索引 / 唯一约束 / NOT NULL / DEFAULT 完全 NOT CHECKED」。
⚠️ **它当初之所以「没法查」，不是工具缺失，而是两侧 schema 都不是静态 SQL**：

| 侧 | schema 怎么来的 | 为什么读文件判不出来 |
|---|---|---|
| **Go** | `service/message/rpc/internal/model/db.go:44` `AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})` | 索引名（`idx_<表名>_<列名>`）、可空性、DEFAULT 全由 **gorm.io/driver/postgres@v1.6.0** 的行为决定 ⇒ **读 struct tag 推不出来**（§3.3.1：tag 是**声明点**，迁移器才是**消费点**） |
| **RAG** | Flyway `V1`~`V5` **叠加后的净效果** | 单看 `V1`（建表时是 `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`）与 `V5`（`ALTER TYPE` + `DROP NOT NULL`）都推不出最终态 |

⇒ 必须两侧各建一个**真库**、真 dump、真比对。

### 2. ✅ 新守门：`tools/difftest/run-message-ddl-parity.sh`（一条命令复现全部）

```bash
bash tools/difftest/run-message-ddl-parity.sh     # 建库 + 对拍
```

> ⚠️ **门禁的红/绿口径（两条命令，两种退出码）**：
> ```bash
> # 🔴 默认：EXIT=1 —— 9 处差异全算失败（3 处索引 + 6 处 DEFAULT）
> bash tools/difftest/run-message-ddl-parity.sh
>
> # 🟢 豁免名单 = **已签字清单**（V-021 ×2 + V-022 ×1 + V-031 ×6）⇒ EXIT=0
> DDL_ALLOW_IDX="idx_message_conversation_last_message_at,idx_conversation_last_message_at,\
> idx_message_conversation_updated_at,idx_conversation_updated_at,idx_notification_recipient_read" \
> DDL_ALLOW_DEFAULT="message_notification.created_at,message_notification.updated_at,\
> message_conversation.created_at,message_conversation.updated_at,message_conversation.last_message_at,\
> message_conversation_message.created_at" \
>   bash tools/difftest/run-message-ddl-parity.sh
> ```
> ⇒ **「默认红」是刻意的**：它保证「任何**新的** DDL 漂移都会立刻把门禁打红」，
> 而**已签字的偏离靠豁免名单显式放行**（放行清单 = 签字清单 ⇒ 两者不会漂移）。
> ⚠️ **豁免名单是「签字」的机器可读形式** —— 往里加一个名字 = 给那条差异补一次签字。
> ⇒ **改名单必须同时改签字档**（`docs/Intentional_Deviations/message/rpc.md`），否则就是「悄悄放行」。

它做四件事（**全程不写被冻结的真源仓库**，只复制副本）：

1. 起 `msgddl-pg`（`postgres:16-alpine`，端口 55450），建 `go_msg_db` / `rag_msg_db`；
2. **Go 侧**：把真源仓库**复制**到临时目录 → 加一个 `cmd/ddlprobe` 探针 →
   调**真源自己的** `model.InitDB(...)`（内部就是上面那个 `AutoMigrate`）
   ⇒ 跑的是**同一份 struct tag + 同一个 gorm 版本**；
3. **RAG 侧**：按序执行 `V1`~`V5`；
4. 跑对拍（`tools/difftest/check-ddl-static.py --domain message --indexes`）。

> ⚠️ **踩到并修掉的两个坑（都是「环境看起来正常、实际会假绿/假红」）**：
> ① **`pg_isready` 不能当就绪判据** —— postgres 镜像 entrypoint 在 initdb 阶段会**临时起一个只监听 socket 的服务**，
>    `pg_isready` 对它返回 0 ⇒ 脚本会在真服务还没起时就去建库 ⇒ 实测报
>    `connection to server on socket ... No such file or directory`。
>    ⇒ 判据改成「`psql -tAc 'select 1'` 真的能连上并执行」。
> ② **`$VAR` 后面紧跟中文标点会被 bash 吞进变量名** —— `$CONTAINER，` 被解析成变量 `CONTAINER<0xEF>`
>    ⇒ `unbound variable`。⇒ 全脚本改用 `${VAR}`。

### 3. ✅ `check-ddl-static.py` 扩到 message 域 + 补上最后两项 `NOT CHECKED`

| 改动 | 说明 |
|---|---|
| **新增 `--domain message`** | 表名映射**为空**（Go `AutoMigrate` 与 RAG Flyway 两侧表名完全相同）⇒ **写一个「看起来更规范」的映射进去 = 凭空制造列集合不一致** |
| **新增 `--indexes`** | 补上 §3.1 对照清单里**最后两项从未被机器核过**的东西：`pg_indexes`（索引）与 `pg_constraint`（约束）<br>⚠️ **默认不开** —— 开了会让 users/task 域因**已知**历史差异直接红，破坏别人已在用的门禁语义 |
| **新增 `--allow-idx` / `--allow-default`** | 豁免名单：让**已签字**的有意偏离（V-021 / V-022 / DEFAULT 残差）不把门禁拖红，而**新**的漂移仍然为红 |
| **DEFAULT 与 NOT NULL 拆成两节报** | 两者的**可接受性完全不同**：可空性差异 = 能不能存 NULL ⇒ 直接影响写入行为，**零容忍**；DEFAULT 差异 = 「省略该列时填什么」⇒ 只在有 INSERT 省略该列时才发作 |
| **索引差异分三类** | ① 同名但定义不同 ② **名字不同但「表+唯一性+列」完全等价**（⇒ 行为等价，**不是缺失**）③ 真的单边缺失 |

### 4. ✅ 活体对拍结果（`go_msg_db` vs `rag_msg_db`，各 37 列 / 12 索引 / 3 约束）

| 维度 | 结果 |
|---|---|
| **列类型 / 长度** | ✅ **0 差异**（37 列逐字相同：bigint / integer / text / varchar(255) / boolean / jsonb / **timestamptz**） |
| **NOT NULL（可空性）** | ✅ **0 差异**（V-020 的 `DROP NOT NULL` 精确命中：Go 侧 `created_at`/`updated_at`/`read_at`/`extra` 可空、`last_message_at` NOT NULL，两侧一致） |
| **DEFAULT** | 🔴 **6 处差异**（见下方第 5 节 ⇒ 新登记 `DRIFT-MSG-V-031`） |
| **索引** | 🔴 **3 处差异**：`idx_notification_recipient_read`（Go 1 列 / RAG 2 列 ⇒ **V-022，已签字 SD-06**）＋ `idx_conversation_*` ×2 名字不同（⇒ **V-021，本轮签字 SD-2026-10-24-01**）<br>⇒ 其余 **9 个索引逐条一致**（含 `uk_conversation_pair` 的 4 列与 `unique=True`） |
| **约束（`pg_constraint`）** | ✅ **两侧完全一致**（各 3 行，都是主键）<br>⇒ **V-023 的修复双向确认**：两侧的 `uk_conversation_pair` 都是**唯一索引**而非表约束（`pg_constraint` 里都没有 `contype='u'` 那一行） |

### 5. ✅ `DRIFT-MSG-V-031` —— 6 个时间列的 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧**多出来的** · **已签字**（`SD-2026-10-24-04`）

```
Go : message_notification.created_at | timestamptz | 可空 | 无默认
RAG: message_notification.created_at | timestamptz | 可空 | DEFAULT CURRENT_TIMESTAMP
（其余 5 列同款：message_conversation.{created_at,last_message_at,updated_at}
               / message_conversation_message.created_at）
```

- **根因**：Go 侧这些列**根本没有 DEFAULT**（GORM 只在 tag 里写了 `autoCreateTime`/`autoUpdateTime`，**不生成 DEFAULT**）；
  RAG 的 `V1`/`V2`/`V3` 建表时写了 `DEFAULT CURRENT_TIMESTAMP`，`V5` 只改了类型与可空性、**没删默认**。
- **⚠️ 这是 `V-020` 当时就登记在案的残差**（`V5` 迁移文件里明写「本文件不删 `DEFAULT CURRENT_TIMESTAMP`（Go 侧无默认，属已知未对齐项）」），
  **但当时没有任何活体证据** ⇒ 本轮首次**实测确认**：残差**恰好 6 列**（与台账里「DEFAULT 残差是 6 列」的数字**完全对上**）。
- **可观测性（已实测排除）**：RAG 的 3 条 INSERT **全部显式绑定**这些列
  （`ConversationMapper.java:143-155` / `NotificationMapper.java:205-219` / `ConversationMessageMapper.java:159-166`）
  ⇒ 默认**永远不触发** ⇒ 两侧行为等价。
  ⚠️ **但这是「当前无路径」而非「结构性无路径」**：将来若有人写一条省略该列的 INSERT，
  Go 会写 **NULL**（可空列）而 RAG 会写 **now()** ⇒ 那时两侧就分岔了。
- **处置**：✅ **2026-10-24 用户拍板：保留签字**（保留 RAG 侧的 DEFAULT，**不加** `V6` 迁移去 `DROP DEFAULT`）
  ⇒ 本条已从 **DRIFT** 转入签字档：[`SD-2026-10-24-04`](../../Intentional_Deviations/message/rpc.md)。
  ⚠️ **读法变更**：看到这条 DDL 对拍为红时，先确认是不是 `SD-2026-10-24-04`，**不要**当新回归去「修」。
- **⚠️ 不得做的事**：不要**默默**把它改掉 —— 删默认是一次**线上行为变更**（任何「省略该列的 INSERT」会从 now() 变 NULL），
  必须重新走一次签字；也不要因为「当前不可观测」就把它从台账上抹掉。
- **门禁豁免名单已更新**：这 6 列已进 `DDL_ALLOW_DEFAULT` ⇒ 门禁**可以转绿**，但**默认仍是红**
  （不传豁免名单时它照红 ⇒ 保证「新的 DDL 漂移」仍然会被抓到）。

### 6. ✅ 关闭：`UNRESOLVED-MSG-V-003`

| 项 | 内容 |
|---|---|
| **原问法** | DDL 的索引 / 唯一约束 / NOT NULL / DEFAULT —— **完全 NOT CHECKED** |
| **关闭依据** | 本轮**双侧活体**建库 + 逐维度比对（见第 4 节）：**索引 12 个、约束 3 个、37 列的可空性与 DEFAULT 全部核过**，逐条证据在上面的对照表与 `tools/difftest/run-message-ddl-parity.sh` 的输出里 |
| **关闭口径** | 关闭的是「**未核过**」这个状态，**不是**「DDL 完全一致」—— 差异见第 4 节的 3 处索引 + 6 处 DEFAULT |
| **⚠️ 残留** | ① 索引/约束对照**只覆盖本域这 3 张表**（Go `AutoMigrate` 的全部产物）；② **不覆盖触发器 / CHECK 约束 / 外键 / 分区**（两侧都没有）；③ `idx_notification_recipient_read` 与 `V-021` 两条索引差异**是已签字的有意偏离**，对拍为红属预期 |

### 7. ✅ `UNRESOLVED-MSG-009` 已关闭（用户 2026-10-24 拍板）

原问法：「时间列 `TIMESTAMP`（非 `TIMESTAMPTZ`）在多实例/多时区下是否仍自洽」，
并建议「补一条 `V4` 迁移」⇒ **该迁移已由 `V5__message_time_columns_timestamptz.sql` 做完**
（8 列全部 `TIMESTAMPTZ`，本轮对拍**实测确认**两侧类型一致）。

⇒ **原问法里「列类型导致不自洽」那一半已经不存在**；剩下的只是
「同一个库被不同时区的 JVM 写」这种**部署口径**问题 —— 它不是代码能修的。
✅ **用户 2026-10-24 拍板：关掉**（不再作为待办）。
⇒ **关闭口径**：关闭的是「**代码侧**是否存在不自洽」这个未知项。
⚠️ **部署侧的约束仍然成立**（同一个库不要被不同时区的 JVM 写），但它已不是一条待办。

### 8. ✅ 本轮的四条签字偏离（同时改了 `docs/Intentional_Deviations/message/rpc.md`）

| 编号 | 原漂移 | 签字结论 |
|---|---|---|
| `SD-2026-10-24-01` | `V-021` 索引名不同 | 保留 RAG 短名，**不改名** |
| `SD-2026-10-24-02` | `V-009` 直方图桶布局 | 签字留档，**不改代码**（双侧实测：两侧桶边界**零交集**，Go 11 桶 vs Micrometer 69 桶） |
| `SD-2026-10-24-03` | `V-010` 错误解包深度 | 签字留档，**不改代码**（并**订正**了原描述：真正不一致的只有 `CodeError` 那一支，`status` 那一支两侧都已穿透） |
| `SD-2026-10-24-04` | `V-031` 6 个时间列的 `DEFAULT CURRENT_TIMESTAMP` | **保留签字**（不加 `V6` 去 `DROP DEFAULT`）—— 用户 2026-10-24 拍板；⚠️ **必须知情**：这是「当前无路径」而非「结构性无路径」，将来若有省略该列的 INSERT，两侧会分岔 |

### 9. ⚠️ 留给主会话的两条**共享面**订正（本工作树**不许碰**，只登记）

按 `docs/Worktree.md` §三，以下两个文件是**共享面**（多域并发必撞）
⇒ **本工作树不写**，只把「哪句话过期了、该改成什么」登记在此，由主会话合并时统一更新：

| 文件 | 现状（已过期） | 该改成 |
|---|---|---|
| `docs/DRIFT/_cross/ddl-type-divergences.md` 第 10 行 | 「message-rpc 的 `V2` 迁移 `ALTER TABLE ADD CONSTRAINT` **无 `IF NOT EXISTS`**（在 GORM 建过表的库上会失败）」 | ⚠️ **该问题已不存在** —— `V4__conversation_pair_unique_index.sql` 已用 `DROP CONSTRAINT IF EXISTS` + `CREATE UNIQUE INDEX IF NOT EXISTS` 把它换成了**唯一索引**（且第十轮活体对拍**双向确认**：两侧 `pg_constraint` 里都**没有** `uk_conversation_pair` 那一行）⇒ 这条该从「差异清单」里**删掉** |
| 同上（**新增**） | —— | ⚠️ **要新增一条真实差异**：message 域 **6 个时间列的 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧多出来的**（Go 侧无默认）⇒ 见本文件第十轮第 5 节的 `DRIFT-MSG-V-031`。⚠️ 注意这份清单的标题是「**DDL 实际差异清单**」，**DEFAULT 差异按定义属于它** ⇒ 不要漏 |

> ⚠️ **为什么不在本工作树直接改**：该文件是 `_cross` 共享面，users / like / article 等域的会话可能同时在改它
> ⇒ 事后无法区分台账归属（`docs/Worktree.md` §三.1 的原话）。

---

### 6. ✅ `DRIFT-MSG-V-030` 已关闭（2026-10-25 · 跨 6 个域，本轮统一修完）

> **登记方在 message 域**，修法落在**各域自己的 `pom.xml`** ⇒ 属跨域改动，本轮**串行统一实施**
> （不走并发 worktree，避免多路并发改同一批 pom）。

**修法**：6 个模块各补 `<build>` 段（含 `spring-boot-maven-plugin`），照 `message-api` 的 V-028 同款写法。

| 模块 | 修前产物 | 修后产物 | `Main-Class` |
|---|---|---|---|
| `article-api` | 68 KB | **69 MB** | ✅ `JarLauncher` |
| `comment-api` | 52 KB | **68 MB** | ✅ `JarLauncher` |
| `follow-api` | 56 KB | **79 MB** | ✅ `JarLauncher` |
| `hot-api` | 32 KB | **67 MB** | ✅ `JarLauncher` |
| `like-api` | 48 KB | **68 MB** | ✅ `JarLauncher` |
| `task-api` | 24 KB | **63 MB** | ✅ `JarLauncher` |

**守门脚本**：✅ `tools/difftest/check-jar-executable.sh` **2026-10-25 实测 `EXIT=0`**（修前 `EXIT=1`）
⇒ 10/10 api 模块产物均可执行。

**各模块测试**（修完复跑，`mvn -o -pl <6 模块> test` ⇒ `BUILD SUCCESS`）：

| 模块 | tests / failures / errors / skipped |
|---|---|
| `article-api` | 86 / 0 / 0 / 0 |
| `comment-api` | 45 / 0 / 0 / 0 |
| `follow-api` | 50 / 0 / 0 / 0 |
| `hot-api` | 86 / 0 / 0 / 0 |
| `like-api` | 81 / 0 / 0 / 0 |
| `task-api` | 90 / 0 / 0 / 0 |

⚠️ **关闭口径**：关闭的是「**这 6 个模块的产物不可执行**」这一条待办。
**本条没有验证「产物真能启动」** —— 启动会撞 Nacos / Redis / Kafka / gRPC 依赖，
「起不来」与「产物不可执行」是两件事 ⇒ 如需这一层证据，另做启动探针。

---

## 2026-10-26 主会话转达：global 域 **E59** —— deadline 的原始错误被**改写**成 `INTERNAL` + `ERROR_DB_UPDATE`（🔴 真漂移，待 message 域 owner 接手）

> **转达方式**：global 域（`align/global`）第三十六轮 **E47 全面订正**时逐条核到 Go 侧，发现本域有 **3 处真漂移**。
> 落点在 message 域 ⇒ **无 message owner 会话** ⇒ **经主会话转达** ⇒ 本节只是**交接指针**，**不代替 message 域下结论**。

| 项 | 值 |
|---|---|
| 出处 | global 台账 **E59**（`docs/DRIFT/global/_README.md`，**按编号指**） |
| 落点 | `service/message/message-rpc/.../MessageDomainService.java:755`（mark conversation read）· `:873`（mark notification read）· `:921`（mark all read） |
| 行为层 | **gRPC status code + 业务码**（wire 可见） |
| 证据强度 | ✅ **主会话已独立复核 Go 侧**（2026-10-26）—— 不是照抄 global 的结论 |

### 两侧事实

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `if err = l.svcCtx.MessageModel.MarkConversationRead(...); err != nil { metrics.ObserveDBError("conversation", "mark_read"); return nil, err }` ⇒ **原样返回 err** —— **不糊成业务码**，deadline 的原始错误因此**能透到 wire 上** | `service/message/rpc/internal/logic/markconversationreadlogic.go:59-62`（同款：`markallnotificationsreadlogic.go:42-45`） |
| **RAG** | `catch (RuntimeException e) { metrics.observeDbError("notification", "mark_read"); throw MessageRpcSupport.grpcRuntimeError(Status.Code.INTERNAL, MessageErrorCode.ERROR_DB_UPDATE.getCode()); }` ⇒ **改写成 `INTERNAL` + `ERROR_DB_UPDATE`** ⇒ **RAG 这一侧比 Go 更糊** | `MessageDomainService.java:757-760` / `:875-878` / `:923-926` |

### 方向判断

🔴 **这一侧是 RAG 错**（与 E47 原记述相反的**另一种**错）⇒ 按 AGENTS.md §3「差异默认判 RAG 错」⇒ **属需对齐的差异**。
⇒ ⚠️ **与 comment / like / follow / article 四个域相反**：那四域的 Go 侧**也**改写 ⇒ 与 Go 一致 ⇒ 销案 ⇒ 不要改。
⇒ **全仓现在「两种口径各占一半」** ⇒ **必须按域逐条看，不能一刀切**。

### 状态

🔴 **待 message 域 owner 接手**（本域无 owner 会话 ⇒ 主会话转达）⇒ **主会话不改代码、不下结论**，只留指针。

---

### 🟡 `DRIFT-MSG-V-032` 🟡 **登记（不改）**：`MessageRpcSlowThresholdGateTest` 在**全量并发回归**里偶发 `UNAVAILABLE: Network closed for unknown reason`

| 字段 | 内容 |
|---|---|
| **Component** | message-rpc |
| **Operation** | `MessageRpcSlowThresholdGateTest.slowCallMustBeMarkedSlow` |
| **Category** | **测试稳定性**（非生产缺陷） |
| **现象** | 2026-10-27 三域全量回归（`-am` 23 模块）里，该用例报 `StatusRuntime UNAVAILABLE: Network closed for unknown reason`（栈：`callAndGetSpan:349` → `warmUp:318`）⇒ 该模块 `Tests run: 84, Failures: 0, Errors: 1` ⇒ BUILD FAILURE（停在 `:message-rpc`，后续模块未跑到） |
| **定性** | **flaky，不是回归**：① **单独复跑该类 2/2 全绿**（8.158 s）；② 该模块**不在**本轮改动范围（本轮只改 like-api / message-api 两个 **api** 组件 + 台账；**message-rpc 不依赖 message-api**）；③ 症状是 gRPC 客户端在服务端拆除/连接关闭时读到 `Network closed`，属**建连/拆除时序**，不是行为语义 |
| **Evidence** | 全量日志 `/tmp/g2-full-reg2.log:15549-15552`；单独复跑 `Tests run: 2, Failures: 0, Errors: 0` |
| **Difference** | 与 Go 无对应关系（Go 侧无此测试）⇒ **不是语义漂移**，是**测试自身的时序脆弱性** |
| **Disposition** | **登记不改**（域侧无法单独消除：涉及其它模块的 gRPC 建连时序）；⚠️ **不因此判 message-rpc 回归**；⇒ 后续遇到同一症状，按「先单独复跑定性」处理，不直接当回归修 |

---

## 2026-10-27 · G2 · U+FFFD 跨树复核：G3 报的「message-rpc 那条」**在本树已干净**（主树仍坏）

| G3 报的 | 本树实测（**字节级**） | 结论 |
|---|---|---|
| `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageRpcProductionAddressTest.java:96`：`地址都必须U+FFFD×3 discovery:///` | **FFFD = 0**；真实内容 = `@DisplayName("生产 application.yml 的两个 grpc client 地址都必须是 discovery:///（三斜杠）")` | 🟢 **已干净** |

⚠️ **判据本身的坑（本轮实测 ⇒ 留档）**：

> 📌 **本节引述里的坏字符已改写为 ASCII 标记 `U+FFFD×3`**（3 个替换字符 = 1 个 UTF-8 汉字的 3 字节全丢）
> ⇒ **台账自身不再植入坏字节** ⇒ 复扫可保持 **0 命中**；需原样比对时按「`文件:行号` + `U+FFFD×3`」去主树/G1/G3/G4 树取原件。

- **`read` 工具的输出不能作为「含 U+FFFD」的证据** —— 本轮 `read` 把正常的「是」渲染成 `U+FFFD×3` ⇒ **纯渲染假象**，字节里没有。
- ⚠️ `grep -rn $'\xef\xbf\xbd'` 在本机 shell 下**也会 0 命中**（转义未生效）⇒ 同样**不能**当判据。
- ⇒ **唯一可信判据 = 字节级扫描**：`b'\xef\xbf\xbd' in path.read_bytes()`。

### 🔴 但：主树 / G1 树 / G3 树 / G4 树 **仍各坏 1 处**（同一句，FFFD×3）

⇒ **因果已坐实**（`git show` 逐版本字节计数）：`ce81c5f^` ⇒ FFFD=**3** ⇒ `ce81c5f` ⇒ FFFD=**0**
⇒ 即 **本树阶段 2 那个 commit（JWT 验签搬出 3s 预算）重写该文件区域时顺带写对了中文**。
⇒ **合并影响**：该文件在**我域写权面** ⇒ 合并时**以本树版本为准** ⇒ 合并后自动干净；
**若取主树侧版本 ⇒ 坏字符被带回去** ⇒ **登记为合并后必查项**。

### ✅ 附带：message-rpc **无**同款启动守门 flake

| 判据 | 结果 |
|---|---|
| 全我域 `grep -rn "destroyForcibly" service/{like,task,message} --include=*.java` | **1 命中**（`LikeRpcRuntimeBootGateTest`）⇒ **message-rpc 不命中** |
| `message-rpc` 有无 `*RuntimeBootGate*` 类 | **无** ⇒ 真进程层守门形态在本模块**不存在** ⇒ G3 报的 flake **不适用本域** |

