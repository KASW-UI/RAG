# DRIFT 台账 · `service/hot/hot-api`

> ⚠️ **本文件此前的全部内容只有一句否定式断言**（「13 个文件全是空骨架」）。
> 2026-10-23 该模块完成首次实现并做了首轮 Go ↔ RAG 对照，本文件已整体重写。
> 2026-10-23 第二轮（组件级验证，见下文第 11~13 节）在此基础上**增量追加**，历史条目与编号一条未删。

**核对日期**：2026-10-24（第五轮，U1/D1 端到端双侧活体对拍）
**真源**：`Sea-RideTheWind_golang/service/hot/api` @ go-zero **v1.10.2**（`go.mod:17`，已核对 module cache 里同时存在 v1.10.0/v1.10.3，**本文件所有行号一律以 v1.10.2 为准**）
**本轮证据强度**（按 AGENTS.md §3.3 口径）：

| 档 | 本轮用到的 | 说明 |
|---|---|---|
| ✅ **双侧活体对拍** | **29 条端到端用例 + 输入上限探针** | 两套 HTTP 栈**同时拉起**，逐字节比**响应体 + 关键响应头**；另有**下游调用序列**比对。详见「第五轮」一节 |
| ✅ **Go 侧活体探针** | 15 条 form 解析用例 + 25 条分页/归一化用例 + 3 组 wire 字节 | 直接调用真源 `httpx`/`jsonx` 与逐字复制的真实 struct，**跑起来了**（第二轮） |
| ✅ **RAG 侧真实上下文探针** | 同矩阵 MockMvc + `@SpringBootTest` 真 Boot 上下文 | 复用**项目真实的** mapper / advice / controller，非自建（第二轮） |
| ✅ **双侧差分** | 分页/归一化 25 行 `diff` 输出为空 | 两侧同输入同函数，逐行相同（第二轮） |
| ⚠️ **静态源码** | 保护层四闸、breaker | 两侧都读了消费点，但**无活体观测**（D9/U5 仍未做） |
| ❌ **真 PG / 真 Redis / 真 Kafka** | 不适用 | 本组件无 DB、无 Redis、无 MQ、无事务、无缓存。⚠️ 本轮下游是**桩** ⇒ **hot-rpc 自身**的行为不在覆盖范围 |
| ❌ **变异验证** | 仍未做（系统级） | 已对**新增的修复**做定向变异；见 D2 |

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
| Java 侧行为是否有断言钉住 | ✅ 是（59 例，含真实 MVC 管线 + 真实 stub 捕获） |
| **双侧活体对拍**（Go 栈真跑起来逐项比对） | ✅ **已做**（2026-10-24 第五轮，29 条端到端用例）—— **但查出 4 条新漂移**（D21~D24），且**推翻了第二轮 4 条 ✅ 结论** |
| **变异验证**（证明测试不是假绿） | ❌ **未做** |
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
| 18 | gRPC **2s** per-call deadline | `zrpc/config.go:31` → `timeoutinterceptor.go:20-25` | `GoStyleGrpcCallDeadline`（min(2s, 请求剩余预算)） | ✅ 读真实 `CallOptions.getDeadline()` |
| 19 | **不加 retry** | `zrpc.RpcClientConf` 无 `Retries` 字段 | 无 retry 配置 | ✅ 读到配置定义 |
| 20 | client 熔断默认开启 | `zrpc/internal/config.go:14` | `com.rag.common.protection` 全局拦截器 | ⚠️ **静态**：扫到 `com.rag.common` 即生效，未做活体验证 |
| 21 | HTTP **3s** 超时 | `rest/config.go:55`（tag 默认，yaml 未写） | `observability.http-timeout: 3s` | ⚠️ **未活体验证**：未观测到真实 503 + `Request Timeout` |
| 22 | 请求保护层四闸默认开启 | `rest/config.go:12-22,52-56` | `rag.protection.*`（1 MiB / 10000 / 900 / breaker） | ⚠️ **未活体验证**：未构造超限请求 |
| 23 | 5 个指标名 + 标签字面量 | `metrics/metrics.go:13-43` | `HotApiMetrics` | ✅ Java 侧断言；⚠️ Prometheus 实际导出**未验** |
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
| D2 | **变异验证** | ❌ 未做（2026-10-23） | 测试是否假绿未证实。⚠️ 第二轮已**间接证实一处假绿**（DRIFT-HOTAPI-015-B 的裸 mapper），但**系统变异仍未做** |
| D3 | 客户端**断连**取消不再传播 | ⚠️ 已留痕（2026-10-23） | Go 把请求 ctx 传进 100 次补全，Servlet 断连不会自动取消在飞的 gRPC。已用「请求线程取一次预算 + 显式下传」保住**超时**语义，**断连**语义不等价 |
| D4 | Go panic 时指标误记为 `success` | ⚠️ 有意差异（2026-10-23） | Go 的 `defer` 读命名返回值 `err`，panic 展开时仍为 nil；Java 按客户端实际看到的结果记 `fail`。该路径无代码可达 |
| D5 | 指标**暴露**是单向增强 | ⚠️ 有意偏离（2026-10-23） | Go 的 5 个指标注册了但**无端点暴露**（yaml 的 Prometheus 块无 Host；`DevServer` 块在 go-zero v1.10.2 无对应字段）。分桶边界已对齐回 `prometheus.DefBuckets` |
| D6 | **端口** | ⏳ 待拍板（2026-10-23） | Go `hotcenter.yaml` = 8893；`Infrastructure.md §3` 给 hot 预占 18897。当前按 18897 落，`HOT_API_PORT` 可覆盖 |
| D7 | GET + JSON body 覆盖 query | ✅ **2026-10-23 复核：原结论修正 —— 这不是漂移** | **原文保留**：「Go 的 `httpx.Parse` 还会跑 `ParseJsonBody`。极冷边界，未承诺一致」。<br>**修正依据**：`core/mapping/utils.go:589-597` `usingDifferentKeys(key, field)` —— 字段有 `form` tag 但**没有 `json` tag** ⇒ JSON unmarshaler（`jsonTagKey`）**整字段跳过**（`unmarshaler.go:571-573` 直接 `return nil`）。<br>**Go 侧活体探针实测**：`?page=5` + body `{"page":9,"page_size":3}` → **`page=5 pageSize=20`**；无 query + body → **`1/20`**；POST + body → **`1/20`**。JSON body **完全被忽略**。<br>⇒ RAG「不读 body」与 Go 行为**一致**，原记的「未实现」是**假阳性**。真漂移另有其人，见 **DRIFT-HOTAPI-013**（畸形 JSON body）。 |
| D8 | client 熔断实际生效 | ⚠️ 静态确认（2026-10-23） | 依赖「扫到 `com.rag.common`」+ `@GrpcGlobalClientInterceptor`，按 §3.3 **不得**据注解存在即断言生效 |
| D9 | 3s 超时 / 保护层四闸实际触发 | ⚠️ 静态确认（2026-10-23） | 同上，需活体观测（503 + `Request Timeout` / 超限裸状态码）。⚠️ 第二轮补了一条**静态可确证的反例**：超时/499 响应头少 `nosniff`（DRIFT-HOTAPI-014） |
| D10 | `management.metrics.distribution.slo` 不可用 | ⚠️ 已实测（2026-10-23） | Boot 的 `ServiceLevelObjectiveBoundary` 转换器解析不了小数秒（`2.5s` → `NumberFormatException`），且会让整个 `MetricsProperties` 绑定失败 ⇒ **上下文起不来**。SLO 边界改在 `HotApiMetrics` 代码侧声明 |

