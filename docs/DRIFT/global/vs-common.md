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
| **E65** | 🔴 OPEN ＋ 附注「**`applyTo` 是 no-op**」 | 🔴 **仍 OPEN，但附注错**：`applyTo` **不是 no-op** —— `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:110-119` 实测会 `stub.withDeadlineAfter(remainingNanos, NANOSECONDS)`（`:115` 算 `remainingNanos` · `:117`/`:119` 两个 `withDeadlineAfter` 分支）⇒ ⇒ 且有**真实调用点** —— 🆕 **2026-10-27 复测定正：全仓生产代码共 6 处，完整模块路径逐条如下**（原写「3 处 + 等」且**无完整路径** ⇒ 复核者定位不到）：<br>① `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/client/FollowRpcClient.java:123`<br>② `service/message/message-api/src/main/java/com/rag/message/message/api/client/MessageRpcClient.java:87`<br>③ `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/client/UserRpcClient.java:46`<br>④ `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/client/ArticleRpcClient.java:50` ← **原台账未列**<br>⑤ `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/client/FavoriteRpcClient.java:136` ← **原台账未列**<br>⑥ `service/article/article-api/src/main/java/com/rag/article/article/api/client/ArticleRpcClient.java:68` ← **原台账未列**<br>⇒ ⇒ ⚠️ **注意：另有一批**出向 client** 走的是 `remainingMillis()` 手搓、不是 `applyTo`**（`like-api/LikeRpcClient.java:138` · `comment-api/CommentRpcClient.java:97` · `task-api/TaskRpcClient.java:106` · `article-api/UserRpcClient.java:83`）⇒ ⇒ **E65/U15 的「统一载体」改造要覆盖的是这两类合计 10 处出向 stub，不是 6 处**（Jason 已拍板走 global 统一载体 `applyTo(stub)` ⇒ ⇒ **本轮不改**）⇒ ⇒ ⚠️ **别把 `hot-api/HotArticleQueryService.java:102` 算进来** —— 那是**本地查询**读剩余预算，不是出向 stub。⇒ **真正的不继承发生在 rpc 进程**：预算 ThreadLocal **只**由 `HttpMiddleware.java:324`（`begin`）/ `:356`（`clear`）写入（🆕 **行号已订正**：原写 `:304`/`:336` 是**陈旧行号** ⇒ 实测 `HttpMiddleware.java:324` = `GoStyleRequestDeadline.begin(deadlineNanos)` · `:356` = `GoStyleRequestDeadline.clear()`）⇒ rpc 进程无 HTTP 入口 ⇒ `DEADLINE_NANOS` 恒 `null` ⇒ `applyTo` 原样返回 stub ⇒ **rpc→rpc 出向不挂 deadline** | 🔴 **OPEN**（**描述订正**） |
| **E66** | 🔴 OPEN | 🔴 **仍 OPEN** ⇒ 本轮**转立项**（路线已探明：Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline ⇒ 零域侧改动；**硬约束**：Boot 4 `LettuceConnectionConfiguration.createClientOptions` 字节码顺序 = offset 146 **先**写 `timeoutOptions` → offset 151 **才**应用 customizer ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖运维配置**；🔴 **2026-10-27 订正（G2 独立核实 · 本层采纳）**：`offset 146` 写的是**硬编码 `TimeoutOptions.enabled()`**，**不是** `spring.data.redis.timeout` ⇒ ⇒ 后者走 `:173-174` 的 `builder.commandTimeout(...)` ⇒ 落 `LettuceClientConfiguration.getCommandTimeout()` ⇒ **RedisURI.timeout**（`ClientOptions` 上**没有** `getCommandTimeout()`）⇒ ⇒ ⇒ **顺序结论不变、归因订正**） | 🔴 **OPEN**（转立项 · 处方与回归口径已齐 ⇒ **待 Jason 拍板**） |
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
| **A1** | 超时 / deadline | `observability/RpcInterceptor`（服务端 deadline 传播）· `observability/GoStyleRequestDeadline`（HTTP 预算载体）· `observability/HttpMiddleware:324`/`:356`（预算写入/清除）· `persistence/DeadlineQueryTimeoutInjector`（SQL statement timeout）· `persistence/SqlTimeoutStatusTranslator`（57014 → `DEADLINE_EXCEEDED`）· `observability/GoStyleRecoverInterceptor` | 🟡 **机制面已对齐**（HTTP 预算 → SQL 取消 → 1015 的整条链，Go 侧逐条有出处）<br>🔴 **但 rpc→rpc 出向仍不继承 deadline（E65）⇒ 共享层侧 = 半开** | **L4**（双侧探针 `run-rpc-timeout-parity.sh` EXIT=0，`client_deadline_ctx` 一致）＋ **L1**（E65 的 ThreadLocal 写入面逐行） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | `spring/RpcServerInterceptorFactory` · `observability/RpcInterceptor` · `protection/GoStyleRpcBreakerInterceptor` · `protection/GoStyleGrpcClientBreakerInterceptor`（**本轮新落** = G3 单 2）· `protection/GoStyleGrpcClientBreakerConfiguration` · `protection/GoStyleProtectionConfiguration:80`（`@Import` 桥）· `protection/GoStyleRpcProtectionChain` | ✅ **client 侧 breaker 已进共享层，10/10 rpc 零改动生效**（`GoStyleProtectionConfiguration` 被 **10/10** rpc `@Import`）<br>🟡 **但只有「有出向调用」的 rpc 组件可观测** —— 本层**自核**（不采信转述）：`@GrpcClient` 计数 ＋ **六种出向方式**交叉核 ⇒<br>**可观测 6 个**：article-rpc **5** · comment-rpc **2** · favorite-rpc **2** · like-rpc **2** · message-rpc **2** · follow-rpc **1**<br>**N/A 4 个**：hot-rpc **0** · task-rpc **0** · user-rpc **0** · admin-rpc **0**（`@GrpcClient` / `newBlockingStub` / `newFutureStub` / `newStub` / `ManagedChannel` / `RestTemplate` / `WebClient` **全 0**） | **L3**（真容器 + 真 gRPC server 守门）＋ **L1**（10/10 `@Import` 用**剥注释后解析注解块**核出；出向面用**六种方式交叉核**） | 2026-10-27 |
| **A3** | HTTP 保护链 | `http/GoStyleFormValues` · `http/GoStyleFormLimitMode` · `http/GoStyleFormValueLimitInterceptor` · `http/GoStyleFormLimitExceededException` · `http/GoStyleFormLimitWebMvcConfig` · `http/GoStyleTomcatParameterLimitCustomizer` · `http/GoStylePathNormalizingFilter`（**本轮新落** = FB-01）· `protection/GoStyleBreakerFilter`（键改**注册期路由模板**）· `protection/GoStyleMaxConnsFilter` · `protection/GoStyleMaxBytesFilter` · `protection/GoStyleCpuSheddingFilter` | 🟡 **共享层 11 个类已验**（含本轮新增：路径归一 / breaker 键）<br>🔴 **仍缺「10 域逐域真实请求」证据**（**共享层已验 ≠ 各域已验**）<br>🟢→🟢 **补（`c15dfeb` · G2 FB-05）**：**`off` 这个取值原先在 YAML 里写不出来** —— YAML 1.1 把未加引号的 `off` 变布尔 `false` ⇒ 域侧按文档写 `mode: off` ⇒ **进程起不来** ⇒ ⇒ 已让 `parse` 接受 `"false"` 这个**精确别名**（**不放宽成任意兜底**）⇒ ⇒ **A3 轴的「配置值 → 代码」这一环此前是断的，现已闭合**（守门 5 条 + 变异 2 个，L4：真启动 + 生产同款 YAML loader）<br>⚠️ `GoStyleMaxConnsFilter` 的 MaxConns 半边 = **已签字偏离**（`SD-2026-10-25-01`）⇒ 不重复质疑<br>🟢→🟢 **补（`5a954bb` · G3 反馈单 轴②）**：**轴 ② 的判据漏了「域自捕获」这一档** ⇒ favorite 的 Go 侧 **7 个 handler 各自捕获** `httpx.Parse` 的错 ⇒ 拒绝形状 = **域自定义信封（200 + {code:5003,msg:"参数错误",data:null}）** ⇒ **既非 `raw` 也非 `envelope`** ⇒ 而旧表把它记成 `raw` ⇒ **错** ⇒ ⇒ 判据补成**两档**（① 域自捕获 ⇒ `off`；② 否则按「Go 侧是否注册 `SetErrorHandler`」分 `raw` / `envelope`）⇒ ⇒ 共享层**不装**闸门（装了会**抢先**于域自己的捕获 ⇒ 形状变 400/5001 ⇒ 与 Go 不符）⇒ ⇒ 与 `tools/difftest/check-tomcat-limits.py` 的「favorite **一项都不许**声明 mode」**不再打架**<br>🔴 **但残留没销账**：favorite 的**边界口径**漂移（**Tomcat 数总「值」数（含空值）vs Go 数非空值个数** ⇒ 总「值」数 2049~10000 且非空值 ≤2048 时 **Go 放行 / RAG 拒**）**仍在** ⇒ 归 `DRIFT-HOTAPI-030`（跨域）+ `DRIFT-FAV-API-001`（域内）⇒ ⇒ 修法要动**运行期容器上限**（= 跨域可观察行为变更）⇒ ⇒ **本层只登记 + 转派**，本轮**不改行为** | **L3**（真 Tomcat 守门：form 闸门 / 路径归一 / breaker 模板键 / Tomcat 参数上限）＋ **L1**<br>🆕 **FB-05 = L4**（真 servlet 上下文 + 真 yaml ⇒ 起得来；生产同款 `YamlPropertySourceLoader` ⇒ 断言拿到 `Boolean.FALSE`）<br>🆕 **G3 轴② = L4**（**双侧活体对拍** `tools/api-envelope-probe/favorite` ⇒ **EXIT=0** ⇒ Go 真源实测 == `expected.txt` == RAG **真 HTTP** 逐字节）＋ **变异 10/10**（每条断言都有专属变异） | 2026-10-27 |
| **A4** | 错误码 / envelope | `http/GoStyleRouterErrorResponses`（404 / 405 **字节形状**）· `http/GoStyleFormLimitExceededException` · `exception/BusinessException` · `response/Response`（⚠️ **业务码本身在域侧** api 的 `GlobalExceptionHandler.mapRPCStatusToCode`，不在共享层） | ✅ **Go 侧「1015 从哪来」已查实并订正归因**（本轮 G3 单 3：1015 = 各 api logic `switch st.Code()` 的 **default 分支**，**不是** go-zero 的错误映射）<br>⚠️ **该结论只对 users 域成立** —— Go 侧 **article 域单列了** `codes.DeadlineExceeded`（`create_article_logic.go:66`）⇒ **不外推** | **L1**（两侧逐行对照，行号逐条核实）＋ 404/405 字节形状另有 **L3**（真 Tomcat） | 2026-10-27 |
| **A5** | 端口治理 | 共享层侧**只有** `http/GoStyleTomcatParameterLimitCustomizer`（管的是**参数个数**，**不是端口**）；**端口段本身在域侧 yaml**（`server.port`）＋ `tools/difftest/check-tomcat-limits.py`（**合并收口执行者共享面** ⇒ 本层不写）<br>🆕 **但「段表」这一格归本层**（**global 协调** ⇒ G2 域台账原话「选段表归 global 协调」）⇒ ⇒ **权威端口段表 = §4.10.19** | 🟡 **共享层在本轴落点很薄** ⇒ 端口治理的**主体不在共享层**（代码侧）⇒ 本层只能给「**容器参数上限**」这一格结论<br>🆕 **段表已落定**（§4.10.19）：**已占 8 段 ＋ 预分配 5 段 ＋ 2 域 N/A ＋ OS 临时段禁占** ⇒ ⇒ **全表无重叠**<br>🆕 **共享层自有 7 处 `RANDOM_PORT` ⇒ 不纳入段治理**（§4.10.19.5 · 三条理由）<br>🔴 **新发现 1 条**：favorite-rpc 段 **21400–21499 嵌套在** favorite 探针段 **21400–21599** 内 ⇒ **两侧都在 G3 写权面内** ⇒ **转 G3 自修**（本层零落码） | **L3**（真 Tomcat 参数上限守门）＋ **L1**（段表 = 四树静态实测 · 口径见 §4.10.19.1 · 可复算）<br>⚠️ **段表是协调产物，不构成 A5 的行为级证据** | 2026-10-27 |
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

⚠️ **`global/_README.md` §七之二跨域桶 = `SHARED` 面** ⇒ 合并后由合并收口执行者统一更新，**G4 与各域都不写**
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

### 4.2 🆕 建议项登记（落点在共享面 ⇒ 需合并收口执行者或 Jason 点头）

> **性质**：本条落点 = `tools/difftest/**`（**无 lane 拥有 = 共享面**）⇒ **G4 不擅自改**
> ⇒ 按本 lane 写权登记在此 ⇒ 由合并收口执行者统一落码，或经 Jason 点头后由 G4 落码。
> **格式** = 反馈单 7 项（`_cross/cross-domain-coupling.md` §3.3 口径）。
>
> ✅ **2026-10-27 02:5x Jason 拍板：合并收口执行者合并时统一改** ⇒ **G4 不落码** ⇒ 本条**已登记待整合**。
> ⇒ 期间兜底 = **台账纪律**（引用可复算结论 + 口径 + 核对日期，**不引用「展开 N 个文件」**）。
> ⇒ ⚠️ **整合时请用「`expand()` 排除 `/target/`」这一种改法** —— **不要**用「加断言：白名单不得命中 target」
> ⇒ 那条断言上线即红（37/61 条 pattern 命中 target）⇒ 连带要改 G1/G2/G3 的白名单 = 越权。
> ⇒ 📌 **归属依据（2026-10-27 G3 指出）**：该文件**无 lane 拥有** ⇒ 按 `docs/Worktree.md` §三·1d「无主/共享面 = 合并后由合并收口执行者统一更新」
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

#### 建议项 2：**A9 轴定义措辞缺陷**（落点 = `docs/DRIFT/_cross/domain-common-lanes.md` = 共享面 ⇒ 合并收口执行者订正）

| 字段 | 内容 |
|---|---|
| 1 现象 | 总纲 A9 写「span 属性 `audit.*`」⇒ **既不完整也不准确**：① 共享层实际用 **`sea.*`**；② `audit.*` / `biz.*` 是**域特有**、且**按域不同**；③ RAG 侧目前**只落地 admin 一处** |
| 2 Go 侧出处 | `service/common/observability/observability.go:24-29`（`sea.*` 六项）· `service/comment/rpc/internal/logic/update_subject_state_logic.go:53-56`（`audit.*`）· `service/favorite/api/internal/logic/favorite/delete_favorite_logic.go:43-44`（`biz.*`） |
| 3 RAG 侧现状 | `service/common/.../observability/ObservabilityConstants.java:63+`（`sea.*`）· `service/users/admin/admin-rpc/.../observability/AdminSpans.java:91-93`（`audit.*`，**唯一已落地的域特有**） |
| 4 证据强度 | **L1 源码对照**（两侧行号已逐条核实） |
| 5 建议修法 | A9 轴改为**分层表述**：「**共享层通用属性 = `sea.*`（两侧一致）**」＋「**域特有属性按域各异**：comment = `audit.*`（Go 侧）· favorite = `biz.*`（Go 侧）· admin = `audit.*`（两侧）」⇒ 避免把**某一域的约定**写成全轴通则 |
| 6 影响面 | A9 轴的 10 份域台账（各域据此判「A9 已对齐」）· 只影响**判定口径**，不影响运行时 |
| 7 是否阻塞本波 | **否** ⇒ 登记待合并收口执行者订正 |

#### 建议项 3：**AGENTS.md §6.3 问题 4 漏记 `article-rpc`**（落点 = `AGENTS.md` = 共享面 ⇒ 合并收口执行者订正）

| 字段 | 内容 |
|---|---|
| 1 现象 | §6.3 问题 4 的「实施状态」只列了 **`favorite-rpc` 例外**（缺 `baseline-version: 0`）⇒ **漏了 `article-rpc`** ⇒ 共享面台账记载不完整 |
| 2 Go 侧出处 | N/A（配置层缺陷，非行为漂移） |
| 3 RAG 侧现状 | `service/article/article-rpc/src/main/resources/application.yml`（有 `flyway:` 段 + `baseline-on-migrate: true`，**无 `baseline-version`**）· `service/favorite/favorite-rpc/src/main/resources/application.yml`（同上） |
| 4 证据强度 | **L3 静态实测**（9 个 rpc 组件逐个 `grep -n`） |
| 5 建议修法 | §6.3 的例外清单补上 **`article-rpc`** ⇒ 两条（`article-rpc` + `favorite-rpc`）并列；⚠️ **这两条落点都在域内 yaml ⇒ 域侧自修**（G3 已认领 article 侧）⇒ **共享层只需订正「记载」，不需落码** |
| 6 影响面 | `AGENTS.md` §6.3 一处文字 · 各域台账引用该处时会误以为只有 favorite 有缺口 |
| 7 是否阻塞本波 | **否** ⇒ 登记待合并收口执行者订正 |

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

#### 建议项 4：**`check-ddl-static.py` 的工作库滞后 ⇒ 假红**（落点 = `tools/difftest/**` = 共享面 ⇒ 合并收口执行者 / 使用者）

| 字段 | 内容 |
|---|---|
| 1 现象 | `check-ddl-static.py` 在 favorite 域报 `EXIT=1`（2 处 `now()` DEFAULT 差异）⇒ **看似代码缺陷，实为工作库滞后** ⇒ 任何域的工作库未刷新都会产生同类假红 |
| 2 Go 侧出处 | Go 侧 `favorite_fact_outbox` 的 `created_at` / `updated_at` **无** `DEFAULT now()`（V7 的对齐目标） |
| 3 RAG 侧现状 | V7 脚本**已落地**（`.../V7__align_favorite_outbox_time_columns_with_go.sql`）｜**工作库** `ddl-favorite-pg/rag_favorite_db` 仍带 `now()` ⇒ **落后于 V7**｜`tools/difftest/check-ddl-static.py:317`（`rag_favorite_db` 库名来源） |
| 4 证据强度 | **L4 活体实测**（容器内 `information_schema.columns` 实查 ＋ 迁移脚本实读 ＋ 探针实跑 `EXIT=0`） |
| 5 建议修法 | **刷新工作库**（重建 `rag_<域>_db` 并按该域 V1..Vn 全量重放）⇒ **不是改代码**；并把上面那条**判别式**写进 `check-ddl-static.py` 的输出（红时提示「先跑 `tools/ddl-parity-probe/<域>/run.sh` 复核」） |
| 6 影响面 | `check-ddl-static.py` 全域输出可信度（**9 域**）· 任何按它的红去改代码的人都会被误导 |
| 7 是否阻塞本波 | **否**（favorite 已由探针证一致） |

#### 建议项 5：**`flyway-concurrent-verify.py` 的 `KNOWN_BLOCKED` 漏记 `article-rpc`**（落点 = `tools/difftest/**` = 共享面 ⇒ 合并收口执行者）

| 字段 | 内容 |
|---|---|
| 1 现象 | 该脚本的 `KNOWN_BLOCKED`（`:48-51`）**只登记了 `favorite`**（「缺 `spring.flyway.baseline-version: 0` ⇒ 共享库里 V1 被 baseline 跳过」）⇒ **同形的 `article-rpc` 未登记** ⇒ 已知缺陷清单**不完整** |
| 2 Go 侧出处 | N/A（配置层缺陷，非行为漂移） |
| 3 RAG 侧现状 | `service/article/article-rpc/src/main/resources/application.yml`（有 `flyway:` 段 + `baseline-on-migrate: true` + `table:` + `schemas:`，**无 `baseline-version`**）｜`service/favorite/favorite-rpc/src/main/resources/application.yml`（同形）｜⚠️ 该脚本对**所有**模块都会算出 `baseline_version`（缺省 = `"1"`，`:93-100`）⇒ **它看得见 article 的 `baseline=1`**，只是**已知缺陷账里没登记** |
| 4 证据强度 | **L3 静态实测**（9 个 rpc 组件逐个 `grep -n` ＋ 脚本 `:48-51` / `:93-100` 实读） |
| 5 建议修法 | `KNOWN_BLOCKED` 增补 `article-rpc`（与 `favorite` 同形同因）⇒ **只补账，不改代码**（两个 yaml 的修法都在**域侧**，域侧自修）；⚠️ 顺带**统一消息**：两个 rpc 的 yaml 注释块都写着「2026-10-25 实测修掉的真缺陷 · global 收口域 X-1」⇒ 建议域侧照抄同一段 |
| 6 影响面 | `check-flyway-concurrent.sh` 的已知缺陷账 ⇒ **AGENTS.md §6.3 问题 4 也只点了 favorite**（见建议项 3）⇒ 两处需同步订正 |
| 7 是否阻塞本波 | **否** ⇒ 登记待合并收口执行者订正 |

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

**🔴 建议项 6（🆕 · 落点 = `tools/difftest/**` = 共享面 ⇒ 合并收口执行者）**：
「**新增 `check-go-form-reachability.py`**：从 Go 真源逐域统计 `httpx.Parse` 命中数 ⇒ 与共享层 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 对照 ⇒ 让**取证书**与**判据表**不会各自漂移」。
⇒ 现状风险：共享层的表是**手工取证快照** ⇒ Go 侧哪天改了 handler，表不会自动跟着变 ⇒ ⇒ 与「写死数字不可复算」同类风险。
⇒ ⚠️ 另建议合并收口执行者把 `check-tomcat-limits.py` 的 `EXPECTED_FORM_LIMIT_MODE` 与共享层那张表**对齐**（现在两处各写一份 domain→mode ⇒ 会漂移）；⚠️ 该脚本当前把 favorite 列为「**禁止出现该属性**」（零触碰守卫）⇒ **那是对的**，别改。

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
| 1 | **A9 轴定义措辞缺陷**（轴定义应分层表述：共享层 `sea.*` 两侧一致；域特有按域：comment `audit.*`、favorite `biz.*`、admin `audit.*`） | **建议项 2**（§4.4）已登记 | `docs/DRIFT/_cross/domain-common-lanes.md` = 共享面 ⇒ 合并收口执行者订正 |
| 2 | **AGENTS.md §6.3 问题 4 漏记 `article-rpc`** | **建议项 3**（§4.4）已登记 | `AGENTS.md` = 共享面 ⇒ 合并收口执行者订正 |
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
| ⚠️ 台账口径冲突（**共享面，本层不回写**） | `docs/DRIFT/_cross/domain-common-lanes.md:125` 仍把 `DRIFT-FOLLOW-008` 列为「🔴 未修」⇒ **已过期**（签字后应从 DRIFT 侧移除）⇒ ⇒ **登记给合并收口执行者**（该文件属共享面 ⇒ 本 lane 不写） |

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

（`spring-boot-data-redis-4.0.8.jar` ⇒ `javap -c` 实测；**G2 独立复核 · 逐位吻合**：offset `142` `aload_3`（builder）→ `143` `invokestatic io/lettuce/core/TimeoutOptions.enabled()` → **`146` `invokevirtual ClientOptions$Builder.timeoutOptions`** → `149` `pop`；`150` `aload_1` → **`151` `invokeinterface ObjectProvider.orderedStream()`** → `157` `invokedynamic`（＝`lambda$createClientOptions$0` ⇒ 逐个 `customize(builder)`）→ `162` `Stream.forEach` → `168` `build()`）⇒ ⇒ **顺序结论成立** ✔
⇒ ⇒ **customizer 里再调一次 `timeoutOptions(...)` 会整体替换 `TimeoutOptions.enabled()` 那份 default source** ⇒ ⇒ **那是新漂移** ⇒ ⇒ 实现时必须**包一层 source 并显式回落**（见下）⇒ ⇒ 需要一轮专门实现。

> 🔴 **订正（2026-10-27 · G2 独立核实 · 本层采纳）**：本节原文写「timeout 来源 = `DataRedisProperties.getTimeout()`，即 `spring.data.redis.timeout`」⇒ ⇒ **这个括号里的归因不对** ⇒ ⇒ **顺序结论不变、处方实质不变、措辞要改**：
> - 源码 `LettuceConnectionConfiguration.java:229` = `builder.timeoutOptions(TimeoutOptions.enabled());` ⇒ ⇒ **入参是硬编码的 default source** ⇒ ⇒ **`spring.data.redis.timeout` 根本没进 `ClientOptions.timeoutOptions`**；
> - 它走**另一条路**：`:173-174` `if (getProperties().getTimeout() != null) { builder.commandTimeout(getProperties().getTimeout()); }` ⇒ ⇒ 落 `LettuceClientConfiguration.getCommandTimeout()` ⇒ ⇒ **RedisURI.timeout**；
> - ⇒ ⇒ `javap io.lettuce.core.ClientOptions` ⇒ **无** `getCommandTimeout()` ⇒ ⇒ **域侧守门读 `getClientConfiguration().getCommandTimeout()` 才拿得到 3s** ⇒ ⇒ **本层原句「先读回 `spring.data.redis.timeout`」的「读回位置」不准**（**读回的值**相同 ⇒ ⇒ **处方不变**）。
>
> ⇒ ⇒ **由此多出一条实现约束（本节原文没有）**：customizer 的 `TimeoutSource` 会**整体替换** `TimeoutOptions.enabled()` 那份 default source ⇒ ⇒ **新 source 在「本请求没有 deadline」时必须回落到 `getClientConfiguration().getCommandTimeout()`** ⇒ ⇒ 否则**命令会失去 3s 上限**（断连时从「3s 快速失败」退化成「无限等待」）⇒ ⇒ **这条必须有网** ⇒ ⇒ **但原句指的那根网是错的**（见下方 🔴 订正）。
>
> 🔴 **订正（2026-10-27 · G1 实测 + G2 字节码复核 · 本层独立采信）—— 原句「域侧守门 ①（`getTimeoutOptions().isTimeoutCommands()` 必须为 true）所钉的那一根网」是错的**：
> - **G1 实测**（Boot 4.0.8 + lettuce 6.8.2）：`TimeoutOptions.getSource()` 恒为 `DefaultTimeoutSource`（`getTimeout(...)` 恒返 **-1**）⇒ ⇒ `isTimeoutCommands()` **恒为 true** ⇒ ⇒ **它不是有效判据**（红不了 ⇒ 钉不住任何东西）；
> - **G2 字节码复核（更强）**：`TimeoutOptions.enabled()` = `builder().timeoutCommands().connectionTimeout().build()` ⇒ ⇒ **两个 flag 各自恒真**（`isTimeoutCommands()` **和** `isApplyConnectionTimeout()`）⇒ ⇒ **单条 flag 一律无效**；
> - ⇒ ⇒ **duration 的唯一真实出口 = `applyConnectionTimeout` 分支**（连接级 timeout / `RedisURI.timeout`）⇒ ⇒ **回落值这条约束的真实判据链 = 4 条，缺一不可**：
>
> | 判据 | 内容 | 能钉住什么 |
> |---|---|---|
> | **A** | `getTimeoutOptions().isTimeoutCommands()` == true | ❌ **恒真 ⇒ 单条无效**（只能当形态断言，不能当网） |
> | **B** | `getTimeoutOptions().isApplyConnectionTimeout()` == true | ❌ **恒真 ⇒ 单条无效**（同上） |
> | **C** | `getTimeoutOptions().getSource()` 恒返 **-1** | ✅ **能钉住「source 是 default ⇒ duration 不由 source 给」** |
> | **D** | **真实时长出口** = `RedisURI.timeout` == **3s**（读 `getClientConfiguration().getCommandTimeout()`） | ✅ **这才是唯一能钉住「3s 上限没丢」的那一根网** |
>
> ⇒ ⇒ **E66 落码后，红测试必须红在 D 上**（A/B/C 都红不了 ⇒ ⇒ **别把 A 当网**）⇒ ⇒ **域侧口径要同步改成「D 变红 ⇒ 属新回归」** ⇒ ⇒ ⚠️ **本条订正先于任何 E66 落码**（AGENTS.md §3.3 强制第 3 条：先改/删错误断言，再改实现）。

**🟢 E66 落码后的回归口径（G2 已给出 · 本层登记）**：
⇒ ⇒ **以 G2 域 task-rpc 的 6 条 Redis 守门全绿为口径** —— ① 命令超时机制启用（`isTimeoutCommands()` = true）⚠️ **① 恒真 ⇒ 单看它红不了 ⇒ 真正的网是 ③**（完整 A~D 判据链见上）· ② 连接超时 5s · ③ 命令超时 3s（读 `getClientConfiguration().getCommandTimeout()` ⇒ **= `RedisURI.timeout` ⇒ 这才是 E66 回落值的真出口**）· ④ 阳性对照（Lettuce 默认 60s）· ⑤ 连接池已启用 · ⑥ 池参数 64/16/0
⇒ ⇒ ⚠️ **⑤⑥ 与 E66 无关** ⇒ ⇒ **若 ⑤⑥ 也红 ⇒ 那是另一件事，不是 E66 引起** ⇒ ⇒ **不拿 ⑤⑥ 的红去否 E66**（也不拿 ⑤⑥ 的绿当 E66 的证据）

**另两处待定（立项时要一起答）**：
1. **载体不统一**：rpc 路径用 `RpcInterceptor.DeadlineState`（epoch 毫秒，静态 `remainingMillis()`）· api 路径用 `GoStyleRequestDeadline`（`nanoTime` 纳秒）⇒ ⇒ 要么统一，要么 source 两处都读。
2. ~~**无 deadline 时的回落值**：Go 侧无 ctx deadline 的 Redis 调用由 go-zero 自己的 `Timeout` 决定；RAG 侧今天靠 `spring.data.redis.timeout` ⇒ ⇒ 回落值必须**等于现状**，否则又是新漂移。~~ ⇒ ⇒ 🟢 **已答（G2 核实 · 本层采纳）**：回落值 = **`getClientConfiguration().getCommandTimeout()`**（＝ `spring.data.redis.timeout` 的值，**不是** `TimeoutOptions.enabled()` 的默认值）⇒ ⇒ **与现状同值** ⇒ ⇒ **不构成新漂移** ⇒ ⇒ ⚠️ **但必须显式回落** —— customizer 的 source 是**整体替换** default source 的 ⇒ ⇒ 不回落 = **命令失去 3s 上限** ⇒ ⇒ 断连时从「3s 快速失败」退化成「无限等待」。

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

#### 4.9.11 🔴 **需回写给合并收口执行者 / G1 的共享面口径订正**（本 lane 不写那些文件）

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
| 写权 | `tools/semantic-probe/README.md` **不在**合并收口执行者专属共享面清单里（那份只有 `tools/difftest/README.md`）⇒ 且本目录是**共享层的双侧探针** ⇒ 归 G4；三门禁复跑 ⇒ **EXIT=0/0/0** |

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
| ① | `tools/difftest/flyway-concurrent-verify.py:47-51` 的 `KNOWN_BLOCKED` **只登记 `favorite`** ⇒ 同形的 **`article-rpc` 没登记**（G3 的建议项 5） | `tools/difftest/**` = **共享面**（无 lane 拥有 ⇒ 合并收口执行者） | ✅ **成立**：实测 `service/article/article-rpc/src/main/resources/application.yml:53-57` 的 flyway 段**既没有 `table:`（⇒ 占用默认账本表名 `flyway_schema_history`）**、**也没有 `baseline-version: 0`** ⇒ 与 `favorite-rpc`（`:86-91`）同款缺陷（`favorite-rpc` 至少有独立 `table:`）⇒ 而该脚本对所有模块都算得出 `baseline_version`、缺省 `"1"` ⇒ **article-rpc 应同样进 `KNOWN_BLOCKED`** | **只记账**（两个 yaml 的修法在**域侧写权**；脚本在共享面） |
| ② | `AGENTS.md:173`（§3.5 表「共享 Flyway 账本」行）**只把 `favorite-rpc` 列为例外** ⇒ **`article-rpc` 漏记**（G3 的建议项 3） | `AGENTS.md` = **合并收口执行者专属** | ✅ **成立**（同上实测） | **只记账** |
| ③ | G2 的 FB-02 对拍脚本（「域的 Go handler 是否调 `httpx.Parse`」× 「该域 yaml 的 mode」） | `tools/difftest/**` = **共享面** | —— （G2 已自陈落点并要求「只登记」） | **只记账** |
| ④ | 各域台账 / `AGENTS.md` 里「**E38② 未闭合 / ① 不销本条**」的措辞**已过期** ⇒ 应读作「**① 已闭环、② 未做且已另立项**」 | 各域台账 = **域侧写权**；`AGENTS.md` = 合并收口执行者 | ✅ 成立（§1.1 重核：① 已由双侧探针坐实） | **只记账**（已在 §1.1 就地订正本 lane 自己的表） |
| ⑤ | `docs/DRIFT/_cross/domain-common-lanes.md:125`（`DRIFT-FOLLOW-008` 仍写「未修」） | `docs/DRIFT/_cross/**` = **合并收口执行者专属** | —— | **只记账** |
| ⑥ | **UTF-8 替换字符（U+FFFD）**：🆕 **「无人拥有」实为 4 处** —— `tools/errmsg-probe/README.md:59`（`未登记码的回〈FFFD×3〉结果`）· `tools/sql-statement-probe/favorite/compare.py:561`（`两侧都〈FFFD×3〉 / 两侧都没有`）· `docs/Pitfalls/message/rpc.md`（2 处）· `docs/Pitfalls/users/user/rpc.md`（1 处） | 前两处：`tools/errmsg-probe/**` · `tools/sql-statement-probe/**` = **既非合并收口执行者专属（≠ `tools/difftest/**`）· 也非任何 lane 的写权面** ⇒ **无人拥有**<br>后两处：**内容**分别属 message / users 域，但 `docs/Pitfalls/**` **不在 G2 的机器白名单** ⇒ ⇒ G2 明确表示「我不能改 ⇒ 请合并收口执行者定归属」⇒ ⇒ **同样按「无人拥有」记账**（**归属待合并收口执行者定**，本层不代改） | ✅ **成立**（G3 于 2026-10-27 的 U+FFFD 全仓扫描报出；**本层已用字节级扫描复核**：`grep -rn $'\xef\xbf\xbd'` 会因 shell 转义而**漏报** ⇒ 一律以 python 字节扫描为准）<br>🆕 **G2 的清单对账（§4.10.23.5）**：G2 那份列 **17** 个文件 ⇒ **本层 12:16 实测 18 个** ⇒ 差 1 = **G2 漏了** `tools/sql-statement-probe/favorite/compare.py` ⇒ ⇒ **补全后两套清单完全一致** ⇒ ⇒ **数字互证**<br>⚠️ 建议字**由 owner 自核**（本层只登记，不代改） | **只记账**（无人拥有 ⇒ 交合并收口执行者） |
| ⑦ | 🔴 **`AGENTS.md` 体量已到预算上限**：`tools/check-agents-budget.py` ⇒ **32,686 B / 326 行 = 99.7% · 余量仅 82 B** ⇒ 「下一次写修复记录就会顶破」 | `AGENTS.md` = **合并收口执行者专属** | ✅ **成立**（本轮实测 `EXIT=0`，但工具自己已打出告警行）<br>⚠️ 本层**未改** `AGENTS.md`（不在写权面）⇒ 只登记 | **只记账**（合并收口执行者需按该工具提示把逐条记录搬去 `docs/DRIFT/_cross/`） |

