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
> - ✅ **真 PG + 真 MyBatis + 真事务**：`CommentRpcGoSemanticsGateTest`（11 例，**2026-10-24 修复轮新增**）
>   —— 手工搭 `SqlSessionTemplate`（`SpringManagedTransactionFactory`）⇒ mapper 的 SQL 与
>   `CommentFactDeliveryService` 的事务跑在**同一条连接**上，与生产同构
> - ✅ **变异测试**（**2026-10-24 修复轮**：10 条变异**全部**让对应守门网变红 ⇒「测试不是假绿」已验）
> - ✅ **双侧活体对拍**（**2026-10-24 对拍轮**：Go 真源 + RAG **四进程同时在跑**，**23 步**请求矩阵逐字段差分，**复跑两轮一致** ⇒ **§4.6**）
> - 🔴 ~~无双侧活体对拍~~ —— 已于 2026-10-24 补上（Go 侧 `etcd` 块删除 + 下游 RPC 指向死端点；RAG 侧 Nacos 注册关闭）
>
> ⇒ 本台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环。
>
> 🟢 **2026-10-24 修复轮**：§2 的 **7 条 MISMATCH 全部已修**，每条都配了**真 PG 守门网**并做了**变异验证**。
> 本轮同时修掉**一条本轮自己引入的偏差**（MISMATCH-02 的原始 `sendErr` 丢失）与**一条假守卫**
> （MISMATCH-06 的守门网曾「因错误原因而绿」，见该条下的「⚠️ 假守卫教训」）。
>
> 🟢 **2026-10-24 对拍轮**：起真源才发现的 **4 个真实缺陷**（此前单测全绿也测不出来）已就地修掉 —— 见 §4.6.2。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | 🔴 **`SEMANTICALLY NOT EQUIVALENT`** —— ⚠️ **但这是首轮对照时的判定；7 条 MISMATCH 已全部修复**（见「修复轮」）⇒ **当前不再有「必须对齐」的分歧项** |
| SEMANTIC MISMATCH | **7 条**（§2）—— ✅ **全部已修**（各配真 PG 守门网 + 变异验证） |
| OBSERVABLE DIFFERENCE | **7 条**（§3）—— **D1 撤销** / **D3 已修** / **D6 已修** / **D7 已修** / D2（时间精度）· D4（authority 部署形态）· D5（dispatch 部署形态）⇒ 后三条已归入签字候选 CAND-03 / CAND-09 |
| 等价确认 | **约 70 个判定点**（§4） |
| 组件规模 | Go 3,620 行 / 33 文件 ⇒ RAG 7,455 行 / 40 类（**RAG 是 Go 的 2.06 倍**） |
| 构建 | `mvn -o -q -pl service/comment/comment-rpc -am -DskipTests install` ⇒ **`EXIT=0`**（本轮实测） |
| 测试 | `mvn -o -pl service/comment/comment-rpc,service/comment/comment-api,service/comment/comment-common -am test` ⇒ **BUILD SUCCESS**；**rpc 85 / api 45 / common 27，合计 157 / 0 failures / 0 errors / 0 skipped**（2026-10-24 实测；本轮新增守门网 31 例） |
| 本台账范围 | **仅 `comment-rpc`**。`comment-api` / `comment-common` 各自独立台账 |
| **修复轮** | **2026-10-24**：§2 的 **7 条 MISMATCH 全部已修**。守门网 = `CommentRpcGoSemanticsGateTest`（**11 例，真 PG**）；**10 条变异全部变红**。⚠️ 计数以「测试」行的 **85 例**为准（后续又加了 EventTime 5 / RedisMetric 5 / Alias 6） |
| **对拍轮** | **2026-10-24**：**双侧活体对拍**已跑通（Go 真源 + RAG 四进程同跑，**23 步**矩阵，**复跑两轮一致**）。结果 **20 一致 / 8 差异**，差异**落在 3 个步骤**上：T07 / T08 根因在 **Go 侧**（SQL 必报错 + 缺 `report_record` 表）⇒ **已签字偏离**；**T20 是新增的一条**（保护层覆盖范围：未注册路径上 Go 从不返回 413 / RAG 返回 413）⇒ 登记为 D6 ⇒ **用户已签字** `SD-2026-10-24-CAND-08`（**接受 RAG 更严/更安全的一侧** ⇒ **代码零改动**）（[`api.md` §3.2](./api.md)）。对拍过程另发现并修掉 **4 个真实缺陷**（§4.6.2） |

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
| **✅ 已修** | **2026-10-24**。`@Transactional` ⇒ **`TransactionTemplate`（编程式事务）**，与 Go 的 `Begin / defer Rollback / 三处 Commit` **逐条同构**；三个写状态分支**刻意不调** `setRollbackOnly()`；错误在**事务提交之后**才抛。范式照抄同仓既有的正确实现 `favorite-rpc/FavoriteFactDispatchService`（不发明第三种写法） |
| **🧪 守门网** | `CommentRpcGoSemanticsGateTest#blockedFrozenEnvelopeIsPersistedEvenThoughTheCallThrows`（**真 PG + 真 MyBatis + 真 `DataSourceTransactionManager`**）：断言抛 `Wire.FrozenEnvelopeBlockedException` **且** `delivery_status=3` **落盘** |
| **🧬 变异实测** | 把 blocked 分支改回 `status.setRollbackOnly()`（= 修复前的 `@Transactional` 语义）⇒ 该测试**变红**（`Failures: 1`）⇒ 守门网不是假绿 |

### MISMATCH-02 · 投递失败 ⇒ `DeliveryFailed` + `retry_count + 1` **永不落盘**

| 项 | 值 |
|---|---|
| **Go 事实** | `fact_delivery.go:99-101` `Updates({delivery_status: Failed, retry_count: retry_count + 1})` → **`:104` `tx.Commit()`** → `:110` 返回错误 |
| **RAG 现状** | `CommentFactDeliveryService.java:121` `markFailed(...)` → `:126` `throw sendErr` |
| **机理** | 同 MISMATCH-01。且**更糟**：失败路径的异常类型不统一 —— `Wire.DataCenterAcceptanceRejectedException`（`Wire.java:415-427`）是 `RuntimeException` ⇒ 回滚 ⇒ `retry_count` **恒 0**；若是受检异常（`IOException`）⇒ Spring **不回滚** ⇒ 反而落盘 |
| **可观察后果** | 同一失败路径**因异常类型不同而 DB 状态不同**；Go 侧则是无条件 Commit。无限重试且重试计数不增长 ⇒ 排障时看不出「已经重试了多少次」 |
| **修法** | 同 MISMATCH-01 |
| **✅ 已修** | **2026-10-24**。同 MISMATCH-01（同一个 `TransactionTemplate` 改动）。🔴 **另修一处本轮自己引入的偏差**：失败路径原本把原始 `sendErr` 换成了通用文案 ⇒ 改回 Go 的 `fact_delivery.go:110` `return false, sendErr`（**原始错误挂 `DeliveryDeferredException` 的 `cause`**，排障时能分清超时 / 拒连 / 回执不匹配） |
| **🧪 守门网** | `CommentRpcGoSemanticsGateTest#failedDeliveryIsPersistedWithRetryCountEvenThoughTheCallThrows`：断言抛错 **且** `delivery_status=2`、`retry_count=1` **落盘**，**且** `hasCause(原始 sendErr)` |
| **🧬 变异实测** | 把 failed 分支改回 `status.setRollbackOnly()` ⇒ 该测试**变红** ⇒ 守门网有效 |

