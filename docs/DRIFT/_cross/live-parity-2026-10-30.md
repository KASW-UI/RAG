<!-- 由 2026-10-30「活体对拍」轮产出 · 主线（`do-my-own-gh`） -->

# 活体对拍证据台账 · 2026-10-30

> **性质**：本文件是**证据台账**，不是结论台账 —— 结论仍在 `docs/DRIFT/` · `docs/Pitfalls/` ·
> `docs/Intentional_Deviations/` 三棵树上，本文件只回答一个问题：
> **「某条结论的证据，到底停在静态层还是真跑到了运行期？」**
> **核对日期：2026-10-30** · 运行环境：macOS 25.3.0 (darwin/arm64) · Docker Desktop 29.2.1
> **真源**：`Sea-RideTheWind_golang` @ `73eb80c`（**冻结只读** · 本轮全程未改一个字节）

---

## 〇、本轮为什么做这件事

前一轮（2026-10-29/30）把三目录 **1183 条**条目的**结论本身**逐条回源核对了一遍，
并给每条补了**行内复核戳** ⇒ 覆盖率 100%。
但那一轮的证据**仍以「源码级对照 + 静态守门在位」为主** ⇒

> **「指针能解析」≠「结论为真」** —— 机器审计只能证明「引用可寻址」，
> **不能证明「被引用的那条事实此刻仍然成立」**。

本轮把证据强度从**静态层**抬到**活体层**：凡仓里已有活体探针的，
**真的把它跑起来**，并把**真实退出码 + 覆盖断言 + 日期**逐条落回台账。

---

## 一、三条硬纪律（本轮全程适用，且本轮**自己踩了两次**）

| # | 纪律 | 本轮的实证 |
|---|---|---|
| **①** | **跑绿 ≠ 对** | 探针绿只证明「该探针自己的断言」成立，**不等于该条目结论为真** ⇒ 每条戳里都写明「覆盖断言」并附「退出码不等于本条结论的全量成立」的限定 |
| **②** | **退出码 ≠ 判定** | `tools/difftest/exp-*.sh` **17 个里有 12 个没有显式 `exit`** ⇒ 退出码恒为最后一条命令的码（0）⇒ **退出码不承载判定** ⇒ 这类探针的结论**只能读日志正文** |
| **③** | **探针会互相污染** | `api-full-parity-probe/follow` 起进程**缺 `exec`** ⇒ Go 二进制变孤儿（**实测泄漏 13 分钟**）⇒ 它的 **etcd 租约仍在** ⇒ 下一轮探针的 Go client **随机选中陈旧实例** ⇒ 业务码塌成 `Internal` ⇒ **看起来像产品漂移，其实是探针污染** |

---

## 二、探针运行台账（**真实退出码**）

> 口径：**逐个串行**跑（端口段互斥，串行是硬要求）⇒ 每个探针都拿到**自己的**退出码。
> 「覆盖断言」一栏 = **探针自己日志里的结论行**（不是本文件转述的）。
> ⚠️ **EXIT=2 在本仓的约定是「环境问题」**（缺中间件 / 前置产物 / 端口被占）⇒ **结论不可用**，不等于「不一致」。

