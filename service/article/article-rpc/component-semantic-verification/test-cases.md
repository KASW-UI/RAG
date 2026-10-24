# 验证用例集（CASE-001 … CASE-032）

> 这些是**本次验证过程中推导出的、应当存在的**用例，
> 用来证明 [`semantic-drifts.md`](semantic-drifts.md) 里的每条差异，并防止修复时回退。
>
> ⚠️ **本次未创建任何测试文件。** 下表 `现状覆盖` 一栏如实记录现有 6 个测试类 / 79 个用例的覆盖情况。
>
> 记号：
> - `✅ 已有覆盖` —— 现有用例已覆盖且期望值正确
> - `⚠️ 覆盖但期望值错` —— 现有用例覆盖了，但**锁住了错误答案**（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）
> - `❌ 零覆盖` —— 现有 79 条完全没碰

## 分层要求（AGENTS.md §3.3）

- **纯 Mockito 单测**不执行 SQL、不装配 Spring 上下文 ⇒ 测不到 `@Transactional`、
  typeHandler、`readOnly`、`FOR UPDATE`、PG 触发器。
- 涉及事务 / jsonb / 触发器 / 行锁 / 排序 / 可见性谓词的用例 ⇒ **必须真 PG（Testcontainers）**。
- 涉及 Kafka 位点契约的用例 ⇒ **必须真 Kafka（Testcontainers）**，且**双侧对拍**。

---

## A. RPC 服务端

