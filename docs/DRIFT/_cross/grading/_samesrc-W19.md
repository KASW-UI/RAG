# W19 · 同源判定（23 条）

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `E65` | `global` | 同一件事 | global 这条讲「rpc handler 路径的出向 gRPC 调用不继承请求 deadline」（落点 `service/common` 的 `RpcInterceptor.onHalfClose` ＋ `GoStyleRequestDeadline`），而 comment `D8`／favorite §37／users §4 三处同号条目逐条自述「**＝ global E65**」⇒ 同一件事，global 即本体 | （未派工 ⇒ 无本体）· 本体＝global `DRIFT/global/_README.md:430`；引用方＝comment `DRIFT/comment/rpc.md:257`／favorite `DRIFT/favorite/rpc.md:3376`／users `DRIFT/users/admin/rpc.md:3699` |
| `E21` | `global` | 同一件事 | global 这条讲「`grpc-inprocess` 是 `test` scope ⇒ 运行时 classpath 上整个 artifact 消失」（落点 `service/common` 各 rpc pom），favorite 同号行明写「已修（**global L12/L13**）」⇒ 同一件事，global 即本体 | （未派工 ⇒ 无本体）· 本体＝global `DRIFT/global/_README.md:369`；引用方＝favorite `DRIFT/favorite/vs-common.md:27` |
| `E38` | `global` | 同一件事 | global 这条讲「超时未继承客户端 deadline」（落点 `service/common` 的 `RpcInterceptor`），users 同号条目的标题即「`global` 域 **E38 第①层**」、comment 侧标「只读指针（**归属 global**）」⇒ 同一件事，global 即本体 | （未派工 ⇒ 无本体）· 本体＝global `DRIFT/global/_README.md:388`；引用方＝users `DRIFT/users/user/rpc.md:832`／`DRIFT/users/admin/rpc.md:3699`／comment `DRIFT/comment/vs-common.md:28` |
| `E59` | `global` | 同一件事 | global 这条讲「message 域把 deadline 的原始错误改写成 `INTERNAL` ＋ `ERROR_DB_UPDATE`」，message 同号章节标题即「主会话转达：**global 域 E59**」、like 同号行复验的也是同一个 `MessageDomainService` 守门 ⇒ 同一件事，global 即本体 | （未派工 ⇒ 无本体）· 本体＝global `DRIFT/global/_README.md:447`；引用方＝message `DRIFT/message/rpc.md:2810`／like `DRIFT/like/vs-common.md:1498` |
| `E66` | `global` | 同一件事 | global 这条讲「Redis 调用不感知 deadline ⇒ 缓存读写照跑不误（含写）」，本体 comment 段的标题即「D9（**＝ global E66**）」且落点同为「`service/common` 无任何 Redis 期限载体」⇒ 同一件事 | `DRIFT/comment/rpc.md:290` |
| `E68` | `global` | 同一件事 | global 这条讲「共享复核器第 4 层只能判行号越界 ⇒ 判不出行号合法但指错文件」，本体 favorite 行讲的正是同一判据（共享工具第 4+ 层补判据、favorite 域抓出 1 条）⇒ 同一件事 | `DRIFT/favorite/vs-common.md:30` |
| `T-6` | `global` | 同一件事 | global 与本体讲的同为 message 域 `-010`「成功／失败响应的 `Content-Type` 缺 `charset`」，落点同为 `service/common` 的 Spring JSON converter，global 版只是把归因订正为「不是 `GoStyleRouterErrorResponses`」⇒ 同一件事 | `DRIFT/common/alignment-2026-10-23.md:693` |
| `T-7` | `global` | 同一件事 | global 与本体讲的同为 message 域 `-011`「状态行 reason phrase 差异」，落点同为容器层 `Tomcat Http11OutputBuffer`（`service/common` 够不着）⇒ 同一件事 | `DRIFT/common/alignment-2026-10-23.md:694` |
| `E60` | `global` | 同一件事 | global 这条讲「like 域 `currentUserLikeTotal` 双失败时少传 error」，like 同号章节标题即「主会话转达：**global 域 E60**」、落点同为 `LikeDomainService.java:765-771` ⇒ 同一件事，global 即本体 | （未派工 ⇒ 无本体）· 本体＝global `DRIFT/global/_README.md:448`；引用方＝like `DRIFT/like/rpc.md:1597`／`DRIFT/like/vs-common.md:1497` |
| `E62` | `global` | 同一件事 | global 这条讲「E46 的毫秒精度在连接池代理路径上不生效 ⇒ 退回秒级向上取整」，本体 favorite 段的标题即「核 **`global` 第四十一轮 E62**」、落点同为 `DeadlineQueryTimeoutInjector` ⇒ 同一件事 | `DRIFT/favorite/rpc.md:3111` |
| `DRIFT-SF-001` | `global` | 同一件事 | global §4.14 讲「`DRIFT-SF-001`：RAG 侧纯墙钟 ⇒ 与 Go 的单调钟逐项同构」，本体 common 域同号行讲的是同一现象同一落点（`Snowflake.java` vs `snowflake.go` ⇒ 墙钟回拨产重复 ID）⇒ 同一件事 | （未派工 ⇒ 无本体）· 本体＝common `DRIFT/common/alignment-2026-10-23.md:420`（＋`:870` 落码记录）；global 侧＝`DRIFT/global/vs-common.md:4155` |
| `C-3` | `global` | 同一件事 | global §4.10.17 是「G3 的 C-3 回执 —— comment 域 6 个业务 span 闭环」，本体 comment 行讲同一件事（新增 `CommentSpans` ＋ 6 个方法拆 `doXxx` 使 span 覆盖整个 logic）⇒ 同一件事（global 这段只对应 comment 同号三处登记中的 `:59` 那一面） | `DRIFT/comment/vs-common.md:59` |
| `DRIFT-CF-018` | `global` | 同一件事 | global §4.18.1 讲「`Dispatcher` 停止能力归调用方（Go-faithful）」，本体 common 域同号行讲同一现象（阻塞版 `run(..., once=false)` 交不出句柄 ⇒ favorite 域进程停不下来）⇒ 同一件事（global 只覆盖共享层半边，favorite 域半边仍开着） | `DRIFT/common/alignment-2026-10-23.md:661` |
| `FB-04` | `global` | 同一件事 | global §4.10.9 讲「G2 派单 FB-04（A9）—— 业务 handler 执行期能拿到当前 server span」，like 域同号是这张单的派单出处、message／task 同号是「对本域＝N/A」回执 ⇒ 三者是同一张单 | （未派工 ⇒ 无本体）· 本体＝global `DRIFT/global/vs-common.md:910`；派单出处＝like `DRIFT/like/vs-common.md:179`；回执＝message `DRIFT/message/vs-common.md:158`／task `DRIFT/task/vs-common.md:228` |
| `PIT-FAV-RPC-038` | `global` | 同一件事 | global §4.10.49 是对 favorite 坑档 `PIT-FAV-RPC-038` ⑤ 的跨域采纳（引用损坏形态只用可见转义写法）＋同一批 10 个 U+FFFD 的归因订正，坑档 ⑤ 自身就写明「G4 引用病症样本 ⇒ 自己台账多了 10 个真 U+FFFD」⇒ 同一件事 | （未派工 ⇒ 无本体）· 本体＝favorite 坑档 `docs/Pitfalls/favorite/rpc.md:1908`；global 侧＝`DRIFT/global/vs-common.md:2709` |
| `PITFALL-FOLLOW-005` | `global` | 同一件事 | global §4.10.1 讲「G1 派单 #8 —— 已落码：关闭路径不得触发 web 类型的类加载」，本体 follow 域讲同一现象（纯 gRPC 进程关停抛 `NoClassDefFoundError: jakarta/servlet/Filter` ⇒ `RpcInterceptor.shutdown()` 不执行 ⇒ RPC 池没回收）且落点同为 `service/common` ⇒ 同一件事 | `DRIFT/follow/vs-common.md:300` |
| `T-17` | `global` | 同一件事 | `T-17` 全仓只在 global 域出现（`common-tails.md:612` 正文标题 ＋ `:643` 汇总表行，是同一节的两种写法），派生层无第二个域的同号条目 ⇒ 自成同源组、无跨域副本可合并（⚠️ 它讲的现象与 common 域 `DRIFT-CF-003` 相同但**编号不同** ⇒ 不是同编号同源） | （未派工 ⇒ 无本体）· 全仓无第二个 `T-17` |
| `DRIFT-HOTAPI-030` | `global` | 同一件事 | global `cross-domain.md` 讲 CD-6「Tomcat 数『总参数个数』vs Go 数『非空值个数』／段数上限」，本体 hot 域同号条目与 article／comment／follow 域同号行讲的全是这一现象（共享层 6 个类 ＋ 每域一行 yaml）⇒ 同一件事 | （未派工 ⇒ 无本体）· 本体＝hot `DRIFT/hot/api.md:2260`（＋`DRIFT/hot/vs-common.md:32`）；引用方＝article `DRIFT/article/vs-common.md:26`／comment `DRIFT/comment/vs-common.md:31`／follow `DRIFT/follow/vs-common.md:28` |
| `N-1` | `global` | 不同的事 | global 这条讲「favorite 域的 Go 基线是『测试夹具基线』⇒ L3 的『触发器 2 ≡ 2』需订正为『Go 生产 0 个触发器』」（落点 `ddl-parity.sh` 的 favorite 分支），users 域同号讲的是「鉴权失败的响应体形状（401 body）」（落点 JWT 验签路径）⇒ 现象与落点都不同 ⇒ 纯编号撞号，保持拆开 | （未派工 ⇒ 无本体）· 同号对照＝users `docs/Intentional_Deviations/users/admin/_shared.md:65` |
| `N-2` | `global` | 不同的事 | global 这条讲「comment 域的 `003_comment_fact_delivery.sql` 全仓 0 引用 ⇒ Go 生产也没有那个触发器」，users 域同号讲的是「Go 真源的中文错误消息是乱码字节、RAG 保留正确中文」⇒ 现象与落点都不同 ⇒ 纯编号撞号，保持拆开 | （未派工 ⇒ 无本体）· 同号对照＝users `docs/Intentional_Deviations/users/user/rpc.md:18`（＋`DRIFT/users/user/rpc.md:1047`） |
| `N-3` | `global` | 不同的事 | global 这条讲「Go 生产代码从不创建任何触发器（`CREATE TRIGGER` 在非测试 Go 代码里 0 命中）」，users 域同号讲的是「`uid=0` 被归类为业务不存在」（已撤销留档）⇒ 现象与落点都不同 ⇒ 纯编号撞号，保持拆开 | （未派工 ⇒ 无本体）· 同号对照＝users `docs/Intentional_Deviations/users/admin/_shared.md:111` |
| `N-4` | `global` | 不同的事 | global 这条讲「`pg_get_triggerdef` 的文本原来被查出来却从不比对」，users 域同号讲的是「`operator_id` 不恒为 0」（已签字偏离）⇒ 现象与落点都不同 ⇒ 纯编号撞号，保持拆开 | （未派工 ⇒ 无本体）· 同号对照＝users `docs/Intentional_Deviations/users/admin/_shared.md:84` |
| `SD-HOT-API-006` | `global` | 同一件事 | global `L1-delivery.md` §13.2 讲的就是这条签字项「`Content-Type` 的 `charset=utf-8` 参数」被撤销的**理由性质**（不可逐字节复刻成立 ⇒ 撤销属纯用户决策），与 hot 域签字档同号条目是同一件事 ⇒ 同一件事 | （未派工 ⇒ 无本体）· 本体＝hot 签字档 `docs/Intentional_Deviations/hot/api.md:189`（＋`:28`／`:45`） |

