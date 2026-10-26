# DRIFT 台账总索引

> 本目录是 **AGENTS.md 漂移档案的物理落点**。
> AGENTS.md 保留「政策 + 索引」，本目录承接「逐条漂移与修复的完整证据」。
> 搬移于 2026-10-23，来源：AGENTS.md @ `81744d1` + 工作区未提交增量。

## 搬移账目

| 项 | 值 |
|---|---|
| 源文件 | `AGENTS.md` 369,625 B / 4094 行 |
| 搬出 | **39 段 / 3,629 行 / 330,494 B**（另 4 处手工搬移：§7.1 锚点表 / §4.3 原话 / §6.3 决策详述 / §0 原文快照）<br>⚠️ **2026-10-25 订正**：`_MANIFEST.json` 的 `entries` 实测 **40 段** —— **搬移本身仍是 39 段**，第 40 段（`_cross/fix-round-2026-10-23.md` L54-101）是**搬移之后**补登记的（`e993ff0`）⇒ 本行的「39 段」指**搬移动作**，与 manifest 的条数不是同一口径 |
| 保留 + 压缩 | AGENTS.md 搬移当时 **30,207 B / 396 行** ⇒ **2026-10-25 现为 32,734 B / 370 行**（§0 结论订正 + §7.2 守门脚本 ⇒ 仍在 **32 KB 预算内**，余 **34 B**） |
| 校验（搬移当时） | **零丢失 · 零重复**（逐字节）；删除前另有 39 段互不重叠 + 覆盖全文无空隙的检查 |
| ⚠️ **校验（2026-10-25 复核）** | 🔴 **`tools/drift-move.py verify` 已转 `EXIT=1`（4 段）** —— `like/rpc.md` L57-71 · `message/rpc.md` L72-101 · `message/api.md` L102-420 · `_cross/fix-round-2026-10-23.md` L54-101 **原文已找不到**。<br>**原因**：**10-25 的 10 域并发对齐把各域台账正文重写了**（追加了大量新轮次章节）⇒ 被搬的那几段原文不再是逐字节可校验的形态。<br>⇒ **本行「零丢失」是 2026-10-23 搬移当时的口径，不是现状**；现状以本行为准。<br>⚠️ **不要**为了让它转绿而回改各域台账正文（那是真实产出）⇒ 该基线（`AGENTS.md` @ `81744d1`）已**作废**。 |
| 逐段指针 | [`_MANIFEST.json`](./_MANIFEST.json) —— 每段带 `AGENTS.md L<a>-L<b>`；6 段进 `docs/Pitfalls/`；`entries` 实测 **40** |
| 可重跑脚本 | `tools/drift-move.py`（幂等；重新执行会覆盖同路径文件） |

每个搬入文件的首行是 `<!-- @move AGENTS.md L<a>-L<b> · 标题 -->` 哨兵行。
**正文为原样字节拷贝，未改一字、未重排、未润色。** 校验方式：剥离哨兵行后与源行区间逐字节比对。

## 按域索引

