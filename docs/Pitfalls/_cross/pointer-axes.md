# 指针的「轴」：行号对 + 机制对 **≠ 轴对**；错误码要分**两层**读；机制文件 ≠ 生效路径（2026-10-30 · RAG 仓 G4↔G1 实测 ⇒ 转 `_cross`）

> **本文件记三件事**：
> ① 把**坐标清单**（一堆 `文件:行号`）交给下游时，**光给正确的行号是不够的** ——
> 下游会把某一行**用到它撑不起的那条结论上** ⇒ ⇒ **行号逐字能核对、机制逐字能核对，却整条错位**。
> ② 同一个**错误码**在「客户端可见层」与「handler 级层」**各指什么** —— 混读会把「已对齐 / 未对齐」**判反**。
> ③ 「找到一个写着那个机制的文件」**≠**「它在生效路径上」—— 先找**禁用开关**。
>
> ⚠️ **判据句（本文最重要的一句）**：**行号对 ＋ 机制对 ≠ 轴对。**
> 核对一个指针时，除了「那一行是不是它说的那样」，还必须问「**这一行属于哪条轴**」。

---

## PIT-AXIS-01 · 把坐标清单交给下游时，**必须按轴分组并写明每组的边界**（否则下游会把它用到撑不起的结论上）

> **状态**：仍生效 · **核对日期**：2026-10-30 · **是否影响当前轮次**：是 —— 本仓每个 lane 都在互相交坐标清单（反馈单 / 回执 / 台账附录）。
> **证据**：本仓 Go 真源 `service/common/observability/rpc.go:38`（原文 = `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)`）· `go-redis/v9@v9.20.0/internal/pool/pool.go:1099-1104` + `:837` · `Sea-RideTheWind_golang/service/user/admin/rpc/internal/logic/logoutlogic.go:38` · 2026-10-30 G4 lane 实测（见下「三例」）。

### 症状长什么样

- 下游 lane 把上游给的某个 `文件:行号` 抄进了**另一条结论**的证据栏；
- 复核时**行号对得上、那一行的内容也对得上** ⇒ ⇒ **复核不疑** ⇒ ⇒ 结论静默错位；
- 错位**扩散**：上游以为自己给了「一份清单」，下游以为那是「这条结论的证据」。

### 三类指针错误（**今天一天全踩了一遍**）

| 形态 | 表现 | 谁踩的 | 怎么认出来 |
|---|---|---|---|
| **① 指针对 · 机制错** | 那一行**确实**是它说的东西，但**上游抄错了对它的描述**（自己没读那一行） | G4（抄 `global` 台账的 `E65` 条目 ⇒ 把 `depCtx` 写成 follow 域的） | 去**那一行**读一遍（§3.1 只保证「有指针」，不保证「指针背后的说法对」） |
| **② 指针对 · 机制对 · 轴/归属错** | 那一行**确实是**它说的东西，但它属于**另一条轴** ⇒ **撑不起**当前结论 | G1（把**gRPC 服务端拦截器**的 `observability/rpc.go:38` 抄进 **Redis** 结论） | 问「**这一行属于哪条轴**」—— 同一个文件里，不同的行属于不同的轴 |
| **③ 指针对 · 机制对 · 轴也对 · 但基线漂移** | 指针在**旧基线**上对，基线移位后就不对了 | 派生层已单独处理（`tools/ledger-derive/derive-stage1.py` 的「指针时效性」判据） | 指针要带**基线 commit**（`docs/Worktree.md` §三·4） |

### 判定式

1. **核对指针 = 三个问题，不是一个**：① 那一行在不在？② 那一行是不是它说的那样？③ **那一行属于哪条轴？** ⇒ **只答前两个 ⇒ 会漏掉形态②**。
2. **凡把坐标清单交给下游 ⇒ 必须按轴分组**，且**每组写明「这一组不能用在别的轴上」** ⇒ ⇒ **只给正确的行号是不够的 —— 要给边界**。
3. **下游抄坐标 ⇒ 连轴标签一起抄**（不要只抄行号）⇒ ⇒ 清单的「轴标签」与「使用须知」是**清单的一部分**，不是可选注释。
4. **清单作者自检**：交付前问一句「**这份清单里有没有哪一行容易被误用到别的轴上**」⇒ ⇒ 有 ⇒ **把那句边界写进清单抬头**（不要指望下游会自己看出来）。
5. ⚠️ **轴的判定要落到「机制」而不是「路径」**：同一个文件（甚至同一行附近）可以同时属两条轴（例：`service/common/observability/rpc.go` 的行 ⇒ 「gRPC 服务端超时轴」；`go-redis` 的 `pool.go` ⇒ 「Redis 派发轴」）⇒ ⇒ **按路径分组会分错**。