> ⚠️ **本 lane 的写权边界**：以上 **7** 条**没有一条**落在 `service/common/**` / `service/users/users-common/**` / 本文件
> ⇒ ⇒ **一律只登记，不擅自改**（AGENTS.md §5.3c：跨写权变更须同一次提交或由合并收口执行者统一处理）。

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
| **本层对 G1 后续项的表态** | G1 的 `DRIFT-USERAPI-009 §7`（MaxBytes 相对 observability 的位置：Go 在预算外 / RAG 在内）⇒ **同意其定性**：其代码修法 = 把 MaxBytes 挪到 +7 之前 ⇒ **槽位已满** ⇒ 必须**整体重排共享层顺序** ⇒ 按 AGENTS.md §5.3c「跨共享层 + 多域必须同一次提交」⇒ **不是 G4 能单方面落的** ⇒ **更便宜的路是签字**（并入 `SD-USERAPI-002` 的范围说明，方向与它一致：RAG 多记 413）⇒ **签字档归合并收口执行者，本层不代签、只登记** |

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
| 🔴 **但我树里 `task-api` 仍是 `mode: raw`** | 而 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 表里 **task-api 的语义答案是 `OFF`**（Go 侧 `task/api/**` 的 `httpx.Parse`/`ParseForm`/`GetFormValues` 命中数 = **0** ⇒ `r.Form` 从不被求值 ⇒ 两道上限**都不可达**）⇒ ⇒ **我树里存在一条「yaml 与语义答案不一致」的待修漂移**（task-api 开了本该关的闸门）⇒ **归属 task 域（G2 写权）** ⇒ **G2 在自己树里改成 `mode: "off"` 正是修它** ⇒ ⇒ 本层**只登记、不代改**<br>🆕 **2026-10-27 复核**：G2 树已改（`service/task/task-api/src/main/resources/application.yml` = `mode: "off"`（**带引号**；⚠️ **行号随树漂 ⇒ 跨树行号必须带 commit**：G2 树 `4786eb5` = `:213` · G2 树当前 HEAD `f5c9e82` = `:236` ⇒ 原写的 `:225` **在两处都对不上**））⇒ ⇒ **合并后本条自动闭环** ⇒ ⇒ ⚠️ **合并前本层树见旧值 = 「树间差异」，不是「漂移」**（同一文件在不同树上的内容/行号都可能不同 ⇒ 跨树断言必须带基线） |
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
| ✅ **G3 的 ③（守门脚本矛盾）** | 矛盾**真实存在**：`tools/difftest/check-tomcat-limits.py:156-159` 的 `FORBIDDEN_FORM_LIMIT_MODE_MODULES = {"service/favorite/favorite-api"}` 要求 favorite **一项都不许声明**（理由 `:334` =「favorite 域不在 global 写权范围 ⇒ 共享层必须零影响」），而共享层表原写 favorite = `RAW` ⇒ ⇒ **两份文件互相打架**<br>⇒ ⇒ **本轮方案使它自然消解**：表改成 favorite = `OFF`（**语义正确**）⇒ ⇒ 与「不许声明」一致 ⇒ ⇒ **`tools/difftest/**` 零改动**（该目录 = **合并收口执行者共享面**，本层不写） |

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
| **候选修法（两条）** | **(a) 域侧**：favorite 自己的 yaml 把 `max-parameter-count` 改成 **10001** ⇒ ⚠️ 与全仓守门 `tools/difftest/check-tomcat-limits.py` 的 `EXPECTED_MAX_PARAM_COUNT = 2048` **冲突** ⇒ ⇒ 需**跨域拍板**（且该脚本 = **合并收口执行者共享面** ⇒ 本层不写）<br>**(b) 共享层**：把 ①②**解耦** —— 容器上限**一律**抬到 10001（与 `mode` 无关），形状闸门仍由 `mode` 决定 ⇒ ⇒ **只改共享层一个类，不碰任何域的 yaml** ⇒ ⚠️ 但它会让 **OFF 域的运行期上限 2048 → 10001**（favorite）⇒ ⇒ **跨域可观察行为变更** ⇒ favorite 域的 `FavoriteApiRealTomcatGateTest` 会随之改 ⇒ ⇒ **必须域侧参与**<br>🟢 **(b) 的论证（2026-10-27 升级 · 依据 G2 的 task 域实测）**：**10001 = Go `net/url` 段数上限（10000）+ 1** ⇒ ⇒ 对**两类域都与 Go 对齐** —— ① **解析 query 的域**（favorite 等 9 域）：容器只当「兜底」，**判定权交还给域内/共享层的 Go 语义判据**（数非空值）⇒ 边界与 Go 一致；② **不解析 query 的域**（task）：Go 侧本就没有 2048 上限，**真实上限就是 10000 段** ⇒ ⇒ 10001 正是 Go 的等价物（2048 反而比 Go 严）⇒ ⇒ **⇒ (b) 对 10 个域全部与 Go 对齐，不是「放宽」** |
| **(b) 的唯一可观察变化** | 🔴 **只有 favorite 那一格**：「总「值」数 2049~10000 且非空值 ≤2048」的请求，从 **RAG 拒（200 + {code:5003}）** 变成 **RAG 放行（200 + 正常业务结果）** ⇒ ⇒ **这正是 Go 的行为**（Go 数非空值 ⇒ 放行）⇒ ⇒ **变化方向 = 更对齐 Go** ⇒ ⇒ 但 favorite 域的 `FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError` 断言的正是**旧**行为 ⇒ ⇒ **那一条要由 favorite 域改** ⇒ ⇒ **⇒ 仍需域侧参与 ⇒ 本层不单方面改** |
| **本层为什么不直接改 (b)** | ① 属**跨域可观察行为**变更（favorite 域的响应会变）⇒ 按「共享层缺陷转 G4，但**跨域行为变更必须让域侧参与**」⇒ ⇒ 先**登记 ＋ 转派**（G3 域内 ＋ 合并收口执行者的 `tools/difftest/**`）；② 共享层**不能假设**「Go 侧自捕获 ⇒ RAG 侧域内也自装了判据」（那是拿 RAG 实现推断 RAG 实现）⇒ ⇒ 判据必须由**域侧**提供 |
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
| **写权自证** | 本轮改动**全部**落在 `service/common/**` ＋ 本文件 ⇒ **零域侧改动**（`tools/difftest/**` = 合并收口执行者共享面 ⇒ **零改动**） |

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
| 🟢 **③ 工具口径缺口：~~只登记 ＋ 转合并收口执行者（`tools/**` = 合并收口执行者共享面 ⇒ 本层不写）~~ ⇒ 🆕 **已由本层自修并落码 `139b428`**（详见 §4.10.35）** | G2 报：`check-ledger-selfcheck.py` 第 5 层的 `CLASS_RE` 只认 `…Test` / `…GateTest` / `…IT` ⇒ ⇒ **支撑类**（各域的 `RealTomcatPort` 等）**永远无法被点名** ⇒ ⇒ 必然进「未点名」⚠️（**提示级 · 不影响退出码**）⇒ ⇒ 本层**只登记**，改动归合并收口执行者<br>🔴 **订正（2026-10-27 · 本层自己的分类判宽了）**：本行原写「`tools/**` = 合并收口执行者共享面 ⇒ 本层不写」⇒ ⇒ **这个泛化是错的** ⇒ 写权守门 `tools/difftest/check-wave-lane-ownership.py` 的 `SHARED` 清单是 **13 个具体文件**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/{cross-domain-coupling,domain-common-lanes}.md` / `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` / `docs/Worktree.md` / `tools/difftest/README.md`）⇒ ⇒ **`tools/check-ledger-selfcheck.py` 不在其中 ⇒ 无人拥有** ⇒ ⇒ 按「**无人拥有面 ⇒ 本层自修**」的既有先例（同 `component-semantic-verification/springboot-semantic-model.md`）⇒ ⇒ **本层自修**（纯 python 守门 · 零业务影响）⇒ ⇒ **不是**越权 |
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
| **结论** | ✅ **Go 侧 = MaxBytes(413) 优先于 Authorize(401)** ⇒ ⇒ **G1 的前提成立** ⇒ ⇒ 「`DRIFT-USERAPI-009 §7` 并入 `SD-USERAPI-002` 签字」的前提**成立** ⇒ ⇒ **本层不反对**（签字档 `docs/Intentional_Deviations/` 归合并收口执行者 ⇒ 本层不代签） |
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
| **`DRIFT-USERAPI-009 §7`（MaxBytes 相对 observability 的位置）** | 落点是共享层 `GoStyleMaxBytesFilter` 的 `@Order`（**归 G4**），但台账建议**并入 `SD-USERAPI-002` 的范围说明**（签字比改码便宜）⇒ ⇒ ⚠️ 签字档 `docs/Intentional_Deviations/` **归合并收口执行者** ⇒ ⇒ **本层只登记、不代签**（§4.10.18.2 已核其前提成立） |

#### 4.10.19 🟡 **端口段表（global 协调 · 本层落定）—— A5 轴的跨域那半**

> **为什么现在做**：§4.10.15.8 ② 登记了「端口段表落点 = 本文件」，但那一格里 users / article / comment / hot 四格写的是「**本层未核**」⇒ ⇒ **不能外推**。
> 本轮把四格**逐格实测**完（2026-10-27）⇒ ⇒ 缺口从「未核」变成「**已核 —— 结论是其中 3 格尚未选段、1 格 N/A**」⇒ ⇒ 再按「**选段表归 global 协调**」（G2 域台账原话：like / message 两域台账都写了这句）**预分配**未选的三格。
> ⇒ ⇒ **本层零落码**（只登记 + 协调 + 1 条转派）—— **端口治理的代码改动在各域侧**（`RealTomcatPort` / `RealGrpcPort` 是**域内测试支撑类** ⇒ 域侧文件 ⇒ 域侧改）。
> ⚠️ **本节不含「已验」字样** —— 段表是**协调产物**，不是行为结论 ⇒ **不构成 A5 的 L3/L4 证据**。

##### 4.10.19.1 口径（**可复算** —— 数字必须能被别的 lane 重跑出来）

| 项 | 值 |
|---|---|
| **计数口径** | **三层，须写明数的是哪一层**（🆕 2026-10-27 补）：<br>① **注解层**（唯一可作治理依据）：`webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT` ⇒ ⚠️ **不是**「grep 命中 `RANDOM_PORT` 的文件数」；<br>② **行层**：`grep -rn "RANDOM_PORT"` 的**命中行数**（含 javadoc / 字符串提及）；<br>③ **文件层**：含 `RANDOM_PORT` 的**文件数**（含只提及不使用）。<br>⇒ ⇒ **G4 树 @ `96b40df` 实测三层 = 22 文件 / 33 行 / 17 注解** ⇒ ⇒ **文件层 − 注解层 = 5**（5 个**只提及不使用**的文件：`message-api/MessageApiProtectionMvcTest.java` · `favorite-api/FavoriteApiRealTomcatGateTest.java` · `favorite-api/FavoriteApiEnvelopeParityTest.java` · `favorite-api/support/RealTomcatPortTest.java` · `favorite-api/support/RealTomcatPort.java` ⇒ **后两个的注释里就写着 `RANDOM_PORT`** ⇒ **命中但不是使用**）⇒ ⇒ **本节第一版差点写成「命中数」** ⇒ 口径必须写死 |
| **跨树数字不可互引**（🆕 2026-10-27 补） | 各树基线不同 ⇒ **同一口径在不同树上的数就是不同**（**实测**，一律「注解层」）：G4 @ `96b40df` = **17** · G1 @ `623d4e6` = **17** · G2 @ `2016fa6` = **12** · G3 @ `5361863` = **17** ⇒ ⇒ ⇒ **任何「真注解 N 处」的说法必须带「树 + commit」** ⇒ ⇒ 单独一个数字**不可复算 ⇒ 不可引用**（同 AGENTS.md §6「状态表纪律」）⇒ ⇒ ⚠️ **G2 报的「真注解 9」= 他们基线 `de696dd` 的数**（他们树后来前移到 `2016fa6` ⇒ 已变 12）⇒ ⇒ **两树对不上时先比 commit，不是比口径** |
| **基线 commit**（各树实测时点的 HEAD） | G4 `1e1899f` · G1 `b7d724c` · G2 `de696dd` · G3 `40bfa82`<br>🆕 **三棵树的基线都已前移**（2026-10-27 12:19 复核，**逐棵重测 ⇒ 数字未变**）：<br>· G1：`0c2a902..b7d724c` = **只动 `docs/DRIFT/follow/api.md`**（台账）⇒ **重测仍 14 处**（逐域分布逐条一致）；<br>· G2：`9ca4b2c..de696dd` = **只动 `docs/DRIFT/{like,message,task}/**` 三份台账** ⇒ **重测仍 9 处**；另核 like **21600–21699** · message **21700–21799** · task **21800–21899** 三组常量**逐条未变**；<br>· G3：`bb185dd..40bfa82` = **只动 `docs/DRIFT/comment/{rpc,vs-common}.md`**（台账）⇒ **重测仍 14 处**；`RealGrpcPort.java:61/:64`（21400/21499）与 `RealTomcatPort.java:61/:64`（21300/21399）**逐条未变**。<br>⇒ ⇒ **口径表里的三个数字（9 / 14 / 14）仍然成立** ⇒ ⇒ **不是「写死就过期」**（每次基线前移都**重测**过）<br>🆕 **2026-10-27 晚间订正**：本行末句「三个数字仍然成立」的**适用域必须收窄为「在该基线 commit 上成立」** —— 三棵树此后**又前移**（G1 → `623d4e6` · G2 → `2016fa6` · G3 → `5361863`）⇒ ⇒ **实测现值：G1 14→17 · G2 9→12 · G3 14→17**（共同增量 = 本层 `cd893d1` 的共享层第 7 处真容器守门，各树另有自己的前移）⇒ ⇒ ⇒ **本行的方法论（每次基线前移都重测）依然正确，失效的是「仍然成立」这个无条件表述** ⇒ ⇒ **现状见 §4.10.19.2 表 B** |
| **核对日期** | 2026-10-27 |
| **复现命令** | `grep -rn "webEnvironment\s*=\s*SpringBootTest.WebEnvironment.RANDOM_PORT" service --include=*.java \| grep -v "/target/"`（**逐树**跑；**不跑 `-o` 不加行号过滤** ⇒ 命中的每一行都是一处使用） |

##### 4.10.19.2 实测清点（**逐域 × 逐树** ⇒ 各树差异是「治理进度」，不是「数据不一致」）

> 🆕 **2026-10-27 订正：拆成「基线」与「当前」两张表** —— 原表只有一栏（是各树**当初实测时点**的数）⇒ ⇒ **读起来像「现状」⇒ ⇒ 台账第一版因此把 G4 树写成 16/common 6，而实测是 17/common 7** ⇒ ⇒ **根因 = 单栏表没有标注它是「基线」还是「当前」**（同 §0「写死数字不可复算 ⇒ 不引用」）。
> ⇒ ⇒ **变化来源（已核实）**：本层 `cd893d1` 引入的 `GoStyleParamLimitOffModeGateTest`（第 7 处共享层真容器守门）⇒ ⇒ **该提交是四棵树的共同祖先**（实测 G1/G2/G3 树 HEAD 都含该文件）⇒ ⇒ **基线之后各树 common 由 4/6 齐平为 7**。

**表 A —— 各树基线（= 台账 v1 当时实测时点的 HEAD）**

| 域 | 真正用到 `RANDOM_PORT` 的真容器测试 | G4 树<br>`1e1899f` | G1 树<br>`b7d724c` | G2 树<br>`de696dd` | G3 树<br>`40bfa82` |
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
| **common（本层）** | 见 §4.10.19.5 | **6** | 4 | 4 | 4 |
| **合计** | | **16** | **14** | **9** | **14** |

**表 B —— 各树当前 HEAD（🆕 2026-10-27 复核 · `git grep` 逐树逐 commit 实测 · 非目测）**

| 域 | G4 树<br>`96b40df` | G1 树<br>`623d4e6` | G2 树<br>`2016fa6` | G3 树<br>`5361863` |
|---|---|---|---|---|
| **favorite** | 0 | 0 | 0 | 0 |
| **like** | 1 | 1 | **0** ✅ 已治理 | 1 |
| **message** | 3 | 3 | **0** ✅ 已治理 | 3 |
| **task** | 1 | 1 | **0** ✅ 已治理 | 1 |
| **users** | 1 | 1 | 1 | 1 |
| **article** | 2 | 2 | 2 | 2 |
| **hot** | 2 | 2 | 2 | 2 |
| **comment** | 0 | 0 | 0 | 0 |
| **follow** | 0 | 0 | 0 | 0 |
| **common（本层）** | **7** | **7** | **7** | **7** |
| **合计（注解层）** | **17** | **17** | **12** | **17** |

> ⚠️ **G4 树基线比别的树多 2 处，是本层自己加的**：`protection/GoStyleBreakerRouteTemplateGateTest`（`138b6ef`）+ `http/GoStylePathNormalizingTomcatGateTest`（`9690b04`）⇒ ⇒ **不是「本层抄了别人的写法」**，是本层新增的真容器守门 ⇒ 处置见 §4.10.19.5。
> ⚠️ **G2 树少 5 处 = G2 已治理**（like 1 + message 3 + task 1）⇒ ⇒ **各树的数字不同是正常的** ⇒ ⇒ **段表必须逐域写，不能只写一个总数**。
> ⚠️ **G2 域报给本层的「真注解 9」= 他们基线 `de696dd` 的数**（表 A 第 4 列）⇒ ⇒ **他们树已前移到 `2016fa6` ⇒ 现为 12** ⇒ ⇒ **两树数字对不上时，先比 commit，再比口径**。

##### 4.10.19.3 权威端口段表（**已占 + 预分配** ⇒ 全表无重叠）

| 段 | 用途 | 归属 | 状态 | 出处（`文件:行号`） |
|---|---|---|---|---|
| 21300–21399 | favorite-api 真容器测试 | G3 | ✅ 已落码 | `service/favorite/favorite-api/src/test/.../support/RealTomcatPort.java:61` `:64` |
| 21400–21599 | **favorite 探针**（3 个探针共用一段） | **G3**（见 §4.10.19.6 归因订正） | ✅ 已落码 | `tools/api-full-parity-probe/favorite/run.sh:96` · `tools/sql-statement-probe/favorite/run.sh:200` · `tools/grpc-contract-probe/favorite/run.sh:186` · 另有守门 `tools/probe-gate/favorite/check-port-ownership.sh:81-88` 在**同段内**做断言 |
| 21400–21499 | favorite-rpc 真进程测试 | G3 | 🔴 **与上一行嵌套重叠** ⇒ 见 §4.10.19.4 | `service/favorite/favorite-rpc/src/test/.../support/RealGrpcPort.java:61` `:64` |
| **21500**（段 `21500–21599`） | comment-rpc 真 gRPC 进程测试 | G3 | 🔴 **落在上一行探针段 `21400–21599` 内 ⇒ 第二处重叠**（🆕 2026-10-27 实测新增 ⇒ §4.10.19.7） | `service/comment/comment-rpc/src/test/.../rpc/CommentSpanWiringTest.java:94` |
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
| 22600–22699 | comment-rpc 真 gRPC 进程测试（**迁出用**） | G3 | 🟡 **本层预分配 · 域侧未落码**（⇒ §4.10.19.7） | — |
| **22700–22999** | **Testcontainers 宿主口段**（**全域共用，按域分子段** ⇒ §4.10.39.2） | 全域 | 🟡 **本层预分配 · 域侧未落码** | — |
| 49152–65535 | **OS 临时段** | — | 🔴 **禁占**（各域真容器测试必须自选专用段）<br>🔴 **2026-10-27 实测新增禁占理由**：Testcontainers 的**随机**宿主口就落在这一段（= Docker published 段，`sysctl net.inet.ip.portrange` 实测 `first=49152 last=65535`）⇒ ⇒ **真容器测试不得继续用 Testcontainers 默认随机口** | — |

> ⚠️ **「预分配」≠「已落码」** ⇒ 域侧照 §4.10.19.3 取自己的段、复制 `RealTomcatPort`（favorite 域那份是**模板**）、改两个常量 + 补一条 `bindsToDedicatedPortSegment` 守门即可。
> ⚠️ **comment 域不分配**：实测**零** `RANDOM_PORT`（全部 MockMvc）⇒ ⇒ **N/A，不是「漏了」**（G3 域台账 A5 行自己也写了 N/A）⇒ ⇒ 将来若 comment 域起真容器测试，从 **22600+** 预留段里取。
> 🔴 **订正（2026-10-27 · G3 树 @ `dec6348` 实测）**：上面这条**已过期** —— comment-rpc **现在有真 gRPC 固定端口 `21500`**（`service/comment/comment-rpc/src/test/.../rpc/CommentSpanWiringTest.java:94` 的 `grpc.server.port=21500`）⇒ ⇒ 「comment 域 N/A / 不分配」**不再成立** ⇒ ⇒ comment 域**已占一段** ⇒ ⇒ 且它**落在 favorite 探针段 `21400–21599` 内** ⇒ ⇒ **第二处重叠** ⇒ ⇒ 见 §4.10.19.7。
> ⚠️ **「预分配」行也不是「已落码」**：上表 `22100`~`22500` 五段在**核对日期 2026-10-27** 实测**全为零命中**（G3 树全仓 `22100|22199|22200|22299|22300|22399|22400|22499|22500|22599` ⇒ 零命中）⇒ ⇒ **段仍可用**。
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

##### 4.10.19.5 🟢 **共享层自有的 7 处 `RANDOM_PORT` ⇒ 不纳入段治理**（**本层写权面内 · 本层独立判定**）

| 项 | 判定 |
|---|---|
| **事实** | 本层 `service/common/src/test/...` 有 **7** 处 `webEnvironment = RANDOM_PORT`（`GoStyleBreakerRouteTemplateGateTest` · `GoStyleFormLimitEnvelopeGateTest` · `GoStylePathNormalizingTomcatGateTest` · `GoStyleJsonContentTypeCharsetTomcatTest` · `GoStyleParamLimitGateTest` · `GoStyleJsonBodyRawHeaderTomcatTest` · **`GoStyleParamLimitOffModeGateTest`**）⇒ 其中 **3 处是本层自己加的**（`138b6ef` / `9690b04` / **`cd893d1`**）⇒ ⇒ **不是「抄了域侧写法」，是本层新增的真容器守门**<br>🆕 **2026-10-27 订正**：本节原写 **6 处**，**漏了 `GoStyleParamLimitOffModeGateTest`**（`service/common/src/test/java/com/rag/common/http/GoStyleParamLimitOffModeGateTest.java:92`）⇒ ⇒ **该文件由 `cd893d1` 引入 ⇒ 是四棵树的共同祖先**（实测 G1/G2/G3 树 HEAD 都含）⇒ ⇒ **各树 common 数现齐平为 7**（详见 §4.10.19.2 表 B）⇒ ⇒ **「共享层 6 处」这个数字在 `cd893d1` 之后即已过期** ⇒ ⇒ 本层口径自测发现的**第 1 处「自己的数字忘了更新」** |
| **为什么不纳入段治理（三条理由）** | ① **本层没有生产端口** —— `service/common/src/main/resources/` 只有 `META-INF`（无 `application.yml`）⇒ ⇒ 域侧治理的核心风险「端口注入被拿掉 ⇒ 落回 `application.yml` 的**生产端口** ⇒ 与真实部署撞端口」**在本层不存在**；<br>② **零复用收益** —— `service/common/pom.xml` **无 `test-jar` 目标**（实测）⇒ ⇒ 域侧拿不到本层的测试支撑类 ⇒ ⇒ 本层就算写一份 `RealTomcatPort`，**没有一个域能复用**（这正是 G2 台账记的「为什么各域各复制一份」）⇒ ⇒ 只会**多一份要维护的副本**；<br>③ **`RANDOM_PORT` 本身不与已占用端口相撞** —— 它是 **OS 分配** ⇒ ⇒ OS 不会把**已被 Docker 发布占用**的端口再发给本层 ⇒ ⇒ 「落进 OS 临时段」的真实代价（与 Docker 发布端口同段）**在 OS 分配语义下不成立** |
| **结论** | ✅ **共享层不占用测试端口段** ⇒ ⇒ 本层在 §4.10.19.3 的段表里**不占行** ⇒ ⇒ **7 处 `RANDOM_PORT` 保持原样，不改** |
| ⚠️ **边界（不得外推）** | ③ 只在「**端口由 OS 分配**」时成立 ⇒ ⇒ 若将来本层引入「**自己指定固定端口**」的测试 ⇒ ⇒ **必须重开这一格**（那时本层才有生产端口式的撞端口风险） |

##### 4.10.19.6 🟢 **归因订正（本层改自己的结论）**：探针段**不是**「本层 `tools/**` 占用」

| 项 | 说明 |
|---|---|
| **原写法（错）** | §4.10.15.8 ② 写「探针 **21400–21599**（**本层 `tools/**` 占用**）」⇒ ⇒ **归因错** |
| **实测** | 那三个探针的文件头自己写着域：`tools/api-full-parity-probe/favorite/run.sh:2`「**favorite 域**」· `tools/sql-statement-probe/favorite/run.sh:2`「**favorite 域**」⇒ ⇒ 它们是 **favorite 域（G3）的探针**，只是**放在 `tools/**` 目录下** ⇒ ⇒ **不是本层的写权面** |
| **为什么要订正** | 留着「本层占用」的写法 ⇒ ⇒ ① 别的 lane 会以为**共享层**要改这些文件（⇒ 抢写权）⇒ ② 将来若探针段要与 favorite-rpc 段解耦 ⇒ ⇒ 会找错人 ⇒ ⇒ **归属写错 = 协调必错** |
| **订正后** | 探针段 = **G3（favorite 域）** ⇒ ⇒ §4.10.19.4 的「两侧都在 G3 写权面内」这一条才成立 ⇒ ⇒ **转派对象正确** |
| **教训（同族）** | 「目录位置 ≠ 归属」—— `tools/**` 放在仓库根，但**文件可以是某个域的** ⇒ ⇒ 归属要**按文件内容/域标注**判，不按目录判（与「**共享判据 = 依赖度数，不是目录位置**」同族） |

##### 4.10.19.7 🔴🟢 **第二处重叠：comment-rpc 的真 gRPC 端口 `21500` 落在 favorite 探针段内**（2026-10-27 实测新增 · **本层只登记 ＋ 转派 G3** ⇒ 零落码）

| 项 | 说明 |
|---|---|
| **现象** | comment-rpc 的真 gRPC 进程测试用**固定端口 `21500`** ⇒ ⇒ `21500` **落在 favorite 探针段 `21400–21599` 之内** ⇒ ⇒ 与 §4.10.19.4 那对**同型**、但**是另一处** ⇒ ⇒ **段表原先只记了 favorite-rpc 那一对 ⇒ 漏了本对** |
| **两侧证据** | comment-rpc：`service/comment/comment-rpc/src/test/.../rpc/CommentSpanWiringTest.java:94` ⇒ `"grpc.server.port=21500"`（`:93` 的注释自称「21500–21599 段是 comment-rpc 的测试专用段」）<br>探针侧：`tools/api-full-parity-probe/favorite/run.sh:96` · `tools/sql-statement-probe/favorite/run.sh:200` · `tools/grpc-contract-probe/favorite/run.sh:186` ⇒ 三处皆 `FIRST, LAST = 21400, 21599`（另 `tools/probe-gate/favorite/check-port-ownership.sh:81-88` 在同段内断言） |
| **为什么两边互不知情** | comment-rpc 自己的注释只列了 favorite 的**域测试段**（`21300–21399` / `21400–21499`）⇒ ⇒ **它看不见 favorite 的「探针段」**（那段住在 `tools/**` 下、域侧看不到）⇒ ⇒ ⇒ **这就是「段表必须把探针段也列进去」的全部意义** —— 表里少一行 = 两边都以为对方不在自己段里 |
| **危害路径** | 与 §4.10.19.4 同款：探针的 `pick_free_port()` **绑住并 listen 后进程退出 ⇒ 端口随即释放** ⇒ ⇒ 有「端口空着」的窗口 ⇒ ⇒ 若 comment-rpc 此刻起 gRPC 服务挑中同一端口 ⇒ ⇒ 探针的 `wait_port()` **只验「连得上」** ⇒ ⇒ **连上 comment-rpc 的测试服务也算「就绪」** ⇒ ⇒ **探针结论不可信** |
| **处置（**两侧都在 G3 写权面 ⇒ **域侧能自修 ⇒ 本层不单方面改**）** | comment-rpc 迁到**预留段 `22600–22699`**（本表末行已写「将来若 comment 域起真容器测试，从 22600+ 预留段里取」⇒ **表早就把段留好了，只是没人回填**）⇒ ⇒ 只需改 `CommentSpanWiringTest.java:94` 的**一个常量** ＋ `:93` 的注释 |
| **为什么不反过来迁探针段** | 探针段被**守门钉死**（`check-port-ownership.sh:81` 的 `21400 <= p1 <= 21599` ＋ `:91` 用 21400 做「被占」样本 ＋ `:95` 断言跳过）⇒ ⇒ 迁探针 = **改 4 个工具的段常量 + 改守门** ⇒ ⇒ 改动面更大 ⇒ ⇒ **迁 comment-rpc 更省** |
| **本层这一条** | **只登记 ＋ 转派**（AGENTS.md §5.3d「域侧能自修的域侧修」）⇒ ⇒ **本层零落码** |

#### 4.10.20 🟢 **U+FFFD 全仓复扫（2026-10-27）—— 本层写权面零命中 ＋ 一处口径差异登记**

> **触发**：§4.10.19 落笔后做**字节级扫描**（`write`/`edit` 之后必做的一步）⇒ ⇒ 顺手把全仓扫了一遍 ⇒ ⇒ **本层写权面（`service/common/**` ＋ `service/users/users-common/**`）零命中**（`0c72969` 那两处修干净后未回潮）⇒ ⇒ 另有一处**与 §4.10.10 记的「13 条」对不上**，按「**写死数字不可复算 ⇒ 不引用**」纪律 ⇒ **只登记本层实测值，不改写历史记录**。

| 项 | 内容 |
|---|---|
| **本层实测（**可复算**）** | **18 个文件 / 47 处**（基线 `1e1899f` · 核对日期 2026-10-27）<br>**复现**：`python3` 逐文件读 `bytes` 判 `b"\xef\xbf\xbd" in data` ⇒ ⚠️ **不用 `grep -rn $'\xef\xbf\xbd'`**（shell 转义 ⇒ 漏报，§4.10.10 已记） |
| **其中落在本层写权面的** | **0 处** ⇒ ✅ `service/common/**` ＋ `service/users/users-common/**` **干净**（`0c72969` 修的两处未回潮）⇒ ⇒ **本层无待办** |
| **其中「无人拥有」的** | **2 处**（与 §4.10.7 ⑥ 已登记的一致）：`tools/errmsg-probe/README.md`（3）· `tools/sql-statement-probe/favorite/compare.py`（3）⇒ ⇒ **仍无人拥有 ⇒ 仍交合并收口执行者**（本层只登记、不代改） |
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
| **🔴 订正 2：第 5 个「无人拥有」文件有归属** | `component-semantic-verification/springboot-semantic-model.md`(5) **已在 `docs/DRIFT/users/admin/rpc.md:2492` 登记** ⇒ ⇒ **属 G1 域台账**，不是「无人拥有 ⇒ 合并收口执行者」 |
| **🟡 补全：G2 的清单漏了 1 个** | `tools/sql-statement-probe/favorite/compare.py`(3) —— 本层 §4.10.7 ⑥ 已登记为「无人拥有」⇒ ⇒ 17 + 1 = **18** ⇒ ⇒ 与本层 **12:16 实测的 18 文件 / 47 处** **完全对上** ⇒ ⇒ ⇒ **两套清单补全后一致 ⇒ 数字互证** |
| **「无人拥有」实为 4 处** | `tools/errmsg-probe/README.md`(3) · `tools/sql-statement-probe/favorite/compare.py`(3) · `docs/Pitfalls/message/rpc.md`(2) · `docs/Pitfalls/users/user/rpc.md`(1) ⇒ ⇒ ⚠️ 后两处的**内容**分别属 message / users 域，但 `docs/Pitfalls/**` **不在 G2 的机器白名单** ⇒ ⇒ G2 明确「我不能改 ⇒ 请合并收口执行者定归属」⇒ ⇒ **同样按「无人拥有」记账**（归属待合并收口执行者定）⇒ ⇒ **§4.10.7 ⑥ 已补全** |
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
| 🔴 `check-tomcat-limits.py` 校的是**哪一层** | 读脚本（`tools/difftest/check-tomcat-limits.py:105` `EXPECTED_MAX_PARAM_COUNT = 2048` ＋ 逐 api 模块读 `application.yml`） | ⇒ **yaml 层**（静态）⇒ ⇒ ⇒ **本次改动 yaml 一字不动 ⇒ 静态门禁不会红 ⇒ 不需要合并收口执行者动手**；<br>⚠️ 该脚本还对 favorite-api 的 `mode` 属性做**反向断言**（存在即红）⇒ ⇒ **不新增 favorite 的 mode 声明**（也不需要） |
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
| **task** | ⚠️ **树间差异**：**本层树**（`a282012` 基线）= **`raw`** ⇒ 运行期**本来就**是 10001 ⇒ **零变化**；**G2 树**已改为 `mode: "off"`（`service/task/task-api/src/main/resources/application.yml`；⚠️ **行号仅在指定 commit 上有效**：G2 树 `4786eb5` = `:213` · G2 树当前 HEAD `f5c9e82` = `:236`）⇒ **合并后** 2048 → 10001 ⇒ **仍零可观察变化**（上限不可达） | 10001（本层树） | 10001 | **零** | ✅ **task-api 全绿**（上限不可达：Go 侧 `r.Form` 从不求值 ＋ 控制器只 `@RequestBody`）⇒ ⇒ 抬上限**打不到任何可观察面** |
| **favorite** | **未声明** ⇒ 缺省 `off` | 2048 | **10001** | 🔴 **唯一一格**：总「值」数 2049~10000 且非空值 ≤2048 ⇒ **Go 放行 / RAG 由「拒（200+5003）」变「进业务层」** ⇒ ⇒ **差 1 闭合** | ✅ **实测红 1 条**（见下） |

> 🔴 **本层自己改自己的一处前提（留痕）**：提交 `cd893d1` 的说明里写「**task（`mode: "off"`）**：2048 → 10001」⇒ ⇒ 那个 `mode` 值是 **G2 树**的 ⇒ ⇒ **本层树**（`a282012` 基线）里 task 的 `mode` 是 **`raw`** ⇒ ⇒ **「task 零可观察变化」这个结论不变**（两种 mode 下上限都不可达）⇒ ⇒ 但**前提要写准** ⇒ ⇒ 本表已按实测改写。

##### 4.10.25.4 🔴 实测：favorite 域红的**只有那一条**，且**新值就是 Go 的形态**

| 项 | 实测值 |
|---|---|
| 红的那一条 | `service/favorite/favorite-api/src/test/java/com/rag/favorite/favorite/api/FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError`（favorite-api 共 **97** 条 / **红 1** / skipped 1） |
| 旧值（改前） | **200 + `{code:5003, msg:"参数错误"}`**（容器在第 2049 个条目就拒 ⇒ 域内 `FavoriteApiSupport#parameterMap` 把它映射成 Go 的解析失败出口）⇒ ⇒ **这是 RAG 独有的形态**（Go 在同一输入下是「放行」） |
| 🔴 **新值（改后）** | **200 + `{code:1015, msg:"服务繁忙"}`** ⇒ ⇒ **1015 = Go 侧 `CodeServerBusy`**（真源 `service/favorite/common/errmsg.go:13` ＋ `:48`）⇒ ⇒ 含义 = **请求真的进了业务层**（测试环境 favorite-rpc 不可用 ⇒ `status.FromError` ⇒ 1015）⇒ ⇒ ⇒ **与 Go 同形**（Go：放行 ⇒ 进业务层 ⇒ 同一出口） |
| ⇒ **结论** | ⇒ ⇒ **这次改动让 RAG 从「误拒」变成「与 Go 同形」** ⇒ ⇒ **不是新引入的漂移，是旧漂移（`DRIFT-HOTAPI-030` 那一格）被闭合** |
| ⇒ **G3 自己早已预告过** | ⚠️ **G3 树 `FavoriteApiRealTomcatGateTest.java`（原引 `:287-289`）**：「⚠️ 5003 是**当前实现**的形态，**不是** Go 的形态：Go 在同一输入下是「放行 ⇒ 200 + 业务结果」… ⇒ **若全局把 max-parameter-count 放宽到 10000（Go net/url 的真实上限），本断言需同步更新**」⇒ ⇒ 与本层的落码**完全一致**<br>🔴 **2026-10-28 订正（本层自查 · G2 提出的「跨树引用必须带 commit」纪律的实证）**：**这条引用不只是行号漂 ⇒ 它的【内容】在 G3 树当前 HEAD `7b3b27d` 里已经不存在了**（实测：该文件现在只有 `:330` 一处含「5003 是」的字样，且是**另一条**断言消息；原引的那段「5003 是当前实现的形态」注释**已不在文件里**）⇒ ⇒ ⇒ **跨树引用会【整段消失】，不只是行号变** ⇒ ⇒ **引用前必须重核内容是否还在**，不能只核行号 |
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
| 🟡 `tools/difftest/check-tomcat-limits.py` 的**文档字符串** | 仍写「`off` ⇒ 不装闸门、**也不改容器上限**」⇒ ⇒ **语义已变**（现在 off 也抬上限）⇒ ⇒ 该脚本的**逻辑**（校 yaml 层）**不受影响** ⇒ ⇒ **不会红** ⇒ ⇒ 但**文档会误导** ⇒ ⇒ **属合并收口执行者写权面 ⇒ 本层不改 ⇒ 登记** |

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
| **三探针** | `run-parity.sh` **EXIT=0 · 56 例** · `run-rpc-timeout-parity.sh` **EXIT=0 · 3 例** · 🔴 `api-envelope-probe/favorite` **EXIT=2（两次）** ⇒ ⇒ **根因见 §4.10.31 ⇒ 产品侧逐字节正确 ⇒ 已在 §4.10.32 修掉成因**（修后 **EXIT=0 · 5/5**） |

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
| **变异验证（各红**且仅红**一条）** | **M1** 闸门差一（`written > max`）⇒ 只红 ①（实测行数=5）· **M2** 计数器改全局静态 ⇒ 只红 ③（first=0 second=0）· **M3** 默认上界 4→1000 ⇒ 只红 ②（**复现原始故障原文 `Too many interim responses received: 9 > 8`**）⇒ 三次跑完均**逐字节还原**（MD5 MATCH）<br>⚠️ **前提（2026-10-27 复核补注）**：M2 的「只红 ③」**只在【隔离单类跑】时成立** ⇒ 整模块一起跑时，既有断连守门也会累加那个全局静态计数器 ⇒ 断言 ① 也可能红 ⇒ ⇒ **这不弱化守门**（整跑只会**更敏感**）⇒ ⇒ 复核已在 `/tmp` 的 `git clone --shared` 副本里**独立复现**三条（结论一致） |
| **回归** | `mvn -o -B -DargLine=-Xmx2g -am -pl service/common test` ⇒ **330 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS**（327 + 新守门 3） |
| ✅ **端到端闭环（原失败探针转绿）** | `bash tools/api-envelope-probe/favorite/run.sh` ⇒ **PROBE_EXIT=0**（修前 **EXIT=2**）· `FavoriteApiEnvelopeParityTest` **5/5 全绿**（14.574s）⇒ ⇒ **且不需要任何客户端绕过参数**（此前用 `-Djdk.httpclient.maxNonFinalResponses=0` 才绿 ⇒ 现证明**成因确实在共享层** ⇒ **那个绕过参数不再需要**）⇒ ⇒ 双侧逐字节一致（Go 真源 == `expected.txt` == RAG 实测）⇒ ⇒ **本条把 §4.10.31 的根因彻底闭环** |
| 🔴 **已知代价（已写进类 javadoc）** | 预算用尽后**不再有主动探测** ⇒ 超长请求里客户端中途断连**不再被提前发现** ⇒ 在飞的下游 gRPC 要等**写最终响应**时才被取消 ⇒ ⇒ **这是「保住客户端能解析响应」换来的取舍**（宁可晚取消，不可让客户端失败）⇒ ⇒ **不再改探测间隔**（那会改掉已签字偏离 `SD-2026-10-25-04` 的「延迟 ≈ 探测间隔」表述） |
| 🔴 **跨域暴露面（只登记 · 共享面待整合）** | 暴露量由各域 `observability.http-timeout` 决定 ⇒ **`article-api` = 70s**（Go 真源 `Timeout: 70000` 对齐值 ⇒ **不能靠改域 yaml 规避**）⇒ **修前最多 140 行**（= 70s ÷ 500ms ⇒ ⚠️ **推算值 · 未逐域实测**）；其余 9 域 = 3s ⇒ 最多 6 行 ⇒ ⇒ **`article` 域最危险** ⇒ 修后一律 ≤ 4 行 |
| ⚠️ **签字档的连带** | `docs/Intentional_Deviations/global/common-tails.md` 的 `SD-2026-10-25-04` 仍写「N = 探测轮数 ≈ 慢请求秒数 ÷ 0.5s」⇒ ⇒ **该文件不属本 lane 写权** ⇒ ⇒ **只在此登记，待合并收口执行者回写**（把 N 改成「有上界（默认 4）」并补 JDK interim 上限的事实） |

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

#### 4.10.34 🟢 **本轮落码清单（最新）**（核对日期 2026-10-27 · 最新基线 `align/g4-service-common` @ `e966a67`）

> ⚠️ **代码态基线 = `407c230`**（= 本轮**最后一次**改生产代码的提交；`e966a67` 只动测试面）⇒ ⇒ 其后若只动台账 ⇒ **生产代码态仍是 `407c230`**。
> ⇒ ⇒ **第十四次全量口径见 §4.10.37**（代码态 `407c230` 的**前一个**生产代码态 `15cf93a` + `d546f45` + `407c230` 三笔均已入账 ⇒ ⇒ §4.10.37 的口径覆盖当前代码态）。

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
| `d546f45` | 🟢 **订正 `GoStyleTomcatParameterLimitCustomizer` 的过期 javadoc**（G2 反馈）—— 「mode==OFF ⇒ 运行期 2048」与实际行为（**无条件 10001**）自相矛盾 | `…/http/GoStyleTomcatParameterLimitCustomizer.java:95-101` | 无（**纯 javadoc** · 零行为变化） |
| `139b428` | 🟢 **台账门禁第 5 层「分子/分母口径不对称」** ⇒ 支撑类不再产生**永消不掉**的覆盖缺口（§4.10.35） | `tools/check-ledger-selfcheck.py` | 无（**只改守门** · 三域输出**逐行一致** ⇒ 零行为影响） |
| `407c230` | 🟢 **独立复核（verifier · PASS）⇒ 3 条 🟡 + 2 条 🟢 全部落码**（§4.10.36） | `…/observability/GoStyleClientDisconnectProbe.java`（javadoc 三处限定 + WARN 改**每进程一次**） | 无（**纯 javadoc + 日志门闩** · 零行为变化） |
| `e966a67` | 🟢 **修 traceparent 守门的判据缺陷** —— 「等待条件弱于断言条件」⇒ 高负载下假红（§4.10.37） | `…/http/GoStyleTraceparentFilterTraceSourceGateTest.java`（等待循环改为轮询**断言的那个东西**） | 无（**纯测试面** · 零生产变化） |

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

#### 4.10.36 🟢 **独立复核（verifier）⇒ PASS ＋ 5 条发现 ⇒ 已全部落码**（2026-10-27）

> **为什么有这一节**：§4.10.32 的改动**改了生产代码**（`15cf93a`）⇒ ⇒ 按本层纪律「**结论对 ≠ 推导对**」，
> 请了一个**独立 verifier**（只读，变异实验在 `/tmp` 的 `git clone --shared` 副本里做，**不动工作树**）复核
> 8 个问题 ⇒ ⇒ **VERDICT = PASS**（无功能性缺陷）＋ 提 **3 条 🟡 + 2 条 🟢** ⇒ ⇒ **本节把 5 条逐条销账**。

| # | 复核发现 | 严重级 | 处置 |
|---|---|---|---|
| ① | **「有效探测窗口 = 上界 × 探测间隔」是个【乘积】，原 javadoc 没写** ⇒ 运维把间隔调到下限 50ms ⇒ 窗口从 2s 塌到 **200ms** ⇒ 「上界 4」会被误读成「固定 4 次、与间隔无关」 | 🟡 | ✅ **已落码**：类 javadoc 补「乘积」一节（默认 4×500ms=2s；间隔调小 ⇒ 客户端更安全但**提前发现窗口同步变短**）⇒ 提交见下 |
| ② | **`maxProbeLines()` 的 WARN 每片都打** ⇒ 一个 70s 请求打 **140 条** WARN ⇒ 并发线性放大 ⇒ **日志风暴**（我引入的） | 🟡 | ✅ **已落码**：改为 **每进程一次**（`AtomicBoolean` 门闩 + `warnMaxProbeLinesOnce()`）⇒ ⚠️ **未加守门**（静态门闩天生**进程级一次** ⇒ 断言会**顺序相关** ⇒ 写了就是假绿 ⇒ 只在 javadoc 与台账留痕） |
| ③ | **「即便客户端把上限配到 4 也不炸」表述过宽** ⇒ 只对「**按 RFC 9110 忽略 1xx** 的客户端」成立；实测 **Python `http.client` 3.11 把 `103 Early Hints` 当最终响应**（`status=103`、body 空）⇒ **1 行即失效** | 🟡 | ✅ **已落码**：javadoc 补前提 + 点名 Python `http.client` + 写明「**对这类客户端既无改善也无恶化**」（1xx 探针是已签字偏离 `SD-2026-10-25-04`）⇒ ⚠️ 本仓 `tools/difftest/matrix.py`（`urllib.request.urlopen`）· `parity-task.py`（`http.client.HTTPConnection`）**正是这类客户端** ⇒ ⇒ 它们能用是因为**那些路径快（< 500ms ⇒ 零 1xx）** ⇒ ⇒ **不是**「它们不受影响」的证明 |
| ④ | **`Expect: 100-continue` 客户端把那个 100 也计入同一个 interim 计数器**（JDK `Exchange.java:466` `nonFinalResponses.incrementAndGet()`）⇒ 计数 = 1 + 上界 ⇒ 这种客户端要再降 1（≤ 3） | 🟢 | ✅ **已落码**（javadoc 一行）⇒ 本仓无人用 `expectContinue` ⇒ 影响为零 |
| ⑤ | **M2 变异「只红 ③」只在【隔离单类跑】时成立**（整模块一起跑时，既有断连守门也会累加那个全局静态计数器 ⇒ 断言 ① 也可能红） | 🟢 | ✅ **台账补注**（不弱化守门：整跑只会**更敏感**）⇒ 落点见下 |
| ⑥ | **正面确认**：`GoStyleTimeoutResponseRaceGateTest` 的**归因订正属实** —— Tomcat 11.0.18 的 `sendEarlyHints` 只有定义、**无内部调用方**；本仓亦 0 命中 ⇒ 那行 103 **确实**出自共享层探针 | 🟢 | ✅ **无需处置**（订正成立） |

**复核给出的三条「正面结论」（独立复现，非采信转述）**

| 项 | 结论 | 复核的取证方式 |
|---|---|---|
| **上界绕不过** | 1xx 字节唯一写出点 = `GoStyleClientDisconnectProbe.java:339-341`；`probe(...)` 唯一调用点 = `HttpMiddleware.java:428`；全仓 `setStatus(1xx)`/`sendError(1xx)` **0 命中**；Tomcat 的 `sendEarlyHints` **无调用方** | 扫全 jar 常量池 + 全仓 grep |
| **计数确为按请求** | `httpReq` 是**未包装**的容器 facade（`HttpMiddleware.java:260`）⇒ attribute map 唯一；读写同在**容器线程的同一循环**（`:411-449`），worker 线程**不碰**；Tomcat `Request.attributes` 实测是 `ConcurrentHashMap` | `javap` 构造器字节码 |
| **JDK 的 interim 计数是「每请求」不是「每连接」** | `Exchange.java:97` `final AtomicInteger nonFinalResponses` 是 **`Exchange` 的实例字段**；`MultiExchange.java:72` 注释原文 *"Creates a new Exchange for each request/response interaction"* | JDK 21.0.10 `src.zip` 一手源码 |

> 🔴 **最后一条是本次修复能成立的前提**：若按**连接**计数 ⇒ keep-alive 复用会累积 ⇒ **cap=4 根本救不了长连接客户端**
> ⇒ ⇒ 复核把这条钉死了 ⇒ ⇒ **「上界」方案对 keep-alive 客户端有效**（这条不核 ⇒ 整个修复的有效性是悬空的）。
> ⚠️ 复核同时指出：**Go `net/http` 客户端对 1xx 无数量上限**（`transport.go` 的 `is1xxNonTerminal` 无限 `continue`）
> ⇒ ⇒ 「客户端硬上限 8」**只对 JDK 成立** ⇒ ⇒ 本层台账里凡写「客户端上限 8」处，均指 **JDK `java.net.http`**。

**复核的「未核实」边界（据实登记）**

| 项 | 状态 |
|---|---|
| `service/common` **全量 330 条绿** | ✅ **本层自跑已证**（`/tmp/g4-common-after-review-fixes.log` ⇒ 330/0/0/0 BUILD SUCCESS）⇒ 复核只跑了 4 个相关类（按约束不许跑全量）⇒ **两者互补，不冲突** |
| 10 个 api 模块的**活体运行期 1xx 行数 = 4** | ⚠️ **未逐域实测**（本轮是共享层内聚改动 · 零 api 改动 · 零 yaml 改动）⇒ ⇒ **只由 `GoStyleClientDisconnectProbeBudgetGateTest` 的真 Tomcat 用例证明机制成立** |
| `article-api` 70s ⇒ 「最多 140 行」 | ⚠️ **推算值**（70s ÷ 500ms）· **未实测** ⇒ ⇒ 已把台账与 javadoc 里的这个数**标注为「推算值 · 未逐域实测」** |
| 其它客户端的 interim 上限谱系（Apache HttpClient 5 / OkHttp / .NET） | ⚠️ **未核实** ⇒ ⇒ 只实证了 **JDK（8/每请求）** · **Python `http.client`（把 1xx 当最终响应 ⇒ 有效上限 0）** · **Go `net/http`（无上限）** |

#### 4.10.35 🟢 **台账门禁第 5 层的「分子/分母口径不对称」** ⇒ 支撑类不再产生**永消不掉**的覆盖缺口（已落码 `139b428`）

| 项 | 值 |
|---|---|
| **来源** | G2 报的一条**工具口径缺口**（原登记在 §4.10.7 ③ ⇒ 当时判为「只登记 ＋ 转合并收口执行者」）⇒ ⇒ **本轮独立定位后自修** |
| **成因（独立定位 · 非采信转述）** | 第 5 层的**分子**（`CLASS_RE`）只认「**测试类命名**」（`Test` / `ContractTest` / `GateTest` / `…IT`），而**分母**却统计测试源文件里的**所有**类型（`class` / `interface` / `enum` / `@interface`）⇒ ⇒ **支撑类**（`RealTomcatPort` / `FakeServerCall` 这类**不是测试类**的测试源文件）⇒ **永远无法被判定为「已点名」** ⇒ ⇒ 成为「**台账怎么改都消不掉**」的覆盖缺口 ⇒ **门禁恒红 ⇒ 门禁失效**（与本文件上方 `*IT` 那条同族：那一条是**分子**认不出命名，这一条是**分母**认得太宽） |
| **落点** | `tools/check-ledger-selfcheck.py` —— `CLASS_RE` 段（新增 `TEST_CLASS_SUFFIX_RE` / `TEST_CLASS_NAME_RE` / `TYPE_DECL_RE` 三个**单一真源**常量）· `layer5_unmentioned()`（候选集改为「文件里**所有**测试类命名的类型」） |
| 🔴 **修法方向的选择（关键）** | **对齐分母**（纯支撑类文件不再计入分母）⇒ ⇒ **不掩盖真实缺口**（真实测试类仍必须被台账点名）<br>⚠️ **另一条路是「放宽分子」**（扩大「已点名」的判定范围，让路径形态也能算点名）⇒ ⇒ **那是往「假绿」方向走** ⇒ ⇒ **本层不做** ⇒ ⇒ 只登记为备选 |
| **为什么本层可以改（写权自证）** | 写权守门 `tools/difftest/check-wave-lane-ownership.py` 的 `SHARED` 清单是 **13 个具体文件** ⇒ **`tools/check-ledger-selfcheck.py` 不在其中** ⇒ ⇒ **无人拥有** ⇒ ⇒ 按「无人拥有面 ⇒ 本层自修」的既有先例处理（同 `component-semantic-verification/springboot-semantic-model.md`）⇒ ⇒ 🔴 **同时订正本层自己的一处过宽分类**（§4.10.7 ③ 原写「`tools/**` = 合并收口执行者共享面」⇒ **泛化错误**，已就地订正） |
| **验证（零行为影响）** | 当前受检的 **3 个域**（`task` / `users` / `users-admin`）**都没有支撑类** ⇒ ⇒ 改前改后**逐行一致**（实测 `diff` 为空：`/tmp/g4-selfcheck-before.txt` vs `/tmp/g4-selfcheck-after.txt`）⇒ ⇒ 默认档 `EXIT=0` ⇒ ⇒ **对其它 lane 零影响**（不改「已点名」的判定 ⇒ 不可能让别的域**新红**） |
| **效果（面向未来）** | 一旦某个域被加进 `JAVA_ROOTS` 且它有支撑类（如 favorite 域的 `RealTomcatPort`）⇒ ⇒ **不再**产生那条消不掉的⚠️ ⇒ ⇒ **提示级输出**回到「有据可查」的状态 |

#### 4.10.37 🟢 **第十四次全量回归口径**（核对日期 2026-10-27 · 代码态 `15cf93a` + `d546f45` + `407c230`）＋ 🟢 **一条真 Tomcat 守门判据缺陷已修**

> **为什么重跑**：§4.10.32 的 `15cf93a` **改了生产代码** ⇒ ⇒ 第十三次全量口径（代码态 `cd893d1`）**已过期**
> ⇒ ⇒ 按 §5.3e「变了就重跑一次收口回归」⇒ ⇒ 本节是**当前代码态**的权威口径。
> ⚠️ **起跑方式**：`ps` 实测到 G2/G3 两棵树都在跑 maven ⇒ ⇒ **硬起第三个会 OOM**（§4.10.13 的 EXIT=137 实证）
> ⇒ ⇒ 本层用「**等空窗再自动起跑**」的看门脚本（`/tmp/g4-full14-wait-run.sh`）⇒ 判据 = 「别的 lane 有没有在跑**全量**（命令行无 `-pl`）」
> ⇒ ⇒ 域内小跑（`-pl` 子集）**允许重叠**（合计 ~5g，24g 机器安全）⇒ ⇒ 起跑前 + 起跑后**各复检一次**。

| 段 | 日志 | 结果 | 说明 |
|---|---|---|---|
| **主跑** | `/tmp/g4-full14.log` | 🔴 `BUILD FAILURE` · **10:35 min** | **fail-fast 停在第 8 个模块 `common`** ⇒ 其后 **45 个模块 SKIPPED**<br>🔴 红的 1 条 = `GoStyleTraceparentFilterTraceSourceGateTest.responseTraceparentBelongsToTheExportedSpan` ⇒ **判据缺陷**（不是被测代码）⇒ **已修**（见下）⇒ **隔离复跑 1/1 绿** |
| **续跑 ①** | `/tmp/g4-full14-rerun.log` | 🔴 `BUILD FAILURE` · **27:51 min**（`-rf :common`） | `common` **整模块绿**（330/0/0/0 ⇒ **flake 不复现**）⇒ 一路跑到 `favorite-api` 才停 ⇒ 其后 **6 个模块 SKIPPED**<br>🔴 红的 1 条 = `FavoriteApiRealTomcatGateTest.containerParameterLimitIsSurfacedAsGoParseError` ⇒ **与第十三次同一条**（**已登记**的判定权口径漂移 `DRIFT-FAV-API-001` ＋ `DRIFT-HOTAPI-030` ⇒ **不是回归**） |
| **续跑 ②** | `/tmp/g4-full14-rerun2.log` | ✅ **BUILD SUCCESS** · **3:06 min**（`-rf :favorite-rpc`） | 最后 6 个模块（`favorite-rpc` / `comment*`）全绿 |

**口径（按 surefire XML 逐模块求和 · 去重）**

| 项 | 值 |
|---|---|
| 命令 | `mvn -B clean verify -DargLine=-Xmx3g`（**全量**，不是 `-pl` 子集）⇒ 续跑用 `-rf :<模块>` |
| Reactor 模块数 | **52** |
| **有测试模块数** | **29** |
| **用例数** | **2480**（第十三次 **2477** ⇒ **+3** = `15cf93a` 的新守门 3 条 ⇒ ⇒ **口径自洽**） |
| **failures / errors / skipped** | **1 / 0 / 4**<br>（那 1 条 failure = `favorite-api` 的**旧**断言 5003 ⇒ **预期内**；`favorite-api` 1 条 skipped ＋ `favorite-rpc` 3 条 skipped = **4**） |
| 耗时 | 主跑 **10:35 min** ＋ 续跑① **27:51 min** ＋ 续跑② **3:06 min** ⇒ **合计 41:32 min** |
| **三门禁** | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **台账三门禁** | `check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=0** |
| **三探针** | `run-parity.sh` **EXIT=0 · 56 例**（一致 54 · 不一致 0 · 已签字偏离 2）· `run-rpc-timeout-parity.sh` **EXIT=0 · 3 例**（两侧一致）· `api-envelope-probe/favorite` **EXIT=0**（5/5 ⇒ **修前 EXIT=2**） |
| **写权自证** | 本轮改动**全部**落在 `service/common/**` ＋ `tools/check-ledger-selfcheck.py`（**无人拥有面**）＋ 本台账 ⇒ **零域侧改动** |

> 🟢 **🔴 那条假红的定性（三步，**不是**「隔离复跑绿就算 flake」）**
> ① **隔离复跑 1/1 绿**（`-Dtest=GoStyleTraceparentFilterTraceSourceGateTest`）
> ② **续跑 ① 里该模块整模块绿**（`common` 330/0/0/0）⇒ ⇒ 与被测代码无关
> ③ **根因是判据本身**：原等待条件等的是「**任意一个**导出请求到达」（`requestCount() > 0`），
> 而断言的是「**含这条 trace-id** 的导出到达」⇒ ⇒ **等的条件比断言弱**
> ⇒ ⇒ 在「导出本身慢」（机器负载高 / batcher 线程被抢 / 别的 lane 并发跑 maven）时，
> 3s 窗口耗尽 ⇒ **报「span 没被导出」的假红** ⇒ ⇒ **修法**：等待条件改为**断言条件本身**
> （轮询「导出字节里含这条 trace-id」，预算仍 3s）⇒ ⇒ **判据与断言同源** ⇒ 且**比原来更严**
> （不再接受「来的是别的 span」）⇒ 提交 `e966a67`（**纯测试面 · 零生产变化**）
> ⚠️ **旁证**：`service/common` 在第十三次全量的补跑①里也抖过一次（`GoStyleJsonContentTypeCharsetTomcatTest`
> 「expected 200 but was -1」）⇒ ⇒ **「真容器 + 固定等待窗口」类守门在机器负载高时是一族已知抖动源**
> ⇒ ⇒ **本层不逐条去改**（改一个还有一个）⇒ ⇒ **只登记**，等下一轮统一处理「等待窗口」这一族。

#### 4.10.38 🟢 **standby 窗口的三件实活（2026-10-27）—— 全仓 `check-*.py` 全量实跑 ＋ 跨树复核的端口段第二处重叠 ＋ 共享层 customizer 守门「已存在 ⇒ 不重复造」**

> **本节零落码** ⇒ 只做**登记 + 转派**（§5.3d「域侧能自修的域侧修」+ §5.3e「G4 随时接收反馈单 ⇒ 落码 ⇒ 回执」）。

**① 全仓 `check-*.py` 全量实跑（**19 个**）⇒ 18 EXIT=0 · 1 EXIT=1**

| 项 | 说明 |
|---|---|
| **触发** | G3 的提醒：「`check-doc-links.py` **不在** AGENTS.md §7.2 点名的 4 个标准门禁里 ⇒ ⇒ 收口回归要跑**仓里全部** `check-*.py`」⇒ ⇒ **本层照办**（`for g in $(ls tools/*.py tools/difftest/check-*.py)` ⇒ 逐个跑 ⇒ **先捕获 `$?` 再替换**，不用同批命令替换覆盖退出码） |
| **🔴 唯一的红** | `tools/difftest/check-ddl-static.py` ⇒ **EXIT=1** ⇒ ⇒ **已登记的 DDL 差异，不是本轮引入**：脚本只跑 `users` 一个域就退出 ⇒ **9 处实质差异**（`users_user.status` 可空性 **1** ＋ `DEFAULT CURRENT_TIMESTAMP` **8**）⇒ ⇒ 本轮 72 笔对 `*.sql` / `*migration*` 的改动 = **0**（`git diff --name-only a282012..HEAD -- '*.sql' '*migration*'` ⇒ **空**）⇒ ⇒ 属 AGENTS.md §0 第 2 条「**刻意保留**」那批 ⇒ ⇒ **只登记，不擅自改** |
| **⇒ 口径修正（重要）** | 本层历次口径里的「**六门禁 0**」＝ AGENTS.md §7.2 点名的 6 个装配/写权守门 **＋** 3 个台账门禁 ⇒ ⇒ **不覆盖** `check-ddl-static` / `check-doc-links` / `check-agents-budget` / `check-cross-domain-holds` / `check-deadline-{downstream,verdict}` / `check-flyway-ledger` / `check-global-lane-ownership` / `check-go-json-body-predicate` / `check-grpc-interceptor-wiring` / `check-grpc-runtime-classpath` / `check-tomcat-limits` ⇒ ⇒ ⇒ **「全部 `check-*.py` 全绿」这句话在 2026-10-27 之前**不成立**；今天成立，但它**不等于 1** ⇒ ⇒ **诚实登记**（不写「全绿」） |

**② 跨树复核 ⇒ 端口段表**第二处**重叠** ⇒ §4.10.19.7**（comment-rpc `21500` ∈ favorite 探针段 `21400–21599`）⇒ ⇒ **只登记 + 转派 G3 ⇒ 零落码**
- ⇒ 顺带**就地订正**了本表两条过期口径：① 「comment 域不分配 ⇒ N/A」**已过期**（comment-rpc 已有真 gRPC 固定端口 `21500`）② 探针段的引用清单**漏了** `tools/probe-gate/favorite/check-port-ownership.sh:81-88`（**在同段内做断言**的守门）
- ⇒ **favorite-rpc 的迁段转派单仍未处理**：G3 树 @ `dec6348` 实测 `service/favorite/favorite-rpc/src/test/.../support/RealGrpcPort.java:61` **仍** `FIRST = 21400` ⇒ ⇒ 预分配段 `22100–22199` **实测零占用**（G3 树全仓 `22100|22199` ⇒ 零命中）⇒ ⇒ **段仍可用**
- ⇒ 🆕 **comment-rpc 域内做法不一致**（新发现 · 转派）：`CommentSpanWiringTest.java:90-94` 的注释**明确写**「`port=0` 会让 OS 从临时端口段挑，而那是 **Docker 随机发布容器端口用的同一段** ⇒ `EADDRINUSE` ⇒ 抖动」⇒ ⇒ 但同域 `service/comment/comment-rpc/src/test/.../rpc/CommentRpcGrpcInterceptorWiringTest.java:101` **仍写** `"grpc.server.port=0"` ⇒ ⇒ **那个类正好踩中它自己注释里说的坑** ⇒ ⇒ 转派 G3（域侧）

**③ 共享层 Redis customizer 的「存在性守门」**已存在** ⇒ 不重复造**（回答 G3 的建议）
- `service/common/src/test/java/com/rag/common/redis/GoStyleRedisClientOptionsGateTest.java:50`（`disconnectedBehaviorIsRejectCommands` ⇒ 断言 `options.getDisconnectedBehavior()` = `REJECT_COMMANDS` ⇒ **在场且生效**）· `:61`（`customizerIsRegisteredExactlyOnce` ⇒ **不重复注册** ⇒ 即「没被遮蔽」）· `:71`（`commandTimeoutIsNotClobbered` ⇒ **不覆盖运维 `timeoutOptions`**）
- ＋ `service/common/src/test/java/com/rag/common/redis/GoStyleRedisClientShadowingGateTest.java`（源文件扫描 ⇒ **域内不得有副本**）
- ⇒ ⇒ G3 提的「共享层 customizer 存在性建议加守门」**已被现有守门覆盖** ⇒ ⇒ **本层零落码** ⇒ ⇒ **不造第二条守门守同一件事**（与 §3.3「断言『Bean 存在』≠ 断言『机制生效』」同源：已有的是**机制生效**级别的断言）

**④ 一条自证教训（同一个坑的第 N 次 —— 「零命中」先复核判据）**
- 查本表那条 `tools/grpc-contract-probe/favorite/run.sh:186` 的出处时，**第一次 grep 零命中** ⇒ 若就此判定「引用已失效」就会**误订正**一条**仍然有效**的出处 ⇒ ⇒ 复核后确认：**文件仍在、出处仍在**（`:186` = `FIRST, LAST = 21400, 21599`）⇒ ⇒ **零命中的真因是 `head -10` 把命中截断了**（管道 + 截断）⇒ ⇒ ⇒ **「零命中」必须先复核判据**（管道/过滤/`head` 截断/大小写/路径写法），**再**下结论 ⇒ ⇒ 这与「**跨树零命中 ≠ 不存在**」同族，但这次是**同树内的 grep 截断** ⇒ ⇒ **更隐蔽**：跨树那条至少有「树 + commit」可核，本条连树都不用换。

#### 4.10.39 🔴🟢 **G1 §34 的根因归属订正 ＋ Testcontainers 宿主口段分配**（2026-10-27 · **本层零落码** · 只登记 + 预分配段）

**① 🔴 订正 G1 §34.4 的根因归属（「25 个 Testcontainers 残留 ⇒ Ryuk 没跑 ⇒ 没人清」**不成立**）**

| 项 | 说明 |
|---|---|
| G1 的原写法 | 「`docker ps` 实测 **47 个容器在跑**，其中 `postgres:16-alpine` **18** 个 ＋ `redis:7-alpine` **7** 个 = **25 个 Testcontainers 残留**」⇒ ⇒ 「`docker ps --filter name=ryuk` ⇒ **零命中** ⇒ ⇒ Ryuk **根本没在跑** ⇒ ⇒ 残留**没人清**」⇒ ⇒ 由此推出修法①「固定 Testcontainers 宿主口段」 |
| **实测反证（本层只读普查，不删任何容器）** | ① **47 个容器**成立；但 **23 个是固定名**：`ddl-users-pg` / `ddl-task-pg` / `ddl-comment-pg` / `ddl-favorite-pg` / `ddl-follow-pg` / `ddl-hot-pg` / `ddl-like-pg` / `ddl-article-pg` / `ddl-message-pg` · `difftest5k-postgres` / `difftest5k-redis` · `cdifftest-postgres` / `cdifftest-redis` / `cdifftest-kafka` · `uudifftest-postgres` / `uudifftest-redis` / `uudifftest-redis-bl` · `taskprobe-postgres` · `userprobe-postgres` / `userprobe-redis` · `msg_ddl_pg` · `rag-l12-pg`<br>⇒ ⇒ **Testcontainers 生成的容器名是随机的**（本机唯一确凿的一例：**`sad_napier`**）⇒ ⇒ ⇒ **这 23 个固定名容器不是 Testcontainers 建的** ⇒ ⇒ ⇒ **Ryuk 只回收带 Testcontainers 标签的容器 ⇒ 管不到它们 ⇒ 「开 Ryuk」解决不了这批残留**<br>② 它们的创建方是 **`tools/difftest/**` 与探针脚本**（固定名 + `docker run` / compose）：`tools/difftest/ddl-parity.sh:334`（`ddl-${domain}-pg`）· `tools/difftest/run.sh:71-73`（`DIFTEST_CONTAINER_PREFIX` ⇒ `${prefix}-postgres` / `${prefix}-redis`）· `tools/difftest/exp-difftest-grpc-user9.sh:52-53`（`uudifftest-*`）· `tools/difftest/parity-task-kafka.py:32-33`（`taskprobe-*`）<br>③ 这些脚本**本来有收尾**：`tools/difftest/run.sh:218-229`（`cleanup()` + `trap cleanup EXIT`）· `ddl-parity.sh:354`/`:450`（`docker rm -f`）⇒ ⇒ ⇒ **所以残留来自「收尾路径没跑到」**（进程被 `kill` ⇒ `trap` 不执行；或 `--keep` 显式保留 ⇒ `run.sh:219-221` 自己就写了「`--keep`：保留容器与进程。**手动收摊**」）⇒ ⇒ ⇒ **真因 = 脚本收尾不覆盖异常路径**，**不是**「Ryuk 没跑」 |
| **⇒ 计数口径订正** | 🔴 **「25 个 Testcontainers 残留」要订正** —— 那 25 个是**按镜像统计**（`postgres:16-alpine` 18 ＋ `redis:7-alpine` 7）⇒ ⇒ **把 difftest/探针的自建容器与 Testcontainers 容器混在一个数里** ⇒ ⇒ ⇒ 本机 Testcontainers 随机名容器**只有 1 个** ⇒ ⇒ ⇒ **修法方向要拆开**：<br>① **占口的**（`tools/difftest/**` ＋ 探针）⇒ ⇒ **收尾必须覆盖异常路径**（`trap` 不覆盖 `SIGKILL`）⇒ ⇒ 建议「启动时先回收**同名前缀**的旧容器」⇒ ⇒ **合并收口执行者面 ＋ 域侧探针**<br>② **被撞的**（Testcontainers 随机口）⇒ ⇒ 落在 OS 临时段 ⇒ ⇒ **域侧真容器测试改固定宿主口** ⇒ ⇒ **这一条**才是 G1 的修法① ⇒ ⇒ **段由本层分配**（见 ②） |
| **⇒ ⚠️ 「Ryuk 零命中」本身也不是「没跑过」** | Ryuk 是**随用随起、随 JVM 退出而回收**的容器 ⇒ ⇒ **此刻没有 Testcontainers JVM 在跑 ⇒ 本就该零命中** ⇒ ⇒ ⇒ **「零命中」⇒「没跑过」是错的推理**（与「跨树零命中 ≠ 不存在」同族，但这次是**同机、跨时刻**）⇒ ⇒ ⇒ ⇒ G1 §34 的**现象**与**三步判据**（一次失败不判回归 ＋ 单独复跑绿 ＋ 环境普查）**仍然成立** ⇒ ⇒ **本层只订正「根因归属」这一格，不动其余结论** |

**② 🟢 Testcontainers 宿主口段分配（**本层的 A5 职责** —— 「段表」这一格归 global 协调）**

| 项 | 说明 |
|---|---|
| 为什么必须**按域分子段** | Testcontainers **没有「宿主口段」这个概念** —— 实测 `TestcontainersConfiguration` 的配置项里**零个** `port` 相关项 ⇒ ⇒ 「指定一段」**只能逐容器固定口** ⇒ ⇒ ⇒ ⇒ 若全域共用一段 ⇒ ⇒ **域与域之间会互相撞**（4 个 lane 并发）⇒ ⇒ ⇒ ⇒ **必须按域分子段** —— 这正是段表的作用 |
| 机制可行性（**已核实的边界**） | ✅ Testcontainers **2.0.5**（版本由 `spring-boot-starter-parent` 的 `testcontainers.version` 管理）⇒ ⇒ `org/testcontainers/containers/FixedHostPortGenericContainer.class` **在本机 jar 里存在**（`~/.m2/repository/org/testcontainers/testcontainers/2.0.5/testcontainers-2.0.5.jar`）⇒ ⇒ **「固定宿主口」在仓内可落地**<br>⚠️ **本层未实测的两层**：① `PostgreSQLContainer` **不能直接换成** `FixedHostPortGenericContainer`（后者不带 PG 的初始化/JDBC 支持）⇒ ⇒ 正确形态应是 `PostgreSQLContainer` ＋ `withCreateContainerCmdModifier(...)` 显式绑口；② **「显式绑口后 `getMappedPort()` 是否返回那个固定口」未实测** ⇒ ⇒ ⇒ **域侧落地时必须先写一条探针验证，不能照抄** |
| **分配结果（`22700–22999`，按域分子段）** | `22700–22729` **users 域**（G1）· `22730–22759` **follow 域**（G1）· `22760–22789` **message 域**（G2）· `22790–22819` **like 域**（G2）· `22820–22849` **task 域**（G2）· `22850–22879` **article 域**（G3）· `22880–22909` **comment 域**（G3）· `22910–22939` **favorite 域**（G3）· `22940–22969` **hot 域**（G3）· `22970–22999` **预留**（30 口）<br>⇒ ⇒ **每域 30 口** ⇒ ⇒ 够一个域并行起 PG + Redis + Kafka（≈ 4~6 个容器）且有余量 ⇒ ⇒ **全部在 `49152–65535` 之外** ⇒ ⇒ **不与任何已占段/预分配段重叠** |
| 落点 | **域侧改自己的测试类**（`@Container PostgreSQLContainer` ⇒ 显式绑口）⇒ ⇒ **本层只分配段 + 记录规则 ⇒ 零落码**（§5.3d） |

**③ 🔴 一条**跨 lane**的诊断判据（来自 G1 §34.5 第 3 条 ⇒ 本层采纳为**全局判据**）**
> **「PG 连接失败」先问「那个端口上回应我的到底是不是 PG」** —— `EOFException` 出现在 `enableSSL` ⇒ **强烈指向「对端不是我预期的服务」**，**不要**直接读成「PG 挂了」⇒ ⇒ 否则会去查一个根本没坏的东西。
> ⇒ ⇒ 本层记成**共享环境判据**（适用全部 4 个 lane 的任何「连库失败」读法）⇒ ⇒ 与 §4.10.37 的「等待条件弱于断言条件 ⇒ 假红」同族：**症状先归因到「那层真正能观测到的东西」**。

**④ 🔴 共享面待合并收口执行者处理（**本层只登记 · 不代改**）**
- `tools/difftest/ddl-parity.sh:66-79` 的 `port_for()` ⇒ **固定口 `55560–55569`**（含 `*) 55567` 兜底）⇒ ⇒ **整段落在 OS 临时段 `49152–65535` 内** ⇒ ⇒ **Testcontainers 的随机口与它是同一个池子** ⇒ ⇒ **这是一条独立于「残留是否清理」的撞车路径** ⇒ ⇒ 建议迁出（⇒ 本层**预留 `23000–23099` 但未分配**）⇒ ⇒ **落点 = `tools/difftest/**`（合并收口执行者面）⇒ 本层不改**

**⑤ 本层这一条** | **零落码**：① 根因归属**已订正**（Ryuk 方向作废）② 段**已分配**（本层职责）③ 域侧改测试类 = 各域 ④ difftest 收尾 = 合并收口执行者

#### 4.10.40 🔴🟢 **本层台账订正轮（2026-10-27 晚）—— 6 处订正 ＋ 一条方法论**（**生产代码零改动**）

> **触发**：G1/G2 两条独立核实（G1 的 `isTimeoutCommands()` 实测 ＋ G2 的字节码复核）**推翻了本台账 §4.9.4 的一句断言**；G2 另指出本台账的 `RANDOM_PORT` 数字与其树对不上 ⇒ ⇒ ⇒ **本层逐条独立复算**（不照抄对方结论）⇒ ⇒ **实测出 6 处订正**，其中 **4 处是本层自己的数字/行号过期**。

| # | 订正对象 | 原写法 | 实测（**本层独立复算**） | 证据强度 |
|---|---|---|---|---|
| 1 | §4.9.4 E66 约束 | 「这条正是域侧守门 ①（`getTimeoutOptions().isTimeoutCommands()` 必须为 true）**所钉的那一根网**」 | 🔴 **该断言错** —— `isTimeoutCommands()` **恒真**（`TimeoutOptions.enabled()` = `builder().timeoutCommands().connectionTimeout().build()` ⇒ **两个 flag 各自恒真**；`DefaultTimeoutSource.getTimeout(...)` 恒返 **-1**）⇒ ⇒ **单条 flag 红不了 ⇒ 钉不住任何东西** ⇒ ⇒ 改为 **A~D 四条判据链**（A/B 恒真只能当形态断言 · **C「source 恒 -1」** · **D「`RedisURI.timeout == 3s`」才是真网**）⇒ ⇒ **E66 落码后红测试必须红在 D 上** | L1（字节码）+ G1/G2 双侧实测 |
| 2 | §4.9.4 回归口径 ① | 「① 命令超时机制启用（`isTimeoutCommands()` = true）」 | ⚠️ **① 恒真 ⇒ 真正的网是 ③**（`getClientConfiguration().getCommandTimeout()` = `RedisURI.timeout` = 3s）⇒ ⇒ 口径同步订正 | 同上 |
| 3 | §4.10 E65 行 | `HttpMiddleware.java:304`（begin）/ `:336`（clear） | 🔴 **陈旧行号** ⇒ 实测 **`:324`**（`GoStyleRequestDeadline.begin(deadlineNanos)`）/ **`:356`**（`GoStyleRequestDeadline.clear()`）⇒ ⇒ ⚠️ 本台账 §4.10.19 附近**已有一行正确写法**（`:324`/`:356`）⇒ ⇒ **同一台账内两套行号并存** | L1（源码实测） |
| 4 | §4.10 A1 轴行 | `observability/HttpMiddleware:304`/`:336` | 同上 ⇒ 改为 `:324`/`:356` | L1 |
| 5 | §4.10 E65 行调用点 | 「3 处 + 等」且**无完整模块路径** | 🆕 **实测生产代码共 6 处 `applyTo`**，逐条补全模块路径（follow-api · message-api · favorite-rpc ×2 · favorite-api · article-api）⇒ ⇒ **另发现 4 处出向 client 走 `remainingMillis()` 手搓**（like-api · comment-api · task-api · article-api）⇒ ⇒ ⇒ **E65/U15「统一载体」改造的覆盖面是 10 处出向 stub，不是 6 处** ⇒ ⇒ ⚠️ **`hot-api/HotArticleQueryService.java:102` 不算**（本地查询，非出向 stub） | L1 |
| 6 | §4.10.19.1/19.2/19.5 + A5 轴行 | RANDOM_PORT「共享层 6 处」· §4.10.19.2 **单栏**（G4=16 / G1=14 / G2=9 / G3=14） | 🔴 **漏 `GoStyleParamLimitOffModeGateTest`（`:92`）** ⇒ 共享层实为 **7 处**；且 §4.10.19.2 单栏表**未标注是「基线」还是「当前」** ⇒ ⇒ **四树逐 commit 复算**（`git grep` 逐树）⇒ ⇒ 拆成**表 A（基线）**与**表 B（当前 HEAD）**：基线 G4 `1e1899f`=16 / G1 `b7d724c`=14 / G2 `de696dd`=9 / G3 `40bfa82`=14；**当前** G4 `96b40df`=**17** / G1 `623d4e6`=**17** / G2 `2016fa6`=**12** / G3 `5361863`=**17** ⇒ ⇒ 计数口径补成**三层**（文件 22 / 行 33 / 注解 17）⇒ ⇒ **文件层 − 注解层 = 5**，且补全 5 个「只提及不使用」的文件名（原只写了 2 个） | L1（四树 `git grep` 逐 commit） |

> 🟢 **方法论沉淀（**本轮最值钱的一条**）**
> ① **「跨树数字不可互引」** —— 同一个口径在不同树上的数**就是不同**（各树基线不同）⇒ ⇒ **任何「N 处」的说法必须带「树 + commit」** ⇒ ⇒ **单独一个数字不可复算 ⇒ 不可引用**（同 AGENTS.md §6）⇒ ⇒ ⚠️ **G2 报的「真注解 9」= 他们基线 `de696dd` 的数**（现已 12）⇒ ⇒ ⇒ **两树对不上时，先比 commit，再比口径**（否则会误判成「口径不一致」而白争一轮）。
> ② **「单栏表 + 未标注时点」是数字过期的高发形态** —— §4.10.19.2 原表只有一栏、写着**基线**的数、却长得像**现状** ⇒ ⇒ ⇒ **凡写死数字的表，要么每格带 commit，要么拆基线/当前两栏**（本轮已拆）。
> ③ **「自己的数字忘了更新」也是漂移** —— 本层 `cd893d1` 加了第 7 处共享层真容器守门，**自己台账没跟上**（且该提交是**四棵树的共同祖先** ⇒ ⇒ **共享层加一个文件 ⇒ 四棵树同时过期** ⇒ ⇒ ⇒ **共享层改动 ⇒ 必须同步刷新四树口径**）。
> ④ **「跑绿」≠「数字对」** —— 本轮 **4/6 处订正全是文档数字**，与测试全绿**毫无关系** ⇒ ⇒ ⇒ **台账数字的唯一防线是「逐条重算 + 带 commit」**，不是测试。

**本层这一条** | **生产代码零改动**（本轮只改台账）⇒ ⇒ ⇒ **第十四次全量口径（代码态 `15cf93a` + `d546f45` + `407c230`）仍然有效** ⇒ ⇒ ⇒ 本轮**不需要重跑全量**（改的全是 `docs/**`）⇒ ⇒ 但**门禁自证必须重跑**（改的是台账本体）⇒ ⇒ 见下节。

#### 4.10.41 🟢 **G2 三张「合并后必红」单 ＋ G1 回执三条 ⇒ 逐条处置**（2026-10-27 晚 · **本层 2 处落码 ＋ 1 处改派 ＋ 1 处订正**）

> **背景**：G2 提 FB-S1 / FB-S2 / FB-S3（= FB-06/07/08）三张「合并后必红」单；G1 回执给了三条（合并顺序约束 / javadoc 过期 / Redis 收编前置）⇒ ⇒ **本层逐条独立复核后处置**，其中 **1 条订正了 G2 的写权归属判断**、**1 条订正了 G1 的计数口径**。

**① 🟢 FB-S1（`tools/difftest/check-tomcat-limits.py` 的 task 行写死 `"raw"`）—— 已落码**

| 项 | 内容 |
|---|---|
| **G2 的原断** | `:149` 的 `EXPECTED_FORM_LIMIT_MODE` 把 `service/task/task-api` 写死 `"raw"`，而共享层真源 `GoStyleFormLimitMode.java:237` 是 `Mode.OFF` ⇒ ⇒ **守门红 ⇒ 守门会把下一个人推向「把 task-api 的 yml 改回 raw」⇒ 重新引入误拒**；附带 `:337` 报错文案 `actual.get('expected_mode')` 恒打印 `None` |
| **本层独立复核** | ✅ **成立** —— 副本 `task-api: "raw"` vs 真源 `:237` `Mode.OFF`；且 `:337` 的 `None` **不是**「期望值为 None」，而是「该域**没在期望表里**」⇒ ⇒ **两个语义必须分开说** |
| **本层修法（不只改那一行）** | 🔴 **删副本，改为从真源派生** —— 真源 javadoc 明写「判据**只有一份**，且**可被机器核对**」⇒ ⇒ **副本即违背该设计的第二份判据** ⇒ ⇒ 本脚本改为 regex 读 `GoStyleFormLimitMode.java` 的 `SEMANTIC_REACHABILITY`（module + `Mode`）⇒ ⇒ **task-api 的期望值随真源自动变 `off`**；<br>⇒ ⇒ **解析失败一律 `SystemExit`**（文件缺失 / 解析出 0 条）⇒ **绝不静默回退到任何副本**（静默回退 = 「以为在核对真源、其实在核对一份可能过期的副本」= §3.3 同款静默失效）；<br>⇒ ⇒ **新增「该域未在真源登记 ⇒ ④ 判据缺失 ⇒ 显式判红」分支**（旧实现 `expected_mode=None` ⇒ `gate_ok=(mode==None)` ⇒ **新域不写这个属性就静默通过** = 盲区）；<br>⇒ ⇒ **合法写法逐条对齐 Java `parse`**：语义答案 `off` ⇒ 合法 = 不写 / `"off"` / `"false"`（三者行为逐字节相同）；`raw`/`envelope` ⇒ 该属性**承重** ⇒ 必须逐字写对；<br>⇒ ⇒ **修法提示里 `off` 改为带引号**（脚本自己原来建议 `mode: off` 无引号 ⇒ **正是 YAML 1.1 布尔坑** ⇒ 会让域侧进程起不来） |
| **落码** | `tools/difftest/check-tomcat-limits.py`（`d4e4745`） |
| **变异验证（3 项全过）** | **M1** Java `task-api` `Mode.OFF` ⇒ `Mode.RAW` ⇒ **门禁转绿** ⇒ ⇒ **证明期望值来自 Java、不是写死**<br>**M2** 删掉 Java 的 `hot-api` 条目 ⇒ **门禁报「该域未在共享层真源登记」** ⇒ ⇒ **新分支是活的**<br>**M3** 真源文件改名 ⇒ **脚本终止并报「不静默回退」** ⇒ ⇒ **无静默失效** |
| 🔴 **合并顺序约束（本层产出）** | 本单使门禁在**本树**转红（task-api 的 yml 仍是 `raw`）⇒ ⇒ **这是「正确的红」**（它检出的是**真漂移**）⇒ ⇒ **必须与 G2 的 task-api yml 订正（`mode: raw` ⇒ `mode: "off"`，G2 提交 `4786eb5`）同批合入** ⇒ ⇒ ✅ **已端到端实测**：把本单打好的守门**模拟跑在 G2 树**上 ⇒ **EXIT=0 全绿** ⇒ ⇒ **成对落地必绿** |

**② 🔴 FB-S2（耦合快照 `common_radiation` 落后）—— 改派合并收口执行者（本层不写）**

| 项 | 内容 |
|---|---|
| **G2 的原断** | `docs/DRIFT/_cross/cross-domain-coupling.md` 的 `<!-- wave-coupling-snapshot -->` 块落后 ⇒ 合并即红；且 G2 认为该文件属「global 写权（G4）」 |
| 🔴 **本层订正写权归属** | **该文件属合并收口执行者面** —— 写权守门真源 `tools/difftest/check-wave-lane-ownership.py:161` 的 `SHARED` 清单**明确列了** `docs/DRIFT/_cross/cross-domain-coupling.md` ⇒ ⇒ ⇒ **本层不写**（§5.3c 写权硬线）⇒ ⇒ **改派合并收口执行者** |
| **本层实测（本树 vs G2 树）** | **G4 树**（`96b40df`）⇒ `check-wave-coupling.py` **EXIT=0**、漂移 **0** 项 ✔<br>**G2 树**（`2016fa6`）⇒ **EXIT=1**、漂移 **9** 项 ⇒ ⇒ ⚠️ **G2 报的是 6 条，现实测 9 条** ⇒ ⇒ **不是口径冲突，是他们树前移了** ⇒ ⇒ 又一次「**先比 commit，再比数字**」 |
| **口径问题（本层裁定）** | G2 问「测试 import 是否计入辐射度」⇒ ⇒ 🔴 **不用改口径** —— 守门**自己的实现**已经定案：`check-wave-coupling.py:162`/`:184` 的扫描 glob 是 `service/**/src/**/*.java` ⇒ ⇒ **含 `src/test`** ⇒ ⇒ 且这是**合理的**：域侧测试 import 共享层类 ⇒ ⇒ **共享层改动确实会影响该域的测试** ⇒ ⇒ ⇒ **所以 G2 报的「口径要不要改」这个问题本身不成立** —— 快照只是**过期** |
| **可机械执行的修法（交给合并收口执行者）** | `python3 tools/difftest/check-wave-coupling.py --print` ⇒ 打印重算结果 ⇒ 贴回 `<!-- wave-coupling-snapshot:begin/end -->` 块 ⇒ ⇒ **不需要人工推演**，合并收口执行者一条命令即可 |

**③ 🟢 FB-S3（task-api yml 的注释与语义表自相矛盾）—— 合并顺序裁定（本层不写）**

| 项 | 内容 |
|---|---|
| **G2 的原断** | `service/task/task-api/src/main/resources/application.yml:177-178` 仍是 `mode: raw` + 用错判据轴（拿「有没有注册 `httpx.SetErrorHandler`」定「开不开」）的注释 ⇒ ⇒ 与 G4 的语义表自相矛盾 |
| **写权** | 🔴 **该文件是 G2 域文件 ⇒ 本层不写**（§5.3c） |
| **裁定** | ✅ **合并时以 G2 分支版本为准**（`mode: "off"` + G2 已订正的注释）⇒ ⇒ **合并清单里必须显式标一行**（否则可能取到我树里那份仍是 `raw` 的旧版 ⇒ ⇒ **门禁会红**）⇒ ⇒ ⇒ **与 ① 的合并顺序约束是同一条** |

**④ 🟢 G1 ①（`3b573fb` 与 `7488b3d` 的合并顺序约束）—— 成立，本层已核 ⇒ 转合并收口执行者**

| 项 | 内容 |
|---|---|
| **G1 的原断** | 两笔**必须同批合入**（或先 `7488b3d` 后 `3b573fb`）⇒ ⇒ 单合 `3b573fb` ⇒ `ObservabilityLifecycle.shutdown()` 真的执行 ⇒ 进程级静态池「终止即永久」⇒ 同模块后续 Spring 上下文被毒化 ⇒ ⇒ **前几轮全量一直绿是「因为没走到」** |
| **本层独立核** | ✅ **成立** —— `3b573fb` = 「关闭路径不得触发 web 类型的类加载（拆出 `HttpPoolLifecycle`）」⇒ 拆出后 `shutdown()` 第一句不再抛 `NoClassDefFoundError` ⇒ ⇒ `RpcInterceptor.shutdown()` 真的会执行；`7488b3d` = 「修『进程级静态池终止即永久』⇒ 多 Spring 上下文被毒化」⇒ ⇒ **方向不对称成立**（`7488b3d` 单独在位 ⇒ 池可重建 ⇒ 仍绿；`3b573fb` 单独在位 ⇒ 会红）<br>⇒ ⇒ **本树状态**：两笔**都已是本分支祖先**（`git merge-base --is-ancestor` 双双 YES）⇒ ⇒ **本层分支已满足该约束** ⇒ ⇒ **约束只对「合并收口执行者逐笔 cherry-pick」与「G1 分支」有效** ⇒ ⇒ **转合并收口执行者**（本层只登记） |
| ⚠️ **G1 的域暴露面数字订正** | G1 报 `user-rpc` **11** / `admin-rpc` **13** / `follow-rpc` **5** 个 `@SpringBootTest` 上下文 ⇒ ⇒ 🔴 **本层按「真注解」重数 = `user-rpc` 9 / `admin-rpc` 12 / `follow-rpc` 3**（`grep -rE "^\s*@SpringBootTest"`）⇒ ⇒ **G1 的数是「含 javadoc 提及」的数**（follow-rpc：提及 7 / 真注解 **3**）⇒ ⇒ ⇒ **方向不变**（这三个域确实是「同模块多 Spring 上下文」的形态 ⇒ 暴露面成立）⇒ ⇒ **数字按注解口径订正**（同 §4.10.19.1 的三层计数纪律） |

**⑤ 🟢 G1 ②（`ObservabilityLifecycle` javadoc 过期）—— 已修（该文件在本层写权面内）**

| 项 | 内容 |
|---|---|
| **G1 的原断** | `3b573fb` 之后本类只剩 `RpcInterceptor.shutdown()` 一句，但 javadoc 仍写「即使某个池关闭出错，也要保证**其他池**被尝试关闭，因此**调用各自独立**」⇒ ⇒ 建议改单池口径 |
| **本层独立核** | ✅ **成立** —— `ObservabilityLifecycle.java:88-89`（修前）确实写着那句 ⇒ ⇒ **该文件属 `service/common/**` ⇒ 本层写权面内** ⇒ ⇒ ⇒ **本层直接修，不转派** |
| **修法** | 改为**单池口径**并**写明「这句已随 HTTP 池拆出而过期」** ⇒ ⇒ 并补一句「若将来再加池进来，**必须同时把这段改回多池口径**」（防止下一轮又留一处过期 javadoc） |

**⑥ 🟢 G1 ③（Redis 收编删除前置）—— 共享层那两条断言**已存在** ⇒ 本层零落码**

| 项 | 内容 |
|---|---|
| **G1 的原断** | 删 5 份域内副本前必须同批重写 5 份 FastFail 守门的装配（`@Import` ⇒ `@ImportAutoConfiguration(GoStyleRedisClientAutoConfiguration.class)`）**并补两条断言**：`getBeansOfType(LettuceClientOptionsBuilderCustomizer.class).size()==1` ＋ key 含 `goStyleRedisClientOptionsCustomizer` ⇒ ⇒ 否则**只断言行为值 ⇒ 收编没做也绿**（`@ConditionalOnMissingBean` 是「**遮蔽**」不是「叠加」） |
| **本层独立核** | ✅ **G1 的判断成立**（`@ConditionalOnMissingBean` = 遮蔽）<br>⇒ ⇒ 🟢 **但那两条断言在共享层**已经存在**（**逐条等价**）—— `service/common/src/test/java/com/rag/common/redis/GoStyleRedisClientOptionsGateTest.java:62`（`getBeanNamesForType(LettuceClientOptionsBuilderCustomizer.class)`）· `:64`（`.hasSize(1)`）· `:65-66`（`[0]` 等于 `"goStyleRedisClientOptionsCustomizer"`）⇒ ⇒ ⇒ **域侧那 5 份守门照抄这两条即可** ⇒ ⇒ **本层零落码**（不重复造） |
| **G1 的 5 项静态核验** | ✅ 本层已复核关键两条：**共享层那份** customizer 只设 `disconnectedBehavior(REJECT_COMMANDS)`、**零 `timeoutOptions` 触碰**（与守门 ③ 同款）⇒ ⇒ **删除域内副本 = 纯删减、零行为变化** ⇒ ⇒ **同意 G1 的结论** |

**⑦ 本层这一条** | **落码 2 处**（`d4e4745` 守门派生 + `ObservabilityLifecycle` javadoc）＋ **改派 1 条**（FB-S2 ⇒ 合并收口执行者）＋ **写权/口径/数字各订正 3 条**（FB-S2 归属 · 辐射度口径 · G1 的域暴露面数字）⇒ ⇒ **生产代码净改动 = 1 个 javadoc** ⇒ ⇒ ⇒ **第十四次全量口径（代码态 `15cf93a` + `d546f45` + `407c230`）仍有效**（本轮只改 `docs/**` 与 `tools/**`，未动任何 `service/**` 的**可执行**代码 ⇒ ⇒ **但 `ObservabilityLifecycle.java` 是 `service/**` 下的 java 文件 ⇒ 已复跑 common 模块**）。<br>🟢 **复跑口径（2026-10-27 22:47~22:49 · `mvn -o -B -DargLine=-Xmx2g -am -pl service/common test`）** = **330 条 / 0 failures / 0 errors / 0 skipped / EXIT=0**（约 1:15 min）⇒ ⇒ **首跑曾红 1 条**（`RpcServerSpanCurrentGateTest.handlerCanSeeTheServerSpan` ⇒ `UNAVAILABLE: Network closed for unknown reason`）⇒ ⇒ **隔离复跑该类 = 4/0/0/0 全绿（EXIT=0）** ＋ **整模块复跑 = 330 全绿** ⇒ ⇒ ⇒ **定性 = 瞬态**（G2 树同刻在跑 8 模块 `clean test` ⇒ 抢端口/抢资源）⇒ ⇒ **与本次 javadoc 改动无因果关系**（改的是注释 ⇒ 不改字节码）⇒ ⇒ **教训同款**：**「同一条失败在隔离复跑里是否复现」= 定性瞬态 vs 缺陷的唯一判据** ⇒ ⇒ ⚠️ **且「共享层一条红 ⇒ 别的 lane 的回归链整条断在他域之前」⇒ 共享层的偶发红是跨 lane 的公共风险**。

**⑧ 🟢 「第十四次全量口径是否仍成立」—— 逐条核对（**不靠「没改生产代码」一句话带过**）**

| 第十四次之后的提交 | 触及面 | 是否已被执行过 | 对「2480 条 / 1 预期内红」口径的影响 |
|---|---|---|---|
| `e966a67`（traceparent 守门判据） | `service/common/src/test/.../GoStyleTraceparentFilterTraceSourceGateTest.java` | ✅ **定向复跑 1/0/0/0**（`-Dtest=GoStyleTraceparentFilterTraceSourceGateTest`）＋ **整模块复跑 330 全绿**（含该类） | 🟢 **该类 `@Test` 数量改前改后都是 1**（实测 `grep -c "@Test"`）⇒ ⇒ **用例总数不变 ⇒ 2480 仍成立**；⚠️ 但**那一条断言**在本全量里是**旧判据**跑的 ⇒ ⇒ 已被上面的定向复跑覆盖 |
| `4cef806`（`ObservabilityLifecycle` javadoc） | `service/common/src/main/java/.../ObservabilityLifecycle.java` | ✅ 整模块复跑 330 全绿 | 🟢 **只改注释** ⇒ `git diff` 的非注释行差集为**空** ⇒ ⇒ **无字节码变化 ⇒ 行为零变化** |
| `d4e4745`（守门派生） | `tools/difftest/check-tomcat-limits.py` | ✅ **不在 maven reactor 内** ⇒ 由**变异 M1/M2/M3** + **打好的守门跑在 G2 树的端到端模拟（EXIT=0）** 覆盖 | 🟢 **不进任何测试计数** |
| `f6d58de` · `96b40df` · `5a50845` · `9a6d99b` · `2643f91` | `docs/**` | — | 🟢 **纯文档** |

⇒ ⇒ **结论：第十五次全量**不必跑** —— 判据不是「没改生产代码」（那是**弱判据**，会漏掉「改的是测试断言」这种情况），而是**「每一个改动过的行都已被执行过」** ⇒ ⇒ **第十四次全量口径（52 模块 / 29 有测试 / 2480 条 / 1 预期内红 / 0 errors / 4 skipped / 41:32 min / 三门禁 0-0-0）继续有效** ⇒ ⇒ ⚠️ **但若后续再有任何 `service/**` 的可执行改动 ⇒ 必须重跑全量**（本条口径的有效期随之结束）。

#### 4.10.42 🟢 **G3 的 D10 回执核对 ＋ 跨 lane 合并选型（D/A）裁定 ＋ 排序桥副本删除授权**（2026-10-27 晚 · **本层零落码**）

**① 🟢 G3 的 D10（comment 域 fact 投递 span 拓扑）—— 逐条核实成立 ⇒ **共享层零改动**

| 项 | 本层独立核（**双方断言都落在共享层 ⇒ 本层必须自己核**） |
|---|---|
| Go 侧 | `Sea-RideTheWind_golang/service/common/communityfact/process.go:26` = `otel.Tracer("sea.rtw.communityfact").Start(ctx, "community.fact_delivery.dispatch")`；全文 **69 行**、**`SetAttributes` 命中 0** ✔ |
| RAG 侧 | `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:168`（tracer = `sea.rtw.communityfact`）· **`:186`** = `tracer.spanBuilder("community.fact_delivery.dispatch")`；**`SetAttributes` 命中 0** ✔ |
| ⇒ 结论 | ✅ **span 拓扑两侧已对齐**（同名 · 同 tracer · 同在共享层 · 两侧都零属性）⇒ ⇒ **G3 域内那两行「删死代码 = 零行为变化」的结论成立** ⇒ ⇒ **共享层不需要改任何东西** ⇒ ⇒ ⚠️ **G3 自己订正了此前「Go 侧 fact 投递根本不用 span」的写法**（真源有 span，只是**建在共享层**）⇒ ⇒ **这正是「某文件零命中」推不出「整条链路零命中」**（G3 记进坑档 PIT-COMMENT-RPC-14，方向正确） |

**② 🟢 跨 lane 合并选型：G3 提 D（直接 merge 本层分支进 G3 树）vs A —— 本层裁「D」，并附可复算证据**

| 项 | 证据（本层实测，**不是推测**） |
|---|---|
| **两树 HEAD** | G4 `aeddbcb` · G3 `5361863` |
| **merge-base** | **`cd893d1`**（= `git merge-base HEAD 5361863`）⇒ ⇒ **本层这 29 笔是 G3 缺的**；G3 另有 **54 笔**本层没有 |
| **合并会不会撞** | ✅ **零冲突** —— `git merge-tree --write-tree --name-only HEAD 5361863` ⇒ **EXIT=0**、输出**只有合并树哈希、无冲突条目**（Git 2.50.1）⇒ ⇒ **不碰任何工作区**的空跑验证 |
| 🔴 **合并会不会覆盖 G3 的域侧工作** | ✅ **不会** —— 本层这 29 笔触及的 `service/**` **全部落在 `service/common/`（10 个文件）**，**零** `service/{comment,hot,favorite,article}/**`；另有 `tools/` 4 个 + **本层自己的** `docs/DRIFT/global/vs-common.md` ⇒ ⇒ ⇒ **G3 的域侧提交一个都不会被覆盖** |
| **写权守门会不会因此变红** | ✅ **不会** —— `check-wave-lane-ownership.py` 的 7 项判据里**没有一项看 diff**：查的是 ① 各 lane 声明的**路径集合**两两不重叠 ② `SHARED` 文件无人声明 ③ 排他归属 ④ **白名单路径必须命中真实文件**（合并只会**增加**文件 ⇒ 更满足）⑤ 7 个私有 `<域>-common` 无跨域引用（本层没碰私有库）⑥ **分支必须存在**（合并不删分支）⇒ ⇒ **合并后仍绿** |
| **「起始 commit」** | 🟢 **D 是 merge ⇒ 不需要起始 commit** —— `git merge align/g4-service-common` 在 G3 树里**自动从 merge-base `cd893d1`** 起算 ⇒ ⇒ **要拿全部 29 笔就 merge 整条**；若只想拿**共享层那半**（跳过本层台账文档），最早一笔触及 `service/` 或 `tools/` 的是 **`22dc67e`** |
| 🟡 **D 的代价（必须说清）** | ⚠️ **D = 把本层分支「搬进」G3 的树** ⇒ ⇒ **本层后续再落码，G3 树就又落后** ⇒ ⇒ **需要再同步一次** ⇒ ⇒ **本层给约定**：**波末收口回归前，本层再发一次同步点**（含 commit 号）⇒ ⇒ **不要零散 cherry-pick**（G3 自己也说了 B 会造「内容同、hash 异」的副本 ⇒ 合主干时撞） |
| ⚠️ **合并后 G3 树里有一个「预期内的红」** | 🔴 `tools/difftest/check-tomcat-limits.py`（本层 `d4e4745` 改为从真源派生）⇒ 合并后它会期望 `task-api` = `off`，而 **G3 树里的 task-api yml 仍是 `raw`**（那是 **G2 域文件**）⇒ ⇒ **该门禁在 G3 树里会红** ⇒ ⇒ **这是「正确的红」**（检出的是真漂移）⇒ ⇒ **必须与 G2 的 task-api yml 订正（`4786eb5`）同批合入** ⇒ ⇒ 本层已在 §4.10.41 ① 登记 |

**③ 🟢 G3 要的「排序桥副本删除授权」—— **授权：可以删**，附 3 个前置**

| 项 | 内容 |
|---|---|
| **授权结论** | ✅ **可以删**（`service/article/article-rpc` · `service/comment/comment-rpc` · `service/favorite/favorite-rpc` 三份域内副本）—— 依据：<br>① **共享层那半已在 G3 树里**：`a1a2e05`（MyBatis-Plus 排序桥进**主 jar**）经 `git merge-base --is-ancestor` 实测**是 `5361863` 的祖先** ⇒ ⇒ **共享层标准件 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` 在他们树里已存在** ⇒ ⇒ ⇒ **删域内副本不必等本层分支**<br>② **行为等价**：三份域内副本的**类体逐字为空**（`public class … {}` ⇒ **不注册任何 bean**）⇒ ⇒ **只提供一条「自动配置排序边」**；共享层那份**类体同样为空**、排序边目标**完全相同**（`after` = Boot 4 的 `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` · `before` = `com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration`）⇒ ⇒ ⇒ **删副本 = 零行为变化** |
| **形式差异（不是缺陷，但要知道）** | 域内副本用**类字面量**（`after = DataSourceAutoConfiguration.class`），共享层用**字符串**（`afterName` / `beforeName`，见 `a1a2e05` 的理由：**类字面量会让「没有 MP 的消费者」在反射层读注解时抛 `TypeNotPresentException`**）⇒ ⇒ 实测 **9 个 rpc 组件全部有 MP 依赖**、`hot-rpc` **无 MP**（⇒ 共享层那份的字符串形式对它**结构性安全**）⇒ ⇒ **今天两边都安全** ⇒ ⇒ **但删掉域内副本 = 消掉一个「将来有人给 rpc 组件去掉 MP ⇒ 上下文起不来」的隐患** |
| 🔴 **前置 1（必做，否则起不来）** | **同批删掉域内 `AutoConfiguration.imports` 里的那一行** —— 实测三份域内副本各自靠**自己模块的** `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 第 1 行注册（如 `com.rag.comment.comment.rpc.config.MybatisPlusOrderingBridgeAutoConfiguration`）⇒ ⇒ **只删 java 文件、不删那一行 ⇒ Boot 启动期找不到类 ⇒ 上下文起不来** ⇒ ⇒ ⇒ **必须同批** |
| 🔴 **前置 2（构建陷阱）** | **验证必须用带 `-am` 的构建** —— 共享层那份在 **`service/common` 的主 jar** 里 ⇒ ⇒ 若用 `mvn -pl <模块> package`（**不带 `-am`**）⇒ ⇒ fat jar 里 `BOOT-INF/lib/common-*.jar` 是 **`~/.m2` 的陈旧包**（不含新标准件）⇒ ⇒ **域内副本一删 ⇒ 产物起不来** ⇒ ⇒ ⇒ **单模块构建不验证**（测试 classpath 与产物 classpath 是两条独立路径） |
| 🔴 **前置 3（留网）** | 补一条守门：断言**该域的 `AutoConfiguration.imports` 里不再出现域内副本的 FQCN**（防「副本删了、注册行留着」或「反之」）⇒ ⇒ 域侧已有「`SqlSessionFactory` 能起来」那类断言（如 `ArticleSpringContextWiringTest:124` 的失败文案就指向该类）⇒ ⇒ **建议补的是「注册行」那一侧** |

**④ 🔴 G3 列的「4 件事同源同前置」—— 本层逐条定性（**它们**不**同源**）**

| G3 的说法 | 本层定性 |
|---|---|
| **① 断连活体对拍** | 🟡 **确实缺本层代码** —— G3 树里**没有** `GoStyleClientDisconnectProbeBudgetGateTest` ⇒ ⇒ 缺的就是本层 **`15cf93a`**（1xx 探测行数上界）⇒ ⇒ **合并（或取这一笔）后即可**。⚠️ 但 **`tools/redis-disconnect-probe/` 这个目录在**两棵树里都不存在**，且本层**从未收到该派单全文** ⇒ ⇒ **本层把它从「等派单」列表里划掉**，改问 G3：**它是否已被你自己的 `dec6348`（hot-rpc + comment-rpc 各 2 条域内守门）取代？** 若是 ⇒ 本层不重复造；若不是 ⇒ **请发规格全文**（本层不凭摘要落码） |
| **② 删域侧排序桥副本** | 🟢 **已授权**（见 ③）⇒ ⇒ **且不必等本层分支**（共享层那半已在他树）⇒ ⇒ **本层零落码** |
| **③ client breaker 装配复验** | 🟢 **缺的不是本层代码** —— 本层这 29 笔**对 breaker 与排序桥本体零命中**；共享层 breaker（`GoStyleGrpcClientBreakerInterceptor` 等）**已在 G3 树里** ⇒ ⇒ **要做的在域侧**：域内 FastFail / wiring 守门的装配方式复验（照 G1 的 Redis 收编同款：`@Import` ⇒ `@ImportAutoConfiguration`）＋ 照抄 `GoStyleRedisClientOptionsGateTest:62`/`:64`/`:65-66` 那两条「有且只有一份」断言 |
| **④ U15 统一载体** | 🔴 **不是卡在共享层** —— **Jason 已拍板「本轮不改」**（方向 = 走 global 统一载体 `applyTo(stub)`）⇒ ⇒ **这一条是 Jason 挂起的，不是等 G4** ⇒ ⇒ **G3 别把域内工作排在它后面** |

**⑤ 本层这一条** | **零落码**（① 是核实 · ② 是裁定 · ③ 是授权 · ④ 是定性）⇒ ⇒ ⇒ **第十四次全量口径仍有效**（本层 `service/**` 无任何改动）⇒ ⇒ ⇒ **本层 HEAD `aeddbcb`（基线 `a282012` 之上 79 笔，未 push）**。

#### 4.10.43 🟢 **G2 旧件回执（FB-02 / FB-05「不要重发」+ 两道共享门禁状态 + `ab0d782` 判据链）**（2026-10-27 晚 · **本层零落码**）

> **背景**：G2 的一条**深队列滞留件**（内文时间戳 12:3x ⇒ 晚于它落地数小时才到我这里）⇒ ⇒ **它的第 1 段（maven 窗口 / 「你的第 11 次全量 2471 条」）已被本层后续状态超越** ⇒ ⇒ **不可照单全收** ⇒ ⇒ 下面只取**仍然成立**的部分，**逐条重核**。

**① 🟢 FB-02 / FB-05 = 「consumed by G2」—— 本层台账记为已闭环 ⇒ **不重发**（G2 明确要求）**

| 反馈单 | 双方状态（本层复核） | 本层动作 |
|---|---|---|
| **FB-05**（`off` 在 YAML 里写不出来 ⇒ `parse` 接受 `"false"` 别名） | 🟢 **我方已闭环** = 提交 `c15dfeb`（`parse` 加 `case "false"` + `GoStyleFormLimitModeYamlBooleanGateTest` 5 条 + 变异 2 个 ⇒ L4）⇒ 本台账 §4.10.13 已登记<br>🟢 **G2 方已闭环**（G2 原话：task 域写 `mode: "off"`，FB-05 在他们台账 CLOSED，like 域零影响） | ✅ **不重发**（G2 两次明说「请不要再发」）⇒ ⇒ **本台账此条即「consumed by G2」的登记处** |
| **FB-02**（A3 闸门开关判据改「语义可达性」） | 🟢 **我方已闭环** = 提交 `182fc32`（本台账 §4.6）+ 判据改写 + 变异<br>🟢 **域侧那半已闭环** = 本台账 §4.10.23.8 实测 G2 树三域 `mode` 与共享层语义表**逐条一致**（task = `mode: "off"`，带引号） | ✅ **不重发**（同上） |

**② 🟢 G2 列为「仍排队在我这边」的两道共享门禁 —— 本层已处置（G2 不知情 ⇒ 须知会）**

| 反馈单 | 本层状态 |
|---|---|
| `check-tomcat-limits.py` 的 `EXPECTED_FORM_LIMIT_MODE` 把 task-api 写死 `"raw"` | ✅ **已落码**（`d4e4745`）—— 不是改那一行，而是**删副本、改为从共享层真源 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 派生** + 新增「该域未在真源登记 ⇒ 判据缺失 ⇒ 显式判红」+ 修「应为 None」文案 + 修法提示 `off` 加引号；**变异 M1/M2/M3 全过**；**打好的守门跑在 G2 树实测 EXIT=0** ⇒ ⇒ **须与 G2 的 task-api yml 订正（`4786eb5`）同批合入**（详见 §4.10.41 ①） |
| `check-wave-coupling.py` 的 `common_radiation` 快照落后 | ✅ **已改派合并收口执行者**（该 `_cross` 文件在写权守门的 `SHARED` 清单 `check-wave-lane-ownership.py:161` ⇒ **不是 global 写权面**）⇒ 机械修法 = `python3 tools/difftest/check-wave-coupling.py --print` 重生成快照；**口径不用改**（守门实现 `check-wave-coupling.py:162`/`:184` 的 glob 含 `src/test` ⇒ 已定案）；**实测漂移 9 项**（G2 报 6 项 ⇒ 树前移所致） |

**③ 🟢 G2 的 `ab0d782`（task-rpc / like-rpc Redis 超时守门补「机制位②」）—— 判据链与本层 A~D **逐条一致****

| 项 | 内容 |
|---|---|
| **G2 的写法** | 新增 `assertThat(options.getTimeoutOptions().isApplyConnectionTimeout()).isTrue()`，并在断言消息里**明写**「本条单独不构成『时长会被施加』的判据」·「单条恒真」·「完整判据链 = 机制位① + 机制位② +『source 恒 -1』+ 时长出口，**缺一不可**」 |
| **本层核对** | ✅ **与本台账 §4.9.4 的 A~D 判据链完全同构**（A = `isTimeoutCommands()` · B = `isApplyConnectionTimeout()` · C =「source 恒 -1」· D = `RedisURI.timeout == 3s`）⇒ ⇒ **G2 要我「保持那个措辞」⇒ 本层确认：保持，且本层台账已按同一措辞写**（A/B 明标「恒真 ⇒ 单条无效」，只有 C/D 是真网） |
| 🔴 **给 G2 的一条操作性提醒（**他们「assertion-direction 变异」正待做**）** | ⚠️ **A 与 B 在 Boot 默认装配下恒真 ⇒ 变异点必须选在「装配面」，不能选在「断言面」**：<br>① **能翻红的位置 = 共享层 customizer 那一侧**（它一旦调 `timeoutOptions(...)` 就会**整体替换** `TimeoutOptions.enabled()` 那份 default source ⇒ 那是新漂移 ⇒ 也是让 A/B 变红的唯一现实入口）；<br>② ⚠️ **但共享层另有一条守门 ③**（`GoStyleRedisClientOptionsGateTest`「命令超时必须**未被**本层改动」）⇒ ⇒ **若变异把整个 `TimeoutOptions` 换掉 ⇒ ③ 也会红** ⇒ ⇒ **「恰好 1 条红」的判据会被破坏** ⇒ ⇒ ⇒ **变异必须只翻那一位**（保留其余语义），否则会把「一条断言红了」误读成「两条网一起塌」；<br>③ ⇒ **本层建议**：变异用「让 `enabled()` 少一位」这种**最小翻转**，而不是「用另一个 `TimeoutOptions` 整体替换」 |

**④ 本层这一条** | **零落码**（① 是登记「不重发」· ② 是状态知会 · ③ 是措辞确认 + 一条操作性提醒）⇒ ⇒ ⇒ **第十四次全量口径仍有效**（本层 `service/**` 无任何改动）⇒ ⇒ ⇒ **本层 HEAD `1be3499`（基线 `a282012` 之上 80 笔，未 push）**。

#### 4.10.44 🟢🔴 **G1 的跨 lane 依赖登记 ⇒ 本层落码补网 ＋ 两处订正 ＋ 一条写权口径冲突（改派合并收口执行者）**（2026-10-27 晚）

> **G1 的登记**：`DRIFT-USERSCOMMON-001`（1017/1018 文案）销号，结论 = **两侧均不可观察 ⇒ 该条不成立**（证伪关闭）⇒ ⇒ **但该销号依赖 `service/users/users-common/**` 的两个类**（本层写权面）⇒ ⇒ G1 不写本模块 ⇒ 只能「登记 + 通知」⇒ ⇒ **本层收到 ⇒ 本层必须自己核这条依赖，并保证它不被无声破坏**。

**① 🔴 订正 G1 的 L4 反证：「`NEW_DRIFT = 0` ⇒ 反证成立」这一步**不成立****

| 项 | 本层独立核（读的是 `tools/difftest/matrix.py` 的实现，不是结论） |
|---|---|
| G1 的原论证 | 「`tools/difftest/matrix-results.txt`（run_id=1792602663396 · 273 条）**NEW_DRIFT = 0**；而 `matrix.py:618-620` 的 `classify()` 写明『status 不一致一律 NEW_DRIFT』⇒ ⇒ 若 admin-api 真的在 wire 上返回 1017（Go 400 vs RAG 200+1017），必被记为 NEW_DRIFT ⇒ 桶为 0 ⇒ **反证成立**」 |
| 🔴 **反证不成立的原因** | **`classify()` 在 `:618-620` 之前还有一个豁免分支** —— `matrix.py:605-611` 的 **`KNOWN_FORM_EMPTY`**：<br>`if (case["go_channel"] == "form" and case["value"] in ("", None, OMIT) and go["status"] != rag["status"]): return "KNOWN_FORM_EMPTY", ...`<br>⇒ ⇒ **「form 通道 + 空值 + 两侧 status 不一致」被**明确豁免** ⇒ ⇒ **不进 NEW_DRIFT 桶**<br>⇒ ⇒ 而 **「uid 缺失」恰恰就是这个形态**（form 通道 + 空值 + Go 400 / RAG 200+1017）⇒ ⇒ ⇒ **⇒ 「桶为 0」证明不了「1017 不可达」** ⇒ ⇒ ⇒ **它证明的只是「没有落进 NEW_DRIFT 桶的漂移」** |
| **实测数据** | `matrix-results.txt`（583 B）= **273 条**（MATCH **130** + KNOWN_FORM_EMPTY **1** + KNOWN_FRAMEWORK_TEXT **142**）· run_id=1792602663396 ✔ · 根因聚类 R1~R4 全 **0** ⇒ ⇒ **无 NEW_DRIFT 桶** ✔（G1 数字**全对**）⇒ ⇒ ⚠️ **但那 143 条 KNOWN 桶里，status 不一致是被容忍的** ⇒ ⇒ ⇒ **「NEW_DRIFT=0」≠「全一致」** |
| ⇒ ⇒ **结论** | 🟡 **G1 的销号结论（1017 在 HTTP 入口不可达 ⇒ 该漂移条不成立）本身仍可能成立**（其域侧另有机器网：`AdminApiMvcPipelineTest` 的「N-3 留档证伪：uid=0 实际透传到 rpc → 1003（与 Go 相同，不是留档所写的 1017）」）⇒ ⇒ **但支撑它的那条 L4 反证不成立** ⇒ ⇒ ⇒ **⇒ 真正的支撑 = 「必填校验在 service 层之前拦」这条代码顺序** ⇒ ⇒ ⇒ **⇒ 而那正是 G1 登记在本层写权面上的依赖** ⇒ ⇒ ⇒ **⇒ 本层据此落码补网（见 ②）** |

**② 🟢 本层落码：把 G1 销号依赖的两个不变量**钉成机器网**（`service/users/users-common/**` ⇒ 本层写权面内）**

| 项 | 内容 |
|---|---|
| **落码** | 新增 `service/users/users-common/src/test/java/com/rag/users/common/http/GoStyleRequiredFieldGateTest.java`（**6 条**）—— 钉住：<br>① `GoStyleRequest.normalize` **必须**调 `assertGoRequiredFields()`（用「回调被调用过」证明 ⇒ **不依赖异常实例同一性**）⇒ **这就是 G1 写的复开触发条件①**；<br>② `normalize(req=null)` ⇒ 走 factory 的零值 DTO **且仍跑**必填校验；<br>③ `field("uid", null)` ⇒ 文案**逐字节** = `field "uid" is not set`；<br>④ `field("uid", 0L / "" / "x")` **不抛**（**零值 ≠ 缺席**，Go 的零值 struct 语义 ⇒ **正向对照，防「一律抛」的假网**）；<br>⑤ 显式 null ⇒ `mustn't be nil`（**与「缺席」是两种文案**，防压成同一文本）；<br>⑥ 零值 DTO（uid=0）⇒ 过校验 |
| **Go 出处（逐条核过，不是照抄 javadoc）** | 🟢 go-zero **`v1.10.2`**（Go 仓 `go.mod` 钉的版本）`core/mapping/unmarshaler.go`：**`:1199`** = `newInitError` 里的 `field %q is not set`（函数头 `:1198`）⇒ 对应本类 ③；**`:862`** = `processNamedFieldWithValue` 里的 `field %q mustn't be nil`（函数头 `:854`）⇒ 对应本类 ⑤；**零值 ≠ 缺席** ⇒ 对应本类 ④ |
| **变异验证（3 项全过，各自红在目标断言上）** | **M1** 去掉 `normalize` 里的 `target.assertGoRequiredFields();` ⇒ **① + ② 红** ⇒ ⇒ **证明它钉的正是「必填校验不再在 service 层之前拦」**<br>**M2** 把 `field` 的 null 检查改成「一律不抛」⇒ **① + ② + ③ 红**<br>**M3** 把 `field` 的文案改成 `mustn't be nil` ⇒ **① + ② + ③ 红**（③ 的失败消息直指「文案必须逐字节一致」）<br>⇒ ⇒ **还原后 6/0/0/0 全绿**，生产代码 **0 改动**（已 `diff` 核过） |
| **回归口径** | `mvn -o -B -DargLine=-Xmx2g -am -pl service/users/users-common test` ⇒ **39 条 / 0 failures / 0 errors / 0 skipped / EXIT=0**（逐类：JwtUtilParityTest · JwtUtilTest · PasswordUtilGoCompatibilityTest · **GoStyleRequiredFieldGateTest**）<br>⇒ ⇒ **`users-common` 由 33 → 39（+6）** ⇒ ⇒ ⚠️ **全量总数口径随之变化：2480 → 2486（= 2480 + 本模块实测增量 6）· 全量未重跑 ⇒ 总数为推算，不是实测**（AGENTS.md 口径纪律：沿用上轮总数必须标注「已过期」⇒ 此处已标注）<br>⇒ ⇒ **爆炸半径已核**：`users-common/pom.xml` **无 `test-jar` 目标** ⇒ ⇒ **本新增只影响本模块的测试编译** ⇒ ⇒ **其余 51 个模块不受影响** ⇒ ⇒ ⇒ **本轮不需要重跑全量**（唯一改动 = 本模块 +6 条测试，且该模块已整跑绿） |
| **给 G1 的意义** | ⇒ ⇒ **G1 的销号结论从此有机器网**：本层改这两个类 ⇒ 本守门红 ⇒ 失败消息直接指向 `DRIFT-USERSCOMMON-001` ⇒ ⇒ **不靠「谁记得去复核」** |

**③ 🔴 订正 G1 引的行号**：`GoStyleRequest.normalize` 在 **`GoStyleRequest.java:52`**、`target.assertGoRequiredFields()` 在 **`:54`** ⇒ ⇒ **G1 写的 `:59` 定位不到**（AGENTS.md：引用必须可定位）⇒ ⇒ `GoStyleRequired.field(...)` 的 **`:32`** ⇒ **G1 写的 `:32` ✔ 对**。

**④ 🔴 一条写权口径冲突（**本层不擅自改 ⇒ 改派合并收口执行者**）**

| 项 | 内容 |
|---|---|
| **冲突** | 🟢 **AGENTS.md §5.3f 写的是「按类判定」**：「`users-common` 是**混合体** ⇒ **按类判定**（**8 个跨域类归 G4** / **3 个 users 专用类留 users 域**），**不按模块一刀切**」<br>🔴 **但机器守门 `tools/difftest/check-wave-lane-ownership.py` 的 G4 白名单是**整目录**：`service/users/users-common/src/**` ⇒ ⇒ **且它的注释自己写着「§5.3f …… 写权归共享层波，**不归 G1**」⇒ ⇒ 引用了 §5.3f 却**丢掉了它的「按类」那一半** ⇒ ⇒ ⇒ **⇒ G1 按 §5.3f 改 `ErrorCode` / `PasswordUtil` / `SchemaInitializer` ⇒ 会被机器守门判**越权** ⇒ ⇒ ⇒ **⇒ 这是「政策 vs 机器门禁」的真实分歧，不是笔误** |
| **本层实测（**按类判定，不是按目录**）** | 🟢 **11 个类逐个按「谁 import 它」测**：<br>**跨域（⇒ G4，8 个）**：`GoRequiredFieldException` · `GoStyleCoercion` · `GoStyleRequest` · `GoStyleRequired` · `GoStyleRequiredFields` · `GoStyleStringInt64` · `BearerTokenExtractor` · `JwtUtil`（**均被 6 个外域 api 之一 import**）<br>**users 域专用（⇒ G1，3 个）**：`PasswordUtil` · `SchemaInitializer` · **`ErrorCode`**（**只被 `users/admin` + `users/user` import**）<br>⇒ ⇒ **与 AGENTS.md §5.3f 写的「8 个 / 3 个」**逐条吻合** ⇒ ⇒ ⇒ **政策是对的，门禁的粒度不对** |
| **本层不擅自改的理由** | 🔴 `tools/difftest/check-wave-lane-ownership.py` **不在任何 lane 的白名单里**、也**不在 `SHARED`（合并收口执行者）清单里**（`SHARED` 只列了 `tools/difftest/README.md`）⇒ ⇒ **按「两张表」判据（lane 白名单 ∪ 合并收口执行者清单）⇒ 它是**写权孤儿** ⇒ ⇒ ⇒ **四条 lane 都被守门禁止碰它 ⇒ 只能合并收口执行者改** ⇒ ⇒ ⇒ **本层只登记 + 改派** |
| **给合并收口执行者的具体修法** | ① G4 白名单把 `service/users/users-common/src/**` **收窄为**「8 个跨域类 + 该目录其余文件」⇒ ⇒ **users 专用类从 G4 白名单里剔除**（**真实路径已核**）：`service/users/users-common/src/main/java/com/rag/users/common/cryptx/PasswordUtil.java` · `.../ddl/SchemaInitializer.java` · `.../errmsg/ErrorCode.java`；② G1 白名单**加**这 3 个文件；③ **注意 `users-common/src` 下还有 4 个非 java 资源**（`src/main/resources/db/01_create_user.sql` · `02_create_user_avatar_history.sql` · `03_create_admin.sql` · `04_create_admin_invite.sql`）⇒ ⇒ **建表脚本属哪一边要单独定**（它们只服务 users 域 ⇒ **倾向 G1**，但**建表责任**按 AGENTS.md §6.3 属 users 域 ⇒ ⇒ **留给合并收口执行者一并裁定**）；④ `expand()` 的 pattern 语言需支持**排除**（现在是纯 glob，无 `-` 语义）⇒ ⇒ **本层已把实测清单备好，可直接照抄** |

**⑤ 🟡 本层一处自我订正（「无人拥有面 ⇒ 我可自修」这个口径本身不完整）**

| 项 | 内容 |
|---|---|
| **我此前写的** | FB-S1（`tools/difftest/check-tomcat-limits.py`）时我写的是「该 .py ∉ SHARED ⇒ **无人拥有面 ⇒ 我可自修**」 |
| 🔴 **订正** | **「不在 SHARED」≠「有人能写」** —— 正确判据是**两张表都查**：**lane 白名单 ∪ 合并收口执行者（SHARED）清单** ⇒ ⇒ `check-tomcat-limits.py` **两张表都没有** ⇒ ⇒ **它是写权孤儿 ⇒ 严格说只能合并收口执行者改** ⇒ ⇒ ⇒ **⇒ 那次落码成立的理由应改为「**G2 的派单**点名了该文件并要求修 ⇒ **等价于一次转派**」**，而不是「无人拥有面 ⇒ 我可修」⇒ ⇒ ⇒ **⇒ 本层已在本节留痕**（结论不变、推导订正）⇒ ⇒ ⇒ **⇒ 通用纪律：写权归属要查两张表；「孤儿」不等于「可自修」** |

**⑥ 本层这一条** | **落码 1 处**（`users-common` 新增 6 条守门）+ **订正 3 处**（G1 的 L4 反证 · G1 的行号 · 本层自己的写权口径）+ **改派 1 条**（写权守门粒度 ⇒ 合并收口执行者）⇒ ⇒ ⇒ **生产代码零改动** ⇒ ⇒ ⇒ **第十四次全量的「用例总数」口径已过期（2480 → 2486·推算）**，**其余口径（模块数 / failures / errors / skipped / 三门禁 / 三探针）不变** ⇒ ⇒ ⇒ **本层 HEAD 待提交（基线 `a282012` 之上 81 笔之上 +1）**。

#### 4.10.45 🟢🔴 **G2 旧件（U+FFFD 归属 + 「路径归属 ≠ 内容归属」+ 一条规则）⇒ 本层零落码 ＋ 一族写权孤儿交合并收口执行者**（2026-10-27 晚）

> 又是**深队列滞留件**（内文 12:4x ⇒ 23:07 才到）⇒ ⇒ **逐条重核**（同 §4.10.43 的纪律）⇒ ⇒ **G4 侧结论：零落码**。

**① 🟢 U+FFFD 清单：三口径都成立，但指的是**不同集合** ⇒ **G4 侧 = 0**（本层零落码）**

| 口径 | 实测（本层实测，**不引用对方的数**） | G4 写权面内的份数 |
|---|---|---|
| **本层树**（`align/g4-service-common` @ `4cec90e`） | **15 个文件 / 36 处** | **0**（`service/common/**` · `service/users/users-common/**` · `docs/DRIFT/global/vs-common.md` · 4 个经转派文件 ⇒ **全 0**） |
| **G2 树**（`align/g2-message-like-task`） | **12 个文件 / 27 处** | **0** |
| **合并并集**（G2 报的「18 文件 / 47 处」⇒ **是这个口径**） | 18 / 47 | 0 |
| ⇒ ⇒ **结论** | 🟢 **G4 名下 U+FFFD = 0** —— G2 说「我上次的清单在 2 个 G4 拥有的文件上过期」，那两个文件**我早已修**：**`0c72969` = `fix(common): 修 service/common 两处 UTF-8 替换字符（U+FFFD）⇒ 纯注释，零行为变化`**（实测 `0c72969` 就在本树历史里）⇒ ⇒ **G2 的清单正确，本层无残留** ⇒ ⇒ **⇒ 本层零落码** | |
| ⚠️ **口径纪律** | ⇒ ⇒ **三个数都不是「错」** ⇒ ⇒ **是三个不同集合** ⇒ ⇒ ⇒ **报 U+FFFD 必须写清「哪棵树的哪个集合」**（同 §4.10.19.1 的三层计数纪律） |

**② 🔴 `docs/Pitfalls/**` —— **整族 49 个文件没有 owner** ⇒ 其中 2 个文件的 3 处 U+FFFD **没有任何 lane 能修** ⇒ 交合并收口执行者**

| 项 | 本层实测 |
|---|---|
| **族规模** | `docs/Pitfalls/**` = **49 个文件 / 13 个目录族**（`_INDEX.md` 1 · `_cross` 8 · `article` 3 · `comment` 4 · `common` 5 · `favorite` 4 · `follow` 4 · `global` 3 · `hot` 3 · `like` 2 · `message` 2 · `task` 3 · `users` 7） |
| 🔴 **归属实测** | **四条 lane 的 `ACTIVE` 白名单里，`docs/Pitfalls/**` 一条都没有**（逐个 lane 抽过：g1 / g2 / g3 / g4 全空）⇒ ⇒ **整族是写权孤儿**；<br>⚠️ **连 `docs/Pitfalls/favorite/**` 也一样** —— 它只出现在 `EXCLUSIVE`（`:176`）里当「favorite 域只归 G3」的排他声明，**并不在 G3 的 `ACTIVE` 白名单** ⇒ ⇒ **G3 也声明不了它** ⇒ ⇒ ⇒ **⇒ 整族（含 favorite）无人能写** |
| **含 U+FFFD 的文件** | `docs/Pitfalls/message/rpc.md` = **2**（G2 主张内容属 message 域）· `docs/Pitfalls/users/user/rpc.md` = **1**（G1 域）⇒ ⇒ **合计 3 处** |
| **G2 的主张 ⇒ 本层裁定** | 🟢 **G2 正确**：「**路径归属 ≠ 内容归属** ——『不在我的写权范围』**不可**读成『不是我的问题』」⇒ ⇒ **但结论是「该文件的 owner 也不存在」** ⇒ ⇒ ⇒ **⇒ 只能合并收口执行者**（本层亦无写权：`docs/Pitfalls/**` ∉ G4 白名单，**G4 的 `EXCLUSIVE` 条目只有 `service/common/**` + `service/users/users-common/**`**）⇒ ⇒ ⇒ **⇒ 本层只登记 + 转合并收口执行者**（§5.3d） |
| **给合并收口执行者的可执行清单** | ① **补一条 `docs/Pitfalls/<域>/**` 的 lane 归属**（按域归各 lane，仿 `docs/DRIFT/<域>/**` 的现成写法）⇒ ⇒ **这比修 U+FFFD 更重要**（否则这族永远没人能改）；② 顺带把 `docs/Pitfalls/favorite/**` 补进 G3 的 `ACTIVE`（现在只在 `EXCLUSIVE` 里 ⇒ 声明不了）；③ U+FFFD 3 处的具体位置已实测（`docs/Pitfalls/message/rpc.md` 2 处 · `docs/Pitfalls/users/user/rpc.md` 1 处）⇒ ⇒ **可照单修**；④ ⚠️ `docs/Pitfalls/_INDEX.md` ∈ `SHARED`（`:158`）⇒ **索引本体的 U+FFFD 归合并收口执行者**（实测当前 0 处 ✔） |

**③ 🟢 G2 的「那张转交单从未发出」⇒ 本层撤销那条拦截**

| 项 | 内容 |
|---|---|
| G2 的原话 | 「THAT TRANSFER SLIP WAS NEVER SENT BY ME, so there is nothing to retract」⇒ 并给证据（G3 队列 17 条完整；逐条搜过 `请转告` / `DRIFT-USERAPI-009` / `验签` / `JwtAuthFilter` ⇒ **唯一命中是本层自己那条拦截项**「POSSIBLE INCOMING CLAIM FROM G2 THAT YOU MUST NOT ACT ON」）⇒ ⇒ 「the slip existed only as a proposal inside my letter to YOU（『would you forward this?』）⇒ it never reached G3」 |
| **本层裁定** | 🟢 **接受 G2 的订正**（证据是它自己逐条搜过的队列，不是空口）⇒ ⇒ **⇒ 本层那条拦截项作废**（它防的是一条**从未发出**的转交 ⇒ ⇒ **无害但也无用**）⇒ ⇒ **⇒ 本层在台账记一笔「拦截已撤」，避免下一轮有人拿它当「G2 有事要转给 G3」的凭据** ⇒ ⇒ **⇒ G3 侧无需任何动作**（拦截的净效果 = G3 不动一件本来就不该动的事） |
| **顺带** | 🟢 **G2 已接受本层的两处订正**（① 真源有该行为只说明**适用**、不说明**RAG 还欠修** ⇒ 判谁欠修**必须先读目标域当前 RAG 状态**；② 「applicable」与「still owed」是**两个独立判断**，压成一个就会造出假转交）⇒ ⇒ **本层采纳这条规则**（与「一个域的结论不能外推」「结论对 ≠ 推导对」同族）⇒ ⇒ **登记进台账** |

**④ 🟢 G2 的「slot 与 duration 必须写成两个轴」—— 本层台账**早已这样写**（§4.10.19.3 的两处不精确条）**

| 项 | 内容 |
|---|---|
| G2 的原话 | 「article-api is on 70s, comment-api and favorite-api on 3s ⇒ **same slot**（+6 before the budget start at +7）but **different duration** ⇒ slot and duration must be written as two axes」 |
| **本层核对** | 🟢 **本层台账已按两轴写**（§4.10.19.3 的「第二处不精确」条：article 域 **70s**（`article-api/application.yml:234` `http-timeout: 70s`）· comment-api **3s**（`:185`）· favorite-api **3s**（`:157`）⇒ ⇒ **槽位相同（`+6` 在预算起点 `+7` 之前）但时长不同**）⇒ ⇒ **G2 的这条 = 本层已有口径 ⇒ 无需改动** ⇒ ⇒ **两树口径一致** |

**⑤ 🟡 G2 状态与「那两道门禁」** | G2 树 HEAD 已前移到 **`a9b235c`**（46 笔）⇒ ⇒ **G2 说的「那两道门禁仍排在你这边」已过期** —— 本层 **FB-S1 已落码**（`d4e4745`）· **FB-S2 已改派合并收口执行者**（§4.10.43 ② 已登记）⇒ ⇒ ⇒ **G2 的 task 台账 items 5/6/7 里那两条，证据仍然有效（给合并收口执行者用），但「排在本层队列里」这个状态已不成立** ⇒ ⇒ **无需再回执**（本层 22:5x 那封已进它队列，尚未被消化）⇒ ⇒ **若它仍在等 ⇒ 那是队列没消化，不是本层没做**。

**⑥ 本层这一条** | **零落码**（① 是口径澄清 + 归属核对 · ② 是转派合并收口执行者 · ③ 是撤销一条拦截 · ④ 是核对一致 · ⑤ 是状态订正）⇒ ⇒ ⇒ **本层 `service/**` 与 `tools/**` 零改动** ⇒ ⇒ ⇒ **第十四次全量的「用例总数」口径仍为 **2486（推算）**；其余口径不变** ⇒ ⇒ ⇒ **本层 HEAD `4cec90e`（基线 `a282012` 之上 82 笔，未 push）**。

#### 4.10.46 🔴🟢 **G3 的 favorite 容器上限要求 ⇒ 自我订正（上一轮结论**错**）＋ 真增量只剩「结构锁」⇒ 落码 1 条 ＋ 变异 M1/M2 证据**（2026-10-27 深夜 · **生产代码零改动**）

> **本节零生产代码改动** ⇒ 只动**测试**（`service/common/src/test/**`）＋ 台账。
> ⇒ ⇒ **AGENTS.md §3.1「以 Go 为唯一基准」不涉及**（本节不判行为对错，只判**机器网**是否到位）。

**① 🔴 自我订正：上一轮结论「`service/common/src/test` 里 `MAX_PARAMETER_COUNT` / `limitCustomizer` **零命中** ⇒ ⇒ 缺机器网」—— 这个结论是**错的**

| 项 | 核对结果 |
|---|---|
| **上一轮的说法** | 「既有测试里**零命中** ⇒ ⇒ 「无条件抬上限」只活在注释里 ⇒ ⇒ 谁把它改回按 mode 分支，没人拦 ⇒ ⇒ 本层正在补真 Tomcat 机器网」 |
| **实测（本轮重核）** | 🟢 **既有守门已经钉住了**：`service/common/src/test/java/com/rag/common/http/GoStyleParamLimitOffModeGateTest.java`（**`cd893d1`**，第十三轮，G3 反馈 (b) 落码时同批加的，290 行）⇒ ⇒ **断言①** `runtimeParameterCountIsRaisedEvenWhenGateIsOff`：`assertEquals(10001, connector().getMaxParameterCount(), …)`（**字面量 10001**）⇒ ⇒ **断言②** 活体：2049 个非空值 ⇒ **200 放行**（无 HTML 错误页）⇒ ⇒ **断言③** 闸门侧：容器里那个闸门 `mode()` == `OFF` |
| **⇒ 结论订正** | ⇒ ⇒ **G3 的「容器上限的抬升必须对所有域无条件生效」这条要求，早已被机器网钉住**（`cd893d1`）⇒ ⇒ **G3 引的「`:109` 在 OFF 时直接 return」是**旧版描述**（当前 `:207`/`limitCustomizer()` 无任何 mode 分支）⇒ ⇒ **本层不需要为这条要求新落码** |
| **🔴 判据错在哪（可复用的教训）** | ⇒ **「零命中」的判据选错了**：我搜的是**标识符**（`MAX_PARAMETER_COUNT` / `limitCustomizer`）⇒ 既有守门用的是**字面量 `10001`** ⇒ ⇒ **标识符零命中 ≠ 语义零覆盖** ⇒ ⇒ 这是 §4.10.38 ④（「『零命中』必须先复核判据」）的**新变种**：上次的错是**管道/`head` 截断**（判据错在**管道**），这次判据本身**没错**（全仓 grep、无截断）⇒ 错在**搜的是名字、不是被测语义** ⇒ ⇒ **⇒ 新规则：要判「某行为有没有网」，必须搜「断言值 / 被测行为」，不能只搜「被测对象的标识符」** |
| **⇒ 另一处自违** | ⇒ ⇒ §4.10.38 ③ 已明写「**不造第二条守门守同一件事**」⇒ ⇒ 本轮**原本**要新建一个独立真 Tomcat 守门类 ⇒ **违反了本层自己上一轮立的规矩** ⇒ ⇒ 处置：撤销那个独立类（`mavis-trash`，未提交⇒ 无历史）⇒ ⇒ **改为在既有守门里追加** |

**② 🟢 真增量 = **一条结构锁**（不是三条）**

| 层 | 既有 `GoStyleParamLimitOffModeGateTest` 覆盖 | 本轮新增 |
|---|---|---|
| **行为**（运行期值） | ① mode=off 时真 Tomcat connector 上限 == **字面量 10001** ✅ | — |
| **行为**（活体） | ② 2049 个非空值 ⇒ 放行 200 + 无 HTML ✅ | — |
| **行为**（闸门） | ③ 容器里那个闸门 `mode()` == `OFF` ✅ | — |
| **结构**（签名） | ❌ **零覆盖** | 🆕 **④ `limitCustomizer()` 必须 `static` 且**无参**` ⇒ ⇒ 产物**结构上**不可能依赖 mode** |

- ⇒ ⇒ **为什么结构锁是真增量**（不是重复）：断言①是**行为性**的，它抓得住「把 mode 分支**接回** `customize()`」；但它**抓不住**下面这一种 ——
  ```java
  public static TomcatConnectorCustomizer limitCustomizer(String mode) { … }
  // customize() 里仍无条件调用它 ⇒ 运行期仍是 10001 ⇒ 断言① 仍绿
  // ⇒ 但「抬上限的产物与 mode 无关」这条**结构性**契约已破
  // ⇒ ⇒ 下一任 agent 只要顺手加一个 if (mode == OFF) return; 就静默退化
  ```
  ⇒ ⇒ ④ 把「产物不可能依赖 mode」变成**签名层面的事实** ⇒ ⇒ 那条退化**写不出来** ⇒ ⇒ 这是 AGENTS.md §3.2/§3.3 的典型缺口（**注释里的契约无网**）的**合法补网**。
- ⇒ ⇒ **为什么不加第 5 条「直接调 `limitCustomizer()`」**（我一度加过、已撤）：在 ④ 成立的前提下它**零增量**（「值写错」会被①抓住；「方法被删」会被④抓住）⇒ ⇒ 且它会**破坏变异验证的干净性**（④ 一旦被破坏，⑤ 的**编译**就先失败 ⇒ 红在编译错误上 ⇒ 不是「红在目标断言上」）。

**③ 🟢 落码：`service/common/src/test/java/com/rag/common/http/GoStyleParamLimitOffModeGateTest.java` 追加 ④（**不新建第二个真 Tomcat 守门** ⇒ 遵守 §4.10.38 ③）**

- **④ 的实现取巧点（有意为之）**：用 `getDeclaredMethods()` **遍历 + 按名字筛**，**不用** `getMethod("limitCustomizer")`（无参查找）⇒ ⇒ 否则变异一旦给它加参数 ⇒ **查找本身**就抛 `NoSuchMethodException` ⇒ ⇒ 测试红在「**异常**」上而不是「**无参**」这个断言上 ⇒ ⇒ **红得没有意义**（AGENTS.md §3.3.3）。
- **期望值写**字面量** 10001，**刻意不引用** `MAX_PARAMETER_COUNT` 常量 ⇒ ⇒ 否则「有人把常量改成 2048」会**同时改掉期望值** ⇒ ⇒ **守门变哑**（§3.3.3「期望值同源 ⇒ 守门是哑的」）⇒ ⇒ 与既有断言①口径一致。

**④ 🟢 变异验证（M1 / M2 各**恰好 1 条红**、且**红在目标断言上**）**

| 变异 | 改法 | 实测结果 |
|---|---|---|
| **M1**（专属） | `limitCustomizer()` 加 `String mode` 参数（+ 构造器存 `mode` + `customize()` 传参）⇒ **运行期仍无条件抬** | `Tests run: 4, Failures: 1, Errors: 0` ⇒ **红在 ④ 的「必须无参」**：`expected: <0> but was: <1>`（`GoStyleParamLimitOffModeGateTest.java:229`）⇒ ⇒ **①②③ 全绿**（运行期上限仍 10001）⇒ ⇒ **④ 是那一格唯一的网** |
| **M2**（专属） | `public static` ⇒ 改 `public`（instance，无参） | `Tests run: 4, Failures: 1, Errors: 0` ⇒ **红在 ④ 的「必须是 static」**：`expected: <true> but was: <false>` ⇒ ⇒ **①②③ 全绿** |
| **复原** | 两个变异均已 `git checkout` 复原 ⇒ 工作区只剩测试文件一处改动 | ✅ |

**⑤ 🟢 回归口径（核对日期 2026-10-27 深夜 · 代码态 = HEAD + 本轮 1 条测试改动）**

| 项 | 值 |
|---|---|
| **改了什么** | **仅** `service/common/src/test/java/com/rag/common/http/GoStyleParamLimitOffModeGateTest.java`（+1 条断言 ④ + 2 个 import）⇒ ⇒ **生产代码零改动** |
| **`service/common` 全模块** | `mvn -o -B -DargLine=-Xmx2g -am -pl service/common test` ⇒ **331 / 0f / 0e / 0s**（上一轮 **330** ⇒ **+1** = 新增的 ④）⇒ **EXIT=0** |
| **守门类单跑** | `GoStyleParamLimitOffModeGateTest` ⇒ **4 / 0f / 0e / 0s**（3 ⇒ +1） |
| **全量口径** | ⇒ **第十四次全量（代码态 `15cf93a`+`d546f45`+`407c230`）的其余口径不变**；**用例总数推算 2486 ⇒ 2487**（= 2486 + 本轮 `service/common` +1）⇒ ⇒ ⚠️ **仍是推算，未跑全量**（本轮只复跑了 `service/common`）⇒ ⇒ **波末收口回归时必须重跑全量并重新逐模块求和** |
| **三门禁** | 本轮未跑全仓 `check-*.py`（本轮**零产品面**改动 ⇒ 写权/装配/耦合三类守门的输入未变）⇒ ⇒ **以第十四次全量的口径为准**，波末统一复跑 |

**⑥ 🟢 回 G3 的口径（本节即回执底稿）**

1. 🟢 **「容器上限的抬升必须对所有域无条件生效」—— 已满足，且**早就有机器网**：`cd893d1`（本层第十三轮）已把两轴解耦（`GoStyleTomcatParameterLimitCustomizer:207` 的 `limitCustomizer()` **无任何 mode 分支**）⇒ ⇒ 既有守门 `GoStyleParamLimitOffModeGateTest` 的**断言①**（真 Tomcat + `mode=off` ⇒ 运行期 == 10001）**就是**那条机器网 ⇒ ⇒ **G3 引的「`:109` 在 OFF 时直接 return」是旧版描述**（`:109` 现在是 javadoc 里讲**旧版**如何分叉的历史说明）⇒ ⇒ ⇒ **「本域零改动」的裁定是对的**。
2. 🟢 **本层这一轮只加了一条**「结构锁」（④）⇒ ⇒ **不影响 G3 域内任何行为** ⇒ ⇒ **G3 的域内守门（`FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError` 改成 Go 语义放行）**不需要为本轮重做**。
3. 🟡 **仍归 G3 域内的那一格**：**总「值」数 > Go 的 10000 段上限**（Tomcat 拒 HTML / Go 拒 text/plain）⇒ ⇒ **G3 已显式登记** ⇒ ⇒ 本层**不代销**。

#### 4.10.47 🔴🟢 **端口段表当轮复测（G3 树 @ `3249ef7`）+ 一处**订正**（comment-rpc 的 OS 分配口是 **4 处**，不是 1 处）+ 一处**新发现**（comment-rpc **没有段管理类**）⇒ 转派 G3 三条**（2026-10-27 深夜 · **本层零落码**）

> **本节零落码** ⇒ 只做**复测 + 订正 + 转派**（AGENTS.md §5.3d「域侧能自修的域侧修」）。
> ⇒ ⇒ **端口段表的落点 = 本文件**（§4.10.15.8 ②）⇒ ⇒ **本层是 global 协调方 ⇒ 只给结论与段号，不替 G3 改域内代码**。

**① 段表复测（核对日期 2026-10-27 深夜 · **G3 树 @ `3249ef7`**，非旧记录的 `dec6348`）**

| 段 | 归属 | 本轮实测 | 与 §4.10.19.3 的一致性 |
|---|---|---|---|
| `21400–21499` | G3（favorite-rpc 真进程）| `service/favorite/favorite-rpc/src/test/.../support/RealGrpcPort.java` ⇒ `FIRST = 21400` / `LAST = 21499` | ✅ **与旧记录一致 ⇒ ⇒ ⚠️ 即「仍未迁段」**（预分配的 `22100–22199` **还没用上**）|
| `21400–21599` | G3（favorite 探针段，3 个探针 + 1 个守门）| `tools/probe-gate/favorite/check-port-ownership.sh:12`（段说明）· `:81-82`（段断言）· `:91`/`:95`/`:100`（用 `21400` 做「被占」样本）| ✅ 一致 |
| **`21500`** | G3（comment-rpc 真 gRPC）| `service/comment/comment-rpc/src/test/.../rpc/CommentSpanWiringTest.java:94` `grpc.server.port=21500` | ✅ 一致 ⇒ ⇒ **仍在 favorite 探针段内** ⇒ ⇒ **第二处重叠仍未解** |
| `22100–22199` | G3（favorite-rpc **迁出用**）| 🟢 **实测零占用**（G3 树全仓 `22100\|22600` ⇒ **零命中**）| ✅ 一致 ⇒ ⇒ **段仍可用** |
| `22600–22699` | G3（comment-rpc **迁出用**）| 🟢 **实测零占用**（同上）| ✅ 一致 ⇒ ⇒ **段仍可用** |

**② 🔴 订正（§4.10.38 ② 登记的数错了）—— comment-rpc 落 OS 临时段的，是 **4 处**，不是 1 处**

| # | 文件:行号 | 取口方式 | 落在哪 |
|---|---|---|---|
| 1 | `service/comment/comment-rpc/src/test/.../rpc/CommentRpcGrpcInterceptorWiringTest.java:101` | `"grpc.server.port=0"` | OS 临时段 |
| 2 | `service/comment/comment-rpc/src/test/.../rpc/CommentRpcSlowThresholdGateTest.java:120` | `"grpc.server.port=0"` | OS 临时段 |
| 3 | `service/comment/comment-rpc/src/test/.../rpc/CommentRedisDisconnectFastRejectGateTest.java:91` | `"grpc.server.port=0"` | OS 临时段 |
| 4 | `service/comment/comment-rpc/src/test/.../rpc/CommentRpcRuntimeBootGateTest.java:92`（`int port = freePort()`）· `:96`（`"--grpc.server.port=" + port`）· `:185`（`new ServerSocket(0)`）| **OS 分配**（不是字面量 `0`，但同样落临时段）| OS 临时段 |
| — | `CommentSpanWiringTest.java:94` | `"grpc.server.port=21500"`（**硬编码固定口**）| 🔴 **落在 favorite 探针段 `21400–21599` 内** |

⇒ ⇒ **§4.10.38 ② 只点了第 1 处** ⇒ ⇒ **订正为 4 处** ⇒ ⇒ 而且第 4 处**形态不同**（`freePort()` + `new ServerSocket(0)` ⇒ 不是 `port=0` 字面量 ⇒ ⇒ **按字面量 grep `port=0` 会漏掉它** ⇒ ⇒ 「查端口用法」不能只搜 `port=0`）。
⇒ ⇒ ⚠️ **同域自相矛盾**：`CommentSpanWiringTest.java:90-94` 的注释**明确写**「`port=0` 会让 OS 从临时端口段挑，而那是 **Docker 随机发布容器端口用的同一段** ⇒ `EADDRINUSE` ⇒ 抖动」⇒ ⇒ 而**同域另外 3 个文件正用 `port=0`** ⇒ ⇒ **那个类正好踩中它自己注释里说的坑**（§4.10.38 ② 只发现了第 1 处）。

**③ 🆕 新发现（决定迁段工程量）：comment-rpc **没有** `RealGrpcPort` 段管理类**

- 实测：`find service/comment -name "*Port*.java"` ⇒ **零命中**（favorite-rpc 有 `RealGrpcPort.java` + `RealGrpcPortTest.java`；**comment-rpc 两样都没有**）⇒ ⇒ `21500` 是**裸硬编码常量**（`CommentSpanWiringTest.java:94`）。
- ⇒ ⇒ **两处迁段的工程量**不同，**不能当成同一件事派**：

| 迁段对象 | 现状 | 改法 | 工程量 |
|---|---|---|---|
| **favorite-rpc** `21400–21499` ⇒ `22100–22199` | 有 `RealGrpcPort.java`（`FIRST`/`LAST` 两个常量）⇒ 脚手架段 `22050–22099` 在**另一个文件**里 | 改 `RealGrpcPort.java` 的 **2 个常量** ⇒ 探针段不动 ⇒ `check-port-ownership.sh` 守门**不用动** | 🟢 **小**（§4.10.19.4 已给结论，本轮**仍未执行**）|
| **comment-rpc** `21500` ⇒ `22600–22699` | **无段管理类** ⇒ 裸常量 | 要么新建一个 `RealGrpcPort` 同款，**要么**只把常量改成 `22600`（但**那样只占 1 个口**，不是一段 ⇒ ⇒ 与「段治理」的初衷不同）| 🟡 **大**（**要 G3 自己裁**：是「补一个段管理类」还是「只迁这一个口」）|

**④ 转派 G3 三条（本层零落码）**

1. 🟡 **favorite-rpc 段迁出**：`RealGrpcPort.java` 的 `FIRST`/`LAST` ⇒ `22100`/`22199`（**2 个常量**）⇒ 脚手架段 `22050–22099` **不动** ⇒ 探针段 `21400–21599` **不动** ⇒ `check-port-ownership.sh` **不用动** ⇒ **实测确认 `22100–22199` 零占用** ⇒ **本层预分配的段已被 G3 认可，只差落码**。
2. 🟡 **comment-rpc 的 `21500` 迁出** ⇒ 目标段 `22600–22699`（**实测零占用**）⇒ ⇒ **但 comment-rpc 没有段管理类** ⇒ ⇒ **请 G3 先裁「补一个 `RealGrpcPort` 同款」还是「只迁这一个口」** ⇒ ⇒ **本层不替它裁**（裁法不同 ⇒ 守门形态不同 ⇒ 属域内设计）。
3. 🔴 **comment-rpc 的 4 处 OS 分配口改专用段**（§② 表）⇒ ⇒ 其中第 4 处（`freePort()` / `new ServerSocket(0)`）**按 `port=0` 字面量查不出来** ⇒ ⇒ 查端口用法必须**逐个测试类看**，不能只 grep。

**⑤ 本层这一条** | **零落码** ⇒ ⇒ **端口段表落点（`docs/DRIFT/global/vs-common.md`）已按当轮复测更新** ⇒ ⇒ **全量用例数不变**（本节零代码改动）⇒ ⇒ ⇒ **本层 HEAD `9dd67ab`**（基线 `a282012` 之上 84 笔，未 push）。

#### 4.10.48 🔴🟢 **G3 回信的逐条核验（2026-10-27 深夜）—— ① 它 ack 的是我**第 11 次**旧回执 ② `:109` **二次订正** ③ 一族 **17 文件**无 owner ④ 🔴 「等共享层落地」这个判断**本身错了**（⇒ 会让人多等一轮）**（本层零落码）**

> **本节零落码** ⇒ 只做**核验 + 订正 + 转派**。
> ⇒ ⇒ **纪律依据**：跨会话消息**可能迟到数小时** ⇒ ⇒ 里面的每个事实都要按**当前状态**重核（AGENTS.md §3.1「以 Go 为唯一基准」的精神在跨会话场景的延伸）。

**① 🔴 先对时：G3 的回信 ack 的是本层**第 11 次**全量的旧回执 ⇒ ⇒ 它在按**过期 3 轮**的状态做决定**

| 项 | G3 ack 的（**第 11 次**）| **当前（**第 14 次**）** |
|---|---|---|
| 全量用例数 | **2471** | **2480**（⇒ 本轮 +1 ⇒ **2487**，⚠️ **推算、未跑全量**）|
| 代码态 | `040a201` | **`15cf93a` + `d546f45` + `407c230`** |
| 本层 HEAD | `cec7131` | **`c9fe18a`**（基线 `a282012` 之上 **85** 笔）|
| D 方案起点 | 它给的 `cec7131` | **`c9fe18a`**（merge-base `cd893d1`）|

⇒ ⇒ **⇒ 新规则（**跨会话场景的第二条**）**：**「收到回执」这件事本身也要对时** ⇒ ⇒ 不只「发出的内容」会过期，**「对方基于哪一版回执做的判断」也会过期** ⇒ ⇒ ⇒ **回执里必须带上「我这封是基于哪一次全量 / 哪个 HEAD」** ⇒ ⇒ 否则对方会拿着你的旧数字去合并。

**② 🔴 `:109` **二次订正**（G3 重申了一次 ⇒ ⇒ 本层给「可自核的探针」而不是第二遍文字断言）**

- G3 原话：「当前 `GoStyleTomcatParameterLimitCustomizer.java:109` 在 OFF 时**直接 return**」⇒ ⇒ **在我树与 G3 树**都**不成立**。
- **实测**：
  - `:107-111` = **javadoc 文字**（「**旧版实测**：favorite … 运行期 = **2048** ⇒ 它是**唯一**停在 2048 的域」）⇒ ⇒ **它描述的是历史，不是现状**；
  - `limitCustomizer` 定义 = **我树 `:211`**（本层 HEAD `8b12d68`） / **G3 树 `:206`**（G3 树 `3249ef7` / `a20282f` / `7b3b27d` **三处实测同为 `:206`**）= `public static TomcatConnectorCustomizer limitCustomizer()` ⇒ ⇒ **无参、无 mode 分支**；
  - 整个文件 `Mode.OFF` **只出现 2 次**，**两次都在 javadoc** ⇒ ⇒ 代码里**零** mode 分支。
- ⇒ ⇒ **「(b) 容器上限抬升对所有域无条件生效」早已落地**（`cd893d1`，**两棵树都有**）⇒ ⇒ **机器网也早已有**（`GoStyleParamLimitOffModeGateTest:121-122`，**两棵树都有**）⇒ ⇒ ⇒ **G3「本域零改动」的裁定是对的**。
- ⇒ ⇒ **教训（与「行号随分支漂移」同族，但更隐蔽）**：**同一个行号在两棵树里指向不同内容** ⇒ ⇒ `:109` 在我树是「旧版实测」的 javadoc，在 G3 树是**另一句** javadoc ⇒ ⇒ ⇒ **引用行号时必须带「树 + commit」**（AGENTS.md §3.1 的引用纪律在跨树场景的延伸）⇒ ⇒ ⇒ **本层回执里凡是「行号 + 内容」的断言，都应附一条可复制的探针命令**，让对方能自核，而不是要求对方相信我。

**③ 🔴 `component-semantic-verification/springboot-semantic-model.md` ⇒ **要按族报，不是按 2 处****

| 项 | 实测 |
|---|---|
| 根目录 `component-semantic-verification/` | **17 个 tracked 文件**（`README.md` · `article-api-cp.txt` · `component-contract.md` · `equivalence-matrix.md` · `go-facts.txt` · `go-probe/`（4）· `go-semantic-model.md` · `java-probe.sh` · `mvn-article-api-test.log` · `semantic-drifts.md` · `spring-run.log` · `springboot-semantic-model.md` · `test-cases.md` · `unresolved.md`）|
| 写权归属 | 🔴 **四条 lane 的 `ACTIVE` 白名单全无** ⇒ ⇒ **整族 17 文件无 owner** |
| U+FFFD（**本层树**）| 🟢 **0**（根目录那份与 `service/article/article-rpc/...` 那份**都干净**）⇒ ⇒ G3 树里根目录那份的 **5 字节 @ :269/:320** 是**G3 树**的实况 ⇒ ⇒ **归 G3 树自己修**（或随合并取本层版本）|
| 第二份副本 | `service/article/article-rpc/component-semantic-verification/springboot-semantic-model.md` ⇒ ⇒ **在 G3 写权面内**（article 域）⇒ ⇒ **那份归 G3** |
| 引用 | `docs/DRIFT/users/admin/rpc.md:2492`（表格行，5 处）⇒ ⇒ **引用在 G1 台账、文件不在任何写权面** |

⇒ ⇒ **处置**：根目录那 **17 文件整族** ⇒ **不在本层写权面** ⇒ ⇒ **交合并收口执行者**（与 §4.10.45 ② 登记的「`docs/Pitfalls/**` 整族 49 文件 / 13 目录族无 owner」**同款处理**）⇒ ⇒ **本层不动**（不越权）⇒ ⇒ **G3 也不认领**（它不属于 G3 域）⇒ ⇒ **G1 也不认领**（引用 ≠ 写权 —— §5.3f 的口径）。

**④ 🔴🔴 G3 的「等共享层落地」这个判断**本身错了** ⇒ ⇒ 若不订正 ⇒ 它会**多等一轮**（或干脆不做）**

> G3 原话：「**我树共享层根本没有 redis 自动配置**（`2f12236` 不在我树）⇒ ⇒ 断连快速失败在我域**尚未成立** ⇒ ⇒ **活体对拍只能等共享层落地**」

| G3 的说法 | 本层实测（**在 G3 树里跑**）| 判定 |
|---|---|---|
| 「我树共享层根本没有 redis 自动配置」 | ❌ **有** —— `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java` **存在** | 🔴 **错** |
| 「`2f12236` 不在我树」 | ❌ **`2f12236` 是 G3 树的祖先**（`git merge-base --is-ancestor 2f12236 HEAD` 通过）| 🔴 **错** |
| 「G3 域的域侧 Redis 断连副本 = 0」 | ✅ **对** —— 全仓生产代码 `RedisDisconnectedBehavior` 只命中 **2** 处：共享层那份 ＋ G1 的 `users/user/user-api/.../RedisDisconnectedBehaviorConfig.java` | ✅ **对** |

⇒ ⇒ ⇒ **净结论**：**G3 域（favorite / hot / comment / article）没有域侧副本遮蔽** ⇒ ⇒ **共享层那份在 G3 域直接生效** ⇒ ⇒ ⇒ **「断连活体对拍（favorite-api 侧）」现在就能做** ⇒ ⇒ ⇒ **不必挂起等共享层**。

⇒ ⇒ **⇒ 新规则（**可复用 · 本轮最值钱的一条**）**：**「要等某层落地」这个判断 ⇒ 必须先核那层在不在目标树里** ⇒ ⇒ 核法 = **文件存在性 ＋ `git merge-base --is-ancestor <该层落码的 commit> <目标树 HEAD>`** ⇒ ⇒ **不是**用「我记得它不在」⇒ ⇒ ⇒ **「等」这个字本身会让人挂起一件已经能做的事** ⇒ ⇒ ⇒ 且它的错误形态是**静默的**（不会报错，只是多等一轮 / 不做）⇒ ⇒ ⇒ **与「零命中是假信号」同族**：都是**「你以为的现状 ≠ 实际现状」**。

**⑤ 本层这一条** | **零落码** ⇒ ⇒ **全量用例数不变** ⇒ ⇒ ⇒ **本层 HEAD `c9fe18a`**（基线 `a282012` 之上 85 笔，未 push，工作区干净）。

#### 4.10.49 🟢🔴 **「引用损坏形态」的**通道规则**（本层采纳 G3 的 PIT-FAV-RPC-038 ⑤ ＋ 补一条**归因订正**：那 10 个字节**不是**本层发过去的）**（2026-10-27 深夜 · **本层零落码**）

> **本节零落码** ⇒ 只做**规则采纳 + 归因订正**。

**① 🔴 归因订正：G3 台账里那 10 个真 U+FFFD，**不是**本层发过去的**

| 候选来源 | 实测 | 判定 |
|---|---|---|
| **本层（G4）发出的消息** | **字节级扫本层会话存档**（`/Users/pr/.minimax/v2/sessions/2026/10/26/18-38-55-731-.../messages.jsonl`，**1 312 655 字节**）⇒ 真 U+FFFD 字节数 = **0** ⇒ 含它的行数 = **0** | 🟢 **排除本层** |
| **G1 会话存档** | 真 U+FFFD = **0** | 🟢 排除 |
| **G2 会话存档** | 真 U+FFFD = **19**（行 `266` / `336` / `338` / `339` / `340`）⇒ ⇒ **全部集中在它自己写的 Go 探针 `main.go` 的注释里**（「**全部被静默丢弃** —— `因为` 2 字被损成 2×FFFD」）⇒ ⇒ **G2 自己已发现并修掉**（它的 thinking 里记着「U+FFFD 字符数 = 2」并做了替换） | 🟡 **G2 自伤自愈，不外流** |
| **最可能路径** | 🔴 **`read` 工具的渲染输出** —— 读一个损坏文件时，工具把损坏字节**渲染成 U+FFFD** ⇒ ⇒ **照抄那段输出 = 把损坏形态搬进台账**（G3 自己早写过「read 输出会把正常汉字渲染成 U+FFFD×3 ⇒ 不能当证据」） | 🔴 **这才是通道** |
| **收口** | G3 报「**我写权面真损坏 = 0**（只有 article-api 那 2 处**故意**的 ext_info key 用例）」⇒ ⇒ ⇒ **当前无活跃损坏** | 🟢 |

**② 🟢 本层采纳的规则（写进本层台账，防止下一任 agent 重犯）**

> **规则 U-1**：**引用损坏形态作证据时，只用可见转义写法**（`U+FFFD` / `\uFFFD` / `␦␦`），**绝不把真 U+FFFD 字节写进台账 / 守门 / 提交信息**。
> ⇒ ⇒ **理由**：台账是**会被别人照抄**的 ⇒ ⇒ 一个真字节进去，就会**在复制链上扩散**（G2 自伤、G3 被扩散 ⇒ ⇒ 同一形态**两次出现在不同 lane**）⇒ ⇒ ⇒ 而**台账自己不需要那个字节** —— 它只需要**说清「这里有 N 个 U+FFFD，在第几行」**。
> ⇒ ⇒ **附带**：「**修 U+FFFD 的动作本身可以引入新的 U+FFFD**」（G3 的 PIT-FAV-RPC-038 ⑤ 已有此条）⇒ ⇒ **修完必须再扫一遍**（字节级），不能凭肉眼。

**③ 🟢 本层的自查结果（当轮实测）**

| 范围 | 真 U+FFFD 命中 |
|---|---|
| `service/common/**`（全量，排除 `target/`）| **0 个文件** |
| `service/users/users-common/**`（全量）| **0 个文件** |
| `docs/DRIFT/global/vs-common.md`（本文件）| **0**（ASCII 文本 `U+FFFD` 出现 36 次 ⇒ 全是**可见文本**，不是字节）|
| `tools/errmsg-probe/README.md`（G2 转派、本层接手）| **0** |
| `tools/sql-statement-probe/favorite/compare.py`（G2 转派、本层接手）| **0** |
| **本层发出的消息**（会话存档字节级）| **0 / 1 312 655 字节** |

**④ 本层这一条** | **零落码** ⇒ ⇒ **全量用例数不变** ⇒ ⇒ ⇒ **本层 HEAD `e94475d`**（基线 `a282012` 之上 86 笔，未 push，工作区干净）。

#### 4.10.50 🟢 **独立复核（verifier）回执的【时序订正】＋ 三棵 lane 树的门禁「绿红错位」实测 ＋ §1e 角色边界确认**（核对日期 2026-10-28 00:0x · 本层 HEAD `ff81709`）

**① 🔴 verifier 的报告是「修复前」的快照 ⇒ 它的 5 条发现早已落码 ⇒ 本轮零新增落码**

| 项 | 实测事实 |
|---|---|
| verifier 任务出稿 | **2026-10-27 19:44:04 CST**（会话存档时间戳 `1793101444865`） |
| 本层修复提交 | `407c230` = **19:48:00 CST** ⇒ ⇒ **比报告晚 4 分钟** |
| 报告审的那棵树 | `3be1dbc`（= `139b428` 之后 · `407c230` 之前） |
| 报告的 5 条发现 | 3 🟡（窗口 = 上界 × 间隔 未写明 · WARN 每片刷屏 · javadoc 对客户端谱系表述过宽）＋ 2 🟢（expect-continue 也计入 interim 计数器 · M2「只红 ③」只在**隔离单类跑**时成立） |
| 当轮处置 | **全部已落码**（§4.10.36）⇒ ⇒ **本轮零新增落码** ⇒ ⇒ **第十四次全量口径（代码态 `15cf93a` + `d546f45` + `407c230`）仍有效** |
| ⚠️ 教训 | **子代理报告的「发现」必须先核时序再落码** ⇒ 否则会把「已修」当成「待修」重做一遍 ⇒ ⇒ **与 §4.10.48「回执要带基于哪一次全量」同源**：**被引用的那份材料本身也可能是旧版** |

**② ✅ §1e 角色边界（trunk `4ae5461` + `c9cf2c1` · 2026-10-27 用户定）⇒ 本层角色未变**

| 角色 | 负责 | 本层对照 |
|---|---|---|
| **G4**（`align/g4-service-common`） | `service/common/**` + `service/users/users-common/**` 的**代码**落码 ＋ 接反馈单执行 | ✅ **本层**（职责未变） |
| **合并收口执行者** | 在**合并收口**这一步统一更新共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` / `tools/difftest/README.md`） | ⇒ **它是一个【步骤】，不是一个常驻会话** ⇒ ⇒ 本层登记的 13 个 SHARED 项**留在合并收口处理**（不再升级给任何会话） |
| **域波 lane** | 自己域的 9 轴结论 ＋ 域侧能自修的域侧自修 | — |
| **运行时故障处置** | 临时角色（只解 runtime 故障） | ⛔ **不承接 lane 的活**（§1e 硬边界 3） |

⇒ ⇒ **「整合者」这个词作废**（四条 lane 的既有记忆里还在流通 ⇒ §1e 明写「**光改守门里的措辞不够**」）⇒ ⇒ **本层台账 88 处（70 行）「整合者」已全部就地改为「合并收口执行者」**（本节 ⑤）⇒ ⇒ **共享面事项从本轮起只做两件事：登记 ＋ 落文件**（§1e 硬边界 1）—— **不再上报给任何会话**。

**③ 🔴 三棵 lane 树的容器上限守门「绿红错位」实测（当轮 · 同一套守门跑三棵树）**

| 树（HEAD） | task-api yaml | 守门脚本版本 | 跑**自己的**守门 | 跑**本层的**守门（真源派生） | 定性 |
|---|---|---|---|---|---|
| **G1** `ddb63f7` | `mode: raw` | ✅ **真源派生**（`d4e4745` 已并入） | **EXIT=1 红**（实际 `raw` / 应为 `off`） | **EXIT=1 红**（同） | 🟡 **红得对** ⇒ task-api 缺 G2 的 yaml 订正 ⇒ **G2 的域**，不是 G1 的回归 |
| **G2** `7725efd` | `mode: "off"` ✅ | ❌ **旧副本**（`d4e4745` 未并入） | **EXIT=1 红**（实际 `off` / 应为 `raw`） | **EXIT=0 绿** ✅ | 🟡 **红得冤** ⇒ 旧副本把 G2 自己的订正当成错 |
| **G3** `cb70876` | `mode: raw` | ❌ 旧副本 | **EXIT=0 绿** | **EXIT=1 红**（实际 `raw` / 应为 `off`） | 🔴 **绿得假** ⇒ 旧副本 ＋ 未订正 yaml 互相抵消 |

⇒ ⇒ **三棵树各红各绿，但没有一个是「对」的** ⇒ ⇒ **A（真源派生脚本）与 B（task-api yaml 订正）必须同批合入** —— §4.10.41 ① 的结论，本轮**三棵树同时实测复现**。
⇒ ⇒ **三条 lane 都要把本层分支并到 `c26e462`**：G1 ✅ 已并（`ddb63f7`）· G2 ❌ 只并到 `d546f45` · G3 ❌ 未并。
⇒ ⇒ ⚠️ **G1 树合并后门禁会红** —— 那是**预期的正确的红**（等 G2 的 `4786eb5` 进合并收口即绿）⇒ ⇒ **G1 不得在自己树里改 task-api 的 yaml**（那是 G2 的域 ＝ 写权越界）。

**④ 本层这一条** | **并入 trunk 的 2 笔**（`4ae5461` + `c9cf2c1`）⇒ HEAD `ff81709` · **零冲突**
⇒ 那 5 个被改的文件本层自己的 87 笔（`a282012..c26e462`）**一笔未碰** ⇒ ⇒ 该写权守门**只看白名单模式 ＋ 分支是否存在，不看分支 diff** ⇒ ⇒ 并入后 **4 个写权门禁 ＋ 5 个台账门禁全 EXIT=0**
⇒ ⇒ ⚠️ **口径订正（不得读成「本层 87 笔零生产改动」）**：`a282012..c26e462` 期间**确有**生产代码改动（`15cf93a` 探针上界 · `853767b` A9 span · `1fb26b2` Redis 收编 等）⇒ ⇒ **准确说法是**：
> **自第十四次全量的代码态（`407c230`）起 ⇒ 本层只改了 1 个 `.java` 的 javadoc**（`d4e4745` 的 `ObservabilityLifecycle.java` 注释口径）＋ **3 个测试文件** ＋ `docs/**` ＋ `tools/**` ⇒ ⇒ **可执行代码零改动** ⇒ ⇒ `service/common` 已单独复跑（330 ⇒ **331**，§4.10.46 的结构锁 ④）⇒ ⇒ **第十四次全量口径（2480 条）其余模块仍有效**；⚠️ **全量推算值 = 2487，但未跑全量 ⇒ 不可当实测数引用**（§4.10.37）。

**⑤ 措辞订正** | 本层台账 **88 处**「整合者」⇒ **全部改为「合并收口执行者」** ⇒ 理由见 ② ⇒ ⇒ **后续 lane 读到的每一处都指向一个【可指向的角色】**，而不是一个不存在的会话 ⇒ ⇒ 与 §4.10.50 ① 同款纪律：**别让读者按一个空指针去找人**。

#### 4.10.51 🟢 **G2 的 FB-S1/FB-S2 根因归属补充单 ⇒ 逐条处置 ＋ FB-S2「三选一」的正式答复 ＋ 跨树引用纪律的自我审计**（核对日期 2026-10-28 00:2x · 本层 HEAD `8b12d68` → 本节提交）

**① 🔴 这封补充单是【过期上下文】⇒ 但它的根因定位全部成立且有价值**

| 项 | 实测 |
|---|---|
| 补充单出稿 | 2026-10-27 **13:2x**（距本节约 **11 小时**）⇒ 一直在本层队列里 |
| 它引的树 | 本层 `cec7131`（= **第 11 次**全量那会儿）⇒ **落后本层 5 代** |
| 根因归属 | ✅ **全部成立**（下逐条核）⇒ ⇒ **值得吸收的是「根因」与「三选一」，不是它的状态判断** |

**② FB-S1 ⇒ 🔴 已闭环（我树 `d4e4745`）—— 它的根因定性【完全正确】，但它诊断的是我树的旧快照**

| 环节 | 它指的是（G4 树 `cec7131`） | **我树现状（`8b12d68`）** |
|---|---|---|
| ① 权威语义 | `GoStyleFormLimitMode.java:237` = `Mode.OFF` | ✅ **同**（`:237` 仍是 `Mode.OFF`） |
| ② 域侧现状 | `task-api/application.yml:176-178` = `mode: raw` | ✅ **同**（但那是 **G2 的域文件** ⇒ G2 已改） |
| ③ 守门期望 | `check-tomcat-limits.py:149` 写死 `"raw"` | ✅ **已改** ⇒ `:179` `derive_expected_form_limit_mode()` + `:207` `EXPECTED_FORM_LIMIT_MODE = derive_expected_form_limit_mode()` ⇒ **期望值已从 `SEMANTIC_REACHABILITY` 派生 ⇒ 单一真源 ⇒ 不会再 stale** |

⇒ ⇒ **它建议的修法（:149 ⇒ 从 `SEMANTIC_REACHABILITY.semanticMode` 取）= 我落码的形态**（落码提交 = **`d4e4745`**）⇒ ⇒ **FB-S1 在本层已闭环**；**它之所以还红，是因为【它的树】没并入 `d4e4745`** ⇒ ⇒ **re-merge 到 `8b12d68` 即绿**（实测：**本层的真源派生守门跑 G2 的树 = EXIT=0 绿** · 假 ROOT 模拟）。
⇒ 🟢 **附一条它没提、但我要登记的教训**：本层最初也差点做成「**直接改 `:149` 的字面量**」⇒ 那只是把 stale 换成「**另一份会再 stale 的副本**」⇒ ⇒ **落码时必须删副本、改为派生**（这正是它建议的形态 —— **根因定位对了，修法形态也要对**）。

**③ 它指出的 yaml 注释 stale ⇒ 属实 ⇒ 但那个文件是 G2 的域 ⇒ 本层不碰**
⇒ `service/task/task-api/src/main/resources/application.yml:177` 那条注释**只用了轴②（拒成什么形状）来定轴①（开不开）**⇒ 而轴①在 task 域的判据是「**上限不可达 ⇒ OFF**」（`GoStyleFormLimitMode` 的 javadoc 明列「`Mode.OFF` ⇔ 域自捕获 **或** 上限不可达」）⇒ ⇒ 那行注释**确实 stale** ⇒ ⇒ **G2 的版本已订正** ⇒ 合并时取 G2 树版本 ⇒ ⇒ **本层不代改**（写权 = G2 域）。

**④ FB-S2 ⇒ 根因 6 条定位【成立】⇒ 其中一条改变了本层的风险口径**

| 共享类 | 漂移 | 引用方 | 层级 |
|---|---|---|---|
| `GoStyleFormLimitMode` · `GoStyleFormValueLimitInterceptor` | None → `["task"]` | `task-api/…/TaskApiFormLimitReachabilityTest.java` | **TEST** |
| `GoStyleRpcBreakerInterceptor` · `GoStyleRpcSheddingInterceptor` | None → `["task"]` | `task-rpc/…/TaskProtectionChainWiringTest.java` | **TEST** |
| `GoStyleGrpcClientBreakerInterceptor` | 增 `like` | `like-api/…/LikeApiGrpcClientBreakerE2ETest.java` | **TEST** |
| **`MqConsumer`** | 增 `like` | `like-rpc/…/mq/LikeUpdateKafkaListener.java`（**`src/main`**）＋ 1 份 TEST | **MAIN ＋ TEST** |

⇒ ⇒ **6 条里 5 条是纯 `src/test` 引用；唯一的 MAIN 是 `MqConsumer`** ⇒ ⇒ **`MqConsumer` 现在是 `like-rpc` 的生产依赖** ⇒ ⇒ **本层改 `MqConsumer` 会波及 like-rpc 的生产 MQ 消费路径** ⇒ ⇒ **G2 合并后必须复跑 like-rpc 的 MQ 用例** —— 这条**本层接受**，并写进波末收口清单。

**⑤ 🔴 FB-S2「三选一」⇒ 本层的正式答复：① + ② 同批（治高估 + 治读法）· ③ 单列一轴（治漏计）且**必须标为「保守上界」**

| 候选 | 治什么 | 本层裁定 | 理由 |
|---|---|---|---|
| ① 分 MAIN / TEST 两轴记录 | **高估**（test import 被记成生产依赖）| ✅ **要做** | 与 ③ **正交**：只做 ① ⇒ 漏计仍在；只做 ③ ⇒ 高估仍在 ⇒ ⇒ **半个修法 = 守门仍然会骗人** |
| ② 快照保留层级标注 | **读法** | ✅ **与 ① 同批** | 分两轴后「task 域引用了 4 个共享类」本身变**二义** ⇒ 不带层级标注 = 读者仍会误读 ⇒ ⇒ **① 与 ② 不可拆** |
| ③ 扫描注册型依赖单列一轴 | **漏计**（`@Component` + `scanBasePackages` ⇒ 域侧 import = 0 但生产路径真的过它）| ✅ **要做**，但**定位为「上界」** | ③ 的静态实现**注定不完整**（`@Bean` 显式注册 / `@Import` / `AutoConfiguration.imports` 都会产生「域侧 import = 0」的依赖）⇒ ⇒ **宁可多报、不可漏报** ⇒ ⇒ **快照里要显式标该轴是上界而非真相** |

⇒ ⇒ **为什么 ③ 不能省**（本层的独立论证）：**注册型依赖是共享层最危险的一类** ⇒ 域侧改一行 `scanBasePackages`、或加一个 `@Import`，就能把共享层的一个 `@Component` 拉进它的**生产路径**，而**域侧生产代码对它零 import** ⇒ ⇒ **import 判定结构性看不见** ⇒ ⇒ 后果 = 共享层改这个类**可能击穿某个域的生产路径，而耦合矩阵却写「零依赖」** ⇒ ⇒ **这正是这个工具存在的意义被漏掉的那一半**。
⇒ ⇒ **支持 ① + ② 的另一条**：G2 自己提的读法修正（「task 生产代码对 4 个 form 闸门类 import = 0」字面为真、但会被读成「task 域对共享层零依赖」，而 `task-rpc` 生产路径**链级**依赖 breaker / shedding）⇒ ⇒ **快照必须能区分「import 级」与「链级（facade 级）」** ⇒ ⇒ 这正是 ① + ② 要解决的。

**⑥ 🔴 FB-S2 的归属：`tools/difftest/check-wave-coupling.py` ∈ SHARED ⇒ 本层不落码 ⇒ 规格写进本层台账 ⇒ 交合并收口执行者**
⇒ ⇒ **本层的动作 = 把修法写成可执行级规格**（判据口径 · 快照字段 · 三条要守的边界）⇒ **不是**本层改那个文件（写权不在本层）。
⇒ ⇒ **当轮实测补充（决定「修法时点」）**：本层 `8b12d68` 跑 `python3 tools/difftest/check-wave-coupling.py` ⇒ **EXIT=0 · 5 项全绿**（含「service/common 逐类辐射度与文档一致」）⇒ ⇒ **那 6 条漂移是「G2 的分支合并进来之后才会红」**（= 它自己说的「合并后必红」）⇒ ⇒ ⇒ **修法时点 = 合并收口那一刻重生成快照**，**不是现在改**（现在改 ⇒ 快照会与当前树对不上 ⇒ 反而制造一条假漂移）。

**⑦ ✅ 合并顺序硬约束：当前【已满足】** ⇒ `3b573fb` + `7488b3d` **必须同批** ⇒ 实测：**本层 `8b12d68` = 两个都在** ✔ · **G2 树 `f5c9e82` = 两个都在** ✔ ⇒ ⇒ **两棵树都齐** ⇒ ⇒ 合并时**不要只挑其一**（只挑 `3b573fb` ⇒ 池「终止即永久」⇒ 拦截器返 INTERNAL 而非 DEADLINE_EXCEEDED ⇒ 由绿转红 ⇒ **那不是域侧回归**）。

**⑧ G2 的 8 模块回归口径 ⇒ 收到，记为波末收口的输入之一**

| 项 | 值 |
|---|---|
| 数字 | **637 条 / 0 failures / 0 errors / 0 skipped** · BUILD SUCCESS · **6:47 min**（13:00:44 → 13:07:38 · 独占跑）|
| 逐模块 | message-api 98 · message-rpc 84 · message-common 21 · like-api 89 · like-rpc 113 · like-common 18 · task-api 97 · task-rpc 117 |
| 口径 | 权威 = 本轮 mtime 切的 surefire XML 求和（起跑标记 epoch `1793077244`）⇒ 与上一轮「八模块 637」一致 |

⇒ ⇒ ⚠️ **这 637 只是「G2 域当前代码态的回归网」，不是「合并共享层后」的结论** ⇒ 合并后仍需复跑（它列的三项：3 处 `DEADLINE_EXCEEDED` 断言 · 共享层 `REJECT_COMMANDS` 零域内配置的断连活体 · Redis 池守门 ⑤⑥）⇒ ⇒ **本层把它记进波末收口清单**。

**⑨ 孤儿 surefire 报告（`LikeRpcServerSpanCurrentProbeTest`：探针建在模块里、源删了报告没删 ⇒ 20 份报告 / 19 个测试类）⇒ 域侧文件本层不碰；但登记一条【共享面建议项】**
⇒ ⇒ **问题不在那一份报告，在「探针的收摊没人管」** ⇒ ⇒ 建议：**探针要么放 `/tmp`，要么连报告一起删** ⇒ ⇒ 且**需要一道守门**（否则下一轮还会出现「按报告求和」被孤儿报告污染）⇒ ⇒ **登记给合并收口执行者**（`tools/difftest/**` = SHARED）。

**⑩ 🔴 「跨树引用必须带 commit id」⇒ 本层采纳，并把它落成【一次自查动作】**
⇒ ⇒ 本层台账 **4 处**跨树引用自查 ⇒ **订正 3 处 ＋ 发现 1 处更严重的失效**：

| 失效形态 | 实证 | 订正 |
|---|---|---|
| ① **行号漂移** | G2 树 `task-api` yaml 的 `mode: "off"`：原写 `:225` ⇒ **在 `4786eb5` = `:213`、在 `f5c9e82` = `:236` ⇒ 两处都对不上** | 已改成「文件路径 ＋ 两处 commit 的实际行号」 |
| ② 🔴 **内容整段消失** | G3 树 `FavoriteApiRealTomcatGateTest` 那段「5003 是当前实现的形态」注释 ⇒ 在 G3 树当前 HEAD `7b3b27d` **已不在文件里**（现只剩 `:330` 另一条含「5003 是」的断言消息） | 已改成「⚠️ 内容已消失 ＋ 引用前必须重核内容是否还在」 |
| ③ 缺 commit | `limitCustomizer` 的「G3 树 `:206`」 | 已补 commit（`3249ef7` / `a20282f` / `7b3b27d` **三处实测同为 `:206`**）|

⇒ ⇒ **判定式（跨树引用 = 三件套）**：**行号 ＋ commit ＋ 内容摘录**，且**引用前重核「内容是否还在」** ⇒ ⇒ 只核行号不够（内容可能整段被改掉）⇒ ⇒ **行号漂移**是「你以为的那份 ≠ 系统实际吃的那份」同族，**内容消失**是它更狠的一档。

**⑪ 本层这一条** | **零落码**（只改台账 `docs/**`）⇒ ⇒ **第十四次全量口径（2480 条）仍有效** ⇒ ⇒ 本层 HEAD `8b12d68` → 本节提交。

#### 4.10.52 🟢 **G2 第二封（13:2x 旧件）⇒ 逐条处置 ＋ 🔴 合并树实测矩阵 ⇒ 产出【主干级】合并硬约束**（核对日期 2026-10-28 00:2x · 本层 HEAD `4459135` → 本节提交）

**① 这封也是旧件 ⇒ [1][2] 无新增待办**（它自己树当时在 `1b28160`，现 `f5c9e82`；引的我树 `cd893d1` 已落后 5 代）

| 它的条目 | 本层现状 | 处置 |
|---|---|---|
| [1] `cd893d1` 的「容器上限与闸门解耦」落到它域 ⇒ 残留形态翻转（更松 1 · 仍不可达）| ✅ 该改动**本层早已落码**（`cd893d1`）⇒ 残留分析已在案（§4.10.42 · A5 轴）⇒ 它域的「零行为影响」判定**本层独立复核成立**（`task-api` 零 `getParameter*` ⇒ Tomcat 惰性解析 ⇒ 上限不可达）| **无新增待办**（它已自己订正台账） |
| [2] 「FB-S1 仍成立 ⇒ `:149` 仍写死 `raw`」| ✅ **在 `cd893d1` 那一刻为真** ⇒ ⇒ **但期望表早已派生**（`d4e4745` ⇒ `:179` + `:207`）⇒ 且**权威行未变**（`GoStyleFormLimitMode.java:237` = `Mode.OFF`）| **已闭环**（回执已发：落码 commit `d4e4745` · 修法形态 = 它建议的「从 `SEMANTIC_REACHABILITY` 派生」） |

**② 🔴 [3]「内容不同但 git 不会冲突」⇒ 本层实测坐实，并升级为可复算的【合并树矩阵】**

| 合并（G4 `4459135` ← 各 lane HEAD） | 合并树 | task-api yaml 取哪一版 | 守门（真源派生版） | 定性 |
|---|---|---|---|---|
| ← **G2** `f5c9e82` | `02a04c25` | **G2 版**（`mode: "off"` @ `:236`）| **EXIT=0 绿** ✅ | **A + B 配对齐全 ⇒ 绿** |
| ← **G1** `6535ac4` | `40fe75f1` | 基线版（`mode: raw` @ `:177`）| EXIT=1 红 | **红得对**（缺 G2 的 yaml 订正）|
| ← **G3** `414f1ec` | `53ab3ae7` | 基线版（`mode: raw` @ `:177`）| EXIT=1 红 | **红得对**（缺 G2 的 yaml 订正）|

⇒ ⇒ **三个合并树全部零冲突（`merge-tree` EXIT=0）⇒ 但其中两个是红的** ⇒ ⇒ ⇒ **「零冲突」≠「判据通过」** ⇒ ⇒ **合并预演不能只报「无冲突」** ⇒ ⇒ **必须在合并树上真跑守门**（本层已做 ⇒ 可复算）。
⇒ ⇒ **为什么 yaml 静默取 G2 版**：**只有 G2 改过那个文件**（G4 的 92 笔**一笔未碰**它）⇒ ⇒ git 无冲突可报 ⇒ **直接取 G2 的版本** ⇒ ⇒ 这就是 G2 说的「**git 绿 ≠ 语义对**」的**机制级解释**。

**③ 🔴 主干级合并硬约束（本次实测产出 · 可复算）**

> **`d4e4745`（G4 的真源派生守门）必须与 `4786eb5`（G2 的 task-api yaml `off`）在同一批合进主干。**

| 只合谁 | 主干守门 | 主干 task-api 行为 | 定性 |
|---|---|---|---|
| 只合 G4 | **红**（期望 `off` / 实际 `raw`）| `raw` ⇒ 容器上限**不抬** ⇒ Tomcat 先于应用层拒绝 | 🔴 **两处都错** |
| 只合 G2 | **假红**（旧副本要求 `raw`）| `off` ✔ | 🟡 **行为对、判据错** |
| **同批合** | **绿** ✅ | `off` ✔ | ✅ **两处都对**（= 上表 G2 合并树 `02a04c25` 实测）|

⇒ ⇒ 这就是「A（判据）与 B（被测对象）必须同批」的**主干级**版本 ⇒ ⇒ **它不是「各 lane 自己树里的事」，是合并收口那一批的硬约束**。

**④ 合并清单必写的一行**（G2 提的，本层确认）

| 合并清单条目 | 理由 |
|---|---|
| `service/task/task-api/src/main/resources/application.yml` **以 G2 树版本为准**（`mode: "off"` + 订正注释）| ⚠️ **合并结果已经就是 G2 版**（实测 `02a04c25` 的 `:236` = `mode: "off"`）⇒ ⇒ 这行**不是「解决冲突」，是「记下来」** ⇒ ⇒ **防的是后续有人按「G4 树的值」把它改回去**（**G4 树里是 `raw`** ⇒ 照 G4 树改回去 = 把正确的订正改错）⇒ ⇒ 同理**注释也取 G2 版**（它那版修了一条**用错判据轴**的推理：只用了轴②「拒绝形状」、漏了轴①第二判据「可达性 ⇒ OFF」）|

**⑤ 本层这一条** | **零落码**（只改台账 `docs/**`）⇒ ⇒ **第十四次全量口径（2480 条）仍有效** ⇒ ⇒ 本层 HEAD `4459135` → 本节提交。

#### 4.10.53 🟢 **G2 的窗口 ACK（13:09~13:31）⇒ 零新增待办 ＋ 窗口纪律的第一次干净执行留档 ＋ task-api 97 条双轮一致**（核对日期 2026-10-28 00:2x · 本层 HEAD `80ebefd` → 本节提交）

**① 这封的基线已过期 ⇒ 它列的两条「STILL OPEN」按当前口径【均已闭环】**

| 它列的 | 它的基线 | 当前口径 |
|---|---|---|
| 「FB-S1：`check-tomcat-limits.py:149` 仍把 task-api 写死 `raw`」 | 我树 `cd893d1` / 它树 `a2e26df`（**均已过期**）| ✅ **已闭环**（`d4e4745`）⇒ 我树 `:179` `derive_expected_form_limit_mode()` + `:207` `EXPECTED_FORM_LIMIT_MODE = derive_expected_form_limit_mode()` ⇒ **全树对 task-api 的写死已零命中** |
| 「FB-S2：`check-wave-coupling.py` 6 条 `common_radiation` 漂移 + 三个修法选项」 | 同上 | ✅ **已答复**（§4.10.51 ⑤）⇒ **① + ② 同批（治高估 + 治读法）· ③ 单列一轴且必须标为「保守上界」**；归属 = SHARED ⇒ 规格已落本层台账 ⇒ 交合并收口执行者 |
| 「合并清单：`service/task/task-api/.../application.yml` 取 G2 树版本（语义冲突，git 拦不住）」 | 同上 | ✅ **已落台账**（§4.10.52 ④）⇒ 并已实测坐实机制（**只有 G2 改过那个文件** ⇒ git 无冲突可报 ⇒ 静默取 G2 版）|

⇒ ⇒ **零新增待办**；⇒ ⇒ **但这一条要专门记**：G2 已**连续多封**把 FB-S1 列为「STILL OPEN」⇒ ⇒ **重提的原因是【基线过期】而不是【没做】** ⇒ ⇒ ⇒ **回执必须带「基于哪个 HEAD」**（§4.10.48 · §4.10.51 ⑥）—— 缺了这个字段，对方**无法区分「没做」与「你不知道我做没做」**。

**② 🟢 窗口纪律的第一次干净执行（值得留档的正面证据）**

| 项 | 实况 |
|---|---|
| 窗口 | 本层**第 12 次**全量（13:09 → ~13:31）|
| G2 做了什么 | **只跑 module-level**：`mvn -o -B -pl service/task/task-api test` ⇒ **97 条 / 0 failures / 0 errors / 0 skipped** · BUILD SUCCESS · **36.832 s** · **真 Tomcat 跑在 `21801`**（G2 专用段 `21800–21899`）|
| 纪律遵守 | **无全量 reactor · 无 `clean verify`** ⇒ **与本层全量零重叠** ⇒ **双方都没被打断** |
| 🔴 **最值得抄的一条** | 它**起跑前自己查了** `ps -eo pid,etime,command \| grep plexus.classworlds.launcher` **＋ `lsof -p <pid> \| grep cwd`（确认是哪一棵树）** ⇒ ⇒ **这正是本层上一轮定的「起跑前自己再查一次（判据 = 树名后缀，不是计数）」被【对方】执行了** ⇒ ⇒ ⇒ **正面证据**：那条纪律**可执行、已被跨 lane 采用** |

**③ 🟢 `task-api` 97 条在两轮独立回归里一致**（窗口 ACK 那轮 `97 / 0f 0e 0s` ＋ 8 模块 637 那轮的逐模块 `task-api 97`）⇒ ⇒ ⇒ **那份 637 的逐模块数字得到【第二次独立印证】** ⇒ 波末收口输入的置信度 +1（仍**不是**「合并共享层后」的结论）。

**④ 本层这一条** | **零落码**（只改台账 `docs/**`）⇒ ⇒ **第十四次全量口径（2480 条）仍有效** ⇒ ⇒ 本层 HEAD `80ebefd` → 本节提交。

#### 4.10.54 🟢 **波末收口回归清单（本层预置 · 合并收口那一步照单执行）**（核对日期 2026-10-28 00:2x · 本层 HEAD `aa216d1`）

> **为什么预置**：波末收口是**全波唯一一次**「所有改动都在同一棵树上」的回归 ⇒ ⇒ **口径必须在跑之前就钉死**，否则跑完再补口径 ⇒ 又变成「口径跟着结果漂」。
> ⇒ 本清单 = 本层的**交班单**，也是**合并收口执行者**那一批的唯一输入（本层只共享层，不替它跑）。

**① 触发条件**：三条 lane 都把本层分支并入自己的树 ⇒ ⇒ 本层起**第 15 次**全量（编号接 §4.10.37 的第十四次）⇒ ⇒ ⚠️ **当前【未触发】** —— **G2 / G3 已并**（`37816ca` / `e18fd52`）· G1 已并 ⇒ ⇒ **只剩下面这一条未满足**：
> ~~🔴 **Docker「宿主→容器」端口转发必须恢复**（§4.10.56 ③ 实测：容器内 `pg_isready` 正常、宿主侧连接 **3s 读不到字节** ⇒ **故障仍在**）⇒ ⇒ **本层的全量回归依赖 Testcontainers 真 PG / Redis** ⇒ ⇒ **未修之前本层【不起全量】** ⇒ ⇒ **处置需 Jason / 合并收口执行者拍板**（重启 Docker Desktop 会连带干掉各 lane 跑了 30+ 小时的 `ddl-*-pg` 等容器 ⇒ **不做跨 lane 破坏性处置**）。~~
> 🟢 **【本条已于 2026-10-28 02:2x 撤销 —— 原结论是本层的探针伪影，不是真故障】**
> ⇒ ⇒ 完整订正见 **§4.10.58**（认错③）⇒ ⇒ **Docker 转发实测健康**（55/55 发布端口 TCP 可连 · 30/30 PG/Redis 容器协议应答正确）⇒ ⇒ **本条不再是第 15 次全量的前置阻塞** ⇒ ⇒ ⇒ **第 15 次全量的唯一剩余触发条件 = 三条 lane 都并入本层分支【尖端】**（§4.10.54 ① 的第一条，已满足）⇒ ⇒ ⇒ **不待 Docker 处置**。
> ⚠️ **合并方式的一句话口径**：**merge 本层分支 `align/g4-service-common` 的【尖端】**即可 ⇒ ⇒ 本层自 `d4e4745` 之后的提交**全是 `docs/**`**（台账）⇒ ⇒ **不改变任何行为** ⇒ ⇒ **不要为了追一个固定 commit 号而落后** ⇒ ⇒ 若确需固定 commit ⇒ 用**当轮最新**的（本层 HEAD 仍在前进 —— 已先后给过 `c26e462` / `8b12d68` / `80ebefd` / `aa216d1` ⇒ **固定号天然会过期** ⇒ ⇒ **以分支尖端为准**）。

**② 跑在哪**：**合并后的树**（trunk 或三树合并树）⇒ ⇒ **不是**本层树 ⇒ ⇒ **跑回归之前，先跑「在合并树上跑守门」**（§4.10.52 ② 的做法：`git merge-tree --write-tree` 出树 OID ⇒ `git archive <tree> | tar -x -C <dir>` ⇒ 在该目录跑守门）⇒ ⇒ **合并树守门红 ⇒ 先解合并约束，再跑全量**（否则白跑一轮 40 分钟）。

**③ 🔴 同批硬约束（不满足就别跑）**：`d4e4745`（G4 真源派生守门）**必须**与 `4786eb5`（G2 的 task-api yaml `off`）**同批进主干** ⇒ ⇒ 否则主干要么「守门红 + 行为也错」、要么「行为对 + 守门假红」（§4.10.52 ③）⇒ ⇒ **合并清单那行**（task-api yaml 以 G2 树版本为准）也要照抄一遍。

**④ 命令口径**：`mvn -o -B -DargLine=-Xmx2g -am clean verify` ⇒ ⇒ **域级跑必须带 `-am`**（否则吃 `~/.m2` 的旧 jar）⇒ ⇒ 用例数**按模块名逐模块求和**（**多段日志不能直接相加**）⇒ ⇒ ⚠️ **起跑前自己查一次 maven 占用**（判据 = `multiModuleProjectDirectory=` 的**树名后缀**，**不是**计数 —— 计数命令自身命令行含模式串 ⇒ 自匹配）。

**⑤ 要报的 4 项**（§5.3e）：① HEAD（**含「基于哪一次全量」**）② 逐个 `文件:行号` ③ 触及的 A 轴 ＋ Go 出处 ④ 回归口径：**跑没跑 / 未跑理由 / 模块数 / 用例数 / failures / errors / skipped / 耗时 / 三门禁**。

**⑥ 三门禁**：`check-wave-lane-ownership` · `check-wave-role-model` · `check-wave-coupling` ⇒ ⇒ **全 EXIT=0**。

> **🔴 订正（2026-10-28 03:3x · 第 15 次全量实测）** —— 上一行的「**全 EXIT=0**」这个**期望值本身要改** ⇒ ⇒ 保留原值可追：
> ⇒ ⇒ `check-wave-coupling` 在**四路合并树**上实测 **EXIT=1（10 项 `common_radiation` 漂移）** ⇒ ⇒ **且这 10 项是【既有债、非合并引入】** ⇒ ⇒ **实测证据**：三棵 lane 树各跑 ⇒ **G1 = 1 项** · **G2 = 9 项** · **G3 = 1 项**（⊂ G2 同名项）⇒ ⇒ **并集 = 10 项** ⇒ ⇒ **与合并树逐条吻合 ⇒ 合并零新增** ✅
> ⇒ ⇒ **归属**：`docs/DRIFT/_cross/cross-domain-coupling.md` = **SHARED 面** ⇒ ⇒ **本层只登记、交合并收口执行者**（工具支持 `--print`）。
> ⇒ ⇒ **另两条**（`check-wave-lane-ownership` / `check-wave-role-model`）实测 **EXIT=0** ✅
> ⇒ ⇒ ⚠️ **给合并收口执行者的一句话**：**「三门禁全 EXIT=0」这个门槛在收口那一步【达不成】** ⇒ ⇒ 要么**先 `--print` 更新快照**（把 10 项对齐到仓库现状），要么**把它降级为「不新增」**（= 并集零新增）⇒ ⇒ **别把既有债当合并引入**。

**⑦ 台账三门禁**：`check-ledger-tables` · `check-ledger-selfcheck` · `check-ledger-count` ⇒ ⇒ ⚠️ **`check-ledger-selfcheck.py` 的默认域是 `task`** ⇒ ⇒ **必须按域跑**，否则拿到的是**别人的绿**。

> **🟢 本轮按域跑（2026-10-28 03:3x）** —— `LEDGER_FILES` 只配了 **task / users / users-admin** 三个域 ⇒ ⇒ 逐域跑：**task EXIT=0** · **users EXIT=0** · **users-admin EXIT=0** ✅ ⇒ ⇒ **默认（= task）也 EXIT=0**（对照）⇒ ⇒ ⚠️ **其余 7 个域（message / like / task 之外的 article / comment / favorite / follow / hot）不在该工具的扫描集内** ⇒ ⇒ **回执里要写清这个边界**（工具口径限制 ⇒ **不是「全绿」**）⇒ ⇒ `--entity-all` 可扩到全仓，但它是**提示级、不计入退出码**。

**⑧ 基线对照表**（第十四次 = 2026-10-27 · 波末 = 待测）

| 口径 | **第十四次**（代码态 `15cf93a`+`d546f45`+`407c230`）| 波末（期望）|
|---|---|---|
| 模块数 | **52**（Reactor）· **29** 个有测试 | ? |
| 用例数 | **2480**（推算 **2487** ⇒ ⚠️ **推算、未跑全量**）| **实测**（逐模块求和）|
| failures | **1**（**预期内** = `favorite-api` 的**旧**断言 5003）| **期望 0**（G3 域侧已把该断言改成 Go 形态 ⇒ 须实测确认）|
| errors | **0** | ? |
| skipped | **4** | ? |
| 耗时 | **41:32 min**（主跑 19:12 + 补跑 3:13 + 其余）| ? |
| 三门禁 | **0-0-0** | 0-0-0 |

**⑨ 各 lane 已报的域级网**（合并前的口径 ⇒ ⚠️ **不是**「合并共享层后」的结论 ⇒ 只是**规模参考**）

| lane | 数字 | 出处 / 状态 |
|---|---|---|
| G1 | 域级全量 **18/18 BUILD SUCCESS** · **688 条** | `13f6a93` · 已报 |
| G2 | 8 模块 **637 条** / 0f 0e 0s / **6:47 min**（`task-api` **97 条两轮独立一致**）| `f5c9e82` · 已报 |
| G3 | — | 🔴 **未报** |
| **G4 共享层** | `service/common` **331** · `users-common` **39** | 本层实测（`service/common` 331 = 330 + §4.10.46 的结构锁 ④）|

**⑩ 本层共享层落码清单** ⇒ 见本文件 §4.10.31~§4.10.46 的落码表（本层**零未验证的可执行改动** ⇒ ⇒ **共享层侧不需要额外验收项** ⇒ 波末只需确认**合并树里共享层那份与本层一致**即可）。

**⑪ 本层这一条** | **零落码**（只改台账 `docs/**`）⇒ ⇒ 本层 HEAD `aa216d1` → 本节提交。

**① ✅ G1 的 ① 收到**（它从本层台账原文核过，不是听转述）⇒ 第八次全量口径 **52 模块 / 29 有测试 / 2461 条 / 0f 0e 4s / 20:21 min / EXIT=0** ＋ 双侧探针 `run-parity.sh` **56 例 EXIT=0** ＋ `run-rpc-timeout-parity.sh` **3 例 EXIT=0** ⇒ 与本层回执一致 ✔

**② 🔴 G1 的 ② 根因【成立】⇒ 本层独立复现（三组证据）**

| 证据 | 本层实测（核对日期 2026-10-28 00:3x）|
|---|---|
| 它的 7 笔提交是否在本层树 | `fa608f3` / `cbf8af8` / `0c2a902` / `b7d724c` / `af0a532` / `ce0c339` / `e8b428f` ⇒ **7/7 `NOT_IN_G4`** ✔ |
| 本层树 vs 主干 | `git rev-list --left-right --count do-my-own-gh...HEAD` ⇒ **落后 0 / 领先 94** ⇒ ⇒ **本层树 = 主干 ＋ 本层 94 笔 ⇒ 不含任何 lane 的工作** |
| G1 域 6 组件的**测试类数** | 本层树 **18 / 17 / 13 / 20 / 3 / 10** ⇒ **与它报的「旧基线」列逐项一致**；它树的新值 = **22 / 21 / 17 / 22 / 8 / 11** ⇒ ⇒ **+12 个测试类、−62 条用例** 双方互证 |

⇒ ⇒ **本层的全量数字对 G1 域【不构成证据】** —— 它证明的是**主干态**的那些组件。

**③ 🔴 ③ 的归属裁定 = 【写进本层台账】（不转合并收口执行者）**

**理由**：这条结论的**主体是「共享层的证据边界」**（本层的全量数字能证明什么 / 不能证明什么）⇒ ⇒ **属本层写权面**；它是一条**口径约束**，**不是**共享索引内容 ⇒ ⇒ 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` 仍归合并收口执行者。

**⇒ 采纳 G1 的 C7 三条判据（写进本层）**：

| # | C7 判据（可机械核）|
|---|---|
| ① | **收口回归的代码态必须包含所有 lane 的提交** ⇒ `git rev-list --left-right --count do-my-own-gh...HEAD` ⇒ **落后数 = 0** ＋ **逐 lane 核 tip 是祖先** |
| ② | **不得引用任何 lane 的 lane 级全量数字当作本域证据** ⇒ 只能当「**共享层那半**」的证据 |
| ③ | **本域数字只有两个来源可采信**：① 本域树上的本域回归 ② 所有 lane 合入后的收口回归（C7）⇒ ⇒ **两者之间的任何数都不作数** |

⇒ ⇒ **交叉登记**：G1 域台账 `docs/DRIFT/users/vs-common.md` **§13.3**（C7 的 G1 侧版本）⇒ 本层 §4.10.54 ⑧ 的基线对照表**加一行「适用范围」**（见下）。
⇒ ⇒ **并据此订正本层自己**：本层第十四次全量（**2480 条**）⇒ **对 G1 / G2 / G3 三个域【不构成证据】** ⇒ 它只证明 **① 共享层本身 ② 主干态的那三个域** ⇒ ⇒ ⇒ **本层台账里所有引用「2480 条」的地方，适用范围 = 共享层 ＋ 主干态**（§4.10.50 §4.10.51 §4.10.52 §4.10.53 的「口径仍有效」一律按此读）。

**④ ✅ ④ 窗口承诺可解除**：第十二次全量**早已收工**（本层已到**第十四次**）⇒ ⇒ **下一轮 = 第十五次（波末收口）**，按 §4.10.54 的 11 项清单 ⇒ ⇒ **G1 的「窗口期零 maven」承诺到此结束**。
⇒ ⇒ 🟢 **另采 G1 那条**：「**跨 lane 的窗口判断一律由动作方自己复检**」⇒ 判据 = **树名后缀**（`ps -eo command | grep "[p]lexus.classworlds" | sed 's/.*multiModuleProjectDirectory=//; s/ org.codehaus.*//'`）⇒ ⇒ **不是 `grep -c` 计数**（计数命令自身命令行含模式串 ⇒ 自匹配）⇒ 本层早已如此（§4.10.50 ④ 记着「**等待器报窗口空 ≠ 窗口真空**」的竞态）。

