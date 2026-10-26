<!-- @move AGENTS.md L57-L71 · §0 like-rpc 事实投递链路跨三个仓库 -->

> 🟥 **当前态指针（2026-10-25 台账校准轮回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（首行是 2026-10-22 的「跨三仓库」速记，正文已到**第五轮** + 2026-10-24 的台账审计）⇒ **当前状态一律以文件末尾「like-rpc 第五轮（2026-10-24）+ 「2026-10-24 台账审计（like-rpc 侧）」」一节为准**。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**
### 🔴 下一个 agent 请先读这条：`like-rpc` 的事实投递链路**跨三个仓库**（2026-10-22 新发现）

> **`Sea-BreakTheWaves` 是本项目的第三个仓库**：`/Users/pr/Documents/GitHub/Sea-BreakTheWaves`。
> 它是 like 领域事实的**下游消费者** —— 消费 `usermodel.like-facts.v1`，并通过
> `GET /internal/v1/community/facts` **回调 like 的权威端点**做逐字段核对才肯落库。
> ⚠️ **先前只搜了 `Sea-RideTheWind_golang` 与 `RAG` 就断言「DC 接收端不存在」是错的**
> （§3.3 纪律的又一次真实发作：否定式断言不可信，跨仓库尤其）。
> 完整拓扑、冻结的权威端点契约、6 个骨架文件清单、12 条漂移、8 条错误断言、
> 31 条测试的盲区分析 ⇒ 见 **`service/like/like-rpc` 小节**。
> 🔴 **2026-10-24 更新（第五轮后）**：上句的「12 条漂移 / 8 条错误断言 / 31 条测试」是**首轮（2026-10-22）**的数字 ——
> 漂移编号已排到 **018**，错误断言已清到 **13 条**，测试已 **57 条**；
> 且**首轮「NOT EQUIVALENT」的 3 条 CRITICAL/HIGH 已修掉 2 条**（第 3 条 = 业务码载体迁移 ⇒ 签字偏离 SD-…-06）。
> ⇒ **当前状态见文件末尾「第三轮 / 第五轮」章节与下方「📌 当前状态」速览。**
>
> **同时注意两个仍未解决的前置项**：
> ① ~~`like-rpc` **从未做过一次 Spring 上下文启动测试**（与 §6.6.5 message-rpc 同型）~~
>    🔴 **2026-10-23 第三轮起已不成立** —— 现已有 `LikeRpcSpringContextWiringTest`（**9 条**，**真 PG Testcontainers**），
>    且它当场抓出了**4 条独立启动阻塞**（`spring-boot-kafka` 缺失 / Jackson 2 Bean 缺失 /
>    MP 排序桥接缺失 / `TaskExecutor` 注入歧义）—— 即「从没起过」这件事**本身就是缺陷**，现已关闭；
> ② dispatch / authority 两个 Go 入口**只经 DB 耦合**，与 like-rpc 的 gRPC 面无任何调用关系
> ⇒ 它们**不必须**随 like-rpc 一起 Java 化，这是「要不要重实现」的前置判断点。

---

## 📌 当前状态（2026-10-24 · 第五轮后）

| 项 | 值 |
|---|---|
| **当前三值判定** | 🔴 **仍 `SEMANTICALLY NOT EQUIVALENT`** —— 但**构成它的条目大部分已修**：首轮 12 条里 **13 条已修 / 1 条证伪**，**只剩事实投递链路（003）这条真的还开着** |
| **仍未修的漂移** | **DRIFT-LIKE-003**（事实投递链路 6 个骨架）—— 需新建**独立进程** + 真 DC 接收服务**不在任何仓** ⇒ `UNRESOLVED` |
| **仍未修的测试缺口** | **L-U9**（真 Kafka 活体证据，`spring-kafka-test` / `testcontainers-kafka` 不在本地 m2）· **L-U11 的 `rpcInterceptorConfigurer` 半边**（🔴 2026-10-24 审计：**留着的理由已过时** —— gRPC server 早就起了，**路径已钉死**：发慢请求看客户端是否 2s 后收到 `DEADLINE_EXCEEDED`；只差实现） |
| **有意不修** | **L-U10**（`LeastBytes` 分区器 + Snappy）⇒ OBSERVABLE，已登记 |
| **已签字偏离** | SD-2026-10-23-06（gRPC 业务码载体迁移＝**框架限制**）· SD-2026-10-23-07（Nacos 注册失败不退出）· SD-2026-10-23-05（proto `package` 归一） |
| **测试** | like-rpc **57 / 0 failures**（含真 PG Testcontainers）；like-api **81 / 0** |
| **本轮改动落点** | 工作树 `RAG.worktrees/like` 分支 `align/like`，**未 push、未建 issue、未开 PR** |

⚠️ **本文件按「轮次」追加**：§0~§10 是**首/二轮**的原始记录、**原文不改**；
第三轮与第五轮的结论在文件末尾对应章节。⇒ **不要**把 §10.2 的判定表当成当前构成。

---

<!-- @move AGENTS.md L1143-L1342 · §0 like-rpc 12 条漂移 -->
### 🔴 `service/like/like-rpc`：已落地，独立子会话验证判定 **NOT EQUIVALENT**，12 条漂移（2026-10-22）
> 🔴 **2026-10-24 更新（第五轮后）**：标题里的「12 条漂移」是**首轮**数字 ——
> 漂移编号现为 **001~018**（其中 **13 条已修**、**1 条证伪**、**3 条是启动阻塞已修**、**1 条待签字**），
> 测试从 **31 条 → 57 条**。⇒ **首轮判定 `NOT EQUIVALENT` 仍成立**，但构成它的具体条目**大部分已被修掉**。

> 本节是独立 verifier 子会话（Go → Spring Boot 基础组件语义一致性验证）的裁定。
> 模块现状：Go `service/like/rpc` → Java `service/like/like-rpc`，主源 39 文件 / 5247 行
> + 5 份 Flyway 迁移，**31 tests 全绿**（Redis 降级 8 / 幂等状态机 11 / 基础件 9 / gRPC wire 3）。
> 🔴 **2026-10-24 更新（第五轮后）**：like-rpc 现有 **57 tests / 0 failures**（真 PG Testcontainers）——
> 新增：`LikeDomainFactSchemaParityTest` 8 / `LikeRpcSpringContextWiringTest` 9（**7 → 9**）/ `LikeGoAlignmentTest` 9 / `LikeServiceGrpcImplWireTest` 3 等。
> ⚠️ **31 条绿测试不能推翻此判定** —— 三条 CRITICAL/HIGH 漂移恰好全部落在
> 「需要真 DB / 真 Redis / 真 Kafka / 跨语言对拍」的缺口里（见末尾盲区分析）。
> 🔴 **2026-10-24 更新**：那三条 CRITICAL/HIGH 现已**修掉 2 条** ——
> ①「组件起不来」已修（4 条启动阻塞）+ ②「业务码丢失」已定性为**框架限制**（签字偏离 SD-2026-10-23-06）；
> **剩下 1 条**是事实投递链路（`DRIFT-LIKE-003`）—— 需新建独立进程 + 真 DC 接收服务不在任何仓 ⇒ `UNRESOLVED`。

#### ✅ 已签字：DRIFT-LIKE-001 按「Java 现状即为已修正」处理（2026-10-22）

`GetUserLikeList` / `GetTargetLikerList` 的 DB 回源，Go 因 GORM 列名不匹配
（`CreateTime` → `create_time`，而 `Select` 取 `created_at`）导致 timestamp **恒为 0**，
`if timestamp==0 { continue }` 对每行成立 ⇒ **列表恒空**；而 Redis ZSet 的 ZADD 只在
回源成功后才执行 ⇒ **回源路径从功能上是死的**，且返回 `isEnd=false` + `nextCursor=0` 的矛盾态。
**实证**：真 PG + gorm v1.31.1，`schema.LookUpField("created_at")` 返回 `nil`，插入真实行后
`Scan` 得 `CreateTime=0`（期望 `1792670400`）。Java 侧 `LikeRecordMapper.java:121,139-147`
显式映射，返回真实数据。

⇒ **签字：Go 的空列表是缺陷不是设计，Java 现状为已修正的 bug，两侧不一致属有意，不再对齐。**
⚠️ 边界必须同时记住：Go 侧该功能**冷缓存下不可用**；灰度期两端同跑时
**Java 会返回 Go 永远返回不了的数据**——这是修正的代价，不是新缺陷。

#### 🔴 跨仓库拓扑：事实链路的下游在**第三个仓库** `Sea-BreakTheWaves`（2026-10-22 新发现）

> ⚠️ **本条修正了一条错误的否定式断言**：先前判定「DC 接收端不在本仓、无法验证」，
> 实际接收方在 `/Users/pr/Documents/GitHub/Sea-BreakTheWaves`。
> AGENTS.md §3.3 的纪律在此再次生效：**否定式断言默认不可信**——
> 「grep 不到实现」≠「不存在」，**跨仓库时尤其**。当时只搜了两个目录就下了结论。

```
Sea-RideTheWind_golang/service/like/rpc
  ├─ appendLikeFact      写 like_domain_fact_outbox（与状态变更同一事务）
  ├─ cmd/fact-dispatch   POST 外部 DataCenter {DC_PLATFORM_EVENT_URL}/v1/events
  │      delivery_status: 0 待投递 / 1 ACCEPTED / 2 FAILED(可重试) / 3 BLOCKED(终止重试·需人工)
  └─ cmd/fact-authority  GET  /internal/v1/community/facts        ◀───┐ HTTP 回调
Sea-BreakTheWaves/service/async/rpc/cmd/worker                        │
  └─ 消费 job type「usermodel.like-facts.v1」                        │
     ├─ 断言 DC 回执（producer / event_id / input_hash / offset /
     │        receipt_id / technical_status==accepted 全部对得上）     │
     └─ 回调 like 权威端点逐字段核对事实，才肯落库 ─────────────────────┘
```

**Evidence（Go 侧）**：
- `service/like/rpc/cmd/fact-dispatch/main.go:52-55`（`LIKE_DATABASE_URL` / `DC_PLATFORM_EVENT_URL` /
  `DC_PLATFORM_SERVICE_TOKEN`，token 长度 <32 **直接拒绝启动**）、`:72`（HTTP 8s 超时）
- `service/like/rpc/internal/model/fact_delivery.go:52-126`（`DispatchFactOnce`：
  `FOR UPDATE SKIP LOCKED` 认领 → 前驱版本必须全部 ACCEPTED → 投递 → 结算；
  冻结信封校验不过 ⇒ **BLOCKED 终止重试**要求人工迁移，失败 ⇒ FAILED 可重试）
- `service/like/rpc/cmd/fact-authority/main.go:71`（`NewAuthorityHandler("rtw.like-mq", ...)`）
- `service/common/communityfact/authority_http.go:28`
  （`mux.HandleFunc("GET /internal/v1/community/facts", ...)`）
- `service/common/communityfact/wire.go:87`（DC endpoint 的 path **必须恰为** `/v1/events`）

**Evidence（`Sea-BreakTheWaves` 侧 = 下游消费者）**：
- `service/async/rpc/cmd/worker/config.go:23`（`likeFactJobType = "usermodel.like-facts.v1"`）
- `service/async/rpc/cmd/worker/config.go:167`（like / comment / favorite 同属 community fact job）
- `service/async/rpc/cmd/worker/config.go:175,196`（`BTW_COMMUNITY_AUTHORITY_URL` / `_TOKEN`）
- `service/async/rpc/cmd/worker/favorite.go:183-186`（`case likeFactJobType` → `NewCommunityAuthorityBinder`）
- `service/async/rpc/internal/app/community_authority_binder.go:46-62`
  （BaseURL **path 必须为空**、token ≥32、3s 超时）、`:110-113`（回执断言）、
  `:116-118`（**确切请求 URL**）、`:65-71`（响应体形状）
- `service/async/rpc/cmd/worker/community_process_integration_test.go:105`
  （like 用例读 `SEA_LIKE_AUTHORITY_URL` / `SEA_LIKE_AUTHORITY_TOKEN`）

#### ✅ 权威端点契约已冻结（`LikeAuthorityEndpointConfig` 原「⚠️ UNKNOWN」注释作废）

| 项 | 冻结值 |
|---|---|
| 方法 + 路径 | `GET /internal/v1/community/facts` |
| 查询参数 | `producer`（仅 `rtw.like-mq` / `rtw.comment-mq`）、`event_id`（≤128 字符） |
| 鉴权 | `Authorization: Bearer <token>`，token **≥32 字符**，否则拒绝装配 |
| 超时 / 上限 | 调用方 3s；响应体 ≤ 256 KiB；**strict JSON**（未知字段即失败） |
| 响应体 | `{event, subject_ref:{issuer,subject_id}, predecessor_event_id?, technical_receipt:{...}, source_event_hash}` |

⇒ 仍需给 like-rpc **新增 HTTP connector**（BaseURL 不能带 path ⇒ 不能挂在 gRPC 端口之后），
但性质从「UNKNOWN 待拍板」降级为「按已冻结契约实现，端口走配置」。

#### 🔴 DRIFT-LIKE-003 改判：事实投递链路缺失，**由「建议关停」翻转为「必须补齐」**

Java 侧 **6 个文件**是 4 行骨架（verifier 报 5 个，**漏计 `LikeFactDispatchScheduler`**）：
`LikeFactProcessLogging` / `LikeAuthorityEndpointConfig` / `LikeFactDispatchScheduler` /
`LikeAuthorityLookupService` / `LikeDeliveryValidator` / `LikeFactDispatchService`。
而生产侧 `LikeDomainFactService`（175 行）**完整**，`LikeDomainFactOutboxMapper` 的
`claimNextDeliverable` / `markBlocked` / `markDeliveryFailed` / `markDelivered` /
`selectByEventId` / `selectByAggregateIdOrderByVersion` 也**全部就绪且零调用方**
⇒ **只写不送**：`delivery_status` 恒 0、`retry_count` 恒 0、
`technical_receipt_id` / `technical_input_hash` / `technical_offset` /
`technical_received_at` / `delivered_at` 五列恒空。

⚠️ **一次反向误判已被证伪并记录在此**：曾建议「关停事实生产 + 留档偏离」，
其核心论据是「接收端不在本仓、无法验证」与「契约 UNKNOWN」——**两条均被 `Sea-BreakTheWaves` 推翻**。
不补的后果不止「表无限增长」：会让 `Sea-BreakTheWaves` 的 **usermodel 画像永久缺 like 的全部
交互事实**（静默缺失，最难发现的一类故障）。

⚠️ **唯一仍为真的证据边界**：真正的 DC 接收服务 `/v1/events` **在两个仓里都只有发送方/客户端**
（`Sea-BreakTheWaves/service/common/clients/datacenter/client.go:153,164` 是客户端）
⇒ **投递环节只能对 stub server 验证，不能对真 DC 验证**。

#### ⚠️ 决策前提：dispatch / authority 与 like-rpc **只经 DB 耦合**

两个 Go 入口都是**独立进程、纯 DB 客户端**，不调用 like-rpc 的任何 gRPC 方法
（`cmd/fact-dispatch/main.go:52`、`cmd/fact-authority/main.go` 均只取 `LIKE_DATABASE_URL`，
投递目标由 `DC_PLATFORM_EVENT_URL` 决定，与 like-rpc 的服务端口无关）。
⇒ **生产侧（`appendLikeFact`，与状态变更同事务）必须在 Java like-rpc 内；
投递侧与权威读侧可以继续留在 Go**，只需让 Flyway DDL 与 GORM struct tag 的列名/类型保持一致。
⚠️ 但这条跨语言 DDL 耦合本身就是 DRIFT-LIKE-001 那类缺陷的来源（GORM 列名不匹配 ⇒ 静默返零），
⇒ 若决定留 Go，**必须补一条 DDL ↔ GORM 列名一致性静态校验**（`tools/check-ddl-static.py` 可扩展），
否则等于把一个**已经真实发生过**的缺陷留在跨语言边界上。

#### 📊 性能对照：Java 化**不会**让它变慢（2026-10-22 核实，非估算）

> 结论先行：**瓶颈不在语言层。** Java 化的真实差异是**内存占用**，不是吞吐。
> ⚠️ 「JVM 常驻 150-300MB」是 JVM 通用基线，**未为本服务实测 RSS**。

**负载实测特征**：`communityfact/process.go:43-61` 的投递循环是**严格串行**的
（`for i := 0; i < 16; i++ { dispatch() }`，无第二个 goroutine 并发）⇒ **进程内 DB 连接占用峰值为 1**。
单次 `DispatchFactOnce`（`fact_delivery.go:52-126`）= 4 次 PG 往返 + **1 次 HTTP POST（8s 超时）**
⇒ **时间 95%+ 花在等网络**，吞吐上限 16 次/秒。
⇒ **GC / JIT / 启动时间 / 堆大小全部不影响**。

| 维度 | Java vs Go |
|---|---|
| 吞吐 / 单次延迟 | **无差别**（串行 + I/O 主导，瓶颈是 DC 延迟） |
| GC 抖动 | **无实质差别**（payload ~600B、分配速率极低，pause 淹没在 3s/8s 超时里） |
| authority 并发 | Java 略优（Tomcat 线程池 vs goroutine-per-request 的调度开销），两边都远超实际需要 |
| **常驻内存** | **Java 大 10-20 倍**（Go 10-30MB；JVM 150-300MB 起 = 堆 + metaspace + code cache + 线程栈 + JIT 区）。⚠️ 容器化必须配 `-XX:MaxRAMPercentage`，否则 OOMKill。**这是「怪物」的真实形态——不是慢，是大** |
| 连接池 | **Java 更安全**：Hikari 默认 `maximumPoolSize=10`；Go `database/sql` 默认 `MaxOpenConns=0`（**不限**）+ `MaxIdleConns=2` ⇒ **Go 那个「不限」才是多副本下的真风险**（打爆 PG `max_connections`） |

#### 🔴 架构更正：`LikeFactDispatchScheduler` **不得**用 `@Scheduled` 内嵌进 like-rpc（2026-10-22）

原骨架注释「驱动 common Dispatcher（@PreDestroy → handle.stop()）」暗示进程内调度，**方向错误**。
Go 的 `DispatchFactOnce` 是**在事务里发 HTTP**：`Begin` → 行锁 → POST → `Commit`，
而 `process.go:28` 给每次 attempt 套了 **10s context** ⇒ **行锁最长持有 10 秒**。
作为独立进程时这只影响 outbox 表；**塞进 like-rpc 进程后会新增两个 Go 现在没有的耦合**：
① 与 gRPC 服务共享同一进程的连接池 / 线程池，一个卡住的出网调用会拖累主服务；
② 故障不隔离（dispatcher 卡死 ⇒ like-rpc 一起卡）。
⇒ 若决定 Java 化，**必须保持独立进程**（独立模块或独立 Spring Boot 应用），不是 `@Scheduled`。
**这一条比「用 Go 还是 Java」重要得多。**

#### ⚠️ 与语言无关的真隐患：`AuthoritativeFact` 是 O(N) 全量重放

`fact_authority.go:69-73` 每次请求 `SELECT ... WHERE aggregate_id=? ORDER BY fact_version` **拉全部行**从头重放，
而 aggregate 是 `like-state/{userId}/{targetType}/{targetId}` ⇒ 用户对同一目标反复操作 N 次，则单次权威查询 O(N)。
**Go 同样如此，重写成 Java 一点都不会改善。** 若 N 会无界增长，该改的是算法（增量重放 / 只验最近窗口），
不是语言。⚠️ **N 的真实分布尚未取证**，本条列为待评估，不作结论。

#### 其余 11 条漂移摘要（明细见 verifier 报告；均未修复）

| ID | 差异 | 分类 | 风险 |
|---|---|---|---|
| **DRIFT-LIKE-002** | 业务码对**未迁移的 Go like-api** 丢失：Go 直发 `grpc-status: 5003`；grpc-java `fromCodeValue(5003)` → `UNKNOWN(2)`（实测 `desc="Unknown code 5003"`，`Code.values().length == 17`），Java 改放自定义 trailer `rag-like-biz-code`，而 Go like-api 只读 `int(st.Code())` ⇒ 灰度期 envelope code 变 **2**、msg 因 `GetErrMsg(2)` 落表外而回落 `"FAIL"` | SEMANTIC MISMATCH | 🔴 |
| DRIFT-LIKE-004 | Kafka 消费侧 Java 多 **9 次进程内重试**：`new DefaultErrorHandler(recoverer)` 内部即 `SeekUtils.DEFAULT_BACK_OFF = FixedBackOff(0,9)`；Go `kq` 的 `ForceCommit` 默认 true 且未注册 errorHandler ⇒ **0 次**，失败只不提交位点 | OBSERVABLE | 🟠 |
| DRIFT-LIKE-005 | Kafka key/分区：Go `Push` 内部即 `PushWithKey(UnixNano)` 且用 `LeastBytes{}` 分区；Java `send(topic,null,payload)` 走默认分区器 | OBSERVABLE | 🟠 |
| DRIFT-LIKE-006 | 模块**从未做过 Spring 上下文加载测试**（无 `@SpringBootTest` / `ApplicationContextRunner`），而 javadoc 点名的 4 个 wiring 测试**全部不存在** —— 与 §6.6.5 message-rpc 同型<br>🔴 **2026-10-23 修复轮更正：部分过期** —— 已新增 `LikeRpcSpringContextWiringTest`（7 条 / 真 PG）且实跑全绿；真实缺口缩到 2 处（`rpcInterceptorConfigurer` 未验证是否真挂上 gRPC 调用链、`@EnableScheduling` 无守门）。详见第三轮 §5 L-U11<br>🔴 **2026-10-24 第五轮再更正：真实缺口缩到 1 处** —— `@EnableScheduling` 那半**已补守门测试**（同一个 wiring 测试类，**9 条**）：① `ScheduledTaskHolder` **真的持有** relay 的 `@Scheduled` 任务 ② 那个任务**真的执行过**（`Task.getLastExecutionOutcome().status() != NONE`）。⇒ 剩下的**只有** `rpcInterceptorConfigurer`（见 §5 L-U11） | UNKNOWN | 🟠 |
| DRIFT-LIKE-007 | 未知字段从「忽略」变「丢弃」：`@JsonIgnoreProperties(ignoreUnknown=false)` 覆盖了 Jackson 3 的默认忽略 ⇒ 抛异常 ⇒ 记 `json_error` 并**提交位点**（永久丢失、不可恢复的消息黑洞） | SEMANTIC MISMATCH | 🟡 |
| DRIFT-LIKE-008 | map 形式 `Updates` **会**刷新 `updated_at`（真 PG 实测：`2020-01-01 08:00` → `2026-10-22 19:48:54.598+08`）；Java `updateState` 不刷新 ⇒ `idx_user_list(user_id, updated_at)` 内容两侧不同 | OBSERVABLE | 🟡 |
| DRIFT-LIKE-009 | 指标 label 漂移 4 处：`redis_read_error` 的 target_type 塞字面量且 action 恒 `"n/a"`；Go「字段不存在」也记指标而 Java 直接返回；Go `GetLikeState` 记 `redis_error` 而 Java 不记；Java 把序列化失败并入 `kafka_error` | OBSERVABLE | 🟡 |
| DRIFT-LIKE-010~012 | `.trim()` 容忍空白（Go `ParseInt` 不忽略） / outbox `ON CONFLICT` 目标（Go 无目标、覆盖任意唯一约束冲突） / 可选 producer 开关粒度（Go 两个 pusher 独立判空，Java 合并为单个 `taskPushersEnabled`） | OBSERVABLE | 🟡 |

