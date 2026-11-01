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
| 5 个可比包（`communityfact` / `logger` / `observability` / `response` / `snowflake`） | 🔴 **3 个 NOT EQUIVALENT**，1 个（`response`）**逐条等价** ⇒ ⚠️ **原表述的算术不成立（3 + 1 ≠ 5）** —— **2026-10-29 订正**：正确的口径是「**3 个已正式判定（NOT EQUIVALENT）+ 4 个实际持开放项**」（5 个包中**只有 `response` 一条给出逐条等价**；另外 4 个包 —— `communityfact` / `logger` / `observability` / `snowflake` —— 各自**另有开放项**，见 §十三·一 的逐条定级表）⇒ 与 [`../../DRIFT/global/vs-common.md`](../../DRIFT/global/vs-common.md) §1 同名词条**逐字一致** |
| §3.3 机制接线（`spring/*`） | 🔴 1 条**可证伪的假框架断言**（已修）；**1 条 H2 形态在 `hot-rpc` 上复发（DRIFT-COMMON-004，未修，归属 `align/hot`）**；2 条常见担忧**已证伪** |
| 跨组件不变量 | 🔴 `communityfact` 消费者两侧不一致（见 §四） |
| 已修 | **3 条 SEMANTIC MISMATCH**（均配回归测试，147 tests / 0 failures）<br>+ DRIFT-COMMON-006 的**本域侧**（死代码标废弃） |
| 已记录未修 | **28 条编号条目**（`DRIFT-COMMON` 3 + communityfact 15 + logger 7 + snowflake 3）<br>另补录 **5 项未编号的观察**（见 §六·六），含 1 条 `UNRESOLVED`、1 条 API 缺陷、2 条跨域归属<br>🔴 **2026-10-29 数字订正**：本行「**28 条编号条目**」**与同文件 §十二·一 的合计（**23**）不一致** ⇒ ⇒ **真实口径 = 「23 条编号 + 5 项未编号 = 28」** —— 「28」是**总数**、不是「编号条目数」⇒ ⇒ 读本行时**必须**按 §十二·一 / §十四·三 的 **23 + 5 = 28** 口径读 |
| 2026-10-24 复验 | 🔴 **推翻了首版 1 条假结论**（「H2 已闭环」实为 9/10）；✅ 其余承重断言均仍成立（见 §六·五）<br>🔴 **新增 2 条**（DRIFT-COMMON-005 / -006），其中 -005 此前被**别的域签了字**却在本域无记录 |
| 📌 决策与交接 | **2 条用户拍板**已记入 §八（守门脚本保留 / COMMON-005 先记账不改）<br>**9 项交接事项**列在 §九（本域一律不越界） |

> 🟥 **2026-10-25 校准轮订正（上表两处数字已过期，详见 §十二）**：
> ① 「**28** 条编号条目」⇒ 实为 **27** 条 —— `DRIFT-COMMON-004` 已由 hot 域 2026-10-25 认领并关闭（§十一 #1）⇒ `DRIFT-COMMON` 未修数 3 → **2**。<br>
> ② communityfact **15 条是对的** —— **§五 的「14 条」才是旧快照**（漏了 §六·六 补录的 `DRIFT-CF-018`）。<br>
> ③ 「9 项交接」⇒ **其中 5 项（#2/#5/#6/#7/#8）的状态从未被记录** ⇒ 真实分布 = **已销账 5 / 未销账 2 / 仍未决 4**（§十二·三）⇒ AGENTS.md §0 的「8 项未决」把「没人看过」当成了「确认未决」。<br>
> ⇒ 本表**其余各行（含第 36 行「3 个 NOT EQUIVALENT」）仍然成立**。

> 🟥 **2026-10-29（R3 轮）二次订正 —— 上面的「27 条」也已过期**：
> ① **27 → 24**（G4 lane）—— 再经 **R3 轮 → 23** —— `DRIFT-COMMON-005`（墙钟→单调钟 + 毫秒→纳秒）**本轮已修**（§一·五 · 落码 `6f39bea`）
>    ＋ `DRIFT-SF-001`（墙钟回拨 ⇒ 重复 ID）**本轮已修**（§十三·二）⇒ ⇒ **27 − 2 = 25**；
>    再减 `DRIFT-COMMON-006`（死代码 `markExpired` **代码已于 2026-10-25 删除**，此前被误算成「未修」）
>    ⇒ ⇒ **24**。<br>
> ② 🔴 **「`DRIFT-COMMON-005` 是唯一硬阻塞」这句话已彻底失效** —— 该条已修 ⇒ ⇒
>    **当前 `service/common` 域内硬阻塞 = 0 条**（`DRIFT-COMMON` 族未修数 = **0**）。<br>
> ③ 🔴 **「已记录未修 = 24 条」这句话<b>不能</b>读成「阶段一未通过」** —— 这 24 条**绝大多数是 `OBSERVABLE`**
>    （不命中 `docs/PhasedGoal.md` P0 任何一条）⇒ ⇒ **逐条定级（P0~P3 × A/B/C/D）见 §十三·一**
>    ⇒ ⇒ 其中 **C 类（阶段二暂缓）按 `docs/PhaseGoal-judge.md` §1.1 ⑥ 第 4 条**不计入组件 R3 ⑥**。<br>
> ④ ✅ **本轮（R3 轮）新修 1 条**：`DRIFT-CF-018`（`Dispatcher.run(once=false)` 交不出句柄 ⇒ `@PreDestroy` 停不下来）
>    —— 落码与证据见 §六·六·1 与 `docs/DRIFT/global/vs-common.md` §4.18.1
>    ⇒ ⇒ **未修数 25 → 24**（含本条）。<br>
> ⑤ ⚠️ **仍然开放且<b>不</b>属本层写权面的**：**外部域尾巴 10 条**（§十二·二的 T-1 ~ T-10）
>    ⇒ ⇒ 那些的修法落在**别的域**或需**收口裁定** ⇒ ⇒ **不计入本组件的 R3 ⑥**
>    （`docs/PhaseGoal-judge.md` §1.1 ⑥ 第 2 条：落点在共享层 / 别的域的跨组件残留 ⇒ 不计入组件 R）。

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

> ⚠️ **2026-10-30 真实性复核（global+common lane）**：原判 **🔴 该缺口「仍然开着」** ⇒ **现行态 = ✅ 已闭环（2026-10-25 由 hot 域认领并修复）**。
> 依据（RAG 侧**消费点**，不是声明点 —— AGENTS.md §3.3.1 ③）：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/HotRpcApplication.java:104-109`（`@Bean public GrpcServerConfigurer rpcInterceptorConfigurer(...)` ⇒ `return server -> server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps));` ⇒ **拦截器真挂到了 hot-rpc 的 server 上**，不再是「只产出 Bean」的 H2 形态）＋ 守门 `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java:150-182`（真 server ＋ **运行时后果**断言「`server.intercept` 没挂上 ⇒ 超时 / deadline 传播 / gRPC span 已静默消失」）· Go 侧 `service/hot/rpc/hot.go:42`（`s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))`）⇒ **两侧一致** · 核对日期 **2026-10-30**。
> ⚠️ **上方表格的「RAG 落点」行与「⚠️ 与 `align/hot` 的现状对照」段是 2026-10-23 首版快照（原值保留、已过期）** —— 首版写的「全文没有 `GrpcServerConfigurer`，也**没有** `server.intercept(...)`」**对现码不成立**。
> ⚠️ 另注：本文件末尾 2026-10-29「已闭环结论的可复算证据补齐」那张表的 `DRIFT-COMMON-004` 行，原引 `HotRpcApplication.java:7` / `:9` / `:30`（**import ＋ javadoc**）⇒ 那恰恰是 **H2 形态**的证据形态（**只看注解不看消费点**）⇒ 本轮已在该行追加订正，改引真正的消费点。

---

### ✅ DRIFT-COMMON-005 —— `DeadlineState` 用**墙钟**，Go 用**单调钟**；且精度被截到毫秒（`SEMANTIC MISMATCH`）—— **2026-10-29 已修**

> **2026-10-24 二次复验时补记。** 这是 `service/common` 自己的东西，但**此前没被任何域记录** ——
> 连已被别的域签字的同一限制，common 域的台账里也是空白（见 §六·五·6）。
>
> 🟢 **2026-10-29 结案**（`align/g4-service-common` · 共享层波次）—— **修法不动公共 API 签名** ⇒
> ⇒ 当初「跨域破坏性变更 ⇒ 不得单域决定」的**阻塞条件已不成立**（见 §八 决策 2 的解除记录）。

| | |
|---|---|
| **Go 出处** | 标准库 `context.WithDeadline`（`$GOROOT/src/context/context.go:625` · `:632`）把 deadline 存成 `time.Time`（字段 `context.go:666`）⇒ **纳秒精度**；且 `time.Time` **自带单调时钟读数**（`$GOROOT/src/time/time.go:152` 的 `ext int64` + `:164` 的 `hasMonotonic` 标志位）；`time.Until(t)`（`time.go:1233`）在有单调读数时走 `subMono(t.ext, runtimeNano()-startNano)` ⇒ **纯单调** ⇒ NTP 校正 / `setSystemTime` / 虚拟机快照恢复**都影响不到它**。Go 侧业务层就是用 `ctx.Deadline()` / `time.Until()` 读预算（实例：`service/knowledge/api/internal/logic/product/search_tools_client.go:184`） |
| **RAG 落点（修前）** | `RpcInterceptor.java` 的 `DeadlineState.DEADLINE = ThreadLocal<Long>`，存 **epoch 毫秒**；`deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()`；`isExpired()` 与 `remainingMillis()` 都用 `System.currentTimeMillis()` |
| **差异 ①（时基）** | Go **单调钟** vs RAG **墙钟**。`System.currentTimeMillis()` 受 NTP 校正 / `setSystemTime` / 虚拟机快照恢复影响 ⇒ 可能**倒退**（则 `isExpired()` 长时间为 false ⇒ **超时不触发**）或**前跳**（则提前判过期） |
| **差异 ②（精度）** | Go **纳秒** vs RAG **毫秒** —— 且是在**创建时**就截断（`timeout.toMillis()`），不是读取时 ⇒ 有效 deadline 时刻本身最多差 < 1ms |
| **🔴 同组件内不一致** | 同一组件的**另一个** deadline 载体 `GoStyleRequestDeadline`（`observability/GoStyleRequestDeadline.java:64`）用的是 `System.nanoTime()`（**单调钟**）+ 纳秒存储 + `withDeadlineAfter(remainingNanos, NANOSECONDS)`<br>⇒ **两个载体时基不同**：**出向（HTTP→gRPC）单调钟、入向（gRPC 服务端）墙钟** |
| **可观察面** | gRPC 服务端超时判定时刻、SQL `queryTimeout` 注入值、业务层 `checkDeadline()` 判定 |
| **✅ 处置（2026-10-29 已落码）** | **不动 `bindToCurrent(long epochMillis)` 的签名与语义** ⇒ ⇒ **跨域零破坏**（`admin-rpc` / `user-rpc` / `favorite-rpc` / `comment-rpc` 的既有调用方**一行不改**）：<br>① `DeadlineState` 的存储从「墙钟 epoch 毫秒」改为「**单调钟纳秒**」（`DEADLINE_NANOS`）⇒ 绑定之后**不受任何墙钟跳变影响**；<br>② `isExpired()` 改用 `monotonicNanos - d >= 0`（差值法 ⇒ 单调钟回绕也正确）；③ `remainingMillis()` 改用**纳秒差下取整**（对齐 Go 的 `time.Until` 转毫秒即截断）；<br>④ **新增** `bindRemainingNanos(long)`（纳秒精度入口，**生产路径全部改用它**）⇒ `RpcInterceptor` 的服务端预算由 `timeout.toMillis()` 改为 `toNanos()`（饱和换算）、客户端 deadline 由 `timeRemaining(MILLISECONDS)` 改为 `timeRemaining(NANOSECONDS)` ⇒ **客户端 deadline 的亚毫秒部分不再被丢弃**；<br>⑤ 超时调度器与 `Future.get` 的等待也改用纳秒 ⇒ **亚毫秒 timeout 不再被截成 0ms**（旧实现 `get(0)` ⇒ 请求被秒判超时） |
| **✅ 回归网** | **新增** `service/common/src/test/java/com/rag/common/observability/DeadlineStateMonotonicClockGateTest.java`（**7 条**）⇒ 含 ① **存储域**断言（反射读真实字段 ⇒ 旧实现存 epoch 毫秒 ⇒ 必红）② **墙钟前跳 1 小时** ⇒ 剩余预算不变、未过期（旧实现会判过期 ⇒ 必红）③ **墙钟后跳 1 小时** ⇒ 剩余预算不变（旧实现会算出 ≈3 602 000ms ⇒ **超时永不触发** ⇒ 必红）④ **兼容入口语义不变**（未来/过去/作用域清理三态）⑤ **纳秒预算 + 饱和不溢出** ⑥ **守恒**：生产代码不得再出现「拿墙钟和 deadline 比」的写法 ⑥-b **注入点必须包级私有** |
| **✅ 变异验证** | **M1 = 把实现改回旧形态**（`bindToCurrent` 存墙钟 epoch 毫秒 + `isExpired`/`remainingMillis` 改回墙钟比较）⇒ ⇒ **7 条里精确 6 条红**（唯一绿的 ⑥-b 守的是注入点可见性、与实现无关 ⇒ 预期绿）⇒ ⇒ **守门不是假绿** |
| **跨域影响面（已核）** | `DeadlineState` 的生产消费点**只有 4 处**：`service/common/.../persistence/DeadlineQueryTimeoutInjector.java:165`（共享层）· `service/users/user/user-rpc/.../UserDomainService.java:154` · `service/users/admin/admin-rpc/.../AdminDomainService.java:178`（均只调 `isExpired()`）· `service/favorite/favorite-rpc/.../fact/FavoriteAuthorityEndpointConfig.java:276`（`bindToCurrent`）⇒ ⇒ **4 处全部只依赖「剩余预算/是否过期」的语义 ⇒ 语义未变 ⇒ 零破坏** |
| **⚠️ 语义微变（已出反馈单给 G3）** | `favorite-rpc` 的生产调用点 `FavoriteAuthorityEndpointConfig.java:276` 传的仍是**墙钟 epoch 毫秒** ⇒ ⇒ **到期时刻不变**，但**到期判定不再受墙钟跳变影响**（这正是修复点）⇒ ⇒ 对它是**更准**而非更松 ⇒ 已按 AGENTS.md §5.3e 出**反馈单 7 项**给 G3（favorite 域）知会 |

**对照结论（同一件事的两侧，别混谈）**：
- ✅ `GoStyleRequestDeadline`（**出向**）**纳秒精度 ✅ 与 Go 等价** —— 存 nanos、`applyTo` 用 `withDeadlineAfter(remainingNanos, NANOSECONDS)`。
- ✅ `DeadlineState`（**入向**）**单调钟 + 纳秒 ✅ 与 Go 等价** —— **2026-10-29 已修**（本条）。

⇒ 两个载体**仍不共享实现**（各自一个 `ThreadLocal<Long>` nanos），
但**两侧现在都是单调钟 + 纳秒** ⇒ ⇒ 「出向已对齐」**可以**推出「入向也对齐」了（**2026-10-29 起**）。

---

### DRIFT-COMMON-006 —— `DeadlineState.markExpired(long)` 是死代码；且有一处跨域 javadoc 在描述它（`OBSERVABLE`）

| | |
|---|---|
| **Go 出处** | 无对应物（ctx 由标准库持有，业务方不需要手动「设过期时刻」） |
| **RAG 落点** | `RpcInterceptor.java:597` `markExpired(long epochMillis)` |
| **差异** | 2026-10-24 全仓 grep 实证：**零生产调用方** —— 只有自身定义 + 3 处 javadoc 提及。实际生效路径是 `bindToCurrent(deadlineEpochMs)`（`:281`，`onHalfClose` 里绑定）。<br>且两者有一处实质差别：`bindToCurrent` 返回的 `Scope` 会在 try-with-resources 结束时 `remove()`（**防线程池串味**），`markExpired` **不会自动清** |
| **连带（跨域）** | 🔴 `service/users/user/user-rpc/.../UserDomainService.java:115` 的 javadoc 声称「配合 `DeadlineState#markExpired(long)`（由 `RpcInterceptor` 调度）」—— **与事实不符**：M7 修复后 deadline 由 **`onHalfClose`** 绑定，且**不在调度线程**上。<br>⇒ **该文件属 users 域，本域不改，仅登记并需知会 `align/users-user`** |
| **处置** | ✅ **本域已修**：`markExpired` 标 `@Deprecated`，javadoc 改写为陈述事实（真实路径、为何保留、为何不删）<br>⚠️ **未删除** —— users 域的 javadoc 仍以 `{@link DeadlineState#markExpired(long)}` 链它，删除会造成**跨域悬空链接**；等该域自行清理引用后再删（实测 `mvn -pl service/users/user/user-rpc install` **EXIT=0**，链接未悬空） |

> ⚠️ **2026-10-30 真实性复核（global+common lane）**：原判 **「`markExpired(long)` 是死代码 ＋ 有一处跨域 javadoc 在描述它」**（上方「处置」栏写的「标 `@Deprecated` · **未删除**」是 2026-10-23/24 的快照）⇒ **现行态 = ✅ 已闭环（2026-10-25 方法本体已删除 · 两域同一次收口）**。
> 依据（RAG 侧）：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:903`（「⚠️ 2026-10-25（`align/global` 收口提交）：原 `markExpired(long)` 已**删除**」）＋ `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:404`（**全文仅剩的 2 处 javadoc 提及之一** ⇒ **方法声明已不存在**）＋ 跨域侧 `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:148`（「⇒ 曾被列为候选来源的 `markExpired(long)` **已移除**」）＋ 守门 `service/common/src/test/java/com/rag/common/observability/RpcInterceptorDeadlineBindingTest.java:206`（断言 `DeadlineState` 上**已无** `markExpired` 声明）· Go 侧 `service/common/observability/rpc.go:69`（服务端超时由 **ctx 自身**判定并 `MarkTimeout` ⇒ **Go 侧无「手动设过期时刻」的对应物**）· 核对日期 **2026-10-30**。
> ⇒ **循环依赖 `CYCLE-001` 同时闭合**（见 `docs/Intentional_Deviations/global/cycle-deps.md` 的 `CYCLE-001` 行）。

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
| DRIFT-CF-001 | `newProcessLogger` 内强制 `validate()`，Go 不校验；空值本应输出 `"unresolved"` | `logging.go:43-67` vs `ProcessLogger.java:150-151` | 未修。⚠️ **跨文件**：需同步改 `favorite-rpc/FavoriteFactProcessLogging.java:76-84` 的 try/catch 降级<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（**C 类 · 阶段二暂缓** ⇒ 不因 C 类升级成 🔴）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/communityfact/ProcessLogger.java:150-151`（`meta.validate();` **仍在**）· Go `service/common/communityfact/logging.go:43-67`（`NewProcessLogger` **不调** `meta.Validate()` ⇒ 空值由 `known()` 落 `"unresolved"`）⇒ **差异仍在** · 核对日期 **2026-10-30** |
| DRIFT-CF-002 | `AuthorityHandlerFactory` 走 3 参重载拿**伪造 logger**，Go 用进程信封 logger ⇒ 6 个 metadata 键无保证 | `authority_http.go` + `fact-authority/main.go:29,71` vs `AuthorityHandler.java:106-108` | 未修（建议改 4 参）<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— ⚠️ **原指针指错行**：`AuthorityHandler.java:106-108` 今日落在 `AuthorityServlet` 的构造器上，**不是**「工厂走 3 参重载」那一处；真正的**消费点** = `service/common/src/main/java/com/rag/common/spring/AuthorityHandlerFactory.java:48`（`AuthorityHandler.create(producer, token, lookup)` ⇒ 走 **3 参**重载）＋ `service/common/src/main/java/com/rag/common/communityfact/AuthorityHandler.java:95-97`（3 参重载用 `LoggerFactory.getLogger(AuthorityHandler.class)` ⇒ **伪造 logger**，非进程信封 logger）· Go `service/comment/rpc/cmd/fact-authority/main.go:29`（`logger := communityfact.NewProcessLogger(os.Stderr, meta)`）＋ `:71`（`communityfact.NewAuthorityHandler(..., logger)`）⇒ **差异仍在**；原写的裸 basename `fact-authority/main.go:29,71` 在真源仓有 **3 个同名** `main.go` ⇒ 本轮改用全路径 · 核对日期 **2026-10-30** |
| DRIFT-CF-005 | Dispatcher 的 10s 超时**只发线程中断** + **单线程池** ⇒ 一次挂死永久卡死；Go 的 ctx 取消会释放 goroutine | `process.go:28-30` + `wire.go:97` vs `Dispatcher.java:122,127` + `:96` | 未修。建议改 `HttpRequest.timeout(...)`（与 B-1 同款定性：`setQueryTimeout` 而非 interrupt）<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— ⚠️ **原指针已随文件演进过期**：`Dispatcher.java:122,127` ＋ `:96` 今日落在 javadoc 上；现码**消费点** = `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:279`（`Executors.newSingleThreadExecutor(...)` ⇒ **单线程池**）＋ `:315`（`future.get(10, TimeUnit.SECONDS)` ⇒ 10s deadline）＋ `:320`（`future.cancel(true)` ⇒ **只发线程中断**）· Go `service/common/communityfact/process.go:28-30`（`context.WithTimeout(attempt, 10*time.Second)` ⇒ ctx 取消**释放 goroutine**）⇒ **差异仍在** · 核对日期 **2026-10-30** |
| DRIFT-CF-006 | 节奏语义：Go `time.NewTicker(1s)` **创建即起算**，RAG **每批后再等满 1s** | `process.go:41,56-60` vs `Dispatcher.java:198-201` | 未修。稳态吞吐两侧不同量级<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— ⚠️ **原指针已过期**：`Dispatcher.java:198-201` 今日落在 `runAsync` 的 javadoc 上；现码**消费点** = `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:399-404`（`stopSignal.get(1, TimeUnit.SECONDS)` ⇒ **每批跑完再等满 1s**）· Go `service/common/communityfact/process.go:41`（`ticker := time.NewTicker(time.Second)` ⇒ **创建即起算**）＋ `:56-60`（`select { case <-ctx.Done(): … case <-ticker.C: }`）⇒ **差异仍在** · 核对日期 **2026-10-30** |
| DRIFT-CF-008 | authority 日志 trace 关联：Go 用 handler ctx，RAG 建 span 后**从不 `makeCurrent()`** ⇒ 三条日志通常无 `trace_id` | `authority_http.go:30-37` + `logging.go:75-81` vs `AuthorityHandler.java:137-143,253` | 未修<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 差异**仍在**（RAG 建 span 后**从不 `makeCurrent()`**）；⚠️ **原指针指的不是这一处**：`:137-143` 今日落在 header / Authorization 校验上；真正的落点 = `service/common/src/main/java/com/rag/common/communityfact/AuthorityHandler.java:128-132`（`spanBuilder(...).startSpan()` ＋ `ctx = ctx.with(span)` ⇒ **无 `makeCurrent()`**）＋ 三条日志 `:198-206` / `:220-226` / `:247-253`（均走 SLF4J ⇒ 读不到 ctx）· Go `service/common/communityfact/authority_http.go:30-37`（`ctx := otel…Extract(...)` ⇒ `logger.InfoContext(ctx, …)`）⇒ **差异仍在** · 核对日期 **2026-10-30** |