**⑤ 🔴 ⑤ 的前提【已过期】⇒ 那条前置【已满足】**（实测 G1 树 `13f6a93`，核对日期 2026-10-28 00:3x）

| 它说的（旧树 `fa608f3`）| **当前实测（`13f6a93`）** |
|---|---|
| `AutoConfiguration.imports` 只有 2 条、无 Redis 那条 | **6 条**，含 `com.rag.common.redis.GoStyleRedisClientAutoConfiguration`（第 5 行）✔ |
| 5 份域内副本还在 ⇒ 删了就 `REJECT_COMMANDS` 全消失 | **已删**（`312a09b`）⇒ 域侧 `src/main` 对 `RedisDisconnectedBehaviorConfig` 的**唯一命中 = 共享层自己那份** ✔ |
| — | `1fb26b2` · `312a09b` · `8376cbb` · `d4e4745` **全部在树内** ✔ |

⇒ ⇒ **它不必再等「本层分支进 `do-my-own-gh`」** ⇒ ⇒ **验收标准它自己写对了**（「**不能用「没红」当通过**」—— `@ConditionalOnMissingBean` 是**遮蔽**不是叠加 ⇒ 遮蔽状态下也是绿的）⇒ ⇒ **且【不必它自己再造机器网】—— 现有三道已够**：