### 🆕 第二轮新增（D11 起接续编号，无冲突）

| # | 项 | 类别 | 状态 |
|---|---|---|---|
| **D11** | **重复 query 参数 → Go 取首值，RAG 拼成 `"首,次"` 后整包 5001** | **SEMANTIC MISMATCH** | 🔴 需修（真 Boot 上下文已复现） |
| **D12** | form 参数总数上限 **2048** | OBSERVABLE DIFFERENCE | ✅ **已实现**（2026-10-24 复核：`GoFormValues.requireFormValueCountWithinLimit` + `HotApiGoParseParityTest` 的 2048/2049 边界用例；⚠️ 本行原记「RAG 侧不存在 / 需拍板」**是错的** —— 实现与测试都在，只是台账没回填） |
| **D13** | **畸形 JSON body** → Go 拒绝 5001，RAG 完全忽略 body | OBSERVABLE DIFFERENCE | 🟡 需拍板 |
| **D14** | 超时 / 499 响应缺 `X-Content-Type-Options: nosniff` | OBSERVABLE DIFFERENCE | ✅ **活体取证完成**（2026-10-24 第五轮）：真栈下 Go 的 503 带 `nosniff`、RAG **不带** ⇒ **确认是漂移**（不是静态推断）。代码在 `service/common` ⇒ **已移交 common 域** |
| **D15** | 运行时 JSON **字段顺序**与 Go 声明序不同（裸 mapper 测不出来） | OBSERVABLE DIFFERENCE | 🟡 已知取舍，需登记 |
| **D16** | **业务错误日志的级别 / 字段集 / `error_reason` 形态**与 Go 不同（nil 响应分支最严重：Go 是 **error 级 + 9 字段**，RAG 是 **info 级 + 0 字段**） | OBSERVABLE DIFFERENCE | ✅ **已修**（2026-10-24 第四轮 ⇒ `DRIFT-HOTAPI-016`；3 条变异全红） |
| **D17** | 下游 gRPC 调用失败时，日志 `error_reason` 是**各自 gRPC 库**的形态（grpc-go `"rpc error: code = Unavailable desc = …"` vs grpc-java `"UNAVAILABLE: …"`，**code 名大小写都不同**） | OBSERVABLE DIFFERENCE | 🆕 **待签字**（**库层**差异 ⇒ 建议「不复刻」） |
| **D18** | Prometheus `_bucket{le=}` 边界序列：Go **12 个**（`DefBuckets` 11 + `+Inf`）vs RAG **80 个**（`publishPercentileHistogram()` 塞进了 69 个 exponential 边界） | OBSERVABLE DIFFERENCE | ✅ **已修**（2026-10-24 第四轮 ⇒ `DRIFT-HOTAPI-018`；去掉 `publishPercentileHistogram()`；3 条变异全红） |
| **D19** | Prometheus 全局标签 `application="hot-api"`（Go 的默认 registry **无**此标签） | OBSERVABLE DIFFERENCE | 🆕 **待决策**（**全仓约定**，跨域 ⇒ hot 域无权单改 ⇒ `DRIFT-HOTAPI-019`） |
| **D20** | hot-api **注册到 Nacos**（Go 侧 hot-api **完全不注册**）⇒ 且实测「Nacos 不可达 ⇒ 整个进程起不来」 | OBSERVABLE DIFFERENCE | 🆕 **待决策**（**部署面** ⇒ `DRIFT-HOTAPI-020`） |

