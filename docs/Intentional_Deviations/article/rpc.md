<!-- @new 2026-10-24 · article 域 rpc 层签字偏离（SD-2026-10-24-01 / SD-2026-10-24-02） -->
<!-- @signed 2026-10-24 · 两条均已由用户签字批准（Jason） -->

# `article` 域 rpc 层签字偏离（SD-2026-10-24-01 / SD-2026-10-24-02）

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」**。
>
> ⚠️ 读之前先记住：**这是决策，不是缺陷。** 审查时不必重复质疑，**也不得「顺手改回 Go 的旧写法」**。

> ✅ **签字状态：本文两条 SD（`SD-2026-10-24-01` / `SD-2026-10-24-02`）已于 2026-10-24 由用户签字批准。**
> ⇒ 「🟡 待签字」的临时状态**已终结**：审查时**直接把它当既定决策**，
> **不得**再把它当「待定」重新质疑，也**不得**「顺手修正回 Go 的旧行为」。
>
> 📌 **2026-10-29 对齐复核（台账对齐 phase · W-D lane）—— 本文件 2 条逐条重核完毕**
>
> 判据 = [`../_INDEX.md`](../_INDEX.md)「被签字描述的那个行为**已不存在** ⇒ 签字自动失效」。
> **2/2 条的「被描述的行为」在 2026-10-29 实测下仍然存在 ⇒ 签字状态全部不变**（无一条需标「已失效」）。
> **但有 2 处撞号必须处理** ⇒ 见下方两条的「唯一别名 / 撞号检查」行：
> `SD-2026-10-24-01` 与 `SD-2026-10-24-02` **各与 message 域的同名条目指向完全不同的偏离** ⇒ ⇒ 本文件两条**加唯一别名** `SD-2026-10-24-01@article` / `SD-2026-10-24-02@article`。

## SD-2026-10-24-01：写路径的**全局锁序**统一为 `article → publication` ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字**（2026-10-24，由 `align/article` 工作树产出、证据已闭环；**2026-10-24 用户 Jason 签字**） |
| 组件 | `service/article/article-rpc` |
| 行为层 | 并发写路径的**行锁获取顺序**（`SELECT ... FOR UPDATE` 的先后） |
| Go 侧 | **两条锁序并存**：发布回调 `article → publication` × 撤回/删除 `publication → article` |
| RAG 侧 | **四条路径全部** `article → publication` |
| 性质 | **RAG 消除了 Go 自身的锁序反转** ⇒ 并发时 Go 可能 `40P01 deadlock detected`，RAG 不会 |

### 差异的实际面

Go 侧四条写路径分成**两种**锁序：

| 路径 | Go 出处（消费点） | Go 锁序 |
|---|---|---|
| 发布回调 `acceptSyncResult` | `internal/mqs/article_sync_result_consumer.go:74` → `WithArticleTx`（`internal/model/community_revision.go:81-89`，先 `FOR UPDATE` article）→ `:140` `tx.Save(&pointer)` 写 publication 行 | **article → publication** |
| 审核消费者 `article_consumer` | `internal/mqs/article_consumer.go:104` → `WithArticleTx`；**只写 article + outbox，不碰 publication 行** | article（单锁） |
| 降级 `updatePublishedToSourceOnly` | `internal/logic/update_article_logic.go:217` `RetractPublicationTx`（`internal/mqs/community_article_event.go:112-114` 先 `FOR UPDATE` publication）→ `:220` `UpdateTx(article)` | **publication → article** |
| 删除 `DeleteArticle` | `internal/logic/delete_article_logic.go:85` `RetractPublicationTx`（同上）→ `:91` `DeleteTx(article)` | **publication → article** |

⇒ **Go 自身**在「发布回调」与「撤回/删除」之间构成**锁序反转**。

RAG 侧四条路径**统一**（`service/article/article-rpc/.../service/ArticleAtomicService.java`）：

