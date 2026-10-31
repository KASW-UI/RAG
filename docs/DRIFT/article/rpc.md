# DRIFT 台账 · `service/article/article-rpc`

> ## 📍 **当前态指针（2026-10-29 · G3 lane `align/r3-article-rpc-close`）**
>
> | 项 | 当前值 |
> |---|---|
> | **R 档** | **R3**（原 **R2**） |
> | **组件状态** | ✅ 达标（归属本组件的必相对齐项 = **0**） |
> | **证据强度** | **L4**（真 Kafka 位点 ＋ 真 PG 落库 ＋ 双侧活体逐字节） |
> | **最新一轮** | **§12**（2026-10-29 第十二轮）—— 逐条结论见 **§12.8** |
> | **`S-5`** | ✅ **范围外**（judge.md §1.1 R0）⇒ **不是「阻塞」** ⇒ 见 **§12.1** |
> | **`U-3` / `U-4`** | ✅ **闭合**（真 Kafka 实测 · 行为断言）⇒ 见 **§12.3** |
> | **`U-9`** | 🟡 拆两半：`U-9a` ✅ 闭合 · `U-9b` 🟡 取证缺口 ⇒ 见 **§12.4** |
> | **`U-4′`** | ✅ **销号**（3 场景逐字节 ＋ 两侧播种同一行）⇒ 见 **§12.2** |
> | **可复算判定命令** | 见 **§12.7** |
>
> ⚠️ **本文件头的「组件判定」行（`:46`）与 §10 的 18 项 checklist 都是 2026-10-23 第二轮的快照** ⇒ **不要当现状读**（judge.md §1.5：L0 = 台账的**条目级最新状态**）。

> ⚠️ **本文件当前无搬移内容**。AGENTS.md §0 里关于 article-rpc 的记录只有一句否定式断言
> （「`article-rpc`（41 个文件）仍全是 4 行空骨架」），该断言**已被 2026-10-23 的实测证伪**。

> ## 📌 怎么读这份台账（**先看这一段**）
>
> 本文件是**多轮累积**的，里面有**十个轮次小节**（2026-10-23 ~ 2026-10-24：
> 2 个「修复轮」+ 8 个编号轮：第二 / 第三 / 第四 / 第五 / 第七 / 第九 / 第十 / 第十一轮），
> 每轮各有一张「判定变化（FIX-xx）」表 ⇒ **那些表是历史快照，只反映当轮结论，不要当现状读。**
>
> | 你要找的东西 | **唯一权威面** |
> |---|---|
> | 某条 `DRIFT-ARTICLE-RPC-xxx` 的**现状** | **§7 的对应条目**（带「当前状态（日期）」横幅 ⇒ 指向关闭它的那一轮） |
> | 某条 `UNRESOLVED-xxx`（`U-*`）的**现状** | **§9 Unresolved**（逐条标了 ✅ 已关闭 + 关闭依据） |
> | 最新的整体判定变化 | **最后一轮**的「判定变化」表（当前是第十一轮 / FIX-17） |
> | 某条结论的**完整取证过程** | 对应轮次的小节（第三轮 / 第四轮 / 第五轮 / 第七轮 / 第九轮 / 第十轮 / 第十一轮） |
>
> ⇒ **判断「某条现在是什么状态」，一律回 §7 / §9 与最新一轮，不要回历史轮次表。**
>
> ### ⚠️ 「第 N 轮」**跨文件会撞号** —— 引用时必须带「哪个文件」
>
> article 域的轮次编号**不是本文件私有的**；但**两份文件在第 2~5 轮区间是撞的**
> （各自从 1 开始数）⇒ **只写「第 N 轮」会指错**。
>
> | 轮次 | 落在哪个文件 | 主题 |
> |---|---|---|
> | 「2026-10-23 修复轮」/「2026-10-23 第二轮」/「2026-10-24 修复轮」（前两个未编号） | **rpc.md** | 初始对齐 / `DRIFT-004` 不修 / `DRIFT-003` 已修 |
> | 第二轮 | **api.md** | 装配层真容器测试（关 5 条 `UNRESOLVED`） |
> | 第三轮 | **api.md** | `DRIFT-007` 码点序 |
> | 第四轮 | **api.md** | `DRIFT-005` query 参数上限 |
> | 第五轮 | **api.md** | `UNRESOLVED-007` / `-008` |
> | 第六轮 | **api.md** | `DRIFT-006` LOW 不修 |
> | 第七轮 | **rpc.md** | `U-2` 锁序实测 |
> | 第八轮 | **api.md** | `UNRESOLVED-010` 关闭 + `DRIFT-009` 已修 |
> | 第九轮 | **rpc.md** | `DRIFT-007` Kafka brokers |
> | 第十轮 | **rpc.md** | `U-5'` 关闭 + `DRIFT-004` 展开并关闭 |
> | 第十一轮 | **rpc.md** | `P-U-2` 关闭（wire 字节级） |
> | 第十二轮 | **api.md** | `strconv.Quote` 的 Latin-1 补充段 |
>
> ⇒ **第 6 轮起**两份文件的编号**不再撞**（每轮只落在一个文件里，编号顺延），
> 且与 commit 顺序一致（已用 `git log` 逐条核对）。
> ⚠️ 「**修复轮**」这个标题**不参与**数字编号（`api.md` 1 个、`rpc.md` 2 个，日期不同）
> ⇒ 引用时**必须带日期**。
>
> ⚠️ 组件判定（2026-10-24 第十一轮）：**`SEMANTICALLY NOT EQUIVALENT`**（不变）。
> ⚠️ 证据强度上限：**真 PG / 真上下文 / 真 Tomcat / 双侧探针**，
> **无双侧活体对拍**（Go 侧仍需 `etcd:2379`）⇒ 所有 ✅ 一律读作
> 「**在该证据强度下一致**」，不是「已闭环」。
>
> ✅ **本域签字偏离现状（2026-10-24）**：[`docs/Intentional_Deviations/article/rpc.md`](../../Intentional_Deviations/article/rpc.md)
> 的 **两条 SD（`SD-2026-10-24-01` / `SD-2026-10-24-02`）均已由用户签字批准** ——
> ⇒ 本域**没有「待签字」的悬空项**；审查时直接当既定决策，**不得「顺手修正回 Go 的旧行为」**。

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
| **S-7** | RPC 服务端 · `ListArticles`（作者视角） | `countAuthorView` / `selectAuthorView` 的 `<if test="secondaryTag != null">` 引用了一个**不存在**的参数（`@Param` 名是 `secondaryTagJson`）⇒ MyBatis `BindingException` ⇒ **该接口 100% 报错** | 已改 | **真 PG + 变异验证**（2026-10-24）：`ArticleJsonbResultMapWiringTest#selectAuthorViewReadsJsonb` 真调 MyBatis；把 `<if>` 的参数名改回 `secondaryTag` ⇒ `BindingException` ⇒ **用例转红** |
| **S-1** | 公开读视图 · `getPublicArticle(incrView=true)` | `@Transactional(readOnly=true)` 包住一条 `UPDATE` ⇒ pgjdbc `BEGIN READ ONLY` ⇒ PG `25006` ⇒ **每次调用必失败** | 已改 | **真 PG + 变异验证**：把注解加回去 ⇒ `25006 cannot execute UPDATE in a read-only transaction`，用例转红（2026-10-23 实测） |
| **S-2** | RPC 服务端 · `ListArticles`（作者视角） | Go `model/article.go:161-176` 把方向拼接整体放在 `if SortBy != ""` 内 ⇒ `sort_by` 为空时**恒 DESC**；Java 无条件方向 ⇒ 默认调用**整页顺序颠倒** | 已改 | **真 PG + 变异验证**（2026-10-24）：`ArticleTransactionWiringTest#authorViewDefaultsToNewestFirst` 显式用 `sortBy=""` + `desc=false`（proto3 默认形态）比对 ASC/DESC；把方向拼接改回「无条件方向」⇒ **用例转红** |
| **S-3** | MQ 消费者 · 失败位点 | Go `kq/config.go:24` `ForceCommit default=true` ⇒ 消费点 `queue.go:235` 不 continue ⇒ **失败也提交位点**；Java 原设 `seekAfterError=false` ⇒ 语义相反 | 已改 | **真上下文 + 变异验证**（2026-10-24）：`ArticleSpringContextWiringTest#kafkaContainersCarryTheForceCommitErrorHandler` 断言 `isSeekAfterError()==true`；把 `setSeekAfterError(true)` 改回 `false` ⇒ **用例转红**（⚠️ 但「机制对不对」仍归 `U-3`/`U-4`，需真 Kafka） |
| **S-4** | MQ 消费者 · 并发度 | Go `kq/config.go:19` `Processors default=8`（消费点 `queue.go:220`）；yaml 只配了 `Consumers: 1`。Java 默认 `concurrency=1` 串行 | 已改（=8） | **真上下文 + 变异验证**（2026-10-24）：`ArticleSpringContextWiringTest#kafkaHandlerConcurrencyMatchesGoProcessorsDefault` 断言**工厂真正产出的容器**的 `getConcurrency()==8`；把 `setConcurrency(8)` 改回 `1` ⇒ **用例转红**（⚠️ 「8 个独立 consumer 线程」vs Go「8 个 handler 拉同一 channel」的模型差异仍归 `U-9`） |
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
| **锁死未完成态** | `ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud` 断言端口**就是**兜底实现 | ⏳ 未改（S-5 阻塞未解前可接受，但 security 域落地时必须先删此断言）<br>🔴 **2026-10-29 已重新评估 ⇒ 性质改判**（见 **§12.9**）：`S-5` 改判为**范围外**后，它**不再是「锁死未完成态」**，而是「**锁死已定稿的设计**」（security 域按 `AGENTS.md:43` **永不**落地 ⇒ 这条断言**没有过期时间**）⇒ **保留**，但「移除条件」的措辞已改写；并顺带修掉它引用的**不存在的** `ArticleContentSecurityWiringTest` |
| **注释合理化偏离** | `ArticleMapper` 把 S-2 归类为「Go 的实现瑕疵，不是有意行为」—— 按 §1.1，Go 的行为（含瑕疵）即基准，偏离须走 signed-deviations | ✅ 已改回（`sort_by` 为空恒 DESC） |

### 修复后的验证

`mvn -o -pl service/article/article-rpc test` ⇒ **BUILD SUCCESS，94 用例 0 失败 0 错误**（原 79 → 94，2026-10-23 实跑）。
新增回归网与其证据强度：

| 用例 | 覆盖 | 强度 |
|---|---|---|
| `ArticleTransactionWiringTest`（真 PG） | S-1 / S-2 / S-7 | ✅ 会真正触发机制 |
| `ArticleSpringContextWiringTest`（真上下文 + 真 PG） | S-3 / S-4 | ✅ 会真正触发机制 |
| `ArticleSemanticsRegressionTest`（新增 10 例） | S-6 / O-1 / O-2 / O-3 / S-5b | ⚠️ 纯 Mockito，只锁调用形态 |

⚠️ **按 §0「本项目 4 条漂移里有 3 条都曾被绿测试锁死」**：本表曾只有 S-1 做过变异验证
（把 `@Transactional(readOnly=true)` 加回去 ⇒ 真 PG 报 `25006` ⇒ 用例转红，2026-10-23 实测）。
**2026-10-24 第十轮已补齐 S-2 / S-3 / S-4 / S-7 四条 ⇒ 5/5 全部做过变异验证**（见下），
本表的回归网**不再有「未证实非假绿」的条目**。

### 仍未决

- **S-5**：需 security 域先落地。落地时必须实现 Go 的四个开关
  （`EnableAdDetection/EnableHtmlSanitization/EnableUnicodeNormalization/EnableWhitespaceNormalization`）
  与 `ConfidenceThreshold: 0.7` + `EnableTextExtraction: true`，并**先改端口再接**（S-5b 已把契约补好）。
- **无双侧活体对拍**：Go 依赖 `etcd:2379` 本机不可拉起。S-1/S-3/O-6 的**存在性**已由框架层字节码与依赖源码取证确认，
  但运行时表现缺对拍确认。
- **S-2/S-3/S-4/S-7 的新回归网非假绿** —— ✅ **2026-10-24 已证**（第十轮，4/4 MUT-RED）。
- **O-4 / O-6 缺容器级用例**，目前只有静态取证。
- U-2 锁顺序反转：已把 `retractAndUpdate` 对齐到 article → publication，四条写路径顺序统一；**真并发用例未写**。
- `spring.jackson.time-zone` 因自定义 `ObjectMapper` 不生效（对 proto 面无影响，意图未达成）。

## 尚未处理

- ~~**S-5**：content security 真实实现（阻塞于 security 域迁移）~~ ⇒ ✅ **2026-10-29 第十二轮改判为「范围外」**（`AGENTS.md:43` ＋ judge.md §1.1 R0 ⇒ security 三域之一不迁移 ⇒ **永不闭环** ⇒ 不是「阻塞」而是**范围外**）⇒ 落点与理由见本文件 **§12.1**
- ~~**S-2/S-3/S-4/S-7 的变异验证**（证明新回归网非假绿）~~ ⇒ ✅ **2026-10-24 第十轮已做，4/4 MUT-RED**
- Go `service/article/rpc` ↔ RAG `article-rpc` 的**双侧活体对拍**（需可用的 etcd）
- MQ 事件契约冻结（`ArticleSyncEventTypes` 625 行仍无冻结留档，本次只验证了与 Go 结构逐字一致，未冻结）
- 本模块的 `HUMAN_ONLY.MD`
---

# 第二轮组件级语义验证（2026-10-23 复核 · 只读）

> **本节不删改上文任何结论**，按 AGENTS.md「增量合并」追加。编号从 `DRIFT-ARTICLE-RPC-001` 起
> （复核前全仓 **零** `DRIFT-ARTICLE-RPC-*` 编号，故不存在撞号；`S-1~S-7 / S-5b / O-1~O-6` 全部原样保留）。
>
> **核对日期 = 2026-10-23。** 证据强度四档：
> **真 PG 探针**（本轮新造，Testcontainers postgres:16-alpine + 真实 Spring 上下文 + 真实 MyBatis 3.5.19）·
> **真上下文/真容器**（既有 Testcontainers 类）· **静态源码**（逐行读过，未跑）· **双侧活体对拍**（本轮仍 **0** 条）。

## 0. 复核基线与本轮方法

| 项 | 值 |
|---|---|
| Go 真源 | `Sea-RideTheWind_golang/service/article/rpc`（go.mod 实测 `go-zero v1.10.2` / `go-queue v1.2.2`） |
| RAG 目标 | `service/article/article-rpc`（36 个主源码文件） |
| proto | `article.proto` 由 AGENTS.md §3.4 覆盖（**复制**非翻译，14 message+enum / 63 字段号两侧逐条一致）⇒ 本轮不重复审 proto 文件本身，只审**消费方式** |
| 本轮新证据 | 真 PG 探针 6 项（`/tmp/articleprobe`，**未落入仓库**；源码零改动），MyBatis 3.5.19 + `mybatis-plus-spring-boot3-starter` |
| 本轮**没有** | 双侧活体对拍（Go 侧仍需 `etcd:2379`）、真 Kafka 活体、容器级 Prometheus scrape |

### 本轮唯一新增的一手运行时证据（探针输出，2026-10-23）

```
PROBE1 selectById(control,@ResultMap):        tags=[alpha, beta]  ext={published_once=true, last_sync_version_ms=1234}
PROBE2 selectAuthorView(NO @ResultMap):        tags=null           ext=null
PROBE3 selectByRevisionId(NO @ResultMap):      tags=null           markdown=# ok
PROBE4 selectRevisionsForPublished(NO @ResultMap): n=1              tags=null
PROBE5 updated_at after updateById = 2020-01-01T00:00Z   （预置 2020-01-01 ⇒ 未推进）
PROBE6 updateById on soft-deleted row: rows=1  brief=bump-on-deleted
```

⇒ **4 条新 SEMANTIC MISMATCH + 3 条新 OBSERVABLE DIFFERENCE**，外加 **3 条历史结论的依据被证伪 / 修正**。

## 1. Component Contract（§4 + §5）

| 项 | 内容 | 证据 |
|---|---|---|
| Purpose | 文章域的**写入侧 + 冻结读侧**：6 个 gRPC 用例（create/get/update/delete/list/upload）、2 个 Kafka 消费者（审核、文章同步结果）、1 个 1s ticker 的 outbox 中继 | 静态 |
| Public API | `CreateArticle / GetArticle / UpdateArticle / DeleteArticle / ListArticles / UploadFile`（`ArticleServiceGrpcImpl:75-109`）；proto 6 rpc 与 Go `article_service_server.go:26-54` 一一对应 | 静态 |
| Dependencies | PostgreSQL `first_db.public`（5 表）· MinIO · Kafka（3 topic / 4 client）· `security.rpc`（**未迁移**）· `message.rpc` · Nacos | 静态 |
| State | 全部状态在 DB：`article.status`、`article.ext_info`（9 键同步状态机）、`article_publication.pointer_version`、`article_sync_outbox.status/retry_count` | 静态 |
| Side Effects | 写 MinIO 对象 · 推 `article-creation-topic` / `article-sync-events` · 发 `SendNotification` · 6 个 Prometheus 指标 | 静态 |
| Success 语义 | 各 rpc 的 `Success=true` **不等于**文章已发布：create 成功只表示「已入审核队列」；发布发生在**同步结果消费者**里 | 静态 |
| Failure 语义 | 三档：Go 的 `status.Error(...)`（带 code）· Go 的**裸 error**（→ grpc-go `UNKNOWN`）· 抛异常重投（Kafka）。**Java 侧只有前两档**，第三档靠"抛异常"表达 | 静态 |

## 2 & 3. GO / SPRINGBOOT 语义模型（§7 / §34，逐 OP）

各 OP 的 18 字段模型见下方等价矩阵（§4）逐格展开；此处只记**两侧结构性不同、必须按轴分开看**的三点：

1. **读者视角与作者视角是两条独立的持久化语义**（Go `article_public.go` 整体 ↔ Java `ArticlePublicDomainService` + `ArticlePublicMapper`）。公开列表**永不返回正文**，公开详情**不读 MinIO**（有 revision 时）。
2. **写入侧的幂等判据全部落在 `article.ext_info`**（`last_sync_event_id` + `last_sync_version_ms` 两个键的**联合**判据）。任何让这两列读不出来的缺陷都会让发布流程**静默 no-op**。
3. **事务边界只有 4 处**（Go `RunInTx` ×2 + `WithArticleTx` ×2；Java `ArticleAtomicService` 的 4 个 `@Transactional`）。Java 侧靠**独立 Bean** 规避 self-invocation（`ArticleAtomicServiceIsAopProxied` 真 PG 已证）。

## 4. Equivalence Matrix（§24 + §35，Result 仅用 5 个枚举值）

| Operation | Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|
| CreateArticle | MinIO 先 / DB 后顺序 | put→insert（`create_article_logic.go:61-113`） | 同（`ArticleDomainService:113-151`） | EQUIVALENT | 静态 |
| CreateArticle | Kafka 失败不回滚已插入行 | 无事务，裸返回 Internal | 同，无 `@Transactional` | EQUIVALENT | 静态 |
| CreateArticle | 初始 `ext_info` 8 键 | `create_article_logic.go:97-106` | `ArticleDomainService:126-135` 逐键一致 | EQUIVALENT | 静态 + `ArticleReviewConsumerTest` |
| CreateArticle | 正文对象名 | `<articlePath><id>.md`（无哈希） | 同 | EQUIVALENT | 静态 |
| GetArticle(author) | 分支顺序 | PublicOnly → RequesterId∧IncrView 非法 → 查库 → 作者校验 → 自增 → 读 MinIO | 同序 | EQUIVALENT | 静态 + `ArticleDomainServiceSemanticsTest` |
| GetArticle(author) | 自增失败 | 只记日志，仍成功 | 同 | EQUIVALENT | 纯 Mockito |
| GetArticle(author) | 自增失败**不记指标** | 只 `LogBusinessErr` | 同 | EQUIVALENT | 纯 Mockito + SimpleMeterRegistry |
| GetArticle(author) | **列表/详情里 jsonb 两列** | GORM `StringArray`/`JSONMap` 的 `Scan` 必填 | 带 `@ResultMap` 的 `selectById` 正常；**见 DRIFT-ARTICLE-RPC-001** | **SEMANTIC MISMATCH** | 真 PG 探针 |
| GetArticle(public) | revision 优先 / legacy 回落 | `get_article_logic.go:126-168` | `ArticlePublicDomainService:95-144` | EQUIVALENT | 真 PG（`publicViewCountIncrementActuallyCommits`） |
| GetArticle(public) | incrView 的 UPDATE 不得落在只读事务 | 无事务 | 无 `@Transactional`（S-1 已改） | EQUIVALENT | 真 PG + **已做变异验证** |
| GetArticle(public) | **revision.secondary_tags** | `revision.SecondaryTags` | **恒 null**（见 DRIFT-ARTICLE-RPC-001） | **SEMANTIC MISMATCH** | 真 PG 探针 PROBE3 |
| GetArticle(public) | **DB 失败的 gRPC code** | `INTERNAL`（`:133` / `:160`） | `UNKNOWN` ⇒ 业务码 5001→1015（见 DRIFT-ARTICLE-RPC-003） | **SEMANTIC MISMATCH** | 静态 |
| GetArticle(public) | `!increased` | `NOT_FOUND "article no longer published"` | 同 | EQUIVALENT | 纯 Mockito |
| UpdateArticle | 三分支优先级 A>B>C | `update_article_logic.go:80-123` | `ArticleDomainService:330-364` | EQUIVALENT | 纯 Mockito |
| UpdateArticle | B 分支把 `last_sync_event_id` 置空 | `SetSyncState(..., "", ...)` | 同 | EQUIVALENT | 纯 Mockito |
| UpdateArticle | not-found 的 code + 文案 | `UNKNOWN + "record not found"` | 同（O-1 已改） | EQUIVALENT | 纯 Mockito |
| UpdateArticle | **`updated_at` 推进** | `Save` + `autoUpdateTime` | **永不推进**（见 DRIFT-ARTICLE-RPC-002） | **SEMANTIC MISMATCH** | 真 PG 探针 PROBE5 |
| DeleteArticle | 空 operator 与非作者同错 | `PermissionDenied "forbidden"` | 同 | EQUIVALENT | 纯 Mockito |
| DeleteArticle | 事务内语句顺序 | 指针→outbox→软删 | 同 | EQUIVALENT | 静态 |
| DeleteArticle | **已软删行是否可被写** | GORM `Save` 追加 `deleted_at IS NULL` | `updateById` 无该条件（见 DRIFT-ARTICLE-RPC-004） | **SEMANTIC MISMATCH** | 真 PG 探针 PROBE6 |
| ListArticles(author) | `sort_by==""` 恒 DESC | `model/article.go:161-176`（方向拼接在 `if` 内） | 同（S-2 已改） | EQUIVALENT | 真 PG |
| ListArticles(author) | 未知 `sort_by` | 落默认字段 + 随 desc | 同 | EQUIVALENT | 真 PG |
| ListArticles(author) | page/size 不归一 | 原样透传 + 原样回显 | 同 | EQUIVALENT | 纯 Mockito |
| ListArticles(author) | **`secondary_tags` / `ext_info`** | 有值 | **恒 null**（DRIFT-ARTICLE-RPC-001） | **SEMANTIC MISMATCH** | 真 PG 探针 PROBE2 |
| ListArticles(public) | 方向无条件应用 + `article.id` 二级键 | `article_public.go:132-147` | 同 | EQUIVALENT | 静态 |
| ListArticles(public) | `REPEATABLE_READ` 只读快照 | `article_public.go:65,128` | `@Transactional(readOnly, REPEATABLE_READ)` | EQUIVALENT | 静态（**锁死用例 `ArticlePublicListSnapshotTest` 不存在**） |
| ListArticles(public) | **`secondary_tags`** | 有值 | **恒 null**（DRIFT-ARTICLE-RPC-001） | **SEMANTIC MISMATCH** | 真 PG 探针 PROBE4 |
| UploadFile | 三段 URL 拼接 + 各自 trim | `upload_file_logic.go:93-108` | 逐条一致（含 base 不去尾斜杠） | EQUIVALENT | 纯 Mockito |
| UploadFile | content-type 回落 | `mime.TypeByExtension` 空 ⇒ octet-stream | `URLConnection.guessContentTypeFromName` 空 ⇒ octet-stream | IMPLEMENTATION DIFFERENCE | 静态（冷门扩展名两侧可能不同，已留痕） |
| UploadFile | 图片带 / markdown 不带 CacheControl | `+ :70` / 无 | 同 | EQUIVALENT | 静态 |
| 审核消费者 | 四元组（status/content/author/nonce） | `article_consumer.go:62-68` | `matchesReviewTuple` | EQUIVALENT | 纯 Mockito |
| 审核消费者 | drop 分支 ⇒ 正常返回 | 4 条 `return nil` | 4 条 `return` | EQUIVALENT | 纯 Mockito |
| 审核消费者 | retry 分支 ⇒ 不确认 | `return err` ⇒ `ForceCommit=true` ⇒ **提交位点即丢弃** | 抛异常 ⇒ recoverer ⇒ 跳过 seek（见 §7 复核与 U-3） | UNKNOWN | 静态（spring-kafka 4.0.7 源码） |
| 审核消费者 | handler 并发度 | `Processors default=8`（`kq/config.go:19` → `queue.go:220`） | `factory.setConcurrency(8)` | EQUIVALENT | 真上下文 |
| 审核消费者 | 文本/图片审核 `success=false` | 两条都 `return error`（重投） | 两条都抛（重投） | EQUIVALENT | 纯 Mockito |
| 审核消费者 | **审核链路可用性** | 两个真 client | `UnavailableContentSecurityClient` 恒抛 | **范围外**（`S-5` 已于 2026-10-29 改判：judge.md §1.1 R0 ⇒ security 域不迁移 ⇒ 不计入本组件必相对齐项）｜⚠️ **两侧可观察行为仍一致**（Go `article_consumer.go:154-155` 在 security 不可用时同样要求审核失败 ⇒ 文章卡在 `REVIEWING`）｜**能力差（Go 能审 / RAG 不能）是事实**，落点在 R0 域 ⇒ 计入域级聚合与矩阵 C | 真上下文 |
| 同步结果消费者 | 判定链顺序 ①~⑤ | `article_sync_result_consumer.go:75-107` | `ArticleAtomicService:339-396` | EQUIVALENT | 真 PG（失败回滚 + **成功路径**，2026-10-24 补） |
| 同步结果消费者 | 六项冻结输入比对 | `:103-107` | `ArticleAtomicService:384-390` | EQUIVALENT | 静态 |
| 同步结果消费者 | 通知在事务提交后 / 失败只记日志 | `:160-163` | `ArticleSyncResultConsumer:146` | EQUIVALENT | 静态 |
| 同步结果消费者 | **通知标题来源** | `article.Title`（**当前行**） | ~~`approved.title`（**冻结 payload**）~~ ⇒ **已改为当前行** | ❌ SEMANTIC MISMATCH ⇒ ✅ **已修**（`DRIFT-ARTICLE-RPC-009`） | 真 PG（行标题 ≠ 冻结标题时也能复现，2026-10-24） |
| outbox 中继 | 捞取条件 / 逐条独立 / key=AggregateID / 推 payload 原文 | `article_sync_outbox_sender.go:21-45` | `ArticleSyncOutboxRelay:89-121` | EQUIVALENT | 静态 |
| outbox 中继 | **`pollIntervalMs<=0` 兜底** | `article.go:62-65` ⇒ 1s | **未接上**（DRIFT-ARTICLE-RPC-006） | **OBSERVABLE DIFFERENCE** | 静态 |
| outbox 中继 | `@Scheduled` 是否真的跑 | goroutine + ticker | `@EnableScheduling` 已声明（`ArticleRpcApplication:65`） | EQUIVALENT | 真上下文 |
| 指标 | 6 个 Prometheus 全名 | `metrics.go:5-69` | 逐条一致（O-2 已改） | EQUIVALENT | SimpleMeterRegistry scrape |
| 指标 | 直方图桶边界 | `metrics.go:46` 显式 8 桶 | ~~`serviceLevelObjectives` + `publishPercentileHistogram` ⇒ **实际 77 桶**~~ ⇒ **已改为只用 SLO 桶（8 个）** | ✅ EQUIVALENT | **真 scrape 实测**（2026-10-24，`DRIFT-ARTICLE-RPC-010`） |
| 错误边界 | `Rpc` / `Raw` / `StatusRuntimeException` / 其它 | grpc-go `status.FromError` 四档 | `ArticleServiceGrpcImpl:119-152` 四档齐 | EQUIVALENT | 纯 Mockito |
| 错误边界 | **普通 `DataAccessException`** | 该路径 Go 本来就返 `INTERNAL` | 落 `UNKNOWN`（DRIFT-ARTICLE-RPC-003） | **SEMANTIC MISMATCH** | 静态 |
| 配置 | server 超时 70000ms | `article.go:36` + `observability/rpc.go:19-23,30-38` | `observability.rpc-timeout: 70s` + 显式挂载（`ArticleRpcApplication:108-111`） | EQUIVALENT | 真上下文 |
| 配置 | security 客户端 10000ms | yaml `SecurityConf.Timeout: 10000` | `grpc.client.security-rpc.deadline: 10s` | EQUIVALENT | 静态 |
| 配置 | message 客户端 2000ms（Go 未配 ⇒ 默认） | `zrpc/config.go:31` → 消费点 `zrpc/client.go:70-71` | `deadline: 2s`（O-6 已改） | EQUIVALENT | 静态 |
| 配置 | **每通道独立 brokers** | `KqConf.Brokers` 逐通道 | 声明但从不消费（DRIFT-ARTICLE-RPC-007） | **OBSERVABLE DIFFERENCE** | 静态 ⇒ 2026-10-24 起：校验器**启动即失败**（见第九轮） |
| DDL | 5 张表列型 / 约束 / 索引 | `article.sql` + `002_community_revision.sql` + AutoMigrate | `V8`/`V9`/`V10`（**仅大小写 + 多 3 条 COMMENT**，逐列 diff 已跑） | EQUIVALENT | 静态（逐行 diff） |
| DDL | 时间列 | `timestamptz` | `timestamptz` ⇒ **DRIFT-MSG-V-007 类偏移在本域不成立** | EQUIVALENT | 静态 |
| 启动 | **schema 门禁时机** | 建 server / 起 MQ **之前** panic | ~~`ApplicationReadyEvent`~~ ⇒ **`SmartInitializingSingleton`**（已修，见 `DRIFT-ARTICLE-RPC-005`） | ✅ EQUIVALENT | **真进程实测**（2026-10-24） |

## 5. External Interaction & Error Flow（§8 + §10）

| 外部系统 | 操作 | 失败在两侧的落点 | Result |
|---|---|---|---|
| PostgreSQL | 普通查询失败 | Go 该处多为 `INTERNAL` / 裸 error；Java 多为 `UNKNOWN` | **SEMANTIC MISMATCH**（DRIFT-ARTICLE-RPC-003） |
| PostgreSQL | `57014 query_canceled` | 两侧都翻成 `DEADLINE_EXCEEDED`（S-6 已改；`SqlTimeoutStatusTranslator` 已在 `@Import`） | EQUIVALENT |
| PostgreSQL | `article_revision_immutable` 触发器拒绝 | 两侧都让事务失败 | EQUIVALENT（真 PG 已证） |
| MinIO | `GetObject` 失败 | Java 用 `statObject` 主动探测，对齐 minio-go v7 `getObject` 的先 stat 语义（`api-get-object.go:680-705`） | EQUIVALENT |
| MinIO | put 失败 | 两侧都 `INTERNAL` + 记 `minio_request_errors{put}` | EQUIVALENT |
| Kafka | push 失败 | 两侧都记 `kafka_push_errors` 并向上抛 | EQUIVALENT |
| Kafka | **失败位点处置** | Go **提交即丢弃**；Java 走 recoverer + 跳过 seek | UNKNOWN（U-3） |
| `security.rpc` | 任何调用 | 恒抛（不可用实现） | **范围外**（`S-5` 改判 · judge.md §1.1 R0 ⇒ **落点在 R0 域** ⇒ 不计入本组件必相对齐项）｜⚠️ **不是「两侧零差异」**：Go 能审、RAG 不能是**能力差**（非行为差） | 静态 |
| `message.rpc` | 通知失败 | 两侧都只记日志、不影响发布 | EQUIVALENT |

## 6. 逐维度结论（§12–§21）

| 维度 | 结论 | 关键证据 |
|---|---|---|
| State（§12） | 9 键 `ext_info` 状态机**逻辑**等价；但读取面被 DRIFT-ARTICLE-RPC-001 打断 | 静态 + 真 PG |
| Cache（§13） | **N/A** —— 本组件无缓存层（Go 侧亦无） | 静态 |
| Auth（§14） | rpc 侧**不做**认证（Go 也不做，身份由 api 层注入 requester/operator 字段）；操作人校验三处逐条一致 | 静态 |
| Transaction（§15） | 4 处边界**集合**一致、顺序一致；`@Transactional` 真代理已证 | 真 PG |
| Concurrency（§16） | 行锁集合一致（`article FOR UPDATE` + `publication FOR UPDATE`）；**锁顺序**由 U-2 记录；Kafka offset 提交/跳过语义双侧均无活体对拍 | 真 PG（部分） |
| Timeout（§17） | server 70s / security 10s / message 2s 三处逐条一致，且都落在**整个 operation** 而非单次 read | 真上下文 + 静态 |
| Retry（§18） | Go **零重试**；Java `FixedBackOff(0,0)` 零重试 ✅；但 seek/提交分支见 U-3 | 静态（spring-kafka 源码） |
| Serialization（§19） | JSON 字段名 / 顺序 / `omitempty` 逐条一致；**两处差异**：① Go `encoding/json` 默认 HTML 转义（`< > &` → `\u003c`），Jackson 不转义（仅影响不经 PG 的 review 消息文本，且其四个字段不含这些字符）② 冻结 payload 落库前被 PG `jsonb` 规范化（**两侧同样**被规范化，故不构成差异） | 静态 |
| Config（§20） | 逐字段核对；两处声明未消费（DRIFT-007）+ 一处兜底未接（DRIFT-006） | 静态 |
| Lifecycle（§21） | 四部件同启同停 ✅；schema 门禁时机不同（DRIFT-ARTICLE-RPC-008） | 真上下文 + 静态 |

## 6-b. 历史结论复核（本轮 · 逐条，保留原文不改写）

> 下表**不替换**上文任何一格，只在其后追加「2026-10-23 复核」列。

| 原条目 | 原文结论 | 2026-10-23 复核 | 判定 |
|---|---|---|---|
| **S-1** | `readOnly` 包住 UPDATE ⇒ 每次必失败；已改 + **唯一做过变异验证** | 静态复核成立：Go `get_article_logic.go:126-168` 确实无事务；`ArticlePublicDomainService` 现在刻意无 `@Transactional`。**本轮未发现它被改回** | ✅ 维持（证据强度：真 PG + 变异） |
| **S-2** | `sort_by==""` 恒 DESC；已改 | 静态复核成立：Go `model/article.go:161-176` 的方向拼接确在 `if opt.SortBy != ""` **内**。⚠️ 但**同一条 SQL 路径**上另有一处未被发现的问题（DRIFT-001） | ✅ 结论维持，⚠️ 该方法仍有缺陷 |
| **S-3** | `ForceCommit default=true` ⇒ 失败也提交位点；已把 `seekAfterError` 设为 `true` | **Go 侧结论成立**：`kq/config.go:24`（声明点）+ `kq/queue.go:230-242`（**消费点**）复核无误，yaml 确实未配 `ForceCommit`。🔴 **但「怎么对齐」的论证被证伪**：`ArticleRpcApplication.java:143-154` 引用的 `commit(consumer, container, offset+1)` 那段代码只在 `commitRecovered=true` 时执行（`SeekUtils.java:229`），而 `FailedRecordProcessor.java:57` 默认 false，本模块从未 `setCommitRecovered(true)` ⇒ **引用的不是本模块的执行路径**。实际跳过靠的是 `SeekUtils.java:107-135`（首条 `recovered()` 为 true ⇒ 不 seek） | 🔴 **原依据修正**为：真实机制是「recoverer 处理后**不 seek**」，与 Go 的 `CommitMessages` 结果**可能**同构，但**未在真 Kafka 上证明** ⇒ U-3 / U-4 |
| **S-4** | `Processors default=8`，已把并发度设为 8 | 静态复核成立：`kq/config.go:19`（声明）+ `kq/queue.go:220`（消费点 `for i := 0; i < q.c.Processors; i++`）；yaml 只配 `Consumers: 1`（那是 `queue.go:249` 的 reader 协程数，不是 handler 并发度）。⚠️ 两者**都是"从同一个 channel 拉 8 个 handler"**，RAG 是"8 个独立 consumer 线程"，分区独占模型不同（见 U-9） | ✅ 维持（⚠️ 模型差异记入 U-9） |
| **S-5** | 两个客户端是 4 行空文件，审核链路不可用；**阻塞** | 复核：仍是 `UnavailableContentSecurityClient` 恒抛，`ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud` 仍锁死兜底实现。**security 微服务不在本项目范围内（AGENTS.md §1）⇒ 该阻塞在本项目内无法闭环** | ⛔ 维持阻塞（不可在本项目内关闭） |
| **S-5b** | 端口补 `success` 字段；已补 | 静态复核成立：`ContentSecurityPort.TextAuditResult#isSuccess` / `ImageAuditResult#isSuccess` 均存在且被 `ArticleReviewConsumer:227/252` **真实消费**（不是死代码） | ✅ 维持 |
| **S-6** | `DEADLINE_EXCEEDED` 被降级为 `UNKNOWN`；已改 | 静态复核成立：`ArticleServiceGrpcImpl:127-141` 单列 `StatusRuntimeException` 透传，且**排在** `catch (RuntimeException)` **之前**。⚠️ 但该修复**只覆盖已带 status 的异常**；普通 `DataAccessException` 仍落 `UNKNOWN` ⇒ 本轮新发现 DRIFT-003 | ✅ 维持，⚠️ 覆盖不完整 |
| **S-7** | `secondaryTag` 参数名不一致 ⇒ `BindingException`；已改 | 静态复核成立：`ArticleMapper.java:130-140` 的 `<if>` 现用 `secondaryTagJson`，与 `@Param` 一致。⚠️ **该方法的 jsonb 映射仍未修**（DRIFT-001） | ✅ 维持，⚠️ 同方法另有缺陷 |
| **O-1** | not-found 文案 `"record not found"`；已改 | 静态复核成立：`ArticleExceptions.articleNotFoundRaw()` 产出该字面量，`invoke` 的 `catch (Raw)` 映射成 `UNKNOWN` | ✅ 维持 |
| **O-2** | 6 个指标全名；已改 | 静态复核 + `SimpleMeterRegistry` scrape 一致。⚠️ 只对了**名字**（`# TYPE` 行），**未对桶边界** ⇒ U-7 | ✅ 维持；🔴 **桶边界部分已于 2026-10-24 补完**（U-7 关闭，并因此发现 `DRIFT-ARTICLE-RPC-010`：实际 77 桶 vs Go 8 桶 ⇒ 已修） |
| **O-3** | DB 计数失败不记 MinIO 指标；已改 | 静态复核成立：`ArticleDomainService:213-225` 的 catch 内**只有** `LOG.warn` | ✅ 维持 |
| **O-4** | `extra.author_id` 传原串 vs 解析后数字串 | 复核：`MessageRpcClient:90-91` 传 `authorId` 原串 ✅ 与 Go `article_sync_result_consumer.go:207` 一致。🔴 **原记录的等价性论证已被证伪并推翻**：曾写「通知标题取 `approved.title`（冻结 payload）而 Go 取 `article.Title`（当前行）；在 `last_sync_event_id`/`version_ms` 双重判据约束下两者必相等」—— **双重判据约束的是 event_id 与 version，与标题无关**；作者在 REVIEWING 期间改标题时两者**确实不等** ⇒ 已立 `DRIFT-ARTICLE-RPC-009` 并**修复**（2026-10-24） | ❌ 原结论作废 ⇒ ✅ **已修**（`DRIFT-ARTICLE-RPC-009`） |
| **O-5** | ~~Flyway 账本~~ | 复核：`article-rpc` 仍用默认 `flyway_schema_history`，按 AGENTS.md §6.3 问题 4 的既定分配归它独占 | ❌ 维持"证伪" |
| **O-6** | message-rpc `deadline: 3s` → `2s`；已改 | 静态复核成立：`go-zero@v1.10.2/zrpc/config.go:31` `Timeout int64 \`json:",default=2000"\``（声明点）+ **消费点** `zrpc/client.go:70-71` `if c.Timeout > 0 { WithTimeout(...) }`；article/rpc 的 `go.mod:17` 锁 `go-zero v1.10.2` | ✅ 维持 |

### 🔴 本轮对**代码注释**的两条独立判定（AGENTS.md §3.3.2）

1. **「ack 语义方向相反」**（`pom.xml:108` + `ArticleReviewConsumer` 类级注释 `:43-45`）：
   **机制方向确实相反**（Go「返回 error ⇒ 不确认」 vs Java「抛异常 ⇒ 不提交」），
   但**可观察结果相同**（两侧都把失败消息丢掉）⇒ 分类 **IMPLEMENTATION DIFFERENCE，不是 MISMATCH**。
   ⚠️ 这条注释有很强的"诱导修错"风险：后来者读到"方向相反"很可能去改已经正确的 `seekAfterError=true`。建议改写为「机制不同、结果相同，见 U-3」。
2. **三处 javadoc 声称的锁死用例并不存在**：
   `JsonbTypeHandler.java:46` → `ArticleJsonbMappingTest`；
   `ArticleEntity.java:25` → `ArticleJsonbMappingTest`；
   `ArticlePublicDomainService.java:157` → `ArticlePublicListSnapshotTest`。
   2026-10-23 实测 `src/test` 下只有 **7** 个测试类，**这三个名字一个都没有**。
   ⇒ 这正是 AGENTS.md §3.3.2 的形态：**以断言形式写下的、不存在的证据**。
   尤其致命的是：真正需要它们守护的 jsonb 映射（DRIFT-001）恰恰**没有被测到**，因为唯一的 jsonb 用例 `jsonbRoundTripsFaithfully`（`ArticleTransactionWiringTest:270-304`）走的是**带 `@ResultMap`** 的 `selectById`。

## 7. Semantic Drifts（§27 + §36）

> 编号 `DRIFT-ARTICLE-RPC-001~008`。**证据强度逐条标注**；凡标「静态」的均未实跑。

### DRIFT-ARTICLE-RPC-001 —— 三处 `@Select` 缺 `@ResultMap`，jsonb 两列静默变 null

> **当前状态（✅ **已修**（2026-10-23 修复轮 · FIX-01，4 处；真 PG 守门 + 变异验证））**

- **Component**: RPC 服务端 + 公开读（Mapper 层）
- **Operation**: `ListArticles`(author) / `GetArticle`(public) / `ListArticles`(public)
- **Category**: Serialization / Result-mapping semantics（§19）
- **Go Behavior**: GORM 自定义 `StringArray.Scan`（`model/article.go:44-54`）与 `JSONMap.Scan`（`:63-73`）**无条件填充** jsonb 列 ⇒ 作者列表返回真实 `secondary_tags` + `ext_info`，公开详情/列表返回 `revision.SecondaryTags`。
- **Java Behavior**: `@Select` 上**没有** `@Results`/`@ResultMap` 时，MyBatis 走 `autoMappingBehavior=PARTIAL` 的**自动映射**，按「Java 类型 + JDBC 类型」去全局 TypeHandler 注册表查 —— `List<String>` / `Map<String,String>` 在注册表里不存在 ⇒ **属性静默跳过**（不抛异常），实体字段恒 `null`。
  - 受影响的三个方法：`ArticleMapper.selectAuthorView`（`ArticleMapper.java:174-208`）、`ArticleRevisionMapper.selectByRevisionId`（`:34-36`）、`ArticlePublicMapper.selectRevisionsForPublished`（`:206-212`）。
  - 对照组（有 `@ResultMap`，正常）：`ArticleMapper.selectById`（`:42-49`）、`selectPublicById`、`selectPublicPage`、`selectByIdForUpdate`、`selectByIdUnscoped`。
- **Difference**: 读者看到的**分类标签恒为空**；作者列表的 `ext_info`（同步状态机的唯一载体）恒为空。
- **Observable Impact**:
  1. `ListArticles`(author) 每条的 `secondary_tags` + `ext_info` 丢失；
  2. `GetArticle`(public) 的 `secondary_tags` 丢失（**正文、标题、作者、时间全部正常** ⇒ 故障极隐蔽）；
  3. `ListArticles`(public) 的 `secondary_tags` 丢失。
- **Why It Matters**: `ext_info` 是审核/发布状态机的判据来源；任何下游依赖 `secondary_tags` 做分类/推荐的消费者会**静默拿到空分类**而不是报错。
- **Business / Technical Impact**: 内容分类能力在 article-rpc 读面上**完全失效**，且三处都无告警。
- **Likely Cause**: MyBatis 的 `@Select` 注解自动映射与 `@TableName(autoResultMap=true)` 是**两套机制**（后者只挂在 MP 自己生成的 SQL 上）；本模块的 mapper javadoc（`ArticleMapper.java:55-65`）**自己写明了这条坑**，但三个方法在写完该段说明之后仍漏了 `@ResultMap`。
- **Must Preserve**: jsonb 两列在**每一条**返回 `ArticleEntity` / `ArticleRevisionEntity` 的 SQL 上都必须走 `JsonbTypeHandler`。
- **Recommended Verification**: 在真 PG 上对**这三个方法**分别断言 `secondaryTags` / `extInfo` 非 null（现有的 `jsonbRoundTripsFaithfully` 只覆盖了带 `@ResultMap` 的 `selectById`）。
- **Classification**: **SEMANTIC MISMATCH**
- **Risk**: **HIGH**
- **Evidence**: **真 PG 探针**（PROBE1 对照组正常 / PROBE2、PROBE3、PROBE4 全 null）+ 静态（方法签名处无 `@ResultMap`）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修（2026-10-23 修复轮 · FIX-01）** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据（RAG 侧三处 `@ResultMap` / `@Results` **当场核过行号**）：`service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mapper/ArticleMapper.java:202`（`@ResultMap("articleEntityResultMap")` ⇒ `selectAuthorView`）· `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mapper/ArticleRevisionMapper.java:48-51`（`@Results(id = "articleRevisionEntityResultMap", … typeHandler = JsonbTypeHandler.StringListTypeHandler.class)` ⇒ `selectByRevisionId`）· `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mapper/ArticlePublicMapper.java:220`（`@ResultMap("…ArticleRevisionMapper.articleRevisionEntityResultMap")` ⇒ `selectRevisionsForPublished`）· Go `service/article/rpc/internal/model/article.go:44-54,63-73`（`StringArray.Scan` / `JSONMap.Scan` ⇒ **无条件填充** jsonb 列）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Java Behavior 列的 `ArticleMapper.java:174-208` / `ArticleRevisionMapper.java:34-36` / `ArticlePublicMapper.java:206-212` 描述的是**修复前**（无 `@ResultMap`）的位置（原句保留）；**修复后的现行位置**见上 ⇒ ⇒ 「三处 jsonb 两列静默变 null」的**根因已消除** ⇒ **不改判**。

### DRIFT-ARTICLE-RPC-002 —— `updated_at` 在所有更新路径上永不推进

> **当前状态（✅ **已修**（2026-10-23 修复轮 · FIX-02；真 PG 守门，含 SkipHooks 反向断言 + 变异验证））**

- **Component**: RPC 服务端（写入侧）+ 公开读（legacy 视图）
- **Operation**: `UpdateArticle`（A/B/C 三分支）、`ArticleReviewConsumer.rejectAsAd`、`queueSyncOutboxWithLock`、`acceptSyncResult`（失败/成功分支）、`handleDeleteResult`
- **Category**: State transition / Side effect（§12 + §22）
- **Go Behavior**: `ArticleRepo.Update` = `Save(article)`（`model/article.go:99-101`），实体带 `UpdatedAt time.Time \`gorm:"autoUpdateTime"\``（`article.go:31`）⇒ **每次写都把 `updated_at` 推进到当前时刻**。
- **Java Behavior**: 全部走 MyBatis-Plus `BaseMapper.updateById`。`ArticleEntity.updatedAt`（`ArticleEntity.java:131`）**没有** `@TableField(fill = FieldFill.INSERT_UPDATE)`，且全仓 **零** `MetaObjectHandler`（grep `MetaObjectHandler|FieldFill` 在 `service/article/**` 命中 0；对比 message-rpc 的 `ConversationEntity.java:64,67` 是**有**的）。MP 默认 `updateStrategy=NOT_NULL`（`application.yml` 未覆盖 `field-strategy`）⇒ **把从库里读出来的旧 `updated_at` 原样写回**。
- **Difference**: `updated_at` 自创建后再不变化。
- **Observable Impact**: `GetArticle`(author) 的 `update_time`；`GetArticle`(public) 的 **legacy 分支** `update_time`（有 revision 时取 `revision.published_at`，不受影响）。
- **Business / Technical Impact**: 以 `update_time` 做增量同步 / 增量抓取的外部消费者会**永远抓不到更新**；作者看到的"最后修改时间"是错的。
- **Likely Cause**: 「ORM 自动维护审计时间戳」被当成了框架默认能力 —— MP 侧需要 `@TableField(fill=…)` + `MetaObjectHandler`，两者都没有，而**代码里没有任何 `@Transactional`/注解缺失的报错**（AGENTS.md §3.3.3 第四类静默失效）。
- **Must Preserve**: 每一次写 article 行都推进 `updated_at`。
- **Recommended Verification**: 真 PG 用例：预置 `updated_at='2020-01-01'` → 改 title → 断言 `updated_at` 已推进（本轮探针 PROBE5 已给出失败态）。
- **Classification**: **SEMANTIC MISMATCH**
- **Risk**: **HIGH**（外部消费者按时间增量拉取的场景）
- **Evidence**: **真 PG 探针** PROBE5 + 静态（实体无 fill 注解 + 全仓零 `MetaObjectHandler`）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修（2026-10-23 修复轮 · FIX-02）** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/domain/ArticleEntity.java:151-152`（`@TableField(fill = FieldFill.INSERT_UPDATE)` ＋ `private OffsetDateTime updatedAt`）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/ArticleMetaObjectHandler.java:82`（`implements MetaObjectHandler`）＋ `:125-131`（`updateFill` ⇒ `setFieldValByName("updatedAt", OffsetDateTime.now(ZoneOffset.UTC), metaObject)` ⇒ **消费点**）· Go `service/article/rpc/internal/model/article.go:31`（`UpdatedAt time.Time \`gorm:"autoUpdateTime"\``）＋ `:99-101`（`ArticleRepo.Update` = `Save(article)` ⇒ **每次写都推进**）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Java Behavior 列的 `ArticleEntity.java:131`（**无** fill 注解）是**修复前**的位置（原句保留）；**修复后**的现行位置 = `:151-152` ＋ `ArticleMetaObjectHandler.java:125-131` ⇒ ⇒ 「`updated_at` 永不推进」的**根因已消除**（注解 ＋ Handler **成对**存在，缺一即静默失效）⇒ **不改判**。

### DRIFT-ARTICLE-RPC-003 —— 公开读的三处 Go `INTERNAL` 退化为 `UNKNOWN`（业务码 5001 → 1015）

> **当前状态（✅ **已修**（2026-10-24 修复轮 · FIX-04；真 PG + 变异验证））**

- **Component**: RPC 服务端 · 公开读视图
- **Operation**: `GetArticle`（`publicOnly=true`）
- **Category**: Error identity / Error propagation（§10 + §11）
- **Go Behavior**:
  - `FindPublicOne` 返回非 `ErrRecordNotFound` 的错误 ⇒ `status.Error(codes.Internal, "read publication failed")`（`get_article_logic.go:131-134`）；
  - `IncrPublicViewCount` 返回错误 ⇒ `status.Error(codes.Internal, "increment public view failed")`（`:157-161`）。
- **Java Behavior**: `ArticlePublicDomainService.getPublicArticle`（`:95-144`）对 `publicMapper.selectPublicById` / `selectPublication` / `revisionMapper.selectByRevisionId` / `incrPublicViewCount` **四处都没有 try/catch** ⇒ DB 异常以 `DataAccessException` 冒到 `ArticleServiceGrpcImpl:147` 的 `catch (RuntimeException)` ⇒ `UNKNOWN` ⇒ api 侧 `default` 分支 ⇒ 业务码 **1015**。
- **Difference**: `5001`（系统内部错误）被换成 `1015`（服务繁忙）。
- **强证据（额外）**: `ArticleExceptions.incrementPublicViewFailed()`（`ArticleExceptions.java:130-132`）**全仓零调用点** —— 这个工厂方法的存在本身就证明"这条 INTERNAL 曾被设计出来、但接线漏了"。`readPublicationFailed()` 虽被调用，却只在 `revision == null` 这一**防御分支**（`ArticlePublicDomainService.java:120`）上，而非 Go 覆盖的"整段读失败"。
- **Observable Impact**: 两套告警口径混用：DB 抖动被计成"服务繁忙/限流"。
- **Likely Cause**: 只对齐了「Go 显式 `status.Error(...)` 的分支」，没对齐「Go 用一段 catch 把一整类底层错误统一映射成 INTERNAL」的写法。S-6 修的是 `StatusRuntimeException` 透传，**不覆盖**普通 `DataAccessException`。
- **Must Preserve**: 公开读路径上所有"底层依赖失败"必须是 `INTERNAL`，只有"不可见 / 竞态落败"才是 `NOT_FOUND`。
- **Recommended Verification**: 让 `selectPublicById` / `incrPublicViewCount` 各抛一次异常，断言 gRPC code 为 `INTERNAL`。**注意**：现有 `ArticlePublicDomainServiceViewTest#publicViewIncrementFailureIsFatal` 名字像覆盖了它，实际只覆盖了 `RowsAffected==0`（`:180`）那条 `NOT_FOUND` 分支 —— **不要把那条绿测试当成已覆盖**。
- **Classification**: **SEMANTIC MISMATCH**
- **Risk**: MEDIUM
- **Evidence**: 静态（`ArticleExceptions.java:130` 零调用点 + `ArticlePublicDomainService.java:96-141` 无 try/catch + `ArticleServiceGrpcImpl.java:147-151`）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修（2026-10-24 修复轮 · FIX-04）** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticlePublicDomainService.java:273-283`（`readPublication(...)` ⇒ `:276-278` 先放行领域异常 `ArticleExceptions.Rpc`，`:279-282` 宽 catch ⇒ `ArticleExceptions.readPublicationFailed()`）＋ `:304-307`（`catch (RuntimeException e)` ⇒ `ArticleExceptions.incrementPublicViewFailed()`）· Go `service/article/rpc/internal/logic/get_article_logic.go:131-134`（`status.Error(codes.Internal, "read publication failed")`）＋ `:157-161`（`status.Error(codes.Internal, "increment public view failed")`）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Java Behavior 列的 `ArticlePublicDomainService.java:96-141`（**无 try/catch**）是**修复前**的位置（原句保留）；**修复后**的现行位置 = `:273-283` ＋ `:304-307` ⇒ ⇒ 「公开读 DB 失败 ⇒ `INTERNAL`（业务码 5001）而非 `UNKNOWN`（1015）」的**结论复核成立**（`readPublicationFailed` / `incrementPublicViewFailed` 两个工厂方法**现在都有调用点**）⇒ **不改判**。

### DRIFT-ARTICLE-RPC-004 —— `updateById` 会写已软删行；Go 的 `Save` 会拒绝

> **当前状态（🔴 **撤销**（2026-10-23 第二轮：复核发现台账的 **Go 侧描述有误** ⇒ 该条**不是**漂移，照台账修反而会制造新漂移 ⇒ 不修）<br>⚠️ **它的相邻差异**（`Save` 的 `Selects="*"` vs MP `NOT_NULL`）已在 **第十轮** 单独展开并关闭（差异真实但**不可观察**）⇒ 见本文件「第十轮」节。）**
> ⚠️ **2026-10-30 主线标记订正（原值保留）** —— 本行用的标记是 **🔴 ＋ 文字「撤销」** ⇒ 但 `docs/DRIFT/**` 的取值域里 **🔴 = 「仍开着 / 与 Go 不一致」**、**⚫ = 「已撤销 / 范围外 / N/A」** ⇒ ⇒ **同一个 🔴 在别处会被读成「仍开着」** ⇒ ⇒ **本条现行态 = ⚫ 已撤销**（不是「仍开着」）⇒ 依据（两侧逐条回到消费点复核：Go `service/article/rpc/internal/model/article.go:99-101` ＋ `gorm@v1.31.1/soft_delete.go:91-93` ＋ `gorm@v1.31.1/finisher_api.go:109-110`）见本节下方「2026-10-30 真实性复核」段。

- **Component**: RPC 服务端 · 写入侧
- **Operation**: 全部 `articleMapper.updateById` 调用点
- **Category**: Concurrency / State（§16）
- **Go Behavior**: `Save` 走 GORM 的 Update 回调，软删除插件**会追加 `deleted_at IS NULL`** ⇒ 已删行影响 0 行；且 GORM `Save` 在 0 行时会退化成 `Create` ⇒ 撞主键 ⇒ **整个事务失败并报错**。
- **Java Behavior**: MP `updateById` 生成 `UPDATE article SET … WHERE id = ?`，**不带任何软删条件** ⇒ 已软删行被**成功改写**（PROBE6：`rows=1`，`brief` 被写回）。
- **Difference**: 已下架文章的 `ext_info` / `brief` 等可被后台路径改写；Go 在同一竞态下**报错**。
- **Observable Impact**: 窄竞态窗口（调用点都先校验过 `deletedAt`，故需"校验通过之后、写入之前"发生并发删除）下，RAG 静默复活脏状态，Go 会失败并重投。
- **Likely Cause**: RAG **刻意不用** `@TableLogic`（`ArticleEntity.java:143-147` 明确说明这个选择），但"哪些写需要软删过滤"没有被显式写进 SQL —— 与"哪些读要 Unscoped"同一条纪律，只在读侧落实了。
- **Must Preserve**: 写 article 行时对已软删行要么拒绝、要么按 Go 的方式让事务失败。
- **Recommended Verification**: 真 PG：软删 → `updateById` → 断言影响 0 行 / 抛错（本轮探针 PROBE6 已给出失败态）。
- **Classification**: **SEMANTIC MISMATCH**（差异会改变"系统应该如何行为"；可达性窄，故风险 MEDIUM 而非 HIGH）
- **Risk**: MEDIUM
- **Evidence**: **真 PG 探针** PROBE6（Java 侧）+ 静态（GORM 软删除插件行为，**Go 侧未实跑** ⇒ 归入 U-4 的一部分）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **🔴 撤销** ⇒ **现行态 = ⚫ 已撤销（结论不变）**。
> 依据（Go 侧**逐条回到消费点**复核「台账原文断言 ⇒ 不成立」这一定性）：Go `service/article/rpc/internal/model/article.go:99-101`（`ArticleRepo.Update` = `m.Db.WithContext(ctx).Save(article)`）· `gorm@v1.31.1/soft_delete.go:76-96`（`SoftDeleteQueryClause.ModifyStatement` ⇒ `:91-93` 追加 `deleted_at = ZeroValue`）＋ `:98-100`（`UpdateClauses` 挂到 UPDATE）· `gorm@v1.31.1/finisher_api.go:109-110`（`RowsAffected == 0` ⇒ 回落 `clause.OnConflict{UpdateAll: true}.Create(value)` ⇒ **`ON CONFLICT DO UPDATE`，不是报错**）· `gorm@v1.31.1/callbacks/create.go:376-378`（跳过条件 ⇒ `DeletedAt` 无 default 且非 AutoCreateTime ⇒ **不被跳过** ⇒ `deleted_at` 被写回同一非零值）· 版本依据：Go `go.mod:28` = `gorm.io/gorm v1.31.1`（与本条正文写的 `v1.31.1` 一致）· 核对日期 2026-10-30。
> ⇒ ⇒ **台账原文那句「GORM `Save` 在 0 行时会退化成 `Create` ⇒ 撞主键 ⇒ 整个事务失败并报错」经复核仍不成立** ⇒ ⇒ **本条不是漂移**、照台账修反而会制造新漂移 ⇒ ⇒ **撤销维持**（原句保留）。
> ⚠️ **状态标记订正（本轮新发现 · 不改原值）**：本条用的标记是 **🔴 ＋ 文字「撤销」** ⇒ 但 `docs/DRIFT/**` 的取值域里 **🔴 = 「仍开着 / 与 Go 不一致」**、**⚫ = 「已撤销 / 范围外 / N/A」** ⇒ ⇒ **同一个 🔴 在别处会被读成「仍开着」** ⇒ ⇒ 本条的**现行态按取值域应读作 ⚫（已撤销）**（本段已写明）⇒ ⚠️ **建议主线统一该标记**（本 lane **只追加订正、不回改历史行**）。

### DRIFT-ARTICLE-RPC-005 —— schema 门禁发生在开始收流量之后 ✅ **已修（2026-10-24）**

> **当前状态（✅ **已修**（2026-10-24 第四轮 · FIX-07；真进程实测 + 变异验证））**

- **Component**: 启动 / 生命周期
- **Operation**: 进程启动
- **Category**: Lifecycle（§21）
- **Go Behavior**: `article.go:41` `model.NewArticleRepo` → `InitDB`（`db.go:62-71`）校验触发器，失败 ⇒ `panic`；该调用**在** `article.go:44` `zrpc.MustNewServer` 与 `:57-60` 两个 `kq.MustNewQueue` **之前** ⇒ 缺 `article_revision_immutable` 时进程**从不收流量**。
- **Java Behavior**（修复前）: `ArticleSchemaGate.verifySchema()` 监听 `ApplicationReadyEvent`（`ArticleSchemaGate.java:73`）⇒ 此时 gRPC server 已监听、Kafka listener 已启动。
- **Difference**: 存在一个「已在收流量、但不可变保证缺失」的窗口；窗口内任意一次误 UPDATE 都能改写已发布正文且**不报错**。
- **Observable Impact**: 短暂窗口内接受写流量；随后 Spring 因异常关闭上下文。
- **Likely Cause**: Spring 的"启动后钩子"比"刷新前钩子"容易写，于是选了一个语义上更晚的时机。
- **Must Preserve**: 门禁必须在 server / consumer 对外可用**之前**生效。
- **Recommended Verification**: 启动期注入"缺触发器"的 schema，断言**从未**建立过监听（对比现有 `ArticleSpringContextWiringTest#flywayActuallyCreatedArticleTables` 只断言表存在）。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Risk**: MEDIUM
- **Evidence**: ~~静态（两侧启动序列逐行读过）~~ ⇒ ✅ **真进程实测**（2026-10-24，见本文件末尾「第四轮」节）：
  缺触发器时 gRPC server **确实绑定过端口**（devh 的 `GrpcServerStartedEvent` 实测触发），
  修复后**不再触发**。

> ⚠️ **同构问题，跨域（2026-10-24 实测，本工作树未修）**：
> `like-rpc` / `comment-rpc` / `favorite-rpc` / `task-rpc` 的 `*SchemaGate`
> **用的是同一套 `@EventListener(ApplicationReadyEvent.class)` 写法**
> ⇒ 它们也都在"server 已起来之后"才校验。
> 是否构成同等级缺陷取决于各自门禁的 fail-fast 程度与所保护的不变量
> （article 域这条守的是「已发布正文不可变」，丢了会**静默**改写已发布内容）。
> ⇒ **不属于 article 域 scope**，登记在此供主会话跨域处理。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修（2026-10-24 第四轮 · FIX-07）** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/bootstrap/ArticleSchemaGate.java:82`（`public class ArticleSchemaGate implements SmartInitializingSingleton` ⇒ **不再是 `ApplicationReadyEvent`**）＋ `:112-115`（`afterSingletonsInstantiated()` ⇒ `verifySchema()`）＋ `:122`（`public void verifySchema()`）· Go `service/article/rpc/article.go:41`（`u := model.NewArticleRepo(c)` ⇒ 校验在**建 server 之前**）＋ `:44`（`zrpc.MustNewServer`）＋ `:58,60`（两个 `kq.MustNewQueue`）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Java Behavior 列的 `ArticleSchemaGate.java:73`（**修复前**的 `@EventListener(ApplicationReadyEvent.class)`）已不存在（原句保留）；**修复后**的现行落点 = 上面的 `:82` ＋ `:112-115` ⇒ ⇒ 「门禁在 server / consumer 对外可用**之前**生效」这一**结论复核成立** ⇒ **不改判**。

### DRIFT-ARTICLE-RPC-006 —— outbox 中继的 `pollIntervalMs<=0` 兜底根本没接上

> **当前状态（✅ **已修**（2026-10-24 修复轮 · FIX-05；真容器 + 变异验证））**

- **Component**: MQ · outbox 中继
- **Operation**: `SendPending` 调度
- **Category**: Configuration（§20）
- **Go Behavior**: `article.go:62-65`：`if relayInterval <= 0 { relayInterval = time.Second }` ⇒ 配 0 仍是每秒。
- **Java Behavior**: `@Scheduled(fixedRateString = "${article-rpc.article-sync-outbox.poll-interval-ms:1000}")`（`ArticleSyncOutboxRelay.java:78`）**直接读原值**；兜底方法 `effectiveIntervalMs()`（`:130-133`）**零调用点**（grep `effectiveIntervalMs` 仅命中定义与 javadoc 引用）。其 javadoc（`:71-76`）却写「兜底放在 effectiveIntervalMs()/effectiveBatchSize()」—— **只兑现了一半**（`effectiveBatchSize()` 确实在 `:90` 被调用，但那本就与 Go 一致）。
- **Difference**: 配置 `poll-interval-ms: 0`（或负数）时，Go = 每秒；Java = `@Scheduled` 对非正 `fixedRate` 直接拒绝（启动期异常）或行为退化。
- **Observable Impact**: 仅在运维把该值配成 ≤0 时出现；默认配置（1000）两侧一致。
- **Likely Cause**: 兜底写成了"一个可被测试调用的方法"，但没人把 `@Scheduled` 的属性指向它。
- **Must Preserve**: `pollIntervalMs <= 0 ⇒ 1s`。
- **Recommended Verification**: 单测断言"配置 0 时调度间隔为 1000ms"；或在注解上改成占位符 + 自定义 `Trigger`。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Risk**: LOW
- **Evidence**: 静态（grep 零调用点 + 注解字面量）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修（2026-10-24 修复轮 · FIX-05）** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleSyncOutboxRelay.java:94-98`（`@Scheduled(fixedRateString = "#{T(…ArticleSyncOutboxRelay).normalizeIntervalMs(${article-rpc.article-sync-outbox.poll-interval-ms:1000})}", initialDelayString = 同一个归一化表达式)` ⇒ **兜底已接进注解本身**）＋ `:118-120`（`public static long normalizeIntervalMs(long configured) { return configured <= 0L ? 1000L : configured; }` ⇒ **单一实现**）＋ `:168-170`（`effectiveIntervalMs()` 也调它 ⇒ **两处不各写一份**）· Go `service/article/rpc/article.go:62-65`（`relayInterval := …; if relayInterval <= 0 { relayInterval = time.Second }`）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Java Behavior 列的 `ArticleSyncOutboxRelay.java:78`（**修复前**的注解字面量）与 `:130-133`（旧的 `effectiveIntervalMs()`）是**修复前**的位置（原句保留）；**修复后**的现行位置 = `:94-98` ＋ `:118-120` ＋ `:168-170`（方法名也由 `effectiveIntervalMs` 改成 `normalizeIntervalMs` ⇒ 更贴合「它被 `@Scheduled` 的 SpEL 直接调」）⇒ ⇒ **「`pollIntervalMs<=0` ⇒ 1s」的结论复核成立** ⇒ **不改判**。

### DRIFT-ARTICLE-RPC-007 —— 每通道独立的 Kafka brokers 是"声明但从不消费"的配置

> **当前状态（2026-10-24 第九轮）**：🔴 **OPEN → 🟡 已处置**（差异本身仍在，落签字偏离，**已签字**）。
> 「配置声明与实际生效不一致」这个**能力差异没有消除**（RAG 仍不能逐通道切 broker），
> 但**静默忽略**已改成**启动即失败** ⇒ 差异从「无人察觉」变成「响亮报错」。
> ⇒ 完整取证与判定变化 ⇒ 本文件「2026-10-24 第九轮」节；
> ⇒ 能力差异落 [`docs/Intentional_Deviations/article/rpc.md`](../../Intentional_Deviations/article/rpc.md)
> （**SD-2026-10-24-02，已签字**），**本文不重复记**（一条问题只落一档）。

- **Component**: 配置
- **Operation**: 全部 Kafka 生产/消费
- **Category**: Configuration（§20）
- **Go Behavior**: `kq.KqConf.Brokers`（**声明点** `go-queue@v1.2.2/kq/config.go:12`）逐通道独立。
  **消费点**：`kq/pusher.go:44-47`（生产，`kafka.Writer{Addr: kafka.TCP(addrs...)}`）、
  `kq/queue.go:121`（消费，`kafka.ReaderConfig{Brokers: c.Brokers}`）。
  仓内调用点：`internal/svc/service_context.go:65,66`（两个 `kq.NewPusher`）、
  `internal/article.go:58,60`（两个 `kq.MustNewQueue`）。
  ⚠️ **2026-10-24 修正**：原文写的 `kq/config.go:14` **行号有误**，
  且 `internal/config/config.go:41` 那行 `Brokers []string` 属于 **`HotEventPusherConf`**（死配置），**不是**四个通道的字段。
  正确链：**类型声明点** `internal/config/config.go:19-22` → **字段声明点** `go-queue@v1.2.2/kq/config.go:12` → **消费点** `kq/pusher.go:44-47` / `kq/queue.go:121`。
- **Java Behavior**: `ArticleRpcProperties.KafkaConf` 完整声明了 `brokers/name/group/topic/offset`，
  yml 里也填了值（`application.yml:141-168`），但**没有任何代码读 `brokers`**；
  实际生效的是全局 `spring.kafka.bootstrap-servers`（`:65`）——
  Spring Kafka 的 `KafkaTemplate` / `ProducerFactory` / `ConsumerFactory` 是**全局单例**。
  ⇒ **2026-10-24 起**：唯一读它的生产代码是 `ArticleKafkaChannelValidator`（它只**校验**，不**连接**）。
- **Difference**: 改 `article-rpc.kq-consumer-conf.brokers` 在 Java 侧**不切 broker**（仍连全局那个），在 Go 侧会切 broker。当前两侧取值恰好相同（`kafka:9092`），所以今天没有行为差异。
- **Observable Impact**: 无（今天）；配置面**误导性**（运维改了以为切走了，实际没切）。
- **Likely Cause**: 「把 Go yaml 的字段名 1:1 搬过来」的机械对齐 —— 表达层对齐做到了，消费没跟上。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Risk**: LOW
- **Evidence**: 静态（grep `getKqConsumerConf()\.getBrokers|getKqPusherConf()\.getBrokers|getArticleSync.*Conf()\.getBrokers` 零命中；`ArticleDomainService:663` 只取 `.getTopic()`）
  → **2026-10-24 追加**：真上下文实测（`ArticleSpringContextWiringTest`，真 PG + 真 Spring 容器）证实
  「四个通道的 brokers 与全局**逐项相同**」且**校验器确实在启动期跑过**（分叉即启动失败，见第九轮节）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **🟡 已处置（差异仍在 · 已签字偏离 `SD-2026-10-24-02`）** ⇒ **现行态 = 🟡 已处置（判定不变）**。
> 依据：RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/ArticleKafkaChannelValidator.java:121`（**全仓唯一的 `getBrokers()` 读点** —— 只**校验**、不**连接**）＋ `:65`（类注释自记「四个键**声明了但没有任何消费点**」）· 签字档 `docs/Intentional_Deviations/article/rpc.md:164`（`SD-2026-10-24-02@article` · 状态 **✅ 仍成立** · 核对日期 2026-10-29）· Go `go-queue@v1.2.2/kq/config.go:12`（`Brokers []string` 声明点）＋ `kq/pusher.go:44-47`（`kafka.Writer{Addr: kafka.TCP(addrs...)}` ⇒ **生产消费点**）＋ `kq/queue.go:120-121`（`kafka.ReaderConfig{Brokers: c.Brokers}` ⇒ **消费消费点**）· 版本依据：Go `go.mod:16` = `github.com/zeromicro/go-queue v1.2.2` · 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Evidence 列的 `ArticleDomainService:663`（只取 `.getTopic()`）⇒ 现行 **`:930`**；`kq/queue.go:121`（现为 `ReaderConfig` 的 `Brokers:` 行，**正确**）保留不变；`internal/svc/service_context.go:65,66`（两个 `kq.NewPusher`）**复核正确** ⇒ ⇒ **「RAG 侧仍不能逐通道切 broker、但静默忽略已改成启动即失败」这一判定复核成立** ⇒ ⇒ **一条差异 ≠ 一个缺陷**（已签字 ⇒ 不是缺陷）⇒ **不改判**。

### DRIFT-ARTICLE-RPC-008 —— `@Scheduled` 首次执行时机与 Go ticker 不同

> **当前状态（✅ **已修**（2026-10-24 修复轮 · FIX-05；真容器 + 变异验证））**

- **Component**: MQ · outbox 中继
- **Operation**: `SendPending` 调度
- **Category**: Lifecycle / Timing（§21）
- **Go Behavior**: `time.NewTicker(interval)` ⇒ **首次触发在 interval 之后**（`article.go:94,101`）。
- **Java Behavior**: `@Scheduled(fixedRateString=…)` 未设 `initialDelay` ⇒ 按 Spring 默认**立即**执行首次。
- **Difference**: 进程刚启动时，Java 会比 Go 早 ≤1s 推第一批 outbox。
- **Observable Impact**: 极小（只是启动瞬间的时序）。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Risk**: LOW
- **Evidence**: 静态（Go 侧确证）；**Spring 侧默认值未读源码** ⇒ 该项的 Java 侧行为同时登记为 **U-4**。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修（2026-10-24 修复轮 · FIX-05）** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleSyncOutboxRelay.java:97-98`（`initialDelayString = "#{T(…ArticleSyncOutboxRelay).normalizeIntervalMs(${article-rpc.article-sync-outbox.poll-interval-ms:1000})}"` ⇒ **首次执行与 fixedRate 用同一个归一化间隔** ⇒ 不再是 Spring 的「立即执行首次」默认）· Go `service/article/rpc/article.go:94`（`ticker := time.NewTicker(s.interval)` ⇒ **首次触发在 interval 之后**）＋ `:101`（`case <-ticker.C:`）· 核对日期 2026-10-30。
> ⚠️ **原 Evidence 的 U-4 已消解**：原文写「**Spring 侧默认值未读源码** ⇒ 该项的 Java 侧行为同时登记为 **U-4**」⇒ ⇒ 本轮**已在代码里核到**（`:97-98` 显式给 `initialDelayString`）⇒ ⇒ **U-4 这半个缺口不再成立**（原句保留）⇒ ⇒ 「Java 比 Go 早 ≤1s 推第一批 outbox」的**差异已消除** ⇒ **不改判**。

### DRIFT-ARTICLE-RPC-009 —— 通知标题取冻结 payload，Go 取当前行（**原台账判成"两者必相等"，该论证已证伪**）

> **当前状态（✅ **已修**（2026-10-24 第三轮 · FIX-06；真 PG + 变异验证））**

- **Component**: MQ · 同步结果消费者
- **Operation**: `acceptSyncResult` 的一次性通知
- **Category**: External Interaction / State（§8 + §12）
- **Go Behavior**: `article_sync_result_consumer.go:147`
  `notifyAuthor, notifyTitle = article.AuthorID, article.Title`
  —— `article` 是**本次事务里 `FOR UPDATE` 锁住的那一行**；标题取**当前行**。
- **Java Behavior**（修复前）: `ArticleAtomicService.acceptSyncResult` 返回
  `new PublishOutcome(article.getAuthorId(), approved.title, true)`
  —— 标题取**冻结 payload**（outbox 里反序列化出来的 `approved`）。
- **Difference**: 两者在「作者在 REVIEWING 期间改过标题」时**不相等** ⇒ 通知正文不同：
  Go 发 `《新标题》已经完成审核并成功入库。`，RAG 发 `《旧标题》已经完成审核并成功入库。`
  （`MessageRpcClient:68-71` 的文案拼装逻辑两侧一致，**差别只在喂进去的标题**。）
- **Observable Impact**: 用户可见（站内通知正文）。
- **Classification**: **SEMANTIC MISMATCH**
- **Risk**: LOW（触发窗口窄，但**可达**，见下）
- **Evidence**: **真 PG**（`ArticleTransactionWiringTest.acceptSyncResultUsesFrozenTitleForRevisionButCurrentRowTitleForNotification`，
  2026-10-24）—— 行标题与冻结标题**故意设成不同值**，修复前该用例转红。

#### 🔴 可达性论证（这是本条与"理论差异"的分别）

**Go 侧三个事实（均已验到消费点）**：

1. **改标题立即写进行**：`update_article_logic.go:142-144`
   ```go
   if in.Title != nil { article.Title = *in.Title; sourceChanged = true }
   ```
   ⇒ `ArticleRepo.Update`（`:112`）把**新标题**落库；已冻结的 outbox payload **不跟着变**。
2. **改标题不会立刻推进 `last_sync_event_id`**：`enqueueReview`（`:232-245`）**只推 Kafka**；
   新 event_id 要等审核回调（`article_consumer.go:107` `SetSyncState(current, "reco_queued", "pending", syncReason, eventID, versionMs, "")`）才写。
3. **发布回调的双重判据只看 event_id 与 version**：`:81-82`
   ```go
   if article.ExtInfo[ExtLastSyncEventID] != result.EventID ||
      article.ExtInfo[ExtLastSyncVersion] != strconv.FormatInt(result.VersionMs, 10) || ...
   ```
   ⇒ **判据里没有标题**。

⇒ 在「标题已改」到「新 event_id 写入」这个窗口里，旧同步结果到达时**双重判据依然成立**
⇒ 发布照常发生（revision / 指针 / 域事件全部照 Go 落库），
**但 Go 的通知用新标题、RAG 用旧标题**。

⚠️ **原台账把这条判成 IMPLEMENTATION DIFFERENCE，理由是「双重判据 ⇒ 两者必相等」——
该论证把判据约束的字段（event_id/version）当成了它约束不到的字段（标题）。**
⇒ 教训见 [`docs/Pitfalls/article/rpc.md`](../../Pitfalls/article/rpc.md) 的 `PF-ARTICLE-RPC-009`。

#### ⚠️ Go 侧本来就存在**两个不同的标题源**，不可互相代替

| 用途 | Go 的源 | 位置 |
|---|---|---|
| **revision 的标题** | `approved.Title`（**冻结 payload**） | `:115` `Title: approved.Title` |
| **通知的标题** | `article.Title`（**当前行**） | `:147` |

⇒ 修复时**只**改通知那一处；revision 那一处**本来就该**取冻结值。
⇒ 回归网同时断言这两个源（任一取错都红），见 `docs/Pitfalls/article/rpc.md` `PF-ARTICLE-RPC-009`。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修（2026-10-24 第三轮 · FIX-06）** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleAtomicService.java:417`（`new PublishOutcome(article.getAuthorId(), article.getTitle(), true)` ⇒ **通知标题取当前行**）＋ `:398`（注释明写「通知标题取**当前行**（`article.getTitle()`），不是冻结 payload 的 `approved.title`」）＋ `:433`（`publishApprovedRevision` 的 javadoc 明写「**不要**把它换成冻结输入的 `approved.title`」）· Go `service/article/rpc/internal/mqs/article_sync_result_consumer.go:147`（`notifyAuthor, notifyTitle = article.AuthorID, article.Title` ⇒ **当前行**）＋ `:115` 侧的 `Title: approved.Title`（⇒ **revision 那一处**取冻结值）· 核对日期 2026-10-30。
> ⇒ ⇒ **两个标题源在两侧仍然分开**（通知＝当前行 / revision＝冻结 payload）⇒ ⇒ **「原台账的『双重判据 ⇒ 两者必相等』论证仍被证伪」这条元结论复核成立** ⇒ ⇒ **不改判**（⚠️ 守门 `PF-ARTICLE-RPC-009` 仍在 `docs/Pitfalls/article/rpc.md`，本轮**未跑** ⇒ 证据强度维持「真 PG（2026-10-24 实测）」）。

### DRIFT-ARTICLE-RPC-010 —— MinIO 耗时的直方图**实际有 77 个桶**，Go 只有 8 个

> **当前状态（✅ **已修**（2026-10-24 第五轮 · FIX-08；真 scrape + 变异验证））**

- **Component**: 指标 · MinIO 耗时直方图
- **Operation**: `ArticleRpcMetrics.observeMinio`
- **Category**: Observability（§21）
- **Go Behavior**: `metrics/metrics.go:41-47`
  ```go
  MinioRequestDuration = prometheus.NewHistogramVec(
    prometheus.HistogramOpts{
      Namespace: Namespace, Name: "minio_request_duration_seconds",
      Buckets:   []float64{0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1},
    }, []string{"operation"})
  ```
  ⇒ 分桶边界就是这 **8** 个（外加 Prometheus 经典直方图固有的 `+Inf`）⇒ **共 9 个**。
- **Java Behavior**（修复前）: `ArticleRpcMetrics.observeMinio` 同时写了
  `.publishPercentileHistogram()` **和** `.serviceLevelObjectives(MINIO_BUCKET_DURATIONS)`。
- **Difference**: 实际抓取到的桶边界是 **77** 个（0.001 / 0.001048576 / 0.001398101 / … / 30.0 / `+Inf`），
  包含 Go 完全没有的整套默认百分位桶。
- **Observable Impact**: ① `histogram_quantile(0.95, …)` 的结果与迁移前**不同** ⇒
  基于分位数的告警阈值失效；② 该指标的 series 基数从 9 涨到 77 ⇒ 存储与查询成本上升。
- **Likely Cause**: 对 Micrometer 的 API 语义误解 ——
  `publishPercentileHistogram()` 被当成了「用我给的 SLO 桶」，
  实际是「**额外**塞进一整套默认百分位桶」。
- **Must Preserve**: 桶边界与 Go **逐条**一致（迁移前后告警阈值不变）。
- **Recommended Verification**: 真 `PrometheusMeterRegistry` 抓一次 exposition，逐行比对 `le=` 标签。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Risk**: MEDIUM（告警阈值静默失效，不报错）
- **Evidence**: **真 scrape 实测**（2026-10-24，`ArticleSemanticsRegressionTest#minioHistogramBucketsMatchGoExactly`）：
  修复前实际 **77** 个边界、期望 **9** 个 ⇒ 用例转红；修复后 9/9 一致。

#### 🔴 根因（Micrometer 消费点，micrometer-core **1.16.7**）

```
DistributionStatisticConfig.getHistogramBuckets(boolean supportsAggregablePercentiles)   // :101-117
  if (percentileHistogram != null && percentileHistogram && supportsAggregablePercentiles) {
      buckets.addAll(PercentileHistogramBuckets.buckets(this));   // ← 默认百分位桶（额外的一整套）
      buckets.add(minimumExpectedValue);                          // → 0.001
      buckets.add(maximumExpectedValue);                          // → 30.0
  }
  if (serviceLevelObjectives != null) {
      for (double sloBoundary : serviceLevelObjectives) buckets.add(sloBoundary);   // ← 只是"再加"
  }
```

⇒ **是并集，不是"SLO 优先"** ⇒ `publishPercentileHistogram()` 一旦出现，SLO 桶就被"淹没"在
77 个桶里。**Go 的 8 个桶一个都没少，但多了 68 个**。

⚠️ 原实现的 javadoc 写着"换算后数值一致，因此两侧的直方图分桶边界相同"——
**这句话本身是错的**（它只对了 SLO 那一半，漏了 `publishPercentileHistogram` 那一半）。
⇒ 这也是台账 O-2「只对了名字，没对桶边界 ⇒ U-7」当时没被追下去的原因：
**错的那句注释正好在解释"为什么看起来等价"。**

## 8. Test Cases（§29 + §30，本轮**提出**而非**执行**的用例）

| CASE | 输入 | 环境 | 期望语义 | 现状 |
|---|---|---|---|---|
| CASE-R01 | 三篇文章，各带非空 `secondary_tags` + `ext_info` | 真 PG | `selectAuthorView` 返回的三行都保留 jsonb 两列 | ❌ **失败**（探针 PROBE2） |
| CASE-R02 | 一篇 PUBLISHED + 有 revision，revision 带标签 | 真 PG | `selectByRevisionId().getSecondaryTags()` 等于落库值 | ❌ **失败**（探针 PROBE3） |
| CASE-R03 | 同上，公开列表 | 真 PG | 每条 `secondary_tags` 等于 revision 值 | ❌ **失败**（探针 PROBE4） |
| CASE-R04 | `updated_at` 预置 2020-01-01，改 title | 真 PG | `updated_at` 已推进 | ❌ **失败**（探针 PROBE5） |
| CASE-R05 | 软删一行后 `updateById` | 真 PG | 影响 0 行或抛错 | ❌ **失败**（探针 PROBE6） |
| CASE-R06 | `selectPublicById` 抛异常 | 真 PG + 真 gRPC | code = `INTERNAL`（5001） | ❌ **未写**（当前得 `UNKNOWN`） |
| CASE-R07 | `incrPublicViewCount` 抛异常 | 同上 | code = `INTERNAL` | ❌ **未写** |
| CASE-R08 | 一条必然失败的审核消息（MinIO 不可达） | 真 Kafka（需 docker-compose） | 消息被**丢弃**，位点推进，无热循环 | ⏳ **未写**（= U-3） |
| CASE-R09 | `poll-interval-ms: 0` | 真上下文 | 实际调度间隔 1000ms | ❌ **未写** |
| CASE-R10 | 缺 `article_revision_immutable` 触发器的 schema | 真上下文 | 端口**从未**建立监听 | ❌ **未写** |
| CASE-R11 | `acceptSyncResult` **成功**路径（发布落库） | 真 PG | revision/指针/域事件/status 四者同时落库 | ✅ **已写并通过**（2026-10-24，3 条：四步联动 / 通知条件 / 标题双来源） |
| CASE-R12 | 并发：同一文章同时「降级」与「删除」 | 真 PG + 多线程 | 无死锁；指针版本单调 | ⏳ 沿用 U-2 |

## 9. Unresolved（§37，必填）

| ID | Question | Go Evidence | Java Evidence | Why Unknown | Required Confirmation |
|---|---|---|---|---|---|
| **U-3**（新） | `seekAfterError=true` + `FixedBackOff(0,0)` 在**真 Kafka** 上到底是"丢弃"还是"进程内无限重投"？ | `kq/config.go:24` `ForceCommit default=true`；消费点 `kq/queue.go:230-242` ⇒ 失败**也**走到 `:240` `CommitMessages` ⇒ **丢弃** | spring-kafka 4.0.7 源码：`SeekUtils.java:107-135` `doSeeks` 对**首条**（失败记录）先问 `recovery.recovered(...)`，返回 true ⇒ **不 seek** ⇒ 实际跳过；`FailedRecordProcessor.java:150-156` 的 javadoc 明确写 "records will be **redelivered on the next poll**" | ✅ **已关闭**（2026-10-29 第十二轮）—— **真 Kafka 实测**（`ArticleKafkaForceCommitGateTest`）：失败消息**只投递 1 次**（不进程内重投）＋ 位点被提交为**它自己的 offset+1** ⇒ **丢弃** ⇒ 与 Go `kq/queue.go:240` 同构。⇒ 详见本文件 **§12.3**（含机制归因的订正） |
| **U-4**（新） | S-3 的**依据**已被证伪，那"已改"还成立吗？ | 同上 | RAG 的 javadoc（`ArticleRpcApplication.java:143-154`）引用的是 `commit(consumer, container, offset+1)`，**该分支只在 `commitRecovered=true` 时执行**（`SeekUtils.java:229`），而 `FailedRecordProcessor.java:57` `commitRecovered` 默认 **false**，本模块**从未** `setCommitRecovered(true)` ⇒ 引用的那段代码**不在本模块的执行路径上** | ✅ **已关闭**（2026-10-29 第十二轮）—— **台账的「证伪」结论成立，但理由只覆盖了两个落点里的一个**：另一个落点（`FailedBatchProcessor.java:255-266`，由 `isSeekAfterError()` 把门）**同样不走**，理由是 `noRetries=true` ⇒ `remaining` 为空 ⇒ `:256` 的守卫为 false ⇒ ⇒ **真正的提交路径是容器侧的 RECORD ack**（`KafkaMessageListenerContainer.java:2897/2925/2936/3196-3199`）⇒ ⇒ **行为仍与 Go 同构**（真 Kafka 实测）⇒ **归因已订正**（`seekAfterError` **不是**承重项，承重的是 `FixedBackOff(0L,0L)`）。⇒ 详见本文件 **§12.3.2** |
| **U-5**（新） | `acceptSyncResult` 的**成功**路径在真 PG 上从未被验证 | `article_sync_result_consumer.go:74-153` | ~~现有真 PG 只测了 `domainOutboxFailureRollsBackEverything`（失败回滚）与两个 `getPublicArticle`~~ | ✅ **已关闭**（2026-10-24） | 补 CASE-R11 的 **3 条**真 PG 用例，覆盖四步联动（revision/指针/域事件/status）、通知条件（首次发布 vs 已发布过）、标题双来源；**并因此发现 `DRIFT-ARTICLE-RPC-009`**（详见本轮修复节） |
| **U-6**（新） | `updated_at` 该用哪种方式补（`@TableField(fill)` + `MetaObjectHandler` / mapper 里显式 SQL / DB 触发器）？ | GORM `autoUpdateTime` 由 ORM 承担 | **已选定并实施**：① `ArticleMetaObjectHandler`（新建 `@Component`，`updateFill` 里写 `updated_at`）；② 实体上补 `@TableField(fill = FieldFill.INSERT_UPDATE)` | ✅ **已关闭**（2026-10-24，**零新代码**） | 这是**实现选择**而非判定阻塞项，FIX-02 已作出选择并落地；配套的真 PG 用例（CASE-R04，`updated_at` 预置 2020-01-01 后改 title ⇒ 已推进）已在 FIX-02 中通过 ⇒ 选择 + 验证都已闭环 |
| **U-7**（新） | Micrometer 直方图的实际输出（`_seconds` 后缀 + 8 个桶边界 + `_count/_sum`）与 Go `HistogramVec` 是否逐桶一致？ | `metrics.go:41-49`（显式 8 桶） | ~~`ArticleRpcMetrics:131-139`（`serviceLevelObjectives` + `publishPercentileHistogram`）~~ | ✅ **已关闭**（2026-10-24） | 真 `PrometheusMeterRegistry` scrape 实测 ⇒ 发现 `publishPercentileHistogram` 与 SLO 桶取**并集** ⇒ 实际 **77** 个边界（Go 只有 **9**：8 + 固有的 `+Inf`）⇒ 已立 `DRIFT-ARTICLE-RPC-010` 并**修复**（去掉 `publishPercentileHistogram`） |
| **U-8**（新） | `@Scheduled` 首次执行的 `initialDelay` 默认值 | `article.go:94` ticker ⇒ 首次在 1s 后 | ~~未读 `ScheduledAnnotationBeanPostProcessor` 源码~~ | ✅ **已关闭**（2026-10-24，**零新代码**，用 FIX-05 已有的实测证据） | ① FIX-05 的变异 #4「去掉 `initialDelayString`」实测首触发在 **84ms** ⇒ **Spring 的默认值就是"立即执行"**；② 修复后由 `ArticleOutboxRelayScheduleTest#firstExecutionHappensAfterTheIntervalNotImmediately` 断言首次 ≥ 900ms（兜底间隔 1000ms）⇒ 与 Go ticker 同款 |
| **U-9**（新） | 并发（8 handler）下的 offset 提交/跳过语义两侧是否一致 | `kq/queue.go:219-246`（8 个 processor 各自 `CommitMessages`，**不保证连续**） | RECORD + 上面那套 handler | ✅ **已拆两半**（2026-10-29 第十二轮）：<br>**U-9a（offset 提交/跳过语义）= ✅ 闭合** —— Go 侧 `kafka-go@v0.4.47/reader.go:171-185` `offsetStash.merge` **取 max** ⇒ 已提交位点**单调不回退**；RAG 侧真 Kafka 实测每分区提交位点 = **最大已投递 offset + 1** 且**从不回退** ⇒ **两侧同构**（L4）<br>**U-9b（handler 并发模型）= 🟡 取证缺口** —— Go 是「1 个拉取者（`Consumers: 1`）→ 8 个 handler 抢同一个 channel」⇒ 同分区消息**可能**并发处理；RAG 是「8 个 consumer 各占自己的分区」⇒ 同分区**严格串行** ⇒ **RAG 更严**的模型差异 ⇒ **Go 侧未活体对拍** ⇒ **不写成「已对齐」** | 详见本文件 **§12.4**（含解除条件：① 登记为有意偏离并签字 ② 补 Go 侧活体探针） |
| **U-2**（沿用） | 锁顺序反转 | Go 侧两条路径都由 `RetractPublicationTx` 的 `FOR UPDATE` 串行化 | Java 已统一成 article → publication，但 `retractAndUpdate` 多了一次 `selectByIdForUpdate` 提前 return | ✅ **已关闭**（2026-10-24） | 补 **CASE-R12 并发真 PG 用例** + **锁序探针**（真 PG 上同时扣住两把锁，观测被阻塞的 SQL）+ **Go 侧探针**（pgx + 真 PG，两种锁序各跑一次）⇒ 结论已定：**Go 自身**存在锁序反转（探针实测 `40P01 deadlock detected`），RAG 统一为 `article → publication` ⇒ **无反转**。⇒ 差异落档到 [`docs/Intentional_Deviations/article/rpc.md`](../../Intentional_Deviations/article/rpc.md)（**SD-2026-10-24-01，已签字**），**不在本文重复记** |
| **U-4'**（沿用） | 双侧活体对拍 | Go 依赖 `etcd:2379`，本机拉不起 | — | ~~本轮仍 **0** 条~~ | ✅ **已销号**（2026-10-29 第十二轮）—— 前提已变（etcd 宿主口已映射）＋ 探针**落仓可复算** `tools/api-full-parity-probe/article/`（`run.sh` ＋ `mutate-parity.sh` ＋ `expected.txt` ＋ `README.md`）⇒ **3 个场景**（`list_articles` / `get_article_exists` / `get_article_missing`）**Go == RAG == 冻结期望值**逐字节一致 ＋ **两侧播种同一行**（`status=2 PUBLISHED` · id `9007199254740993`）⇒ **L4** ⇒ 详见本文件 **§12.2**（⚠️ 含**口径边界**：现钉的是**降级路径**，`user.rpc` 两侧都不可达 ⇒ 成功路径的完整字段语义**仍未**双侧验证 ⇒ **部分闭合**） |
| **U-5'**（沿用） | S-2 / S-3 / S-4 / S-7 新回归网**非假绿**未证 | — | 只有 S-1 做过变异验证 | AGENTS.md §3.3 | ✅ **已关闭**（2026-10-24 第十轮）：S-2 / S-3 / S-4 / S-7 **4/4 全部 MUT-RED** —— 逐条把修复改回去，用例都转红（详见本文件「第十轮」节的变异表）⇒ 五条回归网（S-1~S-4、S-7）**全部**证实非假绿 |

## 10. 最终判定（§39 的 18 项 checklist）

> ⚠️ **本表是 2026-10-23 第二轮的快照**（表内各行的「现状」只反映当轮）⇒ **当前态见 §12.8**；
> 表内第 7 行（External dependency failure）已按 2026-10-29 的实测**同步改口径**（`S-5` 改判为范围外）。

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边 public behavior 是否一致 | ❌ | DRIFT-001~004 |
| 2 | 正常路径是否一致 | ⚠️ | 写路径一致；**读路径丢两列**（001） |
| 3 | 边界输入是否一致 | ⚠️ | page/size、`secondary_tags` 空、`sort_by` 未知值均一致；`poll-interval-ms<=0` 不一致（006） |
| 4 | NotFound / Empty / Null 是否一致 | ❌ | jsonb 列的 null 语义在三个方法上不一致（001） |
| 5 | Error meaning 是否一致 | ❌ | 公开读 5001→1015（003）；`update_time` 陈旧（002） |
| 6 | Error propagation 是否一致 | ⚠️ | Kafka 侧 U-3；gRPC 侧四个 catch 分档一致 |
| 7 | External dependency failure 是否一致 | ✅ | DB / 对象存储 / Kafka 均已覆盖并有断言；**security 域 = 范围外**（`S-5` 改判 · judge.md §1.1 R0 ⇒ 落点在 R0 域 ⇒ 不计入本组件必相对齐项）｜⚠️ **能力差仍在**（Go 能审、RAG 不能）⇒ 计入域级聚合与矩阵 C，**不计入组件 R** |
| 8 | Cache hit / miss 是否一致 | — | **N/A**：两侧均无缓存层 |
| 9 | Transaction boundary 是否一致 | ✅ | 4 处边界与顺序逐条一致；真 PG 证明 AOP 代理生效 |
| 10 | State transition 是否一致 | ❌ | `updated_at` 永不推进（002） |
| 11 | Side effects 是否一致 | ⚠️ | 副作用**集合**一致；失败位点处置待 U-3 |
| 12 | Ordering 是否一致 | ✅ | 作者/公开两条排序规则各自对齐（S-2 已改 + 真 PG） |
| 13 | Retry 是否一致 | ✅ | 两侧均**零**进程内重试（`FixedBackOff(0,0)` ↔ kq 无重试循环） |
| 14 | Timeout scope 是否一致 | ✅ | 70s / 10s / 2s 三处均为 **operation 级**；SQL 超时可取消已接线 |
| 15 | Concurrency semantics 是否一致 | ⚠️ | 行锁集合一致；软删行可写（004）+ **锁顺序：U-2 已关闭（结论落 `../../Intentional_Deviations/article/rpc.md` 的 SD-2026-10-24-01，RAG 更安全 ⇒ 决定不对齐）** + offset 语义（U-9，仍 OPEN） |
| 16 | Authentication / Authorization 是否一致 | ✅ | 两侧 rpc 层都不做认证；操作人/作者校验三处逐条一致 |
| 17 | Serialization semantics 是否一致 | ❌ | 001（结果映射）；HTML 转义差异仅影响不经 PG 的四个字段（低） |
| 18 | Lifecycle 是否一致 | ⚠️ | 四部件同启同停 ✅；门禁时机不同（005）；首次 tick 时刻不同（008/U-8） |

### 判定：**SEMANTICALLY NOT EQUIVALENT**

- 本轮**新增 4 条 SEMANTIC MISMATCH**（001/002/003/004）+ **4 条 OBSERVABLE DIFFERENCE**（005/006/007/008）。
- 其中 **001 / 002 / 004 有真 PG 运行证据**（PROBE2~6），不是静态推断。
- 上一轮的 **7 MISMATCH + 6 OBSERVABLE 全部复核**：13 条「已改」中，**S-1/S-2/S-4/O-1/O-2/O-3/S-5b/S-6 的修复本身站得住**（本轮未发现它们被改回或改错）；**S-3 的结论方向站得住但依据被证伪**（见 §6-b 与 U-4）；**S-5 仍被阻塞**；**S-7 的修复站得住，但同一条 SQL 路径上还藏着 DRIFT-001**。
- ⚠️ 按 AGENTS.md §3.3：上表除 PROBE 外，**「有绿测试」一律不作为 `EQUIVALENT` 的证据**；本组件 S-2/S-3/S-4/S-7 的新回归网仍未做变异验证。

---

<!-- @fix 2026-10-23 修复轮（RPC-side 时间戳 + jsonb 读路径 + 公开列表 SQL） -->

## 2026-10-23 修复轮：已落地并经真 PG 验证

> 本节是**增量追加**，上文 DRIFT-ARTICLE-RPC-001~008 的原始记录**一字未删**。
> 每条给出：修了什么 / 根因到行 / 证据 / 守门测试。
> ⚠️ 判定仍是 **`SEMANTICALLY NOT EQUIVALENT`** —— 本轮只关掉下列 3 条，其余 005~008 与全部 U-* 未动。

### ✅ FIX-01 —— DRIFT-ARTICLE-RPC-001 已修（4 处，比台账原记多 1 处）

**根因**：`@Select` 覆盖了 MP 自带方法 ⇒ 走注解自动映射 ⇒ `List<String>` / `Map<String,String>`
在 TypeHandler 全局注册表里无条目 ⇒ **静默跳过该属性**（不报错、不告警）。

**修法**：挂 `@ResultMap`，与既有 `articleEntityResultMap` 同款。

| # | Mapper.方法 | 原状态 | 现状 |
|---|---|---|---|
| 1 | `ArticleMapper.selectAuthorView` | 缺 | `@ResultMap("articleEntityResultMap")` |
| 2 | `ArticleRevisionMapper.selectByRevisionId` | 缺 | 新增 `@Results(id="articleRevisionEntityResultMap")`（**声明处**） |
| 3 | `ArticleRevisionMapper.selectByArticleId` | 缺 | `@ResultMap("articleRevisionEntityResultMap")` |
| 4 | `ArticlePublicMapper.selectRevisionsForPublished` | 缺 | `@ResultMap("…ArticleRevisionMapper.articleRevisionEntityResultMap")`（跨 Mapper FQN 引用） |

> ⚠️ **台账原记 3 处，实为 4 处** —— 漏的是 `ArticleRevisionMapper.selectByArticleId`。
> 第 2 项要新增 `@Results` 声明，是因为 `ArticleRevisionMapper` 此前**一个 resultMap 都没有**。

**证据**：`ArticleJsonbResultMapWiringTest`（真 PG + Testcontainers，8/8 绿）。
刻意用**非空** jsonb 做种子（`["alpha","beta"]` / `{"reco_sync_state":"pending",…}`）——
null / 空数组在「没映射上」和「真值是空」两种情况下读起来一样，只有非空值能区分。

**可证伪性**：删掉任一 `@ResultMap` 再跑该类，**应当立刻红**。

### ✅ FIX-02 —— DRIFT-ARTICLE-RPC-002 已修（`updated_at` 推进）

**根因（两级，都是静默）**：

1. `ArticleEntity.updatedAt` 缺 `@TableField(fill = INSERT_UPDATE)`，而 `updateById` 默认
   `updateStrategy = NOT_NULL` ⇒ 把实体里**从库里读回来的陈旧值原样写回**。
2. **全仓在修复前零个 `MetaObjectHandler` 实现**（实测）⇒ 只加注解而没有 handler，
   `fill` 同样**静默空转**。

**修法**：`ArticleMetaObjectHandler`（新建，`@Component`，`updateFill` 里
`setFieldValByName("updatedAt", OffsetDateTime.now(ZoneOffset.UTC), metaObject)`）
+ 实体上补 `@TableField(fill = FieldFill.INSERT_UPDATE)`。

**Go 出处**：声明点 `service/article/rpc/internal/model/article.go:31`
（`gorm:"autoUpdateTime"`）；消费点 `gorm@v1.31.1/callbacks/update.go:238-254`
（`Updates(map)` 路径）与 `:274-281`（`Updates(struct)` 路径）。

> ⚠️ **2026-10-24 修正 gorm 版本号**：本条原写 `gorm@v1.31.2/...` ——
> 但真源仓库 `go.mod:28` 写的是 **`gorm.io/gorm v1.31.1`**（`go.sum:428` 佐证，**无 replace 指令**），
> ⇒ 引用的版本与行号都是 v1.31.2 的（v1.31.2 是 `:243-259` / `:279-281`）。
> ⇒ 本条已改为 v1.31.1 的真实行号。
> （⚠️ 同类错误在 `like` / `users-admin` / `users-user` 三个域的台账里也有，见「跨域待办」。）

> 🔴 **为什么不能用 `strictUpdateFill`（这是本轮最反直觉的一点）**
> `mybatis-plus-core` 3.5.17 的 `MetaObjectHandler.strictFillStrategy` 消费点是
> `if (metaObject.getValue(fieldName) == null)` —— **只在当前值为 null 时才填**。
> 而本域 8 处写路径全是**读-改-写**（`ArticleAtomicService:139/301/496/516`、
> `ArticleDomainService:348/360`、`ArticleSyncResultConsumer:207`、`ArticleReviewConsumer:285`，
> 全部 `updateById(entity)`），实体里的 `updatedAt` **永远非 null** ⇒ strict 永远跳过。
> 这与 GORM 的 `value[field.Name] == nil` 看着同款，但**语义不同**：
> GORM 问的是「本次调用有没有赋值」，MP 问的是「字段现在是不是空」。
> Java 侧那个陈旧值**不是调用方的意图**，是上一轮写下的残留。
> 实测佐证：`grep -rn '"updated_at"|"UpdatedAt"' service/article --include=*.go`
> （排除 model 声明与测试）**零命中** ⇒ Go 侧无显式赋值路径 ⇒ **无条件覆盖零差异**。

**`SkipHooks` 等价性（GORM `!stmt.SkipHooks` 那一半）**：
Go 侧仅两处 `UpdateColumn`（`model/article.go:123`、`model/article_public.go:159`，
均为 `view_count + 1`），会跳过 autoUpdateTime。
Java 侧对应两处是**原生 `@Update` 注解**（`ArticleMapper#incrViewCount`、
`ArticlePublicMapper` 同款），完全绕开 MP ⇒ handler 天然不触发。✅ 已对齐，
并由 `ArticleTransactionWiringTest#viewCountIncrementDoesNotTouchUpdatedAt` 单独锁死
（**只测 FIX-02 正向的话，这个改动不会让任何测试变红**）。

**证据**：`ArticleTransactionWiringTest` 新增 S-3/S-4 两条（真 PG），
断言形状被缺陷决定：**必须先读出实体（携陈旧值）再 `updateById` 回去**；
直接 new 一个 `updatedAt=null` 的实体测不出问题，因为 `NOT_NULL` 会跳过该列，
恰好「看起来是好的」。
另有 `ArticleSpringContextWiringTest#metaObjectHandlerIsRegisteredAndConsumedByMybatisPlus`
做**两级装配断言**（容器有 Bean + 从**真实的** `SqlSessionFactory` 的 `GlobalConfig` 里读回来）——
因为 MP 的接住点是 `MybatisPlusAutoConfiguration:216`
的 `getBeanThen(MetaObjectHandler.class, globalConfig::setMetaObjectHandler)`，
而 `getBeanThen`（同文件 `:233-237`）**找不到就静默跳过**。

### ✅ FIX-03 —— 新发现：`ArticlePublicMapper` 公开列表**双重损坏**（100% 报错）

> 🔴 **本条不在原台账里，是修 FIX-01 时被新写的对照组测试撞出来的。**
> 原先无人发现，因为它**被更早的一道错误挡在了后面**。

| 层 | 缺陷 | 症状 |
|---|---|---|
| 第 1 层 | `<if test="secondaryTag …">` 判据名 ≠ `@Param("secondaryTagJson")` | `BindingException: Parameter 'secondaryTag' not found`（**解析期**抛，SQL 还没生成） |
| 第 2 层 | 基础 SQL 已含 `WHERE a.deleted_at IS NULL AND <可见谓词>`，却又套了一层 MyBatis `<where>` 元素 | 生成**两个 WHERE 关键字** ⇒ `syntax error at or near "WHERE"`（PG 拒收） |

**影响面**：`ArticlePublicDomainService:185/187` 同时调用 `countPublic` 与 `selectPublicPage`
⇒ **公开文章列表接口 100% 报错**（带任一过滤条件时）。
第 1 层与 `ArticleMapper.selectAuthorView` 曾有的缺陷**是同一个** —— 那次修了，这次漏了。

**修法**：

1. 两处判据名改回 `secondaryTagJson`；
2. **移除 `<where>` 元素**。`<where>` 的职责是「本段非空时补一个 WHERE 并剥掉开头的 AND」，
   而这里基础 WHERE **无条件存在** ⇒ 直接让三个 `<if>` 各以 ` AND (...)` 开头顺序拼接。
   也不存在「空内容剥前缀」的问题。`countPublic` 与 `selectPublicPage` **必须同步改**，
   否则 total 与页内容口径会不一致。

**Go 对照**：`internal/model/article_public.go:62-88` 是链式 `query.Where(...)`，
由 GORM 以 AND 组合 —— 与顺序拼接同构。

**证据**：`ArticleJsonbResultMapWiringTest#publicSecondaryTagFilterDoesNotThrowBindingException`
（真 PG），**同时覆盖可见谓词的两个分支**：

- **published 分支**（有 `article_publication` 行）：标签匹配 **`revision.secondary_tags`**
  ——「读者看到的分类是批准时的值」；
- **legacy 分支**（无 publication 行 + `status=2`）：标签匹配 **`article.secondary_tags`**。

> ⚠️ 两支各走各的列，**不可互串**：拿 article 的标签去查 published 分支必然落空。
> 该分支由 javadoc 明确要求保留（「删掉它会让全部历史 PUBLISHED 文章从公开列表消失」，
> Go 测试 `article_public_test.go` 的 "legacy" 行专测此点），因此守门测试**必须两支都断**。

### ⚠️ 顺带修正的一条错误断言（AGENTS.md §3.3.2 的教科书案例）

`AdminSemanticDriftRegressionTest`（admin-rpc）里
`resetUserPassword_usesConfiguredDefaultNotSeederConstant` 曾把 UPDATE 的 SET 子句
**写死为不含 `update_time`** —— 那是**一条把缺陷锁死的绿测试**。
按 §3.3.2「修复漂移时先改/删错误断言，再改实现」，**先改断言**（补上 `update_time`），
再改实现；否则会先撞上「测试红了」而误以为修复方向错了。

### 判定变化

| 项 | 修复前 | 修复后 |
|---|---|---|
| jsonb 结果映射（001） | ❌ SEMANTIC MISMATCH | ✅ 已修 + 真 PG 守门 |
| `updated_at` 推进（002） | ❌ SEMANTIC MISMATCH | ✅ 已修 + 真 PG 守门（含 SkipHooks 反向断言） |
| 公开列表 SQL | **未记录** | ✅ 已修 + 真 PG 守门（两分支） |
| 其余 005~008 + 全部 U-* | 未动 | 未动 |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

article-rpc 当前：`mvn -o -pl service/article/article-rpc -am test` ⇒
**105 tests / 0 failures / 0 errors**，BUILD SUCCESS。

---

<!-- @fix 2026-10-23 第二轮：DRIFT-ARTICLE-RPC-004 复核结论 = 撤销，改为「台账描述有误」 -->

## 2026-10-23 第二轮：DRIFT-ARTICLE-RPC-004 **不修** —— 复核发现台账的 Go 侧描述有误

> 上一轮列出的 4 条 HIGH 里，本条是唯一<b>没有照台账修</b>的。
> 原因：复核 Go 侧时发现台账写的「Go 会报错、RAG 静默修改」**不成立**，
> **照台账修反而会制造一条新漂移**。此处保留完整复核证据，供下个 agent 不必重走一遍。

### 台账原文的断言（❌ 经复核不成立）

> 「GORM `Save` 在 0 行时会退化成 `Create` ⇒ **撞主键 ⇒ 整个事务失败并报错**」

### 复核结果（已验到消费点）

**① 软删过滤确实加在 UPDATE 上**（这部分台账是对的）：
`gorm@v1.31.1/soft_delete.go:76-96` `SoftDeleteQueryClause.ModifyStatement`
在 `!stmt.Unscoped` 时追加 `WHERE deleted_at = <ZeroValue>`（`:91-93`），
经 `:98-100 UpdateClauses` 挂到 UPDATE。
（⚠️ 2026-10-24 修正版本号：原写 `gorm@v1.31.2` —— 真源仓库 `go.mod:28` 是 **`v1.31.1`**；
本条的行号 `76-96` 在两个版本里**恰好相同**，只有版本号错。）

**② 0 行之后的回落是 `ON CONFLICT DO UPDATE`，不是报错**：
`finisher_api.go:109-110`（v1.31.1）
```go
updateTx := tx.callbacks.Update().Execute(...)
if updateTx.Error == nil && updateTx.RowsAffected == 0 && !updateTx.DryRun && !selectedUpdate {
    return tx.Session(&Session{SkipHooks: true}).Clauses(clause.OnConflict{UpdateAll: true}).Create(value)
}
```
⇒ 行**物理存在**（软删只是加了列）⇒ 冲突 ⇒ `DO UPDATE` ⇒ **不报错**。

**③ `deleted_at` 会被写回，且写回的是同一个非零值**：
`callbacks/create.go:366-402` 逐列判定跳过的条件是
`PrimaryKey` 或（`HasDefaultValue && DefaultValueInterface==nil && DefaultValue != "NULL"`）或 `AutoCreateTime > 0`。
`DeletedAt` 在 `internal/model/article.go:32` 只标了 `gorm:"index"`，**无 default** ⇒ `HasDefaultValue=false`
⇒ <b>不被跳过</b>；且它不是 AutoCreateTime/AutoUpdateTime 字段 ⇒ 进入 `DO UPDATE SET deleted_at = excluded.deleted_at`。
而 `excluded.deleted_at` 来自 `model/article.go:92` 的 `FindOneUnscoped`（**Unscoped 读，能读到 deleted_at**）⇒ 同一个非零值。

### 两边净行为的对照（修正后）

| | Go `Save` | RAG `updateById` |
|---|---|---|
| UPDATE 带 `deleted_at IS NULL` | ✅ | ❌ |
| 软删行影响行数 | 0（走回落） | 1 |
| 其他列是否被覆盖 | **是**（`DO UPDATE SET`） | **是** |
| `deleted_at` 最终值 | 原非零值（不变） | 不触碰（不变） |
| **是否报错** | **否** | **否** |

⇒ **两侧在可观察层面基本一致**：都静默覆盖软删行的其他列、都不复活、都不报错。

### 为什么不修

若按台账给 `updateById` 加 `deleted_at IS NULL`：
MP 会返回「影响 0 行」，而调用方<b>不检查 RowsAffected</b>（8 处调用点均未检查）
⇒ 变成<b>完全不写</b>，而 Go 是<b>写了</b> ⇒ **这会把一个不存在的缺陷修成一个真实的新漂移**。

### ⚠️ 相邻差异（`Selects="*"` vs `NOT_NULL`）—— **2026-10-24 已展开：存在，但不可观察 ⇒ 不修**

> **当前状态（2026-10-24 第十轮）**：🔴 **未展开 → 🟢 已展开并关闭**。
> 结论：**差异真实存在，但 API 层面不可观察** ⇒ **不立新条目、不改代码**。
> ⇒ 完整取证（Go 侧探针 + RAG 侧真 PG 对拍 + 4/4 变异验证）⇒ 本文件「2026-10-24 第十轮」节。
> ⇒ 新增守门用例 `ArticleNotNullUpdateStrategyWiringTest`（真 PG，5 条）。

**原文的推理（2026-10-24 复核后判定：结论对、但推理错）**：

> Go 的 `Save` 走 `finisher_api.go` 的 `Selects = append(Selects, "*")`
> ——「update 时使用<b>全部</b>字段，含零值字段」；
> MP `updateById` 默认 `updateStrategy = NOT_NULL`，<b>只写非 null 字段</b>。
> ⇒ 一个「把某列置为零值」的更新，两侧结果不同。

⚠️ **错在「零值」这个词上**（真 PG 实测，见第十轮节）：
MP 的 `NOT_NULL` 跳过的只是 **null**；
**零值（`""` / `0` / `false`）照样会被写** ⇒ **两侧在零值上本来就一致**。
真正可能不同的是 **NULL** —— 而 Go 的 `Article` 模型**全是值类型（无指针）**，
`Save` 写的是**零值**（`""` / `0`）⇒ **Go 侧对 `article` 表从来不会写 SQL NULL**。

⇒ 真正剩下的差异面只有两个可空对象列（`secondary_tags` / `ext_info`），
且两侧都在写前 ensure 非空 ⇒ **存储表示可能差一层（SQL NULL vs jsonb `'null'`），
但读回的值与过滤结果完全一致 ⇒ 不可观察**。

### 台账维护教训

本条台账写作时，Go 侧标注的是「**未实跑**（归入 U-4）」——
即**结论本身是推断**，却被写成了 `SEMANTIC MISMATCH` 并进入 §0 计数。
⇒ 与 AGENTS.md §3.1「不要拿未验证的结论当基准」同源：
**推断级证据的条目必须在标题里标注**，否则下一个 agent 会照着它去「修」，
而照着错误结论修比不修更糟。

---

<!-- @fix 2026-10-24 · 由 align/article 工作树产出 · 基线 commit 8422116 -->

## 2026-10-24 修复轮：`DRIFT-ARTICLE-RPC-003` 已修（公开读 DB 失败 ⇒ INTERNAL）

> **本节是增量追加**，上文 §0~§10 与「修复轮 / 第二轮」各节**一字未删**。
> **核对日期 = 2026-10-24。** 基线 commit `8422116`（`align/article` 工作树）。
> **证据强度：真 PG（Testcontainers postgres:16-alpine + 真实 Spring 上下文 + 真实 MyBatis）**
> + **变异验证**（2/2 转红）—— 与 2026-10-23 那轮「仅 S-1 做过变异验证」相比，本条**证据更硬**。

### ✅ FIX-04 —— `DRIFT-ARTICLE-RPC-003` 已修

**Go 出处（三段，逐行读到消费点）**

```
① model/article_public.go:34-57   FindPublicOne —— 三处 DB 失败返回**同一种** error
     if err := publicArticleQuery(...).First(&article).Error; err != nil { return PublicArticle{}, err }  // 文章行
     pointer, err := m.Publication(ctx, id); if err != nil { return PublicArticle{}, err }                 // 指针行
     revision, err := m.RevisionByID(ctx, id, pointer.CurrentRevision); if err != nil { return …, err }    // revision

② internal/logic/get_article_logic.go:129-135
     if err == gorm.ErrRecordNotFound { return nil, status.Error(codes.NotFound, "article not published") }
     if err != nil { …; return nil, status.Error(codes.Internal, "read publication failed") }    // ← **INTERNAL**

③ internal/logic/get_article_logic.go:157-163
     increased, err := l.svcCtx.ArticleRepo.IncrPublicViewCount(l.ctx, in.ArticleId)
     if err != nil { …; return nil, status.Error(codes.Internal, "increment public view failed") }  // ← **INTERNAL**
     if !increased { return nil, status.Error(codes.NotFound, "article no longer published") }      // ← **NOT_FOUND**（不是 INTERNAL）
```

⚠️ **③ 的两个分支码不同**：**失败**⇒ INTERNAL（5001）；**影响 0 行**⇒ NOT_FOUND（1002，撤回赢了竞态）⇒ **不可合并**。

**修法**

RAG 侧把 Go 的**一次** `FindPublicOne` 拆成了**三次** mapper 调用
（`selectPublicById` / `selectPublication` / `selectByRevisionId`），
所以必须**逐个**把 DB 失败翻译成 `INTERNAL`：

- 新增私有方法 `readPublication(Supplier<T>)` —— 任一 DB 失败 ⇒ `ArticleExceptions.readPublicationFailed()`（INTERNAL）；
- 新增私有方法 `incrementPublicView(String)` —— 失败 ⇒ `ArticleExceptions.incrementPublicViewFailed()`（INTERNAL）。

> ⭐ **强证据（额外）**：`ArticleExceptions.incrementPublicViewFailed()` 在本轮之前
> **全仓零调用点** —— 这个工厂方法**本来就是为此准备的**，它的存在本身就是
> 「这条 INTERNAL 曾被设计出来、但接线漏了」的证据（与上一轮对 `readPublicationFailed()` 的判断同型）。

> ⚠️ **异常类型的选择**：`readPublication` 捕获 `RuntimeException`（不是只捕 `DataAccessException`），
> 因为 Go 侧 `FindPublicOne` 是**仓储层调用**，任何失败都是「读失败」；
> 但**显式放行 `ArticleExceptions.Rpc`**（领域异常不该被吞成 INTERNAL）。
> `incrementPublicView` 同理（它内部只可能是 DB 失败）。

**证据：真 PG（不是 Mockito）**

| 用例 | 手法 | 断言 |
|---|---|---|
| `publicReadDbFailureIsInternalNotUnknown` | `ALTER TABLE article_publication RENAME TO …` ⇒ **真 SQL 错误 42P01** ⇒ 真 `DataAccessException` | `Rpc` 的 code = `INTERNAL`、message = `"read publication failed"`（逐字） |
| `publicViewIncrementDbFailureIsInternalNotUnknown` | `ALTER TABLE article ADD CONSTRAINT chk_force_view_fail CHECK (view_count < 0) NOT VALID` ⇒ **只有 UPDATE 失败、SELECT 仍成功** ⇒ 精确命中计数那条路径 | code = `INTERNAL`、message = `"increment public view failed"`（逐字） |

> ⚠️ **为什么不用 Mockito 把 mapper 打桩**：那样测不到「**真 SQL 失败**会不会被翻译成 INTERNAL」。
> 本组用例让**真 PG 真的报错**，走真 MyBatis + 真异常翻译链
> （`MyBatisExceptionTranslator` ⇒ `DataAccessException` ⇒ 本层 catch ⇒ `ArticleExceptions.Rpc`）。
> ⚠️ CHECK 约束必须带 `NOT VALID`：既有行 `view_count=0` 不满足 `view_count<0`，
> 不加的话 **ALTER 自己就会失败**（这是个很容易踩的 PG 细节）。
> ⚠️ 两个用例都用 `try/finally` 恢复 schema（共享容器 ⇒ 不恢复会污染后续用例）。

**变异验证**

| # | 变异内容 | 结果 |
|---|---|---|
| 1 | 去掉 `readPublication` 包装（直接调 `selectPublicById`） | ✅ **转红**（抛的不是 `ArticleExceptions.Rpc`，而是裸 `DataAccessException` ⇒ 会落 `UNKNOWN` ⇒ 1015） |
| 2 | 去掉 `incrementPublicView` 包装 | ✅ **转红**（同上） |

⇒ **2/2 转红** ⇒ 守门网**非假绿**。

**测试计数**：`ArticleTransactionWiringTest` **17 条全绿**（原 15），真 PG 容器 `skipped=0`。
⇒ article-rpc 全模块 **107 tests / 0 failures / 0 errors / 0 skipped**。

### 判定变化

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-RPC-003` 公开读 5001→1015 | ❌ SEMANTIC MISMATCH | ✅ 已修 + 真 PG + 变异验证 |
| `DRIFT-005` / `006` / `007` / `008` | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| 全部 `U-*`（U-2 / U-3 / U-4 / U-5 / U-6 / U-7 / U-8 / U-9 / U-4' / U-5'） | 🔴 OPEN | 🔴 **OPEN** |
| S-5（security 域） | ⛔ 阻塞 | ⛔ **仍阻塞**（security 域不在本项目范围内） |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：只关掉 1 条；`005~008` 与 10 条 `U-*` 仍在，
且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）⇒ 本条的 ✅ 读作「**在真 PG 证据下一致**」，不是「已闭环」。

### ✅ FIX-05 —— `DRIFT-ARTICLE-RPC-006` + `DRIFT-ARTICLE-RPC-008` 已修（outbox 中继的调度语义）

两条**同源**（都在 `@Scheduled` 的属性上），一起修。

**Go 出处（两段，缺一不可）**

```
// ① 间隔兜底 —— service/article/rpc/article.go:62-65
relayInterval := time.Duration(c.ArticleSyncOutbox.PollIntervalMs) * time.Millisecond
if relayInterval <= 0 { relayInterval = time.Second }

// ② 首次触发时机 —— article.go:94,101
ticker := time.NewTicker(s.interval)     // ← 首次 tick 在 interval **之后**
for { select { case <-ticker.C: …SendPending(…) } }
```

**修法**

| 漂移 | 修前 | 修后 |
|---|---|---|
| **006** 兜底没接上 | `@Scheduled(fixedRateString = "${…:1000}")` **直接读原值**；`effectiveIntervalMs()` 兜底方法**零调用点** | 改为 **SpEL**：`#{T(…ArticleSyncOutboxRelay).normalizeIntervalMs(${…:1000})}` |
| **008** 首次时机 | 无 `initialDelay` ⇒ Spring 默认**立即**执行首次 | 显式给 `initialDelayString` **同一个**归一化后的间隔 |

⚠️ **为什么 006 不能直接用占位符兜底**：`@Scheduled` 的 fixedRate **不接受非正数**
⇒ 配置写 0 会让 Spring 在**装配期**就抛异常（容器起不来），
而 Go 侧是「配 0 仍每秒」。
⇒ 只能用 **SpEL**（Spring 先解析 `${…}`，再求值 `#{…}` —— `EmbeddedValueResolver` 的顺序）
才能让兜底真正生效。
⚠️ `normalizeIntervalMs` 必须是 **`static`**：调度属性在**容器装配期**求值，那时拿不到实例注入，
只能用 SpEL 的 `T(...)` 类型引用。

**证据：真容器（`@SpringJUnitConfig` + `@EnableScheduling`）+ 真实时钟**

| 用例 | 手法 | 断言 |
|---|---|---|
| `zeroIntervalStillBootsAndFallsBackToOneSecond` | `@TestPropertySource(properties="…poll-interval-ms=0")` ⇒ **配置真的写 0** | ① 容器**起得来**（修之前是装配期报错）；② `normalizeIntervalMs(0/-1)=1000`、`(500)=500`（正数不得被改写） |
| `firstExecutionHappensAfterTheIntervalNotImmediately` | mock 掉 `fetchPending` 并**记录首次调用的时刻** | 首次进入中继循环的时刻 **≥ 900ms**（兜底间隔 1000ms，留 10% 抖动余量）⇒ 证明 `initialDelay` 生效 |

⚠️ **为什么不需要真 PG / 真 Kafka**：被测的是**调度器**，不是数据面 ——
`outboxMapper.fetchPending` 与 `kafkaTemplate.send` 都 mock，中继在「捞不到事件」处即返回，
**不碰数据库、不发消息**。

**变异验证**

| # | 变异内容 | 结果 |
|---|---|---|
| 3 | `@Scheduled` 改回裸占位符（去掉 SpEL 兜底） | ✅ **转红**（容器装配期报错 ⇒ 用例 ERROR） |
| 4 | 去掉 `initialDelayString` | ✅ **转红**（实测首次触发在 **84ms**，即启动即执行） |

⇒ **2/2 转红** ⇒ 守门网**非假绿**。

> 🔴 **本轮的一条真实假绿（值得单独记）**：
> 第一版测试**只把 `ArticleRpcProperties` 这个 bean 的 `pollIntervalMs` 设成 0**，
> 而 `@Scheduled` 的占位符读的是 **Environment** —— Environment 里**根本没有这个键**
> ⇒ 占位符走默认值 `:1000` ⇒ 把实现改回裸占位符也**不会**报错
> ⇒ **变异验证直接判成「假绿」**。
> ⇒ 教训：**「配置写 0」这个场景必须落在 Environment 上**；
> 「改的是 bean、读的是 Environment」是最隐蔽的一种假绿。
> ⇒ 详见 [`docs/Pitfalls/article/rpc.md`](../../Pitfalls/article/rpc.md) 的新增条目。

### 判定变化（FIX-05）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-RPC-006` 间隔兜底没接上 | ❌ OBSERVABLE DIFFERENCE（LOW） | ✅ 已修 + 真容器 + 变异验证 |
| `DRIFT-ARTICLE-RPC-008` 首次触发时机 | ❌ OBSERVABLE DIFFERENCE（LOW） | ✅ 已修 + 真容器 + 变异验证 |
| `DRIFT-ARTICLE-RPC-005` schema 门禁时机 | ❌ OBSERVABLE DIFFERENCE（MEDIUM） | 🔴 **OPEN**（需把门禁挪到 gRPC server 监听**之前**，非本轮范围）〔**本行为历史快照；现状见 §7 条目 = ✅ 已修（2026-10-24 第四轮 · FIX-07）**〕 |
| `DRIFT-ARTICLE-RPC-007` 每通道独立 Kafka brokers | ❌ OBSERVABLE DIFFERENCE（LOW） | 🔴 **OPEN**（配置面误导，今天无行为差异）〔**本行为历史快照；现状见第九轮 + §7 条目**〕 |
| 全部 `U-*` | 🔴 OPEN | 🔴 **OPEN** |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

---

<!-- @fix 2026-10-24 第三轮 · 由 align/article 工作树产出 · 基线 commit 91da528 -->

## 2026-10-24 第三轮：U-5 关闭 ⇒ **顺带发现并修掉 `DRIFT-ARTICLE-RPC-009`**

> 本轮的起因是台账里的一条 **UNRESOLVED**：`U-5` —— `acceptSyncResult` 的**成功路径**
> 在真 PG 上从未被验证过（此前只测了失败回滚）。
> ⇒ **补上这条网，第一件事就是它转红** —— 而且红的不是"缺一条用例"，
> 而是**一个此前被台账判成"两者必相等"的真实漂移**。

### ✅ FIX-06 —— `U-5` 关闭（成功路径真 PG）+ `DRIFT-ARTICLE-RPC-009` 已修（通知标题取当前行）

#### 本轮补的 3 条真 PG 用例（`ArticleTransactionWiringTest`，真 PostgreSQL 容器）

| 用例 | 钉住什么 | Go 出处 |
|---|---|---|
| `acceptSyncResultSuccessPathPublishesEverything` | **四步联动**：revision / 指针 / 域事件 / 文章状态**同时**落库；正文与标题取**冻结 payload**；`pointer_version` 从 0+1=1 起；`state=published`；`aggregate_version = pointerVersion` | `:109-152` |
| `acceptSyncResultNotifiesOnlyOnFirstPublish` | 通知**条件**：`reason=create` 且 `published_once` 为空 ⇒ 返回 `PublishOutcome`；`published_once` 已非空 ⇒ **不再通知**，但**发布照样发生** | `:146-148` |
| `acceptSyncResultUsesFrozenTitleForRevisionButCurrentRowTitleForNotification` | **标题的两个源**：revision 取冻结 payload，通知取**当前行** | `:115` vs `:147` |

⚠️ 补网过程中**先撞上一个自己的错**：
`acceptSyncResultSuccessPathPublishesEverything` 里我把域事件的 `event_type` 写成了裸 `"published"`，
真跑才发现 Go 侧是 `"community.article." + operation`（`community_article_event.go:91`）
⇒ 裸 `"published"` 只是它的**入参**（`:131`）。
⇒ 已改断言为 `community.article.published` + 补一条 `event_id` 断言（`:65`）。
⚠️ **如果当初没写这条真 PG 用例，这个错误会一直留在台账的"等价"结论里。**

#### 修复本身（1 行 + 注释）

```java
// 修复前
new PublishOutcome(article.getAuthorId(), approved.title, true)     // ← 冻结 payload
// 修复后
new PublishOutcome(article.getAuthorId(), article.getTitle(), true)   // ← 当前行
```

**Go 出处**：`article_sync_result_consumer.go:147`
`notifyAuthor, notifyTitle = article.AuthorID, article.Title`
（`article` = 本次事务里 `FOR UPDATE` 锁住的那一行）。

⚠️ **只改通知这一处**：revision 的标题**本来就该**取冻结值（`:115` `Title: approved.Title`）
⇒ Go 侧这两个标题**本就来自不同的源**，混用即错。

#### 证据

- **真 PG**：`ArticleTransactionWiringTest` **20 tests / 0 failures / 0 errors / 0 skipped**
  （原 17 ⇒ 新增 3；`skipped=0` ⇒ 没有一条被 Docker 条件跳过）。
- **模块级**：`article-rpc` **112 tests / 0 failures / 0 errors / 0 skipped**（原 109 ⇒ +3）；
  连同 `article-api` 67 + `article-common` 15 ⇒ **article 域合计 194 / 0 / 0 / 0**（`BUILD SUCCESS`）。
- **变异验证（三态判定）**：`MUT-RED`
  —— 把 `article.getTitle()` 改回 `approved.title` ⇒ 用例转红，
  报错为 `expected: <作者改过的标题> but was: <冻结时的标题>`
  ⇒ **回归网非假绿**，且它**恰好**能抓住这条漂移。
- ⚠️ **修复前**的那次红（`expected: <作者改过的标题> but was: <冻结时的标题>`）
  与变异红是**同一个断言、同一条消息** ⇒ 测试确实在测这件事，而不是碰巧红。

#### 判定变化（FIX-06）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `U-5` 成功路径真 PG 验证 | 🔴 OPEN（无一条真 PG 用例） | ✅ **已关闭**（3 条用例 + `skipped=0` + 变异验证） |
| `DRIFT-ARTICLE-RPC-009` 通知标题来源 | 🔴 **未被登记**（判成 IMPLEMENTATION DIFFERENCE，论证已证伪） | ✅ **已修** + 真 PG + 变异验证 |
| `O-4` 的等价性论证 | ⚠️ 论证不成立（把判据约束不到的字段当成了约束得到的） | ✅ 已撤销并更正 |
| `DRIFT-005` / `007` | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `U-2` / `U-3` / `U-4` / `U-6` / `U-7` / `U-8` / `U-9` / `U-4'` / `U-5'` | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| S-5（security 域） | ⛔ 阻塞 | ⛔ **仍阻塞**（security 域不在本项目范围内） |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：只关掉 1 条 `U-*`、修掉 1 条新漂移；
`DRIFT-005`/`007` 与 9 条 `U-*` 仍在，且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）
⇒ 本条的 ✅ 读作「**在真 PG 证据下一致**」，不是「已闭环」。

#### 📌 本轮的一条**台账维护教训**（已记入 Pitfalls）

台账原来给这条写的是「IMPLEMENTATION DIFFERENCE：在双重判据约束下两者必相等」。
**这个论证是错的**：双重判据（`last_sync_event_id` + `last_sync_version_ms`）约束的是
**event_id 与 version**，**与标题无关**；而改标题**不立刻**推进 `last_sync_event_id`
（`enqueueReview` 只推 Kafka）。
⇒ **「某个判据 ⇒ 两者必相等」这种句式，在写进台账前必须问：这个判据到底约束了那个字段吗？**
⇒ 与 AGENTS.md §3.1「不要拿未验证的结论当基准」同源，且更隐蔽：
**它长得像一条严谨的论证，且写进了台账，会让下一个 agent 以为"已经证明等价，不用管"。**

---

<!-- @fix 2026-10-24 第四轮 · 由 align/article 工作树产出 · 基线 commit 59ee046 -->

## 2026-10-24 第四轮：`DRIFT-ARTICLE-RPC-005` 已修（schema 门禁移到 server 启动**之前**）

### ✅ FIX-07 —— `DRIFT-ARTICLE-RPC-005` 已修（启动期门禁的时机）

#### Go 出处（逐行，消费点）

```
article.go:41   u := model.NewArticleRepo(c)          ← InitDB 里校验 article_revision_immutable
                                                     ← 失败 ⇒ panic（db.go:61-71）
article.go:44   s := zrpc.MustNewServer(...)           ← gRPC server 在这之后才被创建
article.go:57   serviceGroup.Add(kq.MustNewQueue(...))  ← Kafka consumer 同理
article.go:78   serviceGroup.Start()                  ← 真正开始收流量
```

⇒ Go：**门禁在 server 被创建之前** ⇒ 缺触发器时进程**从不收流量**。

#### Spring 侧的等价位置（spring-framework **7.0.9**，逐行读到消费点）

```
AbstractApplicationContext.refresh()
  :621  finishBeanFactoryInitialization(beanFactory)
         └─ DefaultListableBeanFactory.preInstantiateSingletons()
              ├─ 实例化全部单例（Flyway 的 FlywayMigrationInitializer 在此执行 afterPropertiesSet）
              └─ :1144-1147  SmartInitializingSingleton.afterSingletonsInstantiated()   ← 门禁放这里
  :624  finishRefresh()
         └─ :1013  getLifecycleProcessor().onRefresh()   ← 启动全部 SmartLifecycle
                   ├─ devh  GrpcServerLifecycle    → localServer.start() ⇒ **端口绑定**
                   └─ Kafka KafkaListenerEndpointRegistry → 消费者开始拉消息
```

⇒ `SmartInitializingSingleton` 正好落在「单例已就绪（含 Flyway）」与「任何 `SmartLifecycle` 被启动」**之间**。

#### 修复

```java
// 修复前
@EventListener(ApplicationReadyEvent.class)   // ← refresh() 之后才发布 ⇒ 端口已绑
public void verifySchema() { ... }

// 修复后
public class ArticleSchemaGate implements SmartInitializingSingleton {
    @Override
    public void afterSingletonsInstantiated() { verifySchema(); }
}
```

⚠️ **为什么不能更早**：`BeanFactoryPostProcessor` 阶段连 DataSource 都还没建好、Flyway 更没跑
⇒ 那时校验必然误报"缺表"。
⚠️ **为什么 Flyway 已经跑完了**：`FlywayMigrationInitializer` 是普通单例，
它的 `afterPropertiesSet()` 在实例化阶段就执行完，
而 `afterSingletonsInstantiated()` 是在**实例化循环结束之后**的另一个循环里调用的。

#### 🔴 怎么"看见"服务器有没有启动过（这是本条的关键）

⚠️ **失败启动后端口最终也会关闭 ⇒ 事后查端口分不出「从没启动」与「启动过又关了」。**
⇒ 改用 devh 的一手信号：`GrpcServerLifecycle.createAndStartGrpcServer()`
在 `localServer.start()`（**端口已绑定**）**之后**才
`publishEvent(new GrpcServerStartedEvent(...))`
⇒ **收到该事件 == 确实对外服务过**。

#### 证据

- **真进程实测**（`ArticleSchemaGateStartupOrderTest`，真 PG 容器 + 真 Spring 上下文 + 真 gRPC server）：
  | 用例 | 结果 |
  |---|---|
  | `missingImmutabilityTriggerFailsBeforeGrpcServerEverStarts` | 启动失败 **且** 未收到 `GrpcServerStartedEvent` ✅ |
  | `healthySchemaStartsTheGrpcServer`（**对照**） | 启动成功 **且** 收到了 `GrpcServerStartedEvent` ✅ |
- ⚠️ **对照用例是必需的**：负向用例断言的是「事件**没**发生」，
  监听器本身坏了就会**假绿** ⇒ 必须有一条正例证明监听器是活的。
- **变异验证（三态判定）**：`MUT-RED`
  —— 把门禁改回 `@EventListener(ApplicationReadyEvent.class)`（= 修复前实现）⇒ 用例转红，
  报错为 `expected: <false> but was: <true>`（"gRPC server 已经启动过"）
  ⇒ **回归网非假绿**，且它**恰好**抓住这条漂移。
- **修复前**的那次红（同一句话、同一断言）与变异红**完全一致** ⇒ 测试确实在测这件事。
- **模块级**：`article-rpc` **114 tests / 0 failures / 0 errors / 0 skipped**（原 112 ⇒ +2）；
  连同 `article-api` 67 + `article-common` 15 ⇒ **article 域合计 196 / 0 / 0 / 0**（`BUILD SUCCESS`）。

#### 判定变化（FIX-07）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-RPC-005` schema 门禁时机 | ❌ OBSERVABLE DIFFERENCE（MEDIUM，静态证据） | ✅ **已修** + 真进程实测 + 变异验证 |
| `DRIFT-ARTICLE-RPC-007` 每通道独立 Kafka brokers | ❌ OBSERVABLE DIFFERENCE（LOW） | 🔴 **OPEN**（本轮未动）〔**本行为历史快照；现状见第九轮 + §7 条目**〕 |
| `U-2` / `U-3` / `U-4` / `U-6` / `U-7` / `U-8` / `U-9` / `U-4'` / `U-5'` | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| S-5（security 域） | ⛔ 阻塞 | ⛔ **仍阻塞**（security 域不在本项目范围内） |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮只修掉 1 条；
`DRIFT-007` 与 9 条 `U-*` 仍在，且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）
⇒ 本条的 ✅ 读作「**在真进程证据下一致**」，不是「已闭环」。

#### 📌 跨域待办（不属于 article 域 scope，登记供主会话处理）

`like-rpc` / `comment-rpc` / `favorite-rpc` / `task-rpc` 的 `*SchemaGate`
**用的是同一套 `@EventListener(ApplicationReadyEvent.class)` 写法**
⇒ 同样在"server 已起来之后"才校验。
⇒ 是否构成同等级缺陷，取决于各自门禁的 fail-fast 程度与所保护的不变量。
⇒ ⚠️ **本工作树不碰其它域**（§5.3：只写自己域的台账文件）。

---

<!-- @fix 2026-10-24 第五轮 · 由 align/article 工作树产出 · 基线 commit 50cab9c -->

## 2026-10-24 第五轮：U-7 关闭 ⇒ **顺带发现并修掉 `DRIFT-ARTICLE-RPC-010`（直方图 77 桶 vs Go 8 桶）**

> 起因是台账里的 `U-7`：「直方图的实际输出与 Go 是否逐桶一致？」
> ⇒ 补上真 scrape 用例后，**答案不是"一致"，而是"差 8 倍"**。

### ✅ FIX-08 —— `U-7` 关闭（真 scrape 实测）+ `DRIFT-ARTICLE-RPC-010` 已修（桶边界对齐 Go）

#### 实测结果（修复前）

真 `PrometheusMeterRegistry`（`PrometheusConfig.DEFAULT`）抓 `article_service_minio_request_duration_seconds_bucket`：

| | 桶边界数 | 内容 |
|---|---|---|
| Go `metrics.go:46` | **9** | `0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1` + 固有的 `+Inf` |
| RAG（修复前） | **77** | `0.001, 0.001048576, 0.001398101, …, 30.0, +Inf` |

⇒ Go 的 8 个桶**一个都没少**，但**多了 68 个** ⇒ `histogram_quantile` 的结果与迁移前不同。

#### 根因（Micrometer 消费点，micrometer-core **1.16.7**）

`DistributionStatisticConfig.getHistogramBuckets(...)`（`:101-117`）取的是**并集**：

```
percentileHistogram 分支  →  PercentileHistogramBuckets.buckets(this)  // 默认百分位桶（额外）
                            + minimumExpectedValue (0.001) + maximumExpectedValue (30.0)
serviceLevelObjectives   →  8 个 SLO 桶（只是"再加"）
```

⇒ `publishPercentileHistogram()` **不是**"用我给的 SLO 桶"，而是"**额外**塞进一整套默认百分位桶"。

#### 修复（去掉一行）

```java
Timer.builder("article_service_minio_request_duration")
        .description("Latency of MinIO requests.")
        .tag("operation", operation)
-       .publishPercentileHistogram()          // ← 这一行让桶从 9 涨到 77
        .serviceLevelObjectives(MINIO_BUCKET_DURATIONS)
        .register(registry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
```

⚠️ 只去掉这一行，**不新增任何桶常量** ⇒ 桶集直接由 `MINIO_BUCKETS`（= Go 的 `metrics.go:46` 同一组数值）决定。

#### 证据

- **真 scrape 实测**：修复前 **77** 个边界 ⇒ 用例转红（`expected: <[0.005, …, Infinity]> but was: <[0.001, 0.001048576, …]>`）；
  修复后 **9** 个，与 Go 逐条一致。
- **变异验证（三态判定）**：`MUT-RED`
  —— 把 `.publishPercentileHistogram()` 加回去 ⇒ 用例转红 ⇒ **回归网非假绿**。
- **模块级**：`ArticleSemanticsRegressionTest` **11 条全绿**（原 10 ⇒ +1）；
  `article-rpc` **115 tests / 0 failures / 0 errors / 0 skipped**（原 114 ⇒ +1）；
  连同 `article-api` 67 + `article-common` 15 ⇒ **article 域合计 197 / 0 / 0 / 0**（`BUILD SUCCESS`）。

#### 判定变化（FIX-08）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `U-7` 直方图桶边界真 scrape | 🔴 OPEN（未做 scrape 对拍） | ✅ **已关闭**（实测 + 变异验证） |
| `DRIFT-ARTICLE-RPC-010` 桶边界 77 vs 9 | 🔴 **未被登记**（被当成"应该一致"） | ✅ **已修** + 真 scrape + 变异验证 |
| `O-2` 的范围 | ⚠️ 只对了名字 | ✅ **范围补完**（名字 + 桶边界） |
| `DRIFT-007` / `U-2` / `U-3` / `U-4` / `U-6` / `U-8` / `U-9` / `U-4'` / `U-5'` | 🔴 OPEN | 🔴 **OPEN**（本轮未动）〔**本行为历史快照；`DRIFT-007` 现状见第九轮 + §7 条目**〕 |
| S-5（security 域） | ⛔ 阻塞 | ⛔ **仍阻塞**（security 域不在本项目范围内） |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：只关掉 1 条 `U-*`、修掉 1 条新漂移；
其余 `DRIFT` / `U-*` 仍在，且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）
⇒ 本条的 ✅ 读作「**在真 scrape 证据下一致**」，不是「已闭环」。

#### 📌 一条可复用的元教训

**注释里那句"因此两侧等价"最危险** —— 它既像结论又是**不查证就下结论的理由**。
本轮正是这句注释（"换算后数值一致，因此两侧的分桶边界相同"）让一条 8 倍的差异
在台账里以 `EQUIVALENT` 的面目待了很久。
⇒ 对照 AGENTS.md §3.3.2（以「Go: …」措辞出现的断言属于高危项）：
**任何"因此两侧一致"的注释，都必须配一条能把它证伪的用例**，否则它只是在**阻止**别人去查。

---

<!-- @fix 2026-10-24 第七轮 · 由 align/article 工作树产出 · 基线 commit 990f5ca -->

## 2026-10-24 第七轮：`U-2` 关闭 —— 锁序**实测**（RAG 侧探针 + Go 侧探针）

### ✅ FIX-13 —— `U-2`（锁顺序反转）关闭，结论落 `Intentional_Deviations`

#### 先说结论

- **Go 侧**：四条写路径分成**两种**锁序 ⇒ **Go 自身**存在锁序反转
  ⇒ 并发「发布回调」×「撤回/删除」时，PG 实测 **`40P01 deadlock detected`**。
- **RAG 侧**：四条路径**统一**为 `article → publication` ⇒ **无反转**
  ⇒ 同样并发下**只是串行化，不会失败**。
- ⇒ 这是一条**真实、可观察**的差异，方向是「**RAG 更安全**」
  ⇒ 按 `docs/Worktree.md` 的判据「**这条差异要不要修回去？**」（不要 —— 修回去 = 重新引入死锁）
  ⇒ **落档到 [`docs/Intentional_Deviations/article/rpc.md`](../../Intentional_Deviations/article/rpc.md)**
  （**SD-2026-10-24-01，已签字**），**本文不重复记**（一条问题只落一档）。

#### 两侧锁序逐条对照（行号指向**消费点**）

| 路径 | Go 出处 | Go 锁序 | RAG 出处（`ArticleAtomicService.java`） | RAG 锁序 |
|---|---|---|---|---|
| 发布回调 `acceptSyncResult` | `article_sync_result_consumer.go:74` → `WithArticleTx`（`community_revision.go:81-89` 先 `FOR UPDATE` article）→ `:140` `tx.Save(&pointer)` | **article → publication** | `:339` `selectByIdForUpdate` → `publicationMapper.upsert`（隐式行锁） | article → publication |
| 审核消费者 `article_consumer` | `article_consumer.go:104` → `WithArticleTx`；**只写 article + outbox，不碰 publication 行** | article（单锁） | `:268` `selectByIdForUpdate` | article（单锁） |
| 降级 `updatePublishedToSourceOnly` | `update_article_logic.go:217` `RetractPublicationTx`（`community_article_event.go:112-114` 先 `FOR UPDATE` publication）→ `:220` `UpdateTx(article)` | **publication → article** | `:127` `selectByIdForUpdate` → `:208` `selectForUpdate` | article → publication |
| 删除 `DeleteArticle` | `delete_article_logic.go:85` `RetractPublicationTx` → `:91` `DeleteTx(article)` | **publication → article** | `:162` `selectByIdForUpdate` → `:208` `selectForUpdate` | article → publication |

#### 🔴 证据①：RAG 侧**锁序探针**（真 PG，观测「第一把锁」）

`ArticleTransactionWiringTest#lockOrderIsArticleThenPublication`（**新建**）：

1. 用**另一条连接**开事务，**同时**扣住 `article` 行与 `article_publication` 行两把行锁；
2. 让被测代码在**另一线程**里跑 `retractAndUpdate` ⇒ 它必然卡在**它最先要的那把锁**上；
3. 用 `pg_stat_activity`（`wait_event_type='Lock'` + `application_name` 过滤）
   读出被阻塞后端正在执行的 SQL ⇒ **那条 SQL 唯一确定第一把锁**。

**实测**：被阻塞的语句 = `SELECT * FROM article WHERE id = $1 FOR UPDATE`
⇒ 第一把锁是 **article** ⇒ 锁序 = **article → publication** ✅

⚠️ 判别子串必须是 `"FROM article WHERE"`（**带 WHERE**）：
`article_publication` 是下划线连接，**不含**该子串 ⇒ 否则两条 SQL 会互相误判。

#### 🔴 证据②：Go 侧探针（pgx v5 + 真 PG，两种锁序各跑一次）

探针 `/tmp/probe-lock/go/main.go`（模块 `probe/lock`，驱动 `github.com/jackc/pgx/v5 v5.10.0`
—— 与真源仓库 `go.mod` 同版本；容器 `postgres:16-alpine`）。
两个事务用**会合点**（拿到第一把锁后与对端会合）制造**确定性交错**，**不靠 sleep 碰运气**。

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

⇒ **同样的表、同样的锁语句，只因顺序不同，一个死锁一个不死锁**
⇒ 差异出在**锁序**上，不是别的。

#### 🔴 证据③：并发**结果**用例（CASE-R12）

`ArticleTransactionWiringTest#concurrentRetractAndDeleteOnSameArticleSerializesWithoutDeadlock`（**新建**）：
同一篇文章并发「降级」与「删除」⇒ 断言
**①** 两个事务都在 60s 内完成（超时即死锁哨兵）、失败方**不是** PG 死锁（SQLSTATE 40P01）；
**②** **恰好一个成功**（后到者撞 Go 的 `community publication pointer conflicts with published article`，
`community_article_event.go:123-125`）；**③** 指针版本**单调到 2**（不是 3）、`state='retracted'`；
**④** 域事件**只有 1 条**、`aggregate_version=2`、`event_type=community.article.retracted`。

#### 变异验证（三态判定）—— 一条**转红** + 一条**仍全绿**

| # | 变异 | 受影响的用例 | 结果 |
|---|---|---|---|
| 1 | `retractAndUpdate` 拿掉 article 行锁（`selectByIdForUpdate` → `selectById`）⇒ 锁序变 `publication → article` | `lockOrderIsArticleThenPublication` | ✅ **转红**（实测被阻塞语句变成 `SELECT * FROM article_publication WHERE article_id = $1 FOR UPDATE`） |
| 1' | **同一个**变异 | `concurrentRetractAndDeleteOnSameArticleSerializesWithoutDeadlock` | ⚠️ **仍全绿** |

⚠️ **1' 是本轮最重要的一条负结果**：

**并发用例测的是「两个事务的最终结果」，而只要两条路径的锁序彼此一致，结果就一样**
⇒ 它**证伪不了锁序**。
⇒ 这正是 AGENTS.md 反复强调的「**跑绿 ≠ 对**」：绿测试可能只覆盖了它碰巧覆盖的那部分
（同一个坑在本仓已出现过两次：`article-rpc` 的 101 条全绿而 jsonb 恒 `null`；
`admin-rpc` 的绿测试把 `update_time` 不推进**锁死**了）。
⇒ **必须另配一条「能唯一判定」的探针**（证据①）——
好在它**不需要**造交错：只要同时扣住两把锁，看它卡在哪一把上就行。

#### 测试计数

`ArticleTransactionWiringTest` **22 条全绿**（原 20 ⇒ +2）；
`article-rpc` **117 tests / 0 failures / 0 errors / 0 skipped**（原 115 ⇒ +2）。

### 判定变化（FIX-13）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `U-2` 锁顺序反转 | 🔴 OPEN（真并发用例未写） | ✅ **已关闭**（RAG 侧锁序探针 + Go 侧探针，双侧实测）；**差异落 `../../Intentional_Deviations/article/rpc.md`（SD-2026-10-24-01，已签字）** |
| `U-3` / `U-4` / `U-9`（Kafka 位点 / seek / 并发 offset） | 🔴 OPEN | 🔴 **OPEN（不变）**（需真 Kafka） |
| `U-4'` / `U-5'`（双侧活体对拍 / 回归网非假绿） | 🔴 OPEN | 🔴 **OPEN（不变）** |
| `S-5`（content security） | 🔴 OPEN | 🔴 **OPEN（不变）**（阻塞于 security 域，不在本项目范围） |
| §10 第 15 项 Concurrency semantics | ⚠️（含「锁顺序（U-2）」待查） | ⚠️ **不变**（锁顺序已定性并落档；offset 语义 U-9 仍 OPEN） |
| **组件判定** | `SEMANTICALLY NOT EQUIVALENT` | **`SEMANTICALLY NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮**没有改变任何运行时行为**（只把一条「未验证」定性与关掉），
且其余 `DRIFT` / `UNRESOLVED` 仍在，也**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）。

#### 📌 一条可复用的元教训

> **「并发用例」不等于「并发语义已被验证」—— 它只覆盖它碰巧交错到的那一段。**
>
> 本轮的实测：把锁序从 `article → publication` 改成 `publication → article`（**一个真实的、
> 已实测会死锁的锁序**），并发用例**依然全绿**；只有那条「同时扣住两把锁、观测它卡在哪把上」
> 的探针转了红。
>
> ⇒ 通用判据：并发用例断言的若是**结果**，它就只对**结果**敏感；
> 要断言**机制**（锁序、隔离级别、可见性），必须有一条**能唯一判定该机制**的探针。
>
> ⇒ 造交错最省事的办法往往不是「编排线程时序」，而是
> **「把要区分的那两把锁都扣住，看它卡在哪一把」**——
> 这条探针**确定性**、不依赖运气，且失败信息直接告诉你「它先要的是哪把」。


<!-- @fix 2026-10-24 第九轮 · 由 align/article 工作树产出 · 基线 commit 3eff5cd -->

## 2026-10-24 第九轮：`DRIFT-ARTICLE-RPC-007` 处置 —— 把「静默忽略」改成「启动即失败」

### ✅ FIX-14 —— 四个 Kafka 通道的 `brokers`：**能力差异仍在，但不再静默**

#### 先说结论

- **Go 侧**：四个通道**各自**用自己的 `Brokers` ⇒ 改某个通道的 `brokers` **会切 broker**。
- **RAG 侧**：Spring Kafka 的客户端是**全局单例** ⇒ 四个通道的 `brokers` 键
  **声明了但没有任何消费点** ⇒ 改它**不切 broker**。
- ⇒ 这是一条**真实的能力差异**（不是「今天没触发」的那种）。
  但当前两侧取值**恰好相同**（`kafka:9092`）⇒ **今天没有行为差异**。
- ⇒ 本轮的处置**不是**「消除差异」（那要新建四套 Kafka 客户端，代价见下），
  而是**把「静默忽略」变成「启动即失败」** ⇒ 差异从「无人察觉」变成「响亮报错」。
- ⇒ 能力差异本身落 [`docs/Intentional_Deviations/article/rpc.md`](../../Intentional_Deviations/article/rpc.md)
  （**SD-2026-10-24-02，已签字**），**本文不重复记**（一条问题只落一档）。

#### 🔴 证据①：Go 侧的完整证据链（声明点 → 消费点，AGENTS.md §3.3.1）

| 层 | 出处 | 内容 |
|---|---|---|
| 类型声明点 | `rpc/internal/config/config.go:19-22` | `KqPusherConf` / `KqConsumerConf` / `ArticleSyncPusherConf` / `ArticleSyncResultConsumerConf` 四个都是 `kq.KqConf` |
| **字段声明点** | `go-queue@v1.2.2/kq/config.go:12` | `Brokers []string` |
| **消费点（生产）** | `go-queue@v1.2.2/kq/pusher.go:44-47` | `kafka.Writer{Addr: kafka.TCP(addrs...)}` |
| **消费点（消费）** | `go-queue@v1.2.2/kq/queue.go:121` | `kafka.ReaderConfig{Brokers: c.Brokers}` |
| 仓内调用点（生产） | `rpc/internal/svc/service_context.go:65,66` | `kq.NewPusher(c.KqPusherConf.Brokers, …)` / `kq.NewPusher(c.ArticleSyncPusherConf.Brokers, …)` |
| 仓内调用点（消费） | `rpc/article.go:58,60` | `kq.MustNewQueue(c.KqConsumerConf, consumer)` / `kq.MustNewQueue(c.ArticleSyncResultConsumerConf, resultConsumer)` |

⚠️ **本轮修正了台账里两处行号错误**：

1. 原写 `kq/config.go:14` —— 错，真实位置是 `go-queue@v1.2.2/kq/config.go:12`。
2. 原写「`internal/config/config.go:41` 的 `Brokers`」—— **错**：
   `config.go:41` 那行 `Brokers []string` 属于 **`HotEventPusherConf`**（第 40 行那个匿名 struct），
   **不是**四个通道的字段。
   ⇒ 这类「行号指对了文件、但指错了字段」的引用，比没有引用更危险
   ⇒ 已在校验器与台账里都改成**完整的三层链**（类型 → 字段 → 消费）。

#### 🔴 证据②：`HotEventPusherConf` 为什么**不在**四个通道里

Go 侧第五个通道 `HotEventPusherConf` 也在 `service_context.go:68-70` 建了 Pusher，
但**全模块零调用点**（`grep HotEventPusher` 只命中 svc 的字段声明、构造行与 config 的定义行，
**没有任何 `.Push(...)`**）⇒ 它是 Go 侧的**死配置**。
Java 侧按 `ArticleRpcProperties` 的既定口径**不迁移**它 ⇒ 自然不在本校验范围内。

#### 🔴 证据③：RAG 侧 —— 真上下文实测（真 PG + 真 Spring 容器）

`ArticleSpringContextWiringTest`（**真 PostgreSQL 容器** + 真 `@SpringBootTest` 上下文）本轮 **+3 条**：

| 用例 | 钉住什么 |
|---|---|
| `kafkaChannelBrokersMatchGlobalBootstrapServers` | 真上下文里，四个通道**绑定后**的 `brokers` 与 `KafkaProperties.getBootstrapServers()` **逐项相同** |
| `kafkaChannelValidatorIsRegistered` | 校验器 Bean 确实注册（哨兵在位） |
| `shippedYamlKeepsAllFourChannelsOnTheSameBrokerPlaceholder` | **shipped yml** 里四个 `brokers` 键**全部**引用同一个 `${KAFKA_BROKERS:...}` 占位符 |

⚠️ **第三条是「语义等价性论证的地基」**：
四个通道**同源**（同一个环境变量）⇒ 四个值**必然相同** ⇒ 与全局同源
⇒ 实际生效的 broker 列表与 Go（`rpc/etc/article.yaml:29-30,46-47,56-57,62-63` 四个通道都写死 `kafka:9092`）**逐项相同**
⇒ **默认配置下无可观察差异**。
一旦有人把某个通道的 `brokers` 改成**字面量**，这条地基就塌了 ⇒ 必须被抓住。

#### 🔴 证据④：校验器**在真上下文里当场抓到 4 处真实的不自洽**

把校验器装上后，**4 个 `@SpringBootTest` 类的上下文直接起不来**：

```
Caused by: java.lang.IllegalStateException: article-rpc 的 Kafka 通道「kq-pusher-conf（生产 article-creation-topic）」
  的 brokers=[[kafka:9092]] 与全局 spring.kafka.bootstrap-servers=[[127.0.0.1:1]] 不一致，但该值**不会生效**。
```

原因：这 4 个类都把 `spring.kafka.bootstrap-servers` 改成 `127.0.0.1:1`（让 listener 惰性重试、不阻塞上下文启动），
而**四个通道的 `brokers` 键还留在 yml 默认的 `kafka:9092`** ⇒
**「配置声明的」与「实际生效的」在同一个文件里互相矛盾**。

⇒ 这不是校验器误报，是**这些测试配置本身不自洽**（长期存在、**从未被任何测试发现**）。
⇒ 处置：4 个类**同时**覆盖四个通道的 `brokers`（保持「不阻塞启动」的意图不变，只把配置改自洽）。

⚠️ **这条是本轮最有说服力的证据**：差异之所以能长期躺着，
是因为它**不产生任何行为差异** ⇒ **任何行为测试都不可能发现它**
⇒ 只能靠**显式的配置一致性校验**把它变成响亮的失败。

#### 处置方案：为什么不「给四个通道各建一套工厂」

| 方案 | 代价 | 结论 |
|---|---|---|
| ① 新建 4 套 `ProducerFactory` / `ConsumerFactory` / `KafkaTemplate` | Boot 4 的 `KafkaAutoConfiguration` 对这些类型都是 `@ConditionalOnMissingBean` ⇒ **新增任何一个都会顶掉 Boot 自动装配的那个**；而 `ArticleOutboxRelayScheduleTest` 还自带一个 mock 的 `KafkaTemplate` Bean ⇒ 「哪个模板被注入」变成**装配顺序问题**（AGENTS.md §3.3 的老坑） | ❌ 不做 |
| ② **静默忽略**（现状） | 运维改了通道 broker → 服务正常起来 → 以为流量已切走 → **实际还连在老 broker 上** | ❌ 最危险 |
| ③ **启动即失败** + 说清原因与出路 | 一次配置分叉会让服务起不来 ⇒ 需要运维配合改配置 | ✅ **本轮采用** |

⇒ 选 ③ 的理由：**当前四个通道的取值必然相同**（同源占位符）⇒ 方案 ③ **不会**误伤任何合法部署；
⇒ 而方案 ③ 把「我以为我改了」这个**最贵的错误**变成了一次可见的启动失败。

#### 校验器行为（`ArticleKafkaChannelValidator`）

- 挂在 **`@PostConstruct`** ⇒ Bean 初始化期 ⇒ **早于**对外收流量
  （与 `DRIFT-ARTICLE-RPC-005` 的 schema 门禁同一条纪律：校验必须在对外服务之前；
  ⚠️ 本校验器**早于** schema 门禁，因为它在 Bean 初始化期、门禁在 `SmartInitializingSingleton` 阶段）。
- 逐通道比较**四个**通道的 `brokers` 与全局 `spring.kafka.bootstrap-servers`：
  **逐项、逐序**比较（不是比集合）。
- **不做 trim**（Go 的 `kafka.TCP(addr)` 对地址逐字使用）⇒ 逐字比较。
- 通道 `brokers` 为空 ⇒ 跳过（= 没配，不是分叉点）。
- 全局 `bootstrap-servers` 为空 ⇒ 跳过（无从比较）。
- 报错消息包含：哪个通道、通道的实际值、全局的值、**「这个值不会生效」**、
  **Go 出处（文件:行号）**、以及两条出路。

#### 变异验证（三态判定）—— 6 个变异**全部转红**

| # | 变异 | 结果 |
|---|---|---|
| 1 | 分叉时只 `LOG.warn` 不抛异常（把「启动失败」降级成「记日志」） | ✅ **转红**（5 条失败） |
| 2 | `channels()` 少校验一个通道（把 result-consumer 通道换成 pusher 的配置） | ✅ **转红**（守恒断言抓住） |
| 3 | 比较逻辑从 `equals` 放宽成 `containsAll`（顺序/个数不同不再算分叉） | ✅ **转红** |
| 4 | 去掉「通道 brokers 为空就跳过」的短路 | ✅ **转红** |
| 5 | `normalize` 真正地 `trim`（`add(s.trim())` + 过滤条件） | ✅ **转红** |
| 6 | 去掉「全局为空就跳过」的短路 | ✅ **转红**（补了用例之后） |

⚠️ **两条负结果（比"全绿"本身更有价值）**：

1. **MUT-5 的第一版是「变异无效」**：最初只把过滤条件改成 `!s.trim().isEmpty()`，
   却**忘了 `out.add(s)` 仍加未清洗的值** ⇒ 变异**根本没实现 trim** ⇒ 测试仍全绿。
   ⇒ **「仍全绿」的第一嫌疑永远是「变异没打中」**，而不是「测试不够好」。
   ⇒ 修正变异（`add(s.trim())`）后**转红** ⇒ 证明测试确实在测「逐字比较」。
2. **MUT-6 的第一版是「测不到」**：去掉「全局为空就跳过」的短路后仍全绿
   ⇒ 该分支**零覆盖** ⇒ **补了一条用例**（`emptyGlobalBootstrapServersSkipValidation`）⇒ 重跑后转红。
   ⇒ 这是「批量改 N 处必须配一条覆盖全部 N 处的守恒断言」（AGENTS.md §3.3）的一个具体实例。

#### 测试计数

`ArticleKafkaChannelValidatorTest` **8 条全绿**（新建）；
`ArticleSpringContextWiringTest` **16 条全绿**（原 13 ⇒ +3）；
`article-rpc` **128 tests / 0 failures / 0 errors / 0 skipped**（原 117 ⇒ **+11**）；
连同 `article-api` 80 + `article-common` 15 ⇒ **article 域合计 223 / 0 / 0 / 0**（`BUILD SUCCESS`）。

### 判定变化（FIX-14）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-RPC-007` 每通道独立 Kafka brokers | 🔴 OPEN（**静默忽略**，配置面误导） | 🟡 **已处置**：能力差异仍在（落 SD-2026-10-24-02，已签字），但**静默 → 启动即失败** + 真上下文实测 |
| `U-3` / `U-4` / `U-9`（Kafka 位点 / seek / 并发 offset） | 🔴 OPEN | 🔴 **OPEN（不变）**（需真 Kafka） |
| `U-4'` / `U-5'`（双侧活体对拍 / 回归网非假绿） | 🔴 OPEN | 🔴 **OPEN（不变）** |
| `S-5`（content security） | 🔴 OPEN | 🔴 **OPEN（不变）**（阻塞于 security 域，不在本项目范围） |
| **组件判定** | `SEMANTICALLY NOT EQUIVALENT` | **`SEMANTICALLY NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮**没有改变任何运行时行为**（只把「配置分叉」从静默变成响亮失败），
且其余 `DRIFT` / `UNRESOLVED` 仍在，也**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）
⇒ 本条的 ✅ 读作「**在真上下文证据下一致**」，不是「已闭环」。

#### 📌 一条可复用的元教训

> **「配置面与生效面不一致」永远不会让任何行为测试变红** —— 因为它本来就不产生行为差异。
>
> 本轮的实测：4 个 `@SpringBootTest` 的配置**长期自相矛盾**
> （全局指 `127.0.0.1:1`、通道指 `kafka:9092`），**所有测试一直全绿**，没人发现。
> ⇒ 一旦加上一条「配置一致性校验」，它**当场**把 4 处矛盾全部翻了出来。
>
> ⇒ 通用判据：**配置类差异不能靠行为测试守，只能靠「启动即失败」的守卫守。**
> 而这类守卫最容易被误当成「过度设计」而省掉 ——
> 省掉之后，代价不是「测试没抓住」，而是**运维在生产上按错误的假设操作**。

<!-- @fix 2026-10-24 第九轮 · 结束 -->

---

<!-- @fix 2026-10-24 第十轮 · 由 align/article 工作树产出 · 基线 commit d06ad63 -->

## 2026-10-24 第十轮：`U-5'` 关闭（4/4 MUT-RED） + `DRIFT-004` 相邻差异**展开并关闭**

### ✅ FIX-15 —— `U-5'`：S-2 / S-3 / S-4 / S-7 的新回归网**证实非假绿**

#### 本轮要补的是什么

§9 的 `U-5'` 原文：

> S-2 / S-3 / S-4 / S-7 新回归网**非假绿**未证 —— 只有 S-1 做过变异验证
> ⇒ 逐条把修复改回去看用例是否转红。

⇒ 即：把每条修复**改回缺陷态**，看对应的守门用例**会不会红**。

#### 变异验证结果（4/4 全部 MUT-RED）

| # | 回归网 | 变异（把修复改回去） | 守门用例 | 结果 |
|---|---|---|---|---|
| 1 | **S-2** | `ArticleMapper.selectAuthorView` 的方向拼接改回「无条件方向」（删掉 `sortBy == null or sortBy == '' ⇒ DESC` 那一支） | `ArticleTransactionWiringTest#authorViewDefaultsToNewestFirst`（真 PG） | ✅ **转红** |
| 2 | **S-7** | `ArticleMapper` 的 `<if>` 参数名改回不存在的 `secondaryTag`（`@Param` 名是 `secondaryTagJson`） | `ArticleJsonbResultMapWiringTest#selectAuthorViewReadsJsonb`（真 PG + 真 MyBatis） | ✅ **转红**（`BindingException`） |
| 3 | **S-3** | `ArticleRpcApplication#articleNoCommitErrorHandler` 的 `setSeekAfterError(true)` 改回 `false` | `ArticleSpringContextWiringTest#kafkaContainersCarryTheForceCommitErrorHandler`（真上下文） | ✅ **转红** |
| 4 | **S-4** | `ArticleRpcApplication#articleKafkaListenerContainerFactory` 的 `setConcurrency(8)` 改回 `1` | `ArticleSpringContextWiringTest#kafkaHandlerConcurrencyMatchesGoProcessorsDefault`（真上下文） | ✅ **转红** |

⇒ **五条回归网（S-1 ~ S-4、S-7）至此全部做过变异验证，没有「未证实非假绿」的条目。**

⚠️ **但要说清这条关闭的边界**：
变异验证证明的是「**用例确实在测这件事**」（改坏就红），
**不**证明「这件事的**机制**在真 Kafka 上与 Go 同构」⇒
`U-3` / `U-4`（失败位点的真实机制）与 `U-9`（并发 offset）**仍 OPEN**，它们要的是**真 Kafka**。

### ✅ FIX-16 —— `DRIFT-004` 的相邻差异（`Selects="*"` vs `NOT_NULL`）**展开并关闭**

#### 先说结论

- ⚠️ **台账原文的推理错在「零值」这个词上**：
  MP 的 `NOT_NULL` 跳过的只是 **null**；
  **零值（`""` / `0` / `false`）照样会被写** ⇒ **两侧在零值上本来就一致**。
- Go 的 `Article` 模型**全是值类型（无指针）** ⇒ `Save` 写的是**零值**，**Go 侧对 `article` 表从来不会写 SQL NULL**。
- ⇒ 真正剩下的差异面只有两个可空对象列（`secondary_tags` / `ext_info`），
  且两侧都在写前 **ensure 非空**（Go `EnsureExtInfo` ↔ RAG `ensureExtInfo`，**调用点一一对应**）
  ⇒ 最多差一层**存储表示**（SQL NULL vs jsonb `'null'`）。
- ⇒ 但**读回的值与过滤结果完全一致** ⇒ **API 层面不可观察**
  ⇒ 按 `docs/Worktree.md` 的判据「这条差异要不要修回去？」—— **不用修** ⇒ **不立新条目**。
- ⇒ 同时**新增一条真 PG 守门网**，把这个「不可观察」的结论钉住（否则结论会随实现漂移）。

#### 🔴 证据①：Go 侧探针（gorm **v1.31.1** + 真 PG，模块 `probe/save`）

⚠️ **版本纠正**：真源仓库 `go.mod:28` 是 **`gorm.io/gorm v1.31.1`**（`go.sum:428` 佐证，**无 replace**），
台账原先引的 `v1.31.2` 是**错版本** ⇒ 本轮用 **v1.31.1** 实跑。
探针的 `Article` / `StringArray` / `JSONMap` 三个类型**逐字抄自**
`service/article/rpc/internal/model/article.go`；GORM 配置也照抄 `internal/model/db.go:39-45`
（`SingularTable: true` + `SkipDefaultTransaction: true`）。

| 探针 | 做法 | 实测结果 |
|---|---|---|
| **A** | 只改 `title` 后 `Save`（实体是从库里读出的） | `secondary_tags` / `ext_info` 被**原样写回**（**不写 null**） |
| **B** | 把 `SecondaryTags` / `ExtInfo` 显式置 `nil` 后 `Save` | 写成 **jsonb `'null'`**（**不是 SQL NULL**）；读回是 `nil` |
| **C** | 把 `Brief` 置 `''`、`ViewCount` 置 `0` 后 `Save`（**零值**） | 写成 **`''` / `0`** —— **不是 NULL，也不是「跳过」** |
| **D** | 库里的 jsonb 列本来就是 **SQL NULL**，再 `Save` 一次 | SQL NULL 被**升级成 jsonb `'null'`**；但**读回的值不变**（都是 `nil`） |

⭐ **探针 C 是本轮最关键的一条**：它**直接推翻**了台账那句
「Go 写全部字段含零值 vs MP 只写非 null ⇒ 零值被跳过」。
**Go 的 `Save` 写零值；MP 的 `NOT_NULL` 也写零值 ⇒ 两侧一致。**

#### 🔴 证据②：RAG 侧真 PG 对拍（`ArticleNotNullUpdateStrategyWiringTest`，**新建**，5 条）

真 PG + 真 `@SpringBootTest` 上下文 + **真 Mapper**（不是 mock、不是 H2）。

| # | 用例 | 钉住什么 | 与 Go 探针对应 |
|---|---|---|---|
| 1 | `zeroValuesAreWrittenJustLikeGoSave` | 零值（`""` / `0`）**照样被写** | **探针 C** |
| 2 | `untouchedJsonbColumnsAreWrittenBackUnchanged` | 没碰过的 jsonb 列**原样写回** | **探针 A** |
| 3 | `nullJsonbFieldsAreSkippedInsteadOfWrittenAsJsonNull` | 实体的 jsonb 字段为 null ⇒ 该列**被跳过**（Go 会写 jsonb `'null'`）⇒ **存储表示不同** | **探针 B** |
| 4 | `sqlNullJsonbColumnsReadBackIdenticallyToGo` | 库里是 SQL NULL 时：RAG 保持 SQL NULL，**但读回与 Go 完全一致（都是 null）** | **探针 D** |
| 5 | `filterBehaviourIsIdenticalForSqlNullAndJsonNull` | **过滤面**同样一致：SQL NULL ⇒ 表达式为 NULL、jsonb `'null'` ⇒ `false`，**两者都把行排除** | 补齐第 4 条的另一半 |

⇒ 第 3 条（**存储表示不同**）+ 第 4/5 条（**读回与过滤都一致**）合起来才是完整结论：
**差异真实存在，但不可观察。**

#### 🔴 证据③：变异验证（4/4 全部 MUT-RED）

| # | 变异 | 结果 |
|---|---|---|
| 1 | 给 `secondary_tags` 加 `updateStrategy = ALWAYS`（null 也写 ⇒ 列被清成 SQL NULL） | ✅ **转红** |
| 2 | 给 `brief` 加 `updateStrategy = NOT_EMPTY`（空串被跳过 ⇒ 零值不再写下去） | ✅ **转红** |
| 3 | `JsonbTypeHandler.readList` 对 SQL NULL 返回空 list 而不是 null | ✅ **转红** |
| 4 | `JsonbTypeHandler.readMap` 对 SQL NULL 返回空 map 而不是 null | ✅ **转红** |

⇒ 这条网**不是装饰**：把写入策略或读出归一改掉，它立刻红。

#### 为什么「不可观察」不是「不存在」—— 逐条列清

| # | 面 | Go | RAG | 结论 |
|---|---|---|---|---|
| 1 | 零值（`""`/`0`/`false`） | 写下去（探针 C） | 写下去（用例 1） | ✅ **一致** |
| 2 | 字符串零值（`""`） | 写下去 | 写下去（`NOT_NULL` 不跳空串） | ✅ **一致** |
| 3 | `int` 计数列的 `0` | 写下去 | 写下去（**原始类型，永不为 null**） | ✅ **一致** |
| 4 | 没碰过的 jsonb 列 | 原样写回（探针 A） | 原样写回（用例 2） | ✅ **一致** |
| 5 | `ext_info` 写前 ensure | `EnsureExtInfo`（Go 6 处调用） | `ensureExtInfo`（RAG 10 处调用，**一一对应**） | ✅ **一致** |
| 6 | `updated_at` | `autoUpdateTime`（gorm v1.31.1 `callbacks/update.go:238-254`） | `ArticleMetaObjectHandler.updateFill` | ✅ **一致**（都写非零值） |
| 7 | `deleted_at` | 取自库里读出的值 | 取自库里读出的值 | ✅ **一致** |
| 8 | **jsonb 列的存储表示** | SQL NULL ⇒ 升级成 jsonb `'null'`（探针 D） | 保持 SQL NULL（用例 4） | ⚠️ **不同** |
| 9 | **jsonb 列的读回值** | `nil` | `null` | ✅ **一致**（`readList`/`readMap` 把 SQL NULL、空白、JSON `'null'` 一律读成 null；Go 的 `Scan` 同理） |
| 10 | **过滤面**（`@>` 算子） | SQL NULL ⇒ NULL、jsonb `'null'` ⇒ false ⇒ **都排除该行** | 同 | ✅ **一致**（用例 5） |
| 11 | **创建路径**（`secondary_tags`） | `model.StringArray(in.SecondaryTags)` ⇒ 空 ⇒ jsonb `'null'` | `new ArrayList<>(...)` ⇒ 空 ⇒ jsonb `'[]'` | ⚠️ **存储不同**，但 proto3 `repeated` 字段**两侧都是空**、按具体标签的过滤**两侧都排除** ⇒ ✅ **不可观察** |
| 12 | **wire 层**（proto3 `repeated`/`map` **无 presence**） | nil / 空 ⇒ **字段不存在** | null 守卫 ⇒ **字段不存在** | ✅ **一致**（**wire 字节逐字节相同**，见第十一轮 + [`proto.md`](./proto.md) 的 `P-U-2`） |

⇒ 唯一剩下的存储层差异（#8 / #11）在**读回**（#9）与**过滤**（#10）上都不可见
⇒ **API 层面不可观察 ⇒ 不修。**

#### 测试计数

- **新增** `ArticleNotNullUpdateStrategyWiringTest` **5 条全绿**（真 PG + 真 Mapper）；
- `article-rpc` **133 tests / 0 failures / 0 errors / 0 skipped**（原 128 ⇒ **+5**）；
- 连同 `article-api` 80 + `article-common` 15 ⇒ **article 域合计 228 / 0 / 0 / 0**（`BUILD SUCCESS`）。

#### ⚠️ 台账卫生修正（本轮一并修掉的三处引用错误）

| # | 错误 | 修正 |
|---|---|---|
| 1 | `gorm@v1.31.2/...`（**错版本**） | 真源仓库 `go.mod:28` 是 **`v1.31.1`**（`go.sum:428` 佐证，无 replace）⇒ 行号随之改为 v1.31.1 的真实值（`finisher_api.go:104,109-110`、`callbacks/update.go:238-254`）；`soft_delete.go:76-96` 两版本**恰好相同**，只有版本号错 |
| 2 | 「MP `NOT_NULL` 只写非 null ⇒ **零值被跳过**」 | ⭐ **错**：MP 的 `NOT_NULL` 只跳 **null**，**零值照样写**（真 PG 实测，见证据①探针 C + 证据②用例 1） |
| 3 | 「需逐调用点核对 Go 是否真的用 `Save`（部分路径用 `Updates(map)`）」 | ✅ **已核对**：`article` 表的**全部** Go 写路径都用 `Save`（`model/article.go:100,104`；`article_sync_result_consumer.go:90,152`；`article_consumer.go:125`）；`Updates(map)` 只出现在 **`article_sync_outbox` 表**（`article_sync_outbox.go:67,77`）⇒ 与 `article` 表无关 |

#### 判定变化（FIX-15 / FIX-16）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `U-5'` S-2/S-3/S-4/S-7 回归网非假绿 | 🔴 OPEN（只做过 S-1） | ✅ **已关闭**（4/4 MUT-RED） |
| `DRIFT-004` 相邻差异（`Selects="*"` vs `NOT_NULL`） | 🟡 **未展开**（且推理有误） | 🟢 **已展开**：差异真实存在但**不可观察** ⇒ **不修、不立条目**；新增真 PG 守门网（5 条 + 4/4 MUT-RED） |
| `U-3` / `U-4` / `U-9` | 🔴 OPEN | 🔴 **OPEN（不变）**（需真 Kafka） |
| `U-4'` | 🔴 OPEN | 🔴 **OPEN（不变）**（需 etcd） |
| `S-5` | ⛔ 阻塞 | ⛔ **仍阻塞**（security 域，不在本项目范围） |
| **组件判定** | `SEMANTICALLY NOT EQUIVALENT` | **`SEMANTICALLY NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮**没有改变任何运行时行为**
（只把两条「未验证 / 未展开」定性与关掉，并给前者补了回归网），
且其余 `DRIFT` / `UNRESOLVED` 仍在，也**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）
⇒ 本条的 ✅ 读作「**在真 PG / 真上下文证据下一致**」，不是「已闭环」。

#### 📌 跨域待办（不属于 article 域 scope，登记供主会话处理）

| # | 待办 | 位置 | 依据 |
|---|---|---|---|
| 1 | 🔴 **`gorm@v1.31.2` 的错版本引用**（同一个 Go 仓库、同一份 `go.mod` ⇒ 版本是 v1.31.1） | `docs/DRIFT/like/rpc.md:881`（`schema/field.go:330-341`）<br>`docs/DRIFT/users/admin/rpc.md:868,1087`<br>`docs/DRIFT/users/user/rpc.md:512,767` | `Sea-RideTheWind_golang/go.mod:28` = `gorm.io/gorm v1.31.1`；`go.sum:428` 佐证；**无 replace 指令** |
| 2 | 🔴 `*SchemaGate` 仍用 `@EventListener(ApplicationReadyEvent.class)`（应挪到对外服务**之前**，见 `DRIFT-ARTICLE-RPC-005`） | `like-rpc` / `comment-rpc` / `favorite-rpc` / `task-rpc` | article 域已完成同型改造（`ArticleSchemaGate` 改用 `SmartInitializingSingleton`） |

#### 📌 一条可复用的元教训

> **「ORM 的字段策略」不能靠「读文档」对齐，必须靠「真 PG 上写一行再读回来」对齐。**
>
> 本轮的实测：台账凭「`NOT_NULL` = 只写非 null」推出「零值被跳过 ⇒ 两侧不同」，
> 而真 PG 上 **MP 的 `NOT_NULL` 把 `""` 和 `0` 都写了下去** ——
> **Go 的 `Save` 也写零值** ⇒ **两侧在零值上本来就一致** ⇒ 那条差异的**主要论据直接不成立**。
>
> ⇒ 通用判据：**ORM 语义（哪些列进 SET、哪些被跳过）属于「只能实测」的层**。
> 读 `field-strategy` 的文档或注解名称，得到的都只是**声明点**；
> 必须真 PG 上「写一行 → 查 `information_schema` / 读回」才是**消费点**证据
> （与 AGENTS.md §3.3.1「Go 事实必须读到消费点」同源，只是这次的对象从 Go 换成了 MP）。
>
> ⇒ 附带一条：**「差异不可观察」也是一个结论**，而且需要**正面证明**
> （读回一致 + 过滤一致），不能只说「应该没影响」。

---

<!-- @fix 2026-10-24 第十一轮 · 由 align/article 工作树产出 · 基线 commit a82950d -->

## 2026-10-24 第十一轮：`P-U-2` 关闭 —— 「Go 创建的行」被 RAG 读的 **wire 字节级**判读

### ✅ FIX-17 —— `P-U-2` 关闭（`article-proto`）+ 第十轮那条「不可观察」结论的**第三条独立论据**

#### 起因

第十轮关掉的那条差异（`secondary_tags` 存 **SQL NULL** vs **jsonb `'null'`**）给了两条论据：
**读回**一致 + **过滤**一致。
⇒ 还能不能从**别的层**再证明一次？⇒ **wire 层**。

#### 🔴 证据①：Go 创建的行，落库形态就是 jsonb `'null'`

```go
// service/article/rpc/internal/logic/create_article_logic.go:94
SecondaryTags: model.StringArray(in.SecondaryTags),
```

⚠️ 注意这里是**直接类型转换**，**不是** `append(nil, ...)`（更新路径 `update_article_logic.go:185` 才是 `append`）
⇒ 请求不带二级标签时 ⇒ **nil slice**
⇒ `StringArray.Value()` = `json.Marshal(nil)` = **`"null"`**
⇒ **落库就是 jsonb `'null'`**。

⇒ **灰度期两侧写同一张表时**，Go 建的行在 RAG 侧读到的就是 `'null'::jsonb`
⇒ RAG 的 `JsonbTypeHandler.readList` 把它读成 **null**
⇒ 而 RAG 自己建的行是 `'[]'` ⇒ **两侧同一张表里可能并存两种存储形态**。

#### 🔴 证据②：两侧的「null / 空 ⇒ 不写入」处置 ⇒ wire 一致

| 侧 | 出处 | 处置 |
|---|---|---|
| Go | `internal/logic/get_article_logic.go:112` `SecondaryTags: article.SecondaryTags` | nil slice ⇒ proto3 `repeated`（**无 presence**）⇒ **字段不存在** |
| Go | `internal/logic/get_article_logic.go:191-201` `cloneStringMap`（`len(source)==0 ⇒ return nil`） | nil map ⇒ proto3 `map`（**无 presence**）⇒ **字段不存在** |
| RAG | `ArticleDomainService:261-263` `if (a.getSecondaryTags() != null) { addAllSecondaryTags(...) }` | null ⇒ 不写 ⇒ **字段不存在** |
| RAG | `ArticleDomainService:265-267` `if (a.getExtInfo() != null && !isEmpty()) { putAllExtInfo(...) }` | null / 空 ⇒ 不写 ⇒ **字段不存在** |
| RAG | `ArticlePublicDomainService:394-399`（公开路径） | 同（**同样有守卫**） |

⇒ **wire 字节完全一致** ⇒ **不是漂移**。

⚠️ **顺带排除一个「以为有 bug」的怀疑**：
protobuf 的 `addAll(null)` / `putAll(null)` 会 **NPE**
⇒ 若 RAG 侧漏了 null 守卫，读 Go 建的行就会崩。
⇒ 实测：**两侧的守卫都在** ⇒ 不崩。

#### 🔴 证据③：新回归网（`ArticleNullJsonbToProtoMappingTest`，真 PG，3 条）

| # | 用例 | 钉住什么 |
|---|---|---|
| 1 | `goCreatedRowWithJsonNullTagsReadsBackAsNull` | jsonb `'null'` 的行在 RAG 侧**读回 null** |
| 2 | `nullEntityFieldsMapToEmptyProtoFieldsWithoutThrowing` | 实体的 jsonb 字段为 null ⇒ 映射**不抛 NPE**，两字段为空 |
| 3 | `wireBytesAreIdenticalToProtoWithoutThoseFields` | ⭐ **wire 字节**与「proto3 里没有这两个字段」**逐字节相同** |

⚠️ 第 3 条用**字节**而不是「集合相等」：proto3 的 `repeated` / `map` **没有 presence**
⇒ 「空集合」与「null」在 wire 上**本来就无法区分**
⇒ 要证明「与 Go 一样」，必须比**字节**。

#### 变异验证（3/3 有效变异 MUT-RED + 1 个**变异无效**对照组）

| # | 变异 | 结果 |
|---|---|---|
| 1 | 删掉 `toAuthorProtoArticle` 里 `secondaryTags` 的 null 守卫 | ✅ **转红**（NPE） |
| 2 | 删掉 `extInfo` 的 null 守卫（只剩 `isEmpty`） | ✅ **转红**（NPE） |
| 3 | `readList` 对 JSON `'null'` 返回**空 list**而不是 null | ✅ **转红** |
| 3x | 【**对照组**】只删掉 `"null".equals(json)` 分支 | ⚠️ **仍全绿** ⇒ 判为「**变异无效**」 |

⚠️ **3x 是一条有价值的负结果**：
删掉 `"null".equals(json)` 后**行为不变** ⇒ 说明 **Jackson 自己就把 JSON 字面量 `null` 读成 null**
⇒ 独立实测（Jackson 2.22.3）：
```
readValue("null", List) => null (null? true)
readValue("null", Map)  => null (null? true)
readValue("  ", List)   => 抛异常: MismatchedInputException
```
⇒ 那条分支**与 Jackson 的行为重复**（冗余但无害，保留是为了让契约显式可见）；
⇒ 而 `json.isBlank()` 那条是**必需**的（空白串会让 Jackson 抛异常），
⇒ `json == null`（SQL NULL）那条也**必需**。
⇒ 已把这个「三个分支各自为什么必需」的实测结论写进 `JsonbTypeHandler.readList` 的 javadoc。

### 判定变化（FIX-17）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `P-U-2`（`article-proto`）`Article` message 的 jsonb 两列 | 🔴 OPEN（未逐字段核对） | ✅ **已关闭**（逐字段 + wire 字节级判读）⇒ 落 [`proto.md`](./proto.md) |
| `P-U-1`（跨栈活体对拍） | 🔴 OPEN | 🔴 **OPEN（不变）**（需 etcd） |
| 第十轮那条「存储层差异不可观察」 | 两条论据（读回 / 过滤） | ✅ **三条**（读回 / 过滤 / **wire 字节**）⇒ 结论加强 |
| **组件判定**（`article-rpc`） | `SEMANTICALLY NOT EQUIVALENT` | **`SEMANTICALLY NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮**没有改变任何运行时行为**；
且 `U-3` / `U-4` / `U-9` / `U-4'` / `S-5` 仍在，也**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）。

#### 测试计数

- **新增** `ArticleNullJsonbToProtoMappingTest` **3 条全绿**（真 PG + 真 Mapper + **wire 字节级**比对）；
- `article-rpc` **136 tests / 0 failures / 0 errors / 0 skipped**（原 133 ⇒ **+3**）；
  连同 `article-api` 80 + `article-common` 15 ⇒ **article 域合计 231 / 0 / 0 / 0**（`BUILD SUCCESS`）。

#### 📌 一条可复用的元教训

> **「跨栈写同一张表」时，两侧的「同一列」可能存着不同的物理形态** ——
> 而这种差异**往往在 wire 层被自动抹平**。
>
> 本轮的实测：Go 创建的行里 `secondary_tags` 是 **jsonb `'null'`**，RAG 创建的是 **`'[]'`**
> ⇒ 同一张表里两种形态并存；
> ⇒ 但 proto3 的 `repeated` **没有 presence** ⇒ 两侧映射过去**都是「字段不存在」**
> ⇒ **wire 字节逐字节相同**。
>
> ⇒ 通用判据：评估「存储层差异」能不能被用户看到时，**要一路追到最外面那一层**（这里是 wire 字节），
> 中间任何一层的「无 presence」语义都可能把它**自动抹平**。
> ⇒ 反过来：**没有 presence 的字段（proto3 的 `repeated`/`map`、可空标量）会把空与缺混为一谈** ——
> 想区分它们，就必须在**更靠近存储的那一层**去判。
---

## 附录 · 🧹 启动守门 flake 修法（2026-10-27 · G4 登记 · G3 域内同款）

| 项 | 内容 |
|---|---|
| 症状 | `ArticleRpcRuntimeBootGateTest#realFatJarProcessBootsAndListens` 偶发红（G4 全量回归 2448 条里 1 failure） |
| 根因 | **不是启动失败**：真进程 fat jar 起来 → `tcpProbe` 端口一连上就立刻 `destroyForcibly()` ⇒ **drain 线程还没读到**「gRPC Server started」⇒ 断言与日志读取**竞态**。辨识度：端口已监听 + 无 `NoClassDefFoundError` ⇒ 进程真的起来了 |
| 修法 | ① 收摊前**先等日志标志行出现**（上限 30s、间隔 200ms）② sink `StringBuilder` → **`StringBuffer`**（跨线程读写）③ `drain.join(10s)` 建 happens-before |
| 验证 | ✅ `ArticleRpcRuntimeBootGateTest` **1/1 绿** |
| 守门加固（**2026-10-27 · 照 G2 同款修复 `f57ff38` 的增强**） | 🟢 **标志串提为常量** `GRPC_STARTED_MARKER` ⇒ 消除「**等待循环**」与「**断言**」两处**各写字面量**的漂移隐患（任一处被改 ⇒ 等待与断言不再配套 ⇒ 要么白等满 30s 上限后莫名失败，要么断言**平凡通过**）⇒ **两处共用一个定义** ⇒ 不可能漂移。<br>**验证**：提取后 `ArticleRpcRuntimeBootGateTest` **1/1 绿**（10.40s）· **变异 1/1 精确变红**（把常量值改成假串 ⇒ 等满 30s 上限 ⇒ 红在 `:159` 的**目标断言**、失败消息点名 `MUTANT` 且**带真实日志尾部** ⇒ 证明「进程真起来了、红的是标志串不匹配」⇒ **非假绿**）· 回滚后**字节一致**（md5 相同 ⇒ 与绿版同一份内容） |
| 整域回归 | ✅ **article-rpc 155 条**（skipped 0）全绿 ⇒ **四域 12 模块合计 919 条**（0 failures / 0 errors / 4 skipped · 9:44 min · BUILD SUCCESS · 基线 `eb4d1d2` + 本轮改动 · 核对日期 2026-10-27 · 两口径逐模块一致） |
| 整域回归（**本轮 · D10 落码后 · 2026-10-27**） | ✅ **article-rpc 155 条**（skipped 0）全绿 ⇒ **四域 12 模块合计 922 条**（0 failures / 0 errors / 4 skipped · **10:17 min** · BUILD SUCCESS · 基线 `deeeb8d` + 本轮文档改动 · 核对日期 2026-10-27 · 两口径逐模块一致）<br>⚠️ **上一行的 919 保留原值不改写**（那是基线 `eb4d1d2` 的口径）⇒ ⇒ **两个数不在同一条基线上 ⇒ 不可相加**；本轮本域 **article 三个模块逐条不变**（15 / 99 / 155）⇒ ⇒ 差额 **+3 全部来自 comment-rpc**（D10 新增 3 条守门，commit `deeeb8d`） |
| 同款缺陷 | 🔴 `comment-rpc` 同款（**同属 G3 写权面** ⇒ 已在 comment 域同批修）· 🔴 `like-rpc` 同款（**属 G2 写权面** ⇒ 已回报 G2，本域**不越权改**） |
---

### 附录 · 📌 G4 侧共享层基线与影响面订正（2026-10-27 · 来自 G4 第七次全量回归）

**① G4 第七次全量回归口径**（**共享层基线** ⇒ 本域下次合并 G4 分支时的对照物）：

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| 结果 | **BUILD SUCCESS / MVN_EXIT=0** |
| 规模 | **52 模块 · 2457 条**（29 个有测试的模块**逐模块求和**，不按行序 tail） |
| 计数 | **failures 0 · errors 0 · skipped 4** |
| 耗时 | **20:28 min** |
| 相对上次 | 第五次 2448 ⇒ **+9**（本轮新增 3×3 条守门：关闭路径 / 遮蔽契约 / 池自愈） |
| 双侧探针 | `run-parity.sh` **EXIT=0**（56 例：一致 54 / 不一致 0 / 已签字偏离 2）＋ `run-rpc-timeout-parity.sh` **EXIT=0**（3 例） |
| 三门禁 | `wave-lane-ownership` / `wave-role-model` / `wave-coupling` ⇒ **0/0/0** |
| 来源 | G4 commit `5564c27`（基线含单 4 的 `a1a2e05`） |

**② 🟡 影响面订正注记（**保留原值不改写** + 加注记）**：
G4 在共享层 `docs/DRIFT/global/vs-common.md` §2 的 **A2 行**逐个 rpc **自核**（`@GrpcClient` 计数 ＋
**六种出向方式**交叉核：`newBlockingStub` / `newFutureStub` / `newStub` / `ManagedChannel` /
`RestTemplate` / `WebClient`）后给出**全局**口径：

| 口径 | 内容 |
|---|---|
| **全局**（G4 自核） | **可观测 6 个**（article-rpc · comment-rpc · favorite-rpc · like-rpc · message-rpc · follow-rpc）＋ **N/A 4 个**（**hot-rpc** · task-rpc · user-rpc · admin-rpc）⇒ **6/10 可观测 ＋ 4/10 N/A** |
| **本域面**（G3 自核） | **3/4**（article-rpc · comment-rpc · favorite-rpc 有出向面；**hot-rpc = 0 ⇒ N/A**） |

⇒ ⚠️ **本域面 ≠ 全局面**：**「3/4」只覆盖本域四个 rpc 组件** ⇒ **不得被读成全局口径**。
⇒ **可复用判据**：「**共享层缺位**」是**全域事实**，「**本域受害**」是**逐域事实** ⇒
写影响面必须**逐域实测**（并用**多种检索方式交叉核**），且**不得把域内结论外推**。

**③ G4-4 反馈单已闭合**（本域 2026-10-27 登记的共享层 U+FFFD）：
G4 commit `0c72969`「修 `service/common` 两处 UTF-8 替换字符（U+FFFD）⇒ 纯注释，零行为变化」
⇒ **本域的反馈单已落、零行为变化** ⇒ **无需再跟进**。
---

### 附录 · 📌 第六次全量回归的 `article-rpc` 2 条失败 ⇒ **根因在共享层，本域零改动**（2026-10-27 · G4 通报）

| 项 | 内容 |
|---|---|
| 现象 | `ArticleGrpcInterceptorWiringTest.serverSideTimeoutIsActuallyEnforced` ⇒ `actual status=INTERNAL description=panic: Task … rejected from ScheduledThreadPoolExecutor[Terminated, …]`；另一条 **`handler` 根本没被调用** |
| **归属** | 🔴 **根因在共享层**（`service/common`）⇒ **本域零改动、那两条用例不用改**（G4 已修）⇒ ⚠️ **别把它读成「article-rpc 有缺陷」** |
| 根因 | `RpcInterceptor` / `HttpMiddleware` 的三个**进程级静态池**是 `static final` ⇒ `shutdown()` **永久终止** ⇒ surefire **一模块一 JVM** ⇒ **模块内多个 Spring 上下文** ⇒ 前一个上下文关闭后，后续上下文里 `schedule()/submit()` **被拒** ⇒ 拦截器返回 **INTERNAL** 而不是 **DEADLINE_EXCEEDED** |
| 🔴 **为什么前五轮全量都绿** | 修 G1 派单 #8 之前，纯 gRPC 进程的 `@PreDestroy` **先抛 `NoClassDefFoundError`** ⇒ `RpcInterceptor.shutdown()` **根本没机会执行** ⇒ 三个静态池**侥幸存活** ⇒ ⇒ **那不是「本来就对」，是「缺陷把保护罩挡住了」** |
| 修法 | 三个池改 `AtomicReference` + CAS 自旋 ⇒ **终止后按需重建** ⇒ 落 `service/common`（**G4 写权**）⇒ **本域不用改** |
| 实测 | `article-rpc` **154 条 / 0 失败**（修前同模块 2 条失败）⇒ 已转绿 |
| **教训（可复用）** | ① **「跑绿 ≠ 对」的第四种形态：绿是因为那条路径根本没被执行过** ⇒ 拿到「一直绿」的结论时多问一句「**这条路径在本轮之前真的被执行过吗**」——不只问「测试绿不绿」，还要问「**有没有被另一个缺陷挡在前面**」。<br>② **归属判定看「根因落在哪一层」，不看「失败用例在哪个域」** ⇒ 失败用例在别人域、根因在共享层 ⇒ **修共享层，域侧零改动**（用例不用改）。<br>③ **「修好一个缺陷 ⇒ 另一个潜伏缺陷显形」不是意外而是常态** ⇒ 修缺陷前先想「**它挡住了什么**」⇒ 修完要准备接住新暴露的问题，而不是把它当成「回归失败」。 |

---

### 附录 · 📌 订正注记（2026-10-27 · G3）：G4 **第八次**全量口径（**保留上文第七次原值不改写**）

**① G4 第八次全量回归口径**（来源 G4 commit `23d1c2a` §4.10.11）：

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| 结果 | **BUILD SUCCESS / EXIT=0** |
| 规模 | **52 模块 · 2461 条**（29 个有测试的模块**逐模块求和**，不按行序 tail） |
| 计数 | **failures 0 · errors 0 · skipped 4** |
| 耗时 | **20:21 min** |
| 相对上次 | 第七次 2457 ⇒ **+4** |
| 双侧探针 | `run-parity.sh` **EXIT=0**（56 例）＋ `run-rpc-timeout-parity.sh` **EXIT=0**（3 例） |
| 三门禁 ＋ 台账三门禁 | **0/0/0 ＋ 0/0/0** |

⚠️ **「+4」的归因是【推断】**：依据 = G4 `23d1c2a` 的 §4.10.9 记「FB-04（A9）落码 … **守门 4 条** / 变异 4-4」⇒ **条数吻合**；
（同轮的 §4.10.10 是 **U+FFFD 纯注释**自修 ⇒ 不产生测试条数。）

⚠️ **口径边界（不可相加）**：**2461 是 G4 树的口径**（基线含 G4 单 4 的 `0c72969`）⇒
**本域的 919 条是 G3 树的口径**（4 域 × 3 组件）⇒ **两个数不在同一条基线上 ⇒ 不可相加**；
合并后总数会变 ⇒ **波末收口回归才是最终口径**。

⚠️ **本块是「加注记」，不是「改原值」**：上文第七次口径（2457 条 / 20:28 min / `5564c27`）**原样保留** ⇒
读本域历史结论的人**以两块并列为准** ⇒ ⇒ **不要把第八次误读成「第七次作废」**。

## 二十三、🔴🟢 **Testcontainers 宿主口钉口** —— 把 `PF-ARTICLE-RPC-016` 的**根因**治掉（核对日期 2026-10-28 02:4x）

> **起因**：G4 台账 §4.10.58 分配了「Testcontainers 宿主口段」并**明写两句**：
> ① 「Testcontainers **没有「宿主口段」这个概念** ⇒ 实测 `TestcontainersConfiguration` 的配置项里**零个** `port` 相关项 ⇒ ⇒ 「指定一段」**只能逐容器固定口**」；
> ② 「**本层未实测的两层**：① `PostgreSQLContainer` **不能**直接换成 `FixedHostPortGenericContainer`（后者不带 PG 的初始化/JDBC 支持）⇒ ⇒ 正确形态应是 `PostgreSQLContainer` ＋ `withCreateContainerCmdModifier(...)` 显式绑口；② **「显式绑口后 `getMappedPort()` 是否返回那个固定口」未实测** ⇒ ⇒ **域侧落地时必须先写一条探针验证，不能照抄**」。
> ⇒ ⇒ 本节就是那个「**域侧探针 + 落码**」⇒ ⇒ **G4 的未知项由本域实测回答**。

### 二十三.1 🔴 先答 G4 那个未知项（**实测，不是推断**）

**探针**（临时落码，跑完即删；正式守门已接管该职责）：`service/article/article-rpc/src/test/java/com/rag/article/article/rpc/support/HostPortMechanismProbeTest.java`
（**Testcontainers 2.0.5** · `docker-java-api 3.7.1`）

| 机制 | 做法 | `getMappedPort()` | `getFirstMappedPort()` | 那个固定口上**是谁** |
|---|---|---|---|---|
| **A** | `withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(pin), new ExposedPort(5432))))` | **22850（== 钉的口）** ✔ | 22850 ✔ | **JDBC 连 22850 成功** · `current_database()`=`probe_db` · `inet_server_port()`=**5432** ✔ |
| **B** | 子类暴露 `addFixedExposedPort(pin, 5432)`（= `FixedHostPortGenericContainer` 同款） | **22851（== 钉的口）** ✔ | 22851 ✔ | **JDBC 连 22851 成功** · 同上 ✔ |

⇒ ⇒ **「显式绑口后 `getMappedPort()` 是否返回那个固定口」＝ 是**（两条机制都成立）⇒ ⇒ **G4 的未知项有答案了**。
⇒ ⇒ **落码用机制 A**（G4 台账指定的那个形态）；**机制 B 一并留档** ⇒ 「不止一条路可行」⇒ ⇒ 避免后来人以为只有 A、或反过来去写 `PostgreSQLContainer` 的子类。

⚠️ **口径**：只断言「数字相等」**不够** ⇒ ⇒ 数字相等也可能是「我读到了自己的期望值」⇒ ⇒ 必须**真连一次**：
JDBC 连**固定口** + `inet_server_port()` 回报**容器内口** ⇒ ⇒ 才证明「那个口上不是别的 PG」。
（Redis 同款：`PING\r\n` ⇒ 期望 `+PONG`。）

### 二十三.2 落码清单（**4 个模块 · 28 个测试类**）

| 模块 | 域段（G4 分配） | 本模块宿主口段 | 脚手架段 | 容器数 | 落点 |
|---|---|---|---|---|---|
| `article-rpc` | `22850–22879` | `22850–22869` | `22870–22879` | **9** 个 PG | `service/article/article-rpc/src/test/java/.../support/RealContainerHostPort.java` |
| `comment-rpc` | `22880–22909` | `22880–22899` | `22900–22909` | **11** 个 PG + **1** 个 Redis | `.../comment/rpc/support/RealContainerHostPort.java` |
| `favorite-rpc` | `22910–22939`（域段横跨两模块 ⇒ 取**下半段**） | `22910–22919` | `22920–22929` | **8** 个 PG | `.../favorite/rpc/support/RealContainerHostPort.java` |
| `favorite-api` | 同上 ⇒ 取**上半段** | `22930–22934` | `22935–22939` | **1** 个 Redis | `.../favorite/api/support/RealContainerHostPort.java` |
| `hot-rpc` | `22940–22969` | `22940–22959` | `22960–22969` | **3** 个 Redis | `.../hot/rpc/support/RealContainerHostPort.java` |

⇒ ⇒ **切法沿用 comment 域已定的约定**（见 `comment-rpc` 的 `support/RealGrpcPortTest` 的脚手架段说明）：
**下段 = Testcontainers 宿主口 · 上段 = 守门脚手架段** ⇒ ⇒ **两者零重叠**。
⚠️ **favorite 域段横跨两个模块** ⇒ ⇒ 必须**再按模块切一刀** ⇒ ⇒ 否则 `favorite-rpc` 与 `favorite-api` 同跑时**会撞同一个口**。
⚠️ **`hot-rpc` 的 Redis 容器**原本也吃随机口（`new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))`）⇒ ⇒ **同类根因** ⇒ ⇒ 一并钉。
⚠️ **`hot-rpc` / `comment-rpc` 的 `docker run -p 127.0.0.1:<port>:6379` 那两处**（断连对拍）⇒ ⇒ **本来就是显式口** ⇒ ⇒ 不在本次范围内。

### 二十三.3 三条纪律（本轮踩出来的，别重踩）

1. 🔴 **源扫必须先剥注释**（状态机实现，**不动字符串字面量**）
   ⇒ ⇒ 本轮守门第一版**把 javadoc 里的示例** `RealContainerHostPort.bindPg(RealContainerHostPort.XXX)` **当成了真代码**
   ⇒ ⇒ 报了一条**假红**（「`XXX` 没登记」）⇒ ⇒ 修法 = 扫之前先剥注释（注释内容替换成**等长空格** ⇒ 行号不变）。
2. 🔴 **登记册必须按「常量名」登记，不能按「值」**
   ⇒ ⇒ 段的边界常量 `FIRST = 22850` 与第一个登记口 `GRPC_INTERCEPTOR_WIRING = 22850` **同值**
   ⇒ ⇒ 若按值反查名字，会把 `FIRST` 当成登记项 ⇒ ⇒ 守门对不上账 ⇒ ⇒ 只能按名字登记。
3. 🟡 **「每个测试类一个专属口」而不是「全模块共用一个口」**
   ⇒ ⇒ surefire 默认 1 fork、无 parallel 配置 ⇒ ⇒ 模块内**串行** ⇒ ⇒ 理论上共用一个口也能跑
   ⇒ ⇒ 但那样有「上一个类刚停、下一个类立刻抢同一个口」的**释放窗口** ⇒ ⇒ 撞上 = `EADDRINUSE`（响亮的失败，但仍是抖动）
   ⇒ ⇒ ⇒ 每个测试类一个专属口，**全域不复用**。

### 二十三.4 守门 + 变异验证

**守门**（每模块一份 `support/RealContainerHostPortTest.java`）：**5 条**（article / favorite-rpc / favorite-api / hot-rpc）＋ **6 条**（comment，多一条 Redis 活体）

| # | 断言 | 守的是什么 |
|---|---|---|
| ① | `registeredPortsAreInsideTheSegmentUniqueAndOutOfTheEphemeralRange` | 分配合规：段内 · 唯一 · **不落进 OS 临时端口段** |
| ② | `segmentsDoNotOverlapAndStayInsideTheDomainSegment` | 宿主口段与脚手架段**零重叠**、都在域段内 |
| ③ | `everyContainerDeclaringTestClassIsPinned` | **守恒**：每个「起容器」的测试类都钉了口（漏钉 ⇒ 抓出来） |
| ④ | `everyPinnedPortIsRegisteredAndUsedExactlyOnce` | **守恒**：引用的口在白名单里 · 每个登记口**恰好被引用一次**（重复 ⇒ 抓出来） |
| ⑤ | `pinnedPostgresReportsTheFixedHostPortAndServesPostgresOnIt` / `pinnedRedisReportsTheFixedHostPortAndAnswersPing` | **机制生效（活体）**：`getMappedPort()` == 固定口 ＋ 那个口上就是我的容器 |

**变异验证**（`article-rpc` 那份，5 轮，**每轮只翻一个变量**）：

| 变异 | 预期 | 实测 |
|---|---|---|
| **M1** 删掉一个测试类的钉口调用 | 红在 ③ | ✔ 红在 `everyContainerDeclaringTestClassIsPinned` |
| **M2** 两个类用同一个口 | 红在 ④ | ✔ 红在 `everyPinnedPortIsRegisteredAndUsedExactlyOnce` |
| **M3** 登记册删一项 | 红在 ④ | ✔ 同上 |
| **M4** 登记口改成 `22899`（越段） | 红在 ① | ✔ 红在 `registeredPortsAreInsideTheSegmentUniqueAndOutOfTheEphemeralRange` |
| **M5** 绑定到 `0`（机制失效） | 红在 ⑤ | ✔ 红在 `pinnedContainerReportsTheFixedHostPortAndServesPostgresOnIt` |

⇒ ⇒ **5/5**：每轮**编译通过** ＋ **红在目标断言** ＋ 还原后 **sha256 逐字节一致**。

### 二十三.4-bis 📌 **补齐（2026-10-28 · G3）**：「裸 `docker run` 起容器」原先落在守门的**盲区**里

**原文保留**：§二十三 的落码范围写的是「**Testcontainers** 宿主口钉口」⇒ ⇒ 它的守门特征
（`CONTAINER_DECL`）也只认 Testcontainers 的两种容器声明（`new PostgreSQLContainer` / `new GenericContainer`）。

**📌 订正（2026-10-28）**：「起容器」**不止 Testcontainers 一种** ⇒ ⇒ hot 域的
`HotRedisDisconnectFastRejectGateTest` 用**裸 `docker run -p 127.0.0.1:<port>:6379`** 起 Redis
⇒ ⇒ 它**没有** `bindRedis(...)` 调用 ⇒ ⇒ **守门特征数不到它的钉口** ⇒ ⇒
**它原先落在「起容器 ⇒ 必须钉口」这条机械约束的盲区里** ⇒ ⇒ **缺口就是这么漏过去的**
（而 comment 域那一份**已经**用 `RealGrpcPort.pick()` 钉段 ⇒ ⇒ **同类不同命**）。

**⇒⇒ 本轮补齐（域侧自修 · 不派单）**：

| # | 落点 | 内容 |
|---|---|---|
| ① | `service/hot/hot-rpc/.../support/RealContainerHostPort.java` | 新增两个**登记口** `HOT_RD_REJECT_GATE = 22943`（A 组）/ `HOT_RD_DEFAULT_GATE = 22944`（B 组）⇒ ⇒ **两组各占一个口**（不能复用：容器还在占着）⇒ ⇒ 并登记进 `TEST_CLASS_PORT_NAMES`；新增 `pinned(int)`（校验 ＋ 原样返回）供**非 Testcontainers** 形态使用 |
| ② | `service/hot/hot-rpc/.../HotRedisDisconnectFastRejectGateTest.java` | 宿主口由 `freePort()`（**OS 临时端口段**）⇒ 改为 `RealContainerHostPort.pinned(RealContainerHostPort.HOT_RD_*_GATE)`；**删掉 `freePort()`** 与相关 import |
| ③ | `service/hot/hot-rpc/.../support/RealContainerHostPortTest.java` | 🔴 **守门形态扩展（这才是「堵住」的关键）**：`CONTAINER_DECL` **一并识别裸 `docker run`**；`PIN_CALL` **一并识别 `pinned(...)`** ⇒ ⇒ ⇒ **缺口被机械堵住，不靠人记得** |

**验证（2026-10-28）**：

- `RealContainerHostPortTest` **5 条 / 0 failures / 0 errors**（新增 2 个登记口后仍绿 ⇒ 段内 · 唯一 · 不落 OS 临时段 · 引用恰好一次 全过）；
- `HotRedisDisconnectFastRejectGateTest` **2 条 / 0 failures / 0 errors**，实测 **A(REJECT_COMMANDS) = 0 ms · B(DEFAULT) = 3009 ms**（差 3009 ms）
  ⇒ ⇒ **钉口没有改变被测行为**（钉口前 A=1 ms / B=3007 ms ⇒ 同量级）⇒ ⇒ **这次只动「口从哪来」，不动「测什么」**；
- **变异（M1）**：把钉口退回 OS 临时端口字面量 ⇒ ⇒ **红 2 条**
  ⇒ `everyContainerDeclaringTestClassIsPinned`（「起容器但没钉口」）
  ＋ `everyPinnedPortIsRegisteredAndUsedExactlyOnce`（登记口成孤儿）
  ⇒ ⇒ **守门有判别力，不是恒真**；还原后 sha 复原 ✔。

**⇒⇒ 口径订正（下一轮照这个读）**：§二十三 的覆盖范围是「**Testcontainers 容器**的宿主口」；
**裸 `docker run` 起的容器不在其中** ⇒ ⇒ **凡用裸 docker 起容器的探针，都要自己钉段**
（comment 域已做 · hot 域本轮补上）⇒ ⇒ **下一轮若再出现裸 docker 起容器，先查它钉没钉口**。

### 二十三.5 证据强度（**分层读，别混**）

| 事项 | 强度 | 说明 |
|---|---|---|
| 「钉口机制可用（两条都成立）」 | **L4**（本域活体） | 探针 + 守门都真起过容器、真连过 |
| 「本域 28 个测试类不再把宿主口放进 OS 临时端口段」 | **L4**（结构事实 ＋ 源扫守恒） | 段号 `22850–22969` 全在 `49152–65535` 之外 ⇒ **这是结构事实，不是「跑绿了」** |
| 「宿主口撞口这类 flake 今后不再发生」 | 🟡 **推断** | 一次全量绿**证明不了复发概率** ⇒ 判据是上面的**结构事实** |
| 与 Go 侧的一致性 | **N/A** | 这是**测试基础设施**，**不涉及任何运行时行为** ⇒ ⇒ **不属于 §3.1 的语义对齐范围** |

### 二十三.6 📌 顺带留档：`FavoriteDdlParityTest.java` 里的 **4 个真实 NUL 字节**（**不是损坏**）

⇒ ⇒ 该文件第 136 / 183 行是 `"\0"`（**真实 NUL 字节**，不是转义序列）⇒ ⇒ 用途 = **Redis 复合 key 的分隔符**（**故意的夹具**）
⇒ ⇒ **后果**：`grep` 把该文件判成 **binary** ⇒ `git diff` 也按二进制处理 ⇒ ⇒ **不是 U+FFFD、不是编码损坏**
⇒ ⇒ 🔴 **不要「修」**（与 `article-api` 的 `ArticleApiMvcPipelineTest` 那 2 处 U+FFFD **同款**处置：故意测试数据 ⇒ 保持原样）
⇒ ⇒ ⚠️ 但**脚本化改文本时要注意**：读写必须**按 UTF-8 逐字节往返** ⇒ ⇒ 本轮迁移脚本已加断言「**NUL 字节数不变**」。

#### 📌 订正注记（2026-10-28 10:2x · G3）—— 上面那句「**不要「修」**」【过度推广】，且「同款处置」**不成立**

⇒ **已改（2026-10-28）**：那 4 个真 NUL **已换成 Java 源码转义 `"\0"`**（八进制转义 ＝ `U+0000`）⇒ ⇒ 文件**变回文本**（`b'\x00'` 计数 **4 → 0**）⇒ ⇒ **零行为变化**（实测 `FavoriteDdlParityTest` **1 条 / 0f / 0e / 0s / BUILD SUCCESS**，真 PG 活体）⇒ ⇒ **上面的行号（第 136 / 183 行）也是错的** ⇒ 实测（改前）NUL 在 **`:138` / `:185`**。
⇒ ⇒ **为什么「同款处置」不成立**：
- **`ArticleApiMvcPipelineTest` 那 2 处 U+FFFD** ＝ **测试载荷本身**（**被测对象**）⇒ 真字节**必须留**（改了永远找不到 ⇒ 假红）⇒ **不许动**；
- **本条那 4 个真 NUL** ＝ **可改「写法」的夹具**（值是 Redis 复合 key 的分隔符）⇒ ⇒ **值不动、只把写法从真字节换成源码转义** ⇒ ⇒ **该改**。
⇒ ⇒ 🔴 **但真正该记的是另一半**（G4 通报 · G3 确认）：**真 NUL 的真正代价不是语义，是【git 把该文件当二进制】⇒ 三方合并不做文本合并 ⇒ `git merge-tree --write-tree` 产出「揉出来」的第三版且【不报冲突】⇒ ⇒「零冲突」推不出「内容完整」** ⇒ ⇒ 这才是必须改写法的理由 ⇒ ⇒ 逐条 ⇒ [`favorite/rpc.md` 的 NUL 转义一节](../favorite/rpc.md#📌-nul-转义2026-10-28--g3-真-nul-字节换成-java-源码转义零行为变化且让文件变回文本) ＋ [`PIT-FAV-RPC-040` 订正节](../../Pitfalls/favorite/rpc.md#🔴-订正2026-10-28-102x--g3-第-2-条处置的理由是错的且真-nul-的真正代价不是语义)。
📌 **订正（2026-10-28 10:4x · G3）—— 上面那段里的「产出『揉出来』的第三版且【不报冲突】」两处措辞【被实测推翻】**（原文保留不改写）：决定性实验（五组对照 · git 2.50.1 · base/ours/theirs 三方各异）⇒ ⇒ **git 报 `warning: Cannot merge binary files` ＋ `CONFLICT (content)` ＋ 退出码 1**（**不**是不报冲突）⇒ ⇒ 预演树里该文件**取 ours**（**不是**「揉出来的第三版」）⇒ ⇒ 真合并留 **stage 1/2/3** ⇒ `git commit` 被拒 ⇒ ⇒ **不是「静默提交坏内容」**⇒ ⇒ 真正的危害 ＝「**报冲突但【不产生冲突标记】** ⇒ 工作区看起来正常 ⇒ 人若直接 `git add` 提交 ⇒ 另一侧改动被丢」⇒ ⇒ 🔴 **最危险的一条（D 组）**：**只要 merge-base 还含真 NUL，即使两侧都已去 NUL，这次合并仍走二进制路径** ⇒ ⇒ **「我这边修了 NUL」不足以保证合并安全** ⇒ ⇒ 逐条 ⇒ [`PIT-FAV-RPC-040` 订正第二轮](../../Pitfalls/favorite/rpc.md#🔴-订正第二轮--2026-10-28-104x--g3-决定性实验已做不报冲突与揉出第三版两处措辞被实测推翻)。

### 📌 订正注记（2026-10-28 03:0x）—— **上面那两笔提交信息里的「门禁 16/18、红 2」是错的**

| 项 | 提交信息里写的 | **实测（正确）** |
|---|---|---|
| 门禁 | 「**16/18 EXIT=0**（红 2）」 | **15/18 EXIT=0 · 红 3** |
| 红的那几条 | 只列了 `check-ledger-count` ＋ `check-wave-coupling` | ⇒ **漏了第三条 `check-ddl-static`**（users 域**既知**的 9 处类型差异 ⇒ **不在本域** ⇒ 预期内红） |

⇒ ⇒ **三条红的成因各不相同，别混**：
① `check-ledger-count`（索引 315 / 实测 **331**）⇒ **预期内** ⇒ 合并后**统一** `--fix`（⛔ 不要各 lane 各自 `--fix`）；
   📌 **订正（2026-10-28 10:3x · G3）—— 这里的 331 已过期**（台账条目数在波次中持续增长）⇒ ⇒ **同节 §24.2 的表已更新为 332**，而 §24.2 之后又变了 ⇒ ⇒ **当前实测 = 333（差 +18）** ⇒ ⇒ **原 331 保留不改写**。
② `check-ddl-static`（users 域既知 9 处）⇒ **不在本域** ⇒ 本域零改动；
③ `check-wave-coupling`（`common_radiation` 快照）⇒ = **G4-9** ⇒ 等 G4 更新共享矩阵。

⇒ ⇒ **纪律**：回执里的门禁口径**必须逐条点名红的那几条** ⇒ ⇒ 只报「几个红」不给成因 ⇒ ⇒ 下一轮的人分不清「真红」与「预期红」。

### 二十三.7 🟢🔴 **gRPC 真进程端口也迁出 OS 临时段**（**13 处** ⇒ 本域专用段 `22400–22479`）· 2026-10-28 10:5x · G3

**13 处的形态拆解**：`grpc.server.port=0`（**属性形态**）**7** ＋ `--grpc.server.port=0`（**进程参数形态**）**1** ＋ `freePort()`（**裸取临时口**）**1** ＋ `forPort(0)`（**进程内 gRPC server**）**4**。

**§二十三 治的是「容器宿主口」；这一节治的是另一类口 —— 「被测进程自己 listen 的 gRPC 口」。**

#### 起因（域侧台账早就记着，我先前只当它是「等 G4 派单」⇒ **误判**）

`docs/DRIFT/article/vs-common.md` 的 **A5 行**（2026-10-27 G3 实测补登）已写明：本域**测试面**有 **9 处**落 **OS 临时段 49152–65535**（＝ **Docker 随机发布容器端口用的同一段** ⇒ 别的会话的容器先占住 ⇒ `EADDRINUSE` ⇒ 环境性抖动）—— **8 处** `@SpringBootTest` 写 `"grpc.server.port=0"` ＋ **1 处** `ArticleRpcRuntimeBootGateTest.java:185` 的 `new ServerSocket(0)`（真 fat jar 进程口）。
⚠️ **那一行当时写着「需要 global 段表先分配一段（预留池 `22200–22599` 里挑一段）」⇒ ⇒ 我把它列成「⛔ 等 G4 分配」⇒ ⇒ 那是误判** ⇒ ⇒ **global 段表 §4.10.19.3 早就把 `22400–22499` 预分配给 article 域**（状态「本层预分配 · **域侧未落码**」）⇒ ⇒ **本项本来就是域侧自己的活**（AGENTS.md §5.3d「域侧能自修的域侧修」）。

#### 落码（9 处全迁 ＋ 1 个机制类 ＋ 1 份守门）

| 项 | 值 |
|---|---|
| 机制类 | **新增** `service/article/article-rpc/src/test/java/.../support/RealGrpcPort.java`（`FIRST=22400` / `LAST=22479` ＋ **双地址 bind 自检** `0.0.0.0` ＋ `127.0.0.1` ＋ `holders(port)` 诊断）|
| 守门 | **新增** `support/RealGrpcPortTest.java` ⇒ **6 条** = 选取逻辑 **4 条** ＋ **源扫 2 条** |
| 迁 ① | **7 处** `@SpringBootTest` 的**属性形态** `"grpc.server.port=0"` ⇒ 从 `properties` 里**删掉**该行 ＋ 在**已有的** `@DynamicPropertySource` 方法里注册 `r.add("grpc.server.port", RealGrpcPort::pick)` |
| 迁 ② | **1 处** `ArticleRpcRuntimeBootGateTest.java` 的 `freePort()` ⇒ `RealGrpcPort.pick()`；**删掉 `freePort()` 方法与 `java.net.ServerSocket` import** |
| 迁 ③ | **1 处** `ArticleSchemaGateStartupOrderTest.java`（**进程参数形态**：`app.run("--grpc.server.port=0", …)`）⇒ `"--grpc.server.port=" + RealGrpcPort.pick()` |
| 迁 ④ | **4 处** `ServerBuilder.forPort(0)` ⇒ `forPort(RealGrpcPort.pick())`（`ArticleGrpcInterceptorWiringTest` 2 ＋ `ArticleRpcSlowThresholdGateTest` 2）|

🔴 **为什么是「扩展已有的 `@DynamicPropertySource` 方法」而不是新增一个** ——
Spring 的 **context cache key** 包含 `properties`、但**不含 `@DynamicPropertySource` 的值** ⇒ ⇒
若本类的 properties 与同模块另一个 `@SpringBootTest` **恰好相同** ⇒ ⇒ 两者**共享同一个 Spring 上下文** ⇒ ⇒
共享上下文里只会有**一个** gRPC server（端口由**先建上下文的那个类**的 `@DynamicPropertySource` 决定）。
⇒ ⇒ **本轮刻意把 8 个类的 properties 里的 `port=0` 那一行【同时删掉】** ⇒ ⇒ 它们的 properties **互相仍然相同** ⇒ ⇒
**上下文共享关系与迁移前逐字相同**（零行为变化）⇒ ⇒ 端口由**第一个建上下文的类**从本域专用段挑一个 ⇒ ⇒ **全程零 OS 临时端口**。
⚠️ **踩过的坑（已修）**：迁移脚本的守恒断言第一版写的是「改完源码里不得再出现 `grpc.server.port=0` 这个**子串**」⇒ ⇒ **它把自己新写的说明注释也判成违规** ⇒ ⇒ 改成只认**属性字面量形态**（带引号+逗号）⇒ ⇒ **判据要能区分「代码」与「散文」**。

#### 段内再切一刀（与 comment 域同款约定）

| 段 | 用途 | 谁用 |
|---|---|---|
| `22400–22479`（80 口） | **真进程 gRPC server 端口** | `RealGrpcPort.FIRST/LAST` |
| `22480–22499`（20 口） | **守门脚手架段**（造「已被占」样本）| `RealGrpcPortTest.SCRATCH_FIRST/LAST` |

⇒ ⇒ **必须不相交的理由**：全量套件里**上一个测试类的 gRPC server 还活着**（Spring 缓存测试上下文）⇒ ⇒ 若守门去占**生产段里的**口 ⇒ 就可能撞上那个还活着的 server ⇒ ⇒ **守门自己的样本会 `BindException`** ⇒ ⇒ 守门被环境左右 ⇒ ⇒ 所以守门段与生产段**零重叠**（同 comment 域的 `RealGrpcPortTest`）。
⇒ ⇒ 与本域**容器宿主口段 `22850–22879`**（§二十三.2）**零重叠** ⇒ ⇒ 两个机制互不抢口。
⇒ ⇒ **零跨域协调**：全部落在 global 段表**已预分配给 article 域**的那一段内。

#### 📌 口径订正（2026-10-28 · G3）：**A5 行那「9 处」只数了「Spring 上下文形态」** ⇒ 本域实迁 = **13 处**

⚠️ **被订正的是 `docs/DRIFT/article/vs-common.md` 的 **A5 行**（2026-10-27 G3 实测补登 · 已提交）——**那一行保留不改写** ⇒ ⇒ **它写的「9 处」要按本注记读**。
⇒ ⇒ **A5 行那 9 处** = **8 处** `"grpc.server.port=0"` ＋ **1 处** `freePort()`（`ArticleRpcRuntimeBootGateTest.java`）⇒ ⇒ **漏了 4 处同域、同一个 OS 临时段的** `ServerBuilder.forPort(0)`（**进程内**起 gRPC server，不是 Spring 上下文）
⇒ ⇒ 实测 4 处 = `ArticleGrpcInterceptorWiringTest.java` **2 处** ＋ `ArticleRpcSlowThresholdGateTest.java` **2 处**
⇒ ⇒ ⇒ **本域本轮实迁 = 9 + 4 = 13 处**（本节标题与上表按 **13** 写）。
⚠️ **上表「迁 ①」的口径**（A5 行表述略含糊，此处钉死）：**7 处**是 `@SpringBootTest(properties=…)` 里的**属性形态**
（`ArticleGrpcInterceptorWiringTest` · `ArticleJsonbResultMapWiringTest` · `ArticleNotNullUpdateStrategyWiringTest` ·
`ArticleRpcSlowThresholdGateTest` · `ArticleSpringContextWiringTest` · `ArticleTransactionWiringTest` ·
`service/ArticleNullJsonbToProtoMappingTest`）**＋ 1 处**是**进程参数形态**（`ArticleSchemaGateStartupOrderTest` 的 `--grpc.server.port=0` ⇒ 归「迁 ③」）。
⚠️ **为什么这条订正重要**：若按 A5 行去核「清零了没有」⇒ ⇒ **会漏掉这 4 处** ⇒ ⇒ **A5 行需按本注记读**。

#### 🔴 守门最后两条是「源扫」—— 才是真正**堵住缺口**的部分

| 守门 | 断言 |
|---|---|
| `noTestSourceFallsBackToTheEphemeralPortSegment` | 本模块测试源码（**剥注释后**）不许再出现 **`grpc.server.port=0`** / **`--grpc.server.port=0`** / **`new ServerSocket(0)`** / **`freePort(`** ⇒ ⇒ **三种回潮形态一次堵住**（属性形态 / 进程参数形态 / 辅助方法形态）|
| `everyGrpcServerPortDeclarationGoesThroughRealGrpcPort` | 凡（剥注释后）提到 `grpc.server.port` 的文件**必须**引用 `RealGrpcPort` ⇒ ⇒ 「新增一个测试类却忘了走专用段」会被抓住（**守恒**）|

⚠️ **禁止清单刻意用拼接构造**（`"grpc.server.port=" + "0"`）⇒ ⇒ 这些字面量**不会连续出现在守门文件里** ⇒ ⇒ **源扫不会把自己扫成违规**（否则守门会永远红在自己身上）。
⇒ ⇒ 复用同包的 `RealContainerHostPortTest.stripComments(...)`（**剥注释的状态机**，**不动字符串字面量** ⇒ §二十三.3 纪律 ①）⇒ ⇒ **不重复实现**。

⚠️ **一条诚实的边界（TOCTOU）**：`pick()` 返回时口是空闲的，但**被测进程真正 bind 它之前**存在窗口 ⇒ ⇒ 本类**只能把概率压低、不能归零** ⇒ ⇒ 这正是「本段独占给本域」的意义（而不是「大家共用一个大段、各自碰运气」）。

**证据强度**：**L3**（源码静态 ＋ 守门用例 ＋ 源扫）⇒ ⇒ 与 Go 侧的一致性 **N/A**（**测试基础设施**，不涉及任何运行时行为 ⇒ 不属 §3.1 语义对齐范围）。
📌 **2026-10-28 升 L4（本域侧）**：本域新守门（`support/RealGrpcPortTest` **6 条**）在两轮真回归里**全绿** —— 第 1 跑（12:1x）`article-rpc` **183 / 0 / 0 / 0**；第 2 跑（12:40）**判据改动后仍 183 / 0 / 0 / 0** ⇒ ⇒ 「源扫 + 守恒」这条新判据**在 article 域已跑绿并被验过**（⇒ ⇒ 改判据这一步本身有 L4 支撑，不是「改完就信」）。

---

## 二十四、🔴 **G4 共享层合并后的门禁快照（20 条）**（2026-10-28 · G3）

> **canonical 就在本节**（本域写权面 ⇒ 本节即权威）· 核对日期 **2026-10-28**
> · 合并 commit **`ae218df`**（option D · 单一 merge 源 · **不 cherry-pick** · `git merge-tree --write-tree` 预演 **0 冲突**）
> · G4 源 = `align/g4-service-common` @ **`188e995`** ⇒ ⇒ **G4 未触碰四域任何路径**（`git diff --stat` 空）

### 24.1 合并后全量回归（12 域模块 ＋ `-am` 带进来的共享层）

**1387 条 / 0 failures / 0 errors / 4 skipped** · BUILD SUCCESS · **15:30 min**（2026-10-28）

| 归属 | 模块 | 条数 |
|---|---|---|
| **域侧（4 域 12 模块）** | `article-rpc` 177 · `article-api` 99 · `article-common` 15 · `comment-rpc` 136 · `comment-api` 52 · `comment-common` 27 · `favorite-rpc` 131 · `favorite-api` 131 · `favorite-common` 29 · `hot-rpc` 108 · `hot-api` 96 · `hot-common` 14 | **1015** |
| **共享层** | `service/common` **333** ＋ `service/users/users-common` **39** | **372** |
| **合计** | | **1387** ✅ 逐字相等（`1015 + 372 = 1387`）|

📌 **这一轮 1387 【已经含】favorite-api 段数闸门那 +5 条真 Tomcat 守门**（时间线：`ae218df` = **03:18:21** ⇒ 回归 **03:34 起** ⇒ 同一轮里 `FavoriteApiRealTomcatGateTest` = **13 条**）
⇒ ⇒ ⇒ **不需要为了落码再重跑一次全量**（落码**前**的 favorite-api = 126，那个数**从未**出现在合并后那轮里）
⇒ ⇒ **纪律**：「合并后回归」**不等于**「合并前 + 落码」的加法 ⇒ ⇒ **要去日志里数那一次的实际条数**
（`… -- in <测试类>` 行）⇒ ⇒ **不许用「前一次 + 新增条数」推算**。

⚠️ **聚合口径的两个坑**（写进台账是因为下一轮还会踩）：

1. **`-am` 会把依赖闭包一起带进 reactor** ⇒ 本次目标 12 个模块，reactor 实测 **23** 个
   （`users-common` / 各域 pom 聚合模块都被带进来）⇒ ⇒ **拿 reactor 全量求和 ⇒ 得 1061，不是 1387**
   ⇒ ⇒ **聚合前必须把「目标模块清单」显式写死**，并把非目标模块单列；
2. **maven 的 `Tests run: N, …` 有两种行**：**模块级汇总行**（前面是 `[INFO] Results:`）与
   **每类用例行**（形如 `… -- in com.rag.Foo`）⇒ ⇒ 过滤条件写成 `- in `（单横线）⇒ ⇒
   **每类行被当成模块级汇总行 ⇒ 得 2122（= 1061 × 2）** ⇒ ⇒ **过滤必须用 `-- in `**；
3. **自检规则**：**总数恰好是真实数的整数倍 ⇒ 必是重复计数**。

### 24.2 门禁 20 条：**15 绿 / 4 红 / 1 用法错**（**四条红都不在四域**，逐条点名）

> 📌 **订正（2026-10-28 · G3 · 我自己记账时踩的）**：本节初稿写的是「**16 绿 / 4 红**」⇒ ⇒ **错**
> ⇒ ⇒ 那个 16 是「20 − 4」**减出来的** ⇒ ⇒ 它把 **`check-merge-tree-content.py` 的 `EXIT=2`（无参数 ⇒ 用法错）
> 当成了绿** ⇒ ⇒ **EXIT=2 既不是绿也不是红**（**根本没跑**）⇒ ⇒ 正确口径 = **15 绿 / 4 红 / 1 用法错**。
> ⇒ ⇒ **教训**：「总数 − 红数」**不等于**「绿数」⇒ ⇒ 必须**逐条数 `EXIT=0` 的个数**（同族 = 「总数变了不是结论」）。

#### 📌 ⚠️ **两个「333」是【不同的量】—— 收口时别混**（2026-10-28 10:3x · G3）

| 口径 | 值 | 怎么测的 | 谁负责 |
|---|---|---|---|
| **① 台账编号式条目数** | **333** | `python3 tools/check-ledger-count.py` ⇒「条目（编号式）: 333」⇒ 索引仍称 315 ⇒ **差 +18** | **合并收口执行者**统一 `--fix`（⛔ 各 lane 不各自 fix） |
| **② `service/common` 的测试条数** | **333** | G2 的波末权威数字（9 模块 = 1022 = 域侧 689 + 共享层 333）⇒ **独立复核通过** | 共享层（G4 面） |

⇒ ⇒ **两者数值恰好相同、但含义完全无关** ⇒ ⇒ **台账里凡出现 333，必须标明是哪一个** ⇒ ⇒ 否则下一棒会以为「台账条目数 = 共享层测试条数」而推错账。
⇒ ⇒ **G2 的权威值（共享层 333）与本域 12 模块拆账逐字自洽**：`1015`（域侧 12 模块）＋ `333`（`service/common`）＋ `39`（`users-common`）＝ **`1387`** ✅ ⇒ ⇒ **本域口径【不需要】改**（原 **331** / **332** 皆为**台账条目数**的旧值，已各自加订正注记；**与 ① 无关**）。

| 门禁 | EXIT | 归谁 | 成因（必须点名，否则下一轮分不清「真红」与「预期红」）|
|---|---|---|---|
| `check-ledger-count` | 🔴 1 | **合并收口执行者** | 索引 **315** vs 本树实测 **332**（差 **+17** ⇒ 台账条目数在波次中持续增长）⇒ ⇒ **合并收口时统一 `--fix`** ⇒ ⛔ **各 lane 不各自 `--fix`**（会互相覆盖）⇒ 📌 **订正（2026-10-28 10:3x · G3）—— 332 已过期**：本轮 G3 又新增了台账内容（`PIT-FAV-API-016` + `PIT-FAV-RPC-040` 订正节 + 2 个子节）⇒ ⇒ **重测 = 333（差 +18）** ⇒ ⇒ **原 332 保留不改写**（§24.2 表内）|
| `check-ddl-static` | 🔴 1 | **users 域** | users 域既知 **9** 处 DDL 差异（**类型/长度 0 差异**，其余为可空性 / `DEFAULT CURRENT_TIMESTAMP` 残留 / 缺 3 条非唯一索引）⇒ ⇒ **不在四域** ⇒ ⇒ 本域零改动 |
| `check-tomcat-limits` | 🔴 1 | **G2 域（task-api）** | `task-api` 的 `mode=raw` 应为 `off` ⇒ ⇒ **不在四域** ⇒ ⇒ 本域零改动 |
| `check-wave-coupling` | 🔴 1 | **G4-9（共享矩阵）** | `MqConsumer` 快照 `['comment','hot','task']` vs 现状 `['article','comment','hot','task']` ⇒ ⇒ **只差 `article` 一项** ⇒ ⇒ 落点 `docs/DRIFT/_cross/cross-domain-coupling.md` ⇒ ⛔ **G3 不写（共享面）** ⇒ ⇒ **等 G4 更新** |
| `check-merge-tree-content` | ⚪ **2（用法错）** | —— | **无参数运行** ⇒ `EXIT=2`（用法错）⇒ ⇒ **不是红项** ⇒ ⇒ 带四个 lane 参数运行 = **EXIT=0 / 问题 0 / 不可信 0**（合并树内容完整）⇒ ⇒ 判定时**先看有没有带 lane 参数** |
| 其余 **15** 条 | ✅ **EXIT=0** | 本域 | —— |

⚠️ **`tools/difftest/check-merge-tree-content.py`（G4 新增）**：按四 lane 顺序合并 ⇒
**EXIT=0 · 问题 0 · 不可信 0** ⇒ ⇒ **合并树内容完整**（逐文件 blob 比对 = 三方并集）
⇒ ⚠️ **无参数运行 = EXIT=2（用法错）**，**不是红项** ⇒ ⇒ 判定时先看有没有带 lane 参数。

### 24.3 🔴 `MAX_PARAMETER_COUNT = 10001` 是【设计值】，不是漂移（**订正一则期待**）

⇒ ⇒ canonical 论证（含 Go `net/url/url.go:1001/1004/1020` 逐行 · 共享层 javadoc 出处 · 域侧修法）
⇒ ⇒ 见 [`docs/DRIFT/favorite/api.md`](../favorite/api.md) **§21.3**
⇒ ⇒ **一句话**：Go 放行 **10000** 段、第 **10001** 段报错 ⇒ ⇒ 容器上限取 **10001**（= 上限 + 1）
才能让**应用层**先按 Go 语义拒 ⇒ ⇒ **改成 10000 会让 Tomcat 比 Go 更早拒 ⇒ 反而制造漂移**
⇒ ⇒ **「等共享层改 10000」的期待作废** ⇒ ⇒ **残差那一格归 favorite 域侧自装判据**（已落码，见 §21.4）。

### 24.4 📌 四域指针（各域台账里那一节）

| 域 | 台账里的那一节 | 本域 3 模块条数 |
|---|---|---|
| article | [`docs/DRIFT/article/rpc.md`](./rpc.md) 本节 | 177 + 99 + 15 = **291** |
| comment | [`docs/DRIFT/comment/rpc.md`](../comment/rpc.md)「📌 快照 · 🔴 G4 共享层合并（`ae218df`）后的本域回归」 | 136 + 52 + 27 = **215** |
| favorite | [`docs/DRIFT/favorite/rpc.md`](../favorite/rpc.md)「📌 快照 · 🔴 G4 共享层合并（`ae218df`）后的本域回归」 | 131 + 131 + 29 = **291** |
| hot | [`docs/DRIFT/hot/rpc.md`](../hot/rpc.md)「📌 快照 · 🔴 G4 共享层合并（`ae218df`）后的本域回归」 | 108 + 96 + 14 = **218** |
| | **四域合计** | **1015** ✅（+ 372 共享层 = 1387）|

### 📌 指针（2026-10-28 · G3）—— G4 §4.10.64 ⑤ 的「域内 status 漂移」警告【对 article 域不成立】

⇒ G4 在共享面写「域内 span 若只调 `recordException` ⇒ status 恒 UNSET ⇒ **与 Go 的 `RecordError` 漂移**」⇒ ⇒ **中间那一步是错的**：Go otel-go **v1.44.0** 的 `RecordError` **【不改】status**（接口契约 `go.opentelemetry.io/otel/trace@v1.44.0/span.go:47-51`「**as this method does not change the Span status**」＋ SDK 实现 `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:626-630` javadoc ＋ `:631-655` 实现**全程无 `SetStatus` 调用**）⇒ ⇒ **Go 侧 `service/article/**` 的 `SetStatus` 零命中 ⇒ Go 侧业务 span status 本就恒 UNSET** ⇒ ⇒ **RAG 侧 `ArticleSpans` 零 `setStatus`（类注释 `:81`「本类**不得**调 `setStatus`」）⇒ 两侧一致 ⇒ 不漂移** ⇒ ⇒ **域侧零改动**（**不补 `setStatus`** —— 补了反而是引入 Go 侧不存在的 ERROR）。
⇒ 且守门 `ArticleSpanWiringTest.java:448-449` **已把这条钉成断言**（⇒ 断言 `StatusCode.UNSET`，注释「Go 侧无 `SetStatus` ⇒ 状态保持 UNSET」）⇒ ⇒ **网已张、语义已对齐**（L-10 落码明细 ⇒ [`article/vs-common.md` §3.2](./vs-common.md#32-✅-l-10-已落码2026-10-28--g3-8-个-span6-logic--2-consumer全部补齐)）。
⇒ 逐条证据 ⇒ [`favorite/api.md` §16.4.1](../favorite/api.md#1641-🟢-补证2026-10-28-go-侧-recorderror-也不改-status--两侧-status-语义同构--status-漂移不成立)。

---

# 2026-10-29 第十二轮（G3 lane · `align/r3-article-rpc-close`）：`article-rpc` **R2 → R3**

> **本节是本文件当前的最新一轮** ⇒ 按文件头的「📌 怎么读这份台账」：
> **判定状态一律回本节 + §9**（上文各轮次表都是历史快照）。
>
> **本轮目标**（`docs/PhaseGoal-judge.md` §三 A-2 的 `article-rpc` 行 = **R2 / 🟡** ⇒ 阻塞 C-1「核心 6 域」）：
> 把 `article-rpc` 提到 **R3 / ✅**。

## 12.0 开工前置回执（AGENTS.md §5.3b-0 · 四步）

| 步骤 | 内容 |
|---|---|
| ① 先定自己的任务 | 把 `article-rpc` 从 **R2** 提到 **R3**（判定口径 = `docs/PhaseGoal-judge.md` §1.1 / §1.3 / §1.5 + §七 RPC 型 5 条 + §九） |
| ② 读完四档（本域 + `_cross`） | `docs/DRIFT/article/`（本文件 · `api.md` · `common.md` · `proto.md` · `vs-common.md`）· `docs/Intentional_Deviations/article/rpc.md` · `docs/PhasedGoal.md` · `docs/twoStage.md` §5.3/§5.4/§10 · `docs/PhaseGoal-judge.md` §1.1/§1.3/§1.5/§三/§七/§九 · `docs/Pitfalls/article/` + `docs/Pitfalls/global/cross-domain.md` |
| ③ 回执「读完了」 | 本表 |
| ④ 经用户批准才开工 | ⏳ **批准记录：＿＿＿＿＿＿＿＿（留空 —— 由 Jason 填；本 lane **未代批**、未自行填「已批准」）** |

**本轮计划**（批准后执行）：① 复核 `S-5` 的前提并改判 ② 销号 `U-4′` ③ 用**真 Kafka** 把 `U-3`/`U-4`/`U-9` 从「源码级」升到「运行级」④ 逐条核 §七 RPC 型 5 条，缺的补测试 + 变异必红 ⑤ 结论落本节。

---

## 12.1 🔴 `S-5` 改判：**范围外（judge.md §1.1 R0 口径）⇒ 不计入本组件必相对齐项**（原写「⛔ 阻塞」）

**原台账写法**（2026-10-23 轮次表 `:95` ＋「尚未处理」`:161`）：「⛔ **被阻塞**（security 域未迁移）」。

**复核结论：前提成立，但「阻塞」这个措辞是错的 ⇒ 改判为「范围外」。**

| # | 复核点 | 实测结果 | 出处 |
|---|---|---|---|
| 1 | Go 真源**有没有** security 域？ | **有**！`service/security/rpc/`（`security.go` · `internal/logic/contentsecurityservice/sanitize_content_logic.go` · `internal/logic/imagesecurityservice/detect_image_ad_logic.go` · `client/contentsecurityservice/content_security_service.go`）⇒ ⇒ judge.md §1.1 R0 行写的「Go 侧**无**对应件」对 security **不准确**（那三个域里另两个本 lane 未判，不越界下结论） | `Sea-RideTheWind_golang/service/security/rpc/`（实测 `ls`） |
| 2 | 那本仓为什么不做？ | **用户决策，不是「待办」**：`AGENTS.md:43`「Go 侧 `knowledge`/`points`/`security` **范围外 ⇒ 不是待办**」 | `AGENTS.md:43` |
| 3 | judge.md 的 R0 口径认不认？ | **认**：§1.1 的 **R0 行**把 `knowledge`/`points`/`security` **三域点名** ⇒ security 就是 R0 三域之一 | `docs/PhaseGoal-judge.md:46` |
| 4 | Go 侧的 article-rpc 真的调它吗？ | **调**：`internal/svc/service_context.go:60` `zrpc.MustNewClient(c.SecurityConf)` · `:72` `SecurityRpc:` · `:73` `ImageSecurityRpc:` · `internal/mqs/article_consumer.go:158` `SanitizeContent` · `:253` `DetectImageAd` | 同左（Go 真源行号） |
| 5 | RAG 侧的对应实现是什么形状？ | **窄端口 + fail-loud 兜底**：`ContentSecurityPort`（2 个方法 · 签名逐字段照 Go 调用点对齐）· `ContentSecurityAutoConfiguration`（`@ConditionalOnMissingBean` **条件注册** ⇒ 判断时机确定）· `UnavailableContentSecurityClient`（**恒抛**） | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/` |
| 6 | 两侧的**可观察行为**一致吗？ | **一致**：Go `internal/mqs/article_consumer.go:154-155` `if l.svcCtx.SecurityRpc == nil { return fmt.Errorf("content security client not initialized") }` ⇒ **Go 自己在 security 不可用时也要求审核失败**（fail-loud ⇒ 文章卡在 REVIEWING）⇒ ⇒ RAG 的兜底实现**逐字复刻**了这条（错误文案逐字相同）⇒ ⇒ 两侧**都**把文章留在 `REVIEWING` | Go `article_consumer.go:154-155` ＋ RAG `UnavailableContentSecurityClient.NOT_INITIALIZED` |

⇒ **改判**：`S-5` **不是**「被阻塞」（那个措辞暗示「等 security 域迁完就能闭环」⇒ **而 security 域按用户决策永远不迁** ⇒ 等下去是**永不**闭环）。
⇒ **按 judge.md §1.1 的 R0 口径**（范围外 ⇒ 本仓不实现）**改判为「范围外」** ⇒ 按 **§1.1 ⑥ 第 2 类**（落点在**别的域**的跨组件残留）⇒ **不计入 `article-rpc` 的必相对齐项** ⇒ **不阻断 R3**。
⇒ ⚠️ **同时按 §1.1 ⑥ 的效力边界**：这**不等于**「两侧行为零差异」—— 能力差（Go 能审、RAG 不能）是**事实**，只是它的**落点在 R0 域** ⇒ 计入**域级聚合**与**矩阵 C**，**不计入本组件 R**。
⇒ ⚠️ **「移除条件」的措辞已订正**（2026-10-29 · 本轮）：原文写「若将来 security 域落地…」⇒ 那个措辞暗示「等它迁进来就能撤掉」⇒ **但 security 域按用户决策不会落进本仓** ⇒ ⇒ 本兜底实现**不是临时占位，而是本仓的最终设计**（`ContentSecurityAutoConfiguration` / `UnavailableContentSecurityClient` 的类注释已同步订正）。
⇒ 🔴 **顺带修掉一处 §3.3.2 形态的「不存在的证据」**：`ContentSecurityAutoConfiguration` 的类注释原文写「锁死用例：`ArticleContentSecurityWiringTest`」⇒ **该类全仓不存在**（`find service -name "ArticleContentSecurityWiringTest.java"` ⇒ **0 命中**）⇒ 已改为真实的 `ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud`。

---

## 12.2 ✅ `U-4′` **销号**（双侧活体对拍 **0 条 → 3 场景逐字节 ＋ 两侧播种同一行**）

| 项 | 内容 |
|---|---|
| **探针落点** | `tools/api-full-parity-probe/article/`（`run.sh` ＋ `mutate-parity.sh` ＋ `expected.txt` ＋ `README.md`）—— **在版本控制内**（`git log`：`4edfdc7e` 落码 · `abd167dc` 修基建两处真缺陷）⇒ **可复算**（不是 `/tmp` 里的临时脚本） |
| **判定命令** | `bash tools/api-full-parity-probe/article/run.sh` ⇒ **EXIT=0**<br>（退出码口径：0 = 两侧逐字节一致 · `1` = 有差异 · `2` = 环境问题 / 真源漂移 / 依赖缺失） |
| **实测场景** | **3 个**：`list_articles`（`GET /v1/articles`）· `get_article_exists`（`GET /v1/article/9007199254740993`）· `get_article_missing`（`GET /v1/article/does-not-exist`）⇒ **Go == RAG == 冻结期望值** 逐字节一致（期望值在 `expected.txt`，格式 `CASE|<name>|<hex>`） |
| **两侧播种同一行** | 两侧**各建各的库**（Go 侧真源 DDL `article.sql` ＋ `002_community_revision.sql` · RAG 侧自己的 Flyway）⇒ 各自**播同一行**（`status=2 PUBLISHED` · id `9007199254740993`）⇒ ⇒ 探针比的不是「两边都空」 |
| **变异** | `mutate-parity.sh`：M1（去掉 `Article` 的 `@JsonPropertyOrder`）· M2（去掉 `ListArticlesResp` 的 `@JsonPropertyOrder`）⇒ 精确转红；M3（改 `expected.txt` 里一枚 hex）⇒ `run.sh` **EXIT=1** ⇒ **探针非假绿** |
| **证据强度** | **L4（双侧活体）** —— 两侧服务**同时**在跑（Go 真源栈 ＋ RAG 栈），同一请求比**字节** |
| **核对日期** | **2026-10-29** |

> ⚠️ **口径边界（不许越界读 —— 这条比销号本身更重要）**：
> ① **`get_article_exists` 钉的是「降级路径两侧一致」，不是「取到了文章」** —— 宿主上 `user.rpc` **两侧都不可达** ⇒ 单条详情端点两侧**同样降级成 `5001`**；`list_articles` 的 `author_name` = `"用户 1001"` ⇒ **也是降级路径**（user 侧不可达时的兜底名）。
> ② ⇒ **成功路径的完整字段语义仍未双侧验证**（要补需让两侧的 `user.rpc` 都可用）。
> ③ ⇒ judge.md §九 G-3 原文要求「article 域双侧活体 **0** 条」⇒ 现 **3** 条 ⇒ **部分闭合**，**不得写成全闭合**。

---

## 12.3 ✅ `U-3` ＋ `U-4` **闭合**（真 Kafka 实测 · **行为断言**，不是属性断言）

**新增守门**：`service/article/article-rpc/src/test/java/com/rag/article/article/rpc/mq/ArticleKafkaForceCommitGateTest.java`
（真 Kafka 容器 `apache/kafka:3.7.2` ＋ 真 PG；被测对象是**生产装配本身** —— 生产的 `ArticleRpcApplication#articleNoCommitErrorHandler()` ＋ 生产的 `articleKafkaListenerContainerFactory` ＋ 生产 `application.yml` 绑出来的 ConsumerFactory；**只有 listener 本身**是探针）。

### 12.3.1 实测结论

| 问 | 结论 | 证据 | 强度 |
|---|---|---|---|
| `U-3` 失败消息是「丢弃」还是「进程内无限重投」？ | **丢弃**（不重投） | `failedMessageIsNotRedeliveredInProcess`：投递一条**必然失败**的消息 ⇒ 5s 后投递计数**恰好 +1** | **L4**（真 broker 位点 + 投递计数） |
| `U-3` 失败位点推不推进？ | **推进，且推进到「这条失败消息自己的 offset+1」** | `failedMessageOffsetIsCommittedLikeGoForceCommit`：读消费组已提交位点 ⇒ `committed == boomOffset + 1`（**精确相等**，不是「≥」） | **L4** |
| `U-4` S-3 的**依据**成立吗？ | **行为成立，但原先的归因错了** ⇒ 已订正（见 §12.3.2） | 同上两条行为断言 | **L4** |

### 12.3.2 🔴 `U-4` 的**归因订正**（本轮最实质的一条）

台账 §9 U-4 原文写「引用的 `commit(consumer, container, offset+1)` 那段代码**不在本模块的执行路径上** ⇒ 只在 `commitRecovered=true` 时执行」。
⇒ **实测复核：结论成立，但给出的理由只覆盖了其中一半。**

spring-kafka 4.0.7 里有**两个不同**的 `commit(offset+1)` 落点：

| # | 位置 | 把门条件 | 本模块走不走 | 理由 |
|---|---|---|---|---|
| ① | `SeekUtils.java:229-233` | `commitRecovered` ＋ 要求 `AckMode.MANUAL_IMMEDIATE` | **不走** | 本模块是 `RECORD` 且从未 `setCommitRecovered(true)` ⇒ **台账给出的理由，对这一条成立** |
| ② | `FailedBatchProcessor.java:255-266` | `isSeekAfterError()`（**不需要** `commitRecovered`、**不需要** `MANUAL_IMMEDIATE`） | **也不走** | ⚠️ **台账没提这一条**；它不走是**另一个理由**（下） |

**② 为什么不走**（本轮的实测 + 源码逐行核对）：

```
FailedRecordTracker.java:102      this.noRetries = backOff.start().nextBackOff() == BackOffExecution.STOP;
                                  ⇒ FixedBackOff(0L, 0L) ⇒ noRetries = true
FailedRecordTracker.java:174-177  if (this.noRetries) { attemptRecovery(...); return true; }
                                  ⇒ 失败记录**第一次就判为 recovered** ⇒ 被移出 remaining
FailedBatchProcessor.java:256     if (!remaining.isEmpty()) {   ← ② 的守卫
                                  ⇒ remaining 为空 ⇒ ② 里的 commit(offset+1) **不执行**
```

⇒ **真正的提交路径是「容器侧的 RECORD ack」**（与 `isSeekAfterError()` **无关**）：

```
KafkaMessageListenerContainer.java:2897  commitOffsetsIfNeededAfterHandlingError(cRecord);
KafkaMessageListenerContainer.java:2925  private void commitOffsetsIfNeededAfterHandlingError(...)
KafkaMessageListenerContainer.java:2936      ackCurrent(cRecord, this.isManualAck);
KafkaMessageListenerContainer.java:3196      if (this.isRecordAck && this.producer == null) {
KafkaMessageListenerContainer.java:3197          Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = buildSingleCommits(cRecord);
KafkaMessageListenerContainer.java:3199          commitOffsets(offsetsToCommit);
⇒ 提交的正是**这条失败记录自己的 offset+1** ⇒ 与 Go kq/queue.go:240 同构
```

⇒ 🔴 **实测结论（MUT-M2）**：`setSeekAfterError(true)` **不是承重项** —— 改成 `false` ⇒ **行为完全不变**（失败消息仍只投递 1 次、位点仍提交为「它自己的 offset+1」）⇒ ⇒ **承重的是 `FixedBackOff(0L, 0L)`**（MUT-M1 证明）。
⇒ ⇒ **生产 javadoc**（`ArticleRpcApplication#articleNoCommitErrorHandler()`）已按本结论**改写**（原文把机制归到了 `isSeekAfterError()` 分支 ⇒ **错的**）。

### 12.3.3 Go 出处（逐条指到**消费点**，不是声明点）

```
声明点  go-queue@v1.2.2/kq/config.go:24    ForceCommit bool `json:",default=true"`
声明点  go-queue@v1.2.2/kq/config.go:19    Processors int `json:",default=8"`
消费点  go-queue@v1.2.2/kq/queue.go:230    if err := q.consumeOne(ctx, string(msg.Key), string(msg.Value)); err != nil {
消费点  go-queue@v1.2.2/kq/queue.go:231-233     if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }
消费点  go-queue@v1.2.2/kq/queue.go:235-237     if !q.c.ForceCommit { continue }    ← ForceCommit=true ⇒ 不 continue
消费点  go-queue@v1.2.2/kq/queue.go:240     if err := q.consumer.CommitMessages(ctx, msg); err != nil {
⇒ 失败消息**照样提交自己的位点** ⇒ 被丢弃；且 Go 侧**没有重试循环** ⇒ 不会进程内重投
```

⇒ ⚠️ `service/article/rpc/etc/article.yaml` **没有**配 `ForceCommit` / `Processors` / `CommitInOrder`
⇒ 生效值 = 默认的 `true` / `8` / `false`（AGENTS.md §3.3.1：默认行为是否生效必须读到**消费该 tag 的那段控制流**）。

---

## 12.4 🟡 `U-9` **拆成两半**：`U-9a` ✅ 闭合（offset 提交/跳过语义 · 两侧同构）· `U-9b` 🟡 **取证缺口**

### 12.4.1 ✅ `U-9a`（offset 提交 / 跳过语义）—— **两侧同构，闭合**

| 侧 | 事实 | 出处 | 强度 |
|---|---|---|---|
| Go | 提交的是「**这条消息自己的位点**」 | `go-queue@v1.2.2/kq/queue.go:240` `CommitMessages(ctx, msg)` | E2（源码 · 消费点） |
| Go | 同一分区多次提交取 **max** ⇒ 提交位点**单调不回退**（即使 8 个 goroutine 乱序提交） | `kafka-go@v0.4.47/reader.go:171-185` `offsetStash.merge`：`if offset, ok := offsetsByPartition[c.partition]; !ok \|\| c.offset > offset { offsetsByPartition[c.partition] = c.offset }` | E2（源码 · 合并点） |
| RAG | 每分区提交位点 = **该分区最大已投递 offset + 1**（精确相等）＋ **从不回退** ＋ 分区内首次投递按 offset 递增 | `ArticleKafkaForceCommitGateTest#committedOffsetPerPartitionIsMaxDeliveredPlusOneAndNeverRegresses`（真 Kafka · 读消费组已提交位点 + 逐条投递记录） | **L4**（真 broker 运行面） |

⇒ ⇒ **「offset 提交 / 跳过语义」两侧一致**：两边都是「每分区单调、提交 = 该消息自己的 offset+1、已提交的消息被跳过」。
⇒ ⚠️ 这条**新引了一个 Go 侧出处**（`kafka-go` 的合并点）—— 之前台账只引到 `kq/queue.go` 就下了「不保证连续」的结论，**漏了 kafka-go 会取 max** 这一层 ⇒ **「提交不保证连续」的说法在「已提交位点」这个可观察量上是错的**（乱序的是**提交动作**，不是**已提交位点**）。

### 12.4.2 🟡 `U-9b`（处理器并发模型）—— **取证缺口**，**不许写「已对齐」**

| 侧 | 模型 | 出处 |
|---|---|---|
| Go | **1 个拉取者 → 8 个 handler 抢同一个 channel** ⇒ 同一分区的消息**可能**被并发处理（handler 级副作用的顺序**不保证**） | `go-queue@v1.2.2/kq/queue.go:248-259` `startProducers`（`c.Consumers` 个 goroutine 调 `FetchMessage`）＋ `:219-246` `startConsumers`（`c.Processors` 个 goroutine 读**同一个** `q.channel`）<br>⚠️ `service/article/rpc/etc/article.yaml:51` / `:67` 两个消费者都只配了 **`Consumers: 1`** ⇒ **拉取者只有 1 个**，而 `Processors` 没配 ⇒ 默认 **8** |
| RAG | **8 个 consumer 各占自己的分区** ⇒ 同一分区的消息**严格串行** | `ArticleRpcApplication#articleKafkaListenerContainerFactory`：`setConcurrency(8)` ＋ `AckMode.RECORD` |

⇒ **这是一处模型差异，且 RAG 侧更严**（Go 可能乱序，RAG 不会）⇒ **不是**「offset 提交/跳过语义」的差异（那部分已闭合，见 §12.4.1），而是**handler 级副作用顺序**的差异。

> **为什么仍然 Unknown（judge.md §1.1 ⑥ 第 3 类 ⇒ 计入 §九 ⇒ 不阻断 R3）**：
> **Go 侧没有活体对拍** —— 本仓**没有**跑 Go `kq` 的探针 ⇒ 无法量化「同分区并发处理」在 article 的真实业务上（`article-creation-topic` 一次创建一条消息）**是否可观测**。
>
> **解除条件（两条，任一即可）**：
> ① **登记为有意偏离并签字** ⇒ 按 §1.1 ⑥ 第 1 类排除 ⇒ 落点应写进 `docs/Intentional_Deviations/article/rpc.md`（**⚠️ 该文件不在本 lane 的写权内** ⇒ 由主线/G4 落码）；
> ② **补一个 Go 侧活体探针**（真 Kafka ＋ 真 `kq` ＋ 同分区多条消息）⇒ 证明「乱序副作用」在 article 业务上不可观测 ⇒ 则本项转为「已对齐（不可观测）」。
>
> ⚠️ **本 lane 不把它写成「已对齐」**（judge.md §1.1 ⑥ 第 3 类的原文要求）。

### 12.4.3 ⚠️ 本轮**实测到并修掉**的一条 flake（守门自身的问题，不是被测对象的问题）

`ArticleKafkaForceCommitGateTest` 的**第一版** U-9 用例断言「**同一分区的消息永远由同一个消费线程处理**」。
⇒ **实测证伪并改写**：真 Kafka 上消费组在**启动期会连做两次 rebalance**（第一次：某个 consumer 独占全部 8 个分区；第二次：按 `range` 分配器摊成一人一个）
⇒ ⇒ **分区会在消费线程之间搬家** ⇒ 「线程身份」**不是** Kafka 的不变量 ⇒ 拿它当断言 = 埋了一颗 flake。
⇒ ⇒ **改写后的断言**（才是 U-9 真正问的东西）：**每分区提交位点 = 最大已投递 offset + 1** ＋ **提交位点从不回退** ＋ **分区内首次投递按 offset 递增** ＋ **≥2 个消费线程**（证明 `concurrency=8` 真生效）。
⇒ ⚠️ 这条也说明：**「跑绿」与「跑红」都要先判「是不是断言写错了」** —— 本轮 MUT-M2 首次跑出的那条「分区 5 被 2 个线程处理过」**不是** `seekAfterError` 的后果，而是**断言前提错**。

---

## 12.5 §七 **RPC 型 5 条**逐条核（judge.md §三 A-2 的「必达终点指标」）

| # | 判据 | 结论 | 证据（含 Go 出处） | 强度 |
|---|---|---|---|---|
| ① | Go proto 的 `rpc` 方法集合 **≡** RAG `*ImplBase` 覆写方法集合 | ✅ **相等（6 ≡ 6）** | Go `proto/article.proto:127-132`（`CreateArticle` / `GetArticle` / `UpdateArticle` / `DeleteArticle` / `ListArticles` / `UploadFile`）≡ RAG `ArticleServiceGrpcImpl.java:75/81/87/93/99/103`（6 个 `@Override`）<br>判定命令：`python3 tools/difftest/check-surface-parity.py --domain article` ⇒ **EXIT=0**（输出「gRPC 方法集合逐条相等（Go ArticleService=6）」） | E2 + 静态守门 |
| ② | 每方法 **≥1** 条测试 | ✅ **6/6** | `createArticle` → `ArticleDomainServiceSemanticsTest` · `getArticle` → `ArticleSemanticsRegressionTest` 等 · `updateArticle` → `ArticleDomainServiceSemanticsTest` · `deleteArticle` → `ArticleDomainServiceSemanticsTest` · `listArticles` → `ArticleDomainServiceSemanticsTest` · `uploadFile` → `ArticleDomainServiceSemanticsTest`（逐个 grep 命中） | E3 |
| ③ | 每个**写路径**证明事务边界或幂等 | ✅ **本轮补齐最后一条**（创建路径原先只有「静态」证据） | **既有**：`ArticleTransactionWiringTest`（真 PG）—— `@Transactional` 真生效（AOP 代理断言）· 域事件写失败 ⇒ revision/指针/状态**三者全部回滚**（真实回滚）· 同一 `(aggregate_id, aggregate_version)` 不能有两条域事件（幂等）· 公开读计数**能提交**（不在 read-only 事务里）<br>**本轮新增**：`ArticleCreateArticleKafkaFailureBoundaryTest`（真 PG ＋ 死 broker）⇒ **创建路径**的边界被**钉死**：Kafka 推送失败 ⇒ `INTERNAL` ＋ **已插入的行仍在库里**（Go `create_article_logic.go:125-130`「行不回滚」） | **L4**（真 PG） |
| ④ | 下游超时 / 不可用**有明确行为且有断言** | ✅ **本轮补齐 Kafka 那一格** | **既有**：DB/SQL 超时 ⇒ `DEADLINE_EXCEEDED`（S-6，`ArticleSemanticsRegressionTest`）· MinIO 失败 ⇒ best-effort 仍成功（O-3）· security 不可用 ⇒ 抛错重投（S-5）<br>**本轮新增**：**Kafka 不可用** ⇒ `INTERNAL` ＋ 行不回滚（`ArticleCreateArticleKafkaFailureBoundaryTest`）；**Kafka 失败位点** ⇒ 丢弃 ＋ 提交自己的 offset+1（`ArticleKafkaForceCommitGateTest`） | **L4** |
| ⑤ | 业务码经 gRPC 传输后**可还原** | ✅ **适用形态已澄清**：article-rpc **不上线业务码**，发的是**标准 gRPC code**（`article-common` 的 `ErrorCode` 只用于打业务错误日志字段 —— 见 `pom.xml` 的 `article-common` 注释）⇒ 对应的「可还原」判据 = **gRPC status code 可还原** | `ArticleSemanticsRegressionTest`：已带 status 的异常**原样透传**（`DEADLINE_EXCEEDED` 不能变 `UNKNOWN`）· 非 status 的 `RuntimeException` 一律 `UNKNOWN` · `ArticleExceptions.Rpc` 按自带 code 转发（`NOT_FOUND` 不受影响）<br>⇒ 且 **真 wire** 侧有 `ArticleGrpcInterceptorWiringTest`（真 `ServerBuilder.forPort(...)` ＋ 生产的 `GrpcServerConfigurer` ⇒ 真 gRPC server，断言超时**真的**施加到 server 上、deadline **真的**传播到 handler 线程） | E3 + 真 wire |

> ⚠️ **口径**：① 与 ② 是**契约面 / 存在性** ⇒ 支撑 **R2**；**③ ④ ⑤ 才是 R3 ⑤ 要求的「语义断言（Go 出处写进期望值）」** ⇒ 本轮新增的两个守门类**全部**把 Go `文件:行号` 写进了断言消息。

---

## 12.6 变异记录（**变异必红** ＋ **已还原**）

| 变异 | 手法 | 期望 | 实测（2026-10-29） |
|---|---|---|---|
| **MUT-M1** | `ArticleRpcApplication`：`new FixedBackOff(0L, 0L)` ⇒ `new FixedBackOff(0L, 9L)` | 失败消息被**重投** ⇒ 行为断言转红 | ✅ **EXIT=1 · 2 条转红**：<br>· `failedMessageIsNotRedeliveredInProcess` ⇒ 「失败消息被重投了」**投递计数 13 ⇒ 22**（= 重投 9 次）<br>· `containerErrorHandlerIsProductionZeroRetry` ⇒ `noRetries != true`<br>⚠️ **绝对基数随当轮 warmup 投递数浮动**（实测见过 **13** 与 **38** 两个基数 ⇒ 另一轮观测为 38 ⇒ 47）⇒ **「多出 9 次」才是稳定量**（`FixedBackOff(0L, 9L)` ⇒ `maxAttempts=9` ⇒ 该条消息共投递 10 次） |
| **MUT-M2** | `ArticleRpcApplication`：`handler.setSeekAfterError(true)` ⇒ `false` | **行为不变**（该属性**不是**承重项）⇒ 只有**装配面**断言转红 | ✅ **EXIT=1 · 恰好 1 条转红**（用**改写后**的测试复跑）：`containerErrorHandlerIsProductionZeroRetry`（装配面）⇒ U-3 / U-4 的两条**行为**断言**全绿** ⇒ 与 §12.3.2 的机制推导**一致** |
| **MUT-M3** | `ArticleDomainService#createArticle` 加 `@Transactional` | 行被**回滚** ⇒ 新守门转红（Go 无事务 ⇒ 回滚就是漂移） | ✅ **EXIT=1 · 1 条转红**：`ArticleCreateArticleKafkaFailureBoundaryTest` ⇒ 「article 表里没有行 ⇒ 插入根本没发生」（expected 1 but was 0） |

⇒ **三个变异全部已还原**（`git diff --stat` 见本轮提交说明；`service/**` 除正式改动外为 0）。
⇒ **真 Kafka 守门的稳定性**：改写后的 `ArticleKafkaForceCommitGateTest` **连续 2 次全绿**（CLEAN-RUN-2 / CLEAN-RUN-3，各 **EXIT=0 · 4/4**）⇒ 改写前那条「线程独占」断言的 flake **已消除**。
⇒ ⚠️ 另记一次**环境**事件（**不是**缺陷）：CLEAN-RUN-1 在**编译阶段**被 `SIGTERM`（`EXIT=143`）打断 ⇒ 重跑即绿 ⇒ 按 AGENTS.md「`Killed: 9` / 信号终止属环境问题」处理，**不写成代码缺陷**。

---

## 12.7 ✅ 可复算的判定命令（judge.md §1.3 要求「今天能跑 ＋ 覆盖 R 级所声称的那一层」）

```bash
# ── ① R3 的语义断言（rpc 组件必须有「Go 出处写进期望值」的语义断言）──
# ⚠️ 下面用**主仓**路径；本 lane 的工作树等价写法（探针与源码都已落仓，两边都能跑）：
#   cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-article-rpc-close
cd /Users/pr/Documents/java&golang/RAG
mvn -o -B -pl service/article/article-rpc -am test \
    -Dtest='ArticleKafkaForceCommitGateTest,ArticleCreateArticleKafkaFailureBoundaryTest' \
    -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
# ⇒ 断言条数：4 + 1 = 5 条；Go 出处写进期望值：5/5
# ⇒ 退出码 0 = 全绿

# ── ② 组件全量回归 ──
mvn -o -B -pl service/article/article-rpc -am test
# ⇒ surefire XML 求和 = 本组件条数

# ── ③ 契约面（支撑 R2 的那一半）──
python3 tools/difftest/check-surface-parity.py --domain article    # ⇒ EXIT=0

# ── ④ 双侧活体对拍（U-4′ 的销号依据）──
bash tools/api-full-parity-probe/article/run.sh                     # ⇒ EXIT=0
bash tools/api-full-parity-probe/article/mutate-parity.sh          # ⇒ EXIT=0 = 变异全部精确转红
```

---

## 12.9 🔴 `S-5` 改判的**连带项**：`ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud` 的**重新评估**

> 台账旧表（`:128`「锁死未完成态」行）写：「该用例断言端口**就是**兜底实现 ⇒ ⏳ 未改（S-5 阻塞未解前可接受，但 security 域落地时必须先删此断言）」。
> ⇒ `S-5` 改判为**范围外**之后，这条的**性质变了** ⇒ 必须重新评估。

### 12.9.1 结论：**保留，但改判其性质**（不是「锁死未完成态」，是「锁死已定稿的设计」）

| 原读法 | 复核后读法 | 依据 |
|---|---|---|
| 「锁死**未完成**态」—— 等 security 域落地就该删 | 「锁死**已定稿**的设计」—— security 域**永不**落地（`AGENTS.md:43`）⇒ 这条断言**没有过期时间** | `AGENTS.md:43`（security 范围外）· `docs/PhaseGoal-judge.md:46`（R0 行点名三域） |
| 断言「端口**就是**兜底实现」= 锁死占位物 | 断言「端口**就是**兜底实现」= 锁住**「审核链路必须 fail-loud」**这条**语义契约**的实现载体 | Go `service/article/rpc/internal/mqs/article_consumer.go:154-155` `if l.svcCtx.SecurityRpc == nil { return fmt.Errorf("content security client not initialized") }` ⇒ **Go 自己在 security 不可用时也要求审核失败** ⇒ RAG 的兜底**逐字复刻**这条 ⇒ 两侧可观察行为一致 |

⇒ **保留该断言**：它守的是一条**安全关键**的不变量（security 不可用 ⇒ 审核必须失败 ⇒ 文章卡在 `REVIEWING` ⇒ **绝不 fail-open 放行未审核内容**）。
⇒ ⚠️ **但把它的「移除条件」改写了**（见 §12.1 末段）：不再写「等 security 域落地就删」，改写为「若（超出本项目范围）security 域落地 ⇒ **先改端口再接** ＋ **先删/改**这条『端口就是兜底实现』断言」。
⇒ 🔴 **顺带修掉一处 §3.3.2 形态的「不存在的证据」**：`ContentSecurityAutoConfiguration` 的类注释原文引用 `ArticleContentSecurityWiringTest` ⇒ **该类全仓不存在** ⇒ 已改为真实的 `ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud`（2026-10-29 本轮落码）。

### 12.9.2 ⚠️ 这条断言**不是**「fail-loud」的唯一守门 —— 别把它当唯一证据

| 断言 | 它守得住什么 | 它**守不住**什么 |
|---|---|---|
| `contentSecurityPortIsExactlyOneAndFailsLoud`（**装配面**） | 容器里**恰好一个** `ContentSecurityPort`（⇒ 不 NPE / 不歧义）＋ 拿到兜底时**调用必抛** | **守不住**「换成真实现后 fail-loud 仍成立」—— 一旦有真实现，这条断言会**先红**（它是**按当前实现**写的，不是**按语义**写的） |
| `ArticleSemanticsRegressionTest`（**S-5 / S-5b**，纯 Mockito） | `success=false` ⇒ **抛错重投**、**绝不**当成「不是广告」放行（Go `article_consumer.go:171-173` / `:263-265`） | 守不住「真实现接上后仍 fail-loud」（那是**端口契约**层的事，已由 S-5b 把 `success` 字段补好） |

⇒ ⇒ **两条合起来**才是 fail-loud 的守门网；**任一条单独存在都不足以宣称「审核链路语义已钉死」**（AGENTS.md §3.3.3 第 ② 条：「断言『Bean 存在』≠ 断言『机制生效』」的同款）。

## 12.8 ✅ **本轮结论（2026-10-29）**

| 项 | 结论 |
|---|---|
| **R 档** | **R2 → R3**（④ Go 侧每个业务决策点有 `文件:行号` 出处 ✓ · ⑤ RAG 侧有自动化测试断言该行为 ✓ · ⑥ 归属本组件的必相对齐项 = 0 ✓） |
| **必相对齐项** | **0** —— `S-5` 判为**范围外**（judge.md §1.1 R0）· `U-3` / `U-4` **闭合** · `U-9a` **闭合** · `U-9b` 判为**取证缺口**（§1.1 ⑥ 第 3 类 ⇒ 计入 §九 ⇒ 不阻断）· `U-4′` **销号** |
| **证据强度** | **L4**（最高档）—— 真 Kafka 位点推进 ＋ 真 PG 落库回读 ＋ 双侧活体逐字节对拍 |
| **新增守门** | `ArticleKafkaForceCommitGateTest`（4 条 · 真 Kafka）· `ArticleCreateArticleKafkaFailureBoundaryTest`（1 条 · 真 PG ＋ 死 broker） |
| **组件全量回归** | **188 条 / 0F / 0E / 0S**（25 个测试类 · `mvn -o -B -pl service/article/article-rpc -am test` ⇒ BUILD SUCCESS · 6:44 min）⇒ 原 183 ＋ 4 ＋ 1 |
| **变异** | **3/3 精确转红**（MUT-M1 行为红 · MUT-M2 仅装配面红 ⇒ 证明该属性非承重 · MUT-M3 行被回滚红）⇒ **全部已还原** |
| **§七 RPC 型 5 条** | **5/5 满足**（③ ④ 两格由本轮**补齐**，其余三格既有） |
| **仍开放（不阻断 R3）** | ① `U-9b`（Go 侧 handler 并发模型**未活体对拍**）② 双侧活体的**成功路径完整字段语义**（`user.rpc` 两侧都不可达 ⇒ 现钉的是**降级路径**）③ `O-4` / `O-6` 仍只有静态取证 |
| **⚠️ 不许越界读** | **本组件不是「零差异」** —— §12.4.2 的模型差异、`O-4`/`O-6` 的静态取证、双侧活体只覆盖降级路径 ⇒ 都**不等于**「两侧行为逐字节一致」 |

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-full-parity-probe/article/run.sh` ⇒ **EXIT=0**（2026-10-30 03:21:39 · 34s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：== 2/8 构建 Go 侧二进制（真源**只读** ⇒ 产物落临时目录） == ⏐ == 8/8 请求矩阵（Go vs RAG vs expected.txt ⇒ 逐字节） == ⏐ ✅ list_articles  Go == RAG == expected.txt（逐字节） ⏐ ✅ get_article_exists  Go == RAG == expected.txt（逐字节） ⏐ ✅ get_article_missing 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

## 📌 追加 · 🟢 **rpc 域「指标抓取载体」落地 + 真启动证据（2026-10-30 · G3 第三轮）**

**落码清单（域内文件）**

- `service/article/article-rpc/pom.xml` —— 新增 `spring-boot-starter-web`（Actuator 只给 endpoint 与自动配置，**不给 servlet 容器** ⇒ 载体必须有容器才成立）。
- `service/article/article-rpc/src/main/resources/application.yml` —— 新增顶层 `server.port: ${ARTICLE_RPC_MANAGEMENT_PORT:9092}`（= Go 观测端口，**一字不差**）；`management.endpoint.prometheus.enabled: true`；`spring.cloud.nacos.discovery.register-enabled: false`；新增顶层 `rag.side: rpc`；口令占位符改为**裸** `${POSTGRES_PASSWORD}`。
- `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleRpcPrometheusCarrierGateTest.java` —— **真 fat jar ＋ 真 PG 容器** ⇒ 真打 `/actuator/prometheus` ⇒ 6 例（含「产物 jar 里的默认值 = Go 端口」那条**产物层**断言）。
- `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleRpcSharedLayerWebBeanGateTest.java` —— 装配层断言「api 侧 web Bean **不在** rpc 容器里」＋**正向对照**（证明 servlet 前提成立 ⇒ 断言非空转）。
- `support/RealGrpcPort.java` —— 新增 `pick(int forbidden)` 重载 ⇒ 观测面与业务面**必须占不同的口**（否则两次 `pick()` 可能挑到同一个空口 ⇒ `EADDRINUSE` 偶发红）。
- `support/RealContainerHostPort.java` —— 宿主口登记册新增 `22861`（载体门）与 `22862`（分边门）。

**真启动证据（进程级 · 2026-10-30 实跑 · 生产 yaml 原样）**

- `GET http://127.0.0.1:9092/actuator/prometheus` ⇒ **HTTP 200**、**163 个指标族**、响应体含公共标签 `application="article-rpc"`。
- **同一时刻** gRPC 端口 **19001** 与观测端口 **9092** **同时 LISTEN**（`lsof` 实测）⇒ 业务面没有被载体挤掉。
- 日志实测含 `Tomcat started on port 9092` ＋ `gRPC Server started, listening on address: 0.0.0.0, port: 19001` ＋ `Started ArticleRpcApplication in 6.683 seconds`。

**🔴 一条「差点被当成产品缺陷」的偶发失败 —— 真因已坐实（不是缺陷）**

- 现象：首轮真启动出现过一次 `Application run failed` ＋ `ErrCode:-400, ErrMsg:Shutdown in progress`。
- 真因 = **测试脚本在进程启动途中发了 SIGTERM** ⇒ JVM 进入关闭期 ⇒ `java.base/java/lang/ApplicationShutdownHooks.java:41-56` 的静态块里 `Shutdown.add(1, false, …)` 抛 `IllegalStateException` ⇒ `hooks` 被置 `null` ⇒ 之后 nacos-client 首次 `Runtime.addShutdownHook` 必然抛 `Shutdown in progress`（同文件 `:66-67`）⇒ 该异常是 `RuntimeException`，而 `bootstrap/NacosRegistration.java:104` 只 `catch (NacosException e)` ⇒ **catch 不住** ⇒ 冒泡出 `@EventListener` ⇒ 打印 `Application run failed`。
- **A/B 实证**：对照组（跑完整再停）签名 **clean**；实验组（启动途中 SIGTERM）签名 **hit**（同一串 `Shutdown in progress`）⇒ 产品无缺陷，是探针写法缺陷（进程必须活到验完为止）。

**载体副作用（本轮实测撞到并已修）**

- 补上 `spring-boot-starter-web` ⇒ classpath 上有了 servlet ⇒ Spring Cloud Alibaba 的**自动注册**（`NacosAutoServiceRegistration` 监听 `WebServerInitializedEvent`）被激活 ⇒ 它注册的是 **web 容器端口**（= 观测端口 9092，**不是** gRPC 端口 9001）⇒ Nacos 里会出现「article-rpc → 9092」这条错端口实例 ⇒ api 连过去只拿到 `/actuator/**` ⇒ **gRPC 链路断**；Nacos 不可达时更狠：`failFast=true` ⇒ **直接把启动打断**（`Failed to start bean 'webServerStartStop'`）—— hot 域首轮真启动即撞此条。
- 修法 = 三域显式 `spring.cloud.nacos.discovery.register-enabled: false`。本模块的注册是**手写**的（`bootstrap/NacosRegistration.java:88` 用 `GrpcServerProperties.getPort()` ⇒ 注册的**就是** gRPC 端口）⇒ 与 Go 侧 `zrpc.MustNewServer` 注册 `ListenOn` 的语义一致。

**Nacos 单实例验收（2026-10-30 实跑 · 真 Nacos standalone）**

- `article-rpc` 在 Nacos 里**实例数 = 1**，实例端口 = **gRPC 端口**（实测 `port=19001`），**不是**观测端口 9092。
- 日志里的**手写**注册行 = `NacosRegistration registered: service=article-rpc group=DEFAULT_GROUP ip=… port=19001`；**自动注册痕迹 = 0 条**。

**🟢 本轮第一次拿到的「正向结论」：指标名轴两侧一致（6/6）**

- Go `service/article/rpc/metrics/metrics.go:6` 的 `Namespace = "article_service"`，6 个指标（`:11` / `:21` / `:31` / `:41` / `:52` / `:62`）与 RAG `metrics/ArticleRpcMetrics.java:59`（Namespace）＋ `:88` / `:101` / `:110` / `:149` / `:161` / `:178` 的 6 个 meter **逐条一致**（Micrometer 的自动后缀规则 Counter→`_total`、Timer→`_seconds` 与 Go 的命名对齐）。
- ⚠️ 两侧的 `CounterVec` **空 vec 在 scrape 中不出现**（首次观测才出 series）⇒ RAG 侧 lazy 注册**行为同构** ⇒ 不改。
- ⇒ 这条结论**只有在载体落地后才拿得到**（此前 RAG 侧虽有 meter，但**没有端点** ⇒ 抓不到 ⇒ 比不了）。

**🔴 顺带推翻本域台账旧的一句错断言（「Go 无 metrics 包」）**

- 旧断言写的是「Go 侧 `service/article/rpc/internal/` 下**无 metrics 包** ⇒ Go 没有自定义 counter ⇒ RAG 侧 `article_service_*` 是 RAG 自有 ⇒ 比 Go 宽」。
- **该断言错了**：**枚举漏了一层** —— Go 的 metrics 包在 `service/article/rpc/metrics/`（**不在 `internal/` 下**）⇒ 真源路径 `service/article/rpc/metrics/metrics.go:1-6` ⇒ **Go 侧有** metrics 包 ⇒ ⇒ 「RAG 自有 ⇒ 比 Go 宽」这个推论**不成立** ⇒ 已在 RAG 侧 `application.yml` 的注释里据实订正（改写为 Go 的 **Namespace** `article_service` + 真源路径）。
- ⇒ **教训（可复用）**：**否定结论必须换一种枚举方式交叉扫** —— 路径深度不一致（`internal/` vs 包根）会**直接漏掉整个包**。

**口令兜底取消（2026-10-30 · 合并收口执行者派工 · 与 Go 逐字同形）**

- Go `service/article/rpc/etc/article.yaml:13` = `Password: "${POSTGRES_PASSWORD}"`（**裸**）⇒ go-zero 的加载 = `core/conf/config.go:63` `loader([]byte(os.ExpandEnv(string(content))), v)` ⇒ `os.ExpandEnv` **不认** `${VAR:default}` ⇒ 缺变量 ⇒ **空口令** ⇒ 连库失败 = fail-fast。
- ⇒ RAG 侧原先的 `${POSTGRES_PASSWORD:password}`（走 Spring 的 `:default`）⇒ 缺变量时会用默认值连上去 ⇒ **RAG 比 Go 松** ⇒ 属**真漂移**，**不是**「更严的有意偏离」⇒ ⇒ 本轮**取消兜底**（本文件两处：数据源那段与 `article-rpc.postgres` 那段）。
- ⚠️ **残余差异（失败时刻不同）**：Go = **启动成功、连库时**失败；RAG = **启动即失败**（Spring 解析不到占位符 ⇒ DataSource bean 创建失败）⇒ ⇒ 属「RAG 更严」的一侧 ⇒ 由**合并收口执行者**在合并窗口统一登记。

## 📌 追加 · ✅ **出向 gRPC 缺 Go 的 2s 客户端上界（零期限）→ 已修**（2026-10-31 · G3 第八轮）

> **一句话**：本域 `article-rpc` 的出向出口 `MessageRpcClient` 用**裸 stub** 发起调用
> ⇒ ⇒ **出向调用完全没有期限**（既无 2s 客户端封顶，且因跑在 Kafka consumer 线程上
> ⇒ 载体 ThreadLocal **不可见** ⇒ 连「继承入向 deadline」也不成立）
> ⇒ ⇒ 而 Go 侧同款出口**有** 2000ms 客户端封顶 ⇒ ⇒ **可观察行为分叉**（下游挂死时 Go 2s 返回、RAG 一直等）。

### 缺口本体

| 项 | 证据（`文件:行号`） |
|---|---|
| RAG 调用点 | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/MessageRpcClient.java` ⇒ `stub.sendNotification(req.build())` ⇒ **裸 stub，零 deadline** |
| RAG 调用链 | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleSyncResultConsumer.java:213`（`messageRpcClient.sendArticlePublished(…)`）⇒ 该类 `:80` 是 `@KafkaListener` ⇒ ⇒ **Kafka consumer 线程** |
| 载体可见性 | ⇒ consumer 线程 ⇒ 共享层 `RpcInterceptor.onHalfClose` 绑的 ThreadLocal **不可见** ⇒ ⇒ 连「继承入向 deadline」都**不成立** |
| Go 对照 | `service/article/rpc/internal/mqs/article_sync_result_consumer.go:197`（`l.svcCtx.MessageRpc.SendNotification(ctx, …)`）⇒ **同一形态**（Kafka 消费 ctx，无 deadline）⇒ ⇒ Go 侧该出口**只有客户端 2s 封顶** ⇒ ⇒ **两侧差的就是这 2s** |

**Go 侧证据（消费点，不是声明点）**
- `go-zero@v1.10.2/zrpc/internal/client.go:113-117`：`if c.middlewares.Timeout { interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout)) }` ⇒ **客户端无条件**装超时拦截器。
- `go-zero@v1.10.2/zrpc/internal/config.go:15`：`Timeout bool json:",default=true"` ⇒ 中间件开关**默认开**。
- `go-zero@v1.10.2/zrpc/config.go:31`：`Timeout int64 json:",default=2000"` ⇒ **2000ms**。
- `go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25`：`ctx, cancel := context.WithTimeout(ctx, t)` ⇒ 父 ctx 更早则**先到者生效**。

**🔴 为什么「2s 客户端上界」这一半**只能**由域侧提供**
- 共享层的 `GoStyleOutboundDeadlineInterceptor.effectiveCallOptions` 算的是
  `min(请求载体剩余纳秒, 客户端硬上界 capMillis, 调用点自带 deadline)`
  ⇒ 而 `GoStyleProtectionProperties.OutboundDeadline#capMillis` **默认 0 = 不启用**
  ⇒ 且全仓 `service/<域>/<组件>/src/main/resources/*.yml` 的 `outbound-deadline` **零配置**
  ⇒ ⇒ **拦截器层根本不提供 Go 的 2000ms 客户端上界**
  ⇒ ⇒ 那一半**只在域侧调用点** ⇒ ⇒ **域侧漏了就是真漂移**（RAG 比 Go 松）。

### 🔴 Go 侧 zrpc client `Timeout` **真值表**（必须逐域读 yaml · 不能一律按 2000 补）

| 域 / 方向 | Go 侧 yaml 真值 | RAG 侧现状 | 判定 |
|---|---|---|---|
| `article-rpc` → `message-rpc` | `service/article/rpc/etc/article.yaml` 的 `MessageRpc:` 段**没写 `Timeout`** ⇒ **default 2000ms** | **零期限** | 🔴 **真漂移（本条）** |
| `article-api` → `article-rpc` | `service/article/api/etc/article-api.yaml:12` **`ArticleRpcConf.Timeout = 70000`**（显式写） | `GoStyleRequestDeadline.applyTo(stub)` ⇒ 继承 HTTP 顶层 `Timeout: 70000`（`article-api.yaml:4`）⇒ ≈ **70s** | ✅ **一致 —— 不是缺口** |
| `article-rpc` → `security-rpc` | `service/article/rpc/etc/article.yaml:90` `SecurityConf.Timeout = 10000` | 骨架未实现（`SecurityRpcClient` 是占位） | N/A |
| `hot` / `favorite` / `comment` 三域的出向 | 各自 yaml 里**零 `Timeout:` 行** ⇒ 全部 **default 2000ms** | 各自已有 2s 上界 | ✅ 一致 |

⚠️ **本表为什么必须存在**：Go 的 2000ms 是 **`default` 而不是普遍真值**
⇒ ⇒ `article-api` 就是反例（**70000ms**）⇒ ⇒ **任何域补「2s 客户端上界」都必须先读自己那份 yaml**
⇒ ⇒ **硬编码 2000 会把 70000 的域改严 35 倍**（Go 侧 70s ⇒ RAG 侧 2s ⇒ RAG 更严 ⇒ 同样是漂移）。
（共享层 `GoStyleOutboundDeadlineInterceptor` 的类 javadoc 已就 `article-api.yaml:12` 记过这条警告 ⇒ 此处是**域侧实测核对**。）

### 影响（可观察行为分叉）

- 下游 `message-rpc` 挂死时：**Go 侧 2s 就返回**（`DEADLINE_EXCEEDED` ⇒ 按调用方口径折叠）；
  **RAG 侧一路等到底** ⇒ ⇒ **调用耗时 + 失败时机都不同** ⇒ ⇒ 属**真漂移**，不是表达层差异。
- ⚠️ 该调用点**吞掉异常**（`catch (StatusRuntimeException)` ⇒ 只记日志、返回 false）
  ⇒ ⇒ 失败**时机**的差异不会向上冒泡 ⇒ ⇒ 表现为「消费变慢」而非「报错」⇒ **更难被发现**。

### 证据强度与处置建议

| 项 | 结论 |
|---|---|
| 证据强度（**修前**） | **L2 源码级**（逐字读了裸 stub 调用、调用链、`@KafkaListener` 注解、Go 侧同款调用）⇒ ⚠️ **未**做活体计时 |
| **实际修法（已落码）** | 照抄**本仓现成正确实现**（不发明第三种写法 · AGENTS.md §3.2）：**comment 域** `service/comment/comment-rpc/.../client/GoStyleClientCallTimeout.java` 的**两段式**（`capByClientTimeout(applyTo(stub))`）—— ⚠️ 本出口在 consumer 线程上 ⇒ `applyTo` 会**原样返回**（载体不可见）⇒ 实际效果 = **2s 封顶**（与 Go 同形）<br>⚠️ **封顶值必须先读本域 yaml**：本域 `MessageRpc:` 段**没写 `Timeout`** ⇒ 取 **default 2000ms** ⇒ ⇒ 与 comment / favorite 两域同款（那两份 yaml 也都没写）⇒ ⇒ **硬编码 2000 在本域成立**；但**不可推广**（`article-api` 是 70000）<br>⇒ **落点**：① 新增 `service/article/article-rpc/.../client/GoStyleClientCallTimeout.java`（照抄 favorite 域那份，package-private 工具类）② `MessageRpcClient` 新增私有方法 `messageRpc()` ＝ `GoStyleClientCallTimeout.capByClientTimeout(GoStyleRequestDeadline.applyTo(stub))`，调用点由裸 stub 改为走它 （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| 状态 | ✅ **已修**（2026-10-31 · Jason 拍板「修」· G3 域侧自修 · 落点在本域）⇒ 修法与证据见上一行；守门网见下一段 |

## 📌 追加 · 🟡 **共享层演进 ⇒ 本域「载体守门」的正向对照参照物失效（已改用测试自带探针）**（2026-10-31 · G3 第八轮）

> **一句话**：2026-10-31 的回归（**带工作树共享层**跑）里，`ArticleRpcSharedLayerWebBeanGateTest` 的第 ② 条
> 「**正向对照**：共享层那个带 servlet 条件的自动配置确实在容器里」**转红**
> ⇒ ⇒ **根因不是载体漂移，而是共享层演进把这个守门的「参照物前提」改掉了** ⇒ ⇒ 已就地改用**测试自带探针**。

### 现象与根因

| 项 | 内容 |
|---|---|
| 现象 | `ArticleRpcSharedLayerWebBeanGateTest#servletConditionHoldsInThisContext` 红：`GoStyleJsonContentTypeAdvice` 不在容器里 |
| 根因 | 共享层 `53bc05ec`（G4 lane · 2026-10-30）把一批 web Bean 从「**只有** `@ConditionalOnWebApplication(SERVLET)`」改成「servlet 条件 **+** api 侧条件（`ConditionalOnApiSide`）」⇒ ⇒ **`GoStyleJsonContentTypeAdvice` 现在是 api 侧 Bean** ⇒ ⇒ 在 rpc 容器里**本就不该在** ⇒ ⇒ **守门红得对，但守门本身的前提已过期** |
| 为什么以前是绿的 | 此前回归**没带 `-am`** ⇒ 用的是 m2 里 **2026-10-29 08:08 的旧** `com.rag:common` jar ⇒ 那份共享层里这些 Bean 还只有 servlet 条件 ⇒ ⇒ 在 MOCK 上下文里都在 ⇒ 绿 |
| 修法（已落码） | 改用**本类自带的探针** `ServletContextProbeConfiguration`（**只**带 `@ConditionalOnWebApplication(SERVLET)`、**不带** api 侧条件）⇒ ⇒ **实测踩了两个坑（逐个定位，不是猜的）**：<br>① **注册面**：探针最初做成**类的嵌套** `@TestConfiguration` ⇒ 实测**根本没被自动注册**（探针始终不在容器里）⇒ ⇒ 而上下文底账实测为 `ctx=GenericWebApplicationContext` / `env=ApplicationServletEnvironment` / `session` scope 已注册 ⇒ ⇒ **条件是匹配的 ⇒ 问题在「注册」不在「条件」** ⇒ ⇒ 最终改成**文件顶层（包私有）`@Configuration` ＋ 测试类上显式 `@Import`** ⇒ 注册不再依赖任何自动探测机制；<br>② **编译面**：`@Import(Xxx.class)` 写在**类自身的注解**上时，javac 按 JLS 作用域规则**解析不到本类的嵌套成员类型**（嵌套类型的作用域是「类体内部」，而类声明上的注解在类体之外）⇒ ⇒ 编译报「找不到符号」⇒ ⇒ 搬到文件顶层后 `@Import` 可解析（**这是必须搬文件顶层的第二个独立理由**）；<br>③ **追加一条不依赖任何 bean 条件的直接断言**：`context instanceof WebApplicationContext` ⇒ ⇒ 直接读「前提事实」本身 ⇒ **不依赖共享层任何 Bean 的条件组合** ⇒ ⇒ 与探针那条**成对**（探针验「servlet 条件机制真的匹配」，直接断言验「上下文真的是 servlet web app」） |

### 为什么必须留这一条（别当噪音删掉）

- 第 ③ 条断言的是「api 侧那 5 个 web Bean **一个都不在** rpc 容器里」
  ⇒ ⇒ 若本上下文**压根不是 servlet web app**，那 5 个 Bean 自然不在 ⇒ ⇒ **第 ③ 条就是空断言**（无鉴别力）
  ⇒ ⇒ 第 ② 条的**正向对照**是第 ③ 条的前提 ⇒ ⇒ 两者必须成对维护。
- ⚠️ 本条同时是一个**方法学提醒**：守门里「拿共享层某个 Bean 当参照物」的做法
  ⇒ ⇒ **会随共享层演进而失效** ⇒ ⇒ 判据应当**自带参照物**。

## 📌 追加 · ✅ **出向 deadline 的 2s 客户端封顶已落码 + 守门网已补（L4 活体 · 变异转红）**（2026-10-31 · G3 第九轮）

> **一句话**：上一段的缺口（`article-rpc` → `message-rpc` 出向**零期限**）已按 Jason 拍板「修」**落码**
> ⇒ ⇒ 落点是**域内**（`service/article/article-rpc/**`）⇒ ⇒ **两侧同形**（Go 2s 封顶 / RAG 2s 封顶）
> ⇒ ⇒ 另补一条 **5 例守门**（L4 活体）⇒ ⇒ **三个语义维度各有专属判据**（变异矩阵实测精确转红）。

### 落码（两处，都在域内）

| 项 | 证据（`文件:行号`） |
|---|---|
| 新增工具类 | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/GoStyleClientCallTimeout.java`（package-private；**照抄 favorite 域同款**，不发明第三种写法 · AGENTS.md §3.2）⇒ `capByClientTimeout` ＝ `min(载体剩余纳秒, 2s)`，常量 `CLIENT_TIMEOUT_MILLIS = 2000` （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| 调用点改造 | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/MessageRpcClient.java` ⇒ 新增私有方法 `messageRpc()` ＝ `GoStyleClientCallTimeout.capByClientTimeout(GoStyleRequestDeadline.applyTo(stub))`；原裸 stub 调用 `stub.sendNotification(req.build())` 改为 `messageRpc().sendNotification(req.build())` （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| 封顶值的来源 | Go `service/article/rpc/etc/article.yaml` 的 `MessageRpc:` 段**无 `Timeout` 行** ⇒ 取 `zrpc/config.go:31` 的 `json:",default=2000"` ⇒ ⇒ **不是拍脑袋的数**；⚠️ 同仓 `service/article/api/etc/article-api.yaml:12` ＝ **70000** 是反例 ⇒ ⇒ **不可推广**（硬编码 2000 到别的域会把 70000 的域改严 35 倍） |

### 🔴 本出口在生产上落在「**无载体**」那一档（必须说清楚）

- 调用链：`service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleSyncResultConsumer.java:213` ⇒ 该类 `:80` 是 `@KafkaListener` ⇒ ⇒ **Kafka consumer 线程**
- ⇒ ⇒ 共享层 `RpcInterceptor.onHalfClose` 绑的 ThreadLocal 载体**在该线程不可见**
- ⇒ ⇒ 第一段 `applyTo` **原样返回 stub** ⇒ ⇒ 净效果 = **恰好 2s 封顶**
- ⇒ ⇒ 与 Go 侧**同形**（Go 的 `service/article/rpc/internal/mqs/article_sync_result_consumer.go:197` 传的也是 Kafka 消费 ctx ⇒ 同样无 deadline ⇒ 同样只有 2s 封顶）

### 守门网（5 例 · `ArticleOutboundDeadlineCarrierGateTest`）

| 用例 | 专属判别维度 |
|---|---|
| `carrierIsReallyBoundOnHandlerThreadOverRealWire` | 真 wire + 真共享层 `RpcInterceptor` ⇒ 证「载体在 gRPC handler 线程**真的绑上了**」＋「客户端 deadline **真的穿过 wire** 到了服务端」（⇒ 把证据从 L2 源码级升到 **L4 活体**） |
| `outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger` | 入向预算 **> 2s** ⇒ 证「2s 客户端封顶**真的生效**」（专治「不压任何 deadline」） |
| `outboundInheritsInboundBudgetWhenItIsShorter` | 入向预算 **< 2s** ⇒ 证「出向上界**真的继承了**入向剩余」（专治「无脑套 2s」） |
| `outboundBudgetKeepsNanosecondPrecision` | 手动绑定**已知绝对纳秒**（10.9ms）⇒ 证「域内计算点**不截断亚毫秒**」（可确定性分辨毫秒版） |
| `noCarrierStillGetsTwoSecondClientCap` | **无载体** ⇒ 证 2s 封顶**无条件**在 ⇒ ⇒ **这一条才是本域生产出口真正落的那一档** |

### 🔴 变异矩阵（守门非假绿 · 2026-10-31 实测）

| 注入的变异 | 转红的用例（实测） | 读数 |
|---|---|---|
| 纳秒 → 毫秒截断（旧实现语义） | `outboundBudgetKeepsNanosecondPrecision` | 5 tests / **1 failure** |
| 完全不压 2s 封顶（原样返回 stub） | `noCarrierStillGetsTwoSecondClientCap` ＋ `outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger` | 5 tests / **2 failures** |
| 无视入向预算，永远只套 2s | `outboundBudgetKeepsNanosecondPrecision` ＋ `outboundInheritsInboundBudgetWhenItIsShorter` | 5 tests / **2 failures** |
| （基线 / 还原后） | — | 5 tests / **0 failure** |

⇒ ⇒ **三个语义维度（纳秒精度 / 2s 封顶 / 继承入向）各有专属判据** ⇒ ⇒ 本守门**对被测差异有判别力**，不是「错误答案的守卫」。
⇒ 三个变异均已还原（仓内残留锚点检查 = **0**）；还原后复跑全绿。

### ⚠️ 本守门**证什么 / 不证什么**（别过度解读）

- ✅ **证**：「载体可见时 ⇒ 出向上界 = min(载体剩余, 2s)」这个**计算点本身**是对的（纳秒精度 + 无条件 2s 封顶）。
- ❌ **不证**：「生产装配面（真 `@GrpcClient` ⇒ devh 拦截器链）真的把 deadline 给到了」⇒ ⇒ 本守门是**域侧计算点**的证据，不是**装配面**的证据。
- ⚠️ 生产出口落在「无载体」那一档 ⇒ ⇒ 本守门的**第 ⑤ 条**才是与生产同形的那一条；第 ①②③④ 条证的是**同一段代码**的另一半（用来排除「写死 2s、完全不看载体」的假实现 ⇒ 否则第 ⑤ 条会成假绿）。

## 📌 追加 · ✅ **「副本数 = 漂移面」的收敛面已从 3 份归零**（2026-10-31 · G3 第九轮登记 · **第十一轮闭合**：域侧三份私有副本**全部删除** ⇒ 形态 = 共享层唯一实现 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`）

> **一句话**：上一段落的落码让本域多了一份 `GoStyleClientCallTimeout` 私有副本（**2026-10-31 第十一轮：三份域内副本已全部删除 ⇒ 收敛面 = 0 份**）
> ⇒ ⇒ 全仓私有副本从 **2 份**变成 **3 份**（article-rpc / comment-rpc / favorite-rpc，**全在 G3 域内**）
> ⇒ ⇒ 合并收口执行者的「副本收敛到共享层」派工单**其前提已被本轮改变**（已发纠正面）
> ⇒ ⇒ 收敛的必要性**更强**，但**不是**本域单方面能做的（共享层是 G4 写权）。

### 副本清单与「代码体逐字一致」的实测

| 副本 | 归属 | 总行 | 代码行（去 javadoc） | 封顶值来源 |
|---|---|---|---|---|
| `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/GoStyleClientCallTimeout.java` | 本域（**本轮新增**） | 132 | 21 | Go `service/article/rpc/etc/article.yaml` 的 `MessageRpc:` 段（`:92`）**没写 `Timeout`** ⇒ default **2000**（**2026-10-31 第十一轮：该副本已删除** ⇒ 改由共享层 `GoStyleOutboundDeadlineInterceptor` ＋ 域侧 `cap-millis=2000`） |
| `service/comment/comment-rpc/.../client/GoStyleClientCallTimeout.java` | G3 域 | 139 | 21 | `comment.yaml` 的 `MessageRpc:` / `ArticleRpc:` 两段都没写 ⇒ **2000**（**2026-10-31 第十一轮：该副本已删除** ⇒ 改由共享层 `GoStyleOutboundDeadlineInterceptor` ＋ 域侧 `cap-millis=2000`） |
| `service/favorite/favorite-rpc/.../client/GoStyleClientCallTimeout.java` | G3 域 | 121 | 21 | `favorite.yaml` 的 `UserRpc:` / `ArticleRpc:` 两段都没写 ⇒ **2000**（**2026-10-31 第十一轮：该副本已删除** ⇒ 改由共享层 `GoStyleOutboundDeadlineInterceptor` ＋ 域侧 `cap-millis=2000`） |

⇒ **三份的代码体除 `package` 行外逐字一致**（逐行 diff 只差 package）⇒ ⇒ **三份是纯复制**，收敛时可直接取任一份改 package ⇒ ⇒ 差别只在 javadoc。

### 🔴 收敛时必须防的粒度坑：**组件级 `capMillis` 装不下本域的两个出向方向**

- Go 的 `c.Timeout` 是**每个 zrpc client（方向）**一个值；而共享层的
  `GoStyleProtectionProperties.OutboundDeadline#capMillis` 是**组件级**一个值。
- 本域 `article-rpc` 组件内两个出向方向**真值不同**：

| 方向 | Go yaml 真值 | 依据 |
|---|---|---|
| `article-rpc` → `message-rpc` | **2000**（default） | `service/article/rpc/etc/article.yaml` 的 `MessageRpc:` 段（`:92`）**没写 `Timeout`** |
| `article-rpc` → `security-rpc` | **10000** | 同文件 `SecurityConf:` 段 `:90` `Timeout: 10000` |

⇒ ⇒ **组件级 `capMillis` 表达不了本域** ⇒ ⇒ 只有两条路：
① `security-rpc` 那个方向继续不实现（**现状如此**：`SecurityRpcClient` / `ImageSecurityRpcClient`
都是**骨架占位**、无真实调用）⇒ ⇒ 收敛**当下**不构成实际冲突；
② 或按「取最小值」保守处理 ⇒ ⇒ 会把 security-rpc 从 10s 改严到 2s
⇒ ⇒ **RAG 比 Go 严** ⇒ ⇒ 同样是漂移（只是方向相反）⇒ ⇒ **不可**。
⇒ ⇒ ⚠️ **一旦 security-rpc 的骨架落地，本条就会变成真冲突** ⇒ 收敛方案必须留这个口。

### ✅ 收敛**不会**丢纳秒精度（读共享层实现确认）

`service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java:144-177`
的 `effectiveCallOptions` 三项**全是纳秒**：
① `GoStyleRequestDeadline.remainingNanos()` · ② `TimeUnit.MILLISECONDS.toNanos(capMillis)` ·
③ `existing.timeRemaining(TimeUnit.NANOSECONDS)` ⇒ ⇒ 域侧 `applyTo` 挂的**纳秒** deadline
被第③项**取 min 保留** ⇒ ⇒ 收敛后精度**不丢** ⇒ ⇒ 收敛是**语义等价**的（前提：`capMillis` 逐组件配对）。

### ⚠️ 收敛的**必做配套**（别漏）

三域那 5 例守门（**5 例 × 3 域**、变异矩阵已验精确转红）**依赖域侧常量**
`GoStyleClientCallTimeout.CLIENT_TIMEOUT_MILLIS` ⇒ ⇒ 若收敛后改成读 `capMillis` 配置项（**2026-10-31 第十一轮：已按此收敛** —— 域侧副本删除 ⇒ 域侧 yml 配 `rag.protection.outbound-deadline.cap-millis=2000`）
⇒ ⇒ **三份守门的判据必须同步改**（否则它们直接红）⇒ ⇒ 这是收敛的**配套改动**。

### 当前处置

- 🟡 **本域不单方面动**（`service/common/**` 是 **G4 写权** ⇒ 收敛须**同一次提交**同时改共享层与域代码 · §5.3c）
- ⇒ 等 G4 先落共享层那半（一份实现 ＋ `capMillis` 逐组件配），本域在**同一提交**里删私有副本、改走共享层、配 `capMillis = 2000`
- ⇒ ⚠️ **`article-api` 那条不配**（Go `article-api.yaml:12` = **70000** ⇒ 配 2000 会改严 35 倍）

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W4

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| FIX-06 | D | P3 | 否 | Go `service/article/rpc/internal/mqs/article_sync_result_consumer.go:147`（`notifyAuthor, notifyTitle = article.AuthorID, article.Title` ⇒ 通知标题取**当前行** `article.Title`，而 revision 取**冻结 payload** 的 `approved.Title`（同文件 `:120`））vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleAtomicService.java:417`（`? new PublishOutcome(article.getAuthorId(), article.getTitle(), true)` ⇒ 通知标题取**当前行** `article.getTitle()`，与 Go `:147` 同源）⇒ **差异已消失**（台账 `DRIFT/article/rpc.md:1161` 记的 `DRIFT-ARTICLE-RPC-009` 已修：原先用 `approved.title` ⇒ 已改为 `article.getTitle()`）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| FIX-08 | D | P3 | 否 | Go `service/article/rpc/metrics/metrics.go:46`（`Buckets: []float64{0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1}` ⇒ **8 个**桶边界 + 固有 `+Inf` = 9）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/metrics/ArticleRpcMetrics.java:62`（`private static final double[] MINIO_BUCKETS = {0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1};` ⇒ **同一组 8 个数值**）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/metrics/ArticleRpcMetrics.java:127`（javadoc「**绝不能**加 `publishPercentileHistogram()`」⇒ 该行已从 Timer 构造里去掉 ⇒ Micrometer 不再并入默认百分位桶）⇒ **差异已消失**（真 scrape 实测 9 个边界，与 Go 逐条一致；台账 `DRIFT/article/rpc.md:1345` 的 FIX-08 已修，`DRIFT-ARTICLE-RPC-010` 已闭）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| FIX-17 | D | P3 | 否 | Go `service/article/rpc/internal/logic/create_article_logic.go:94`（`SecondaryTags: model.StringArray(in.SecondaryTags)` ⇒ **直接类型转换**（非 `append`）⇒ 不带二级标签时 = nil slice ⇒ `StringArray.Value()` = `json.Marshal(nil)` = `"null"` ⇒ Go 建的行落库就是 `jsonb 'null'`）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleDomainService.java:349`（`if (a.getSecondaryTags() != null) { builder.addAllSecondaryTags(a.getSecondaryTags()); }` ⇒ **null 不写**）＋ `:353`（`if (a.getExtInfo() != null && !a.getExtInfo().isEmpty()) { builder.putAllExtInfo(a.getExtInfo()); }` ⇒ **null / 空不写**）⇒ **差异已消失**（Go 侧 proto3 `repeated`/`map` **无 presence** ⇒ nil ⇒ 字段不存在；RAG 侧 null ⇒ 不写 ⇒ 字段不存在 ⇒ **wire 字节逐字节相同**，台账 `DRIFT/article/rpc.md:1875` 的 P-U-2 已闭）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| O-5 | D | P3 | 否 | Go `service/article/rpc/internal/model/db.go:55`（`if err := db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{}); err != nil {` ⇒ Go 侧**用 GORM AutoMigrate 建表**，全程**没有 Flyway 账本**这个概念）vs RAG `service/article/article-rpc/src/main/resources/application.yml:67`（flyway 段：`enabled: true` / `baseline-on-migrate: true` / `locations` / `schemas` ⇒ **不配 `table:` ⇒ 用默认表名 `flyway_schema_history`**）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/bootstrap/ArticleSchemaGate.java:139`（错误文案「… ⇒ Flyway migrations did not run. Check spring.flyway.enabled, locations=classpath:db/migration, and that the pom includes org.springframework.boot:spring-boot-flyway (required since Boot 4).」⇒ 本域**确实靠 Flyway 建表** ⇒ Go 侧 `AutoMigrate` 的 RAG 对应物 = Flyway 迁移 + 这道 schema gate）⇒ **差异已消失**：O-5 指控的那条是「`article-rpc`/`like-rpc`/`message-rpc` **共用一本** `flyway_schema_history`」⇒ 经回源复核**该差异从未成立** —— 实测 6 个 rpc 各有独立账本（`service/like/like-rpc/src/main/resources/application.yml:81` = `flyway_schema_history_like` · `service/message/message-rpc/src/main/resources/application.yml:105` = `flyway_schema_history_message` · `service/comment/comment-rpc/src/main/resources/application.yml:126` = `flyway_schema_history_comment` · `service/favorite/favorite-rpc/src/main/resources/application.yml:129` = `flyway_schema_history_favorite` · `service/follow/follow-rpc/src/main/resources/application.yml:79` = `flyway_schema_history_follow` · `service/task/task-rpc/src/main/resources/application.yml:79` = `flyway_schema_history_task`），**只有 `article-rpc` 用默认表名**（`service/article/article-rpc/src/main/resources/application.yml:67`）⇒ 按 AGENTS.md §6.3 问题 4 的既定分配「默认表名归 `article-rpc` 独占」⇒ 与其余 6 本独立账本**互不冲突** ⇒ 台账 `DRIFT/article/rpc.md:119`/`:342` 的「❌ 维持『证伪』」经回源复核**成立**（`DRIFT/article/rpc.md:129` §「O-5 证伪（上一版 grep 被 `head -30` 截断导致的误报）」）⇒ 另：Go 侧 `AutoMigrate` vs RAG 侧 Flyway 本身属**表达层**迁移，不是缺陷 ⇒ **经分析确认不需要再实现** ⇒ 无落码动作 ⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| S-3 | C | P2 | 否 | Go `go-queue@v1.2.2/kq/config.go:24`（`ForceCommit bool \`json:",default=true"\`` ⇒ **声明点**）＋ `go-queue@v1.2.2/kq/queue.go:230-242`（**消费点**：`if err := q.consumeOne(...); err != nil { if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }; if !q.c.ForceCommit { continue } }; if err := q.consumer.CommitMessages(ctx, msg); err != nil {` ⇒ ForceCommit=true ⇒ **不 continue ⇒ 失败也提交位点**）＋ Go 真源 `service/article/rpc/etc/article.yaml`（`KqConsumerConf` 段**未配** ForceCommit ⇒ 生效值 = true）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/ArticleRpcApplication.java:241`（`handler.setSeekAfterError(true);`）＋ `:231`（`DefaultErrorHandler handler = new DefaultErrorHandler(` … 零次重试 BackOff）⇒ **差异仍成立（承重项不同，但可观察面已收敛）**：Go 的承重项是 `ForceCommit` tag 的**消费点**；RAG 侧实测（`service/article/article-rpc/src/test/java/com/rag/article/article/rpc/mq/ArticleKafkaForceCommitGateTest.java:138`「`setSeekAfterError(true)` **不是承重项**」）**承重项是零次重试 BackOff** ⇒ 二者**共同的可观察效果**（失败消息只投递 1 次、位点照样提交）**已对齐** ⇒ 台账 `DRIFT/article/rpc.md:332` 记的「🔴 原依据修正」+ `U-3`/`U-4` 未在真 Kafka 上证明已被 `docs/DRIFT/article/rpc.md:2442` §12.3（真 Kafka 实测）闭合 ⇒ 残留 = **机制层归属不同**（表达层可接受）与**真 Kafka 下失败消息的精确 ack 语义**尚未逐条对拍 ⇒ 归 **C**（阶段二用真 Kafka 双侧对拍钉死） | 2026-10-30 |
| S-5 | D | P3 | 否 | Go `service/article/rpc/internal/mqs/article_consumer.go:154-155`（`if l.svcCtx.SecurityRpc == nil { return fmt.Errorf("content security client not initialized") }` ⇒ Go 自己在 security 不可用时**也要求审核失败** ⇒ 文章卡在 REVIEWING）＋ `service/article/rpc/internal/svc/service_context.go:60`（`securityClient := zrpc.MustNewClient(c.SecurityConf)`）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/UnavailableContentSecurityClient.java:69`（`private static final String NOT_INITIALIZED = "content security client not initialized";` ⇒ **文案逐字相同**）＋ `:76`（`throw new SecurityAuditException(NOT_INITIALIZED);`）⇒ **差异已消失**（两侧可观察行为一致 = 都把文章留在 REVIEWING）⇒ 且 security 域按 `AGENTS.md` §1 **范围外 ⇒ 不是待办**（用户决策，`docs/DRIFT/article/rpc.md:2400` §12.1 已改判「范围外 ⇒ 不计入本组件必相对齐项」）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |

### lane W9

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-ARTICLE-RPC-007 | D（已签字偏离 ⇒ 决策不是缺陷） | P3 | 否 | Go `go-queue@v1.2.2/kq/config.go:12`（`Brokers []string` ⇒ 逐通道独立声明）＋ `go-queue@v1.2.2/kq/pusher.go:44-47`（`NewPusher` ⇒ `kafka.Writer{Addr: kafka.TCP(addrs...)}` ⇒ **生产**消费点）＋ `go-queue@v1.2.2/kq/queue.go:120-121`（`kafka.ReaderConfig{Brokers: c.Brokers}` ⇒ **消费**消费点）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/ArticleKafkaChannelValidator.java:121`（**全仓唯一的生产侧 `getBrokers()` 读点** ⇒ 只**校验**、不**连接**；`service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/ArticleRpcProperties.java:280` 只是 getter 定义）＋ `:65`（类注释自记「四个键**声明了但没有任何消费点**」）⇒ **差异仍成立**（RAG 侧 Spring Kafka 客户端是全局单例 ⇒ 不能逐通道切 broker；Go 侧可以）⇒ 但**已签字**：`docs/Intentional_Deviations/article/rpc.md:164`（`SD-2026-10-24-02` · 状态 ✅ 仍成立 · 2026-10-24 用户 Jason 签字）⇒ **是决策不是缺陷** ⇒ 归 D；⚠️ 处置面已把「静默忽略」改成「启动即失败」（校验器 `@PostConstruct` 抛异常 ⇒ 启动失败）⇒ 无待实现内容 | 2026-10-30 |
| U-9 | D（差异已消失 ⇒ `U-9a` 半两侧同构、已闭合） | P3 | 否 | Go `go-queue@v1.2.2/kq/queue.go:240`（`if err := q.consumer.CommitMessages(ctx, msg); err != nil {` ⇒ 提交的是**这条消息自己的位点**）＋ `kafka-go@v0.4.47/reader.go:171-185`（`offsetStash.merge` ⇒ `offsetsByPartition[c.partition]` **只在 `c.offset` 更大时**才被覆盖 ⇒ 同一分区多次提交取 **max** ⇒ 已提交位点单调不回退）vs RAG `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/mq/ArticleKafkaForceCommitGateTest.java:354`（`committedOffsetPerPartitionIsMaxDeliveredPlusOneAndNeverRegresses` ⇒ 真 Kafka 读消费组已提交位点 + 逐条投递记录）＋ `:352`（`@DisplayName` 写明「Go kq/queue.go:240 + kafka-go reader.go:171-185 取 max ⇒ 两侧同构」）⇒ **差异已消失**（`U-9a` 半：两侧都是「每分区单调、提交 = 该消息自己的 offset+1、已提交的消息被跳过」）⇒ ⚠️ 本条**剩余的一半 = 处理器并发模型**，另立编号 **`U-9b`** 单独定级（见下）⇒ 本条主体已无待实现内容 ⇒ 归 D | 2026-10-30 |
| U-9b | C（阶段 2 最终工程建设项 · 取证缺口；「还没查」不构成 D） | P3 | 否 | Go `go-queue@v1.2.2/kq/queue.go:248-259`（`startProducers`：`q.c.Consumers` 个 goroutine 调 `FetchMessage` ⇒ 拉取者）＋ `:219-246`（`startConsumers`：`q.c.Processors` 个 goroutine 读**同一个** `q.channel` ⇒ 8 个 handler 抢同一个 channel ⇒ 同一分区消息**可能**被并发处理、handler 级副作用顺序**不保证**）＋ `go-queue@v1.2.2/kq/config.go:19`（`Processors int json:",default=8"` ⇒ 未配即 8）＋ `service/article/rpc/etc/article.yaml:51` ／ `:67`（两个消费者都只配 `Consumers: 1` ⇒ 拉取者只有 1 个）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/ArticleRpcApplication.java:270`（`factory.setConcurrency(PROCESSOR_CONCURRENCY)`）＋ `:109`（`static final int PROCESSOR_CONCURRENCY = 8;`）＋ `:271-272`（`setAckMode(… AckMode.RECORD)`）⇒ **差异仍成立**（Go 可能同分区并发处理 · RAG 的 8 个 consumer 各占自己的分区 ⇒ 同分区**严格串行** ⇒ **RAG 侧更严**）⇒ 🔴 **不许写「已对齐」**：Go 侧**无活体对拍**（本仓从未跑过 Go `kq` 探针 —— `tools/` 下无该探针）⇒ 解除条件二选一：① 登记有意偏离并签字（落点 `docs/Intentional_Deviations/article/rpc.md`，**不在本 lane 写权面**）② 补 Go 侧活体探针（真 Kafka ＋ 真 `kq` ＋ 同分区多条消息）⇒ 「还没查」**不构成 D** ⇒ 归 C；⚠️ 按基线口径**不计入**阶段一判定（`docs/PhaseGoal-judge.md` §1.1 ⑥ 第 4 条 · `docs/PhaseGoal/g3.md:24` 亦记「**不降 R** ⇒ 按 §1.1 ⑥ 第 4 条不计入」）⇒ 不阻断阶段一 | 2026-10-30 |