| 域 | 台账 | 状态 |
|---|---|---|
| **`users`** | [`users/_shared.md`](./users/_shared.md)（67 KB，最大）<br>[`users/user/api.md`](./users/user/api.md) · [`users/user/rpc.md`](./users/user/rpc.md)<br>[`users/admin/api.md`](./users/admin/api.md) · [`users/admin/rpc.md`](./users/admin/rpc.md)<br>[`users/admin/_shared.md`](./users/admin/_shared.md) | 唯一做过完整 Go↔RAG 行为审计的域 |
| **`message`** | [`message/api.md`](./message/api.md) · [`message/rpc.md`](./message/rpc.md)<br>[`_cross/message-domain-wide.md`](./_cross/message-domain-wide.md) | 3 处请求解析层漂移 + 6 项启动级缺陷全关 |
| **`like`** | [`like/api.md`](./like/api.md) · [`like/rpc.md`](./like/rpc.md) | 事实投递链路**跨三个仓库**（含 `Sea-BreakTheWaves`） |
| **`common`** | [`common/alignment-2026-10-23.md`](./common/alignment-2026-10-23.md) ⭐ **组件级重对齐（最大一份，27 条编号未修）**<br>[`common/protection.md`](./common/protection.md) · [`common/http.md`](./common/http.md)<br>[`common/deadline.md`](./common/deadline.md) · [`common/audit.md`](./common/audit.md)<br>⚠️ 签字偏离已迁出 ⇒ [`../Intentional_Deviations/`](../Intentional_Deviations/_INDEX.md) | 🔴 **不是「请求保护层四层的最终落点」** —— `alignment-2026-10-23.md` §〇 已推翻「audit.md 标『✅ 审计完成』的覆盖面不成立」：5 个可比包 **3 个 NOT EQUIVALENT**（communityfact / logger / observability / snowflake），仅 `response` 逐条等价。**未修 = 27 条编号条目**（DRIFT-COMMON **2** + communityfact 15 + logger 7 + snowflake 3）+ 5 项未编号观察 ⇒ **common 是共享层，其他域的尾巴挂在它身上**（like-api 015 / follow-api 008 / hot D14·D25·D27 / message -010·-011·-013 的落点全在此）<br>✅ **2026-10-25 校准轮（§十二）**：① 28 → **27** 条（`DRIFT-COMMON-004` 已由 hot 域关闭）；② **§十二·二 新增「10 条外部尾巴」清单**（含落点 `文件:行号` + 真实状态）⇒ **T-3（nosniff）实测已修未销账**、**T-4（499）仍开放且全仓无守门网**；③ 9 项交接真实分布 = **已销账 5 / 未销账 2（#2 假断言 / #8 错放文件）/ 仍未决 4**；④ **§十二·四 找到一条「两步闭环」**（改 `UserDomainService.java:113` 一行 javadoc ⇒ 删 `RpcInterceptor.java:617` 的 `markExpired` ⇒ **消 2 条编号 + 1 条交接**） |
| **`hot`** | [`hot/rpc.md`](./hot/rpc.md) · [`hot/common.md`](./hot/common.md)<br>[`hot/api.md`](./hot/api.md) | ✅ **三组件现已全部拿到双侧活体对拍**（common 逐字节一致 · rpc 第十一轮真 Kafka + 2×Redis · api 第五轮端到端 29 条）。✅ **2026-10-25 第十二轮**：**hot 域正式认领并修复 `DRIFT-COMMON-004`**（hot-rpc 缺 `GrpcServerConfigurer` ⇒ 拦截器从未挂上 server ⇒ 无 span / **无 deadline 传播** / SQL 不注入 `queryTimeout`）—— 此前该缺口**只在 common 域台账里有记录、hot 域三份台账零提及**。守门：`HotGrpcInterceptorWiringTest`（3 条**行为级**）+ **两档变异**（含「保留 Bean 摘掉 `server.intercept`」的精确 H2 形态）；`check-grpc-interceptor-wiring.py` ⇒ **`EXIT=0`**（修前 `EXIT=1`）。<br>✅ **2026-10-25 第十三轮**：**D21 / D22 / D23（Tomcat 容器输入上限三旋钮）已闭合并已修** —— 10 个 api 模块的 `application.yml` 统一配 `relaxed-query-chars: ["[", "]"]` / `max-parameter-count: 2048` / `max-http-request-header-size: **1028KB**`（⚠️ **不是 1MB**：Go 真实上限 = `DefaultMaxHeaderBytes(1<<20)` **+ 4096 bufio slop**）⇒ 双侧活体复测：`?page[]=5` 两侧 **200 且响应体逐字节相同**、值个数首个翻转 n **两侧都是 2049**、请求行首个翻转长度 **两侧都是 1052536 字节（差 0）**；守门 = `check-tomcat-limits.py`（静态 10/10 ✅，两档变异 `EXIT=1`）＋ `HotApiRealTomcatGateTest` 3 条**真 Tomcat**断言（**MUT-A/B/C 变异全红**）。⚠️ **新查出 2 条残余**：`DRIFT-HOTAPI-030`（两层限制语义不同 ⇒ **跨域待拍板**）/ `DRIFT-HOTAPI-031`（超长请求行 431 vs 400 ⇒ **框架限制**，候选 `SD-2026-10-25-CAND-10`）。⚠️ **剩余仍在域外**：D14 / D25 / D27 移交 `service/common` |
| **`article`** | [`article/common.md`](./article/common.md)<br>[`article/api.md`](./article/api.md) · [`article/rpc.md`](./article/rpc.md) · [`article/proto.md`](./article/proto.md) | 🔴 **api 与 rpc 均已对照，且都仍判 `NOT EQUIVALENT`**：api 第十三轮（41 文件 / 5,733 行，2026-10-24 修复轮 4 条未修漂移已修 + 变异验证）判定**不变**（仍有 DRIFT / UNRESOLVED 在，无双侧活体对拍）；rpc 13 条已改，**S-5 阻塞于 security 域迁移**，MQ 事件契约未冻结，U-2 真并发用例未写 |
| **`favorite`** | [`favorite/rpc.md`](./favorite/rpc.md) | **rpc 已落地 + 首轮源码级对照（2026-10-23）**：36 文件 / 7,057 行 / **56 例绿**（真 PG）；本轮**修掉 6 处 L2 语义偏差**（含 3 处「写路径误用读路径 5007」）。🔴 **2026-10-25 本轮未做**（合并时按用户要求跳过）⇒ `api`/`common`/`proto` 仍零台账 |
| 🆕 **`comment` / `follow` / `task`** | [`comment/api.md`](./comment/api.md) · [`comment/rpc.md`](./comment/rpc.md) · [`comment/common.md`](./comment/common.md) · [`comment/proto.md`](./comment/proto.md)<br>[`follow/api.md`](./follow/api.md) · [`follow/rpc.md`](./follow/rpc.md) · [`follow/common.md`](./follow/common.md) · [`follow/proto.md`](./follow/proto.md)<br>[`task/rpc.md`](./task/rpc.md) · [`task/api.md`](./task/api.md) · [`task/proto.md`](./task/proto.md) · [`task/_README.md`](./task/_README.md) | **2026-10-25 并发对齐后**：三域「零台账」状态已解除，各自 4/4/3 组件全部完成首轮对照。⚠️ 合并前的旧状态见 [`_cross/new-domains-2026-10-23.md`](./_cross/new-domains-2026-10-23.md) 与 [`_cross/component-coverage-matrix.md`](./_cross/component-coverage-matrix.md) |
| 🆕 **`global`**（收口域） | [`global/_README.md`](./global/_README.md)<br>[`global/common-tails.md`](./global/common-tails.md) · [`global/cross-domain.md`](./global/cross-domain.md) · [`global/cycle-deps.md`](./global/cycle-deps.md)<br>🆕 [`global/lanes/_PROTOCOL.md`](./global/lanes/_PROTOCOL.md)（多 lane 并行协议 + 15 份任务书）<br>🆕 **lane 台账（自足片段，共享文件由整合者 fold）**：[`L1-delivery.md`](./global/lanes/L1-delivery.md)（第二十~二十五轮 · 含 Go 侧活体探针 + T-6）· [`L2-rpcgate.md`](./global/lanes/L2-rpcgate.md)（X-5 残留）· [`L3-ddl.md`](./global/lanes/L3-ddl.md)（五域 DDL 双侧活体）· [`L4-flyway.md`](./global/lanes/L4-flyway.md)（三进程同库共存）· [`L5-signoff.md`](./global/lanes/L5-signoff.md)（C 类登记 + T-11/T-12 定性）· **第二十七轮 6 条**：[`L6-x5gate.md`](./global/lanes/L6-x5gate.md)（X-5 轴 + article slow 轴 + 缺口④ 复评）· [`L7-slowgate.md`](./global/lanes/L7-slowgate.md)（comment/like 的 X-5 + slow 轴）· [`L8-slowgate2.md`](./global/lanes/L8-slowgate2.md)（user/message/hot 的 slow 轴）· [`L9-interrupt.md`](./global/lanes/L9-interrupt.md)（task/follow 的 slow 轴 + 缺口③ 定性）· [`L10-ddldeep.md`](./global/lanes/L10-ddldeep.md)（DDL 触发器函数体 + runner 重建）· [`L11-flywaydeep.md`](./global/lanes/L11-flywaydeep.md)（Flyway 三条边界）· **第二十八轮 4 条**：[`L12-rpcboot-article-comment-like.md`](./global/lanes/L12-rpcboot-article-comment-like.md)（两斜杠 ×5 + scope ×2）· [`L13-rpcboot-message-task.md`](./global/lanes/L13-rpcboot-message-task.md)（两斜杠 ×2 + scope + Main-Class）· [`L14-rpcboot-hot.md`](./global/lanes/L14-rpcboot-hot.md)（hot-rpc 无 Main-Class —— L11 漏报的那一条）· [`L15-rpcboot-tools.md`](./global/lanes/L15-rpcboot-tools.md)（两个守门缺口：target/ 重复计数 + rpc 产物从未被查） | **跨微服务 + `service/common` 影响的搁置漂移**收口处（§3.5）。**不是第 11 个微服务域**，按议题分档。已推进 **45 轮**：common 尾巴 T-1~T-16（**T-6 已修** · T-11 已修 · **T-14 已修** · **T-15 ③ 已做** · **T-16 第①层已修**）· **第二十八轮：N6/N7/N8 已修**（真 fat jar 起不来那批）+ **两个守门缺口闭合**· **第二十九轮：盘点口径三来源合流**（+ ② 各域台账的域外交接段）· **第三十轮：E30 已修**（RPC 超时的 status description ⇒ Go 的 `context deadline exceeded`）+ **T-14 已修** + **T-15 ③** + **第三个双侧活体探针** `tools/semantic-probe/rpc-timeout/`· **第三十一轮：E38 第①层已修**（超时有效期限 ⇒ `min(客户端 deadline, 服务端 timeout)`；第②层另立项）· **第三十二轮：E40/E41/E44 已修**（共享复核工具三处缺陷：第 1 层零去噪 ⇒ 新增 N8~N13；第 4 层输出不可复现 ⇒ `sorted(srcs)`；自检阳性对照分支相关 ⇒ 改「复原后==变异前」）· **第三十三轮：E46 落地**（SQL 取消时刻 == deadline 时刻 ⇒ pgjdbc 毫秒精度 + 已过期立即失败）+ **E38 台账订正**（探针 2026-10-24 就已存在 ⇒ 真缺口是未进编排 ⇒ 另立 E48）+ **新登记 E47**（业务层吞掉 DEADLINE_EXCEEDED · 24 处硬改写 + 3 处静默降级）· **第三十四轮：E50~E53 已修**（共享复核工具四缺陷：去噪**行级化** ⇒ favorite 域 **181** 条引用从「178 条被淹没」变成「**FAIL 0 → 1**（真缺陷浮出）」+ 权威清单换成真源 `go.mod`（含 `// indirect`）+ **`module@version/...` 精确定位**（消 users 域 3 条假 FAIL）+ **`<GOROOT>/src/...` 标准库通道**⇒ **纯 Python 改动 ⇒ 净增 0 条**）· **第三十五轮：E55/E56 已修**（共享复核工具的**域适配** ⇒ 别的域跑它不再拿到 task 域的结论 + **commit 清单口径** ⇒ 「与主干的公共祖先」换成「登记物自己构成的链」⇒ **主仓 45 → 1**（剩的那 1 条是真问题）· **align/global 141 → 0** ⇒ **纯 Python 改动 ⇒ 净增 0 条**） ⇒ 跨域不变量 X-1~X-10 · 循环依赖 CYCLE-001~002；**待办清单 ⇒ `_README.md` §七之二** |
| **`_cross`** | [`_cross/open-items.md`](./_cross/open-items.md)<br>[`_cross/decisions.md`](./_cross/decisions.md) | 已知未修表 + 决策详述（**签字偏离已迁出** ⇒ 见 `docs/Intentional_Deviations/`） |

