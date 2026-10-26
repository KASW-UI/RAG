<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314 -->

# global · common 尾巴签字档（common-tails）

> **归属**：`global` 收口域的「common-tails」议题档（§5.3b）。
> **对象**：**别的域**的漂移，但**修法落在** `service/common` ⇒ 落此档的是「**别的域因 common 而对不对**」。
> ⚠️ **与 `docs/DRIFT/common/` 的边界**：common 域问「common 自己对不对」；global 问「**别人**因为 common 而对不对」。

---

## SD-2026-10-25-01：`MaxConns` 闸门作用域 —— RAG **全局一个**，Go **每路由一个**

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字（2026-10-25，用户拍板）** —— 原为待签字候选 `SD-2026-10-25-CAND-11`，签字后转正 |
| 签字决定 | **保留 RAG 的全局闸门**（不改成 per-route） ⇒ 理由见「为什么签字」一节 |
| 组件 | `service/common/protection/GoStyleMaxConnsFilter.java`（**共享层** ⇒ 10 个 api 模块全部适用） |
| 对应的 DRIFT 条目 | **T-2**（`follow` 域 `DRIFT-FOLLOW-008`）· **T-5**（`hot` 域 `D27`）⇒ 两条**同一件事** |
| 行为层 | **过载时的可观察行为**（并发连接上限的作用域） |
| 证据强度 | ✅ **源码级逐行对照**（Go 出处 + RAG 出处均已定位到行）；⚠️ **未做双侧活体压测** |

### ✅ 为什么签字（2026-10-25 用户拍板）

1. **实际影响接近零**：Tomcat 默认 `max-threads=200` ⇒ 请求会**先在容器层排队** ⇒ 理论上到不了 10000 在途
   ⇒ 两种口径在当前部署下**不可观察**；
2. **改成 per-route 不是「改一行」**：Servlet Filter 是**按注册一次、作用于全部路由**的
   ⇒ 要实现 per-route，得按 `HttpServletRequest#getRequestURI()` 分桶计闸门
   ⇒ 是**一次非平凡改造**，且会引入新的「分桶键语义」问题（同一路由多前缀？带 context path？）；
3. **方向上 RAG 更严**（全局总量受限 vs 单路由受限）⇒ 不是「放宽」类风险。

⇒ ⇒ **签字为有意偏离**，不修。

### 🔴 签字前的历史状态（为什么它曾是「第三态」）

| 说法 | 出处 | 判定 |
|---|---|---|
| 「**此处为有意的表达层收敛，已在此显式留痕**，不是等价性的隐藏缺口」 | `GoStyleMaxConnsFilter` 的 javadoc（实现者的自述） | ⚠️ **注释里的「有意」不构成签字** |
| 「🔴 **仍未修**」 | `docs/DRIFT/global/_README.md` §三 T-2 / T-5 | ⚠️ 与上一行**互斥** |
| 全仓 `docs/Intentional_Deviations/` | —— | ❌ **无对应签字条目** |

⇒ 签字前，它**既不在 DRIFT 的「要修」侧，也不在签字档** ⇒ 是个**无归属的第三态**。
⇒ 2026-10-25 由用户拍板签字 ⇒ **正式归位到本档（Intentional_Deviations）**，
并从 `docs/DRIFT/global/` 侧移除该条（§5.3b 硬边界 2：
「不要在 DRIFT 里记『决定不对齐』的偏离」⇒ 决定不对齐的，**只**在本档落笔）。

### 差异的实际面

| 侧 | 闸门作用域 | 上限 |
|---|---|---|
| **Go** | **每条路由一个** latch ⇒ per-route | 每路由 **10000**（`RestConf.MaxConns`，`rest/config.go:52` 的 tag 默认值） |
| **RAG** | **全进程一个** `Semaphore` | 全局 **10000** |

### Go 出处（L2 框架层 `go-zero@v1.10.2`）

```go
// rest/handler/maxconnshandler.go:12-36
func MaxConnsHandler(n int) func(http.Handler) http.Handler {
    if n <= 0 { return func(next http.Handler) http.Handler { return next } }
    return func(next http.Handler) http.Handler {
        latch := syncx.NewLimit(n)          // ← 闭包内新建 ⇒ 每调用一次一个 latch
        ...
```

⇒ `rest/engine.go:132-146` 的 `buildChainWithNativeMiddlewares(fr, route, metrics)` **按路由**调用
`handler.MaxConnsHandler(ng.conf.MaxConns)`（`engine.go:145-146`）
⇒ 前提 `ng.chain == nil`（`engine.go:103`）；而 `ng.chain` **全仓只由 `rest.WithChain` 赋值**
（`rest/server.go:141-145`）
⇒ **本项目 0 处使用 `rest.WithChain`**，10 个 api 全用 `rest.MustNewServer(c.RestConf)`
（`article` 多一个 `rest.WithCors()`，**不设** `ng.chain`）
⇒ **`ng.chain` 恒 nil ⇒ native 中间件必然按路由构建 ⇒ Go 确实是 per-route。**

### RAG 出处

`service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java`
⇒ 构造函数里 `new Semaphore(maxConns)` ⇒ **全进程一个**。

### 可观察差异

**一个端点打满 10000 并发 ⇒ RAG 侧其它端点也一起被拒（503）；Go 侧其它端点不受影响。**
⇒ 方向上 RAG **更严**（全局总量受限 vs 单路由受限）。

### 签字后已做的两处落地

| # | 动作 | 落点 |
|---|---|---|
| ① | 代码 javadoc 的「有意的表达层收敛」自述 ⇒ **改成指向本档的指针**（并加一句「不要因为 Go 是 per-route 就顺手改回」） | `service/common/protection/GoStyleMaxConnsFilter.java` |
| ② | 从 `docs/DRIFT/global/` 侧**移除**该条（只留一行链接） | `docs/DRIFT/global/common-tails.md` T-2 / T-5 |

⚠️ **签字 ≠ 已解决**：签字的是「**决定不对齐**」这件事，不是「行为已对齐」。
⇒ 残留风险（见下）依然存在，**不要**因为签了本条就把它当成已解决。

### 触发面与残留风险

- 触发条件是「**单端点并发 ≥ 10000**」⇒ 正常业务碰不到；
- ⚠️ 但 RAG 的 Tomcat 默认 `max-threads=200` ⇒ 请求会**先在容器层排队** ⇒ 理论上前不到 10000 在途
  ⇒ 现行实现的自述提到这一点。⚠️ **但那只是「当前部署碰不到」**，不是「语义等价」
  ⇒ 运维把 `max-threads` 调到万级做压测时，两者行为就会分叉。

### 守门

`service/common/src/test/java/com/rag/common/protection/GoStyleMaxConnsFilterTest.java`
⇒ 已覆盖闸门本身的借入/归还语义。
⚠️ **但它没有钉住「作用域是全局一个还是 per-route」** ⇒ 这正是本条需要签字的那一面。
⇒ 若选择「签字」，**建议补一条断言**：把作用域（全局/分路由）显式写进测试，
避免它日后悄悄变样而无人察觉。

### 留痕位置