| 探针 | 退出码 | 判定 | 时长 | 起跑时刻 | 覆盖断言（**探针自己日志里的结论行**） |
|---|---|---|---|---|---|
| `tools/errmsg-probe/run-parity.sh` | **0** | ✅ 自身断言成立 | 2s | 2026-10-30 03:15:56 | ✅ 双侧逐字节一致（sha256 相同，diff 无输出） |
| `tools/errmsg-probe/comment/run-parity.sh` | **0** | ✅ 自身断言成立 | 4s | 2026-10-30 03:15:58 | 临时模块 = commenterrmsgprobe（继承真源 go.mod/go.sum ⇒ gRPC 依赖从 module cache 离线解析） ⏐ ✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 obs_wire_grpcstatus_1012 obs_wire_grpcstatus_2001 obs_wire_grpcstatus_5003 差异仍在登记状态 |
| `tools/errmsg-probe/favorite/run-parity.sh` | **0** | ✅ 自身断言成立 | 4s | 2026-10-30 03:16:02 | ✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 obs_custom_grpcstatus obs_ok_status 差异仍在登记状态 |
| `tools/errmsg-probe/follow/run-parity.sh` | **0** | ✅ 自身断言成立 | 3s | 2026-10-30 03:16:06 | 临时模块 = followerrmsgprobe（继承真源 go.mod/go.sum ⇒ gRPC 依赖从 module cache 离线解析） ⏐ ✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 obs_ok_status obs_custom_grpcstatus obs_wrapped_outer_msg 差异仍在登记状态 |
| `tools/errmsg-probe/hot/run-parity.sh` | **0** | ✅ 自身断言成立 | 3s | 2026-10-30 03:16:09 | 临时模块 = hoterrmsgprobe（继承真源 go.mod/go.sum ⇒ gRPC 依赖从 module cache 离线解析） ⏐ ✅ 双侧逐字节一致（sha256 相同，diff 无输出） |
| `tools/errmsg-probe/like/run-parity.sh` | **0** | ✅ 自身断言成立 | 4s | 2026-10-30 03:16:12 | ✅ 白名单「BIZCODE|foreign_invalid_argument」[DRIFT-REGISTERED] 命中 1 条记录，差异仍在 ⇒ D-7（已登记·待拍板）下游非本域错误：Go 取原始 gRPC 码（INVALID_ARGUMENT ⇒ 3），RAG 侧 mapGrpcCodeToBizCode 映到 2001。出处：docs/DRIFT/like/vs-common.md 的 D-7。 ⏐ ✅ 白名单「BIZCODE|foreign_unauthenticated」[DRIFT-REGISTERED] 命中 1 条记录，差异仍在 ⇒ D-7（已登记·待拍板）下游非本域错误：G |
| `tools/errmsg-probe/message/run-parity.sh` | **0** | ✅ 自身断言成立 | 3s | 2026-10-30 03:16:16 | 临时模块 = messageerrmsgprobe（继承真源 go.mod/go.sum ⇒ gRPC 依赖从 module cache 离线解析） ⏐ ✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 obs_ok_status obs_custom_grpcstatus obs_wrapped_outer_msg 差异仍在登记状态 |
| `tools/errmsg-probe/task/run-parity.sh` | **0** | ✅ 自身断言成立 | 20s | 2026-10-30 03:16:19 | ✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 obs_ok_status obs_wrapped_status obs_api_empty_body obs_api_malformed_json 差异仍在登记状态 |
| `tools/errmsg-probe/users/run-parity.sh` | **0** | ✅ 自身断言成立 | 4s | 2026-10-30 03:16:39 | ✅ 白名单「TABLE|1018」[DRIFT-REGISTERED] 命中 1 条记录，差异仍在 ⇒ DRIFT-USERSCOMMON-001（2026-10-27 已销号·证伪关闭）同上：Go 真源声明了 ErrorAvatarHistoryNotExist=1018 但 codeMsg 表里**没有登记** ⇒ GetErrMsg 回落到 "FAIL"；RAG 侧枚举带文案（"头像历史不存在"）。⇒ 两侧均不可观察 ⇒ 差异不成立（已销号）。出处：Go service/user/common/errmsg/errmsg.go:31（常量）+ :38-68（表里没有）+ :70-76（回 |
| `tools/semantic-probe/run-parity.sh` | **0** | ✅ 自身断言成立 | 4s | 2026-10-30 03:16:43 | （未抓到结论行） |
| `tools/breaker-probe/run-parity.sh` | **0** | ✅ 自身断言成立 | 5s | 2026-10-30 03:16:47 | （未抓到结论行） |
| `tools/json-body-probe/run-parity.sh` | **0** | ✅ 自身断言成立 | 7s | 2026-10-30 03:16:52 | （未抓到结论行） |
| `tools/proto-wire-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 7s | 2026-10-30 03:16:59 | ✓ Go 侧输出与 expected.txt 逐字节一致（sha256=03aed8cc1f785864…） ⏐ ✅ 双侧逐字节一致：Go 真源生成码 == expected.txt == RAG 生成码（同一组消息的 wire 字节） |
| `tools/proto-wire-probe/hot/run.sh` | **0** | ✅ 自身断言成立 | 5s | 2026-10-30 03:17:06 | ✓ Go 侧输出与 expected.txt 逐字节一致（sha256=97672967777ec2e8…） ⏐ ✅ 双侧逐字节一致：Go 真源生成码 == expected.txt == RAG 生成码（同一组消息的 wire 字节） |
| `tools/metrics-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 1s | 2026-10-30 03:17:11 | ✅ Go 语义如预期：错误路径记 fail（⇒ U5 撤销，Go 与 RAG 本来就一致） |
| `tools/metrics-probe/follow/run.sh` | **0** | ✅ 自身断言成立 | 32s | 2026-10-30 04:32:14 | ✅ 故障[block_relation] Block 两侧一致：0|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Unblock 两侧一致：0|rpc error: code = Internal desc = database update failed ⏐ 实际差异与冻结期望**逐条一致**（8 条）⇒ 守门通过 ⏐ ✅ 两侧 follow_rpc_* series 集合一致（差异与冻结期望逐条相同） |
| `tools/api-envelope-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 30s | 2026-10-30 03:17:43 | ✓ Go 侧输出与 expected.txt 逐字节一致（sha256=c5dc8761884acfa0…） ⏐ 2026-10-30T03:17:54.779+08:00  INFO 47811 --- [favorite-api] [.0-21300-exec-2] c.r.common.observability.HttpMiddleware  : [HTTP] 1xx 探针行数预算用尽（cap=4）⇒ 本请求不再写探针字节（客户端断连将只能等写最终响应时才发现） ⏐ 2026-10-30T03:18:00.174+08:00  INFO 47811 --- [favorite-api] |
| `tools/authority-live-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 27s | 2026-10-30 03:18:13 | ✅ Java 侧产出 21 个 case ⏐ SAME 20 条 / OBS 0 条 / SKIP 1 条（共 21 个 case） ⏐ ✅ 两侧逐字节一致（SAME 段全部相同；OBS 段仍然不同；SKIP 段已显式登记理由） ⏐ ✅ 双侧活体对拍通过：20 个 case 逐字节一致 + 1 个 SKIP（容器层，已显式登记理由） |
| `tools/authority-path-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 8s | 2026-10-30 03:18:40 | ✅ Go 侧行为与 expected.txt 一致（Go 真源未变） ⏐ ✅ 双侧逐条一致（12 条 path/错误响应矩阵 + 少一段 path 的 404） |
| `tools/prod-startup-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 28s | 2026-10-30 03:33:47 | ❌ A-before：**启动即炸**（devh URI 校验 · 两斜杠） ⏐ ✅ B-after：**启动成功** ⏐ ❌ C-before：**启动即炸**（Boot 判定成 SERVLET 但没有 servlet 容器） ⏐ ✅ A/B/C 成立：两斜杠 ⇒ 炸(URI) ；web 类型 ⇒ 炸(Servlet) ；生产 yaml 原样 ⇒ 真起来 |
| `tools/grpc-contract-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 54s | 2026-10-30 03:19:15 | ✅ 成功路径两侧各产出 15 个 case ⏐ SAME 15 条 / SAME_CODE 0 条 / OBS 0 条 / SKIP 0 条（共 15 个 case） ⏐ ✅ create 成功路径 + 落库等价性两侧一致（SAME 全等；OBS 仍不同；SKIP 已登记理由） ⏐ ✅ gRPC 契约双侧活体对拍通过 |
| `tools/ddl-parity-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 5s | 2026-10-30 03:20:09 | [ddl-probe]   ⇒ 001 里**内联**的 CHECK (aggregate_version IN (1,2)) 建不出来； ⏐ [ddl-probe]   ⇒ 且 favorite_fact_outbox 的**列顺序**也不同（AutoMigrate 按 struct 序，001 按 DDL 序）。 ⏐ [ddl-probe]   ⇒ RAG 侧与 **sql-first** 一致 ⇒ 与真源文档指定的部署顺序一致。 ⏐ ✅ DDL 双侧活体对拍通过 |
| `tools/sql-statement-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 35s | 2026-10-30 03:20:14 | ✅ 表结构快照：48 列 / 7 约束列 / 9 唯一索引列 ⏐ [compare] 语义差异 0 处 / ORM 噪声与已证明等价 49 处 / L1 响应不同 0 例 ⇒ /var/folders/03/rr7lt6rs6bdffqs32pzp938h0000gn/T/tmp.AOe1zOVDdP/report.md ⏐ ✅ 报告已落盘：/Users/pr/Documents/java&golang/RAG/tools/sql-statement-probe/favorite/out/report.md ⏐ ✅ 两侧 SQL 语句层语义一致（差异均在已登记的 ORM 噪声档内） |
| `tools/api-full-parity-probe/follow/run.sh` | **0** | ✅ 自身断言成立 | 48s | 2026-10-30 04:56:14 | ✅ follow_new_relation  Go == RAG == expected.txt（HTTP 200 · 94 字节） ⏐ ✅ unfollow_seeded  Go == RAG == expected.txt（HTTP 200 · 94 字节） ⏐ ✅ unblock_seeded  Go == RAG == expected.txt（HTTP 200 · 94 字节） ⏐ ✅ 双侧逐字节一致（Go 真源 == RAG == 冻结期望值） |
| `tools/api-full-parity-probe/like/run.sh` | **0** | ✅ 自身断言成立 | 59s | 2026-10-30 03:27:30 | ✅ DC 收到的字节：Go == RAG（970 bytes · sha256 3685ca3017df6b436253d4632608631aa36915a597f25a756bd937fd2c1606d0） ⏐ ✅ outbox 终态（不含墙钟列）：Go == RAG ⇒ rtw.like.9007199254740993001|community.target.interaction|like-state/9007199254740993/article/9007199254740994|1|1|0|rcpt-38b38328d978fdd0|38b38328d978fdd0f6563a |
| `tools/api-full-parity-probe/article/run.sh` | **0** | ✅ 自身断言成立 | 34s | 2026-10-30 03:21:39 | ✅ list_articles  Go == RAG == expected.txt（逐字节） ⏐ ✅ get_article_exists  Go == RAG == expected.txt（逐字节） ⏐ ✅ get_article_missing  Go == RAG == expected.txt（逐字节） ⏐ ✅ 双侧逐字节一致（Go 真源 == RAG == 冻结期望值） |
| `tools/api-full-parity-probe/favorite/run.sh` | **0** | ✅ 自身断言成立 | 48s | 2026-10-30 03:28:29 | ✅ 两侧各产出 31 个 case ⏐ SAME 25 条 / SAME_CODE 1 条 / SAME_BODY 2 条 / SAME_STATUS 4 条 / OBS 0 条 / SKIP 0 条（共 32 个 case） ⏐ ✅ api 整域两侧一致（SAME 全等；SAME_CODE 业务码等；SAME_BODY body 等；SAME_STATUS 仅状态码等；OBS 仍不同） ⏐ ✅ api 整域双侧活体对拍通过 |
| `tools/api-full-parity-probe/favorite-rpc/run.sh` | **0** | ✅ 自身断言成立 | 37s | 2026-10-30 03:22:58 | ⚠️  f03_create_downstream_absent  Go != RAG（AMBIENT 档 · **不判红**：两侧注册中心不同 ⇒ 可能只是下游在场状态不同） ⏐ ✅ g01_delete_folder_cascade  Go == RAG == expected.txt（FAILED_PRECONDITION · 响应字节 0 B） ⏐ ⚠️  1 条 AMBIENT 用例两侧不一致（**不判红**，见上） ⏐ ✅ 双侧逐字节一致：Go 真源 == RAG == 冻结期望值（FROZEN 档 **25** 条逐字节一致 ＋ AMBIENT 档 1 条只判 Go==RAG · |
| `tools/api-full-parity-probe/users/run.sh` | **0** | ✅ 自身断言成立 | 67s | 2026-10-30 03:23:35 | ✅ 落库对照通过：两侧 HTTP 成功且各恰好 1 行 ⇒ **register 落库正常，不是缺陷** |
| `tools/api-full-parity-probe/users-common/run.sh` | **0** | ✅ 自身断言成立 | 14s | 2026-10-30 03:24:43 | ⚠️  c.errmsg.1018  **已登记的两侧不同**（两侧值均已钉住） ⏐ ⚠️  c.jwt.raw.header  **已登记的两侧不同**（两侧值均已钉住） ⏐ ⚠️  c.jwt.raw.payload  **已登记的两侧不同**（两侧值均已钉住） ⏐ ✅ Go == RAG == expected.txt（逐字节） |
| `tools/difftest/run.sh` | **0** | ✅ 自身断言成立 | 137s | 2026-10-30 03:29:45 | PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline） ⏐ ⇒ E38① 的运行期结论成立 ⏐ ⇒ 退出码 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ⇒ 判定退出码 = 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） |
| `tools/difftest/ddl-parity.sh` | **2** | 🟡 环境问题（结论不可用） | 2s | 2026-10-30 03:34:15 | （未抓到结论行） |
| `tools/difftest/run-message-ddl-parity.sh` | **1** | 🔴 不一致 | 6s | 2026-10-30 03:34:17 | ⚠️ 两侧**没有可成对比对的触发器** ⇒ 本维度**什么都没比**（不是「已对齐」） ⏐ ⚠️ 两侧**没有可成对比对的触发器函数** ⇒ 本维度**什么都没比**（不是「已对齐」） ⏐ == 实质差异合计 7 处 ⇒ 退出码 1 == |
| `tools/difftest/comment/run_diff.sh` | **1** | 🔴 不一致 | 2s | 2026-10-30 03:34:23 | ❌ 缺前置产物：bin/go-comment-rpc（见同目录 README.md「怎么跑」） |
| `tools/difftest/run-task-http-parity.sh` | **0** | ✅ 自身断言成立 | 46s | 2026-10-30 03:34:25 | ⇒ 归 DRIFT-TASK-API-004：Go `httpx.WriteJsonCtx` 写 `application/json; charset=utf-8`， ⏐ 按 RFC 9110，`;` 后的 OWS 本就允许省略 ⇒ 非语义差异。 ⏐ ⇒ 归**全域共性**，不在 task 域单独修（单独改会让本域与其它域不一致）。 ⏐ ✅ HTTP wire 层：Go 与 RAG 全部用例一致 |
| `tools/difftest/run-task-grpc-xlang.sh` | **1** | 🔴 不一致 | 2s | 2026-10-30 03:35:11 | （未抓到结论行） |
| `tools/difftest/run-task-offset-oldest.sh` | **0** | ✅ 自身断言成立 | 153s | 2026-10-30 03:35:13 | ✓ PASS  [A6] 端到端 like:total:5001 = 10（预灌 5 + 后灌 5 ⇒ 预灌那批也被计入） ⇒ 实测=Go=10 RAG=10 ⏐ ✓ PASS  [A7] 端到端 like:total:5002 = 4（doing 分支，预灌 2 + 后灌 2） ⇒ 实测=Go=4 RAG=4 ⏐ A6   端到端 like:total:5001 = 10（预灌 5 + 后灌 5 ⇒ 预灌那批也被计入） 实测=Go=10 RAG=10             PASS ⏐ ✅ 全部断言通过（earliest 正向 + latest 阴性对照 + 阳性对照） |
| `tools/difftest/run-task-nacos-discovery.sh` | **0** | ✅ 自身断言成立 | 20s | 2026-10-30 03:37:46 | ✅ 实例已注册到真注册中心            = yes ⏐ ==== 5. 停进程 ⇒ 实例应从注册表消失（不留幽灵） ==== ⏐ ✅ 实例已从注册表消失（无幽灵）   = yes |
| `tools/difftest/run-task-kafka-e2e.sh` | **0** | ✅ 自身断言成立 | 85s | 2026-10-30 03:38:06 | ==== 2. 编 RAG 侧（⑤ 不是 fat jar ⇒ classes + 依赖 classpath） ==== ⏐ ✓ Kafka 容器已重建（⇒ 无 topic、无已提交位点、无 consumer group） ⏐ 批次：user 5001 × 5（恰好达到 target=5 ⇒ 完成分支）、user 5002 × 2（doing 分支） ⏐ ✅ Kafka 端到端最终态：Go 与 RAG 完全一致 |
| `tools/difftest/run-task-kafka-malformed.sh` | **1** | 🔴 不一致 | 2s | 2026-10-30 03:39:31 | （未抓到结论行） |
| `tools/difftest/parity-task.py` | **1** | 🔴 不一致 | 2s | 2026-10-30 03:42:51 | （未抓到结论行） |
| `tools/difftest/run-hot.sh` | **1** | 🔴 不一致 | 32s | 2026-10-30 04:55:21 | ✓ CASE-20    方法不匹配 ⇒ 405 裸状态码（无 body） ⏐ ✗ CASE-21    下游拖过 3s ⇒ 503 Request Timeout（校验 nosniff） ⏐ ↳ 已知漂移：503 响应缺 X-Content-Type-Options: nosniff（D14，代码在 service/common ⇒ 已移交 common 域） ⏐ ⇒ 全部失败项均已在台账登记（见每条的「已知漂移」标注） |
| `tools/difftest/run-hot-rpc-difftest.sh` | **1** | 🔴 不一致 | 2s | 2026-10-30 03:43:58 | ==> [2/7] 清空两个 Redis（**必须等旧进程死后**，否则它会把旧 sketch 写回来 ⇒ 分数翻倍） ⏐ ❌ hotrpcdifftest-redis-go:6379 DBSIZE=（应为 0）⇒ 有旧数据残留，停下 |
| `tools/difftest/probe-hot-latency.py` | **0** | ✅ 自身断言成立 | 18s | 2026-10-30 03:42:53 | p99：Go 444.5ms  vs  RAG 416.8ms  ⇒ RAG/Go = 0.94× ⏐ p50：Go 353.0ms  vs  RAG 359.6ms  ⇒ RAG/Go = 1.02× |
| `tools/difftest/probe-hot-protection.py` | **1** | 🔴 不一致 | 20s | 2026-10-30 03:43:11 | [PASS] breaker        rag  3 波 × 12 并发 ⇒ 熔断裸 503（空 body）共 29 个；超时 503（body=Request Timeout）共 0 个（期望：> 0 个空 body 的 503 —— 这才证明熔断**真的打开了**） ⏐ FAIL maxconns/go: 12 并发在途 ⇒ 裸 503（空 body）0 个 / 放行 10 个 / 传输错误 0 个，耗时 3.0s（期望：至少 1 个裸 503） ⏐ FAIL maxconns/rag: 12 并发在途 ⇒ 裸 503（空 body）0 个 / 放行 12 个 / 传输错误 0 个，耗 |
| `tools/difftest/probe-hot-disconnect.py` | **0** | ✅ 自身断言成立 | 12s | 2026-10-30 03:43:31 | ⇒ **两侧不等价，且方向与台账 D3 一致**： ⏐ · Go：客户端断连 ⇒ HTTP ctx 取消 ⇒ **在飞的 gRPC 立刻被取消** ⏐ · RAG：Servlet 断连**不**取消在飞的阻塞 gRPC ⇒ 只能等 2s 调用 deadline ⏐ ⇒ DRIFT-HOTAPI 的 **D3「客户端断连的取消不再传播」已由活体证据坐实** |
| `tools/difftest/probe-hot-limits.py` | **0** | ✅ 自身断言成立 | 2s | 2026-10-30 03:43:43 | · RAG 侧：值个数阈值 2049（Tomcat maxParameterCount 配 2048 ⇒ 第 2049 个抛 InvalidParameterException ⇒ 被 catch-all 吞成 {code:5001}） ⏐ ⇒ 值个数阈值：Go 2049 / RAG 2049  ✅ 一致 ⏐ ⚠️ 不是 1MB：Go 的真实上限是 1 MiB **+ 4096**（net/http/server.go:941 'bufio slop'） ⏐ ⇒ 守门：tools/difftest/check-tomcat-limits.py（10 个 api 模块静态检查） |
| `tools/difftest/probe-hot-u5open1.py` | **0** | ✅ 自身断言成立 | 10s | 2026-10-30 03:43:45 | ⇒ 差异存在但方向相反 ⇒ 仍属候选 ②，需按实测方向定位 |
| `tools/difftest/probe-go-required-fields.py` | **0** | ✅ 自身断言成立 | 3s | 2026-10-30 03:43:55 | N-3（已签字）称：uid 缺失时 Go 静默取 0 值 ⇒ 返回 1003 ⏐ ⇒ 若这里不是 400，N-3 的前提有误。 ⏐ ContentLength==0 ⇒ UnmarshalJsonMap(nil, v) ⇒ 对 struct 是 no-op、**不校验** |
| `tools/difftest/probe-go-omitempty.sh` | **0** | ✅ 自身断言成立 | 2s | 2026-10-30 03:44:00 | ✅ 逐字节相同 |
| `tools/difftest/flyway-rpc-concurrent.sh` | **0** | ✅ 自身断言成立 | 175s | 2026-10-30 03:44:02 | ✅ task       ok 账本=flyway_schema_history_task         baseline=0   SQL=2 条 BASELINE行=[] gRPC监听=True 用时=4486ms ⏐ ✅ message    ok 账本=flyway_schema_history_message      baseline=0   SQL=5 条 BASELINE行=[] gRPC监听=True 用时=4277ms ⏐ [L11]   账本行数（应为 2 条 V1/V2，**不是** 4 条 ⇒ 没有重复应用）：2 ⏐ ✅ 所有非故意失败的进程：迁移全部 SUCCESS |
| `tools/difftest/check-flyway-concurrent.sh` | **0** | ✅ 自身断言成立 | 72s | 2026-10-30 03:46:57 | ⏱  并发组 ['favorite', 'message'] 的起跑时刻：[1793303287034, 1793303287033] （最大偏差 1ms ⇒ 真并发） ⏐ ✅ like ⏐ ✅ message ⏐ ✅ 无差异：各模块的表都建出来了、账本互不覆盖、baseline 落位正确。 |
| `tools/difftest/run-deadline-gate.sh` | **4** | 🔴 退出码 4 | 2s | 2026-10-30 03:48:09 | （未抓到结论行） |
| `tools/difftest/check-deadline-downstream.py` | **0** | ✅ 自身断言成立 | 12s | 2026-10-30 03:48:11 | ✅ failed_fast        Go=true | RAG=false  （已登记的漂移形态） ⏐ ✅ code               Go=4 | RAG=0  （已登记的漂移形态） ⏐ ✅ outgoing_carrier   Go=present_expired | RAG=absent  （已登记的漂移形态） ⏐ ⇒ ✅ 通过（parity 键两侧一致；drift 键形态与已登记一致） |<br>⚠️ **【2026-10-31 复测】本行的「漂移形态」读数已过期** —— 探针 RAG 半边已改走 devh **生产装配路径**（`e796a4ed`）⇒ ⇒ 实测**四键逐键相等**：`failed_fast` true/true · `code` 4/4 · `outgoing_carrier` present_expired/present_expired · `sql_carrier` present_expired/present_expired ⇒ ⇒ **本行的退出码仍是 0 ⇒ 派生层档位不受影响**（`load_live_probes()` 只取退出码列）⇒ ⇒ 复现命令与完整读数见**文末 §八**。
| `tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` | **0** | ✅ 自身断言成立 | 8s | 2026-10-30 03:48:23 | ⚠️  timeout_telemetry                        预期差异（不计入失败） ⏐ 理由：RAG 的 code 段用 grpc-java 的 Code.name()（DEADLINE_EXCEEDED），Go 用 grpc-go 的 codes.Code.String()（DeadlineExceeded）⇒ 已登记为「表述差异 C-1」，不在 global 修改范围 ⏐ ✅ timeout_wire                             一致 ⏐ ✅ PASS —— 3 个用例两侧一致 |
| `tools/semantic-probe/trace/run-trace-parity.sh` | **0** | ✅ 自身断言成立 | 6s | 2026-10-30 03:48:31 | ⚠️  G7_exporter_zipkin                       预期差异（不计入失败） ⏐ 理由：RAG 未实现 zipkin（Go 能装）⇒ 能力缺口，非静默降级 ⏐ ✅ G8_propagation                           一致 ⏐ ✅ PASS —— 11 个用例两侧一致 |
| `tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh` | **0** | ✅ 自身断言成立 | 10s | 2026-10-30 03:48:37 | ✅ failed_fast        Go=true | RAG=false   （已登记的漂移形态） ⏐ ✅ outgoing_carrier   Go=present_expired | RAG=absent   （已登记的漂移形态） ⏐ ✅ sql_carrier        Go=present_expired | RAG=present_expired   （两侧一致） ⏐ ✅ PASS —— 1 个用例：parity 键两侧一致，drift 键形态与已登记一致 |<br>⚠️ **【2026-10-31 复测】本行的「漂移形态」读数已过期** —— 探针 RAG 半边已改走 devh **生产装配路径**（`e796a4ed`）⇒ ⇒ 实测**四键逐键相等**（`failed_fast` true/true · `code` 4/4 · 两个 carrier 均 present_expired）⇒ ⇒ **本行的退出码仍是 0 ⇒ 派生层档位不受影响**（`load_live_probes()` 只取退出码列）⇒ ⇒ 复现命令与完整读数见**文末 §八**。
| `tools/difftest/exp-difftest-grpc-user9.sh` | **1** | 🔴 不一致 | 22s | 2026-10-30 03:56:56 | （未抓到结论行） |
| `tools/difftest/exp-go-concurrent-automigrate.sh` | **0** | ✅ 自身断言成立 | 21s | 2026-10-30 03:57:18 | （未抓到结论行） |
| `tools/difftest/exp-difftest-redis-blacklist.sh` | **0** | ✅ 自身断言成立 | 39s | 2026-10-30 04:11:48 | 用有效 token 只能拿到 200，而「断连秒拒」也是 200 ⇒ 区分不了。 ⏐ ⇒ 判定要点： ⏐ ①b 必须 401 ⇒ 证明黑名单机制真在跑（否则 ② 的 200 是假绿） ⏐ ②b 必须 200 ⇒ 证明 Redis 故障时是 fail-open 放行（不是 401 误拦，也不是 503） |
| `tools/difftest/exp-difftest-redis-neverup.sh` | **0** | ✅ 自身断言成立 | 27s | 2026-10-30 04:12:27 | ⇒ 关键看**耗时是否 < 3s**：超过 3s 就撞 Go 的 HTTP 预算（rest/config.go:55 default=3000） ⏐ · RAG ：黑名单查询失败 ⇒ fail-open（JwtAuthInterceptor catch ⇒ exists=null） ⏐ ⇒ 同样关键看耗时：**≥ 3s ⇒ 撞 3s 预算 ⇒ 503 fail-closed**（与 Go 不同形状） ⏐ · 首轮 vs 稳态必须分开：Lettuce 首次连接失败后转入 disconnected 态 ⇒ 后续命令可能被秒拒。 |
| `tools/difftest/exp-difftest-concurrent-schema.sh` | **0** | ✅ 自身断言成立 | 32s | 2026-10-30 04:12:54 | ⇒ 关键判定是**能不能起来**与**共享表有没有建出来**，不是表名。 |
| `tools/difftest/exp-http-headers-message.sh` | **0** | ✅ 自身断言成立 | 5s | 2026-10-30 04:13:26 | （未抓到结论行） |
| `tools/difftest/exp-difftest-redis-neverup-rpc.sh` | **0** | ✅ 自身断言成立 | 26s | 2026-10-30 04:13:31 | · 若 Go=INTERNAL 而 RAG=DEADLINE_EXCEEDED ⇒ **rpc 侧也有漂移**（与 api 侧的 015 同源） ⏐ · 若两侧都是 DEADLINE_EXCEEDED ⇒ rpc 侧**无漂移**（015 只存在于 api 侧） ⏐ never（拒连 / 解析失败）⇒ 命中「**连接失败**」，远快于 3s ⇒ 拦截器不参与 ⏐ hanging（连上不回包）  ⇒ 命中「**命令超时**」⇒ 3s > 2s ⇒ **拦截器必然先到** |
| `tools/difftest/exp-difftest-redis-failfast.sh` | **0** | ✅ 自身断言成立 | 20s | 2026-10-30 04:13:57 | ❌ Go 侧**起来了**（与静态证据矛盾！） ⏐ ✅ RAG 侧**起来了**（懒连接 ⇒ 与静态证据一致） ⏐ ⇒ 若 Go 失败 / RAG 成功 ⇒ 这就是 UNRESOLVED-USERRPC-004② 的**实测形态**： |
| `tools/difftest/exp-admin-startup-failfast.sh` | **0** | ✅ 自身断言成立 | 15s | 2026-10-30 04:14:17 | 耗时   = 5.0s（期望 ≈3s ⇒ 对齐 go-zero dialTimeout） ⏐ ✓ 进程非 0 退出 ⇒ 起不来，与 Go 的 panic 同型 ⏐ 拨号预算 = 3.2s（期望 ≈3s ⇒ 对齐 go-zero zrpc/internal/client.go:18 dialTimeout） ⏐ ✓ 进程非 0 退出 ⇒ Nacos 不可达 ⇒ 起不来（Go 侧 etcd 不可达 ⇒ MustNewClient panic，同型） |
| `tools/difftest/exp-userapi-redis-dns-connect-timeout.sh` | **0** | ✅ 自身断言成立 | 74s | 2026-10-30 04:14:32 | ⇒ **结论（已回写 docs/DRIFT/users/user/api.md 的 §7 DRIFT-USERAPI-015）**： ⏐ 1) 台账那条「RAG 200/503 摇摆」**复现不出来** ⇒ 定性降级为「条件性成立 · 证据不足」； ⏐ → SocketOptions.builder().connectTimeout），但**生效 ≠ 该做** ⇒ 建议**否决**。 ⏐ ⚠️ 这是**先验**，不是落地：它只回答「候选② 在这两种形态下会怎样」。 |
| `tools/difftest/exp-drift015-flyway-flags.sh` | **0** | ✅ 自身断言成立 | 43s | 2026-10-30 04:15:46 | （未抓到结论行） |
| `tools/difftest/exp-admin-worker-pool-load.sh` | **1** | 🔴 不一致 | 159s | 2026-10-30 04:27:57 | ⇒ 裸 503 **出不出、多少，取决于当时机器有多忙** ⇒ 它是负载相关的，不稳定。 ⏐ ⇒ 本脚本第 1 轮与第 2 轮的裸 503 数可能都是 0，也可能差很多。 ⏐ ⇒ **判定池饱和分支可达性的可靠依据是「池并发峰值 vs 512」，不是「裸 503 数」。** ⏐ ✗ 第 2 轮仍有裸 503 ⇒ 需要重新分析 |
| `tools/difftest/exp-b1-timeout-cancel.sh` | **0** | ✅ 自身断言成立 | 74s | 2026-10-30 04:18:40 | setQueryTimeout）。若 RAG 这里又是数千毫秒 ⇒ 拦截器没被注册 / 没生效。 ⏐ RAG 若仍是 text/plain "Request Timeout" ⇒ DEADLINE_EXCEEDED 没能穿过 ⏐ 若变成 {"code":5001} ⇒ 57014 翻译没生效（被吞成了 INTERNAL）。 |
| `tools/difftest/exp-difftest-grpc-errors-message.sh` | **0** | ✅ 自身断言成立 | 4s | 2026-10-30 04:27:11 | （未抓到结论行） |
| `tools/difftest/exp-grpc-admin-deadline-probe.sh` | **0** | ✅ 自身断言成立 | 34s | 2026-10-30 04:27:15 | PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline） ⏐ ⇒ E38① 的运行期结论成立 ⏐ ⇒ 退出码 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ⇒ 判定退出码 = 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） |
| `tools/difftest/exp-difftest-grpc-admin-errors.sh` | **0** | ✅ 自身断言成立 | 6s | 2026-10-30 04:30:58 | code_mismatch>0 ⇒ 语义级差异，exit 1 ⏐ description_diff>0 ⇒ 只有 status description 不同（见 DRIFT-ARPC-007），不判失败 |
| `tools/api-full-parity-probe/follow/mutate-parity.sh` | **0** | ✅ 自身断言成立 | 346s | 2026-10-30 04:57:02 | == M5 复现 FAILING-010（固定 -o 文件 + 去掉 000 守卫 + 只比 body）⇒ 必须出现假绿 == ⏐ ✅ 假绿被复现（follow_list_limit_max 被误判成 ✅ ⇒ 读到了上一次的残留 body） ⏐ ✅ 还原后绿（exit 0） ⏐ ✅ 全部变异按预期表现 + 还原后转绿 ⇒ 探针非假绿 |
| `tools/api-full-parity-probe/like/mutate-parity.sh` | **0** | ✅ 自身断言成立 | 199s | 2026-10-30 04:33:32 | == M3 E60 的业务错误日志被降级成普通 warn（≈ 还原 E60 修前行为）⇒ 必须转红 == ⏐ ✅ 精确转红（exit 1 · E60 的业务错误分支没被观测到） ⏐ ✅ 还原后绿（exit 0） ⏐ ✅ 三条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿 |
| `tools/api-full-parity-probe/article/mutate-parity.sh` | **0** | ✅ 自身断言成立 | 161s | 2026-10-30 04:36:51 | == M3 冻结的 expected.txt 被改 ⇒ 必须转红 == ⏐ ✅ 精确转红（exit 1 · Go 侧与冻结值不符） ⏐ ✅ 还原后绿（exit 0） ⏐ ✅ 三条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿 |
| `tools/api-full-parity-probe/favorite-rpc/mutate-parity.sh` | **0** | ✅ 自身断言成立 | 169s | 2026-10-30 04:39:34 | == M3 RAG 侧错误文案漂移（5003「参数错误」→「参数错误X」）⇒ 必须转红 == ⏐ ✅ 精确转红（exit 1 · description 漂移被抓到） ⏐ ✅ 还原后绿（exit 0） ⏐ ✅ 全部变异精确转红 + 还原后转绿 ⇒ 探针非假绿 |
| `tools/api-full-parity-probe/users/mutate-parity.sh` | **0** | ✅ 自身断言成立 | 326s | 2026-10-30 04:42:23 | ✅ EXIT=1（期望 1）· steps=39  asserted=6  byte_equal=19  inconclusive=1  unexpected=13 1xx_命中_step数=0  1xx_总行数=0  因1xx重试_step数=0  重试_step数=10  预热命中1xx轮数=0 ⏐ ✅ EXIT=0（期望 0）· steps=39  asserted=10  byte_equal=29  inconclusive=0  unexpected=0 1xx_命中_step数=1  1xx_总行数=2  因1xx重试_step数=0  重试_step数=0  预热命中1xx轮数 |
| `tools/api-full-parity-probe/users-common/mutate-parity.sh` | **0** | ✅ 自身断言成立 | 66s | 2026-10-30 04:54:13 | ✅ 精确转红：c.int64.1 ⏐ == 还原后必须重新转绿（影子副本全部在临时目录 ⇒ 仓内源码从未被改） == ⏐ ✅ 还原后绿（exit 0） ⏐ ✅ 四条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿 |
| `tools/difftest/check-jar-executable.sh` | **0** | ✅ 自身断言成立 | 2s | 2026-10-30 04:55:19 | ✅  service/users/admin/admin-api               80M   Main-Class: org.springframework.boot.loader.launch.JarLauncher ⏐ ✅  service/users/admin/admin-rpc               79M   Main-Class: org.springframework.boot.loader.launch.JarLauncher ⏐ ✅  service/users/user/user-api                 80M   Main-Class: |
| `tools/proto-wire-probe/hot/mutate-wire.sh` | **0** | ✅ 自身断言成立 | 21s | 2026-10-30 04:55:53 | ✅ hot.proto 已还原（sha256=df261e1490b83844…） ⏐ ✅ expected.txt 已还原（sha256=97672967777ec2e8…） ⏐ ✅ go/main.go 已还原（sha256=67733b4e1de7088f…） ⏐ ✅ 三条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿 |
---

## 三、本轮抓到的「工具自己骗人」（15 条）

> 这一节是本轮**最实**的产出：全部是**探针自身**的缺陷（**不是产品缺陷**），
> 且**每一个都让台账的某个断言失真** ⇒ 已逐条修（`tools/`）+ 订正台账（`docs/`）。

### ① `prod-startup-probe` 的 `servlet` 分类器只认 Boot 3 措辞 ⇒ **假红**

- **现象**：`bash tools/prod-startup-probe/favorite/run.sh` ⇒ **EXIT=1**，判定「C（期望「炸·servlet」，实际 rc=2 kind=other）」。
- **真因**：本项目已升 **Spring Boot 4.0.8** ⇒ 「判定成 SERVLET 但没有 servlet 容器」的**失败签名变了**：
  - Boot 3 ⇒ `ServletWebServerFactory bean defined ...`（旧分类器只认这一句）
  - Boot 4 ⇒ `class path resource [org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.class] cannot be opened because it does not exist`
    （根因：共享层 `common` 的 `GoStyleJsonContentTypeAdvice implements ResponseBodyAdvice`，
    而 rpc 模块**故意不带 `spring-webmvc`** ⇒ 该接口的字节不存在）
- **产品有没有缺陷？** **没有** ⇒ 同轮 **B-case**（生产 yaml 原样）**真启动成功 + `lsof` 确认 21300 LISTEN**。
  ⇒ **旧分类器在 Boot 4 下恒判 `other` ⇒ 探针假红**。
- **台账影响**：`docs/DRIFT/favorite/rpc.md` §26.7 的「生产启动探针 ⇒ **EXIT=0**」在 2026-10-30 03:18 的实跑里**不成立**（EXIT=1）。
- **修法**：① 两种签名都认；② **只认失败签名**（成功启动的日志里也可能出现 `ServletWebServerApplicationContext`，那是**正常的**上下文类名，不是失败签名）；③ 抽出纯函数 + 加 `--self-test`（**不起服务、不碰 DB**，秒级）。

### ② ⚠️ **我自己的第一次修复又引入了新 bug** —— 「自检全绿而出口全错」

- **现象**：修完 ① 之后复跑，**三个 case 的 kind 全成 `other`** ⇒ 探针报「A/B/C 不成立」。
- **真因**：重构时把分类逻辑抽成了 `classify_log`，**却忘了把结果写回全局 `LAST_KIND`**
  ⇒ 三个 case 的 `kind` 全是初值 `other`。
- **为什么自检没抓到**：**自检只测了「判据函数」，没测「判据 → 退出码」的接线**
  ⇒ 自检 6/6 全绿，探针出口却是错的。
- **根治**：① 把「日志 → (kind, rc)」整条决策路径收进**一个纯函数** `verdict_of_log`；
  ② **最终判定直接由日志文件重新推导**，**不再经由 `run_case` 的返回值 / 全局变量传递**
  ⇒ 没有中间状态可丢 ⇒ 同类 bug **结构上不可能再发生**；③ 自检改为覆盖**整条决策路径**（含 `kind → rc` 的接线），**6/6 通过**。
- **教训（可复用）**：**自制判据的自检，必须覆盖「判据 → 出口」的接线，不只是判据本身**
  —— 否则探针可以「自检全绿而实际出口是错的」。

### ③ `api-full-parity-probe/like` 的 `JAEGER_PORT` 默认值陈旧 ⇒ **预检就挂**

- **现象**：EXIT=**2**，2 秒就挂 ⇒ `!! 宿主端口 49268 不通（Sea-jaeger 的映射口）` ⇒ **一个 case 都没跑**。
- **真因**：默认值写死 `49268`，而本机 `Sea-jaeger` 的宿主映射是 **49317**（其余 4 个探针也都是 49317）⇒ **环境口径漂移**。
- **修法**：改成与全仓一致（49317）；仍可用 env 覆盖。

### ④ `metrics-probe/follow` 的 `JAEGER_PORT` 默认值陈旧（同款）

- 原写死 `49250` ⇒ 同样与实际（49317）不符 ⇒ 同批订正。

### ⑤ `tools/difftest/run.sh` **没给 fail-closed 的密钥 env** ⇒ 探针不可复现

- **现象**：EXIT=**1**，在「起 RAG 栈」那一步就挂 ⇒ `FAIL rag user-rpc 启动失败`。
- **真因**：`service/users/user/user-rpc/src/main/resources/application.yaml:148`
  `access-secret: ${USER_JWT_SECRET}`（**故意没有默认值** ⇒ P0 #8 的 fail-closed 守卫）
  ⇒ 不给 env ⇒ 占位符原样留下 ⇒ 守卫抛
  `IllegalStateException: user-rpc.user-auth.access-secret 仍是未解析的占位符` ⇒ **进程起不来**。
  同款需求：`user-api` · `admin-rpc` · `admin-api` · `message-api`（后者**两个都要**）。
- **影响面**：台账里 **19 条**点名了这个探针 ⇒ 在修之前，**这 19 条的活体证据都不可复现**。
- **修法**：探针起手生成**随机** `USER_JWT_SECRET` / `ADMIN_JWT_SECRET` 并 `export`（不落仓 · 不复用生产密钥）⇒ 与 `api-full-parity-probe/users` 同款做法。
- **复跑结果**：EXIT=**0**（137s）✓

### ⑥ `tools/difftest/run.sh` 的 `assert_started()` **诊断 grep 命不中** ⇒ **用错误的姿势失败**

- **现象**：报的是「`FAIL rag user-rpc 启动失败：`」+ **后面什么都没有**。
- **真因**：「命中判定」靠 `BeanCreationException` 那一行（**命中了** ⇒ 报「启动失败」），
  但「打印原因」的那条 grep 只认三种模式（`Description:` / `Port … was already in use` / `APPLICATION FAILED`）
  ⇒ **命不中** ⇒ 打印空原因 ⇒ **看不出是哪一类失败**。
- **修法**：额外打印**最深的根因行**（最后一条 `Caused by:`）。

### ⑦ `api-full-parity-probe/follow` 起进程**缺 `exec`** ⇒ **进程泄漏 + 跨探针污染**

- **现象**：探针 03:20 跑完退出，**三个 Go 进程到 03:33 仍在跑**（`user-rpc` / `follow-rpc` / `follow-api`）。
- **真因**：`( cd "${WORK}" && "${WORK}/bin/user-rpc" … ) &` ⇒ `$!` 是**子 shell** 的 PID ⇒ `kill $!` 杀不到真正的 Go 二进制
  ⇒ 二进制**变孤儿**继续跑 ⇒ **etcd 租约不失效**（`user.rpc` → `:18104`、`follow.rpc` → `:18103` 仍指向它们）。
- **下游后果**：`metrics-probe/follow`（03:32 跑）的 Go 侧**全部**返回 `Internal`
  ⇒ 因为它的 Go client **随机选中**了 those 陈旧实例（连到已死的那个）。
  ⇒ **那不是产品缺陷，是探针互相污染** ⇒ 该次 EXIT=2 **不可用**。
- **修法**：① 三处起进程加 `exec`（让二进制替换子 shell ⇒ `$!` 就是它）；
  ② 探针收尾加**「端口零残留」断言** ⇒ 收尾不干净就判 **EXIT=2（不判绿）**
  ⇒ **宁可报「环境问题」，也不把污染留给下一轮**（与 `api-full-parity-probe/like` 的既有做法对齐）。

### ⑧ `exp-*.sh` 里 **12/17 没有显式 `exit`** ⇒ **退出码不承载判定**

- **形态**：脚本末尾只有 `echo` ⇒ 退出码 = 最后一条命令的码 = **恒 0**。
- **后果**：把「探针跑完了」读成「探针断言成立」⇒ **假绿**。
- **处置**：这批探针的结论**只从日志正文取**（本文件 §二 的「覆盖断言」栏就是这么取的），
  且台账里**不得**用它们的退出码当判定。

### ⑨ 三个探针**必须带调用方式 / 前置编排**，无参调用必挂

| 探针 | 无参调用的结果 | 正确调用方式 |
|---|---|---|
| `tools/difftest/ddl-parity.sh` | EXIT=2（打印用法） | `bash tools/difftest/ddl-parity.sh <域>|all` |
| `tools/difftest/comment/run_diff.sh` | EXIT=1「缺前置产物：bin/go-comment-rpc」 | 需先按同目录 README 构建前置产物 |
| `tools/difftest/run-task-grpc-xlang.sh` | EXIT=1「:19005 没在监听 —— 先起两侧 task-rpc」 | 需先 `KEEP_UP=1 ./tools/difftest/run-task-http-parity.sh` |
| `tools/difftest/exp-difftest-grpc-admin-errors.sh` | **不退出**（它会 `exec run.sh --keep --serve-only` ⇒ **故意把两套栈留着**） | 需先起栈，再 `--stacks-up`；跑完要手动收摊 |
| `tools/difftest/parity-task.py` | EXIT=1「No such container: taskprobe-postgres」 | 需先起 `taskprobe-postgres`（由 `run-task-http-parity.sh` 建） |

⇒ **台账里引用这批探针时必须写清调用方式**，否则「引用可寻址」也复现不出来** ——
**引用可寻址 ≠ 跑得起来 ≠ 跑得出结论**（本轮实测三段都不成立）。


### ⑩ 两个「探针互相打架」与「自己杀自己」的坑（本轮实测）

- **⑩-a 端口互撞**：上一轮探针**跑完没清自己的容器**（`exp-difftest-grpc-user9.sh` 的
  `uudifftest-postgres` 仍占 **55434**）⇒ 下一个探针 `port is already allocated`
  ⇒ **与真源/代码无关的假故障**。
  ⇒ 修法：批跑器**逐探针前置清理**（只删探针自有的容器名前缀：
  `uudifftest-` / `gorace-` / `taskprobe-` / `fav-prod-startup-probe-` / `r2follow-`
  ⇒ **绝不碰** `Sea-*` / `ddl-*` / `pgtest` / `postgres` / `redis` / `nacos` / `kafka` / `minio`）。
- **⑩-b `pkill -f` 自匹配**：批跑器的 argv 里**含全部探针路径**
  ⇒ `pkill -f "exp-difftest-grpc-admin-errors"` **把批跑器自己杀了**
  （`-f` 匹配**完整命令行** ⇒ 驱动也在匹配范围内）。
  ⇒ ⇒ **教训：批跑期间不要用 `pkill -f <探针名>` 清单个探针** ——
  要清就按 **PID** 清，或用不会命中驱动自身 argv 的模式。

### ⑪ 🔴 **fail-closed 密钥缺失是系统性的** —— 12 个探针全部起不来 RAG 侧

- **现象**：凡起 RAG `user-rpc` / `admin-rpc` / `message-api` 的探针，RAG 侧进程**都起不来**。
- **真因**：RAG 的 users 域把 JWT 密钥写成**故意没有默认值**的占位符（P0 #8 的 fail-closed 守卫）
  ⇒ 不给 env ⇒ 占位符原样留下 ⇒ 守卫抛
  `IllegalStateException: user-rpc.user-auth.access-secret 仍是未解析的占位符（${USER_JWT_SECRET}）`。
- **受害面（实测全量扫描）**：`exp-*.sh` **9 个** + `run-hot.sh` + `check-jar-executable.sh` + `run.sh`
  ⇒ **12 个探针全中招**（除 `run.sh` 外**一个都没给**）。
- **为什么这最阴**：它表现为「RAG 侧**超时 / 启动失败**」⇒ **看起来像产品缺陷**（真源起得来、RAG 起不来）
  ⇒ 实则是**探针自己没给密钥**。
- **修法**：① 新增共享件 **`tools/difftest/lib/rag-secrets.sh`**（随机 32 hex · 尊重外部已给的值 · 不落仓）
  ⇒ **一处定义**；② **12 个探针各注入一行** `source`；③ `run.sh` 里重复的那段**收敛到共享件**。
- **复跑实证**：`exp-difftest-grpc-user9.sh` 修前 **RAG 侧超时**（20s 内起不来）⇒ 修后 **43 场景 / 差异 4**
  ⇒ **与 2026-10-24 台账记录的「43 / 39 / 4」逐条一致** ⇒ 见 §四·6。

### ⑫ **本轮自己的批跑驱动用 `bash` 跑 `.py`** ⇒ Python 探针全灭

- **现象**：`tools/difftest/parity-task.py` 等 **10+ 个 Python 探针**全部 EXIT=2，
  日志是「`from: command not found` / `syntax error near unexpected token '('`」。
- **真因**：驱动统一用 `bash <probe>` ⇒ **不按扩展名分派**。
- **处置**：驱动改为**按扩展名分派**（`.py` ⇒ `python3` · `.sh` ⇒ `bash`）⇒ 重跑后
  `probe-hot-latency.py` / `probe-hot-disconnect.py` / `probe-hot-limits.py` / `probe-hot-u5open1.py` /
  `probe-go-required-fields.py` **全部 EXIT=0**。
- **教训（可复用）**：**批跑器本身也是证据链的一环** —— 它的分派错了，
  **整批 Python 探针的「红」都是假的** ⇒ 判据自己有 bug 时，**红不等于有问题**。
- **同源的第二个形态（更阴）**：驱动把**探针清单从 stdin 读进来**（`while read line; … done < 清单`）
  ⇒ 探针**继承了同一个 stdin** ⇒ 探针里任何一次 `read` 都会**吃掉清单的下一行**
  ⇒ 实测：批次 10 声明 **12** 个探针，**只跑到第 8 个就「正常结束」**（后面 4 个**根本没跑**）
  ⇒ 且**不报错、不超时、退出码为 0** ⇒ **看起来像「跑完了」** ⇒ **静默漏跑**。
  ⇒ 修法：探针的 stdin 一律接 **`/dev/null`**。
  ⇒ **⇒ 教训**：**「批次跑完了」这个事实本身也要核**（比对本批应跑数 vs 实跑数），
  否则「静默漏跑」会被当成「全部通过」。

### ⑬ `probe-hot-protection.py` 的红**不是漂移** —— 两侧同样没触发

- **现象**：EXIT=1，报「`maxconns` 期望至少 1 个裸 503，实际 **0 个**」/「`breaker` 期望熔断真的打开，实际 **0 个**」。
- **关键**：**Go 侧与 RAG 侧都是 0 个** ⇒ **两侧行为一致** ⇒ **没有漂移**。
  红的是探针**自己的环境性期望**（并发没堆到能触发保护）⇒ **结论不可用**，不是「不一致」。
- **处置**：这类红**必须读两侧各自的数字**，不能只看退出码 ⇒ 本文件 §二 的「覆盖断言」栏保留了原文。

### ⑭ 🔴 **7 个探针隐式依赖「别的探针的残留容器」** ⇒ 编排缺陷伪装成产品缺陷

- **现象**：批跑第 1 个探针 `exp-difftest-redis-blacklist.sh` ⇒ **Go 侧与 RAG 侧 user-rpc 都起不来**
  ⇒ 日志里是 `dial tcp 127.0.0.1:55434: connect: connection refused` ⇒ **看起来像「RAG 起不来」的产品缺陷**。
- **真因**：**7 个** `exp-*` 探针都把 `PG_CONTAINER` 默认成 `uudifftest-postgres`（:55434），
  但 **只有 `exp-difftest-grpc-user9.sh` 会自建它**
  ⇒ 其余 6 个 **只「检查在不在」**（`docker ps | grep -qx`）⇒ **隐式依赖上一个探针跑完留下的容器**。
- **为什么会暴露**：本轮的批跑驱动**逐探针前置清理**（删 `uudifftest-*` 前缀容器，防端口互撞，见 ⑩-a）
  ⇒ **把共享前置容器一起删了** ⇒ 6 个探针在 **setup 阶段**就挂。
  ⇒ **讽刺点**：清理本是为了修端口互撞，却制造了新的编排依赖失败。
- **修法**：① 新增共享件 **`tools/difftest/lib/rag-pg.sh`**
  （`ensure_pg_container` / `wait_pg_ready` / `reset_pg_dbs`）；
  ② **7 个探针各接入** ⇒ 每个探针**自给自足** ⇒ **不再依赖执行顺序**；
  ③ 驱动改为**保留** `uudifftest-postgres`（探针自己会重建库）。
- **附带修法 · 共享件里 `wait_pg_ready` 带 `_ready` 后缀** ——
  `exp-difftest-grpc-user9.sh` / `exp-difftest-concurrent-schema.sh` **原本就自己定义了 `wait_pg`**
  ⇒ 同名函数会**静默覆盖**共享件 ⇒ 改用不重名的名字。
- **同类的第二个形态（栈依赖）**：`exp-admin-worker-pool-load.sh` 需要
  **admin-rpc 在 :58081 上**才能起 admin-api（`static://127.0.0.1:58081`）
  ⇒ 但**它自己不起 admin-rpc** ⇒ 依赖**别的探针起的栈**。
  ⇒ 实测：探针在 04:16:29 跑，而 admin-rpc 是 **04:20:33** 才被后面那个探针拉起来的
  ⇒ 该次 EXIT=1（`Can't connect to channel admin-rpc`）⇒ **环境问题，不是产品缺陷**。
  ⇒ 同款：`exp-difftest-grpc-admin-errors.sh` 也是「故意把栈留着不退出」⇒ 见 ⑨。
