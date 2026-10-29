# `hot-api` 有意偏离台账

> **范围**：`service/hot/hot-api`（Go `service/hot/api/`）。
> **归属**：`align/hot` 工作树产出（`RAG.worktrees/hot`）；基线 = `do-my-own-gh` @ `8422116` 之后的分支点。
> **核对日期**：2026-10-24（**第十一轮回填**：新增 `SD-HOT-API-010`；第六轮 = 017 / 019 / 020 / 024 + D6 共 5 条签字）。
> **差异本体**（未签字的缺陷）在 [`../../DRIFT/hot/api.md`](../../DRIFT/hot/api.md)。
> 本文件只登记**明知与 Go 不同、经签字批准**的偏离。
>
> ⚠️ **读法**：对拍看到这里登记的条目变红，**先确认是不是这一条**，不要当新回归去「修」。
>
> 🟥 **2026-10-24 第十一轮回填**：下方汇总表此前**只列到 `SD-HOT-API-009`**，
> 而 `SD-HOT-API-010`（补全的跨请求并发总上限）**早已在第十轮签字、正文也在** ⇒ **汇总表漏行**，
> 对拍时按汇总表办事会**漏掉这条已签字偏离** ⇒ 本次补上。

## 🆕 2026-10-29 逐条重核结论（台账对齐 phase · W-E lane）

> **判定口径**：`docs/Intentional_Deviations/_INDEX.md:34` ——「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效** ⇒ 不计入计数」。
> **方法**：**回 Go 真源 + 回 RAG 源码两侧核**（真源 = `Sea-RideTheWind_golang`，冻结只读；go-zero 取 `v1.10.2`）。
> **撞号检查**：本域 `SD-HOT-API-001..010` **全域唯一**（`grep -rn "SD-HOT-API-" docs/Intentional_Deviations/` ⇒ 每号只对应**一条**偏离；`hot/common.md` 的引用指向同一条）⇒ **无撞号** ⇒ **不需要** `SD-<编号>@<域>` 别名。