### 复发堵法（本仓已落地 · 2026-10-30）

- **上游侧**：G4 在自己台账的坐标清单抬头加了「**使用须知**」—— 明写「本表全部条目属 E65 · 出向 gRPC 轴」＋「`observability/rpc.go:*` 撑不起 Redis 结论」＋「Redis 轴的锚点在别处」＋ 判据句（commit `cfef73cd`）。
  ⇒ ⇒ **清单的边界与清单同生共死**（边界写在别处 = 没有边界）。
- **下游侧**：拿到坐标清单先问「这组属哪条轴」；清单没标轴 ⇒ **要求上游补**（而不是自己猜）。

### 实证三例（各带 `文件:行号`）

1. **形态①**：G4 给 G1 的反馈单里，「Go 侧」第 ① 条写「`follow/rpc/internal/logic/helper.go:82` = `depCtx` = otel span ctx 的返回值」⇒ ⇒ **该行实为直接透传 ctx**（`resp, err := svcCtx.UserRpc.GetUser(ctx, ...)`）⇒ ⇒ **行号对、机制错** ⇒ G4 自查抓出并订正。
2. **形态②**：G1 的 `E66`（Redis 写不绑 deadline）行把 `service/common/observability/rpc.go:38` 当 Go 侧证据 ⇒ ⇒ **该行确实是 `context.WithTimeout(ctx, timeout)`**（机制对）⇒ ⇒ **但它是项目级 gRPC 服务端拦截器** ⇒ ⇒ 撑不起 Redis 结论 ⇒ ⇒ **轴错**。
   ⇒ **Redis 侧真正承重的三行**：`logoutlogic.go:38`（`SetexCtx(l.ctx, …)` ⇒ **写绑 ctx**）· `pool.go:1099-1104`（`waitTurn` fast path **无条件先查 `ctx.Done()`** ⇒ 命令不下发）· `pool.go:837`（`getConn` **必调** `waitTurn`）⇒ ⇒ 三者合起来才支撑「Go 的 Redis 写**受 deadline 约束**」。
3. **形态③**：派生层对指针时效性的判据（`tools/ledger-derive/derive-stage1.py`）—— 指针要相对**它所属的那份台账的基线**解释。

---

## PIT-AXIS-02 · 同一个错误码在**两层**里各指什么 —— 混读会把「已对齐 / 未对齐」判反

> **状态**：仍生效 · **核对日期**：2026-10-30 · **是否影响当前轮次**：是 —— 本仓的 `logout` 超时面正在被改。
> **证据**：Go 真源 `service/common/observability/rpc.go:25-74`（真源自己的超时拦截器）· 同文件 `:53-54`（handler goroutine 持锁）/ `:59-72`（select）/ `:64`（`done` 分支要先拿锁）/ `:69`（`codes.DeadlineExceeded`）· `service/user/admin/rpc/internal/logic/logoutlogic.go:38-41`（handler 的错误路径）· `service/user/common/errmsg/errmsg.go:7`（`ErrorServerCommon = 5001`）· RAG 侧 `service/common/.../observability/RpcInterceptor.java:341-342` ＋ `:358-366`（定时器先 `close`）· `service/users/admin/admin-rpc/.../service/AdminDomainService.java:259`（域内预检）。

### 症状长什么样

- 讨论「某个错误码漂移了没有」时，**没说是哪一层**；
- 两边各有一个 1015 和一个 5001 ⇒ ⇒ 一方拿**客户端可见层**的码去比另一方的 **handler 级**码 ⇒ ⇒ **判成「不同形」或「同形」，两个方向都可能是错的**；
- ⇒ ⇒ 更坏的一层：**据此下的施工指令**（让域侧改抛另一个码）在**客户端可见层**上会造成**竞态** —— 拦截器的 `close(DEADLINE_EXCEEDED)` 与业务抛出的 `INTERNAL` 抢同一个 call 的**首次 close**。

### 两条轴（判据）

