# L1 · `align/global`（本体）—— X-4 like 半边（社区事实投递链路）

> 本文件既是**任务书**也是本 lane 的**台账片段**：开工前读它，做完把结论追加在本文件末尾。
> 协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
>
> ⚠️ **本 lane 就是 `align/global` 本体**（工作树 `../RAG.worktrees/global`）——
> **已有一个会话在上面跑**，且它是 **L2~L5 的集成目标分支** ⇒
> ① **不要另开 `align/global-delivery`**（重复劳动）；
> ② **提交前只 `git add` 自己改过的文件**，⛔ **不要 `git add -A`**（会扫进别人的在途编辑，见 PIT-GLOBAL-020）。

## 一、目标

把 `global` 域待办 **A5 + B4** 做完：
RAG 的 like 域**另起炉灶**写了一套 outbox + stream + scheduler，与 Go 侧共享组件 `service/common/communityfact`
是**两份实现** ⇒ 要么收敛到唯一真源，要么证明等价。

**判据（B4）= 四条调度契约逐条对拍**：

| # | 契约 | Go 出处 |
|---|---|---|
| 1 | 投递端点 `POST /v1/events` | Go `service/common/communityfact/wire.go` |
| 2 | 1s 周期 | Go `cmd/fact-dispatch/main.go:67` `time.NewTicker(time.Second)` |
| 3 | 每轮 16 条 | Go `cmd/fact-dispatch/main.go:74` `for i := 0; i < 16; i++` |
| 4 | **单条失败即 break** | Go `cmd/fact-dispatch/main.go:75-79` |

## 二、起点：`align/global` 上**已有一半**（不要从零写）

`align/global` HEAD 上**已提交 811 行**半成品（`align/global-delivery` 从它切 ⇒ 继承）：

| 文件 | 状态 |
|---|---|
| `service/like/like-rpc/.../service/LikeFactDispatchService.java` | 已实现 `Dispatcher.DispatcherImpl` |
| `service/like/like-rpc/.../service/LikeDeliveryValidator.java` | 新增（383 行） |
| `service/like/like-rpc/.../mq/LikeFactDispatchScheduler.java` | 已接 `Dispatcher.runAsync` |
| `service/like/like-rpc/.../mapper/LikeDomainFactOutboxMapper.java` | 已改 |
| `service/like/like-rpc/.../config/LikeRpcProperties.java` | 已加配置 |
| `service/like/like-rpc/src/main/resources/application.yml` | 已加一行 |
| `service/like/like-rpc/src/test/.../LikeFactDeliveryGoSemanticsGateTest.java` | **新增守门（557 行）** |
| `service/comment/comment-rpc/.../service/CommentFactDeliveryService.java` | 仅 javadoc 订正（指向 like 侧） |

⚠️ **这半截在合并前是「未提交」状态** ⇒ 第一件事是确认它在 `align/global` 上已落成提交，否则你的分支继承不到。

## 三、必读（定向读，⛔ 不要通读 4 个规范台账）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| 域定义 + 纪律 | `docs/DRIFT/global/_README.md` | §〇 / §七 |
| 本域待办清单 | `docs/DRIFT/global/_README.md` | §七之二 的 **A5** / **B4** 两行 |
| X-4 逐条取证 | `docs/DRIFT/global/cross-domain.md` | **§二·十**（X-4 已确证那节） |
| like 侧已确证的漂移 | `docs/DRIFT/global/cross-domain.md` | §二·十 ②「RAG 的 like 域另起炉灶」 |
| 坑 | `docs/Pitfalls/global/cross-domain.md` | 全部（不长） |
| Go 真源 | `Sea-RideTheWind_golang/service/common/communityfact/`（`wire.go` / `process.go` / `logging.go`）+ `cmd/fact-dispatch/main.go` | 逐字读 |

## 四、写权范围（白名单，出了就是越权）

**只写这些**：
- `service/like/like-rpc/**`
- `service/comment/comment-rpc/**`
- `service/common/communityfact/Dispatcher.java`（共享层非阻塞 API —— **本域对 `service/common` 有单写权**，但**只碰这一个文件**）
- `docs/DRIFT/global/lanes/L1-delivery.md`（本文件）

