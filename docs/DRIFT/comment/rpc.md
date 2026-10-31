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
>   —— ⚠️ **2026-10-29 真实性复核（W-D lane）**：该类**现有 14 个 `@Test`**
>   （`service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcGoSemanticsGateTest.java:245,260,283,305,327,349,369,391,409,425,444,459,488,514`）
>   ⇒ 上行「11 例」是 **2026-10-24 修复轮当时的计数**（**陈旧快照**）⇒ **证据强度判定不变**，**仅计数需按 14 例读**。
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

> 📌 **编号口径（2026-10-29 · 台账对齐 phase · W-D lane）** —— 本文件的 `D1~D10` / `U1~U16` 是**按组件**编号
> ⇒ ⇒ **`comment/api.md` 的 `D1` / `D2` / `D6` 与本文件的 `D1` / `D2` / `D6` 是完全不同的条目**
> （本文件 D1 = **`comment_domain_fact_outbox.created_at` 的取值来源**；api.md D1 = **非 JSON Content-Type ⇒ 415**；
> 本文件 D2 = **时间精度**；api.md D2 = **Bearer 前缀剥离的长度边界**；
> 本文件 D6 = **Redis 错误的指标 label 与打点次数**；api.md D6 = **保护层的覆盖范围**）
> ⇒ ⇒ **跨文件引用必须带组件前缀**：本文件 = `D1@comment-rpc` · `U1@comment-rpc`；api 侧 = `D1@comment-api` · `U1@comment-api`。
> ⇒ 依据（可复算）：`grep -rn 'D6' docs/DRIFT/comment/` ⇒ **同一个标签在两个文件指向不同条目**；
> 且本文件 §4.6.2 实测矩阵里有 **3 处**引用的是**api.md** 的 D 编号（T21 → `D1` · T22 → `D2` · T20 → `D6`）
> ⇒ ⇒ **本轮已在那 3 处加组件前缀**（见 §4.6.2）⇒ **原文保留，只加前缀**。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | 🔴 **`SEMANTICALLY NOT EQUIVALENT`** —— ⚠️ **首轮对照时的判定；7 条 SEMANTIC MISMATCH（§2）已全部修复**（见「修复轮」）⇒ **§2 不再有「必须对齐」的分歧项**；🔴 **但 §3 新增 2 条真漂移 D8 / D9**（2026-10-26，= global E65 / E66）⇒ **「必须对齐的分歧项」重新出现 2 条**，二者**完整闭环跨共享层**（`service/common` ⇒ global 单写权）⇒ **待修法拍板**（§3.5 / §3.6）<br>⚠️ **2026-10-31 订正**：**D9（= global E66）的「写面」已销账**（共享层 Redis 期限载体已落地并自动装配进本域 ⇒ 双侧活体对拍逐键相等 ＋ 本域装配面守门 3 例 ⇒ 见 §3.6 —— ⚠️ **整条 E66 仍 🔴 未闭合** · 残余三条见 global 面 §4.19.19）⇒ ⇒ **D8（= global E65）已于 2026-10-31 销账**（本体面已由 global 销账 ⇒ 四键逐键相等 ⇒ 见 §3.5）⇒ ⇒ **「必须对齐的分歧项」现只余 1 条（D9 / E66）** ⇒ **总体判定不变**（仍 NOT EQUIVALENT —— 残余 = D9 / E66） |
| SEMANTIC MISMATCH | **7 条**（§2）—— ✅ **全部已修**（各配真 PG 守门网 + 变异验证） |
| OBSERVABLE DIFFERENCE | **9 条**（§3）—— **D1 撤销** / **D3 已修** / **D6 已修** / **D7 已修** / D2（时间精度）· D4（authority 部署形态）· D5（dispatch 部署形态）⇒ 后三条已归入签字候选 CAND-03 / CAND-09；🔴 **D8 / D9 为 2026-10-26 新增**（= global **E65 / E66**：出向 gRPC 不继承 deadline · Redis 不感知 deadline）⇒ **均已独立核实成立**（§3.5 / §3.6 / U15 / U16）⚠️ **2026-10-31 订正**：**D9（= global E66）只销了「写面」那半**（整条 E66 仍 🔴 未闭合 ⇒ 见 §3.6）；**D8（= global E65）已于 2026-10-31 销账** ⇒ ⇒ §3 的**开放**面现只余 D9；此处「9 条」是**列出**的条数 ⇒ **不随销账变动** |
| 等价确认 | **约 70 个判定点**（§4） |
| 组件规模 | Go 3,620 行 / 33 文件 ⇒ RAG 7,455 行 / 40 类（**RAG 是 Go 的 2.06 倍**） |
| 构建 | `mvn -o -q -pl service/comment/comment-rpc -am -DskipTests install` ⇒ **`EXIT=0`**（本轮实测） |
| 测试 | `mvn -o -pl service/comment/comment-rpc,service/comment/comment-api,service/comment/comment-common -am test` ⇒ **BUILD SUCCESS**；**rpc 87 / api 45 / common 27，合计 159 / 0 failures / 0 errors / 0 skipped**（**2026-10-26 复测**；⚠️ 原记「rpc 85 / 合计 157」是 **2026-10-24** 的数，已过期 —— 差的 2 例是 `CommentProtectionChainWiringTest`，由**别域**提交 `21e4124`（message 域「挖出 6 个 api 的同款产物不可执行」那轮）顺手加进 `comment-rpc` 的**跨域保护层接线守网**，**不是本域新增** ⇒ 本域语义判定不变） |
| 本台账范围 | **仅 `comment-rpc`**。`comment-api` / `comment-common` 各自独立台账 |
| **修复轮** | **2026-10-24**：§2 的 **7 条 MISMATCH 全部已修**。守门网 = `CommentRpcGoSemanticsGateTest`（**11 例，真 PG**）；**10 条变异全部变红**。⚠️ 计数以「测试」行的 **87 例**为准（EventTime 5 / RedisMetric 5 / Alias 6 + 别域加的 ProtectionChain 2；**2026-10-26 复测校准**）<br>⚠️ **2026-10-29 真实性复核（W-D lane）**：`CommentRpcGoSemanticsGateTest` **现有 14 个 `@Test`**（原记「11 例」= 2026-10-24 修复轮当时的**陈旧计数**）⇒ **「7 条 MISMATCH 全部已修」这一判定不变**（每条 MISMATCH 的守门网方法仍在，见各自条目），**仅守门网用例数按 14 例读**。 |
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

## 3. OBSERVABLE DIFFERENCE（**10 条** —— 行为等价，但实现路径 / 部署形态不同）

| # | 判定点 | Go 侧事实 | RAG 侧现状 | 性质 |
|---|---|---|---|---|
| **D1** | ~~`comment_domain_fact_outbox.created_at` 的取值来源~~ ⇒ 🟢 **撤销（2026-10-24 复核：原结论是误判）** | **Go 侧同样走 DB 默认**（`not null;default:CURRENT_TIMESTAMP` ⇒ GORM **不**把该列放进 INSERT）⇒ 两侧都是「事务开始时刻」⇒ **无差异** | ✅ **无差异**（详见下方 §3.1） |
| **D2** | 时间精度 | `domain_fact.go:94/98` `time.Now().UTC()` = **纳秒** | `CommentDomainFactService.java:120/124` `Instant.now()` = 微秒级 | `event_time`/`available_at` 小数位数不同 ⇒ 载荷字节不同（`validCommentDelivery` 比的是**瞬时**与 **JCS 哈希**，语义不受影响） |
| **D3** | `EventTime` 的 UTC 归一化 | `domain_fact.go:96` `fact.EventTime = fact.EventTime.UTC()` → `:97` `OccurredAt = EventTime` | `CommentDomainFactService.java:118-122`：**修复前** `eventTime` 非 null 时**原样使用** | ✅ **已修**（2026-10-24，详见下方 §3.2） |
| **D4** | authority 端点的部署形态 | `cmd/fact-authority/main.go:52/75-80` = **独立进程 + 独立监听端口**，默认就在跑 | `CommentAuthorityEndpointConfig.java:42` `@ConditionalOnProperty(comment.authority.enabled=true)`；`application.yml:196` 默认 `false`；且 pom 无 `spring-boot-starter-web` ⇒ 即使打开也起不来 | ⚠️ **建议登记为签字偏离**（见 `docs/Intentional_Deviations/comment/rpc.md`） |
| **D5** | fact-dispatch 的部署形态 | `cmd/fact-dispatch/main.go:52-77` = 独立进程，缺配置即退出；默认就在投递 | `CommentFactDispatchScheduler.java:58-63` + `application.yml:178` `dispatch-enabled: false` ⇒ 默认整条投递链路**不参与运行** | ⚠️ **建议登记为签字偏离** |
| **D6** | 缓存读穿失败时的 **Redis 指标 label** | `comment_subject_cache.go:31-35`：`GetSubjectWithCache` 返回 err 时打 `action="GetSubjectWithCache"`；同时 `GetSubjectCache` 内部对 **Redis 真异常**再打一次 `action="GetSubjectCache"` ⇒ **Redis 真异常时打两个指标** | `CommentCacheService.java:182` 把**所有**异常（含 JSON 解析失败）都记成 `action="GetSubjectCache"`；`getSubjectWithCache` 在 `readSubjectCache` 返回 null 时**不再打点** ⇒ **只打一个，且 label 名不同** | ✅ **已修**（2026-10-24，详见下方 §3.3） |
| **D7** | **回填 Redis 失败**时的读路径 | `comment_subject_cache.go:59` `_ = c.SetSubjectCache(...)` ⇒ **显式忽略错误** ⇒ Redis 挂掉时读路径**照常返回 DB 回源的结果** | **修复前** `CommentCacheService` 的 `writeSubjectCache` 抛 `IllegalStateException` ⇒ 整个 `getSubjectWithCache` 失败 ⇒ **对外 500** |
| **D8** | 🔴 **出向 gRPC 调用不继承请求 deadline**（= global **E65**，2026-10-26 新增） | `subject_fallback.go:17` `articleRPC.GetArticle(ctx, …)` ⇒ **吃 `ctx`** ⇒ deadline 到点**立刻中止** | `ArticleRpcClient.java:54/65` = **裸 stub** ⇒ **零期限** ⇒ deadline 到点**照跑不误** | ✅ **已闭合**（2026-10-31 · 本体面已由 global 销账 ⇒ 四键逐键相等 ⇒ 详见下方 **§3.5**；⚠️ **X-6 判据整体仍 🔴** —— 残余 = 客户端硬上界 2000ms 未启用 · **P2-T04** · 域侧 yml · **另一条轴** ⇒ ⇒ **不得**把本条的 ✅ 读成 X-6 已闭合） |
| **D9** | 🔴→✅ **Redis 调用不感知 deadline**（= global **E66**，2026-10-26 新增；**2026-10-31 已销账**） | `internal/cache/*.go` **全部**带 `ctx`（`rdb.Get/Set/Scan`）⇒ deadline 到点缓存操作**立即中止** | **修前**：`CommentCacheService.java` 全部 `redis.opsForValue()…`（**含写** `:279`/`:331`/`:433`/`:528` 的 `set`、`:619` 的 `delete`）**零 deadline 检查** ⇒ **照写不误**<br>**修后**：共享层的 Redis 期限载体经 `BeanPostProcessor` 自动装配进本域 ⇒ **预算耗尽 ⇒ 命令不下发 ⇒ 写不再发生** ⇒ ⇒ 两侧同形 | ✅ **已销账（2026-10-31 · A 活体）**（详见下方 **§3.6**） |

| **D10** | 🔴 **fact 投递链路的 span 属性 = RAG 侧独有的「无效代码」**（2026-10-27 新增 · G4 通报） | `service/comment/rpc/internal/model/fact_delivery.go` 里 `span`/`Span`/`trace`/`Trace` **零命中** ⇒ Go 侧**根本不用 span** | 原实现 `CommentFactDeliveryService.java:148` `Span.current()` ＋ `:193-194` 写 `comment.fact.event_id` / `comment.fact.aggregate_version` ⇒ ⚠️ **唯一调用方是 `bootstrap/CommentFactDispatchScheduler.java:50`（调度线程）** ⇒ `Span.current()` 返回 **invalid span** ⇒ `if (span != null)` **恒真**但 `setAttribute` **静默丢弃** | 🟡 **行为层两侧一致**（Go 也没有这些属性 ⇒ 可观测行为无差）⇒ ⇒ **不是语义漂移，是 RAG 侧独有的无效代码** ⇒ **2026-10-27 已删**（连 `import io.opentelemetry.api.trace.Span`）＋ 原地留注记防复活；⚠️ **本笔提交不含代码**（守门未跑 ⇒ 未验证 ⇒ 不进 PR）⇒ **待窗口跑绿后单独提交** |

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

### 3.5 ✅ D8（= global **E65**）：出向 gRPC 调用**不继承**请求 deadline ⇒ `ArticleRpcClient` 是**裸 stub** —— **本条已闭合**（**2026-10-31 · 本体面已由 global 销账**：`docs/DRIFT/global/vs-common.md:30` 记「E65 本条 = ✅ 已闭合（2026-10-31 第四次订正 · 四键逐键相等）」⇒ **本层独立复跑 `tools/difftest/check-deadline-downstream.py` ⇒ EXIT=0**（Go = RAG = `{'failed_fast':'true','code':'4','outgoing_carrier':'present_expired','sql_carrier':'present_expired'}`）⇒ ⇒ **现行态 = ✅ 已闭合**）｜ **阶段归属 = D 类** · **优先级 P3** · **不阻断阶段一**（本体已销账 ⇒ 无待实现项；⚠️ **X-6 判据整体仍 🔴** —— 残余 = 客户端硬上界 2000ms 未启用 · **P2-T04** · 域侧 yml · **另一条轴** ⇒ ⇒ **X-6 整体的 A 不得挂到本条**）｜ 📌 **2026-10-31 第十一轮 · 本域那半已闭合**（历史记录保留）：本域两个出向出口改由共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 2s 客户端封顶**并继承入向剩余** · 域内守门 `CommentOutboundDeadlineCarrierGateTest` **7/7 绿** ＋ 域侧 `rag.protection.outbound-deadline.cap-millis=2000`

> **归属**：global 域第四十六轮（`align/global` @ **`96d94db`**）在 `align/global` 台账登记为 **E65**（**E38② 重估后窄化出的第③层**）⇒ 命中 **comment 域代码** ⇒ **本域独立复核后落账**（§3.3「双侧同时跑的探针」纪律：Go 侧逐行取证 + RAG 侧读消费点）。
> ⚠️ **落点横跨两层** ⇒ **共享层那半归 global**（`service/common` 单写权）· **本域那半在本域** ⇒ **本域只登记 + 指针，不改共享层**。

| 项 | 值 |
|---|---|
| **Go 事实** | `service/comment/rpc/internal/logic/subject_fallback.go:17-20` `resp, err := articleRPC.GetArticle(ctx, &articleservice.GetArticleRequest{ArticleId: targetID, IncrView: false})` ⇒ **吃 `ctx`** ⇒ 继承请求 deadline ⇒ **deadline 到点 ⇒ 出向调用立刻中止** |
| **RAG 现状** | `service/comment/comment-rpc/.../client/ArticleRpcClient.java:54` 持有 `ArticleServiceBlockingStub`，`:65` `stub.getArticle(...)` 是**裸 stub** ⇒ **零期限** |
| **调用点** | `CommentDomainService.java:259` `return articleRpcClient.fetchAuthorId(targetId);` ⇒ **在 handler 路径上**（`resolveSubjectFallback`） |
| **可观察后果** | deadline 到点后，Go 的 `GetArticle` **立刻失败**（⇒ 回落逻辑折叠成 `ErrorSubjectNotFound`），RAG 的**照跑不误** ⇒ 出向调用可能越过本应中止的时限继续占用下游 |
| **根因（共享层）** | RAG 曾有**两个互不相通**的期限载体：① `DeadlineState`（`RpcInterceptor.java:517` 绑定 ⇒ **SQL 路径**在用，`service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java:165` 消费）· ② `GoStyleRequestDeadline`（`HttpMiddleware.java:324` `begin()` / `:356` `clear()` ⇒ **HTTP 路径**）。<br>📌 **2026-10-30 订正（共享层 `53bc05ec`）**：**两个载体已连通** —— `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:494`（`onHalfClose`）现在**同时**绑 `DeadlineState`（`:517`）与 `GoStyleRequestDeadline`（`:519`）⇒ **同一个绝对时刻**（`:311-312` 算一次 ⇒ 两载体不会各算各的 now）⇒ ⇒ **「gRPC 服务端路径上从未绑定 / `applyTo` 恒 no-op」这句已过期**。<br>⇒ 📌 **2026-10-30 的快照（2026-10-31 第十一轮已闭合：该域内副本已删 ⇒ 上界改由共享层按**纳秒**算 ⇒ 「残留 = 精度差 ≤1ms」随之消解）** —— 当时的说法是「但本域出口的精度未对齐」：`client/ArticleRpcClient.java:71` 的出向出口走 `GoStyleClientCallTimeout.withDeadline(stub)`（实现 `:90-95`）⇒ **它读 `GoStyleRequestDeadline.remainingMillis()`** ⇒ ⇒ **载体在位时它已经继承入向剩余预算**（= `min(2s, 剩余)`）⇒ ⇒ **「继承」这一半已闭合**（2026-10-30 源码级复核）⇒ ⇒ **残留 = 精度差 ≤1ms**（`remainingMillis()` 截断到毫秒 ⇒ `GoStyleRequestDeadline.java:90-96` ⇒ 预算不晚于 Go 的纳秒预算 ⇒ 最多早 1ms 到期）⇒ ⇒ ⚠️ 探针的 `failed_fast`/`code` 键**不能当这条的证据**（探针脚本自述其 RAG 半边手搓 Netty channel ⇒ 不走 devh 全局拦截器 ⇒ 测不到域内 client） |
| **🟢 独立核实结论** | ✅ **成立**。主会话 2026-10-26 逐条复核 Go 侧（`subject_fallback.go:17` 吃 ctx）+ RAG 侧（`:65` 裸 stub）+ 载体分离事实（`begin()` 唯一调用点 = `HttpMiddleware.java:263`），**未照抄 global 的结论** |
| **⚠️ 对 global 台账的一处订正** | global 转达写「`begin()` 全仓只有 `HttpMiddleware.java:304` 一个调用点」⇒ **行号有出入**：实际是 **`:263`**（`:283` 是配套的 `clear()`）⇒ **结论不变**（仍只有 HTTP 一处），但按 §3.3.1「行号指向**消费点**」应订正 |
| **🔴 覆盖面比 global 转达更广（重要）** | global 转达说「comment / like / message 三处同款」⇒ **独立扫全仓 rpc 侧出向 stub，实际是 6 处裸 stub**：`comment-rpc/ArticleRpcClient.java:54` · `comment-rpc/MessageRpcClient.java:49` · `like-rpc/MessageRpcClient.java:53` · `follow-rpc/UserRpcClient.java:32` · `message-rpc/UserRpcClient.java:26` · `article-rpc/MessageRpcClient.java:54` |
| **🔴🔴 另发现一处「看起来修了、其实没修」（最坏形态）** | `favorite-rpc/.../UserRpcClient.java:56` 与 `favorite-rpc/.../ArticleRpcClient.java:57` **已经写了** `GoStyleRequestDeadline.applyTo(stub)` ⇒ **看起来已对齐**；但 favorite-rpc 是 **gRPC 服务端** ⇒ 该载体在此**从未绑定** ⇒ **两处都是 no-op** ⇒ **同型缺陷仍存在**。｜ 📌 **2026-10-30 订正（共享层 `53bc05ec`）**：共享层已在 `RpcInterceptor.onHalfClose` 绑定该载体 ⇒ ⇒ **favorite-rpc 的两处 `applyTo` 现已真正生效** ⇒ ⇒ **本条对 favorite 域不再成立**（favorite 域侧确认见 [`favorite/rpc.md`](../favorite/rpc.md) 的「共享层『出向 deadline 载体』落码后的本域侧确认」段）⇒ ⇒ **本条对 comment 域也已收窄**：comment 域的出口 `withDeadline` **读 `remainingMillis()`** ⇒ **继承已生效**（毫秒精度）⇒ ⇒ 残留仅**精度差 ≤1ms**<br>⇒ 这是 §3.3「**代码里写了 ≠ 机制生效**」的变体：**比裸 stub 更难发现**，因为它伪装成已修 ⇒ 已作为**只读指针**反馈给 global（favorite 域另有 owner 会话 ⇒ **本域不代改、不代下结论**） |
| **证据强度** | 🟡 **源码级（两侧逐行取证）+ global 侧双侧活体探针实测**（Go `failed_fast=true` / `code=4` vs RAG `failed_fast=false` / `code=0`）⇒ **活体层证据由 global 提供，本域独立核实的是源码层** |
| **⚠️ 本域那半的修法（**待 Jason 拍板，本轮未实施**）** | global 给的方向：共享层在 `RpcInterceptor.onHalfClose` 把有效期限也绑进 `GoStyleRequestDeadline` ⇒ 本域把裸 stub 改成 `GoStyleRequestDeadline.applyTo(stub)`（comment / like / message 等 6 处）。<br>💡 **主会话补充一条 global 方案里没提到的关键事实**：`DeadlineState` **在 gRPC 服务端路径上已经绑定了**（`RpcInterceptor.java:281/372`）⇒ ⇒ **本域那半其实不必等共享层**：可直接用 `DeadlineState.remainingMillis()` 构造 stub deadline（**同仓既有先例**：`users/admin/AdminDomainService.java:140` · `users/user/UserDomainService.java:145` 已用 `DeadlineState.isExpired()`）⇒ **现在就能真正闭环 E65**。<br>⚠️ **两条路各有代价**：走 `applyTo` ⇒ 共享层落地前是 **no-op**（行为不变、但也没修好）· 走 `DeadlineState` ⇒ **现在就能修好**，但**偏离 global 的统一载体方向**，且会**让 global 的门禁 `check-deadline-downstream.py` 变红**（drift 键 `outgoing_carrier` 钉的是 RAG=`absent`）⇒ **必须由 global 回写台账**（global 已明示「请回来找我，我回写台账」）。⇒ **本域不擅自选边，等拍板** |
| **⚠️ 为什么本域现在不写守门网** | `applyTo` 在**共享层那半落地前是 no-op** ⇒ 写出来的守门网**测不出任何东西**（断言恒成立）⇒ 属 §3.3「**绿测试可能是错误答案的守卫**」⇒ **不写假绿网**；等共享层落地 / 修法拍板后，一并配**能真正触发载体**的守门网 ｜ 📌 **2026-10-30 订正**：该前提**已过期** —— 共享层 `53bc05ec` 已落地 ⇒ ⇒ 载体在位 ⇒ ⇒ 守门网**现在可以真正触发载体**（断言不再恒成立）⇒ ⇒ **「不写假绿网」的理由已消失** ⇒ ⇒ 守门网**现在就能写**（可断言「出向预算 = `min(2s, 入向剩余)`」**不再恒成立**）⇒ ⇒ **本轮未做** |

