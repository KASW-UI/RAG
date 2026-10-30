# Round 2 · E65 / E66 —— 出向 deadline 传播（闭合 A 类阻断）

> **本轮目标**：把「Go 侧 deadline 到点 ⇒ 出向调用立刻失败」与「RAG 侧出向调用**无限期挂住**」的**行为相反**改成一致。
> **本轮不执行整改**（规范 §一.6 / §一.7）—— 本文件是**计划**，不是执行记录。

---

## ⚠️ 本轮的三条铁律（每条都踩过坑）

1. 🔴 **漂移形态守门「变红」是预期结果，不是回归。**
   `tools/difftest/check-deadline-downstream.py` 是**漂移形态守门**（它的 docstring ③：「drift 键是**已登记的漂移形态** ⇒ 漂移还在 ⇒ 绿；形态变了（**被修好** / 前提变了）⇒ **红**」）
   ⇒ **本轮把 E65 修好之后，这个门必然转红** ⇒ 那不是「守门坏了」，是**守门在提醒「该把漂移形态守门改成等值守门了」**。
   ⇒ **必须同一次提交里重写门禁**（`P2-T05`），否则会有人把这次红当回归「修回去」。

2. 🔴 **写权横跨共享层 + 四个域 ⇒ 必须同一次提交。**
   `service/common/**` = **G4 单写权**（`docs/DRIFT/global/lanes/_PROTOCOL.md` 硬规则 + `docs/DRIFT/_cross/cross-domain-coupling.md:282` 的 C1）；裸 stub 调用点分属 **comment / like / message**（favorite 侧只改共享层即生效）。
   ⇒ **不允许**「先落共享层、再让各域跟上」⇒ 会出现**中间态 = 一半修了、一半没修** ⇒ 跨态更危险。
   ⇒ ⚠️ **`favorite` 域对 `global` 零写权**（`_PROTOCOL.md` §六）⇒ 共享层改动**必须记账并告知 favorite owner**。

3. 🔴 **改造面数字不可信，必须重数。**
   台账里的「21 个出向 stub / 30 处零 deadline」**被订正过两次**（原写「20 个 / 7 处」⇒ 后订正为「21 个 / 30 处」；中途还错过一次「零 deadline 的 7 处」⇒ 实为 **11 个类 / 30 个调用点**）。
   ⇒ **执行者必须逐条重数并落盘**（`P2-T01`）⇒ 不得直接引用台账数字。

---

## §5.1 本轮目标与结束状态

| 项 | 内容 |
|---|---|
| **处理哪些已知问题** | ① `E65`（🔴 OPEN · **无阶段归属定级**）—— 出向 gRPC 不继承请求 deadline ⇒ **RAG 侧无限期挂住** ② `E66`（🔴 OPEN · **无定级** · 仅 L1 证据）—— Redis 写不绑 deadline ③ `E38②`（窄化后的第③层残余）④ comment 域 `D8` / `D9`（= E65 / E66）⑤ like 域 `FB-05`、task 域 `FB-06`、users 域 `DRIFT-ARPC-010`（= E66） |
| **产生哪些可检查的结果** | ① **逐条落盘**的出向 stub 全表（类 + 调用点 + Go 对照 + 行号）② 共享层**两载体打通**（`DeadlineState` 与 `GoStyleRequestDeadline` 合一）③ 各域裸 stub 全部走 `applyTo` ④ **11 个零上界类**补上 2000ms 硬上界（与 Go `zrpc/config.go:31` `Timeout default=2000` 同形）⑤ **守门从「钉漂移形态」改成「等值」**，且**变异必红** ⑥ 双侧活体复跑：Go 与 RAG 的 `failed_fast` / `code` / `outgoing_carrier` **逐项相等** ⑦ `E66` 拿到**双侧活体证据**并**完成定级** |
| **为什么是下一轮的必要前提** | ① `Round 3` 的 **P0 #4 行改判**与 **X-6 行改判**必须有「两侧相等」的活体证据 ② `Round 3` 的 C-2 / C-3 聚合依赖 X-6 ③ `Round 7` 的最终验收的 P0 #4 判据靠它 ④ 顺带把 `E38②` 的第③层**真正归零**（原为「窄化后另立项」） |
| **本轮不做** | 不改 Go 真源（冻结只读）· 不做**架构级**改造（第四十六轮已否决「handler 挪受控 executor + 超时 interrupt」原修法 ⇒ 照做会**新造漂移**，因为 **interrupt 连 Go 都不做**）· 不改根级文件与 `docker-compose.yaml`（Round 1 的面）· 不改 `PhaseGoal-judge.md`（Round 3 的面）· 不动 `tools/difftest/` 的守门**清单计数口径**（动了要同改 judge §6.3 ⇒ 制造 B14） |

---

## §5.2 开始条件