### MISMATCH-03 · `technical_received_at` 写成了**本地投递时间**，不是 DC 回执里的接收时刻

| 项 | 值 |
|---|---|
| **Go 事实** | `fact_delivery.go:94` `received, receiptOK := communityfact.ValidateReceipt(event, receipt)` → `:115` `"technical_received_at": received`（= DC 回执的 `received_at`，`.UTC()` 后落库） |
| **RAG 现状** | `CommentFactDeliveryService.java:112-114` 拿到了含 `Instant received` 的 `validated` 但**丢弃未用**；`:130-132` `markAccepted(..., now, now)` ⇒ `technical_received_at == delivered_at == 本地投递时间` |
| **可观察后果** | DC 的接收时刻被本地时刻顶替。`CommentAuthorityLookupService.java:351-353` 的权威读响应 `technical_receipt.received_at` 取自该列 ⇒ **wire 上可观察的值不同** |
| **修法** | `markAccepted(...)` 的两个时间参数分别传 `validated.get().atOffset(UTC)` 与 `now`（mapper 签名本就区分了两个参数，是**调用方传错**） |
| **✅ 已修** | **2026-10-24**。`markAccepted(..., receivedAt, now)`：`receivedAt` = `validated.get().atOffset(UTC)`（**DC 回执的 `received_at`**），`now` = 本地投递时刻（`delivered_at`） |
| **🧪 守门网** | `CommentRpcGoSemanticsGateTest#technicalReceivedAtComesFromTheDataCenterReceipt`：回执 `received_at` 固定为 `2020-01-02T03:04:05Z` ⇒ 断言 `technical_received_at` **等于**该值、**且不等于** `delivered_at` |
| **🧬 变异实测** | 改回 `markAccepted(..., now, now)` ⇒ 该测试**变红** ⇒ 守门网有效 |

### MISMATCH-04 · 事实载荷的**未知字段严格性**被类级注解取消

| 项 | 值 |
|---|---|
| **Go 事实** | `fact_delivery.go:33` `communityfact.StrictDecode(..., &payload)`；`service/common/communityfact/wire.go:121-131` `decoder.DisallowUnknownFields()` ⇒ 载荷多一个字段 ⇒ 解码失败 ⇒ `validCommentDelivery` false ⇒ 该行 **BLOCKED** |
| **RAG 现状** | `CommentDomainFactService.java:220` `@JsonIgnoreProperties(ignoreUnknown = true)` 挂在 `CommentFact` 上 ⇒ **类级注解覆盖** `Wire.MAPPER` 的 `FAIL_ON_UNKNOWN_PROPERTIES=true`（`Wire.java:115-116`）；`CommentDeliveryValidator.java:173-180` 静默忽略未知字段 |
| **可观察后果** | 载荷被篡改 / 来自更高 schema 版本时，Go **拒发并 BLOCKED**，RAG **照发** ⇒ DC 收到一个 Go 源站会判定为非法的事实 |
| **讽刺点** | `CommentDeliveryValidator.java:30-36` 的 javadoc 恰好写着「用错 mapper 会让『载荷里多一个字段』这种篡改悄悄通过校验」，而 `:38-42` 注入的其实是**共享的宽松 mapper**（不是 javadoc 说的「专用 mapper」）—— 严格性恰恰被这个注解取消 |
| **✅ 已修** | **2026-10-24**。① **移除** `CommentFact` 上的 `@JsonIgnoreProperties(ignoreUnknown = true)`（类级注解会**覆盖** mapper 级严格性）；② `CommentDeliveryValidator` 改用**本域私有**的 `STRICT_MAPPER`（`FAIL_ON_UNKNOWN_PROPERTIES=true`）。⚠️ **刻意不动 `service/common` 的 `Wire.MAPPER`** —— 它被 like / message / article / favorite / follow / task **六域共用**，且 `align/common` 是**另一条并发分支（不同基线）** ⇒ 改它会波及多域并引入合并冲突 |
| **🧪 守门网** | `CommentRpcGoSemanticsGateTest#payloadWithUnknownFieldIsBlocked`：载荷多一个 `tampered_extra_field` ⇒ 断言 **BLOCKED**（`delivery_status=3`）**且 sender 从未被调用** |
| **🧬 变异实测** | 恢复 `@JsonIgnoreProperties(ignoreUnknown = true)` ⇒ 该测试**变红** ⇒ 守门网有效 |

### MISMATCH-05 · 载荷里三个时间字段**无格式校验**

| 项 | 值 |
|---|---|
| **Go 事实** | `domain_fact.go:77-79` `EventTime/OccurredAt/AvailableAt time.Time` ⇒ `encoding/json` 解码必须是 RFC3339，否则 `StrictDecode` 报错 ⇒ BLOCKED |
| **RAG 现状** | `CommentDomainFactService.java:299-306` 用 `String` 承载 ⇒ 任意字符串都能通过；`CommentDeliveryValidator.java:151-160` **只对 `occurred_at` 做 `Instant.parse`**，`event_time` / `available_at` 完全无校验 |
| **可观察后果** | Go 会 BLOCKED 的畸形载荷（如 `"event_time":"not-a-time"`）在 RAG 侧通过校验并被投递；`CommentAuthorityLookupService.java:326-327/396-398` 的零值判定只看 `0001-01-01` 前缀，也拦不住 |
| **✅ 已修** | **2026-10-24**。`CommentDeliveryValidator` 新增 `parseGoRfc3339()`（`OffsetDateTime.parse(..., ISO_OFFSET_DATE_TIME)`，对齐 Go `time.Time.UnmarshalJSON` —— **要求偏移必须存在**，与 Go 的 RFC3339 接受集合一致），对 `event_time` / `occurred_at` / `available_at` 三字段补校验。⚠️ **刻意保持 `CommentFact` 的三个时间字段为 `String`** —— 改成 `Instant` 会因 mapper 未注册 `JavaTimeModule` 而反序列化失败，且会改线上字节 |
| **🧪 守门网** | `CommentRpcGoSemanticsGateTest#payloadWithMalformedEventTimeIsBlocked` + `#payloadWithMalformedAvailableAtIsBlocked` |
| **🧬 变异实测** | 摘掉那两行 RFC3339 校验 ⇒ **两个测试同时变红** ⇒ 守门网有效 |

### MISMATCH-06 · 信封数值字段**未禁 String→int 强转**

