<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-rpc`

> **核对时间：2026-10-25**
> ⚠️ 本档的条目分两类：
> · **DEV-FAV-RPC-xxx** —— 已确认「两侧确实不同」且判定「不修回去」的偏离（**待签字**）；
> · **U5 撤销** —— 原 U5 条目**前提被实测证伪** ⇒ 它**不是**偏离，见下方「撤销记录」。

---

## 撤销记录 · 原 U5（`ObserveRPC` 的 `result` 标签）· ❌ **撤销**

| | 内容 |
|---|---|
| **原结论（2026-10-23）** | 「Go 侧 **21 处**错误返回因**未赋值命名返回值 `err`**，被记成 `success`；Java 侧按真实结果计数 ⇒ **有意的指标行为变更，未签字**」。 |
| **实测证伪（2026-10-25）** | ① Go 的 {@code return nil, favoritecommon.GRPCError(...)} **会**把返回值赋给对应的**命名返回值** {@code err}（Go 语言语义）⇒ defer 里的 {@code ObserveRPC} 看到的是**非 nil** 的 err ⇒ 记的是 <b>{@code fail}</b>，**不是** {@code success}。<br>② 那个「21」是 {@code return nil, GRPCError(...)} 的**出现处数**（实测 **22** 处，不是 21）。 |
| **证据** | 可复现探针：`bash tools/metrics-probe/favorite/run.sh` ⇒ **EXIT=0**<br>输出：`RESULT\|return-stmt-only\|fail` / `RESULT\|assigned-first\|fail` / `RESULT\|success\|success`。<br>⚠️ 该脚本会**从 Go 真源抽取** {@code ObserveRPC} 的 result 判定并做 sha256 比对 ⇒ **真源变了探针立刻响**。 |
| **结论** | **Go 与 RAG 在这一点上本来就一致**（都按真实结果记 success/fail）⇒ **不存在偏离，无需签字**。<br>⇒ 已同步改正 RAG 侧 {@code FavoriteRpcMetrics} 的 javadoc（此前那段「Go 有 21 处编码疏忽」是**错误的「Go: …」断言**，AGENTS.md §3.3.2 点名的形态）。 |
| **风险转移** | Java 没有「命名返回值 + defer」组合 ⇒ RAG 靠 try/catch/finally + **每个失败分支显式设 {@code RESULT_FAIL}** 来达成同一语义 ⇒ **新增失败分支漏设就会把失败记成 success**。<br>⇒ 已由 `FavoriteRpcMetricsResultTagTest`（4 例，运行时断言）守住。 |
| **守门** | `tools/metrics-probe/favorite/run.sh`（Go 侧语义）+ `FavoriteRpcMetricsResultTagTest`（RAG 侧行为）。 |

---

## DEV-FAV-RPC-001 · 🟡 **待签字** · `favorite_request_seconds_counter` 的 Prometheus 渲染名

| | 内容 |
|---|---|
| **Go 行为** | `metrics.go:22-27` 是 `prometheus.NewCounterVec(CounterOpts{Namespace:"", Subsystem:"favorite", Name:"request_seconds_counter"})` ⇒ Prometheus 渲染为 **`favorite_request_seconds_counter`**（`CounterVec` 的 `Name` 已是终名，**不**追加 `_total`）。 |
| **RAG 行为** | `FavoriteRpcMetrics.observeRpc` 用 Micrometer `Counter.builder("favorite_request_seconds_counter")` ⇒ Micrometer 的 Counter 在 Prometheus 侧**自动追加 `_total`** ⇒ 渲染为 **`favorite_request_seconds_counter_total`**。 |
| **对齐的部分** | ✅ 其余三个指标名**完全一致**：`favorite_request_total` / `favorite_ops_total` / `favorite_db_error_total`（名字已以 `_total` 结尾 ⇒ Micrometer 不再追加）。 |
| **为什么接受** | 改名的代价是**观测断档**（Prometheus 侧 `rate(favorite_request_seconds_counter[5m])` 立刻失效）；而 Go 的这个指标本身是**「累计秒数的计数器」**（`CounterVec.Add(time.Since(started).Seconds())`，不是 Histogram）⇒ 把它换成 Timer 会把语义从「累计」变成「分布」，那是**另一个指标**而不是同一个指标的修正。 |
| **什么时候会变成真差异** | 若看板/告警按 Go 的旧名写 ⇒ 迁到 RAG 后该查询**静默返回空**（不是报错）⇒ 需要同步改看板。 |
| **守门** | `FavoriteRpcMetricsResultTagTest#metricNamesMatchGo` 钉住 Micrometer 侧的 counter 名。 |
| **状态** | 🟡 **待签字** |