| 条件 | 状态 | 证据 / 解除方式 |
|---|---|---|
| **`P2-T01` 的落点全表已产出** | ⚠️ **本轮第一件事** | 表里每条有 `文件:行号` + Go 对照 ⇒ 否则后面的改动没有工作单 |
| **`Round 1` 的真栈可用**（E65 的**验收**需要） | ⚠️ **验收前置** | `Round 1` 的 `P1-T05` ⇒ 20/20 在服务 ⇒ **双侧活体探针要真跑两个进程**<br>⚠️ **实现任务（`P2-T01`~`P2-T04`）不依赖本条** ⇒ 可与 Round 1 **并行** |
| **`check-deadline-downstream.py` 当前可跑** | ✅ | 实测 2026-10-29：EXIT=0，输出 `Go {failed_fast:true, code:4, outgoing_carrier:present_expired, sql_carrier:present_expired}` ⇔ `RAG {failed_fast:false, code:0, outgoing_carrier:absent, sql_carrier:present_expired}` |
| **Go 真源可读** | ✅ | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`（冻结只读）· 需 `go-zero@v1.10.2` 的 `zrpc/config.go:31` / `zrpc/client.go:70-71` / `zrpc/internal/client.go:116` / `zrpc/internal/config.go:15` |
| **写权协议已确认** | ⚠️ **需执行前核对** | `docs/DRIFT/global/lanes/_PROTOCOL.md`（硬规则 1~3 + 拆分纪律 1~2 + 合并纪律 5 步）· `docs/DRIFT/_cross/cross-domain-coupling.md` 的 **C1~C7** |
| **无前置任务** | ✅ | 本轮可与 Round 1 并行 |

> ⚠️ **若前置不满足**：① 落点全表未出 ⇒ **本轮不得开始落码**（无工作单 = 盲改）② 真栈未就绪 ⇒ **实现可继续、验收不得开始**（并按 §5.6 记录阻塞）。

---

## §5.3 逐项任务清单

### P2-T01 · 重数并落盘「出向 stub 全表」

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P2-T01` |
| **原始编号** | `E65` · `E38②` · `X-6` · `P0 #4` |
| **来源** | `docs/DRIFT/global/vs-common.md:30`/`:46`/`:63` · `docs/DRIFT/global/_README.md:391` · `docs/DRIFT/_cross/domain-common-lanes.md:131` · `docs/DRIFT/comment/rpc.md:179`（`D8`）· `docs/DRIFT/like/rpc.md`（`U15`） |
| **当前状态** | 🔴 台账数字**被订正过两次**，不可信 ⇒ 需重数 |
| **执行目的** | 改造面是**跨 5 个模块的 21 个类 / 30 个调用点** ⇒ 不先精确落表，就会漏改（漏一个 = 该条路径仍然挂住） |
| **具体操作** | ① 全仓扫**出向 stub 提供者**（`@GrpcClient` 注入的 stub 字段 + `newStub`/`build` 出来的 stub）② 对每个 stub 判定三态：**① 走 `applyTo(stub)`**（有上限但 gRPC 路径 no-op）· **② 走 `remainingMillis()` 手搓** · **③ 裸 stub（零 deadline、无上界）** ③ 对每个调用点记录：`文件:行号` · 所属模块 · 当前形态 ④ 逐条给出 **Go 对照**（Go 侧对应调用点 + 上下文是否带 ctx）⑤ 标出 **Go 的 2000ms 硬上界出处**（`zrpc/config.go:31`）⑥ **必须自证计数**（脚本输出 + 人工抽查 ≥3 条） |
| **具体落点** | 新建 `docs/RoundPlan/round-02/outbound-stub-inventory.md` |
| **执行主体** | **合并收口执行者（主线）**（只读 + 写 `docs/RoundPlan/**`） |
| **前置条件** | 无 |
| **并行关系** | 与 `P1-T01` **可并行**；本轮其余任务**都依赖它** ⇒ **必须最先完成** |
| **写权限** | 只写 `docs/RoundPlan/round-02/**` ⇒ **不改任何产品代码** |
| **验收命令** | ```bash# ① 计数可复算（脚本自带，且必须打印 Δ_start/Δ_end）\npython3 docs/RoundPlan/round-02/count-outbound-stubs.py\n# ② 与 Go 对照表逐条抽查 ≥3 条（人工）\n# ③ 三态分类的总和 == 总数（脚本自检）``` |
| **验收证据** | ① 表内**每条**有 `文件:行号` + 三态归类 + Go 对照 ② 三态计数之和 = 总数（脚本自检打印）③ ⚠️ **零 deadline 的类数**单独列出并与 Go 的 2000ms 上界逐条对照 |
| **完成条件** | **零 deadline 的类逐个有名字**（不是「约 11 个」）＋ 表可被 `P2-T02`~`P2-T04` 直接当工作单用 |
| **失败处理** | 扫不全（拿不准某个 stub 算不算出向）⇒ **按「保守包含」原则全部列入**，宁可多改不可漏改 ⇒ 并在表里标「待人工判定」 |
| **状态更新** | `docs/DRIFT/global/vs-common.md` 的 E65 行**本轮不改**（G4 面，但账要在 Round 3 统一回填）⇒ 落表先行 |
| **后续任务** | `P2-T02` · `P2-T03` · `P2-T04` |