- `docs/DRIFT/global/common-tails.md` T-2 / T-5（**已按 §5.3b 硬边界 2 移除该两条，只留指针**）；
- `docs/DRIFT/global/_README.md` §三 T-2 / T-5（同步）；
- 踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md) PIT-GLOBAL-002「档位错置」。

---

## SD-2026-10-25-02 ❌ **已撤销（2026-10-25 第十轮）** —— 「框架限制」这个记录**不再成立**：T-4 资源面已被**方案⑤ 主动探测写**修掉 ⇒ **本条从未被签字，也不需再签**

| 项 | 值 |
|---|---|
| 状态 | ❌ **已撤销（2026-10-25 第十轮）** —— 本条**从未被签字**（第四轮曾被**误转正**，第八轮已撤销回待签字）；第十轮**问题本身被修掉** ⇒ 「框架限制」这个定性**不再成立** ⇒ **本条作废，不需签字** |
| ⇒ **取代它的签字** | **`SD-2026-10-25-04`**（同档，见下）—— 方案⑤「主动探测写」带来的 **wire 偏离**（客户端先收到 N 行 `103 Early Hints`） |
| 类别 | ⚠️ **待定（第八轮已查证 6 条路径）** —— 原判「**框架限制**」**已被证伪**（生态里确实有办法）⇒ 准确表述：**「在「Tomcat + HTTP/1.1 + 同步 Servlet + 保 `Content-Length`」这组约束下没有 Go 等价路径」** ⇒ 而**这组约束是项目自己选的** ⇒ ⇒ 本条实质是「**要不要为这一点动约束**」的**决策项**，不是事实登记 ⇒ 见 §①~④ |
| 组件 | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（**共享层** ⇒ 10 个 api 模块全部适用） |
| 对应的 DRIFT 条目 | **T-4**（`hot` 域 `DRIFT-HOTAPI-025`）的**资源面**残留<br>⇒ ⚠️ T-4 的**观测面**（499 可见）**已修**，**不在本档** |
| 行为层 | **资源占用 / 下游压力**（**无 wire 差异** —— 客户端已走，收不到任何东西） |
| 证据强度 | ✅ **真 Tomcat 11.0.24 实测 ×2**（① AsyncListener 三回调皆不触发；② **同步 servlet** 形态下：主动 `flush()` 在断连后 **~500ms** 抛 `ClientAbortException`，而轮询 `SocketWrapperBase.isClosed()` **滞后 5.6s**）+ ✅ **Tomcat 源码逐行取证**（HTTP/2 `RST_STREAM` → `AsyncListener.onError` 完整链路）+ ✅ **Spring 官方 `DisconnectedClientHelper` javadoc**（事后分类，非主动信号） |

### ⚠️ 用户 2026-10-25 05:46 的质询：「springboot 做分布式肯定有办法」

⇒ **这个质询成立，本条因此退回待签字。**
原先把它判成「框架限制 ⇒ 不存在决策空间」是**过宽的结论** ——
实测只证明了**「Tomcat 的 Servlet 层不向应用发断连信号」**，
**没有**证明「换容器 / 换栈 / 主动探测 / 下沉到 socket 层」都不可行。

⇒ ✅ **2026-10-25 第八轮：6 条路径全部查完（源码取证 + 2 个真 Tomcat 探针实测）**
⇒ **结论：生态里确实有办法，但每一条都要拿「wire 一致性」或「技术栈一致性」去换。**

### ① 六条路径的查证结果

| # | 路径 | 判定 | 依据 | 代价 |
|---|---|---|---|---|
| ① | **换 Servlet 容器**（Jetty `HttpChannel.Listener#onAbort()`） | 🟡 **技术上可行** | Jetty 服务端 `HttpChannel` 有 `onAbort()` 回调（`HttpChannelState#onReadEof()` 明写 "Called to signal that a read has read -1"） | 🔴 **10 个 api 模块的运行时全换**（`spring-boot-starter-tomcat` → `-jetty`）⇒ 影响面远超本条收益 |
| ② | **换栈**（WebFlux + Netty ⇒ `Channel.isActive()` / `channelInactive`） | 🟡 **技术上可行** | Netty 有连接级事件，Spring WebFlux 可拿到 `Channel` | 🔴 **整个技术栈重写** ⇒ 超出「用 Spring 生态表达 Go 行为」的项目范围 |
| ③ | **下沉到 Tomcat 内部 API + 轮询** | ❌ **实测否决** | ✅ 反射链**是通的**：`RequestFacade` → `Request.getCoyoteRequest()`（`catalina/connector/Request.java:169`）→ 字段 `hook` → `AbstractProcessor.getSocketWrapper()` → `NioSocketWrapper`；**但** `isClosed()` 实测**滞后 5.6 秒**（客户端 986ms 断连 ⇒ 6622ms 才翻 true）⇒ 因为它只在 **Tomcat 真正 close socket** 时才置位（`SocketWrapperBase.java:657-658`）⇒ **比主动写还慢** ⇒ 否决 | — |
| ④ | **HTTP/2 的 `RST_STREAM`** | 🟡 **技术上可行（但偏离 Go）** | ✅ Tomcat 源码完整链路：`Http2UpgradeHandler.java:1808-1822` `reset()` → `http2/Stream.java:216-226` `receiveReset()` → `http2/Stream.java:1461-1494`（`:1464` `resetReceived=true` + `DISPATCH_ERROR`）→ `AbstractProcessor.java:646-652` → `CoyoteAdapter.java:140-157`（`:151/154` `listener.onError`、`:157` `setErrorState`）⇒ **`AsyncListener.onError` 真触发**（HTTP/1.1 **无此路径**，`http11/**` 内 `DISPATCH_ERROR` 找不到） | 🔴 **Go 侧实测是明文 HTTP/1.1**（`Sea-RideTheWind_golang` 全仓无 TLS / 无 `http2` / 无 `h2c` 配置；唯一 `http.Server` 是 `fact-authority`）⇒ **换协议 = 主动偏离 Go** ⇒ 须签字；且要 async 化 + 客户端须支持 h2c |
| ⑤ | **主动探测写**（周期 `flush()`） | ✅ **实测有效**（Servlet 官方 API） | ✅ 探针实测：客户端 **986ms** 断连 ⇒ 主动 `flush()` 在 **1649ms** 抛 `ClientAbortException: Broken pipe`（`catalina/connector/OutputBuffer.java:327-331` 包装）⇒ **延迟 ≈ 探测周期** | 🔴 **必须提前提交响应** ⇒ `Content-Length` 退化成 `Transfer-Encoding: chunked` ⇒ **wire 差异**（Go 是 `Content-Length`）⇒ 须签字；且与 A3 刚修好的「先定 `Content-Length` 保形」逻辑直接冲突 |
| ⑥ | **缩短在飞 gRPC 的 deadline** | ✅ **零改动可用** | RAG 已有 `observability.http-timeout` ⇒ 断连后「最多浪费多久」= 剩余 deadline | ⚠️ **不是语义修复**（Go 是 0ms 取消），但**可观察行为零变化**（客户端已走）⇒ **不需要签字**，只需一个运维决策 |

### ② 两个关键事实（决定推荐顺序）