| CASE | 维度 | 场景 | 断言 | 现状覆盖 |
|---|---|---|---|---|
| CASE-001 | 正常 | `CreateArticle` 全链路（真 PG + stub MinIO + 真 Kafka） | 返回 article_id；`article` 行 status=REVIEWING；`ext_info` 8 键；MinIO 存在 `<id>.md` 且 `ContentType=text/markdown` | ❌ 零覆盖（现有 create 用例为 Mockito） |
| CASE-002 | 正常 | `GetArticle` 作者视角，requester=作者，incr=false | `markdown_content` = MinIO 真实内容；`status` = 真实值 | ❌ |
| CASE-003 | 空 | `GetArticle` 作者视角，`ext_info` 为空 map | protobuf **不输出** ext_info 字段（Go `cloneStringMap` 返回 nil） | ⚠️ `ArticleDomainServiceSemanticsTest` 覆盖字段来源，未覆盖空 map |
| CASE-004 | 不存在 | `GetArticle` 作者视角，id 不存在 | `NOT_FOUND` + `"article not found"` | ✅ `:129` |
| CASE-005 | 非法组合 | `GetArticle`：`requester_id != ""` 且 `incr_view=true` | `INVALID_ARGUMENT` + `"author read cannot increment view"`，且**未触碰 DB** | ✅ `:83` |
| CASE-006 | 分支顺序 | `GetArticle`：`public_only=true` **且** `requester_id != ""` **且** `incr_view=true` | 走公开分支，**不**返回 INVALID_ARGUMENT | ✅ `:96` |
| CASE-007 | 鉴权 | `GetArticle`：`requester_id != ""` 但非作者 | `PERMISSION_DENIED` + `"article author differs"`，且**未读 MinIO** | ✅ `:114` |
| CASE-008 | 部分失败 | `GetArticle` 作者视角，`incr_view=true` 且 DB 更新失败 | **仍返回成功**（best-effort）；`minio_request_errors_total{get}` **不增长**（O-3） | ❌ 零覆盖 |
| CASE-009 | **回滚** | `GetArticle` **公开**视角 `incr_view=true`，真 PG + **真 Spring 代理** | 不抛异常；`view_count` 真的 +1（S-1 的回归守卫） | ❌ **零覆盖**（ViewTest 全 Mockito；两个 Testcontainers 类都没调过 `getPublicArticle(.., true)`） |
| CASE-010 | 正常 | `GetArticle` 公开视角，有 revision（真 PG） | **零次**对象存储调用；正文 = `revision.markdown`；`update_time` = `published_at` | ✅ `:86`（Mockito，可靠性不足以证伪「真的不读 MinIO」） |
| CASE-011 | legacy | `GetArticle` 公开视角，PUBLISHED + 无指针行 | 正文回落 MinIO；`ext_info = {publication_gap: legacy_revision_missing}` | ✅ `:119` |
| CASE-012 | 依赖失败 | `GetArticle` 公开视角，MinIO 读失败（legacy 路径） | `INTERNAL` + `"read legacy article failed"`（两条失败文案同一） | ❌ |
| CASE-013 | **并发** | `GetArticle` 公开视角，`incr_view=true` 与并发 retract 竞态 | `incrPublicViewCount` 返回 0 ⇒ `NOT_FOUND` + `"article no longer published"` | ❌（`ArticleTransactionWiringTest:329-353` 只验 mapper 层，未验 service 层的竞态分支） |
| CASE-014 | 正常 | `UpdateArticle` 分支 A（已发布 + 显式降级 + 同时改内容） | 走 **A**（retract）而非 B；`status = requestedStatus`；指针转 `retracted`；新增 1 条域事件 | ✅ `:170`（Mockito，未验真 PG 的域事件/指针） |
| CASE-015 | 正常 | `UpdateArticle` 分支 B（已发布 + 仅改内容） | `status=REVIEWING`；`last_sync_event_id` 被写为 **`""`**（幂等判据重新可用）；审核消息已发 | ✅ `:188,204` |
| CASE-016 | 重复调用 | `UpdateArticle` 分支 C（已 REVIEWING 且无源变更） | **不**重新入队审核 | ✅ `:218` |
| CASE-017 | State Semantics | `UpdateArticle` 改正文 | MinIO 新对象名 = `<path><id>-<sha256hex(md)>.md`；**旧对象不被覆盖** | ✅ `:228,241` |
| CASE-018 | 不存在 | `UpdateArticle` / `DeleteArticle`，id 不存在 | gRPC code = `UNKNOWN`（**不是** NOT_FOUND）；description 两侧逐字比对（O-1） | ❌ |
| CASE-019 | 鉴权 | `DeleteArticle`，`operator_id=""` | `PERMISSION_DENIED` + `"forbidden"`，与「非作者」**完全同一** | ✅ `:258` |
| CASE-020 | 重复调用 | `DeleteArticle` 已删文章 | 第二次不再产生 retract 域事件（域事件计数不变） | ❌ 零覆盖（Go 侧 `community_article_test.go:306-309` 有断言） |
| CASE-021 | **并发** | `UpdateArticle` 分支 A 与 `DeleteArticle` 同时作用于同一 article（真 PG，两连接） | 两侧都完成，**无死锁**；最终状态一致（U-2 的回归守卫） | ❌ |
| CASE-022 | **排序** | `ListArticles` 作者视角：`sort_by=""`、`desc=false`、3 条不同 `created_at` | 顺序为 **created_at DESC**（S-1…**S-2** 的回归守卫） | ❌ **零覆盖**（`ArticleDomainServiceSemanticsTest:296,311,317` 全 `any()/anyBoolean()`） |
| CASE-023 | 排序 | `ListArticles` 作者视角：`sort_by="view_count"`、`desc=true` | 按 `view_count DESC` | ❌ |
| CASE-024 | 排序 | `ListArticles` **公开**视角：`sort_by=""`、`desc=false` | `created_at ASC, id ASC`（公开路径**不受 S-2 影响**） | ❌ |
| CASE-025 | Pagination | `ListArticles` 作者视角 `page=0` | offset 夹 0；响应 `page=0`、`page_size` 原样 | ✅ `:308` |
| CASE-026 | Pagination | `ListArticles` 公开视角 `page_size=500` / `=0` | 回显 100 / 20 | ✅ `:208` |
| CASE-027 | 序列化 | `ListArticles` 作者视角 | 每条 `markdown_content` = **对象 key**（Go 的既有不一致，照搬） | ❌ |
| CASE-028 | 正常 | `UploadFile`，`.png` | 对象名 `<imagePath><id>.png`；`Cache-Control: public, max-age=31536000, immutable`；URL 三段拼接 | ✅ `:337` |
| CASE-029 | 空 | `UploadFile`，`file_name="noext"` | `Content-Type: application/octet-stream` | ✅ `:355` |

---

## B. MQ 消费者

