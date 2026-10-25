# L5 · `align/global-signoff` —— C 类登记 + T-7/T-11/T-12 定性 + A6 订正

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的一堆问题** —— 用户既定偏好：**登记类事项登记完就躺着**，
> 不升级为拍板。只有**真正需要决策才能往下走**的事项才写进「需拍板」清单（**最多 1~2 条**）。

## 一、目标

把 `global` 域待办里**全部「登记类」**做干净，并给 **T-11 / T-12** 定性。

## 二、任务清单

### A. 补齐缺失的签字候选条目（`docs/Intentional_Deviations/global/`）

| 编号 | 内容 | 出处 |
|---|---|---|
| **C2** | **T-7**（状态行缺 reason phrase，落点 Tomcat `Http11OutputBuffer`）⇒ **框架限制** ⇒ ⚠️ 台账明写「**尚未登记候选条目** ⇒ 需补」 | `docs/DRIFT/global/common-tails.md` §一·T-7 |
| **C1** | `SD-2026-10-25-CAND-10`（hot 域 `DRIFT-HOTAPI-031`：超长请求行 ⇒ RAG 400+HTML / Go 431+`text/plain`）⇒ 框架限制 | `docs/DRIFT/global/_README.md` §七之二 C1 |
| **C3** | users 域 **8 处 `DEFAULT CURRENT_TIMESTAMP`**（Go 无）⇒ **刻意保留**（同 message 域 `SD-2026-10-23-06` 已签的同形态） | `_README.md` §七之二 C3 |
| **C4** | `users_user.status` 可空性（Go 可空 / RAG `NOT NULL`）⇒ **RAG 更严** ⇒ 同 C3 族 | `_README.md` §七之二 C4 |

⚠️ **每条签字候选必须先自己复核前提**（两侧**确实**不同）——
已发生过「7 条里 1 条前提根本不成立 ⇒ 登记错了」的事故 ⇒ **前提不成立的** ⇒ **不登记**，写明为什么不成立。

### B. T-11 / T-12 定性（§一 · 两条都是第十九轮新登记、**未修**）

| 编号 | 问题 | 要定性成什么 |
|---|---|---|
| **T-11** | trace **导出**链路在 RAG 侧整体缺失（`opentelemetry-sdk-extension-autoconfigure` 不在 ⇒ Boot 的 tracing 自动配置不生效 ⇒ span 建了也无人收；⚠️ exporter 的 jar 是在的，缺的是装配） | 🔴 **先核实 Go 侧到底导不导出 span** —— go-zero 的 `rest/handler/tracehandler.go` 只做 `propagator.Inject`（**注入 header**），**不一定导出** ⇒ 若 Go 也不导出 ⇒ **本条不是漂移** ⇒ 撤销或改写。**先查真源再下结论** |
| **T-12** | 「方法不匹配」这个出口，RAG 比 Go **多带**一个 `Traceparent` | 落点 = **出口层级差异**（Go 在 router 层 / RAG 在 `DispatcherServlet` 内）⇒ 台账已判「共享层改不动 ⇒ 建议签字认账」⇒ 复核后定稿为**签字候选**（或证伪） |

### C. A6 订正（纯文档）

- **B6** like 域 `SD-2026-10-23-09` 的**描述**订正：
  🔴 台账原文「`GoStyleRequestDeadline` 的公开 API 只暴露毫秒」**不成立** ——
  `applyTo(stub)` 就是**纳秒**的（`GoStyleRequestDeadline.java:110-120`）⇒
  真实理由是「**只读** API 只有毫秒粒度」（`LikeRpcClient.java:108-121`：
  `applyTo` 没有「上界」概念 ⇒ 表达不了 `min(上限, 剩余)`）。
- ⛔ `docs/DRIFT/common/alignment-2026-10-23.md:575/:600` 把这条记成了 `SD-2026-10-23-09`
  ⇒ **编号记错**（`-09` 是 worker 池裸 503 那条）⇒ 本 lane **可改**（属 global 写权范围）。

### D. `_README.md` §七之二 的**快照过期**（顺手修）

`_README.md` §七之二 是 **2026-10-25 06:40** 的快照 ⇒ **第十九轮新增的 T-11 / T-12 没进去**。
⇒ ⚠️ 但 `_README.md` 是**共享文件** ⇒ 本 lane **不直接改** ⇒ **把补丁内容写进本文件末尾**，由整合者 fold。

## 三、必读（定向读）