### 🟢 2026-10-25 增量（本轮已修 · 两处「域内桶」缺口）

| 项 | 修前 | 修后 | 台账 |
|---|---|---|---|
| **`DRIFT-COMMON-004`**：`hot-rpc` 缺 `GrpcServerConfigurer` ⇒ 拦截器**从未挂上 server**（无 span / **无 deadline 传播** / SQL 不注入 `queryTimeout`） | 🔴 `check-grpc-interceptor-wiring.py` **`EXIT=1`** | ✅ **`EXIT=0`**（10/10 接线完整） | [`hot/rpc.md`](./hot/rpc.md) 第十二轮 |
| **`DRIFT-MSG-V-030`**：6 个 api 模块（article/comment/follow/hot/like/task-api）产物**不可执行** | 🔴 `check-jar-executable.sh` **`EXIT=1`** | ✅ **`EXIT=0`**（10/10 可执行 fat jar） | [`message/rpc.md`](./message/rpc.md) 第 6 节 |

⇒ **全量回归**：**2235 tests / 0 failures / 0 errors / 0 skipped**（**52 模块** · 2026-10-26 实测）⇒ 满足「绿灯必须额外断言 `skipped=0`」纪律。<br>⚠️ **2026-10-25 订正**：本行原写 **2110** —— 与实测差 1，**不是测试少了一条，是统计口径差**（见 [`../Pitfalls/_cross/testing-pitfalls.md`](../Pitfalls/_cross/testing-pitfalls.md)「陈旧 surefire 报告静默抬高测试计数」）。⇒ **现行口径：测试数只取回归日志的模块级汇总，不取 `target/surefire-reports/TEST-*.xml` 求和**（求和会被 10-23 留下的孤儿报告抬高）。
> ⚠️ **2026-10-26 更新（第二十九~三十五轮）**：本行的数字已更新到 **2310 条 / 52 模块**（`align/global` @ 第三十五轮收口）⇒ 第二十七轮是 **2263 条** ⇒ 第二十八轮净增 **38 条**（本轮新增 13 个守门类 / 44 条用例中，**在 `mvn test` 阶段跑的 38 条**；余 6 条 = 2 个 `*FatJarArtifactGateIT` 绑在 verify 阶段）⇒ **第三十轮净增 2 条**（E30 守门 `RpcInterceptorTimeoutDescriptionGateTest`）⇒ **第三十一轮净增 2 条**（E38 守门 `RpcInterceptorClientDeadlineGateTest`）⇒ **第三十二轮净增 0 条**（**纯 Python 工具改动** ⇒ 不动 Java 测试 ⇒ 数字不变 ⇒ 账对得上）⇒ **第三十三轮净增 5 条**（E46 守门：`service/common` 单测 **+3**（10 条，原 7 条）+ comment-rpc **真 PostgreSQL** 守门 **+2** ⇒ 账对得上）⇒ **第三十四轮净增 0 条**（**纯 Python 工具改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第三十三轮**同值 2310** ⇒ 账对得上）⇒ **第三十五轮净增 0 条**（**纯 Python 工具改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第三十四轮**同值 2310** ⇒ 账对得上）⇒ **第三十六轮**为**纯文档轮**（只订正台账 / 登记新条目，**无任何 Java 或工具改动** ⇒ **不重跑全仓回归** ⇒ 数字与第三十五轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第三十七轮净增 0 条**（**纯 Python 工具改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第三十六轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第三十八轮**为**纯文档轮**（favorite owner 回执的**理由**订正 ⇒ E46 / E47 单元格补记 + **AGENTS.md 的 favorite 行订正** + 新坑 PIT-GLOBAL-053 ⇒ **无任何 Java 或工具改动** ⇒ **不重跑全仓回归** ⇒ 数字与第三十七轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第三十九轮**为**纯文档轮**（合回与否拍板留痕 + 统一措辞 + E30 陷阱留痕 ⇒ **无任何 Java 或工具改动** ⇒ **不重跑全仓回归** ⇒ 数字与第三十八轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第四十轮净增 0 条**（**纯 shell/python 改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第三十九轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第四十一轮净增 0 条**（**纯 shell/python 改动**（探针 / 守门 / 编排全是 shell+python）⇒ 不动任何 Java 测试 ⇒ 数字与第四十轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第四十二轮净增 0 条**（**纯文档 + 一份工具 README 改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第四十一轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第四十三轮净增 0 条**（**纯 Python 工具改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第四十二轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第四十四轮净增 0 条**（**纯 shell/python + 文档改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第四十三轮**同值 2310**（**2026-10-26 核对**）⇒ 账对得上）⇒ **第四十五轮净增 4 条**（**E62 落地**：新增 Java 用例 ⇒ `service/common` **+3**（unwrap 逻辑 3 条）· `comment-rpc` **+1**（真池路径的毫秒级断言）⇒ 数字 **2310 → 2314**（**2026-10-26** 核对）⇒ **账对得上**）⇒ **0 failures / 0 errors / 0 skipped** ⇒ **两口径一致**（模块级汇总 29 个模块 = **2310** = XML 306 份求和）⇒ 跑前删过全部旧 surefire XML ⇒ 无历史残留污染 ⇒ 耗时 **19:04 min**⇒ ⚠️ **第三十一轮第一次回归是假红**（users/user 守门 `BindException` ⇒ **单独复跑 ×3 全绿** ⇒ 与改动无关 ⇒ 坑 **PIT-GLOBAL-038**）⇒ ⚠️ **统计口径**：失败模块的汇总行前缀是 `[ERROR]` ⇒ 只 grep `[INFO]` 会漏掉整模块（差 129 = 失败模块）⇒ **第四十六轮净增 0 条**（**纯 shell/python + 文档改动** ⇒ 不动任何 Java 测试 ⇒ 数字与第四十五轮**同值 2314**（**2026-10-26** 核对）⇒ **账对得上**）
· **第三十六轮：E47 全面订正**（逐条核 Go 侧 ⇒ **原定性方向相反** ⇒ **21 处销案**（comment/like/follow/article 与 Go 一致）· **2 处静默降级销案** · **真漂移另有其人**：E58（user/admin · RAG 更准 ⇒ 走有意偏离候选）· E59（message · RAG 更糊）· E60（like · 少传 error））
· **第三十七轮：E61 已修**（共享复核工具第 1 层的「状态叙述」词表太窄 ⇒ 用**正常中文**写状态叙述（「查不到 / 悬空 / 预期状态 / 尚未存在 / 待合入」）仍被报成悬空引用 ⇒ ⇒ **别域只能改台账去迎合工具** ⇒ 修法 = **N13 拆三层**（存在性词 · **落地态** · **免责**）+ **缺陷标记护栏** ⇒ **裸词只靠免责半句才生效** ⇒ 层 0 **5 条对照** + 端到端变异 **M9/M10/M11** ⇒ **前后 diff 零回归**（主仓 157 份台账 ⇒ 缺失 2 → 2 · 去噪 113 → 113）⇒ **纯 Python 改动 ⇒ 净增 0 条**）
· **第三十八轮：favorite owner 回执的「理由」订正**（结论不变、理由写准 · **E46** 补记毫秒精度对 favorite 的准确论证（**≤999ms 窗口 ⇒ 差 = δ 毫秒级，不是 0**）· **E47** 订正「favorite 的 SQL 语句层未做」（favorite **第二十轮**已做 ⇒ **四层全闭** ⇒ **抓出 3 处 L1 可观察漂移** ⇒ 「域内未知项归零」是**提前收口**）· 新坑 **PIT-GLOBAL-053**（**跨域回执的行号必须在回执方分支上复核**）· **AGENTS.md** favorite 行订正）
· **第三十九轮：合回与否已拍板（暂不合回）**（Jason 拍板 ⇒ `align/global` 与 `align/favorite` **都不并入 `do-my-own-gh`** ⇒ global 的「合回时机」**待拍板项结掉** ⇒ 三域**统一措辞**「已提交并推双远端（`<commit>`），**待合回 `do-my-own-gh`**；📌 合回与否已拍板（2026-10-26）：暂不合回 ⇒ **维持「待合回』不是「忘了合」**」⇒ 🔴 **陷阱留痕**：合回前**主仓代码仍是旧串** ⇒ **任何基于主仓的验证都看不到 E30 的修复** ⇒ 别误读成「修复没做对」⇒ 🟢 **合回预演 EXIT=0**（users/admin 域 owner 已核实）· **E48 仍开着**（探针没进 `run.sh` 编排 ⇒ A 类））
· **第四十轮：E48 已修**（客户端 deadline 档探针**进编排** ⇒ 但复核发现**根因更深**：探针的「判定」**从来没被实现**（头部写着 `exit 1 ⟺ 两侧差异 > 1s`，尾部却是**无条件 `exit 0`**）⇒ ⇒ **接进编排只会是「假闸门」** ⇒ **判定抽成可单测的纯函数** + 探针透传退出码（**0=PASS / 1=FAIL / 2=INCONCLUSIVE**）+ **`run.sh` 在 `diff.py` 之后调闸门**+ **22 条断言**（带稳定 ID）+ **变异 5 条全部精确命中** ⇒ 🟡 **并订正第三十三轮的评估**（端口**本来就支持 env 覆盖**）⇒ 纯 shell/python 改动 ⇒ **净增 0 条**）
· **第四十一轮：E48 真跑第一次拿到**有效**观测 ⇒ 顺带挖出 **E62****（🔴 探针**施加侧**（加锁）**写死容器名**、**观测侧**用 `$PG_CONTAINER` ⇒ 两者指向不同容器 ⇒ **锁从未生效** ⇒ 查询没被阻塞 ⇒ **自然没有取消事件** ⇒ ⇒ **前三轮真跑拿到的全是无效观测**（不假绿，但**结论是误诊**）⇒ 且**探针头部早就写对了这条警告** ⇒ **注释里的警告 ≠ 代码里的约束** ⇒ 修法五件（施加侧与观测侧同容器 · **锁生效自证**（pg_locks **持有者视角**）· **前提不成立 ⇒ fail-fast**（`exit 4`）· **阴性文案改列举成因** · `run.sh` **端口预检豁免「自己的」占用**）⇒ 🟢 **守门 24 → 30 条断言** + **变异 7 → 15 条全精确命中**（且**守门自己被自己的改动弄坏两次** ⇒ 判据必须锚代码结构/分支）⇒ 🟢 **真跑**：长档两侧 Δ_end 一致（−5/+5）⇒ **对照组成立**；短档 Go **Δ_end=+0ms**（继承 ✓）· RAG **Δ_end=+833ms** ⇒ **PASS**⇒ 🔴 **第四十轮「信号 ≈1700ms」的推算被实测证伪**（实测 833ms）⇒ 🔴 **新登记 E62**：RAG 的 `Δ_start=+1139ms` ≈ **1s** ⇒ 日志实证 **`HikariProxyPreparedStatement 不支持毫秒级 queryTimeout ⇒ 退回秒级向上取整`** ⇒ ⇒ **E46 的毫秒精度在生产路径上没生效** ⇒ **11 个组件受影响** ⇒ ⇒ **E38② 的「架构级改造是闭环必要条件」需重估**（🟡 **尚未验证** ⇒ 本轮**不改 E38② 定性**）⇒ 纯 shell/python 改动 ⇒ **净增 0 条**）
· **第四十二轮：users/admin 域 owner 回执 ⇒ 他们纠正了我一处「旧版修法描述」**（我第四十一轮回信里写的第③层修法是 **E46 修正前的旧版**：量级被高估 + **「interrupt」单独做不足** ⇒ pgjdbc 不响应线程中断 ⇒ 必须配「取消正在跑的 JDBC 语句」）⇒ ⇒ 订正 = 回信改口 + **把订正镜像进 E38 单元格**（E46 早写着，但 E38 自己那行仍带旧描述 ⇒ 别人只看 E38 就会照旧版做）⇒ 🟢 **行号复核（PIT-GLOBAL-053 反向形态）**：他们核的是 `af416d9`（第三十三轮快照）⇒ 记下「回执方核的是哪一版 ref」这条纪律⇒ 🔴 **E62 的结论被加强**：E46 ⇒ interrupt 单独做不足 ⇒ 唯一有效杠杆是「取消正在跑的 JDBC 语句」⇒ ⇒ **那正是毫秒级 query timeout 在做的事** ⇒ ⇒ **修好 E62 很可能直接消掉 E38② 在 PG 层面的可观察差距** ⇒ 🟡 **写准不夸大**（E38② 剩下的可能是更窄的「业务线程不中断」⇒ **本轮不改定性**）⇒ 🟢 **档位前提建议已落地**：`tools/semantic-probe/README.md` 加**通用档位设计规则** ⇒ 纯文档 + 工具 README 改动 ⇒ **净增 0 条**）
· **第四十三轮：users/admin 域 owner 第二轮回执 ⇒ 他们跑的是旧版工具 ⇒ 新登记 E63（共享复核工具第 5/6 层写死 `service/task` ⇒ 别的域拿到 task 的类）并修掉** （**第 2/3 层**当前版已显式跳过（E55）· **第 4 层**当前版有真实信息（164 条已验证 + 36 条带原因去噪）⇒ **他们 ④ 的大部分结论基于第三十二轮旧版**）⇒ 修法：`JAVA_ROOTS` 按域取根 + **未定义域显式跳过** + 修 `surefire_counts` 的写死路径 ⇒ 层 0 新增 3 条对照 + 变异 3 条全命中 ⇒ 🟢 **他们 ① 全部核实通过**（4 个 SHA / 792 行 / 4 个锚点 / 探针 Go 半边行号）⇒ 🔴 **他们 ③ 的 C-1 补全**（deadline 同款 + 「差异只在文本」两条硬事实：wire 不含码名 · 两侧遥测属性都存**数字** code ⇒ **③ 表述差异（无需签字）**）⇒ 🟢 **他们 ⑤ 的澄清已落进 E38/E62/E46**（第③层的真正差距 = 「ctx/SQL 有没有感知 deadline」⇒ ⇒ **修好 E62 = 补上第③层缺的这一半** ⇒ E38② 的可观察面很可能归零 ⇒ **仍不改定性**）⇒ **净增 0 条**）
· **第四十四轮：users/admin 域 owner 第三轮回执 ⇒ 他们提请复核我台账的一处**归因** ⇒ 复核成立 ⇒ 订正** （`Δ_end` **由 statement_timeout 决定** ⇒ 与「业务线程有没有被 interrupt」无关 ⇒ ⇒ **`Δ_end` 随第②层动、不随第③层动** ⇒ **结论（定性）不变但理由要换** ⇒ 订正 E46 + E38）⇒ 🔴 **但「观察依据会失效」只是推演** ⇒ 第四十一轮**实测 `Δ_end` = +833ms** ⇒ ⇒ **推演前提不成立**（真因 = **E62**：毫秒精度在**生产路径**没生效 ⇒ 走秒级向上取整）⇒ ⇒ **观察依据目前仍成立** ⇒ ⇒ ⇒ **E62 是 E38② 要不要立项的前置条件** ⇒ 🟢 **采纳第三步判据**（闸门在探针声称 PASS 时要求 `Δ_start=`/`Δ_end=` 证据 ⇒ 不足 ⇒ `exit 5`）⇒ **守门 30 → 33 条** + **变异 15 → 17 条全精确命中**（M2/M5 预期红行变长 ⇒ 加强不是退化）⇒ 🟢 **他们的 ⑤ 闭环可借鉴**（断言**消费点** + 断言 `@Intercepts` 挂在 `Executor` ⇒ 采纳为 E62 守门的设计要求）⇒ ⑦ 🔴 **本轮自查抓到一个「更早」的台账缺陷（6 处）**：`_README.md` 时间线表 **3 行**（第三十七/三十八/三十九轮）**行首被吃掉** + `_INDEX.md` 域表内 **3 行**（第三十六/三十七/三十八轮）**落点落进表格块内部** ⇒ ⇒ GFM 把它当上一行的 **lazy continuation** ⇒ **表格结构静默塌陷**（内容一个字没丢 ⇒ 肉眼扫不出 ⇒ 也没人发现）⇒ ⇒ **根因 = 落账脚本每轮用「长文本 `replace`」插一行**（三个破坏者 `697f547`/`85c4588`/`dc3770d` ⇒ **连续三轮每轮破一次**）⇒ ⇒ **内容层守门查不出来**（`check-ledger-selfcheck.py` 第 1~4 层不看结构）⇒ ✅ **还原与 git 历史逐字节比对**（3 行 1346/2118/3336 B 全一致）+ **搬移整行** ⇒ ⇒ **新守门 `tools/check-ledger-tables.py`**（判据：表格块内不得有「无行首的内容行」· 内建 **3 条变异**全精确命中 · 含阴性对照）⇒ **已登记进 AGENTS.md §7.2 工具表** ⇒ ⚠️ `--all` 全仓扫另有 **10 处在 `docs/FileTree/comment/common.md`**（comment 域文件 ⇒ **记账 + 转达 owner** ⇒ global 不改） ⇒ **净增 0 条**）
· **第四十五轮：E62 落地（毫秒精度在**生产路径**上恢复）⇒ 探针复跑取真值 ⇒ **E38② 的可观察面归零**（833ms → 22ms）** —— ① ✅ **E62 已修**（注入器探测/调用前先 **unwrap 到驱动实现类** ⇒ 用 JDK 的 `Wrapper` ⇒ 不把驱动类编进共享库 API 面）② 🟢 **守门 ①**（`service/common` 纯单测）：**13 条绿**（原 10 + **3 条新**）③ 🟢 **守门 ②**（真 PG + **真 HikariCP 池** + 真插件链）：**3 条绿**（原 2 + 1）⇒ 🔴 **E46 守门从无池改成真池**（**第一次跑在生产形态上**）+ **断言消费点**（直接读驱动 `getQueryTimeoutMs()`）+ **自证断言** ④ 🟢 **变异 3 条全精确命中**（脚本入仓 `tools/difftest/mutate-deadline-precision.py`；**M2 把守门换回无池必须红**）⑤ 🔴 **发现阈值形同虚设**：旧默认 1000ms **>** 实测 E62 回归信号 833ms ⇒ **E62 全坏了探针照样 PASS** ⇒ ⇒ **重校准为 150ms** + 新增 **E48-34/35/36**（行为化校准守卫）⇒ ⇒ **变异验证**：撤掉 unwrap ⇒ 差 **810ms** ⇒ **FAIL** ⇒ **探针真的抓得住回归了** ⑥ ✅ **E64 已修**（探针的「就绪判定」会被上一轮遗留的栈满足 ⇒ 起栈前先清残留）⇒ **E48 守门 33 → 37 条** · **变异 17 → 20 条** ⇒ 🟡 **E38② 的定性本轮不变**（可观察面归零是事实；「要不要立项」须专门一轮重估）⇒ **净增 0 条**
· **第四十六轮：E38② 重估 ⇒ **原修法否决** + 第③层**窄化**成 E65 / E66 两条 ⇒ 新增**双侧活体探针** + **漂移形态守门**** —— ① 🔴 **原修法「handler 挪受控 executor + 超时 interrupt」三个理由逐条被否**：**目标已被 E62 达成**（毫秒精度 `statement_timeout`，不需要 interrupt）· **interrupt 连 Go 都不做**（`rpc.go:47-57` 的 handler 在独立 goroutine，`rpc.go:68-71` 的 select 返回后 goroutine 继续跑）⇒ 照做会造出**比 Go 更严的一侧 ⇒ 新造漂移** · 「挪受控 executor」的价值只服务于 interrupt ② 🔴 **但第③层不是零残余** ⇒ **逐面核对 8 面**（Go 真源行号齐全）：① deadline 时刻客户端收到 status ✅（`rpc.go:68-71` ⇔ `RpcInterceptor.java:281-283`）· ② PG 查询在 deadline 被取消 ✅（E62）· ③ handler 在 deadline 后**继续跑** ✅ **两侧同款** · ④ 返回值丢弃 ✅ · ⑤ 遗留工作的**资源成本**（goroutine 廉价 ⇔ 平台线程昂贵）⚪ **框架/架构特性，非语义漂移** · ⑥ **出向 gRPC 调用** 🔴 **真漂移 ⇒ E65** · ⑦ **Redis 调用** 🔴 **真漂移 ⇒ E66** · ⑧ Kafka 生产 🟡 待核 ⇒ E67 ③ ✅ **新增双侧活体探针** `tools/semantic-probe/deadline-downstream/`（Go 半边 = **冻结仓真源** + 真 grpc-go · RAG 半边 = **生产类 `RpcInterceptor`** + 真 grpc-java Netty）⇒ **实测**：`failed_fast` Go=**true**/RAG=**false** · `code` Go=**4**/RAG=**0** · `outgoing_carrier` Go=**present_expired**/RAG=**absent** · `sql_carrier` 两侧**一致** ④ 🟢 **漂移形态守门** `tools/difftest/check-deadline-downstream.py`（**不是等值守门** ⇒ 漂移被修好就红 ⇒ 逼人回写台账）+ **变异 2 条全精确命中** `tools/difftest/mutate-deadline-downstream.py`（M1 生产代码绑定载体 ⇒ 红 `outgoing_carrier` · M2 给下游调用加 deadline ⇒ 红 `failed_fast`+`code`）⑤ 🟡 **给 Jason 的建议**：**E38② 按原样立项 ⇒ 建议否决**；**改为按 E65 / E66 两条窄项立项**（共同根因 = **RAG 没有 ctx 传播** ⇒ 共享层加统一的「请求期限载体」比逐点打补丁更对味 · **不需要架构级改造**）⚠️ **写权**：`service/common` = global 单写权 · 裸 stub 调用点分属 comment/like/message ⇒ 落地须同一次提交 · **favorite 侧只改共享层即生效 ⇒ 须记账 + 通知 favorite owner** ⑥ 🟡 **AGENTS.md 压冗余**：工具表回到「索引」形态（§7.2 本就写明「用途见各脚本 docstring」）⇒ **32,757 → 32,292 B（余 476 B）** ⇒ 顺手把 **7 个此前未登记**的工具纳入索引 ⑦ ⚠️ **本轮是 shell/python + 文档改动 ⇒ 不动任何 Java 测试 ⇒ 全仓回归净增 0 条**（**2314 条 / 0 失败**，2026-10-26 核对）
⚠️ **2026-10-26 更新（第二十七轮）**：本行的数字已更新到 **2263 条 / 52 模块**（`align/global` @ 第二十七轮收口 · 6 条 lane 合回后）⇒ 上一轮（第二十六轮）是 **2235 条**⇒ 本轮净增 **28 条**（X-5 轴 4 个组件 ×3 + slow 轴 8 个域 ×2）⇒ **0 failures / 0 errors / 0 skipped**⇒ 上一次全仓回归的中途快照（`full5`）曾是 **2234 条 / 2 failures**（两条红 = T-6 初版的 MockMvc 路径分叉 + like 守门自身的调度器泄漏 ⇒ **均非生产缺陷** ⇒ 已修）⇒ 逐条 ⇒ [`global/lanes/L1-delivery.md`](./global/lanes/L1-delivery.md) §十四。

