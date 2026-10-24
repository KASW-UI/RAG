<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-rpc`

> **核对时间：2026-10-25**
> ✅ **2026-10-25 用户已签字**：**3 条**（DEV-FAV-RPC-001 / -003 / -004）——
> 「**保留现状，登记为签字偏离**」⇒ 审查时**不必重复质疑**，但**也不得「顺手修回 Go 行为」**。
> ⚠️ **本档有 2 条已撤销**：
> · **DEV-FAV-RPC-002**（2026-10-25 第五轮）—— **前提被逐行核对证伪** ⇒ 它**不是**偏离 ⇒ 撤销；
> · **DEV-FAV-RPC-005**（2026-10-25 第四轮）—— U4 已修 ⇒ 不再是偏离 ⇒ 撤销。
> ⇒ **现存 3 条已签字 + 2 条已撤销**。
> 🏷 **候选，建议签**：**DEV-FAV-RPC-006**（GORM 自动生成的**唯一索引名**）—— 2026-10-25 第六轮新登记
> （随 DDL 逐列对照查出）⇒ **建议签字**，理由见该条。
> ⚠️ 本档的条目分三类：
> · **DEV-FAV-RPC-xxx** —— 已确认「两侧确实不同」且判定「不修回去」的偏离（**已于 2026-10-25 全部签字**）；
> · **🏷 候选** —— 同上，但**尚未签字**（登记即交付物，**不占用拍板额度**）；
> · **撤销记录** —— 原条目**前提被实测证伪** ⇒ 它**不是**偏离，见下方「撤销记录」。

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

## 撤销记录 · DEV-FAV-RPC-005（权威读的单次 lookup 3s 超时未接到 DB 查询）· ❌ **撤销**

| | 内容 |
|---|---|
| **原结论（2026-10-25 第三轮）** | 「Go 的 3s 是**单次 lookup 的预算**，RAG 侧更贴近的等价物是「查询超时」而非「连接超时」⇒ 需要先确认 MyBatis-Plus 侧能否把 deadline 传到 SQL 层再决定是修还是登记」⇒ **待签字 · 修法待定**。 |
| **本轮结论（2026-10-25 第四轮）** | ✅ **已修，不再是偏离** ⇒ 本条**撤销**。<br>① 仓库里**已有**成套机制且本模块**早已注册**：`DeadlineQueryTimeoutInjector`（按线程 deadline 给每条 SQL 设 `setQueryTimeout` ⇒ pgjdbc 发 CancelRequest ⇒ **与服务端语义同一条、连接保可用**）+ `SqlTimeoutStatusTranslator`（把 `57014` 翻成 `DEADLINE_EXCEEDED`）。<br>② 只需在**调用点**（`FavoriteAuthorityEndpointConfig`，与 Go 的绑定点 `http.go:44` 一致）用 `DeadlineState.bindToCurrent(now + 3_000)` 绑上 ⇒ 一次 lookup 的多条 SQL **共用一个 3s 预算** ⇒ 与 Go 的「一个 ctx 覆盖整次 lookup」逐字一致。<br>③ ⚠️ **不是** `@Options(timeout=3)`：那是**每条语句**各给 3s（6 条慢查询能拖到 18s）⇒ **量纲不同**。 |
| **证据** | `FavoriteAuthorityLookupTimeoutTest`（3 例，真 PG Testcontainers + 真 MyBatis + 真 CancelRequest）⇒ **3 例全绿**；<br>**变异**（去掉 `bindToCurrent`）⇒ **1 例转红**，实测 elapsed = **10024ms**（落到安全网的 PG `statement_timeout=10s`，而不是 3s）⇒ **不是假绿**。<br>逐条记录 ⇒ [`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §10.1。 |
| **保留的说明** | ⚠️ **超时值刻意硬编码 3s，不做配置项**：Go 侧是硬编码常量，做成配置就等于允许部署时改掉它 ⇒ 那时两侧行为又不一致了。 |