| 读什么 | 在哪 |
|---|---|
| 域纪律 + 待办清单 | `docs/DRIFT/global/_README.md` §〇 / §七 / §七之二 |
| T-7 / T-11 / T-12 | `docs/DRIFT/global/common-tails.md` §一·T-7 / §一·T-11 / §一·T-12 |
| 签字档**既有格式** | `docs/Intentional_Deviations/_INDEX.md` + `docs/Intentional_Deviations/hot/api.md`（`SD-HOT-API-010` 是最近一条样板） |
| 已签字的同形态先例 | `docs/Intentional_Deviations/message/`（`SD-MESSAGE-2026-10-23-06` = `DEFAULT CURRENT_TIMESTAMP` 同形态） |
| Go 真源 | `Sea-RideTheWind_golang/rest/handler/tracehandler.go`（T-11/T-12 的判据）· `Sea-RideTheWind_golang/rest/server.go:58`（404 也带 traceparent） |

## 四、写权范围（白名单）

**只写这些**：
- `docs/Intentional_Deviations/global/**`
- `docs/Pitfalls/global/**`
- `docs/DRIFT/global/lanes/L5-signoff.md`（本文件）

**⛔ 禁区**：
- `service/**`（**本 lane 一行代码都不改** —— 这是文档/登记任务）
- `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md`（**共享文件 ⇒ 写补丁进本文件，等整合者 fold**）
- `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json`
- `docs/*/favorite/`

## 五、交付物

1. `docs/Intentional_Deviations/global/` 下新增/更新若干条**签字候选**条目，格式照既有样板。
2. **T-11 / T-12 的定性结论**（含 Go 出处 `文件:行号`）。
3. **A6 的订正文本**（供整合者 fold 进 `common/alignment-2026-10-23.md` 与 like 域台账）。
4. `_README.md` §七之二 的**补丁**（含 T-11 / T-12 两行）。
5. **需拍板清单（≤2 条）**：只列「不拍板就没法继续」的；其余一律**登记完就躺着**。

## 六、验收标准

- [ ] 每条签字候选的**前提已自己复核**（两侧确实不同）⇒ 不成立的**不登记**，写明原因
- [ ] 每条含：Go 出处 `文件:行号` · 两侧实际差异 · 性质分类（框架限制 / 刻意保留 / 破坏性变更）· 签字状态
- [ ] T-11 的结论**基于 Go 真源的实读**（不是推断）⇒ 若结论是「Go 也不导出 ⇒ 不是漂移」⇒ 明确写出并**撤销**该登记
- [ ] **需拍板清单 ≤ 2 条**
- [ ] 一条 Go 事实都没有「只写结论不带行号」的（§3.3 强制三条之一）

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **登记的前提可能不成立** | 已发生过：7 条里 1 条的前提根本不成立 ⇒ **先复核前提再登记** ⇒ 别把错误前提推给用户签字 |
| **登记 ≠ 待拍板** | 用户明确纠正过：「都进 `docs/Intentional_Deviations/` 了，为什么还要我拍板」⇒ 登记完就躺着 |
| **「Go: …」措辞是高危项** | §3.3.2：以「Go: …」形式写在 `@DisplayName` / javadoc 里的断言，**必须**有 `文件:行号` 支撑消费点，否则**不许用该措辞** |
| **共享文件** | `_README.md` / `common-tails.md` / `cross-domain.md` / `cycle-deps.md` / `AGENTS.md` / `_INDEX.md` ⇒ **本 lane 不写** ⇒ 补丁进本文件 |

---

<!-- 以下为 L5 lane 的产出（第二十六轮）· 协议见 `_PROTOCOL.md` -->

# L5 产出（2026-10-25 第二十六轮）

> **基线**：`d7a126a` · **分支**：`align/global-signoff` · **性质**：**纯文档**（**生产代码零改动**，`service/**` 一行没碰）
> **本 lane 对 favorite 的改动量**：**0 个文件**（favorite 域对 global 零写权，§六）

## 八、A 组 · C 类签字候选 —— 逐条复核前提 + 登记结果

