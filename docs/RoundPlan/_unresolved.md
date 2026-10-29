# 未解决事项清单（阶段一 · RoundPlan）

> **本清单只登记，不修。** 每条都指向一个执行者或一个轮次；本文件本身不含任何整改动作。
> **上位索引** = [`_task-universe.md`](_task-universe.md)（任务全集）· [`round-01/README.md`](round-01/README.md) ~ [`round-07/README.md`](round-07/README.md)（逐轮计划）· 判据基线 = [`docs/PhaseGoal-judge.md`](../PhaseGoal-judge.md)。

## 口径说明

1. 本清单**只登记不修** —— 「去向 / 处置」列指向执行者或轮次，本文件不执行任何整改。
2. **6 类互不重叠** —— 一条事项只归一类；归类依据是「它对阶段一判据的作用」，不是它属于哪个域、哪个文件。
3. **只有 A 类（第 1 类）阻阶段一** —— 第 5 / 6 类的事项**不计入**阶段一判定。
4. **台账登记类事项不升级为「请你拍板」** —— 已登记进 `docs/Intentional_Deviations/` 的候选，登记完就躺着（措辞「🏷 候选，建议签」）。
5. **C 类阶段二暂缓项不计入** —— 依据 `docs/PhaseGoal-judge.md:53-57`（§1.1 ⑥ 精确边界 · **第 4 条** = `:57` 那条 C 类 bullet）⇒ C 类定级口径见 `docs/twoStage.md:954`。
6. **证据强度以各域台账为准** —— 「已改」≠「已验证」；台账里的 ✅ 一律读作「在该证据强度下一致」。
7. **本清单不含结论句** —— 不写任何「已通过 / 已对齐」类判定；判定归 `docs/PhaseGoal-judge.md` §8.4（Round 3 的面）。

---

## 1. 阶段一阻断项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `BLK-01` | **P0 #5 覆盖缺口** —— 判据主语是「**项目**」不是「某个域」 | 旧结论只覆盖 users 域 **1/10 域**、**2/20 服务** ⇒ 实测照 `README.md:127-147` §三 原文起 `article-api` ⇒ `nacos registry … serverAddr='nacos:8848'` → `ErrCode:-401 Client not connected` → `Application run failed`（进程退出）；只用 shell 覆盖地址 + 错开端口 ⇒ article-api 正常启动（`Tomcat started on port 8899` ＋ Nacos 注册成功 ＋ `/actuator/health` 200 `{"status":"UP"}`）⇒ **缺陷面 = 地址 + 端口两类，不是代码问题** | `P0 #5`（`docs/PhasedGoal.md:46`）· `X-12`（judge §五）· `P0 #8`（`:49`，fail-closed 不得被本次改动弄坏）· `S1-1` / `S1-4` / `S1-6` | `docs/PhasedGoal.md:46` · `docs/PhaseGoal-judge.md:436` · `README.md:127-147` · `docs/RoundPlan/round-01/README.md:12` | **Round 1**（`P1-T01`~`P1-T05`） |
| `BLK-02` | **E65 出向 gRPC 不继承请求 deadline** —— Go 到点立刻失败，RAG 出向调用可无限挂住 | 🔴 **L4 双侧活体坐实**：Go 到点立刻失败（`failed_fast=true, code=4`），RAG 出向调用**无 deadline 上界 ⇒ 可无限挂住**（实测 `failed_fast=false, code=0`）⇒ **根因 = RAG 没有 ctx 传播 ⇒ 非 SQL 的 I/O 全部不感知 deadline** | `P0 #4`（`docs/PhasedGoal.md:45`）· `X-6`（judge §五）· `C-2` / `C-3` · `S1-1` / `S1-4` / `S1-6` | `docs/DRIFT/global/vs-common.md:30` · `docs/DRIFT/global/vs-common.md:46` · `docs/DRIFT/global/_README.md:391` · `docs/PhaseGoal-judge.md:259` | **Round 2**（`P2-T01`~`P2-T05`） |
| `BLK-03` | **`S1-FOLLOW-007` —— 基线仍把「已签字偏离」记成开放缺陷** ⇒ 机器生成的派单指令会让 G4 去改掉用户已决定保留的行为 | 🔴 **维持仍开着**（2026-10-30 users lane 重核：基线里那条「已签字偏离」**仍被记成开放缺陷**）· 阶段归属 **A**（派单错误 ⇒ 共享层被改坏）· 优先级 **P1** | `S1-1` / `S1-4`（阶段 1 准入面）· 判据基线 = `docs/PhaseGoal-judge.md`（§1.5：**四层状态只认一个来源**） | `docs/DRIFT/users/stage1-admission.md:524` · `docs/PhaseGoal-judge.md:192`（`follow-api` 的 A-2 行「残留」列仍写 `DRIFT-FOLLOW-008`） | **Round 3**（基线复判订正面 · `P3-T01`~`P3-T09`）—— ⚠️ **Round 3 已在「基线行改判」范围内** ⇒ 本条**不是新工作**，而是**派生链没连上** ⇒ 动作 = 把 `S1-FOLLOW-007` 接进 Round 3 的条目清单（`P3-T02` / `P3-T07` 的行） |