## 🔴 状态更正（2026-10-23 实测，与 AGENTS.md §0 冲突）

搬移时顺带做了一次真实执行核对，**推翻了 AGENTS.md §0 的两条否定式断言**：

| AGENTS.md §0 原记录 | 实测 | 证据 |
|---|---|---|
| 「`article-api`（29 个文件）+ `article-rpc`（41 个文件）**仍全是 4 行空骨架**」 | **article-api：36 文件 / 4,275 行 / 0 个骨架**<br>**article-rpc：36 文件 / 7,029 行 / 仅 2 个骨架** | `find service/article/*/src/main -name '*.java'` + `wc -l`；最新写入 **10-22 23:56**，晚于 AGENTS.md 最后编辑（23:40） |
| 「`hot-api`（13 个文件仍全是 12 行空骨架）」 | ⚠️ **已于 2026-10-23 过期**：`hot-api` 已实现（16 文件 / 2,088 行 / 59 例绿），并完成**首轮源码级语义对照**（未发现 `SEMANTIC MISMATCH`）—— 但**无双侧活体对拍、无变异验证** ⇒ 见 [`hot/api.md`](./hot/api.md) | `mvn -pl service/hot/hot-api package` BUILD SUCCESS；逐条证据强度以该台账**证据强度列**为准 |