| 编号 | 内容 | **前提复核结论** | 登记结果 | 落点 |
|---|---|---|---|---|
| **C2** | **T-7** 状态行缺 reason phrase | ✅ **成立** —— Go 侧 `net/http/server.go:1602-1605` 写 `StatusText(code)`；RAG 侧 Tomcat `Http11OutputBuffer.java:372-374` **刻意省略** | 🆕 **已登记** `SD-2026-10-25-CAND-14`（**框架限制**） | `docs/Intentional_Deviations/global/common-tails.md` |
| **C1** | `SD-2026-10-25-CAND-10`（hot 域 `DRIFT-HOTAPI-031`） | ✅ **成立**（双侧活体实测，Go 431+`text/plain` / RAG 400+HTML）⇒ **但已登记在别处** | ⛔ **不重复登记** —— 真实记录处 = `docs/Intentional_Deviations/hot/api.md:332`（§5.3b 硬边界 2：一条问题只在一个档落笔；先例 = `SD-2026-10-25-03` 因重复登记被撤销）⇒ **产出「Go 出处补齐」补丁**（见 §十一·①） | 不新建 |
| **C3** | users 域 **8 处** `DEFAULT CURRENT_TIMESTAMP` | ✅ **成立** —— Go 侧 8 处 gorm tag **无** `default`；RAG 侧 8 处 DDL **有**；真 PG 活体对照 **DEFAULT 恰好 8 处差异** | 🆕 **已登记** `SD-2026-10-25-CAND-16`（**刻意保留**） | `docs/Intentional_Deviations/global/users-domain.md`（**新文件**，见 §十三·③） |
| **C4** | `users_user.status` 可空性 | ✅ **成立** —— Go `data_model.go:11` 有 `default:0` 但**无** `not null`；RAG `01_create_user.sql:39` 是 `NOT NULL` | 🆕 **已登记** `SD-2026-10-25-CAND-17`（**RAG 更严**） | 同上 |

### 8.1 🏷 登记后的状态（**全部「候选，建议签」** —— 登记完就躺着，不升级为拍板）

| 编号 | 签字号 | 性质分类 | 状态 |
|---|---|---|---|
| **C2** | `SD-2026-10-25-CAND-14` | 🔴 **框架限制（容器层）** | 🏷 候选，建议签 |
| **T-12** | `SD-2026-10-25-CAND-15` | 🔴 **出口层级差异**（共享层改不动） | 🏷 候选，建议签 |
| **C3** | `SD-2026-10-25-CAND-16` | ✅ **刻意保留**（当前不可观察） | 🏷 候选，建议签 |
| **C4** | `SD-2026-10-25-CAND-17` | ✅ **RAG 更严** | 🏷 候选，建议签 |

⚠️ **编号口径**：`CAND-13` **已回收**（`docs/DRIFT/global/cross-domain.md:492`「编号已回收，勿引用」）⇒ 本轮从 **CAND-14** 起编。

### 8.2 ⚠️ **C3 的「同形态先例」指针在 `_README.md` 里是错的**（⇒ PIT-GLOBAL-022）

`_README.md` §七之二 C3 行写「同 message 域 **`SD-2026-10-23-06`** 已签的同形态」——
**不成立** ⇒ message 域的 `SD-2026-10-23-06`（`docs/Intentional_Deviations/message/rpc.md:141`）讲的是
**`idx_notification_recipient_read` 的列数（Go 1 列 / RAG 2 列）**，与 `DEFAULT CURRENT_TIMESTAMP` 毫无关系。

⇒ **真实的同形态先例**（本轮**已逐条打开核对**）：

| 先例 | 位置 | 形态 | 状态 |
|---|---|---|---|
| `SD-2026-10-24-04` | `docs/Intentional_Deviations/message/rpc.md:80` | 6 个时间列的 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧多出来的（Go 侧无默认） | ✅ 已签字 |
| `SD-2026-10-24-CAND-01` | `docs/Intentional_Deviations/comment/rpc.md:22` | 9 处时间列的 `NOT NULL DEFAULT CURRENT_TIMESTAMP`（RAG 更严） | ⏳ 未签字候选 |

⇒ `SD-2026-10-25-CAND-16` 挂的是**上面这两条**，不是 `_README.md` 原先写的那个 ⇒ 订正补丁见 §十一·③。

## 九、B 组 · T-11 / T-12 定性（**基于 Go 真源实读**）

### 9.1 T-11（trace 导出链路）= ✅ **已修，不是误报** ⇒ **登记不撤销**

**复核结论：Go 侧**确实**导出 span ⇒ RAG 侧此前「导出链路整体缺失」是**真漂移**，**不是**台账幻觉。

| Go 出处（**消费点**） | Go 事实 |
|---|---|
| `go-zero@v1.10.2/core/trace/agent.go:105-132` | `startAgent` 装 `sdktrace.NewTracerProvider(opts...)`（`:126`）+ **`otel.SetTracerProvider(tp)`（`:127`）** ⇒ **全局 provider 真的被装上** |
| `core/trace/agent.go:115-124` | **端点非空**才装 exporter ⇒ `:123` `sdktrace.WithBatcher(exp)` ⇒ **span 真的被导出**到 jaeger |
| `core/trace/agent.go:100-101` | `default: return nil, fmt.Errorf("unknown exporter: %s", c.Batcher)` ⇒ 非法 exporter ⇒ **整个 agent 装不上** |
| `core/trace/agent.go:43-52` | `StartAgent` 用 `sync.Once`（`:48`）⇒ 装不上**只 `logx.Error(err)`（`:50`）继续启动**（不 panic） |
| `core/trace/propagation.go:8-11` | `init()` **无条件**装全局传播器 = `Composite(TraceContext, Baggage)` |

