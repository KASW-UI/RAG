<!-- @new 2026-10-24 · comment 域 rpc 组件首轮 Go ↔ RAG 语义对照 -->
<!-- 产出归属：`align/comment` 工作树（`/Users/pr/Documents/java&golang/RAG.worktrees/comment`）· 基线 commit `8422116` -->

# DRIFT 台账 · `service/comment/comment-rpc`

> **核对时间：2026-10-24**
> **真源：** `Sea-RideTheWind_golang/service/comment/rpc`（冻结版，go-zero v1.10.2 + GORM）
> **本轮性质：** 首轮**组件级**语义对照（AGENTS.md §3.5）。此前本组件**零台账**。
>
> ⚠️ **证据强度分层**（§0）：
> - ✅ **真 PG**：`CommentSchemaPgGateTest`（18 例，真起 Testcontainers PG）
> - ✅ **真构建**：`mvn -o -pl service/comment/comment-rpc -am install` ⇒ `EXIT=0`（本轮实测）
> - ✅ **源码逐行对照**：Go 侧 33 个 `.go`（3,620 行）逐文件读过，非抽样
> - 🔴 **无双侧活体对拍** —— Go 侧依赖 `etcd` + Kafka + 真 PG，起不来；RAG 侧依赖 Nacos + Kafka + Redis
> - 🔴 **未做变异测试** —— 「测试不是假绿」这一半没验
>
> ⇒ 本台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | 🔴 **`SEMANTICALLY NOT EQUIVALENT`** |
| SEMANTIC MISMATCH | **7 条**（§2） |
| OBSERVABLE DIFFERENCE | **6 条**（§3） |
| 等价确认 | **约 70 个判定点**（§4） |
| 组件规模 | Go 3,620 行 / 33 文件 ⇒ RAG 7,455 行 / 40 类（**RAG 是 Go 的 2.06 倍**） |
| 构建 | `mvn -o -q -pl service/comment/comment-rpc -am -DskipTests install` ⇒ **`EXIT=0`**（本轮实测） |
| 本台账范围 | **仅 `comment-rpc`**。`comment-api` / `comment-common` 各自独立台账 |

> 🟥 **最严重的 3 条（MISMATCH-01/02/03）都在「事实投递链路」上**，
> 且**都是 Go 侧的显式 `tx.Commit()` 在 Java 侧被 Spring 的回滚规则吃掉**。
> 这不是「Java 写错」，是**「Go 的事务边界比 Java 的注解粒度细」**这类漂移 —— 1:1 直译必然踩。

---

## 1. 落地形态（Go Component → Java Behavior）

| Go 侧 | RAG 侧 | 说明 |
|---|---|---|
| `comment.go` + `cmd/fact-authority` + `cmd/fact-dispatch`（**3 个进程**） | **1 个 Spring Boot JVM**（`CommentRpcApplication`） | Go 的 authority / dispatch 是**独立进程 + 独立端口**；RAG 合并后变成配置开关 ⇒ 部署形态差异，见 §3-D4 / §3-D5 |
| `internal/logic/*` **7 个 logic** + `subject_fallback.go` | **`CommentDomainService`**（7 个用例） | 与 Go 同形：logic 层**无事务** |
| `internal/model/comment.go` 的 **3 个 `conn.Transaction`** | **`CommentTransactionalService`**（3 个 `@Transactional`） | ⚠️ 必须独立 Bean —— 否则 `@Transactional` self-invocation 静默失效（§3.3） |
| `internal/cache/*` 6 文件 12 方法（共享 1 个 `redis.Client` + 1 个 `singleflight.Group`） | **`CommentCacheService`**（合成 1 个类） | 合理：Go 侧本就是同一个 struct 的方法集 |
| `internal/mqs/audit_consumer.go` | **`CommentAuditListener`**（`@KafkaListener`） | |
| `internal/model/domain_fact.go` + `fact_delivery.go` + `fact_authority.go` | `CommentDomainFactService` + `CommentFactDeliveryService` + `CommentAuthorityLookupService` + `CommentDeliveryValidator` | RAG 拆得更细（4 类 vs Go 3 文件），**拆分本身是表达层**，问题在语义 |

---

## 2. 🔴 SEMANTIC MISMATCH（7 条 —— 需要对齐）

### MISMATCH-01 · 冻结信封非法 ⇒ `DeliveryBlocked` **永不落盘**