| 轴 | 谁定的 | Go 侧的值 | RAG 侧的值 |
|---|---|---|---|
| **客户端可见层** | **拦截器**（超时分支先赢） | `codes.DeadlineExceeded` = 1015 | `DEADLINE_EXCEEDED` = 1015 |
| **handler 级层** | **业务代码的 catch** | `codes.Internal`「注销失败,Redis写入异常」= 5001 | 现状 1015（域内预检抢先抛）· 改后 5001 |

⚠️ **两层的值恰好互为对方的「另一种码」** ⇒ ⇒ **最容易读反**：只比数字（1015 vs 5001）而不说是哪一层 ⇒ ⇒ 结论直接翻。

### 判定式

1. **凡断言「错误码漂移了 / 一致了」⇒ 必须写明是客户端可见层还是 handler 级层** ⇒ ⇒ 只写码值 = 没写完。
2. **「客户端可见层」的第一问不是「业务抛了什么」而是「谁先 close 了这个 call」** ⇒ ⇒ Go 侧 = 拦截器的 select（`done` 分支要先拿**被 handler goroutine 整个执行期持有**的锁 ⇒ 超时分支必胜）⇒ ⇒ RAG 侧 = 定时器（`RpcInterceptor.java:341-342` ⇒ 客户端 deadline 已过期时 `effective=0` ⇒ 调度器**立即** fire ⇒ 先 close）⇒ ⇒ **两侧都是「拦截器赢」⇒ 客户端可见层恒 1015**。
3. **改「handler 级层」前先问：改完客户端可见层会不会变？** ⇒ ⇒ 若新引入的异常会落进业务 catch 并被改写成另一个码 ⇒ ⇒ **它可能与拦截器的首次 close 抢** ⇒ ⇒ 判据 = 「谁先 close」，不是「业务抛了什么」。
4. **两侧都写出来再比**：`客户端可见层 1015 = 1015` / `handler 级 5001 ≠ 1015` ⇒ ⇒ **一行里两层都写 ⇒ 读的人不会拿错层**。
5. **附带判据**：审「某个错误码是不是 Go 那样」⇒ 必须同时看**三层**：业务 catch · 拦截器/中间件的改写 · 框架自身的默认映射 ⇒ ⇒ **只看业务路径 ⇒ 必判反**（本条的根因）。

---

## PIT-AXIS-03 · 「找到一个写着那个机制的文件」≠「它在生效路径上」—— 先找禁用开关

> **状态**：仍生效 · **核对日期**：2026-10-30 · **是否影响当前轮次**：是。
> **证据**：真源 `service/common/observability/rpc.go:19-23`（`DisableNativeRpcTimeout`）· `service/user/admin/rpc/admin.go:33` ＋ `:43` · `go-zero@v1.10.2/zrpc/server.go:164-166`（`if c.Timeout > 0` 才注册原生拦截器）· `go-zero@v1.10.2/zrpc/internal/serverinterceptors/timeoutinterceptor.go`（**死代码路径**）。

### 症状长什么样

- 要确认「超时是怎么收口的」⇒ 搜到框架里那个**同名的**拦截器文件 ⇒ ⇒ 读它 ⇒ ⇒ **机制描述完全正确** ⇒ ⇒ 引用它当证据；
- ⇒ ⇒ **但那个实现被项目层显式关掉了** ⇒ ⇒ **真正生效的是项目自己那一份** ⇒ ⇒ 结论**可能整个反过来**。

### 实证一（2026-10-30 · 本仓 `logout` 超时面）

- `go-zero@v1.10.2/zrpc/internal/serverinterceptors/timeoutinterceptor.go` = go-zero 的**原生** unary 超时拦截器（`:34` `WithTimeout` ＋ `:43-55` handler 跑 goroutine ＋ `:57-72` select）⇒ ⇒ **机制描述无误**；
- 但真源 `service/common/observability/rpc.go:19-23` 的 `DisableNativeRpcTimeout` **先把原 timeout 取出、再把 `conf.Timeout` 置 0** ⇒ ⇒ 置 0 ⇒ `zrpc/server.go:164` 的 `if c.Timeout > 0` 不成立 ⇒ ⇒ **原生拦截器根本没注册**；
- ⇒ ⇒ 真源改为**自己**注册（`service/user/admin/rpc/admin.go:43`：`observability.NewUnaryServerInterceptor(rpcTimeout, …)`，带的是刚取出的原 timeout）⇒ ⇒ **生效的是真源那一份**。