**⛔ 禁区**：
- `service/common/**` 的**其余**文件（`http/` · `observability/` · `protection/` · `pom.xml` 等）
- `service/favorite/**` 与 `docs/*/favorite/`（零写权）
- `service/{article,hot,message,users,follow,task}/**`（别的 lane / 别的域）
- `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` · `AGENTS.md` · 三个 `_INDEX.md`（共享文件，整合者统一更新）

## 五、交付物

1. **代码**：让四条调度契约**逐条**成立（或证伪一条并说明 Go 出处）。
2. **守门**：`LikeFactDeliveryGoSemanticsGateTest` 补齐到**四条契约各一条断言**，且：
   - 真 PG（不是 mock）——
   - 每条断言都能被**变异**打红，且**红在目标断言上**（不是红在更早的 `assertThrows` 上，cf. PIT-GLOBAL-018）。
3. **台账**：本文件末尾追加「结论 + 证据（`文件:行号`）+ 变异结果 + 未证成的部分」。

## 六、验收标准

- [ ] `mvn -o -pl service/like/like-rpc,service/comment/comment-rpc -am test` 全绿，**且 `skipped = 0`**
- [ ] 四条契约各有断言，且**都做过变异**（红在目标断言上）
- [ ] 每个 Go 事实都带 `文件:行号`，行号指向**消费点**不是声明点（§3.3.1）
- [ ] 明确写出：**哪些是已证等价、哪些是已知残留、哪些是本轮未覆盖**
- [ ] **真启动**验证过一次（like-rpc 的 fact dispatch 开关打开后，进程起得来、投递真的发生）

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **半截的 stale 编译** | 合并前曾出现 `Dispatcher.runAsync` 「找不到符号」⇒ 那是 **stale 产物**（PIT-GLOBAL-019 同款），重跑即绿 ⇒ **遇到先重跑，别急着改代码** |
| **`@PostConstruct` 阻塞** | `Dispatcher.run` 是**阻塞式** ⇒ 在 `@PostConstruct` 里调它 ⇒ **Spring 上下文永远刷新不完** ⇒ 组件起不来 ⇒ 必须 `runAsync`（已改，但**守门要能抓**这个形态） |
| **开关默认关闭 ⇒ 测不出来** | `like.fact.dispatch-enabled` 默认 false ⇒ 「绿测试 + 起不来」是经典形态 ⇒ 必须有**真启动**守门 |
| **上游资源不进 reactor** | 改 `like-common` 的 DDL 只跑 `like-rpc` ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ 必须 `-am` + `-Dsurefire.failIfNoSpecifiedTests=false` |

---

## 八、结论（2026-10-25 · L1 收口）

> 本节是**本 lane 的台账片段**（协议 §二.3）⇒ 自足，不依赖 §三 的那 4 个规范台账文件。
> 共享台账（`docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md`）**由整合者 fold**。

### 8.1 一句话结论

`like` 域的社区事实投递链路**按 Go `fact_delivery.go` 逐条落地**（改前只有三个 4 行骨架 ⇒ **完全没有投递能力**），
四条调度契约**逐条有守门**，守门共 **22 条**（**真 PG 16 + 真 Spring 上下文 + 真 HTTP 假 DC**，**零 mock**），
并**逐条做过变异**证明守门非空转 ⇒ **14/14 变异全部转红**（含 1 条**复合变异**）。

⚠️ **但「已证等价」的强度只在「源码级对照 + RAG 侧活体对拍」这一层** ⇒ 不等于 Go 侧也活体跑过（见 §8.4）。

### 8.2 已证等价（Go 消费点 → RAG 落点 → 守门 → 变异）

