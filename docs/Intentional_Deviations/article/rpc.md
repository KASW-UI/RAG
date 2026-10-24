<!-- @new 2026-10-24 · article 域 rpc 层签字偏离（SD-2026-10-24-01） -->

# `article` 域 rpc 层签字偏离（SD-2026-10-24-01）

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」**。
>
> ⚠️ 读之前先记住：**这是决策，不是缺陷。** 审查时不必重复质疑，**也不得「顺手改回 Go 的旧写法」**。

## SD-2026-10-24-01：写路径的**全局锁序**统一为 `article → publication`

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字**（2026-10-24 由 `align/article` 工作树产出；证据已闭环，**等用户签字**） |
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
