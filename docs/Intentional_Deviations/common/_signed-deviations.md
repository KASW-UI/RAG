<!-- @move AGENTS.md L2192-L2331 · §3.4.1 service/common 的 Java 端差异 -->

> **本文件属于 `docs/Intentional_Deviations/` 档案**。
> ⚠️ **本文件混合了三类性质不同的差异，review 时不可混谈**（原文已声明，此处重申）：
> ① **有意偏离** = 主动选择与 Go 不同（须签字）；② **框架限制** = 想对齐但语言/框架做不到；
> ③ **表述差异** = 语义已对齐、仅字面不同。

---

#### 3.4.1 `service/common` 的 Java 端差异（2026-10-21 逐行审计产出）

Go ↔ RAG 对照审计 `service/common` 全部 8 个 Go 非测试源文件与 RAG 全部主源码，产出 20 项偏差、修复 21 处（分支 `row/common-fix-semantic-drift`，issue #71186341）。**以下 5 条是修复后仍然存在、且无法靠改代码消除的差异**。

**分类原则**：「有意偏离」= 主动选择与 Go 不同（须签字，不得顺手改回）；「框架限制」= 想对齐但语言/框架做不到；「表述差异」= 语义已对齐、仅字面不同。三者性质不同，review 时不可混谈。

##### A. 有意偏离（需签字）

| # | 位置 | 差异 | Go 行为 | Java 行为 | 理由 | 关闭条件 |
|---|---|---|---|---|---|---|
| A-1 | `Logger.doLogFatal` / `LoggerFactoryBean.logFatal` | **等价域收窄** | `logger.go:293-296` 输出 `fatal_error` 后无条件 `os.Exit(1)`，**永远终止** | 抛 `FatalError`，**仅在无人 catch 时**终止 | 用户 2026-10-21 拍板：`System.exit` 会绕过 Spring graceful shutdown 与 `@PreDestroy` | 若业务出现 `catch (FatalError)` 后继续运行的写法，等价性即被打破 |
| A-2 | `Logger.isFiltered` / `LoggerFactoryBean.isFiltered` | **`call_chain` 内容比 Go 少框架帧** | `logger.go:53-58` 的 `github.com/zeromicro/go-zero/` 前缀**本身是死的**（`getFuncName` 同样剥掉包路径），只有 `runtime.`/`testing.` 生效 | 按该列表的**意图**执行，6 条前缀全部可命中 | Go 侧是 bug；Java 照抄会让「过滤」永远不生效。已在 javadoc 留档 | 若要求逐字复刻 Go 的**实际行为**（即不过滤），需回退本项 |

##### 🔴 逐条重核（核对日期 **2026-10-29** · W-F lane · 共享层台账面）

> **判据**：`docs/Intentional_Deviations/_INDEX.md:34` —— 「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效**」。
> ⇒ 本次对 A-1 / A-2 / B-1 / C-1 / D-1 / D-2 **逐条回 Go 真源 + 回 RAG 源码两侧核对**。

| 编号 | **唯一别名** | 签字描述的行为**现在还存在吗** | 结论 | 证据（两侧 `文件:行号`，核对日期 2026-10-29） |
|---|---|---|---|---|
| **A-1** | `A-1@common` | ✅ **存在** —— Go 侧 `os.Exit(1)` **无条件终止**；RAG 侧抛 `FatalError`，**仅在无人 catch 时**终止 | **签字有效 · 状态不变** | Go `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/common/logger/logger.go:293-296`（`logx.WithContext(ctx).Errorw("fatal_error", fields...)` 之后**无条件 `os.Exit(1)`**）· RAG `service/common/src/main/java/com/rag/common/logger/Logger.java:374`（`doLogFatal`）→ `:443`（`throw new FatalError(...)`） |
| **A-2** | `A-2@common` | ✅ **存在** —— Go 的 `github.com/zeromicro/go-zero/` 前缀**仍是死前缀**：`getFuncName` 只回末两段 ⇒ **不含包路径** ⇒ 该前缀永不命中 | **签字有效 · 状态不变** | Go `logger.go:53-58`（`filterPrefixes` 三条）＋ `:299-316`（`getFuncName` ⇒ `strings.Join(parts[len(parts)-2:], ".")`）· RAG `Logger.java:136-140`（`FILTER_PREFIXES` 六条）＋ `:542-553`（`isFiltered` 比 `declaringClass.getName()` ⇒ 6 条前缀全部可命中） |

