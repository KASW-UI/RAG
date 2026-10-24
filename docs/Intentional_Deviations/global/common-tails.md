<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314 -->

# global · common 尾巴签字档（common-tails）

> **归属**：`global` 收口域的「common-tails」议题档（§5.3b）。
> **对象**：**别的域**的漂移，但**修法落在** `service/common` ⇒ 落此档的是「**别的域因 common 而对不对**」。
> ⚠️ **与 `docs/DRIFT/common/` 的边界**：common 域问「common 自己对不对」；global 问「**别人**因为 common 而对不对」。

---

## SD-2026-10-25-CAND-11：`MaxConns` 闸门作用域 —— RAG **全局一个**，Go **每路由一个**

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字候选（未签字）** —— ⚠️ **本条是「代码自称有意、但档里没签字」的第三态**，见下 |
| 组件 | `service/common/protection/GoStyleMaxConnsFilter.java`（**共享层** ⇒ 10 个 api 模块全部适用） |
| 对应的 DRIFT 条目 | **T-2**（`follow` 域 `DRIFT-FOLLOW-008`）· **T-5**（`hot` 域 `D27`）⇒ 两条**同一件事** |
| 行为层 | **过载时的可观察行为**（并发连接上限的作用域） |
| 证据强度 | ✅ **源码级逐行对照**（Go 出处 + RAG 出处均已定位到行）；⚠️ **未做双侧活体压测** |

### 🔴 为什么它是「第三态」

| 说法 | 出处 | 判定 |
|---|---|---|
| 「**此处为有意的表达层收敛，已在此显式留痕**，不是等价性的隐藏缺口」 | `GoStyleMaxConnsFilter` 的 javadoc（实现者的自述） | ⚠️ **注释里的「有意」不构成签字** |
| 「🔴 **仍未修**」 | `docs/DRIFT/global/_README.md` §三 T-2 / T-5 | ⚠️ 与上一行**互斥** |
| 全仓 `docs/Intentional_Deviations/` | —— | ❌ **无对应签字条目** |

⇒ 按 §5.3b 判据（「这条差异**要不要**『修回去』？要 ⇒ DRIFT；不要（有意）⇒ Intentional_Deviations」），
它**目前既不在 DRIFT 的「要修」侧，也不在签字档** ⇒ 是个**无归属的第三态**。
⇒ 本条目就是把它**归位**到「待签字候选」：**既不擅自修、也不擅自批**。

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

### 签字即接受 / 不签字即要修

| 选择 | 后果 |
|---|---|
| **签字（本条成立）** | RAG 保留全局闸门 ⇒ 需在 javadoc 里把「有意的表达层收敛」**改成指向本档的指针**（现在的自述没有落点）⇒ 并从 `docs/DRIFT/global/` 的 T-2/T-5 移除 |
| **不签字（要修）** | 改成 per-route ⇒ **落 `service/common`（`align/global` 的单写权范围）** ⇒ 需注意：`Servlet Filter` 是**按注册一次、作用于全部路由**的 ⇒ 要实现 per-route，得按 `HttpServletRequest#getRequestURI()` 分桶计闸门 ⇒ **不是**把 `Semaphore` 换个地方那么简单 |

⚠️ **签字前不许擅自改**（§5.3b：签字权在用户；且「不要因为签了本条就把它当成已解决」的镜像面 ——
**没签也不许当它已对齐**）。

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

- `docs/DRIFT/global/common-tails.md` T-2 / T-5（含逐行 Go/RAG 出处）；
- `docs/DRIFT/global/_README.md` §三 T-2 / T-5；
- 踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../../Pitfalls/global/common-tails.md) PIT-GLOBAL-002「档位错置」。

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 `SD-2026-10-25-CAND-11`（T-2 / T-5 同一条），把「代码自称有意、但无签字」的第三态**归位到待签字候选** ⇒ 等用户拍板 |
