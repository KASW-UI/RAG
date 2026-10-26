# global 第二十一轮 —— T-11 落地：装全局 OpenTelemetry（trace **导出**链路 + 传播器）

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`aac691c`
> **性质**：**跨域共享层的功能缺口补齐**（`service/common` 装全局 OTel SDK）+ 10 条真守门 + 变异测试
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（**零改动**；共享层只**新增**能力 ⇒ favorite 的**可观察行为会变** ⇒ 按 §5.3c **记账并告知 owner**）

---

## 一、要解决什么

`docs/DRIFT/global/common-tails.md` **T-11**（第十九轮登记、**未修**）：
「trace **导出**链路在 RAG 侧整体缺失（span 建了也无人收）」。

台账原文的**证据边界**写着：「本条**未实测**『RAG 侧真的没有 span 被导出』—— 依据是依赖树 + `GlobalOpenTelemetry` 无 provider（**静态取证**）⇒ **下一轮第一步应是用真进程验证**」。
⇒ 本轮**先补上那个实测**，再修。

### 根因（不是「忘了加一行」）

Go 侧每个服务启动时都会装一个**全局** `TracerProvider`：

```
rest/server.go:49          c.SetUp()
  → core/service/serviceconf.go:67   trace.StartAgent(sc.Telemetry)
      → core/trace/agent.go:105-135  startAgent(c Config)
           ParentBased(TraceIDRatioBased(c.Sampler))      // :111
           WithResource(NewSchemaless(ServiceName))        // :106 + :112
           if len(c.Endpoint) > 0 { WithBatcher(exp) }    // :114-121
           otel.SetTracerProvider(tp)                     // :127
           otel.SetErrorHandler(...)                      // :128-130
  → core/trace/propagation.go:8-11   init() 里**无条件**装 composite 传播器
  → core/service/serviceconf.go:71-73  proc.AddShutdownListener(trace.StopAgent)
```

而 RAG 侧**全仓无 exporter 装配** ⇒ `GlobalOpenTelemetry.get()` 是 **no-op**
⇒ 所有 `get().getTracer(...)` 建出的 span `SpanContext` **恒 INVALID**
⇒ **既没有 span，也没有导出**。
⇒ 各 yaml 里的 `otel: {...}` **一直是对的**（10 个 api + 8 个 rpc 都写了 endpoint / sampler / service.name）⇒ **缺的只是装配**。

### 🔴 顺带坐实的第二个缺口（本轮新发现）

Go 的 `core/trace/propagation.go` 的 `init()` **无条件**装传播器 ⇒
`service/common/communityfact/wire.go:104` 的
`otel.GetTextMapPropagator().Inject(ctx, propagation.HeaderCarrier(request.Header))`
**真的写出了 `traceparent`** ⇒ **Go 发给 DC 的请求带这个头**。

而 RAG 侧 `Wire.java:226` 的 inject 打在 **no-op 传播器**上 ⇒ **DC 请求少一个 `traceparent` 头**（**出向请求头的字节级差异**）。

---

## 二、Go 出处（逐条）→ RAG 落点

| Go 位置 | 语义 | RAG 落点 |
|---|---|---|
| `core/trace/agent.go:111` | `ParentBased(TraceIDRatioBased(Sampler))` | `GoStyleOtelAgent.buildProvider`（`Sampler.parentBased(Sampler.traceIdRatioBased(ratio))`） |
| `core/trace/agent.go:106` + `:112` | Resource 带 `service.name` | `Resource.getDefault().merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), …)))` |
| `core/trace/agent.go:114-121` | **`endpoint` 非空才装 batcher** | `if (config.endpoint() != null) { … OtlpGrpcSpanExporter + BatchSpanProcessor }` |
| `core/trace/agent.go:127` | `otel.SetTracerProvider(tp)`（**全局**） | `GlobalOpenTelemetry.set(sdk)` |
| `core/trace/propagation.go:8-11` | `init()` 装 `TraceContext + Baggage` composite（**无条件**） | `OpenTelemetrySdk.builder().setPropagators(ContextPropagators.create(TextMapPropagator.composite(…)))` |
| `core/trace/agent.go:43-52` | `StartAgent` 用 `sync.Once` ⇒ 同 JVM 只装一次 | `GoStyleOtelAgent.INSTALLED` 静态标志 |
| `core/trace/agent.go:49-51` | 装不上**只 `logx.Error`，继续启动** | `catch (IllegalStateException) { LOG.error(...); provider.close(); return null; }`（**不抛**） |
| `core/trace/agent.go:55-62` + `serviceconf.go:71-73` | 关停 flush（`tp.Shutdown`） | `GoStyleOtelAgent.shutdown()` + **JVM shutdown hook** |
| `core/trace/config.go:9` | `Sampler float64`（**比例**） | `GoStyleOtelConfig.toRatio`（Boot 的采样器**名字** → 比例） |
| `core/trace/agent.go:70-71` | `WithEndpoint(c.Endpoint)` + `WithInsecure()` | `GoStyleOtelConfig.normalizeEndpoint`（**裸 host:port 补 `http://`**） |
| `rest/handler/tracehandler.go:32-64` | **一个** provider：server span 既写响应头又被导出 | `GoStyleTraceparentFilter` 改用**全局** tracer（此前是私有 provider ⇒ 不同源） |