#### ⚠️ Java 侧 8 条错误断言（其中 5 条经真框架实测证伪，优先级等同缺陷）

| # | 断言内容 | 实测事实 | 出处 |
|---|---|---|---|
| **A** | 「GORM 扫 `created_at` 取的是 `data.Unix()` 即秒，秒/毫秒刻度不一致是真实可观察行为，原样保留」 | GORM **根本没匹配上该列**，`timestamp` 恒 0，整段分析描述的是一条**不可达路径** | `LikeDomainService.java:374-386,944-951` |
| **B** | 「Go 的 `Updates` 用 map 形式不会带上 `updated_at`，保持一致」 | map 形式**会**带上（`ConvertToAssignments` map 分支显式追加），**真 PG 实测** | `LikeRecordMapper.java:194-200` |
| **C** | 「`kq.Pusher.Push(ctx, msg)` **不带 key**」 | `Push` 内部即 `PushWithKey(ctx, strconv.FormatInt(time.Now().UnixNano(),10), v)` ⇒ **带 key** | `LikeDomainService.java:1014`、`LikeHotEventOutboxRelay.java:80` |
| **D** | 「Go `Push` 是**同步**阻塞写，所以这里选 acks=1 + 异步」 | `NewPusher` 默认挂 `ChunkExecutor`，`Push` 只入队并立即返回 ⇒ **异步缓冲**，写错误只出现在 `logx.Error`，永不返回调用方 | `LikeDomainService.java:984-985`、`application.yml:91-94` |
| **E** | 「不做进程内重试……若在 Java 侧加 `FixedBackOff` 做 N 次进程内重试，那是**新增行为**」 | `new DefaultErrorHandler(recoverer)` **内部就是 `FixedBackOff(0,9)`** ⇒ 声称「不做」的事正在发生 | `LikeUpdateKafkaListener.java:44-52,170-175`、`LikeRpcApplication.java:105-110` |
| **F** | 「这五个装配点**每一条都有对应的 wiring 测试锁死**」并点名 2 个测试类 | **4 个测试类一个都不存在**（`LikeMessageIngestWiringTest` / `LikeGrpcInterceptorWiringTest` / `LikeSchemaGateTest` / `NacosRegistrationTest`），模块零 Spring 上下文测试 | `LikeRpcApplication.java:35-55,88`、`LikeMessageIngestService.java:24-26` |
| **G** | outbox relay 注释「`Push` **不带 key**，且用 payload 原文」 | 同 C。「用 payload 原文」这半句是对的 | `LikeHotEventOutboxRelay.java:80` |
| **H** | 「Go 侧 etcd 注册失败会让 `MustNewServer` panic ⇒ 进程退出」 | **未核实**（`zrpc.MustNewServer` 的 etcd 失败路径未查证），标 UNKNOWN | `NacosRegistration.java:91-92` |

⇒ **A / C / D / E 是同一型错误**：只读框架源码或文档，对 Go 侧下了**否定式或类比式断言**而未做实证，
且其中 A / D 描述的代码路径在 Go 侧**根本不可达**。**B 是照搬 GORM 直觉未验证。**
**F 是「把没看过的东西当成看过了」的又一例**（§3.3 同款）。
⇒ 修相邻代码时应顺手清理这 8 条注释，否则会让下一个人沿错误前提继续改。

#### ⚠️ 现有 31 条测试的盲区（解释 3 条 CRITICAL/HIGH 为何全部逃过）

| 测试类 | 真正证明了「与 Go 一致」的部分 | 逃过了什么 |
|---|---|---|
| `LikeDomainServiceRedisDegradationTest`(8) | ✅ Redis 降级语义（Redis 挂/超时都回源 DB、DB 失败才致命、Lua 失败才致命） | ❌ 全是 Mockito mock `StringRedisTemplate`，**从未连过真 Redis**；`getUserLikeList` 只有一条验 `fetchLimit` 参数的用例，**完全不验回源结果** ⇒ 001 逃过 |
| `LikePrimitivesTest`(9) | 部分：`scoreOf/timestampOf` 互逆、并发唯一性、Lua 返回码常量、key 字面量 | ❌ `htmlEscapingMatchesGoEncodingJson` 只验 **Java 自己前后一致**，未与 Go 实跑对拍 |
| `LikeMessageIngestServiceTest`(11) | ✅ 幂等三层顺序、hash 冲突报错重投、单调守卫、软删拒绝、状态机转移 —— 真对 Go 逐行写的 | ❌ 全 mock Mapper，**`@Transactional` 是否真生效（代理有没有被绕过）零证据** |
| `LikeServiceGrpcImplWireTest`(3) | ✅ 业务码经真 in-process gRPC 链路往返、文案保真、同码不同文案 | ❌ 只测 **Java like-api → Java like-rpc**；**无任何「Go like-api → Java like-rpc」验证** ⇒ 002 逃过 |

⇒ **这是系统性盲区不是偶然遗漏**：31 条测试在「降级语义 / 幂等状态机 / 文案保真」三条线上是真对 Go 的，
但它们系统性地绕开了所有需要真 DB / 真 Redis / 真 Kafka / 跨语言对拍的断言。

#### ❌ 8 条 UNRESOLVED（尚无运行证据）

U1 上下文能否启动 · U2 拦截器是否真挂上 gRPC server · U3 Flyway 是否真建表 ·
U4 `@Select` 承载 `INSERT…RETURNING` 与 `:jsonb` 在真 PG 的行为 · U5 真 Kafka 位点行为 ·
**U6 `KafkaTemplate.send` 既无 `.get()` 也无 `whenComplete` ⇒ 异步失败完全静默**
（叠加 DRIFT-005，`kafka_error` 指标几乎永远打不出来） · U7 `MustNewServer` etcd 失败是否 panic ·
U8 `HotspotTTL` 读取点（两侧都不读，无行为影响，但 `LikeRpcProperties` 仍映射了该属性且注释自相矛盾）。
⚠️ **U1 的教训与 §6.6.5 完全同型**：message-rpc 是「21 条全绿却一次都没真正启动过」，
like-rpc 复制了同一套装配代码（`@MapperScan` / `@Import` / `GrpcServerConfigurer` /
`@EnableScheduling` / 自定义 `kafkaListenerContainerFactory`）**却同样零证据表明能起来**。

---
---

# like-rpc 语义对照台账（第二轮 · 组件级 · 2026-10-23）

> **本节是第二轮复核**（首轮 = 2026-10-22，见上方「🔴 `service/like/like-rpc`：… 12 条漂移」节，**原文一字未改**）。
> **核对日期**：2026-10-23　**范围**：`Sea-RideTheWind_golang/service/like/rpc`（36 个 `.go`，全量读，未抽样）
> ↔ `RAG/service/like/like-rpc`（39 个主源文件 + 5 份 Flyway 迁移 + 4 个测试类 / 31 条测试）
> **方法**：`.agents/specs/semantic-verification-prompt.md` 全部 40 章节 + 本项目 8 条硬约束
> **真源**：Go（AGENTS.md §1.1，2026-10-20 起）

## 0. 本轮结论摘要

| | 内容 |
|---|---|
| **最终判定** | **SEMANTICALLY NOT EQUIVALENT** |
| **新增漂移** | **6 条**：SEMANTIC MISMATCH **4** 条（DRIFT-LIKE-013 ~ 016，全部是「服务起不来」）+ OBSERVABLE DIFFERENCE **2** 条（DRIFT-LIKE-017 ~ 018） |
| **推翻的历史结论** | **3 条**：① 首轮 U1「上下文能否启动」→ **已证实不能**（实测 4 个独立硬阻塞）；② 首轮 U3「Flyway 是否真建表」→ **已证实会**（真 PG 16 建出 `flyway_schema_history_like` + 5 张表）；③ AGENTS.md §3.3.3 与 `docs/Pitfalls/like/rpc.md` 对本组件的「**静默**失效」表述 → **修正为「硬启动失败」**（详见 DRIFT-LIKE-015） |
| **Boot 4 Kafka 缺依赖** | **实测失效**，且失效形态与既有记载**不同**：不是「上下文照常启动、一行消息都不消费」，而是 **Spring 上下文直接启动失败**（`NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.kafka.core.KafkaTemplate'`） |
| **关键 unresolved** | 事实投递链路在 Java 侧**仍是 6 个 4 行骨架**（投递侧 + 权威读侧）；真 DC `/v1/events` 接收服务不在任何仓内 ⇒ 链路末端不可验证（U9） |

### 0.1 证据强度声明（本轮）

| 编号 | 证据类型 | 具体手段 | 能证明什么 / 不能证明什么 |
|---|---|---|---|
| **E-Boot** | **RAG 真容器启动（真 PG 16 + 真 Redis 7 + 真 Kafka + 真 Nacos）** | 本机起 `postgres:16-alpine`(55433) / `redis:7-alpine`(56380)，用**生产 classpath**（`mvn dependency:build-classpath` + `target/classes`）跑 `com.rag.like.like.rpc.LikeRpcApplication`，逐个击穿启动阻塞；另用 `--debug` 取 **ConditionEvaluationReport** | 装配层真实行为、Bean 缺失、启动成败。**不含**任何业务请求 |
| **E-Cp** | **classpath 事实** | `mvn -o -pl service/like/like-rpc dependency:tree` / `build-classpath`；`Class.forName` 探针 | 「某个自动配置类在不在 classpath 上」「某个 Bean 定义在不在容器里」 |
| **E-CTX** | **真 Spring 容器最小上下文** | 自建 `AnnotationConfigApplicationContext`，注册**生产类** `LikeHotEventOutboxRelay` + 探针 `@KafkaListener` 类，做「有/无 `spring-boot-kafka`」对照 | Bean 创建失败点、`KafkaListenerAnnotationBeanPostProcessor` 是否注册。**不含** Kafka 真实收发 |
| **E-PG** | **真 PostgreSQL 16** | 真 PG 上跑 Flyway 迁移后 `information_schema.tables` / `\dt` | 表与账本表**实际**建出来没有；列类型 |
| **E-Src** | **静态源码** | 两侧逐行对照；Go 侧引用到**消费点**行号（含 go-zero / go-queue / gorm 的**依赖源码消费点**） | 只证明「代码写成了这样」，**不证明运行期真的这样** |
| **E-Cfg** | 配置 + 装配源码 | yaml ↔ struct tag ↔ 自动配置注册链 | 「配置与装配代码都在」，**不等于运行期生效**（AGENTS.md §3.3） |

> ⚠️ **本组件 31 条绿测试一条都不是本轮证据**：它们全部是**纯 Mockito** 或**纯 in-process gRPC**，
> 没有任何一条能装配 Spring 上下文（首轮已记录，本轮 E-Boot 实证了后果）。
> ⚠️ **本轮全部结论都是「RAG 侧单侧」证据**：Go 侧本轮**没有**起活体探针
> （Go 的 6 个 logic 依赖 Redis/PG/Kafka，且真源已冻结）⇒ Go 侧行为仍以**源码 + 依赖源码消费点**为据，
> 按硬约束 3「只跑 RAG 侧的探针不算数」，**凡 Go 侧只有静态证据的结论一律标 `UNKNOWN` 或注明证据强度**。

## 1. Component Contract

| | 内容 | 出处 |
|---|---|---|
| **Component** | `like-rpc` = like 域的 **gRPC 服务端 + Kafka 消费端 + 热点发件箱中继 + 事实流生产侧** | Go：`like.go` / `internal/mq/mq.go` / `internal/cmd/*` |
| **Purpose** | ① 接收点赞/取消点赞/点踩/取消点踩（Redis Lua 原子改 6 个 key）；② 提供批量点赞数/点赞状态/点赞列表/获赞总数查询（Redis 读 + PG 回源）；③ 消费点赞事件落库（幂等 + 事实流）；④ 把「首次点赞」事件经本地发件箱推给热点排名系统 | Go `internal/logic/*.go`、`internal/mq/internal/mqs/likemq.go` |
| **Public Operations** | `LikeAction` / `GetLikeCount` / `GetLikeState` / `GetUserLikeList` / `GetTargetLikerList` / `GetUserTotalLike`（6 个 unary RPC） | Go `internal/server/likeserviceserver.go:26-53`、`likeservice/likeservice.go:33-40` |
| **Inputs** | proto message（`like.proto` 字段号/枚举逐条一致，见 AGENTS.md §3.4）；无鉴权（身份由调用方 like-api 注入 `user_id`） | Go `pb/like.proto`；RAG `service/like/like-proto` |
| **Outputs** | 业务响应 + **gRPC 业务码**（Go 侧 `grpc-status: 5003` 等越界码；Java 侧走自定义 trailer） | Go `service/like/common/errmsg/errmsg.go:33-35`；RAG `like-common/…/LikeGrpcError.java:36-65` |
| **Dependencies** | Redis（Hash/ZSet/String + Lua EVAL）、PostgreSQL（5 张表）、Kafka（1 生产 topic + 3 推送 topic + 1 消费 topic）、message-rpc（gRPC 通知）、Nacos（服务发现）、gRPC server | Go `internal/svc/servicecontext.go:26-48`；RAG `pom.xml` |
| **State** | Redis：6 类 key（`like_count` / `dislike_count` / `like_state` / `user_like_list` / `target_liker_list` / `user_total_like`）；PG：`like_record` / `like_consume_inbox` / `like_outbox_event` / `like_domain_fact_outbox` / `like_fact_stream` | Go `internal/logic/*.go` 的 `fmt.Sprintf` 拼键；RAG `service/LikeRedisKeys.java` |
| **Side Effects** | 写 Redis / 写 PG / 发 Kafka / 调 message-rpc / 注册 Nacos | 同上 |
| **Failure Model** | **Redis 读失败 ⇒ 降级回源 DB（不致命）**；**Redis 写失败（Lua）⇒ 致命**；**DB 失败 ⇒ 致命**；**Kafka 失败 ⇒ 只记日志**；**通知失败 ⇒ 只记日志** | Go `getlikecountlogic.go:139-142`、`likeactionlogic.go:195-200`、`getusertotallikelogic.go:66-71`、`likeactionlogic.go:395-397` |

### 1.1 ⚠️ 本组件的边界到哪为止（必读）

```
【本组件负责，且已实现】
  like-rpc(gRPC 6 用例) ──写──▶ Redis / PG
  like-rpc(Kafka 消费)   ──写──▶ like_record / like_consume_inbox /
                                  like_domain_fact_outbox / like_fact_stream
  like-rpc(outbox relay) ──推──▶ article-hot-events
  like-rpc(事实生产侧)   ──写──▶ like_domain_fact_outbox（冻结信封 + 版本号）

【不在本组件内（Go 侧是独立进程，Java 侧是 4 行骨架 ⇒ 链路在 Java 侧断开）】
  事实投递   Go cmd/fact-dispatch  →  POST {DC}/v1/events
  权威读     Go cmd/fact-authority →  GET  /internal/v1/community/facts
  真 DC 接收服务  ❗**不在本仓，也不在 Sea-BreakTheWaves 仓**（那里只有客户端）
  下游消费者 Sea-BreakTheWaves（第三仓）消费 usermodel.like-facts.v1
```

⇒ **本组件的验证无法闭环整条事实链路**（首轮已记，本轮复核**仍然成立**）：
Java 侧 `LikeFactDispatchService` / `LikeDeliveryValidator` / `LikeAuthorityLookupService` /
`LikeFactDispatchScheduler` / `LikeAuthorityEndpointConfig` / `LikeFactProcessLogging`
**至今仍是 4 行骨架**（本轮逐个 `cat` 确认，文件名与首轮完全一致）。
⇒ 详见 §9 U9 / U10。

## 2. GO-SEMANTIC-MODEL

### OP-01 `LikeAction`（Go `internal/logic/likeactionlogic.go:157-309`）

| 维度 | Go 行为 | 出处（消费点） |
|---|---|---|
| Input | `TargetId` / `TargetType` 非空、`UserId > 0`；`AuthorId` 可为 0 | `likeactionlogic.go:169-173` |
| Processing 1 | 拼 6 个 Redis key（`like_count:{type}` / `like_state:{type}:{id}` / `user_like_list:{uid}:{type}` / `target_liker_list:{type}:{id}` / `dislike_count:{type}` / `user_total_like:{authorId}`） | `:176-189` |
| Processing 2 | `generateCursorID()` = `(UnixMilli << 22) \| (nodeId=1 << 12) \| seq`，包级 `sync.Mutex` | `:38-54`, `:191` |
| Processing 3 | **一次 Lua EVAL** 原子改 6 个 key，返回 0/1/2 | `:56-122`, `:193-209` |
| Processing 4 | 返回 0 ⇒ 幂等，**不推 Kafka**；返回 1/2 ⇒ 生成雪花 msgId、组 `KafkaLikeMsg`、`Push` 到 `like_promotion_topic` | `:210-253` |
| External | `BizRedis.EvalCtx`（**失败 ⇒ 致命**，业务码 5012「Redis更新失败」） | `:195-200` |
| External | `KafkaPusher.Push`（**失败只记日志**，不影响返回值）；`Push` 内部即 `PushWithKey(UnixNano)`，分区器 `LeastBytes` | `:240-250`；`go-queue@v1.2.2/kq/pusher.go:123-125`, `:44-48` |
| Result | **无条件** HGET 两次回读点赞数/点踩数；`Success: true` 恒为真 | `:254-282`, `:303-308` |
| Side Effect | `resInt != 0 && action == LIKE && targetType == "article"`（**大小写不敏感**）⇒ ① `max(Redis, DB)` 修正点赞数 ② 推任务进度（用户路 + 文章路）③ `author_id > 0 && user_id != author_id` ⇒ 给作者发通知 | `:284-301` |
| Error | 入参非法 ⇒ 业务码 2001「参数异常」；Lua 失败 ⇒ 5012 | `:169-173`, `:195-200` |
| Concurrency | 游标生成靠包级互斥锁；Lua 保证 6 key 原子 | `:38-54`, `:193` |
| Timeout | 无显式超时（走 zrpc 2000ms 服务端超时，见 §6.7） | `like.go:30,41` |

### OP-02 `GetLikeCount`（Go `internal/logic/getlikecountlogic.go:33-137`）

| 维度 | Go 行为 | 出处 |
|---|---|---|
| Input | `TargetIds` 非空，否则 2001「输入需要查询的ID为空」 | `:42-45` |
| Processing | **两次独立 `HmgetCtx`**（like / dislike）；**出错不 return**，继续往下走 | `:50-61` |
| Branch | `likeStr == ""` ⇒ 进 `missingIds`（**只看 like**）；`dislikeStr == ""` ⇒ 当作 `"0"` | `:63-85` |
| External | 全部 miss ⇒ `GetBatchLikeCount`（`state IN (1,2)` 分组）；**DB 失败 ⇒ 致命**（5003「DB查询失败」） | `:99-107`；`internal/model/like.go:69-92` |
| Side Effect | 回源后 **HSET 回填**两个 hash（写失败只记日志） | `:124-131` |
| Result | `Counts[id] = {like_count, dislike_count}`；查不到 ⇒ 0/0 | `:109-121` |

### OP-03 `GetLikeState`（Go `internal/logic/getlikestatelogic.go:33-104`）

| 维度 | Go 行为 | 出处 |
|---|---|---|
| Input | `TargetIds` 非空，否则 2001 | `:43-46` |
| Processing | **逐 id 单条 `HgetCtx`**（不是批量）；出错不 return ⇒ 当 miss | `:48-64` |
| External | miss ⇒ `GetUserBatchLikeState`（**无 state 过滤**）；DB 失败 ⇒ 致命 5003 | `:78-86`；`internal/model/like.go:94-112` |
| Side Effect | **防穿透**：查不到的 id 取 map 零值 0 **并写回 Redis** | `:87-99` |
| Result | `States[id]`；查不到 ⇒ 0 | `:91-92` |

### OP-04 `GetUserTotalLike`（Go `internal/logic/getusertotallikelogic.go:33-85`）

| 维度 | Go 行为 | 出处 |
|---|---|---|
| Input | `UserId > 0`，否则 2001「非法的用户ID」 | `:39-42` |
| Processing | `GetCtx(user_total_like:{uid})`；**出错 ⇒ 落到 miss 分支**（`if err != nil {} else if val != ""` 的形状） | `:45-65` |
| Side Effect | 命中 ⇒ `Expire` **续期**（`CacheTTL`，yaml 3600s）；miss ⇒ DB 计数后 `Setex` | `:56-57`, `:73-74` |
| External | `GetTotalLikeCount` = `WHERE author_id=? AND state=1`；失败 ⇒ 致命 5003 | `:66-71`；`internal/model/like.go:45-49` |

### OP-05 `GetUserLikeList`（Go `internal/logic/getuserlikelistlogic.go:33-133`）