| 路径 | RAG 出处 | RAG 锁序 |
|---|---|---|
| `retractAndUpdate`（降级） | `:127` `articleMapper.selectByIdForUpdate` → `:208` `publicationMapper.selectForUpdate` | article → publication |
| `retractAndDelete`（删除） | `:162` `selectByIdForUpdate` → `:208` `selectForUpdate` | article → publication |
| `acceptSyncResult`（发布回调） | `:339` `selectByIdForUpdate` → `publicationMapper.upsert`（隐式行锁） | article → publication |
| `queueSyncOutboxWithLock` | `:268` `selectByIdForUpdate`（只锁 article） | article（单锁） |

⇒ **无反转**。

### 证据（双侧实测，2026-10-24）

#### ① RAG 侧 —— 真 PG **锁序探针**（观测「第一把锁」）

`ArticleTransactionWiringTest#lockOrderIsArticleThenPublication`：
用**另一条连接**同时扣住 `article` 行与 `article_publication` 行两把锁，
让被测代码在另一线程里跑 `retractAndUpdate` ⇒ 它必然卡在**它最先要的那把锁**上；
再用 `pg_stat_activity`（`wait_event_type='Lock'`）读出被阻塞后端正在执行的 SQL
⇒ **那条 SQL 唯一确定第一把锁**。

实测：**被阻塞的语句 = `SELECT * FROM article WHERE id = $1 FOR UPDATE`** ⇒ 第一把锁是 article
⇒ 锁序 = **article → publication**。

⚠️ 判别子串用 `"FROM article WHERE"`（**带 WHERE**）：`article_publication` 是下划线连接，
不含该子串 —— 否则两条 SQL 会互相误判。

#### ② Go 侧 —— Go 探针（pgx v5 + 真 PG，两种锁序各跑一次）

探针：`/tmp/probe-lock/go/main.go`（模块 `probe/lock`，依赖 `github.com/jackc/pgx/v5 v5.10.0`
—— 与真源仓库 `go.mod` 同版本；容器 `postgres:16-alpine`）。
两个事务用**会合点**（拿到第一把锁后与对端会合）制造**确定性交错**，不靠 sleep 碰运气。

```
=== Go 实际锁序：撤回路径 publication→article  ×  发布路径 article→publication ===
  T1（SELECT * FROM article_publication WHERE article_id = $1 FOR UPDATE）: 失败: ERROR: deadlock detected (SQLSTATE 40P01)
  T2（SELECT * FROM article WHERE id = $1 FOR UPDATE）: 成功
  ⇒ 结论：**发生死锁**（40P01 deadlock detected）

=== RAG 统一锁序：两条路径都是 article→publication ===
  T1（SELECT * FROM article WHERE id = $1 FOR UPDATE）: 成功
  T2（SELECT * FROM article WHERE id = $1 FOR UPDATE）: 成功
  ⇒ 结论：**无死锁**（两次事务被串行化）
```

⇒ **同样的表、同样的锁语句，只因顺序不同，一个死锁一个不死锁** ⇒ 差异出在**锁序**上，不是别的。

#### ③ 变异验证（三态判定）

| # | 变异 | 受影响的用例 | 结果 |
|---|---|---|---|
| 1 | `retractAndUpdate` 拿掉 article 行锁（`selectByIdForUpdate` → `selectById`）⇒ 锁序变 `publication → article` | `lockOrderIsArticleThenPublication` | ✅ **转红**（实测被阻塞语句变成 `SELECT * FROM article_publication WHERE article_id = $1 FOR UPDATE`） |
| 1' | 同一个变异 | `concurrentRetractAndDeleteOnSameArticleSerializesWithoutDeadlock` | ⚠️ **仍全绿** —— 见下 |

⚠️ **1' 是一条重要的负结果**：并发用例（CASE-R12）测的是「两个事务的**最终结果**」，
而**只要两条路径的锁序彼此一致，结果就一样** ⇒ 它**证伪不了锁序**。
⇒ 这正是 AGENTS.md 反复强调的「**跑绿 ≠ 对**」：绿测试可能只覆盖了它碰巧覆盖的那部分。
⇒ 必须另配一条**能唯一判定锁序**的探针（就是 ①）。

