<!-- @new 2026-10-29 · G2 lane（align/g2-message-like-task）· 转派 G4 的共享层反馈单 -->

> 🟥 **本文件是 G2 → G4 的正式反馈单**（AGENTS.md §5.3e 的 7 项格式）。
> **归属**：`align/g2-message-like-task` 工作树产出 · **基线 commit** `29a0bce` · **核对日期** 2026-10-29
> **落点**：`service/common/**` + `service/users/users-common/**` ⇒ **G2 只读** ⇒ **全部转派 G4**。
> ⚠️ **本文件不要求 G4 本轮就落码** —— ① `-010`/`-011`/`-013` **用户已于 2026-10-24 拍板「保持登记，本轮不做」**；
> ② `-011`/`-012` 已查明是**容器层框架限制** ⇒ **根本不可修**。
> ⇒ **本单的性质 = 登记 + 转达**，**不是**「请 G4 立刻动手」。

---

## §0 一句话

**message 域**有 **4 条**响应头/状态行的差异（`-010`/`-011`/`-012`/`-013`），
其中 **2 条落点在共享层**（`-010` charset、`-013` `Traceparent`）、**2 条是容器层框架限制**（`-011` reason phrase、`-012` 空格）。
⇒ **本波不阻塞**；**本单只做登记与转达**。

---

## §1 反馈单（7 项 × 4 条）

### FB-G2-01 · `DRIFT-MSGAPI-010` · ✅ **已闭环（2026-10-29 对齐）** —— 共享层 T-6 第二十五轮已落码（`GoStyleJsonContentTypeAdvice.java:127,151`）⇒ 本反馈单**关闭**

| 7 项 | 内容 |
|---|---|
| **① 现象** | 成功 / 错误响应的 `Content-Type` **缺 `charset`** ⇒ Go `application/json; charset=utf-8` vs RAG `application/json` |
| **② Go 侧 `文件:行号`** | `go-zero@v1.10.2/rest/httpx/responses.go` 写响应时的 Content-Type 设定（`application/json; charset=utf-8`）；⚠️ 精确行号以 Go 侧原文件为准 ⇒ 见本域台账 [`api.md` §7 `DRIFT-MSGAPI-010`](api.md) 的逐条出处 |
| **③ RAG 侧现状** | JSON 转换器的 `defaultContentType` 就是 **`application/json`** ⇒ 落点在 **api 侧的 `WebMvcConfig` 或共享层的转换器配置**（`service/common`） |
| **④ 证据强度** | **E4**（**真 Tomcat** 容器级实测：`MessageApiTimeoutRealTomcatTest` + 6 条路径 × 两侧全量 header dump，产物 `/tmp/difftest-msg-headers/`） |
| **⑤ 建议修法** | 给转换器补一个带 charset 的 media type ⇒ ⚠️ **实施前必须先确认**：改后**全部 10 个 api** 的 JSON 响应头都会变 ⇒ **跨域影响** ⇒ 需**用户拍板** |
| **⑥ 影响面** | **20 个组件共享 `service/common`** ⇒ 影响**全部 10 个 api 组件** |
| **⑦ 是否阻塞本波** | **否** —— 用户 2026-10-24 拍板「**保持登记，本轮不做**」（**不是签字**）⇒ 属**跨域共享面改动**，不阻塞 G2 的任何组件 ✅ |

⚠️ **连带约束（已查实，转达避免重复劳动）**：即使补上 charset，Tomcat 11 也会把 `application/json; charset=utf-8` 序列化成 **`application/json;charset=utf-8`**（**吃掉空格**）⇒ **与 Go 仍差一个空格** ⇒ 那部分是 `DRIFT-MSGAPI-012`（**不可修**，见 FB-G2-03）。

---

### FB-G2-02 · `DRIFT-MSGAPI-013` · ✅ **已闭环（2026-10-29 对齐）** —— 共享层 T-8 第十九轮已落码（`GoStyleTraceparentFilter.java:188-191,270-283`）⇒ 本反馈单**关闭**