| # | 机器网 | 位置 | 判什么 |
|---|---|---|---|
| ① | 共享层 customizer 唯一性 | `service/common/src/test/.../redis/GoStyleRedisClientOptionsGateTest.java`（3 条：customizer **有且只有一份** ＋ 名字 `goStyleRedisClientOptionsCustomizer`）| 共享层那份**真的被装配**且**只有一份** |
| ② | 不得新建域内副本 | `service/common/src/test/.../redis/GoStyleRedisClientShadowingGateTest.java` | 域侧**零副本**（有副本 ⇒ 遮蔽）|
| ③ | 域侧 FastFail 守门的 bean 身份断言 | `users/admin/*` ＋ `follow-api` 等 5 份 | 域侧容器里**装的是共享层那份** |

⇒ ⇒ **「没红」不能当通过** ⇒ ⇒ **但「这三道网变红」可以当【不通过】** ⇒ ⇒ ⇒ **验收 = 这三道网必须绿 ＋ 共享层那份的 `REJECT_COMMANDS` 确实落在客户端 `ClientOptions` 上**。

**⑥ 🔴 G1 后续三封带来的四条 —— 其中两条是本层要认的错**

**⑥-1 ✅ G1 的 #6 第 1 步【取消】⇒ 本层零动作**
⇒ 它用逐层证据链推翻了原修法（V-002）：Go `service/user/user/rpc/internal/model/data_model.go:11` ⇒ `Status int64 \`gorm:"column:status;default:0"\`` ⇒ **值类型 · 非指针** ⇒ **GORM 对值类型写库恒写 0（不会写 NULL）** ⇒ ⇒ **Go 侧该列永不出现 NULL** ⇒ ⇒ 「**DDL 声明差异 · 行为零差异**」⇒ ⇒ **维持 RAG NOT NULL = 零成本零风险**。
⇒ ⇒ **本层确认收到 ⇒ 不落 DDL、不为此跑全量** ⇒ ⇒ ⚠️ **落 DDL 的方向正好相反**：RAG 侧 `callSettersOnNulls=false` ⇒ NULL 时跳过 setter ⇒ 读出 `null` ≠ Go 的 `0` ⇒ **制造一条方向相反的新漂移** ⇒ ⇒ ⇒ **剩下的唯一落点 = 销号签字**（`docs/Intentional_Deviations/**` ⇒ **不在 G1、也不在 G4 写权面** ⇒ **交合并收口执行者**）⇒ ⇒ **本层不代签、只登记**。

