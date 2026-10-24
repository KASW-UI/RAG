<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 · 本条已于本轮闭环（同一提交两步走完） -->

# global · 循环依赖台账（cycle-deps）

> **归属**：`global` 收口域的「cycle-deps」议题档（§5.3b）。
> **对象**：**修法同时落在 `service/common` 与别的域代码**的漂移 ⇒ 单域改不动 ⇒ 必须 `global` 出手。

---

## 一、CYCLE-001 ✅ **已闭环**（2026-10-25）

### 1.1 循环本体

```
service/common/.../RpcInterceptor.java:617（改前）   DeadlineState.markExpired(long)   ← @Deprecated 但保留
        │  保留的唯一理由（RpcInterceptor.java:603-611（改前）的 javadoc 明写）：
        │    「service/users/user/user-rpc 的 UserDomainService javadoc 仍以 markExpired(long)
        │      描述 deadline 的来源（该描述本身也不准确……）…… 删除本方法会让那些域的 javadoc 链接悬空，
        │      而跨域文件的改动不由本域决定」
        ↓
service/users/user/user-rpc/.../UserDomainService.java:113（改前）
        「配合 {@link DeadlineState#markExpired(long)}（由 {@code RpcInterceptor} 调度）」
        ← 这行 javadoc 本身就是错的（真相：deadline 由 onHalfClose 的 bindToCurrent 设置）
```

⇒ **真循环**：A 保留废弃方法是为了 B 的 javadoc 链接不断；B 的 javadoc 修好了，A 就可以删方法。
**谁先动都会留半截** ⇒ 必须**同一次提交**里两步走完。

⇒ 🔴 **这正是 `global` 存在的第一个具体理由** —— 它就是那条「能同时动 `service/common`
与 `service/users/**`」的合法落点（§5.3b 规则 2）。

### 1.2 本轮的实测事实（不是照抄台账）

| 事实 | 证据 |
|---|---|
| `markExpired` **零生产调用方** | 全仓 grep：5 处命中**全是 javadoc 文本**（`RpcInterceptor.java:132` / `:266` / `:607` / `:617`（定义本身）+ `UserDomainService.java:113`）——**以上行号为改前**（改后该方法已不存在）⇒ **无一处真实调用** |
| deadline 的**实际来源** | `RpcInterceptor.java:282`（`onHalfClose` → `DeadlineState.bindToCurrent(deadlineEpochMs)`）与 `RpcInterceptor.java:373`（`handle()` 路径的 worker 提交处 → `bindToCurrent`）⇒ **两处都是 `bindToCurrent`，都不是 `markExpired`** |
| `markExpired` 即便被调用也**永远不生效** | 它只做 `DEADLINE.set(...)`；`DEADLINE` 是 `ThreadLocal`（`RpcInterceptor.java:590`）⇒ 设在超时调度线程上对 handler 线程**不可见**（M7 修复的由来，`RpcInterceptor.java:263-275` 的 javadoc 已记） |
| `bindToCurrent` **可见性足够** | `public static`（`RpcInterceptor.java:648`）⇒ 跨包 `{@link}` 安全 |

### 1.3 改动内容（**两步，同一次提交**）