| # | 语义 | Go 出处（**消费点**） | RAG 落点 | 守门用例 | 变异 |
|---|---|---|---|---|---|
| 1 | 事务边界：`Begin` → 持锁投递 → `Commit` | `fact_delivery.go:57` / `:82` / `:103` / `:118` | `LikeFactDispatchService.java:116`（`TransactionTemplate`） | `validFrozenEnvelopeIsDeliveredAndAccepted` | M1 / M2 / M3 |
| 2 | 校验失败 ⇒ `status=3` ＋ **Commit 后**才抛 | `fact_delivery.go:77-82` | `LikeFactDispatchService.java:139-146` ＋ `:201-203` | `blockedFrozenEnvelopeIsPersistedEvenThoughTheCallThrows` | M1 / M3 |
| 3 | 投递失败 ⇒ `status=2` ＋ `retry+1` ＋ **原样带出**错误 | `fact_delivery.go:99-103` / `:113` | `LikeFactDispatchService.java:165-179` ＋ `:204-209` | `failedDeliveryIsPersistedWithRetryCountEvenThoughTheCallThrows` | M2 |
| 4 | 发的是**信封列原始字节** | `fact_delivery.go:92` | `LikeFactDispatchService.java:157`（`rawBytes(row.getDeliveryEnvelope())`） | `endpointContractIsPostV1EventsCarryingTheEnvelopeColumnBytes` | —（由契约① 直接断言字节相等） |
| 5 | `delivered_at` = **应用时钟**，且在 **send 之后**取 | `fact_delivery.go:111`（send 在 `:92`） | `LikeDomainFactOutboxMapper.java:134-149`（**传参**，非 SQL `now()`） | `deliveredAtIsTakenAfterTheSendNotAtTransactionStart` | M4 |
| 6 | 认领：`SKIP LOCKED` ＋ **前序屏障** ＋ `ORDER BY created_at,event_id` | `fact_delivery.go:63-69` | `LikeDomainFactOutboxMapper.java:89-105` | `laterVersionIsBlockedByUnacceptedPredecessor` | M9 |
| 7 | 队列空 ⇒ `(false, nil)`（**不是错误**） | `fact_delivery.go:70-72` | `LikeFactDispatchService.java:120-125` | `emptyOutboxReturnsFalseWithoutError` | — |
| 8 | 信封 ⇔ 行身份一致（含 `AggregateVersion == *FactVersion`） | `fact_delivery.go:27` | `LikeDeliveryValidator.java:178-185` | ①（夹具自检 `envelopeAggregateVersion`）+ `:520` | — |
| 9 | 载荷 **19 字段**、`aggregate_version` 恒 nil | `domain_fact.go:54-74` / `:127` / `:135` | `LikeDeliveryValidator.LikeFact`（`:263-312`） | `payloadWithAggregateVersionIsBlocked` | M5 |
| 10 | 载荷 ⇔ 信封交叉校验 | `fact_delivery.go:36-43` | `LikeDeliveryValidator.java:203-233` | `payloadWithUnknownFieldIsBlocked` / `payloadWithMalformedEventTimeIsBlocked` | M7 / M8 |
| 11 | 载荷 JCS 哈希 == 行 `payload` 列 JCS 哈希 | `fact_delivery.go:44-48` | `LikeDeliveryValidator.java:237-244` | `validFrozenEnvelopeIsDeliveredAndAccepted`（正向） | — |
| 12 | 🔴 **like 的载荷 `aggregate_id` ≠ 信封 `aggregate_id`**（**没有**这条判据） | `domain_fact.go:92`（载荷）vs `:126`（信封） | `LikeDeliveryValidator.java:203-213`（**刻意不加**该判据） | `payloadAggregateIdMayDifferFromEnvelopeAggregateId` | M6 |
| 13 | 调度启动：**非阻塞**、默认关闭、`token ≥ 32` | `main.go:55`（token）/ `:76`（RunDispatcher） | `LikeFactDispatchScheduler.java:70` / `:84` / `:104`（`Dispatcher.runAsync`） | `LikeFactDispatchSchedulerStartupTest`（5 条） | M10 / M11 / M12 |
| 14 | 节奏 1s ＋ 每轮 16 条 ＋ **失败即 break** | `process.go:41` / `:44` / `:46-50` / `:52-53` | 共享层 `Dispatcher.java:258`（批次）/ `:263-274`（两个 break） | `LikeFactDispatchContractGateTest`（3 条） | M13 / **M14（复合）** |