| 项 | 值 |
|---|---|
| **Go 事实** | `internal/model/fact_delivery.go:79-80` 置 `delivery_status = DeliveryBlocked` → **`:83` `tx.Commit()`** → `:89` 返回 `ErrFrozenEnvelopeBlocked` |
| **RAG 现状** | `CommentFactDeliveryService.java:70` `@Transactional`（**无 `noRollbackFor`**）→ `:92` `outboxMapper.markBlocked(...)` → `:97` `throw` |
| **机理** | Spring 默认规则：`RuntimeException` ⇒ **回滚**。`Wire.ERR_FROZEN_ENVELOPE_BLOCKED` 继承 `RuntimeException` ⇒ **刚执行的 UPDATE 被回滚** |
| **可观察后果** | 该行永远停在 `delivery_status=0` ⇒ 调度器每秒重新领到它 ⇒ **BLOCKED 永不落盘**，Go 的「等人工迁移」语义完全丢失，且形成**无限重领活锁** |
| **修法** | `markBlocked` 拆到独立 `REQUIRES_NEW` 事务；或 `@Transactional(noRollbackFor = ...)` + 显式提交 |

### MISMATCH-02 · 投递失败 ⇒ `DeliveryFailed` + `retry_count + 1` **永不落盘**

| 项 | 值 |
|---|---|
| **Go 事实** | `fact_delivery.go:99-101` `Updates({delivery_status: Failed, retry_count: retry_count + 1})` → **`:104` `tx.Commit()`** → `:110` 返回错误 |
| **RAG 现状** | `CommentFactDeliveryService.java:121` `markFailed(...)` → `:126` `throw sendErr` |
| **机理** | 同 MISMATCH-01。且**更糟**：失败路径的异常类型不统一 —— `Wire.DataCenterAcceptanceRejectedException`（`Wire.java:415-427`）是 `RuntimeException` ⇒ 回滚 ⇒ `retry_count` **恒 0**；若是受检异常（`IOException`）⇒ Spring **不回滚** ⇒ 反而落盘 |
| **可观察后果** | 同一失败路径**因异常类型不同而 DB 状态不同**；Go 侧则是无条件 Commit。无限重试且重试计数不增长 ⇒ 排障时看不出「已经重试了多少次」 |
| **修法** | 同 MISMATCH-01 |

### MISMATCH-03 · `technical_received_at` 写成了**本地投递时间**，不是 DC 回执里的接收时刻

| 项 | 值 |
|---|---|
| **Go 事实** | `fact_delivery.go:94` `received, receiptOK := communityfact.ValidateReceipt(event, receipt)` → `:115` `"technical_received_at": received`（= DC 回执的 `received_at`，`.UTC()` 后落库） |
| **RAG 现状** | `CommentFactDeliveryService.java:112-114` 拿到了含 `Instant received` 的 `validated` 但**丢弃未用**；`:130-132` `markAccepted(..., now, now)` ⇒ `technical_received_at == delivered_at == 本地投递时间` |
| **可观察后果** | DC 的接收时刻被本地时刻顶替。`CommentAuthorityLookupService.java:351-353` 的权威读响应 `technical_receipt.received_at` 取自该列 ⇒ **wire 上可观察的值不同** |
| **修法** | `markAccepted(...)` 的两个时间参数分别传 `validated.get().atOffset(UTC)` 与 `now`（mapper 签名本就区分了两个参数，是**调用方传错**） |

### MISMATCH-04 · 事实载荷的**未知字段严格性**被类级注解取消

| 项 | 值 |
|---|---|
| **Go 事实** | `fact_delivery.go:33` `communityfact.StrictDecode(..., &payload)`；`service/common/communityfact/wire.go:121-131` `decoder.DisallowUnknownFields()` ⇒ 载荷多一个字段 ⇒ 解码失败 ⇒ `validCommentDelivery` false ⇒ 该行 **BLOCKED** |
| **RAG 现状** | `CommentDomainFactService.java:220` `@JsonIgnoreProperties(ignoreUnknown = true)` 挂在 `CommentFact` 上 ⇒ **类级注解覆盖** `Wire.MAPPER` 的 `FAIL_ON_UNKNOWN_PROPERTIES=true`（`Wire.java:115-116`）；`CommentDeliveryValidator.java:173-180` 静默忽略未知字段 |
| **可观察后果** | 载荷被篡改 / 来自更高 schema 版本时，Go **拒发并 BLOCKED**，RAG **照发** ⇒ DC 收到一个 Go 源站会判定为非法的事实 |
| **讽刺点** | `CommentDeliveryValidator.java:30-36` 的 javadoc 恰好写着「用错 mapper 会让『载荷里多一个字段』这种篡改悄悄通过校验」，而 `:38-42` 注入的其实是**共享的宽松 mapper**（不是 javadoc 说的「专用 mapper」）—— 严格性恰恰被这个注解取消 |