⇒ ⇒ **T-11 的登记不撤销**，改成「**✅ 已修**」，并附**活体对拍**证据：

| 落地轮次 | 产出 | 出处 |
|---|---|---|
| **第二十一轮** | 装**全局** `OpenTelemetry`（`GoStyleOtelAgent` / `GoStyleOtelConfig` / `GoStyleOtelAutoConfiguration`）⇒ **10 条守门 + 4/4 变异全红** | [`lanes/L1-delivery.md`](L1-delivery.md) §九 |
| **第二十三轮** | 补**采样比例**（`Telemetry.Sampler`）+ **batcher**（`Telemetry.Batcher`）校验 ⇒ 关掉 §九 的未证成 ③④ | 同上 §十一 |
| **第二十四轮** | 🆕 **Go 侧活体对拍** —— `tools/semantic-probe/trace/`（Go **包内**探针 + RAG 生产类探针 + 对拍脚本）⇒ **11 个用例 · 8 一致 · 3 已知能力缺口（OTLP-HTTP / Zipkin / file exporter）· 0 条漂移** | 同上 §十二 |

⚠️ **代码侧已落地（可验）**：`service/common/src/main/java/com/rag/common/observability/{GoStyleOtelAgent,GoStyleOtelConfig,GoStyleOtelAutoConfiguration}.java`
+ `tools/semantic-probe/trace/{go/liveprobe_test.go,java/TraceProbe.java,run-trace-parity.sh}`。

⇒ ⇒ **DRIFT 侧 `docs/DRIFT/global/common-tails.md` §一·T-11 的状态行要改**（**共享文件 ⇒ 补丁见 §十一·②**）。

### 9.2 T-12（「方法不匹配」出口 RAG 多带一个 `Traceparent`）= ✅ **定性成立** ⇒ **出口层级差异** ⇒ 签字候选

**复核结论：Go 侧的三条出口，只有两条经过 `TraceHandler`** —— 这**不是**推断，**落到了注册点**：

| Go 出处 | 事实 |
|---|---|
| **`go-zero@v1.10.2/rest/engine.go:113-115`** | `handle := chn.ThenFunc(route.Handler)` ⇒ `router.Handle(route.Method, route.Path, handle)` ⇒ **含 `TraceHandler` 的链是被「注册进 router」的** ⇒ **router 自己生成的 405 根本不经过它** |
| `rest/engine.go:134-138` | `TraceHandler` 是 native middleware 链**第一个**（`:146` MaxConns / `:149` Breaker / `:152` Shedding 都在它之后） |
| `rest/router/patrouter.go:59-83` | 三条出口：`:61-68` 命中 · `:71-75` 路径未注册（404）· **`:77-82` 方法不匹配（405）** |
| `rest/engine.go:245-251` | 出口② 的 404 走 `notFoundHandler`，该处**自建一条以 `handler.TraceHandler(...)`（`:248`）开头的新链** ⇒ **带** traceparent |
| `rest/server.go:58` | `opts = append([]RunOption{WithNotFoundHandler(nil)}, opts...)` ⇒ 每个 api **默认**都装了 notFoundHandler ⇒ **默认 404 也是带 traceparent 的**（易误判点） |
| `rest/handler/tracehandler.go:61` | `propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))` ⇒ **注入点在每路由链内部** |

⇒ ⇒ **定性 = 出口层级差异**（Go 在 router 层 / RAG 在 `DispatcherServlet` 内）
⇒ ⇒ **共享层改不动**（filter 阶段**无法知道**最终会不会落到方法不匹配 —— 那要等 `RequestMappingHandlerMapping` 匹配完，**那时响应已经在写**）
⇒ ⇒ **定为签字候选** `SD-2026-10-25-CAND-15`（**候选，建议签**）。

⚠️ **⚠️ 效应边界（不要越读）**：差异**只**在「方法不匹配」**这一个**出口 ——
「路由命中」与「路径未注册 404」两侧**都带** traceparent ⇒ **别读成「RAG 一律多一个」**。
⚠️ **证据边界**：Go 侧为**源码级**（**未起 Go 进程做活体对拍**）；RAG 侧为**真 Tomcat 实测**
（`service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:532`
`methodNotAllowedCarriesTraceparentUnlikeGo` ⇒ **404 + 空 body + 带 `Traceparent`**）。
⚠️ **本条注册进的是 `docs/Intentional_Deviations/global/common-tails.md`**（与 DRIFT 侧 §一·T-12 同档）。

