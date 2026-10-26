# global 第二十轮 —— A5′ 落地：`like` 域事实投递（对齐 Go `fact_delivery.go`）＋ 共享层 `Dispatcher.runAsync`

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`76e227b`
> **性质**：**功能缺口补齐（X-4 的 like 半边）＋ 共享层新增非阻塞 API ＋ 真 PG 守门 + 真启动守门 + 变异测试**
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（**零改动**；共享层只**新增**方法 ⇒ favorite 现有行为**逐字节不变**）

---

## 一、要解决什么

`docs/DRIFT/global/cross-domain.md` §二·十（X-4，第九轮确证 / 第十九轮末定性改判）：

| | 内容 |
|---|---|
| **Go** | `service/like/rpc/internal/model/fact_delivery.go:60-127` 的 `DispatchFactOnce` ＋ `:23-50` 的 `validLikeDelivery`；`service/like/rpc/cmd/fact-dispatch/main.go:54-77` 的进程循环 |
| **RAG（改前）** | `like-rpc` 侧**三个 4 行骨架**：`LikeFactDispatchService` / `LikeFactDispatchScheduler` / `LikeDeliveryValidator` ⇒ **完全没有投递能力** |
| **第十九轮末的定性** | 共享层 `Dispatcher` **已按 Go 形状实现**（批次 16 / 节奏 1s / 单次 10s / 单条失败即 break）⇒ **不存在「ticker 语义漂移」**；**真缺的只有投递那一段** ⇒ **功能缺口，不能签字认账** ⇒ 本轮补 |

⚠️ **为什么不能照抄 comment 域那份**：两域的 Go 判据**不同** ——
comment 要求「载荷 `comment_id` == 信封 `aggregate_id`」，
而 **like 的载荷 `aggregate_id`（`targetType/targetID`）与信封 `aggregate_id`（`like-state/{user}/{type}/{id}`）本来就不相等**
（Go `domain_fact.go:93` vs `:102`）⇒ 照抄会把**合法事实判成 BLOCKED**（功能自毁）⇒ 守门第 ② 条专门钉住这条。

---

## 二、Go 出处（逐条）

| Go 位置 | 语义 | RAG 落点 |
|---|---|---|
| `fact_delivery.go:57-61` | `tx := db.Begin()` ＋ `defer tx.Rollback()` | `TransactionTemplate`（**编程式事务**，见下） |
| `fact_delivery.go:63-69` | 认领：`FOR UPDATE SKIP LOCKED` ＋ **前序屏障** `NOT EXISTS` | `LikeDomainFactOutboxMapper#claimNextDeliverable` |
| `fact_delivery.go:70-72` | 队列空 ⇒ `(false, nil)`（**不是错误**） | `DispatchOutcome.EMPTY` ⇒ 返回 `false`、**不抛** |
| `fact_delivery.go:77-89` | 校验失败 ⇒ `delivery_status=3`（BLOCKED）＋ **`tx.Commit()`** ＋ `return ErrFrozenEnvelopeBlocked` | `markBlocked` ＋ **提交之后**才抛 |
| `fact_delivery.go:92` | `sender.Send(ctx, event, json.RawMessage(*row.DeliveryEnvelope))` ⇒ 发**信封列原始字节** | `rawBytes(row.getDeliveryEnvelope())` |
| `fact_delivery.go:98-110` | 发送失败/回执不匹配 ⇒ `status=2` ＋ `retry_count+1` ＋ **Commit** ＋ `return false, sendErr`（**原样交出**） | `markDeliveryFailed` ＋ `DeliveryDeferredException(cause)` |
| `fact_delivery.go:111-120` | 成功 ⇒ `status=1` ＋ 5 个回执列 ＋ `delivered_at = time.Now().UTC()`（**应用时钟**、**在 send 之后**取） | `markDelivered(..., receivedAt, deliveredAt)` —— `delivered_at` **由调用方传参** |
| `fact_delivery.go:23-30` | 信封 ⇔ 行身份一致（含 `event.AggregateVersion == *row.FactVersion`） | `LikeDeliveryValidator#isValidDelivery` ② |
| `fact_delivery.go:31-34` | `occurred_at` 必须能按 RFC3339Nano 解析 | `parseGoRfc3339` |
| `fact_delivery.go:36-43` | `StrictDecode` 载荷 ＋ 载荷 ⇔ 信封交叉校验（**`payload.AggregateVersion` 必须为 nil**） | `LikeFact`（**19 字段**）＋ `STRICT_MAPPER` |
| `fact_delivery.go:44-49` | 载荷 JCS 规范化哈希 == 行 `payload` 列的 JCS 规范化哈希 | `Wire.canonicalHash` 双向 |
| `domain_fact.go:54-74` | `likeFact` 的**字段集合**（19 个） | `LikeDeliveryValidator.LikeFact` |
| `domain_fact.go:124-129` vs `:133-136` | 信封 `AggregateVersion` 与行 `FactVersion` 取**同一个** `stream.Version` | 守门夹具自检（见 §五·三） |
| `cmd/fact-dispatch/main.go:58` | `endpoint == "" \|\| len(token) < 32` ⇒ **进程退出** | 告警后**不启动**（单 JVM 不能退出） |
| `cmd/fact-dispatch/main.go:67/71/74/75-79` | 节奏 1s / 单次 10s / 批次 16 / 单条失败即 break | **共享层 `Dispatcher`**（本轮只加 `runAsync`，不改这四项） |

