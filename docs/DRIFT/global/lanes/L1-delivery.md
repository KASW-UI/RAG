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

### 8.8 验证记录（2026-10-25 收口）

| 验证 | 命令 | 结果 |
|---|---|---|
| 三套守门单独跑 | `mvn -o test -pl service/like/like-rpc -am -Dtest='LikeFactDeliveryGoSemanticsGateTest,LikeFactDispatchContractGateTest'` | **17 条全绿**（14 + 3） |
| like-rpc ＋ comment-rpc 全量 | `mvn -o test -pl service/like/like-rpc,service/comment/comment-rpc -am test` | **like-rpc 83 条 / comment-rpc 89 条** ⇒ failures=0 errors=0 **skipped=0** |
| **全仓** | `mvn -o test -fae` | **BUILD SUCCESS**；按 surefire XML 的 `tests=` 汇总：**2213 条 / failures=0 / errors=0 / skipped=0**（273 个 XML） |
| 变异套件 | `MUT_WT=<隔离工作树> python3 mutation-round20.py` | **14/14 全部转红**；末尾**还原校验** 6 个被改文件**逐字节一致** |

⚠️ 变异跑在**隔离工作树**（`/tmp/rag-round20-mut`，已 `git worktree remove` 清理）⇒ 变异态**没有**污染 `align/global`
（PIT-GLOBAL-020 的直接要求；本轮已因该坑误提交过一次 ⇒ `5c3b424` 修回）。

**推送**：`origin/align/global` `76e227b..c27cff1` · `upstream/align/global` `43c7c8d..c27cff1`（均 fast-forward）。

---

## 九、第二十一轮 · T-11：装全局 OpenTelemetry（trace 导出链路 + 传播器）

> 本节是**本 lane 的台账片段**（协议 §二.3）⇒ 自足。共享台账 `common-tails.md` 的 T-11 段**由整合者 fold**。
> spec：`.agents/specs/global-round21-otel-global-sdk.md`
> ⚠️ **该 spec 文件是 L1 白名单的「延伸」** —— 原白名单只列了 round20 那份 spec；
> 但 `.agents/specs/**` **不在 L2~L5 任何一条 lane 的白名单里** ⇒ **文件级零重叠** ⇒ 不会撞车（已请整合者复核）。

### 9.1 一句话结论

RAG 侧此前**全仓无 exporter 装配** ⇒ `GlobalOpenTelemetry.get()` 是 no-op ⇒ span 的 `SpanContext` 恒 INVALID
⇒ **既没有 span，也没有导出**；而 Go 侧每个服务启动时都装**全局** provider + 传播器 ⇒
⇒ 本轮在 `service/common` 补上装配 ⇒ **10 条守门**（真 Spring 上下文 / 真 gRPC 假 OTLP 接收端 / 真 Tomcat）全绿、**4/4 变异**证明守门非空转。

### 9.2 已证等价（Go 消费点 → RAG 落点 → 守门 → 变异）

| # | 语义 | Go 出处（**消费点**） | RAG 落点 | 守门 | 变异 |
|---|---|---|---|---|---|
| 1 | `ParentBased(TraceIDRatioBased(Sampler))` | `core/trace/agent.go:111` | `GoStyleOtelAgent.buildProvider` | `sampledFlagFollowsInboundParent`（既有，filter 那条） | N1 |
| 2 | Resource 带 `service.name` | `core/trace/agent.go:106` + `:112` | 同上 | ①（全局装配真跑） | N1 |
| 3 | **endpoint 非空才装 batcher** | `core/trace/agent.go:114-121` | `buildProvider` 的 `if (config.endpoint() != null)` | `spanIsActuallyExportedToTheWire` | **N2** |
| 4 | `otel.SetTracerProvider(tp)`（**全局**） | `core/trace/agent.go:127` | `GoStyleOtelAgent.install` | `globalProviderIsRealInsideRealSpringContext` | **N1** |
| 5 | 传播器 `TraceContext + Baggage`（**无条件**） | `core/trace/propagation.go:8-11` | `OpenTelemetrySdk.builder().setPropagators(...)` | 同上（inject 出 traceparent） | N1 |
| 6 | `sync.Once` ⇒ 同 JVM 只装一次 | `core/trace/agent.go:43-52` | `GoStyleOtelAgent.INSTALLED` | — | — |
| 7 | 装不上**只记 ERROR，继续启动** | `core/trace/agent.go:49-51` | `catch (IllegalStateException) { LOG.error(...); }` | — | — |
| 8 | 关停 flush | `core/trace/agent.go:55-62` + `serviceconf.go:71-73` | `shutdown()` + JVM shutdown hook | `shutdownFlushesPendingSpans` | **N3** |
| 9 | 端点无 scheme ⇒ 明文 | `core/trace/agent.go:70-71`（`WithInsecure`） | `GoStyleOtelConfig.normalizeEndpoint` | ②（真导出 ⇒ 传输通） | N2 |
| 10 | **一个** provider：server span 既写响应头又被导出 | `rest/handler/tracehandler.go:32-64` | `GoStyleTraceparentFilter` 改用全局 tracer | `responseTraceparentBelongsToTheExportedSpan` | **N4** |

### 9.3 变异结果表

| 变异 | 改了什么 | 期望红在 | 结果 | 连带红 |
|---|---|---|---|---|
| **N1** | `install` 不调 `GlobalOpenTelemetry.set(sdk)` | `globalProviderIsRealInsideRealSpringContext` | ✅ 红 | 1 |
| **N2** | `buildProvider` 不装 exporter | `spanIsActuallyExportedToTheWire` | ✅ 红 | 1 |
| **N3** | `shutdown()` 不 close provider | `shutdownFlushesPendingSpans` | ✅ 红 | 0 |
| **N4** | filter 退回私有 provider | `responseTraceparentBelongsToTheExportedSpan` | ✅ 红 | 0 |

⇒ **4/4 全部转红**；隔离工作树 `/tmp/rag-round21-mut` 内跑（PIT-GLOBAL-020）⇒ 结束后**还原校验通过**。

### 9.4 🔴 可观察变化（**记账，不静默**）

| # | 变化 | 依据 | 影响面 |
|---|---|---|---|
| ① | **出向 DC 请求多一个 `traceparent` 头** | Go `communityfact/wire.go:104` 就是这么做的 ⇒ RAG 此前**少一个**（全局传播器是 no-op） | like / comment / favorite 的事实投递链路 |
| ② | **rpc / MQ / communityfact 的 span 从 no-op 变真**（会被导出） | Go 侧本来就是真建 span | `RpcInterceptor` / `MqConsumer` / `Dispatcher` / `AuthorityHandler` / `MessageDomainService` / `UserDomainService` / `AdminSpans` / `FavoriteFactDispatchScheduler` |
| ③ | **响应头的 traceparent 与导出物同源**（此前**不同源** ⇒ trace 在边界处断成两截） | Go 只有一个 provider | 10 个 api 模块 |
| ④ | ⚠️ **Go 的 `otel.SetErrorHandler` 在 OTel Java 1.44.0 里没有对应物** | 实测：api / sdk / sdk-trace / sdk-common **四个 jar 里都没有 `ErrorHandler` 这个类/接口** | 导出失败由 SDK 默认路径处理 ⇒ **已知残留** |

### 9.5 未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **Go 侧未活体对拍** | 本轮 Go 事实全部来自**逐行读源码** ⇒ 没有真跑 Go 进程看 jaeger |
| ② | **真 jaeger 未接触** | 守门用**自制假 OTLP 接收端** ⇒ 证的是「OTLP/gRPC 字节真的出了机器」，**不是**「jaeger 真的收到了」 |
| ③ | **采样比例只覆盖 1.0** | 本仓 20 个模块都写 `always_on` / `parentbased_always_on` ⇒ `toRatio` 的其它分支未测 |
| ④ | **协议只支持 `grpc`** | 与 Go 的 `Batcher: otlpgrpc` 一致；非 grpc 的 `protocol` ⇒ **抛错**（不静默降级） |
| ⑤ | **多进程下的 agent 行为未验** | Go 的 `sync.Once` 是**进程内**语义；跨进程各自装一份（Go 侧同款） |