| 项 | 值 |
|---|---|
| **Go 事实** | `encoding/json` 对 `{"schema_version":"1"}`（string→int）**报错** ⇒ `StrictDecode` 失败 ⇒ BLOCKED |
| **RAG 现状** | `Wire.java:115-116` 的 `Wire.MAPPER` 只配了 `FAIL_ON_UNKNOWN_PROPERTIES`，**未禁 String→int 强转** ⇒ `"schema_version":"1"` 被读成 `1`，通过 `CommentDeliveryValidator.java:108` 的 `event.schemaVersion != 1` 检查 |
| **可观察后果** | 被篡改的信封（数值字符串化）在 Go 侧 BLOCKED、在 RAG 侧通过 |
| **备注** | `Jackson2ObjectMapperConfiguration.java:82-90` 配的 Textual 强转禁令是给 **Kafka 审计消息**用的共享 mapper；`Wire.MAPPER` 是**另一个独立 mapper**，没吃到这条禁令 |
| **✅ 已修** | **2026-10-24**。`CommentDeliveryValidator` 新增**本域私有** `STRICT_MAPPER`，把 Go `encoding/json` 的类型严格性**整套**搬过来：`Integer←String/Float` Fail、`Float←String` Fail、`Textual←Integer/Float/Boolean` Fail、`Boolean←String/Integer` Fail（Go 对这六种类型不匹配**一律报错**，Jackson 2 默认**静默强转**）。⚠️ **未改 `service/common` 的 `Wire.MAPPER`**（六域共用 + `align/common` 不同基线） |
| **🧪 守门网** | `CommentRpcGoSemanticsGateTest#envelopeWithStringifiedSchemaVersionIsBlocked`（信封 `schema_version:"1"`）+ `#payloadWithNumericStringFieldIsBlocked`（载荷 `target_type:5`） |
| **🧬 变异实测** | 把 `STRICT_MAPPER` 的强转配置**整段删掉**（= 修复前只禁未知字段的 `Wire.MAPPER`）⇒ **两个测试同时变红** ⇒ 守门网有效 |
| **⚠️ 假守卫教训** | 第一版守门网用的是「载荷 `schema_version` 数字化」。它**恒绿** —— 宽松解码把 `1` 读成 `"1"`，而 `"1" ≠ "rtw.community-fact.v1"` ⇒ **照样 BLOCKED，但原因是「版本号不匹配」而不是「类型不合法」**。⇒ **绿测试可能是「因错误原因而绿」的守卫**（§3.3）。换成 `target_type`（数字化后读成 `"5"`，非空 ⇒ 整条事实会被**照常投递**）才真正区分 |

### MISMATCH-07 · `comment_index` / `comment_content` INSERT 时 `updated_at` 取值不同

| 项 | 值 |
|---|---|
| **Go 事实** | `comment.go:80-90` 构造 `CommentIndex` 时**只赋 `CreatedAt: createTime`**，`UpdatedAt` 留零 ⇒ GORM `callbacks/create.go:344-347` 用 `curTime`（**入库时刻**）填 |
| **RAG 现状** | `CommentTransactionalService.java:179` `index.setUpdatedAt(createdAt)` ⇒ **评论创建时刻**（Kafka 消息里的 `create_time`） |
| **可观察后果** | INSERT 瞬间两值不同。该列不进 wire（`get_comment_logic.go:139` 只取 `created_at`），此后每次 UPDATE 两侧都写 `CURRENT_TIMESTAMP` ⇒ **不可观察窗口极窄**，但值确实不同 |
| **严重度** | 列在 wire 上 ⇒ 按 `comment_content`/`comment_index` 的 `updated_at` 是否被任何读取路径使用，本次**未找到读取方** ⇒ 记为 MISMATCH 但影响面窄 |
| **✅ 已修** | **2026-10-24**。两个 mapper 的 INSERT 改 `updated_at = CURRENT_TIMESTAMP`（**入库时刻**），`created_at` 仍是 Kafka 的 `create_time`（**评论创建时刻**）。⚠️ **GORM 侧依据（实测源码）**：`gorm@v1.31.2/callbacks/create.go:340-351` —— 字段值为**零** ⇒ 填 `curTime`；**非零** ⇒ 原样保留。Go 侧 `CommentIndex`/`CommentContent`（`data_model.go:22-49`）只赋 `CreatedAt` ⇒ `UpdatedAt` 留零 ⇒ 被填 `curTime`（入库时刻） |
| **🧪 守门网** | `CommentRpcGoSemanticsGateTest#commentIndexInsertUsesInsertTimeForUpdatedAt` + `#commentContentInsertUsesInsertTimeForUpdatedAt`：把 `created_at` 设成 1 小时前 ⇒ 断言 `updated_at` 比它晚 10 分钟以上、且 ≈ now |
| **🧬 变异实测** | 两处 `CURRENT_TIMESTAMP` 改回 `#{createdAt}` ⇒ **两个测试同时变红** ⇒ 守门网有效 |

---

## 3. OBSERVABLE DIFFERENCE（6 条 —— 行为等价，但实现路径 / 部署形态不同）

| # | 判定点 | Go 侧事实 | RAG 侧现状 | 性质 |
|---|---|---|---|---|
| **D1** | ~~`comment_domain_fact_outbox.created_at` 的取值来源~~ ⇒ 🟢 **撤销（2026-10-24 复核：原结论是误判）** | **Go 侧同样走 DB 默认**（`not null;default:CURRENT_TIMESTAMP` ⇒ GORM **不**把该列放进 INSERT）⇒ 两侧都是「事务开始时刻」⇒ **无差异** | ✅ **无差异**（详见下方 §3.1） |
| **D2** | 时间精度 | `domain_fact.go:94/98` `time.Now().UTC()` = **纳秒** | `CommentDomainFactService.java:120/124` `Instant.now()` = 微秒级 | `event_time`/`available_at` 小数位数不同 ⇒ 载荷字节不同（`validCommentDelivery` 比的是**瞬时**与 **JCS 哈希**，语义不受影响） |
| **D3** | `EventTime` 的 UTC 归一化 | `domain_fact.go:96` `fact.EventTime = fact.EventTime.UTC()` → `:97` `OccurredAt = EventTime` | `CommentDomainFactService.java:118-122`：**修复前** `eventTime` 非 null 时**原样使用** | ✅ **已修**（2026-10-24，详见下方 §3.2） |
| **D4** | authority 端点的部署形态 | `cmd/fact-authority/main.go:52/75-80` = **独立进程 + 独立监听端口**，默认就在跑 | `CommentAuthorityEndpointConfig.java:42` `@ConditionalOnProperty(comment.authority.enabled=true)`；`application.yml:196` 默认 `false`；且 pom 无 `spring-boot-starter-web` ⇒ 即使打开也起不来 | ⚠️ **建议登记为签字偏离**（见 `docs/Intentional_Deviations/comment/rpc.md`） |
| **D5** | fact-dispatch 的部署形态 | `cmd/fact-dispatch/main.go:52-77` = 独立进程，缺配置即退出；默认就在投递 | `CommentFactDispatchScheduler.java:58-63` + `application.yml:178` `dispatch-enabled: false` ⇒ 默认整条投递链路**不参与运行** | ⚠️ **建议登记为签字偏离** |
| **D6** | 缓存读穿失败时的 **Redis 指标 label** | `comment_subject_cache.go:31-35`：`GetSubjectWithCache` 返回 err 时打 `action="GetSubjectWithCache"`；同时 `GetSubjectCache` 内部对 **Redis 真异常**再打一次 `action="GetSubjectCache"` ⇒ **Redis 真异常时打两个指标** | `CommentCacheService.java:182` 把**所有**异常（含 JSON 解析失败）都记成 `action="GetSubjectCache"`；`getSubjectWithCache` 在 `readSubjectCache` 返回 null 时**不再打点** ⇒ **只打一个，且 label 名不同** | ✅ **已修**（2026-10-24，详见下方 §3.3） |
| **D7** | **回填 Redis 失败**时的读路径 | `comment_subject_cache.go:59` `_ = c.SetSubjectCache(...)` ⇒ **显式忽略错误** ⇒ Redis 挂掉时读路径**照常返回 DB 回源的结果** | **修复前** `CommentCacheService` 的 `writeSubjectCache` 抛 `IllegalStateException` ⇒ 整个 `getSubjectWithCache` 失败 ⇒ **对外 500** | ✅ **已修**（2026-10-24，详见下方 §3.4） |