---

### 判定口径（本 lane 自查）

- **本体怎么定的**：任务书清单给的「本体位置」＝派工清单 `docs/DRIFT/_cross/grading/_lanes.json`（第 1／2 轮派工）＋ `/tmp/rag-verify/lane-briefs/round3-body.json`（canonical body 映射）⇒ 本 lane 23 条里 **8 条有本体**（`E66`／`E68`／`T-6`／`T-7`／`E62`／`C-3`／`DRIFT-CF-018`／`PITFALL-FOLLOW-005`），**15 条「未派工 ⇒ 无本体」**；对这 15 条，改以**同编号在其它域的登记**作对照（对照点已逐条写进「本体位置」列）。
- **「同一件事」的判据**：同现象 ＋ 同落点 ＋ 同组件 ⇒ 判为同一件事，共 **19 条**。其中 **11 条的 global 登记就是本体**（`E65`／`E21`／`E38`／`E59`／`E60`／`FB-04`／`DRIFT-SF-001`／`PIT-FAV-RPC-038`／`DRIFT-HOTAPI-030`／`SD-HOT-API-006`／`T-17`），别域条目逐条自述「＝ global E65」之类 ⇒ **global 是本体、别域是引用** ⇒ 拆开后应当**以 global 这份为准**。
- **「不同的事」＝ 纯编号撞号**，共 **4 条**（`N-1`／`N-2`／`N-3`／`N-4`）：global 的 `N-1~N-4` 是 L10 lane 的 DDL 触发器／基线新发现（`lanes/L10-ddldeep.md:332-335`），users 域的 `N-1~N-4` 是签字档里的鉴权 body 形状／中文乱码／`uid=0`／`operator_id` ⇒ 现象与落点全不同 ⇒ **保持拆开**。
- **合并时的覆盖边界（3 条）**：`C-3`（global 只覆盖 comment 同号三处登记中的 `:59` 那一面）、`DRIFT-CF-018`（global 只覆盖共享层半边，favorite 域半边仍开）、`PIT-FAV-RPC-038`（global 只覆盖 ⑤ 与归因订正那一面）⇒ 合并成一条时**不得**拿 global 这半的状态覆盖别域那半。
- **只读台账、不读 Go**：本轮判据是**台账文本本身讲的是什么**（落点 ／ 组件 ／ 现象）⇒ 全部对照基于 `docs/DRIFT/**` ＋ `docs/Pitfalls/**` ＋ `docs/Intentional_Deviations/**` 正文，未打开 Go 真源。
- **计数**：同一件事 **19** 条 · 不同的事 **4** 条 · 无法判定 **0** 条。