### 实证二（同源 · 「配置里写了」≠「开得了」）

- 指标抓取出口：**配置层**（actuator endpoint 有没有开）与**载体层**（进程有没有 HTTP 容器 —— `spring-boot-starter-web` ⇒ `tomcat-embed-core`）是**两个层**；
- ⇒ ⇒ `endpoints.web.exposure.include` 写了 `prometheus` 但**模块没有 web starter** ⇒ ⇒ **抓取方连不上 HTTP** ⇒ ⇒ 「配了」不等于「开得了」；
- ⇒ ⇒ 本仓实测：**10 个 rpc 里只有 1 个有 web starter ＋ tomcat** ⇒ ⇒ 9/10 无载体；
- ⚠️ **判据**：**不要 grep pom，要查依赖树** —— `service/common/pom.xml` 的 web starter 是 `test` scope ⇒ **不传递**；且父子继承下「grep 不到子模块 pom」**≠**「运行时没有」⇒ ⇒ **依赖树才是 §3.3.1 要求的「实际生效」证据**；
- ⚠️ **同族的第三形态**：**能力已装配 ≠ 能力可用** —— 本仓实例：`GoStyleRedisDeadlineAutoConfiguration` 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里有登记 ⇒ ⇒ 才是活的（反之，只在源码里看见一个类 = 不算数）。

### 判定式

1. **凡要引用「框架的某个机制」当证据 ⇒ 先在项目侧搜有没有「禁用 / 替换开关」**（`Disable*` · `*Timeout = 0` · `AddUnaryInterceptors` · `WithTimeout` 之类）⇒ ⇒ **找到开关 = 你的证据可能不是生效的那一份**。
2. **判据升级为「这个实现是否被注册 / 装配」** ⇒ ⇒ **找装配点**（`AddUnaryInterceptors` / `@Import` / `AutoConfiguration.imports` / `BeanPostProcessor`）⇒ ⇒ **装配点里没有它 ⇒ 它是死代码**。
3. **⚠️ 与 AGENTS.md §3.3.1 的关系**：§3.3.1 说的是「配置里没写 ≠ 没开」⇒ ⇒ **本条是它的两个镜像**：「配置里写了 ≠ 开得了」＋「机制类在源码里 ≠ 机制在运行时」。
4. **反向也要查**：项目自己那一份**有没有被条件装配**（`@ConditionalOnClass` / `Optional` / `enabled` 默认值）⇒ ⇒ **默认值也要读**（本仓实例：`RedisDeadline.enabled` 默认 `true` ⇒ 各域 yml 零 opt-in ⇒ 域侧零改动自动生效）。

---

## PIT-AXIS-04 · 判据红了 ⇒ **先定位「红在哪一行」** —— 根因常常在**更前面一层**，你正在查的那层根本没被测到

> **状态**：仍生效 · **核对日期**：2026-10-30 · **是否影响当前轮次**：是 —— 本仓共享层 Redis 期限代理正在修。
> **证据**：`service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadlineConnectionFactory.java:39-43`（先 `delegate.getConnection()` 再包代理 ⇒ **预算检查不在生效路径上**）· G4 lane 的 `TempDiagnoseTest` 实测报红在 **`:44`** = `RedisConnection c = f.getConnection();`（**不是**发命令那一行）⇒ ⇒ `LettuceConnectionFactory.getConnection()`（SDR 4.0）**会立刻建连** · `service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadlineAutoConfiguration.java:63-75`（`BeanPostProcessor` 改的是 `RedisTemplate` 的**字段**，不是 bean ⇒ `@Autowired RedisConnectionFactory` 拿到的仍是裸 factory）。

### 症状长什么样

- 守门 / 探针红了 ⇒ 你立刻提出一个**机制假设**（「命令级代理没命中」「`stringCommands()` 绕过了」「`enabled` 开关是 false」）；
- ⇒ ⇒ 那些假设**一个都没被测到** —— 因为**失败发生在更前面一层**，流程**根本没走到**你怀疑的那一步；
- ⇒ ⇒ 越查越深，**离根因越远**：改的是命令层，实际要改的是**连接获取层**；
- ⇒ ⇒ 更坏的一层：你在命令层加的补丁**看起来能修好**（守门转绿）⇒ ⇒ **但机制从未在生产路径上生效过**。