---

## 撤销记录 · DEV-FAV-RPC-002（Go 侧两处 `module` label 写错，RAG 原样保留）· ❌ **撤销**

| | 内容 |
|---|---|
| **原结论（2026-10-25 第三轮）** | 「Go 侧 `create_favorite_logic.go:89` 与 `get_favorites_by_folder_logic.go:68` 都在 **item** 流程里用了 `folderModule` ⇒ **Go 侧 label 写错**；RAG 侧**按 Go 原样保留** ⇒ 属有意偏离。」 |
| **实测证伪（2026-10-25 第五轮）** | ① **那两行不是 item 流程**，而是<b>收藏夹（folder）查询</b>的失败分支：<br>· `create_favorite_logic.go:79` 是 `FindFolderByFolderId` ⇒ 其失败分支的 `:89` 记 `folderModule` —— <b>正确</b>；<br>· `get_favorites_by_folder_logic.go:58` 是 `FindFolderByFolderId` ⇒ 其失败分支的 `:68` 记 `folderModule` —— <b>正确</b>。<br>② 真正的 <b>item</b> 读失败分支在别处：`get_favorites_by_folder_logic.go:81`（`FindFavoritesByFolderId`）⇒ `:84` 记 `itemModule`；`create_favorite_logic.go:122`（`FindFavoriteByFolderTarget`）⇒ `:130` 记 `itemModule` —— <b>也都正确</b>。<br>③ ⇒ **Go 侧 18 处 `ObserveDBError` 的 label 全部自洽**（label 恒等于被操作对象的模块）。 |
| **RAG 侧核实** | `FavoriteDomainService` 的两处调用点注释与实现**同样自洽**：<br>· `:657-659`「Go: get_favorites_by_folder_logic.go:84 ⇒ `ObserveDBError(**itemModule**…)`，<b>上一行的收藏夹读失败才是 folderModule（Go: :68）—— 两者不可混</b>」—— <b>逐字正确</b>；<br>· `:376-377`「Go: delete_favorite_folder_logic.go:88 ⇒ `ObserveDBError(**folderModule**, "delete", "db")`」—— <b>逐字正确</b>。 |
| **结论** | 🔴 **两侧本来就一致 ⇒ 不存在偏离，无需签字** ⇒ **本条撤销**。<br>⇒ 原登记之所以出错，是把「收藏夹查询失败分支」误读成了「item 流程」—— 即<b>看错了分支</b>，不是 Go 的缺陷。<br>⇒ 同源提醒：本台账 §2 的 D5/D6 早已写明「Go 侧全部自洽，错的是 RAG 侧的映射，已修」—— **本条与 D5/D6 是同一个坑的两次重犯**。 |
| **连带修正** | `FavoriteRpcMetrics` 的 javadoc ③ 原写「两处 `module` label 写错…本实现按 Go 原样保留」⇒ 这是 AGENTS.md §3.3.2 点名的<b>错误「Go: …」断言</b>，已改正为「Go 侧是对的，本实现与 Go 一致」。 |
| **守门** | `FavoriteRpcMetricsResultTagTest`（4 例，运行时断言）+ 本条撤销记录里的行号证据（逐行可复核）。 |

---

## DEV-FAV-RPC-001 · ✅ **已签字** · `favorite_request_seconds_counter` 的 Prometheus 渲染名