| 步 | 文件 | 改动 |
|---|---|---|
| **①** | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java` | `checkDeadline()` 的 javadoc **订正假断言**：删掉「配合 `DeadlineState#markExpired(long)`（由 `RpcInterceptor` 调度）」，改为逐条写明 **① 主路径 = `onHalfClose` 的 `bindToCurrent`**（绑在真正跑 handler 的线程上、作用域自动清理）**② 兜底 = `Thread#interrupt()`**（`handle()` 路径 `Future.cancel(true)`）⇒ 并注明 `markExpired` 已移除及原因 |
| **②** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` | **删除** `DeadlineState.markExpired(long)`（含 `@Deprecated` 与整段 javadoc）⇒ 原地留一段「为何删除 + 为何留到今天」的说明（含指向本台账的链接）<br>**顺带修正** `newUnaryServerInterceptor` 的 javadoc（`:132`，改前原文写「到时调用 `markExpired`」）—— 原文写「到时调用 `markExpired` 把过期时刻挂到 ThreadLocal」，那是**M7 修复前**的旧机制描述，属**现在时的过期断言** ⇒ 改为「把 deadline 过期时刻绑定到真正跑 handler 的线程（`bindToCurrent`，作用域自动清理）」 |

⚠️ **顺序纪律**（§3.3.3）：「修复漂移时**先改/删错误断言，再改实现**」⇒ 本轮**先改 users 侧 javadoc**，
**再删 common 侧方法** ⇒ 避免出现「javadoc 链接指向已删除方法」的悬空中间态。

### 1.4 守门测试（**真运行时证据**，非 mock）

新增：`service/common/src/test/java/com/rag/common/observability/RpcInterceptorDeadlineBindingTest.java`

| 用例 | 断言 |
|---|---|
| ① `deadlineIsBoundOnHandlerThread` | **真 in-process gRPC 管线**（`InProcessServerBuilder` + **真装** `RpcInterceptor.newUnaryServerInterceptor`）<br>· 客户端见 `DEADLINE_EXCEEDED`（与 Go `ctx.Done() → DeadlineExceeded` 同形）<br>· handler 线程名 **≠** 测试线程名（证明绑定发生在 handler 线程）<br>· handler 入口 `remainingMillis() != Long.MAX_VALUE` 且 `> 0`（证明**已绑定**而非「未设置」）<br>· handler 睡过 deadline 后 `isExpired() == true` 且 `remainingMillis() == 0`（**核心断言**：订正后的 javadoc 为真） |
| ② `markExpiredDeclarationIsGone` | 反射断言 `DeadlineState` 上**已无** `markExpired` 声明 |
| ③ `noCodeReferencesMarkExpired` | **守恒断言**：全仓 `service/**` 的 `.java` **代码**里零 `markExpired` 引用（注释与字符串字面量已剔除） |
| ④ `noJavadocLinkToMarkExpired` | 全仓 `service/**` 无 `{@link ...markExpired}` 的 javadoc 链接（避免悬空链接被当成证据） |

⇒ **为什么必须是真管线**（§3.3.3）：被测对象是**框架机制**（gRPC 的 `onHalfClose` 线程模型 + ThreadLocal 作用域）。
纯 Mockito 单测**不会**执行 gRPC 管线 ⇒ 天然测不到「deadline 有没有真的绑到 handler 线程」。

### 1.5 🧪 变异测试（**证明守门网不是「错误答案的守卫」**）

| 变异 | 预期 | 实测 |
|---|---|---|
| **M1**：`onHalfClose` 里**去掉** `bindToCurrent` | ① 转红 | ✅ **转红**：`handler 入口 remainingMillis() == Long.MAX_VALUE ⇒ deadline 没有绑到 handler 线程` |
| **M2**：把 `markExpired` **加回** `DeadlineState` | ②③ 转红 | ✅ **转红**：`DeadlineState 仍声明 markExpired` + `以下文件在代码里仍引用 markExpired` |

⇒ **两次变异都被精准抓住并还原** ⇒ 守门网**非空转**，也不是「锁死错误答案」的绿测试。

### 1.6 Go 出处（真源 = L2 框架层）

`go-zero@v1.10.2/zrpc/internal/serverinterceptors/timeoutinterceptor.go`：

```go
ctx, cancel := context.WithTimeout(ctx, t)
defer cancel()
...
select {
case <-done:      return resp, err
case <-ctx.Done(): err := ctx.Err(); ...   // DeadlineExceeded
}
```

⇒ Go 侧 deadline **随 ctx 传递**，handler goroutine 里 `ctx.Done()` 直接可读。
⇒ Java 侧**无 ctx 传播**（grpc-java 限制）⇒ 用 `ThreadLocal` + `bindToCurrent` 表达，
**但必须绑在真正跑 handler 的线程上** ⇒ 本台账守的就是这一条。
⚠️ 本条**不**主张「RAG 与 Go 的 deadline 语义完全等价」—— 那是 DRIFT-018 已登记的 Java 化差异。

---

## 二、CYCLE-002 ⏸ **未展开**（登记备查）

`service/common` 与别的域之间**可能**还有同型循环（本轮**只**发现并闭环了 CYCLE-001）。
⇒ **本轮未做系统性排查**（那需要跨全部域扫 javadoc 的 `{@link}` 悬空与跨文件方法引用）。
⇒ 建议下一轮：全仓扫「`service/common` 里被 `@Deprecated` 保留、且 javadoc 里点名了别的域」的方法
⇒ 每一处都是 CYCLE 同型。

---

## 三、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **首版 + CYCLE-001 闭环**。`align/global` @ `58e8314` 产出。两步走完（users 侧订正 javadoc → common 侧删方法），配真 in-process gRPC 守门测试 4 条 + 变异测试 2 组（均转红）。登记 CYCLE-002 待查 |