| 事实 | 依据 |
|---|---|
| **Go 侧 api 是明文 HTTP/1.1** | `Sea-RideTheWind_golang` 全仓无 TLS / 无 `golang.org/x/net/http2` / 无 `h2c` 配置；唯一的 `http.Server` 是 `service/{like,comment,favorite}/rpc/cmd/fact-authority/main.go`（**不是 api 服务端**）⇒ ⇒ **换 HTTP/2 = 主动偏离 Go**，不是「对齐」 |
| **Spring 官方也没有主动信号** | `org.springframework.web.util.DisconnectedClientHelper`（Since 6.1）只**事后按异常分类**（`ClientAbortException` / `EOFException` / `Broken pipe` / `connection reset by peer`）⇒ **官方路径同样是「写失败」** ⇒ ⇒ 「主动探测写」不是野路子，而是**官方推荐的唯一手段**（SSE 心跳也是同一手法） |

### ③ 修正后的定性

⇒ ❌ **「Java/Spring 生态做不到」** —— **已被证伪**（①②④⑤ 都有可行路径）。
⇒ ✅ 准确表述：**「在「Tomcat + HTTP/1.1 + 同步 Servlet + 保 `Content-Length`」这组约束下，没有 Go 等价路径」**
⇒ ⚠️ **而这组约束是项目自己选的，不是生态的极限** ⇒ ⇒ **本条的签字与否，实质是「要不要为这一点去动约束」的决策，不是「技术能不能」的事实登记。**

### ④ 推荐顺序（供拍板）

| 序 | 方案 | 改动量 | wire 变化 | 需签字 | 效果 |
|---|---|---|---|---|---|
| **1** | **缩短在飞 gRPC 的 deadline**（⑥） | 改配置 | ❌ 无 | ❌ **不需要** | 断连后浪费从 2s → N ms（**非 0**） |
| 2 | **主动探测写**（⑤） | `service/common` 加周期写 | 🔴 **有**（chunked） | ✅ 要 | 断连后 ~1 个探测周期内取消 |
| 3 | HTTP/2 + `AsyncListener.onError`（④） | 改协议 + async 化 | 🔴 **有**（协议层） | ✅ 要 | 断连后 ~1ms 取消（最接近 Go） |
| 4 | 换 Jetty（①）/ 换 Netty（②） | 🔴 **换运行时/换栈** | 🟡 取决于实现 | ✅ 要 | 同上，但成本最高 |

⇒ ⚠️ **方案 1 值得单独说**：它**不是**语义修复，但它是**唯一「零改动 + 零 wire 差异 + 零签字」的止损手段**
⇒ **建议先把方案 1 做掉**（把浪费窗口从 2s 压到可接受），**再决定**要不要为「Go 等价的即时取消」去动约束。

⇒ **研究结论已回 ⇒ 本条要么被「方案 1+2/3/4 组合」修掉，要么在「明确保留现约束」的前提下签字认账。**
⚠️ 在此之前**不要**把它当成已定性事实对外引用，也**不要**引用旧版的「框架限制 ⇒ 无决策空间」。

### ❌ 本条作废的最终处置（第十轮）

