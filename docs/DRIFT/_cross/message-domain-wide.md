<!-- @move AGENTS.md L421-L483 · §0 message 全域第二轮语义验证 -->
### 🔴 `service/message` 全域第二轮语义验证：**10 项漂移 + 3 条新挖出的 P0**（2026-10-22）

> **详细留档见 §6.7**（逐条取证、UNRESOLVED、修复结果、变异验证表、两条通用纪律）。
> 本小节只回答「message 域现在还有没有漂移」。

**验证方式与前两轮不同**：不是「找当前实现的缺陷」，而是按 **Go → Spring 行为等价模型逐维度对照**
（契约 / 输入 / 分支 / 外部交互 / 状态 / 错误身份 / 超时 / 重试 / 并发 / 生命周期）。
覆盖 4 个组件：`message-rpc` + `message-api` + `message-common` + `message-proto`，
Go 侧通读 36 个文件、RAG 侧通读 71 个文件。

**结论：`SEMANTICALLY NOT EQUIVALENT`**
> ⚠️ **2026-10-22 19:10 纠正**：本节初版称「与 §6.6 均无重叠」——**错的**。
> §6.6 另有一张 DRIFT-MSG-004~012 的漂移表，其中 **8 行就是下表的 V-001~V-008**。
> 本轮真正新发现的只有 **V-009 / V-010** 与补测试挖出的 3 条 P0；
> 其余 8 项的价值在于**定级 + 修复 + 防回退**（§6.6 原标 `UNKNOWN`）。
> 完整对照见 §6.7.0。

#### 第一批（10 项静态对照）：**6 项已修 + 4 项签字留档 + 0 项待外部条件**

> ⚠️ **2026-10-24 第十轮更新**：`V-009` / `V-010` 已由**用户拍板签字留档**
> （`SD-2026-10-24-02` / `SD-2026-10-24-03`）⇒ 「2 项待外部条件」归零。

| 编号 | 一句话 | 状态 |
|---|---|---|
| **DRIFT-MSG-V-001** | `listNotifications(unread_only=true)` 的 **`total` 丢了过滤** | ✅ 已修 |
| **DRIFT-MSG-V-002** | 广播通知单条 foreach INSERT，**收件人 ≥5462 整批失败** | ✅ 已修 |
| **DRIFT-MSG-V-003** | DB/内部故障业务码 **Go `1015` → RAG `5001`/`5008`** | ⏸ **签字保留 RAG 侧**，见 §6.7.8 |
| **DRIFT-MSG-V-004** | 会话消息排序键 **`created_at asc` → `id asc`** | ✅ 已修 |
| **DRIFT-MSG-V-005** | 会话列表**缺次级排序键 `updated_at DESC`** | ✅ 已修 |
| **DRIFT-MSG-V-006** | `markConversationRead` **多写一列** `conversation.updated_at` | ✅ 已修 |
| **DRIFT-MSG-V-007** | 时间列 `timestamptz`→`timestamp` + 读侧按 UTC 解释墙钟 ⇒ **部署时区非 UTC 时全部时间戳偏一个时区差**（上海 = 偏晚 8h） | ✅ 已修（§6.7.10，部署时区已确认为 Asia/Shanghai） |
| **DRIFT-MSG-V-008** | `String.trim()` vs `strings.TrimSpace` **空白集不同** | ✅ 已修 |
| **DRIFT-MSG-V-009** | 直方图 bucket 布局不同（**第十轮实测：两侧桶边界零交集** —— Go 11 桶 vs Micrometer 69 桶） | ✅ **已签字留档**（`SD-2026-10-24-02`） |
| **DRIFT-MSG-V-010** | 错误解包深度不同（**第十轮订正**：真正不一致的只有 `CodeError` 那一支，`status` 那一支两侧都已穿透） | ✅ **已签字留档**（`SD-2026-10-24-03`） |

#### 🔴 第二批：**补测试时当场挖出 3 条 P0，静态审计两轮都没抓到**（§6.7.7）

| 编号 | 一句话 | 后果 |
|---|---|---|
| **DRIFT-MSG-V-011** | `#{item.isRead}` 取不到属性（实体字段叫 `read`） | **`SendNotification` 100% 失败** ⇒ admin 定向通知与广播**从未成功过一次** |
| **DRIFT-MSG-V-012** | `listByParticipant` 的 `limit` / `offset` **实参传反** | SQL 变成 `LIMIT 0` ⇒ **`ListConversations` 永远返回空列表**，且无任何错误日志 |
| **DRIFT-MSG-V-013** | MP 的 `JacksonTypeHandler` 用 `setString` 绑定，**jsonb 列拒收**；且缺 `autoResultMap` | 通知写入再次失败；**读**方向同样失效 |

> **这三条 P0 的共同形态**：都是「一条从未被任何测试执行过的写/查路径」。
> 与 §6.6.4 的 DRIFT-MSG-004/005/006（模块起不来）同源 ——
> **缺失的不是测试数量，是「让代码真正跑一次」这个动作。**

> ⚠️ **编号纪律**：本轮用 `DRIFT-MSG-V-*`，**不复用** `DRIFT-MSG-001..006`（§6.6）与
> `DRIFT-MSGAPI-001..004`（本节上方）。
> **代码注释里的漂移编号不是权威台账** —— `MessageAtomicService` javadoc 已发生编号撞车
> （把「多写 updated_at」标成 DRIFT-MSG-006，而 §6.6 的 -006 是「缺 flyway」），
> 并引用了**本文件里不存在的** `DRIFT-MSG-008`。

> ⚠️ **测试覆盖率必须重新表述**：修复前，`message-rpc` 的 21 条 + `message-api` 的 59 条 +
> `semantic-probe` 的 23 例双侧对拍，**没有一条能抓到上表任何一项**。
> 尤其 `-001`（无任何用例断言分页接口的 `total`）与 `-002`（无任何用例造过 >5461 收件人）。
> ⇒ **「有测试」与「测到了漂移面」是两件事**，见 §6.7.5 纪律 A。

