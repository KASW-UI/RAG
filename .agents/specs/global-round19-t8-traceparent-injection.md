# global 第十九轮 —— T-8 落地：HTTP 响应补 W3C `Traceparent`（对齐 go-zero `tracehandler.go`）

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`ff80214`
> **性质**：**共享层真漂移修复（10 个 api 全生效）+ 真 Tomcat 守门 + 变异测试 + 台账**
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（**零改动**；但**共享层生效 ⇒ favorite 的响应多了一个头** ⇒ 记账 + 告知 owner）

---

## 一、要解决什么

`docs/DRIFT/global/common-tails.md` §一·T-8（第九轮归因）：

| | 内容 |
|---|---|
| **Go** | `go-zero@v1.10.2/rest/handler/tracehandler.go:61` ⇒ `propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))` ⇒ **响应头**带 `Traceparent: 00-<traceid>-<spanid>-01`<br>`rest/engine.go:134-138`（每条路由）+ `:246-251`（404）⇒ **每个响应都带** |
| **RAG** | **完全没有** ⇒ 缺 `Traceparent`（message 域 6 档真容器 dump 里 Go 侧 401/404/200 **都带**） |
| **落点** | `service/common` 的 HTTP 入口 ⇒ **改 1 处 ⇒ 10 个 api 全生效 ⇒ 不碰任何 api 模块的代码** |

---

## 二、🔴 取证：根因**不是**「忘了写一行」

写这行之前，RAG 侧**连「当前 server span」都不存在**：

| # | 事实 | 证据 |
|---|---|---|
| ① | 全仓**没有** `micrometer-tracing-bridge-otel`、**没有** `opentelemetry-sdk-extension-autoconfigure` | `grep -rn "micrometer-tracing" --include=pom.xml .` ⇒ **0 命中**；`mvn dependency:tree`（hot-api）⇒ 只有 `opentelemetry-sdk-extension-autoconfigure-spi`（**SPI**，不是自动配置） |
| ② | ⇒ Spring Boot 的 tracing 自动配置**不生效** ⇒ `GlobalOpenTelemetry.get()` 是 **no-op** | `GlobalOpenTelemetry` 在未 set 时返回 no-op |
| ③ | ⇒ `Span.fromContext(ctx).getSpanContext()` 恒为 **INVALID** | ⇒ **任何**「从当前上下文取 traceparent 再写出去」的实现都写不出东西 |

⚠️ ⇒ **只加一行 `setHeader` 是无效的**：得先有一个能产生**有效** `SpanContext` 的 tracer。

### 🔴 顺带查出一个**共享层版本错配**（真跑才暴露）

第一次跑守门 ⇒ 运行时 `NoSuchMethodError: io.opentelemetry.context.propagation.TextMapGetter.getAll`：

```
java.lang.NoSuchMethodError: io.opentelemetry.context.propagation.TextMapGetter.getAll(java.lang.Object, java.lang.String)
    io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator.extractMulti(W3CBaggagePropagator.java:142)
```

⇒ 根因（`mvn dependency:tree -pl service/common`）：

```
+- io.opentelemetry:opentelemetry-api:jar:1.62.0:compile      ← 被手写版本号
+- io.opentelemetry:opentelemetry-sdk:jar:1.44.0:compile      ← BOM 管
|  +- io.opentelemetry:opentelemetry-sdk-trace:jar:1.44.0
+- io.opentelemetry:opentelemetry-context:jar:1.44.0:compile  ← BOM 管
```

⇒ `opentelemetry-api` 1.62.0 的 `W3CBaggagePropagator` 会调 `TextMapGetter.getAll`，
而 `opentelemetry-context` **1.44.0 没有这个方法**（`javap` 实测：**1.55.0 才有**）。

⇒ 修法：**删掉手写的 1.62.0**，统一由 BOM（`<opentelemetry.version>` = 1.44.0）管
—— 这本来就是那段 pom 注释写着的意图（「统一管理 OpenTelemetry 各模块版本，避免子模块手写版本号」）。

⚠️ **对 10 个 api 零影响**：它们的 otel 版本由**根 pom 的 Spring Boot BOM**（1.55.0）管
⇒ 实测 `hot-api` 的 otel 全家是 1.55.0（api/context/sdk **同版本**）⇒ 本模块改版本不改变 api 进程实际跑的版本。