---

## 三、改了什么

### ① `service/like/like-rpc`（本域落地）

| 文件 | 改动 |
|---|---|
| `service/LikeDeliveryValidator.java` | **新实现**：`validLikeDelivery` 逐条照抄 ＋ 定型 `LikeFact`（19 字段）＋ 自带**严格** mapper（禁未知字段 / 禁标量强转 / 禁尾部 JSON）＋ `parseGoRfc3339` |
| `service/LikeFactDispatchService.java` | **新实现**：`implements Dispatcher.DispatcherImpl`；`TransactionTemplate` 编程式事务；四条出口 EMPTY / BLOCKED / FAILED / SENT；**错误在提交之后才抛** |
| `mq/LikeFactDispatchScheduler.java` | **新实现**：`@PostConstruct`/`@PreDestroy`；默认关闭；`dc-endpoint` 非空 ＋ `dc-token ≥32` 才启动 |
| `mapper/LikeDomainFactOutboxMapper.java` | `markDelivered` 加 `deliveredAt` 参数（`delivered_at` 从 SQL `now()` 改为**传参** ⇒ Go 是应用时钟） |
| `config/LikeRpcProperties.java` | `Fact` 加 `once`（默认 false，对齐 comment / favorite） |
| `resources/application.yml` | `like.fact.once: false` |

### ② 🔴 `service/common`（共享层，global 独占写权）

**新增** `Dispatcher.runAsync(...)`：**非阻塞**启动投递循环（循环跑在守护线程 `communityfact-dispatcher` 上，句柄在**进入循环之前**发布 ⇒ 调用方能立刻 `stop()`）。

🔴 **为什么必须新增而不是改 `run`**：
`Dispatcher.run` 是**阻塞式**的（循环跑在**调用线程**上，直到 `stop()` 才返回），
而业务域的调用点都在**生命周期回调**里：

| 调用点 | 阻塞的是 | 症状 |
|---|---|---|
| `@PostConstruct start()` | **Spring 上下文刷新线程** | 上下文永远刷新不完 ⇒ **整个服务起不来** |
| `@EventListener(ApplicationReadyEvent.class)` | **`SpringApplication.run()` 主线程** | 进程启动后主线程永久占用 |

⇒ `run` 的**阻塞语义刻意保持不变**（它对应 Go 独立进程形态）⇒ **favorite 现有行为逐字节不变** ⇒ 零触碰。
⇒ like / comment 两个调度器改调 `runAsync`。

### ③ `service/comment/comment-rpc`（同款缺陷，顺手修）

`CommentFactDispatchScheduler` 同样在 `@PostConstruct` 里调**阻塞式** `Dispatcher.run` ⇒ 同款启动阻塞 ⇒ 一并切到 `runAsync`。

---

## 四、守门

| 守门 | 形态 | 条数 |
|---|---|---|
| `LikeFactDeliveryGoSemanticsGateTest` | **真 PG 16（Testcontainers）+ 真 MyBatis（`SqlSessionTemplate` ＋ `SpringManagedTransactionFactory`）+ 真 `DataSourceTransactionManager`**；独立账本 `flyway_schema_history_like_fact_gate` | **14** |
| `LikeFactDispatchSchedulerStartupTest` | **真 Spring 上下文**（`AnnotationConfigApplicationContext` ⇒ `@PostConstruct`/`@PreDestroy` 真的跑）＋ `refresh()` 放**独立线程** ＋ **超时** | **5** |
| `LikeFactDispatchContractGateTest` | **真 PG 16 + 真 `com.sun.net.httpserver.HttpServer` 扮演 DC**（**不 mock**）＋ 共享层 `Dispatcher` 真实驱动 | **3** |

⇒ **合计 22 条**，全部落在「真 PG / 真 Spring 上下文 / 真 HTTP」上 ⇒ **零 mock**。

⚠️ **为什么必须真启动**：启动阻塞这类缺陷**「开关默认关闭」时完全测不出来**（测试全绿，真启动就挂）⇒
只有「真起一个 Spring 上下文 ＋ 把 `refresh()` 放进独立线程并超时」才能抓住。