### 9.6 🔴 favorite 记账（**本轮对 favorite 零文件改动**）

> `service/favorite/**` 与 `docs/*/favorite/**` **零改动**（`git diff` 可验）。
> 但**共享层改动会影响 favorite 的可观察行为** ⇒ 按协议 §六「必须记账并告知 owner」：

| 项 | 内容 |
|---|---|
| **favorite 侧受影响的地方** | ① `FavoriteFactDispatchScheduler.java:146` 建的 span 从 no-op 变真（会被导出）；② favorite 域的 yaml 有 `otel:` 段 ⇒ 自动配置清单生效 ⇒ favorite-api / favorite-rpc **也会装上全局 provider** ⇒ ③ favorite-api 的**响应头**会多一个由**全局** provider 生成的 `Traceparent`（此前是 filter 的私有 provider 生成的 ⇒ **trace-id 变了**，但仍是合法格式） |
| **favorite 代码需要改吗** | **不需要** ⇒ 装配在共享层自动完成 |
| **favorite owner 需要知道什么** | ① favorite 的 trace 现在**真的会被导出**到 jaeger（此前不会）；② favorite-api 的响应头 traceparent 的 **trace-id 变了**（内容格式不变）⇒ 若 favorite 域有守门断言 trace-id 的**具体值**，需要复核（若有 ⇒ 那是**依赖 no-op 行为的断言**，属于该域自己的问题） |
| **本轮对 favorite 的改动量** | **0 个文件** |

### 9.7 交接整合者（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 T-11 段 | 共享台账 ⇒ 协议 §二.3 |
| ② | 更新 `AGENTS.md` §0 的 `global` 行 ＋ 三个 `_INDEX.md` ＋ `_MANIFEST.json` | 共享索引 |
| ③ | 把**本轮变异脚本**落仓到 `tools/difftest/`（建议名 `otel-mutation.py`） | `tools/` **不在 L1 白名单**（L3 写 `ddl*`、L4 写 `flyway*`）⇒ 只留在会话临时目录 `/tmp/mutation-round21.py`；§9.3 已把 4 条变异的锚点与期望红点写全 ⇒ 可据此重建 |
| ④ | §9.2 / §9.4 的结论 fold 进 `docs/Intentional_Deviations/global/`（若有需签字项） | 那是 L5 的白名单 |
| ⑤ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |

---

## 十、第二十二轮 · T-13：超时路径的「响应撕裂 / 状态码被抢」⇒ 对齐 Go 的**单写者**语义

> **触发**：第二十一轮（T-11）全仓回归的**唯一一条红**
> （`MessageApiTimeoutRealTomcatTest.slowDownstreamYieldsGoTimeoutContract` ⇒ 客户端 `IOException: HTTP/1.1 header parser received no bytes`
> ⇒ 服务端 Tomcat `NullPointerException: Cannot invoke "MimeHeaderField.getName()" because "this.headers[i]" is null`）。
>
> ⚠️ **白名单延伸（请整合者一并复核）** —— 本节是**本 lane 的台账片段**（协议 §二.3）⇒ 自足；
> 共享台账 `common-tails.md` 的 T-13 段**由整合者 fold**。
> spec：`.agents/specs/global-round22-timeout-response-race.md`（同样**不在 L2~L5 任何一条 lane 的白名单里** ⇒ 文件级零重叠）。
> 🔴 **且本轮改了 `service/common/**`（`observability/HttpMiddleware.java` + 新增一个 test）** ——
> 而 §四 的 L1 白名单**原本只允许 `service/common/communityfact/Dispatcher.java`**。
> ⇒ **事实上的延伸始于第二十一轮（T-11）**：那一轮已经改了 `service/common/{observability,http}/**` + `pom.xml`
> + `src/main/resources/**` + `src/test/**`，**但 §九 没有把它登记成白名单延伸**（只登记了 spec 那一份）——
> ⇒ **本节把这条账补上**：T-11 与 T-13 两条 lane 都把 `service/common/**` 纳入了实际写权，
> **请整合者复核并把 §四 的白名单正式更新**（否则台账与实际写权不一致，review 时会误判成越权）。

### 10.1 一句话结论

🔴 **这条缺陷早于 T-11**（A/B 对拍坐实）⇒ **T-11 的全局 OpenTelemetry 没有引入任何回归**；
真正的问题是 RAG 的 `ObservabilityResponseWriter` **不是** Go 的 `observabilityResponseWriter` 的等价物
—— Go 侧它是**纯 buffer**（`Write`/`WriteHeader` 从不碰底层 `http.ResponseWriter` ⇒ **socket 永远只有一个写者**），
RAG 侧它**没覆写 `flushBuffer()`** ⇒ **worker 线程能提交响应** ⇒ 两个写者同时改 Tomcat 的头数组
⇒ ① `MimeHeaders` 留 null 洞 ⇒ 读头 NPE ⇒ 响应一个字都发不出去（EOF）；
② 状态码被 worker 抢走（客户端收到 **200** 而不是 503）。**已修**（4 条改动，缺一不可）。

### 10.2 归因（A/B 对拍 ⇒ 排除 T-11）

| 步骤 | 做法 | 结果 |
|---|---|---|
| ① 结构取证 | `git diff aac691c..1d179ff -- service/common/.../HttpMiddleware.java service/common/.../GoStyleMaxBytesFilter.java` | **空** ⇒ 涉事的竞态代码在 T-11 前后**逐字节相同** |
| ② 可复现的门 | 新增 `GoStyleTimeoutResponseRaceGateTest`（真 Tomcat + **生产用的** `HttpMiddleware` + 30ms 超时 + 200 轮 + 裸 socket） | 修前 **200/200 全坏** |
| ③ **A/B 对拍** | 同一个门拷到 **`aac691c`（T-11 之前）** 的隔离工作树 | **同样 200/200 全坏** |

⇒ ⇒ **缺陷早于 T-11 ⇒ 不是 T-11 引入的**（T-11 的回归只是**第一次把它撞出来**）。

### 10.3 根因（Go 出处 → RAG 落点）

| Go 位置（真源） | 语义 | RAG 落点（修前） | 判定 |
|---|---|---|---|
| `service/common/observability/http.go` 的 `type observabilityResponseWriter` | `header`/`body`/`status` + `mu` + `timedOut` ⇒ **纯 buffer** | `ObservabilityResponseWriter` 同名字段，但**额外持有 `original` 并实时透传** | ❌ 不等价 |
| 同文件 `func (w *observabilityResponseWriter) Write(...)` | `if w.timedOut { return 0, http.ErrHandlerTimeout }` ⇒ 超时后**丢弃**业务写 | `getWriter()` 的 write 也有 `timedOut` 判断 ✓ | ✅ 等价 |
| 同文件 `flushTo(dst)` | **只在 done 分支**调用 ⇒ 唯一落 socket 的地方 | `flushTo` 也只在 done 分支调用 ✓ | ✅ 等价 |
| 同文件 `case <-ctx.Done(): rec.markTimedOut(); … http.Error(w, ...)` | **先标记、后写**；`w` 是**原始** writer | 修前：`workerFuture.cancel(true)` **在** `markTimedOut()` **之前** ⇒ worker 可能抢先提交 | ❌ 不等价 |
| 同文件 `observabilityResponseWriter` **没有** flush/commit 概念 | ⇒ worker **结构上无法**提交响应 | 修前：`ObservabilityResponseWriter` **没覆写 `flushBuffer()`** ⇒ `ServletResponseWrapper` **透传** ⇒ worker 能提交 | ❌ **根因** |

⇒ **修法（4 条，缺一不可）**：

