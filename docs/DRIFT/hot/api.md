# DRIFT 台账 · `service/hot/hot-api`

> ⚠️ **本文件此前的全部内容只有一句否定式断言**（「13 个文件全是空骨架」）。
> 2026-10-23 该模块完成首次实现并做了首轮 Go ↔ RAG 对照，本文件已整体重写。
> 2026-10-23 第二轮（组件级验证，见下文第 11~13 节）在此基础上**增量追加**，历史条目与编号一条未删。
>
> 🟥 **2026-10-24 第十一轮后回填（本节的「核对日期」与「证据强度表」此前停在第六轮）**：
> 下方「语义对照逐项结论」之前的头部**一度严重过期**（写着「D9/U5 仍未做」「变异验证仍未做」、
> 「hot-rpc 自身的端到端对拍仍未做」）⇒ **这些断言在写下时全部为真，到第十一轮已全部被推翻**。
> 本次回填只改**头部**；**正文里各轮的原始记录一条未删**（历史快照价值），逐条订正以就近的
> 🟥 复核批注 + 各轮章节为准。

**归属**：`align/hot` 工作树产出（`RAG.worktrees/hot`）；基线 = `do-my-own-gh` @ `8422116` 之后的分支点。
**核对日期**：2026-10-24（**第十一轮**：hot-rpc D1 闭合 + U7 闭合 + hot 域 U9 系统变异 25/25 + U5-OPEN-1 闭合）
**真源**：`Sea-RideTheWind_golang/service/hot/api` @ go-zero **v1.10.2**（`go.mod:17`，已核对 module cache 里同时存在 v1.10.0/v1.10.3，**本文件所有行号一律以 v1.10.2 为准**）
**当前证据强度**（按 AGENTS.md §3.3 口径 · 截至第十一轮）：

| 档 | 已取得 | 说明 |
|---|---|---|
| ✅ **双侧活体对拍** | **hot-api 29 条端到端用例**（第五轮）+ **hot-rpc 10 组 top_k 逐字节**（第十一轮） | 两套 HTTP 栈 / 两套 gRPC 栈**同时拉起**，逐字节比**响应体 + 关键响应头**；hot-rpc 侧为**真 Kafka + 2×真 Redis + 真 gRPC** |
| ✅ **真中间件活体** | 真 Redis（12 例）、真 Kafka（4 例）、真 Tomcat、**真 PG 不适用** | hot-rpc 侧另在第十一轮取得「真 Kafka + 2×Redis + 自造栅栏」的**完整双栈对拍** |
| ✅ **Go 侧活体探针** | 15 条 form 解析 + 25 条分页/归一化 + 3 组 wire 字节 | 直接调用真源 `httpx`/`jsonx` 与逐字复制的真实 struct，**跑起来了**（第二轮） |
| ✅ **RAG 侧真实上下文探针** | 同矩阵 MockMvc + `@SpringBootTest` 真 Boot 上下文 | 复用**项目真实的** mapper / advice / controller，非自建（第二轮） |
| ✅ **双侧差分** | 分页/归一化 25 行 `diff` 输出为空 | 两侧同输入同函数，逐行相同（第二轮） |
| ⚠️ **静态源码（平台不可达部分）** | **CpuShedding 四闸之一** | darwin 上 Go 侧 `CpuUsage()` 恒 0 ⇒ **结构性不可达** ⇒ 只能「源码逐条对照 + go-zero 上游自带测试 + RAG 侧 10 例同场景单测」取证（见 U5） |
| ✅ **变异验证** | **hot 域合计 25/25 变异点全部被守门抓到** | hot-api **11**（第十轮 10 + 第十一轮 M11）/ hot-rpc **10** / hot-common **4**；守门缺口 **3 处**已补（`page_size=51`、合并 >100 截断层、桶冲突 uint32 衰减） |
| ❌ **真 PG** | 不适用 | 本组件无 DB、无事务、无缓存 |

> ⚠️ **「绿测试」≠「已验证」**（AGENTS.md §0）：本轮所有修复都是在**测试全绿 / 编译通过**的状态下做的
> ⇒ 「已修」一律读作「**在该证据强度下一致**」。

---

## 2026-10-23 首轮实现 + 首轮语义对照

| 项 | 实测值 |
|---|---|
| 主源码 | **16 个 Java 文件 / 2,088 行**（此前 13 个文件 / 每份 11~12 行） |
| 测试 | **8 个测试类 / 59 例，全绿**（2026-10-23 复核：逐类 surefire XML 核对 `7+5+4+10+12+7+14=59` ✅ 原结论成立。⚠️ 注意 `.txt` 摘要会把 `@Nested` 类报成 `Tests run: 0`，`HotRankPaginatorTest` 真实为 14 例 —— **别拿 `.txt` 合计**） |
| 构建 | `mvn -pl service/hot/hot-api package` → **BUILD SUCCESS** |
| 结论 | ⚠️ **不是「已验证无漂移」** —— 见下节 |

### ⚠️ 结论的准确措辞（勿简化为「已对齐」）

| 维度 | 状态 |
|---|---|
| 代码是否落地 | ✅ 是 |
| Go ↔ RAG **源码级**对照是否做过 | ✅ 是（逐行读 Go 到控制流级，非只贴声明行） |
| Java 侧行为是否有断言钉住 | ✅ 是（**首轮 59 例**；🟥 **截至第十一轮 = 87 条**，含真实 MVC 管线 + 真实 stub 捕获 + 真 Kafka/Redis 门禁） |
| **双侧活体对拍**（Go 栈真跑起来逐项比对） | ✅ **已做**（2026-10-24 第五轮，29 条端到端用例）—— **但查出 4 条新漂移**（D21~D24），且**推翻了第二轮 4 条 ✅ 结论** |
| **变异验证**（证明测试不是假绿） | ❌ **未做** 🟥 **2026-10-24 第十/十一轮：已闭合** —— hot-api 11 + hot-rpc 10 + hot-common 4 = **hot 域 25/25 变异点全部被守门抓到**；过程中查出并修补 **3 处真实守门缺口** |
| **真 PG / 真 Redis / 真 Kafka** | ➖ 本模块**不涉及**（无 DB、无 Redis、无 MQ、无事务、无缓存） |

⇒ 逐条状态以本文件为准，**不得**据此断言「hot-api 与 Go 语义等价」。

### 🔴 本次对照最大的方法论弱点（必须记录）

AGENTS.md §3.1 明确警告：**「不要拿『现有 RAG 代码』当对照基准」**。
本轮的顺序是「读 Go → 写 Java → 再逐行回读 Go 复核」，
⇒ **复核时已经知道 Java 侧写了什么，存在用实现反推基准的风险**。
这与 §3.1「必须有一遍回到原文件 line-by-line」仍有距离。

⇒ **下一步该做的是双侧活体对拍**（`tools/difftest/run.sh` 起 Go 与 RAG 两套栈逐项比），
而不是再加 Java 侧单测。加单测只会让「Java 是自洽的」更可信，**不会**让「Java = Go」更可信。

---

## 语义对照逐项结论

判定口径：**未发现 `SEMANTIC MISMATCH`**（与 `article` 域的首轮结论相反）。
但「未发现」≠「不存在」——见上节的方法论弱点。

| # | 行为 | Go 出处（消费点） | Java 落点 | 证据强度 |
|---|---|---|---|---|
| 1 | 路由 `GET /hot/v1/articles`、前缀 `/hot/v1` | `handler/routes.go:16-25` | `HotArticleController` | ✅ 双侧：Go 源码 + MVC 管线 7 例 |
| 2 | **零鉴权** | `routes.go` 只有 `rest.WithPrefix`，无 `WithJwt` | 刻意**不**建 `interceptor/`、不建 `WebMvcConfig` | ✅ 双侧；MVC 测试匿名访问即证 |
| 3 | 空值丢弃 → 取 tag 默认值 | `httpx/util.go:44-45` → `unmarshaler.go:918-933` | `GoFormValues.raw` | ✅ 读到消费点 |
| 4 | 整数按 `strconv.ParseInt(str,10,32)` | `core/mapping/utils.go:104-125`（经 `fillPrimitive:1099`） | `GoFormValues.int32` | ✅ 读到消费点 |
| 5 | 解析失败出口 = **200 + {code:5001}**，非 400 | `hotcenter.go:53-66` + `getHotArticlesHandler.go:18-21` | `GoFormValues` 抛 `BusinessException(5001)` | ✅ 双侧 |
| 6 | `SetErrorHandler` 返回值**恒 200** | `hotcenter.go:61` | `GlobalExceptionHandler.decideStatus` 全返回 200 | ✅ MVC 测试 3 例 |
| 7 | 信封只包一层（`body instanceof Response` 放行） | `httpx.SetOkHandler` | `GlobalResponseAdvice` | ✅ MVC 测试「顶层 code 不是 200」 |
| 8 | 404 / 405 **裸响应、无信封** | `router/patrouter.go:59-83` | `GoStyleRouterErrorResponses`（复用，非重写） | ✅ MVC 测试逐字节 |
| 9 | 分页 1/20/50、`TopK=100` 硬编码 | `getHotArticlesLogic.go:25-28,63` | `HotRankPaginator` 常量 | ✅ 双侧 |
| 10 | `page<=0→1`、`pageSize>50→50` | `logic:172-190` | `HotRankPaginator.normalize` | ✅ 双侧（Go `_test.go:62-95` 同款用例） |
| 11 | **int32 乘法溢出 → 空页** | `logic:192-208` | 用 `int` 原样复现（**刻意不用 `long`**） | ✅ Java 侧断言 |
| 12 | `rank` = 过滤后稠密序号、**跨页连续** | `logic:119` + `_test.go:131-147` | `HotRankPaginator.assignRanks` | ✅ 双侧同款断言 |
| 13 | `total` = 过滤后窗口大小，与分页参数无关 | `logic:78` | `HotRankPaginator.slice` | ✅ 双侧 |
| 14 | 4 条 skip 分支 + 各自 metric | `logic:126-154` | `HotArticleHydrator` | ✅ Java 侧逐条断言 |
| 15 | **fatal vs best-effort 的不对称** | `logic:65-69` vs `logic:137-142` | `HotArticleQueryService.fetchTopK` / `Hydrator` | ✅ 同一套夹具双向对照 |
| 16 | 并发窗口 **8**、按下标保序 | `logic:93,108` | 每请求 `Semaphore(8)` + 下标数组 | ✅ 并发峰值断言 `==8`；30 条乱序完成仍保序 |
| 17 | `PublicOnly=true` / `IncrView=false` / 不带 `RequesterId` | `logic:132-136` + `_test.go:35-37` | `ArticleRpcClient` facade 硬编码 | ✅ **捕获型拦截器读真实下发的请求**（非 mock 数据层） |
| 18 | gRPC **2s** per-call deadline | `zrpc/config.go:31` → `timeoutinterceptor.go:20-25` | ⚠️ **2026-10-31 第十三轮**：上界改由**共享层** `GoStyleOutboundDeadlineInterceptor` 承担（域 yml 的 `rag.protection.outbound-deadline.cap-millis: 2000`）；域侧 `GoStyleGrpcCallDeadline` **只保留「请求预算传播」**（worker 线程看不到载体 ⇒ 必须显式传）⇒ 二者合起来 = min(2s, 请求剩余预算) | ✅ 真发调用（in-process server 挂起 ⇒ 断「有上界」与「没上界」）＋ 真 `CallOptions.getDeadline()` |
| 19 | **不加 retry** | `zrpc.RpcClientConf` 无 `Retries` 字段 | 无 retry 配置 | ✅ 读到配置定义 |
| 20 | client 熔断默认开启 | `zrpc/internal/config.go:14` | `com.rag.common.protection` 全局拦截器 | 🟥 **已升级为活体（第十轮）**：装配层 3 例 + **行为层 E2E**（内嵌恒返 INTERNAL 的 gRPC server ⇒ 判据是「**下游调用次数停止增长**」）⇒ 熔断**真的打开**已被坐实；变异验证变红 ✅ |
| 21 | HTTP **3s** 超时 | `rest/config.go:55`（tag 默认，yaml 未写） | `observability.http-timeout: 3s` | 🟥 **已升级为活体（第五轮 CASE-21）**：article-rpc 睡 30s 把请求拖过 3s ⇒ 两侧**都**产出 `503 + "Request Timeout\n"`（body 逐字节同），**只差 `nosniff`**（= D14，跨域 common） |
| 22 | 请求保护层四闸默认开启 | `rest/config.go:12-22,52-56` | `rag.protection.*`（1 MiB / 10000 / 900 / breaker） | 🟥 **已升级为活体（第八轮）**：**三闸双侧坐实**（MaxBytes 超限 ⇒ 两侧 `413` 空 body 无 Content-Type；MaxConns 同阈值 8 ⇒ 两侧各 8 个裸 503；Breaker ⇒ 两侧都真打开）；🔴 **CpuShedding 在 darwin 上 Go 侧结构性不可达**（`//go:build !linux` ⇒ `CpuUsage()` 恒 0）⇒ 该闸只能静态 + go-zero 上游测试取证 |
| 23 | 5 个指标名 + 标签字面量 | `metrics/metrics.go:13-43` | `HotApiMetrics` | 🟥 **已升级为活体（第四轮 U11）**：真 `/actuator/prometheus` ⇒ 实测 RAG **80** 个 `le` 边界 vs Go 的 **12** ⇒ 查出 **DRIFT-HOTAPI-018**（**已修**，去掉 `publishPercentileHistogram()` ⇒ 复测回到 12 个、数值逐条一致） |
| 24 | `hot_api_reject_total` 零触发点 | `metrics.go:65-67` | 注册但**不补触发点** | ✅ 保持 Go 形态 |
| 25 | `secondary_tags` 空时是 `null` 不是 `[]` | Go proto3 nil slice + tag 无 `omitempty` | 构造处显式置 null | ✅ 裸 `ObjectMapper` 断言 |
| 26 | `hot_score` 是 **uint32** | `hot.proto:17` | `long` + `Integer.toUnsignedLong` | ✅ 对 `0xFFFFFFFF` → `4294967295` 断言 |

> ⚠️ **第 25 行的证据强度本轮下调**：见第三轮 DRIFT-HOTAPI-015-B ——
> `HotApiWireContractTest` 用的是**裸 `ObjectMapper`**，而**运行时用的是 Spring Boot 装配的 mapper**，
> 两者在本项目实测出**不同行为**（字段顺序）。该测试因此**证明不了运行时**的字节形状。

---

## 刻意保留的 Go 既有行为（不要「顺手优化」）

| 项 | 说明 |
|---|---|
| `hot_rpc_nil_response` 打在 **article skip** 指标上 | 语义上它不是「某篇文章被跳过」，是整包响应为 nil。Go 侧既有归类，按 §15 保留 |
| `article_id` 取 **`article.GetId()`** 而非 `hotItem.GetArticleId()` | 两者通常同值，但契约上取 article 侧（`logic:157`） |
| `hot_api_reject_total` 是死指标 | 补触发点 = 新增行为 |
| `total` 不随分页参数变化 | 客户端不能用它推断「一共有多少篇」 |

---

## ⚠️ 未闭合 / 已知差异（逐条带核对日期）

| # | 项 | 状态 | 备注 |
|---|---|---|---|
| D1 | **双侧活体对拍** | ✅ **已做（2026-10-24 第五轮）** | 端到端 HTTP 栈两侧**同时拉起**（Go hot-api + RAG hot-api + 下游桩），29 条用例逐字节比响应体 + 关键响应头 ⇒ **24/29 通过**，并**新查出 4 条漂移**（D21~D24）。详见「第五轮」一节。⚠️ 第二轮的**解析层/分页层/wire 层**双侧探针对拍仍然有效，但**第二轮的 CASE 表有 4 行结论被本轮推翻**（MockMvc 测不到真 Tomcat 的请求行校验） |
| D2 | **变异验证** | ✅ **已闭合（第十轮，2026-10-24）** | hot-api 主干 **10 个系统性语义变异 ⇒ 10/10 全部被守门抓到**（基线 85→**86** tests / 0 failures）；🔴 并**查出并修补 1 个真实守门缺口**（`page_size=51` 边界无测试 ⇒ 新增 `HotApiMvcPipelineTest#pageSizeCapBoundaryIsApplied`）。⚠️ 第一轮 3 个「没变红」复核后确认**全是「变异本身是 no-op」**（含 `Integer.parseInt(String)` 本就是十进制，已 jshell 实测）⇒ 不是守门缺口。✅ **第十一轮已扩展到全 hot 域**：hot-rpc **10 个**（基线 86 条）+ hot-common **4 个**（基线 14 条）⇒ 合计 **hot 域 25 个变异点全部被守门抓到**；🔴 hot-rpc 侧**查出并修补 2 个真实守门缺口**（合并的 >100 截断层 / 桶冲突衰减概率的 uint32 语义） |
| D3 | 客户端**断连**取消不再传播 | ✅ **活体坐实**（2026-10-24 第七轮） | Go 把请求 ctx 传进 100 次补全，Servlet 断连不会自动取消在飞的 gRPC。已用「请求线程取一次预算 + 显式下传」保住**超时**语义，**断连**语义不等价 ⇒ **第七轮用裸 socket 强制 RST 实测**：Go 侧断连后 **1~3ms** 内 8 个在飞 gRPC 全部取消；RAG 侧**等到 2s deadline** 才结束，**且断连后继续发第二批** ⇒ 差异由「静态推断」升级为「活体证据」。连带产出 **DRIFT-HOTAPI-025** <br>⚠️ **2026-10-30 主线同步（favorite+hot lane 复核 ⇒ 原判两半要拆开读，原值保留）** —— 上面那句「RAG 侧**等到 2s deadline** 才结束，**且断连后继续发第二批**」**分两半**：① **「等 2s deadline + 无任何 499 记录」已不成立** —— 共享层 `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:462-486`（断连判定 ⇒ **先取消在飞的 gRPC Context**）＋ `:675-695` `recordClientDisconnected`（`markClientCancelled()` ＋ `log.warn("[HTTP] 499 …")`）＋ `:88` `HTTP_CLIENT_CLOSED_REQUEST = 499` ⇒ ⇒ **499 现在可见、在飞 gRPC 现在会被取消**；② **「断连后继续发第二批」仍成立** —— `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleHydrator.java:120-126` `CompletableFuture.runAsync(…, hydrationExecutor)` ⇒ **100 次补全跑在独立池** ⇒ 断连取消**到不了补全批次**（同 `docs/Intentional_Deviations/hot/api.md` 的 `SD-HOT-API-002`）⇒ ⇒ **本行现行态 = 🟡 部分闭合（观测面 + 在飞调用已修 · 补全批次残留）** ⇒ 与 `DRIFT-HOTAPI-025` 的现行态一致 |
| D4 | Go panic 时指标误记为 `success` | ⚠️ 有意差异（2026-10-23） | Go 的 `defer` 读命名返回值 `err`，panic 展开时仍为 nil；Java 按客户端实际看到的结果记 `fail`。该路径无代码可达 |
| D5 | 指标**暴露**是单向增强 | ⚠️ 有意偏离（2026-10-23）<br>🔴 **2026-10-29 真实性复核**：原判 **⚠️ 有意偏离（2026-10-23）** ⇒ **现行态 = ⛔ 已失效（被签字描述的那个行为不存在 ⇒ 这一格不再是「有意偏离」）**。<br>依据（两侧 · 核对日期 2026-10-29）：Go 侧**确实暴露** `/metrics` —— `Sea-RideTheWind_golang/service/hot/api/etc/hotcenter.yaml:16-23` 的 `DevServer{Enabled: true, Host: 0.0.0.0, Port: 16067, MetricsPath: /metrics, EnableMetrics: true}` ⇒ 被 go-zero v1.10.2 `core/service/serviceconf.go:77` `devserver.StartAgent(sc.DevServer)` 真实消费 ⇒ `internal/devserver/server.go:45-48` `if s.config.EnableMetrics { prometheus.Enable(); s.handleFunc(s.config.MetricsPath, promhttp.Handler().ServeHTTP) }` ⇒ **默认 registry** 的 `/metrics` 被挂上 ⇒ 正是 `service/hot/api/internal/metrics/metrics.go:47-51` 用 `prometheus.MustRegister(...)` 注册的那 5 个 `hot_api_*`（**RAG 侧**：`service/hot/hot-api/src/main/resources/application.yml:182-183` `management.metrics.tags.application: hot-api` + Actuator `/actuator/prometheus` ⇒ 确实暴露）。<br>⇒ ⇒ **「Go 注册了但无端点暴露」这个前提不成立** ⇒ **权威订正**见 [`docs/Intentional_Deviations/hot/api.md`](../../Intentional_Deviations/hot/api.md) 的 `SD-HOT-API-004`（该条 2026-10-29 已判 **⛔ 已失效·已撤销** ⇒ 不计入「已签字」计数）⇒ **本行的差异描述与历史叙述原样保留，不改写** | Go 的 5 个指标注册了但**无端点暴露**（yaml 的 Prometheus 块无 Host；`DevServer` 块在 go-zero v1.10.2 无对应字段）。分桶边界已对齐回 `prometheus.DefBuckets`<br>🔴 **2026-10-29 订正（原文保留，不改写）**：上面这句的前提**已被实测证伪** —— ① Go yaml **有** `DevServer` 块且 `EnableMetrics: true`（`service/hot/api/etc/hotcenter.yaml:16-23`）⇒ **不是**「无对应字段」；② go-zero v1.10.2 `core/service/serviceconf.go:40` `DevServer DevServerConfig` + `:77` `devserver.StartAgent(sc.DevServer)` + `internal/devserver/server.go:45-48` ⇒ ⇒ **Go 侧 `http://0.0.0.0:16067/metrics` 确实有这 5 个 `hot_api_*` 指标**。<br>⇒ **残留的真实差异只剩「暴露面不同源」**（Go = 独立 dev server 的 `:16067/metrics` ＋ `/healthz` ＋ `/debug/pprof/`；RAG = 业务**同端口** `:18897/actuator/prometheus`）⇒ **不是本条签字的内容** ⇒ **不新签、不计数** ⇒ 归本文件待办（跨域观察面差异）。<br>⇒ ⚠️ **本条不得再被读成「RAG 单向增强」** —— 2026-10-29 核对日期。 |
| D6 | **端口** | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」） | Go `hotcenter.yaml` = 8893；`Infrastructure.md §3` 给 hot 预占 18897 ⇒ **保持 18897**（`HOT_API_PORT` 可覆盖）⇒ `SD-HOT-API-009`。⚠️ HTTP 端口**不是 wire 契约**（各自独立）⇒ 无行为差异 |
| D7 | GET + JSON body 覆盖 query | ✅ **2026-10-23 复核：原结论修正 —— 这不是漂移** | **原文保留**：「Go 的 `httpx.Parse` 还会跑 `ParseJsonBody`。极冷边界，未承诺一致」。<br>**修正依据**：`core/mapping/utils.go:589-597` `usingDifferentKeys(key, field)` —— 字段有 `form` tag 但**没有 `json` tag** ⇒ JSON unmarshaler（`jsonTagKey`）**整字段跳过**（`unmarshaler.go:571-573` 直接 `return nil`）。<br>**Go 侧活体探针实测**：`?page=5` + body `{"page":9,"page_size":3}` → **`page=5 pageSize=20`**；无 query + body → **`1/20`**；POST + body → **`1/20`**。JSON body **完全被忽略**。<br>⇒ RAG「不读 body」与 Go 行为**一致**，原记的「未实现」是**假阳性**。真漂移另有其人，见 **DRIFT-HOTAPI-013**（畸形 JSON body）。 |
| D8 | client 熔断实际生效 | ✅ **已闭合（第十轮，2026-10-24）** | **装配层**（第四轮 `GoStyleGrpcClientBreakerWiringTest` 3 例）+ **行为层**（第十轮 `GoStyleGrpcClientBreakerE2ETest`：内嵌恒返 INTERNAL 的 gRPC server ⇒ 判据是「**下游调用次数停止增长**」）⇒ 熔断**真的打开**已被行为级证据坐实；**变异验证**（`service/common` 的 `if (!breaker.accept())` → `&& Boolean.FALSE` ⇒ 变红 ✅） |
| D9 | 3s 超时 / 保护层四闸实际触发 | ✅ **已闭合（第八轮，2026-10-24）** —— 三闸双侧活体坐实 + CpuShedding 平台不可达（**非缺陷**） | ✅ **活体（2026-10-24 第八轮）**：**三闸双侧活体坐实** —— MaxBytes（默认阈值，超限 → 两侧 **413 + 空 body + 无 Content-Type**，边界「恰好等于」也一致）；MaxConns（同阈值 8 + 16 并发 → 两侧**各 8 个裸 503**）；Breaker（两侧都**真的打开** → 裸 503 + 空 body）。⚠️ **CpuShedding 在 darwin 上 Go 侧结构性不可达**（`core/stat/internal/cpu_other.go` 带 `//go:build !linux` ⇒ `CpuUsage()` 恒 0 ⇒ 永不降载）⇒ 只能静态 + 上游测试取证。产出 **DRIFT-HOTAPI-026**（413 后 Go RST）/ **DRIFT-HOTAPI-027**（MaxConns 默认阈值下 RAG 不可达）/ **U5-OPEN-1**（熔断打开时机不稳定 ⇒ **第十一轮已闭合**，根因 = 补全预算的取样时刻 ⇒ **DRIFT-HOTAPI-029**，已修） |
| D10 | `management.metrics.distribution.slo` 不可用 | ⚠️ 已实测（2026-10-23） | Boot 的 `ServiceLevelObjectiveBoundary` 转换器解析不了小数秒（`2.5s` → `NumberFormatException`），且会让整个 `MetricsProperties` 绑定失败 ⇒ **上下文起不来**。SLO 边界改在 `HotApiMetrics` 代码侧声明 |

### 🆕 第二轮新增（D11 起接续编号，无冲突）

| # | 项 | 类别 | 状态 |
|---|---|---|---|
| **D11** | **重复 query 参数 → Go 取首值，RAG 拼成 `"首,次"` 后整包 5001** | **SEMANTIC MISMATCH** | ✅ **已修**（`DRIFT-HOTAPI-011`：`GoFormValues.firstFormValue` 取首值） |
| **D12** | form 参数总数上限 **2048** | OBSERVABLE DIFFERENCE | ✅ **已实现**（2026-10-24 复核：`GoFormValues.requireFormValueCountWithinLimit` + `HotApiGoParseParityTest` 的 2048/2049 边界用例；⚠️ 本行原记「RAG 侧不存在 / 需拍板」**是错的** —— 实现与测试都在，只是台账没回填） |
| **D13** | **畸形 JSON body** → Go 拒绝 5001，RAG 完全忽略 body | OBSERVABLE DIFFERENCE | ✅ **已修**（`DRIFT-HOTAPI-015`） |
| **D14** | 超时 / 499 响应缺 `X-Content-Type-Options: nosniff` | OBSERVABLE DIFFERENCE | ✅ **已实现**（2026-10-26 订正）—— ⚠️ **本行原状态停在第五轮**（「确认是漂移 ⇒ 已移交 common 域」）⇒ **共享层已落地**：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:728` `resp.setHeader("X-Content-Type-Options", "nosniff")` ⇒ ⇒ **wire 级 L4**（本域真 Tomcat 断言 `HotApiRealTomcatGateTest:229-230` ⇒ 客户端**真的收到**）⇒ ⇒ **不是待办**。⚠️ Go 侧**全仓零命中** ⇒ nosniff 来自 Go `net/http` 的 `http.Error` 自动加头 ⇒ ⇒ 断言写的是「Go 的 `http.Error` 会加 nosniff」 |
| **D15** | 运行时 JSON **字段顺序**与 Go 声明序不同（裸 mapper 测不出来） | OBSERVABLE DIFFERENCE | ✅ **已修**（2026-10-24 第五轮）—— ⚠️ **本行原状态是「🟡 已知取舍，需登记」，是过期的**（同文件 `:742` / `:817` 早已记「已修」，**只是状态列没回填**）⇒ 修法 = 两个 DTO 加 `@JsonPropertyOrder`（`HotArticlesResp:48` / `HotArticleCard:84`）⇒ 守门 `HotApiGoWireOrderTest`（**去掉注解即变红**）⇒ 第五轮双侧活体**逐字节相同**（`:734` CASE-15d）⇒ ⇒ **不需要登记为偏离** |
| **D16** | **业务错误日志的级别 / 字段集 / `error_reason` 形态**与 Go 不同（nil 响应分支最严重：Go 是 **error 级 + 9 字段**，RAG 是 **info 级 + 0 字段**） | OBSERVABLE DIFFERENCE | ✅ **已修**（2026-10-24 第四轮 ⇒ `DRIFT-HOTAPI-016`；3 条变异全红） |
| **D17** | 下游 gRPC 调用失败时，日志 `error_reason` 是**各自 gRPC 库**的形态（grpc-go `"rpc error: code = Unavailable desc = …"` vs grpc-java `"UNAVAILABLE: …"`，**code 名大小写都不同**） | OBSERVABLE DIFFERENCE | ✅ **已签字**（2026-10-24 第六轮，用户拍板「不复刻」⇒ `SD-HOT-API-005`） |
| **D18** | Prometheus `_bucket{le=}` 边界序列：Go **12 个**（`DefBuckets` 11 + `+Inf`）vs RAG **80 个**（`publishPercentileHistogram()` 塞进了 69 个 exponential 边界） | OBSERVABLE DIFFERENCE | ✅ **已修**（2026-10-24 第四轮 ⇒ `DRIFT-HOTAPI-018`；去掉 `publishPercentileHistogram()`；3 条变异全红） |
| **D19** | Prometheus 全局标签 `application="hot-api"`（Go 的默认 registry **无**此标签） | OBSERVABLE DIFFERENCE | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」⇒ `SD-HOT-API-007`） |
| **D20** | hot-api **注册到 Nacos**（Go 侧 hot-api **完全不注册**）⇒ 且实测「Nacos 不可达 ⇒ 整个进程起不来」 | OBSERVABLE DIFFERENCE | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」⇒ `SD-HOT-API-008`；⚠️ 残留风险「Nacos 抖动 ⇒ hot-api 起不来」签字即接受） |

### 🆕 第五轮新增（2026-10-24 U1/D1 端到端对拍，D21 起接续编号）

| # | 项 | 类别 | 状态 |
|---|---|---|---|
| **D21** | **Tomcat 请求目标字符校验**拒绝 query 里的 `[` `]` ⇒ `?page[]=5` **Go 200 / RAG 400 + Tomcat HTML 错误页** | **SEMANTIC MISMATCH** | ✅ **已修**（2026-10-25 第十三轮 ⇒ `DRIFT-HOTAPI-021`）：10 个 api 模块统一配 `server.tomcat.relaxed-query-chars: ["[", "]"]` ⇒ 双侧活体复测 **Go 200 / RAG 200，响应体逐字节相同**；真 Tomcat 守门 + **MUT-A 变异转红** ✅。⚠️ **第二轮 CASE-04b/04c 的 ✅ 结论被推翻**（MockMvc 绕过 Tomcat 的请求行校验） |
| **D22** | **form 参数个数上限**：Go **2048**（应用层）vs RAG **1000**（Tomcat `maxParameterCount`）⇒ 1001~2048 区间 Go 成功 / RAG 5001 | OBSERVABLE DIFFERENCE | ✅ **已修**（2026-10-25 第十三轮 ⇒ `DRIFT-HOTAPI-022`）：统一配 `server.tomcat.max-parameter-count: 2048` ⇒ 双侧活体复测**首个翻转 n 两侧都是 2049**、响应体逐字节相同；真 Tomcat 守门 + **MUT-B 变异转红** ✅。⇒ **RAG 侧自己实现的 2048 上限在 GET query 上不可达** 这个副作用已消除。⚠️ **残余**：`DRIFT-HOTAPI-030`（两层限制语义不同 ⇒ 精确窗口） |
| **D23** | **请求行长上限**：Go `net/http` **1 MiB** vs RAG Tomcat **8 KiB** ⇒ 8 KiB~1 MiB 的 query：Go 处理 / RAG **400 + Tomcat HTML 错误页**（不是业务信封） | OBSERVABLE DIFFERENCE | ✅ **已修**（2026-10-25 第十三轮 ⇒ `DRIFT-HOTAPI-023`）：统一配 `server.max-http-request-header-size: **1028KB**` ⇒ 双侧活体复测**首个翻转长度两侧都是 1052536 字节（差 0）**；真 Tomcat 守门 + **MUT-C 变异转红** ✅。⚠️ **原台账写的 `1MB` 比 Go 严 4096 字节**（Go = `DefaultMaxHeaderBytes` **+ 4096 bufio slop**）⇒ **本轮已订正**。⚠️ **残余**：`DRIFT-HOTAPI-031`（超限后的 431 vs 400，框架限制） |
| **D24** | `Content-Type` 媒体类型**参数**：Go `application/json; charset=utf-8`（**分号后有空格**）vs RAG `application/json`（无 charset） | OBSERVABLE DIFFERENCE | ✅ **已签字**（2026-10-24 第六轮，用户拍板「不复刻」⇒ `SD-HOT-API-006`；⚠️ Spring 的 `MediaType.toString()` 产出 `;` 后**无空格** ⇒ **不可逐字节复刻**） |

---

## 与已完成的 hot 域两组件的关系

> 🟥 **2026-10-24 第十一轮回填**：本表原写「hot-api 端到端 HTTP 栈未对拍」「hot-api 是证据强度最弱的一个」
> ⇒ **第五轮已做**（29 条端到端用例，双栈同时拉起）；**第十一轮 hot-rpc 也补齐了端到端对拍**（真 Kafka + 2×Redis + 真 gRPC）
> ⇒ **hot 域三组件现在全部拿到双侧活体对拍**。

| 组件 | 状态 | 台账 |
|---|---|---|
| `hot-common` | ✅ 双侧对拍 sha256 逐字节一致（14 条契约 + 4 个变异点全红） | [`common.md`](common.md) |
| `hot-rpc` | ✅ 双侧活体对拍逐字节一致（**第十一轮**：真 Kafka + 2×真 Redis + 真 gRPC，10 组 top_k 全同），86 条契约测试 | [`rpc.md`](rpc.md) |
| **`hot-api`** | ✅ **端到端 HTTP 栈已对拍**（第五轮 29 条用例，24/29 + 修正配方 28/29）+ **解析层/分页层/wire 层双侧探针对拍**（15+25+3 组），87 条测试 | **本文件** |

⇒ hot 域三组件**代码均已落地且全部拿到双侧活体对拍**。

🟥 **2026-10-26 第二十八轮订正**：下面这行**已过期**（写于 2026-10-24）——
原文说「剩下的是**域外**的三条 D14（nosniff）/ D25（499）/ D27（MaxConns）+ 待拍板的 D21 / D22 / D23」。
**逐条核对后，这 7 条现已全部闭合**（§6.3 状态表纪律：任何「未修」断言必须带核对日期）：

| 条目 | 原状态 | **核对结果（2026-10-26）** | 证据 |
|---|---|---|---|
| **D14** nosniff | 🔁 跨域移交 common 域 | ✅ **已实现**（不是「待移交」） | 共享层 `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java` 的 `resp.setHeader("X-Content-Type-Options", "nosniff")`（**锚点 = 这行调用形态**；**行号以合并后的树为准** —— G1/G3/G4 三树当前均为 **728**）|
| **D25** 499 | 🔁 跨域移交 common 域 | ✅ **已闭合**（与 T-13 一并解决） | `docs/DRIFT/global/cross-domain.md` §二·十（T-13 响应撕裂/状态码被抢） |
| **D27** MaxConns 作用域 | 🔁 跨域移交 common 域 | 🏷 **已签字为有意偏离**（**不是待办**） | `SD-2026-10-25-01`（global 台账 T-2 / T-5）⇒ `docs/Intentional_Deviations/global/common-tails.md` |
| **D21** relaxed-query-chars | ⏸ 待拍板 | ✅ **第十三轮已落地 10/10** | `server.tomcat.relaxed-query-chars: ["[", "]"]` |
| **D22** max-parameter-count | ⏸ 待拍板 | ✅ **第十三轮已落地 10/10** | `server.tomcat.max-parameter-count: 2048` |
| **D23** 请求行长 | ⏸ 待拍板 | ✅ **第十三轮已落地 10/10** | `server.max-http-request-header-size: 1028KB`（⚠️ 不是 1MB，见台账） |
| **DRIFT-HOTAPI-024** Content-Type charset | — | ✅ **第二十五轮已修**（global T-6） | 共享层 `GoStyleJsonContentTypeAdvice` ⇒ 10 个 api 全生效 |

⇒ **hot-api 域内已无可修的语义漂移**（`DRIFT-HOTAPI-014` 亦为跨域移交，且 nosniff 已实现）。

### 📌 D14 的**证据强度** = 已是 **wire 级 L4**（2026-10-28 10:3x · G3 补记）

⇒ **G1 通报 admin 域落了一个「真 Tomcat + 真 `java.net.http.HttpClient`」的字节级 nosniff 探针 ⇒ 问我域要不要照落同款**
⇒ ⇒ **我域【早就有】** —— `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiRealTomcatGateTest.java`
（**真 Tomcat · `RANDOM_PORT` · 真 HTTP 客户端**）⇒ **`:229-230` 直接断言 `X-Content-Type-Options == "nosniff"`**
（用例名「🔴 U6 —— 404 在真 Tomcat 下是裸响应 + text/plain + nosniff」，`:221`）⇒ ⇒ **D14 不是「静态对照」，是 wire 级实测** ✅
⇒ ⇒ **Go 侧出处**：Go 全仓 `grep -rn "X-Content-Type-Options" service/ --include=*.go` ⇒ **零命中**
⇒ ⇒ **nosniff 是 Go `net/http` 标准库 `http.Error` 自动加的**（不是显式代码）⇒ ⇒ 故断言写的是「**Go 的 `http.Error` 会加 nosniff**」
⇒ ⇒ ⇒ **本域不需要再落同款探针**（已闭合）⇒ ⇒ 已回函 G1 说明，避免它重复落码。
⇒ ⇒ 📌 **与 G1 的 admin 域那条的关系**：admin 域探针覆盖的是 **400 缺必填字段** 形态；我域覆盖的是 **404 `http.NotFound`** 形态
⇒ ⇒ **两域形态不同、但结论同源**（都落到 `net/http` 的自动加头）⇒ ⇒ 共享机制这一条**已有两个域的独立 wire 级证据**。

## 尚未处理

- 本模块的 `HUMAN_ONLY.MD`
- ~~D1（端到端双侧活体对拍）—— 这是 hot 域收口的最后一块实质工作~~ ⇒ ✅ **第五轮已闭合**（hot-api 侧）/ **第十一轮已闭合**（hot-rpc 侧）
- ~~剩余未闭合项**全部**落在域外：跨域移交 **D14 / D25 / D27**（代码在 `service/common`）+ 全仓约定待拍板 **D21 / D22 / D23**~~ ⇒ ✅ **2026-10-26 订正：这 6 条现已全部闭合**（D27 转为**已签字的有意偏离**）⇒ **本模块的未闭合项清单为空**


---
---

# 第二轮：组件级验证（2026-10-23）

> 本部分按 `.agents/specs/semantic-verification-prompt.md` §31-b 组织，
> §32–§37 的分析维度一个不丢。第一轮的历史内容全部保留在上文。

## 1. Component Contract（§4 + §5）

```text
Component:        hot-api（Go 侧 hotcenter）
Purpose:          对外提供「当前滚动窗口内的热门文章」公开只读列表
Public Operations: GET /hot/v1/articles?page=&page_size=   ← 全服务仅此 1 个端点
Inputs:           query 的 page / page_size（int32，键名 snake_case）
Outputs:          HTTP 200 + {code,msg,data{items,total,page,page_size,scope}}
Dependencies:     hot-rpc   GetHotArticles(TopK=100)   ← fatal，失败即整包 5001
                  article-rpc GetArticle(public_only=true, incr_view=false) × ≤100 ← best-effort，失败仅跳过该条
                  Prometheus registry（5 个 collector）