#### 本轮新确立的三条通用纪律（已写进 §6.7，对全仓适用）

- **A**：分页接口的 **`total` 与 `items` 必须成对断言**；过滤条件要逐条覆盖到**计数**而不只是列表。
- **B**：`String.trim()` / `strip()` **都不是** `strings.TrimSpace` 的等价物（连 NBSP 都不认），
  跨语言迁移必须显式写空白集合。
- **C**：**做变异测试时每次变异与还原都必须 `mvn clean`** ——
  增量编译会把变异 class 留在 `target/classes`，导致「还原后复跑」测的不是当前源码（§6.7.7 实测踩中）。

<!-- @move AGENTS.md L3542-L3900 · §6.7 service/message 全域语义一致性验证 -->
### 6.7 `service/message` 全域语义一致性验证（2026-10-22 第二次）

> **本节与 §6.6 的关系**：§6.6 是「`message-rpc` 单模块首轮审计 + 修 6 项」；
> 本节是「`message` **全域**（rpc + api + common + proto）的独立第二轮验证」，
> **验证方法与 §6.6 不同**：不是「找当前实现的缺陷」，而是**按 Go → Spring 的
> 行为等价模型逐维度对照**（契约 / 输入 / 分支 / 外部交互 / 状态 / 错误身份 / 超时 / 重试 / 并发 / 生命周期）。
> 两节**无重叠**：§6.6 的 6 项本节一条都没重复发现，本节的 10 项 §6.6 也没抓到。

#### 6.7.0 编号命名空间 —— **以及一次必须公开纠正的错误**

**本轮发现用 `DRIFT-MSG-V-0xx`**（V = verification 第二轮），不复用 `DRIFT-MSG-*` 也不复用
`DRIFT-MSGAPI-*`。

> 🔴 **纠正一：本节首版声称「与 §6.6 的 6 项均无重叠」——这是错的。**
> §6.6 除了那 6 条 P0，**另有一张 DRIFT-MSG-004~012 的 9 行漂移表**，
> 其中 **8 行正是本轮的 V-001/002/003/004/005/006/007/008**。逐条对照：
>
> | §6.6 漂移表 | 内容 | = 本轮 |
> |---|---|---|
> | DRIFT-MSG-004 | `listNotifications` 的 `total` 丢 `unread_only` | **= V-001** |
> | DRIFT-MSG-005 | DB 错误身份 `1015` vs `5001`/`5008` | **= V-003** |
> | DRIFT-MSG-006 | `markConversationRead` 多写一列 | **= V-006** |
> | DRIFT-MSG-007 | `trimContent` 空白集 | **= V-008** |
> | DRIFT-MSG-008 | `ListConversations` 缺次级排序键 | **= V-005** |
> | DRIFT-MSG-009 | `GetConversationMessages` 排序键 | **= V-004** |
> | DRIFT-MSG-010 | 时间戳 epoch | **= V-007** |
> | DRIFT-MSG-011 | 广播批量插入未分片 | **= V-002** |
> | DRIFT-MSG-012 | 部署面（无 servlet 容器 vs 引了 web starter） | 本轮未复核 |
>
> ⇒ **本轮真正「新发现」的只有 2 项：V-009（直方图 bucket）与 V-010（错误解包深度）**，
> 外加补测试时挖出的 3 条 P0（V-011/012/013）。
>
> **那第二轮的价值在哪？** 不在「发现」，在**定级 + 修 + 守**：
> §6.6 的表把它们标成 `UNKNOWN`（可接受性未定），本轮逐条补齐了取证、
> 可接受性判断、修复与防回退用例。§6.6 的 DRIFT-MSG-009 甚至写着
> 「雪花单调 ⇒ 实践等价，**ACCEPTABLE**」——本轮证明它**不等价**（并列时顺序不同）并已修。

> 🔴 **纠正二：本节首版称「`DRIFT-MSG-008` 在本文件里根本不存在」——也是错的。**
> 它存在，在 §6.6 的那张漂移表里。
>
> 🔴 **纠正三（根因）：§6.6 自身就有三套编号分配互相撞车**，这是本文件最严重的台账问题：
>
> | 编号 | §6.6.1 第一张表（3 条 P0） | §6.6.1 第二张表（模块起不来） | §6.6 漂移表（9 行） |
> |---|---|---|---|
> | `DRIFT-MSG-004` | — | `NacosRegistration` Bean 名撞车 | `listNotifications` 的 `total` |
> | `DRIFT-MSG-005` | — | 缺 `@MapperScan` | DB 错误身份 |
> | `DRIFT-MSG-006` | — | 缺 `spring-boot-flyway` | `markConversationRead` 多写列 |
>
> ⇒ **同一个 ID 在同一文件里指两个完全不同的缺陷。**
> 而 §0 的速查表只引用了「-001..-006」（第二张表那套），
> **完全看不到漂移表的 9 行** —— 这才是「§0 看着全绿、实际漂移一片」的机制来源。
> ⇒ **`MessageAtomicService` javadoc 里那句「DRIFT-MSG-006」与「DRIFT-MSG-008」并非笔误，
> 它引用的确实是漂移表那套编号**，只是被 §0 省略了。
>
> **本轮不做编号重整**（会牵动 §0/§6.6 大量交叉引用，代价大于收益），
> 但**必须留档**：查漂移时**两个 ID 空间都要查**，
> 且 `DRIFT-MSG-V-*` 是本轮之后的新空间。

#### 6.7.1 验证结论

**`SEMANTICALLY NOT EQUIVALENT`** —— 10 项差异。

| 编号 | 维度 | 一句话 | 风险 | 状态 |
|---|---|---|---|---|
> ⚠️ **2026-10-24 第十一轮前向指针** —— 下面这张表的「状态」列是 **2026-10-22 第二轮**的判断，
> **V-001 / V-002 / V-004 / V-005 / V-008 已修、V-003 已签字（收窄）、V-006 已修、V-007 已修**
> ⇒ 与同文件 §0 的速查表**一致**（那边已全部更新）⇒ **不要**照着这列「待修」去估工作量。