### MISMATCH-05 · 载荷里三个时间字段**无格式校验**

| 项 | 值 |
|---|---|
| **Go 事实** | `domain_fact.go:77-79` `EventTime/OccurredAt/AvailableAt time.Time` ⇒ `encoding/json` 解码必须是 RFC3339，否则 `StrictDecode` 报错 ⇒ BLOCKED |
| **RAG 现状** | `CommentDomainFactService.java:299-306` 用 `String` 承载 ⇒ 任意字符串都能通过；`CommentDeliveryValidator.java:151-160` **只对 `occurred_at` 做 `Instant.parse`**，`event_time` / `available_at` 完全无校验 |
| **可观察后果** | Go 会 BLOCKED 的畸形载荷（如 `"event_time":"not-a-time"`）在 RAG 侧通过校验并被投递；`CommentAuthorityLookupService.java:326-327/396-398` 的零值判定只看 `0001-01-01` 前缀，也拦不住 |

### MISMATCH-06 · 信封数值字段**未禁 String→int 强转**

| 项 | 值 |
|---|---|
| **Go 事实** | `encoding/json` 对 `{"schema_version":"1"}`（string→int）**报错** ⇒ `StrictDecode` 失败 ⇒ BLOCKED |
| **RAG 现状** | `Wire.java:115-116` 的 `Wire.MAPPER` 只配了 `FAIL_ON_UNKNOWN_PROPERTIES`，**未禁 String→int 强转** ⇒ `"schema_version":"1"` 被读成 `1`，通过 `CommentDeliveryValidator.java:108` 的 `event.schemaVersion != 1` 检查 |
| **可观察后果** | 被篡改的信封（数值字符串化）在 Go 侧 BLOCKED、在 RAG 侧通过 |
| **备注** | `Jackson2ObjectMapperConfiguration.java:82-90` 配的 Textual 强转禁令是给 **Kafka 审计消息**用的共享 mapper；`Wire.MAPPER` 是**另一个独立 mapper**，没吃到这条禁令 |

### MISMATCH-07 · `comment_index` / `comment_content` INSERT 时 `updated_at` 取值不同

| 项 | 值 |
|---|---|
| **Go 事实** | `comment.go:80-90` 构造 `CommentIndex` 时**只赋 `CreatedAt: createTime`**，`UpdatedAt` 留零 ⇒ GORM `callbacks/create.go:344-347` 用 `curTime`（**入库时刻**）填 |
| **RAG 现状** | `CommentTransactionalService.java:179` `index.setUpdatedAt(createdAt)` ⇒ **评论创建时刻**（Kafka 消息里的 `create_time`） |
| **可观察后果** | INSERT 瞬间两值不同。该列不进 wire（`get_comment_logic.go:139` 只取 `created_at`），此后每次 UPDATE 两侧都写 `CURRENT_TIMESTAMP` ⇒ **不可观察窗口极窄**，但值确实不同 |
| **严重度** | 列在 wire 上 ⇒ 按 `comment_content`/`comment_index` 的 `updated_at` 是否被任何读取路径使用，本次**未找到读取方** ⇒ 记为 MISMATCH 但影响面窄 |

---

## 3. OBSERVABLE DIFFERENCE（6 条 —— 行为等价，但实现路径 / 部署形态不同）

