# W21 · 同源判定（11 条）

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `E7` | `follow` | 不同的事 | follow 这条讲「双侧活体对拍本轮未复验、follow 域侧探针源头已消失」（`DRIFT/follow/stage1-admission.md:382`）；global 同号 E7 讲「CD-1 三进程同库共存（Flyway 账本隔离）」（`DRIFT/global/_README.md:338`）⇒ 现象与落点都不同 ⇒ 纯撞号 | （未派工 ⇒ 无本体）· 同号对照 = `DRIFT/global/_README.md:338`（本号在 `DRIFT/users/stage1-admission.md:509` 又讲第三件事＝follow 域自己的 E4） |
| `S1-FOLLOW-004` | `follow` | 同一件事 | follow 这条讲「根 `docker-compose.yaml` 只编排 user-rpc/user-api ⇒ follow-rpc/follow-api 无编排入口」；users 档 `:523` 是同一条的抄录（「域外上报」），两处同指根级 compose 缺 follow 入口 ⇒ 同一件事 | （未派工 ⇒ 无本体）· 实际本体 = `DRIFT/follow/stage1-admission.md:145` ／ `:394` ／ `:537` |
| `E3` | `follow` | 不同的事 | follow 这条讲「0 个落仓双侧对拍入口、`/tmp` 探针源头已消失」（`DRIFT/follow/stage1-admission.md:378`）；users 同号 E3 讲「`follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口」（`DRIFT/users/stage1-admission.md:505`）⇒ 现象与落点都不同 ⇒ 纯撞号（⚠️ users 这条的内容实际等于 follow 域自己的 **E2**） | （未派工 ⇒ 无本体）· 同号对照 = `DRIFT/users/stage1-admission.md:505` |
| `E2` | `follow` | 不同的事 | follow 这条讲「`follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口」（`DRIFT/follow/stage1-admission.md:377`）；global 同号 E2 讲「T-11 trace 导出链路在 RAG 侧整体缺失」（`DRIFT/global/_README.md:333`）⇒ 现象与落点都不同 ⇒ 纯撞号 | （未派工 ⇒ 无本体）· 同号对照 = `DRIFT/global/_README.md:333` |
| `DRIFT-G1-001` | `follow` | 同一件事 | follow §7.1 讲「`NACOS_GROUP` 对 `follow-rpc` 写死无效」，并自述「完整证据链见 users 档 §7（同一轮、同一 A/B 实验、同一批进程）· 本节只记 follow 侧受影响的部分」；users §7.1 讲同一条根因（表格里含 follow-rpc 那行）⇒ 同一件事 | （未派工 ⇒ 无本体）· 实际本体 = `DRIFT/users/vs-common.md:153`（follow 侧 = `DRIFT/follow/vs-common.md:174`） |
| `DRIFT-G1-002` | `follow` | 同一件事 | follow §7.2 讲「`follow-api` 缺 `immediate-connect-timeout` ⇒ 与 Go `MustNewClient` panic 语义不一致」；users §7.2 讲同一条（覆盖 user-api／follow-api 两个 api，同 Go `MustNewClient` 出处、同一活体实证）⇒ 同一件事 | （未派工 ⇒ 无本体）· 实际本体 = `DRIFT/users/vs-common.md:190`（follow 侧 = `DRIFT/follow/vs-common.md:200`） |
| `S1-FOLLOW-013` | `follow` | 同一件事 | follow 侧 `vs-common.md:823` 与 users 档 `:530` 讲的都只有一件事——`FollowApiFailurePathMatrixTest.java:417` 的断言消息写「全长 20 字节」（实测 19）⇒ 同一文件同一行同一错数 ⇒ 同一件事 | （未派工 ⇒ 无本体）· 实际本体 = `DRIFT/follow/vs-common.md:823`（跨域抄录 = `DRIFT/users/stage1-admission.md:530`） |
| `FB-03` | `task` | 同一件事 | task 这条讲「A2 保护链缺「层构成」只读查询入口 ⇒ 域级守门无法分别断言 breaker/shedding」，其 Go 出处（go-zero `zrpc/server.go:144-167`）、RAG 侧现状（`GoStyleRpcProtectionChain.java:61-78`）与修法（暴露只读链层构成入口）与 global 本体逐项同款 ⇒ 同一张单，本段只覆盖 task 域那半 | `DRIFT/global/vs-common.md:402` |
| `FB-04` | `task` | 同一件事 | task 这条是「FB-04 对本域 = N/A」回执（commit `853767b`），讲的正是 global 那张单（A9：`onHalfClose` 里让 `makeCurrent()` 的 scope 包住 handler 执行期 ⇒ 业务 handler 拿得到当前 server span）⇒ 同一张单，本段只覆盖「task 域两侧 0 命中 ⇒ 不受影响」这一面 | （未派工 ⇒ 无本体）· 本体 = `DRIFT/global/vs-common.md:910`（派单出处 = like 域 `DRIFT/like/vs-common.md:94`） |
| `DRIFT-MSG-V-026` | `task` | 同一件事 | task 这条讲「熔断/降载链必须真挂到 gRPC server」，与 message 域本体那条跨组件不变量（10/10 个 rpc 组件的 `GoStyleRpcProtectionChain.build(...)` 消费点，含 `TaskRpcApplication.java:138`）是同一件事；task 段把共享层那半明确拆给本域 §4 的 FB-03 ⇒ 同一件事（task 侧 🟡 是旧快照：域内守门已由 `fad4c63`／G4 合并后升级到 5 条） | （未派工 ⇒ 无本体）· 本体 = `DRIFT/message/rpc.md:1008`（task 侧 = `DRIFT/task/vs-common.md:34`） |
| `FB-02` | `task` | 同一件事 | task 这条讲「A3 form 闸门开关判据缺「语义可达性」维度 ⇒ 本域多出一条 Go 没有的拒绝」；global 本体讲的是同一个判据缺陷（共享层把「off 的依据」写成「写权范围」而非 Go 侧是否真的解析 form/query），且其逐域取证表里 task 行正是「命中数 = 0 ⇒ 语义答案 = off」⇒ 同一张单，本段只覆盖 task 域那半 | `DRIFT/global/vs-common.md:373` |

## 口径说明（3 条）

1. **本体怎么定的**：`docs/DRIFT/_cross/grading/_lanes.json`（第 1／2 轮派工）＋ `/tmp/rag-verify/lane-briefs/round3-body.json`（canonical body 映射）⇒ 本 lane 11 条里 **2 条有本体**（`FB-03` → global:402 · `FB-02` → global:373）· **9 条「未派工 ⇒ 无本体」**；对这 9 条，改以**同号在别域的登记**作对照（对照点已逐条写进「本体位置」列）。
2. **只读台账、不读 Go**：本轮判据是**台账文本本身讲的是什么**（落点 ／ 组件 ／ 现象）⇒ 全部对照基于 `docs/DRIFT/**` 正文，未打开 Go 真源。
3. **合并时的覆盖边界**：判「同一件事」的 8 条里，`FB-04`／`DRIFT-MSG-V-026`／`FB-03`／`FB-02` 四条只覆盖**域内那一半**（适用性回执 · 域内登记 · 域侧守门升级 · 域内取证）⇒ 合并成一条时**不得**拿域内那半的状态覆盖本体整条，否则会反向制造假缺口。