| **DRIFT-MSG-V-001** | 结果语义 | `listNotifications(unread_only=true)` 的 **`total` 丢了 `is_read=false` 过滤** | HIGH | ✅ 已修（第一轮） |
| **DRIFT-MSG-V-002** | 外部交互 | 广播通知**单条 foreach 多行 INSERT**，收件人 ≥ 5462 时整批失败 | HIGH | ✅ 已修（第一轮） |
| **DRIFT-MSG-V-003** | 错误身份 | DB / 内部故障的业务码 **Go `1015` → RAG `5001`/`5008`** | MED-HIGH | ⏸ 已签字（第八轮**收窄**到只剩「不回显原始错误文本」） |
| **DRIFT-MSG-V-004** | 排序语义 | 会话消息排序键 **`created_at asc` → `id asc`** | MED | ✅ 已修（第一轮） |
| **DRIFT-MSG-V-005** | 排序语义 | 会话列表**缺次级排序键 `updated_at DESC`** | MED | ✅ 已修（第一轮） |
| **DRIFT-MSG-V-006** | 副作用集 | `markConversationRead` **多写一列** `conversation.updated_at` | MED | ✅ 已修（第一轮） |
| **DRIFT-MSG-V-007** | 序列化 | 时间列 **`timestamptz` → `timestamp`** + 时区解读不同 | MED（条件性） | ✅ 已修（第七轮 V-020：8 列改 `TIMESTAMPTZ`） |
| **DRIFT-MSG-V-008** | 输入解释 | `String.trim()` vs `strings.TrimSpace` **空白集不同** | MED | ✅ 已修（第一轮） |
| **DRIFT-MSG-V-009** | 可观测指标 | 直方图 **bucket 布局不同**（DefBuckets vs Micrometer 默认） | LOW | ✅ **已签字留档**（`SD-2026-10-24-02`，2026-10-24） |
| **DRIFT-MSG-V-010** | 错误传播 | 错误**解包深度不同**（Go `errors.As` 穿透 vs Java `instanceof` 不穿透） | LOW / latent | ✅ **已签字留档**（`SD-2026-10-24-03`，2026-10-24） |

#### 6.7.2 两项 HIGH 的取证

##### 🔴 DRIFT-MSG-V-001：`total` 丢了 `unread_only` 过滤

```
Go  message.go:39-48
    query := Model(&Notification{}).Where("recipient_id = ?", recipientID)
    if unreadOnly { query = query.Where("is_read = ?", false) }   // ← 先加过滤
    total = query.Count()                                          // ← 复用同一个 query ⇒ total 只数未读

RAG MessageDomainService.doListNotifications:756-763
    items  = notificationMapper.listByRecipient(..., unreadOnly, ...)   // ← 有 is_read=false
    total  = selectCount(QueryWrapper.eq("recipient_id", uid))          // ← 没有 is_read
    unread = countUnreadByRecipient(uid, 0, 0)
```

**可观察影响**：`unread_only=true` 时响应体 `{"total":N,"unread_count":M}` 中 `total` 恒 ≥ M 且不随过滤变化
⇒ 前端分页器按 total 算总页数会多翻出空页。

**Counterfactual（§3.2 检验）= YES**：Spring 开发者写 `countByRecipient` 时几乎必然只按主维度过滤，
把 `unreadOnly` 当成「列表查询条件」而不是「计数条件」——这个写法看起来完全合理。
**这是典型的迁移期语义漂移**，不是笔误。

**测试现状**：message-rpc 现有 21 条测试中**无任何一条断言 `total`** ⇒ 这条漂移完全无人看守。

##### 🔴 DRIFT-MSG-V-002：广播在 >5461 用户时整体失败

```
Go   message.go:22-27   conn.CreateInBatches(items, 200)     ← 自动分批，收件人数无上限
RAG  NotificationMapper.insertBatch   单条 <foreach> 多行 INSERT，12 列/行
```

Postgres 协议单条 SQL 绑定参数上限 **65535** ⇒ `floor(65535/12) = 5461`。
**recipients ≥ 5462 时整条 SQL 失败 → 事务回滚 → 一条通知都发不出去。**
广播模式下 `collectBroadcastRecipients` 收集**全量用户** ⇒ 这是广播通知的规模悬崖。

> 🔴 **附带发现（注释说了假话）**：
> [`MessageAdminSendNotificationService`](/Users/pr/Documents/java&golang/RAG/service/message/message-api/src/main/java/com/rag/message/message/api/service/MessageAdminSendNotificationService.java)
> 的类注释写着「message-rpc 按 `page-size=200` **分批插库**」。
> **这句话是事实错误**：`broadcast.page-size` 只用于 `admin.GetUserList` 的 **RPC 拉取分页**，
> 插库侧没有任何分批逻辑。
> ⇒ §3.3 的又一次发作：**注释给了错误的安全感**，让读代码的人以为规模问题已经处理过了。

#### 6.7.3 三条「已签字但表述需要校正」的差异

| 项 | 现状表述 | 本轮校正 |
|---|---|---|
| **DRIFT-MSG-V-006** | RAG javadoc 标为「DRIFT-MSG-006 有意保留的差异，需 review 签字」 | 编号撞车（见 §6.7.0）；且**它并不能补偿 DRIFT-MSG-V-005** —— 补写 `updated_at` 只是多了一个 Go 没有的副作用，排序键仍然缺失 |
| **`String.trim()`** | RAG javadoc 定性为「有意保留的**表达层差异**，不在本项修复范围」 | **定性错误**。它改变了**输入解释**（全 NBSP 的 content：Go 判空→5003，RAG 入库）与**持久化结果**，按 §3.2 的字段差异规则**必须作为行为差异继续验证**，不能按「表达层」豁免 |
| **`bizCodeFromError` 判定顺序** | RAG javadoc 写「顺序重要：先识别 status，再识别 CodeError」 | **顺序无关**：Java 单继承下 `CodeError` 与 `StatusRuntimeException` 互斥，两个分支不可能同时命中。建议在 javadoc 补这句，否则后人会误以为顺序有语义而不敢动 |

