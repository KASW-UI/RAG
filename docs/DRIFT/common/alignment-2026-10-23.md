<!-- @new 2026-10-23 22:5x–23:1x · align/common 工作树产出 · 基线 commit 8422116 -->

> 🟥 **当前态指针（2026-10-25 台账校准轮回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（文件名与首行仍停在 2026-10-23，而正文已有 §六·五 时效性复验、§八 决策记录、§十一 交接结案状态）⇒ **当前状态一律以文件末尾「§十一「交接事项的结案状态（2026-10-25 更新）」」一节为准**。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**

# `service/common` 组件级重对齐（2026-10-23）

> **产出方**：`align/common` 工作树（`RAG.worktrees/common`），基线 commit **`8422116`**（与 `do-my-own-gh` 同点）。
> **核对时间**：2026-10-23 22:49–23:10（真实执行：`mvn -o -pl service/common test` ×4 轮 + `javap` ×2 + `find`/`grep` 全仓扫描）。
> **复验时间**：2026-10-24 19:26–19:55（**三轮**，真实执行：全分支 `git log`/`git diff` 对照 + 全量 `grep -rl`（不加截断）
> + `mvn -o -pl service/common test` 重跑 + 新增守门脚本实跑 + 文档相对链接逐个解析校验 + 台账编号机器计数）。
> **复验推翻了 1 条首版的假结论**（见 §二），**并新增 2 条此前无记录的条目**（DRIFT-COMMON-005 / -006，见 §一·五）。
> **本文件回答**：`service/common` 台账标「✅ 审计完成」之后，还剩什么没对齐。
>
> ⚠️ **本文件所有行号引用均以基线 `8422116` 为准**。`service/common` 正在被别的域并发改动
> （`align/message` 已加 7 个类、`align/users-user` 改了 shedder）⇒ 合并后行号会移位，见 §六·五。
>
> ⚠️ **本文件不改写既有 4 份台账**（`audit.md` / `protection.md` / `http.md` / `deadline.md`），
> 只做**增量**：新发现 + 对既有结论的更正。真源为 Go：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/common/`。

---

## 〇、本轮一句话结论

**台账「✅ 审计完成」的覆盖面不成立。** 该结论来自 2026-10-21 那轮，
而那轮自己写下了一句被后续忽视的话：

> 「本节记录的是**代码层面**的差异结论。`service/common` **从未做过 Go ↔ RAG 运行时对拍**」

本轮按 §3.5「common 不是可选项」重做组件级对照，实测结果：

| 范围 | 结论 |
|---|---|
| 5 个可比包（`communityfact` / `logger` / `observability` / `response` / `snowflake`） | 🔴 **3 个 NOT EQUIVALENT**，1 个（`response`）**逐条等价** |
| §3.3 机制接线（`spring/*`） | 🔴 1 条**可证伪的假框架断言**（已修）；**1 条 H2 形态在 `hot-rpc` 上复发（DRIFT-COMMON-004，未修，归属 `align/hot`）**；2 条常见担忧**已证伪** |
| 跨组件不变量 | 🔴 `communityfact` 消费者两侧不一致（见 §四） |
| 已修 | **3 条 SEMANTIC MISMATCH**（均配回归测试，147 tests / 0 failures）<br>+ DRIFT-COMMON-006 的**本域侧**（死代码标废弃） |
| 已记录未修 | **28 条编号条目**（`DRIFT-COMMON` 3 + communityfact 15 + logger 7 + snowflake 3）<br>另补录 **5 项未编号的观察**（见 §六·六），含 1 条 `UNRESOLVED`、1 条 API 缺陷、2 条跨域归属 |
| 2026-10-24 复验 | 🔴 **推翻了首版 1 条假结论**（「H2 已闭环」实为 9/10）；✅ 其余承重断言均仍成立（见 §六·五）<br>🔴 **新增 2 条**（DRIFT-COMMON-005 / -006），其中 -005 此前被**别的域签了字**却在本域无记录 |
| 📌 决策与交接 | **2 条用户拍板**已记入 §八（守门脚本保留 / COMMON-005 先记账不改）<br>**9 项交接事项**列在 §九（本域一律不越界） |

> 🟥 **2026-10-25 校准轮订正（上表两处数字已过期，详见 §十二）**：
> ① 「**28** 条编号条目」⇒ 实为 **27** 条 —— `DRIFT-COMMON-004` 已由 hot 域 2026-10-25 认领并关闭（§十一 #1）⇒ `DRIFT-COMMON` 未修数 3 → **2**。<br>
> ② communityfact **15 条是对的** —— **§五 的「14 条」才是旧快照**（漏了 §六·六 补录的 `DRIFT-CF-018`）。<br>
> ③ 「9 项交接」⇒ **其中 5 项（#2/#5/#6/#7/#8）的状态从未被记录** ⇒ 真实分布 = **已销账 5 / 未销账 2 / 仍未决 4**（§十二·三）⇒ AGENTS.md §0 的「8 项未决」把「没人看过」当成了「确认未决」。<br>
> ⇒ 本表**其余各行（含第 36 行「3 个 NOT EQUIVALENT」）仍然成立**。

---

## 一、本轮修掉的 3 条（全部带回归测试）

### ✅ DRIFT-COMMON-001 —— gRPC 超时路径的 span 永不结束（`SEMANTIC MISMATCH`）

| | |
|---|---|
| **Go 出处** | `service/common/observability/rpc.go:68-71`：<br>`case <-timeoutCtx.Done(): err := status.Error(codes.DeadlineExceeded, …); MarkTimeout(ctx, err, …); return nil, err` |
| **RAG 落点** | `RpcInterceptor.java:192`（修前）——超时调度器关的是**原始 `call`**；而打标与 `span.end()` 只发生在 `:204-241` 的 `wrappedCall.close()` 里 |
| **机制** | `wrappedCall` 是 `ForwardingServerCall.SimpleForwardingServerCall`，原始 `call` 是它的**委托目标**。关原始 call ⇒ **完全绕过** span 打标与 `span.end()` |
| **可观察面** | 客户端能看到 `DEADLINE_EXCEEDED` ✅，但**遥测里这次超时完全不存在**：span 永不结束 ⇒ 永不导出 ⇒ 既无 `sea.timeout=true`，也持续占内存 |

**为什么两边 `MarkTimeout` / `markTimeout` 本身没有差异**（差点误判的地方）：
`Observability.markTimeout`（`Observability.java:115-127`）与 Go `MarkTimeout`
（`observability.go:108-129`）**逐条对称，且都不 `span.End()`**。
Go 这样做是对的——它的 span 生命周期归 otel 插桩所有，拦截器只加属性。
**RAG 多做了「自己建 span、自己 end span」**（`:163-166` + `:239`），
却没在自己新增的 timeout 分支上补齐这份职责 ⇒ 典型的**无出处 idiomomatization**（§3.2）。

**修法**：超时调度器改为关闭 `wrappedCall`（经 `AtomicReference` 中转，因 `wrappedCall` 在其后才创建），
从而与业务 `close` **共用同一条打标出口**，并复用 M6 已有的 `spanFinalized` CAS 保证只执行一次。

**证据（变异→红→修复→绿，实测）**：

```
新增 service/common/src/test/java/com/rag/common/observability/RpcInterceptorTimeoutSpanTest.java
  ① 修前：assertThat(spans).isNotEmpty()  →  FAILURE（exporter 返回 0 个 span）
  ② 修后：同一测试 →  PASS
  ③ service/common 全量：tests=147 failures=0 errors=0 skipped=0（原 146）
```

⚠️ 该测试**不是**「断言 Bean 存在」。它装真 OTel SDK + 真 `InMemorySpanExporter`，
驱动真实 `ServerInterceptor`，只把 gRPC 传输层换成桩 ⇒ 被驱动的调度器 / `wrappedCall` / span 代码全部真实执行。

---

### ✅ DRIFT-COMMON-002 —— `validateReceipt` 遇缺失 `received_at` 抛 NPE（`SEMANTIC MISMATCH`）

| | |
|---|---|
| **Go 出处** | `communityfact/wire.go:148-149`：`received, err := time.Parse(time.RFC3339Nano, receipt.ReceivedAt); return received.UTC(), err == nil` ⇒ `""` 走 `err != nil` 分支，返回 `(零值, false)`，**不抛** |
| **RAG 落点** | `Wire.java:351-356`（修前）：`try { Instant.parse(receipt.receivedAt) } catch (DateTimeParseException e) { return false }` |
| **机制** | `Instant.parse(null)` 抛 **`NullPointerException`**，不在 `DateTimeParseException` 的 catch 范围内 ⇒ 异常向上冒泡。`Wire.java:343-348` 的守卫块对另外 **7 个**字段都做了 null 防御，**唯独漏了 `receivedAt`** |
| **可观察面** | DataCenter 返回缺 `received_at` 的 receipt 时：Go 判「receipt 不合格」，RAG 抛异常炸穿调用链 |

**修法**：`receivedAt == null || isEmpty()` 前置返回 `Result(null, false)`。

---

### ✅ DRIFT-COMMON-003 —— `AuthorityUnavailable` 判定前先走到根因，404 误降级成 503（`SEMANTIC MISMATCH`）

| | |
|---|---|
| **Go 出处** | `communityfact/authority_http.go:58`：`if errors.Is(err, ErrAuthorityUnavailable)` ⇒ `errors.Is` **从 `err` 出发沿 Unwrap 链逐个比对** |
| **RAG 落点** | `AuthorityHandler.java:195-198`（修前）：`Throwable cause = unwrapCause(ex); if (isAuthorityUnavailable(cause))` |
| **机制** | `unwrapCause`（`:83-89`）一路走到链的**最深**元素；而 `isAuthorityUnavailable`（`:69-78`）**本身已经会沿链遍历**。预遍历的唯一效果是**把中间层的元素全部丢弃** |
| **触发条件** | `AuthorityUnavailableException` 有 `(String, Throwable)` 构造器（`:61-63`，public）⇒ 它自己带 cause 时就位于链的**中间**，被跳过 |
| **可观察面** | 🔴 **HTTP 状态码 404 → 503，客户端直接可见** |

**修法**：删除预遍历，直接 `isAuthorityUnavailable(ex)`；
并**删除已成为死代码的 `unwrapCause`**（留着等于给后人留一份可复发的错误映射 —— 同 `open-items` P3 删 `codeFromRPCError` 的理由）。

---

## 一·五、🔴 本轮新发现但**未修**（跨域归属）

### DRIFT-COMMON-004 —— `hot-rpc` 未把 gRPC 拦截器挂到 server 上（H2 形态复发）

| | |
|---|---|
| **归属** | 🔴 **`align/hot` 域**（`service/hot/hot-rpc` 的 Application 类不是 common 的文件）<br>本文件只登记证据，**未越界修改** |
| **Go 出处** | `service/hot/rpc/hot.go:42` `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))` —— Go 侧 **12 个** rpc 服务（含范围外的 `points` / `security`）**无一例外**全部显式注册 |
| **RAG 落点** | `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/HotRpcApplication.java:44-45` —— `@Import({ RpcServerInterceptorFactory.class, … })` ✅ **有**，但**全文没有 `GrpcServerConfigurer`**，也**没有 `server.intercept(...)`** |
| **机制** | ① 齐了（Bean 存在）② 缺（框架不用它）⇒ **正是 H2 的原始形态** |
| **可观察面** | `interceptCall` / `onHalfClose` 从未执行 ⇒ ① gRPC 无 span；② **无 deadline 传播** ⇒ handler 线程上 `DeadlineState` 恒未设置 ⇒ 业务层 `checkDeadline()` 全是死代码；③ `DeadlineQueryTimeoutInjector` 因拿不到 `DeadlineState` 而**不给 SQL 设 `queryTimeout`**；④ gRPC 无超时（客户端只能等 api 层 `http-timeout`） |
| **额外证据** | `hot-rpc` **无任何 interceptor 接线测试**（其余 9 个 rpc 模块都有 `*GrpcInterceptorWiringTest` 一类）⇒ 缺测试 + 缺接线，**绿灯不构成任何保证** |
| **守门** | 本轮新增 `tools/difftest/check-grpc-interceptor-wiring.py`：**`EXIT=1`，精确指向 `hot-rpc`** |

**修法（属 `align/hot`）**：
1. `HotRpcApplication` 补 `@Bean GrpcServerConfigurer { server -> server.intercept(rpcUnaryServerInterceptor); }`；
2. 补一条 `*GrpcInterceptorWiringTest`，断言 `verify(builder).intercept(...)` **真被调用**（不是断言 Bean 存在）；
3. ⚠️ 第 2 步不可省 —— §3.3：断言「Bean 存在」≠ 断言「机制生效」，而这正是 H2 当年被误判为已修的原因。

**⚠️ 与 `align/hot` 的现状对照**：`align/hot` 已有 3 个 commit（`8638055` / `81804b8` / `4f1b1c9`），
但改的是 **`hot-api`** 侧（`GlobalExceptionHandler` / `HotArticleQueryService` / 新增 4 个 api 测试）
与 `docs/DRIFT/hot/*.md` —— **尚未触及 `hot-rpc` 的 Application 类**。
⇒ 该缺口**仍然开着**，需知会 `align/hot`。

**§3.5 跨组件不变量提醒**：这是**「共享组件的消费方漏接」**这一类缺陷的首次实例。
`service/common` 被 16 个组件依赖，其**接线义务随组件下沉**（见
[`protection.md`](./protection.md)「私有组件变共享 ⇒ 装配义务必须一起搬」），
但**没有任何机制强制消费方履行**。
⇒ 本轮新增的静态守门脚本就是这个缺口的通用解：**把「每个消费方都要配两件」变成一条可跑的检查**。

---

### DRIFT-COMMON-005 —— `DeadlineState` 用**墙钟**，Go 用**单调钟**；且精度被截到毫秒（`SEMANTIC MISMATCH`）

> **2026-10-24 二次复验时补记。** 这是 `service/common` 自己的东西，但**此前没被任何域记录** ——
> 连已被别的域签字的同一限制，common 域的台账里也是空白（见 §六·五·6）。

| | |
|---|---|
| **Go 出处** | 标准库 `context.WithDeadline`（`context.go:625` / `:632`）把 deadline 存成 `time.Time` ⇒ **纳秒精度**，且 `time.Now()` 带**单调时钟读数**、`Add` 会保留它；`time.Until(d)` 走单调读数。Go 侧业务层就是用 `ctx.Deadline()` / `time.Until()` 读预算（实例：`service/knowledge/api/internal/logic/product/search_tools_client.go:184`） |
| **RAG 落点** | `RpcInterceptor.java:589` `DeadlineState.DEADLINE = ThreadLocal<Long>`，存 **epoch 毫秒**；`:170-172` `deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()`；`:602-605` `isExpired()` 与 `:608-612` `remainingMillis()` 都用 `System.currentTimeMillis()` |
| **差异 ①（时基）** | Go **单调钟** vs RAG **墙钟**。`System.currentTimeMillis()` 受 NTP 校正 / `setSystemTime` / 虚拟机快照恢复影响 ⇒ 可能**倒退**（则 `isExpired()` 长时间为 false ⇒ 超时不触发）或**前跳**（则提前判过期） |
| **差异 ②（精度）** | Go **纳秒** vs RAG **毫秒** —— 且是在**创建时**就截断（`timeout.toMillis()`），不是读取时 ⇒ 有效 deadline 时刻本身最多差 < 1ms |
| **🔴 同组件内不一致** | 同一组件的**另一个** deadline 载体 `GoStyleRequestDeadline`（`observability/GoStyleRequestDeadline.java:64`）用的是 `System.nanoTime()`（**单调钟**）+ 纳秒存储 + `withDeadlineAfter(remainingNanos, NANOSECONDS)`（`:115-119`）<br>⇒ **两个载体时基不同**：**出向（HTTP→gRPC）单调钟、入向（gRPC 服务端）墙钟** |
| **可观察面** | gRPC 服务端超时判定时刻、SQL `queryTimeout` 注入值、业务层 `checkDeadline()` 判定 |
| **处置** | 🔴 **未修** —— 改法要动公共 API 签名（`bindToCurrent(long epochMillis)` → 纳秒版），而 `admin-rpc` / `user-rpc` 的**测试**直接调它（`AdminDeadlinePassthroughTest.java:150`、`DeadlinePassthroughTest.java:144`）⇒ **跨域破坏性变更，不得单域决定** |

**对照结论（同一件事的两侧，别混谈）**：
- ✅ `GoStyleRequestDeadline`（**出向**）**纳秒精度 ✅ 与 Go 等价** —— 存 nanos、`applyTo` 用 `withDeadlineAfter(remainingNanos, NANOSECONDS)`。
- 🔴 `DeadlineState`（**入向**）**墙钟 + 毫秒** —— 本条。

⇒ 两个载体**不共享实现**（一个 `ThreadLocal<Long>` nanos、一个 `ThreadLocal<Long>` epoch-millis），
所以「出向已对齐」**不能**推出「入向也对齐」。

---

### DRIFT-COMMON-006 —— `DeadlineState.markExpired(long)` 是死代码；且有一处跨域 javadoc 在描述它（`OBSERVABLE`）

| | |
|---|---|
| **Go 出处** | 无对应物（ctx 由标准库持有，业务方不需要手动「设过期时刻」） |
| **RAG 落点** | `RpcInterceptor.java:597` `markExpired(long epochMillis)` |
| **差异** | 2026-10-24 全仓 grep 实证：**零生产调用方** —— 只有自身定义 + 3 处 javadoc 提及。实际生效路径是 `bindToCurrent(deadlineEpochMs)`（`:281`，`onHalfClose` 里绑定）。<br>且两者有一处实质差别：`bindToCurrent` 返回的 `Scope` 会在 try-with-resources 结束时 `remove()`（**防线程池串味**），`markExpired` **不会自动清** |
| **连带（跨域）** | 🔴 `service/users/user/user-rpc/.../UserDomainService.java:115` 的 javadoc 声称「配合 `DeadlineState#markExpired(long)`（由 `RpcInterceptor` 调度）」—— **与事实不符**：M7 修复后 deadline 由 **`onHalfClose`** 绑定，且**不在调度线程**上。<br>⇒ **该文件属 users 域，本域不改，仅登记并需知会 `align/users-user`** |
| **处置** | ✅ **本域已修**：`markExpired` 标 `@Deprecated`，javadoc 改写为陈述事实（真实路径、为何保留、为何不删）<br>⚠️ **未删除** —— users 域的 javadoc 仍以 `{@link DeadlineState#markExpired(long)}` 链它，删除会造成**跨域悬空链接**；等该域自行清理引用后再删（实测 `mvn -pl service/users/user/user-rpc install` **EXIT=0**，链接未悬空） |

---

## 二、§3.3 机制接线复核（本轮独立自查）

### 🔴 已修：共享类 javadoc 里的**可证伪假框架断言**

`RpcServerInterceptorFactory.java:14-16`（修前）断言：

> 「grpc-spring-boot-starter 会**自动**把所有 `ServerInterceptor` 类型的 Bean 注册到 gRPC server」

这正是 **H2 缺陷的认知源头** —— 当年正是因为信了这句，`@Import` 被误判为「已修」。
**本轮用 `javap` 独立复核**（不采信台账证词），`3.1.0.RELEASE` 实测：

```
javap GrpcServerFactoryAutoConfiguration
  → shadedNettyGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer, List<GrpcServerConfigurer>)
  → nettyGrpcServerFactory(…同上…)
  → inProcessGrpcServerFactory(…同上…)          ← 三个工厂方法全无 ServerInterceptor 形参
javap GrpcServerConfigurer
  → interface GrpcServerConfigurer extends java.util.function.Consumer<ServerBuilder<?>>
```

⇒ **断言为假，已改写为中性描述 + 附 javap 证据**，并写明「每个 rpc 模块要配两件、缺任一件静默失效」。

**顺带纠正的 3 处 Go 出处错误**（§3.1 要求每个 Go 事实带可核对的行号）：
原 javadoc 写的 `service/user/rpc/user.go` 与 `service/user/rpc/admin.go` **两个路径都不存在**，
且漏列 4 个注册点。实测范围内 **10 个** Go rpc 服务**全部**显式注册
（`DisableNativeRpcTimeout` 行 → `AddUnaryInterceptors` 行）：

| 服务 | 行 | 服务 | 行 |
|---|---|---|---|
| `service/user/user/rpc/user.go` | 32 → 43 | `service/user/admin/rpc/admin.go` | 33 → 43 |
| `service/article/rpc/article.go` | 36 → 51 | `service/task/rpc/task.go` | 28 → 44 |
| `service/hot/rpc/hot.go` | 32 → 42 | `service/like/rpc/like.go` | 30 → 41 |
| `service/message/rpc/message.go` | 34 → 44 | `service/comment/rpc/comment.go` | 33 → 54 |
| `service/follow/rpc/follow.go` | 34 → 44 | `service/favorite/rpc/favorite.go` | 40 → 49 |

（另有范围外的 `service/security/rpc/security.go:43`。）

**H2 现状复核（2026-10-24 复验后更正）：🔴 未完全闭环 —— 9/10，`hot-rpc` 漏 ②。**

> ⚠️ **本行在 2026-10-23 首版写的是「10 个 rpc 模块全部有，已闭环」——那句话是错的。**
> 错因与 [`docs/Pitfalls/common/observability.md`](../../Pitfalls/common/observability.md) 坑 4 完全同类：
> 当初的 `grep GrpcServerConfigurer` 输出**被桌面端截断**（输出里明写了 `truncated`），
> 我没察觉就据此外推「全部都有」——**正是我自己写进踩坑档的那条坑，我自己也犯了**。
> 2026-10-24 复验（不加截断、全量 `grep -rl`）才发现 `hot-rpc` 不在其中。

实测（`grep -rl GrpcServerConfigurer --include=*.java service/`，2026-10-24）：

| rpc 进程 | ① `@Import(RpcServerInterceptorFactory)` | ② `@Bean GrpcServerConfigurer` + `.intercept(` |
|---|---|---|
| `user-rpc` / `admin-rpc` | ✅ | ✅ |
| `article-rpc` / `comment-rpc` / `favorite-rpc` | ✅ | ✅ |
| `follow-rpc` / `like-rpc` / `message-rpc` / `task-rpc` | ✅ | ✅ |
| **`hot-rpc`** | ✅ | 🔴 **缺** ⇒ 见 DRIFT-COMMON-004 |

**新增守门脚本**（本轮补）：[`tools/difftest/check-grpc-interceptor-wiring.py`](../../../tools/difftest/check-grpc-interceptor-wiring.py)
—— 逐个 rpc 进程断言「① 与 ② 同时具备」，当前 **`EXIT=1`，精确指向 `hot-rpc`**。
⇒ 这类缺陷**不该再靠人眼数 grep 命中文件数**。


### ✅ 已证伪：台账担心的「`ObservabilityProperties` 双 Bean」不成立

`audit.md:26` 写「同时带 `@Component` + `@ConfigurationProperties`…会产生两个同名类型 Bean」。
实测**两条注册路径互斥**：

| 模块类型 | 注册方式 | 实测 |
|---|---|---|
| **10 个 api** | `@SpringBootApplication(scanBasePackages = {…, "com.rag.common"})` | 组件扫描拾取 `@Component` |
| **10 个 rpc** | 裸 `@SpringBootApplication`（**不扫** `com.rag.common`，避免拖进 servlet 类） | 一律 `@Import` |

同一模块只走一条 ⇒ 不存在同类型双 Bean。**`@Component` 必须保留**（api 路径靠它）。
⚠️ `audit.md:26` 那句「统一走 `@Import` 路径」表述不准确，建议后续更正（本轮未改该文件）。

### ✅ 已证伪：`task-api` 可能没扫到 `com.rag.common` 的担忧

`TaskApiApplication` 的 `@SpringBootApplication` 是**裸注解**，一度可疑；
实测它同时带 `@ComponentScan(basePackages = {…, "com.rag.common"}, excludeFilters = {…})`，
且类 javadoc 把「注解属性不合并、`scanBasePackages` 会悄悄退回」这个坑写清楚了 ⇒ **正确**。

---

## 三、🔴 DRIFT-COMMON-LOG-001：日志 KV 字段在**生产**被丢弃（`SEMANTIC MISMATCH`）

**这是本轮杠杆最大的一条，且差点被自己的证据推翻两次。**

| | |
|---|---|
| **Go 出处** | `logger/logger.go:148-157` 组 8 个 `logx.LogField` → `:168` `logx.WithContext(ctx).Errorw("business_error", fields...)`；go-zero `Encoding` 默认 **json**（`core/logx/config.go:16`），各服务 yaml 未设 ⇒ 逐条落盘 |
| **RAG 落点** | `Logger.java:272-281` `slf4j.atError().setMessage("business_error").addKeyValue("service",…)…` —— 字段**进了** `ILoggingEvent.getKeyValuePairs()` |

**关键分水岭 = 运行环境，两条读数都真实、但不矛盾**：

| 环境 | pattern 来源 | 含 `%kvp`？ | 字段可见？ |
|---|---|---|---|
| `service/common` 纯单测（无 Boot、无 `logback.xml`） | logback **1.5.38** `BasicConfigurator` 默认 | ✅ **含** | **看得见** |
| **真实 Spring Boot 应用** | Boot **4.0.8** `logging/logback/defaults.xml` | ❌ **不含** | **看不见** |

**Boot 4.0.8 默认（从 jar 内解出，非凭记忆）：**
```
CONSOLE_LOG_PATTERN = %clr(%d{…}){faint} %clr(%5p) %clr(%PID:-){magenta}
                      %clr(--- [app][thread]){faint}%clr(%-40.40logger{39}){cyan}
                      %clr(:){faint} %m%n%wEx
```
⇒ **无 `%kvp`**；结构化输出需显式 opt-in `CONSOLE_LOG_STRUCTURED_FORMAT`（默认为空）。

**全仓取证**：`find **/logback*.xml` = **0 命中**；20 个 `application.yml` 的 `logging:` 块**只有 `level:`，无 `pattern`**；
`service/common/pom.xml:84-87` 无 `logstash-logback-encoder`。

**实测输出长这样（单测里，字段在）**：
```
ERROR logger-test-omits-err -service="logger-test-omits-err" event_id="2113…"
  error_code="400" file_line="LoggerTest.java:108" call_chain="…" error_reason="boom"- business_error