### 可达性（不是理论上的可能）

死锁需要「发布回调」与「撤回/删除」**同时命中同一篇文章**：

- 发布回调进入 publication 写路径的前置条件是文章处于 **REVIEWING**
  （`article_sync_result_consumer.go:92-94` 的 `article.Status != REVIEWING ⇒ return nil`）；
- **删除**路径**不检查状态**（`delete_article_logic.go:45-62` 只查存在性与作者）
  ⇒ 文章在 REVIEWING 期间被作者删除，**完全正常**。

⇒ 真实交错：
`T1 = acceptSyncResult`（持 article 锁 → 写 pointer 行）
× `T2 = DeleteArticle`（持 publication 锁 → `DELETE article`）
⇒ Go：T1 要 publication，T2 要 article ⇒ **死锁**，PG 中止其中一个（`40P01`）
⇒ RAG：两条路径都先要 article ⇒ **串行化**，只是慢一点，**不会失败**。

### 影响面逐条评估（**为什么现在安全**）

| # | 依赖点 | 结论 | 依据 |
|---|---|---|---|
| 1 | 单线程 / 低并发下的行为 | ✅ **无影响** | 锁序只在**并发**下才显形；单线程下两侧的读写顺序完全一致 |
| 2 | 用户可见行为 | 🟢 **RAG 更好** | Go 侧死锁会让其中一个操作**失败**（gRPC `Internal` ⇒ api 侧 1015）；RAG 侧两个操作都成功，只是串行 |
| 3 | 指针版本 `pointer_version` 单调 | ✅ **无影响** | 两侧都由 `FOR UPDATE` + `UNIQUE(aggregate_id, aggregate_version)` 保证单调（Go：`community_article_event.go:114,136`；RAG：`ArticlePublicationMapper.selectForUpdate` + `upsert`） |
| 4 | 域事件条数 | ✅ **无影响** | 同上：唯一约束保证同一 `aggregate_version` 只有一条 |
| 5 | 事务内判定（no-op 提前返回） | ✅ **无影响** | 死锁只改变「谁先拿到锁」，不改变任何一条判定的内容 |
| 6 | Go 侧死锁是否会**自愈** | ⚠️ 不可依赖 | PG 中止的是**死锁环中的某一个**事务；Go 侧无重试（`RpcClientConf` 无 `Retries`，见 §4 矩阵「RPC retry」行）⇒ 该请求**真的失败** |

### 为什么**不修回去**（这条是决策，不是遗漏）

把 RAG 的锁序改回 Go 的 `publication → article`，就是**主动重新引入一个已实测的死锁**：

- Go 侧探针已经证明该顺序在真 PG 上会 `40P01 deadlock detected`；
- 而 RAG 现在的统一顺序是**已被活体证据钉死**的正确形态。

⇒ 「对齐 Go」在这里等于「把一个已证实的可用性缺陷搬过来」。
⇒ 按 AGENTS.md §1「行为以 Go 为准」的**适用边界**：那条规则针对的是**功能语义**；
**并发缺陷不在「必须复刻」的范围内** —— 复刻它只会让用户的操作失败。

⚠️ **若真源日后自己统一了锁序**（Go 侧也改成 article → publication），
⇒ 本条**自动失效**，应改为「已对齐」并从本文移除。

### 关联

- 结论的取证过程与逐条对照 ⇒ [`../../DRIFT/article/rpc.md`](../../DRIFT/article/rpc.md) 的
  「2026-10-24 第七轮」节（U-2 关闭记录）。
- 台账里的 `U-2`（锁顺序反转）**已关闭** ⇒ 它的结论落在这里，**不在 DRIFT 里重复记**。

### 📌 2026-10-29 对齐复核（台账对齐 phase · W-D lane）