### 🆕 第五轮新增（2026-10-24 U1/D1 端到端对拍，D21 起接续编号）

| # | 项 | 类别 | 状态 |
|---|---|---|---|
| **D21** | **Tomcat 请求目标字符校验**拒绝 query 里的 `[` `]` ⇒ `?page[]=5` **Go 200 / RAG 400 + Tomcat HTML 错误页** | **SEMANTIC MISMATCH** | 🔴 **需修（跨域）** ⇒ `DRIFT-HOTAPI-021`。⚠️ **第二轮 CASE-04b/04c 的 ✅ 结论被推翻**（MockMvc 绕过 Tomcat 的请求行校验） |
| **D22** | **form 参数个数上限**：Go **2048**（应用层）vs RAG **1000**（Tomcat `maxParameterCount`）⇒ 1001~2048 区间 Go 成功 / RAG 5001 | OBSERVABLE DIFFERENCE | 🔴 **需修（跨域）** ⇒ `DRIFT-HOTAPI-022`。⇒ **RAG 侧自己实现的 2048 上限在 GET query 上不可达**（Tomcat 先拒） |
| **D23** | **请求行长上限**：Go `net/http` **1 MiB** vs RAG Tomcat **8 KiB** ⇒ 8 KiB~1 MiB 的 query：Go 处理 / RAG **400 + Tomcat HTML 错误页**（不是业务信封） | OBSERVABLE DIFFERENCE | 🔴 **需修（跨域）** ⇒ `DRIFT-HOTAPI-023` |
| **D24** | `Content-Type` 媒体类型**参数**：Go `application/json; charset=utf-8`（**分号后有空格**）vs RAG `application/json`（无 charset） | OBSERVABLE DIFFERENCE | 🆕 **待签字** ⇒ `DRIFT-HOTAPI-024`（⚠️ Spring 的 `MediaType.toString()` 产出 `;` 后**无空格** ⇒ 即使显式补 charset 也**不可逐字节复刻** ⇒ 建议「不复刻」） |

---

## 与已完成的 hot 域两组件的关系

| 组件 | 状态 | 台账 |
|---|---|---|
| `hot-common` | ✅ 双侧对拍 sha256 逐字节一致 | [`common.md`](common.md) |
| `hot-rpc` | ✅ 双侧对拍逐字节一致，46 条契约测试 | [`rpc.md`](rpc.md) |
| **`hot-api`** | ⚠️ **已实现**（16 文件 / 2,088 行 / 59 例绿）+ **解析层/分页层/wire 层已双侧探针对拍**（15+25+3 组），**端到端 HTTP 栈未对拍** | **本文件** |

⇒ hot 域三组件**代码均已落地**；`hot-api` 是其中**证据强度最弱**的一个
（另两个是双侧对拍逐字节一致，它是「双侧探针（分层）+ Java 侧单测」，缺端到端）。

## 尚未处理

- 本模块的 `HUMAN_ONLY.MD`
- D1（端到端双侧活体对拍）—— **这是 hot 域收口的最后一块实质工作**

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