```

**可观察面**：Go 每条 `business_error` 有 8~10 个 JSON key；RAG 生产侧只剩 `ERROR … : business_error` 一行文本。
⇒ **日志采集 / 告警 / 排障全线失能**，且 RAG 侧没有任何测试会发现（单测环境反而看得见）。

**未修 —— 需要你拍板**：修法是给 `service/common` 加 `logback-spring.xml`（含 `%kvp`）或统一 `logging.pattern.*`。
⚠️ 这是**跨 20 个模块的共享日志格式变更**，影响面远超 `service/common`，本轮**只诊断未实施**。
按 AGENTS.md §3.3，落地时**必须**配一条真启动的守门测试断言 stdout 含 `call_chain=`，
否则改完无法证明生效（这正是本条自己踩的坑）。

---

## 四、跨组件不变量：`communityfact` 消费者两侧不一致 🔴

**这条不属于任何单个组件，按 §3.5 不塞进组件台账，单列于此并需知会对应域。**

台账 D-2（`_signed-deviations.md`）称「RAG 侧**零消费者**」—— **该结论已过期**（无核对日期，按 §6.2 本就该按 `UNKNOWN` 处理）。
2026-10-23 20:2x 落地的 comment / favorite / follow 三域接了它：

| 域 | Go 侧引用 `communityfact` | RAG 侧引用 |
|---|---|---|
| `like` | ✅ **6 个文件**（`cmd/fact-authority/main.go`、`cmd/fact-dispatch/main.go`、`internal/model/fact_delivery.go` 等） | 🔴 **0**（仅 `LikeDomainFactService.java:29` 一处 javadoc 提及） |
| `comment` | ✅ 6 | ✅ 4 |
| `favorite` | 🔴 0 | 🔴 **10** |
| `article` | 🔴 0 | 🔴 1 |

**2026-10-24 复验（逐分支实测 `git grep -l "com.rag.common.communityfact"`）**：
`like` **仍是 0**（`align/like` 分支上也没有）⇒ **核心结论未变，仍成立**。
唯一变化：`comment` 从 4 个文件增至 **5**（`align/comment` 自己加了一处引用），且新增 `align/comment` 分支。
⚠️ 换言之：**别的域在往这条链路上加消费者，却没人补 `like` 那一半。**

⇒ **`like-rpc` 缺了 Go 侧的整套事实链路装配**（authority 查询端 + dispatch 进程 + delivery model）；
`favorite-rpc` / `article-rpc` 多出了 Go 侧没有的用法。
**归属**：`align/like` 与 `align/favorite` 工作树。本文件只登记，不越界修。

**连带影响**：D-2 原本判 `communityfact` 为「代码已对齐但零运行证据 ⇒ 不算行为对齐」。
现在它成了 **15 个文件、跨 3 个域**的关键路径 ⇒ 那句 `UNRESOLVED` 的**风险等级大幅上升**。

---

## 五、已记录未修（`communityfact` · 14 条）

> 每条均为**读 Go 原文 line-by-line** 得出，Go 事实一律带 `文件:行号`。完整覆盖清单见本次三路子审计报告。

### `SEMANTIC MISMATCH`（5 条）

| # | 项 | Go 出处 | 现状 |
|---|---|---|---|
| DRIFT-CF-001 | `newProcessLogger` 内强制 `validate()`，Go 不校验；空值本应输出 `"unresolved"` | `logging.go:43-67` vs `ProcessLogger.java:150-151` | 未修。⚠️ **跨文件**：需同步改 `favorite-rpc/FavoriteFactProcessLogging.java:76-84` 的 try/catch 降级 |
| DRIFT-CF-002 | `AuthorityHandlerFactory` 走 3 参重载拿**伪造 logger**，Go 用进程信封 logger ⇒ 6 个 metadata 键无保证 | `authority_http.go` + `fact-authority/main.go:29,71` vs `AuthorityHandler.java:106-108` | 未修（建议改 4 参） |
| DRIFT-CF-005 | Dispatcher 的 10s 超时**只发线程中断** + **单线程池** ⇒ 一次挂死永久卡死；Go 的 ctx 取消会释放 goroutine | `process.go:28-30` + `wire.go:97` vs `Dispatcher.java:122,127` + `:96` | 未修。建议改 `HttpRequest.timeout(...)`（与 B-1 同款定性：`setQueryTimeout` 而非 interrupt） |
| DRIFT-CF-006 | 节奏语义：Go `time.NewTicker(1s)` **创建即起算**，RAG **每批后再等满 1s** | `process.go:41,56-60` vs `Dispatcher.java:198-201` | 未修。稳态吞吐两侧不同量级 |
| DRIFT-CF-008 | authority 日志 trace 关联：Go 用 handler ctx，RAG 建 span 后**从不 `makeCurrent()`** ⇒ 三条日志通常无 `trace_id` | `authority_http.go:30-37` + `logging.go:75-81` vs `AuthorityHandler.java:137-143,253` | 未修 |

### `OBSERVABLE DIFFERENCE`（8 条）

| # | 项 | Go 出处 | 现状 |
|---|---|---|---|
| DRIFT-CF-003 | authority 响应体：Go `json.NewEncoder().Encode` **追加尾 `\n`** 且 `escapeHTML` 默认开；RAG 两者都没有 ⇒ **字节不同** | `authority_http.go:72` vs `AuthorityHandler.java:223,239` | 未修（需先定「以哪侧为真」） |
| DRIFT-CF-009 | `deliveryErrorCode`：Go `errors.Is` 只认哨兵**实例**；RAG 按**类型**判 ⇒ `error_code` 取值范围更宽 | `process.go:64-69` + `wire.go:35` vs `Dispatcher.java:288-302` | 未修（`Dispatcher.java:279-286` 的 javadoc 自称「与 `errors.Is` 同构」，失准） |
| DRIFT-CF-011 | OBS-r3 信封 JSON 键顺序**与 Go 相反**（metadata 块前移、trace 块前移到 KV 之前） | `logging.go:50-58,61-65,78` vs `ProcessLogger.java:246-269` | 未修。`ProcessLogger.java:55` 注释称「严格保证顺序」说明是**刻意**选择 ⇒ 应走签字偏离 |
| DRIFT-CF-012 | 时间戳精度：Go **纳秒** / RAG **毫秒** | `logging.go:53` vs `ProcessLogger.java:85-98` | 未修 |
| DRIFT-CF-013 | token 长度阈值：Go `len()` 是 **UTF-8 字节数**，RAG `String.length()` 是 **UTF-16 码元** | `wire.go:87` + `authority_http.go:24` vs `Wire.java:191` + `AuthorityHandler.java:98` | 未修（非 ASCII token 时阈值不等） |
| DRIFT-CF-014 | 畸形 endpoint 报错形态：Go **统一文案**；RAG `URI.create` 直接抛 `IllegalArgumentException` | `wire.go:84-89` vs `Wire.java:194` | 未修 |
| DRIFT-CF-015 | receipt 解码错误少一层 `decode DataCenter event receipt: ` 前缀 | `wire.go:116` vs `Wire.java:240` | 未修 |
| DRIFT-CF-017 | 缺 `InstallFrameworkLogger` 等价物（框架日志未并入 OBS-r3，键未改名，`log_source` 永远只有 `"application"`） | `logging.go:104-135` vs `ProcessLogger.java:46-47` | 未修。⚠️ 是否真触发取决于各域 logback 配置 |

### `UNRESOLVED`（1 条）

| # | 项 | 为什么判不了 |
|---|---|---|
| DRIFT-CF-004 | `Event.payload` 序列化路径不同：Go `json.RawMessage` **逐字输出**；RAG `Object` 由 Jackson 按**运行期类型**重序列化（键序 / 数字字面量 / 重复键全丢） | 取决于 `favorite-rpc` / `comment-rpc` 实际怎么填 `payload`。**不在 `common` 包内可判定**，须随那两域的审计一起定级 |

---

## 六、`logger` / `response` / `snowflake` 结论

### `response` —— ✅ `SEMANTICALLY EQUIVALENT`（逐条等价）

`code` / `msg` / `data` 三个字段的数字类型、键名、**字段顺序**、无 `omitempty` 全部一致；`null` 两侧同样输出。
唯一差异是 `Response.java:3` 一个**未使用的 import**（无行为影响）。

### `logger` / `snowflake` —— 🔴 `SEMANTICALLY NOT EQUIVALENT`

| # | 项 | 类型 | Go 出处 |
|---|---|---|---|
| DRIFT-LOG-001 | KV 字段生产侧全丢 | MISMATCH | `logger.go:148-157,168` — **见 §三，已单独展开** |
| DRIFT-LOG-002 | `call_chain` 比 Go **少一帧**：Go 尾部恒有 `logger.LogXxx` 包装帧（`runtime.Callers` skip 语义），RAG 跳过 | MISMATCH | `logger.go:139` + `:320-323`（⚠️ Go 侧 `logger.go:139` 自己的注释描述的是 skip=4 的语义，**Go 注释与实现不符**） |
| DRIFT-LOG-003 | 生产代码**从不调 `Logger.init`** ⇒ 三个静态方法必抛 `IllegalStateException`；且 `serviceName` 来源与 Go 的 `c.Name` 是不同取值体系 | MISMATCH | `logger.go:63-70`（Go 侧 15 处 main 都调 `logger.Init`） |
| DRIFT-LOG-004 | `Init` 失败后可恢复性相反：Go `sync.Once` 中毒（失败即永久失效），RAG 可重试成功 | MISMATCH | `logger.go:63-70` vs `Logger.java:171-181` |
| DRIFT-LOG-005 | Go 自动附加 `caller` / `trace` / `span`，RAG 全无（MDC 也不在默认 pattern 里） | OBSERVABLE | go-zero `richlogger.go:210,217-225` |
| DRIFT-LOG-006 | Go `Mode: file` 落盘 + 滚动；RAG 只写 stdout | OBSERVABLE | 各服务 yaml |
| DRIFT-LOG-007 | `error_reason` 空 message 兜底：Go 恒取 `Error()`（得 `""`），RAG 退 `toString()`（得类全名） | OBSERVABLE | `logger.go:118-125` vs `Logger.java:243` |
| DRIFT-SF-001 | **时钟源不同**：Go `time.Since(n.epoch)` 走**单调时钟**（回拨不可达）；RAG `System.currentTimeMillis()` 走**墙钟**（NTP / `setSystemTime` / 虚拟机快照恢复都会倒退）⇒ 位布局与回绕逻辑逐条一致，但「无回拨保护」在两侧的**实际概率不在一个量级**，RAG 一旦回拨重合即产**重复 ID** | MISMATCH | `bwmarrin/snowflake@v0.3.0/snowflake.go:126,139` vs `Snowflake.java:242` |
| DRIFT-SF-002 | workerId 多出 Go 没有的 `snowflake.worker-id` 配置档（且已确认该档是**活的**：`article-api` 扫 `com.rag.common`） | OBSERVABLE | `snowflake.go:53-59,62-84` vs `SnowflakeBean.java:59-78` |
| DRIFT-SF-003 | 初始化时机不同（Go 懒加载于首次 `GetID`；RAG 容器 refresh 期）+ `SNOWFLAKE_NODE_ID` **空白容错相反**（Go `Atoi` 不 trim，RAG `trim()`） | OBSERVABLE | `snowflake.go:22-42,56` vs `Snowflake.java:128-142,285` |

**签字偏离现状复核**（台账要求）：

- **A-1（`logFatal` 抛 `FatalError` 而非 `os.Exit(1)`）—— ✅ 仍成立**。关闭条件（全仓出现「catch 后继续运行」）**未触发**：
  2026-10-24 复验：全仓 `catch (FatalError)` 已从 7 处增至 **15 处 / 8 个域**（article / comment / favorite / follow / like / message 各 1 + users 2），
  **逐处看过，全部是 `throw fatal;` 或 `throw e;`**，无一处吞掉后继续。
  ⚠️ 但 `FatalErrorNotSwallowedTest` **至今不存在**（只在 spec 被提及）⇒ **关闭条件目前靠 15 处人工 rethrow 维持，无守门测试**。
  ⇒ 15 处分散在 8 个域，任何一处写错都不会被任何测试抓到 —— **建议补一个「全仓扫描 `catch (FatalError)` 必须 rethrow」的静态守门**（形态同本轮新增的 `check-grpc-interceptor-wiring.py`）。
- **A-2（`call_chain` 过滤）—— ✅ 仍成立**，且可更精确：Go 的 `"github.com/zeromicro/go-zero/"` 前缀**本来就匹配不上**
  （`isFiltered` 消费的是 `getFuncName` 产出的**短名**；Go 自己的 `logger_test.go:189` 绕过 `getFuncName` 直接喂全路径，是同义反复）。
  ⚠️ **DRIFT-LOG-002 与 A-2 必须一起修**：RAG 多出的第 10 条 `com.rag.common.logger.` 会滤掉 logger 自身帧，
  而 Go 保留 `logger.LogXxx` 尾帧 —— 改一处不改另一处会产生新的不一致。

---

## 六·五、台账时效性（2026-10-24 复验）—— 这份台账现在还成立吗

**结论：主结论仍成立，但本文件里有 1 处假结论已更正、若干计数已陈旧、且 `service/common` 正被别的域并发改动。**

### 1. 基线

| 项 | 值 |
|---|---|
| 本文件基线 | `8422116` |
| `do-my-own-gh` 当前 HEAD | **仍是 `8422116`**（未前进）⇒ **基线无漂移**，本文件的证据链仍然对得上 |
| `align/common` 当前 HEAD | `01db672`（本文件所在） |

### 2. 承重断言逐条复验（2026-10-24 真实执行）

| 断言 | 复验结果 |
|---|---|
| `service/common` 147 tests / 0 failures | ✅ **仍成立**（重跑实测） |
| 3 处修复仍在（`wrappedCallRef` / `isAuthorityUnavailable(ex)` / `receivedAt == null`） | ✅ **仍成立**（grep 命中 3 / 1 / 1） |
| A-1 关闭条件未触发 | ✅ **仍成立**（15 处逐个验过全是 rethrow；计数由 7 改为 15） |
| Go 侧范围内 10 个 rpc 服务全部注册拦截器 | ✅ **仍成立**（全量 grep 得 12 个，其中 `points` / `security` 范围外） |
| **「10 个 rpc 模块全部有 `GrpcServerConfigurer`」** | 🔴 **假结论，已更正为 9/10**（`hot-rpc` 缺） |
| communityfact 消费者不一致 | ✅ **仍成立**（`like` 仍为 0；`comment` 由 4 增至 5） |

### 3. `service/common` 正被别的域并发改动（合并前必须知道）

| 分支 | `service/common` 规模 | 动了什么 |
|---|---|---|
| `8422116`（基线） | 44 文件 / 7,529 行 | — |
| **`align/common`（本分支）** | 44 文件 / **7,587 行** | 本轮 3 处修复 + 注释 |
| **`align/message`** | 🔴 **51 文件 / 8,602 行**（**+7 类**） | 新增 `GoStyleCpuReader` / `GoStyleSheddingStat` / `GoStyleRpcSheddingInterceptor` / `GoStyleRpcBreakerInterceptor` / `GoStyleRpcFailurePolicy` / `GoStyleRpcProtectionChain` / `FakeServerCall` / `GoStyleRecoverInterceptor`；改 `HttpMiddleware` / `GoStyleAdaptiveShedder` / `GoStyleBreaker` |
| **`align/users-user`** | 44 文件 / 7,594 行 | 改 `GoStyleAdaptiveShedder` / `GoStyleCpuSheddingFilter`（+20 行） |

⚠️ **两个直接后果**：

1. **本文件引用的行号会失效** —— 例：本文件 §三引用的
   `GoStyleCpuSheddingFilter.java:92-96`（`shedder.allow()` → 503）在 `align/users-user` 上已移位到 **112-116**。
   ⇒ 本文件所有行号引用**均以基线 `8422116` 为准**，合并后需重新定位。
2. **`protection/` 包的对照基准在移动** —— 详见 §七.1。

### 4. 合并冲突评估（§5.3）

| 冲突面 | 结论 |
|---|---|
| 本轮改的 5 个源文件 | ✅ **无冲突** —— 全分支扫描确认除 `01db672` 外无人改过 |
| `docs/Pitfalls/common/protection.md` | ✅ **无冲突** —— 原为 0 字节占位，只有本分支填了内容 |
| `docs/DRIFT/common/` 其余 4 份 | ✅ **无冲突** —— 只有本分支新增了 `alignment-2026-10-23.md` |
| 三档台账跨域写串 | ✅ **未发生** —— `align/message` 只写 `docs/*/message/*`，`align/users-user` 只写 `docs/*/users/*`，均未越界写 `docs/*/common/*` |
| 🔴 `align/message` ↔ `align/users-user` | ⚠️ **两者在 `service/common/protection/GoStyleAdaptiveShedder.java` / `GoStyleCpuSheddingFilter.java` / `GoStyleRpcSheddingInterceptor.java` 上都改了** ⇒ 这两支合并时大概率冲突。**不属本域，但需知会主会话。** |

### 5. ⚠️ `docs/` 下「common」的**命名歧义**（导航陷阱）

合并后 `docs/` 下会同时存在**三处**叫 common 的东西，**互不相干**：

| 路径 | 是什么 | 归谁 |
|---|---|---|
| `docs/DRIFT/common/` | **`service/common`**（跨服务共享层，16 个组件依赖） | **本域** |
| `docs/DRIFT/message/common.md` | `service/message/message-common`（该域的 errmsg 码表模块） | `align/message` |
| `docs/DRIFT/users/common.md` | `service/users/users-common` | `align/users-user` |

⇒ 看到 `common` 三个字**不能**推断是同一件事。§7.2 已就 `docs/FileTree/` 记过一次同类歧义，此处再记一次。

### 6. 🔴 别的域给**共享组件的限制**签了字，而 common 域台账对此**毫无记录**

2026-10-24 复验发现：`align/like`（commit `cff298c`）新增了
**`SD-2026-10-23-09`（⏳ 待签字）**，内容是：

> 组件：`service/like/like-api`（全部 6 个端点的下游调用）
> 性质：**框架限制**（**共享组件 `GoStyleRequestDeadline` 的公开 API 只暴露毫秒**）

⇒ 被签字的根因在 **`service/common`**（共享组件），而 common 域的三档台账里**一个字都没记**。

**本轮补记的结果**（见 DRIFT-COMMON-005）：那条签字的描述**只对了一半** ——
`GoStyleRequestDeadline` 内部存的是**纳秒**、`applyTo` 用 `withDeadlineAfter(remainingNanos, NANOSECONDS)`，
**出向链路是纳秒精度的，与 Go 等价**；
真正只有毫秒的是**另一个载体** `DeadlineState`（gRPC 服务端**入向**），
它连时基都不同（墙钟 vs 单调钟）。

⇒ **教训**：**「某个域为 X 签了字」不等于「X 的根因已被记录在它所属的域里」。**
共享组件的限制被下游域签字时，**根因域的台账必须同时落一条**，否则：
① 根因域复审时会把它当新发现重提一遍；② 下游的签字理由可能建立在**不准确的描述**上（本例就是）。

⇒ 另：`align/like` 把它记在 `docs/Intentional_Deviations/like/api.md`，
但按 §5.3b「<域> = 微服务名，<组件> = api/rpc/common/proto」，
**根因属于 `service/common` ⇒ 应在 `docs/Intentional_Deviations/common/` 也有一份**。
本轮**未动别域文件**，仅在此登记，供主会话合并时协调。

---

## 六·六、🔍 本轮查到但**首版台账漏记**的条目（2026-10-24 补录）

首版台账只收了「MISMATCH / OBSERVABLE / UNRESOLVED」三类。逐行审计过程中还有几条**够不上那三类、但确实是问题**的项，漏了。补上：

| # | 项 | 性质 | 出处 |
|---|---|---|---|
| DRIFT-CF-018 | `Dispatcher.run(once=false)` 创建了 `handle`（`Dispatcher.java:146`），但只在**循环退出后**才返回（`:211`），`once` 分支在 `:173` 直接 return ⇒ **`@PreDestroy` 拿不到 handle**，`Dispatcher.java:223-227` 的 javadoc 描述的用法**不可达** | **API 缺陷**（非 Go 语义差异） | `Dispatcher.java:146,173,211,223-227` |
| — | Go 侧 `LogConf.MaxContentLength` 默认 **0** 且各服务 yaml 未设 ⇒ **64KB 截断在本项目配置下不生效**；在 `core/logx/fields.go` / `rotatelogger.go` 里都未找到 1<<16 缓冲或「多读一行」逻辑 | **不是漂移**（RAG 实现了但 Go 没启用 ⇒ 行为等价） | go-zero `core/logx/config.go` 的 `LogConf` |
| — | `LoggerFactoryBean` 在各模块的注册方式**不一致**（有的显式列 `@Import(LoggerFactoryBean.class)`，有的只靠 `scanBasePackages` 含 `com.rag.common`）⇒ 是否有模块漏注册 ⇒ 该模块打日志路径整体不可用 | **跨模块，未验** | `LoggerFactoryBean.java:76` + 各 `*Application` |
| — | `Logger` 静态入口的 `serviceName` 来自 `spring.application.name`，Go 来自 yaml 的 `Name`（如 `usercenter`）⇒ **`service` 字段值两侧必然不同** | 归入 DRIFT-LOG-003，但**逐模块取值对照表未拉**（20 个模块） | `logger.go:63` + 各服务 yaml `Name:` |
| — | `Logger` 的三个 `doLogXxx` 是 `public`，Go 的 `logBusinessErr` 是**包私有** | 表述差异（暴露面变大，无行为差） | `Logger.java:221,297,374` vs `logger.go:98` |
| — | Go 侧 `points` / `security` 也注册 `AddUnaryInterceptors`（`service/points/rpc/points.go`、`service/security/rpc/security.go:43`） | **范围外**，但影响「Go 侧注册点共 12 个」这个计数的口径 | 同左 |

---

## 七、本轮**没有**覆盖的范围（诚实边界）

1. **`protection/`（13 文件）与 `http/`（2 文件）本轮未做 Go 对照** ——
   这两个包在 Go 侧**没有对应文件**，它们是 go-zero `rest.Server` 内建中间件（breaker / shedder / maxConns / maxBytes）
   与 `httpx` 路由错误字节形状的 Spring 表达层。
   ⇒ 对齐基准应是 **go-zero v1.10.2 库源码**，不是本仓 `Sea-RideTheWind_golang`。属独立一轮工作。
   ⚠️ **2026-10-24 追加的紧迫性**：这两个包正在被别的域大改 ——
   `align/message` 往里加了 **7 个新类**（RPC 侧 breaker / shedding / failure policy / protection chain / recover），
   `align/users-user` 改了 `GoStyleAdaptiveShedder` / `GoStyleCpuSheddingFilter`。
   ⇒ **对照基准在移动**。真要做这一轮，**必须先钉死一个基准 commit**，否则结论会随别人的提交漂移。
   ⇒ 更要紧的是：新加的 7 个类**从未被任何台账对照过**（它们是 2026-10-23 晚间之后新增的）。
2. **`persistence/`（2 文件）未审** —— B-1 的缓解手段（`setQueryTimeout` 注入 + `57014` 翻译），已签字关闭，但未做变异验证。
3. **`observability` 子审计的完整覆盖清单本轮未收齐** —— 本轮在该包上**独立发现并修掉**了 DRIFT-COMMON-001，
   其余条目（`HttpMiddleware` / `MqConsumer` / `GoStyleRequestDeadline` 的逐函数对照）待补。
   ⚠️ **2026-10-24 追加**：`align/message` 已改 `HttpMiddleware.java`（+10 行）⇒ 该文件的对照基准同样在移动。
   ⚠️ **2026-10-24 再追加**：**`GoStyleRequestDeadline` 与 `DeadlineState` 已审完**（结论见 DRIFT-COMMON-005）：
   前者（出向）**纳秒精度、与 Go 等价**；后者（入向）**墙钟 + 毫秒**。
   ⇒ 本项剩余未审的是 `HttpMiddleware`（758 行）与 `MqConsumer`（145 行）。
4. **全程无 Go ↔ RAG 双侧活体对拍** —— 本轮最强证据是「真 OTel SDK + 真 span exporter 驱动真实拦截器」+
   `javap` 字节码 + 全仓配置扫描。**仍未达到 §3.3 理想态**（真 PG / 真 Redis / 双栈对拍）。
5. **`service/common` 自身无启动入口** —— 它是库模块，20 个测试类中仅 `ObservabilityWiringTest` 装配 Spring 上下文，
   且那 4 条用例**全是断言 Bean 存在**。⚠️ 它自己宣称「验证 H1/H2 的机制确实生效」，
   而 `grpcInterceptorBeanExists` 恰恰是 H2（starter 不用该 Bean）**按定义抓不到**的形状 —— **为执行 §3.3 而写的测试本身违反了 §3.3**。
   ⇒ 本轮用 `RpcInterceptorTimeoutSpanTest` 补上了「断言运行时后果」这一半。
   ⇒ **2026-10-24 新增反面证据**：DRIFT-COMMON-004 正是「H2 形态在 `hot-rpc` 上复发」——
   而 `hot-rpc` 当时**没有**接线测试。⇒ 「有测试」也不等于「测了这一条」。
6. **`DRIFT-LOG-003` 的 `serviceName` 取值对照表未逐模块拉**（Go `c.Name` ↔ RAG `spring.application.name`，20 个模块）。
7. **Boot 4 是否改变了 `%kvp` 默认渲染** —— §三 已从 4.0.8 的 `defaults.xml` 直接取证为「不含」，
   但**未在真实启动的应用里抓一次 stdout** 闭环（§3.3 要求运行证据，本条只有配置层 + jar 内证据）。
8. **本文件未复审 `align/message` 新增的 7 个 `service/common/protection/` 类** ——
   它们从未被任何 Go 对照覆盖，属本次台账的**已知空白**，且不在本轮开工时的基线内。

---

## 八、📌 决策记录（2026-10-24 用户拍板）

> 本节记**用户明确拍过板的事**。目的是：**下一轮复验不要把它当新议题重新提一遍**。

### 决策 1：`tools/difftest/check-grpc-interceptor-wiring.py` **保留**

| 项 | 值 |
|---|---|
| 拍板 | ✅ **保留**（2026-10-24，用户） |
| 产物 | `tools/difftest/check-grpc-interceptor-wiring.py`（可执行，`EXIT=1` 精确指向 `hot-rpc`） |
| ⚠️ 归属说明 | 它**落在 `service/common` 之外**（在 `tools/`）。<br>⇒ 依据：**它不改任何别的域的代码**，只**报告**哪个消费方没接上 —— 是**检测器**，不是**越界修复**。<br>⇒ 预置在 `tools/difftest/` 也与既有惯例一致（`check-flyway-ledger.py` 同样是跨模块静态检查）。 |
| 生效条件 | 待 `hot-rpc` 修好后应转 `EXIT=0` ⇒ **该脚本同时是「修好了」的验收信号** |
| 📌 **交接给主会话** | ⚠️ 该脚本**尚未进入 AGENTS.md §7.2 的工具表** ⇒ 主会话合并时需补一行，否则它**不可被发现**（§7.2 表格是工具的唯一索引） |

### 决策 2：DRIFT-COMMON-005 **先记入台账，本轮不改**

| 项 | 值 |
|---|---|
| 拍板 | 📌 **先记入台账，不改**（2026-10-24，用户） |
| 归档位置 | ✅ 留在 `docs/DRIFT/common/`（**不**进 `docs/Intentional_Deviations/common/`）<br>判据（§5.3b 硬边界 2）：**这条差异要不要「修回去」？** —— 要（技术上可修），只是**改动是跨域破坏性的** ⇒ 属 DRIFT，不属「决定不对齐」 |
| 改动为什么必须跨域 | `bindToCurrent(long epochMillis)` 是 `service/common` 的**公共 API**，而 `admin-rpc` / `user-rpc` 的**测试**直接调它（`AdminDeadlinePassthroughTest.java:150`、`DeadlinePassthroughTest.java:144`）⇒ 改签名会打断别域 |
| 关闭条件 | 需要一个**同时动 `service/common` + `admin-rpc` + `user-rpc`** 的决策（谁主导？API 兼容还是允许破坏？）⇒ **不属于单域可决** |
| ⚠️ 遗留 | `align/like` 的 `SD-2026-10-23-09` 已就**同一现象**签字，但**描述只对了一半**（见 §六·五·6）⇒ 合并时两侧描述需对齐 |

---

## 九、📮 交接给主会话（合并时处理，本域不越界）

| # | 事项 | 归属 | 说明 |
|---|---|---|---|
| 1 | **`hot-rpc` 缺 `GrpcServerConfigurer`** | `align/hot` | DRIFT-COMMON-004。`align/hot` 当前 3 个 commit 都在 `hot-api` 侧，**未触及 hot-rpc 的 Application**。修法 + 必备测试见 §一·五 |
| 2 | **`UserDomainService.java:115` 的 javadoc 假断言** | `align/users-user` | DRIFT-COMMON-006 连带项：声称 deadline 由「`markExpired`（RpcInterceptor 调度）」设置，实际是 `onHalfClose` 的 `bindToCurrent` |
| 3 | **DRIFT-COMMON-005 的定案** | 跨域（common + users） | 决策 2 关闭条件：`bindToCurrent` 签名是否允许破坏性变更 |
| 4 | **`align/like` 的 `SD-2026-10-23-09` 描述订正** | `align/like` | 签字理由说 `GoStyleRequestDeadline` 只暴露毫秒，实际它内部是**纳秒**；真正只有毫秒的是 `DeadlineState` |
| 5 | **守门脚本进 AGENTS.md §7.2 工具表** | 主会话 | 决策 1 的配套；不进表 = 不可被发现 |
| 6 | **`align/message` ↔ `align/users-user` 合并冲突** | 主会话 | 两者都改了 `service/common/protection/GoStyleAdaptiveShedder.java` / `GoStyleCpuSheddingFilter.java` / `GoStyleRpcSheddingInterceptor.java` |
| 7 | **共享面索引更新** | 主会话 | `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`（§5.3：本域一律不碰） |
| 8 | **`docs/Pitfalls/common/_signed-deviations.md` 疑似错放** | 主会话 | 0 字节占位，却顶着 Intentional_Deviations 的名字（同名文件在 `docs/Intentional_Deviations/common/`）。本域未动 |
| 9 | **社区消费者：Go 用 `like`，RAG 不用** | `align/like` / `align/favorite` | §四。`like` 域在 Go 侧有整套事实链路装配，RAG 侧零引用；`favorite` 反之。**别的域在往这条链上加消费者，却没人补 `like` 那一半** |

---

## 十、效力边界

- 本文件所有断言的**基线是 commit `8422116`**。若 `do-my-own-gh` 已前进，须 `git merge do-my-own-gh` 后重新核对（§5.3.4）。
- 「已修」= **有回归测试且测试红→绿可复现**；**不等于**已在真实部署环境验证。
- 「未修」条目均为**读 Go 原文得出**，未经运行时对拍 ⇒ 一律读作「**在该证据强度下不一致**」。
- 本文件**不改** `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`（§5.3 共享面，合并后由主会话统一更新）。
---

## 十一、📮 交接事项的结案状态（2026-10-25 更新）

> 本节只更新**状态**，不改写上文 §九 的原始记录（那是 2026-10-23 的原始交接快照）。

| # | 事项 | 原归属 | **2026-10-25 状态** |
|---|---|---|---|
| 1 | **`hot-rpc` 缺 `GrpcServerConfigurer`** | `align/hot` | ✅ **已关闭** —— 由 hot 域正式认领（该域三份台账此前**零提及**这条缺口）并修复。<br>修法：补 `@Bean GrpcServerConfigurer` → `server.intercept(GoStyleRpcProtectionChain.build(...))` + `@Import(GoStyleProtectionConfiguration.class)`。<br>守门：新增 `HotGrpcInterceptorWiringTest`（3 条，**行为级**）+ **两档变异**（MUT-1 摘 Bean / **MUT-2 摘 `server.intercept` 而保留 Bean** ⇒ 2 条行为断言转红）。<br>`tools/difftest/check-grpc-interceptor-wiring.py` ⇒ **`EXIT=0`**（修前 `EXIT=1`）。<br>逐条证据 ⇒ [`docs/DRIFT/hot/rpc.md`](../hot/rpc.md) 第十二轮 |
| 3 | **`DRIFT-COMMON-005` 的定案**（`bindToCurrent` 签名是否允许破坏性变更） | 跨域（common + users） | ⏸ **仍未决** —— 本轮**未动**（属「同时动 common + admin-rpc + user-rpc」的破坏性变更，**不是单域可决**） |
| 4 | **`align/like` 的 `SD-2026-10-23-09` 描述订正** | `align/like` | ⏸ **仍未决** —— like 域本轮未动 |
| 9 | **社区消费者：Go 用 `like`，RAG 不用** | `align/like` / `align/favorite` | ⏸ **仍未决** —— 与 `DRIFT-LIKE-003`（事实投递链路，末端不在任何仓）是同一条，**favorite 域本轮未做** |

✅ **已关闭**：#1（hot-rpc 接线）。

⚠️ **本轮连带产出的跨域登记（供后续会话接手）**：

| # | 事项 | 归属 | 说明 |
|---|---|---|---|
| **10** | **裸 `ServerCallHandler` 注册到 unary 方法上，grpc-java 根本不会 dispatch** ⇒ 「超时生效」类断言在这种写法下**依然是绿的** | **`align/task`** | `service/task/task-rpc/.../TaskGrpcInterceptorWiringTest.java` 的 `slowTaskService()` 用的正是裸 `ServerCallHandler` ⇒ 它的「睡 10 秒的服务」**实际从未睡过**。<br>⇒ 该用例的断言**依然有效地证明了「服务端超时生效」**，但**证明不了「handler 被调用过」/「deadline 传播到了 handler 线程」**。<br>⇒ 建议该域换成 `ServerCalls.asyncUnaryCall` 并补一条「handler 被调用过」的断言。<br>实测证据 ⇒ [`docs/DRIFT/hot/rpc.md`](../hot/rpc.md) 第十二轮 §五 |
| **11** | **6 个 api 模块的 jar 不可执行** | **跨 6 域（已修）** | `DRIFT-MSG-V-030`，登记方在 message 域 ⇒ **2026-10-25 已统一修完**，守门脚本 `EXIT=0` |

---

## 十二、📌 2026-10-25 台账校准轮：common 作为「共享写点」的真实状态

> 本节由 2026-10-25 的一次**全档机械扫描 + 定向实测**产出，**不改写上文任何历史记录**（§〇~§十一 原文保留）。
> **本节的目的**：把「common 域是瓶颈、其他域的尾巴全挂在它身上」这个判断**落到可执行的粒度** —— 每条尾巴的**落点 `文件:行号`**、**当前真实状态**、**闭合需要几步**。
> ⚠️ **本节不实施任何修复**（用户 2026-10-25 明确「我自己修」）⇒ 本节只提供**事实与顺序**。
>
> 🟥 **本节初稿的日期被守门脚本当场抓住并已订正** —— 初稿把本轮的核对日期写成了**次日**（一个**未来日期**；系统当日为 **2026-10-25**），
> 共 10 处（本文件 9 处 + `docs/DRIFT/_INDEX.md` 1 处）⇒ `python3 tools/check-agents-budget.py` 的「未来日期检查」返回 **`EXIT=1`** 并逐行列出 ⇒
> **已全部订正为 2026-10-25**。
> ⇒ 这正是 §6.2「核对日期必须由一次真实执行产出」这条纪律**有机器检查**的样子 —— **写完台账必须重跑 `check-agents-budget.py`**，它同时守体量与日期。
> ⚠️ **连带一条工具纪律**：连「复盘一个未来日期错误」这句话本身都不能写出那个未来日期的字面量（守门脚本是机械匹配）⇒ **反例要用「次日 / 未来日期」这类相对表述**。

### 十二·一、层一：common 域自身的漂移真实条数 = **27 条编号 + 5 项未编号**

**对 §〇 第 40 行的订正**：

| §〇 原写 | 实测 | 依据 |
|---|---|---|
| **28** 条编号条目 | **27** 条 | `DRIFT-COMMON-004`（`hot-rpc` 缺 `GrpcServerConfigurer`）已由 **hot 域 2026-10-25 认领并关闭**（§十一 #1）⇒ `DRIFT-COMMON` 未修数 **3 → 2** |
| communityfact **15** 条 | ✅ **15 是对的** | §五 的表格只列 **14** 条（5 MISMATCH + 8 OBS + 1 UNRESOLVED）⇒ **漏了 `DRIFT-CF-018`**（它在 §六·六「补录」里）⇒ **§五 的「14 条」是旧快照**，§〇 的 15 才是全量 |

**逐族明细（2026-10-25 扫描）**：

| 编号族 | 未修 | 编号清单 | 状态 |
|---|---|---|---|
| `DRIFT-COMMON` | **2** | `-005`（`DeadlineState` 墙钟 vs Go 单调钟）/ `-006`（`markExpired` 死代码 + 跨域 javadoc 假断言） | 🔴 **无任何域认领**（§八 决策 2 明确「先记账不改」） |
| `DRIFT-CF`（communityfact） | **15** | `-001 -002 -003 -004 -005 -006 -008 -009 -011 -012 -013 -014 -015 -017 -018`（**无 -010 / -016**） | 🔴 **一条未动** |
| `DRIFT-LOG`（logger） | **7** | `-001` ~ `-007` | 🔴 未修 |
| `DRIFT-SF`（snowflake） | **3** | `-001` ~ `-003` | 🔴 未修 |
| **合计** | **27** | | + **5 项未编号**（§六·六） |

⚠️ **5 个可比包 3 个 `NOT EQUIVALENT`** 依然成立（`communityfact` / `logger` / `observability` / `snowflake` 中只有 `response` 逐条等价）⇒ **§〇 第 36 行未过期**。

### 十二·二、层二：common 是**唯一的共享写点** —— 外部域的尾巴清单（10 条）

> **这是比层一更硬的那层**：这些条目**不属于 common 域的漂移**，但**修法落在 `service/common` 的共享类里** ⇒ 改一处影响 6–10 个域 ⇒ 按 §5.3 必须**单域串行** ⇒ 并发对齐在这一点上**必然排队**。

| # | 域 | 条目 | 落点（`文件:行`） | 差异 | 当前真实状态（2026-10-25 实测） |
|---|---|---|---|---|---|
| **T-1** | `like` | `DRIFT-LIKEAPI-015` | `common/http/GoStyleJsonBodyConverter.java:115-117`（`getContentType().toString()` ⇒ MediaType 已小写化）vs `goReadsBody` `:98-101`（`contains` 大小写敏感） | Go 用**原始头**（`strings.Contains` 敏感），RAG 用**已解析 MediaType** | 🔴 **仍未修**（`like/api.md:456`）。⚠️ **6 域共用**（like / follow / message / users×2 / task / article）⇒ **单点解多域** |
| **T-2** | `follow` | `DRIFT-FOLLOW-008` | `common/protection/GoStyleMaxConnsFilter.java` | Go `MaxConnsHandler` **每路由一个** latch，RAG **全局一个** | 🔴 **仍未修**（`follow/api.md:20`「`service/common` 共享模块 ⇒ 派单」） |
| **T-3** | `hot` | `D14` / `DRIFT-HOTAPI-014` | `common/observability/HttpMiddleware.java:431`（`writeTimeoutResponse`） | 503 / 499 响应缺 `X-Content-Type-Options: nosniff` | ✅ **已修，但 hot 域台账未销账** —— 代码 2026-10-24 commit `8d019e0` 已 `setHeader(..., "nosniff")`；真 Tomcat 守门 `MessageApiTimeoutRealTomcatTest:140` 断言 `x-content-type-options == "nosniff"` ⇒ **实际已闭合** |
| **T-4** | `hot` | `D25` / `DRIFT-HOTAPI-025` | `common/observability/HttpMiddleware.java:332`（`writeTimeoutResponse(resp, 499)`） | 客户端断连后 RAG **等 2s deadline**、**断连后继续发下游调用**、**无 499 记录**；Go 侧 1–3ms 内取消 8 个在飞 gRPC 并打 `[HTTP] 499` | 🔴 **仍开放，且全仓无 499 守门测试**（`grep -rln "499" service/*/*/src/test/java/` ⇒ **0 命中**）⇒ 修了也**没有防回退网** |
| **T-5** | `hot` | `D27` | `common/protection/GoStyleMaxConnsFilter.java` | 同 T-2（MaxConns 作用域） | 🔴 仍开放 |
| **T-6** | `message` | `-010` | `common/observability/HttpMiddleware.java:421`（`setContentType("text/plain; charset=utf-8")`）+ `common/http/GoStyleRouterErrorResponses.java:83` | 成功 / 错误响应 `Content-Type` 缺 `charset` | 🟡 **仍 OPEN**（用户 2026-10-24 拍板「保持登记，本轮不做」）⇒ **不是签字**，是登记在册的待办 |
| **T-7** | `message` | `-011` | 同上路径 | 状态行 reason phrase 差异 | 🟡 仍 OPEN（同上拍板） |
| **T-8** | `message` | `-013` | 同上路径 | 缺 `Traceparent` | 🟡 仍 OPEN（同上拍板） |
| **T-9** | `comment` | `D3` | `common/protection/GoStyleGrpcClientBreakerInterceptor.java:193`（`method.getFullMethodName()`，**无 target 前缀**） | Go `path.Join(cc.Target(), method)` ⇒ 单 channel 下不可观察，将来多 target 会把计数合并 | 🔴 仍开放（`comment/api.md:126`「改动影响多域 ⇒ 只登记不改」） |
| **T-10** | `users` | 交接 **#2** | `service/users/user/user-rpc/.../UserDomainService.java:113`（javadoc 仍写「由 `RpcInterceptor` 调度」） | 声称 deadline 由 `DeadlineState#markExpired(long)` 设置，实际是 `onHalfClose` 的 `bindToCurrent` | 🔴 **仍未修**（见 十二·四 的**循环依赖**） |