| | 内容 |
|---|---|
| **Go 行为** | `metrics.go:22-27` 是 `prometheus.NewCounterVec(CounterOpts{Namespace:"", Subsystem:"favorite", Name:"request_seconds_counter"})` ⇒ Prometheus 渲染为 **`favorite_request_seconds_counter`**（`CounterVec` 的 `Name` 已是终名，**不**追加 `_total`）。 |
| **RAG 行为** | `FavoriteRpcMetrics.observeRpc` 用 Micrometer `Counter.builder("favorite_request_seconds_counter")` ⇒ Micrometer 的 Counter 在 Prometheus 侧**自动追加 `_total`** ⇒ 渲染为 **`favorite_request_seconds_counter_total`**。 |
| **对齐的部分** | ✅ 其余三个指标名**完全一致**：`favorite_request_total` / `favorite_ops_total` / `favorite_db_error_total`（名字已以 `_total` 结尾 ⇒ Micrometer 不再追加）。 |
| **为什么接受** | 改名的代价是**观测断档**（Prometheus 侧 `rate(favorite_request_seconds_counter[5m])` 立刻失效）；而 Go 的这个指标本身是**「累计秒数的计数器」**（`CounterVec.Add(time.Since(started).Seconds())`，不是 Histogram）⇒ 把它换成 Timer 会把语义从「累计」变成「分布」，那是**另一个指标**而不是同一个指标的修正。 |
| **什么时候会变成真差异** | 若看板/告警按 Go 的旧名写 ⇒ 迁到 RAG 后该查询**静默返回空**（不是报错）⇒ 需要同步改看板。 |
| **守门** | `FavoriteRpcMetricsResultTagTest#metricNamesMatchGo` 钉住 Micrometer 侧的 counter 名。 |
| **状态** | ✅ **已签字**（2026-10-25，用户 Jason：**保留现状，登记为签字偏离**）—— **不是缺陷**；改动需**重新征签**<br>⚠️ **签字附带的前置动作**：迁移看板 / 告警时**同步**改用 `favorite_request_seconds_counter_total` ⇒ ⚠️ Prometheus 里改名后是**静默返回空**（不是报错）⇒ 不改就会得到「看起来没数据」的假象。 |

---

## ~~DEV-FAV-RPC-002~~ · ❌ **已撤销**（2026-10-25 第五轮）