---

## 三、改了什么

### ① 新增 `service/common/.../http/GoStyleTraceparentFilter.java`

`@Component` + `@Order(HIGHEST_PRECEDENCE + 3)` ⇒ **10 个 api 全生效，零 api 代码改动**。

| # | 动作 | 对着 Go 的哪一行 |
|---|---|---|
| ① | **私有** `SdkTracerProvider`（`Sampler.parentBased(Sampler.traceIdRatioBased(1.0))`），**刻意不注册成全局** | `core/trace/agent.go:110` 的 `ParentBased(TraceIDRatioBased(c.Sampler))`（Go yaml `Sampler: 1.0`） |
| ② | `PROPAGATOR.extract(ctx, req, getter)` ⇒ 入向 `traceparent` 成为 **parent** | `tracehandler.go:47` 的 `propagator.Extract(r.Context(), …)` |
| ③ | `tracer.spanBuilder(...).setParent(parent).setSpanKind(SERVER).startSpan()` | `tracehandler.go:48-52` 的 `tracer.Start(ctx, spanName, WithSpanKind(SpanKindServer), …)` |
| ④ | `PROPAGATOR.inject(Context.current(), resp, setter)` —— **在 `chain.doFilter` 之前** | `tracehandler.go:61` 的 `propagator.Inject(spanCtx, HeaderCarrier(w.Header()))`（在 `next.ServeHTTP` 之前） |
| ⑤ | setter **canonical 化**头名（`traceparent` ⇒ `Traceparent`） | Go 的 `propagation.HeaderCarrier.Set` 走 `textproto.MIMEHeader.Set` ⇒ canonical |

⚠️ **为什么不注册成全局**：`GlobalOpenTelemetry.set(...)` **全局只能设一次**
⇒ 一旦设了，同 JVM 内所有 `GlobalOpenTelemetry.get().getTracer(...)` 的调用方
（`RpcInterceptor` / `MqConsumer` / `AdminSpans` / …）行为**全部改变**（no-op ⇒ 真建 span）
⇒ 那是**另一件事**（新登记 T-11），不该由 T-8 带出来。

#### 🔴 为什么 `@Order` 是 `HIGHEST_PRECEDENCE + 3` —— **链上位置是「承重」的**

三条约束各有 Go 出处，缺一条就产生**字节级差异**：

| # | 约束 | Go 出处（**消费点**） | 违反后的差异 |
|---|---|---|---|
| ① | **CORS 必须在本类之前** | `rest/internal/cors/handlers.go:61-63`：`cors.Middleware` 对 `OPTIONS` **短路**（写 204、**不调 next**）⇒ 预检**不经过 router** ⇒ **不经过 TraceHandler** | 预检响应**多带** `Traceparent`（Go 没有） |
| ② | **MaxConns/Breaker/Shedding 必须在本类之后** | `rest/engine.go:135` 的 `TraceHandler` 是 native 链**第一个**（`:146/:149/:152` 才是那三个） | 被拒成 503 的响应**丢头**（Go 带着） |
| ③ | **必须在 `HttpMiddlewareFilter` 之前** | `HttpMiddleware.java:269` 在**容器线程**抓 `Context.current()`（`parentOtelCtx`），`:312` 在 worker 线程 `makeCurrent()` 复原 ⇒ 它给 span 打的属性（`:358/:455/:485`）落在「**进入时当前的那个 span**」上 | 属性全落到 no-op span 上 |

⇒ **实测链序**（`article-api` 真容器，逐条钉死）：

```
CORS(+1) → path.Clean(+2) → traceparent(+3) → MaxConns(+4) → Breaker(+5)
  → Shedding(+6) → HttpMiddleware(+7) → MaxBytes(+8) → JsonBodyLimit(+9)
```

⚠️ **相对顺序没变，变的是整块的取值**（保护层四件套 + `HttpMiddleware` + `MaxBytes` **整体后移**）
—— 要给本类腾出「CORS 之后、MaxConns 之前」这个**整数位**。
⚠️ 顺带修掉一个**隐式依赖**：原先 CORS 与 `path.Clean` **同为** `HIGHEST_PRECEDENCE`
⇒ 相对顺序靠 **Bean 注册序** ⇒ 现**各自显式取值**（`+1` / `+2`）。