**撞号检查**：`A-x` / `B-x` / `C-x` / `D-x` 四族编号**全仓只在本文件使用**（实测 `grep -rlE '(^|[^A-Za-z0-9_-])([ABCD])-[0-9]+' docs/Intentional_Deviations/ --include='*.md'` ⇒ 命中文件 = 本文件 ＋ `_INDEX.md`（**仅计数行**））⇒ **无跨域撞号**。
⇒ 仍为可寻址，给出唯一别名 `A-1@common` / `A-2@common` / `B-1@common` / `C-1@common` / `D-1@common` / `D-2@common`
⇒ ⚠️ **别名不改变计数**（`_INDEX.md` 的口径是「`A/C/D-x` 族 **5 条**（A-1 / A-2 / C-1 / D-1 / D-2；B-1 已关闭）」—— 本轮**5 条全部维持原判**，故**计数不变**）。

**描述完整性**：A-1 / A-2 的描述**完整**（两侧行为、理由、关闭条件俱在）⇒ **无需追加订正**。

##### B. 框架限制导致的已知不等价（**不是**有意偏离，无法靠改 `service/common` 消除）

| # | 位置 | 差异 | 影响面 | 关闭条件 |
|---|---|---|---|---|
| B-1 | `HttpMiddleware` 超时分支 / `RpcInterceptor` 超时调度 | **超时无法取消在途 IO**。Go 的 `context.WithTimeout` 到点 ctx Done，所有 ctx-aware IO（`http.Client` / `sql.DB` / `grpc.Dial`）立即返回、连接池立刻释放；Java 只能 `Future.cancel(true)` 发 `InterruptedException`，而 **JDBC 驱动、grpc-java 阻塞 stub、`java.net.http.HttpClient` 均不响应线程 interrupt** —— 阻塞在 socket read 上的调用会挂到查询自行结束 | **✅ 已实测坐实并已修复（E-4 + issue #71187874，2026-10-21）**。**修前**：`exp-b1-timeout-cancel.sh` 用 ACCESS EXCLUSIVE 表锁造真阻塞、两侧同跑 `user.login`，客户端返回后 **Go 侧 PG 查询 203ms 消失、RAG 侧还存活 4657ms**。**修后**：`DeadlineQueryTimeoutInjector` 按 `DeadlineState.remainingMillis()` 给每条 SQL 设 `setQueryTimeout` ⇒ pgjdbc 发 CancelRequest，**RAG 侧查询 176ms 消失，与 Go 的 182ms 同量级**；响应体也从 `text/plain "Request Timeout"` 变成 Go 的 `{"code":1015,"msg":"服务繁忙"}`。完整三层证据（纯 JDBC / 端到端 / 手段定性）见 §3.4.1「B-1 补记」 | **✅ 已关闭**（本行原写「无法靠改 `service/common` 关闭」，该结论**已过时**）。① **字节码证据**：链路 MyBatis-Plus → MyBatis → Spring JDBC → pgjdbc → socket，逐层 `javap -c` 数 `isInterrupted` 命中**均为 0** —— interrupt 在整条数据访问路径上无人检查。② **行为级证据**（`tools/difftest/probe-jdbc-cancel.java`）：主动 interrupt 一条等锁查询，它**照常正常返回、耗时 11014ms**（比不 interrupt 还长 1s）。③ **修复路径**：拦 `StatementHandler.prepare` 注入 timeout —— 与 Go `database/sql` 的 ctx-aware 取消是**同一条服务端语义且连接保可用**；`socketTimeout` 会让连接报废，语义相反，不可用。⚠️ 修复过程中还暴露更大的前置缺陷：gRPC `ServerInterceptor` 从未挂到 server 上，`DeadlineState` 恒未设置 ⇒ **DRIFT-018 的 24 处守卫此前全部是死代码**，见 §6.4 的 H2 勘误 |


##### 🔴 B-1 逐条重核（核对日期 **2026-10-29**）—— 「已关闭」的事实**是否真的还成立**