> ⚠️⚠️ **2026-10-29 真实性复核（W-D lane · 本条拆成「两半」，一半已闭合 · 另一半仍开着 —— 原判 🔴 与原句一律保留）**
>
> | 半 | 现行态 | 依据（2026-10-29 实测） |
> |---|---|---|
> | ① **「2s 封顶」**（Go `TimeoutInterceptor` 的 per-client 默认 2s） | ✅ **已闭合（2026-10-31 第十一轮：域侧副本已删 ⇒ 改由共享层统一施加）** | `service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java:137-140`（读 `capMillis`）`:157-158`（把 2s 压进 min）· 域侧 `comment-rpc/.../application.yml` 的 `rag.protection.outbound-deadline.cap-millis=2000` · **本域两个出向 client 现在是裸 stub**：`ArticleRpcClient` 与 `MessageRpcClient`（同包）⇒ 上界由 devh 全局拦截器链统一施加（`min(入向剩余, 2s, 调用点自带)` · 预算耗尽给 **1ns**）⇒ **不再是「零期限」** |
> | ② **「继承请求 deadline」**（Go 侧 `subject_fallback.go:17` 吃 `ctx` ⇒ 出向调用随服务端 deadline 到点即止） | ✅ **已闭合**（**2026-10-31 第十一轮**：域内副本已删 ⇒ 共享层按纳秒算 ⇒ 残留的「≤1ms 精度差」已消解；**2026-10-31 本体销账**：四键逐键相等 ⇒ 本体亦闭） | 📌 **共享层那半已落地**（`53bc05ec` ⇒ `RpcInterceptor.onHalfClose` 也绑 ⇒ 两个载体**已连通**，同一绝对时刻）⇒ 📌 **本域出口已继承（2026-10-30 快照 · 2026-10-31 第十一轮：域内副本已删、精度改由共享层按纳秒算 ⇒ 残留的「≤1ms 精度差」已消解）**：`GoStyleClientCallTimeout.withDeadline(stub)`（实现 `:90-95`）**读 `remainingMillis()`** ⇒ 载体在位时实际生效的是 `min(2s, 剩余)` ⇒ ⇒ **「继承」这一半已闭合**（原写「载体在位却未被消费」**已过期**）；**残留 = 精度差 ≤1ms（2026-10-31 第十一轮已消解）**（原写「`GoStyleRequestDeadline` 仍只在 HTTP worker `begin()`」**也已过期**）；该副本当时的 javadoc（`GoStyleClientCallTimeout.java:60-64`，**该文件已于 2026-10-31 删除**）即写明「**本类只解决『2s 封顶』这一半** … 另一半点在**共享层** ⇒ 已登记为 global 的 **E65**」 |
>
> ⇒ **判定**：本条**核心主张（出向 gRPC 不继承请求 deadline）已被推翻** ⇒ **现行态 = ✅ 已闭合**（**本体 = global 已销账** ⇒ 四键逐键相等 ⇒ ①「2s 封顶」与 ②「继承请求 deadline」**两半均闭** ⇒ ⇒ **整条 D8 / E65 = ✅ 已闭合**）。｜ 📌 **2026-10-30 订正**：**② 这一半仍未闭合，但原因已换** —— 原为「共享层那半未落地」（`GoStyleRequestDeadline` 只在 HTTP worker 绑定 ⇒ gRPC 服务端恒 no-op），现为「**共享层那半已落地（`53bc05ec`）· 本域出口已继承（毫秒精度）· 残留仅精度差 ≤1ms**」⇒ ⇒ **本条已不是「不继承」，而是「精度未对齐」** ⇒ ⇒ **落点 = 本域的精度对齐**（`service/comment/comment-rpc/**` 的两处出口改用纳秒版 `applyTo`）。｜ 📌 **【2026-10-31 · G3 第十六轮 · 本轮独立复核（非照抄本文件）】上面那句「落点 = 本域的精度对齐」**已过期** ⇒ ⇒ 该落点**已由第十一轮闭合** ⇒ ⇒ ⇒ **本域那半（①「2s 封顶」＋ ②「继承请求 deadline」）= 两半均已闭合** ⇒ ⇒ ⇒ **本体亦已销账**（2026-10-31 · global 面 `docs/DRIFT/global/vs-common.md:30` 记「E65 本条 = ✅ 已闭合 · 四键逐键相等」⇒ **本层独立复跑 `tools/difftest/check-deadline-downstream.py` ⇒ EXIT=0**）⇒ ⇒ ⇒ **整条 D8 / E65 = ✅ 已闭合**（**销账权在 global** · 本域只做**投影订正**）⇒ ⇒ ⇒ **派生层 `status` 随之由 `🔴 未闭合` 转 `✅ 已一致`**（定级层 U5-O 第 5 轮 `W33` 已先改判 **D / P3 / 阻断=否** ⇒ 本次是**台账层销账** ⇒ 与该定级一致）⇒ ⇒ ⇒ **本行不再构成本域的阶段一阻断项**。<br>**本轮独立复核的 6 条事实**（每条带坐标）：① 域侧副本已删净 —— `service/comment/comment-rpc/**` 下**零个** `GoStyleClientCallTimeout.java`；② 两个出向 client 是**裸 stub**（`client/ArticleRpcClient.java:57` ＋ `client/MessageRpcClient.java:52` 的 javadoc 明写「deadline 由共享层的全局客户端拦截器统一施加」）⇒ ⇒ 域侧**零**自带封顶；③ 共享层按**纳秒**算 —— `service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java:194` `GoStyleRequestDeadline.remainingNanos()` · `:196` 取 min · `:200-201` 并入 `capMillis`（**读的是纳秒** ⇒ ⇒ 「毫秒 → 纳秒」的精度对齐**已达成**）；④ 载体在 gRPC 服务端路径绑定 —— `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:494` `onHalfClose()` 里 `try (...)` **同时**绑两个载体（`:505` 注释直写「🔴 E65」），且 `super.onHalfClose()`（真正跑 handler）在**作用域内** ⇒ ⇒ handler 执行期 `remainingNanos()` 读得到；⑤ devh 的全局拦截器装在 **channel** 上 ⇒ ⇒ **裸 stub 也经过** —— `net/devh/boot/grpc/client/channelfactory/AbstractChannelFactory.java:115` `return ClientInterceptors.interceptForward(channel, interceptors);`；⑥ devh 的全局拦截器按 **Bean 注解**收集 —— `net/devh/boot/grpc/client/interceptor/AnnotationGlobalClientInterceptorConfigurer.java:58` `getBeansWithAnnotation(GrpcGlobalClientInterceptor.class)`。⇒ ⇒ **①②⑥ 三条合起来 ⇒ 「域侧零封顶 ＋ 共享层统一施加 min(入向剩余纳秒, 2s)」这条链在生产路径上成立** ⇒ ⇒ **② 这一半闭合**（不是靠台账自述，是靠上面 6 条坐标）。
> ⇒ ⚠️ **上表「RAG 现状」行已过期**（原句保留）：原写「`:65` `stub.getArticle(...)` 是**裸 stub** ⇒ **零期限**」⇒ **现已不成立**（`:71` 已套 2s 封顶）⇒ **该行只能按上面 ①/② 两格读**。
> ⇒ ⚠️ **上表「本域那半的修法（待拍板，本轮未实施）」行已过期**（原句保留）：**域侧那半已实施**（形态 = 域内 `GoStyleClientCallTimeout` 包装，**不是**原写的「直接 `GoStyleRequestDeadline.applyTo(stub)`」—— 后者在共享层落地前会是 no-op）⇒ **共享层那半（②）仍待 global**。｜ 📌 **2026-10-30 订正**：**「共享层那半（②）仍待 global」已过期** —— `53bc05ec` 已落地 ⇒ ⇒ **2026-10-31 第十一轮：该待办已闭合** —— 域内副本 `GoStyleClientCallTimeout` **已删除**，出向恢复裸 stub ⇒ 上界由共享层 `GoStyleOutboundDeadlineInterceptor` 按**纳秒**算（读 `remainingNanos()` + 域侧 `cap-millis=2000`）⇒ ⇒ **「毫秒 → 纳秒」的精度对齐随之闭合** ⇒ ⇒ 上一句「剩余落点 = 本域出口改用纳秒版」为 2026-10-30 的历史待办。
> ⇒ **本域那 6 处裸 stub 的覆盖面**：D8 行所列 6 处中，**comment 域的 2 处（`ArticleRpcClient` / `MessageRpcClient`）已封顶**；其余 4 处属 like / follow / message / article 域 ⇒ **本 lane 未核**。
> ⇒ 核对日期 2026-10-29。

### 3.6 D9（= global **E66**）：Redis 调用**不感知 deadline** ⇒ 缓存读写**照写不误** —— **写面已闭合**（2026-10-31 · 双侧活体逐键相等）· **整条 E66 仍 🔴 未闭合**（残余三条见 global 面 §4.19.19）

> **归属**：global 域第四十六轮（`align/global` @ **`96d94db`**）登记为 **E66** ⇒ 命中 **comment 域代码** ⇒ **本域独立复核后落账**。

| 项 | 值 |
|---|---|
| **Go 事实** | `service/comment/rpc/internal/cache/` **全部**带 `ctx`：`comment_subject_cache.go:76` `GetSubjectCache(ctx,…)` → `c.rdb.Get(ctx, SubjectKey(subjectID))` · `comment_subject_cache.go:103` `SetSubjectCache(ctx,…)` → `c.rdb.Set(ctx, key, b, ttl)` · `comment_invalidation_cache.go:10/37/56` `InvalidateTargetCaches` / `DeleteCommentIndexCache` / `deleteKeys` → `c.rdb.Scan(ctx, cursor, pattern, 100)` / `c.deleteKeys(ctx, …)` ⇒ **go-redis v9 的这些方法都吃 `ctx`** ⇒ **deadline 到点 ⇒ 缓存操作立即中止** |
| **RAG 现状（2026-10-31 订正）** | ✅ **已闭环** —— 共享层的 Redis 期限载体已落地并**自动装配进本域**：① `service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadlineConnectionFactory.java:58`（**建连之前**先查预算 ⇒ 与 Go 的 `internal/pool/waitTurn(ctx)` fast path 检查位置逐条对齐；`:60` 的拨号在检查**之后**）② `service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadlineConnection.java:77`（**每条命令**再查一次 ⇒ 兜住**事务绑定**场景）③ `service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadlineAutoConfiguration.java:63` 的静态 `BeanPostProcessor`（`:79` 换 factory）把各 `RedisTemplate` 的 connection factory 换成包装版 ⇒ ⇒ 本域 `service/comment/comment-rpc/.../service/CommentCacheService.java:88` 注入的 `StringRedisTemplate` **就是**被包过的那个 ⇒ ⇒ **全部** `redis.opsForValue()…`（**含写**：`:279`/`:331`/`:433`/`:528` 的 `set`、`:619` 的 `delete`；读：`:250`/`:341`/`:376`/`:479`）**零改动**即被覆盖 ⇒ **预算耗尽 ⇒ 命令不下发 ⇒ 写不再发生**。⚠️ **拦截面已按 javap 实测核实**（本仓实际用的 `spring-data-redis 4.0.7`，由 `mvn -o -pl service/common dependency:list` 实测 ⇒ 非推理）：`DefaultedRedisConnection` **重声明**了 `get` / `set` / `mGet` / `del` / `scan` ⇒ ⇒ 本域 **11 个**调用点**全部在拦截面内**；本域**零 pipeline 写** ⇒ ⇒ pipeline 面（命令在 `closePipeline()` 才下发 ⇒ 不拦截）**在本域无缺口** |
| **可观察后果（2026-10-31 订正）** | **修前**：deadline 到点后，Go 的缓存读写**不再发生**；RAG **照写不误** ⇒ **写路径尤其值得注意**（`set` / `delete` 在 deadline 后仍落 Redis）。**修后（现行态）**：两侧**同形** —— deadline 到点 ⇒ **命令根本不下发** ⇒ RAG 的 `set` / `delete` **不再落 Redis**（读回来仍是旧值）⇒ ⇒ 原判那条可观察差异**已消失** |
| **根因（共享层）· 2026-10-31 订正** | **修前**：`service/common` 里**没有任何 Redis 相关的期限载体** ⇒ `DeadlineState`（只覆盖 SQL）· `GoStyleRequestDeadline`（只覆盖 HTTP + 出向 stub）**都不覆盖 Redis** ⇒ **本域那半在共享层落地前无法实施**。**现状**：共享层那半**已落地**（Redis 期限载体 ＋ `BeanPostProcessor` 自动装配）⇒ ⇒ **原根因已消解**；⚠️ 残留的是**资源层**窄窗口（「拨号窗口内过期 ⇒ RAG 仍把连接拨出来、Go 取消拨号」）⇒ 归 `docs/DRIFT/global/vs-common.md` §4.19.7（global 域写权） |
| **🟢 独立核实结论** | ✅ **成立**（**2026-10-26 当时的口径**：主会话核 Go 侧全部 cache 方法签名 + Redis 调用（`rdb.Get/Set/Scan` 均带 `ctx`）+ RAG 侧 `CommentCacheService.java` 零 deadline 检查 ⇒ **未照抄 global 的结论**）⇒ ⇒ **该漂移已于 2026-10-31 销账**（见上方「RAG 现状（2026-10-31 订正）」行与「本域现状登记」行）⇒ ⇒ 本行留作**历史核验记录**，**不是**现行结论 |
| **证据强度** | 🟢 **A 活体**（2026-10-31 升级）—— **两侧同时跑**的探针 `tools/semantic-probe/redis-deadline/run-redis-deadline-parity.sh`（**本域独立复跑，EXIT=0**；Go 半边用冻结仓 `go.mod`/`go.sum` 钉死的 go-redis v9，RAG 半边编译**生产类** `GoStyleRedisDeadlineConnectionFactory` ＋ 真 Lettuce）⇒ 两侧 PROBE 行**逐键相等**：`form=exhausted` ⇒ `write_applied=false` / `err_is_deadline=true` / `value_after=baseline`（**读回来仍是基线值 ⇒ 命令根本没下发**）＋ `form=alive`（负控）⇒ `write_applied=true` / `err_is_deadline=false` ⇒ ⇒ 「请求 deadline 已耗尽 ⇒ 缓存写不再发生」两侧**同形**（活体证据）。＋ **本域装配面活体守门** `CommentRedisDeadlineCarrierWiringGateTest`（**3 例**，真 Redis Testcontainers，本域实测 3/3 绿）⇒ ① 容器里 `StringRedisTemplate` **有且只有一个**且其 `getConnectionFactory()` **就是** `GoStyleRedisDeadlineConnectionFactory`（BPP 包装**真的装配上了**）② 载体耗尽 ⇒ `set` 抛 `QueryTimeoutException` 且**读回仍是基线值** ③ 载体充足 ⇒ 写得进（负控）④ **事务绑定路径**（`redis.execute(SessionCallback)` = 本域 `:429`/`:433` 与 `:524`/`:528` 的形态）⇒ 载体耗尽 ⇒ 内层 `set` 同样被拦且值未变 ⇒ ⇒ **机制层 ＋ 装配面 ＋ 事务面**三面同形。⚠️ **证据边界**：① 共享层的**变异**证据（拆 BPP ⇒ 判据 ① 应转红 / 拆每命令检查 ⇒ 判据 ② 应转红）**归 G4 写权**，本域**未代做、不代称**；② 「A 活体」档在派生层里还需**两个外部条件**：探针文件在本分支树上（主干 `1da8075b` 已落，本分支落后主干 ⇒ **合批窗口才生效**）＋ 全局活体台账 `docs/DRIFT/_cross/live-parity-2026-10-30.md` §二 登记该探针行（**共享面 ⇒ 收口执行者**）⇒ ⇒ **本地派生层该格仍显示「B 静态」**（⇒ 那是「工具引用还没解析到」，**不是**「证据弱」） |
| **修法方向（**原写「待 Jason 拍板，本轮未实施」· 2026-10-31 订正：已实施**）** | global 的判断：**共同根因只有一个 —— RAG 没有 ctx 传播** ⇒ 共享层补一个**统一的「请求期限载体」**（现在两个互不相通的载体才是病根）⇒ Redis 调用点也能查到期限。<br>⇒ **落地形态**（共享层 `service/common`，域侧**零改动**）：Redis 期限载体 ＋ `BeanPostProcessor` 把各 `RedisTemplate` 的 connection factory 换成预算感知版 ⇒ ⇒ **本域那半随之生效** ⇒ ⇒ 原写「依赖共享层先有载体 ⇒ **共享层未落地前本域无法实施** ⇒ 本域不擅自加临时机制」**已消解**（且本域**确实没有引入第三种载体** —— 载体只有共享层这一份） |
| **⚠️ 本域现状登记** | **现行态 = 🔴 未闭合**（2026-10-31 · **整条 E66**）—— ⚠️ **已闭合的只是「写面」**（缓存读写命令**是否下发**那一面）：共享层 Redis 期限载体（`service/common` 的 `GoStyleRedisDeadlineConnectionFactory` ＋ `BeanPostProcessor` 自动装配）⇒ 各域**零改动**生效 ⇒ 预算耗尽 ⇒ 命令不下发 ⇒ **双侧活体对拍** `tools/semantic-probe/redis-deadline/run-redis-deadline-parity.sh`（**EXIT=0**）⇒ 两侧 PROBE 行**逐键相等**（`form=exhausted` ⇒ `write_applied=false` / `value_after=baseline`；`form=alive` 负控 ⇒ `write_applied=true`）⇒ ⇒ **写面已同形**（A 活体）⇒ ⇒ **但整条 E66 仍 🔴 未闭合** ⇒ **残余三条**（登记在 global 面 `docs/DRIFT/global/vs-common.md` §4.19.19 · global 域写权）：① **handler 级码集合面**（Go 侧 = {1015, 5001} · RAG 侧仅机制推导 · 尚未双侧活体对拍 ⇒ G1 面）② **资源层窄窗口**（拨号窗口内过期 ⇒ RAG 仍把连接拨出来、Go 取消拨号 ⇒ global 面写权）③ **域侧装配面断言**（comment 域已有容器面活体守门 `CommentRedisDeadlineCarrierWiringGateTest` · users 域待补 ⇒ G1 面）⇒ ⇒ ⇒ **⚠️ 本条不得被读成「E66 整条已闭合」** ⇒ ⇒ ⇒ 否则会盖掉上面三条残余 ⇒ ⇒ ⇒ **派工面假绿**（2026-10-31 实测：合本域改动后派生行 `status` 会从 `🔴 未闭合` 翻成 `✅ 已一致`、`evidence` 从 `A 活体` 降成 `B 静态`、`gap` 从「静态取证 · 缺活体对拍」变 `—` ⇒ ⇒ 三列同时退化）⇒ ⇒ ⇒ **共享 overlay `docs/DRIFT/_cross/grading/W6.md`（E66 行）已同步**（2026-10-31 · 收口执行者）⇒ ⇒ ⇒ ⚠️ **本行的收窄由合并收口执行者代 G3 lane 落**（G3 lane 会话因 Token Plan 用量上限耗尽停摆 · `errorCode 42212`）⇒ ⇒ 落码依据 = 已下达的「甲」裁定（**整条 E66 不得销账 · 只销「写面」那半**）＋ 独立 verifier 的 FAIL 判定 ⇒ ⇒ ⇒ **同一次提交里实测过派生层**：收窄前派生行会翻成 `✅ 已一致`、`evidence` 降成 `B 静态` ⇒ ⇒ 收窄后实测 `status = 🔴 未闭合` 保持不变⇒ ⇒ ⇒ 阶段归属与优先级以该 overlay 为准 |

---


### 3.7 🔴 D10：fact 投递链路的 span 属性 = RAG 侧独有的**无效代码**（**不是语义漂移**）

**通报来源**：G4 在其 **FB-04**（让 gRPC 业务 handler 执行期能拿到当前 server span）之后查出，**报给 G3**（G3 写权，G4 不代改）。
⚠️ **一条线索**：FB-04 修好后「**有 span 的线程**」变多了 ⇒ ⇒ 原本「到处都没 span ⇒ 没人发现」变成
「**handler 有、调度线程没有** ⇒ 差异显形」⇒ ⇒ **这条很可能是被 FB-04 照出来的，不是新引入的**。

**核验（四条逐条实测，不采信转述）**

> ⚠️ **本节含一处早期误判的订正**（2026-10-27，**未提交过 ⇒ 直接改正，不是事后翻案**）：
> 最初写的是「Go 侧 fact 投递链路**根本不用 span**」⇒ **不准确** ⇒ Go 侧**有**这个 span，
> 只是建在**共享层**且**不带属性** ⇒ ⇒ **结论不变**（那两个属性是 RAG 侧独有的、且写不进去），
> **但依据整条换掉** ⇒ ⇒ 教训见坑档 [`PIT-COMMENT-RPC-14`](../../Pitfalls/comment/rpc.md)。

| 判据 | 实测 |
|---|---|
| Go 侧用不用 span | ⚠️ **不是「不用」** —— Go 侧**有** fact 投递 span，但**建在共享层**且**不带属性**：`service/common/communityfact/process.go:26` `otel.Tracer("sea.rtw.communityfact").Start(ctx, "community.fact_delivery.dispatch")`（全文 69 行、**零** `SetAttributes`）⇒ ⇒ 域内 `service/comment/rpc/internal/model/` 整包（**10** 个非测试文件）**零** span 命中 ⇒ ⇒ Go 的 `DispatchFactOnce`（`fact_delivery.go:53`）**从不写 span 属性** ⇒ ⇒ **那两个属性确实是 RAG 侧独有的** |
| RAG 侧对应的 span 在哪 | **共享层** `service/common/.../communityfact/Dispatcher.java:186`（**同名** `community.fact_delivery.dispatch` ＋ **同 tracer** `sea.rtw.communityfact`）⇒ ⇒ **span 拓扑两侧已对齐**，差的只是这两个属性 |
| 为什么 RAG 侧也写不进去 | 共享层把 span 放进 **Context**（`Dispatcher.java:189` `ctx.with(span)`）而**从不 `makeCurrent`**（该文件**零** `makeCurrent`）＋ `dispatchFactOnce` 跑在**另一个线程**（`:172-176` `communityfact-dispatch` 单线程池、`:190` `supplyAsync`）⇒ ⇒ OTel 默认 `ThreadLocalContextStorage` 用的是**普通** `ThreadLocal`（**不继承**）⇒ ⇒ 投递线程上 `Span.current()` 恒为 **invalid span**（**非 null**）⇒ ⇒ `if (span != null)` **恒真** ⇒ ⇒ `setAttribute` 落到 invalid span ⇒ **静默丢弃**（otel-java 的 invalid span 是 no-op） |
| RAG 侧谁在调 | 唯一调用方 = `bootstrap/CommentFactDispatchScheduler.java:95`（把 `deliveryService::dispatchOnce` 交给共享层 `Dispatcher.runAsync`）⇒ ⇒ **必走**上面那条 `communityfact-dispatch` 线程 ⇒ ⇒ 上面那条推理对本域**恒成立**（无旁路） |

**⇒ 定性（重要，别写成「语义漂移」）**：**行为层两侧一致**（Go 侧**也没有**这两个属性 ⇒ 可观测行为无差）⇒ ⇒
**这不是语义漂移**，是 **RAG 侧独有的「看起来在工作、实际永远不生效」的代码** ⇒ ⇒
属 AGENTS.md §3.3.3「注解 / 写法存在 ≠ 机制生效」那一族。

**修法（2026-10-27 已改）**：删**改动前** `CommentFactDeliveryService.java:148` 的 `Span span = Span.current();`
＋ `:192` 的 `if (span != null) {` ＋ `:193-194` 的两个 `span.setAttribute(…)` ＋ 随之失效的 `:6` `import io.opentelemetry.api.trace.Span`
⇒ **零业务行为变化**（那两个属性本来就写不进去）⇒ **信息不丢**（`:146-147` 的 `logger.info` 已打 `event_id` / `aggregate_version`）
⇒ **原地留注记**（写明「Go 侧那两属性不存在」＋「RAG 侧为什么也写不进去」）⇒ **防复活**。

**守门（3 条 · 加进 `CommentRpcGoSemanticsGateTest`，与该类既有装置同跑）**

| # | 断言 | 作用 |
|---|---|---|
| ① **装置活性** | 同线程挂的真 span **能**收到人为写的属性 | ⇒ **排除平凡通过**（否则 ② 会因为「根本没有 span」而恒绿） |
| ② **对齐** | 把一个**真 span 挂在投递外面**（`makeCurrent` 作用域内跑 `dispatchOnce`）⇒ 断言那个 span 上**无** `comment.fact.*` 属性 | ⇒ **牙齿检查**：若实现又写 `Span.current().setAttribute` ⇒ 该 span 会带上属性 ⇒ **必红** ⇒ 「**改回去必红、改完必绿**」 |
| ③ **根因** | 不挂作用域时 `Span.current().getSpanContext().isValid() == false` | ⇒ 钉住「原实现写不进去」这个根因 |

⚠️ **② 的前提（已核，写在这里防后人误改）**：`dispatchOnce` **自己不开 span**（**改动前**该类 `Span` 引用仅 2 处：取值 ＋ 写属性），
且它收到的 `Context.root()` 只是**参数**、**不会** `makeCurrent` ⇒ ⇒ `Span.current()` 仍取决于**线程作用域**。

**范式出处（照抄，不发明第三种写法）**：OTel 装配层 = 同域 `CommentSpanWiringTest`（自建 `CollectingExporter`，
**不引 `opentelemetry-sdk-testing`** ⇒ **零新依赖**）· 投递装置 = 本类既有装置（真 PG ＋ Flyway ＋ 真 MyBatis ＋ fake sender）。

**🔴 当前状态（2026-10-27 已跑绿 · 核对日期 2026-10-27）**：代码 + 守门**已改且已验证** ⇒