> ⚠️ `BLK-02` 的守门 `check-deadline-downstream.py` 的 **PASS 语义是「漂移还在」** ⇒ **不能拿它的 PASS 证明 X-6 成立**（见 `docs/RoundPlan/round-02/README.md` `P2-T05`）。

> 🆕 **2026-10-30 · 派生层真缺口轮（`docs/DRIFT/_cross/stage1-derivation.md` §4.1）**
> ⇒ 本类新增 `BLK-03`（**1 条**）。判定口径 = **状态开放**（🔴 / 仍生效 / ⏳ 未签字）
> **且** 阶段归属 ∈ {A, B} **且** `_task-universe.md` 的 37 条任务**都没有指向它**。
> ⇒ ⚠️ 派生层口径下 **A/B 类未排期共 3 条**（本类 1 条 ＋ 第 2 类 2 条）⇒ **已全部登记**。

---

## 2. 证据缺口

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `GAP-01` | **G-19 proto「消费方式」取证** | 🟡 取证缺口 · 不降 R —— `.proto` 文件层逐字节一致**推不出**消费方式一致（`optional` presence / 代码里的字段号硬编码 / Jackson 配置）⇒ AGENTS.md §3.4 的效力边界在 **10 个 proto 上全部成立** | `X-10`（judge §五）· proto 组件 ③⑤ · `R3 ⑥ 第 3 条`（取证缺口不计入） | `docs/PhaseGoal-judge.md:483` · `docs/PhaseGoal-judge.md:263` · `docs/DRIFT/_cross/proto-verification.md:40-46` | **Round 5** `P5-T01` |
| `GAP-02` | **G-17 `like-common` 专属台账缺失** | 🟡 **记录缺口**（非行为缺口）—— 「从未与 Go 对照」的判定已被仓内证据推翻（它有 Go 派生的契约测试 `LikeErrorContractTest`：29 个码逐条 1:1 ＋ 文案表 ＋ 未知码回落 ＋ `CodeError` 序列化）⇒ 缺的只是**逐条台账记录** | `like-common` 组件的**可审计性** · 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 3 条） | `docs/PhaseGoal-judge.md:481` | **阶段二**（见本清单第 6 类）· 本阶段只登记 |
| `GAP-03` | **`U-01` 的「DC → 第三仓」一段**（`Sea-BreakTheWaves`） | 🟡 取证缺口（部分闭合）—— like → DC 那一跳已 L4 闭合（两侧 970 B sha256 相同 ＋ outbox 终态逐字段相同）；**DC → 第三仓消费端那一跳仍未取证**（需 pinned checkout ＋ 40-hex SHA）⇒ **仓外、本机不可核验** ⇒ **不是缺陷** | 不计入 `X-1` 判定 · 不阻阶段一 | `docs/DRIFT/like/api.md:801` · `docs/DRIFT/like/rpc.md:9` | **阶段一之外**（见本清单第 6 类） |
| `GAP-04` | **共享层 28 项里的「证据缺口 1」** —— `LoggerFactoryBean` 各模块注册方式不一致 | 🟡 **U-2** —— 各模块的注册方式不一致（有的显式 `@Import`，有的只靠 `scanBasePackages`）⇒ **是否有模块漏注册「未验」**（跨模块，未验）⇒ 属**取证缺口**而非行为差异 | 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 3 条）· 须登记进 judge §九 证据缺口清单 | `docs/DRIFT/common/alignment-2026-10-23.md:957` · `docs/DRIFT/common/alignment-2026-10-23.md:525` | **Round 5** `P5-T02`（台账对账面）· 跨模块复核归各域 lane |
| `GAP-05` | **`S1-FOLLOW-008` —— `check-surface-parity.py` 的两个静默假绿口子** | 🔴 **维持仍开着**（2026-10-30 users lane 重核：**工具在位**（`tools/difftest/check-surface-parity.py` · 23,215 B · 2026-10-29 03:27），但**「两个口子」的具体位置本轮未逐条复现**）⇒ 属**取证缺口**（不是已证实的缺陷）· 阶段归属 **B**（阶段 1 竞争力补强）· 优先级 **P2** | 阶段 1 契约面判据的**守门可信度** · 不阻阶段 1 | `docs/DRIFT/users/stage1-admission.md:525` · `tools/difftest/check-surface-parity.py` | **Round 5** `P5-T02` 口径（取证与台账对齐）—— **共享工具面** ⇒ 落点 `tools/difftest/`（**G4 写权面**）⇒ 按 §5.3e 走共享层反馈单 |
| `GAP-06` | **`S1-FOLLOW-009` —— 5 个 api 模块缺 `TESTCONTAINERS_RYUK_DISABLED`** | 🔴 **维持仍开着**（2026-10-30 users lane 重核：`grep -rln "TESTCONTAINERS_RYUK_DISABLED" service/ --include='*.yml' --include='*.yaml'`（排除 target）⇒ **0 命中**；逐模块实测仍缺）· 阶段归属 **B** · 优先级 **P2** | 阶段 1 的**验证手段可用性**（Testcontainers 清理容器会拖慢/挂住本地跑测）· 不阻阶段 1 | `docs/DRIFT/users/stage1-admission.md:526` | **Round 4 / Round 5** —— **各域域侧**（5 个 api 模块）⇒ 归各域 lane，**不是**共享层 |