#### 6.7.4 UNRESOLVED（本轮新增；**编号用 `UNRESOLVED-MSG-V-*`**，避开 §6.6 已占用的 -001..-005）

| 编号 | 问题 | 为何无法判定 | 需要什么 |
|---|---|---|---|
| ~~UNRESOLVED-MSG-V-000~~ | ~~JVM 实际时区 ⇒ V-007 是否真的发作~~ | — | ✅ **2026-10-22 用户已确认：部署时区 = Asia/Shanghai** ⇒ 该漂移**真实发作**。见 §6.7.10 |
| **UNRESOLVED-MSG-V-001** | Nacos namespace/group 不一致 | `message-rpc` 用 `public`/`DEFAULT_GROUP`，user/admin 用 `sea-rtw`/`USER_GROUP`（`message-api/application.yml:14-19` 自述） | 两侧对齐后才能做活体对拍（**跨域**：user/admin 域） |
| **UNRESOLVED-MSG-V-002** ✅ **已关闭（2026-10-24 第十轮）** | ~~`insertBatch` 参数上限的确切触发点~~ | —— | ✅ **关闭依据**：真 PG 守门 `notificationBatchInsertMustSurviveTheParameterLimit` **真造了 6000 条** recipient（> 5461 阈值）并断言全部落库 ⇒ 阈值已被活体覆盖；且 `DRIFT-MSG-V-002` 的修法本身（按 200 分片）**已把参数上限变成不可达** ⇒ 原问法已**过时** |
| **UNRESOLVED-MSG-V-003** ✅ **已关闭（2026-10-24 第十轮）** | ~~DDL 的索引 / 唯一约束 / NOT NULL / DEFAULT~~ | —— | ✅ **关闭依据**：**双侧活体 DDL 对拍**（Go 侧跑真源自己的 `AutoMigrate`、RAG 侧按序执行 V1~V5，逐条 dump `pg_indexes` / `pg_constraint` / `information_schema.columns`）⇒ 索引 12 个、约束 3 个、37 列的可空性与 DEFAULT **全部核过**。<br>⚠️ **关闭的是「未核过」这个状态，不是「DDL 完全一致」** —— 实测差异：索引 3 处（V-021 ×2 名字 + V-022 ×1 列数）+ **DEFAULT 6 处**（⇒ 新登记 `DRIFT-MSG-V-031`）。<br>⇒ 复现：`bash tools/difftest/run-message-ddl-parity.sh` |

> ⚠️ **DRIFT-MSG-V-007 的条件化结论**（本轮唯一「影响面覆盖全部时间字段」的一条）：
> Go 侧 `gorm.io/driver/postgres@v1.6.0` 的 `postgres.go:251-255` 把 `schema.Time` 映射为
> **`timestamptz`**，且 DSN 显式钉 `TimeZone=Asia/Shanghai`（`db.go:22`）；
> RAG 侧 DDL 是 **`TIMESTAMP`（无时区）**，读出时用 `LocalDateTime.toEpochSecond(ZoneOffset.UTC)`
> （[`MessageRpcSupport.java:87,106,261-264`](/Users/pr/Documents/java&golang/RAG/service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/MessageRpcSupport.java)）。
> ⇒ **JVM 时区 = UTC 则完全一致；非 UTC 则出错。**
> ⚠️ **2026-10-22 19:08 实测校正：方向是「RAG 比 Go 晚 8 小时」，不是「早」。**
> （此前本文与首次报告都写成「早」，属推理错误，已由 `jshell` 实测算准。
> 该错误已作为 §3.3 同款的反面教材留在本节：<b>时区偏移的方向不能靠推理，必须实测。</b>）
> 配置维度上也不等价：**Go 显式钉了 session 时区，RAG 什么都没钉**。

#### 6.7.5 本轮确立的两条通用纪律

> **纪律 A：持久化查询的「过滤条件」必须逐条覆盖到「计数」而不只是「列表」。**
> DRIFT-MSG-V-001 的形态是：列表查询带了过滤、计数查询没带。
> 这在代码评审里极难一眼看出（两行相距 7 行、变量名都是同一个 `unreadOnly`），
> 且**单测极容易漏**——因为测「列表返回了正确的 2 条」不需要 `total` 参与断言。
> ⇒ 断言分页接口时，**`total` 与 `items` 必须成对断言**，否则 `total` 是无人区。

> **纪律 B：`String.trim()` / `trimStart()` / `strip()` 不得当作 `strings.TrimSpace` 的等价物。**
> 跨语言迁移时**空白字符集**是默认被忽略的输入解释维度。最小判法：
> 把 `"\u00A0"`（NBSP）、`"\u3000"`（全角空格）、`"\u2028"` 各写一条用例。
> 顺带记一个容易反向误判的点：`strip()` 用的是 `Character.isWhitespace`，
> 它**也**不认 NBSP（`isWhitespace('\u00A0') == false`），**两个都不是**，
> 要对齐 Go 必须自己写显式集合。

#### 6.7.6 本轮核对通过、无需改动的部分（留档以免重复审计）