| 项 | 值 |
|---|---|
| **落码 commit** | **`deeeb8d`**（代码 + 守门**同一笔**；文档另起一笔）⇒ ⇒ 台账里的行号均指**改动前**的行号（`:6` / `:148` / `:192-194`） |
| 门禁跑通 | `mvn -o -pl service/comment/comment-rpc -am test -Dtest=CommentRpcGoSemanticsGateTest` ⇒ **EXIT=0** · **14** 条（既有 **11** ＋ 新增 **3**）· 0 failures / 0 errors / 0 skipped ⇒ ⇒ surefire 报告里 **3 条新用例名逐条在列**（⇒ 不是「被过滤掉的绿」） |
| **变异验证** | 把 `Span span = Span.current();`（原 `:148`）＋ `if (span != null) { … }`（原 `:192-194`）**原样加回**（行号与改动前**逐条对齐**）⇒ ⇒ **14 条里恰好 1 条红**：`factDeliveryDoesNotWriteSpanAttributes`；另两条（① 装置活性、③ 根因）**仍绿** ⇒ ⇒ **精确变红**（不是全红 ⇒ 判据没被别的因素带偏）⇒ ⇒ 失败断言原文点名 `comment.fact.event_id` / `comment.fact.aggregate_version` ⇒ ⇒ **「改回去必红、改完必绿」成立** ⇒ ⇒ 回滚后本域回归恢复全绿 |
| 本域回归 | `mvn -o --fail-at-end -pl service/comment/comment-rpc,service/comment/comment-api,service/comment/comment-common -am test` ⇒ **BUILD SUCCESS** · **198** 条（common **27** / api **52** / rpc **119**）· **0 failures / 0 errors / 0 skipped** ⇒ ⇒ 两口径（模块汇总行 ≡ 逐类明细行）**逐模块一致** ⇒ ⚠️ rpc **119** = 旧 **116** ＋ 本轮新增 **3** |
| 零行为变化 | 删掉的那几行**本来就写不进去**（投递线程上 `Span.current()` 恒为 invalid span）⇒ ⇒ 生产行为**零变化** ⇒ ⇒ 本轮净变化 = 删 **6** 行死代码（含 1 个 import）+ 增 **3** 条测试 |

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

> 📌 **2026-10-29 · G3 补机器证据 + 订正基线归类**（基线 `docs/PhaseGoal-judge.md` §十一 **D-07**）
>
> · **机器证据（可复算）**：`python3 tools/difftest/check-ddl-static.py --domain comment --indexes`
>   ⇒ **EXIT=2**，输出逐条列出**仅 RAG 有**的 **8 列**（`report_record` 的 `id` / `user_id` / `comment_id` /
>   `target_type` / `target_id` / `reason` / `detail` / `created_at`）⇒ ⇒ **差异单位是「一整张表 8 列」**，
>   **不是**基线 D-07 写的「2 条（`user_id` / `target_type`）」⇒ ⇒ **D-07 的计数口径有误**。
>   ⇒ 同一次运行里**其余 6 张共享表**的类型/长度 **0 差异** · 可空性 **0 差异** · DEFAULT **0 差异** ·
>   索引 **Go 16 ≡ RAG 16**（2 处仅命名形态差）· 约束逐条一致 ⇒ ⇒ **除 `report_record` 外两侧一致**。
>   ⚠️⚠️ **2026-10-29 真实性复核（W-D lane · 🔴 本段「其余 6 张共享表」结论已证伪，原句保留 + 追加订正）**
>
>   **本段的「机器证据」有两处问题，2026-10-29 本机实测坐实**：
>
>   ① 🔴 **「同一次运行」根本跑不到那 6 张表** —— `check-ddl-static.py` 的「列集合不一致」分支在
>   **类型 / 可空性 / DEFAULT / 索引 / 约束 / 触发器** 七项对照**之前**就 `return 2`
>   ⇒ **默认口径的产出就是 `EXIT=2` + 8 行「仅 RAG 有: `report_record.*`」**（2026-10-29 实跑复现）
>   ⇒ ⇒ **「可空性 0 差异 · DEFAULT 0 差异 · 索引 16 ≡ 16」不可能是那一次运行的输出**。
>   ② 🔴 **即便用补跑口径（`--rag-db` 指向去掉 `report_record` 的同源副本）重跑，结论也与本段相反** —— **2026-10-29 本机实跑：`EXIT=1`、实质差异 23 处**：
>   **可空性 8 处差异**（`subject` / `comment_index` / `comment_content` / `comment_like` × `created_at` / `updated_at` ⇒ Go `nullable=YES` / RAG `nullable=NO`）·
>   **DEFAULT 10 处差异**（其中 **8 处**是同族时间列 `go=<NULL>` / `rag=CURRENT_TIMESTAMP`，另 **2 处** `subject.id` / `comment_like.id` 是 IDENTITY 盲点假阳性）·
>   类型/长度 **52 列逐字相同** · 索引 **Go 11 个 / RAG 13 个**（RAG 多 `idx_comment_fact_delivery` + `idx_comment_legacy_fact_aggregate`）·
>   约束 **6 / 9 行**（RAG 多 `comment_new_fact_has_dc_wire` + `comment_fact_stream_version_range`，另 1 处仅命名形态差）·
>   触发器 **0 / 1 个**（RAG 多 `comment_fact_identity_immutable`）。
>   ⇒ ⇒ **「除 `report_record` 外两侧一致」这句话是错的**（**类型/长度**那一维度确实 0 差异，**其余六维度都不是**）。
>
>   ⇒ **本块「机器证据（可复算）」这一措辞作废** ⇒ 按主线要求，comment 域这 **8 处可空性差 + 8 处 DEFAULT 差（+ 2 处 IDENTITY 假阳性）目前**机器不可复算**（默认口径提前退出）⇒ 台账一律写「**需人工核对**」**，不写「可复算」。
>   ⚠️⚠️ **2026-10-30 真实性复核（W-D lane · 二次订正：上一行原措辞与本块 ② 自相矛盾 ⇒ 原句保留 + 订正）**
>
>   上一行原写「**目前机器不可复算** ⇒ 台账一律写『需人工核对』，不写『可复算』」⇒ ⇒ **这句只对「默认口径」成立，对「补跑口径」不成立** ——
>   本块 ② 自己就写了「**即便用补跑口径（`--rag-db` 指向去掉 `report_record` 的同源副本）重跑 ⇒ `EXIT=1` / 23 处**」⇒ ⇒ **同一段里两句话互斥** ⇒ ⇒ **必须订正**。
>
>   ⇒ ✅ **准确口径 = 「默认口径不可复算 · 补跑口径可复算」**：
>   · **默认口径**：`python3 tools/difftest/check-ddl-static.py --domain comment --indexes` ⇒ **EXIT=2** ⇒ 在**已签字**的 `report_record` 列集合差异处**提前退出** ⇒ ⇒ **七项对照一个都跑不到** ⇒ ⇒ 这一口径下**确实不可复算**。
>   · **补跑口径**（工具与仓内文件**零改动**，只覆盖 `--rag-db` 指向同源副本库）⇒ **EXIT=1 / 23 处** ⇒ ⇒ **可复算** ⇒ ⇒ 「一律写需人工核对、不写可复算」这句**对补跑口径是错的**。
>   ⇒ ⇒ **台账写法（本 lane 统一口径）**：凡引用本域 DDL 数字，**写「可复算（补跑口径 `--rag-db` 覆盖）」＋ 同时记「默认口径 `EXIT=2`（列集合不一致处提前退出）」** —— **两个口径都要写**。
>   ⇒ ⚠️ **本仓已有的同款记录（互为复算）**：`docs/DRIFT/comment/vs-common.md` §9.0（补跑口径命令原文 + `EXIT=1`）· §9.2（八个维度逐项结论）· §9.4（23 处逐条）· §9.4② 的 2026-10-29 真实性复核（「**本节数字可复算（须按 §9.0 的补跑口径）**」）⇒ ⇒ **两处口径现已一致**。
>   ⇒ ⚠️ **2026-10-30 本 lane 独立复跑一次**（新建同源副本 `rag_comment_db_cmp6` ⇒ `DROP TABLE report_record` ⇒ `--rag-db rag_comment_db_cmp6`）⇒ **EXIT=1 / 23 处**（可空性 8 · DEFAULT 10 · 索引 2 · 约束 2 · 触发器 1）⇒ ⇒ **与 2026-10-29 那次逐条对上** ⇒ **数字可复算成立**。核对日期 2026-10-30。
>   ⇒ **正确口径与逐条结论以 [`vs-common.md` §9.0 / §9.4](./vs-common.md) 为准**（那里已用 `--rag-db` 补跑口径记录了同一组数字：23 处 / 可空性 8 / DEFAULT 10）⇒ **本块的「差异单位是一整张表 8 列」「D-07 计数口径有误」「本项是决策不是缺陷」三条结论不变**（`report_record` 那部分确实成立）。
>   ⇒ ⚠️ **同表上方第 435 行「9 处时间列的 NOT NULL + DEFAULT ⇒ 🔴 不一致」才是对的** ⇒ 本块与它**自相矛盾**，现以本订正段与 `vs-common.md` §9.4 为准。核对日期 2026-10-29。
> · **归类订正**：D-07 把本项列进「§11.1 🔴 **真实实现缺陷**（代码/配置层面有确凿差异，**必须修**）」并标注「**未签字**」
>   ⇒ ⇒ **与既有签字偏离矛盾**：`docs/Intentional_Deviations/comment/rpc.md` 的 **`SD-2026-10-24-CAND-02`**
>   （2026-10-24 用户 Jason 签字）已覆盖**整张 `report_record` 表** ⇒ ⇒ **本项是「决策」不是「缺陷」**
>   ⇒ ⇒ 与 AGENTS.md §0 的共性完全一致（「RAG 做了更严/更安全的那一侧」⇒ 对拍看到它变红**先确认是不是签字项**）。
> · **处置**：**不删 V5**（删了 = 让 `ReportComment` 在全新部署上必然 5004 ⇒ 制造一个 Go 侧才有的部署缺口）
>   ⇒ **保留现状**；基线的归类订正属**共享面**（`docs/PhaseGoal-judge.md` 是 4 条 lane 矩阵的**唯一数据源**）
>   ⇒ ⇒ **本工作树不碰** ⇒ 记在这里，留给**合并收口执行者**订正。

### 4.5.1 ⚠️ **2026-10-30 真实性复核（W-D lane）**：补跑口径 **23 处**的**逐条出处** ＋ 🔴 **根因**：Go 的「生产 DDL」根本不是那些手写 `.sql`

> **性质**：上面 §4.5 的表 + 上面那段 📌 只给了**计数**；本节补齐**逐条出处**（表名 + 列名 + 两侧取值 + `文件:行号`），并给出**根因**。
> ⚠️ **本节的数字全部来自本 lane 的实跑**（命令原文见上面 2026-10-30 二次订正段）⇒ 与 `docs/DRIFT/comment/vs-common.md` §9.2 / §9.4 **互为复算**（两处数字一致）。

**逐维度逐条（机器原文 + 两侧 `文件:行号` 出处）**