### 3.1 🟢 D1 撤销：Go 的 `default:` 标签会**吃掉** GORM 的 `autoCreateTime`

| 项 | 值 |
|---|---|
| 原结论 | 「Go 的 `CreatedAt` 是 GORM `autoCreateTime` ⇒ INSERT 显式带 `created_at` = Go 进程当时；RAG 走 DDL 默认 = 事务开始时刻 ⇒ 值不同」 |
| 复核结论 | 🟢 **原结论不成立 —— 两侧都是「事务开始时刻」，无差异** |
| 复核日期 | 2026-10-24 |

**证据链（三重，全部可复现）**：

1. **Go 的 tag**（`rpc/internal/model/domain_fact.go:29`）：
   `CreatedAt time.Time \`gorm:"column:created_at;not null;default:CURRENT_TIMESTAMP"\``
   ⚠️ **没有** `autoCreateTime` 标签，但**有** `default:` 标签。

2. **GORM 的消费点**（`gorm.io/gorm@v1.31.2/callbacks/create.go:268-270`）：
   ```go
   if field := stmt.Schema.FieldsByDBName[db]; !field.HasDefaultValue || field.DefaultValueInterface != nil {
       ... 把该列加进 INSERT ...
   }
   ```
   ⇒ `HasDefaultValue = true`（`schema/field.go:210-213` 解析 `DEFAULT` tag 时置位）
   且 `DefaultValueInterface == nil`（`schema/field.go:277-281` 试图 `now.Parse("CURRENT_TIMESTAMP")` **失败**
   ⇒ 不赋值）⇒ **整个条件为 false** ⇒ **`created_at` 根本不进 INSERT 列清单** ⇒ **DB 默认生效**。

3. **活体数据**（双侧对拍那轮的 `go_comment` 库）：
   Go 的 `DeleteCommentTx` 里 `comment_index` 的 `state=2` UPDATE（`comment.go:288`，推进 `updated_at` = GORM 的 `curTime`）
   **先于** `appendCommentFact` 的 outbox INSERT（`comment.go:338`），
   而两者在**同一个事务**里 ⇒ 若 `created_at` 是 GORM 在 INSERT 时刻填的，它必须 **≥** `comment_index.updated_at`。
   实测：`outbox.created_at = 10:39:06.06046` **<** `comment_index.updated_at = 10:39:06.089807`
   ⇒ **`created_at` 来自 DB 默认（事务开始时刻）**，不是 GORM 填的。✅

⇒ **RAG 侧的 mapper（INSERT 不带 `created_at`，交给 DDL 默认）本来就是对的 ⇒ 本轮不改代码。**

> ⚠️ **为什么这个坑值得单独记**：`domain_fact.go:29` 的 `CreatedAt` 字段名会让 GORM 的
> **命名约定**（`schema/field.go:294-295`：`field.Name == "CreatedAt"` ⇒ `AutoCreateTime`）生效，
> 但 `default:` 标签又让 INSERT **跳过**它 ⇒ **看 struct 定义会得出相反的结论**。
> ⇒ 这正是 AGENTS.md §3.3.1 说的「必须读到**消费该 tag 的那段控制流**，只贴 struct 定义行不算证据」。
> ⇒ 已登记为 `docs/Pitfalls/comment/rpc.md` 的 PIT-COMMENT-RPC-10。

### 3.2 ✅ D3 已修：`event_time` 归一化到 UTC

| 项 | 值 |
|---|---|
| **Go 事实** | `domain_fact.go:95-97`：`if fact.EventTime.IsZero() { … = time.Now().UTC() }` → **`:96` `fact.EventTime = fact.EventTime.UTC()`（无条件）** → `:97` `OccurredAt = EventTime` |
| **修复前 RAG** | `CommentDomainFactService.append()` 只做了第一步（`null ⇒ now`），**漏了第二步** ⇒ 调用方传 `+08:00` 时 payload/envelope 保留本地偏移 |
| **可观察后果** | Go 的 `occurred_at` 是 UTC（`"…T10:00:00Z"`），RAG 是 `"…T18:00:00+08:00"` ⇒ **信封字节漂移**（`ORDER BY`/JCS 哈希口径不同） |
| **当前是否可见** | ⚠️ 当前 3 个调用方（`CommentTransactionalService:234/389/542`）都传 UTC ⇒ **当前不可观察**；但 `append(CommentFact)` 是 **public API**，第 4 个调用方传本地偏移就会漂 ⇒ 属**预防性**对齐 |
| **✅ 已修** | **2026-10-24**。新增 `CommentDomainFactService.normalizeEventTimeToUtc(String)`：`null`/空白 ⇒ now；Go 零值时刻（`0001-01-01T00:00:00Z`）⇒ now；**带偏移的 RFC3339 ⇒ 转成 UTC（`Z`）**；已是 UTC ⇒ 不动；**无法解析 ⇒ 原样保留**（Go 的 `time.Time` 不可能有非法值，抛异常等于新增 Go 没有的失败模式 ⇒ 交给投递侧 RFC3339 校验判 BLOCKED，MISMATCH-05 已覆盖该路径） |
| **🧪 守门网** | **新增** `CommentFactEventTimeGateTest`（**5 例，真 PG + 真 MyBatis**）：断言落库的 **payload jsonb** 与 **delivery_envelope jsonb** 里的 `event_time`/`occurred_at` 都是 UTC 形态；另含「已是 UTC 不动」「纳秒精度 + 末尾 0 去掉（Go RFC3339Nano 语义）」「null ⇒ now」「无法解析 ⇒ 原样保留」 |
| **🧬 变异实测** | 把 `append()` 改回「原样透传」⇒ **2/5 变红**，症状精确：`expected: <2026-10-24T10:00:00Z> but was: <2026-10-24T18:00:00+08:00>` ⇒ 守门网不是假绿 |

### 3.3 ✅ D6 已修：Redis 错误的**指标 label 与打点次数**

| 项 | 值 |
|---|---|
| **Go 事实** | `comment_subject_cache.go:16-73` 的 `GetSubjectWithCache` + `:75-101` 的 `GetSubjectCache`：**三种**结局、**两处**打点判定 |
| **关键区分** | ① **Redis 真异常** ⇒ `GetSubjectCache` 自己打 `("comment_redis","GetSubjectCache","query")`（`:80-82`），**且**返回 err ⇒ 调用方再打 `("comment_redis","GetSubjectWithCache","query")`（`:26-29` / `:39-42`）⇒ **两个 label 各 2 次**（外层读 + singleflight 双检）<br>② **JSON 解析失败** ⇒ `GetSubjectCache` **不打点**（`:94-98` 注释明说「视作缓存未命中，不记 metric」），但**返回 err** ⇒ 调用方仍打 `GetSubjectWithCache` ⇒ **该 label 2 次、`GetSubjectCache` 0 次**<br>③ **未命中（`redis.Nil`）** ⇒ 两侧都不打点 |
| **修复前 RAG** | 两个 catch **合并**成一个 ⇒ 两种异常都只打一个 `GetSubjectCache` ⇒ label 集合与 Go 不同（**多** `GetSubjectCache`、**少** `GetSubjectWithCache`） |
| **✅ 已修** | **2026-10-24**。`CommentCacheService` 引入**三态** `SubjectCacheRead{value, error, redisFailure}`，把「Redis 真异常」与「JSON 解析失败」**分开**；打点位置照 Go：Redis 异常打 `GetSubjectCache`、两种异常都让调用方打 `GetSubjectWithCache`、JSON 解析失败**不**打 `GetSubjectCache` |
| **🧪 守门网** | **新增** `CommentCacheRedisMetricGateTest`（**5 例：真 Redis 容器 + 真 PG + 真 MyBatis + 真 Micrometer registry**）：① 命中 ⇒ 0 个指标；② 未命中 ⇒ 0 个；③ JSON 解析失败 ⇒ **只**有 `GetSubjectWithCache`=2、`GetSubjectCache`=0；④ Redis 真异常（连**没人监听的端口**⇒ Lettuce 真被拒）⇒ 两个 label 各 2；⑤ 回填失败 ⇒ 读路径照常返回（见 §3.4） |
| **🧬 变异实测** | 把两个 catch **合并**（= 修复前）⇒ **1/5 变红**，症状精确：`expected: <0.0> but was: <2.0>`（`GetSubjectCache` 多打了 2 次）⇒ 守门网不是假绿 |