---

## DEV-FAV-RPC-002 · 🟡 **待签字** · Go 侧两处 `module` label 写错，RAG 原样保留

| | 内容 |
|---|---|
| **Go 行为** | `rpc/internal/logic/create_favorite_logic.go:89` 与 `rpc/internal/logic/get_favorites_by_folder_logic.go:68` 都在 **item** 流程里用了 `folderModule`（⇒ 这两条 item 相关的指标被打上 `favorite_folder` 标签）。 |
| **RAG 行为** | **按 Go 原样保留**（同一处 label 错配）。 |
| **为什么接受** | label 是**指标契约的一部分**：改了它，两侧的看板/告警就不可比 ⇒ 属「要改必须两侧同时改 + 迁移看板」的决策，不属「顺手修」。 |
| **什么时候会变成真差异** | 若按 Go 的意图修正（改成 `itemModule`），则**与 Go 不一致** ⇒ 那时本条从「保留」变成「有意偏离」，需反向登记。 |
| **守门** | RAG 侧调用点有注释标出（`FavoriteRpcMetrics` 的 javadoc ③）。 |
| **状态** | 🟡 **待签字** |

---

## DEV-FAV-RPC-003 · 🟡 **待签字** · preflight 的退出码 → 结构化日志（U2）

| | 内容 |
|---|---|
| **Go 行为** | `cmd/fact-legacy-preflight/main.go` 是**独立进程** ⇒ 退出码即门禁：<br>· `0` = clear<br>· `1` = DB 打不开 / 读失败 / report 写失败<br>· `2` = **DATABASE_URL_MISSING**（未配置，`:22-25`）**或** blocked（`!report.Clear()`，`:45-51`） |
| **RAG 行为** | `FavoriteLegacyPreflightRunner` 在 **Spring 进程内**执行 ⇒ **刻意不调 `System.exit`**（那会连带杀掉同一 JVM 里的 gRPC 服务）⇒ 改为结构化日志：`outcome=succeeded` / `not_reconciled` / `failed` + `error_code`。 |
| **⚠️ 两处额外差异（Go 侧原台账未覆盖）** | ① **Go 的 report 是写到 stdout 的 JSON**（`:40` `json.NewEncoder(os.Stdout).Encode(report)`）⇒ 运维能直接 `jq` 取值；**RAG 侧不产出 JSON** ⇒ 只能读日志。<br>② **Go 的「未配置」是 exit 2**（与 blocked 同级），而 RAG 侧「DB 不可用」落 `outcome=failed`（对应 Go 的 exit 1）⇒ **配置缺失与读取失败在两侧落到不同档**。 |
| **为什么接受** | `System.exit` 会杀掉同 JVM 的 gRPC 服务 ⇒ 不可接受；日志形态是 Spring 侧唯一安全的替代。 |
| **什么时候会变成真差异** | CI 门禁若按 Go 的退出码写 ⇒ 在 RAG 上**永远拿不到退出码** ⇒ 必须改成解析日志（`outcome=not_reconciled`）⇒ 门禁脚本需要同步改造。 |
| **其它已知差异** | Go 的 `context.WithTimeout(ctx, 2*time.Minute)`（单次预检最多 2 分钟）在 RAG 侧**未设**（Spring 进程内无法安全中断长事务）⇒ 已在 `FavoriteLegacyPreflightRunner` 的 javadoc 注明。 |
| **状态** | 🟡 **待签字** |

---

## DEV-FAV-RPC-004 · 🟡 **待签字** · 权威读端点的部署形态（U1）