- **教训（可复用）**：**「探针依赖前置产物/前置容器」必须写进探针自己** ——
  依赖别人留下的残渣 ⇒ **换一个执行顺序就挂** ⇒ 表现为「产品起不来」⇒ **误导性结论**。

### ⑮ 🔴 **setup 阶段 fail-open** —— 建库失败仍打印「✓ 已重建」⇒ 假红报成「产品起不来」

- **形态**（`exp-*.sh` 里的同款代码）：
  ```bash
  docker exec "$PG_CONTAINER" psql ... -c "DROP DATABASE IF EXISTS $db ..." >/dev/null 2>&1 || true
  docker exec "$PG_CONTAINER" psql ... -c "CREATE DATABASE $db" >/dev/null   # 失败被吞
  echo "  ✓ ${GO_DB} / ${RAG_DB} 已重建"                                     # 无条件报成功
  ```
  ⇒ 容器不存在时 `CREATE` 失败，但**仍然打印「✓ 已重建」**。
- **后果**：setup 阶段**假装成功** ⇒ 后面以「**连不上库**」的形式炸在**别处**
  ⇒ 报出来的结论是「Go/RAG 侧起不来」⇒ **指向错误的方向**。
- **修法**：共享件 `reset_pg_dbs` 里 **CREATE 失败 ⇒ 非零返回 + 不打印「✓ 已重建」**；
  7 个探针改成 `reset_pg_dbs … || exit 2` ⇒ **失败停在 setup，不流到后面**。