| 项 | 结论 |
|---|---|
| **唯一别名** | `SD-2026-10-24-01@article`（原编号 `SD-2026-10-24-01` **撞号** ⇒ 见下行） |
| **撞号检查** | 🔴 **同一编号在两个域指向两条完全不同的偏离** ⇒ `docs/Intentional_Deviations/message/rpc.md:30`（**`message_conversation` 两个索引的**名字**与 Go 不同**）与**本条**（**写路径的全局锁序**）⇒ ⇒ 本条**加唯一别名** `SD-2026-10-24-01@article`；**message 域那条**由其 owner 处理（本 lane **不碰** `docs/Intentional_Deviations/message/**`）⇒ ⇒ **本条从本日起应以别名为准** |
| **被描述的行为现在还存在吗** | ✅ **仍存在** ⇒ **签字有效，状态不变**（✅ 已签字） |
| **核对日期** | 2026-10-29 |
| **Go 侧实测（两种锁序仍在）** | `service/article/rpc/internal/model/community_revision.go:81-89`（`WithArticleTx` ⇒ **先** `FOR UPDATE` article）· `service/article/rpc/internal/mqs/community_article_event.go:110-114`（`RetractPublicationTx` ⇒ **先** `FOR UPDATE` publication）⇒ ⇒ **Go 自身的锁序反转未被修复** ⇒ **签字的前提条件仍在** |
| **RAG 侧实测（四条路径仍统一）** | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleAtomicService.java:127`（`retractAndUpdate` ⇒ `selectByIdForUpdate`）· `:162`（`retractAndDelete`）· `:268`（`queueSyncOutboxWithLock`）· `:339`（`acceptSyncResult`）· `:208`（`publicationMapper.selectForUpdate`）· `:506`（`publicationMapper.upsert`）⇒ ⇒ **RAG 侧仍是 `article → publication` 单一顺序 ⇒ 差异仍在** |
| **⚠️ 自失效条件（原文保留）** | 本条原文已写明「⚠️ **若真源日后自己统一了锁序**（Go 侧也改成 article → publication）⇒ ⇒ 本条**自动失效**」⇒ ⇒ **2026-10-29 实测：真源尚未统一** ⇒ **本条仍然有效** |

## SD-2026-10-24-02：Kafka 四个通道**不能逐通道切 broker**（RAG 侧是全局单例） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字**（2026-10-24，由 `align/article` 工作树产出、证据已闭环；**2026-10-24 用户 Jason 签字**） |
| 组件 | `service/article/article-rpc` |
| 行为层 | **Kafka 客户端的粒度** —— 每个通道能否连各自的 broker |
| Go 侧 | 四个通道**各自**一个客户端（`kq.KqConf.Brokers` 逐通道）⇒ **可以逐通道切 broker** |
| RAG 侧 | `KafkaTemplate` / `ProducerFactory` / `ConsumerFactory` 是**全局单例** ⇒ 四通道**共用** `spring.kafka.bootstrap-servers` ⇒ **不能逐通道切 broker** |
| 性质 | **框架表达能力所限**（Spring Kafka 的客户端是全局装配的），不是实现疏忽 |
| 处置 | 差异**不消除**，但**不允许静默** ⇒ `ArticleKafkaChannelValidator` 在启动时发现分叉即**启动失败** |

### 差异的实际面

| 通道 | Go 侧客户端 | Go 出处（声明点 → 消费点） | RAG 侧 |
|---|---|---|---|
| 审核事件生产（`KqPusherConf`） | `kq.Pusher` | `go-queue@v1.2.2/kq/config.go:12` → `kq/pusher.go:44-47`（`kafka.TCP(addrs...)`）；调用点 `internal/svc/service_context.go:65` | 全局 `KafkaTemplate` |
| 审核事件消费（`KqConsumerConf`） | `kq.MessageQueue` | `kq/config.go:12` → `kq/queue.go:121`（`kafka.ReaderConfig{Brokers: c.Brokers}`）；调用点 `internal/article.go:58` | 全局 `ConsumerFactory` |
| 同步事件生产（`ArticleSyncPusherConf`） | `kq.Pusher` | 同上；调用点 `internal/svc/service_context.go:66` | 全局 `KafkaTemplate` |
| 同步结果消费（`ArticleSyncResultConsumerConf`） | `kq.MessageQueue` | 同上；调用点 `internal/article.go:60` | 全局 `ConsumerFactory` |

⚠️ **别引错行号**：Go 仓内 `internal/config/config.go:41` 也有一行 `Brokers []string`，
但它属于 **`HotEventPusherConf`**（第 40 行那个匿名 struct）——
那是 Go 侧的**死配置**（`service_context.go:68-70` 建了 Pusher，但全模块**零调用点**），
**不是**四个通道的字段。四个通道的字段声明点在 `go-queue@v1.2.2/kq/config.go:12`。

### 今天为什么**没有行为差异**

| 侧 | 取值 | 出处 |
|---|---|---|
| Go | 四个通道全部 `kafka:9092` | `rpc/etc/article.yaml:29-30, 46-47, 56-57, 62-63` |
| RAG | 四个通道全部 `"${KAFKA_BROKERS:kafka:9092}"`，全局 `spring.kafka.bootstrap-servers` 也是同一个占位符 | `service/article/article-rpc/src/main/resources/application.yml` |

⇒ 四个通道**同源**（同一个环境变量）⇒ 四个值**必然相同** ⇒ 与全局**同源**
⇒ **实际连接到的 broker 列表与 Go 逐项相同** ⇒ 无可观察差异。

### 为什么**不修回去**（这条是决策，不是遗漏）

「修回去」意味着给四个通道**各建一套** `ProducerFactory` / `ConsumerFactory` / `KafkaTemplate`。
代价（已逐条评估）：

1. Boot 4 的 `KafkaAutoConfiguration` 对这些类型都是 `@ConditionalOnMissingBean`
   ⇒ **新增任何一个都会顶掉 Boot 自动装配的那个**；
2. `ArticleOutboxRelayScheduleTest` 还自带一个 mock 的 `KafkaTemplate` Bean
   ⇒ 「哪个模板被注入」变成**装配顺序问题**（AGENTS.md §3.3 的老坑）；
3. 而这一切是为了一个**当前取值全相同**、**今天没有任何行为差异**的能力。

⇒ 结论：**能力差异保留，但「静默」不保留。**

### 为什么一定要「启动即失败」而不是「记个警告」

**静默忽略是最坏的一种**：运维改了某个通道的 broker，看到「服务正常起来了」，
于是以为流量已经切走 —— **实际上所有通道还连在老 broker 上**。

⇒ `ArticleKafkaChannelValidator`（`@PostConstruct`，早于对外收流量）在启动时逐通道比较
四个通道的 `brokers` 与全局 `spring.kafka.bootstrap-servers`，
**不一致就抛 `IllegalStateException` ⇒ 启动失败**，并说清：哪个通道、实际值、全局值、
「这个值不会生效」、Go 出处、以及两条出路。

### 边界（本条**不覆盖**什么）

| # | 边界 | 说明 |
|---|---|---|
| 1 | 通道 `brokers` **为空**（没配） | Go 侧空列表会让客户端**起不来**；RAG 侧回落到全局 ⇒ **RAG 更宽松**。校验器对空值**跳过**（= 没配，不是分叉点） |
| 2 | 全局 `bootstrap-servers` 为空 | 无从比较 ⇒ 跳过 |
| 3 | **地址逐字比较，不 trim** | Go 的 `kafka.TCP(addr)` 对地址逐字使用 ⇒ 带空格的地址在 Go 侧是「连不上」；RAG 侧校验也逐字比较，**不引入 Go 没有的宽容度** |
| 4 | **第五个通道** `HotEventPusherConf` | Go 侧死配置（零调用点），Java 侧**不迁移** ⇒ 不在校验范围 |

### 证据（真上下文实测 + 变异验证，2026-10-24）

- **真上下文**（真 PG + 真 `@SpringBootTest`）：
  `ArticleSpringContextWiringTest#kafkaChannelBrokersMatchGlobalBootstrapServers`
  ⇒ 四个通道**绑定后**的 `brokers` 与全局**逐项相同**；
  `#shippedYamlKeepsAllFourChannelsOnTheSameBrokerPlaceholder`
  ⇒ shipped yml 里四个 `brokers` **全部**引用同一个 `${KAFKA_BROKERS:...}`（守住「同源」这个地基）。
