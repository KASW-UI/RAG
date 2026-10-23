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
| 审核消费者 | **审核链路可用性** | 两个真 client | `UnavailableContentSecurityClient` 恒抛 | **SEMANTIC MISMATCH**（= S-5，仍阻塞） | 真上下文 |
| 同步结果消费者 | 判定链顺序 ①~⑤ | `article_sync_result_consumer.go:75-107` | `ArticleAtomicService:339-396` | EQUIVALENT | 真 PG（失败回滚 + **成功路径**，2026-10-24 补） |
| 同步结果消费者 | 六项冻结输入比对 | `:103-107` | `ArticleAtomicService:384-390` | EQUIVALENT | 静态 |
| 同步结果消费者 | 通知在事务提交后 / 失败只记日志 | `:160-163` | `ArticleSyncResultConsumer:146` | EQUIVALENT | 静态 |
| 同步结果消费者 | **通知标题来源** | `article.Title`（**当前行**） | ~~`approved.title`（**冻结 payload**）~~ ⇒ **已改为当前行** | ❌ SEMANTIC MISMATCH ⇒ ✅ **已修**（`DRIFT-ARTICLE-RPC-009`） | 真 PG（行标题 ≠ 冻结标题时也能复现，2026-10-24） |
| outbox 中继 | 捞取条件 / 逐条独立 / key=AggregateID / 推 payload 原文 | `article_sync_outbox_sender.go:21-45` | `ArticleSyncOutboxRelay:89-121` | EQUIVALENT | 静态 |
| outbox 中继 | **`pollIntervalMs<=0` 兜底** | `article.go:62-65` ⇒ 1s | **未接上**（DRIFT-ARTICLE-RPC-006） | **OBSERVABLE DIFFERENCE** | 静态 |
| outbox 中继 | `@Scheduled` 是否真的跑 | goroutine + ticker | `@EnableScheduling` 已声明（`ArticleRpcApplication:65`） | EQUIVALENT | 真上下文 |
| 指标 | 6 个 Prometheus 全名 | `metrics.go:5-69` | 逐条一致（O-2 已改） | EQUIVALENT | SimpleMeterRegistry scrape |
| 指标 | 直方图桶边界 | `metrics.go:46` 显式 8 桶 | `serviceLevelObjectives` 同 8 值 | UNKNOWN | 未做容器级 scrape 对拍 |
| 错误边界 | `Rpc` / `Raw` / `StatusRuntimeException` / 其它 | grpc-go `status.FromError` 四档 | `ArticleServiceGrpcImpl:119-152` 四档齐 | EQUIVALENT | 纯 Mockito |
| 错误边界 | **普通 `DataAccessException`** | 该路径 Go 本来就返 `INTERNAL` | 落 `UNKNOWN`（DRIFT-ARTICLE-RPC-003） | **SEMANTIC MISMATCH** | 静态 |
| 配置 | server 超时 70000ms | `article.go:36` + `observability/rpc.go:19-23,30-38` | `observability.rpc-timeout: 70s` + 显式挂载（`ArticleRpcApplication:108-111`） | EQUIVALENT | 真上下文 |
| 配置 | security 客户端 10000ms | yaml `SecurityConf.Timeout: 10000` | `grpc.client.security-rpc.deadline: 10s` | EQUIVALENT | 静态 |
| 配置 | message 客户端 2000ms（Go 未配 ⇒ 默认） | `zrpc/config.go:31` → 消费点 `zrpc/client.go:70-71` | `deadline: 2s`（O-6 已改） | EQUIVALENT | 静态 |
| 配置 | **每通道独立 brokers** | `KqConf.Brokers` 逐通道 | 声明但从不消费（DRIFT-ARTICLE-RPC-007） | **OBSERVABLE DIFFERENCE** | 静态 |
| DDL | 5 张表列型 / 约束 / 索引 | `article.sql` + `002_community_revision.sql` + AutoMigrate | `V8`/`V9`/`V10`（**仅大小写 + 多 3 条 COMMENT**，逐列 diff 已跑） | EQUIVALENT | 静态（逐行 diff） |
| DDL | 时间列 | `timestamptz` | `timestamptz` ⇒ **DRIFT-MSG-V-007 类偏移在本域不成立** | EQUIVALENT | 静态 |
| 启动 | **schema 门禁时机** | 建 server / 起 MQ **之前** panic | `ApplicationReadyEvent`（DRIFT-ARTICLE-RPC-008） | **OBSERVABLE DIFFERENCE** | 静态 |

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
| `security.rpc` | 任何调用 | 恒抛（不可用实现） | **SEMANTIC MISMATCH**（S-5，阻塞） |
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
| **O-2** | 6 个指标全名；已改 | 静态复核 + `SimpleMeterRegistry` scrape 一致。⚠️ 只对了**名字**（`# TYPE` 行），**未对桶边界** ⇒ U-7 | ✅ 维持（⚠️ 范围不全） |
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