### 3.4 ✅ D7 已修：回填 Redis 失败 ⇒ 读路径**照常成功**

| 项 | 值 |
|---|---|
| **Go 事实** | `comment_subject_cache.go:59` `_ = c.SetSubjectCache(ctx, subjectID, &subject, 5*time.Minute)` ⇒ **显式忽略错误**；`get_comment_logic.go:176` 同样 `_ =` |
| **修复前 RAG** | `CommentCacheService.getSubjectWithCache` 直接调 `writeSubjectCache(...)`，后者在 Redis 失败时抛 `IllegalStateException` ⇒ 整个读路径失败 ⇒ **对外 500**（而 Go 侧**照常返回** DB 回源的结果） |
| **可观察后果** | Redis 不可用时，Go 的评论查询**仍然可用**（降级回源 DB），RAG 的**整个接口 500** ⇒ 可用性面差异 |
| **✅ 已修** | **2026-10-24**。`getSubjectWithCache` 的回填包 try/catch，异常**记日志后忽略**（照 Go 的 `_ =`）。⚠️ `CommentDomainService:404-409` 的另一处回填**本来就**是 catch 后忽略 ⇒ 与 Go 一致，无需改 |
| **🧪 守门网** | `CommentCacheRedisMetricGateTest#writeBackFailureDoesNotFailTheRead`（Redis 指向死端口 ⇒ 读与写都失败）⇒ 断言**仍返回 DB 回源的结果**、且 `SetSubjectCache`(set) 打点存在 |
| **🧬 变异实测** | 去掉那个 try/catch（= 修复前）⇒ **2/5 变红**（`IllegalStateException: redis set subject cache failed` 抛到测试）⇒ 守门网不是假绿 |

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
| **9 处时间列的 NOT NULL + DEFAULT** | 🔴 **不一致**（Go 可空无 DEFAULT / RAG `NOT NULL DEFAULT CURRENT_TIMESTAMP`）⇒ **10 处是「RAG 更严」** ⇒ 🟡 **仍待签字**（`SD-2026-10-24-CAND-01`），见 `docs/Intentional_Deviations/comment/rpc.md` |
| `report_record` 表在 Go 侧**根本不存在**（`db.go:15` 的 AutoMigrate 列表不含它，3 个手写脚本也不建） | 🔴 RAG V5 建了 ⇒ Go 侧全新部署上 `ReportComment` **必然 5004** ⇒ RAG 侧是「更全」⇒ ✅ **2026-10-24 已签字**（`SD-2026-10-24-CAND-02`） |

---

## 4.6 🟢 双侧活体对拍（2026-10-24）—— 证据强度的最后一档

> **性质**：Go 真源与 RAG **同时起四个进程**、跑**同一套请求矩阵**、**逐字段差分**。
> 这是 AGENTS.md §3.3「两侧同时跑的探针」的**组件级**形态 —— 前面所有 ✅ 都只是「在该证据强度下一致」，
> **只有本节是「两侧同时在跑时确实一致」**。

### 4.6.1 基建与隔离（隔离不对 ⇒ 差异全是串味）

| 要素 | Go 侧 | RAG 侧 | 为什么必须这么隔离 |
|---|---|---|---|
| rpc 端口 | `19001`（`go build` 产物 + 改过的 yaml） | `59001`（fat jar） | 端口不冲突 |
| api 端口 | `18888` | `58888` | 同上 |
| 数据库 | `go_comment` | `rag_comment`（同一 PG 容器 `127.0.0.1:55440`） | 两侧各自的库 |
| Kafka topic | `comment-audit-topic-go` | `comment-audit-topic-rag` | ⚠️ 共用 topic ⇒ **互相消费对方的事实** ⇒ 全是假差异 |
| Kafka 消费组 | `comment-audit-group-go` | `comment-audit-group-rag` | 同上 |
| Redis 库 | **db0** | **db1**（`--spring.data.redis.database=1`） | ⚠️ 两侧 Redis key 格式**逐字一致**（`comment:subject:%s` 等）⇒ 不分库必然串味 |
| 下游 `article-rpc` / `message-rpc` | `127.0.0.1:19999`（**死端点**） | `127.0.0.1:19999`（**同一个死端点**） | 两侧都超时 ⇒ **超时路径可比**；指向不同端点会造成假差异 |
| Nacos | — | `SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false` | 本机无 Nacos |
| `etcd` | yaml 里**整块删除** | — | Go 侧 zrpc 需要，但本项目只用 `static` 端点 |

⚠️ **Go 侧两处 harness 侧的坑（不是产品缺陷）**：

1. **`Offset: last` 会跳过消息**：kafka-go 的 `LastOffset` 把起始位点定到**消息之后** ⇒ 消费组无成员时消息被跳过。
   ⇒ harness 改用 `Offset: first` + **每轮删 topic**。
2. **Go 侧 `consumers: 8` 但只发 1 条**：`kq` 的 `startConsumers` 用 `Processors`、`startProducers` 用 `Consumers` ⇒ 8 个消费者会**抢同一条**消息（只有一条时无所谓）。

### 4.6.2 对拍抓出来的 **4 个真实缺陷**（单测全绿时测不出来）

