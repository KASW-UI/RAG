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
- 踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../../Pitfalls/global/common-tails.md) PIT-GLOBAL-002「档位错置」。

---

## SD-2026-10-25-CAND-12 🟡 **待签字候选（未签字）** —— 客户端断连后**在飞的 gRPC 不会被提前取消**

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字候选（未签字）** —— 属**框架限制**，不是「RAG 偷懒」<br>⚠️ **签字权在用户** ⇒ agent **未**自行批准 |
| 类别 | **框架限制**（想对齐但**语言/框架做不到** ⇒ 无法靠改 `service/common` 消除） |
| 组件 | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（**共享层** ⇒ 10 个 api 模块全部适用） |
| 对应的 DRIFT 条目 | **T-4**（`hot` 域 `DRIFT-HOTAPI-025`）的**资源面**残留<br>⇒ ⚠️ T-4 的**观测面**（499 可见）**已修**，**不在本档** |
| 行为层 | **资源占用 / 下游压力**（**无 wire 差异** —— 客户端已走，收不到任何东西） |
| 证据强度 | ✅ **真 Tomcat 11.0.24 实测**（嵌入式，裸 socket RST）+ ✅ **Tomcat 源码逐行取证** |

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

### 为什么不能对齐（框架限制的证据）

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

⇒ ⇒ **不是实现缺陷，是容器能力边界。**
⇒ 踩坑留痕 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../../Pitfalls/global/common-tails.md) PIT-GLOBAL-007。

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
- ⚠️ **签字前它仍不是「已批准」** ⇒ 若用户**不**签字，本条应退回 `docs/DRIFT/global/common-tails.md`
  继续作为 🔴 开放项。

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **新增候选 `SD-2026-10-25-CAND-12`**（T-4 资源面残留）。T-4 的**观测面**已修（499 可见）⇒ 不在本档；本档只收**资源面**（断连后在飞 gRPC 不被提前取消）—— 经真 Tomcat 11.0.24 实测 + Tomcat 源码取证确认为**框架限制** ⇒ 落为**待签字候选**（**未签字**） |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 `SD-2026-10-25-CAND-11`（T-2 / T-5 同一条），把「代码自称有意、但无签字」的第三态**归位到待签字候选** ⇒ 等用户拍板 |
| 2026-10-25 | **用户签字** ⇒ 候选号 `CAND-11` 转正为 **`SD-2026-10-25-01`**，状态由「待签字」改为「已签字」。签字决定 = **保留 RAG 的全局闸门，不改成 per-route**。并完成两处落地：代码 javadoc 改为指向本档的指针 + 从 DRIFT 侧移除该条 |