- **变异验证**：`ArticleKafkaChannelValidatorTest` **6/6 有效变异全部转红**
  （含两条负结果：一次「变异无效」、一次「测不到 ⇒ 补用例」）。
- ⚠️ **校验器装上后当场抓到 4 处真实的不自洽**：4 个 `@SpringBootTest` 把全局改成
  `127.0.0.1:1` 而通道仍是 `kafka:9092` ⇒ **长期存在却从未被任何行为测试发现**
  ⇒ 这正是「配置类差异只能靠守卫守、不能靠行为测试守」的实证。

### 关联

- 完整取证过程 ⇒ [`../../DRIFT/article/rpc.md`](../../DRIFT/article/rpc.md) 的
  「2026-10-24 第九轮」节（FIX-14）。
- 元教训 ⇒ [`../../Pitfalls/article/rpc.md`](../../Pitfalls/article/rpc.md) 的 **PF-ARTICLE-RPC-013**。

### 📌 2026-10-29 对齐复核（台账对齐 phase · W-D lane）

| 项 | 结论 |
|---|---|
| **唯一别名** | `SD-2026-10-24-02@article`（原编号 `SD-2026-10-24-02` **撞号** ⇒ 见下行） |
| **撞号检查** | 🔴 **同一编号在两个域指向两条完全不同的偏离** ⇒ `docs/Intentional_Deviations/message/rpc.md:46`（**`message_rpc_request_duration_seconds` 的直方图**桶边界零交集**）与**本条**（**Kafka 四通道不能逐通道切 broker**）⇒ ⇒ 本条**加唯一别名** `SD-2026-10-24-02@article`；**message 域那条**由其 owner 处理（本 lane **不碰** `docs/Intentional_Deviations/message/**`）⇒ ⇒ **本条从本日起应以别名为准** |
| **被描述的行为现在还存在吗** | ✅ **仍存在** ⇒ **签字有效，状态不变**（✅ 已签字） |
| **核对日期** | 2026-10-29 |
| **RAG 侧实测（仍是全局单例 + 启动即失败守卫）** | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/ArticleKafkaChannelValidator.java:118`（`@PostConstruct`）· `:131`（不一致即抛 `IllegalStateException` ⇒ **启动失败**）⇒ ⇒ **「静默忽略 → 启动即失败」的处置仍在** |
| **能力差异仍在（不变式）** | `service/article/article-rpc/src/main/resources/application.yml:86`（全局 `spring.kafka.bootstrap-servers` = `${KAFKA_BROKERS:kafka:9092}`）· `:178,184,193,199`（四个通道的 `brokers` **全部** = `${KAFKA_BROKERS:kafka:9092}`）⇒ ⇒ **四通道仍共用全局 `KafkaTemplate` / `ProducerFactory` / `ConsumerFactory` ⇒ 仍不能逐通道切 broker ⇒ 差异仍在**；但**今天仍无行为差异**（四值同源）⇒ **签字含义（能力差异保留、但不允许静默）仍成立** |
| **Go 侧实测** | Go 的 `kq.KqConf.Brokers` **逐通道**声明（`go-queue@v1.2.2/kq/config.go:12`）⇒ **Go 侧可以逐通道切 broker** ⇒ ⇒ **能力差异的根源仍在** |
