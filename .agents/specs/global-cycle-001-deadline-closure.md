<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314 -->
<!-- spec 落仓：按 AGENTS.md §5.1「提交前 spec 已落仓」 -->

# spec · global CYCLE-001 —— deadline 假断言的循环依赖闭环

> **PR 范围**：`align/global` → `do-my-own-gh`
> **域**：`global`（收口域，§3.5）
> **议题档**：`cycle-deps`（+ `common-tails`）
> **基线**：`do-my-own-gh` @ `58e8314`

---

## 一、要解决什么

**一条 javadoc 假断言，和一个为它而保留的废弃方法，构成真循环。**

```
service/common/.../RpcInterceptor.java:617（改前）   DeadlineState.markExpired(long)   @Deprecated 但保留
   └─ 保留的唯一理由：「users 域的 UserDomainService javadoc 仍以 markExpired 描述 deadline 来源
                        （该描述本身也不准确）…… 删除会让那些域的 javadoc 链接悬空，
                          而跨域文件的改动不由本域决定」
        ↓
service/users/user/user-rpc/.../UserDomainService.java:113（改前）
   └─ 「配合 {@link DeadlineState#markExpired(long)}（由 {@code RpcInterceptor} 调度）」   ← 假断言
```

⇒ **谁先动都会留半截**：先删方法 ⇒ users 域 javadoc 链接悬空；先改 javadoc ⇒ 方法继续无人敢删。
⇒ **必须同一次提交里两步走完** ⇒ 这正是 `global` 收口域存在的第一个具体理由。

### 附带的第二处假断言

`RpcInterceptor.newUnaryServerInterceptor` 的 javadoc（`:132`）写的是
「**到时调用** `markExpired` 把过期时刻挂到 ThreadLocal」——
那是 **M7 修复之前**的旧机制，**现在时**却仍在描述一个已不生效的机制。
⇒ 与 T-10 同源（都是「机制换了，注释没换」），本轮一并订正。

---

## 二、改什么（**同一次提交，两步**）