| # | 缺陷 | 症状 | 根因 | 修法 | 守门网 |
|---|---|---|---|---|---|
| **P-1** | **`comment-rpc` 进程根本起不来** | `APPLICATION FAILED TO START: Unable to start web server` | 本模块 pom 的 `jakarta.servlet:jakarta.servlet-api`（provided）被 Spring Boot repackage 打进 `BOOT-INF/lib` ⇒ `WebApplicationType.deduceFromClasspath()` 判成 **SERVLET** ⇒ 找不到 `ServletWebServerFactory`。（`message-rpc`/`like-rpc` 无此依赖故不受影响） | `application.yml` 加 `spring.main.web-application-type: none` | **新增** `CommentRpcWebApplicationTypeGateTest`（2 例：断言「危险依赖确实在 classpath 上」+「缓解配置确实生效」） |
| **P-2** | **缺 MyBatis-Plus 自动配置排序桥** ⇒ `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` | 上下文起不来 | `MybatisPlusAutoConfiguration`（3.5.17）的 `@AutoConfigureAfter` 指向 **Boot 3** 的 `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`；Boot 4 挪到 `org.springframework.boot.jdbc.autoconfigure...` ⇒ **排序引用悬空** ⇒ MP 在 DataSource 之前求值 ⇒ `@ConditionalOnSingleCandidate(DataSource)` 失败 | 照抄同仓 `like-rpc` 的 `MybatisPlusOrderingBridgeAutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 同上（Boot 4 下 8 个用 mybatis-plus 的 rpc 模块里**只有 comment-rpc 漏了**） |
| **P-3** | **`comment-api` 启动即 `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`** | api 进程起不来 | ⚠️ **是本仓 `f628fb1` 自己引入的**：把 `grpc-inprocess` 声明成 `<scope>test</scope>`，按 Maven「最近者优先」**顶掉了** devh 传递进来的 **compile 作用域**那份 ⇒ 运行期缺失 | **删掉该声明**（devh 的传递依赖同时满足测试与运行期）+ 在 pom 里写明原因 | 真起 api 进程即验 |
| **P-4** | **`SubjectMapper.decrementCounts` 的 `<if>` 拼在 WHERE 之后** ⇒ `syntax error at or near ","` ⇒ 删根评论时**整个事务回滚** ⇒ 对外报 5002「DB更新失败」 | 删根评论 100% 失败 | 动态 SQL 的条件片段位置写错（`<set>` 自动去尾逗号，`applyInteractionDeltas` 同款写法） | 改用 `<set>` | **新增** `CommentTransactionalSqlGateTest`（**10 例，真 PG + 真 MyBatis**，覆盖全部带 `<if>`/`<set>` 的 mapper 方法）⇒ **10/10 绿**；🧬 **变异实测**：把 `<if>` 拼回 WHERE 之后 ⇒ **1/10 变红** ⇒ 守门网有效 |

> 🔴 **P-4 是本轮唯一的「单测零覆盖」型缺陷**：mapper 的动态 SQL 在**单测里根本没被拼过**（单测 mock 掉了数据层），
> 只有**真 PG + 真 MyBatis** 才会撞上语法错。
> ⇒ 与 AGENTS.md §3.3「纯 Mockito mock 数据层的单测不算数」完全对上。

### 4.6.3 对拍结果：**23 步 / 20 一致 / 8 差异**（**复跑两轮结果一致**）

> ⚠️ **第一轮是 17 步 / 15 一致 / 6 差异**；第二轮把矩阵**扩到 23 步**（新增 6 步专打「此前未触发的判定点」）
> ⇒ 差异从 6 条变成 8 条，但**归因集合没变**，只是**多暴露了一条此前从未被观察到的差异**（T20）。

**8 条差异全部落在 3 个步骤上 ⇒ 3 个根因，其中 2 个在 Go 侧、1 个在保护层覆盖范围。**

| 步骤 | Go 侧 | RAG 侧 | 归因 |
|---|---|---|---|
| **T07 `manage`（`action_type=2` = `MANAGE_UNPIN`）** | **5002**「DB更新失败」 | **200** `{success:true}` | ✅ **已签字偏离**（`SD-2026-10-24-CAND-06`，2026-10-24 用户 Jason）—— 🔴 **Go 侧的 SQL 必报错**：`comment.go:165` `gorm.Expr("attribute & ~?", val)` —— `val` 是 `uint` ⇒ PG 收到**未定型参数** ⇒ `operator is not unique: ~ unknown (SQLSTATE 42725)`。已在 PG 上直接复现（`PREPARE t(unknown) AS SELECT 4::bigint & ~$1` ⇒ 同一错误）。⇒ **RAG 侧实现的是 Go 的*意图***（`attribute & ~#{mask}`，mask 是 **有类型**的 `long` ⇒ PG 解析为 `~ bigint` ⇒ 成功） |
| **T08 `report`** | **5004**「DB插入失败」 | **200** `{success:true}` | ✅ **已签字偏离**（`SD-2026-10-24-CAND-02`，2026-10-24 用户 Jason）—— 🔴 **Go 侧根本没有 `report_record` 表**：`db.go:15` 的 `AutoMigrate` 列表只有 6 个模型，**不含 `ReportRecord`**；3 个手写迁移脚本也不建它 ⇒ Go 侧**全新部署上 `ReportComment` 必然 5004** |
| **T20 `404-oversized-body`（1.5 MB → 未注册路径）** | **transport error**（`Broken pipe` / `Connection reset by peer`，**服务端从不返回状态码**） | **HTTP 413**（空 body） | ✅ **已签字偏离**（`SD-2026-10-24-CAND-08`，2026-10-24 用户 Jason）—— **保护层的覆盖范围**：Go 的 MaxBytes 是 **per-route**（`rest/engine.go:105` 在 `bindRoute` 内拼 chain ⇒ 未注册路径不经它 ⇒ **无界读**），RAG 的是**全局 servlet Filter** ⇒ **RAG 是更严/更安全的一侧** ⇒ **代码零改动**。详见 [`api.md` §3.2](./api.md)（D6） |

> ⚠️ **T07 / T08 现在都是「已签字偏离」，不是待办** ⇒ 后续对拍再看到它们变红，
> **不得当成新回归去「修」回 Go 的旧行为**（AGENTS.md §0 第 1 条）。
>
> ⚠️ **T20 也不是本轮代码改动引入的**（RAG 的全局 Filter 是既有实现，Go 的 per-route 语义也是既有事实）
> ⇒ **归因是「覆盖面被补上了」，不是「行为被改坏了」。**

> ⚠️ **同款缺陷在 Go 的点赞路径上潜伏**：`comment.go:571` 是**同一个** `attribute & ~?` 写法
> （仅当 `userId == ownerId` 且取消赞时触发）⇒ **Go 侧「作者取消自己的赞」也会 5002**。
> 本轮矩阵用的是 `user 1001 ≠ owner 2002` ⇒ 该分支**未被触发** ⇒ 两侧一致。
> ⇒ **这是 Go 侧的潜伏缺陷，不是 RAG 的漂移**（RAG 侧的 `applyInteractionDeltas` 用**有类型**的 `long` mask ⇒ 正常）。

### 4.6.4 对拍另确认的**一致**（此前只有静态证据）

| 判定点 | 结果 |
|---|---|
| 7 个 HTTP 路由 + 7 个 RPC 的**响应体逐字段一致** | ✅ **20/23 步全字段一致**（含 `subject.total_count` / `root_count` / `state` / `comment[]` 的每一项） |
| **创建是异步链路**（请求返回 ≠ 已落库） | ✅ 两侧都是「推 Kafka → 消费端落库」：Go `mqs/audit_consumer.go` / RAG `CommentAuditListener` ⇒ **架构同构** |
| 落库后的**事实投递条数** | ✅ `comment_domain_fact_outbox` 两侧**都是 3 条** |
| `GetComment` **不按 `state` 过滤**（已删评论仍在列表里） | ✅ 两侧一致（Go `OnlyNormal:false`；RAG `<if test="onlyNormal">` 恒 false）⇒ 删除后**两侧的 list 都仍返回该评论**（对拍里 `等 delete 落库` 两侧**同时**超时，属**一致**而非差异） |
| `CreateCommentResp.subject_count` **恒 0** | ✅ 两侧一致 |
| 越权 / 空内容 / 缺字段的**业务码**（1008 / 2001 / 1006 / 1013） | ✅ 逐条一致 |
| 缺失 Authorization / 坏 JWT ⇒ **401** | ✅ 两侧一致 |
| **未注册路径 ⇒ 404**（不被鉴权遮蔽成 401） | ✅ **T18** 一致 |
| **已注册路径 + 错方法 ⇒ 405** | ✅ **T19** 一致 |
| **Bearer 前缀长度边界**（`"Bearer "` 恰好 7 字符 ⇒ 两侧都 401） | ✅ **T22** 一致 ⇒ 证实「D2 当前不可观察」 |
| **`iat` 在未来 ⇒ 401** | ✅ **T23** 一致 ⇒ **D4 的修复被活体确认**（不只是单测绿） |
| **非 JSON Content-Type ⇒ 两侧都 200 + `{code:5001}`** | ✅ **T21** 一致 ⇒ **D1 撤销**（详见 [`api.md` §3.3](./api.md)） |