### `OBSERVABLE DIFFERENCE`（8 条）

| # | 项 | Go 出处 | 现状 |
|---|---|---|---|
| DRIFT-CF-003 | authority 响应体：Go `json.NewEncoder().Encode` **追加尾 `\n`** 且 `escapeHTML` 默认开；RAG 两者都没有 ⇒ **字节不同** | `authority_http.go:72` vs `AuthorityHandler.java:223,239` | 未修（需先定「以哪侧为真」）<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类 · 需先定「以哪侧为真」）—— ⚠️ **原指针指错行**：`AuthorityHandler.java:223,239` 今日落在两处 WARN 日志块上，**不是**响应体写；真正的落点 = `service/common/src/main/java/com/rag/common/communityfact/AuthorityHandler.java:217`（`MAPPER.writeValueAsBytes(fact)` ⇒ **无尾 `\n`**、Jackson 默认**不**转义 HTML）＋ `:233`（`resp.getOutputStream().write(body)`）· Go `service/common/communityfact/authority_http.go:72`（`json.NewEncoder(w).Encode(fact)` ⇒ **追加尾 `\n`** ＋ `escapeHTML` 默认开）⇒ **字节仍不同** · 核对日期 **2026-10-30** |
| DRIFT-CF-009 | `deliveryErrorCode`：Go `errors.Is` 只认哨兵**实例**；RAG 按**类型**判 ⇒ `error_code` 取值范围更宽 | `process.go:64-69` + `wire.go:35` vs `Dispatcher.java:288-302` | 未修（`Dispatcher.java:279-286` 的 javadoc 自称「与 `errors.Is` 同构」，失准）<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（**D 类**）—— ⚠️ **本行「⇒ `error_code` 取值范围更宽」这一句已被本文件 §十三·一 证伪**（两侧 `deliveryErrorCode` **各只有 2 个返回值** ⇒ 取值集合**完全相同**；真实差异是**判定边界**：Go 认哨兵**实例** / RAG 认**类型**，且**当前不可达**）⇒ 本行该半句**过强**，以本段为准；⚠️ **原指针已过期**：`:288-302` 今日落在 `runAsync` 上，现码 = `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:531-545`（`t instanceof Wire.FrozenEnvelopeBlockedException` ⇒ **按类型**判）＋ `:528-529`（自称「与 Go 的 `errors.Is` 同构」的 javadoc 现落在此）· Go `service/common/communityfact/process.go:64-69`（`errors.Is(err, ErrFrozenEnvelopeBlocked)` ⇒ **按实例**判）＋ `service/common/communityfact/wire.go:35`（哨兵定义）· 核对日期 **2026-10-30** |
| DRIFT-CF-011 | OBS-r3 信封 JSON 键顺序**与 Go 相反**（metadata 块前移、trace 块前移到 KV 之前） | `logging.go:50-58,61-65,78` vs `ProcessLogger.java:246-269` | 未修。`ProcessLogger.java:55` 注释称「严格保证顺序」说明是**刻意**选择 ⇒ 应走签字偏离<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（**D 类** · JSON 键序无语义）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/communityfact/ProcessLogger.java:246-269`（顺序 = metadata 块 → timestamp/level/message → trace 块 → KV）· Go `service/common/communityfact/logging.go:50-58,61-65,78`（`WithAttrs` 注入 metadata ＋ `Handle` 里 `record.AddAttrs(trace_id, span_id, trace_sampled)`）⇒ **两侧键序仍相反**；`ProcessLogger.java:55` 的「严格保证顺序」注释**仍在** ⇒ **刻意选择**成立 ⇒ 仍应走签字偏离 · 核对日期 **2026-10-30** |
| DRIFT-CF-012 | 时间戳精度：Go **纳秒** / RAG **毫秒** | `logging.go:53` vs `ProcessLogger.java:85-98` | 未修<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/communityfact/ProcessLogger.java:85-98`（`formatTimestamp(long epochMillis)` ⇒ **毫秒**精度，`ILoggingEvent#getTimeStamp()` 本身即毫秒）· Go `service/common/communityfact/logging.go:53`（`Format("2006-01-02T15:04:05.999999999Z07:00")` ⇒ **纳秒**）⇒ **差异仍在** · 核对日期 **2026-10-30** |
| DRIFT-CF-013 | token 长度阈值：Go `len()` 是 **UTF-8 字节数**，RAG `String.length()` 是 **UTF-16 码元** | `wire.go:87` + `authority_http.go:24` vs `Wire.java:191` + `AuthorityHandler.java:98` | 未修（非 ASCII token 时阈值不等）<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类 · RAG 侧**更严**，非安全缺口）—— ⚠️ **原指针有一处指错行**：`AuthorityHandler.java:98` 今日是**空行**；真正的落点 = `service/common/src/main/java/com/rag/common/communityfact/AuthorityHandler.java:87`（`token.length() < 32` ⇒ **UTF-16 码元**）＋ `service/common/src/main/java/com/rag/common/communityfact/Wire.java:191`（同款 `token.length() < 32`）· Go `service/common/communityfact/wire.go:87`（`len(token) < 32` ⇒ **UTF-8 字节数**）＋ `service/common/communityfact/authority_http.go:24`（同款）⇒ **差异仍在** · 核对日期 **2026-10-30** |
| DRIFT-CF-014 | 畸形 endpoint 报错形态：Go **统一文案**；RAG `URI.create` 直接抛 `IllegalArgumentException` | `wire.go:84-89` vs `Wire.java:194` | 未修<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/communityfact/Wire.java:194`（`URI parsed = URI.create(endpoint);` ⇒ 畸形 endpoint 直接抛 `IllegalArgumentException`）· Go `service/common/communityfact/wire.go:84-89`（`url.Parse` 失败 ⇒ 走**统一文案**「community DataCenter event sender is not configured」）⇒ **报错形态仍不同** · 核对日期 **2026-10-30** |
| DRIFT-CF-015 | receipt 解码错误少一层 `decode DataCenter event receipt: ` 前缀 | `wire.go:116` vs `Wire.java:240` | 未修<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/communityfact/Wire.java:240`（`strictDecode(...)` ⇒ **无** `decode DataCenter event receipt: ` 前缀）· Go `service/common/communityfact/wire.go:116`（`fmt.Errorf("decode DataCenter event receipt: %w", err)`）⇒ **差异仍在** · 核对日期 **2026-10-30** |
| DRIFT-CF-017 | 缺 `InstallFrameworkLogger` 等价物（框架日志未并入 OBS-r3，键未改名，`log_source` 永远只有 `"application"`） | `logging.go:104-135` vs `ProcessLogger.java:46-47` | 未修。⚠️ 是否真触发取决于各域 logback 配置<br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类 · 是否真触发取决于各域 logback 配置）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/communityfact/ProcessLogger.java:46-47`（javadoc 明写「Java 端**没有** `go-zero logx` 等价物；`InstallFrameworkLogger` 在 Java 版不需要，删除对应方法」）＋ `:251`（`log_source` **恒为** `"application"`）· Go `service/common/communityfact/logging.go:104-135`（`InstallFrameworkLogger` ＋ `frameworkLogWriter.write` 把框架日志**并入同一信封**并把 `caller`/`trace`/`span` **改名**为 `code_location`/`trace_id`/`span_id`）⇒ **差异仍在** · 核对日期 **2026-10-30** |

### `UNRESOLVED`（1 条）

| # | 项 | 为什么判不了 |
|---|---|---|
| DRIFT-CF-004 | `Event.payload` 序列化路径不同：Go `json.RawMessage` **逐字输出**；RAG `Object` 由 Jackson 按**运行期类型**重序列化（键序 / 数字字面量 / 重复键全丢） | 取决于 `favorite-rpc` / `comment-rpc` 实际怎么填 `payload`。**不在 `common` 包内可判定**，须随那两域的审计一起定级<br>⚠️ **2026-10-30 复核（global+common lane）**：**原判「UNRESOLVED」已过期**（本文件 §十三·一 已定级）⇒ **现行态 = 🔴 已定级 · C 类（阶段二暂缓）** —— 差异**全部落在** `service/common/src/main/java/com/rag/common/communityfact/Wire.java:129`（`@JsonProperty("payload") public Object payload;` ⇒ Jackson 按**运行期类型**重序列化）· 反证：`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteDeliveryValidator.java:84-85`（`payload` 声明成 `JsonNode` ⇒ **保留原始形态**，favorite 是**唯一已规避**的域）＋ `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentDeliveryValidator.java:140-148`（strict decode）· Go `service/common/communityfact/wire.go:47`（`Payload json.RawMessage` ⇒ **逐字输出**）· 核对日期 **2026-10-30** |

---

## 六、`logger` / `response` / `snowflake` 结论

### `response` —— ✅ `SEMANTICALLY EQUIVALENT`（逐条等价）

`code` / `msg` / `data` 三个字段的数字类型、键名、**字段顺序**、无 `omitempty` 全部一致；`null` 两侧同样输出。
唯一差异是 `Response.java:3` 一个**未使用的 import**（无行为影响）。

### `logger` / `snowflake` —— 🔴 `SEMANTICALLY NOT EQUIVALENT`

| # | 项 | 类型 | Go 出处 |
|---|---|---|---|
| DRIFT-LOG-001 | KV 字段生产侧全丢 | MISMATCH | `logger.go:148-157,168` — **见 §三，已单独展开**<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 差异**仍在**，但**措辞要收窄**（见本文件 §十三·一 LOG-001）：数据**已进** logback 事件，只是**全仓无 pattern 渲染**。RAG 侧**消费点** = `service/common/src/main/java/com/rag/common/logger/Logger.java:263-281`（`getCallChain(3)` ＋ `atError().setMessage("business_error").addKeyValue(...)` ⇒ KV 挂在 `ILoggingEvent.getKeyValuePairs()` 上）＋ `service/common/src/main/resources/logback-spring.xml`（**不存在** ⇒ `find **/logback*.xml` = **0 命中**）＋ `service/like/like-rpc/src/main/resources/application.yml:332`（`logging:` 块**只有 `level:`**、无 `pattern`）· Go `service/common/logger/logger.go:148-157`（8 个 `logx.LogField`）＋ `:168`（`logx.WithContext(ctx).Errorw("business_error", fields...)`）· 核对日期 **2026-10-30** |
| DRIFT-LOG-002 | `call_chain` 比 Go **少一帧**：Go 尾部恒有 `logger.LogXxx` 包装帧（`runtime.Callers` skip 语义），RAG 跳过 | MISMATCH | `logger.go:139` + `:320-323`（⚠️ Go 侧 `logger.go:139` 自己的注释描述的是 skip=4 的语义，**Go 注释与实现不符**）<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 差异**仍在**，本轮把**成因**落到代码：RAG 侧 `service/common/src/main/java/com/rag/common/logger/Logger.java:148`（`FILTER_PREFIXES` 里第 10 条 `"com.rag.common.logger."` ⇒ **滤掉 logger 自身帧** ⇒ 少了 `Logger.LogBusinessErr` 那一帧）＋ `:494-509`（`getCallChain(int skip)` 用 `StackWalker` 逐帧 `isFiltered`）· Go `service/common/logger/logger.go:139`（`getCallChain(3)`）＋ `:320-323`（`runtime.Callers(skip, pcs)` ⇒ **skip=3 从导出的包装帧起算**）＋ `:44-58`（`filterPrefixes` 只含 `runtime.` / `testing.` / `github.com/zeromicro/go-zero/` ⇒ **滤不掉** logger 自身帧）⇒ **RAG 仍比 Go 少一帧** · 核对日期 **2026-10-30** |
| DRIFT-LOG-003 | 生产代码**从不调 `Logger.init`** ⇒ 三个静态方法必抛 `IllegalStateException`；且 `serviceName` 来源与 Go 的 `c.Name` 是不同取值体系 | MISMATCH | `logger.go:63-70`（Go 侧 15 处 main 都调 `logger.Init`）<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 本轮实测**生产代码仍零调用**：全仓 `grep -rn 'Logger\.init('` 命中 **16 处**，其中 **14 处在** `service/common/src/test/java/com/rag/common/logger/LoggerTest.java`、**2 处是主源码里的 javadoc 注释**（`service/common/src/main/java/com/rag/common/spring/LoggerFactoryBean.java:49` ＋ `service/article/article-api/src/main/java/com/rag/article/article/api/service/ArticleAuthorResolver.java:60`）⇒ **`src/main` 里零调用**成立；⇒ 「三个静态方法**必抛** `IllegalStateException`」这一句**只在被调用时**才成立（当前生产零调用 ⇒ **当前不会抛**）· Go `service/common/logger/logger.go:63-70`（`Init` 用 `sync.Once`，Go 侧各服务 main 都调）· 核对日期 **2026-10-30** |
| DRIFT-LOG-004 | `Init` 失败后可恢复性相反：Go `sync.Once` 中毒（失败即永久失效），RAG 可重试成功 | MISMATCH | `logger.go:63-70` vs `Logger.java:171-181`<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（**D 类** · RAG 侧**更可恢复**）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/logger/Logger.java:171-181`（`init()` 用 `synchronized (GLOBAL) { if (!GLOBAL.initialized) { … } }` ⇒ **失败后不毒化、可重试成功**）· Go `service/common/logger/logger.go:63-70`（`globalLogger.once.Do(...)` ⇒ `sync.Once` **中毒**，失败即永久失效）⇒ **可恢复性仍相反** · 核对日期 **2026-10-30** |
| DRIFT-LOG-005 | Go 自动附加 `caller` / `trace` / `span`，RAG 全无（MDC 也不在默认 pattern 里） | OBSERVABLE | go-zero `richlogger.go:210,217-225`<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 指针当场核过（Go 侧在 `go-zero@v1.10.3` 的 module cache 内）：`core/logx/richlogger.go:210`（`Field(callerKey, getCaller(...))` ⇒ **caller**）＋ `:217-220`（**trace**）＋ `:221-225`（**span**）；RAG 侧 `service/common/src/main/java/com/rag/common/logger/Logger.java:274-281`（`doLogBusinessErr` 的字段集 = service/event_id/trace_id/error_code/file_line/call_path/call_chain/error_reason）⇒ **无 `caller`、无 `span`**（`trace` 与 `trace_id` 同值 ⇒ 真正净损失 = `caller` + `span`，见 §十三·一 LOG-005）· 核对日期 **2026-10-30** |
| DRIFT-LOG-006 | Go `Mode: file` 落盘 + 滚动；RAG 只写 stdout | OBSERVABLE | 各服务 yaml<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类 · 部署形态）—— ⚠️ **本条此前无任何 `文件:行号` 指针（不可复算）⇒ 本轮补上**：Go 侧 `service/user/user/rpc/etc/user.yaml:6`（`Mode: file` ⇒ **落盘**）＋ `:7`（`Path: ../../../../log/user-rpc` ⇒ **按服务分目录**）＋ `service/common/logger/logger.go:62`（「日志的基础配置（Mode/Path/Level等）由 go-zero 配置文件加载」）；RAG 侧 `service/like/like-rpc/src/main/resources/application.yml:332`（`logging:` 块**只有 `level:`** ⇒ **无 `logging.file.name`**）＋ `service/common/src/main/resources/logback-spring.xml`（**不存在**）⇒ 配合全仓 `find **/logback*.xml` = **0 命中** ⇒ **RAG 只写 stdout**（Boot 默认 console appender）· 核对日期 **2026-10-30** |
| DRIFT-LOG-007 | `error_reason` 空 message 兜底：Go 恒取 `Error()`（得 `""`），RAG 退 `toString()`（得类全名） | OBSERVABLE | `logger.go:118-125` vs `Logger.java:243`<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/logger/Logger.java:243`（`errorReason = err.getMessage() != null ? err.getMessage() : err.toString();`）· Go `service/common/logger/logger.go:118-125`（`errorReason = err.Error()` ⇒ `getMessage()==null` 的异常得 `""`）⇒ **分歧仍在**，且**边界更窄**：只存在于 `getMessage()==null` 的异常（`new RuntimeException("")` 两侧**已一致**）· 核对日期 **2026-10-30** |
| DRIFT-SF-001 | **时钟源不同**：Go `time.Since(n.epoch)` 走**单调时钟**（回拨不可达）；RAG `System.currentTimeMillis()` 走**墙钟**（NTP / `setSystemTime` / 虚拟机快照恢复都会倒退）⇒ 位布局与回绕逻辑逐条一致，但「无回拨保护」在两侧的**实际概率不在一个量级**，RAG 一旦回拨重合即产**重复 ID** | MISMATCH | `bwmarrin/snowflake@v0.3.0/snowflake.go:126,139` vs `Snowflake.java:242`<br><br>⚠️ **2026-10-30 复核（global+common lane）**：**原判 MISMATCH 已过期** ⇒ **现行态 = ✅ 已修（2026-10-29 · G4 lane）** —— 依据：RAG `service/common/src/main/java/com/rag/common/snowflake/Snowflake.java:79`（`wallClockMillis`）＋ `:85`（`monotonicNanos`）＋ `:160`（`this.anchorEpochNanos = (wallClockMillis.getAsLong() - EPOCH) * 1_000_000L;` ⇒ **锚点只取一次**）＋ `:313-320`（javadoc 引库注释「to make sure we use the monotonic clock if available」）⇒ **墙钟回拨 ⇒ 重复 ID 的路径已断** · Go `bwmarrin/snowflake@v0.3.0/snowflake.go:126,139`（`n.epoch = curTime.Add(...Sub(curTime))` ＋ `time.Since(n.epoch)` ⇒ **单调**）· 核对日期 **2026-10-30** <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/check-deadline-downstream.py` ⇒ **EXIT=0**（2026-10-30 03:48:11 · 12s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ sql_carrier        Go=present_expired \| RAG=present_expired  （两侧一致） ⏐ ✅ failed_fast        Go=true \| RAG=false  （已登记的漂移形态） ⏐ ✅ code               Go=4 \| RAG=0  （已登记的漂移形态） ⏐ ✅ outgoing_carrier   Go=present_expired \| RAG。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| DRIFT-SF-002 | workerId 多出 Go 没有的 `snowflake.worker-id` 配置档（且已确认该档是**活的**：`article-api` 扫 `com.rag.common`） | OBSERVABLE | `snowflake.go:53-59,62-84` vs `SnowflakeBean.java:59-78`<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类 · 表达层扩展）—— 指针当场核过：RAG `service/common/src/main/java/com/rag/common/spring/SnowflakeBean.java:59-78`（`resolveWorkerId(props)` 的 **三档**：env → **Properties** → hash）＋ `service/common/src/main/java/com/rag/common/spring/SnowflakeProperties.java:26-27`（`@Component` ＋ `@ConfigurationProperties("snowflake")` ⇒ **确有一档 yaml 配置**）＋ `service/article/article-api/src/main/java/com/rag/article/article/api/ArticleApiApplication.java:81-84`（`@SpringBootApplication(scanBasePackages = {"com.rag.article.article.api", "com.rag.common"})` ⇒ **该档在 article-api 里是活的**）· Go `service/common/snowflake/snowflake.go:53-59`（env 档）＋ `:62-84`（hash 兜底）⇒ **Go 侧确无 yaml 档** ⇒ 差异仍在 · 核对日期 **2026-10-30** |
| DRIFT-SF-003 | 初始化时机不同（Go 懒加载于首次 `GetID`；RAG 容器 refresh 期）+ `SNOWFLAKE_NODE_ID` **空白容错相反**（Go `Atoi` 不 trim，RAG `trim()`） | OBSERVABLE | `snowflake.go:22-42,56` vs `Snowflake.java:128-142,285`<br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类）—— ⚠️ **原指针已过期**：`Snowflake.java:128-142,285` 今日落在 `State` 的锚点 javadoc 与 `Generate()` javadoc 上；现码**消费点** = `service/common/src/main/java/com/rag/common/spring/SnowflakeBean.java:40-43`（`@Component` 构造器 ⇒ **容器 refresh 期**就 `Snowflake.setWorkerId(workerId)`）＋ `service/common/src/main/java/com/rag/common/snowflake/Snowflake.java:389`（`Long.parseLong(explicit.trim())` ⇒ **trim**）＋ `:204-218`（`init()` 的 `sync.Once` 等价物）· Go `service/common/snowflake/snowflake.go:22-42`（`Init()` 在 `GetID()` 里**懒加载**）＋ `:56`（`strconv.Atoi(idStr)` ⇒ **不 trim**）⇒ **两处差异仍在** · 核对日期 **2026-10-30** |

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
| DRIFT-CF-018 | `Dispatcher.run(once=false)` 创建了 `handle`（`Dispatcher.java:146`），但只在**循环退出后**才返回（`:211`），`once` 分支在 `:173` 直接 return ⇒ **`@PreDestroy` 拿不到 handle**，`Dispatcher.java:223-227` 的 javadoc 描述的用法**不可达** | **API 缺陷**（非 Go 语义差异） | `Dispatcher.java:146,173,211,223-227`<br>🟢 **2026-10-29（R3 轮）已修** ⇒ 见 §十四·一 #15 与 `docs/DRIFT/global/vs-common.md` §4.18.1<br>⚠️ **首版的行号已过期**：现码为 `Dispatcher.java:222`（建句柄）/`:253`（once 分支返回）/`:291`（循环退出后返回）⇒ **首版两处不准**：① 「once 分支直接 return ⇒ 拿不到 handle」**对现码不成立**；② 真实成立的是「**`once=false` 的阻塞版**拿不到 handle」 <br>⚠️ **2026-10-30 复核（global+common lane）**：**同一条目的行号又移位了一次**（原值保留）—— 现码落点 = `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:338`（建句柄）＋ `:343-347`（句柄在**进入循环之前**发布）＋ `:374`（`once` 分支**确实** `return handle;`）＋ `:412`（`once=false` 时循环退出后才返回）⇒ ⇒ **首版结论「once 分支直接 return ⇒ 拿不到 handle」对现码仍不成立**；真实成立的是「**`once=false` 的阻塞版**拿不到 handle」⇒ 与本文件 §十三·一 与 §十四 两处已追加的 2026-10-30 复核段一致 · 核对日期 **2026-10-30** |
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