### 8.3 变异结果表

**14 条变异全部转红（14/14），且 6 个被改文件**全部还原**（脚本末尾有守恒校验）。**

| 变异 | 改了什么（锚点 = `文件:行`） | 期望红在 | 结果 | 连带红 | 备注 |
|---|---|---|---|---|---|
| **M1** | `LikeFactDispatchService.java:140` 去掉 `markBlocked` | `blockedFrozenEnvelopeIsPersistedEvenThoughTheCallThrows` | ✅ 红 | 6 | 连带红都是「本来就该 BLOCKED」的用例 ⇒ 去掉落盘 ⇒ 全变「没落盘」⇒ **过强但方向正确** |
| **M2** | `LikeFactDispatchService.java:173` 去掉 `markDeliveryFailed` | `failedDeliveryIsPersistedWithRetryCountEvenThoughTheCallThrows` | ✅ 红 | 0 | — |
| **M3** | `LikeFactDispatchService.java:145` BLOCKED 分支**在事务内**直接抛 | 同 M1 | ✅ 红 | 6 | 证明「错误必须在提交之后才抛」这条被守住了 |
| **M4** | `LikeDomainFactOutboxMapper.java:141` `delivered_at` 改回 SQL `now()` | `deliveredAtIsTakenAfterTheSendNotAtTransactionStart` | ✅ 红 | 0 | 证明「应用时钟 vs 数据库事务开始时刻」这条被守住了 |
| **M5** | `LikeDeliveryValidator.java:208` 去掉 `payload.aggregateVersion != null` | `payloadWithAggregateVersionIsBlocked` | ✅ 红 | 0 | — |
| **M6** | `LikeDeliveryValidator.java:209` **加上** comment 那条「载荷 aggregate_id == 信封 aggregate_id」 | `payloadAggregateIdMayDifferFromEnvelopeAggregateId` | ✅ 红 | 5 | 🔴 **过强**：该判据在 like 域本就不成立 ⇒ 合法事实全被 BLOCKED ⇒ 连带 5 条一起红。**它证明的正是「like 不得照抄 comment 判据」** |
| **M7** | `LikeDeliveryValidator.java:230` 去掉三个时间字段的 RFC3339 校验 | `payloadWithMalformedEventTimeIsBlocked` | ✅ 红 | 1 | — |
| **M8** | `LikeDeliveryValidator.java:84` `.enable(FAIL_ON_UNKNOWN_PROPERTIES)` → **`.disable`** | `payloadWithUnknownFieldIsBlocked` | ✅ 红 | 0 | ⚠️ **首版写错**：删掉 `.enable(...)` 是**无效变异**（Jackson 该特性**默认就是 true**）⇒ 差点误判成「守门空转」 |
| **M9** | `LikeDomainFactOutboxMapper.java:96` 去掉 `NOT EXISTS` 前序屏障 | `laterVersionIsBlockedByUnacceptedPredecessor` | ✅ 红 | 0 | ⚠️ **首版空转**：两条事实先后插入 ⇒ 低版本 `created_at` 更早 ⇒ 排序天然先看到它 ⇒ 拿掉屏障结果一样 ⇒ 已修夹具（高版本 `created_at` 提前 + 夹具自检） |
| **M10** | `LikeFactDispatchScheduler.java:104` `runAsync` → **阻塞式 `run`** | `enabledSchedulerDoesNotBlockContextRefresh` | ✅ 红 | 1 | ⚠️ **首版锚点未命中**：基线是被污染的工作树 ⇒ 干净基线后命中（这是 PIT-GLOBAL-020 的直接后果） |
| **M11** | `LikeFactDispatchScheduler.java` 去掉 `@PreDestroy` | `enabledSchedulerRunsInBackgroundAndStopsOnClose` | ✅ 红 | 2 | — |
| **M12** | `LikeRpcProperties.java:118` `dispatchEnabled` 默认改 `true` | `defaultConfigurationKeepsDispatchDisabled` | ✅ 红 | 0 | — |
| **M13** | `Dispatcher.java:258` 批次 16 → **64** | `dispatcherDeliversSixteenPerRoundWithOneSecondTick` | ✅ 红 | 0 | — |
| **M14** | `Dispatcher.java:269` 去掉失败分支的 `break` **＋** `:209` 失败路径报 `sent=true` | `failedDeliveryBreaksTheInnerBatchImmediately` | ✅ 红 | 0 | 🔴 **复合变异（两处）**：Go `process.go:46-53` 有**两个** break，而失败路径上 `sent` 恒 false ⇒ **单点删任一个都不改变行为**（首版单点变异是等价变异） |