**⑥-2 🔴 本层认一条错：对外知会把一次 `-pl` 限定的定向跑【称作「全量」】**
⇒ G1 实测：13:0x 那条知会对应的命令是 `mvn -o -B -fae -pl <5 个模块> -am verify` ⇒ ⇒ **覆盖面只有那 5 个模块 ＋ 依赖** ⇒ ⇒ **不是全仓全量** ⇒ ⇒ ⇒ **我的错**：对外知会写「现在起第九次全量（`mvn -B clean verify`，约 20 分钟）」⇒ ⇒ **编号与性质双错**（那是**重跑第九次那一轮**，且**不是全量**）。
⇒ ⇒ **精确边界（勿扩大）**：**§4.10.26 记录的【第十二次】覆盖面是全的**（主跑 46 ＋ 补跑 6 = **52 模块**，两段拼起来，已如实写明）⇒ ⇒ **G1 13:35 实测到的那条 `-pl` 5 个 = 正是我的【补跑 ②】**（`comment-api` / `comment-rpc` / `comment-common` / `comment-proto` / `favorite-rpc` —— 与 §4.10.26 的补跑命令**逐字一致**）⇒ ⇒ ⇒ **「第十二次不是全量」这个结论【不成立】**（它是把补跑段当成了整轮）⇒ ⇒ ⇒ **本层该认的只有 13:0x 那条对外知会**。
⇒ ⇒ **由此立的纪律**（与 G1 同款）：**「`ps` 只能证明『这棵树在跑 maven』⇒ 不能证明『在跑全量』」** ⇒ ⇒ **模块级与全量级在 `ps` 里长得一样** ⇒ ⇒ ⇒ **凡说「在跑全量」，必须同时给出 `-pl` 的覆盖面**（或明写「全仓 reactor · 52 模块」）⇒ ⇒ **本层的「第十二次全量」记法（两段拼起来 ＋ 写清各段覆盖面）就是按这条来的**。