⚠️ **不是 common 尾巴的一条（订正）**：`comment/api.md` 的 **D2**（Bearer 前缀长度边界 `> 7` vs Go `> 6`）落点是 **`service/users/users-common/.../BearerTokenExtractor.java:74-81`** ⇒ **属 `users` 域，不属 `service/common`** ⇒ 把它算作 common 的尾巴是错的（**D3** 才是）。

### 十二·三、9 项交接的真实状态（逐条实测，2026-10-25）

> ⚠️ **§十一 只记了 #1 / #3 / #4 / #9 / #10 / #11 六条** ⇒ **#2 / #5 / #6 / #7 / #8 五条的状态从未被记录**。
> ⇒ AGENTS.md §0 的「**8 项未决交接**」把「**没人看过**」当成了「**确认未决**」。

| # | 事项 | §十一 记录 | **2026-10-25 实测** | 判定 |
|---|---|---|---|---|
| 1 | `hot-rpc` 缺 `GrpcServerConfigurer` | ✅ 已关闭 | —— | ✅ **已销账** |
| 2 | `UserDomainService` javadoc 假断言 | **未记录** | `UserDomainService.java:113` **原样还在** | ❌ **未销账**（同时是 T-10） |
| 3 | `DRIFT-COMMON-005` 定案 | ⏸ 仍未决 | 跨域（common + admin-rpc + user-rpc）的破坏性变更 | ⏸ **仍未决**（唯一被反复确认的未决项） |
| 4 | `like` 的 `SD-2026-10-23-09` 描述订正 | ⏸ 仍未决 | like 域本轮未动 | ⏸ **仍未决** |
| 5 | 守门脚本进 AGENTS.md §7.2 | **未记录** | `AGENTS.md` §7.2 工具表**已有** `check-grpc-interceptor-wiring.py` | ✅ **已销账** |
| 6 | `align/message` ↔ `align/users-user` 合并冲突 | **未记录** | `service/common/.../protection/` 三个冲突文件**无冲突标记残留**，单副本存在 | ✅ **已销账** |
| 7 | 共享面索引更新 | **未记录** | `docs/DRIFT/_INDEX.md` / `AGENTS.md` / `_MANIFEST.json` **已更新** | ✅ **已销账** |
| 8 | `docs/Pitfalls/common/_signed-deviations.md` 疑似错放 | **未记录** | 该文件**仍是 0 字节**，原地未动 | ❌ **未销账**（真名在 `docs/Intentional_Deviations/common/`） |
| 9 | 社区消费者：Go 用 `like`，RAG 不用 | ⏸ 仍未决 | 与 `DRIFT-LIKE-003` 同一条，favorite 域本轮未做 | ⏸ **仍未决** |
| 10 | 裸 `ServerCallHandler` 不被 dispatch（task 域） | 已登记 | task 域本轮未动 | ⏸ **仍未决** |
| 11 | 6 个 api 模块 jar 不可执行 | ✅ 已修 | —— | ✅ **已销账** |