### 决策 2：DRIFT-COMMON-005 **先记入台账，本轮不改** —— 🟢 **2026-10-29 阻塞已解除并已修**（见 §一·五）

| 项 | 值 |
|---|---|
| 拍板 | 📌 **先记入台账，不改**（2026-10-24，用户） |
| 归档位置 | ✅ 留在 `docs/DRIFT/common/`（**不**进 `docs/Intentional_Deviations/common/`）<br>判据（§5.3b 硬边界 2）：**这条差异要不要「修回去」？** —— 要（技术上可修），只是**改动是跨域破坏性的** ⇒ 属 DRIFT，不属「决定不对齐」 |
| 改动为什么必须跨域 | `bindToCurrent(long epochMillis)` 是 `service/common` 的**公共 API**，而 `admin-rpc` / `user-rpc` 的**测试**直接调它（`AdminDeadlinePassthroughTest.java:150`、`DeadlinePassthroughTest.java:144`）⇒ 改签名会打断别域 |
| 关闭条件 | 需要一个**同时动 `service/common` + `admin-rpc` + `user-rpc`** 的决策（谁主导？API 兼容还是允许破坏？）⇒ **不属于单域可决** |
| ⚠️ 遗留 | `align/like` 的 `SD-2026-10-23-09` 已就**同一现象**签字，但**描述只对了一半**（见 §六·五·6）⇒ 合并时两侧描述需对齐 |
| **🟢 2026-10-29：关闭条件已被绕过 ⇒ 本条已修** | **关闭条件的前提是「只有改签名这一条路」** —— 而 2026-10-29 找到了**第二条路**：**签名与语义都不动**，只改**内部存储与读取**<br>⇒ ⇒ **跨域零破坏**（`admin-rpc` / `user-rpc` / `favorite-rpc` / `comment-rpc` 的调用方**一行不改**）⇒ ⇒ **「不属于单域可决」的前提消失** ⇒ ⇒ 本层（共享层波次）**自行落码**，**无需**再开「同时动三个域」的决策<br>⇒ 落码与证据 ⇒ **§一·五 的 DRIFT-COMMON-005 条目（已改为 ✅）** |
| **⚠️ 仍需合并收口知会的一点** | `align/like` 的 `SD-2026-10-23-09`（同一现象的签字）与本条**描述只对了一半** ⇒ ⇒ **本条已修 ⇒ 该签字项的「现象」已不复存在** ⇒ ⇒ **留给合并收口执行者**决定是否同步订正（**不是本层能单方面改的** —— 它在 `docs/Intentional_Deviations/like/`） |

---

## 九、📮 交接给主会话（合并时处理，本域不越界）

| # | 事项 | 归属 | 说明 |
|---|---|---|---|
| 1 | **`hot-rpc` 缺 `GrpcServerConfigurer`** | `align/hot` | DRIFT-COMMON-004。`align/hot` 当前 3 个 commit 都在 `hot-api` 侧，**未触及 hot-rpc 的 Application**。修法 + 必备测试见 §一·五 |
| 2 | **`UserDomainService.java:115` 的 javadoc 假断言** | `align/users-user` | DRIFT-COMMON-006 连带项：声称 deadline 由「`markExpired`（RpcInterceptor 调度）」设置，实际是 `onHalfClose` 的 `bindToCurrent` |
| 3 | **DRIFT-COMMON-005 的定案** | 跨域（common + users） | 决策 2 关闭条件：`bindToCurrent` 签名是否允许破坏性变更 |
| 4 | **`align/like` 的 `SD-2026-10-24-10` 描述订正** | `align/like` | 签字理由说 `GoStyleRequestDeadline` 只暴露毫秒，实际它内部是**纳秒**；真正只有毫秒的是 `DeadlineState`。⚠️ **2026-10-25 订正编号**：原文写 `SD-2026-10-23-09` ⇒ **记错了** —— `-09` 是**同档的另一条**（worker 池饱和的**裸 503**），与 deadline **无关** ⇒ 正确编号 = **`SD-2026-10-24-10`** ⇒ 勿按编号盲查 |
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
| 4 | **`align/like` 的 `SD-2026-10-24-10` 描述订正** | `align/like` | ✅ **已订正**（2026-10-25 · 第二十六轮 L5）—— 编号订正为 **`SD-2026-10-24-10`**（原文 `SD-2026-10-23-09` **记错** ⇒ `-09` 是 worker 池裸 503 那条）；签字理由订正为「**只读 API（`remainingMillis()`）只有毫秒粒度**」—— ⚠️ **不是「整个公开 API 只有毫秒」**（`applyTo(stub)` 是**纳秒**的，`GoStyleRequestDeadline.java:110-120`）；真实限制在**只读那一侧**：`applyTo` **没有「上界」概念** ⇒ 表达不了 `min(上限, 剩余)`（`LikeRpcClient.java:108-121`）⇒ 落点 ⇒ [`docs/Intentional_Deviations/like/api.md`](../../Intentional_Deviations/like/api.md) |
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

### 十二·一、层一：common 域自身的漂移真实条数 = **23 条编号 + 5 项未编号**（2026-10-29 三次订正 · 漏减 `DRIFT-CF-018`）

**对 §〇 第 40 行的订正**：

| §〇 原写 | 实测 | 依据 |
|---|---|---|
| **28** 条编号条目 | **23** 条 | `DRIFT-COMMON-004`（`hot-rpc` 缺 `GrpcServerConfigurer`）已由 **hot 域 2026-10-25 认领并关闭**（§十一 #1）⇒ `DRIFT-COMMON` 未修数 **3 → 2**；🟢 **2026-10-29 再减 2**（`-005` 本轮已修 · `-006` 的代码 2026-10-25 已删 ⇒ 此前把「已删」算成「未修」是陈旧快照）⇒ **2 → 0** ⇒ ⇒ **27 → 25**；🟢 **2026-10-29 再减 1**（`DRIFT-SF-001` 本轮已修，见 §十三·二）⇒ ⇒ **25 → 24**；🟢 **2026-10-29 再减 1**（`DRIFT-CF-018` **共享层半边已修**，见 §六·六·1 与 `docs/DRIFT/global/vs-common.md` §4.18.1）⇒ ⇒ **24 → 23** ⇒ ⚠️ **「24 条」是修 `CF-018` 之前的数**（陈旧快照） |
| communityfact **15** 条 | ✅ **15 是对的**（**全量条数**；⚠️ **未修 = 14** —— `-018` 的**共享层半边**已于 2026-10-29 修（见 §六·六·1 与 `docs/DRIFT/global/vs-common.md` §4.18.1），但 **favorite 域半边仍待修** ⇒ 故它**不能整体**读作「已闭合」） | §五 的表格只列 **14** 条（5 MISMATCH + 8 OBS + 1 UNRESOLVED）⇒ **漏了 `DRIFT-CF-018`**（它在 §六·六「补录」里）⇒ **§五 的「14 条」是旧快照**，§〇 的 15 才是全量 |

**逐族明细（2026-10-25 扫描）**：

| 编号族 | 未修 | 编号清单 | 状态 |
|---|---|---|---|
| `DRIFT-COMMON` | **0** | 🟢 `-005`（`DeadlineState` 墙钟 vs Go 单调钟）**2026-10-29 已修**（见 §一·五）<br>🟢 `-006`（`markExpired` 死代码 + 跨域 javadoc 假断言）**代码已于 2026-10-25 删除**（`align/global` 收口提交）⇒ **本表此前把它算作「未修」是陈旧快照** | ✅ **两条都已闭合** |
| `DRIFT-CF`（communityfact） | **14** | `-001 -002 -003 -004 -005 -006 -008 -009 -011 -012 -013 -014 -015 -017 -018`（**无 -010 / -016**）—— 🔴 `-018` 的**共享层半边 2026-10-29 已修**（Go-faithful：停止能力归调用方 ⇒ 见 §六·六·1 与 `docs/DRIFT/global/vs-common.md` §4.18.1）⇒ **favorite 域半边仍待修**（落点 G3）⇒ ⇒ 本行未修数 **15 → 14** | 🔴 **共享层半边已修（2026-10-29）· 余 14 条未动** |
| `DRIFT-LOG`（logger） | **7** | `-001` ~ `-007` | 🔴 未修 |
| `DRIFT-SF`（snowflake） | **2** | `-002` `-003` | 🟢 `-001`（**墙钟回拨 ⇒ 重复 ID**）**2026-10-29 已修**（落码记录见 §十三·二）⇒ **3 → 2**；余 2 条未修 |
| **合计** | **23** | | + **5 项未编号**（§六·六）<br>🔴 **订正（2026-10-29 · 二次）**：`DRIFT-COMMON` 族 2 → **0**（`-005` 本轮已修 · `-006` 的代码 2026-10-25 已删 ⇒ 本表此前是**陈旧快照**）⇒ ⇒ **27 → 25**；**再减 1**（`DRIFT-SF-001` 本轮已修，见 §十三·二）⇒ ⇒ **25 → 24**<br>🔴 **订正（2026-10-29 · 三次）**：**再减 1**（`DRIFT-CF-018` **共享层半边已修** —— 见 §六·六·1 与 `docs/DRIFT/global/vs-common.md` §4.18.1）⇒ ⇒ **24 → 23** ⇒ ⚠️ **「24 条」是修 `CF-018` 之前的数**（**陈旧快照**）⇒ ⇒ **本表合计 = 23 条编号 + 5 项未编号 = 28**（与 §十四·三「⇒ 其中仍未修：**23** 编号 + **5** 未编号 = **28**」逐条对齐）<br>⚠️ **「未修」的口径已变**：这 23 条里**绝大多数是 OBSERVABLE**（不命中 `PhasedGoal.md` P0 任何一条）⇒ ⇒ 逐条定级见 §十三·一 ⇒ ⇒ **「未修」≠「阶段一未通过」** |

⚠️ **5 个可比包 3 个 `NOT EQUIVALENT`** 依然成立（`communityfact` / `logger` / `observability` / `snowflake` 中只有 `response` 逐条等价）⇒ **§〇 第 36 行未过期**。

### 十二·二、层二：common 是**唯一的共享写点** —— 外部域的尾巴清单（10 条）

> **这是比层一更硬的那层**：这些条目**不属于 common 域的漂移**，但**修法落在 `service/common` 的共享类里** ⇒ 改一处影响 6–10 个域 ⇒ 按 §5.3 必须**单域串行** ⇒ 并发对齐在这一点上**必然排队**。

| # | 域 | 条目 | 落点（`文件:行`） | 差异 | 当前真实状态（2026-10-25 实测） |
|---|---|---|---|---|---|
| **T-1** | `like` | `DRIFT-LIKEAPI-015` | `common/http/GoStyleJsonBodyConverter.java:115-117`（`getContentType().toString()` ⇒ MediaType 已小写化）vs `goReadsBody` `:98-101`（`contains` 大小写敏感） | Go 用**原始头**（`strings.Contains` 敏感），RAG 用**已解析 MediaType** | 🔴 **仍未修**（`like/api.md:461`）。⚠️ **6 域共用**（like / follow / message / users×2 / task / article）⇒ **单点解多域** |
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

#### ⚠️ **2026-10-29 真实性复核（W-F lane · shared-layer 收口域）—— §十二·二 这张表的「当前真实状态」列是 2026-10-25 的陈旧快照**

> ⚠️ **原表头写「当前真实状态（2026-10-25 实测）」** ⇒ 逐条回**两侧源码**复核（Go 真源 `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` ＋ RAG 本仓 `service/common/**`）后，**10 条里有 7 条的现状已变** ⇒
> ⇒ **本表的历史叙述行一律保留**（不改写历史）⇒ **现行态**见下表 · 核对日期 **2026-10-29**。
> ⚠️ **口径**：本表与 [`docs/DRIFT/global/_README.md`](../global/_README.md) §三 是**同一份清单的两个副本** ⇒ 后者已被 2026-10-25 逐轮订正过，**本表没跟上** ⇒ 本节即是把两者对齐。

| # | **原判（2026-10-25）** | **现行态（2026-10-29 复核）** | **两侧依据（`文件:行号`）** | **是否改判** |
|---|---|---|---|---|
| **T-1** | 🔴 仍未修 | ✅ **已修** | RAG `service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java:331` = `inputMessage.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)`（**原始头值** ⇒ 大小写敏感）· 判据本体 `goReadsBody` `:241-244` = `contentType.contains("application/json")`（**大小写敏感**）· Go `go-zero@v1.10.2/rest/httpx/requests.go:153-155`（`withJsonBody` = `strings.Contains`）⇒ **两侧同形** | ✅→✅（**原判为陈旧快照**） |
| **T-2** | 🔴 仍未修 | ✅ **已签字**（`SD-2026-10-25-01`）⇒ **不再是未修项** | RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:102`（`private final Semaphore latch;`）＋ `:106`（**构造期建一次** ⇒ 全进程一个）· Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go`（**每路由 new 一个**）⇒ **差异仍在** ⇒ **已签字 ⇒ 是决策不是缺陷** | ✅→✅（**原判为陈旧快照**） |
| **T-3** | ✅ 已修 | ✅ **已修**（**仅指针过期**） | RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:728` = `resp.setHeader("X-Content-Type-Options", "nosniff")`（方法体起于 `:716`）⇒ ⚠️ **原指针 `:431` 已失效**（该行现是 `try (…Scope ignoredScope = …)` ⇒ 与 `writeTimeoutResponse` 无关 ⇒ **E68 同型：行号合法但指错位置**）· Go `net/http/server.go` `http.Error` 路径同写该头 | ✅→✅（**指针订正**） |
| **T-4** | 🔴 仍开放，且全仓无 499 守门 | ✅ **观测面 + 资源面均已修** | RAG `…/observability/HttpMiddleware.java:88`（`HTTP_CLIENT_CLOSED_REQUEST = 499`）＋ `:612`（`flushCaptured`）＋ `:458`/`:484`（`writeTimeoutResponseQuietly(499, …)`）＋ `…/observability/GoStyleClientDisconnectProbe.java`（**主动探测写** = 方案⑤ ⇒ 在飞 gRPC 会被提前取消）⇒ **wire 偏离已另签字** `SD-2026-10-25-04` | ✅→✅（**原判为陈旧快照**） |
| **T-5** | 🔴 仍开放 | ✅ **已签字**（同 T-2） | 同 T-2 的两侧依据 | ✅→✅（**原判为陈旧快照**） |
| **T-6** | 🟡 仍 OPEN（用户 2026-10-24 拍板「保持登记，本轮不做」） | ✅ **已修**（**原「拍板不做」已被后续拍板覆盖**） | RAG `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（`GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"`）＋ `:151`（`response.getHeaders().set(CONTENT_TYPE, …)`）· 已登记 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2` ⇒ **10 个 api 全生效**（含 `service/like/like-api/pom.xml:61-64` 依赖 `common`）· ⇒ 挡路签字 **`SD-HOT-API-006` 已撤销**（2026-10-25 用户拍板撤销，理由即「修 T-6」）⇒ **不是「登记在册的待办」，是已闭合** | ✅→✅（**原判为陈旧快照**） |
| **T-7** | 🟡 仍 OPEN | 🟡 **仍是登记在册的待办**（**未签字**） | RAG 侧落点 = **Tomcat 容器层**（`service/common` 够不着）⇒ 已登记为**待签字候选** `SD-2026-10-25-CAND-14`（`docs/Intentional_Deviations/global/common-tails.md`）⇒ **状态取值：⏳ 未签字**（不是 🔴 缺陷、也不是 ✅ 已修） | 🟡→🟡（**仅表述精化**：待办 ⇒ 待签字候选） |
| **T-8** | 🟡 仍 OPEN | ✅ **已修**（残留一条已另登记） | RAG `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java`（**在位**）⇒ 「方法不匹配」出口**多带**一个 `Traceparent` 的残差已另登记为待签字候选 `SD-2026-10-25-CAND-15` | ✅→✅（**原判为陈旧快照**） |
| **T-9** | 🔴 仍开放 | ✅ **已修**（2026-10-25 第三轮） | RAG `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:185`（`String authority = channel.authority();`）＋ `:290`（`goBreakerName(targetOf(next), fullMethodName)`）⇒ 身份源 = `Channel.authority()` ⇒ 与 Go `path.Join(cc.Target(), method)` 同形 | ✅→✅（**原判为陈旧快照**） |
| **T-10** | 🔴 仍未修 | ✅ **已闭环**（两步走完） | RAG `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:138-151`（javadoc 明写「**2026-10-25 更正 —— 原 javadoc 误写为 `DeadlineState#markExpired(long)`，该断言为假**」＋「`markExpired(long)` **已移除**」）· `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` 现**已无 `markExpired` 方法**（仅剩 2 处 javadoc 提及）⇒ **循环依赖已断** | ✅→✅（**原判为陈旧快照**） |

> ⚠️ **2026-10-29 附一条「指针过期」的独立记账**（与上表的状态无关，但同源）：上表 T-3 / T-4 / T-6 三行的**落点行号**都已漂 ⇒
> **T-3** `HttpMiddleware.java:431` ⇒ **现 `:716`**（方法 `writeTimeoutResponse`）· **T-4** `HttpMiddleware.java:332` ⇒ **现 `:612`**（方法 `flushCaptured`）· **T-6** `HttpMiddleware.java:421` ⇒ **该行现为 `try (…Scope ignoredScope = …)`，与本条无关**（T-6 的真实落点是 `http/GoStyleJsonContentTypeAdvice.java:127`/`:151`）。
> ⇒ 三处**行号合法但指错位置**（**E68 同型**）⇒ **上表「落点」列按历史保留，不回改**（AGENTS.md §6「原值一律保留」）⇒ **以本附注为现行指针**。

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

1. **语义层**：common 域自身是**全仓最大的单域漂移堆**（**23 条**编号 + **5 项**未编号；5 个可比包 **3 个 `NOT EQUIVALENT`**）⇒ 这部分**没有收敛**，且 communityfact 的 **15 条里 14 条一条未动**（`-018` 的**共享层半边**已于 2026-10-29 修 ⇒ **favorite 域半边仍待修**）。
   > 🟥 **2026-10-29（R3 轮）订正**：本行原写「**27 条**」⇒ **已过期** ⇒ **24 条**
   > （`DRIFT-COMMON-005` · `DRIFT-SF-001` 本轮已修 · `DRIFT-COMMON-006` 的代码 2026-10-25 已删 ⇒ 27 − 3 = 24）
   > ⇒ 🔴 **再减 1**（`DRIFT-CF-018` **共享层半边已修** —— 见 §六·六·1 与 `docs/DRIFT/global/vs-common.md` §4.18.1）⇒ ⇒ **23 条** ⇒ 与 §十二·一 / §十四·三 逐条对齐。
   > ⇒ 逐条定级见 §十三·一。⚠️ **「23 条」≠「阶段一未通过」** —— 绝大多数是 `OBSERVABLE`。
2. **变更层（更硬）**：common 域是**唯一的共享写点** —— 别的域的未修项只要修法落在 `service/common`，就**自动从并发任务降级为串行任务**（§5.3 单域串行）⇒ **这才是 10 域并发对齐吞吐被卡住的那一层**，而不是「common 的漂移条目多」。

⇒ **两条可选路径（供裁决，本轮未选）**：

- **路径 A：立「共享写点」准入规则** —— 给 `service/common` 定专门归属、准入与串行手册（类似 `docs/Worktree.md`，但针对共享类），并要求**任何落进 `service/common` 的修复**先登记「影响哪些域」。
- **路径 B：先摘「单点解多域」的尾巴** —— 按「改一次同时解掉几条」排序，优先 `GoStyleJsonBodyConverter`（T-1，**6 域共用**）与 `GoStyleMaxConnsFilter`（T-2 + T-5，**2 条**）；再摘 十二·四 的**两步闭环**（`markExpired` ⇒ 2 条 + 1 条交接）。

⚠️ **本节给的是事实与顺序建议，不含任何实施**（用户 2026-10-25 明确「我自己修」）。

---

## 十三、📌 阶段一收工轮：25 条 `CF` / `LOG` / `SF` 逐条定级（2026-10-29 · G4 lane）

> **本节的依据**：`docs/PhasedGoal.md` 的 P0 九条（P0 #3「不存在已确认的严重数据丢失、**重复处理**或状态错误问题」
> 是本轮唯一的**真阻塞**判据）＋ `docs/twoStage.md` §5.3（差异与暂缓项的 **11 字段**）＋ §10（P0~P3 × A/B/C/D 阶段归属）。
>
> **取证方式**：两路**只读**子会话独立取证（CF 族 15 条 / LOG 族 7 条），逐条回 Go 真源原文取 `文件:行号`；
> SF 族 3 条由 G4 lane 亲自核（含本地 `~/go/pkg/mod/github.com/bwmarrin/snowflake@v0.3.0/snowflake.go` 与
> `$GOROOT/src/time/time.go`）。⇒ **不是转述，是逐行对照后的结论。**
>
> **证据强度口径**：本节全部为 **E2（源码级）** —— 未做双侧活体对拍（`tools/` 探针本轮未跑）⇒
> 每条「一致/不一致」的读法都是「**在该证据强度下不一致**」，不是「已验证」。
>
> 🔴 **本节的一条方法论订正**：**「一条差异」不等于「一个缺陷」**。台账首版把 25 条全部记成「未修」，
> 读起来像 25 个待修的 bug ⇒ 实际上 **其中 0 条命中 P0 九条**（唯一命中 P0 #3 的 `DRIFT-SF-001` 已在本轮落码修复）
> ⇒ ⇒ **它们绝大多数是「可观测性/形态/部署面」的差异** ⇒ 按 `twoStage.md` §5.3 属**合法的「差异与暂缓项清单」**
> ⇒ ⇒ **不是**「阶段一未通过」。⇒ ⇒ **但这不等于「它们都可以放着不管」** —— 下面逐条给出阶段归属与恢复方案。

### 十三·一、逐条定级总表（25 条）