| 步 | 文件 | 改动 | 为什么必须这一步 |
|---|---|---|---|
| **①** | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java` | `checkDeadline()` 的 javadoc **订正假断言**：删「配合 `markExpired`（由 `RpcInterceptor` 调度）」，改为逐条写明 **主路径 = `onHalfClose` → `bindToCurrent`**（绑在真正跑 handler 的线程上、作用域自动清理）+ **兜底 = `Thread#interrupt()`**（`handle()` 路径 `Future.cancel(true)`），并注明 `markExpired` 已移除及原因 | **先改错误断言**（§3.3.3：先改/删错误断言，再改实现）⇒ 避免出现「javadoc 指向已删方法」的悬空中间态 |
| **②** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` | **删除** `DeadlineState.markExpired(long)`（含 `@Deprecated` 与整段 javadoc），原地留「为何删除 + 为何留到今天」的说明；**顺带修正** `:132` 的过期机制描述 | 依赖 ① 完成 ⇒ 才能安全删 |

⚠️ **单写权**：② 改 `service/common/**` ⇒ **只有 `align/global` 能写**（§5.3b 规则 1）。
⇒ 本 PR 若与别的域并发，**别的域此刻不得改 `service/common`**。

---

## 三、证据（Go ↔ RAG 逐行对照）

### Go 出处（真源 = L2 框架层 `go-zero@v1.10.2`）

```go
// zrpc/internal/serverinterceptors/timeoutinterceptor.go
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
**但必须绑在真正跑 handler 的线程上**（M7 修复的由来）。

### RAG 侧机制（改动后）

| 位置 | 事实 |
|---|---|
| `RpcInterceptor.java:282` | `onHalfClose` → `DeadlineState.bindToCurrent(deadlineEpochMs)`（**handler 线程**） |
| `RpcInterceptor.java:373` | `handle()` 路径的 worker 提交处 → `bindToCurrent` |
| `RpcInterceptor.java:590` | `DEADLINE` 是 `ThreadLocal` ⇒ 设在别的线程上对 handler 不可见 |
| `RpcInterceptor.java:648` | `bindToCurrent` 是 `public static` ⇒ 跨包 `{@link}` 安全 |

### `markExpired` 为何可删（实测）

- **零生产调用方**：全仓 grep 命中 5 处，**全是 javadoc 文本**，无一处真实调用。
- **即便被调也永远不生效**：只做 `DEADLINE.set(...)`；设在超时调度线程上对 handler 线程不可见。
- **不做作用域清理**：`bindToCurrent` 返回的 `Scope` 在 try-with-resources 结束时 `remove()`。

---

## 四、守门测试（**真运行时证据**，非 mock）

新增 `service/common/src/test/java/com/rag/common/observability/RpcInterceptorDeadlineBindingTest.java`

| # | 用例 | 断言 |
|---|---|---|
| ① | `deadlineIsBoundOnHandlerThread` | **真 in-process gRPC 管线**（`InProcessServerBuilder` + **真装** `RpcInterceptor.newUnaryServerInterceptor`）· 客户端见 `DEADLINE_EXCEEDED` · handler 线程名 ≠ 测试线程名 · handler 入口 `remainingMillis() != Long.MAX_VALUE` 且 `> 0` · 睡过 deadline 后 `isExpired() == true` 且 `remainingMillis() == 0` |
| ② | `markExpiredDeclarationIsGone` | 反射断言 `DeadlineState` 上已无 `markExpired` 声明 |
| ③ | `noCodeReferencesMarkExpired` | **守恒断言**：全仓 `service/**` 的 `.java` **代码**里零引用（注释与字符串字面量已剔除） |
| ④ | `noJavadocLinkToMarkExpired` | 全仓无 `{@link ...markExpired}` 的 javadoc 链接 |

⇒ **为什么必须真管线**（§3.3.3）：被测对象是**框架机制**（gRPC 的 `onHalfClose` 线程模型 + ThreadLocal 作用域）。
纯 Mockito 单测**不执行 gRPC 管线** ⇒ 天然测不到「deadline 有没有真的绑到 handler 线程」。

### 🧪 变异测试（证明守门网非空转）

| 变异 | 实测 |
|---|---|
| **M1**：`onHalfClose` 去掉 `bindToCurrent` | ✅ **转红**：`handler 入口 remainingMillis() == Long.MAX_VALUE ⇒ deadline 没有绑到 handler 线程` |
| **M2**：把 `markExpired` 加回 `DeadlineState` | ✅ **转红**：`DeadlineState 仍声明 markExpired` + `以下文件在代码里仍引用 markExpired` |

⇒ 两次变异**均精准转红并还原** ⇒ 不是「锁死错误答案」的绿测试。

---

## 五、台账产出（§5.3b 三档）

| 档 | 文件 | 内容 |
|---|---|---|
| DRIFT | `docs/DRIFT/global/cycle-deps.md` | CYCLE-001 完整闭环记录 + CYCLE-002（未展开） |
| DRIFT | `docs/DRIFT/global/common-tails.md` | T-1 ~ T-10 逐条复核结论（T-1/T-2/T-5/T-9 从「待复核」升级为「已确认真漂移」） |
| DRIFT | `docs/DRIFT/global/cross-domain.md` | 7 条跨域不变量状态 + **X-8 真源分层**（L1 项目层 / L2 go-zero 框架层） |
| DRIFT | `docs/DRIFT/global/_README.md` | 主索引同步（T-10 ✅ 闭环 / 未销账 6-1-4） |
| Pitfalls | `docs/Pitfalls/global/common-tails.md` | PIT-GLOBAL-001「只在项目树里 grep 必然落空」· PIT-GLOBAL-002「档位错置」 |
| Pitfalls | `docs/Pitfalls/global/cycle-deps.md` | PIT-GLOBAL-003「订正假断言必须补运行时证据 + 变异验证」· PIT-GLOBAL-004「守恒扫描的自指陷阱」 |

⚠️ **不碰**：`docs/DRIFT/_INDEX.md` · `docs/Pitfalls/_INDEX.md` · `docs/Intentional_Deviations/_INDEX.md` ·
`AGENTS.md` · `docs/DRIFT/_MANIFEST.json` ⇒ **合并后由主会话统一更新**（§5.3b 硬边界 1）。

---

## 六、验收标准

- [x] `mvn -pl service/common -am package` 通过
- [x] `mvn -pl service/users/user/user-rpc -am package` 通过
- [x] 全量 `mvn clean verify` 通过
- [x] 新增守门测试 4 条全绿，**且**两组变异均转红
- [x] 台账三档写回（只写 `global` 子目录）
- [x] spec 已落仓

---

## 七、本 PR **不做**什么（明确划界）

| 不做 | 原因 |
|---|---|
| 修 T-1（Content-Type 大小写） | 需连带改**各域业务模块**断言 `goReadsBody` 边界的测试 ⇒ 跨域，另起一轮 |
| 修 T-9（熔断 target 前缀） | 需先做「devh starter 能否拿到 channel target」的可行性探针 |
| 修 / 签 T-2、T-5（MaxConns 作用域） | 🔴 **档位错置待用户拍板**（代码自称「有意」但无签字）⇒ 不自行决定 |
| 展开 T-4 / T-6 / T-7 / T-8 | 需真 Tomcat / 真 PG 判定，另起一轮；且 T-6/7/8 同文件，**一次拍板一次改** |
| 展开 X-1 ~ X-7 跨域不变量 | 各自需真 PG / 真启动 / 跨域协调，另起一轮（排期见 `cross-domain.md` §二） |
| 动 `service/common` 以外的域代码 | §5.3b 规则 1：`service/common/**` 只有 `align/global` 能写；其它域只读 |

---

## 八、已知残余

| 残余 | 说明 |
|---|---|
| **`markExpired` 的历史引用** | 保留在 `RpcInterceptor.java` 的删除说明 javadoc 里（`{@code markExpired(long)}`，**非** `{@link}`）⇒ 用例 ④ 只禁 `{@link}` 悬空链接，不禁历史说明 |
| **javadoc 链接悬空的历史面** | 本轮只保证 `service/**` 内无 `{@link ...markExpired}` ⇒ 其它目录（如 `tools/`）不在扫描范围（本轮未改动） |
| **`DRIFT-COMMON-005`（`bindToCurrent` 签名是否允许破坏性变更）** | ⏸ **仍未决**（交接 #3）⇒ **唯一被反复确认仍未决的项** ⇒ 本 PR **不动** `bindToCurrent` 的签名 ⇒ 不与之冲突 |
