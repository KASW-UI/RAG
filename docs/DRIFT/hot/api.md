# DRIFT 台账 · `service/hot/hot-api`

> ⚠️ **本文件此前的全部内容只有一句否定式断言**（「13 个文件全是空骨架」）。
> 2026-10-23 该模块完成首次实现并做了首轮 Go ↔ RAG 对照，本文件已整体重写。

## 2026-10-23 首轮实现 + 首轮语义对照

| 项 | 实测值 |
|---|---|
| 主源码 | **16 个 Java 文件 / 2,088 行**（此前 13 个文件 / 每份 11~12 行） |
| 测试 | **8 个测试类 / 59 例，全绿** |
| 构建 | `mvn -pl service/hot/hot-api package` → **BUILD SUCCESS** |
| 结论 | ⚠️ **不是「已验证无漂移」** —— 见下节 |

### ⚠️ 结论的准确措辞（勿简化为「已对齐」）

| 维度 | 状态 |
|---|---|
| 代码是否落地 | ✅ 是 |
| Go ↔ RAG **源码级**对照是否做过 | ✅ 是（逐行读 Go 到控制流级，非只贴声明行） |
| Java 侧行为是否有断言钉住 | ✅ 是（59 例，含真实 MVC 管线 + 真实 stub 捕获） |
| **双侧活体对拍**（Go 栈真跑起来逐项比对） | ❌ **未做** —— Go 侧依赖 `etcd:2379` + 本机 Nacos，本轮未拉起 |
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
| D1 | **双侧活体对拍** | ❌ 未做（2026-10-23） | Go 侧依赖 `etcd:2379` + Nacos；**优先级高于再加单测** |
| D2 | **变异验证** | ❌ 未做（2026-10-23） | 测试是否假绿未证实 |
| D3 | 客户端**断连**取消不再传播 | ⚠️ 已留痕（2026-10-23） | Go 把请求 ctx 传进 100 次补全，Servlet 断连不会自动取消在飞的 gRPC。已用「请求线程取一次预算 + 显式下传」保住**超时**语义，**断连**语义不等价 |
| D4 | Go panic 时指标误记为 `success` | ⚠️ 有意差异（2026-10-23） | Go 的 `defer` 读命名返回值 `err`，panic 展开时仍为 nil；Java 按客户端实际看到的结果记 `fail`。该路径无代码可达 |
| D5 | 指标**暴露**是单向增强 | ⚠️ 有意偏离（2026-10-23） | Go 的 5 个指标注册了但**无端点暴露**（yaml 的 Prometheus 块无 Host；`DevServer` 块在 go-zero v1.10.2 无对应字段）。分桶边界已对齐回 `prometheus.DefBuckets` |
| D6 | **端口** | ⏳ 待拍板（2026-10-23） | Go `hotcenter.yaml` = 8893；`Infrastructure.md §3` 给 hot 预占 18897。当前按 18897 落，`HOT_API_PORT` 可覆盖 |
| D7 | GET + JSON body 覆盖 query | ❌ 未实现（2026-10-23） | Go 的 `httpx.Parse` 还会跑 `ParseJsonBody`。极冷边界，未承诺一致 |
| D8 | client 熔断实际生效 | ⚠️ 静态确认（2026-10-23） | 依赖「扫到 `com.rag.common`」+ `@GrpcGlobalClientInterceptor`，按 §3.3 **不得**据注解存在即断言生效 |
| D9 | 3s 超时 / 保护层四闸实际触发 | ⚠️ 静态确认（2026-10-23） | 同上，需活体观测（503 + `Request Timeout` / 超限裸状态码） |
| D10 | `management.metrics.distribution.slo` 不可用 | ⚠️ 已实测（2026-10-23） | Boot 的 `ServiceLevelObjectiveBoundary` 转换器解析不了小数秒（`2.5s` → `NumberFormatException`），且会让整个 `MetricsProperties` 绑定失败 ⇒ **上下文起不来**。SLO 边界改在 `HotApiMetrics` 代码侧声明 |

---

## 与已完成的 hot 域两组件的关系

| 组件 | 状态 | 台账 |
|---|---|---|
| `hot-common` | ✅ 双侧对拍 sha256 逐字节一致 | [`common.md`](common.md) |
| `hot-rpc` | ✅ 双侧对拍逐字节一致，46 条契约测试 | [`rpc.md`](rpc.md) |
| **`hot-api`** | ⚠️ **已实现**（16 文件 / 2,088 行 / 59 例绿）+ **源码级对照完成**，**无双侧活体对拍** | **本文件** |

⇒ hot 域三组件**代码均已落地**；`hot-api` 是其中**证据强度最弱**的一个
（另两个是双侧对拍逐字节一致，它是「源码对照 + Java 侧单测」）。

## 尚未处理

- 本模块的 `HUMAN_ONLY.MD`
- D1（双侧活体对拍）—— **这是 hot 域收口的最后一块实质工作**