External Calls: 同 Go，但 deadline 由 GoStyleGrpcCallDeadline 显式施加（ThreadLocal 不跨线程池）
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
| GET | client breaker | 默认开 | 全局拦截器 | **UNKNOWN** | 静态；未活体验证（D8） |
| GET | HTTP 超时时长 | 3s | 3s | EQUIVALENT | 静态 |
| GET | 超时出口状态码/体 | 503 + `Request Timeout\n` | 503 + 同体 | EQUIVALENT | 静态；未活体观测（D9） |
| GET | **超时/499 响应头 nosniff** | **有** | **无** | **OBSERVABLE DIFFERENCE** | 静态双侧 |
| GET | 断连 499 | `errors.Is(Canceled)`→499 | IOException→499 | **UNKNOWN** | 静态；需活体（D3） |
| GET | 保护层四闸 | 默认开（tag） | `rag.protection.*` | **UNKNOWN** | 静态；需活体（D9） |
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
             Java min(2s, 请求剩余预算)（GoStyleGrpcCallDeadline）
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
Java:  每请求 new Semaphore(8)，**先 acquire 再 submit** 到 32 共享线程池（4 请求 × 8）
       ⇒ 每请求同样享有完整 8 在途；results 按下标落位
差异:  Go 的 goroutine 廉价、可无限等待；Java 的 32 池 + 64 队列 + CallerRunsPolicy 会产生
       「池满时由请求线程自己跑」的背压 ⇒ 极端并发下 Java 的**单请求延迟分布**与 Go 不同
       （Go 是纯排队，Java 可能在请求线程上串行执行补全）