| # | 维度 | 机器报出 | 逐条（表 · 列 ⇒ 两侧取值） | Go 侧出处 | RAG 侧出处 |
|---|---|---|---|---|---|
| 1 | 列类型 / 长度 | **0 处** | 机器原文「`go_comment_db=52 列 rag_comment_db_cmp6=52 列（键集合一致 ✓）`」+「**逐字相同 52 列**」 | `service/comment/rpc/internal/model/data_model.go:5,22,42,87`（4 个 struct 定义行） | `service/comment/comment-rpc/src/main/resources/db/migration/V1..V8` |
| 2 | **可空性（NOT NULL）** | 🔴 **8 处** | `subject.created_at` · `subject.updated_at` · `comment_index.created_at` · `comment_index.updated_at` · `comment_content.created_at` · `comment_content.updated_at` · `comment_like.created_at` · `comment_like.updated_at` —— **8 条同一形态**：`go=(nullable=YES)` vs `rag=(nullable=NO)` | `data_model.go:14-15`（Subject）· `:34-35`（CommentIndex）· `:47-48`（CommentContent）· `:95-96`（CommentLike）—— tag **只有 `autoCreateTime`/`autoUpdateTime`，无 `not null`** ⇒ 落 NULL | `V1:27,28` · `V2:22,23` · `V3:18,19` · `V4:24,25` —— 全部 `NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| 3 | **DEFAULT** | 🔴 **10 处**（**2 处经人工闭合为工具方法假阳性** ⇒ 实质 **8**） | **8 处** = 上面同样 8 列：`go=<NULL>` vs `rag=CURRENT_TIMESTAMP`；<br>**2 处假阳性** = `subject.id`（`go=nextval('subject_id_seq'::regclass)` vs `rag=<NULL>`）· `comment_like.id`（`go=nextval('comment_like_id_seq'::regclass)` vs `rag=<NULL>`） | `data_model.go:6`（`Subject.Id` = `primaryKey;autoIncrement`）· `data_model.go:88`（`CommentLike.Id`）⇒ gorm 建 `bigserial` ⇒ `column_default` = `nextval(...)` | `V1:19` · `V4:17` —— `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY` ⇒ **IDENTITY 的默认值存在 `pg_attribute.attidentity`，不进 `column_default`** ⇒ 工具的 DEFAULT 维度**看不见 IDENTITY 列** ⇒ 报成差异 |
| 4 | **索引**（`pg_indexes`） | 🔴 **2 处** | `comment_domain_fact_outbox.idx_comment_fact_delivery`（`cols=['delivery_status','created_at','event_id']`）· `comment_domain_fact_outbox.idx_comment_legacy_fact_aggregate`（表达式索引 `((payload`）—— **均仅 RAG 有**；机器原文「**Go 11 个 RAG 13 个**」 | **Go 生产 schema 无**（`AutoMigrate` 不建）⇒ ⚠️ Go 的**手写** `003_comment_fact_delivery.sql:12` / `002_comment_dc_wire.sql:15` **有** —— 但那两份**只被测试脚本执行**（见下「根因」）⇒ 不在生产 DDL | `V8:47-48` · `V6:42-44` |
| 5 | **约束**（`pg_constraint`） | 🔴 **2 处** | `comment_domain_fact_outbox.comment_new_fact_has_dc_wire`（`CHECK (((aggregate_id IS NOT NULL) AND (fact_version IS NOT NULL) AND (delivery_envelope IS NOT NULL))) NOT VALID`）· `comment_fact_stream.comment_fact_stream_version_range`（`CHECK (((version >= 1) AND (version <= '9007199254740991'::bigint)))`）—— **均仅 RAG 有**；机器原文「**两侧各 6/9 行**」+「**逐条一致 6 行 ✓**」 | **Go 生产 schema 无** ⇒ ⚠️ Go 的**手写** `002_comment_dc_wire.sql:25` / `:10` **有** —— 同样**只被测试脚本执行** | `V6:63` · `V7:31` |
| 6 | **触发器**（`pg_trigger`） | 🔴 **1 处** | `comment_domain_fact_outbox.comment_fact_identity_immutable`（`CREATE TRIGGER … BEFORE UPDATE ON public.comment_domain_fact_outbox FOR EACH ROW EXECUTE FUNCTION reject_comment_fact_identity_change()`）—— **仅 RAG 有**；机器原文「**两侧各 0/1 个**」 | **Go 生产 schema 无** ⇒ ⚠️ Go 的**手写** `003_comment_fact_delivery.sql:15,30` **有** —— 同样**只被测试脚本执行** | `V8:66`（函数体）· `V8:82`（触发器） |
| | **合计** | **23** | **8 + 10 + 2 + 2 + 1** ⇒ 扣掉第 3 项 2 处假阳性 ⇒ **实质 21** | | |

**🔴 根因（本节最重要的一条）：Go 的「生产 DDL」根本不是那些手写 `.sql`**

- Go 的生产建表 = `service/comment/rpc/internal/model/db.go:15` 的 **GORM `AutoMigrate`**（**6 个 struct**）⇒ **不建**上面第 4/5/6 项那 5 个对象。
- Go 侧**确实**有手写 `.sql`（`service/comment/rpc/internal/model/migrations/001|002|003_*.sql`）⇒ **RAG 的 `V6`/`V7`/`V8` 是逐字照抄它们的**
  ⇒ ⇒ **§4.5 表里那几行「✅ 逐字一致」**（`idx_comment_legacy_fact_aggregate` / `comment_new_fact_has_dc_wire` / 信封不可变触发器 / `comment_fact_stream` 的 version 范围 CHECK）
  = 「**RAG 与 Go 的手写脚本逐字一致**」 —— 而**不是**「RAG 与 Go 的**生产 schema** 一致」⇒ ⇒ **这四行的参照系需要显式改写**。
- ⚠️ **那三份 `.sql` 在 Go 仓里只被测试脚本执行**（`service/comment/rpc/internal/model/test-community-postgres.sh:47,49`）⇒ ⇒ **不进 Go 的生产 DDL** ⇒ ⇒ **「对拍时 Go 侧看不到这些对象」不是环境缺口，是事实**。
- ⇒ ⇒ **第 4/5/6 项那 5 处**（索引 2 + CHECK 2 + 触发器 1）= **RAG 比 Go 的生产 schema 多出来的对象** ⇒ 逐条登记，**本轮不改代码**。

**⚠️ 人工复核后必须显式标注的 4 处（机器判定 ≠ 真实语义差）**

| 对象 | 机器判定 | 人工复核 | 结论 |
|---|---|---|---|
| `comment_like.idx_user_comment_like` | **语义等价（仅命名形态差，机器不计失败）** | Go = **唯一约束**（`contype=u`）· RAG = **唯一索引** ⇒ 都保证 `(user_id, comment_id)` 唯一 | ✅ **真等价** ⇒ 不计 |
| `subject.id` / `comment_like.id` 的 DEFAULT | 机器报成 **2 处差异** | RAG 侧 `information_schema` 实测 `is_identity=YES` · `identity_generation=BY DEFAULT` · `column_default` 为空 ⇒ **IDENTITY 的默认值不进 `column_default`** ⇒ 工具的 DEFAULT 维度**看不见 IDENTITY 列** | ✅ **非真差**（两侧「主键自动生成」语义完全一致）⇒ **机器 23 ⇒ 实质 21** |
| `comment_fact_stream.comment_fact_stream_version_range` | 机器报成 **实质差异（仅 RAG 有）** | Go 在**应用层**强制同一条区间：`service/comment/rpc/internal/model/domain_fact.go:121` `if stream.Version < 1 \|\| stream.Version > 9007199254740991 { return fmt.Errorf("comment fact version out of DC range") }` | 🟡 **载体不同、语义等价**（Go = 应用层 `if` · RAG = DB CHECK）⇒ **不改代码**；登记为「RAG 侧多一道 DB 层防线（更严）」 |
| `comment_new_fact_has_dc_wire` | 机器报成 **实质差异（仅 RAG 有）** | Go 侧**生产 schema 无此 CHECK**；Go 应用层靠 `service/comment/rpc/internal/model/fact_delivery.go:65-68` 的 SQL 条件实现取件路径，**没有**等价的 DB 约束 | 🔴 **RAG 侧多一道 DB 层约束（更严）** ⇒ 属 `SD-2026-10-24-CAND-09`（DDL 更严）同族 ⇒ **不删**（删了 = 撤掉一道已签字的防线） |

**⚠️「索引 Go 16 ≡ RAG 16」这个数字在本域任何口径下都不成立**

- 实测：**Go 11 / RAG 13**（补跑口径 6 表）· **Go 11 / RAG 15**（RAG 侧含 `report_record` 的 7 表活库）⇒ ⇒ **「16」查无出处**。
- ⚠️ **疑似串了 `article` 域的数字** —— `check-ddl-static.py --domain article --indexes` 的机器原话正是「**Go 16 个 RAG 16 个**」（见 `docs/DRIFT/article/vs-common.md` §10）⇒ ⇒ **「同一次运行里」那句话整体作废**。
- ⇒ ⇒ 上面那段 📌 的「**索引 Go 16 ≡ RAG 16**（2 处仅命名形态差）」**两个数字都不对**：真实是 **11 vs 13**，且**不是**「仅命名形态差」，而是 **2 处仅 RAG 有**。

**⇒ 结论**

1. 🔴 **本域 DDL 不是「除 `report_record` 外两侧一致」** —— 补跑口径实测 **21 处实质差异**（可空性 8 · DEFAULT 8 · 索引 2 · CHECK 2 · 触发器 1）。
2. 🔴 其中 **16 处**（可空性 8 + DEFAULT 8）= **同族**（`SD-2026-10-24-CAND-01`，**未签字候选**）—— Go 侧时间列**可空无 DB 默认**、RAG 侧 `NOT NULL DEFAULT CURRENT_TIMESTAMP` ⇒ **RAG 更严** ⇒ 按 `docs/twoStage.md` §十 属 **C 类（阶段 2）**，**不是阶段 1 准入阻断**。
3. 🟡 其余 **5 处**（索引 2 + CHECK 2 + 触发器 1）= **RAG 比 Go 的生产 schema 多出来的对象** ⇒ 根因见上 ⇒ **逐条登记，不在本轮改代码**。
4. ✅ **类型 / 长度 0 差异**（52/52 逐字相同）—— 与 AGENTS.md §0 的总体结论一致，**不变**。
5. ⛔ **`report_record` 整张表仍不计入差异**（已签字 `SD-2026-10-24-CAND-02` · 决策 = 保留 RAG 侧该表）⇒ ⇒ **不得据此删 `V5`**。

**⇒ 转交主线的 1 项**：`docs/DRIFT/_cross/ddl-type-divergences.md:58` 写「**comment 的 8 处当前无法用 `check-ddl-static.py --domain comment` 复算**」⇒ ⇒ **口径需按本节订正** —— **数字 22 = users 8 + message 6 + comment 8 不变**；但「不可复算」应改为「**默认口径不可复算（提前中止）· 补跑口径可复算（`--rag-db` 覆盖）**」（补跑口径见本节与 `vs-common.md` §9.0）⇒ ⇒ **该档是共享面（主线写权）⇒ 本 lane 不碰**。

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

### 4.6.1-bis 📌 **本节已「交出」给 G1 lane**（2026-10-28）—— 交的是**隔离配方**，不是只有结果

**为什么交**：G1 lane 的 `DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013` 两条要升 **L4**，按其口径「**没有两侧同时跑的探针就不写 L4**」
⇒ ⇒ 它需要的是**「怎么把两侧同时跑起来」的配方** ⇒ ⇒ 本节 §4.6.1 的隔离表正是那份配方。

**交出的内容（要点，供 G1 复用）**：
| 隔离维度 | 做法 | 不这么做会怎样 |
|---|---|---|
| 端口 | Go 侧 `19001` / `18888`、RAG 侧 `59001` / `58888` | 端口冲突 ⇒ 起不来 |
| 数据库 | 同一 PG 容器、**两个库**（`go_comment` / `rag_comment`）| 共库 ⇒ 数据互相污染 ⇒ 假差异 |
| Kafka | **两套 topic + 两套消费组**（`-go` / `-rag` 后缀）| 共用 topic ⇒ **互相消费对方的事实** ⇒ 全是假差异 |
| Redis | **两个 db**（Go `db0` / RAG `db1`）| 两侧 key 格式**逐字一致** ⇒ 不分库必然串味 |
| 下游 RPC | 两侧**同一个死端点** `127.0.0.1:19999` | 指向不同端点 ⇒ 超时路径不可比 ⇒ 假差异 |
| 服务发现 | RAG 侧关 Nacos 注册（`SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false`）；Go 侧 yaml 里**整块删掉 etcd** | 本机无 Nacos / etcd ⇒ 启动卡住 |
| 消息位点 | kafka-go 的 `Offset: last` 会**跳过消息** ⇒ harness 改 `Offset: first` ＋ **每轮删 topic** | 消费组无成员时消息被跳过 ⇒ 测了个空 |

⚠️ **一条纪律**：这 7 条里**任何一条**没做到 ⇒ ⇒ **差异里会混进「串味」** ⇒ ⇒ 得到的不是「两侧差异」而是「两侧环境差异」⇒ ⇒ **对拍结论作废**。

**🔴 断连活体对拍要「约窗口」吗？—— 本域**不需要**（2026-10-28 澄清）**
- 本域的断连对拍（[`hot/rpc.md` §十二.9 规格](../hot/rpc.md) 与本文档 §十二）用的是**每组各自起一个 `redis:7-alpine` 的一次性容器**（`docker rm -f` 收尾）⇒ ⇒ **不碰 `difftest5k-redis`** ⇒ ⇒ **不动 G1 域 4 个进程在用的共享容器** ⇒ ⇒ **无需约窗口**。
- ⇒ 只有当探针**改用**共享容器时，才需要「谁断 / 多久 / 谁恢复」的约定 ⇒ ⇒ **本域的配方里没有这一条**。

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
| **Bearer 前缀长度边界**（`"Bearer "` 恰好 7 字符 ⇒ 两侧都 401） | ✅ **T22** 一致 ⇒ 证实「`D2@comment-api` 当前不可观察」（⚠️ **2026-10-29 加组件前缀**：本文件 §3 的 `D2` 是**时间精度**，与本行引用的 api 侧 `D2`（Bearer 前缀长度边界）**不是同一条**） |
| **`iat` 在未来 ⇒ 401** | ✅ **T23** 一致 ⇒ **D4 的修复被活体确认**（不只是单测绿） |
| **非 JSON Content-Type ⇒ 两侧都 200 + `{code:5001}`** | ✅ **T21** 一致 ⇒ **`D1@comment-api` 撤销**（详见 [`api.md` §3.3](./api.md)）<br>⚠️ **2026-10-29 加组件前缀**：本文件 §3 的 `D1` 是 **`comment_domain_fact_outbox.created_at` 的取值来源**（§3.1 · **已撤销**），与本行引用的 api 侧 `D1` **不是同一条** |

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
| **RPC 超时的错误文案不同** | Go `service/common/observability/rpc.go:69` ⇒ `context deadline exceeded`；RAG `service/common/observability/RpcInterceptor.java:187` ⇒ `rpc timeout after 2000ms`。**两侧 code 都是 4（DeadlineExceeded）** | 🔧 **已由 global 域修复（第三十轮，global 持 `service/common` 单写权）** ⇒ 落点 `RpcInterceptor.java` + 守门 `RpcInterceptorTimeoutDescriptionGateTest`（2 例，真 Netty server + 真 channel，断言**网络上真正传过来的字节**）+ 探针 `tools/semantic-probe/rpc-timeout/`（Go 半边直接 `replace` 引用冻结真源）⇒ ✅ **已提交并推双远端**（`49808ad`；分支最新 `85c4588`，`origin` + `upstream` 均已同步），**待合回 `do-my-own-gh`**；📌 **合回与否已拍板（2026-10-26）：暂不合回 ⇒ 维持「待合回」不是「忘了合」** ⇒ 销账细节与 **C-1 边界**见 §5 的 **U10** |
| `comment-api` 缺 gRPC 熔断 | —— | ✅ **已作废**（`docs/DRIFT/_cross/new-domains-2026-10-23.md:43-45`）：实测 RAG 侧熔断**已存在** |
| **（只读指针 · 归属 global）业务层把「SQL 被 deadline 取消」改写/吞掉** —— global 台账 **E47**（`docs/DRIFT/global/_README.md`，**按编号指**）<br>🔴🔴 **第三十六轮已全面订正：原定性方向相反** —— 原记述「RAG 把 DEADLINE_EXCEEDED 改写/吞掉」**只对了数目、错了定性** ⇒ **comment / like / follow / article 四个域的 Go 侧同样改写** ⇒ **那 21 处与 Go 一致 ⇒ 销案** ⇒ ⛔ **comment 域那 10 处 catch 不要改**。 | ⛔ **本域不重复登记**（落点在 `service/common` ⇒ **global 单写权**）。<br>🟢 **本域已逐条核过 Go 侧（2026-10-26，comment owner 独立复核）** —— Go 侧**每一条 DB 错误路径都改写成业务码**，**不区分 deadline**：<br>· `report_comment_logic.go:79-92` ⇒ `errmsg.NewGrpcErr(errmsg.ErrorDbInsert, "DB插入失败")`<br>· `delete_comment_logic.go:76-77` ⇒ `errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")`<br>· `update_subject_state_logic.go:79-80` ⇒ `errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")`<br>· `like_comment_logic.go:62-63` ⇒ `errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "DB更新失败")`<br>⇒ RAG 侧对应 10 处（`CommentDomainService.java:161/434/447/474/523/541/590/651/690/710`）**全部是** `catch (RuntimeException e) { result = "sys_fail"; … throw CommentExceptions.dbXxx(); }` ⇒ **与 Go 的 catch-all 逐字同款** ⇒ **一致，不是漂移** ⇒ ✅ **销案**。<br>⚠️ **行号已核（2026-10-26）**：上述 10 行号与当前文件**逐一对上**；⚠️ **若将来 `CommentDomainService` 结构变动，先重核行号再引用**。<br>⚠️⚠️ **2026-10-29 真实性复核（W-D lane）**：原判 **✅ 销案** ⇒ **现行态 = ✅ 销案（结论不变）**，但**上句的行号与计数已过期** —— `CommentDomainService.java`（现 **883 行**）已重构，**原文那 10 个行号（161/434/447/474/523/541/590/651/690/710）已不再指向 catch 块** ⇒ **本行自己的预警（「若结构变动，先重核行号」）已触发**。现值：**9 处** `catch (RuntimeException e) { result = "sys_fail"; … throw CommentExceptions.dbXxx(); }` = **`CommentDomainService.java:180,490,503,530,605,674,754,810,830`**（另有 `:587` = `throw CommentExceptions.likeOwnerLookupFailed()`，同为业务码改写但**非 `db*` 码** ⇒ 未计入该 9 处）⇒ **Go 侧 4 处锚点已逐条重核、逐字对上**：`service/comment/rpc/internal/logic/report_comment_logic.go:79-92` · `delete_comment_logic.go:76-77` · `update_subject_state_logic.go:79-80` · `like_comment_logic.go:62-63`（`ErrorDbInsert=5004` / `ErrorInputWrong=2001`，见 `service/like/common/errmsg/errmsg.go:44,64`）⇒ ⇒ **两侧「DB 错误一律改写成业务码」的形态判定不变**（**销案成立**），**代码零改动**。核对日期 2026-10-29。<br>💡 **同域教训**（global 侧 `PIT-GLOBAL-051`）：**登记「某域把异常改写了」之前必须逐条核 Go 侧** ⇒ 否则会让**本来正确的域去改本来正确的代码** ⇒ **本条已销案，代码零改动**。 |
| **（只读指针 · 归属 global）超时未继承客户端 deadline** —— global 台账 **E38**（`docs/DRIFT/global/_README.md`，**按编号指，不按行号**）<br>Go `rpc.go:38` `context.WithTimeout(ctx, timeout)` ⇒ `ctx` **携带客户端 deadline** ⇒ 有效期限 = `min(客户端 deadline, now+2s)`；RAG `RpcInterceptor.java:214-215` `deadlineEpochMs = now + timeout` ⇒ **只算服务端自己的 2s** | ⛔ **本域不重复登记**（落点在 `service/common` ⇒ **global 单写权**）。<br>🟢 **E38 第①层已由 global 落地**（`align/global` @ `d531abd`，2026-10-26）⇒ 有效期限已改成 `min(客户端 deadline, 服务端 2s)`。<br>🔴 **E38② 仍开着（global 第三十九轮回执，2026-10-26）**：架构级「**handler 挪受控 executor + 超时 interrupt**」**未做** ⇒ ⇒ **只做 ①（deadline 计算）销不了账** ⇒ ⚠️ 本行「E38 第①层已落地」**不等于**「E38 整条闭环」⇒ 与 users/admin 域 owner 的复述一致 ⇒ **本域仍只做只读指针**。<br>🔴 **残余窗口 = global 的 E46**（`docs/DRIFT/global/_README.md`，同样按编号指）—— 🟢 **E46 已由 global 落地**（`d20b377` 第三十三轮：SQL 取消时刻 == deadline 时刻 · **毫秒精度** + **已过期立即失败**）⇒ 该窗口已被压掉。<br>🟢 **状态（2026-10-26 五次复核）**：`align/global` HEAD = **`85c4588`**（第三十九轮 · **合回与否已拍板：暂不合回**），**origin + upstream 两个远端均已同步** ⇒ E38 / E46 / E47 / E30 **任何人拉分支都能看到** ⇒ 指针不会指向拉不到的编号。📌 **合回与否已拍板（2026-10-26）：暂不合回 ⇒ 维持「待合回」不是「忘了合」**。<br>—— ⚠️ **本条原写的机制归因已订正**（2026-10-26 global 复核）：RAG **不是**「PG 查询跑满到 2s」⇒ 它**有** SQL 级缓解：`service/common/.../persistence/DeadlineQueryTimeoutInjector`（拦 `StatementHandler.prepare` ⇒ 按线程 `DeadlineState.remainingMillis()` 设 `Statement.setQueryTimeout`）⇒ pgjdbc 到点发 **CancelRequest** ⇒ **查询在服务端被掐掉**；**comment-rpc 已注册**（`CommentRpcApplication.java:67`）⇒ 10 个 rpc 组件全注册。<br>⇒ **真正的差异是「秒级向上取整」留下的 ≤1s 窗口**：`DeadlineQueryTimeoutInjector.java:93-95` 用 `Math.ceil(remaining/1000.0)` **秒** ⇒ 取消时刻 = `deadline + (0~999ms)`；Go 侧 `m.conn.WithContext(ctx)` 贯穿 model 层**每一次查询/事务** ⇒ ctx 到点**立即** CancelRequest ⇒ 窗口 = **毫秒级** ⇒ **两侧差 2~3 个数量级**。<br>🟡 **对 comment 域的精确落法（条件性，不是必现）**：`comment-api` 的 per-call deadline = `min(2s, HTTP 剩余预算)`（MISMATCH-API-03 的修法）⇒ **当 HTTP 剩余预算 < 2s 时**，客户端先放弃而服务端业务线程继续跑 ⇒ 若查询耗时**恰好落在 `(remaining, ceil(remaining/1000)×1000]` 这条 ≤1s 带子里**，则 SQL 自然完成 ⇒ **事务提交** ⇒ 写 gRPC 响应时 call 已 close ⇒ **响应被丢弃** ⇒ ⇒ 两侧对「这次写入算不算成功」的答案相反（**概率性命中，不是必现**）。<br>⚠️ **现有守卫不覆盖**：`checkDeadline()` 只在**方法入口**（admin-rpc 13 处），**不在提交前**。<br>⇒ **跟踪请走 global 的 E38 / E46**，本条只做指针；**本域不重复登记** |

---

## 5. 未决 / 待办

| # | 事项 | 类型 |
|---|---|---|
| **U1** | ~~MISMATCH-01/02 需真 PG 守门测试~~ ⇒ ✅ **2026-10-24 已完成**：`CommentRpcGoSemanticsGateTest`（11 例，真 PG + 真 MyBatis + 真事务），**并做了 10 条变异验证** | ✅ 已闭环 <br>⚠️ **2026-10-29 真实性复核（W-D lane）**：原判 **✅ 已闭环** ⇒ **现行态 = ✅ 已闭环**（判定不变）。依据：`CommentRpcGoSemanticsGateTest.java` **现有 14 个 `@Test`**（原记「11 例」= **陈旧计数**）· `CommentFactDeliveryService.java:11,115`（`TransactionTemplate` 仍在 ⇒ MISMATCH-01/02 的修法仍在）· 核对日期 2026-10-29。 |
| **U2** | ~~MISMATCH-03/04/05/06 一次改动可解 4 条~~ ⇒ ✅ **2026-10-24 已完成**（改动落在 `CommentFactDeliveryService` / `CommentDomainFactService` / `CommentDeliveryValidator` 三处 + 两个 mapper） | ✅ 已闭环 |
| **U3** | ~~本组件无任何投递路径测试~~ ⇒ ✅ **2026-10-24 已补**：`dispatchOnce` 的三条出口（blocked / failed / accepted）均有真 PG 守门网 | ✅ 已闭环 |
| **U4** | Go 侧三个 `_test.go`（`domain_fact_test.go` / `fact_process_fixture_test.go` / `fact_delivery_logging_test.go`）**默认 `t.Skip`** ⇒ Go 侧投递链路的**期望行为在 Go 仓里本身也没有活体证据** | ⚠️ 真源侧缺口 |
| **U5** | ~~`comment_fact_stream` 的 CHECK 在 Go 侧是否常态存在取决于迁移执行顺序~~ | 🟢 **已查实**（2026-10-24）：Go 那三份手写迁移 `001/002/003` **只被测试脚本** `internal/model/test-community-postgres.sh:47,49` 执行 ⇒ **生产路径只有 `db.go:15` 的 `AutoMigrate`** ⇒ **生产的 `comment_fact_stream` 没有 version CHECK** ⇒ **RAG 侧 V7 多一个 CHECK** ⇒ 登记为 **`SD-2026-10-24-CAND-09`**（待签字）。⚠️ **不可观察**（`version` 从 1 递增，要到 2^53-1 才触发） |
| **U6** | ~~`comment_like` 的 Go 侧有两份互相冲突的 DDL 产物~~ | 🟢 **已查实**（2026-10-24）：**生产权威 = `AutoMigrate`**（手写 `001` 只在测试脚本跑）⇒ 两份产物的差异只剩**形态等价**项：`id`（`bigserial` ↔ `IDENTITY`）、唯一性（**唯一索引** ↔ **表约束**）⇒ **无行为差异**。🟢 **活体佐证**：两个活库的 `pg_constraint` / `pg_indexes` 差分里，`idx_user_comment_like` 确实是一边索引、一边约束 ⇒ **语义等价** |
| **U7** | ~~MISMATCH-07（`updated_at` INSERT 取值）—— 未找到任何读取方~~ ⇒ ✅ **2026-10-24 已修**：按 GORM `create.go:340-351` 的实测语义改成入库时刻；**仍无读取方** ⇒ 影响面窄但值已对齐 | ✅ 已修（影响面窄） |
| **U8** | ✅ **`SD-2026-10-24-CAND-06` 已签字**（2026-10-24，用户 Jason）：Go 的 `attribute & ~?` 在 PG 上**必然报错**（`~ unknown`）⇒ `MANAGE_UNPIN` / `MANAGE_UNFEATURE` **100% 失败**；RAG 侧实现 Go 的*意图*（有类型的 mask）。**签字含义覆盖两处**（manage 清位 + `comment.go:571` 点赞清位）⇒ RAG 侧两处**均已正确、无需改代码** | ✅ 已闭环 |
| **U9** | ✅ **`SD-2026-10-24-CAND-02` 已签字**（2026-10-24，用户 Jason）：`report_record` 表 Go 侧**不存在** ⇒ Go 的 `ReportComment` **必然 5004**；RAG 侧建了表 ⇒ 可用。**由活体对拍实证**（Go=5004 / RAG=200） | ✅ 已闭环 |
| **U10** | 🔧 **修复已由 global 域实施，销账待其合回** —— `service/common` 的 RPC 超时 **description** 两侧不同。<br>**修前状态（保留，不抹掉）**：Go `service/common/observability/rpc.go:68-71` `status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ description = **`context deadline exceeded`**；RAG 修前 `RpcInterceptor.java:196/204` = **`rpc timeout after 2000ms`** ⇒ **两侧 code 相同（4 / `DEADLINE_EXCEEDED`），description 不同**。<br>**三域独立记录**（互相印证，非孤证）：本条 U10 · `docs/DRIFT/users/user/rpc.md` **V-007**（§7 详述 + CASE-31，标注「跨组件 `service/common`」）· `docs/DRIFT/users/admin/rpc.md` §1.3 档位 B 活体对照行（Go `context deadline exceeded` vs RAG `rpc timeout after 2000ms`）⇒ 8 个 rpc 模块的 `rpc-timeout: 2s` ⇒ **该路径线上真实生效**，不是纸面差异。<br>**✅ 修法**（global 持 `service/common` 单写权，第三十轮）：落点 `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` ⇒ 新增常量 `GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded"`，并把**客户端真正收到的** status description 与**遥测 `sea.biz_msg`** 一并换成 Go 的逐字串（`goStyleStatusMessage(...)`）。<br>**🧪 守门**：`service/common/src/test/java/com/rag/common/observability/RpcInterceptorTimeoutDescriptionGateTest`（2 例：真 Netty server + 真 channel + 真客户端 + 真 OTel SDK/InMemorySpanExporter ⇒ 断言的是**网络上真正传过来的那串字节**）。<br>**🔬 探针**：`tools/semantic-probe/rpc-timeout/`（Go 半边**直接 `replace` 引用冻结仓真源**（不改写冻结仓一字节）+ 真 gRPC server；RAG 半边跑**生产类** ⇒ `timeout_wire` 两侧逐字节一致）。<br>**🧬 变异**：M1 改回旧串 ⇒ 守门 2/2 红 + 探针 `EXIT=1`；M2 只改遥测段 ⇒ 只红遥测那条、wire 那条仍绿。<br>⚠️ **状态（2026-10-26 五次复核 · 按 global 第三十九轮 `85c4588` 的拍板结果统一措辞）**：该修复**已提交并推双远端**（主改动 `49808ad`；分支最新 **`85c4588`**，`origin` + `upstream` **两个远端都已推到该提交** ⇒ **不会再丢**，AGENTS.md §5.3「共享文件的改动要尽早提交」），**待合回 `do-my-own-gh`**；📌 **合回与否已拍板（2026-10-26）：暂不合回 ⇒ 维持「待合回」不是「忘了合」** ⇒ 本条**保持「销账待其合回」**，待合回落地后再改成「已销账」（本域**不代销**）。<br>🔴 **必须记住的陷阱（global 第三十七 / 三十九轮实测）**：**在合回之前，主仓代码仍是旧串**（`RpcInterceptor` 的超时 description 未生效）⇒ ⇒ **任何基于主仓的验证都看不到 E30 的修复** ⇒ ⇒ 跑门禁 / 对拍时**别把「主仓还是旧串」误读成「修复没做对」**；同理，守门类 `RpcInterceptorTimeoutDescriptionGateTest` **只在 `align/global` 上、主干上没有** ⇒ 若第 1 层报「守门类缺失」，那是**分支差异**，**不是**「守门没写」。<br>⚠️ **C-1 边界（不得越界）**：修完后剩余的 **code 名**表示差异（Go `codes.Code.String()` = CamelCase `DeadlineExceeded` vs grpc-java `Code.name()` = `DEADLINE_EXCEEDED`）已登记为 `docs/Intentional_Deviations/common/_signed-deviations.md` **C 类 C-1**（**不做臆造映射**）⇒ **本轮不动**；守门**刻意把 C-1 的形态钉住**（断言 `code = DEADLINE_EXCEEDED`）⇒ **请勿**为了让 code 名也一致而去改 grpc-java 的 code 命名 | 🔧 销账待 global 合回 |
| **U11** | ⚠️ **Go 侧潜伏缺陷**：`comment.go:571`（点赞的 `attribute & ~?`）与 `comment.go:165` 同款 ⇒ **Go 侧「作者取消自己的赞」也会 5002**。本轮矩阵 `user 1001 ≠ owner 2002` ⇒ 未触发 ⇒ 两侧一致。⇒ **Go 侧缺陷**，RAG 侧已正确 | ⚠️ 真源侧缺陷（记录，不修 RAG） |
| **U12** | ✅ **跨栈 gRPC 路径前缀**（未知项 #5）：Go `package pb;` ⇒ `/pb.CommentService/*`，RAG `package comment;` ⇒ `/comment.CommentService/*`。**新增 `PbCommentServiceAlias`**（`@GrpcService` + `BindableService`，用 `MethodDescriptor.toBuilder().setFullMethodName()` 改名 + 复用 `getServerCallHandler()`）⇒ **只增不改**（`comment.` 路径原样保留）⇒ **不推翻**已签字的 `SD-2026-10-23-04`。守门网 `PbCommentServiceAliasGateTest` 6 例（真 in-process server，两条路径都通、同一实现、方法集合对应、未注册方法仍 UNIMPLEMENTED） | ✅ 已闭环（**半修**，见 U13） |
| **U13** | 🔴 **U12 的伴生阻塞（不可修）**：`io.grpc.Status` **只有 17 个常量** ⇒ grpc-java **发不出** `grpc-status: 1012` ⇒ RAG 的业务码在**自定义 trailer** 里 ⇒ **Go 侧读不到** ⇒ 跨栈错误码必然退化成 `UNKNOWN(2)`。⇒ **结论：混跑必须同栈配对，别名只是兜底** | 🔴 跨栈限制（**协议层**，非本域可修） |
| **U14** | ~~T20 保护层覆盖范围差异~~ | ✅ **已闭环**（2026-10-24 活体实测 T20 ⇒ 登记为 **`D6@comment-api`** ⇒ **用户已签字** `SD-2026-10-24-CAND-08`：**接受 RAG 更严/更安全的一侧** ⇒ **代码零改动** ⇒ 共享面 `service/common/protection` **不动**） |
| **U15** | ✅ **D8（= global E65）出向 gRPC 调用继承请求 deadline**（**2026-10-31 第十一轮闭合**）—— 当时的判据是「`ArticleRpcClient.java:65` 是裸 stub ⇒ 零期限」⇒ **该判据已过期**（继承由共享层载体 + 共享层拦截器给、2s 封顶由域侧 `cap-millis=2000` 给）⇒ ⇒ 2026-10-26 的独立核实与 2026-10-29 的复核结论**均为历史快照** ⇒<br>⚠️ **2026-10-29 真实性复核（W-D lane · 历史快照）**：原判 **🔴 仍开着** ⇒ 当时的现行态 = 🔴 仍开着（只开「继承」这一半） ⇒ ①「**2s 封顶**」**已闭合**（**2026-10-31 第十一轮：域侧副本已删除 ⇒ 改由共享层 `GoStyleOutboundDeadlineInterceptor` ＋ 域侧 `application.yml` 的 `rag.protection.outbound-deadline.cap-millis=2000` 施加** ⇒ **不再是零期限**）· ②「**继承服务端 deadline**」**仍开着**（`GoStyleRequestDeadline` 仍只在 HTTP worker `begin()` ⇒ 纯 gRPC 下无预算 ⇒ 实际是平铺 2s）⇒ ⇒ **本行「`:65` 是裸 stub ⇒ 零期限」的证据已过期**，**按 D8 的两格拆读**（见 §3.5 下的 2026-10-29 订正段）⇒ **核心主张不变 ⇒ 不升级整条为 ✅**。核对日期 2026-10-29。⇒ 详见 **§3.5**。<br>⚠️ **完整闭环跨共享层**：`GoStyleRequestDeadline.begin()` 只在 HTTP 路径（`HttpMiddleware.java:263`）绑定 ⇒ gRPC 服务端路径上 `applyTo` 是 **no-op** ⇒ **共享层那半归 global**（`service/common` 单写权）⇒ **本域那半待修法拍板后落地**。<br>💡 **本域那半其实不必等共享层**：`DeadlineState` 在 gRPC 路径**已绑定**（`RpcInterceptor.java:281/372`）⇒ 可用 `DeadlineState.remainingMillis()` 直接闭环（同仓先例：`users/admin/AdminDomainService.java:140`）⇒ **两条路各有代价（会让 global 的门禁 `check-deadline-downstream.py` 变红）** ⇒ **待 Jason 拍板，本域不擅自选边**。<br>⚠️ **本域现在不写守门网**：`applyTo` 在共享层落地前是 no-op ⇒ 守门网**测不出任何东西** ⇒ 属 §3.3「绿测试可能是错误答案的守卫」⇒ **不写假绿网** | ✅ **已一致（2026-10-31 第十一轮 · G3）** —— ①「2s 封顶」：域侧副本已删 ⇒ 共享层 `GoStyleOutboundDeadlineInterceptor` ＋ 域侧 `cap-millis=2000` 施加；②「继承请求 deadline」：载体在 **gRPC handler 线程**可见（`RpcInterceptor.onHalfClose` 绑定）⇒ 共享层拦截器算 `min(入向剩余, 2s)` ⇒ 两侧同形（Kafka consumer 那条出口两侧同样都无 ctx deadline ⇒ 同形）；③ **L4 活体证据**：域内守门 `CommentOutboundDeadlineCarrierGateTest`（**7/7 绿**：入向长于 2s ⇒ 压到 2s ／ 入向短于 2s ⇒ 继承入向剩余 ／ 纳秒精度 ／ 无载体仍 2s ／ 配置面 `cap-millis=2000` ／ 无域内副本） |
| **U16** | 🔴→✅ **D9（= global E66）Redis 调用不感知 deadline** —— **已销账（2026-10-31）**：Go 侧 `service/comment/rpc/internal/cache/*.go` **全部**带 `ctx`（`rdb.Get/Set/Scan`）⇒ deadline 到点缓存操作立即中止；RAG 侧 `CommentCacheService.java` 的 `redis.opsForValue()…`（**含写**：`:279`/`:331`/`:433`/`:528` 的 `set`、`:619` 的 `delete`）**零改动**即被共享层的 Redis 期限载体覆盖 ⇒ **预算耗尽 ⇒ 命令不下发 ⇒ 写不再发生** ⇒ ⇒ 原判「零 deadline 检查 ⇒ 写路径照写不误」（本域 2026-10-26 独立核实成立）**已于 2026-10-31 失效** ⇒ 详见 **§3.6**。<br>✅ **本域单方面无法闭环**那一格**已消解**：共享层那半已落地（`service/common` 的 Redis 期限载体 ＋ `BeanPostProcessor` 自动装配 ⇒ 各域**零改动**获得该能力）⇒ ⇒ **本域不再需要单独修**。<br>🟢 **证据强度：升级为 A 活体** —— **两侧同时跑**的探针 `tools/semantic-probe/redis-deadline/run-redis-deadline-parity.sh`（**本域独立复跑 EXIT=0**）：`form=exhausted` ⇒ `write_applied=false` / `value_after=baseline` ⇒ **命令根本没下发**；`form=alive`（负控）⇒ `write_applied=true` ⇒ ⇒ **两侧逐键相等**。＋ **本域装配面活体守门** `CommentRedisDeadlineCarrierWiringGateTest`（3 例，真 Redis）⇒ 装配面 / 耗尽不写 / 事务绑定路径 三面同形 ⇒ ⇒ **机制层 ＋ 装配面 ＋ 事务面**齐备（⚠️ 共享层的**变异**证据归 G4 写权，本域**未代做、不代称**）。⚠️ **残余**：共享层登记的「拨号窗口内过期」**资源层**窄窗口仍存 ⇒ 归 `docs/DRIFT/global/vs-common.md` §4.19.7（**不在本域写权内**） | ✅ **已销账**（§3.6 · 2026-10-31） |

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
---