| 项 | 值 |
|---|---|
| **原关闭声明（保留）** | 「✅ **已关闭**（本行原写『无法靠改 `service/common` 关闭』，该结论**已过时**）」—— 关闭依据 = E-4 + issue #71187874（2026-10-21） |
| **核「关闭的那个事实现在是不是真的成立」** | ✅ **成立（限「SQL 载体」这一面）**<br>① 关闭所依赖的**两个产物仍在位**：`service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java` ＋ `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java`<br>② 关闭的**前提**「gRPC `ServerInterceptor` 真挂到 server 上」**仍成立** —— 实跑 `python3 tools/difftest/check-grpc-interceptor-wiring.py` ⇒ **EXIT=0**（10/10 rpc 进程 ① `@Import` ＋ ② `GrpcServerConfigurer` 齐备） |
| **🔴 追加订正（原句保留，不抹掉）** | B-1 的「已关闭」**覆盖面止于「SQL 载体」**。<br>⇒ **「grpc 出向载体」不归 B-1**：2026-10-29 双侧活体实测（RAG 侧 `outgoing_carrier=absent` vs Go 侧 `present_expired`）⇒ **出向 gRPC 不继承请求 deadline** ⇒ 该面是 **`E65`**（🔴 **OPEN**，落点 `service/common`）⇒ ⇒ **不得**据 B-1 的「已关闭」推断「所有超时载体都已闭合」。<br>**证据**：`docs/PhaseGoal-judge.md` §五 X-6（2026-10-29 实跑 `tools/difftest/check-deadline-downstream.py`）＋ `docs/DRIFT/global/vs-common.md` §1.1（E65 行） |
| **结论** | **B-1 的「已关闭」仍成立（限 SQL 载体）**；**追加一条覆盖面订正** —— 出向 gRPC 载体单列为 `E65`（🔴 OPEN） |
| **唯一别名** | `B-1@common`（见上方「撞号检查」） |

##### B-1 补记：机制实验与缓解手段定性（2026-10-21 同日同场三组对照）

B-1 上方的时长数字取自首轮实验。本轮把它拆成三层各测一遍，**结论有实质修正**，此前数字一并更正。

**第一层 —— 纯 JDBC 机制层（`tools/difftest/probe-jdbc-cancel.java`，绕开 HTTP/gRPC/MyBatis）**
ACCESS EXCLUSIVE 表锁持 10s，三组对照：

| mode | 结果 | 耗时 |
|---|---|---|
| `none` | ✅ 正常返回（阻塞到锁释放） | 10022ms |
| `qto:2` | ❌ **`SQLState=57014`** `canceling statement due to user request` | **2034ms** |
| `interrupt` | ✅ 正常返回 —— **interrupt 零效果** | 11014ms |

⇒ interrupt 组比 none 组还慢 1s（interrupt 就发生在 1000ms 处），说明 interrupt **连让查询稍早返回都没做到**。B-1 由此从「字节码推断」升级为「行为实测」。

**第二层 —— 同日同场端到端对照（`exp-b1-timeout-cancel.sh`，21:03 一场连跑基线与注入两组）**

| | 客户端返回 | 响应体 | PG 查询总寿命 | 客户端返回后又存活 |
|---|---|---|---|---|
| **Go**（基线） | 2190ms | `{"code":1015,"msg":"服务繁忙"}` | ~2.2s | 203ms |
| **RAG 基线** | 3174ms | `text/plain "Request Timeout"` | **~7.4s** | **4657ms** |
| **RAG 注入 `default-statement-timeout=2`** | 2449ms | `{"code":5001,"msg":"系统内部错误"}` | ~2.06s | 193ms |

⇒ 注入组 RAG 与 Go 已同量级（worker `business_error` 落在请求发起 +2.06s，与设定值吻合）。
基线组的 RAG 查询 `pid=113 query_start=21:03:15.203` 一直活到 `21:03:22.600`，而 worker `business_error` 在 `21:03:22.722` —— **worker 是在查询真正结束后才退出的**，比客户端返回（21:03:18.382）晚了 4.34s。

> **⚠️ 本次差点踩的坑：把日志里相邻两行当成同一次请求。**
> 首轮观测出现「客户端 2121ms 返回 / 查询 398ms 消失 / worker 却 6860ms 后才结束」的「三方矛盾」，
> 追了很久。真相是：`business_info` 那一行属于 **[2/4] 阶段的注册请求**，
> `business_error` 才是本轮 login 的 —— 中间隔着整段 Go 探测，**根本不是同一次调用**。
> **纪律**：任何靠日志两行之差推断耗时的读数，必须先确认两行属于**同一次请求**
> （本例靠 worker 侧 span 名 / 请求内容区分），否则该读数不可用。
> 这与 N-3/N-6「留档声称的行为从未存在过」同类：**先证伪读数，再谈结论**。

**第三层 —— 缓解手段定性**