> 🔴 **2026-10-29 口径订正（W-F lane · 共享层台账面）—— 本表的适用范围**：
> **本表的 25 条 = `CF` 族 15 + `LOG` 族 7 + `SF` 族 3** ⇒ ⇒ **它**不覆盖** `E65` / `E66` 这两条 `E` 族条目
> （落点同样在 `service/common`：`RpcInterceptor` / `GoStyleRequestDeadline` / `DeadlineState`）。
> ⇒ ⇒ **因此「本表 A 类 = 0」≠「共享层 A 类硬阻塞 = 0」** —— ⚠️ **2026-11-01 订正（G4 lane）**：原句写「🔴 **`E65` 是 A 类且仍 OPEN**」⇒ ⇒ **该前提已消失** —— `E65` 已于 **2026-10-30 双侧活体证伪并闭合**（`docs/DRIFT/global/_README.md:430`「✅ **已闭合（2026-10-30 · 第三次订正）**」⇒ 派生层 `E65` = **✅ 已一致** · gap = 「已闭合 · 差异已消失 ⇒ 无待实现项」）⇒ ⇒ **现态 = 本表 25 条 A 类 = 0 ＋ `E65` 已闭合 ⇒ 共享层 A 类硬阻塞 = 0**（`E66` 仍 🔴 未闭合 ⇒ 见 `docs/DRIFT/global/_README.md:431`）：
> ⚠️ **2026-10-29 双侧活体实测（Go `outgoing_carrier=present_expired` vs RAG `absent`）⇒ 「出向 gRPC 不继承请求 deadline」** —— ⚠️ **2026-10-30 该实测结论已被双侧活体证伪并订正**（两侧逐键相同 ⇒ 同款）⇒ ⇒ **此处保留原记录仅作历史** ⇒ ⇒ **以 `docs/DRIFT/global/_README.md:430` 的 E65 行（✅ 已闭合）为准**：
> ⇒ ⇒ **X-6 判据不满足 ⇒ C-3 破** ⇒ ⇒ **阶段一已改判为「❌ 未通过」**（`docs/PhaseGoal-judge.md` v5）—— ⚠️ **2026-11-01 订正（G4 lane）**：该判读**已被后续轮次推翻** —— `E65` 闭合 ⇒ ⇒ **阶段一现为「✅ 通过」**（主干 `1fa8a080`「全服务起栈 · 20/20 在服务 ⇒ 闭合 P0 #5 证据覆盖缺口 ⇒ 阶段一未通过 ⇒ 通过」）⇒ ⇒ **本表 25 条的定级结论不变**（C 21 · D 6 · 证据缺口 1 · A 0）：
> **证据**：`docs/PhaseGoal-judge.md` §五 X-6 行 ＋ [`../../DRIFT/global/vs-common.md`](../../DRIFT/global/vs-common.md) §1.1 的 **E65 / E66** 两行。
> ⇒ **本表 25 条的逐条定级本身不改**（原值保留）⇒ **只加这条范围说明**。

> **字段对应 `twoStage.md` §5.3 的 11 项**：编号 · Go 出处 · 原始行为 · 阶段一当前行为 · 具体差异 ·
> 暂缓理由 · 对业务正确性/安全性/数据一致性/可靠性的影响 · 是否阻断阶段一 · 阶段二恢复方案 · 阶段二测试场景与通过标准 · 当前证据及剩余缺口。
> 表格里逐列给出；「剩余缺口」统一为 **「未做双侧活体对拍 ⇒ 证据强度停在 E2」**（除特别标注）。

#### `communityfact` 族（15 条）

| # | Go 出处（`文件:行号`） | 原始行为 | 阶段一当前行为 | 具体差异 | 暂缓理由 | 影响（业务正确性/安全/数据一致性/可靠性） | 阻断阶段一 | 归属 | 阶段二恢复方案 + 测试与通过标准 | 证据 |
|---|---|---|---|---|---|---|---|---|---|---|
| **CF-001** | `logging.go:43-49`（`NewProcessLogger` 不调 `Validate`）＋ `:29-39`（`Validate` 是独立方法）＋ `comment/rpc/cmd/fact-authority/main.go:29`→`:32`（**调用方**才校验） | Go：**先建 logger、后校验** ⇒ 校验失败时那条日志**仍带** `unresolved` 信封 | `ProcessLogger.java:150-151` 工厂内 `meta.validate()` ⇒ **抛 `IllegalArgumentException`**；域侧 `favorite-rpc/.../bootstrap/FavoriteFactProcessLogging.java:79-87` **catch 后静默降级**为普通 SLF4J logger | **失败语义不同**：Go = 记一条 `unresolved` 信封日志后 `exit(1)`；RAG = 抛异常被吞 ⇒ **信封消失**，进程继续跑 | 失败路径的可观测性契约（非业务语义） | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（降级后日志不是 OBS-r3 信封，6 个 metadata 键全无） | **否** | **C**（阶段二） | ① `ProcessLogger.java:151` 的 `validate()` 移出工厂 ⇒ 改由**装配层**显式校验并 fail-fast；② `FavoriteFactProcessLogging.java:83-87` 的 catch **不得静默降级**，至少输出 OBS-r3 兜底信封。测试：构造 metadata 缺字段 ⇒ 断言**进程 fail-fast 且退出前产出含 `unresolved` 的信封**（双侧各跑一次，逐字节比对信封 JSON 键集合） | E2 |
| **CF-002** | `comment/rpc/cmd/fact-authority/main.go:29`（建**进程信封** logger）＋ `:71`（把该 logger 传给 handler） | Go：authority handler 拿到的**就是**进程信封 logger ⇒ 6 个 metadata 键有保证 | `AuthorityHandlerFactory.java:44` 走 **3 参重载** ⇒ `AuthorityHandler.java:95-97` 回落成 `LoggerFactory.getLogger(AuthorityHandler.class)`（**普通 SLF4J**） | 6 个 metadata 键（`service`/`environment`/`service_version`/`instance_id`/`component`/`log_source`）在 authority 日志中**全部缺失** | 前提：`CommentAuthorityEndpointConfig.java:42` `@ConditionalOnProperty(... enabled=true)` ⇒ **默认关闭、当前未在运行** ⇒ 打开才生效 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（仅日志面） | **否** | **C**（阶段二） | `AuthorityHandlerFactory.register(...)` 增 `Logger` 形参 ⇒ 装配处传 `ProcessLogger.newProcessLogger(...)` 的返回值 ⇒ 或**删掉** `AuthorityHandler.java:95-97` 的隐式 fallback，强制显式传 logger（编译器兜底）。测试：开启 `comment.authority.enabled=true` ⇒ 断言 authority 日志**含 6 个 metadata 键**且 `log_source=application` | E2 |
| **CF-003** | `authority_http.go:71-72`（`json.NewEncoder(w).Encode(fact)`）＋ Go 标准库 `encoding/json/stream.go`（`NewEncoder` ⇒ `escapeHTML: true`；`Encode` ⇒ `WriteByte('\n')`） | Go：响应体**带尾 `\n`** ＋ **HTML 转义默认开**（`&`→`\u0026`、`<`→`\u003c`） | `AuthorityHandler.java:217` `MAPPER.writeValueAsBytes(fact)` ⇒ **无尾 `\n`**；Jackson 默认**不**做 HTML 转义 | 字节不同 | ⚠️ **对端不在仓内**：Go 全仓只有服务端 + 自带测试，**无任何客户端**；真实对端是第三个仓库（`COMMUNITY_FACTS_ACCEPTANCE.md:43` 指向 BTW）⇒ **「是否被消费」不可证**。且尾 `\n` 对任何 JSON 解析器无害 ⇒ 最可能的对端形态下**影响为零** | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 ❌ | **否** | **C**（阶段二 · 且**先要拿到对端契约**） | ① 先向 BTW 侧确认是否逐字节比对；② 若是 ⇒ `AuthorityHandler.java:217` 后补 `'\n'`（低成本）＋ 给 Jackson 注册自定义 `CharacterEscapes`（**Jackson 无内建开关**）。测试：含 `<`/`&` 的 payload ⇒ 双侧响应体**逐字节**比对 | E2（对端契约**未取** ⇒ 剩余缺口在此） |
| **CF-004** | `wire.go:47`（`Payload json.RawMessage`）＋ 标准库 `stream.go`（`RawMessage.MarshalJSON` **原样返回**）＋ 消费点 `comment/rpc/internal/model/fact_delivery.go:129-134` ＋ `fact_authority.go:23` | Go：**逐字输出**原始 payload 字节（键序/数字字面量不变） | `Wire.java:129` `public Object payload` ⇒ Jackson 按**运行期类型**重序列化。实测两域填法**不同**：comment-rpc = `LinkedHashMap`（`CommentDeliveryValidator.java:140-148`）；favorite-rpc = **`JsonNode`**（`FavoriteDeliveryValidator.java:84-85`，该类 javadoc **自述**了根因） | 台账首版说「键序全丢/数字字面量全丢/重复键全丢」⇒ **前两条被推翻**（键序两侧都来自 PG `jsonb` 文本、已被 PG 规范化；整数精确、小数归一）⇒ 真实差异只剩「**favorite 已规避、comment 未规避**」 | ✅ **本轮已可定级**（首版记「不在 common 包内可判定」是错的）：差异**全部落在 `Wire.java:129` 一行** ⇒ **落点 100% 在本层** | 业务正确性 ❌ / 安全 ❌ / 数据一致性 🟡（弱：消费方若对 `event.payload` **单独算 hash/比对**，两侧不同）/ 可靠性 ❌ | **否** | **C**（阶段二 · 但**修法一行且已验证过路径**） | `Wire.java:129` 的 `Object` ⇒ `com.fasterxml.jackson.databind.JsonNode`（favorite 已验证的路径）⇒ 同步检查 `CommentDeliveryValidator.java:265/321` 的 `writeValueAsString(event.payload)`（对 `JsonNode` 仍成立）。测试：同一 payload 经 authority 往返 ⇒ 断言**字节相等**（favorite 已有的 JsonNode 路径作对照） | E2 |
| **CF-005** | `process.go:28-30`（`context.WithTimeout(attempt, 10*time.Second)` ⇒ deadline **随 ctx 进入业务**）＋ `wire.go:97`（`http.NewRequestWithContext(ctx,…)`）＋ `comment/rpc/cmd/fact-dispatch/main.go:73`（`http.Client{Timeout: 8 * time.Second}`） | Go：ctx deadline ⇒ **HTTP 随 ctx 取消**；DB 侧 `m.conn.WithContext(ctx)` ⇒ 查询随 ctx 取消；另有 **8s 客户端总超时**双保险 | `Dispatcher.java:198` `future.get(10, SECONDS)` → `:203` `future.cancel(true)` ⇒ **只发线程 interrupt，无取消传播**；线程池 `Dispatcher.java:172-176` = `newSingleThreadExecutor` ⇒ **core=max=1、无界队列**；被中断线程卡在 **JDBC**（`CommentFactDeliveryService.java:118`）**不响应 interrupt** | ⚠️ **台账首版「一次挂死永久卡死」过强**（循环本身没冻结，`stopSignal.get(1s)` 仍可唤醒）⇒ 准确表述：**一次不可中断的 JDBC 挂死 ⇒ 投递链路永久停滞**（进程与循环仍活）；卡在 HTTP 时 interrupt **能**打断（只丢一次投递） | 影响**投递**而非存储 ⇒ 事件仍在 outbox 未丢 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 🟡（事件永久停在 outbox 永不送达 ⇒ 但**不丢**）/ 可靠性 🔴（链路永久停滞） | **否**（不命中 P0 #3「数据丢失」—— outbox 里数据还在） | **C**（阶段二） | ① `Wire.java:218-223` 给 `HttpRequest` 加 `.timeout(Duration.ofSeconds(8))` 对齐 Go 的 `http.Client{Timeout: 8s}`；② `Dispatcher.java:172-176` 改带界队列 + 多 worker（或让 `dispatchFactOnce` 自带可取消 deadline）；③ `CommentFactDeliveryService.java:118` 之前检查 `Thread.interrupted()`。测试：注入一个挂死的 JDBC ⇒ 断言**下一轮仍能投递**（双侧同场景对照） | E2 |
| **CF-006** | `process.go:41-42`（`ticker := time.NewTicker(time.Second)`，**循环外**创建 ⇒ 绝对 1s 栅格）＋ `:56-60`（`select` 取 tick ⇒ 若本批耗时 ≥1s 则**立即**进下一批） | Go 周期 = `max(1s, 批次耗时)` | `Dispatcher.java:278-289` ⇒ `stopSignal.get(1, SECONDS)` ⇒ **每批结束后重新计时满 1s** | RAG 周期 = `批次耗时 + 1s`（**稳态吞吐两侧差一个量级**） | 只影响**延迟**，outbox 未清空前**不丢**事件 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（吞吐） | **否** | **C**（阶段二） | `Dispatcher.java:278-289` ⇒ `ScheduledExecutorService.scheduleAtFixedRate`（**固定频率** = 对应 Go ticker 的绝对栅格；⚠️ **不是** `scheduleWithFixedDelay`）。测试：投递 N 批 ⇒ 断言**总耗时**两侧落在同量级 | E2 |
| **CF-008** | `authority_http.go:30-34`（`ctx = otel.Tracer(...).Start(ctx, …)` ⇒ `r = r.WithContext(ctx)`）＋ `:37`/`:63`/`:74`（三条日志**全带 ctx**）＋ `logging.go:75-81`（`traceLogHandler.Handle` ⇒ span 有效则加 trace 三键） | Go：三条日志**都**带 `trace_id`/`span_id`/`trace_sampled` | `AuthorityHandler.java:126-132` 建 span 并 `ctx = ctx.with(span)` ⇒ **从不 `makeCurrent()`**（该文件零 `makeCurrent`）⇒ 读侧 `ProcessLogger.java:203-211` 读**线程局部** `Context.current()` ⇒ **恒 invalid** | 三条日志**通常无 `trace_id`**（MDC 兜底也救不了：全仓 `MDC.put` 只在 `common/logger/Logger.java:271/334/421`，authority 路径不经过） | 可观测性语义（不影响返回值/持久化） | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（排障能力） | **否** | **C**（阶段二） | `AuthorityHandler.java:132` 之后用 `try (Scope scope = ctx.makeCurrent()) { … }` 包住整个 handler 体（`span.end()` 留 `finally`）；或在日志点显式 `addKeyValue("trace_id", …)`。测试：请求 authority ⇒ 断言其日志**含 `trace_id`/`span_id`** 且与该请求的 span **同值** | E2 |
| **CF-009** | `process.go:64-69`（`deliveryErrorCode` ⇒ `errors.Is(err, ErrFrozenEnvelopeBlocked)`）＋ `wire.go:35`（哨兵 `ErrFrozenEnvelopeBlocked`） | Go：`errors.Is` 对 `*errors.errorString` 目标走**指针身份**比较 ⇒ **只认那一个哨兵实例** | `Dispatcher.java:368-382` ⇒ 按**类型**判（`t instanceof Wire.FrozenEnvelopeBlockedException`） | ❌ **台账首版主张「`error_code` 取值范围更宽」被证伪** —— 两侧 `deliveryErrorCode` **各只有 2 个返回值**（`INVALID_FROZEN_ENVELOPE` / `DELIVERY_ATTEMPT_FAILED`）⇒ **取值集合完全相同**（台账说的「哨兵 A/B/C 三个」在 error_code 语境下不成立：`wire.go:36` 的 `ErrAuthorityUnavailable` 从不参与 error_code 计算）。✅ **真实差异更窄**：Go 认**实例**、RAG 认**类型** ⇒ 任意文案的新实例也会被判成 `INVALID_FROZEN_ENVELOPE`（当前**不可达**：该类型唯一构造点是 `Wire.java:91-92` 的单例，三域只在 BLOCKED 分支抛它） | 潜在差异，当前不可达 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 ❌ | **否** | **D**（经分析：当前不可达 ⇒ 不实现）＋ 🔴 **但必须改一处 javadoc** | ① 🔴 **`Dispatcher.java:365-366` 的「与 Go 的 `errors.Is(err, ErrFrozenEnvelopeBlocked)` **同构**」断言不成立**（实例判 ≠ 类型判）⇒ 属 AGENTS.md §3.3.2 高危措辞 ⇒ **本轮已订正**（见 §十三·三）；② 若将来要真对齐 ⇒ `Dispatcher.java:374` 改实例判 `t == Wire.ERR_FROZEN_ENVELOPE_BLOCKED`。测试：构造一个**不同文案**的 `FrozenEnvelopeBlockedException` ⇒ 断言 `deliveryErrorCode` 返回 `DELIVERY_ATTEMPT_FAILED` | E2 |
| **CF-011** | Go 标准库 `log/slog/handler.go` 的 `commonHandler.handle` ＋ `appendNonBuiltIns` ＋ `logging.go:52-57`（`ReplaceAttr` 改名）＋ `:61-65`（`WithAttrs` 注入 6 个 metadata 键）＋ `:78-79`（`record.AddAttrs` **追加** trace 三键） | Go 键序 = `timestamp, level, message, service, environment, service_version, instance_id, component, log_source, <业务KV>, trace_id, span_id, trace_sampled` | `ProcessLogger.java:244-269` ⇒ `:246-251`（metadata 六键）→ `:253-255`（timestamp/level/message）→ `:257`（trace 三键）→ `:267-269`（业务 KV） | RAG 键序 = `service, …, log_source, timestamp, level, message, trace_id, span_id, trace_sampled, <业务KV>` ⇒ **metadata 块前移** ＋ **trace 块前移到 KV 之前** | ⚠️ **JSON 对象键序无语义**；且 Go 侧验收**只断言键存在不断言顺序**（`service/common/communityfact/authority_http_test.go:33-36`、`:64-67` 用 `strings.Contains`）⇒ 现有消费者不受影响 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 ❌ | **否** | **D**（经分析确认不实现 —— JSON 键序无语义，且 Go 侧无顺序断言 ⇒ 对齐它**不产生任何可观察收益**） | 若将来要对齐：`ProcessLogger.java:244-269` 重排为 `timestamp, level, message` → 6 个 metadata → 业务 KV → trace 三键（trace 三键必须移到 `:267-269` 之后）。测试：**先补 Go 侧的顺序断言**（当前没有 ⇒ 无从对拍） | E2 |
| **CF-012** | `logging.go:53`（`attr.Value.Time().UTC().Format("2006-01-02T15:04:05.999999999Z07:00")` ⇒ **纳秒**、最多 9 位、尾零裁掉、UTC 输出 `Z`） | Go：**纳秒**精度时间戳字符串 | `ProcessLogger.java:253`（`formatTimestamp(event.getTimeStamp())`）＋ `:85-98` ⇒ 入参 `ILoggingEvent#getTimeStamp()` 是**毫秒** ⇒ 最多 3 位有效数字（裁尾零语义与 UTC 输出一致） | 时间戳精度 纳秒 vs 毫秒 | 无下游按纳秒解析的证据 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（排障粒度） | **否** | **C**（阶段二） | 改用 logback 的 `event.getInstant()`（纳秒精度）替代 `getTimeStamp()`，`formatTimestamp` 入参改 `Instant`。测试：断言时间戳字符串**含 9 位小数位**（Go 同款） | E2 |
| **CF-013** | `authority_http.go:24` ＋ `wire.go:87`（`len(token) < 32` ⇒ Go 的 `len` 是 **UTF-8 字节数**） | Go 按 **UTF-8 字节数**判 token 长度 | `AuthorityHandler.java:87` ＋ `Wire.java:191` ⇒ `token.length() < 32` ⇒ **UTF-16 码元** | UTF-16 码元数 ≤ UTF-8 字节数 ⇒ **Go 更早放行、RAG 更早拒绝**（例：31 个 BMP 非 ASCII 字符 ⇒ Go 62 字节**收**、RAG 31 码元**拒**）；ASCII token 两侧完全等价 | ⚠️ **分歧方向是「RAG 更严」** ⇒ 不会放行弱 token ⇒ **不是安全缺口**；且 `AuthorityHandler.java:113` 已把 token 存成 UTF-8 字节 ⇒ 改法极低成本 | 业务正确性 ❌ / 安全 🟡（更严的一侧 ⇒ **不构成缺口**）/ 数据一致性 ❌ / 可靠性 ❌ | **否** | **C**（阶段二 · 或 **D**） | `AuthorityHandler.java:87` ＋ `Wire.java:191` 改按 UTF-8 **字节数**判（用 `tokenBytes.length`）。⚠️ **对齐方向是「放宽」** ⇒ 属安全侧的取舍 ⇒ **建议按 §10 走「有意偏离」或经裁决再改**，不擅自放宽。测试：31 个非 ASCII 字符的 token ⇒ 断言两侧**一致放行** | E2 |
| **CF-014** | `wire.go:84-89`（`url.Parse(endpoint)` + 一串条件 ⇒ **任何畸形 endpoint 都归一为同一句文案** `"community DataCenter event sender is not configured"`） | Go：**统一文案**（且 `url.Parse` 本身宽松 ⇒ 多数畸形输入不报错而是落到后续条件分支） | `Wire.java:194` `URI.create(endpoint)` ⇒ **畸形输入直接抛 `IllegalArgumentException`**（如 `"not a url"` ⇒ `Expected scheme name at index 0`）⇒ **绕过** `:196-203` 的统一文案分支 | 错误**形态 + 文案**不同（调用方 `CommentFactDispatchScheduler.java:97-104` / `FavoriteFactDispatchScheduler.java:97-104` 都 catch `RuntimeException` ⇒ 两侧都不致命） | 两侧都是「拒绝启动投递」⇒ 不构成越权 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（排障可读性） | **否** | **C**（阶段二） | `Wire.java:194` 包 try/catch ⇒ 抛 `IllegalArgumentException("community DataCenter event sender is not configured", e)`（统一文案，保留 cause）。测试：畸形 endpoint ⇒ 断言**错误文案逐字等于** Go 侧那句 | E2 |
| **CF-015** | `wire.go:114-117`（`fmt.Errorf("decode DataCenter event receipt: %w", err)` ⇒ 固定前缀 **且** 保留 cause 链） | Go：**固定前缀** + `%w` 保留 cause | `Wire.java:238-241` ⇒ 直接抛 Jackson 的**原始异常** | 既无前缀也无包装 ⇒ **丢失语义层**（无法与其他 IOException 区分） | 只影响排障可读性 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（排障） | **否** | **C**（阶段二） | `Wire.java:240` 包一层 `catch (IOException e) { throw new IOException("decode DataCenter event receipt: " + e.getMessage(), e); }`。测试：喂一个非法 receipt ⇒ 断言**异常消息以该前缀开头**且 cause 非空 | E2 |
| **CF-017** | `logging.go:104-114`（`InstallFrameworkLogger` ⇒ `logx.SetWriter(frameworkLogWriter{…})`）＋ `:118-135`（键改名 `trace`→`trace_id`/`span`→`span_id`/`caller`→`code_location`，并跳过与信封重复的键）＋ 验收口径 `service/comment/rpc/COMMUNITY_FACTS_ACCEPTANCE.md:45` | Go：**框架日志（含 signal 关闭日志）进入同一个 OBS-r3 信封**，`log_source="framework"` | `ProcessLogger.java:251` `json.put("log_source", "application")` ⇒ **硬编码** ⇒ 永远只有 `"application"`；无 `code_location` 映射；无「框架日志并入信封」的通路。`ProcessLogger.java:46-47` 的 javadoc 明写这是**刻意删除**（「Java 端没有 go-zero logx 等价物」） | 信封覆盖面差异（Go 的验收明确要求框架日志同信封，RAG 不满足） | Java 侧无 go-zero logx 等价物 ⇒ 需另建通路（Spring Boot 的 `LoggingSystem` ⇒ 接到同一 appender） | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（观测契约） | **否** | **C**（阶段二） | 给 `ProcessLogAppender` 增加 `log_source` 可配项 ＋ 一个 `frameworkLogger(…)` 入口 ⇒ 把 Spring Boot 的 `LoggingSystem`（启动/关闭日志）接到同一 appender；键改名按 Go 的 `trace/span/caller` → `trace_id/span_id/code_location` 对齐。测试：触发一次优雅关闭 ⇒ 断言该日志**含 `log_source=framework`** 且在同一信封内 | E2 |
| **CF-018** | — （**非 Go 语义差异**，是 RAG 侧 API 缺陷） | Go：`process.go:20-62` `RunDispatcher(ctx, …, once bool)` 是**阻塞式**，靠 `ctx.Done()` 退出 | `Dispatcher.java:222` 建 handle → `:224-226` **只有 `runAsync` 才发布** → `:253`（once 分支**确实**返回 handle）→ `:291`（`once=false` 时**循环退出后**才返回）⇒ ⇒ **阻塞版 `run(…, once=false)` 的调用方永远拿不到 handle** ⇒ `@PreDestroy` 无法 `stop()` <br><br>⚠️ **2026-10-30 复核（global+common lane）**：**本行原引的行号已随源码演进过期**（原值保留）—— 现码落点 = `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:338`（`DispatcherHandle handle = callerHandle != null ? callerHandle : DispatcherHandle.create();` ⇒ **建句柄**）＋ `:343-347`（`stopSignal` 绑定 ＋ `handlePublisher.accept(handle)` ⇒ **句柄在进入循环之前发布**）＋ `:374`（`once` 分支 `return handle;` ⇒ **确实**返回）＋ `:399-404`（1s 栅格）＋ `:412`（循环退出后 `return handle;`）⇒ ⇒ 「**`once=false` 的阻塞版拿不到句柄**」这一**真实**结论**仍成立**，而原句里「once 分支直接 return ⇒ 拿不到 handle」**对现码不成立**（`:374` 的 once 分支**确实**返回 handle）· Go 侧 `service/common/communityfact/process.go:20` ＋ `:56-60` · 核对日期 **2026-10-30** | ⚠️ **台账首版两处不准**：① 「once 分支直接 return ⇒ 拿不到 handle」**对现码不成立**（`:253` 的 once 分支**确实**返回）；② 真实成立的是 **`once=false` 的阻塞版**拿不到 handle。🔴 **有活的触发点**：`favorite-rpc/.../fact/FavoriteFactDispatchScheduler.java:119-124` 调的正是阻塞版 `Dispatcher.run(ctx, …, config.isOnce(), false)`，启动点 `:77-78` `@EventListener(ApplicationReadyEvent.class)` ⇒ 开启 `favorite.fact.dispatch.enabled=true` 后：**Dispatcher.run 在主线程永久阻塞**、`this.handle` 永不赋值 ⇒ `:134-141` 的 `@PreDestroy stop()` 拿到 null ⇒ **停不下来**、主线程不返回 ⇒ **进程关不掉**（对照：comment-rpc 走 `runAsync`，`:113-119` 的 `@PreDestroy` 有效）<br>🟢 **2026-10-29 订正（半修口径）**：**共享层半边已修**（Go-faithful：停止能力归调用方 —— `Dispatcher.java:90/136/139` 的 javadoc 明写「DRIFT-CF-018（2026-10-29 修）」＋ 阻塞重载标 `@deprecated` ＋ 提供「调用方先造句柄」的重载）＋ 守门 `DispatcherHandleLifecycleGateTest` 在位；🔴 **但 `favorite` 域半边仍成立** —— `FavoriteFactDispatchScheduler.java:119` **仍调阻塞版 `Dispatcher.run(...)`** ⇒ `once=false` 时句柄永远拿不到 ⇒ `@PreDestroy` 拿到 null ⇒ **进程仍无法优雅关闭** ⇒ **落点 G3 ⇒ 派单 R5·F-7**| **不是** Go 语义差异（⇒ 是 RAG 侧 API 缺陷）⇒ 但**命中 P0 #3 的可靠性面**（进程无法优雅关闭）⇒ **不暂缓** ⇒ **共享层半边 2026-10-29 已修**；**favorite 域半边待修** | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🔴（**进程无法优雅关闭**、主线程被占） | **否**（P0 #1「核心业务链路能够真实运行」仍成立 —— 常态运行不受影响）⇒ ⇒ **不计入阶段一 ⑥**（§1.1 ⑥ 第 4 条）⇒ 但**它是一条真缺陷** ⇒ 不进「暂缓」而进 **R5 派单** | ✅ **已修一半**（共享层，2026-10-29）＋ 🔴 **余 favorite 域半边（G3）⇒ 派单 R5·F-7** | ① ✅ **共享层已修**（`Dispatcher.run(once=false)` 交不出句柄 ⇒ 改为**调用方先造句柄**（Go-faithful：停止能力归调用方）＋ 阻塞重载标 `@deprecated` ＋ 提供 `runAsync` 路径）；② 🔴 **favorite 域仍待修**：`FavoriteFactDispatchScheduler.java:119` ⇒ 改用 `Dispatcher.runAsync(…)`（与 comment-rpc 的 `CommentFactDispatchScheduler.java:93` 对齐）⇒ 落点 G3。测试：开启 `favorite.fact.dispatch.enabled=true` ⇒ 发起优雅关闭 ⇒ 断言**进程在 N 秒内退出**且 `@PreDestroy` 拿到非 null handle | **E4**（共享层：守门 `DispatcherHandleLifecycleGateTest` ＋ 变异验证 3 组精确转红）<br>⚠️ **favorite 域半边仍为 E2** |