## 7. 🔴 Redis **TCP 连接超时**漂移（Go `DialTimeout` **5s** vs Lettuce 默认 **10s**）—— 2026-10-27 已修

> ℹ️ **2026-10-27 编号订正**：本节原标题含 **`D10`**，与 §3 摘要表的 **`D10`**（fact 投递链路的无效 span 属性）**撞号** ⇒ **已去掉**。
> ⇒ **本条不占 `D` 编号**：§3 的 `D1`~`D10` 体系 = 「**OBSERVABLE DIFFERENCE（行为等价，但实现路径 / 部署形态不同）**」；
> 而本条是**真漂移**（连接超时 **10s** vs Go **5s** ⇒ 可观察的时序差异）⇒ **不属该体系** ⇒ 故留在 §7 自成一节。

### 7.1 漂移事实（修前）

| 项 | Go（真源，唯一基准） | RAG 修前 | RAG 修后 |
|---|---|---|---|
| **TCP 连接**超时<br>（`DialTimeout` / `SocketOptions.connectTimeout`） | **5s** | 🔴 **10s** | ✅ **5s** |
| 命令超时<br>（`Read/WriteTimeout` / `commandTimeout`） | **3s** | ✅ 3s | ✅ 3s |

**Go 出处（消费点，不是声明点）**：

```
service/comment/rpc/internal/cache/redis.go:17-19
    rdb := redis.NewClient(&redis.Options{Addr: Host})   ← **只设了 Addr**
  ⇒ DialTimeout 未设
  → go-redis v9.20.0 options.go:338-340
    if opt.DialTimeout == 0 { opt.DialTimeout = 5 * time.Second }
    ⇒ DialTimeout = **5s**
    （ReadTimeout/WriteTimeout 同理走 options.go:364-379 ⇒ 3s）
```

**RAG 侧为什么「不写就错」**（Spring Boot **4.0.8**，L3 字节码实测）：

| 事实 | 证据 |
|---|---|
| `spring.data.redis.connect-timeout` **非空时**才设 `SocketOptions.connectTimeout` | `LettuceConnectionConfiguration.createClientOptions` 字节码 offset 9→31：`getConnectTimeout()` → `SocketOptions.builder().connectTimeout(...)` → `ClientOptions.Builder.socketOptions(...)` |
| **不写 ⇒ 整段不设** ⇒ 落回 Lettuce 默认 | lettuce-core 6.8.2 `SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10`（秒） |
| `spring.data.redis.timeout` 只喂**命令**超时，**不再**兼管连接超时 | 同一自动配置里 `getTimeout()` → `LettuceClientConfigurationBuilder.commandTimeout(...)` ⇒ **Boot 2/3 才是同一个属性** |
| 该属性确实存在（不是臆造的名字） | `spring-boot-data-redis-4.0.8.jar` 的 `spring-configuration-metadata.json`：`spring.data.redis.connect-timeout`（defaultValue = None） |

⇒ **修前漂移 = 2 倍**。

> 🔴 **本条在 comment-rpc 的可观察性比 api 域强**：comment-rpc 是**纯 gRPC 进程**
> （**没有 HTTP 预算兜底**）⇒ 连接超时 10s vs 5s 会**直接**体现在 gRPC 调用耗时上。

### 7.2 修法与守门

| 项 | 内容 |
|---|---|
| 修法 | `application.yml` 的 `spring.data.redis` 下显式加 `connect-timeout: 5s`（**域内文件 ⇒ 域侧自修**，不依赖共享层） |
| 为什么共享层不参与 | `service/common` 的 `GoStyleRedisClientAutoConfiguration` **只**设 `disconnectedBehavior(REJECT_COMMANDS)`、**不碰** `socketOptions` / `timeoutOptions`（其 javadoc「为什么不动命令超时」段已写明）⇒ 连接超时**完全由本域 yml 决定** ⇒ **不装副本、不重复收编**<br>🔴 **订正（2026-10-27 第十八轮 · 合并后）**：合并前这条是**跨树指针**（本树 `find service/common/src/main/java -iname "*redis*"` ⇒ 零命中）⇒ ⇒ 合并后**已在本树** ⇒ ⇒ **树内重验成立**：唯一的 builder 调用 = `disconnectedBehavior(REJECT_COMMANDS)`（`:131-132`）⇒ **不碰 `socketOptions` / `timeoutOptions`** ⇒ ⇒ **结论不变**（从跨树推断升级为树内事实） |
| 守门 | 新增 1 个契约守门（**1 例**）—— 走**整条真实链路**：`ConfigDataApplicationContextInitializer` 加载本模块**真实的** `application.yml` → Boot **真实** `DataRedisAutoConfiguration`（它 `@Import` 了 `LettuceConnectionConfiguration`）→ 真 `LettuceConnectionFactory` → 断言**消费点** `getClientConfiguration().getClientOptions().get().getSocketOptions().getConnectTimeout()` 与 `getClientConfiguration().getCommandTimeout()` |
| 为什么断言「消费点」而不是「yml 里有这个键」 | 后者挡不住「属性名写错 ⇒ Spring 静默用默认值」（§3.3.1） |

### 🧪 变异验证（防假绿）

| 步骤 | 结果 |
|---|---|
| 本轮跑守门 | ✅ **1/1 绿** |
| **变异**：删掉 `connect-timeout: 5s` 一行（其余不动） | 🔴 **精确转红**：报「**expected: 5S but was: 10S**」⇒ 守门**真的**在读 Lettuce 的 socket 连接超时，不是恒绿 |
| 还原 | ✅ **1/1 绿** · `sha256` 与变异前备份**逐字一致** |
| 整域回归 | ✅ **四域 12 模块 = 919 条**（article-common 15 / article-api 99 / article-rpc 155 / comment-common 27 / comment-api 52 / comment-rpc 116 / favorite-common 29 / favorite-api 105 / favorite-rpc 114 / hot-common 14 / hot-api 96 / hot-rpc 97）⇒ **0 failures / 0 errors / 4 skipped** · 9:44 min · BUILD SUCCESS<br>口径：`mvn -o --fail-at-end -pl <12 模块> -am test`；基线 `eb4d1d2` + 本轮改动；核对日期 **2026-10-27**；两口径（模块汇总行求和 ≡ 逐类明细行求和）**逐模块一致** |
| 整域回归（**本轮 · D10 落码后 · 2026-10-27**） | ✅ **四域 12 模块 = 922 条**（article-common 15 / article-api 99 / article-rpc 155 / comment-common 27 / comment-api 52 / **comment-rpc 119**（= 旧 116 ＋ D10 新增 3） / favorite-common 29 / favorite-api 105（skipped 1） / favorite-rpc 114（skipped 3） / hot-common 14 / hot-api 96 / hot-rpc 97）⇒ **0 failures / 0 errors / 4 skipped** · **10:17 min** · BUILD SUCCESS<br>口径：`mvn -o --fail-at-end -pl <12 模块> -am test`；基线 `deeeb8d` + 本轮文档改动；核对日期 **2026-10-27**；两口径（模块汇总行 ≡ 逐类明细行）**逐模块一致**<br>⚠️ **上一行的 919 保留原值不改写**（那是基线 `eb4d1d2` 的口径）⇒ ⇒ **两个数不在同一条基线上 ⇒ 不可相加**；本轮除 comment-rpc 外**其余 11 个模块逐条不变** ⇒ ⇒ 差额 **+3 全部来自 comment-rpc**（D10 新增 3 条守门，commit `deeeb8d`） |

**证据强度**：L3 静态实测（Boot 4.0.8 **字节码** + Lettuce 6.8.2 常量 + go-redis v9.20.0 源码）
＋ L4 **真容器**装配断言（`ApplicationContextRunner` + Boot 真实 `DataRedisAutoConfiguration`）。

> ⚠️ 本域 yml 开了 `spring.data.redis.lettuce.pool.enabled: true` ⇒ pom **必须**引
> `commons-pool2`（本域 pom 已显式声明并写了理由）⇒ 守门起真容器时不会踩
> `NoClassDefFoundError: GenericObjectPoolConfig`。

---

## 8. 🧹 U+FFFD 清理（2026-10-27）—— **1 处真损坏**

| 文件:行 | 损坏形态 | 重建 | 依据 |
|---|---|---|---|
| `service/CommentAuditPublisher.java:19` | `把它当` + **3×U+FFFD** + `<b>会失败</b>` | `把它当作<b>会失败</b>` | 3×U+FFFD = **1 个汉字**（UTF-8 3 字节逐字节替换）；同句语义「把它当作『会失败』的操作处理」是唯一自然读法；**只改注释**，不改行为 |

⇒ 重建后本域 `grep -rln $'\xef\xbf\xbd'` **零命中**。

---

## 9. 🧹 启动守门 flake 修法（2026-10-27 · G4 登记）

### 9.1 症状

`CommentRpcRuntimeBootGateTest#realFatJarProcessBootsAndListens` 在 G4 的全量回归（2448 条）里**偶发红 1 条**。

### 9.2 根因（**不是启动失败**）

原逻辑：真进程 fat jar 起来 → `tcpProbe` 端口一连上就**立刻** `destroyForcibly()` ⇒
**drain 线程还没读到**「gRPC Server started」那行日志 ⇒ 该断言**必然偶发红**。

**辨识度**（为什么判定它是竞态而不是启动失败）：端口已监听 + 无 `NoClassDefFoundError`
⇒ 进程**真的起来了** ⇒ 红的只是「断言与日志读取的竞态」。

### 9.3 修法（对齐 G4 建议方向）

| 步骤 | 内容 |
|---|---|
| ① 收摊前**先等日志行出现** | 轮询「gRPC Server started」标志行，上限 30s、间隔 200ms |
| ② sink 改**线程安全** | `StringBuilder` → **`StringBuffer`**（sink 跨线程读写） |
| ③ 建 happens-before | `drain.join(10s)` |

### 9.4 验证

| 项 | 结果 |
|---|---|
| 定向跑 | ✅ `CommentRpcRuntimeBootGateTest` **2/2 绿** |
| 守门加固（**2026-10-27 · 照 G2 同款修复 `f57ff38` 的增强**） | 🟢 **标志串提为常量** `GRPC_STARTED_MARKER` ⇒ 消除「**等待循环**」（`:132`）与「**断言**」（`:145`）两处**各写字面量**的漂移隐患 ⇒ 若任一处被改而另一处没改 ⇒ 要么白等满 30s 上限后莫名失败，要么断言**平凡通过**（守门失效）⇒ **两处共用一个定义** ⇒ 不可能漂移。<br>**验证**：提取后 `CommentRpcRuntimeBootGateTest` **1/1 绿**（7.005s）· **变异 1/1 精确变红**（把常量值改成假串 ⇒ 等满 30s 上限 ⇒ 红在 `:159` 的**目标断言**、失败消息点名 `MUTANT` 且**带真实日志尾部** ⇒ 证明「进程真起来了、红的是标志串不匹配」⇒ **非假绿**）· 回滚后**字节一致**（md5 相同 ⇒ 与绿版同一份内容 ⇒ 先前那次绿即「还原后」的证明） |
| 整域回归 | ✅ **comment-rpc 116 条**（skipped 0）全绿 ⇒ **四域 12 模块合计 919 条**（0 failures / 0 errors / 4 skipped · 9:44 min · BUILD SUCCESS · 基线 `eb4d1d2` + 本轮改动 · 核对日期 2026-10-27 · 两口径逐模块一致） |
| 整域回归（**订正注记 · 2026-10-27**） | ⚠️ **上一行的 116 / 919 是基线 `eb4d1d2` 的口径，保留原值不改写** ⇒ ⇒ 本轮（D10 落码后）的最新口径 = **comment-rpc 119 条** ＋ **四域合计 922 条**（见本文件 §3.7 与上面那行「整域回归」）⇒ ⇒ **两个数不在同一条基线上 ⇒ 不可相加** |
| 同款缺陷在本域另一组件 | 🔴 `ArticleRpcRuntimeBootGateTest` **同款** ⇒ 已在 **article 域**同批修（属 G3 写权面） |
| 同款缺陷在 G2 域 | 🔴 `like/like-rpc/…/LikeRpcRuntimeBootGateTest.java` **同款** ⇒ **属 G2 写权面** ⇒ 已回报 G2，本域**不越权改**<br>✅ **订正（2026-10-27）**：G2 已在本域修完（commit `f57ff38`）⇒ 修法三步同款 ＋ **一处增强**（标志串提为常量 `GRPC_STARTED_MARKER`，消除「等待循环/断言两处字面量」的漂移隐患）⇒ ⇒ **本域的两个同款守门已照该增强加固**（见上一行） |
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

### 附录 · 📌 共享层静态池潜伏缺陷 ⇒ **「前五轮全量都绿」不等于「本来就对」**（2026-10-27 · G4 通报）

- **第六次全量**里 `article-rpc` 的 2 条失败，根因在**共享层**：`RpcInterceptor` / `HttpMiddleware` 的三个**进程级静态池**是 `static final` ⇒ `shutdown()` **永久终止** ⇒ surefire **一模块一 JVM** ⇒ **模块内多个 Spring 上下文** ⇒ 前一个上下文关闭后，后续上下文里 `schedule()/submit()` **被拒** ⇒ 拦截器返回 **INTERNAL** 而不是 **DEADLINE_EXCEEDED** ⇒ ⇒ **G4 已修**（`AtomicReference` + CAS 自旋，**落 `service/common` ⇒ 本域零改动**）。
- 🔴 **为什么前五轮全量都绿**：修 G1 派单 #8 之前，纯 gRPC 进程的 `@PreDestroy` **先抛 `NoClassDefFoundError`** ⇒ `shutdown()` **根本没机会执行** ⇒ 静态池**侥幸存活** ⇒ ⇒ **「绿」是因为那条路径根本没被执行过**。
- ⇒ **本域（comment-rpc，同样用这三个共享池）的历史全量结论要按这条重新读**：不是「本来就对」，而是「**缺陷把保护罩挡住了**」⇒ ⇒ **教训**：拿到「一直绿」的结论时多问一句「**这条路径在本轮之前真的被执行过吗**」；**归属判定看根因落在哪一层，不看失败用例在哪个域**。
---

### 附录 · 📌 待验：rpc 侧 Redis 断连的**对外后果与 api 侧不同**（2026-10-27 · G1 通报 ＋ 本域自核）

- **G1 的形态**（users 域已修并立了 2 例守门）：rpc 侧 Redis 断连 ⇒ 命令**排队到 `commandTimeout: 3s`**
  ≫ rpc 组件的 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 **1015「服务繁忙」**；
  而 Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」` ⇒ ⇒ **api 侧与 rpc 侧后果不同**（503 vs RPC 撞超时）
  ⇒ ⇒ **共享层收编时两侧要分别验，别只验 api 侧**。
- **本域自核 ⇒ 同款形态成立**：
  - **comment-rpc**：Redis 调用在 **gRPC 请求路径**（`CommentCacheService` 的 `opsForValue()` 读写）⇒ 断连 ⇒ 排队到 3s
    > `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 与 Go 的快速失败**后果不同** ⇒ 🔴 **待验**。
  - **hot-rpc**：`get_hot_articles_logic.go:43` 的 `ZRevRangeWithScores` 在请求路径 ⇒ 同款 ⇒ 🔴 **待验**。
  - ⚠️ **`mqs/hot_handler.go` 的 MQ 消费路径不属这一族**（ctx = Kafka 消费 ctx / `context.Background()`）⇒ **不修**。
- **修法落点 = 共享层**（`service/common/.../redis/GoStyleRedisClientAutoConfiguration` 的 `REJECT_COMMANDS`）
  ⇒ ⇒ **本域不装副本**（域内副本会**遮蔽**共享层那份 ⇒ **收编根本没发生**）
  ⇒ ⇒ **等 G4 合进我树后**，按 G2 的配方做**断连活体对拍**（先连上 → 静默等待且**期间不发命令** → 外部 `docker stop`
  ⇒ 量第一条命令挂多久）⇒ **本域零域侧改动**；⚠️ 但**「两侧后果分别验」要落进验的清单**，不能只验 api 侧。
  - 🔴 **订正（2026-10-27 第十八轮 · 合并后）**：**「等 G4 合进我树后」这个前置已解除** —— merge `e18fd52` 把 `align/g4-service-common` @ `cd893d1` 合入 ⇒ ⇒ 共享层的 `GoStyleRedisClientAutoConfiguration` **已在本树** ⇒ ⇒ **「共享层那份必被创建」现在可以在树内验**（我四域仍是**零域侧副本** ⇒ **无遮蔽** ⇒ 收编在技术上成立）⇒ ⇒ ⚠️ **但活体对拍本身仍被机器级环境阻塞**：本机 Docker 的「宿主→容器」端口转发对多数容器失效（映射口「TCP 连上但读不到字节」，而容器内 `psql` 正常 ⇒ 坏的是转发路径）⇒ ⇒ **本条维持「🔴 待验」**，待 Docker 恢复后按 G2 的配方重跑（**零域内配置**，量第一条命令挂多久）⇒ ⇒ **本域零域侧改动**这一条**不变**。
- ✅ **本域无遮蔽风险（已自核）**：`grep -rn "LettuceClientOptionsBuilderCustomizer|RedisDisconnectedBehaviorConfig|DisconnectedBehavior"`
  在 `service/{favorite,comment,hot,article}` 的**源码**里 ⇒ **0 命中** ⇒ 共享层那份**必被创建**
  （三模块都引 `spring-boot-starter-data-redis` ⇒ `@ConditionalOnClass` 命中）⇒ **零域侧动作** ✅