## 十、C 组 · A6 订正（**B6**：like 域 `SD-2026-10-23-09` 的描述）

> ⛔ 目标文件 `docs/DRIFT/common/alignment-2026-10-23.md` 与 `docs/Intentional_Deviations/like/api.md`
> **都不在本 lane 的白名单内** ⇒ **只产出补丁文本**，交整合者改。

### 10.1 🔴 编号记错

`docs/DRIFT/common/alignment-2026-10-23.md:575` 与 `:600` 把 like 域那条 deadline 偏离记成了 **`SD-2026-10-23-09`**
⇒ **编号记错** —— **`-09` 是 worker 池饱和的裸 503 那条**，与 deadline **无关**。
⇒ **正确的编号 = `SD-2026-10-24-10`**（`docs/Intentional_Deviations/like/api.md:77-111`，⏳ 待签字候选）。

### 10.2 🔴 签字理由本身**不成立**（⇒ 台账原文要改）

台账原文（like 档 `SD-2026-10-24-10` 的「性质」字段）：

> **框架限制**（共享组件 `GoStyleRequestDeadline` 的**公开 API 只暴露毫秒**）

⇒ 🔴 **不成立** —— 该类有**两个** public API：

| API | 粒度 | 出处 |
|---|---|---|
| `remainingMillis()` | **毫秒** | `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java` |
| **`applyTo(stub)`** | **纳秒** | 同上 **`:110-120`** ⇒ `stub.withDeadlineAfter(remainingNanos, TimeUnit.NANOSECONDS)` |

⇒ ⇒ **真实理由是「**只读** API 只有毫秒粒度」**，不是「整个公开 API 只有毫秒」：

| 项 | 出处 |
|---|---|
| 只读 API（`remainingMillis()`）⇒ **毫秒粒度** | `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java` |
| `applyTo` **没有「上界」概念** ⇒ **表达不了 `min(上限, 剩余)`** | `service/common/src/main/java/com/rag/common/observability/LikeRpcClient.java:108-121` |

### 10.3 📌 **补丁文本（交整合者 · 逐字替换）**

**① `docs/Intentional_Deviations/like/api.md`** —— `SD-2026-10-24-10` 的「性质」字段：

> **旧**：框架限制（共享组件 `GoStyleRequestDeadline` 的**公开 API 只暴露毫秒**）

> **新**：框架限制（共享组件 `GoStyleRequestDeadline` 的**只读 API（`remainingMillis()`）只有毫秒粒度**
> —— ⚠️ **不是「整个公开 API 只有毫秒」**：`applyTo(stub)` 是**纳秒**的
> （`GoStyleRequestDeadline.java:110-120` ⇒ `stub.withDeadlineAfter(remainingNanos, TimeUnit.NANOSECONDS)`）；
> **真实限制**在**只读那一侧**：`applyTo` **没有「上界」概念** ⇒ **表达不了 `min(上限, 剩余)`**
> （`LikeRpcClient.java:108-121`））

**② `docs/DRIFT/common/alignment-2026-10-23.md:575` 与 `:600`** —— 编号：

> **旧**：`SD-2026-10-23-09`
> **新**：`SD-2026-10-24-10`（**并补一句**：`SD-2026-10-23-09` 是**同档的另一条**——worker 池饱和的**裸 503** ——与 deadline **无关** ⇒ 勿按编号盲查）

⚠️ **两处都改**（`:575` 与 `:600`）⇒ §5.3b 硬边界 2「一条问题只在一个档落笔」下，**编号要能寻址**。

## 十一、D 组 · 共享文件的补丁文本（**整合者 fold**）

> ⚠️ `_README.md` §七之二 是 **2026-10-25 06:40 的快照** ⇒ 缺 T-11 / T-12 与其后**二十一到二十五轮**的全部进展。
> 以下 **4 个补丁** 均**只写文本**，**本 lane 不改共享文件**。

### ① `docs/Intentional_Deviations/hot/api.md` —— `SD-2026-10-25-CAND-10` **补 Go 出处**（C1）

> ⚠️ **C1 的登记已在 `hot/api.md:332` 存在**（⏳ 待签字候选，双侧活体实测）⇒ **本 lane 不重复登记**。
> ⚠️ **但该条目缺「Go 出处 `文件:行号`」**（§3.3 强制三条之一）⇒ **补齐**：