### ② `service/common/pom.xml` —— 统一 OpenTelemetry 版本（见 §二）

---

## 四、🔴 三个**容易写错**的点（本轮的真正价值）

| # | 坑 | 本轮怎么钉住 |
|---|---|---|
| ① | **回显入向 parent**（把入向头原样写回）⇒ 「看起来完全正常」 | 守门断言 **span-id 必须不同于入向** ⇒ **变异 M2** 把它改回回显 ⇒ **红在目标断言上** |
| ② | **头名大小写** ⇒ Go 是 `Traceparent`，OTel Java 写 `traceparent` 且 Tomcat 原样发出 ⇒ **字节级不一致** | 守门直接断言 **wire 上是 `\r\nTraceparent: `** ⇒ **变异 M3** 去掉 canonical 化 ⇒ 红（实测 wire 上就是小写 `traceparent:`） |
| ③ | **注入时机** ⇒ 放在业务之后 ⇒ 错误状态码就丢头 | 守门有「404 响应也带」一条 |

---

## 五、✅ 守门（真 Tomcat + **裸 socket**，看**原始字节**）

`service/common/src/test/java/com/rag/common/http/GoStyleTraceparentFilterTest.java` —— **6 条**：

| # | 用例 | 断言 |
|---|---|---|
| ① | `responseCarriesServerSpanTraceparent` | 有 `Traceparent`；形状 `00-<32hex>-<16hex>-<flags>`；**trace-id 沿用入向**；**span-id ≠ 入向**；flags `01` |
| ② | `responseCarriesFreshTraceparentWhenNoInbound` | 无入向也生成合法的（trace-id 非全 0） |
| ③ | `errorStatusResponseAlsoCarriesTraceparent` | 404 响应也带（**应用级** 404） |
| ④ | `sampledFlagFollowsInboundParent` | 入向 flags `00` ⇒ 响应 flags `00`（**Go 的 ParentBased 语义**） |
| ⑤ | `headerNameIsCanonicalizedLikeGo` | wire 上是 `Traceparent`（**不是**小写） |
| ⑥ | `containerLevelNotFoundIsOutOfFilterChain` | **容器级** 404（error valve）**没有**头 ⇒ 写清边界 |

### 第二轮：`article-api` 真容器 **+3 条**（`ArticleApiAssemblyWiringTest`，共 **13** 条）

| # | 用例 | 断言 |
|---|---|---|
| ⑦ | `effectiveFilterOrderMatchesGoChain`（**扩充**） | 完整链序**逐条钉死**（含本类）+ `cors < traceparent < maxConns` **成对断言** |
| ⑧ | `optionsPreflightHasNoTraceparent` | **OPTIONS 预检 204 不带 `Traceparent`**（Go 的 `cors.Middleware` 短路 ⇒ 不经过 TraceHandler） |
| ⑨ | `normalResponseCarriesTraceparent` | 正常请求的响应**带** `Traceparent`（共享层 filter 真进了 api 的链，§3.3） |
| ⑩ | `methodNotAllowedCarriesTraceparentUnlikeGo` | 🔴 **characterization test**：方法不匹配（Go = 404 空 body）**多带**一个 `Traceparent` ⇒ **T-12** 的实测证据 |

⚠️ **为什么必须裸 socket**：要断言的是**响应头在 wire 上的字节**（头名大小写）⇒ MockMvc 看不到。
⚠️ **为什么第 ⑥ 条存在**：Tomcat 的 **error valve** 生成的 404 错误页**不经过 filter 链**
⇒ 任何 filter 都加不上头 ⇒ **不构成**与 Go 的漂移（Go 的 404 由**链内**的 `notFoundHandler` 生成）。

---

## 六、🟢 变异（三条全部转红，且**红在目标断言上**）