> 🆕 **2026-10-30 · 派生层真缺口轮** —— 本类新增 `GAP-05` / `GAP-06`（**2 条**）。
> ⇒ 连同第 1 类的 `BLK-03`，派生层口径下 **A/B 类未排期共 3 条 ⇒ 已全部登记**（无遗漏）。
> ⇒ ⚠️ 派生层另有 **453 条「状态开放但未定级」**（**不在施工单、也未推给阶段 2**）
> ⇒ 它们**需要人工定级才能进施工单** ⇒ 清单见 [`docs/DRIFT/_cross/stage1-derivation.md`](../DRIFT/_cross/stage1-derivation.md) §4.2。

---

## 3. 环境或权限受阻项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `ENV-01` | **Go 模块缓存 / 参考仓间歇性不可见** | 🔴 同一路径 5 分钟内「可读 → 不可读 → 可读」，且 `ls` 与 `os.path.isdir` 结论互相打脸 ⇒ 判据依赖外部输入时**必须探针先行 + 硬失败退出码 2**，**绝不静默降级成「这条不可核验」** | 任何依赖 Go 真源 / 参考仓的判据（含 `BLK-02` 的双侧活体、DDL 逐列对照） | 本轮计划期实测观察（**仓内无台账出处**）· 退出码口径参照 `tools/difftest/check-ddl-static.py:985-987` | 各轮「防错约束」小节（写入 `round-0*/README.md`）· 受阻时记「未完成任务及原因」 |
| `ENV-02` | **两个仓的 compose 容器名 + 端口 100% 冲突** | 本仓与兄弟仓 `Sea-RideTheWind_java` 都用 `${CONTAINER_PREFIX:-Sea}-*` 与 49848 / 49432 / 49380 / 49090 / 49317 / 49992 / 49100 ⇒ **本来就互斥**；兄弟仓容器已 park 为 `SeaOld-*`（数据卷是匿名卷，未动） | `P0 #5` / `X-12` 的起栈面（Round 1 全部） | `docs/RoundPlan/round-01/README.md:23` | **Round 1** `P1-T02` / `P1-T05`（基础设施前提） |
| `ENV-03` | **本机 Docker 默认镜像源拉大 blob 会 EOF** | 环境面，不是项目缺陷 ⇒ 换 `docker.m.daocloud.io` 后 `docker tag` 回本地名可绕 | Round 1 依赖镜像重建的任何一步 | 本轮计划期实测观察（**仓内无台账出处**） | **Round 1** `P1-T05`（起栈前置）· 受阻时记「未完成任务及原因」 |
| `ENV-04` | **Docker 端口释放延迟** | 容器 `docker rm -f` 后宿主口**仍被 docker-proxy 占住** ⇒ 必须**轮询** `lsof -nP -iTCP:<port> -sTCP:LISTEN` 直到空才起下一个 ⇒ 属**假红来源**之一 | `P0 #5` / Round 1 `P1-T05` 的判读 | `docs/RoundPlan/round-01/README.md:26` · `docs/Pitfalls/global/cross-domain.md:1307`（PIT-GLOBAL-038 端口占用型假红） | **Round 1** `P1-T05`（轮询到空）· 三步定性法 |
| `ENV-05` | **共享 Nacos 服务发现面让并发探针互相污染** | 报 `INTERNAL` / `5001` 是**已知串扰**，**不是缺陷** ⇒ **跑红 ≠ 是缺陷** | 并发探针的结果判读（Round 4 `P4-T08`、Round 6 收口回归） | `docs/Pitfalls/global/cross-domain.md:2503`（PIT-GLOBAL-068） | **Round 4** `P4-T08`（探针 Nacos 隔离） |