| 维度 | Go 行为 | 出处 |
|---|---|---|
| Input | `page_size <= 0 \|\| > 100` ⇒ **归一为 10**；`fetchLimit = limit + 1` | `:41-45` |
| Cursor | `cursor > 0` ⇒ `maxScore = cursor - 1`，否则 `math.MaxInt64`；**同一个 cursor 同时当 ZSet maxScore 与 DB 的 `id < cursor` 上界**（两套刻度不一致，Go 现状） | `:48-52`；`internal/model/like.go:124-127` |
| Processing | `ZrevrangebyscoreWithScoresAndLimit(0, maxScore, 0, fetchLimit)`；出错不 return | `:53-58` |
| Branch | `len(res) > limit` ⇒ `isEnd=false` 并截断 | `:62-78` |
| Result | `Timestamp = score >> 22`（**毫秒**）；`nextCursor = score`；`isEnd` 时 `nextCursor = 0` | `:70-77`, `:124-126` |
| External | 全部 miss ⇒ `GetUserLikeList`（`state=1`，`ORDER BY id DESC`）；**DB 失败 ⇒ 致命 5003** | `:83-95`；`internal/model/like.go:121-133` |
| Side Effect | 回源时 **ZADD**（score = `timestamp << 22`，`timestamp` 来自 GORM 扫的 `created_at`）＋ `Expire(redisKey, 3600*24*7)` = 7 天 | `:96-121` |

### OP-06 `GetTargetLikerList`（Go `internal/logic/gettargetlikerlistlogic.go:34-140`）

与 OP-05 同构，差异：member 是 `user_id`（`ParseInt` 失败取 0）；`Expire(redisKey, 86400*7)`（数值同 7 天）；
返回 `LikeItem`（无 `target_type` 字段）。出处 `:44-48`, `:73-83`, `:105-129`。

### OP-07 Kafka 消费（Go `internal/mq/internal/mqs/likemq.go:83-135`）

| 维度 | Go 行为 | 出处 |
|---|---|---|
| Consumer | `kq.MustNewQueue`，group `like-update-group`，topic `like_promotion_topic`，`Offset: first`，**`Consumers: 8`（8 个消费协程）** | `internal/mq/mq.go:69`；`internal/mq/etc/mq.yaml`；`go-queue@v1.2.2/kq/config.go:16,18`、`kq/queue.go:114-115`, `:249` |
| Branch 1 | JSON 解析失败 ⇒ **返回 nil** ⇒ **提交位点（丢弃）**，指标 `receive/json_error` | `:86-90` |
| Branch 2 | 字段校验失败（`msg_id` 解析不了 / ≤0 / user_id ≤0 / target 空 / state ∉ 1..4）⇒ **返回 nil（丢弃）**，指标 `receive/empty_msg_id` | `:92-97` |
| Branch 3 | outbox 构造失败（雪花 ID）⇒ **返回 err** ⇒ **不提交（重投）**，指标 `receive/outbox_error` | `:116-121` |
| Branch 4 | DB 事务失败 ⇒ **返回 err** ⇒ **不提交（重投）**，指标 `receive/tx_error` | `:127-131` |
| Success | 提交位点，指标 `receive/committed` | `:132-133` |
| Transaction | **inbox + like_record + domain_fact + outbox 同一个事务** | `internal/model/like.go:148-186` |
| 幂等三层 | ① inbox 主键冲突 ⇒ 已有 ② `payload_hash` 不同 ⇒ **报错重投**（不跳过）③ `inbox.status == 1` ⇒ 跳过 | `internal/model/like.go:164-179` |
| 状态机 | action 1⇒1；2⇒仅 old==1 才 0；3⇒2；4⇒仅 old==2 才 0；`newState != oldState` 才发事实 | `internal/model/like.go:209-239` |
| 事实 | `appendLikeFact`：遗留未版本化事实 ⇒ **报错**；`INSERT..ON CONFLICT DO UPDATE SET version=version+1 RETURNING` 原子取号；版本须 ∈ [1, 2^53-1] | `internal/model/domain_fact.go:100-119` |

### OP-08 outbox relay（Go `internal/mq/internal/mqs/like_outbox_sender.go:20-38` + `internal/mq/mq.go:29-44`）

| 维度 | Go 行为 | 出处 |
|---|---|---|
| 触发 | **每秒 ticker**，`SendPending(ctx, 100)` | `mq.go:31-43`, `:39` |
| 捞取 | `status IN (0,2) AND retry_count < 3`，`ORDER BY created_at asc`，`LIMIT n` | `internal/model/like_outbox.go:28-36` |
| 逐条 | 推送失败 ⇒ `MarkFailed`（`status=2, retry_count+1`）**并 continue**；成功 ⇒ `MarkSent` | `like_outbox_sender.go:27-35`；`like_outbox.go:38-53` |
| 载荷 | **payload 原文**，不经重新序列化 | `like_outbox_sender.go:28` |

### OP-09 事实投递 / 权威读（Go `internal/model/fact_delivery.go` / `fact_authority.go` / `cmd/*`）

见 §1.1 边界说明。投递：`FOR UPDATE SKIP LOCKED` 认领 → 前驱必须全部 ACCEPTED → 校验冻结信封 → POST（8s 超时）→ 结算；
校验不过 ⇒ **BLOCKED 终止重试**；发送/回执不符 ⇒ **FAILED 可重试**。
权威读：`GET /internal/v1/community/facts`，token ≥32 字节，`producer` 仅 `rtw.like-mq`，全量重放校验。

## 3. SPRINGBOOT-SEMANTIC-MODEL

### OP-01 `likeAction`（RAG `service/LikeDomainService.java:598-693`）

| 维度 | Java 行为 | 出处 |
|---|---|---|
| Input | 同 Go（`targetId`/`targetType` 空、`userId <= 0` ⇒ 2001「参数异常」） | `:602-607` |
| Processing 1 | 6 个 key 由 `LikeRedisKeys` 构造（字面量与 Go 逐条相同） | `:609-614`；`service/LikeRedisKeys.java:31-82` |
| Processing 2 | `LikeCursorIdGenerator.nextId()` = `(System.currentTimeMillis() << 22) \| (1L << 12) \| seq`，`synchronized` 静态方法 | `:616`；`service/LikeCursorIdGenerator.java:102-120` |
| Processing 3 | `redis.execute(LikeActionScript.INSTANCE, keys, args)` —— **Lua 原文逐字保留**（含 `INCR` 无正数守卫 / `DECR` 有守卫的不对称） | `:620-625`；`service/LikeActionScript.java:68-134` |
| Processing 4 | 返回 0 ⇒ 幂等不推；1/2 ⇒ 雪花 msgId（失败只记 `snowflake_error` 且 msgId="0"）⇒ `likeMsgPublisher.publish` | `:630-657` |
| External | Lua 抛异常 ⇒ **致命** 5012「Redis更新失败」 | `:626-629` |
| External | `kafkaTemplate.send(topic, null, payload)` —— **key 为 null**；**异步**，失败只记日志 | `service/LikeDomainService.java:1013-1021` |
| Result | **无条件** `hgetCountLenient` 两次；`Success: true` 恒真 | `:660-661`, `:686-692` |
| Side Effect | `resInt != 0 && ACTION_LIKE && equalsIgnoreCase("article")` ⇒ ① `max(Redis, DB)` ② 任务进度 ③ 通知 | `:663-684` |
| Error | 兜底 `LikeExceptions.Rpc` ⇒ gRPC 业务码；其它 RuntimeException ⇒ 5001 | `service/impl/LikeServiceGrpcImpl.java:99-123` |

### OP-02 ~ OP-06 读路径

结构与 Go 一一对应（`LikeDomainService.java:143-215` / `:249-298` / `:327-358` / `:387-478` / `:490-574`），
**关键适配层**在 `:765-895`（`hmgetLenient` / `hgetLenient` / `hsetLenient` / `getLenient` / `setexLenient` /
`expireLenient` / `zrevrangeLenient` / `zaddLenient` / `hgetCountLenient`）：
**Redis 异常 ⇒ 返回空结果 ⇒ 上层自然走 DB 回源**（Go 的 `Hmget` 失败返回 `(nil, err)` 的等价物）。
⚠️ 注意 `hgetCountLenient` 在**异常时**打 `redis_read_error` 指标（`:881`），
Go 在**「字段不存在」**时也打同一指标（`likeactionlogic.go:265`）⇒ **两处语义不同**（见 DRIFT-LIKE-009，首轮已记）。

### OP-07 Kafka 消费（RAG `mq/LikeUpdateKafkaListener.java:76-130`）

| 维度 | Java 行为 | 出处 |
|---|---|---|
| Consumer | `@KafkaListener(id="like-update-consumer", topics="#{@likeKafkaTopics.likeTopic}", groupId="${spring.kafka.consumer.group-id}", containerFactory="kafkaListenerContainerFactory")`；group `like-update-group` ✓；`auto-offset-reset: earliest` ✓；**并发度 = 1**（容器工厂未设 `setConcurrency`，yaml 也没有 `spring.kafka.listener.concurrency`） | `:76-79`；`application.yml:88-112`；`LikeRpcApplication.java:112-120` |
| Branch 1 | JSON 解析失败 ⇒ **正常 return** ⇒ 提交位点（丢弃）；`@JsonIgnoreProperties(ignoreUnknown=false)` 让未知字段也走这条 | `:82-89`；`mq/LikeEventContracts.java:40` |
| Branch 2 | 字段校验失败 ⇒ **正常 return**（丢弃） | `:92-97`, `:144-159` |
| Branch 3 | outbox 构造失败 ⇒ **抛异常** ⇒ 不提交（重投） | `:110-116` |
| Branch 4 | DB 事务失败 ⇒ **抛异常** ⇒ 不提交（重投） | `:121-127` |
| Success | 提交位点（`ack-mode: record` + `enable-auto-commit: false`） | `application.yml:99,112` |
| Transaction | `@Transactional(rollbackFor = Exception.class)` on `LikeMessageIngestService#processBatch`，**由 listener 跨 Bean 注入调用**（非自调用 ⇒ 代理生效） | `service/LikeMessageIngestService.java:124-129`；`mq/LikeUpdateKafkaListener.java:122` |
| 幂等三层 / 状态机 | 与 Go 逐条对应 | `service/LikeMessageIngestService.java:132-247` |

### OP-08 outbox relay（RAG `mq/LikeHotEventOutboxRelay.java:61-94`）

`@Scheduled(fixedRateString="${like.outbox-relay-interval:1000}")`；捞取 `status IN (0,2) AND retry_count < 3`
`ORDER BY created_at ASC LIMIT 100` ✓；逐条 `kafkaTemplate.send(topic, null, payload)` ⇒ **key 为 null**；
失败 ⇒ `markFailed` 并 continue ✓；**问题**：`send` 是异步的，成功即 `markSent`（Go 的 `Push` 同样是入队即返回，**形状一致**）。

### OP-09 事实生产侧（RAG `service/LikeDomainFactService.java:68-159`）

生产侧**已完整实现**（遗留事实检查 ⇒ 报错；原子取号 ⇒ `LikeFactStreamMapper#bumpFactStreamVersion`；
payload 与冻结信封**用同一份原文**；`aggregate_version` / `target_revision` 恒 `null`）⇒ 与 Go
`internal/model/domain_fact.go:76-138` 一一对应。
**投递侧与权威读侧仍是 4 行骨架**（`LikeFactDispatchService` / `LikeDeliveryValidator` /
`LikeAuthorityLookupService` / `LikeFactDispatchScheduler` / `LikeAuthorityEndpointConfig` /
`LikeFactProcessLogging`）⇒ **DRIFT-LIKE-003 未关闭**。

## 4. Equivalence Matrix

> Result 取值只用：`EQUIVALENT` / `IMPLEMENTATION DIFFERENCE` / `OBSERVABLE DIFFERENCE` / `SEMANTIC MISMATCH` / `UNKNOWN`

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| **组件级** | **进程可启动性** | `MustNewServer` 正常起 | **起不来**（4 个独立硬阻塞） | **SEMANTIC MISMATCH** | E-Boot |
| LikeAction | 入参校验 | `:169-173` | `:602-607` | EQUIVALENT | E-Src |
| LikeAction | Redis key 字面量 | `:176-189` | `LikeRedisKeys.java:31-82` | EQUIVALENT | E-Src |
| LikeAction | 游标生成（毫秒<<22\|node<<12\|seq） | `:38-54` | `LikeCursorIdGenerator.java:102-120` | EQUIVALENT | E-Src |
| LikeAction | Lua 脚本与 6 key 原子性 | `:56-122` | `LikeActionScript.java:68-134` | EQUIVALENT | E-Src |
| LikeAction | Lua 返回 0/1/2 分支 | `:210-253` | `:630-657` | EQUIVALENT | E-Src |
| LikeAction | Redis 写失败 ⇒ 致命 5012 | `:195-200` | `:626-629` | EQUIVALENT | E-Src |
| LikeAction | Redis 读失败 ⇒ 计数 0 + `redis_read_error` | `:263-267` | `:880-884` | **OBSERVABLE DIFFERENCE** | 首轮 DRIFT-LIKE-009 |
| LikeAction | Kafka **key** | `kq/pusher.go:123-125`（UnixNano） | `:1015`（null） | **OBSERVABLE DIFFERENCE** | 首轮 DRIFT-LIKE-005 |
| LikeAction | Kafka **分区器** | `kq/pusher.go:48`（`LeastBytes`） | 默认（null key ⇒ sticky） | **OBSERVABLE DIFFERENCE** | 首轮 DRIFT-LIKE-005 |
| LikeAction | Kafka 失败 ⇒ 只记日志 | `:240-250` | `:1017-1021` | EQUIVALENT（但异步失败不可见） | 首轮 U6 |
| LikeAction | 幂等时仍回读计数 | `:254-282` | `:660-661` | EQUIVALENT | E-Src |
| LikeAction | `targetType` 大小写不敏感 | `:286` | `:666` | EQUIVALENT | E-Src |
| LikeAction | 文章支路 max(Redis, DB) | `:366-377` | `:700-708` | EQUIVALENT | E-Src |
| LikeAction | 任务进度双路 + 可选 pusher 粒度 | `:311-343`, `svc:50-56` | `LikeTaskProgressPublisher.java:69-110` | **OBSERVABLE DIFFERENCE** | 首轮 DRIFT-LIKE-012 |
| LikeAction | 通知失败只记日志 | `:395-397` | `MessageRpcClient.java:87-91` | EQUIVALENT | E-Src |
| LikeAction | `Success` 恒 true | `:304` | `:688` | EQUIVALENT | E-Src |
| GetLikeCount | 空 id ⇒ 2001 | `:42-45` | `:147-149` | EQUIVALENT | E-Src |
| GetLikeCount | Redis 失败 ⇒ 全量回源 | `:50-61`,`:139-142` | `:156-157`,`:765-781` | EQUIVALENT | E-Src |
| GetLikeCount | miss 判定只看 like | `:72-78` | `:168-175` | EQUIVALENT | E-Src |
| GetLikeCount | DB 失败 ⇒ 致命 5003 | `:103-107` | `:227-229` | EQUIVALENT | E-Src |
| GetLikeCount | 回源后 HSET 回填 | `:124-131` | `:209-210` | EQUIVALENT | E-Src |
| GetLikeCount | 计数取 `state IN (1,2)` 分组 | `model/like.go:78` | `LikeRecordMapper.java:85` | EQUIVALENT | E-Src |
| GetLikeState | 逐 id 单条 HGET | `:49-57` | `:261-270` | EQUIVALENT | E-Src |
| GetLikeState | 防穿透写回 0 | `:87-99` | `:289-294` | EQUIVALENT | E-Src |
| GetLikeState | DB 失败 ⇒ 致命 5003 | `:82-86` | `:283-287` | EQUIVALENT | E-Src |
| GetUserTotalLike | 非法 uid ⇒ 2001 | `:39-42` | `:330-332` | EQUIVALENT | E-Src |
| GetUserTotalLike | Redis 错 ⇒ 当 miss | `:45-65` | `:335`,`:804-813` | EQUIVALENT | E-Src |
| GetUserTotalLike | 命中续期 EXPIRE | `:56-57` | `:341` | EQUIVALENT | E-Src |
| GetUserTotalLike | 回源 SETEX + 计数口径 | `:66-79` | `:348-357` | EQUIVALENT | E-Src |
| GetUserLikeList | page_size 归一 10 / 上限 100 | `:41-45` | `:388`,`:919-921` | EQUIVALENT | E-Src |
| GetUserLikeList | cursor 双刻度混用 | `:48-52`,`model/like.go:125-127` | `:392`,`LikeRecordMapper.java:123` | EQUIVALENT（照搬 Go 的不一致） | E-Src |
| GetUserLikeList | DB 回源时间戳刻度（秒） | `model/like.go:130` | `:442`,`:952-954` | **IMPLEMENTATION DIFFERENCE**（GORM 列名错配已签字，DRIFT-LIKE-001） | 首轮签字 |
| GetUserLikeList | `target_type` 取值来源（Redis 路径用请求值 / DB 路径用库值） | `:74` vs `:112` | `:413` vs `:455` | EQUIVALENT | E-Src |
| GetUserLikeList | 列表 ZSet TTL = 7 天 | `:120` | `:464`,`LikeRpcProperties.java:80` | EQUIVALENT | E-Src |
| GetTargetLikerList | 同 OP-05 各项 | `:44-139` | `:490-574` | EQUIVALENT | E-Src |
| Kafka 消费 | group / 起始位点 | `mq.yaml`, `kq/config.go:16`,`queue.go:114-115` | `application.yml:90,95` | EQUIVALENT | E-Src |
| Kafka 消费 | **并发度** | **8**（`kq/config.go:18`,`queue.go:249`） | **1**（容器工厂未设 concurrency） | **OBSERVABLE DIFFERENCE** | **DRIFT-LIKE-018** |
| Kafka 消费 | JSON 坏 ⇒ 丢弃并提交 | `likemq.go:86-90` | `:82-89` | EQUIVALENT | E-Src |
| Kafka 消费 | 未知字段 | Go `encoding/json` **忽略** | `@JsonIgnoreProperties(ignoreUnknown=false)` ⇒ **抛异常** ⇒ 丢弃并提交 | **SEMANTIC MISMATCH** | 首轮 DRIFT-LIKE-007 |
| Kafka 消费 | 持久层失败 ⇒ 重投 | `likemq.go:127-131` | `:121-127` | EQUIVALENT | E-Src |
| Kafka 消费 | **进程内重试次数** | **0**（kq 无 errorHandler） | **9**（`DefaultErrorHandler` 内置 `FixedBackOff(0,9)`） | **OBSERVABLE DIFFERENCE** | 首轮 DRIFT-LIKE-004 |
| Kafka 消费 | 幂等三层顺序 | `model/like.go:164-179` | `LikeMessageIngestService.java:157-180` | EQUIVALENT | E-Src |
| Kafka 消费 | 状态机转移 | `model/like.go:211-224` | `LikeMessageIngestService.java:220-226` | EQUIVALENT | E-Src |
| Kafka 消费 | 事务边界 | `model/like.go:149` | `LikeMessageIngestService.java:124` | EQUIVALENT（代理生效条件满足） | E-Src |
| Kafka 消费 | `updated_at` 刷新 | GORM map 形式**会**刷新（`gorm@v1.31.1/callbacks/update.go:201,238`） | `LikeRecordMapper.java:201-209` **不刷新** | **OBSERVABLE DIFFERENCE** | 首轮 DRIFT-LIKE-008 |
| Kafka 消费 | outbox `ON CONFLICT` 目标 | Go 无目标（吞任意唯一冲突） | `ON CONFLICT (event_key)` | **OBSERVABLE DIFFERENCE** | 首轮 DRIFT-LIKE-011 |
| outbox relay | 捞取条件 / 逐条 continue / retry<3 搁置 | `like_outbox.go:31-34`, `like_outbox_sender.go:27-35` | `LikeOutboxEventMapper.java:49-54`, `LikeHotEventOutboxRelay.java:78-93` | EQUIVALENT | E-Src |
| 事实生产 | appendFact 全链路 | `domain_fact.go:76-138` | `LikeDomainFactService.java:68-159` | EQUIVALENT | E-Src |
| 事实**投递** | SKIP LOCKED 认领 / BLOCKED / FAILED | `fact_delivery.go:52-126` | **骨架未实现** | **SEMANTIC MISMATCH** | 首轮 DRIFT-LIKE-003 |
| 事实**权威读** | 12 道全链路重放校验 | `fact_authority.go:53-119` | **骨架未实现** | **SEMANTIC MISMATCH** | 首轮 DRIFT-LIKE-003 |
| 错误 | 业务码载体 | `grpc-status: 5003`（越界码） | 自定义 trailer `rag-like-biz-code`；`grpc-status` 落 `UNKNOWN(2)` / 输入错落 `INVALID_ARGUMENT(3)` | **SEMANTIC MISMATCH**（平台强制） | 首轮 DRIFT-LIKE-002 + §7.6 修正 |
| 配置 | Flyway 账本表名 | 无（Go 用 AutoMigrate） | `flyway_schema_history_like` | UNKNOWN（跨组件不变量） | E-PG（单体已证） |
| DDL | `like_domain_fact_outbox` 列类型与约束 | GORM `integer` + Go 手写迁移 `smallint/integer` + CHECK + 触发器 + 部分索引 | `BIGINT` + **无** CHECK/触发器/部分索引 | **OBSERVABLE DIFFERENCE** | **DRIFT-LIKE-017** |

## 5. External Interaction & Error Flow

### 5.1 Redis

| | Go | Java |
|---|---|---|
| 操作 | `EvalCtx` / `HgetCtx` / `HmgetCtx` / `HsetCtx` / `GetCtx` / `SetexCtx` / `ExpireCtx` / `ZaddCtx` / `ZcardCtx` / `ZrevrangebyscoreWithScoresAndLimitCtx` | `RedisTemplate.execute` / `opsForHash().get/multiGet/put` / `opsForValue().get/set` / `expire` / `opsForZSet().add/zCard/reverseRangeByScoreWithScores` |
| 读失败语义 | 返回 `(nil/"" , err)`，**调用方不 return** ⇒ 全部当 miss | 抛异常 ⇒ 被 `*Lenient` 捕获 ⇒ 返回空 ⇒ 全部当 miss |
| 写失败语义 | Lua 失败 ⇒ **致命**；HSET/EXPIRE 失败 ⇒ 只记日志 | Lua 失败 ⇒ **致命**；HSET/EXPIRE 失败 ⇒ 只记日志 |
| **读失败 vs 字段不存在** | **两者都记 `redis_read_error`**（`likeactionlogic.go:265` vs `:263-267`） | **字段不存在不记指标**，只有异常才记（`LikeDomainService.java:880-884`） |
| 序列化 | go-redis v9，Lua 参数全部 `fmt.Sprintf("%d")` 转字符串 | Lettuce，Lua 参数全部 `String.valueOf` |