### 4.6.5 ⚠️ 对拍 harness 的两个坑（**不是**产品缺陷，但会制造假差异）

| 坑 | 现象 | 判据 / 修法 |
|---|---|---|
| **不等异步落库就往下跑** | 第一轮跑出 **24 条差异**，其中 20 条是**同一根因**：RAG 侧 `create` 返回 200 但**库里当时还没有这一行** ⇒ 后续 `list`/`like`/`delete` 全部「评论不存在」 | ⚠️ **固定 `sleep(3)` 不可靠**（实测 RAG 侧迟 19s）⇒ harness 改成**轮询到「可见/已消失」为止**（上限 40s / 15s） |
| **Kafka topic 不预建 ⇒ 消费者组 rebalance 退避** | RAG 消费者 **订阅于 18:23:00、19 秒后（18:23:34）才 join 成功拿到分区** ⇒ create→可见迟到 19s。根因：topic 在启动前被删掉 ⇒ 消费者订阅了一个**不存在的 topic** ⇒ Kafka 4.x 的 rebalance **指数退避**（`group.initial.rebalance.delay.ms=3000` 起，上限 `group.rebalance.backoff.max.ms=30000`） | harness 改成**「先删后建」**（`--create --partitions 1`）⇒ 消费者订阅时 topic 已存在 ⇒ 立刻 join。⚠️ **Go 侧不受影响**（kafka-go Reader 不走组协调 ⇒ 同样是毫秒级）⇒ 这是 **Kafka 客户端实现差异**，不是 comment 域语义差异，但**在产品上会表现为「RAG 的审计管道延迟远高于 Go」** |
| **基础设施挂掉 ⇒ 报告里全是「差异」** | Kafka 容器被 **OOM kill（`Exited (137)`）** ⇒ 两侧审计管道全断 ⇒ 产出「create 返回 200 但库里没有」+「RAG 侧 2s deadline exceeded」——**两个都不是 comment 域的语义问题**，但会被当成差异列出来 | harness 加**前置健康检查**：容器状态 + PG/Redis 可达 + `comment-audit-topic*` 数量 ≥ 2 ⇒ 任一不过 ⇒ **`exit 4` 中止**，不跑矩阵（否则产出的全是假差异） |

> ⚠️ **这两条都写进了 `docs/Pitfalls/comment/rpc.md`**（PIT-COMMENT-RPC-07 / -08）。

### 4.6.6 跨域发现（**不在本域 scope，未改**）

| 发现 | 两侧事实 | 处置 |
|---|---|---|
| **RPC 超时的错误文案不同** | Go `service/common/observability/rpc.go:69` ⇒ `context deadline exceeded`；RAG `service/common/observability/RpcInterceptor.java:187` ⇒ `rpc timeout after 2000ms`。**两侧 code 都是 4（DeadlineExceeded）** | 🔧 **已由 global 域修复（第三十轮，global 持 `service/common` 单写权）** ⇒ 落点 `RpcInterceptor.java` + 守门 `RpcInterceptorTimeoutDescriptionGateTest`（2 例，真 Netty server + 真 channel，断言**网络上真正传过来的字节**）+ 探针 `tools/semantic-probe/rpc-timeout/`（Go 半边直接 `replace` 引用冻结真源）⇒ ⚠️ **2026-10-26 复核：修复尚未 commit、未合回 `do-my-own-gh`** ⇒ 销账细节与 **C-1 边界**见 §5 的 **U10** |
| `comment-api` 缺 gRPC 熔断 | —— | ✅ **已作废**（`docs/DRIFT/_cross/new-domains-2026-10-23.md:43-45`）：实测 RAG 侧熔断**已存在** |
| **（只读指针 · 归属 global）超时未继承客户端 deadline** —— global 台账 **E38**（`docs/DRIFT/global/_README.md:333`）<br>Go `rpc.go:38` `context.WithTimeout(ctx, timeout)` ⇒ `ctx` **携带客户端 deadline** ⇒ 有效期限 = `min(客户端 deadline, now+2s)`；RAG `RpcInterceptor.java:214-215` `deadlineEpochMs = now + timeout` ⇒ **只算服务端自己的 2s** | ⛔ **本域不重复登记**（落点在 `service/common` ⇒ **global 单写权**）。<br>🟡 **但对本域有一条延迟可观察面**：`comment-api` 的 per-call deadline = `min(2s, HTTP 剩余预算)`（MISMATCH-API-03 的修法）⇒ **当 HTTP 剩余预算 < 2s 时**，客户端先放弃而 **RAG 服务端的 PG 查询仍跑满到 2s**（Go 侧会随客户端一起取消）⇒ 可能出现「客户端已收不到结果、但 RAG 侧的事务/写入已完成」的**副作用差异**。<br>⇒ 跟踪请走 **global 的 E38**，本条只做指针 |

---