⇒ **真实分布**：**已销账 5 条**（#1/#5/#6/#7/#11）· **未销账 2 条**（#2/#8）· **仍未决 4 条**（#3/#4/#9/#10）。
⇒ **「8 项未决」这个数**（9 − 1）把 4 条「已销账但没记」也算成了未决 ⇒ **应改为「4 条仍未决 + 2 条未销账」**。

### 十二·四、🔴 一条可**两步闭环**的循环依赖（本次扫描的新发现）

```
service/common/.../RpcInterceptor.java:617   markExpired(long)  ← 标 @Deprecated，保留不删
        │  保留的唯一理由（RpcInterceptor.java:603-611 的 javadoc 明写）：
        │    「service/users/user/user-rpc 的 UserDomainService javadoc 仍以 markExpired(long)
        │      描述 deadline 的来源（该描述本身也不准确……）…… 删除本方法会让那些域的 javadoc 链接悬空，
        │      而跨域文件的改动不由本域决定」
        ↓
service/users/user/user-rpc/.../UserDomainService.java:113   ← 这行 javadoc 本身就是错的
        （真相：deadline 由 onHalfClose 的 bindToCurrent 设置 ⇒ RpcInterceptor.java:281 / :372）
```

⇒ **实测确认**：`grep -rn "markExpired" service/ --include='*.java'` ⇒ **除定义处（`RpcInterceptor.java:617`）外，全仓 0 个生产调用方**；引用它的只有 `RpcInterceptor` 自己的 javadoc（`:132` / `:265` / `:607`）与 `UserDomainService.java:113` 的 javadoc。
⇒ **闭合只需两步**：