⇒ **结论：Redis 的错误语义整体 EQUIVALENT**，唯一差异是**指标口径**（首轮 DRIFT-LIKE-009）。

### 5.2 PostgreSQL

| | Go | Java |
|---|---|---|
| 建表 | `db.AutoMigrate(...)` 5 张表（`internal/model/db.go:15`） | Flyway `V1..V5`（**已实测建成**，E-PG） |
| 事务 | `db.Transaction(func(tx))` 显式 | `@Transactional(rollbackFor = Exception.class)` |
| 幂等插入 | `clause.OnConflict{DoNothing: true}`（**无目标**） | inbox：`ON CONFLICT (msg_id)`；seed：**无目标** ✓；outbox：`ON CONFLICT (event_key)`（**有目标**） |
| 行锁 | `clause.Locking{Strength: "UPDATE"}` | `FOR UPDATE` |
| 软删 | `Unscoped()` 唯一一次例外 | `selectForUpdateIncludingDeleted` 唯一不带 `delete_at IS NULL` 的查询 |
| 版本取号 | 裸 SQL `INSERT..ON CONFLICT DO UPDATE..RETURNING` | `@Select` 同一条 SQL（`LikeFactStreamMapper.java:45-50`） |

### 5.3 Kafka

| | Go | Java |
|---|---|---|
| 生产 | `kq.Pusher`（**异步缓冲**，`ChunkExecutor`；`Push` 带 UnixNano key；`LeastBytes` 分区） | `KafkaTemplate.send`（异步；**key=null**；默认分区器） |
| 消费 | `kq.Queue`，8 协程，`Offset: first` | `@KafkaListener`，**并发 1**，`auto-offset-reset: earliest` |
| 位点 | `Consume` 返回 err ⇒ 不确认 | 抛异常 ⇒ 不提交（`ack-mode: record`） |
| 重试 | 0 次进程内重试 | **9 次**（`DefaultErrorHandler` 内置 `FixedBackOff(0,9)`） |

### 5.4 gRPC / 业务码

| 场景 | Go | Java | 等价？ |
|---|---|---|---|
| 入参非法 | `grpc-status: 2001`，desc「参数异常」 | `grpc-status: 3`（INVALID_ARGUMENT），desc「参数异常」，业务码 2001 在 trailer | **NO**（码位置变 + 数值变） |
| DB 失败 | `grpc-status: 5003`，desc「DB查询失败」 | `grpc-status: 2`（UNKNOWN），desc「参数异常」→ 实为「DB查询失败」，业务码 5003 在 trailer | **NO** |
| Redis 写失败 | `grpc-status: 5012` | `grpc-status: 2`，业务码 5012 在 trailer | **NO** |

⇒ **DRIFT-LIKE-002 仍是 SEMANTIC MISMATCH**；本轮补充：**漂移是「按业务码逐条不同」的，不是统一落 UNKNOWN**（见 §7.6）。

## 6. 逐维度结论

### 6.1 State Semantics（§12）
Go 状态机 = `state ∈ {0,1,2}` × `action ∈ {1,2,3,4}`；Java 逐条对应（`LikeMessageIngestService.java:220-226`）。
**`state==2 && last_operation_id==0` 的「遗留状态拒绝」分支两侧都有**（`model/like.go:203-205` / `LikeMessageIngestService.java:206-210`）✓
⇒ **EQUIVALENT**。

### 6.2 Cache Semantic Equivalence（§13）

| 项 | Go | Java | Result |
|---|---|---|---|
| Cache Key | 6 类 key 字面量 | 同 | EQUIVALENT |
| TTL | `like_count`/`dislike_count`/`like_state` **无 TTL**；两个列表 ZSet **7 天**（仅回源后设置）；`user_total_like` **CacheTTL 3600s** | 同 | EQUIVALENT |
| Read Order | 读 → miss ⇒ DB ⇒ 回填 | 同 | EQUIVALENT |
| Cache Hit/Miss | 判据见 §4 | 同 | EQUIVALENT |
| Negative Cache | `GetLikeState` 把「查不到」缓存成 0 | 同（`:291`） | EQUIVALENT |
| Cache Failure → DB Fallback | 有（Redis 挂 ⇒ 全量回源） | 有（`*Lenient` 适配层） | EQUIVALENT |
| Serialization | go-redis 字符串 | Lettuce 字符串 + Jackson 2（**但 Bean 缺失，见 DRIFT-LIKE-016**） | **SEMANTIC MISMATCH（运行期）** |

### 6.3 Authentication / Authorization（§14）
**两侧都没有鉴权层**：身份完全由调用方（like-api）注入 `user_id` / `author_id`。
Go `likeactionlogic.go:169-173` 只做非空/正数校验；Java `:602-607` 相同 ⇒ **EQUIVALENT**（含「都没有」这一事实）。

### 6.4 Transaction Semantic Equivalence（§15）

| | Go | Java |
|---|---|---|
| 边界 | `ProcessLikeMessageBatch` 的 `db.Transaction`（`model/like.go:149`） | `LikeMessageIngestService#processBatch` 的 `@Transactional`（`:124`） |
| 内的操作 | inbox insert + like_record upsert + fact append + outbox insert + inbox status=1 | 同 5 步，顺序一致（`:157-247`） |
| 代理生效 | 显式 tx，天然生效 | **跨 Bean 注入调用**（listener → ingestService），**非 self-invocation** ⇒ 代理生效 ✓ |
| 回滚 | 任一步 error ⇒ 整个 tx 回滚 ⇒ 返回 err ⇒ 不提交位点 | `@Transactional(rollbackFor = Exception.class)` ⇒ RuntimeException 回滚 ⇒ listener 抛异常 ⇒ 不提交位点 ✓ |

⇒ **EQUIVALENT**（静态 + 结构证据；**运行期未验证**，因为上下文起不来 ⇒ 见 U11）。

### 6.5 Concurrency Semantic Equivalence（§16）
- 游标生成：Go 包级 `sync.Mutex` / Java `synchronized` 静态方法 ⇒ 互斥等价 ✓
- 状态机并发：两侧都靠 `SELECT ... FOR UPDATE` 行锁串行化 ✓
- **Kafka 消费并发度 8 → 1** ⇒ **OBSERVABLE DIFFERENCE（DRIFT-LIKE-018）**

### 6.6 Timeout Semantic Equivalence（§17）
| | Go | Java |
|---|---|---|
| 时长 | `zrpc.RpcServerConf.Timeout` 默认 **2000ms**（`go-zero@v1.10.2/zrpc/config.go:31` 的 `json:",default=2000"`）；`observability.DisableNativeRpcTimeout` 读出后把原生超时置 0（`service/common/observability/rpc.go:19-23`） | `observability.rpc-timeout: 2s`（`application.yml:237-238`） |
| Scope | **整个 handler**（拦截器 `context.WithTimeout` + 独立 goroutine，超时返回 `codes.DeadlineExceeded`） | 由 `common` 模块的 `RpcServerInterceptorFactory` 实现（**本组件外**） |
| 装配 | `s.AddUnaryInterceptors(...)`（`like.go:41`） | `@Bean GrpcServerConfigurer` → `server.intercept(...)`（`LikeRpcApplication.java:90-93`） |
| 结论 | 时长一致 ✓；**运行期是否真生效 UNKNOWN**（上下文起不来） | ⇒ **UNKNOWN**（U11） |

### 6.7 Retry Semantic Equivalence（§18）
| | Go | Java |
|---|---|---|
| Kafka 消费重试 | **0 次**进程内重试（kq 未注册 errorHandler） | **9 次**（`DefaultErrorHandler` 内置 `FixedBackOff(0,9)`，`LikeUpdateKafkaListener.java:170-175`） |
| outbox relay | 每秒重扫，`retry_count < 3` ⇒ 最多 3 次后永久搁置 | 同 ✓ |
| 事实投递 | FAILED 可重试 / BLOCKED 终止 | 骨架未实现 |
⇒ **OBSERVABLE DIFFERENCE**（首轮 DRIFT-LIKE-004，本轮复核**仍然成立**：那段 javadoc 仍在说「不做进程内重试」）。

### 6.8 Serialization Semantic Equivalence（§19）
| 契约 | Go | Java | Result |
|---|---|---|---|
| `KafkaLikeMsg` 字段名/顺序 | `types.go:3-12`（`msg_id`…`created_at`） | `@JsonPropertyOrder` 同序 ✓ | EQUIVALENT |
| HTML 转义（影响 `payload_hash`） | Go **开** | `GoCompatibleJson.write` 逐字符套 Go 转义表（`GoCompatibleJson.java:56-71`） | EQUIVALENT |
| U+2028/2029 | Go 转义 | 同 ✓ | EQUIVALENT |
| `payload_hash` 字节一致 | `like_message_hash.go:9-27` | `LikeMessageDigest.java:65-79` | EQUIVALENT（**未双侧对拍**，⇒ UNKNOWN，见 U12） |
| 未知字段 | Go **忽略** | `@JsonIgnoreProperties(ignoreUnknown=false)` ⇒ **抛异常** | **SEMANTIC MISMATCH**（首轮 DRIFT-LIKE-007） |
| `null` vs 缺失 | Go `encoding/json` 输出 `null`；Jackson `ObjectNode.putNull` 同 | 事实 payload 显式 `putNull("aggregate_version")` / `putNull("target_revision")` ✓ | EQUIVALENT |
| RFC3339Nano | `time.Format(RFC3339Nano)`（去尾零） | `GoCompatibleJson.rfc3339Nano`（手写去尾零） | EQUIVALENT（**未对拍** ⇒ UNKNOWN） |

### 6.9 Configuration Semantic Equivalence（§20）
| Go 字段 | Go 默认（消费点） | Java | Result |
|---|---|---|---|
| `Storage.Redis.CacheTTL` | 3600（`config.go:25`，yaml 显式 3600） | `like.redis.cache-ttl: 3600s` | EQUIVALENT |
| `Storage.Redis.HotspotTTL` | 1800（`config.go:26`）—— **Go 侧无任何读取点**（本轮 grep 确认） | `hotspot-ttl: 1800s`，Java 侧同样不读 | EQUIVALENT（都是死配置） |
| `Storage.Redis.BatchSize` | 1000（`config.go:27`）—— **无读取点** | 刻意不映射 ✓ | EQUIVALENT |
| `Storage.RocksDB.*` | — —— **无读取点** | 不映射 ✓ | EQUIVALENT |
| `HotSpot.*` / `AntiBrush.*` | — —— **无读取点** | 不映射 ✓ | EQUIVALENT |
| `KafkaConf.Topic` | `like_promotion_topic`（`config.go:38`） | `like.kafka.like-topic` ✓ | EQUIVALENT |
| `KafkaPusher`（死配置） | `like_update_topic` —— **`servicecontext.go:37` 建的是 `c.KafkaConf` 的 pusher，这份从未被读** | 不实现 ✓ | EQUIVALENT |
| `TaskUserPusherConf` / `TaskArticlePusherConf` | 两个独立可选 pusher（`svc:50-56`） | 合并为单个 `task-pushers-enabled` | **OBSERVABLE DIFFERENCE**（首轮 DRIFT-LIKE-012） |
| `Kafka.Group/Offset/Consumers` | `like-update-group` / `first` / **8** | `like-update-group` / `earliest` / **1** | **OBSERVABLE DIFFERENCE**（DRIFT-LIKE-018） |
| `MessageRpc.Etcd.Key` | `message.rpc` | `grpc.client.message-rpc.address` | **SEMANTIC MISMATCH**（DRIFT-LIKE-013：格式错，启不来） |
| RPC 超时 | 2000ms（`zrpc/config.go:31`） | `observability.rpc-timeout: 2s` | EQUIVALENT（运行期 UNKNOWN） |
| Flyway 账本 | 无 | `flyway_schema_history_like` | UNKNOWN（跨组件不变量，U13） |

### 6.10 Lifecycle Semantic Equivalence（§21）
| | Go | Java |
|---|---|---|
| 进程数 | **4 个 main**（gRPC / mq / fact-dispatch / fact-authority） | **1 个 JVM**（合并） |
| 启动顺序 | `InitDB` → `NewServiceContext` → `MustNewServer` → `s.Start()` | Spring 上下文 refresh（**当前失败**） |
| 关停 | `defer s.Stop()` / `serviceGroup.Stop()` | Spring `ContextClosedEvent` + `@PreDestroy`（`NacosRegistration.java:100-110`） |
| 事实投递进程 | 独立进程（首轮已论证：不得内嵌成 `@Scheduled`） | **未实现**（骨架） |
| 结论 | — | **SEMANTIC MISMATCH**（进程拓扑 + 启动失败） |

### 6.11 Observable Behavior（§22）
返回码 / 错误 / DB 状态 / cache 状态 / MQ 消息 / RPC 调用 / 重试次数 / 状态转移 —— 逐条见 §4 矩阵。
**唯一「完全不可观察」的一类是 Go 有、Java 没有的：整个服务（Java 侧 0 个请求能被服务）**。

## 7. Semantic Drifts

> 历史条目 **DRIFT-LIKE-001 ~ 012** 全部保留在文件首节（原样未改）。本节只列 **2026-10-23 新增**。

### DRIFT-LIKE-013 — gRPC 客户端寻址 URI 格式错误 ⇒ 上下文启动失败
> 🔴 **状态：仍未修**（第四轮只修了 014/015/016 三条启动阻塞，013 **是唯一剩余的启动阻塞**）—— ⚠️ 生产配置 `application.yml:139` 仍是 `discovery://message-rpc`（**两斜杠**）；而测试用 `static://` 绕过 ⇒ **这个后果没有任何测试覆盖**。

| 项 | 内容 |
|---|---|
| **Component** | like-rpc（`MessageRpcClient` / `LikeRpcApplication` 装配） |
| **Operation** | 组件级（启动期） |
| **Category** | Service Discovery / 装配配置 |
| **Go Behavior** | `zrpc.MustNewClient(c.MessageRpc)`（`internal/svc/servicecontext.go:46`），`MessageRpc.Etcd.Key: message.rpc`（`etc/like.yaml`）⇒ go-zero 用 etcd key 寻址，**无 URI 字符串** |
| **Java Behavior** | `application.yml:132` 写 `grpc.client.message-rpc.address: discovery://message-rpc`（**两斜杠**）⇒ devh starter 3.1.0 的 `DiscoveryClientResolverFactory.newNameResolver`（`DiscoveryClientResolverFactory.java:82`）解析失败 |
| **Difference** | Java 侧寻址串**格式非法**，与本仓其它模块的约定（**三斜杠** `discovery:///<name>`）不一致 |
| **Observable Impact** | **Spring 上下文直接启动失败**：`java.lang.IllegalArgumentException: Incorrectly formatted target uri; expected: 'discovery:[//]/<service-name>'; but was 'discovery://message-rpc'`（E-Boot 第一次启动即命中）⇒ like-rpc **0 个请求可被服务** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | 🔴 CRITICAL |
| **Must Preserve** | 服务发现名必须是 `message-rpc`，且 URI 必须是 devh 认得的 `discovery:///<name>` 形式 |
| **Evidence** | E-Boot（`/tmp/like-boot-nokafka.log:59,67`）；同仓正确写法：`service/users/admin/admin-api/src/main/resources/application.yml:56,65,71`、`service/like/like-api/src/main/resources/application.yml:104`、`service/hot/hot-api/…:53,59`、`service/message/message-api/…:52`（均三斜杠）；`MessageRpcClient.java:52` 的 `@GrpcClient("message-rpc")` |

### DRIFT-LIKE-014 — MyBatis-Plus（Boot 3 starter）在 Boot 4 下自动配置被静默跳过 ⇒ 无 `SqlSessionFactory` ⇒ 启动失败
> ✅ **状态：已修（2026-10-23 第三轮 / 2026-10-24 修复轮）** —— 修法：新增 `config/MybatisPlusOrderingBridgeAutoConfiguration.java`（排序桥接）+ `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册它 ⇒ 绕过 Boot 3/4 的 `@AutoConfigureAfter` 旧包名悬空。**下表是修复前的描述。**

| 项 | 内容 |
|---|---|
| **Component** | like-rpc（5 个 Mapper + 全部 DB 读写） |
| **Operation** | 组件级（启动期） |
| **Category** | Framework Module Migration / 自动配置静默失效 |
| **Go Behavior** | GORM 直连，`AutoMigrate` 建表（`internal/model/db.go:15`），无「自动配置」这一层 |
| **Java Behavior** | pom 引 `mybatis-plus-spring-boot3-starter:3.5.17`（Boot 3 专用 starter）。该 starter 的 `MybatisPlusAutoConfiguration` 上有 `@AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)`，而 **Boot 4.0.8 已把该类搬到 `org.springframework.boot.jdbc.autoconfigure`（`spring-boot-jdbc` 模块）**，旧 FQN 在全仓 Boot 4.0.8 jar 中**已不存在** ⇒ 排序保证丢失 ⇒ `@ConditionalOnSingleCandidate(DataSource.class)` 在 DataSource Bean 注册**之前**求值 ⇒ **不匹配** ⇒ 自动配置被**静默跳过** |
| **Difference** | Java 侧**根本没有 `SqlSessionFactory` Bean**；`@MapperScan` 出来的 Mapper 全部创建失败 |
| **Observable Impact** | **Spring 上下文启动失败**：`Error creating bean with name 'likeOutboxEventMapper' … Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`（`mybatis-spring-4.0.0` 的 `SqlSessionDaoSupport.checkDaoConfig:121`）⇒ like-rpc **0 个请求可被服务** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | 🔴 CRITICAL |
| **Must Preserve** | 5 个 Mapper 必须能拿到真实 `SqlSessionFactory`；且启动期必须**报错**而不是静默跳过 |
| **Evidence** | E-Boot：`--debug` 的 ConditionEvaluationReport 原文 `MybatisPlusAutoConfiguration: Did not match: - @ConditionalOnSingleCandidate (types: javax.sql.DataSource; SearchStrategy: all) did not find any beans (OnBeanCondition)`；`mybatis-plus-spring-boot3-starter-3.5.17.jar` 的常量池含 `org/springframework/boot/autoconfigure/jdbc/DataSourceAutoConfiguration`；`spring-boot-autoconfigure-4.0.8.jar` 内该类**不存在**，`spring-boot-jdbc-4.0.8.jar` 内为 `org/springframework/boot/jdbc/autoconfigure/DataSourceAutoConfiguration` |

### DRIFT-LIKE-015 — `spring-boot-kafka` 缺失 ⇒ `KafkaTemplate` / `ConsumerFactory` 缺失 ⇒ 启动失败且 `@KafkaListener` 永不生效
> ✅ **状态：已修（2026-10-23 第三轮 / 2026-10-24 修复轮）** —— 修法：`service/like/like-rpc/pom.xml:156-159` 显式引入 `org.springframework.boot:spring-boot-kafka`（Boot 4 把 Kafka 自动配置拆出 `spring-boot-autoconfigure`）。**下表是修复前的描述。**

| 项 | 内容 |
|---|---|
| **Component** | like-rpc（Kafka 生产 3 处 + 消费 1 处） |
| **Operation** | 组件级（启动期）+ `LikeAction` / outbox relay / Kafka 消费 |
| **Category** | Framework Module Migration（Boot 4 拆模块） |
| **Go Behavior** | go-queue 自带 producer/consumer，不依赖任何「自动配置」层：`kq.NewPusher`（`internal/svc/servicecontext.go:37`）、`kq.MustNewQueue`（`internal/mq/mq.go:69`） |
| **Java Behavior** | pom 只引 `org.springframework.kafka:spring-kafka`（**`spring-kafka` 的 pom 对 `spring-boot-kafka` 零引用**），**没有** `org.springframework.boot:spring-boot-kafka` ⇒ Boot 4 的 `KafkaAutoConfiguration`（提供 `KafkaTemplate` / `ConsumerFactory`）与 `KafkaAnnotationDrivenConfiguration`（带 `@EnableKafka`，提供 `KafkaListenerAnnotationBeanPostProcessor`）**都不在 classpath 上** |
| **Difference** | ① `KafkaTemplate` Bean 不存在 ⇒ `LikeHotEventOutboxRelay` / `LikeTaskProgressPublisher` / `KafkaLikeMsgPublisher` 三个 `@Component` 全部构造失败；② `ConsumerFactory` Bean 不存在 ⇒ `LikeRpcApplication#kafkaListenerContainerFactory` 创建失败；③ 即使前两条被绕过，**`@KafkaListener` 也永远不会被处理**（无 `KafkaListenerAnnotationBeanPostProcessor`、无 `KafkaListenerEndpointRegistry`） |
| **Observable Impact** | **Spring 上下文启动失败**：`NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.kafka.core.KafkaTemplate<java.lang.String, java.lang.String>' available` ⇒ like-rpc **0 个请求可被服务**；且即使强行启动，**一行消息都不会被消费** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | 🔴 CRITICAL |
| **Counterfactual（§26）** | ✅ **HIGH-RISK DRIFT** —— 一个「按 Spring Boot 3 经验」写的 pom，在 Boot 4 下看起来完全正常（`@KafkaListener` 源码无异常、`spring-kafka` 依赖存在），却让整个服务**静默失去 Kafka 能力**。这正是 AGENTS.md §3.3.3 点名的形态 |
| **Must Preserve** | Kafka 生产（3 个 topic）与消费（1 个 topic / group `like-update-group`）能力必须真实装配 |
| **Evidence** | E-Cp：`mvn -o -pl service/like/like-rpc dependency:tree` 输出**只有** `org.springframework.kafka:spring-kafka:4.0.7:compile`；`spring-kafka-4.0.7.pom` 中 `spring-boot-kafka` 出现 **0** 次；`Class.forName("org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")` → **ClassNotFoundException**；E-CTX：`LikeHotEventOutboxRelay` 真实 Bean 创建失败（`UnsatisfiedDependencyException` → `NoSuchBeanDefinitionException`）；E-Boot：击穿 DRIFT-014 后，应用级第一个失败点正是 `likeHotEventOutboxRelay` 的 `KafkaTemplate`（`/tmp/like-boot-4.log:20`） |
| **历史结论修正** | **2026-10-23 复核：AGENTS.md §3.3.3 与 `docs/Pitfalls/like/rpc.md` 记的「静默失效 —— 上下文照常启动、容器正常起来、一行消息都不消费」，对本组件**不成立**：本组件把 `KafkaTemplate` / `ConsumerFactory` 作为**必需**构造依赖注入 ⇒ 失效形态是**上下文启动失败（硬失败）**，不是静默。两条表述**都不准确**，正确表述见 §7 备注 |