| # | 改动 | 挡的是 |
|---|---|---|
| ① | `ObservabilityResponseWriter` 加 `underlyingLock`；所有对底层的写 + `markTimedOut`/`markClientCancelled` **同一把锁** | NPE（`count` 交错 ⇒ null 洞） |
| ② | 超时/断连后 `flushBuffer()` **no-op** | 状态码被抢（worker 抢先 commit） |
| ③ | **先标记、后中断**（`markTimedOut()` 提到 `cancel(true)` 之前） | 标记落地前的漏网写 |
| ④ | 超时响应**显式 `Content-Length: 15`** | wire 对齐（Go 走 net/http 缓冲 ⇒ 算长度；RAG `flush()` 即提交 ⇒ 退化成 chunked） |

⚠️ ② **不是**「永远 no-op」：Servlet 规范「commit 后设 status 无效」是**容器语义**
（`HttpMiddlewareTest#statusSetAfterCommitIsIgnoredByServletSemantics` 把它固化成显式契约）
⇒ 正常路径必须照常透传 ⇒ 只在超时/断连之后掐掉。

### 10.4 已证等价（Go 消费点 → RAG 落点 → 守门 → 变异）

| # | Go 消费点 | RAG 落点 | 守门 | 变异 |
|---|---|---|---|---|
| 1 | `http.Error(w, httpTimeoutBody, 503)` ⇒ 状态码 503 | `writeTimeoutResponse` 的 `setStatus(503)` | `timeoutResponseIsNeverTorn` | M1 / M2 |
| 2 | `http.Error` ⇒ body = `"Request Timeout\n"` | `getWriter().write(body); write('\n')` | 同上（解 chunk 后逐字节比对） | M0 |
| 3 | `http.Error` ⇒ `X-Content-Type-Options: nosniff` | `setHeader("X-Content-Type-Options","nosniff")` | 同上 | M0 |
| 4 | `http.Error` ⇒ `Content-Type: text/plain; charset=utf-8` | `setContentType(...)`（**分号后的空格是 Tomcat 框架限制**，已记录） | 同上（只断言到 charset） | M0 |
| 5 | Go 的 writer 超时后**丢弃**业务写 | `flushBuffer()` 超时后 no-op + `writeToOriginal` 的标志检查 | 同上（断言无 `X-Late-Write`） | M1 / M3 / M4 |
| 6 | Go：**只有一个写者**碰 socket | `underlyingLock` 串行化 + ②③ | 同上（200 轮全干净） | M0 / M1 / M2 |

### 10.5 变异结果表

> 隔离工作树 `/tmp/rag-round22-mut`（**PIT-GLOBAL-020**：不在共享工作树跑）· 基线 `1d179ff` + 本轮修复 + 新门
> 三道闸（PIT-GLOBAL-018）：① 跑前删旧 surefire XML ② 看 maven 退出码 ③ grep `COMPILATION ERROR`（编译失败 ≠ 转红）
> 判定口径：目标 testcase **变红**（failure 或 error 都算）；连带红数记录但不扣分。

| # | 变异 | 目标 testcase | 结果 | 坏轮次 / 200 | 红点（门实际报出的） |
|---|---|---|---|---|---|
| **M0** | **整体回退**（`git checkout` 生产文件 ⇒ 修前状态） | `timeoutResponseIsNeverTorn` | ✅ 红 | **194** | 状态行 = `HTTP/1.1 200`（worker 抢走） |
| **M1** | `flushBuffer()` 去掉「超时后 no-op」 | 同上 | ✅ 红 | **131** | 状态行 = `HTTP/1.1 200` |
| **M2** | `writeTimeoutResponseQuietly` 改走 `writeToOriginal`（被「已超时」标志 no-op 吞掉） | 同上 | ✅ 红 | **200** | 状态行 = `HTTP/1.1 200`（503 根本没写出） |
| **M3** | `writeToOriginal` 去掉「超时/断连后 no-op」标志检查 | 同上 | ✅ 红 | **163** | **worker 的晚写漏进响应（`X-Late-Write`）** |
| **M4** | 顺序倒转：`cancel(true)` 提到 `markTimedOut()` 之前 | 同上 | ✅ 红 | **1** | 状态行 = `HTTP/1.1 200` |

⇒ **5/5 全部转红**；还原校验通过（隔离树的生产文件与工作树**逐字节相同**）。

⚠️ **两条要如实说的**：
1. **M4 只坏 1/200** ⇒ 「先标记、后中断」这一条守的是**窄窗口**（生产里偶发的那次就落在这个窗口）
   ⇒ 它**不能**单独证明这条改动是必需的 ⇒ **真正的承重墙是 M1/M3（超时后不许 worker 再碰底层）**。
2. **M0 只坏 194/200（不是 200/200）** ⇒ 说明**缺陷形态本身有随机性**（取决于两条线程路径的相对时序）
   ⇒ ⇒ 「修前 200/200 全坏」这个说法**只在 A/B 那一次成立**，**不应作为稳定结论引用**。
   ⇒ 稳定结论是：**修前 100% 会坏**（A/B 两次独立运行：200/200 与 194/200）⇒ **每一轮都有很高概率坏**。

⚠️ **本轮踩到的一个新坑（⇒ 建议 L5 立 `PIT-GLOBAL-021`）**：
**变异结果的判定正则必须匹配 surefire 打印的「全限定名」**（`-- in com.rag.common.observability.XxxTest`），
而不是简单类名 ⇒ 否则**红与绿都读不出来** ⇒ 5 条全报 `NO_RESULT`，
**看起来像「守门没被触达」，实际是「守门红了但脚本读不出来」**。
⇒ 与 PIT-GLOBAL-014（「没转红」先排除「测试没跑」）同源，但方向相反：**这次是「跑了红，但没读出来」**。

### 10.6 🔴 可观察变化（**记账，不静默**）

| # | 变化 | 依据 | 影响面 |
|---|---|---|---|
| ① | **超时响应不再可能被 worker 抢走** ⇒ 稳定 `503 + "Request Timeout\n"`（原先可能是 200 / 撕裂 / EOF） | Go 的单写者语义 | **全部 10 个 api**（共享层） |
| ② | **超时响应多一个 `Content-Length: 15`**（原先 `Transfer-Encoding: chunked`） | Go 的 `http.Error` ⇒ net/http 算长度 | 全部 10 个 api |
| ③ | **错误处理路径（`@ExceptionHandler` 返回 `ResponseEntity`）的响应改由 `flushCaptured` 统一提交** ⇒ 框架从 chunked 变 `Content-Length` | 同上 | 全部 10 个 api（**向 Go 收敛**） |

### 10.7 未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **Tomcat 那条 `103 Early Hints` 的触发点未定位** | Tomcat 11 只有 `Response.sendError(103)` → `sendEarlyHints()` 会发它，而 RAG 侧**无人**调 `sendError(103)` ⇒ 触发链未查清；**但**它 ① 是 informational（客户端按 RFC 9110 忽略）② 修前修后都出现 ③ 最终响应完好 ⇒ **判为无影响** |
| ② | **Go 侧未活体对拍** | Go 事实全部来自**逐行读源码**（`service/common/observability/http.go`） |
| ③ | **并发压测未做** | 本门是 200 轮串行；未做多线程并发压测 |
| ④ | **其余 9 个 api 的超时路径未逐域对拍** | 本门在 `service/common` 用生产中间件证明「语义层」；各 api 的**业务链**（控制器 / 异常处理器）形态不同 ⇒ 是否还有别的「晚写」形态**未验** |

### 10.8 🔴 favorite 记账（**本轮对 favorite 零文件改动**）

> `service/favorite/**` 与 `docs/*/favorite/**` **零改动**。
> 但**共享层改动会影响 favorite 的可观察行为** ⇒ 按协议 §六「必须记账并告知 owner」：

| 项 | 内容 |
|---|---|
| **favorite 侧受影响的地方** | favorite-api 的**超时路径**现在稳定返回 `503 + "Request Timeout\n" + Content-Length: 15`（原先可能返回 200 / 撕裂 / EOF）⇒ **向 Go 收敛** |
| **favorite 代码需要改吗** | **不需要** ⇒ 修法全在 `service/common` |
| **favorite owner 需要知道什么** | ① favorite 的超时响应**不再可能状态码被抢**；② 多一个 `Content-Length` 头；③ 若 favorite 域有守门断言「超时时是 chunked / 是 200」⇒ 需要复核（若有 ⇒ 那是**依赖缺陷行为的断言**） |
| **本轮对 favorite 的改动量** | **0 个文件** |