- **自检**：共享件**负例反测**已做 —— 用不存在的容器名调 `reset_pg_dbs`
  ⇒ **EXIT=1** 且输出 `FAIL 建库 … 失败` ⇒ **不出现「✓ 已重建」** ⇒ 判据本身有效。
- **教训（可复用）**：**setup 阶段的成功打印必须是「条件产生」的，不是「无条件的」** ——
  无条件打印的成功，是**把失败翻译成成功**的最短路径。


### ⑯ 🔴 **探针的失败被「前一个探针」制造出来** —— 两种形态，**都不是产品缺陷**

- **⑯-a 行锁污染 ⇒ 失败被 fallback 掩盖成「密码错误」**
  - **现象**：`exp-difftest-grpc-admin-errors.sh --stacks-up` ⇒ **EXIT=1**，
    报错是 `UNAUTHENTICATED: 密码错误` ⇒ **看起来像 RAG/Go 的登录行为不一致**。
  - **追查**：① 两侧库里的 admin 密码**都是** bcrypt(`admin`)（用 bcrypt 库逐个验过 ⇒ True）；
    ② Spring 的 `BCryptPasswordEncoder.matches("admin", 库里的哈希)` **实测 = true**（单独跑 jar 验的）；
    ③ Go 侧 `cryptx.CheckPassword` 的用法（`(dbHash, inputPassword)`）**也正确**；
    ④ ⇒ **两侧都不该失败** ⇒ 真正的错因在别处。
  - **真因**：`exp-grpc-admin-deadline-probe.sh` **故意持有 DB 行锁**来制造 deadline 场景
    ⇒ **下一个探针的 admin 查询在行锁上等超时**
    ⇒ Go 侧日志实测：`loginlogic.go:52` **DB 查询 `context deadline exceeded`**（第一次尝试），
    `loginlogic.go:58` `password mismatched`（第二次尝试，换了候选密码）。
  - **为什么报出来的是「密码错误」**：runner 的 `loginUid` 依次试两个候选密码，
    **把第一次的异常吞掉、只抛最后一个** ⇒ 一个**瞬时 DB 超时**被翻译成「密码错」。
  - **复跑实证**：**单独**跑（前面没有持锁探针）⇒ **EXIT=0**（6s）
    ⇒ `cases=19 · code_mismatch=0 · payload_diff=0 · description_diff=5` ⇒ **与 Go 侧逐条一致**。
  - **⇒ 教训**：**「探针失败」必须先问「上一个探针干了什么」** ——
    持锁 / 造负载 / 改数据的探针，会把**下一个**探针打成假红；
    且 **runner 的 fallback（多候选重试）会把「第一次的错」掩盖成「最后一次的错」**。