### DRIFT-LIKE-016 — Boot 4 无 Jackson 2 自动配置 ⇒ `com.fasterxml.jackson.databind.ObjectMapper` Bean 缺失 ⇒ 启动失败
> ✅ **状态：已修（2026-10-23 第三轮 / 2026-10-24 修复轮）** —— 修法：新增 `config/Jackson2ObjectMapperConfiguration.java`（自建 Jackson 2 `ObjectMapper` Bean）+ `LikeRpcApplication` 的 `@Import` 引入它。**下表是修复前的描述。**

| 项 | 内容 |
|---|---|
| **Component** | like-rpc（5 个类注入 Jackson 2 `ObjectMapper`：`LikeUpdateKafkaListener` / `LikeDomainService.KafkaLikeMsgPublisher` / `LikeTaskProgressPublisher` / `LikeDomainFactService` / `LikeMessageDigest`） |
| **Operation** | 组件级（启动期） |
| **Category** | Framework Module Migration（Boot 4 JSON 默认切 Jackson 3） |
| **Go Behavior** | `encoding/json`，标准库，无「Bean」概念 |
| **Java Behavior** | Boot 4 把 JSON 自动配置搬进 `org.springframework.boot:spring-boot-jackson`，而它的 `JacksonAutoConfiguration` 构造的是 **Jackson 3**（`tools.jackson.databind.ObjectMapper`，见 `spring-boot-jackson-4.0.8-sources.jar` 的 import 段）；**Boot 4 根本没有 `spring-boot-starter-json`**（本地仓库只有 2.5.14 / 3.2.5 / 3.5.11）⇒ **没有任何自动配置能产出 Jackson 2 的 `ObjectMapper` Bean** |
| **Difference** | 本模块代码全程用 Jackson 2（`com.fasterxml.jackson.databind.*`），但 pom 里既没有 `spring-boot-jackson`，也没有自己声明 `@Bean ObjectMapper` |
| **Observable Impact** | **Spring 上下文启动失败**：`No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available` ⇒ like-rpc **0 个请求可被服务** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | 🔴 CRITICAL |
| **Must Preserve** | 5 个类需要一个**可用的** `ObjectMapper`；⚠️ 若改为 Jackson 3，`GoCompatibleJson` 的逐字符转义层、`@JsonPropertyOrder`、`@JsonIgnoreProperties` 语义都要重新验证（**不是一行改动**） |
| **Evidence** | E-Boot（`/tmp/like-boot-6.log:20`、`/tmp/like-boot-7.log:20`，两次独立启动均命中）；`spring-boot-jackson-4.0.8-sources.jar` 的 `JacksonAutoConfiguration.java:35-44` import 的是 `tools.jackson.databind.ObjectMapper`；本地仓库 `spring-boot-starter-json` 仅到 3.5.11；根 pom 注释「Spring Boot 4 自动配置的 tools.jackson 只在 message-api 的 HTTP 边界用」——**like-rpc 用的 Jackson 2 并没有对应自动配置** |

### DRIFT-LIKE-017 — `like_domain_fact_outbox` DDL 漂移：列类型错 + 丢失 Go 侧的 CHECK / 触发器 / 部分索引 / 版本范围约束
> ✅ **状态：已修（2026-10-23 第三轮）** —— 修法：新增 `V6__align_like_domain_fact_outbox_with_go.sql`（**不改 V4**），6 项对齐 + `LikeDomainFactSchemaParityTest` 8 条真 PG 守门。**下表是修复前的描述。**

| 项 | 内容 |
|---|---|
| **Component** | like-rpc（事实流写路径）+ 跨语言 DDL 耦合 |
| **Operation** | 事实追加（`appendLikeFact`）与事实投递/权威读（Go 侧） |
| **Category** | DDL / Schema |
| **Go Behavior** | **两套 DDL 同时存在**：① `AutoMigrate` 按 struct tag 建表（`internal/model/domain_fact.go:14-29`）；② 手写迁移 `internal/model/migrations/002_like_dc_wire.sql` / `003_like_fact_delivery.sql` 追加列与约束：<br>• `delivery_status smallint NOT NULL DEFAULT 0`、`retry_count integer NOT NULL DEFAULT 0`<br>• `like_fact_stream.version bigint NOT NULL **CHECK (version BETWEEN 1 AND 9007199254740991)**`<br>• `CHECK (aggregate_id IS NOT NULL AND fact_version IS NOT NULL AND delivery_envelope IS NOT NULL) NOT VALID`（约束名 `like_new_fact_has_dc_wire`）<br>• **部分索引** `idx_like_legacy_fact_subject_target ON ((payload->>'subject_ref'),(payload->>'target_type'),(payload->>'target_id')) WHERE delivery_envelope IS NULL`<br>• **触发器** `like_domain_fact_identity_immutable`（BEFORE UPDATE：改动 event_id / event_type / payload / aggregate_id / fact_version / delivery_envelope 即 `RAISE EXCEPTION 'like fact envelope is immutable'`） |
| **Java Behavior** | `src/main/resources/db/migration/V4__create_like_domain_fact_outbox.sql`：`delivery_status **BIGINT**`、`retry_count **BIGINT**`，**无** version 范围 CHECK、**无** `like_new_fact_has_dc_wire`、**无** 触发器、**无** legacy 部分索引；`V5__create_like_fact_stream.sql`：`version BIGINT NOT NULL`（无 CHECK） |
| **Difference** | ① **列类型**：Go 的 `AutoMigrate` 对 `DeliveryStatus int32`（无 type tag）产出 **`integer`**（`gorm.io/driver/postgres@v1.6.0` 的 `DataTypeOf`：`size<=16→smallint`、`size<=32→integer`、否则 `bigint`；`gorm.io/gorm@v1.31.1/schema/field.go:338-339` 把 `int32` 的 `Size` 设为 32），Go 的手写迁移产出 **`smallint`**；Java 侧是 **`bigint`** ⇒ **三方不一致**。<br>② **约束缺失**：Java 侧表**没有**事实信封不可变触发器 ⇒ Java 侧若误 UPDATE 身份列，Go 侧会拒绝、Java 侧不会（灰度期两侧写同一张表 ⇒ 保护不对称）。<br>③ **legacy 部分索引缺失** ⇒ `countUnversionedLegacyFacts` 的查询在 Java 侧建出来的表上无索引支撑。 |
| **Observable Impact** | ① 灰度期两侧写同一张表时，Go 的 `DeliveryStatus int32` ↔ Java 的 `long` 在 `markDeliveryFailed` 的 `retry_count + 1` 等运算上取值范围不同（int32 上界 vs bigint）——目前不溢出，但**类型契约已不一致**；② 不可变性保护单边缺失；③ 查询计划差异 |
| **Classification** | **OBSERVABLE DIFFERENCE** |
| **Risk** | 🟠 HIGH |
| **Must Preserve** | ① 列类型必须与 Go 的 `AutoMigrate` 产物一致（`integer`）；② 不可变性触发器与 `like_new_fact_has_dc_wire` CHECK 必须两侧都在；③ legacy 部分索引必须两侧都在 |
| **Likely Cause（含历史断言修正）** | **2026-10-23 复核：`V4__create_like_domain_fact_outbox.sql` 的注释断言「GORM 对『无 size 的整数』统一取 bigint（不是 integer / smallint）」是错的** —— 被引为依据的 gorm 消费点（`driver/postgres@v1.6.0` 的 `DataTypeOf`）对 `int32`（Size=32）返回的是 **`integer`**。该错误断言是本条漂移的直接成因；这也是首轮「8 条错误断言」之外的**第 9 条错误断言**（新增 **I**） |
| **Evidence** | E-Src：Go `internal/model/domain_fact.go:14-29`、`internal/model/migrations/002_like_dc_wire.sql`、`003_like_fact_delivery.sql`；RAG `V4__create_like_domain_fact_outbox.sql`、`V5__create_like_fact_stream.sql`；gorm 消费点 `gorm.io/driver/postgres@v1.6.0/postgres.go` `DataTypeOf`、`gorm.io/gorm@v1.31.1/schema/field.go:338-339`；E-PG：Java 侧真 PG 建出的列类型（见 §8 CASE-13） |

### DRIFT-LIKE-018 — Kafka 消费并发度 8 → 1
> ✅ **状态：已修（2026-10-23 第三轮）** —— 修法：`LikeRpcApplication` 的 `kafkaListenerContainerFactory` 里 `factory.setConcurrency(likeKafkaTopics.getConsumers())`（= 8，对齐 Go `mq.yaml` 的 `Consumers: 8`）· 配置键 `like.kafka.consumers`。**下表是修复前的描述。**

| 项 | 内容 |
|---|---|
| **Component** | like-rpc（Kafka 消费侧） |
| **Operation** | Kafka 消费（点赞事件落库） |
| **Category** | Throughput / Concurrency |
| **Go Behavior** | `internal/mq/etc/mq.yaml` 的 `Kafka.Consumers: 8`；`go-queue@v1.2.2/kq/config.go:18` 的默认值也是 8；消费点 `kq/queue.go:249` `for i := 0; i < q.c.Consumers; i++` ⇒ **8 个消费协程** |
| **Java Behavior** | `LikeRpcApplication#kafkaListenerContainerFactory`（`:112-120`）只设了 `setConsumerFactory` + `setCommonErrorHandler`，**未设 `setConcurrency`**；`application.yml` 也没有 `spring.kafka.listener.concurrency` ⇒ `ConcurrentKafkaListenerContainerFactory` 默认 **concurrency = 1** |
| **Difference** | 单实例消费并发 8 → 1 |
| **Observable Impact** | ① 单条消息处理耗时不变 ⇒ **端到端延迟在低负载下不变**；② 高负载下积压消化速度 **÷8**；③ `like_record` 的行锁竞争窗口变宽（单消费者串行 ⇒ 反而**冲突更少**，这一项方向相反） |
| **Classification** | **OBSERVABLE DIFFERENCE** |
| **Risk** | 🟠 MEDIUM |
| **Must Preserve** | 消费并发度（或明确记录「有意降为 1」并说明理由） |
| **Evidence** | E-Src：`internal/mq/etc/mq.yaml`、`go-queue@v1.2.2/kq/config.go:18`、`kq/queue.go:249`；RAG `LikeRpcApplication.java:112-120`、`application.yml:110-112` |

### 7.6 对历史条目的复核修正（不改编号、不删原文）

| 历史条目 | 2026-10-23 复核结论 |
|---|---|
| **DRIFT-LIKE-002**（业务码丢失） | **仍然成立**，但**漂移形态要修正为「按业务码逐条不同」**：`LikeGrpcError.mapToGrpcCode`（`like-common/…/LikeGrpcError.java:110-115`）把 `ERROR_INPUT_WRONG(2001)` 映射成 **`INVALID_ARGUMENT(3)`**，其余（含 5003 / 5012）落 `UNKNOWN(2)`。首轮表述「`fromCodeValue(5003)` → `UNKNOWN(2)`」只覆盖了非输入错那一类。**Go 侧 `grpc-status` 对 2001 是 2001、对 5003 是 5003**（`errmsg.NewGrpcErr` = `status.Error(codes.Code(code), msg)`，`codes.Code` 不做范围校验）⇒ **三类码的落点都不同**，但「业务码与文案仍可在 trailer/description 里无损取回」这一结论不变 |
| **DRIFT-LIKE-003**（事实投递链路缺失） | **仍然成立**：Java 侧 6 个骨架文件逐个复核，**仍是 4 行**；生产侧 `LikeDomainFactService` **完整**且已被 `LikeMessageIngestService` 调用 ⇒ 「只写不送」成立 |
| **DRIFT-LIKE-004**（9 次进程内重试） | **仍然成立**：`LikeUpdateKafkaListener.java:170-175` 用的 `new DefaultErrorHandler(recoverer)` **内置 `FixedBackOff(0,9)`**；而同一文件 `:44-49` 的 javadoc 仍写着「不做进程内重试」⇒ **错误断言 E 仍在** |
| **DRIFT-LIKE-005**（Kafka key/分区） | **仍然成立**，且本轮把消费点钉到 `go-queue@v1.2.2/kq/pusher.go:123-125`（`Push` → `PushWithKey(UnixNano)`）与 `:44-48`（`Balancer: &kafka.LeastBytes{}`） |
| **DRIFT-LIKE-006**（模块从未做过 Spring 上下文加载测试） | **仍然成立，且后果已实测**：本轮首次真正启动该应用（E-Boot）⇒ **上下文起不来**（DRIFT-LIKE-013~016）。⇒ 首轮 U1「上下文能否启动」**结论：不能**<br>🔴 **2026-10-23 修复轮更正：本条现已「部分过期」** —— 修复轮新增了 `LikeRpcSpringContextWiringTest`（7 条，**真 PG Testcontainers**），第三轮实跑全绿 ⇒「**从未**做过 Spring 上下文加载测试」不再成立。<br>⚠️ **剩下的真实缺口只有 2 处**（见第三轮 §2 与 §5 的 L-U11）：`rpcInterceptorConfigurer`（Bean 存在但**没挂进 gRPC 调用链**）与 `@EnableScheduling`。<br>🔴 **2026-10-24 第五轮更正：这 2 处现在只剩 1 处** —— `@EnableScheduling` 那半**已补守门测试**（`LikeRpcSpringContextWiringTest` 增到 **9 条**：断言「任务进了调度器」+「任务真的执行过」，**不是**「Bean 存在」）；变异验证：摘掉 `@EnableScheduling` ⇒ **2 条转红** ✅。<br>⇒ **仍然没有**守门测试的是 **`rpcInterceptorConfigurer`**（要验「真挂进 gRPC 调用链」，得观测拦截器的**行为**而不是链里有它）。<br>⇒ 本条**不能**简单标「已关」：断言「Bean 存在」抓不到「Bean 在但没接上」这一形态。 |
| **DRIFT-LIKE-007**（未知字段从忽略变丢弃） | **仍然成立**：`LikeEventContracts.java:40,94,134` 三处 `@JsonIgnoreProperties(ignoreUnknown = false)` |
| **DRIFT-LIKE-008**（`updated_at` 刷新差异） | **仍然成立**，并把 Go 侧机制钉到消费点：`gorm.io/gorm@v1.31.1/callbacks/update.go:201`（`case map[string]interface{}`）→ `:238`（`field.AutoUpdateTime > 0` ⇒ 追加自动更新时间）⇒ **map 形式确实会刷新** ⇒ `LikeRecordMapper.java:196-199` 的注释（错误断言 B）仍错 |
| **DRIFT-LIKE-009**（指标 label 漂移 4 处） | **仍然成立**：`LikeDomainService.java:881`（`redis_read_error` 的 target_type 是 key 反解出来的类型、action 恒 `"n/a"`）、`hgetCountLenient` 只在**异常**时打点而 Go 在**字段不存在**时也打点 |
| **DRIFT-LIKE-010 ~ 012**（trim / ON CONFLICT 目标 / pusher 粒度） | ~~**仍然成立**：`:912` 的 `raw.trim()` vs Go `ParseInt` 不忽略空白；`LikeOutboxEventMapper.java:32` 的 `ON CONFLICT (event_key)` vs Go 无目标；`LikeKafkaTopics.taskPushersEnabled` 单开关 vs Go 两个独立 pusher~~<br>🔴 **2026-10-23 第三轮更正：本条已全部修掉** —— ① `raw.trim()` 已删（`LikeDomainService.java:979-988` + `LikeUpdateKafkaListener.java:201`，**2 处**）；② `ON CONFLICT` 已去掉仲裁目标（`LikeOutboxEventMapper.java:39` ⇒ `ON CONFLICT DO NOTHING`）；③ 单开关已拆成两个独立开关（`LikeKafkaTopics.java:70,80` + `application.yml:189-190` + `LikeTaskProgressPublisher.java:77,93`）。**本行原文保留（首轮快照）。** |
| **首轮 U1**（上下文能否启动） | **已解决**：**不能**。4 个独立硬阻塞，见 §7 DRIFT-LIKE-013~016 |
| **首轮 U3**（Flyway 是否真建表） | **已解决（like-rpc 单体侧）**：真 PG 16 上跑通，**建出了 `flyway_schema_history_like` 账本表 + 5 张 `like_*` 表**（E-PG）。⚠️ **跨组件那一半仍未解决**（三模块共 schema 共存），见 U13 |
| **首轮 8 条错误断言** | **仍然全部在位**（A/B/C/D/E/F/G/H 的原文仍留在源码注释里）⇒ **本轮新增第 9 条错误断言 I**：`V4__create_like_domain_fact_outbox.sql` 的「GORM 对无 size 的整数统一取 bigint」（见 DRIFT-LIKE-017） |
| **AGENTS.md §3.3.3 / `docs/Pitfalls/like/rpc.md`** | **2026-10-23 复核：原结论修正为 ——** 「只引 `spring-kafka` 时 `@KafkaListener` 被完全静默忽略」这一**机制层**描述成立；但**对 like-rpc 这个组件**，`KafkaTemplate` / `ConsumerFactory` 是**必需**依赖 ⇒ 真实失效形态是 **Spring 上下文启动失败**，比「静默不消费」更早、更响。原表述「上下文照常启动、容器正常起来、一行消息都不消费」**不适用于本组件**，保留原文并在此修正 |

## 8. Test Cases

> 状态列：`PASS` = 本轮已实测；`STATIC` = 静态可判定；`BLOCKED` = 被启动失败阻塞，未能运行。

| ID | Input | Environment | Go | Spring Boot | Expected Semantic Result | Equivalent | 状态 / 证据 |
|---|---|---|---|---|---|---|---|
| CASE-01 正常成功 | `LikeAction(targetType=article, userId=42)` | Redis+PG 真 | Lua 返回 2 → 推 Kafka → 回读计数 | 同 | `success=true, like_count=N` | YES | STATIC（E-Src）；**运行期 BLOCKED**（起不来） |
| CASE-02 空输入 | `TargetIds=[]` | — | 2001「输入需要查询的ID为空」 | 同 | 业务码 2001 + 原文 | YES | STATIC（E-Src） |
| CASE-03 不存在 | `GetLikeState` 查不存在的 target | PG 空表 | 0 + **写回 Redis** | 同 | `states[id]=0` 且 Redis 有 0 | YES | STATIC（E-Src） |
| CASE-04 重复 | 同一 msg_id 重投 | PG | inbox 冲突 + hash 相同 + status=1 ⇒ 跳过 | 同 | 不重复应用 | YES | STATIC（E-Src） |
| CASE-05 外部依赖失败（Redis 读） | Redis 停 | — | 全量回源 DB，**不致命** | `*Lenient` 适配 ⇒ 全量回源 | 业务照常返回 | YES | STATIC；运行期 BLOCKED |
| CASE-06 超时 | RPC 超时 2s | — | 拦截器返回 `DeadlineExceeded` | `observability.rpc-timeout: 2s` | 2000ms 上限 | UNKNOWN | **BLOCKED**（U11） |
| CASE-07 Retry | 持久层失败 | PG 停 | 不提交位点 ⇒ 重投 | 抛异常 ⇒ 不提交 | 重投而非丢弃 | YES（但 Java 另有 9 次进程内重试 ⇒ DRIFT-LIKE-004） | STATIC |
| CASE-08 Cache Hit | `like_count` 命中 | Redis | 直返 | 直返 | 一致 | YES | STATIC |
| CASE-09 Cache Miss | `like_count` 未命中 | Redis 空 | 回源 + **HSET 回填** | 同 | 回填后二次读命中 | YES | STATIC |
| CASE-10 权限失败 | 无鉴权层 | — | 无鉴权 | 无鉴权 | 一致 | YES | STATIC（两侧都没实现） |
| CASE-11 并发调用 | 同 user+target 两条消息 | PG | `FOR UPDATE` 串行 + `last_operation_id` 单调守卫 | 同 | 不产生状态错乱 | YES | STATIC |
| CASE-12 重复调用（并发） | 同上 | PG | `INSERT..ON CONFLICT DO NOTHING` + 行锁 | 同 | 一致 | YES | STATIC |
| CASE-13 部分操作成功 | fact 追加成功但 outbox 失败 | PG | 整个事务回滚 | 同（`@Transactional`） | 全部回滚 | YES | STATIC；**运行期 BLOCKED**（U11） |
| CASE-14 Transaction Rollback | inbox 写成功后 record 写失败 | PG | tx 回滚，inbox 不留行 | 同 | 一致 | YES | STATIC |
| CASE-15 Serialization Edge Case | `payload_hash` 跨语言一致 | 含 `<`/`>`/`&` 的 target_id | Go `encoding/json` **开** HTML 转义 | `GoCompatibleJson.write` 逐字符套转义表 | **两侧哈希逐字节相同** | UNKNOWN | STATIC 逻辑一致，但**未做双侧对拍**（U12） |
| **CASE-16（本轮新增）组件启动** | 启动 `LikeRpcApplication` | 真 PG + 真 Redis + 真 Kafka + 真 Nacos | `MustNewServer` 正常起 | **起不来**（4 个阻塞） | 组件可服务 | **NO** | **PASS（实测失败）** E-Boot |
| **CASE-17（本轮新增）Flyway 建表** | 启动到 Flyway 阶段 | 真 PG 16 | `AutoMigrate` 建 5 表 | 建出 `flyway_schema_history_like` + 5 表 | 两类机制各建各的表 | YES（单体侧） | **PASS** E-PG |
| **CASE-18（本轮新增）Kafka 消费就位** | 检查容器 registry | 真 Kafka | 8 协程消费 | 依赖缺失 ⇒ 未装配 | `@KafkaListener` 进 registry | **NO** | **PASS（实测）** E-CTX |
| **CASE-19（本轮新增）DDL 列类型** | 读真 PG 列类型 | 真 PG 16 | `integer`（AutoMigrate）/`smallint`（手写迁移） | `bigint` | 一致 | **NO** | **PASS** E-PG + E-Src |