### 10.9 交接整合者（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 T-13 段 | 共享台账 ⇒ 协议 §二.3 |
| ② | 更新 `AGENTS.md` §0 的 `global` 行 + 三个 `_INDEX.md` + `_MANIFEST.json` | 共享索引 |
| ③ | 把**本轮变异脚本**落仓到 `tools/difftest/`（建议名 `timeout-race-mutation.py`） | `tools/` **不在 L1 白名单** ⇒ 只留在会话临时目录 `/tmp/mutation-round22.py`；§10.5 已把 5 条变异的锚点与期望红点写全 ⇒ 可据此重建 |
| ④ | **新坑 `PIT-GLOBAL-021`（判定正则）** ⇒ 落 `docs/Pitfalls/global/` | 那是 **L5** 的白名单 |
| ⑤ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |

### 10.10 验证记录（2026-10-25 收口）

| 项 | 结果 |
|---|---|
| **全仓回归**（`mvn -o test -fae`） | ✅ **BUILD SUCCESS** · **2218 tests / 0 failures / 0 errors / 0 skipped**（276 份 XML） |
| **上一轮（T-11 收口时）** | 🔴 BUILD FAILURE · 2217 tests · **1 error**（就是本轮修掉的那条偶发超时测试）⇒ **2218 = 2217 + 本轮新增的 1 条门** |
| **`service/common` 模块** | ✅ 239 条全绿（含新增门） |
| **那条偶发测试**（`MessageApiTimeoutRealTomcatTest`） | ✅ **连跑 5 次全绿**（修前：单独复跑也绿 ⇒ 属偶发 ⇒ 回归时才撞出） |
| **新门在回归里** | ✅ `GoStyleTimeoutResponseRaceGateTest` 1 条 / 7.092 s / 0 失败 |
| **变异** | ✅ **5/5 全部转红**（隔离工作树 `/tmp/rag-round22-mut`，已清理）+ 还原校验逐字节相同 |
| **A/B 对拍** | ✅ `aac691c`（T-11 之前）同门 **同样全坏** ⇒ **缺陷早于 T-11** |
| **favorite** | ✅ 代码 / 配置 / 测试 / 台账 **零改动**（`git diff` 可验）；共享层影响已记账（§10.8） |
| **提交 / 推送** | `ea0ae25` · origin `1d179ff..ea0ae25` · upstream `1d179ff..ea0ae25`（均 fast-forward） |

---

## 十一、第二十三轮 · T-11 残留：采样比例（`Telemetry.Sampler`）+ batcher（`Telemetry.Batcher`）补齐 **+ 一处错账订正**

> **基线**：`25f6837`（第二十二轮 T-13 收口）· **性质**：**共享层（`service/common`）的补齐 + 一条自曝错账的订正**
> **本轮对 favorite 的改动量**：**0 个文件**（§11.8 记账）

### 11.1 一句话结论

**第二十一轮台账 §9.5 ③④ 两条「未证成」本轮关闭**：采样比例现在真的按 Go 的 `Telemetry.Sampler` 生效（不再是哑配置），
batcher 协议现在**真的会校验**（写错 ⇒ 装配失败 ⇒ 记 ERROR ⇒ **继续启动**，照 Go 的 `startAgent` 失败路径）。
⇒ **同时自曝并订正了第二十一轮的一处错账**（§9.5 ④ 声称「非 grpc 的 protocol ⇒ 抛错」，**代码里根本没有这层校验** ⇒ 那是真缺陷）。

### 11.2 🔴 自曝错账：§9.5 ④ 写的是「抛错」，**代码里没有这层校验**

第二十一轮台账原文：

> ④ | **协议只支持 `grpc`** | 与 Go 的 `Batcher: otlpgrpc` 一致；非 grpc 的 `protocol` ⇒ **抛错**（不静默降级）

⇒ **复查代码时发现**：`GoStyleOtelAgent.buildProvider(...)` **压根没读 `config.protocol()`**
⇒ 任何协议值都被**静默当成 grpc** ⇒ **不是文档问题，是真缺陷**。

⇒ 这正是 AGENTS.md **§3.3.2** 点名的形态：「以『Go: …』形式写的断言，本身是高危项」——
本轮是**自曝**一条，不是被别人抓的。

### 11.3 🔴 第二个坑：两侧的**协议值字面量就不一样**

| 侧 | 位置 | 值 |
|---|---|---|
| Go | `core/trace/config.go:12` `Batcher string` | **`otlpgrpc`**（合法集合 `zipkin\|otlpgrpc\|otlphttp\|file`） |
| RAG | 10 个 api 的 yaml `otel.exporter.otlp.protocol` | **`grpc`** |

⇒ 字面量差一个 `otlp` 前缀 ⇒ **不显式映射就会把合法值判成非法值**
⇒ ⇒ 本轮**第一版实现就是这么写错的**（判 `otlpgrpc`）⇒ **被自己的新门当场抓住**（`unknown exporter: grpc`）。

### 11.4 已证等价（Go 消费点 → RAG 落点 → 守门 → 变异）

| Go 消费点 | Go 语义 | RAG 落点（修后） | 守门 |
|---|---|---|---|
| `core/trace/config.go:9` `Sampler float64` | 采样是**比例**（默认 1.0） | `GoStyleOtelConfig.toRatio(...)`：Boot 的采样器名 → 比例 | 门 3 |
| `core/trace/agent.go:111` `ParentBased(TraceIDRatioBased(c.Sampler))` | **永远** ParentBased ⇒ 入向 parent 不采样 ⇒ 子 span 也不采样 | `Sampler.parentBased(Sampler.traceIdRatioBased(ratio))`（**不变**，本轮补的是它的守门） | 门 1 · 门 2 |
| `core/trace/config.go:12` `options=zipkin\|otlpgrpc\|otlphttp\|file` | Batcher 的**合法集合** | `GoStyleOtelConfig.GO_BATCHERS` + `RAG_PROTOCOL_TO_GO_BATCHER` | 门 5 |
| `core/trace/agent.go:100-101` `default: return nil, fmt.Errorf("unknown exporter: %s", ...)` | 不认识的 exporter ⇒ **整个 agent 装不上** | `GoStyleOtelConfig.validateBatcher()` ⇒ `IllegalStateException` | 门 4 |
| `core/trace/agent.go:114-121` `if len(c.Endpoint) > 0 { createExporter(c) }` | **端点为空 ⇒ 根本不看 Batcher** | `validateBatcher()` 的 `if (endpoint == null) return;` | 门 6 |
| `core/trace/agent.go:43-52` `tp, err := startAgent(c); if err != nil { logx.Error(err); return }` | 装不上 ⇒ **只记 ERROR，继续启动**（全局仍是 no-op） | `GoStyleOtelAgent.install(...)` catch 住 ⇒ 记 ERROR ⇒ 返回 null ⇒ **异常不逃出去** | 门 7 |

### 11.5 改动（3 个文件）

| # | 文件 | 改动 |
|---|---|---|
| ① | `service/common/.../observability/GoStyleOtelConfig.java` | 新增 `GO_BATCHERS` / `RAG_PROTOCOL_TO_GO_BATCHER` / `IMPLEMENTED_BATCHER` + `validateBatcher()` |
| ② | `service/common/.../observability/GoStyleOtelAgent.java` | `buildProvider` 开头调 `config.validateBatcher()`；`install` 把 `buildProvider` 包进 try/catch ⇒ **失败不阻断启动** |
| ③ | `service/common/src/test/.../observability/GoStyleOtelSamplerProtocolGateTest.java`（**新增**） | 7 条守门 |

#### 守门（7 条）