**在该条目的表格里，「组件」行之后插入一行**：

> | **Go 出处** | **Go 标准库 `net/http/server.go`**（⚠️ **不在 `Sea-RideTheWind_golang` 项目树里** ⇒ 见 **PIT-GLOBAL-001** / **PIT-GLOBAL-006**）：<br>· **`:931`** `const DefaultMaxHeaderBytes = 1 << 20 // 1 MB` ⇒ **上限基数 = 1 MiB**；<br>· **`:941`** `return int64(s.maxHeaderBytes()) + 4096 // bufio slop` ⇒ **真实阈值 = 1 MiB + 4096**（**RAG 侧的 `1028KB` 就是照这条配的**）；<br>· **`:2040`** `const errorHeaders = "\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n"` ⇒ **`Content-Type` 是 `text/plain; charset=utf-8`**；<br>· **`:2049-2050`** `const publicErr = "431 Request Header Fields Too Large"` + `fmt.Fprintf(c.rwc, "HTTP/1.1 "+publicErr+errorHeaders+publicErr)` ⇒ **状态码 431 + body 就是这行字**。<br>**RAG 侧** Tomcat `org/apache/coyote/http11/Http11InputBuffer.java:768`（`fill`）抛 `IllegalArgumentException: Request header is too large` ⇒ 请求**没进 Servlet** ⇒ 状态码与错误体由 Tomcat 决定（`Constants.java` 无 431 预置常量 ⇒ 走 `Http11OutputBuffer` 的默认分支）。<br>⚠️ **阈值本身两侧已对齐**（翻转点**都是 1052536 字节，差 0**）⇒ 差异**只在拒绝时的呈现形态**（状态码 + `Content-Type` + body） |

⚠️ **本条的 Go 出处**与 T-7 同族：**不在 `Sea-RideTheWind_golang` 项目树里**（在 Go 标准库 / go-zero 依赖里）
⇒ 见 **PIT-GLOBAL-001** / **PIT-GLOBAL-006**。

### ② `docs/DRIFT/global/common-tails.md` §一·T-11 —— 状态行改「✅ 已修」

> **旧**：### T-11 🔴 **第十九轮新发现 · 未修** —— trace **导出**链路在 RAG 侧整体缺失（span 建了也无人收）

> **新**：### T-11 ✅ **已修（2026-10-25 第二十一 ~ 二十四轮）** —— trace **导出**链路已在 RAG 侧补齐
>
> **Go 侧事实（真源实读，`go-zero@v1.10.2`）**：`core/trace/agent.go:105-132` 的 `startAgent` 装 `sdktrace.NewTracerProvider(opts...)`（`:126`）+ **`otel.SetTracerProvider(tp)`（`:127`）** ⇒ **全局 provider 真的装上**；`core/trace/agent.go:115-124` **端点非空**才装 exporter（`:123` `sdktrace.WithBatcher(exp)`）⇒ **Go 确实导出 span**；`core/trace/agent.go:100-101` 非法 exporter ⇒ `unknown exporter`；`core/trace/agent.go:43-52` 装不上**只 `logx.Error` 继续启动**；`core/trace/propagation.go:8-11` 全局传播器 = Composite(TraceContext, Baggage)，**无条件**装。
> ⇒ ⇒ **本条曾是「真漂移」，不是误报** ⇒ **登记保留**（**不撤销**），只把状态改成「已修」。
>
> **落地**：第二十一轮装全局 OTel SDK（`GoStyleOtelAgent` / `GoStyleOtelConfig` / `GoStyleOtelAutoConfiguration`，10 条守门 + 4/4 变异全红）· 第二十三轮补采样比例 + batcher 校验 · **第二十四轮 Go 侧活体对拍**（`tools/semantic-probe/trace/` ⇒ **11 用例 · 8 一致 · 3 已知能力缺口 · 0 漂移**）⇒ 逐条 ⇒ [`lanes/L1-delivery.md`](L1-delivery.md) §九 / §十一 / §十二。

### ③ `docs/DRIFT/global/_README.md` §七之二 —— C3 行的**先例指针订正** + T-11 / T-12 补行

> **旧（C3 行）**：| **C3** | users 域 **8 处 `DEFAULT CURRENT_TIMESTAMP`**（Go 无） | 刻意保留（同 message 域 `SD-2026-10-23-06` 已签的同形态） |