### DRIFT-ARTICLE-RPC-002 —— `updated_at` 在所有更新路径上永不推进

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

### DRIFT-ARTICLE-RPC-003 —— 公开读的三处 Go `INTERNAL` 退化为 `UNKNOWN`（业务码 5001 → 1015）

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

### DRIFT-ARTICLE-RPC-004 —— `updateById` 会写已软删行；Go 的 `Save` 会拒绝

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

### DRIFT-ARTICLE-RPC-005 —— schema 门禁发生在开始收流量之后

- **Component**: 启动 / 生命周期
- **Operation**: 进程启动
- **Category**: Lifecycle（§21）
- **Go Behavior**: `article.go:41` `model.NewArticleRepo` → `InitDB`（`db.go:62-71`）校验触发器，失败 ⇒ `panic`；该调用**在** `article.go:44` `zrpc.MustNewServer` 与 `:57-60` 两个 `kq.MustNewQueue` **之前** ⇒ 缺 `article_revision_immutable` 时进程**从不收流量**。
- **Java Behavior**: `ArticleSchemaGate.verifySchema()` 监听 `ApplicationReadyEvent`（`ArticleSchemaGate.java:73`）⇒ 此时 gRPC server 已监听、Kafka listener 已启动。
- **Difference**: 存在一个「已在收流量、但不可变保证缺失」的窗口；窗口内任意一次误 UPDATE 都能改写已发布正文且**不报错**。
- **Observable Impact**: 短暂窗口内接受写流量；随后 Spring 因异常关闭上下文。
- **Likely Cause**: Spring 的"启动后钩子"比"刷新前钩子"容易写，于是选了一个语义上更晚的时机。
- **Must Preserve**: 门禁必须在 server / consumer 对外可用**之前**生效。
- **Recommended Verification**: 启动期注入"缺触发器"的 schema，断言**从未**建立过监听（对比现有 `ArticleSpringContextWiringTest#flywayActuallyCreatedArticleTables` 只断言表存在）。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Risk**: MEDIUM
- **Evidence**: 静态（两侧启动序列逐行读过）。

### DRIFT-ARTICLE-RPC-006 —— outbox 中继的 `pollIntervalMs<=0` 兜底根本没接上

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

### DRIFT-ARTICLE-RPC-007 —— 每通道独立的 Kafka brokers 是"声明但从不消费"的配置

- **Component**: 配置
- **Operation**: 全部 Kafka 生产/消费
- **Category**: Configuration（§20）
- **Go Behavior**: `kq.KqConf.Brokers`（`kq/config.go:14`）逐通道独立，`svc/service_context.go:65-71` 的三个 `kq.NewPusher` 与 `kq.MustNewQueue` 各自读自己的 `Brokers`。
- **Java Behavior**: `ArticleRpcProperties` 完整声明了 `KafkaConf.brokers/name/offset`（`ArticleRpcProperties.java:210-215`），yml 里也填了值（`application.yml:141-168`），但**没有任何代码读它们**；实际生效的是全局 `spring.kafka.bootstrap-servers`（`:65`）。
- **Difference**: 改 `article-rpc.kq-consumer-conf.brokers` 在 Java 侧**毫无效果**，在 Go 侧会切 broker。当前两侧取值恰好相同（`kafka:9092`），所以今天没有行为差异。
- **Observable Impact**: 无（今天）；配置面**误导性**。
- **Likely Cause**: 「把 Go yaml 的字段名 1:1 搬过来」的机械对齐 —— 表达层对齐做到了，消费没跟上。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Risk**: LOW
- **Evidence**: 静态（grep `getKqConsumerConf()\.getBrokers|getKqPusherConf()\.getBrokers|getArticleSync.*Conf()\.getBrokers` 零命中；`ArticleDomainService:662-664` 只取 `.getTopic()`）。

### DRIFT-ARTICLE-RPC-008 —— `@Scheduled` 首次执行时机与 Go ticker 不同

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