- ✅ **前提已坐实（本轮自核 · 补出处）**：上文说的「`rpc-timeout: 2s`」指的是**服务端**超时（不是客户端封顶），
  Go 侧出处 = go-zero v1.10.2 `zrpc/config.go:46`
  `RpcServerConf.Timeout int64 \`json:",default=2000"\`` ⇒ **服务端**默认 2000ms；
  ⚠️ **`:31` 那个 `Timeout` 是 `RpcClientConf.Timeout`（客户端）** ⇒ **两个字段默认同值但语义不同** ⇒ 别混。
  ⇒ Go 侧 `service/comment/rpc/etc/comment.yaml` **零顶层 `Timeout` 键** ⇒ 走默认 **2000ms = 2s** ⇒ 两侧同为 2s。
  ⇒ **真源侧消费点（唯一超时执行者）**：`service/comment/rpc/comment.go:33` `DisableNativeRpcTimeout(&c.RpcServerConf)`
  ＋ `service/comment/rpc/comment.go:54` `NewUnaryServerInterceptor(rpcTimeout, ...)` ⇒ **框架自带超时被关掉、由真源自建接管**。
  ⇒ RAG 侧等价物 = `observability.rpc-timeout` ⇒ 消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:80-81`
  ⇒ **已在 docs/DRIFT/comment/vs-common.md 的 A1 行结案（服务端一致）**。
  ⚠️ **因此**：上文「断连 ⇒ 命令排队到 `commandTimeout: 3s` ≫ `rpc-timeout: 2s` ⇒ **RPC 先超时**」里的 2s，
  指的就是**这个服务端预算** ⇒ **不是**客户端 2s 封顶那一条 ⇒ 别拿客户端那条来解释服务端先超时。

### 附录 · 📌 **订正注记（2026-10-27 · G3 · 20:15）：上面「🔴 待验」的**机制格已验完**（新增 §十二）

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上文「🔴 **待验**」**原样保留** ⇒ 读本域历史结论的人**以两块并列为准**。

- **已验的格**：**「共享层那份 `REJECT_COMMANDS` 在本域真的生效吗」** ⇒ ✅ **已验**（§十二：仓内守门 2 条 ＋ **活体 A/B** 实测 **A=1ms / B=3005ms** ＋ **Go 腿 1708–1776ms**）
- **仍未验的格**：**「断连时客户端（api 侧）到底看到什么」** ⇒ 仍 🔴 **待验**（那是 **gRPC 响应层**的问题：断连 ⇒ 命令是**毫秒级拒单**（不是排 3s）⇒ ⇒ **RPC 不再撞 2s 服务端超时** ⇒ ⇒ 客户端看到的**不再是 1015「服务繁忙」**，而是域内的业务错误 ⇒ **但这条要真发一次 gRPC 调用才测得到** ⇒ **未做**）
- ⇒ 🔴 **上文那条「机器级 Docker 端口转发故障 ⇒ 活体对拍被阻塞」的订正（第十八轮）已被本轮实测覆盖** —— 本轮**活体对拍真的跑通了**（真 Redis 容器 → 连上 → PING → 静默 1s → `docker stop` → 量第一条命令）⇒ ⇒ **那个阻塞前提彻底消失** ⇒ ⇒ ⚠️ **但上文原值不改写**（按纪律：登记 + 注记，不改写历史）⇒ ⇒ **以本块为准**。
- ⇒ ⚠️ **上文的「修法落点 = 共享层」这句现在有了实测支撑**：共享层那份**确实**在 comment-rpc 里生效（无域内副本 ⇒ `size == 1` ＋ bean 名对得上）⇒ ⇒ **comment-rpc 零域侧改动**这条结论成立 ✅。


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
---

### 附录 · 📌 拍板落地注记（2026-10-27 · Jason）＋ 订正一处「既有先例」（U15 / D8 = global E65）

**① 方向已定**：**走 global 统一载体**（`GoStyleRequestDeadline.applyTo(stub)`）⇒ **本轮不改** ⇒ 共享层那半落地后本域照做。
⇒ **被否决的备选** = 用 `DeadlineState.remainingMillis()` 直接构造出向 stub deadline（理由：会引入第二种载体 ＋ 偏离 Go 的单载体语义 ＋ 需 global 改探针期望值）
⇒ 完整理由与影响面见 [`vs-common.md`](vs-common.md) **§4.2**。

**② 🔴 订正一处「同仓既有先例」**：本文件 §3.5 与 U15 行原引
「`users/admin/AdminDomainService.java:140` · `users/user/UserDomainService.java:145` 已用 `DeadlineState.isExpired()`」⇒
**实测不成立** —— 那两处是**本地超时检查**（`if (Thread.currentThread().isInterrupted() || DeadlineState.isExpired())`）
⇒ **不是「用 `DeadlineState` 构造出向 gRPC deadline」** ⇒ ⇒ **不得据它论证「仓内已有第二种载体的出向用法」**；
⚠️ 且该判断的**行号已漂移**（实测命中在 `AdminDomainService.java:178`）⇒ **引用前须重新定位**
⇒ **保留原值不改写**（本条即订正注记）。

---

## 10. 🟡 Redis **连接池形态**：**形态已对齐**，仅**数值**不同 —— 2026-10-27 登记（G2 挖出 · 本域**独立复核**）

> **公共证据链（Boot 4.0.8 字节码判据 · classpath 探测 · 处置理由 · 复开触发条件）见 [`favorite/api.md` §15](../favorite/api.md#15-🔴-redis-连接池形态漂移go-有池-vs-rag-无池-2026-10-27-登记g2-挖出--本域独立复核--只登记不落码)** ⇒ 本节**只记本域特有**的部分。

### 10.1 本域结论

| 项 | Go 真源 | RAG（本树 HEAD `18aa012`） | 判定 |
|---|---|---|---|
| **连接池形态** | ✅ **有池** | ✅ **有池** | 🟢 **形态一致** |
| 池容量 / 活跃上限 | `PoolSize = 10 × GOMAXPROCS(0)`（**运行时值**） | `max-active: 64`（**固定**） | 🟡 **数值不对齐** |
| 空闲连接下限 | `MinIdleConns = 0`（本域走**直连**路径） | `min-idle: 4` | 🟡 **数值不对齐** |
| 空闲连接保留上限 | ⛔ **go-redis 无对应概念**（只存 `PoolSize` + `MinIdleConns`） | `max-idle: 16` | ⛔ **语义无对应 ⇒ 不可 1:1** |
| **证据强度** | **L1** | **L3**（字节码 + `dependency:tree` 实测） | ⚠️ 不是 L4（无活体并发对拍） |
| 核对日期 | 2026-10-27 | 2026-10-27 | — |

### 10.2 两侧证据

1. **Go 侧（直连 go-redis）**：`service/comment/rpc/internal/cache/redis.go:17-19` `redis.NewClient(&redis.Options{Addr: Host})` ⇒ **只设了 Addr** ⇒ `PoolSize` 未设 ⇒ go-redis v9.20.0 `options.go:350-351` ⇒ **`PoolSize = 10 × GOMAXPROCS` 生效**；`MinIdleConns` 未设 ⇒ **= 0**。
2. **Go yaml**：`service/comment/rpc/etc/comment.yaml:37-40` `BizRedis: {Host, Type, Pass}` ⇒ **无池字段**。
3. **RAG 侧**：`service/comment/comment-rpc/src/main/resources/application.yml:152-157` ⇒ `lettuce.pool.enabled: true` ＋ `max-active: 64` ＋ `max-idle: 16` ＋ `min-idle: 4` ⇒ ⇒ `Pool.enabled = true` ⇒ **有池**（走 `PoolBuilderFactory` 分支）。
4. **classpath 实测**：`mvn -B -o -pl service/comment/comment-rpc -am dependency:tree -Dincludes=org.apache.commons:commons-pool2` ⇒ EXIT=0 ⇒ **1 命中**：`org.apache.commons:commons-pool2:jar:2.12.1:compile` ⇒ ⇒ `PoolBuilderFactory` 需要的 `GenericObjectPoolConfig` **可用** ⇒ ⇒ 池**真的开得起来**（不是「配了但起不来」）。
   ⚠️ **这正是本域 pom 里显式声明 `commons-pool2` 的原因**（`service/comment/comment-rpc/pom.xml:181-191`）⇒ **Boot 4 的 starter 不传递 `commons-pool2`** ⇒ 缺它 ⇒ `NoClassDefFoundError: GenericObjectPoolConfig` ⇒ **组件起不来**（先例：`like-rpc`）⇒ ⇒ **本域这条是「配了 + 依赖在位」的完整闭环**，不是「只配了一半」。

### 10.3 处置

**形态已对齐 ⇒ 不必动**；**数值不对齐 ⇒ 登记**（**不落码**）⇒ 理由与复开触发条件见 [`favorite/api.md` §15.3](../favorite/api.md#153-处置只登记不落码写死复开触发条件)（① 可观察行为 ② `10 × GOMAXPROCS` 是运行时推导值 ③ 两套池语义无一一对应 ④ 需并发活体对拍 ⑤ 连带影响断连/重试语义）。
⇒ ⚠️ **本域是三个用 Redis 的域里唯一「有池」的那个** ⇒ ⇒ 若共享层未来要加**池形态**维度的守门，**本域是唯一能提供正样本的域**（`favorite-api` / `hot-rpc` 都是无池）⇒ 记一笔给 **G4**。

### 10.4 📌 **订正注记（2026-10-27 · G3）：§10 的「数值不对齐」已落码修复** —— 上文原样保留不改写

⚠️ **本块是「加注记」，不是「改原值」** ⇒ §10.1 的表（含「`min-idle: 4`」与「⛔ go-redis 无对应概念」两处）与 §10.3 的「**不落码**」**原样保留** ⇒
读本域历史结论的人**以两块并列为准** ⇒ ⇒ **不要**把 §十一 误读成「§10 作废」。

1. **§10.1 表里「空闲连接下限 = `min-idle: 4`」这一行已过期** ⇒ 现行口径 = **`min-idle: 0`**（**精确对齐 Go**）⇒ 落码与守门见 **§十一**。
2. 🔴 **§10.1 表里「⛔ go-redis 无对应概念（只存 `PoolSize` + `MinIdleConns`）」这句措辞不准确** ——
   **go-redis v9.20.0 有 `MaxIdleConns`**（`options.go:224-228`，**无 default 赋值** ⇒ **0**）
   ⇒ 消费点 `internal/pool/pool.go:1257`
   `if p.cfg.MaxIdleConns == 0 || p.idleConnsLen.Load() < p.cfg.MaxIdleConns` ⇒ **0 = 无上限**
   ⇒ ⇒ 所以正确的表述是：**「有对应项，Go 侧取值 = 0（无上限）」**，而不是「无对应概念」
   ⇒ ⇒ RAG 侧的 `max-idle: 16` 是**表达层取值（比 Go 更严）**，属**可登记的残留差异**，不是「语义无对应 ⇒ 不可 1:1」。
   ⚠️ **同款措辞错误也出现在 G2 的 `task/vs-common.md` A1 ⑦** ⇒ 已一并通报（见 §十一.4）。
3. **§10.3 写的「不落码」已被本轮推翻** ⇒ **落码**（域侧自修）⇒ 见 **§十一**。

---

## 十一、✅ Redis **连接池参数口径** —— **已落码**（2026-10-27 · G3 · 修 §10 的「数值不对齐」）

### 十一.1 一句话

**形态本来就对**（§10）⇒ 本轮只修**参数**：`min-idle` **4 → 0**（**精确对齐 Go**）＋ **新增 `max-wait: 4s`**（池耗尽的等待上限）⇒
守门 **4 条** ＋ **变异 4 条全部精确转红**。

### 十一.2 落码清单

| # | 文件 | 位置 | 改了什么 |
|---|---|---|---|
| 1 | `service/comment/comment-rpc/src/main/resources/application.yml` | `:159-165` | `min-idle: 4` → **`0`**；**新增** `max-wait: 4s`（`enabled` / `max-active` / `max-idle` 未动） |
| 2 | `service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/config/CommentRpcRedisPoolContractTest.java` | **新增** | 守门 **4 条**（读**真装配**，不读 yml 文本） |

⚠️ **pom 无需改**：`commons-pool2` 早已显式声明（`service/comment/comment-rpc/pom.xml:181-191`）⇒ 本域**不存在** `hot-rpc` 那条「pom 必须补依赖否则启动期 `NoClassDefFoundError`」的风险。

### 十一.3 取值依据（逐项对着 Go 的真值，不拿「现有 RAG 代码」当基准 · AGENTS.md §3.1）

| 语义 | Go 真值（消费点） | 本域取值 | 对齐度 |
|---|---|---|---|
| 有没有池 | ✅ `service/comment/rpc/internal/cache/redis.go:17-19` `redis.NewClient(&redis.Options{Addr: Host})` ⇒ `PoolSize` 未设 ⇒ `go-redis@v9.20.0/options.go:350-351` `= 10 × GOMAXPROCS` ⇒ **有池** | `enabled: true` ＋ pom 有 `commons-pool2` | ✅ 形态一致（§10 已记，本轮未动） |
| 总量上限 | 🟡 `MaxActiveConns` 未设 ⇒ `options.go:230-235` ⇒ Go **无总量硬上限**；`PoolSize` = 10×核数（机器相关） | `max-active: 64` | 🟡 数量级（性能面残留） |
| **最少空闲** | ✅ `MinIdleConns` 未设 ⇒ `options.go:218-222` **无 default** ⇒ **0**（本域 Go **直连 go-redis**，**不走** go-zero ⇒ 不是 8） | **`min-idle: 0`** | ✅ **精确对齐（本轮修复项）** |
| 最多空闲 | 🟡 `MaxIdleConns` 未设 ⇒ `options.go:224-228` **无 default** ⇒ **0** ⇒ 消费点 `internal/pool/pool.go:1257` ⇒ **0 = 无上限** | `max-idle: 16` | 🟡 表达层取值（比 Go 更严） |
| **池耗尽等待** | ✅ `PoolTimeout` 未设 ⇒ `options.go:380-386` `= ReadTimeout + 1s` = **4s**；消费点 `internal/pool/pool.go:1113` `p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)` | **`max-wait: 4s`** | ✅ **语义对齐（本轮新增项）** |

#### 📌 订正注记（2026-10-27 · G3 · 21:1x）：**`PoolSize` 是「基准连接数」，不是「连接数硬上限」**（G1 lane 提出 · 本域**独立读源码复核后确认**）

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上表**结论一个字未改**（本域原表的「总量上限」行**本来就写对了**）⇒ 本轮只是把**口径**写得更准。

- **G1 的订正（原始出处）**：**别把「Go 最多 `10×核数` 条连接」写进台账** ⇒ ⇒ 那会把**基准值**当成**上限**。
- **本域独立复核（go-redis v9.20.0 `options.go:200-206` 字段注释原文）**：
  `// PoolSize is the base number of socket connections.`
  ＋ `// If there is not enough connections in the pool, new connections will be allocated in excess of PoolSize,`
  ＋ `// you can limit it through MaxActiveConns`
  ⇒ ⇒ **池不够时连接数会「超出 `PoolSize`」** ⇒ ⇒ **真正的总量上限是 `MaxActiveConns`**
  ⇒ ⇒ 本域 Go 侧 `MaxActiveConns` **未设** ⇒ **Go 无总量硬上限** ✅（上表「总量上限」行**原本就写对了**）。
- **更精确的说法（本轮补）**：`PoolSize` 的真正作用是**「信号量容量」** ——
  `internal/pool/pool.go:421` `semaphore: internal.NewFastSemaphore(opt.PoolSize)`
  ⇒ ⇒ **池耗尽的上限 = `PoolSize` 这个信号量容量**
  ⇒ ⇒ `pool.go:1098` `func (p *ConnPool) waitTurn(ctx context.Context) error` ⇒ 抢不到就
  `pool.go:1113` `p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)`
  ⇒ ⇒ `pool.go:1124` `case ErrPoolTimeout:` ⇒ **错误身份 = `redis: connection pool timeout`**
  ⇒ ⇒ **并发命令数 > `PoolSize` ⇒ 必然进等待 ⇒ 上限 4s**（与上表「池耗尽等待」行**一致**）。
⇒ **证据强度**：**L1**（两侧源码逐行）⇒ ⇒ **未做并发压测 ⇒ 不写 L4**。

🔴 **`max-wait` 为什么是「必配」而不是「优化」**：不配 ⇒ commons-pool2 2.12.1 `BaseObjectPoolConfig:59,68`
`DEFAULT_MAX_WAIT = Duration.ofMillis(-1L)` ⇒ 消费点 `GenericObjectPool:306`
`p = negativeDuration ? idleObjects.takeFirst() : idleObjects.pollFirst(remainingWaitDuration)`
⇒ **负值 ⇒ 阻塞到有人还连接为止（无上限）**；
🔴 且 **Lettuce 的 3s 命令超时救不了场** —— `CommandExpiryWriter.write(command)` 才起算计时器
（实测 lettuce-core 6.8.2 字节码），而**借连接发生在写命令之前**
⇒ ⇒ 池耗尽时 **3s 超时根本没开始** ⇒ ⇒ **无限挂起**（Go 是 4s 失败）⇒ ⇒ **光有池反而比 Go 更糟**。

### 十一.4 守门 + 变异验证（**4 条全部精确转红** ⇒ 守门非假绿）

**守门**：`CommentRpcRedisPoolContractTest`（**4 条**，零 Docker / 零 Testcontainers / 零真 Redis）——
起**真** `ApplicationContextRunner` ＋ Boot **真** `DataRedisAutoConfiguration` ⇒ 断言**真装配**的
`LettucePoolingClientConfiguration.getPoolConfig()`：
① 池必须启用 · ② `maxTotal=64` / `maxIdle=16` / **`minIdle=0`** · ③ **`maxWait=4s`** · ④ 阳性对照（commons-pool2 默认 = -1ms）。

**变异**：① `min-idle: 0→4`（**回到本轮修之前的值**）⇒ 红在 ② 的 `minIdle`；② 删 `max-wait` ⇒ 红在 ③；
③ `max-active: 64→65` ⇒ 红在 ② 的 `maxTotal`；④ `enabled: true→false` ⇒ 红在 ①。

**实测结果（4 条全部精确转红 · 2026-10-27）**

| 变异 | 手法 | 结果 |
|---|---|---|
| **MUT-A** | yml `pool.min-idle: 0` → `4`（**回到本轮修之前的值**） | ✅ **1 红**（`expected: 0 but was: 4`） |
| **MUT-B** | yml 删掉 `pool.max-wait` | ✅ **1 红**（`expected: 4S but was: -0.001S` ⇒ **实测到「无限等」**） |
| **MUT-C** | yml `pool.max-active: 64` → `65` | ✅ **1 红**（`expected: 64 but was: 65`） |
| **MUT-D** | yml `pool.enabled: true` → `false` | ✅ **3 红**（① ＋ ② ＋ ③，全部红在「池必须已启用」这条**前置断言**上 ⇒ **红点精确**） |

🔴 **一条必须记下的方法论**：变异跑**故意跑红的报告会留在 `target/surefire-reports/` 里**
⇒ ⇒ 跑完变异**必须再做一次覆盖性全量回归**，否则任何「读 `target/` 的判据」读到的是**上一次跑剩的产物**
⇒ ⇒ **本轮实测踩到了**：变异跑完后 XML 求和显示 comment-rpc「3 failures」，而 maven 汇总行是 123/0/0/0
⇒ ⇒ **以 maven 汇总行为准、以 XML 求和复核**，两者不一致时**先查报告新鲜度**。

### 十一.5 证据强度与边界

| 项 | 值 |
|---|---|
| **证据强度** | **L3** —— Go 侧 **L1 逐行对照** ＋ RAG 侧**真装配运行证据** ＋ 变异 |
| ⚠️ **不是 L4** | Go 侧无同时跑的探针 ⇒ **不是双侧活体对拍** |
| ⚠️ **未覆盖** | 并发活体对拍（p99 口径）⇒ ⚠️ **原记的阻塞理由「机器级 Docker 端口转发故障」已于 2026-10-27 19:2x 实测不成立**（四个探针全通，见 [`hot/rpc.md` §十一.8](../hot/rpc.md)）⇒ ⇒ **阻塞前提已消失** ⇒ 现在只差「Go 侧同时跑的探针」 |
| ⚠️ **守门不证明** | SQL / DDL / 事务（本域有库，守门只起 Redis 自动配置）· Kafka · 断连语义 |

### 十一.6 跨域影响（本轮实测 · 已通报 G2 / G4）