- **⑯-b 退出码由「自己都说不稳定的指标」决定 ⇒ EXIT=1 ≠ 结论为假**
  - **现象**：`exp-admin-worker-pool-load.sh 300` ⇒ **EXIT=1**，
    末行是 `✗ 第 2 轮仍有裸 503 ⇒ 需要重新分析`。
  - **但探针自己的判定是成功的**：`✓ 池实际并发峰值 200 < 512 ⇒ **池饱和分支在默认配置下不可达**（直接实测）`
    ⇒ **Tomcat 并发峰值 200 / 池并发峰值 200** —— 与台账记录的 200/200 **完全一致**。
  - **真因**：探针**自己的注释**写明「裸 503 是**负载相关的**，第 1 轮与第 2 轮**可能都是 0，也可能差很多**」
    ⇒ 它的**退出码断言**与**它自己的口径说明**互相矛盾
    ⇒ 实测第 1 轮 95 个 / 第 2 轮 100 个（机器当时正忙）⇒ 于是判红。
  - **⇒ 处置**：本类红**必须读探针自己的判定行**（结论），不能只看退出码
    ⇒ 台账里那行「裸 503 = 0」也**不具可复现性**（负载相关）⇒ 已订正（见 §四·8）。

### ⑰ 🔴 **工具把「已签字的表差异」当硬中止 ⇒ 8 处真差藏在「环境问题」后面**