```
⇒ 常态 EQUIVALENT；**极端并发下 OBSERVABLE DIFFERENCE（延迟分布，非结果）**。
并发峰值 ==8 已由 RAG 侧断言（证据：纯 Mockito + 真实 Semaphore，非双侧）。记为 U10。

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
| CASE-04b 重复（数组记法） | `?page[]=5&page[]=9` | 5 / 20 | 5 / 20 | 🔴 **被第五轮推翻** ⇒ 真栈下 RAG 是 **400 + Tomcat HTML 错误页**（`DRIFT-HOTAPI-021`）。⚠️ 原 ✅ 是 **MockMvc 测的** |
| CASE-04c 单值数组记法 | `?page[]=5` | 5 / 20 | 5 / 20 | 🔴 **被第五轮推翻** ⇒ 同上 |
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
| CASE-17 参数上限 | 2048 / 2049 个 form 值 | OK / **5001** | OK / **5001**（阈值 1000 ⇒ 1001 起就 5001） | 🔴 **阈值不同**（`DRIFT-HOTAPI-022`）。⚠️ 第二轮原写「RAG: OK / OK」**是错的** |
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
> 3. **CASE-15d / CASE-19 / CASE-20 升级为「活体实测同」** ⇒ 第五轮真栈逐字节确认。
> 4. **CASE-06 的超时分支已活体触发** ⇒ 两侧状态码与 body 逐字节相同，**只差 `nosniff`**（D14）。

## 9. Unresolved（§37）

> 按 §31-a：**没有实际读过两边源码得出的结论不得写成 SEMANTIC MISMATCH**。以下逐条说明缺什么证据。

| # | Question | Go Evidence | Java Evidence | Why Unknown | 需要什么活体证据 |
|---|---|---|---|---|---|
| **U1** | 端到端 HTTP 栈两侧是否逐字节一致？ | `hotcenter.go` 全链路已读 | 真 Boot 上下文已跑 | ⚠️ **已闭合（第五轮）**：两套栈**同时拉起**（Go hot-api + RAG hot-api + 下游同构双桩），29 条用例逐字节比**响应体 + 关键响应头** + 期望值 + **下游调用序列** ⇒ 默认 **24/29**，带修正配方 **28/29**。⇒ **查出 4 条新漂移**（D21~D24），并**推翻第二轮 4 条 ✅ 结论** | ✅ **已完成**（`tools/difftest/run-hot.sh` + `diff-hot.py`；详见「第五轮」一节） |
| **U2** | 3s 超时真的产出 `503 + Request Timeout`？ | `http.go:88` + `rest/config.go:55` | `HttpMiddleware:419-425` | ⚠️ **已闭合（第五轮）**：`CASE-21` 用「20 条热榜 + article-rpc 睡 30s」把请求**真的拖过 3s** ⇒ 两侧**都**产出 `HTTP 503 + "Request Timeout\n"`（**body 逐字节相同**）。⚠️ 唯一差异 = **缺 `X-Content-Type-Options: nosniff`** ⇒ 即 **D14**（代码在 `service/common` ⇒ **已移交 common 域**） | ✅ **已完成**（第五轮 `CASE-21`） |
| **U3** | 客户端断连是否真产出 499，且 gRPC 真的被取消？ | `http.go:84-86` `errors.Is(Canceled)`→499 | `HttpMiddleware:332` IOException→499 | 静态两侧一致，但 Servlet 断连**不会**自动取消在飞的阻塞 gRPC（D3 已留痕） | curl `--max-time 0.5` 撞慢下游，抓 499 + PG/服务端观察 gRPC 是否被取消 |
| **U4** | client breaker 是否真在链上并会因超时打开？ | `zrpc/internal/client.go:112-113` + `breakerinterceptor.go:16-18` | 依赖「扫到 `com.rag.common`」+ `@GrpcGlobalClientInterceptor` | ⚠️ **部分闭合（第四轮）**：**装配层已补**（hot-api 此前**没有** wiring 测试 —— 而 `follow-api` / `message-api` 各有一份 ⇒ 正是本域缺口）⇒ 新增 `GoStyleGrpcClientBreakerWiringTest`（3 例：拦截器在 starter 的 `GlobalClientInterceptorRegistry` 里 + factory 认识本域两个 `@GrpcClient` 锚点 + 熔断默认开启）。⚠️ **「熔断真的打开」的行为级证据仍未做** | 决定 DRIFT-HOTRPC-004 的严重度；也可能暴露 Go 侧独有的 shadow 残留 | **仍需**：内嵌一个恒返 INTERNAL 的 gRPC server，连续调用直到 breaker 打开 ⇒ 观察 `interceptor.trackedMethods()`（参照 `message-api` 的 `GoStyleGrpcClientBreakerE2ETest` 范式）。⚠️ hot-api 侧响应体**看不出**熔断（前后都是 5001）⇒ 只能看拦截器/指标 |
| **U5** | 保护层四闸（MaxConns/MaxBytes/CpuShedding/Breaker）真触发？ | `rest/config.go:12-22,52-56` tag 默认 | `rag.protection.*`（静态同值） | 未构造超限请求（D9） | 并发 10001 连接 / 发送 >1 MiB body / CPU 打满，各观察裸状态码 |
| **U6** | 404 / 405 在**真 Tomcat** 下的响应头与 body 是否逐字节同？ | `http.Error` 四行（server.go:2337-2356） | MockMvc 实测：`404 page not found\n` + text/plain + nosniff；405 无 body | ✅ **已闭合（第四轮）**：**真 Tomcat**（`RANDOM_PORT` + JDK `HttpClient`）⇒ 404 = `404 page not found\n` + `text/plain` + **`nosniff`**；405 = 裸状态码 + `Allow`、**无 body** | ✅ 已完成（`HotApiRealTomcatGateTest` 的 `#notFoundIsBareGoStyleResponse` / `#methodNotAllowedIsBareStatusCode`） |
| **U7** | `TopK=100` 被 hot-rpc 如何解释？items 是否按热度降序？ | `logic:62-64` 硬编码 100；`hot.proto:10` 注释「默认 20，最大 100」 | `HotRpcClient:63` 硬编码 100 | **本轮按简报要求未假设对端 rpc 正确**；这属 hot-rpc 组件 | **必须与 [`hot/rpc.md`](rpc.md) 合并判读**：确认 hot-rpc 是否 clamp 到 100、是否排序、是否分页 |
| **U8** | percent-encoding 在真 Tomcat 下的绑定值 | `url.ParseQuery` 解 `%2B`→`+` ⇒ 5 | MockMvc **不解码**（`%2B5` 原样传入）；但 `.param("page","+5")` ⇒ 5 | ✅ **已闭合（第四轮）**：**真 Tomcat** 下 `%2B5` ⇒ 解码成 `+5` ⇒ `Integer.parseInt("+5")` 接受前导 `+` ⇒ **`page=5`**，与 Go 的 `strconv.ParseInt("+5")` **等价**（两侧都接受前导 `+`）⇒ **不需要改 `GoFormValues`**（见 Pitfall P4） | ✅ 已完成（`HotApiRealTomcatGateTest#percentEncodedPlusIsDecodedAndAccepted`） |
| **U9** | 59 条测试是否假绿？ | — | 本轮**间接证实过一处**：DRIFT-HOTAPI-015-B 的裸 mapper 让字段序测试永久无效 | 未做系统变异（D2） | 对 `HotRankPaginator` / `GlobalResponseAdvice` / `GoFormValues` 做定向变异；并把 `HotApiWireContractTest` 换成运行时 mapper 后重跑 |
| **U10** | 极端并发下补全的延迟分布是否可比？ | Go：100 goroutine + 每请求新建 chan(8)，无池上限 | Java：每请求 Semaphore(8) + **32 共享池** + 64 队列 + `CallerRunsPolicy` | 常态窗口都是 8；**池满时 Java 会在请求线程上串行跑补全**，Go 是纯排队 | 8 并发请求 × 100 items 的压测，比 p99 而非成功率；确认没有请求被 `CallerRunsPolicy` 拖成串行 |
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
| 15 | Concurrency semantics 是否一致？ | ⚠️ 部分 | 常态窗口 8 一致；**池背压下延迟分布不同**（U10） |
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
> | `DRIFT-HOTAPI-021` | `?page[]=5` → Go 200 / RAG **400 + Tomcat HTML 错误页** | `server.tomcat.relaxed-query-chars: "[,]"` | 跨域 |
> | `DRIFT-HOTAPI-022` | 值个数上限 Go **2048** / RAG **1000** | `server.tomcat.max-parameter-count: 2048` | 跨域 |
> | `DRIFT-HOTAPI-023` | 请求行长 Go **1 MiB** / RAG **8 KiB** | `server.max-http-request-header-size: 1MB` | 跨域 |
> | `DRIFT-HOTAPI-024` | `Content-Type` 的 charset 参数 | **建议不复刻**（Spring 侧不可逐字节复刻） | 待签字 |
>
> ⇒ 三条容器类差异**同时打开后**：对拍矩阵 **24/29 → 28/29**，输入上限阈值与 Go **完全一致**
> （实测首个翻转 n 两侧都是 **2049**）⇒ **配方已被证实**，只剩 D14（`nosniff`，common 域）。