#### `logger` 族（7 条）

| # | Go 出处（`文件:行号`） | 原始行为 | 阶段一当前行为 | 具体差异 | 暂缓理由 | 影响 | 阻断 | 归属 | 阶段二恢复方案 + 测试与通过标准 | 证据 |
|---|---|---|---|---|---|---|---|---|---|---|
| **LOG-001** | `logger.go:148-157`（构造 `fields`：service/event_id/trace_id/error_code/file_line/call_path/call_chain/error_reason）＋ `:160-165`（可选 user_id/article_id）＋ `:168` `logx.WithContext(ctx).Errorw("business_error", fields…)` ⇒ go-zero 默认 JSON 编码把这些字段写进日志行（`core/logx/logs.go:296-301` → `core/logx/writer.go:468` `writeJson`） | Go：KV 字段**进日志行** | `Logger.java:284-289` 用 `at.addKeyValue(...)` ⇒ KV 挂在 `ILoggingEvent.getKeyValuePairs()` 上（**数据到达了 logback 事件**）⇒ 但**全仓无任何 pattern**：`glob **/logback*.xml` = 0 命中、20 个配置文件的 `logging:` 块**只有 `level:`** ⇒ 且 **Boot 4.0.8 上游 `defaults.xml` 的 `CONSOLE_LOG_PATTERN` 无 `%kvp`、无 `%X{}`** ⇒ ⇒ **KV 与 MDC `trace_id` 在生产 stdout/file 里都不渲染** | ⚠️ **措辞订正**：不是「数据没进日志框架」，而是「**数据到了 logback 事件，却没被 pattern 渲染出来**」 | 可观测性 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（排障能力） | **否** | **C**（阶段二） | 共享层 `service/common/src/main/resources/` 加 `logback-spring.xml`（含 `%kvp` ＋ `%X{trace_id}`）⇒ ⚠️ **但 Boot 的 `logback-spring.xml` 加载规则是「类路径根目录」，放在 jar 内**不保证**被各 api 采用** ⇒ ⇒ 落地形态需先验证（**真启动**守门：起一个真应用 ⇒ 断言 stdout 含 `call_chain=`）。⚠️ **纯单测用 `ListAppender` 断言 `getKeyValuePairs()` 测不到**（单测环境恰恰是能看见 KV 的环境） | E2（含 Boot 4.0.8 上游一手） |
| **LOG-002** | `logger.go:139`（`getCallChain(3)`）＋ `:320-323`（`runtime.Callers(skip, pcs)`）＋ Go 标准库 `runtime/extern.go:326-328`（skip 语义：**0 = `runtime.Callers` 自己，1 = 它的调用者**） | Go 帧序：0=`runtime.Callers`、1=`getCallChain`、2=`(*Logger).logBusinessErr`、3=**包级 `LogBusinessErr`（包装帧）**、4=业务调用方 ⇒ `skip=3` ⇒ **包装帧在链里**（反转后落尾部）⇒ ⚠️ **`logger.go:139` 自己的注释是错的**（skip=3 只跳过前三层，既没跳包级 `LogBusinessErr`，也没跳业务调用方） | `Logger.java:263` `getCallChain(3)`；`Logger.java:499-520` 的 `StackWalker.walk` 流首帧 = 调用 `walk` 的方法（即 `getCallChain`）⇒ 帧序：0=`getCallChain`、1=`doLogBusinessErr`、2=`Logger.logBusinessErr`、3=业务调用方 ⇒ `Logger.java:506` `if (count++ < skip) continue;` ⇒ skip=3 ⇒ **首帧 = 业务调用方，包装帧被跳过** | `call_chain` **尾部少一帧** | ⚠️ **对照组不要误列**：`file_line`/`call_path` 两侧**等价**（Go `runtime.Caller(2)` ≡ RAG `Logger.java:249-252` `s.skip(2)`）；`logInfo` 两侧都无 `call_chain` | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（日志字段内容） | **否** | **C**（阶段二） | `Logger.java:263` 与 `:413` 的 `getCallChain(3)` ⇒ `getCallChain(2)`（Go skip=3 ≡ RAG skip=2，两侧 helper 帧数差 1）。⚠️ **只改数字不够**：RAG 的 `FILTER_PREFIXES`（`Logger.java:148-149`）含 `com.rag.common.logger.` 与 `com.rag.common.spring.` ⇒ 会把那一帧**再过滤掉**；而 Go 的过滤表（`logger.go:53-58`：`runtime.`/`testing.`/`github.com/zeromicro/go-zero/`）**不匹配** `logger.LogBusinessErr` 这个短名 ⇒ Go 保留、RAG 丢 ⇒ ⇒ **完整修法 = 改 skip 且让「日志入口帧」不被过滤**。测试：断言 `call_chain` 的**尾部帧**等于 `logger.LogBusinessErr`（Go 侧同款断言） | E2 |
| **LOG-003** | `logger.go:63-70`（`Init(svcName)`：`sync.Once` 内校验非空、设 `serviceName`）⇒ **范围内 18 处**生产调用点 `logger.Init(c.Name)`（不是台账写的 15 处）；⚠️ **`hot/rpc/hot.go:27-31` 与 `task/rpc/task.go:23-27` 两个 main 完全不调**（两者只 `conf.MustLoad` 后直接起服务）⇒ ⇒ 这两个服务若调 `logger.LogBusinessErr`，**Go 侧自己会在 `logger.go:100-102` panic**（Go 侧自身缺口，RAG 不必对齐） | Go：静态入口**生产在用**，服务名来自 yaml 顶层 `Name:`（`core/service/serviceconf.go:32-33`） | ① `Logger.java:171-181` 的 `init` **生产零调用方**（全仓 grep：命中只有测试 `LoggerTest.java` 与 javadoc/注释）；② 生产真路径 = **Bean 路径**：`LoggerFactoryBean.java:75-83` 构造注入 `spring.application.name` ⇒ **不碰** `Logger.GLOBAL` ⇒ 三个静态方法 `Logger.java:222-224`/`:300-302`/`:377-379` 在生产**从未被调用** ⇒ **「必抛 `IllegalStateException`」这个推论不成立**（没人调 ⇒ 不会抛）；③ **服务名取值体系不同** | ⚠️ **台账首版两处需改写**：① 「必抛」过强 ⇒ 真实形态是「**静态入口从未被生产代码使用**」（潜在可用性缺陷）+「**服务名取值体系不同**」两件事；② 「15 处」应为 **18 处**且有 2 个 main 不调。🔴 **实测 20 模块对照表：4 个一致、16 个不一致**（RAG 的 `spring.application.name` 多数等于 Go 的 `Telemetry.Name`，而 Go logger 实际用的是 `Name:`）⇒ **确是两套取值体系**，不是个别笔误。⚠️ 另有 **5 处配置注释**声称「Logger.java 已显式调 `Logger.init("xxx")`」（favorite-api `application.yml:189` · user-api `:187` · admin-rpc `:147` · admin-api `:180` · user-rpc `application.yaml:169`）—— **与实现不符**（§3.3.2 高危断言） | **不属阶段一必对齐项**（P0 九条**无一条命中** —— 静态入口**生产零调用** ⇒ 常态运行不受影响）⇒ 归 **C 类**阶段二；⚠️ 其中「§3.3.2 要求的**失效注释清理**」属**零风险的正确性卫生**，可与阶段二同批做 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（静态入口一旦被用 ⇒ 异常会落在「本该永不失败的日志调用」上 ⇒ 可能把 200 变 500 —— `ArticleAuthorResolver.java:58-71` 的 javadoc 记录的正是这个坑） | **否** | **C**（阶段二） | ① **先删/改那 5 处失效注释**（§3.3.2）；② 静态入口要么在生产 `main` 里补 `Logger.init(spring.application.name)`（对应 Go 的 `logger.Init(c.Name)`），要么标注「仅测试用」并从公共 API 收口；③ **服务名取值体系**对齐 = **逐模块**把 `spring.application.name` 改成 Go 的 `Name:` 值（**属各域写权**，不在本层）⇒ 需按域派单。测试：每个模块断言其 `service` 字段 == Go 的 `c.Name`（**20 模块对照表**） | E2 |
| **LOG-004** | `logger.go:63-70`（panic 在 `once.Do` 内）＋ Go 标准库 `sync/once.go:50-51,77`（「If f panics, Do considers it to have returned; future calls of Do return without calling f」） | Go：`Init` 失败 ⇒ **Once 被毒化** ⇒ 后续 `Init(合法名)` 是 no-op ⇒ `serviceName` 永远为 `""` ⇒ 之后每次打日志都在 `logger.go:100-102`/`:174-176`/`:230-232` **panic** | `Logger.java:171-181` ⇒ 抛异常时 `GLOBAL.initialized = true` 那行**不执行** ⇒ `initialized` 仍为 `false` ⇒ 后续 `init(合法名)` **可成功**（可重试） | 失败后的**可恢复性相反** | ⚠️ **两侧 `Init` 在生产都未被触发**（RAG 零调用；Go 传的都是 `c.Name`，非空）⇒ 影响为零 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 ❌ | **否** | **D**（经分析确认不实现 —— RAG 侧是**更可恢复**的一侧，且生产不触发 ⇒ 对齐 Go 反而更危险） | 若将来要对齐 ⇒ `Logger.java:171-181` 用一个「失败也置位」的 `AtomicBoolean` 模拟 `sync.Once` 毒化。⚠️ **对齐方向是「更严」⇒ 属决策项，不是缺陷**。测试：`init("")` 抛 ⇒ 再 `init("ok")` ⇒ 断言**行为符合选定语义**（当前测试 `LoggerTest.java:36-42`/`:44-53` **都没覆盖「失败后再试」这条序列** ⇒ 属 E3 缺口） | E2 |
| **LOG-005** | go-zero v1.10.3 `core/logx/richlogger.go:210`（`caller`）＋ `:217-220`（`trace`）＋ `:222-225`（`span`）（均非空才加）；键名 `core/logx/vars.go:56,60,62`（`caller`/`span`/`trace`）＋ `core/logx/logs.go:17`（`callerDepth = 4`）；**同一性**：`core/trace/utils.go:22` ⇒ `trace` 与 logger 的 `trace_id` **同值** | Go 自动附加 `caller` / `trace` / `span` | `Logger.java:274-281`（`doLogBusinessErr`）/`:335-341`（`doLogInfo`）/`:424-430` 的字段集 = service/event_id/trace_id/error_code/file_line/call_path/call_chain/error_reason（+可选 user_id/article_id）⇒ **无 `caller`、无 `trace`、无 `span`**；MDC 只放 `trace_id`（`:271`/`:334`），而 Boot 4.0.8 默认 pattern 无 `%X{}` ⇒ 连 MDC 也不渲染 | ⚠️ **「Go 自动附加 trace」这半句要收窄** —— `trace` 与 logger 自带的 `trace_id` **同值** ⇒ 真正**只存在于 Go** 的是 **`caller`** 与 **`span`** ⇒ 净损失 = `caller` + `span` | 可观测性字段缺失 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡 | **否** | **C**（阶段二） | ① 在 `Logger.java:274-281`/`:424-430`/`:335-341` 补 `caller`（Go 的 `caller` = 发出日志的文件:行 ⇒ 可用 `StackWalker` 已有帧推出）与 `span`（`SpanContext.getSpanId()` —— `Logger.java:565-575` 已在取 span context）；② 若补 `trace` 字段，注意与 `trace_id` 同值，避免语义混淆；③ MDC 可见性随 **LOG-001** 的 pattern 修复一并解决。测试：断言日志**含 `caller` 与 `span`** 且 `span` == 该请求的 span id | E2 |
| **LOG-006** | `service/like/rpc/etc/like.yaml:8-11`（`Log: {ServiceName, Mode: file, Path: ../../../log/like-rpc}`）+ 消费链 `core/service/serviceconf.go:34` → `core/logx/config.go:12`（`Mode` 默认 console）→ `core/logx/logs.go:303-310`（`case fileMode: setupWithFiles`）→ `:528` → `core/logx/writer.go:178` `newFileWriter` → `logs.go:426-438` `createOutput` ⇒ `DefaultRotateRule`/`NewSizeLimitRotateRule`（带 `keepDays`/`gzip`/`maxSize`/`maxBackups`；`logs.go:196-206`），`Rotation` 默认 `daily` ⇒ **落盘 + 滚动**。同款配置另见 `article/rpc/etc/article.yaml:18-19`、`comment/rpc/etc/comment.yaml:13-14`、`follow/rpc/etc/follow.yaml:11-12`、`message/rpc/etc/message.yaml:10-11`、`favorite/api/etc/favorite.yaml:7-8`、`hot/api/etc/hotcenter.yaml:7-8` | Go：**落盘 + 滚动** | 20 个模块配置里**无 `logging.file.name`/`logging.file.path`、无文件 appender、无 logback 配置** ⇒ 只有 Boot 默认 console appender ⇒ **只写 stdout**（compose 里也没有 `logging:` driver 配置 ⇒ 落盘若要发生只能靠宿主采集 stdout） | 部署形态差异 | **运维面差异** ⇒ P0 九条**无一条命中**（不影响业务正确性 / 安全 / 数据一致性）⇒ 归 **C 类**阶段二；⚠️ 共享层 `logback-spring.xml` 的落盘改动会**改变全部 10 个 api + 10 个 rpc 的日志行为** ⇒ 跨全仓 ⇒ 本轮不做 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡（运维面） | **否** | **C**（阶段二） | 共享层 `service/common/src/main/resources/logback-spring.xml` 加 `RollingFileAppender`（`SizeAndTimeBasedRollingPolicy`，对齐 Go 的 daily + keepDays + gzip），路径映射 Go 的 `Log.Path`（如 `../../../log/like-rpc` ⇒ `<LOG_DIR>/like-rpc`）⇒ ⚠️ **落点 100% 在本层，但影响 20 个模块** ⇒ 按 §5.3e 走共享层反馈单。测试：真启动 ⇒ 断言日志文件**被创建且按大小滚动** | E2 |
| **LOG-007** | `logger.go:118-125`（`errorReason := "unknown error"; if err != nil { errorReason = err.Error(); … }`） | Go 恒取 `Error()` ⇒ 空 message 的 error 得 **`""`**（Go 的 `error` 接口没有「nil message」概念） | `Logger.java:243`（`doLogBusinessErr`）＋ `:398`（`doLogFatal`）⇒ `err.getMessage() != null ? err.getMessage() : err.toString()` ⇒ `new RuntimeException()`（`getMessage()==null`）⇒ 得 **`"java.lang.RuntimeException"`**（类全名） | ⚠️ **边界比台账窄**：分歧**只存在于 message 为 `null` 的异常**；`new RuntimeException("")` ⇒ `getMessage()==""`（非 null）⇒ RAG 得 `""` ⇒ **与 Go 一致** | 日志字段内容 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 ❌ / 可靠性 🟡 | **否** | **C**（阶段二） | `Logger.java:243` 与 `:398` ⇒ `getMessage()` 为 null 时取 **`""`**（对齐 Go 的 `err.Error()` 语义），不再退 `toString()`。若确实想要类名，应作为**独立字段**（如 `error_type`）而非占用 `error_reason`。测试：喂 `new RuntimeException()`（null message）⇒ 断言 `error_reason` == **`""`**（⚠️ 现有 `LoggerTest.java:103-115` 只用 `new RuntimeException("boom")` ⇒ **从不覆盖 null-message 分支** ⇒ 现测不会红） | E2 |

#### `snowflake` 族（3 条）