1. 改 `UserDomainService.java:113` **一行 javadoc** ⇒ 指向 `bindToCurrent` ⇒ **同时消掉 T-10 / 交接 #2**；
2. 删 `RpcInterceptor.java:617` 的 `markExpired(long)`（连带 `:132` / `:265` / `:607` 三处 javadoc 引用）⇒ **消掉 `DRIFT-COMMON-006`**。

⇒ **投入产出比最高的一处切口**：2 步 ⇒ 消 2 条编号未修 + 1 条未销账交接。

⚠️ **本节只登记，不实施**（用户 2026-10-25 明确「我自己修」）。

### 十二·五、两处**跨域台账**的自相矛盾（跨域登记，本域不改）

| 位置 | 问题 | 实测 |
|---|---|---|
| `docs/DRIFT/hot/api.md:180` | 「尚未处理」节仍写「全仓约定待拍板 **D21 / D22 / D23**（Tomcat 三旋钮）」 | **这三条 2026-10-25 第十三轮已修**（`hot/api.md` 第 2063 行起；`D21`/`D22`/`D23` 三行状态列均为「✅ 已修」）⇒ 该节是第七轮时期的章节，**标题却读起来像现状** |
| `docs/DRIFT/comment/api.md:125` | `D2` 被归入「剩余 D2 / D3 两条属 `service/common` 跨域」 | `D2` 的落点是 **`service/users/users-common/.../BearerTokenExtractor.java:74-81`** ⇒ 属 `users` 域；**只有 `D3` 才在 `service/common`** |