### P2-T02 · 共享层：把服务端有效期限绑进出向载体（G4 单写权）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P2-T02` |
| **原始编号** | `E65`（共享层半边）· `E38②` |
| **来源** | `docs/DRIFT/global/vs-common.md:46`（重核段）· `docs/DRIFT/global/_README.md:391`（候选修法：共享层在 `onHalfClose` 里把有效期限也绑进 `GoStyleRequestDeadline`）· `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` · `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:110-119`（`applyTo` 的两个 `withDeadlineAfter` 分支）· `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:324`（`begin`）/ `:356`（`clear`） |
| **当前状态** | 🔴 **未做**。根因一句话：**链上有两个互不相通的载体** —— `RpcInterceptor.DeadlineState`（服务端 / rpc 线程）vs `GoStyleRequestDeadline`（出向 stub / api 线程）；`GoStyleRequestDeadline.begin` 在全仓生产代码中**只出现 1 次**（`HttpMiddleware.java:324`）⇒ **rpc 进程无 HTTP 入口 ⇒ 出向 stub 永远看不到入向 deadline** |
| **执行目的** | 这是**一处改动覆盖全部 rpc→rpc 路径**的杠杆点；不改它，就得逐个域逐个调用点打补丁 |
| **具体操作** | ① 在 `RpcInterceptor.onHalfClose()` 里，把**服务端已算出的有效期限**（`deadlineEpochMs` / `DeadlineState` 的预算）**同时**绑进 `GoStyleRequestDeadline`（与 `HttpMiddleware.begin` 同一载体）② 确认 `applyTo(stub)` 在 gRPC 路径**不再是 no-op**（`GoStyleRequestDeadline.java:115` 算 `remainingNanos` · `:117`/`:119` 两个 `withDeadlineAfter` 分支）③ ⚠️ **`DeadlineState` 与 OTel scope 是两套**（like 域 `vs-common.md:200` 已记）⇒ **不要**把两者混成一套 ⇒ `DeadlineState` 的既有语义保持不变 ④ ⚠️ **`onHalfClose` 的 scope 边界**：现有实现已在 `otelCtx.makeCurrent()` 内包住 `super.onHalfClose()`（`RpcInterceptor.java:453-465`）⇒ 绑定期**必须落在同一 scope 内** ⑤ 补一条**装配层断言**（真 `SpringApplication` + 真 `GrpcServerFactory`，断言出向 stub 确实拿到了 deadline），不是 Mockito |
| **具体落点** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` · `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java` · `service/common/src/test/**`（新增装配层测试） |
| **执行主体** | **G4**（`service/common/**` 的**唯一**写者 · `align/g4-service-common`） |
| **前置条件** | `P2-T01` |
| **并行关系** | **不可**与 `P2-T03`/`P2-T04` 分开提交 ⇒ **必须同一次提交**（见「写权限」）· 可与 `P1-*` 并行 |
| **写权限** | `service/common/**` ⇒ **只有 G4 能写**；G1~G3 **只读** |
| **验收命令** | ```bash# ① 模块级测试（必须带 -am，否则 ~/.m2 过期 com.rag:common ⇒ 假红）\nmvn -o -B -DargLine=-Xmx2g -pl service/common -am test\n# ② 装配层断言必须是真的（真 SpringApplication + 真 GrpcServerFactory）\n# ③ 变异必红：把 onHalfClose 里的绑定去掉 ⇒ 新增断言必须红\npython3 tools/difftest/mutate-deadline-downstream.py   # 本轮会重做（P2-T05）``` |
| **验收证据** | ① 模块级测试 0F/0E ② 装配层断言的**变异精确转红** ③ `outgoing_carrier` 在 RAG 侧由 `absent` 变 `present_expired` |
| **完成条件** | **rpc 进程的出向 stub 能看到入向 deadline**（不是「加了代码」而是「断言到值」） |
| **失败处理** | 若 `applyTo` 在 gRPC 路径仍 no-op ⇒ **停下**，回到 `GoStyleRequestDeadline` 的载体选择重新设计（**不要**在调用点堆手搓 `remainingMillis()`） |
| **状态更新** | `docs/DRIFT/global/vs-common.md` 的 E65 行 + `docs/DRIFT/global/_README.md` 的 A1 轴（「半开」→ 改写）⇒ G4 写权面，**本轮可改**（G4 拥有 `global/vs-common.md`） |
| **后续任务** | `P2-T05`（守门改造）· `P2-T06`（E66 载体挂靠） |

### P2-T03 · 各域裸 stub 调用点改用 `applyTo(stub)`

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P2-T03` |
| **原始编号** | `E65`（域侧半边）· comment 域 `D8` · like 域 `U15` |
| **来源** | `docs/DRIFT/global/vs-common.md:46`（逐条 `文件:行号` 清单）· `docs/DRIFT/global/_README.md:391`（「各域裸 stub 调用点改用 `applyTo`」）· `docs/DRIFT/comment/rpc.md:179` |
| **当前状态** | 🔴 **未做**。已知样例：`comment-rpc/client/ArticleRpcClient.java:65`（裸 stub）· `like-rpc/client/MessageRpcClient.java:85` · `message-rpc/client/UserRpcClient.java:42` · `follow-rpc/client/UserRpcClient.java:50`（注释明说「没有在此处设 deadline」）· `favorite-rpc/client/{UserRpcClient.java:45, ArticleRpcClient.java:50}`（走 `applyTo` ⇒ gRPC 路径 no-op ⇒ **`P2-T02` 修好后自动生效**） |
| **执行目的** | `applyTo` 只在**载体里**有值时才有意义 ⇒ 调用点必须**统一走同一个载体**（不能一半 `applyTo` 一半手搓 `remainingMillis()`） |
| **具体操作** | ① 按 `P2-T01` 的全表，把**所有**出向 stub 调用点**统一**改成 `stub.applyTo(deadlineState)` 形态（载体名以 `P2-T02` 的实现为准）② **清掉手搓 `remainingMillis()` 的那批**（`like-api/LikeRpcClient.java:138` · `comment-api/CommentRpcClient.java:97` · `task-api/TaskRpcClient.java:106` · `article-api/UserRpcClient.java:83`）⇒ 否则**两套语义并存**，修一半 = 混进表述差异 ③ ⚠️ **不要把 `hot-api/HotArticleQueryService.java:102` 算进来** —— 那是**本地查询**读剩余预算，**不是出向 stub** ④ 每处改完补一条断言（**断 deadline 值本身**，不是断「调用了某个方法」） |
| **具体落点** | `service/comment/comment-rpc/**/client/*.java` · `service/like/like-rpc/**/client/*.java` · `service/message/message-rpc/**/client/*.java` · `service/follow/follow-rpc/**/client/*.java` · 各域对应 `src/test/**` |
| **执行主体** | **各域 lane**（comment → G3 · like / message / follow → G2 / G1） |
| **前置条件** | `P2-T01` ∧ `P2-T02`（载体先就位） |
| **并行关系** | 各域之间**可并行**（文件不重叠）⇒ ⚠️ **同模块的 `target/` 不能并发** ⇒ 分波次；**但必须与 `P2-T02` 同一次提交** |
| **写权限** | `service/<域>/**` = **各域 lane**；**不得**碰 `service/common/**` |
| **验收命令** | ```bash# ① 不得再有「手搓 remainingMillis」的出向 stub\ngrep -rn 'remainingMillis()' service/*/*/src/main/java --include='*Client.java' | grep -v 'HotArticleQueryService'\n# ② 域级测试（必须带 -am）\nmvn -o -B -DargLine=-Xmx2g -pl service/comment/comment-rpc,service/like/like-rpc,service/message/message-rpc,service/follow/follow-rpc -am test``` |
| **验收证据** | ① 剩余命中 **0 条**（或逐条有「不是出向 stub」的说明）② 四个域测试 0F/0E ③ 每处改动的**断言变异必红** |
| **完成条件** | 全仓**出向 stub 只有一种 deadline 载体**，且每个调用点有断言 |
| **失败处理** | 某域改不动（载体接口不匹配）⇒ **停下并上报**，**不要**在该域手搓兜底（会造出第二套语义） |
| **状态更新** | 各域台账：`comment/rpc.md` 的 `D8` ⇒ 销号；`like/vs-common.md` 的 `U15` ⇒ 销号（**域写权面 ⇒ 各域自改**） |
| **后续任务** | `P2-T05` · `P2-T06` |