⇒ **T-4 的资源面已被「方案⑤ 主动探测写」修掉** ⇒ 「在飞 gRPC 不会被提前取消」**不再是事实** ⇒
⇒ **本条（记录「框架限制」的那个）作废**，**不需要签字**。
⇒ **取代它的是 [`SD-2026-10-25-04`](#sd-2026-10-25-04-附记--🟡-官方路径记录可选后续优化--不是必选项)（方案⑤ 带来的 wire 偏离）** —— 那条**已由用户签字**。

---

## SD-2026-10-25-04 ✅ **已签字（2026-10-25，用户拍板）** —— 方案⑤「主动探测写」带来的 **wire 偏离**：客户端先收到 N 行 `103 Early Hints`

| 项 | 值 |
|---|---|
| **状态** | ✅ **已签字（2026-10-25）** —— 用户原话：「**CAND-12：主动探测写（周期 flush()）（签字偏离）**」<br>🔴 **2026-10-25 06:40 用户再次明确三点**：①「**先记录官方处理**」②「**接受现有的 CAND-12 处理**」③「**签字接受 socket 的偏离**」⇒ ⇒ **本条闭环，不再另选方案**；官方路径 ⇒ **登记为「后续优化选项」，不是必选项**（见 §⑦）⇒ **当前实现不因本记录返工** |
| **类别** | **有意偏离**（主动选择与 Go 不同 ⇒ 须签字） |
| **组件** | `service/common/src/main/java/com/rag/common/observability/GoStyleClientDisconnectProbe.java`（**共享层** ⇒ 10 个 api 模块全部适用） |
| **对应的 DRIFT 条目** | **T-4** 的**资源面**（`hot` 域 `DRIFT-HOTAPI-025`）⇒ ✅ **已修**（不再签字认账） |
| **行为层** | **wire 层（响应流的最前面多了 1xx）** + **时序层（499 的定型时点晚于 Go）** |
| **证据强度** | ✅ **真 Tomcat 11.0.24 实测**（`GoStyleClientDisconnectProbeGateTest`，3 条）+ ✅ **变异测试转红**（证明守门非空转） |

### ① 偏离的实际面

| | Go 真源 | RAG（方案⑤ 落地后） |
|---|---|---|
| **响应流最前面** | **没有 1xx** | 🔴 **多出 N 行 `HTTP/1.1 103 Early Hints\r\n\r\n`**（28 字节/行）<br>N = 探测轮数 ≈ 慢请求秒数 ÷ 0.5s<br>⇒ **快请求（< 500ms）零影响** |
| **客户端断连 → 在飞 gRPC 取消** | **1~3ms**（`net/http` 取消 `r.Context()`） | 🟡 **≤ 1 个探测周期**（实测 **461ms**；探测间隔 500ms） |
| **499 的定型时点** | **断连瞬间** | 🟡 **下一个探测周期**（实测 **~505ms**） |

⇒ ⚠️ **字节层面确实多了 1xx**；但 RFC 9110 要求客户端**忽略 1xx** ⇒ **业务语义不变**（客户端解析出的最终响应与 Go 完全一致）。

### ② 为什么必须选这个方案（其余 5 条的代价，见 `SD-2026-10-25-02` §①）

| 方案 | 为何不选 |
|---|---|
| ⑥ 缩短在飞 deadline | 只是**止损**，不是 Go 等价（Go 是 0ms 取消）⇒ 先做，但**不解决本条** |
| ⑤ **主动探测写** | ✅ **本条** —— 唯一能做到「真取消在飞 RPC」且**不动协议 / 不换容器 / 不换栈**的路 |
| ④ HTTP/2 `RST_STREAM` | Go 侧实测是**明文 HTTP/1.1** ⇒ 换协议 = **偏离 Go**（且要 async 化 + 客户端支持 h2c） |
| ③ 轮询 Tomcat 内部 `isClosed()` | ❌ **实测否决**：反射链通，但**滞后 5.6 秒**，比主动写还慢 |
| ①② 换 Jetty / 换 Netty | 🔴 换运行时 / 换整个技术栈 ⇒ 成本远超收益 |

### ③ 关键设计取舍：为什么探针写的是「**直接写 socket 的 1xx**」而不是 `response.flushBuffer()`

- `flushBuffer()` 会**提前提交响应** ⇒ 之后 `setContentType` / `setHeader` 被 Tomcat **静默忽略**
  ⇒ **会丢掉 `Content-Type`**（本仓 E-1 那个 bug 的同款）⇒ **比本条更严重的回归**。
- 直接写 socket 的 `103 Early Hints` **不经过 Tomcat 的 `OutputBuffer`** ⇒ **不提交响应**
  ⇒ **`Content-Type` 与 `Content-Length` 全部保住**（实测 `Content-Length: 23` 未退化成 chunked）。
- ⚠️ 必须 `write` **且** `flush`：Tomcat 的 socket 写缓冲在未满时**不会真正 syscall** ⇒ 只写不 flush **探不出错误**（实测）。

### ④ 已知风险（必须知情）

| 风险 | 缓解 |
|---|---|
| **反射链依赖 Tomcat 内部 API**（`RequestFacade.request` → `getCoyoteRequest()` → `Request.hook` → `getSocketWrapper()`）⇒ Tomcat 升级可能静默失效 | ✅ **守卫测试会红**（`normalClientIsNotAffectedByProbeBytes` 断言「必须看到 1xx」⇒ 反射链一断就红，**不会静默降级**）；探针本身 fail-open（`UNAVAILABLE` ⇒ 不改变现有行为） |
| 裸写绕过 Tomcat `OutputBuffer` ⇒ 理论上可能与业务裸写交错 | 本仓 `ObservabilityResponseWriter` 是**全缓冲式**、worker 不碰 socket ⇒ **当前不触发** |
| 探测间隔是**运维旋钮**（默认 500ms，system property `rag.common.http.disconnect-probe-interval-ms` 可覆盖，下限 50ms） | ⇒ 调小 ⇒ 取消更快但 1xx 更多 |

### ⑤ 守门

`service/common/src/test/java/com/rag/common/observability/GoStyleClientDisconnectProbeGateTest.java` —— **3 条**（真 Tomcat + 真 `HttpMiddleware` + 真 in-process gRPC server）：

1. 🔴 客户端 RST ⇒ **在飞的下游 gRPC 被取消**（实测 461ms，远早于 8s HTTP 超时）**且** HTTP 层很快定型 499
   ⇒ ⚠️ 下游通过 `io.grpc.Context.current().addListener` 观察取消（**不是**靠线程中断）⇒ **断言有判别力**
2. ✅ 客户端不断连 ⇒ body 逐字节相同、**`Content-Length` / `Content-Type` 全保住**
   ⇒ 含**正向对照**（必须先看到 1xx，否则后面的断言全是空转）
3. 🔒 探针字节**不破坏 HTTP 流**（1xx 可被客户端正确忽略）

⇒ **变异测试**：把 `probe(...)` 换成 `State.UNAVAILABLE` ⇒ **3 条全红**（核心断言报「实测取消延迟=**-1ms**」= 从未被取消）⇒ **守门非空转**。

⚠️ `HttpClientDisconnectRealTomcatTest`（既有 3 条）**必须同步改一处**：它的 body 提取用 `indexOf("\r\n\r\n")`（=「第一个空行」）⇒ 1xx 出现后该假设失效 ⇒ 改为「按报文块跳过 1xx」的解析器
⇒ **断言强度不变**（仍要求 `Content-Length == body 字节数`），framing 结论不变。

### ⑥ 留痕位置

- `service/common/src/main/java/com/rag/common/observability/GoStyleClientDisconnectProbe.java`
- `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（切片等待 + 探测 + gRPC Context 取消）
- `service/common/src/test/java/com/rag/common/observability/GoStyleClientDisconnectProbeGateTest.java`
- `docs/DRIFT/global/common-tails.md` T-4 ③ · `docs/Pitfalls/global/common-tails.md` PIT-GLOBAL-007

**签字人：Jason（用户）　签字日期：2026-10-25**（首次签字：同日晚，「CAND-12：主动探测写（周期 flush()）（签字偏离）」；**再次明确签字：2026-10-25 06:40**，「接受现有的 CAND-12 处理」+「签字接受 socket 的偏离」）

---

## SD-2026-10-25-04 附记 · 🟡 **官方路径记录（可选后续优化 —— 不是必选项）**

> **用户 2026-10-25 06:40 指示**：「**先记录官方处理**」+「**把官方的处理列为后续优化选项，而不是必选项**」
> ⇒ ⇒ **本附记只是记录**，**不触发任何返工**；`SD-2026-10-25-04` 的实现（方案⑤）**保持不变**。
> ⇒ **触发重议的条件只有一个**：① 协议切到 HTTP/2（`RST_STREAM` 真事件）或 ② 栈切到 WebFlux/Netty（`channelInactive` 真事件）。

### ① 官方到底提供了什么（全量 grep：spring-web 7.0.9 + spring-webmvc 7.0.9 + spring-boot 4.0.8 + spring-boot-tomcat 4.0.8）

| 官方机制 | 出处 | 它做什么 | 能不能「提前」 |
|---|---|---|---|
| `DisconnectedClientHelper` | `spring-web/.../web/util/DisconnectedClientHelper.java` | **事后**按异常类名 / message 分类（异常名集合 `:48-49`；关键词 `:45`；javadoc 覆盖范围 `:94-96`） | ❌ **不能** —— 输入是**已存在的 `Throwable`** ⇒ 必须先有 IO 失败 |
| `DefaultHandlerExceptionResolver.handleDisconnectedClientException(...)` | `spring-webmvc/.../mvc/support/DefaultHandlerExceptionResolver.java:249` + `:522`（**since 6.2**） | Spring MVC 官方处理断连异常的地方 | ❌ javadoc 原文：「**By default, do nothing since the response is not usable.**」⇒ 官方立场 = **断连后响应不可用 ⇒ 什么都不做** |
| `AsyncListener.onError(AsyncEvent)` | `spring-web/.../context/request/async/StandardServletAsyncWebRequest.java:188`（`startAsync()` 在 `:140` 自动 `asyncContext.addListener(this)` `:159`） | Servlet 规范层唯一的**事件式**入口 | 🟡 **条件性** —— 依赖**容器先发现断连** |
| `WebAsyncManager` 的错误接线 | `spring-web/.../context/request/async/WebAsyncManager.java:342-353` | 容器 error 通知里 `if (ex instanceof IOException)` ⇒ 包成 `new AsyncRequestNotUsableException("Servlet container error notification for disconnected client", ex)`（`:348`） | 🟡 同上 |
| `DeferredResult.onError / onTimeout / onCompletion` | `spring-web/.../context/request/async/DeferredResult.java:178 / :165 / :188`（`@since 5.0`） | **官方给应用层的钩子**（取消在飞工作就挂这里） | 🟡 同上 |
| Boot 侧 | `spring-boot` / `spring-boot-tomcat` 4.0.8 | — | ❌ grep「disconnect / broken pipe / ClientAbort」= **0 命中** ⇒ **Boot 没有任何官方断连检测** |

⇒ **官方全部用法都是「拿到异常之后」**（`AbstractHandlerExceptionResolver:177` · `ExceptionHandlerExceptionResolver:463` · `DefaultHandlerExceptionResolver:249` · `HttpWebHandlerAdapter:77`）⇒ **官方没有「检测」API，只有「事件消费」链路**。

### ② 为什么 HTTP/1.1 下官方链路也早不了（Tomcat 源码）

`tomcat-embed-core-11.0.24` `coyote/AbstractProtocol.java:1494-1503`（`Connection.longPoll()`）：

```java
if (!processor.isAsync()) {
    socket.registerReadInterest();     // ← 只有非 async 才注册读兴趣
}
```

⇒ **async 期间 Tomcat 不读 socket** ⇒ HTTP/1.1 下客户端发 RST，容器**不会主动察觉** ⇒ 事件仍要等「下一次写失败」。
⇒ ⚠️ **这条源码结论在第十轮已被运行时实测独立验证**：「Servlet 里 `request.startAsync()` + 注册 `AsyncListener`，客户端裸 socket `SO_LINGER{1,0}` 强制 **RST** ⇒ **`onError` / `onTimeout` / `onComplete` 一个都不触发**」⇒ **官方 async 链路在 HTTP/1.1 下确实等不到事件**。
⇒ 同期实测（RAG 侧）：轮询 Tomcat 内部 `isClosed()` **滞后 5.6 秒** ⇒ 比主动写还慢（第八轮路径③ 已否决）。

### ③ 组合矩阵（**为什么官方路径不能替代方案⑤**）

| 组合 | 事件何时来 | 要不要探针 |
|---|---|---|
| HTTP/1.1 + 同步（**当前实现**） | 只有我们自己写失败时才知道 | **要**（方案⑤） |
| HTTP/1.1 + async（官方链路） | 同样只有写失败时才知道 ⇒ **不更早** | **还是要**（探针） |
| HTTP/2 + async（官方链路） | Tomcat 收 `RST_STREAM` ⇒ **直接触发 `onError`** ⇒ 真提前 | **不要**（但偏离 Go 的明文 HTTP/1.1） |
| WebFlux + Netty | `channelInactive` ⇒ 真提前 | 不要（换栈） |

### ④ 后续优化选项清单（**都不是必选项**）

| # | 选项 | 落点 | 收益 | 代价 / 为什么现在不做 |
|---|---|---|---|---|
| **O-1** | 用 `DisconnectedClientHelper.isClientDisconnectedException(ex)` 替掉我们自研的断连判定 | `service/common`（探针那一处） | 分类/日志级别与 Spring 官方一致；白拿跨容器（Tomcat / Jetty）判据 | 🟢 **成本最低** ⇒ 若做，是本附记里**唯一值得顺手做**的一项 |
| **O-2** | handler 改 async（`DeferredResult`），取消逻辑搬进 `DeferredResult.onError(...)` | 10 个 api 的 handler + 线程模型 | 事件分类与异常语义标准化（`AsyncRequestNotUsableException`） | 🔴 HTTP/1.1 下**不更早**，却要改 10 个域的 handler + 响应提交时机 + 拦截器链 ⇒ **风险远大于收益** |
| **O-3** | 切 HTTP/2 | 网关 / 客户端 / 全部 api | 真事件 ⇒ **可去掉探针 ⇒ wire 偏离（1xx）也随之消失** | 🔴 **偏离 Go**（Go 侧实测明文 HTTP/1.1）⇒ 需整体决策 |
| **O-4** | 切 WebFlux + Netty | 整个栈 | 真事件 + 官方 `doOnCancel` | 🔴 换栈 ⇒ 成本远超收益 |

⇒ ⚠️ **O-1 ~ O-4 一律是「可选」，不是「必做」** ⇒ **当前实现（方案⑤ + `SD-2026-10-25-04`）保持不变**。

### ⑤ 一条附带的官方立场（值得记住）

对**同步**请求，Spring 官方的默认处理是「**什么都不做**」（`DefaultHandlerExceptionResolver.handleDisconnectedClientException`，since 6.2）
⇒ 而 RAG 侧做的是「**记录 499 + 取消在飞 gRPC**」⇒ **本来就超出官方默认行为** ⇒ 这部分偏离已由 `SD-2026-10-25-04` 认账。

### ⑥ 留痕

- 取证版本：spring-web / spring-webmvc **7.0.9** · spring-boot / spring-boot-tomcat **4.0.8** · tomcat-embed-core **11.0.24**
- 取证方式：`javap`（公开 API + `getOrder()` 字节码）+ 各 jar 的 `-sources` 逐行 + Tomcat 源码 ⇒ **均为源码级**，**未做运行时实测**（本附记不触发改动 ⇒ 无需实测）
- 关联：`docs/DRIFT/global/common-tails.md` T-4 ③ · `docs/DRIFT/global/_README.md` T-4 行

---

### 差异的实际面

| 侧 | 客户端断连后，在飞的下游 gRPC 调用 |
|---|---|
| **Go 真源** | **断连后 1~3ms 内全部取消**（`net/http` 取消 `r.Context()` ⇒ ctx 传播到下游）<br>⇒ 且**断连后不再发新的下游调用** |
| **RAG** | ❌ **不取消** ⇒ 仍要等到**自己的 deadline** 才结束；断连后**仍会继续发新的下游调用** |

⇒ 可观察面：**下游被无谓地压**（资源浪费）；**客户端侧无差异**（客户端已断连，收不到任何东西）。

### Go 出处（L1 项目层）

```go
// Sea-RideTheWind_golang/service/common/observability/http.go:53-56
ctx, cancel := context.WithTimeout(r.Context(), timeout)   // ← 挂在 r.Context() 上
req := r.WithContext(ctx)
```
⇒ `r.Context()` 由 **`net/http` 在客户端断开时取消** ⇒ `ctx` 随之取消
⇒ 所有 **ctx-aware** 的下游调用（gRPC）**立即中止**。

### RAG 出处

`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`
⇒ 容器线程 `doneLatch.await(timeout)` ⇒ 期间**没有任何机制**能感知「客户端已走」
⇒ worker 里的 gRPC 调用只能等到**自己的 deadline**（`GoStyleRequestDeadline`）。

### 为什么（暂）不能对齐 —— **注意这条结论的边界**

⚠️ **下述三条只证明「Tomcat + HTTP/1.1 + Spring MVC」这一种组合下拿不到信号**，
**不证明「整个 Java/Spring 生态做不到」** ⇒ 见上文「用户质询」表的 6 条待查路径。

1. **Servlet 容器不向应用暴露「客户端已断连」信号** ——
   真 Tomcat 11.0.24 实测：Servlet 里 `startAsync()` + `AsyncListener`，
   客户端裸 socket `SO_LINGER{1,0}` 强制 **RST** ⇒
   **`onError` / `onTimeout` / `onComplete` 一个都不触发**；
2. **Tomcat 源码同证** ——
   `org/apache/coyote/AbstractProcessorLight.java:62` 对 `SocketEvent.DISCONNECT`
   明写 **"Do nothing here, just wait for it to get recycled"**；
   `org/apache/catalina/connector/CoyoteAdapter.java:140-160` 的
   `setErrorState(t, true)`（唯一会 fire `onError` 的地方）**只在「非容器线程上的 I/O 错误」时触发**；
3. **应用层也拿不到 socket** —— `org/apache/catalina.connector.Request` 里
   **没有任何 socket 访问器** ⇒ 无法自己轮询「对端还在不在」。

⇒ ⇒ **不是 RAG 的实现缺陷；是「Tomcat + HTTP/1.1 + Spring MVC」这一组合的能力边界。**
⇒ ⚠️ **「组合」两个字是本条的效力边界** —— 换容器 / 换栈 / 下沉到 socket 层都可能越过去
⇒ **不得**把本条读成「Java 生态做不到」。
⇒ 踩坑留痕 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md) PIT-GLOBAL-007。

### 触发面与残留风险

- 触发条件 = **客户端在请求在途期间断连**（浏览器刷新 / 弱网 / 客户端崩溃 / 爬虫超时）；
- 影响 = **下游被无谓地压**（直到请求自己的 deadline）；
- ⚠️ 影响量级取决于**业务自身的 deadline**：RAG 的 HTTP 超时上限（`observability.http-timeout`）
  决定了「断连后最多再浪费多久」⇒ **把该值调小 ⇒ 浪费窗口同步变小**（这是**运维旋钮**，不是语义修复）。

### 守门

`service/common/src/test/java/com/rag/common/observability/HttpClientDisconnectRealTomcatTest.java`
⇒ 守的是**已修的那一面**（499 可见 + framing 不退化）。
⇒ ⚠️ **本条（提前取消）没有守门，也不可能有** —— 容器不给信号 ⇒ 无从断言。

### 留痕位置

- `docs/DRIFT/global/common-tails.md` T-4 ③（**只留指针 + 差异面**，不搬内容）；
- `docs/Pitfalls/global/common-tails.md` PIT-GLOBAL-007（为什么不能对齐）；
- ⚠️ **签字 ≠ 行为已对齐**：签字的是「**这个不等价已被认账**」，不是「行为已一致」。
⇒ 残留风险（在飞调用仍要等到自己的 deadline）依然存在，**不要**因为签了本条就把它当成已解决。

---

## SD-2026-10-25-CAND-14 · 🏷 **待签字候选** —— HTTP 状态行**缺 reason phrase**（Go `HTTP/1.1 200 OK` / RAG `HTTP/1.1 200`）

| 项 | 值 |
|---|---|
| 状态 | 🏷 **候选，建议签** —— **未签字**（登记完就躺着，**不升级为拍板**） |
| 性质分类 | 🔴 **框架限制（容器层）** —— 想对齐但**语言/框架做不到** |
| 组件 | **Tomcat 全体** ⇒ **10 个 api 模块全部适用**（不是某个域的） |
| 对应的 DRIFT 条目 | **T-7**（[`docs/DRIFT/global/common-tails.md`](../../DRIFT/global/common-tails.md) §一·T-7）· 另见 [`docs/DRIFT/global/_README.md`](../../DRIFT/global/_README.md) §三 域表行 |
| 行为层 | **HTTP 状态行的字节形态**（客户端可见）⇒ **不影响任何业务语义** |
| 证据强度 | ✅ **源码级逐行对照**（Go 标准库写状态行处 + go-zero 写状态码处 + Tomcat 写状态行处，**三侧均已定位到行**） |

### 差异的实际面

| 侧 | 状态行 |
|---|---|
| **Go** | `HTTP/1.1 200 OK` · `HTTP/1.1 401 Unauthorized` · `HTTP/1.1 404 Not Found` · `HTTP/1.1 405 Method Not Allowed`（**带 reason phrase**） |
| **RAG** | `HTTP/1.1 200` · `HTTP/1.1 401` · `HTTP/1.1 404` · `HTTP/1.1 405`（**只有状态码，无 reason phrase**） |

### Go 出处（**消费点**，§3.3.1）

- **`go-zero@v1.10.2/rest/httpx/responses.go:183`** —— `doWriteJson` 里 `w.WriteHeader(code)` ⇒ 状态行交给 Go 标准库写；
- **Go 标准库 `net/http/server.go:1596-1611`** 的 `writeStatusLine` ⇒ 其中 **`:1602-1605`**：
  `if text := StatusText(code); text != "" { bw.Write(...code...); bw.WriteByte(' '); bw.WriteString(text) }`
  ⇒ **reason phrase 由 `StatusText(code)` 写进状态行**。

> ⚠️ **Go 出处不在项目树里** ⇒ 在 `Sea-RideTheWind_golang/` 里 grep `writeStatusLine` **必然落空**
> ⇒ 见 [`docs/Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md) **PIT-GLOBAL-001**（查 Go 出处只在项目树里 grep 一定会落空）与 **PIT-GLOBAL-006**（Go 出处要分两层）。
> ⚠️ 本条 Go 事实的取证版本：**Go 1.25.11 标准库**（真源仓库冻结在 `go-zero v1.10.2`，其 `net/http` 依赖宿主 Go 工具链）。