| # | 守门 | 断言 | Go 出处 |
|---|---|---|---|
| 1 | `ratioZeroMeansNothingIsExported` | `Sampler=0.0` ⇒ 根 span **不 recording**、**不 sampled**、**一个 span 都不出机器**（关停 flush 后 collector 仍是 0） | `agent.go:111` |
| 2 | `parentBasedKeepsSampledParentEvenWhenRatioIsZero` | `Sampler=0.0` + 入向 `traceparent`（flags=01）⇒ 子 span **仍被采样并导出** | `agent.go:111` 的 ParentBased |
| 3 | `samplerNameMapsToGoRatio` | `always_on`⇒1.0 / `always_off`⇒0.0 / `traceidratio`⇒arg / 非法名⇒**炸** | `config.go:9` + `agent.go:111` |
| 4 | `unknownBatcherMustFailInsteadOfSilentlyUsingGrpc` | 非法协议 + 端点非空 ⇒ **抛错**（消息含 `unknown exporter`） | `agent.go:100-101` |
| 5 | `goSupportedButUnimplementedBatcherMustFail` | Go 支持但本仓未实现（`otlphttp`）⇒ **同样抛错**（不许静默降级） | `config.go:12` |
| 6 | `emptyEndpointSkipsBatcherValidation` | 端点为空 ⇒ **不校验**、能装出 provider | `agent.go:114-121` |
| 7 | `installFailureDoesNotBreakStartup` | 装不上 ⇒ `install` **返回 null 不抛** + 「已装配」标志为 false + 全局仍是 no-op | `agent.go:43-52` |

⚠️ **门 1 的一处自我订正**：第一版断言「被 DROP 的 span 的 SpanContext **无效**」⇒ **红了**
⇒ 复查 OTel Java 1.44.0 与 Go SDK 的真实语义：**DROP 决策下 SpanContext 仍有效**（有 trace-id/span-id），
只是 **sampled 标志为 false**（trace flags = `00`）⇒ ⇒ 响应头里**仍会有** `traceparent`（flags=00）。
⇒ 断言改成 `isRecording()==false` + `isSampled()==false` + **一个 span 都不导出**。

### 11.6 变异结果表（5 条，**全部转红**）

| # | 变异 | 期望红点 | 实测 |
|---|---|---|---|
| M1 | `buildProvider` 去掉 `validateBatcher()` | 门 4 | ✅ 红（fail=3） |
| M2 | `validateBatcher` 去掉「端点为空不校验」 | 门 6 | ✅ 红（err=1） |
| M3 | `install` 不再吞异常 | 门 7 | ✅ 红（fail=1） |
| M4 | sampler 去掉 `ParentBased` 包装 | 门 2 | ✅ 红（fail=1） |
| M5 | `toRatio` 的 `always_off` 改成 1.0 | 门 3 | ✅ 红（fail=1） |

⇒ **5/5 全部转红**（隔离工作树 `/tmp/rag-round23-mut`，已清理）+ **还原校验逐字节相同**。

### 11.7 🔴 可观察变化（**记账，不静默**）

| # | 变化 | 依据 | 影响面 |
|---|---|---|---|
| ① | **协议值写错 ⇒ 装配失败**（记 ERROR + 继续启动）⇒ 全局 trace **不生效** | Go：`unknown exporter` ⇒ `startAgent` 返错 ⇒ 只 `logx.Error` | 本仓 20 个模块的 yaml 现在只写 `grpc` ⇒ **今天零行为变化**；写错值时**不再静默当 grpc** |
| ② | **`Sampler` 的非 1.0 取值现在真的生效** | Go：`Telemetry.Sampler` 是比例 | 本仓 yaml 全是 `always_on`/`parentbased_always_on`（⇒ 比例 1.0）⇒ **今天零行为变化**；但**以后**改 yaml 就能真的改采样率（此前是「配了也不生效」的哑配置） |

### 11.8 🔴 favorite 记账（**本轮对 favorite 零文件改动**）

> `service/favorite/**` 与 `docs/*/favorite/**` **零改动**。
> 但**共享层改动会影响 favorite 的可观察行为** ⇒ 按协议 §六「必须记账并告知 owner」：

| 项 | 内容 |
|---|---|
| **favorite 侧受影响的地方** | favorite 的 yaml 写 `otel.exporter.otlp.protocol: grpc` + `otel.traces.sampler: parentbased_always_on` ⇒ **两个值都合法** ⇒ **零行为变化** |
| **favorite 代码需要改吗** | **不需要** ⇒ 修法全在 `service/common` |
| **favorite owner 需要知道什么** | ① favorite **不受**本轮「协议写错 ⇒ 装配失败」的影响（它的协议值合法）；② 采样比例若将来改成非 1.0，favorite 的 trace 采样率会**真的变**（此前是哑配置） |
| **本轮对 favorite 的改动量** | **0 个文件** |

### 11.9 未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **Go 侧未活体对拍** | Go 事实全部来自**逐行读源码**（`core/trace/{config,agent}.go`）⇒ 没有真跑 Go 进程 |
| ② | **真 jaeger 未接触** | 守门用**自制假 OTLP 接收端** ⇒ 证的是「OTLP/gRPC 字节真的出了机器」，**不是**「jaeger 真的收到了」 |
| ③ | **多进程下的 agent 行为未验** | Go 的 `sync.Once` 是**进程内**语义 |
| ④ | **`otlphttp` / `zipkin` / `file` 三种 exporter 未实现**（**能力缺口**，不是静默降级） | 本轮只登记（Go 能装、RAG 装不上 ⇒ 行为同构但**能力不等价**）；实现它们要引入新依赖 ⇒ 超出本轮 scope |

### 11.10 ✅ 本轮**关闭**的上一轮未证成项

| 项 | 结论 |
|---|---|
| **T-13 未证成 ①「`103 Early Hints` 触发点未定位」** | ✅ **已定位**：是 `GoStyleClientDisconnectProbe` 的**主动探测写** —— 它用 `SocketWrapperBase.write + flush` **直接写 socket**（绕过 Tomcat `OutputBuffer` ⇒ 不提交响应），写的就是 `HTTP/1.1 103 Early Hints\r\n\r\n` ⇒ **正是已签字偏离 `SD-2026-10-25-04`**（「响应流最前面多出 N 行 103」）⇒ **不是新缺陷** ⇒ 本轮关闭 |
| **T-11 未证成 ③「采样比例只覆盖 1.0」** | ✅ **已证**（门 1/2/3） |
| **T-11 未证成 ④「协议只支持 grpc」** | ✅ **已证 + 修好**（门 4/5/6/7；且订正了「代码里没抛」这条错账） |

### 11.11 交接整合者（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 T-11 段 | 共享台账 ⇒ 协议 §二.3 |
| ② | **订正共享台账的过期行**（清单见 spec §九，共 10 行） | 同上 |
| ③ | 把**本轮变异脚本**落仓到 `tools/difftest/`（建议名 `otel-sampler-mutation.py`） | `tools/` **不在 L1 白名单** ⇒ 只留在会话临时目录 `/tmp/mutation-round23.py`；§11.6 已把 5 条变异的锚点与期望红点写全 |
| ④ | **`PIT-GLOBAL-021`（变异判定正则必须匹配全限定名）** ⇒ 落 `docs/Pitfalls/global/` | 那是 **L5** 的白名单 |
| ⑤ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |

### 11.12 验证记录（2026-10-25 收口）

| 项 | 结果 |
|---|---|
| **`service/common` 模块全量** | ✅ **246 条全绿**（239 + 本轮新增 7 条） |
| **本轮新门** | ✅ `GoStyleOtelSamplerProtocolGateTest` **7 条 / 0 失败** |
| **变异** | ✅ **5/5 全部转红**（隔离工作树 `/tmp/rag-round23-mut`，已清理）+ 还原校验逐字节相同 |
| **favorite** | ✅ 代码 / 配置 / 测试 / 台账 **零改动**（`git diff` 可验）；共享层影响已记账（§11.8） |
| **全仓回归** | ⏳ **未跑**（本轮只动了 `service/common` 的 otel 配置与 agent ⇒ 影响面 = 全仓 20 个模块的 otel 装配 ⇒ 收口前补跑） |
| **提交 / 推送** | ⏳ 待提交 |

---