| # | 判定点 | Go 侧事实 | RAG 侧现状 | 性质 |
|---|---|---|---|---|
| **D1** | `comment_domain_fact_outbox.created_at` 的取值来源 | `domain_fact.go:29` GORM `autoCreateTime` ⇒ INSERT 显式带 `created_at` = Go 进程当时 | `CommentDomainFactOutboxMapper.java:55-64` 的 INSERT **不带** `created_at` ⇒ 走 DDL 默认（`V6:24` `DEFAULT CURRENT_TIMESTAMP`）= **事务开始时刻** | 值不同（两者都是「now」，当前不改变投递顺序，但 `ORDER BY created_at` 的排序键口径不同） |
| **D2** | 时间精度 | `domain_fact.go:94/98` `time.Now().UTC()` = **纳秒** | `CommentDomainFactService.java:120/124` `Instant.now()` = 微秒级 | `event_time`/`available_at` 小数位数不同 ⇒ 载荷字节不同（`validCommentDelivery` 比的是**瞬时**与 **JCS 哈希**，语义不受影响） |
| **D3** | `EventTime` 的 UTC 归一化 | `domain_fact.go:96` `fact.EventTime = fact.EventTime.UTC()` → `:97` `OccurredAt = EventTime` | `CommentDomainFactService.java:119-124`：`eventTime` 非 null 时**原样使用** | 当前 3 个调用方都传 UTC-Z ⇒ **当前不可观察**；`append` 是 public API，第 4 个调用方若传 `+08:00`，envelope 字节会漂移 |
| **D4** | authority 端点的部署形态 | `cmd/fact-authority/main.go:52/75-80` = **独立进程 + 独立监听端口**，默认就在跑 | `CommentAuthorityEndpointConfig.java:42` `@ConditionalOnProperty(comment.authority.enabled=true)`；`application.yml:196` 默认 `false`；且 pom 无 `spring-boot-starter-web` ⇒ 即使打开也起不来 | ⚠️ **建议登记为签字偏离**（见 `docs/Intentional_Deviations/comment/rpc.md`） |
| **D5** | fact-dispatch 的部署形态 | `cmd/fact-dispatch/main.go:52-77` = 独立进程，缺配置即退出；默认就在投递 | `CommentFactDispatchScheduler.java:58-63` + `application.yml:178` `dispatch-enabled: false` ⇒ 默认整条投递链路**不参与运行** | ⚠️ **建议登记为签字偏离** |
| **D6** | 缓存读穿失败时的 **Redis 指标 label** | `comment_subject_cache.go:31-35`：`GetSubjectCache` 返回 err 时，`GetSubjectWithCache` 打 `action="GetSubjectWithCache"`；同时 `GetSubjectCache` 内部对 **Redis 真异常**再打一次 `action="GetSubjectCache"` ⇒ **Redis 真异常时打两个指标** | `CommentCacheService.java:182` 把**所有**异常（含 JSON 解析失败）都记成 `action="GetSubjectCache"`；`getSubjectWithCache` 在 `readSubjectCache` 返回 null 时**不再打点** ⇒ **只打一个，且 label 名不同** | 指标 label 集合不同 ⇒ Prometheus 曲线口径不同。**JSON 解析失败**这一项 Go 侧也不打 `GetSubjectCache`（`comment_subject_cache.go:96-98` 注释明说），RAG 却打了 ⇒ RAG 侧**多打**一个 |

---

## 4. 等价确认（钉住，避免下轮重复查）