### RAG 出处（**容器层** ⇒ `service/common` 够不着）

- **Tomcat `org/apache/coyote/http11/Http11OutputBuffer.java:372-374`** —— 原文注释：
  > `// The reason phrase is optional but the space before it is not. Skip`
  > `// sending the reason phrase. Clients should ignore it (RFC 7230) and it`
  > `// just wastes bytes.`

  ⇒ **Tomcat 是刻意省略的**，不是漏写；
- 同一方法 **`:358-367`** ⇒ 200 / 400 / 404 三种状态码直接写**预置字节常量**
  （`org/apache/coyote/http11/Constants.java:112` `_200_BYTES` = `"200"` · `:116` `_400_BYTES` = `"400"` · `:120` `_404_BYTES` = `"404"`）
  ⇒ 常量里**本来就没有** reason phrase；
- 取证版本：**tomcat-embed-core 11.0.24**（`-sources.jar`）。

⇒ **落点在容器层**（状态行由 Tomcat 生成）⇒ **不是 `service/common` 的任何一行** ⇒ **共享层改不了**。

### 判定与残留风险

- RFC 7230 §3.1.2 / RFC 9112 **允许**省略 reason phrase ⇒ 客户端按状态码解析 ⇒ **业务语义无影响**，只是**字节级不等价**；
- ⚠️ 本条**不是**「RAG 偷懒」，也**不是**「忘了配开关」—— Tomcat **没有**「把 reason phrase 写回去」的开关；
- ⚠️ **效力边界**：本条**只**覆盖 **HTTP/1.1 状态行**；响应**头**与**体**各条另计（见 `SD-2026-10-25-04` 与 hot 域 `Content-Type` 各条）；
- ⚠️ 「归因尚未定位」这句话**已作废**（第九轮已定位到 `Http11OutputBuffer`）⇒ **别再在 `service/common` 里找修法**。