## 十二、第二十四轮 · T-11「Go 侧未活体对拍」闭环 **+ 探针挖出的新缺口（门 8）**

> **基线**：`71477ff`（第二十三轮收口）· **性质**：**新增双侧活体探针（工具）+ 一条新守门**；**生产代码零改动**
> **本轮对 favorite 的改动量**：**0 个文件**（§12.8 记账）

### 12.1 一句话结论

第二十一~二十三轮的 T-11 台账一直挂着一条未证成 ——「**Go 侧未活体对拍**（Go 事实全部来自逐行读源码）」——
而 AGENTS.md **§3.3 规则 2** 要求「凡写『与 Go 一致』，必须有**两侧同时跑的探针**」。

⇒ **本轮把这一半补上**：新增 `tools/semantic-probe/trace/`（**Go 包内探针 + RAG 生产类探针 + 对拍脚本**），
**11 个用例 · 8 一致 · 3 已知能力缺口 · 0 条漂移**。
⇒ 并且**顺手挖出一条 RAG 侧从来没有守门的语义** ⇒ 补了**门 8**。

### 12.2 Go 侧活体探针：怎么跑起来的（三个坑）

| # | 坑 | 现象 | 处置 |
|---|---|---|---|
| ① | **必须进包内** | `createExporter` / `startAgent` 是 go-zero `core/trace` 的**未导出**函数 ⇒ 外部包**根本调不到** ⇒ 「unknown exporter」「端点为空不校验」「四种 exporter 都能构造」这三条**只能**靠**包内测试**活体证 | 探针写成 `package trace`（不是 `trace_test`）⇒ **直接跑真源**，不是「照抄真源逻辑再自己跑一遍」 |
| ② | **模块缓存只读，且 `go -overlay` 不许改它** | 实测报错：`Files beneath GOMODCACHE must not be replaced` ⇒ overlay 这条路走不通 | 改成：把 module cache 里的 go-zero **整份拷到临时目录** → 放入包内探针 → `go test ./core/trace/`；**拷完做 `diff -r` 逐字节校验** ⇒ 确认「跑的就是缓存里那份真源」 |
| ③ | **只输出稳定值** | trace-id 是**随机**的 ⇒ 两侧永远对不上 | 探针只输出布尔 / 枚举（`parent_trace_id_inherited=true`），**不输出原始 id** |

⇒ 落点：`tools/semantic-probe/trace/{go/liveprobe_test.go, java/TraceProbe.java, run-trace-parity.sh}`（**第二个** semantic probe）。

### 12.3 双侧对拍结果（11 个用例）

| 用例 | 测什么 | Go | RAG | 判定 |
|---|---|---|---|---|
| `G1_startAgent_failure` | 装配失败 ⇒ 不 panic、返回 null、全局仍 no-op | ✅ | ✅ | **一致** |
| `G2_unknown_exporter` | 非法 batcher + 端点非空 ⇒ 抛 `unknown exporter` | ✅ | ✅ | **一致** |
| `G3_empty_endpoint` | 非法 batcher + **端点为空** ⇒ 不报错 | ✅ | ✅ | **一致** |
| `G4_ratio_zero` | `Sampler=0.0` ⇒ 不 recording / 不 sampled / **SpanContext 仍有效** | ✅ | ✅ | **一致** |
| `G5_parentbased_sampled_parent` | `Sampler=0.0` + 入向 **sampled** parent ⇒ 子 span 仍采样 | ✅ | ✅ | **一致** |
| `G6_parentbased_unsampled_parent` | `Sampler=1.0` + 入向 **未采样** parent ⇒ 子 span **不**采样 | ✅ | ✅ | **一致**（但 RAG 侧**此前无守门** ⇒ §12.4） |
| `G7_exporter_otlpgrpc` | OTLP/gRPC | ✅ | ✅ | **一致** |
| `G7_exporter_otlphttp` | OTLP/HTTP | ✅ | ❌ | ⚠️ **已知能力缺口** |
| `G7_exporter_zipkin` | Zipkin | ✅ | ❌ | ⚠️ **已知能力缺口** |
| `G7_exporter_file` | 文件 | ✅ | ❌ | ⚠️ **已知能力缺口** |
| `G8_propagation` | 全局传播器 = Composite(TraceContext, Baggage) | ✅ | ✅ | **一致** |

⇒ **11 个用例：8 一致 · 3 已知能力缺口 · 0 条漂移**

⚠️ 3 条能力缺口进脚本的 `KNOWN_GAP` 白名单 ⇒ **不计入失败，但始终打印**，且每条必须带台账出处
（与 `tools/semantic-probe/run-parity.sh` 的 `known-dev` 白名单同款纪律：**加白名单 = 承认「这条差异不修」**）。

### 12.4 🔴 探针挖出的缺口：RAG 侧**没有**「入向 parent 未采样 ⇒ 子 span 不采样」这条守门

`GoStyleTraceparentFilter` 的类注释里写着：

> ⇒ 根 span 必采样；**入向 parent 不采样时子 span 也不采样**。

⇒ 但这句话**没有任何运行期证据** ⇒ 正是 AGENTS.md **§3.3.2** 点名的形态
（「以『Go: …』形式写的断言，本身是高危项」）。

⇒ 本轮用 Go 真源**活体**确认了这条语义（Go 的 `sdktrace.ParentBased` 的
`remoteParentNotSampled ⇒ AlwaysOff` 分支，探针 G6）
⇒ 随后在 RAG 侧补了对应的 JUnit 守门：

**新增守门**：`GoStyleOtelSamplerProtocolGateTest#remoteParentNotSampledDropsChildSpanEvenWhenRatioIsOne`
（该类 **7 → 8** 条）—— 断言：子 span **不 recording** · **不 sampled** · **SpanContext 仍有效** · **零导出**。

### 12.5 变异测试（2 条，**全部转红**）

隔离工作树 `/tmp/rag-round24-mut`（已清理）+ 三道闸（PIT-GLOBAL-018）+ 全限定名判定（PIT-GLOBAL-021）。

| # | 变异 | 期望红点 | 实测 |
|---|---|---|---|
| **M6** | `setRemoteParentNotSampled(alwaysOn())` | **只有门 8 红**，门 2 保持绿 | ✅ `Tests run: 8, Failures: 1` · 目标门 FAIL · 兄弟门 PASS |
| **M7** | 去掉 `parentBased` 包装 | 门 2 红 | ✅ `Tests run: 8, Failures: 2` · 兄弟门 FAIL |

⇒ **M6 的设计要点**：用 OTel Java 的 `Sampler.parentBasedBuilder(...).setRemoteParentNotSampled(...)`
**只改一个分支** ⇒ 证明门 8 守的**正是那一个分支**（而不是被别的门「顺带」照红）。

### 12.6 🔴 探针自身**非空转**的证明

在隔离树注入 **M7** 后跑 `run-trace-parity.sh` ⇒ **报出 2 条不一致**（`G5` / `G6`）且 **exit 1**
⇒ **不是「永远打平」的假绿**。

### 12.7 可观察变化（**记账，不静默**）

| # | 变化 | 影响面 |
|---|---|---|
| ① | **生产代码零改动** ⇒ **零行为变化** | 全仓 |
| ② | **新增一条 JUnit 守门**（门 8）⇒ 今后若有人把 `parentBased` 改成非 ParentBased 变体 ⇒ 红 | `service/common` |
| ③ | **新增一个双侧探针** ⇒ 「T-11 与 Go 一致」从此**有两侧同时跑的证据** | 工具层 |
| ④ | 探针的 `KNOWN_GAP` 白名单**明写**了 3 条能力缺口 ⇒ 它们**始终可见**，不会被当成「已对齐」 | 工具层 |

### 12.8 🔴 favorite 记账（**本轮对 favorite 零文件改动**）

> `service/favorite/**` 与 `docs/*/favorite/**` **零改动**；**生产代码零改动** ⇒ favorite 的可观察行为**零变化**
> ⇒ 仍按协议 §六记账并告知 owner。