> **新（C3 行）**：| **C3** | users 域 **8 处 `DEFAULT CURRENT_TIMESTAMP`**（Go 无） | 刻意保留 ⇒ 🏷 **已登记 `SD-2026-10-25-CAND-16`**（`docs/Intentional_Deviations/global/users-domain.md`）<br>⚠️ **先例指针订正**：「同 message 域 `SD-2026-10-23-06`」**不成立**（那条讲的是**索引列数**）⇒ 真实同形态先例 = `message/rpc.md` 的 **`SD-2026-10-24-04`**（6 列 DEFAULT，已签）+ `comment/rpc.md` 的 **`SD-2026-10-24-CAND-01`**（9 处 `NOT NULL DEFAULT`，⏳ 未签） |

**并在其后补两行（§七之二 C 表）**：

> | **C6** | **T-11**（trace 导出链路整体缺失） | ✅ **已修**（第二十一 ~ 二十四轮）⇒ **不是误报** —— Go `core/trace/agent.go:126-127` 确实装全局 provider + `:123` `WithBatcher` ⇒ Go 确实导出 ⇒ 曾是真漂移 ⇒ **登记保留**；详见 `common-tails.md` §一·T-11 与 `lanes/L1-delivery.md` §九/§十一/§十二 |
> | **C7** | **T-12**（「方法不匹配」出口 RAG 多带一个 `Traceparent`） | 🔴 **出口层级差异** ⇒ **共享层改不动** ⇒ 🏷 **已登记 `SD-2026-10-25-CAND-15`**（`docs/Intentional_Deviations/global/common-tails.md`）<br>⚠️ 只影响「方法不匹配」**这一个**出口（404 与正常响应两侧都带） |

### ④ `docs/DRIFT/_cross/open-items.md` P3 行 + `docs/DRIFT/_cross/ddl-type-divergences.md:79` —— 状态更新

> ⚠️ `open-items.md:41` 的 P3 行写「🟡 2026-10-25 实测 · 未修 · **无签字编号**」⇒ **现已登记** ⇒ 状态追加「🏷 `SD-2026-10-25-CAND-16`（未签字）」。
> ⚠️ `ddl-type-divergences.md:79` 写「users 域那 8 处**没有**对应的签字条目或 `V-xxx` 编号」
> ⇒ **后半句已过期**：users 域**有** `DRIFT-USERRPC-V-011`（真 PG 对照，见 `docs/DRIFT/users/user/rpc.md:1415`）⇒ 建议改为「**已登记 `SD-2026-10-25-CAND-16`（未签字）；DRIFT 侧编号 = `DRIFT-USERRPC-V-011`」。

## 十二、需拍板清单（**≤ 2 条；本轮实际 0 条**）

### ❌ **无。**

⇒ 依据用户既定偏好（**2026-10-25 明确纠正**）：「**都进 `docs/Intentional_Deviations/` 了，为什么还要我拍板**」
⇒ ⇒ **A 组的 4 条全部「登记完就躺着」**，措辞统一为「**🏷 候选，建议签**」⇒ **不升级为拍板**。
⇒ ⚠️ **D1 / D2 不重复提**（`_README.md` §七之二 D 表已列，**已单独处理**）。

## 十三、交接整合者清单（本 lane 越权做不了的事）

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | **§十一 的 4 个补丁**（`hot/api.md` 补 Go 出处 · `common-tails.md` §一·T-11 改状态 · `_README.md` §七之二 订正 + 补 C6/C7 · `open-items.md` + `ddl-type-divergences.md` 更新） | **共享文件** ⇒ 协议 §二.3 |
| ② | **`docs/Intentional_Deviations/_INDEX.md` 同步** —— 计数表要加：**CAND 族 10 → 14 条**（新增 `CAND-14/15/16/17`）· **未签字候选合计 14 → 18 条**<br>⇒ 并在「已有内容」表加一行：`global/users-domain.md`（**2 条**）+ `global/common-tails.md`（**+2 条**）<br>⇒ ⚠️ 计数口径：**已签字 49 条不变**（本轮**零条**转正） | **三个 `_INDEX.md` 之一** ⇒ 协议 §二.3 |
| ③ | **`docs/Intentional_Deviations/global/users-domain.md` 的归属裁决** —— 本档是 **global 代收**的临时落点（users 域档 `docs/Intentional_Deviations/users/_shared.md` 当前 **0 字节**，且**不在本 lane 白名单内**）⇒ **建议整合者把两条 fold 进 `users/_shared.md` 后删除本档**（§5.3b 硬边界 2：一条问题只在一个档落笔） | **目标文件不在白名单内** |
| ④ | **`docs/Pitfalls/_INDEX.md` 同步** —— 新增 **PIT-GLOBAL-022**（抄来的先例指针也要复核）· **PIT-GLOBAL-023**（「出口层级」的结论要落到注册点） | **共享索引** |
| ⑤ | **`docs/DRIFT/global/_INDEX.md` / `_MANIFEST.json`** 同步（新增 `docs/Intentional_Deviations/global/users-domain.md`） | **共享索引 / 清单** |
| ⑥ | **`AGENTS.md` §0 `global` 行** 更新（`global` 域的签字候选数 + 「C 类登记已补齐」） | **`AGENTS.md` 是共享文件** |
| ⑦ | **`align/global-signoff` → `align/global` 的合并** | 由 Jason / 整合者决定时机 |