### 守门

⚠️ **本条没有守门，也不可能有** —— 状态行由容器写，**应用层没有任何 API 能改它** ⇒ 无从断言。

### 留痕位置

- [`docs/DRIFT/global/common-tails.md`](../../DRIFT/global/common-tails.md) §一·T-7（**只留指针 + 差异面**，不搬内容）；
- [`docs/DRIFT/global/_README.md`](../../DRIFT/global/_README.md) §三（T-7 行）。

---

## SD-2026-10-25-CAND-15 · 🏷 **待签字候选** —— 「方法不匹配」这个出口，RAG 比 Go **多带**一个 `Traceparent`

| 项 | 值 |
|---|---|
| 状态 | 🏷 **候选，建议签** —— **未签字**（登记完就躺着，**不升级为拍板**） |
| 性质分类 | 🔴 **框架限制 / 出口层级差异** —— **不是**「RAG 忘了删」 |
| 组件 | `service/common` 的 `GoStyleTraceparentFilter`（`@Order(HIGHEST_PRECEDENCE + 3)`，见 `GoStyleTraceparentFilter.java:190`）⇒ **10 个 api 模块全部适用** |
| 对应的 DRIFT 条目 | **T-12**（[`docs/DRIFT/global/common-tails.md`](../../DRIFT/global/common-tails.md) §一·T-12） |
| 行为层 | **响应头**（`Traceparent` 多一个）⇒ **不影响任何业务语义** |
| 证据强度 | ✅ Go 侧**控制流逐行取证**（**注册点** + 三条出口）；✅ RAG 侧**真 Tomcat 实测**（`ArticleApiAssemblyWiringTest`）<br>⚠️ **未做双侧活体对拍**（Go 侧未起进程）⇒ 「Go 的该响应无 `Traceparent`」目前是**源码级**结论 |