## 9. Unresolved

| ID | Question | Go Evidence | Java Evidence | Why Unknown | Impact | Required Confirmation |
|---|---|---|---|---|---|---|
| **U1**（首轮） | ~~上下文能否启动~~ | — | — | — | — | ✅ **已解决：不能**（DRIFT-LIKE-013~016） |
| **U2**（首轮） | 拦截器是否真挂上 gRPC server | `like.go:41` `AddUnaryInterceptors` | `LikeRpcApplication.java:90-93` `GrpcServerConfigurer` | **上下文起不来 ⇒ 无法运行时验证**；结构上等价 | 超时 / deadline 传播 / span 全部可能静默失效 | 修完启动阻塞后补一条**装配层测试**（断言 `ServerInterceptor` 真的进了 gRPC server） |
| **U3**（首轮） | ~~Flyway 是否真建表~~ | — | — | — | — | ✅ **已解决（单体侧）**：真 PG 建出 `flyway_schema_history_like` + 5 表；**跨组件那一半见 U13** |
| **U4**（首轮） | `@Select` 承载 `INSERT…RETURNING` 与 `:jsonb` 在真 PG 的行为 | `domain_fact.go:112-116` | `LikeFactStreamMapper.java:45-50`、`LikeDomainFactOutboxMapper.java:53-59` | 只读过 SQL 文本，**未在真 PG 上跑过**（上下文起不来） | 版本取号 / 事实写入可能整条失效 | 启动修复后跑一次「两条消息 ⇒ 版本 1、2」的活体用例 |
| **U5**（首轮） | 真 Kafka 位点行为 | `kq` `ForceCommit` | `ack-mode: record` + `enable-auto-commit: false` | 同上，未跑过真 Kafka | 丢消息 / 重复消费 | 启动修复后用真 Kafka 灌坏消息 + 好消息，验「坏消息提交、好消息不提交」 |
| **U6**（首轮） | `KafkaTemplate.send` 异步失败完全静默 | Go `Push` 同样入队即返回（`kq/pusher.go:82`） | `LikeDomainService.java:1013-1021` | **两侧都静默** ⇒ 不是漂移，但指标口径有别 | `kafka_error` 指标几乎永远打不出来 | 两边都不改的话，写进「已知指标局限」而不是漂移 |
| **U7**（首轮） | `MustNewServer` etcd 注册失败是否 panic | `zrpc.MustNewServer` 失败路径**未查证** | `NacosRegistration.java:90-96` 捕获 `NacosException` 后继续启动 | Go 侧未查证 ⇒ 不能断言「Go 会退出」 | Nacos 抖动时两侧行为可能不同 | 读 `go-zero@v1.10.2/zrpc/rpcthirdparty.go` 或 `rpcregister.go` 的注册失败路径 |
| **U8**（首轮） | `HotspotTTL` 读取点 | 本轮 grep 确认**无读取点** | 同样不读（`LikeRpcProperties.java:70`） | — | 无 | ✅ 结论：**两侧都不读**，不构成漂移 |
| **U9（本轮新增）** | **事实投递链路的末端由谁实现、能不能验证** | `cmd/fact-dispatch/main.go:52-57,72`（`DC_PLATFORM_EVENT_URL` + 8s 超时）；`internal/model/fact_delivery.go:52-126`（SKIP LOCKED 认领 / BLOCKED / FAILED） | `LikeFactDispatchService` / `LikeDeliveryValidator` / `LikeFactDispatchScheduler` **仍是 4 行骨架** | **真 DC 接收服务 `/v1/events` 在 `Sea-RideTheWind_golang` 与 `Sea-BreakTheWaves` 两个仓里都只有发送方/客户端** ⇒ 投递环节只能对 stub server 验证 | Java 侧事实**只写不送**：`delivery_status` 恒 0、`retry_count` 恒 0、5 个技术回执列恒空 | 对着**已冻结的权威端点契约**（`GET /internal/v1/community/facts`）补齐 Java 侧投递与权威读；投递目标用 stub DC 验证 |
| **U10（本轮新增）** | **Java 侧的权威读端点是否会被 `Sea-BreakTheWaves` 回调** | `cmd/fact-authority/main.go:51-55,71`（`LIKE_FACT_AUTHORITY_LISTEN` + token ≥32 字节） | `LikeAuthorityEndpointConfig` **仍是 4 行骨架** | Java 侧**根本没有这个 HTTP 端点** ⇒ 链路在 Java 侧**断开** | 第三仓的 usermodel 画像**永远拿不到 Java 侧产生的 like 事实**（静默缺失） | 决定「Java 侧要不要提供权威读端点」；若要，端口与鉴权按已冻结契约走 |
| **U11（本轮新增）** | 修完 4 个启动阻塞后，**运行时**语义（超时 / 事务 / 指标 / Kafka 位点）是否与 Go 一致 | — | — | 全部依赖「上下文能起」 | 本轮所有静态结论都缺运行时闭环 | 启动阻塞修完 ⇒ 补**一次真实启动 + 一次真实请求**的活体对拍 |
| **U12（本轮新增）** | `payload_hash` / `occurred_at` 等跨语言字节是否**逐字节**一致 | `like_message_hash.go:9-27`、`domain_fact.go:85,96,128` | `LikeMessageDigest.java:65-79`、`GoCompatibleJson.java:56-71,120-143` | 逻辑逐条对齐，但**未双侧对拍** | 灰度期同 msg_id 重投会被误判成「ID 被复用」⇒ 消息卡死 | 双侧跑同一组输入，比对 `payload_hash` 与 `occurred_at` 字节 |
| **U13（本轮新增 · 跨组件不变量）** | **三模块共 schema 共存时，Flyway 账本是否真的互不干扰** | Go 侧无 Flyway | `like-rpc` 单体已证：建出 `flyway_schema_history_like`（E-PG）；`message-rpc` / `article-rpc` 侧**本轮未验** | 跨组件 ⇒ **不属于本组件**，且需要三个模块**同时**对同一 schema 启动才能证 | 任一方账本没按配置表名建 ⇒ 后启动者把自己的迁移当「已应用」静默跳过 | 三模块同时对同一个 `first_db` 启动一次，验三本账本并存 |
| **U14（本轮新增）** | **DRIFT-LIKE-014 的正确修法** | — | — | 三个候选：换 Boot-4 专用 MP starter / 手写 `SqlSessionFactory` Bean / 用 `spring.autoconfigure.exclude` 之外的手段补排序 | 修法不同 ⇒ 后续维护成本与风险不同 | 拍板后由实现者落地 |
| **U15（本轮新增）** | **DRIFT-LIKE-016 的 Jackson 2 去留** | Go `encoding/json`（标准库） | 现用 Jackson 2，但 Boot 4 只自动配置 Jackson 3 | 换 Jackson 3 ⇒ `GoCompatibleJson` 转义层、`@JsonPropertyOrder`、`@JsonIgnoreProperties` 语义全部要重验 | 字节级契约（`payload_hash`）可能破 | 拍板「自建 Jackson 2 `@Bean ObjectMapper`」还是「迁 Jackson 3 并重跑 U12」 |

## 10. 最终判定

### 10.1 spec §39 的 18 项 checklist（逐条）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 两边的 public behavior 是否一致？ | ❌ **否** —— Go 侧 6 个 RPC 正常服务；Java 侧**上下文起不来**，0 个请求可被服务 |
| 2 | 正常路径是否一致？ | ⚠️ **静态一致，运行期未验证** —— 6 个 logic 与 Java 侧逐条对齐，但应用起不来 |
| 3 | 边界输入是否一致？ | ✅ 一致（空 id / 非法 uid / page_size 越界 三处都一致） |
| 4 | NotFound / Empty / Null 是否一致？ | ✅ 一致（`GetLikeState` 查不到 ⇒ 0 + 写回防穿透；`GetLikeCount` 查不到 ⇒ 0/0） |
| 5 | Error meaning 是否一致？ | ❌ **否** —— 业务码的**载体**从 `grpc-status` 迁到 trailer（DRIFT-LIKE-002），且落点按码逐条不同（2001→3，5003/5012→2） |
| 6 | Error propagation 是否一致？ | ✅ 一致（业务异常 ⇒ `observer.onError` ⇒ 不被 grpc-java 覆写 description；兜底 5001） |
| 7 | External dependency failure 是否一致？ | ✅ 一致（Redis 读失败降级、Redis 写失败致命、DB 失败致命、Kafka 失败只记日志、通知失败只记日志） |
| 8 | Cache hit / miss 是否一致？ | ✅ 一致（miss 判定、回源、回填、防穿透、TTL 全部对齐） |
| 9 | Transaction boundary 是否一致？ | ✅ 结构一致（inbox+record+fact+outbox 同一事务）；⚠️ **运行期未验证**（U4 / U11） |
| 10 | State transition 是否一致？ | ✅ 一致（action→state 映射、条件转移、遗留状态拒绝分支全对齐） |
| 11 | Side effects 是否一致？ | ⚠️ **部分** —— Redis/PG/Kafka/通知副作用一致；**事实投递与权威读在 Java 侧缺失**（DRIFT-LIKE-003） |
| 12 | Ordering 是否一致？ | ⚠️ **否（Kafka 分区）** —— Go 用 `LeastBytes` 按 UnixNano key 分区，Java 用 null key 走默认分区器（DRIFT-LIKE-005） |
| 13 | Retry 是否一致？ | ❌ **否** —— Kafka 消费 Java 侧多 9 次进程内重试（DRIFT-LIKE-004） |
| 14 | Timeout scope 是否一致？ | ⚠️ **UNKNOWN** —— 时长都是 2000ms，但运行期未验证（U2 / U11） |
| 15 | Concurrency semantics 是否一致？ | ⚠️ **否** —— 消费并发 8→1（DRIFT-LIKE-018）；游标生成与行锁互斥一致 |
| 16 | Authentication / Authorization 是否一致？ | ✅ 一致（**两侧都没有鉴权层**，身份由调用方注入） |
| 17 | Serialization semantics 是否一致？ | ⚠️ **部分** —— 字段名/顺序/HTML 转义/RFC3339Nano 逻辑对齐；**未知字段行为相反**（DRIFT-LIKE-007）；跨语言字节未对拍（U12） |
| 18 | Lifecycle 是否一致？ | ❌ **否** —— Go 4 进程 / Java 1 JVM（事实投递进程未拆出，首轮已论证「不得内嵌」）；~~且 Java 侧当前**起不来**~~<br>🔴 **2026-10-24 更正**：4 条启动阻塞已修（第三/四轮）⇒ **现在起得来**（`LikeRpcSpringContextWiringTest` 真 PG 实跑全绿）；剩下的「起不来」只剩 **DRIFT-LIKE-013**（生产配置里 `discovery://message-rpc` 两斜杠，且**无测试覆盖**） |

### 10.2 判定

```
存在会改变行为的差异（4 条 SEMANTIC MISMATCH + 2 条 OBSERVABLE DIFFERENCE）
  ⇒ SEMANTICALLY NOT EQUIVALENT
```

| 分类 | 数量 | 编号 |
|---|---|---|
| **SEMANTIC MISMATCH** | **4** | DRIFT-LIKE-013 / 014 / 015 / 016（**全部是「组件起不来」**）+ 历史 DRIFT-LIKE-002 / 003 / 007（本轮复核仍成立） |
| **OBSERVABLE DIFFERENCE** | **2** | DRIFT-LIKE-017 / 018 + 历史 DRIFT-LIKE-004 / 005 / 008 / 009 / 010~012 |
| **已签字的有意偏离** | 1 | DRIFT-LIKE-001（Go 侧 `created_at` 列名错配导致列表恒空；Java 现状视为已修正的 bug） |

🔴 **2026-10-24 更新（第五轮后）—— 这张表是**第二轮**的构成，已过期**：
- **SEMANTIC MISMATCH 4 条**（013/014/015/016）—— **全部已修**（它们是「组件起不来」的 4 条硬启动阻塞）；
- 历史 **002** ⇒ 已定性为**框架限制**（签字偏离 SD-2026-10-23-06）· **007** ⇒ **证伪**（`@JsonIgnoreProperties(ignoreUnknown=false)` 是 no-op）· **003** ⇒ **仍未修**（事实投递链路）；
- **OBSERVABLE 2 条**（017/018）—— **017 已修**（DDL 对齐）· **018 已修**（消费并发 8）；
- 历史 **004/005/008/009/010/011/012** —— **全部已修**（第三轮）。
⇒ **当前仍构成 `NOT EQUIVALENT` 的只剩 003 一条**；当前速览见文件开头「📌 当前状态」。

### 10.3 与首轮判定的关系

- 首轮判定 **NOT EQUIVALENT** ⇒ **本轮维持**，但**性质变了**：首轮是「12 条业务语义漂移」；
  本轮发现的是 **「4 个独立的硬启动阻塞」** ⇒ **在修好它们之前，其余漂移一条都验证不了**
  （这也是为什么 U2 / U4 / U5 / U11 只能停在 UNKNOWN）。
- ⚠️ **优先级建议**：先修 013~016（让组件能起），再谈 017/018 与历史条目。
- ⚠️ **本组件的验证无法闭环整条事实链路**（§1.1）：Java 侧投递侧 + 权威读侧仍是骨架，
  且真 DC 接收服务不在任何仓内 ⇒ U9 / U10 必须带着「链路末端未覆盖」这一前提被下游引用。


---

<!-- @fix 2026-10-23 第三轮：DRIFT-LIKE-017 已修（6 项，不止台账列的 4 项） -->

## 2026-10-23 第三轮：DRIFT-LIKE-017 已修

> like-rpc 当前：**54 tests / 0 failures / 0 errors**（含新类 `LikeDomainFactSchemaParityTest` 8 条），BUILD SUCCESS。
> 🔴 **2026-10-24 审计更正：原文写「64 tests」—— 与任何时点都不自洽**（31 → 46 → 55 → 57 四个数各自可复算，只有 64 无对应状态）⇒ 按本章节时点应为 **54**（46 + 8）。
> **本文件最终条数 = 57**（第三轮 55 + 第五轮 2）。
> 全部在**真 PG** 上验证（Testcontainers `postgres:16-alpine`）。

### 修复方式：新增 V6，**不改 V4**

like-rpc **仍用 Flyway**（独立账本 `flyway_schema_history_like`，AGENTS.md §6.3 问题 4）。
改已发布的 V4 会导致 **checksum mismatch ⇒ 启动失败**。
users 域虽已废弃 Flyway 改幂等重放，但 **like-rpc 没有** —— 两条机制不能混用，只能追加版本。
⇒ `V6__align_like_domain_fact_outbox_with_go.sql`，全部语句幂等（`IF NOT EXISTS` / 存在性判定）。

### ① 列类型 `BIGINT` ⇒ `integer`（核心，且台账自身的纠错是对的）

Go 的 `DeliveryStatus int32` / `RetryCount int32`（`internal/model/domain_fact.go:21-22`）
**无 `type` tag**，要验到<b>两个消费点串起来</b>才算结论：

| 消费点 | 内容 |
|---|---|
| `gorm@v1.31.2/schema/field.go:330-341` | `if field.Size == 0 { … case reflect.Int32: field.Size = 32 }` |
| `gorm.io/driver/postgres@v1.6.0/postgres.go:231-234` | `case size <= 32: return "integer"` |

⇒ AutoMigrate 建出 **`integer`**。

> ⚠️ **V4 原注释「GORM 对『无 size 的整数』统一取 bigint」确实是错的**（错因：只看了
> `field.Size` 默认分支的 `Int64/Int` 那一行，漏了 `Int32` 的独立分支）。
>
> ⚠️ **另一处易误判**：Go 的 `003_like_fact_delivery.sql` 里写的是
> `ADD COLUMN IF NOT EXISTS delivery_status smallint / retry_count integer`，
> 但 **AutoMigrate 已建好列 ⇒ 整条 ALTER 是空操作**，Go 的实际类型始终是 `integer`。

**连带收益**：RAG 的 `LikeDomainFactOutboxEntity` 本来就用 `int`（对应 Go 的 int32），
改完后**实体类型与 DDL 也对齐了**（此前是 DDL=BIGINT / 实体=int 的错配）。

### ②③④⑤⑥ 台账<b>没列全</b>的 5 项

| # | 对象 | Go 出处 | 说明 |
|---|---|---|---|
| ② | `like_new_fact_has_dc_wire` CHECK | `002_like_dc_wire.sql` | `NOT VALID` **逐字照抄** —— 它放行存量行但照常拦新 INSERT；改成 `VALID` 会在存量行不满足时迁移失败，那是 Go 没有的行为 |
| ③ | `idx_like_legacy_fact_subject_target` | `002` | 部分索引 `WHERE delivery_envelope IS NULL`，RAG 侧**此前完全没有** |
| ④ | `idx_like_fact_delivery` | `003` | 复合 `(delivery_status, created_at, event_id)`，是 `FOR UPDATE SKIP LOCKED` + `ORDER BY created_at` 所需；**单列那条（对应 Go 的 `index` tag）保留不删** |
| ⑤ | `like_domain_fact_identity_immutable` 触发器 | `003` | `BEFORE UPDATE`（**非** constraint trigger）+ 函数体逐字照抄 |
| ⑥ | `like_fact_stream_version_range` CHECK | `002` | `version BETWEEN 1 AND 2^53-1` —— version 要进 JSON 信封，超界即静默丢精度 |

### 加约束前的两项安全性核对（不做就是在赌）

1. **⑤ 触发器会不会打死生产写路径**：`LikeDomainFactOutboxMapper` 三处 UPDATE 只写
   `delivery_status` / `retry_count` / `technical_*` / `delivered_at`，
   **没有一列**落在不可变清单（`event_id`/`event_type`/`payload`/`aggregate_id`/`fact_version`/`delivery_envelope`）里
   ⇒ 不会被误伤。
2. **② CHECK 会不会拒绝合法写入**：`LikeDomainFactService:157-158` 调 `insertFact` 时
   `streamId` / `factVersion` / `envelopeJson` **三者都非 null** ⇒ 不会打脸。

### 证据：`LikeDomainFactSchemaParityTest`（8/8，真 PG）

**查 pg_catalog 而非读 DDL 文件** —— schema 类断言只有查真库才算数
（读脚本只能证明「写了什么」，读实体只能证明「声明了什么」）。

- `deliveryColumnsAreIntegerNotBigint` —— `information_schema.columns` 断言 2 列是 `integer`
- `otherColumnTypesUnchanged` —— **反向对照**：`technical_offset`/`fact_version` 必须仍是 `bigint`
  （`int64 ⇒ Size=64`），防止把「改类型」做成无差别降级
- `newFactMustCarryDcWire` —— CHECK **真的触发**：三件套齐全通过，缺 `delivery_envelope` / 缺 `fact_version` 各自被拒
- `legacyPartialIndexExists` / `deliveryCompositeIndexExists` / `singleColumnDeliveryIndexStillPresent`
- `envelopeIsImmutableButDeliveryMetadataIsNot` —— 触发器**双向**验证：
  改 `payload` / `fact_version` 被拒，**改投递元数据必须放行**
  （只测「被拒」会漏掉「误伤生产写路径」这个更危险的方向）
- `factStreamVersionRangeEnforced` —— `version=0` 与 `2^53` 各自被拒

### 过程中踩到并已记录的坑

- ⚠️ **jsonb 列必须显式 `CAST(? AS jsonb)`**：pgjdbc 把 Java `String` 默认绑成
  `character varying`，jsonb 列直接拒收。
  这与 admin-rpc 的 `extra_info` 是**同一个坑**（那边已有注释记录）——
  说明「jsonb + JdbcTemplate 参数」这个组合在本项目已踩过两次，值得进 §3.3 的清单。

---
---

# like-rpc 第三轮 · 修复轮（2026-10-23）

> **本节是修复轮记录，前两节原文一字未改**。
> 首轮 = 2026-10-22（12 条，文件首节）；第二轮 = 2026-10-23 组件级（013~018，见上节）。
> **分支**：`align/like` · **范围**：DRIFT-LIKE-002 / 004 / 005 / 007~012 / 018 + 错误断言 A~I。
> **基线**：改前 `like-rpc` 46 tests 全绿；改后 **55 tests 全绿**（新增 `LikeGoAlignmentTest` 9 条）。
> ⚠️ 这 9 条修复**没有一条**被既有 46 条覆盖 —— 改回原状时它们依然全绿。

## 0. 本轮结论摘要

| | 内容 |
|---|---|
| **已修** | **9 条**：002（定性为框架限制，未改代码）/ 004 / 005 / 007（**证伪**）/ 008 / 009 / 010 / 011 / 012 / 018 |
| **已修** | 错误断言 **A~I 共 9 条**全部清理，另**新发现并清理 4 处** |
| **证伪** | **DRIFT-LIKE-007 不成立** —— `@JsonIgnoreProperties(ignoreUnknown=false)` 是 **no-op** |
| **🔴 纠正台账自身** | **5 处**（原文全部保留，就地加更正）—— 详见 **§4.5**。最重的两处：<br>① `§OP-07` 的「Branch 3/4 ⇒ 返回 err ⇒ **不提交（重投）**」**反了** —— Go 实为**提交位点 ⇒ 丢弃**；<br>② 错误断言 **A** 的前提不成立 —— 它描述的是 Go 侧一条**不可达路径** |
| **台账部分过期** | `§7.6` 对 **006** 的「从未做过 Spring 上下文测试 —— 仍然成立」**已过期**（修复轮新增了真 PG 的 wiring 测试），真实缺口缩到 2 处 |
| **新发现（Java 侧缺陷）** | `epochSeconds` ⇒ 同一条记录冷/热缓存下 timestamp **差 1000 倍**（见 §2.10） |
| **保留未对齐** | 分区器 `LeastBytes` + `Compression: Snappy`（OBSERVABLE，见 **§2.11**） |

