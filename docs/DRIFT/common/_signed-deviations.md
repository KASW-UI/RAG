<!-- @move AGENTS.md L2192-L2331 · §3.4.1 service/common 的 Java 端差异 -->
#### 3.4.1 `service/common` 的 Java 端差异（2026-10-21 逐行审计产出）

Go ↔ RAG 对照审计 `service/common` 全部 8 个 Go 非测试源文件与 RAG 全部主源码，产出 20 项偏差、修复 21 处（分支 `row/common-fix-semantic-drift`，issue #71186341）。**以下 5 条是修复后仍然存在、且无法靠改代码消除的差异**。

**分类原则**：「有意偏离」= 主动选择与 Go 不同（须签字，不得顺手改回）；「框架限制」= 想对齐但语言/框架做不到；「表述差异」= 语义已对齐、仅字面不同。三者性质不同，review 时不可混谈。

##### A. 有意偏离（需签字）

| # | 位置 | 差异 | Go 行为 | Java 行为 | 理由 | 关闭条件 |
|---|---|---|---|---|---|---|
| A-1 | `Logger.doLogFatal` / `LoggerFactoryBean.logFatal` | **等价域收窄** | `logger.go:293-296` 输出 `fatal_error` 后无条件 `os.Exit(1)`，**永远终止** | 抛 `FatalError`，**仅在无人 catch 时**终止 | 用户 2026-10-21 拍板：`System.exit` 会绕过 Spring graceful shutdown 与 `@PreDestroy` | 若业务出现 `catch (FatalError)` 后继续运行的写法，等价性即被打破 |
| A-2 | `Logger.isFiltered` / `LoggerFactoryBean.isFiltered` | **`call_chain` 内容比 Go 少框架帧** | `logger.go:53-58` 的 `github.com/zeromicro/go-zero/` 前缀**本身是死的**（`getFuncName` 同样剥掉包路径），只有 `runtime.`/`testing.` 生效 | 按该列表的**意图**执行，6 条前缀全部可命中 | Go 侧是 bug；Java 照抄会让「过滤」永远不生效。已在 javadoc 留档 | 若要求逐字复刻 Go 的**实际行为**（即不过滤），需回退本项 |

##### B. 框架限制导致的已知不等价（**不是**有意偏离，无法靠改 `service/common` 消除）

| # | 位置 | 差异 | 影响面 | 关闭条件 |
|---|---|---|---|---|
| B-1 | `HttpMiddleware` 超时分支 / `RpcInterceptor` 超时调度 | **超时无法取消在途 IO**。Go 的 `context.WithTimeout` 到点 ctx Done，所有 ctx-aware IO（`http.Client` / `sql.DB` / `grpc.Dial`）立即返回、连接池立刻释放；Java 只能 `Future.cancel(true)` 发 `InterruptedException`，而 **JDBC 驱动、grpc-java 阻塞 stub、`java.net.http.HttpClient` 均不响应线程 interrupt** —— 阻塞在 socket read 上的调用会挂到查询自行结束 | **✅ 已实测坐实并已修复（E-4 + issue #71187874，2026-10-21）**。**修前**：`exp-b1-timeout-cancel.sh` 用 ACCESS EXCLUSIVE 表锁造真阻塞、两侧同跑 `user.login`，客户端返回后 **Go 侧 PG 查询 203ms 消失、RAG 侧还存活 4657ms**。**修后**：`DeadlineQueryTimeoutInjector` 按 `DeadlineState.remainingMillis()` 给每条 SQL 设 `setQueryTimeout` ⇒ pgjdbc 发 CancelRequest，**RAG 侧查询 176ms 消失，与 Go 的 182ms 同量级**；响应体也从 `text/plain "Request Timeout"` 变成 Go 的 `{"code":1015,"msg":"服务繁忙"}`。完整三层证据（纯 JDBC / 端到端 / 手段定性）见 §3.4.1「B-1 补记」 | **✅ 已关闭**（本行原写「无法靠改 `service/common` 关闭」，该结论**已过时**）。① **字节码证据**：链路 MyBatis-Plus → MyBatis → Spring JDBC → pgjdbc → socket，逐层 `javap -c` 数 `isInterrupted` 命中**均为 0** —— interrupt 在整条数据访问路径上无人检查。② **行为级证据**（`tools/difftest/probe-jdbc-cancel.java`）：主动 interrupt 一条等锁查询，它**照常正常返回、耗时 11014ms**（比不 interrupt 还长 1s）。③ **修复路径**：拦 `StatementHandler.prepare` 注入 timeout —— 与 Go `database/sql` 的 ctx-aware 取消是**同一条服务端语义且连接保可用**；`socketTimeout` 会让连接报废，语义相反，不可用。⚠️ 修复过程中还暴露更大的前置缺陷：gRPC `ServerInterceptor` 从未挂到 server 上，`DeadlineState` 恒未设置 ⇒ **DRIFT-018 的 24 处守卫此前全部是死代码**，见 §6.4 的 H2 勘误 |

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
| C-1 | `RpcInterceptor.goStyleStatusMessage` | gRPC Java 的 `Code.name()` 返回 `NOT_FOUND`，Go 的 `codes.Code.String()` 返回 `NotFound`。code 与 desc 均已保留（Go 出处 `rpc.go:95`），仅常量表示不同，不做臆造映射 |

##### D. UNRESOLVED（证据不足，**不得**按「已对齐」处理）

| # | 项 | 未知点 | 需要什么才能定 |
|---|---|---|---|
| D-1 | `Wire.canonicalHash` 的 JCS 库 | ✅ **已关（2026-10-21）**。`service-common` 的 `JcsDifferentialTest`(5/5 通过)用 cyberphone 出版 testdata 作 oracle:**复制 6 对(input,expected-output)到 `src/test/resources/jcs-fixture/{input,output}/`,erdtman 跑同样输入,断言 byte-equal**。这是真正的 Go vs Java 差分 —— 期望值就是 cyberphone 自身的 canonical 字节输出。覆盖:6 个 KAT 文件(数组/法文字符/嵌套/Unicode/数值宽度/边界) + 4 条针对性(键序/空白/指数大小端/数值塌缩) + 锁住 `Wire.canonicalHash` 全链 JCS→SHA-256→hex 与 cyberphone hash path 一致。离线无外部依赖。**实质成果**:erdtman 与 cyberphone 在 RFC 8785 KAT 上 byte-equal —— 签发/校验链两端产同一 canonical,D-1 承诺实证成立。**留档**:erdtman 在 parse 阶段对未转义 ASCII 控制字符 fail-fast,cyberphone 不 fail —— 这是 erdtman 的额外输入校验质保,**不是** D-1 想要的 canonical 差异,见 `JcsDifferentialTest` 末尾注释 |
| D-2 | `communityfact` 整体 | RAG 侧**零消费者**。代码层面已对齐，但**没有任何运行证据**，不能算行为对齐 | 接入 `comment` / `article` 等域后补集成测试 |

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