| CASE | 维度 | 场景 | 断言 | 现状覆盖 |
|---|---|---|---|---|
| CASE-030 | **Retry 契约** | 真 Kafka：投一条必然失败的消息，随后正常处理一条 | 提交位点的集合；与 Go `ForceCommit=true` 下**同一断言**（S-3 的回归守卫） | ⚠️ `ArticleSpringContextWiringTest:213-242` 只断言 `isSeekAfterError()==false`，**锁错了方向** |
| CASE-031 | **并发度** | 真 Kafka：灌 N 条慢消息 | 同时在飞的消息数 = **8**（S-4 的回归守卫） | ❌ 零覆盖 |
| CASE-032 | 依赖失败 | 审核消费者：content security 不可用 | 抛异常 → 消息不推进；**不得**判定为「不是广告」而放行 | ⚠️ `ArticleSpringContextWiringTest:248-266` 锁住了「兜底实现」这一**未完成态** |
| CASE-033 | Error Identity | 审核消费者：security 返回 `Success=false` | **抛错重投**，不得当作通过 | ❌ 零覆盖（且 `ContentSecurityPort` 无 success 字段，见 S-5） |
| CASE-034 | 重复调用 | 审核消费者：同一条消息连投 2 次 | 只入队 1 条 outbox（事务外幂等 + 事务内幂等两层） | ⚠️ `:143` 只覆盖了幂等**判定**，未覆盖并发两层 |
| CASE-035 | 正常 | 审核消费者：正文含图片 + 封面 | 图片按顺序送审，封面在**最后**；dataURI 为 `data:<mime>;base64,…`；非 png/webp 一律 `image/jpeg` | ✅ `:329,341,371` |
| CASE-036 | 并发 | 同步结果消费者：两条结果消息并发到达 | 只有一个 revision 被写入；`max(revision)+1` 不撞主键 | ❌ |
| CASE-037 | 重放 | 同步结果消费者：重放一条已发布的成功结果 | 无任何写入（判定链 ② 命中） | ❌ |
| CASE-038 | 重放 | 同步结果消费者：迟到且 `version_ms` 不符的结果 | 无任何写入（判定链 ② 命中） | ❌ |
| CASE-039 | 回滚 | 同步结果消费者：域事件写失败（注入触发器） | revision / 指针 / 状态三者全部回滚 | ✅ `ArticleTransactionWiringTest:170-228`（真 PG） |
| CASE-040 | 序列化 | 通知 `extra.author_id` | 与 DB 原串**逐字一致**（含前导零场景）（O-4） | ❌ |

---

## C. MQ 生产 / outbox

| CASE | 维度 | 场景 | 断言 | 现状覆盖 |
|---|---|---|---|---|
| CASE-041 | 正常 | relay 一轮 | 推送 key = `aggregate_id`；value = payload **原文**（与 DB 里那字节一致） | ❌ |
| CASE-042 | 部分失败 | 一批里第 2 条推送失败 | 第 1、3 条仍被 `markSent`；第 2 条 `markFailed` 且 `retry_count+1`；不中断整批 | ❌ |
| CASE-043 | Retry 上限 | `retry_count = maxRetry` | 该事件**不再被捞取**（永久搁置，不进死信、不告警） | ❌ |
| CASE-044 | Configuration | `poll-interval-ms=0` / `batch-size=0` | 回落 1000ms / 100（不是启动报错） | ❌ |

---

## D. 对象存储

| CASE | 维度 | 场景 | 断言 | 现状覆盖 |
|---|---|---|---|---|
| CASE-045 | Lifecycle | bucket 已存在 | **不**重设 policy（与 Go 的空分支一致） | ❌ |
| CASE-046 | Lifecycle | `MakeBucket` 失败且 `BucketExists` 也失败 | 只记日志，**不阻止启动** | ❌ |
| CASE-047 | 依赖失败 | `statObject` 返回 404 | 与 Go minio-go 一致，在**同一失败时点**抛错 | ❌ |

---

## E. 持久化 / Repository