| 手段 | 实现 | 超时后果 | 与 Go 是否等价 |
|---|---|---|---|
| **`Statement.setQueryTimeout`** | pgjdbc 起 `StatementCancelTimerTask` → 发 **CancelRequest** | 查询停止，**连接保持可用** | ✅ 等价 Go 的 ctx cancel |
| `socketTimeout` | `PGStream.setSocketTimeout` → `Socket.setSoTimeout` | socket 读超时 → **连接报废** | ❌ 语义相反，会污染连接池 |

⇒ **只能用 `setQueryTimeout`，不能用 `socketTimeout`。**

##### B-1 补记（续）：生产方案已实施（2026-10-21，issue #71187874）

上述实验结论已落地为代码，**两半缺一不可**：

| # | 组件 | 位置 | 职责 |
|---|---|---|---|
| ① | `DeadlineQueryTimeoutInjector` | `service/common` `persistence/` | 拦 `StatementHandler.prepare`，按 `DeadlineState.remainingMillis()` 设 `setQueryTimeout(ceil)`；**无 deadline 时完全不动** |
| ② | `SqlTimeoutStatusTranslator` | 同上 | 拦 `Executor.query/update`，把 `SQLState=57014` 翻成 `Status.DEADLINE_EXCEEDED` |
| ③ | 46 处 `catch (StatusRuntimeException e) { throw e; }` | `UserDomainService` 17 + `AdminDomainService` 29 | 让 ①② 的产物、以及 `checkDeadline()` 自己抛的 DEADLINE_EXCEEDED，**不被 `catch (Exception e)` 吞成 INTERNAL** |

**api 层零改动**：`DEADLINE_EXCEEDED` 落到 `mapRPCStatusToCode` 的 default 分支 → 1015，与 Go 一致。

> ⚠️ ③ 顺带修掉一个既有缺陷：`checkDeadline()` 抛的正是 `StatusRuntimeException(DEADLINE_EXCEEDED)`，
> 而它被写在 `try` 块内 ⇒ 此前会被同方法的 `catch (Exception e)` 吞成 INTERNAL。
> **即 DRIFT-018 那 24 处守卫里，落在 try 内部的部分从未生效过。**

**端到端结果（修复前 → 修复后，对照 Go）**：

| | 修复前 | 修复后 | Go |
|---|---|---|---|
| 客户端返回 | 3170ms（= api 的 3s） | **2223ms**（= rpc 的 2s） | 2208ms |
| 响应体 | `text/plain "Request Timeout"` | **`{"code":1015,"msg":"服务繁忙"}`** | 同左 |
| PG 查询滞留 | 4303ms | **176ms** | 182ms |
| difftest 39 步 | 39/39 | **39/39，unexpected=0** | — |

**⚠️ 但实施过程先撞上一个更大的前置缺陷**：③ 全部补齐、①② 全部就位后，
端到端**仍然零效果**（3318ms / `text/plain` / 查询滞留 4378ms）。诊断日志显示：
`newUnaryServerInterceptor` 确实收到 `timeout=PT2S`，可 `interceptCall` 与 `onHalfClose`
**从未被调用**，`DeadlineState.remainingMillis()` 恒为 `Long.MAX_VALUE`。

**根因不在 B-1，而是 gRPC `ServerInterceptor` 从未真正挂到 server 上** ——
`@Import` 只保证 Bean 存在，starter 3.1.0 不自动注册。详见 **§6.4 的 H2 勘误小节**。
⇒ 这条链路上**任何**依赖 gRPC 超时 / deadline 传播的机制此前都是死的，
包括 §3.4.4 登记的 DRIFT-018（24 处 `checkDeadline()`）——**它们一次都没真正生效过**。

**新增回归网（32 条）**：
`DeadlineQueryTimeoutInjectorTest`（7）+ `SqlTimeoutStatusTranslatorTest`（8）+
`DeadlinePassthroughTest`（user-rpc 7）+ `AdminDeadlinePassthroughTest`（admin-rpc 8）+
`GrpcInterceptorWiringTest`（2，防 H2 假绿复发）+ 两个结构性元测试（6）。
全仓 383 → **421 条，0 failures**。

