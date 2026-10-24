<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 -->

# global · 循环依赖踩坑档（cycle-deps）

---

## PIT-GLOBAL-003 🔴 **订正一句假断言，必须同时补上它的运行时证据**

### 现象

`UserDomainService#checkDeadline` 的 javadoc 原文声称 deadline 来自
「`DeadlineState#markExpired(long)`（由 `RpcInterceptor` 调度）」—— **假断言**
（那个方法零生产调用方，且设在超时调度线程上对 handler 线程不可见）。

本轮把它**改对**了。⚠️ 但**改对 ≠ 闭环**：

> 如果只是把「假断言 A」换成「断言 B」，而 B 同样没有运行时证据支撑，
> ⇒ 那只是**把一句假话换成另一句假话**，而且更难发现（因为它现在长得像已修）。

### 正确处置（三件套，缺一不可）

| # | 动作 | 本轮的实现 |
|---|---|---|
| ① | **改断言** | javadoc 改为逐条写明真实机制（`onHalfClose` → `bindToCurrent`，绑在 handler 线程上） |
| ② | **补运行时证据** | `RpcInterceptorDeadlineBindingTest#deadlineIsBoundOnHandlerThread`：真 in-process gRPC 管线 + 真拦截器 ⇒ 断言 handler 线程上 `remainingMillis() != Long.MAX_VALUE` 且过期后 `isExpired() == true` |
| ③ | **变异验证** | 注入变异（去掉 `bindToCurrent`）⇒ 用例**必须转红** ⇒ 否则 ② 可能只是个「绿摆设」 |

### ⚠️ 变异是第 ③ 步，不是可选项

AGENTS.md §3.3.3：**「绿测试可能是『错误答案的守卫』，比没有测试更危险」**。
本轮实测两组变异（见 `DRIFT/global/cycle-deps.md` §1.5）**都精准转红** ⇒ 守门网非空转。
⚠️ **只跑 RAG 的探针不算数**（§3.3 强制三条之二）：变异测试只能证明「测试不是假绿」，
**不能证明「测试的期望值是对的」** ⇒ 期望值必须**回到 Go 出处**逐行核对
（Go 侧：`go-zero/zrpc/internal/serverinterceptors/timeoutinterceptor.go` 的 `context.WithTimeout` + `select ctx.Done()`）。

---

## PIT-GLOBAL-004 ⚠️ **守恒扫描会命中它自己**（自指陷阱）

### 现象

守门用例 ③ 的实现是「全仓扫 `service/**` 的 `.java`，看代码里还有没有 `markExpired`」。
第一次跑 ⇒ **红了，而且是红在自己身上**：

```
以下文件在代码里仍引用 markExpired（注释/字符串已剔除）：
  [service/common/src/test/java/com/rag/common/observability/RpcInterceptorDeadlineBindingTest.java]
```

⇒ 守门测试的源码里**必然**含这个字面量（断言字符串 + 扫描用的 `Pattern` 字面量）。

### 两个叠加的坑

| # | 坑 | 处理 |
|---|---|---|
| ① | **代码扫描**命中守门文件自己的**字符串字面量** | 扫描前先**剔除注释与字符串字面量**，只留可执行代码 |
| ② | **javadoc 链接扫描**命中守门文件自己的 `Pattern.compile("...markExpired")` | 该扫描跑在**原始文本**上 ⇒ 必须**排除守门文件自身** |

⇒ ⚠️ **排除范围必须精确到「这一个文件」**，不能排除整个测试目录 ——
否则「换个文件再引入一次」就能绕过守门。

### 连带教训

「剔除注释」这一步**不是可有可无的洁癖**：本仓的台账纪律要求把历史写成注释
（如「原 `markExpired` 已删除」）⇒ 不剔除就会把**说明性文字**误判成「代码引用」⇒ **假红**。
⇒ 守恒断言的判定口径必须写清楚：**「代码」指剔除注释与字符串后的可执行部分**。

### 出处

- 踩坑位置：`service/common/src/test/java/com/rag/common/observability/RpcInterceptorDeadlineBindingTest.java`
- 关联：`docs/DRIFT/global/cycle-deps.md` §1.4 / §1.5
- 2026-10-25，`align/global` @ `58e8314`