⇒ 「article 域整体仍未完成」的**计数已过期**。但两个模块**从未做过 Go ↔ RAG 语义对照**，
所以这是「代码已落地但未验证」，**不是「已验证无漂移」**。

## 🔴 状态更正 #2（2026-10-23 后续实测）

上表最后一句**已再次过期** —— `article-rpc` 当日补做了**首轮组件级语义对照**：

| 项 | 结论 |
|---|---|
| 结论 | ❌ `SEMANTICALLY NOT EQUIVALENT`（对照时刻） |
| 差异 | 7 条 `SEMANTIC MISMATCH` + 6 条 `OBSERVABLE DIFFERENCE`（另 1 条 Flyway 账本项**已证伪**） |
| 处置 | 13 条**已改**，S-5 阻塞于 security 域迁移 |
| 证据强度 | ⚠️ **仅 S-1 做过变异验证**（把 `@Transactional(readOnly=true)` 加回去 ⇒ 真 PG `25006` ⇒ 用例转红）；S-2/S-3/S-4/S-7 的新回归网**未证实非假绿** |
| 未决 | 无双侧活体对拍（Go 依赖 `etcd:2379` 不可拉起）；MQ 事件契约未冻结 |

⇒ 「已改」**不等于**「已验证」。逐条状态以 [`article/rpc.md`](./article/rpc.md) 的**状态列**为准。
⚠️ 本轮修复由一次**只读验证任务**的子 agent 越权执行，且其最终汇总与自身报告不一致（见该台账「流程问题」节）—— 属流程问题，不计入语义漂移。

