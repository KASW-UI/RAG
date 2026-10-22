<!-- @move AGENTS.md L57-L71 · §0 like-rpc 事实投递链路跨三个仓库 -->
### 🔴 下一个 agent 请先读这条：`like-rpc` 的事实投递链路**跨三个仓库**（2026-10-22 新发现）

> **`Sea-BreakTheWaves` 是本项目的第三个仓库**：`/Users/pr/Documents/GitHub/Sea-BreakTheWaves`。
> 它是 like 领域事实的**下游消费者** —— 消费 `usermodel.like-facts.v1`，并通过
> `GET /internal/v1/community/facts` **回调 like 的权威端点**做逐字段核对才肯落库。
> ⚠️ **先前只搜了 `Sea-RideTheWind_golang` 与 `RAG` 就断言「DC 接收端不存在」是错的**
> （§3.3 纪律的又一次真实发作：否定式断言不可信，跨仓库尤其）。
> 完整拓扑、冻结的权威端点契约、6 个骨架文件清单、12 条漂移、8 条错误断言、
> 31 条测试的盲区分析 ⇒ 见 **`service/like/like-rpc` 小节**。
>
> **同时注意两个仍未解决的前置项**：
> ① `like-rpc` **从未做过一次 Spring 上下文启动测试**（与 §6.6.5 message-rpc 同型）；
> ② dispatch / authority 两个 Go 入口**只经 DB 耦合**，与 like-rpc 的 gRPC 面无任何调用关系
> ⇒ 它们**不必须**随 like-rpc 一起 Java 化，这是「要不要重实现」的前置判断点。

<!-- @move AGENTS.md L1143-L1342 · §0 like-rpc 12 条漂移 -->
### 🔴 `service/like/like-rpc`：已落地，独立子会话验证判定 **NOT EQUIVALENT**，12 条漂移（2026-10-22）

> 本节是独立 verifier 子会话（Go → Spring Boot 基础组件语义一致性验证）的裁定。
> 模块现状：Go `service/like/rpc` → Java `service/like/like-rpc`，主源 39 文件 / 5247 行
> + 5 份 Flyway 迁移，**31 tests 全绿**（Redis 降级 8 / 幂等状态机 11 / 基础件 9 / gRPC wire 3）。
> ⚠️ **31 条绿测试不能推翻此判定** —— 三条 CRITICAL/HIGH 漂移恰好全部落在
> 「需要真 DB / 真 Redis / 真 Kafka / 跨语言对拍」的缺口里（见末尾盲区分析）。

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
| DRIFT-LIKE-006 | 模块**从未做过 Spring 上下文加载测试**（无 `@SpringBootTest` / `ApplicationContextRunner`），而 javadoc 点名的 4 个 wiring 测试**全部不存在** —— 与 §6.6.5 message-rpc 同型 | UNKNOWN | 🟠 |
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