### 差异的实际面

| 出口 | Go | RAG | 判定 |
|---|---|---|---|
| ① 路由命中（path + method 都对） | 带 `Traceparent` | 带 `Traceparent` | ✅ 一致 |
| ② 路径**未注册** ⇒ 404 | **带** `Traceparent` | 带 `Traceparent` | ✅ 一致 |
| ③ **路径命中、方法不匹配** ⇒ 405 | ❌ **不带** | ✅ **带**（多一个） | 🔴 **差异** |

⇒ ⚠️ **别把它读成「RAG 的 `Traceparent` 一律多一个」** —— 差异**只**在「方法不匹配」这**一个**出口。

### Go 出处（**注册点** —— 这是判定的关键，§3.3.1）

- **`go-zero@v1.10.2/rest/engine.go:113-115`** ——
  `handle := chn.ThenFunc(route.Handler)` ⇒ `router.Handle(route.Method, route.Path, handle)`
  ⇒ **含 `TraceHandler` 的链是被「注册进 router」的**（那个 handler 来自 `rest/handler/tracehandler.go:35`）
  ⇒ ⇒ **router 自己生成的响应（405）根本不经过这条链** ⇒ 不带 `Traceparent`；
- **`rest/engine.go:134-138`** —— `TraceHandler` 是 native middleware 链**第一个**（`:146` MaxConns / `:149` Breaker / `:152` Shedding 都在它之后）；
- **`rest/router/patrouter.go:59-83`** —— 分发器的**三条出口**：
  - `:61-68` 路由命中 ⇒ `result.Item.(http.Handler).ServeHTTP(w, r)` ⇒ **经过**注册进去的链 ⇒ **带**；
  - `:71-75` 路径未注册 ⇒ `pr.handleNotFound(w, r)` ⇒ 走 `engine.go:245-251` 的 `notFoundHandler`，
    该处**自建一条以 `handler.TraceHandler(...)` 开头的新链**（`:248`）⇒ **带**；
  - `:77-82` **方法不匹配** ⇒ `pr.notAllowed.ServeHTTP(w, r)`（`:78`）或 `w.WriteHeader(http.StatusMethodNotAllowed)`（`:81`）
    ⇒ **两条都在 router 层** ⇒ **不经过**任何含 `TraceHandler` 的链 ⇒ **不带**；
- **`rest/server.go:58`** —— `opts = append([]RunOption{WithNotFoundHandler(nil)}, opts...)`
  ⇒ 每个 api **默认**都装上了 notFoundHandler ⇒ 出口② 的 404 之所以带 traceparent，是因为它**确实经过**了 `TraceHandler`；
- **`rest/handler/tracehandler.go:61`** —— `propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))`
  ⇒ 注入点在**每路由链内部** ⇒ 这才是「出口③ 拿不到」的**结构性**原因。

### RAG 出处

- 出口③ 落在 **`DispatcherServlet` 内**
  （`service/article/article-api/src/main/java/com/rag/article/article/api/advice/GlobalExceptionHandler.java:339`
  的 `handleMethodNotSupported` ⇒ 404 空 body）
  ⇒ servlet **Filter 链早已跑完** ⇒ 共享层的 `GoStyleTraceparentFilter` **已经注入过头** ⇒ **多带一个**；
- ⚠️ **RAG 侧在 filter 阶段无法知道**「这个请求最终会不会落到方法不匹配」——
  那要等 `RequestMappingHandlerMapping` 匹配完才知道，**那时响应已经开始写了**。

### 实测证据

- ✅ `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:532`
  `methodNotAllowedCarriesTraceparentUnlikeGo`（**真 Tomcat**）——
  `GET /v1/article`（该路径只注册了 POST）⇒ **404 + 空 body + 带 `Traceparent`**
  ⇒ 与 Go 的同一路径（404 空 body、**无** `Traceparent`）**只差这一个头**；
- ⚠️ 该断言是 **characterization test**（钉的是**实测现状**，不是 Go 的事实）⇒ `@DisplayName` 已明说「残留差异」。

### 为什么**不修**（代价 vs 收益）

| 修法 | 代价 |
|---|---|
| 在共享层**自建一份路由表** | 与各域的 controller **重复维护** ⇒ **新的漂移源** |
| 让**各域把路由元数据**暴露给共享层 | **改 10 个域的代码** ⇒ 跨域改动 ⇒ 超出 global 单写权 |

⇒ 为了「少一个**不影响业务语义**的响应头」而引入「10 个域的路由元数据耦合」⇒ **代价远超收益** ⇒ **建议签字认账**。

### 触发面与残留风险

- 影响面 = **10 个 api 全适用**（共享层），但**只影响「方法不匹配」这一个出口**；
- ⚠️ **方向上是「RAG 更全」那一侧**（多带一个头）⇒ `trace-id` 本来就在正常响应里给出 ⇒ **不构成信息泄漏**；
- ⚠️ 若将来有客户端**严格逐字节比对** 405 响应 ⇒ 需在客户端侧兼容（**不是服务端能修的**）。

### 守门

`ArticleApiAssemblyWiringTest.methodNotAllowedCarriesTraceparentUnlikeGo`（characterization test，见上）。

### 留痕位置