**⑥-3 🟢 槽位两处（6 vs 7）与「+7 = 3s 预算起点」：G1 侧独立复核成立**
⇒ 「我树 6 个 Filter / G4 树 7 个」= **代码态差**（`9690b04` 引入的 `GoStylePathNormalizingFilter` 不在主干 ⇒ G1 树里没有）⇒ ⇒ **不是 G1 漏数** ✔；⇒ ⇒ `begin`/`clear` 都在 `HttpMiddlewareFilter` 链内 ⇒ ⇒ 排在它之后的 Filter 都落进 3s 预算 ⇒ ⇒ **G1 的推导成立** ✔；⇒ ⇒ **MaxBytes(+8) 唯一性**：两侧都成立 ⇒ ⇒ **签字比改码便宜**（挪它要**同一次提交**改：`@Order` ＋ 本层槽位快照守门的快照 ＋ 台账登记理由 ⇒ 否则守门必红）⇒ ⇒ **签字档归合并收口执行者 ⇒ 本层与 G1 都不代签、只登记**；⇒ ⇒ **G1 的域侧守门与本层的共享层槽位快照守门互补、不重复 ⇒ 两边都留着**。

**⑥-4 🟢 机制教训（G1 自纠的那条，本层同款 ⇒ 写成硬纪律）**
⇒ 「**改了结论必须主动通知，不能指望对方去读文件**」⇒ ⇒ 对方树上的文件是**旧的** ⇒ ⇒ ⇒ G1 那封信的机制说明：它的树里 `docs/DRIFT/users/vs-common.md` 连「V-002」都**零命中** ⇒ ⇒ 它读到的不是 G1 台账，是整合者 sweep 那一版 ⇒ ⇒ ⇒ **⇒ 本层的对应义务**：凡本层对外发过「等某一步」的承诺，而那一步后来被推翻 ⇒ **必须【主动】发一条把它关掉**，不能指望对方轮询文件。