**判定口径（2026-10-25 订正）**：目标 testcase **变红**即可（`failure` 或 `error` 都算）——
M6 的目标用例是 `<error>`（合法事实被判 BLOCKED ⇒ 收到 `FrozenEnvelopeBlockedException`）⇒ **仍然是「红在目标 testcase 上」** ⇒ 守门有效。
**连带红数必须记录**：过强的变异会把别的用例一起带红（M1/M3 各 6 条、M6 5 条）⇒ 连带红**不代表**守门有问题，只说明这条变异「粗」。


### 8.4 未证成（**不声称已证明的**）

| # | 未证成项 | 原因 | 归属 |
|---|---|---|---|
| ① | **Go 侧未活体对拍** | 本轮 Go 事实全部来自**逐行读源码**（`fact_delivery.go` / `domain_fact.go` / `main.go` / `process.go`）⇒ **没有**真跑 Go 进程投递 | 与 X-1「三进程并发」同批 |
| ② | **DC 侧（仓外平台）未接触** | 回执形状靠 `Wire.validateReceipt` 与 Go `ValidateReceipt` 逐条对照判定 ⇒ 假 DC 是**自制的 HTTP 服务器**，不是真 DC | 不可在本仓闭合 |
| ③ | **多副本并发投递未验** | `SKIP LOCKED` ＋ 前序屏障的**并发**语义靠**单实例**测试覆盖 | X-1 |
| ④ | **应用与数据库时钟漂移**这一维不可观测 | 同机同钟 ⇒ 只验了「应用时钟 vs 事务开始时刻」（2 秒慢 sender 区分） | 环境限制 |
| ⑤ | **DC → like 的入向权威读端点** | `LikeAuthorityEndpointConfig` 仍是**未实现骨架**；自带说明「监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector」⇒ **wire 面变更 ⇒ 待拍板** | ⛔ 本轮不碰 |
| ⑥ | **投递的端到端** | 验的是「事实 → 冻结信封 → 校验 → 投递调用 → 回执落库」，**不含** DC 真实接收 | 同 ② |

### 8.5 本轮踩到的坑（4 个新的 ⇒ 建议 L5 立 PIT 条目）

| # | 坑 | 现象 | 处置 |
|---|---|---|---|
| **A** | 🔴 **「守门空转」与「变异无效」混为一谈** | M8 / M14 首版都报「目标用例没红」⇒ 差点误判成守门空转。实查：**Jackson 的 `FAIL_ON_UNKNOWN_PROPERTIES` 默认就是 true**（⇒ 删 `.enable(...)` 无效）；**「失败即 break」由两个 break 共同保证**（错误路径上 `sent` 恒 false ⇒ 单删任一个都不改变行为）⇒ 两条都是**变异写错了**，守门本身是好的 | ① 报「守门空转」前先确认**变异真的改变了行为**；② 复合契约用**复合变异** |
| **B** | 🔴 **判别点选错 ⇒ 守门测的不是被测语义** | M14 的断言是「毒丸失败 ⇒ 后面那条一次都没被试」⇒ 但毒丸失败后 `status=2` 仍可领且 `created_at` 最老 ⇒ 后面那条**无论 break 在不在**都被饿死 ⇒ 那条断言对两种实现都成立 | 判别点换成「**同一轮内试了几次**」：Go 的 break 打的是**内层 16 次批次** ⇒ 有 break 时毒丸每 tick 只试 1 次（3.5s ≈ 4 次）；无 break 则每 tick 连打 16 次（≈50+ 次） |
| **C** | 🔴 **排序天然掩盖了被测语义** | M9 的两条事实先后插入 ⇒ 版本 1 的 `created_at` 天然更早 ⇒ 取件排序本来就先看到版本 1 ⇒ 拿掉前序屏障结果一样 | 把高版本的 `created_at` **提前** ⇒ 此时**只有屏障**能挡住它；并加**夹具自检断言**（`firstByClaimOrder`）防止夹具再退化 |
| **D** | 🔴 **「等条件成立」超时静默返回 ⇒ 后续断言空转通过** | 契约守门的 `awaitTrue` 超时后直接返回 ⇒ 「毒丸请求数 == 0」在**什么都没发生**的情况下恒真 | `awaitTrue` 超时**即抛** `AssertionError` |