## 十四、⚠️ 本轮新立 / 发现的 Pitfall

| 编号 | 一句话 | 落点 |
|---|---|---|
| **PIT-GLOBAL-022** | 🔴 **抄来的「同形态先例」编号也要复核** —— 指针不是证据，它本身是一条**待核对的断言**；**错的指针比空指针更贵** | [`docs/Pitfalls/global/cross-domain.md`](../../../Pitfalls/global/cross-domain.md) |
| **PIT-GLOBAL-023** | 🔴 **「这个出口在 XX 层 ⇒ 不经过 X」是推断，不是证据** —— 「层」的判断必须落到**注册点** | [`docs/Pitfalls/global/common-tails.md`](../../../Pitfalls/global/common-tails.md) |

⇒ 两条都是**本轮实际踩到 / 实际差点踩到**的，不是凑数的：
**PIT-GLOBAL-022** 是复核 C3 的先例指针时**当场发现的错指针**；
**PIT-GLOBAL-023** 是给 T-12 定性时**必须补上的一步**（否则「router 层 ⇒ 不经过」只是推断）。

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | 🆕 **第二十六轮（L5 lane `align/global-signoff` · 基线 `d7a126a`）** —— **纯文档**，**生产代码零改动**<br>① **A 组**：🆕 登记 **4 条待签字候选**（全部「**🏷 候选，建议签**」，**登记完就躺着，不升级为拍板**）—— `SD-2026-10-25-CAND-14`（C2 · T-7 状态行 reason phrase · **框架限制**）· `SD-2026-10-25-CAND-15`（T-12 · **出口层级差异**）· `SD-2026-10-25-CAND-16`（C3 · users 域 8 处 DEFAULT · **刻意保留**）· `SD-2026-10-25-CAND-17`（C4 · `users_user.status` 可空性 · **RAG 更严**）；⛔ **C1 不重复登记**（已存在于 `hot/api.md:332` ⇒ §5.3b 硬边界 2）⇒ 只产出「**补 Go 出处**」补丁<br>② **B 组定性**：**T-11 = ✅ 已修，不是误报**（Go `core/trace/agent.go:126-127` + `:123` 确实装全局 provider 与 batcher ⇒ Go 确实导出 ⇒ 曾是真漂移 ⇒ **登记不撤销**；活体对拍证据 = `tools/semantic-probe/trace/` 11 用例 8 一致 0 漂移）· **T-12 = ✅ 定性成立**（Go `rest/engine.go:113-115` 的**注册点** + `patrouter.go:59-83` 三条出口 ⇒ 405 绕过 `TraceHandler` ⇒ **出口层级差异** ⇒ **共享层改不动** ⇒ 签字候选）<br>③ **C 组**：产出 **A6 / B6 订正补丁**（like 档 `SD-2026-10-24-10` 的「性质」字段 + `docs/DRIFT/common/alignment-2026-10-23.md:575/:600` 的**编号记错**）—— **目标文件不在白名单 ⇒ 只出补丁文本**<br>④ **D 组**：产出 `_README.md` §七之二 的**快照补丁**（C3 先例指针订正 + 补 C6/C7 两行）+ `hot/api.md` 补 Go 出处 + `common-tails.md` §一·T-11 改状态 + `open-items.md` / `ddl-type-divergences.md` 更新<br>⑤ 🆕 **2 条 Pitfall**：`PIT-GLOBAL-022`（抄来的先例指针也要复核）· `PIT-GLOBAL-023`（「出口层级」的结论要落到注册点）<br>⑥ **需拍板清单：0 条**（用户既定偏好：登记类事项登记完就躺着）<br>⑦ 🔴 **顺手修掉 5 处坏相对链接**（本 lane 拥有写权的两个文件内）：`docs/Intentional_Deviations/global/common-tails.md` 的 `../../../Pitfalls/...`（**2 处是本文件原有的**）+ `docs/DRIFT/global/lanes/L5-signoff.md` 的 `../../Pitfalls/...` ⇒ 路径少/多一级 ⇒ **点不开** ⇒ 已改正 ⇒ 本轮全量链接自检 **MISSING_LINKS = 0** |