| # | Go 出处（`文件:行号`） | 原始行为 | 阶段一当前行为 | 具体差异 | 暂缓理由 | 影响 | 阻断 | 归属 | 阶段二恢复方案 + 测试与通过标准 | 证据 |
|---|---|---|---|---|---|---|---|---|---|---|
| **SF-001** | `bwmarrin/snowflake@v0.3.0/snowflake.go:124`（`var curTime = time.Now()`）→ `:126`（`n.epoch = curTime.Add(time.Unix(Epoch/1000, (Epoch%1000)*1000000).Sub(curTime))`，库注释原文 *"to make sure we use the monotonic clock if available"*）→ `:139`（`now := time.Since(n.epoch).Nanoseconds() / 1000000`）⇒ 两个时间点都带单调读数 ⇒ `Time.Sub`（`$GOROOT/src/time/time.go:1194`）走 `subMono`（`:1210`）⇒ **纯单调** | Go：**对墙钟回拨免疫**（epoch 时刻被挂上单调读数） | 🔴 **修复前**：`Snowflake.java:242` `System.currentTimeMillis() - EPOCH` ⇒ **纯墙钟** ⇒ 回拨 ⇒ `now < lastTimestamp` ⇒ sequence 归零 ⇒ **同一毫秒产出完全相同的 ID** | **真实差异**（不是概率差异）：回拨一旦重合即产重复 ID | ⛔ **无暂缓理由** ⇒ **命中 `docs/PhasedGoal.md` P0 #3「重复处理」** ⇒ **本轮已落码修复**（见 §十三·二） | 业务正确性 🟡 / 安全 ❌ / **数据一致性 🔴（重复 ID）** / 可靠性 🔴 | **是** ⇒ **已修** | **A**（阶段一准入阻断项 ⇒ 已处理） | — （已修） | **E4**（守门 8 条 ＋ 变异 4/8 精确红） |
| **SF-002** | `snowflake.go:53-59`（`SNOWFLAKE_NODE_ID` 优先）＋ `:62-84`（`SERVICE_NAME` + PID ⇒ sha256 前 2 字节 ⇒ uint16） | Go：workerId 只有 **env** 与 **hash 兜底**两档 | `SnowflakeBean.java:59-78` 多出 `snowflake.worker-id` 配置档（且已确认**该档是活的**：`article-api` 扫 `com.rag.common`） | RAG **多出一个 Go 没有的配置档** | 「多一档」是**表达层扩展**，不是行为漂移 ⇒ ⚠️ 但它**是活的** ⇒ 配了就会改变 workerId ⇒ 需登记 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 🟡（workerId 选取口径不同）/ 可靠性 ❌ | **否** | **C**（阶段二 · 或按「有意偏离」签字） | 两条路：① 若认定「多一档运维便利」是有意扩展 ⇒ 走 `docs/Intentional_Deviations/` 签字登记；② 若要求严格同构 ⇒ 删掉该档（**⚠️ 会改变已依赖它的部署** ⇒ 需协调）。测试：断言**未配置时**两侧 workerId 选取路径**逐条一致**（env > hash） | E2 |
| **SF-003** | `snowflake.go:22-42`（`Init` 是 `sync.Once` 包裹 ⇒ **懒加载**于首次 `GetID`；`:41` `GetID()` 第一行调 `Init()`）＋ `:56`（`strconv.Atoi(idStr)` ⇒ **不 trim**） | Go：**懒加载** ＋ `SNOWFLAKE_NODE_ID` **空白容错 = 拒绝**（`Atoi(" 5 ")` 报错 ⇒ 落 hash 兜底） | ① RAG 在**容器 refresh 期**就 `setWorkerId`（`SnowflakeBean.java:34`）⇒ 初始化时机不同；② `Snowflake.java:285` `Long.parseLong(explicit.trim())` ⇒ **trim** ⇒ `SNOWFLAKE_NODE_ID=" 5 "` 时 RAG **接受**，Go **拒绝** | 初始化时机不同 ＋ 空白容错**相反** | ① 初始化时机：RAG 侧在 refresh 期解析 workerId，Go 侧在首次 `GetID` ⇒ **可观察的差异仅在「进程启动后、首次取 ID 前」的 workerId 可见性**（`currentWorkerId()`）；② 空白容错：仅当运维**手工把 env 设成带空格的值**时触发 ⇒ 现实中极罕见 | 业务正确性 ❌ / 安全 ❌ / 数据一致性 🟡（workerId 选取）/ 可靠性 ❌ | **否** | **C**（阶段二） | ② 去掉 `Snowflake.java:285` 的 `.trim()`（**一行**）即可与 Go 同构 ⇒ ⚠️ 但这会让「当前依赖 trim 的人」受影响 ⇒ 需先查全仓/部署是否真有人设带空格的值（**当前 grep 全仓无带空格的引用** ⇒ 风险极低）；① 初始化时机属**架构差异** ⇒ 若要对齐需改 `SnowflakeBean` 的装配时机（**跨 10 域** ⇒ 成本高）⇒ 建议**只登记**。测试：`SNOWFLAKE_NODE_ID=" 5 "` ⇒ 断言**落 hash 兜底**（与 Go 一致） | E2 |

### 十三·二、🔴 `DRIFT-SF-001` 的落码记录（`twoStage.md` §5.4 的 9 字段）

| 字段 | 内容 |
|---|---|
| **修改前的实现和行为** | `Snowflake.java:242` `long now = System.currentTimeMillis() - EPOCH;`（**纯墙钟**）＋ `:348`（自旋循环内**又读一次**墙钟）⇒ 墙钟倒退 ⇒ `now < lastTimestamp` ⇒ 落进 `else` 分支把 `sequence` 归零 ⇒ 若与回拨前某毫秒重合 ⇒ **同一毫秒产出完全相同的 ID**（重复 ID）。另：`Snowflake.java:231-234` 的 javadoc 写着「继承自 Go 的已知风险（时钟回拨）」—— **说反了**（Go 恰恰免疫回拨，「不免疫」是 RAG 纯墙钟实现独有的）。 |
| **具体问题** | 命中 `docs/PhasedGoal.md` **P0 #3**「关键数据能够正确持久化……**不存在已确认的严重数据丢失、重复处理或状态错误问题**」⇒ **重复 ID = 重复处理** ⇒ **必须修，不能只登记**（阶段一准入阻断项）。 |
| **修改后的实现** | ① `State` 新增两个锚点：`anchorEpochNanos`（**Node 创建时刻「距 epoch 的纳秒数」**）＋ `anchorMonotonicNanos`（Node 创建时刻的单调钟读数）⇒ 与 Go 的 `n.epoch` **语义同构**（Go 的 epoch 时刻被挂上单调读数）；② `State.nowMillisSinceEpoch()` = `(anchorEpochNanos + 单调流逝纳秒) / 1e6` ⇒ **距 epoch 时刻的毫秒数**（**不是**「距进程启动」）；③ `nextIdInternal` 与**自旋循环**两处**都**改用该方法（**原实现两处都读墙钟**）；④ `wallClockMillis` / `monotonicNanos` 两个**包级私有** `LongSupplier` 注入点（与同模块 `RpcInterceptor.DeadlineState` 既有约定同款）；⑤ 🔴 **静态初始化顺序**：`STATE` 是 `static final` ⇒ 类加载即构造 ⇒ 两个 supplier **必须声明在 `STATE` 之前**（否则构造时读到 `null` ⇒ NPE）—— 已按此排布。 |
| **修改理由** | 这是「**把 RAG 改成 Go 已有行为**」，**不是**在两侧之间选边 ⇒ ⇒ 不构成需要用户拍板的语义决策（调度员 2026-10-29 独立核实后背书）。 |
| **实际变更文件** | `service/common/src/main/java/com/rag/common/snowflake/Snowflake.java`（改）<br>`service/common/src/test/java/com/rag/common/snowflake/SnowflakeMonotonicClockGateTest.java`（**新增** · 8 条） |
| **测试场景和断言** | ① 墙钟倒退 1 小时 ⇒ ID 仍**严格递增**；② 墙钟倒退期间连续 200 个 ID **全部唯一**；②' 墙钟「**回拨 → 再追回同一毫秒**」⇒ **不得产出重复 ID**（🔴 直接钉 P0 #3）；③ 墙钟前跳 1 小时 ⇒ ID 仍递增；④ 时间戳分量**落在 epoch 之后**且 ≈（当前墙钟 − EPOCH）（🔴 钉住「锚点含 epoch 偏移」）；⑤ 位布局不变（41/10/12）；⑥ 同毫秒 sequence +1、跨毫秒归零；⑦ 时间戳分量只随**单调钟**前进（墙钟跳 10 年也不动）。 |
| **实际执行结果** | ✅ **8/8 绿**（守门）＋ 既有 `SnowflakeTest` **4/4 绿**。<br>🔴 **变异验证 M1**（把 `nowMillisSinceEpoch()` 改回 `wallClockMillis.getAsLong() - EPOCH`）⇒ **精确 4/8 红**：`wallClockRollbackDoesNotMakeIdGoBackwards`（ID 倒退）· `noDuplicateIdWhenWallClockReturnsToSameMillisecond`（**产出与 id1 逐位相同的重复 ID**）· `sequenceIncrementsWithinSameMillisecond`（sequence 未归零）· `mutationGuardIsDiscriminating`（时间戳跟着墙钟跳 10 年）⇒ ⇒ **守门有判别力，非假绿**。 |
| **尚未解决的问题** | ⚠️ `DRIFT-SF-003` 的 `trim()` 差异**未修**（一行可修，但属 C 类，见上表）⇒ ⇒ **该条仍开放**。 |
| **对阶段二的影响** | SF-001 已闭合 ⇒ 阶段二只剩 SF-002（配置档取舍）/ SF-003（trim + 初始化时机）。**位布局与回绕逻辑一字未动** ⇒ 阶段二若再动 snowflake，**不要重写这段锚点逻辑**（它已与 Go 同构且有守门）。 |

### 十三·三、本轮**订正**的台账表述（5 处，均为「原表述过强/不准」）

| 原表述（首版） | 订正后 | 依据 |
|---|---|---|
| `DRIFT-LOG-003`：「生产代码从不调 `Logger.init` ⇒ **三个静态方法必抛** `IllegalStateException`」 | **两件事分开**：① 静态入口**从未被生产代码使用**（潜在可用性缺陷，**当前不会抛**）；② 服务名**取值体系不同**（20 模块：4 一致 / 16 不一致）。另「Go 侧 15 处 main」⇒ **实为 18 处**，且 `hot/rpc` 与 `task/rpc` **两个 main 完全不调** | 全仓 grep 计数 ＋ `git ls-files` 逐模块 yaml 对照表（§十三·一 LOG-003 行） |
| `DRIFT-LOG-007`：「`error_reason` 空 message 兜底：RAG 退 `toString()`」 | **边界更窄**：分歧**只存在于 `getMessage()==null`** 的异常；`new RuntimeException("")` 两侧**已一致** | `Logger.java:243`/`:398` 的三元式 + `RuntimeException("")` 的 `getMessage()` 实测语义 |
| `DRIFT-CF-009`：「`deliveryErrorCode` 按类型判 ⇒ **`error_code` 取值范围更宽**」 | ❌ **证伪** —— 两侧 `deliveryErrorCode` **各只有 2 个返回值** ⇒ **取值集合完全相同**；真实差异是**判定边界**（Go 认实例 / RAG 认类型），且**当前不可达** | `process.go:64-69` ＋ `Dispatcher.java:368-382` 逐行对照 |
| `DRIFT-CF-018`：「`once` 分支直接 return ⇒ `@PreDestroy` 拿不到 handle」 | **对现码不成立**（`:253` 的 once 分支**确实**返回 handle）；真实成立的是 **`once=false` 的阻塞版**拿不到 handle ⇒ 且 **favorite 域有活的触发点**（进程关不掉） | `Dispatcher.java:222`/`:224-226`/`:253`/`:291` ＋ `FavoriteFactDispatchScheduler.java:119-124` |
| `DRIFT-CF-004`：「**不在 `common` 包内可判定**，须随那两域的审计一起定级」 | ✅ **本轮已可定级** —— 差异**全部落在 `Wire.java:129` 一行**；且「键序全丢 / 数字字面量全丢」被推翻（favorite 域**恰是唯一已规避的域**） | `CommentDeliveryValidator.java:140-148` ＋ `FavoriteDeliveryValidator.java:84-85`（该类 javadoc 自述根因）＋ PG `jsonb` 文本规范化事实 |

### 十三·四、本轮**跨域台账**订正（只登记，不改他人文件）

| 编号 | 陈旧断言 | 现状 | 落点 |
|---|---|---|---|
| `docs/DRIFT/global/common-tails.md:551` | 「真正只有毫秒的是 `DeadlineState`」 | ❌ **已失效** —— `DRIFT-COMMON-005` 已修（单调钟 + 纳秒）⇒ **全仓已无「只有毫秒」的 deadline 载体** | 合并收口执行者（共享面） |
| `docs/DRIFT/users/admin/rpc.md:2772`（G1 文件） | 提到 `deadlineEpochMs` | ❌ **已从生产代码移除**（`DRIFT-COMMON-005` 修复时删掉） | G1（其自有文件） |
| `docs/Intentional_Deviations/like/` 的 `SD-2026-10-23-09` | 登记的现象 | ❌ **现象已不复存在** | G2（其自有文件） |

#### 十三·四·二、🔴 **阶段一基线**（`docs/PhaseGoal-judge.md`）的 5 处待订正（**共享面 ⇒ 只登记**）

> `docs/PhaseGoal-judge.md` 是 **4 条 lane 矩阵的唯一数据源**（`tools/difftest/check-phasegoal-lanes.py --write` 依它生成 `docs/PhaseGoal/g{1..4}.md`）⇒ ⇒ **G4 不写该文件**（不在 G4 写权面）⇒ ⇒ 只登记 + 上报给**合并收口执行者**。

| 基线行 | 陈旧内容 | 现状（实测） | 建议订正 |
|---|---|---|---|
| `:191`（`follow-api` 行 · 「仍开放项」列） | `DRIFT-FOLLOW-008`（MaxConns 闸门粒度 ⇒ **落点 `service/common`**） | ❌ **已签字** —— `docs/Intentional_Deviations/global/common-tails.md:11`·`:548` 的 `SD-2026-10-25-01`（**2026-10-25 用户签字**）⇒ 决策 = **保留 RAG 的全局闸门，不改成 per-route** ⇒ ⇒ **不是待办** | 从「仍开放项」列移除（改为「已签字偏离」）⇒ ⇒ 该行 R 级不受影响 |
| `:498`（§十一 `D-06`） | 「共享层 **27 条编号未修** …… `DRIFT-COMMON-005` 是**唯一硬阻塞**（破坏性变更，需用户裁定）」 | ❌ **三处陈旧** —— ① 条数 **27 → 23**（`-005` · `-006` · `DRIFT-SF-001` · **`DRIFT-CF-018`（R3 轮修）** ⇒ 详见 §十二·一 与 §十三·二 与 §十四·一 #15）；② 「唯一硬阻塞」已消失 ⇒ **不再需要用户裁定**；③ **23 条里 20 条是 C 类**（20 C + 3 D + 0 A/B = **23 仍未修**） ⇒ 按 §1.1 ⑥ 第 4 条**不计入 R3 ⑥** ⇒ ⇒ 组件判定的**真正剩余项**不是「逐条修」，而是「**④⑤ 两个判据**（逐类 Go 出处 + 自动化测试）」（⇒ 本轮 R3 轮已补齐 73/73，见 `docs/DRIFT/global/vs-common.md` §4.18.3） | 改为「**23 条编号未修**（20 条 C 类 · 3 条 D 类 · **0 条 A/B 类**）＋ 5 项未编号（1 C / 3 D / 1 取证缺口）· **无硬阻塞**」 |
| `:217`（`service/common` 行 · 判定列 🔴） | 🔴 理由 = 「**27 条编号未修** …… `DRIFT-COMMON-005` 是唯一硬阻塞（破坏性变更，需用户裁定）」 | 🔴 **判据口径问题，不是「逐条修」问题** —— §1.1 的 **R3 ⑥ 只排除「已签字偏离 / 跨组件残留 / 取证缺口」，没有给「C 类阶段二暂缓项」留出口** ⇒ ⇒ §十三·一 的 24 条里**绝大多数是 OBSERVABLE（不命中 P0 任何一条）** ⇒ ⇒ 若 R3 ⑥ 补上该出口 ⇒ ⇒ **本行应重算** | **先复核 R3 ⑥ 的排除清单** ⇒ 按复核结果重算 `:217` ⇒ 重跑 `check-phasegoal-lanes.py --write` + `check-phasegoal-consistency.py` ⇒ ⚠️ **顺序有依赖**（R3 ⑥ 口径变了 ⇒ 4 条 lane 矩阵**全部**要重算） |
| `:499`（§十一 `D-07`） | comment 域 2 条「仅 RAG 有」的 DDL（`report_record.user_id` / `target_type`）⇒ 落点标注「comment 域（**未签字**）」 | ❌ **「未签字」标注已失效** —— `docs/Intentional_Deviations/comment/rpc.md:17` 记载「✅ **2026-10-24 用户已签字**：**CAND-02**（`report_record` 表）、**CAND-06**（`manage` 清位）」⇒ ⇒ **已签字 ⇒ 不是待办** | 「未签字」改为「**已签字**（`SD-2026-10-24-CAND-02`）· 决策 = **保留 RAG 侧 `report_record` 表**」 |
| `:187`（`favorite-api` 行 · 「仍开放项」列） | 405 多一个 `Content-Type` ⇒ 标注「**落点 `service/common`**」 | ❌ **落点归属订正** —— 共享层 `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:144` **第一行就是** `if (body == null) return body;` ⇒ 405 的 body 为 null ⇒ **共享层不写 Content-Type**；**真正来源是 favorite-api 域内** `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/advice/GlobalResponseAdvice.java:79`（**在 `body == null` 早退之前**写 `setContentType`）⇒ ⇒ 其余 7 域的 advice **都是先早退** | 「落点 `service/common`」改为「**落点 favorite-api 域内 `GlobalResponseAdvice`**」⇒ ⇒ **该条的落点归 G3**，G4 不落码 |

> ⚠️ **`:395`**（§八 聚合判定「有条件通过」的例外清单）引用了「共享层 27 条编号未修」⇒ **随 `:498` 一并订正**。

#### 十三·四·三、🔴 **两条红线**（Jason 已签字）⇒ 本轮**不落码**，只做基线订正登记

| 红线 | 签字档 | 决策内容 | 本轮动作 |
|---|---|---|---|
| **红线①** | `docs/Intentional_Deviations/global/common-tails.md:11` · `:548` 的 **`SD-2026-10-25-01`**（2026-10-25 用户签字） | `MaxConns` 闸门作用域 = **RAG 全局一个 / Go 每路由一个** ⇒ 决策 = **保留 RAG 的全局闸门，不改成 per-route** | ⛔ **不落码**（不是缺陷）⇒ 关联订正见上表 `:191` |
| **红线②** | `docs/Intentional_Deviations/comment/rpc.md:67` 的 **`SD-2026-10-24-CAND-02`**（2026-10-24 用户签字） | `report_record` 表 = **RAG 侧存在、Go 侧根本不存在** ⇒ 决策 = **保留 RAG 侧该表**（连带 `CAND-06` 的 `manage` 清位） | ⛔ **不落码**（不是缺陷）⇒ 关联订正见上表 `:499` |

> ⇒ 两条红线**都落在「基线仍写「未签字 / 未修 / 落点在共享层」」这一形态上** ⇒ ⇒ **本轮只登记基线订正，不改任何生产代码**。

#### 十三·四·四、🔴 **本轮收到的 3 张他域反馈单**（登记 + 转派，**不越界落码**）

| 来源 | 反馈内容 | 落点判定 | 本轮动作 |
|---|---|---|---|
| **G3**（`G4-15`） | favorite-api 的 405 响应多一个 `Content-Type` ⇒ G3 判「落点 100% 在 `service/common`」 | ❌ **独立核实后推翻** —— 见上表 `:187` ⇒ **落点在 favorite-api 域内** | ⛔ **不在 G4 落码** ⇒ **转派 G3**（域内 `GlobalResponseAdvice:79`）⇒ 本层**只登记** |
| **G3**（`G4-10` 波及） | 9 份 `tools/*/favorite/run*.sh` 各自复制了一份 `default_go_repo()`（**每份用不同探针文件做存在性判断**）⇒ **同一个目录对不同脚本可能是「真源」也可能是「不是」** | 按 AGENTS.md §5.3f「共享判据 = **依赖度数**」⇒ 这 9 份**只被 favorite 域引用** ⇒ ⇒ **属 favorite 域私有** ⇒ ⇒ **落点归 G3** | ✅ G4 已提供**共享解析函数**（`tools/difftest/go_repo.py` ＋ `tools/difftest/lib/go-repo.sh`）⇒ **G3 切过去即可**；⛔ **G4 本轮已把这 9 份全部 `git checkout` 回退，未改一个字节** |
| **G2** | 建议新增「**出处抽验脚本**」（批量核对 javadoc 里「Go 出处」的可复算性） | 🔴 **属阶段 2 建设项**（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7 ⇒ 收口执行者的事） | ⛔ **不预支**（AGENTS.md §5.3g：阶段 2 不在本 lane 范围内）⇒ **登记**，等收口执行者排期 |


### 十三·五、🔴 本节**不构成**的结论（诚实边界）

1. **本节全部为 E2（源码级）** —— 未跑任何双侧活体探针 ⇒ 每条「不一致」都应读作「**在该证据强度下不一致**」⇒ **不可外推为「已验证的行为差异」**。
2. **本节不判定 `service/common` 的组件状态** —— `docs/PhaseGoal-judge.md:217` 是**共享面**（4 条 lane 矩阵的唯一数据源）⇒ ⇒ 只登记给合并收口执行者：🔴 **该行的 🔴 理由挂在「27 条编号未修」上，而 §1.1 的 R3 ⑥ 只排除「已签字偏离 / 跨组件残留 / 取证缺口」，**没有给「C 类阶段二暂缓项」留出口** ⇒ ⇒ 本节 24 条里**绝大多数是 OBSERVABLE（不命中 P0 任何一条）** ⇒ ⇒ **判定口径需收口复核**（是**判据口径**问题，不是「逐条修」问题）⇒ 详见 §十三·四·二。
3. **`communityfact` 的对端（BTW / Sea-BreakTheWaves）不在本机** ⇒ CF-003 的「是否被消费」**不可证** ⇒ 本节只给出「**仓内无消费方**」的搜索范围与结论边界 ⇒ ⇒ **「尾 `\n` 与 HTML 转义要不要对齐 Go」在拿到对端契约前无法定论** ⇒ **本轮不落码、不预支**（⇒ 已登记为待向对端确认项）。
4. **本节未覆盖**：`service/common` 的 `protection/`（13 文件）与 `http/`（2 文件）本轮**仍未做 Go 对照**（对齐基准是 go-zero 库源码，不是本仓）⇒ 属**独立一轮工作** ⇒ 不在本节范围内。
5. **本节的 25 条定级是 E2（源码级）结论，不是「阶段一通过」的判据** —— 阶段一的判定唯一出处是 `docs/PhaseGoal-judge.md` ＋ `docs/PhasedGoal.md` 的 P0 九条 ⇒ ⇒ 本节的作用是**把「差异」与「缺陷」分开**，供收口执行者复核 R3 ⑥ 口径时使用 ⇒ ⇒ **不替代、不预判阶段一结论**。

---

## 十四、📌 R3 轮收口：24 条编号 + 5 项未编号的**显式分类**（2026-10-29 · `align/r3-service-common`）