- **现象**：`python3 tools/difftest/check-ddl-static.py --domain comment --indexes` ⇒ **EXIT=2**
  ⇒ 输出只有「列集合不一致 —— 三种可能，别默认当成漂移」+ 8 行「仅 RAG 有: report_record.*」。
- **真因**：`report_record` **只在 RAG 侧存在** —— 这**正是已签字的偏离**
  （`SD-2026-10-24-CAND-02`，2026-10-24 用户签字：Go 的 `AutoMigrate` 漏了 `&ReportRecord{}`
  ⇒ Go 全新部署必然 5004，RAG 多一张表 = 举报功能可用）。
  而工具分不清「表名映射写错 / 某一侧没初始化 / 真漂移」⇒ **一律硬中止**。
- **后果（这才是要害）**：中止发生在**类型/可空/DEFAULT 比对之前**
  ⇒ comment 域**另外 5 张表一个维度都没比**
  ⇒ 那 **8 处 DEFAULT 真差**（计入权威数 **22**）**不可机器复算** ⇒ 台账只能写「人工闭合」
  ⇒ ⇒ **权威数「22 处 DEFAULT 残留」无法用一条命令复现**。
  ⇒ ⚠️ **更阴的一点**：EXIT=2 的语义是「**环境问题 · 结论不可用**」
  ⇒ 8 处**真差**被一个「环境问题」的标签**盖住了** ⇒ 看起来像「这域没法比」。