---

## 三、改了什么

### ① `service/common/src/main/java/com/rag/common/observability/GoStyleOtelConfig.java`（新）

Go 的 `Telemetry` 四字段投影。🔴 **两套键名都要认**（实测 20 个模块）：

| 语义 | api 模块（10 个） | rpc 模块（8 个） |
|---|---|---|
| 服务名 | `otel.resource.attributes.service.name` | `otel.service.name` |
| 端点 | `otel.exporter.otlp.endpoint`（**带** `http://`） | `otel.exporter.otlp.endpoint`（**不带** scheme） |
| 协议 | `otel.exporter.otlp.protocol: grpc` | 不写（默认 grpc） |
| 采样 | `otel.traces.sampler: always_on` / `parentbased_always_on` | `otel.traces.sampler: parentbased_always_on` |

### ② `.../GoStyleOtelAgent.java`（新）

照 `startAgent` 逐条装配；`buildProvider(...)` 抽成**静态纯工厂**（不碰全局）⇒ 守门可以拿它造一个「指向假 OTLP 接收端」的 provider 做**端到端导出**验证。

### ③ `.../GoStyleOtelAutoConfiguration.java` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（新）

🔴 **装配点必须是 `BeanFactoryPostProcessor`**：
OTel Java 的 `GlobalOpenTelemetry.set(...)` 只能调一次，且**先调 `get()` 会让后续 `set()` 抛 `IllegalStateException`**；
本仓有**四处构造器里就调 `get()`** ⇒ 普通 Bean 会被抢跑 ⇒ 全局永远装不上。

🔴 **走自动配置清单而非组件扫描**：
rpc 模块**刻意不扫** `com.rag.common`（保护层四个 Filter 依赖 servlet API）⇒ 走扫描的话 **rpc 侧永远装不上**，
而 Go 侧 **api 与 rpc 都装** ⇒ ⇒ 必须走清单 ⇒ 且**零域模块改动**（favorite 域代码同样零改动）。

### ④ `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java`（改）

改用**全局** tracer。此前是**私有** provider ⇒ 响应头里的 traceparent 指向**另一条未导出的 trace**
⇒ **trace 在边界处断成两截**（客户端拿到的 trace-id 在 jaeger 里查不到）⇒ 与 Go 不等价。

### ⑤ `service/common/pom.xml`

`+ io.opentelemetry:opentelemetry-exporter-otlp`（**compile**，BOM 管版本）· `+ io.grpc:grpc-netty-shaded`（**test**，假 OTLP 接收端用）。

---

## 四、守门（10 条，**零 mock**）

| 守门 | 形态 | 条数 |
|---|---|---|
| `GoStyleOtelAgentGateTest` | **真 Spring Boot 上下文**（自动配置清单 ⇒ BFPP ⇒ 全局装配）＋ **真 gRPC 假 OTLP 接收端**（`grpc-netty-shaded` 起真服务端，OTLP 客户端侧是 exporter 内置的 OkHttp h2c prior-knowledge） | **3** |
| `GoStyleTraceparentFilterTraceSourceGateTest` | **真 Tomcat** ＋ **真 OTLP 接收端** ⇒ 断言**响应头的 trace-id 出现在真正导出的字节里** ⇒ 同源 | **1** |
| `GoStyleTraceparentFilterTest` | 真 Tomcat + 裸 socket（既有）⇒ 改为**显式装配全局 provider** | **6** |