### ⚠️ 本结论的效力边界（三条，越界即失效）

1. **「NOT EQUIVALENT」只覆盖到本轮实测过的输入面。**
   未跑的输入（超时、断连、超限、breaker、端到端栈）一律在第 9 节，
   **不得**据此推断它们一致或不一致。
2. **「已双侧实测」≠「双侧服务同时跑起来对拍」。**
   本轮是**分层双侧探针**（Go 真库 + RAG 真上下文，同输入逐行比对），
   强度高于源码对照、低于端到端对拍（U1/D1 仍未做）。
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

| ID | 上一轮判定 | 本轮处置 | 证据 |
|---|---|---|---|
| **DRIFT-HOTAPI-011** | 🔴 SEMANTIC MISMATCH | ✅ **已修** | Go 活体探针 + 真实 MVC 管线 4 例 |
| **DRIFT-HOTAPI-015-B** | ⚠️ 证据缺口（测试永久无效） | ✅ **已补** | 新增 `HotApiGoWireOrderTest`（真实上下文 + 原始字节）｜⚠️ 2026-10-24 改号：原编为 012，与「2048 上限」撞车 |
| **DRIFT-HOTAPI-013** | 🟠 OBSERVABLE DIFFERENCE | ✅ **已修** | Go 活体探针 + 真实 MVC 管线 2 例 |
| **DRIFT-HOTAPI-014** | 🟡 OBSERVABLE DIFFERENCE | 🔁 **跨域移交 common 域** | 本域无权改（代码在 `service/common`） |
| **DRIFT-HOTAPI-015** | 🟡 OBSERVABLE DIFFERENCE | ✅ **已修** | `@JsonPropertyOrder` + 真实运行时断言 + 变异变红 |

---

## DRIFT-HOTAPI-011 ✅ 已修 · SEMANTIC MISMATCH · MEDIUM

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
  并额外支持 Go 的 **`[]` 数组记法后缀**（`httpx/util.go:59-61` 剥掉后缀后归到同名 key）。
- **守门测试**：`HotApiGoParseParityTest` 4 例（真实 Boot 上下文 + MockMvc）。

### ⚠️ 本轮发现的一条 **Go 侧非确定性**（新增观察，非 RAG 缺陷）

`?page=1&page[]=2` 这种**同名 + 带后缀混用**时，Go 的 `GetFormValues` 会把两个 key
都写进同一个 `params["page"]`（后者**覆盖**前者），而 `r.Form` 是 map ⇒ **遍历序随机**
⇒ Go 的结果在 `page=1` / `page=2` 之间**随机**（实测 8 次：6 次 2、2 次 1）。
⇒ RAG 取**确定性**的「精确名优先」⇒ 落在 Go 的行为包络内，但**比 Go 更确定**。
⇒ 登记为 [`docs/Intentional_Deviations/hot/api.md`](../../Intentional_Deviations/hot/api.md) `SD-HOT-API-001`。

---

## DRIFT-HOTAPI-015-B ✅ 已补 · 证据缺口（裸 mapper 让字段序测试永久无效）

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

- **Go 行为**：`httpx/requests.go:66-68` 的 `ParseJsonBody` 在 `Parse` 里**无条件**执行；
  `httpx/requests.go:153-155` 的 `withJsonBody(r)` = `r.ContentLength > 0 && strings.Contains(Content-Type, "application/json")`；
  `core/mapping/jsonunmarshaler.go:45-53` 先 `jsonx.UnmarshalFromReader`（**JSON 语法校验**）再按 json tag 填字段。
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

## DRIFT-HOTAPI-014 🔁 跨域移交（common 域）· OBSERVABLE DIFFERENCE · LOW

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

## DRIFT-HOTAPI-017 🆕 待签字（不复刻）· OBSERVABLE DIFFERENCE · LOW（**库层**）

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
- **为什么不修**（建议按有意偏离签字）：
  1. 形态由**两个 gRPC 库各自决定**，不是 hot 域的代码 ⇒ 要复刻就得在 hot-api **手拼** `"rpc error: code = "` 前缀
     并维护一张 code 名大小写映射表 ⇒ **在错误的层**（hot 域凭什么知道 gRPC 错误串的格式？）；
  2. 与 `DRIFT-HOTRPC-004`（Redis 重试）**同一个判断**：把库行为搬进业务层，比差异本身更坏；
  3. **可观察影响极小**：`error_reason` 是**日志自由文本**，码值在两侧都另有结构化 `error_code` 字段承载
     （Go `logger.go:152` / RAG `Logger.java:277`）⇒ 语义信息不丢。