> **实现期踩到的三个坑（均已写进代码注释）**
> ① `Invocation.proceed()` 声明抛的是 `InvocationTargetException`（反射包装），**不是** `SQLException`
>    —— 写 `catch (SQLException)` 编译通过但**永远匹配不上**，翻译器静默失效。
> ② MyBatis 3.5.19 的 `Invocation` 构造器校验 `method.getDeclaringClass()` ∈ 内部白名单，
>    测试里自造同名方法会报 `"is not supported as a plugin target"`；必须用 `mock(接口)`。
> ③ `LambdaUpdateWrapper` 在无 Spring 上下文时缺 MP 的 TableInfo 缓存，抛
>    `MybatisPlusException: can not find lambda cache` —— 它**不是** StatusRuntimeException，
>    于是被**正确地**吞成 INTERNAL，测试以「放行分支没生效」的面貌失败，极易误导排查方向。

##### C. 表述差异（语义已对齐，仅字面不同，无需签字）

| # | 位置 | 差异 |
|---|---|---|
| C-1 | `RpcInterceptor.goStyleStatusMessage` | gRPC Java 的 `Code.name()` 返回 `NOT_FOUND`，Go 的 `codes.Code.String()` 返回 `NotFound`。code 与 desc 均已保留（Go 出处 `rpc.go:95`），仅常量表示不同，不做臆造映射<br>**第四十三轮补全（users/admin 域 owner 回执带出 deadline 同款）**：deadline 场景**同款** —— gRPC Java `Code.name()` = **`DEADLINE_EXCEEDED`**（`RpcInterceptor.java:274` / `:486` 调 `goStyleStatusMessage(Code.DEADLINE_EXCEEDED, …)`）vs Go `codes.Code.String()` = **`DeadlineExceeded`**（Go 出处 `rpc.go:68`：`status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())`⇒ `err.Error()` = `"rpc error: code = DeadlineExceeded desc = context deadline exceeded"`）⇒ ⇒ **两侧的 `sea.biz_msg` 只差这一个字符串的大小写风格**（Go 驼峰 · Java 全大写）<br>⚠️ **已核：差异只在「文本」，不在「码值」，也不在 wire** —— ① **wire 不含码名**（gRPC 只编码**数字 code + message**）② **两侧的遥测属性都存数字 code**（Go `rpc.go:116-121` `attribute.Int("rpc.grpc.status_code", int(code))`；RAG `RpcInterceptor.java:307` / `:384` / `:636` 全部用 `code.value()`）⇒ ⇒ **不是行为差异 ⇒ 属 ③ 表述差异（无需签字）** ⇒ **不做臆造映射、不为对齐它去改 grpc-java 的命名** |


##### 🔴 C-1 逐条重核（核对日期 **2026-10-29**）

