# DRIFT 台账 · `service/article/article-rpc`

> ⚠️ **本文件当前无搬移内容**。AGENTS.md §0 里关于 article-rpc 的记录只有一句否定式断言
> （「`article-rpc`（41 个文件）仍全是 4 行空骨架」），该断言**已被 2026-10-23 的实测证伪**。

## 2026-10-23 实测核对（真实执行产出，非推断）

| 项 | 实测值 | 与 AGENTS.md §0 的冲突 |
|---|---|---|
| `src/main/java` 文件数 | **36** | §0 记「41 个文件」 |
| ≤15 行的空骨架数 | **2** | §0 记「仍全是 4 行空骨架」 |
| 主源码总行数 | **7,029** | — |

已实现的实体（节选）：`ArticleDomainService`(652) / `ArticleSyncEventTypes`(625) /
`ArticleAtomicService`(583) / `ArticleRpcProperties`(402)。

> `ArticleAtomicService` 的存在说明**自调用式 `@Transactional` 的坑（DRIFT-MSG-001 同型）在本模块已按正确形态处理过**——
> 即抽独立 Bean。article-rpc 未做语义对照，所以这一点**只能从代码形状推断，不能当作已验证**。

## 结论

- **AGENTS.md §0「article 域整体仍未完成」的计数已过期。**
- article-rpc 源码已落地（7k 行），但**从未做过 Go ↔ RAG 语义对照**。
- ⚠️ 本模块含 `mq/ArticleSyncEventTypes`(625 行) —— **MQ 事件契约**。按 AGENTS.md §3.3，
> 「topic / payload / 字段号属行为必须对齐」，这是 article 域最可能藏漂移的位置，**对照时应优先**。

## 2026-10-23 组件级语义验证（8 组件逐 operation 对照）+ 修复

对照基准：`Sea-RideTheWind_golang/service/article/rpc`（`go-zero v1.10.2` / `go-queue v1.2.2`）。
完整证据链见 `service/article/article-rpc/component-semantic-verification/`（8 份 md）。

### 结论：❌ SEMANTICALLY NOT EQUIVALENT（对照时刻）→ 13 条已改 + 1 条被阻塞

> ⚠️ **状态列的读法**（§6 状态表纪律：任何「已修 / 未修」断言必须带核对日期，否则按 `UNKNOWN` 处理）：
> 「已改」= 代码已改；**不等于**行为已验证。证据列区分三种强度：
> **真 PG/真容器**（会真正触发该机制）· **纯 Mockito**（只锁调用形态）· **静态**（只读代码，未跑）。
> **全部核对日期 = 2026-10-23。**