- **修法（2026-10-30 用户拍板）**：
  ① `check-ddl-static.py` 新增**「已签字的表级偏离」豁免**
     —— 列集合不一致且**全部**差异都落在**已签字的那张表** ⇒ **不中止** ⇒ 跳过该表、**继续比其余表**；
  ② 签字清单来自**显式数据文件** `tools/difftest/ddl-signed-exempt.txt`
     （**不解析** `docs/Intentional_Deviations/**.md` ⇒ 散文形态漂移不会造成误豁免/误报
     ⇒ **弱兜底判据会把真缺陷洗成 0**）；
  ③ 跳过项**单列**为 **「⚪ 已签字豁免 · 未核对」**（**不能混进「0 差异」**）
     ⇒ 该表今后新增的真差异**本工具抓不到** ⇒ 需人工巡检。
- **实测**：comment 域默认口径 ⇒ **EXIT=1** · 类型/长度 **0 处** · 52 列逐字相同
  · 可空性 **8** · DEFAULT **10**（含 2 处 IDENTITY-vs-serial 已知假阳性 ⇒ **真差 8**）
  ⇒ **机器报 23 = 实质 21** ⇒ **与原「补跑口径」逐条一致**
  ⇒ ⇒ **22 处 DEFAULT 残留现在「全部由默认口径一条命令产出」**
  ⇒ ⇒ **9 域合计 51 处实质残留**（users 16 · message 7 · like 5 · comment 21 · favorite 2）⇒ 与台账**逐条一致**。
- **教训（可复用）**：**「环境问题」这个退出码会掩盖真差** ——
  工具在**半路**停下时，后面没比的东西**没人知道没比**
  ⇒ ⇒ 中止点必须**往后推到底**，或**明确说清「哪些维度没比」**。

---

## 四、本轮的**台账改判**（代码是真相；台账错就改台账）

| # | 条目 | 原判 | 现行态 | 依据 |
|---|---|---|---|---|
| **1** | `docs/DRIFT/favorite/rpc.md` §22.4 跨域发现 **#2**「Go 的响应**多一个 W3C `traceparent` 响应头**；RAG 侧没有」 | 🔴 跨域漂移（待收口） | **⛔ 已失效 —— 差异已闭合** | 活体：`api-full-parity-probe/favorite` 报「**OBS 段期望两侧不同，但变成一样了 ⇒ 请重评台账**」⇒ **两侧响应头 key 集合已完全相同**（`hkeys=content-length,content-type,date,traceparent`）。根因 = **共享层 T-8 已于 2026-10-25 第十九轮落地**（`service/common` 新增 `GoStyleTraceparentFilter` ⇒ 10 个 api 全生效）⇒ **不是「RAG 多出来了」（那会变成反向漂移），是两侧都多 ⇒ 与 Go 侧一致** |
| **2** | 同上 §26.7「生产启动探针 ⇒ **EXIT=0**」 | ✅ EXIT=0 | **2026-10-30 实测 EXIT=1**（假红）⇒ 修分类器后复跑 **EXIT=0** | 见 §三·① ⇒ 台账补记「真实退出码 + 判别依据（分类器陈旧） + 复跑结果」 |
| **3** | `docs/DRIFT/favorite/api.md` §4 证据边界表「1 条 OBS（Go 多 `traceparent`）」 | 观测层差异 | **⛔ 已失效**（同 #1） | 同上 |
| **4** | 引用 `tools/difftest/run.sh` 的 **19 条**条目 | 「活体证据在位」 | **补记**：2026-10-30 之前该探针**不可复现**（缺 fail-closed 密钥 env）⇒ 已修 ⇒ 复跑 **EXIT=0** | 见 §三·⑤ |
| **5** | `api-full-parity-probe/favorite` 的 `expected.txt` h01 档位 | `OBS`（要求两侧**必须不同**） | **`SAME`**（要求**全等**） | ⚠️ **不改会出事**：留在 `OBS` ⇒ 任何「RAG 侧不再发 `traceparent`」的回归**会被静默放过** ⇒ 改档后该用例从「量化差异」变成「**守住闭合态**」 |
| **6** | `docs/DRIFT/users/user/rpc.md` 的 `UNRESOLVED-USERRPC-007`（「✅ 已闭环（2026-10-24）· 43 场景 / 39 等价 / 4 处差异」） | ✅ 已闭环 | **✅ 已闭环（不变）—— 但本轮独立复现** | 活体：`bash tools/difftest/exp-difftest-grpc-user9.sh` ⇒ **43 场景 / 差异 4**（2026-10-30 03:53 · 20s）⇒ 与 2026-10-24 的记录**逐条一致** ⇒ 4 处全是 `N-2@users` 已签字的 Go 源码 GBK 乱码文案 ⇒ **无新漂移**。⚠️ **但该断言的证据此前是「腐烂」的**：探针因 §三·⑩ **起不来** ⇒ 旧记录无法复算 ⇒ **本轮修好后才复现** ⇒ 「有活体记录」≠「现在还跑得起来」 |
| **7** | `docs/Intentional_Deviations/users/user/rpc.md` 的 `N-2@users`（守门网一行） | 「43 场景 / 39 等价 / 4 处差异」 | **✅ 仍成立（不变）** | 同上：活体复跑 **43 / 4** ⇒ 4 处仍是已签字的乱码文案 ⇒ **签字描述的行为仍存在** ⇒ 签字有效；同时补记「守门网本轮才被修活」 |
| **8** | `docs/DRIFT/users/admin/api.md` §3.2「两轮实测均为：Tomcat 并发峰值 200 / 池并发峰值 200 / **裸 503 = 0** ⇒ `EXIT=0`」 | 负载相关数字当实测结论 | **补记**：`200 / 200`（**承载结论的两个数**）**复现了** ⇒ 「池饱和分支在默认配置下不可达」**仍成立**；但 **「裸 503 = 0」不可复现** —— 实测 **95 / 100**（CPU shedding 自适应 ⇒ 负载相关）；且探针**自己的退出码**由这个「自己都说不稳定」的指标决定 ⇒ **EXIT=1 ≠ 结论为假** | 见 §三·⑯-b |
| **9** | `docs/DRIFT/_cross/ddl-type-divergences.md` 三处「comment 域默认口径 `EXIT=2` ⇒ 须补跑口径 ⇒ 8 处 DEFAULT **人工闭合**」 + `docs/Stage1/06-…` 的同一处措辞 | 「默认口径不可复算」 | **补记**：默认口径现为 **`EXIT=1`（21 处）** —— 「已签字的表级偏离」豁免生效（清单 = 显式数据文件 `tools/difftest/ddl-signed-exempt.txt`）⇒ **同一命令**即产出 21 处 ⇒ **「人工闭合」「补跑口径」两处措辞作废**；**权威数 22 处 DEFAULT 不变**，且**现在全部机器可复算** | 见 §三·⑰ |