| 7 项 | 内容 |
|---|---|
| **① 现象** | 响应**缺 `Traceparent` 头** ⇒ Go `Traceparent: 00-<trace-id>-<span-id>-01`（每个响应都有）vs RAG **无** ⇒ 归类：**响应头集合不对称** ⇒ 影响分布式追踪的**跨域接力**（下游拿不到上游的 trace context） |
| **② Go 侧 `文件:行号`** | `go-zero@v1.10.2/rest/handler/tracehandler.go:61` `propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))`；传播器 `go-zero@v1.10.2/core/trace/propagation.go:9-11`（`TraceContext{}` + `Baggage{}` 复合传播器 ⇒ 写出的正是 `traceparent` + `tracestate`）<br>前提：`service/message/api/etc/messagecenter.yaml:18-22` `Telemetry: {Name: message-api, Endpoint: jaeger:4317, Sampler: 1.0, Batcher: otlpgrpc}` |
| **③ RAG 侧现状** | ① **span 存在**：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:305-310 / 346-351 / 376-378`（`Span.fromContext(ctx)`，打了 `service.name` / `http.route` / `http.method` / `http.status_code`）<br>② **OTel 依赖存在**：`service/message/message-api/pom.xml:131-135` `opentelemetry-exporter-otlp`（全仓 18 个组件都有）<br>③ **缺的只是最后一步**：把 span 的 trace context 写进 HTTP **响应头** |
| **④ 证据强度** | **E3**（Go 侧出处逐行 + RAG 侧代码定位；**未做双侧活体 header dump 对拍**） |
| **⑤ 建议修法** | 在共享层的 `HttpMiddleware` 里，把**当前 span 的 trace context** 注入响应头（`TraceContext` 传播器的 `Inject`）⇒ ⚠️ 需确认注入的是 **span context**（含 span-id）而不是仅 trace-id（Go 写的是 `00-<trace-id>-<span-id>-01` ⇒ **含 span-id**） |
| **⑥ 影响面** | **共享层 `service/common`** ⇒ 全部 **10 个 api** 组件 |
| **⑦ 是否阻塞本波** | **否**（同上，用户 2026-10-24 拍板「保持登记，本轮不做」） |

---

### FB-G2-03 · `DRIFT-MSGAPI-011` · 🔴 **不可修**（容器层框架限制 · **转达即可，不必动手**）

| 7 项 | 内容 |
|---|---|
| **① 现象** | 状态行**缺 reason phrase** ⇒ Go `HTTP/1.1 200 OK` / `401 Unauthorized` / `404 Not Found` / `405 Method Not Allowed` vs RAG `HTTP/1.1 200` / `401` / `404` / `405` |
| **② Go 侧 `文件:行号`** | `go-zero@v1.10.2/rest/httpx/responses.go:183` 写 `code + 空格 + StatusText(code)`；reason phrase 由**标准库**补：Go 标准库 `net/http/server.go:1596-1611`（`:1602-1605` 写 `StatusText(code)`）+ `net/http/status.go:81` |
| **③ RAG 侧现状** | ① **应用层**只写码不带 reason：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:907` `public void setStatus(int sc)` + `:920` `resp.setStatus(sc)`<br>② **容器层（决定性事实）**：Tomcat 的 `Response` **已没有 `setStatus(int, String)`** ⇒ **应用层无法**补 reason phrase<br>⇒ **实测 `javap`**：`tomcat-embed-core` **9.0.63** 有 `setStatus(int)` **+** `setStatus(int,String)`；**10.1.52** 与 **11.0.24** **只剩 `setStatus(int)`**<br>⇒ 本项目 **Boot 4.0.8 ⇒ `tomcat.version=11.0.24`**（`spring-boot-dependencies-4.0.8.pom`）⇒ **无路可走** |
| **④ 证据强度** | **E4**（**实测 `javap` 三个 Tomcat 版本的方法签名** + 真容器状态行 dump） |
| **⑤ 建议修法** | **无** ⇒ 性质 = **框架限制（容器层）** ⇒ **共享层 `service/common` 够不着** ⇒ 已登记于 `docs/Intentional_Deviations/global/common-tails.md` 的 **`SD-2026-10-25-CAND-14`**（🏷 待签字候选 · 性质「框架限制（容器层）」）<br>⇒ ⚠️ **`docs/Intentional_Deviations/**` 不在 G4 写权内**（G4 写权 = `service/common/**` + `service/users/users-common/**` + `docs/DRIFT/global/vs-common.md`）⇒ **签字登记由收口执行者处理** |
| **⑥ 影响面** | 全部 **10 个 api**（Go 侧全都有 reason phrase）⇒ 但**不可修** ⇒ 影响面 = 「展示面」而非「功能面」 |
| **⑦ 是否阻塞本波** | **否**（**不可修** ⇒ 不构成任何 lane 的待办） |