---

## 4. 等待用户裁定的事项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `DEC-01` | **like 域 `003-C`（暴露形态）** | 🟡 `DRIFT-LIKE-003` 的 **A 段**（事实投递链路）/ **B 段**（权威读语义 Java 化）已闭环，**仅剩 C 段（暴露形态）** ⇒ 已改判 C 类暂缓 `S1-LIKE-001` ⇒ **等部署形态拍板**（原写「scope 过大」的理由**已作废**） | 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 4 条）· ⚠️ **不得据此宣称 like 域「零差异」**（C 类 ≠ 零差异） | `docs/Stage1/05-差异与暂缓项清单.md:490` · `docs/PhaseGoal-judge.md:371` | 等用户拍板 · 登记在 `docs/Stage1/05` 的 11 字段清单 · 本轮不改判 |
| `DEC-02` | **12 条未签字候选**（`CAND` 族 **9** ＋ `like/api.md` 的 **3**）<br>⚠️ **2026-10-29 机器复算订正**：原写 **18 条**（`CAND` 族 14 ＋ `like/api.md` 4）⇒ **现值 12** —— 差额来自：`CAND` 族里 **4 条早已转正为已签字**（`CAND-07`/`CAND-11`/`CAND-12` 等）＋ **1 条已撤销**、`like/api.md` 的 `SD-2026-10-23-08` **已失效**（⇒ 4 → 3）⇒ 权威表见 [`docs/Intentional_Deviations/_INDEX.md`](../Intentional_Deviations/_INDEX.md) | ⏳ **未签字** —— `SD-` 而非 `CAND-` 编号的条目原计数漏掉 ⇒ 合计 12 条 ⇒ **不升级拍板**（已登记即为交付物）⇒ 仅登记 | ⚠️ judge §1.1 ⑥ 第 1 条**只豁免「已签字偏离」** ⇒ 这 18 条**尚未签字** ⇒ 不得按已签字豁免；各组件 ⑥ 判定逐条按域台账核 | `docs/Intentional_Deviations/_INDEX.md:28-30` | 仅登记（措辞「🏷 候选，建议签」）· **不进拍板清单** |
| `DEC-03` | **`D-05` `follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口** | 🔴 同款 **5 处**：favorite / message / like / user-user / user-admin（Go 有 DevServer `/metrics`）⇒ 用户 2026-10-24 定「本轮不落地，转全仓 rpc 层统一方案交主会话拍板」⇒ **需重新拍板** | `follow-rpc` **降 R2**（可观察差异）· 跨 **6** 个 rpc 组件 | `docs/PhaseGoal-judge.md:564` · `docs/DRIFT/global/_README.md:340`（E31） | 等用户拍板 · **属阶段二** |
| `DEC-04` | **`P1-T05` 里某个服务真起不来** | 🔴 走 `PIT-GLOBAL-032` 三步定性法判「真失败 / 假红」⇒ **真失败** ⇒ 走 `CT-02`（定级 + 记录来源/证据/影响的判据/所需决策）⇒ **不得当场改产品代码**（规范 §一.6） | 阻塞 `P1-T05` 的「20/20 在服务」判定 ⇒ 连带阻塞 `P3-T02`（P0 #5 行改判）⇒ **可能使 `BLK-01` 持续存在** | `docs/RoundPlan/round-01/README.md` `P1-T05` 失败处理 | 走 `CT-02` ⇒ 落到「暂缓 / 登记 / 转派」三选一 ⇒ **不得**为让矩阵好看而把两个服务塞回同一端口 |
| `DEC-05` | **`P2-T06` 的 E66 定级去向（二选一必须写明）** | 🔴 若定 **A/B** ⇒ 需落地（Lettuce 期限载体 + **规避 Boot 4 `LettuceConnectionConfiguration.createClientOptions` 字节码顺序硬约束** ⇒ 必须自证「`spring.data.redis.timeout` 仍生效」）⇒ 落点在 `service/common/**` ⇒ **G4 写权**；若定 **C** ⇒ 登记进交付物 5 暂缓清单（**11 字段齐全**）⇒ **不计入**阶段一判定 | `X-6` / `P0 #4` / `C-3`（若 E66 被判 A/B） | `docs/DRIFT/global/vs-common.md:31`/`:47` · `docs/DRIFT/global/_README.md:392` | **落地或登记，二选一必须写明**（不能既不落地也不登记）⇒ 落地须与 `P2-T02` **同一次提交** |

---

## 5. 允许暂缓且不阻断阶段一的事项

> ⚠️ 本类全部条目的共同依据 = `docs/PhaseGoal-judge.md:53-57`（§1.1 ⑥ 精确边界 · **第 4 条** = `:57` 那条 C 类 bullet：按 `docs/twoStage.md:954` 定级为 **C 类**的差异与暂缓项 ⇒ **不计入**组件 R3 ⑥）⇒ **不计入阶段一判定**。
> ⚠️ 「不计入」≠「零差异」⇒ 任何复判都**不得**据此宣称某组件「已对齐」。

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `DEP-01` | **共享层 28 项**（23 编号 + 5 未编号） | 🟡 逐条定级 **A 类 0 · B 类 0 · C 类 21 · D 类 6 · 证据缺口 1** ⇒ 域内硬阻塞 = 0 ⇒ 组件 `service/common` R2 → R3 | **依据 §1.1 ⑥ 第 4 条 ⇒ 不计入阶段一判定** | `docs/DRIFT/global/vs-common.md:33` · `docs/DRIFT/common/alignment-2026-10-23.md:652` · `docs/DRIFT/common/alignment-2026-10-23.md:964-972` | 阶段二（`docs/twoStage.md` §5.3 的 11 字段清单）· 本阶段只登记不修 |
| `DEP-02` | **DDL 残留 51 处**（9 域） | 🟡 **C 类 23 · D 类 28 · A 类 0** ⇒ **类型/长度 9 域 0 差异**；`hot` = **N/A** ⇒ **不许写成「已对齐」** | **依据 §1.1 ⑥ 第 4 条 ⇒ 不计入阶段一判定** | `docs/DRIFT/_cross/ddl-type-divergences.md:178` | 阶段二 · 逐域证据在各域 `vs-common.md` §9 |
| `DEP-03` | **`S1-USERS-002`**（B 类部署凭据默认值） | 🟡 **8 个 rpc 模块 / 10 处** `${POSTGRES_PASSWORD:password}` × 9 ＋ `${MINIO_SECRET_KEY:minioadmin}` × 1 ⇒ 落点在**各域自己的 rpc 模块** ⇒ **域侧可自修**（原写「归 G4」是错的） | B 类 ⇒ **不计入** `P0 #8` 判定（残留已登记） | `docs/Stage1/05-差异与暂缓项清单.md:73` · `docs/Stage1/02-阶段1简历项目验收总览.md:51` | **Round 4** `P4-T01` / `P4-T02`（已排期修复 ⇒ 本类只登记「已排期」） |
| `DEP-04` | **`DRIFT-CF-018` favorite 域半边** | 🔴 C 类 —— 共享层半边**已修** ⇒ favorite 域半边**仍成立**：`FavoriteFactDispatchScheduler.java:119` 仍调阻塞版 `Dispatcher.run(...)` ⇒ `once=false` 时句柄永远拿不到 ⇒ `@PreDestroy` 拿到 null ⇒ **进程无法优雅关闭** | C 类 ⇒ **不计入**阶段一判定（`P0 #1`） | `docs/DRIFT/global/vs-common.md:4646`（§4.18.1 共享层修复记录）· `docs/Stage1/06-最小可行实施计划及验收结果.md:158` | **Round 4** `P4-T03`（已排期修复 ⇒ 本类只登记「已排期」） |
| `DEP-05` | **comment 域整张 `report_record` 表（8 列）仅 RAG 有** | ✅ **已签字** `SD-2026-10-24-CAND-02`（2026-10-24 · 用户 Jason）⇒ Go 侧**根本不存在**该表 ⇒ 属**已签字偏离** ⇒ **是决策不是缺陷** | 已签字偏离 ⇒ 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 1 条） | `docs/Intentional_Deviations/comment/rpc.md:67` · `docs/Intentional_Deviations/comment/rpc.md:17` · `service/comment/comment-rpc/src/main/resources/db/migration/V5__create_comment_report_record.sql:26-36` | 阶段二 · **保护项**（各轮「不得顺手修回 Go 行为」约束） |

---

## 6. 阶段一之外的事项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `OUT-01` | **阶段二十二维度生产级审查** | ⬜ 未启动 —— 12 个维度（业务闭环 / 微服务边界 / 服务发现与 RPC / 超时重试熔断 / 数据一致性 / 认证授权 / 性能容量资源 / 日志指标追踪 / 测试覆盖质量 / 构建部署配置发布 / 备份恢复灾难 / 可维护性）**逐维**完整审查，**不得删减或合并** | **不计入** `S1-1` ~ `S1-6`（阶段一判据不含生产级审查） | `docs/twoStage.md:517` · `docs/twoStage.md:521-532` · `docs/twoStage.md:59` | 阶段二 §6.3 · 阶段一**不预装**该清单 |
| `OUT-02` | **§九 故障矩阵**（生产流量 / SLO / 灾备演练） | ⬜ 未启动 | **不计入**阶段一 | `docs/twoStage.md:898` · `docs/twoStage.md:74-78`（`P2` 原文） | 阶段二 |
| `OUT-03` | **§6.1 重新建立最终改造范围** | ⬜ 未启动 —— ⚠️ 原文「**不得假定阶段 1 的暂缓清单已经包含所有缺口**」⇒ 第 5 类清单**不可**被当成阶段二的完整输入 | **不计入**阶段一 | `docs/twoStage.md:460` · `docs/twoStage.md:473` | 阶段二 2A |
| `OUT-04` | **Go 真源范围外三域**（`knowledge` / `points` / `security`） | ⚪ **R0 = 范围外** ⇒ Go 侧无对应件 ⇒ 本仓不实现 ⇒ **是决策不是待办**（⚠️ 不得列为整改任务） | **不计入**任何组件的必相对齐项 · **不计入**阶段一 | `docs/PhaseGoal-judge.md:46` · `docs/DRIFT/_cross/proto-verification.md:46` | **不做** |
| `OUT-05` | **`P1` 维度 3 / 7 / 8** | ⬜ 阶段一未覆盖 —— 维度 3（服务发现与 RPC 通信）里的 **proto 消费方式取证**（= `GAP-01`）；维度 7（**性能、容量与资源管理**）；维度 8（日志、指标、追踪与告警）里的 **rpc 层指标出口**（= `DEC-03`） | `P1` 是「简历竞争力要求」，**不构成阶段一阻塞**（`docs/PhasedGoal.md:54-68`） | `docs/PhasedGoal.md:54` · `docs/twoStage.md:523` · `docs/twoStage.md:527` · `docs/twoStage.md:528` | 阶段二 · 其中 proto 消费方式的取证已提前排进 **Round 5** `P5-T01` |
| `OUT-06` | **`P2` 全部** | ⬜ 「暂不作为简历项目阻塞条件」（完整生产级 SLO 与事故管理体系 / 基于真实生产流量的容量规划 / 定期灾难恢复演练 / 完整灰度发布与自动回滚 / 穷尽式验证） | **不计入**阶段一 | `docs/PhasedGoal.md:70-78` | 阶段二 |

---

## § 自检

| 类 | 条数 | 编号 |
|---|---|---|
| 1. 阶段一阻断项 | **2** | `BLK-01` · `BLK-02` |
| 2. 证据缺口 | **4** | `GAP-01` · `GAP-02` · `GAP-03` · `GAP-04` |
| 3. 环境或权限受阻项 | **5** | `ENV-01` ~ `ENV-05` |
| 4. 等待用户裁定的事项 | **5** | `DEC-01` ~ `DEC-05` |
| 5. 允许暂缓且不阻断阶段一的事项 | **5** | `DEP-01` ~ `DEP-05` |
| 6. 阶段一之外的事项 | **6** | `OUT-01` ~ `OUT-06` |
| **合计** | **27** | — |

- **6 类齐全，无遗漏**（类名一字不改、顺序不变 ⇒ 对齐规范 §11.6）。
- 每条 6 列（`编号 / 事项 / 当前状态 / 影响的判据 / 来源（文件:行号） / 去向 / 处置`），「影响的判据」均落到具体判据编号（`P0 #n` / `X-n` / `C-n` / `S1-n` / `R3 ⑥` / `D-05` / `G-n` / `U-n` / `DRIFT-*`）。
- **6 类互不重叠**：A 类 2 条；证据缺口 4 条（含 1 条仓外不可核验 `GAP-03`）；环境受阻 5 条；真正需拍板 **5** 条（其中 `DEC-02` 按口径说明第 4 条**仅登记**；`DEC-04`/`DEC-05` 走 `CT-02` 定级路径）；暂缓 5 条；阶段一之外 6 条。
- **只有第 1 类阻阶段一**；第 5 / 6 类均写明「依据 §1.1 ⑥ 第 4 条 ⇒ 不计入阶段一判定」或等价依据。
- **本清单不含结论句**（无任何「已通过 / 已对齐」类断言）。