| # | 变异 | 结果 |
|---|---|---|
| **M1** | 去掉 `PROPAGATOR.inject(...)` | 🔴 **RED** —— 5 条红（①②③④⑤） |
| **M2** | 改成 `PROPAGATOR.inject(parent, ...)`（**回显入向**） | 🔴 **RED** —— 红在 ① 的 **span-id ≠ 入向** 断言上（`actual=00-1111…-2222…-01`） |
| **M3** | setter 不 canonical 化 | 🔴 **RED** —— 红在 ⑤（wire 上实测是小写 `traceparent:`） |
| **M4** | 本类挪到 `MaxConns` **之后**（`+3 → +10`） | 🔴 **RED** —— 红在 article-api 的「**traceparent 必须在 MaxConns 之前**」断言上<br>⚠️ `service/common` 那 6 条**全绿**（它们手工把 filter 挂进 Tomcat ⇒ **看不见 `@Order`**）⇒ **顺序这条约束只有 article-api 的守门能抓** |
| **M5** | CORS 挪到本类**之后**（`+1 → +10`） | 🔴 **RED** —— 红在「**OPTIONS 预检不带 `Traceparent`**」断言上（实测预检响应带上了头） |

---

## 七、Go 出处汇总

| 事实 | 出处 |
|---|---|
| 响应头注入 `traceparent` | `go-zero@v1.10.2/rest/handler/tracehandler.go:61` |
| 入向 context 作为 parent | 同上 `:47` |
| server span 的 `SpanKind` | 同上 `:49` |
| 每个路由都挂 | `go-zero@v1.10.2/rest/engine.go:134-138` |
| 404 也挂 | 同上 `:246-251` |
| `Middlewares.Trace` 默认 **true** | `go-zero@v1.10.2/rest/config.go:11-22`（`Trace bool \`json:",default=true"\``） |
| sampler = `ParentBased(TraceIDRatioBased(1.0))` | `go-zero@v1.10.2/core/trace/agent.go:110` + Go yaml `Telemetry: { Sampler: 1.0 }` |
| 头名 canonical | Go `net/textproto.MIMEHeader.Set`（`CanonicalMIMEHeaderKey`） |
| Go 侧 api 全部带（无 `TraceIgnorePaths`） | 各 api 的 `etc/*.yaml` **未配** `Middlewares` / `TraceIgnorePaths` |
| **CORS 对 OPTIONS 短路**（⇒ 预检不经过 TraceHandler） | `go-zero@v1.10.2/rest/internal/cors/handlers.go:61-63` |
| **TraceHandler 是 native 链第一个** | `go-zero@v1.10.2/rest/engine.go:135`（MaxConns/Breaker/Shedding 在 `:146/:149/:152`） |
| **未注册路径的 404 也带**（`NewServer` **默认**装 notFoundHandler） | `go-zero@v1.10.2/rest/server.go:58` + `rest/engine.go:245-251` |
| **「方法不匹配」在 router 层 ⇒ 不带**（⇒ T-12） | `go-zero@v1.10.2/rest/router/patrouter.go:66-72` |

---

### 🔴 本轮残留（**只登记、未修**）：**T-12** ——「方法不匹配」出口 RAG **多带**一个 `Traceparent`

| | 内容 |
|---|---|
| **Go** | `rest/router/patrouter.go:66-72` ⇒ 路径命中、**方法不对** ⇒ 走 `pr.notAllowed`（article = `cors.NotAllowedHandler`）或默认 405 ⇒ **两条都在 router 层 ⇒ 不经过 TraceHandler ⇒ 不带** |
| **RAG** | 同一出口落在 `DispatcherServlet` **内** ⇒ filter 链早已跑完 ⇒ **多带** |
| **实测** | `ArticleApiAssemblyWiringTest.methodNotAllowedCarriesTraceparentUnlikeGo`（真 Tomcat）⇒ `GET /v1/article` ⇒ **404 + 空 body + 带 `Traceparent`** |
| **为什么不修** | 根因是**出口层级不同** ⇒ 共享层改不动（filter 阶段无法知道最终会不会落到方法不匹配）⇒ 要么共享层自建路由表、要么 10 个域暴露路由元数据 ⇒ **代价远超「多一个头」的收益** ⇒ 建议**签字认账** |
| **台账** | [`docs/DRIFT/global/common-tails.md`](../../docs/DRIFT/global/common-tails.md) §一·**T-12** |

⚠️ **易误判点（已查清）**：Go 的「**未注册路径**的 404」**也带** traceparent
⇒ 因为 `rest/server.go:58` 的 `NewServer` **默认** prepend `WithNotFoundHandler(nil)`
⇒ ⇒ **T-12 只涉及「方法不匹配」这一个出口**，别读成「RAG 的 traceparent 一律多一个」。