- **⚠️ 与 `DRIFT-HOTAPI-016` 的分界**：`016` 修的是**hot 域自己写的**格式（`errmsg.go:15-17`）⇒ 该修；
  `017` 是**库写的**格式 ⇒ 不该由 hot 域修。
- **Evidence**：【双侧实测：Go 探针 `/tmp/grpc-err-probe/` + grpc-java 直跑】
- **状态**：🆕 **待用户拍板**（建议签字为「不复刻」⇒ 登记 `SD-HOT-API-005`）。

---

## DRIFT-HOTAPI-018 ✅ 已修 · OBSERVABLE DIFFERENCE · MEDIUM（指标分桶边界）

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

## DRIFT-HOTAPI-019 🆕 待决策（全仓级）· OBSERVABLE DIFFERENCE · LOW

- **Component**：Prometheus 全局标签 `application="hot-api"`
- **Go 行为**：`prometheus.MustRegister(...)` 注册到**默认 registry** ⇒ **无任何全局标签**。
- **RAG 行为**：`application.yml` 的 `management.metrics.tags.application: hot-api`
  ⇒ 每个指标都多一个 `application` 标签。
- **⚠️ 这不是 hot 域单点问题**：全仓 **article-api / article-rpc / comment-api / favorite-api /
  favorite-rpc / task-rpc …** 都配了同一项 ⇒ 是**跨域约定** ⇒ 按 §5.3 hot 域**无权单独改**。
- **影响**：低 —— 不改任何业务判定；但按「Go 无此标签」的字面口径，两侧的指标**标签集不同**
  ⇒ 跨实现直接写 PromQL 会对不上（需要先 `sum by(...)` 去掉 `application`）。
- **处置**：🆕 **待用户拍板**。两个选项：
  1. **保持**（全仓一致）⇒ 登记为有意偏离（与 `SD-HOT-API-004` 的「暴露面单向增强」同族）；
  2. **全仓去掉**（跨域动作，需另开一轮）⇒ 与 Go 逐字一致。

---

## DRIFT-HOTAPI-020 🆕 待决策 · OBSERVABLE DIFFERENCE · MEDIUM（**部署面**）

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
- **处置**：🆕 **待用户拍板**。两个选项：
  1. **对齐 Go**：`spring.cloud.nacos.discovery.enabled=false`（或去掉 starter）
     ⇒ 不注册、注册失败也不影响启动 ⇒ 与 Go 逐字一致；
  2. **保持 + 签字**：与 hot-rpc 同口径「注册失败 ⇒ 存活 + 健康检查 NOT_SERVING」
     ⇒ 需**同样补**健康检查（hot-api 侧目前**没有**）。
- ⚠️ 本轮**只登记、不擅改** —— 改 pom / 改部署形态都超出「域内漂移对齐」的范围。

---

## ⚠️ 本轮**未**闭合的项（不得读成「hot-api 已完成」）