⚠️ 另有 2 条已记入 `docs/Pitfalls/global/`（由 L5 维护，本 lane 不写）：**PIT-GLOBAL-019**（构建中改源码 ⇒ 绿的是旧字节码）· **PIT-GLOBAL-020**（变异在共享工作树跑 ⇒ 变异态被提交进主线）。

### 8.6 🔴 favorite 记账（**零触碰** ⇒ 明确告知 owner）

> `favorite` 域对 `global` **零写权**（协议 §六）⇒ 本 lane **未改** `service/favorite/**` 与 `docs/*/favorite/**` 的**任何**文件。
> 但共享层改动**影响 favorite 的可观察行为** ⇒ 按协议 §六「必须记账并告知 owner」记录如下。

| 项 | 内容 |
|---|---|
| **共享层改动** | `service/common/.../Dispatcher.java` **只新增** `runAsync(...)`（`:122-153`）⇒ **`run` 的阻塞语义逐字节未动**（`:161-299`）⇒ **favorite 现有行为零变化** |
| **favorite 同款缺陷（只读取证，未改）** | `service/favorite/favorite-rpc/.../fact/FavoriteFactDispatchScheduler.java:77` 是 `@EventListener(ApplicationReadyEvent.class)`，`:119` 调**阻塞式** `Dispatcher.run(...)` |
| **症状与 like/comment 的差别（⚠️ 别照抄结论）** | like/comment（改前）是 `@PostConstruct` ⇒ **上下文刷新不完 ⇒ 服务起不来**；favorite 是 `@EventListener(ApplicationReadyEvent)` ⇒ **上下文已刷新、Web 容器已起 ⇒ 能服务**，但 `SpringApplication.run()` **永不返回**（主线程被占住）⇒ 非 web 形态 / `run()` 之后的代码会受影响 |
| **修法** | 改调已就位的 `Dispatcher.runAsync(...)`（共享层方法已存在，favorite owner 可直接用）⇒ **本轮由 owner 自行决定何时改** |
| **本轮对 favorite 的改动量** | **0 个文件**（代码 / 配置 / 测试 / 台账全部零改动） |

### 8.7 交接给整合者（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节（§8）**fold 进** `docs/DRIFT/global/{_README,cross-domain}.md` | 共享台账 ⇒ 协议 §二.3「lane 不写共享文件」 |
| ② | 更新 `AGENTS.md` §0 的 `global` 行 ＋ 三个 `_INDEX.md` ＋ `docs/DRIFT/_MANIFEST.json` | 同上（共享索引） |
| ③ | 把**变异脚本**落仓到 `tools/difftest/`（建议名 `like-mutation.py`） | `tools/` **不在 L1 白名单**（L3 写 `ddl*`、L4 写 `flyway*` 前缀文件）⇒ 本 lane 只把它留在会话临时目录 `/tmp/mutation-round20.py`；**§8.3 的表已把 14 条变异的锚点与期望红点写全** ⇒ 可据此重建 |
| ④ | §8.5 的 4 条坑建议立成 `docs/Pitfalls/global/` 条目 | `docs/Pitfalls/global/**` 在 **L5** 白名单 |
| ⑤ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |
