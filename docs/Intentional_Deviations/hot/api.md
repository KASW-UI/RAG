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

| ID | 一句话 | 方向 | 状态 |
|---|---|---|---|
| **SD-HOT-API-001** | `?page=1&page[]=2`（同名 + `[]` 后缀混用）取**确定值**，而 Go 在两个值之间**随机** | RAG 更确定 | ✅ 已签字（2026-10-23） |
| **SD-HOT-API-002** | 客户端**断连**的取消信号不再向 100 次补全传播（超时语义已保住） | RAG 少一个取消源 | ✅ 已留痕（2026-10-23，第二轮） |
| **SD-HOT-API-003** | Go panic 时指标被误记为 `success`；RAG 按客户端实际看到的结果记 `fail` | RAG 更准 | ✅ 已留痕（2026-10-23，第二轮；该路径无代码可达） |
| **SD-HOT-API-004** | 5 个 `hot_api_*` 指标**真的被暴露**（Go 注册了但无端点暴露） | RAG 单向增强 | ✅ 已留痕（2026-10-23，第二轮） |
| **SD-HOT-API-005** | 下游 gRPC 失败时，日志 `error_reason` 用 **grpc-java** 的形态（`"UNAVAILABLE: …"`）而非 grpc-go 的（`"rpc error: code = Unavailable desc = …"`） | 库层形态差异 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「不复刻」） |
| **SD-HOT-API-006** | 成功路径 `Content-Type` **不带** `charset=utf-8` 参数（Go 带） | 库/框架形态差异 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「不复刻」） |
| **SD-HOT-API-007** | Prometheus 指标带全局标签 `application="hot-api"`（Go 的默认 registry 无此标签） | 全仓约定 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」） |
| **SD-HOT-API-008** | hot-api **注册到 Nacos**（Go 侧 hot-api 完全不注册） | 部署面 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」） |
| **SD-HOT-API-009** | hot-api 的 HTTP 端口取 **18897**（Go 是 8893） | 部署参数 | ✅ **已签字**（2026-10-24 第六轮，用户拍板「保持」） |
| **SD-HOT-API-010** | 补全的**跨请求**并发总上限（RAG 有 64 的封顶，Go **没有**） | **RAG 更保守**（保护下游） | ✅ **已签字**（2026-10-24 第十轮，用户拍板「方案 A：池 64 + 登记为签字偏离」） |

---

## SD-HOT-API-001 · 重复键混用时取确定值（Go 侧随机）

- **Component**：`GET /hot/v1/articles` 的 query 解析
- **Go 出处**：
  - `rest/httpx/util.go:59-61` — 名字带 `[]` 后缀的**剥掉后缀**再写进 `params[name]`；
    ⇒ `?page=1&page[]=2` 时，**两个 key 写的是同一个 `params["page"]`**，后者**覆盖**前者；
  - `rest/httpx/util.go:36` — `for name, values := range r.Form` ⇒ **map 遍历序随机**
    ⇒ 谁覆盖谁是**随机的**。
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

## SD-HOT-API-002 ✅ 已留痕 · 客户端**断连**的取消信号不再向 100 次补全传播

> 🟥 **2026-10-24 第十一轮回填**：本条原先**只有汇总表一行、正文缺章节** ⇒ 已补齐。

- **Component**：`GET /hot/v1/articles` 的补全阶段，**客户端断连**时的取消传播
- **Go 出处**：Go 把**请求 ctx** 传进 100 次补全的每一次调用 ⇒ 客户端断连 ⇒ ctx 被取消 ⇒ **在飞的 gRPC 立即取消**
- **RAG 形态**：Servlet 断连**不会自动取消**在飞的 gRPC；RAG 用「请求线程取一次预算 + 显式下传」保住了**超时**语义，
  但**断连**语义不等价 ⇒ 🔴 第七轮**活体坐实**（裸 socket + `SO_LINGER=0` 强制 RST）：
  Go 侧断连后 **1~3ms** 内 8 个在飞 gRPC **全部取消**；RAG 侧**要等到 2s deadline** 才结束，**且断连后继续发第二批**下游调用
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 **D3** 行 + **DRIFT-HOTAPI-025**（⚠️ 后者代码在 `service/common` ⇒ **跨域**，
  hot 域内不可修）

---

## SD-HOT-API-003 ✅ 已留痕 · Go panic 时指标被误记为 `success`，RAG 按客户端实际看到的结果记 `fail`

> 🟥 **2026-10-24 第十一轮回填**：本条原先**只有汇总表一行、正文缺章节** ⇒ 已补齐。

- **Component**：`hot_api_request_total` 的结果标签（`success` / `fail`）
- **Go 出处**：Go 的 `defer` 读的是**命名返回值** `err` ⇒ panic 展开时该变量**仍为 nil** ⇒ 指标被记成 `success`
- **RAG 形态**：Java 按客户端**实际看到的结果**记 `fail`
- **为何接受**：**该路径无代码可达**（panic 是不可恢复的进程级事件，客户端根本收不到响应）⇒ 差异**无可观察后果**
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 **D4** 行

---

## SD-HOT-API-004 ✅ 已留痕 · 5 个 `hot_api_*` 指标**真的被暴露**（Go 注册了但无端点暴露）

> 🟥 **2026-10-24 第十一轮回填**：本条原先**只有汇总表一行、正文缺章节** ⇒ 已补齐。

- **Component**：`hot_api_*` 指标的**暴露面**（是否有 `/actuator/prometheus` 端点）
- **Go 出处**：Go 侧 5 个 collector 全部 `prometheus.MustRegister(...)` 注册到**默认 registry**，
  但 `hotcenter.yaml` 的 Prometheus 块**无 `Host`**、`DevServer` 块在 go-zero v1.10.2 **无对应字段**
  ⇒ **注册了但没有端点暴露**
- **RAG 形态**：Spring Boot Actuator **真的暴露** `/actuator/prometheus` ⇒ 指标**可被抓取**
- **为何接受**：这是**单向增强**（RAG 多一个观察面，Go 侧看不到）⇒ **不改任何指标数值、不改任何业务判定**
- **⚠️ 边界**：本条**只**管「有没有端点」；**分桶边界的数值差异**是另一条（`DRIFT-HOTAPI-018`，**已修** ⇒ 两侧都是 12 个 `le` 边界）
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 **D5** 行

---

## SD-HOT-API-005 ✅ 已签字 · 下游 gRPC 失败的日志串用 grpc-java 形态

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

## SD-HOT-API-006 ✅ 已签字 · `Content-Type` 不带 `charset=utf-8` 参数

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

## SD-HOT-API-007 ✅ 已签字 · Prometheus 指标带全局标签 `application="hot-api"`

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

## SD-HOT-API-008 ✅ 已签字 · hot-api 注册到 Nacos（Go 侧不注册）

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

## SD-HOT-API-009 ✅ 已签字 · hot-api 的 HTTP 端口取 18897

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

## SD-HOT-API-010 ✅ 已签字 · 补全的**跨请求**并发总上限（RAG 有，Go 没有）—— RAG 更保守的一侧

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
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 DRIFT-HOTAPI-028。