| 项 | 状态 | 说明 |
|---|---|---|
| **端到端双侧活体对拍**（D1/U1） | ✅ **本轮已闭合** | 两套栈**同时拉起**（Go hot-api + RAG hot-api + 下游同构双桩），29 条用例逐字节比**响应体 + 关键响应头** + 期望值 + **下游调用序列** ⇒ 默认配置 **24/29 通过**，并**新查出 4 条漂移**（D21~D24）；带修正配方时 **28/29**（剩下的 1 条 = D14，已移交 common 域） |
| **超时 / 保护层四闸的活体观测**（D9/U2/U5） | 🟡 **部分闭合（第五轮）** | **超时分支已活体触发**：`CASE-21` 让请求真的拖过 3s ⇒ 两侧**都**产出 `503 + "Request Timeout\n"`（body 逐字节相同），**只差 `nosniff`**（= D14，跨域移交 common 域）。⚠️ **保护层四闸**（MaxConns / MaxBytes / CpuShedding / Breaker）**仍未构造超限请求** ⇒ 仍是静态确认 |
| **变异验证**（D2/U9） | 🟡 部分 | 已对**新增的 6 条修复**做了定向变异（hash 5/5、字段序 1/1、日志面 3/3、bucket 边界 3/3）；**全组件系统变异仍未做** |
| **端口归属**（D6） | ⏳ 待拍板 | Go `hotcenter.yaml` = 8893；`Infrastructure.md §3` 给 hot 预占 18897。当前按 18897 落 |
| **下游 gRPC 错误串形态**（DRIFT-HOTAPI-017） | 🆕 **待签字** | 库层差异（grpc-go `"rpc error: code = Unavailable desc = …"` vs grpc-java `"UNAVAILABLE: …"`）⇒ 建议签字「不复刻」 |
| **Prometheus 分桶边界**（DRIFT-HOTAPI-018） | ✅ **本轮已闭合** | 真 Actuator 实测：`publishPercentileHistogram()` 会塞进 69 个 exponential 边界 ⇒ 实测 80 个 vs Go 的 12 个 ⇒ **已修**，3 条变异全红 |
| **真 Tomcat 响应头 / percent-encoding**（U6/U8/D14） | ✅ **本轮已闭合** | 真 Tomcat（RANDOM_PORT）下：404 = `404 page not found\n` + `text/plain` + `nosniff`；405 = 裸状态码 + `Allow`、无 body；`%2B5` ⇒ 解码成 `+5` ⇒ `page=5`（两侧等价）。⚠️ D14 本身仍**跨域移交** common 域（那是超时/499 路径，不是 404/405） |
| **Prometheus 导出形态**（U11） | ✅ **本轮已闭合** | 真 `/actuator/prometheus`：Counter 名 `hot_api_request_total`（**两侧都是单 `_total`**，原注释的「双 `_total`」说法已被实测推翻并订正） |
| **client breaker 装配层**（U4/D8） | 🟡 **部分闭合** | hot-api 此前**没有** wiring 测试（而 `follow-api` / `message-api` 各有一份）⇒ 本轮新增 `GoStyleGrpcClientBreakerWiringTest`（3 例）+ 变异 M-W1 变红。⚠️ **「熔断真的打开」的行为级证据仍未做**（需内嵌恒返 INTERNAL 的 gRPC server，参照 message-api 的 E2E 范式） |
| **全局 `application` 标签**（DRIFT-HOTAPI-019） | 🆕 **待决策** | Go 的默认 registry 无此标签；RAG 有 ⇒ **全仓约定**（跨域）⇒ hot 域无权单改 |
| **Nacos 服务注册**（DRIFT-HOTAPI-020） | 🆕 **待决策** | Go 侧 hot-api **完全不注册**；RAG 注册 ⇒ 且实测「Nacos 不可达 ⇒ 启动失败」 |
| **日志面已对齐的部分** | ✅ **本轮闭合** | `DRIFT-HOTAPI-016` 已修（`CodeError.Error()` 整串 + 日志级别 + 字段集），3 条变异全红 |

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

### DRIFT-HOTAPI-021 —— Tomcat 请求目标字符校验拒绝 `?page[]=5` 🔴 SEMANTIC MISMATCH（跨域）

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

### DRIFT-HOTAPI-022 —— form 参数个数上限：Go 2048 vs RAG 1000 🔴 OBSERVABLE DIFFERENCE（跨域）

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

### DRIFT-HOTAPI-023 —— 请求行长上限：Go 1 MiB vs RAG 8 KiB 🔴 OBSERVABLE DIFFERENCE（跨域）

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

### DRIFT-HOTAPI-024 —— `Content-Type` 的 charset 参数 🆕 待签字（不复刻）

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
  # 对齐 Go net/http 的 MaxHeaderBytes 默认 1 MiB（Boot 4 默认 8KB）
  max-http-request-header-size: 1MB
  tomcat:
    # 对齐 go-zero httpx/util.go:15 的 maxFormParamCount = 2048（Boot 4 默认 1000）
    max-parameter-count: 2048
    # 允许 query 里的 [ ]（Go 的 net/http 允许；Boot 4 默认拒绝 ⇒ 400）
    # ⚠️ 该属性是 List<Character> ⇒ YAML 里要写成列表，不能写字符串
    relaxed-query-chars: "[,]"
```

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

| 项 | 状态 | 说明 |
|---|---|---|
| **hot-rpc 自身的端到端对拍** | ❌ 未做 | 本轮下游是桩 ⇒ hot-rpc 的行为（Kafka 消费、Redis 快照、TopK 语义）**不在覆盖范围** ⇒ 属 `hot/rpc.md` 的 D1 |
| **超时 / 保护层四闸的活体观测**（D9/U2/U5） | 🟡 部分 | `CASE-21` 已让 3s 超时**真的触发**（两侧都产出 `503 + Request Timeout\n`）⇒ **D9 的「超时分支」有活体证据了**；但「超时响应头缺 nosniff」= D14，**跨域移交 common 域**；保护层四闸仍未构造超限请求 |
| **客户端断连 499**（D3/U3） | ❌ 未做 | 需要 `curl --max-time` 撞慢下游；本轮未构造 |
| **变异验证**（D2/U9） | 🟡 部分 | 已对**新增的修复**做过定向变异；**全组件系统变异仍未做** |
| **Go 侧 `hot.api` 的 `Log.Mode` 等配置项** | ✅ 已对齐副本 | 真源 yaml 零改动（纪律 1 自检通过：跑完 `git status --porcelain -uno` 为空） |