---

## 五、变异（**每条必须红在目标断言上**）

见 §六 的表。

### 🔴 三条本轮踩到的坑

1. **夹具 bug 伪装成实现 bug** —— 首跑 1 条红，报错还挂在**另一个测试方法**上：
   `Wire.ERR_FROZEN_ENVELOPE_BLOCKED` 是**静态单例**，它的栈是**类初始化时**捕获的**陈旧栈**
   ⇒ 报错指向**第一次**触发它的那条用例。
   真因：夹具给版本 2 的事实写死了信封 `aggregate_version:1`，而行 `fact_version=2`
   ⇒ Go 的 `fact_delivery.go:27` 会判 BLOCKED ⇒ **实现是对的、夹具是错的**
   ⇒ 修法 = 夹具让 `aggregate_version` 跟着 `fact_version` 走 ＋ **加一条夹具自检断言**（防再犯）。
2. **`Dispatcher.run` 的阻塞性**：第一版调度器守门直接用 `Dispatcher.run` ⇒ 上下文刷新被堵 ⇒ 改 `runAsync`。
3. **构建进行中改源码 ⇒ 绿的是旧字节码**（§PIT-GLOBAL-019 第 4 种形态，本轮再次撞上）：改完必须**重跑**。

### 🔴 四条变异测试本身踩到的坑（第二轮：守门与变异**互相**咬出）

4. **「守门空转」与「变异无效」是两件事**（M8 / M14 首版都踩）：
   - **M8**：删掉 `.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` 什么也没改变
     ⇒ **Jackson 该特性默认就是 true** ⇒ **变异无效**，守门是好的
     ⇒ 真正能咬住的是 `.disable(...)`。
   - **M14**：删掉失败分支的 `break;` 什么也没改变
     ⇒ Go `process.go:46-53` 的内层循环有**两个** break（`err != nil` 与 `!sent`），
       而失败路径上 `sent` 恒为 false ⇒ **单删任一个都不改变行为** ⇒ 必须**复合变异**。
   ⇒ 报「守门空转」之前，先确认**变异真的改变了行为**。
5. **判别点选错 ⇒ 守门测的不是被测语义**（M14）：原断言「后面那条一次都没被试」对**有无 break 都成立**
   ⇒ 改成「**同一轮内试了几次**」（Go 的 break 打的是内层 16 次批次）。
6. **排序天然掩盖被测语义**（M9）：两条事实先后插入 ⇒ 低版本 `created_at` 更早 ⇒ 拿掉屏障结果一样
   ⇒ 把高版本 `created_at` **提前** ＋ 加**夹具自检断言**。
7. **「等条件成立」超时静默返回 ⇒ 后续断言空转通过**：`awaitTrue` 超时**即抛**。

---

## 六、变异结果表

**14 条变异全部转红（14/14），6 个被改文件全部还原。**

| 变异 | 改了什么 | 期望红在 | 结果 | 连带红 |
|---|---|---|---|---|
| M1 | 去掉 `markBlocked`（`LikeFactDispatchService.java:140`） | `blockedFrozenEnvelopeIsPersistedEvenThoughTheCallThrows` | ✅ | 6 |
| M2 | 去掉 `markDeliveryFailed`（`:173`） | `failedDeliveryIsPersistedWithRetryCountEvenThoughTheCallThrows` | ✅ | 0 |
| M3 | BLOCKED 分支在事务内直接抛（`:145`） | 同 M1 | ✅ | 6 |
| M4 | `delivered_at` 改回 SQL `now()`（mapper `:141`） | `deliveredAtIsTakenAfterTheSendNotAtTransactionStart` | ✅ | 0 |
| M5 | 去掉 `payload.aggregateVersion != null`（validator `:208`） | `payloadWithAggregateVersionIsBlocked` | ✅ | 0 |
| M6 | **加上** comment 那条 aggregate_id 判据（validator `:209`） | `payloadAggregateIdMayDifferFromEnvelopeAggregateId` | ✅ | 5（过强） |
| M7 | 去掉三个时间字段的 RFC3339 校验（validator `:230`） | `payloadWithMalformedEventTimeIsBlocked` | ✅ | 1 |
| M8 | `.enable(FAIL_ON_UNKNOWN_PROPERTIES)` → `.disable`（validator `:84`） | `payloadWithUnknownFieldIsBlocked` | ✅ | 0 |
| M9 | 去掉 `NOT EXISTS` 前序屏障（mapper `:96`） | `laterVersionIsBlockedByUnacceptedPredecessor` | ✅ | 0 |
| M10 | `runAsync` → 阻塞式 `run`（scheduler `:104`） | `enabledSchedulerDoesNotBlockContextRefresh` | ✅ | 1 |
| M11 | 去掉 `@PreDestroy` | `enabledSchedulerRunsInBackgroundAndStopsOnClose` | ✅ | 2 |
| M12 | `dispatchEnabled` 默认改 `true`（props `:118`） | `defaultConfigurationKeepsDispatchDisabled` | ✅ | 0 |
| M13 | 批次 16 → 64（`Dispatcher.java:258`） | `dispatcherDeliversSixteenPerRoundWithOneSecondTick` | ✅ | 0 |
| **M14** | 去掉失败分支的 `break` **＋** 失败路径报 `sent=true`（`Dispatcher.java:269` ＋ `:209`）⇒ **复合变异** | `failedDeliveryBreaksTheInnerBatchImmediately` | ✅ | 0 |