State:            无本地状态；只读端点，无事务、无缓存
Side Effects:     5 个指标计数；业务日志；**不写任何持久化**（incr_view=false ⇒ 阅读量不变）
Failure Model:    三种出口，见下表
```

**失败模型（三种形状，刻意不同，不可统一）**：

| 场景 | HTTP | body |
|---|---|---|
| 成功 | 200 | `{"code":200,"msg":"OK","data":{...}}` |
| 业务失败（hot-rpc 报错 / nil 响应 / query 解析失败 / 框架层异常） | **200** | `{"code":5001,"msg":"internal server error","data":null}` |
| 路由层 404 / 405 | 404 / 405 | **裸响应，无信封** |
| 超时 3s / 客户端断连 | 503 / 499 | `text/plain` + `Request Timeout\n`，**无信封** |
| 保护层拒绝（超连接/超字节/降载/熔断） | 裸状态码 | 空 body，无信封 |

## 2. GO-SEMANTIC-MODEL（§7）

```text
OP-01  GET /hot/v1/articles

Input:        page / page_size 原始 query 串（可重复、可带 [] 后缀、可带前导 +）
Precondition: 无（零鉴权；routes.go 只有 WithPrefix，没有 WithJwt）
Trigger:       路由匹配 GET /hot/v1/articles

Processing:
 1. observability 中间件用 context.WithTimeout(r.Context(), 3000ms) 建预算（observability/http.go:53）
 2. httpx.Parse：
     ParsePath → ParseForm（GetFormValues 丢弃空值 util.go:44-45、剥 [] 后缀 util.go:57-59、
             计数上限 2048 util.go:15,48-53、同名多值 WithFromArray 取 Index(0) unmarshaler.go:835-846）
             → ParseHeaders → ParseJsonBody（requests.go:66）
    ⚠️ ParseJsonBody 对本端点**无效**：字段只有 form tag 没有 json tag，
       usingDifferentKeys（utils.go:589-597）让 json unmarshaler 整字段跳过
    整数按 strconv.ParseInt(str,10,32)（utils.go:104-125）
 3. Parse 出错 → httpx.ErrorCtx → SetErrorHandler → 恒 200 + 5001
 4. normalizePagination：page<=0→1, pageSize<=0→20, pageSize>50→50
 5. hot-rpc GetHotArticles(TopK=100)
      rpcErr != nil  → FATAL   ：rpc_error_total{callee} + article_skip{hot_rpc_nil_response or 无} + 5001
      hotResp == nil → FATAL   ：article_skip{reason=hot_rpc_nil_response} + 5001
 6. hydrateArticles：每 item 一个 goroutine，sem 容量 8，按下标落位
      4 条 skip 分支（nil/空 id、RPC 错、article 缺失、status≠PUBLISHED），各带 metric
 7. rank = 过滤后稠密序号（分页前算，跨页连续）
 8. total = 过滤后窗口长度（与分页参数无关）
 9. paginate：start = int((page-1)*pageSize)  ← int32 乘法，溢出为负 ⇒ 空页
10. httpx.OkJsonCtx → SetOkHandler 包一层信封 → 200

External Calls: hot-rpc ×1（2s deadline，无 retry，breaker 开）；
                article-rpc ×N（N≤100，并发窗口 8，每线程 2s deadline）
Result:         200 + 信封
State Change:   无
Side Effects:   request_total{route,result}、request_duration_seconds{route}、
                rpc_error_total{callee}、article_skip_total{reason}（reject_total 零触发）
Error:          解析失败 → 200+5001；hot-rpc 失败 → 200+5001；article-rpc 失败 → 跳过该条
Timeout:        HTTP 3s（超时执行者只有一个：observability 中间件，
                go-zero 原生 Timeout 已被 DisableNativeRestTimeout 关掉），
                出口 = http.Error("Request Timeout", 503)；ctx.Canceled（客户端断连）→ 499
Retry:          无（RpcClientConf 无 Retries 字段）
Concurrency:    补全窗口 8（每次调用新建的 channel 信号量 ⇒ 并发请求互不占用）
Lifecycle:      gRPC conn 进程级；无连接池预热；无优雅停机定制
```

## 3. SPRINGBOOT-SEMANTIC-MODEL（§34，同结构）

```text
OP-01  GET /hot/v1/articles

Input:        @RequestParam(required=false) String page / page_size
Precondition: 无（无 interceptor、无 WebMvcConfig、无 SecurityFilter）
Trigger:       @RequestMapping("/hot/v1") + @GetMapping("/articles")

Processing:
 1. HttpMiddlewareFilter（@Order(HIGHEST+5)，依赖 scanBasePackages 含 com.rag.common）
    建 3s 预算（observability.http-timeout: 3s）
 2. GoFormValues.int32：null/"" → tag 默认值；Integer.parseInt(v,10) 越界/非法 → BusinessException(5001)
    ⚠️ 不读 request body；无 2048 上限；无 [] 后缀处理
 3. BusinessException(5001) → GlobalExceptionHandler → 恒 200 + 5001
 4. HotRankPaginator.normalize：同 Go
 5. hot-rpc GetHotArticles(TopK=100)，deadline = min(2000ms, 剩余预算)
      StatusRuntimeException → FATAL → 5001；null → FATAL → 5001
 6. HotArticleHydrator.hydrate：每请求 Semaphore(8)，先 acquire 再 submit 到 32 线程池，
    按下标落位；4 条 skip 分支各带 metric；捕 RuntimeException（比 Go 的 rpcErr!=nil 更宽）
 7. HotRankPaginator.assignRanks：过滤 null + 稠密 rank
 8. slice：total = 过滤后长度；paginate 用 int 复现 int32 溢出
 9. GlobalResponseAdvice.beforeBodyWrite 包一层（body==null / instanceof Response 放行）

External Calls: 同 Go，但 deadline 分两段：请求预算由 GoStyleGrpcCallDeadline 显式传播（ThreadLocal 不跨线程池），2s 上界由共享层拦截器施加（2026-10-31 第十三轮）
Result:         200 + 信封
State Change:   无
Side Effects:   同 5 个 Micrometer meter（名称/标签 1:1，分桶 = prometheus.DefBuckets）
Error:          同上；⚠️ 多一条 Exception 兜底（Go 侧由 Recover 中间件承担）
Timeout:        3s，出口 writeTimeoutResponse：status + text/plain + "Request Timeout\n"
                ⚠️ 缺 X-Content-Type-Options: nosniff（Go 的 http.Error 有）→ D14
