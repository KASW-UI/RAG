<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 -->

# global · 循环依赖踩坑档（cycle-deps）

---

## PIT-GLOBAL-003 🔴 **订正一句假断言，必须同时补上它的运行时证据**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要重核「deadline 由谁设置」这类 javadoc 断言 ⇒「改对 ≠ 闭环」直接适用。
> **旧号**：`PIT-GLOBAL-003` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-03`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确处置 三件套 ＋ `docs/DRIFT/global/cycle-deps.md`

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

## PIT-GLOBAL-004 ⚠️ **守恒扫描会命中它自己**（自指陷阱）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不写守恒扫描** ⇒「扫描会命中它自己」本轮不触发。
> **旧号**：`PIT-GLOBAL-004` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-04`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §两个叠加的坑

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

---

## PIT-GLOBAL-010 ⚠️ **跨域引用扫描会命中「JDK 同名类」** ⇒ 必须有白名单  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不写跨域引用扫描** ⇒「命中 JDK 同名类」本轮不触发。
> **旧号**：`PIT-GLOBAL-010` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-10`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确处置（`JDK_TYPE_NAMES` 白名单）

### 现象

写「`service/common` 的 javadoc 不得出现指向**别的域**类的 `{@link}`」这条扫描规则时，
第一版把 **`{@link System}`** 判成了违规。

### 根因

判定口径是「`{@link X}` 里的 `X` 是不是某个业务域的类」。
⇒ `System` 在 **users 域**恰好也有同名类（`com.rag.users.…System` 之类）
⇒ **JDK 类型名**被误判成「跨域引用」⇒ **假红**。

### 正确处置

⇒ 扫描器维护一份 **`JDK_TYPE_NAMES` 白名单**（`System` / `Object` / `String` / `List` / `Map` …）
⇒ 命中白名单 ⇒ **不算违规**。

### 教训

「按类名判归属」这类规则，**必须**先处理**同名**这一类歧义：
JDK 类型、业务域类型、第三方类型会互相撞名。
⇒ 规则宁可**漏报**也不要**假红** —— 一个会误报的守门，久了就没人信它。

### 出处

- 踩坑位置：`tools/difftest/check-cross-domain-holds.py`
- 关联：`docs/DRIFT/global/cycle-deps.md` §二（CYCLE-002 = 0 命中）
- 2026-10-25，`align/global`
