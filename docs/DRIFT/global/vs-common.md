<!-- @new 2026-10-27 · `global-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G4 波次工作树（`align/g4-service-common`）** -->

# `global-common`（现 **G4 共享层波**）台账 —— 「共享层 ↔ 各域」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（⚠️ **global 是收口域（§3.5），不是微服务** ⇒ **没有自己的 `-common` 库**；本 lane 对应的就是 `service/common` 本身）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G4` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `global-common`（**原第 10/10 条**） |
| **现分支 / 工作树** | **`align/g4-service-common`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g4-service-common`<br>⚠️ 原 `align/global-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/common/`（`com.rag.common` 共享层：`observability` / `protection` / `http` / `persistence` / `response` / `communityfact` / `exception` / `logger` / `snowflake` / `spring`） |
| 域侧台账 | [`_README.md`](_README.md) · [`cross-domain.md`](cross-domain.md) · [`common-tails.md`](common-tails.md) · [`cycle-deps.md`](cycle-deps.md) · [`lanes/`](lanes/) |
| **写权** | 🔴 **`service/common/**` + `service/users/users-common/**` 的唯一写者**（G4 = 共享层波）＋ 本 lane 台账 `docs/DRIFT/global/vs-common.md`；⚠️ 旧的 `align/global` 全域 lane **已弃用**（Jason：一个工作树跨多域 ⇒ 粒度与操作内容不可控） |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |
| 🔴 **Go 出处的路径口径** | ⚠️ **go-zero 的内部路径一律是「依赖源码」，不是真源仓路径** —— 真源仓 `Sea-RideTheWind_golang` **没有** `core/` `rest/` `zrpc/` 目录（实测：顶层只有 `api/` `doc/` `proto/` `service/` `grafana/` 等）⇒ ⇒ 凡引用 go-zero 内部文件，**必须写成 `go-zero@v1.10.2/<路径>`**（版本 = 真源 `go.mod` pin 的版本）⇒ ⇒ **不带前缀 ⇒ 后人回真源仓找不到 ⇒ 引用等于失效** ⇒ ⇒ **2026-10-27 已把本台账 19 处补齐**（唯一例外 = `javap` 的 Java 常量池签名 `core/annotation/Order;` ⇒ 那不是 go-zero 路径） |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| **E21** | `grpc-inprocess` 是 `test` scope ⇒ 运行时 artifact 消失 | `service/common`（各 rpc pom） | ✅ **已修**（L12/L13） | [`_README.md`](_README.md) |
| **E38②** | 业务层吞掉「SQL 被 deadline 取消」（机制层） | `service/common` | 🟡 **① 已闭环 / ② 另立项**（见下方重核） | [`_README.md`](_README.md) |
| **E46 / E62** | 超时 deadline 与毫秒精度 | `service/common` | ✅ **已修并合回主干** | [`_README.md`](_README.md) |
| **E65** | 出向 gRPC **不继承**请求 deadline | `service/common`（`RpcInterceptor` / `GoStyleRequestDeadline`） | 🔴 **OPEN**（附注已订正，见下方重核） | [`_README.md`](_README.md) |
| **E66** | Redis 写**不绑 deadline** | `service/common` | 🔴 **OPEN**（本轮转立项） | [`_README.md`](_README.md) |
| **E68 / E69 / E70** | 共享复核器的「实体归属」判据 + 「假 FAIL 机器」三处真缺陷 | 共享工具 | ✅ **已落** | [`_README.md`](_README.md) |
| **未修 27 条编号** | 5 个可比包 **3 个 `NOT EQUIVALENT`**（communityfact / logger / observability / snowflake；仅 `response` 逐条等价） | `service/common` | 🔴 **未修** ⇒ **其他域的尾巴挂在它身上** | [`alignment-2026-10-23.md`](../common/alignment-2026-10-23.md) |
| **X-6** | `DRIFT-HOTAPI-030`（Tomcat 参数个数口径）⇒ 共享层 6 个类 + 每域一行 yaml | `service/common` | ✅ **已落地**（第十四轮） | [`cross-domain.md`](cross-domain.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

### 1.1 🆕 §1 的**逐条重核**（核对日期 **2026-10-27** · 基线 `align/g4-service-common` @ `2e2a84f`）

| 编号 | 快照状态 | 本层重核结果 | 处置 |
|---|---|---|---|
| **E21** | ✅ 已修（L12/L13） | 本轮改动面**不涉及** ⇒ **未复核** ⇒ 维持快照状态（不为其补新的核对日期） | 维持 ✅ |
| **E38②** | 🔴 A 类 backlog | 🔴 **口径已过期**：**第 ① 层已闭环** —— 本层**自己复跑** `bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0**、`client_deadline_ctx` **一致**（另 1 条 `timeout_telemetry` 是**已签字的 C-1 表述差异**，不计入失败）。⇒ 剩下的 **②** = **E65 / E66**（架构级第四十六轮已否决原修法 ⇒ 窄化） | 🟡 **① 已闭环 / ② 另立项** |
| **E46 / E62** | ✅ 已修并合回主干 | 本轮改动面**不涉及** ⇒ **未复核** ⇒ 维持 | 维持 ✅ |
| **E65** | 🔴 OPEN ＋ 附注「**`applyTo` 是 no-op**」 | 🔴 **仍 OPEN，但附注错**：`applyTo` **不是 no-op** —— `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:110-120` 实测会 `stub.withDeadlineAfter(remainingNanos, NANOSECONDS)`；且有**真实调用点**（`follow-api` `FollowRpcClient.java:123` · `message-api` `MessageRpcClient.java:87` · `favorite-rpc` `UserRpcClient.java:46` 等）。⇒ **真正的不继承发生在 rpc 进程**：预算 ThreadLocal **只**由 `HttpMiddleware.java:304`（`begin`）/ `:336`（`clear`）写入 ⇒ rpc 进程无 HTTP 入口 ⇒ `DEADLINE_NANOS` 恒 `null` ⇒ `applyTo` 原样返回 stub ⇒ **rpc→rpc 出向不挂 deadline** | 🔴 **OPEN**（**描述订正**） |
| **E66** | 🔴 OPEN | 🔴 **仍 OPEN** ⇒ 本轮**转立项**（路线已探明：Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline ⇒ 零域侧改动；**硬约束**：Boot 4 `LettuceConnectionConfiguration.createClientOptions` 字节码顺序 = offset 146 **先**写 `timeoutOptions`（来自 `spring.data.redis.timeout`）→ offset 151 **才**应用 customizer ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖运维配置**） | 🔴 **OPEN**（转立项） |
| **E68 / E69 / E70** | ✅ 已落 | 本轮改动面**不涉及** ⇒ **未复核** ⇒ 维持 | 维持 ✅ |
| **未修 27 条编号** | 🔴 未修 | 本轮改动面**不涉及**（落点在 `docs/DRIFT/common/alignment-2026-10-23.md`）⇒ **未复核** ⇒ 维持 | 维持 🔴 |
| **X-6** | ✅ 已落地（第十四轮） | ✅ 维持（共享层 6 个类已落码；本轮另有 `GoStyleFormLimitMode` 判据改写 = FB-02，见 §4.6） | 维持 ✅ |

> ⇒ **重核的净结果**：**没有任何一条被推翻**（没有「✅ 其实还没修」的反转）；
> **2 条口径过期已就地订正**（E38② 的 ① 层、E65 的附注）。
> ⚠️ **未复核的条目刻意不补核对日期** —— 「没查」与「查过没问题」是两件事，混起来就是台账失真。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

> **口径说明**（AGENTS.md §5.3d）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**；
> **N/A ≠ 已验** —— N/A 的含义是「**共享层在本轴上没有落点**」（不是「查过没问题」）。

| 轴 | 机制 | **共享层侧落点** | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | `observability/RpcInterceptor`（服务端 deadline 传播）· `observability/GoStyleRequestDeadline`（HTTP 预算载体）· `observability/HttpMiddleware:304`/`:336`（预算写入/清除）· `persistence/DeadlineQueryTimeoutInjector`（SQL statement timeout）· `persistence/SqlTimeoutStatusTranslator`（57014 → `DEADLINE_EXCEEDED`）· `observability/GoStyleRecoverInterceptor` | 🟡 **机制面已对齐**（HTTP 预算 → SQL 取消 → 1015 的整条链，Go 侧逐条有出处）<br>🔴 **但 rpc→rpc 出向仍不继承 deadline（E65）⇒ 共享层侧 = 半开** | **L4**（双侧探针 `run-rpc-timeout-parity.sh` EXIT=0，`client_deadline_ctx` 一致）＋ **L1**（E65 的 ThreadLocal 写入面逐行） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | `spring/RpcServerInterceptorFactory` · `observability/RpcInterceptor` · `protection/GoStyleRpcBreakerInterceptor` · `protection/GoStyleGrpcClientBreakerInterceptor`（**本轮新落** = G3 单 2）· `protection/GoStyleGrpcClientBreakerConfiguration` · `protection/GoStyleProtectionConfiguration:80`（`@Import` 桥）· `protection/GoStyleRpcProtectionChain` | ✅ **client 侧 breaker 已进共享层，10/10 rpc 零改动生效**（`GoStyleProtectionConfiguration` 被 **10/10** rpc `@Import`）<br>🟡 **但只有「有出向调用」的 rpc 组件可观测** —— 本层**自核**（不采信转述）：`@GrpcClient` 计数 ＋ **六种出向方式**交叉核 ⇒<br>**可观测 6 个**：article-rpc **5** · comment-rpc **2** · favorite-rpc **2** · like-rpc **2** · message-rpc **2** · follow-rpc **1**<br>**N/A 4 个**：hot-rpc **0** · task-rpc **0** · user-rpc **0** · admin-rpc **0**（`@GrpcClient` / `newBlockingStub` / `newFutureStub` / `newStub` / `ManagedChannel` / `RestTemplate` / `WebClient` **全 0**） | **L3**（真容器 + 真 gRPC server 守门）＋ **L1**（10/10 `@Import` 用**剥注释后解析注解块**核出；出向面用**六种方式交叉核**） | 2026-10-27 |
| **A3** | HTTP 保护链 | `http/GoStyleFormValues` · `http/GoStyleFormLimitMode` · `http/GoStyleFormValueLimitInterceptor` · `http/GoStyleFormLimitExceededException` · `http/GoStyleFormLimitWebMvcConfig` · `http/GoStyleTomcatParameterLimitCustomizer` · `http/GoStylePathNormalizingFilter`（**本轮新落** = FB-01）· `protection/GoStyleBreakerFilter`（键改**注册期路由模板**）· `protection/GoStyleMaxConnsFilter` · `protection/GoStyleMaxBytesFilter` · `protection/GoStyleCpuSheddingFilter` | 🟡 **共享层 11 个类已验**（含本轮新增：路径归一 / breaker 键）<br>🔴 **仍缺「10 域逐域真实请求」证据**（**共享层已验 ≠ 各域已验**）<br>🟢→🟢 **补（`c15dfeb` · G2 FB-05）**：**`off` 这个取值原先在 YAML 里写不出来** —— YAML 1.1 把未加引号的 `off` 变布尔 `false` ⇒ 域侧按文档写 `mode: off` ⇒ **进程起不来** ⇒ ⇒ 已让 `parse` 接受 `"false"` 这个**精确别名**（**不放宽成任意兜底**）⇒ ⇒ **A3 轴的「配置值 → 代码」这一环此前是断的，现已闭合**（守门 5 条 + 变异 2 个，L4：真启动 + 生产同款 YAML loader）<br>⚠️ `GoStyleMaxConnsFilter` 的 MaxConns 半边 = **已签字偏离**（`SD-2026-10-25-01`）⇒ 不重复质疑<br>🟢→🟢 **补（`5a954bb` · G3 反馈单 轴②）**：**轴 ② 的判据漏了「域自捕获」这一档** ⇒ favorite 的 Go 侧 **7 个 handler 各自捕获** `httpx.Parse` 的错 ⇒ 拒绝形状 = **域自定义信封（200 + {code:5003,msg:"参数错误",data:null}）** ⇒ **既非 `raw` 也非 `envelope`** ⇒ 而旧表把它记成 `raw` ⇒ **错** ⇒ ⇒ 判据补成**两档**（① 域自捕获 ⇒ `off`；② 否则按「Go 侧是否注册 `SetErrorHandler`」分 `raw` / `envelope`）⇒ ⇒ 共享层**不装**闸门（装了会**抢先**于域自己的捕获 ⇒ 形状变 400/5001 ⇒ 与 Go 不符）⇒ ⇒ 与 `tools/difftest/check-tomcat-limits.py` 的「favorite **一项都不许**声明 mode」**不再打架**<br>🔴 **但残留没销账**：favorite 的**边界口径**漂移（**Tomcat 数总「值」数（含空值）vs Go 数非空值个数** ⇒ 总「值」数 2049~10000 且非空值 ≤2048 时 **Go 放行 / RAG 拒**）**仍在** ⇒ 归 `DRIFT-HOTAPI-030`（跨域）+ `DRIFT-FAV-API-001`（域内）⇒ ⇒ 修法要动**运行期容器上限**（= 跨域可观察行为变更）⇒ ⇒ **本层只登记 + 转派**，本轮**不改行为** | **L3**（真 Tomcat 守门：form 闸门 / 路径归一 / breaker 模板键 / Tomcat 参数上限）＋ **L1**<br>🆕 **FB-05 = L4**（真 servlet 上下文 + 真 yaml ⇒ 起得来；生产同款 `YamlPropertySourceLoader` ⇒ 断言拿到 `Boolean.FALSE`）<br>🆕 **G3 轴② = L4**（**双侧活体对拍** `tools/api-envelope-probe/favorite` ⇒ **EXIT=0** ⇒ Go 真源实测 == `expected.txt` == RAG **真 HTTP** 逐字节）＋ **变异 10/10**（每条断言都有专属变异） | 2026-10-27 |
| **A4** | 错误码 / envelope | `http/GoStyleRouterErrorResponses`（404 / 405 **字节形状**）· `http/GoStyleFormLimitExceededException` · `exception/BusinessException` · `response/Response`（⚠️ **业务码本身在域侧** api 的 `GlobalExceptionHandler.mapRPCStatusToCode`，不在共享层） | ✅ **Go 侧「1015 从哪来」已查实并订正归因**（本轮 G3 单 3：1015 = 各 api logic `switch st.Code()` 的 **default 分支**，**不是** go-zero 的错误映射）<br>⚠️ **该结论只对 users 域成立** —— Go 侧 **article 域单列了** `codes.DeadlineExceeded`（`create_article_logic.go:66`）⇒ **不外推** | **L1**（两侧逐行对照，行号逐条核实）＋ 404/405 字节形状另有 **L3**（真 Tomcat） | 2026-10-27 |
| **A5** | 端口治理 | 共享层侧**只有** `http/GoStyleTomcatParameterLimitCustomizer`（管的是**参数个数**，**不是端口**）；**端口段本身在域侧 yaml**（`server.port`）＋ `tools/difftest/check-tomcat-limits.py`（**整合者共享面** ⇒ 本层不写）<br>🆕 **但「段表」这一格归本层**（**global 协调** ⇒ G2 域台账原话「选段表归 global 协调」）⇒ ⇒ **权威端口段表 = §4.10.19** | 🟡 **共享层在本轴落点很薄** ⇒ 端口治理的**主体不在共享层**（代码侧）⇒ 本层只能给「**容器参数上限**」这一格结论<br>🆕 **段表已落定**（§4.10.19）：**已占 8 段 ＋ 预分配 5 段 ＋ 2 域 N/A ＋ OS 临时段禁占** ⇒ ⇒ **全表无重叠**<br>🆕 **共享层自有 6 处 `RANDOM_PORT` ⇒ 不纳入段治理**（§4.10.19.5 · 三条理由）<br>🔴 **新发现 1 条**：favorite-rpc 段 **21400–21499 嵌套在** favorite 探针段 **21400–21599** 内 ⇒ **两侧都在 G3 写权面内** ⇒ **转 G3 自修**（本层零落码） | **L3**（真 Tomcat 参数上限守门）＋ **L1**（段表 = 四树静态实测 · 口径见 §4.10.19.1 · 可复算）<br>⚠️ **段表是协调产物，不构成 A5 的行为级证据** | 2026-10-27 |
| **A6** | errmsg 契约 | 共享层**不产 errmsg**（1015 等业务码在域侧 api）⇒ 共享层只负责**错误响应形状**（`http/GoStyleRouterErrorResponses`） | ✅ **双侧对拍通过**：`bash tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0 · 56 例（一致 54 · 已签字偏离 2 · 不一致 0）**（⚠️ README 原写「23 例」已过期 ⇒ 本轮订正 `2231398`） | **L4**（双侧活体对拍） | 2026-10-27 |
| **A7** | proto 契约 | `service/proto`（**不在共享层写权面**）⇒ 共享层侧 **N/A** | **N/A**（共享层无落点）⚠️ 提醒：**复制忠实 ≠ 消费方式已验**（AGENTS.md §3.4 的效力边界）⇒ 10 个 proto 的 message/字段号/类型/枚举 10/10 一致，但 `optional` presence 语义 / Jackson 配置差异**不在**该结论覆盖内 | **L1**（静态逐条对照，AGENTS.md §3.4 背书） | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | 共享层**无 DDL / migration**（`service/common` 不建表）⇒ 本轴共享层落点 = `persistence/DeadlineQueryTimeoutInjector`（`default-statement-timeout` 的**运行时**语义，**不是** DDL） | **N/A**（共享层不建表）⚠️ DDL 双侧活体逐列对照 = **9 域**（类型/长度 **0 差异**；`DEFAULT CURRENT_TIMESTAMP` 残留 **16 处** = **刻意保留**；users 域缺 3 条非唯一索引 = **性能非行为**）⇒ 结论以各域台账为准 | **L3**（`tools/difftest/check-ddl-static.py` 双侧活体对拍） | 2026-10-27 |
| **A9** | trace / otel | `observability/GoStyleOtelAutoConfiguration`（**`BeanFactoryPostProcessor`** 装配点）· `observability/GoStyleOtelAgent` · `observability/GoStyleOtelConfig` · `http/GoStyleTraceparentFilter` · `observability/GoStyleClientDisconnectProbe` | ✅ **全局 OTel 装配点已收口共享层**（`GlobalOpenTelemetry.set` 只能调一次，且**先 `get()` 会让后续 `set()` 抛 `IllegalStateException`** ⇒ 必须 BFPP ⇒ 理由与 4 个构造器抢跑点已写进 javadoc）<br>🟡 **域特有业务 span 不在共享层**（**comment 的 6 个已由 G3 修完** ⇒ `C-3` 落码 `d2d7268` ⇒ **本层登记的跨域线索闭环**；**favorite 的 16 个仍缺** ⇒ `L-4` 未落 ⇒ **域侧自修** ⇒ ⇒ **共享层零改动**）<br>🔴→🟢 **补（`853767b` · G2 反馈单 FB-04）**：本轴此前**缺一个子面 ——「业务 handler 执行期能拿到当前 server span」**。Go 侧 `tracinginterceptor.go:18-24` 把 span **放回 ctx** ⇒ 业务 `trace.SpanFromContext(l.ctx)` 取得到 ⇒ 属性真落；RAG 侧 handler **不接收 ctx** ⇒ 唯一等价物是 `Span.current()`，而拦截器此前**只在 `close()` / `onCancel()` 里 `makeCurrent()`** ⇒ **handler 执行期取到 invalid（no-op）span** ⇒ 属性**静默丢弃** ⇒ ⇒ 域侧若按 `Span.current().setAttribute(...)` 落码 ⇒ **跑绿但零效果**（与「假绿」同源）⇒ 已在 `onHalfClose()` 里把 `makeCurrent()` 的 scope **包住 handler 执行期**修好 ⇒ **本轴此前不是「全绿」，是「少验了一个面」** | **L3**（真容器 + `GoStyleOtelAgentGateTest`）＋ **L1**（域侧 span 清单由 G3 逐条核）<br>🆕 **FB-04 子面 = L3**（真 netty gRPC server + 真 OTel SDK + `InMemorySpanExporter`）＋ **变异 4/4 精确命中** | 2026-10-27 |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> **本 lane 就是共享层本身** ⇒ 「逐域 `go-form-limit.mode` ＋ Go 侧 `SetErrorHandler` 注册」的对照由本 lane 核：
> comment/follow/hot/like/message **5 域注册 ⇒ envelope**；article/user/admin **3 域未注册且装了闸门 ⇒ raw**；
> **task 未注册，但 Go 侧 `r.Form` 从不求值 ⇒ 语义答案 = `off`（闸门本不该开）** ⇒ 🆕 **G2 域内已改**（`align/g2-message-like-task` 的 `service/task/task-api/src/main/resources/application.yml:225` = `mode: "off"`，**带引号** ⇒ 保持字符串 ⇒ 与 FB-05 的别名修法兼容）⇒ ⇒ **合并后本格即闭环** ⇒ ⇒ ⚠️ **本层树里合并前仍见 `mode: raw` ⇒ 那是「树间差异」，不是「漂移」**（§4.10.23.8）；
> **favorite 未注册，但 7 个 handler 各自捕获 `httpx.Parse` 的错 ⇒ 拒绝形状 = 域自定义信封
> （200 + {code:5003,…}）⇒ 语义答案 = `off`（共享层不装闸门）** ⇒ ⇒ **两侧都限**，只是限的形状不同
> ⇒ ⇒ **不是「零触碰」**（2026-10-27 订正，详见 §4.10.15）。
> ⇒ ⚠️ **共享层已验 ≠ 各域已验** ⇒ 本 lane 仍须**逐域跑一次真实请求**确认拒绝形状。

> **本表的三条诚实边界**（AGENTS.md §0：「已改」≠「已验证」）：
> ① **9 轴里 2 轴在共享层侧是 N/A**（A7 proto / A8 DDL）⇒ **不是「已验」，是「共享层无落点」**；
> ② **A5 共享层落点很薄** ⇒ **代码侧**只给了「容器参数上限」这一格（端口治理的**主体在各域侧**）⇒ **但「段表」这一格归 global（本层）** ⇒ 已落定 ⇒ 见 **§4.10.19**；
> ③ **A1 / A3 共享层已验但仍半开**（E65 / 10 域逐域真实请求）⇒ 不得读成「全绿」。
> **两侧同时跑的探针**（AGENTS.md §3.3 要求「只跑 RAG 的探针不算数」）⇒ 本层实跑：
> `run-parity.sh`（**56 例** · EXIT=0 · A6 证据）＋ `run-rpc-timeout-parity.sh`（**3 例** · EXIT=0 · A1 证据）。
>
> ⚠️ **A2 的影响面是本层「自核 + 订正」的结果**（2026-10-27）：
> G3 的订正把 G3 域内的面从「4/4」改成「**3/4**（hot-rpc N/A）」⇒ **该订正本身也不完整** ——
> 本层逐个 rpc 用 **`@GrpcClient` 计数 ＋ 六种出向方式交叉核**，得：
> **可观测 6 个**（article-rpc 5 · comment-rpc 2 · favorite-rpc 2 · like-rpc 2 · message-rpc 2 · follow-rpc 1）
> ＋ **N/A 4 个**（hot-rpc 0 · **task-rpc 0** · **user-rpc 0** · **admin-rpc 0**）
> ⇒ ⇒ **「共享层缺位」不等于「本域受害」**：缺位是**全域**事实，受害是**逐域**事实
> ⇒ ⇒ 写影响面必须**逐域实测出向面**，不能拿一个域的结论推另一个域。

## 3. 共享层能自修的（**G4 直接修**；G1~G3 只登记 + 派单到这里）

- 🔴 **`service/common/**` + `service/users/users-common/**` 的唯一写权 owner**（AGENTS.md §5.3c·§5.3d·§5.3f）⇒ G1~G3 的派单**全部落到 G4**
- `GoStyleBreakerFilter` 注释订正 · `SqlTimeoutStatusTranslator` javadoc 归因 · `manifest_chain --no-merges` ⇒ 本 lane 自办

## 4. 需要 **G4** 处理的（**本 lane 就是 G4** ⇒ 直接自办）

- （无待派单）**本 lane 是收口方** ⇒ G1~G3 的派单在此汇聚 ⇒ 按「叶子文件」切 lane（`lanes/_PROTOCOL.md`）
- ⚠️ **G4 = 共享层常设执行者**（AGENTS.md §5.3e · 2026-10-27 02:23 Jason 定）：**随时接收** G1/G2/G3 的反馈单 ⇒ **执行落码** ⇒ 跑**全量 2411** ⇒ **回执**反馈方；**落码窗口常开**（不是「只在 G4 波激活」）⇒ G1~G3 期间**只读、只登记**

### 4.1 🆕 派单通道与回执规格（2026-10-27 02:4x · 与 G3 握手定）

⚠️ **前提**：各波次工作树的**台账文件在合并前互不可见**（G3 树里写的 `docs/DRIFT/<域>/vs-common.md`，G4 树里读不到）
⇒ **文件通道不是派单的前置条件，而是「事后核账」的凭证**。

| 轨 | 载体 | 作用 | 时点 |
|---|---|---|---|
| **实时轨** | 跨会话消息（`session send`） | **唯一能在合并前驱动 G4 落码的通道** ⇒ 反馈单 **7 项写全**（现象 / Go 侧 `文件:行号` / RAG 侧现状 / 证据强度 / 建议修法「两侧都应是 Y」/ 影响面 / 是否阻塞本波） | 波次进行中 |
| **正式轨** | 域侧 `docs/DRIFT/<域>/vs-common.md` 的「需转派」段 | **合并后的对账凭证** ⇒ 防「实时轨丢单、事后无痕迹」⇒ 两轨内容应一致 | 合并后 |
| **收口登记** | **本文件**（G4 唯一有写权的文档面） | 派单汇聚处 + 落码回执 | 持续 |

⚠️ **`global/_README.md` §七之二跨域桶 = `SHARED` 面** ⇒ 合并后由整合者统一更新，**G4 与各域都不写**
⇒ 派单不要指望落在那里。

**落码回执规格（固定 4 项）** —— 光给 commit 号，接收方无法判断影响面：

1. `git rev-parse HEAD`；
2. 改动文件清单（逐个 `文件:行号`）；
3. 触及的机制面轴（A1~A9 哪几条）+ 对应 Go 出处；
4. 回归口径与结果 —— **该字段本身必须写全**（🆕 2026-10-27 G3 要求）：
   **①** 跑没跑（`mvn -B clean verify` / 模块级 / 未跑）；**②** **未跑时必须把「未跑的理由」写进这一字段本身**
   （如「纯 javadoc 改动 ⇒ 零字节码变化 ⇒ 全量对本次改动无增量信息」）；
   **③** 跑了的话给出模块数 / 用例数 / failures / errors / skipped / 耗时；**④** 三门禁结果。
   ⇒ ⚠️ **理由不要只写在正文** —— 接收方核账时**直接看第 4 项** ⇒ 理由散落在正文里等于没写。

**大回归错峰**：G4 全量 = `mvn -B clean verify`（上轮 2411 条 / 21:38 min）⇒ **开始前 session send 知会对方**（四棵工作树 `target/` 独立不互踩，仅 CPU/磁盘互抢）。

**高引用度类的响应预期**：修法落在 9 域引用类（`GoStyleProtectionProperties` / `LoggerFactoryBean`）或 8 域引用类
（`GoStyleRouterErrorResponses` / `ObservabilityProperties` / `Response` / `RpcServerInterceptorFactory`）⇒ **编译面 = 16 个组件**
⇒ G4 先自跑全量确认编译面未炸，再回执 ⇒ **此类单比普通单慢一轮**。

**零重叠已实测**（核对日期 2026-10-27）—— 口径 = **源文件**（未构建工作树），用 `check-wave-lane-ownership.py` 自己的 `expand()` + `_rel()` 复算：
**结论 = G4 ∩ G3 = ∅（文件级零重叠）⇒ 跨树 merge 干净**。
⚠️ **不引用「展开 N 个文件」作为规模证据** —— 该数字**随工作树构建状态变化** ⇒ 不可复算 ⇒ 见 §4.2 建议项 1。

### 4.2 🆕 建议项登记（落点在共享面 ⇒ 需整合者或 Jason 点头）

> **性质**：本条落点 = `tools/difftest/**`（**无 lane 拥有 = 共享面**）⇒ **G4 不擅自改**
> ⇒ 按本 lane 写权登记在此 ⇒ 由整合者统一落码，或经 Jason 点头后由 G4 落码。
> **格式** = 反馈单 7 项（`_cross/cross-domain-coupling.md` §3.3 口径）。
>
> ✅ **2026-10-27 02:5x Jason 拍板：整合者合并时统一改** ⇒ **G4 不落码** ⇒ 本条**已登记待整合**。
> ⇒ 期间兜底 = **台账纪律**（引用可复算结论 + 口径 + 核对日期，**不引用「展开 N 个文件」**）。
> ⇒ ⚠️ **整合时请用「`expand()` 排除 `/target/`」这一种改法** —— **不要**用「加断言：白名单不得命中 target」
> ⇒ 那条断言上线即红（37/61 条 pattern 命中 target）⇒ 连带要改 G1/G2/G3 的白名单 = 越权。
> ⇒ 📌 **归属依据（2026-10-27 G3 指出）**：该文件**无 lane 拥有** ⇒ 按 `docs/Worktree.md` §三·1d「无主/共享面 = 合并后由整合者统一更新」
> ⇒ **本就有既有规则可依**，属**登记类**、不必另开一轮拍板。
> ⇒ ⏰ **落码时点**：若 Jason 想提前改（改动量 = `expand()` 一行过滤）⇒ **建议排在 G1~G3 收口之后**，
> 避免它们正在跑的守门中途变口径（**结论不变** —— G3 已实测：重叠/命中全部不变 ⇒ 期间跑旧版守门不会红）。

**建议项 1：`expand()` 不排除 `target/` ⇒ 守门打印的「白名单展开 N 个文件」不可复算**

| 字段 | 内容 |
|---|---|
| 1 现象 | `expand()` 用 `glob.glob(base + "/**/*", recursive=True)` ⇒ **不排除 `target/`** ⇒ 白名单 `<域>/<组件>/**` 会把**构建产物**一并计入 ⇒ 打印的展开数**随工作树构建状态变化** |
| 2 Go 侧出处 | N/A（工具自身缺陷，非行为漂移） |
| 3 RAG 侧现状 | `tools/difftest/check-wave-lane-ownership.py:243-248` |
| 4 证据强度 | **L3 静态实测**（同一脚本、两处运行、数字对拍） |
| 5 建议修法 | ⛔ **不要加「白名单不得命中 `target/`」这条断言** —— 实测 **37/61 条现有 pattern 命中 target** ⇒ 该断言**上线即 EXIT=1（红）** ⇒ 修它要改 **G1/G2/G3 的白名单** = 改别人的写权定义 ⇒ 不可行。<br>✅ **改用「`expand()` 统一排除 `/target/`」（一行过滤）」** ⇒ 实测：断言 **1/2/5 全绿**、断言 **4/6 不受影响**（独立判定 / 扫 `src/main/java`）、**无任何 lane 需改白名单**、**计数完全可复算**（主工作区 / G3 树 / G4 树三处源文件口径**同为 305/322/465/145**）<br>⇒ **两轨分离**：**计数不可复算 = 台账纪律**（引用结论 + 口径 + 核对日期，不引用数字）；**白名单命中 target = `expand()` 的过滤口径问题**（不是断言问题） |
| 6 影响面 | 守门本身（4 条 lane 全读它）· 断言 1（两两重叠）· 断言 5（白名单必须命中真实文件） |
| 7 是否阻塞本波 | **否** —— 当前结论不受影响（见下） |

**实测数据（核对日期 2026-10-27）**：

| 口径 | G1 | G2 | G3 | G4 | 断言 1 |
|---|---|---|---|---|---|
| 主工作区 `RAG/`（target 文件 4485） | 1542 | 1482 | 2141 | **145** | PASS |
| G3 工作树（target 文件 0） | 305 | 322 | 465 | **145** | PASS |

⇒ **G4 恒 145**：其白名单只含 `service/common/src/**` + `service/users/users-common/src/**` + 2 个 `pom.xml`
⇒ **`target/` 在白名单外**（`service/common/target/` 无主）；**ACTIVE pattern 共 61 条，其中 37 条命中 target**
（G1 10/15 · G2 11/17 · G3 16/24 · **G4 0/5**）。
⇒ 🟢 **无现网假红**：实测「含 target / 不含 target」两种口径下**断言 1 均 PASS**，且当前 ACTIVE 白名单**无任何跨 lane 相交**
⇒ 假红需「白名单写成 `service/<域>/**` 这类宽形式」才触发 ⇒ **属潜在脆弱性，非当前缺陷**。
⇒ ⚠️ **本条已导致一次实际污染**（2026-10-27）：G4 台账首版曾写入「G3 展开 465 / G4 展开 145」作为规模证据
⇒ 由 G3 在落账前指出 ⇒ **已订正为「引用结论 + 口径 + 核对日期」**（见 §4.1 末条）⇒ **教训 = 跨树引用数字前先问「这个数字换个工作树还成立吗」**。

**两种改法的实测模拟**（核对日期 2026-10-27）：

| 改法 | 断言 1 | 断言 2 | 断言 5 | 断言 4/6 | 需改他树白名单？ | 可行 |
|---|---|---|---|---|---|---|
| ⛔ 加断言「白名单不得命中 target」 | — | — | — | — | **要**（37 条 pattern 命中 ⇒ 上线即红） | ❌ |
| ✅ `expand()` 排除 `/target/` | ✅ PASS | ✅ PASS | ✅ PASS | 不受影响 | **不要** | ✅ |

⇒ ✅ 改法②下的**可复算计数**（源文件口径）：**G1 305 · G2 322 · G3 465 · G4 145**
⇒ 主工作区（target 4485）/ G3 树（target 0）/ G4 树（target 0）**三处完全一致** ⇒ 这才是**可写进台账的数字口径**。
⇒ ⚠️ 改法②不动任何断言的判定结果，**但会改变守门打印的计数** ⇒ G1~G3 正在跑这个守门 ⇒ 落码时点需知会（不改则计数不可复算，风险为潜在）。

### 4.3 🆕 预告预登记（**G3 实时轨预告 · 非正式派单** · 落点已独立核准 · 核对日期 2026-10-27）

> **来源**：G3 跨会话消息（实时轨）预告 article 域 2 条待派单。**正式派单未到 ⇒ 本节只做落点核准，不落码。**
> ⚠️ **本节的存在意义 = 「派单前先核准落点，别照抄台账的落点列」**（AGENTS.md §3.1：只贴声明行不算证据）

#### 预告 1：`DRIFT-ARTICLE-API-006`（Go 必填字段校验）⇒ ⛔ **不进 G4 排期**

| 核查项 | 结论 |
|---|---|
| **编号 ↔ 主题是否对得上** | ❌ **对不上** —— 权威面 `docs/DRIFT/article/api.md:676-682` 的 `DRIFT-ARTICLE-API-006` = 「**8–10 MiB JSON body 的错误文案**（LOW，**2026-10-24 已确认不修**）」；而 `docs/DRIFT/article/vs-common.md:27` 把「**Go 必填字段校验**」挂在**同一编号**下 ⇒ 命中 `api.md:8-11` 自己警告的「**两套编号同形不同义**」 |
| **该机制是否还缺共享层实现** | ✅ **不缺** —— 契约 `GoStyleRequiredFields` 已落地于 `service/users/users-common/.../http/GoStyleRequiredFields.java:46-53`，带**三段 Go 出处**（`go-zero@v1.10.2/core/mapping/utils.go` `parseOption` · `unmarshaler.go:918-969` · `:1199`）＋ 运行时探针 `tools/difftest/probe-go-required-fields.py`（2026-10-21） |
| **article 域接入情况** | `CreateArticleReq implements GoStyleRequiredFields`（`service/article/article-api/.../dto/CreateArticleReq.java:57` + `:109`）⇒ **已接入**；`DeleteArticleReq.java:14` / `UpdateArticleReq.java:31` **显式不实现**（附了理由） |
| **落点复核结果** | ⇒ 若真残留，落点在 **`service/article/article-api/**`（DTO 层）= G3 自己的写权面** ⇒ 按 C3「域侧能自修的自修」⇒ **不甩给 G4** |
| **结论** | ⛔ **不进 G4 排期** —— ① 编号撞号（须先由 G3 订正编号/摘要）；② 落点疑在域侧 ⇒ 派单前请 G3 核实行号 + 明确落点 |

#### 预告 2：`S-6` 的 `SqlTimeoutStatusTranslator` **javadoc 归因** ⇒ ✅ **可进排期，落点已核准**

| 核查项 | 结论 |
|---|---|
| **落点** | ✅ `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java` 的 javadoc **「Go 出处」段（第 31-35 行）** ⇒ **G4 写权面内**（`service/common/**`） |
| **问题定性** | 该段以「**Go 出处**」措辞断言 Go 行为，**却一个 `文件:行号` 都没有** ⇒ 命中 AGENTS.md **§3.3.2**（以「Go: …」措辞写的断言是高危项，须满足 §3.3.1 三条，否则不得用该措辞） |
| **更深的发现** | 🟡 **结论对、归因措辞错** —— javadoc 写「Go 侧 ctx 到点后…**经 go-zero 的错误映射**成为 1015」⇒ 实测 Go 侧 **1015 不是框架映射、也不是 deadline 映射**：它是**各 api logic 显式 `switch st.Code()` 的 `default` 分支**产出（`service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48`，同族 13 个 admin logic ＋ `service/user/user/api/internal/logic/user/selectavatarlogic.go:60`）⇒ **落点被写偏了一层** |
| **Go 侧真源出处（已逐条核实，2026-10-27）** | ① `service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48` —— `switch st.Code()`(44) / `case codes.Internal:`(45) → `ErrorServerCommon`(46) / `default:`(47) → `errmsg.CodeServerBusy`(48)<br>② `service/user/common/errmsg/errmsg.go:6`（`CodeServerBusy = 1015`）＋ `:41`（`"服务繁忙"`）—— ⚠️ 路径是 **`errmsg/errmsg.go`（目录）**，不是 `errmsg.go`<br>③ `service/common/observability/observability.go:146-158` —— `IsTimeoutError` 是 Go 侧**唯一** `errors.Is(err, context.DeadlineExceeded)` 的公共处理点（`:150` `errors.Is` / `:153` `codes.DeadlineExceeded` / `:157` `netErr.Timeout()`），且**只做可观测性判定、不产码** |
| **RAG 侧出处（已核实）** | ① `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/advice/GlobalExceptionHandler.java:83`（`mapRPCStatusToCode`）＋ **`:90`（default → 1015）**<br>② `service/users/user/user-api/src/main/java/com/rag/users/user/api/advice/GlobalExceptionHandler.java:153` ＋ **`:162`（default → 1015）** |
| **建议修法（两侧都应是 Y）** | ① javadoc 的「Go 出处」段**逐条补上上述 `文件:行号`**（指向**消费点**而非声明点，§3.3.1-3）；② **改正归因措辞** —— 1015 来自 **api logic 的 `switch st.Code()` default 分支**，不是「go-zero 的错误映射」；③ 保留原结论（`DEADLINE_EXCEEDED` 未被单列 ⇒ 落 default ⇒ 1015 ⇒ **api 层无需改动**）—— 该结论经上表 Go/RAG 两侧行号**证实成立** |
| **证据强度** | **L1 源码对照**（Go 侧行号已逐条核实存在）＋ RAG 侧行号已核实 ⇒ **满足正式派单的「Go 侧行号必须真实存在」前置** |
| **落码影响面** | **纯 javadoc** ⇒ 零字节码变化 ⇒ 编译面不受影响；⚠️ 需在回执里说明「全量 2411 对纯 javadoc 改动无增量信息」的取舍，或按 C2 仍跑一次作为共享层复验窗口 |
| **是否阻塞本波** | **否** ⇒ 进 G4 排期（等正式派单到达后落码） |

### 4.4 来自 G3 的三条发现 · **G4 独立复核结果**（核对日期 2026-10-27）

> **来源**：G3 实时轨。**G4 逐条独立复核，未采信对方结论**（复核纪律：读消费点，不读快照）。

| # | G3 的发现 | 复核结论 | G4 的独立证据 |
|---|---|---|---|
| 1 | `article-rpc` / `favorite-rpc` 缺 `baseline-version: 0`；且 **AGENTS.md §6.3 问题 4 只点了 `favorite-rpc`，漏了 `article-rpc`** | ✅ **成立**（G3 主动更正了自己先前那条错报 —— 起因是把 `grep -c` 的**匹配行数**当成了**配置值**） | 全仓 **9 个** rpc 组件逐个实测：**已钉 0** = `comment-rpc:109` · `follow-rpc:76` · `like-rpc:78` · `message-rpc:102` · `task-rpc:76`（**5 个**）｜**有 flyway 段但缺** = **`article-rpc`** · **`favorite-rpc`**（**2 个** ⇒ 即真缺陷面）｜**N/A（无 flyway 段）** = `hot-rpc` · **`admin-rpc`**｜**`user-rpc` 用 `application.yaml`（非 `.yml`）且无 flyway 配置 ⇒ N/A**<br>⇒ ⚠️ G3 的表**漏了 `admin-rpc` 与 `user-rpc` 两行**（均为 N/A ⇒ **不影响结论**） |
| 2 | favorite 的 `go-form-limit.mode` 缺失是**共享层的有意设计** ⇒ **勿提「共享层加一档」的派单** | ✅ **成立** | `service/common/.../http/GoStyleFormLimitMode.java:41-49`（默认 `OFF` 的理由：favorite 不在 global 写权范围 ⇒ **共享层对它必须零影响**；若默认 `RAW` ⇒ favorite 的 2049 场景会从「5003」变「400 原文」= 对别人的域的未授权改动；**其它 9 个域必须显式声明**）＋ `tools/difftest/check-tomcat-limits.py:156-158`（**零触碰守卫**：favorite-api **必须没有**这一项）<br>实测 mode：`article-api` **raw** · `comment-api` **envelope** · `hot-api` **envelope** · `task-api` **raw** · `user-api` **raw** · `admin-api` **raw** · **`favorite-api` 无声明 ⇒ OFF（刻意）** |
| 3 | **A9 轴定义本身**（「span 属性 `audit.*`」）与两侧源码都不符 | ✅ **成立** —— ⚠️ 但 G3 给的按域分布我第一遍**在 RAG 侧查不到**（comment 的 `audit.*`、favorite 的 `biz.*` 均命中 0）⇒ 原因是**那些域特有属性在 Go 侧，不在 RAG 侧** | **共享层两侧都是 `sea.*`**：RAG `ObservabilityConstants.java:63+`（`sea.error.kind` / `sea.biz_code` / `sea.biz_msg` / `sea.timeout` / `sea.slow` / `sea.duration_ms`）＋ Go `service/common/observability/observability.go:24-29`（**同名同值**）<br>**域特有在 Go 侧**：comment = `audit.*`（`service/comment/rpc/internal/logic/update_subject_state_logic.go:53-56` · `create_comment_logic.go:56-59`）· favorite = `biz.*`（`service/favorite/api/internal/logic/favorite/delete_favorite_logic.go:43-44` 等 4 文件）· admin = `audit.*`（RAG 侧已落地 `AdminSpans.java:91-93`）<br>⇒ ⇒ **RAG 侧已落地的域特有属性只有 admin 一处** |

#### 建议项 2：**A9 轴定义措辞缺陷**（落点 = `docs/DRIFT/_cross/domain-common-lanes.md` = 共享面 ⇒ 整合者订正）

| 字段 | 内容 |
|---|---|
| 1 现象 | 总纲 A9 写「span 属性 `audit.*`」⇒ **既不完整也不准确**：① 共享层实际用 **`sea.*`**；② `audit.*` / `biz.*` 是**域特有**、且**按域不同**；③ RAG 侧目前**只落地 admin 一处** |
| 2 Go 侧出处 | `service/common/observability/observability.go:24-29`（`sea.*` 六项）· `service/comment/rpc/internal/logic/update_subject_state_logic.go:53-56`（`audit.*`）· `service/favorite/api/internal/logic/favorite/delete_favorite_logic.go:43-44`（`biz.*`） |
| 3 RAG 侧现状 | `service/common/.../observability/ObservabilityConstants.java:63+`（`sea.*`）· `service/users/admin/admin-rpc/.../observability/AdminSpans.java:91-93`（`audit.*`，**唯一已落地的域特有**） |
| 4 证据强度 | **L1 源码对照**（两侧行号已逐条核实） |
| 5 建议修法 | A9 轴改为**分层表述**：「**共享层通用属性 = `sea.*`（两侧一致）**」＋「**域特有属性按域各异**：comment = `audit.*`（Go 侧）· favorite = `biz.*`（Go 侧）· admin = `audit.*`（两侧）」⇒ 避免把**某一域的约定**写成全轴通则 |
| 6 影响面 | A9 轴的 10 份域台账（各域据此判「A9 已对齐」）· 只影响**判定口径**，不影响运行时 |
| 7 是否阻塞本波 | **否** ⇒ 登记待整合者订正 |

#### 建议项 3：**AGENTS.md §6.3 问题 4 漏记 `article-rpc`**（落点 = `AGENTS.md` = 共享面 ⇒ 整合者订正）

| 字段 | 内容 |
|---|---|
| 1 现象 | §6.3 问题 4 的「实施状态」只列了 **`favorite-rpc` 例外**（缺 `baseline-version: 0`）⇒ **漏了 `article-rpc`** ⇒ 共享面台账记载不完整 |
| 2 Go 侧出处 | N/A（配置层缺陷，非行为漂移） |
| 3 RAG 侧现状 | `service/article/article-rpc/src/main/resources/application.yml`（有 `flyway:` 段 + `baseline-on-migrate: true`，**无 `baseline-version`**）· `service/favorite/favorite-rpc/src/main/resources/application.yml`（同上） |
| 4 证据强度 | **L3 静态实测**（9 个 rpc 组件逐个 `grep -n`） |
| 5 建议修法 | §6.3 的例外清单补上 **`article-rpc`** ⇒ 两条（`article-rpc` + `favorite-rpc`）并列；⚠️ **这两条落点都在域内 yaml ⇒ 域侧自修**（G3 已认领 article 侧）⇒ **共享层只需订正「记载」，不需落码** |
| 6 影响面 | `AGENTS.md` §6.3 一处文字 · 各域台账引用该处时会误以为只有 favorite 有缺口 |
| 7 是否阻塞本波 | **否** ⇒ 登记待整合者订正 |

#### 🔴 **拒单判据：favorite 零影响**（G4 侧的硬线）

> **依据**：`GoStyleFormLimitMode.java:41-49` ＋ `check-tomcat-limits.py:156-158` ＋ AGENTS.md §5.3c。
> ⇒ **G4 收到任何「要求共享层碰 favorite」的派单 ⇒ 一律退回**，并回指上面三处出处。
> ⇒ favorite 域自己的问题（例：域特有 span 属性 `biz.*` 未落地）⇒ **favorite 域自修**，**共享层零改动**。

#### 🟡 **跨域线索（A9 域特有属性在 RAG 侧未落地）—— 属域侧，共享层零改动**

> **线索**：Go 侧 comment 埋 `audit.*`（`update_subject_state_logic.go:53-56`）· favorite 埋 `biz.*`（4 文件）；
> **RAG 侧实测**：comment-rpc 的 span 写入点**只有共享层 `sea.*`**（其测试 `CommentRpcSlowThresholdGateTest:233-245` 断言的是 `ATTR_SLOW`/`ATTR_DURATION` = `sea.*`）⇒ `audit.*` **未埋**；
> favorite 侧**无任何 `SetAttributes` 写入点** ⇒ `biz.*` **未埋**。
> ⚠️ RAG 侧 comment **有** `UpdateSubjectState` 对应功能（`UpdateSubjectStateReq` / `UpdateSubjectStateService` / `CommentController`）⇒ 不是「功能不存在」导致的省略。
> ⇒ **本会话不下「漂移」结论**（未验两侧 span 语义是否等价、也未查 `docs/Intentional_Deviations/` 是否有签字项）
> ⇒ **登记为线索，交 comment / favorite 域侧自查**；**共享层零改动**。

### 4.5 G3 提供的 L4 证据的**独立复核** + 两个新的共享面建议项（核对日期 2026-10-27）

> **背景**：G3 报「`check-ddl-static.py` 在 favorite 域报 `EXIT=1`（2 处 `now()` DEFAULT 差异）⇒ 是常驻容器旧快照 ⇒ **不是代码缺陷**，别照那条红去修」。
> **G4 复核：✅ 结论成立，且定位比 G3 说的更准。**

#### 复核：为什么那条红是**工作库滞后**（而不是代码缺陷）

| 环节 | 实测 |
|---|---|
| **V7 脚本本身** | `service/favorite/favorite-rpc/src/main/resources/db/migration/V7__align_favorite_outbox_time_columns_with_go.sql` ⇒ 标题即「**对齐 … 去掉 DEFAULT now()**」⇒ **代码侧已落地** |
| **活体工作库** | `ddl-favorite-pg / rag_favorite_db` 的 `favorite_fact_outbox.created_at` / `updated_at` **仍带 `now()`** ⇒ **停在 V7 之前** |
| **它是谁建的** | `tools/difftest/check-ddl-static.py` 的工作库（库名 `rag_<域>_db` / `go_<域>_db`，容器 `ddl-<域>-pg`；见该脚本 `:140/:317` 等）⇒ **由迁移脚本直接 psql 重放**、**不走 Flyway** ⇒ ⇒ **工作库是否最新，取决于谁最后重放过** ⇒ **滞后就假红** |
| **账本表** | 该库 **`flyway_schema_history%` 表 = 0 张** ⇒ 印证「不是 Flyway 建的」 |
| **权威面** | `tools/ddl-parity-probe/favorite/run.sh`（`:180-187` **每次新建 `ragdb` 并重放 V1..V7 全量**）⇒ **EXIT=0 才是权威** ⇒ 与 G3 一致 |

⇒ **判别式（写入台账，供下一个 reader 免走弯路）**：
**「`check-ddl-static.py` 报红」时，先用 `tools/ddl-parity-probe/<域>/run.sh` 复核** ⇒ 探针绿 = **工作库滞后**（刷新工作库）｜探针红 = **真漂移**（改代码）。

#### 🔴 **顺带实测：全部 `ddl-*` 容器的工作库都不是 Flyway 建的**

⇒ 9 个容器 × 2 库 = **18 个库，`flyway_schema_history%` 账本表全部 = 0 张**
⇒ ⇒ **「Flyway 账本隔离 / `baseline-version` / `baseline-on-migrate`」这一维度，在 DDL 对拍容器里从未被运行时验证**
⇒ ⚠️ **但不是缺口**：`tools/difftest/check-flyway-concurrent.sh` + `flyway-concurrent-verify.py` **走真 Flyway 路径**
（脚本 `:23-24` 自述校验「各域的表都建出来了、账本互不覆盖、**baseline 位置正确**」；`:238` 打印 `baseline=`）⇒ **该维度有独立的守门覆盖**。

#### 建议项 4：**`check-ddl-static.py` 的工作库滞后 ⇒ 假红**（落点 = `tools/difftest/**` = 共享面 ⇒ 整合者 / 使用者）

| 字段 | 内容 |
|---|---|
| 1 现象 | `check-ddl-static.py` 在 favorite 域报 `EXIT=1`（2 处 `now()` DEFAULT 差异）⇒ **看似代码缺陷，实为工作库滞后** ⇒ 任何域的工作库未刷新都会产生同类假红 |
| 2 Go 侧出处 | Go 侧 `favorite_fact_outbox` 的 `created_at` / `updated_at` **无** `DEFAULT now()`（V7 的对齐目标） |
| 3 RAG 侧现状 | V7 脚本**已落地**（`.../V7__align_favorite_outbox_time_columns_with_go.sql`）｜**工作库** `ddl-favorite-pg/rag_favorite_db` 仍带 `now()` ⇒ **落后于 V7**｜`tools/difftest/check-ddl-static.py:317`（`rag_favorite_db` 库名来源） |
| 4 证据强度 | **L4 活体实测**（容器内 `information_schema.columns` 实查 ＋ 迁移脚本实读 ＋ 探针实跑 `EXIT=0`） |
| 5 建议修法 | **刷新工作库**（重建 `rag_<域>_db` 并按该域 V1..Vn 全量重放）⇒ **不是改代码**；并把上面那条**判别式**写进 `check-ddl-static.py` 的输出（红时提示「先跑 `tools/ddl-parity-probe/<域>/run.sh` 复核」） |
| 6 影响面 | `check-ddl-static.py` 全域输出可信度（**9 域**）· 任何按它的红去改代码的人都会被误导 |
| 7 是否阻塞本波 | **否**（favorite 已由探针证一致） |

#### 建议项 5：**`flyway-concurrent-verify.py` 的 `KNOWN_BLOCKED` 漏记 `article-rpc`**（落点 = `tools/difftest/**` = 共享面 ⇒ 整合者）

| 字段 | 内容 |
|---|---|
| 1 现象 | 该脚本的 `KNOWN_BLOCKED`（`:48-51`）**只登记了 `favorite`**（「缺 `spring.flyway.baseline-version: 0` ⇒ 共享库里 V1 被 baseline 跳过」）⇒ **同形的 `article-rpc` 未登记** ⇒ 已知缺陷清单**不完整** |
| 2 Go 侧出处 | N/A（配置层缺陷，非行为漂移） |
| 3 RAG 侧现状 | `service/article/article-rpc/src/main/resources/application.yml`（有 `flyway:` 段 + `baseline-on-migrate: true` + `table:` + `schemas:`，**无 `baseline-version`**）｜`service/favorite/favorite-rpc/src/main/resources/application.yml`（同形）｜⚠️ 该脚本对**所有**模块都会算出 `baseline_version`（缺省 = `"1"`，`:93-100`）⇒ **它看得见 article 的 `baseline=1`**，只是**已知缺陷账里没登记** |
| 4 证据强度 | **L3 静态实测**（9 个 rpc 组件逐个 `grep -n` ＋ 脚本 `:48-51` / `:93-100` 实读） |
| 5 建议修法 | `KNOWN_BLOCKED` 增补 `article-rpc`（与 `favorite` 同形同因）⇒ **只补账，不改代码**（两个 yaml 的修法都在**域侧**，域侧自修）；⚠️ 顺带**统一消息**：两个 rpc 的 yaml 注释块都写着「2026-10-25 实测修掉的真缺陷 · global 收口域 X-1」⇒ 建议域侧照抄同一段 |
| 6 影响面 | `check-flyway-concurrent.sh` 的已知缺陷账 ⇒ **AGENTS.md §6.3 问题 4 也只点了 favorite**（见建议项 3）⇒ 两处需同步订正 |
| 7 是否阻塞本波 | **否** ⇒ 登记待整合者订正 |

### 4.6 🆕 G2 派单三张（FB-01 / FB-02 / FB-03）—— **已落码**（核对日期 2026-10-27 · 基线 `align/g4-service-common` @ `182fc32`）

> **落点全部独立核准**（读消费点，不采信派单的「落点」列）；**影响面按域实测**（不采信「N 域全命中」）。
> ⚠️ **三张单的落码范围都比派单描述更窄** —— 理由逐条写在下表「本层实际做了什么」。

#### FB-01 · A3「路径归一」进共享层

| 项 | 结论 |
|---|---|
| 1 现象 | Go 的 router 对请求路径做 `path.Clean` ⇒ 路径变体命中同一条路由；RAG 侧原先**只有 article-api 有**（域内副本）⇒ 其余 9 个 api 缺这一层 |
| 2 Go 侧出处 | go-zero v1.10.2 `go-zero@v1.10.2/rest/router/patrouter.go:48`（注册侧 `cleanPath := path.Clean(reqPath)`）+ `:60`（匹配侧 `reqPath := path.Clean(r.URL.Path)`）—— 逐字核对过 |
| 3 RAG 侧现状 | `service/common/src/main/**` 下 `GoStylePathNormalizingFilter` 命中数 = **0**；全仓唯一实现在 `service/article/article-api/.../config/GoStylePathNormalizingFilter.java`（域内类） |
| 4 证据强度 | **L4（双侧活体）**：🟢 Go 侧 = /tmp 临时工程起**真 go-zero v1.10.2 rest server** + curl，5 个 target 全 200；RAG 侧 = **真 Tomcat + 裸 socket** 同 5 个 target 同解 ⇒ ⇒ 两侧比的是**同一个请求**（JDK HttpClient 会自己归一 URI ⇒ 必须裸 socket） |
| 5 建议修法 | **Y = 「所有 api 的请求路径都按 Go 的 `path.Clean` 归一后再匹配」** ⇒ 归一器落**共享层**（不得按域各写一份）⇒ 归一语义 = Go `path/clean.go` ⇒ 必须排在读路径的 Filter 之前 |
| 6 影响面 | 共享层 `service/common/http/*` ⇒ 被 10 个 api 全依赖；实际缺口 **9 个**（article 已有等价实现） |
| 7 是否阻塞本波 | **否** |

**本层实际做了什么（比派单描述窄）**：
- 新增 `service/common/http/GoStylePathNormalizingFilter`（**字节级逐字移植** Go 的 `Clean`）＋ `GoStylePathNormalizingAutoConfiguration`（**唯一注册点**，走 `AutoConfiguration.imports` ⇒ 与各 api 的 `@ComponentScan` 配置**完全解耦** ⇒ 「漏一个域就静默少一层」不可能发生）。
- ⚠️ **过渡期去重**（article 域内副本还在，属 G3 写权）：按 **Bean 名**（不是类型 —— 两份的类不同）判重；判据可靠（组件扫描在解析阶段注册定义、自动配置是延迟导入 ⇒ 求值时已存在）；且**万一失效 ⇒ 两个同名 Bean ⇒ 启动直接失败**（硬失败，不是静默失效）；且 **simple name 与 Bean 名都与旧副本一致** ⇒ article-api 的「精确 9 项 Filter 链序」守门**不需要任何改动**就继续成立（**实测 article-api 13 条全绿**）。
- 🟢 **顺手修掉旧版移植的 2 处偏差**（差分守门抓到的，不是派单提到的）：
  `"/"`（Go `"/"` vs 旧版 `""`）、`".."`/`"../a"`（Go 原样 vs 旧版丢点）⇒ 共享层按 Go 的**字节级算法**重写（写入位置 + 回退到上一个 `/`）⇒ 32 条 Go 实测基准的**差分守门**逐条钉死。
  ⇒ ⚠️ **rooted 根路径族在请求路径上可达** ⇒ article 的域内副本仍带旧偏差 ⇒ 删掉副本后自动消失（见下）。

**🔴 域侧协调项（不在本层写权）**：`service/article/article-api/.../config/GoStylePathNormalizingFilter.java` 应由 **G3 删除**（只删这一个文件，**不要**动 `ArticleApiAssemblyWiringTest`）⇒ 删后 article-api 自动改用共享层那份 ⇒ 链序守门仍成立。

#### FB-02 · A3 form 闸门的开关判据改成「语义可达性」

| 项 | 结论 |
|---|---|
| 1 现象 | 共享层把「`off` 的依据」写成「**favorite 域不在 global 的写权范围内**」⇒ 那是一条**与 Go 行为无关**的理由 ⇒ ① 回答不了「下一个域该开还是该关」② 漏声明的域**静默落到缺省 `off`**（而缺省只对「Go 侧不解析」的域才正确） |
| 2 Go 侧出处 | go-zero v1.10.2 `go-zero@v1.10.2/rest/httpx/requests.go`（`Parse → ParseForm → GetFormValues`）+ `go-zero@v1.10.2/rest/httpx/util.go:15`（`maxFormParamCount = 2048`，数**非空值**）+ `:48-53`（超限 ⇒ `too many form values` ⇒ Parse 失败）⇒ **只有 handler 真调 `httpx.Parse` 的域，`r.Form` 才被求值** ⇒ 两道上限才可达 |
| 3 RAG 侧现状 | `service/common/http/GoStyleFormLimitMode.java:41-49`（旧判据）；`GoStyleTomcatParameterLimitCustomizer.java:107-108` 与 `GoStyleFormValues.java:38`（同一套措辞的三处落点） |
| 4 证据强度 | **L1（两侧源码逐字对照）**；Go 侧命中数**实测**：task/api = **0**、article/api = 6（对照组）、favorite/api = 7、user/api = 8、admin/api = 13 |
| 5 建议修法 | **Y = 「闸门按『Go 侧是否真的解析 form/query』决定开否」** ⇒ 判据从「写权范围」扩成「语义可达性」 |
| 6 影响面 | 判据与 5 类都在 `service/common/http/*` ⇒ 被 10 个 api 全依赖 |
| 7 是否阻塞本波 | **否**；但**阻塞 task-api 的 A3 结案** |

**⚠️ 与派单描述不一致之处（本层独立核实的结论，必须同步给 G2）**：
- **task**：Go 侧 `httpx.Parse` 命中数 = **0** ⇒ 语义答案 = **`off`** ⇒ task 当前 yaml 的 `raw` 是一条**待修漂移**（**G2 域内自修**）。
  🆕 **本条已闭环（2026-10-27 · G2 域内已改）** —— `align/g2-message-like-task` 的 `service/task/task-api/src/main/resources/application.yml:225` = `mode: "off"`（带引号）⇒ ⇒ **合并后即生效** ⇒ ⇒ 详见 §4.10.23.8（**本段是派单当时的记述 ⇒ 按「不改写历史」只加指路，不重写**）。
- **favorite**：Go 侧命中 **7 处** ⇒ 语义答案 = **`raw`** ⇒ 而它实际缺省 `off` ⇒ **这是本次顺带挖出的一条真实漂移**（LOW：Go 400 + text/plain + Go 原文 vs RAG 400 + Tomcat HTML，且 Tomcat 数**参数总数含空值**、Go 数**非空值**）。
  ⇒ **本层处置**：**零行为改动** ⇒ 共享层只改判据表述 + 落一张**逐域取证表**；favorite 的偏离登记为**已知偏离**（含可观察差异）⇒ **不改 favorite**（零写权约束成立 ⇒ 本层的「favorite 零影响」拒单判据成立）。
- **user-api / admin-api**：Go 侧命中 8 / 13 处 ⇒ 语义答案 `raw` ⇒ 二者 yaml **已经是 `raw`** ⇒ 一致（**排除了「漏声明静默落到 off」在这两域发生过**）。

**本层实际做了什么（比派单描述窄）**：
- `GoStyleFormLimitMode`：判据改写 ＋ 新增 `SEMANTIC_REACHABILITY`（**10 个 api 域逐域取证表**，每行带 Go 侧证据）＋ `REGISTERED_DEVIATIONS`（登记 favorite）＋ 查询入口（未知域返回 `null`，不许瞎猜）。
- `GoStyleTomcatParameterLimitCustomizer` / `GoStyleFormValues`：把当判据用的「写权范围」措辞改成「语义可达性」，并点明 favorite 是**刻意保留**而非「已对齐」。
- 配套守门 6 条（`GoStyleFormLimitSemanticCriterionTest`）。

**🔴 建议项 6（🆕 · 落点 = `tools/difftest/**` = 共享面 ⇒ 整合者）**：
「**新增 `check-go-form-reachability.py`**：从 Go 真源逐域统计 `httpx.Parse` 命中数 ⇒ 与共享层 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 对照 ⇒ 让**取证书**与**判据表**不会各自漂移」。
⇒ 现状风险：共享层的表是**手工取证快照** ⇒ Go 侧哪天改了 handler，表不会自动跟着变 ⇒ ⇒ 与「写死数字不可复算」同类风险。
⇒ ⚠️ 另建议整合者把 `check-tomcat-limits.py` 的 `EXPECTED_FORM_LIMIT_MODE` 与共享层那张表**对齐**（现在两处各写一份 domain→mode ⇒ 会漂移）；⚠️ 该脚本当前把 favorite 列为「**禁止出现该属性**」（零触碰守卫）⇒ **那是对的**，别改。

#### FB-03 · A2 保护链的「链构成」只读入口

| 项 | 结论 |
|---|---|
| 1 现象 | 链构成由两个条件分支决定（`:65` shedding 需 `enabled && maxCpuPermille>0`；`:72` breaker 需 `enabled`）⇒ 域级守门无法**分别**断言 breaker / shedding ⇒ 只能「关掉降载换确定性」⇒ 断言被迫退化（名义上写 breaker/shedding，实际只证明了 breaker） |
| 2 Go 侧出处 | go-zero v1.10.2 `go-zero@v1.10.2/zrpc/server.go:157-159`（Breaker，默认 true）+ `:160-163`（Shedding，`CpuThreshold > 0` 才挂）⇒ Go 侧四个拦截器**无条件按序装上** |
| 3 RAG 侧现状 | `service/common/protection/GoStyleRpcProtectionChain.java:61-78` —— 链构成不可从外部查询 |
| 4 证据强度 | **L1**（两侧源码逐字对照） |
| 5 建议修法 | **Y = 「每一层是否在链上，可被域级守门直接断言」** ⇒ 共享层暴露**只读**的「链层构成」查询入口 ⇒ **零行为改动** |
| 6 影响面 | 共享层 `service/common/protection/*` ⇒ 被 9 个 rpc 域依赖 |
| 7 是否阻塞本波 | **否**（守门能力缺口） |

**本层实际做了什么**：
- `GoStyleRpcProtectionChain`：新增 `Layered`（**继承** `ServerInterceptor` ⇒ 匿名类可同时是「拦截器」+「带层名」；另立接口则匿名类**无法**实现）、`Composition` + `compose(props)`（**唯一**构成计算点，`build` 内部**消费**它 ⇒ 「配置推出的构成」与「实际构造出的链」不可能不一致）、`layerNames(chain)`（对**已构造出的链**的观测）。
- ⚠️ `build` 改为消费 `compose(props)` ⇒ **条件与顺序逐字不变** ⇒ 零行为改动。
- 配套守门 7 条（`GoStyleRpcProtectionChainCompositionTest`），含「四种开关组合下 compose() 与实际链**逐一吻合**」这条**单一来源**断言。

#### 三张单的**变异验证**记录（红测试必须红在目标断言上）

| 变异 | 结果 | 恢复后 |
|---|---|---|
| FB-01：撤掉归一层的自动配置注册 | **4 failures / 0 errors**（干净 `AssertionError`，红在 4 个路径变体那 4 条） | 全绿 |
| FB-02：把 `compose()` 与 `build()` 的条件拆成两份（制造不一致） | 由「compose 与实际链逐一吻合」那条断言抓住 | 全绿 |
| FB-03：摘掉降载层的 `Layered` 标记 | **5 failures / 0 errors**（干净 `AssertionError`，红在层名列表那 5 条） | 全绿 |

### 4.7 🆕 不需落码项（3 条）+ 一条**不重复派单**

| # | 事项 | 处置 | 落点 / 归属 |
|---|---|---|---|
| 1 | **A9 轴定义措辞缺陷**（轴定义应分层表述：共享层 `sea.*` 两侧一致；域特有按域：comment `audit.*`、favorite `biz.*`、admin `audit.*`） | **建议项 2**（§4.4）已登记 | `docs/DRIFT/_cross/domain-common-lanes.md` = 共享面 ⇒ 整合者订正 |
| 2 | **AGENTS.md §6.3 问题 4 漏记 `article-rpc`** | **建议项 3**（§4.4）已登记 | `AGENTS.md` = 共享面 ⇒ 整合者订正 |
| 3 | 🔴 **`DRIFT-HOTRPC-004` 的范围澄清** | **登记（防销错账）**：它闭的是 **`hot-api` 侧**，**不是 `hot-rpc` 侧** ⇒ ⇒ **别把它当「hot-rpc 侧已闭合」销掉**；另：权威面里「client breaker 是否真在链上」那条编号是 **`U4`**（`hot/api.md` §9 Unresolved 表），**不是** `DRIFT-HOTRPC-004`（那条是**另一条**：Redis 客户端重试） | 域台账（`docs/DRIFT/hot/**`）= 域侧 ⇒ 回执时已提醒对应 owner |
| 4 | **`E65`** | **不重复派单**：已是 global 台账的 OPEN 项 ⇒ 重复派会造出两个编号指同一件事 | 已在 global 台账 ⇒ 本 lane 不再登记 |

### 4.8 🆕 单 4（Jason 拍板）—— MyBatis-Plus 排序桥进**共享层主 jar**（核对日期 2026-10-27 · 基线 `align/g4-service-common` @ `a1a2e05`）

> **来源**：G3 的回执转达（Jason 拍板原方案 = **桥放主 jar + 各域删自己那份**）。

#### 4.8.1 🔴 本层先前的顾虑**不成立**（但**是我自己**的 grep 假阳性，不是对方的错）

| 项 | 结论 |
|---|---|
| 我先前的顾虑 | 「桥放主 jar ⇒ 会波及 16 个消费者的 MP 自动配置启用状态 ⇒ 是行为变更」 |
| **实测** | **有效 classpath 上有 mybatis-plus 的只有 9 个 rpc**（article / comment / favorite / follow / like / message / task / users-user / users-admin）；**其余 11 个**（10 个 api ＋ `hot-rpc`）**没有** ⇒ ⇒ 那 9 个**各自已有同向的域侧桥** ⇒ ⇒ 放主 jar 只是**多一个同向排序节点、无环、功能等价** ⇒ ⇒ **不改变任何消费者的启用状态** |
| ⚠️ **我错在哪** | 我用 `grep -rln "mybatis-plus" --include=pom.xml service/` 判定 ⇒ **命中了 `task-api/pom.xml` 里的注释文本**（「刻意**不**引入的：mybatis-plus / flyway —— task-api 不碰 DB」）⇒ ⇒ **把「注释里提到」当成了「依赖里有」** ⇒ ⇒ **假阳性** ⇒ ⇒ 权威口径是 `mvn dependency:list`（实测：task-api **无** mybatis）⇒ ⇒ 对方的实测是对的 |
| ⇒ 教训 | 「某依赖在不在 classpath 上」**必须**用 `mvn dependency:list` 判定；grep pom.xml 分不出「直接依赖 / `<exclusions>` 排除 / 注释提及」三种 ⇒ 与「javadoc 污染 grep 计数」「跨行数组注解单行正则必然 0 命中」同族 |

#### 4.8.2 本层落码：**用字符串形式** ⇒ 对「没有 MP」的消费者**结构性安全**

- 新增 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java`（**主 jar**）并登记进 `service/common` 的 `AutoConfiguration.imports`。
- 🔴 **关键取舍：排序注解写成 `afterName` / `beforeName`（字符串），不是类字面量**。
  Boot 4 的 `@AutoConfiguration` 有 `beforeName`/`afterName`
  （`@AliasFor(annotation = AutoConfigureBefore.class, attribute = "name")`）
  ⇒ ⇒ 类名只是**字符串** ⇒ ⇒ **目标类在不在 classpath 上都不影响本类的加载**
  ⇒ ⇒ 若写类字面量（域侧那 9 份的写法）⇒ 任何在**反射**层面读该注解的消费者
  ⇒ 抛 `TypeNotPresentException` ⇒ ⇒ **上下文直接起不来**。
  ⇒ ⇒ **本层这版比域侧那 9 份更稳**（域侧那 9 份能工作，只因为它们所在模块恰好都有 MP）。
- **顺带**：删掉单 1 探针原先自造的 **test scope** 那座桥（探针在 `service/common` 里、看不到域侧那 9 座，当时必须自造）
  ⇒ ⇒ 探针改为**复用主 jar 这座** ⇒ ⇒ **单 1 的守门从此直接验证共享层主 jar 的桥**（更强）。

#### 4.8.3 证据（两个方向都验）

| 场景 | 验证 | 结果 |
|---|---|---|
| **有 MP** | `MybatisPlusInterceptorChainGateTest`（真 PG + 真 mapper + 三层加码） | **3 / 3 绿** |
| **没有 MP** | article-api 的 `ArticleApiAssemblyWiringTest`（**真 Tomcat + 真应用上下文**；article-api classpath 上**没有** MP） | **13 / 13 绿** ⇒ ⇒ 桥在无 MP 消费者上**不炸上下文** |

#### 4.8.4 🔴 域侧协调项（**各域写权，本层不代删**）

- **9 个 rpc 组件各自的域侧桥**（例：`service/message/message-rpc/src/main/java/com/rag/message/message/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`）可删 ⇒ ⇒ 删后全仓只剩**共享层这一份**。
- ⚠️ **删除前置**：必须先让共享层这份（`a1a2e05`）**合进各域的树** ⇒ 否则删掉域侧那份 ⇒ 该域**彻底失去**排序桥 ⇒ MP 自动配置不生效。
- ⚠️ **共存期间无风险**：两份桥是**同向**排序边 ⇒ 排序图多一个同向节点、**无环、功能等价** ⇒ ⇒ 不改变任何行为 ⇒ ⇒ **删除不是「必须立刻」**，是「合并后清理」。
- ⚠️ **顺带发现（不在本层写权，也不在本单范围）**：`task-api` 的 pom 里**没有** MP（实测 `dependency:list`）⇒ ⇒ 它**本来就不需要**桥 ⇒ ⇒ 本单**没有**改变 task-api 的任何行为。

### 4.9 🆕 G1 的 7 张派单（2026-10-27）—— **逐条独立复核 + 本层处置**

> **来源**：G1 会话 `mvs_0aae96a903c14c8ea5f0fcc822f8df75`（2026-10-27 送单，声明「不需要额外确认回复，按 C2 自行排期落码 ⇒ 回执 4 项即可」）。
> ⚠️ **本节所有「前提成立与否」的判断都是本层独立复核的结论**，不是采信派单快照（AGENTS.md §3.3.1）。
> ⚠️ **MaxConns 那半边是已签字偏离** ⇒ 按 §5.3b **不重复质疑、也不「顺手修正回旧行为」**。

#### 4.9.1 总表：7 单逐条定性

| # | 派单条目 | 前提**成立**？ | 本层处置 | 落点（独立核准） |
|---|---|---|---|---|
| **1** | `DRIFT-USERAPI-013` / `DRIFT-FOLLOW-008` 同族：Go 每路由一个 latch + breaker 键用注册期模板（404 不得用 raw URI 兜底） | **半成立** | 🔴 **MaxConns 半边 = 已签字 `SD-2026-10-25-01` ⇒ 不修**；🟢 **breaker 键半边 = 真漂移 ⇒ 已落码** | `service/common/.../protection/GoStyleBreakerFilter.java` + 新增 `GoStyleRouteTemplateResolver` / `GoStyleRouteTemplateSource` / `GoStyleRouteResolution` / `GoStyleBreakerRouteTemplateAutoConfiguration` |
| **2** | `DRIFT-ARPC-010`（= **E66**）：Redis 写不绑 deadline | **成立** | 🟡 **本轮不落码 ⇒ 转立项**（路线已探明，但有一处**硬约束**，见 §4.9.4） | 共享层机制可行（零域侧改动），但落点涉及 `service/common` 的 lettuce 依赖 + 各域调用点语义 |
| **3** | `UNRESOLVED-ARPC-002`（= **E38②**）：有效期限 = min(客户端 deadline, 服务端 timeout) ⇒ `deadlineEpochMs` + 超时调度器延时两处都要改 | **❌ 前提已过期** | 🟢 **不落码**（① 第三十一轮已修；② 第四十六轮已否决原修法并窄化成 E65/E66） | 已在 `service/common/.../observability/RpcInterceptor.java`（`af416d9`） |
| **4** | `DRIFT-ADMINAPI-007`：503 响应 `Content-Type` charset 分号后空格 | **成立**（残差） | 🟢 **本轮不做 + 登记在案**（兄弟条 `DRIFT-MSGAPI-010` 用户已拍板「保持登记，本轮不做」⇒ 同类同先例 ⇒ **不升级为拍板题**） | 属 `admin-api` 域（`DRIFT-ADMINAPI-007`）⇒ 不在本层写权 |
| **5** | `UNRESOLVED-USERSCOMMON-003`：`GoStyleCoercion::applyTo` 的全局副作用 | **❌ 前提不成立** | 🟢 **不落码**（口径已过期；且已注册的 8 个 api **不会误伤**） | 台账口径订正属 **users 域台账写权** ⇒ 归 G1 |
| **6** | `DRIFT-USERRPC-V-002`：`users_user.status` NOT NULL 两侧不同 | **DDL 层成立 · 行为层零差异**（🔴 **2026-10-27 订正**，见 §4.9.7） | 🟢 **不落码 · 本层 DDL 保持 NOT NULL 不动**（放开可空**反而制造新漂移**：`callSettersOnNulls=false` ⇒ 读出 `null` ≠ Go 的 `0` ⇒ 方向相反）⇒ ⇒ **原「跨 lane 协调 + 两步方案」整体撤回**（前提「Go 读到 NULL 会扫描报错」已被 GORM 源码证伪）⇒ ⇒ **销号条件已满足**（G1 `ded22af` 已在其域台账逐处订正，本层确认成立） | DDL 落点 `service/users/users-common/src/main/resources/db/01_create_user.sql:39`（**本层写权**· **保持不动**）· 原配套 `service/users/admin/admin-rpc/.../domain/UserEntity.java:60`（**G1 写权**· **不必改**） |
| **7** | `DRIFT-USERAPI-014` 跨域推广：Redis 断连 ⇒ fail-open（不排队等重连）⇒ 机制上移到 `service/common` | **成立** | 🟢 **已落码**（零域侧改动 ⇒ 覆盖所有用 Redis 的组件） | 新增 `service/common/.../redis/GoStyleRedisClientAutoConfiguration.java` |

#### 4.9.2 🔴 #1 的 MaxConns 半边 = **已签字偏离 ⇒ 不修**（§5.3b）

| 项 | 结论 |
|---|---|
| 派单说法 | 「Go 每路由一个 MaxConns latch」⇒ 落点 `service/common/.../protection/GoStyleMaxConnsFilter.java` |
| **独立复核** | Go 侧确实每路由一个：`go-zero@v1.10.2/rest/engine.go:99-101`（`bindRoute` ⇒ `route` 是**逐路由参数**）→ `go-zero@v1.10.2/rest/engine.go:131-132`（`buildChainWithNativeMiddlewares` ⇒ 逐路由调 `handler.MaxConnsHandler`）→ `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12-20`（每次 `syncx.NewLimit(n)`） |
| **但** | 这条**已经签字**：`docs/Intentional_Deviations/global/common-tails.md:11-31` 的 **`SD-2026-10-25-01`**（2026-10-25 用户拍板，签字决定 = **保留 RAG 的全局闸门，不改成 per-route**）⇒ ⇒ **不修、不重复质疑、也不「顺手改回」** |
| ⚠️ 台账口径冲突（**共享面，本层不回写**） | `docs/DRIFT/_cross/domain-common-lanes.md:125` 仍把 `DRIFT-FOLLOW-008` 列为「🔴 未修」⇒ **已过期**（签字后应从 DRIFT 侧移除）⇒ ⇒ **登记给整合者**（该文件属共享面 ⇒ 本 lane 不写） |

#### 4.9.3 🟢 #1 的 breaker 键半边 = **真漂移 ⇒ 已落码**

**Go 出处（消费点，逐条核过）**：

| Go 事实 | 位置 | 对 RAG 的含义 |
|---|---|---|
| breaker 是**逐路由**建的 | `go-zero@v1.10.2/rest/engine.go:99-101` + `:131-132`（`handler.BreakerHandler(route.Method, route.Path, metrics)`） | 隔离单位 = 路由，不是具体 URI |
| breaker 的**名字** = `METHOD://route.Path` | `go-zero@v1.10.2/rest/handler/breakerhandler.go:15`（`breakerSeparator = "://"`）+ `:19`（`breaker.WithName(strings.Join([]string{method, path}, breakerSeparator))`） | 键必须是**注册期模板** |
| **404 不经过 breaker** | `go-zero@v1.10.2/rest/router/patrouter.go:59-78`（没匹配上 ⇒ `handleNotFound`）→ `go-zero@v1.10.2/rest/engine.go:244-268`（`notFoundHandler` 的链**只有 Trace + Log** ⇒ **没有 MaxConns / Breaker / Shedding**） | 未命中路由**不得**进 breaker |

**RAG 侧修前状态**：`GoStyleBreakerFilter.routeKey` = `request.getMethod() + "://" + request.getRequestURI()` ⇒ ① 每个**具体 URI** 一个 breaker ⇒ ② 404 也进 breaker。

**可观察面（按域实测，不是推断）**：

| 组件 | 有无路径变量 | 后果 |
|---|---|---|
| **article-api** | **有** —— `ArticleController.java:137,152,167,181`（`/article/{id}`、`/me/article/{id}` 等 4 条）；Go 侧 `service/article/api/internal/handler/routes.go:28,33,38,51` 是模板 `/article/:id` | 🔴 **可观察**：Go 上 `/article/1` 持续 5xx 会把同模板的 `/article/2` 一起挡住，RAG 修前不会 |
| 其余 9 个 api | **零**路径变量（`grep 'Mapping("[^"]*{'` 只命中 article-api 1 个文件） | ⇒ 模板 == 具体 URI ⇒ **修前修后无差别** ⇒ 爆炸半径变化**只发生在 article 域** |

**本层落码**（4 个文件，全部在 `service/common/**`）：

1. `protection/GoStyleRouteResolution.java`（新）—— 三态结果（`TEMPLATE` / `NOT_MATCHED` / `UNKNOWN`）。⚠️ **刻意不引用任何 Spring MVC 类型** ⇒ 因为它会被 Filter 引用 ⇒ 而**没有 spring-webmvc 的进程（10 个 rpc）加载不了 MVC 类型** ⇒ `NoClassDefFoundError`。
2. `protection/GoStyleRouteTemplateSource.java`（新）—— 窄接口（同样零 MVC 类型）⇒ Filter 只依赖「能解析模板」这件事，**不依赖实现**。
3. `protection/GoStyleRouteTemplateResolver.java`（新）—— 实现：用 **Spring 自己的** `HandlerMapping.getHandler(request)`（= DispatcherServlet 走的同一条匹配路径 ⇒ **不重新实现路由匹配**）⇒ 读 `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`。
4. `protection/GoStyleBreakerRouteTemplateAutoConfiguration.java`（新）—— `@AutoConfiguration` + `@ConditionalOnClass(HandlerMapping.class)` + `@ConditionalOnWebApplication(SERVLET)`。

**⚠️ 三个刻意的设计决定**：

| 决定 | 为什么 |
|---|---|
| **三态返回**（`TEMPLATE` / `NOT_MATCHED` / `UNKNOWN`） | `UNKNOWN`（没有 HandlerMapping / 调用抛异常 / 属性缺失）⇒ 调用方**回落旧行为**（raw URI 分桶、照常进 breaker）⇒ ⇒ **绝不因为「解析失败」而悄悄关掉熔断保护** |
| **不直接让 Filter 依赖 resolver 类型** | 无 MVC 的进程会在**注入点解析 Bean 类型**时加载那个类 ⇒ `NoClassDefFoundError` ⇒ **硬失败** ⇒ 故抽出零 MVC 类型的接口 |
| **`@ConditionalOnMissingBean` 去重** | 本层两个 `@AutoConfiguration` 位于 `com.rag.common.**` ⇒ **10 个 api 的 `scanBasePackages` 含 `com.rag.common`** ⇒ 同一个类会**既被组件扫描、又被自动配置清单**各注册一次 ⇒ 而两者的 `@Bean` 方法名**相同** ⇒ 不同名冲突 ⇒ **启动直接失败**（Boot 默认不允许 bean 覆盖）⇒ ⇒ 必须在 `@Bean` 上加 `@ConditionalOnMissingBean`（与既有 `GoStylePathNormalizingAutoConfiguration` 同一套办法） |

**依赖处置**：`service/common/pom.xml` 新增 **`spring-webmvc`（provided）** ⇒ **provided 不传递** ⇒ **16 个消费者的 classpath 零变化** ⇒ 只有本来就带 Spring MVC 的 api 进程会用上。

#### 4.9.4 🟡 #2（E66）—— **本轮不落码 ⇒ 提请立项**（路线已探明 + 一处硬约束）

**路线（已探明，可行）**：`io.lettuce.core.TimeoutOptions.TimeoutSource` 是**抽象类**，
`public abstract long getTimeout(RedisCommand<?,?,?>)` ⇒ **逐命令动态求值** ⇒
⇒ 共享层可以返回一个「当前请求的剩余 deadline」⇒ **Redis 命令被 deadline 约束** ⇒ **零域侧改动**。

**🆕 2026-10-27 补充约束（G2 提供 · 本层独立核过）—— rpc 侧**已有现成载体**，但**读的不是同一个东西**：

| 项 | 事实（本层实测） | 对 E66 落码的影响 |
|---|---|---|
| api 侧的载体 | `GoStyleRequestDeadline.begin(deadlineNanos)` **只在** `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:324` 绑定（`:356` clear）⇒ ⇒ **api 侧独占** | ⇒ ⇒ **rpc 侧用不了它** |
| rpc 侧的载体 | `RpcInterceptor` 的 `DeadlineState`：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:461`（worker 线程里 `DeadlineState.bindToCurrent(deadlineEpochMs)`）⇒ ⇒ **已经在 handler 线程绑好了**（`:215-236` 的 javadoc 已写明「业务线程可随时读 `remainingMillis()`」） | ⇒ ⇒ **E66 的 `TimeoutSource` 若在 rpc 侧落地 ⇒ 读的是 `DeadlineState.remainingMillis()`，不是 `GoStyleRequestDeadline`** ⇒ ⇒ **两个域面要接两个不同的载体** ⇒ ⇒ **落码时不能只实现 api 侧** |
| ⇒ ⇒ **结论** | ⇒ ⇒ **rpc 侧不需要新增机制**（载体已在）⇒ ⇒ ⇒ **但 `TimeoutSource` 的取值函数必须同时认这两个来源** | ⇒ ⇒ **E66 的实现清单要多一个分支**（不是「只改 api 侧」） |
| ⚠️ **边界（G2 已划清，本层同意）** | like/task 的 **MQ / outbox / sink 路径不属 E66 域** —— Go 侧那些 ctx 是 **Kafka 消费 ctx**（`service/task/rpc/internal/sink/like_sink.go:110-128` 的 `loop(ctx)`）⇒ 本来就没有请求 deadline ⇒ ⇒ **两侧同形 ⇒ 不修**（避免造出「比 Go 更严」的新漂移） | ⇒ ⇒ **不扩到 MQ 面** |
| **证据强度** | G2 域侧逐条核好的调用点清单已登记（like-rpc **11** 处 · task-rpc **3** 处）⇒ ⇒ **L1（源码级）** ⇒ ⇒ **不主张 L4** | ⇒ ⇒ 与 E66 现有强度同此 |

**🔴 但有一处硬约束（本轮实测字节码得出，**改法必须绕开它**）**：
Boot 4 的 `LettuceConnectionConfiguration.createClientOptions` 里的**顺序**是：

```
offset 146: ClientOptions$Builder.timeoutOptions(TimeoutOptions)      ← 先写「命令超时」
offset 151: ObjectProvider.orderedStream()                            ← 后应用 customizer
```

（`spring-boot-data-redis-4.0.8.jar` ⇒ `javap -c` 实测；timeout 来源 = `DataRedisProperties.getTimeout()`，
即 `spring.data.redis.timeout`。）⇒ ⇒ **customizer 里再调一次 `timeoutOptions(...)` 会整体覆盖运维配的命令超时** ⇒ ⇒ **那是新漂移** ⇒ ⇒ 实现时必须**先读回 `spring.data.redis.timeout`** 再包一层 source（无 deadline 时回落到该值）⇒ ⇒ 需要一轮专门实现。

**另两处待定（立项时要一起答）**：
1. **载体不统一**：rpc 路径用 `RpcInterceptor.DeadlineState`（epoch 毫秒，静态 `remainingMillis()`）· api 路径用 `GoStyleRequestDeadline`（`nanoTime` 纳秒）⇒ ⇒ 要么统一，要么 source 两处都读。
2. **无 deadline 时的回落值**：Go 侧无 ctx deadline 的 Redis 调用由 go-zero 自己的 `Timeout` 决定；RAG 侧今天靠 `spring.data.redis.timeout` ⇒ ⇒ 回落值必须**等于现状**，否则又是新漂移。

**为什么本轮不硬落**：E66 在 global 台账里本来的定性就是「**落点跨域 ⇒ 只记账 + 提请立项，实施需各域 owner 参与**」
（`docs/DRIFT/global/_README.md` §七之二十）⇒ ⇒ 且它还缺**双侧活体证据**（E66 现有证据强度 = 源码级，**未经双侧活体验证**）
⇒ ⇒ 按 AGENTS.md §3.3「凡写『与 Go 一致』必须有两侧同时跑的探针」，**先把机制落了但没有双侧证据**＝ 半个闭环 ⇒ ⇒ 本轮**只提交机制可行性结论 + 硬约束**。

#### 4.9.5 🟢 #3 = **前提已过期 ⇒ 不落码**（`af416d9` 已把两处都改好）

| 项 | 证据 |
|---|---|
| 派单要求 | 「`deadlineEpochMs` 计算 + 超时调度器延时**两处都要改**」 |
| **实际状态** | 🔴 **两处都已在共享层改好**（第三十一轮，`af416d9`）⇒ `git merge-base --is-ancestor af416d9 HEAD` ⇒ **YES**（是本分支祖先） |
| 逐行位置 | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:234`（`effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）· `:235-237`（`deadlineEpochMs`）· `:263`（超时调度器延时用 `effectiveTimeoutMs`）· `:592`/`:595`（`clientDeadlineRemainingMs()` 取 `io.grpc.Context.current().getDeadline()`） |
| ② 架构级那半边 | 🔴 **第四十六轮已否决原修法**（「handler 挪受控 executor + 超时 interrupt」）并把第③层窄化成 **E65（出向 gRPC 不继承 deadline）** + **E66（Redis 不感知 deadline）** ⇒ ⇒ **不在本单范围** |
| ⇒ 结论 | **不需要落码** ⇒ 回执里给出上面 5 个行号 + 提交号即可 |

#### 4.9.6 🟢 #5 = **前提不成立 ⇒ 不落码**（口径已过期，且已注册的 8 个 api 不会误伤）

**派单的前提**：「通用版 `GoStyleCoercion::applyTo` 被 user-api / admin-api / **favorite-api** 三域注册 ⇒ 全局副作用是否波及非 users 域」。

**独立复核（实测）**：

| 项 | 实测 |
|---|---|
| 派单说的「三域」 | ❌ **不成立** ⇒ 实测**已注册 8 个 api**：article / comment / favorite / follow / like / message ＋ users 的 user-api / admin-api |
| 台账口径「通过 `JsonMapperBuilderCustomizer` **全局施加**」 | ❌ **已过期** ⇒ `GoStyleCoercion` 自身 javadoc `:96-103` 明写「**刻意不做 `@Configuration`**」（users-common 也被纯 gRPC 的 admin-rpc 用着）⇒ 改由**各 api 各自注册** |
| **未注册**的两个 api 是否漏了 | 🟢 **都不是漏**：**hot-api** ⇒ **零 `@RequestBody`**（规则不适用）；**task-api** ⇒ 有 `@RequestBody GetTaskReq`，但 Go 侧该类型带**自定义 `UnmarshalJSON`** ⇒ Go 侧**也不走**标准 mapping 规则 ⇒ RAG 已在 DTO 层手写镜像（`GetTaskReq.setId(Object)` / `normalize` / `numberLiteral`，对应 `service/task/api/internal/types/types.go:16-36`）⇒ ⇒ **规则不适用** |
| **会不会误伤**（真正的问点） | 🟢 **不会** ⇒ **Go 全仓只有 1 个自定义 `UnmarshalJSON`**（`service/task/api/internal/types/types.go:16`）⇒ ⇒ 凡注册了全局规则的 api，其 Go 侧请求类型**都走标准 mapping** ⇒ ⇒ 规则与 Go 逐条一致 |
| ⇒ 结论 | **无漂移，不落码**；台账那条的**问法**（「全局副作用」）需订正 ⇒ **该文件属 users 域台账写权 ⇒ 归 G1 改** |

#### 4.9.7 🔴 #6 = **真漂移，但本层不能单独改 ⇒ 跨 lane 协调**

| 项 | 证据 |
|---|---|
| 漂移成立 | Go：`service/user/user/rpc/internal/model/data_model.go:11` = `Status int64 \`gorm:"column:status;default:0"\`` ⇒ GORM **未标 `not null`** ⇒ 建出**可空**列；RAG：`service/users/users-common/src/main/resources/db/01_create_user.sql:39` = `status BIGINT NOT NULL DEFAULT 0` ⇒ 🔴 **可空性不同**（真库实证见 `docs/DRIFT/users/user/rpc.md:1518`） |
| **为什么不能单独改** | 🔴 `docs/DRIFT/users/user/rpc.md:1559` 明写：「**动它会立刻激活 admin-rpc 的同款缺陷**」—— `service/users/admin/admin-rpc/.../domain/UserEntity.java:60` 是 `private Long status = 0L;` ＋ MyBatis `callSettersOnNulls=false` ⇒ **NULL 读成 0** ⇒ admin 侧会把 NULL 当「活跃」⇒ ⇒ **必须与 admin 侧一起改** |
| **写权归属** | DDL（`service/users/users-common/**`）= **本层写权**；`admin-rpc` 的 `UserEntity` = **G1 写权** ⇒ ⇒ **跨 lane** |
| 🔴🔴 **2026-10-27 订正：上一行的「必须与 admin 侧同改」前提被证伪 ⇒ 两步方案整体撤回** | G1 回执（提交 `ded22af`）给出逐层证据链，**本层已独立复核成立**：<br>① `gorm.io/gorm` `schema/field.go`（真源 `go.mod` pin 的 **v1.31.1**；v1.31.2 同一段代码**逐字相同**）的 `case reflect.Int, …, reflect.Int64:` 分支里：`case **int64: if data != nil && *data != nil { field.ReflectValueOf(ctx, value).SetInt(**data) }` ⇒ ⇒ **DB 值是 NULL ⇒ `*data == nil` ⇒ 什么都不做 ⇒ 字段保持 Go 零值 0 ⇒ 不报错**（本层读源码核实）；<br>② `database/sql/convert.go` 的 `case reflect.Pointer: if src == nil { dv.SetZero(); return nil }` ⇒ **dest 的 kind 是 Pointer** ⇒ 走这一支 ⇒ **不报错**；<br>③ 双侧活体对拍（G1 域台账 `docs/DRIFT/users/user/rpc.md`「2026-10-23 第三轮」）：`status IS NULL` 打 Login ⇒ **Go 200 + token** ⇒ ⇒ **「删掉 = 0L」才是不等价的那一侧**，上午那次「删 = 0L + 加 null ⇒ INTERNAL」是**误修**，当天已恢复。<br>⇒ ⇒ **本层原来的那句「Go 侧读到 NULL status 会扫描报错（`converting NULL to int64 is unsupported`）= 事实错误** ⇒ ⇒ **「保留 NOT NULL」与「放开可空」在行为层同形 ⇒ ⇒ 那条裁决题不存在 ⇒ ⇒ 「需 Jason 拍板」撤销** |
| ⇒ ⇒ **正确的结论（与原两步方案相反）** | **行为层零差异**：Go「可空 + 读成 **0**」vs RAG「**不可空** ⇒ 读不到 NULL ⇒ 也是 **0**」⇒ ⇒ **可观察结果相同** ⇒ ⇒ **保持 RAG 的 NOT NULL 就是等价的那一侧**（RAG 更严：写 NULL 被 PG 拒；Go 侧写得进去但读出来还是 0 ⇒ 行为同形）⇒ ⇒ **「放开可空」反而会制造新漂移**：RAG 侧 MyBatis `callSettersOnNulls=false` ⇒ NULL 时**跳过 setter** ⇒ 若字段没有 `= 0L` 初始值 ⇒ 读出 **null** ≠ Go 的 **0**；且 admin 侧会从「NULL 当活跃(0)」变成「NULL 当未知(null)」⇒ ⇒ **方向相反** ⇒ ⇒ ⇒ **第 1 步（改 `admin-rpc` 的 `UserEntity.status`）不执行；第 2 步（放开 DDL 的 NOT NULL）也不执行 ⇒ ⇒ 两步方案整体撤回** ⇒ ⇒ **本层 DDL 保持 `status BIGINT NOT NULL DEFAULT 0` 不动**（DDL 层差异**如实保留** ⇒ 归「**DDL 层差异 · 行为层零差异**」）⇒ ⇒ **本层零落码** |
| ⇒ ⇒ **销号条件已满足** | G1 已在其域台账（`users/user/rpc.md` 的 V-002 条 ＋ `users/_shared.md` 的 ADJ-01 ＋ `users/vs-common.md` 的 A8 轴）逐处订正（提交 `ded22af`，**纯台账零代码**）⇒ ⇒ **本层已确认该订正成立** ⇒ ⇒ **V-002 / ADJ-01 / A8 那一格 ⇒ 可销号**（本层无遗留落码动作） |

#### 4.9.8 🟢 #4 = **本轮不做 + 登记在案**（不升级为拍板题）

- 事实：`DRIFT-ADMINAPI-007` = 503 响应的 `Content-Type` 应为 `text/plain; charset=utf-8`（分号后**有**空格）；`nosniff` 那半边**已修**，**只剩 charset 空格残差**。
- **先例**：兄弟条 `DRIFT-MSGAPI-010`（message-api 同款）用户已拍板「**保持登记，本轮不做**」⇒ ⇒ **同类同先例** ⇒ 本轮**不做 + 登记在案** ⇒ **不把登记类事项升级成「请你拍板」**（用户偏好）。
- 落点：`admin-api` 域 ⇒ **不在本层写权** ⇒ 也不由本层代改。

#### 4.9.9 🟢 #7 = **已落码**（零域侧改动 ⇒ 覆盖所有用 Redis 的组件）

**Go 出处（消费点）**：

| Go 事实 | 位置 |
|---|---|
| 黑名单查询失败 ⇒ **放行**（fail-open） | `service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35-36`（`if err == nil && exists` ⇒ **err != nil 不拦截**）· 同款另见 `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go` 与 `service/follow/api/internal/middleware/checkblacklistmiddleware.go`（⚠️ **三份文件 md5 各不相同**，但「err != nil ⇒ 放行」三条一致） |
| Redis 客户端**快速失败** | `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:14`（`maxRetries = 3`）· `:32-40`（`red.NewClient(&red.Options{Addr, MaxRetries: maxRetries, MinIdleConns: idleConns})` ⇒ **未设** `DialTimeout`/`ReadTimeout` ⇒ 用 go-redis v9 默认值 ⇒ 连接被拒时**立即失败** ⇒ 实测 ~1.7s） |

**本层落码**：新增 `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java`
⇒ 注册 `LettuceClientOptionsBuilderCustomizer` ⇒ `builder.disconnectedBehavior(REJECT_COMMANDS)`
⇒ **零域侧改动**即让**所有**用 Redis 的组件拿到 Go 的失败语义：

| 组件 | 修前 | 修后 |
|---|---|---|
| user-api | ✅ 已有域内配置（2026-10-24 第九轮修 `DRIFT-USERAPI-014`） | 不变 |
| **follow-api** | 🔴 断连 ⇒ 命令**挂起**等重连 ⇒ 撞超时预算 ⇒ **503**（Go 是 **200**） | ✅ 断连即拒单 ⇒ 快速失败 ⇒ fail-open |
| **favorite-api** | 🔴 同上 | ✅ 同上 |
| comment / like / hot / task 的 **rpc** | 🔴 缓存读写断连时挂起（Go 侧 `ExistsCtx`/`ZRevRangeWithScores`/`HGetAll` 全 ctx-aware ⇒ 快速失败） | ✅ 同上 |

**⚠️ 两个刻意的决定**：

1. **不碰 `timeoutOptions`** ⇒ 见 §4.9.4 的字节码证据 ⇒ 只改断连行为 ⇒ **运维配的 `spring.data.redis.timeout` 原样保留**。
2. **与 users 域既有那份并存 = 幂等** ⇒ 两份都设 `REJECT_COMMANDS` ⇒ Boot 会依次应用所有 customizer ⇒ **后覆盖前、值相同 ⇒ 结果相同** ⇒ ⇒ users 域那份可**合并后单独删除**（纯删除、零行为变化）⇒ **删除前置 = 共享层这份先合进各域的树**。

**依赖处置**：`service/common/pom.xml` 新增 **`lettuce-core` + `spring-boot-data-redis`（均 provided）** ⇒ **不传递** ⇒ 16 个消费者的 classpath 零变化 ⇒ 没有 Redis 的进程由 `@ConditionalOnClass` 挡住。

#### 4.9.10 本波落码清单 + 验证（核对日期 2026-10-27 · 基线 `align/g4-service-common` @ `138b6ef`）

| 工单 | 提交 | 落码文件（全部在 `service/common/**`） |
|---|---|---|
| **#7** | `1fb26b2` | `main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java`（新）· `src/test/java/com/rag/common/redis/GoStyleRedisClientOptionsGateTest.java`（新）· `pom.xml`（+`lettuce-core` / `spring-boot-data-redis`，均 provided）· `AutoConfiguration.imports`（+1 行） |
| **#1** | `138b6ef` | `main/java/com/rag/common/protection/GoStyleRouteResolution.java`（新）· `GoStyleRouteTemplateSource.java`（新）· `GoStyleRouteTemplateResolver.java`（新）· `GoStyleBreakerRouteTemplateAutoConfiguration.java`（新）· `GoStyleBreakerFilter.java`（改）· `src/test/java/com/rag/common/protection/GoStyleBreakerRouteTemplateGateTest.java`（新）· `pom.xml`（+`spring-webmvc`，provided）· `AutoConfiguration.imports`（+1 行） |

**守门（新增 6 条）+ 变异（3 条全精确命中）**：

| 守门 | 断言 | 变异 | 变异结果 |
|---|---|---|---|
| `GoStyleBreakerRouteTemplateGateTest#sameTemplateSharesOneBreaker` | 同模板的两个具体 URI **共用一个** breaker（真 Tomcat + 真 Spring MVC + 真 `@GetMapping("/x/{id}")`） | 键退回 raw `getRequestURI()` | 🔴 **1 failures**，红在**目标断言 ①**（另两条仍绿 ⇒ 精确命中） |
| `GoStyleBreakerRouteTemplateGateTest#unmatchedRouteDoesNotCreateBreaker` | 404 **不得**创建 breaker | 去掉「未命中跳过」分支 | 🔴 **1 failures**，红在**目标断言 ②** |
| `GoStyleBreakerRouteTemplateGateTest#everyRegisteredRouteResolvesToTemplate` | **每条**已注册路由都必须能解析出模板（防「解析失败 ⇒ 静默失去熔断保护」） | —— | —— |
| `GoStyleRedisClientOptionsGateTest#disconnectedBehaviorIsRejectCommands` | `ClientOptions.getDisconnectedBehavior() == REJECT_COMMANDS` | 退回 `DEFAULT` | 🔴 **1 failures**，红在**目标断言 ①** |
| `GoStyleRedisClientOptionsGateTest#customizerIsRegisteredExactlyOnce` | customizer **有且只有一份**（单一来源） | —— | —— |
| `GoStyleRedisClientOptionsGateTest#commandTimeoutIsNotClobbered` | `timeoutOptions` **未被**本层改动（运维配的 `spring.data.redis.timeout` 要保住） | —— | —— |

**回归口径**：

| 范围 | 结果 |
|---|---|
| `service/common` 模块 | **298 条 / 0 failures / 0 errors**（修前 292 ⇒ **净增 6**）· BUILD SUCCESS |
| 三门禁 | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **全量 `mvn -B clean verify`** | ✅ **BUILD SUCCESS / EXIT=0** · **52 个模块** · **2448 条**（基线 2442 + 本波新增 6）· **failures 0 / errors 0 / skipped 4** · **23:04 min** · 基线 `align/g4-service-common` @ `fc783fb`（代码态 `63079ed`）<br>⚠️ **中间跑过 3 次非绿，原因逐条查清并登记**：① 第三次（`138bac3`）= comment-rpc 一条**既有时序 flake**（见 §4.9.12）⇒ 单独复跑通过；② 第四次 = **EXIT=137（被系统 SIGKILL）** ⇒ 根因是**另一个 lane 同时跑 maven** ⇒ **不是代码问题**（见 §4.9.13）⇒ ⇒ 第五次独占重跑 ⇒ **全绿** |
| **写权零越权** | 本波改动文件**全部**在 `service/common/**` ＋ 本文件 ⇒ **未触碰** `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/DRIFT/global/_README.md` / `docs/Worktree.md` / `tools/difftest/**` |

#### 4.9.11 🔴 **需回写给整合者 / G1 的共享面口径订正**（本 lane 不写那些文件）

| 文件 | 需订正的内容 | 订正方向 |
|---|---|---|
| `docs/DRIFT/_cross/domain-common-lanes.md:125` | `DRIFT-FOLLOW-008` 仍列为「🔴 **未修**」 | ⬆️ **已过期** ⇒ 2026-10-25 用户签字（`SD-2026-10-25-01`）后应从 DRIFT 侧移除 |
| `docs/DRIFT/users/common.md` §6 | `UNRESOLVED-USERSCOMMON-003` 的问法（「**全局副作用**是否波及非 users 域」） | ⬆️ **前提已过期**（不是全局施加，是各 api 各自注册）⇒ 且实测**已注册的 8 个 api 不会误伤** ⇒ 可直接结案（**该文件属 users 域台账写权 ⇒ 归 G1 改**） |
| `docs/DRIFT/users/user/rpc.md` §`DRIFT-USERRPC-V-002` | 「🔴 仍成立，但被卡」 | 🔴 **2026-10-27 订正**：**「卡点」不存在** ⇒ ⇒ **DDL 层差异成立 · 行为层零差异**（Go 读 NULL ⇒ GORM 跳过 setter ⇒ 得 **0**；RAG NOT NULL ⇒ 读不到 NULL ⇒ 也是 **0**）⇒ ⇒ ⇒ **「保持 NOT NULL」就是等价的那一侧** ⇒ ⇒ **原两步方案与「需 Jason 拍板」那条裁决题一并撤销**（详见 §4.9.7 的逐层证据链）⇒ ⇒ **可销号** |

#### 4.9.12 🟡 全量回归里撞到一条**既有**时序 flake（**不是本波引入** · 属 comment 域写权 ⇒ 已通知 G3）

**现象**（第三次全量回归 · 基线 `138b6ef`）：**2448 条 / failures=1 / errors=0 / skipped=4** ⇒ 唯一失败：

| 项 | 内容 |
|---|---|
| 用例 | `service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcRuntimeBootGateTest#realFatJarProcessBootsAndListens` |
| 失败断言 | 「**日志里没有 devh 的『gRPC Server started』**」 |
| 🔴 **同一用例的另一个断言已通过** | 「**真 fat jar 起不来（gRPC 端口没有在监听）**」⇒ **端口确实在监听** ⇒ 进程**真的起来了** |
| 🔴 **classpath 也没问题** | 同用例第三条 `assertFalse(output.contains("NoClassDefFoundError"))` **通过** ⇒ ⇒ **本波新增的两个自动配置在「无 spring-webmvc 的进程」里没有炸**（这恰好是本次最该验的那条风险） |
| 根因形态 | 该用例在**端口探活成功就立刻 `process.destroyForcibly()`**（源码 `:107-119`）⇒ 日志 drain 线程（`:103-105`）可能还没读到那行 ⇒ **断言与日志读取存在竞态** |
| 单独复跑 | ✅ **通过**（实测：`mvn -o -pl service/comment/comment-rpc -am test -Dtest=CommentRpcRuntimeBootGateTest` ⇒ 1/1 绿） |
| 前两次全量 | ✅ 同样用例在基线 `433eac3` 的两次全量（各 2442 条 / 0 failures）**都过** ⇒ ⇒ **不是本波引入** |
| ⇒ 处置 | **该文件属 comment 域写权 ⇒ 本 lane 不代改** ⇒ 已按「共享层变更通知」把 flake 形态与建议修法发给 **G3**（不催、不代改）；本条登记在案 |

⚠️ **为什么这条值得单列**：它同时是**一次意外收获** —— 本波给 `service/common` 的
`AutoConfiguration.imports` 加了 2 行（其中 1 行引用 **只有 api 进程才有**的 `spring-webmvc` 类型）
⇒ 而 comment-rpc 是**纯 gRPC 进程**（没有 spring-webmvc）⇒ ⇒ 「自动配置清单里出现一个
本进程没有的类，会不会 `NoClassDefFoundError`」这个风险 ⇒ **由这条用例的真 fat jar 启动实测排除了**
（`@ConditionalOnClass` 的 ASM 元数据判定在 Boot 4 上确实安全）。

#### 4.9.13 🟡 **大回归错峰不是形式主义**：并发跑全量会被系统 **OOM-kill**（实测 EXIT=137）

| 项 | 实测 |
|---|---|
| 现象 | 第四次全量回归 **EXIT=137**（SIGKILL）⇒ 死在 **article-rpc** 的测试阶段 ⇒ **失败前 0 条断言失败** |
| 🔴 **根因** | **另一个 lane 同时在跑 maven**（G1 的 `g1-users-follow` 工作树，05:23 起跑 `*RedisConnectTimeoutSocketTest`）⇒ 两个回归同时抢 CPU/内存 ⇒ **article-rpc 的测试 JVM 被 macOS OOM-kill** |
| 排除「是我的代码」 | ① article-rpc 在**前三次**全量里都过（2442/2442/2448 条）；② 本波改动与 article-rpc **无交集**（它不扫 `com.rag.common`、无 Redis、无 spring-webmvc ⇒ 我的两个自动配置对它全部惰性）；③ 失败前 **0 条断言失败**；④ 该模块单独复跑通过 |
| 环境证据 | 机器 24 GB；`docker stats` 显示多个常驻栈（kafka ~980 MiB / nacos ~964 MiB / …）⇒ 基线占用已经不低 ⇒ 再叠两个并发回归就过线 |
| ⇒ 处置 | 等对方跑完再重跑（**第五次全量 ⇒ 见下表**）⇒ 并已按 AGENTS.md §4.1「**大回归错峰：开始前 session send 知会对方**」**知会 G1/G2/G3** |
| ⇒ 教训 | 「大回归错峰」这条约定**有真实的失败形态**（不是「慢一点」，是**被杀**）⇒ ⇒ **并发跑全量会被 SIGKILL，而不是报错** ⇒ ⇒ 排查全量回归红/中断时，**第一件事是看是不是被别人挤死的**（`EXIT=137` / `EXIT=143`），而不是先怀疑代码 |

#### 4.9.14 🟢 **G1 的 #3 订正**（状态描述撤回）＋ 顺手订正 `tools/semantic-probe/README.md` 的过期数字

**① #3 的订正 —— 与本层先前的独立复核**结论一致** ⇒ #3 仍**不落码****

| 项 | 内容 |
|---|---|
| G1 撤回的说法 | 「RAG 侧 `deadlineEpochMs = now + timeout.toMillis()` ⇒ 只算服务端自己的 2s ⇒ 客户端 deadline 完全不参与」⇒ **过期快照**，G1 已主动撤回 |
| 本层先前的独立复核（落码前做的） | 同结论：① 第三十一轮已修（`af416d9`，是我分支祖先）⇒ `RpcInterceptor.java:234`（`effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）· `:235-237`（`deadlineEpochMs`）· `:263`（超时调度器延时）· `:592`/`:595`（取 `io.grpc.Context.current().getDeadline()`）；② 架构级第四十六轮已否决原修法 ⇒ 窄化成 E65/E66 |
| **本层自己复跑双侧探针**（不采信转述） | `bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0** ⇒ `client_deadline_ctx` **一致** ⇒ **E38② 的第 ① 层确认已闭环**；另 1 条 `timeout_telemetry` 是**已签字的表述差异**（C-1，驼峰 vs 全大写）⇒ 不计入失败 |
| ⇒ 结论 | **#3 不落码**（我没动 `RpcInterceptor`）⇒ 「① 已闭环 / ② 未做且已另立项」这条表述**要写进各域台账**（AGENTS.md 与域台账里「E38② 未闭合 / ① 不销本条」的措辞**过期**） |
| ⚠️ 采纳 G1 的元教训 | 「**落码任何一条前，先跑一遍对应探针复核状态**，别照抄台账」⇒ 本层本轮 7 单里，**#3 已按此复跑探针**；**其余 6 单的落点与状态判定也都不是照抄台账**（逐条读 Go 消费点 + 逐域实测影响面） |

**② `tools/semantic-probe/README.md` 的过期数字 —— 已订正**（G1 报备 ⇒ G4 落码）

| 项 | 内容 |
|---|---|
| 过期内容 | 「当前 **23/23** 一致」「不过滤计数会变成 **24 vs 23**」「## 用例清单（**23 例**）」⇒ 而脚本实跑是 **56 例** |
| 本层独立核实 | `bash tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0**，末行 **「总计 56 例：一致 54，不一致 0，已签字偏离 2」** ⇒ ⇒ 旧 23 例 = 原有 6 组（6+6+2+3+2+4）⇒ 新增 5 组 = A(7)+B(10)+C(8)+D(6)+X(2)=33 ⇒ **23+33 = 56** ✔ |
| 落码 | `2231398`（**纯文档 ⇒ 零字节码变化 ⇒ 全量回归结果不受影响**）⇒ 三处数字改正 + **补齐 A/B/C/D/X 五组**（内容逐条取自脚本实跑输出 ⇒ 可复算）+ 新增「**怎么复核这个数字**」小节（写明这四个数是脚本实跑出来的、文档与脚本不一致时以脚本为准） |
| 顺带查清一件 | **A6 在探针源码（`go/` 与 `java/` 两半）里根本不存在** ⇒ 是**编号留坑**，**不是**有用例被静默丢掉 ⇒ 已写进 README（免得后人以为漏了） |
| 写权 | `tools/semantic-probe/README.md` **不在**整合者专属共享面清单里（那份只有 `tools/difftest/README.md`）⇒ 且本目录是**共享层的双侧探针** ⇒ 归 G4；三门禁复跑 ⇒ **EXIT=0/0/0** |

### 4.10 🆕 本轮（2026-10-27 · G4 第 6 轮）—— G1 #8 / G3 单 3 / G2 澄清核对 / Redis 收编

#### 4.10.1 🔴 G1 派单 #8（`PITFALL-FOLLOW-005`）—— **已落码**：关闭路径不得触发 web 类型的类加载

**现象**：纯 gRPC 进程关闭时抛 `java.lang.NoClassDefFoundError: jakarta/servlet/Filter`
⇒ ① 污染关闭日志；② 因关闭钩子**不吞异常** ⇒ **后面的 `RpcInterceptor.shutdown()` 根本不执行** ⇒ RPC 池没被回收。

**根因链（4 条，逐条有证据，非源码推理）**：

| # | 事实 | 证据 |
|---|---|---|
| ① | `HttpMiddleware` 的**方法签名**就引用了 `jakarta.servlet.Filter`，且方法体里有 `new Filter() { … }`（**匿名类**）⇒ **类被加载时**，字节码验证器就要确认「这个匿名类是 `Filter` 的实现」⇒ 必须先加载 `Filter` | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:8`（import）· `:231`（返回类型）· `:236`（匿名类） |
| ② | 纯 gRPC 进程的**运行时 classpath 上没有 servlet-api** | `mvn -o -pl <rpc> dependency:list` ⇒ 只有 `spring-web:7.0.9` + `tomcat-embed-el`，**无** `jakarta.servlet-api` / `tomcat-embed-core`（like-rpc / user-rpc / hot-rpc 三个逐一核过） |
| ③ | rpc 进程**不扫** `com.rag.common` ⇒ `HttpMiddleware` **从不加载** ⇒ 它的**首次加载恰好落在** `@PreDestroy` 这条关闭路径上 ⇒ 炸在关停瞬间 | 10 个 rpc 的 `scanBasePackages` 均**不含** `com.rag.common`（逐个核） |
| ④ | **受影响面 = 9 个 rpc**（`hot-rpc` 不受影响） | 剥注释后解析 `@Import({ … })` 块 ⇒ article / comment / favorite / follow / like / message / task / users-user / users-admin **9 个**都点名了 `ObservabilityLifecycle`；**hot-rpc 没有** ⇒ N/A |

**修法（零域侧改动）**：

| 件 | 内容 |
|---|---|
| `ObservabilityLifecycle` | 只关 **RPC** 池（`RpcInterceptor` 零 servlet / 零 `HttpMiddleware` 引用，已核其 import 面）⇒ 关闭路径**不再碰任何 web 类型** |
| **新增** `HttpPoolLifecycle` | `@Component` + `@ConditionalOnWebApplication(type = SERVLET)` ⇒ 只在 **servlet 进程**注册 ⇒ 由它关 HTTP 池 |
| api 侧 | 10 个 api **全部扫** `com.rag.common`（逐一核）⇒ `HttpPoolLifecycle` **照旧注册** ⇒ HTTP 池**仍被回收** ⇒ **行为不变** |
| rpc 侧 | HTTP 池**从未被创建过**（`HTTP_EXECUTOR` 是 `HttpMiddleware` 的 static 字段，而该类在 rpc 进程里从不加载）⇒ 本来就没什么可关 ⇒ **语义等价** |
| ⚠️ 红线 | **`ObservabilityLifecycle` 不可改名 / 不可移除** —— 9 个 rpc 组件在 `@Import` 里点名了它 ⇒ 改名要动 9 个**域侧**文件 ⇒ **越本层写权** ⇒ 只能改其内部 |

**守门（新增 3 条）**：`HttpPoolShutdownWithoutServletGateTest`
① **剔掉 servlet-api 的 classpath 上起子 JVM 跑关闭钩子** ⇒ 必须干净返回
（子进程探针 `HttpShutdownProbeWithoutServlet`；**同一个 JVM 无法卸载已加载的类** ⇒ 只能新起一个 JVM 复刻「纯 gRPC 进程」的 classpath 形态）；
② `ObservabilityLifecycle` 的**常量池**里不得出现 `HttpMiddleware` 引用；
③ **servlet 上下文注册 / 非 web 上下文不注册** `HttpPoolLifecycle`。

**变异验证 2/2 变红**（红在目标断言上）：把 `HttpMiddleware.shutdown()` 加回 `shutdown()` 第一句
⇒ 子进程**真的抛** `NoClassDefFoundError: jakarta/servlet/Filter`（与派单报的**同形**）⇒ ①② 红、③ 仍绿。

**证据强度：L3**（真 JVM · 真缺类 · 真关闭路径 · 变异命中）—— **不是** L1 源码推理。

**Go 出处（消费点）**：Go 侧 rpc 进程的关闭路径**不加载任何 web 类型** ——
`service/follow/rpc/follow.go:45`（`defer s.Stop()`，唯一关闭路径），
而其 import 清单 `service/follow/rpc/follow.go:3-21` **零** `net/http` / servlet 类
⇒ ⇒ RAG 侧这是**内部缺陷**，**不是**双向语义漂移（Go 侧没有「等价形态」可对照）。

#### 4.10.2 🔴 Redis「收编」的真相：**域内副本会「遮蔽」共享层**（不是「叠加」）+ 两处 javadoc 事实订正

**G1 新建了 3 份域内副本**（`user-api` 那份是**本仓既有** `ea8f01e`；`admin-api` / `follow-api` 两份是 G1 在 `a4e3740` 新建）
⇒ 本层复核后得到三条结论：

| # | 结论 | 证据 |
|---|---|---|
| ① | 三份副本与共享层**语义逐条相同**（都**只**设 `disconnectedBehavior(REJECT_COMMANDS)`、都**不碰** `timeoutOptions`）⇒ **删除它们是纯删除、零行为变化** | 三份副本的 `@Bean` 方法体逐行读过 |
| ② | 🔴 域内副本**不是**与共享层「叠加」，而是**把共享层那份 Bean 整个压掉**（`@ConditionalOnMissingBean` ⇒ 域内已定义该类型 ⇒ 共享层那份**根本不被创建**）⇒ 容器里恒为**恰好一份** | 新增守门 `GoStyleRedisClientOptionsPrecedenceGateTest` ① ②（真 Spring 上下文 + 真 Lettuce 客户端） |
| ③ | ⚠️ **代价 = 遮蔽**：域内副本在场时**共享层在该域完全不生效** ⇒ 共享层的后续改动（例如 E66 的 `timeoutOptions`）在该域**静默不生效** ⇒ ⇒ **域内副本必须删除**，删除才是真正的收编 | 守门 ③ 用**判别器**证明（域内副本刻意设 `DEFAULT`，与共享层的 `REJECT_COMMANDS` 相反 ⇒ 生效值是 `DEFAULT` ⇒ 共享层那份**没被叠加**）；变异 3/3 变红 |

**顺带订正共享层 javadoc 的两处事实错误**（Boot 4.0.8 字节码级）：

| 原说法 | 实测 | 证据 |
|---|---|---|
| 「本类会**既被组件扫描、又被自动配置清单**各注册一次 ⇒ **启动直接失败**」 | ❌ **不成立** ⇒ `@AutoConfiguration` **元注解就是 `@Configuration`** ⇒ `AutoConfigurationExcludeFilter`（`@SpringBootApplication` 内建的排除过滤器之一；`task-api` 甚至显式重写了一遍）的判据 =「既是 `@Configuration`、又出现在 `AutoConfiguration.imports` 清单里」⇒ **本类被排除出组件扫描** ⇒ 只经清单注册一次 | `javap` 读 `AutoConfiguration` ⇒ `@AliasFor(annotation = Configuration.class)`；`javap` 读 `AutoConfigurationExcludeFilter` ⇒ `ImportCandidates.load(AutoConfiguration.class)` + `isAnnotated(Configuration.class)` |
| 「两份都设 `REJECT_COMMANDS` ⇒ Boot 会把所有 customizer **依次应用** ⇒ 后者覆盖前者」 | ❌ 机制描述错 ⇒ 是**遮蔽**（共享层那份**不被创建**）⇒ 不存在叠加顺序问题 | 同上守门 ① ② |

⇒ **`@ConditionalOnMissingBean` 保留**，但理由改写为：**「让域内副本优先、保证容器里恰好一份」**。

**⇒ 共享层当前真实生效面（订正 §4.9.9 的表述）**：

| 类别 | 组件 | 断连行为来源 |
|---|---|---|
| **共享层生效**（有 Redis 且**无域内副本**） | `favorite-api` · `task-api` · `comment-rpc` · `hot-rpc` · `like-rpc` · `task-rpc` · `admin-rpc` · `user-rpc`（**8 个**） | 共享层 `GoStyleRedisClientAutoConfiguration` |
| **共享层被遮蔽**（有域内副本） | `user-api`（本仓既有 `ea8f01e`）· `admin-api` · `follow-api`（G1 `a4e3740` 新建） | 域内副本（**与共享层逐条同值** ⇒ 对外行为相同） |

**⇒ 派给 G1 的收编动作（**G1 写权**，本层不代删）**：删除那 3 份 `RedisDisconnectedBehaviorConfig`
⇒ 删完这三域即由共享层接管，且**对外行为零变化**（两侧逐条同值，已核）。
⚠️ 删**前**的前提已满足：三份副本与共享层**逐条同值**（只设 `REJECT_COMMANDS`、不碰 `timeoutOptions`）。

#### 4.10.3 🟢 G3 单 3 —— `SqlTimeoutStatusTranslator` 的「Go 出处」段**归因订正**（纯 javadoc）

**原段**：「Go 侧 ctx 到点后…**经 go-zero 的错误映射**成为「服务繁忙」1015」⇒ 🟡 **结论对、归因错**，且**零 `文件:行号`**（命中 AGENTS.md §3.3.2）。

**订正后的 Go 出处（本层逐条回冻结真源核过，不采信派单快照）**：

| 事实 | `文件:行号` |
|---|---|
| 1015 = **各 api logic 显式 `switch st.Code()` 的 `default` 分支**（**不是** go-zero 的错误映射、**也不是** deadline 映射） | `service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48`（`switch` 44 · `case codes.Internal:` 45 → `ErrorServerCommon` 46 · `default:` 47 → `CodeServerBusy` 48）· **同族共 13 个 admin logic** 同款 · `service/user/user/api/internal/logic/user/selectavatarlogic.go:59-60` |
| 1015 的值与文案 | `service/user/common/errmsg/errmsg.go:6`（`CodeServerBusy = 1015`）· `:41`（`"服务繁忙"`） |
| Go 侧 ctx 到点怎么变成 `DeadlineExceeded` | `service/common/observability/observability.go:146-158`（`IsTimeoutError`：`:150` `context.DeadlineExceeded` · `:153` `codes.DeadlineExceeded` · `:157` 网络超时）⇒ **只做可观测性判定，不产任何业务码** |
| RAG 侧对应消费点 | `service/users/admin/admin-api/.../advice/GlobalExceptionHandler.java:83`（声明）+ **`:90`**（default → 1015）· `service/users/user/user-api/.../advice/GlobalExceptionHandler.java:153` + **`:162`** |

⚠️ **补了一条派单里没有、但会让人误判的边界**：「`DEADLINE_EXCEEDED` 未被单列 ⇒ 落 default ⇒ 1015」
**只对 users 域成立** —— Go 侧 **article 域单列了** `codes.DeadlineExceeded`
（`service/article/api/internal/logic/article/create_article_logic.go:66` ⇒ `errmsg.ErrorArticlePublishing`，**不走 1015**）
⇒ **本条不适用于 article 域**，也不外推到其它域。
⚠️ 另注：`knowledge` 域另有 4 处 `context.DeadlineExceeded`（该域**不在本项目范围**，已在 javadoc 注明）。

**证据强度：L1**（两侧逐行对照，行号全部核实）⇒ 属**文档断言**类问题 ⇒ **纯 javadoc ⇒ 零字节码变化**。

#### 4.10.4 🟢 单 1 的探针：**不需要另造一座 test-scope 桥**（复用主 jar 那座）

- G3 的订正指出：探针 3/3 error 的根因是**探针没复刻生产的排序桥**（MP 的 `@AutoConfigureAfter` 引用的是 **Boot 3 的包名** ⇒ Boot 4 下排序边断链 ⇒ MP 退避）。
- **本层的处置**：**不另造** —— 单 4 已把桥放进**共享层主 jar**（`a1a2e05`）且已注册进 `service/common` 的 `AutoConfiguration.imports`
  ⇒ 探针（`MybatisPlusProbeApplication` = plain `@Configuration` + `@EnableAutoConfiguration`）**自动就吃到了那座桥**
  ⇒ `MybatisPlusInterceptorChainGateTest` **3/3 绿**（实测）。
  ⇒ ⇒ **没有「两条桥」**，也不需要「测试资源里再注册一行」。
- ⚠️ 顺带留一条教训：本轮前序提交 `bf4f4e5` 记的是「**test scope 驱动打挂同模块 7 个既有测试上下文**」
  ⇒ ⇒ **在共享层引入「测试作用域的自动配置」是个陷阱**（它会改变同模块其它上下文的装配面）⇒ 能用主 jar 那座就不要再造。

#### 4.10.5 🟢 G2 的 FB-02 澄清（第 1 步）—— **措辞核对结论：与要求一致**

- `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:11-42`
  - `:11`「闸门『开 / 关』的**唯一判据**：**语义可达性**（G2 反馈单 FB-02）」
  - `:13-23` 两条分支逐条写了「Go 侧该域 handler 调不调 `httpx.Parse`」
    （调 ⇒ `httpx.Parse` → `ParseForm` → `GetFormValues`（`httpx/util.go:15-48`）⇒ 两道上限**可达** ⇒ 闸门必须开；
      不调 ⇒ `r.Form` 从不被求值 ⇒ 两道上限**都不可达** ⇒ 闸门必须关 = `Mode.OFF`）
  - `:24` 旧措辞（「favorite 域不在 global 的写权范围内」）已被**显式退役**并写明理由
  - `:39-42` 「开不开」与「拒成什么形状」拆成**两条独立的轴**
- ⇒ **判据文案已是「该域 Go 侧不解析 form/query ⇒ 该上限在 Go 侧不可达」** ✔
- **未做**（按 G2 的边界）：**没有**在共享层为 task 写特例；**没有**碰 favorite 的任何东西。
- **G2 建议项 3 的对拍脚本**落点 = `tools/difftest/**` = 无 lane 拥有的共享面 ⇒ **只登记，不擅自改**。

#### 4.10.7 🟡 **共享面待整合项**（本 lane **只登记、不改** —— 那些文件不属任何 lane 的写权面）

| # | 事项 | 落点 | 本层的**独立核对** | 处置 |
|---|---|---|---|---|
| ① | `tools/difftest/flyway-concurrent-verify.py:47-51` 的 `KNOWN_BLOCKED` **只登记 `favorite`** ⇒ 同形的 **`article-rpc` 没登记**（G3 的建议项 5） | `tools/difftest/**` = **共享面**（无 lane 拥有 ⇒ 整合者） | ✅ **成立**：实测 `service/article/article-rpc/src/main/resources/application.yml:53-57` 的 flyway 段**既没有 `table:`（⇒ 占用默认账本表名 `flyway_schema_history`）**、**也没有 `baseline-version: 0`** ⇒ 与 `favorite-rpc`（`:86-91`）同款缺陷（`favorite-rpc` 至少有独立 `table:`）⇒ 而该脚本对所有模块都算得出 `baseline_version`、缺省 `"1"` ⇒ **article-rpc 应同样进 `KNOWN_BLOCKED`** | **只记账**（两个 yaml 的修法在**域侧写权**；脚本在共享面） |
| ② | `AGENTS.md:173`（§3.5 表「共享 Flyway 账本」行）**只把 `favorite-rpc` 列为例外** ⇒ **`article-rpc` 漏记**（G3 的建议项 3） | `AGENTS.md` = **整合者专属** | ✅ **成立**（同上实测） | **只记账** |
| ③ | G2 的 FB-02 对拍脚本（「域的 Go handler 是否调 `httpx.Parse`」× 「该域 yaml 的 mode」） | `tools/difftest/**` = **共享面** | —— （G2 已自陈落点并要求「只登记」） | **只记账** |
| ④ | 各域台账 / `AGENTS.md` 里「**E38② 未闭合 / ① 不销本条**」的措辞**已过期** ⇒ 应读作「**① 已闭环、② 未做且已另立项**」 | 各域台账 = **域侧写权**；`AGENTS.md` = 整合者 | ✅ 成立（§1.1 重核：① 已由双侧探针坐实） | **只记账**（已在 §1.1 就地订正本 lane 自己的表） |
| ⑤ | `docs/DRIFT/_cross/domain-common-lanes.md:125`（`DRIFT-FOLLOW-008` 仍写「未修」） | `docs/DRIFT/_cross/**` = **整合者专属** | —— | **只记账** |
| ⑥ | **UTF-8 替换字符（U+FFFD）**：🆕 **「无人拥有」实为 4 处** —— `tools/errmsg-probe/README.md:59`（`未登记码的回〈FFFD×3〉结果`）· `tools/sql-statement-probe/favorite/compare.py:561`（`两侧都〈FFFD×3〉 / 两侧都没有`）· `docs/Pitfalls/message/rpc.md`（2 处）· `docs/Pitfalls/users/user/rpc.md`（1 处） | 前两处：`tools/errmsg-probe/**` · `tools/sql-statement-probe/**` = **既非整合者专属（≠ `tools/difftest/**`）· 也非任何 lane 的写权面** ⇒ **无人拥有**<br>后两处：**内容**分别属 message / users 域，但 `docs/Pitfalls/**` **不在 G2 的机器白名单** ⇒ ⇒ G2 明确表示「我不能改 ⇒ 请整合者定归属」⇒ ⇒ **同样按「无人拥有」记账**（**归属待整合者定**，本层不代改） | ✅ **成立**（G3 于 2026-10-27 的 U+FFFD 全仓扫描报出；**本层已用字节级扫描复核**：`grep -rn $'\xef\xbf\xbd'` 会因 shell 转义而**漏报** ⇒ 一律以 python 字节扫描为准）<br>🆕 **G2 的清单对账（§4.10.23.5）**：G2 那份列 **17** 个文件 ⇒ **本层 12:16 实测 18 个** ⇒ 差 1 = **G2 漏了** `tools/sql-statement-probe/favorite/compare.py` ⇒ ⇒ **补全后两套清单完全一致** ⇒ ⇒ **数字互证**<br>⚠️ 建议字**由 owner 自核**（本层只登记，不代改） | **只记账**（无人拥有 ⇒ 交整合者） |
| ⑦ | 🔴 **`AGENTS.md` 体量已到预算上限**：`tools/check-agents-budget.py` ⇒ **32,686 B / 326 行 = 99.7% · 余量仅 82 B** ⇒ 「下一次写修复记录就会顶破」 | `AGENTS.md` = **整合者专属** | ✅ **成立**（本轮实测 `EXIT=0`，但工具自己已打出告警行）<br>⚠️ 本层**未改** `AGENTS.md`（不在写权面）⇒ 只登记 | **只记账**（整合者需按该工具提示把逐条记录搬去 `docs/DRIFT/_cross/`） |

> ⚠️ **本 lane 的写权边界**：以上 **7** 条**没有一条**落在 `service/common/**` / `service/users/users-common/**` / 本文件
> ⇒ ⇒ **一律只登记，不擅自改**（AGENTS.md §5.3c：跨写权变更须同一次提交或由整合者统一处理）。

#### 4.10.8 🔴 修 G1 #8 时**暴露**出来的潜伏缺陷：**进程级静态池「终止即永久」⇒ 多 Spring 上下文被毒化**

| 项 | 内容 |
|---|---|
| **现象** | 第六次全量回归 **BUILD FAILURE**：`article-rpc` **2 条**失败 ——<br>① `ArticleGrpcInterceptorWiringTest.serverSideTimeoutIsActuallyEnforced`：`actual status=INTERNAL description=panic: Task … rejected from ScheduledThreadPoolExecutor[**Terminated**, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 4]` ⇒ `expected: <DEADLINE_EXCEEDED> but was: <INTERNAL>`<br>② `ArticleGrpcInterceptorWiringTest.deadlineIsPropagatedToHandlerThread`：`handler 根本没被调用（线程 = <未进入>）` |
| **归因（3 环）** | ① `RpcInterceptor.TIMEOUT_SCHEDULER` / `HANDLE_EXECUTOR` 与 `HttpMiddleware.HTTP_EXECUTOR` 是 **`static final` 的进程级单例**（`RpcInterceptor.java:74` / `:84`、`HttpMiddleware.java:160`）；② `shutdown()` 会**永久终止**它们（`shutdownNow()`，不可复活）；③ surefire「**一个模块一个 JVM**」⇒ **模块内存在多个 Spring 上下文** ⇒ 前一个上下文关闭 ⇒ 池被终止 ⇒ 后续上下文里 `schedule()` / `submit()` 被拒（`RejectedExecutionException`）⇒ 拦截器返回 `INTERNAL` 而不是 `DEADLINE_EXCEEDED` |
| 🔴 **为什么这条是「修 #8 才暴露」的** | 修之前，纯 gRPC 进程的 `@PreDestroy` **先抛 `NoClassDefFoundError`** ⇒ `RpcInterceptor.shutdown()` **根本没机会执行** ⇒ 静态池**侥幸存活** ⇒ **前五轮全量一直是绿的** ⇒ ⇒ **那不是「本来就对」，是「缺陷把保护罩挡住了」** |
| **证据** | ① **隔离跑** `ArticleGrpcInterceptorWiringTest` ⇒ **3/3 绿**（⇒ 典型**跨测试污染**，不是本用例自身缺陷）；② 全量里的失败信息**直指** `ScheduledThreadPoolExecutor[Terminated]`；③ 三处 `static final` 池定义逐行可核 |
| **修法（落在共享层 ⇒ 域侧零改动）** | 三个池改为 **`AtomicReference` + CAS 自旋** ⇒ **被终止后，下一次使用按需重建**：<br>① **生产**（单上下文）：shutdown 之后没有下一次使用 ⇒ **池保持关闭** ⇒ **关闭钩子的意图不变**；<br>② **多上下文 / 多测试**：用到时自动重建 ⇒ 不再被毒化；<br>③ 线程是 **daemon** ⇒ 即便重建也不阻止 JVM 退出 |
| **守门（新增 3 条）** | `ObservationPoolSelfHealingGateTest`：① `shutdown()` **真的终止**当时的池（关闭钩子的意图不被稀释）；② 终止后取用 ⇒ 拿到**新**池且**真的能**调度 / 提交；③ HTTP 池同理 |
| **变异验证** | 把「按需重建」退回「直接返回已终止的那份」⇒ ② 红在「必须是**新的**池」 |
| ⚠️ **归属** | 失败用例在 `article-rpc`（**G3 写权**）⇒ 但**根因在共享层** ⇒ 修法落 `service/common`（**本层写权**）⇒ **那 2 条用例不用改** |
| **A 轴** | 属 **A1**（超时 / deadline）的机制面 ⇒ §2 的 A1 行结论不变；证据链由「L4 双侧探针」＋本条的「**L3 全量复现 + 隔离绿对照**」共同支撑 |

> ⇒ **教训（值得留在仓里）**：**「前五轮全量都绿」不等于「这条路径被验过」** ——
> 在本例里，是**另一个缺陷（#8）恰好挡在前面**，让这条路径**从未真正执行过**。
> ⇒ ⇒ 修掉挡路的缺陷 ⇒ 潜伏缺陷显形 ⇒ **这是「跑绿 ≠ 对」的第四种形态**：
> **绿是因为没走到**。
> ⇒ 判定式：**问「这条路径在本轮之前真的被执行过吗」**，而不只是问「测试绿不绿」。

#### 4.10.9 🔴 G2 派单 **FB-04**（A9）—— **已落码**：业务 handler 执行期能拿到**当前 server span**

| 项 | 内容 |
|---|---|
| **1 现象** | Go 侧 like 域 6 处业务逻辑用 `trace.SpanFromContext(l.ctx)` 给 **server span 加属性**；RAG 侧的 Java gRPC handler **不接收 ctx** ⇒ 唯一等价物是 `Span.current()` ⇒ 而 `RpcInterceptor` 此前**只在 `close()`（`:391`）与 `onCancel()`（`:490`）里 `makeCurrent()`** ⇒ **handler 执行期 `Context.current()` 是根 context** ⇒ `Span.current()` 拿到 **invalid（no-op）span** ⇒ `setAttribute` **静默丢弃** ⇒ ⇒ 域侧若按 `Span.current().setAttribute(...)` 落码 ⇒ **跑绿但零效果**（与「假绿」同源） |
| **2 Go 侧 `文件:行号`** | `go-zero v1.10.2 zrpc/internal/serverinterceptors/tracinginterceptor.go:18-24` —— `UnaryTracingInterceptor` 里 `ctx, span := startSpan(ctx, info.FullMethod)` ⇒ `resp, err := handler(ctx, req)` ⇒ **handler 拿到的 ctx 携带 span**<br>同文件 **`:95-107`**（`startSpan` 全函数）⇒ `tr.Start(trace.ContextWithRemoteSpanContext(ctx, spanCtx), …)` ⇒ **span 从 ctx 派生并放回 ctx**<br>⇒ 业务 `trace.SpanFromContext(l.ctx)` **取得到** ⇒ 属性真落 |
| **3 RAG 侧现状（修前）** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:453-465`（`onHalfClose()`）—— **没有** `makeCurrent()` ⇒ handler 执行期无 current span<br>（`makeCurrent()` 只在 `:391` `close()` 与 `:490` `onCancel()`） |
| **4 证据强度** | **L3** —— 真 netty gRPC server（端口 0）＋ 真 OTel SDK（`InMemorySpanExporter`）⇒ 断言落在**导出的 span 数据**上；不是「注解存在」也不是「Bean 存在」 |
| **5 修法（落共享层 ⇒ 域侧零改动）** | `RpcInterceptor.java:456` —— 在 `onHalfClose()` 里把 `otelCtx.makeCurrent()` 的 scope **包住 `super.onHalfClose()`**（即 handler 执行期）⇒ 业务 `Span.current()` 就是那个 server span<br>⇒ **不做「让业务自建子 span」**：Go 侧 like 域是**给已有 server span 加属性、不新建** ⇒ 自建子 span 会让 trace 里多一个 Go 没有的 span ⇒ **结构漂移** |
| **6 守门（新增 4 条）** | `service/common/src/test/java/com/rag/common/observability/RpcServerSpanCurrentGateTest.java`：① handler 里 `Span.current()` 必须**有效且 recording**；② 业务 `setAttribute` 的属性必须**真的出现在导出的 server span 上**；③ handler 里看到的必须**就是**导出的那个 span（不是子 span）；④ scope 不得**泄漏到下一次**请求<br>⇒ 每次调用前清空观测与导出 ⇒ **不依赖 JUnit 方法执行顺序**（JUnit 5 默认按方法名 hash 排序） |
| **6.1 与既有守门的分工（**先查再造 ⇒ 不重复造网**）** | 🔴 合理的质疑：「在 `onHalfClose` 里加一层 OTel scope，会不会**破坏 deadline 预算的 ThreadLocal 传播**？」<br>⇒ 查既有守门：`service/common/src/test/java/com/rag/common/observability/RpcInterceptorDeadlineBindingTest.java` ① **已在真 gRPC 管线 + 真 handler 线程**上断言「入口 `remainingMillis() != Long.MAX_VALUE`」＋「睡过 deadline 后 `isExpired() == true`」⇒ ⇒ **该问题已被现有守门覆盖** ⇒ **本层不新增重复守门**，由第八次全量**实跑**它来实证<br>⇒ **两者互补不重叠**：它验「**deadline** 在 handler 线程可见」（`DeadlineState` = **预算** ThreadLocal）· 本轮新守门验「**span** 在 handler 线程可见」（OTel `Context` = **另一套** ThreadLocal）⇒ ⇒ **两套 ThreadLocal 互不干扰**这一点由「两个守门同时绿」共同证明 |
| **7 变异验证** | 撤掉 `makeCurrent()` 包装 ⇒ **4/4 红（0 errors）** ⇒ 精确命中目标断言，无邻近误伤 |
| **8 是否阻塞本波** | **否** —— 纯共享层改动 ⇒ 10 个 rpc 组件**域侧零改动** |

##### 4.10.9.1 影响面**自核**（Go 全仓 `SpanFromContext` **12 处**逐处分类，核对日期 2026-10-27）

| Go 侧落点 | 数量 | RAG 侧等价物 | FB-04 是否相关 |
|---|---|---|---|
| `service/like/rpc/internal/logic/` 6 处（`getlikestatelogic.go:37` · `getlikecountlogic.go:37` · `gettargetlikerlistlogic.go:35` · `getuserlikelistlogic.go:34` · `getusertotallikelogic.go:34` · `likeactionlogic.go:161`） | 6 | `Span.current()`（**域侧待落码**） | ✅ **正是 FB-04 修的** —— `l.ctx` 就是 handler 传下来的携带 span 的 ctx |
| `service/common/observability/observability.go:70/:92/:114/:132` | 4 | `Observability.mark*` 的 **`Span.fromContext(ctx)`**（**显式传 ctx**，`Observability.java:73/:94/:120/:139`） | ❌ **不受影响** ⇒ 共享层 `sea.*` 属性**本来就落得到**（与 Go 的显式 ctx 语义一一对应）⇒ **零回归** |
| `service/knowledge/api/internal/telemetry/operation.go:256` · `http.go:128` | 2 | —— | ❌ **范围外**（`knowledge` 不在 10 域范围内 ⇒ 不是待办） |

> ⇒ **RAG 侧主源码里 `Span.current()` 的真实调用只有 1 处**（其余命中全是 javadoc / 注释）：
> `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentFactDeliveryService.java:148`
> ⇒ 它由 **`CommentFactDispatchScheduler.java:95`（`deliveryService::dispatchOnce`）** 在**调度线程**上调用
> ⇒ **不经过 gRPC handler** ⇒ **FB-04 碰不到它** ⇒ **零回归** ✅

> 🟢 **对称性核查（FB-04 是否需要对称补 HTTP 侧）⇒ 不需要**
> ⇒ Go 侧 `service/common/observability/http.go` **无任何 span 起点**（span 归 go-zero `rest` 自带的 `TraceHandler`）
> ⇒ RAG 侧 **`HttpMiddleware.java:332` 已有 `parentOtelCtx.makeCurrent()`** 包住处理链
> ⇒ ⇒ **HTTP 路径的 current span 可见性本来就具备** ⇒ ⇒ **FB-04 的修法只落 gRPC 侧** ⇒ **影响面不扩散**。

> ⚠️ **附带查出（属 comment 域 = G3 写权，本 lane 不代改）**：上述 `:148` 拿到的 `Span.current()` 在调度线程上是 **invalid span**
> ⇒ `:193-194` 的 `comment.fact.event_id` / `comment.fact.aggregate_version` **静默丢弃**
> ⇒ 而 **Go 侧 comment 的 fact 投递链路根本不用 span**（`service/comment/rpc/internal/model/fact_delivery.go` 与 `cmd/fact-dispatch/main.go` **均无 span/trace 调用**）
> ⇒ ⇒ 定性：**不是「RAG 少了 Go 有的东西」（不是漂移），而是 RAG 侧多了一段「看似在做可观测、实则完全无效」的代码**
> ⇒ ⚠️ **风险是「给人错觉」**：读代码会以为 comment 的 fact 投递有 trace 追踪 ⇒ **实际上一条 span 都没有**
> ⇒ 已按回执规格 **4-3 点明给 G3**。

#### 4.10.10 🟢 **U+FFFD（UTF-8 替换字符）**：G3 的全仓扫描 ⇒ **2 条落在 G4 写权面 ⇒ 本层自修**（核对日期 2026-10-27）

| 项 | 内容 |
|---|---|
| **来源** | G3 于 2026-10-27 做的**全仓 U+FFFD 扫描**（13 条域外）⇒ 其中 **2 条正好落在 `service/common/**`（= G4 写权面）** ⇒ 按「**域侧能自修的域侧修**」**本层自修** |
| **落点 ①** | `service/common/src/test/java/com/rag/common/observability/GoStyleOtelSamplerProtocolGateTest.java:24`<br>修前：`{@link GoStyleOtelAgentGateTest} 证的〈FFFD×2〉「全局 provider 装上了 / span 真导出 / 关停会 flush」`<br>修后：`{@link GoStyleOtelAgentGateTest} 证明「全局 provider 装上了 / span 真导出 / 关停会 flush」` |
| **落点 ②** | `service/common/src/test/java/com/rag/common/protection/FakeServerCall.java:13`<br>修前：`测试用〈FFFD×2〉 {@link ServerCall} —— 只实现服务端拦截器真正用到的那几个方法。`<br>修后：`测试用的 {@link ServerCall} —— 只实现服务端拦截器真正用到的那几个方法。` |
| **核验（不盲信 G3 的「建议字」）** | ① **字节级扫描**（python 读 `bytes`）⇒ 两处各 `FFFD_total=2` ⇒ **确认损坏**；② **码点 dump** ⇒ 两处都是 `0xfffd 0xfffd` 连续；③ **`git log --all` 逐提交追** ⇒ 两文件**首个提交即已损坏** ⇒ **原字不可考** ⇒ 只能**按语义重建**（与 G3 处理 favorite 那 4 处的方式一致） |
| **推断依据（形态 + 语义双重吻合）** | **2×FFFD = 一个 3 字节汉字丢了末字节**（UTF-8 汉字 3 字节 ⇒ 剩 2 字节不完整 ⇒ 2 个替换符）<br>⇒ 「**明**」`E6 98 8E` 丢末字节 → `E6 98` ⇒ **正好 2×FFFD**；「**的**」`E7 9A 84` 丢末字节 → `E7 9A` ⇒ **正好 2×FFFD**<br>⇒ 两条推断**都与字节形态吻合**，且语义通顺（`证明「…」` / `测试用的 {@link …}`）⇒ **可信** |
| **修法** | **字节级替换**（python `\uXXXX` 转义）⇒ 写进文件的是**正确 UTF-8 字节**；⚠️ **不用 bash heredoc 写中文**（AGENTS.md 已记：heredoc 的中文会被打成 U+FFFD —— 本次用转义序列**绕开**该陷阱）⇒ 替换后**严格 UTF-8 解码通过** |
| **影响面** | **纯 javadoc 注释** ⇒ **零行为变化**；两文件都在 `service/common` 的**测试**面 ⇒ 由第八次全量回归覆盖 |
| **其余 11 条的分派（不归本层）** | **G2 域 3 条**（`like-rpc/…/LikeOutboxEventEntity.java:17` · `message-rpc/…/MessageRpcProductionAddressTest.java:96` · `message-api/…/GoStyleGrpcClientBreakerE2ETest.java:43`）⇒ **G2 写权**（G3 已直接分派给 G2）· **G1 域 3 条** ⇒ **G1 写权**（G3 已分派）· **共享面 2 条** ⇒ **无人拥有** ⇒ 已登记到 **§4.10.7 ⑥** · **3 处「故意保留」**（`article-api` 的非 BMP 码点序用例 · `user-api` 的「body 不得含替换符」断言 · `docs/Pitfalls/**` 记录病症样本处）⇒ **一个字都不动** |

> ⚠️ **可复用的核验手法（值得留在仓里）**：`grep -rn $'\xef\xbf\xbd'` **会漏报**（shell 转义 / locale 差异）
> ⇒ ⇒ **一律用 python 读 `bytes` 判**；且 **必须逐行看上下文** ⇒ **有的 U+FFFD 是「故意的」**（被测对象 / 病症样本）⇒ **改它会直接废掉那条测试**。

> 🔴 **本层自己踩到的坑（差点「修 U+FFFD 却引入 U+FFFD」）**：台账里**引用**病症样本时，
> 我把 G3 回信里的**带替换符原文原样抄了进去** ⇒ **自己的台账里多了 10 个真 U+FFFD**（字节级扫描当场抓到）
> ⇒ ⇒ **修法：引用病症样本一律用**描述**而不是复制替换符** —— 写 `〈FFFD×3〉`（连着几个就写几）⇒ **文件里不留真替换符**
> ⇒ ⚠️ 而且这与「故意保留」是**两回事**：故意保留是**被测对象**（改了就废测试）· **引用**是**台账叙述**（完全可以写成描述）⇒ ⇒ **两者都不该留真替换符**。

#### 4.10.11 🟢 **第八次全量回归口径**（核对日期 2026-10-27 · 代码态 `853767b` + `0c72969`）

| 项 | 值 |
|---|---|
| **命令** | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| **结果** | ✅ **BUILD SUCCESS / `MVN_EXIT=0`** |
| **模块数** | **52**（Reactor Summary 计数） |
| **有测试的模块数** | **29**（与第七次同） |
| **用例数** | **2461**（**29 行「模块名 ↔ 数字」明细**逐项求和 ⇒ **非按行序 tail**） |
| **failures / errors / skipped** | **0 / 0 / 4**（`favorite-api` skipped 1 · `favorite-rpc` skipped 3） |
| **耗时** | **20:21 min**（09:57:42 起跑 ⇒ 10:18:03 完成） |
| **相对上一轮** | 第七次 2457 ⇒ **+4**（= FB-04 守门 **4 条**；`service/common` **307 → 311**）⇒ **其余 28 个模块逐条一致** |
| **逐模块明细** | `users-common` 33 · `common` **311** · `admin-rpc` 112 · `admin-api` 129 · `user-rpc` 133 · `user-api` 107 · `article-common` 15 · `article-rpc` 154 · `article-api` 92 · `hot-common` 14 · `hot-api` 96 · `hot-rpc` 96 · `message-common` 21 · `message-rpc` 84 · `message-api` 88 · `like-common` 18 · `like-api` 81 · `like-rpc` 96 · `task-api` 90 · `task-rpc` 114 · `follow-common` 37 · `follow-api` 50 · `follow-rpc` 74 · `favorite-common` 29 · `favorite-api` 97 · `favorite-rpc` 110 · `comment-common` 27 · `comment-api` 45 · `comment-rpc` 108 ⇒ **逐项可复算** |
| **双侧探针**（同批次复跑） | `run-parity.sh` ⇒ **EXIT=0 · 56 例**（一致 54 · 不一致 0 · 已签字偏离 2）· `run-rpc-timeout-parity.sh` ⇒ **EXIT=0 · 3 例**（两侧一致；`timeout_telemetry` 是已登记的 C-1 表述差异，不计失败） |
| **三门禁**（同批次复跑） | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **台账门禁** | `check-ledger-tables` / `check-ledger-selfcheck` / `check-ledger-count` ⇒ **全 EXIT=0** · `check-agents-budget` ⇒ EXIT=0（⚠️ 但 **AGENTS.md 余量仅 82 B** ⇒ 已登记 §4.10.7 ⑦） |
| **写权自证** | 本轮改动**全部**落在 `service/common/**` ＋ `docs/DRIFT/global/vs-common.md` ⇒ **零域侧改动**（`git diff --name-only 5564c27..HEAD` = **4 个文件，全在 `service/common/**`**） |

> 🔴 **口径纪律（本轮特意再确认一次）**：surefire 的输出有**两类行** ——
> ① **模块汇总行**（`Tests run: N, Failures: …` 且**不以 `-- in` 结尾**）⇒ **每模块恰一行**；
> ② **测试类行**（以 `-- in <FQCN>` 结尾）⇒ 每**测试类**一行（本轮 358 行）。
> ⇒ ⇒ **合计只能取 ①**；**②** 求和会**多算**（把每个测试类的总数又加了一遍）
> ⇒ ⇒ 本轮用「**先剥注释、再按 `Building <module>` 行配对**」把 ① 与**模块名**绑定 ⇒ **29 行明细可逐项复算**。
> ⇒ ⚠️ **绝不按行序 tail**（上一次就栽在「把 `comment-common` 的 27 当成 `comment-rpc` 的条数」）。

> 🟡 **错峰记录**：本次**没有**在别的 lane 跑 maven 的窗口起全量 ——
> 起跑前**实测确认**（`ps aux | grep maven` ⇒ 零真实 maven 进程），
> 且此前 G1 正在跑 6 模块子集回归（pid 6839/6840，09:53 起）⇒ **本层等到 09:57:15 它结束后才起**
> ⇒ ⇒ **避免了 EXIT=137（系统 OOM-kill）**（§4.9.13 已记该机制）。

#### 4.10.12 🟢 **G1 的 Filter 槽位协调约束** ⇒ 独立核实 + 落码**槽位快照守门**（核对日期 2026-10-27）

| 项 | 内容 |
|---|---|
| **来源** | G1 落码 `DRIFT-USERAPI-009②`（user-api 的 JWT 验签搬出 3s 预算）后发来**协调约束（非派单 · 无需落码）**：「共享层若要动 Filter 的 `@Order`，请在回执里点名这一条」 |
| **🔴 本层的独立核实（不采信转述）** | 逐个类反射/文本核 `@Order` ⇒ **G1 给的 6 个槽位全部一致**：Traceparent **+3** · MaxConns **+4** · Breaker **+5** · CpuShedding **+6** · HttpMiddleware **+7** · MaxBytes **+8**<br>⇒ **但 G1 的清单漏了一个**：`GoStylePathNormalizingFilter` = **+2**（`http/GoStylePathNormalizingFilter.java:70`）⇒ 共享层实际是 **7 个** Filter 槽位<br>⇒ **另核 G1 的推导前提**（「+7 = 3s 预算起点」）：**成立** —— 预算的 `begin` / `clear` 都在 `HttpMiddlewareFilter` 的链内（`…/observability/HttpMiddleware.java:324 begin` / `:356 clear`）⇒ ⇒ **G1 的域侧约束推导正确** |
| **G1 的约束（原文要点）** | 域侧 `GoStyleJwtAuthFilter` 落在 **+6**，两端被夹住：**必须 &lt; +7**（否则验签回到 3s 预算内 ⇒ 002 退回 OPEN）· **必须 ≥ +3**（否则验签落到 traceparent 的 SERVER span 外 ⇒ 观测面漂移）⇒ ⇒ **[+3,+6] 四槽全被共享层占满** ⇒ 域侧只能与 CpuShedding(+6) 并列<br>⇒ 并列为何安全：该 Filter **绝不写响应**（只验签、只把结果放进 request attribute，401 仍由 interceptor 抛）⇒ 谁先谁后不改任何响应 |
| **🔴 本层动作：把「先后手清单」变成机器可验** | 新增 `service/common/src/test/java/com/rag/common/spring/SharedFilterOrderSnapshotGateTest.java`（**4 条**）：<br>① **槽位快照**：7 个共享层 Filter 的 `@Order` 精确值 ⇒ **任何重排都红** ⇒ 强制「改槽位」变成**显式决策**（改快照 = 明知故犯）而不是「顺手挪一个数字」<br>② **3s 预算起点不被抢占**：其后的共享层 Filter **只允许**已签字的 `MaxBytes(+8)`<br>③ **域侧仍有可用槽位**：`HttpMiddlewareFilter` 槽位必须 **≥ +7** ⇒ 域侧 +6 才能排在预算外；且 `[+3, 预算起点)` 区间**必须仍被占用**（说明这段是**被刻意保留**的）<br>④ **真 servlet 上下文**里 7 个 Filter **确实注册** ＋ **生效次序**与快照一致（防「注解在、bean 不在」） |
| **与 G1 域侧守门的分工（**互补，不重复**）** | G1 的 `UserApiJwtVerificationScopeTest` 在 **users 域**、且只在**真被重排时**才红 ⇒ **只覆盖 user-api 一个域**<br>⇒ 本守门是 **共享层侧**、**全域**、且是**快照式**（重排必红 ⇒ 强制显式化）⇒ ⇒ **两者互补** |
| **变异验证（三个 · 逐条证明 4 条断言各有判别力）** | **A** `HttpMiddlewareFilter +7 → +6`（= G1 担心的「下调」场景）⇒ **①③ 红（2/4）**<br>**B** `Traceparent +3 ↔ MaxConns +4`（相对次序交换）⇒ **①④ 红（2/4）**<br>**C** `CpuShedding +6 → +9`（落到预算起点之后）⇒ **①②④ 红（3/4）**<br>⇒ 恢复后 **4/4 绿** · **生产代码零改动** |
| **🔴 过程中被自己的守门抓出两次「空转」（值得留仓）** | ① **④** 曾写成「从容器取 `FilterRegistrationBean` 断言其 order」⇒ 我加的**守恒断言当场把它抓红** ⇒ 查 Boot 4 源码才知：**容器根本不注册 `FilterRegistrationBean` 这个 bean**（`ServletContextInitializerBeans` 内部用**包级私有**的 `FilterRegistrationBeanAdapter` **临时**包装）⇒ ⇒ 「哪个 Filter 注册在哪个 order」**没有公开读法** ⇒ 改用 **Spring 自己排 Filter 用的 `AnnotationAwareOrderComparator`**（即 `ServletContextInitializerBeans#getOrder` 内部走的那条路）作**等价读法**（并把这段经过写进 javadoc）<br>② **②** 曾用**快照值**而非**实际读数**算越界者 ⇒ **变异 C 抓出它恒绿（空转）** ⇒ 已改为**实际读数**（`readActualSlots()`）⇒ ⇒ **「绿是因为没走到」的又一形态：断言自己算的是「快照」而不是「事实」** |
| **回归口径（本轮）** | **模块级**：`mvn -o -pl service/common test` ⇒ **315 条 / 0 失败 / 0 errors / 0 skipped**（311 + 本次 4 条）<br>**全量未跑 · 理由**：本轮**零生产代码改动**（只新增**一个测试文件**）⇒ 其余 51 个模块的产物与行为**不受影响** ⇒ 且第八次全量（**基线 `23d1c2a` 代码态** · 52 模块 / **2461** 条 / 0 失败 / 0 errors / 4 skipped / 20:21 min / `EXIT=0`）刚跑过且全绿 ⇒ 不重复 20 分钟全量<br>**双侧探针**（同批次复跑）：`run-parity.sh` ⇒ **EXIT=0 · 56 例**（一致 54 / 不一致 0 / 已签字偏离 2）· `run-rpc-timeout-parity.sh` ⇒ **EXIT=0 · 3 例**<br>**三门禁**：**0 / 0 / 0** · **台账三门禁**：**0 / 0 / 0** |
| **本层对 G1 后续项的表态** | G1 的 `DRIFT-USERAPI-009 §7`（MaxBytes 相对 observability 的位置：Go 在预算外 / RAG 在内）⇒ **同意其定性**：其代码修法 = 把 MaxBytes 挪到 +7 之前 ⇒ **槽位已满** ⇒ 必须**整体重排共享层顺序** ⇒ 按 AGENTS.md §5.3c「跨共享层 + 多域必须同一次提交」⇒ **不是 G4 能单方面落的** ⇒ **更便宜的路是签字**（并入 `SD-USERAPI-002` 的范围说明，方向与它一致：RAG 多记 413）⇒ **签字档归整合者，本层不代签、只登记** |

#### 4.10.13 🟢 **G2 派单 FB-05**（A3）—— **已落码**：`mode: off` 在 YAML 里必须可用（核对日期 2026-10-27 · 基线 `align/g4-service-common` @ `c15dfeb`）

| 项 | 内容 |
|---|---|
| **① 现象** | 未加引号的 `mode: off` 被 **YAML 1.1**（SnakeYAML）解析成**布尔 `false`** ⇒ `@Value` 拿到字符串 `"false"` ⇒ 而 `parse` 的合法值域（`off\|raw\|envelope`）里**没有**它 ⇒ **构造期抛 `IllegalArgumentException` ⇒ `BeanCreationException` ⇒ 进程起不来** ⇒ ⇒ **文档里那个合法取值，在 YAML 里根本写不出来**（除非加引号 `mode: "off"`）<br>⇒ **一颗等着踩的雷**：任何域一旦按文档写 `mode: off` 就会起不来 |
| **② Go 侧出处** | Go 侧**没有这个开关**（`go-zero@v1.10.2/rest/config.go` 里没有 form-limit 旋钮；`maxFormParamCount = 2048` 是 `go-zero@v1.10.2/rest/httpx/util.go:15` 的常量）⇒ ⇒ **OFF 在 Go 侧的等价物就是「什么都不做」** ⇒ ⇒ RAG 的 OFF 语义本身**是对的**，问题**只出在「配置值 → 代码」的传递** |
| **③ RAG 侧现状（修前）** | `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:209-225`（`parse` 只认 `off`/`raw`/`envelope`）· `…/http/GoStyleFormValueLimitInterceptor.java:68-72`（**构造期** `@Value` + `parse` ⇒ 非法值 ⇒ 启动失败）· `…/http/GoStyleTomcatParameterLimitCustomizer.java:110-111`（**同一把尺子**）<br>⇒ 🟢 **本层实测核对了 G2 的「两边不会对同一属性得出不同解读」**：两个消费点的 `@Value` **默认值完全一致**（都是 `${rag.http.go-form-limit.mode:off}` ⇒ `…FormValueLimitInterceptor.java:70` 与 `…TomcatParameterLimitCustomizer.java:111`）⇒ ⇒ **成立**；且 `GoStyleFormLimitWebMvcConfig` **不消费**该属性（只注册拦截器）⇒ 无第三个解读点 |
| **④ 证据强度** | **L4** —— ① G2 的**真上下文启动失败实测**（报错值是 `false` 而不是 `off` ⇒ YAML 1.1 布尔化的实证）；② **本层独立复现**：守门 ③ 用**生产同款**的 `YamlPropertySourceLoader` 解析未加引号的 `mode: off` ⇒ 断言拿到的**确实是 `Boolean.FALSE`** ⇒ ⇒ **本层独立确认「当前 Boot 4 的解析器仍走 YAML 1.1 布尔表」**（不采信转述，也不拿裸 SnakeYAML 代替） |
| **⑤ 修法** | `parse` **额外接受 `"false"`** —— ⚠️ **只加这一个别名**（`false`/`False`/`FALSE` 三个形态，因 `parse` 已 `toLowerCase`）<br>⇒ **理由（本层独立判断，非照抄派单）**：`"false"` 是**精确匹配**、**不是**兜底 —— 它是**文档化合法取值 `off` 的「唯一实际编码形式」**（YAML 1.1 把 `off`/`no`/`n` 全变成 `false`）⇒ ⇒ 接受它 = **让实现对齐文档**，而不是放宽合法性<br>⇒ **不放宽成「任意非法值都当 OFF」** —— 那会**重新引入**「静默兜底 = 以为装上了其实没装」（`parse` 自己的 javadoc 明确反对）⇒ 例：`mode: OFF1` 若兜底成 OFF ⇒ 该域会**静默失去闸门** ⇒ ⇒ **非法值仍一律抛异常**<br>⇒ **方向性判断**：**让实现对齐文档**（`off` 是文档化合法取值）而不是**改文档迁就实现**（把 `off` 从值域里删掉）<br>⇒ 同步：`PROPERTY` 的 javadoc 写清「YAML 里写 `off` **必须加引号**：`mode: "off"`」；**异常消息改成自解释**（列出 `off \| false \| raw \| envelope` + 引号提示 ⇒ 免得下一个人被「值：false」误导成配置写错） |
| **⑥ 守门（新增 5 条）** | `service/common/src/test/java/com/rag/common/http/GoStyleFormLimitModeYamlBooleanGateTest.java`：① `off`/`raw`/`envelope`/`null`/空串 的**原语义一字未改**；② `"false"`/`"False"`/`"FALSE"` 与 `off` **同义**；③ **YAML 端到端**（生产同款 loader ⇒ 断言是 `Boolean.FALSE`）；④ **真启动**（`WebApplicationContextRunner` + **真 yaml**（未加引号）⇒ **上下文起得来**）；⑤ **非法值仍炸**（`bogus`/`true`/`on`/`yes`/`OFF1`/`no`/`n`）⇒ 证明**没有兜底** |
| **⑦ 变异验证** | **A** 删掉 `case "false"`（回到修前）⇒ **②③④ 红**（④ = 真启动起不来 ⇒ **复刻了 G2 的 L4 失败形态**）<br>**B** `default` 改成「任意非法值都当 OFF」⇒ **⑤ 红**<br>⇒ 恢复后 **5/5 绿** · 生产代码 diff 只含正式修法 |
| **⑧ 是否阻塞本波** | **否** ⇒ 现状行为**零变化**（见下方「独立核实」：**当前受害域 = 0**） |

##### 4.10.13.1 本层的**独立核实**（不采信转述）

| 项 | 实测结果 |
|---|---|
| **「当前受害域 = 0」** | ✅ **成立** —— 全仓 `service/*/*-api/src/main/resources/application.yml` 实测：`article-api` `raw` · `comment-api` `envelope` · `follow-api` `envelope` · `hot-api` `envelope` · `like-api` `envelope` · `message-api` `envelope` · `user-api` `raw` · `admin-api` `raw` · **favorite-api 根本没有 `go-form-limit` 段**（⇒ 缺省 `OFF` ⇒ **语义正确**，不是「刻意保留的偏离」⇒ 详见 §4.10.15）⇒ ⇒ **无任何域写字面量 `off`** ⇒ ⇒ **本次修法对现状零行为变化** |
| 🔴 **但我树里 `task-api` 仍是 `mode: raw`** | 而 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 表里 **task-api 的语义答案是 `OFF`**（Go 侧 `task/api/**` 的 `httpx.Parse`/`ParseForm`/`GetFormValues` 命中数 = **0** ⇒ `r.Form` 从不被求值 ⇒ 两道上限**都不可达**）⇒ ⇒ **我树里存在一条「yaml 与语义答案不一致」的待修漂移**（task-api 开了本该关的闸门）⇒ **归属 task 域（G2 写权）** ⇒ **G2 在自己树里改成 `mode: "off"` 正是修它** ⇒ ⇒ 本层**只登记、不代改**<br>🆕 **2026-10-27 复核**：G2 树已改（`service/task/task-api/src/main/resources/application.yml:225` = `mode: "off"`，**带引号**）⇒ ⇒ **合并后本条自动闭环** ⇒ ⇒ ⚠️ **合并前本层树见旧值 = 「树间差异」，不是「漂移」**（同一文件在不同树上的内容/行号都可能不同 ⇒ 跨树断言必须带基线） |
| 🟢 **本层回 Go 真源复核了那张表（不采信共享层自写的「目标态」表）** | `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 的 javadoc 自陈「这张表是**目标态**，不是现状快照」⇒ ⇒ 本层**回 Go 真源逐条核了 task 这一格**：<br>· `service/task/api/**` 对 `httpx.Parse` / `ParseForm` / `GetFormValues` / `r.Form` ⇒ **零命中**<br>· handler 走 `json.NewDecoder(r.Body)`（`service/task/api/internal/handler/task/gettaskhandler.go:19`）⇒ `r.Form` **从不被求值** ⇒ 两道上限**都不可达** ⇒ **语义答案 = `OFF`** ✅ 与表一致<br>· 横向对比（防「只核了一个域」）：`service/article/api/**` 与 `service/message/api/**` 均有 `httpx.Parse` 命中 ⇒ 那些域闸门**必须开** ✅<br>⇒ ⇒ **该表在 task 这一格是准确的**（⚠️ 其余 9 格本层**未逐条核**，不外推） |
| 🔴 **漂移的方向 = 「误拒」，不是「多记 413」** | 我树里 task-api 的 `mode: raw` ⇒ 闸门**装着** ⇒ **超过 2048 个非空 query 值就 400 拒掉**<br>⇒ 而 Go 侧 `r.Form` 从不求值 ⇒ **Go 照常受理**<br>⇒ ⇒ 可观察差异 = **RAG 拒掉了 Go 能受理的请求**（<b>误拒</b>方向）⇒ ⚠️ 正是 `GoStyleFormLimitMode` 类 javadoc 自己说的「**『误拒』比『漏拒』更危险**（会挡掉 Go 能受理的请求）」⇒ ⇒ **这条的优先级高于「多记 413」那一类** ⇒ ⇒ 归 task 域（G2 写权）⇒ 本层只登记 |
| **各域 yaml 注释的订正** | 落点是 `service/*/*-api/src/main/resources/application.yml`（**各域写权**）⇒ 本层**只登记**；G2 已在 task-api 域内改好<br>⚠️ **实测澄清**：全仓**没有任何**域的 yaml 注释写着「`mode: off`」—— 各域注释写的都是 `mode: raw` / `mode: envelope` ⇒ ⇒ **「各域 yaml 注释」实际只指 G2 自己改过的那份 task-api**（他在改值时把注释写成了 `mode: off` ⇒ 故需订正成 `mode: "off"`）⇒ ⇒ **不需要逐域改 9 份** |
| **🔴 全仓「唯一未加引号的 `mode: off`」** | 实测 `grep -rn "mode: *off" service/`（排除 `target/`）⇒ **全仓唯一命中就是本层守门自己的那段测试 yaml**（`GoStyleFormLimitModeYamlBooleanGateTest.java:57`）⇒ ⇒ ① 测试面（`src/test/resources` / `src/test/java`）**零配置** ② 无其它配置源（`application-*.yml` / `*.properties` 均无）⇒ ⇒ **「当前受害域 = 0」彻底成立**，且那颗雷已被**主动引爆**（而不是等着踩） |
| 🔴 **favorite 域零影响（写权硬线）** | `GoStyleFormLimitMode` 的 javadoc 明确：favorite **不在 global 的写权范围内** ⇒ **共享层对它必须零影响** ⇒ 本轮改了共享层的 `parse` ⇒ **必须自证零影响**：<br>① favorite-api **不声明**该属性（实测其 yaml 无 `go-form-limit` 段）⇒ `@Value` 取**缺省** `${...:off}` ⇒ 注入字符串 `"off"`<br>② `parse("off")` 的返回值**一字未改**（守门 ① 钉死）⇒ OFF（与修前相同）<br>③ 新增的 `"false"` 分支**只在属性显式写成 `false`/未加引号的 `off` 时触发** ⇒ favorite 走**缺省路径** ⇒ **永不进入新分支**<br>⇒ ⇒ **对 favorite 零行为变化** ✅ |

#### 4.10.14 🟢 **第九次全量回归口径**（核对日期 2026-10-27 · 代码态 `c15dfeb`）

| 项 | 值 |
|---|---|
| **命令** | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| **结果** | ✅ **BUILD SUCCESS / `MVN_EXIT=0`** |
| **模块数** | **52**（Reactor Summary 计数） |
| **有测试的模块数** | **29**（与前两轮同） |
| **用例数** | **2470**（**29 行「模块名 ↔ 数字」明细**逐项求和 ⇒ **非按行序 tail**） |
| **failures / errors / skipped** | **0 / 0 / 4**（`favorite-api` skipped 1 · `favorite-rpc` skipped 3） |
| **耗时** | **20:22 min**（10:34:55 起跑 ⇒ 10:55:17 完成） |
| **相对上一轮** | 第八次 2461 ⇒ **+9**（= 槽位快照守门 **4** + FB-05 守门 **5**）⇒ `service/common` **311 → 320** ⇒ **其余 28 个模块逐条一致** |
| **逐模块明细** | `users-common` 33 · `common` **320** · `admin-rpc` 112 · `admin-api` 129 · `user-rpc` 133 · `user-api` 107 · `article-common` 15 · `article-rpc` 154 · `article-api` 92 · `hot-common` 14 · `hot-api` 96 · `hot-rpc` 96 · `message-common` 21 · `message-rpc` 84 · `message-api` 88 · `like-common` 18 · `like-api` 81 · `like-rpc` 96 · `task-api` 90 · `task-rpc` 114 · `follow-common` 37 · `follow-api` 50 · `follow-rpc` 74 · `favorite-common` 29 · `favorite-api` 97 · `favorite-rpc` 110 · `comment-common` 27 · `comment-api` 45 · `comment-rpc` 108 ⇒ **逐项可复算** |
| **双侧探针**（同批次复跑） | `run-parity.sh` ⇒ **EXIT=0 · 56 例**（一致 54 · 不一致 0 · 已签字偏离 2）· `run-rpc-timeout-parity.sh` ⇒ **EXIT=0 · 3 例** |
| **三门禁**（同批次复跑） | `check-wave-lane-ownership` / `check-wave-role-model` / `check-wave-coupling` ⇒ **0 / 0 / 0** |
| **台账门禁** | `check-ledger-tables` / `check-ledger-selfcheck` / `check-ledger-count` ⇒ **全 EXIT=0** |
| **写权自证** | 本轮改动**全部**落在 `service/common/**` ＋ 本文件 ⇒ **零域侧改动** |

> 🟡 **为什么本轮必须跑全量（而上一轮「只加守门」时可以不跑）**：本轮**改了共享层生产代码**
> （`GoStyleFormLimitMode.parse` 新增一个 `case`）⇒ ⇒ 它被 **10 个 api 全依赖** ⇒ ⇒ 必须全量。
> ⇒ 上一轮（槽位快照守门）**零生产代码改动** ⇒ 只跑 `service/common` 模块（315 条）并写明「未跑全量 + 理由」。
> ⇒ ⇒ **两者都写进了台账**，不混用口径。

#### 4.10.15 🟢 **G3 反馈单「轴 ② 判据」订正落码**（核对日期 2026-10-27 · 代码态 `5a954bb`）

> **派单来源**：G3 本轮发来 5 条（④免单 · ⑤确认，无需动作）；本条 = 其 **②「轴 ② 判据对 favorite 不成立」**。
> **落点已独立核准**（派单的「落点」列是快照，不采信）：`service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java`
> ＋ `GoStyleTomcatParameterLimitCustomizer.java` ＋ `GoStyleFormValues.java` ＋ `GoStyleFormLimitExceededException.java`
> ＋ 3 个测试类 ⇒ **共 7 文件，全部在 G4 写权面内**。

##### 4.10.15.1 本层的**独立核实**（不采信转述）

| 项 | 实测结果 |
|---|---|
| **G3 的质疑成立吗** | ✅ **成立** —— 共享层表 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 的 favorite 行原写 `Mode.RAW`（＝「Go 侧没注册 `SetErrorHandler` ⇒ 400 + text/plain + Go 原文」）⇒ **错** |
| **Go 侧 favorite 的真实形状** | **200 ＋ `{"code":5003,"msg":"参数错误","data":null}`** —— 7 个 handler **各自捕获** `httpx.Parse` 的错：<br>· `service/favorite/api/internal/handler/favorite/*.go:18-22`（形态一致）⇒ `httpx.OkJsonCtx` ＋ `Code: favoritecommon.ErrorInvalidParam` ＋ `Msg: GetErrMsg(...)` ＋ `Data: nil`<br>· `service/favorite/common/errmsg.go:16` ⇒ `ErrorInvalidParam = 5003`；`:51` ⇒ 文案「参数错误」<br>· go-zero `go-zero@v1.10.2/rest/httpx/responses.go:56-63` ⇒ `OkJsonCtx` ⇒ `WriteJsonCtx(ctx, w, http.StatusOK, v)` ⇒ **HTTP 200** |
| ⇒ ⇒ **既非 RAW（400＋text/plain）也非 ENVELOPE（200＋5001）** | ⇒ ⇒ **第三种形状：域自定义业务码信封** ⇒ ⇒ 「轴 ②」只按「注册 / 没注册」分**两档不够用** |
| **`SetErrorHandler` 注册点复核** | 全仓 **5 个**：`like/api/likecenter.go:48` · `comment/api/commentservice.go:47` · `follow/api/followcenter.go:53` · `message/api/messagecenter.go:53` · `hot/api/hotcenter.go:53` ⇒ ⇒ **与表的 `envelope` 档一致**；**其余 9 域的「自捕获」命中行数全 0** |
| **Go 侧目录复核**（防「只核了一个域」） | Go api 域目录 = `service/{article,comment,favorite,follow,hot,knowledge,like,message,task}/api` ＋ **`service/user/user/api`**（`httpx.Parse` **8** 处）＋ **`service/user/admin/api`**（**13** 处）⇒ ⇒ 与表的命中数一致 |
| **RAG 侧 favorite 装了吗** | ✅ **装了**（域内自实现，**逐字复刻 Go**）：`FavoriteApiSupport#goFormFirstValue`（`:369-392`）数**非空值**、**跨 key 累计**、超 2048 抛 `IllegalArgumentException("too many form values")`（`:381-383`）；`#parameterMap`（`:428-436`）把 Tomcat 的 `InvalidParameterException` **映射成同一出口** ⇒ ⇒ 对外只呈现 Go 的那一种形态 |
| **L4 活体对拍（两侧同时跑）** | ✅ `bash tools/api-envelope-probe/favorite/run.sh` ⇒ **EXIT=0** ⇒ **Go 真源实测 == `expected.txt` == RAG 真 HTTP 逐字节**（`err_too_many_form_params` = `200` ＋ `application/json; charset=utf-8` ＋ `{"code":5003,"msg":"参数错误","data":null}`）⇒ ⇒ 「favorite 与 Go 逐字同形」是**实测**，不是源码推断 |
| ✅ **G3 的 ③（守门脚本矛盾）** | 矛盾**真实存在**：`tools/difftest/check-tomcat-limits.py:156-159` 的 `FORBIDDEN_FORM_LIMIT_MODE_MODULES = {"service/favorite/favorite-api"}` 要求 favorite **一项都不许声明**（理由 `:334` =「favorite 域不在 global 写权范围 ⇒ 共享层必须零影响」），而共享层表原写 favorite = `RAW` ⇒ ⇒ **两份文件互相打架**<br>⇒ ⇒ **本轮方案使它自然消解**：表改成 favorite = `OFF`（**语义正确**）⇒ ⇒ 与「不许声明」一致 ⇒ ⇒ **`tools/difftest/**` 零改动**（该目录 = **整合者共享面**，本层不写） |

##### 4.10.15.2 落码内容（**零运行时行为变化**）

| 落点 | 改动 | 行为影响 |
|---|---|---|
| `GoStyleFormLimitMode.DomainReachability` | 新增字段 `boolean goSideHandlesParseError`（插在 `goSideParsesForm` 之后）＋ 补 `@param` javadoc | **无**（`Mode` 枚举未动、`parse` 未动；record 加字段 ⇒ 10 个构造点同步改写） |
| `GoStyleFormLimitMode#selfConsistent` | 改成**两条规则**：域自捕获 ⇒ 语义模式必须 `OFF`；否则维持 `OFF ⇔ !goSideParsesForm` | **无**（自洽校验，不参与运行时决策） |
| `SEMANTIC_REACHABILITY` 的 favorite 行 | `(…, true, **true**, Mode.**OFF**, …)` ＋ 证据串补「RAG 侧域内也已自装」 | **无**（目标态表；域侧 yaml 不变 ⇒ 运行期不变） |
| `REGISTERED_DEVIATIONS` | **删 favorite 那条** ⇒ 表变空 | **无**（该表只被 `hasRegisteredDeviation` 查询；无调用方依赖其内容） |
| `Mode.OFF` javadoc | 补「**第二种情形**」（域自捕获） | **无**（注释） |
| 类 javadoc 轴 ② | 改成**两判据** ＋ 轴 ② 表补「域自定义」一行 | **无**（注释） |
| `GoStyleTomcatParameterLimitCustomizer` javadoc | 订正**三处错误陈述**（见 §4.10.15.4） | **无**（注释） |
| `GoStyleFormValues` / `GoStyleFormLimitExceededException` / `GoStyleParamLimitGateTest` / `GoStyleFormLimitModeTest` javadoc | 同源错误陈述（「favorite ⇒ RAW」「favorite 零触碰」）同步订正 | **无**（注释） |
| **守门** `GoStyleFormLimitSemanticCriterionTest` | **重写**（6 条） | **无** |

##### 4.10.15.3 守门与**变异验证**（每条断言都有专属变异）

| 断言 | 专属变异 | 实测打红位置 |
|---|---|---|
| ① 表覆盖全部 api 域 | favorite 的模块名改 `favorite-api-X` | 🔴 `tableCoversEveryApiModule`（＋④⑥） |
| ② 每一行自洽 | favorite 的 `goSideHandlesParseError` 改 `false` / favorite 的 mode 改 `RAW` / task 的 parses 改 `true` / task 的 mode 改 `RAW` | 🔴 `everyRowIsSelfConsistent`（**4** 个变异） |
| ③ task 语义 = OFF | task 的 parses 改 `true` / handles 改 `true` / mode 改 `RAW` | 🔴 `taskIsSemanticallyOff`（**3** 个变异） |
| ④ favorite 语义 = OFF ＋ **不是**模式偏离 | handles 改 `false` / mode 改 `RAW` / parses 改 `false` / 证据串去掉 `5003` / 把 favorite 加回 `REGISTERED_DEVIATIONS` | 🔴 `favoriteIsSemanticallyOffBecauseTheDomainHandlesItItself`（**5** 个变异） |
| ⑤ 刻意保留的偏离表为空 | 把 favorite 加回 `REGISTERED_DEVIATIONS` | 🔴 `noRegisteredDeviationIsKept` |
| ⑥ 查询入口 | `semanticModeOf` 对未知域返回 `OFF` 而非 `null` / favorite 的 mode 改 `RAW` | 🔴 `lookupApiBehaves` |

⇒ **10/10 变异全部按预期打红**，且**失败方法集合与预期逐一吻合**（无多余红、**无编译错误**）⇒ ⇒ 每条断言都有判别力（**不是恒绿空转**）。
⇒ 脚本 `/tmp/g4-g3-axis2-mutations.sh`：每个变异改一处 → 跑守门 → 比对失败方法集合 → 还原 ⇒ 末尾 `SRC_RESTORED_OK`。

##### 4.10.15.4 🔴 **顺带订正的三处错误陈述**（**同源错误**，不订就会误导下一个 agent）

| 位置 | 错在哪 | 改成 |
|---|---|---|
| `GoStyleTomcatParameterLimitCustomizer`（类 javadoc ＋ `customize()` 内注释） | ① 「10 份 yaml 保持一致 ＋ 共享层统一覆盖 ⇒ **无分叉、favorite 零改动**」—— 它把「**yaml 层**一致」当成了「**运行期**一致」；② 「favorite 停在 OFF 是**刻意保留**的已知偏离，不是『已对齐』」 | ① 写清「yaml 层全仓 = 2048，但**运行期**由 `mode` 决定 ⇒ ⇒ **favorite（唯一未声明 `mode` 的域）运行期 = 2048、其余 9 域 = 10001 ⇒ 分叉真实存在**」；② 改成「favorite 停在 OFF 是**语义正确**的（域自捕获 ⇒ 共享层不能装闸门）⇒ 与 Go **逐字同形**」 |
| `GoStyleFormValues`（类 javadoc 的逐域取证表 ＋ `goParseViolation` 的 javadoc） | 把 favorite 与 article/user/task 并列在「**没注册 `SetErrorHandler`** ⇒ 400 + text/plain」那一行 ⇒ **推论错误** | favorite 单列一行「**没注册，但 7 个 handler 各自捕获** ⇒ 拒绝形状由域自己定（200 ＋ {code:5003,…}）」；并补「『没注册』**推不出**『走 400 形状』——**装不装闸门**与**拒成什么形状**是**两条独立的轴**」 |
| `GoStyleFormLimitExceededException`（类 javadoc） | 「其余域（article / user / task / favorite）走 RAW」 | 改成「其余**装闸门**的域（article / user / admin）走 RAW」＋「**两个域收不到它**：task（闸门不开）与 favorite（域自己捕获）」 |

##### 4.10.15.5 🔴 **残留漂移（未销账）＋ 修法方向（本轮只登记 ＋ 转派）**

| 项 | 内容 |
|---|---|
| **残留事实** | favorite 的 form 上限**边界口径**与 Go 不同：**Tomcat 数「总参数值个数（含空值）」，Go 数「非空值个数」** ⇒ ⇒ 总「值」数 **2049~10000** 且非空值 ≤2048 时，**Go 放行 / RAG 拒**（RAG 落 200 ＋ {code:5003}，Go 落 200 ＋ 正常业务结果）⇒ ⇒ 归 **`DRIFT-HOTAPI-030`**（跨域 · 收口在 global）＋ **`DRIFT-FAV-API-001`**（域内） |
| **根因（本层实测定位）** | 共享层把**两件独立的事**耦合在同一个 `mode` 上：①「**装不装** form 值闸门（决定拒绝**形状**）」与 ②「**抬不抬**容器参数上限（决定谁来当**判定权**）」⇒ ⇒ **`OFF` ⇒ 不覆盖容器上限** ⇒ ⇒ favorite（**唯一未声明 `mode` 的域**）**运行期上限 = 2048** ⇒ ⇒ 其余 9 域（都声明了 `mode` ⇒ ≠ OFF）**运行期 = 10001** ⇒ ⇒ **favorite 是唯一停在 2048 的域** ⇒ ⇒ **分叉存在**，且它就是这条漂移的**成因** |
| **L4 实测证据** | 🔴 **探针日志实锤**：favorite 域把容器超限映射成 Go 出口时打出的原文 = `too many request parameters: More than the …` ⇒ ⇒ **Tomcat 的 `max-parameter-count` 确实在 2049 时触发** ⇒ ⇒ **运行期上限 = 2048**（不是 10001）⇒ ⇒ 分支**不是推理，是实测** |
| **候选修法（两条）** | **(a) 域侧**：favorite 自己的 yaml 把 `max-parameter-count` 改成 **10001** ⇒ ⚠️ 与全仓守门 `tools/difftest/check-tomcat-limits.py` 的 `EXPECTED_MAX_PARAM_COUNT = 2048` **冲突** ⇒ ⇒ 需**跨域拍板**（且该脚本 = **整合者共享面** ⇒ 本层不写）<br>**(b) 共享层**：把 ①②**解耦** —— 容器上限**一律**抬到 10001（与 `mode` 无关），形状闸门仍由 `mode` 决定 ⇒ ⇒ **只改共享层一个类，不碰任何域的 yaml** ⇒ ⚠️ 但它会让 **OFF 域的运行期上限 2048 → 10001**（favorite）⇒ ⇒ **跨域可观察行为变更** ⇒ favorite 域的 `FavoriteApiRealTomcatGateTest` 会随之改 ⇒ ⇒ **必须域侧参与**<br>🟢 **(b) 的论证（2026-10-27 升级 · 依据 G2 的 task 域实测）**：**10001 = Go `net/url` 段数上限（10000）+ 1** ⇒ ⇒ 对**两类域都与 Go 对齐** —— ① **解析 query 的域**（favorite 等 9 域）：容器只当「兜底」，**判定权交还给域内/共享层的 Go 语义判据**（数非空值）⇒ 边界与 Go 一致；② **不解析 query 的域**（task）：Go 侧本就没有 2048 上限，**真实上限就是 10000 段** ⇒ ⇒ 10001 正是 Go 的等价物（2048 反而比 Go 严）⇒ ⇒ **⇒ (b) 对 10 个域全部与 Go 对齐，不是「放宽」** |
| **(b) 的唯一可观察变化** | 🔴 **只有 favorite 那一格**：「总「值」数 2049~10000 且非空值 ≤2048」的请求，从 **RAG 拒（200 + {code:5003}）** 变成 **RAG 放行（200 + 正常业务结果）** ⇒ ⇒ **这正是 Go 的行为**（Go 数非空值 ⇒ 放行）⇒ ⇒ **变化方向 = 更对齐 Go** ⇒ ⇒ 但 favorite 域的 `FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError` 断言的正是**旧**行为 ⇒ ⇒ **那一条要由 favorite 域改** ⇒ ⇒ **⇒ 仍需域侧参与 ⇒ 本层不单方面改** |
| **本层为什么不直接改 (b)** | ① 属**跨域可观察行为**变更（favorite 域的响应会变）⇒ 按「共享层缺陷转 G4，但**跨域行为变更必须让域侧参与**」⇒ ⇒ 先**登记 ＋ 转派**（G3 域内 ＋ 整合者的 `tools/difftest/**`）；② 共享层**不能假设**「Go 侧自捕获 ⇒ RAG 侧域内也自装了判据」（那是拿 RAG 实现推断 RAG 实现）⇒ ⇒ 判据必须由**域侧**提供 |
| ⚠️ **⚠️ 本行是「我改了自己的结论」—— 上一轮（写 §4.10.15 初稿时）判断错了** | 🔴 **G2 反馈单（2026-10-27 11:40）实测否掉了本层上一轮的判断**：「task 域改 `off` ⇒ 运行期上限掉回 2048 ⇒ **比 Go 更严**」⇒ ⇒ **这句是错的** ⇒ ⇒ **订正如下**：<br>· **task 域的控制器只有 `@RequestBody`，从不调 `getParameter*`** ⇒ ⇒ **Tomcat 的参数解析是惰性的** ⇒ ⇒ **2048 这条上限对 task 域不可达** ⇒ ⇒ **不会误拒** ⇒ ⇒ **task 域与 Go 一致**（Go 侧 `r.Form` 从不求值 ⇒ 本来就没有 2048 上限）<br>· **实测支撑**：G2 的域内守门 `TaskApiFormLimitReachabilityTest`（真 Tomcat ＋ `HttpURLConnection`）用 **2100 个非空 query 值** 断言 **200 ＋ 正常业务载荷** ⇒ ⇒ **2100 > 2048 却放行** ⇒ ⇒ 惰性成立；且其**变异**（yaml 改回 `mode: raw` ⇒ **4 条红**，含 `400 + Go 原文 too many form values, error: …`）⇒ ⇒ **正反两面都验到了**<br>· ⇒ ⇒ **「OFF ⇒ 运行期 2048」对 task 域无害** ⇒ ⇒ **本层已把给 G2 的那条提醒撤销**（见 §4.10.15.8） |
| 🔴 **于是「分叉」的受害面<b>只有 favorite 一个域</b>** | favorite 域**自己调** `getParameterMap()`（`FavoriteApiSupport#parameterMap` ⇒ `:428-436`）⇒ ⇒ **它会触发 Tomcat 的参数解析** ⇒ ⇒ **2048 对它是可达的** ⇒ ⇒ **只有它**会因「OFF ⇒ 运行期 2048」而与 Go 的边界口径不同 ⇒ ⇒ 其余 9 域要么不解析 query（task）⇒ 不可达，要么模式 ≠ OFF（其余 8 域）⇒ 运行期 10001 |

##### 4.10.15.6 回归口径（**第十次全量** · 核对日期 2026-10-27 · 代码态 `5a954bb`）

| 项 | 值 |
|---|---|
| **命令** | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| **结果** | ✅ **BUILD SUCCESS** |
| **模块数 / 有测试的模块数** | **52 / 29** |
| **用例数** | **2470**（**29 行「模块名 ↔ 数字」明细**逐项求和 ⇒ **非按行序 tail**）⇒ 与第九次**持平**（守门仍是 6 条 ⇒ **用例数守恒**） |
| **failures / errors / skipped** | **0 / 0 / 4**（`favorite-api` skipped 1 · `favorite-rpc` skipped 3） |
| **耗时** | **20:23 min**（11:18:58 起跑 ⇒ 11:39:21 完成） |
| **逐模块明细** | `users-common` 33 · `common` **320** · `admin-rpc` 112 · `admin-api` 129 · `user-rpc` 133 · `user-api` 107 · `article-common` 15 · `article-rpc` **154** · `article-api` 92 · `hot-common` 14 · `hot-api` 96 · `hot-rpc` 96 · `message-common` 21 · `message-rpc` 84 · `message-api` 88 · `like-common` 18 · `like-api` 81 · `like-rpc` 96 · `task-api` 90 · `task-rpc` 114 · `follow-common` 37 · `follow-api` 50 · `follow-rpc` 74 · `favorite-common` 29 · `favorite-api` 97 · `favorite-rpc` 110 · `comment-common` 27 · `comment-api` 45 · `comment-rpc` 108 ⇒ **逐项可复算**（与第九次**逐条一致**） |
| **双侧探针**（同批次） | `tools/api-envelope-probe/favorite/run.sh` ⇒ **EXIT=0**（Go 真源 == `expected.txt` == RAG 真 HTTP **逐字节**）· `run-parity.sh` ⇒ **EXIT=0 · 56 例**（一致 54 · 不一致 0 · 已签字偏离 2）· `run-rpc-timeout-parity.sh` ⇒ **EXIT=0 · 3 例** |
| **三门禁 ＋ 台账门禁** | `check-wave-lane-ownership` / `check-wave-role-model` / `check-wave-coupling` ⇒ **0 / 0 / 0**；`check-ledger-tables` / `check-ledger-selfcheck` / `check-ledger-count` ⇒ 全 **EXIT=0** |
| **写权自证** | 本轮改动**全部**落在 `service/common/**` ＋ 本文件 ⇒ **零域侧改动**（`tools/difftest/**` = 整合者共享面 ⇒ **零改动**） |

##### 4.10.15.7 ✅ **G3 的 ①（「隔离跑 article-rpc 154 条」是否受旧 jar 影响）—— 用本轮全量闭环**

| 项 | 结论 |
|---|---|
| **原问题** | G3 曾用 `mvn -o -pl service/article/article-rpc test`（**不带 `-am`** ⇒ 共享层从 `~/.m2` 取**旧 jar**）跑出「**154 条 0 失败**」⇒ 该结论**可不可信**？ |
| **本层复核方式** | 用**第十次全量**（reactor 内构建 ⇒ **用当前源码**）里的同一模块作对照 ⇒ **`article-rpc` = 154 条 / 0 失败 / 0 errors** ⇒ ⇒ **两个数字**与那次隔离跑**逐条一致** |
| **结论** | ✅ **那次隔离跑的结论成立**（「article-rpc 154 条 0 失败」不因旧 jar 而失效）⇒ ⇒ 旧 jar 的影响面**没有落到 article-rpc 这 154 条上**<br>⚠️ **但纪律不变**：**单模块跑域内测试必须加 `-am`**，否则共享层取 `~/.m2` 旧 jar ⇒ **假红 / 假绿**都可能 ⇒ 本轮已把它立为本层纪律 |
| **⚠️ 为什么不另跑一次 `-am` 隔离跑** | 本轮全量**已经**是「reactor 内构建 ＋ 当前源码」的**更强**对照（覆盖 52 模块 / 2470 条）⇒ ⇒ 再跑一次 `-am` 隔离跑**不增加证据强度**，只是重复 ⇒ ⇒ 结论以全量口径为准（§4.10.15.6 的逐模块明细可复算） |

##### 4.10.15.8 🟢 **G2 的回信（2026-10-27 11:40）—— 本层的 4 条处理**

| 项 | 处理 |
|---|---|
| 🔴 **① 撤销给 G2 的「task 域副作用」提醒（本层上一轮判断错误）** | 本层上一轮给 G2 发过一条 ASCII 提醒：「task 域把 `mode: raw` 改成 `off` ⇒ 运行期容器上限会从 10001 掉回 2048 ⇒ **比 Go 更严**」⇒ ⇒ **该判断已被 G2 的域内实测否掉**（见 §4.10.15.5 的「本行是『我改了自己的结论』」）⇒ ⇒ **本层已发纠正**（并明确「**不要**据此去做额外动作」）⇒ ⇒ ⚠️ **教训**：这条与 memory 里「**结论对不等于推导对**」同族 —— 本层当时是**由 yaml 的值推出运行期值**，却**没验证「那条上限对该域是否可达」**（task 不调 `getParameter` ⇒ 不可达）⇒ ⇒ **「运行期上限不同」≠「行为不同」** ⇒ ⇒ 判定漂移必须再问一句「**这个上限在该域的代码路径上可达吗**」 |
| 🟢 **② 端口段表：落点 = 本文件（G4 写权面只有 `docs/DRIFT/global/vs-common.md`）** | G2 已在三域台账登记本域段（like **21600–21699** · message **21700–21799** · task **21800–21899**），并让 global 建全局表 ⇒ ⇒ **本层不新建文件**（G4 写权面 = `service/common/**` + `service/users/users-common/**` + **本文件**）⇒ ⇒ 全局段表以**本文件为落点**汇总登记：<br>· favorite **21300–21399**（G3 域台账）· 探针 **21400–21599**（⚠️ **原写「本层 `tools/**` 占用」是归因错误** —— 那三个探针是 **favorite 域（G3）**的，见 §4.10.19.6）· like **21600–21699** · message **21700–21799** · task **21800–21899**（G2 域台账）· favorite 脚手架 **21950–21999**（G3 域台账）· OS 临时段 **49152–65535**（**禁占** ⇒ 各域真容器测试必须自选专用段）<br>⚠️ **未落定的格**：users 域（G1）与 article / comment / hot（G3）的段号**本层未核** ⇒ ⇒ **不外推** ⇒ ⇒ 待各域台账补齐后由本层统一登记<br>🆕 **已由 §4.10.19 逐格实测并落定**（**已占 8 段 ＋ 预分配 5 段 ＋ 2 域 N/A** ＋ **新发现 1 条嵌套重叠** ＋ **归因订正 1 条**） |
| 🟢 **③ 工具口径缺口：只登记 ＋ 转整合者（`tools/**` = 整合者共享面 ⇒ 本层不写）** | G2 报：`check-ledger-selfcheck.py` 第 5 层的 `CLASS_RE` 只认 `…Test` / `…GateTest` / `…IT` ⇒ ⇒ **支撑类**（各域的 `RealTomcatPort` 等）**永远无法被点名** ⇒ ⇒ 必然进「未点名」⚠️（**提示级 · 不影响退出码**）⇒ ⇒ 本层**只登记**，改动归整合者 |
| 🟢 **④ FB-04 / FB-05 的回执已发，但 G2 侧仍在等 ⇒ 已重发摘要** | 本层对 FB-04（`853767b`）与 FB-05（`c15dfeb`）**上一轮就发了最终回执**（4 项规格 ＋ 回归口径 ＋ 独立核实）⇒ ⇒ G2 本轮回信仍写「**仍在等你回执**」⇒ ⇒ **两侧消息队列未消化**（G2 侧队列深）⇒ ⇒ 本层**重发一份摘要**（结论不变，不重复长表）⇒ ⇒ ⚠️ 「不重派」指的是**不重派派单**，**回执摘要不属重派** |

#### 4.10.16 🟢 **G1 的补充回执（2026-10-27）—— 槽位权威表 ＋ 一处理由更正 ＋ 零落码**

> **来源**：G1 在槽位协调约束的回执之后补了一条：① 复核槽位表 ② 两个域的实证（`+6` 并列 CpuShedding 无害）③ 对本层的请求（「**不要**把 `HttpMiddlewareFilter` 的 @Order 下调到 +6 或更低；上调（更外层）同样不行 ⇒ **保持 +7**」）。
> ⇒ ⇒ **本轮本层零落码**（只回执 + 台账）⇒ ⇒ G1 的 `9203bcc` 不需要为本层做任何调整。

##### 4.10.16.1 槽位权威表（**共享层真源 = 本层树**，2026-10-27 实测）

| 槽位 | Filter | 落点（`文件:行号`）＝ **`@Order` 注解行**（值真正所在的那行） |
|---|---|---|
| **+2** | `GoStylePathNormalizingFilter` | `service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingFilter.java:70` |
| **+3** | `GoStyleTraceparentFilter` | `…/http/GoStyleTraceparentFilter.java:190` |
| **+4** | `GoStyleMaxConnsFilter` | `…/protection/GoStyleMaxConnsFilter.java:96` |
| **+5** | `GoStyleBreakerFilter` | `…/protection/GoStyleBreakerFilter.java:87` |
| **+6** | `GoStyleCpuSheddingFilter` | `…/protection/GoStyleCpuSheddingFilter.java:63` |
| **+7** | `HttpMiddlewareFilter`（**3s 预算起点**） | `…/spring/HttpMiddlewareFilter.java:37` |
| **+8** | `GoStyleMaxBytesFilter` | `…/protection/GoStyleMaxBytesFilter.java:83` |

> 🆕 **行号精度订正（2026-10-27）** —— 本表原写的 7 个行号（`PathNormalizing:16` / `Traceparent:32` / `MaxConns:20` / `Breaker:22` / `CpuShedding:19` / `HttpMiddleware:20` / `MaxBytes:19`）**全部落在 javadoc 注释行**，不是 `@Order` 注解行 ⇒ ⇒ **点不出那个槽位值** ⇒ ⇒ 与 AGENTS.md §3.3.1「行号指向**消费点**而非**声明点**」相悖 ⇒ ⇒ **已订正为 `@Order` 注解行**（上表）。
> ⇒ ⇒ **内部不自洽是发现它的原因**：§4.10.12 里同一批槽位写的是**对的**行号（`PathNormalizing.java:70`）⇒ ⇒ 两处对不上才暴露出来 ⇒ ⇒ **「同一事实的两处记录不一致」是一条便宜的自查信号**。
> ⇒ ⇒ ⚠️ **这不影响任何结论**（7 档槽位值本身没变 ⇒ 快照守门 §4.10.12 / §4.10.21 的断言依旧成立）⇒ ⇒ **只订正引用精度，不改结论**。

⚠️ **实测方式**：`@Order(Ordered.HIGHEST_PRECEDENCE + N)` **不是**单 token ⇒ 必须**剥注释后解析完整表达式并求值**（单 token 正则会只捞到 `LOWEST_PRECEDENCE` 那一个 ⇒ 清单类结论不得用 grep 命中数）。
⚠️ **G1 报的是 6 档（无 +2）⇒ 不是谁错，是两棵树的共享层版本不同**：`+2` 那条落在 **`9690b04`（G2 FB-01 · A3，共享层）** ⇒ ⇒ **G1 树里还没有它**（实测：`ls` ⇒ no such file）⇒ ⇒ **合并后是 7 档**。

##### 4.10.16.2 🔴 **一处理由更正（结论不变，理由要换）**

| 项 | 内容 |
|---|---|
| **G1 的结论** | 「域内 Filter 的合法窗口 `[+3,+6]`，四槽全被占满 ⇒ 域侧只能**并列 +6**」 |
| **合并后的真实理由** | 域侧可用区间 = **「[+3, 预算起点)」的最后一个槽位（= +6）**，由三条推出 ——<br>① `GoStyleTraceparentFilter` 必须 **≤ +6** ⇒ 域侧必须 **≥ +3**（否则验签落到 traceparent 的 SERVER span 之外 ⇒ 观测面漂移）<br>② **+2 不可用**：它与 `GoStylePathNormalizingFilter` **同槽** ⇒ 同 order 的相对顺序由**容器注册序**决定 ⇒ **不是确定性** ⇒ 域侧不应依赖它<br>③ **+7 = 预算起点** ⇒ 不可用（否则验签落进 3s 预算 ⇒ 域侧的「413 压过 401」不变式会变）<br>⇒ ⇒ **⇒ 结论不变（并列 +6），但「四槽占满」是**他树**的描述 ⇒ 合并后该说法会失效** ⇒ ⇒ 域侧若照「四槽占满 ⇒ 只能并列」去推理，会在合并后误以为「+2 空出来了 ⇒ 可以用」 |
| **这三条已被机器钉住** | ✅ 正是 §4.10.12 落的那台**快照守门**第 ③ 条（`SharedFilterOrderSnapshotGateTest`）：预算起点 ≥ +7 ＋ Traceparent ≤ +6 ＋ `[+3, 预算起点)` 区间**必须仍被占用** ⇒ ⇒ **域侧「并列 +6」由共享层断言保证** ⇒ ⇒ 域侧**不必各自维护槽位表** |

##### 4.10.16.3 「共享层常量会漂」的兜底机制（= G1 自己点出的那个根因）

| 项 | 内容 |
|---|---|
| **根因（G1 指出）** | `d9062f8` 插入 `Traceparent(+3)` 时**整体后移**了其它 Filter 的 `@Order` ⇒ ⇒ 「照抄旧 @Order 数字的推论」必然过期 ⇒ ⇒ 任何域侧推论**必须重算** |
| **本层的兜底** | ✅ **快照守门**（`d56989f`）：7 个 Filter 的 `@Order` 精确值**逐条钉死** ⇒ ⇒ **有人重排 ⇒ 守门必红** ⇒ ⇒ 而**重排前共享层没有任何测试会红** ⇒ ⇒ 这就是「常量会漂」的**机器级**兜底<br>⇒ ⇒ **⇒ 域侧不必自己 grep 维护表**；**要拿权威表就读那台守门的 `SLOT_SNAPSHOT`** |
| ⚠️ **本层回执正文的一处笔误（已当场订正）** | 本层给 G1 的消息里把守门路径写成 `…/src/main/java/com/rag/common/spring/SharedFilterOrderSnapshotGateTest.java` ⇒ ⇒ **错** ⇒ ⇒ 实际在 **`service/common/src/test/java/com/rag/common/spring/SharedFilterOrderSnapshotGateTest.java`** ⇒ ⇒ **台账 §4.10.12 的路径本来就是对的**（`src/test/java/…`）⇒ ⇒ 只错在回执正文 ⇒ ⇒ **已在给 G1 的消息里订正** |

##### 4.10.16.4 「共享层排序桥」**早已落了**（⇒ G1 的域侧副本合并时即可删）

| 项 | 内容 |
|---|---|
| **G1 的说法** | 「G1 三个域侧排序桥（`MybatisPlusOrderingBridgeAutoConfiguration`）等 G4 的共享层桥落码后再删」 |
| **实测** | ✅ **共享层版本早已存在**：`service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java`（⇒ 各域 `*/rpc/config/` 下另有 10 份**域内副本**）⇒ ⇒ 合并后**域内副本被遮蔽**（不是叠加）⇒ ⇒ **域内副本在合并时删即可** ⇒ ⇒ **不存在「还没落的共享层桥」** |
| ⚠️ **别把两件事混谈** | `MybatisPlusOrderingBridgeAutoConfiguration` 管的是 **MyBatis-Plus 拦截器链**的顺序 ⇒ ⇒ 与 **Filter 槽位**（§4.10.16.1）**无关** ⇒ ⇒ 两件事各自的证据与修法都要分开读 |

#### 4.10.17 🟢 **G3 的 C-3 回执（2026-10-27）—— comment 域 6 个业务 span 闭环（本层零落码）**

> **性质**：**回执**，不是派单 —— G3 域内自修（不动共享层、不改 pom）⇒ ⇒ 本层只需**确认 + 更新台账**。

##### 4.10.17.1 G3 落了什么（本层只登记，不复核代码）

| 项 | 内容 |
|---|---|
| **落码** | 域内私有常量类 `service/comment/comment-rpc/.../service/CommentSpans.java`（照仓内先例 `AdminSpans` 的范式）＋ `CommentDomainService` 的 **6 个方法**改成「开 span → 设属性 → 跑业务体」（业务体拆成 `doXxx` 私有方法 ⇒ **span 生命周期覆盖整个 logic** ⇔ Go 的 `defer span.End()`） |
| **Go 侧逐条实测（不是按方法名推）** | 5 个 `Action-Comment-*`（tracer = `comment-rpc`）＋ `GetComment`（🔴 tracer = **`comment.rpc`（带点）**、🔴 **无 `audit.*` 前缀**）⇒ ⇒ **Go 侧三处不一致逐字保留**（tracer 名 / 属性前缀 / 状态语义）⇒ ⇒ ✅ 符合 AGENTS.md §3.1「**不得顺手统一**」 |
| **「Go 记不记这个错误」的判据** | Go 的 `RecordError` 精确落在 `result = "sys_fail"` 的 **15 处**分支，`biz_fail` **一律不记** ⇒ ⇒ 判据取自 Go `service/comment/common/errmsg/errmsg.go:35-48` 的**码段事实**（**5xxx = 系统类** · **1xxx/2xxx = 业务类**）⇒ ⇒ 「业务码 ≥ 5000 ⇒ 系统失败 ⇒ Go 会记」⇒ ⇒ **可复用范式**（见 §4.10.17.3） |
| **刻意不做的三处** | `likeComment` **不加 span**（Go 侧 `tracer.Start` 命中数 = 0 ⇒ 是**对齐不是遗漏**）· 枚举按 Go `EnumStringOf`（未知值 = **数字字符串**，不是 Java 的 `UNRECOGNIZED`）· `GetComment` 的 `page`/`page_size` 记**原始入参**（Go `:72-73`） |
| **验证** | 守门 `CommentSpanWiringTest`（真 Spring 上下文 ＋ 真 PG ＋ 自装 SDK ＋ **收集型 exporter** ⇒ 断言**导出的 span**）⇒ **7/7**，含 4 条判别式断言（业务失败无异常事件 · `GetComment` 有异常事件且状态 ERROR · `GetComment` 的 span 里不出现任何 `audit.*` · `likeComment` 不产生 span）；comment-rpc 整域 **115 条 / 0 failures** |

##### 4.10.17.2 ✅ 本层确认：那条 `GlobalOpenTelemetry.set has already been called` 的 ERROR 是**预期噪声**

| 项 | 内容 |
|---|---|
| **G3 的观察** | 测试日志里有 `GoStyleOtelAgent: GlobalOpenTelemetry.set has already been called` 的 ERROR ⇒ 他判断是「预期噪声」并**没有**当缺陷 |
| **本层的机制出处** | ✅ **本层台账 §2 的 A9 行早已写明**：「全局 OTel 装配点已收口共享层（`GlobalOpenTelemetry.set` **只能调一次**，且**先 `get()` 会让后续 `set()` 抛 `IllegalStateException`** ⇒ **必须 BFPP** ⇒ 理由与 4 个构造器抢跑点已写进 javadoc）」⇒ ⇒ **域侧自装 OTel SDK 时**：共享层的 `set` 因「已被域侧占用」而抛 ⇒ **被 `install` 捕获** ⇒ **继续启动** ⇒ ⇒ **域侧那个 SDK 保持生效** ⇒ ⇒ **⇒ 这恰好是 G3 的守门能断言「导出的 span」的前提**（不是噪声掩盖了问题，而是**设计使然**） |
| **本层的态度** | ✅ **确认「预期噪声」** ⇒ ⇒ 已在 A9 行补上这句，避免下一个 agent 在别处看到同款日志时误判为缺陷 |

##### 4.10.17.3 🟢 可复用范式（**从 G3 这条里抽出来的，值得沉淀**）

> **「Go 记不记这个错误」的判据，不要靠「读 Go 源码感觉」，要落到**可求值的事实**上。**
> G3 的做法：Go 的 `RecordError` 精确落在 `result = "sys_fail"` 的 15 处分支 ⇒ ⇒ 判据取自 `errmsg.go` 的**码段事实**（5xxx 系统类 / 1xxx·2xxx 业务类）⇒ ⇒ 「业务码 ≥ 5000 ⇒ Go 会记」⇒ ⇒ **域侧只要按码段判，就能复刻 Go 的记/不记**。
> ⇒ ⇒ 与 AGENTS.md §5.3e「判据必须是**能在真源上求值的事实**」同款 ⇒ ⇒ 与本层 FB-04 用的判据（`Span.current()` 能否拿到真 span）同族。

##### 4.10.17.4 🟡 本层转给 G3 的**另一条**尚未回复（⇒ 不销账）

| 项 | 内容 |
|---|---|
| **本层上轮转的线索** | 🔴 **comment 域 fact 投递链路上的 `Span.current()`**：`CommentFactDeliveryService.java:148` 的 `Span.current()` 发生在**调度线程**（`CommentFactDispatchScheduler.java:95`）⇒ ⇒ 取到 **invalid span** ⇒ ⇒ `:193-194` 的属性**静默丢弃**；而 Go 侧 comment fact 投递链路**根本不用 span** ⇒ ⇒ **RAG 侧独有的无效代码** |
| **本轮 G3 的回执里没提这条** | ⇒ ⇒ **不销账**（不能因为 C-3 落了就默认这条也修了）⇒ ⇒ **本层已在台账保留** ⇒ ⇒ 回执里**点名问一次**（是已修、未修，还是判定为「不修」） |

#### 4.10.18 🟢 **G1 的补充回执 ③（2026-10-27）—— Go 腿活体证据 + 本层核验 + 一条已知缺口**

##### 4.10.18.1 G1 给的 L4 证据（本层登记）

| 项 | 内容 |
|---|---|
| **Go 腿活体探针** | 探针 **import 真源自己的 `service/common/observability`**（非重写版）⇒ 打印 `nativeTimeoutMiddlewareEnabled=false` ⇒ **go-zero 原生 TimeoutHandler 不在链上** ⇒ 3s **只**由真源自己的 observability 中间件提供（ctx 在 `observability/http.go:53` 自建）⇒ ⇒ **Go 侧「3s 预算不覆盖 JWT 验签」由 L1（读源码）升为 L4（运行时活体）** ⇒ user / admin / follow 三域的 `009②` 结论同步升 L4 |
| **三腿** | ① 有效 token ⇒ `PROBE-A(outer) deadline_set=false` / `PROBE-B(inner) deadline_set=true remaining=3s` ② handler 睡 4s ⇒ **HTTP 503 @ 3.002s**（与 Go `http.go:88` 逐字一致）③ **非法 token ⇒ 401 + 空 body，且 A/B/HANDLER 三个观测点一个都没打印** ⇒ 🔴 **决定性反证**：Authorize 在所有 user middleware 之前 ⇒ 验签在 3s 预算之外 |
| **对共享层的约束（重申并加固）** | ① `HttpMiddlewareFilter` 的 `@Order` **必须保持 +7**（下调到 +6 或更低 ⇒ 吃掉域侧唯一可用槽位；上调 ⇒ 验签落回预算内 ⇒ **两个方向都不行**）② **共享层若新增 Filter ⇒ 必须让出 +4/+5/+6** ③ `@Order` 数字会漂 ⇒ 任何据旧数字的推论都要**重算** |

##### 4.10.18.2 🟢 本层**独立核验**了「Go 侧 413 优先于 401」这个前提（**G1 是对的，本层一度两次判反**）

| 项 | 内容 |
|---|---|
| **结论** | ✅ **Go 侧 = MaxBytes(413) 优先于 Authorize(401)** ⇒ ⇒ **G1 的前提成立** ⇒ ⇒ 「`DRIFT-USERAPI-009 §7` 并入 `SD-USERAPI-002` 签字」的前提**成立** ⇒ ⇒ **本层不反对**（签字档 `docs/Intentional_Deviations/` 归整合者 ⇒ 本层不代签） |
| **硬证据（`go-zero@v1.10.2`，真源 `go.mod` pin 的版本）** | `go-zero@v1.10.2/rest/engine.go:101-116` `bindRoute`：`:103` 取 `ng.chain` → `:105` `buildChainWithNativeMiddlewares(...)`（**native middlewares 在内**，其中 `go-zero@v1.10.2/rest/engine.go:163-164` 挂 `handler.MaxBytesHandler`）→ `:108` `appendAuthHandler(...)`（**`Authorize` 在 `:74/:77`**）→ `:110` 用户自定义 middlewares → `:113` `ThenFunc`<br>⇒ ⇒ **同一条 `chn`** ⇒ ⇒ **`Authorize` 排在 native middlewares 之后** ⇒ ⇒ **413 先于 401** |
| ⚠️ **本层两次判反的教训（可复用）** | ① **行号直觉会骗人**：`:74`（Authorize）看起来早于 `:105`（native middlewares），但 `:74` **在另一个函数里**，真正把它 append 上去的是 `:108`（在 `:105` **之后**）⇒ ⇒ **必须看调用结构，不能看行号大小**<br>② **`chain.Then` 的反向包裹不是「执行顺序反转」**：`go-zero@v1.10.2/rest/chain/chain.go:38-42` 的注释写明「`Append` … as the **last** ones in the request flow … requests in c go m1 -> m2 -> m3 -> m4」⇒ ⇒ `Then`（`:81-91`）的反向包裹**正是为了实现「append 顺序 = 请求流顺序」**⇒ ⇒ ⇒ **推中间件顺序必须同时看「调用结构」与「库的顺序契约」两样** |
| ⚠️ **跨树引用共享层行号必须带基线** | G1 引的 `service/common/observability/HttpMiddleware.java:270/294/304/322` 是**他树**（`align/g1-users-follow` @ `0c2a902`）的行号；**本树**（`align/g4-service-common`）的预算起算/清除在 **`:324` begin / `:356` clear** ⇒ ⇒ **同一文件不同树 ⇒ 行号不同** ⇒ ⇒ 跨树引用必须标基线 commit，否则复核会「找不到」⇒ ⇒ **（与 memory 里「引用库内依赖源码必须带模块限定路径」同族）** |

##### 4.10.18.3 🟡 **已知缺口（显式登记 · 本层落码尝试后撤掉）**：「共享层新增 Filter ⇒ 必须让出 +4/+5/+6」**目前无机器保障**

> 🆕 **本节的缺口已由 §4.10.21 收口（2026-10-27）** —— 但**修法与本节原设想不同**：
> 本节当时倾向「**域内真装配面**」（落 ①），实际落地的是**第三条路** —— **枚举本模块自己的编译产物**
> ⇒ ⇒ 它**不需要容器**、**不需要域侧**、**不依赖 Spring 的类路径扫描** ⇒ ⇒ 本节记的两个死结同时绕开。
> ⇒ ⇒ **本节原文全部保留**（记录当时的判断与撤回理由，不改写历史）⇒ ⇒ **现状以 §4.10.21 为准**：
> ✅ **共享层那一半已闭合**（新增共享层 Filter ⇒ 必红 ⇒ 逼它显式登记槽位）；
> 🔴 **域侧那一半仍未闭合**（「**域侧**新增 Filter ⇒ 不得吃掉 +4/+5/+6」）⇒ ⇒ 仍是域侧落码（真 api 上下文读「实际注册的带 `@Order` 的 Filter」）⇒ ⇒ **本层不代做**。

| 项 | 内容 |
|---|---|
| **缺口** | ①~④ 都**只读快照里那 7 个类**（`readActualSlots()` 与真容器上下文里 `withUserConfiguration(...)` 显式列出的 7 个）⇒ ⇒ **共享层新增第 8 个 Filter ⇒ ① 根本看不到它** ⇒ ⇒ ⇒ **G1 的约束②只有口头约定，没有网** |
| **本层的落码尝试（已撤）** | 试过加第 ⑤ 条：**类路径扫描** `com.rag.common` 下所有带 `@Order` 的 `Filter` 实现 ⇒ 断言「⊆ 快照」⇒ ⇒ **实测只扫到 2/7**（`GoStylePathNormalizingFilter` + `HttpMiddlewareFilter`；**`protection` 包的 4 个与 `http` 包的 `GoStyleTraceparentFilter` 全漏**）⇒ ⇒ **原因未查明** ⇒ ⇒ ⇒ **⚠️ 判据本身不可靠 ⇒ 拿它当守门 = 假绿** ⇒ ⇒ ⇒ **按「宁可不做，不要假绿」撤掉 ⑤**（回退到 4 条，4 条已复跑绿）<br>⇒ ⇒ 附一条**可复用教训**：**类路径扫描（`ClassPathScanningCandidateComponentProvider`）在这个仓的环境下扫不全 ⇒ 不可直接当守门判据** ⇒ ⇒ 引用它时必须先自证「扫到的集合 = 预期的全集」 |
| **可行落法方向（未落）** | ① **域内**：在某个真 api 应用（如 `favorite-api`）的上下文里读「**实际注册的**带 `@Order` 的 Filter」集合 ⇒ ⇒ 那才是**真装配面** ⇒ ⇒ **G3 的域可做**<br>② **共享层**：需要一个**统一装配面**才能一次装全 ⇒ ⇒ ⚠️ 现状是 `GoStyleProtectionConfiguration` **刻意不扫 `com.rag.common`**（`protection/GoStyleProtectionConfiguration.java:53-54`）⇒ ⇒ 那 4 个 protection Filter 靠**各域的组件扫描**注册 ⇒ ⇒ 共享层自己的测试上下文**装不全** ⇒ ⇒ ⇒ **⇒ 落 ①（域内）更现实** |
| **本层的态度** | ⏸️ **本轮不落**（只登记）⇒ ⇒ **不把「看起来能过」的守门塞进仓** ⇒ ⇒ 该缺口**留在台账上**，等有人能做出**不假绿**的实现再落 |

##### 4.10.18.4 另两条（**不是 G4 的**）—— 本层的处置

| 项 | 处置 |
|---|---|
| **`UNRESOLVED-ARPC-002`（E38② 第①层）** | G1 与 G2 的探针口径一致 ⇒ **① 层已闭环** ⇒ ⇒ 🔴 **本层不动 `RpcInterceptor` 的 deadline 计算**（会把对的改错 ⇒ 制造新漂移）⇒ ⇒ **确认不动** |
| **`DRIFT-USERAPI-009 §7`（MaxBytes 相对 observability 的位置）** | 落点是共享层 `GoStyleMaxBytesFilter` 的 `@Order`（**归 G4**），但台账建议**并入 `SD-USERAPI-002` 的范围说明**（签字比改码便宜）⇒ ⇒ ⚠️ 签字档 `docs/Intentional_Deviations/` **归整合者** ⇒ ⇒ **本层只登记、不代签**（§4.10.18.2 已核其前提成立） |

#### 4.10.19 🟡 **端口段表（global 协调 · 本层落定）—— A5 轴的跨域那半**

> **为什么现在做**：§4.10.15.8 ② 登记了「端口段表落点 = 本文件」，但那一格里 users / article / comment / hot 四格写的是「**本层未核**」⇒ ⇒ **不能外推**。
> 本轮把四格**逐格实测**完（2026-10-27）⇒ ⇒ 缺口从「未核」变成「**已核 —— 结论是其中 3 格尚未选段、1 格 N/A**」⇒ ⇒ 再按「**选段表归 global 协调**」（G2 域台账原话：like / message 两域台账都写了这句）**预分配**未选的三格。
> ⇒ ⇒ **本层零落码**（只登记 + 协调 + 1 条转派）—— **端口治理的代码改动在各域侧**（`RealTomcatPort` / `RealGrpcPort` 是**域内测试支撑类** ⇒ 域侧文件 ⇒ 域侧改）。
> ⚠️ **本节不含「已验」字样** —— 段表是**协调产物**，不是行为结论 ⇒ **不构成 A5 的 L3/L4 证据**。

##### 4.10.19.1 口径（**可复算** —— 数字必须能被别的 lane 重跑出来）

| 项 | 值 |
|---|---|
| **计数口径** | 只数**真正使用**：`webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT`（**注解**）⇒ ⚠️ **不是**「grep 命中 `RANDOM_PORT` 的文件数」⇒ **两者差 5**（`RealTomcatPort.java` / `RealTomcatPortTest.java` 自己的注释里就写着 `RANDOM_PORT` ⇒ **命中但不是使用**）⇒ ⇒ **本节第一版差点写成「命中数」** ⇒ 口径必须写死 |
| **基线 commit**（各树实测时点的 HEAD） | G4 `1e1899f` · G1 `b7d724c` · G2 `de696dd` · G3 `40bfa82`<br>🆕 **三棵树的基线都已前移**（2026-10-27 12:19 复核，**逐棵重测 ⇒ 数字未变**）：<br>· G1：`0c2a902..b7d724c` = **只动 `docs/DRIFT/follow/api.md`**（台账）⇒ **重测仍 14 处**（逐域分布逐条一致）；<br>· G2：`9ca4b2c..de696dd` = **只动 `docs/DRIFT/{like,message,task}/**` 三份台账** ⇒ **重测仍 9 处**；另核 like **21600–21699** · message **21700–21799** · task **21800–21899** 三组常量**逐条未变**；<br>· G3：`bb185dd..40bfa82` = **只动 `docs/DRIFT/comment/{rpc,vs-common}.md`**（台账）⇒ **重测仍 14 处**；`RealGrpcPort.java:61/:64`（21400/21499）与 `RealTomcatPort.java:61/:64`（21300/21399）**逐条未变**。<br>⇒ ⇒ **口径表里的三个数字（9 / 14 / 14）仍然成立** ⇒ ⇒ **不是「写死就过期」**（每次基线前移都**重测**过） |
| **核对日期** | 2026-10-27 |
| **复现命令** | `grep -rn "webEnvironment\s*=\s*SpringBootTest.WebEnvironment.RANDOM_PORT" service --include=*.java \| grep -v "/target/"`（**逐树**跑；**不跑 `-o` 不加行号过滤** ⇒ 命中的每一行都是一处使用） |

##### 4.10.19.2 实测清点（**逐域 × 逐树** ⇒ 各树差异是「治理进度」，不是「数据不一致」）

| 域 | 真正用到 `RANDOM_PORT` 的真容器测试 | G4 树 | G1 树 | G2 树 | G3 树 |
|---|---|---|---|---|---|
| **favorite** | 0（已治理） | 0 | 0 | 0 | 0 |
| **like** | 1 | 1 | 1 | **0** ✅ 已治理 | 1 |
| **message** | 3 | 3 | 3 | **0** ✅ 已治理 | 3 |
| **task** | 1 | 1 | 1 | **0** ✅ 已治理 | 1 |
| **users** | 1（`user-api` 的 `GoStyleGrpcClientBreakerE2ETest`） | 1 | 1 | 1 | 1 |
| **article** | 2（`ArticleApiAssemblyWiringTest` · `ArticleApiFormParamLimitTest`） | 2 | 2 | 2 | 2 |
| **hot** | 2（`HotApiRealTomcatGateTest` · `GoStyleGrpcClientBreakerE2ETest`） | 2 | 2 | 2 | 2 |
| **comment** | 0 ⇒ **N/A** | 0 | 0 | 0 | 0 |
| **follow** | 0 ⇒ **N/A** | 0 | 0 | 0 | 0 |
| **common（本层）** | 6 | **6** | 4 | 4 | 4 |
| **合计** | | **16** | **14** | **9** | **14** |

> ⚠️ **G4 树比别的树多 2 处，是本层自己加的**：`protection/GoStyleBreakerRouteTemplateGateTest`（`138b6ef`）+ `http/GoStylePathNormalizingTomcatGateTest`（`9690b04`）⇒ ⇒ **不是「本层抄了别人的写法」**，是本层新增的真容器守门 ⇒ 处置见 §4.10.19.5。
> ⚠️ **G2 树少 5 处 = G2 已治理**（like 1 + message 3 + task 1）⇒ ⇒ **各树的数字不同是正常的** ⇒ ⇒ **段表必须逐域写，不能只写一个总数**。

##### 4.10.19.3 权威端口段表（**已占 + 预分配** ⇒ 全表无重叠）

| 段 | 用途 | 归属 | 状态 | 出处（`文件:行号`） |
|---|---|---|---|---|
| 21300–21399 | favorite-api 真容器测试 | G3 | ✅ 已落码 | `service/favorite/favorite-api/src/test/.../support/RealTomcatPort.java:61` `:64` |
| 21400–21599 | **favorite 探针**（3 个探针共用一段） | **G3**（见 §4.10.19.6 归因订正） | ✅ 已落码 | `tools/api-full-parity-probe/favorite/run.sh:96` · `tools/sql-statement-probe/favorite/run.sh:200` · `tools/grpc-contract-probe/favorite/run.sh:186` |
| 21400–21499 | favorite-rpc 真进程测试 | G3 | 🔴 **与上一行嵌套重叠** ⇒ 见 §4.10.19.4 | `service/favorite/favorite-rpc/src/test/.../support/RealGrpcPort.java:61` `:64` |
| 21600–21699 | like-api 真容器测试 | G2 | ✅ 已落码 | `service/like/like-api/src/test/.../support/RealTomcatPort.java:69` `:72` |
| 21700–21799 | message-api 真容器测试 | G2 | ✅ 已落码 | `service/message/message-api/src/test/.../support/RealTomcatPort.java:69` `:72` |
| 21800–21899 | task-api 真容器测试 | G2 | ✅ 已落码 | `service/task/task-api/src/test/.../support/RealTomcatPort.java:69` `:72` |
| 21950–21999 | favorite-api **脚手架段**（守门测试占端口用） | G3 | ✅ 已落码 | `service/favorite/favorite-api/src/test/.../support/RealTomcatPortTest.java:38` `:41` |
| 22050–22099 | favorite-rpc **脚手架段** | G3 | ✅ 已落码 | `service/favorite/favorite-rpc/src/test/.../support/RealGrpcPortTest.java:45` `:48` |
| **22100–22199** | **favorite-rpc 新段（迁出用）** | G3 | 🟡 **本层预分配 · 域侧未落码** | — |
| **22200–22299** | **users 域**（user-api / admin-api） | G1 | 🟡 **本层预分配 · 域侧未落码** | — |
| **22300–22399** | **follow 域** | G1 | 🟡 **本层预分配**（当前**零使用** ⇒ 预占防将来撞车） | — |
| **22400–22499** | **article 域** | G3 | 🟡 **本层预分配 · 域侧未落码** | — |
| **22500–22599** | **hot 域** | G3 | 🟡 **本层预分配 · 域侧未落码** | — |
| 22600–22999 | 预留 | — | — | — |
| 49152–65535 | **OS 临时段** | — | 🔴 **禁占**（各域真容器测试必须自选专用段） | — |

> ⚠️ **「预分配」≠「已落码」** ⇒ 域侧照 §4.10.19.3 取自己的段、复制 `RealTomcatPort`（favorite 域那份是**模板**）、改两个常量 + 补一条 `bindsToDedicatedPortSegment` 守门即可。
> ⚠️ **comment 域不分配**：实测**零** `RANDOM_PORT`（全部 MockMvc）⇒ ⇒ **N/A，不是「漏了」**（G3 域台账 A5 行自己也写了 N/A）⇒ ⇒ 将来若 comment 域起真容器测试，从 **22600+** 预留段里取。
> ⚠️ **选段不是「随便挑一段空闲的」**：要避开的清单 = 上表**全部已占段** ＋ **OS 临时段** ＋ **部署端口段**（`Infrastructure.md` 的 18xxx / 19xxx / 49xxx —— 与测试段是两套空间，**不冲突**）。

##### 4.10.19.4 🔴 **新发现：favorite 域自己的两段嵌套重叠**（**本层不单方面改 ⇒ 转 G3**）

| 项 | 内容 |
|---|---|
| **现象** | favorite-rpc 的真进程测试段 **21400–21499** **完整落在** favorite 探针段 **21400–21599** 之内 ⇒ ⇒ **两段嵌套** ⇒ ⇒ 段表自相矛盾 |
| **两侧证据** | `RealGrpcPort.java:61` `FIRST = 21400` · `:64` `LAST = 21499`　vs　`tools/api-full-parity-probe/favorite/run.sh:96` `FIRST, LAST = 21400, 21599`（另两个探针同段：`:200` / `:186`） |
| **危害路径（**本层**逐行核过，**不是**「同时挑到同一个端口」）** | ⚠️ **不是**「两方同时挑中同一端口」—— **两侧的挑端口逻辑都有「双绑定自检」**（探针 `free_port()` 同时绑 `0.0.0.0` 与 `127.0.0.1`；`RealGrpcPort.pick()` 同款）⇒ ⇒ **已被占的端口会被跳过** ⇒ ⇒ 真危害是 **TOCTOU 窗口 ＋ connect-only 就绪判定**：<br>① 探针 `free_port()` 在 python 里绑住并 `listen` 后**退出** ⇒ **端口随即被释放**（实测代码路径：`print(p); break` ⇒ 进程退出 ⇒ socket 关闭）⇒ ⇒ **探针真正起服务前有一段「端口空着」的窗口**；<br>② 该窗口里 favorite-rpc 的 `pick()` 可能挑中同一个端口 ⇒ ⇒ 探针的服务起不来，**或**起在别处；<br>③ 探针的 `wait_port()`（`run.sh:127`）**只验「连得上」**（`connect_ex(...) == 0`）⇒ ⇒ **连上 favorite-rpc 的 gRPC 测试服务也算「就绪」** ⇒ ⇒ **探针结论整个不可信**（这正是 favorite 域台账 §9.3 自己写的那句「探针侧更危险」） |
| **为什么仍然算缺陷** | 段表的作用就是**把「可能抢同一个端口的人」压到最少** ⇒ ⇒ favorite-rpc 的测试服务**从「随机第三方」变成「本仓同波次必在的候选」** ⇒ ⇒ **命中概率被显著抬高** ⇒ ⇒ 嵌套段必须拆开 |
| **处置（**两侧都在 G3 写权面内 ⇒ **域侧能自修 ⇒ 本层不单方面改**）** | **把 favorite-rpc 的段迁到本层预分配的 `22100–22199`**（改 `RealGrpcPort.java:61` `:64` 两个常量即可 ⇒ `RealGrpcPortTest` 的脚手架段 `22050–22099` **不动** ⇒ **不与新段重叠**）⇒ ⇒ **探针段 `21400–21599` 不动** ⇒ ⇒ `tools/probe-gate/favorite/check-port-ownership.sh:81` 的段断言**也不用动** ⇒ ⇒ **改动面最小** |
| **为什么不反过来迁探针段** | 探针段被**守门钉死**（`check-port-ownership.sh:81` `21400 <= p1 <= 21599` ＋ `:91` 用 21400 做「被占」样本 ＋ `:95` 断言跳过）⇒ ⇒ 迁探针 = **改段常量 + 改守门** ⇒ ⇒ 改动面更大 ⇒ ⇒ **迁 favorite-rpc 更省**（本层只给结论，改不改由 G3 决定） |
| **本层这一条** | **只登记 + 转派**（AGENTS.md §5.3d「域侧能自修的域侧修」）⇒ ⇒ **本层零落码** |

##### 4.10.19.5 🟢 **共享层自有的 6 处 `RANDOM_PORT` ⇒ 不纳入段治理**（**本层写权面内 · 本层独立判定**）

| 项 | 判定 |
|---|---|
| **事实** | 本层 `service/common/src/test/...` 有 **6** 处 `webEnvironment = RANDOM_PORT`（`GoStyleBreakerRouteTemplateGateTest` · `GoStyleFormLimitEnvelopeGateTest` · `GoStylePathNormalizingTomcatGateTest` · `GoStyleJsonContentTypeCharsetTomcatTest` · `GoStyleParamLimitGateTest` · `GoStyleJsonBodyRawHeaderTomcatTest`）⇒ 其中 **2 处是本层自己本轮加的**（`138b6ef` / `9690b04`）⇒ ⇒ **不是「抄了域侧写法」，是本层新增的真容器守门** |
| **为什么不纳入段治理（三条理由）** | ① **本层没有生产端口** —— `service/common/src/main/resources/` 只有 `META-INF`（无 `application.yml`）⇒ ⇒ 域侧治理的核心风险「端口注入被拿掉 ⇒ 落回 `application.yml` 的**生产端口** ⇒ 与真实部署撞端口」**在本层不存在**；<br>② **零复用收益** —— `service/common/pom.xml` **无 `test-jar` 目标**（实测）⇒ ⇒ 域侧拿不到本层的测试支撑类 ⇒ ⇒ 本层就算写一份 `RealTomcatPort`，**没有一个域能复用**（这正是 G2 台账记的「为什么各域各复制一份」）⇒ ⇒ 只会**多一份要维护的副本**；<br>③ **`RANDOM_PORT` 本身不与已占用端口相撞** —— 它是 **OS 分配** ⇒ ⇒ OS 不会把**已被 Docker 发布占用**的端口再发给本层 ⇒ ⇒ 「落进 OS 临时段」的真实代价（与 Docker 发布端口同段）**在 OS 分配语义下不成立** |
| **结论** | ✅ **共享层不占用测试端口段** ⇒ ⇒ 本层在 §4.10.19.3 的段表里**不占行** ⇒ ⇒ **6 处 `RANDOM_PORT` 保持原样，不改** |
| ⚠️ **边界（不得外推）** | ③ 只在「**端口由 OS 分配**」时成立 ⇒ ⇒ 若将来本层引入「**自己指定固定端口**」的测试 ⇒ ⇒ **必须重开这一格**（那时本层才有生产端口式的撞端口风险） |

##### 4.10.19.6 🟢 **归因订正（本层改自己的结论）**：探针段**不是**「本层 `tools/**` 占用」

| 项 | 说明 |
|---|---|
| **原写法（错）** | §4.10.15.8 ② 写「探针 **21400–21599**（**本层 `tools/**` 占用**）」⇒ ⇒ **归因错** |
| **实测** | 那三个探针的文件头自己写着域：`tools/api-full-parity-probe/favorite/run.sh:2`「**favorite 域**」· `tools/sql-statement-probe/favorite/run.sh:2`「**favorite 域**」⇒ ⇒ 它们是 **favorite 域（G3）的探针**，只是**放在 `tools/**` 目录下** ⇒ ⇒ **不是本层的写权面** |
| **为什么要订正** | 留着「本层占用」的写法 ⇒ ⇒ ① 别的 lane 会以为**共享层**要改这些文件（⇒ 抢写权）⇒ ② 将来若探针段要与 favorite-rpc 段解耦 ⇒ ⇒ 会找错人 ⇒ ⇒ **归属写错 = 协调必错** |
| **订正后** | 探针段 = **G3（favorite 域）** ⇒ ⇒ §4.10.19.4 的「两侧都在 G3 写权面内」这一条才成立 ⇒ ⇒ **转派对象正确** |
| **教训（同族）** | 「目录位置 ≠ 归属」—— `tools/**` 放在仓库根，但**文件可以是某个域的** ⇒ ⇒ 归属要**按文件内容/域标注**判，不按目录判（与「**共享判据 = 依赖度数，不是目录位置**」同族） |

#### 4.10.20 🟢 **U+FFFD 全仓复扫（2026-10-27）—— 本层写权面零命中 ＋ 一处口径差异登记**

> **触发**：§4.10.19 落笔后做**字节级扫描**（`write`/`edit` 之后必做的一步）⇒ ⇒ 顺手把全仓扫了一遍 ⇒ ⇒ **本层写权面（`service/common/**` ＋ `service/users/users-common/**`）零命中**（`0c72969` 那两处修干净后未回潮）⇒ ⇒ 另有一处**与 §4.10.10 记的「13 条」对不上**，按「**写死数字不可复算 ⇒ 不引用**」纪律 ⇒ **只登记本层实测值，不改写历史记录**。

| 项 | 内容 |
|---|---|
| **本层实测（**可复算**）** | **18 个文件 / 47 处**（基线 `1e1899f` · 核对日期 2026-10-27）<br>**复现**：`python3` 逐文件读 `bytes` 判 `b"\xef\xbf\xbd" in data` ⇒ ⚠️ **不用 `grep -rn $'\xef\xbf\xbd'`**（shell 转义 ⇒ 漏报，§4.10.10 已记） |
| **其中落在本层写权面的** | **0 处** ⇒ ✅ `service/common/**` ＋ `service/users/users-common/**` **干净**（`0c72969` 修的两处未回潮）⇒ ⇒ **本层无待办** |
| **其中「无人拥有」的** | **2 处**（与 §4.10.7 ⑥ 已登记的一致）：`tools/errmsg-probe/README.md`（3）· `tools/sql-statement-probe/favorite/compare.py`（3）⇒ ⇒ **仍无人拥有 ⇒ 仍交整合者**（本层只登记、不代改） |
| **其余 16 处的归属** | **各域文件**（like / comment / message / favorite / users / article）⇒ ⇒ **各域写权** ⇒ ⇒ **不是本层待办**；其中 `component-semantic-verification/springboot-semantic-model.md`（5 处）**已在 `docs/DRIFT/users/admin/rpc.md:2492` 登记** ⇒ ⇒ **不是新发现** |
| 🟡 **与 §4.10.10 的「13 条」对不上** | §4.10.10 记的是 G3 于 2026-10-27 的扫描结果（13 条域外 ＋ 2 条落在本层）⇒ ⇒ 与本层实测（18 文件 / 47 处）**口径或时点不一致**（可能是「文件数」vs「处数」· 也可能是扫描范围/文件类型不同）⇒ ⇒ ⚠️ **未查明 ⇒ 不下结论**（**不写「G3 少报了 N 条」** ⇒ 那会把「我的口径」当成「唯一正确的口径」）⇒ ⇒ **本层只登记本层实测值 ＋ 口径 ＋ 基线 ＋ 日期** ⇒ ⇒ **旧记录保留原样**（它是**当时的观测**，改写它等于伪造历史） |
| **本层这一条** | **只登记**（零落码） |

#### 4.10.21 🟢 **G1 约束② 的「共享层那一半」已闭合** —— 换判据：**产物枚举**（不是类路径扫描 · 不是域内真装配面）

> **来源**：§4.10.18.3 登记的已知缺口 ——「共享层新增 Filter ⇒ 必须让出 +4/+5/+6」**没有机器保障**（①~④ 都只读快照里那 7 个类 ⇒ ⇒ 第 8 个共享层 Filter 根本看不见）。那一节当时写「**等有人能做出不假绿的实现再落**」⇒ ⇒ **本轮做出了** ⇒ ⇒ 本节收口。
> ⚠️ **修法与 §4.10.18.3 的原设想不同**：那一节倾向「**域内真装配面**」（落 ①），实际落地的是**第三条路** —— **枚举本模块自己的编译产物** ⇒ ⇒ 它**不需要容器**、**不需要域侧**、**不依赖 Spring 的类路径扫描** ⇒ ⇒ 那一节记的两个死结同时绕开。

##### 4.10.21.1 缺口是什么（精确化）

| 项 | 内容 |
|---|---|
| **原缺口** | 共享层若**新增**第 8 个带 `@Order` 的 Filter ⇒ ⇒ 它可能正好占掉各域 JWT 验签唯一可用的 **+6** 槽位 ⇒ ⇒ 而 `SharedFilterOrderSnapshotGateTest` 的 ①~④ **只遍历 `SLOT_SNAPSHOT.keySet()`** ⇒ ⇒ **新 Filter 对它完全不可见** ⇒ ⇒ **G1 的约束②只有口头约定，没有网** |
| **闭合的是哪一半** | ✅ **共享层那一半**（「共享层**新增** Filter ⇒ 必须显式登记槽位」）⇒ **本层闭合** |
| **仍未闭合的是哪一半** | 🔴 **域侧那一半**（「**域侧**新增 Filter ⇒ 不得吃掉 +4/+5/+6」）⇒ ⇒ 落在**域侧文件** ⇒ ⇒ 需**域侧真装配面**（真 api 上下文读「实际注册的带 `@Order` 的 Filter」）⇒ ⇒ **本层不代做**（§4.10.18.3 的「可行落法方向 ① 域内」**仍然有效**） |

##### 4.10.21.2 判据：为什么「产物枚举」比「类路径扫描」可靠

| 项 | 内容 |
|---|---|
| **类路径扫描为什么不行**（§4.10.18.3 已实测） | `ClassPathScanningCandidateComponentProvider` 扫 `com.rag.common` ⇒ **只扫到 2/7**（`protection` 包的 4 个 + `http` 包的 `GoStyleTraceparentFilter` 全漏）⇒ ⇒ **原因未查明** ⇒ ⇒ 判据不可靠 ⇒ ⇒ 撤回 |
| **产物枚举为什么可靠** | 枚举面 = **本模块真实产出的全部 `.class` 文件**（`target/classes`，用 **code source** 取 ⇒ **与工作目录无关**）⇒ ⇒ **不存在「扫不全」这条路** ⇒ ⇒ 枚举结果 = 产物全集 |
| **实测证据（**关键**）** | 变异 A 的失败消息里，本判据枚举到 **8 个**（7 个快照内 + 1 个变异体）⇒ ⇒ **`protection` 包那 4 个当年被类路径扫描漏掉的，一个不落地被枚举到**（`GoStyleBreakerFilter` · `GoStyleCpuSheddingFilter` · `GoStyleMaxBytesFilter` · `GoStyleMaxConnsFilter`）⇒ ⇒ **7/7 全中** |
| **为什么不需要容器** | ④ 之所以要真 servlet 上下文，是因为「哪个 Filter **注册**在哪个 order」没有公开读法（Boot 4 的 `FilterRegistrationBeanAdapter` 是**包级私有**）⇒ ⇒ 而本判据问的是「本模块**产出**了哪些带 `@Order` 的 Filter」⇒ ⇒ **产物枚举就是那件事的直接读法** ⇒ ⇒ 不碰注册面 ⇒ ⇒ **两条死结同时绕开** |

##### 4.10.21.3 落码（`service/common/src/test/java/com/rag/common/spring/SharedFilterOrderSnapshotGateTest.java`）

| 项 | 内容 |
|---|---|
| **新增断言 ⑤** | 「共享层**产出**的带 `@Order` 的 Filter 全集 **==** 快照」⇒ **双向相等**（**不是** ⊆）⇒ ⇒ 「枚举面失效（枚举到 0 个）」也会红 ⇒ ⇒ **不会退化成恒绿** |
| **枚举根目录** | `GoStyleTraceparentFilter.class.getProtectionDomain().getCodeSource().getLocation()` ⇒ ⇒ **与工作目录无关**（IDE 跑也拿得到）⇒ ⇒ 取不到 ⇒ ⇒ **直接失败**（**不跳过**）⇒ ⇒ 否则这条会退化成「什么都不查」的恒绿 |
| **不带 `@Order` 的 Filter** | **不计入** ⇒ ⇒ Spring 给它 `Ordered.LOWEST_PRECEDENCE`（链尾）⇒ ⇒ **不占任何槽位** ⇒ ⇒ 无需登记 ⇒ ⇒ 本条**有意不管** |
| **守恒断言** | 枚举到的 `.class` 文件列表**不得为空** ⇒ ⇒ 「枚举失效」当场被抓 |

##### 4.10.21.4 变异验证（**2 个** —— 方向不同，不可互相替代）

| 变异 | 做法 | 期望 | 实测 |
|---|---|---|---|
| **A（产品侧 ·「多一个」）** | 在共享层新增第 8 个带 `@Order(+9)` 的 Filter | ⑤ 红，且**红在目标断言上** | ✅ **红**（`everySharedLayerFilterIsRegisteredInSnapshot`）⇒ 失败消息**点名** `GoStyleMutationProbeFilter` ⇒ ⇒ **其余 4 条仍绿** ⇒ ⇒ 「红测试红在目标断言上」成立 |
| **B（判据侧 ·「少一个」）** | 让枚举**漏掉一类** | ⑤ 红（缺一个方向），且**只有 ⑤ 红** | ✅ **红**（5 条里 1 失败 ⇒ ①③④ 仍绿）⇒ ⇒ 证明「少一个」方向**由 ⑤ 独占** |
| ⚠️ **B 的性质（不得夸大）** | — | — | ⚠️ **B 是判据侧变异，不是产品侧变异** ⇒ ⇒ 它证明的是「⑤ 对集合差异敏感」⇒ ⇒ **不是**「产品少一个会被 ⑤ 抓到」⇒ ⇒ **产品侧的「少一个」其实由编译器兜住**（快照引用了不存在的类 ⇒ **编译期就红**）⇒ ⇒ 如实标注 |
| **复原** | 撤掉变异体 | 全绿 | ✅ **5/5 绿**；`service/common` 模块 **321 条 / 0 失败 / 0 errors / 0 skipped** |

##### 4.10.21.5 独立复核（**不依赖守门自己的反射**）

| 项 | 内容 |
|---|---|
| **字节码层复核** | `javap -v` 读 7 个 Filter 的 `RuntimeVisibleAnnotations` ⇒ ⇒ 槽位 = **+2 +3 +4 +5 +6 +7 +8** ⇒ ⇒ 与快照**逐条一致** ⇒ ⇒ **守门的读数与字节码一致** ⇒ ⇒ 不是「守门自己读自己」 |
| ⚠️ **复核判据自己也踩过坑** | 第一版 `javap` 提取脚本匹配 `core/annotation/Order;` ⇒ ⇒ **只匹配到常量池那行**、取不到**用法行**（用法行是 `org.springframework.core.annotation.Order(`，**不带 `;`**）⇒ ⇒ 一度得出「7 个都没有 `@Order`」⇒ ⇒ **那是判据的缺陷，不是被测对象的缺陷** ⇒ ⇒ 修正后取值正常 ⇒ ⇒ **可复用教训：独立复核工具自己出错时，先怀疑工具**（与「工具的缺陷会伪装成被测对象的缺陷」同族） |

##### 4.10.21.6 边界（不得外推）

| 边界 | 内容 |
|---|---|
| **只管「共享层产出」的 Filter** | ⇒ ⇒ **域侧新增** Filter 仍不在覆盖面 ⇒ ⇒ 那一半靠**域侧真装配面**守门（域侧落码） |
| **枚举面 = `service/common` 本模块** | ⇒ ⇒ `service/users/users-common`（同属 G4 写权面）**当前产出 0 个 Filter**（实测）⇒ ⇒ 故不在覆盖面内 ⇒ ⇒ **若将来它引入 Filter，本条须扩面**（做法：在该模块自己的测试里加同一判据 ⇒ **各模块各守自己那份** ⇒ 避免跨模块的路径依赖） |
| **不是 A 轴的行为级证据** | 这是**协调约束**的机器保障 ⇒ ⇒ **不改变**任何 A1~A9 的结论与证据强度 |
| **零运行时行为变化** | 只改了**测试面**（`src/test/java/**`）⇒ ⇒ **产品代码零改动** ⇒ ⇒ 部署行为零变化 |

#### 4.10.22 🟢 **第十一次全量回归口径**（核对日期 2026-10-27 · 代码态 `040a201`）

> **为什么要重跑**：本层**改了共享层测试面**（新增守门 ⑤，`040a201`）⇒ ⇒ 虽然**零生产代码改动**、运行时行为与第十次完全一致，但
> ① **条数会变**（+1）⇒ ⇒ **不能拿第十次的 2470 顶**（写死数字不可复算）；
> ② 「**跑绿 ≠ 对**」⇒ ⇒ 那条新断言**必须在 reactor 里也成立**（它在 `service/common` 模块单跑已绿，但 reactor 里的依赖解析/产物形态可能不同 ⇒ ⇒ **尤其要确认 `target/classes` 的 code source 取法在 reactor 下同样成立**）⇒ ⇒ 用全量确认。

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify`（**全量 reactor**，不是 `-pl` 子集） |
| 结果 | ✅ **BUILD SUCCESS / `EXIT=0`** |
| 模块数 | **52**（Reactor Summary 计数） |
| 有测试的模块数 | **29** |
| 用例数 | **2471**（**逐模块名求和** ⇒ 非按行序 tail）⇒ ⇒ 第十次 **2470** ⇒ **+1** ⇒ ⇒ **恰好等于新增的那 1 条守门 ⑤** ⇒ ⇒ 若不是 +1 ⇒ 说明计数口径出了问题 ⇒ ⇒ **这是本次最关键的一个交叉验证** |
| failures / errors / skipped | **0 / 0 / 4**（`favorite-api` 1 + `favorite-rpc` 3）⇒ ⇒ 与第十次**逐条一致** |
| 耗时 | **22:10 min**（12:12:35 起跑 ⇒ 12:34:45 完成） |
| 逐模块明细（**可逐项复算**） | `common` **321**（320 + 守门 ⑤）· `article-rpc` **154**（与第十次**逐条一致**）· 其余 27 个有测试的模块与第十次**逐条一致** |
| **计数口径纪律（本轮自查）** | ① 模块汇总行（`Tests run: N, Failures: …`）与 `-- in <FQCN>` **测试类行分开算**（后者每测试类一行 ⇒ 会多算）；② 汇总行的三种前缀（`INFO` / `WARNING` / `ERROR`）**全收**；③ **本轮实测「同一模块没有多行汇总」**（29 个有测试的模块 = 29 行模块汇总）⇒ ⇒ **无「同模块累加」导致的多算风险** |
| **双侧探针**（同批次复跑） | `tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0 · 56 例**（一致 54 · 不一致 0 · 已签字偏离 2）· `tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0 · 3 例**（两侧一致）· `tools/api-envelope-probe/favorite/run.sh` ⇒ **EXIT=0**（Go 真源实测 == `expected.txt` == RAG 实测 **逐字节**；500 兜底 OBS 段两侧仍不同 = 已登记差异，非回归） |
| **三门禁**（同批次复跑） | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **台账三门禁** | `check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=0** |
| **大回归窗口协调** | 起跑前 `ps` 实测 **0 个 maven**；`session send` 告知 G2（12:16）⇒ ⇒ **G2 明确让出整个窗口**，并用 `ps` 复核「**唯一占用者是 G4**」⇒ ⇒ 本轮**独占窗口 12:12:35 → 12:34:45** ⇒ ⇒ **零并发** ⇒ ⇒ 无 EXIT=137（OOM-kill）风险 |
| **写权自证** | 本轮改动**全部**落在 `service/common/**`（**仅** `src/test/java/**`）＋ `docs/DRIFT/global/vs-common.md` ⇒ ⇒ **零域侧改动 / 零生产代码改动** |

#### 4.10.23 🟢 **G2 的回执处理（2026-10-27 12:4x）—— 三条订正 ＋ 一张假转派单**

> **来源**：G2 主动来信 7 条 —— ① 核实本层两条共享层变更「零域侧动作」· ② 报告**本层的 FB-01/02/03 回执没投递到它队列** · ③ 报告本轮落码（009② for like/message）· ④ **一张跨 lane 转派单**（009② 也波及 G3 三域）· ⑤ 跨 lane U+FFFD 损坏清单 · ⑥ 一条共享层槽位事实订正 · ⑦ 两条域内自登记。
> ⇒ ⇒ **本节只登记 + 订正 + 转发**；**本轮本层零落码**（写权面无任何代码改动）。

##### 4.10.23.1 ① 本层两条共享层变更：G2 **实测**确认「零域侧动作」

| 变更 | G2 的核实方式 | 结论 |
|---|---|---|
| Redis 断连即拒单（`1fb26b2`） | 三域**无生产侧** `ClientOptions` / `LettuceClientOptions` / `disconnectedBehavior` 自定义 ⇒ 只命中 **task-rpc 的 2 个测试类**里手工 `new LettuceConnectionFactory(...)`（不走 Spring 自动配置 ⇒ 不受影响） | ✅ **like-rpc / task-rpc 无需域侧改动** |
| HTTP breaker 键改「注册期路由模板」（`138b6ef`） | 三域**无路径变量** ⇒ 模板 == 具体 URI ⇒ **零差别** | ✅ 与本层判断一致 |

⇒ ⇒ **本层登记**（这是**实测**不是口头承诺 ⇒ 证据强度高于「域侧保证」）。

##### 4.10.23.2 ② 🔴 **本层的 FB-01/02/03 回执没投递到 G2 队列** ⇒ 本层**重发**

| 项 | 内容 |
|---|---|
| **现象** | G2 收件队列里只有 `9787`（G1 的 009② 同步）· `10239`（G1 回它）· `10375`（本层的知会）⇒ ⇒ **FB-01 / FB-02 / FB-03 的回执不在它队列** ⇒ ⇒ G2 推测「可能发给了某个旧 session id」 |
| **本层复核** | 三条回执的**工作产物在树上可查**（FB-01 = `9690b04` · FB-02/FB-03 = `182fc32`）⇒ ⇒ G2 也**独立复核过**「三条都在」⇒ ⇒ ⇒ **丢的是「消息投递」，不是「工作产物」** |
| **本层动作** | ✅ **重发一份摘要**（结论不变）⇒ ⇒ ⚠️ 「不重派」指的是**不重派派单**（新工作）⇒ ⇒ **回执摘要不属重派** |
| **教训（可复用）** | **「我发了」≠「对方收到了」** ⇒ ⇒ 跨 lane 回执必须**让对方确认收到** ⇒ ⇒ 与「回执要含 4 项」配套：**回执的第 5 项 = 对方确认**（本层此前漏了这一项） |

##### 4.10.23.3 ③ G2 本轮落码（`ce81c5f`）—— 009② for like-api + message-api

⇒ ⇒ 本层登记：其 Go 侧依据**由本层独立复核成立**（见 §4.10.23.4 的 Go 侧核）⇒ ⇒ **共享层侧零动作**（域侧 Filter 落 `+6` ⇒ 共享层槽位表已钉死 ⇒ §4.10.16.1）⇒ ⇒ G2 的 12 条守门 + 变异 6/6 + 三域六模块全量 0/0/0，本层只登记不复核其代码。

##### 4.10.23.4 ④ 🔴 **G2 的转派单 = 假转派单** ⇒ 本层**抢在 G2 消息落地前通知了 G3** ⇒ **不要照它重做**

| 项 | 内容 |
|---|---|
| **G2 的主张** | 009②（JWT 验签被计入 HTTP 预算）**也适用于** article-api / comment-api / favorite-api ⇒ 「配方 G1 已写好，Go 侧依据同源」⇒ 请求本层转告 G3 |
| **G2 的核验方式** | 逐域核 **Go 侧 `routes.go` 有没有 `rest.WithJwt`** ＋ **RAG 侧 api 有没有鉴权类** |
| **🔴 断点在哪** | **只核了真源有 ⇒ 推出「适用」⇒ 顺势当成「待修」** ⇒ ⇒ **没核 RAG 侧的当前状态** ⇒ ⇒ ⇒ **「真源有这条行为」推不出「RAG 侧还欠这条修复」**（RAG 可能早已修好）⇒ ⇒ **与 §3.1 同族、方向相反**：那边的错是「拿现有 RAG 代码当基准」，这边的错是「拿真源当唯一依据、忽略了 RAG 现状」⇒ ⇒ **两边都不能省** |
| **本层独立复核 · Go 侧前提成立** | ① `service/article/api/internal/handler/routes.go` `rest.WithJwt` **×1** · `comment` **×1** · `favorite` **×1** · `hot` **×0**（hot 不适用 ✓ 与 G1 独立判定一致）；② `service/common/observability/http.go:30-34` `DisableNativeRestTimeout` 置 `conf.Middlewares.Timeout = false` ⇒ ⇒ 原生 TimeoutHandler **不在链上**；③ 同文件 `:53` `context.WithTimeout(r.Context(), timeout)` ⇒ ⇒ **预算由共享 observability 中间件起算**（它在 `server.Use` 的用户中间件里）⇒ ⇒ 排在 go-zero `Authorize` **之后** ⇒ ⇒ **验签不计入预算** ⇒ ⇒ **前提成立** |
| **🔴 但 RAG 侧结论不成立** | 三个域**早已落地**：<br>· `article-api/.../auth/GoStyleJwtAuthFilter.java:101` = `@Order(Ordered.HIGHEST_PRECEDENCE + 6)`<br>· `comment-api/.../auth/GoStyleJwtAuthFilter.java:93` = `+6`<br>· `favorite-api/.../auth/GoStyleJwtAuthFilter.java:95` = `+6`<br>⇒ ⇒ 三个拦截器都**只消费** `VERIFICATION_ATTRIBUTE` 并原样重抛 ⇒ ⇒ **验签已在预算之外** |
| **而且 G3 自己的台账已登记为闭环** | `article/vs-common.md:121`（**L-8** ✅）· `comment/vs-common.md:153`（**C-6** ✅）· `favorite/vs-common.md:137`（**L-8** ✅）⇒ ⇒ ⚠️ **它们用的是各自域的编号**（`L-8` / `C-6`），**不是** users 域的 `009②` ⇒ ⇒ ⚠️ **用别的域的编号当全局编号去检索 ⇒ 必然「零命中」⇒ 差点报出一个假缺口** ⇒ ⇒ **本轮我自己踩了这个坑**（已在 §4.10.23.7 留痕） |
| **本层动作** | ✅ **已在 G2 的消息落地前通知 G3**（明确「不要照它重做」＋ 附全部实测行号）⇒ ⇒ **共享层侧零动作**（域侧 Filter 落 `+6` ⇒ 预算起点 `+7` ⇒ 槽位权威表 §4.10.16.1 已钉死 ⇒ 且新断言 ⑤ 保证共享层不会偷偷挪预算起点、也不会新增未登记的 Filter） |
| **第二处不精确（G2 的表）** | 表里三个域都写「3s 预算」⇒ ⇒ **article 域是 70s**（`article-api/application.yml:234` `http-timeout: 70s`）· comment-api 3s（`:185`）· favorite-api 3s（`:157`）⇒ ⇒ **槽位相同（`+6` 在预算起点 `+7` 之前）但时长不同** |

##### 4.10.23.5 ⑤ 🧹 **U+FFFD 跨 lane 清单对账** ⇒ 本层**纠正两处 ＋ 补全一处**

| 项 | 内容 |
|---|---|
| **G2 的清单** | 17 个文件：G1 4 · G3 6 · **G4 2** · 无人拥有 5 |
| **🔴 订正 1：G2 那 2 处「G4」早已修完** | G2 列的 `GoStyleOtelSamplerProtocolGateTest.java`(2) · `FakeServerCall.java`(2) = **本层 `0c72969` 已修**（纯注释 · 零行为变化）⇒ ⇒ 本层 **12:16 字节级复扫 = `service/common/**` 零命中** ⇒ ⇒ ⇒ **G2 的清单在这一点上过时**（基于更早的一次扫描）⇒ ⇒ **不必再来登记** |
| **🔴 订正 2：第 5 个「无人拥有」文件有归属** | `component-semantic-verification/springboot-semantic-model.md`(5) **已在 `docs/DRIFT/users/admin/rpc.md:2492` 登记** ⇒ ⇒ **属 G1 域台账**，不是「无人拥有 ⇒ 整合者」 |
| **🟡 补全：G2 的清单漏了 1 个** | `tools/sql-statement-probe/favorite/compare.py`(3) —— 本层 §4.10.7 ⑥ 已登记为「无人拥有」⇒ ⇒ 17 + 1 = **18** ⇒ ⇒ 与本层 **12:16 实测的 18 文件 / 47 处** **完全对上** ⇒ ⇒ ⇒ **两套清单补全后一致 ⇒ 数字互证** |
| **「无人拥有」实为 4 处** | `tools/errmsg-probe/README.md`(3) · `tools/sql-statement-probe/favorite/compare.py`(3) · `docs/Pitfalls/message/rpc.md`(2) · `docs/Pitfalls/users/user/rpc.md`(1) ⇒ ⇒ ⚠️ 后两处的**内容**分别属 message / users 域，但 `docs/Pitfalls/**` **不在 G2 的机器白名单** ⇒ ⇒ G2 明确「我不能改 ⇒ 请整合者定归属」⇒ ⇒ **同样按「无人拥有」记账**（归属待整合者定）⇒ ⇒ **§4.10.7 ⑥ 已补全** |
| **本层动作** | ✅ 只登记 + 纠正（**零落码**：无一个文件落在本层写权面） |

##### 4.10.23.6 ⑥ 共享层槽位事实：**本层这份已经是 `+7`** ⇒ **无需订正**

| 项 | 内容 |
|---|---|
| **G2 的顾虑** | 三域台账里共享层槽位数字曾被记成 `HttpMiddlewareFilter @Order(HIGHEST_PRECEDENCE+5)` ⇒ ⇒ 请求「共享层那份若也写着 +5，请由写权方订正」 |
| **本层核** | **本层台账写的就是 `+7`**（§4.10.16.1 槽位权威表 ＋ §4.10.12 独立核实段：Traceparent **+3** · MaxConns **+4** · Breaker **+5** · CpuShedding **+6** · HttpMiddleware **+7** · MaxBytes **+8** ＋ PathNormalizing **+2**）⇒ ⇒ 与真源实测一致（`javap` 字节码层复核，§4.10.21.5）⇒ ⇒ **共享层这份无需订正** ⇒ ⇒ **已回信告知 G2**（免得他继续挂一个不存在的待办） |
| **🟡 顺带修掉本层自己的一处引用精度缺陷** | 本层 §4.10.16.1 的 7 个行号原本**全落在 javadoc 注释行**（`PathNormalizing:16` / `Traceparent:32` / `MaxConns:20` / `Breaker:22` / `CpuShedding:19` / `HttpMiddleware:20` / `MaxBytes:19`）⇒ ⇒ **点不出那个槽位值** ⇒ ⇒ 已订正为 **`@Order` 注解行**（`:70` / `:190` / `:96` / `:87` / `:63` / `:37` / `:83`）⇒ ⇒ **结论未变**（7 档值本身没变 ⇒ 快照守门断言依旧成立）<br>⇒ ⇒ **发现它的原因 = 内部不自洽**（§4.10.12 里同一批槽位的行号是对的）⇒ ⇒ **「同一事实的两处记录不一致」= 一条便宜的自查信号** |

##### 4.10.23.7 ⑦ 本层这轮自己的三处留痕（**结论对 ≠ 推导对**）

| 项 | 内容 |
|---|---|
| **🔴 差点报出的假缺口** | 我先按 users 域的 ID（`009②`）去 grep G3 三个域的台账 ⇒ **零命中** ⇒ ⇒ 差点写成「G3 域台账里 009② 一处都没登记」⇒ ⇒ **实际上三个域都登记了**（`L-8` / `C-6` / `L-8`）⇒ ⇒ **错在「把别的域的编号当全局编号」** ⇒ ⇒ **域内编号不是全局编号 ⇒ 跨域检索必须先找该域自己的编号** ⇒ ⇒ 与「共享判据 = 依赖度数，不是目录位置」同族：**编号的归属与判据一样，不能想当然** |
| **🟡 差点跟着 G2 的表转发** | 若直接转发 ⇒ ⇒ G3 会照一条**已修**的项**再做一遍** ⇒ ⇒ 那会把对的改错 ⇒ ⇒ **本层在 G2 的消息落地前就通知了 G3**（附实测行号）⇒ ⇒ **成本 = 一次独立核（4 次 grep ＋ 2 次 javap ＋ 1 次拦截器读码）** |
| **🟢 已发给 G3 的内容（摘要）** | ① 「不要照 G2 那张单子重做」＋ 三域 `GoStyleJwtAuthFilter` 的 `@Order(+6)` 行号 ② G3 三域台账的闭环行号（`article:121` / `comment:153` / `favorite:137`）③ Go 侧前提本层已独立核实成立 ④ **article 的预算是 70s 不是 3s** ⑤ 残留 = 「Filter 未命中 ⇒ 就地验签 ⇒ 落回预算内」（域内代码已用 WARN 自登记）⑥ 共享层侧**零动作**（槽位权威 ＋ 新断言 ⑤）⑦ 另提 article-api 的域内 `GoStylePathNormalizingFilter` 副本（**遮蔽**共享层那份）⇒ 合并时删 |

##### 4.10.23.8 🟢 **FB-02 的域侧那半已闭环**（G2 已改）＋ 本层台账一处过期陈述的订正 ＋ 一处转义残留自修

| 项 | 内容 |
|---|---|
| **G2 三域的 `mode` 现状（**本层实测，G2 树 `de696dd`**）** | `service/like/like-api/src/main/resources/application.yml:125` = `mode: envelope`　·　`service/message/message-api/src/main/resources/application.yml:152` = `mode: envelope`　·　`service/task/task-api/src/main/resources/application.yml:225` = **`mode: "off"`（带引号）**<br>⇒ ⇒ 与共享层语义表**逐条一致**：本层 `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:213`（like = `Mode.ENVELOPE`）· `:216`（message = `Mode.ENVELOPE`）· `:219`（task = `Mode.OFF`）⇒ ⇒ ⇒ **FB-02 的域侧那半已闭环** |
| **task 那处的写法要点** | **必须带引号** —— YAML 1.1（SnakeYAML）把**未加引号的** `off` 解析成**布尔 false** ⇒ ⇒ `@Value` 拿到字符串 `"false"` ⇒ ⇒ 共享层 `parse` 判非法值 ⇒ ⇒ **启动期 `BeanCreationException`**（G2 在 yaml 注释里写了这次实测的报错原文）⇒ ⇒ **FB-05 的修法（`parse` 接受 `"false"` 这个精确别名）与此不冲突**：带引号是域侧的正解，别名只是让「照文档写 `off`」不会炸 |
| 🟡 **本层台账的过期陈述已订正（三处，逐处性质不同）** | ① **§2 的 A3 段**（**当前状态**陈述）⇒ 「task 的 yaml 的 `raw` 是一条**待修漂移**（G2 域内改 yaml）」⇒ ⇒ **改为「G2 域内已改（`application.yml:225` = `mode: "off"`）⇒ 合并后本格即闭环 ⇒ ⚠️ 本层树合并前仍见旧值 = **树间差异**，不是漂移**」<br>② **§4.6 FB-02 段**（**派单当时的记述**）⇒ ⇒ **按「不改写历史」只加一条指路**（标注已闭环 ＋ 指向本节）⇒ ⇒ **原文一字未动**<br>③ **§4.10.15 的残留漂移表** ⇒ 补 G2 树改好的**行号** ＋ 「合并前本层树见旧值 = 树间差异」的措辞 |
| 🔴 **附带修掉本层台账的 4 处「字面反斜杠」残留** | 本层早期用工具写文件时留下的 `\〈反斜杠〉+双引号 的转义残留（`mode: "off"` 共 4 处，分布在 2 行）⇒ ⇒ **markdown 能正常显示**（`〈反斜杠 + 双引号〉` 渲染成 `"`）⇒ ⇒ **肉眼看不出来** ⇒ ⇒ ⚠️ **本节刻意保留 1 处「反斜杠 + 双引号」的组合** —— 那是**扫描命令的 pattern 本身** ⇒ ⇒ 与「故意保留的 U+FFFD」**同款道理**（它是**被测对象**：改了就废掉这条扫描说明）⇒ ⇒ **口径 = 除这 1 处外全清**（本层其他台账文件亦同）⇒ ⇒ 但**源码是脏的** ⇒ ⇒ 扫法：`grep '\\\"'`（注意：**`grep` 会假阴性的坑见 §4.10.20 —— U+FFFD 那次**；本次用 python 逐行 repr 定位 ⇒ 计数权威）<br>⇒ ⇒ **修法 = 纯 ASCII 替换**（`〈反斜杠 + 双引号〉` → `"`）⇒ 改完 U+FFFD = 0 ＋ UTF-8 严格解码通过 ⇒ ⇒ **零行为变化**（纯文档） |
| **本层这一条** | **只登记 + 订正文档**（**零落码**）⇒ ⇒ **代码态仍是 `040a201`** ⇒ ⇒ 第十一次全量（2471 条）口径**仍然有效**，不需重跑 |

#### 4.10.24 🟢 **G1 的 Redis 收编反馈单**（2026-10-27 13:0x）—— 独立复核：**五份**副本（不是三份）· 共享层**零代码改动** · 收编 = 删 5 份

> **G1 的原话**：「收编时请删掉这**三份**域内副本（user-api / admin-api / follow-api）」⇒ ⇒ **本层不采信这个数字**，到 G1 树（HEAD `b7d724c`）**实测**。

##### 4.10.24.1 实测：域内副本是 **5 份**，且**代码逐字一致**

| # | 副本路径（G1 树 `b7d724c`） | 注解 | `@Bean` 方法名 | body |
|---|---|---|---|---|
| 1 | `service/users/user/user-api/src/main/java/com/rag/users/user/api/config/RedisDisconnectedBehaviorConfig.java` | `@Configuration` | `goStyleRedisDisconnectedBehavior` | `builder -> builder.disconnectedBehavior(REJECT_COMMANDS)` |
| 2 | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/config/RedisDisconnectedBehaviorConfig.java` | `@Configuration` | 同上 | 同上 |
| 3 | `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/config/RedisDisconnectedBehaviorConfig.java` | `@Configuration` | 同上 | 同上 |
| 4 | `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/config/RedisDisconnectedBehaviorConfig.java` | `@Configuration` | 同上 | 同上 |
| 5 | `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/config/RedisDisconnectedBehaviorConfig.java` | `@Configuration` | 同上 | 同上 |

⇒ ⇒ **5 份的「非 javadoc 代码」逐字一致** ⇒ ⇒ 与共享层那份
（`service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java`
的 `goStyleRedisClientOptionsCustomizer`）**只差 bean 方法名** ⇒ ⇒
⇒ **删除 = 纯删除、零行为变化**（两边都只设 `disconnectedBehavior`、都**不碰** `timeoutOptions`）。

⇒ ⇒ **共享层那份的「目标态」已被机器钉住**（`service/common/src/test/java/com/rag/common/redis/GoStyleRedisClientOptionsGateTest.java` 3 条：
`REJECT_COMMANDS` / **恰好一份**且名为 `goStyleRedisClientOptionsCustomizer` / `timeoutOptions` 未被改写）
⇒ ⇒ **共享层零代码改动** ⇒ ⇒ **删除动作在 G1 写权面 ⇒ G1 做**（本层不代删）。

##### 4.10.24.2 🔴 G1 删副本时的**连带必改项**（本层实测清单，防编译断裂）

> ⚠️ **本小节的「位置」列全部是跨树引用**（G1 树）⇒ ⇒ **在本树按路径查会零命中** ⇒ ⇒ 「零命中」≠「不存在」
> ⇒ ⇒ **复核时必须带树 + commit**：`git -C <G1 树> show b7d724c:<path>`（**行号只在那个 commit 上有效**）。
> ⇒ ⇒ **本层实测**：本树只有 `user-api` 的那份副本与守门 ⇒ ⇒ **其余 4 份副本与 4 份守门是 G1 第十四轮 `0c2a902` 新增的** ⇒ ⇒ 本树 `find` 只命中 1 份（⇒ **本层自己踩了一次「零命中 ⇒ 不存在」的坑** ⇒ 由此才有 §4.10.29.6 那条纪律）。

| 项 | 位置（G1 树 `b7d724c`） | 为什么必须一起改 |
|---|---|---|
| 🔴 **2 处 `@Import`** | `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcRedisDisconnectedFastFailTest.java:159` · `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcRedisDisconnectedFastFailTest.java:170` | 删掉 `RedisDisconnectedBehaviorConfig` 类 ⇒ **`@Import` 指向不存在的类 ⇒ 编译断裂** ⇒ ⇒ 必须连带处理<br>🔴🔴 **本行建议已订正（G1 实测 ＋ 本层独立核 · §4.10.29.1）**：**不是「删」，是「换成 `@ImportAutoConfiguration(GoStyleRedisClientAutoConfiguration.class)`」** ⇒ ⇒ 理由：那 2 份 rpc 守门的上下文是 **`@SpringBootConfiguration`**（**不含** `@EnableAutoConfiguration`）⇒ ⇒ **`AutoConfiguration.imports` 清单根本不会被读** ⇒ ⇒ 只删 `@Import` ⇒ 容器里 customizer 数量 = **0** ⇒ ⇒ 守门**静默测不到任何断连策略**（这类「测不到」的绿测试比没有测试更危险）⇒ ⇒ **api 侧连带项 = 零**（3 份 api 守门用 **`@SpringBootTest`** 引导真实应用类 ⇒ 清单会被读 ⇒ 注解都不用改 —— 本层在 G1 树逐份核过注解行） |
| 🟡 **3 个域测试的 javadoc 提及该类名** | follow-api `FollowApiRedisDisconnectedFastFailTest.java:90` / `:132` / `:328` · admin-api `AdminApiRedisDisconnectedFastFailTest.java:86` / `:128` / `:324` · user-api `UserApiRedisDisconnectedFastFailTest.java:80` / `:122` / `:310` | 指向**不存在的类** ⇒ 后人按注释找不到文件 ⇒ 须改措辞 |
| 🔴 **域侧变异验证会失效** | 5 个 `*RedisDisconnectedFastFailTest` | 变异的前提是「改域内副本」⇒ **副本删掉后这个前提消失** ⇒ ⇒ 变异必须改到**共享层那份** ⇒ ⇒ 而共享层那份在**仍有副本时是被遮蔽的** ⇒ ⇒ 删副本后共享层那份才真正生效 ⇒ ⇒ **域侧守门不能再靠「变异自己的副本」自证**（要么变异点迁到共享层（跨 lane），要么由共享层自己的守门承担）⇒ ⇒ **这是收编的真实代价，必须登记，不能装作「删了就完事」** |

##### 4.10.24.3 🟢 两个故障注入坑（G1 反馈单给出 · **本层已独立核过接口面**）

| 坑 | 事实 | 出处 |
|---|---|---|
| **必须 FIN，不能 `container.stop()`** | `container.stop()` 发的是 **RST**（强杀）⇒ 测不出「排队」分支；线上真实场景（`docker stop`）是**优雅退出 ⇒ FIN** ⇒ ⇒ 须**先关监听口 + 再对已建立的连接半关闭** | **G1 树 `b7d724c`** `service/users/user/user-api/src/test/java/com/rag/users/user/api/UserApiRedisDisconnectedFastFailTest.java:88-105` / `:280-291` / `:331-333`（本层读原文核实）<br>🟢 **本树等价行号**（同一机制 · **两树版本不同 ⇒ 行号不同**）：**`:102`**（FIN/RST 说明 javadoc）· **`:343`** / **`:367`**（`shutdownOutput()` 半关闭） |
| **`LettuceConnectionFactory` 没有 `getClientOptions()`** | 只能走 `getClientConfiguration().getClientOptions()`（`orElse(null)`） | 同上（**G1 树 `b7d724c`** `:225` / `:245`）<br>🟢 **本树等价行号**：**`:206`**（`connectionFactory.getClientConfiguration().getClientOptions().orElse(null)`） |

##### 4.10.24.4 🟡 「同机制不同形态」的准确含义（**不推翻**「删除 = 零行为变化」）

G1 的 `0c2a902`（第十四轮）标题里的「**同机制不同形态**」指的是 **Redis 断连漂移在 api 侧与 rpc 侧的「后果」形态不同**
（`DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013`）：**api 侧后果 = 503**；**rpc 侧后果 = 命令排队挂到 `commandTimeout: 3s` ≫ `rpc-timeout: 2s` ⇒ RPC 先超时 ⇒ 客户端看到 1015「服务繁忙」**（而 Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」`）
⇒ ⇒ **它说的是「后果」的形态，不是「断连副本」本身的形态** ⇒ ⇒ **5 份副本代码逐字一致**这条实测结论**不受影响** ⇒ ⇒ **删除仍是零行为变化**。

##### 4.10.24.5 本层动作（**只 1 处 javadoc 订正 · 零运行时行为变化**）

| 项 | 内容 |
|---|---|
| 🟢 **订正共享层 javadoc 的「三份」** | `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java` 的类级 javadoc「本仓现状：**三份**域内副本」⇒ ⇒ **改为「五份」并列出 5 个模块名** ⇒ ⇒ **原句是**「共享层自己的事实描述」**写错了 ⇒ 会误导下一个 agent 只删 3 份 ⇒ 留下 2 份仍遮蔽共享层** ⇒ ⇒ 必须改 |
| **落点** | `…/redis/GoStyleRedisClientAutoConfiguration.java:77-79`（`@Bean` javadoc 上方那段的末行） |
| **零运行时行为变化** | 纯注释 ⇒ ⇒ **代码态不变**（本节 §4.10.25 的落码才是代码态变更） |

---

##### 4.10.24.6 🟢 **受益面：本层此前只写了「要删的 5 份」，漏了「已经在受益的 5 个」**（G2 订正 · 本层实测复核）

⇒ **G2 的订正**（本层已独立复核，**成立**）：共享层那份 customizer **不只**影响「有域内副本」的域 ⇒ ⇒ 像 like-rpc / task-rpc 这种**自带 `spring-boot-starter-data-redis` 且无域内副本**的组件 ⇒ ⇒ **共享层那份本来就命中** ⇒ ⇒ 对它们是**净收益**（断连时命令从「挂 60s」变「1ms 拒单」）⇒ ⇒ ⇒ **不是「零行为影响」，是「零代码改动、但有行为收益」**。

⇒ **本层独立实测（不采信转述）** —— 生产代码真在用 Redis 的组件共 **10** 个：

| 分类 | 组件 | 共享层那份是否命中 |
|---|---|---|
| **有域内副本 ⇒ 共享层被遮蔽 ⇒ 收编 = 删副本** | user-api · user-rpc · admin-api · admin-rpc · follow-api（**5**） | ❌ **被遮蔽**（`@ConditionalOnMissingBean` ⇒ 域内副本在场 ⇒ 共享层那份**根本不创建**） |
| **无域内副本 ⇒ 共享层直接命中 ⇒ 已在受益** | comment-rpc · hot-rpc · like-rpc · task-rpc · favorite-api（**5**） | ✅ **命中**（`AutoConfiguration.imports` 注册 ⇒ 这 5 个都是 `@SpringBootApplication` ⇒ 自动配置生效面全覆盖） |
| 有 redis starter 但生产代码不用 Redis | task-api | — （不构成受益面） |

⇒ ⇒ **口径订正**：本台账此前（§4.9.9 的落码表 ＋ §4.10.24 的收编表）**只强调了「G1 域要删 5 份」** ⇒ ⇒ **漏写了「另外 5 个组件已经在受益」** ⇒ ⇒ **本节补齐**（**不删旧段，只加指路**）。
⇒ ⇒ **证据分层**：本层腿 = **静态实测**（10 个组件的 pom + 生产代码 + `@SpringBootApplication` + 自动配置清单）· **域侧运行证据 = G2 提供**（like-rpc / task-rpc 断连 ⇒ 命令从「挂 60s」变「1ms 拒单」）⇒ ⇒ 两者**互为两侧** ⇒ ⇒ **合并按弱侧 = L3** ⇒ ⇒ **不写 L4**。

---

#### 4.10.25 🔴🟢 **G3 反馈：favorite 容器上限走 (b) 共享层解耦** ⇒ **已落码**：容器上限的抬升与闸门的有无**解耦**

> **G3 的答复**（提交 `cd676f2`）：**(b) 共享层解耦，本域零改动**；并给了一条**实现要求**：
> 「容器上限的抬升必须对所有域**无条件**生效，OFF 只该关掉应用层的 Go 风格判定」。

##### 4.10.25.1 本层的独立核（**不采信转述**，三处关键事实）

| 事实 | 本层核法 | 结论 |
|---|---|---|
| Go 的 query **段数**上限 | 真源 Go 1.25 `net/url/url.go:1001` `defaultMaxParams = 10000`（共享层 javadoc 已引，本层沿用同一出处） | ⇒ **10001 = 段数上限 + 1**（既不是 2048，也不是不限）⇒ ⇒ **抬到 10001 对两类域都是对齐** |
| 🔴 `check-tomcat-limits.py` 校的是**哪一层** | 读脚本（`tools/difftest/check-tomcat-limits.py:105` `EXPECTED_MAX_PARAM_COUNT = 2048` ＋ 逐 api 模块读 `application.yml`） | ⇒ **yaml 层**（静态）⇒ ⇒ ⇒ **本次改动 yaml 一字不动 ⇒ 静态门禁不会红 ⇒ 不需要整合者动手**；<br>⚠️ 该脚本还对 favorite-api 的 `mode` 属性做**反向断言**（存在即红）⇒ ⇒ **不新增 favorite 的 mode 声明**（也不需要） |
| G3 的实现要求是否成立 | 读 `GoStyleTomcatParameterLimitCustomizer` + `GoStyleFormValueLimitInterceptor` 两处 | ⇒ **成立**：旧实现把「装不装闸门」（拒绝形状）与「抬不抬容器上限」（判定权归谁）**耦合在同一个 `mode` 上** ⇒ ⇒ 拦截器那侧**已经**按 mode 分支（`mode == OFF ⇒ return true`）⇒ ⇒ **只需把 customizer 那侧解耦** |

##### 4.10.25.2 落码（生产侧 · **A3 轴**）

| 项 | 内容 |
|---|---|
| 🔴 **容器上限无条件化** | `service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java` ⇒ **删掉** `if (mode == OFF) { return; }` ⇒ ⇒ **一律抬到 10001** |
| 🟢 **构造函数改为只校验** | 同一个类的构造函数：`GoStyleFormLimitMode.parse(mode)` **只做校验、不再保存** ⇒ ⇒ **「非法 mode 值 ⇒ 构造期炸」这条 fail-fast 纪律不因解耦而丢**（真判定在拦截器那侧） |
| 🟢 **语义 javadoc 同步** | `…/http/GoStyleFormLimitMode.java`（`Mode.OFF`：「不装闸门、也不改容器上限」⇒ 改为「**不装闸门（但容器上限照抬）**」＋ 两轴解耦说明）· `…/http/GoStyleFormLimitModeTest.java`（类 javadoc） |
| 🟢 **新增守门** | `service/common/src/test/java/com/rag/common/http/GoStyleParamLimitOffModeGateTest.java`（**3 条**）：① 运行期 connector = **10001**（off 模式）② 活体：2049 个非空值 ⇒ **200**（容器不拒 + 闸门没装）③ 容器里那个**真的**闸门 bean 确实是 `OFF` 且原样放行 |

##### 4.10.25.3 影响面（**按域实测**，不是按推理）

> **实测方法**：逐个 api 模块读 `application.yml` 的 `rag.http.go-form-limit.mode` 与 `server.tomcat.max-parameter-count`
> ⇒ **10 个 api 模块的 yaml 层 `max-parameter-count` **全部**是 `2048`（本层**一字未动** ⇒ ⇒ **静态门禁 `check-tomcat-limits.py` 不会红**）。

| 域 | `mode`（逐模块实测） | 改前运行期 | 改后运行期 | 可观察变化 | 实测 |
|---|---|---|---|---|---|
| article / user-api / admin-api | `raw` | 10001 | 10001 | **零** | 本层不动这些域的 yaml ⇒ 运行期本来就是 10001 |
| comment / follow / hot / like / message | `envelope` | 10001 | 10001 | **零** | 同上 |
| **task** | ⚠️ **树间差异**：**本层树**（`a282012` 基线）= **`raw`** ⇒ 运行期**本来就**是 10001 ⇒ **零变化**；**G2 树**已改为 `mode: "off"`（`service/task/task-api/src/main/resources/application.yml:225`）⇒ **合并后** 2048 → 10001 ⇒ **仍零可观察变化**（上限不可达） | 10001（本层树） | 10001 | **零** | ✅ **task-api 全绿**（上限不可达：Go 侧 `r.Form` 从不求值 ＋ 控制器只 `@RequestBody`）⇒ ⇒ 抬上限**打不到任何可观察面** |
| **favorite** | **未声明** ⇒ 缺省 `off` | 2048 | **10001** | 🔴 **唯一一格**：总「值」数 2049~10000 且非空值 ≤2048 ⇒ **Go 放行 / RAG 由「拒（200+5003）」变「进业务层」** ⇒ ⇒ **差 1 闭合** | ✅ **实测红 1 条**（见下） |

> 🔴 **本层自己改自己的一处前提（留痕）**：提交 `cd893d1` 的说明里写「**task（`mode: "off"`）**：2048 → 10001」⇒ ⇒ 那个 `mode` 值是 **G2 树**的 ⇒ ⇒ **本层树**（`a282012` 基线）里 task 的 `mode` 是 **`raw`** ⇒ ⇒ **「task 零可观察变化」这个结论不变**（两种 mode 下上限都不可达）⇒ ⇒ 但**前提要写准** ⇒ ⇒ 本表已按实测改写。

##### 4.10.25.4 🔴 实测：favorite 域红的**只有那一条**，且**新值就是 Go 的形态**

| 项 | 实测值 |
|---|---|
| 红的那一条 | `service/favorite/favorite-api/src/test/java/com/rag/favorite/favorite/api/FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError`（favorite-api 共 **97** 条 / **红 1** / skipped 1） |
| 旧值（改前） | **200 + `{code:5003, msg:"参数错误"}`**（容器在第 2049 个条目就拒 ⇒ 域内 `FavoriteApiSupport#parameterMap` 把它映射成 Go 的解析失败出口）⇒ ⇒ **这是 RAG 独有的形态**（Go 在同一输入下是「放行」） |
| 🔴 **新值（改后）** | **200 + `{code:1015, msg:"服务繁忙"}`** ⇒ ⇒ **1015 = Go 侧 `CodeServerBusy`**（真源 `service/favorite/common/errmsg.go:13` ＋ `:48`）⇒ ⇒ 含义 = **请求真的进了业务层**（测试环境 favorite-rpc 不可用 ⇒ `status.FromError` ⇒ 1015）⇒ ⇒ ⇒ **与 Go 同形**（Go：放行 ⇒ 进业务层 ⇒ 同一出口） |
| ⇒ **结论** | ⇒ ⇒ **这次改动让 RAG 从「误拒」变成「与 Go 同形」** ⇒ ⇒ **不是新引入的漂移，是旧漂移（`DRIFT-HOTAPI-030` 那一格）被闭合** |
| ⇒ **G3 自己早已预告过** | G3 树 `FavoriteApiRealTomcatGateTest.java:287-289`：「⚠️ 5003 是**当前实现**的形态，**不是** Go 的形态：Go 在同一输入下是「放行 ⇒ 200 + 业务结果」… ⇒ **若全局把 max-parameter-count 放宽到 10000（Go net/url 的真实上限），本断言需同步更新**」⇒ ⇒ 与本层的落码**完全一致** |
| ⇒ **域侧动作（G3）** | 该断言须从「必须含 `code:5003`」改为「**不得**是 5003 / 不得是 Tomcat HTML 错误页」⇒ ⇒ **本层不代改**（域侧写权）⇒ ⇒ **须在 G3 那一笔 merge 里一起改**（否则留下半截）<br>🔴🔴 **本行建议已订正（G3 已落码 ＋ 本层独立核 · §4.10.29.8）**：**纯负向断言不够**（「200 + body 里没有 5003 + 不是 HTML」**证明不了请求真的进了业务层** —— 200 + 空 body 也能过）⇒ ⇒ **必须再有一条正向断言** ⇒ ⇒ 而正向断言就该写 Go 的契约值 **1015**（真源 `favorite/common/errmsg.go:13` ＋ `:115` ＋ `:136` ⇒ **`CodeServerBusy = 1015` 就是这一格的契约**）⇒ ⇒ ⇒ **G3 的写法（负向两条 ＋ 正向一条）正确且更强** |

##### 4.10.25.5 变异验证（**每条断言都有专属变异**）

| 变异 | 做法 | 红在哪 |
|---|---|---|
| **M1（产品侧 · customizer）** | 把「无条件抬上限」改回「`mode == OFF ⇒ return`」 | ⇒ **断言① 红**（运行期落到 **1000** = Boot 默认，本测试应用没配 yaml ⇒ **不是** 2048）⇒ + **断言② 连带红**（容器在第 2049 个就拒 ⇒ 400）⇒ ⇒ **如实标注：②对M1不是专属** |
| **M2（产品侧 · interceptor）** | 删掉「`mode == OFF ⇒ 直接放行`」 | ⇒ **断言③ 红**（`preHandle` 不再返回 true）⇒ + **断言② 连带红**（400 + Go 原文）⇒ ⇒ **如实标注：③对M2专属，②对M2不专属** |

⇒ ⇒ **① ← M1（专属）· ③ ← M2（专属）· ② 被两个变异连带打红（因为它同时依赖容器上限与闸门缺席两件事）** ⇒ ⇒ **无「无专属变异」的断言**。

##### 4.10.25.6 ⚠️ 共享面待整合（**本层只登记 · 不改** —— 那个文件不属本层写权面）

| 项 | 内容 |
|---|---|
| 🟡 `tools/difftest/check-tomcat-limits.py` 的**文档字符串** | 仍写「`off` ⇒ 不装闸门、**也不改容器上限**」⇒ ⇒ **语义已变**（现在 off 也抬上限）⇒ ⇒ 该脚本的**逻辑**（校 yaml 层）**不受影响** ⇒ ⇒ **不会红** ⇒ ⇒ 但**文档会误导** ⇒ ⇒ **属整合者写权面 ⇒ 本层不改 ⇒ 登记** |

##### 4.10.25.7 🟡 **证据强度与已知缺口**（这一格**不是** L4）

| 项 | 值 |
|---|---|
| **Go 腿** | ✅ **L1 源码级（本层直接读真源，不是转述）**：`go-zero@v1.10.2/rest/httpx/util.go:15` `maxFormParamCount = 2048` ＋ 同文件计数循环里的 `if len(v) == 0 { continue }` ⇒ **空值不计数** ⇒ ⇒ 「1 个非空值 + 2048 个空值」在 Go 侧 **n = 1 ⇒ 不报错 ⇒ 放行**；<br>另：`Go 1.25.11 src/net/url/url.go:1001` `const defaultMaxParams = 10000` ＋ `:1004` `withinDefaultMax := params <= defaultMaxParams` ⇒ ⇒ 2049 段 **不**触发 Go 的段数上限 ⇒ ⇒ **Go 腿的「放行」是两层都放行** |
| **Go 腿的旁证** | ✅ favorite 域的**既有双侧探针**里已有一个**对照组**：`tools/api-envelope-probe/favorite/go/main.go:149-157`「2048 个参数恰好在界内 ⇒ 必须**不报错**」＋ `:183-188` 的注释「GetFormValues 数的是**非空值个数**」⇒ ⇒ 计数口径早已被**真 httpx.Parse** 实测钉住 |
| **RAG 腿** | ✅ **L3 真容器活体**：真 Tomcat ＋ 真域内判据 ⇒ 实测 **200 + `{code:1015, msg:"服务繁忙"}`** ⇒ 「请求真的进了业务层」⇒ **与 Go 的「放行 ⇒ 进业务层」同形** |
| **合并口径** | ⇒ ⇒ **L3**（按弱侧）⇒ ⇒ **不得写成 L4** |
| 🔴 **已知缺口（L4 那一格还没有）** | **`tools/api-envelope-probe/favorite/` 的 2049 用例是「2049 个**全非空**值」** ⇒ ⇒ 那是**应用层**判据（与本次改动无关）⇒ ⇒ **本次闭合的「差 1」格（1 非空 + 2048 空）没有双侧活体用例** ⇒ ⇒ ⇒ **该探针属 favorite 域工具面（G3 写权）⇒ 本层不扩** ⇒ ⇒ **建议 G3 补一条「1 个非空值 + N 个空值」的双侧用例** ⇒ 那才是这格的 L4 证据 |
| ⚠️ **另一条仍然存在、但与本次改动无关的残余** | 总「值」数 **> 10001** 时，Tomcat 拒（400 + HTML 错误页），Go 拒（400 + `net/url` 原文）⇒ ⇒ **状态码一致、body 不同** ⇒ ⇒ 归 `DRIFT-HOTAPI-030` 的**下一格** ⇒ ⇒ 本次改动**不覆盖**它（要覆盖需容器上限再抬或做 body 映射）⇒ ⇒ **显式登记，不假装闭合** |

---

#### 4.10.26 🟢 **第十二次全量回归口径**（核对日期 2026-10-27 13:09~13:36 · 代码态 `cd893d1`）

> ⚠️ **本轮口径是「两段拼起来」的** —— 这是**必须如实写**的地方，不能写成「一次跑完 52 模块」。
> 🔴 **原因**：`mvn -B clean verify` 默认 **fail-fast** ⇒ 撞到 favorite-api 那条红（**预期内**的域侧动作，见 §4.10.25.4）⇒ **reactor 停在 [46/52]** ⇒ ⇒ **后 6 个模块（comment 域 5 个 ＋ `favorite-rpc`）根本没跑** ⇒ ⇒ 所以口径 = **主跑（46 模块）+ 补跑（6 模块，`-fae` + `-pl` + `-am`）**。

| 项 | 值 |
|---|---|
| 代码态 | **`cd893d1`**（本轮**唯一**改生产代码的提交）⇒ ⇒ 与第十一次全量（代码态 `040a201`）**不是同一个代码态** |
| 主跑 | `mvn -B clean verify` ⇒ **BUILD FAILURE / `MVN_EXIT=1`**（**预期内**）· 13:09:05 → 13:28:31 = **19:26 min** · **跑到 [46/52] favorite-api 停住** |
| 补跑 | `mvn -o -B -fae -pl service/comment/comment-api,service/comment/comment-rpc,service/comment/comment-common,service/comment/comment-proto,service/favorite/favorite-rpc -am verify` ⇒ **EXIT=0** · 13:31~13:35 |
| **模块数** | **52**（46 ＋ 6，**两段合计**，不是一次跑完） |
| **有测试的模块数** | **29**（主跑 25 ＋ 补跑 4；`comment` / `comment-proto` / `favorite` 三个聚合器无测试） |
| **用例数** | **2474** = 主跑 **2184** ＋ 补跑 **290** |
| **failures / errors / skipped** | **1 / 0 / 4**（skipped：favorite-api 1 ＋ favorite-rpc 3） |
| **那 1 条红** | `service/favorite/favorite-api/.../FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError` ⇒ **预期内** ⇒ 断言的是**旧行为**（`code:5003`）⇒ 新行为是 `code:1015`（＝Go 的 `CodeServerBusy`）⇒ ⇒ **需 G3 在同一笔 merge 里改**（§4.10.25.4） |
| **交叉验证（两条独立路径得同一总数）** | ① 第十一次全量日志里同样那 4 个有测试的模块 = `favorite-rpc` **110** ＋ `comment-common` **27** ＋ `comment-api` **45** ＋ `comment-rpc` **108** = **290** ⇒ 与本轮补跑**逐条一致**；<br>② 第十一次全量 **2471** ＋ 本轮新增的 **3** 条共享层守门 = **2474** ⇒ 与「2184 ＋ 290」**完全吻合** ⇒ ⇒ ⇒ **总数不是估出来的，是两条路各算一遍对上的** |
| **逐模块要点** | `common` **324**（321 ＋ **3** 新守门）· `favorite-api` **97**（红 1 · skipped 1）· `favorite-rpc` **110**（skipped 3）· `comment-common` **27** · `comment-api` **45** · `comment-rpc` **108** · 其余 24 个模块与第十一次**逐条一致**（0 失败 0 errors） |
| **双侧探针**（同批次复跑） | `tools/api-envelope-probe/favorite/run.sh` ⇒ **EXIT=0**（**双侧逐字节一致**：Go 真源实测 == `expected.txt` == RAG 实测）· `tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0**（**56 例**：一致 54 · 不一致 0 · 已签字偏离 2）· `tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0**（**3 例**两侧一致） |
| **三门禁**（同批次复跑） | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **台账门禁** | `check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=0** |
| **写权自证** | 本轮改动**全部**落在 `service/common/**` ＋ `docs/DRIFT/global/vs-common.md` ⇒ **零域侧改动** |
| 🟢 **工作区噪声（根因已定位并已清）** | 原：未跟踪目录 `io/`（`io/opentelemetry/api/GlobalOpenTelemetry.class`，**mtime 归一化成 1980-02-01** ⇒ 从 jar 解出、不是源码产物）⇒ ⇒ **本层 2026-10-27 14:3x 定位根因**：该文件 md5 `e7207e7c24aa36c55a8d283bd82cdae9` **==** `~/.m2/.../opentelemetry-api-**1.36.0**.jar` 内同名条目的 md5（**逐字节相同**）⇒ ⇒ 而**本仓 pin 的是 `opentelemetry-bom 1.44.0`**（`service/common/pom.xml:18`）⇒ ⇒ 连版本**都不是构建在用的** ⇒ ⇒ **它 = 本层早前做 OTel 字节码取证时、在仓库根解 jar 的遗留**（`tools/**` 全仓无解压 ⇒ 不是脚本干的）⇒ ⇒ **不是构建产物 ⇒ 不影响构建与提交** ⇒ ⇒ **已按可恢复方式删除**（`mavis-trash`）⇒ ⇒ 本层工作区 `git status` 现为**空**<br>🆕 **新规则（取证纪律）**：**解 jar 一律解到 `mktemp -d` 的临时目录 ⇒ 绝不在仓库根解** ⇒ ⇒ 否则既污染 `git status`，又让「工作区噪声」变成每个 agent 都要重新查一遍的**悬案** |

> 🔴 **本轮口径的三条诚实边界**：
> ① **不是「一次跑完 52 模块」** —— 是 fail-fast 停在 [46/52] ＋ 补跑 6 个 ⇒ ⇒ **别把 `MVN_EXIT=1` 读成「全量红了」**；
> ② **那 1 条红是「跨域可观察行为变更的预期后果」**，不是共享层缺陷 ⇒ ⇒ 但它**必须由域侧改掉** ⇒ ⇒ 在 G3 改掉之前，**合并主干后全量仍会红 1 条**；
> ③ **「差 1」那一格是 L3，不是 L4**（§4.10.25.7）⇒ ⇒ **不许读成「双侧活体已逐字节闭合」**。

---

#### 4.10.27 🔴🟢 **Redis 超时的跨域全表**（G2 的发现 ⇒ 本层推广成 10 个组件的逐项实测）—— **8 个组件的连接超时比 Go 松 2 倍**

> **触发**：G2 在其域发现「task-rpc 的命令/连接超时**两个都没配** ⇒ 落 Lettuce 默认（命令 60s / 连接 10s）⇒ 断连时命令挂 60s ⇒ 撞 `rpc-timeout: 2s` ⇒ 客户端看到 1015」
> ⇒ ⇒ **本层把它推广成「全部用 Redis 的组件」的逐项实测**（G2 的发现本身成立，且**不是孤例**）。

##### 4.10.27.1 三条机制事实（本层**独立核过**，不采信转述）

| # | 事实 | 证据（本层实测） |
|---|---|---|
| ① | **Go 侧生效的 Redis 超时 = go-redis 的库默认**，不是真源 yaml 里写的值 | 真源 `go.mod` pin：`github.com/redis/go-redis/v9 **v9.20.0**` · `github.com/zeromicro/go-zero **v1.10.2**`；<br>go-redis `options.go` 的 `init()`：`if opt.DialTimeout == 0 { opt.DialTimeout = **5 * time.Second** }` ＋ `switch opt.ReadTimeout { case 0: opt.ReadTimeout = **3 * time.Second** }` ⇒ ⇒ **连接 5s / 命令 3s**；<br>⚠️ 真源 yaml 里的 `ReadTimeoutMs: 1000` / `DialTimeoutMs: 500` **不生效**（G2 实测：对应 struct **没有**这两个字段 ＋ go-zero 的 `conf.Load` 是**非严格**模式 ⇒ 未知字段被静默忽略 ⇒ `LOAD OK` 但字段没进 struct）⇒ ⇒ **真值 = 库默认** |
| ② | 🔴 **Boot 的两个超时属性彼此独立**：`timeout` → **命令**超时；`connect-timeout` → **连接**超时 | `spring-boot-data-redis-4.0.3` 的 `LettuceConnectionConfiguration` 字节码：`DataRedisProperties.getTimeout()` → `LettuceClientConfigurationBuilder.commandTimeout(...)`；另一处 `DataRedisProperties.getConnectTimeout()` → `SocketOptions$Builder.connectTimeout(...)` ⇒ ⇒ **未配 `connect-timeout` ⇒ 不会回落到 `timeout`** |
| ③ | ⇒ ⇒ **未配 `connect-timeout` ⇒ 连接超时 = Lettuce 自己的默认 = 10s**（**不是** `timeout` 的值） | `lettuce-core-6.1.8` 的 `SocketOptions` 静态块：`DEFAULT_CONNECT_TIMEOUT_DURATION = Duration.ofSeconds(**10**)`（字节码 `ldc2_w 10` + `Duration.ofSeconds`）⇒ ⇒ **比 Go 的 5s 松 2 倍** ⇒ ⇒ ⚠️ **G1 的 `admin-rpc/application.yml` 注释写的是「回落 3s」⇒ 机制写错**（结论方向不变：仍与 Go 的 5s 不一致；但**值与机制都错**）⇒ ⇒ 见 §4.10.27.3 的转派单 |

##### 4.10.27.2 🔴 逐组件实测表（**10 个生产代码真在用 Redis 的组件**，YAML 解析器读值，不是 grep）

| 组件 | 域 | yaml | 命令 timeout | 连接 connect-timeout | 与 Go（**命令 3s / 连接 5s**）相比 |
|---|---|---|---|---|---|
| `admin-api` | G1 | `application.yml` | 3s | **5s** | ✅ **完全一致** |
| `admin-rpc` | G1 | `application.yml` | 3s | **5s** | ✅ **完全一致**（⚠️ 但它的**注释**把机制写错了 ⇒ §4.10.27.3） |
| `user-api` | G1 | `application.yml` | 3s | — | 🔴 **连接 = 10s**（≠ 5s） |
| `user-rpc` | G1 | `application.yaml` | 3s | — | 🔴 **连接 = 10s**（≠ 5s） |
| `follow-api` | G1 | `application.yml` | **2s** | — | 🔴🔴 **命令 2s（≠ 3s，比 Go 更严）＋ 连接 = 10s** |
| `like-rpc` | G2 | `application.yml` | 3s | — | 🔴 连接 = 10s ⇒ **G2 本轮已补 `connect-timeout: 5s`** |
| `task-rpc` | G2 | `application.yml` | — | — | 🔴🔴 **两个都没配 ⇒ 命令 60s（实测 60021ms，G2）＋ 连接 10s** ⇒ **G2 本轮已补 `timeout: 3s` + `connect-timeout: 5s`** |
| `comment-rpc` | G3 | `application.yml` | 3s | — | 🔴 **连接 = 10s** |
| `hot-rpc` | G3 | `application.yml` | 3s | — | 🔴 **连接 = 10s** |
| `favorite-api` | G3 | `application.yml` | 3s | — | 🔴 **连接 = 10s** |

⇒ ⇒ **6 个组件待修**（G1 的 3 个 ＋ G3 的 3 个）；G2 的 2 个已修。
⇒ ⇒ 🔴 **危害口径**（**比 Go 更松，不是更严**）：连接超时 10s > 任何 rpc 组件的 `rpc-timeout: 2s` ⇒ ⇒ **一次 Redis 断连 ⇒ 先撞 RPC 超时 ⇒ 客户端看到 1015「服务繁忙」**（Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」`）⇒ ⇒ 与 G2 在 task-rpc 上实测到的是**同一机制**。
⇒ ⇒ ⚠️ `follow-api` 的**命令超时 2s** 是**反方向**的一条（比 Go 的 3s 更严 ⇒ 「比 Go 更严」同样是漂移）⇒ ⇒ 单独列出，不与连接超时那条混为一谈。

##### 4.10.27.3 转派单（**域侧动作，本层不改域内文件**）

| # | 落点（域写权面） | 事实 | 建议修法 |
|---|---|---|---|
| **R1** | `service/users/user/user-api/src/main/resources/application.yml`（G1） | `timeout: 3s` ✔ 但**无** `connect-timeout` ⇒ 连接 = **10s** | 补 `connect-timeout: 5s`（与 `admin-api` 同款） |
| **R2** | `service/users/user/user-rpc/src/main/resources/application.yaml`（G1） | 同上 | 同上 |
| **R3** | `service/follow/follow-api/src/main/resources/application.yml`（G1） | `timeout: **2s**`（≠ Go 3s）＋ 无 `connect-timeout` | 命令超时改 **3s** ＋ 补 `connect-timeout: 5s` ⇒ ⇒ ⚠️ **2s 这个值 yaml 里没有任何理由说明** ⇒ ⇒ **G1 需自行判定它是「刻意收紧」还是「抄错」**（若刻意 ⇒ 登记为签字偏离，不改） |
| **R4** | `service/users/admin/admin-rpc/src/main/resources/application.yml`（G1） | ✅ **值是对的**（3s + 5s）但**注释的机制写错**（写「不显式配 ⇒ 连接超时跟着变成 3s」⇒ **实际是 Lettuce 默认 10s**） | **只改注释**（零行为变化）⇒ ⇒ 注释是「值为什么会错」的依据 ⇒ ⇒ 写错会误导下一个 agent |
| **R5** | `service/comment/comment-rpc/src/main/resources/application.yml`（G3） | `timeout: 3s` ✔ 但无 `connect-timeout` | 补 `connect-timeout: 5s` |
| **R6** | `service/hot/hot-rpc/src/main/resources/application.yml`（G3） | 同上 | 同上 |
| **R7** | `service/favorite/favorite-api/src/main/resources/application.yml`（G3） | 同上 | 同上 |

⇒ ⇒ ⚠️ **本层只登记 + 转派**：这 7 条的落点**全部在域侧写权面**（`service/**/<域>/**`）⇒ ⇒ **本层不代改**（与 §5.3e「共享层缺陷一律转派 G4」不冲突 —— 这不是共享层缺陷，是**域侧配置缺项**）。
⇒ ⇒ ⚠️ **与 E66 的关系**：E66（Redis 命令绑 deadline）解决的是「**命令**超时要按请求剩余预算」⇒ ⇒ **它不修「连接超时 = 10s」**（那是 `connect-timeout` 的配置缺项）⇒ ⇒ **两条独立**：配置缺项 ⇒ **域侧补 yaml**（本节）；deadline 感知 ⇒ **共享层机制**（§4.9.4）⇒ ⇒ **别把 E66 的实现清单当成这条的修法**。

##### 4.10.27.4 证据强度（**按弱侧，不写 L4**）

| 腿 | 强度 | 说明 |
|---|---|---|
| Go 腿 | **L1 源码级（本层直接读依赖源码）** | `go-redis@v9.x` 的 `options.go` `init()`（DialTimeout 5s / ReadTimeout 3s）＋ 真源 `go.mod` 的 pin 版本；⚠️ **真源 yaml 里那两个字段不生效**这条是 **G2 的域侧实测**（`LOAD OK` 但字段没进 struct）⇒ ⇒ **合并后按 L1 记**，**不主张 L4** |
| RAG 腿 | **L2 配置层（本层逐组件 YAML 解析）+ L2 机制层（本层字节码）** | 10 个组件的值逐项解析 ＋ Boot 4.0.3 / Lettuce 6.1.8 的字节码（命令/连接两条独立路径 ＋ `DEFAULT_CONNECT_TIMEOUT_DURATION = 10s`）⇒ ⇒ **不是「读 yaml 就信」**（§3.3.1） |
| 合并 | **L1~L2** | ⇒ ⇒ **「连接超时 = 10s」是机制级结论**（不依赖活体）⇒ ⇒ **「断连 ⇒ 1015」那半仍是 L1**（G2 在 task-rpc 上有活体实测） |

---

#### 4.10.28 🟢 **把「不得新建域内 Redis 副本」从台账禁令落成机器门禁**（G2 的显式禁止项 ⇒ 台账拦不住人 ⇒ 源文件扫描守门 · 提交 `22dc67e`）

> **触发**：G2 在其域台账里写了**显式禁止项**（「不得新建域内 Redis 副本」）⇒ ⇒ 本层的判断是：
> **台账禁令拦不住人** ⇒ ⇒ 域侧 agent **看不到**共享层台账（各域各一份 ⇒ 零重叠 ⇒ 不自动送达）⇒ ⇒
> 「域内新建一份更保险」这种直觉很容易被执行 ⇒ ⇒ ⇒ **必须有机器闸门**。
> ⇒ ⇒ 本节 = 把那条禁令**落成「真构建会红」的守门**。

##### 4.10.28.1 落码（**零生产改动** ⇒ 零运行时行为变化）

| 项 | 值 |
|---|---|
| 新增守门 | `service/common/src/test/java/com/rag/common/redis/GoStyleRedisClientShadowingGateTest.java`（**3 条**） |
| 生产代码 | **零改动**（只加测试面） |
| 判据 | 扫 `service/**/src/main/java`（**排除 `service/common`**）找 `LettuceClientOptionsBuilderCustomizer` ⇒ ⇒ **白名单之外零命中** |
| 白名单 | `service/users/user/user-api/src/main/java/com/rag/users/user/api/config/RedisDisconnectedBehaviorConfig.java`（**收编前的遗留** · 第九轮修 `DRIFT-USERAPI-014` 落的原件） |
| 扫描根 | **从 code source 向上找**（同时含 `service/` 与 `pom.xml` 的目录）⇒ ⇒ **不依赖工作目录**（surefire 的 cwd = 模块目录 · IDE = 仓库根 ⇒ 两种布局都得对） |
| Go 出处 | `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:14`（`maxRetries = 3` ⇒ 断连**立即失败**）＋ `service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35-36`（`err != nil` ⇒ **放行**）⇒ ⇒ 断连要**快速失败**才进得了 fail-open 那段 |

##### 4.10.28.2 🔴 三条断言 ＋ **每条一条专属变异**（实测：每次**只有那一条**红）

| 断言 | 断言什么 | 专属变异 | 实测结果 |
|---|---|---|---|
| ① `noDomainSideShadowingCopyExists` | 白名单之外**零**声明（闸门的主断言） | **M1** = 白名单跳过失效（`allowed.contains(...)` → `false && ...`） | ✅ **只有 ① 红**（`Failures: 1` · 报出 user-api 那份） |
| ② `scannerActuallyReportsACopy` | **临时目录**造假副本 ⇒ 扫描器**必须**报出（防 ① 是「什么都没扫到」的**假绿**） | **M2** = 只扫「有 `pom.xml` 的 Maven 模块」（临时假模块没有 `pom.xml`） | ✅ **只有 ② 红** |
| ③ `scannerSeesTheRealRepository` | **真实仓**自检：(a) 关掉共享层排除 ⇒ 必须扫到**共享层那份**（锚点恒存在）；(b) 白名单中**仍存在**的每一份 ⇒ 必须都能扫到 | **M3** = 扫描器跳过路径含 `/com/rag/common/` 的文件 | ✅ **只有 ③ 红**（报「在**真实仓**里根本扫不到声明 ⇒ 断言①是假绿」） |

⇒ ⇒ **还原方式 = 字节备份 + md5 双向校验**（**不用 `git checkout --`** ⇒ 本文件当时是**未跟踪的新文件** ⇒ `checkout` 会**无声抹掉**真实改动）⇒ ⇒ 三次还原 md5 全部 `1714c7ff4e094451857aeb222bd4ecf6`（一致）。
⇒ ⇒ ⚠️ **实现层刻意不带「模块必须有 `pom.xml`」这条约束**（它就是 M2 的注入点）⇒ ⇒ 写在实现里会让断言②**直接红**（第一次跑正是如此 ⇒ 已改）⇒ ⇒ **「变异注入点」不得提前落进实现**。

##### 4.10.28.3 🔴 **白名单必须自适应**（收编完成后**不得**变红）

> G1 已 merge 本分支（`b19a61a`「收编删除的前置」）⇒ ⇒ 他们的工作区里 **5 份 `RedisDisconnectedBehaviorConfig` 已 `D`（已暂存删除）**
> ⇒ ⇒ **白名单里的 user-api 那份也会被删掉** ⇒ ⇒ 若断言写成「白名单那份必须存在」⇒ ⇒ **收编一完成这条守门就红**
> ⇒ ⇒ **红的原因与守门意图无关** ⇒ ⇒ **那是最坏的一种红** ⇒ ⇒ 故本守门的设计是三条自适应：
> ① **白名单项文件不存在 ⇒ 自动跳过**（不红，并在断言消息里提示清空白名单）；
> ② 断言③(a) 的锚点用**共享层那份**（`GoStyleRedisClientAutoConfiguration` · 本层不删 ⇒ **恒存在**）⇒ ⇒ 收编前后**恒有断言**；
> ③ 断言③(b) 只断言「白名单中**仍存在**的每一份能被扫到」⇒ ⇒ 收编完成后自动为空 ⇒ ⇒ **不红**。
> ⇒ ⇒ **收编完成后的正确处置 = 清空白名单**（本层写权）。

##### 4.10.28.4 ⚠️ 边界（**不外推**）

| 项 | 说明 |
|---|---|
| 判据是「文件里出现该类型名」 | ⇒ ⇒ **保守**（引用即算）⇒ ⇒ 但仓内现状：**生产源码里出现该类型名的只有 2 份**（共享层那份 + user-api 那份遗留）⇒ ⇒ **无误报面** |
| 只扫**生产**源码 | ⇒ ⇒ 域侧**测试**里引用该类型是允许的（测试不产生生产 Bean）⇒ ⇒ 共享层自己的测试替身 `DomainLikeRedisDisconnectedBehaviorConfig` 也在允许范围内 |
| 跨 lane 表现 | G2/G3 树里 user-api 那份**存在** ⇒ ⇒ 绿；G1 树里删完 ⇒ ⇒ 白名单项失效 ⇒ ⇒ **仍绿**（§4.10.28.3）⇒ ⇒ **这条守门不会因为别的 lane 收编而变红** |
| 证据强度 | **L2（构建期）** —— 它证明的是「**再建一份就会红**」⇒ ⇒ **不主张 L4**（不涉及活体行为）⇒ ⇒ **不改变任何 A 轴结论**（只是把 §4.10.24 的既有结论变成机器可执行） |

#### 4.10.29 🔴 **G1 的订正 ＋ 一条波次级判据**（2026-10-27 14:1x）—— 本层的「连带删 `@Import`」**错了**，已就地订正

> **触发**：G1 完成收编后回函，指出两条：① 本层 §4.10.24.2 的「连带**删** `@Import`」**不够**；② 一条**波次级**判据（`-pl` 不带 `-am` ⇒ 跑的是 `~/.m2` 里的**旧** `com.rag:common`）。
> ⇒ ⇒ **本层对两条都独立核过**（不采信转述）⇒ ⇒ ① **成立**（§4.10.24.2 已就地订正）；② **成立且对本层无影响**（见 §4.10.29.2）。

##### 4.10.29.1 🔴 订正一：「连带**删** `@Import`」⇒ **必须换成 `@ImportAutoConfiguration`**

| 腿 | 事实 | 出处（本层读原文核实） |
|---|---|---|
| rpc 侧 | 2 份守门的上下文 = **`@SpringBootConfiguration`**（**不含** `@EnableAutoConfiguration`）⇒ ⇒ **`AutoConfiguration.imports` 清单根本不会被读** ⇒ ⇒ 只删 `@Import` ⇒ 容器里 customizer 数量 = **0** ⇒ ⇒ 守门**静默测不到任何断连策略** | G1 树 `b19a61a` 工作区：`service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcRedisDisconnectedFastFailTest.java:195` · `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcRedisDisconnectedFastFailTest.java:182` |
| api 侧 | 3 份守门用 **`@SpringBootTest`**（引导真实应用类）⇒ ⇒ 清单会被读 ⇒ ⇒ 删掉域内副本后**共享层那份自动接管** ⇒ ⇒ **连带项 = 零**（注解都不用改） | 同树 `service/users/user/user-api/src/test/java/com/rag/users/user/api/UserApiRedisDisconnectedFastFailTest.java:140` |
| 结论 | ⇒ ⇒ **G1 的订正成立**（**值与机制都对**）⇒ ⇒ §4.10.24.2 第 1 行**已就地订正**（不重写历史段，只加指路）⇒ ⇒ **G1 已实施**（其树内两处 `@ImportAutoConfiguration`） | — |

⚠️ **本层的教训（自己踩的）**：**同一机制在不同组件上的装配形态可以不同** ⇒ ⇒ 我把「api 侧连带项 = 零」**外推**成「rpc 侧也是零」⇒ ⇒ **错了** ⇒ ⇒ **逐组件实测，不外推**（§4.10.24.4 说「不推翻『删除 = 零行为变化』」那条仍然成立 —— 被推翻的只是**连带项的修法**）。

##### 4.10.29.2 🔴 订正二（**波次级**）：`-pl` 不带 `-am` ⇒ 跑的是**本地仓库里的旧 `com.rag:common`**

| 项 | 事实 |
|---|---|
| 机制 | `mvn -pl <域内模块> test`（**不带 `-am`**）⇒ reactor 里**只有域内模块** ⇒ `com.rag:common` **从 `~/.m2` 解析** ⇒ ⇒ 跑的是**旧 jar** |
| 实测 | G1 域内跑收编后回归 ⇒ `MVN_EXIT=1` ＋ 编译错误「程序包 `com.rag.common.redis` 不存在」⇒ ⇒ 那个 jar 里 `com/rag/common/redis/` 条目 = **0** ⇒ ⇒ G4 新增的 4 个类**全部不在** ⇒ ⇒ 它 = **主干代码态** |
| ⇒ ⇒ 含义 | **任何域级回归，只要共享层代码在期间变过，那个回归就【证明不了共享层改动的兼容性】**（域内模块压根没加载改过的那份共享层）⇒ ⇒ **判据**：跑之前先问「**这次回归的 classpath 上 `com.rag:common` 是哪一份？**」⇒ 共享层动过 ⇒ **必须带 `-am`** |
| ⇒ 对**本层**的影响 | **本层的全量（52 模块）是有效的** —— `service/common` **在 reactor 内** ⇒ ⇒ 用**源码** ⇒ ⇒ 不受这条影响 ⇒ ⇒ 但**各域拿本层的全量当「我域与共享层兼容」的证据是不成立的** ⇒ ⇒ 各域必须**自己**带 `-am` 跑一次 |
| ⇒ 与本层既有纪律的关系 | 与「**lane 级全量 ≠ 别的 lane 的回归证据**」同族，但**更隐蔽**：那条是「**别的 lane 的树**不含我的提交」；这条是「**连共享层都不是我这份树**」⇒ ⇒ **两者都会让「跑绿」变成假绿** ⇒ ⇒ **本层的守门清单里已加这一条**（见 §4.10.29.7） |

##### 4.10.29.3 🟢 G1 的收编落地结果（**本层只登记，不复算**）

| 项 | G1 的实测值 |
|---|---|
| 收编 | 5 份域内副本**已删**；5 份守门各补 **2 条 bean 身份断言**（`size()==1` ＋ key 含 `goStyleRedisClientOptionsCustomizer`） |
| 回归 | 我域 6 组件 **667** ＋ `service/common` **324** = **991** · BUILD SUCCESS · 06:47 min（**带 `-am`**） |
| 变异 | ① 去掉 rpc 侧 `@ImportAutoConfiguration` ⇒ **2/2 红**；② 运行期 exclude 共享层自动配置（**直接 `-D`** 才传得进测试 JVM ⇒ 塞在 `-DargLine` 里**不生效** ⇒ 又一个「参数没到它该到的地方」的实例）⇒ **2/2 红**；③ 装回「域内副本替身」⇒ 2 条只红 1 条（**名字断言**红、机制腿绿）⇒ ⇒ 证明「收编真的发生」这条判据能**单独**抓住「收编没做」 |
| ⇒ 对本层新守门的影响 | G1 树里那 5 份副本已删 ⇒ ⇒ 本层 §4.10.28 的守门在 G1 树里**仍绿**（白名单项自动失效 ＋ 锚点恒存在）⇒ ⇒ **这条守门不会因收编而变红**（设计目标达成） |

##### 4.10.29.4 🟡 共享面数字订正（**只登记，不代改**）

| 项 | 值 |
|---|---|
| 全仓 U+FFFD 清单 | G1 实测：**合并回主干后 = 23 份 / 63 处**（本层 §4.10.20 记录的是本树口径）⇒ ⇒ **复扫要比「文件清单」而不是比份数**（两份清单**不是同一批文件**）⇒ ⇒ **本层不自行改数**（等主干合并态实测） |

##### 4.10.29.5 🔴 **本层自己踩了一次「零命中 ⇒ 不存在」的坑**（**自陈，非指责**）

| 事实 | 说明 |
|---|---|
| 现象 | 复核 G1 的订正时，按 §4.10.24.2 的路径在**本树** `find` ⇒ **4/5 份守门文件零命中** ⇒ ⇒ 第一反应是「G1 的文件不存在 / 他在说假话」 |
| 真因 | 那 4 份文件是 **G1 第十四轮 `0c2a902` 新增的** ⇒ ⇒ **只存在于 G1 树** ⇒ ⇒ 本树零命中是**正常**的 ⇒ ⇒ §4.10.24.2 的「位置」列**已带树 + commit**（`G1 树 b7d724c`）⇒ ⇒ **引用本身合规**，只是**复核姿势**错了 |
| ⇒ ⇒ 纪律 | **引用他树的文件 ⇒ 复核必须用 `git -C <树> show <commit>:<path>`** ⇒ ⇒ **在本树按路径查 ⇒ 零命中 ≠ 不存在**（与 §4.10.20 的 U+FFFD「grep 会假阴性 ⇒ 一律 python 读 bytes」同族：**工具口径错 ⇒ 结论就错**） |

##### 4.10.29.6 🟡 本层为此新增的两条**复核纪律**（写进本层工作方式）

| # | 纪律 | 触发 |
|---|---|---|
| ① | **跨树引用必须带「树 + commit」**（行号只在那个 commit 上有效）⇒ ⇒ **本层台账里所有引他树的位置列一律带** | §4.10.24.2 / §4.10.24.3 |
| ② | **跑域级回归前先问「classpath 上的 `com.rag:common` 是哪一份」** ⇒ ⇒ 共享层动过 ⇒ **必须带 `-am`** | §4.10.29.2 |

##### 4.10.29.7 证据强度

| 腿 | 强度 | 说明 |
|---|---|---|
| Go 腿 | **N/A** | 本节是**装配形态**与**构建口径**问题 ⇒ ⇒ 与 Go 侧无关 ⇒ ⇒ **不产生任何 A 轴结论变化** |
| RAG 腿 | **L1（源码级 · 本层读原文核实）** | ① 的三处注解行（rpc `:195` / `:182` ＋ api `:140`）**本层在 G1 树逐行读过** ⇒ ⇒ **不是「采信转述」** |
| 合并 | **L1** | ⇒ ⇒ **不主张 L4**（无活体） |

##### 4.10.29.8 🔴 订正三（**本层自己**）：§4.10.25.4 的「形状断言」建议**少了一条正向断言** ⇒ ⇒ **G3 的写法更强、且是对的**

| 项 | 内容 |
|---|---|
| 本层原建议 | §4.10.25.4：「该断言须从「必须含 `code:5003`」改为「**不得**是 5003 / 不得是 Tomcat HTML 错误页」」⇒ ⇒ **纯负向** |
| 缺口 | 🔴 **纯负向断言不够** —— 「200 ＋ body 里没有 5003 ＋ 不是 HTML」**证明不了请求真的进了业务层**（一个 200 ＋ 空 body 也能过）⇒ ⇒ ⇒ **必须有一条正向断言** |
| G3 的实际写法（G3 树 `e18fd52` 工作区 · `service/favorite/favorite-api/src/test/java/com/rag/favorite/favorite/api/FavoriteApiRealTomcatGateTest.java` · 方法体自 `:300` 起） | ① `assertEquals(200, statusCode)` ② `assertFalse(body.contains("\"code\":5003"))` ③ **`assertTrue(body.contains("\"code\":1015"))`** ④ `assertFalse(isTomcatHtmlErrorPage(resp))` ⇒ ⇒ **在负向之上补了正向那条** ⇒ ⇒ 并保留了旧方法名（**以 DisplayName 与断言为准**） |
| Go 侧核对（**本层独立核，非转述**） | 真源 `service/favorite/common/errmsg.go:13` ⇒ `CodeServerBusy = 1015`；`:108-116` ⇒ `BizCodeFromError` 中 `status.FromError(err)` 不 ok ⇒ **`:115` 返回 `CodeServerBusy`**；`:130-137` ⇒ `default: return CodeServerBusy`（gRPC `Unavailable` 也落这里）⇒ ⇒ **1015 就是这一格的 Go 契约值** |
| ⇒ ⇒ 订正 | **本层的建议缺了正向那条** ⇒ ⇒ 正确的下限是「**负向两条 ＋ 正向一条（Go 契约值）**」⇒ ⇒ §4.10.25.4 已就地加订正标记（不重写历史段）⇒ ⇒ **G3 的写法正确** |
| 🆕 教训 | **「别写死具体值」不是通用规则** —— 只有当那个值**不是契约**时才适用；**当它就是 Go 侧契约值时，硬编码反而更强**（能抓住「错误码映射被改」这类漂移）⇒ ⇒ **先问「这个值是不是契约」再决定断言形态** ⇒ ⇒ 与本层「形状优先」的泛化相比：**G3 比本层更准** |

#### 4.10.30 🟢 **第十三次全量回归口径**（核对日期 2026-10-27 · 代码态 `cd893d1` + `22dc67e`）

> ⚠️ **口径的复算方法（本轮新增 · 可复算）**：从三段日志里取**每个模块的 surefire 汇总行**
> （即 `<b>没有</b> " -- in " 后缀的那一行`）逐模块求和 ⇒ **不要按行序 tail**（那是 reactor 末模块，不是总数）。
> 🔴 **三段日志不能直接相加**：补跑那两段的命令带了 `-am` ⇒ `users-common` / `favorite-common` / `common`
> **被重新构建** ⇒ 直接相加会**重复计 389 条** ⇒ 下面的口径是**去重后**的。

| 段 | 日志 | 模块 | 用例 | failures | errors | skipped | 结论 |
|---|---|---|---|---|---|---|---|
| **主跑** | `/tmp/g4-full13.log` | **25**（含 `favorite-api`） | **2187** | **1**（预期内） | 0 | 1 | 🔴 `BUILD FAILURE` / `MVN_EXIT=1` ⇒ **fail-fast 停在 [46/52] `favorite-api`** ⇒ 其后 6 个模块 SKIPPED |
| **补跑 ①** | `/tmp/g4-rest6-13.log` | 3（重叠 3 + 新 0） | 89 | **3**（`common`） | 0 | 0 | 🔴 `BUILD FAILURE` ⇒ `common` 3 条新红（`GoStyleJsonContentTypeCharsetTomcatTest`「expected 200 but was -1」）⇒ **隔离复跑 4/4 绿** ⇒ 判为**非确定性抖动**（不是本波引入） |
| **补跑 ②** | `/tmp/g4-rest6-13b.log` | **4 个新**（+ 3 个重叠） | **290** | 0 | 0 | **3** | ✅ **全绿 / `BUILD SUCCESS` / `MVN_EXIT=0`**（14:36:24 → 14:39:37 = **3:13 min**） |
| **合计（去重）** | —— | **29 个有测试模块** | **2477** | **1** | **0** | **4** | ✅ 除 `favorite-api` 那 1 条**预期内**旧断言外全绿 |

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify -DargLine=-Xmx3g`（**全量**，不是 `-pl` 子集） |
| 主跑耗时 | **19:12 min**（14:09:08 起跑） |
| 模块数 | **52**（Reactor 计数）· 其中 **29 个有测试模块** |
| 用例数 | **2477**（逐模块汇总行求和 ⇒ 去重后） |
| failures / errors / skipped | **1 / 0 / 4**（那 1 条 failure = `favorite-api` 的**旧**断言 5003 ⇒ **预期内**；`favorite-api` 另 1 条 skipped ＋ `favorite-rpc` 3 条 skipped = **4**） |
| 相对上一轮 | 第十二次 **2474** ⇒ **+3**（= `22dc67e` 的新守门 3 条）⇒ ⇒ **本轮的生产代码态与第十二次相同**（`cd893d1` 未变） |
| 逐模块要点 | `service/common` **327**（本轮 330 是 §4.10.32 之后）· `article-rpc` **154** · `admin-rpc` **112** · `task-rpc` **110** · `favorite-rpc` **110**（3 skipped） |
| **三门禁**（同批次复跑） | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **台账三门禁** | `check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=0** |
| **三探针** | `run-parity.sh` **EXIT=0 · 56 例** · `run-rpc-timeout-parity.sh` **EXIT=0 · 3 例** · 🔴 `api-envelope-probe/favorite` **EXIT=2（两次）** ⇒ ⇒ **根因见 §4.10.31 ⇒ 产品侧逐字节正确 ⇒ 已在 §4.10.32 修掉成因** |

#### 4.10.31 🔴 **探针 `api-envelope-probe/favorite` EXIT=2 的根因：三层取证 ⇒「红的是客户端层，产品侧正确」**

> **结论先行**：RAG 的最终响应与 Go 冻结期望 **逐字节一致** ⇒ **业务侧无缺陷**；
> 红的是**客户端层** —— 共享层的 1xx 探测行数**无上界** ⇒ 击穿了客户端对 interim(1xx) 响应的硬上限。
> ⚠️ **教训：「探针红」≠「产品错」** —— 必须先做 **raw socket 抓包 + 字节级比对**再定性。

**第 ① 层：raw socket 抓原始字节**（`/tmp/g4wire/raw.py` · `sweep.py` · `timed.py`）

| 观测 | 值 | ⇒ 说明 |
|---|---|---|
| 端口 21300（`RealTomcatPort.FIRST`）上的 Tomcat 在最终响应前连发 | **N 行 `HTTP/1.1 103 Early Hints\r\n\r\n`** | 1xx 是**共享层探针写的**（不是 Tomcat 自发） |
| `n=2049` 一次采样 | `elapsed=6.73s` · `count103=12` · 最终 `200 + {"code":5003,"msg":"参数错误","data":null}` | ⇒ **行数 ≈ 耗时 ÷ 探测间隔（500ms）⇒ 无上界** |
| `n=5` / `n=2047`（走业务层） | 最终 `200 + {"code":1015,"msg":"服务繁忙","data":null}` | ⇒ **1015 与 5003 是两个不同格子**（见下方注） |
| 不带 Authorization 的 401 路径 | **零 1xx**、干净 401 JSON | ⇒ **1xx 只出现在「过了鉴权、走业务」路径** ⇒ 进一步否掉「Tomcat 自发」 |

**第 ② 层：JDK 源码（`$JAVA_HOME/lib/src.zip`）**

| 位置 | 事实 |
|---|---|
| `java.net.http/jdk/internal/net/http/Exchange.java:71-72` | `MAX_NON_FINAL_RESPONSES = Utils.getIntegerNetProperty("jdk.httpclient.maxNonFinalResponses", 8)` ⇒ **默认上限 8**（可由系统属性配置；`module-info.java:129` 亦列出该属性） |
| 同文件 `:544-550` | 计数超限即抛 `ProtocolException("Too many interim responses received: 9 > 8")` ⇒ **8 个 1xx 可过、第 9 个即炸** |
| 同文件 `:566-568` | 仅当 `request.expectContinue()` 为真才发 `Expect: 100-Continue` ⇒ 本测试未设 ⇒ **不是 Expect 导致**（假服务器 `FakeServer.java` 抓到 JDK 实际只发 4 行头：`GET` / `Host` / `User-Agent` / 空行） |

**第 ③ 层：字节级比对**

| 项 | 结论 |
|---|---|
| `tools/api-envelope-probe/favorite/expected.txt:10`（Go 冻结期望，Content-Type 按 RFC 9110 OWS 归一后） **vs** RAG 实测 | **逐字节一致 = True** ⇒ ⇒ **探针期望值是对的，RAG 实现也是对的** |

> 🔴 **1015 与 5003 不可混为一谈**（本轮差点误判）：Go 侧 `httpx.Parse` 失败 ⇒ `favorite/common/errmsg.go:16` `ErrorInvalidParam=5003`（文案「参数错误」，`:51`）；
> **进业务层后**业务失败 ⇒ `BizCodeFromError`（同文件 `:113-136`）default ⇒ `:13` `CodeServerBusy=1015`（文案「服务繁忙」，`:48`）
> ⇒ ⇒ **两格对应两个不同的失败阶段** ⇒ **不可把 1015 当成「参数超限」的答案**。

> ⚠️ **已排除的假设**（每条都有取证，不是推测）：
> 「探针红 = 我的改动引入」**错**（`cd893d1` 早于第十二次全量 ⇒ 12/13 轮运行期上限相同）·
> 「端口被别人占」**错**（监听者就是本测试自己的 JVM）·「`expected.txt` 被改 / Go 侧输出漂移」**错**（sha256 一致）·
> 「Tomcat 自发 1xx」**错**（401 路径零 1xx）·「JDK 发 `Expect: 100-continue`」**错**（抓包证否）。

#### 4.10.32 🔴🟢 **1xx 探测行数上界**（已落码 `15cf93a`）—— 共享层 ⇒ **10 个 api 组件全部适用**

| 项 | 值 |
|---|---|
| **为什么必须落** | 共享层的「客户端断连主动探测」每片等待醒来就往 socket 写一行 1xx ⇒ **行数 ≈ 耗时 ÷ 探测间隔 ⇒ 原本无上界** ⇒ ⇒ **任何耗时 > ~4s 的请求**（8 × 500ms）都会让 JDK `java.net.http` 客户端**直接抛异常、读不到最终响应** |
| **落点 ①** | `service/common/src/main/java/com/rag/common/observability/GoStyleClientDisconnectProbe.java`<br>新增 `MAX_PROBE_LINES_PROPERTY`（`rag.common.http.disconnect-probe-max-lines`）· `DEFAULT_MAX_PROBE_LINES = 4` · `State.BUDGET_EXHAUSTED` · `maxProbeLines()` · 计数**按请求**（request attribute）· 预算闸门排在**反射链之前** · 类 javadoc 补「为什么必须有行数上界」一节（含 JDK 源码取证） |
| **落点 ②** | 同目录 `HttpMiddleware.java` ⇒ 识别 `BUDGET_EXHAUSTED` ⇒ **每请求记一条 INFO**；⚠️ **等待语义完全不变**（总窗口既不延长也不缩短）⇒ **fail-open** |
| **落点 ③（新守门）** | `service/common/src/test/java/com/rag/common/observability/GoStyleClientDisconnectProbeBudgetGateTest.java`（**3 条**）—— ① 线上 1xx 行数 ≤ 预算 ② **真 JDK `HttpClient`** 能读到最后响应 ③ 预算是**按请求**的（第二个请求不吃光前一个的预算）<br>⇒ **真 Tomcat + 真 JDK 客户端**（§3.3：mock response 的 `write()` 永远不失败 ⇒ 那种测试是「错误答案的守卫」） |
| **落点 ④（顺带订正）** | `GoStyleTimeoutResponseRaceGateTest.java` ⇒ **归因错误**：原注释称 103 是「Tomcat 11 自发」⇒ 实测是**共享层探针**写的（401 路径零 1xx ⇒ 若容器自发，401 也该有）⇒ **不影响该用例判定**（两种来源下都要跳过 1xx），但影响「谁负责 1xx 行数」 |
| **落点 ⑤（顺带订正）** | `GoStyleClientDisconnectProbe` 类 javadoc 的签字档指针：`SD-2026-10-25-02`（**已撤销**）⇒ **`SD-2026-10-25-04`（✅ 已签字 2026-10-25）** |
| **为什么取 4 而不是 8** | 客户端硬上限 = JDK 默认 **8** ⇒ 取**一半** ⇒ 即便客户端把上限配到 4 也炸不了 ⇒ ⚠️ **不要写成 8**（那就没有余量） |
| **变异验证（各红**且仅红**一条）** | **M1** 闸门差一（`written > max`）⇒ 只红 ①（实测行数=5）· **M2** 计数器改全局静态 ⇒ 只红 ③（first=0 second=0）· **M3** 默认上界 4→1000 ⇒ 只红 ②（**复现原始故障原文 `Too many interim responses received: 9 > 8`**）⇒ 三次跑完均**逐字节还原**（MD5 MATCH） |
| **回归** | `mvn -o -B -DargLine=-Xmx2g -am -pl service/common test` ⇒ **330 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS**（327 + 新守门 3） |
| 🔴 **已知代价（已写进类 javadoc）** | 预算用尽后**不再有主动探测** ⇒ 超长请求里客户端中途断连**不再被提前发现** ⇒ 在飞的下游 gRPC 要等**写最终响应**时才被取消 ⇒ ⇒ **这是「保住客户端能解析响应」换来的取舍**（宁可晚取消，不可让客户端失败）⇒ ⇒ **不再改探测间隔**（那会改掉已签字偏离 `SD-2026-10-25-04` 的「延迟 ≈ 探测间隔」表述） |
| 🔴 **跨域暴露面（只登记 · 共享面待整合）** | 暴露量由各域 `observability.http-timeout` 决定 ⇒ **`article-api` = 70s**（Go 真源 `Timeout: 70000` 对齐值 ⇒ **不能靠改域 yaml 规避**）⇒ **修前最多 140 行**；其余 9 域 = 3s ⇒ 最多 6 行 ⇒ ⇒ **`article` 域最危险** ⇒ 修后一律 ≤ 4 行 |
| ⚠️ **签字档的连带** | `docs/Intentional_Deviations/global/common-tails.md` 的 `SD-2026-10-25-04` 仍写「N = 探测轮数 ≈ 慢请求秒数 ÷ 0.5s」⇒ ⇒ **该文件不属本 lane 写权** ⇒ ⇒ **只在此登记，待整合者回写**（把 N 改成「有上界（默认 4）」并补 JDK interim 上限的事实） |

#### 4.10.33 🟢 **本轮三条杂项**（`io/` 工作区噪声根因 · U+FFFD 三分类 · 收到的订正归档）

**① `io/` 工作区噪声的根因（已清 · 不是构建产物）**

| 项 | 值 |
|---|---|
| 现象 | 仓库根出现 `io/opentelemetry/api/GlobalOpenTelemetry.class` ⇒ `git status` 脏 |
| **根因** | 该文件的 **md5 == `~/.m2` 里 `opentelemetry-api-1.36.0.jar` 的同名条目**；而本仓 pin 的是 `opentelemetry-bom` **1.44.0** ⇒ ⇒ **连版本都不是构建在用的** ⇒ ⇒ 它 = 本层早前做 OTel 字节码取证时**在仓库根解 jar** 的**遗留** ⇒ `tools/**` 全仓无解压 ⇒ **不是脚本干的** ⇒ **归本层** |
| 处置 | 按**可恢复**方式删除（`mavis-trash`）⇒ 本层工作区 `git status` 现为空 |
| 🆕 **新规则（取证纪律）** | **解 jar 一律解到 `mktemp -d` 临时目录**，绝不在仓库根解（提交 `6df0090`） |

**② U+FFFD 的**三分类**（本轮口径 · 取代「全仓归零」这个非法目标）**

| 类别 | 判据 | 处置 |
|---|---|---|
| **① 真损坏** | 源码里的坏字节（`bytes` 里含 `EF BF BD`） | **按语义重建**（先追 `git log --all` 看原字是否可考；不可考才按语义补） |
| **② 故意保留** | **被测对象**（如「body 不得含替换符」的断言）· **病症样本**（`docs/Pitfalls/**`） | **一个字都不动**（改了就废掉那条测试） |
| **③ 引用/搬运** | 台账或消息里**抄了**带替换符的原文 | **写成描述**（`〈FFFD×N〉`）⇒ **文件里不留真替换符**（复制即新造） |

> ⚠️ **口径纪律**：`bytes.count(b"\xef\xbf\xbd")` = **U+FFFD 字符个数**（每字符 3 字节）⇒ **不是字节数**
> ⇒ ⇒ **「全仓归零」不是合法目标**（存在 ①/② 两类合法命中）。
> ⚠️ **扫描范围**：必须**区分「源码」与「构建产物」** ⇒ `target/` 里的旧 jar 会带着**已修掉**的坏字符 ⇒ 判据 = **构建时间 vs 修复时间**。

**③ 本轮收到的订正（归档）**

| 来源 | 订正内容 | 处置 |
|---|---|---|
| **G2** | `853767b` 的 javadoc 称「全仓 `Span.current()` 原本只有 1 处」⇒ **合并后不成立** | ✅ **已独立核准并落码** `6cb0a60` ⇒ 改为「合并前 1 处（comment-rpc `CommentFactDeliveryService.java:148`，跑在调度线程 ⇒ 碰不到 ⇒ 零回归）；合并后 2 处（like 域 `LikeSpans.java:242/:246`，落在受益面上 ⇒ **由 no-op 变真生效 ⇒ 预期行为变更，非回归**）」+ 补写「like 域 A9 端到端 E2E 必须在合并后补」「契约级守门 ≠ 端到端生效」「MQ 路径不受影响」⇒ **回执见 §4.10.34** |
| **G2** | 本层 `GoStyleRedisClientAutoConfiguration` 的 customizer **只调 `disconnectedBehavior(REJECT_COMMANDS)`**（本树实测 `:129-131` ⇒ **不调 `timeoutOptions`、不碰池**） | ✅ **已回执** ⇒ ⇒ G2 新增的 Redis 连接池（`task-rpc`）**理论上不受影响**，但「理论上」**不算数** ⇒ ⇒ **合并后仍须实测复跑其 ⑤⑥** |
| **G3** | `service/common` 里 2 处 U+FFFD | ✅ **本层已修**（`0c72969`）⇒ 并已就 §4.10.31/§4.10.32 向 G3 通报「探针红在客户端层、产品侧正确」 |

#### 4.10.6 🟢 本轮落码清单（核对日期 2026-10-27 · 最新基线 `align/g4-service-common` @ `cd893d1`）

> ⚠️ **本节已被 §4.10.34 取代**（口径截至 `cd893d1`）⇒ ⇒ **最新落码清单与最新回归口径看 §4.10.34** ⇒ ⇒ 本节按「不改写历史」原样保留。

> ⚠️ **代码态基线 = `cd893d1`**（= 本轮**最后一次**改代码的提交）⇒ ⇒ `cd893d1` **改了生产代码**（容器上限无条件化）
> ⇒ ⇒ 与「第十一次全量（§4.10.22 · 代码态 `040a201`）」**不是**同一个代码态 ⇒ ⇒ **必须重跑全量** ⇒ ⇒ **第十二次全量口径见 §4.10.26**。
> ⇒ ⇒ 上一行「代码行为自 `5a954bb` 起未变」**已被本轮打破**（`cd893d1` 改了生产代码）⇒ ⇒ 该行按「不改写历史」保留，**以本行为准**。
> ⇒ ⇒ **中间那批提交**（`ee1b797` · `2f3d5c2` · `acb6fe6` · `b71ac22` · `1e1899f` · `46f67bf`）= **全是文档**（台账）⇒ ⇒ **零代码影响**。
> ⇒ ⇒ **测试面变更**：`22dc67e`（§4.10.28 的新守门，**只加测试** ⇒ **生产代码态仍是 `cd893d1`**）⇒ ⇒ **生产行为未变**，但**用例数变了** ⇒ ⇒ **需重跑全量**（口径见 §4.10.29）。

| commit | 内容 | 落点（`文件:行号`） | A 轴 |
|---|---|---|---|
| `3b573fb` | **G1 #8**：关闭路径不得触发 web 类型的类加载 | `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:…`（`shutdown()` 只关 RPC 池）· **新增** `…/spring/HttpPoolLifecycle.java`（`@ConditionalOnWebApplication(type = SERVLET)`）· **新增** `…/spring/HttpPoolShutdownWithoutServletGateTest.java`（3 条）· **新增** `…/spring/HttpShutdownProbeWithoutServlet.java`（子进程探针） | A3 |
| `2f12236` | Redis 自动配置的两处**事实订正** ＋ **遮蔽契约**守门 | `…/redis/GoStyleRedisClientAutoConfiguration.java:…`（类级 javadoc + `@Bean` javadoc）· **新增** `…/precedence/GoStyleRedisClientOptionsPrecedenceGateTest.java`（3 条）· **新增** `…/precedence/DomainLikeRedisDisconnectedBehaviorConfig.java` | A3 |
| `2e2a84f` | **G3 单 3**：`SqlTimeoutStatusTranslator` 的「Go 出处」归因订正 + 补齐 `文件:行号` | `…/persistence/SqlTimeoutStatusTranslator.java:…` | A4 |
| `6d9c3b2` | **§2 的 A1~A9 逐轴表** ＋ **§1 抽取快照逐条重核**（本 lane 交付主体） | `docs/DRIFT/global/vs-common.md` | A1~A9 |
| `7488b3d` | 🔴 **静态池「终止即永久」⇒ 按需自愈**（修 #8 时暴露的潜伏缺陷） | `…/observability/RpcInterceptor.java:…`（`timeoutScheduler()` / `handleExecutor()`）· `…/observability/HttpMiddleware.java:…`（`httpExecutor()`）· **新增** `…/observability/ObservationPoolSelfHealingGateTest.java`（3 条） | A1 |
| `853767b` | 🔴 **G2 FB-04**：业务 handler 执行期能拿到**当前 server span** | `…/observability/RpcInterceptor.java:456`（`onHalfClose()` 里 `makeCurrent()` 的 scope 包住 handler 执行期）· **新增** `…/observability/RpcServerSpanCurrentGateTest.java`（4 条） | **A9** |
| `0c72969` | 🟢 **U+FFFD 两处**（纯注释 · 零行为变化） | `…/observability/GoStyleOtelSamplerProtocolGateTest.java:24` · `…/protection/FakeServerCall.java:13` | 无（纯注释） |
| `d56989f` | 🟢 **共享层 Filter 槽位快照守门**（4 条 · 零生产改动） | **新增** `…/spring/SharedFilterOrderSnapshotGateTest.java`（① 槽位快照 ② 预算起点不被抢占 ③ 域侧可用槽位 ④ 真容器次序） | 无（只加守门） |
| `c15dfeb` | 🟢 **G2 FB-05**：`parse` 接受 `"false"` 作为 `off` 的别名（YAML 1.1 布尔化） | `…/http/GoStyleFormLimitMode.java:…`（`parse` 加一个 `case` + `PROPERTY` javadoc + 异常消息）· **新增** `…/http/GoStyleFormLimitModeYamlBooleanGateTest.java`（5 条） | **A3** |
| `5a954bb` | 🟢 **G3 反馈单 轴②**：form 闸门判据补「**域自捕获**」第一判据 ⇒ favorite 语义答案 = `OFF` | `…/http/GoStyleFormLimitMode.java:…`（`DomainReachability` 加 `goSideHandlesParseError` + `selfConsistent` 两规则 + 表的 favorite 行 + **删** `REGISTERED_DEVIATIONS` 的 favorite 条 + `Mode.OFF`/类 javadoc）· `…/http/GoStyleTomcatParameterLimitCustomizer.java:…`（订正「无分叉 / favorite 零改动」等三处陈述）· `…/http/GoStyleFormValues.java:…` · `…/http/GoStyleFormLimitExceededException.java:…` · **重写** `…/http/GoStyleFormLimitSemanticCriterionTest.java`（6 条）· 另 2 个测试类 javadoc 订正 | **A3** |
| `040a201` | 🟢 **G1 约束② 的「共享层那一半」闭合**：新增断言 ⑤「共享层**产出**的带 `@Order` 的 Filter 全集 == 快照」⇒ ⇒ **新增第 8 个共享层 Filter 必红** ⇒ ⇒ 逼它显式登记槽位 | `service/common/src/test/java/com/rag/common/spring/SharedFilterOrderSnapshotGateTest.java:…`（⑤ `everySharedLayerFilterIsRegisteredInSnapshot` + `sharedLayerClassesRoot()` + `enumerateOrderedFilters()`）⇒ ⇒ **只改测试面** ⇒ ⇒ **零生产改动 / 零运行时行为变化** | 无（只加守门；**协调约束**的机器保障，**不改任何 A 轴结论**） |
| `cd893d1` | 🔴 **容器上限的抬升与闸门的有无解耦**（G3 反馈 (b)）＝ **本轮唯一改生产代码的提交**：删掉 `if (mode == OFF) return;` ⇒ **一律抬到 10001**；构造函数改为**只校验** `mode`（fail-fast 不丢） | `service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:…`（`customize()` + 构造函数 + 类级 javadoc「两轴解耦」一节）· `…/http/GoStyleFormLimitMode.java:…`（`Mode.OFF` 的 javadoc）· `…/test/java/com/rag/common/http/GoStyleFormLimitModeTest.java:…`（类 javadoc）· **新增** `…/test/java/com/rag/common/http/GoStyleParamLimitOffModeGateTest.java`（**3 条**）· `…/redis/GoStyleRedisClientAutoConfiguration.java:…`（**纯注释**：javadoc「三份」→「五份」） | **A3**（超时/deadline 的失败语义）＋ **A1**（Redis 断连的 fail-fast/fail-open 语义 · 仅 javadoc） |
| `22dc67e` | 🟢 **把「不得新建域内 Redis 副本」从台账禁令落成机器门禁**（G2 的显式禁止项 ⇒ 台账拦不住人） | **新增** `…/redis/GoStyleRedisClientShadowingGateTest.java`（**3 条** · 变异 M1/M2/M3 各专属）⇒ ⇒ **零生产改动** | 无（只加守门；**把 §4.10.24 的既有结论变成机器可执行** ⇒ **不改变任何 A 轴结论**） |

**回归口径与结果（第七次全量 · 核对日期 2026-10-27）**

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify`（**全量**，不是 `-pl` 子集） |
| 结果 | ✅ **BUILD SUCCESS / `MVN_EXIT=0`** |
| 模块数 | **52**（Reactor Summary 计数） |
| 用例数 | **2457**（**29 个有测试的模块**逐模块求和 ⇒ 非按行序 tail） |
| failures / errors / skipped | **0 / 0 / 4** |
| 耗时 | **20:28 min**（09:19:51 起跑 ⇒ 09:40:19 完成） |
| 相对上一轮 | 第五次 2448 ⇒ **+9**（本轮新增 3×3 条守门：关闭路径 / 遮蔽契约 / 池自愈） |
| 逐模块明细 | `service/common` **307**（304 + 3）· `article-rpc` **154**（第六次同模块 2 条失败 ⇒ 本轮 0）· 其余 27 个模块与第五次逐条一致 |
| **双侧探针**（同批次复跑） | `run-parity.sh` ⇒ **EXIT=0 · 56 例**（一致 54 · 不一致 0 · 已签字偏离 2）· `run-rpc-timeout-parity.sh` ⇒ **EXIT=0 · 3 例**（两侧一致） |
| **三门禁**（同批次复跑） | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| 写权自证 | 本轮改动**全部**落在 `service/common/**` ＋ `docs/DRIFT/global/vs-common.md` ⇒ **零域侧改动** |

> ⚠️ **第七次之前的那次（第六次）是 BUILD FAILURE** ⇒ 失败与修法见 §4.10.8 ⇒
> ⇒ **前五轮全量都绿，是因为 #8 的缺陷恰好挡在前面** ⇒ 那不是「本来就对」。

#### 4.10.34 🟢 **本轮落码清单（最新）**（核对日期 2026-10-27 · 最新基线 `align/g4-service-common` @ `15cf93a`）

> ⚠️ **代码态基线 = `15cf93a`**（= 本轮**最后一次**改生产代码的提交）⇒ ⇒ 其后的提交若只动台账 ⇒ **生产代码态仍是 `15cf93a`**。
> ⇒ ⇒ **本轮的生产代码相对第十三次全量（§4.10.30 · 代码态 `cd893d1`）**变了**（`15cf93a` 改了 1xx 探针的行数上界）
> ⇒ ⇒ ⇒ **合并前必须重跑全量**（口径口径以本节的 `service/common` 330 为准，全量数字待重跑后回填）。

| commit | 内容 | 落点（`文件:行号`） | A 轴 |
|---|---|---|---|
| `15cf93a` | 🔴 **1xx 探测行数设「每请求上界」**（默认 4）⇒ 慢请求不再击穿客户端 interim 上限（§4.10.31 根因 ⇒ §4.10.32 修法） | `…/observability/GoStyleClientDisconnectProbe.java`（`MAX_PROBE_LINES_PROPERTY` / `DEFAULT_MAX_PROBE_LINES=4` / `State.BUDGET_EXHAUSTED` / `maxProbeLines()` / `probe()` 预算闸门 / 类 javadoc）· `…/observability/HttpMiddleware.java`（`BUDGET_EXHAUSTED` 分支）· **新增** `…/observability/GoStyleClientDisconnectProbeBudgetGateTest.java`（3 条 + 变异 M1/M2/M3）· `…/observability/GoStyleTimeoutResponseRaceGateTest.java`（归因订正） | **A5**（HTTP 超时/断连的失败语义）＋ **A3**（共享层的跨域暴露面） |
| `6cb0a60` | 🟢 **订正 `853767b` 的 javadoc 计数口径**（G2 FB-04）：「合并前 1 处 / 合并后 2 处」分写 + 受益面说明 | `…/observability/RpcInterceptor.java`（`onHalfClose()` 的类内 javadoc「计数口径（2026-10-27 订正 · G2 反馈单 FB-04）」小节） | **A9**（业务 handler 执行期的 span 可见性） |
| `cc6e38e` | 🟢 修 `tools/errmsg-probe/README.md:59` + `tools/sql-statement-probe/favorite/compare.py:561` 两处 **U+FFFD 真损坏**（G2 转派） | tools 面（G2 明确转派 ⇒ 属本层可写面） | 无（纯文本） |
| `f68a9cd` | 🟢 修 `component-semantic-verification/springboot-semantic-model.md` 两处 **U+FFFD 真损坏**（无人拥有面 ⇒ 本层承接） | 同上 | 无（纯文本） |
| `01b9c4c` | 🟢 裸 socket 空响应从不透明 `-1` 改成可读失败（诊断可用性） | `…/observability/**`（测试侧工具方法） | 无（只改测试） |
| `6df0090` | 🟢 `io/` 工作区噪声的**根因定位**（md5 对上 `~/.m2` 旧版 1.36.0 jar ⇒ 不是构建产物）＋ 已清 | 本台账 | 无（零落码） |
| `22dc67e` | 🟢 「不得新建域内 Redis 副本」的**机器门禁**（源文件扫描守门 · 3 条 + 变异 M1/M2/M3） | 新增 `…/redis/GoStyleRedisClientShadowingGateTest.java` | 无（只加守门） |
| `a553a7b` / `c164e63` | 🟢 台账 §4.10.28 / §4.10.29（+ 就地订正 §4.10.24.2/.3） | 本台账 | 无 |

**回归口径与结果（第十三次全量 · 核对日期 2026-10-27 · 代码态 `cd893d1` + `22dc67e`）**

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify -DargLine=-Xmx3g`（**全量**，不是 `-pl` 子集） |
| 结果 | 🔴 主跑 `BUILD FAILURE` / `MVN_EXIT=1`（**预期内**：fail-fast 停在 [46/52] `favorite-api`）⇒ ⇒ **补跑 ② 全绿 / `BUILD SUCCESS` / `MVN_EXIT=0`** |
| 模块数 | **52**（Reactor）· **29 个有测试模块** |
| 用例数 | **2477**（逐模块 surefire 汇总行求和 ⇒ **去重后**；详见 §4.10.30） |
| failures / errors / skipped | **1 / 0 / 4**（那 1 条 = `favorite-api` 的**旧**断言 5003 ⇒ **预期内**） |
| 耗时 | 主跑 **19:12 min**（14:09:08 起）＋ 补跑 ② **3:13 min**（14:36:24→14:39:37） |
| 相对上一轮 | 第十二次 2474 ⇒ **+3**（= `22dc67e` 的新守门） |
| **三门禁** | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **台账三门禁** | `check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=0** |
| **三探针** | `run-parity.sh` **EXIT=0 · 56 例** · `run-rpc-timeout-parity.sh` **EXIT=0 · 3 例** · 🔴 `api-envelope-probe/favorite` **EXIT=2** ⇒ ⇒ **根因见 §4.10.31 ⇒ 产品侧逐字节正确** |
| **写权自证** | 本轮改动**全部**落在 `service/common/**` ＋ `tools/**`（G2 转派 + 无人拥有面）＋ 本台账 ⇒ **零域侧改动** |

**`15cf93a` 之后的增量回归（合并前需重跑全量）**

| 项 | 值 |
|---|---|
| 命令 | `mvn -o -B -DargLine=-Xmx2g -am -pl service/common test` |
| 结果 | ✅ **Tests run: 330, Failures: 0, Errors: 0, Skipped: 0 · BUILD SUCCESS**（327 + 新守门 3） |
| 变异验证 | M1 / M2 / M3 **各红且仅红一条断言** ⇒ 三次跑完均**逐字节还原**（MD5 MATCH） |

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
      —— ⚠️ **共享层本身没有独立进程**（它是库）⇒ 等价形态 = **让承载它的组件真启动一次**：
      ① 第七次全量含各组件的**真 fat-jar 启动门禁**（`…RuntimeBootGateTest`）⇒ 全部真起来过；
      ② 本波新增的 `HttpPoolLifecycle` / `GoStyleRedisClientAutoConfiguration` / `GoStyleGrpcClientBreakerConfiguration`
      都在**真容器**里被装配（守门用真 Tomcat / 真 gRPC server）；
      ③ 🔴 **这一条在本轮之前是「假完成」的** —— §4.10.8 记着：修 #8 之前，`RpcInterceptor.shutdown()`
      **从未在纯 gRPC 进程里真正执行过** ⇒ **那条路径根本没被走到** ⇒ ⇒ 本轮才第一次真正走到。
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期** ⇒ **2026-10-27 完成**（`6d9c3b2`）
      ⚠️ 表内含 **3 条诚实边界**（2 轴 N/A ≠ 已验 · A5 落点很薄 · A1/A3 仍半开）⇒ **不得读成「9 轴全绿」**。
- [x] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
      ⇒ `bash tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0 · 56 例**（一致 54 / 已签字偏离 2 / 不一致 0）⇒ **A6 的 L4 证据**；
      `bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0 · 3 例**（`client_deadline_ctx` 一致）⇒ **A1 的 L4 证据**。
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）⇒ **2026-10-27 完成**（`6d9c3b2`）
      ⚠️ **未复核的条目刻意不补核对日期**（「没查」≠「查过没问题」）⇒ 净结果：**无一条被推翻**，**2 条口径过期已就地订正**（E38② 的 ① 层 · E65 的附注）。
- [x] §3 的域侧修法**落码 + 守门**（含变异验证）⇒ 共享层侧全部完成（`1fb26b2` Redis 断连 · `138b6ef`+`0b4d10d` breaker 键 · `9690b04` 路径归一 · `182fc32` form 判据 + 保护链只读入口 · `a1a2e05` MP 排序桥 · `3b573fb` 关闭路径 web 类型 · `2f12236` 遮蔽契约守门 · **`853767b` A9：handler 执行期 current server span（G2 FB-04）**）
- [x] 🆕 **G4 写权面内的 U+FFFD 自修**（`0c72969`，2 处 · 纯注释 · 零行为变化）⇒ 详见 §4.10.10
      ⚠️ 来源是 **G3 的全仓扫描** ⇒ ⇒ **跨 lane 的发现要落到「谁拥有谁修」**：落在本 lane 写权面的**本层自修**，落在别人域的**转派**，落在无人拥有面的**只登记**（§4.10.7 ⑥）。
- [x] 🆕 **A5 端口段表（global 协调职责）落定**（§4.10.19）⇒ **已占 8 段 ＋ 预分配 5 段 ＋ 2 域 N/A ＋ OS 临时段禁占** ⇒ ⇒ **全表无重叠**
      ⇒ **本层零落码**（`RealTomcatPort` / `RealGrpcPort` 是**域内测试支撑类** ⇒ 域侧文件 ⇒ 域侧改）⇒ ⇒ **1 条转派 G3**（favorite-rpc 段 `21400–21499` **嵌套在** favorite 探针段 `21400–21599` 内 ⇒ 两侧都在 G3 写权面内 ⇒ 建议迁到本层预分配的 `22100–22199`）＋ **1 处本层归因订正**（探针段**不是**「本层 `tools/**` 占用」⇒ 是 favorite 域的探针）。
- [x] 🆕 **G1 约束② 的「共享层那一半」闭合**（§4.10.21 · 提交 `040a201`）⇒ **新增守门 ⑤**：共享层**产出**的带 `@Order` 的 Filter 全集 **==** 快照 ⇒ ⇒ **新增第 8 个共享层 Filter 必红**
      ⇒ ⇒ **换判据**（不采用「域内真装配面」，也不用已撤回的类路径扫描）⇒ ⇒ **枚举本模块自己的编译产物**（code source 取根 ⇒ 与工作目录无关）⇒ ⇒ 变异 A（产品侧·多一个）**红在目标断言上**、变异 B（判据侧·少一个）**只有 ⑤ 红**、`javap` 字节码层独立复核 **+2~+8 逐条一致** ⇒ ⇒ **只改测试面 ⇒ 零生产改动 / 零运行时行为变化**
      ⇒ ⇒ 🔴 **域侧那一半仍未闭合**（「**域侧**新增 Filter ⇒ 不得吃掉 +4/+5/+6」）⇒ ⇒ **需域侧真装配面守门**（域侧落码 ⇒ 本层不代做）
- [x] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）—— ⚠️ **`_README.md` §七之二属共享面 ⇒ 本 lane 不写**（合并后由整合者统一更新）⇒ **G3 四张 + G2 三张 + G1 八张共 15 张已登记在 §4.3~§4.10**（G1 七张的逐条定性见 §4.9.1 · 第八张见 §4.10.1）
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）⇒ **本 lane 全程未写**；⇒ 需要回写的 **5 条**已登记在 **§4.10.7**（含 `tools/difftest/flyway-concurrent-verify.py` 漏记 `article-rpc` · `AGENTS.md:173` 漏记 `article-rpc`）。
- [x] 🆕 **1xx 探测行数上界**（`15cf93a`）—— 共享层 ⇒ **10 个 api 组件全部适用**（§4.10.32）
      ⇒ ⇒ 守门 3 条 ＋ **专属变异 M1/M2/M3**（各红且仅红一条）⇒ ⇒ `service/common` **330 条全绿**
      ⇒ ⇒ 🔴 **待整合者回写 1 条**：`docs/Intentional_Deviations/global/common-tails.md` 的 `SD-2026-10-25-04`
      仍写「N = 探测轮数 ≈ 慢请求秒数 ÷ 0.5s」⇒ **N 现在有上界（默认 4）** ⇒ 建议补「客户端 interim 上限」的事实
      ⇒ ⇒ **该文件不属本 lane 写权 ⇒ 本层只登记、不代改**（§4.10.32 末行）