⚠️ **「顺序依赖的假绿」已修**：整套 `service/common` 跑下来是绿的，但那只因为**别的测试类恰好先装过全局 provider**
⇒ 单跑 filter 那条类会红 ⇒ 现改为**每个类自己显式装配**（且装的是**生产装配代码**）。

---

## 五、变异（**每条必须红在目标断言上**）

见 §六 的表。

---

## 六、变异结果表

**4 条变异全部转红（4/4），2 个被改文件全部还原。**

| 变异 | 改了什么 | 期望红在 | 结果 | 连带红 |
|---|---|---|---|---|
| **N1** | `GoStyleOtelAgent.install` **不调** `GlobalOpenTelemetry.set(sdk)` ⇒ 全局仍是 no-op | `globalProviderIsRealInsideRealSpringContext` | ✅ | 1（`shutdownFlushesPendingSpans`） |
| **N2** | `buildProvider` **不装 exporter**（`if (config.endpoint() != null)` → `if (false)`） | `spanIsActuallyExportedToTheWire` | ✅ | 1（同上） |
| **N3** | `shutdown()` **不 close provider** ⇒ 关停不 flush | `shutdownFlushesPendingSpans` | ✅ | 0 |
| **N4** | `GoStyleTraceparentFilter.tracer()` 退回**私有** provider | `responseTraceparentBelongsToTheExportedSpan` | ✅ | 0 |

**判定口径**：目标 testcase **变红**即可（`failure` 或 `error` 都算）；**连带红数**必须记录。
⚠️ N1 / N2 的连带红是**必然**的：全局 provider / exporter 没了，关停 flush 那条自然也没东西可 flush ⇒ **连带红不是守门有问题**。


---

## 七、🔴 可观察变化（**记账，不静默**）

| # | 变化 | 依据 | 影响面 |
|---|---|---|---|
| ① | **出向 DC 请求多一个 `traceparent` 头** | Go `communityfact/wire.go:104` 就是这么做的 ⇒ RAG 此前**少一个** | 全部走 `communityfact` 的投递链路（like / comment / favorite） |
| ② | **rpc / MQ / communityfact 的 span 从 no-op 变真**（会被导出） | Go 侧本来就是真建 span | `RpcInterceptor` / `MqConsumer` / `Dispatcher` / `AuthorityHandler` / `MessageDomainService` / `UserDomainService` / `AdminSpans` / `FavoriteFactDispatchScheduler` |
| ③ | **响应头的 traceparent 与导出物同源**（此前不同源） | Go 只有一个 provider | 10 个 api 模块 |
| ④ | ⚠️ **Go 的 `otel.SetErrorHandler` 在 OTel Java 1.44.0 里没有对应物** | 实测：api / sdk / sdk-trace / sdk-common **四个 jar 里都没有 `ErrorHandler` 这个类/接口** | 导出失败由 SDK 默认路径处理 ⇒ **已知残留** |

---

## 八、证据边界（**不声称已证明的**）

1. ⚠️ **Go 侧未活体对拍** —— 本轮的 Go 事实全部来自**逐行读源码**（`core/trace/agent.go` / `core/trace/propagation.go` / `core/service/serviceconf.go` / `rest/handler/tracehandler.go`）⇒ **没有**真跑 Go 进程看 jaeger。
2. ⚠️ **真 jaeger 未接触** —— 守门用的是**自制的假 OTLP 接收端** ⇒ 证的是「OTLP/gRPC 字节真的出了机器」，**不是**「jaeger 真的收到了」。
3. ⚠️ **采样比例只覆盖 1.0** —— 本仓 20 个模块都写 `always_on` / `parentbased_always_on` ⇒ `toRatio` 的其它分支（`always_off` / `traceidratio`）**未测**。
4. ⚠️ **协议只支持 `grpc`** —— 与 Go 的 `Batcher: otlpgrpc` 一致；`otelhttp` **未装配**（遇到非 grpc 的 `protocol` ⇒ 抛错，**不静默降级**）。
5. ⚠️ **多进程/多副本下的 agent 行为未验** —— Go 的 `sync.Once` 是**进程内**语义；跨进程各自装一份（Go 侧同款）。