- **错误身份**：21 项 code + msg 逐条 1:1；`msg → code` 反查表（排除 200/500 + first-write-wins）一致。
- **IDL**：9 个 RPC、全部字段号、3 个枚举值全部 1:1。
- **业务分支**：9 个 Logic 的校验顺序、错误码、gRPC code 逐条对齐，含 `5201/5204/5205/5206/5207` 这几个易错分支。
- **事务边界**：会话 + 消息原子写；事务内**语句序**（先 UPDATE 会话后 INSERT 消息，刻意对齐以避免加锁顺序相反）。
- **HTTP 契约**：除 401 外**恒 200 + envelope**；401 **空 body**；404 / 405 / 415 各自独立。
- **身份解析**：claim 缺失 / 非数字 / 非整数 / 超 int64 **全部 → 5004（HTTP 200，非 401）**；身份先于参数校验。
- **角色硬编码**：user 端点 `SenderRole=USER` + `RecipientRole=USER`；admin 端点 `ADMIN`。
- **解析层**：`,string` 双向接受（根因是 `json.Number.Kind() == string`）；`,optional` vs `,omitempty` 的 null 语义。
- **保护层四层参数值**：MaxConns 10000 / MaxBytes 1MiB / CpuThreshold 900 / Breaker 开。
- **client 熔断**：acceptable 判据 6 个 code 逐条对齐；**retry 两侧都无（保持不加）**。
- **超时**：rpc 2s（Go `zrpc/config.go:46` 默认 2000ms）/ http 3s（Go `rest/config.go:55` 默认 3000ms）。
- **`trimPreview` 上限是 80 码点**（不是 255 char、不是 UTF-16 code unit）——这个曾经修错的地方现在是对的。
- **`MarkNotificationRead` 单条 UPDATE + `RowsAffected==0 → 5203`**，原子性比 Go 修复前的形态更好。

#### 6.7.7 修复结果（2026-10-22 晚，与 6.7.1 同一批纪律）

| 编号 | 结论 | 修法一句话 | 防回退用例 | 变异验证 |
|---|---|---|---|---|
| **DRIFT-MSG-V-001** | ✅ 已修 | 新增 `NotificationMapper.countByRecipient(recipientId, role, kind, unreadOnly)`，`total` 与 `items` 共用同一个 `unreadOnly`；`countUnreadByRecipient` 改为委托它，避免两处漂移 | `listNotificationsTotalMustRespectUnreadOnly`（真 PG，**total 与 items 成对断言**） | ✅ 去掉 `unreadOnly` ⇒ 红 |
| **DRIFT-MSG-V-002** | ✅ 已修 | `insertNotificationsTx` 按 `INSERT_BATCH_SIZE=200` 切片，全部仍在同一 `@Transactional` 内 | `notificationBatchInsertMustSurviveTheParameterLimit`（真 PG，**6000 条**）+ `batchInsertMustStayAtomicAcrossChunks`（跨片仍原子） | ✅ 去掉切片 ⇒ 红 |
| **DRIFT-MSG-V-004** | ✅ 已修 | `listByConversationPage` 排序键 `id` → `created_at`（与 DDL 索引 `(conversation_id, created_at)` 对齐） | `conversationMessagesMustBeOrderedByCreatedAtNotId`（真 PG，**构造与 id 相反的次序**） | ✅ 改回 `getId` ⇒ 红 |
| **DRIFT-MSG-V-005** | ✅ 已修 | `listByParticipant` 补次级键 `orderByDesc(updatedAt)` | `conversationListMustUseUpdatedAtAsTiebreaker`（真 PG，**last_message_at 完全并列**） | ✅ 去掉次级键 ⇒ 红 |
| **DRIFT-MSG-V-006** | ✅ 已修 | 删掉 `markConversationReadTx` 里的 `touchUpdatedAt`（**理由**：DRIFT-MSG-V-005 修好后，它当初的补偿对象已消失） | `markConversationReadMustNotTouchConversationUpdatedAt`（真 PG + **正向确认消息真被标读**，防假绿） | ✅ 加回 `touchUpdatedAt` ⇒ 红 |
| **DRIFT-MSG-V-008** | ✅ 已修 | `trimContent` / `trimPreview` 改走 `trimGoSpace`；逐码点复刻 Go `unicode.IsSpace` 的 **25 个码点**（`go run` 实测枚举得出，非凭记忆） | `MessageRpcSupportTrimPreviewTest` +5 例（NBSP / U+3000 / 分隔符 / **U+001C–1F 反向断言** / 25 码点全枚举 / 全空白塌成空串） | ✅ 3 次：`trim()` ⇒ 4 红、**`strip()` ⇒ 4 红**、改共享 helper ⇒ 5 红 |
| **DRIFT-MSG-V-009** | ✅ **已签字留档**（`SD-2026-10-24-02`，2026-10-24 用户拍板） | 未改代码（留档）；⚠️ 第十轮**双侧实测**：Go 11 桶 vs Micrometer 69 桶，**边界零交集** | — | — |
| **DRIFT-MSG-V-010** | ✅ **已签字留档**（`SD-2026-10-24-03`，2026-10-24 用户拍板） | 未改代码（留档）；⚠️ 第十轮**逐行核对两侧消费点**并**订正了描述**：真正不一致的只有 `CodeError` 那一支 | — | — |

##### 🔴 由新测试**当场挖出**的 3 条 P0 —— 静态审计两轮都没抓到

这三条**不在 §6.7.1 的 10 项里**，是写 `MessageSemanticEquivalenceTest` 跑真 PG 时暴露的。
**`message-rpc` 此前 21 条测试 + `message-api` 59 条 + `semantic-probe` 23 例，
没有一条能碰到它们** —— 再次印证 §6.6.4 的结论。

| 编号 | 缺陷 | 后果 | 修法 | 用例 |
|---|---|---|---|---|
| **DRIFT-MSG-V-011** | `insertBatch` 里写的是 `#{item.isRead}`，而实体字段叫 `read`（getter `isRead()`）⇒ MyBatis `Reflector` 找不到属性 | `org.apache.ibatis.reflection.ReflectionException: There is no getter for property named 'isRead'` ⇒ **`SendNotification` 100% 失败**。而 `insertBatch` 是通知写入的**唯一生产路径**（`notificationMapper.insert(` 在 main 下零命中），`sendNotification` 又**零测试覆盖** ⇒ **admin 的定向通知与广播从未成功过一次** | `#{item.read}` | `notificationBatchInsertMustSurviveTheParameterLimit` |
| **DRIFT-MSG-V-012** | `listByParticipant(role, id, limit, offset)` 的调用点传的是 `(…, in.getOffset(), limit)` ⇒ 实参落到 `limit=offset, offset=limit` | SQL 变成 `LIMIT <offset> OFFSET <limit>`，`offset=0` 时即 **`LIMIT 0`** ⇒ **`ListConversations` 永远返回空列表**（会话列表页全空）。与 DRIFT-MSG-002 同款形态：一个被写错的普通值让整条路径静默失效、**不产生任何错误日志** | 修正实参顺序 | `conversationListMustUseUpdatedAtAsTiebreaker`（`itemsCount` 期望 2 实得 0） |
| **DRIFT-MSG-V-013** | `extra` 列是 `jsonb`，但用的是 MP 自带 `JacksonTypeHandler` —— 其 `AbstractJsonTypeHandler.setNonNullParameter` 反编译出来是 **`ps.setString(...)`**，pgjdbc 按 **VARCHAR** 发送 | `ERROR: column "extra" is of type jsonb but expression is of type character varying` ⇒ 通知写入再次失败。且 `@TableName` 缺 `autoResultMap = true` ⇒ **读**方向同样失效 | 新增 `JsonbMapTypeHandler`（`ps.setObject(i, json, Types.OTHER)`）+ `@TableName(autoResultMap = true)` | `notificationBatchInsertMustSurviveTheParameterLimit` |