---

## 八、📌 待维护者合并到**共享索引**（global 不写，§5.3b）

| 位置 | 要加什么 |
|---|---|
| `AGENTS.md` §0 域表 `global` 行 | T-8 ⇒ 「✅ 第十九轮已落地」；B10 ⇒ 「✅ 第十八轮已落地」；X-5 ⇒ 「✅ 第十七轮已落地」 |
| `docs/DRIFT/_INDEX.md` | global 域的台账变更记录指针 |

## 九、📌 交该域 owner 的账

| 项 | 说明 |
|---|---|
| **favorite 域零改动** | 代码 / 配置 / 测试 / 台账**全零改动** |
| **但共享层生效 ⇒ favorite 的响应多了一个 `Traceparent` 头** | ⇒ **这不是新漂移**：Go 的 favorite api **同样**挂 `TraceHandler`（`engine.go` 对所有路由统一挂）⇒ 本次是**向 Go 收敛** |
| **favorite 也同吃 T-12** | 共享层 ⇒ 全 10 个 api 一致生效 ⇒ favorite 在「方法不匹配」出口也多带一个头 ⇒ **同样不是**给 favorite 新造的漂移（Go 的 favorite 在该出口同样不带） |
| **无需 favorite owner 做任何事** | 仅需知悉；§5.3c「共享层改动若影响 favorite 行为必须记账并告知 owner」已履行 |

### 📌 交 **article** 域 owner 的账（本轮改到了 article-api 的代码）

| 项 | 说明 |
|---|---|
| **article-api 的 6 个 filter 的 `@Order` 数值变了**（相对顺序**不变**） | `ArticleGoStyleCorsFilter` `MIN→+1` · `GoStylePathNormalizingFilter` `MIN→+2` · `GoStyleMaxConnsFilter` `+1→+4` · `GoStyleBreakerFilter` `+2→+5` · `GoStyleCpuSheddingFilter` `+3→+6` · `HttpMiddlewareFilter` `+4→+7` · `GoStyleMaxBytesFilter` `+5→+8` · `GoStyleJsonBodyLimitFilter` `+6→+9` ⇒ **中间插入**共享层的 `GoStyleTraceparentFilter`(`+3`) |
| **⇒ `docs/DRIFT/article/api.md` 的链序快照已过期** | 该文件 ~`:1410-1435` 记的是「真容器实测链序（2026-10-24）」+ 各 filter 的 `@Order` 数值 + 「**前两位同为 `HIGHEST_PRECEDENCE` ⇒ 相对顺序由 Bean 注册序决定**」的说明<br>⇒ **现在**：数值全变、**多了 traceparent filter**、且「前两位同级」**已不再成立**（现为 `+1` / `+2`，由 `@Order` 表达）<br>⚠️ **该文件不在 global 的写权内** ⇒ 本轮**只记账，不改** ⇒ 请 article owner 在下次动该域时一并刷新 |
| **article-api 的守门已同步更新**（在 global 侧） | `ArticleApiAssemblyWiringTest.effectiveFilterOrderMatchesGoChain` 的完整链序已改为 9 项 + 新增 `cors < traceparent < maxConns` 成对断言 ⇒ **该断言现在同时是 article 域的链序回归网** |
| **like 域 `SD-2026-10-24-10` 的描述需订正**（末轮核实，**只记账**） | 🔴 「`GoStyleRequestDeadline` 的**公开 API 只暴露毫秒**」**不成立**（`applyTo(stub)` 就是**纳秒**的）；「为什么不修」的理由也**不对**（真正理由在 `LikeRpcClient.java:108-121`：**`applyTo` 没有「上界」概念**）⇒ 准确措辞 = 「**只读** API 只有毫秒粒度」<br>⇒ ⚠️ `like` 档与 `common` 档（后者还**记错了编号**：`SD-2026-10-23-09` ⇒ 实为 `SD-2026-10-24-10`）**各需 owner 订正** ⇒ **只记账，不改** |
| **article 域新增 3 条真容器断言** | `optionsPreflightHasNoTraceparent` · `normalResponseCarriesTraceparent` · `methodNotAllowedCarriesTraceparentUnlikeGo`（T-12 的 characterization） |