> **本节的依据**：`docs/twoStage.md` **§10**（P0~P3 严重程度 × A/B/C/D 阶段归属）
> ＋ **§5.3**（差异与暂缓项的 **11 字段**）。
> **§10 的两个刻度不能互相替代**（原文）：**P0–P3 描述问题严重程度，A–D 描述阶段归属**。
>
> **本节回答的问题**：`docs/PhaseGoal-judge.md` §8.2 **C-2「零 🔴 组件」** 的唯一缺口是共享层 `service/common`
> ⇒ ⇒ 要判它能不能升到 **R3**，必须先回答：**归属本组件的「必须对齐 / 语义 MISMATCH / 真漂移」分歧项 = 0？**
> ⇒ ⇒ 判据 = **P0 ∩ A**（阶段一准入阻断项）**与**「非 C 类的真漂移」两条。
>
> **⚠️ 口径边界（已由 Jason 2026-10-29 裁定，`docs/PhaseGoal-judge.md` §1.1 ⑥ 第 4 条）**：
> **C 类（阶段二暂缓）不计入 R3 ⑥** ⇒ ⇒ 但 **C 类 ≠ 零差异** ⇒ ⇒ 下面每一行都保留在
> **§5.3 的「差异与暂缓项清单」** 里，**且 11 字段齐全**（见 §十三·一的逐条总表）⇒ ⇒ **不得据此宣称本组件「已对齐」**。

### 十四·一、24 条编号**清单项**（含已修的 `CF-018` ⇒ **仍未修 23**）· 逐条分类（P0~P3 × A/B/C/D）

| # | 编号 | P（严重程度） | 归属 | 阻断阶段一 | 一句话理由 | 11 字段所在 |
|---|---|---|---|---|---|---|
| 1 | `DRIFT-CF-001` | **P3** | **C** | 否 | 校验失败时的**失败路径可观测性**（信封丢失），非业务语义 | §十三·一 CF-001 |
| 2 | `DRIFT-CF-002` | **P3** | **C** | 否 | authority 日志少 6 个 metadata 键；且该功能**默认关闭** | §十三·一 CF-002 |
| 3 | `DRIFT-CF-003` | **P3** | **C** | 否 | 响应体尾 `\n` + HTML 转义差异；**对端不在仓内** ⇒ 是否被消费不可证 | §十三·一 CF-003 |
| 4 | `DRIFT-CF-004` | **P3** | **C** | 否 | `Wire.payload` 按运行期类型重序列化 ⇒ 字节可能不同（**修法一行**） | §十三·一 CF-004 |
| 5 | `DRIFT-CF-005` | **P2** | **C** | 否 | 不可中断的 JDBC 挂死 ⇒ 投递链路停滞（**outbox 数据仍在** ⇒ 不命中 P0 #3） | §十三·一 CF-005 |
| 6 | `DRIFT-CF-006` | **P3** | **C** | 否 | 投递节奏 = 批次耗时 + 1s（Go 是绝对 1s 栅格）⇒ 只影响延迟 | §十三·一 CF-006 |
| 7 | `DRIFT-CF-008` | **P3** | **C** | 否 | authority 三条日志通常无 `trace_id` ⇒ 排障能力 | §十三·一 CF-008 |
| 8 | `DRIFT-CF-009` | **P3** | **D** | 否 | Go 认**实例** / RAG 认**类型** ⇒ **当前不可达** ⇒ 经分析不实现 | §十三·一 CF-009 |
| 9 | `DRIFT-CF-011` | **P3** | **D** | 否 | JSON 键序无语义 ＋ Go 侧**无顺序断言** ⇒ 对齐无任何可观察收益 | §十三·一 CF-011 |
| 10 | `DRIFT-CF-012` | **P3** | **C** | 否 | 时间戳精度 纳秒 vs 毫秒 ⇒ 无下游按纳秒解析的证据 | §十三·一 CF-012 |
| 11 | `DRIFT-CF-013` | **P3** | **C** | 否 | token 长度按 UTF-16 码元 vs UTF-8 字节 ⇒ **RAG 更严** ⇒ 不是安全缺口 | §十三·一 CF-013 |
| 12 | `DRIFT-CF-014` | **P3** | **C** | 否 | 畸形 endpoint 的**错误文案**不同 ⇒ 两侧都是「拒绝投递」 | §十三·一 CF-014 |
| 13 | `DRIFT-CF-015` | **P3** | **C** | 否 | 缺固定错误前缀 ⇒ 只影响排障可读性 | §十三·一 CF-015 |
| 14 | `DRIFT-CF-017` | **P3** | **C** | 否 | 框架日志未并入 OBS-r3 信封 ⇒ 观测契约 | §十三·一 CF-017 |
| 15 | `DRIFT-CF-018` | ~~**P2**~~ | ~~C~~ → **✅ 已修** | 否 | **共享层半边本轮已修**（Go-faithful：停止能力归调用方）⇒ 详见 §六·六·1 与 `docs/DRIFT/global/vs-common.md` §4.18.1 | §十三·一 CF-018 |
| 16 | `DRIFT-LOG-001` | **P3** | **C** | 否 | KV 已进 logback 事件，但**全仓无 pattern 渲染** ⇒ 生产 stdout 看不到 | §十三·一 LOG-001 |
| 17 | `DRIFT-LOG-002` | **P3** | **C** | 否 | `call_chain` **尾部少一帧**（包装帧被过滤掉） | §十三·一 LOG-002 |
| 18 | `DRIFT-LOG-003` | **P3** | **C** | 否 | 静态入口生产零调用 + 服务名取值体系不同（20 模块 4 一致 / 16 不一致） | §十三·一 LOG-003 |
| 19 | `DRIFT-LOG-004` | **P3** | **D** | 否 | `Init` 失败后 RAG **可重试** / Go 被 `sync.Once` 毒化 ⇒ RAG 是更可恢复的一侧 ⇒ 对齐反而更危险 | §十三·一 LOG-004 |
| 20 | `DRIFT-LOG-005` | **P3** | **C** | 否 | 缺 `caller` + `span` 两个字段 ⇒ 可观测性字段缺失 | §十三·一 LOG-005 |
| 21 | `DRIFT-LOG-006` | **P3** | **C** | 否 | Go 落盘 + 滚动 vs RAG 只写 stdout ⇒ 部署形态 | §十三·一 LOG-006 |
| 22 | `DRIFT-LOG-007` | **P3** | **C** | 否 | `error_reason` 在 **message 为 null** 的异常上退化成类名 | §十三·一 LOG-007 |
| 23 | `DRIFT-SF-002` | **P3** | **C** | 否 | RAG **多出** `snowflake.worker-id` 一档（Go 只有 env + hash）⇒ 表达层扩展 | §十三·一 SF-002 |
| 24 | `DRIFT-SF-003` | **P3** | **C** | 否 | `SNOWFLAKE_NODE_ID` 的 **trim 差异**（Go 不 trim）+ 初始化时机不同 ⇒ 一行可修 | §十三·一 SF-003 |

**已修但保留在册的 1 条**（`twoStage.md` §5.4 要求记录「修改前 / 后 / 理由 / 文件 / 测试 / 结果 / 未解决 / 影响」）：

| 编号 | P | 归属 | 状态 | 落码记录 |
|---|---|---|---|---|
| `DRIFT-SF-001` | **P0** | **A** | ✅ **已修**（2026-10-29） | 墙钟回拨 ⇒ **重复 ID** ⇒ 命中 `docs/PhasedGoal.md` **P0 #3「重复处理」** ⇒ 唯一命中 P0 的条目 ⇒ §十三·二（E4 · 守门 8 条 ＋ 变异 4/8 精确红） |

### 十四·二、5 项未编号观察（§六·六）· 逐条分类

| # | 项 | P | 归属 | 阻断阶段一 | 理由 | 11 字段 |
|---|---|---|---|---|---|---|
| U-1 | Go `LogConf.MaxContentLength` 默认 **0** ⇒ 64KB 截断在 Go 侧**未启用** ⇒ RAG 实现但 Go 未启用 ⇒ **行为等价** | — | **D**（**不是缺陷**） | 否 | 「不是漂移」⇒ 按 `twoStage.md` §10 的 D 类「经分析确认可不实现或不适用」登记 ⇒ **不得当缺陷修** | 本节即 11 字段（结论型条目，无「恢复方案」） |
| U-2 | `LoggerFactoryBean` 各模块注册方式不一致 ⇒ 是否有模块漏注册**未验** | — | **取证缺口**（**非 A/B/C/D 缺陷项**） | 否 | ① **跨模块**（各域写权面）② **未验** ⇒ 按 `docs/PhaseGoal-judge.md` §1.1 ⑥ 第 3 条「取证缺口」⇒ **不计入组件 R3 ⑥**，但**必须**登记进 §九 证据缺口清单 | 同上 |
| U-3 | `Logger` 静态入口的 `serviceName` 取值体系不同（Go 来自 yaml `Name:`，RAG 来自 `spring.application.name`） | **P3** | **C**（**并入 `DRIFT-LOG-003`**） | 否 | 不是独立条目 ⇒ 归 `DRIFT-LOG-003` 的 11 字段里（该条已含 20 模块对照表：4 一致 / 16 不一致） | §十三·一 LOG-003 |
| U-4 | `Logger` 的三个 `doLogXxx` 是 `public`，Go 的 `logBusinessErr` 是**包私有** | **P3** | **D**（**表述差异**） | 否 | 「暴露面变大、无行为差」⇒ 表达层 ⇒ D 类 ⇒ **不修** | 本节 |
| U-5 | Go 侧 `points` / `security` 也注册 `AddUnaryInterceptors` | — | **D**（**范围外**） | 否 | `knowledge` / `points` / `security` **范围外 ⇒ 不是待办**（AGENTS.md §一）⇒ 但它影响「Go 侧注册点共 12 个」这个**计数口径** ⇒ 该口径已在各域台账按「范围内 10 个」记录 | 本节 |

### 十四·三、分类**汇总**（机器可复算）

| 分类 | 编号（本轮口径 = 24 条，**含本轮已修的 `CF-018`**）| 未编号 | 合计 | 说明 |
|---|---|---|---|---|
| **A**（阶段一准入阻断项） | **0** | 0 | **0** | `DRIFT-SF-001` 曾是 A 类 ⇒ **已修**（§十三·二）⇒ ⇒ **当前 A 类 = 0**<br>🔴 **2026-10-29 订正（口径范围）**：**本行的「A 类 = 0」只覆盖本表这 29 条**（`CF` / `LOG` / `SF` ＋ 5 项未编号）⇒ ⇒ **它**不覆盖** 落点同样在 `service/common` 的 **`E65`（出向 gRPC 不继承 deadline）** —— **`E65` 是 🔴 A 类且仍 OPEN**（2026-10-29 双侧活体实测：Go `outgoing_carrier=present_expired` vs RAG `absent`）⇒ ⇒ **「共享层 A 类硬阻塞 = 0 ⇒ 阶段一通过」这句话对 `E65` 不成立** ⇒ ⇒ **阶段一已改判为「❌ 未通过」**（`docs/PhaseGoal-judge.md` v5 · §五 X-6 · §8.2 C-3 破）<br>**证据**：`docs/PhaseGoal-judge.md` §五 X-6 行 ＋ [`../../DRIFT/global/vs-common.md`](../../DRIFT/global/vs-common.md) §1.1 E65 行 |
| **B**（阶段一竞争力补强项） | **0** | 0 | **0** | 本层<b>没有</b>「竞争力补强」性质的条目（那些是 R2 阶段 5 的事） |
| **C**（阶段二最终工程建设项） | **20** | 1（U-3） | **21** | ⇒ 按 §1.1 ⑥ 第 4 条 **不计入 R3 ⑥** ⇒ **但必须逐条保留在 §5.3 清单里（11 字段齐全）** |
| **D**（经分析确认不实现 / 不适用） | **3**（CF-009 · CF-011 · LOG-004） | 3（U-1 · U-4 · U-5） | **6** | 每条都有**明确理由**（`twoStage.md` §10 要求） |
| **取证缺口**（非 A/B/C/D） | — | 1（U-2） | **1** | 按 §1.1 ⑥ 第 3 条 ⇒ **不计入 R3 ⑥**，但须进 §九 |
| **本轮已修**（不再是「未修项」） | 1（**`DRIFT-CF-018`** ⇒ 见 `docs/DRIFT/global/vs-common.md` §4.18.1） | — | **1** | ⇒ ⇒ **本轮「未修」数 24 → 23** |
| **合计（29 条清单）** | **24** | **5** | **29** | ⇒ 与 §十二·一「**23 条编号（未修）+ 5 项未编号 = 28**」**逐条对齐**（⚠️ 本行的 **24** = **清单口径**，含已修的 `CF-018` ⇒ ⇒ **仍未修 = 23**） |
| **⇒ 其中仍未修** | **23** | **5** | **28** | ⚠️ **这是本轮之后的真实数字** ⇒ 「24 条」是**本轮修 CF-018 之前**的数 |

> ⚠️ **另一条已修、不在这 29 条里的**：`DRIFT-SF-001`（**A 类** · 墙钟回拨 ⇒ 重复 ID · P0 #3）
> —— 2026-10-29 G4 lane 已修（§十三·二的 9 字段）⇒ ⇒ **当前 `service/common` 域内 A 类硬阻塞 = 0 条**。
>
> 🔴 **2026-10-29 订正（W-F lane · 共享层台账面）—— 这句话现在只对「本表 29 条」成立**：
> **共享层还有一条 🔴 A 类未修项 —— `E65`**（出向 gRPC **不继承**请求 deadline；落点 `service/common` 的
> `RpcInterceptor` / `GoStyleRequestDeadline`）⇒ ⇒ **共享层的 A 类硬阻塞 = 1（不是 0）** ⇒ ⇒
> **「共享层 A 类硬阻塞 = 0 ⇒ 阶段一通过」这句话作废** ⇒ ⇒ **阶段一已改判为「❌ 未通过」**。
> **证据**：`docs/PhaseGoal-judge.md` §五 **X-6**（2026-10-29 实跑 `tools/difftest/check-deadline-downstream.py` ⇒ EXIT=0，
> 但该守门是**漂移形态门** ⇒ **PASS ⇒ 漂移仍在**）＋ [`../../DRIFT/global/vs-common.md`](../../DRIFT/global/vs-common.md) §1.1 **E65** 行。
> ⇒ ⚠️ **口径纪律**：`E65` / `E66` **不在 §十三·一 的 25 条表内**（那是 `CF` / `LOG` / `SF` 三族）⇒ ⇒
> **任何以「本表 A 类 = 0」为依据的「阶段一通过」推论，都必须先补上 `E65` 这一条**。

> 🔴 **本节的诚实边界（AGENTS.md §0：「已改」≠「已验证」）**：
> ① 全部为 **E2（源码级）** —— 本轮**未跑任何双侧活体探针** ⇒ 「分类」是**在该证据强度下**的分类；
> ② **C 类 ≠ 零差异** ⇒ ⇒ **不得据此宣称 `service/common`「已对齐」**（`docs/PhaseGoal-judge.md` §1.1 ⑥ 第 4 条原文）；
> ③ **U-2 是取证缺口，不是「查过没问题」**；
> ④ **10 条外部域尾巴（§十二·二 T-1 ~ T-10）不在本节** ⇒ 它们的修法落在**别的域**或需**收口裁定**
> ⇒ ⇒ 按 §1.1 ⑥ 第 2 条 **不计入本组件的 R3 ⑥**，但**计入域级聚合**与跨组件矩阵。
---

## 2026-10-29 · W-F lane · 🔴 **「已闭环」结论的可复算证据补齐**（不改判 · 只补证据）

> ⚠️ **本节不改任何结论的状态** —— 上面各条**原判一律保留**（AGENTS.md §6）⇒ 本节只做一件事：**把「✅ 已闭环 / ✅ 已修」的证据从「叙述」补成「可复算的 `文件:行号`」**（AGENTS.md §6 的「把证据补成可复算的」这一格）。
> **方法**：逐条回 **RAG 源码**（`service/common/**` / 域侧 `service/*/*-rpc/**`）**读消费点**（不是声明点）—— AGENTS.md §3.3.1 ③ 的要求。核对日期 **2026-10-29**。