> **DRIFT-MSG-V-013 的两条通用结论**（对全仓 MyBatis-Plus 模块都成立）：
> 1. **MP 自带的 `JacksonTypeHandler` 不能直接用于 `jsonb` 列** —— 它用 `setString` 绑定，
>    pgjdbc 会带显式 VARCHAR 类型，Postgres 直接拒收。正确写法是 `Types.OTHER`。
> 2. **`@TableField(typeHandler = …)` 写在字段上 ≠ 它在 SELECT 上生效** ——
>    MyBatis-Plus 只有在 `@TableName(autoResultMap = true)` 时才把 typeHandler 应用到
>    自动生成的结果映射。这是 §3.3「断言 Bean 存在 ≠ 断言机制生效」在 ORM 层的同款形态。

##### 🔴 本轮踩到的方法论陷阱：**Maven 增量编译会把变异 class 留在 target 里**

做 §6.6.5 要求的变异验证时，8 个变异点跑完、源码**全部还原**之后复跑，
**仍然红**（`column "extra" is of type jsonb`），而源码里 `JsonbMapTypeHandler` 明明是
`ps.setObject(i, ..., Types.OTHER)`。加 `clean` 后立刻全绿。

⇒ **本仓做变异测试时，每次变异与每次还原都必须 `mvn clean`**，
否则**「还原后复跑」这一步测的不是当前源码，而是上一次变异残留的 class**。
这条会让「变异 → 还原 → 复跑」的标准闭环得出**假的红灯**，
进而得出「测试不稳定 / 修不好」的**假结论** —— 比不测更坏。

##### ⚠️ 另一条同源陷阱（本轮也踩到）：**编译错误不会出现在你习惯 grep 的那几行里**

`JsonbMapTypeHandler` 首版有未捕获的 `JsonProcessingException`，编译失败。
而当时的验证命令是
`mvn -q ... | grep -E "Tests run:|ERROR\]   |ERROR: "`，
**编译错误的格式是 `[ERROR] /path:[line,col] 未报告的异常错误…`，一条都没匹配上**，
输出里只剩一个 python 的 `ok` —— 看起来像「跑完了、没报错」。

⇒ **验证纪律**：**先看退出码**（`EXIT=$?`），再看测试统计。
用 `grep` 过滤 mvn 输出时，**必须额外匹配 `[ERROR] /`（编译错误行）**，
或干脆先 `if [ $? -ne 0 ]` 判断。**「过滤后没输出」≠「没有失败」。**

#### 6.7.10 DRIFT-MSG-V-007 决策与修复：部署时区 = Asia/Shanghai（2026-10-22 19:08）

**前置**：§6.6 已把它登记为 `DRIFT-MSG-010` 并挂在 `UNRESOLVED-MSG-001`「JVM 运行时区未知」上。
用户于 2026-10-22 19:08 确认 **message 服务的部署时区是 Asia/Shanghai** ⇒ 该条目**解除挂起，本轮关闭**。

##### 🔴 第一件事：纠正方向 —— 我此前写反了，且是靠推理得出的

首次报告与 §6.7.4 都写「RAG 比 Go **早** 8 小时」。**方向反了：实际是「晚」**。
`jshell` 实测（JVM 时区 = Asia/Shanghai）：

```
JVM default zone       = Asia/Shanghai
写入的墙钟(无时区)      = 2026-10-22 19:08:17
真实瞬时(trueInstant)  = 2026-10-22T11:08:17Z

Go  .Unix()             = 1792667297
RAG toEpochSecond(UTC)  = 1792696097   差 +8 小时
修法 atZone(system)     = 1792667297   差  0 小时
```

⇒ **RAG 的 `created_at` / `latest_message_at` / `updated_at` 比真源晚 8 小时。**
⇒ 与 §3.3 同款的反面教材：**时区偏移的方向不能靠推理，必须实测。**
（推理为什么容易反：`toEpochSecond(UTC)` 把「+08:00 的墙钟」当成 UTC，得到的 epoch **偏大**；
但人直觉上「少了一次换算」容易往「偏小」想。）

##### 根因

Go 侧时间列是 **`timestamptz`**（gorm postgres driver `postgres.go:251-255` 把 `schema.Time`
映射成 `timestamptz`），DSN 钉 `TimeZone=Asia/Shanghai`（`db.go:22`）⇒ 读出 `time.Time`（绝对瞬时）
⇒ `.Unix()` **与时区无关**。
RAG 侧列是 **`TIMESTAMP`（无时区）**，实体 `LocalDateTime`，pgjdbc 对无时区列**不做换算**
⇒ 这是一个**不含 epoch 信息的墙钟**，而读侧却用 `toEpochSecond(ZoneOffset.UTC)` 把它当成 UTC。

##### 修法：读侧统一走 `atZone(ZoneId.systemDefault())`

`MessageRpcSupport` 新增私有 `toEpochSecond(LocalDateTime)`，替换 **4 处**
（`toNotificationItem` / `toMessageItem` / `buildConversationItem` 的 `latestMessageAt` 与 `updatedAt`）。