- [`docs/DRIFT/global/common-tails.md`](../../DRIFT/global/common-tails.md) §一·T-12（**只留指针 + 差异面**，不搬内容）；
- [`docs/DRIFT/global/lanes/L1-delivery.md`](../../DRIFT/global/lanes/L1-delivery.md) §十二（T-8 落地后才暴露出来的残留）。

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | 🆕 **第二十六轮（L5 · `align/global-signoff`）—— 补登 2 条待签字候选** ⇒ ① 🆕 **`SD-2026-10-25-CAND-14`（T-7 状态行缺 reason phrase）** —— 性质 = **框架限制（容器层）**；Go 出处 = `go-zero@v1.10.2/rest/httpx/responses.go:183` → Go 标准库 `net/http/server.go:1596-1611`（`:1602-1605` 写 `StatusText(code)`）；RAG 出处 = **Tomcat `Http11OutputBuffer.java:372-374`**（原文注释「Skip sending the reason phrase」）+ `Constants.java:112/116/120`（预置常量里本就没有 reason phrase）⇒ **落点在容器层，`service/common` 够不着** ⇒ **候选，建议签** ② 🆕 **`SD-2026-10-25-CAND-15`（T-12「方法不匹配」出口 RAG 多带一个 `Traceparent`）** —— 性质 = **出口层级差异**；Go 出处 = `rest/engine.go:113-115`（**注册点**：含 `TraceHandler` 的链被注册进 router）+ `rest/router/patrouter.go:59-83`（三条出口）+ `rest/engine.go:245-251`（404 自建链）+ `rest/server.go:58`（默认装 notFoundHandler）+ `rest/handler/tracehandler.go:61`（注入点）；RAG 出处 = `GlobalExceptionHandler.java:339`（出口③ 落在 `DispatcherServlet` 内）⇒ **filter 阶段无法预知最终是否落到方法不匹配 ⇒ 共享层改不动** ⇒ **候选，建议签**。⚠️ **两条均登记完就躺着，不升级为拍板**（用户既定偏好） |
| 2026-10-25 | ✅ **第十三轮（官方路径记录 + 再次明确签字）** —— 用户 2026-10-25 06:40：「**先记录官方处理**，然后**接受现有的 CAND-12 处理**，然后**签字接受 socket 的偏离**（但是我需要你**把官方的处理列为后续优化选项，而不是必选项**）」⇒ ① 🔴 **`SD-2026-10-25-04` 状态追加「再次明确签字」**（签字人 Jason，2026-10-25 06:40）⇒ **本条闭环，不再另选方案** ② 🆕 **新增「附记 · 官方路径记录（可选后续优化 —— 不是必选项）」** —— 全量 grep spring-web 7.0.9 / spring-webmvc 7.0.9 / spring-boot 4.0.8 / spring-boot-tomcat 4.0.8：**官方没有「检测」API，只有「事件消费」链路**（`DisconnectedClientHelper` 事后分类 · `DefaultHandlerExceptionResolver.handleDisconnectedClientException` since 6.2「do nothing」· `AsyncListener.onError` + `WebAsyncManager` + `DeferredResult.onError`）；Boot 侧 grep = **0 命中** ③ 关键 Tomcat 事实：`AbstractProtocol.java:1494-1503` —— **async 期间不注册读兴趣** ⇒ HTTP/1.1 下官方链路**也不更早** ⇒ 方案⑤与官方路径**互补而非替代** ④ 登记 **O-1 ~ O-4** 四个**可选**后续优化（O-1 用官方 `DisconnectedClientHelper` 分类 ⇒ 成本最低；O-2 async 化；O-3 HTTP/2；O-4 WebFlux+Netty）⇒ ⚠️ **一律非必选**，**当前实现不返工** |
| 2026-10-25 | ✅ **第八轮：6 条路径全部查完（源码 + 2 个真 Tomcat 探针）** ⇒ **「生态做不到」被证伪**：① 换 Jetty（`HttpChannel.Listener#onAbort()`）🟡可行·换运行时 ② 换 WebFlux+Netty 🟡可行·换栈 ③ **轮询 Tomcat 内部 `isClosed()` ❌实测否决**（反射链通，但实测**滞后 5.6s**，比主动写还慢）④ HTTP/2 `RST_STREAM` ⇒ Tomcat **真触发 `AsyncListener.onError`** 🟡可行但**偏离 Go**（Go 侧实测明文 HTTP/1.1）⑤ **主动探测写 ✅实测有效**（断连后 ~500ms 抛 `ClientAbortException`）但**改 framing** ⑥ 缩短在飞 deadline ✅**零改动·零 wire·零签字** ⇒ ⇒ **修正定性**：不是「生态做不到」，而是「**在既定约束下**没有 Go 等价路径」，而约束是项目自己选的 ⇒ ⇒ **本条从「事实登记」改判为「决策项」**
| 2026-10-25 | ✅ **第十轮（方案⑤ 落地 + 签字）** —— 用户 2026-10-25：「**CAND-12：主动探测写（周期 flush()）（签字偏离）**」⇒ ① 选定**方案⑤**并实现（`GoStyleClientDisconnectProbe` + `HttpMiddleware` 切片等待 + gRPC `Context` 取消）② 🔴 **撤销 `SD-2026-10-25-02`** —— 它记录的是「框架限制」，而**问题本身已被修掉** ⇒ **本条从未被签字也不需再签** ③ 🆕 **新增 `SD-2026-10-25-04`（已签字）** —— 方案⑤ 带来的 **wire 偏离**：客户端先收到 N 行 `103 Early Hints`（快请求零影响）+ **499 定型时点晚于 Go**（~505ms）
| 2026-10-25 | ⚠️ **改回「待签字」+ 降级原分类（用户质询后）** —— 用户 2026-10-25 05:46：「**先等下签字，我想知道有没有其他办法，springboot 做分布式肯定有办法**」。⇒ ① 第四轮把它**误转正为「已签字」**（用户当时只问了「它是什么」，**未点头**）⇒ **改回待签字**，原候选号 `CAND-12` 保留。② 原分类「**框架限制**」**过宽** ⇒ 降级为「**Tomcat 的 Servlet 层不给断连信号**」⇒ 实测只覆盖「Tomcat + HTTP/1.1 + Spring MVC」一种组合 ⇒ **不等于「Java/Spring 生态做不到」**。③ 登记 **6 条待查技术路径**（换容器 Jetty/Undertow · 换栈 WebFlux+Netty · 反射取 Tomcat 内部 socket 状态 · HTTP/2 的 RST_STREAM 路径 · 主动探测写 · 缩短在飞 deadline）⇒ **研究结论回来前不签字** |
| 2026-10-25 | **候选 `SD-2026-10-25-CAND-12` 转正为 `SD-2026-10-25-02`（已签字）**。它是**框架限制**（Servlet 容器不向应用暴露客户端断连信号）⇒ **不存在决策空间** ⇒ 按三分口径属「已定性的事实登记」而非「待拍板项」⇒ 签字只认账事实、**不改任何代码**。<br>⚠️ **该转正已于同日撤销**（用户当时只问了「它是什么」，未点头；且用户随后质询「有没有其他办法」）⇒ 见上一行 |
| 2026-10-25 | **新增候选 `SD-2026-10-25-CAND-12`**（T-4 资源面残留）。T-4 的**观测面**已修（499 可见）⇒ 不在本档；本档只收**资源面**（断连后在飞 gRPC 不被提前取消）—— 经真 Tomcat 11.0.24 实测 + Tomcat 源码取证确认为**框架限制** ⇒ 落为**待签字候选**（**未签字**） |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 `SD-2026-10-25-CAND-11`（T-2 / T-5 同一条），把「代码自称有意、但无签字」的第三态**归位到待签字候选** ⇒ 等用户拍板 |
| 2026-10-25 | **用户签字** ⇒ 候选号 `CAND-11` 转正为 **`SD-2026-10-25-01`**，状态由「待签字」改为「已签字」。签字决定 = **保留 RAG 的全局闸门，不改成 per-route**。并完成两处落地：代码 javadoc 改为指向本档的指针 + 从 DRIFT 侧移除该条 |