### 十二·六、结论：**「common 是瓶颈」这个判断需要改写**

**不准确的说法**：「common 域是瓶颈，其他域的尾巴全挂在 common 域身上」。

**更准确的两层说法**（本节全部实测支撑）：

1. **语义层**：common 域自身是**全仓最大的单域漂移堆**（**27 条**编号 + **5 项**未编号；5 个可比包 **3 个 `NOT EQUIVALENT`**）⇒ 这部分**没有收敛**，且 communityfact 的 **15 条一条未动**。
2. **变更层（更硬）**：common 域是**唯一的共享写点** —— 别的域的未修项只要修法落在 `service/common`，就**自动从并发任务降级为串行任务**（§5.3 单域串行）⇒ **这才是 10 域并发对齐吞吐被卡住的那一层**，而不是「common 的漂移条目多」。

⇒ **两条可选路径（供裁决，本轮未选）**：

- **路径 A：立「共享写点」准入规则** —— 给 `service/common` 定专门归属、准入与串行手册（类似 `docs/Worktree.md`，但针对共享类），并要求**任何落进 `service/common` 的修复**先登记「影响哪些域」。
- **路径 B：先摘「单点解多域」的尾巴** —— 按「改一次同时解掉几条」排序，优先 `GoStyleJsonBodyConverter`（T-1，**6 域共用**）与 `GoStyleMaxConnsFilter`（T-2 + T-5，**2 条**）；再摘 十二·四 的**两步闭环**（`markExpired` ⇒ 2 条 + 1 条交接）。

⚠️ **本节给的是事实与顺序建议，不含任何实施**（用户 2026-10-25 明确「我自己修」）。