| ID | 结论 | 证据（核对日期 2026-10-29） |
|---|---|---|
| `SD-HOT-API-001` | ✅ **仍成立** | Go 侧随机性仍在：`rest/httpx/util.go:36` 的 `for name, values := range r.Form` 遍历 **map**（顺序随机）+ `:59-61` 剥 `[]` 后缀写进同一个 key ⇒ 谁覆盖谁随机。RAG 侧仍是**取确定值**：`service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:200-215`（先查精确名，再查 `name[]`，取**首个非空**）⇒ 差异仍存在<br>🔴 **2026-10-29 真实性复核 —— 上行那两个 Go 行号各差 1（原文保留，不改写）**：go-zero v1.10.2 实测 ⇒ `for name, values := range r.Form` 在 **`:35`**（`:36` 是 `filtered := make(...)`）；剥 `[]` 后缀并写回同一 key 在 **`:57-60`**（`:57` HasSuffix → `:58` `name = name[:len(name)-2]` → `:60` `params[name] = filtered`）⇒ ⇒ **语义断言逐条正确、签字仍成立**，但**指针应读作 `:35` / `:57-60`**（E68 同型：行号合法但指向错行）。**核对日期 2026-10-29**。 |
| `SD-HOT-API-002` | ✅ **仍成立** | RAG 侧仍**不**把 Servlet 断连转成 gRPC 取消：断连信号仍只体现为「2s deadline 到点结束」（`docs/DRIFT/hot/api.md` 的 **D3** 行 + `DRIFT-HOTAPI-025`）⇒ 「少一个取消源」的形态未变 |
| `SD-HOT-API-003` | ✅ **仍成立** | Go 的 `defer` 读**命名返回值** `err` ⇒ panic 展开时仍为 nil ⇒ 指标仍被记 `success`；RAG 侧仍按客户端**实际看到的结果**记 `fail` ⇒ 差异未变（且该路径仍无代码可达） |
| `SD-HOT-API-004` | ⛔ **已失效（签字描述的行为已不存在）** ⇒ **应从计数中剔除** | 签字描述的是「**Go 注册了 5 个 collector 但无端点暴露** ⇒ RAG 单向增强」。**实测：该不对称不存在** —— Go 侧**确实暴露** `/metrics`：<br>① `service/hot/api/hotcenter.go:41` `rest.MustNewServer(c.RestConf)`；<br>② go-zero v1.10.2 `rest/server.go:48-50` `NewServer` → `c.SetUp()`；<br>③ `rest/config.go:45-46` `RestConf` 内嵌 `service.ServiceConf`；<br>④ `core/service/serviceconf.go:40` `DevServer DevServerConfig json:",optional"` + `:29` `DevServerConfig = devserver.Config` + `:77` `devserver.StartAgent(sc.DevServer)`；<br>⑤ `internal/devserver/config.go` `Enabled(default=true)` / `MetricsPath(default=/metrics)` / `EnableMetrics(default=true)`，`internal/devserver/server.go:45-48` `if s.config.EnableMetrics { prometheus.Enable(); s.handleFunc(s.config.MetricsPath, promhttp.Handler().ServeHTTP) }`；<br>⑥ Go 真源 `service/hot/api/etc/hotcenter.yaml` 的 `DevServer: {Enabled: true, Host: 0.0.0.0, Port: 16067, MetricsPath: /metrics, EnableMetrics: true}` **被解析**；<br>⑦ 被暴露的正是 `promhttp.Handler()`（**默认 registry**）⇒ 正是 `service/hot/api/internal/metrics/metrics.go:47-51` 用 `client_golang` 的 `prometheus.MustRegister(...)` 注册的那 5 个 `hot_api_*`。<br>⇒ ⇒ **Go 侧 `http://0.0.0.0:16067/metrics` 有这 5 个指标** ⇒ **「RAG 单向增强」这个被签字的行为已不存在** ⇒ 签字自动失效。<br>⚠️ **签字理由的表述也随之不成立**（见下条目的订正注记） |
| `SD-HOT-API-005` | ✅ **仍成立** | RAG 侧仍记 grpc-java 形态：`service/common/src/main/java/com/rag/common/logger/Logger.java` 的 `error_reason` 取 `err.getMessage()`；Go 侧 `service/common/logger/logger.go:118-123` 记 `err.Error()`（grpc-go 形态 `"rpc error: code = Unavailable desc = …"`）⇒ 形态差异未变 |
| `SD-HOT-API-006` | ⛔ **已失效（2026-10-25 已撤销 · 本轮复核确认）** ⇒ **不计入计数** | 复核确认原判：RAG 侧 JSON 响应**现在带** `charset=utf-8` ⇒ `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java`（`@AutoConfiguration`，已登记进 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` ⇒ **10 个 api 全部生效**）写 `application/json;charset=utf-8`（`:127` `GO_JSON_CONTENT_TYPE` / `:151` `response.getHeaders().set(...)`）⇒ **被签字描述的那个行为（「不带 charset」）已不存在** ⇒ 维持撤销 |
| `SD-HOT-API-007` | ✅ **仍成立** ＋ ⚠️ **描述需订正** | 被签字的行为仍在：`service/hot/hot-api/src/main/resources/application.yml:182-183` `management.metrics.tags.application: hot-api` ⇒ RAG 侧 Prometheus 指标**带** `application="hot-api"`（Go 侧默认 registry 无此标签）。<br>⚠️ **但签字理由里的一个前提已过期**（原句保留在正文，见该条目的「订正注记」）：**「全仓统一配了同一项」已不成立** —— 实测 8 个 api 模块里**只有 5 个**配了 `management.metrics.tags.application`（`article-api` / `comment-api` / `favorite-api`（`${spring.application.name}`）/ `hot-api` / **`message-api`**），`follow-api` / `like-api` / `task-api` **未配** ⇒ hot-api 去掉该标签将变成 **3 个例外之一**，而非「唯一例外」（⚠️ **主线二次订正**：本条原写「4 个 / `message-api` 未配 / 5 个例外」⇒ 有误 ⇒ 详见下方「订正注记」）<br>📌 **2026-10-29 真实性复核 · 口径补全（不改写上面那句）**：上面那句数的是**「8 个非 users 的 api 模块」** ⇒ **若把 users 域的两个 api 一并计入**，实测（逐模块看 `management:` → `metrics:` → `tags:` → `application:` 这个**嵌套**块；⚠️ **不能用 `grep 'management.metrics.tags.application'` 点号路径** —— YAML 是嵌套写法 ⇒ 该串在任何模块里都不出现 ⇒ 会误得「0 个全没配」）**全仓 10 个 api 模块里 7 个配了 / 3 个没配**：`article-api`（`:225`）/ `comment-api`（`:173`）/ `favorite-api`（`:195`）/ `hot-api`（`:183`）/ **`message-api`（`:213`）** / `users/user`（`:187`）/ `users/admin`（`:178`）配了；**`follow-api` / `like-api` / `task-api` 没配**。<br>⇒ ⇒ **签字的行为仍成立**（RAG 侧每个指标仍带 `application="hot-api"`，Go 侧默认 registry 仍不带全局标签）⇒ **维持签字、不撤销**；只是**「全仓统一」这个理由的强度下降** ⇒ **3 个例外之一**这一格无论按哪个口径都成立。**核对日期 2026-10-29** |
| `SD-HOT-API-008` | ✅ **仍成立** | RAG 侧仍注册到 Nacos：`service/hot/hot-api/src/main/resources/application.yml:22-26`（`spring.cloud.nacos.discovery.server-addr` / `namespace` / `group`）+ `HotApiApplication` 的 `@EnableDiscoveryClient` ⇒ Go 侧 hot-api 仍**完全不注册** ⇒ 差异未变 |
| `SD-HOT-API-009` | ✅ **仍成立** | `service/hot/hot-api/src/main/resources/application.yml:34` 仍为 `port: ${HOT_API_PORT:18897}`（Go `etc/hotcenter.yaml` 的 `Port: 8893`）⇒ 端口差异未变 |
| `SD-HOT-API-010` | ✅ **仍成立** | RAG 侧仍保留跨请求总上限：`service/hot/hot-api/src/main/java/com/rag/hot/hot/api/config/HotHydrationExecutorConfiguration.java:65` `POOL_SIZE = 64`（+ 队列 64）⇒ Go 侧无此封顶 ⇒ 差异未变 |
| `SD-2026-10-25-CAND-10` | ⏳ **仍是未签字候选**（不是签字偏离，不计入「已签字」计数） | 未签字候选，超长请求行（Go 431 + `text/plain` vs RAG 400 + Tomcat HTML）⇒ 本轮**不**升级为签字项（登记即交付物） |

> ⚠️ **计数影响（由主线更新 `docs/Intentional_Deviations/_INDEX.md`，本 lane 不写共享索引）**：
> `SD-HOT-API-004` **新增**一条「已失效」⇒ `SD-` 族「已签字合计」应 **再减 1**（原 40 条中已含 `SD-HOT-API-006` 的那次减 1）。

| ID | 一句话 | 方向 | 状态 |
|---|---|---|---|
| **SD-HOT-API-001** | `?page=1&page[]=2`（同名 + `[]` 后缀混用）取**确定值**，而 Go 在两个值之间**随机** | RAG 更确定 | ✅ 已签字（2026-10-23） |
| **SD-HOT-API-002** | 客户端**断连**的取消信号不再向 100 次补全传播（超时语义已保住） | RAG 少一个取消源 | ✅ 已留痕（2026-10-23，第二轮） |
| **SD-HOT-API-003** | Go panic 时指标被误记为 `success`；RAG 按客户端实际看到的结果记 `fail` | RAG 更准 | ✅ 已留痕（2026-10-23，第二轮；该路径无代码可达） |
| **SD-HOT-API-004** | 5 个 `hot_api_*` 指标**真的被暴露**（Go 注册了但无端点暴露） | RAG 单向增强 | ⛔ **已失效（2026-10-29 重核）** —— 被签字描述的行为**已不存在**（Go 侧 `DevServer` 确实起 `/metrics`）⇒ **不计入计数**（正文见该条目的订正注记） |
| **SD-HOT-API-005** | 下游 gRPC 失败时，日志 `error_reason` 用 **grpc-java** 的形态（`"UNAVAILABLE: …"`）而非 grpc-go 的（`"rpc error: code = Unavailable desc = …"`） | 库层形态差异 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「不复刻」） |
| **SD-HOT-API-006** | 成功路径 `Content-Type` **不带** `charset=utf-8` 参数（Go 带） | 库/框架形态差异 | ⛔ **已撤销**（2026-10-25，用户拍板撤销）⇒ **被签字描述的那个行为已不存在**（第二十五轮 global T-6 已修：RAG 侧现在带 `charset=utf-8`） |
| **SD-HOT-API-007** | Prometheus 指标带全局标签 `application="hot-api"`（Go 的默认 registry 无此标签） | 全仓约定 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」）⚠️ **2026-10-29 订正**：签字**仍有效**，但「全仓统一配了同一项」的前提已过期（8 个 api 里**只有 5 个**配了 —— 含 `message-api`；未配的是 `follow-api` / `like-api` / `task-api`） |
| **SD-HOT-API-008** | hot-api **注册到 Nacos**（Go 侧 hot-api 完全不注册） | 部署面 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」） |
| **SD-HOT-API-009** | hot-api 的 HTTP 端口取 **18897**（Go 是 8893） | 部署参数 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」） |
| **SD-HOT-API-010** | 补全的**跨请求**并发总上限（RAG 有 64 的封顶，Go **没有**） | **RAG 更保守**（保护下游） | ✅ **已签字**（2026-10-24 第十轮，用户拍板「方案 A：池 64 + 登记为签字偏离」） |

---

## SD-HOT-API-001 · 重复键混用时取确定值（Go 侧随机） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：`GET /hot/v1/articles` 的 query 解析
- **Go 出处**：
  - `rest/httpx/util.go:59-61` — 名字带 `[]` 后缀的**剥掉后缀**再写进 `params[name]`；
    ⇒ `?page=1&page[]=2` 时，**两个 key 写的是同一个 `params["page"]`**，后者**覆盖**前者；
  - `rest/httpx/util.go:36` — `for name, values := range r.Form` ⇒ **map 遍历序随机**
    ⇒ 谁覆盖谁是**随机的**。
  > 🔴 **2026-10-29 真实性复核 —— 上面两个行号各差 1（原值保留，不改写）**：
  > 本机实测（go-zero **v1.10.2**，真源 `go.mod:17` pin；文件 `~/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2/rest/httpx/util.go`）⇒
  > **① `for name, values := range r.Form` 在 `:35`**（`:36` 是 `filtered := make([]string, 0, len(values))`）；
  > **② 剥 `[]` 后缀 + 写回同一个 key 在 `:57-60`**（`:57` `if strings.HasSuffix(name, arraySuffix)` → `:58` `name = name[:len(name)-2]` → `:60` `params[name] = filtered`）；
  > ⇒ **语义断言本身逐条正确**（map 遍历序随机 ⇒ 谁覆盖谁随机；`[]` 后缀被剥掉 ⇒ 两个 key 写同一个 `params["page"]`）⇒ **签字仍成立**；
  > ⇒ ⚠️ **但指针必须改对** —— 「行号合法、指向错行」是 E68 同型（**指针能解析 ≠ 结论为真**）⇒ ⇒ **本条的行号应读作 `:35` 与 `:57-60`**。
  > ⇒ 📌 **旁证（同一文件的另外两个真锚点，供后续引用照抄）**：`:15` `maxFormParamCount = 2048`；`:33` `var n int`（**在 name 循环之外** ⇒ 计数器**跨所有 key 共享**）。
  > 🔴 **2026-10-29 附带发现：同一个错行号已经进了【产品代码注释】（本 lane 不改代码 ⇒ 只登记）** ——
  > `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:129` 与 `:203` 两处「Go: …」注释写的也是 `httpx/util.go:59-61` ⇒ **同一个差 1 的行号**。
  > ⇒ **语义断言本身正确**（`?page[]=5` 确实被剥掉 `[]` 后缀归到 key `page`）⇒ **只是行号指向了 `:59`–`:61`（`}` / `params[name] = filtered` / `}`）而不是真正的剥后缀那一行 `:57-58`**。
  > ⇒ ⚠️ **AGENTS.md §3.3.2 的形态**（「以「Go: …」措辞写出的断言本身是高危项」）⇒ **改法 = 把这两处注释的 `:59-61` 改成 `:57-60`** ⇒ **但 `service/**` 是产品代码、本 phase 的写权边界只在 `docs/**`** ⇒ ⇒ **不擅自改** ⇒ **登记在此，交由 hot 域的落码轮处理**。
  > ⇒ 📌 **同文件里另外两条「Go: …」注释是对的**（可作对照）：`:210` `Go: httpx/util.go:44-45`（空值被丢弃 ⇒ **正确**）· `:204` 的 `name[]` 查询逻辑与 Go 的 `params[name]` 一一对应 ⇒ **正确**。
- **实测（go-zero v1.10.2 活体探针，同一 URL 连跑 8 次）**：`page=2` 6 次、`page=1` 2 次
  ⇒ **Go 自己的行为是非确定性的**。
- **RAG 形态**：`GoFormValues.firstFormValue` 先查**精确名** `page`，查不到再查 `page[]`
  ⇒ 固定返回 `page=1`。
- **签字理由**：RAG 取的值**落在 Go 的行为包络内**（Go 的两个可能取值之一），
  而**复刻 Go 的随机性**没有任何价值、且会让对拍无法进行。
- **⚠️ 边界**：本条**只**覆盖「同名 + `[]` 后缀**混用**」这一种输入。
  纯重复（`?page=5&page=3`）与纯数组记法（`?page[]=5&page[]=9`）两侧都是**取首值**，**完全一致**。
- **留痕位置**：`GoFormValues.firstFormValue` 的类注释 + `docs/DRIFT/hot/api.md` DRIFT-HOTAPI-011。

---

## SD-HOT-API-002 ✅ 已留痕 · 客户端**断连**的取消信号不再向 100 次补全传播 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> 🟥 **2026-10-24 第十一轮回填**：本条原先**只有汇总表一行、正文缺章节** ⇒ 已补齐。

- **Component**：`GET /hot/v1/articles` 的补全阶段，**客户端断连**时的取消传播
- **Go 出处**：Go 把**请求 ctx** 传进 100 次补全的每一次调用 ⇒ 客户端断连 ⇒ ctx 被取消 ⇒ **在飞的 gRPC 立即取消**
- **RAG 形态**：Servlet 断连**不会自动取消**在飞的 gRPC；RAG 用「请求线程取一次预算 + 显式下传」保住了**超时**语义，
  但**断连**语义不等价 ⇒ 🔴 第七轮**活体坐实**（裸 socket + `SO_LINGER=0` 强制 RST）：
  Go 侧断连后 **1~3ms** 内 8 个在飞 gRPC **全部取消**；RAG 侧**要等到 2s deadline** 才结束，**且断连后继续发第二批**下游调用
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 **D3** 行 + **DRIFT-HOTAPI-025**（⚠️ 后者代码在 `service/common` ⇒ **跨域**，
  hot 域内不可修）

---

## SD-HOT-API-003 ✅ 已留痕 · Go panic 时指标被误记为 `success`，RAG 按客户端实际看到的结果记 `fail` ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> 🟥 **2026-10-24 第十一轮回填**：本条原先**只有汇总表一行、正文缺章节** ⇒ 已补齐。

- **Component**：`hot_api_request_total` 的结果标签（`success` / `fail`）
- **Go 出处**：Go 的 `defer` 读的是**命名返回值** `err` ⇒ panic 展开时该变量**仍为 nil** ⇒ 指标被记成 `success`
- **RAG 形态**：Java 按客户端**实际看到的结果**记 `fail`
- **为何接受**：**该路径无代码可达**（panic 是不可恢复的进程级事件，客户端根本收不到响应）⇒ 差异**无可观察后果**
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 **D4** 行

---

## SD-HOT-API-004 ⛔ **已失效（2026-10-29 重核 · 签字描述的行为已不存在）** · 5 个 `hot_api_*` 指标**真的被暴露**（Go 注册了但无端点暴露） ｜ **状态（核对日期 2026-10-29）：⛔ 已失效·已撤销**

> 🟥 **2026-10-24 第十一轮回填**：本条原先**只有汇总表一行、正文缺章节** ⇒ 已补齐。

> ### 🔴 2026-10-29 订正注记（台账对齐 phase · W-E lane）—— **本条签字自动失效**
>
> **判定依据**：`docs/Intentional_Deviations/_INDEX.md:34`「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效** ⇒ 不计入计数」。
>
> **被签字描述的那个行为 = 「Go 注册了 5 个 collector 但**无端点暴露** ⇒ RAG 是单向增强」**。
> **实测（2026-10-29 · 回 Go 真源 + 回 go-zero v1.10.2 源码两侧核）**：**该行为不存在** —— **Go 侧确实暴露 `/metrics`**：
>
> | # | 证据（文件:行号） | 事实 |
> |---|---|---|
> | ① | Go 真源 `service/hot/api/hotcenter.go:41` | `rest.MustNewServer(c.RestConf)` ⇒ 走 `rest` 的构造路径 |
> | ② | go-zero v1.10.2 `rest/server.go:48-50` | `NewServer` 第一件事就是 `c.SetUp()` ⇒ 一定会执行 `ServiceConf.SetUp()` |
> | ③ | go-zero v1.10.2 `rest/config.go:45-46` | `RestConf` **内嵌 `service.ServiceConf`** ⇒ `DevServer` 字段**被解析** |
> | ④ | go-zero v1.10.2 `core/service/serviceconf.go:29` / `:40` / `:77` | `DevServerConfig = devserver.Config`；`DevServer DevServerConfig json:",optional"`；`SetUp()` 里 `devserver.StartAgent(sc.DevServer)` |
> | ⑤ | go-zero v1.10.2 `internal/devserver/config.go` | `Enabled bool json:",default=true"` / `MetricsPath string json:",default=/metrics"` / `EnableMetrics bool json:",default=true"` |
> | ⑥ | go-zero v1.10.2 `internal/devserver/server.go:45-48` | `if s.config.EnableMetrics { prometheus.Enable(); s.handleFunc(s.config.MetricsPath, promhttp.Handler().ServeHTTP) }` ⇒ **默认 registry** 的 `/metrics` 端点被挂上 |
> | ⑦ | Go 真源 `service/hot/api/etc/hotcenter.yaml` 的 `DevServer:` 块 | `Enabled: true` / `Host: 0.0.0.0` / `Port: 16067` / `MetricsPath: /metrics` / `EnableMetrics: true` ⇒ **被 ①~⑥ 真实消费** |
> | ⑧ | Go 真源 `service/hot/api/internal/metrics/metrics.go:3` / `:47-51` | 用的是 `github.com/prometheus/client_golang/prometheus`（**默认 registry**）的 `prometheus.MustRegister(...)` ⇒ **正是 ⑥ 的 `promhttp.Handler()` 所服务的那个 registry** |
>
> ⇒ ⇒ **Go 的 hot-api 在 `http://0.0.0.0:16067/metrics` 上确实暴露这 5 个 `hot_api_*` 指标**
> ⇒ ⇒ **「RAG 单向增强、Go 侧看不到」这个被签字的行为已不存在** ⇒ **本条签字自动失效** ⇒ **应从「已签字合计」中剔除**（**计数由主线更新 `docs/Intentional_Deviations/_INDEX.md`** ⇒ 本 lane 不写共享索引）。
>
> ⚠️ **原签字理由的表述也不成立（原文保留，不改写）**：上面第 105-106 行写「`DevServer` 块在 go-zero v1.10.2 **无对应字段**」
> ⇒ **该句是错的**：`DevServer` **确实存在**（`core/service/serviceconf.go:40`），而且 `rest.RestConf` **内嵌**了含它的 `service.ServiceConf`
> ⇒ ⇒ 真正的原因是**字段存在、且被 `SetUp()` 真实消费** ⇒ **Go 侧有端点**。
> ⇒ ⚠️ 因此原签字理由「这是**单向增强**（RAG 多一个观察面，Go 侧看不到）」**整条不成立**。
>
> ⚠️ **本条留下的、仍然成立的残余差异（**不是**本条签字的内容 ⇒ **不新签、不计数**）**：
> **暴露面不同源** —— Go = `0.0.0.0:16067` 的**独立 dev server** 的 `/metrics`（+ `/healthz` + `/debug/pprof/`）；
> RAG = `18897` **同一个端口**的 `/actuator/prometheus`（Actuator 端点）。
> ⇒ 即便指标本身两侧都可见，**抓取地址 / 端口 / 是否与业务端口同源** 仍不同 ⇒ 这是一条**新的**观察面差异，
> ⚠️ **本轮不登记为签字偏离**（登记需先复核两侧是否真的「都是 12 个 `le` 边界」之外还有别的语义差）⇒ **归 `docs/DRIFT/hot/api.md` 的待办**，不计入本档。

- **Component**：`hot_api_*` 指标的**暴露面**（是否有 `/actuator/prometheus` 端点）
- **Go 出处**：Go 侧 5 个 collector 全部 `prometheus.MustRegister(...)` 注册到**默认 registry**，
  但 `hotcenter.yaml` 的 Prometheus 块**无 `Host`**、`DevServer` 块在 go-zero v1.10.2 **无对应字段**
  ⇒ **注册了但没有端点暴露**
- **RAG 形态**：Spring Boot Actuator **真的暴露** `/actuator/prometheus` ⇒ 指标**可被抓取**
- **为何接受**：这是**单向增强**（RAG 多一个观察面，Go 侧看不到）⇒ **不改任何指标数值、不改任何业务判定**
- **⚠️ 边界**：本条**只**管「有没有端点」；**分桶边界的数值差异**是另一条（`DRIFT-HOTAPI-018`，**已修** ⇒ 两侧都是 12 个 `le` 边界）
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 **D5** 行

---

## SD-HOT-API-005 ✅ 已签字 · 下游 gRPC 失败的日志串用 grpc-java 形态 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：hot-api 业务错误日志的 `error_reason` 字段，在**下游 gRPC 调用失败**时的形态
- **Go 出处**：
  - `service/hot/api/internal/logic/hot/getHotArticlesLogic.go:67` ——
    `logger.LogBusinessErr(l.ctx, hotcommon.ErrorServerCommon, rpcErr)`，
    记的是**下游 gRPC error 本身**（不是 `*CodeError`）；
  - `service/common/logger/logger.go:118-123` —— `errorReason = err.Error()`；
  - ⇒ `error_reason` = **grpc-go** 的 `(*status.Error).Error()` 形态。
- **RAG 形态**：记的是 grpc-java 的 `StatusRuntimeException`
  ⇒ `error_reason` = `err.getMessage()`（`service/common/logger/Logger.java:241-245`）
  = **grpc-java** 的形态。
- **两侧实测**（Go 探针复用真源 `go.mod`/`go.sum` 离线构建；grpc-java 直跑）：

  | | `error_reason` 实际值 | gRPC code 名 |
  |---|---|---|
  | Go（grpc-go） | `"rpc error: code = Unavailable desc = boom"` | `Unavailable`（CamelCase） |
  | RAG（grpc-java） | `"UNAVAILABLE: boom"` | `UNAVAILABLE`（全大写） |

- **签字理由（已批准）**：
  1. 形态由**两个 gRPC 库各自决定**，不是 hot 域的代码 ⇒ 复刻要在 hot-api **手拼**前缀并维护
     **code 名大小写映射表** ⇒ **在错误的层**（hot 域凭什么知道 gRPC 错误串的格式？）；
  2. 与 `SD-HOT-RPC-002`（Redis 重试不复刻）**同一个判断**：把库行为搬进业务层，比差异本身更坏；
  3. **可观察影响极小**：`error_reason` 是**日志自由文本**，码值在两侧都另有结构化 `error_code` 字段承载
     （Go `logger.go:152` / RAG `Logger.java:277`）⇒ 语义信息不丢。
- **⚠️ 与 `DRIFT-HOTAPI-016` 的分界**：`016` 是**hot 域自己写的**格式（`errmsg.go:15-17`）⇒ 该修、已修；
  本条是**库写的**格式 ⇒ 不该由 hot 域修。
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 DRIFT-HOTAPI-017 + `HotArticleQueryService.fatal` 的 javadoc。

---

## SD-HOT-API-006 ⛔ 已撤销（2026-10-25）· `Content-Type` 的 `charset=utf-8` 参数 ｜ **状态（核对日期 2026-10-29）：⛔ 已失效·已撤销**

### 撤销记录（2026-10-25 · 第二十五轮 · global T-6）

| 项 | 内容 |
|---|---|
| **撤销人** | 用户（**签字权在用户**；AGENTS.md §5.3b） |
| **撤销依据** | 用户拍板：「撤销 SD-HOT-API-006，修 T-6」⇒ 理由是**向 Go 靠拢**（Go 的 JSON 响应带 `charset=utf-8`） |
| **实施** | `service/common` 新增 `GoStyleJsonContentTypeAdvice`（`@AutoConfiguration` ⇒ **10 个 api 全部生效**，零 api 代码改动）<br>⇒ RAG 侧 JSON 响应头现在带 `charset=utf-8` ⇒ **被本条签字描述的那个行为（「不带 charset」）已不存在** ⇒ 本条签字**自动失效** |
| **守门** | `service/common` 的 `GoStyleJsonContentTypeCharsetTomcatTest`（**真 Tomcat + 裸 socket**，3 条）<br>⇒ 变异 2/2 全红（M8 不写头 ⇒ 两个 JSON 门红；M9 无条件写 ⇒ 「不误伤」门红） |

#### 🔴 顺带**订正**一处此前的「理由已被证伪」判断（global 台账写错了）

global 台账（`common-tails.md` §一·T-6）原写：

> ⚠️ **该理由已被本仓自己证伪**：`GoStyleRouterErrorResponses.java:81-88` 对 `text/plain; charset=utf-8`
> 就是**直接写原始字符串** ⇒ **同一手法用在 `application/json; charset=utf-8` 上即可**
> ⇒ ⇒ **要修 T-6，就必须先撤销 `SD-HOT-API-006`**

⇒ ⇒ **这个判断不成立**（2026-10-25 第二十五轮实测订正）：**写原始字符串并不管用** ——
真 Tomcat 11 会把 Content-Type **拆成 (type/subtype, charset) 两份存**、写出去时**重新拼**：

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

⇒ ⇒ **本条签字的原始理由「不可逐字节复刻」是成立的，而且比当初写的更强** ——
不是「Spring 的 `MediaType.toString()` 少个空格」，而是「**应用层根本拿不到那个空格**」
（`setContentType` / `setHeader` / `addHeader` 三条路都汇到同一处归一化）。
⇒ ⇒ ⇒ **撤销的性质因此变了**：**不是**「技术上变得可行了」，而是
**「技术上仍然不可逐字节复刻，但我们选择向 Go 靠拢（加上 charset 参数）」** ⇒ 纯**用户决策**。

#### 残余：那个空格（**已接受，不新签**）

| | 实际响应头 |
|---|---|
| Go | `application/json; charset=utf-8`（**分号后有空格**） |
| RAG（修后） | `application/json;charset=utf-8`（**分号后无空格**，Tomcat 归一化） |

⇒ 按 RFC 9110，参数分隔符两旁的 **OWS 不显著** ⇒ 两者**语义相同**
⇒ ⇒ **与 404 路径是同一类、仓库早已接受的做法**：
`LikeApiRouterErrorRealTomcatTest` 对 404 的 `text/plain; charset=utf-8`
就是**刻意解析后比对**（去空格），注释明写「比原始字符串会得到一个『必然红、且红了也不代表有错』的断言（已实测）」。
⇒ ⇒ 本条**不需要新登记**（不是新的差异类别）。

### 以下为 2026-10-24 的**原始签字记录**（留档，不删）


- **Component**：hot-api 成功路径响应头 `Content-Type`
- **Go 出处**：
  - `service/hot/api/hotcenter.go:45-51` → `httpx.SetOkHandler`；
  - `go-zero v1.10.2 rest/httpx/responses.go:doWriteJson` ——
    `w.Header().Set(ContentType, header.ContentTypeJson)`；
  - `go-zero v1.10.2 rest/internal/header/headers.go:17` ——
    `ContentTypeJson = "application/json; charset=utf-8"`（**分号后有空格**）。
- **RAG 形态**：Spring 的 `MappingJackson2HttpMessageConverter` 写
  `Content-Type: application/json`（**无 charset**）。
- **两侧实测**（2026-10-24 第五轮端到端对拍，逐字节比响应头）：

  | | 实际响应头 |
  |---|---|
  | Go | `Content-Type: application/json; charset=utf-8` |
  | RAG | `Content-Type: application/json` |

  ⇒ **媒体类型相同**（`application/json`），差的是**参数**。

- **签字理由（已批准）**：
  1. **不可逐字节复刻**：即使 RAG 侧显式补 charset，Spring 的 `MediaType.toString()`
     产出的是 `application/json;charset=utf-8`（**分号后无空格**）⇒ 与 Go 仍差一个空格
     ⇒ 要逐字节相同只能靠 filter 在响应写出后改写 header；
  2. **规范方向相反**：RFC 8259 规定 JSON 的字符集恒为 UTF-8，**不应**在 `application/json`
     上声明 charset ⇒ Go 的写法是历史包袱，不是应当追随的标准；
  3. **可观察影响极小**：客户端按 `Content-Type` 选解析器 ⇒ 两侧选的都是 JSON 解析器；
     按 charset 解码的客户端在 JSON 上恒用 UTF-8（RFC 8259）⇒ 不受影响。
- **⚠️ 与 `DRIFT-HOTAPI-021/022/023` 的分界**：那三条是**容器默认值比 Go 严**导致
  **请求被拒**（Go 成功 / RAG 400）⇒ **该修**（已有实测配方）；
  本条是**响应头多一个参数** ⇒ **不影响任何请求的处理结果** ⇒ 建议不复刻。
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 DRIFT-HOTAPI-024 + `tools/difftest/diff-hot.py`
  的 `split_media()`（媒体类型硬比、参数差异归入「已记录差异」）。

---

## SD-HOT-API-007 ✅ 已签字 · Prometheus 指标带全局标签 `application="hot-api"` ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> ### ⚠️ 2026-10-29 订正注记（台账对齐 phase · W-E lane）—— **签字仍有效，但签字理由的「全仓统一」前提已过期**
>
> **① 被签字描述的那个行为：仍存在** ⇒ **签字有效**。
> 证据：`service/hot/hot-api/src/main/resources/application.yml:182-183` 仍配
> `management.metrics.tags.application: hot-api` ⇒ RAG 侧每个 Prometheus 指标**仍带** `application="hot-api"`；
> Go 侧默认 registry **仍不带**任何全局标签（`service/hot/api/internal/metrics/metrics.go` 的 5 个 collector 只带各自 label）。
>
> **② 但签字理由第 1 条的前提已过期（原文保留，不改写）**：
> 原文写「`article-api` / `article-rpc` / `comment-api` / `favorite-api` / `favorite-rpc` / `task-rpc` / `message-*` / `user-*` / `admin-*` **都配了同一项** ⇒ hot-api 单独去掉会变成**全仓唯一的例外**」。
> **实测（2026-10-29 · 逐模块核 `management.metrics.tags`）**：8 个 **api** 模块里**只有 5 个**配了 `management.metrics.tags.application` ——
> `article-api`（`article-api`·`:225`）/ `comment-api`（`comment-api`·`:173`）/ `favorite-api`（`${spring.application.name}`·`:195`）/ `hot-api`（`hot-api`·`:183`）/ **`message-api`（`message-api`·`:213`）**；
> **`follow-api` / `like-api` / `task-api` 都没有配** ⇒
> ⇒ hot-api 单独去掉该标签**不再是「唯一例外」**，而是变成 **3 个例外之一**。
> ⇒ ⇒ **「全仓约定」这个理由的强度下降了**（签字的**行为**仍在，**理由**的部分前提不再成立）⇒ **保留原句 + 本订正**。
> ⚠️ **2026-10-29 主线二次订正（本条原写「4 个 / message-api 未配 / 5 个例外之一」⇒ 有误）** —— 主线实测：**`message-api` 是配了的**（`service/message/message-api/src/main/resources/application.yml:210-213`：`management.metrics.tags.application: message-api`）⇒ ⇒ **正确口径 = 配了 5 个（article / comment / favorite / hot / **message**）· 未配 3 个（follow / like / task）· 去掉 hot 的标签 ⇒ 变成 3 个例外之一**（不是 5 个）。<br>⚠️ **本条订正一度踩了自己的坑**：第一版核验用 `grep 'management.metrics.tags.application'`（**点号路径**）⇒ 而 YAML 是**嵌套写法**（`management:` → `metrics:` → `tags:` → `application:`）⇒ 该串**在任何模块里都不出现** ⇒ 误得「0/8 全没配」⇒ **先怀疑判据、再怀疑被测物**。**核验口径 = 逐模块看 `tags:` 块下的 `application:` 键**。
> ⇒ ⚠️ **但结论不变**：被签字的行为确实存在、且与「不加全局标签」的 Go 侧仍不一致 ⇒ **维持签字、不撤销、不改任何一侧**。
>
> **③ 另注**：签字理由第 3 条把本条与 `SD-HOT-API-004` 称为「同族（都是 Go 侧没有、RAG 侧有）」⇒
> **`SD-HOT-API-004` 已于 2026-10-29 判定失效**（Go 侧**有** `/metrics` 端点）⇒ ⇒ **本条的「同族」依据只剩「Go 侧无全局标签」这一半**，另一半已不成立。
>
> **④ 撞号检查**：本域 `SD-HOT-API-007` **全域唯一**（`grep -rn "SD-HOT-API-007" docs/Intentional_Deviations/` ⇒ 只有本条）⇒ **无撞号** ⇒ **不需要** `SD-<编号>@<域>` 别名。

- **Component**：hot-api 的 Prometheus 指标**标签集**
- **Go 出处**：
  - `service/hot/api/internal/metrics/metrics.go` 的 5 个 collector 全部
    `prometheus.MustRegister(...)` 注册到**默认 registry**；
  - 默认 registry **不带任何全局标签** ⇒ 导出的每个指标只有 `Subsystem:"hot_api"`
    + 各 collector 自己的 label（`route` / `callee` / `reason` / `code` …）。
- **RAG 行为**：`application.yml` 的 `management.metrics.tags.application: hot-api`
  ⇒ Micrometer 给**每个**指标加一个 `application="hot-api"` 全局标签。
- **签字理由（已批准）**：
  1. **这是全仓约定，不是 hot 域单点问题** —— `article-api` / `article-rpc` / `comment-api` /
     `favorite-api` / `favorite-rpc` / `task-rpc` / `message-*` / `user-*` / `admin-*` 都配了同一项
     ⇒ hot-api 单独去掉会变成**全仓唯一的例外** ⇒ 比差异本身更坏；
  2. **影响面**：只改标签集，**不改任何指标数值、不改任何业务判定**；
     跨实现直接写 PromQL 时 `sum by(...)` 去掉 `application` 即可对齐；
  3. 与 `SD-HOT-API-004`（指标暴露面单向增强）**同族** —— 都是「Go 侧没有、RAG 侧有」的增强。
- **⚠️ 与 `DRIFT-HOTAPI-018` 的分界**：018 是**分桶边界**（Go 12 个 `le` vs RAG 80 个）
  ⇒ 那条**改了数值语义**，必须修（已修）；本条只多一个标签、**数值语义不变** ⇒ 不修。
- **⚠️ 残留风险**：跨实现的裸 PromQL 对不上 ⇒ 需先 `sum by(...)`。
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 DRIFT-HOTAPI-019 +
  `application.yml` 的 `management.metrics.tags` 段注释。

---

## SD-HOT-API-008 ✅ 已签字 · hot-api 注册到 Nacos（Go 侧不注册） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：hot-api 的**服务注册**（部署面）
- **Go 出处**：
  - `service/hot/api/` 全目录 `grep -rn "nacos\|Nacos"` **零命中**；
  - `internal/svc/serviceContext.go` 只有两个 `zrpc.MustNewClient`（hot-rpc / article-rpc），
    **没有任何注册中心** ⇒ Go 侧 hot-api **不在任何注册中心里**。
- **RAG 行为**：pom 引 `spring-cloud-starter-alibaba-nacos-discovery`
  ⇒ Spring Cloud Alibaba **自动注册**到 Nacos（`spring.cloud.nacos.discovery.server-addr`）。
- **签字理由（已批准）**：
  1. **全仓约定**：其余 9 个 api + 全部 rpc 模块都注册 ⇒ Go 侧 hot-api 不注册是**它自己的例外**
     （go-zero 那套 hot-api 当时没接注册中心）⇒ 让 RAG 侧对齐 Go 会让 hot-api 成为
     **唯一不入注册中心的服务**，运维侧「统一按注册中心寻址」会漏掉它；
  2. 影响面：服务发现形态差异，**不改任何业务判定**。
- **⚠️ 残留风险（签字即接受这条代价，必须写进部署注意项）**：
  **Nacos 不可达 ⇒ hot-api 整个进程起不来**（实测）：
  ```
  Caused by: ErrCode:-401, ErrMsg:Client not connected, current status:STARTING
      at com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy.requestToServer
  Caused by: org.springframework.context.ApplicationContextException: Failed to start bean 'webServerStartStop'
      at com.alibaba.cloud.nacos.registry.NacosServiceRegistry.register
  ```
  ⇒ **Nacos 抖动 ⇒ hot-api 直接起不来**，Go 侧不受影响。
  ⇒ 缓解手段：`register-enabled=false` 可关掉注册（本次对拍 harness 就用它）。
- **⚠️ 与 `DRIFT-HOTRPC-010`（hot-rpc 侧）的方向不一致（仍然存在）**：
  hot-rpc 侧已按「注册失败 ⇒ **存活** + 健康检查 `NOT_SERVING`」签字并**已实现**
  （`SD-HOT-RPC-004`）；hot-api 侧是「注册失败 ⇒ **启动失败**」。
  ⇒ 这条不对称**本轮不动**（改它需要 `service/common` 的 `HealthIndicator` 能力 ⇒ 跨域），
    但**必须留痕**：hot-api 的可用性依赖 Nacos，比 hot-rpc 脆弱。
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 DRIFT-HOTAPI-020。

---

## SD-HOT-API-009 ✅ 已签字 · hot-api 的 HTTP 端口取 18897 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：hot-api 的 HTTP 监听端口
- **Go 出处**：`service/hot/api/etc/hotcenter.yaml` 的 `Port: 8893`（go-zero `rest.RestConf`）。
- **RAG 行为**：`application.yml` 的 `server.port: ${HOT_API_PORT:18897}`
  ⇒ 默认 **18897**（`Infrastructure.md §3` 给 hot 域预占的端口），可用 `HOT_API_PORT` 覆盖。
- **签字理由（已批准）**：
  1. **HTTP 端口不是 wire 契约** —— 它是**部署参数**，两侧各自独立；
     跨实现的客户端**从不直连对端的端口** ⇒ 端口不同不产生任何行为差异；
  2. **18897 是全仓端口分配的真源**（`Infrastructure.md §3`）⇒ 保持即遵守分配；
  3. `HOT_API_PORT` 留了覆盖口 ⇒ 需要与 Go 的 8893 对齐时**部署参数即可**，不必改代码。
- **⚠️ 残留风险**：若某个部署脚本按「两侧同端口」写死 ⇒ 会撞车
  ⇒ 记为**部署注意项**，不靠代码兜底。
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 D6 行 + `application.yml` 的 `server.port` 段注释。

---

## SD-HOT-API-010 ✅ 已签字 · 补全的**跨请求**并发总上限（RAG 有，Go 没有）—— RAG 更保守的一侧 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：hot-api 的补全执行器（`service/hot/hot-api/.../config/HotHydrationExecutorConfiguration.java`）
- **Go 出处**：
  - `getHotArticlesLogic.go:93`：`sem := make(chan struct{}, hydrationConcurrency)`
    —— **每个请求各建一个**容量 8 的信号量；
  - `getHotArticlesLogic.go:101-109`：每个 item 一个 goroutine，**阻塞在 `sem <-` 上排队**
    （阻塞的 goroutine **不占 OS 线程**）；
  - ⇒ **N 个并发请求 ⇒ N×8 个同时在途的下游调用，跨请求合计在途【无全局封顶】**。
- **RAG 行为**：
  - 每请求窗口 = `Semaphore(8)`（`HotArticleHydrator`）✅ **与 Go 一一对应**；
  - **但**所有请求的补全任务都投到**同一个全局池** ⇒ 跨请求合计在途被
    `POOL_SIZE`（**64**）+ 队列（64）封顶 ⇒ **并发 > 8 时 RAG 排队，Go 不排**。
- **签字理由（已批准，2026-10-24，用户拍板「方案 A：池 64 + 登记为签字偏离」）**：
  1. **保留这层保护是有意的**：它给下游 article-rpc 兜了一层并发上限，
     而 Go 侧**没有** ⇒ 这是「**RAG 比 Go 更稳 / 更保护下游**」的一侧；
  2. **与 Go 等价的改法存在但更激进**：把池换成「每任务一虚拟线程」（Java 21 JEP 444）
     ⇒ 彻底取消全局上限、逐条等价于 goroutine；
     ⚠️ 但那会把下游并发**完全放开**（无上限），与「保护下游」的初衷相反 ⇒ **不采用**；
  3. **阈值取 64 的依据是实测**：8 并发 × 每请求 8 窗口 = 64
     ⇒ 在**本组件已验证的并发档位（≤ 8）下与 Go 完全等价**
     （活体实测：下游在途峰值两侧**都是 64**，p99 比值 **1.08×**）；
  4. **附带效果**：`POOL_SIZE + QUEUE = 64 + 64 = 128` 个任务位 = **16 个并发请求**
     ⇒ **并发 ≤ 16 时 `CallerRunsPolicy` 不会触发** ⇒ 背压形态是**纯排队**（与 Go 同类）。
- **⚠️ 残留风险（签字即接受）**：
  - **并发 > 8 时**，RAG 侧会出现 Go 侧没有的**跨请求排队** ⇒ 延迟分布不同
    （不是结果不同 —— 两侧的响应体与成功率仍逐条一致）；
  - **并发 > 16 时**，`CallerRunsPolicy` 会触发 ⇒ **任务跑在请求线程上**
    ⇒ Go 侧**永远不会有**这种形态（goroutine 独立）⇒ 这是**第二层**残余差异，
    需在压测档位上留意；
  - 阈值是**部署决策** ⇒ 改动会影响下游压力 ⇒ 改前应重跑
    `tools/difftest/probe-hot-latency.py` 复测。
- **守门**：`HotArticleHydratorTest#concurrentRequestsEachKeepTheirOwnWindow`
  断言「8 个并发请求的合计在途 = 64」⇒ **池被改小 ⇒ 立刻变红**（变异验证：32 ⇒ 变红，实测）。
  🔴 **2026-10-25 修了「测量方式」，守门强度不变**：原实现用 `Thread.sleep(30)` 制造时间重叠 ⇒
  峰值是**时序量** ⇒ CPU 忙时（全量并行测试正是此时）峰值掉到 36/29 ⇒ 断言随机红
  （实测：负载下必红，空闲时连跑 3 次全绿）⇒ 那是**断言形式与机制冲突**，不是实现回归。
  **现改用 `CyclicBarrier(64)`**：全部 64 个任务都被调度到才放行 ⇒
  峰值**必然** = 64（与机器负载无关）；池=32 ⇒ 屏障凑不齐 ⇒ 超时变红（实测仍报 `expected 64 but was 32` ✅）。
  ⇒ **签字要求的守门语义（池被改小立刻变红）完整保留**，只是不再对机器负载敏感。
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 DRIFT-HOTAPI-028。

---

## SD-2026-10-25-CAND-10：超长请求行 ⇒ RAG **400 + Tomcat HTML**，Go **431 + `text/plain`** ｜ **状态（核对日期 2026-10-29）：⏳ 未签字**

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字候选（未签字）** —— 属**框架限制**，不是「RAG 偷懒」 |
| 组件 | `service/hot/hot-api`（**性质是 Tomcat 全体** ⇒ 10 个 api 模块都适用） |
| **Go 出处** | **Go 标准库 `net/http/server.go`**（⚠️ **不在 `Sea-RideTheWind_golang` 项目树里** ⇒ 见 **PIT-GLOBAL-001** / **PIT-GLOBAL-006**）：<br>· **`:931`** `const DefaultMaxHeaderBytes = 1 << 20 // 1 MB` ⇒ **上限基数 = 1 MiB**；<br>· **`:941`** `return int64(s.maxHeaderBytes()) + 4096 // bufio slop` ⇒ **真实阈值 = 1 MiB + 4096**（**RAG 侧的 `1028KB` 就是照这条配的**）；<br>· **`:2040`** `const errorHeaders = "\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n"` ⇒ **`Content-Type` 是 `text/plain; charset=utf-8`**；<br>· **`:2049-2050`** `const publicErr = "431 Request Header Fields Too Large"` + `fmt.Fprintf(c.rwc, "HTTP/1.1 "+publicErr+errorHeaders+publicErr)` ⇒ **状态码 431 + body 就是这行字**。<br>**RAG 侧** Tomcat `org/apache/coyote/http11/Http11InputBuffer.java:768`（`fill`）抛 `IllegalArgumentException: Request header is too large` ⇒ 请求**没进 Servlet** ⇒ 状态码与错误体由 Tomcat 决定（`Constants.java` 无 431 预置常量 ⇒ 走 `Http11OutputBuffer` 的默认分支）。<br>⚠️ **阈值本身两侧已对齐**（翻转点**都是 1052536 字节，差 0**）⇒ 差异**只在拒绝时的呈现形态**（状态码 + `Content-Type` + body）📌 **2026-10-29 真实性复核 —— 这个指针是「jar 内第三方源码」，补成可复算**（**行号 `:768` 原样保留，不改**）：**版本** = Tomcat **11.0.24**（Spring Boot 4.0.8 管的版本 ⇒ `~/.m2/repository/org/springframework/boot/spring-boot-dependencies/4.0.8/spring-boot-dependencies-4.0.8.pom:209` `<tomcat.version>11.0.24</tomcat.version>`）⇒ **jar** = `~/.m2/repository/org/apache/tomcat/embed/tomcat-embed-core/11.0.24/tomcat-embed-core-11.0.24-sources.jar` ⇒ **成员** = `org/apache/coyote/http11/Http11InputBuffer.java` ⇒ **该行实际内容（实测）** = `throw new IllegalArgumentException(sm.getString("iib.requestheadertoolarge.error"));` ⇒ **所在方法** = `private boolean fill(boolean block)`（方法头在 `:753`）⇒ ⇒ **指针正确**。<br>⇒ ⚠️ **本机可核验**（不是「不可核验的外部锚点」）⇒ 机器判据 `tools/difftest/ledger-claim-audit.py --domain hot` 把它单列进 **「📦 jar 内第三方源码」档**、**不计失败**（该档单列 = 已定位到具体 jar + 成员路径 + 那一行的实际内容）。 |
| 行为层 | **HTTP 状态码 + 错误响应体形状**（客户端可见）—— 仅限「请求行 > 1 MiB + 4 KiB」 |
| 证据强度 | ✅ **双侧活体实测**（2026-10-25，二分出两侧翻转点） |

### 差异的实际面

| 侧 | 状态码 | `Content-Type` | body |
|---|---|---|---|
| Go | **431** | `text/plain; charset=utf-8` | `431 Request Header Fields Too Large`（35 B） |
| RAG | **400** | `text/html;charset=utf-8` | Tomcat 的 HTML 错误页（**435 B**） |

**根因**：Tomcat 在**连接器层**就拒了，请求**根本没进 Servlet**：

```
INFO  o.apache.coyote.http11.Http11Processor : Error parsing HTTP request header
java.lang.IllegalArgumentException: Request header is too large
	at org.apache.coyote.http11.Http11InputBuffer.fill(Http11InputBuffer.java:768)
```

⇒ Spring 的 `GlobalExceptionHandler` **没有机会介入** ⇒ 状态码与错误体**由 Tomcat 决定**。

### 为什么**配不出来**（⇒ 只能签字，不能「修」）

- Tomcat 的 `Connector` **没有**「请求行过大 ⇒ 用 431」的开关；
- `server.error.whitelabel.enabled=false` 只改 **body**、**不改状态码**，
  而且会**波及其他错误响应**（404 / 405 的形态已与 Go 对齐 ⇒ 不能动）。

⇒ 与 `SD-HOT-API-006`（`Content-Type` 的 charset 参数）同族：**框架层不可逐字节复刻**。

### 触发面与残留风险（签字即接受）

- 触发条件是「**请求行 > 1 MiB + 4 KiB**」⇒ **病态输入**，正常客户端不会碰到；
- ⚠️ **阈值本身已对齐**：RAG 与 Go 的翻转点**都是 1052536 字节（差 0）**
  ⇒ 这不是「RAG 更严/更松」，只是**拒绝时的呈现形态**不同。

### 守门

`HotApiRealTomcatGateTest#requestLineLengthLimitMatchesGo` 断言
「20 KiB 放行」＋「超过 1 MiB+4 KiB 被拒且状态码 = 400」⇒
**把 RAG 侧的实际形态钉死**，避免它悄悄漂成别的样子。
⚠️ 该断言**故意断言 RAG 的 400 而不是 Go 的 431** —— 目的是记录现状，不是认可它。

### 留痕位置

`docs/DRIFT/hot/api.md` 的 `DRIFT-HOTAPI-031`；
另一处相关残余（同轮查出的 `DRIFT-HOTAPI-030`）**仍属缺陷**，在本档之外。