| 项 | 值 |
|---|---|
| **性质** | **表述差异**（语义已对齐 · 仅字面不同 · **无需签字** ⇒ 不计入签字计数） |
| **核「描述的行为现在还存在吗」** | ✅ **存在** —— gRPC Java `Code.name()` 返回**全大写**（`DEADLINE_EXCEEDED` / `NOT_FOUND`），Go `codes.Code.String()` 返回**驼峰**（`DeadlineExceeded` / `NotFound`）⇒ 两侧 `sea.biz_msg` 只差这一个字符串的大小写风格 |
| **证据（2026-10-29）** | RAG `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:671-674`（`goStyleStatusMessage` ⇒ `"rpc error: code = " + code.name() + " desc = " + desc`）· Go `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/common/observability/rpc.go:69`（`status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ code 名由 protobuf `String()` 渲染）＋ `:120`（`attribute.Int("rpc.grpc.status_code", int(code))` ⇒ **遥测侧两侧都存数字 code**） |
| **🔴 追加订正（原句保留）** | 原档写的 Go 行号 **`rpc.go:68` / `rpc.go:95`** **已漂**（2026-10-29 实测：`:69` 才是 `status.Error(codes.DeadlineExceeded, …)`；`:95` 现在是 `MarkBusinessError(...)`）⇒ **本档的 Go 出处指针按上面的实测行号更新**，**结论不变** |
| **结论** | **状态不变**（表述差异）· 唯一别名 `C-1@common`

##### D. UNRESOLVED（证据不足，**不得**按「已对齐」处理）

| # | 项 | 未知点 | 需要什么才能定 |
|---|---|---|---|
| D-1 | `Wire.canonicalHash` 的 JCS 库 | ✅ **已关（2026-10-21）**。`service-common` 的 `JcsDifferentialTest`(5/5 通过)用 cyberphone 出版 testdata 作 oracle:**复制 6 对(input,expected-output)到 `src/test/resources/jcs-fixture/{input,output}/`,erdtman 跑同样输入,断言 byte-equal**。这是真正的 Go vs Java 差分 —— 期望值就是 cyberphone 自身的 canonical 字节输出。覆盖:6 个 KAT 文件(数组/法文字符/嵌套/Unicode/数值宽度/边界) + 4 条针对性(键序/空白/指数大小端/数值塌缩) + 锁住 `Wire.canonicalHash` 全链 JCS→SHA-256→hex 与 cyberphone hash path 一致。离线无外部依赖。**实质成果**:erdtman 与 cyberphone 在 RFC 8785 KAT 上 byte-equal —— 签发/校验链两端产同一 canonical,D-1 承诺实证成立。**留档**:erdtman 在 parse 阶段对未转义 ASCII 控制字符 fail-fast,cyberphone 不 fail —— 这是 erdtman 的额外输入校验质保,**不是** D-1 想要的 canonical 差异,见 `JcsDifferentialTest` 末尾注释 |
| D-2 | `communityfact` 整体 | RAG 侧**零消费者**。代码层面已对齐，但**没有任何运行证据**，不能算行为对齐 | 接入 `comment` / `article` 等域后补集成测试 |


##### 🔴 D-1 / D-2 逐条重核（核对日期 **2026-10-29**）

| 编号 | **唯一别名** | 核 | 结论 |
|---|---|---|---|
| **D-1** | `D-1@common` | ✅ **关闭事实仍成立** —— `service/common/src/test/java/com/rag/common/communityfact/JcsDifferentialTest.java` 在位 ＋ 夹具 `service/common/src/test/resources/jcs-fixture/{input,output}/` 在位 | **状态不变（已关）** |
| **D-2** | `D-2@common` | 🔴 **原描述的第一句已过期** —— 「RAG 侧**零消费者**」**不成立**：实测 `grep -rln 'com.rag.common.communityfact' service/ --include='*.java' \| grep '/src/main/'` ⇒ **20 个域侧生产文件**已引用（article 1 · comment 4 · favorite 10 · like 5）⇒ 「零消费者」是 **2026-10-21 的快照** | **保留原句 + 追加订正**（见下）；**状态不变** |

**D-2 追加订正（2026-10-29 · 原句保留，不抹掉）**：
> **原句（保留）**：「`communityfact` 整体 —— RAG 侧**零消费者**。代码层面已对齐，但**没有任何运行证据**，不能算行为对齐」
> ⇒ **订正 ①**：「**零消费者**」**已不成立** —— 四个域（article / comment / favorite / like）**共 20 个生产源文件**已 import `com.rag.common.communityfact`（实测命令见上表）⇒ 那半句是 **2026-10-21 的快照**。
> ⇒ **订正 ②**：**「没有任何运行证据 ⇒ 不得按『已对齐』处理」这一半仍成立** ⇒ 本条的**可行动内核不变** ⇒ **状态不变**。
> ⇒ **订正 ③**：本条**不再承担逐条清单** —— 逐条证据已由 [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md) §十三·一 的 **CF 族 15 条**（每条带 Go 侧 `文件:行号`）承担 ⇒ 本条只保留「无运行证据 ⇒ 不得声称已对齐」这一句。

##### 本节的效力边界

- 本节记录的是**代码层面**的差异结论。`service/common` **从未做过 Go ↔ RAG 运行时对拍**（两边同时喂同一批输入比对输出）。因此「已修复 20 项偏差」≠「行为已验证等价」。
  - ⚠️ **2026-10-21 更正**：本行原写「全仓也**没有真实 PG / Redis 集成测试**（现有测试全部 mock 数据层）」，
    该句**已被后续工作推翻**，现共 5 个 testcontainers 文件：`UserRpcContainerWiringTest`（真 PG 12 条，
    见 §6.2 P1 已关闭）、`AvatarTransactionWiringTest`、`AdminRpcContainerWiringTest`、
    `UserApiMvcPipelineTest` / `AdminApiMvcPipelineTest`（真 Redis 容器 + 真 MVC 管线）。
    **但覆盖面仍有限**：`admin-rpc` 侧只有 wiring 级验证，`service/common` 的 20 项偏差
    仍**没有任何一条**跑在真实 PG/Redis 上 —— mock 数据层的单测证明不了 DDL、索引、
    事务隔离与连接池行为。
- 尤其 B-1 这类差异，**只有真实慢查询 + 真实超时才能暴露**，静态审查与单测都测不到，
  `tools/difftest` 也**没有慢查询场景**（见 §6.2.6「证据边界台账」）。

---