⚠️ **M8 / M14 首版是「无效变异」而不是「守门空转」**：
M8 删掉 `.enable(...)` 无效（Jackson 该特性**默认就是 true**）；
M14 单点删 `break` 无效（两个 break 共同保证「失败即 break」，错误路径上 `sent` 恒 false）。
⇒ 报「守门空转」之前，**先确认变异真的改变了行为**。

⚠️ **判定口径**：目标 testcase **变红**即可（`failure` 或 `error` 都算）；
**连带红数**必须记录（过强变异会连带带红别的用例）。

⇒ 逐条论证、证据行号与未证成部分 ⇒ [`docs/DRIFT/global/lanes/L1-delivery.md`](../../docs/DRIFT/global/lanes/L1-delivery.md) §8。

---

## 七、范围外 / 记账（**不静默**）

| # | 事项 | 性质 | 处置 |
|---|---|---|---|
| ① | `LikeAuthorityEndpointConfig`（**DC → like 的入向权威读端点**） | 仍是**未实现骨架**；自带说明「监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector（**wire 面变更 ⇒ 待拍板**）」 | ⛔ 本轮**不碰**（属 wire 面变更 ⇒ 需用户拍板）⇒ **记账** |
| ② | `favorite` 域的 `FavoriteFactDispatchScheduler` | **同类缺陷**（阻塞式 `Dispatcher.run`），但**触发点不同 ⇒ 症状不同**：favorite 是 `@EventListener(ApplicationReadyEvent)`（`:77`）⇒ 上下文已刷新、**能服务**，但 `SpringApplication.run()` **永不返回**（主线程被占住）；like/comment 改前是 `@PostConstruct` ⇒ **起不来** | ⛔ **favorite 零触碰（本轮 0 个文件）** ⇒ **记账 + 明确告知 owner**（修法：改调已就位的 `Dispatcher.runAsync`） |
| ③ | `article` 域 | Go 侧 `article` **没有**事实投递（`communityfact` 只被 like / comment / favorite 用） | 无需处理 |
| ④ | **变异脚本落仓**（`tools/difftest/like-mutation.py`） | `tools/` **不在 L1 白名单**（L3 写 `ddl*`、L4 写 `flyway*`）⇒ 本轮只留在会话临时目录 | ⇒ **交接整合者**；§六 的表已把 14 条变异的**锚点与期望红点**写全 ⇒ 可据此重建 |
| ⑤ | 本轮 4 条新坑（变异测试与守门互相咬出） | 属 `docs/Pitfalls/global/**` ⇒ 在 **L5** 白名单 | ⇒ **交接 L5**（明细见 `docs/DRIFT/global/lanes/L1-delivery.md` §8.5） |

---

## 八、证据边界（**不声称已证明的**）

1. ⚠️ **Go 侧未做活体对拍** —— 本轮的 Go 事实全部来自**逐行读源码**（`fact_delivery.go` / `domain_fact.go` / `main.go`），**不是**真跑 Go 进程投递。
2. ⚠️ **DC 侧（仓外平台）未接触** —— 回执的形状由 `Wire.validateReceipt` 与 Go 的 `ValidateReceipt` 逐条对照判定，**没有**真调 DC。
3. ⚠️ **「投递耗时」这一维只验了应用时钟 vs 事务开始时刻**（用 2 秒慢 sender 区分）⇒ **应用与数据库时钟漂移**这一维**在本环境不可观测**（同机同钟）⇒ 未声称已覆盖。
4. ⚠️ **多副本并发投递未验** —— `SKIP LOCKED` ＋ 前序屏障的**并发**语义靠**单实例**测试覆盖；真正的多进程并发形态仍未验证（与 X-1「三进程并发」同属未验项）。
5. ⚠️ **投递的「端到端」仍未验** —— 本轮验的是「事实 → 冻结信封 → 校验 → 投递调用 → 回执落库」这一段，**不含** DC 真实接收。