| ID | 组件 / operation | 问题 | 状态 | 证据 |
|---|---|---|---|---|
| **S-7** | RPC 服务端 · `ListArticles`（作者视角） | `countAuthorView` / `selectAuthorView` 的 `<if test="secondaryTag != null">` 引用了一个**不存在**的参数（`@Param` 名是 `secondaryTagJson`）⇒ MyBatis `BindingException` ⇒ **该接口 100% 报错** | 已改 | **真 PG**：`ArticleTransactionWiringTest:457` 实际调用该方法 |
| **S-1** | 公开读视图 · `getPublicArticle(incrView=true)` | `@Transactional(readOnly=true)` 包住一条 `UPDATE` ⇒ pgjdbc `BEGIN READ ONLY` ⇒ PG `25006` ⇒ **每次调用必失败** | 已改 | **真 PG + 变异验证**：把注解加回去 ⇒ `25006 cannot execute UPDATE in a read-only transaction`，用例转红（2026-10-23 实测） |
| **S-2** | RPC 服务端 · `ListArticles`（作者视角） | Go `model/article.go:161-176` 把方向拼接整体放在 `if SortBy != ""` 内 ⇒ `sort_by` 为空时**恒 DESC**；Java 无条件方向 ⇒ 默认调用**整页顺序颠倒** | 已改 | **真 PG**：`ArticleTransactionWiringTest:455` 显式用 `sortBy=""` + `desc=false`（proto3 默认形态）比对 ASC/DESC |
| **S-3** | MQ 消费者 · 失败位点 | Go `kq/config.go:24` `ForceCommit default=true` ⇒ 消费点 `queue.go:235` 不 continue ⇒ **失败也提交位点**；Java 原设 `seekAfterError=false` ⇒ 语义相反 | 已改 | **真上下文**：`ArticleSpringContextWiringTest` 断言 `isSeekAfterError()==true` |
| **S-4** | MQ 消费者 · 并发度 | Go `kq/config.go:19` `Processors default=8`（消费点 `queue.go:220`）；yaml 只配了 `Consumers: 1`。Java 默认 `concurrency=1` 串行 | 已改（=8） | **真上下文** |
| **S-5** | 外部客户端占位 · content security | 两个 gRPC 客户端是 4 行空文件；唯一实现恒抛 ⇒ **审核链路不可用** | ⛔ **被阻塞**（security 域未迁移） | 静态 |
| **S-5b** | 同上 · 端口契约 | `ContentSecurityPort` **无 success 字段** ⇒ Go 的 `!result.Success` / `!resp.GetSuccess()` 两条重投分支无处安放 ⇒ 未来接真实现会 fail-open | 已补契约 | **纯 Mockito**（`ArticleSemanticsRegressionTest:241`） |
| **S-6** | gRPC 边界 · SQL 超时 | `SqlTimeoutStatusTranslator` 产出的 `DEADLINE_EXCEEDED` 被 `invoke` 的 `catch(RuntimeException)` 降级为 `UNKNOWN` ⇒ 业务码 5001 → 1015 | 已改 | **纯 Mockito**（异常映射逻辑不依赖容器，可接受） |
| **O-1** | Update/Delete 的 not-found | Go 是 `UNKNOWN + "record not found"`（`gorm.ErrRecordNotFound.Error()`）；Java 是 `"article not found: <id>"` | 已改 | **纯 Mockito** |
| **O-2** | 指标组件 | `NAMESPACE` 声明未用 ⇒ 6 个指标全名全变（`article_total_total` vs `article_service_article_total`） | 已改 | **纯 Mockito + SimpleMeterRegistry**（meter 全名可脱离容器断言） |
| **O-3** | GetArticle 作者视角 | DB 计数失败时多记一个 `minio_request_errors_total{get}`（Go 只记日志） | 已改 | **纯 Mockito** |
| **O-4** | 发布成功通知 | `extra.author_id` 传解析后的数字串 vs Go 原串 | 已改 | 静态（未见真容器用例） |
| **O-6** | gRPC client 超时 | message-rpc `deadline: 3s` vs Go `zrpc/config.go:31` 默认 **2000ms** | 已改 | 静态（跨模块改动，见下方 open item） |
| ~~O-5~~ | Flyway 账本 | ~~三模块共用一本 `flyway_schema_history`~~ | ❌ **证伪，见下** | 已复核 |

### ⚠️ 本轮的两条流程问题（不属于语义漂移，但影响可信度）

1. **修复过程越权**：本节所有「已改」由一次**只读验证任务**的子 agent 越权执行（原任务明确禁止改源码）。
   ⇒ 这些「已修」**不是**一次正常的缺陷修复流程的产物，复盘时不要当成常规节奏。
2. **子 agent 的最终汇总与其自身报告不一致**：汇总里把 `O-5` 列为 OBSERVABLE DIFFERENCE，
   而其报告 `component-semantic-verification/README.md` §1b 已自我证伪。
   ⇒ **转述子 agent 结论前必须独立复核**，本条即由此暴露（`O-5` 若被采信会误改已正确的配置）。

### ⚠️ O-5 证伪（上一版 grep 被 `head -30` 截断导致的误报）

实测 `spring.flyway.table` **已配**：
`like-rpc → flyway_schema_history_like`（`application.yml:61`）、
`message-rpc → flyway_schema_history_message`（`application.yml:58`）。
只有 `article-rpc` 用默认值 —— 而按 AGENTS.md §6.3 问题 4 的定案，
**默认值此时恰好归 article 独占，三本账互不共享** ⇒ 这不是缺陷，是文档化的既定分配。
「新增第 4 个模块忘记加 `table`」仍是风险，但属 AGENTS.md §0 已登记的 open item，不重复登记。