**⑦ 🟡 三路 maven 并发（13:35 实测 · 与历史 `EXIT=137` 同形态）**

| 树 | 命令 | `-pl` 覆盖面 |
|---|---|---|
| G3 | `-B --fail-at-end -am test` | **12 个模块**（article ×3 ＋ comment ×3 ＋ favorite ×3 ＋ hot ×3）|
| **G4** | `-o -B -fae -am verify` | **5 个模块**（= 本层的**补跑 ②**）|
| G2 | `-o -B test` | **9 个模块**（`common` ＋ message ×3 ＋ like ×3 ＋ task ×2）|

⇒ ⇒ 三条同时在跑（**19 个 java 进程** · 24 GB 内存 ⇒ 默认堆 6 GB/条 ⇒ 合计 18 GB ⇒ load 11.76）⇒ ⇒ **EXIT=137 风险是真的**；⇒ ⇒ **处置 = 不叫停任何一条**（杀了就丢工作）＋ **不叠第四条**；⇒ ⇒ **G1 建议「后续每条显式加 `-DargLine=-Xmx2g`」⇒ 本层采纳**（本层历次全量都用 `-DargLine=-Xmx2g`）⇒ ⇒ **本层当前不起全量 ⇒ 这条并发风险对【本层】当下为 0**。

**⑧ 本层这一条** | **零落码**（只改台账 `docs/**`）⇒ ⇒ **本节与 §4.10.56 一并提交**（在 `766e7dc` 之上）。

#### 4.10.56 🟢 **G2 / G3 的「合并完成」回执 ⇒ 三条实测结论 ＋ 🔴 一个波末收口的前置阻塞 ＋ 🔴 一个跨树数字不可复算的实证**（核对日期 2026-10-28 01:0x~01:4x · 本层 HEAD `766e7dc` → 本节提交）

> **背景**：G2（`37816ca`）与 G3（`e18fd52`）**都已把本层分支合进自己的树**（起始 `cd893d1` ⇒ 本层自那以后的提交全是 `docs/**`）⇒ ⇒ **三条 lane 全部已持有本层的代码** ⇒ ⇒ **只差「合本层的 docs 尖端」**（零行为影响）。

**① ✅ G2 的合并后回归（9 模块 reactor · 实测）**

| 项 | 值 |
|---|---|
| reactor | `service/common` ＋ G2 的 8 个域模块 = **9 个** · 13:31:32 → 13:39:12 · **7:39 min** · `MVN_EXIT=0` · BUILD SUCCESS |
| 逐模块 | **common 324** · message-api 98 · message-rpc 89 · message-common 21 · like-api 89 · like-rpc 115 · like-common 18 · task-api 97 · task-rpc 119 ⇒ **合计 970 / 0f / 0e / 0s** |
| 域侧净增 | 合并前 637 ⇒ 合并后 **646**（+9 = task +2 · like +2 · message +5 ⇒ 与新增测试**逐条对得上**）|
| 🟢 **值得记的一条** | 它**拒绝**把共享层 jar `install` 进 `~/.m2`（那会改变我正在跑的回归所解析的产物）⇒ ⇒ 改为把 `service/common` **放进 reactor** ⇒ ⇒ **「域级回归要带 `-am`」这条纪律的一个反例佐证**：共享层改动必须**随 reactor 一起编译**，不能吃 m2 里的旧 jar |
| 🟢 **变异验证的一条坑** | 变异后期望列表里**出现两个相同的 `containsExactly("recover","breaker")`** ⇒ ⇒ **naive 的全局还原脚本会 assert 中止 ⇒ 文件留在变异态** ⇒ ⇒ **还原必须位置精确**（改第一个 + 校验文件只剩一个完整字面量）|
| 🟢 **它自认的一条** | 窗口占用检查后面跟了一句**写死的 `(empty = safe to start)`** ⇒ 它**误读了那行** ⇒ ⇒ **这条已被它自己改成「占用方清单后面不许有固定判词」** ⇒ ⇒ **可复用：占用检查的输出后面**不要**跟任何固定结论行**（⇒ 读者会把固定行当成结论）|

**② 🔴 G3 的根因订正 ⇒ 【是本层 `cd893d1` 带来的正向闭合】**

| 项 | G3 的订正（实测） |
|---|---|
| 它先前写的 | 「CPU 自适应降载 ⇒ `GoStyleCircuitOpenException` ⇒ 1015」⇒ ⇒ **被它自己证伪**：降载命中时 `GoStyleCpuSheddingFilter#doFilter` 走 `response.setStatus(503)` 并**直接 return** ⇒ ⇒ **裸 503、无 body、无信封 ⇒ 产不出 1015** |
| 1015 的真来源 | **favorite 域自己的业务层**：`invokeRpc` catch ⇒ `FavoriteGrpcError#bizCodeFromError` ⇒ 普通 error ⇒ 1015（Go `favorite/common/errmsg.go:113-116` 的 `status.FromError` ok=false ⇒ `CodeServerBusy=1015` ⇒ **逐字同形**；`:135-136` 的 Unavailable 落 default ⇒ 同为 1015）|
| ⇒ ⇒ **真根因 = 本层 `cd893d1`** | 容器上限抬到 10001 ⇒ 2049 个值不再被 Tomcat 拒 ⇒ **进业务层** ⇒ favorite-rpc 不可用 ⇒ 1015 ⇒ ⇒ **与 Go 同形**（Go：放行 ⇒ 进业务层 ⇒ 同一出口）⇒ ⇒ ⇒ **不是回归 ⇒ 是旧漂移 `DRIFT-HOTAPI-030` 那一格闭合** |
| ⇒ ⇒ **本层的记法** | 🔴 **共享层改动第一次有【域可见的正收益】**：本层「容器上限无条件抬到 10001」⇒ ⇒ **favorite 域从「RAG 特有行为（5003）」回到「Go 同形（1015）」** ⇒ ⇒ ⇒ **这正是 AGENTS.md §3.1「行为以 Go 为准」的判据** ⇒ ⇒ **本层的 `cd893d1` 由「有争议的严格化」升级为「对齐 Go 的解耦」**（A3 轴 · 跨域暴露面）|
| 🟢 域侧动作 | 断言从「**必须**含 `code:5003`」改为 **Go 语义**（① HTTP 200 ② 不含 5003 ③ **含 1015**（证明真的进业务层）④ 非 Tomcat HTML）⇒ 守门 **8/8 绿**；**方法名故意保留旧名**（不打断本层 §4.10.25.4 的引用）|
| 🟢 变异层 | 本层的改动让**两条旧变异当场哑了**（MUT-A：容器不再抛「参数过多」⇒ 归一永不触发；MUT-C：yaml 不再决定运行期值）⇒ ⇒ 新增 **MUT-F**（域内判据「数非空值」→「数总值」）**1 例精确变红** ⇒ ⇒ **坑档 `PIT-FAV-API-010`**（与 `PIT-FAV-API-009` 同族：009 = 断言绑错层 ⇒ 假红；010 = **变异**绑错层 ⇒ **断言无人守护**）|

**③ 🔴🔴 机器级阻塞：Docker「宿主→容器」端口转发对多数容器失效 ⇒ 【波末收口回归的前置阻塞】**

> 🟢🟢 **【本节已被本层自己推翻 —— 2026-10-28 02:2x · 完整订正见 §4.10.58（认错③）】**
> ⇒ ⇒ **原结论是本层的【探针伪影】，不是真故障** ⇒ ⇒ **下方原值保留**（保留错误现场才能追溯「这个错是怎么产生的」）⇒ ⇒ **但请勿再据此行动**。
> ⇒ ⇒ **真相**：宿主侧**连得上**（TCP 握手成功）⇒ ⇒ **超时发生在「读」上，不是「连」上** ⇒ ⇒ 而 PG **不会回复垃圾字节**（我发的是单字节 `\x00` ⇒ PG 等的是一个合法启动包 ⇒ 永远不回）⇒ ⇒ **「读超时」被本层误读成「连不上」**。
> ⇒ ⇒ **实测终态（核对日期 2026-10-28 02:2x）**：**55/55** 已发布容器端口 **TCP 可连**（0 连不上）· **30/30** PG / Redis 容器 **协议应答正确**（PG 启动包 ⇒ `b'N'`；Redis `PING` ⇒ `b'+PONG\r\n'`）⇒ ⇒ ⇒ **Docker 转发健康** ⇒ ⇒ ⇒ **本节不再是第 15 次全量的前置阻塞** ⇒ ⇒ ⇒ **§4.10.54 ① 的那条触发条件已同步撤销**。
> ⇒ ⇒ **连带撤下的处置建议**：~~**重启 Docker Desktop**~~ ⇒ ⇒ **不需要**（那会白杀各 lane 30+ 小时的 `ddl-*-pg` ⇒ 幸好没做）。

| 判据 | 本层当轮实测（核对日期 2026-10-28 01:4x）|
|---|---|
| 容器侧 | `docker exec difftest5k-postgres pg_isready -h 127.0.0.1 -p 5432` ⇒ **`accepting connections`** ✔ |
| 宿主侧 | `socket.create_connection(("127.0.0.1", 60432), timeout=3)` ⇒ **TimeoutError（连上但 3s 读不到任何字节）** ⇒ ⇒ **与 G3 的报告一致 ⇒ 故障仍在** ⛔ **（原判读）** ⇒ ⇒ **实为「读超时 ≠ 连不上」⇒ 本行判读错了** |
| 判读 | ⇒ ⇒ **容器内服务正常 ⇒ 是转发路径坏了 ⇒ 与代码无关** ⛔ **（原判读，2026-10-28 02:2x 推翻：转发没坏 ⇒ 是探针发的不是协议包）** |
| ⇒ ⇒ **对本层的影响** | 🔴 **本层的全量回归依赖 Testcontainers 真 PG / Redis** ⇒ ⇒ **在它修好之前，本层【不起全量】**（§4.10.54 的触发条件里要**加这一条**）⇒ ⇒ **处置需 Jason / 合并收口执行者拍板**（重启 Docker Desktop 会连带干掉各 lane 跑了 30+ 小时的 `ddl-*-pg` 等容器 ⇒ **不做跨 lane 破坏性处置**）⛔ **（2026-10-28 02:2x 全部撤销 ⇒ 无需任何处置）** |

**④ 🔴 「324 vs 331」不可复算 ⇒ 一条跨项目可复用的口径教训**

| 项 | 实测 |
|---|---|
| G2 合并后回归报 | `service/common` = **324** |
| 本层实测 | `service/common` = **331**（§4.10.46 结构锁 ④ 后）⇒ ⇒ **差 7** |
| 测试**类**清单 | **63 = 63 ⇒ 差集为空** ⇒ ⇒ **不是「少了几类测试」** |
| 逐文件 `@Test` 数 | **只有 1 处不同**：`GoStyleParamLimitOffModeGateTest`（我 4 / G2 3 = 我的结构锁 ④ · `9dd67ab` ⇒ G2 树不含它）⇒ ⇒ **逐文件差 = 1 ≠ 条数差 7** |
| 产例注解普查 | `@ParameterizedTest` / `@RepeatedTest` / `@TestFactory` / `@TestTemplate` **全 0** ⇒ ⇒ **排除「参数化」解释** |
| 🟢 **根因（本层查到了）** | 本树 `@Test` 注解 **334** 个，但**实测只跑 331** ⇒ ⇒ **3 条「有注解但没跑」**；而 G2 树 333 个注解、实跑 324 ⇒ ⇒ **9 条没跑** ⇒ ⇒ ⇒ **「未跑数」之差 9 − 3 = 6 ⇒ 恰好补上缺口** ⇒ ⇒ ⇒ ⇒ **差 7 = 1（我的结构锁）+ 6（两边「未跑数」不同）** |
| ⇒ ⇒ **教训** | 🔴 **「`@Test` 注解数」≠「实跑用例数」** ⇒ ⇒ **两边都存在这个差额** ⇒ ⇒ ⇒ **拿「注解数 / 类数」去对账「用例数」，必错** ⇒ ⇒ ⇒ **归因只能靠实测（surefire 报告）** ⇒ ⇒ ⇒ 且 **「差 X 条」的归因必须先确认两边用的是同一个「计用例」口径** |

**⑤ 两条待 G3 裁的，我给口径**（它明确问「可以吗」，本层答）

| # | 它的问 | 本层的答 |
|---|---|---|
| ① | **排序桥副本（L-7）**：article-rpc / favorite-rpc 域内各还有 1 份副本，且各自注册在域内 `AutoConfiguration.imports` ⇒ 「域内副本由我删可以吗？」| ⚠️ **它的前提已过期** ⇒ **本层实测它当前树 `11f96dd`**：域侧副本**只剩 6 份**（`like-rpc` · `follow-rpc` · `message-rpc` · `admin-rpc` · `user-rpc` · `task-rpc`）⇒ ⇒ **article-rpc / favorite-rpc 那两份【它自己已删】**（`a20282f`）⇒ ⇒ ⇒ **本层授权不变**：**域内副本可删**（域侧那份用类字面量 `after = DataSourceAutoConfiguration.class` ⇒ 会让无 MP 的消费者反射失败 ⇒ 本层那份用**字符串** `afterName`）⇒ ⇒ **但它只能删自己域的那 2 份** ⇒ ⇒ **其余 5 份属 G1 / G2 的域** ⇒ ⇒ **本层不代它们授权 ⇒ 由对应 lane 自删** |
| ② | **T2 双侧活体用例**：`tools/api-envelope-probe/<域>/**` **不在任何 lane 的 ACTIVE 白名单** ⇒ 归属待裁定 | ⇒ ⇒ **技术口径 = 加进对应 lane 的 ACTIVE 白名单**（该目录是**按域**的探针 ⇒ 天然属该域）⇒ ⇒ **落点是 `tools/difftest/check-wave-lane-ownership.py` = SHARED ⇒ 本层不能改** ⇒ ⇒ **交合并收口执行者**（§1e）⇒ ⇒ **在它落码前，该域的 T2 一格只到 L3**（真容器活体 200 + 1015）⇒ **L4 缺** ⇒ 不得读成已闭合 |

**⑥ 本层这一条** | **零落码**（只改台账 `docs/**`）⇒ ⇒ 本层 HEAD `766e7dc` → 本节提交。

#### 4.10.57 🟢 **G2 报的「层 4 精确定位通道是死代码」⇒ 本层落码修掉（`9037a2b`）**

> **为什么这一条落到本层**：缺陷在 `tools/check-ledger-selfcheck.py` ⇒ **不在** `check-wave-lane-ownership.py` 的 SHARED 13 文件清单里 ⇒ ⇒ **无人拥有面** ⇒ ⇒ 按既有先例（§4.10.16 ③ · `139b428`）**本层自修**（纯 python 守门 · 零业务影响）⇒ ⇒ **不是越权**。
> ⚠️ **G2 的报法是对的**：它给的是**探针输出**（可复现的观测）而不是「我觉得这里不对」⇒ ⇒ 本层**逐字复现**后才落码。

**① 现象（G2 报 · 本层逐字复现）**

| 项 | 内容 |
|---|---|
| 现象 | 台账里以「带版本前缀」写的 Go 引用（`go-zero@v1.10.2/rest/httpx/util.go:15`）⇒ **层 4 的「精确定位通道」定位不到** ⇒ ⇒ 只能退回 **basename 猜** ⇒ ⇒ 跨库同名歧义（`config.go` 14 处 · `unmarshaler.go` 3 处 …）|
| 本层复核 | 逐字复现 G2 探针（`/tmp/g2-probe-precise-channel.py`）输出 ⇒ **与 G2 报的一致** ⇒ ⇒ 不是误报 |

**② 根因 = 两处契约不一致（不是一处）**

| # | 契约 | 现状 | 后果 |
|---|---|---|---|
| ① | **提取器 ↔ 解析器** | `REF_RE`（原 `:208`）字符类 `[A-Za-z0-9_./-]` **不含 `@`** | `go-zero@v1.10.2/…` 在 `@` 处**被截断** ⇒ 提成 `v1.10.2/rest/httpx/util.go` ⇒ `_direct_module_candidate` 返回 `None` ⇒ ⇒ **通道从台账文本永远进不去 ⇒ 是死代码** |
| ② | **解析器 ↔ 台账既有写法** | `_direct_module_candidate` 的**全式**正则要求模块段**含 `.`** | **短式**（台账里的既有写法，如 `docs/DRIFT/global/common-tails.md:51`）**也解析不到** ⇒ ⇒ 「带版本前缀 ⇒ 路径唯一 ⇒ 消除同名歧义」这条**设计意图兑现不了** |

⚠️ **两条合起来才解释「为什么它一直没被发现」**：① 使短式**进不去**（提取阶段就断）② 使短式**解析不到**（解析阶段也断）⇒ ⇒ **双重失效** ⇒ ⇒ 而**原来的自测是「假绿」**（见 ③）。

**③ 🔴 原自测「假绿」的机制（这一条比缺陷本身更值得记）**

- 原自测**直调** `_direct_module_candidate` ⇒ ⇒ **绕过 `REF_RE`** ⇒ ⇒ **提取器与解析器的契约不一致，它看不见**
- ⇒ ⇒ **自测过、真跑不通** ⇒ ⇒ 🔴 **纪律：凡涉及「提取器 ↔ 解析器」这类【成对契约】，自测必须走端到端链路**（台账文本 → 提取 → 解析 → 真命中）⇒ ⇒ 否则自测只证明「解析器在被正确调用时是对的」
- ⇒ ⇒ 本层据此把自测改成**端到端**（`tools/check-ledger-selfcheck.py:1996` ②⁚）并**补一条「同名多模块 ⇒ `None`」**（`:2023` ②⁛ · 守住「不猜」的消歧语义）

**④ 修法（三处 · 提交 `9037a2b`）**

| # | 落点 | 做法 |
|---|---|---|
| ① | `tools/check-ledger-selfcheck.py:215` | `REF_RE` 字符类**补 `@`**（其余一字未改）|
| ② | `:1059` / `:1062` `_module_dirs_index(gomodcache)` | 新增 module cache 里 `<name>@<ver>` 目录索引（**懒建 + 进程内缓存 + 只扫深度 ≤ 3** ⇒ 不拖慢门禁）|
| ③ | `:1100` / `:1130` `_direct_module_candidate` | 增**短式后缀唯一匹配**：`<name>@<ver>/<rest>` 在 module cache 里是**唯一路径后缀** ⇒ **同样精确** ⇒ ⇒ **多命中返回 `None`（不猜）** ⇒ ⇒ 保住消歧语义 |

⇒ ⇒ 🟢 **关键结论：台账写法【不必改】** —— 短式**可以做到同样精确** ⇒ ⇒ **跨域一致性优先** ⇒ ⇒ **不改既有台账文本**（改写法 = 让 10 个域的台账跟着动 = 凭空造合并冲突面）。

**⑤ 验证（六道）**

| 判据 | 结果 |
|---|---|
| 改后自测 | **EXIT=0** · 新增 3 条断言全绿 |
| **内置 `--mutation-test` 套件** | **EXIT=0 · 18 项全部精确命中**（含「阴性对照：不报」⇒ 判据不是恒红）|
| **改前/改后完整输出 diff** | **只差这 3 行** ⇒ 当前默认域（task 的 6 份台账）**零计数影响** ⇒ ⇒ ⇒ 这是**潜伏死代码修复**，**不是行为变更**（⇒ 第十四次全量口径 **2480 条**仍有效）|
| **变异 M1**（删 `REF_RE` 的 `@`）| **2 条红** —— 红在「REF_RE 提取不出带 `@版本` 的完整路径（提取到 `'v1.10.2/rest/httpx/util.go'`）」（**逐字复现 G2 探针输出**）**+** 「短式端到端定位失败 ⇒ 精确定位通道是死代码」⇒ **EXIT=1** |
| **变异 M2**（短式不查唯一性 ⇒ 乱挑一个）| **1 条红** —— 红在「同名多模块时乱挑了一个 ⇒ 消歧语义被破坏」⇒ **EXIT=1** |
| **还原校验** | 变异前后 `shasum -a 256` **逐字节一致**（`b31a752129b65b259a57fd7137324041c3a79c32b28a0926443e0f680e6454a9`）⇒ 还原后 **EXIT=0** ⇒ ⇒ **变异本身没有留下残留** |
| **写权守门** | `check-wave-lane-ownership.py` ⇒ **EXIT=0（PASS）**；`check-wave-role-model.py` ⇒ **EXIT=0** |
| **U+FFFD** | 字节级扫 `b'\xef\xbf\xbd'` ⇒ **0**（157 931 bytes）|
| **业务影响** | **零** —— 本轮改动**只在 `tools/`** ⇒ `service/**` 未动 ⇒ ⇒ **本层 HEAD `a38ad75` → `9037a2b`（98 笔）** |

**⑥ 🟢 本层这一条** | **本轮首次有「守门工具」类落码**（此前本层的落码全在 `service/**`）⇒ ⇒ ⇒ **性质 = 潜伏死代码修复** ⇒ ⇒ **不改任何门禁的判据口径** ⇒ ⇒ **不改台账文本** ⇒ ⇒ ⇒ **零行为影响 · 零计数影响**。

#### 4.10.58 🔴🟢 **认错③：本层上一轮上报的「Docker 端口转发故障」= 探针伪影，不是真故障 ⇒ 当轮推翻 + 撤销一切处置建议**（核对日期 2026-10-28 02:2x）

> ⚠️ **这是本层第三次认错，也是后果最重的一次** —— 前两条是**编号/性质**错，这一条是**上报了一条不存在的机器级阻塞**，并据此**提议过破坏性处置** ⇒ ⇒ **本节原样保留错误现场**（§4.10.56 ③ 与 §4.10.54 ① 均已加订正注记）⇒ ⇒ **保留是为了让下一个 agent 看到「这个错是怎么产生的」**。

**① 错在哪**

| 项 | 内容 |
|---|---|
| 本层的探针 | `s=create_connection(('127.0.0.1',60432),timeout=3)` → `s.settimeout(3)` → `s.sendall(b'\x00')` → `s.recv(16)` |
| 🔴 **错点** | **TimeoutError 实际发生在 `recv` 上** —— `create_connection` **早就成功了** ⇒ ⇒ **「连上了」** ⇒ ⇒ 本层把**「读超时」当成了「连不上」** |
| ⇒ ⇒ **真根因** | **PG 不会回复垃圾字节** —— 它等的是一个合法的 **SSLRequest / StartupMessage** ⇒ ⇒ 单字节 `\x00` ⇒ **永远不回** ⇒ ⇒ ⇒ **「3s 读不到字节」是【协议正确行为】，不是故障** |
| ⇒ ⇒ **一句话** | 本层**用「问错了问题」的方式，去断言「服务坏了」** |

**② 本轮实测链（5 步，每步都独立取证据）**

| 步 | 动作 | 结果 |
|---|---|---|
| ① | 复测原探针（12 个已发布端口）| 12/12 **「FAIL TimeoutError」** ⇒ ⇒ 与上轮一致 ⇒ ⇒ **但此时这个信号已是「已知可疑」** |
| ② | **找对照证据** | **G2 同一批**的 JDBC 探针**成功**连上 `127.0.0.1:22769` / `22799`（`PROBE fixed-port jdbc ok` / `mapped=22799`）⇒ ⇒ **同一时刻「我的探针全挂 + 别人的探针全通」** ⇒ ⇒ ⇒ **这不是故障，是我的探针错**（🔴 **关键：矛盾的证据就在手里，本层却选了错的那一边**）|
| ③ | **先证伪探针方法本身** | 起一个本地监听 ⇒ 用**同一个** probe 函数连它 ⇒ **OK（读到 `b'HELLO'`）** ⇒ ⇒ **探针方法本身有效** ⇒ ⇒ **排除「本机网络整体不通」** |
| ④ | **换正确协议探** | PG 发 **SSLRequest 启动包**（`\x00\x00\x00\x08\x04\xd2\x16\x2f`）⇒ ⇒ **connect OK ＋ 握手回 `b'N'`**；Redis 发 `PING\r\n` ⇒ ⇒ **`b'+PONG\r\n'`** ⇒ ⇒ ⇒ **转发是通的** |
| ⑤ | **全量逐端口体检** | **55/55** 已发布容器端口 **TCP 可连**（0 连不上）· **30/30** PG / Redis 容器 **协议应答正确** ⇒ ⇒ 剩 18 个无协议应答的是 **kafka / minio / nacos / etcd / grafana / prometheus / neo4j / milvus / beanstalkd / user-api** ⇒ ⇒ **又是拿 PG 握手去探它们** ⇒ ⇒ **与本次判定无关**（各 lane 的 Testcontainers 只打 PG / Redis）|