| 组件 | 原状 | 结论 |
|---|---|---|
| **`comment-rpc`** | 本域 | ✅ **已修**（本节） |
| **`hot-rpc`** | 本域（G3） | ✅ **已修**（[`hot/rpc.md` §十一](../hot/rpc.md)）⇒ **形态 ＋ 参数 ＋ `max-wait` 三项全修** |
| **`task-rpc`** | G2 | 🔴 **缺 `max-wait`** ⇒ 池耗尽**无限等**（Go 4s）⇒ 已通报 |
| **`like-rpc`** | G2 | 🔴 **缺 `max-wait`** ＋ `min-idle: 4`（Go 侧走 go-zero ⇒ 应 **8**）⇒ 已通报 |
| **`favorite-api`** | 本域（G3） | 🟡 **无池** ⇒ 池形态本身仍是漂移 ⇒ 见 [`favorite/api.md` §15.7](../favorite/api.md#157-🆕-落码清单本域-favorite-域--尚未落码--待排期) 的落码清单（**`min-idle` 应为 8**，与本域不同） |

---

## 十二、✅ Redis **断连活体对拍**（共享层的 `REJECT_COMMANDS` 在本域**真的生效**）—— 2026-10-27 · G3

### 十二.1 一句话

**共享层那份 `REJECT_COMMANDS` 在 comment-rpc 里真的生效** ⇒ 断连后第一条命令 **1 ms 失败**（不是排队到 3s 命令超时）
⇒ **Go 腿同场景 1708–1776 ms（4 轮）** ⇒ ⇒ **两侧都是「快速失败」，语义等价**；
⇒ 对照组（Lettuce 默认 `DEFAULT`）**3005 ms** ⇒ ⇒ **机制是承重的**（不是「反正都快」）。

### 十二.2 三段判据（守门 = `CommentRedisDisconnectFastRejectGateTest`，**2 条**，零真 PG）

| 判据 | 内容 | 为什么这么判 |
|---|---|---|
| **①** | 共享层那份 customizer **在场且没被域内副本遮蔽**：`getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` 的 **size == 1** ＋ bean 名含 `goStyleRedisClientOptionsCustomizer` | 共享层那份带 `@ConditionalOnMissingBean` ⇒ **域内若自己声明一个，共享层就退让** ⇒ 那时 size 可能是 1 但不是共享层那个 ⇒ **size == 1 ＋ 名字对得上两条合起来**才证明「用的就是共享层那份」 |
| **②** | 断连行为**消费点** = `connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()` == `REJECT_COMMANDS` | **不是读配置项**（那是「声明」）、也不是读 `ClientConfiguration` ⇒ 必须读**真在跑的客户端**的选项 |
| **③** | **活体 A/B**：真 Redis 容器 ⇒ 连上 ⇒ PING ⇒ **静默等待 1s（期间不发任何命令）** ⇒ 外部 `docker stop`（**优雅 FIN**，不是 kill 的 RST）⇒ 量**第一条命令**的耗时 | **A 组** = 共享层产出的那套 ClientOptions；**B 组** = 同一套**只**把断连行为换成 Lettuce 默认 `DEFAULT` ⇒ ⇒ **A 快 / B 排队到命令超时** ⇒ ⇒ 唯一差别就是那条机制 |

### 十二.3 实测数字（**2026-10-27 20:0x–20:1x · 本轮现跑**）

| 侧 | 场景 | 实测 | 判读 |
|---|---|---|---|
| **Go 腿**（真源 go-redis v9.20.0 ＋ **comment 域真源 `Options`** = `service/comment/rpc/internal/cache/redis.go:17-19` 的 `{Addr}` —— **只设 1 个字段** ⇒ 其余全走库默认） | 断连后第一条命令 | **1708 / 1776 / 1741 / 1753 ms**（**4 轮**） | **快速失败**（go-redis 的 `MaxRetries` 默认 3 ＋ 重试退避 ⇒ 拒连重试若干次后失败）⇒ 与 G2 台账记的 **1694 ms** 同量级 ✅；err = `dial tcp … connect: connection refused` |
| **Go 腿**（hot 域真源 `Options` = `{Addr, Password}`，**同一支探针换参数跑**） | 同上 | **1669 / 1808 / 1710 / 1771 ms**（**4 轮**） | 同量级 ⇒ ⇒ **两套真源 `Options` 的断连行为一致** |
| **RAG A 组**（共享层 `REJECT_COMMANDS`） | 同上 | **1 ms** | **毫秒级拒单** ⇒ 不排队 ⇒ **绝不撞 3s 命令超时** |
| **RAG B 组**（Lettuce 默认 `DEFAULT`） | 同上 | **3005 ms** | **排队到 3s 命令超时** ⇒ ⇒ 这是**对照组** |

⇒ **A/B 差 = 3004 ms** ⇒ ⇒ **机制是承重的**。
⇒ **两侧对比**：Go **~1.7 s** vs RAG(A) **1 ms** ⇒ ⇒ **量级不同（Go 的重试退避 vs Lettuce 立即拒单），
但语义相同：都是「快速失败」，都不是「挂到命令超时」** ⇒ ⇒ ⇒ **Go 的 `MaxRetries=3` 重试面**另属他档（**Lettuce 无重试**）⇒ **本条不覆盖那一条**（不混）。

### 十二.4 ⚠️ 口径边界（三条，逐条读）

1. 🔴 **Go 腿是「本轮现跑的一次性探针」，源文件不在仓内** ⇒ ⇒ **不可从仓复现**
   ⇒ ⇒ **证据强度**：RAG 腿 = **L4（仓内可复现的活体 A/B）**；Go 腿 = **L4-一次性（实测但仓外）**
   ⇒ ⇒ ⇒ **本轴整体不能写成「仓内可复现的双侧活体对拍」** ⇒ ⇒ 要长期可复现 ⇒ **需把 Go 探针入 `tools/`**
   （**`tools/**` 是 G4 写权面** ⇒ 已派单给 G4）。
   ⇒ **探针的等价规格**（可据此重建，**不依赖原探针文件**）：
   - **真源 `Options`**：`service/comment/rpc/internal/cache/redis.go:17-19` 的 `{Addr}`
     （hot 域 = `service/hot/rpc/internal/svc/service_context.go:17-20` 的 `{Addr, Password}`）
   - **其余全部走 go-redis v9.20.0 库默认**（`ReadTimeout 3s` / `DialTimeout 5s` /
     `PoolSize 10×GOMAXPROCS` / `PoolTimeout = ReadTimeout+1s`）
   - **时序**：**连上 → PING → 静默 1000ms（期间不发任何命令）→ `docker stop`（SIGTERM ⇒ 优雅 FIN）→ 量第一条命令**
   - **编排**：每组**各自**取一个空闲端口（不能复用同一个：容器还在占着）⇒ 起 `redis:7-alpine` ⇒ 等就绪 ⇒ 跑探针 ⇒ `docker rm -f` 收尾
2. **RAG 腿测的是「池里有空闲连接」这一形态**（本域现在**有池**）⇒ **不是**「池耗尽」那形态
   ⇒ ⇒ 两者**必须分开判**（池耗尽那形态由 §十一 的 `max-wait` 那条覆盖）⇒ ⚠️ **别把两条混为一谈**。
3. **不外推**：like / task / hot / users / admin 各域的域上下文不同 ⇒ customizer 数量可能不同
   ⇒ 行为可能不同 ⇒ ⇒ **每个域自己跑一遍**才是证据（**G3 的 hot 域自己跑了一份** ⇒ **两域互不代替**）。

### 十二.5 回归与门禁（**2026-10-27 20:23 · 本轮现跑**）

| 项 | 结果 |
|---|---|
| **comment-rpc 全量** | **125 / 0 failures / 0 errors / 0 skipped** ⇒ **BUILD SUCCESS**（exit 0，Finished at `2026-10-27T20:23:37+08:00`）⇒ 基线 **123** ＋ 本轮新守门 **2** = **125** ✅ |
| **comment-rpc surefire 复核** | **23 份 XML**（基线 22 ＋ 新增 1 个测试类）⇒ **求和 125/0/0/0** ⇒ **与 maven 汇总行一致** ⇒ **0 孤儿报告** |
| **新守门自身** | `CommentRedisDisconnectFastRejectGateTest` = **2 / 0 / 0 / 0**，**Skipped = 0** ⇒ ⇒ **docker 路径真跑到了**（不是被 `assumeTrue` 跳过）⇒ 耗时 **10.85 s** |
| **hot-rpc 全量**（同轮） | **103 / 0 / 0 / 0** ⇒ 基线 101 ＋ 2 = 103 ✅（详见 [`hot/rpc.md` §十二](../hot/rpc.md#十二✅-redis-断连活体对拍共享层的-reject_commands-在本域真的生效-2026-10-27--g3)） |
| **service/common 全量**（同轮 reactor） | **324 / 0 / 0 / 0** ⇒ **三模块合计 552 / 0 红** |
| **本轮改动文件** | `CommentRedisDisconnectFastRejectGateTest.java`（新增 310 行 / 2 条）· 本档两处段落 |
| **门禁（本轮终检）** | `tools/check-ledger-tables` **EXIT=0** · `tools/check-ledger-selfcheck` **EXIT=0** · `tools/check-ledger-count` **EXIT=1**（索引称 **315** / 实测 **328** ⇒ 差 **+13** ⇒ 索引在 `docs/DRIFT/_INDEX.md` = **合并收口执行者写权面** ⇒ **合并后统一 `--fix`**；本轮 **+2** 来自两域各增的 **§十二**）〔📌 **2026-10-27 订正**：**索引路径写错了** ⇒ 被改的是 **`docs/Pitfalls/_INDEX.md`**（不是 `docs/DRIFT/_INDEX.md`）⇒ **根因与闭合动作见 [`hot/rpc.md` §十二.6](../hot/rpc.md#十二6-📌-台账计数差-13-的根因定位2026-10-27--g3)** ⇒ **原值保留不改写**〕· `tools/check-doc-links` **EXIT=0**（本轮**实修 18 处**失效锚点 ⇒ 全部改用门禁自己的 `slug()` **实算值** ⇒ ⚠️ **锚点不要手写**）· `tools/difftest/check-wave-{lane-ownership,coupling,role-model}` **全 0** · `tools/ledger-lint/favorite/{tables,selfcheck,vs-reality}` = **0 / 1 / 1** ⇒ **与本轮改动前完全相同**（selfcheck 仍 **5 条**，**全在本轮 diff 之外**；vs-reality 那 1 条 = 「共享面零改动」，**结构性**）⇒ ⇒ **本轮零新增门禁级问题** |
| **U+FFFD 字节级扫描** | 本轮改动文件 **8 份全部 0 命中** · 本域台账＋坑档全量（**33 份**，已剔 `target/`）**0 命中** |
| **全仓 `check-*.py` 巡检** | **18 个脚本 ⇒ 17 个 EXIT=0 / 1 个 EXIT=1** ⇒ 那 1 个是 `tools/difftest/check-ddl-static.py` ⇒ **是 `users` 域的 9 处既知差异**（`users_user.status` 可空性 1 处 ＋ `DEFAULT CURRENT_TIMESTAMP` 残留 8 处）⇒ ⇒ **不在本域** ⇒ ⇒ **与本轮改动无因果关系** |

---

## 🔴 **阈值类结论的「共享层依赖」声明**（2026-10-27 · 按 G2 [3] 的要求做的重核）

⚠️ **凡本档写了「**运行期上限 / 阈值 / 池形态**」类结论，必须标「依赖共享层哪个版本」** ⇒ ⇒ **共享层一改 ⇒ 那条要重核**（重核，不是重写）。

| 本档结论 | 依赖面 | 当前实测（2026-10-27 重核） | 共享层一改会怎样 |
|---|---|---|---|
| **§10 的 Redis 池形态 = 有池**（comment-rpc） | **依赖共享层的 dependency 状态**（`isPoolEnabled` 在 `enabled == null` 时**回落到「classpath 上有没有 `commons-pool2`」**） | ✅ 本域 `enabled: true` ⇒ **显式配置 ⇒ 不依赖 classpath 探测**；且实测 classpath **1 命中**（`commons-pool2:2.12.1`）⇒ ⇒ **有池结论成立（且比 favorite/hot 更稳）** | ⚠️ 共享层若把 `enabled` 的默认值**改成 true** ⇒ ⇒ 本域会**静默多出**一份池配置语义 ⇒ ⇒ §10 要重核 |
| **`min-idle: 4` vs Go 的 `0`**（数值轴） | **不依赖**共享层（域内 yml） | 🔴 仍偏离（Go 侧 go-redis 直连 ⇒ `MinIdleConns` 未设 ⇒ **0**）—— ⚠️ **这是「落码前」的实测** ⇒ ⇒ ✅ **已修**（§十一 · 2026-10-27：`min-idle: 4 → 0` ＋ 补 `max-wait: 4s`） | 不受影响 |
| **Redis connect-timeout = 5s** | **不依赖**共享层（共享层只设 `disconnectedBehavior(REJECT_COMMANDS)`） | ✅ 域内 yml 显式配 5s | 共享层开始碰 `socketOptions` 时才变 |
| **🆕 §十一 的池参数 = `min-idle 0` ＋ `max-wait 4s`**（comment-rpc） | **不依赖**共享层**（域内 yml 显式配 ⇒ 共享层依赖树变化**不会**翻转本域的池参数）** —— ⚠️ 但**域内 pom 的 `commons-pool2`** 是 load-bearing 依赖 | ✅ **2026-10-27 实测**：`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0` / **`max-wait: 4s`** ⇒ 守门 4 条全绿 ＋ 变异 4 条全精确转红 ⇒ ⇒ **域内 pom 已有 `commons-pool2`（`:181-191`）⇒ 不存在 hot-rpc 那条「缺依赖 ⇒ 启动期失败」的风险** | 🔴 **域内 pom**：删掉 `commons-pool2` ⇒ ⇒ 启动期 `NoClassDefFoundError` ⇒ ⇒ **组件起不来**（不是「静默变无池」）⇒ ⇒ **这正是想要的形状** |
| **🆕 §十二 的断连行为 = `REJECT_COMMANDS`**（comment-rpc） | **完全依赖共享层那份 `GoStyleRedisClientAutoConfiguration`**（域内**零副本** ⇒ 共享层那份带 `@ConditionalOnMissingBean` ⇒ **域内一旦自己声明一个，共享层就退让**） | ✅ **2026-10-27 20:1x 实测**：`getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` **size == 1** ＋ bean 名含 `goStyleRedisClientOptionsCustomizer` ⇒ **无域内副本遮蔽**；**消费点** `connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()` == `REJECT_COMMANDS`；**活体 A/B** = **A(REJECT_COMMANDS) 1 ms** vs **B(DEFAULT) 3005 ms** ⇒ 差 **3004 ms** | 🔴 **共享层若删掉/改名那份 customizer** ⇒ ⇒ 本域**静默回落**到 Lettuce 默认 `DEFAULT` ⇒ ⇒ 断连从 **1 ms** 变成 **~3005 ms**（排队到命令超时）⇒ ⇒ **重新撞 2s 服务端超时** ⇒ ⇒ **§十二 必须重核**；<br>ℹ️ **域内侧无风险**：本域**零副本** ⇒ 域内改代码**不会**翻转这条 ⇒ ⇒ **这条的写权全在共享层** |

> 📌 **订正注记（2026-10-27 · G3）**：上表**第二行**（「`min-idle: 4` vs Go 的 `0`」的「🔴 仍偏离」）是**落码前**的重核结论 ⇒ **原样保留不改写** ⇒
> ⇒ 读本域历史结论的人**以「第二行 + 🆕 两行」并列为准** ⇒ ⇒ **不要**把 🆕 行误读成「第二行作废」⇒
> ⇒ 落码后的权威口径 = **§十一**（池参数）＋ **§十二**（断连行为），二者均带守门与活体证据。

⇒ **共享的判据明细表在 [`favorite/api.md` §17](../favorite/api.md#17-🔴-阈值类结论的共享层依赖声明2026-10-27--按-g2-3-的要求做的重核)（同款机制，三域共用一条判据）** ⇒ ⇒ 改一处要三域一起重核。

## 📌 指针 · 🔴🟢 **Testcontainers 宿主口钉口**（本域已落码 · 2026-10-28）

⇒ ⇒ **canonical 全文在** [`docs/DRIFT/article/rpc.md`](../article/rpc.md) 的「**二十三、🔴🟢 Testcontainers 宿主口钉口**」一节
⇒ ⇒ 本域那一段：模块 `comment/comment-rpc` · 域段 `22880–22909` · 本模块宿主口段 `22880–22899` · 脚手架段 `22900–22909` · **11** 个 PG + **1** 个 Redis 个容器
⇒ ⇒ 落点：`service/comment/comment-rpc/src/test/java/.../support/RealContainerHostPort.java` ＋ 同名 `RealContainerHostPortTest.java`（守门 **6** 条）
⇒ ⇒ **与 Go 侧的一致性：N/A**（测试基础设施，不涉及运行时行为）

## 📌 指针 · 🔴🟢 **redis-disconnect 探针（Go 腿 · comment 模式）**（2026-10-28）

⇒ ⇒ **canonical 全文在** [`docs/DRIFT/hot/rpc.md`](../hot/rpc.md) 的「**§十二.9**」一节（含两条订正注记）
⇒ ⇒ 本域那一档：真源 Options = `service/comment/rpc/internal/cache/redis.go:17-19` ⇒ **`redis.Options{Addr}`**（Password 留空）
⇒ ⇒ **实测**：`first_command_ms` = **1679 ms** · 归一化结论 `class=fast-fail err_present=true` ⇒ ⇒ **与冻结基线 `expected.txt` 逐字节一致** ✔
⇒ ⇒ **落点（仓内，可复现）**：`service/hot/hot-rpc/src/test/probe/redis-disconnect/`（`main.go` · `run.sh` · `expected.txt` · `README.md`）
⇒ ⇒ ⚠️ **归口**：`tools/redis-disconnect-probe/` 是**共享层**写法（`tools/**` 归 G4）⇒ ⇒ 本探针落在**域侧** ⇒ ⇒ **两份实现不要同时存在**
⇒ ⇒ **RAG 腿不重复造**：域内既有 `CommentRedisDisconnectFastRejectGateTest`（守 RAG 腿）⇒ ⇒ 本探针只守 **Go 腿 ＋ 双侧对照**
⇒ ⇒ **与 Go 侧的一致性：语义一致（两侧都「快速失败」）· 毫秒数不一致（Go ~1.7 s vs RAG 1 ms）⇒ 只主张语义那一层**

## 📌 快照 · 🔴 **G4 共享层合并（`ae218df`）后的本域回归**（2026-10-28 · G3）

**合并**：option D（**单一 merge 源 · 不 cherry-pick**）⇒ `git merge --no-ff align/g4-service-common`
（G4 @ `188e995`）⇒ ⇒ **`git merge-tree --write-tree` 预演 0 冲突** ⇒ ⇒
**G4 未触碰本域任何路径**（`git diff --stat` 空）⇒ ⇒ **本域代码零改动 ⇒ ⇒ 本域结论不受影响**。

**合并后本域 3 模块回归**（`mvn -B -o --fail-at-end -am test -pl <3 模块>`，`-DargLine=-Xmx2g`）：

| 模块 | 条数 |
|---|---|
| `service/comment/comment-rpc` | 136 |
| `service/comment/comment-api` | 52 |
| `service/comment/comment-common` | 27 |
| **本域小计** | **215** |

**全量口径**（12 域模块 ＋ `-am` 带进来的共享层）：**1387 条 / 0 failures / 0 errors / 4 skipped**
· BUILD SUCCESS · **15:30 min**（2026-10-28）
⇒ ⇒ **拆账（可复算）**：`service/common` **333** ＋ `service/users/users-common` **39** ＋ **四域合计 1015**
⇒ ⇒ **1015 + 333 + 39 = 1387**（逐字相等）。
⇒ ⇒ **本域 Redis 连接超时那条**（`CommentRpcRedisTimeoutContractTest`）⇒ ⇒ **已在 `0c6295c` 闭合**
（`connect-timeout: 5s`）⇒ ⇒ **G4 的 R6 派单是过期基线** ⇒ ⇒ 详见
[`docs/DRIFT/comment/vs-common.md`](../comment/vs-common.md) 的 **C-7**。

**门禁快照（20 条）**：**canonical 在** [`docs/DRIFT/article/rpc.md`](../article/rpc.md) 的
「**二十四、🔴 G4 共享层合并后的门禁快照（20 条）**」一节 ⇒ ⇒ **15 绿 / 4 红 / 1 用法错（EXIT=2，不是红项），四条红都不在本域**
（`check-ledger-count` = 合并收口执行者 · `check-ddl-static` = users 域 · `check-tomcat-limits` = G2 域 ·
`check-wave-coupling` = G4-9 共享矩阵）。

### 📌 指针（2026-10-28 · G3）—— G4 §4.10.64 ⑤ 的「域内 status 漂移」警告【对 comment 域不成立】

⇒ G4 在共享面写「域内 span 若只调 `recordException` ⇒ status 恒 UNSET ⇒ **与 Go 的 `RecordError` 漂移**」⇒ ⇒ **中间那一步是错的**：Go otel-go **v1.44.0** 的 `RecordError` **【不改】status**（接口契约 `go.opentelemetry.io/otel/trace@v1.44.0/span.go:47-51`「**as this method does not change the Span status**」＋ SDK 实现 `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:626-630` javadoc ＋ `:631-655` 实现**全程无 `SetStatus` 调用**）⇒ ⇒ **Go 侧只 `RecordError` 的业务 span status 本就恒 UNSET ⇒ 两侧一致 ⇒ 不漂移** ⇒ ⇒ **域侧零改动**（**不补 `setStatus`** —— 补了反而是引入 Go 侧不存在的 ERROR）。
⇒ ⚠️ **唯一需要显式 `SetStatus` 的是 `GetComment`，本域【已经逐字复刻了】**：Go `service/comment/rpc/internal/logic/get_comment_logic.go:85-86` / `:101-102` / `:110-111` / `:117-118` 四个错误分支**各自** `RecordError` ＋ `SetStatus(codes.Error, err.Error())` ⇒ ⇒ RAG `CommentSpans.tracedWithErrorStatus(...)`（**只给 `GetComment` 用**，其余走 `traced` ⇒ 无 `setStatus`）⇒ 逐条对应 ✅ ⇒ 且守门 `CommentSpanWiringTest.java:237-238`（业务失败 ⇒ **断言 `StatusCode.UNSET`**）／`:390-392`（GetComment 系统失败 ⇒ **断言 `StatusCode.ERROR`**）**两条判别式断言已把两侧 status 语义钉死** ⇒ ⇒ **网已张、语义已对齐**。
⇒ 逐条证据 ⇒ [`favorite/api.md` §16.4.1](../favorite/api.md#1641-🟢-补证2026-10-28-go-侧-recorderror-也不改-status--两侧-status-语义同构--status-漂移不成立) ＋ [`comment/vs-common.md`](./vs-common.md) A9 行。

## 十三、🟢🔴 **gRPC 真进程端口补齐到「OS 临时段之外」**（**5 处**：`forPort(0)` ×4 ＋ 裸 `new ServerSocket(0)` 死口 ×1 ⇒ 本域专用段 `22600–22699`）· 2026-10-28 10:5x · G3

**本域的机制类与 Spring 上下文形态的口**在**上一轮**已落（`support/RealGrpcPort.java` · `FIRST=22600` / `LAST=22699`）⇒ ⇒ **本节补的是剩下的两类形态**：**进程内 gRPC server**（`ServerBuilder.forPort(0)`）与**裸取临时口**（`new ServerSocket(0)`）。

#### 起因（与 article / hot 两域同款）

⚠️ **OS 临时段 `49152–65535` ＝ Docker 随机发布容器端口用的同一段** ⇒ ⇒ 别的会话的容器先占住 ⇒ ⇒ `EADDRINUSE` ⇒ ⇒ **长得像「被测行为坏了」，实为环境撞口**
⇒ ⇒ **实证（G2 lane 2026-10-28）**：`BindException: Address already in use (/0.0.0.0:49992)`（49992 = 本机 `Sea-kafka` 容器发布口 · `Up 7 天`）⇒ ⇒ **不是被测代码的错**。
⇒ ⇒ global 段表 §4.10.19.3 **早就把 `22600–22699` 预分配给 comment-rpc 域**（状态「本层预分配 · 域侧未落码」）⇒ ⇒ **本项本就是域侧自修**（AGENTS.md §5.3d）。

#### 落码（5 处全迁 ＋ 守门 +2 条）

| 项 | 值 |
|---|---|
| 迁 ① | **4 处** `ServerBuilder.forPort(0)` ⇒ `forPort(RealGrpcPort.pick())`（`CommentRpcGrpcInterceptorWiringTest` 2 ＋ `CommentRpcSlowThresholdGateTest` 2）|
| 迁 ② | **1 处** 裸 `new ServerSocket(0)`（`service/CommentCacheRedisMetricGateTest.java` 的**死口**样本：故意占一个没人听的端口 ⇒ 用来验「连不上时的行为」）⇒ ⇒ `deadPort = RealGrpcPort.pick()` |
| 守门 | **扩** `support/RealGrpcPortTest.java` ⇒ **+2 条源扫**（5 种「OS 盲选口」形态禁止清单 ＋ **守恒**：提到端口就必须引用 `RealGrpcPort`）|

⚠️ **迁 ② 为什么不能用「随便一个数」**：死口样本的作用是「**确保这个端口没人听**」⇒ ⇒ 用 `RealGrpcPort.pick()`（先 bind 自检、再返回）⇒ ⇒ **既避开 OS 临时段，又保证确实是死口** ⇒ ⇒ 比原来更准（原来 `new ServerSocket(0)` 只是「借一个临时号」，**没有保证它此刻没人听** ⇒ ⇒ 属**弱断言**）。

#### 证据强度与边界

- **L3**（源码扫描：迁后本域 `grpc.server.port=0` / `forPort(0)` / `new ServerSocket(0)` **各 0 处**）**＋ L4**（本轮真回归，见 hot 域同款「本轮回归」表）。
- ⚠️ **TOCTOU 窗口诚实标注**：`pick()` 只能**压低**概率、**不能归零** ⇒ ⇒ 本段独占给本域 ⇒ 别的域/别的会话不占这一段。
- ⚠️ **本节只治「端口从哪来」，不改「测什么」** ⇒ ⇒ 用例语义零变化。

#### 本轮回归（与 hot 域同跑）

| 项 | 值 |
|---|---|
| 第 1 跑（12:1x）| `comment-rpc` **138 / 0 / 0 / 0** ✅（`service/common` 同轮 **333 / 0 / 0 / 0**）|
| 第 2 跑（12:40）| 🔴 `comment-rpc` **编译失败**（判据改动时我误插常量 ⇒ 缺 `GRPC_PORT_KEY` / `REAL_PORT_USAGE`）⇒ ⇒ **已修** ⇒ ⇒ **12:4x 编译验证 EXIT=0**（`mvn -o -B --fail-at-end -pl … -am test-compile`）|
| 🔴 **诚实边界** | 本域**新守门判据**只到「编译通过」，**测试未重跑**（Jason 2026-10-28 插队指令：**不起回归**）⇒ ⇒ **不得宣布「comment 守门已绿」** |

#### 🟡 **口径订正（2026-10-28 · G3）**：G2 报的本域数字与本树实测**不一致**

⇒ ⇒ **G2 报的「comment = 2 处」** vs **本树实测「5 处」（本轮迁的）** ⇒ ⇒ 两边数的**树不同**（本树另有上一轮已迁的 `grpc.server.port` 形态）⇒ ⇒ ⇒ **不是谁数错，是「比的不是同一份」** ⇒ ⇒ **口径**：迁**前**以本树实测为准，迁**后**以「各形态 = 0」为准。

## 📌 追加 · 🟡 **「Go 侧无该面」这个派工前提被实测证伪（2026-10-30 · G3 第三轮）**

**派工前提与实测结论**

- 派工给的口径是「`comment-rpc` 的 Go 侧**没有**指标抓取面 ⇒ 本轮不补」。
- **实测证伪**：Go 侧 `service/comment/rpc/comment.go:38-41` **自建** `promhttp` 处理器（`http.Handle("/metrics", promhttp.Handler())`）并**自起** HTTP 服务（`http.ListenAndServe(":9091", nil)`）⇒ ⇒ **Go 的 comment-rpc 确实有指标面**，在 **`:9091` 的 `/metrics`**。
- ⇒ ⇒ **RAG 侧 comment-rpc 缺该面 = 一条真实漂移**（不是「无该面」）⇒ 已按 **C 类**登记（见 `docs/DRIFT/comment/vs-common.md`）⇒ **下一轮**补。
- ⇒ **本轮仍不落码载体**，两个理由：① 超出本轮派工范围（本轮只批了三个域的载体）；② **comment 的 Go 侧是「自建 promhttp」**，与另三域的「go-zero 内建代理」**机制不同** ⇒ 载体形态要另做判断，**不能照抄** follow-rpc。

**本轮本域的实际改动：取消口令兜底（1 处）**

- Go `service/comment/rpc/etc/comment.yaml:9` 的 DataSource 串里口令是**裸** `${POSTGRES_PASSWORD}` ⇒ go-zero 的加载 = `core/conf/config.go:63` `loader([]byte(os.ExpandEnv(string(content))), v)` ⇒ `os.ExpandEnv` **不认** `${VAR:default}` ⇒ 缺变量 ⇒ **空口令** ⇒ 连库失败 = fail-fast。
- ⇒ RAG 侧原先 `service/comment/comment-rpc/src/main/resources/application.yml:67` 的 `${POSTGRES_PASSWORD:password}`（走 Spring 的 `:default`）⇒ 缺变量时会用默认值连上去 ⇒ **RAG 比 Go 松** ⇒ 属**真漂移** ⇒ 本轮**取消兜底**。
- ⚠️ **残余差异（失败时刻不同）**：Go = 启动成功、连库时失败；RAG = 启动即失败 ⇒ 属「RAG 更严」的一侧 ⇒ 由**合并收口执行者**在合并窗口统一登记。


## 📌 追加 · ✅ **comment 域两个出向出口的 deadline 继承形态：逐出口核对（两侧同形）**（2026-10-30 · G3 第五轮）

> **一句话**：本域 `comment-rpc` 有 **2 条**出向出口，它们对「入向 deadline 载体」的**可见性不同** ——
> ⇒ 一条在 **gRPC handler 线程**（载体可见 ⇒ **继承**入向剩余预算
> ⇒ 📌 **归因（2026-10-31 订正）**：「继承」这一半是 **devh 全局客户端拦截器链**给的
> （`GoStyleOutboundDeadlineInterceptor`，`enabled` **默认 true**）⇒ **不是**域侧 `applyTo` 给的
> ⇒ 详见第六轮段末「归因订正」小节），
> ⇒ 一条在 **Kafka consumer 线程**（载体不可见 ⇒ **只有 2s 客户端封顶**）；
> ⇒ 而 **Go 侧那一条 consumer 出口的 ctx 同样没有 deadline** ⇒ ⇒ **两侧逐出口同形**。
> ⇒ ⇒ **「继承」那一半只对 handler 线程那条出口成立**；consumer 那条**不是漂移**。
> ⚠️ **销账权不在本域**（C-3 / U15 的落点是 `service/common` ＋ global E65）⇒ 本节只给**域侧事实 + 证据**，判定归 global。

### 为什么「载体在位」这一个事实不足以判定继承

共享层的载体是 **ThreadLocal**（`service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:64` 的 `DEADLINE_NANOS`），
绑定点只有 gRPC 服务端拦截器一处（`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:494` 的 `onHalfClose`
⇒ `:517` 绑 `DeadlineState` ＋ `:519` 绑 `GoStyleRequestDeadline` ⇒ 同一绝对时刻，`:311-312` 算一次）。

⇒ ⇒ **载体只在「跑 gRPC handler 的那个线程」上可见** ⇒ ⇒ 同一个域里的两条出向出口，
**只要调用线程不同，继承结论就可能相反** ⇒ ⇒ 只看「载体在位」就下「本域出向已继承」的结论，是**跨线程的越界推断**。

### 出口一 · `ArticleRpcClient`（**在 gRPC handler 线程 ⇒ 继承**）

| 项 | 证据（`文件:行号`） |
|---|---|
| RAG 调用点 | `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/ArticleRpcClient.java:71`（`GoStyleClientCallTimeout.withDeadline(stub)`）· 发起点 `:83` （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| RAG 调用链 | `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/impl/CommentServiceGrpcImpl.java:73`（`domainService.getComment(request)`）⇒ `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentDomainService.java:427`（`loadSubject`）⇒ `:431` ⇒ `:274`（`resolveSubjectFallback`）⇒ `:254`（`resolveOwnerId`）⇒ `fetchAuthorId` ⇒ ⇒ **全程在 gRPC handler 线程上** |
| Go 对照 | `service/comment/rpc/internal/logic/subject_fallback.go:12`（`resolveSubjectFallback(ctx context.Context, …)` ⇒ ctx 由 handler 传入）· `:17`（`articleRPC.GetArticle(ctx, …)` ⇒ 父 deadline 经 ctx 传播 ⇒ 到点即止） |
| 载体可见性 | ⇒ handler 线程 ⇒ 共享层 `RpcInterceptor.onHalfClose` 绑的载体**可见** ⇒ ⇒ **继承入向剩余预算**（= `min(2s, 剩余)`）｜ 📌 **2026-10-31 订正 · 归因**：继承由 **devh 全局客户端拦截器链**提供（`GoStyleOutboundDeadlineInterceptor` 第 ① 项读**同一个载体**）；域侧显式 `applyTo` 对「继承」这一半**冗余**，但它是**域侧 2s 上界计算**的输入（拦截器层 `capMillis` 默认 0 ⇒ 不提供上界）⇒ ⇒ 「2s 上界」那一半**只在域侧** |
| 当前实现的精度 | `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/GoStyleClientCallTimeout.java:90-96` 读 `GoStyleRequestDeadline.remainingMillis()` ⇒ **毫秒截断**（`service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:90-96`）⇒ 与 Go 的纳秒预算（`go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25`）**差 ≤1ms** （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |

### 出口二 · `MessageRpcClient`（**在 Kafka consumer 线程 ⇒ 不继承** —— 且 Go 侧同样不继承）

| 项 | 证据（`文件:行号`） |
|---|---|
| RAG 调用点 | `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/MessageRpcClient.java:66`（`GoStyleClientCallTimeout.withDeadline(stub)`）· 发起点 `:81` （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| RAG 调用链 | `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentAuditListener.java:249`（`messageRpcClient.sendNotification(…)`）⇒ 该类 `:99` 是 `@KafkaListener` ⇒ ⇒ **调用发生在 Kafka consumer 线程**，不是 gRPC handler 线程 |
| 载体可见性 | ⇒ consumer 线程 ⇒ 共享层 `RpcInterceptor` 绑的 ThreadLocal **不可见**（`GoStyleRequestDeadline.remainingMillis()` 返回 `-1`）⇒ ⇒ 出向**只有 2s 客户端封顶**（`service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/GoStyleClientCallTimeout.java:92-94` 的 `DEFAULT_CALL_TIMEOUT_MILLIS` 分支） （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| Go 对照 | `service/comment/rpc/internal/mqs/audit_consumer.go:101`（`l.svcCtx.MessageRpc.SendNotification(ctx, …)`）⇒ 该 `ctx` 是 **Kafka 消费 ctx**，**本身没有 deadline** ⇒ ⇒ Go 侧该出口**同样只有客户端 2s 封顶** ⇒ ⇒ **两侧同形 ⇒ 不是漂移** |
| ⚠️ 结论的形状 | 这条**不是**「RAG 少继承了」⇒ **不得**被当成漂移去「修」⇒ ⇒ 也**不得**用它去论证「载体机制在本域无效」 |

### 证据强度分层（诚实边界 · 不外推）

| 结论 | 强度 | 依据 |
|---|---|---|
| 共享层在 gRPC handler 上绑定载体 | **L4 活体** | 上一轮 favorite 域侧确认已实测（`docs/DRIFT/favorite/rpc.md` 本轮新增段）· 共享层 `RpcInterceptor.java:494`/`:517`/`:519` |
| 出口一的**调用线程**与**调用链** | **L2 源码级** | 逐字读了 `@KafkaListener`（`CommentAuditListener.java:99`）、gRPC impl（`CommentServiceGrpcImpl.java:73`）与 `CommentDomainService` 的私有方法链（`:427`/`:431`/`:274`/`:254`） |
| 出口二**不继承** | **L2 源码级 ＋ 否定结论** | 依据 = 「共享层的绑定点**只有** gRPC 拦截器一处」这一**枚举完整性**前提 ⇒ 已核 `service/common` 内 `GoStyleRequestDeadline.begin(` / `bindAbsoluteNanos(` 的全部调用点 ⇒ 无第二个绑定点 |
| ⚠️ **未覆盖** | — | 本节**不**证明「出口二在生产里真的拿到了 2s」—— 那需要活体计时；本节只证明**两侧的 deadline 形态同形**（RAG 侧 2s 封顶 ＋ Go 侧 2s 封顶） |

### 对本域台账的影响

- `docs/DRIFT/comment/vs-common.md:112`（C-3 / U15 的现行开放面标题）**已就地订正**：「等 global 统一载体」这一步**已完成** ⇒ 开放面收窄为**精度对齐** ＋ **逐出口线程差异**。
- `docs/DRIFT/comment/vs-common.md:119`（影响面格）**已就地订正**：补上「两个出口载体可见性不同 ⇒ 继承结论不同」。
- `docs/DRIFT/comment/vs-common.md:834`（C-3 行）上一轮已订正为「继承已闭合 · 残留 = 精度差 ≤1ms」⇒ **本节补充的是它未覆盖的「逐出口」维度**（该行原文只举了 `client/ArticleRpcClient.java:71` 一个出口）。
- ⚠️ **本节不销 C-3** ⇒ C-3 仍是 **P1 · 阻断阶段一**（销账权在 global）⇒ 本域只给事实。


## 📌 追加 · ✅ **本域出向 deadline 载体：纳秒精度已落码 + 守门网已补（L4 活体 + 变异转红）**（2026-10-30 · G3 第六轮）

> **一句话**：Jason 拍板「本轮做」⇒ 本域两个出向出口已从**毫秒版**改为**纳秒两段式**
> ⇒ ⇒ **精度差 ≤1ms 消除** ⇒ ⇒ 并补了一条**能真正触发载体**的守门
> ⇒ ⇒ **「载体在域内出口线程上可见」**这条**从 L2 源码级升到 L4 活体**。
> ⚠️ **2026-10-31 订正 · 这条守门证的是什么、不是什么**：
> ⇒ ⇒ **证**：① 载体在 gRPC handler 线程**真的可见**（真 wire ＋ 真共享层 `RpcInterceptor`）② **域内 2s 客户端上界的计算点**取 min 正确（纳秒精度 + 三组变异矩阵）。
> ⇒ ⇒ **不证**：「生产装配面（真 `@GrpcClient` ⇒ devh 拦截器链把 deadline 送到了出向调用）」—— 那一环仍缺活体证据
> ⇒ ⇒ ⇒ 故 **C-3 仍破的理由 = 双侧活体证据缺口**（不是「域侧真漂移」）⇒ **X-6 定级复判挂起**（归合并收口执行者统一复判）⇒ 本域**不动** X-6 / C-3 的状态标记。
> ⚠️ **销账权不在本域**（C-3 / U15 的落点是 `service/common` ＋ global E65）⇒ 本节只给**域侧事实 + 证据**。

### 落码清单（4 个文件）

| 位置 | 改动 |
|---|---|
| `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/GoStyleClientCallTimeout.java` | `withDeadline`（读 `remainingMillis()`）⇒ 改为 **`capByClientTimeout`**（读 stub 上 deadline 的**纳秒**剩余），语义 = `min(载体剩余纳秒, 2s)`；常量改名 `CLIENT_TIMEOUT_MILLIS`（对齐同款正确实现） （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/ArticleRpcClient.java:71` | 调用点改为 `GoStyleClientCallTimeout.capByClientTimeout(GoStyleRequestDeadline.applyTo(stub))` （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**） |
| `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/MessageRpcClient.java:66` | 同上 |
| `service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/client/CommentOutboundDeadlineCarrierGateTest.java` | **新增守门**（5 条用例） |

⚠️ **范式来源（同仓已有正确实现，不发明第三种写法 · AGENTS.md §3.2）**：
`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/client/GoStyleClientCallTimeout.java:89-100` （**2026-10-31 第十一轮：该域内私有副本 `GoStyleClientCallTimeout` **已删除** ⇒ 形态 = 共享层 `GoStyleOutboundDeadlineInterceptor`（devh `@GrpcGlobalClientInterceptor`）统一施加 ＋ 域侧只配 `rag.protection.outbound-deadline.cap-millis=2000`**）
（`capByClientTimeout`）＋ 同域两个调用点
（`client/ArticleRpcClient.java:56-57` · `client/UserRpcClient.java:55-56` 的 `capByClientTimeout(applyTo(stub))` 形态）⇒ 本域**照抄**。

### Go 侧的语义基准（消费点）

- **min 语义**：`go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25`
  `ctx, cancel := context.WithTimeout(ctx, t)` ⇒ 父 ctx 更早则**先到者生效** ⇒ Java 侧取 `min`。
- **纳秒精度**：同一行的 `t` 是**纳秒** ⇒ Go 侧**不**做毫秒截断 ⇒ 本域旧实现读 `remainingMillis()` ⇒ **差 ≤1ms** ⇒ 本轮消除。
- **无条件 2s**：`go-zero@v1.10.2/zrpc/internal/client.go:113-117`（装拦截器）
  ＋ `go-zero@v1.10.2/zrpc/internal/config.go:15`（`Timeout bool json:",default=true"`）
  ＋ `go-zero@v1.10.2/zrpc/config.go:31`（`Timeout int64 json:",default=2000"`）。
- **父 ctx 的 deadline 来自入向**：`go-zero@v1.10.2/zrpc/internal/serverinterceptors/timeoutinterceptor.go:34`。

### 守门网（5 条用例 · 各管一个语义维度 · 缺一条都不够）

| 用例 | 证什么 | 形态 |
|---|---|---|
| `carrierIsReallyBoundOnHandlerThreadOverRealWire` | **载体在 gRPC handler 线程真的绑上了** ＋ 客户端的 deadline **真的穿过 wire 到了服务端** | **真 socket**（`RealGrpcPort.pick()`，本域段 22600–22699）＋ 真装共享层 `RpcInterceptor.newUnaryServerInterceptor(Duration.ZERO, …)` ＋ 客户端 `withDeadlineAfter(1500ms)` |
| `outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger` | 入向预算 **> 2s** ⇒ 出向上界被压到 **2s**（客户端封顶真的生效） | 同上（客户端 `withDeadlineAfter(5000ms)`） |
| `outboundInheritsInboundBudgetWhenItIsShorter` | 入向预算 **< 2s** ⇒ 出向上界**继承入向剩余**（不是无脑套 2s） | 同上（客户端 `withDeadlineAfter(1500ms)`） |
| `outboundBudgetKeepsNanosecondPrecision` | 域内出向计算点**不截断亚毫秒** | 手动绑定已知绝对纳秒（10.9ms）⇒ 测**相对损失**（出向比载体窄了多少）⇒ 纳秒版 ≈ μs 级；毫秒版 ≈ **0.9ms** ⇒ **必然红** |
| `noCarrierStillGetsTwoSecondClientCap` | 无载体时 2s 客户端封顶**无条件在**（Go 侧是拦截器级别的） | 域内真实消费点 + 真 stub |

⚠️ **为什么必须拆成「长预算」与「短预算」两个场景**：入向预算**短于** 2s 时，
「继承入向预算」与「原样返回（不压任何 deadline）」算出来的上界**完全相同**
⇒ ⇒ 单一「短预算」场景**分辨不了**这两种实现
⇒ ⇒ 本轮实测：只留「短预算」场景时，注入「完全不压 deadline」的变异**不会让它转红**
⇒ ⇒ 拆成两个场景后该变异**精确转红**（见下面的变异矩阵）。

⚠️ **为什么第一条必须用真 socket 而不是 in-process**：in-process **不走 wire** ⇒ 没有 `grpc-timeout` 头
⇒ 服务端 `io.grpc.Context.current().getDeadline()` 读到 `null` ⇒ 载体**根本不会绑** ⇒ 用 in-process 写这条 ⇒ **必然假红**。

⚠️ **为什么载体在 handler 线程可见**（载体是 ThreadLocal ⇒ 只在绑它的线程可见）：
grpc-java 的 `ServerBuilder` **默认 executor = directExecutor**
⇒ `ServerCalls.asyncUnaryCall` 最终用 `call.getExecutor()` ⇒ **同线程** ⇒ 与生产形态同款。

#### 🔴 变异矩阵（守门非假绿 · 2026-10-30 实测）

逐个注入三种「错误实现」，看**哪些用例转红** ⇒ ⇒ 证明每个语义维度都有**专属**判据：

| 注入的变异 | 转红的用例（实测） |
|---|---|
| 纳秒 → 毫秒截断（旧实现语义） | `outboundBudgetKeepsNanosecondPrecision` |
| 完全不压 deadline（原样返回 stub） | `noCarrierStillGetsTwoSecondClientCap` ＋ `outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger` |
| 无视入向预算，永远只套 2s | `outboundBudgetKeepsNanosecondPrecision` ＋ `outboundInheritsInboundBudgetWhenItIsShorter` |

⇒ ⇒ **三个语义维度（纳秒精度 / 2s 封顶 / 继承入向）各有专属判据**
⇒ ⇒ 本守门**对被测差异有判别力**，不是「错误答案的守卫」⇒ 三个变异均已还原（仓内无残留锚点）⇒ 还原后复跑全绿。

⚠️ **稳定性**：本守门连跑 **3 次全绿**（判据 0.4ms 不抖）⇒ 不是「时红时绿」的假信号。

#### 🔴 判据设计的两处坑（已被本轮实测踩过 · 防下一个人重踩）

1. **绝对剩余值不能当判据**：本机「两次 `System.nanoTime()` 调用之间」的机器开销约 **0.3–0.5ms**
   ⇒ ⇒ 用绝对值判会**时红时绿**（假信号）⇒ 必须拿「载体当下的剩余」当基线，只测**相对损失**。
2. **第一次跑要付类加载 + JIT 的钱**（实测 ≈ **0.15ms**）⇒ ⇒ 那笔钱会混进「相对损失」
   ⇒ ⇒ 必须**先热身**（跑一遍同款路径）再测 ⇒ 否则会**误判成精度损失**。

### 证据强度（诚实边界）

| 结论 | 强度 | 依据 |
|---|---|---|
| 载体在 gRPC handler 线程真的绑上 | **L4 活体** | 真 wire ＋ 真共享层拦截器 ＋ 客户端 deadline 真的到了服务端（`carrierIsReallyBoundOnHandlerThreadOverRealWire`）|
| ⚠️ **生产装配面**（真 `@GrpcClient` ⇒ devh 拦截器链把 deadline 送到出向调用） | **未验** | 机制侧已独立 javap 复核（devh `AbstractChannelFactory:61/:88/:95` `interceptForward`）⇒ 但**没有活体运行证据** ⇒ 仍缺 ⇒ C-3 的活体证据缺口即在此处 |
| 域内出向上界是**纳秒**且无毫秒截断 | **L4 活体 ＋ 变异精确转红** | `outboundBudgetKeepsNanosecondPrecision` |
| 无载体时 2s 封顶 | **L4 活体** | `noCarrierStillGetsTwoSecondClientCap` |
| 出口二（Kafka consumer 线程）**不继承** | **L2 源码级** | 上一轮新增段的逐出口核对（线程归属靠读注解与调用链）⇒ ⚠️ **未**做活体计时 |

### 收益边界（别把这次的收益说大）

- **出口一**（`ArticleRpcClient`，gRPC handler 线程）：**收益真实存在，且是链路级的** ⇒
  旧实现 `withDeadline(stub)` 在 stub 上挂的是**毫秒** deadline ⇒ 拦截器 `effectiveCallOptions` 第 ③ 项
  「调用点自己声明的 deadline」取 min 时会看到它 ⇒ ⇒ **最终出向上界被域侧的毫秒值截短**
  ⇒ ⇒ 即使拦截器层是纳秒的，**整条链路仍是毫秒精度** ⇒ 改纳秒后链路整体纳秒 ⇒ ≤1ms 精度差消除
  ⇒ ⇒ **这条改动不是「冗余」**：拦截器层 `capMillis` 默认 0（不启用）、全仓 yml 零配置
  ⇒ ⇒ **Go 的 2000ms 客户端上界本来就只在域侧提供** ⇒ 域侧那半不可去。
- **出口二**（`MessageRpcClient`，Kafka consumer 线程）：**收益为零** ⇒ 载体在该线程**不可见** ⇒ 走的是「无载体 ⇒ 2s 封顶」分支 ⇒ 改动前后**同一条路径** ⇒ 且 Go 侧该出口的 ctx 同样无 deadline ⇒ **两侧同形，不是漂移**。

### 🔴 验证方法学发现（影响「本域此前所有不带 `-am` 的回归结论」· 2026-10-30）

- **现象**：本轮改本域代码后，`mvn -pl service/comment/comment-rpc test`（**不带 `-am`**）编译**失败**：
  `GoStyleRequestDeadline` 找不到 `remainingNanos` / `bindAbsoluteNanos` / `Scope`。
- **根因**：`com.rag:common:0.0.1-SNAPSHOT` 在**本地 m2 里的 jar 是 2026-10-29 08:08 的旧版**
  （反编译确认：只有 `begin` / `clear` / `isPresent` / `remainingMillis` / `applyTo`
  ⇒ **没有** 53bc05ec 落的 `remainingNanos` / `bindAbsoluteNanos` / `Scope`）
  ⇒ ⇒ 不带 `-am` 时 Maven **从 m2 取旧 jar** ⇒ **测的不是工作树里的共享层代码**。
- **⇒ 结论**：⚠️ **凡是验「共享层落码后行为」的回归，必须带 `-am`**（或先 `mvn install` 共享层）
  ⇒ ⇒ **本域此前几轮「12 模块全绿」的数字（含 2026-10-30 合并后那轮 1240 tests）
  ⇒ ⇒ 只能读作「旧共享层 ＋ 域内代码全绿」，**不**证明「合并后（含 53bc05ec）全绿」**
  ⇒ ⇒ 本轮已用 `-am` 重跑（结论见本轮提交说明）。
- ⚠️ **这是跨 lane 的方法学问题**（别的 lane 若也用不带 `-am` 的命令，同样中招）⇒ **已报合并收口执行者**。

## 📌 追加 · 🔴 **「继承」这一半的归因订正（2026-10-31 · G3 · 合并收口执行者纠正面之后）**

> **订的是什么**：合并收口执行者 2026-10-31 派单指出「域侧改 `applyTo(stub)` 即可继承请求 deadline」这个**根因表述不成立**，
> 并对「域侧出口首次真正继承」的**理由**提出改写建议。
> ⇒ ⇒ 本域**独立复核**（不采信转述）后的结论如下（favorite 域同款订正见 `docs/DRIFT/favorite/rpc.md` 同名小节）。

### 结论一 · 机制主张**成立**（已独立 javap 复核，不是采信转述）

- devh 3.1.0.RELEASE `net/devh/boot/grpc/client/channelfactory/AbstractChannelFactory`
  ⇒ `:61 GlobalClientInterceptorRegistry.getClientInterceptors()` ⇒ `:88 sortInterceptors(...)`
  ⇒ `:95 ClientInterceptors.interceptForward(channel, interceptors)`
  ⇒ ⇒ **全局客户端拦截器接在 channel 上**。
- `net/devh/boot/grpc/client/interceptor/AnnotationGlobalClientInterceptorConfigurer`
  ⇒ `getBeansWithAnnotation(GrpcGlobalClientInterceptor.class)`
  ⇒ ⇒ 本仓 `GoStyleOutboundDeadlineInterceptor`（`@GrpcGlobalClientInterceptor`）**会被收集**。
- 本四域 main 代码 `ManagedChannelBuilder` / `NettyChannelBuilder` **零命中**
  ⇒ 出向 stub **全部**是 `@GrpcClient` 注入 ⇒ ⇒ **无一例外**走那条被拦截的 channel。

⇒ ⇒ **域侧出向调用本来就继承请求 deadline** ⇒ ⇒ 「域侧显式 `applyTo` 是闭合 E65 的必要条件」这个根因**不成立**。

### 结论二 · 「继承由拦截器链给」**不能倒推成「以前也给」**

- `GoStyleOutboundDeadlineInterceptor.effectiveCallOptions` 第 ① 项读
  `GoStyleRequestDeadline.remainingNanos()` ⇒ **与域侧读的是同一个载体**
  ⇒ 载体缺席时返回 **-1** ⇒ `if (requestRemaining >= 0L)` **跳过**
  ⇒ ⇒ **拦截器链在没有载体时也给不了请求 deadline**。
- `git show 53bc05ec^:service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` 的 `onHalfClose`
  ⇒ `try (DeadlineState.Scope ignored = DeadlineState.bindRemainingNanos(effectiveTimeoutNanos)) { super.onHalfClose(); }`
  ⇒ ⇒ **只绑 SQL 载体，不绑出向载体** ⇒ **53bc05ec 之前载体确实缺席**。

⇒ ⇒ 「53bc05ec 之前没继承、之后才继承」**成立** ⇒ ⇒ **不建议**把它改写成「出向 deadline 本来就由 devh 拦截器链给到了」
—— 那会把正确的事实改错。**正确的理由**：「继承由 devh 拦截器链给；53bc05ec 之前载体缺席 ⇒ 拦截器链第 ① 项也拿不到 ⇒ 那时确实没继承」。

### 结论三 · 「冗余」分两半（决定域侧那份 `applyTo` 能不能去）

| 面 | 谁提供 | 域侧显式 `applyTo` 是否冗余 |
|---|---|---|
| **继承请求 deadline** | devh 全局客户端拦截器链（`enabled` **默认 true**） | **冗余**（拦截器也会给） |
| **2s 客户端上界**（Go 的 `TimeoutInterceptor(2000ms)`） | **2026-10-31 第十一轮：已从「只在域侧」改为「共享层唯一实现 ＋ 域侧只配参数」** —— 共享层 `GoStyleOutboundDeadlineInterceptor`（读 `rag.protection.outbound-deadline.cap-millis`）＋ 本域 `application.yml` 显式配 `cap-millis: 2000`；⚠️ 共享层默认 `capMillis=0` **仍然不启用** ⇒ **其它域若不配 = 出向零期限** | **不再冗余**（域内副本已删） |

⇒ ⇒ 且域侧那份 `applyTo` **不可去**：要算「`min(请求剩余, 2s)`」就得先有「请求剩余」
⇒ ⇒ `applyTo` 正是把载体剩余挂到 stub 上的那一步 ⇒ **它是域侧 2s 上界计算的输入**。

### 结论四 · 对本域台账与守门的影响

- 本域「两侧同形」「继承那一半已闭合」的**结论不变**，**只补归因**。
- 🔴 **守门证什么、不证什么**（避免与探针/共享面混引）：
  - **证**：载体在 gRPC handler 线程真的可见（L4 活体）＋ 域内 2s 上界计算点取 min 正确（L4 活体 + 三组变异矩阵）。
  - **不证**：「生产装配面（真 `@GrpcClient` ⇒ devh 拦截器链把 deadline 送到出向调用）」—— **仍缺活体证据**。
- ⇒ ⇒ **C-3 仍破的理由 = 双侧活体证据缺口**（不是「域侧真漂移」）⇒ **X-6 定级复判挂起**（归合并收口执行者）
  ⇒ ⇒ 本域**不动** X-6 / C-3 的状态标记（派生层 delta 实测 **0 行**）。
- ⚠️ 探针的 `failed_fast` / `code` 两格**是探针自己的构造**（自建裸 netty channel ⇒ 绕开 devh 装配）
  ⇒ ⇒ 那不是域侧缺陷 ⇒ 探针口径**归 global**，本域**不改探针**。

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


### lane W6

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| E66 | —（**已销账** ⇒ 无待实现项；⚠️ 原「C（阶段 2 最终工程建设项 · 落点 100% 在共享层，本域无法自修）」定级来自共享 overlay `docs/DRIFT/_cross/grading/W6.md`（E66 行）⇒ ⇒ **待收口执行者同步**（共享面 ⇒ 本域**不代改**）） | P3 | 否（不阻断阶段一） | **2026-10-31 销账** —— Go 侧 `service/comment/rpc/internal/cache/comment_subject_cache.go:76`（`GetSubjectCache(ctx context.Context, subjectID string)` ⇒ 带 ctx）＋ `:84`（`c.rdb.Get(ctx, SubjectKey(subjectID))`）＋ `:103`（`SetSubjectCache(ctx, …)`）＋ `service/comment/rpc/internal/cache/comment_invalidation_cache.go:10`（`InvalidateTargetCaches(ctx, …)`）＋ `:37`（`DeleteCommentIndexCache(ctx, …)`）＋ `:56`（`deleteKeys(ctx, …)`）⇒ go-redis 的这些调用都吃 ctx ⇒ deadline 到点缓存读写立即中止 vs RAG `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentCacheService.java:250`（`redis.opsForValue().get(...)`）＋ `:279`（`set`）＋ `:331`（`set`）＋ `:341`（`get`）＋ `:376`（`get`）＋ `:433`（`set`）＋ `:479`（`multiGet`）＋ `:528`（`set`）＋ `:619`（`delete`）⇒ ⇒ **差异已消解**：共享层的 Redis 期限载体（`GoStyleRedisDeadlineConnectionFactory.java:58` 建连前查 ＋ `GoStyleRedisDeadlineConnection.java:77` 每命令查）经 `BeanPostProcessor` 自动装配进本域 ⇒ ⇒ **预算耗尽 ⇒ 命令不下发 ⇒ 写不再发生**（原判「读与写全部零 deadline 检查 ⇒ 差异仍成立」**已失效**）。**证据强度 = A 活体**：双侧活体对拍 `tools/semantic-probe/redis-deadline/run-redis-deadline-parity.sh`（**本域独立复跑 EXIT=0**）⇒ 两侧 PROBE 行逐键相等（`form=exhausted` ⇒ `write_applied=false` / `value_after=baseline`）＋ 本域装配面活体守门 `CommentRedisDeadlineCarrierWiringGateTest`（3 例，真 Redis，3/3 绿）⇒ ⇒ **机制层 ＋ 装配面 ＋ 事务面**同形。⚠️ **残余**：共享层登记的「拨号窗口内过期」**资源层**窄窗口仍存 ⇒ 归 `docs/DRIFT/global/vs-common.md` §4.19.7（global 域写权） | 2026-10-31 |