### 实证（2026-10-30 · 本仓共享层 Redis 期限代理）

> ⚠️ **本小节标题里刻意不写编号** —— 标题里若出现任何编号 token，派生层的退化分支（`find_id_token` 扫标题全文取**第一个**编号 token）会把**本节**当成**那个编号的候选** ⇒ ⇒ **劫持它的 domain / wave / basis** ⇒ ⇒ 编号只写在正文里（见 `PIT-DERIVE-01`）。

- G4 的探针断言「异常链里必须有 `QueryTimeoutException`」⇒ 红了 ⇒ **但红在 `:44`（`getConnection()`）**，拿到的是 `RedisConnectionFailure: Unable to connect to Redis` ⇒ ⇒ **失败层 = 获取连接**，不是命令派发；
- ⇒ ⇒ 读实现：`GoStyleRedisDeadlineConnectionFactory.getConnection()`（`:39-43`）**先连后包** ⇒ ⇒ `assertNotExhausted` **不在生效路径上** ⇒ ⇒ 预算耗尽时 RAG 仍然会开一条 TCP 连接；Redis 不可达时得到的是**连接失败**而不是**期限错误**；
- ⇒ ⇒ 于是「命令级拦截为什么没生效」这个问题**问错了层** —— 它压根没被执行到；
- ⇒ ⇒ **修法**：把 `assertNotExhausted` 放进 `getConnection()`、放在 `delegate.getConnection()` **之前** ⇒ ⇒ 预算耗尽时真的不碰 socket，**且异常类型与「Redis 在不在线」无关** ⇒ ⇒ 守门**不需要真 Redis 就能跑** ⇒ ⇒ 这才是把「未证明」变成「已证明」的那一步。

### 判定式

1. **守门红 ⇒ 第一件事是读「红在哪一行」**（断言消息里的行号 / stack trace / **异常类型**），**不是**立刻提机制假设。
2. **异常类型本身就是层级线索**：`RedisConnectionFailure`（连不上）⇒ 失败在**获取连接**层 ⇒ ⇒ 修法在连接层；`QueryTimeoutException` ⇒ 失败在**命令派发前** ⇒ ⇒ 修法在派发层。⇒ **两个类型指向两个不同的修法**。
3. **「机制有没有接上」要追问「接在哪一步」**：包在连接对象上 ≠ 检查发生在命令之前 ⇒ ⇒ **检查点必须早于任何 I/O** —— 而**获取连接本身就是 I/O**。
4. **把检查提前 ⇒ 守门从「需要环境」变成「不依赖环境」**：检查点提到 `getConnection()` 之前 ⇒ 无 Redis 时异常类型仍然确定 ⇒ ⇒ 守门可在 CI 跑 ⇒ ⇒ **这本身就是可证明性的提升**，不只是修法选择。
5. **别用否定式假设开路**（「是不是被绕过了？」）：先证明「失败发生在哪一层」，再问「这一层为什么没拦住」。
6. **连带要核「覆盖面」**：包住 `RedisTemplate` 的字段 ≠ 包住 `RedisConnectionFactory` 这个 **bean** ⇒ ⇒ `@Autowired RedisConnectionFactory` 的注入点**不在覆盖内** ⇒ ⇒ 覆盖面要**逐个注入点**列出来核（本仓实测：域侧 main 代码**零**直接注入 ⇒ 当时只是潜在缺口，但类 javadoc 的覆盖面表述已经超出实际）。

---

## 效力边界（2026-10-30）

- 本文件管**四个维度**：**指针的「轴 / 归属」**（`PIT-AXIS-01`）· **错误码的两层读法**（`PIT-AXIS-02`）· **机制是否在生效路径上**（`PIT-AXIS-03`）· **判据红在哪一层**（`PIT-AXIS-04`）；
  **「指针对不对」**由 `tools/difftest/ledger-claim-audit.py` 管（落空 / 实体错位）· **「指针时效性」**由派生层管。
- ⚠️ **本文的表格首格一律不用编号**、**行内不写状态标记** ⇒ ⇒ 否则本文件会被派生层当成**新的台账条目**（见 `docs/Pitfalls/_cross/derived-layer.md` 的 `PIT-DERIVE-01/02`）。
- ⚠️ **本文的编号 token 只出现在标题首 token 位置**（`PIT-AXIS-01` / `-02` / `-03` / `-04`）⇒ ⇒ 本文件**不自噬**。