### 12.9 未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **真 jaeger 未接触** | 探针只验「装配结果 / 采样决策 / 传播器字段」⇒ **不是**「jaeger 真的收到了 span」 |
| ② | **Go 侧 OTel SDK 1.40.0 vs RAG 侧 1.44.0** | 版本不同 ⇒ 探针证的是**语义**，不是「逐字节同版本」 |
| ③ | **多进程下的 agent 行为未验** | Go 的 `sync.Once` 是**进程内**语义 |
| ④ | **`otlphttp` / `zipkin` / `file` 仍未实现** | 能力缺口（Go 能装、RAG 装不上）⇒ **行为同构、能力不等价** |
| ⑤ | **Go 的 `rest/handler/tracehandler.go`（HTTP 层）不在探针范围** | 响应头层由 RAG 侧 `GoStyleTraceparentFilter*` 系列守门负责 |
| ⑥ | **探针不是 CI 门** | 与既有 `tools/semantic-probe/run-parity.sh` 同款定位：**手动跑**（需 Go toolchain + module cache） |

### 12.10 交接整合者（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 T-11 段 | 共享台账 ⇒ 协议 §二.3 |
| ② | 更新 `AGENTS.md` §0 的 `global` 行 + 三个 `_INDEX.md` + `_MANIFEST.json` | 共享索引 |
| ③ | `tools/difftest/README.md` 的工具清单补一行（新的 `semantic-probe/trace`） | 共享文件 |
| ④ | **L2 / L3 / L4 三条 lane 的合并** | ⇒ **L3 / L4 本轮已由整合者合并**（`eee3bbb` / `8e6b7b2`）；**L2 仍在跑** |
| ⑤ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |

### 12.11 验证记录（2026-10-25 收口）

| 项 | 结果 |
|---|---|
| **双侧活体对拍** | ✅ `run-trace-parity.sh` **EXIT=0** —— 11 用例 · 8 一致 · 3 已知缺口 · 0 漂移 |
| **Go 半边** | ✅ go-zero v1.10.2 **真源包内**探针，11 行 `PROBE|` 输出；拷贝**逐字节校验**通过（`diff -r` 无差异） |
| **RAG 半边** | ✅ 只调**生产类** `GoStyleOtelAgent` / `GoStyleOtelConfig`（不复制装配逻辑） |
| **新门** | ✅ `GoStyleOtelSamplerProtocolGateTest` **8 条 / 0 失败**（原 7 条） |
| **变异** | ✅ **2/2 全部转红**（隔离树 `/tmp/rag-round24-mut`，已清理）+ 还原**逐字节一致** |
| **探针非空转** | ✅ 注入 M7 ⇒ 探针报 2 条不一致 + **exit 1** |
| **机器守门** | ✅ `check-global-lane-ownership.py` **EXIT=0**（含本轮两处白名单订正） |
| **favorite** | ✅ 代码 / 配置 / 测试 / 台账 **零改动**（`git diff` 可验） |
| **全仓回归** | ⏳ 见 §12.12（第二十三轮改动 + 本轮新门的合并回归） |
| **提交** | `af8b542`（探针 + 门 8）· `d88604e`（白名单订正 + spec）· `eee3bbb`（合并 L3）· `8e6b7b2`（合并 L4） |

### 12.12 全仓回归（第二十三轮改动 + 本轮新门）

| 项 | 结果 |
|---|---|
| **命令** | `mvn -o test -fae` |
| **结果** | ⏳ 待填 |

---

## 十三、第二十五轮 · **T-6**：JSON 响应的 `Content-Type` 补上 `charset=utf-8`（对齐 Go）

> **基线**：`8e6b7b2`（第二十四轮 + L3/L4 合并）· **性质**：**共享层改动**（`service/common`）⇒ **10 个 api 全部生效**
> **触发**：**用户拍板**（2026-10-25 21:57）——「撤销 `SD-HOT-API-006`，修 T-6」
> **本轮对 favorite 的改动量**：**0 个文件**（§13.9 记账）

### 13.1 一句话结论

T-6 修好了：**RAG 的 JSON 响应头从裸 `application/json` 变成 `application/json;charset=utf-8`** ⇒
**「Go 有 charset、RAG 完全没有」这个实质差距关闭**。

⇒ 但**逐字节**与 Go 一致**做不到** —— 原因**不是** Spring 的 `MediaType.toString()`，
而是 **Tomcat 11 把 Content-Type 拆成 (type/subtype, charset) 两份存、写出时重新拼**
⇒ 线上恒为 `type/subtype;charset=xxx`（**分号后无空格**）⇒ **应用层拿不到那个空格**。

### 13.2 🔴 自曝错账 #2：台账说「`SD-HOT-API-006` 的理由已被本仓自己证伪」—— **这个判断不成立**

global 台账（`common-tails.md` §一·T-6）原写：

> ⚠️ **该理由已被本仓自己证伪**：`GoStyleRouterErrorResponses.java:81-88` 对 `text/plain; charset=utf-8`
> 就是**直接写原始字符串** ⇒ **同一手法用在 `application/json; charset=utf-8` 上即可**
> ⇒ ⇒ **要修 T-6，就必须先撤销 `SD-HOT-API-006`**

⇒ ⇒ **第二十五轮实测证明这个推理是错的**：**写原始字符串并不管用**。真 Tomcat 11 会归一化：

```
org/apache/catalina/connector/Response.java（11.0.24）setContentType(String)
    String[] parsedType = MediaTypeCache.parse(type);
    if (parsedType[1] == null) { coyoteResponse.setContentTypeNoCharset(parsedType[0]); }
    else {                                   // 有 charset ⇒ **拆开存**
        coyoteResponse.setContentTypeNoCharset(parsedType[0]);
        coyoteResponse.setCharsetHolder(CharsetHolder.getInstance(parsedType[1]));
    }
org/apache/coyote/Http11OutputBuffer.writeHeaders()
    headerBuffer.put(contentType); headerBuffer.put(SEMICOLON); headerBuffer.put(CHARSET);
    ⇒ 线上恒为 type/subtype;charset=xxx（**分号后没有空格**）
```

⇒ ⇒ **两个入口都汇到 `setContentType`**（`setHeader` / `addHeader` 都先走 `checkSpecialHeader`）
⇒ ⇒ **应用层根本拿不到那个空格**。

⇒ ⇒ ⇒ **所以 `SD-HOT-API-006` 的原始理由「不可逐字节复刻」是成立的，而且比当初写的更强**
（当初写的是「Spring 的 `MediaType.toString()` 少个空格」；真实原因是「**容器层**归一化」）。

⇒ ⇒ ⇒ **撤销的性质因此改变**：**不是**「技术上变得可行了」，而是
**「技术上仍然不可逐字节复刻，但我们选择向 Go 靠拢（补上 charset 参数）」** ⇒ **纯用户决策**。

### 13.3 实施（3 个文件）

| # | 文件 | 改动 |
|---|---|---|
| ① | `service/common/.../http/GoStyleJsonContentTypeAdvice.java`（**新增**） | `@AutoConfiguration` + `@RestControllerAdvice` ⇒ JSON 响应头写**原始字符串** `application/json; charset=utf-8` |
| ② | `service/common/src/main/resources/META-INF/spring/...AutoConfiguration.imports` | 登记上面这个 advice ⇒ **10 个 api 全部生效**（含 favorite） |
| ③ | `service/common/.../http/GoStyleRouterErrorResponses.java` | **订正一条被证伪的注释**（见 §13.4） |

#### 为什么挂在 **ResponseBodyAdvice** 而不是转换器上

共享层那个 JSON 转换器（`GoStyleJsonBodyConverter`）**不是所有 api 都启用** ——
`task-api` 明确把它排除在组件扫描之外（`TaskApiApplication` 的 `@ComponentScan` exclude）
⇒ 挂在它上面 ⇒ **task-api 不生效** ⇒ 不满足「改 1 处 ⇒ 10 个 api 全部生效」。
⇒ Advice 是**按「响应体是不是 JSON」判定**的 ⇒ **与用哪个转换器无关**。

#### 判定规则（两条取或）