## 1. 证据强度声明（本轮）

| 编号 | 类型 | 说明 |
|---|---|---|
| **E-GoSrc** | Go 真源**消费点**源码 | go-zero / go-queue / gorm **依赖源码**读到行号，不只贴 `pom`/yaml |
| **E-FwSrc** | 框架源码消费点 | spring-kafka **4.0.7 sources jar** 实读（`unzip` 后按行号引用） |
| **E-Test** | 本仓真容器/真断言 | `mvn test` 实跑；含 **真 PG Testcontainers**（`LikeDomainFactSchemaParityTest` / `LikeRpcSpringContextWiringTest`） |
| ⚠️ **缺口** | **无真 Kafka** | 本轮**没有**起真 broker ⇒ 「失败消息之后的后续消息是否被处理」**没有活体证据**，只有源码链 |

## 2. 逐条修复记录

### 2.1 DRIFT-LIKE-004 ✅ 已修（并**推翻台账原表述**）

**台账原表述（错）**：§7.6 称「Go `kq` 的 `ForceCommit` 默认 true 且未注册 errorHandler ⇒ **0 次**，失败只不提交位点」。

**实测真源（E-GoSrc）**：

```go
// go-queue@v1.2.2/kq/queue.go:230-243
if err := q.consumeOne(ctx, string(msg.Key), string(msg.Value)); err != nil {
    if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }  // 默认实现：只 logc.Errorf
    if !q.c.ForceCommit { continue }                            // ⚠️ ForceCommit=true ⇒ 不 continue
}
if err := q.consumer.CommitMessages(ctx, msg); err != nil { … } // ⚠️ 落到这里 ⇒ 提交位点
```

- `kq/config.go:24` `ForceCommit bool \`json:",default=true"\``
- `service/like/rpc/internal/mq/etc/mq.yaml` —— **没有覆盖该字段** ⇒ 生效值 `true`
- `kq/queue.go:362-366` 默认 errorHandler 只 `logc.Errorf`，**不改控制流**

⇒ **确定结论：Go 侧消费失败 ⇒ 提交位点 ⇒ 该消息永久丢弃、不重投，且继续处理下一条。**

⚠️ **连带纠正 §OP-07 的 Branch 3 / Branch 4**：表里写的「返回 err ⇒ **不提交（重投）**」是错的。
⇒ 「Go 的 inbox `payload_hash` 不同 ⇒ **报错重投**」（`internal/model/like.go:164-179`）这条注释所述的机制，
**在 Go 侧从未真正发生** —— 「重投」意图被 `ForceCommit=true` 架空。**这是真源自身的缺陷**，已留档，RAG 侧不「顺手修正」。

**Java 侧原实现**：`new DefaultErrorHandler(recoverer)` —— 单参构造内部即 `SeekUtils.DEFAULT_BACK_OFF`
（`DefaultErrorHandler.java:81-83`），而 `SeekUtils.java:57` `DEFAULT_MAX_FAILURES = 10`、`:63` `DEFAULT_BACK_OFF = FixedBackOff(0, 9)`
⇒ **失败消息被真的重新 poll 并重跑 listener 共 9 次**（`FailedRecordTracker.recovered` 每次返回 false ⇒ `SeekUtils.doSeeks` seek ⇒ `seekOrRecover` 抛 `RecordInRetryException`）。

**修法**：`new DefaultErrorHandler(recoverer, new FixedBackOff(0, 0))`
⇒ 第一次失败即 `nextBackOff == STOP` ⇒ `FailedRecordTracker.java:185-195` 走 else 分支 ⇒ 调 recoverer 后**返回 `true`**
⇒ `SeekUtils.doSeeks` **不 seek** ⇒ 容器继续处理后续消息、位点推进 ⇒ **丢弃 + 继续**，与 Go 逐条一致。

⚠️ 顺带修掉 2 处**方向写反**的注释：`LikeRpcApplication` 原写「本处理器不 seek、不提交、失败消息在重启后重投」
与「**默认** recoverer 会 seek ⇒ 丢消息」—— 两者都与 Spring Kafka 4.0.7 的实际控制流**相反**。

### 2.2 DRIFT-LIKE-005 ✅ 已修（**范围比台账小**）

台账说「Java `send(topic,null,payload)` 走默认分区器」——**对，但漏了另两路本来就带 key**。

**真源逐条钉死（E-GoSrc）**：

| 调用点 | API | key |
|---|---|---|
| `likeactionlogic.go:240` `KafkaPusher.Push` | `Push` | **UnixNano**（`pusher.go:123-125` 内部即 `PushWithKey(UnixNano)`） |
| `likeactionlogic.go:317` `TaskUserPusher.PushWithKey` | `PushWithKey` | `strconv.FormatInt(in.UserId,10)` |
| `likeactionlogic.go:340` `TaskArticlePusher.PushWithKey` | `PushWithKey` | `in.TargetId` |
| `like_outbox_sender.go:28` `HotEventPusher.Push` | `Push` | **UnixNano** |

⇒ Java 的两路 TaskProgress **key 本来就对**；真正要改的只有 **2 处 `null`**。

**修法**：新增 `LikeKafkaPushKey.unixNano()`，改 `KafkaLikeMsgPublisher` / `LikeHotEventOutboxRelay`。
⚠️ **不能用 `System.nanoTime()`** —— 它是任意单调计时器值，与 Unix epoch 无关；灰度期逐条 diff 消息元数据会全错。

**顺带修掉台账 U6**：`kafkaTemplate.send(...)` 全模块**零** `whenComplete` ⇒ 异步失败连日志都没有
（而 `application.yml` 原注释声称有「失败回调日志」，**是假的**）。已在 2 处补 `whenComplete` + `LOG.error`。

### 2.3 DRIFT-LIKE-007 ❌ **证伪，漂移不成立**

台账称「`@JsonIgnoreProperties(ignoreUnknown=false)` 覆盖了 Jackson 3 的默认忽略 ⇒ 抛异常 ⇒ 记 `json_error` 并提交位点」。

**证伪依据（E-Test）**：
1. `ignoreUnknown = false` 就是 Jackson 的**默认值** ⇒ 该注解**不含任何"开启严格"的信息**；
2. 类级 `@JsonIgnoreProperties` **无法覆盖**全局的 `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`；
   真正决定行为的是 `Jackson2ObjectMapperConfiguration.java:91` 的 `.disable(FAIL_ON_UNKNOWN_PROPERTIES)`；
3. **绿测实证**：`LikeRpcSpringContextWiringTest#unknownFieldsAreIgnoredLikeGoEncodingJson`
   断言「带 `extra_field` 的消息解析成功」，本轮实跑**通过** ⇒ 实际行为**本就是「忽略」＝与 Go 一致**。

**处置**：删除 3 处误导性注解 + import，并在类 javadoc 写明「要改严格模式请改全局 ObjectMapper 那一处」。

### 2.4 DRIFT-LIKE-008 ✅ 已修（错误断言 **B**）

台账说 Go map 形式**会**刷新 `updated_at`（真 PG 实测）—— **本轮独立复核，成立**：

1. `internal/model/data_model.go:19` `UpdatedAt time.Time \`gorm:"autoUpdateTime;not null;index:idx_user_list,priority:2"\`` ⇒ `AutoUpdateTime > 0`；
2. `gorm.io/gorm@v1.31.1/callbacks/update.go:201` `case map[string]interface{}` → `:236-243`
   `if field.AutoUpdateTime > 0 && value[...] == nil` ⇒ 往 SET **append `updated_at = NowFunc()`**。

⚠️ 而且该列正是 `idx_user_list(user_id, updated_at)` 的 priority:2 ⇒ 原 Java 实现不刷新 ⇒ **索引内容两侧不同**。

**修法**：`LikeRecordMapper#updateState` 的 SQL 加 `updated_at = now()`。

### 2.5 DRIFT-LIKE-009 ✅ 已修（4 处 label 全改）

| # | 原状 | 真源（E-GoSrc） | 处置 |
|---|---|---|---|
| 1 | `redis_read_error` 第 1 个 label 传**字面量** `"like_count"`/`"dislike_count"` | `likeactionlogic.go:265,280` 传 `in.TargetType` | 改传 `request.getTargetType()` |
| 2 | 第 2 个 label 恒 `"n/a"` | 传 `actionStr` | 改传真实 `actionStr` |
| 3 | **字段为空时完全不打点** | `if err == nil && str != ""` 的 **else** ⇒ 字段不存在**也**打点 | 补打点 |
| 4 | `GetLikeState` **一次都不**打 `redis_error` | `getlikestatelogic.go:53-56` `if err != nil` ⇒ 打点 | 补打点（用可区分「读失败 / 字段不存在」的 `hgetLenientWithError`） |
| 5（额外） | 序列化失败被并进 `kafka_error` | `likeactionlogic.go:236` **`json_error`** 是**独立** label | `publish` 改返回 `PublishOutcome` 三态 |

⚠️ **注意 #3 与 #4 的形状相反**：`LikeAction` 用 else 分支（两种都打点），`GetLikeState` 用 `if err != nil`（只对错误打点）。
⚠️ Go 侧 `LikeActionCount` 共 **7** 个 result label（`invalid_param` / `redis_error` / `idempotent` / `snowflake_error` / `json_error` / `kafka_error` / `success` / `redis_read_error`），Java 此前只有 **6** 个（缺 `json_error`）。

### 2.6 DRIFT-LIKE-010 ✅ 已修（2 处，不止台账记的 1 处）

Go `strconv.ParseInt` **不忽略任何空白**。除台账记的 `LikeDomainService#parseLongOrZero`（`:912` 的 `.trim()`），
**`LikeUpdateKafkaListener#isValid` 的 `Long.parseLong(msg.msgId.trim())` 也是同类** ——
Go `likemq.go:92` 是 `strconv.ParseInt(msg.MsgID, 10, 64)` ⇒ `" 123 "` 在 Go 侧 `parseErr != nil` ⇒ **整条丢弃**，
而 Java 会**接受**。⇒ 两处都已去掉 `.trim()`。

### 2.7 DRIFT-LIKE-011 ✅ 已修

Go 是 `clause.OnConflict{DoNothing: true}`（**无仲裁目标**），Java 写的是 `ON CONFLICT (event_key) DO NOTHING`。

⚠️ **该表有两个唯一约束**（`event_id` 主键 + `uk_event_key`，见 `V3__create_like_outbox_event.sql:16,28`）
⇒ 带上仲裁目标后**撞 `event_id` 主键会抛唯一约束异常**，Go 则静默忽略。⇒ 已去掉目标列。
（`like_consume_inbox` 只有一个唯一约束 ⇒ 两者等价；`like_record` 本来就是无目标写法。）

### 2.8 DRIFT-LIKE-012 ✅ 已修

Go `servicecontext.go:43-44` 是**两个独立** `newOptionalPusher`（各判 `len(Brokers)==0 || Topic==""`）
⇒ 两路可分别启停；Java 合并成一个 `taskPushersEnabled`，**丢失粒度**（Go 侧不存在的能力）。

**修法**：拆成 `task-user-pusher-enabled` / `task-article-pusher-enabled`。
⚠️ **不做旧键回退**：两个字段默认都是 `true`，Spring 绑定**无法区分「未配置」与「显式配成 true」**
⇒ 任何回退逻辑都会把「显式只开一路」误判成「都没配」。已把 yml 键直接换成两个，旧字段标 `@Deprecated` 且不参与判定。
⚠️ **这条是被守门测试当场抓出来的实现 bug**（详见 §4）。

### 2.9 DRIFT-LIKE-018 ✅ 已修

Go `internal/mq/etc/mq.yaml` `Kafka.Consumers: 8`（`kq/config.go:18` 默认值也是 8），消费点 `kq/queue.go:249`
`for i := 0; i < q.c.Consumers; i++` ⇒ **8 个消费协程**；Java 侧 factory 未设 ⇒ 并发度 **1**。

**修法**：`LikeRpcApplication#kafkaListenerContainerFactory` 调 `factory.setConcurrency(likeKafkaTopics.getConsumers())`。

⚠️ **关键坑**：`spring.kafka.listener.concurrency` 在本模块**是死配置** —— 因为该 Bean 自己 `new` 了 factory，
Boot 的 `ConcurrentKafkaListenerContainerFactoryConfigurer` **不参与**。已在 yaml 与 javadoc 两处标注。

### 2.10 🔴 **本轮新发现的 Java 侧缺陷**（不在原台账内）

`epochSeconds`（Unix **秒**）用于 DB 回源写 ZSet 的 score，而写路径用 `UnixMilli`（毫秒）
⇒ 同一条记录在 **cache-hit** 与 **cache-miss** 下返回**差 1000 倍**的 `LikeRecordItem.timestamp`。

**根因正是错误断言 A**：那段注释的依据是「GORM 把 timestamptz 扫进 int64 时取 `data.Unix()` ⇒ 秒」，
**但该前提不成立** ⇒ 描述的其实是一条**不可达路径**：

1. `internal/model/like.go:114-119` `UserLikeListResult.CreateTime` 无 gorm tag
   ⇒ gorm `toDBName("CreateTime")`（`schema/naming.go`）推出 DBName = **`create_time`**；
2. 而 SQL 取的是 **`created_at`**（`like.go:130` `Select("id, target_type,target_id, created_at")`）；
3. 两者不匹配 ⇒ `Scan` 后 **`CreateTime` 恒为 0**
   ⇒ `getuserlikelistlogic.go:94-96` 的 `if timestamp == 0 { continue }` **每行命中**
   ⇒ 该分支**从未**执行 ZADD、**从未**产出任何元素 ⇒ **回源路径整体是死代码**。

⚠️ 这**同时证实了 DRIFT-LIKE-001 的签字依据**（Go 侧冷缓存下列表恒空）。
⚠️ 可对齐的是 Go 的**命中分支**（`getuserlikelistlogic.go:70-77` `realTimestamp := score >> 22`，
score 来自 `UnixMilli << 22`）⇒ **`timestamp` 单位是毫秒**。⇒ Java 两处回源分支（`GetUserLikeList` / `GetTargetLikerList`）已改为 `epochMillis`。

⚠️ **与 DRIFT-LIKE-001 签字不冲突**：签字的是「Go 空列表是缺陷、Java 返回真实数据」，
**没有**签字「Java 应当沿用 Go 那条死路径的秒刻度」。

### 2.11 ⚠️ **保留未对齐**（有意，非遗漏）

Go `kq/pusher.go:44-49` `NewPusher` 固定 `Balancer: &kafka.LeastBytes{}` + `Compression: kafka.Snappy`；
Java 侧用默认（按 key 哈希）分区器、**未配 Snappy**。

⇒ **不改的理由**：二者**不改变消息内容与业务结果**（消费端按 group 均衡），
只影响「消息落在哪个分区 / 网络占用」；而自定义 `Partitioner` 会引入
「broker 扩缩容 / rebalance 时与 kafka-go 行为分叉」的新风险，收益不抵风险。
⇒ 记为 **OBSERVABLE DIFFERENCE**，在 `application.yml` 与 `LikeKafkaPushKey` javadoc 双处留档。

## 3. 错误断言清理（A~I 全部 + 新增 4 处）

| # | 断言 | 复核结论 | 处置 |
|---|---|---|---|
| **A** | 「回源写秒 / 写路径写毫秒，是真实可观察行为，原样保留」 | ❌ **前提不成立**，描述的是不可达路径（见 §2.10） | 重写 + 改为毫秒 |
| **B** | 「map 形式不会带上 `updated_at`」 | ❌ 错，`update.go:236-243` 会追加 | 重写 + 补 `updated_at = now()` |
| **C** | 「Go `Push` 不带 key」 | ❌ 错，`Push` 内部即 `PushWithKey(UnixNano)` | 重写 + 传 key |
| **D** | 「Go `Push` 是**同步**阻塞写，所以选 acks=1 + 异步」 | ❌ 错，默认挂 `ChunkExecutor`（`pusher.go:66-89`）⇒ 只入队即返回 | 重写（保留 acks=1，与 kafka.Writer 默认一致） |
| **E** | 「不做进程内重试……加 `FixedBackOff` 是新增行为」 | ❌ 半错：`DefaultErrorHandler` 单参构造**内建** `FixedBackOff(0,9)`，正在发生 | 重写 + 显式 0 次 |
| **F** | 「这五个装配点**每一条都有对应的 wiring 测试锁死**」并点名 `LikeGrpcInterceptorWiringTest` | ❌ 该类**不存在**；第 3/5 条**无守门测试** | 重写 + 标注测试缺口 |
| **G** | outbox relay「`Push` 不带 key」 | ❌ 同 C | 重写 + 传 key |
| **H** | 「Go 侧 etcd 注册失败会让 `MustNewServer` **panic** ⇒ 进程退出」 | ⚠️ **结论对、机制错**：是 `logx.Must` ⇒ `os.Exit(1)`（`logs.go:241`，`ExitOnFatal` 默认 true），panic 是 `ExitOnFatal=false` 时的次生路径 | 重写 + 补齐调用链行号 |
| **I** | `V4__…sql` 的「GORM 对无 size 的整数统一取 bigint」 | 首轮已由 DRIFT-LIKE-017 记录；修复轮已加 `V6__align_…sql` | 保留（已对齐） |

**本轮新发现并清理的 4 处**（不在 A~I 内）：

| # | 位置 | 原断言 | 实情 |
|---|---|---|---|
| **J** | `LikeRpcApplication#kafkaListenerContainerFactory` | 「本处理器**不 seek、不提交**，失败消息重启后重投」 | 两句都反了（§2.1） |
| **K** | 同上 | 「**默认** recoverer 会 seek ⇒ 丢消息」 | 反了：默认 9 次重试才是 seek 的那条；本实现显式 0 次 ⇒ **不 seek** |
| **L** | `application.yml` producer 段 | 「Go 的 `Push` 是同步阻塞写」「失败回调日志」 | 前半错（见 D）；后半**该回调根本不存在**（U6） |
| **M** | `application.yml` kafka consumer 段 | 「持久层失败则**不提交**（retry）」 | 错：Go `ForceCommit=true` ⇒ **照样提交**（§2.1） |

## 4. ⚠️ 元教训：守门测试当场抓出**我自己**的实现 bug

给 `task-pushers-enabled` 写「旧键回退兼容」时，我先加了回退逻辑再写测试，
测试立刻红了：`legacyTaskPushersSwitchStillWorks` 期望 `false` 实际 `true`。

⇒ **根因不是笔误，是设计不可行**：两个细分开关默认值都是 `true`，
而 Spring 的绑定**无法区分「未配置」与「显式配置成 true」** ⇒ 任何回退逻辑都会误判。

⇒ 最终改为**不做回退** + yml 直接换键 + 旧字段 `@Deprecated` 不参与判定。
⚠️ 若当时没有这条测试，这段代码会以「兼容层」的姿态留下，且**永远不会被发现** ——
这正是 §3.3.3「绿测试可能是错误答案的守卫」的**反向**版本：
**没有测试的错误，不是错误答案，而是不存在的断言。**

⇒ 已把该用例改为 `legacyTaskPushersSwitchIsNoLongerConsulted`，把「不做回退」这个决定**钉进测试**，
避免下一个人出于「善意」再把回退逻辑加回来。

## 4.5 🔴 本轮对**台账自身**的更正（原文均保留，未删改）

> 这一节记的是「台账自己写错了什么」。
> ⚠️ 保留原文是因为它们当时**基于当时能拿到的证据**，错在**推理链的一环**，
> 而不是抄写错误 —— 这类更正比「抄错了」更值得留档。

| 台账位置 | 原文结论 | 本轮实测 | 更正 |
|---|---|---|---|
| `§OP-07` 表格 Branch 3 / Branch 4 | 「outbox 构造失败 / DB 事务失败 ⇒ 返回 `err` ⇒ **不提交（重投）**」 | `kq/config.go:24` `ForceCommit` 默认 **true** 且 `mq.yaml` 未覆盖 ⇒ `queue.go:235` 的 `if !q.c.ForceCommit { continue }` **不成立** ⇒ 落到 `CommitMessages` ⇒ **提交位点** | ❌ **反了**。Go 侧这两个分支是**丢弃**，不是重投 |
| `§7.6` 对 004 的复核 | 「Go 的 `ForceCommit` 默认 true 且未注册 errorHandler ⇒ **0 次**，失败**只不提交位点**」 | 同上 | ⚠️ **「0 次」对，「只不提交位点」错** —— 恰恰是 `ForceCommit=true` 导致**照样提交** |
| `§7.6` 对 006 的复核 | 「模块从未做过 Spring 上下文加载测试 —— **仍然成立**」 | 修复轮已新增 `LikeRpcSpringContextWiringTest`（7 条 / 真 PG），第三轮实跑全绿 | ⚠️ **部分过期**，真实缺口缩到 2 处（见 §5 L-U11）<br>🔴 **第五轮再缩到 1 处**：`@EnableScheduling` 那半已补守门测试（9 条） |
| `§1` 首轮摘要与错误断言 **A** | 「GORM 对 timestamptz 取 `data.Unix()` ⇒ 回源产出**秒**，与写路径毫秒不一致，**是真实可观察行为，原样保留**」 | `UserLikeListResult.CreateTime` 的 gorm DBName 是 `create_time`，`Select` 取的是 `created_at` ⇒ **不匹配 ⇒ 恒 0** ⇒ `getuserlikelistlogic.go:94-96` 每行 `continue` ⇒ **该分支从未执行过 ZADD** | ❌ **前提不成立**，描述的是一条**不可达路径**。详见 §2.10 |
| 错误断言 **E**（源码注释侧） | 「不做进程内重试……若在 Java 侧加 `FixedBackOff` 做 N 次进程内重试，那是新增行为」 | `DefaultErrorHandler` 单参构造**内建** `FixedBackOff(0,9)` ⇒ 该行为**正在发生** | ❌ 半错，见 §2.1 |