### P2-T04 · 11 个「零 deadline」类补 2000ms 硬上界

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P2-T04` |
| **原始编号** | **`X-6` 轴②**（`docs/PhaseGoal-judge.md:260` §五 X-6 行 · 残余 = 「客户端硬上界 2000ms 未启用」）· **⚫ `E65` 已解绑**（E65 本条 = **✅ 已闭合** —— 继承面经双侧活体四键逐键相等证成 ⇒ ⇒ 挂在 E65 上会让本单指向一条**已闭合**项）· `E38②`（历史来源，保留） |
| **来源** | **`docs/DRIFT/global/vs-common.md:3812`**（**`G4-A4-05`** · §4.11.5「出向 stub 全表」= 21 个类 / 30 处零 deadline ＋ Go 的 `zrpc/config.go:31` 对照；⚠️ **原指针 `:46` 已漂移** —— 那行现在是 E65 的 §1.1 表行，**不是清单**）· **`docs/DRIFT/global/_README.md:430`**（E65 行；⚠️ **原指针 `:391` 已漂移** —— 那行现在是空的）· **`docs/PhaseGoal-judge.md:260`**（§五 X-6 行 = 本条现在的**挂靠点**） |
| **当前状态** | 🔴 **未做**。已点名的类（以 `P2-T01` 重数为准）：`message-rpc/client/UserRpcClient.java:42`（裸注入 stub · `@GrpcClient("user-rpc")`）· `message-rpc/client/AdminRpcClient.java:58`/`:92` · `like-rpc/client/MessageRpcClient.java:85` · `follow-rpc/client/UserRpcClient.java:50` · `user-api/client/UserRpcClient.java:144` · `admin-api/client/AdminRpcClient.java:174` |
| **当前状态（补）** | ⚠️ 「11 个」这个数**本身被订正过**（原写 9 ⇒ 后订正 11）⇒ 以 `P2-T01` 的重数为准 |
| **执行目的** | 「零 deadline」比「不继承请求预算」**更严重** —— **连上界都没有** ⇒ 可无限期挂起 ⇒ 与 Go 的 2000ms 硬上界**直接相反** |
| **具体操作** | ① 对 `P2-T01` 标为「③ 裸 stub / 零上界」的**每一个类**：补一个**默认 2000ms** 的出向上界（与 Go `zrpc/config.go:31` `Timeout int64 json:",default=2000"` 同形）② 上界**可 env 覆盖**（便于压测与故障注入）③ 补断言：**即使入向没有 deadline**，出向也必须带 ≤2000ms 的上界 ④ ⚠️ **`devh 3.1.0.RELEASE` 没有 `deadline` 这个属性** ⇒ `grpc.client.*.deadline` 是**空配** ⇒ **不要**靠它配（台账里那 2 处交叉证据已失效） ⑤ 🟢 **域侧只配参数 ⇒ 不得再造私有 helper** —— 共享层的**唯一实现已在本仓**：`service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java`（带 devh 的 `@GrpcGlobalClientInterceptor` ⇒ 装在**每个**出向 devh 调用上 ⇒ 与 Go 的 `zrpc/client.go:70-71` 同形 ⇒ ⇒ **域侧零代码改动**）⇒ ⇒ 只配 `rag.protection.outbound-deadline.cap-millis` = 该域 Go 的 `Timeout`；⚠️ **`capMillis` 默认 = 0（不启用）**，理由见 `GoStyleProtectionProperties.java:261`（Go 的 2000ms 是**默认值**且各域可覆盖 —— article api 侧是 70000 ⇒ 写成全局默认会让 article **比 Go 严 35 倍**）⇒ ⇒ **必须逐域配**；共享层已补**运行期证据**（`GoStyleOutboundDeadlineClientCapGateTest`：cap 生效 / cap=0 负控 / min 语义 三形态） ⑥ 🔴 **副本数 = 漂移面** —— 仓里**已存在 2 份**域侧私有副本（comment-rpc 139 行 · favorite-rpc 121 行）⇒ ⇒ 再逐域补就是第三、四份 ⇒ ⇒ **正确形态 = 共享层给可复用实现 ＋ 域侧只配参数**；⚠️ 那两份是**既成的漂移面**（**不是**模板）⇒ ⇒ 域侧改造时**删掉它们、改用共享实现** ⇒ ⇒ 共享层已有一道**棘轮守门**（`GoStyleClientCallTimeoutSingleSourceGateTest`）**禁止第三份** |
| **具体落点** | 同 `P2-T03` 的调用点所在类 + 各域 `src/test/**` |
| **执行主体** | **各域 lane**（与 `P2-T03` **同一批提交**）＋ **共享层那半 = G4**（`service/common/**`：唯一实现 ＋ 运行期证据 ＋ 棘轮守门 · **已在共享层就位** ⇒ ⇒ 域侧只剩 yml 与删副本） |
| **前置条件** | `P2-T01` |
| **并行关系** | 与 `P2-T03` **同域同提交**（同一批文件）⇒ 不可拆 |
| **写权限** | `service/<域>/**` = 各域 lane（**共享层那半** = `service/common/**` = **G4**） |
| **验收命令** | ```bash# ① 零上界类归零（脚本判定）\npython3 docs/RoundPlan/round-02/count-outbound-stubs.py --assert-no-zero-deadline\n# ② 域级测试 0F/0E\nmvn -o -B -DargLine=-Xmx2g -pl service/<域>/<域>-rpc -am test``` |
| **验收证据** | ① 「零上界类数」= **0** ② 每个类有断言断「即使无入向 deadline，出向也带 ≤2000ms」③ 变异必红（把上界去掉 ⇒ 断言红） |
| **完成条件** | **零 deadline 的类 = 0**（这是本轮最硬的一条量化判据） |
| **失败处理** | 某个类的 stub 由框架代管、改不了 ⇒ **上报**，并把该类**显式登记**为「零上界残留」⇒ 不得静默放过 |
| **状态更新** | 同 `P2-T03` |
| **后续任务** | `P2-T05` |

### P2-T05 · 守门改造：漂移形态守门 → 等值守门（+ 变异重做）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P2-T05` |
| **原始编号** | `E65` · `E48` · `E62`（守门族） |
| **来源** | `tools/difftest/check-deadline-downstream.py`（docstring ③ 明写「漂移还在 ⇒ 绿」）· `tools/difftest/mutate-deadline-downstream.py` · `docs/DRIFT/global/vs-common.md`（A1 轴） |
| **当前状态** | 🔴 **未做**。当前门 = **漂移形态守门**（钉住「漂移必须长成这个样子」）⇒ 修好 E65 后它**必然转红** |
| **执行目的** | 守门必须与「X-6 成立」对齐 ⇒ **等值守门**（drift 键两侧必须相等）；否则修好了反而红 ⇒ 下一个人会把漂移「修回去」 |
| **具体操作** | ① `check-deadline-downstream.py`：把 drift 键（`failed_fast` / `code` / `outgoing_carrier`）的判据从「**与已登记形态一致**」改成「**两侧相等**」；parity 键（`sql_carrier`）保持不变 ② **保留**「两侧都真跑过」的自证（① 各产出 1 条 `PROBE|` 行 —— `PIT-GLOBAL-054`：退出码 0 ≠ 脚本真跑过 ⇒ 须交 `Δ_start=`/`Δ_end_`，不足即 `exit 5`）③ `mutate-deadline-downstream.py` 变异重做：M1 = 把 `onHalfClose` 的载体绑定去掉 ⇒ 必红 `outgoing_carrier`；M2 = 给下游调用加 deadline ⇒ 必红 `failed_fast` + `code` ④ ⚠️ **变异判定用「集合差分」不用「关键词匹配」**（`PIT-GLOBAL-048`）⑤ ⚠️ **变异必须打在源码上**（`src/main/**`），不能打 `target/classes`（会被资源拷贝覆盖 ⇒ 假绿，`P-HOTRPC-19`）⑥ ⚠️ **变异脚本自身要能编译**（编译不过 ⇒ 测试没跑 ⇒ 读旧 XML ⇒ 假绿，`PIT-GLOBAL-018`） |
| **具体落点** | `tools/difftest/check-deadline-downstream.py` · `tools/difftest/mutate-deadline-downstream.py`（⚠️ **在 `tools/difftest/` 下 ⇒ 属 §6.3 的 27 个计数口径 ⇒ **文件名与数量都不许变** ⇒ 只改内容，不新增文件） |
| **执行主体** | **合并收口执行者（主线）** —— `tools/difftest/` 的**守门清单计数口径**由收口执行者维护 |
| **前置条件** | `P2-T02` ∧ `P2-T03` ∧ `P2-T04` |
| **并行关系** | **必须与 `P2-T02`~`P2-T04` 同一次提交**（否则会出现「代码改了、门还是旧形态 ⇒ 红」） |
| **写权限** | `tools/difftest/**` = **主线**（⚠️ **不得新增 `check-*.py`** ⇒ 会动 27 的计数 ⇒ 制造 B14） |
| **验收命令** | ```bashpython3 tools/difftest/check-deadline-downstream.py      # 改后：drift 键两侧相等 ⇒ EXIT=0\npython3 tools/difftest/mutate-deadline-downstream.py  # 变异 ⇒ 精确转红\npython3 tools/difftest/check-phasegoal-consistency.py   # 守门数量没变 ⇒ §6.3 无需改``` |
| **验收证据** | ① 等值门 EXIT=0 ② 变异 **2/2 精确转红**（红的方法集合必须与预期**完全相等** ⇒ 多红一条 = 判据间意外耦合）③ §6.3 的命令计数**未变** |
| **完成条件** | **守门与「X-6 成立」同义** ⇒ 漂移复现时红、漂移修好时绿 |
| **失败处理** | 变异不红 ⇒ **先怀疑工具，再怀疑世界，最后才怀疑被测代码**（`P-15` 的排查顺序：① 变异编译进去了吗（`javap` 看字节码）② 脚本改对地方了吗 ③ 断言敏感吗） |
| **状态更新** | `docs/DRIFT/global/vs-common.md` 的 E65 行 + A1 轴（「半开」→ 已闭合） |
| **后续任务** | `P2-T06` · `P6-T01`（收口回归会复跑它） |

### P2-T06 · E66：先补双侧活体证据，再定级，再落地或登记

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P2-T06` |
| **原始编号** | `E66` · `DRIFT-ARPC-010`（**同一条，不要当两条**）· `DRIFT-USERAPI-013`（相邻）· comment `D9` · like `FB-05` · task `FB-06` |
| **来源** | `docs/DRIFT/global/vs-common.md:31`/`:47` · `docs/DRIFT/global/_README.md:392` · `docs/DRIFT/_cross/domain-common-lanes.md:132` · `docs/DRIFT/like/vs-common.md:109-119`（FB-05）· `docs/DRIFT/task/vs-common.md:148`/`:212`（FB-06）· `docs/DRIFT/users/admin/rpc.md:3000` |
| **当前状态** | 🔴 **OPEN · 转立项**；**证据仅 L1**（源码级逐条对照）；**无阶段归属定级**；**judge 里出现 0 次** |
| **当前事实** | ① RAG 侧 `service/common` 里**没有任何 Redis 期限载体** ⇒ `comment-rpc/service/CommentCacheService.java` 的全部 `redis.opsForValue()…`（**含写** `:279` `set`）**零 deadline 检查** ② Go 侧**全部 ctx-aware** ⇒ 到点即止 ③ 与 E65 **同源**（RAG 没有 ctx 传播 ⇒ 非 SQL 的 I/O 全部不感知 deadline）④ 处方已探明：Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline ⇒ **零域侧改动**；⚠️ **硬约束**：Boot 4 `LettuceConnectionConfiguration.createClientOptions` 字节码顺序 = offset 146 **先**写 `timeoutOptions`（且是**硬编码 `TimeoutOptions.enabled()`**，**不是** `spring.data.redis.timeout`）→ offset 151 **才**应用 customizer ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖运维配置** |
| **阶段一影响** | **② 缺验收证据** ⇒ 按 Jason 2026-10-29 裁定：**与 E65 同轮立项，但先补双侧活体证据再定级** |
| **执行目的** | E65 已证明「RAG 没有 ctx 传播 ⇒ 非 SQL 的 I/O 全部不感知 deadline」⇒ E66 是**同一根因的另一半** ⇒ 但**证据强度只有 L1**，不能拿源码级推断当活体结论 |
| **具体操作** | ① **先取证**：做双侧活体探针（Go 侧走真源 Redis 调用路径，RAG 侧走**生产类** + 真 Redis）⇒ 落 `tools/<kind>/<域>/`（⚠️ **必须落仓**，`/tmp` 不算证据）② 探针必须**自证跑过**（`Δ_start=`/`Δ_end_`）+ **变异必红** ③ 依证据按 `twoStage.md` §十 定级（A/B/C/D + P0~P3）④ 若定 A/B ⇒ 落地（Lettuce 期限载体 + **规避 Boot 4 字节码顺序硬约束** ⇒ 必须自证「`spring.data.redis.timeout` 仍生效」）⑤ 若定 C ⇒ 登记进交付物 5 暂缓清单（**11 字段齐全**）⑥ **销账时注意**：`E66` 与 `DRIFT-ARPC-010` 是**同一条** ⇒ 一条销号，**别当两条** |
| **具体落点** | 探针：`tools/<kind>/<域>/`（**域内探针目录** ⇒ 写权归对应域 lane）· 共享层载体：`service/common/**`（G4）· 域侧调用点：各域 redis 调用处（各域 lane） |
| **执行主体** | **G4**（共享层载体 + 定级）＋ **各域 lane**（探针 + 调用点） |
| **前置条件** | `P2-T02`（载体已就位 ⇒ Redis 期限才有地方挂） |
| **并行关系** | 取证可与 `P2-T03`/`P2-T04` 并行；**落地**必须与 `P2-T02` 同一次提交 |
| **写权限** | `service/common/**` = G4 · `service/<域>/**` = 各域 lane · `tools/<kind>/<域>/` = 对应域 lane |
| **验收命令** | ```bash# ① 双侧活体探针（在仓内）\ngit ls-files tools/ | grep -E 'redis|deadline' \n# ② 变异必红\n# ③ 若落地：断言 spring.data.redis.timeout 仍生效（Boot 4 硬约束）\n# ④ 域级测试 0F/0E``` |
| **验收证据** | ① 探针**在版本控制内** ② 变异精确转红 ③ **定级结论写进台账**（含 A/B/C/D + 严重程度 + 依据 + 日期）④ 若落地 ⇒ 「运维 timeout 未被覆盖」的断言存在 |
| **完成条件** | **E66 有明确定级结论**，且「落地」或「登记」**二选一必须写明**（不能既不落地也不登记） |
| **失败处理** | 探针做不出双侧活体（环境/时序）⇒ **登记为环境受阻 + 写解除条件** ⇒ ⚠️ **绝不写「已对齐」**（`AGENTS.md` §3.3.1） |
| **状态更新** | `docs/DRIFT/global/vs-common.md`（E66 行 + A1 轴）· 各域台账的 `FB-05`/`FB-06`/`DRIFT-ARPC-010` ⇒ **各域自改** |
| **后续任务** | `P3-T01`（P0 #4 行改判要写 E66 的结论）· `P7-T01` |

---

## §5.4 具体验收命令（本轮整体）

```bash
cd "/Users/pr/Documents/java&golang/RAG"

# ── ① 守门（等值门；改前应红、改后应绿）────────────────────
python3 tools/difftest/check-deadline-downstream.py
python3 tools/difftest/mutate-deadline-downstream.py

# ── ② 落点表的自检（零上界类 = 0）────────────────────────
python3 docs/RoundPlan/round-02/count-outbound-stubs.py --assert-no-zero-deadline

# ── ③ 共享层 + 四域测试（必须带 -am）──────────────────────
mvn -o -B -DargLine=-Xmx2g -pl service/common -am test
mvn -o -B -DargLine=-Xmx2g -pl service/comment/comment-rpc,service/like/like-rpc,\
service/message/message-rpc,service/follow/follow-rpc,service/users/user/user-api,\
service/users/admin/admin-api -am test

# ── ④ 双侧活体（两侧同时跑 ⇒ 逐字节比对）─────────────────
bash tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh
bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh

# ── ⑤ 基线自洽（守门数量没变 ⇒ §6.3 无需改）──────────────
python3 tools/difftest/check-phasegoal-consistency.py

# ── ⑥ bash 3.2 变量名坑（新增脚本必查）───────────────────
python3 tools/difftest/scan-bash-legacy-vars.py
```

---

## §5.5 可客观判定的完成条件

| # | 判据 | 怎么判 |
|---|---|---|
| 1 | 落点表齐全 | `outbound-stub-inventory.md` 每条有 `文件:行号` + 三态 + Go 对照；**三态之和 = 总数** |
| 2 | **零 deadline 类 = 0** | `count-outbound-stubs.py --assert-no-zero-deadline` ⇒ **0** |
| 3 | 出向 stub 只有一种载体 | `grep -rn 'remainingMillis()' service/*/*/src/main/java --include='*Client.java'` ⇒ **0 命中**（`HotArticleQueryService` 除外） |
| 4 | **两侧 `failed_fast` / `code` / `outgoing_carrier` 相等** | 双侧活体探针输出**逐项相等**（Go 为基准） |
| 5 | 守门与「X-6 成立」同义 | 等值门 EXIT=0；**变异 2/2 精确转红** |
| 6 | 探针自证 | 输出含 `Δ_start=` / `Δ_end_`；不足 ⇒ `exit 5`（不许 exit 0） |
| 7 | E66 有定级 | 台账里有 A/B/C/D + 严重程度 + 依据 + 日期；**「落地」或「登记」二选一写明** |
| 8 | 回归绿 | 共享层 + 六个模块 `mvn test` ⇒ **0F / 0E** |
| 9 | §6.3 计数未变 | `check-phasegoal-consistency.py` EXIT=0（守门**数量**不变 ⇒ 6.3 无需改） |
| 10 | **不能算完成的情况** | 只改了共享层没改调用点（⇒ 一半修一半没修）· 变异没红就宣布通过 · 把「门变红」当回归修回去 · 引用台账的「21/30」而不重数 · 把 `hot-api/HotArticleQueryService.java:102` 当出向 stub 改掉 |

---

## §5.6 本轮结束检查

- [ ] **P2-T01** 出向 stub 全表（三态分类自洽 + Go 对照 + 零上界类逐个有名）
- [ ] **P2-T02** 共享层载体打通（`applyTo` 在 gRPC 路径不再是 no-op）＋ 装配层断言**变异必红**
- [ ] **P2-T03** 各域裸 stub 统一走 `applyTo`；手搓 `remainingMillis()` 归零
- [ ] **P2-T04** **零 deadline 类 = 0**；每个类有「即使无入向 deadline 也带 ≤2000ms」的断言
- [ ] **P2-T05** 守门改等值门；变异 2/2 精确转红；§6.3 计数未变
- [ ] **P2-T06** E66 有定级 + 落地或登记；探针在版本控制内 + 变异必红
- [ ] 证据路径：落点表 `docs/RoundPlan/round-02/outbound-stub-inventory.md` · 守门输出落 `/tmp` 留档并摘进台账
- [ ] 原始编号 → 最新状态：`E65` → 待销号 · `E38②` 第③层 → 待归零 · `E66` → 待定级 · comment `D8`/`D9` · like `U15`/`FB-05` · task `FB-06` · users `DRIFT-ARPC-010`
- [ ] 未完成任务及原因：（逐条写）
- [ ] 被阻塞的后续任务：`P3-T01`（P0 #4 行）· `P3-T03`（X-6 行）· `P3-T04`（C-2/C-3）—— **全部等本轮的活体证据**
- [ ] 本轮新增问题及阶段一分类：（按 §十 定级 ⇒ 走 `CT-02`）
- [ ] **是否满足进入 Round 3 的条件**：**是** —— 前提是「两侧相等」的活体证据已产出；否则 `Round 3` **不得改判**（会再次用旧口径写 ✅）
- [ ] ⚠️ **本轮禁止**：改 Go 真源 · **架构级改造 / 超时 interrupt**（第四十六轮已否决 ⇒ 会新造漂移）· 改根级文件 · 改 `PhaseGoal-judge.md` · **新增 `tools/difftest/check-*.py`**（会动 27 的计数 ⇒ B14）· **分两次提交落共享层与域侧**（⇒ 中间态更危险）

---

## 防错约束（Pitfalls 转执行约束）

| PIT 编号 | 出处（规范位置 · 逐条可核） | 规则（摘自该 PIT 标题原文） | 约束绑定的任务 ID |
|---|---|---|---|
| `PIT-GLOBAL-054` | `docs/Pitfalls/global/cross-domain.md:1962` | 🔴 **「接进编排」≠「有了闸门」** —— 六种假绿（判定只写在注释里 / 退出码是意外得来的 / 守门分支短路 / 端口与就绪不跟随约定 / **施加侧空转** / **阴性观测误诊**） | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-048` | `docs/Pitfalls/global/cross-domain.md:1719` | 🔴 **变异判据本身也会假阴性 ⇒ 用「集合差分」，不要用「关键词匹配」** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-040` | `docs/Pitfalls/global/cross-domain.md:1425` | 🔴 **变异无效的五种形态**（规则被掩护 / 对照 flaky / 变异打错层 / 阳性对照依赖分支 / 台账引述错路径） | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-018` | `docs/Pitfalls/global/cross-domain.md:512` | 🔴 **变异脚本的两种「假绿」：① 编译不过 ⇒ 测试没跑 ⇒ 读到旧 XML；② 红在更早的断言上 ⇒ 目标断言没被执行到** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-019` | `docs/Pitfalls/global/cross-domain.md:576` | 🔴 **改「上游模块的资源」做变异，而只跑下游 ⇒ 变异到不了运行代码 ⇒ 报成 GREEN（假阴性）** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-016` | `docs/Pitfalls/global/cross-domain.md:414` | 🔴 **共享层新增方法后，单独跑下游模块 ⇒ `NoSuchMethodError`**（不是编译错误！） | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-033` | `docs/Pitfalls/global/cross-domain.md:1149` | 守门的**期望值**可能就落在**已登记的偏离范围**里 ⇒ 修一半 = 把「表述差异」混进「行为漂移」 | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-031` | `docs/Pitfalls/global/cross-domain.md:1042` | 🔴 **守门断言的字符串可能同时出现在被测文件的注释里 ⇒ 断言恒真 ⇒ 变异测不出来** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-061` | `docs/Pitfalls/global/cross-domain.md:2310` | 🔴 **「去噪」是那条判据的「计账规则」，不是「放弃检查」** ⇒ 让**另一条判据**失去覆盖 ⇒ 变异样本被相邻行吃掉 ⇒ **假绿** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-026` | `docs/Pitfalls/global/cross-domain.md:801` | 🔴 **`GlobalOpenTelemetry.set(sdk)` 存的是「包装器」不是 sdk ⇒ 断言 `get() == sdk` 永远不成立** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-036` | `docs/Pitfalls/global/cross-domain.md:1220` | Spring 的 `@DynamicPropertySource` **不进** context cache key ⇒ 靠「properties 恰好不同」隔离上下文是**侥幸** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-020` | `docs/DRIFT/global/lanes/_PROTOCOL.md:125` | 变异只准在**隔离工作树**里跑 ⇒ 提交前 `git diff` 逐个确认变异已还原 | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-028` | `docs/Pitfalls/global/cross-domain.md:912` | 🔴 **slow 阈值的「配置被忽略」是可静默发生的退化 ⇒ 守门的「占用时长」必须 < Go 的兜底阈值** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-030` | `docs/Pitfalls/global/cross-domain.md:995` | 🔴 **守门只扫一半的模块 ⇒ 另一半的缺陷只能靠「手工盘点」发现 ⇒ 手工必漏** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-014` | `docs/Pitfalls/global/cross-domain.md:288` | 🔴 **变异测试报「没转红」时，先排除「测试压根没跑」** —— 否则会误删一个好守门 | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
| `PIT-GLOBAL-021` | `docs/Pitfalls/global/cross-domain.md:735` | 🔴 **变异判定只认 surefire 的 XML —— stdout 的 `-- in` 行在类有 `@DisplayName` 时是「显示名」不是 FQN** | 约束本轮任务：`P2-T01` · `P2-T02` · `P2-T03` · `P2-T04` · `P2-T05` · `P2-T06` |