Retry:          无
Concurrency:    Semaphore(8)/请求 + 32 共享池 + CallerRunsPolicy 背压
Lifecycle:      executor 由容器 destroyMethod 关闭
```

## 4. Equivalence Matrix（§24 + §35）

> Evidence 档：`Go✅`=Go 侧活体探针；`RAG✅`=真实 Boot 上下文探针；`双侧`=两侧同输入逐行比对；
> `静态`=两侧都读到消费点但无运行证据；`—`=不适用。

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| GET | 路由与前缀 | `/hot/v1/articles` GET | 同 | EQUIVALENT | Go✅+RAG✅ |
| GET | 零鉴权 | 无 WithJwt | 无 interceptor | EQUIVALENT | Go✅+RAG✅ |
| GET | 无 query → 默认 | 1 / 20 | 1 / 20 | EQUIVALENT | 双侧 |
| GET | `?page=5` | 5 / 20 | 5 / 20 | EQUIVALENT | 双侧 |
| GET | `?page_size=7` | 1 / 7 | 1 / 7 | EQUIVALENT | 双侧 |
| GET | `?page=`（空值丢弃） | 1 / 20 | 1 / 20 | EQUIVALENT | 双侧 |
| GET | `?page`（裸键） | 1 / 20 | 1 / 20 | EQUIVALENT | 双侧 |
| GET | `?page=-1`（不在 Web 层拒） | -1 / 20 | -1 / 20 | EQUIVALENT | 双侧 |
| GET | `?page=010`（base 10 非八进制） | 10 / 20 | 10 / 20 | EQUIVALENT | 双侧 |
| GET | `?page=0x10` | 解析失败→5001 | 5001 | EQUIVALENT | 双侧 |
| GET | `?page=99999999999`（int32 越界） | 越界→5001 | 5001 | EQUIVALENT | 双侧 |
| GET | `?page= 5`（前导空格） | 失败→5001 | 5001 | EQUIVALENT | 双侧 |
| GET | `?page=5.0` | 失败→5001 | 5001 | EQUIVALENT | 双侧 |
| GET | `?page=abc` | 失败→5001 | 5001 | EQUIVALENT | 双侧 |
| GET | **`?page=5&page=3`（重复键）** | **5 / 20** | **5001** | **SEMANTIC MISMATCH** | 双侧 + RAG✅真实Boot |
| GET | `?page[]=5`（数组后缀，单值） | 5 / 20 | 5 / 20 | EQUIVALENT | 双侧 |
| GET | **`?page[]=5&page[]=9`（重复）** | **5 / 20** | **5001** | **SEMANTIC MISMATCH** | 双侧 |
| GET | `?page=+5` / `%2B5`（前导 + 号） | 5 / 20 | 5 / 20（`param()` 实测） | EQUIVALENT | Go✅+RAG✅；⚠️ 见 Pitfall P4 |
| GET | **form 参数 2048 个** | OK | OK | EQUIVALENT | Go✅ |
| GET | **form 参数 2049 个** | **失败→5001** | **接受并成功** | **OBSERVABLE DIFFERENCE** | Go✅；RAG 静态（无该限制） |
| GET | **body = 合法 JSON `{"page":9}`** | **被忽略**（仍 1/5） | **被忽略**（不读 body） | **EQUIVALENT** | Go✅+RAG✅ ⇒ 修正 D7 |
| GET | **body = 畸形 JSON `{`（CT=json）** | **失败→5001** | **成功** | **OBSERVABLE DIFFERENCE** | Go✅；RAG 静态 |
| GET | 解析失败出口 | 200 + 5001 | 200 + 5001 | EQUIVALENT | 双侧 |
| GET | 成功信封层数 | 1 层 | 1 层 | EQUIVALENT | RAG✅ |
| GET | 错误信封 | `{code:5001,msg,data:null}` | 逐字节同 | EQUIVALENT | 双侧（Go✅ wire + RAG✅ wire） |
| GET | 空页 items | `[]` 非 null | `[]` 非 null | EQUIVALENT | 双侧 wire |
| GET | `secondary_tags` 空 | `null` | `null` | EQUIVALENT | 双侧 wire |
| GET | `hot_score` uint32 极值 | `4294967295` | `4294967295` | EQUIVALENT | 双侧 wire |
| GET | HTML 字符转义 | `<b>x</b>` / `a&b` **不转义** | 同（jsonx 显式 SetEscapeHTML(false)） | EQUIVALENT | Go✅（`core/jsonx/json.go:18`）+ RAG✅ |
| GET | 信封顶层字段序 | `code,msg,data` | `code,msg,data` | EQUIVALENT | 双侧 wire |
| GET | **items 内部字段序** | **声明序** `article_id,rank,hot_score,title,…` | **字母序** `article_id,author_id,brief,…` | **OBSERVABLE DIFFERENCE** | RAG✅真实Boot（裸 mapper ≠ 运行时） |
| GET | 404 状态/体/头 | 404 + `404 page not found\n` + text/plain + nosniff | 逐项同 | EQUIVALENT | Go源码 + RAG✅（⚠️ MockMvc，待真 Tomcat） |
| GET | 405 状态/体/头 | 405 + `Allow` + 无 body 无 CT | 同 | EQUIVALENT | 同上 |
| GET | 分页归一化（8 组） | 见 D-numeric | 同 | EQUIVALENT | 双侧 25 行 diff 为空 |
| GET | paginate 溢出（17 组含 int32 溢出） | 见 D-numeric | 同 | EQUIVALENT | 双侧 25 行 diff 为空 |
| GET | 4 条 skip 分支 + metric | 逐条 | 逐条 | EQUIVALENT | 静态 + Go `_test.go` |
| GET | fatal / best-effort 不对称 | fatal / best-effort | 同 | EQUIVALENT | 静态 + 双侧测试契约 |
| GET | 并发窗口 8 | 每请求新建信号量 | 每请求 Semaphore(8) | EQUIVALENT | 静态 + RAG 峰值断言 |
| GET | 按下标保序 | `hydrated[idx]=` | 下标数组 | EQUIVALENT | 静态 + RAG 乱序断言 |
| GET | gRPC deadline | 2s 且嵌套于 3s | min(2s, 剩余预算) | EQUIVALENT | 静态（两侧都读到消费点） |
| GET | 无 retry | 无 Retries 字段 | 无 | EQUIVALENT | 静态 |
| GET | client breaker | 默认开 | 全局拦截器 | ✅ **已闭合（第十轮）** | 🟥 原标 UNKNOWN：**第十轮**取得行为级 E2E 证据（内嵌恒返 INTERNAL 的 gRPC server ⇒ 判据是「**下游调用次数停止增长**」）⇒ 熔断真的打开已被坐实；装配层 3 例另有 `GoStyleGrpcClientBreakerWiringTest` |
| GET | HTTP 超时时长 | 3s | 3s | EQUIVALENT | 静态 |
| GET | 超时出口状态码/体 | 503 + `Request Timeout\n` | 503 + 同体 | EQUIVALENT | 🟥 原写「未活体观测」：**第五轮 CASE-21** 已活体坐实（article-rpc 睡 30s 拖过 3s ⇒ 两侧状态码与 body 逐字节同，**只差 `nosniff`** = D14）|
| GET | **超时/499 响应头 nosniff** | **有** | **无** | **OBSERVABLE DIFFERENCE** | 静态双侧 |
| GET | 断连 499 | `errors.Is(Canceled)`→499 | IOException→499 | **NOT EQUIVALENT** | 🟥 原标 UNKNOWN：**第七轮**已活体坐实（裸 socket + `SO_LINGER=0` 强制 RST ⇒ Go 侧 8 个在飞 gRPC 在断连后 1~3ms 全部取消、**日志有 `[HTTP] 499`**；RAG 侧**等 2s deadline**、断连后继续发下游调用、**无任何 499 记录**）⇒ **D3 坐实** + 产出 `DRIFT-HOTAPI-025`（代码在 `service/common` ⇒ 跨域）|
| GET | 保护层四闸 | 默认开（tag） | `rag.protection.*` | ✅ **三闸已闭合（第八轮）** | 🟥 原标 UNKNOWN：**MaxBytes / MaxConns / Breaker 三闸双侧活体坐实**；🔴 **CpuShedding 在 darwin 上 Go 侧结构性不可达**（`//go:build !linux` ⇒ `CpuUsage()` 恒 0）⇒ 该闸只能静态 + go-zero 上游测试取证 |
| GET | 指标名/标签 | 5 个 collector | 5 个 Micrometer meter | EQUIVALENT | 静态 + RAG 断言 |
| GET | 指标暴露 | 无端点 | /actuator/prometheus | OBSERVABLE DIFFERENCE（**有意单向增强**，已签字 D5） | 静态 |
| — | Transaction boundary | 无事务 | 无事务 | EQUIVALENT | — |
| — | Cache hit/miss | 无缓存 | 无缓存 | — | — |
| — | Authentication | 零鉴权 | 零鉴权 | EQUIVALENT | 双侧 |
| — | Lifecycle | 进程级 conn | 容器托管 | EQUIVALENT（实现差异） | 静态 |

## 5. External Interaction & Error Flow（§8 + §10）

### 外部系统：hot-rpc（gRPC）

```text
Operation:  HotService/GetHotArticles(GetHotArticlesRequest{top_k=100})
Go 参数:    TopK: maxHotArticles(100)  —— 硬编码，与 page/page_size 无关（logic:62-64）
Java 参数:  setTopK(100)              —— 同
Failure:    Go rpcErr != nil → FATAL；Java StatusRuntimeException → FATAL
Does Failure Block Operation: 是（整包 5001）。article-rpc 相反：不阻断
Side Effect: rpc_error_total{callee="hot_rpc_get_hot_articles"}
Timeout:     Go 2s（zrpc/config.go:31 tag 默认 → timeoutinterceptor.go:25）
             Java min(2s, 请求剩余预算) = 共享层上界（cap-millis=2000）⊗ 域侧传播（GoStyleGrpcCallDeadline，2026-10-31 第十三轮）
Retry:       无
⚠️ 依赖前提：TopK=100 被下游如何解释（是否封顶、items 是否按热度降序）
   属 hot-rpc 组件，必须与 hot/rpc.md 合并判读 → 见 U7
```

### 外部系统：article-rpc（gRPC，×≤100）

```text
Operation:  ArticleService/GetArticle{article_id, incr_view=false, public_only=true, requester_id=""}
Go:         显式传 PublicOnly/IncrView 于调用点（logic:132-136）
Java:       facade 硬编码在唯一出口（ArticleRpcClient:59-62）—— 语义等价论证见其类注释
Failure:    两条路径都是 best-effort → 跳过该条 + rpc_error_total + article_skip_total
Does Failure Block Operation: 否（这是与 hot-rpc 的核心不对称）
Side Effect: incr_view=false ⇒ 不改阅读量；public_only=true ⇒ 取公开投影（改稿中文章不被误杀）
Timeout/Retry: 同上
```

### Error Flow 明细（§10）

| Failure | 触发条件 | Go 处理 | Go 输出 | Java 处理 | Java 输出 | 等价 |
|---|---|---|---|---|---|---|
| E1 query 解析失败 | 非法 int32 | `httpx.ErrorCtx` + SetErrorHandler（非 CodeError ⇒ 5001） | 200 `{code:5001}` | `BusinessException(5001)` | 200 `{code:5001}` | YES |
| E2 form 参数超限 | >2048 | `GetFormValues` 返回 error → 同 E1 | 200 `{code:5001}` | 无此限制 | 200 成功 | **NO** |
| E3 body 畸形 JSON | CT=json 且 JSON 非法 | `ParseJsonBody` 失败 → 同 E1 | 200 `{code:5001}` | 不读 body | 200 成功 | **NO** |
| E4 重复 query 键 | `?page=5&page=3` | 取首值成功 | 200 正常信封 | 拼 `"5,3"` → 解析失败 | 200 `{code:5001}` | **NO** |
| E5 hot-rpc 报错 | gRPC error | `metrics` + `logBusinessErr` + 返回 CodeError | 200 `{code:5001}` | 同 | 200 `{code:5001}` | YES |
| E6 hot-rpc nil 响应 | 防御分支 | `article_skip{hot_rpc_nil_response}` + 5001 | 200 `{code:5001}` | 同 | 200 `{code:5001}` | YES |
| E7 article-rpc 报错 | 单条失败 | 跳过 + 2 metric + 日志 | 200 正常（少一条） | 同 | 200 正常（少一条） | YES |
| E8 article 缺失 | resp/article nil | `article_skip{article_missing}` | 200 正常 | 同 | 200 正常 | YES |
| E9 非 PUBLISHED | status≠2 | `article_skip{article_not_published}` | 200 正常 | 同 | 200 正常 | YES |
| E10 路由不存在 | 路径不匹配 | `http.NotFound`（**不进 chain**） | 404 裸 | `NoResourceFoundException` → 裸 404 | 404 裸 | YES |
| E11 方法不匹配 | 方法不匹配 | `Allow` + 405（**不进 chain**） | 405 裸 | 裸 405 + Allow | 405 裸 | YES |
| E12 HTTP 超时 | >3s | `http.Error(503)` | 503 text/plain | `writeTimeoutResponse(503)` | 503 text/plain | 值 YES / 头 NO（D14） |
| E13 客户端断连 | ctx.Canceled | `http.Error(499)` | 499 text/plain | IOException→499 | 499 text/plain | **UNKNOWN**（D3） |
| E14 未捕获 panic | logic 意外 | Recover 中间件 → SetErrorHandler | 200 `{code:5001}` | `@ExceptionHandler(Exception)` | 200 `{code:5001}` | YES |

## 6. 逐维度结论（§12–§21）

### §12 State Semantics
本组件**无状态机**：只读、无缓存、无事务。初始态/迁移态不适用。
唯一状态是**请求内**的 `hydrated[]` 数组：Go `make([]*T, len)` 全 nil 起 → 按下标填 → 过滤 nil → 稠密 rank；
Java 同构。**EQUIVALENT**（静态 + 双侧测试契约）。

### §13 Cache Semantic Equivalence
**两侧都无缓存**（Go 无 Redis / 无 `zcache`；RAG 无 `@Cacheable`）。不适用。

### §14 Authentication / Authorization Semantic Equivalence
```text
Go:    routes.go:16-25 只有 rest.WithPrefix("/hot/v1")，无 WithJwt / 无 WithBlacklist
       ⇒ 匿名可读；public 路径集合 = {全部}；无 blacklist key；无 fail-open/fail-closed 判据
Java:  HotApiApplication 刻意不注册任何 interceptor/SecurityFilter；HotArticleController 无注解
       ⇒ 匿名可读（@SpringBootTest 匿名访问即证）
```
⇒ **EQUIVALENT**。⚠️ 本组件**没有** JWT / 黑名单中间件，任务简报里「blacklist key 前缀与 fail-open」一条**对本组件不适用**——不得凭空制造一个「已验证」结论。

### §15 Transaction Semantic Equivalence
两侧**均无事务**：无 DB、无多写。article-rpc 的 `incr_view=false` 确保补全是纯读。
⇒ 不适用 / EQUIVALENT。

### §16 Concurrency Semantic Equivalence
```text
Go:    每个 item 起一个 goroutine（logic:101-109），容量 8 的 channel 信号量**每次调用新建**
       ⇒ 并发请求之间互不占用；结果写 hydrated[idx]，与完成顺序无关；wg.Wait() 汇合
Java:  每请求 new Semaphore(8)，**先 acquire 再 submit** 到 64 共享线程池（8 请求 × 8）
       ⇒ 每请求同样享有完整 8 在途；results 按下标落位
差异:  Go 的 goroutine 廉价、可无限等待；Java 的 64 池 + 64 队列 + CallerRunsPolicy
       ⇒ **跨请求的合计在途**被池卡在 64（Go 无上限）⇒ 属**已签字偏离** SD-HOT-API-010
       ⇒ 并发 ≤ 8 时完全等价；> 8 时 RAG 多一层排队（延迟分布不同，结果相同）
```
⇒ 常态 EQUIVALENT；**极端并发下 OBSERVABLE DIFFERENCE（延迟分布，非结果）**。

> 🔴 **第十轮（U10）活体实测订正 + 已修**（本节原结论有两处已被推翻/升级）：
> ① ~~「池满时由请求线程自己跑」（Go 纯排队，Java 可能串行执行补全）~~ ——
>    **实测 `CallerRunsPolicy` 从未触发**（下游在途峰值**未超过**池容量）
>    ⇒ 两侧背压形态**同为纯排队**；
> ② 「并发峰值 ==8 已由 RAG 侧断言（纯 Mockito + 真实 Semaphore，非双侧）」—— 升级为
>    **双侧活体 + 归因到具体参数**：下游在途峰值 **Go 64（= 8 请求 × 每请求 8，完整保留）
>    vs RAG 32（= 旧池容量，腰斩）** ⇒ p99 **1.76×** ⇒ **DRIFT-HOTAPI-028**
>    ⇒ **已修**：池 32→64 ⇒ 复测**峰值 64 vs 64、p99 1.08×、p50 0.96×**
>    ⇒ 残余差异转 **SD-HOT-API-010**。
>    （脚本：`tools/difftest/probe-hot-latency.py`；判别器：桩的 ENTER/EXIT 事件日志算峰值）
> ⚠️ **绝对延迟值不可跨轮比较**（机器负载在变）⇒ **只有同轮比值有意义**。

### §17 Timeout Semantic Equivalence
```text
Go:    3s（rest/config.go:55 tag 默认，yaml 未写）。唯一执行者是 observability 中间件
       （DisableNativeRestTimeout 先把 go-zero 原生 Timeout 关掉，http.go:30-34）
       作用域 = 整个操作；结果 = 503 + "Request Timeout"（Canceled 则 499）
       gRPC 2s 嵌在 3s 之内 ⇒ 先到者生效
Java:  3s（observability.http-timeout: 3s），作用域同为整个操作
       gRPC = min(2000ms, 剩余预算)（GoStyleRequestDeadline 在请求线程取一次后显式下传）
```
⇒ 时长与作用域 **EQUIVALENT**；**出口响应头不等价**（D14：缺 `nosniff`）。
⚠️ 3s 实际触发**未活体观测**（D9/U2）⇒ 该行整体证据强度 = 静态。

### §18 Retry Semantic Equivalence
两侧**均无 retry**。Go 侧 `zrpc.RpcClientConf`（config.go:24-35）确认无 `Retries` 字段
（注意勿与 `zrpc.ClientConf` 混淆）。⇒ EQUIVALENT（静态，已读到配置定义）。

### §19 Serialization Semantic Equivalence
```text
编码器:  Go = jsonx.Marshal（core/jsonx/json.go:12-30，**显式 SetEscapeHTML(false)**，:18）
        Java = Spring Boot 装配的 Jackson
字段名:  两侧 13 + 5 字段名逐条一致（首轮已对，本轮 wire 探针再次确认）
空/缺:   Go unset string = ""；proto3 repeated 空 = nil → JSON null
        Java unset string 由 protobuf 返回 "" → ""；secondary_tags 构造处显式置 null → null
uint32:  Go 写无符号十进制；Java 需 Integer.toUnsignedLong（HotArticleHydrator:199）
时间:    create_time 是 int64 毫秒/秒原样透传，两侧均不做时区转换（本组件不做序列化）
HTML:    两侧均不转义 < > & ⇒ EQUIVALENT（Go 侧靠 jsonx:18，Jackson 默认亦不转义）
字段序:  ⚠️ Go = 声明序；Java 运行时 = 字母序 ⇒ OBSERVABLE DIFFERENCE（D15）
```
⚠️ 本轮**先验错了再纠正**：曾假设 Go 的 `jsonx` 会把 `<` 转成 `\u003c`，
读到 `json.go:18` 的 `SetEscapeHTML(false)` 后推翻 —— **「Go 转义 HTML」是错的**。

### §20 Configuration Semantic Equivalence
| 配置 | Go 生效值 | 出处 | RAG | 判定 |
|---|---|---|---|---|
| HTTP Timeout | **3000 ms** | `rest/config.go:55` tag（**yaml 未写**） | `observability.http-timeout: 3s` | EQUIVALENT |
| gRPC Timeout | **2000 ms** | `zrpc/config.go:31` tag（**yaml 未写**） | `GoStyleGrpcCallDeadline` 常量 | EQUIVALENT |
| MaxConns | 10000 | `rest/config.go:52` tag | `rag.protection.max-conns` | EQUIVALENT（静态） |
| MaxBytes | 1048576 | `rest/config.go:53` tag | `rag.protection.max-bytes` | EQUIVALENT（静态） |
| CpuThreshold | 900‰ | `rest/config.go:56` tag | `rag.protection.cpu-shedding` | EQUIVALENT（静态） |
| client Breaker | on | `zrpc/internal/config.go:14` tag | 全局拦截器 | UNKNOWN（D8） |
| client Timeout 中间件 | on | `zrpc/internal/config.go:15` tag | 显式 deadline | EQUIVALENT |
| Port | 8893 | `etc/hotcenter.yaml:3` | 18897 | **有意偏离，待拍板（D6）** |
| 服务发现 | etcd `hot.rpc` | `etc/hotcenter.yaml:25-34` | Nacos `discovery:///hot-rpc` | 实现差异（寻址机制换实现） |

⚠️ **本条再次演示 AGENTS.md §3.3.1**：9 个配置项里 **6 个在 Go 的 yaml 里一个字都没写**，
逐字段对 yaml 永远看不见；必须读到 struct tag 的**消费点**（如 `DisableNativeRestTimeout` 的 `conf.Timeout` 读取）。

### §21 Lifecycle Semantic Equivalence
Go：gRPC conn 在 `NewServiceContext` 建立，进程级；无预热、无优雅停机定制。
Java：`@GrpcClient` stub 由 devh 管理；补全线程池由容器 `destroyMethod` 关闭。
⇒ EQUIVALENT（实现不同，生命周期边界相同）。

## 7. Semantic Drifts（§27 + §36）

### DRIFT-HOTAPI-011 —— 重复 query 参数：Go 取首值，Spring 拼成逗号串后整包 5001
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:200-216`（`firstFormValue` ⇒ `getParameterValues(name)` 的**首个非空值** ⇒ `?page=5&page=3` ⇒ `page=5`）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/controller/HotArticleController.java:91`（调用点，已不用 `@RequestParam`）· Go `rest/httpx/util.go:35`（同名多值收进同一个 `[]string`）+ `rest/httpx/requests.go:27-31`（`formUnmarshaler` 带 `mapping.WithFromArray()`）+ `core/mapping/unmarshaler.go:835-846`（非 slice 字段且值是数组 ⇒ `val.Index(0)`）· 核对日期 2026-10-30。

```text
Component:    hot-api / GET /hot/v1/articles
Category:     Input Interpretation（重复键的多值语义）
Severity:     SEMANTIC MISMATCH（Go 成功 → Java 拒绝，方向是「Go 能用 / Java 不能用」）
Risk:         MEDIUM

Go Behavior:
  Input  ?page=5&page=3  （或 ?page[]=5&page[]=9）
  → GetFormValues 把同名多值收进一个 []string（httpx/util.go:60）
  → formUnmarshaler 带 WithFromArray()（httpx/requests.go:27-31）
  → unmarshaler.go:835-846 fromArray 分支：非 slice 字段且值是数组 ⇒ val.Index(0)
  ⇒ Page = 5，**成功**
Java Behavior:
  → @RequestParam(required=false) String 拿到的是 Spring 把 String[] 用 "," 拼成的 "5,3"
    （实测：?page=5&page=3 ⇒ raw="5,3"；?page[]=5&page[]=9 ⇒ raw="5,9"）
  → GoFormValues.int32("5,3") → Integer.parseInt 失败 → BusinessException(5001)
  ⇒ 200 {"code":5001,...}，**整包失败**

Observable Impact:
  任何会重复传同名参数的客户端（代理层重试参数、部分 SDK 的数组展开、
  手工构造 ?ids=&ids= 这类 URL）从「正常返回第 1 页」变成「整包 5001」。
Must Preserve:
  重复键取**首值**（Go 的 Index(0)），不是逗号拼接、也不是「取最后一个」或报错。
Evidence:
  ✅ Go 侧活体探针（真 go-zero v1.10.2 httpx.Parse，逐字复制 types.HotArticlesReq）：
       ?page=5&page=3      → OK  page=5 pageSize=20
       ?page[]=5&page[]=9  → OK  page=5 pageSize=20
  ✅ RAG 侧真实 Boot 上下文（@SpringBootTest + @AutoConfigureMockMvc）：
       ?page=5&page=3      → {"code":5001,"msg":"internal server error","data":null}
  ✅ RAG 侧 raw 绑定值探针：page="5,3" / page="5,9"
  ⚠️ 单值 ?page[]=5 两侧都是 5 ⇒ 「[] 后缀」本身不是漂移，**只有重复才是**。
Recommended Verification:
  在 GoFormValues 侧或 controller 侧改为取首个值（与 Go 的 Index(0) 对齐），
  并补一条 ?page=5&page=3 的 MockMvc 断言（当前 59 例里**没有**这条）。
```

### DRIFT-HOTAPI-012 —— form 参数总数上限 2048（✅ 已实现，见下方状态段）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已实现** ⇒ **现行态 = ✅ 已实现**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:224-245`（`requireFormValueCountWithinLimit`：`n++` 后 `n > 2048` 即抛 ⇒ 第 **2049** 个非空值报错）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/controller/HotArticleController.java:88`（调用点在解析 `page` **之前**）· Go `rest/httpx/util.go:15`（`maxFormParamCount = 2048`）+ `rest/httpx/util.go:48-53`（`n < maxFormParamCount` 否则 `too many form values`）· 核对日期 2026-10-30。

```text
Component:    hot-api / GET /hot/v1/articles
Category:     Input Limits
Severity:     OBSERVABLE DIFFERENCE（Go 拒绝 / RAG 接受）
Risk:         LOW

Go Behavior:
  httpx/util.go:15  maxFormParamCount = 2048
  httpx/util.go:48-53  if n < maxFormParamCount {...} else { return nil, fmt.Errorf("too many form values, ...") }
  ⇒ 第 2049 个值即整体报错 → SetErrorHandler → 200 {"code":5001}
  实测：2048 个值 OK；2049 个值 ERR
Java Behavior:
  @RequestParam 只绑 page / page_size 两个名字；Servlet 容器另有自己的 URL 长度上限，
  但**没有「form 值个数」这个计数器** ⇒ 2049 个垃圾参数照常 200 成功。
Must Preserve:
  「超过 2048 个 form 值 ⇒ 5001」这条拒绝路径。
  ⚠️ 但它也可以被判定为**不值得复刻的 Go 既有怪癖**——需要一次显式决策，不要默认忽略。
Evidence: ✅ Go 侧活体探针（2048 OK / 2049 ERR）；✅ RAG 侧已实现并有真实 MVC 管线测试。

**✅ 状态（2026-10-24 复核）：本条早已实现，台账此前记成「RAG 侧无对应代码 / 需拍板」是错的。**
- **实现**：`GoFormValues.requireFormValueCountWithinLimit(HttpServletRequest)`
  —— 遍历 `request.getParameterMap()`（Servlet 侧 query + POST form 合并，与 Go 的 `r.Form` 同域），
  空值跳过（Go `util.go:44-45`），`n > 2048` 即抛 `BusinessException(5001)`；
- **调用点**：`HotArticleController.java:88`（在解析 `page` / `page_size` **之前**）；
- **边界与 Go 一致**：Go 是 `if n < 2048 { append; n++ } else { error }`
  ⇒ 第 **2049** 个非空值报错；RAG 是 `n++; if (n > 2048) throw` ⇒ 同样是第 **2049** 个报错；
- **错误形状与 Go 一致**：Go 的 `fmt.Errorf` 是**普通 error**（非 `*CodeError`）
  ⇒ `SetErrorHandler` 只取 code/msg ⇒ 恒 `5001 + "internal server error"`，**错误详情不进 wire**；
- **守门测试**：`HotApiGoParseParityTest#formValueCountAtLimitIsAccepted`（2048 → 200）
  与 `#formValueCountOverLimitIsRejected`（2049 → 5001 + `internal server error`）。
```

### DRIFT-HOTAPI-013 —— 畸形 JSON body：Go 拒绝，RAG 完全忽略 body
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:267-312`（`requireParsableJsonBody` ⇒ 先用 `GoStyleJsonBodyConverter.goReadsBody` 判 `ContentLength>0 && Content-Type 含 application/json`，再流式 `readTree`；语法错误 / 顶层非对象 ⇒ `BusinessException(5001)`）· Go `rest/httpx/requests.go:66-68`（`ParseJsonBody` 在 `Parse` 里**无条件**执行）+ `rest/httpx/requests.go:153-155`（`withJsonBody` 判据）· 核对日期 2026-10-30。

```text
Component:    hot-api / GET /hot/v1/articles
Category:     Input Interpretation（body 的存在性）
Severity:     OBSERVABLE DIFFERENCE
Risk:         LOW-MEDIUM

Go Behavior:
  httpx.Parse 的最后一步无条件跑 ParseJsonBody（requests.go:66-68）
  ParseJsonBody → withJsonBody(r)：r.ContentLength > 0 && Content-Type 含 application/json
                  （requests.go:153-155）
  → 成立则 mapping.UnmarshalJsonReader（requests.go:119-120）
  ⇒ **JSON 语法非法即在解析阶段报错** → 5001
  实测：?page=5 + Content-Type: application/json + body `{` → ERR（unexpected EOF）→ 5001
Java Behavior:
  controller 只读 query，从不碰 body ⇒ 同样的请求 **200 正常成功**
Must Preserve:
  ⚠️ 注意区分两件事（这是 D7 修正后的关键）：
    (a) **合法** JSON body 对本端点**无效**（字段无 json tag ⇒ 整字段跳过）⇒ 两侧「忽略」是一致的；
    (b) **畸形** JSON body 在 Go 是**一条真实的拒绝路径** ⇒ RAG 缺失。
  本条只针对 (b)。
Evidence: ✅ Go 侧活体探针；⚠️ RAG 侧为静态判定。
Recommended Verification:
  先决策「是否复刻这条极冷拒绝路径」。若不复刻，需在台账签字，
  否则下一个读代码的人会再次把它当成 D7 那样「记成未实现」。
```

### DRIFT-HOTAPI-014 —— 超时 / 499 响应缺 `X-Content-Type-Options: nosniff`
> 🔴 **2026-10-30 真实性复核（favorite+hot lane）**：原判 **🔁 跨域移交 common 域（待 common 域处理）** ⇒ **现行态 = ✅ 已闭合（nosniff 已实现）**（**改判**）。
> 依据：RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:728`（`writeTimeoutResponse` **已写** `resp.setHeader("X-Content-Type-Options", "nosniff")` ⇒ 503（超时）与 499（断连）**两条路径都带**）· Go `service/common/observability/http.go:88`（`http.Error(w, httpTimeoutBody, statusCode)` ⇒ Go `net/http` 的 `http.Error` 必设 `X-Content-Type-Options: nosniff`）· 核对日期 2026-10-30。
> ⇒ ⇒ **本条标题里「RAG 侧缺 nosniff」这个前提已不成立**（**原文保留，不改写**）⇒ 与本文件 `:141` 的 D14 行、`:180` 的「2026-10-26 第二十八轮订正」表（`D14 nosniff` = ✅ **已实现**）以及 [`vs-common.md`](./vs-common.md) 的 `DRIFT-HOTAPI-014` 行口径一致 ⇒ **不再需要 common 域返工**。

```text
Component:    hot-api / 超时与断连出口
Category:     Response Headers
Severity:     OBSERVABLE DIFFERENCE
Risk:         LOW（硬化头缺失；无业务判定依赖）

Go Behavior:
  observability/http.go:88   http.Error(w, httpTimeoutBody, statusCode)
  net/http/server.go:2337-2356  http.Error 做四件事：
      h.Del("Content-Length")
      h.Set("Content-Type", "text/plain; charset=utf-8")
      h.Set("X-Content-Type-Options", "nosniff")   ← 本条关注点
      w.WriteHeader(code); fmt.Fprintln(w, error)
  ⇒ 503（超时）与 499（断连）**两条路径都带 nosniff**
Java Behavior:
  HttpMiddleware.writeTimeoutResponse（:419-425）
      setStatus / setContentType / write(body) / write('\n') / flush
  ⇒ **没有** nosniff；且这条路径同时服务 503 与 499
  ⚠️ 对照：404 路径的 GoStyleRouterErrorResponses:98 **是**设了 nosniff 的，
     实测真实 Boot 上下文也确认 404 带 nosniff ⇒ **同一个组件里两条路径的形状不一致**
Observable Impact:
  客户端拿到的响应少一个硬化头。业务判定（code/msg/status）不受影响。
Must Preserve:
  「凡走 http.Error 形状的响应都带 nosniff + text/plain + 尾随换行」。
Evidence: ⚠️ **静态双侧**（两侧都读到写出点），未活体观测。
  ⚠️ 按 AGENTS.md §3.3「代码里写了 ≠ 客户端收到」，
     响应头必须真 Tomcat 取证一次（MockMvc 测不到，见 Pitfall P5）。
```

### DRIFT-HOTAPI-015 —— 运行时 JSON 字段顺序为字母序，与 Go 声明序不同

```text
Component:    hot-api / 成功信封的 data
Category:     Serialization（字节级）
Severity:     OBSERVABLE DIFFERENCE（JSON 语义等价；**字节级不等价**）
Risk:          MEDIUM（本项目 difftest 按字节比对；任何按字节断言的客户端/看板会红）

Go Behavior:
  types.go:6-20 的**声明序**：
    article_id, rank, hot_score, title, brief, cover_image_url, author_id,
    create_time, view_count, like_count, comment_count, manual_type_tag, secondary_tags
  go-zero 走 jsonx.Marshal（core/jsonx/json.go:12-30）⇒ 按结构体声明序输出
Java Behavior:
  ⚠️ **运行时用的是 Spring Boot 装配的 mapper**，它开启了
     MapperFeature.SORT_PROPERTIES_ALPHABETICALLY ⇒ 输出为**字母序**：
    article_id, author_id, brief, comment_count, cover_image_url, create_time,
    hot_score, like_count, manual_type_tag, rank, secondary_tags, title, view_count
  而**裸 new ObjectMapper() 与 Jackson2ObjectMapperBuilder.json().build() 都是 false（声明序）**
  ⇒ 字段值、类型、null 语义两侧**逐字节等价**，**只有顺序不同**。

🔴 这解释了为什么 `HotApiWireContractTest` 抓不到它：
  该测试用**裸 ObjectMapper** 断言（首轮结论第 25 行写的正是这一点），
  裸 mapper 与 Go 同为声明序 ⇒ **测试永远绿，而运行时是字母序**。
  这是 AGENTS.md §3.3「断言 Bean 存在 ≠ 断言机制生效」的又一实例：
  **断言用哪个 mapper ≠ 运行时用哪个 mapper**。

Evidence:
  ✅ Go 侧 wire 探针（jsonx.Marshal + 逐字复制的 13 字段 struct）
  ✅ RAG 侧真实 Boot 上下文（@SpringBootTest + @AutoConfigureMockMvc 原始响应体）
  ✅ 对照实验：Jackson2ObjectMapperBuilder.json().build() 的
     SORT_PROPERTIES_ALPHABETICALLY = **false** ⇒ 差异来自 Boot 的 JacksonAutoConfiguration
  ⚠️ 本轮**先验错了再纠正**：最初以为是 standalone 的问题，
     实测 standalone 与真 Boot **都是字母序** ⇒ 是装配层，不是测试写法。
Must Preserve:
  若 difftest 要求逐字节，则需在 Boot 侧关掉该 feature（Jackson2ObjectMapperBuilderCustomizer）
  或把 difftest 改成「JSON 语义比对」。
Recommended Verification:
  把 HotApiWireContractTest 改成用**运行时那个 mapper**（注入 ObjectMapper bean），
  否则该测试对字段序永久无效。
```

## 8. Test Cases（§29 + §30）

> 双向对拍表。Go 列 = 本轮 Go 侧活体探针实测值；RAG 列 = MockMvc / 真实 Boot 上下文实测值。
> ✅ = 已双侧实测；⚠️ = 仅静态。

| CASE | 输入 / 环境 | Go | RAG | 等价 |
|---|---|---|---|---|
| CASE-01 正常成功 | 无 query，hot-rpc/article-rpc 正常 | 200 `{code:200,page:1,page_size:20}` | 同 | ✅ |
| CASE-02 空输入 | `?page=` / `?page` / `?page_size=` | 1 / 20（**取默认，不报错**） | 1 / 20 | ✅ |
| CASE-03 不存在 | 空 items | `"items":[]`, total=0 | 同 | ✅ |
| CASE-04 重复 | `?page=5&page=3` | 5 / 20 | 5 / 20 | ✅ **活体实测**（第五轮） |
| CASE-04b 重复（数组记法） | `?page[]=5&page[]=9` | 5 / 20 | 5 / 20 | ✅ **第十三轮已修**（`DRIFT-HOTAPI-021`）⇒ 配 `relaxed-query-chars: ["[", "]"]` 后两侧 **200 且响应体逐字节相同**。⚠️ 中间的 🔴（第五轮推翻第二轮的 ✅）**成立过**：那是因为 MockMvc 测的 |
| CASE-04c 单值数组记法 | `?page[]=5` | 5 / 20 | 5 / 20 | ✅ **第十三轮已修**（`DRIFT-HOTAPI-021`）⇒ 同上 |
| CASE-05 外部依赖失败（fatal） | hot-rpc 报错 | 200 `{code:5001}` | 200 `{code:5001}` | ✅ **活体实测**（第五轮，逐字节相同） |
| CASE-05b 外部依赖失败（best-effort） | 单条 article-rpc 报错 | 跳过该条，总数减 1 | 同 | ✅ **活体实测**（第五轮：`normal` 模式 total=3 vs `all-ok` 模式 total=4） |
| CASE-06 超时 | 令下游 >3s | 503 + `Request Timeout\n` + nosniff | 503 + 同体，**缺 nosniff** | 🟡 **第五轮已活体触发**：两侧**状态码与 body 逐字节相同**，差的是 `X-Content-Type-Options`（**D14**，跨域移交 common 域） |
| CASE-07 Retry | — | 无 | 无 | ✅ |
| CASE-08/09 Cache Hit/Miss | — | 无缓存 | 无缓存 | — |
| CASE-10 权限失败 | — | 零鉴权，无此路径 | 零鉴权 | ✅ |
| CASE-11 并发调用 | 多请求 × 100 items | 每请求窗口 8 | 每请求 Semaphore(8) | ✅ 静态 + RAG 断言 |
| CASE-12 重复调用（幂等） | 同参多次 | 无副作用（incr_view=false） | 同 | ✅ |
| CASE-13 部分操作成功 | 4 条 skip 各一 | 逐条 metric + 少 N 条 | 同 | ✅ **活体实测**（第五轮：4 条 skip 分支全部由桩触发，**下游调用序列两侧一致** ⇒ 两侧跳过的条数相同） |
| CASE-14 Transaction Rollback | — | 无事务 | 无事务 | — |
| CASE-15 序列化边界 | `hot_score=0xFFFFFFFF` | `4294967295` | `4294967295` | ✅ **活体实测**（第五轮，逐字节相同） |
| CASE-15b 序列化边界 | 空 `secondary_tags` | `null` | `null` | ✅ **活体实测**（第五轮） |
| CASE-15c 序列化边界 | 标题含 `<b>` `&` | 不转义 | 不转义 | ✅ **活体实测**（第五轮；含双引号 / 反斜杠 / 换行，逐字节相同） |
| CASE-15d 序列化边界 | 字段顺序 | 声明序 | 声明序 | ✅ **活体实测**（第五轮，逐字节相同 ⇒ D15 的修正在真栈里站得住） |
| CASE-16 int32 溢出分页 | `page=42949674&page_size=50` | 空页 | 空页 | ✅ **活体实测**（第五轮，逐字节相同） |
| CASE-17 参数上限 | 2048 / 2049 个 form 值 | OK / **5001** | OK / **5001** | ✅ **第十三轮已修**（`DRIFT-HOTAPI-022`）⇒ 配 `max-parameter-count: 2048` 后**首个翻转 n 两侧都是 2049**，响应体各 55 B **逐字节相同**。⚠️ 第二轮原写「RAG: OK / OK」**是错的**；第五轮原写「阈值 1000 ⇒ 1001 起就 5001」也已被订正 |
| CASE-18 body | 合法 JSON | **忽略** | **忽略** | ✅ **活体实测**（第五轮） |
| CASE-19 body | 畸形 JSON | **5001** | **5001** | ✅ **活体实测**（第五轮） |
| CASE-20 路由层 | 404 / 405 | 裸响应 | 裸响应（真栈实测同） | ✅ **活体实测**（第五轮：404 = `404 page not found\n` + `text/plain` + `nosniff`；405 = 裸状态码 + `Allow`、无 body） |

> 🟥 **2026-10-24 复核：本表是「第二轮」的快照，CASE-15d / CASE-17 / CASE-19 三行的 ❌ 已被第三轮推翻。**
> 现状态：D15 字段序 ✅ 已修（`@JsonPropertyOrder` + `HotApiGoWireOrderTest`）、
> D12 参数上限 ✅ **早已实现**（`GoFormValues.requireFormValueCountWithinLimit` + `HotApiGoParseParityTest`
> 的 2048/2049 边界用例 —— 本表原写「Java: OK / OK」**是错的**）、D13 畸形 body ✅ 已修。
> ⇒ 三行现均为 ✅；未闭合的只有 **D14 缺 nosniff**（已移交 common 域）与 **真 Tomcat 取证**。
>
> 🟥 **2026-10-24 第五轮再订正（本表已被端到端对拍实测覆盖，4 行结论被推翻）**：
> 1. **CASE-04b / CASE-04c 被推翻** ⇒ 真栈下 RAG 对 `?page[]=5` 是 **400 + Tomcat HTML 错误页**
>    （`DRIFT-HOTAPI-021`）⇒ 原 ✅ 是 **MockMvc 测的**（MockMvc 不走 Tomcat 的请求行校验）。
> 2. **CASE-17 被推翻** ⇒ 真栈下 RAG 的**值个数阈值是 1000**（Tomcat `maxParameterCount`），
>    不是 2048 ⇒ 1001~2048 区间 Go 成功 / RAG 5001（`DRIFT-HOTAPI-022`）。
>    ⇒ **D12「✅ 已实现」在实现层成立、但运行时不可达** ⇒ 须与 D22 合并看。
>    🟢 **2026-10-25 第十三轮**：D22 已修 ⇒ 阈值 **两侧都是 2049**，
>    「运行时不可达」这个副作用**已消除**（见 D21/D22/D23 三行）。
> 3. **CASE-15d / CASE-19 / CASE-20 升级为「活体实测同」** ⇒ 第五轮真栈逐字节确认。
> 4. **CASE-06 的超时分支已活体触发** ⇒ 两侧状态码与 body 逐字节相同，**只差 `nosniff`**（D14）。

## 9. Unresolved（§37）

> 按 §31-a：**没有实际读过两边源码得出的结论不得写成 SEMANTIC MISMATCH**。以下逐条说明缺什么证据。

| # | Question | Go Evidence | Java Evidence | Why Unknown | 需要什么活体证据 |
|---|---|---|---|---|---|
| **U1** | 端到端 HTTP 栈两侧是否逐字节一致？ | `hotcenter.go` 全链路已读 | 真 Boot 上下文已跑 | ⚠️ **已闭合（第五轮）**：两套栈**同时拉起**（Go hot-api + RAG hot-api + 下游同构双桩），29 条用例逐字节比**响应体 + 关键响应头** + 期望值 + **下游调用序列** ⇒ 默认 **24/29**，带修正配方 **28/29**。⇒ **查出 4 条新漂移**（D21~D24），并**推翻第二轮 4 条 ✅ 结论** | ✅ **已完成**（`tools/difftest/run-hot.sh` + `diff-hot.py`；详见「第五轮」一节） |
| **U2** | 3s 超时真的产出 `503 + Request Timeout`？ | `http.go:88` + `rest/config.go:55` | `HttpMiddleware:419-425` | ⚠️ **已闭合（第五轮）**：`CASE-21` 用「20 条热榜 + article-rpc 睡 30s」把请求**真的拖过 3s** ⇒ 两侧**都**产出 `HTTP 503 + "Request Timeout\n"`（**body 逐字节相同**）。⚠️ 唯一差异 = **缺 `X-Content-Type-Options: nosniff`** ⇒ 即 **D14**（代码在 `service/common` ⇒ **已移交 common 域**） | ✅ **已完成**（第五轮 `CASE-21`） |
| **U3** | 客户端断连是否真产出 499，且 gRPC 真的被取消？ | `http.go:84-86` `errors.Is(Canceled)`→499 | `HttpMiddleware:332` IOException→499 | ✅ **已闭合（第七轮，2026-10-24）**：**裸 socket + SO_LINGER=0 强制 RST**（urllib/httpclient 都做不到「发到一半就消失」）⇒ 两侧都**实测**了：Go 侧 8 个在飞 gRPC 在**断连后 1~3ms** 全部被取消、且**日志里有** `[HTTP] 499 ... duration 503.3ms`；RAG 侧则**要等到 2s 调用 deadline** 才结束、**并在断连后继续发第二批**下游调用、且**没有任何 499 记录** ⇒ **D3 坐实**，并连带查出 **DRIFT-HOTAPI-025** | ✅ **已完成**（`tools/difftest/probe-hot-disconnect.py`） |
| **U4** | client breaker 是否真在链上并会因超时打开？ | `zrpc/internal/client.go:112-113` + `breakerinterceptor.go:13-18` | 依赖「扫到 `com.rag.common`」+ `@GrpcGlobalClientInterceptor` | ✅ **已闭合（第十轮，2026-10-24）**：**装配层**（第四轮补 `GoStyleGrpcClientBreakerWiringTest` 3 例）+ **行为层**（第十轮新增 `GoStyleGrpcClientBreakerE2ETest`：**内嵌恒返 INTERNAL 的 gRPC server**，连续调用直到 breaker 真打开）⇒ 判据是「**下游调用次数停止增长**」（hot-api 响应体前后都是 5001，**看不出**熔断）⇒ 断言「熔断必须短路下游」。⚠️ 第一版断言「前 40 次都打到下游」实测红（只有 16 次）⇒ 根因：go-zero `googlebreaker.go:52-79` 的 `total` 来自 `rollingwindow.go` 的 `Reduce(ignoreCurrent)` ⇒ **只认已完成的 250ms 桶**。**变异验证**：`service/common` 的 `if (!breaker.accept())` → `&& Boolean.FALSE` ⇒ 带 `-am` 重跑**变红** ✅ ⇒ 守门非假绿 | 决定 DRIFT-HOTRPC-004 的严重度；也可能暴露 Go 侧独有的 shadow 残留 | ✅ **已完成**（`GoStyleGrpcClientBreakerE2ETest` + `GoStyleGrpcClientBreakerWiringTest`） |
| **U5** | 保护层四闸（MaxConns/MaxBytes/CpuShedding/Breaker）真触发？ | `rest/config.go:12-22,52-56` tag 默认 | `rag.protection.*`（静态同值） | ✅ **已闭合（第八轮，2026-10-24）· 三闸双侧活体 + 一闸平台不可达**：**MaxBytes** 两侧默认阈值下**逐字节一致**（超限 → 413 + 空 body + 无 Content-Type；「恰好等于阈值」也一致放行）；**MaxConns** 两侧同阈值 8 + 16 并发 → **各 8 个裸 503**（拒绝数恰等于阈值）；**Breaker** 两侧都**真的打开** → 裸 503 + 空 body（与超时的「503 + `Request Timeout\n`」可区分）。🔴 **CpuShedding 在 darwin 上 Go 侧结构性不可达**（`core/stat/internal/cpu_other.go` 的 `//go:build !linux` ⇒ `RefreshCpu()` 恒 0 ⇒ `stat.CpuUsage()` 恒 0 ⇒ `systemOverloaded()` 恒 false ⇒ `shouldDrop()` 永假；**活体佐证 = Go 自己的 stat 行 `CPU: 0m`**）⇒ 该闸只能「源码逐条对照 + go-zero 上游自带测试（`core/load`/`core/breaker` 全绿）+ RAG 侧 10 例同场景单测」取证。产出 **DRIFT-HOTAPI-026** / **DRIFT-HOTAPI-027** / **U5-OPEN-1**（**第十一轮已闭合** ⇒ **DRIFT-HOTAPI-029**） | ✅ **已完成**（`tools/difftest/probe-hot-protection.py`） |
| **U6** | 404 / 405 在**真 Tomcat** 下的响应头与 body 是否逐字节同？ | `http.Error` 四行（server.go:2337-2356） | MockMvc 实测：`404 page not found\n` + text/plain + nosniff；405 无 body | ✅ **已闭合（第四轮）**：**真 Tomcat**（`RANDOM_PORT` + JDK `HttpClient`）⇒ 404 = `404 page not found\n` + `text/plain` + **`nosniff`**；405 = 裸状态码 + `Allow`、**无 body** | ✅ 已完成（`HotApiRealTomcatGateTest` 的 `#notFoundIsBareGoStyleResponse` / `#methodNotAllowedIsBareStatusCode`） |
| **U7** | `TopK=100` 被 hot-rpc 如何解释？items 是否按热度降序？ | `logic:62-64` 硬编码 100；`hot.proto:10` 注释「默认 20，最大 100」 | `HotRpcClient:63` 硬编码 100 | ⚠️ **已闭合（2026-10-24 第十一轮，hot-rpc 侧活体对拍）**：本项原为「hot-api 不假设对端 rpc 正确」的悬空项，**本轮已由 hot-rpc 自己的双侧活体对拍闭合**（真 Kafka + 2×真 Redis + 真 gRPC，300 条事件）⇒ **10 组 top_k（0/1/5/20/100/101/200/1000/-1/-100）两侧响应逐字节一致**（sha256 相同）；**k≤0 ⇒ 回落 20**、**k>100 ⇒ clamp**、**items 两侧都按热度降序**（`descending=true`）；且每个文章的分数与「事件权重和」的**独立预测**逐条吻合 | ✅ **已完成**（`tools/difftest/run-hot-rpc-difftest.sh` + `hot_rpc_compare.java`；证据见 [`hot/rpc.md`](rpc.md) 第十一轮） |
| **U8** | percent-encoding 在真 Tomcat 下的绑定值 | `url.ParseQuery` 解 `%2B`→`+` ⇒ 5 | MockMvc **不解码**（`%2B5` 原样传入）；但 `.param("page","+5")` ⇒ 5 | ✅ **已闭合（第四轮）**：**真 Tomcat** 下 `%2B5` ⇒ 解码成 `+5` ⇒ `Integer.parseInt("+5")` 接受前导 `+` ⇒ **`page=5`**，与 Go 的 `strconv.ParseInt("+5")` **等价**（两侧都接受前导 `+`）⇒ **不需要改 `GoFormValues`**（见 Pitfall P4） | ✅ 已完成（`HotApiRealTomcatGateTest#percentEncodedPlusIsDecodedAndAccepted`） |
| **U9** | 59 条测试是否假绿？ | — | 本轮**间接证实过一处**：DRIFT-HOTAPI-015-B 的裸 mapper 让字段序测试永久无效 | ✅ **已闭合（第十轮，2026-10-24）**：对 hot-api 主干实现做 **10 个系统性语义变异**（分页上限/总数/起点/rank 稠密/跳过 nil/空页/十进制解析/gRPC deadline/信封包装/补全预算）⇒ **10/10 全部被守门抓到**，基线 **85 tests / 0 failures**。✅ **第十一轮扩展**：hot-rpc **10 个**（含 3 个原「没变红」的缺口 ⇒ 补 2 条守门后 **10/10**）+ hot-common **4 个（4/4）** ⇒ **hot 域合计 25 个变异点全部有守门**；hot-api 侧再加 **M11（补全预算的绝对 deadline）** ⇒ **11/11**。🔴 **过程中查出并修补了 1 个真实守门缺口**：`page_size=51`（恰好越过上限一格）此前**无任何测试钉住** ⇒ 新增 `HotApiMvcPipelineTest#pageSizeCapBoundaryIsApplied`，变异验证「恰好这一条变红」。⚠️ 第一轮 3 个「没变红」复核后确认**全是「变异本身是 no-op」**（含 `Integer.parseInt(String)` 本就是十进制）⇒ 不是守门缺口 | ✅ **已完成**（`tools/difftest/mutate-hot.py`） |
| **U10** | 极端并发下补全的延迟分布是否可比？ | Go：100 goroutine + 每请求新建 chan(8)，**无池上限** | Java：每请求 Semaphore(8) + **64 共享池** + 64 队列 + `CallerRunsPolicy` | ⚠️ **旧结论被实测推翻**：「池满时 Java 会在请求线程上串行跑补全」—— 实测下游在途峰值 **= 池容量，未超过 ⇒ `CallerRunsPolicy` 从未触发** ⇒ 背压形态是**纯排队**（与 Go 同类） | ✅ **已闭合（第十轮，2026-10-24）**：桩切 `many-100`（100 条 = Go 的 `TopK`；article-rpc 固定睡 20ms 后**正常返回**），**8 并发 × 20 轮 = 160 请求/侧**，用桩的 **ENTER/EXIT 事件日志**算下游在途峰值。**初测**：成功率两侧 **160/160 全 200**（结果完全一致）；**p99 1.76×**、p50 1.95×；**下游在途峰值 Go=64 vs RAG=32** ⇒ 根因 = `HotHydrationExecutorConfiguration.POOL_SIZE=32` ⇒ 产出 **DRIFT-HOTAPI-028**。**同日已修**（用户拍板方案 A：池 32→64 + 签字）：复测 **峰值 32→64（与 Go 一致）**、**p99 比值 1.76×→1.08×**、p50 **0.96×** ⇒ 残余差异（「RAG 有跨请求上限、Go 没有」）转 **SD-HOT-API-010**；新增守门用例 `HotArticleHydratorTest#concurrentRequestsEachKeepTheirOwnWindow`（变异回 32 ⇒ 变红 ✅） | ✅ **已完成**（`tools/difftest/probe-hot-latency.py`） |
| **U11** | Prometheus 实际导出形态是否同 Go | Go：`HistogramVec` 带 `_bucket{le=}`，边界 = `prometheus.DefBuckets`（**11 个** + `+Inf`） | Micrometer `publishPercentileHistogram` + `serviceLevelObjectives(GO_DEF_BUCKETS)` | ✅ **已闭合（第四轮）**：真 Tomcat + 真 `/actuator/prometheus` ⇒ 查出 **DRIFT-HOTAPI-018**（RAG 实测 **80** 个 `le` 边界 vs Go 的 **12**）⇒ **已修**（去掉 `publishPercentileHistogram()` ⇒ 实测回到 **12** 个、数值逐条一致）；同时**推翻**原注释「Counter 是 `hot_api_request_total_total`」⇒ 实测**两侧都是** `hot_api_request_total`（**单** `_total`），注释已订正 | ✅ 已完成（`HotApiRealTomcatGateTest` 的 3 条 U11 用例） |

## 10. 最终判定（§39 的 18 项 checklist）

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致？ | ⚠️ **否** → ✅ **已修** | 原判依据 D11（重复键：Go 成功 / Java 5001）｜🟥 2026-10-24：D11 已修（`GoFormValues.firstFormValue`） |
| 2 | 正常路径是否一致？ | ✅ 一致 | CASE-01 双侧实测 |
| 3 | 边界输入是否一致？ | ❌ 否 → ✅ **已修** | 原判依据 D11 / D12 / D13｜🟥 2026-10-24：三条**均已修**（D12 更是早已实现，是台账没回填） |
| 4 | NotFound / Empty / Null 是否一致？ | ✅ 一致 | 空页 `[]`、`secondary_tags:null`、`items` 非 null 全部双侧 wire 实测 |
| 5 | Error meaning 是否一致？ | ✅ 一致 | 解析失败与下游失败都归 5001 + `internal server error`（E1/E5/E6） |
| 6 | Error propagation 是否一致？ | ⚠️ 部分 → ✅ **已修** | 原判依据：D13 少一条 Go 的拒绝路径｜🟥 2026-10-24：D13 已修（`GoFormValues.requireParsableJsonBody`） |
| 7 | External dependency failure 是否一致？ | ✅ 一致 | 两个下游的 fatal / skip 方向均同（E5–E9） |
| 8 | Cache hit / miss 是否一致？ | — | 两侧均无缓存 |
| 9 | Transaction boundary 是否一致？ | — | 两侧均无事务 |
| 10 | State transition 是否一致？ | ✅ 一致 | 无状态机；请求内 `hydrated[]` 同构 |
| 11 | Side effects 是否一致？ | ⚠️ 部分 | 5 个指标名/标签一致；**暴露面**有意单向增强（D5）；`incr_view=false` 均不写库 |
| 12 | Ordering 是否一致？ | ✅ 一致 | 按下标保序；rank 跨页连续 |
| 13 | Retry 是否一致？ | ✅ 一致 | 两侧均无 |
| 14 | Timeout scope 是否一致？ | ✅ 一致（时长与作用域）／🟡 **响应头差 1 项** | 3s 整操作 + 2s 嵌套；**第五轮已活体触发**（U2）⇒ 两侧状态码与 body 逐字节相同，**只差 `nosniff`**（D14） |
| 15 | Concurrency semantics 是否一致？ | 🟡 **并发 ≤ 8 时等价** | **每请求窗口 8 一致** ✅；**跨请求合计在途** Go 无上限、RAG 被池压到 64 ⇒ 并发 ≤ 8 时**完全等价**（实测峰值 64 vs 64、p99 1.08×）；并发 > 8 时 RAG 多一层排队 ⇒ 延迟分布不同（结果仍同）⇒ 属**已签字偏离 SD-HOT-API-010**。⚠️ **旧结论已推翻**：`CallerRunsPolicy` 实测**从未触发** ⇒ 背压形态是**纯排队**，与 Go 同类 |
| 16 | Authentication / Authorization 是否一致？ | ✅ 一致 | 两侧零鉴权；本组件无 JWT / 黑名单中间件，该维度**不适用** |
| 17 | Serialization semantics 是否一致？ | ✅ **一致（body 层）**／🟡 **头差 1 项** | 第五轮真栈逐字节确认：**值/类型/null/uint32/HTML 转义/字段顺序**全一致；**响应头**差 `Content-Type` 的 charset 参数（D24）+ 超时头缺 `nosniff`（D14） |
| 18 | Lifecycle 是否一致？ | ✅ 一致 | 连接与线程池的边界相同（实现不同） |

### 最终结论

```text
SEMANTICALLY NOT EQUIVALENT
```

**理由**：存在 **1 条 SEMANTIC MISMATCH**（D11 重复 query 参数），
方向是「**Go 接受、RAG 拒绝**」—— 按 AGENTS.md §5「真源是 Go，出现行为差异默认判 RAG 错」，
这是必须修的缺陷，不是可签字的有意偏离。

另有 **4 条 OBSERVABLE DIFFERENCE**（D12 参数上限、D13 畸形 body、D14 缺 nosniff、D15 字段序），
其中 D14 / D15 需拍板取舍，D12 / D13 建议签字为「不复刻的 Go 既有怪癖」。

> 🟥 **2026-10-24 复核：上段是「第二轮」当时的结论，现已被第三轮 + 第四轮推翻，其中一条还记错了。**
> - **D12 参数上限**：结论**相反** —— Go 侧那条 2048 上限**在 RAG 侧早已实现**
>   （`GoFormValues.requireFormValueCountWithinLimit`，controller 调用，`HotApiGoParseParityTest` 覆盖 2048/2049 边界），
>   本轮**不需要**签字为「不复刻的怪癖」，也**不需要**修 —— 是台账没回填。已更正 §7 与 D12 行。
> - **D13 畸形 body**：✅ 已修（`GoFormValues.requireParsableJsonBody`）。
> - **D15 字段序**：✅ 已修（`@JsonPropertyOrder` + `HotApiGoWireOrderTest`，去掉注解即变红）。
> - **D14 缺 nosniff**：🔁 **跨域移交 common 域**（代码在 `service/common` 的 `HttpMiddleware.writeTimeoutResponse`）
>   ⇒ **hot 域内不可修**，需 common 域接手。
> ⇒ 本组件**域内**已无可修的语义漂移；未闭合项全部是「证据缺口」或「跨域移交」。
>
> 🟥 **2026-10-24 第五轮再订正（端到端对拍实测后）**：
> 上段「域内已无可修的语义漂移」**只对「域内代码」成立**，**对「域内配置」不成立** ——
> 第五轮查出 **4 条新的可观察差异**，其中 3 条的根因在 **Tomcat 容器默认值**（属域内 `application.yml`
> 可配，但**影响全部 10 个 api 模块** ⇒ 按 §5.3 跨域移交）：
>
> | 新增 | 差异 | 修法（**已实测**） | 归属 |
> |---|---|---|---|
> | `DRIFT-HOTAPI-021` | `?page[]=5` → Go 200 / RAG **400 + Tomcat HTML 错误页** | ✅ `server.tomcat.relaxed-query-chars: ["[", "]"]`（**第十三轮已落地 10/10**） | 跨域 |
> | `DRIFT-HOTAPI-022` | 值个数上限 Go **2048** / RAG **1000** | ✅ `server.tomcat.max-parameter-count: 2048`（**第十三轮已落地 10/10**） | 跨域 |
> | `DRIFT-HOTAPI-023` | 请求行长 Go **1 MiB** / RAG **8 KiB** | ✅ `server.max-http-request-header-size: **1028KB**`（**第十三轮已落地 10/10**；⚠️ **不是 1MB**，见下） | 跨域 |
> | `DRIFT-HOTAPI-024` | `Content-Type` 的 charset 参数 | ✅ **已修**（**第二十五轮 global T-6**：共享层加 charset ⇒ 10 个 api 全部生效）⇒ ⚠️ 「不可逐字节复刻」**仍成立**（Tomcat 归一化）⇒ 残余只差一个 OWS | ✅ 已修 |
>
> ⇒ 三条容器类差异**同时打开后**：对拍矩阵 **24/29 → 28/29**，输入上限阈值与 Go **完全一致**
> （实测首个翻转 n 两侧都是 **2049**）⇒ **配方已被证实**，只剩 D14（`nosniff`，common 域）。
>
> 🟥 **2026-10-25 第十三轮订正 + 落地**（本表原先写的三处「修法」有**一处不够准**）：
>
> | 项 | 原写 | 订正后 | 依据 |
> |---|---|---|---|
> | `-021` 的写法 | `"[,]"`（字符串按逗号切） | `["[", "]"]`（YAML 列表） | 两种都被 Boot 接受（`List<Character>` 的 binder 歧义），**列表无歧义** |
> | `-023` 的值 | `1MB` | **`1028KB`** | Go 真实上限 = `DefaultMaxHeaderBytes(1<<20)` **+ 4096**（`net/http/server.go:941 // bufio slop`）⇒ 配 1MB 会比 Go **严 4096 字节**（实测：Go 1052536 翻转 / 配 1MB 的 RAG 1048440 翻转） |
> | `-022` 的值 | `2048` | **保持 2048**（**不是** `-1`） | Tomcat 数「总参数个数」、Go 数「非空值个数」⇒ 语义不同；且另外 7 个 api 模块**没有**应用层判据 ⇒ 配 `-1` 会拆掉它们唯一的上限。详见 `DRIFT-HOTAPI-030` |
>
> ⇒ 落地范围 = **10 个 api 模块的 `application.yml`**；
> 守门 = `tools/difftest/check-tomcat-limits.py`（静态，10/10 ✅）
> ＋ `HotApiRealTomcatGateTest` 的 3 条**真 Tomcat 运行时**断言（MUT-A/B/C 变异**全红**）。

### ⚠️ 本结论的效力边界（三条，越界即失效）

1. **「NOT EQUIVALENT」只覆盖到本轮实测过的输入面。**
   未跑的输入（超时、断连、超限、breaker、端到端栈）一律在第 9 节，
   **不得**据此推断它们一致或不一致。
2. **「已双侧实测」≠「双侧服务同时跑起来对拍」。**
   本轮是**分层双侧探针**（Go 真库 + RAG 真上下文，同输入逐行比对），
   强度高于源码对照、低于端到端对拍（U1/D1 仍未做）。
   🟥 **2026-10-24 第十一轮订正：这一条已过期** —— **U1 与 D1 均已闭合**：
   U1 = 第五轮 29 条端到端用例（两套 HTTP 栈同时拉起）；D1 = 第十一轮 hot-rpc 双侧活体对拍
   （真 Kafka + 2×真 Redis + 真 gRPC）。⇒ 本条**当时成立、现已不适用**，
   当前 hot 域的证据强度以文件头部「当前证据强度」表为准。
3. **RAG 侧证据的 mapper 依赖已暴露一次假绿（DRIFT-HOTAPI-015-B）。**
   凡本文件标 ✅ 的 RAG 行，均注明了**用的是哪个 mapper / 哪套上下文**；
   未注明的仍按 AGENTS.md §3.3 记 `UNKNOWN`。
---

<!-- ====================================================================================
     ↓↓↓ 第三轮：2026-10-23 23:0x「对齐修复轮」（align/hot 分支）
     ↑ 上方是 2026-10-23 首轮实现 + 第二轮组件级验证的历史记录，一条未删
     ==================================================================================== -->

# 第三轮：修复轮（2026-10-23）

> **范围**：`service/hot/hot-api`（Go `service/hot/api/`）。
> **分支**：`align/hot`（worktree `../RAG.worktrees/hot`），**只改 hot 域文件**。
> **本轮性质**：把第二轮台账里 **1 条 SEMANTIC MISMATCH + 4 条 OBSERVABLE DIFFERENCE** 逐条处置。
> **只读边界**：**Go 真源仓库零改动**；探针在 `/tmp`，直接调用 **go-zero v1.10.2 的
> `rest/httpx.Parse`** + **逐字复制**的 `types.HotArticlesReq`。

## 本轮一句话

**第二轮的 `SEMANTICALLY NOT EQUIVALENT` 判定里，唯一那条 SEMANTIC MISMATCH（D11 重复 query 参数）
已修；4 条 OBSERVABLE DIFFERENCE 里 3 条已修（D12 / D13 / D15），1 条（D14 缺 nosniff）
归 **common 域**、本域无权改，已登记移交。**

## 逐条处置

> 🆕 **2026-10-29 对齐（台账对齐 phase · W-E lane）—— 本表的「上一轮判定」列是【历史值】，不是【当前状态】**
> 
> ⚠️ **同一个编号在本域出现三种 emoji，各有各的字段含义** ⇒ **不是**台账自相矛盾，**别当新回归去「修」**：
> | 位置 | 记的是什么 | 取值 |
> |---|---|---|
> | **上方 `###` 明细标题** | **严重度**（差异本身的量级 —— 「SEMANTIC MISMATCH · HIGH」/「BLOCKING」/「OBSERVABLE DIFFERENCE · MEDIUM」）⇒ **修复合灭它、差异本身仍在 ⇒ 严重度不变** | 🔴 / 🟠 |
> | **下方 `##` 收口标题** | **当前状态** | ✅ 已修 / ⏸ / 📝 已签字 |
> | **本表的第 2 列「上一轮判定」** | **历史值**（按 AGENTS.md D5「保留原值 + 加时间线指针」**原样保留**） | 🔴 / 🟠 / 🟡 |
> | **本表的第 3 列「本轮处置」** | **当前状态**（**权威**） | ✅ / ⏸ / 📝 |
> 
> ⇒ ⇒ **判定「某个编号现在是什么状态」时，一律以「本轮处置」列与下方 `##` 收口标题为准** ⇒ ⇒ 本表**不构成**「同一编号两种状态」。
> 
| ID | 上一轮判定 | 本轮处置 | 证据 |
|---|---|---|---|

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/probe-hot-disconnect.py` ⇒ **EXIT=0**（2026-10-30 03:43:31 · 12s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：⇒ 取消原因判定：**BY_DISCONNECT** ⏐ ⇒ 取消原因判定：**BY_DEADLINE** ⏐ ⇒ **两侧不等价，且方向与台账 D3 一致**： ⏐ · Go：客户端断连 ⇒ HTTP ctx 取消 ⇒ **在飞的 gRPC 立刻被取消** ⏐ · RAG：Servlet 断连**不**取消在飞的阻塞 gRPC ⇒ 只能等 2s 调用 deadline ⏐ ⇒ DRIFT-HOTAPI 的 **D3「客户端断连的取消不；<br>`tools/difftest/probe-hot-latency.py` ⇒ **EXIT=0**（2026-10-30 03:42:53 · 18s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> go  http://127.0.0.1:18903  并发 8 × 20 轮（mode=many-100） ⏐ ==> rag  http://127.0.0.1:58903  并发 8 × 20 轮（mode=many-100） ⏐ p99：Go 444.5ms  vs  RAG 416.8ms  ⇒ RAG/Go = 0.94× ⏐ p50：Go 353.0ms  vs  RAG 359.6ms  ⇒ RAG/Go = 1；<br>`tools/difftest/probe-hot-protection.py` ⇒ **EXIT=1**（2026-10-30 03:43:11 · 20s）—— 🔴 **探针判定为「不一致」**；覆盖断言：[FAIL] maxconns       rag  12 并发在途 ⇒ 裸 503（空 body）0 个 / 放行 12 个 / 传输错误 0 个，耗时 3.0s（期望：至少 1 个裸 503） ⏐ [FAIL] breaker        go   3 波 × 12 并发 ⇒ 熔断裸 503（空 body）共 0 个；超时 503（body=Request Timeout）共 0 个（期望：> 0 个空 body 的 503 ——；<br>`tools/difftest/run-hot-rpc-difftest.sh` ⇒ **EXIT=1**（2026-10-30 03:43:58 · 2s）—— 🔴 **探针判定为「不一致」**；覆盖断言：==> [1/7] 清场：停掉旧的 hot-rpc 进程 ⏐ ==> [2/7] 清空两个 Redis（**必须等旧进程死后**，否则它会把旧 sketch 写回来 ⇒ 分数翻倍） ⏐ ❌ hotrpcdifftest-redis-go:6379 DBSIZE=（应为 0）⇒ 有旧数据残留，停下；<br>`tools/difftest/run-hot.sh` ⇒ **EXIT=1**（2026-10-30 03:39:35 · 30s）—— 🔴 **探针判定为「不一致」**；覆盖断言：✓ CASE-18    畸形 JSON body ⇒ 5001 ⏐ ✓ CASE-19    不存在的路径 ⇒ 404 裸响应（text/plain + nosniff） ⏐ ✓ CASE-20    方法不匹配 ⇒ 405 裸状态码（无 body） ⏐ ✗ CASE-21    下游拖过 3s ⇒ 503 Request Timeout（校验 nosniff） ⏐ ↳ 已知漂移：503 响应缺 X-Content-Type-Opti。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。
| **DRIFT-HOTAPI-011** | 🔴 SEMANTIC MISMATCH | ✅ **已修** | Go 活体探针 + 真实 MVC 管线 4 例<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:200-216`（`firstFormValue` 取首个非空值）· Go `rest/httpx/util.go:35` + `core/mapping/unmarshaler.go:835-846`（`val.Index(0)` 取首值）· 核对日期 2026-10-30。 |
| **DRIFT-HOTAPI-015-B** | ⚠️ 证据缺口（测试永久无效） | ✅ **已补** | 新增 `HotApiGoWireOrderTest`（真实上下文 + 原始字节）｜⚠️ 2026-10-24 改号：原编为 012，与「2048 上限」撞车 |
| **DRIFT-HOTAPI-013** | 🟠 OBSERVABLE DIFFERENCE | ✅ **已修** | Go 活体探针 + 真实 MVC 管线 2 例<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:267-312`（`requireParsableJsonBody` ⇒ 畸形 / 非对象 body ⇒ 5001）· Go `rest/httpx/requests.go:66-68` + `rest/httpx/requests.go:153-155`（`ParseJsonBody` 无条件执行）· 核对日期 2026-10-30。 |
| **DRIFT-HOTAPI-014** | 🟡 OBSERVABLE DIFFERENCE | 🔁 **跨域移交 common 域**（原判，保留）<br>✅ **已闭合**（2026-10-30 现行态） | 本域无权改（代码在 `service/common`）<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **🔁 跨域移交 common 域** ⇒ **现行态 = ✅ 已闭合（nosniff 已实现）**（**改判**）。依据：RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:728`（`writeTimeoutResponse` **已写** `X-Content-Type-Options: nosniff`，覆盖 503 + 499）· Go `service/common/observability/http.go:88`（`http.Error` ⇒ `net/http` 必设 nosniff）· 核对日期 2026-10-30。 |
| **DRIFT-HOTAPI-015** | 🟡 OBSERVABLE DIFFERENCE | ✅ **已修** | `@JsonPropertyOrder` + 真实运行时断言 + 变异变红<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/dto/HotArticleCard.java:84` + `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/dto/HotArticlesResp.java:48`（`@JsonPropertyOrder` 钉死 Go 声明序）+ `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiGoWireOrderTest.java:81`（真实上下文 + 原始字节守门）· Go `service/hot/api/internal/types/types.go:6-20`（item 13 字段声明序）+ `service/hot/api/internal/types/types.go:27-33`（resp 5 字段声明序）· 核对日期 2026-10-30。 |

---

## DRIFT-HOTAPI-011 ✅ 已修 · SEMANTIC MISMATCH · MEDIUM
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:200-216`（`firstFormValue`）· Go `rest/httpx/util.go:35` + `core/mapping/unmarshaler.go:835-846`（`val.Index(0)`）· 守门在仓：`service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiGoParseParityTest.java` · 核对日期 2026-10-30。

- **Go 行为（权威取证，非推测）**：go-zero v1.10.2
  - `rest/httpx/util.go:60` — 同名多值收进一个 `[]string`；
  - `rest/httpx/requests.go:27-31` — `formUnmarshaler` 带 `mapping.WithFromArray()`；
  - `core/mapping/unmarshaler.go:835-846` — 非 slice 字段且值是数组 ⇒ **`val.Index(0)`**（取首值）。
- **Go 活体探针实测**（`httpx.Parse` + 逐字复制的 `types.HotArticlesReq`）：

  | 输入 | Go | RAG 修前 | RAG 修后 |
  |---|---|---|---|
  | `?page=5&page=3` | **page=5** | 🔴 5001（`"5,3"` 拼串后 parseInt 失败） | ✅ page=5 |
  | `?page[]=5&page[]=9` | **page=5** | 🔴 5001 | ✅ page=5 |
  | `?page=5&page=` | **page=5** | ✅ page=5 | ✅ page=5 |
  | `?page=1,2` | **5001**（`strconv.ParseInt("1,2")` 失败，不做逗号拆分） | ✅ 5001 | ✅ 5001 |

- **RAG 修法**：`HotArticleController` **不再用 `@RequestParam`**，改为
  `GoFormValues.firstFormValue(request, name)` → `request.getParameterValues(name)` 取**第 0 个**非空值，
  并额外支持 Go 的 **`[]` 数组记法后缀**（`httpx/util.go:57-60` 剥掉后缀后归到同名 key）。<br>⚠️ **2026-10-30 指针订正**：原写 `httpx/util.go:59-61` ⇒ 实测（go-zero `v1.10.2`，真源 `go.mod:17` pin）`:59` = `}`（if 的收尾）、`:60` = `params[name] = filtered`、`:61` = `}` ⇒ **漏掉了真正剥后缀的那两行**（`:57` `if strings.HasSuffix(name, arraySuffix) {` ＋ `:58` `name = name[:len(name)-2]`）⇒ ⇒ **现行指针 = `:57-60`**（覆盖「判 HasSuffix → 剥后缀 → 归到同名 key」整段）⇒ **语义不变**（行号合法但指向错行）。
- **守门测试**：`HotApiGoParseParityTest` 4 例（真实 Boot 上下文 + MockMvc）。

### ⚠️ 本轮发现的一条 **Go 侧非确定性**（新增观察，非 RAG 缺陷）

`?page=1&page[]=2` 这种**同名 + 带后缀混用**时，Go 的 `GetFormValues` 会把两个 key
都写进同一个 `params["page"]`（后者**覆盖**前者），而 `r.Form` 是 map ⇒ **遍历序随机**
⇒ Go 的结果在 `page=1` / `page=2` 之间**随机**（实测 8 次：6 次 2、2 次 1）。
⇒ RAG 取**确定性**的「精确名优先」⇒ 落在 Go 的行为包络内，但**比 Go 更确定**。
⇒ 登记为 [`docs/Intentional_Deviations/hot/api.md`](../../Intentional_Deviations/hot/api.md) `SD-HOT-API-001`。

---

## DRIFT-HOTAPI-015-B ✅ 已补 · 证据缺口（裸 mapper 让字段序测试永久无效）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已补** ⇒ **现行态 = ✅ 已补**（未改判；本条**原无指针**，本轮补上）。
> 依据：RAG `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiGoWireOrderTest.java:81`（`fieldOrderMatchesGoDeclarationOrder` ⇒ **真实 Boot 上下文 + 原始响应体字节** ⇒ 逐个断言 13 个 item 字段与 5 个 resp 字段的相对顺序）+ `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/dto/HotApiWireContractTest.java`（**保留但只**验「Jackson 按注解怎么写」，**不**声称能证明运行时字节形状）· Go `service/hot/api/internal/types/types.go:6-20`（item 字段声明序）+ `service/hot/api/internal/types/types.go:27-33`（resp 字段声明序）· 核对日期 2026-10-30。
> ⇒ ⇒ **「证据缺口」确实已补上**（两个测试类都在仓、职责边界写清楚）⇒ 本条维持 ✅。

> ⚠️ **本条曾被错编成 `DRIFT-HOTAPI-012`**（标题写「012 · 证据缺口（测试永久无效）」）——
> 但主题讲的是**字段序测试永久无效**，那属于 **DRIFT-HOTAPI-015** 的证据缺口；
> 而 **012 的真正主题是「form 值个数上限 2048」**，它早已实现（见 §7 的 DRIFT-HOTAPI-012 条目）。
> ⇒ 同一编号被用于两件不同的事，**已于 2026-10-24 改号为 015-B**，并在此显式说明，避免下一个人读错。

- **原问题**：`HotApiWireContractTest` 用**裸 `ObjectMapper`** 断言字段序，
  而运行时用的是 Spring Boot 装配的 mapper ⇒ 该测试**永远绿**，运行时是字母序它也测不出来。
- **本轮处置（两件事，缺一不可）**：
  1. **保留** `HotApiWireContractTest`，但把它的**职责边界写清楚**：
     它只验「Jackson 按注解怎么写」（字段名 / `null` vs `[]` / uint32 无符号），
     **不**声称能证明运行时的字节形状；
  2. **新增** `HotApiGoWireOrderTest`：**真实 Boot 上下文** + **原始响应体字节**，
     逐个断言 13 个 item 字段与 5 个 resp 字段的**相对顺序**等于 Go 的声明序。
- **⚠️ 这是本轮最值得记住的一条方法论**：
  **「断言用了哪个 mapper」≠「断言了运行时」**。
  裸 mapper 与运行时 mapper 是**两个对象**，前者绿不代表后者对。
  ⇒ 凡涉及「序列化后的字节形状」，必须走**真实上下文 + 原始字节**。

---

## DRIFT-HOTAPI-013 ✅ 已修 · OBSERVABLE DIFFERENCE · LOW-MEDIUM
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:267-312`（`requireParsableJsonBody`）· Go `rest/httpx/requests.go:66-68` + `rest/httpx/requests.go:119-120`（`io.LimitReader(r.Body, maxBodyLen)` ⇒ 只读前 8 MiB）· 核对日期 2026-10-30。

- **Go 行为**：`httpx/requests.go:66-68` 的 `ParseJsonBody` 在 `Parse` 里**无条件**执行；
  `httpx/requests.go:153-155` 的 `withJsonBody(r)` = `r.ContentLength > 0 && strings.Contains(Content-Type, "application/json")`；
  `core/mapping/jsonunmarshaler.go:45-52` 先 `jsonx.UnmarshalFromReader`（**JSON 语法校验**）再按 json tag 填字段。
- **Go 活体探针实测**（Content-Type: application/json）：

  | body | Go | RAG 修前 | RAG 修后 |
  |---|---|---|---|
  | `{`（畸形） | 🔴 5001（unexpected EOF） | 🟢 200 | ✅ 5001 |
  | `[1,2]` | 🔴 5001（type mismatch） | 🟢 200 | ✅ 5001 |
  | `null` / `123` / `"x"` | 🔴 5001（unsupported type） | 🟢 200 | ✅ 5001 |
  | `{"page":9,"page_size":3}` | 🟢 200，**page 仍取 query 的 5** | 🟢 同 | ✅ 同 |
  | 空 body / `text/plain` / 无 Content-Type | 🟢 200（Go 根本不读 body） | 🟢 同 | ✅ 同 |

- **RAG 修法**：`GoFormValues.requireParsableJsonBody(request)`
  —— 只在 `contentLength > 0` 且 Content-Type 含 `application/json` 时读 body；
  语法错误 / 顶层非对象 ⇒ `BusinessException(5001)`（⇒ **200 + `{code:5001}`**，与 Go 的 `SetErrorHandler` 同形）。
- **⚠️ 一条刻意<b>不</b>做的**：**不开** `FAIL_ON_TRAILING_TOKENS`。
  Go 用的是 `json.Decoder.Decode`（`core/jsonx/json.go:74-77`），它**只读一个值**、**不**拒绝尾随内容
  ⇒ `{"a":1} garbage` 在 Go 是**通过**的；开了那个特性反而会造出一条 Go 没有的拒绝路径。

---

## DRIFT-HOTAPI-014 ✅ 已闭合（nosniff 已实现）· OBSERVABLE DIFFERENCE · LOW
> 🔴 **2026-10-30 真实性复核（favorite+hot lane）**：原判 **🔁 跨域移交（common 域）· ⏳ 待 common 域处理** ⇒ **现行态 = ✅ 已闭合（nosniff 已实现）**（**改判**）。
> 依据：RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:728`（`writeTimeoutResponse` **已写** `resp.setHeader("X-Content-Type-Options", "nosniff")`；`:716-740` 同一方法同时服务 503 与 499 两条路径）· Go `service/common/observability/http.go:88`（`http.Error(w, httpTimeoutBody, statusCode)`）· 核对日期 2026-10-30。
> ⇒ ⇒ 上面正文里「**RAG**：`com.rag.common` 的 `HttpMiddleware.writeTimeoutResponse` **没有** nosniff」与「**状态**：⏳ **待 common 域处理**」**两句已过期**（**原文保留，不改写**）⇒ 与本文件 `:141` 的 D14 行、`:180` 的「2026-10-26 第二十八轮订正」表口径一致 ⇒ **不需要 common 域再处理**。

- **Go**：`observability/http.go:88` 的 `http.Error(w, httpTimeoutBody, statusCode)`
  ⇒ `net/http` 的 `http.Error` 会设 `Content-Type: text/plain; charset=utf-8` **和**
  `X-Content-Type-Options: nosniff` ⇒ **503（超时）与 499（断连）两条路径都带 nosniff**。
- **RAG**：`com.rag.common` 的 `HttpMiddleware.writeTimeoutResponse` **没有** nosniff。
- **⚠️ 归属**：该代码在 **`service/common`**（`com.rag.common.observability.HttpMiddleware`），
  **不属于 hot 域** ⇒ 按 AGENTS.md §5.3「只写自己域」，本轮**不改**，
  已在本文件登记并移交 ⇒ 需由 **common 域**在其 worktree 里处置。
- **对照事实（说明这不是 hot-api 单点的选择）**：同一个组件里 **404 路径**
  （`com.rag.common.http.GoStyleRouterErrorResponses`）**是**设了 nosniff 的
  ⇒ 「同一组件内两条路径形状不一致」这件事**发生在 common 域内部**，hot 域只是消费者。
- **影响面**：客户端拿到的超时/断连响应少一个硬化头；**业务判定（code/msg/status）不受影响**。
- **状态**：⏳ **待 common 域处理**。本域无后续动作。

---

## DRIFT-HOTAPI-015 ✅ 已修 · OBSERVABLE DIFFERENCE · MEDIUM
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/dto/HotArticleCard.java:84` + `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/dto/HotArticlesResp.java:48`（`@JsonPropertyOrder`）+ `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiGoWireOrderTest.java:81`（守门，去掉注解即变红）· Go `service/hot/api/internal/types/types.go:6-20` + `service/hot/api/internal/types/types.go:27-33`（声明序）· 核对日期 2026-10-30。

- **Go 行为**：`encoding/json` 按**结构体声明序**输出
  （`service/common/response/response.go` = `code, msg, data`；
  `api/internal/types/types.go:27-33` = `items, total, page, page_size, scope`；
  `api/internal/types/types.go:6-20` = 13 个 item 字段的声明序）。
- **RAG 原行为**：运行时 mapper 输出**字母序**
  （实测 body：`article_id, author_id, brief, comment_count, cover_image_url, create_time,
  hot_score, like_count, manual_type_tag, rank, secondary_tags, title, view_count`）⇒ **与 Go 字节不同**。
- **⚠️ 根因（第三轮补齐，比上一轮的说法更准确）**：
  **不是** Boot 显式开启了 `SORT_PROPERTIES_ALPHABETICALLY`（全 jar 扫描该字符串**零命中**），
  而是 **Jackson 3 把这个特性的默认值翻成了 true**：
  - Jackson 3 `tools.jackson.databind.MapperFeature`：`SORT_PROPERTIES_ALPHABETICALLY` 构造实参 = `iconst_1` ⇒ **默认 true**；
  - Jackson 2 `com.fasterxml.jackson.databind.MapperFeature`：同名字段构造实参 = `iconst_0` ⇒ **默认 false**。
  ⇒ Boot 4 的 web 栈用 **Jackson 3**（`tools.jackson.core:jackson-databind:3.1.5`）
  ⇒ 运行时字母序；**裸 `new ObjectMapper()` 与 `Jackson2ObjectMapperBuilder.json().build()` 都是 Jackson 2**
  ⇒ 声明序 ⇒ **这才是「为什么裸 mapper 测不出来」的真正原因**（上一轮归因到 Boot 装配层，不准确）。
- **RAG 修法**：`HotArticleCard` / `HotArticlesResp` 加 `@JsonPropertyOrder({...})`
  ⇒ 注解**优先于** `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` 这类全局特性
  ⇒ 无论 mapper 怎么配，输出都等于 Go 的声明序。
  （全仓同一手法已在用：`like-rpc` 的 `LikeEventContracts`、`comment-rpc` 的 `CommentCacheService`。）
- **守门测试 + 变异验证**：`HotApiGoWireOrderTest`（真实上下文 + 原始字节）；
  **去掉 `@JsonPropertyOrder` ⇒ 该测试变红**（实测失败信息里直接打印出字母序 body）⇒ **守门有效**。

---

<!-- ====================================================================================
     ↓↓↓ 第四轮：2026-10-24「日志面 / CodeError 对齐轮」（align/hot 分支）
     ↑ 上方首轮 + 第二轮 + 第三轮的历史记录一条未删
     ==================================================================================== -->

# 第四轮：日志面 / `CodeError` 对齐轮（hot 域第六轮，2026-10-24）

> **范围**：`service/hot/hot-api`（+ `docs/DRIFT/hot/common.md` 的边界回填）。
> **分支**：`align/hot`（worktree `../RAG.worktrees/hot`），**只改 hot 域文件**。
> **本轮性质**：把 `docs/DRIFT/hot/common.md` 边界 #2（`CodeError.Error()` 未复刻）从「定性为非漂移」
> **推翻并修掉** —— 复核时发现它**不是**「少段前缀」那么简单，而是**两处叠加**的漂移。
> **只读边界**：**Go 真源仓库零改动**（探针在 `/tmp`，复用真源的 `go.mod`/`go.sum` 离线构建）。

## 本轮一句话

**边界 #2 的原定性（「仅日志少段冗余前缀，属表达层，非 L1」）是错的**：
实测发现「hot-rpc 返回空响应」这条日志在 Go 侧是 **error 级 + 9 个字段**（含 `error_code` / `error_reason`），
RAG 侧却是 **info 级 + 无这些字段** ⇒ **级别、字段集、字段值三处都不一样**。
⇒ 已修（`DRIFT-HOTAPI-016`）；同时**新查出**一条**库层**差异（`DRIFT-HOTAPI-017`），**不在 hot 域可复刻范围**。

---

## DRIFT-HOTAPI-016 ✅ 已修 · OBSERVABLE DIFFERENCE · MEDIUM（日志面）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/exception/HotCodeError.java:88`（`getMessage()` 覆写为 Go `(*CodeError).Error()` 整串）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/exception/HotCodeError.java:77`（`getBizMsg()` 纯文案，给信封）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/advice/GlobalExceptionHandler.java:99`（`bizMsgOf` 分流）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleQueryService.java:191`（nil 响应分支 `logBusinessErr`，不再 `logInfo`）· Go `service/hot/api/internal/logic/hot/getHotArticlesLogic.go:65-74`（两条 fatal 分支都记 `LogBusinessErr`）+ `service/hot/common/errmsg.go:15-17`（`Error()` = `"ErrCode:%d, Errmsg:%s"`）· 核对日期 2026-10-30。

- **Component**：hot-api 的**业务错误日志**（`error_reason` / 日志级别 / 字段集）
- **Go 行为**（`service/hot/api/internal/logic/hot/getHotArticlesLogic.go:65-74`）：
  ```go
  if rpcErr != nil {
      metrics.ObserveRPCError(hotRPCCallee)
      logger.LogBusinessErr(l.ctx, hotcommon.ErrorServerCommon, rpcErr)   // :67
      return nil, hotcommon.NewErrCode(hotcommon.ErrorServerCommon)        // :68
  }
  if hotResp == nil {
      metrics.ObserveArticleSkip(skipReasonNilHotPayload)
      nilRespErr := hotcommon.NewErrCode(hotcommon.ErrorServerCommon)       // :72
      logger.LogBusinessErr(l.ctx, hotcommon.ErrorServerCommon, nilRespErr) // :73
      return nil, nilRespErr                                                // :74
  }
  ```
  ⇒ **两条 fatal 分支都记 `LogBusinessErr`（error 级 + 9 字段）**；
  其中 `:73` 记的是 **`*CodeError`** ⇒ 其 `error_reason` = `(*CodeError).Error()` 的整串
  （`service/hot/common/errmsg.go:15-17` ⇒ `"ErrCode:5001, Errmsg:internal server error"`）。
- **RAG 原行为**（修前）：`HotArticleQueryService.fatal(...)`
  - `cause != null` ⇒ `logger.logBusinessErr(ctx, code, cause)` ✅（对齐 `:67`）；
  - `cause == null` ⇒ **`logger.logInfo(ctx, "hot rpc returned a nil response for route ...")`**
    ⇒ ❌ **info 级、无 `error_code` / `error_reason` / `file_line` / `call_path` / `call_chain` 字段**。
  ⇒ 三处不一致：**① 级别**（ERROR vs INFO）、**② 字段集**（9 个 vs 0 个）、**③ 字段值**（`error_reason` 缺 `ErrCode:5001, Errmsg:` 前缀）。
- **修法**（三处，缺一不可）：
  1. **新增 `HotCodeError extends BusinessException`**（`com.rag.hot.hot.api.exception`）：
     `getMessage()` **覆写**为 Go 的 `(*CodeError).Error()` 整串；另开 `getBizMsg()` 暴露**纯文案**。
     ⚠️ **为什么放在 hot-api 而不是 hot-common**：`hot-common` 的 pom **不依赖** `service/common`
     （Go 侧 `errmsg.go` 也是零依赖的纯值库）⇒ 在那里 `extends BusinessException` 会引入一条 Go 侧没有的依赖；
     且 Go 侧 hot-**rpc** 全域不用 `CodeError`（只有 api 侧用）⇒ 不需要跨组件共享，落在用它的组件里不制造新耦合。
  2. **`GlobalExceptionHandler.bizMsgOf(e)`**：信封取 `HotCodeError.getBizMsg()`（**纯文案**），
     其余 `BusinessException` 仍取 `getMessage()`。
     ⚠️ **必须这样分**：Go 的信封是**逐字段复制**（`hotcenter.go:53-66` 取 `codeErr.Code` / `codeErr.Msg`），
     **不是** `Error()` 整串 ⇒ 若信封也用 `getMessage()`，响应体会多出 `ErrCode:5001, Errmsg:` 前缀 ⇒ **制造新漂移**。
  3. **`HotArticleQueryService.fatal(null)` 分支**：`logInfo` → `logBusinessErr(ctx, code, err)`
     （`err` 就是 `HotCodeError`）⇒ 对齐 Go `:72-73`。
- **守门测试**：`HotCodeErrorLogParityTest`（3 例）
  ① `getMessage()` 逐字节 = `"ErrCode:5001, Errmsg:internal server error"`；
  ② **真触发日志链路**（logback `ListAppender` 挂在真实 logger 上，走完整
     `HotArticleQueryService → LoggerFactoryBean.logBusinessErr → Logger.doLogBusinessErr → slf4j`）
     ⇒ 断言**级别 = ERROR**、**`error_reason` 逐字节对齐**、**`error_code` = 5001**；
  ③ 信封的 `msg` 仍是**纯文案**且**不含** `ErrCode:` 前缀。
  ⚠️ ② 是「真触发」而不是读内部字段 —— 只断言 `getMessage()` 的返回值**证明不了**日志链路真的用了它。
- **变异验证（3 条，全部按预期变红）**：

  | # | 变异 | 预期红 | 实测 |
  |---|---|---|---|
  | **M-A** | `HotCodeError.getMessage()` 改回返回 `bizMsg` | ① + ② | ✅ 2 条红：`expected: <ErrCode:5001, Errmsg:internal server error> but was: <internal server error>` |
  | **M-B** | `bizMsgOf` 改回 `e.getMessage()` | ③ | ✅ 1 条红：`expected: <internal server error> but was: <ErrCode:5001, Errmsg:internal server error>` |
  | **M-C** | nil 响应分支改回 `logInfo` | ② | ✅ 1 条红：`expected: <ERROR> but was: <INFO>` |

  ⇒ 三条分别锁住「日志值」「信封值」「日志级别」三个面 ⇒ **守门非假绿**。
- **顺带订正**：`HotArticleQueryServiceTest#hotRpcFailureIsFatal` 原断言
  `assertEquals("internal server error", e.getMessage())` —— 锁的正是**旧的不一致行为**
  （RAG 侧 `getMessage()` 是纯文案，Go 侧是 `Error()` 整串）⇒ 按 §3.3「先改错误断言」改为断言对齐后的形态。
- **Evidence**：【Go 活体源码逐行 + RAG 运行证据（日志事件实际字段）】
- ⚠️ **同族但**未**修的**部分见下一条 `DRIFT-HOTAPI-017` ⇒ **本条修完不等于「日志面全等」**。

---

## DRIFT-HOTAPI-017 ✅ 已签字（不复刻）· OBSERVABLE DIFFERENCE · LOW（**库层**）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已签字（不复刻）** ⇒ **现行态 = ✅ 已签字（不复刻）**（未改判）。
> 依据：RAG `service/common/src/main/java/com/rag/common/logger/Logger.java:243`（`error_reason` 取 `err.getMessage()` ⇒ grpc-java 形态）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleQueryService.java:183`（hot 侧记下游 gRPC 失败）· Go `service/common/logger/logger.go:118-123`（`error_reason = err.Error()` ⇒ grpc-go 形态 `"rpc error: code = Unavailable desc = …"`）+ `service/common/logger/logger.go:152`（结构化 `error_code` 字段 ⇒ 语义信息不丢）· 核对日期 2026-10-30。
> ⇒ ⇒ **签字口径成立**（形态由两个 gRPC 库各自决定 ⇒ 不在 hot 域修）⇒ 与 `docs/Intentional_Deviations/hot/api.md` 的 `SD-HOT-API-005`（✅ 仍成立）一致。

- **Component**：hot-api 日志的 `error_reason`，在**下游 gRPC 调用失败**时的形态
- **Go 行为**：Go 侧 `:67` 记的是 `rpcErr`（**gRPC 库产生的 error 对象**）⇒
  `error_reason` = `err.Error()` ⇒ **grpc-go 的形态**。
- **RAG 行为**：RAG 侧记的是 grpc-java 的 `StatusRuntimeException` ⇒ `error_reason` = `err.getMessage()`
  ⇒ **grpc-java 的形态**。
- **两侧实测（真源 go.mod/go.sum 离线构建 + grpc-java 直跑）**：

  | | `error_reason` 的实际值 | gRPC code 名 |
  |---|---|---|
  | **Go（grpc-go）** | `"rpc error: code = Unavailable desc = boom"` | `Unavailable`（**CamelCase**） |
  | **RAG（grpc-java）** | `"UNAVAILABLE: boom"` | `UNAVAILABLE`（**全大写**） |

  ⇒ **连 code 名的大小写都不同** ⇒ 逐字节对齐需要一张 **code 名映射表**。
- **为什么不修**（✅ 已按有意偏离签字，2026-10-24 第六轮）：
  1. 形态由**两个 gRPC 库各自决定**，不是 hot 域的代码 ⇒ 要复刻就得在 hot-api **手拼** `"rpc error: code = "` 前缀
     并维护一张 code 名大小写映射表 ⇒ **在错误的层**（hot 域凭什么知道 gRPC 错误串的格式？）；
  2. 与 `DRIFT-HOTRPC-004`（Redis 重试）**同一个判断**：把库行为搬进业务层，比差异本身更坏；
  3. **可观察影响极小**：`error_reason` 是**日志自由文本**，码值在两侧都另有结构化 `error_code` 字段承载
     （Go `logger.go:152` / RAG `Logger.java:277`）⇒ 语义信息不丢。
- **⚠️ 与 `DRIFT-HOTAPI-016` 的分界**：`016` 修的是**hot 域自己写的**格式（`errmsg.go:15-17`）⇒ 该修；
  `017` 是**库写的**格式 ⇒ 不该由 hot 域修。
- **Evidence**：【双侧实测：Go 探针 `/tmp/grpc-err-probe/` + grpc-java 直跑】
- **状态**：✅ **已签字**（2026-10-24 第六轮，用户拍板「不复刻」）⇒ `SD-HOT-API-005`。

---

## DRIFT-HOTAPI-018 ✅ 已修 · OBSERVABLE DIFFERENCE · MEDIUM（指标分桶边界）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/metrics/HotApiMetrics.java:163`（**只** `.serviceLevelObjectives(GO_DEF_BUCKETS)`；**已无** `publishPercentileHistogram()`）+ `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiRealTomcatGateTest.java:166`（`prometheusBucketBoundariesMatchGoDefBuckets`，真 Tomcat + 真 `/actuator/prometheus`）· Go `service/hot/api/internal/metrics/metrics.go:25-30`（`prometheus.DefBuckets`）+ `prometheus/client_golang@v1.21.1/prometheus/histogram.go:271`（`DefBuckets = {.005,.01,.025,.05,.1,.25,.5,1,2.5,5,10}`）· 核对日期 2026-10-30。

- **Component**：`hot_api_request_duration_seconds` 的 Prometheus `_bucket{le=}` 边界序列
- **Go 行为**：`metrics.go:24-29` —— `prometheus.HistogramVec{Buckets: prometheus.DefBuckets}`
  ⇒ 导出**恰好 11 个** `le` 边界 + `+Inf` = **12 个**。
  ⚠️ `DefBuckets` 取自**源码**：`client_golang@v1.21.1/prometheus/histogram.go:271`
  = `{.005, .01, .025, .05, .1, .25, .5, 1, 2.5, 5, 10}`。
- **RAG 原行为**：`HotApiMetrics.observeRequest` 同时开了
  `.publishPercentileHistogram()` + `.serviceLevelObjectives(GO_DEF_BUCKETS)`
  ⇒ **真 Actuator 实测导出 80 个 `le` 边界**：
  Micrometer 的 `publishPercentileHistogram()` **强制**改用它自己的 exponential percentile 桶
  （69 个：`0.001, 0.001048576, 0.001398101, …`），把 SLO 边界只是**合并**进去。
- **影响**：**两侧的 `histogram_quantile` 不可比** ——
  Go 侧按 `DefBuckets` 插值、RAG 侧按另一套边界插值 ⇒ 同一个 p95 在两侧不是一个数。
  ⚠️ 原注释声称「开了 SLO 就能可比」⇒ **实现与意图不符**（这是该修的缺陷，不是取舍）。
- **修法**：**去掉** `.publishPercentileHistogram()`，**只留** `.serviceLevelObjectives(GO_DEF_BUCKETS)`。
- **实测（探针逐变体对比，Micrometer 1.16.7 + `io.micrometer.prometheusmetrics`）**：

  | 变体 | `le` 边界数 | 与 Go 一致？ |
  |---|---|---|
  | `publishPercentileHistogram()` + SLO（**修前**） | **80** | ❌ |
  | **只 SLO（修后）** | **12**（11 + `+Inf`） | ✅ **数值逐条一致** |
  | 只 `publishPercentileHistogram()` | 69 | ❌ |
  | 裸 Timer | 0（只有 `_count/_sum/_max`） | ❌ |

  ⇒ 「只 SLO」是**唯一**能给出 Go 那 11 个边界的配置。
- **守门测试**：`HotApiRealTomcatGateTest#prometheusBucketBoundariesMatchGoDefBuckets`
  （**真 Tomcat + 真 `/actuator/prometheus`**）⇒ 断言边界**数量 = 12**、**逐条数值相等**、
  末位 = `+Inf`，并**反向断言** Micrometer 的 exponential 边界（`0.001048576` 等）**一个都不许出现**。
- **变异验证（3 条，全部按预期变红）**：

  | # | 变异 | 实测 |
  |---|---|---|
  | **M-P1** | 加回 `.publishPercentileHistogram()` | ✅ `Expected size: 12 but was: 80` |
  | **M-P2** | 把一个 SLO 边界改错（10s → 20s） | ✅ `expected: 10.0 but was: 20.0` |
  | **M-P3** | 去掉 `.serviceLevelObjectives(...)` | ✅ `Expected size: 12 but was: 0` |

- ⚠️ **残留的库层差异（不修，见 DRIFT-HOTAPI-017 同族）**：
  **文本**形态不同 —— Micrometer 写 `1.0 / 5.0 / 10.0`，client_golang 写 `1 / 5 / 10`
  ⇒ **数值相同、文本不同** ⇒ Prometheus 的 `le` 是字符串标签，标准解析按数值处理 ⇒ 语义等价。

---

## DRIFT-HOTAPI-019 ✅ 已签字（保持）· OBSERVABLE DIFFERENCE · LOW
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已签字（保持）** ⇒ **现行态 = ✅ 已签字（保持）**（未改判；本条**原无指针**，本轮补上）。
> 依据：RAG `service/hot/hot-api/src/main/resources/application.yml:182-183`（`management.metrics.tags.application: hot-api` ⇒ 每个指标多一个 `application` 标签）+ `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiRealTomcatGateTest.java:205`（`globalApplicationTagIsRagOnly`，真 Actuator 断言）· Go `service/hot/api/internal/metrics/metrics.go:45-51`（`prometheus.MustRegister(...)` ⇒ **默认 registry** ⇒ **无任何全局标签**）· 核对日期 2026-10-30。
> ⇒ ⇒ **被签字描述的行为仍在** ⇒ 与 `docs/Intentional_Deviations/hot/api.md` 的 `SD-HOT-API-007`（✅ 仍成立）一致 ⇒ **维持签字**。

- **Component**：Prometheus 全局标签 `application="hot-api"`
- **Go 行为**：`prometheus.MustRegister(...)` 注册到**默认 registry** ⇒ **无任何全局标签**。
- **RAG 行为**：`application.yml` 的 `management.metrics.tags.application: hot-api`
  ⇒ 每个指标都多一个 `application` 标签。
- **⚠️ 这不是 hot 域单点问题**：全仓 **article-api / article-rpc / comment-api / favorite-api /
  favorite-rpc / task-rpc …** 都配了同一项 ⇒ 是**跨域约定** ⇒ 按 §5.3 hot 域**无权单独改**。
- **影响**：低 —— 不改任何业务判定；但按「Go 无此标签」的字面口径，两侧的指标**标签集不同**
  ⇒ 跨实现直接写 PromQL 会对不上（需要先 `sum by(...)` 去掉 `application`）。
- **处置**：✅ **已签字（2026-10-24 第六轮，用户拍板「保持」）** ⇒ `SD-HOT-API-007`。
  理由：全仓统一都配了这一项 ⇒ hot-api 单独去掉会成为**全仓唯一的例外**；
  影响只到标签集，**不改任何指标数值与业务判定**；与 `SD-HOT-API-004` 同族。

---

## DRIFT-HOTAPI-020 ✅ 已签字（保持）· OBSERVABLE DIFFERENCE · MEDIUM（**部署面**）

- **Component**：hot-api 的**服务注册**（Nacos）
- **Go 行为**：**完全不注册** —— `service/hot/api/` 全目录 `grep -rn "nacos\|Nacos"` **零命中**；
  `internal/svc/serviceContext.go` 只有两个 `zrpc.MustNewClient`（hot-rpc / article-rpc），**没有任何注册中心**。
- **RAG 行为**：pom 引了 `spring-cloud-starter-alibaba-nacos-discovery`
  ⇒ Spring Cloud Alibaba **自动注册**到 Nacos。
- **⚠️ 实测到的后果（可用性级别）**：`@SpringBootTest(webEnvironment = RANDOM_PORT)` 下
  **Nacos 不可达 ⇒ 整个上下文起不来**：
  ```
  Caused by: ErrCode:-401, ErrMsg:Client not connected, current status:STARTING
      at com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy.requestToServer
  Caused by: org.springframework.context.ApplicationContextException: Failed to start bean 'webServerStartStop'
      at com.alibaba.cloud.nacos.registry.NacosServiceRegistry.register
  ```
  ⇒ **注册失败 ⇒ web server 起不来 ⇒ 进程退出**。
  ⚠️ 这与 hot-rpc 的 **DRIFT-HOTRPC-010**（注册失败 ⇒ 存活 + 健康检查 NOT_SERVING）
  **方向相反**：hot-rpc 侧已按「保持存活」签字，hot-api 侧却是「注册失败 ⇒ 启动失败」。
- **影响**：中等 —— 服务发现形态不同（Go 侧 hot-api 不在注册中心里），
  且 **Nacos 抖动 ⇒ hot-api 直接起不来**（Go 侧不受影响）。
- **处置**：✅ **已签字（2026-10-24 第六轮，用户拍板「保持」）** ⇒ `SD-HOT-API-008`。
  理由：其余 9 个 api + 全部 rpc 模块都注册 ⇒ 对齐 Go 会让 hot-api 成为**唯一不入注册中心**的服务。
  ⚠️ 签字即接受残留风险：「Nacos 不可达 ⇒ hot-api 起不来」；
  ⚠️ 与 hot-rpc 侧 `DRIFT-HOTRPC-010`（「注册失败 ⇒ 存活 + NOT_SERVING」）**方向仍相反**，本轮不动
  （改它要 `service/common` 的 `HealthIndicator` 能力 ⇒ 跨域），但**已留痕**。
  原备选（未采纳）：`spring.cloud.nacos.discovery.enabled=false`（或去掉 starter）；
  2. **保持 + 签字**：与 hot-rpc 同口径「注册失败 ⇒ 存活 + 健康检查 NOT_SERVING」
     ⇒ 需**同样补**健康检查（hot-api 侧目前**没有**）。
- ⚠️ 本轮**只登记、不擅改** —— 改 pom / 改部署形态都超出「域内漂移对齐」的范围。

---

## ⚠️ 本轮**未**闭合的项（不得读成「hot-api 已完成」）

| 项 | 状态 | 说明 |
|---|---|---|
| **端到端双侧活体对拍**（D1/U1） | ✅ **本轮已闭合** | 两套栈**同时拉起**（Go hot-api + RAG hot-api + 下游同构双桩），29 条用例逐字节比**响应体 + 关键响应头** + 期望值 + **下游调用序列** ⇒ 默认配置 **24/29 通过**，并**新查出 4 条漂移**（D21~D24）；带修正配方时 **28/29**（剩下的 1 条 = D14，已移交 common 域） |
| **超时 / 保护层四闸的活体观测**（D9/U2/U5） | ✅ **第八轮已闭合** | **超时**：CASE-21 让请求真的拖过 3s ⇒ 两侧都产出 `503 + "Request Timeout\n"`（body 逐字节相同），只差 `nosniff`（= D14，跨域移交 common 域）。**保护层四闸（第八轮）**：**MaxBytes** 两侧默认阈值下逐字节一致（超限 → 413 + 空 body + 无 Content-Type；边界也一致）；**MaxConns** 同阈值 8 + 16 并发 → 两侧**各 8 个裸 503**（机制等价）；**Breaker** 两侧都真的打开 → 裸 503 + 空 body。🔴 **CpuShedding 在 darwin 上 Go 侧结构性不可达**（`//go:build !linux` ⇒ `CpuUsage()` 恒 0 ⇒ 永不降载；活体佐证 = Go stat 行 `CPU: 0m`）⇒ 只能静态 + go-zero 上游测试取证 ⇒ **这是「本机不可能更强」的证据，不是「两边一致」** |
| **客户端断连**（D3/U3） | ✅ **第七轮已闭合** | 裸 socket + SO_LINGER=0 强制 RST ⇒ Go 侧 8 个在飞 gRPC 在**断连后 1~3ms** 全部取消、**日志有 `[HTTP] 499`**；RAG 侧**等 2s deadline**、**断连后继续发下游调用**、**无任何 499 记录** ⇒ **D3 坐实** + 新产出 **DRIFT-HOTAPI-025** |
| **变异验证**（D2/U9） | ✅ **第十轮已闭合（组件级）** | **hot-api 主干 10 个系统性语义变异 ⇒ 10/10 全部被守门抓到**（基线 85/0）；🔴 并**查出并修补 1 个真实缺口**（`page_size=51` 边界无测试）；⚠️ 第一轮 3 个「没变红」**全是变异本身 no-op**（P15 教训：「变异没红」先证伪变异有效性）。✅ **第十一轮已扩展到全 hot 域**：hot-rpc **10 个**（基线 86）+ hot-common **4 个**（基线 14）⇒ **25/25 全部被守门抓到**；hot-rpc 侧查出并修补 **2 个真实守门缺口** |
| **延迟分布 / 背压形态**（U10） | ✅ **已闭合并已修** | 8 并发 × 100 items × 20 轮（160 请求/侧）：**成功率两侧 160/160 全 200**（结果一致）；初测 **p99 1.76×**、下游在途峰值 **Go 64 vs RAG 32** ⇒ **DRIFT-HOTAPI-028**。✅ **同日已修**（池 32→64）：复测 **峰值 64 vs 64**、**p99 1.08×**、p50 **0.96×** ⇒ 残余差异转 **SD-HOT-API-010**（RAG 保留跨请求总上限 = 比 Go 更保守的一侧，已签字）。⚠️ **旧结论被推翻**：`CallerRunsPolicy` 实测**从未触发**（峰值未超池容量）⇒ 背压形态是**纯排队**，与 Go 同类 |
| **端口归属**（D6） | ✅ **第六轮已签字** | 用户 2026-10-24 拍板「保持」⇒ `SD-HOT-API-009`：保持 18897（`HOT_API_PORT` 可覆盖）。⚠️ HTTP 端口**不是 wire 契约** |
| **下游 gRPC 错误串形态**（DRIFT-HOTAPI-017） | ✅ **第六轮已签字** | 用户 2026-10-24 拍板「不复刻」⇒ `SD-HOT-API-005`。库层差异（grpc-go `"rpc error: code = Unavailable desc = …"` vs grpc-java `"UNAVAILABLE: …"`）|
| **Prometheus 分桶边界**（DRIFT-HOTAPI-018） | ✅ **本轮已闭合** | 真 Actuator 实测：`publishPercentileHistogram()` 会塞进 69 个 exponential 边界 ⇒ 实测 80 个 vs Go 的 12 个 ⇒ **已修**，3 条变异全红 |
| **真 Tomcat 响应头 / percent-encoding**（U6/U8/D14） | ✅ **本轮已闭合** | 真 Tomcat（RANDOM_PORT）下：404 = `404 page not found\n` + `text/plain` + `nosniff`；405 = 裸状态码 + `Allow`、无 body；`%2B5` ⇒ 解码成 `+5` ⇒ `page=5`（两侧等价）。⚠️ D14 本身仍**跨域移交** common 域（那是超时/499 路径，不是 404/405） |
| **Prometheus 导出形态**（U11） | ✅ **本轮已闭合** | 真 `/actuator/prometheus`：Counter 名 `hot_api_request_total`（**两侧都是单 `_total`**，原注释的「双 `_total`」说法已被实测推翻并订正） |
| **client breaker 装配层**（U4/D8） | ✅ **已闭合（第十轮）** | hot-api 此前**没有** wiring 测试（而 `follow-api` / `message-api` 各有一份）⇒ 第四轮新增 `GoStyleGrpcClientBreakerWiringTest`（3 例）+ 变异 M-W1 变红（**装配层**）；🟥 **第十轮补齐行为层**：`GoStyleGrpcClientBreakerE2ETest` 内嵌恒返 INTERNAL 的 gRPC server，判据是「**下游调用次数停止增长**」（hot-api 响应体前后都是 5001，**看不出**熔断）⇒ **「熔断真的打开」已被行为级证据坐实**；变异（`if (!breaker.accept())` → `&& Boolean.FALSE`）变红 ✅。⚠️ 第十二轮附带修：该测试阶段 F 的**比率型断言**改为**直接计数**（P-HOTAPI-23） |
| **全局 `application` 标签**（DRIFT-HOTAPI-019） | ✅ **第六轮已签字** | 用户 2026-10-24 拍板「保持」⇒ `SD-HOT-API-007`：全仓统一配了同一项 ⇒ hot-api 单独去掉会成为**唯一例外**<br>🔴 **2026-10-29 真实性复核**：原判 **✅ 已签字（行为仍在）** ⇒ **现行态 = ✅ 仍成立**，但**签字理由里的「全仓统一 / 唯一例外」这个前提是错的**（原句保留，不改写）—— 实测（逐模块看 `management:` → `metrics:` → `tags:` → `application:` 这个**嵌套**块；⚠️ **不能用 `grep 'management.metrics.tags.application'` 这种点号路径** —— YAML 是嵌套写法 ⇒ 该串在任何模块里都不出现 ⇒ 会误得「0/8 全没配」）：**8 个非 users 的 api 模块里 5 个配了**（`article-api` `:225` / `comment-api` `:173` / `favorite-api` `:195` / `hot-api` `:183` / **`message-api` `:213`**），**3 个没配**（`follow-api` / `like-api` / `task-api`）⇒ ⇒ hot-api 单独去掉该标签 ⇒ **变成 3 个例外之一**，**不是「唯一例外」**。<br>⇒ 📌 **口径补全**：把 users 域的两个 api 一并计入 ⇒ **全仓 10 个 api 模块里 7 个配了 / 3 个没配**（`users/user` `:187` ＋ `users/admin` `:178` 也配了）⇒ ⇒ **「全仓统一」这个理由的强度下降，但不影响签字本身**（被签字的行为确实仍在：RAG 侧每个 Prometheus 指标仍带 `application="hot-api"`，Go 侧默认 registry 仍不带全局标签）。<br>⇒ ⚠️ **核对日期 2026-10-29**；权威条目见 [`docs/Intentional_Deviations/hot/api.md`](../../Intentional_Deviations/hot/api.md) 的 `SD-HOT-API-007`（含同款订正注记）。 |
| **Nacos 服务注册**（DRIFT-HOTAPI-020） | ✅ **第六轮已签字** | 用户 2026-10-24 拍板「保持」⇒ `SD-HOT-API-008`。⚠️ 签字即接受残留风险：「Nacos 不可达 ⇒ hot-api 起不来」（实测 `ErrCode:-401`）；缓解手段 = `register-enabled=false` |
| **日志面已对齐的部分** | ✅ **本轮闭合** | `DRIFT-HOTAPI-016` 已修（`CodeError.Error()` 整串 + 日志级别 + 字段集），3 条变异全红 |
| **容器输入上限三旋钮**（D21/D22/D23 ⇒ `DRIFT-HOTAPI-021/022/023`） | ✅ **第十三轮已闭合并已修（跨域落地 10/10）** | 10 个 api 模块的 `application.yml` 统一配 `relaxed-query-chars: ["[", "]"]` / `max-parameter-count: 2048` / `max-http-request-header-size: **1028KB**`（⚠️ **不是 1MB**：Go 真实上限 = `DefaultMaxHeaderBytes(1<<20)` **+ 4096 bufio slop**）。**双侧活体复测**：`?page[]=5` 两侧 **200 且响应体逐字节相同**；值个数首个翻转 n **两侧都是 2049**；请求行首个翻转长度 **两侧都是 1052536 字节（差 0）**。守门 = `tools/difftest/check-tomcat-limits.py`（静态 10/10 ✅）＋ `HotApiRealTomcatGateTest` 3 条**真 Tomcat**运行时断言（**MUT-A/B/C 变异全红** ✅） |
| ↳ **残余 ①**（`DRIFT-HOTAPI-030`） | 🔴 **未修（跨域 · 待拍板）** | Tomcat 数「**总参数个数**」、Go 数「**非空值个数**」⇒ 语义不同 ⇒ **2049~10000 段且非空值 ≤2048** 时 **Go 200 / RAG 5001**。彻底修需「② 改 `-1` ＋ 每个 api 模块补 Go 的 `net/url` 10000 段上限」⇒ 涉及 10 个域（`favorite` 域台账还是 0 字节）⇒ 本轮不越界改<br>📌 **2026-10-29 对齐（台账对齐 phase · W-E lane）—— 原值保留 + 追加**：这一格的**描述**（2049~10000 窗口）**已被共享层的落码消掉** ⇒ **主轴 ✅ 已闭合**（共享层 `GoStyleTomcatParameterLimitCustomizer.MAX_PARAMETER_COUNT = 10001` 且 `customize(...)` **无 `mode` 分支** ⇒ 无条件抬上限 ⇒ 2026-10-29 复核代码仍如此）⇒ **本行原写的「未修」指的是「主轴未修」，现已不成立**。<br>⇒ **当前仍 🔴 的只有「后续格（段数轴）」**：共享层 `MAX_PARAMETER_COUNT` **至今 = 10001**（未改 10000）⇒ **RAG 翻转点 = 10002 段 / Go = 10001 段**（Go 上限 = `net/url/url.go:1001` `defaultMaxParams = 10000`）⇒ 差一格 ⇒ **修法 = 共享层 `10001 → 10000`**（共享面 ⇒ 本域无权改）⇒ 逐格状态 ⇒ 见本文件 `DRIFT-HOTAPI-030` 条目的「2026-10-29 对齐」块 |
| ↳ **残余 ②**（`DRIFT-HOTAPI-031`） | 🟡 **未修（框架限制）** | 超长请求行：Go **431 + `text/plain`** vs RAG **400 + Tomcat HTML**（Tomcat 在**连接器层**就拒 ⇒ Spring 的 `GlobalExceptionHandler` 没机会介入）⇒ Tomcat 无对应开关 ⇒ 建议登记为签字偏离候选 `SD-2026-10-25-CAND-10`（**未签字**）。触发条件是请求行 > 1 MiB+4 KiB（病态输入） |

---

# 🆕 第五轮（2026-10-24）· U1 / D1 端到端双侧活体对拍

> **本节是 hot-api 组件的收口工作**。前面四轮都是**分层**对拍（Go 真库函数 ↔ RAG 真实 MVC 管线），
> 两侧**从未同时跑起来**。本轮第一次把两套 HTTP 栈同时拉起，做**逐字节**差分。
>
> **本轮推翻了自己前一轮的 4 条结论** —— 全部是「MockMvc 测不到真 Tomcat」的形态（§3.3 的老坑）。

## 1. 工具与编排

| 文件 | 作用 |
|---|---|
| `tools/difftest/run-hot.sh` | 编排：构建 Go hot-api + RAG hot-api + 编译桩 → 起 4 个进程 → 跑对拍 → 收摊。`--no-build` / `--keep` / `--serve-only` |
| `tools/difftest/conf/go/hot-api.yaml` | Go 真源 `service/hot/api/etc/hotcenter.yaml` 的**副本**（真源零改动）：`Etcd` 整块移除改 `Endpoints` 直连、`Log.Mode: console`、独占端口 18903 / DevServer 19069 |
| `tools/difftest/hot-stub-server.java` | **同构双桩**：一个进程同时提供 `hot.HotService` + `article.ArticleService`，数据固定、可按「模式文件」切行为 |
| `tools/difftest/diff-hot.py` | 29 条用例的逐字节对拍矩阵 |
| `tools/difftest/probe-hot-limits.py` | 输入上限探针（二分出两侧的**值个数阈值**与**请求行长阈值**） |
| `tools/difftest/check-tomcat-limits.py` | **跨模块**静态守门：10 个 api 进程的三个容器旋钮是否配好且**值正确**（第十三轮新增） |

**端口分配**（全部独占，**没有碰**本机的 `Sea-*` 容器与 `Sea-RideTheWind_java` 进程）：
Go hot-api `18903` · RAG hot-api `58903` · Go 侧桩 `19903` · RAG 侧桩 `19904`。

**为什么下游用桩、而不是真 hot-rpc / 真 article-rpc**：
hot-api 的链路是 `HTTP → hot-rpc.GetHotArticles(100) → article-rpc.GetArticle(每条)`。
若用真下游，两侧会引入两个**与 hot-api 无关**的不确定源：① 两侧 Kafka 消费进度不同；
② 两侧 Redis 快照写入时机不同 ⇒ 那是「对拍环境」的漂移，不是 hot-api 的漂移，判别力归零。
⇒ 下游钉死，任何字节差异都 **100% 归因于 hot-api**。
（代价：**hot-rpc 自身的行为不在本轮覆盖范围** —— 那属 `hot/rpc.md` 的 D1。）

**为什么用「同构双桩」而不是「一个共用桩」**：
共用桩分不清「谁发起的调用」。同构双桩各写各的调用日志 ⇒ 除比 HTTP 响应，还能比
**下游调用序列**（比了就知道两侧调的方法、参数、条数是否一致 —— 例如两侧都跳过空 `article_id` 的那条）。

## 2. 结果

| 配置 | 通过 | 失败用例 |
|---|---|---|
| **默认**（RAG 侧不配任何 Tomcat 旋钮） | **24 / 29** | `CASE-04b`、`CASE-04c`、`CASE-15`、`CASE-16`、`CASE-21` |
| **带修正配方**（见 §4） | **28 / 29** | `CASE-21`（= D14，已移交 common 域） |

**24 条通过意味着**：信封形状、字段顺序、`items:[]` vs `null`、`secondary_tags:null`、
`hot_score` 无符号（4294967295）、HTML 不转义、`scope` 常量、分页两层归一化、
int32 溢出空页、rank 跨页连续、fatal vs best-effort 的不对称、404/405 裸响应、
畸形 JSON body 拒绝 —— 这些**全部逐字节相同**。

**每条用例都做 5 层断言**：
① 跨侧状态码相同 ② 跨侧**响应体逐字节**相同（**不是** JSON 语义比对 ——
JSON 比对会放过字段顺序、`null` vs `[]`、uint32 溢出这类差异，而那正是本域最常踩的坑）
③ 跨侧**关键响应头**相同（`content-type` 媒体类型 / `x-content-type-options` / `allow`）
④ 每条用例的**期望值**（来自 Go 真源语义）必须在**两侧**都成立
⑤ 两侧桩的**下游调用序列**（多重集）相同

## 3. 🆕 本轮新查出的 4 条漂移

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/probe-hot-limits.py` ⇒ **EXIT=0**（2026-10-30 03:43:43 · 2s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：· RAG 侧：值个数阈值 2049（Tomcat maxParameterCount 配 2048 ⇒ 第 2049 个抛 InvalidParameterException ⇒ 被 catch-all 吞成 {code:5001}） ⏐ ⇒ 值个数阈值：Go 2049 / RAG 2049  ✅ 一致 ⏐ ⚠️ 不是 1MB：Go 的真实上限是 1 MiB **+ 4096**（net/http/server.go:941 'buf；<br>`tools/difftest/run-hot.sh` ⇒ **EXIT=1**（2026-10-30 03:39:35 · 30s）—— 🔴 **探针判定为「不一致」**；覆盖断言：✓ CASE-18    畸形 JSON body ⇒ 5001 ⏐ ✓ CASE-19    不存在的路径 ⇒ 404 裸响应（text/plain + nosniff） ⏐ ✓ CASE-20    方法不匹配 ⇒ 405 裸状态码（无 body） ⏐ ✗ CASE-21    下游拖过 3s ⇒ 503 Request Timeout（校验 nosniff） ⏐ ↳ 已知漂移：503 响应缺 X-Content-Type-Opti。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

### DRIFT-HOTAPI-021 —— Tomcat 请求目标字符校验拒绝 `?page[]=5` 🔴 SEMANTIC MISMATCH（跨域）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修（第十三轮「Tomcat 三旋钮」轮 · 10/10 落地）** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/resources/application.yml:48`（`relaxed-query-chars: ["[", "]"]`）+ 守门 `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiRealTomcatGateTest.java:284`（`bracketArrayNotationIsAcceptedLikeGo`，**真 Tomcat + 裸 socket**）+ 跨模块守门 `tools/difftest/check-tomcat-limits.py`（**本轮实跑 10/10 ✅ · EXIT=0**）· Go `service/hot/api/internal/types/types.go:22-25`（`page` / `page_size` 的 form tag）· 核对日期 2026-10-30。
> ⚠️ **本条 `###` 标题里的 🔴 = 严重度**（按本文件 `:890-897` 的图例）⇒ **差异本身的量级不变**，修复合灭它、严重度不变。

> ✅ **已修（2026-10-25 第十三轮「Tomcat 三旋钮」轮）** —— 10 个 api 模块统一配
> `server.tomcat.relaxed-query-chars: ["[", "]"]`。
> **双侧活体复测**（`tools/difftest/run-hot.sh --serve-only` + 双侧探针）：
> `?page[]=5` ⇒ **Go 200 / RAG 200**，且**响应体逐字节相同**
> （两侧都是 `{"code":200,"msg":"OK","data":{...,"page":5,...}}`，各 95 B）。
> **运行时守门**：`HotApiRealTomcatGateTest#bracketArrayNotationIsAcceptedLikeGo`
> （**真 Tomcat + 裸 socket** —— `URI.create` 会拒 `?page[]=5`，用 JDK HttpClient 等于没测）。
> **变异验证 MUT-A**（摘掉该旋钮）⇒ 对应断言**转红** ✅ ⇒ 不是假绿。
> **跨模块守门**：`tools/difftest/check-tomcat-limits.py`（10/10 ✅ `EXIT=0`；
> 漏配任一模块 ⇒ `EXIT=1` 并指名道姓）。
> ⚠️ **写法从 `"[,]"` 改成 YAML 列表 `["[", "]"]`** —— 两种写法都被 Boot 接受
> （`List<Character>` 的 binder 歧义），但列表形式**无歧义**（§3.3：连「配置怎么写」都会漂）。

| | Go 真源 | RAG |
|---|---|---|
| 请求 | `GET /hot/v1/articles?page[]=5&page[]=9` | 同 |
| 响应 | **HTTP 200** + `{"code":200,...,"page":5,...}` | **HTTP 400** + **Tomcat 的 HTML 错误页**（435 B，`text/html`） |

**根因**（RAG 侧实测栈）：
```
java.lang.IllegalArgumentException: Invalid character found in the request target
  [/hot/v1/articles?page[]=5&page[]=9 ]. The valid characters are defined in RFC 7230 and RFC 3986
  at org.apache.coyote.http11.Http11InputBuffer.parseRequestLine(Http11InputBuffer.java:473)
  [tomcat-embed-core-11.0.24.jar]
```
⇒ Tomcat 11.0.24 在 **HTTP 解析层**就拒了（请求**根本没进 Servlet**），
⇒ 响应**不是**业务信封，而是 Tomcat 的 HTML 错误页 ⇒ **形状与状态码双双不同**。

⚠️ **这条直接推翻了第二轮 CASE-04b / CASE-04c 的 ✅ 结论**：
那两行是**MockMvc** 测的，MockMvc **不走 Tomcat 的请求行校验** ⇒ 假绿。
（同一形态还有第四轮的 U6/U8「真 Tomcat 补测」—— 那些是真 Tomcat，所以结论有效。）

⚠️ **更刺眼的一点**：RAG 侧 `GoFormValues.firstFormValue` **专门实现了** `name + "[]"`
的回退（注释里写着「Go `?page[]=5` 剥掉 `[]` 后缀后归到 key `page`」），
但在真栈里**这段代码是死的** —— 请求到不了控制器。
⇒ 「代码写了」≠「客户端收到」的老坑又一次。

**修法（已实测）**：`server.tomcat.relaxed-query-chars='[,]'`
（Boot 4 的该属性是 `List<Character>` ⇒ **必须按逗号分隔传**，传 `'[]'` 会报
`String [[]] with length 2 cannot be converted to char type`）。
该属性的描述限定「只有 `< > [ \ ] ^ ` { | }` 可加」⇒ `[` `]` 在允许集内。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/run-hot.sh` ⇒ **EXIT=1**（2026-10-30 03:39:35 · 30s）—— 🔴 **探针判定为「不一致」**；覆盖断言：✓ CASE-18    畸形 JSON body ⇒ 5001 ⏐ ✓ CASE-19    不存在的路径 ⇒ 404 裸响应（text/plain + nosniff） ⏐ ✓ CASE-20    方法不匹配 ⇒ 405 裸状态码（无 body） ⏐ ✗ CASE-21    下游拖过 3s ⇒ 503 Request Timeout（校验 nosniff） ⏐ ↳ 已知漂移：503 响应缺 X-Content-Type-Opti。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

### DRIFT-HOTAPI-022 —— form 参数个数上限：Go 2048 vs RAG 1000 🔴 OBSERVABLE DIFFERENCE（跨域）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修（第十三轮「Tomcat 三旋钮」轮 · 10/10 落地）** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/resources/application.yml:49`（`max-parameter-count: 2048`）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/form/GoFormValues.java:224-245`（RAG **自己**的应用层 2048 判据：`n > 2048` 即抛）+ 守门 `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiRealTomcatGateTest.java:305`（`formValueCountLimitMatchesGo`）+ 跨模块守门 `tools/difftest/check-tomcat-limits.py`（**本轮实跑 10/10 ✅ · EXIT=0**）· Go `rest/httpx/util.go:15`（`maxFormParamCount = 2048`）+ `rest/httpx/util.go:48-53`（第 **2049** 个非空值报错）· 核对日期 2026-10-30。
> ⇒ ⇒ **两侧首个翻转 n 都是 2049** ⇒ 「RAG 侧自己实现的 2048 上限在 GET query 上**不可达**」这个副作用**已消除**（原文保留）。
> ⚠️ **本条 `###` 标题里的 🔴 = 严重度**（按本文件 `:890-897` 的图例）⇒ 严重度不变；残余差异仍归 `DRIFT-HOTAPI-030`（不在本清单内）。

**实测**（`probe-hot-limits.py` 二分，「第一个不再是 200/{code:200} 的 n」）：

| | 首个翻转 n | 翻转时的响应 |
|---|---|---|
| Go | **2049** | HTTP 200 + `{"code":5001,...}`（**应用层**拒绝） |
| RAG（默认） | **1001** | HTTP 200 + `{"code":5001,...}`（**Tomcat 层**拒绝） |

⇒ 形态**恰好相同**（都是 200 + 5001），但**阈值差一半** ⇒
**1001 ~ 2048 这段区间：Go 成功、RAG 5001** ⇒ 可观察差异。

**根因**：Boot 4 的 `server.tomcat.max-parameter-count` 默认 **1000**
⇒ Tomcat 抛 `InvalidParameterException` ⇒ 被 RAG 的 catch-all
（`GlobalExceptionHandler.onAnyException`）吞成 `{code:5001}`。
```
org.apache.tomcat.util.http.InvalidParameterException: More than the maximum number of
request parameters (GET plus POST) for a single request ([1,000]) were detected.
```

⚠️ **副作用**：RAG 侧**自己实现的** 2048 上限（`GoFormValues.requireFormValueCountWithinLimit`）
在 **GET query 上不可达** —— Tomcat 总是在 1000 就先拒了。
⇒ 台账 D12「✅ 已实现」在**实现层面**成立，但在**运行时不可达** ⇒ 须与 D22 合并看。

**修法（已实测）**：`server.tomcat.max-parameter-count=2048`
⇒ 实测阈值变成 **2049**，与 Go **完全一致**。

> ✅ **已修（2026-10-25 第十三轮「Tomcat 三旋钮」轮）** —— 10 个 api 模块统一配
> `server.tomcat.max-parameter-count: 2048`。
> **双侧活体复测**：首个翻转 n **两侧都是 2049**
> （Go 200 + `{code:5001}` / RAG 200 + `{code:5001}`，**响应体各 55 B 且逐字节相同**）。
> ⇒ 「RAG 自己实现的 2048 上限在 GET query 上**不可达**」这个副作用**已消除**。
> **运行时守门**：`HotApiRealTomcatGateTest#formValueCountLimitMatchesGo`
> ⇒ **变异验证 MUT-B**（改回 1000）⇒ 对应断言**转红** ✅。
> ⚠️ **残余差异（新编号）**：Tomcat 数的是「**总参数个数**」，Go 数的是「**非空值个数**」
> ⇒ 两者只在「值全非空」时阈值巧合相等 ⇒ 精确窗口见 **`DRIFT-HOTAPI-030`**。
> ⚠️ **② 为什么配 2048 而不是 `-1`（关掉）**：`service/**` 实测只有 **article / hot / favorite**
> 三个 api 复刻了 Go 的应用层判据，**另外 7 个 api 模块 Tomcat 这层就是唯一的上限**
> ⇒ 配 `-1` 等于**拆掉那 7 个模块的唯一上限**；且 Go 1.25 `net/url` 还有 `defaultMaxParams = 10000`
> 的**段数**上限（`article-api` 复刻了、`hot-api` 没有）⇒ 关掉后顶部会**多出**一段新差异。
> ⇒ 2048 是「当前各模块应用层复刻程度下」的**最小公倍数**；彻底对齐的修法见 `DRIFT-HOTAPI-030`。

### DRIFT-HOTAPI-023 —— 请求行长上限：Go 1 MiB vs RAG 8 KiB 🔴 OBSERVABLE DIFFERENCE（跨域）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修（第十三轮「Tomcat 三旋钮」轮 · 10/10 落地）** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/resources/application.yml:50`（`max-http-request-header-size: 1028KB` —— ⚠️ **不是 1MB**，见条目正文）+ 守门 `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiRealTomcatGateTest.java:330`（`requestLineLengthLimitMatchesGo`）+ 跨模块守门 `tools/difftest/check-tomcat-limits.py`（**本轮实跑 10/10 ✅ · EXIT=0**）· Go `net/http/server.go:940-942`（`initialReadLimitSize` = `DefaultMaxHeaderBytes(1<<20) + 4096` bufio slop ⇒ 真实上限 **1052536** 字节）· 核对日期 2026-10-30。
> ⚠️ **本条 `###` 标题里的 🔴 = 严重度**（按本文件 `:890-897` 的图例）⇒ 严重度不变；残余差异（431 + `text/plain` vs 400 + Tomcat HTML）仍归 `DRIFT-HOTAPI-031`（在本清单内，已单列）。

**实测**（单值 query，隔离长度变量）：

| | 首个翻转长度 | 翻转时的响应 |
|---|---|---|
| Go | **> 200000 字节**（`net/http` `MaxHeaderBytes` 默认 **1 MiB**） | — |
| RAG（默认） | **8056 字节** | **HTTP 400** + **Tomcat HTML 错误页**（435 B） |

⇒ 8 KiB ~ 1 MiB 的 query：Go 正常处理（并可能因值个数超限而回 5001），
RAG 直接 **400 + HTML 错误页** ⇒ 响应**形状**都不同（不是业务信封）。

**根因**：Boot 4 的 `server.max-http-request-header-size` 默认 **8KB**
⇒ Tomcat 的 `maxHttpHeaderSize`（请求行 + 全部请求头一起算）。

**修法（已实测）**：`server.max-http-request-header-size=1MB`
⇒ 实测到 200000 字节都不翻转，与 Go 一致。

> ✅ **已修（2026-10-25 第十三轮「Tomcat 三旋钮」轮）** —— 10 个 api 模块统一配
> `server.max-http-request-header-size: 1028KB`。
> **双侧活体复测**：首个翻转长度 **Go 1052536 字节 = RAG 1052536 字节（差 0 字节）**。
> **运行时守门**：`HotApiRealTomcatGateTest#requestLineLengthLimitMatchesGo`
> ⇒ **变异验证 MUT-C**（改回 8KB）⇒ 对应断言**转红** ✅。
>
> ⚠️⚠️ **最终值不是 1MB，是 1028KB** —— 本条原先写的 `1MB` **比 Go 严 4096 字节**：
> Go 的**真实**读上限 = `DefaultMaxHeaderBytes(1<<20)` **+ 4096**，
> 出处 `net/http/server.go:940-942`
> ```go
> func (s *Server) initialReadLimitSize() int64 {
> 	return int64(s.maxHeaderBytes()) + 4096 // bufio slop
> }
> ```
> ⇒ 实测：Go 在 query 长 **1052536** 字节处翻转（431），
> 而配 `1MB` 的 RAG 在 **1048440** 字节就翻转（400）—— 差的**正好是这 4096 字节**。
> ⇒ 配 `1028KB` 后两侧翻转点**完全重合**。
> ⇒ ⚠️ 改配置时**不要**「顺手改回 1MB」：守门脚本 `check-tomcat-limits.py` 断言的是
> **1052672 字节**，改回 1MB 会被当场抓住（`EXIT=1`）。
>
> ⚠️ **残余差异（新编号）**：超限后的**状态码与错误体形状**仍不同
> （Go 431 + `text/plain` vs RAG 400 + Tomcat HTML）⇒ 见 **`DRIFT-HOTAPI-031`**（框架限制）。

### DRIFT-HOTAPI-024 —— `Content-Type` 的 charset 参数 ✅ **已修**（第二十五轮 · global T-6）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修（第二十五轮 · global T-6）** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:151`（共享层 `ResponseBodyAdvice` ⇒ 写 `application/json;charset=utf-8` ⇒ **10 个 api 全生效**，含 hot-api）+ `service/common/src/test/java/com/rag/common/http/GoStyleJsonContentTypeCharsetTomcatTest.java`（真 Tomcat + 裸 socket 守门，在仓）· Go `rest/internal/header/headers.go:17`（go-zero v1.10.2 `header.ContentTypeJson = "application/json; charset=utf-8"`）· 核对日期 2026-10-30。
> ⇒ ⇒ **「RAG 不带 charset」这个被签字描述的行为已不存在** ⇒ 与 `docs/Intentional_Deviations/hot/api.md` 的 `SD-HOT-API-006`（⛔ 已撤销）口径一致 ⇒ **本条确实已修**，不重复登记。

> 🟥 **2026-10-25 订正 + 落地**（本条原先的状态是「✅ 已签字（不复刻）」⇒ **该签字已被用户撤销**）：
> ① `SD-HOT-API-006` **已撤销**（用户拍板）⇒ 被它签字描述的行为（「RAG 的 JSON 响应不带 charset」）**已不存在**；
> ② 修法 = **共享层** `service/common` 新增 `GoStyleJsonContentTypeAdvice`
>    （`@AutoConfiguration` ⇒ **10 个 api 全部生效** ⇒ **零 api 代码改动** ⇒ 含 favorite）；
> ③ ⚠️ **「不可逐字节复刻」这个结论**（本节原文写的「Spring 的 `MediaType.toString()` 少个空格」）
>    **仍然成立，而且理由更强**：真 Tomcat 11 把 Content-Type 拆成 (type/subtype, charset) 重新拼
>    ⇒ 线上恒为 `application/json;charset=utf-8` ⇒ **写原始字符串也拿不到那个空格**；
> ④ ⇒ 修后残余 = **只差一个 OWS**（RFC 9110 OWS 不显著 ⇒ 语义相同）
>    ⇒ 与 404 路径同属**仓库早已接受**的类别（`LikeApiRouterErrorRealTomcatTest` 解析后比对）。
> ⑤ 守门 = `service/common` 的 `GoStyleJsonContentTypeCharsetTomcatTest`（真 Tomcat + 裸 socket，3 条）
>    + 变异 2/2 全红。**逐条证据 ⇒ global 台账 `docs/DRIFT/global/lanes/L1-delivery.md` §十三。**

<details><summary>（以下为 2026-10-24 的原始记录，留档）</summary>

**原始状态**：✅ 已签字（不复刻）—— 当时结论是「即使 RAG 侧显式补 charset，Spring 的 `MediaType.toString()`
产出的是 `application/json;charset=utf-8`（**分号后无空格**）⇒ 与 Go **仍不逐字节相同**」。

</details>

| | 响应头 |
|---|---|
| Go | `Content-Type: application/json; charset=utf-8`（**分号后有空格**） |
| RAG | `Content-Type: application/json`（**无 charset**） |

**根因**：Go 侧 httpx 写死 `header.ContentTypeJson = "application/json; charset=utf-8"`
（go-zero v1.10.2 `rest/internal/header/headers.go:17`）；
Spring 的 `MappingJackson2HttpMessageConverter` 写 `application/json`。

**为什么建议「不复刻」**：即使 RAG 侧显式补 charset，Spring 的 `MediaType.toString()`
产出的是 `application/json;charset=utf-8`（**分号后无空格**）⇒ 与 Go **仍不逐字节相同**。
⇒ 要逐字节相同只能靠 filter 在响应写出后改写 header ⇒ 代价与收益不成比例。
⇒ 建议登记为**有意偏离**（`SD-HOT-API-006`），媒体类型本身（`application/json`）**相同**。

## 4. ✅ 修正配方（**已实测**：三旋钮同时打开 ⇒ 28/29 + 阈值完全对齐）

```yaml
server:
  # 对齐 Go net/http 的真实上限 = DefaultMaxHeaderBytes(1<<20) + 4096 bufio slop
  # （net/http/server.go:941）⇒ 1028KB；Boot 4 默认 8KB
  # ⚠️ 不是 1MB —— 1MB 会比 Go 严 4096 字节（实测：Go 1052536 翻转 / 1MB 时 RAG 1048440 翻转）
  max-http-request-header-size: 1028KB
  tomcat:
    # 对齐 go-zero httpx/util.go:15 的 maxFormParamCount = 2048（Boot 4 默认 1000）
    max-parameter-count: 2048
    # 允许 query 里的 [ ]（Go 的 net/http 允许；Boot 4 默认拒绝 ⇒ 400）
    # ⚠️ 该属性是 List<Character> ⇒ 写成**列表**（`"[,]"` 也能被 binder 接受，但列表无歧义）
    relaxed-query-chars: ["[", "]"]
```

> 🟥 **2026-10-25 第十三轮订正**：本块原写 `max-http-request-header-size: 1MB` 与
> `relaxed-query-chars: "[,]"` ⇒ **两处都不该照抄**（前者少 4096 字节、后者有 binder 歧义）。
> ⇒ 已落地 **10 个 api 模块**并由 `tools/difftest/check-tomcat-limits.py` 守门。

**实测效果**：
- 对拍矩阵 **24/29 → 28/29**（`CASE-04b` / `CASE-04c` / `CASE-15` / `CASE-16` 四条全绿）
- 边界探针：Go 首个翻转 n = **2049**，RAG 首个翻转 n = **2049** ⇒ **完全一致**
- 请求行长阈值：两侧到 200000 字节都**不翻转** ⇒ **完全一致**
- 剩下的唯一失败是 `CASE-21` = **D14**（503 缺 `nosniff`）⇒ 代码在 `service/common`，**已移交 common 域**

⚠️ **本轮只登记、不擅改**：这三个旋钮是 **Tomcat 容器级**的，影响**全部 10 个 api 模块**
（article-api / comment-api / favorite-api / follow-api / hot-api / like-api / message-api /
task-api / user-api / admin-api 同款默认值）⇒ 属**全仓约定**，按 §5.3「只写自己域」
⇒ **跨域移交**，由 common 域 / 用户拍板后统一落地。
（Go 侧 `net/http` 的 1 MiB 与 go-zero 的 2048 都是**库级默认**，RAG 侧要对齐就得**逐个显式配** ——
这就是「默认值藏在库里、照 yaml 对永远看不见」的又一例，§3.3.1。）

## 5. 本轮的方法论教训

### 5.1 MockMvc 不是「真 Tomcat」（第 N 次，但这次代价最大）

第二轮 4 条 ✅ 结论（CASE-04b / 04c / 15 / 16）**全部**建立在一个**不经过 Tomcat 请求行校验**
的测试载体上。⇒ **「MockMvc 实测同」不能写进「等价」那一栏**。
（第四轮的 U6/U8 用了 `RANDOM_PORT` 真 Tomcat，所以那些结论不受影响 —— 本轮实测 404/405 与
percent-encoding 两侧**确实**逐字节一致。）

### 5.2 探针自己也会被环境污染（第二次踩同一类坑）

`probe-hot-limits.py` 第一版量出「Go 首个翻转 n = 1125，HTTP 503」—— **假数**。
根因：`diff-hot.py` 的最后一条用例是 `CASE-21`（下游睡 30s），把桩的**模式文件**留在了 slow 上
⇒ 探针的每个请求都被拖过 3s HTTP 超时 ⇒ 量到的是**超时点**，不是**限制点**。
⇒ 修法：探针启动时先把模式切回 `hot-rpc-empty`（不发任何 article-rpc 调用 ⇒ 响应时间与被测变量无关）。
⇒ **教训**：探针必须**显式设定**它依赖的环境，不能假设「上一个动作留下的环境是对的」。

### 5.3 「同一套代码，配置不同 ⇒ 结论可能完全相反」

同一个 RAG 二进制，只改 3 个启动参数，结果从 **24/29** 变成 **28/29**。
⇒ **结论必须绑定配置**。台账里任何「已对齐 / 不对齐」都必须写清**在什么配置下**测的。

## 6. 本轮**未**覆盖（不得读成「hot-api 全部闭合」）

> ⚠️ **本节已于 2026-10-24 第十轮刷新**（原表里三行写着「未做」但其实已闭合 ⇒ 陈旧状态订正）。
> 🟥 **2026-10-24 第十一轮再订正**：表里第一行「hot-rpc 自身的端到端对拍 ❌ 仍未做」**已过期** ⇒ **第十一轮已闭合**。

| 项 | 状态 | 说明 |
|---|---|---|
| **hot-rpc 自身的端到端对拍** | ✅ **已闭合（第十一轮）** | 走通了文末「hot-rpc D1」节写下的可行性清单：**2 个真 Redis**（Go 23979 / RAG 23980，因两侧 key 硬编码）+ **真 Kafka**（3 分区）+ **自造栅栏**（两侧 group-id 不同 ⇒ 进度天然不对称）+ **真 gRPC**（Go 23983 / RAG 23984）⇒ **10 组 top_k（0/1/5/20/100/101/200/1000/-1/-100）两侧响应逐字节一致**（sha256 相同）；k≤0 ⇒ 回落 20、k>100 ⇒ clamp、items 两侧都 `descending=true`。⚠️ **方法论要点**：事件必须**无桶冲突**，否则随机衰减分支让两侧发散（比出来只有噪声）⇒ 详见 [`hot/rpc.md`](rpc.md) 第十一轮 |
| **超时 / 保护层四闸的活体观测**（D9/U2/U5） | ✅ **已闭合（第八轮）** | 超时：`CASE-21` ⇒ 两侧 `503 + Request Timeout\n`（body 逐字节同），只差 `nosniff`（= D14，**跨域**）。保护层四闸：**MaxBytes / MaxConns / Breaker 三闸双侧活体坐实**；🔴 **CpuShedding 在 darwin 上 Go 侧结构性不可达**（`//go:build !linux` ⇒ `CpuUsage()` 恒 0）⇒ 只能静态 + go-zero 上游测试取证 |
| **客户端断连 499**（D3/U3） | ✅ **已闭合（第七轮）** | 裸 socket + `SO_LINGER=0` 强制 RST ⇒ Go 侧 8 个在飞 gRPC 在**断连后 1~3ms** 全部取消、**日志有 `[HTTP] 499`**；RAG 侧**等 2s deadline**、**断连后继续发下游调用**、**无任何 499 记录** ⇒ **D3 坐实** + 产出 **DRIFT-HOTAPI-025**（代码在 `service/common` ⇒ 跨域） |
| **变异验证**（D2/U9） | ✅ **已闭合（第十轮，组件级）** | hot-api 主干 **10 个系统变异 ⇒ 10/10 全部被守门抓到**（基线 86/0）；并**查出并修补 1 个真实缺口**。✅ 第十一轮已扩展：hot-rpc **10/10**（基线 86）+ hot-common **4/4**（基线 14）⇒ hot 域合计 **25/25**；hot-rpc 侧查出并修补 **2 个真实守门缺口** |
| **Go 侧 `hot.api` 的 `Log.Mode` 等配置项** | ✅ 已对齐副本 | 真源 yaml 零改动（纪律 1 自检通过：跑完 `git status --porcelain -uno` 为空） |


---

## DRIFT-HOTAPI-025 🟡 部分闭合（499 已可见 · 断连取消已生效）· 残留时序差（补全批次独立池）
> **原标题（2026-10-24 原文，逐字保留）**：`## DRIFT-HOTAPI-025 —— 客户端断连时 RAG 侧**不产出 499**，且断连事件**完全不可见**` —— ⚠️ **2026-10-30 主线订正**：本条 `##` 标题按**本文件图例**是**当前状态面** ⇒ 原标题断言的两个现象（「不产出 499」「完全不可见」）**今日均已不成立**（499 已写出、断连事件已有日志 ⇒ 依据见下方「2026-10-30 真实性复核」段）⇒ ⇒ 标题改为**状态槽形态**（与本文件 `## DRIFT-HOTAPI-014 ✅ 已闭合（nosniff 已实现）· …` 同款）⇒ ⇒ **原标题逐字保留在本段**，语义历史不丢；⚠️ **全仓无 `#drift-hotapi-025` 锚点入链** ⇒ 改标题**不破链**。
> 🔴 **2026-10-30 真实性复核（favorite+hot lane）**：原判 **「RAG 侧不产出 499 + 断连事件完全不可见」· 🔁 跨域登记（hot 域不可修）** ⇒ **现行态 = 🟡 部分闭合（观测面 + 资源面已修 · 残留时序差）**（**改判**）。
> 依据：RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:462-486`（探针判定「客户端已断连」⇒ **先** `grpcCtx.cancel(null)` 取消在飞 gRPC、**再** `rec.writeTimeoutResponseQuietly(HTTP_CLIENT_CLOSED_REQUEST, httpReq)` ⇒ **真的写出 499**）+ `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:675-695`（`recordClientDisconnected` ⇒ `log.warn("[HTTP] {} - {} {} - {} - duration {}ms")` ⇒ **断连事件在日志里可见**）+ `service/common/src/main/java/com/rag/common/observability/GoStyleClientDisconnectProbe.java:234`（探针字节 `HTTP/1.1 103 Early Hints

`）· Go `service/common/observability/http.go:84-88`（`case <-ctx.Done()` ⇒ `errors.Is(ctx.Err(), context.Canceled)` ⇒ 499 ⇒ `http.Error`）· 核对日期 2026-10-30。
> ⇒ ⇒ **本条标题描述的两个现象（「不产出 499」「完全不可见」）均已不成立**（**原文保留，不改写**）⇒ 与本文件 `:181` 的「2026-10-26 第二十八轮订正」表（`D25 499` = ✅ **已闭合**）及 `docs/DRIFT/global/_README.md:90`（T-4 = 「观测面 + 资源面均已修」，实测 461ms）口径一致。
> ⚠️ **残留（🟡）**：hot-api 的 **100 次补全**跑在独立池 `hotHydrationExecutor` 上（`service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleHydrator.java:118-124`）⇒ **不继承** worker 线程上 attach 的 `io.grpc.Context`（`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:337-345`）⇒ 探针的 `grpcCtx.cancel(null)` **到不了补全批次** ⇒ 「断连后继续发下游调用」这一句在 hot-api 上**仍成立** ⇒ 属**时序差**，不是「499 不产出」。
> ⚠️ **本条原写「归属：代码在 `service/common` ⇒ 跨域」仍然成立**（修法在共享层，hot 域无写权）⇒ **本条不是 hot 域可自修的待办**。

> **发现轮次**：2026-10-24 第七轮（U3/D3 断连取证）
> **类别**：OBSERVABLE DIFFERENCE（**可观测性 + 资源浪费**；**无 wire 差异** —— 客户端已走，收不到 499）
> **严重度**：MEDIUM
> **归属**：代码在 `service/common` 的 `HttpMiddleware` ⇒ **跨域**

### 活体实测（第七轮，`tools/difftest/probe-hot-disconnect.py`）

做法：桩切 `article-slow`（article-rpc 睡 30s ⇒ 请求一定在飞）⇒ **裸 socket** 发
`GET /hot/v1/articles`，500ms 后用 `SO_LINGER={1,0}` 强制 **RST**
（`urllib` / JDK `HttpClient` 都做不到「发到一半就消失」）⇒ 读桩的**带时间戳事件日志**。

| | **Go 真源** | **RAG** |
|---|---|---|
| 客户端断连时刻 | +504 ms | +502 ms |
| 在飞 gRPC 被取消的时刻 | **+505 ~ 507 ms**（断连后 **1~3 ms**） | **+2155 ~ 2188 ms**（= **2s 调用 deadline**） |
| 断连后是否**继续发下游调用** | **否**（ctx 取消 ⇒ 立即停） | **是**（+2198 ms 又发了 `slow-09..16` 一批） |
| 499 是否产出 | ✅ **是**，且**进日志**：`[HTTP] 499 - GET /hot/v1/articles - 127.0.0.1:53546 - duration 503.3ms` | ❌ **未产出**（日志里连 499 / broken pipe / ClientAbort 字样都没有） |

### 根因：499 的**触发条件**与「**谁写 socket**」绑死

**Go**（`service/common/observability/http.go:84-88`）：
```go
case <-ctx.Done():
    statusCode := http.StatusServiceUnavailable
    if errors.Is(ctx.Err(), context.Canceled) {
        statusCode = httpClientClosedRequest      // 499
    }
    http.Error(w, httpTimeoutBody, statusCode)
```
⇒ 499 由 **ctx 被取消**驱动。HTTP 断连 ⇒ Go 的 `http.Server` 取消请求 ctx ⇒ **必定**产出 499。
（`http.Error` 写给已走的客户端会失败，但那不影响 499 **已被决定**这件事。）

**RAG**（`service/common/observability/HttpMiddleware.java:338-364`）：
```java
Throwable panic = panicHolder[0];              // ← worker 线程抛出的异常
if (panic != null) {
    boolean clientCancelled = isClientCancelledException(panic);   // IOException
    int statusCode = clientCancelled ? HTTP_CLIENT_CLOSED_REQUEST : 500;
    ...
    writeTimeoutResponse(httpResp, statusCode);
```
⇒ 499 由「**worker 线程**里抛了 IOException（写响应失败）」驱动。

⚠️ **但本组件的 worker 从不写 socket**：
`ObservabilityResponseWriter` 把响应体**写进内存缓冲**，真正写 socket 的是
**filter 线程**的 `rec.flushTo(httpResp)`（`HttpMiddleware.java:389`）⇒
写失败抛在 **filter** 上，**不在 worker 的 panic 通道里** ⇒
`panicHolder[0]` 永远是 `null` ⇒ **永远进不了 499 分支**。

⇒ **这不是配置问题，是结构性差异**：Go 的 499 由 **ctx** 驱动（与写在哪一层无关），
RAG 的 499 由 **worker 内的写失败**驱动（而本组件的写发生在 worker 之外）。

### 影响

1. **无 wire 差异** —— 客户端已断连，两侧都收不到 499；
2. **可观测性差异（实质）** —— Go 的日志里有 `[HTTP] 499`，**RAG 侧什么都没有**
   ⇒ **断连事件在 RAG 侧完全不可见**（499 只落在 span 属性里，且未接 trace 后端时连 span 也没有）；
3. **资源浪费** —— 断连后 RAG 继续把剩余条目的补全做完（还**继续发新的下游调用**）
   ⇒ 下游被无谓地压；Go 侧 ctx 一取消就停。

### 处置

🆕 **跨域登记，hot 域不可修**（代码在 `service/common` 的 `HttpMiddleware`）。
建议 common 域考虑把 499 的判定从「worker 内的写失败」改成
「**filter 写 socket 失败**（`flushTo` 抛 `IOException`）」或
「**容器侧的客户端断连信号**」—— ⚠️ 具体方案由 common 域定，
hot 域只提供**活体复现脚本**（`tools/difftest/probe-hot-disconnect.py`）。

---

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/probe-hot-disconnect.py` ⇒ **EXIT=0**（2026-10-30 03:43:31 · 12s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：⇒ 取消原因判定：**BY_DISCONNECT** ⏐ ⇒ 取消原因判定：**BY_DEADLINE** ⏐ ⇒ **两侧不等价，且方向与台账 D3 一致**： ⏐ · Go：客户端断连 ⇒ HTTP ctx 取消 ⇒ **在飞的 gRPC 立刻被取消** ⏐ · RAG：Servlet 断连**不**取消在飞的阻塞 gRPC ⇒ 只能等 2s 调用 deadline ⏐ ⇒ DRIFT-HOTAPI 的 **D3「客户端断连的取消不。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

## DRIFT-HOTAPI-026 —— MaxBytes 拒绝后 Go **重置连接（RST）**，RAG 干净关闭
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **「🆕 登记为已记录差异，不修」** ⇒ **现行态 = 🟡 差异仍在 · 已登记不修（容器/传输层 · 非缺陷）**（未改判）。
> 依据：RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxBytesFilter.java:108-112`（`getContentLengthLong() > maxBytes` ⇒ `setStatus(413)`，**不** `sendError`、**不**写 body ⇒ 与 Go 的裸 `w.WriteHeader(413)` 同形）+ `service/hot/hot-api/src/main/resources/application.yml:144`（`max-bytes: 1048576` = Go tag 默认）· Go `rest/handler/maxbyteshandler.go:19-22`（`r.ContentLength > n` ⇒ 裸 `w.WriteHeader(413)`）+ `rest/config.go:53`（`MaxBytes int64 json:",default=1048576"`）· 核对日期 2026-10-30。
> ⇒ ⇒ **闸门行为两侧仍等价**（413 + 空 body + 无 Content-Type）⇒ 残留**只在连接层**（Go RST / RAG 干净关闭）⇒ 按原判**保留登记、不修** ⇒ **不升级为 🔴**（一条差异 ≠ 一个缺陷）。

> **发现轮次**：2026-10-24 第八轮（U5 保护层四闸活体取证）
> **类别**：OBSERVABLE DIFFERENCE（**连接层**；响应体/状态码/响应头**逐字节相同**）
> **严重度**：LOW
> **归属**：容器/传输层行为（Go `net/http` vs Tomcat），**非 hot 域代码** ⇒ 跨域登记

### 活体实测（第八轮，`tools/difftest/probe-hot-protection.py --probe maxbytes`）

两侧都跑**默认阈值** 1048576（Go `rest/config.go:53` 的 tag 默认；RAG `application.yml` 的 `rag.protection.max-bytes`）。

| | **Go 真源** | **RAG** |
|---|---|---|
| `Content-Length: 1048577`（超限） | **413**，body **0B**，**无** `Content-Type` | **413**，body **0B**，**无** `Content-Type` |
| 超限后连接状态 | 🔴 **RST（ConnectionResetError）** | ✅ 干净关闭（`closed`） |
| `Content-Length: 1048576`（**恰好等于**阈值） | **200** + 业务信封（1112B） | **200** + 业务信封（1112B，**逐字节相同**） |

⇒ **闸门行为本身两侧等价**：严格大于才拒（Go `maxbyteshandler.go:19` 的 `r.ContentLength > n`），
拒绝时都是**裸 413 + 空 body + 无 Content-Type**（Go 是裸 `w.WriteHeader(413)`，RAG 是 `setStatus(413)` 且**刻意不** `sendError`）。

### 唯一差异：Go 在 413 之后重置连接

Go 的 `http.Server` 在 handler 返回后要处理「请求体没被读完」：
`reqBody.Close()` 最多吞 256KB，剩下未读 ⇒ **带未读数据关连接 ⇒ 内核发 RST**。
⇒ 客户端（哪怕已经收到 413）通常会看到 `ConnectionResetError`。

RAG 侧 Tomcat 的 `maxSwallowSize`（默认 2MB）足够吞掉这 1MB ⇒ 不重置。

⚠️ **实测踩到的坑**：探针第一版把「连接重置」当成了「整个请求失败」⇒ **误判成「Go 侧没实现 MaxBytes」**。
修正后（**先看有没有拿到完整响应，再看连接状态**）才是上表的结论。
⇒ 这条本身也是 Pitfall **P20** 的同源教训：**别把传输层的收尾差异当成业务层的失败**。

### 处置

🆕 **登记为已记录差异，不修**。理由：
1. 响应（状态码 / body / 关键响应头）**逐字节相同** ⇒ wire 层无差异；
2. 差异只在「服务端是否复用这条连接」——Go 侧因为**拒绝了一个未读完的大 body**而必须断，
   RAG 侧 Tomcat 愿意吞掉；**这是容器策略差异，不是业务语义差异**；
3. 真要「修」，得让 RAG 也对未读完的大 body 主动断连接 ⇒ 那是**新增行为**（Go 侧也只在
   「body 超限」这一条路径上断，其它路径不复刻）⇒ 反而制造新的不一致。

---

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/probe-hot-protection.py` ⇒ **EXIT=1**（2026-10-30 03:43:11 · 20s）—— 🔴 **探针判定为「不一致」**；覆盖断言：[FAIL] maxconns       rag  12 并发在途 ⇒ 裸 503（空 body）0 个 / 放行 12 个 / 传输错误 0 个，耗时 3.0s（期望：至少 1 个裸 503） ⏐ [FAIL] breaker        go   3 波 × 12 并发 ⇒ 熔断裸 503（空 body）共 0 个；超时 503（body=Request Timeout）共 0 个（期望：> 0 个空 body 的 503 ——。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

## DRIFT-HOTAPI-027 —— MaxConns 在 RAG 侧**默认阈值下结构性不可达**（且 Go 是 per-route、RAG 是 global）
> 🔴 **2026-10-30 真实性复核（favorite+hot lane）**：原判 **「🔁 跨域登记，hot 域不可修（建议 common 域与运维一起决定）」** ⇒ **现行态 = 🏷 已签字为有意偏离（`SD-2026-10-25-01`，2026-10-25 用户拍板）· 不是待办**（**改判**）。
> 依据：RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:99-106`（**全进程一个** `Semaphore` ⇒ 全局作用域）+ `service/hot/hot-api/src/main/resources/application.yml:143`（`max-conns: 10000`）· Go `rest/engine.go:145-147`（`chn.Append(handler.MaxConnsHandler(ng.conf.MaxConns))` ⇒ **每条路由各建一个** ⇒ per-route）+ `rest/config.go:52`（`MaxConns int json:",default=10000"`）· 核对日期 2026-10-30。
> ⇒ ⇒ **被描述的行为仍然存在**（RAG 全局 / Go per-route）⇒ 但它**已不是待办** ⇒ 已由 `docs/Intentional_Deviations/global/common-tails.md` 的 **`SD-2026-10-25-01`**（✅ 仍成立，**明写**「对应的 DRIFT 条目 = T-2（follow 域 `DRIFT-FOLLOW-008`）· **T-5（hot 域 `D27`）** ⇒ 两条同一件事」）签字 ⇒ **决定 = 保留 RAG 的全局闸门，不改成 per-route** ⇒ 与本文件 `:182` 的「2026-10-26 第二十八轮订正」表（`D27 MaxConns 作用域` = 🏷 **已签字为有意偏离（不是待办）**）口径一致。
> ⚠️ **上面正文里「建议 common 域与运维一起决定」这一句已过期**（**原文保留，不改写**）⇒ 决策已在 2026-10-25 做出。

> **发现轮次**：2026-10-24 第八轮（U5 保护层四闸活体取证）
> **类别**：SEMANTIC MISMATCH（**闸门可达性** + **隔离单位**）
> **严重度**：MEDIUM
> **归属**：RAG 侧实现（`service/common/protection/GoStyleMaxConnsFilter`）+ 容器默认参数 ⇒ **跨域**

### 活体实测（第八轮）

**① 闸门机制本身：两侧等价 ✅**

把两侧阈值**降到同一个值 8**（Go：`tools/difftest/conf/go/hot-api-lowmaxconns.yaml` 的 `MaxConns: 8`；
RAG：`--rag.protection.max-conns=8`），16 个请求**完全重叠**（两侧日志都显示同一时刻起跑）：

| | **Go 真源** | **RAG** |
|---|---|---|
| 裸 503（空 body）个数 | **8** | **8** |
| 放行 | 5 个 200 + 3 个超时 503（合计 8 在途） | 8 个（全部超时 503） |
| 耗时 | 3.0s | 3.0s |

⇒ 拒绝数**恰好等于阈值**，形态**裸 503 + 空 body**（Go `maxconnshandler.go:34` 裸 `w.WriteHeader(503)`）
⇒ **闸门机制两侧等价**。

**② 但在默认阈值（10000）下，RAG 侧永远撞不到**

| | **Go 真源** | **RAG** |
|---|---|---|
| 阈值 | 10000（`rest/config.go:52` tag 默认） | 10000（`rag.protection.max-conns`） |
| 真正卡住并发的东西 | 无（`http.Server` 不限并发） | 🔴 **Tomcat `max-threads` 默认 200** ⇒ 同时在途**最多 200** |
| 次一道坎 | — | 🔴 **Tomcat `max-connections` 默认 8192** ⇒ 第 8193 条连接在 **TCP 层**被拒（**连 HTTP 状态码都没有**） |
| 阈值 10000 可达？ | ✅ 需 10001 个同时在途 | ❌ **不可达** |

⇒ **RAG 侧这层闸门在默认部署下是死代码**：它永远等不到 10000 个在途请求。
（Tomcat 的排队行为是「变慢」，Go 的拒绝行为是「立刻 503」——
**这是两个不同的可观察行为**：客户端的退避/重试策略完全不同。）

**③ 隔离单位也不一样**

- Go：`engine.go:145-147` 在**每条路由的 chain** 里各建一个 `MaxConnsHandler`
  ⇒ **每路由一个 10000 的信号量**，不同路由互不影响；
- RAG：`GoStyleMaxConnsFilter` 是**全局 Filter**，只有**一个**信号量 ⇒ 所有路由共用。

⇒ hot-api 只有 1 条路由 ⇒ 本组件**不可观察**；但**多路由组件**（article / message / like …）上可观察。

### 处置

🆕 **跨域登记，hot 域不可修**（代码在 `service/common`）。
- `GoStyleMaxConnsFilter` 的 javadoc §2 **已经写明** Tomcat 三参数与 Go 的差异
  ⇒ 本条是把那段**已知的静态结论升级成活体证据**（阈值 8 时两侧拒绝数完全一致 ⇒ 机制没问题）。
- 建议 common 域与**运维**一起决定：是否把 `server.tomcat.max-threads` 调到万级，
  以便 MaxConns 真正成为一道**可触发的**闸门；否则应把这层的默认阈值调到**可达区间**
  （否则它只是「口径对齐」的摆设）。
- ⚠️ **本轮未改任何一侧的默认值**：改阈值 = 改部署形态，属于用户拍板范围。

---

## U5-OPEN-1（开放项）—— 熔断实验中两侧 **5xx 累积速率**不同

> **发现轮次**：2026-10-24 第八轮
> **状态**：✅ **已闭合（第十一轮，2026-10-24）** —— 根因 = **候选 ②（剩余预算的取样时刻）**，
> 已修（**DRIFT-HOTAPI-029**）；修后两侧**逐波完全一致**。以下是原始记录（保留供追溯）。
> **⇒ 隔离实验与判决过程 ⇒ [DRIFT-HOTAPI-029](#drift-hotapi-029-✅-已修--semantic-mismatch--medium)**

### 现象

在「桩切 `article-slow-short` ⇒ 补全拖过 3s HTTP 超时」的场景下打 HTTP 级熔断
（`breakerhandler.go:22-29`，**503 裸写在 `:27`** ⇒ body 为空；判别器 = **503 且 body 为空** vs **503 且 body = `Request Timeout\n`**）：

| 轮次 | Go 真源 | RAG |
|---|---|---|
| 第 1 轮（12 并发 × 3 波） | 第 1 波：**9** 个超时 503；第 2 波：**2** 个熔断裸 503；第 3 波：0 | 第 1 波：9 个超时 503；第 2 波：**2** 个熔断裸 503；第 3 波：0 |
| 第 2 轮（12 并发 × 3 波，**同一天隔几分钟重跑**） | 第 1 波：**4** 个超时 503 + **8** 个 200；第 2/3 波：**0** 个熔断裸 503 | 第 1 波：**12** 个超时 503；第 3 波：**3** 个熔断裸 503 |

⇒ **两侧的 HTTP 熔断都能打开、都产出「裸 503 + 空 body」**（闸门机制 ✅ 等价）。
⇒ 但**打开的时机**不稳定：Go 侧第 2 轮几乎没打开（0 个），RAG 侧第 1 轮 12 个请求**全部**超时。

### 候选原因（两条，都还没排除）

1. **客户端熔断（U4 那条）的开启时机不同** ⇒ 一旦打开，补全就变成「快速失败 + best-effort skip」
   ⇒ 请求以 **200** 结束 ⇒ **5xx 不再累积** ⇒ HTTP 熔断就永远打不开。
   ⇒ Go 侧第 2 轮第 1 波出现 **8 个 200**，正是这个形态。
2. **两侧补全的「失败快慢」不同**：Go 的 `buildHotArticleItem`（`getHotArticlesLogic.go:132`）
   用 `l.ctx`（**不传显式 deadline**）⇒ deadline = min(zrpc 的 2s, ctx 剩余)；
   RAG 的 `HotArticleHydrator` 显式传 `remainingBudgetMs` ⇒ min(2s, 剩余)。
   ⇒ **表达式等价**，但「剩余」的**取样时刻**可能不同（Go 在 ctx 上、RAG 在调用点各取一次）。

⚠️ **已排除的一个候选**（记录下来，免得后人重查）：
「两侧单次 gRPC deadline 不同（Go 987ms vs RAG 2s）」——**不成立**。
987ms 那些是**第二批**（8 并发窗口用满后，第 9、10 条的 ctx 剩余只剩 ~1s）；
Go 侧 zrpc 的超时是 `zrpc/client.go:70-71` + `TimeoutInterceptor` ⇒ **min(2s, ctx 剩余)**，与 RAG 一致。

### 为什么不定性

两个原因的**可观察后果相同**（都是「5xx 累积速率不同 ⇒ HTTP 熔断打开的时机不同」），
但**修法完全不同**：① 要对齐客户端熔断的开启时机；② 要对齐「剩余预算」的取样时刻。
⇒ **不隔离就动手 = 盲修**（这正是本仓 §3.3「先改/删错误断言，再改实现」的同类陷阱）。

### 隔离实验（下一步要做的事）

把**客户端熔断**从场景里摘掉：让补全的失败**不计入客户端熔断**即可
（Go 侧 `codes.Acceptable` 把 `DeadlineExceeded` 判为**不可接受**⇒ 计入；
可改用桩「**慢但成功**」或「**立即返回非 DeadlineExceeded 的错误**」来避开）。
⇒ 届时两侧的 5xx 累积速率若一致 ⇒ 原因 ①；若仍不一致 ⇒ 原因 ②。

⇒ **本条不阻塞 U5 的结论**：四闸里三闸已活体坐实，CpuShedding 是平台不可达。

---

## U5 结论（第八轮，四闸逐条）

| 闸 | Go 出处 | 活体结果 | 结论 |
|---|---|---|---|
| **MaxBytes** | `rest/handler/maxbyteshandler.go:10-25` | 两侧**默认阈值**下：超限 → **413 + 空 body + 无 Content-Type**（逐字节相同）；**恰好等于阈值** → 两侧都放行（200 + 同一份 1112B 信封） | ✅ **EQUIVALENT**（连接层有一处已记录差异 ⇒ **DRIFT-HOTAPI-026**） |
| **MaxConns** | `rest/handler/maxconnshandler.go:12-36` | 两侧**同阈值 8** + 16 并发 → **各 8 个裸 503（空 body）**，拒绝数恰等于阈值 | ✅ 机制 **EQUIVALENT**；⚠️ **默认阈值 10000 下 RAG 侧结构性不可达**（Tomcat 200 线程 / 8192 连接）⇒ **DRIFT-HOTAPI-027** |
| **CpuShedding** | `rest/handler/sheddinghandler.go:20-52` + `core/load/adaptiveshedder.go` | 🔴 **darwin 上 Go 侧结构性不可达**：`core/stat/internal/cpu_other.go` 带 `//go:build !linux` ⇒ `RefreshCpu()` 恒 0 ⇒ `stat.CpuUsage()` 恒 0 ⇒ `systemOverloaded()` 恒 false ⇒ `shouldDrop()` 永假。**活体佐证**：Go 自己的 stat 行打 `CPU: 0m` | 🟡 **无法双侧活体对拍**（平台事实，非实现缺陷）⇒ 证据降级为：① 源码逐条对照；② **go-zero 上游自带测试**（`core/load` / `core/breaker`，从 module cache 复制到 /tmp 跑，**真源仓库零改动**）全绿；③ RAG 侧 `GoStyleAdaptiveShedderTest` 10 例覆盖**同一组场景**（含 `overloadedAndHighInFlightSheds`） |
| **Breaker** | `rest/handler/breakerhandler.go:18-42`（**503 裸写在 `:27`**；判别器 `cw.Code < 500` 在 `:33`） | 两侧都能**真的打开**，拒绝形态 = **裸 503 + 空 body**（与超时的「503 + `Request Timeout\n`」可区分） | ✅ 机制 **EQUIVALENT**；⚠️ 当时「打开时机不稳定」⇒ **U5-OPEN-1** ⇒ **第十一轮已定性并修复**（根因 = 补全预算取样时刻 ⇒ **DRIFT-HOTAPI-029**）⇒ 修后两侧**逐波完全一致** |

> **证据强度**：MaxBytes / MaxConns / Breaker 三闸 = **双侧活体**（最强档）；
> CpuShedding = **静态 + 上游参考测试**（本机不可能更强）。

### 📌 订正注记（2026-10-27 · G3 · 21:0x）：本域 **3 处引用行号越界**已实修（**结论一个字未改**）

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上文那些**机制结论**全部**原样有效** ⇒ 本轮只改**引用指针**。

| 位置 | 原引用 | 事实 | 实修 |
|---|---|---|---|
| 上文 `DRIFT-HOTAPI-013` 段 | `core/mapping/jsonunmarshaler.go:45-53` | go-zero v1.10.2 该文件**只有 52 行** ⇒ `53` 越界 | ⇒ 改为 **`core/mapping/jsonunmarshaler.go:45-52`**（`unmarshalJsonReader` 全函数；`jsonx.UnmarshalFromReader` 在 `:47`，按 json tag 填字段在 `:51`）|
| 本节「现象」段 | `breakerhandler.go:19-44` | 同上 ⇒ 该文件**只有 42 行** ⇒ `44` 越界 | ⇒ 改为 **`breakerhandler.go:22-29`**（`if err != nil` 分支；**503 裸写在 `:27`**）|
| 本节 Breaker 行 | `rest/handler/breakerhandler.go:19-44` | 同上 ⇒ `44` 越界 | ⇒ 改为 **`rest/handler/breakerhandler.go:18-42`**（`BreakerHandler` 全函数；**503 裸写在 `:27`**；判别器 `cw.Code < 500` 在 `:33`，`promise.Accept()` 在 `:34`，`promise.Reject(...)` 在 `:36`，`next.ServeHTTP` 在 `:39`）|

⇒ **G2 通报来源**：`docs/DRIFT/global/_README.md:569` 已把这条登记为 **hot 域漂移**（「🔴 行号越界 —— go-zero `rest/handler/breakerhandler.go` 只有 42 行 ⇒ `44` 越界」）⇒ ⇒ **owner = G3** ⇒ 本域自修。
⇒ **顺带记一条方法论**：**真源的 api 入口文件名【逐域不同】，不能按 go-zero 惯例推** —— 实测 `service/article/api/article.go` · `service/favorite/api/favorite.go`（**无 center**）· 但 `service/hot/api/hotcenter.go` · `service/comment/api/commentservice.go`（**有 center/service**）⇒ ⇒ **按惯例猜文件名 ⇒ 必错** ⇒ ⇒ **必须逐个 `ls` 真源**。

---

## U9 / D2 —— 全组件**系统变异**（第十轮，2026-10-24）：84 条测试是不是假绿？

> **目标**：定向变异只证明「**我刚修的那几处**有守门」；
> 它**不能**证明「这个组件的 84 条测试**整体**有效」。
> ⇒ 本轮对 hot-api 的**主干实现**逐点注入语义错误，要求**每一点都至少有一条测试变红**，
> 并记下**是哪条测试抓到的**（「抓到」= 那条测试真的在守这条语义；「没抓到」= 守门有缺口）。
> **工具**：`tools/difftest/mutate-hot.py`（可重复执行）。

### 结果总表

| 变异 | 注入的错误 | Go 出处 | 结果 | 抓到它的测试 |
|---|---|---|---|---|
| M01 | 分页上限放宽 1（`> 50` ⇒ `> 51`） | `getHotArticlesLogic.go:185-187` | ✅ 抓到（**修补后**） | `HotApiMvcPipelineTest#pageSizeCapBoundaryIsApplied`（**本轮新增**） |
| M02 | 响应 `total` 多算 1 | `getHotArticlesLogic.go:78` | ✅ 抓到（14 条） | `HotApiMvcPipelineTest#pageSizeCapIsApplied` 等 |
| M03 | 分页起点少减一个 pageSize | `getHotArticlesLogic.go:197` | ✅ 抓到 | `HotApiGoWireOrderTest#fieldOrderMatchesGoDeclarationOrder` 等 |
| M04 | rank 从 0 开始（不稠密） | `getHotArticlesLogic.go:120` | ✅ 抓到（5 条） | `HotRankPaginatorTest#assignsDenseRanks` 等 |
| M05 | 补全失败的空位不再跳过 | `getHotArticlesLogic.go:116-118` | ✅ 抓到（9 条） | `HotArticleHydratorTest#skipsNilAndEmptyArticleId` 等 |
| M06 | 空页判定放宽 | `getHotArticlesLogic.go:80` | ✅ 抓到（7 条） | `HotRankPaginatorTest#totalIsFilteredWindowSize` 等 |
| M07 | 十进制解析改成 radix 8 | `getHotArticlesLogic.go:172-176` | ✅ 抓到（3 条） | `GoFormValuesTest#leadingZeroIsDecimalNotOctal`（**这条本来就存在**） |
| M08 | gRPC deadline 去掉「请求剩余预算」约束 | `zrpc/config.go:31` + ctx 剩余 | ✅ 抓到（2 条） | `ArticleRpcClientTest#mustUseRequestBudgetWhenShorterThanTwoSeconds` 等 |
| M09 | 响应包装不再识别「已成型的信封」 | `hotcenter.go:45-51` | ✅ 抓到（7 条） | `HotApiGoParseParityTest#*` 等 |
| M10 | 补全时给下游多要 1s 预算 | `getHotArticlesLogic.go:132` | ✅ 抓到 | `HotArticleHydratorTest#forwardsRequestBudgetToEveryHydration` |

⇒ **10 / 10 有效变异点全部被守门抓到**；基线 **85 tests / 0 failures**。

### ⚠️ 第一轮有 3 个「没变红」——**复核后确认全是「变异本身无效」**，不是守门缺口

按 §3.3「**变异没红的第一反应是「我的变异脚本错了」**」（Pitfall **P15**），逐个复核：

| 原变异 | 为什么是 **no-op** | 处置 |
|---|---|---|
| `Integer.parseInt(v, 10)` ⇒ `Integer.parseInt(v)` | 🔴 **Java 的 `parseInt(String)` 本来就是十进制**（JDK 文档：*signed decimal integer*）⇒ 两种写法**语义相同**。**已用 jshell 实测**：`parseInt("010")` = `parseInt("010",10)` = 10 | 改成 radix **8**（⇒ `"010"` = 8）⇒ **立刻被 `GoFormValuesTest#leadingZeroIsDecimalNotOctal` 抓到** |
| `pageSize > MAX` ⇒ `pageSize >= MAX` | `pageSize == 50` 时两个分支**都**赋 50 ⇒ 无差别 | 改成 `> MAX + 1`（⇒ `page_size=51` 不被截）⇒ **见下面的真缺口** |
| `start >= total` ⇒ `start > total` | `start == total` 时：原版返回 `[0,0]`，变异版返回 `[total,total]` ⇒ **`slice` 层两者都是空列表** ⇒ **外部不可观察** | 该点**结构上不可观察**（`paginate` 的内部契约差异没有任何观测点）⇒ 换成可观察的 M02（`total` 多算 1）⇒ **14 条测试抓到** |

⇒ **教训（值得写进 P15）**：**「变异没红」有三种成因，排查顺序必须是**：
① 变异本身是 no-op（**最常见**，且最容易被误判成「守门缺口」）；
② 变异改法破坏了编译；③ 守门真的不敏感。
⇒ **先证伪「变异有效」再谈「守门缺口」**，否则会写出不存在的缺陷。

### 🔴 本轮查出并修补的**真实守门缺口**（M01）

**缺口**：**没有任何测试钉住 `page_size=51`**（恰好越过上限一格）。

**为什么没钉住**：既有的 `pageSizeCapIsApplied` 请求的是 `page_size=999` ——
把上限从「`> 50`」放宽到「`> 51`」它**照样**被截到 50 ⇒ 抓不到那个变异。
⇒ **能区分「`> 50`」与「`> 51`」的输入只有 `51` 自己**，而它此前没被测过。

**Go 语义**（`getHotArticlesLogic.go:185-187`）：`50` 不截断、`51` 截成 50、`999` 截成 50。

**修补**：新增 `HotApiMvcPipelineTest#pageSizeCapBoundaryIsApplied`
（同时钉 `50` 与 `51` 两个值）⇒ **变异验证**：M01 重新注入后**恰好这一条变红** ✅
⇒ 守门缺口**已闭合**。

### 结论

1. **hot-api 的 85 条测试在「已覆盖的语义」上不是假绿** —— 10 个主干语义点，
   **每一个**都有测试真的在守，且能报出**是哪条测试**在守。
2. **系统性变异确实能挖出定向变异挖不到的东西**：M01 的缺口（`page_size=51`）
   在前面五轮的定向变异里**一次都没出现过** —— 因为定向变异只盯着「**刚修过的**」那几行，
   而系统性变异会扫到**从没被测过的边界**。
3. ⚠️ **本轮结论的边界**：变异覆盖的是 hot-api 的**主干实现**（分页/信封/补全/参数/deadline），
   **不含** hot-rpc（84 条）与 hot-common（14 条）⇒ 那两个组件的**系统性变异**仍待做。
   🟥 **2026-10-24 第十一轮订正：这条边界已到期** —— hot-rpc **10 个**（基线 86）+ hot-common **4 个**（基线 14）
   的系统性变异**已做完且 4/4 + 10/10 全被守门抓到** ⇒ **hot 域合计 25/25**；
   hot-rpc 侧**查出并修补 2 处真实守门缺口**（合并的 >100 截断层、桶冲突衰减概率的 uint32 语义）
   ⇒ **这正是「边界到期」的价值**：第十轮在 hot-api 里挖出的 M01 缺口（`page_size=51`），
   第十一轮在 hot-rpc 里挖出了**同族**的缺口（只断言条数抓不到排序方向）。

---

## DRIFT-HOTAPI-028 ✅ **已修**（阈值 32→64）· 残余差异转 **SD-HOT-API-010** —— RAG 侧补全的**跨请求并发窗口被共享池腰斩成一半**（Go 是 64，RAG 只有 32）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修（阈值 32→64）** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/config/HotHydrationExecutorConfiguration.java:65`（`POOL_SIZE = 64`）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/config/HotHydrationExecutorConfiguration.java:72`（`QUEUE_CAPACITY = 64`）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleHydrator.java:69`（`HYDRATION_CONCURRENCY = 8` = 每请求窗口）· Go `service/hot/api/internal/logic/hot/getHotArticlesLogic.go:93`（`sem := make(chan struct{}, hydrationConcurrency)` ⇒ **每请求各建一个** ⇒ N×8 在途、**无全局封顶**）· 核对日期 2026-10-30。
> ⇒ ⇒ **阈值 64 确实在位**（32 已不存在）⇒ 残余差异仍是「RAG 有跨请求总上限、Go 没有」⇒ 已由 `docs/Intentional_Deviations/hot/api.md` 的 `SD-HOT-API-010`（✅ 仍成立）签字 ⇒ **维持**。

> **发现轮次**：2026-10-24 第十轮（U10 延迟分布对拍）
> **类别**：OBSERVABLE DIFFERENCE（**延迟分布**；**结果完全一致** —— 两侧成功率 100%，响应体逐字节同）
> **严重度**：MEDIUM
> **归属**：RAG 侧 `service/hot/hot-api/.../config/HotHydrationExecutorConfiguration.java` ⇒ **本域可修**
> **处置**：✅ **已修**（用户 2026-10-24 拍板「方案 A：池 64 + 登记为签字偏离」）⇒
> 阈值 `POOL_SIZE` **32 → 64**；残余差异（「RAG 有跨请求总上限、Go 没有」）转 **SD-HOT-API-010** 签字留痕。

### 活体实测（第十轮，`tools/difftest/probe-hot-latency.py`）

场景：桩切 `many-100`（**100 条**热榜 = Go 硬编码的 `TopK=100`；article-rpc 每次**固定睡 20ms** 后**正常返回**）
⇒ **8 并发 × 20 轮 = 160 请求/侧**。
判别器 = 桩的 **ENTER/EXIT 带时间戳事件日志**算出的「下游同时在途数」**峰值**。

| | **Go 真源** | **RAG** |
|---|---|---|
| 成功率 | **160/160 全 200**，零传输错误 | **160/160 全 200**，零传输错误 |
| p50 | **342.5 ms** | **667.0 ms**（= **1.95×**） |
| p90 / p95 | 354.5 / 369.4 ms | 691.6 / 700.3 ms |
| **p99** | **405.9 ms** | **715.3 ms**（= **1.76×**） |
| **下游同时在途峰值** | **64** ✅（= 8 请求 × 每请求 8 窗口，**完整保留**） | 🔴 **32**（= **共享池容量** ⇒ **腰斩**） |

（三轮独立复跑，p99 比值落在 **1.52× ~ 1.91×**，峰值 **恒为 64 vs 32** ⇒ 结论稳定。）

### 根因：Go 的并发窗口是**每请求**的，RAG 的池是**全局**的

- **Go**（`getHotArticlesLogic.go:91-112`）：`sem := make(chan struct{}, hydrationConcurrency)` ——
  **每个请求各建一个容量 8 的信号量** ⇒ N 个并发请求 ⇒ **N × 8** 个同时在途的下游调用，
  **没有全局上限**（goroutine 本来就廉价）。
- **RAG**（`HotHydrationExecutorConfiguration`）：
  - 每请求窗口 = `Semaphore(8)`（`HotArticleHydrator`）✅ **与 Go 一致**；
  - **但**所有请求的补全任务都投到**同一个 32 线程的共享池**
    （`POOL_SIZE = 32`，注释写的是「4 个并发请求 × 每个请求 8 个在途」）
    ⇒ **8 并发时需要 64 个在途任务，池只有 32 ⇒ 32 个在排队**
    ⇒ 每个请求实际只拿到 **32 / 8 = 4** 个在途名额 ⇒ **窗口从 8 掉到 4**
    ⇒ 补全耗时约翻倍 ⇒ **p50 1.95× / p99 1.76×**。

⚠️ **这是「容量」问题不是「语义」问题**：单请求（并发 1）时两侧窗口都是 8 ⇒ **完全一致**；
只有**并发 ≥ 5**（32/8+1）时 RAG 侧开始排队 ⇒ 差异随并发放大。

### ⚠️ 同时**推翻了台账的一条旧结论**

旧结论（本文件 §15 / §Concurrency semantics）：
> 「常态窗口都是 8；**池满时 Java 会在请求线程上串行跑补全**，Go 是纯排队」

⇒ 🔴 **「Java 会在请求线程上串行跑补全」被实测推翻**：
峰值 **32 = 池容量**，**没有超过 32** ⇒ `CallerRunsPolicy` **从未触发**
⇒ RAG 侧的背压形态是「**纯排队**」，**与 Go 同类**（只是排的队不同：Go 排 goroutine，RAG 排 `ThreadPoolTaskExecutor` 的 64 容量队列）。
⇒ **两侧的背压语义一致**；剩下的只有**排队深度**（并发窗口）这一处差异。

### 处置（**已修** —— 2026-10-24 用户拍板「方案 A：池 64 + 登记为签字偏离」）

用户 2026-10-24 明确选择**保守路线**：**保留**这层跨请求保护（它是「RAG 比 Go 更稳」的一侧），
把阈值提到能覆盖已验证的并发档位，并把「RAG 有上限、Go 没有」**签字登记**。

| 方案 | 内容 | 结论 |
|---|---|---|
| **A（已采用）** | `POOL_SIZE` **32 → 64**（= 8 并发 × 每请求 8 窗口）+ 登记 **SD-HOT-API-010** | ✅ **已实施** |
| B（未采用，留档） | 换成「每任务一虚拟线程」（Java 21 JEP 444）⇒ 彻底取消全局上限、逐条等价于 goroutine | ⚠️ **未采用**：会把下游并发**完全放开**，与「保护下游」的初衷相反（且 grpc-java blocking stub 在虚拟线程里的 **pinning** 需另行实测） |
| — | 平台线程无界池 | 🚫 **不可行**：会 OOM / 上下文切换风暴 |

**修复后的活体复测**（同一轮、同一环境，两侧对比才有意义）：

| | **Go 真源** | **RAG（修后）** | **RAG（修前）** |
|---|---|---|---|
| 下游同时在途峰值 | **64** | **64** ✅（修前 **32**） | 32 |
| p50 | 513.4 ms | **491.9 ms**（0.96×） | 667.0 ms（1.95×） |
| p99 | 1038.1 ms | **1118.7 ms（1.08×）** | 715.3 ms（1.76×，但那是**另一轮**的绝对值） |
| 成功率 | 160/160 全 200 | 160/160 全 200 ✅ | 160/160 全 200 |

⇒ **峰值 32 → 64 = 与 Go 一致**；**p99 比值 1.76× → 1.08×**（p50 0.96×）⇒ 延迟分布基本追平。
⚠️ **绝对值不可跨轮比较**（机器负载在变：这一轮 Go 侧的 p50 也从 342ms 涨到 513ms）⇒ **只有同轮比值有意义**。

**守门测试**：`HotArticleHydratorTest#concurrentRequestsEachKeepTheirOwnWindow`
—— 断言「**8 个并发请求**的合计在途 = 8×8 = **64**」。
⚠️ **为什么单请求的旧测试抓不到**：旧用例 `neverExceedsEightConcurrentHydrations` 只跑**一个**请求
⇒ 单请求窗口是 8 ⇒ **池是 32 还是 64 都通过** ⇒ 必须**多请求同时跑**，池才会成为瓶颈。
**变异验证**：把 `POOL_SIZE` 改回 32 ⇒ 该用例**恰好以 32 变红** ✅ ⇒ 守门非假绿。

🔴 **2026-10-25 修：断言的「测量方式」从时序量改成结构性事实。**
原实现靠 `Thread.sleep(30)` 让 64 个任务**时间上重叠** ⇒ 峰值是**时序量** ⇒
CPU 一忙（正是全量 `mvn -T1C test` 并行跑 29 个模块时的状态）池线程启动就慢，
64 个 sleep 不再完全重叠 ⇒ 峰值掉到 36/29/19 ⇒ **断言随机红**
（实测：制造 CPU 负载后必红 `expected 64 but was 36`；空闲时连跑 3 次全绿）。
⇒ **不是实现回归，是断言形式与机制冲突**（与 **P-HOTAPI-23** 同源）。
**修法**：mock 里的 `Thread.sleep(30)` 换成 `CyclicBarrier(64)` ——
每个任务进入后先到达屏障 ⇒ 必须**全部 64 个都被调度到**才放行：
  · 池 = 64 ⇒ 全部到达 ⇒ 放行 ⇒ 峰值**必然** = 64（**与机器负载无关**）
  · 池 = 32 ⇒ 只有 32 个能到达 ⇒ 屏障凑不齐 ⇒ 超时 ⇒ 变红（实测仍报 `expected 64 but was 32` ✅）
⇒ **守门强度不减**（签字文件 SD-HOT-API-010 要求的「池被改小立刻变红」仍成立），
但**不再对机器负载敏感**。变异验证已重跑：池=32 ⇒ 变红 ✅；负载下 ⇒ 全绿 ✅。

**残余差异（已签字）**：并发 **> 8** 时 RAG 仍会跨请求排队、并发 **> 16** 时会触发 `CallerRunsPolicy`
⇒ 详见 **SD-HOT-API-010**（含残留风险清单）。

---

## U10 结论（第十轮）

| 问题 | 答案 |
|---|---|
| 极端并发下补全的**延迟分布**可比吗？ | 🔴 **不可比**：p99 **1.76×**（405.9ms vs 715.3ms）、p50 **1.95×**；根因已**归因到具体参数**（共享池 32 vs 需要的 64）⇒ **DRIFT-HOTAPI-028** |
| 结果（成功率 / 响应体）可比吗？ | ✅ **完全可比**：两侧 160/160 全 200、零错误；响应体逐字节同（`many-100` 模式下两侧 items 数与 total 一致） |
| 有请求被 `CallerRunsPolicy` 拖成串行吗？ | ✅ **没有** —— 下游在途峰值 **32 = 池容量**，**未超过** ⇒ `CallerRunsPolicy` **从未触发** ⇒ 背压形态 = **纯排队**（与 Go 同类）⇒ **推翻**台账旧结论「Java 会在请求线程上串行跑补全」 |
| Go 的并发窗口保住了吗？ | ✅ **64 = 8 请求 × 每请求 8**，**完整保留** ⇒ Go 侧无全局上限（每请求一个 `chan struct{}` 信号量） |

---

## hot-rpc D1（端到端双侧对拍）—— ✅ **第十一轮已闭合**（下方是当时的可行性分析，保留供追溯）

> **状态**：✅ **已闭合（第十一轮，2026-10-24）** —— 真 Kafka + **2 个真 Redis** + 真 gRPC，
> **10 组 top_k 全部逐字节一致**（sha256 相同），**U7 同步闭合**。
> ⇒ 完整过程与证据 ⇒ [`hot/rpc.md` 第十一轮](rpc.md#第十一轮d1-活体对拍闭合--u7-闭合--u9-系统变异2026-10-24)。
> ⇒ 编排脚本 ⇒ `tools/difftest/run-hot-rpc-difftest.sh`。
> ⚠️ 下列「三条硬约束」与「可行性分析」是**当时**的记录，**结论已全部落地**，保留以便后人复核取舍。

### 为什么它比 hot-api 的 D1 难（**已查清的三条硬约束**）

1. **Redis key 两侧都硬编码** ⇒ **不能共用一个 Redis**：
   - Go：`get_hot_articles_logic.go:16` `redisHotKey = "hot:articles"`；`hot_handler.go:390` `hot:articles:shadow`、`:208/:209` `hot:articles:p%d`
   - RAG：`HotRedisSnapshotStore.java:53-62` 同样是 `hot:articles` / `hot:articles:shadow` / `hot:articles:p%d`
   ⇒ 共用一个 Redis ⇒ **两侧互相覆盖对方的快照** ⇒ 比较结果**必然「相同」且**毫无意义**。
   ⇒ **必须起 2 个 Redis 实例**（或 1 个 + 两侧可配置前缀 —— 但那会引入「前缀可配」这个**Go 侧没有的行为** ⇒ 不可取）。
2. **Kafka 消费进度天然不对称** ⇒ 必须加「**两侧都消费完**」的栅栏：
   - 两侧都要消费**同一批事件**，但各自 `group-id` 不同 ⇒ 进度不同 ⇒ 快照内容不同 ⇒ 直接比会得到**假差异**。
   - ⇒ 需要：投 N 条事件 → **轮询两侧的 Redis 快照直到都包含预期条目** → 再比 gRPC 输出。
   - ⚠️ 这与 hot-api 的 D1 用「同构双桩」是**不同的问题**：桩是**无状态**的，Kafka 是**有状态**的 ⇒ 栅栏必须自己造。
3. **快照是周期合并的**（Go 30s 周期 / RAG `HotMergeScheduler`）⇒ **快照时机**本身就是一个与 hot-rpc 逻辑无关的不确定源
   ⇒ 必须在「两侧都完成合并」之后再比，否则比的是**合并时刻**而不是**合并结果**。

### 需要起的东西（清单，供下轮直接开工）

| 组件 | 数量 | 备注 |
|---|---|---|
| Kafka | 1 | `apache/kafka:3.7.2`（本地已有镜像） |
| Redis | **2** | `redis:7-alpine`（本地已有镜像）—— 见约束 ① |
| Go hot-rpc | 1 | 需一份 yaml 副本（`Endpoints` 直连 + 本地 Kafka/Redis + 独占端口），**真源零改动** |
| RAG hot-rpc | 1 | `KAFKA_BROKERS` / `REDIS_HOST` 指到上面两个容器 + `register-enabled=false` |
| 两侧 hot-api | 2 | 已有（`tools/difftest/run-hot.sh`），但要改指到各自的 hot-rpc |
| 事件生产者 | 1 | 固定内容、固定顺序的 N 条 `article-hot-events` |
| 栅栏 | 1 | 轮询两侧 Redis 直到都含预期条目 |

### 预期能查出的东西（按价值排序）

1. **HeavyKeeper 近似 top-k 的排序/截断是否逐条一致**（`domain/HeavyKeeper.java` vs Go `heavykeeper`）
2. **`GetHotArticles` 的 gRPC 响应体是否逐字节同**（字段序 / null vs [] / uint32）
3. **合并窗口内的分片 key 与大 key 的读取路径是否等价**（`HotRedisSnapshotStore` 的 4 段 Lua 已在 UNR-2 用真 Redis 逐条验过 ⇒ 这条大概率已闭合）
4. **消费并发度**（UNR-5 已答：Go 每分区一 goroutine vs RAG `concurrency=1` ⇒ 已登记 DRIFT-HOTRPC-009）

⇒ ⚠️ **第 3 条大概率已闭合**（真 Redis IT 已逐条跑过 4 段 Lua）⇒ 本轮 D1 的**增量价值主要在第 1、2 条**。
⇒ **建议**：若只想花**一半**的力气，可先做「**只比 gRPC 响应体**」的版本
（跳过 Kafka 栅栏，改为**直接往两侧 Redis 灌同一份快照**）—— ⚠️ 但那要求 RAG 侧的快照格式可注入，
而 `hot:articles` 是 zset ⇒ 需要知道它的 member 编码 ⇒ **仍要先读 Go 的写入侧**。

---

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/probe-hot-latency.py` ⇒ **EXIT=0**（2026-10-30 03:42:53 · 18s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> go  http://127.0.0.1:18903  并发 8 × 20 轮（mode=many-100） ⏐ ==> rag  http://127.0.0.1:58903  并发 8 × 20 轮（mode=many-100） ⏐ p99：Go 444.5ms  vs  RAG 416.8ms  ⇒ RAG/Go = 0.94× ⏐ p50：Go 353.0ms  vs  RAG 359.6ms  ⇒ RAG/Go = 1；<br>`tools/difftest/run-hot-rpc-difftest.sh` ⇒ **EXIT=1**（2026-10-30 03:43:58 · 2s）—— 🔴 **探针判定为「不一致」**；覆盖断言：==> [1/7] 清场：停掉旧的 hot-rpc 进程 ⏐ ==> [2/7] 清空两个 Redis（**必须等旧进程死后**，否则它会把旧 sketch 写回来 ⇒ 分数翻倍） ⏐ ❌ hotrpcdifftest-redis-go:6379 DBSIZE=（应为 0）⇒ 有旧数据残留，停下；<br>`tools/difftest/run-hot.sh` ⇒ **EXIT=1**（2026-10-30 03:39:35 · 30s）—— 🔴 **探针判定为「不一致」**；覆盖断言：✓ CASE-18    畸形 JSON body ⇒ 5001 ⏐ ✓ CASE-19    不存在的路径 ⇒ 404 裸响应（text/plain + nosniff） ⏐ ✓ CASE-20    方法不匹配 ⇒ 405 裸状态码（无 body） ⏐ ✗ CASE-21    下游拖过 3s ⇒ 503 Request Timeout（校验 nosniff） ⏐ ↳ 已知漂移：503 响应缺 X-Content-Type-Opti。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

## DRIFT-HOTAPI-029 ✅ 已修 · SEMANTIC MISMATCH · MEDIUM
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleHydrator.java:261`（`remainingBudgetMs(long deadlineNanos)` ⇒ **绝对 deadline** 在**调用点**重算 `min(2s, deadline-now)`）+ `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleHydrator.java:159`（`buildHotArticleItem` 把绝对 deadline 传进每次调用）· Go `service/hot/api/internal/logic/hot/getHotArticlesLogic.go:93-110`（goroutine 共享 ctx 的**绝对** deadline）· 守门在仓：`service/hot/hot-api/src/test/java/com/rag/hot/hot/api/service/HotArticleHydratorTest.java` · 核对日期 2026-10-30。

- **Component**：`hot-api` 的**补全（hydration）阶段**
  （`HotArticleQueryService` + `HotArticleHydrator` + `ArticleRpcClient`）
- **Operation**：每个 article RPC 的 **gRPC deadline** 怎么算
- **根因**：RAG 把「请求开始时取一次的**剩余毫秒快照**」传给**所有**补全 ⇒
  **后发的补全拿到的是「全新的 2s」，不被请求的绝对 deadline 卡住**；
  Go 侧共享的是 ctx 上的**绝对** deadline，每个补全在**自己的发起时刻**重算
  `min(ctx.deadline, now + 2s)` ⇒ 两者**不等价**。

### 一、判决过程（U5-OPEN-1 的隔离实验 E1）

台账列了两条候选原因（① 客户端熔断的开启时机 / ② 剩余预算的取样时刻），
两者**可观察后果相同、修法完全不同** ⇒ 「不隔离就动手 = 盲修」。
本轮用**下游桩的服务端视角**做判决：在飞 gRPC 调用被客户端取消的瞬间，
桩会立刻打出 `ARTICLE_CALL_CANCELLED`（带毫秒时间戳）⇒
「调用死在哪一刻」在服务端**可直接观测**，不需要相信客户端的自述。

**场景**：`article-slow-short`（10 条热榜 ⇒ 并发窗口 8 ⇒ 第 3 批 2 条必然是「后发的」，
article-rpc 睡 5s ⇒ 必然拖过 3s 的 HTTP 超时）。

| 量 | Go 真源 | RAG（**修前**） | RAG（**修后**） |
|---|---|---|---|
| HTTP 响应 | 503 + `Request Timeout\n` @ 3006ms | 503 + `Request Timeout\n` @ 3027ms ✅同形 | 503 + `Request Timeout\n` @ 3017ms ✅同形 |
| article 调用数 | 10 | 10 ✅ | 10 ✅ |
| 前 8 条死亡时刻 | ~1992-1996ms（2s 调用 deadline） | ~1994-2006ms ✅ | ~2000-2019ms ✅ |
| **后 2 条死亡时刻** | **2978-2979ms**（= 请求 deadline t0+3s） | **3997-4007ms**（= 自己发起+2s）❌ | **2975-2977ms** ✅ |
| **两侧差值** | —— | **+1028ms** ❌ | **+7ms** ✅ |

⇒ **候选 ② 成立**（修前差 1.03s ⇒ RAG 的后发补全确实**没被请求 deadline 卡住**）。

### 二、为什么这会造成「5xx 速率不同」（U5-OPEN-1 的原始症状）

后 2 条补全的失败是 `DEADLINE_EXCEEDED` ⇒ 在 Go 的 `codes.Acceptable` 里**不可接受**
⇒ **计入 client breaker 的失败**。而它们**落在哪一段时间桶**，
直接决定 breaker 的 `dropRatio` ⇒ 决定熔断**何时打开** ⇒ 决定后续请求是
「补全慢 → 503」还是「熔断短路 → 快速失败 → 200」。
⇒ 修前：Go 的这 2 条失败落在 t≈3.0s、RAG 落在 t≈4.0s ⇒ 两边 breaker 的失败分布不同 ⇒ 打开时机不同。

**修后复测（原始症状已消失）** —— 同一场景（12 并发 × 3 波）：

| 波次 | Go（修前，第 1 轮） | RAG（修前，第 1 轮） | **Go（修后）** | **RAG（修后）** |
|---|---|---|---|---|
| 第 1 波 | 9 超时 503 + 2 熔断裸 503 | 9 超时 503 | **8 超时 503 + 4 个 200** | **8 超时 503 + 4 个 200** ✅ |
| 第 2 波 | 2 熔断裸 503 | 0 | **12 个 200** | **12 个 200** ✅ |
| 第 3 波 | 0 | 3 熔断裸 503 | **12 个 200** | **12 个 200** ✅ |

⇒ **两侧逐波完全一致**。
⚠️ 顺带说明：此轮「HTTP 层熔断**没有**打开」（熔断裸 503 = 0）是**两侧相同**的形态 ——
**客户端**熔断先打开 ⇒ 补全变快速失败 ⇒ 请求以 **200** 结束 ⇒ HTTP 层收不到 5xx。
（`probe-hot-protection.py` 的 breaker 判据要求「熔断裸 503 > 0」，在本场景下不适用；
「HTTP 层熔断真的能打开」这一命题已由第四/十轮的 U4 闭合，与本条无关。）

### 三、修法（§3.2 三段式留痕见 `HotArticleHydrator#remainingBudgetMs`）

| | 修前 | 修后 |
|---|---|---|
| 传递的东西 | 请求开始时的**剩余毫秒快照** | 请求的**绝对 deadline**（nanoTime 时基） |
| 每个调用的预算 | `min(2s, 快照)`（**冻结**） | `min(2s, deadline − now)`（**调用点重算**） |
| 实际 deadline | `now + min(2s, 快照)` | `min(now + 2s, deadline)` ⇒ **与 Go 逐字相同** |

⚠️ 精度：由 `GoStyleRequestDeadline#remainingMillis()`（毫秒、向下取整）重建的绝对 deadline
比真值**最多早 1ms** ⇒ RAG 这一侧**更保守**（不会比 Go 多活）。
⚠️ `hot-rpc` 那次调用**不改**：它在预算快照之后**立即**发生 ⇒
此刻 `min(2s, 剩余)` 与 Go 的 `min(ctx.deadline, now+2s)` **取值相同**。

### 四、守门 + 变异验证

| 项 | 内容 |
|---|---|
| **守门测试** | `HotArticleHydratorTest#remainingBudgetIsRecomputedAtEachCallSite`：10 条 > 并发窗口 8 ⇒ 后 2 条必然后发；断言「后发补全拿到的剩余预算**更小**」（差额 ≥ 40ms）且「不超过请求 deadline 对应的剩余量」 |
| **⚠️ 先改断言** | 原断言 `forwardsRequestBudgetToEveryHydration`（「剩余预算被**原样**传到底层」）**本身就是漂移后的语义** ⇒ 按 §3.3.3「先改/删错误断言，再改实现」先改掉，再改实现 |
| **变异验证** | 把 `remainingBudgetMs(deadlineNanos)` 改成「把绝对 deadline 误当成剩余毫秒原样传下去」⇒ 每个调用都拿 2000ms ⇒ 请求 deadline 彻底失效 ⇒ **守门必须变红** ✅ —— 实测（`tools/difftest/mutate-hot.py --only M11`，基线 **87 tests / 0 failures**）⇒ ✅ **1 条红**：`HotArticleHydratorTest#remainingBudgetIsRecomputedAtEachCallSite` |
| **附带修复** | `GoStyleGrpcClientBreakerE2ETest` 的阶段 F 原用**比率**（`downstreamCalls < httpRequests/2`）表达「远小于」⇒ 实测**偶发失败**（42 vs 41）。根因不是实现漂移，而是**比率型断言与本机制的「首桶下界」天然冲突**（熔断只统计**已完成的 250ms 桶** ⇒ 第一个桶里的请求必然全部打到下游）⇒ 改用**直接计数**「被短路的请求数 ≥ 10」，更稳也更强 |

### 五、证据强度

| 手段 | 覆盖 |
|---|---|
| **双侧活体对拍（真栈 + 真桩 + 毫秒时间戳）** | ✅ 在飞 gRPC 的**取消时刻**、HTTP 响应形态、逐波 5xx 分布 —— 修前 +1028ms / 修后 +7ms |
| **守门测试 + 变异验证** | ✅ 「预算在调用点重算」这条语义本身 |
| ⚠️ **未覆盖** | ① 「客户端熔断」的**概率性**（`proba.TrueOnProba`）在两侧的**随机序列**不同 ⇒ 熔断打开的**具体请求序号**仍可能差 1~2 个；② **多副本部署**下的分片分配（UNR-5）；③ 单请求场景下的**首次**失败批次（本条只影响**后发**的补全） |

### 六、连带影响

⇒ 本条修好后，**U5-OPEN-1 的两个候选原因只剩候选 ① 存疑**：
「客户端熔断开启时机」是否仍有残余差异，本轮的逐波数据**两侧完全一致** ⇒ **未发现残余差异** ⇒ 一并闭合。

---

## 2026-10-25 补记：`DRIFT-MSG-V-030` 的**域内部分**已修 —— 本模块产物现在是可执行 jar

> **登记方在 message 域**（[`docs/DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-030`）——
> 它是 V-028 的同款缺陷，但**修法落在本模块自己的 `pom.xml`** ⇒ 本域已修。

| 项 | 修前 | 修后 |
|---|---|---|
| 产物 | **32 KB** library jar，**无 `Main-Class`** | **67 MB** Spring Boot fat jar，`Main-Class: org.springframework.boot.loader.launch.JarLauncher` |
| `java -jar` | 「中没有主清单属性」 | 可启动（外部依赖齐备的前提下） |
| pom | **无 `<build>` 段** | 补 `<build>` 段 + `spring-boot-maven-plugin`（版本由根 pom 的 `spring-boot-starter-parent` 继承，不写 `<version>`） |

**修法**：照 `service/message/message-api/pom.xml` 的 V-028 同款写法，在本模块 pom 补
`<build><plugins><plugin>spring-boot-maven-plugin</plugin></plugins></build>`。
启动类 `HotApiApplication` 唯一且带 `main` 方法 ⇒ **不显式写 `<mainClass>`**（少一处可能写错的配置）。

⚠️ **本次只证明「产物带 `Main-Class` 且是可执行 fat jar」**，**没有**真起进程验证启动成功
（会撞 Nacos / Redis / Kafka / gRPC 等外部依赖 ⇒ 起不来不等于产物有问题）。

⚠️ **「`mvn package` 成功」不算数** —— 本项目至今的每一项验证都跑在容器 / 测试环境里，
**从来没有碰过 jar 本身**（这正是 §3.3「编译通过 ≠ 产物可运行」的形态）。
⇒ **判据是守门脚本**：`tools/difftest/check-jar-executable.sh`
（逐个 api 模块解包 `META-INF/MANIFEST.MF` 看 `Main-Class`）
⇒ **2026-10-25 实测 `EXIT=0`**（修前 `EXIT=1`）。

---

## 第十三轮（2026-10-25）：「Tomcat 容器输入上限三旋钮」全仓对齐

> **范围**：全部 **10 个 api 模块**的 `application.yml` 的 `server:` 块（D21 / D22 / D23 的修法落点）。
> **性质**：**跨域**（影响全部 10 个 api 进程），按 §5.3b 本轮**只写 hot 域台账**，
> 其余 9 个域的台账由各自域补（合并后统一更新共享索引）。
> **守门**：`tools/difftest/check-tomcat-limits.py`（静态，10/10 ✅ `EXIT=0`）
> ＋ `HotApiRealTomcatGateTest` 的 3 条**真 Tomcat 运行时**断言（裸 socket）。

| 旋钮 | Boot 4 默认 | 配成 | Go 侧依据 | 修后双侧活体实测 |
|---|---|---|---|---|
| ① `server.tomcat.relaxed-query-chars` | 无 | `["[", "]"]` | `net/url.ParseRequestURI` **不校验** query（实测 err=nil）＋ go-zero `httpx.GetFormValues` 支持方括号数组记法 | `?page[]=5` ⇒ **Go 200 / RAG 200，响应体逐字节相同**（各 95 B） |
| ② `server.tomcat.max-parameter-count` | 1000 | `2048` | go-zero `httpx/util.go:15` `maxFormParamCount = 2048` | 首个翻转 n **两侧都是 2049**，响应体各 55 B **逐字节相同** |
| ③ `server.max-http-request-header-size` | 8KB | **`1028KB`** | `net/http/server.go:931` `DefaultMaxHeaderBytes = 1<<20` **+ `:941` `+4096 // bufio slop`** | 首个翻转长度 **Go 1052536 字节 = RAG 1052536 字节（差 0）** |

### ⚠️ ③ 为什么是 `1028KB` 而不是 `1MB`（本轮**推翻**了原台账的修法）

原台账写「`server.max-http-request-header-size=1MB`」⇒ **比 Go 严 4096 字节**。
Go 的真实读上限不是一个常量，是**常量 + bufio 余量**：

```go
// net/http/server.go:940-942（go1.25.11）
func (s *Server) initialReadLimitSize() int64 {
	return int64(s.maxHeaderBytes()) + 4096 // bufio slop
}
```

⇒ 实测（双侧活体，二分「第一个不再是 200 的 query 长度」）：
Go **1052536** 字节翻转（431）；配 `1MB` 的 RAG **1048440** 字节翻转（400）
—— 差值**正好 4096** ⇒ 反推出两边各自的真实上限：`1 MiB + 4 KiB` vs `1 MiB`。
⇒ 配 `1028KB`（= 1052672 B）后**两侧翻转点完全重合**。

### ⚠️ ② 为什么是 `2048` 而不是 `-1`（关闭 Tomcat 的上限）

Tomcat 的 `maxParameterCount` 数的是**总参数个数**（query + post），
go-zero 的 `maxFormParamCount` 数的是**非空值个数** ⇒ **两层语义不同**（详见 `DRIFT-HOTAPI-030`）。
本轮**没有**选 `-1`，理由：

1. `service/**` 实测：只有 **article-api / hot-api / favorite-api** 三个模块复刻了 Go 的应用层判据；
   **另外 7 个 api 模块，Tomcat 这层就是唯一的上限** ⇒ 配 `-1` 等于**拆掉那 7 个模块的唯一上限**。
2. Go 1.25 的 `net/url` 还有**段数**上限 `defaultMaxParams = 10000`
   （`article-api` 的 `GoStyleGetFormValuesFilter` 复刻了，`hot-api` / `favorite-api` **没有**）
   ⇒ 关掉 Tomcat 的上限后，「≥10001 段」那段会**从「巧合相同」变成「真的不同」**。

⇒ `2048` 是「当前各模块的应用层复刻程度下」的**最小公倍数**；
**彻底对齐的修法见 `DRIFT-HOTAPI-030` 的「修法」节**（跨域，需一次拍板）。

---

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/check-jar-executable.sh` ⇒ **EXIT=0**（2026-10-30 04:55:19 · 2s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅  service/task/task-api                       64M   Main-Class: org.springframework.boot.loader.launch.JarLauncher ⏐ ✅  service/task/task-rpc                       96M   Main-Class: org.springframework.boot.loader.launc。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

## DRIFT-HOTAPI-030 —— 🔴 OBSERVABLE DIFFERENCE：Tomcat 数「总参数个数」、Go 数「非空值个数」⇒ 中间一段区间两侧行为不一致（**跨域 · 待拍板**）

> ### ✅🟡 2026-10-29 对齐（台账对齐 phase · W-E lane）：本条**拆成两格**，两格状态**不同** —— 别再把它当「一条」
>
> **原标题（🔴 OBSERVABLE DIFFERENCE · 跨域 · 待拍板）原样保留不改写**（D5）⇒ 下面是**追加**的当前状态。
>
> | 格 | 内容 | **当前状态** | 依据（核对日期 2026-10-29） |
> |---|---|---|---|
> | **主轴** | 「Tomcat 数**总参数个数**（含空值）」vs「Go 数**非空值个数**」⇒ 区间 **2049~10000 且非空值 ≤2048** 时 Go 放行 / RAG 拒 | ✅ **已闭合** | 共享层 `service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java` —— `MAX_PARAMETER_COUNT = 10001` 且 `customize(...)` 里**无任何 `mode` 判断**（**无条件**抬上限）⇒ 容器不再比 Go 更早拒 ⇒ 该窗口 Go 放行 / RAG 也放行。落码 = G4 `cd893d1`（2026-10-27）<br>⇒ **2026-10-29 复核代码：该常量至今仍是 10001、`customize` 仍无 `mode` 分支 ⇒ 主轴仍闭合** |
> | **后续格（段数轴）** | 总**段数** > Go 的 `net/url` 上限 `defaultMaxParams = 10000`（Go 1.25.11 `src/net/url/url.go:1001`）⇒ Go 解析失败 / RAG 放行 | 🔴 **仍 OPEN** | 共享层 `MAX_PARAMETER_COUNT` **至今 = 10001**（未改 10000）⇒ Tomcat 的运行期上限仍比 Go 的段数上限多 1 ⇒ **RAG 侧首个翻转点 = 10002 段 / Go 侧 = 10001 段** ⇒ 差一格<br>⇒ 域内实测记录 ⇒ `docs/DRIFT/favorite/api.md` §18（favorite 域 L4 真 Tomcat 实测：Go 10001 段 / RAG 10002 段）<br>⇒ **修法 = 共享层 `MAX_PARAMETER_COUNT` `10001 → 10000`**（**共享面 ⇒ 本域无权改** ⇒ 归 `global` / G4） |
>
> ⚠️ **本条的状态取值（2026-10-29 起）**：**主轴 = ✅ 已闭合**；**后续格 = 🔴 仍 OPEN** ⇒ **同一个编号下两格状态不同是「有意为之」**（不是台账不一致）⇒ 对拍时**先看落在哪一格**。
> ⚠️ **本域不擅自改**：共享层属 `service/common`（AGENTS.md §5.3c）⇒ ⇒ 本条**不**在本域改代码，只把两格状态分开记清。
> ⚠️ **跨域台账**（`docs/DRIFT/_cross/open-items.md` · `docs/DRIFT/global/**`）**不是本 lane 的写权范围** ⇒ 由主线 / G4 统一改。

### 现象（2026-10-25 双侧活体实测，hot 域）

请求带 **2049 ~ 10000 个 query 参数**、其中**非空值 ≤ 2048** ⇒ **Go 200 / RAG 5001**。

| 输入（query） | Go | RAG | |
|---|---|---|---|
| 2049 个 `a=1`（**全非空**） | 200 + `{code:5001}` | 200 + `{code:5001}` | ✅ 相同 |
| **2049 个 `a=`（全空值）** | **200 + `{code:200}`** | **200 + `{code:5001}`** | 🔴 **不同** |
| **3000 个 `a=`** | **200 + `{code:200}`** | **200 + `{code:5001}`** | 🔴 **不同** |
| 10001 个 `a=`（段数超 Go 的 10000） | 200 + `{code:5001}` | 200 + `{code:5001}` | ✅ 相同（**巧合**：Tomcat 的 2048 总数上限先拒） |

### 📌 订正注记（2026-10-27 · G3 · 22:5x）—— 上表**已过期**（共享层把容器上限**无条件**抬到 10001 之后）

⚠️ **上表原样保留不改写**（它是 2026-10-25 的历史实测）⇒ ⇒ **但它的 RAG 列已不是现状**：
共享层 `GoStyleTomcatParameterLimitCustomizer`（G4 落码 `cd893d1`）现在**无条件**把容器上限抬到
`MAX_PARAMETER_COUNT = 10001` ⇒ ⇒ **Tomcat 的「2048 总数上限」不再是运行期上限** ⇒ ⇒ 上表三行各自变成：

| 输入 | 2026-10-25 记的 RAG | **2026-10-27 的现状** | 与 Go |
|---|---|---|---|
| 2049 个 `a=`（全空值） | 200 + `{code:5001}`（被拒） | 🔴 **200 + 业务结果（放行）**（容器上限 10001 ⇒ 不抛 ⇒ 应用层数**非空值** = 0 ⇒ 放行） | ✅ **已一致**（该窗口**闭合**） |
| 3000 个 `a=` | 200 + `{code:5001}`（被拒） | 🔴 **200 + 业务结果（放行）** | ✅ **已一致**（同上） |
| **10001 个 `a=`** | 200 + `{code:5001}`（被拒） | 🔴 **200 + 业务结果（放行）** ⇒ ⇒ **Go 200 + `{code:5001}` / RAG 放行 ⇒ 两侧不一致** | 🔴 **新的差一格** |

⇒ ⇒ **残留窗口从「2049~10000 且非空值 ≤2048」收窄成「恰好 10001 段」**
⇒ ⇒ **favorite 域已 L4 实测坐实**（真 Tomcat，2026-10-27）：**首个翻转点 = Go 10001 段 / RAG 10002 段**
⇒ ⇒ 顺带**证伪**了「Go 侧拒绝形状 = 400」这个前提（真源 handler 写的是 **200 + 5003**）
⇒ ⇒ **修法 = 共享层 `MAX_PARAMETER_COUNT` `10001 → 10000`**（改后 RAG 翻转点 = 10001 ⇒ 与 Go **同形**）
⇒ ⇒ 逐条证据 + 修法 + 前提 ⇒ **`docs/DRIFT/favorite/api.md` §18**（favorite 域本轮 L4 实测）
⇒ ⇒ ⚠️ **本域不擅自改**：`service/common/**` 是共享面（AGENTS.md §5.3c）⇒ ⇒ 已派单 G4。

### 根因：两层限制的**语义不同**

- **Tomcat**（`org.apache.tomcat.util.http.Parameters.processParameters`）：
  `limit > -1 && parameterCount >= limit` ⇒ 抛 `InvalidParameterException`，
  **数的是参数总数**（query + post）。实测触发点：limit=1000 ⇒ 第 1001 个就抛；
  limit=2048 ⇒ 第 2049 个就抛 ⇒ 与实测完全吻合。
- **go-zero**（`rest/httpx/util.go:44-53`）：
  ```go
  for _, v := range values {
      if len(v) == 0 { continue }          // ← 空值**不计数**
      if n < maxFormParamCount { filtered = append(filtered, v); n++ }
      else { return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode()) }
  }
  ```
  ⇒ **数的是非空值个数** ⇒ 2049 个**空值**参数在 Go 侧计 **0**。

⇒ 两层阈值**只在「值全非空」时巧合相等**（2049）。
⚠️ RAG 侧的应用层判据（`GoFormValues.requireFormValueCountWithinLimit`）**逐字复刻了 Go 的空值跳过**
（`if (v == null || v.isEmpty()) continue;`）—— 但它调 `request.getParameterMap()`，
**Tomcat 的参数解析在那一刻先抛** ⇒ 应用层那段**根本执行不到**。

> ### 🔴 2026-10-29 真实性复核（本节「根因」格**已钉死** · 原文保留，不改写）
>
> ⚠️ **上面这段「根因」描述的是 2026-10-27 之前的状态**（那时容器上限 = yaml 的 2048 ⇒ 容器先于应用层拒）。
> ⇒ **它与本条上半部分「2026-10-29 对齐」块、以及与 favorite 域的 L4 实测（§18）**曾经**自相矛盾** ——
> **现已钉死**（核对日期 2026-10-29 · 两侧源码 + 本地 sources jar 实测）：
>
> **① Tomcat 到底数什么（钉死）** —— Tomcat **11.0.24**（Spring Boot 4.0.8 管的版本：`spring-boot-dependencies-4.0.8.pom:209` `<tomcat.version>11.0.24</tomcat.version>`）：
> `org/apache/tomcat/util/http/Parameters.java:241-254` `addParameter(String key, String value)`：
> ```java
> if (limit > -1 && parameterCount >= limit) {          // :247
>     throw new InvalidParameterException(...);          // :249
> }
> parameterCount++;                                     // :251  ← 每次调用都 +1
> paramHashValues.computeIfAbsent(key, ...).add(value);  // :253
> ```
> ⇒ **`parameterCount++` 在 `:251`，位于 `addParameter` 的每次调用里 ⇒ 它数的是「**名值对总数**」**
> （**不是**「去重后的参数名个数」）⇒ **对 GET query 而言 = 段数**（每个 `a=1` 段 = 1 个名值对）
> ⇒ **query 与 body 表单字段都计入**（`processParameters` 的两条路径都调 `addParameter`）。
> ⇒ **抛点在 `addParameter`（`:247-251`）**，不是 `processParameters` 本身（那是调用方）—— 原文写的类名正确、**方法名指错了一层**（**原文保留，不改写**）。
> ⇒ **「limit=1000 ⇒ 第 1001 个就抛 / limit=2048 ⇒ 第 2049 个就抛」= 正确**（`>=` 判定 ⇒ 第 `limit+1` 个抛）。
>
> **② Go 到底数什么（钉死）** —— Go **1.25.11**（`go version` ⇒ `go1.25.11 darwin/arm64`）：
> `src/net/url/url.go:1001` `const defaultMaxParams = 10000`；`:1003-1017` `urlParamsWithinMax(params)` 判 `params <= defaultMaxParams`；
> `:1019-1022` `parseQuery` 调 `urlParamsWithinMax(strings.Count(query, "&") + 1)` ⇒ **数的是「段数」** ⇒ **首个翻转点 = 10001 段**。
> ⇒ go-zero 应用层 `rest/httpx/util.go:15` `maxFormParamCount = 2048`、`:44-45` 空值 `continue`、`:48-52` 超限报错
> ⇒ **数的是「非空值个数」**，且计数器 `n` 声明在 **`:33`**（**在 `for name, values := range r.Form`（`:35`）之外**）⇒ **跨所有 key 共享**（**原文未写这一句 ⇒ 补**）。
>
> **③ 由此得到的「轴」结论（这才是残留差异的真正形状）**：
>
> | 轴 | Go 数什么 | RAG 数什么 | 现行阈值 | 差 |
> |---|---|---|---|---|
> | **段数轴**（`net/url` 10000 段） | **段数** | **段数**（Tomcat `parameterCount`） | Go **10000** / RAG **10001** | 🔴 **差一格**（Go 翻转点 10001 段 / RAG 10002 段） |
> | **非空值轴**（go-zero 2048 非空值） | **非空值个数** | **非空值个数**（`GoFormValues.requireFormValueCountWithinLimit` 逐字复刻） | 两侧都 **2048** | ✅ **一致** |
>
> ⇒ ⇒ **原文那句「两层限制的语义不同」只在「非空值轴」上成立**（且那一轴**现在已一致**）；
> ⇒ ⇒ **当前真正仍 OPEN 的残留是「段数轴」上的同轴差一格**，**不是**「语义不同」。
> ⇒ ⇒ **修法随之收窄**：**不需要**「关掉 Tomcat 上限 + 各域补 10000 段判据」两步（原文「修法」节保留，不改写）——
> **共享层把 `MAX_PARAMETER_COUNT` 从 `10001` 改成 `10000`** 即可让**段数轴**与 Go 同形；
> ⚠️ **但改完必须逐轴复测非空值轴**（Tomcat 上限一旦 = 10000，2049 个非空值仍会被容器先拒 ⇒ 拒绝**形状**是
> Tomcat 的 400/HTML 还是 Go 的 200+5003 ⇒ 取决于应用层判据是否抢在容器解析之前 ⇒ **这是「动手前须先钉死」的那一点**）。
>
> **④ 「动手前须先钉死」的三件事（2026-10-29 明确记录）**：
> ① Tomcat 的比较语义 = **名值对总数**（= query 段数）—— 上面 ① 已钉死；
> ② 共享层的**实际运行期值** = **10001**（不是 yaml 里的 2048）—— 已钉死：`service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:170` `MAX_PARAMETER_COUNT = 10001`、
> `:181-196` `customize(...)` **无 `mode` 分支**（无条件抬上限）；
> ⚠️ **为什么 yaml 的 2048 不生效（机制，2026-10-29 实测）**：Boot 4.0.8 `TomcatWebServerFactoryCustomizer.ORDER = 0`（`spring-boot-tomcat-4.0.8-sources.jar` 的 `autoconfigure/TomcatWebServerFactoryCustomizer.java:73`）⇒ **先于**共享层的 `@Order(Ordered.LOWEST_PRECEDENCE)` 执行；
> 而 Boot 的 `customizeMaxParameterCount`（同文件 `:333-334`）**不是** `factory.setMaxParameterCount(...)`，而是 **`factory.addConnectorCustomizers((connector) -> connector.setMaxParameterCount(v))`** ⇒ 两个 connector customizer **按插入顺序**跑（`TomcatWebServerFactory.java:417-419`）⇒ **后插入者胜** ⇒ **运行期 = 10001**；
> ③ **当前仍 🔴 的只有「后续格（段数轴）」** ⇒ **修法落在共享层** ⇒ **本域无权改** ⇒ **只登记，不改产品代码**（AGENTS.md §5.3c）。
>
> ⇒ **本条状态取值（2026-10-29）**：**主轴 = ✅ 已闭合** / **后续格（段数轴）= 🔴 仍 OPEN** ⇒ **两格状态不同是「有意为之」**（见本条上半部分的「2026-10-29 对齐」块）。
> ⇒ **本节的差异描述与历史叙述全部原样保留**；本注记只追加「已钉死」的事实与**收窄后的修法**。

### 影响范围

**全部 10 个 api 模块**（Tomcat 层的行为与业务模块无关）。

### 修法（待拍板 · **跨域**）

1. ② 改成 `server.tomcat.max-parameter-count: -1`（**关闭** Tomcat 的总参数上限
   —— Tomcat 的判定是 `limit > -1`，负值即**完全关闭**）；
2. **每个 api 模块**都补齐 Go 的应用层两段判据：
   - Go 1.25 `net/url/url.go:1001-1022` 的 **10000 段**上限
     （`article-api` 的 `GoStyleGetFormValuesFilter` 已复刻 ⇒ 可作模板）；
   - go-zero 的 **2048 非空值**上限（`article-api` / `hot-api` / `favorite-api` 已有）。

⇒ **为什么本轮没做**：第 1 步会让第 2 步**成为硬前提** ——
在只改了 7 个模块（`hot-api` / `favorite-api` 没有 10000 段判据）的情况下关掉 Tomcat 的上限，
「≥10001 段」那段会**从「巧合相同」变成「真的不同」** ⇒ 那是**引入新漂移**，不是修漂移。
⇒ 且第 2 步要动 **10 个域 + `favorite` 域（台账为 0 字节）** ⇒ 按 §5.3b **不越界改**。

---

## DRIFT-HOTAPI-031 —— 🟡 框架限制：超长请求行的**状态码与错误体形状**不可配（Go 431 + `text/plain` vs RAG 400 + `text/html`）
> ✅ **2026-10-30 真实性复核（favorite+hot lane）**：原判 **🟡 框架限制（建议登记为签字偏离候选 `SD-2026-10-25-CAND-10`，未签字）** ⇒ **现行态 = 🟡 框架限制 · 候选仍未签字**（未改判）。
> 依据：RAG `service/hot/hot-api/src/main/resources/application.yml:50`（`max-http-request-header-size: 1028KB` ⇒ 阈值已与 Go 对齐，但**超限后的状态码/错误体由 Tomcat 决定**）+ `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:96`（共享层 advice 只管**成功**响应的 `Content-Type` ⇒ **管不到连接器层直接产出的错误页**）· Go `service/hot/api/hotcenter.go:53-66`（`httpx.SetErrorHandler` ⇒ 业务错误走**业务信封** + HTTP 200；`net/http/server.go:2049` 的 431 则是 `net/http` 自己产的 `text/plain`）· 核对日期 2026-10-30。
> ⇒ ⇒ **「Tomcat 在连接器层就拒 ⇒ 状态码与错误体由 Tomcat 决定」这个前提仍成立**（`service/common` 侧无 431 开关可用）⇒ **维持 🟡 框架限制**；候选 `SD-2026-10-25-CAND-10` 在 `docs/Intentional_Deviations/hot/api.md` 仍是 **⏳ 未签字** ⇒ **不升级为签字项、不升级为 🔴**。

### 现象

请求行超过上限时（`?a=` + >1 MiB 的 query）：

| | 状态码 | `Content-Type` | body |
|---|---|---|---|
| Go | **431** | `text/plain; charset=utf-8` | `431 Request Header Fields Too Large`（35 B） |
| RAG | **400** | `text/html;charset=utf-8` | Tomcat 的 HTML 错误页（**435 B**） |

### 根因

Tomcat 在**连接器层**就拒了，请求**根本没进 Servlet**：

```
INFO  o.apache.coyote.http11.Http11Processor : Error parsing HTTP request header
java.lang.IllegalArgumentException: Request header is too large
	at org.apache.coyote.http11.Http11InputBuffer.fill(Http11InputBuffer.java:768)
```

⇒ Spring 的 `GlobalExceptionHandler` **没有机会介入** ⇒ 状态码与错误体**由 Tomcat 决定**。

### 为什么**配不出来**（框架限制）

- Tomcat 的 `Connector` **没有**「请求行过大 ⇒ 用 431」的开关；
- `server.error.whitelabel.enabled=false` 只改 **body**，**不改状态码**，
  而且会**波及其他错误响应**（404 / 405 的形态已与 Go 对齐 ⇒ 不能动）。

⇒ 属**框架限制**（不是「RAG 偷懒」）⇒ 建议登记为**签字偏离候选**
（`SD-2026-10-25-CAND-10`，**未签字**，见 [`docs/Intentional_Deviations/_INDEX.md`](../../Intentional_Deviations/_INDEX.md)）。
⚠️ 触发条件是「请求行 > 1 MiB + 4 KiB」⇒ **病态输入**，正常客户端不会碰到。

## 附录 · 🔴 api 侧「客户端硬上界」：上界交给共享层，**请求预算仍由域侧传播**（2026-10-31 · G3 第十三轮）

### 一、这次改了什么（**域侧**，只动本域）

| 落点 | 变更前 | 变更后 |
|---|---|---|
| 域内传播类 `GoStyleGrpcCallDeadline` | `min(2s, 请求剩余预算)` ⇒ **自带封顶**（域侧私有常量 `DEFAULT_CALL_TIMEOUT_MILLIS`）⇒ 那是**第二个上界来源** | **只传播、不封顶**：无预算 ⇒ 原样返回；有预算 ⇒ 按剩余预算传播；预算耗尽 ⇒ 1ns（即时失败） |
| 域 yml 的 `rag.protection.outbound-deadline` 段 | **不存在** ⇒ 共享层 `capMillis` 取默认 0 ＝ **不启用** | `cap-millis: ${HOT_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000}` |
| 域内守门 | **不存在** | **新增** `HotApiOutboundDeadlineCapGateTest`（**7 例**：配置面 2 ＋ 行为面 3 ＋ 形态面 2） |

### 二、⚠️ 为什么本组件**仍需**域侧显式传播（**收敛不是「全删」**）

- 本组件的补全跑在**线程池 worker**上，而 `GoStyleRequestDeadline` 是 **ThreadLocal**
  （由 `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:324` 设在跑业务的 worker 线程上）
  ⇒ ⇒ worker 上**读不到**载体 ⇒ ⇒ 共享层拦截器在那里**看不到**请求预算
  ⇒ ⇒ 预算必须在**请求线程**上取一次（`HotArticleHydrator#remainingBudgetMs`，把请求的**绝对** deadline 折算成剩余毫秒）
  ⇒ ⇒ **显式**传进来 ⇒ ⇒ 所以 `GoStyleGrpcCallDeadline` **保留**（职责收窄为「纯传播」）
- ⇒ ⇒ 二者合起来仍是 Go 的公式：min(now+2s, 请求 deadline)
  （Go：`service/hot/api/internal/logic/hot/getHotArticlesLogic.go:132` 的 `l.svcCtx.ArticleRpc.GetArticle(l.ctx, …)`
  ⇒ ctx 随 goroutine 传下去 ⇒ 每次调用各自按发起时刻重算）
- ⇒ ⇒ 该类里剩下的两条 `withDeadlineAfter` 代码行（预算耗尽 ⇒ 1ns ／ 按剩余预算传播）
  是**同一个传播方法的两个分支** ⇒ 属共享层棘轮判据 ④ 认可的「**纯预算传播**」形态（**不是** cap）

### 二、Go 侧真值（**逐条读源码**，不采信转述）

| 环节 | 出处 | 内容 |
|---|---|---|
| 声明点 | `go-zero@v1.10.2/zrpc/config.go:31` | `Timeout int64 `json:",default=2000"`` ⇒ **默认 2000ms**（挂在 `zrpc.RpcClientConf` 上 ⇒ **按 client** 生效，不是全局一个数） |
| 消费点 | `go-zero@v1.10.2/zrpc/client.go:70-71` | `if c.Timeout > 0 { opts = append(opts, WithTimeout(time.Duration(c.Timeout)*time.Millisecond)) }` ⇒ **每个 client 各自**取自己的 yaml／tag 值 |
| 装配点 | `go-zero@v1.10.2/zrpc/internal/client.go:113-117` | 无条件装 `TimeoutInterceptor`（⇒ 出向调用**恒**有客户端超时，与有没有 HTTP 预算无关） |
| 开关默认值 | `go-zero@v1.10.2/zrpc/internal/config.go:15` | `Timeout bool `json:",default=true"`` |
| 执行点 | `go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25` | `ctx, cancel := context.WithTimeout(ctx, t)` ⇒ **取更早者**（min 语义） |

### 三、装配面（**为什么域侧删掉自己那份之后，上界仍然兜得住**）

- devh `grpc-client-spring-boot-starter 3.1.0.RELEASE` 的
  `net/devh/boot/grpc/client/interceptor/AnnotationGlobalClientInterceptorConfigurer.java`
  ⇒ `getClientInterceptorBeans()` = `applicationContext.getBeansWithAnnotation(GrpcGlobalClientInterceptor.class)`
  ⇒ ⇒ **按 Bean 注解收集**（**不是**类路径扫描）⇒ ⇒ 共享层
  `GoStyleOutboundDeadlineConfiguration` 产出的那个 `@Bean` 会被收进 registry ✔
- 本域 `HotApiApplication.java:69-72`：`@SpringBootApplication(scanBasePackages = {"com.rag.hot.hot.api", "com.rag.common"})`
  ⇒ ⇒ `com.rag.common.protection.GoStyleOutboundDeadlineConfiguration` 被扫到 ⇒ **Bean 在位** ✔
- devh 的 channel factory 建 channel 的最后一步是 `ClientInterceptors.interceptForward(channel, interceptors)`
  ⇒ ⇒ 全局拦截器装在 **channel** 上 ⇒ ⇒ **裸 stub 也经过它**（不需要域侧逐个调用点改造）
- 共享层 `GoStyleOutboundDeadlineInterceptor#effectiveCallOptions`（`service/common/.../protection/GoStyleOutboundDeadlineInterceptor.java:187-220`）
  = min(① 请求载体剩余（可见时）② `capMillis` ③ 调用点自带 deadline)
  ⇒ ⇒ **cap 与载体无关**（`if (capMillis > 0L)` 独立成立）⇒ ⇒ 域侧那份删掉后，**上界仍由共享层兜住** ✔
  ⇒ ⇒ 且与 Go 的 `context.WithTimeout(ctx, t)` **同形**（取更早者）

### 四、本域的 Go yaml 真值（**两个方向都零 `Timeout:` 行** ⇒ 组件级上界表达得了）

- `service/hot/api/etc/hotcenter.yaml:25-35` ⇒ `HotRpcConf:` 与 `ArticleRpcConf:` 两段**都**只有 `Etcd.{Hosts,Key}` ⇒ **都没有 `Timeout`**
  ⇒ ⇒ 两个方向的生效值**都是** 2000ms ⇒ ⇒ **组件级一个 cap 表达得了**（与 `article-api` 的「两方向真值不同」正相反）

### 五、守门证据（**实测**，含变异）

- 绿读数：`mvn -o -B -pl service/common,service/hot/hot-api -am test -Dtest='HotApiOutboundDeadlineCapGateTest'`
  ⇒ **7/7 绿**
- 变异（**共享层**：把 `GoStyleOutboundDeadlineInterceptor` 的 `capMillis` 写死 0）⇒ ⇒ 本域 **2 条**转红
  ⇒ ⇒ 症状 = 调用挂满 8s 后**返回 OK**（而不是在 2s 处 `DEADLINE_EXCEEDED`）
  ⇒ ⇒ ⚠️ 该变异**必须带 `-am`**（否则链 `~/.m2` 旧快照 jar ⇒ 变异进不了被测链路）
- 本域用例 ④（显式预算 300ms < cap 2000ms ⇒ 出向死在 ~300ms）⇒ ⇒ **钉的是 min，不是覆盖**
  ⇒ ⇒ 那条同时证明「域侧那份**传播**在删掉封顶之后**仍然生效**」（= 本轮收敛没有把请求预算弄丢）
- 🧹 **本域既有的 `ArticleRpcClientTest` 两条旧断言已改写**（它们锁的是**已删除**的域侧上界）：
  「域 client 会自带 2s 上界」⇒ 改为「域 client **不再自带**上界（无请求预算时**不挂** deadline）＋ **两个 client 的请求预算传播规则一致**」
  ⇒ ⇒ 「**上界**真的存在」那一半由 `HotApiOutboundDeadlineCapGateTest`（真发调用）承担 ⇒ ⇒ 两半合起来把这条轴钉住

### 六、与共享层的关系（**转派 G4**）

- 共享层棘轮 `service/common/src/test/java/com/rag/common/protection/GoStyleClientCallTimeoutSingleSourceGateTest.java`
  的**点位级例外集**（`KNOWN_INLINE_CAP_CALL_SITES`）里挂着本域那一条
  ⇒ ⇒ 本轮删除后该条目**失效** ⇒ ⇒ 按「**棘轮只能变紧，不能变松**」⇒ ⇒ 需 G4 剪掉它
  ⇒ ⇒ ⚠️ **剪例外集与删内联必须落在同一次提交**（否则主线会红一次）
  ⇒ ⇒ 建议 G4 把本分支合进他们分支、在同一个 merge commit 里剪 ⇒ 主线**零红窗**
- 共享层**模型缺口**（本轮再次登记）：`cap-millis` 是**组件级一个值**
  ⇒ ⇒ 对「同一组件内各 client 真值不同」的域（如 `article-api`：`70000` vs `2000`）**表达不了**
  ⇒ ⇒ 建议共享层加 `rag.protection.outbound-deadline.per-client.<client-name>`
  ⇒ ⇒ 详见 `docs/DRIFT/article/api.md` 的同款登记

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W12

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-HOTAPI-025 | C（阶段二 · 残留是 hot-api 域内的「补全批次继承可取消 Context」工程项 ⇒ 域内可自修） | P2 | 否 | Go `service/common/observability/http.go:80-88`（`case <-ctx.Done():` ⇒ `statusCode := http.StatusServiceUnavailable`，`errors.Is(ctx.Err(), context.Canceled)` ⇒ **499**，`http.Error(w, httpTimeoutBody, statusCode)` ⇒ Go 的 499 由 **ctx 取消**驱动 ⇒ 客户端断连必产 499）vs RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:481-484`（探针判定「客户端已断连」⇒ `recordClientDisconnected(...)` ＋ `rec.writeTimeoutResponseQuietly(HTTP_CLIENT_CLOSED_REQUEST, httpReq)` ⇒ **真的写出 499**）＋ `:689`（`log.warn("[HTTP] {} - ...")` ⇒ 断连事件**在日志里可见**）⇒ **原标题断言的两个现象（「不产出 499」「完全不可见」）已不成立**；⚠️ **仍成立的残留 = 「断连后继续发下游调用」**：RAG `service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/HotArticleHydrator.java:74`（`private final Executor hydrationExecutor;`）＋ `:79`（`@Qualifier("hotHydrationExecutor")`）＋ `:124`（补全批次提交到**独立池** ⇒ **不继承** worker 线程上 attach 的可取消 `io.grpc.Context`，该 Context 在 `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:337-340` 才 attach）vs Go `service/hot/api/internal/logic/hot/getHotArticlesLogic.go:101-109`（`go func(){ ... l.buildHotArticleItem(hotItem) }()`）＋ `:132`（`l.svcCtx.ArticleRpc.GetArticle(l.ctx, ...)` ⇒ 补全 goroutine **显式带请求 ctx** ⇒ 断连即取消）⇒ ⇒ **差异仍成立**（形态 = 断连后 hot-api 仍继续发下游调用 ⇒ 属**时序差**，不是「499 不产出」）⇒ **不阻断阶段一** | 2026-10-30 |
| DRIFT-HOTAPI-026 | D（经分析确认不实现 · 容器/传输层收尾策略差异 ⇒ 是决策不是缺陷） | P3 | 否 | Go `go-zero@v1.10.2/rest/handler/maxbyteshandler.go:19-22`（`if r.ContentLength > n` ⇒ 裸 `w.WriteHeader(http.StatusRequestEntityTooLarge)` ⇒ **413 ＋ 空 body ＋ 无 Content-Type**）＋ `go-zero@v1.10.2/rest/config.go:53`（`MaxBytes int64 json:",default=1048576"`）vs RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxBytesFilter.java:108-113`（`getContentLengthLong() > maxBytes` ⇒ `response.setStatus(SC_REQUEST_ENTITY_TOO_LARGE)`，**不** `sendError`、**不**写 body ⇒ 与 Go 同形）＋ `service/hot/hot-api/src/main/resources/application.yml:144`（`max-bytes: 1048576` = Go tag 默认）⇒ **闸门行为两侧逐条等价**；**残留差异只在连接层**（Go：拒绝一个未读完的大 body ⇒ 内核发 RST；RAG：Tomcat `maxSwallowSize` 足够吞掉 ⇒ 干净关闭）⇒ ⇒ **差异仍成立**（仅连接层；响应状态码 / body / 关键响应头**逐字节相同**）⇒ **D（经分析确认不实现）**：理由 = ① wire 层无差异；② 差异是**容器收尾策略**（Go `net/http` vs Tomcat），不是业务语义；③ 真要「修」得让 RAG 对未读完的大 body 主动断连接 ⇒ 那是**新增 Go 侧不存在的断连行为**（Go 只在「body 超限」这一条路径上断）⇒ 反而制造新的不一致 ⇒ ⇒ **保留登记即可，不阻断阶段一** | 2026-10-30 |
| DRIFT-HOTAPI-031 | C（阶段二 · 未签字候选 ⇒ 不得自动归 D ⇒ 需工程手段或走完签字流程） | P3 | 否 | Go `net/http/server.go:2049`（`const publicErr = "431 Request Header Fields Too Large"` ⇒ Go 自己产的 **431 ＋ `text/plain`**）＋ `service/hot/api/hotcenter.go:53`（`httpx.SetErrorHandler` ⇒ 业务错误走业务信封 ＋ HTTP 200，但连接器层的 431 由 `net/http` 自己写）vs RAG `service/hot/hot-api/src/main/resources/application.yml:50`（`max-http-request-header-size: 1028KB` ⇒ **阈值已与 Go 对齐**，但超限后的状态码与错误体由 Tomcat 连接器层决定 ⇒ 400 ＋ `text/html` 错误页）＋ `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:96`（`ResponseBodyAdvice` ⇒ 只管**成功**响应的 `Content-Type`，**够不到**连接器层直接产出的错误页）⇒ **差异仍成立**：Go = 431 ＋ `text/plain`，RAG = 400 ＋ `text/html`；⚠️ 触发条件是「请求行 > 1 MiB ＋ 4 KiB」的**病态输入**（正常客户端碰不到）。⚠️ **不得自动归 D**：候选 `SD-2026-10-25-CAND-10` 在 `docs/Intentional_Deviations/hot/api.md:33` 与 `:421` 仍是 **⏳ 未签字** ⇒ 「未签字候选不因位于 IntDev 目录就归 D」⇒ ⇒ **剩余实现面 = 阶段二要么用工程手段让超长请求行也产出 Go 的 431 ＋ `text/plain`，要么走完签字流程** ⇒ **不阻断阶段一** | 2026-10-30 |

### lane W7

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-HOTAPI-027 | D | P3 | 否 | Go `go-zero@v1.10.2/rest/engine.go:145-147`（**路由循环内** `chn.Append(handler.MaxConnsHandler(ng.conf.MaxConns))` ⇒ 每条路由各建一个）＋ `rest/handler/maxconnshandler.go:20`（`latch := syncx.NewLimit(n)` 在 handler 闭包**内部** ⇒ **per-route**）＋ `rest/config.go:52`（`MaxConns int json:",default=10000"`）。RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:102`（`private final Semaphore latch;`）＋ `:104-106`（**构造期建一次** ⇒ **全进程一个** = global）＋ `service/hot/hot-api/src/main/resources/application.yml:143`（`max-conns: 10000`）⇒ ⇒ 「Go per-route / RAG global」这个**行为差异仍成立**（且默认阈值 10000 下 RAG 侧结构性不可达：Tomcat `max-threads` 默认 200）。**D**：已由 `docs/Intentional_Deviations/global/common-tails.md` 的 **`SD-2026-10-25-01`**（✅ 仍成立，明写覆盖 T-2（follow `DRIFT-FOLLOW-008`）· T-5（hot `D27`）两条同一件事）**签字** ⇒ **决定 = 保留 RAG 的全局闸门，不改成 per-route** ⇒ 是决策不是缺陷 | 2026-10-30 |