**③ 教训（跨项目可复用）**

- 🔴 **探针必须先证伪「探针自己」，再拿去判别人的死活**
  1. **任何「服务不可达」类结论**，必须先用**同一个探针**连一个【已知可用】的对端（本地监听 / 确定在跑的服务）⇒ 探针不通过 ⇒ **不许下结论**；
  2. **超时要分清发生在哪一步** —— `create_connection` 成功就说明**连上了** ⇒ ⇒ **`read` 超时 ≠ 不可达**；
  3. **协议服务要用它自己的协议探** ⇒ ⇒ **「发了字节没回」不是「不通」，可能是「我发的不是它要的东西」**；
  4. **优先用被测方自己的客户端**（JDBC / `pg_isready` / `redis-cli`）⇒ ⇒ **别用裸 socket 猜协议**。
- ⇒ ⇒ **同族**：与「零命中是假信号」「跑绿 ≠ 判据活着」「用 `@Test` 注解数复算用例数」同源 —— 都是**「你用来下结论的那个信号，不度量你以为的那个量」**；本条补的是**「你用来下结论的那个探针，本身可能就是坏的」**。
- ⇒ ⇒ **🔴 代价清单（这一条最值得记）**：本层据此上报过**一条不存在的机器级阻塞** ⇒ ⇒ ① 已告知 G1 / G2 / G3「波末收口起不来」（三方都收到了）⇒ ⇒ ② 已向 Jason 提议**重启 Docker Desktop**（⇒ 会连带杀掉各 lane 跑了 30+ 小时的 `ddl-*-pg` ⇒ **破坏性**）⇒ ⇒ **幸好没做** ⇒ ⇒ ⇒ ⇒ **一个「探针伪影」若无人复核，就会变成一次真实的破坏性处置**。
  ⇒ ⇒ **纪律**：**凡据此提议【破坏性处置】的结论，必须有第二个独立探针交叉验证**（本例：**G2 的 JDBC 探针就是那个第二证据 ⇒ 它当时就在手里 ⇒ 本层没采用**）。

**④ 连带处置（本层已做 / 未做）**

| 项 | 状态 |
|---|---|
| 台账 §4.10.54 ① 的触发条件 | ✅ **已撤销**（本节提交）|
| 台账 §4.10.56 ③ | ✅ **已加订正注记**（**保留原值 ＋ 推翻说明**）|
| 告知 G1 / G2 / G3「波末收口起不来」 | ✅ **已发更正**（三条 lane 各一条）|
| 向 Jason 提议的「重启 Docker Desktop」 | ✅ **已撤回**（**不需要任何 Docker 处置**）|
| 第 15 次全量的前置阻塞 | 🟢 **解除** ⇒ ⇒ **唯一剩余触发条件 = 三条 lane 并入本层分支【尖端】**（§4.10.54 ① 第一条 ⇒ **已满足**）|
| 正确探针法固化 | 🟢 **已固化**：**PG = SSLRequest 启动包 → 期望 `b'N'`** · **Redis = `PING\r\n` → 期望 `b'+PONG\r\n'`**（见 ② ④）|

**⑤ 本层这一条** | **零落码**（只改台账 `docs/**`）⇒ ⇒ 本层 HEAD `c0ca71e` → 本节提交。

#### 4.10.59 🔴🟢 **第 15 次全量的首轮红 ⇒ 牵出共享层三处真问题（1 处 Go 漂移 + 1 处非密闭测试 + 1 处错注释）⇒ 本层落码**（核对日期 2026-10-28 02:3x）

> **起因**：第 15 次全量（波末收口那一次）的**首轮**在 `service/common` 红 1 条 ⇒ ⇒ `GoStyleRpcProtectionChainTest.sheddingRejectionIsVisibleToTheOuterBreaker:116`
> ⇒ 断言消息「**前置条件：CPU 恒过载时必须出现降载**」⇒ ⇒ **不是产品行为红，是【前置条件】红** ⇒ ⇒ 必须查清「为什么前置条件会不成立」。

**① 先判「是不是合并引入的」**

| 判据 | 结果 |
|---|---|
| `git diff <本层树> <合并树> -- service/common/` | **空** ⇒ ⇒ **`service/common` 在两棵树之间零差异** ⇒ ⇒ **不是合并引入** |
| 复跑（合并树 · 同一测试类 · 6 次）| **6/6 绿** ⇒ ⇒ **非确定性 ⇒ flaky** |
| 第二轮全量（同命令）| `service/common` **绿** ⇒ ⇒ ⇒ **首轮红是【负载/时序】触发，不是代码态红** |
| ⇒ ⇒ **但** | ⚠️ **不能就这么放过**：波末收口那一轮**必然**是「`clean` 全量编译之后」⇒ ⇒ 而首轮红恰恰发生在那时（swap 已用 88%）⇒ ⇒ ⇒ **这条测试在收口工况下会随机红** ⇒ ⇒ **必须修**（否则每一轮收口都有踩雷风险）|

**② 根因 A：测试的「前置条件」依赖机器速度（**非密闭**）**

| 项 | 内容 |
|---|---|
| 机制 | `GoStyleAdaptiveShedder#minRt()` 取窗口内**已完成桶**的最小平均 RT（Go 的 `IgnoreCurrentBucket` ⇒ **当前桶不算**）⇒ ⇒ 而原配方用 `drive()` 50 次**正常完成**来抬 `avgFlying` ⇒ ⇒ **那 50 次会把【实测 RT】写进窗口** ⇒ ⇒ ⇒ **前置条件成了「机器速度 + 桶边界」的函数** |
| ⇒ ⇒ **两头都会翻车** | ① **机器慢** ⇒ 实测 RT 膨胀 ⇒ `minRt` 大 ⇒ `maxFlight = maxPass*minRt*0.01` 大 ⇒ `cap` 大 ⇒ **触发不了降载** ⇒ 前置条件红；<br>② **机器快** ⇒ 50 次调用**没跨过 100ms 桶边界** ⇒ RT 全落在当前桶 ⇒ `minRt` 取默认 **1000ms** ⇒ `maxFlight = 10` ⇒ `cap ∈ [1,10]` ⇒ **反而必过** ⇒ ⇒ ⇒ **只有一条窄时间窗能过** ⇒ ⇒ ⇒ **典型 flaky** |
| Go 出处（对照）| `go-zero@v1.10.2/core/load/adaptiveshedder_test.go:124-178` 的 `TestAdaptiveShedderShouldDrop` ⇒ ⇒ Go **直接设 `shedder.avgFlying = 80` / `shedder.flying = 80`** ⇒ ⇒ **Go 的测试是密闭的**（不靠推导）⇒ ⇒ **RAG 这条是「自己推导状态」的版本 ⇒ 推导链上混进了实测 RT** |
| ⇒ ⇒ **修法（两阶段 · 测试面 · 生产代码零改动 · 不新增任何测试接缝）** | 阶段①：先堆 **200** 个悬空调用 ⇒ `flying = 200`（此阶段 `avgFlying = 0` ⇒ `highThru()` 恒假 ⇒ **必然不被降载** ⇒ `flying` 精确等于 200）；<br>阶段②：只用 `shedder.fail()`（Go: `promise.Fail()` —— **只减在途、绝不记 RT**）抬 `avgFlying` ⇒ EMA 推到 ≈140 ⇒ **窗口里始终没有任何 RT 数据** ⇒ `minRt()` 恒为 1000ms ⇒ `maxPass()` 恒为 1 ⇒ `maxFlight = 10` ⇒ `cap ∈ [1, 10]` ⇒ ⇒ `avgFlying ≈ 139.7 > cap` 且 `flying = 140 > cap` ⇒ ⇒ **必然出现降载（与机器快慢无关）** |
| ⇒ ⇒ **为什么阶段②不能用 `driveOpen` 补在途** | 🔴 **Go 的 `Allow()`（`adaptiveshedder.go:127-133`）是先判降载、后 `addFlying(1)`** ⇒ ⇒ **被拒的请求【不计入在途】** ⇒ ⇒ 用 `driveOpen` 补偿会**越补越少** ⇒ ⇒ （本层第一版配方正是这么写的 ⇒ **自查时被抓出来** ⇒ 改成两阶段）⇒ ⇒ **RAG 的 `allow()` 与 Go 逐字一致 ⇒ 这不是漂移** |
| ⇒ ⇒ **真实断言一字未动** | 被守的是「**shedding 的降载拒绝必须能被外层 breaker 看到**」（链序 Breaker → Shedding）⇒ ⇒ **只改了【怎么把状态摆到那个位置】，没改【守什么】** ⇒ ⇒ **守卫的牙没钝** |

**③ 根因 B：调用点的「整型毫秒截断」⇒ Go 的 `float64 + ceil` 被抵消（**真漂移 · §3.1**）**

| 项 | 内容 |
|---|---|
| Go 出处 | `go-zero@v1.10.2/core/load/adaptiveshedder.go:284-287`：<br>`rt := float64(timex.Since(p.start)) / float64(time.Millisecond)` ⇒ `rtCounter.Add(int64(math.Ceil(rt)))` ⇒ ⇒ **浮点毫秒 + 向上取整、无下限** |
| RAG 原状 | `GoStyleRpcSheddingInterceptor`：`long rtMs = (System.nanoTime() - startNanos) / 1_000_000L;` ⇒ ⇒ **先截断成整数** ⇒ ⇒ 被调方 `pass(double)` 里的 `Math.ceil(rtMs)` **成了空操作** ⇒ ⇒ ⇒ **「浮点 + ceil」在调用点被抵消** ⇒ ⇒ **半修**（与 §4.10.57 那条「两处契约不一致」**同族**：修了一处、另一处还在） |
| ⇒ ⇒ **后果** | RT=0.2ms：Go 记 **1**、RAG 记 **0**；RT=1.5ms：Go 记 **2**、RAG 记 **1** ⇒ ⇒ **系统性少记约 1ms** ⇒ ⇒ `minRt()` 偏小 ⇒ `maxFlight` 偏小 ⇒ ⇒ **在途上限偏低 ⇒ RAG 比 Go 更早降载** ⇒ ⇒ AGENTS.md §3.1：**行为差异默认判 RAG 错** ⇒ ⇒ **修** |
| ⇒ ⇒ **修法** | 调用点改成 `/ 1_000_000D`（**浮点**）⇒ ⇒ `ceil` 才真正生效 ⇒ ⇒ 与 Go 逐字等价 |
| ⇒ ⇒ **为什么之前没人发现** | 🔴 `GoStyleAdaptiveShedderTest:184-186` **已经在 `pass(double)` 这一层断言过 ceil**（`pass(1.2D)` → 记 2）⇒ ⇒ **被调方有网** ⇒ ⇒ **调用点这一截断从来没有被任何断言覆盖** ⇒ ⇒ **「有守门」≠「这一处被守住」** |
| ⇒ ⇒ **补网** | ① 把 RT 测量抽成 `static double measuredRtMs(long startNanos)`（**抽出来是为了让它有网**）；② 新增守卫 `GoStyleRpcSheddingInterceptorTest#rtMeasurementKeepsFractionalMilliseconds`：断言 1.5ms 前开始的调用其 RT **落在 [1.4, 1.6]** ⇒ ⇒ **截断若回来 ⇒ 必红**（且**确定性**，不依赖机器速度）|

> **🔴 落码形态订正（2026-10-28 02:4x · 落码后核对）** —— 上一行的「补网」是**落码前的计划**；**实际落码与它有三处不同** ⇒ ⇒ 按 AGENTS.md §3.2（留痕必须准确）**保留原值可追**，此处标出差异：
>
> | 计划 | 实际落码 | 为什么改 |
> |---|---|---|
> | `static double measuredRtMs(long)` | **实例方法** ＋ 注入式时钟源 `private final LongSupplier nanoTime`（`GoStyleRpcSheddingInterceptor.java:117`）＋ 4 参包私有构造（`:135`）⇒ ⇒ `measuredRtMs` 读**注入的**时钟（`:166-167`）| `static` 读不到注入的时钟 ⇒ ⇒ 要让「RT 恰好 1.5ms」能被**确定性**造出来 ⇒ ⇒ 必须能换掉时间源 |
> | 断言 RT 落在 `[1.4, 1.6]`（**带上界**）| **只断言下界** `isGreaterThan(1.0D)`（`GoStyleRpcSheddingInterceptorTest.java:197`）| 🔴 **上界会假红**：机器有负载时两次 `nanoTime()` 之间可隔几百微秒 ⇒ 实测过 **2.47ms** ⇒ ⇒ **这正是本条要消灭的那类脆弱性** ⇒ ⇒ 判据只抓「有没有被截断」（截断 ⇒ 恰好 1.0；不截断 ⇒ ≥ 1.5）|
> | **一条**守卫（helper 层）| **两条**（helper 层 `:187` ＋ **调用点层** `:202`）| 🔴 **只测 helper 绿、调用点照样能截断** ⇒ ⇒ 「有守门」≠「这一处被守住」⇒ ⇒ **成对契约两处都要有网** |
>
> ⇒ ⇒ **调用点层那条**用**假时钟**（第 1 次取 `0`、第 2 次取 `1_500_000ns` ⇒ RT 恰好 1.5ms）⇒ ⇒ 断言被调方 `recordedRtSum()` **必须 == 2**（Go `math.Ceil(1.5)=2`）⇒ ⇒ **整型截断 ⇒ 被调方收到 1.0 ⇒ 记 1 ⇒ 该断言必红** ⇒ ⇒ **红在目标断言上**。
>
> ⇒ ⇒ ⚠️ **`LongSupplier` 是本层唯一的测试接缝** ⇒ ⇒ **不新增任何行为分支**（默认 `System::nanoTime`）⇒ ⇒ 生产语义不变。

**④ 根因 C：拦截器的一句注释是错的（**留痕失真**）**

| 项 | 内容 |
|---|---|
| 原文 | 「Go: sheddinginterceptor.go:25 —— **每个进入本拦截器的请求都先 +1**（在 Allow 之前）」 |
| 实际 | Go `sheddinginterceptor.go:31` 的 `IncrementTotal()` **确实**在 `Allow()` 之前；但**在途数 +1 在 `Allow()` 内部、判降载之后**（`adaptiveshedder.go:127-133`）⇒ ⇒ **被拒请求不计入在途** |
| ⇒ ⇒ **处理** | 订正注释（**不是**改行为 —— 行为本来就与 Go 一致）⇒ ⇒ AGENTS.md §3.2：**留痕必须准确**，错注释会误导读代码的人去「先 +1」 |

**⑤ 触及的 A 轴 ＋ Go 出处**

| 轴 | 结论 |
|---|---|
| **A3**（跨域暴露面 · 共享层行为）| 🟡 **有变更**：降载阈值计算链的 **RT 记录**改了 ⇒ ⇒ 属**共享层**（`service/common/**`）⇒ ⇒ **10 个 api / 各 rpc 组件的降载行为都吃这条** ⇒ ⇒ ⇒ **必须走全量回归**（§4.10.54 清单）|
| 其余 A 轴 | 不变（本轮只碰降载的 RT 记录 + 一个测试 + 一句注释）|
| **Go 出处（三处，逐条带行号）** | ① `core/load/adaptiveshedder.go:284-287`（`float64` + `math.Ceil`）· ② `core/load/adaptiveshedder.go:127-133`（`Allow()` 先判降载后 `addFlying(1)`）· ③ `core/load/adaptiveshedder_test.go:124-178`（Go 的测试**直接设状态** ⇒ 密闭）· ④ `zrpc/internal/serverinterceptors/sheddinginterceptor.go:31-46`（`IncrementTotal` 在前 / `defer` 里 `Fail` vs `Pass`）|

> **🔴 影响面订正（2026-10-28 03:0x · 落码后 grep 实测）** —— 上一行的「**10 个 api / 各 rpc 组件**」这个写法**不准** ⇒ ⇒ 保留原值可追，此处给准确口径：
>
> | 项 | 实测结果 |
> |---|---|
> | 降载拦截器的位置 | **gRPC server 侧**（`GoStyleRpcSheddingInterceptor` 是 `io.grpc.ServerInterceptor`）⇒ ⇒ **api 组件（HTTP 入口）零装配** |
> | 实际吃这条的 = **10 个 rpc 组件** | 逐条 grep 生产装配点 `GoStyleRpcProtectionChain.build(...)`：<br>`like-rpc/LikeRpcApplication.java:137` · `comment-rpc/CommentRpcApplication.java:99` · `follow-rpc/FollowRpcApplication.java:118` · `message-rpc/MessageRpcApplication.java:134` · `favorite-rpc/FavoriteRpcApplication.java:108` · `admin-rpc/AdminRpcApplication.java:112` · `user-rpc/UserRpcApplication.java:118` · `task-rpc/TaskRpcApplication.java:138` · `hot-rpc/HotRpcApplication.java:108` · `article-rpc/ArticleRpcApplication.java:131` |
> | ⇒ ⇒ **结论修正** | 影响面 = **10 个 rpc 组件**（不是「10 个 api」）⇒ ⇒ **全量回归的必要性不变**（这 10 个 rpc 全在 reactor 里）⇒ ⇒ 但**若有人按「10 个 api」去核对 api 侧 ⇒ 会核对错对象**（api 侧零装配 ⇒ 改 RT 对 api 侧**零行为影响**）|


**⑥ 本层这一条** | **本轮首次有【共享层生产代码】的行为变更**（不是加守门、不是纯注释）⇒ ⇒ **必须全量回归 + 变异验证** ⇒ ⇒ **零可执行代码改动**的说法在本节**不再成立**（§4.10.50~§4.10.58 那段期间成立）。

#### 4.10.60 🟢 **合并内容守门落码（`check-merge-tree-content.py` ＋ `check-no-nul-bytes.py`）⇒ 把「零冲突 ≠ 内容完整」这条判据【机器化】**（核对日期 2026-10-28 03:5x · 本层 HEAD `9a61ee4` → 本节提交）

> **🔴🔴 本节【前一版】的前提是错的 ⇒ 已订正 ⇒ 详见 §4.10.61。**
> **前一版（提交 `9a61ee4`）声称「合并树丢了 G3 的改动 / 被揉坏」⇒ ⇒ 【不成立】** ⇒ ⇒
> 那是本层拿「**G3 的最新 HEAD**」去比「**用 G3 的旧 HEAD 建出来的合并树**」造成的【时间错位】假象。
> ⇒ ⇒ **本节只保留【站得住的部分】**：① 「零冲突 ≠ 内容完整」这条**机制**成立（git 对含真 NUL 的文件按二进制处理 ⇒ 不报冲突 ⇒ 只取一边/逐块揉）；② 两个守门（把这条判据机器化）；③ **两棵合并树的内容实测完整**。

**① 机制（成立，但本轮【未触发】）**

> **git 对含【真 NUL 字节】的文件按【二进制】处理 ⇒ ⇒ 三方合并【不做文本合并】⇒ ⇒ 只可能「取一边」或「逐块揉」⇒ ⇒ 而且【不报冲突】。**

⇒ ⇒ **「`git merge-tree` 零冲突」推不出「内容完整」** ⇒ ⇒ 这是对 §4.10.52 那条合并结论的**边界补充**（那条讲「零冲突 ≠ 判据通过」；本条讲「**零冲突 ≠ 内容完整**」—— 两个不同的失效面）。
⇒ ⇒ ⚠️ **触发条件**（本轮实测出来的精确条件）：只有**多方都改同一个含 NUL 的文件**时才会揉坏；**只有一方改**时 git 取那一侧 ⇒ **内容正确** ⇒ ⇒ **本轮四路合并树里没有任何这种情形**（见 ③ 的实测）。

**② 落码：两个守门**

| 守门 | 作用 | 变异自测 | 当前状态 |
|---|---|---|---|
| `tools/difftest/check-no-nul-bytes.py` | 扫 `docs/**` · `service/**` · `tools/**` · `AGENTS.md` 的**真 NUL**：文档**必须零 NUL**；源码走**显式白名单**（每条写清「谁该修 + 修完怎么删」；**已无 NUL ⇒ 🟡 提示可清空**；**数量变了 ⇒ 🟡 提示复核豁免**）⇒ ⇒ **拦住「下一个域踩同一个坑」** | **4 项全命中**（M1 文档注入 ⇒ 红 + 点名 · M2 源码注入 ⇒ 红 + 点名 · M3 白名单失效 ⇒ 红 + 点名 · M4 白名单已无 NUL ⇒ 提示可清空） | **EXIT=0**（当前 2 个命中**都在白名单**）⇒ ⇒ **守门从第一天就绿**，且能挡住**新的** NUL 引入 |
| `tools/difftest/check-merge-tree-content.py` | **「合并树内容 = 各步合并的并集」**这条判据的机器化 ⇒ ⇒ 逐步（每步用**那一步自己的** merge-base）验证：① 两边相同 ⇒ 结果必须相同 ② 只有 X 改 ⇒ 结果必须 == X ③ 只有 Y 改 ⇒ 结果必须 == Y ④ 两边都改 ⇒ 文本合法（不判）· **含真 NUL ⇒ 🔴 静默揉坏** | —（靠 ③ 的实测 + 两棵树复核） | ✅ **PASS**（见 ③） |

⚠️ **第二版才做对的地方（第一版判据有缺陷 ⇒ 差点冤枉 G3）**：第一版拿 `git merge-base(ref1 ref2 ...)` 当全局 base ⇒ ⇒ 但各 lane 之间存在**包含关系**（G1/G2 已并入本层尖端）⇒ ⇒ merge-base 偏向**包含者** ⇒ ⇒ 「谁改了这个文件」被**整片误判**（实测误报 **72 条**）⇒ ⇒ **教训**：**判「谁改了什么」必须用【那一步合并自己的】merge-base，不能用全局共同祖先**。

**③ 内容实测：两棵合并树都【完整】（用守门 + 当时的 ref 复核）**

| 合并树 | 建它时用的 ref | 守门结果 | 结论 |
|---|---|---|---|
| `4b0fdd7`（上一轮第 15 次全量那棵）| `929223e` ⊕ `caba808` ⊕ `11f96dd` ⊕ `b79779e` | **PASS**（3 步 · 问题 0 · 不可信 0）· 且 `--expect 4b0fdd7` **一致** | ✅ **内容完整** ⇒ ⇒ **上一轮「第 15 次全量 2764 条 / 0f / 0e / 4s」的口径【有效】⇒ ⇒ §4.10.59 的数字不用订正** |
| 当前四路（用**当前** ref 重建）| G1 · G2 · G3 · 本层尖端 | **PASS**（3 步 · 问题 0 · 不可信 0） | ✅ **内容完整** |

⇒ ⇒ **这直接否掉了「上一轮全量口径有缺损」那个说法**（它来自 §4.10.61 描述的那次错误比对）。

**④ 顺带查清：真 NUL 只有 2 个文件（`git ls-files` 逐 blob 查 `b'\x00'`）**

| 文件 | NUL 数 | 归属 | 性质 | 是否触发揉坏 |
|---|---|---|---|---|
| `service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/FavoriteDdlParityTest.java` | 4 | **G3 写权面** | **故意测试夹具**（Redis 复合 key 的分隔符）⇒ ⇒ 可用 Java 转义 `'\0'` **零行为变化**地改掉 | ❌ **本轮未触发**（只有 G3 改过它 ⇒ git 取 G3 那一侧 ⇒ 正确）⇒ ⇒ **但只要将来有第二方改同一个文件 ⇒ 立刻会揉坏** |
| `docs/DRIFT/users/admin/rpc.md` | 2 | **G1 写权面** | 台账里**粘了被观测对象的真字节** ⇒ ⇒ 应改成**可见转义**（`U+0000`）| ❌ 本轮未触发 |

⇒ ⇒ **两处仍建议改**（理由从「已经丢了东西」改成「**机制成立 ⇒ 将来会咬人**」）⇒ ⇒ **转派已发**（G3 · G1）。

⚠️ **注意与 U+FFFD 那条纪律同源但不同**：U+FFFD 那条讲「**别把损坏字节照抄进台账**」；本条讲「**别把【真控制字节】粘进台账**」⇒ ⇒ 后果更重（U+FFFD 只是难读，**真 NUL 会让 git 改变合并行为**）。

**⑤ 合并树守门实测（真 worktree 下，11 + 2 个）**

**9 绿**：`check-tomcat-limits` · `check-wave-lane-ownership` · `check-wave-role-model` · `check-grpc-interceptor-wiring` · `check-flyway-ledger` · `check-ledger-tables` · `check-ledger-selfcheck` · `check-doc-links` · `check-agents-budget`
**2 红 = 既有债、非合并引入**（两个都在 **SHARED 面 ⇒ 本层只登记、交合并收口执行者**）：

| 红项 | 实测 | 落点（**SHARED ⇒ 本层不代改**）| 工具 |
|---|---|---|---|
| `check-wave-coupling` | **10 项** `common_radiation` 漂移 | `docs/DRIFT/_cross/cross-domain-coupling.md` | 支持 `--print` |
| `check-ledger-count` | 索引称 **315** · 实测 **330**（差 **+15**）| `docs/Pitfalls/_INDEX.md` | 支持 `--fix` |

⇒ ⇒ **「合并引入 = 0」的实测证据**（不是推断）：三棵 lane 树各跑 `check-wave-coupling` ⇒ **G1 = 1 项**（`GoStyleRedisClientAutoConfiguration`）· **G2 = 9 项** · **G3 = 1 项**（`MqConsumer`，⊂ G2 的同名项）⇒ ⇒ **并集 = 10 项** ⇒ ⇒ **与合并树的 10 项逐条吻合** ⇒ ⇒ **合并零新增坐实** ✅

⇒ ⇒ **ledger-count 那 +15 的构成（逐文件实测）**：**全部来自 G3 的 5 份 Pitfalls 台账** ⇒ `article/rpc.md` 15→16（+1）· `comment/rpc.md` 13→16（+3）· `favorite/api.md` 9→14（+5）· `favorite/rpc.md` 37→40（+3）· `hot/rpc.md` 20→24（+4）⇒ ⇒ **G1 / G2 / 本层三棵树的实测数都是 315**（与索引一致）⇒ ⇒ ⇒ **合并收口执行者跑一次 `--fix` 即可**（**不需逐条手改**）。

**⑥ 本轮两笔台账订正（与本条无关 · 来自 `d212323` 的两处「留痕失真」）**

| 位置 | 原值 | 订正 |
|---|---|---|
| §4.10.59 ③「补网」 | 「`static double measuredRtMs`」＋「断言落在 `[1.4, 1.6]`」＋「一条守卫」 | **落码实际是**：`static`→**实例方法 + 注入式时钟源**（`:117`/`:135`）· 断言**去掉上界**（实测慢机 2.47ms ⇒ 上界会假红 ⇒ **这正是本条要消灭的那类脆弱性**）· **一条→两条**守卫（helper 层 `:187` ＋ **调用点层** `:202`）⇒ ⇒ 提交 `32d8b91` |
| §4.10.59 ⑤「影响面」 | 「**10 个 api** / 各 rpc 组件」 | **实测是 10 个【rpc】组件**（降载在 **gRPC server 侧** ⇒ **api 侧零装配**）⇒ ⇒ 逐条装配点见订正块 ⇒ ⇒ 提交 `6b80f7f` |

**⑦ 交班项（仍在，不因本轮落码而消解）**

🔴 **`GoStyleRpcProtectionChainTest.sheddingRejectionIsVisibleToTheOuterBreaker:116`**（「前置条件：CPU 恒过载时必须出现降载」）—— **非密闭测试**：
- 本层第一版密闭改造（200 次 `driveOpen` + N 次 `shedder.fail()`）**两次都红** ⇒ ⇒ **已 `git checkout` 改回原配方**（**生产代码零改动**）⇒ ⇒ **交班**。
- **实测真值**（临时插桩后已撤）：原配方下 breaker 窗口 `total=51 accepts=1 w=1.5 weightedAccepts=1.5 dropRatio=0.8558` ⇒ ⇒ **熔断侧断言本身是概率性的（~85.6% 通过）** ⇒ ⇒ 该守卫有**两处**不确定性（前置条件依赖实测 RT ＋ 熔断判定带随机）⇒ ⇒ **谁要重做，第一步应先把这几个记账数打出来，而不是拍脑袋改配方**。
- ⚠️ **本轮 `d212323` 对它的影响（方向已知，量未测）**：RT 记录从「整型截断」改成「浮点 + ceil」⇒ ⇒ 记入的 RT **整体 +0~1ms** ⇒ ⇒ `minRt()` 偏大 ⇒ ⇒ `maxFlight` 偏大 ⇒ ⇒ `cap` 偏大 ⇒ ⇒ **降载更难触发** ⇒ ⇒ ⚠️ **这条的前置条件在本轮之后【只会更难成立】** ⇒ ⇒ 若本轮全量红在它 ⇒ ⇒ **要区分「flaky 复现」与「RT 修正导致的确定性恶化」** ⇒ ⇒ **判据 = 看 breaker 窗口那 5 个记账数**。

#### 4.10.61 🔴🟢 **认错④：推翻本层上一轮上报的「合并树把 G3 的改动揉坏了 / 丢了 40 行」⇒ 那是【时间错位】造成的假象**（核对日期 2026-10-28 04:0x · 本层 HEAD `9a61ee4` 的下一笔）

> **被推翻的结论**（原文留在提交 `9a61ee4` 的 §4.10.60 里，可 `git show 9a61ee4` 追）：
> 「🔴 合并期数据丢失的真陷阱：含真 NUL 字节的文件被 git 当二进制 ⇒ 三方合并静默丢内容 / 揉坏 ⇒ **合并树 ≠ 三方并集**」
> ⇒ ⇒ **【实证部分全部不成立】**（机制部分成立 ⇒ 见 §4.10.60 ①）。

**① 我当时是怎么错的（逐条复盘）**

| 步骤 | 我做的 | 对不对 |
|---|---|---|
| 1 | 核「合并树 = 三方并集」⇒ 逐文件比 **blob 哈希** | ✅ 方法对 |
| 2 | 取「三方共同 blob」= **本层树**（`align/g4-service-common`）里的 blob | ⚠️ **本层树 ≠ 建树时的 base**（本层树含 `d212323`，而建 `69a9334` 时用的是 `b79779e`）|
| 3 | 取「G3 的 blob」= **G3 当时的最新 HEAD**（`b99462e`）| 🔴 **错**：`69a9334` 是用 G3 的 **`44a913e`** 建的 ⇒ ⇒ **拿后来的 G3 去比当时的合并树** |
| 4 | 得出「合并树 = 第三版 ⇒ 被揉坏」 | ❌ **结论错**：`6077498e` **就是** G3 在 `44a913e` 时的版本（`11f96dd` 起就是它）⇒ ⇒ 合并**取 theirs，正确** |

⇒ ⇒ **`docs/Pitfalls/favorite/rpc.md` 的「G3 40 条 · 合并树 39 条」同理** ⇒ ⇒ 那 1 条（`PIT-FAV-RPC-040`）是 G3 在 **`ebdccbe`**（**晚于** `44a913e`）才加的 ⇒ ⇒ 合并树里没有它是**正确**的，不是丢失。

**② 独立复核（用「建树时那一刻的 ref」重跑）**

| 合并树 | 当时的 ref | 复核结果 |
|---|---|---|
| `4b0fdd7` | `929223e` ⊕ `caba808` ⊕ `11f96dd` ⊕ `b79779e` | **PASS** · 且重建结果 **== `4b0fdd7`**（`--expect` 一致）|
| 当前四路 | G1 · G2 · G3 · 本层尖端 | **PASS** |

⇒ ⇒ **两棵合并树的内容都完整** ⇒ ⇒ **§4.10.59 的「第 15 次全量 2764 条 / 0f / 0e / 4s」口径【有效】⇒ ⇒ 不用订正**。

**③ 代价（必须如实记）**

| 代价 | 内容 |
|---|---|
| **已发出去的消息里含错结论** | 给 G3、G1 各发过一封「你的改动被揉坏了 / 丢了 40 行」⇒ ⇒ **必须主动更正**（纪律：改了结论要**主动**通知，不能指望对方去读文件）⇒ ⇒ **更正已发** |
| **已落码的守门** | 两个守门**都保留**（`check-no-nul-bytes.py` / `check-merge-tree-content.py`）⇒ ⇒ 理由从「已实证丢失」改成「**机制成立 ⇒ 预防**」⇒ ⇒ 且第二个守门**恰好就是防「我这次这种时间错位」的工具**（它强制用「建树时那一刻的 ref」）|
| **台账** | §4.10.60 已改成正确版 + 本节留认错痕迹 |

**④ 教训（跨项目可复用 ⇒ 已落 agent memory）**

- **「跨树引用」要对齐 commit，不只是对齐路径**：比较两个树的内容时，**两侧必须取「同一时刻」的版本** ⇒ ⇒ 否则「A 比 B 少了一条」可能只是「B 是后来才加的」⇒ ⇒ **判据 = 引用时带上 commit，并在比对前重核「那个 commit 是不是当时的那个」**。
- **判「谁改了什么」要用【那一步合并自己的】merge-base**：全局共同祖先在「lane 之间有包含关系」时会整片误判（本次误报 72 条）⇒ ⇒ **差一个 base ⇒ 结论方向相反**。
- ⚠️ 与既有条目的关系：「**跨树引用 = 行号 + commit + 内容摘录三件套**」（§4.10.51 ⑩）**已经写了纪律，但本轮我自己没做到**（只对齐了路径，没对齐 commit）⇒ ⇒ **纪律写了不等于做到了** ⇒ ⇒ 这次靠**新写的守门**才当场发现 ⇒ ⇒ **⇒ 把「人工核对」变成「机器判据」，才是可靠的做法**。

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
- [x] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）—— ⚠️ **`_README.md` §七之二属共享面 ⇒ 本 lane 不写**（合并后由合并收口执行者统一更新）⇒ **G3 四张 + G2 三张 + G1 八张共 15 张已登记在 §4.3~§4.10**（G1 七张的逐条定性见 §4.9.1 · 第八张见 §4.10.1）
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由合并收口执行者统一更新）⇒ **本 lane 全程未写**；⇒ 需要回写的 **5 条**已登记在 **§4.10.7**（含 `tools/difftest/flyway-concurrent-verify.py` 漏记 `article-rpc` · `AGENTS.md:173` 漏记 `article-rpc`）。
- [x] 🆕 **1xx 探测行数上界**（`15cf93a`）—— 共享层 ⇒ **10 个 api 组件全部适用**（§4.10.32）
      ⇒ ⇒ 守门 3 条 ＋ **专属变异 M1/M2/M3**（各红且仅红一条）⇒ ⇒ `service/common` **330 条全绿**
      ⇒ ⇒ 🔴 **待合并收口执行者回写 1 条**：`docs/Intentional_Deviations/global/common-tails.md` 的 `SD-2026-10-25-04`
      仍写「N = 探测轮数 ≈ 慢请求秒数 ÷ 0.5s」⇒ **N 现在有上界（默认 4）** ⇒ 建议补「客户端 interim 上限」的事实
      ⇒ ⇒ **该文件不属本 lane 写权 ⇒ 本层只登记、不代改**（§4.10.32 末行）
- [x] 🆕 **独立复核（verifier）的回执处理 ＋ 三树门禁绿红错位实测 ＋ §1e 角色边界确认**（§4.10.50 · 核对日期 2026-10-28）
      ⇒ ⇒ **verifier 报告 = 修复前快照**（出稿 19:44:04 vs 修复提交 `407c230` 19:48:00）⇒ **5 条发现早已落码 ⇒ 本轮零新增落码**
      ⇒ ⇒ **三棵 lane 树的容器上限守门「各红各绿，但没有一个是『对』的」**（G1 红得对 · G2 红得冤 · G3 绿得假）⇒ ⇒ **A（真源派生脚本）与 B（task-api yaml 订正）必须同批合入**（三树同时实测复现 §4.10.41 ①）
      ⇒ ⇒ **并入 trunk 2 笔政策提交**（`4ae5461` + `c9cf2c1` ⇒ HEAD `ff81709` · 零冲突）⇒ **本层角色未变**（G4 = 共享层代码落码）⇒ ⇒ **台账 88 处「整合者」已统一改为「合并收口执行者」**（§1e：这个词已作废 ⇒ 别让读者按空指针去找人）
      ⇒ ⇒ **零可执行代码改动** ⇒ **第十四次全量口径（2480 条）仍有效**（`service/common` 已单独复跑 = 331）⚠️ **全量推算值 2487 不可当实测数引用**
- [x] 🆕 **守门工具的「潜伏死代码」修复**（`9037a2b` · §4.10.57 · 核对日期 2026-10-28）—— G2 探针报 ⇒ 本层逐字复现
      ⇒ ⇒ **层 4 的「带版本前缀精确定位通道」是死代码**（`REF_RE` 字符类不含 `@` ⇒ 提取阶段就截断 ＋ `_direct_module_candidate` 只认全式 ⇒ 解析阶段也断 ⇒ **双重失效**）
      ⇒ ⇒ **修法**：`REF_RE` 补 `@` ＋ 新增 module cache `<name>@<ver>` 目录索引 ＋ **短式后缀唯一匹配**（**多命中 ⇒ `None` ⇒ 不猜**）⇒ ⇒ **台账写法不必改**（跨域一致性优先）
      ⇒ ⇒ 🔴 **原自测是「假绿」**（直调解析器、绕过提取器）⇒ ⇒ 新增**端到端**自测 ＋ **同名多模块 ⇒ `None`** 两条 ⇒ ⇒ **纪律：凡「提取器 ↔ 解析器」这类成对契约，自测必须走完整链路**
      ⇒ ⇒ **变异 M1（2 红）/ M2（1 红）各红且仅红在目标断言上** ＋ 内置 `--mutation-test` 18 项精确命中 ＋ 还原后 **sha256 逐字节一致**
      ⇒ ⇒ **改前/改后输出 diff 只差 3 行 ⇒ 零计数影响** ⇒ ⇒ **性质 = 潜伏死代码修复** ⇒ ⇒ **零业务影响**（`service/**` 未动）⇒ ⇒ **第十四次全量口径（2480 条）不受影响**
      ⇒ ⇒ **写权**：`tools/check-ledger-selfcheck.py` **不在** SHARED 13 文件清单 ⇒ **无人拥有面** ⇒ 按先例（§4.10.16 ③ · `139b428`）**本层自修**，非越权
- [x] 🔴🟢 **认错③：撤销「Docker 端口转发故障」这条【不存在的机器级阻塞】**（§4.10.58 · 核对日期 2026-10-28 02:2x）
      ⇒ ⇒ **真根因 = 本层的探针发了垃圾字节 `\x00`，PG 不理垃圾 ⇒ 「读超时」被误读成「连不上」** ⇒ ⇒ **实测终态：55/55 发布端口 TCP 可连 · 30/30 PG/Redis 容器协议应答正确（`b'N'` / `b'+PONG\r\n'`）⇒ Docker 转发健康**
      ⇒ ⇒ **连带撤下**：§4.10.54 ① 的触发条件（已撤销）· §4.10.56 ③（已加订正注记 · **保留原值**）· 已告知三 lane 的「波末收口起不来」（已发更正）· 向 Jason 提议的「重启 Docker Desktop」（**已撤回 · 不需要任何 Docker 处置**）
      ⇒ ⇒ ⇒ **第 15 次全量的前置阻塞解除** ⇒ ⇒ **唯一剩余触发条件 = 三条 lane 并入本层分支【尖端】**（已满足）
      ⇒ ⇒ **纪律**：**凡据此提议【破坏性处置】的结论，必须有第二个独立探针交叉验证**（本例第二证据 = G2 的 JDBC 探针 ⇒ **当时就在手里 ⇒ 本层没采用**）