---

### FB-G2-04 · `DRIFT-MSGAPI-012` · 🟡 **不可修**（Tomcat 序列化行为 · **不转派**）

| 7 项 | 内容 |
|---|---|
| **① 现象** | `Content-Type` 分号后的**空格**不可达 ⇒ Go `text/plain; charset=utf-8`（有空格）vs RAG `text/plain;charset=utf-8`（无空格） |
| **② Go 侧 `文件:行号`** | `go-zero@v1.10.2/rest/httpx/responses.go`（响应头写出侧） |
| **③ RAG 侧现状** | **实测**：Tomcat 11 的 `Response.setContentType(String)` 会把 Content-Type **解析成 MediaType 再序列化**，序列化时**吃掉空格**；且 **`setHeader("Content-Type", …)` 也一样**（Tomcat 内部对 Content-Type **转手调 `setContentType`**）⇒ **没有任何写法能产出带空格的 Content-Type** |
| **④ 证据强度** | **E4**（真容器实测 + 框架源码机制） |
| **⑤ 建议修法** | **无** ⇒ 归 **框架限制**，**不可修** ⇒ **不转派 G4**（转派了也是空转） |
| **⑥ 影响面** | 全部 api 的 `text/plain` 响应（**3s 超时那条**等） |
| **⑦ 是否阻塞本波** | **否**（不可修） |

---

## §2 给 G4 的行动项（**只有 2 条**，且**都要等用户拍板**）

| # | 行动项 | 阻塞 G2？ | 前置条件 |
|---|---|---|---|
| 1 | `-010`：给 JSON 转换器补带 charset 的 media type | **否** | ⚠️ **需用户拍板**（改后**全部 10 个 api** 的响应头都变） |
| 2 | `-013`：在共享层 `HttpMiddleware` 里把 span 的 trace context 注入响应头 | **否** | ⚠️ 需用户拍板（**同样跨全部 10 个 api**）+ 需确认注入 **span context**（含 span-id） |

⚠️ **`-011` / `-012` 不在行动项里** —— 它们是**容器层框架限制**，**共享层够不着** ⇒ 转达的目的是「**别再查了、别再派了**」。

## §3 G2 侧的处置（**已在本域完成，无需 G4 介入**）

| 项 | 状态 |
|---|---|
| `message-api` 组件判定 | ✅ **R4 / E4**（不受这 4 条影响 —— 它们是**响应头/状态行**层，**不是**本域的行为语义） |
| `-010`/`-011`/`-012`/`-013` 的**归属** | 登记在 [`api.md`](api.md)（本域台账）⇒ 状态 = **OPEN 但本波不做**（用户 2026-10-24 拍板） |
| ⇒ **结论** | **本单不产生 G2 的任何未闭合项**；G2 阶段一组件判定不受影响 |