| | 内容 |
|---|---|
| **Go 行为** | `cmd/fact-authority/main.go` 是**独立进程**（`FAVORITE_AUTHORITY_LISTEN` 单独监听）：<br>· `FAVORITE_DATABASE_URL` / `FAVORITE_AUTHORITY_LISTEN` / `FAVORITE_AUTHORITY_TOKEN`（**≥32 字节**）任一不满足 ⇒ **进程启动失败 exit 1**（`:35-37`）<br>· `authority.NewHandler` 在 token <32 时**再拒一次**（`http.go:28-30`）<br>· 启动前 `sqlDB.PingContext(ctx)` 探活（`:53-55`）<br>· `ReadHeaderTimeout: 3s` / `WriteTimeout: 5s`（`:57`）<br>· SIGTERM/SIGINT ⇒ 5s 优雅关闭（`:66-70`） |
| **RAG 行为** | `FavoriteAuthorityEndpointConfig` 整体被 `@ConditionalOnProperty(favorite.fact.authority.enabled=true)` 门禁 ⇒ **默认不注册任何 HTTP 端点**（favorite-rpc 保持「纯 gRPC、零 HTTP 端口」）；配置不完整或 token <32 时 ⇒ **不注册端点 + ERROR 告警，进程照常运行**。 |
| **为什么接受** | Go 的形态是「4 个进程合并成 1 个 JVM」的必然结果（`cmd/fact-dispatch` / `cmd/fact-authority` / `cmd/fact-legacy-preflight` 在 Go 侧都不注册任何 gRPC method）⇒ RAG 侧把「进程边界」降级为「配置项」。**默认关闭 ⇒ 默认零副作用**。 |
| **⚠️ 剩余可观察差异（Go 侧原台账未列全）** | ① **配置不完整时 Go 进程起不来、RAG 照常跑** ⇒ 监控探针看到的是「服务活着但端点不存在」。<br>② **无启动前 DB 探活**（RAG 的 DB 探活时机不同）。<br>③ **HTTP server 的超时值不同**（Go 3s/5s vs Spring Boot 默认）。<br>④ **优雅关闭超时不同**（Go 5s）。 |
| **什么时候会变成真差异** | 开启该开关时（部署形态变更）⇒ ① 最需要留意：下游（BTW）会拿到连接被拒，而不是像 Go 那样整个进程不存在。 |
| **状态** | 🟡 **待签字** |

---

## DEV-FAV-RPC-005 · 🟡 **待签字** · 权威读的单次 lookup 3s 超时未接到 DB 查询（U4）

| | 内容 |
|---|---|
| **Go 行为** | `authority/http.go:44` / `:80`：`ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)` ⇒ **约束的是 DB 查询**（`AuthoritativeFavoriteFact(ctx, …)` 把 ctx 传进 GORM）。慢查询 ⇒ 3s 后 ctx 到期 ⇒ 返回 error ⇒ 走 **503 "source unavailable"** 分支。 |
| **RAG 行为** | `FavoriteAuthorityLookupService` 走 MyBatis-Plus + HikariCP，**没有**把该 deadline 接到连接/查询上 ⇒ 要等到 Hikari 的 `connection-timeout` 或 PG 的 `statement_timeout`。 |
| **可观察差异** | 慢查询下的**响应时长**不同（3s vs 更久）；**功能分支不变**（查不到 → 404 / 出错 → 503）。 |
| **为什么接受（暂记为待签字）** | ⚠️ 这一条**尚未定论**：Go 的 3s 是**单次 lookup 的预算**，RAG 侧更贴近的等价物是「查询超时」而非「连接超时」⇒ 需要先确认 MyBatis-Plus 侧能否把 deadline 传到 SQL 层（`@Options(timeout=…)`）再决定是修还是登记。 |
| **状态** | 🟡 **待签字 · 修法待定** |

---

## 本档其它空位

- `api.md` ⇒ 401 响应 body 形态（DEV-FAV-API-001），见 [`api.md`](./api.md)。
- `common.md` ⇒ OBS 两条（DEV-FAV-COMMON-001 / -002），见 [`common.md`](./common.md)。