⚠️ **这五条的共同形状**（值得记住）：
**每一条都是「读到了声明，但没读到消费点」** ——
`ForceCommit` 读的是 `config.go` 的 tag 而不是 `queue.go` 的 `if`；
`CreateTime` 读的是 struct 字段而不是 `Scan` 的列名匹配；
`FixedBackOff` 读的是调用侧的一行 `new DefaultErrorHandler(x)` 而不是它的构造链。
⇒ 这正是 AGENTS.md §3.3.1 反复强调的那件事，本轮又实证了一次。

## 5. 本轮遗留（未修 / 无证据）

| ID | 内容 | 为什么留着 |
|---|---|---|
| **DRIFT-LIKE-003** | 事实投递链路 6 个骨架文件 | 本轮**有意不碰**（需新建独立进程，scope 过大）；链路末端真 DC 接收服务**不在任何仓**，投递环节只能对 stub 验证 |
| **L-U9** | 「失败消息之后，后续消息是否真的被继续处理」**无活体证据** | 本轮**没有真 Kafka**。源码链已钉死（`recovered` 返回 `true` ⇒ `doSeeks` 不 seek），但那是**源码级**不是**活体级** |
| **L-U10** | 分区器 `LeastBytes` + Snappy | OBSERVABLE，见 §2.11 |
| **L-U11** | `rpcInterceptorConfigurer` / `@EnableScheduling` 无守门测试 | 错误断言 F 已标注为**已知测试缺口**（需真实 gRPC server / 调度器才能验）<br>🔴 **2026-10-24 第五轮：`@EnableScheduling` 那半已修**（见 §6.5）⇒ **只剩 `rpcInterceptorConfigurer` 一处**<br>🔴 **2026-10-24 审计更正：「需真实 gRPC server 才能验」这个理由已过时** —— like-rpc 的测试里 gRPC server **早就起了**（`grpc.server.port=0`）；真正的路径是「发一个慢请求，看客户端是否在 `observability.rpc-timeout: 2s` 后收到 `DEADLINE_EXCEEDED`」⇒ **它从「环境受阻」降级为「尚未实现」**（详见审计章节 §3.2） |

## 6. 本轮新增守门测试

`service/like/like-rpc/src/test/java/com/rag/like/like/rpc/service/LikeGoAlignmentTest.java` —— **9 条**：

`errorHandlerHasZeroInProcessRetries`（反射读**真实生效**的 backOff，锁 0 次）·
`goSideSaysDropNotRetry`（真源语义文档化断言）· `listenerConcurrencyMatchesGoConsumers` ·
`likeMsgPublishUsesNonNullUnixNanoKey`（mock `KafkaTemplate` 捕获 key + 量纲校验）·
`hotEventRelayUsesNonNullKey` · `taskPushersAreIndependentlySwitchable` ·
`legacyTaskPushersSwitchIsNoLongerConsulted` · `parseLongOrZeroRejectsWhitespace` ·
`timestampUnitIsMillisecondsNotSeconds`

⚠️ **能力边界（必须随结论一起读）**：本类证明的是**配置形状与调用参数**，
**不能**证明「真实 broker 上失败消息之后的消费行为」—— 那需要真 Kafka，属 §5 的 L-U9。

---

# like-rpc 第五轮 · L-U11 的 `@EnableScheduling` 半边补上（2026-10-24）

> **分支** `align/like` · **基线**：改前 55 tests 全绿；改后 **57 tests 全绿**（`LikeRpcSpringContextWiringTest` 由 **7 条 → 9 条**）。
> ⚠️ 本轮**没有改一行生产代码** —— 只补了**守门测试** + 更正了**已经过时的 javadoc 断言**。

## 6.5.1 本轮补的是什么（以及为什么它不是「补一条 Bean 断言」）

`DRIFT-LIKE-006` 的真实缺口在第三轮被缩到 **2 处**，本轮关掉其中 **1 处**：

| 缺口 | 状态 |
|---|---|
| `@EnableScheduling` —— outbox relay 的 1 秒 ticker 靠它 | ✅ **本轮补上守门测试** |
| `rpcInterceptorConfigurer` —— 删掉它之后 `getBean(ServerInterceptor.class)` **照样是绿的**，因为 Bean 仍在，只是**没挂进 gRPC 调用链** | ❌ **仍然缺**（要验「真挂上调用链」得观测拦截器的**行为**，不是链里有它） |

### ⚠️ 为什么「断言 `LikeHotEventOutboxRelay` 这个 Bean 存在」**不算**守门测试

那个 Bean 是 `@Component` ⇒ **摘掉 `@EnableScheduling` 之后它照样被创建**，
只是 ticker 再也不会被触发 ⇒ **热点事件永远积压，且启动日志零告警、零报错**。
这正是 AGENTS.md §3.3.3「断言『Bean 存在』不等于断言『机制生效』」的现场。

⇒ 本轮锁的是**两件更强的事**：

1. **装配层**：`ScheduledTaskHolder`（Spring 7 里在 `org.springframework.scheduling.config` 包，
   由 `ScheduledAnnotationBeanPostProcessor` 实现）**真的持有**那个 `@Scheduled` 任务 ——
   识别方式是 `ScheduledMethodRunnable.toString()` = `"<声明类全名>.<方法名>"`
   ⇒ 命中 `com.rag.like.like.rpc.mq.LikeHotEventOutboxRelay.sendPending`。
2. **行为层**：`Task.getLastExecutionOutcome().status()` 从 `NONE`（从没跑过）变成 `STARTED`/`SUCCESS`/`ERROR`
   ⇒ 证明**调度器真的执行了它**。
   ⚠️ `Task` 的 `OutcomeTrackingRunnable`（Spring 6.2+）在**每次执行**时更新 outcome，
   `NONE → STARTED → SUCCESS/ERROR`；且断言用的是 `SUCCESS` 而不只是「≠ NONE」——
   **「ticker 在跑但每轮都抛异常」是另一个缺陷，不能算通过**。

## 6.5.2 🔴 变异验证（不是「跑绿」就算数）

| 变异 | 结果 |
|---|---|
| 摘掉 `LikeRpcApplication` 上的 `@EnableScheduling` | **2 条转红** ✅<br>`relayScheduledTaskIsRegisteredWithTheScheduler` → 「ScheduledTaskHolder 缺失 ⇒ @EnableScheduling 没生效」<br>`relayTaskActuallyRuns` → 同上<br>⚠️ 其余 **7 条仍绿**（它们不依赖调度）—— 这正是期望的判别分布 |

## 6.5.3 🔴 本轮同时更正的两条**已经过时**的 javadoc 断言

§3.3.3 的强制顺序要求**先改错误断言**：本轮改了 **2 处**生产代码注释（**未动任何逻辑**）：

| 位置 | 原文 | 更正为 |
|---|---|---|
| `LikeRpcApplication` 类 javadoc | 「第 3 条与第 5 条**没有对应测试**」 | 第 5 条（`@EnableScheduling`）**已有**守门测试（同一个 wiring 测试类，9 条）；**仍然没有**的是**第 3 条**（`rpcInterceptorConfigurer`） |
| `LikeRpcApplication` 类 javadoc | 「真实的装配层回归网只有 `LikeRpcSpringContextWiringTest`（**7 条**）」 | 改为 **9 条**，并逐条列出覆盖范围（含新增的调度两条） |

⚠️ 这两处若不改，下一个 agent 读到的就是**过期事实** ——
而「注释说没有测试」会让下一个人**以为可以跳过**，
「注释说 7 条」会让下一个人**以为新增的两条不在回归网里**。

## 6.5.4 本轮**没有**做的事（留档，含原因）

| 项 | 为什么没做 |
|---|---|
| `rpcInterceptorConfigurer` 的守门测试 | 要验「真挂进 gRPC 调用链」，得观测拦截器的**行为**（如它设置的 span / deadline 传播），而不是「链里有它」。本轮 gRPC server 确实在 `grpc.server.port=0` 起了，但**构造一条能观测到拦截器行为的请求**需要另立一轮；本轮先把**能干净闭环**的那半边关掉 |
| 真 Kafka（`L-U9`） | `spring-kafka-test` / `testcontainers-kafka` **不在本地 m2** ⇒ 要联网拉依赖；Docker 可用但依赖待补 |

---

# 2026-10-24 台账审计（like-rpc 侧）

> **目的 / 方法**：同 api 侧审计章节（机器扫行号引用 + 逐条比对 **18 个 `DRIFT-LIKE-*` 编号** + 内部自洽性）。
> ⚠️ 本轮**改了 1 处生产文件的注释**（`application.yml` 的错误断言 M —— 台账声称已清理、实际漏改），**未动任何逻辑**。

## 1. 已就地更正的过期陈述（原文保留）

| 位置 | 原文 | 现状 | 处置 |
|---|---|---|---|
| 开头「请先读这条」区块 | 「`like-rpc` **从未做过一次 Spring 上下文启动测试**」 | 第三轮起已有 `LikeRpcSpringContextWiringTest`（**9 条 / 真 PG**），且它当场抓出 4 条启动阻塞 | 划掉 + 加更正 |
| 同区块标题 | 「**12 条漂移**」 | 编号已排到 **018**；13 条已修 / 1 条证伪 | 加更正 |
| 同区块 | 「**31 tests 全绿**」 | **57** | 加更正 + 列出各测试类条数 |
| 同区块 | 「三条 CRITICAL/HIGH …」 | 3 条里**已修 2 条**（起不来 / 业务码载体迁移＝框架限制），**剩 1 条**（事实投递链路） | 加更正 |
| §7 的 013~018 | 通篇是**漂移描述、无状态列** ⇒ 读 §7 的人会以为全还开着 | 014/015/016/017/018 已修；**013 仍未修**且**无测试覆盖** | **每条标题下加「状态」标注** |
| §7.6 的 010~012 | 「**仍然成立**」 | 第三轮**已全部修掉**（trim ×2 / ON CONFLICT / 拆两开关） | 加更正 |
| §10.1 第 18 项 | 「且 Java 侧当前**起不来**」 | 4 条启动阻塞已修 ⇒ **现在起得来**；只剩 013 | 加更正 |
| §10.2 判定表 | 002 列为 SEMANTIC MISMATCH | 偏离档已定性为**框架限制 SD-2026-10-23-06** | 加更正指针（定性不一致的坑） |
| 第三轮 §017 章节 | 「**64 tests**」 | 与**任何时点都不自洽**（31 → 46 → 55 → 57 四个数各自可复算，只有 64 无对应状态）⇒ 该时点应为 **54** | 改 + 注明最终 57 |

## 2. 🔴 本轮**发现并修掉**的一处代码级错误断言（台账声称已清理、实际漏改）

**错误断言 M** —— 台账 §3「错误断言清理」表把它列为**已清理**，但代码里**三句还在**：

| 位置 | 原文（错） | 事实 |
|---|---|---|
| `service/like/like-rpc/src/main/resources/application.yml:92-94` | 「与 Go kq 一样，位点**只在处理成功后提交**，解析/校验失败的坏消息会被**主动提交**（drop），持久层失败则**不提交**（retry）」 | **两句都反了** |
| 同文件 `:97-98` | 「若保留 true，则『持久层失败 → 不提交位点 → retry』这条契约不成立（**kq 的语义是 Consume 返回 error ⇒ 不确认**）」 | **方向写反** |

**真源（已读到消费点）**：

```go
// go-queue@v1.2.2/kq/config.go:24
ForceCommit   bool   `json:",default=true"`

// service/like/rpc/internal/mq/etc/mq.yaml —— 没有覆盖该字段 ⇒ 生效值 true

// go-queue@v1.2.2/kq/queue.go:230-242
if err := q.consumeOne(ctx, string(msg.Key), string(msg.Value)); err != nil {
    if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }   // 默认实现：只 logc.Errorf
    if !q.c.ForceCommit { continue }                             // ⚠️ ForceCommit=true ⇒ 不 continue
}
if err := q.consumer.CommitMessages(ctx, msg); err != nil { … }  // ⚠️ 落到这里 ⇒ **提交位点**
```

⇒ **Go 侧消费失败 ⇒ 提交位点 ⇒ 该消息永久丢弃、不重投**（与 §2.1 的结论一致）。
Java 侧 `DefaultErrorHandler(recoverer, new FixedBackOff(0, 0))` ⇒ 失败**不 seek**、跳过该条、位点在下一条成功时跨过它 ⇒ **同样丢弃**。
⇒ **两侧语义一致：失败即丢弃，不重投** —— 而原注释写的「不提交（retry）」是**与 Go 相反**的方向。

⇒ **本轮已改这三句注释**（零逻辑改动），并把 Go 出处写进 yaml。

⚠️ **元教训**：这是 §3.3.3「**先改/删错误断言，再改实现**」在**同一轮里只做了一半**的现场 ——
第三轮改了**实现**（`FixedBackOff(0,0)`）与**两处 javadoc**（J/K），却漏了 `application.yml` 里的**同款断言**（M），
而台账还把它记成「已清理」。
⇒ **错误断言的清单必须逐条对着文件核，不能凭「我记得改了」勾掉。**

## 3. 🔴 两条**新发现**（此前台账完全没有）

### 3.1 `service/common` 的 `RpcServerInterceptorFactory` javadoc 说错了 —— 且与 like-rpc 的 javadoc **直接冲突**

| 侧 | 断言 |
|---|---|
| `service/common/.../RpcServerInterceptorFactory.java`（**共享模块**） | 「grpc-spring-boot-starter（`net.devh:grpc-server-spring-boot-starter`）**会自动把所有 `ServerInterceptor` 类型的 Bean 注册到 gRPC server**，业务模块无需手写 `grpcServer.use(...)`」 |
| `service/like/like-rpc/.../LikeRpcApplication.java`（like 域） | 「devh starter **不会**自动把用户提供的 `ServerInterceptor` 挂到 server 上。没有它，超时 / deadline 传播 / gRPC span **全部静默消失**，而 Bean 断言照样是绿的」 |

**实测 devh 3.1.0 的真实行为（读源码，不是看文档）**：

| 机制 | 出处 | 收集什么 |
|---|---|---|
| `GlobalServerInterceptorRegistry.initServerInterceptors()` | `net/devh/boot/grpc/server/interceptor/GlobalServerInterceptorRegistry.java:79-87` | 只遍历 **`GlobalServerInterceptorConfigurer`** Bean |
| `AnnotationGlobalServerInterceptorConfigurer.getServerInterceptorBeans()` | 同包 `:57-60` | `applicationContext.getBeansWithAnnotation(**GrpcGlobalServerInterceptor**.class)` ⇒ **只认带这个注解的 Bean** |
| `@GrpcService(interceptors = …)` | `net/devh/boot/grpc/server/service/AnnotationGrpcServiceDiscoverer.java:91` | 另一条显式路径 |

⇒ **普通的 `ServerInterceptor` Bean 不会被自动注册** ⇒ **like-rpc 的 javadoc 是对的，common 的是错的**
（common 那句漏了 `@GrpcGlobalServerInterceptor` 这个前提）。
⇒ 佐证：**全仓 `grep -rn "GrpcGlobalServerInterceptor" service/` 零命中** ⇒ 在本仓，common 那句话**从未成立过**。

⚠️ **本轮没有改 common**（共享模块，§5.3 跨域并发约束）⇒ **留档待处理**。
⚠️ **影响面 = 所有 rpc 域**（`like-rpc` / `comment-rpc` / `follow-rpc` / `message-rpc` / `favorite-rpc` /
`user-rpc` / `admin-rpc` / `task-rpc` / `hot-rpc` / `article-rpc` 全部 `@Import(RpcServerInterceptorFactory.class)`）
⇒ 这条 javadoc 若被下一个人当真，可能有人**删掉各域的 `rpcInterceptorConfigurer`** ⇒
超时 / deadline 传播 / gRPC span **在所有 rpc 域静默消失**、且**没有任何报错**。
⇒ **建议列入下一个「共享组件串行窗口」处理**（与 015 的 `GoStyleJsonBodyConverter` 同批）。

### 3.2 L-U11 留着没做的**理由已经过时**

台账 §5 的 L-U11 写「需真实 gRPC server / 调度器才能验」⇒ **但 like-rpc 的测试里 gRPC server 早就起了**
（`LikeRpcSpringContextWiringTest` 用 `grpc.server.port=0`）⇒ **环境障碍不存在**。

**剩下的问题不是「怎么起 server」，而是「怎么观测拦截器的行为」** —— 而答案已经有了：
`RpcInterceptor` 在超时时会 `wrappedCall.close(DEADLINE_EXCEEDED)`
（`service/common/.../observability/RpcInterceptor.java:55`、`:120`）
而 like-rpc 的 `observability.rpc-timeout: 2s`（`application.yml:252-253`）
⇒ **发一个会睡很久的真实 gRPC 请求 ⇒ 客户端应在 2 秒后收到 `DEADLINE_EXCEEDED`**；
若拦截器没挂上，请求会一直挂着 ⇒ **这正是「行为级」的判据**（不是「链里有它」）。

⇒ **L-U11 的 `rpcInterceptorConfigurer` 半边，从「环境受阻」降级为「尚未实现」** —— 台账已更正（见 §5 与开头速览）。

## 4. 补录：台账此前**零提及**、但代码里存在的东西

| 东西 | 位置 | 说明 |
|---|---|---|
| `spring-boot-kafka` 依赖 | `service/like/like-rpc/pom.xml:156-159` | **014 之后编号那条的修法**（DRIFT-LIKE-015 的修法） |
| `spring-boot-flyway` 依赖 | 同上 `:224-227` | Boot 4 拆模块后必需 —— 缺它**启动日志里连一行 Flyway 都没有** |
| `commons-pool2` 依赖 | 同上 `:124-127` | Lettuce 连接池 |
| `grpc-inprocess` 显式版本 | 同上 `:265-270` | 测试用 |
| `MybatisPlusOrderingBridgeAutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `config/` + `resources/META-INF/` | **DRIFT-LIKE-014 的修法**（台账原正文只写了「怎么坏」，没写「怎么修」） |
| `Jackson2ObjectMapperConfiguration` | `config/Jackson2ObjectMapperConfiguration.java` | **DRIFT-LIKE-016 的修法**（含 number→string / number→boolean 一律 `Fail` 的强转策略） |
| `LikeSchemaGate` | `bootstrap/LikeSchemaGate.java`（`@Component`） | Flyway 之后的第二道防线（启动自检）—— 台账此前只在说「测试类 `LikeSchemaGateTest` 不存在」时提过它 |
| `like.redis.list-ttl`（7d） | `application.yml:166-170` + `config/LikeRpcProperties.java:80` | Go 出处已写在 yaml 注释里（`getuserlikelistlogic.go:120` / `gettargetlikerlistlogic.go:128`，两处都是 7 天） |
| `like.kafka.consumers`（8） | `application.yml:189-190` + `config/LikeKafkaTopics.java:47-61` | 对齐 Go `mq.yaml` 的 `Consumers: 8`（DRIFT-LIKE-018 的配置载体） |

## 5. 本轮**没有**做的（留档，含原因）

| 项 | 原因 |
|---|---|
| 改 `service/common` 的 `RpcServerInterceptorFactory` javadoc | **共享模块** ⇒ §5.3 跨域并发约束 ⇒ 留档（见 §3.1），列入下一个共享组件串行窗口 |
| 补 `rpcInterceptorConfigurer` 的守门测试 | §3.2 已把**路径**钉死（发慢请求看 `DEADLINE_EXCEEDED`）⇒ 但这是**新增一条行为级测试**，超出「核对台账」的范围 ⇒ **等你确认后再做** |
| 事实投递链路（003）/ `L-U9`（真 Kafka）/ `L-U10`（有意不修） | 与前几轮结论一致，**状态未变** |

---

## 2026-10-26 主会话转达：global 域 **E60** —— `currentUserLikeTotal` 双失败时**少传 error**（🔴 真漂移，待 like 域 owner 接手）

> **转达方式**：global 域（`align/global`）第三十六轮 **E47 全面订正**时逐条核到 Go 侧，发现本域有一条**真漂移**。
> 落点在 like 域 ⇒ **无 like owner 会话** ⇒ **经主会话转达** ⇒ 本节只是**交接指针**，**不代替 like 域下结论**。

| 项 | 值 |
|---|---|
| 出处 | global 台账 **E60**（`docs/DRIFT/global/_README.md`，**按编号指**） |
| 落点 | `service/like/like-rpc/.../LikeDomainService.java:765-771`（`currentUserLikeTotal`） |
| 行为层 | **RPC 返回值**（error 是否被吞掉） |
| 证据强度 | ✅ **主会话已独立复核 Go 侧**（2026-10-26）—— 不是照抄 global 的结论 |

### 两侧事实

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `dbTotal, dbErr := GetUserActiveLikeCount(...)`；`if dbErr != nil { if err == nil { return int64(total), nil }; return 0, dbErr }` ⇒ **Redis 与 DB 双失败时返回 `(0, dbErr)`** —— **error 被传出去** | `service/like/rpc/internal/logic/likeactionlogic.go:346-364` |
| **RAG** | `catch (Exception e) { if (redisOk) { return total; } LOG.warn("both redis and db failed …"); return 0L; }` ⇒ **双失败时只 `return 0L`** ⇒ **error 被吞掉** | `LikeDomainService.java:762-771` |

### 🔴 附带一条：**javadoc 与实现不符**

`LikeDomainService.java:747-748` 的 javadoc 明写：

> 「即：Redis 与 DB **同时**失败才算失败，且此时返回 `0` + error。调用方 `syncTaskProgress` 只在出错时记日志，不影响点赞结果。」

⇒ 但实现只 `return 0L`（**没有**返回 error）⇒ **注释说的与代码做的不一致**。

### ⚠️ 边界：只有这 **1 处** 是真漂移，**别的不要改**

global 第三十三轮的 **E47 原定性已被第三十六轮全面订正**：**like 域的 5 处 catch 与 Go 一样改写 ⇒ 与 Go 一致 ⇒ 销案 ⇒ 不要改**。
⇒ **本域只有 E60 这 1 处需要处理** ⇒ **不要按 E47 的原定性去动那 5 处**。

### 状态

🟡 **待 like 域 owner 接手**（本域无 owner 会话 ⇒ 主会话转达）⇒ **主会话不改代码、不下结论**，只留指针。