**为什么用 `systemDefault()` 而不是硬编码 `Asia/Shanghai`**：
写侧用的是 `LocalDateTime.now()`（= JVM 本地墙钟），读侧也用本地时区解释 ⇒ **写读自洽**，
**无论 JVM 跑在哪个时区 epoch 都正确**。硬编码 `Asia/Shanghai` 反而会在「JVM 改成 UTC」的部署上错。
⇒ **本条的决策不是「部署时区必须是上海」，而是「写读必须用同一个时区解释」** ——
后者才是可长期成立的性质。

| 防回退用例 | 锁住的性质 | 变异验证 |
|---|---|---|
| `notificationCreatedAtMustBeTheTrueInstantInShanghai` | 上海时区下等于真实瞬时 | ✅ 退回 `toEpochSecond(UTC)` ⇒ 红 |
| `mustStaySelfConsistentUnderADifferentJvmZone` | **换到 UTC 仍自洽**（禁止硬编码时区） | ✅ 硬编码 `Asia/Shanghai` ⇒ 红 |
| `messageCreatedAtMustUseTheSameConversion` | 会话消息走同一套规则 | ✅ 同上 ⇒ 红 |
| `conversationTimestampsMustUseTheSameConversion` | 会话两个时间字段同样 | ✅ 同上 ⇒ 红 |
| `epochMustDenoteAbsoluteInstant` | epoch 是绝对瞬时（还原回墙钟 == 写入值） | ✅ 同上 ⇒ 红 |
| `nullTimestampsMustStillMapToZero` | null 仍回 0，修时区不得改 null 语义 | — |
| `notificationTimestampsMustRoundTripLosslessly`（真 PG） | **`TIMESTAMP` 列对墙钟无损** —— 整个修法依赖这个前提 | — |

> ⚠️ **每条用例都自己 `TimeZone.setDefault(...)` 并在 finally 还原**：
> 跟着宿主机时区跑的话，在 UTC 机器上修复前后结果**完全相同**（差 0）⇒ 用例假绿。
> 判据必须与宿主机时区无关（§6.7.5 纪律 B 的同源问题：**默认值域也要显式钉**）。

##### ⚠️ 遗留的稳健性缺口（**不在本次修复范围，已留档**）

列类型仍是 `TIMESTAMP` 而非 `TIMESTAMPTZ`，数据**不自描述瞬时**。上表的自洽性成立的前提是
「一个 DB 只由同一时区的 JVM 写」。以下情形会重新错位：

1. 迁移期新旧实例并存、且两个实例时区不同；
2. 换时区后回读历史数据；
3. 同一张库被另一个时区的进程写入。

**根治需要一条 `V4` 迁移**把三张表的时间列改成 `TIMESTAMPTZ`
（`ALTER TABLE … ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Shanghai'`），
**且读侧仍需保留 `atZone(systemDefault())`**（因为实体仍是 `LocalDateTime`，pgjdbc 按 JVM 时区折算）。

**本轮不做该迁移的理由**：① 行为已通过读侧修法完全正确，迁移不改变当前可观察行为；
② `ALTER TABLE` 在已有数据的表上有锁表与回填风险；
③ §6.7.4 的 `UNRESOLVED-MSG-V-003`（索引/唯一约束/NOT NULL/DEFAULT 完全 NOT CHECKED）说明
**这张 DDL 还没有被系统性核对过**，在没核对前做类型迁移是盲改。
⇒ 记为**已知缺口**，与 `tools/difftest/check-ddl-static.py` 的跑库核对一起做。
> ✅ **2026-10-24 第十轮更新**：`UNRESOLVED-MSG-V-003` **已关闭**（双侧活体 DDL 对拍，见 §6.7.4 与
> [`DRIFT/message/rpc.md`](../message/rpc.md) 的「第十轮」小节）⇒ 这条「盲改」的约束**已解除**。
> ⚠️ 但对拍同时实测出 **6 列 DEFAULT 残差**（⇒ `DRIFT-MSG-V-031`）——
> 若将来要动时间列的迁移，**那 6 列的 DEFAULT 也要一起想清楚**（别再叠一层没核过的改动）。

#### 6.7.8 DRIFT-MSG-V-003 决策：**签字保留 RAG 侧，不对齐 Go**（2026-10-22）

**决策：不改代码。** RAG 的 `5001` / `5008` 比 Go 的 `1015` 更正确，对齐过去是**引入缺陷**。

| 判据 | Go 行为 | RAG 行为 | 谁更对 |
|---|---|---|---|
| 业务码语义 | `1015` "service busy" —— 把**数据库故障**说成**服务繁忙** | `5001` "internal server error" / `5008` "database update failed" | **RAG** |
| 信息泄露 | `shared.RPCError` 把 gRPC description（= gorm 原始错误文本）塞进 envelope：<br>`{"code":1015,"msg":"ERROR: relation \"x\" does not exist (SQLSTATE 42P01)"}` | 只回码表文案 | **RAG** |
| 可运维性 | DB 故障被计为「服务繁忙」⇒ 值班会去看**容量/限流**，而故障在 DB | 码可区分「系统内部」与「写库失败」 | **RAG** |

**签字理由**：AGENTS.md 的对齐原则是「复刻 Go 的**行为**」，而**不是**「复刻 Go 的**缺陷**」。
Go 侧这条同时命中信息泄露与错误归因两条，已属缺陷而非语义。

**必须一起签字的代价**（不签字就会被人当 bug 报回来）：
- ⚠️ **监控 / 告警规则若按 `code == 1015` 判定「下游熔断」，迁移后不再命中** ——
  基础设施故障现在落在 `5001` / `5008`。**告警规则需要同步改**。
- ⚠️ **业务错误路径完全一致**（`5002/5003/5004/5005/5201-5207` 逐条核对过），
  所以这条差异**只在基础设施故障时显现**，压测与正常流量测不出来 ——
  也正因如此，它不会被「测试全绿」自动发现，必须靠本节留档。