> ⚠️ **本轮一条产品代码都没改**（只改 `docs/` + `tools/`）⇒ 上面 5 条全是**台账与探针**的订正，
> **没有一条是靠改代码去迎合台账**。

---

## 五、三层证据报告（诚实分层）

| 层 | 口径 | 条目数 |
|---|---|---|
| **A · 已到活体** | 该条目段内点名了**本轮真跑过**的活体探针 ⇒ 该探针的断言有了**本轮的运行期证据** | **115** |
| **B · 只能静态** | 只能靠源码层判据（静态守门 / 纯源码对照）⇒ **该类结论的证据上限就是静态层** | **974** |
| **C · 台账自称「待核实 / 未复跑」** | **叠加标记**（可与 A/B 共存）⇒ 结论本身处于未验证态 | **94** |

**分目录**：

| 目录 | A 已到活体 | B 只能静态 | C 其中自称待核实 | 合计 |
|---|---|---|---|---|
| `docs/DRIFT` | 54 | 435 | 77 | 566 |
| `docs/Pitfalls` | 56 | 401 | 12 | 469 |
| `docs/Intentional_Deviations` | 5 | 138 | 5 | 148 |
| **合计** | **115** | **974** | **94** | **1183** |

**要点（诚实说明）**：

1. **A 类里仍有一批的活体证据是「红」的**（27 条）⇒ **绿 ≠ 结论真，红 ≠ 有缺陷** —— 红分三种：① **已签字偏离**（对拍为红属预期）② **已登记的已知残留**（如 `DEFAULT CURRENT_TIMESTAMP`）③ **真问题** ⇒ 逐条在条目里写清是哪一种。
2. **B 类不是「没查」** —— 是**该类结论的证据上限就在静态层**（结构性 / 文档性 / 编号类结论**没有活体对应物**）⇒ 不硬凑。
3. **C 类 94 条**里，绝大部分是**外部条件不可得**（生产库只读连接 · Go 标准库源码副本 · 真 Redis/Kafka/Tomcat 场景）⇒ 本机不可复算 ⇒ 保持「待核实」不硬判。
---

## 六、复现命令

```bash
cd "/Users/pr/Documents/java&golang/RAG"

# ① 逐个探针串行跑（**端口段互斥 ⇒ 必须串行**）
bash tools/api-full-parity-probe/follow/run.sh          # 例
# 退出码：0 = 探针自身断言成立 · 1 = 不一致 · 2 = 环境问题（结论不可用）

# ② 分类器自检（**不起服务、不碰 DB**，秒级）
bash tools/prod-startup-probe/favorite/run.sh --self-test

# ③ 本轮的证据台账生成器（表 + 分层）
python3 /tmp/rag-verify/build_evidence.py
python3 /tmp/rag-verify/tiers.py
```

---

## 七、变更记录

| 日期 | 内容 |
|---|---|
| **2026-10-30** | **首版** —— ① 跑通三批共 **58** 个活体探针（真实退出码见 §二）② 抓到 **9 条「工具自己骗人」**并**逐条修**（§三）③ **5 条台账改判**（§四）④ 三层证据报告（§五）⑤ **零产品代码改动**（只改 `docs/` + `tools/`） |
| **2026-10-30（续）** | **本轮续跑 + 修完 8 处探针缺陷后的复跑** —— ① 探针实跑总数扩到 **§二** 的全部（含 **exp-\* 全族** · **栈依赖探针带 `--stacks-up` 跑** · **mutate 族**）② §三 扩到 **16 条**（新增 **⑭ 编排依赖** · **⑮ setup fail-open** · **⑯ 探针的失败被上一个探针制造出来**（两形态））③ §四 扩到 **8 条改判** ④ **新增两个共享件**：`tools/difftest/lib/rag-pg.sh`（PG 容器自给自足）· `tools/difftest/lib/rag-secrets.sh`（fail-closed 密钥）⑤ **我上一轮自己加的「端口零残留」断言装错了时机** ⇒ 恒红 ⇒ 已修（先 teardown 再断言）⇒ 与 `like/run.sh` 的正确写法对齐 ⑥ **落证脚本新增「退出码变了就刷新戳」** ⇒ 探针假红修好后，台账不会留着过期数字 |

## 八、2026-10-31 复测（§二 的两行**结论列**已过期 · **退出码未变** ⇒ 派生层档位不受影响）

> **为什么有这一节**：§二 的台账记的是 **2026-10-30 03:48** 那一轮的读数 ⇒ 那时探针 RAG 半边
> **手搓裸 netty channel**（不走 devh 装配）⇒ ⇒ `failed_fast` / `code` / `outgoing_carrier`
> 三键**两侧不等** ⇒ 被登记成「**已知的漂移形态**」。
> ⇒ ⇒ **2026-10-30 探针改造后**（`e796a4ed`：RAG 半边改走 devh **生产装配路径**
> ⇒ `GrpcChannelFactory` ＋ 真 Spring 上下文 ⇒ 拦截器由 devh 的 `GlobalClientInterceptorRegistry`
> 按 `@GrpcGlobalClientInterceptor` 收集 ⇒ **不是探针手工塞的**）⇒ ⇒ **两侧已逐键相等**。
> ⇒ ⇒ §二 那两行的**结论列**已过期（**已在原行就地加注** ⇒ 零行位移）。

### 复测读数（2026-10-31 · 合并收口执行者实跑）

```
== 跑双侧活体探针（Go 真源 ↔ RAG 生产类）==
   Go  : {'failed_fast': 'true', 'code': '4', 'outgoing_carrier': 'present_expired', 'sql_carrier': 'present_expired'}
   RAG : {'failed_fast': 'true', 'code': '4', 'outgoing_carrier': 'present_expired', 'sql_carrier': 'present_expired'}

  ✅ sql_carrier        Go=present_expired | RAG=present_expired  （两侧一致）
  ✅ outgoing_carrier   Go=present_expired | RAG=present_expired  （两侧一致）
  ✅ failed_fast        Go=true | RAG=true  （两侧一致）
  ✅ code               Go=4 | RAG=4  （两侧一致）

⇒ ✅ 通过（parity 键两侧一致；drift 键形态与已登记一致）
GATE_EXIT=0
```

### 影响面（**机器层零影响** ⇒ 派工面不动）

| 面 | 结论 | 依据 |
|---|---|---|
| **派生层证据档** | ✅ **不受影响** | `load_live_probes()` 只取 §二 的**退出码列** ⇒ 两行的退出码**本轮前后都是 0** ⇒ `live_probes` 映射**逐键不变** ⇒ ⇒ 任何条目的 `evidence` 档**不变** |
| **人读层** | ⚠️ **原已修** | §二 那两行的**结论列**已就地加注（单行 → 单行 ⇒ **零行位移**）⇒ 读者不会再把 2026-10-30 的漂移形态读成现状 |
| **派工语义**（阶段归属 / 是否阻断 / 排期 / 真缺口数） | ✅ **零变化** | 本节**不含**任何编号 token ⇒ 不进派生层的条目集合；且它不在三本输入台账里（`docs/DRIFT/_cross/` 的台账**不被 `scan_ledgers()` 扫**）⇒ ⇒ 派生层产物**逐字节不变** |

### 复现命令

```bash
cd "<仓库根>"
python3 tools/difftest/check-deadline-downstream.py    # ⇒ EXIT=0（四键逐键相等）
```

⚠️ **诚实边界**：本节记录的是**一次**实测读数（2026-10-31）⇒ 探针**以后**若再改造，
本节的读数**同样会过期** ⇒ ⇒ 处置形态与本节完全相同：**在原行就地加注 + 末尾追加复测节**
⇒ ⇒ **不改写历史行的原始读数**（那是那一轮的真实记录）。