### DRIFT-ARTICLE-RPC-009 —— 通知标题取冻结 payload，Go 取当前行（**原台账判成"两者必相等"，该论证已证伪**）

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
| **U-3**（新） | `seekAfterError=true` + `FixedBackOff(0,0)` 在**真 Kafka** 上到底是"丢弃"还是"进程内无限重投"？ | `kq/config.go:24` `ForceCommit default=true`；消费点 `kq/queue.go:230-242` ⇒ 失败**也**走到 `:240` `CommitMessages` ⇒ **丢弃** | spring-kafka 4.0.7 源码：`SeekUtils.java:107-135` `doSeeks` 对**首条**（失败记录）先问 `recovery.recovered(...)`，返回 true ⇒ **不 seek** ⇒ 实际跳过；`FailedRecordProcessor.java:150-156` 的 javadoc 明确写 "records will be **redelivered on the next poll**" | 只有**源码**级证据；无真 Kafka 对拍，且 `remaining` 列表在 RECORD 模式下是否只含失败记录未实测 | 起一套真 Kafka，让 `ArticleReviewConsumer` 撞一次必然失败的消息，观察该 offset 是否前进、是否重复投递 |
| **U-4**（新） | S-3 的**依据**已被证伪，那"已改"还成立吗？ | 同上 | RAG 的 javadoc（`ArticleRpcApplication.java:143-154`）引用的是 `commit(consumer, container, offset+1)`，**该分支只在 `commitRecovered=true` 时执行**（`SeekUtils.java:229`），而 `FailedRecordProcessor.java:57` `commitRecovered` 默认 **false**，本模块**从未** `setCommitRecovered(true)` ⇒ 引用的那段代码**不在本模块的执行路径上** | 结论方向（对齐丢弃）大概率仍成立，但**现有论证是错的**；锁测试 `kafkaContainersCarryTheForceCommitErrorHandler` 只断言 `isSeekAfterError()==true`，**分不清"因为这个理由对"与"碰巧对"** | 同 U-3；并把锁测试改成断言**行为**（失败一次后该 offset 已提交且不再投递），不是断言属性值 |
| **U-5**（新） | `acceptSyncResult` 的**成功**路径在真 PG 上从未被验证 | `article_sync_result_consumer.go:74-153` | ~~现有真 PG 只测了 `domainOutboxFailureRollsBackEverything`（失败回滚）与两个 `getPublicArticle`~~ | ✅ **已关闭**（2026-10-24）：补 CASE-R11 的 **3 条**真 PG 用例，覆盖四步联动（revision/指针/域事件/status）、通知条件（首次发布 vs 已发布过）、标题双来源；**并因此发现 `DRIFT-ARTICLE-RPC-009`**（详见本轮修复节） |
| **U-6**（新） | `updated_at` 该用哪种方式补（`@TableField(fill)` + `MetaObjectHandler` / mapper 里显式 SQL / DB 触发器）？ | GORM `autoUpdateTime` 由 ORM 承担 | RAG 侧三选一 | 这是**实现选择**，不是判定阻塞项；但选 `DB 触发器` 会让"已存在的 46 处批量改"之类的口径需要重新评估 | 由修复方决定；选定后必须配一条真 PG 用例（CASE-R04） |
| **U-7**（新） | Micrometer 直方图的实际输出（`_seconds` 后缀 + 8 个桶边界 + `_count/_sum`）与 Go `HistogramVec` 是否逐桶一致？ | `metrics.go:41-49`（显式 8 桶） | `ArticleRpcMetrics:131-139`（`serviceLevelObjectives` + `publishPercentileHistogram`） | 现有断言只比对 `# TYPE` 里的**名字**（`ArticleSemanticsRegressionTest:177-220`），**没比对 bucket 边界**；且 `publishPercentileHistogram` 可能追加额外桶 | 容器级 `/actuator/prometheus` scrape 后逐行 diff `..._bucket` |
| **U-8**（新） | `@Scheduled` 首次执行的 `initialDelay` 默认值 | `article.go:94` ticker ⇒ 首次在 1s 后 | 未读 `ScheduledAnnotationBeanPostProcessor` 源码 | DRIFT-ARTICLE-RPC-008 的 Java 侧一半 | 读 Spring 源码或起真容器看首两次触发时间戳 |
| **U-9**（新） | 并发（8 handler）下的 offset 提交/跳过语义两侧是否一致 | `kq/queue.go:219-246`（8 个 processor 各自 `CommitMessages`，**不保证连续**） | RECORD + 上面那套 handler | 两边都**没有**活体对拍；顺序错乱窗口未量化 | 真 Kafka 压测 + 逐条比对 |
| **U-2**（沿用） | 锁顺序反转 | Go 侧两条路径都由 `RetractPublicationTx` 的 `FOR UPDATE` 串行化 | Java 已统一成 article → publication，但 `retractAndUpdate` 多了一次 `selectByIdForUpdate` 提前 return | 真并发用例未写（CASE-R12） | 同左 |
| **U-4'**（沿用） | 双侧活体对拍 | Go 依赖 `etcd:2379`，本机拉不起 | — | 本轮仍 **0** 条 | 起 etcd |
| **U-5'**（沿用） | S-2 / S-3 / S-4 / S-7 新回归网**非假绿**未证 | — | 只有 S-1 做过变异验证 | AGENTS.md §3.3 | 逐条把修复改回去看用例是否转红 |

## 10. 最终判定（§39 的 18 项 checklist）

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边 public behavior 是否一致 | ❌ | DRIFT-001~004 |
| 2 | 正常路径是否一致 | ⚠️ | 写路径一致；**读路径丢两列**（001） |
| 3 | 边界输入是否一致 | ⚠️ | page/size、`secondary_tags` 空、`sort_by` 未知值均一致；`poll-interval-ms<=0` 不一致（006） |
| 4 | NotFound / Empty / Null 是否一致 | ❌ | jsonb 列的 null 语义在三个方法上不一致（001） |
| 5 | Error meaning 是否一致 | ❌ | 公开读 5001→1015（003）；`update_time` 陈旧（002） |
| 6 | Error propagation 是否一致 | ⚠️ | Kafka 侧 U-3；gRPC 侧四个 catch 分档一致 |
| 7 | External dependency failure 是否一致 | ⚠️ | DB/对象存储已覆盖；**security 域仍不可用**（S-5，阻塞） |
| 8 | Cache hit / miss 是否一致 | — | **N/A**：两侧均无缓存层 |
| 9 | Transaction boundary 是否一致 | ✅ | 4 处边界与顺序逐条一致；真 PG 证明 AOP 代理生效 |
| 10 | State transition 是否一致 | ❌ | `updated_at` 永不推进（002） |
| 11 | Side effects 是否一致 | ⚠️ | 副作用**集合**一致；失败位点处置待 U-3 |
| 12 | Ordering 是否一致 | ✅ | 作者/公开两条排序规则各自对齐（S-2 已改 + 真 PG） |
| 13 | Retry 是否一致 | ✅ | 两侧均**零**进程内重试（`FixedBackOff(0,0)` ↔ kq 无重试循环） |
| 14 | Timeout scope 是否一致 | ✅ | 70s / 10s / 2s 三处均为 **operation 级**；SQL 超时可取消已接线 |
| 15 | Concurrency semantics 是否一致 | ⚠️ | 行锁集合一致；软删行可写（004）+ 锁顺序（U-2）+ offset 语义（U-9） |
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
（`gorm:"autoUpdateTime"`）；消费点 `gorm@v1.31.2/callbacks/update.go:243-259`
（`Updates(map)` 路径）与 `:279-281`（`Updates(struct)` 路径）。

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
`gorm@v1.31.2/soft_delete.go:76-96` `SoftDeleteQueryClause.ModifyStatement`
在 `!stmt.Unscoped` 时追加 `WHERE deleted_at = <ZeroValue>`，
经 `:98-100 UpdateClauses` 挂到 UPDATE。

**② 0 行之后的回落是 `ON CONFLICT DO UPDATE`，不是报错**：
`finisher_api.go:117-121`
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

### ⚠️ 真正存在的相邻差异（另立条目，未修）

Go 的 `Save` 走 `finisher_api.go:113-114` 的 `Selects = append(Selects, "*")`
——「update 时使用<b>全部</b>字段，含零值字段」；
MP `updateById` 默认 `updateStrategy = NOT_NULL`，<b>只写非 null 字段</b>。
⇒ 一个「把某列显式置为零值」的更新，两侧结果不同。
**这才是真实差异**，但它与 004 描述的不是同一件事，且需逐调用点核对 Go 是否真的用 `Save`（部分路径用 `Updates(map)`）⇒ 未展开。

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
| `DRIFT-ARTICLE-RPC-005` schema 门禁时机 | ❌ OBSERVABLE DIFFERENCE（MEDIUM） | 🔴 **OPEN**（需把门禁挪到 gRPC server 监听**之前**，非本轮范围） |
| `DRIFT-ARTICLE-RPC-007` 每通道独立 Kafka brokers | ❌ OBSERVABLE DIFFERENCE（LOW） | 🔴 **OPEN**（配置面误导，今天无行为差异） |
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