#### 6.7.9 本轮测试资产总账

| 类 | 规模 | 覆盖 |
|---|---|---|
| `MessageSemanticEquivalenceTest`（新增，真 PG Testcontainers） | **7** | DRIFT-MSG-V-001 / -002 / -004 / -005 / -006 / -011 / -012 / -013 |
| `MessageRpcSupportTrimPreviewTest`（+5 例） | **11** | DRIFT-MSG-V-008 |
| `MessageRpcSupportTimeZoneTest`（新增，纯单元） | **6** | DRIFT-MSG-V-007 |
| `MessageGrpcInterceptorWiringTest` / `MessageTransactionWiringTest` / `MessageMarkNotificationReadTest` | 15 | §6.6 的 DRIFT-MSG-001/002/003 |
| **message-rpc 合计** | **39**（原 21） | — |

**message 域回归（2026-10-22 19:26，隔离跑）**：

| 范围 | 结果 |
|---|---|
| `message-rpc`（39 例） | ✅ 全绿 |
| `message-api`（100 例，**单模块隔离连跑两次**） | ✅ BUILD SUCCESS，0 错误 |
| `tools/semantic-probe/run-parity.sh` | ✅ 23/23 一致，EXIT=0 |

> ⚠️ **全仓口径当前不可复现**：本机有另一个会话并行跑 Maven（6 个进程），
> 持续改写 `service/like/**` 并重建共享 `target/` 与 `~/.m2` SNAPSHOT jar。
> 在并发构建期间出现的 `like-rpc` 编译失败与 `MessageApiMvcPipelineTest` 的
> 41 条 `ClassNotFoundException`，**均与本轮改动无关**（该类在 `users-common/target/classes`
> 与已安装 jar 里都存在；本轮未碰 `GlobalExceptionHandler` / `message-api/pom.xml` / `users-common`）。
> ⇒ 引用测试数字时**必须标注「隔离跑」还是「全仓跑」**。

> ⚠️ **仍然零覆盖的**：`sendNotification` / `sendChatMessage` 的**端到端 happy path**（走真 gRPC 端口）、
> `listConversations` 的分页翻页、任何 ≥5461 收件人的真实广播。
> 本轮的 `MessageSemanticEquivalenceTest` 是**进程内直调 service 层**，不是双端口对拍；
> 真源对拍仍要等 `UNRESOLVED-MSG-003`（Go 真源在本环境起不来）。


---

## 2026-10-24 第十轮：**双侧活体 DDL 对拍**（关掉 `UNRESOLVED-MSG-V-002` / `-003`）+ 3 条签字 + 1 条新登记

> **用户指示**：「A5：起真 PG 跑 `check-ddl-static.py`，核对 DDL 索引/唯一约束/NOT NULL/DEFAULT」
> ⇒ 本轮**没改任何业务代码**。

| 项 | 结论 |
|---|---|
| **新守门** | `bash tools/difftest/run-message-ddl-parity.sh` —— 一条命令建两个真库（Go 侧跑**真源自己的 `AutoMigrate`**，RAG 侧执行 V1~V5）并逐维度比对。**全程不写被冻结的真源仓库**（只复制副本） |
| **脚本升级** | `tools/difftest/check-ddl-static.py` 新增 `--domain message` / `--indexes` / `--allow-idx` / `--allow-default` —— 补上 §3.1 对照清单里**最后两项从未被机器核过**的维度（`pg_indexes` / `pg_constraint`） |
| **列类型 / 长度** | ✅ **0 差异**（37 列逐字相同） |
| **NOT NULL（可空性）** | ✅ **0 差异**（`V-020` 的 `DROP NOT NULL` 精确命中） |
| **约束（`pg_constraint`）** | ✅ **两侧完全一致**（各 3 个 PK）⇒ **V-023 双向确认**（两侧的 `uk_conversation_pair` 都是**唯一索引**而非表约束） |
| **索引** | 🔴 **3 处差异**：`idx_notification_recipient_read`（Go 1 列 / RAG 2 列 ⇒ **V-022**，已签字 `SD-06`）＋ `idx_conversation_*` ×2 名字不同（⇒ **V-021**，**本轮签字** `SD-2026-10-24-01`）⇒ 其余 9 个逐条一致 |
| **DEFAULT** | 🔴 **6 处差异** ⇒ **`DRIFT-MSG-V-031`（本轮已签字保留，`SD-2026-10-24-04`）** —— RAG 侧多 `DEFAULT CURRENT_TIMESTAMP`，Go 侧无默认；**当前不可观测**（3 条 INSERT 全显式绑定），但**必须知情**：这是「当前无路径」而非「结构性无路径」 |
| **关闭** | ✅ `UNRESOLVED-MSG-V-002`（6000 条真 PG 守门已覆盖阈值，且原问题已因分片修法而过时）<br>✅ `UNRESOLVED-MSG-V-003`（**关的是「未核过」，不是「DDL 完全一致」**）<br>✅ `UNRESOLVED-MSG-009`（用户 2026-10-24 拍板关闭 —— `V5` 已把 8 列改 `TIMESTAMPTZ`，活体对拍实测两侧类型 0 差异；剩下的是**部署口径**，不是代码问题） |
| **签字** | ✅ `SD-2026-10-24-01`（V-021 索引名）· `SD-2026-10-24-02`（V-009 直方图桶，**双侧实测边界零交集**）· `SD-2026-10-24-03`（V-010 错误解包深度，**并订正了原描述**）· `SD-2026-10-24-04`（**V-031 六列 DEFAULT，保留签字**） |
| **顺手更正** | `UNRESOLVED-MSG-009` 的**前提已过期**（它建议的「补一条 `V4` 迁移」已由 `V5` 做完）⇒ 别再去补；剩下的只是部署口径问题 |

⚠️ **逐条证据在** [`DRIFT/message/rpc.md`](../message/rpc.md) 的「第十轮」小节（含实测量、复现命令、踩到的两个环境坑）。