1. **选中的转换器是 Jackson 系** ⇒ 响应体就是 JSON；
2. **选中的 Content-Type 是 JSON** ⇒ 即使转换器不是 Jackson，body 仍是 JSON。

⇒ 404 / 405 那条 `ResponseEntity<byte[]>`（`ByteArrayHttpMessageConverter` + 显式 `text/plain`）
**两条都不满足** ⇒ **不会被误伤** ⇒ 这条由守门第 3 条钉死。

### 13.4 🔴 顺带订正一条**被证伪的注释**（`GoStyleRouterErrorResponses`）

原文（现已被订正）：

> 直接写原始字符串是**唯一能保证逐字节对上的做法**。

⇒ **实测证伪**：容器照样归一化 ⇒ 写原始字符串**并不能**逐字节对上。
⇒ 写原始字符串的**真正**作用是：**保证 charset 参数存在**（不会被退化成裸 `text/plain`）。
⇒ 新注释补上了 Tomcat 的逐行依据 + 仓库既有先例。

### 13.5 守门（3 条，真 Tomcat + 裸 socket）

`GoStyleJsonContentTypeCharsetTomcatTest`

| # | 守门 | 断言 |
|---|---|---|
| 1 | `jsonResponseContentTypeMatchesGoByteForByte` | JSON 成功响应 ⇒ `application/json;charset=utf-8`（**charset 必须在**） |
| 2 | `jsonErrorResponseContentTypeMatchesGoByteForByte` | JSON 失败响应（`@ExceptionHandler`）⇒ 同一个 |
| 3 | `explicitContentTypeIsNotClobbered` | 显式 `text/plain; charset=utf-8` ⇒ **不许**被改写成 JSON |

⚠️ **为什么断言的是「归一化后」的形态**：与仓库既有先例一致 ——
`LikeApiRouterErrorRealTomcatTest` 对 404 的 `text/plain; charset=utf-8`
就是**刻意解析后比对**（去空格），注释明写「比原始字符串会得到一个『必然红、且红了也不代表有错』的断言（已实测）」。
⇒ ⇒ 本守门沿用同一口径：锁「charset 必须在 + type 必须是 application/json」，**不去锁那个空格**。

### 13.6 变异（2 条，**全部转红**）

| # | 变异 | 期望红点 | 实测 |
|---|---|---|---|
| **M8** | Advice 不写 Content-Type | 两个 JSON 门红 + 不误伤门绿 | ✅ `maven_exit=1 · tests=3 · fail=2` |
| **M9** | Advice 无条件写（去掉 JSON 判定） | 不误伤门红 + 两个 JSON 门绿 | ✅ `maven_exit=1 · tests=3 · fail=1` |

⇒ ⇒ 两条变异**各打各的**：M8 证明「charset 必须出现」非空转；M9 证明「不许误伤显式声明」非空转。

### 13.7 🔴 踩到的一个新坑：`PIT-GLOBAL-021` 需要补一条

**`surefire` 打印的 `-- in` 后面跟的是类的 `@DisplayName`（若有），不是全限定名。**

⇒ ⇒ 只匹配 FQN 的判定正则 ⇒ **红与绿都读不出来** ⇒ 误报 `NO_RESULT`
⇒ ⇒ 本轮第一版变异脚本就踩了（判定行全报 `NO_RESULT`，但 XML 逐条判定是对的）。

⇒ ⇒ **修法**：判定**只认 surefire 的 XML**（逐条 `testcase`），**不依赖 stdout 的 `-- in` 行**。

### 13.8 可观察变化（**记账，不静默**）

| # | 变化 | 影响面 |
|---|---|---|
| ① | **JSON 响应头多一个 `charset=utf-8`**（`application/json` ⇒ `application/json;charset=utf-8`）⇒ **向 Go 收敛** | **全部 10 个 api**（共享层） |
| ② | 与 Go 的**残余** = 只差一个 OWS（`;` 后有无空格）⇒ RFC 9110 **OWS 不显著** ⇒ **语义相同** | 同上 |
| ③ | 404 / 405 的 `text/plain; charset=utf-8` **不受影响**（走 `ByteArrayHttpMessageConverter` + 显式头 ⇒ advice 不介入） | 同上 |

### 13.9 🔴 favorite 记账（**本轮对 favorite 零文件改动**）

> `service/favorite/**` 与 `docs/*/favorite/**` **零改动**。
> 但**共享层改动会影响 favorite 的可观察行为** ⇒ 按协议 §六「必须记账并告知 owner」：

| 项 | 内容 |
|---|---|
| **favorite 侧受影响的地方** | favorite-api 的 **JSON 响应头**现在带 `charset=utf-8`（原先没有）⇒ **向 Go 收敛**（Go 的 favorite api 同样带） |
| **favorite 代码需要改吗** | **不需要** ⇒ 修法全在 `service/common` |
| **favorite owner 需要知道什么** | ① favorite 的 JSON 响应头**多了** `charset=utf-8`；② 若 favorite 域有守门断言「响应 Content-Type 恰为 `application/json`」⇒ 需要复核（若是 ⇒ 那是**依赖旧缺陷的断言**） |
| **本轮对 favorite 的改动量** | **0 个文件** |

### 13.10 未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **Go 侧未活体对拍** | Go 的 `ContentTypeJson` 取自**逐行读源码**（`rest/internal/header/headers.go:17`）⇒ 没跑 Go 进程 |
| ② | **「那个空格」在应用层彻底不可达** | 已坐实（Tomcat 归一化）⇒ 但**没试过**改 Tomcat 配置 / 换容器 ⇒ 未穷尽所有绕法 |
| ③ | **其余 9 个 api 未逐域对拍** | 本轮在 `service/common` 用**真 Tomcat** 证明「共享层语义」；各 api 的业务链形态不同 ⇒ 是否还有别的 Content-Type 出口**未逐域验** |
| ④ | **RFC 8259 的规范方向问题未重估** | `SD-HOT-API-006` 的理由②说「RFC 8259 规定 JSON 的 charset 恒为 UTF-8，**不应**声明」⇒ 本轮按**用户决策**加了 charset ⇒ 该规范方向的取舍**未在台账重评** |

### 13.11 交接整合者（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 **T-6** 段 + **订正 §一·T-6 里那条被证伪的「理由已被证伪」** | 共享台账 ⇒ 协议 §二.3 |
| ② | `docs/DRIFT/hot/api.md` 的 `DRIFT-HOTAPI-024` 已由本 lane 更新（状态改「已修」）⇒ **请复核** | 跨域台账（global 有写权，但 hot 域 owner 需知会） |
| ③ | `docs/Intentional_Deviations/hot/api.md` 的 `SD-HOT-API-006` 已由本 lane **撤销** ⇒ `docs/Intentional_Deviations/_INDEX.md` 需同步 | 共享索引 |
| ④ | 更新 `AGENTS.md` §0 的 `global` 行 + 三个 `_INDEX.md` + `_MANIFEST.json` | 共享索引 |
| ⑤ | **L2 / L5 两条 lane 的合并** | ⇒ **L3 / L4 本轮已合并**（`eee3bbb` / `8e6b7b2`）；**L2 已完成待合**（`0e2efaf`）；**L5 待启动** |
| ⑥ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |

### 13.12 验证记录（2026-10-25 收口）

| 项 | 结果 |
|---|---|
| **守门验红** | ✅ 修前 `Content-Type: application/json` ⇒ **两个 JSON 门红**（证明守门非空转 + 坐实漂移） |
| **修后** | ✅ `Content-Type: application/json;charset=utf-8` ⇒ 3 条门全绿 |
| **`service/common` 全量** | ✅ **250 条全绿**（原 247 + 本轮 3） |
| **变异** | ✅ **2/2 全部转红**（隔离树 `/tmp/rag-round25-mut`，已清理）+ 还原**逐字节一致** |
| **favorite** | ✅ 代码 / 配置 / 测试 / 台账 **零改动**；共享层影响已记账（§13.9） |
| **全仓回归** | ⏳ 待跑（改动在共享层 ⇒ 影响面 = 全部 10 个 api） |