### 4.1 七个 RPC 用例的行为决策点

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| `CreateComment` 校验顺序与文案 | `create_comment_logic.go:63-75`（target 空 → content 空） | `CommentDomainService.java:142-151` | ✅ 文案逐字一致 |
| `CreateComment` **不写库**，只推 Kafka | `:102-132` | `:177-199` + `CommentAuditPublisher` | ✅ |
| `CreateCommentResp.subject_count` **恒 0** | `:139` | `:208` | ✅ 未擅自「修好」 |
| `resolveOwnerID` 回落失败时返回**原始 err**（⇒ 1012 而非 5003） | `create_comment_logic.go:152-155` `return 0, err` | `CommentDomainService.java:235-239` | ✅ 照搬了 Go 的「疑似 bug」 |
| `resolveSubjectFallback` 的三个判定（EqualFold "article" / article 非空 / authorId > 0） | `subject_fallback.go:13-28` | `CommentDomainService.java:255-260` + `ArticleRpcClient.java:80-94` | ✅ 含 `ParseInt` 失败与 `≤0` 都折叠成 null |
| `GetComment` 分页归一（page≤0→1 / size≤0→20 / size>100→100） | `get_comment_logic.go:56-67` | `:296-307` | ✅ |
| `GetComment` **排序默认值反直觉**（`sort_type==0 ⇒ 热度序`） | `:78-81` | `:310` | ✅ |
| `GetComment` `OnlyNormal` **恒 false** ⇒ 已删/审核中的评论也在列表里 | `:98` | `:317` | ✅ 未修正 |
| `GetComment` index/content **按下标配对**（缺 content 行 ⇒ 串位） | `:122-127` | `:324-329` | ✅ 刻意保留 Go 的缺陷 |
| `GetCommentResp.subject.owner_id` **恒 0** | `:152-159`（逐字段赋值，无 owner_id） | `:350-360` | ✅ |
| `DeleteComment` 权限 = 本人 **或** up 主 | `delete_comment_logic.go:97` | `:457` | ✅ |
| `DeleteComment` **重复删除幂等**（`state != 2` 才回退计数） | `comment.go:287` | `CommentTransactionalService.java:345` | ✅ |
| `LikeComment` 的 **no-op 不产出事实**（4 个分支） | `comment.go:518/528/534/542` | `CommentTransactionalService.java:456-487` | ✅ |
| `LikeComment` no-op **仍插入锁锚行**（Go 的现状） | `comment.go:483-487` | `:429` | ✅ |
| `LikeComment` **兜底发 2001 而非 5004**（Go 日志记 5004、线上发 2001） | `like_comment_logic.go:61-64` | `CommentExceptions.likeOwnerLookupFailed()` | ✅ 照搬 |
| `ManageComment` **无鉴权** | `manage_comment_logic.go:59-86` | `CommentDomainService.java:613-663` | ✅ 刻意不补 |
| `ManageComment` 位偏移（PIN=2 / FEATURE=3） | `:66-86` | `:627-638` | ✅ |
| `UpdateSubjectState` 只有 up 主鉴权，**无 admin 分支** | `update_subject_state_logic.go:85` | `:699` | ✅ 注释与实现不一致，**照实现** |
| `UpdateSubjectState` 越权文案是「非up主无法评论」 | `:88` | `CommentExceptions.subjectStateForbidden()` | ✅ 逐字照搬（含语义错位） |
| `ReportComment` 重复举报被吞但仍返回 success | `comment.go:243` + `report_comment_logic.go:97-99` | `:582-595` | ✅ |
| proto3 未知枚举值 | Go 是 `int32(in.State)` 原样透传 | RAG 用 `getStateValue()`/`getReasonValue()`/`setActionTypeValue()` | ✅ **RAG 这里比 Go 更严谨**，但结果相同（未知值不被改写成 0） |

### 4.2 事务与行锁

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| `InsertCommentTx` 幂等探针（同 ID 身份冲突 ⇒ 报错） | `comment.go:39-60` | `CommentTransactionalService.java:117-148` | ✅ |
| `InsertCommentTx` 的 `subjectRef` 校验**在**参数校验**之前** | `:29-31` → `:33-35` | `:111` → `:113` | ✅ 顺序一致 |
| `DeleteCommentTx` 的 `SELECT … FOR UPDATE` | `comment.go:258` | `CommentIndexMapper.java:65-66` | ✅ |
| `DeleteCommentTx` target 对不上也报 `CommentNotFound` | `:267-269` | `:328-331` | ✅ |
| `LikeCommentTx` 的**三步顺序**（播锁锚 → FOR UPDATE → 读评论） | `comment.go:483-510` | `:428-447` | ✅ |
| `LikeCommentTx` 读评论**不加锁**（点赞与删除可并发） | `comment.go:499` | `CommentIndexMapper.findOneByIdPlain` | ✅ |
| `applyInteractionDeltas` 的三个条件性列 + `len(updateCols)>0` 才执行 | `comment.go:561-583` | `:500-511` + `CommentIndexMapper.java:207-228` | ✅ 条件等价 |
| `attribute & ~mask`（**不是** `attribute - mask`，后者会借位） | `comment.go:163` | `CommentIndexMapper.java:189` | ✅ |
| `upsertCommentSubject` 的 `subject.total_count + 1` **表名限定前缀不能省** | `comment.go:111` | `SubjectMapper.java:87` | ✅ |
| `upsertCommentSubject` 冲突分支**不更新** owner_id/state/attribute/updated_at | `comment.go:115-118` | `SubjectMapper.java:87-88` | ✅ |
| GORM `Updates(map)` 自动推进 `updated_at` | `comment.go:299` / `:187` | `SubjectMapper.java:108/125` | ✅ RAG 显式写出 |