| 编号 | 原状态 | **现行态（2026-10-29 复核）** | 可复算证据（`文件:行号` · **消费点**） | 是否改判 |
|---|---|---|---|---|
| **`DRIFT-COMMON-001`** | ✅ 已闭环 | ✅ **维持** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:305`（`// 🔴 DRIFT-COMMON-001 修复：超时必须走 wrappedCall，而不是原始 call` ⇒ **超时分支走的是 wrapped call**）＋ `:396`（`// DRIFT-COMMON-001：把 wrappedCall 交给超时调度器，使超时与业务 close 共用同一条…`）⇒ ⇒ **span 的结束路径与业务路径同源** ⇒ 原判成立 <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = ✅ **维持**（不改判）—— 指针当场核过：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:305`（「🔴 DRIFT-COMMON-001 修复：超时必须走 wrappedCall」）＋ `:396`（「把 wrappedCall 交给超时调度器」）**逐字仍在** ⇒ 超时与业务 close 共用同一条 span 结束路径 · Go 侧 `service/common/observability/rpc.go:69`（`status.Error(codes.DeadlineExceeded, …)` ＋ `MarkTimeout` ⇒ Go 的 select 超时分支同样走同一条打标出口）· 核对日期 **2026-10-30** | ➖ 不改判 |
| **`DRIFT-COMMON-002`** | ✅ 已闭环 | ✅ **维持** | `service/common/src/main/java/com/rag/common/communityfact/Wire.java:351`（`// DRIFT-COMMON-002 修复：receivedAt 缺失时 Go 返回 (零值, false)，不抛`）＋ `:356`（`if (receipt.receivedAt == null \|\| receipt.receivedAt.isEmpty()) {` ⇒ **先短路**）＋ `:361`（`received = Instant.parse(receipt.receivedAt)` ⇒ **只在非空时解析**）＋ `:142`（`@JsonProperty("received_at") public String receivedAt;`）⇒ ⇒ **NPE 路径已消** <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = ✅ **维持**（不改判）—— 指针当场核过：`service/common/src/main/java/com/rag/common/communityfact/Wire.java:351`（`// DRIFT-COMMON-002 修复：receivedAt 缺失时 Go 返回 (零值, false)，不抛`）＋ `:356`（对 `receipt.receivedAt` 做 null 与空串**双判空** ⇒ 先短路）＋ `:361`（`received = Instant.parse(receipt.receivedAt);` ⇒ 只在非空时解析）＋ `:142`（`@JsonProperty("received_at") public String receivedAt;`）**逐字仍在** ⇒ NPE 路径已消 · Go 侧 `service/common/communityfact/wire.go:148-149`（`time.Parse(time.RFC3339Nano, receipt.ReceivedAt)` ⇒ `""` 走 `err != nil` 分支，返回零值 + false）· 核对日期 **2026-10-30** | ➖ 不改判 |
| **`DRIFT-COMMON-003`** | ✅ 已闭环 | ✅ **维持** | `service/common/src/main/java/com/rag/common/communityfact/AuthorityHandler.java:56`（`public static final class AuthorityUnavailableException extends RuntimeException` ⇒ **sentinel 存在**）＋ `:67`（`// 沿 cause 链查找 … sentinel，等价 Go 版 errors.Is(err, ErrAuthorityUnavailable)`）＋ `:69`（`private static boolean isAuthorityUnavailable(Throwable err) {`）⇒ ⇒ **判定沿 cause 链走**（Go `errors.Is` 语义）⇒ **404 误降级成 503 的路径已消** <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = ✅ **维持**（不改判）—— 指针当场核过：`service/common/src/main/java/com/rag/common/communityfact/AuthorityHandler.java:56`（`public static final class AuthorityUnavailableException extends RuntimeException`）＋ `:67`（「沿 cause 链查找 … sentinel，等价 Go 版 `errors.Is(err, ErrAuthorityUnavailable)`」）＋ `:69`（`private static boolean isAuthorityUnavailable(Throwable err) {`）**逐字仍在** ⇒ 判定沿 cause 链走 ⇒ 404 误降级成 503 的路径已消 · Go 侧 `service/common/communityfact/authority_http.go:58`（`if errors.Is(err, ErrAuthorityUnavailable) {`）· 核对日期 **2026-10-30** | ➖ 不改判 |
| **`DRIFT-COMMON-004`** | ✅ 已由 hot 域 2026-10-25 认领并关闭 | ✅ **维持** | **域侧消费点**：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/HotRpcApplication.java:7`（`import com.rag.common.spring.RpcServerInterceptorFactory;`）＋ `:9`（`import io.grpc.ServerInterceptor;`）＋ `:30`（javadoc 明写「`@Import(RpcServerInterceptorFactory.class)`」）⇒ ⇒ **拦截器确实挂到了 hot-rpc 的 server 上**（AGENTS.md §3.3.1 ③：看**消费点**、不看注解名） <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = ✅ **维持**（不改判）—— ⚠️ **但本行原引的证据形态不对**：`:7` / `:9` / `:30` 是 **import ＋ javadoc** ⇒ 那恰恰是 **H2 形态**（**只看注解不看消费点**）的证据，**不能**据此断言「拦截器挂到了 server 上」。真正的**消费点** = `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/HotRpcApplication.java:104-109`（`@Bean public GrpcServerConfigurer rpcInterceptorConfigurer(...)` ⇒ `return server -> server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps));`）＋ 守门 `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java:150-182`（真 server ＋ **运行时后果**断言）· Go 侧 `service/hot/rpc/hot.go:42` ⇒ **两侧一致** · 核对日期 **2026-10-30** | ➖ 不改判 |
| **`DRIFT-COMMON-005`** | ✅ 已闭环（2026-10-29 修完） | ✅ **维持** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:852`（javadoc「当前线程的 deadline 过期时刻（**单调钟**纳秒，`System.nanoTime()` 域）」）＋ `:855`（「🔴 **为什么存『单调钟纳秒』而不是『墙钟 epoch 毫秒』**（DRIFT-COMMON-005 修复）」）＋ `:882`（`private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();`）＋ `:897`（单调钟读数 supplier）⇒ ⇒ **时基（墙钟→单调钟）与精度（毫秒→纳秒）两格都落地** <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = ✅ **维持**（不改判）—— 指针当场核过：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:852`（「当前线程的 deadline 过期时刻（**单调钟**纳秒，`System.nanoTime()` 域）」）＋ `:855`（「🔴 **为什么存『单调钟纳秒』而不是『墙钟 epoch 毫秒』**（DRIFT-COMMON-005 修复）」）＋ `:882`（`private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();`）＋ `:897`（`static java.util.function.LongSupplier monotonicNanos = System::nanoTime;`）**逐字仍在** ⇒ 时基与精度两格都落地 · Go 侧 = **标准库** `context.WithDeadline`（存 `time.Time` ⇒ 纳秒 + 自带单调读数；`time.Until` 走 `subMono`）⇒ **两侧一致** · 核对日期 **2026-10-30** | ➖ 不改判 |
| **`DRIFT-COMMON-006`** | ✅ 代码 2026-10-25 已删除（`markExpired` 死代码 + 跨域 javadoc 假断言） | ✅ **维持** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` 全文 **`markExpired` 只剩 2 处 javadoc 提及**（`:404` 讲旧实现、`:903` 记 2026-10-25 收口提交）⇒ ⇒ **方法本体已不存在** ⇒ 跨域 javadoc 侧：`service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:138-151`（javadoc 明写「原 javadoc 误写为 `DeadlineState#markExpired(long)`，该断言为假」＋「`markExpired(long)` **已移除**」）⇒ ⇒ **两边同时闭合** ⇒ **CYCLE-001 的循环依赖已断** <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = ✅ **维持**（不改判）—— 指针当场核过：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:404`（旧实现的 javadoc 提及）＋ `:903`（「⚠️ 2026-10-25（`align/global` 收口提交）：原 `markExpired(long)` 已**删除**」）⇒ **全文仅剩这 2 处 javadoc 提及、方法声明确已不存在**；跨域侧 `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:148`（「⇒ 曾被列为候选来源的 `markExpired(long)` **已移除**」）⇒ **两边同时闭合** ⇒ **`CYCLE-001` 的循环依赖已断**；守门 `service/common/src/test/java/com/rag/common/observability/RpcInterceptorDeadlineBindingTest.java:206` · Go 侧 `service/common/observability/rpc.go:69`（ctx 自身判超时 ⇒ **无「手动设过期时刻」的对应物**）· 核对日期 **2026-10-30** | ➖ 不改判 |
| **`DRIFT-SF-001`** | ✅ 已修（2026-10-29，雪花时钟源） | ✅ **维持** | `service/common/src/main/java/com/rag/common/snowflake/Snowflake.java:79`（`static java.util.function.LongSupplier wallClockMillis = System::currentTimeMillis;`）＋ `:85`（`static java.util.function.LongSupplier monotonicNanos = System::nanoTime;`）＋ `:160`（`this.anchorEpochNanos = (wallClockMillis.getAsLong() - EPOCH) * 1_000_000L;` ⇒ **锚点只取一次**）＋ `:313-320`（javadoc 引库注释「to make sure we use the monotonic clock if available」＋「旧实现的问题：`System.currentTimeMillis() - EPOCH` 是**纯墙钟**」）⇒ ⇒ **墙钟回拨 ⇒ 重复 ID 的路径已断** <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = ✅ **维持**（不改判）—— 指针当场核过：`service/common/src/main/java/com/rag/common/snowflake/Snowflake.java:79`（`static java.util.function.LongSupplier wallClockMillis = System::currentTimeMillis;`）＋ `:85`（`static java.util.function.LongSupplier monotonicNanos = System::nanoTime;`）＋ `:160`（`this.anchorEpochNanos = (wallClockMillis.getAsLong() - EPOCH) * 1_000_000L;` ⇒ **锚点只取一次**）＋ `:313-320`（javadoc 引库注释「to make sure we use the monotonic clock if available」＋「旧实现的问题：`System.currentTimeMillis() - EPOCH` 是**纯墙钟**」）**逐字仍在** ⇒ **墙钟回拨 ⇒ 重复 ID 的路径已断** · Go 侧 `snowflake.go:126`（`n.epoch = curTime.Add(time.Unix(...).Sub(curTime))`）＋ `snowflake.go:139`（`now := time.Since(n.epoch).Nanoseconds() / 1000000` ⇒ **单调**）· 核对日期 **2026-10-30** | ➖ 不改判 |
| **`DRIFT-CF-018`** | 🟡 **共享层半边已修 · favorite 域半边仍待修** | 🟡 **维持（半边拆分）** | **共享层侧在位**：`service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:48`（「🔴🔴 **DRIFT-CF-018 修复：调用方先造句柄**（Go-faithful 的停止能力入口）」）＋ `:90`（「🔴 **DRIFT-CF-018（2026-10-29 修）：本重载在 `once=false` 时**不可用**」）＋ `:109`（`@deprecated 阻塞式重载在 once=false 时交不出句柄（DRIFT-CF-018）`）<br>🔴 **favorite 域半边**：落点 `service/favorite/favorite-rpc/**`（**G3 写权面 · 不在本 lane**）⇒ **本轮未核 ⇒ 不宣称已验** <br><br>⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🟡 **维持（半边拆分）**（不改判）—— 指针当场核过：`service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:48`（「🔴🔴 **DRIFT-CF-018 修复：调用方先造句柄**」）＋ `:90`（「🔴 **DRIFT-CF-018（2026-10-29 修）：本重载在 `once=false` 时**不可用**」）＋ `:109`（`@deprecated` 说明）**逐字仍在**；守门 `service/common/src/test/java/com/rag/common/communityfact/DispatcherHandleLifecycleGateTest.java:74-75`（断言「调用方持有的句柄能在阻塞的 `run` 返回之前把它停掉」）**在位** ⇒ **共享层半边确实已修**。🔴 **favorite 域半边本轮已实读、确认仍成立**：`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/fact/FavoriteFactDispatchScheduler.java:119-124`（**仍调阻塞版** `Dispatcher.run(ctx, …, config.isOnce(), false)`）＋ `:77`（`@EventListener(ApplicationReadyEvent.class)`）＋ `:134`（`@PreDestroy`）⇒ `once=false` 时句柄永远拿不到 ⇒ **进程仍无法优雅关闭** ⇒ **落点 G3（favorite 域写权）** · Go 侧 `service/common/communityfact/process.go:20`（`RunDispatcher` **不返回句柄**）＋ `:56-60`（唯一退出条件是 `ctx.Done()`）· 核对日期 **2026-10-30** | ➖ **不改判**（🟡 半边拆分，**两格状态不同是有意为之**） |

> ⚠️ **本节没有做的事（诚实边界）**：① **没有改任何产品代码**（本 phase 只改 `docs/`）；② **没有复跑真 PG 活体对拍**（`UNRESOLVED-USERSCOMMON-004` 那条属 users-common 台账，不在本文件）；③ **`DRIFT-CF-018` 的 favorite 域半边本轮未核** ⇒ **不写「已闭合」**。

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W13

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-CF-018 | C（阶段 2 最终工程建设项 · 落点 favorite 域写权 G3） | P2 | 否 | Go `service/common/communityfact/process.go:20`（`func RunDispatcher(ctx context.Context, dispatcher Dispatcher, sender Sender, logger *slog.Logger, once bool) error` ⇒ **不返回任何句柄**）＋ `:56-60`（`select { case <-ctx.Done(): return nil; case <-ticker.C: }` ⇒ 循环唯一退出条件是 **ctx** ⇒ **停止能力归调用方**）vs RAG `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:337-338`（`callerHandle != null ? callerHandle : DispatcherHandle.create()`）＋ `:343-347`（句柄在**进入循环之前**发布）＋ `:374`（`once` 分支 `return handle;`）＋ `:412`（`once=false` 时循环退出后才返回）⇒ **共享层半边已修**（`:48`／`:109`／`:113`／`:142` 的 javadoc ＋ `@Deprecated(forRemoval = true)` ＋ 守门 `service/common/src/test/java/com/rag/common/communityfact/DispatcherHandleLifecycleGateTest.java:74`）；🔴 **favorite 域半边仍开放**：`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/fact/FavoriteFactDispatchScheduler.java:119-124`（仍调 **6 参 `@Deprecated` 阻塞重载** `Dispatcher.run(ctx, …, config.isOnce(), false)` ⇒ `Dispatcher.java:142-145` 内部把 `callerHandle` 传成 `null`）＋ `:77`（`@EventListener(ApplicationReadyEvent.class)` 启动）＋ `:134-140`（`@PreDestroy` 读 `this.handle` ⇒ 恒为 null）⇒ `once=false` 时句柄永远拿不到 ⇒ 进程无法优雅关闭 ⇒ ⇒ **差异仍成立**（剩余那部分 = 把 favorite 的调用点切到 7 参「调用方先造句柄」重载；落点 = **favorite 域写权 G3** ⇒ 阶段二补齐）；⚠️ **本条同时是 §十二·一 的统计断言**（`docs/DRIFT/common/alignment-2026-10-23.md:661` 标题「common 域自身的漂移真实条数 = 23 条编号 + 5 项未编号」）⇒ 本轮**逐族复算**：`:675`（CF 族未修 **14**：-001／-002／-003／-004／-005／-006／-008／-009／-011／-012／-013／-014／-015／-017）＋ `:676`（LOG 族 **7**）＋ `:677`（SF 族 **2**，SF-001 已修）＋ `:678`（合计 **23** 条编号）＋ `:1014`（⇒ 其中仍未修 **23 编号 + 5 未编号 = 28**）—— 与 2026-10-30 的 per-item 复核（`:375-398` 的 CF 14 条逐条仍未修／`:413-419` 的 LOG 7 条逐条仍未修／`:421-422` 的 SF-002／SF-003 仍未修、SF-001 已修）**逐条对齐** ⇒ **「23 条编号 + 5 项未编号 = 28」这个数仍成立**；⚠️ 但若按用户 2026-10-30 口径「🟡 部分闭合 = 仍有待实现项」把 CF-018 计入「开放」总数，则 common 域自身的开放数应为 **24 编号 + 5 未编号 = 29**（CF-018 剩余的那半边落点在 **favorite 域** `service/favorite/**` ⇒ 严格说不属「common 域自身」⇒ 本就不进这 23；台账宜补一句口径注）⇒ 归 C · **不阻断阶段一**（触发条件 `favorite.fact.dispatch.enabled=true`，默认 false ⇒ 只影响优雅关闭与部署 ⇒ 不命中 P0 九条） | 2026-10-30 |
| T-7 | D（经分析确认不实现 · 落点在容器层 ⇒ 应用层无实现路径） | P3 | 否 | Go 标准库 `net/http/server.go:1596-1611`（`writeStatusLine` ⇒ 其中 `:1602-1605` 用 `StatusText(code)` 把 **reason phrase** 写进状态行 ⇒ `HTTP/1.1 200 OK`）＋ `go-zero@v1.10.2/rest/httpx/responses.go:183`（`w.WriteHeader(code)` ⇒ 状态行交给 Go 标准库写）vs RAG `service/common/pom.xml:320`（`tomcat-embed-core` 依赖 ⇒ 本项目容器 = Tomcat **11.0.24**）＋ `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:127`（`ResponseEntity.status(HttpStatus.NOT_FOUND)` ⇒ 应用层只给**状态码**、状态行由容器生成 ⇒ **仓内无任何代码能补 reason phrase**）＋ `:113-115`（本仓自记「那个空格在 Tomcat 11 上应用层拿不到」＋「RFC 9110 的 OWS 不显著 ⇒ 两者语义相同」）⇒ ⇒ **差异仍成立**（字节级：Go `HTTP/1.1 200 OK` vs RAG `HTTP/1.1 200`）—— 容器侧出处**独立核对通过**：`tomcat-embed-core 11.0.24` 的 `org/apache/coyote/http11/Http11OutputBuffer.java:370`（`headerBuffer.put(Constants.SP);`）＋ `:372-374`（原文注释「The reason phrase is optional but the space before it is not. Skip sending the reason phrase.」）＋ `:376`（`put(Constants.CR).put(Constants.LF);`）⇒ Tomcat 只写「状态码 ＋ 一个空格 ＋ CRLF」且**无任何开关** ⇒ 落点在**容器层**、`service/common` 够不着 ⇒ **经分析确认不可修** ⇒ 归 D；⚠️ **状态仍是 ⏳ 未签字候选** `SD-2026-10-25-CAND-14`（`docs/Intentional_Deviations/global/common-tails.md:436`）⇒ **不得**按「已签字偏离 ⇒ 决策不是缺陷」销账（本条的 D 只表示「代码层无实现路径」，签字动作仍在用户侧）；⚠️ 该候选档自记「**待核实（缺真容器字节复测）**」⇒ 本轮**未**起真 Tomcat 取状态行原始字节 ⇒ 该**字节级子项**保持待核实；P3（RFC 7230／9110 允许省略 reason phrase ⇒ 客户端按状态码解析 ⇒ **业务语义无影响**，仅字节级不等价）⇒ **不阻断阶段一** | 2026-10-30 |

### lane W7

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| CF-018 | C | P2 | 否 | Go `service/common/communityfact/process.go:20`（`func RunDispatcher(ctx, dispatcher, sender, logger, once bool) error` ⇒ **阻塞式**、**不返回任何句柄**）＋ `:32-33`（`once` 分支 `return nil`）＋ `:56-60`（唯一退出条件 = `select { case <-ctx.Done(): … case <-ticker.C: }`）⇒ Go 的停止能力**归调用方（ctx）**。RAG `service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:338`（`DispatcherHandle handle = callerHandle != null ? callerHandle : DispatcherHandle.create();`）＋ `:346`（`handlePublisher.accept(handle)` ⇒ **句柄在进入循环之前就发布**）＋ `:374`（`once` 分支 `return handle;`）＋ `:412`（循环退出后 `return handle;`）⇒ **共享层半边已修**（`Dispatcher.java:48`/`:90`/`:109` 的 javadoc + `@Deprecated` 标注 + 守门 `DispatcherHandleLifecycleGateTest`）；🔴 **但 favorite 域半边仍成立**：`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/fact/FavoriteFactDispatchScheduler.java:119-124`（**仍调阻塞版** `Dispatcher.run(ctx, …, config.isOnce(), false)`）＋ `:77`（`@EventListener(ApplicationReadyEvent.class)`）＋ `:134`（`@PreDestroy` 读 `this.handle`）⇒ `once=false` 时句柄永远拿不到 ⇒ **进程无法优雅关闭** ⇒ ⇒ **差异仍成立**。**C**：落点 = favorite 域写权（G3），是「API 缺陷」而非 Go 语义差异 ⇒ 阶段二补齐；**P2**：触发条件为 `favorite.fact.dispatch.enabled=true`（`service/favorite/favorite-rpc/src/main/resources/application.yml:200` 默认 `false`）⇒ 只影响优雅关闭/部署，不命中 P0 九条 ⇒ 不阻断阶段一 | 2026-10-30 |
| DRIFT-COMMON-LOG-001 | C | P1 | 否 | Go `service/common/logger/logger.go:148-157`（8 个 `logx.LogField`：service/event_id/trace_id/error_code/file_line/call_path/call_chain/error_reason）＋ `:168` `logx.WithContext(ctx).Errorw("business_error", fields...)`；go-zero `core/logx/config.go:16`（`Encoding` 默认 `json`）⇒ KV **逐条落盘**。RAG `service/common/src/main/java/com/rag/common/logger/Logger.java:272-281`（`atError().addKeyValue(...)` ⇒ KV 已进 `ILoggingEvent.getKeyValuePairs()`），但**全仓 `logback*.xml` = 0 命中**、19 个 `application.yml` 的 `logging:` 块**只有 `level:`** ⇒ Boot 4.0.8 上游 `spring-boot-4.0.8.jar!org/springframework/boot/logging/logback/defaults.xml:14` 的 `CONSOLE_LOG_PATTERN` **不含 `%kvp`**（`CONSOLE_LOG_STRUCTURED_FORMAT` 默认空）⇒ 生产 stdout 只剩 `ERROR … : business_error` ⇒ **差异仍成立**（不是「数据没进日志框架」，是「进了事件但没被 pattern 渲染」）。**C**：修法 = 共享层加 `logback-spring.xml`（含 `%kvp`）或统一 `logging.pattern.*` ⇒ 影响 20 个模块 = 生产工程机制 ⇒ 阶段二；**P1**：业务错误日志的采集/告警/排障全线失能（不命中 P0 九条 ⇒ 不阻断阶段一） | 2026-10-30 |
| T-10 | D | P3 | 否 | Go `service/common/observability/rpc.go:38`（`timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ deadline **来自 ctx**，Go 侧**没有**「手动设过期时刻」的原语）；超时出口同文件 `:69`（`status.Error(codes.DeadlineExceeded, …)` ⇒ 与业务 close **同一条**打标路径）。RAG `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:138-151`（javadoc 已改写为「主路径 = `onHalfClose` 的 `DeadlineState#bindToCurrent`」，并明写「`markExpired(long)` **已移除**」）＋ `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:404` / `:903`（`markExpired` 全文**只剩 2 处 javadoc 提及**，方法本体已不存在）⇒ ⇒ 假断言**差异已消失**（跨域循环依赖 CYCLE-001 同批闭环）⇒ **D**：本条无待实现项 | 2026-10-30 |
| T-6 | D | P3 | 否 | Go `go-zero@v1.10.2/rest/internal/header/headers.go:17`（`ContentTypeJson = "application/json; charset=utf-8"`）＋ `rest/httpx/responses.go:182`（`w.Header().Set(ContentType, header.ContentTypeJson)`）＋ 真源调用点 `service/message/api/internal/handler/message/getunreadsummaryhandler.go:28`（`httpx.OkJsonCtx`）⇒ Go 的 JSON 响应**带 charset**。RAG `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（`GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"`）＋ `:151`（`response.getHeaders().set(HttpHeaders.CONTENT_TYPE, …)`）＋ 已登记 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2` ⇒ 10 个 api 全生效 ⇒ ⇒ 台账记的「成功 / 错误响应 `Content-Type` **缺 charset**」这一条**差异已消失**（charset 已在；仅剩「分号后一个空格」的字节差 = Tomcat/序列化层，已随 T-7 族另记，非本条）⇒ **D**：本条无待实现项（挡路的 `SD-HOT-API-006` 已于 2026-10-25 撤销） | 2026-10-30 |

### 十三·五、🟢 **hub 派工单核销轮**（2026-11-01 · G4 lane）—— 共享层「28 项」实为 **25 项**且**定级逐条一致** ⇒ **本轮只订正「E65 前提已消失」那 3 行**，**25 条的定级与状态列一律不动**

**一、派工单「回写 29 条状态列」= 🔴 无对象 ⇒ 本轮未改那 29 条**

· ① **派生层侧的失真已被归零**：新派生层里 `gap` 含「已闭合」的 **105 条**，其 `status` **全部 = ✅ 已一致** ⇒ **仍被报「未闭合/部分闭合」的 = 0** ⇒ ⇒ 派工单所依据的旧口径（60 条自相矛盾行）**已不存在**（收口执行者 `48d46c14` 治本：行级 `closed` 与 U5-O 合并都同时改 `status`）。
· ② **那 29 条本身是真未闭合**：台账正文**逐条自述**「⚠️ **2026-10-30 复核（global+common lane）**：现行态 = 🔴 **仍未修**（C 类 / D 类 · 阶段二暂缓）」⇒ ⇒ **状态列本就正确** ⇒ ⇒ **把它们改成「已闭合」= 把真缺口改成假闭合 ⇒ 不做**（这正是 §3.3.1「以运行证据为准」的同一纪律）。

**二、派工单「共享层 28 项逐条状态列与定级对齐」= 🟡 数量口径已过期 ⇒ 实为 25 项 ⇒ 且定级逐条一致 ⇒ 本轮未改那 25 条**

· **28 是旧数** —— `docs/Stage1/05-差异与暂缓项清单.md` 的汇总表**自己已订正**为「**25**（未修 23 ＋ 已修 2：`SF-001` 全条 · `CF-018` 共享层半边）」并注明「原写 23 ⇒ 那是**未修数**被当成了**条目数**」⇒ ⇒ 本表 §十三·一 的标题「逐条定级总表（**25 条**）」与之一致 ⇒ ⇒ **两侧同数，无缺口**。
· **机器逐条比对**（本层自跑 · 非转述）：台账 §十三·一 的 **25** 条 vs Stage1/05 的 **25** 条 ⇒ ① **编号集合完全相同**（两侧各 25 · 无单侧项）⇒ ② **定级列逐条比对 = 0 处不一致** ⇒ ⇒ **无对象**。
· **定性分布不变**：C 21 · D 6 · 证据缺口 1 · **A 0** ⇒ ⇒ 与 Stage1/05 的「A 类硬阻塞 = 0」一致。

**三、本轮实际落码 = 3 处**（**§十三·一 的适用范围说明**，**单行就地替换 ⇒ 零行位移**）

| # | 位置 | 原表述 | 现表述 | 依据 |
|---|---|---|---|---|
| 1 | §十三·一 适用范围说明 · 第 3 行 | 「🔴 **`E65` 是 A 类且仍 OPEN**」 | 「**该前提已消失** —— `E65` 已于 2026-10-30 双侧活体证伪并闭合 ⇒ ⇒ **现态 = 本表 25 条 A 类 = 0 ＋ `E65` 已闭合 ⇒ 共享层 A 类硬阻塞 = 0**（`E66` 仍 🔴 未闭合）」 | `docs/DRIFT/global/_README.md` 的 **E65 行**（✅ **已闭合**）⇒ 派生层 `E65` = **✅ 已一致** · gap = 「已闭合 · 差异已消失 ⇒ 无待实现项」 |
| 2 | 同节 · 第 4 行 | 「2026-10-29 双侧活体实测（Go `outgoing_carrier=present_expired` vs RAG `absent`）⇒ **出向 gRPC 不继承请求 deadline**」 | 「**⚠️ 2026-10-30 该实测结论已被双侧活体证伪并订正**（两侧逐键相同 ⇒ 同款）⇒ ⇒ **此处保留原记录仅作历史**」 | 同上（E65 第三次订正） |
| 3 | 同节 · 第 5 行 | 「X-6 判据不满足 ⇒ C-3 破 ⇒ 阶段一已改判为「❌ 未通过」」 | 「**该判读已被后续轮次推翻** —— `E65` 闭合 ⇒ ⇒ **阶段一现为「✅ 通过」** ⇒ ⇒ **本表 25 条的定级结论不变**」 | 主干 `1fa8a080`「全服务起栈 · 20/20 在服务 ⇒ 闭合 P0 #5 证据覆盖缺口 ⇒ 阶段一未通过 ⇒ 通过」 |

· ⚠️ **只订正范围说明** ⇒ ⇒ **25 条的定级、状态、证据强度一律不动**（那是 2026-10-30 复核的产物）。

**四、门禁与回归（绑工作树 `align/g4-ledger-close` @ `48d46c14`）**

· **自己域回归**（surefire XML 求和 ⇒ 运行期口径）：`service/common` **92 类 / 488 条 / 0F / 0E / 0S** ＋ `service/users/users-common` **4 类 / 39 条 / 0F / 0E / 0S** ⇒ **BUILD SUCCESS** ⇒ ⇒ 本轮**零产品代码改动**。
· **静态守门**：13 项（含 `derive-stage1.py --check`）**全 EXIT=0**。
· ⚠️ **派生层需重算**（本轮改了两处状态列）⇒ 产物在 `docs/DRIFT/_cross/**` ⇒ **收口执行者的面** ⇒ ⇒ **本层不代写派生物**。
· ⚠️ `E66` 仍未闭合（三面残余：① handler 级码集合面 ② 资源层窄窗口 ③ 域侧装配面断言 ⇒ 第③格 = **users 域容器面守门 ⇒ 归 G1**）⇒ ⇒ **共享层侧仍持有 ①②**。