| CASE | 维度 | 场景 | 断言 | 现状覆盖 |
|---|---|---|---|---|
| CASE-048 | 序列化 | jsonb 往返：含引号 / 中文 / 特殊字符的 `secondary_tags` 与 `ext_info` | 保真；列类型 = `jsonb` | ✅ `ArticleTransactionWiringTest:265-301`（真 PG） |
| CASE-049 | Lifecycle | 自定义 `@Select` 覆盖的每个方法 | `ext_info` **非 null**（typeHandler 生效；静默退化为 null 是本域最危险的缺陷） | ⚠️ 只覆盖 `selectById`（`:184,186`），其余 5 个自定义查询未逐个验 |
| CASE-050 | State Semantics | 软删可见性 | `selectById` 看不到；`selectByIdUnscoped` 看得到；二次 `softDelete` 影响 0 行 | ✅ `ArticleTransactionWiringTest:307-325` |
| CASE-051 | Lifecycle | 不可变触发器 | `UPDATE` / `DELETE article_revision` 均失败且内容不变 | ✅ `ArticleTransactionWiringTest:133-151` |
| CASE-052 | Concurrency | `selectByIdForUpdate` 双连接 | 第二个连接阻塞直到第一个提交 | ❌ |
| CASE-053 | State Semantics | 公开可见性谓词的 select 版与 update 版 | 二者**判定集合完全相同** | ❌ |
| CASE-054 | Lifecycle | 三模块共用一本 `flyway_schema_history` | 按序启动后 13 张表全部存在（O-5） | ❌ |

---

## F. 配置与生命周期

| CASE | 维度 | 场景 | 断言 | 现状覆盖 |
|---|---|---|---|---|
| CASE-055 | Lifecycle | 缺 `article_revision_immutable` 触发器 | **启动失败**（Go 是 panic） | ✅ `ArticleTransactionWiringTest:125-130` 只断言触发器**存在**；**未验「缺失 ⇒ 拒绝启动」** |
| CASE-056 | Timeout Scope | 真 PG + 调小 `observability.rpc-timeout` | DB 调用超时 ⇒ gRPC code = `DEADLINE_EXCEEDED`（**不是** UNKNOWN）（S-6） | ❌ |
| CASE-057 | Timeout Scope | message-rpc 挂起 | 调用在 **2s** 返回（Go 默认 2000ms，非 3s）（O-6） | ❌ |
| CASE-058 | Configuration | `spring.jackson.time-zone` | 该键当前**不生效**（自定义 `ObjectMapper` 绕过 Boot 绑定）（U-4） | ❌ |
| CASE-059 | Lifecycle | gRPC `ServerInterceptor` | 真正挂在 server 上（不止 Bean 存在） | ✅ `ArticleSpringContextWiringTest:151-166` |

---

## G. 指标

| CASE | 维度 | 场景 | 断言 | 现状覆盖 |
|---|---|---|---|---|
| CASE-060 | Serialization | `/actuator/prometheus` 输出 | 6 个指标全名与 Go 一致（O-2） | ❌ |
| CASE-061 | State Semantics | 全量走一遍 create/update/delete/review/publish | `article_status_total` **只有** `reviewing` 有值（Go 侧 published/rejected 无调用点，**不得补全**） | ❌ |
| CASE-062 | 序列化 | `kafka_push_errors_total` 的 `topic` 标签取值 | `article_review` / `article_sync_event`（用途名，**不是真 topic**） | ❌ |

---

## 优先级建议（若后续要补测试）

| 优先 | CASE | 守护的漂移 |
|---|---|---|
| **P0** | 009 | **S-1**（公开读必失败） |
| **P0** | 022 | **S-2**（默认列表顺序反转） |
| **P0** | 030 / 031 | **S-3 / S-4**（Kafka 契约与并发度） |
| **P0** | 033 | **S-5**（security 端口无 success 语义） |
| **P1** | 056 | **S-6**（超时码降级） |
| **P1** | 054 | **O-5**（Flyway 账本） |
| **P1** | 018 / 008 | **O-1 / O-3** |
| **P2** | 057 / 060 / 040 | **O-6 / O-2 / O-4** |
| **P2** | 020 / 021 / 036 / 052 | 重复调用 / 并发 / 回滚（Go 侧有断言，Java 侧无） |
| **P3** | 049 / 053 / 055 / 058 / 061 / 062 | 逐查询 typeHandler、谓词对偶、闸门反证、死配置、埋点完整性 |