### 4.3 缓存层

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 5 个 key 格式逐字一致（含 `comment:subject:%s` **不含 targetType**） | `cache/keys.go:5-20` | `CommentRedisKeys.java:30-61` | ✅ 连 Go 的 key 碰撞缺陷都照搬了 |
| SCAN 模式 `comment:index:%s:%s:*` | `comment_invalidation_cache.go:19` | `CommentRedisKeys.java:73-75` | ✅ |
| 4 个 TTL（5min / 3min / 10min / 24h） | 4 个 `default*TTL` const | `CommentCacheService.java:74-83` | ✅ |
| **四类读穿的错误语义各不相同**（前两者降级回源、后两者直接失败） | `comment_subject_cache.go:31-35` / `comment_ids_page_cache.go:39-44` / `comment_index_cache.go:41-47` / `comment_content_cache.go:41-48` | `CommentCacheService.java` 类级 javadoc + 各方法 | ✅ 逐条保留 |
| 缓存 JSON 的字段名是 **Go 的结构体字段名**（`Id`/`TargetType`，非 snake_case） | Go 无 json tag ⇒ 按导出字段名输出 | `CommentCacheService.java:58-64` + `JsonProperty` 钉死 | ✅ 灰度期互通 |
| 按入参顺序重排输出 | `comment_index_cache.go:108-116` / `comment_content_cache.go:111-119` | `:362-373` | ✅ |
| `preloadNextReplyIDsPage` **零调用点** ⇒ 不迁移 | `comment_ids_page_cache.go:91-139`（全仓无调用） | `CommentCacheService.java:213-215` 说明不迁移 | ✅ 不为死代码预留结构 |

### 4.4 Kafka 消费侧（4 条出口方向）

| 判定点 | Go 侧事实 | RAG 侧现状 | ✅ |
|---|---|---|---|
| 解析失败 ⇒ **丢弃**（return nil） | `audit_consumer.go:36-39` | `CommentAuditListener.java:115-122` | ✅ |
| 落库失败 ⇒ **重投**（return err） | `:48-51` | `:133-139` + `:100-106` 抛异常 | ✅ |
| 缓存失效失败 ⇒ 只记日志 | `:52-59` | `:141-155` | ✅ |
| 通知发送失败 ⇒ 只记日志 | `:117-119` | `CommentAuditPublisher` 侧 | ✅ |
| 只有 `status == 0` 才发通知 | `:61-63` | `:157-160` | ✅ |
| 通知三条规则（父评论作者 / 查父失败即放弃 / `recipient<=0 \|\| ==msg.UserId` 不发） | `audit_consumer.go:79-99` | `:181-209` | ✅ |
| `previewComment` 按 **rune** 截断 40 位 | `:122-132` | `:223-237`（`codePointCount`） | ✅ 含代理对 |
| Kafka 消息 **11 字段 snake_case + 声明顺序 + 无 omitempty** | `common/types/kqueue.go:3-15` | `CommentAuditMessage.java:47-51` + `@JsonPropertyOrder` | ✅ 字节序一致 |
| `create_time` 是**秒** | `create_comment_logic.go:114` `now.Unix()` | `:190` `now.getEpochSecond()` | ✅ |
| **同步推送**（Kafka 失败 ⇒ 5009） | `create_comment_logic.go:126-132` | `CommentAuditPublisher.java:80-81` `future.get(timeout)` | ✅ |
| 生产/消费**同一个 topic** | `comment.yaml:26` 与 `:32` | `application.yml:156` | ✅ 连 Go 的怪现状都照搬 |

### 4.5 DDL（7 表 60 列）