| # | 事项 | 类型 |
|---|---|---|
| **U1** | ~~MISMATCH-01/02 需真 PG 守门测试~~ ⇒ ✅ **2026-10-24 已完成**：`CommentRpcGoSemanticsGateTest`（11 例，真 PG + 真 MyBatis + 真事务），**并做了 10 条变异验证** | ✅ 已闭环 |
| **U2** | ~~MISMATCH-03/04/05/06 一次改动可解 4 条~~ ⇒ ✅ **2026-10-24 已完成**（改动落在 `CommentFactDeliveryService` / `CommentDomainFactService` / `CommentDeliveryValidator` 三处 + 两个 mapper） | ✅ 已闭环 |
| **U3** | ~~本组件无任何投递路径测试~~ ⇒ ✅ **2026-10-24 已补**：`dispatchOnce` 的三条出口（blocked / failed / accepted）均有真 PG 守门网 | ✅ 已闭环 |
| **U4** | Go 侧三个 `_test.go`（`domain_fact_test.go` / `fact_process_fixture_test.go` / `fact_delivery_logging_test.go`）**默认 `t.Skip`** ⇒ Go 侧投递链路的**期望行为在 Go 仓里本身也没有活体证据** | ⚠️ 真源侧缺口 |
| **U5** | ~~`comment_fact_stream` 的 CHECK 在 Go 侧是否常态存在取决于迁移执行顺序~~ | 🟢 **已查实**（2026-10-24）：Go 那三份手写迁移 `001/002/003` **只被测试脚本** `internal/model/test-community-postgres.sh:47,49` 执行 ⇒ **生产路径只有 `db.go:15` 的 `AutoMigrate`** ⇒ **生产的 `comment_fact_stream` 没有 version CHECK** ⇒ **RAG 侧 V7 多一个 CHECK** ⇒ 登记为 **`SD-2026-10-24-CAND-09`**（待签字）。⚠️ **不可观察**（`version` 从 1 递增，要到 2^53-1 才触发） |
| **U6** | ~~`comment_like` 的 Go 侧有两份互相冲突的 DDL 产物~~ | 🟢 **已查实**（2026-10-24）：**生产权威 = `AutoMigrate`**（手写 `001` 只在测试脚本跑）⇒ 两份产物的差异只剩**形态等价**项：`id`（`bigserial` ↔ `IDENTITY`）、唯一性（**唯一索引** ↔ **表约束**）⇒ **无行为差异**。🟢 **活体佐证**：两个活库的 `pg_constraint` / `pg_indexes` 差分里，`idx_user_comment_like` 确实是一边索引、一边约束 ⇒ **语义等价** |
| **U7** | ~~MISMATCH-07（`updated_at` INSERT 取值）—— 未找到任何读取方~~ ⇒ ✅ **2026-10-24 已修**：按 GORM `create.go:340-351` 的实测语义改成入库时刻；**仍无读取方** ⇒ 影响面窄但值已对齐 | ✅ 已修（影响面窄） |
| **U8** | ✅ **`SD-2026-10-24-CAND-06` 已签字**（2026-10-24，用户 Jason）：Go 的 `attribute & ~?` 在 PG 上**必然报错**（`~ unknown`）⇒ `MANAGE_UNPIN` / `MANAGE_UNFEATURE` **100% 失败**；RAG 侧实现 Go 的*意图*（有类型的 mask）。**签字含义覆盖两处**（manage 清位 + `comment.go:571` 点赞清位）⇒ RAG 侧两处**均已正确、无需改代码** | ✅ 已闭环 |
| **U9** | ✅ **`SD-2026-10-24-CAND-02` 已签字**（2026-10-24，用户 Jason）：`report_record` 表 Go 侧**不存在** ⇒ Go 的 `ReportComment` **必然 5004**；RAG 侧建了表 ⇒ 可用。**由活体对拍实证**（Go=5004 / RAG=200） | ✅ 已闭环 |
| **U10** | 🔧 **修复已由 global 域实施，销账待其合回** —— `service/common` 的 RPC 超时 **description** 两侧不同。<br>**修前状态（保留，不抹掉）**：Go `service/common/observability/rpc.go:68-71` `status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ description = **`context deadline exceeded`**；RAG 修前 `RpcInterceptor.java:196/204` = **`rpc timeout after 2000ms`** ⇒ **两侧 code 相同（4 / `DEADLINE_EXCEEDED`），description 不同**。<br>**三域独立记录**（互相印证，非孤证）：本条 U10 · `docs/DRIFT/users/user/rpc.md` **V-007**（§7 详述 + CASE-31，标注「跨组件 `service/common`」）· `docs/DRIFT/users/admin/rpc.md` §1.3 档位 B 活体对照行（Go `context deadline exceeded` vs RAG `rpc timeout after 2000ms`）⇒ 8 个 rpc 模块的 `rpc-timeout: 2s` ⇒ **该路径线上真实生效**，不是纸面差异。<br>**✅ 修法**（global 持 `service/common` 单写权，第三十轮）：落点 `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` ⇒ 新增常量 `GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded"`，并把**客户端真正收到的** status description 与**遥测 `sea.biz_msg`** 一并换成 Go 的逐字串（`goStyleStatusMessage(...)`）。<br>**🧪 守门**：`service/common/src/test/java/com/rag/common/observability/RpcInterceptorTimeoutDescriptionGateTest`（2 例：真 Netty server + 真 channel + 真客户端 + 真 OTel SDK/InMemorySpanExporter ⇒ 断言的是**网络上真正传过来的那串字节**）。<br>**🔬 探针**：`tools/semantic-probe/rpc-timeout/`（Go 半边**直接 `replace` 引用冻结仓真源**（不改写冻结仓一字节）+ 真 gRPC server；RAG 半边跑**生产类** ⇒ `timeout_wire` 两侧逐字节一致）。<br>**🧬 变异**：M1 改回旧串 ⇒ 守门 2/2 红 + 探针 `EXIT=1`；M2 只改遥测段 ⇒ 只红遥测那条、wire 那条仍绿。<br>⚠️ **状态（2026-10-26 二次复核）**：该修复**已 `commit` ⇒ `align/global` @ `4974677`（主改动 `49808ad`），且 `origin` + `upstream` **两个远端都已推到该提交** ⇒ **不会再丢**（AGENTS.md §5.3「共享文件的改动要尽早提交」）；**但仍未合回 `do-my-own-gh`** ⇒ 主干此刻**还没有**这批修复 ⇒ 本条**保持「销账待其合回」**，待合回落地后再改成「已销账」（本域**不代销**）。<br>⚠️ **C-1 边界（不得越界）**：修完后剩余的 **code 名**表示差异（Go `codes.Code.String()` = CamelCase `DeadlineExceeded` vs grpc-java `Code.name()` = `DEADLINE_EXCEEDED`）已登记为 `docs/Intentional_Deviations/common/_signed-deviations.md` **C 类 C-1**（**不做臆造映射**）⇒ **本轮不动**；守门**刻意把 C-1 的形态钉住**（断言 `code = DEADLINE_EXCEEDED`）⇒ **请勿**为了让 code 名也一致而去改 grpc-java 的 code 命名 | 🔧 销账待 global 合回 |
| **U11** | ⚠️ **Go 侧潜伏缺陷**：`comment.go:571`（点赞的 `attribute & ~?`）与 `comment.go:165` 同款 ⇒ **Go 侧「作者取消自己的赞」也会 5002**。本轮矩阵 `user 1001 ≠ owner 2002` ⇒ 未触发 ⇒ 两侧一致。⇒ **Go 侧缺陷**，RAG 侧已正确 | ⚠️ 真源侧缺陷（记录，不修 RAG） |
| **U12** | ✅ **跨栈 gRPC 路径前缀**（未知项 #5）：Go `package pb;` ⇒ `/pb.CommentService/*`，RAG `package comment;` ⇒ `/comment.CommentService/*`。**新增 `PbCommentServiceAlias`**（`@GrpcService` + `BindableService`，用 `MethodDescriptor.toBuilder().setFullMethodName()` 改名 + 复用 `getServerCallHandler()`）⇒ **只增不改**（`comment.` 路径原样保留）⇒ **不推翻**已签字的 `SD-2026-10-23-04`。守门网 `PbCommentServiceAliasGateTest` 6 例（真 in-process server，两条路径都通、同一实现、方法集合对应、未注册方法仍 UNIMPLEMENTED） | ✅ 已闭环（**半修**，见 U13） |
| **U13** | 🔴 **U12 的伴生阻塞（不可修）**：`io.grpc.Status` **只有 17 个常量** ⇒ grpc-java **发不出** `grpc-status: 1012` ⇒ RAG 的业务码在**自定义 trailer** 里 ⇒ **Go 侧读不到** ⇒ 跨栈错误码必然退化成 `UNKNOWN(2)`。⇒ **结论：混跑必须同栈配对，别名只是兜底** | 🔴 跨栈限制（**协议层**，非本域可修） |
| **U14** | ~~T20 保护层覆盖范围差异~~ | ✅ **已闭环**（2026-10-24 活体实测 T20 ⇒ 登记为 D6 ⇒ **用户已签字** `SD-2026-10-24-CAND-08`：**接受 RAG 更严/更安全的一侧** ⇒ **代码零改动** ⇒ 共享面 `service/common/protection` **不动**） |

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