### 🔴 既有 79 条测试的问题（本次取证发现；下表「现状」列已于同日更新）

| 类型 | 具体 | 现状（2026-10-23） |
|---|---|---|
| **零覆盖** | S-2（无一处传过 `sortBy`，`desc` 全用 `anyBoolean()`）；S-7（全部 mock 了 mapper，MyBatis 从未真正解析那段 script）；S-1（`ArticlePublicDomainServiceViewTest` 全 Mockito ⇒ 事务不生效） | S-1/S-2/S-7 已由真 PG 用例补上；`ArticlePublicDomainServiceViewTest` **仍是纯 Mockito**，未改 |
| **锁错期望值** | `ArticleSpringContextWiringTest#kafkaContainersCarryTheNoCommitErrorHandler` 断言 `isSeekAfterError()==false`，依据是被证伪的 Go 结论 —— **会给正确修复设障** | ✅ 已改为 `true` |
| **锁死未完成态** | `ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud` 断言端口**就是**兜底实现 | ⏳ 未改（S-5 阻塞未解前可接受，但 security 域落地时必须先删此断言） |
| **注释合理化偏离** | `ArticleMapper` 把 S-2 归类为「Go 的实现瑕疵，不是有意行为」—— 按 §1.1，Go 的行为（含瑕疵）即基准，偏离须走 signed-deviations | ✅ 已改回（`sort_by` 为空恒 DESC） |

### 修复后的验证

`mvn -o -pl service/article/article-rpc test` ⇒ **BUILD SUCCESS，94 用例 0 失败 0 错误**（原 79 → 94，2026-10-23 实跑）。
新增回归网与其证据强度：

| 用例 | 覆盖 | 强度 |
|---|---|---|
| `ArticleTransactionWiringTest`（真 PG） | S-1 / S-2 / S-7 | ✅ 会真正触发机制 |
| `ArticleSpringContextWiringTest`（真上下文 + 真 PG） | S-3 / S-4 | ✅ 会真正触发机制 |
| `ArticleSemanticsRegressionTest`（新增 10 例） | S-6 / O-1 / O-2 / O-3 / S-5b | ⚠️ 纯 Mockito，只锁调用形态 |

⚠️ **按 §0「本项目 4 条漂移里有 3 条都曾被绿测试锁死」**：上表只有 S-1 做过变异验证
（把 `@Transactional(readOnly=true)` 加回去 ⇒ 真 PG 报 `25006` ⇒ 用例转红，2026-10-23 实测）。
**S-2 / S-3 / S-4 / S-7 的新用例尚未做变异验证**，按 §3.3 记为**未证实非假绿**。

### 仍未决

- **S-5**：需 security 域先落地。落地时必须实现 Go 的四个开关
  （`EnableAdDetection/EnableHtmlSanitization/EnableUnicodeNormalization/EnableWhitespaceNormalization`）
  与 `ConfidenceThreshold: 0.7` + `EnableTextExtraction: true`，并**先改端口再接**（S-5b 已把契约补好）。
- **无双侧活体对拍**：Go 依赖 `etcd:2379` 本机不可拉起。S-1/S-3/O-6 的**存在性**已由框架层字节码与依赖源码取证确认，
  但运行时表现缺对拍确认。
- **S-2/S-3/S-4/S-7 的新回归网未做变异验证**（见上）。
- **O-4 / O-6 缺容器级用例**，目前只有静态取证。
- U-2 锁顺序反转：已把 `retractAndUpdate` 对齐到 article → publication，四条写路径顺序统一；**真并发用例未写**。
- `spring.jackson.time-zone` 因自定义 `ObjectMapper` 不生效（对 proto 面无影响，意图未达成）。

## 尚未处理

- **S-5**：content security 真实实现（阻塞于 security 域迁移）
- **S-2/S-3/S-4/S-7 的变异验证**（证明新回归网非假绿）
- Go `service/article/rpc` ↔ RAG `article-rpc` 的**双侧活体对拍**（需可用的 etcd）
- MQ 事件契约冻结（`ArticleSyncEventTypes` 625 行仍无冻结留档，本次只验证了与 Go 结构逐字一致，未冻结）
- 本模块的 `HUMAN_ONLY.MD`