> **Go 侧两处 `module` label 写错，RAG 原样保留** ⇒ **前提被证伪，撤销**。
> ⇒ 完整记录见本档上方的「撤销记录 · DEV-FAV-RPC-002」，
> 逐条行号证据见 [`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §10.8。
>
> ⚠️ **别把这条当新回归去「修」**：它**从来不是偏离** ——
> 那两行是**收藏夹查询**的失败分支，`folderModule` 本来就是对的 ⇒ **Go 与 RAG 一直一致**。

---

## DEV-FAV-RPC-003 · ✅ **已签字** · preflight 的退出码 → 结构化日志（U2）

| | 内容 |
|---|---|
| **Go 行为** | `cmd/fact-legacy-preflight/main.go` 是**独立进程** ⇒ 退出码即门禁：<br>· `0` = clear<br>· `1` = DB 打不开 / 读失败 / report 写失败<br>· `2` = **DATABASE_URL_MISSING**（未配置，`:22-25`）**或** blocked（`!report.Clear()`，`:45-51`） |
| **RAG 行为** | `FavoriteLegacyPreflightRunner` 在 **Spring 进程内**执行 ⇒ **刻意不调 `System.exit`**（那会连带杀掉同一 JVM 里的 gRPC 服务）⇒ 改为结构化日志：`outcome=succeeded` / `not_reconciled` / `failed` + `error_code`。 |
| **⚠️ 两处额外差异（Go 侧原台账未覆盖）** | ① **Go 的 report 是写到 stdout 的 JSON**（`:40` `json.NewEncoder(os.Stdout).Encode(report)`）⇒ 运维能直接 `jq` 取值；**RAG 侧不产出 JSON** ⇒ 只能读日志。<br>② **Go 的「未配置」是 exit 2**（与 blocked 同级），而 RAG 侧「DB 不可用」落 `outcome=failed`（对应 Go 的 exit 1）⇒ **配置缺失与读取失败在两侧落到不同档**。 |
| **为什么接受** | `System.exit` 会杀掉同 JVM 的 gRPC 服务 ⇒ 不可接受；日志形态是 Spring 侧唯一安全的替代。 |
| **什么时候会变成真差异** | CI 门禁若按 Go 的退出码写 ⇒ 在 RAG 上**永远拿不到退出码** ⇒ 必须改成解析日志（`outcome=not_reconciled`）⇒ 门禁脚本需要同步改造。 |
| **其它已知差异** | Go 的 `context.WithTimeout(ctx, 2*time.Minute)`（单次预检最多 2 分钟）在 RAG 侧**未设**（Spring 进程内无法安全中断长事务）⇒ 已在 `FavoriteLegacyPreflightRunner` 的 javadoc 注明。 |
| **状态** | ✅ **已签字**（2026-10-25，用户 Jason：**保留现状，登记为签字偏离**）—— **不是缺陷**；改动需**重新征签**<br>⚠️ **强制来源**：`System.exit` 会**杀掉同 JVM 里的 gRPC 服务** ⇒ 物理上不可能照抄 Go 的退出码。<br>⇒ **签字附带的前置动作**：CI 门禁**必须**改为解析日志（`outcome=not_reconciled`）⇒ 按 Go 的退出码写的门禁在 RAG 上**永远拿不到退出码**。 |

---

## DEV-FAV-RPC-004 · ✅ **已签字** · 权威读端点的部署形态（U1）

| | 内容 |
|---|---|
| **Go 行为** | `cmd/fact-authority/main.go` 是**独立进程**（`FAVORITE_AUTHORITY_LISTEN` 单独监听）：<br>· `FAVORITE_DATABASE_URL` / `FAVORITE_AUTHORITY_LISTEN` / `FAVORITE_AUTHORITY_TOKEN`（**≥32 字节**）任一不满足 ⇒ **进程启动失败 exit 1**（`:35-37`）<br>· `authority.NewHandler` 在 token <32 时**再拒一次**（`http.go:28-30`）<br>· 启动前 `sqlDB.PingContext(ctx)` 探活（`:53-55`）<br>· `ReadHeaderTimeout: 3s` / `WriteTimeout: 5s`（`:57`）<br>· SIGTERM/SIGINT ⇒ 5s 优雅关闭（`:66-70`） |
| **RAG 行为** | `FavoriteAuthorityEndpointConfig` 整体被 `@ConditionalOnProperty(favorite.fact.authority.enabled=true)` 门禁 ⇒ **默认不注册任何 HTTP 端点**（favorite-rpc 保持「纯 gRPC、零 HTTP 端口」）；配置不完整或 token <32 时 ⇒ **不注册端点 + ERROR 告警，进程照常运行**。 |
| **为什么接受** | Go 的形态是「4 个进程合并成 1 个 JVM」的必然结果（`cmd/fact-dispatch` / `cmd/fact-authority` / `cmd/fact-legacy-preflight` 在 Go 侧都不注册任何 gRPC method）⇒ RAG 侧把「进程边界」降级为「配置项」。**默认关闭 ⇒ 默认零副作用**。 |
| **⚠️ 剩余可观察差异（Go 侧原台账未列全）** | ① **配置不完整时 Go 进程起不来、RAG 照常跑** ⇒ 监控探针看到的是「服务活着但端点不存在」。<br>② **无启动前 DB 探活**（RAG 的 DB 探活时机不同）。<br>③ **HTTP server 的超时值不同**（Go 3s/5s vs Spring Boot 默认）。<br>④ **优雅关闭超时不同**（Go 5s）。 |
| **什么时候会变成真差异** | 开启该开关时（部署形态变更）⇒ ① 最需要留意：下游（BTW）会拿到连接被拒，而不是像 Go 那样整个进程不存在。 |
| **状态** | ✅ **已签字**（2026-10-25，用户 Jason：**保留现状，登记为签字偏离**）—— **不是缺陷**；改动需**重新征签**<br>⚠️ **强制来源**：「Go 的 4 个进程合并成 1 个 JVM」是**已拍板**的架构决策 ⇒ 进程边界降级为配置项是必然结果；且**默认关闭 ⇒ 默认零副作用**。<br>⇒ **签字附带的前置动作**：**开启该开关之前**必须补「就绪探针」⇒ 否则监控只会看到「进程活着但端点不存在」，下游（BTW）拿到的是**连接被拒**。 |

---

## DEV-FAV-RPC-006 · 🏷 **候选，建议签** · GORM 自动生成的**唯一索引名**

| | 内容 |
|---|---|
| **Go 行为** | `favorite_folder.folder_id` 与 `favorite_item.favorite_id` 上的唯一索引，名字由 **GORM 的命名策略**决定 ⇒ **`idx_favorite_folder_folder_id`** / **`idx_favorite_item_favorite_id`**。<br>依据：`gorm.io/gorm@v1.31.1/schema/index.go:119-134` —— 未命名的 `uniqueIndex` 走 `namer.IndexName(table, column)` ⇒ `idx_<table>_<column>`（**与是否 UNIQUE 无关**；`gorm.io/gorm@v1.31.1/schema/naming.go:89-90`）。 |
| **RAG 行为** | 同两个唯一索引命名为 **`uk_favorite_folder_folder_id`** / **`uk_favorite_item_favorite_id`**（`V1__create_favorite_folder.sql:33`、`V2__create_favorite_item.sql:53`）。 |
| **实测 / 核对** | 2026-10-25 第六轮 **DDL 逐列对照**（[`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §12.2）⇒ **4 张表列级零差异**；本条是**唯一**的差异项 ⇒ **列集合相同、唯一性相同、约束语义相同 ⇒ 行为等价**。 |
| **为什么接受** | ① **纯标识符差异** ⇒ 约束的**语义**（唯一性、列集合、冲突行为）完全相同；<br>② RAG 侧按 **SQLSTATE 23505** 翻译错误码（`AlreadyExists/5302`、`AlreadyExists/5203`）⇒ **不看约束名** ⇒ 错误码**不受影响**；<br>③ 命名 `uk_*` 比 GORM 的 `idx_*` **更可读**（能一眼看出是唯一索引）⇒ 保留。 |
| **什么时候会变成真差异** | 若将来**统一**索引名（例如从 Go 库迁到 RAG 库、或反向）⇒ PG 目录里的索引标识符会变 ⇒ **任何按名字 DROP/重建索引的运维脚本会失效** ⇒ 那时才是行为差异（且是**部署脚本**层，不是业务层）。<br>⚠️ 本仓**两侧都没有**按名字操作这两个索引的脚本 ⇒ 当前无影响。 |
| **守门** | 逐列对照（§12.2）只断言**列集合 + 唯一性**，**不**断言索引名 ⇒ 本条不会因改名而转红 ⇒ 但也**不会被静默「顺手统一」**（本条已登记 ⇒ 统一前需重评）。 |
| **状态** | 🏷 **候选，建议签**（2026-10-25 第六轮新登记）—— **不是缺陷**；改动需**重新征签**。<br>⚠️ **登记的前提已逐行核对**：GORM 侧索引名**不是**猜的，是读 `gorm.io/gorm@v1.31.1` 与 `gorm.io/driver/postgres@v1.6.0` 的源码得出的 ⇒ 前提成立。 |

---

## ~~DEV-FAV-RPC-005~~ · ❌ **已撤销**（2026-10-25 第四轮）

> **权威读的单次 lookup 3s 超时未接到 DB 查询** ⇒ **已修，不再是偏离**。
> ⇒ 完整记录见本档顶部的「撤销记录 · DEV-FAV-RPC-005」，
> 逐条证据见 [`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §10.1。
>
> ⚠️ **别把这条当新回归去「修」**：它现在**已经与 Go 一致**了。

---

## 本档其它空位

- `api.md` ⇒ 401 响应 body 形态（DEV-FAV-API-001），见 [`api.md`](./api.md)。
- `common.md` ⇒ OBS 两条（DEV-FAV-COMMON-001 / -002），见 [`common.md`](./common.md)。