## 🟡 状态更正 #3（2026-10-23 后续实测）

上表 `hot-api` 一行同样**已过期** —— 该模块当日完成首次实现 + 首轮语义对照：

| 项 | 结论 |
|---|---|
| 代码 | **16 文件 / 2,088 行 / 59 例测试全绿**；`mvn -pl service/hot/hot-api package` BUILD SUCCESS |
| 对照 | **首轮源码级 Go ↔ RAG 对照完成**，26 项逐条列出，**未发现 `SEMANTIC MISMATCH`** |
| 结论 | ❌ **不是「已验证无漂移」** —— **无双侧活体对拍、无变异验证** |
| 🔴 方法论弱点 | 顺序是「读 Go → 写 Java → 回读 Go 复核」，**复核时已知 Java 写了什么**，与 §3.1「不要拿现有 RAG 代码当对照基准」仍有距离 |
| 未决 | D1 双侧活体对拍、D2 变异验证、D6 端口 18897 vs Go 8893 待拍板 |

⇒ **下一步该做的是双侧活体对拍，不是再加 Java 侧单测。**
逐条状态与证据强度以 [`hot/api.md`](./hot/api.md) 为准。
⚠️ hot 域三组件代码均已落地，但 `hot-api` 是其中**证据强度最弱**的一个
（另两个是双侧对拍逐字节一致）。