| 判定点 | 结论 |
|---|---|
| **列类型** | ✅ **0 处 MISMATCH**（60/60 一致）：`bigint` / `integer`（**非** `smallint`）/ `timestamptz`（**非** `timestamp`）/ `varchar(n)` / `text` / `jsonb` / `char(64)` |
| `comment_index.id` **不自增**（雪花 ID） | ✅ `V2:11` `BIGINT PRIMARY KEY` 无 IDENTITY/SERIAL；RAG `@TableId(type=INPUT)` |
| `comment_content.comment_id` 是主键（**非外键**） | ✅ `V3:15` |
| 5 个索引名 + 列序（含 `idx_target_root_time` 四列 priority 1→4） | ✅ 逐位一致 |
| `uk_comment_fact_stream`（aggregate_id + fact_version **复合**） | ✅ `V6:31-32` |
| `idx_comment_legacy_fact_aggregate`（表达式索引 + WHERE 谓词） | ✅ `V6:42-44` 逐字一致 |
| `comment_new_fact_has_dc_wire` CHECK（**NOT VALID**） | ✅ `V6:62-64` 逐字一致 |
| 信封不可变触发器（函数体 + `BEFORE UPDATE FOR EACH ROW` + 6 列清单 + RAISE 文案） | ✅ `V8:66-84` 逐字一致 |
| `comment_fact_stream` 的 version 范围 CHECK | ✅ 语义一致（约束名不同 ⇒ 表述差异） |
| **9 处时间列的 NOT NULL + DEFAULT** | 🔴 **不一致**（Go 可空无 DEFAULT / RAG `NOT NULL DEFAULT CURRENT_TIMESTAMP`）⇒ **10 处是「RAG 更严」** ⇒ 建议签字，见 `docs/Intentional_Deviations/comment/rpc.md` |
| `report_record` 表在 Go 侧**根本不存在**（`db.go:15` 的 AutoMigrate 列表不含它，3 个手写脚本也不建） | 🔴 RAG V5 建了 ⇒ Go 侧全新部署上 `ReportComment` **必然 5004** ⇒ RAG 侧是「更全」⇒ 建议签字 |

---

## 5. 未决 / 待办

| # | 事项 | 类型 |
|---|---|---|
| **U1** | MISMATCH-01/02 需**真 PG + 真 Spring 上下文**的守门测试：断言「冻结信封非法 ⇒ `delivery_status=3` 落盘」「投递失败 ⇒ `delivery_status=2` 且 `retry_count=1` 落盘」 | 🔴 修漂移 + 配回归网 |
| **U2** | MISMATCH-03/04/05/06 同属 `CommentFactDeliveryService` / `CommentDomainFactService` / `Wire` 三处，一次改动可解 4 条 | 🔴 修漂移 |
| **U3** | 本组件**无任何投递路径测试**（现有 5 个测试类全不覆盖 `dispatchOnce`）⇒ 上述结论的证据强度上限是「Spring 默认回滚规则推导」，**未实测** | ⚠️ 证据缺口 |
| **U4** | Go 侧三个 `_test.go`（`domain_fact_test.go` / `fact_process_fixture_test.go` / `fact_delivery_logging_test.go`）**默认 `t.Skip`** ⇒ Go 侧投递链路的**期望行为在 Go 仓里本身也没有活体证据** | ⚠️ 真源侧缺口 |
| **U5** | `comment_fact_stream` 的 CHECK 在 Go 侧**是否常态存在取决于迁移执行顺序**（生产顺序有 / 测试顺序无） | ⚠️ 待核 |
| **U6** | `comment_like` 的 Go 侧有**两份互相冲突的 DDL 产物**（AutoMigrate vs 手写 `001`），在 `id`（`bigserial` vs IDENTITY）与唯一约束形态上不同 ⇒ 不存在单一「Go 侧 DDL」基线 | ⚠️ 真源侧歧义 |
| **U7** | MISMATCH-07（`updated_at` INSERT 取值）—— 未找到任何读取方 ⇒ 影响面待确认 | ⚠️ 待核 |

---

## 6. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/Intentional_Deviations/comment/rpc.md`](../../Intentional_Deviations/comment/rpc.md) | 本台账的**签字偏离**落点（DDL 更严侧、`report_record` 表、部署形态） |
| [`docs/Pitfalls/comment/rpc.md`](../../Pitfalls/comment/rpc.md) | 本台账的**踩坑**落点（为什么这些坑会踩） |
| [`docs/DRIFT/comment/api.md`](./api.md) | `comment-api` 的独立台账 |
| [`docs/DRIFT/comment/common.md`](./common.md) | `comment-common` 的独立台账 |
| [`docs/DRIFT/comment/proto.md`](./proto.md) | `comment-proto` 的独立台账 |
| [`docs/DRIFT/_cross/component-coverage-matrix.md`](../_cross/component-coverage-matrix.md) | 38 组件覆盖矩阵 —— **共享文件，合并后由主会话统一更新**（本工作树不碰） |