## 🔴 状态更正 #4 —— 覆盖度实测：两个台账**未覆盖全部微服务**（2026-10-23）

实测口径：`service/` 下 **26 个模块**逐个对照本目录 + `docs/Pitfalls/`；分母取 Go 真源 `service/` 下 **14 个服务**。

| 缺口 | 明细 | 证据 |
|---|---|---|
| **分母缺口（最大）** | ⚠️ **2026-10-23 12:25 已缩小**：Go 有 14 个服务，RAG 现覆盖 **10 个域**（`comment` / `favorite` / `follow` / `task` 已升为范围内，但**仅 proto 层**）。仍**完全未译**的只剩 **3 个**：`knowledge` / `points` / `security` ⇒ 本目录对它们只能停在「未翻译」这一句 | Go `service/*` 目录实测；RAG `service/pom.xml` 现列 10 个 `<module>` |
| **组件缺口** | 8 个组件**无专属台账文件**：6 个 `*-proto`（`article` / `hot` / `like` / `message` / `user` / `admin`）+ `like-common` + `message-common`。其中 **`article-proto` / `like-proto` / `user-proto` 在两个目录里零提及** | `grep -rl` 全目录扫描 |
| **Pitfalls 是骨架** | ⚠️ **2026-10-25 更正**：本行原写「27 个文件里 21 个是 0 字节占位，只有 6 份有内容」—— **已过期**。现状：**46 个 `.md` 里 16 个 0 字节、29 份有内容、合计 280 条坑位条目**（`common` 5 份 + `_cross` 4 份为散文式，不计入条数） | `find docs/Pitfalls -name '*.md' -size 0c`（2026-10-25 实测） |
| **口径陷阱** | 「有台账」≠「已对照」。见 AGENTS.md §0 各域**证据强度列**——`hot/api` 仅源码级、`article/api` 从未对照 | 本目录各 `<域>/<模块>.md` |

⚠️ `knowledge` 曾被**整个漏出**枚举起（AGENTS.md §2 与本目录 `_cross/status-snapshot-2026-10-22.md` 的「从未验证过的子模块 | 9 个」两处均无它）——
Go 侧它是**独立微服务**（含 api 组件，依赖 Neo4j + Milvus），RAG 侧仅 `Infrastructure.md` 标「❌ 未实装」。
⇒ AGENTS.md §2 的枚举行已按实测校正；`status-snapshot-2026-10-22.md` 保持「原样快照」不动，**覆盖度以本节为准**。

## 🔴 状态更正 #5 —— `@EnabledIfDockerAvailable` 让真 PG 用例**静默全跳**（2026-10-23 20:2x）

一次全量 `mvn -o -T1C test` 得到 **`BUILD SUCCESS` / `1630 tests / 0 failures`**，但里面藏着 15 条**根本没跑**的用例：

```
service/task/task-rpc/…/TaskProgressUpsertParityTest
  tests=15  skipped=15   <skipped message="Docker is not available"/>
```

**而 Docker 明明可用** —— 同一次构建里 `FavoriteSchemaParityTest`(13) / `CommentSchemaPgGateTest`(18) / `FollowRpcWiringTest`(20) 全部真起了 PG 容器、`skipped=0`。
唯一差别：只有前者加了 `@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable`（`TaskProgressUpsertParityTest.java:69`）。

⚠️ 为什么这条特别危险：**类名里带 `Parity`（= 与 Go 对照）**，而它测的恰好是 §3.3 点名的盲区（Flyway 是否真跑、MyBatis-Plus 是否真产出 `SqlSessionFactory`），并且 **reactor 报 BUILD SUCCESS，没有任何红灯提示你少跑了它**。

⇒ **新增纪律**：凡以「真 PG 守门」为证据强度的模块，绿灯必须**额外断言 skipped 数 = 0**，「tests 全绿」本身不构成「真 PG 已验」的证据。
⚠️ 该 skip 的 `message`（`Docker is not available`）**不可信** —— 它把「Docker 真不可用」与「Ryuk 镜像拉不下来」压成同一个 `false`
（`DockerAvailableDetector` 是 `catch (Throwable) → false`）。**真实根因已定性**：`task-rpc` 是全仓唯一漏配
`RYUK_DISABLED` surefire 注入的容器模块 ⇒ 类级求值触发拉 Ryuk ⇒ `ContainerFetchException` ⇒ 整类静默 disable。
逐条、修法与元教训见 [`_cross/component-coverage-matrix.md`](./_cross/component-coverage-matrix.md)。**本条只诊断未实施。**

## ⚠️ 两处刻意未按 `<域>/<模块>` 拆分的段落

为**避免割裂叙事**，以下两段保持整体存放，用导航页指向：

1. **`users/_shared.md`（67 KB，最大文件）** —— AGENTS.md §6.2.5 / §6.2.6 以 `service/users` / `tools/difftest`
   为标题，同时覆盖 user-rpc(22) / admin-rpc(19) / admin-api(12) / user-api(10) / users-common(9)。
   拆成 4 份会把「Flyway 并发」「PasswordUtil」「DTO 矩阵」这些跨模块叙事切断。
   ⇒ `users/user/rpc.md` 与 `users/admin/rpc.md` 是**只含精确行号索引的导航页**。

2. **`_cross/message-domain-wide.md`（34 KB）** —— AGENTS.md §6.7 是 `service/message` **全域**验证，
   含 message-api 与 message-rpc 双方，单列 `<api|rpc>` 会失真。

## 保留在 AGENTS.md 的内容（phase B 不动）

| 行区间 | 内容 | 理由 |
|---|---|---|
| 1–56 | 文件头 + §0 两张总览表 | 索引本体，phase B 重写为精简版 |
| 1535–1863 | §一 项目身份与双轨策略 / §二 HUMAN_ONLY / §三 **3.1~3.3 语义对齐纪律** | 纯政策 |
| 2332–2386 | §四 重构 phase 计划 / §五 用户协作规则 / §六 标题 + **状态表纪律三条约定** | 纯政策 |
| 3055–3124 | §6.3 架构决策 / §6.4 其他子模块索引 | 决策 ≠ 漂移 |
| 4025–4094 | §七 引用清单 | 索引 |

> §六 标题下的「任何『已修 / 未修』断言都必须带核对日期，否则按 `UNKNOWN` 处理」**必须留在 AGENTS.md**——
> 它是本目录所有台账条目的书写纪律。

## 阅读顺序建议

1. 先读 AGENTS.md §3.1~§3.3（纪律）—— **这是唯一会每次进入上下文的部分**
2. 再按上表进本目录对应域
3. 判断某条断言是否可信时，看它带不带**核对日期**；不带 ⇒ 按 `UNKNOWN` 处理（§6.2 约定）
---

## `_cross/` 跨模块档案

| 文件 | 内容 |
|---|---|
| [`open-items.md`](_cross/open-items.md) | 已知未修表（§6.2）—— 任何「已修 / 未修」断言都必须带核对日期，否则按 `UNKNOWN` |
| ~~signed-deviations.md~~ | **已迁出** ⇒ [`../Intentional_Deviations/_cross/signed-deviations.md`](../Intentional_Deviations/_cross/signed-deviations.md)（N-1 / N-2 / N-4 / N-5） |
| [`decisions.md`](_cross/decisions.md) | §6.3 问题 1~3 的决策详述与被否决的方向 |
| [`go-anchors.md`](_cross/go-anchors.md) | Go 真源锚点表（判定行为时读 Go 哪个文件）—— **参考资料，非政策** |
| [`status-snapshot-2026-10-22.md`](_cross/status-snapshot-2026-10-22.md) | §0 压缩前的原文快照，**测试口径与逐域状态以本文件为准** |
| [`user-qa-verbatim.md`](_cross/user-qa-verbatim.md) | 用户问答原话（备查，防止后续被「优化」掉） |
| [`message-domain-wide.md`](_cross/message-domain-wide.md) | `service/message` 全域语义验证 |
| [`component-coverage-matrix.md`](_cross/component-coverage-matrix.md) | **组件级覆盖矩阵（38 组件逐个）** —— 谁对照过 / 证据强度到哪层 / 下一刀切哪。**要按微服务逐个做时看这份** |

## 🔴 跨域项（配置层已修，运行时验证仍缺）

**Flyway 账本撞号**（影响 `message-rpc` / `like-rpc` / `article-rpc`）——
状态行见 [`open-items.md`](_cross/open-items.md)，完整坑档见
**[`../Pitfalls/_cross/flyway-ledger-collision.md`](../Pitfalls/_cross/flyway-ledger-collision.md)**。

---

## 证据强度分层（2026-10-23 实测）

> 从 AGENTS.md §〇 搬出。**判读台账里 ✅ 的前提。**

**证据强度分层（2026-10-23 实测）**：只有 `like-api` / `hot-api` 拿到**双侧活体对拍**；
> `article-rpc` / `admin-rpc` / `hot-rpc` / `like-rpc` / `message-rpc` / `user-rpc` 拿到**真 PG 或真启动**；
> `article-api` / `admin-api` / `message-api` / `user-api` 的上限是**静态源码**。
> ⇒ 12 份台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环。

> **仍未关闭的三类**（明细台账在 DRIFT，**勿凭本表下结论**）：

