# 阶段一终点指标（Phase 1 End-State Judge）

> **一句话**：把 `docs/PhasedGoal.md` 与 `docs/twoStage.md` 的**语言描述**落成**可判定的矩阵** ——
> 每个微服务 / 组件**复刻到什么程度**才算阶段一完成，**用什么命令判定**，**今天到哪一步了**。
>
> **版本**：**修订版 v5**（**2026-10-29 复判的两条 A 类阻断订正**：① **P0 #5 证据覆盖缺口**（证据只覆盖 users 域 1/10 ⇒ 由 ✅ 改判 🟡）② **E65 / X-6 口径反**（「✅ 探针 PASS」= **漂移形态仍在** ⇒ 判据不满足 ⇒ 由 ✅ 改判 🔴）⇒ **判据原文一字未改**，只改「当前状态」列；v4 = v3 + 机器自检后的评级订正）
> **核对时间**：2026-10-28（基线 commit `7ec6fc6` + 本次新增 `tools/difftest/check-{surface-parity,phasegoal-consistency}.py`）｜ **2026-10-29 增补**：R3 ⑥ 边界补**第 4 条**（C 类阶段二暂缓项不计入 · Jason 裁定）＋ `DRIFT-COMMON-005` 已修的**四处订正**（§1.1 ⑥ · A-2 `service/common` 行 · 域级卡 · §六 C-2 · §十 D-06）；**矩阵评级未重算**（Jason 明示「按现矩阵判」）
> **真源**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`（冻结，只读）
> **上一版**：v1（2026-10-28）—— **v1 的 13 处结论在本版被修订**，逐条见 [§十 变更记录](#十变更记录原结论--问题--修订后结论--依据)。

---

## 〇、这份文件是什么、不是什么

| 是 | 不是 |
|---|---|
| 阶段一**终点指标**的唯一定义处（组件级 + 域级 + 跨组件三层） | 第三份验收标准 —— **准入线仍以 `docs/PhasedGoal.md` §二 / `docs/twoStage.md` §2.1 为准**（两者逐字相同） |
| 把「合格」翻译成**数字 + 判定命令** | 新的评分体系 —— **评分规则仍以 `docs/twoStage.md` §8 为准**（E0–E5 证据分级） |
| 给 lane 执行者用的**验收清单**（阶段一只做本域） | 已完成清单 —— **进度仍以各域台账为准**（`docs/DRIFT/<域>/<组件>.md`） |

**本文件自身受三条纪律约束**（与 AGENTS.md 一致）：

1. **一条合法指标 = 对象 + 数字 + 判定命令。** 没有命令的句子不是指标，是愿望。
2. **每个断言带核对日期。** 无日期按 `UNKNOWN` 处理（AGENTS.md §6.2）。
3. **「已改」≠「已验证」** ⇒ 复刻度（R，覆盖面）与证据强度（E，可信度）**分开记，不互相推导**。

### ⚠️ v3 的四条新增纪律（本次审查产出）

4. **「台账里写着已修」≠「可以判定通过」。** 必须回到**条目级最新状态**（见 §1.5），不能引用台账头的「组件判定」行。
5. **判定命令的绿灯只能证明它自己检查的那一面。** 命令的实际覆盖范围见 [§六](#六判定命令的实测覆盖范围)。
6. **只存在于 `/tmp`、已消失、或无法从当前仓库恢复的探针 ⇒ 不作为当前可复算的通过证据**（历史记录保留，但标记为「曾取得，不可复算」）。
7. **「无台账」≠「从未对照」。** 判定记录**在哪**是**记录缺口**（⇒ §九），**行为有没有对齐**是**另一件事**（⇒ 组件 R 与状态）。把两者混为一谈，会把「记录缺失」伪装成「行为漂移」（v3 订正了 4 处这样的误判，§十 #18~#21）。
8. **本文件自身受机器自检约束** —— `tools/difftest/check-phasegoal-consistency.py`（15 组检查：表格结构 · 矩阵集合 · 38 组件构成 · R/E/状态取值域 · **R↔状态耦合** · **判定命令路径存在性** · **✅ ⇒ 有可复算命令** · **域状态 = MIN 聚合** · 编号连续性 · **P0 汇总** · 测试条数 · §6.3 命令数 · 无未来日期）⇒ **改完必跑**。

---

## 一、三套刻度

### 1.1 复刻度 R（覆盖面 —— 「复刻到什么程度」）

> **v2 修订（v3 保留）**：R4 增加「探针须**在版本控制内**、**现在可复算**」这一条；R3 的第 ⑥ 条被精确定义；
> 新增 **「未评级」**（组件存在但**从未与 Go 对照过** ⇒ 不参与 R 判定，直接判组件状态 🔴）。

| 级 | 名称 | 判据（**全部满足**才够这一级） |
|---|---|---|
| **R0** | 范围外 | Go 侧无对应件 ⇒ 本仓不实现（`knowledge` / `points` / `security` 三域） |
| **R1** | 骨架 | 有端点 / 方法，但业务逻辑是桩、硬编码成功或返回模拟数据 |
| **R2** | **契约面已复刻** | ① wire 契约（HTTP 路径+方法集合 / gRPC 方法集合 / proto 字段号 / DB 列）**逐条对齐**；② **有守门可复算**（脚本或测试能自动判定）；③ 业务分支**未**逐条对照 |
| **R3** | **语义面已复刻** | R2 **＋** ④ Go 侧每个业务决策点有 `文件:行号` 出处；⑤ RAG 侧有**自动化测试断言该行为**；⑥ **归属本组件**的「必须对齐 / 语义 MISMATCH / 真漂移」分歧项 **= 0**（**C 类阶段二暂缓项不计入** ⇒ 见下方「⑥ 的精确边界」第 4 条 · **Jason 2026-10-29 裁定**） |
| **R4** | **运行面已复刻** | R3 **＋** ⑦ **双侧同时运行**的探针对拍通过，**且该探针在版本控制内、现在可复算**；⑧ 有**变异验证**记录（改实现 ⇒ 探针必红） |
| **未评级** | 从未对照 | 该组件**既无专属台账，也无任何仓内可复算的 Go↔RAG 对照产物**（守门脚本 / 契约测试类 / 双侧探针）⇒ 从未与 Go 逐条对照过 ⇒ **不得据此判任何等级**，组件状态直接 🔴<br>⚠️ **「无专属台账」≠「从未对照」**（v3 修订）：若存在**仓内可复算的对照产物**或**跨域对照记录**（`docs/DRIFT/_cross/proto-verification.md`），则**不算未评级**；此时「**判定记录位置不统一**」计入 [§九](#九证据缺口清单)，**不因此降 R**（降 R 会把「记录在哪」伪装成「行为没对齐」） |

> **⑥ 的精确边界（v2 澄清 · 2026-10-29 补第 4 条）** —— 下列**四**类**不计入**「归属本组件的必相对齐项」；前 3 类**必须**登记到 [§九 证据缺口清单](#九证据缺口清单)，第 4 类按其自身口径登记：
> - **已签字偏离**（决策，不是缺陷）⇒ 记入 §11.4；
> - **落点在共享层 / 别的域**的跨组件残留 ⇒ 不计入组件 R，但**计入域级聚合**与[矩阵 C](#五矩阵-c跨组件不变量12-条)；
> - **取证缺口**（台账写「为什么仍然 Unknown」，而**不是**「已断言不一致」）⇒ 计入 §九。
> - **按 `docs/twoStage.md` §10 定级为 C 类**（阶段二暂缓 · 语义等价）的**差异与暂缓项** ⇒ **不计入**本条（C 类 = **已登记的已知差异**，既不是「必须对齐」、也不是「语义 MISMATCH / 真漂移」）⇒ 但**必须**逐条保留在 `docs/twoStage.md` §5.3 的「差异与暂缓项清单」（11 字段）**并**在组件台账可查 ⇒ ⚠️ **不得据此宣称该组件「已对齐」**（C 类 ≠ 零差异）。<br>**裁定**：Jason 2026-10-29（调度员落码）⇒ 口径**封死** ⇒ 此后**不再**因「C 类算不算真漂移」重算任何矩阵。
>
> ⚠️ **R 与 E 不可互相推导**：R4 的组件可能只有 E4（无生产环境）；反过来 E3 的组件可能只有 R2。

### 1.2 证据强度 E（可信度）—— 直接引用 `docs/twoStage.md` §8.1，**不重定义**

| 级 | 名称 | 含义 |
|---|---|---|
| E0 | 无证据 | 尚未找到实现或尚未完成检查 |
| E1 | 声明证据 | 仅文档 / 注释 / README / 配置声明 |
| E2 | 源码证据 | 找到具体实现，未证明正确 |
| E3 | 测试证据 | 有可定位的测试**及实际执行结果** |
| E4 | 运行证据 | 有实际运行验证 / 指标 / 故障演练结果 |
| E5 | 生产运行证据 | 可追溯的真实运行数据、事故记录、SLO |

**E 带星号（E\*）= 曾取得该等级的证据，但**探针不在版本控制内 ⇒ 本轮不可复算**。

### 1.3 组件状态

| 状态 | 判据 | 对阶段一的影响 |
|---|---|---|
| ✅ **达标** | R ≥ R3 **且** 归属本组件的必相对齐项 = 0 **且** 有**可复算**的判定命令<br>⚠️ **「可复算判定命令」的口径（v3 精化）**：① 该命令**今天能跑**（仓内路径真实存在 / `mvn -pl <模块> -am test`）；② 该命令**断言的对象覆盖本组件 R 级所声称的那一层** —— R3 必须有**语义断言**（Go 出处写进期望值），分两种：<br>・**api / rpc / common 组件**：**纯契约面 / 静态集合**守门（`check-surface-parity.py` 的端点集合）**不满足 ⑤** ⇒ 只能支撑 R2；<br>・**proto 组件**：`.proto` 的**字段号 / message 名 / 枚举**就是该组件的行为层 ⇒ `check-proto-static.sh` 这类**逐字节 diff 满足 ⑤**；但它**不覆盖** `optional` presence 与代码里的字段号硬编码 ⇒ 那两项属【PROTO 型】③⑤ 的**取证缺口**（§九），**不降 R** | 计入通过 |
| 🟡 **契约达标 / 语义待补** | R = R2（未达 R3） | 登记为差异项或暂缓项（§5.5） |
| 🔴 **未达标** | R 未评级（从未对照）**或** 归属本组件的必相对齐项 ≠ 0 | **A 类准入阻断项** |
| ⚫ **受阻** | 环境缺前置条件 | **不得写成「已修」**，也**不得写成「未修」** |
| ⬜ **豁免** | 有明确理由的不适用 | D 类，须写明理由 |

> **v2 注**：本轮实测**中间件全部可用**（Kafka `127.0.0.1:59993` · Redis `127.0.0.1:57379` 等 8 个 · Nacos `127.0.0.1:8848` · etcd 容器 `Sea-etcd` 在跑但宿主 `2379` **未做端口映射**）⇒ **本轮没有 ⚫ 受阻的组件**；原先「本机无 Kafka/Redis/Nacos」的说法**不成立**，已删除（见 §10 变更 #9）。

### 1.4 指标书写规则

| 反例（不是指标） | 正例（是指标） |
|---|---|
| 「错误码要和 Go 一致」 | 「错误码码值 + 文案 + 未知码回落**逐字节一致**，判定：`GO_REPO=… bash tools/errmsg-probe/run-parity.sh` ⇒ EXIT=0」 |
| 「测试要覆盖失败路径」 | 「每端点**至少 1 条**失败路径测试（参数非法 / 权限不足 / 下游不可用），判定：模块级 `mvn test` ⇒ surefire XML 中该类 `failures=0` 且 `tests>0`」 |
| 「要能做双侧对拍」 | 「**两侧同时运行**同一请求矩阵，比响应字节；判定：`bash tools/<probe>/run.sh` ⇒ EXIT=0，且**变异后 EXIT≠0**」 |

### 1.5 ⭐ 判定权威层模型（v2 新增 · v3 保留 —— 四层状态只认一个来源）

**同一个事实只允许有一个权威记录位置**，其余层一律**引用**，不独立判定。

| 层 | 对象 | **唯一权威位置** | 本文件对应层 | 引用方式 |
|---|---|---|---|---|
| **L0 组件层** | 某组件的 R / E / 未决项 | **该组件的台账「条目级最新状态」** —— 即台账里**逐条编号的状态表**（§Unresolved / §7 状态表 / 最新轮次节），**不是**台账头的「组件判定」行 | [§三 A-2](#三矩阵-a-2终点指标与当前判定39-行--38-组件--1-共享层) | A-2 的「权威来源」列给出 `文件:行号` |
| **L1 域层** | 某域的状态 | **L0 的聚合**（聚合规则见 §8.1，**只有这一处**） | [§四 B](#四矩阵-b10-张域级终点指标卡) | B 卡只写聚合结果 + 阻塞项 |
| **L2 跨组件层** | 跨域不变量 | **`docs/DRIFT/global/`（收口域）** 的对应台账 | [§五 C](#五矩阵-c跨组件不变量12-条) | C 表只写指标 + 判定命令 + 状态 |
| **L3 阶段层** | P0 九条 / 阶段一结论 | **本文件 §七 / §8.3** | §七 · §8.3 | 由 L0/L1/L2 派生，**不引入新事实** |

> ⚠️ **为什么必须这样**：本轮实测发现 **5 份台账的「文件头核对日期 / 组件判定」已被正文推翻**：
> `like/api.md:1220`（仍写 NOT EQUIVALENT）被 `:42`（2026-10-27 订正）推翻 ·
> `like/api.md:468`（015 未修）被 `:43` 推翻 · `like/rpc.md:974` 被 `:978-983` 推翻 ·
> `message/api.md:10`（日期 10-23）实际已到 10-27 · `message/rpc.md:16`（10-23）实际已到 10-28。
> ⇒ **直接引用台账头的「组件判定」行会低估现状** ⇒ 本文件一律引用**条目级最新状态**。

---

## 二、矩阵 A-1：定位与规模（39 行 = 38 组件 ＋ 1 共享层 · 2026-10-29 实测）

> `main/LOC` = `src/main/java` 下 `.java` 的**文件数 / 逐文件 `splitlines()` 行数**
> （**不是** `cat | wc -l` —— 后者少计无尾换行的文件）。
> `tests` = **源声明测试方法数**（**环境无关口径** · 2026-10-31 起）= `src/test/java` 里 `@Test` / `@ParameterizedTest` / `@RepeatedTest` / `@TestFactory` / `@TestTemplate` 的**方法声明数** ⇒ ⇒ **一个参数化方法算 1**（不是 surefire 展开后的 N 条 case）⇒ ⇒ **读源码 ⇒ 可复算 ⇒ 与 Docker 状态无关**。⚠️ **【2026-10-31 订正 · 合并收口执行者】本列此前 = 「2026-10-29 那次 `mvn clean verify` 的实测执行读数」⇒ ⇒ 已改为环境无关口径** —— 原口径有**两个独立限制**：① **陈旧** —— 此后各域新增/改动的测试**没进本列**（例：`user-rpc` 原 **168**，2026-10-31 实测执行 **184** ⇒ 差 **16**）② **环境相关** —— Docker 停 ⇒ 容器在 `@BeforeAll` 起不来 ⇒ **整类 N 个方法折叠成 1 条 error** ⇒ 读数**偏小**；而 `@ParameterizedTest` 被 surefire **展开成 N 条 case** ⇒ 读数**偏大** ⇒ ⇒ **同一份代码两次跑能出两个数** ⇒ ⇒ ⇒ **原口径不可当「覆盖面」用** ⇒ ⇒ 故改。⚠️ **【2026-10-31 再订正 · 合并收口执行者 · G2 回执触发】两个口径的差是**两个方向相反的量** ⇒ ⇒ **不可相减**：① **源声明数 > 原列**（**真增量** = 陈旧）= **13 行 · 合计 +150**（12 个域行 + 共享层 `service/common` 405→480；例：`user-rpc` 168→184 · `admin-rpc` 128→139 · `follow-rpc` 86→97）② **原列 > 源声明数**（`@ParameterizedTest` / `@TestFactory` 被 surefire **展开成 N 条 case**）= **6 行 · 合计 −219**（`article-api` 216 vs 133 · `task-api` 135 vs 75 · `follow-api` 143 vs 96 · `users-common` 39 vs 25 · `hot-api` 116 vs 109 · `task-rpc` 137 vs 129）③ **两者相等** = **14 行** ⇒ ⇒ ⇒ **⇒ 「陈旧」的真证据是方向 ①，不是「两个总数相减」**（我第一版就是拿总数相减去论证「陈旧」⇒ ⇒ **判据形态错**，已改）。✅ **本轮已落**：**全仓源声明测试方法数** = **3,307**（**29 个含测模块**）⇒ ⇒ **A-1 的 39 行已逐行重算并回填** ⇒ ⇒ **列求和 = 3,307 = 实测** ⚠️ **【2026-10-31 · 合并主干后 · 零行位移再订正】** 3,299 → **3,300**（+1 = 主干 G3 那笔的 `article-rpc` 198→199）⇒ ⇒ **这次是 M2 判据自己抓到的**（上一轮我把本句的写法改成可匹配形态后，M2 从「空转」变成「真生效」⇒ ⇒ 本轮它第一次就报了差值）⇒ ⇒ **说明该判据确实在盯**。 ⚠️ **【2026-10-31 · G2 lane · 第二来源收敛轮 · 零行位移订正】** 本句的总数由 **3,226** 改为 **3,299**（+73）—— 其中 **+7 是本 lane 的 5 个组件**（`like-api` 111→113 · `like-rpc` 162→163 · `message-api` 121→122 · `message-rpc` 114→115 · `task-api` 82→84 ⇒ ⇒ 本 lane 只改**自己那 5 行**，逐行对账由机器守门保证）· **+66 是并入主干的其他 lane 的增量**（本 lane 未改那些行）⇒ ⇒ 合计数按**实测**回填。 ⚠️ **本句的写法同时修掉一处「判据空转」**：机器守门 M2 的匹配式要求「`**全仓源声明测试方法数** = **数字**`」（**数字也要带 `**`**）⇒ ⇒ 原写法「`**全仓源声明测试方法数 = 3,226**`」**匹配不上** ⇒ ⇒ M2 一直停在「未找到 ⇒ 跳过」（**空转**）⇒ ⇒ 现改为可匹配形态 ⇒ ⇒ **M2 从此真的生效**（⚠️ `tools/**` 归共享层写权 ⇒ **本 lane 没有改那个脚本**，只是把文档写成它能匹配的形态 ⇒ ⇒ 若要改脚本本身 ⇒ ⇒ 转派共享层那半） ⇒ ⇒ **机器守门 `tools/difftest/check-source-test-decl-count.py`**（逐行对账 + 求和对账 + 行数 39 ⇒ ⇒ **A-1 忘了更新就红** ⇒ ⇒ 文档数字与源码脱节**机器可见**）⇒ ⇒ **M-12 已闭合**。⚠️ **本列是「覆盖面」口径，不是「执行结果」口径** ⇒ ⇒ **「有 N 个 `@Test` 方法」≠「N 个断言成立」** ⇒ ⇒ **运行期证据仍以 surefire XML 为准**（§七 P0 #6 的「全量回归 N 条 / 0F / 0E」是**运行期**读数 ⇒ 与本列**两个口径、不可相减**）。 ⚠️ **【2026-10-31 · 第三波合并后 · 零行位移再订正】** 3,300 → **3,314**（+21 = 第三波各域新增守门测试类：hot-api +7 · comment-api +4 · favorite-api +3 · like-api +2 · task-api +2 · like-rpc +1 · message-api +1 · message-rpc +1） ⇒ ⇒ 🔴 **同时订正本句此前那个「3,300」本身就是错的** —— 用 `git grep -o` 在 `3431111f` 上**实测**当时的源声明总和 = **3,293**（**不是 3,300**） ⇒ ⇒ 即 **`check-source-test-decl-count.py` 的 M2 在 `3431111f` 上本来就是红的** ⇒ ⇒ **上一笔提交里「29 个 `check-*.py` 全绿」那句是错的**（与此前 `check-wave-coupling.py` 那次**同族**：**把 lane 报的数字当成了实测值**） ⇒ ⇒ **教训**：凡要往文档写一个「总数」，**必须取守门自己打印的实测值**，不能取 lane 回执里的数字（⇒ 见 `docs/Pitfalls/_cross/testing-pitfalls.md`）。 ⚠️ **上面那句「3,299 → **3,300**（+1 = 主干 G3 那笔的 `article-rpc` 198→199）」的记录本身也作废** —— 那次改判**当时就是错的**（真相是 3,293），**不是事后位移**。 ⚠️ **【2026-10-31 第三轮订正 · 合并收口执行者】本列口径再收窄一格 ⇒ **只算代码态**：注释 / javadoc 里提到的 `@Test` **文本不算** ⇒ ⇒ 原口径把 `HttpShutdownProbeWithoutServlet` 的 javadoc `{@code @Test}` 算成了一个测试 ⇒ ⇒ **全仓虚高 7**（`service/common` +1 · `follow-api` +2 · `user-api` +2 · `admin-api` +2）⇒ ⇒ 本列总和 **3,314 → 3,307** ⇒ ⇒ 订正后**共享层源声明 480 == surefire 执行读数 480**（执行读数 480 仍与订正后源声明一致）（`mvn -o -pl service/common -am test` · surefire XML：tests=480 · skipped=0 · failures=0 · errors=0）⇒ ⇒ **两个口径互证成立** ⇒ ⇒ **守门 `check-source-test-decl-count.py` 已改代码态计数**（`code_state_mask` ＋ M4 自证）

| 域 | 组件 | Go 原件（真源） | RAG 实现 | main/LOC | tests |
|---|---|---|---|---|---|
| `article` | `article-api` | `service/article/api/internal/handler/routes.go` + `internal/logic/`（7 handler） | `service/article/article-api/`（`controller/ArticleController.java`） | 43 / 5,470 | 133 |
| `article` | `article-rpc` | `service/article/rpc/internal/logic/` + `internal/svc/`（6 rpc） | `service/article/article-rpc/`（`impl/ArticleServiceGrpcImpl.java`） | 39 / 8,737 | 199 |
| `article` | `article-common` | `service/article/common/errmsg/errmsg.go`（45 行） | `service/article/article-common/…/errmsg/ErrorCode.java` | 1 / 257 | 15 |
| `article` | `article-proto` | `proto/article.proto` | `service/article/article-proto/src/main/proto/article.proto` | —（生成） | 0 |
| `comment` | `comment-api` | `service/comment/api/internal/handler/routes.go` + `internal/logic/`（7） | `service/comment/comment-api/`（`controller/CommentController.java`） | 37 / 3,479 | 66 |
| `comment` | `comment-rpc` | `service/comment/rpc/internal/logic/`（7） | `service/comment/comment-rpc/`（`impl/CommentServiceGrpcImpl.java`） | 43 / 8,656 | 146 |
| `comment` | `comment-common` | `service/comment/common/errmsg/errmsg.go` | `service/comment/comment-common/…/errmsg/`（4 类） | 4 / 567 | 27 |
| `comment` | `comment-proto` | `proto/comment.proto`（245 行） | `service/comment/comment-proto/src/main/proto/comment.proto` | —（生成） | 0 |
| `favorite` | `favorite-api` | `service/favorite/api/internal/handler/routes.go` + `internal/logic/`（7） | `service/favorite/favorite-api/`（`controller/`） | 36 / 3,885 | 158 |
| `favorite` | `favorite-rpc` | `service/favorite/rpc/internal/logic/`（7） | `service/favorite/favorite-rpc/`（`impl/FavoriteServiceGrpcImpl.java`） | 36 / 7,950 | 143 |
| `favorite` | `favorite-common` | `service/favorite/common/errmsg.go` | `service/favorite/favorite-common/…/errmsg/`（4 类） | 4 / 875 | 29 |
| `favorite` | `favorite-proto` | `proto/favorite.proto`（107 行） | `service/favorite/favorite-proto/src/main/proto/favorite.proto` | —（生成） | 0 |
| `follow` | `follow-api` | `service/follow/api/internal/handler/routes.go` + `internal/logic/`（8） | `service/follow/follow-api/`（`controller/FollowController.java`） | 31 / 3,585 | 99 |
| `follow` | `follow-rpc` | `service/follow/rpc/internal/logic/`（8） | `service/follow/follow-rpc/`（`impl/FollowServiceGrpcImpl.java`） | 16 / 2,717 | 99 |
| `follow` | `follow-common` | `service/follow/common/errmsg.go` | `service/follow/follow-common/…/errmsg/`（4 类） | 4 / 562 | 37 |
| `follow` | `follow-proto` | `proto/follow.proto`（52 行） | `service/follow/follow-proto/src/main/proto/follow.proto` | —（生成） | 0 |
| `hot` | `hot-api` | `service/hot/api/internal/handler/routes.go`（1） | `service/hot/hot-api/`（`controller/HotArticleController.java`） | 17 / 2,452 | 116 |
| `hot` | `hot-rpc` | `service/hot/rpc/internal/logic/`（5） | `service/hot/hot-rpc/`（`impl/HotServiceGrpcImpl.java`） | 15 / 2,535 | 135 |
| `hot` | `hot-common` | `service/hot/common/errmsg.go`（121 行） | `service/hot/hot-common/…/errmsg/`（2 类） | 2 / 172 | 14 |
| `hot` | `hot-proto` | `proto/hot.proto` | `service/hot/hot-proto/src/main/proto/hot.proto` | —（生成） | 0 |
| `like` | `like-api` | `service/like/api/internal/handler/routes.go` + `internal/logic/`（6） | `service/like/like-api/`（`controller/LikeController.java`） | 37 / 3,479 | 113 |
| `like` | `like-rpc` | `service/like/rpc/internal/logic/`（6） | `service/like/like-rpc/`（`impl/LikeServiceGrpcImpl.java`） | 42 / 7,535 | 163 |
| `like` | `like-common` | `service/like/common/errmsg/errmsg.go` | `service/like/like-common/…/errmsg/`（4 类） | 4 / 515 | 18 |
| `like` | `like-proto` | `proto/like.proto` | `service/like/like-proto/src/main/proto/like.proto` | —（生成） | 0 |
| `message` | `message-api` | `service/message/api/internal/handler/routes.go` + `internal/logic/`（13） | `service/message/message-api/`（`MessageController` + `MessageAdminController`） | 49 / 4,992 | 122 |
| `message` | `message-rpc` | `service/message/rpc/internal/logic/`（9） | `service/message/message-rpc/`（`impl/MessageRpcServiceImpl.java`） | 19 / 3,852 | 115 |
| `message` | `message-common` | `service/message/common/errmsg/errmsg.go` | `service/message/message-common/…/errmsg/`（3 类） | 3 / 526 | 21 |
| `message` | `message-proto` | `proto/message.proto` | `service/message/message-proto/src/main/proto/message.proto` | —（生成） | 0 |
| `task` | `task-api` | `service/task/api/internal/handler/routes.go`（1） | `service/task/task-api/`（`controller/TaskController.java`） | 11 / 1,496 | 84 |
| `task` | `task-rpc` | `service/task/rpc/internal/logic/`（1） | `service/task/task-rpc/`（`impl/TaskServiceGrpcImpl.java`） | 28 / 4,310 | 129 |
| `task` | `task-proto` | `proto/task.proto` | `service/task/task-proto/src/main/proto/task.proto` | —（生成） | 0 |
| `users` | `user-api` | `service/user/user/api/internal/handler/routes.go` + `internal/logic/`（9） | `service/users/user/user-api/`（`controller/UserController.java`） | 43 / 3,454 | 138 |
| `users` | `user-rpc` | `service/user/user/rpc/internal/logic/`（9） | `service/users/user/user-rpc/`（`impl/UserServiceGrpcImpl.java`） | 15 / 2,781 | 184 |
| `users` | `admin-api` | `service/user/admin/api/internal/handler/routes.go` + `internal/logic/`（13） | `service/users/admin/admin-api/`（`controller/AdminController.java`） | 56 / 3,903 | 160 |
| `users` | `admin-rpc` | `service/user/admin/rpc/internal/logic/`（13） | `service/users/admin/admin-rpc/`（`AdminServiceGrpcImpl.java`） | 20 / 3,608 | 139 |
| `users` | `users-common` | `service/user/common/{cryptx,errmsg,jwt}`（3 文件） | `service/users/users-common/`（11 类） | 11 / 1,045 | 25 |
| `users` | `user-proto` | `proto/user.proto` | `service/users/user/user-proto/src/main/proto/users.proto`（**已改名**） | —（生成） | 0 |
| `users` | `admin-proto` | `proto/admin.proto` | `service/users/admin/admin-proto/src/main/proto/admin.proto` | —（生成） | 0 |
| **共享层** | `service/common` | `service/common/`（5 包：`communityfact` / `logger` / `observability` / `response` / `snowflake`） | `service/common/`（73 类） | 73 / 15,313 | 480 |

> ⚠️ **口径**：
> - **38 组件 = 10 api + 10 rpc + 8 common + 10 proto**（AGENTS.md §3.5，**本轮实测自洽**）。`task` 域**无 `-common`**（Go 侧同样没有，**不是遗漏**）⇒ `task` 计 3 个、`users` 计 7 个。
> - **`service/common` 是跨服务共享层，不在 38 之内**（AGENTS.md §5.3f：写权归 G4）⇒ 单列第 39 行。
>   ⚠️ **源台账的行数陷阱**：`docs/DRIFT/_cross/component-coverage-matrix.md` 的当前态表**有 39 行**（38 组件 ＋ 1 行 `跨服务 / service/common`），标题写「38 组件」⇒ **按行数数会多数 1 行**。
> - `*-proto` 的 `main/LOC = —（生成）`：代码由 protoc 生成，不手写（AGENTS.md §3.4）。**`user.proto` → `users.proto` 的改名**是记录在案的表达层归一。

---

## 三、矩阵 A-2：终点指标与当前判定（39 行 = 38 组件 ＋ 1 共享层）

> **通用终点指标**（按组件类型引用，不重复展开）：
>
> - **【API 型】** ① 端点集合：Go `routes.go` 的 `(method, path)` **归一化后**与 RAG 方法级映射**集合相等**；② JWT 保护端点集合与 Go `rest.WithJwt` 段**逐条一致**；③ 每端点 **≥1** 条断言**业务结果**（非仅状态码）的测试；④ 每端点 **≥1** 条**失败路径**测试；⑤ 失败路径**不返回误导性成功**。
> - **【RPC 型】** ① Go proto 的 `rpc` 方法集合 **≡** RAG `*ImplBase` 子类覆写方法集合（大小写归一）；② 每方法 **≥1** 条测试；③ 每个**写路径**证明事务边界或幂等；④ 下游超时 / 不可用**有明确行为且有断言**；⑤ 业务码经 gRPC 传输后**可还原**。
> - **【COMMON 型】** ① Go 侧该 `<域>-common` **每个文件**都有对应 RAG 类；② 码值 + 文案 + 未知码回落**逐字节一致**；③ 业务码经 gRPC 传输后可还原。
> - **【PROTO 型】** ① `.proto` 剥 `option` / 空白后行为层零差异；② message 名 / 字段名 / 字段号 / rpc 方法逐条一致；③ `optional` presence 语义逐条核对；④ 字段号**无硬编码覆盖**；⑤ 消费点构造方式与 Go 一致；⑥ wire 层字节对拍。

| 域 | 组件 | 必达终点指标（可判定） | 判定命令（**覆盖范围见 §六**） | R | E | **权威来源**（L0） | 仍开放项 | 状态 |
|---|---|---|---|---|---|---|---|---|
| `article` | `article-api` | 【API 型】全 6 条 | `python3 tools/difftest/check-surface-parity.py --domain article`（契约面）＋ 模块级 `mvn -pl service/article/article-api -am test` | **R3** ↑ | E3 | `docs/DRIFT/article/api.md:26`（判定）· `:886`（`UNRESOLVED-002`）· `:1095`（双侧 0 条） | `UNRESOLVED-002`（**跨组件**：依赖 article-rpc 响应语义约定） | ✅ |
| `article` | `article-rpc` | 【RPC 型】全 5 条 | `bash tools/api-full-parity-probe/article/run.sh`（**双侧活体** · 3 场景逐字节 ＋ **两侧播种同一行**）＋ `mvn -o -pl service/article/article-rpc -am test`（**真 Kafka 门禁 4 条** ＋ **写路径边界 1 条**（真 PG ＋ 死 broker））＋ `python3 tools/difftest/check-grpc-interceptor-wiring.py` | **R3** ↑ | **E4** | `docs/DRIFT/article/rpc.md` **§12**（§12.1 `S-5` 改判 · §12.2 `U-4′` 销号 · §12.3 `U-3`/`U-4` 闭合 · §12.4 `U-9` 拆半 · §12.5 RPC 型 5 条逐条核 · §12.6 变异 3/3 · §12.7 可复算判定命令 · §12.8 结论 · §12.9 `S-5` 连带项重评估）· `tools/api-full-parity-probe/article/README.md`（落仓探针） | ✅ **`S-5` 改判「范围外」**（judge §1.1 **R0** = Go 侧无对应件）⇒ 原写「⛔ 阻塞」是**错的** ⇒ 实为**永不闭环**（`AGENTS.md:43` 的 `knowledge`/`points`/`security` 三域**不迁移** ⇒ 不是待办）⇒ **不计入本组件必相对齐项**<br>✅ **`U-4′` 销号**（双侧活体 **0 条 → 3 场景逐字节** ＋ **两侧播种同一行** · **L4**）<br>✅ **`U-3` / `U-4` 闭合**（**真 Kafka** · **行为断言**而非属性断言）⇒ ⚠️ **`U-4` 的归因订正**：承重项是 **`FixedBackOff(0L,0L)`**，**不是** `setSeekAfterError(true)` ⇒ 生产 javadoc 已按实测机制改写（AGENTS.md §3.3.2）<br>🟡 **`U-9` 拆两半**：`U-9a` ✅ 闭合（offset 提交 / 跳过语义 · 两侧同构）· `U-9b` 🟡 **取证缺口**（**不降 R** ⇒ 按 §1.1 ⑥ 第 4 条不计入）<br>✅ **§七 RPC 型 5 条逐条核**（③ ④ 本轮补齐）· **变异 3/3 精确转红**（全部已还原）⇒ **归属本组件的必相对齐项 = 0** | ✅ |
| `article` | `article-common` | 【COMMON 型】全 3 条 | `GO_REPO=… bash tools/errmsg-probe/run-parity.sh` ⇒ EXIT=0 | **R4** | E4 | `docs/DRIFT/article/common.md:5`·`:50-53` | **0 条** | ✅ |
| `article` | `article-proto` | 【PROTO 型】①–⑤（⑥ 不适用） | `python3 tools/difftest/check-surface-parity.py --domain article`（rpc 方法集） | **R3** | E3 | `docs/DRIFT/article/proto.md:18`·`:175`（`P-U-1`）· `:236-238` | `P-U-1`（跨栈活体未实测 ⇒ 取证缺口）· **台账未见变异验证** | ✅ |
| `comment` | `comment-api` | 【API 型】全 5 条 | `bash tools/difftest/comment/run_diff.sh` ⇒ 23 步矩阵 | **R4** | E4 | `docs/DRIFT/comment/api.md:24`·`:334-344` | `U4`（共享台账过期结论待更正）· `U10`（D1 机制未查实，可观察行为已定） | ✅ |
| `comment` | `comment-rpc` | 【RPC 型】全 5 条 | `bash tools/difftest/comment/run_diff.sh`（23 步矩阵）＋ `mvn -pl service/comment/comment-rpc -am test` | **R4** ↑ | E4 | `docs/DRIFT/comment/rpc.md:38`·`:179`(`D8`)·`:180`(`D9`)·`:668`(`E47`) | `D8`/`D9`（**落点 `service/common`** ⇒ 跨组件残留，阻塞**域**）· `U13`（跨栈业务码不可传输，协议层） | ✅ |
| `comment` | `comment-common` | 【COMMON 型】全 3 条 | `bash tools/difftest/comment/run_diff.sh`（错误码链路被 23 步矩阵端到端覆盖） | **R3** | E3 | `docs/DRIFT/comment/common.md:24`·`:112-125` | **0 条必对齐项** · **台账未见变异验证** | ✅ |
| `comment` | `comment-proto` | 【PROTO 型】全 6 条 | `python3 tools/difftest/comment/check-proto-parity.py` ＋ `run_diff.sh`（wire） | **R4** | E4 | `docs/DRIFT/comment/proto.md:19`·`:112`(`U4`)·`:113`（变异实测） | `U4`（跨栈业务码不可传输，协议层不可修） | ✅ |
| `favorite` | `favorite-api` | 【API 型】全 5 条 | `bash tools/api-envelope-probe/favorite/run.sh` ＋ `bash tools/api-full-parity-probe/favorite/run.sh` | **R4** | E4 | `docs/DRIFT/favorite/api.md:7-13`·`:316-325`·`:586`(`U9`) | `U9`（Go 16062 DevServer HTTP 面，RAG 无 ⇒ 待拍板）· 405 多一个 `Content-Type`（落点 `service/common`）· `B2`（流程项） | ✅ |
| `favorite` | `favorite-rpc` | 【RPC 型】全 5 条 | `bash tools/grpc-contract-probe/favorite/run.sh` ＋ `bash tools/ddl-parity-probe/favorite/run.sh` ＋ `bash tools/sql-statement-probe/favorite/run.sh` ＋ `bash tools/api-full-parity-probe/favorite-rpc/run.sh`（**rpc 层双侧活体**） | **R4** | **E4** | `docs/DRIFT/favorite/rpc.md:14-19`·`:504`·`:1801-1804` | `U7`（**环境受限** ⇒ **已解除**：etcd 宿主口 `49319` 已发布 ⇒ 双侧活体成立 · 见 `tools/api-full-parity-probe/favorite-rpc/`）· `U9`（待拍板）· `E65`（出向 gRPC 不继承 deadline ⇒ 落点共享层） | ✅ |
| `favorite` | `favorite-common` | 【COMMON 型】全 3 条 | `bash tools/errmsg-probe/favorite/run-parity.sh` ⇒ EXIT=0（108 行逐字节） | **R4** | E4 | `docs/DRIFT/favorite/common.md:19-20`·`:52-62` | **0 条**（2 条 OBS 已签字） | ✅ |
| `favorite` | `favorite-proto` | 【PROTO 型】①–⑤ ＋ wire 字节 | `bash tools/proto-wire-probe/favorite/run.sh`（6 组消息、两个运行时） | **R3** ↓ | E4 | `docs/DRIFT/favorite/proto.md:18`·`:156`·`:183` | **0 条** · **台账未见变异验证** ⇒ R4 不成立 | ✅ |
| `follow` | `follow-api` | 【API 型】全 5 条 | `mvn -pl service/follow/follow-api -am test` ⇒ `FollowApiMvcPipelineTest` 等（**Go 出处写进用例**：`helper.go:23` / `authhandler.go:106` / `checkblacklistmiddleware.go:42-48`）＋ `python3 tools/difftest/check-surface-parity.py --domain follow`（**仅契约面**）<br>✅ **双侧对拍探针已落仓**（`tools/api-full-parity-probe/follow/`）⇒ **20 条「Go == RAG == 冻结期望值」三方逐字节** + **M1~M5 变异精确转红 + 还原转绿** ⇒ 满足 §1.1 的 **R4 ⑦⑧** | **R4** ↑ | **E4** | `docs/DRIFT/follow/api.md:12-13`（52/52 = **历史声明**）· `tools/api-full-parity-probe/follow/README.md`（落仓探针 · 20 条三方逐字节）· `docs/DRIFT/follow/vs-common.md`（**当前态指针**）· `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiMvcPipelineTest.java:154`·`:177`·`:229`（Go 出处） | `DRIFT-FOLLOW-008`（MaxConns 闸门粒度 ⇒ **落点 `service/common`** ⇒ 跨组件残留）<br>⚠️ **2026-10-30 真实性复核（主线 · 收口执行者）**：本行把 `DRIFT-FOLLOW-008` 记成**开放缺陷** ⇒ **已陈旧** —— 该差异**已于 2026-10-25 经用户签字**（`SD-2026-10-25-01` / T-2 ⇒ 签字档 `docs/Intentional_Deviations/global/common-tails.md:11` + `:18`「对应的 DRIFT 条目 = T-2（`follow` 域 `DRIFT-FOLLOW-008`）· T-5（`hot` 域 `D27`）⇒ 两条同一件事」）⇒ ⇒ **它是「有意偏离」不是缺陷** ⇒ **不计入本行的开放残余差异**（原值保留，不改写）· 核对日期 2026-10-30。· ✅ **§九 G-1 已闭合**（2026-10-29） | ✅ |
| `follow` | `follow-rpc` | 【RPC 型】全 5 条 | `bash tools/metrics-probe/follow/run.sh`（**双侧活体指标 + 行为对拍** · Go 147 行 / RAG 155 行 · **28 步**请求矩阵含 **10 步故障注入** ⇒ 状态码 + 响应体逐字节一致）＋ `mvn -o -pl service/follow/follow-rpc -am test`（**86 条全绿**）<br>✅ **探针已落仓** ⇒ 满足 §1.1 的 **R4 ⑦⑧**（2 处变异精确转红 + 还原转绿） | **R4** ↑ | **E4** | `docs/DRIFT/follow/rpc.md`（**DRIFT-FOLLOW-006 的落地**节 · 12.1~12.5 逐条）· `tools/metrics-probe/follow/README.md`（落仓探针）· `tools/metrics-probe/follow/expected-diff.txt`（冻结的 8 条 `_max` 期望）· `docs/DRIFT/follow/vs-common.md` **§17**（**当前态指针**） | ✅ **`DRIFT-FOLLOW-006` 已关闭**（2026-10-29 · `573cf08f`）—— RAG 侧 `/actuator/prometheus` 观测口 **16065**（与 Go `DevServer.Port` **同口**）<br>🆕 **修一个真缺陷**：`_bucket` 边界原用 Micrometer 的 **68 个 HdrHistogram 桶**，与 Go 的 `prometheus.DefBuckets`（`client_golang@v1.23.2/prometheus/histogram.go:271`）**零重合** ⇒ 同一段 `histogram_quantile()` 两侧结果不同 ⇒ **不是表达层差异** ⇒ 改用 `serviceLevelObjectives(...)` 逐个钉 Go 的 11 个桶 ⇒ 逐个相同<br>⚠️ **根因（表达层）**：actuator 只给 endpoint + 自动配置，**不给 servlet 容器** ⇒ `WebApplicationType=NONE` ⇒ `management.endpoints.web.*` 一直是**死配置** ⇒ pom 加 `spring-boot-starter-web` ＋ yml 关 Nacos 自动注册（自动注册注册的是 **web 容器端口** ⇒ 会把观测口注册成 `follow-rpc` ⇒ 业务码塌 1015）<br>✅ **RPC 型 ①~⑤ 逐条实跑**（收口执行者复核）：① Go `FollowService=8` ≡ RAG 8（`check-surface-parity.py`）② 8 个方法**每个 ≥1 条测试**（`FollowServiceGrpcImplTest` 等）③ 写路径 `@Transactional(rollbackFor=Exception.class)`（`FollowBlockTransactionService:70`）④ 下游不可用有明确行为 + 断言（探针 10 步故障注入）⑤ 业务码经 gRPC 传输可还原（28 步状态码逐字节一致）<br>⚠️ **5 条残余差异全部 P3 / D 类**（A/B/C 类 **0**）⇒ 按 §1.1 ⑥ 第 4 条**不计入**（列入**交付物 5**） | ✅ |
| `follow` | `follow-common` | 【COMMON 型】全 3 条 | `mvn -pl service/follow/follow-common -am test` ⇒ `FollowErrorCodeContractTest` / `FollowGrpcErrorContractTest`（域内 **37 条**） | **R3** | E3 | `docs/DRIFT/follow/common.md:11-12`·`:39-45` | **0 条** | ✅ |
| `follow` | `follow-proto` | 【PROTO 型】①–⑤ | `bash tools/difftest/check-proto-static.sh`（**10 域逐字节** · G-6）＋ `python3 tools/difftest/check-surface-parity.py --domain follow`（rpc 方法集） | **R3** ↑ | E3 | `docs/DRIFT/follow/proto.md:13`·`:78`· `docs/DRIFT/_cross/proto-verification.md:44`（「有网」档） | **0 条必对齐项** · ⚠️ **消费方式未验**（`optional` presence / 字段号硬编码 / Jackson）⇒ **取证缺口** G-19（**不降 R**）⇒ **§1.3 的 proto 分型口径：逐字节 diff 满足 ⑤ ⇒ R3 成立** | ✅ |
| `hot` | `hot-api` | 【API 型】全 5 条 | `python3 tools/difftest/check-tomcat-limits.py`（**仅配置值**）＋ `bash tools/difftest/run-hot.sh`（双侧 29 例） | **R4** | E4 | `docs/DRIFT/hot/api.md:16-27`·`:1237-1238` | `DRIFT-HOTAPI-030`（**第十四轮已落地**，本台账标题过期 —— ⚠️ **2026-10-29 真实性复核：原判「已落地」⇒ 现行态 = 🟡 部分闭合**（**三格显式声明**：① **主轴已闭合**（2049~10000 且非空值 ≤2048）—— 8 域共享层闸门 + favorite 域侧闸门（**复用共享层判据** ⇒ 含段数轴）⇒ 依据 `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:307-314`（与 Go `net/url/url.go:1020` **逐字同形**）+ `:262`；② **「恰好 10001 段」已闭合** —— 10 域两侧同拒同放（task `mode:"off"` ⇒ Go 侧从不解析 ⇒ Go 放行 / RAG 容器恰好 10001 放行 · Tomcat `Parameters.java:247`）⇒ ⚠️ **「favorite 域 L4 实测 = Go 10001 段 / RAG 10002 段」是 2026-10-27 陈旧快照**（favorite 域侧闸门 2026-10-28 才落码）；③ **残留 1 格**：**task 域 >10001 段** ⇒ Go 放行 / RAG 容器 400+HTML ⇒ **RAG 更严** ⇒ **活体证据缺失 ⇒ 待核实** ⇒ ⛔ 不许改常量（改 10000 ⇒ Tomcat 抢在应用层之前 HTML 400 拒 ⇒ 制造新漂移）· 核对日期 2026-10-29））· `DRIFT-HOTAPI-031`（框架限制，候选未签字） | ✅ |
| `hot` | `hot-rpc` | 【RPC 型】全 5 条 | `bash tools/difftest/run-hot-rpc-difftest.sh`（gRPC 逐字节）＋ `check-grpc-interceptor-wiring.py` | **R4** | E4 | `docs/DRIFT/hot/rpc.md:11-20`·`:717`(`DRIFT-HOTRPC-006`)·`:1047-1057` | `DRIFT-HOTRPC-006`（**已取证未签字**）· `DRIFT-HOTRPC-005`（保持不修，已登记）· proto 回归网（用户决定不做） | ✅ |
| `hot` | `hot-common` | 【COMMON 型】全 3 条 | `mvn -pl service/hot/hot-common -am test` ⇒ `HotErrorCodeContractTest`（**期望值逐条抄自 Go `errmsg.go:34-46`** const 块，断言 11 个码 + 顺序 + 重复）＋ `HotErrorMessagesContractTest` ＋ `bash tools/errmsg-probe/hot/run-parity.sh` ＋ `bash tools/proto-wire-probe/hot/run.sh` | **R4** ↑ | **E4** | `docs/DRIFT/hot/common.md:11-12`· `tools/errmsg-probe/hot/`（错误码 · EXIT=0 · 变异 4/4）· `tools/proto-wire-probe/hot/`（proto 消息体字节 · EXIT=0 · 变异 3/3）· `:57-58`（变异 11/11）· `service/hot/hot-common/src/test/java/com/rag/hot/hot/common/errmsg/HotErrorCodeContractTest.java:18-27`（**Go 出处**） | **0 条** · ✅ **§九 G-5 已闭合**（2026-10-29）：错误码 + proto wire **两份探针均落仓** ⇒ 满足 §1.1 的 **R4 ⑦⑧** | ✅ |
| `hot` | `hot-proto` | 【PROTO 型】①–⑤ | `mvn -o -pl service/hot/hot-rpc test -Dtest=HotProtoContractTest`（**期望值由 `generate.py` 从 Go 真源 `proto/hot.proto` 生成**，记录真源 sha256）＋ `python3 tools/difftest/check-surface-parity.py --domain hot` | **R3** ↑ | E3 | `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/proto/HotProtoContractTest.java:38-45`（期望值**由真源生成**）· `service/hot/hot-rpc/src/test/resources/proto-contract/{hot-proto-contract.expected.txt,generate.py}`（**已入库**）· `docs/DRIFT/hot/vs-common.md:67`（`H-1` 已落码）· `docs/DRIFT/_cross/proto-verification.md:21`（`hot` 0 行 diff · 11=11 · 18=18） | **0 条行为差异** · ⚠️ **判定记录在 `hot/vs-common.md` 而非 `hot/proto.md`**（**位置不统一** ⇒ §九 G-18，**不降 R**）· ⚠️ `hot/vs-common.md:59` 与 `:67` **自相矛盾**（§九 G-15） | ✅ |
| `like` | `like-api` | 【API 型】全 5 条 | `mvn -pl service/like/like-api -am test` ⇒ `LikeApiGoAlignmentTest` / `LikeApiMvcPipelineTest` 等（**Go 出处写进用例**）＋ `python3 tools/difftest/check-surface-parity.py --domain like`（**仅契约面**）<br>✅ **双侧对拍探针已落仓**（`tools/api-full-parity-probe/like/`）⇒ 阶段 A **9 case 逐字节** / 阶段 B DC **970 B sha256 相同** / 阶段 C **E60 双失败注入** + **3 变异精确转红** ⇒ 满足 §1.1 的 **R4 ⑦⑧** | **R4** ↑ | **E4** | `docs/DRIFT/like/api.md:42-43`（6 条 MISMATCH 全修）· `tools/api-full-parity-probe/like/README.md`（落仓探针）· `:772`（§9.1 的 **U-01~U-08 逐条结论**）· `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiGoAlignmentTest.java` | **0 条必对齐项**（012~017 全修）· 4 条待签字候选（021/022/SD-10/SD-11）· `U-01`~`U-08`（**取证缺口**，非必对齐）· ✅ **§九 G-2 已闭合**（2026-10-29）· 失败路径矩阵见 `docs/DRIFT/like/vs-common.md` | ✅ |
| `like` | `like-rpc` | 【RPC 型】全 5 条 | `bash tools/api-full-parity-probe/like/run.sh`（**已落仓** · 阶段 A **8 case / 627 B** 逐字节 + 阶段 B DC **970 B sha256** + 阶段 C **107 B** · **M1~M3 变异精确转红 + 还原转绿** ⇒ `MUTATE_EXIT=0`）＋ `mvn -o -pl service/like/like-rpc -am test`<br>⚠️ **但该探针是 API 型 ⇒ 不覆盖本组件的 gRPC 面** ⇒ **R4 ⑦⑧ 对本组件仍不成立** ⇒ **R 档维持 R2** | **R2** ↓ | E3 | `docs/DRIFT/like/rpc.md:35-39`·`:1538`（global `E60`）· `:1572`· `tools/api-full-parity-probe/like/README.md`（**已落仓探针** · 2026-10-29） | **`DRIFT-LIKE-003`**（**三段拆分：A 投递链路已闭环 · B 权威读语义 Java 化已闭环** ⇒ 仅剩 **C 暴露形态待拍板**；台账旧述「6 个 4 行骨架 ⇒ `UNRESOLVED`」**已于 2026-10-29 实测过时**）· ⚠️ **`E60` 已于 2026-10-29 修并销案**（`currentUserLikeTotal` 双失败少传 error ⇒ 已修 + 已守门 + 已实跑 4/4 绿）· `L-U9`/`L-U11` ⇒ ⚠️ **本行 R2 / 🟡 未重算**（Jason 2026-10-29 裁定「阶段一按现矩阵判」）<br>✅ **收口执行者复核（2026-10-29）**：① Go `LikeService=6` ≡ RAG 6（`check-surface-parity.py`）② 6 个方法**每个 ≥1 条测试**（`getLikeCount` 6 / `likeAction` 3 / `getUserTotalLike` 3 / `getLikeState` 2 / `getUserLikeList` 2 / `getTargetLikerList` 1 个测试文件命中）③ 写路径有事务边界（`LikeMessageIngestService:124` `@Transactional(rollbackFor=Exception.class)`）④ 下游超时/不可用有断言（`LikeFactDispatchContractGateTest` / `LikeRedisDisconnectFastRejectGateTest` / `LikeRpcGrpcInterceptorWiringTest`）⑤ 业务码经 gRPC 传输可还原（`E60` 已销案）<br>⚠️ **本组件仍缺的只有一项**：**gRPC 级的双侧落仓探针**（现有的是 API 型）⇒ ⑦⑧ 不成立 ⇒ **不上调 R 档** | 🟡 |
| `like` | `like-common` | 【COMMON 型】全 3 条 | `mvn -pl service/like/like-common -am test` ⇒ `LikeErrorContractTest`（**29 个码逐条 1:1** · 文案表 29 条 · 未知码回落 `FAIL` · `CodeError` 格式与 JSON 序列化 · `newCode`/`newCodeMsg` 区分）＋ `LikeGrpcErrorTest` | **R3** ↑ | E3 | `service/like/like-common/src/test/java/com/rag/like/like/common/errmsg/LikeErrorContractTest.java:28-29`（**Go 出处写进 javadoc**：`errmsg.go:37-67` / `:69-99` / `:101-107`）· `:77-83`（29 条计数） | **0 条行为差异**（逐条钉死）· ⚠️ **无专属台账 ⇒ 判定记录缺失**（**证据记录**缺口，**不是**行为缺口 ⇒ §九 G-17，**不降 R**） | ✅ |
| `like` | `like-proto` | 【PROTO 型】①–⑤ | `bash tools/difftest/check-proto-static.sh`（**10 域逐字节** · G-6）＋ `python3 tools/difftest/check-surface-parity.py --domain like` | **R3** ↑ | E3 | `docs/DRIFT/_cross/component-coverage-matrix.md:63`（**无专属台账** ⇒ **记录缺口** G-17）· `docs/DRIFT/_cross/proto-verification.md:44`（「有网」档）· `docs/Intentional_Deviations/like/proto.md` | **0 条必对齐项** · ⚠️ **消费方式未验**（`optional` presence / 字段号硬编码 / Jackson）⇒ **取证缺口** G-19（**不降 R**）⇒ **§1.3 的 proto 分型口径：逐字节 diff 满足 ⑤ ⇒ R3 成立** | ✅ |
| `message` | `message-api` | 【API 型】全 5 条 | `bash tools/semantic-probe/run-parity.sh` ＋ `bash tools/difftest/run.sh` | **R4** | E4 | `docs/DRIFT/message/api.md:1151`·`:1148-1150` | `-010`/`-011`/`-013`（**落点共享层**）· `-012`（**不可修**：Tomcat 序列化吃空格） | ✅ |
| `message` | `message-rpc` | 【RPC 型】全 5 条 | `bash tools/difftest/exp-difftest-grpc-errors-message.sh` ＋ `run-message-ddl-parity.sh` | **R4** ↑ | E4 | `docs/DRIFT/message/rpc.md:1251`·`:1170-1183` | `UNRESOLVED-MSG-008`（三模块同库三账本 ⇒ **跨域**）· `UNRESOLVED-MSG-V-001`（Nacos namespace ⇒ **跨域**）· 共享面三项 | ✅ |
| `message` | `message-common` | 【COMMON 型】全 3 条 | `mvn -pl service/message/message-common -am test` ⇒ `MessageErrorMessagesTest` ＋ `MessageGrpcErrorTest`（**21 条**） | **R3** | E3 | `docs/DRIFT/message/common.md:162-164`（证据强度自陈：非双侧活体）· `:172` | `CodeError` Jackson 序列化**无断言**（取证缺口）· **台账未见变异验证** ⇒ R4 不成立 | ✅ |
| `message` | `message-proto` | 【PROTO 型】①–⑥ | `bash tools/difftest/exp-difftest-grpc-errors-message.sh`（活体往返） | **R3** | E3 | `docs/DRIFT/message/proto.md:112`·`:124-127` | **0 条** · **台账未见变异验证** ⇒ R4 不成立 | ✅ |
| `task` | `task-api` | 【API 型】全 5 条 | `bash tools/difftest/run-task-http-parity.sh`（44 条矩阵） | **R4** | E4 | `docs/DRIFT/task/api.md:24`·`:27`·`:503-527` | `DRIFT-TASK-API-004`（charset/空白 ⇒ **全域共性**，不在本域单修） | ✅ |
| `task` | `task-rpc` | 【RPC 型】全 5 条 | `bash tools/difftest/run-task-kafka-e2e.sh` ＋ `run-task-grpc-xlang.sh` ＋ `run-task-offset-oldest.sh` | **R4** | E4 | `docs/DRIFT/task/rpc.md:23`·`:1519`（`DRIFT-TASK-RPC-008` **已落码**）· `:1646` | `DRIFT-TASK-RPC-008` **已落码**（`:449` 池已启用 + 池参数 64/16/0）· `otel.*` 观测差异（**跨域共性**） | ✅ |
| `task` | `task-proto` | 【PROTO 型】①–⑥ | `bash tools/difftest/check-proto-static.sh`（已覆盖 task ⇒ EXIT=0）＋ `run-task-grpc-xlang.sh` | **R3** | E3 | `docs/DRIFT/task/proto.md:16-18`·`:121-123` | **0 条** · **台账未见变异验证** ⇒ R4 不成立 | ✅ |
| `users` | `user-api` | 【API 型】全 5 条 | `bash tools/difftest/run.sh` ＋ `python3 tools/difftest/matrix.py` ＋ `exp-difftest-redis-blacklist.sh` | **R4** | E4 | `docs/DRIFT/users/user/api.md:13`·`:623-625`（**头部 3 条已被正文更新为 1 条**）· `:619` | `DRIFT-USERAPI-013`（**落点 `service/common`** ⇒ 跨组件；global 已拆半：MaxConns 半边已签字、breaker 半边已落码） | ✅ |
| `users` | `user-rpc` | 【RPC 型】全 5 条 | `bash tools/difftest/exp-difftest-grpc-user9.sh`（43 场景） | **R4** | E4 | `docs/DRIFT/users/user/rpc.md:65`·`:898`(`V-002`)·`:905`(`V-011`)·`:906`(`V-012`) | `V-002`（`status` NOT NULL ⇒ **行为层零差异**，建议保持；**销号签字不在 G1 写权面**）· `V-011`（DEFAULT，**当前不可观察**）· `V-012`（观测面，日志归因）⇒ **三者均非必对齐项** | ✅ |
| `users` | `admin-api` | 【API 型】全 5 条 | `bash tools/difftest/run.sh` ＋ `diff.py --scenario all` | **R4** | E4 | `docs/DRIFT/users/admin/api.md:1184`（`DRIFT-ADMINAPI-001` **已签字 + 严重度下调**）· `:499`(`002` 降级)· `:513`(`003` 已签字) | `DRIFT-ADMINAPI-006 ①`（401 计入观测面 ⇒ OBSERVABLE）· `DRIFT-ADMINAPI-013`（测试基础设施「OS 盲选口」，**非 Go 对照项**） | ✅ |
| `users` | `admin-rpc` | 【RPC 型】全 5 条 | `bash tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up`（18 cases） | **R4** | E4 | `docs/DRIFT/users/admin/rpc.md:1439`（`ARPC-003` **已签字**）· `:3765`(`ARPC-010` 已修) | `ARPC-007`（**Go 真源 12 处字面量转码损坏** ⇒ Go 侧缺陷，记录不修 RAG）· `ARPC-008`（`status = 0L` 等价性已订正） | ✅ |
| `users` | `users-common` | 【COMMON 型】全 3 条 | `mvn -pl service/users/users-common -am test` ⇒ `JwtUtilParityTest`（**Go 出处写进 javadoc**：`jwt.go:12`·`:14`）＋ `PasswordUtilGoCompatibilityTest`（`password.go:15-18`）＋ `GoStyleRequiredFieldGateTest` ＋ `bash tools/api-full-parity-probe/users-common/run.sh`（**双侧活体**） | **R4** ↑ | **E4** | `docs/DRIFT/users/common.md:556`（判定已升级 EQUIVALENT）· `tools/api-full-parity-probe/users-common/README.md`（落仓探针）· `:522-525`（3 条 UNRESOLVED）· `service/users/users-common/src/test/java/com/rag/users/common/jwt/JwtUtilParityTest.java:20-26` | **0 条必对齐项** · `UNRESOLVED-002`/`004`/`005`（**取证缺口**，任一取证即重开判定）· ✅ **双侧对拍探针已落仓**（**176 条「声明」→ 260 条「可复算」** · **218 条逐字节相同 / 25 条单侧专属 / 17 条已登记的两侧不同 / 0 失败** · 变异精确转红）⇒ 满足 §1.1 的 **R4 ⑦⑧**（**E4\* 消星**）· ✅ **§九 G-4 已闭合**（2026-10-29） | ✅ |
| `users` | `user-proto` | 【PROTO 型】①–⑤ | `bash tools/difftest/check-proto-static.sh`（user 段 ⇒ **逐字节比对字段名 / 字段号 / message 名 / rpc 签名**，本轮实跑 EXIT=0）＋ `python3 tools/difftest/check-surface-parity.py --domain user` | **R3** ↑ | E3 | `docs/DRIFT/_cross/proto-verification.md:24`（`user`→`users` **0 行**行为层 diff · 20=20 message+enum · 47=47 字段号）· `:44`（**「有网」档** ⇒ 改坏即红）· `docs/DRIFT/users/user/rpc.md:224` | **0 条行为差异** · ⚠️ **消费方式未验**（`optional` presence / 字段号硬编码 / Jackson）⇒ **取证缺口** ⇒ §九 G-19，**不计入 ⑥、不降 R** · ⚠️ **无专属台账**（记录在 `_cross/proto-verification.md`） | ✅ |
| `users` | `admin-proto` | 【PROTO 型】①–⑤ | `bash tools/difftest/check-proto-static.sh`（**admin 段** ⇒ 逐字节 diff，本轮实跑 EXIT=0）＋ `python3 tools/difftest/check-surface-parity.py --domain admin` | **R3** ↑ | E3 | `docs/DRIFT/_cross/proto-verification.md:19`（`admin` **0 行**行为层 diff · 28=28 message+enum · 61=61 字段号）· `:44`（**「有网」档**）· `docs/DRIFT/users/admin/rpc.md:51` | **0 条行为差异** · ⚠️ **消费方式未验** ⇒ **取证缺口** ⇒ §九 G-19，**不降 R** · ⚠️ **无专属台账** | ✅ |
| **共享层** | `service/common` | 【COMMON 型】全 3 条 | `bash tools/breaker-probe/run-parity.sh` ＋ `bash tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh` ＋ `mvn -pl service/common -am clean test`（**405 例 / 0F / 0E / 0S**） | **R3** ↑ | E3 | `docs/DRIFT/common/alignment-2026-10-23.md` §十二·一（`24 条编号 + 5 项未编号`）· **§十四**（R3 轮显式分类）· `docs/DRIFT/global/vs-common.md` §2（9 轴）· §4.18 | **28 项未修**（`DRIFT-CF` 15 ＋ `DRIFT-LOG` 7 ＋ `DRIFT-SF` 2 = **24 条编号** · 本轮修 1（`DRIFT-CF-018`）⇒ **23 条未修** ＋ **5 项未编号**）＋ 10 条外部尾巴；⚠️ **2026-10-29 R3 轮定级**：**A 类 0 · B 类 0 · C 类 21 · D 类 6 · 证据缺口 1** ⇒ **域内硬阻塞 = 0**（`DRIFT-COMMON` 族未修数 = **0**）<br>⚠️ **2026-10-29 计数订正（本轮实跑复核）**：原写「**24 条编号 + 5 项未编号**」⇒ **过期** ⇒ 共享层台账 `§十二·一` 的合计表**漏减 `DRIFT-CF-018`**（该条 2026-10-29 **已修**：`Dispatcher.java:90/136/139` 的 javadoc 明写「DRIFT-CF-018（2026-10-29 修）」＋ 守门 `DispatcherHandleLifecycleGateTest` 在位 ⇒ 台账 §〇 第 60 行亦已登记「新修 1 条」）⇒ ⇒ **未修编号 = 15(CF) − 1(CF-018) + 7(LOG) + 2(SF) = 23** ⇒ **23 编号 + 5 未编号 = 28 项**（与定级 **C 21 + D 6 + 证据缺口 1 = 28** 自洽）⇒ ⚠️ 台账 `§十二·一` 与 `§十三·一` 的 CF-018 行**仍是陈旧快照** ⇒ 已派单 G4 订正（`docs/DRIFT/common/**` 属 G4 写权面）⇒ 按 §1.1 ⑥ 第 4 条（C 类阶段二暂缓项）**不计入 R3 ⑥**（Jason 2026-10-29 裁定）⇒ ⇒ **R 档由 R2 升 R3**<br>⚠️ **仍非 ✅**：9 轴 = A1🟡 · A2✅ · A3🟡 · A4🟡 · A5🟡 · **A6🟡（🔴→🟡）** · A7/A8 N/A · A9✅ ⇒ 38 个 🟡 类的 javadoc 引用**未做内容锚定** ⇒ ⇒ **组件状态由 🔴 上调 🟡** | 🟡 |

> **图例**：`↑` = v3 上调，`↓` = v3 下调（逐条理由见 §十）。
> ⚠️ **组件状态与 R 不等价**：`R3` + 「仍开放项 = 取证缺口」⇒ **组件状态仍可判 ✅**（如 `like-api`）；`R3` + 「仍开放项 = 真实差异」⇒ **判 🟡/🔴**（如 `like-rpc`）。

---

## 四、矩阵 B：10 张域级终点指标卡

> **域级聚合规则（唯一出处，§8.1）**：域状态 = MIN(域内各组件状态)；
> 序：✅ 达标 > 🟡 契约达标/语义待补 > ⚫ 受阻 > 🔴 未达标。
> ⚠️ **跨组件残留**（落点在共享层/别域）**不计入组件 R**，但**计入域级聚合**。

| 域 | 组件数 | 组件状态明细 | **域状态** | 阻塞项（落点） |
|---|---|---|---|---|
| `article` | 4 | api ✅ · rpc ✅ · common ✅ · proto ✅ | **✅** | ✅ **`S-5` 改判「范围外」**（§1.1 **R0** = Go 侧无对应件 ⇒ `knowledge`/`points`/`security` 三域**不迁移** ⇒ **不是待办**）⇒ 原写「⛔ 阻塞」是**错的**<br>· ✅ **`U-4′` 销号**（双侧活体 3 场景逐字节 ＋ 两侧播种同一行 · L4）· ✅ **`U-3`/`U-4` 闭合**（真 Kafka · 行为断言）· 🟡 **`U-9b`**（**取证缺口**，非必对齐 ⇒ 不降 R）⇒ **本域阻塞项归零** |
| `comment` | 4 | api ✅ · rpc ✅ · common ✅ · proto ✅ | **✅** | `D8`/`D9`（**`service/common`** ⇒ 阻塞共享层，不阻塞本域） |
| `favorite` | 4 | api ✅ · rpc ✅ · common ✅ · proto ✅ | **✅** | `U9`（部署形态，待拍板）· `E65`（**`service/common`**） |
| `follow` | 4 | api ✅ · rpc ✅ · common ✅ · proto ✅ | **✅** | ✅ **`DRIFT-FOLLOW-006` 已关闭**（2026-10-29 · `573cf08f`）—— 5 个 `follow_rpc_*` 指标的**抓取出口落地**（观测口 16065，与 Go `DevServer.Port` 同口）＋ **顺带修一个真缺陷**（`_bucket` 边界与 Go `prometheus.DefBuckets` 零重合 ⇒ 已逐桶对齐）⇒ **本域阻塞项归零**<br>· `-008`（MaxConns 闸门粒度 ⇒ 落点 **`service/common`** ⇒ **跨组件残留 ⇒ 不计入组件 R、不阻塞本域**） |
| `hot` | 4 | api ✅ · rpc ✅ · common ✅ · proto ✅ | **✅** | `DRIFT-HOTAPI-031`（框架限制，候选未签字）· `DRIFT-HOTRPC-006`（已取证未签字）· **hot-proto 的判定记录在 `hot/vs-common.md` 而非 `hot/proto.md`**（位置不统一 ⇒ §九 G-18） |
| `like` | 4 | api ✅ · rpc 🟡 · common ✅ · proto ✅ | **🟡** | **`DRIFT-LIKE-003-C`**（暴露形态待拍板；**A/B 两段已闭环**）· ⚠️ **`E60` 已销案**（2026-10-29）· **like-common 无专属台账**（**记录缺口**，非行为缺口 ⇒ §九 G-17）· ⚠️ 域状态 **🟡 未重算**（Jason 裁定） |
| `message` | 4 | api ✅ · rpc ✅ · common ✅ · proto ✅ | **✅** | `-010`/`-011`/`-013`（**`service/common`**）· `MSG-008`/`MSG-V-001`（跨域） |
| `task` | 3 | api ✅ · rpc ✅ · proto ✅ | **✅** | `DRIFT-TASK-API-004` / `otel.*`（**全域共性**） |
| `users` | 7 | user-api ✅ · user-rpc ✅ · admin-api ✅ · admin-rpc ✅ · users-common ✅ · user-proto ✅ · admin-proto ✅ | **✅** | `DRIFT-USERAPI-013`（**`service/common`**）· `V-002`/`V-011`（**销号签字不在写权面**）· **两个 proto 的「消费方式」未取证**（§九 G-19） |
| **共享层** `service/common` | 1 | 🟡 | **🟡** | **28 项未修**（`DRIFT-CF` 15 ＋ `DRIFT-LOG` 7 ＋ `DRIFT-SF` 2 = **24 条编号** · 本轮修 `DRIFT-CF-018` ⇒ **23 条未修** ＋ **5 项未编号**）＋ 10 条外部尾巴；⚠️ **2026-10-29 R3 轮定级**：**A 类 0 · B 类 0 · C 类 21 · D 类 6 · 证据缺口 1** ⇒ **域内硬阻塞 = 0**（`DRIFT-COMMON` 族已归零）⇒ 按 §1.1 ⑥ 第 4 条 **C 类不计入**（Jason 裁定）⇒ ⇒ **不阻塞全仓**；⚠️ 仍非 ✅（9 轴里 A1/A3/A4/A5/A6 五格 🟡） |

---

## 五、矩阵 C：跨组件不变量（12 条）

> **跨组件不变量 = 不归属任何单个组件、但影响多个域的约束**（AGENTS.md §3.5 的 `global` 收口域）。
> ⇒ **本层不过 ⇒ 域层全绿也不算数。**
> ⚠️ **本层的状态一律以 `docs/DRIFT/global/` 的台账为准**（L2 权威位置）；下表只写**指标 + 判定命令 + 引用**。
> 🔴 **编号消歧（2026-10-29 必读）**：**本层的 `X-1`~`X-12` 与 `docs/DRIFT/global/cross-domain.md` 的 `X-n` 是两套不同编号** —— 本层的 `X-6` = **deadline 分层预算与传播**，而 global 档的旧 `X-6` = **`DRIFT-HOTAPI-030`（Tomcat 参数个数口径）** ⇒ **两套 10 个号里只有 `X-1` 同物，其余 9 个全错位** ⇒ ⇒ **global 档已于 2026-10-29 重编号为 `CD-1`~`CD-10`**（`CD` = Cross-Domain；旧号一律保留，见该档「编号重整」映射表）⇒ **本层继续用 `X-n`，global 档用 `CD-n`，引用必须带「文件 + 编号」**。

| # | 不变量 | 涉及组件 | 必达终点指标 | 判定命令（**覆盖范围见 §六**） | 状态（2026-10-28） |
|---|---|---|---|---|---|
| **X-1** | **共享 Flyway 账本隔离** | 7 个 rpc 模块 | 每模块独立账本表 `flyway_schema_history_<模块>`；**同库三进程并发**不撞号 | `python3 tools/difftest/check-flyway-ledger.py` ⇒ EXIT=0（**仅静态 yaml**）＋ `bash tools/difftest/check-flyway-concurrent.sh`（**真并发**） | ✅ **已闭环**（配置层 + 真 PG 三进程并发同库，L4）<br>⚠️ `favorite-rpc` 缺 `baseline-version: 0` ⇒ 只记账 |
| **X-2** | **DDL 双侧活体逐列** | 9 域（favorite 只建库、hot N/A） | 类型/长度 **0 差异**；可空性、DEFAULT、索引、唯一约束、触发器逐项一致或**已签字保留**；**活库不得落后于仓里的 `.sql`** | `python3 tools/difftest/check-ddl-static.py --domain <域> --indexes --check-source` | 🟡 **类型/长度 9 域 0 差异**；users 可空性 1 + DEFAULT 16 残留（**刻意保留**）· comment 域**整张 `report_record` 表（8 列）仅 RAG 有**（**已签字保留**）· hot **N/A**<br>✅ **2026-10-29 补齐「源码 ↔ 活库」维度**（`--check-source`）⇒ **9 域实测全部一致**（hot = N/A）⇒ 迁移改了没重跑**当场变红**（变异必红已实测）⚠️ **2026-10-29 判据升级**：`--indexes` 与 `--check-source` **两层均已逐域实跑**（9 域）⇒ **索引 / 唯一约束 / 触发器不再是 `NOT CHECKED`**；「仓里的 `.sql` ↔ 活库」**9 域全部一致**（`--check-source` 让 PG 自己在干净临时库原样重放）⇒ **迁移改了没重跑当场变红**（变异必红已实测）<br>⚠️ **残留 ⇒ 仍非「逐项一致」**（2026-10-29 · **9 域逐域机器核的权威数**）：**实质残留合计 51 处** —— users **16**（可空性 1 ＋ `DEFAULT` 8 ＋ 索引 7）· message **7**（`DEFAULT` 6 ＋ 索引 1）· like **5**（额外索引 2 ＋ 额外 `CHECK` 2 ＋ 额外不可变触发器 1）· comment **21**（可空性 8 ＋ `DEFAULT` 8 ＋ 额外索引 2 ＋ 额外 `CHECK` 2 ＋ 额外触发器 1）· favorite **2**（额外 `CHECK`）· **article / follow / task 各 0**（8 维度逐项一致）· hot **N/A**（Go 侧无对应建表 ⇒ **不是「0 差异」**）；另 comment **整张 `report_record` 表（8 列）仅 RAG 有** ⇒ **已签字** `SD-2026-10-24-CAND-02`<br>✅ **逐条定级（`docs/twoStage.md` §十）⇒ 落「C 类 23 / D 类 28 / A 类 0」三档** ⇒ 全部**不产生数据丢失·重复处理·状态错误** ⇒ 按 §1.1 ⑥ 第 4 条**不计入**阶段一判定（逐条列入**交付物 5** 暂缓项 ＋ 各域台账 §9）：users 可空性 1 = `CAND-17` **C 类** · users `DEFAULT` 8 = `CAND-16` **D 类** · users 索引 7（Go 独有 3 = **C 类** 真缺 ＋ RAG 独有 4 = **D 类** 冗余）· message 两条**全部已签**（`SD-2026-10-24-04`）⇒ **D 类** · like 索引 2 = **D 类** ＋ `CHECK` 2 ＋ 触发器 1 = **C 类** · comment `CAND-01` 16 处 = **C 类** ＋ `CAND-09` 5 处 = **D 类** · favorite 两条**已签**（`DEV-FAV-RPC-006` / `DEV-FAV-RPC-007`）⇒ **D 类** |
| **X-3** | **gRPC 拦截器真接线** | 10 个 rpc 模块 | 每个 rpc 模块的拦截器**真挂上 server** | `python3 tools/difftest/check-grpc-interceptor-wiring.py` ⇒ EXIT=0（**纯静态 grep**，不启动服务） | ✅ **10/10 接线完整**（静态层） |
| **X-4** | **api 容器上限三旋钮** | **全部 10 个 api** | 三个旋钮的**配置值**与 Go 目标值一致 | `python3 tools/difftest/check-tomcat-limits.py` ⇒ EXIT=0（**只读 `application.yml`，不验证运行时**） | ✅ **10/10 配置值一致**；运行时由 `HotApiRealTomcatGateTest` 3 条真 Tomcat 断言覆盖（引 hot 台账） |
| **X-5** | **产物可执行性** | 10 个 api + 10 个 rpc | 每个 fat jar **可执行**（含 `Main-Class`） | `bash tools/difftest/check-jar-executable.sh` ⇒ EXIT=0 | ✅ **2026-10-29（R4 轮）已复核 · 20/20** —— 全量 `mvn clean verify` 后重跑：**「已检查 20/20 个模块（未构建 0 个）」** ＋ 20 个 jar 全部带 `Main-Class: org.springframework.boot.loader.launch.JarLauncher`（64M ~ 99M fat jar）⇒ ⇒ **绿灯这次真的覆盖了全部 20 个模块**<br>🔴 **顺带修掉守门自身的一个假红 bug**：脚本最后一行写的是裸 `$total）` / `$checked/$total）` ⇒ **macOS 自带 bash 3.2 会把紧跟 `$VAR` 后的全角右括号算进变量名** ⇒ `set -u` ⇒ `total?: unbound variable` ⇒ **脚本在最后一行崩掉 ⇒ 明明 20/20 全过却 EXIT=1**（与 `tools/api-full-parity-probe/users/` README 记的同类坑同源）⇒ 修法 = 一律用 `${VAR}` 显式界定变量名边界；⇒ 并新增扫描器 `tools/difftest/scan-bash-legacy-vars.py`（**不是 `check-*` ⇒ 不计入 §6.3 的 27 个计数**）全仓扫这一类 |
| **X-6** | **deadline 分层预算与传播** | 10 个 rpc + 共享层 | 下游 deadline = `min(客户端 deadline, 服务端 timeout)`；SQL 取消时刻 == deadline 时刻 | `python3 tools/difftest/check-deadline-verdict.py`（**门禁自身有效性**，fixture + 假探针）＋ `check-deadline-downstream.py`（**真双侧活体**；⚠️ **【2026-10-30 第三次订正】已由「故意不等值 ⇒ 钉漂移形态」改为「**纯等值**」** —— 四个键全部 parity ⇒ 两侧必须相等）＋ `bash tools/difftest/run-deadline-gate.sh` | 🔴 **仍不满足（2026-10-30 第三次订正 · 理由已收窄）** —— ⚠️ **判据原文一字未改**（§3.3.2）⇒ 改的只是**当前状态**<br>**① 继承面 = ✅ 已闭合（双侧活体 · 逐键相等）**：**【2026-10-30】** 探针 RAG 半边改走 **devh 生产装配路径**（真 Spring 上下文 ＋ devh 自己的 `GrpcChannelFactory` ＋ `GlobalClientInterceptorRegistry`）⇒ ⇒ 实测 Go = RAG = `{'failed_fast': 'true', 'code': '4', 'outgoing_carrier': 'present_expired', 'sql_carrier': 'present_expired'}` ⇒ ⇒ **四键逐键相等** ⇒ ⇒ 「出向 gRPC 继承请求 deadline」**两侧同款** ⇒ ⇒ **台账 `E65` 本条已闭合**（`docs/DRIFT/global/_README.md` + `docs/DRIFT/global/vs-common.md`）⇒ ⇒ **不再是 A 类阻断**<br>**② 残余 = 🔴「客户端硬上界 2000ms 未启用」**（= **另一条轴** · P2-T04 · 域侧 yml ⇒ 与「是否继承请求预算」**不是同一件事**）⇒ ⇒ ⇒ 判据「下游 deadline = min(**客户端 deadline**, 服务端 timeout)」的**第一项**（Go 侧 zrpc client `Timeout` 2000ms）在 RAG 侧**缺项**（`capMillis` 默认 0 = 不启用）⇒ ⇒ ⇒ **判据仍不满足** ⇒ ⇒ ⇒ **X-6 仍 🔴**<br>**③ 连带**：§8.2 **C-3**（「12 条无 🔴」）**仍不成立**（破的是**这一项**，不是继承面）⇒ §8.3「通过」的必要条件之一仍被破 ⇒ **阶段一仍不能判「通过」**<br>**④ 历史保留（2026-10-29 复判）**：原判 🔴 的理由是「`outgoing_carrier` 两侧不等」＋「探针 PASS 是漂移形态门的口径反」⇒ ⇒ **那两格漂移已被证明是探针产物**（RAG 半边自搓裸 channel ⇒ 绕开 devh 装配）⇒ ⇒ **原判的理由已失效，但结论（🔴）因 ② 仍成立**<br>⚠️ **口径纪律（已随守门升级而变）**：本行的守门**现在**是**等值门** ⇒ **PASS ⇒ 两侧真的相等** ⇒ ⇒ **判绿 ⇒ 可以写「已对齐」（就继承面而言）**<br>**【2026-10-31 复判 · 合并收口执行者】** —— 继承面那一半**已固化且经独立复核**⇒ ⇒ **复判结论：X-6 维持 🔴**，唯一残余不变 = **客户端硬上界那一轴**（Go 的 `c.Timeout=2000ms`；RAG `capMillis=0` 默认未启用 ⇒ 归 **`P2-T04`（域侧 yml）**）⇒ ⇒ **阶段一 C-3 破不变** ⇒ ⇒ **不因本轮证据改判**（判据原文要求「下游 deadline = min(客户端 deadline, 服务端 timeout)」⇒ ⇒ 服务端 timeout 那一侧仍与 Go 不同 ⇒ ⇒ 继承面闭合 ≠ 整条判据闭合）。**证据三项**：① **已固化** —— 探针改造已提交进主干（`e796a4ed`，2026-10-30 22:19）⇒ ⇒ G4 lane 在主干工作区**独立跑同一脚本 ⇒ EXIT=0 / PASS · 四键逐键相等**⇒ ⇒ 证据不再是「未提交的工作区读数」，而是**在仓的那份**（且该提交后未再改动 ⇒ 逐字相同）；② **装配面被证实** —— 保留 stderr 重跑取得路径诊断行（devh `InProcessChannelFactory` ＋ 全局拦截器名单 ⇒ ⇒ 拦截器由 `@GrpcGlobalClientInterceptor` 收进 registry ⇒ 不是探针手工塞的）⇒ ⇒ **正控 4ms / 负控 1506ms 可区分** ⇒ ⇒ 快速失败可归因于装配面；③ **判据非空转** —— 变异集 `tools/difftest/mutate-deadline-downstream.py` 实跑 EXIT=0⇒ ⇒ M1（拆掉 `RpcInterceptor.onHalfClose` 的载体绑定）**红在 `failed_fast` + `outgoing_carrier`**、M2（把正式观测换成裸 channel）**红在 `failed_fast`** ⇒ ⇒ 两个变异**均被精确命中**（红键集合逐条相等）⇒ ⇒ **还原后 sha256 逐字节一致 ＋ 基线复跑仍 PASS**。⚠️ **传输层不对称（如实记，不写成「两侧完全同构」）**：探针的**下游 hop 走 in-process**（classpath 上无非 shaded 的 `io.grpc.netty`）⇒ ⇒ 而**上游 hop 仍是真 TCP**⇒ ⇒ 四个被测键测的是「载体存在且已过期 ⇒ 立即失败」的语义、**不依赖传输层** ⇒ ⇒ **结论不受这处降级影响**。⚠️ **变异脚本目前没有任何守门调用它**（只有 docstring 提到）⇒ ⇒ 「判据非空转」这个保证**尚未被自动守住**，且该脚本会**注入改动到生产码** ⇒ ⇒ 不宜在共享工作树里当常驻守门跑 ⇒ ⇒ **归属待定**。 |
| **X-7** | **错误码契约（跨域）** | 8 个 `<域>-common` + `users-common` | 码值 + 文案 + 未知码回落**逐字节一致**；业务码经 gRPC 可还原 | `GO_REPO=… bash tools/errmsg-probe/run-parity.sh`（**article**）＋ `bash tools/errmsg-probe/favorite/run-parity.sh`（**favorite**） | ✅ **2026-10-29 覆盖 9/9 域**（G-7 闭合）—— 9 域各有一份 `tools/errmsg-probe/<域>/run-parity.sh`（**双侧逐字节** · sha256 相同）+ `mutate-parity.sh`（**变异精确转红**）⇒ **白名单可审**（逐条豁免 + 理由）· **OBS 段**单列且「差异消失即 exit 2」⇒ 有牙齿<br>⚠️ **证据强度 = L1/L2**（双侧同时跑的**静态**对照）⇒ **不是双侧活体** ⇒ **不得据此宣称「已对齐」**<br>⚠️ **退出码口径已统一**（2026-10-29）：0 = 全部精确转红 = 通过 · 1 = 守门失效 · 2 = 环境问题 ⇒ 此前并存过**三套**约定 ⇒ 已统一并**逐域实跑复验**<br>⚠️ **task 域的特殊形状**：task **无 `-common`、无码值表** ⇒ 该域比的是 **gRPC 码 + description 文案**（4 层 43 条）⇒ Go 侧直调真源（用「永远失败的 gorm.ConnPool」+「必然连不上的 redis Dialer」注入受控文案 ⇒ 走真实代码路径） |
| **X-8** | **HTTP 路径 / 方法契约** | 全部 10 个 api | (method, path) 归一化后**集合相等**；JWT 保护集合相等 | **`python3 tools/difftest/check-surface-parity.py`**（本次新增落仓）⇒ **HTTP 端点 Go 72 ≡ RAG 72**；gRPC 方法 **Go 71 ≡ RAG 71** | 🟡 **8/10 域逐条相等**；`users`/`admin` 两域 = **已签字偏离**（前缀 + method）<br>⚠️ **JWT 保护面未自动比对**（RAG 侧写在拦截器内部名单，非 `addPathPatterns`） |
| **X-9** | **gRPC 方法覆盖** | 10 个 rpc | Go proto `rpc` 集合 ≡ RAG `*ImplBase` 覆写集合 | 同上 `check-surface-parity.py` | ✅ **10/10 域逐条覆盖 · Go 71 ≡ RAG 71** |
| **X-10** | **proto 契约 + 消费方式** | 10 个 proto | 行为层零差异 ＋ `optional` presence ＋ 无字段号硬编码 ＋ 消费点一致 ＋ wire 字节 | `bash tools/difftest/check-proto-static.sh`（**只覆盖 user/admin/task**）＋ `python3 tools/difftest/comment/check-proto-parity.py`（**只覆盖 comment**） | ✅ **2026-10-29 落仓静态守门覆盖 10/10 域**（G-6 闭合）—— `tools/difftest/check-proto-static.sh` 扩到 **10 域逐字节**（字段名 / 字段号 / message 名 / rpc 签名）+ **变异必红** ⇒ `follow` / `like` / `hot` **现在有静态守门**了<br>⚠️ **效力边界不变**：静态文本 diff **不覆盖**运行时 `optional` presence 语义 / 代码里的字段号硬编码 / Jackson 配置 ⇒ 那是 §九 **G-19**（取证缺口，**不降 R**）⇒ **proto 组件的 ⑤ 由逐字节 diff 满足**（§1.3 的分类型口径） |
| **X-11** | **敏感配置链** | 10 个 api + 2 个 rpc + compose | 密钥**无明文默认值**；缺变量 ⇒ **启动失败** | `docker compose config` ⇒ 渲染出非空 secret **且** 缺变量时启动失败 | 🟡 **2026-10-29 由 🔴 上调** —— ① **JWT 验签密钥链已闭合**：**10 个 api/rpc 模块全部**去掉 yaml 明文默认值 + `@PostConstruct` **fail-closed 守卫**（判据 = `null` / 空串 / 全空白 / **未解析占位符字面量** `Pattern ^\$\{[^}]*\}$`）⇒ **三条路都拒绝启动**，报错含**配置键 + 环境变量名** ② 🔴 **缺陷机理（比空密钥更危险）**：Spring Boot 4.0.8 的 `SpringConfigurationPropertySource` 返回**原始值**、`PropertySourcesPlaceholdersResolver` 用 `ignoreUnresolvablePlaceholders=true` ⇒ **缺变量时绑定的是字面量 `${USER_JWT_SECRET}` 而非 `null`** ⇒ 只判 `null‖isBlank()` 的守卫**挡不住** ⇒ **HMAC 密钥公开 ⇒ token 可伪造** ⇒ 守卫必须判**占位符字面量** ③ **守卫验证走真路径**（真 `SpringApplication` + 真 ConfigData `classpath:/application.yml`）⇒ 断言根因必须是守卫抛的 `IllegalStateException` ④ **变异必红**：4 个模块各 **4 条精确转红** ⑤ `check-no-prod-secrets.py` ⇒ **EXIT=0**（未豁免命中 **0**）· 回归 10 模块 **0F / 0E**<br>⚠️ **残留（已登记 · B 类 · 不阻断）**：**8 个 rpc 模块**的**部署凭据默认值** —— 实测 **10 处**（`${POSTGRES_PASSWORD:password}` **× 9** ＋ `${MINIO_SECRET_KEY:minioadmin}` **× 1**）：like-rpc 1 · comment-rpc 1 · follow-rpc 1 · message-rpc 1 · favorite-rpc 1 · admin-rpc 1 · task-rpc 1 · **article-rpc 3**（PG × 2 ＋ MinIO × 1）⇒ 已登记 **`S1-USERS-002`**（交付物 5 · 🟡 **B 类**）⇒ 按 §1.1 ⑥ 第 4 条**不计入**；⚠️ **归属订正**：落点在**各域自己的 rpc 模块**（域内 ⇒ 域侧可自修）⇒ **原写「归 G4 共享层」是错的**<br>⚠️ **本轮实测订正三处过期数字**（2026-10-29）：① 原写「**9 个 rpc 模块**」⇒ 实为 **8 个**（**user-rpc 无 `application.yml`（不直连 PG）· hot-rpc 无 `datasource`** ⇒ 这两个域本来就不在清单里）② 原写「`${*_AUTHORITY_TOKEN:}`」⇒ **全仓 0 命中**（该形态**不存在**）⇒ 删除 ③ 原写「已登记 `F-6`/`A-5`」⇒ 交付物 5 里**无这两个编号**，实为 **`S1-USERS-002`**<br>✅ **2026-10-29（R4 轮）硬阻断已消解**：判据「**空签名密钥可起栈**」**已不成立**（`@PostConstruct` fail-closed 守卫三路全拒 + **4 模块各 4 条变异精确转红** + `check-no-prod-secrets.py` EXIT=0）⇒ ⇒ **P0 #8 已 ✅** ⇒ ⇒ **本行保留 🟡 的唯一原因 = 残留的 B 类部署凭据默认值**（`S1-USERS-002`）⇒ 按 §1.1 ⑥ 第 4 条**不计入** ⇒ ⇒ **不再是阶段一阻断项** |
| **X-12** | **构建 / 配置 / 启动说明** | 根级 | README ≥ 1 屏且能照着构建启动；`.env.example` 存在；compose fail-closed | 人工 + `docker compose config` ＋ `./manage.sh up` ＋ `curl` 真打端点 | ✅ **2026-10-29 已闭合**（🟡 → ✅ · R4 轮）：README **21 B → 9,815 B**（照做说明 · 真实端点 `/user/v1/login` + 真实网络 `Sea-TryGo`）· `.env.example` **新建（56 变量）** · compose **`:?` fail-closed**（**实测** 缺变量 EXIT=1 / 设变量 EXIT=0）· `Infrastructure.md` 按现状订正（⚠️ **真实路径在仓库根**，非 `docs/FileTree/`）<br>✅ **2026-10-29（R4 轮）由 🟡 上调 ✅** —— **按 README 真起了一次栈**（不是「改了文档就算」）：① **`./manage.sh up` ⇒ EXIT=0** —— 7 个容器全起（postgres / redis / nacos **healthy** ＋ jaeger / prometheus / user-rpc / user-api）② **`POST /user/v1/register` ⇒ HTTP 200**（`{"code":200,"msg":"OK","data":{"uid":"2115724539190054912"}}`）③ **`POST /user/v1/login` ⇒ HTTP 200** + 真实签名 JWT（167 字符）④ 带 token 打受保护端点 `/user/v1/get` ⇒ **HTTP 200** + 字段回显；**不带 ⇒ HTTP 401**（`{"code":1006,"msg":"TOKEN已过期"}`）⑤ **数据真落库**（`psql -U sea -d sea_user` ⇒ 4 张表 + `users_user` 1 行）⇒ ⇒ **P0 #5 已 ✅**<br>⚠️ **真起栈暴露并修掉 compose 3 处「新机器必然起不来」的缺陷**（commit `0b707b39`）：① `dockerfile: Dockerfile` vs 仓内 `dockerfile`（小写）⇒ **Linux 必挂** ② `cache_from`/`cache_to`（`type=local,src=~/.m2/repository`）**三重不成立**且两 service **并发写同一 dest** ⇒ **build 整轮失败** ③ `postgres:latest` 已到 **18.6**，命名卷挂在旧的 `/var/lib/postgresql/data` ⇒ 官方 entrypoint **拒绝启动** ⇒ 改挂 `/var/lib/postgresql` |

---

## 六、判定命令的实测覆盖范围

> ⚠️ **本节是 v2 新增的核心（v3 保留）**：绿灯**只能证明命令自己检查的那一面**。
> 下表的「不能证明」列 ⇒ **任何矩阵行都不得用它推导更强的结论**。

### 6.1 真·双侧活体对拍（两侧同时运行并逐字节/逐字段比对）—— 可作运行面证据

| 命令 | 覆盖面 | case | 依据 |
|---|---|---|---|
| `tools/errmsg-probe/run-parity.sh` | **article 域错误码层**（码值+文案+回落） | 19 个未登记码 | `:93-104`（sha256 + `diff -u`） |
| `tools/errmsg-probe/favorite/run-parity.sh` | **favorite 域错误码层**（五段含 WIRE/BIZCODE） | 19 + 2 OBS | `:150-196` |
| `tools/semantic-probe/run-parity.sh` | **message 域解析语义**（判定 + 落值） | 23 | `:134-141` |
| `tools/breaker-probe/run-parity.sh` | **共享层熔断**（A 逐条 / B 逐字 / C 统计 ≤0.15） | 6 码 + 统计批 | `:91-124` |
| `tools/json-body-probe/run-parity.sh` | **hot 域 body 读取判据**（只比 ACCEPTED/REJECTED） | 20 | `:74-94` |
| `tools/difftest/diff.py` | **users 域 HTTP wire**（status + 归一化 body 逐字段） | SCENARIOS 表 | `:659-664`、`:710-712` |
| `tools/difftest/matrix.py` | **users 域 DTO 字段边界矩阵**（字段列表**从真源 `types.go` 现场解析**） | 5 桶分档 | `:32-35`、`:84-99` |
| `tools/difftest/comment/diff_comment.py` | **comment 域请求矩阵** | 23 步 | `:285-309` |
| `tools/difftest/run-hot-rpc-difftest.sh` | **hot-rpc gRPC 逐字节**（真 Kafka + 2×真 Redis） | 10 组 | `:178-186` |
| `tools/difftest/run-task-{http-parity,grpc-xlang,kafka-e2e,offset-oldest}.sh` | **task 域 HTTP / 跨语言 gRPC / Kafka 端到端 / offset 语义** | 44 步 / 7 / 批量 / 正负对照 | `:187-271` |
| `tools/difftest/run-message-ddl-parity.sh` + `check-ddl-static.py` | **DDL 双侧活体库逐列** | 逐列 | `:102-152` |
| `tools/api-{envelope,full-parity}-probe/favorite/run.sh` | **favorite 响应字节 + 全 HTTP 面** | 11 / 32 | `:111-134` / `:15-21` |
| `tools/{grpc-contract,proto-wire,ddl-parity,authority-live,sql-statement}-probe/favorite/run.sh` | **favorite gRPC 契约 / wire 字节 / DDL / 权威读 / SQL 语句** | 36 / 6 / 19 / 40 / 运行时 | 各 `run.sh` |
| `tools/difftest/check-deadline-downstream.py` | **真双侧活体**，但**故意不等值**（钉漂移形态） | 4 键 | `:66-119` |
| `service/favorite/favorite-rpc/src/test/java/…/proto/FavoriteProtoWireParityTest.java`（**JUnit，非脚本**） | **favorite proto 的 wire 字节双侧对拍**（Go 生成码 vs Java 生成码，**两个不同运行时**） | 6 组消息 | `FavoriteProtoWireParityTest.java:20-34`（javadoc 自述「两侧运行时不同 ⇒ 不是同一份实现自证」） |
| `tools/api-full-parity-probe/article/run.sh` + `mutate-parity.sh` | **article 域双侧活体**（Go 真源栈 + RAG 栈**同时起** ⇒ 同一请求比字节） | **3 场景**（`list_articles` / `get_article_exists` / `get_article_missing`）· **两侧播种同一行**（`status=2 PUBLISHED` · id `9007199254740993`） | `tools/api-full-parity-probe/article/README.md`（**主线独立复跑 EXIT=0**；M2/M3 精确转红 · M1 经主线手动复现亦 exit 1 精确转红） |
| `tools/api-full-parity-probe/like/run.sh` + `mutate-parity.sh` | **like 域双侧活体** | 阶段 A **9 case 逐字节** / 阶段 B DC **970 B sha256 相同** / 阶段 C **E60 双失败注入** | 同目录 README（EXIT=0；**3 变异精确转红**） |
| `tools/api-full-parity-probe/follow/run.sh` | **follow 域双侧活体** | **20 条「Go == RAG == 冻结期望值」三方逐字节** | `tools/api-full-parity-probe/follow/README.md`（M1~M5 全按预期 + **还原转绿**） |
| `tools/api-full-parity-probe/users-common/run.sh` + `mutate-parity.sh` | **users-common 活体对账** | **176 条「声明」→ 260 条「可复算」**；实跑 = **218 条逐字节相同 / 25 条单侧专属 / 17 条已登记的两侧不同 / 0 失败** | 同目录 README（**主线独立复跑 EXIT=0**） |
| `tools/api-full-parity-probe/favorite-rpc/run.sh` + `mutate-parity.sh` | **favorite-rpc 整域双侧活体**（在 **gRPC wire 层**抓原始字节 ⇒ 字段顺序漂移无法被客户端库洗掉） | **25 条逐字节一致**（FROZEN 25 + AMBIENT 1 只判 Go==RAG）· 7 条业务错误码与中文 description 逐字节一致 | `tools/api-full-parity-probe/favorite-rpc/README.md`（EXIT=0；M1/M2/M3 精确转红） |
| `tools/errmsg-probe/<域>/run-parity.sh`（**9 域**：article · favorite · hot · like · users · comment · follow · message · task） | **错误码契约双侧逐字节**（码值 + 文案 + 未知码回落） | article 49 行 · favorite 108 行 · like 114 行 · users 84 行 · … | 各域 README（**9/9 域 EXIT=0**；`mutate-parity.sh` 变异精确转红；**退出码口径已统一为 0=通过 / 1=守门失效 / 2=环境问题**） |
| `tools/proto-wire-probe/hot/run.sh` | **hot 域 proto 消息体 wire 字节双侧** | 6 组消息 · 两个运行时 | 同目录 README（EXIT=0；变异 3/3 精确转红） |

### 6.2 静态 / 单侧检查 —— **不能**当运行面证据

| 命令 | 实际查什么 | **不能证明** |
|---|---|---|
| `tools/difftest/check-surface-parity.py`（**本次新增**） | Go `routes.go` ↔ RAG Controller 的 (method, path) 集合；Go proto ↔ RAG `*ImplBase` 方法集合 | **不查**请求体/响应体/错误码/事务/并发；**不查** RAG 侧 JWT 保护面；**不查** proto 的字段号 / presence / wire 字节 |
| `service/{article/article-rpc,hot/hot-rpc}/src/test/java/…/*ProtoContractTest.java`（**JUnit，非脚本**） | **生成码的描述符** vs **由 Go 真源脚本生成并冻结的基准**（`generate.py` + 真源 sha256） | **不覆盖** wire 字节 / HTTP2 帧；**基准是冻结快照** ⇒ Go 真源改了**必须重跑 `generate.py`** 才反映（否则「绿」是旧基准的绿） |
| `tools/difftest/check-proto-static.sh` | **仅 user / admin / task** 三个 proto 的**静态文本** diff（字段号/message 名/rpc 签名） | **不查**运行时 `optional` presence 语义；**不查**代码里字段号硬编码；**不覆盖另外 7 个 proto** |
| `tools/difftest/comment/check-proto-parity.py` | **仅 comment** proto 的静态文本 diff | 脚本自写「**不校验消费方式**」（字段号硬编码 / `optional` presence / Jackson 配置） |
| `tools/difftest/check-ddl-static.py` | **双侧活体库**逐列 / 可空性 / DEFAULT（`--indexes` 才查索引/约束/触发器）· **`--check-source` 另核「仓里的 `.sql` ↔ 活库」** | **默认不开 `--indexes` ⇒ 索引/约束/触发器全未查**；⚠️ **源码↔活库那一维要显式加 `--check-source`**（不加就只比活库 ⇒ 「迁移改了没重跑」不可见）；触发器**只比结构化属性**，不比 SQL 文本、不判函数体语义 |
| `tools/difftest/check-jar-executable.sh` | 各模块 `target/*.jar` 的 `MANIFEST.MF` 里有没有 `Main-Class` | **不启动 jar**；**⚠️ 没有产物 ⇒ 跳过且不计失败** ⇒ 绿灯可能只覆盖少数模块 |
| `tools/difftest/check-grpc-interceptor-wiring.py` | rpc 模块的 `*Application.java` 源码里有没有 `@Import` + `.intercept(` | **不启动任何服务**；不验证拦截器真被框架调用 |
| `tools/difftest/check-flyway-ledger.py` | `application*.yml` 里 `spring.flyway.table` 的**配置值** | **不连库、不启动**；不验证 Flyway 真按该名建表 |
| `tools/difftest/check-deadline-verdict.py` | **门禁自身有效性**（fixture + 假探针 + bash lint） | **不产生任何业务对拍结论**（脚本自述「不需要起栈」） |
| `tools/difftest/check-go-json-body-predicate.py` | RAG 源码里该判据**只有一份实现** | **不验证**该判据与 Go 行为一致 |
| `tools/difftest/check-grpc-runtime-classpath.py` | `pom.xml` 的 `grpc-inprocess` scope ＋ `application.yml` 的 `address:` | **不验证运行时 classpath**；**baseline 内的违规只打印不失败** ⇒「绿 ≠ 全仓没缺陷，只是没有新缺陷」 |
| `tools/difftest/check-merge-tree-content.py` | git 对象层面的合并内容完整性 | 与业务语义无关；**需 ref 参数**；会向 `.git` 写 commit 对象 |
| `tools/difftest/check-no-nul-bytes.py` | 文件字节层的 NUL 扫描 | 与语义无关 |
| `tools/difftest/check-no-prod-secrets.py` | `docs/` + `service/` + `tools/` 里的**生产密钥明文**扫描（**脚本自身只存 SHA-256，不存明文** ⇒ 不是新的泄露面） | **只扫工作树、不扫 git 历史** ⇒ 不证明「密钥没进过 git」；白名单是**豁免不是免检**（每条附理由/owner/删法）；`service/users/users-common/**` **永不可豁免** |
| `tools/metrics-probe/favorite/run.sh` | **只跑 Go 半边**（脚本自述「本探针不是双侧对拍」） | **不是双侧证据** |
| `tools/authority-path-probe/favorite/run.sh` | Go 半边是**标准库复刻，不是真源** | **不是双侧证据** |
| `tools/prod-startup-probe/favorite/run.sh` | **同一个 jar 的三个参数形态** | **不是双侧证据**（单侧启动形态） |
| `tools/probe-gate/favorite/*.sh` | 探针**基建**正确性（PG 就绪 / 端口归属） | **不产生任何语义结论** |
| `tools/difftest/{run.sh,run-hot.sh,comment/run_diff.sh,run-task-*.sh,run-hot-rpc-difftest.sh,run-message-ddl-parity.sh}` | **编排器**（起栈 + 前置健康校验） | 自身不判定；**结论强度取决于被它调用的脚本** |

### 6.3 本轮实跑结果（**27 项命令** · 2026-10-29 · 18 行）

> ⚠️ **取退出码的口径**（本轮踩过坑，写在这里防止再犯）：**先落变量再打印** ——
> `out=$(cmd 2>&1); rc=$?` ⇒ **不许**用 `cmd | tail` 的退出码（那是 `tail` 的）。
> ⚠️ **「跑绿 ≠ 对」**：每个 EXIT=0 的门都配了**变异测试**（§九 G-19 / §十 #28），本轮另修了**两个守门自身的假绿/假红**（见下）。

| 命令 | EXIT | 备注 |
|---|---|---|
| `check-proto-static.sh` | **0** | **10 个域**逐字节对照（本轮从 3 域扩到 10 域 · G-6） |
| `check-jar-executable.sh` | **0** | ✅ **已检查 20/20 个模块**（未构建 0 个）⇒ 20 个 fat jar 全带 `Main-Class: JarLauncher`（**本轮修了门自身的假红**：裸 `$total）` 在 bash 3.2 上把全角括号算进变量名 ⇒ `set -u` ⇒ 脚本末行崩 ⇒ 明明全过却 EXIT=1） |
| `check-plaintext400-headers.sh` | **0** | **真 Tomcat 400 头**（`text/plain` 400 必须带 `Content-Type` ⇒ E-1） |
| `check-flyway-concurrent.sh` | **0** | **L4 真 PG 三进程并发同库** ⇒ 各域表都建出来、账本互不覆盖、baseline 落位正确 |
| `check-merge-tree-content.py`（带 4 棵 lane ref） | **0** | **逐步 merge-tree**（g1 ⊕ g2 ⊕ g3 ⊕ g4）⇒ 每一步「那侧的改动没丢」⇒ 问题 0 · 不可信 0 |
| `check-ddl-static.py`（裸跑 = users 域） | **1** | **9 处实质差异**（类型/长度 **0 差异**）⇒ 全部是 `DEFAULT CURRENT_TIMESTAMP` 残留（**刻意保留**）⇒ 按 §十定级 **A 类 0** |
| `check-ddl-static.py --domain comment --indexes --check-source` | **2** | **已签字偏离** `SD-2026-10-24-CAND-02`（`report_record` 整张表 8 列仅 RAG 有）⇒ **不是新漂移** |
| `check-ddl-static.py --domain hot --indexes --check-source` | **3** | **N/A**（hot 域两侧都无数据库 ⇒ 「本维度无对象可比」⇒ **不许写成「已对齐」**） |
| `check-ddl-static.py --domain {follow,task,article}` `--indexes --check-source` | **0** | 3 域 **8 维度逐项一致**（含索引/约束/触发器 + 源码↔活库） |
| `check-tomcat-limits.py` | **0** | 静态配置值（10/10 api 三旋钮一致） |
| `check-grpc-interceptor-wiring.py` | **0** | 静态（10/10 接线完整） |
| `check-flyway-ledger.py` | **0** | 静态 yaml（7 个模块独立账本表） |
| `check-go-json-body-predicate.py` | **0** | 静态 |
| `check-grpc-runtime-classpath.py` | **0** | 静态 + baseline |
| `check-no-nul-bytes.py` / `check-cross-domain-holds.py` | **0** | |
| `check-deadline-verdict.py` / `check-deadline-downstream.py` | **0** | 前者 fixture（门禁自身有效性），后者**真双侧** |
| `check-global-lane-ownership.py` / `check-wave-{coupling,lane-ownership,role-model}.py` | **0** | 写权与波次耦合门 |
| `check-ledger-{count,tables}.py` / `check-agents-budget.py` | **0** | |
| `check-ledger-selfcheck.py` | **1** | 🔴 **1 处不一致** = `docs/DRIFT/task/rpc.md` 的 **task-rpc 测试数 134 ⇒ 实测 137** ⇒ **task 域写权面** ⇒ 已派单 **R5-F9**（**本轮不开单 ⇒ 守门保持红** ⇒ **如实记账，不粉饰**） |
| `check-phasegoal-consistency.py` | **0** | **本文件自身的内部一致性自检**（15 组检查）—— 本轮**抓到本文件 §十·二 冻结表有一行列数不对**（裸竖线把行切成 4 列）⇒ 已修 |
| `check-phasegoal-lanes.py` | **0** | 由本文件生成/校验 per-lane 分摊矩阵（`docs/PhaseGoal/g{1..4}.md`）⇒ **本轮重新生成 4 份** |
| `check-no-prod-secrets.py` | **0** | 生产密钥明文扫描（`docs/` + `service/` + `tools/`）：**未豁免命中 0**；`--mutation-test` ⇒ **3/3 变异全部符合预期** |
| `tools/g3-anchor-check/check-anchors.py --all` | **0** | **OK=3208 · LINE_OOB=0 · FILE_MISSING=0 · VERSION_MISMATCH=0**（`AMBIGUOUS=1993` 是「同名多文件、机器判不了」⇒ 不计入失败）<br>🟢 **本轮修掉一个假阳性簇**：`kafka-go` 不在 `PINNED` 里 ⇒ 带版本前缀的路径解析不到 ⇒ 报 `[NO-SUCH-PATH]` ⇒ **手工回真源核实该引用是对的**（`kafka-go@v0.4.47/reader.go` 存在 · 171-185 行正是 `offsetStash.merge`）⇒ 已补进 `PINNED`（版本取真源 `go.mod:100`） |
| `tools/difftest/scan-bash-legacy-vars.py`（**新增扫描器 · 不计入 27**） | **1** | 全仓扫「裸 `$VAR` 后紧跟多字节字符」⇒ **本轮共抓到 2 处真 bug**：`check-jar-executable.sh`（已修）· `check-proto-static.sh`（已修）⇒ **剩 1 处属 hot 域写权面**（`hot-rpc` 探针收尾行）⇒ 已派单 **R5-F10** |

> ✅ **本轮：27 个守门全部实跑**（上一轮只执行了 24 个、另外 3 个当时被跳过 ⇒ 本轮**全部补执行且全绿**）：`check-flyway-concurrent.sh`（EXIT=0 · L4）· `check-plaintext400-headers.sh`（EXIT=0）· `check-merge-tree-content.py`（EXIT=0 · 带 4 棵 lane ref）。
> ⚠️ **仍有 1 个门是红的**（`check-ledger-selfcheck.py`）⇒ **原因明确**（task 域台账的测试数 134 vs 实测 137）⇒ **属 task 域写权面** ⇒ 按 §5.3f **不越界改** ⇒ **派单 R5-F9** ⇒ **如实记账，不粉饰成全绿**。
> ⚠️ **`check-ddl-static.py` 的 1 / 2 / 3 三个退出码不是「门坏了」** ⇒ 分别是「**刻意保留的 DEFAULT 残留**」「**已签字偏离**」「**N/A**」⇒ 三者**均已按 §十定级（A 类 0）** ⇒ **不阻阶段一**。
> ⚠️ **本轮（2026-10-29）未跑**：`check-datasource-password-form.py` —— **2026-10-30 才落**的「rpc 模块口令占位符形态」守门 ⇒ ⇒ **不属本轮** ⇒ ⇒ §6.3 的「27 项」仍按本轮口径；该门在 2026-10-30 已实跑（EXIT=0 · 内置变异自测 6/6）⇒ ⇒ 记在 [`docs/Worktree.md`](Worktree.md) 的 **M-8** 行。· `check-rpc-common-scan-exclusion.py` —— **2026-10-30 才落**的「rpc 域不扫 `com.rag.common` ＋ servlet 形态须声明 `rag.side`」跨域静态守门 ⇒ ⇒ **不属本轮** ⇒ ⇒ §6.3 的「27 项」仍按本轮口径；该门在 2026-10-30 已实跑（EXIT=0 · 内置变异自测 **5/5**，含 1 条**阴性对照**；⚠️ **同日第四次订正**：变异集已重锚 —— 原 M3 靠的是**已修掉的 pom 注释 bug** ⇒ ⇒ 修好后空转 ⇒ ⇒ 重锚为 M2/M3 并补 M5）⇒ ⇒ 记在 [`docs/Worktree.md`](Worktree.md) 的 **M-7** 行。· `check-mutation-anchor-liveness.py` —— **2026-10-31 才落**的「**变异脚本锚点存活**」只读守门（`tools/difftest/mutate-*.py` 那一族**变异脚本**改生产码/探针 ⇒ ⇒ 若旧串随生产代码演进而**命中 0 处** ⇒ ⇒ **变异空转** ⇒ ⇒ 「变异转红」这个证据**静默变空** ⇒ ⇒ 而脚本自己仍 EXIT=0 ⇒ ⇒ **没有任何机器信号**）⇒ ⇒ **不属本轮** ⇒ ⇒ §6.3 的「27 项」仍按本轮口径；该门在 2026-10-31 已实跑（EXIT=0 · 覆盖 4 脚本 / **50 条变异** · **已做变异测试**：故意打断一条锚点 ⇒ ⇒ 守门**精确红在那一条** ⇒ **鉴别力成立** ⇒ 还原后 sha256 一致）⇒ ⇒ **并查出 2 条此前是空的变异**（`mutate-deadline-verdict.py` M19 命中 2 处 · `mutate-hot.py` M10 命中 0 处 ⇒ ⇒ 已重定基）⇒ ⇒ 记在 [`docs/Worktree.md`](Worktree.md) 的 **M-10** 行。· `check-grpc-channel-target-scheme.py` —— **2026-10-31 才落**的「**测试源码手搓 gRPC channel 必须显式写 `dns:///` scheme**」跨域静态守门（devh 把 Nacos discovery resolver 注册进 grpc-java **进程级全局** `NameResolverRegistry` ⇒ ⇒ 无 scheme 的 target 被接管去 Nacos 查服务名 ⇒ ⇒ `UNAVAILABLE` ⇒ ⇒ 表现为「**单跑绿、同跑红**」）⇒ ⇒ **不属本轮** ⇒ ⇒ §6.3 的「27 项」仍按本轮口径；该门在 2026-10-31 已实跑（**EXIT=0** · 扫描 `service/**/*.java` **1330** 个文件 · 例外集 **9 个点位** · 内置变异自测 **5/5**：M1 已登记文件里新增**同文件不同形态**的调用点 ⇒ 红 `E1`（**点位级精度** · 证伪「文件级豁免 = 永久白地」）· M2 未登记文件新增调用点 ⇒ 红 `E1` · M3 同形字样进注释 ⇒ **不误报** · M4 同形字样进字符串字面量 ⇒ **不误报** · M5 例外失效检测 ⇒ 红 `E2`）⇒ ⇒ 记在 [`docs/Worktree.md`](Worktree.md) 的 **M-11** 行。· `check-source-test-decl-count.py` —— **2026-10-31 才落**的「**judge §二 A-1 的 `tests` 列 == 源声明测试方法数**（环境无关口径）逐行对账」只读守门 ⇒ ⇒ A-1 改了口径（surefire 执行条数 ⇒ 源声明方法数）⇒ ⇒ 必须有一道机器门盯住「文档数字与源码是否同步」⇒ ⇒ **不属本轮** ⇒ ⇒ §6.3 的「27 项」仍按本轮口径；该门在 2026-10-31 已实跑（**EXIT=0** · 39 行逐行一致 · 列求和 **3,226** == 实测源声明数 · 含 10 个 `proto` 行的 0 值对账）⇒ ⇒ 记在 [`docs/Worktree.md`](Worktree.md) 的 **M-12** 行。

---

## 七、P0 九条复核（逐条：指标 → 证据 → 命令 → 结论）

> 对照 `docs/PhasedGoal.md` §二 P0 与 `docs/twoStage.md` §2.1（**两者逐字相同**）。
> ⚠️ **v2/v3 的修订原则**：**「存在测试」≠「业务行为正确」**；**「证据不足」必须显式标出，不得用邻近证据顶替**。

| P0 | 内容 | 支撑证据（**逐条**） | 判定命令 | **v3 结论** |
|---|---|---|---|---|
| **1** | 核心业务链路能够真实运行，不存在关键功能仅有接口骨架、模拟数据或硬编码成功结果 | ① 契约面 **HTTP 72 ≡ 72 + gRPC 71 ≡ 71**（10 域）② 全量回归 **3294 条 / 0F / 0E / 4S**（2026-10-29 真跑 · 逐模块条数见 §十二）③ **核心 6 域全部有「仓内可复算」的双侧活体**（favorite 10 探针 / comment 23 步 / message / task 44 步 / users **39 步**（HTTP 层落仓探针 `tools/api-full-parity-probe/users/` · EXIT=0 · 变异 6/6 精确转红）＋ **43 场景**（gRPC 层 `tools/difftest/exp-difftest-grpc-user9.sh`）/ hot 29 例 / **article 3 场景 + 两侧播种同一行** / **like 阶段 A 9 case + 阶段 B 970 B sha256** / **follow 20 条三方逐字节** / **favorite-rpc 25 案 wire 层逐字节**）④ **`like` 域事实投递链路**（`DRIFT-LIKE-003`）**A 段已闭环**（事实投递链路）＋ **B 段已闭环**（权威读语义 Java 化）⇒ **仅剩 C 段（暴露形态）= C 类暂缓**；`E60`/`E59` **已销案** ⑤ 10 个域有真 PG / 真容器守门 | `python3 tools/difftest/check-surface-parity.py` ＋ `mvn -o -B -DargLine=-Xmx2g clean test` ＋ 各域双侧探针（§六 6.1） | ✅ **满足**（2026-10-29 上调）<br>⚠️ **上调依据 = 三处「证据不可复算」逐条消除**（**不是「为了让矩阵变绿」**）：① `like` 域 6 条 SEMANTIC MISMATCH 的复算证据**已落仓可复算**（§九 G-2 闭合 · EXIT=0 + 3 变异精确转红）② `article` 域从「双侧活体 **0** 条」⇒ **3 场景 + 两侧播种同一行**（§九 G-3 闭合 · **主线独立复跑 EXIT=0**）③ `like` 事实投递链路 **A/B 两段已闭环** ⇒ ⇒ **「不存在骨架/模拟数据/硬编码成功」成立**，且**「核心链路已取到双侧活体证据」也成立**<br>⚠️ **口径（不许含糊）**：`003-C` 属 **C 类**（暴露形态）⇒ 按 §1.1 ⑥ 第 4 条**不计入** ⇒ 但**不得据此宣称 like 域「零差异」**；另 `U-01` 的 **DC → 第三仓** 一段属**仓外取证缺口**（`Sea-BreakTheWaves` 需 pinned checkout + 40-hex SHA ⇒ 不在本机）⇒ 已登记、**不计入** |
| **2** | 核心参数校验、业务规则、身份认证和必要的权限控制有效 | ① **参数校验**：users 域字段级笛卡尔矩阵（字段列表**从真源 `types.go` 现场解析**）＋ `semantic-probe`（message 解析语义）＋ `json-body-probe`（hot body 判据）＋ `comment` 23 步矩阵 ＋ `favorite` 32 例 ② **身份认证**：`JwtVerificationScopeTest`（users / admin / comment / favorite / like）＋ `CommentJwtTokenMatrixGateTest`（11 例）③ **业务规则**：**失败路径矩阵 0 域无缺口**（**227 条** · 真 Tomcat / 真 gRPC server · 见 P0 #4）④ **权限控制**：**「必要的」权限控制 = 实际有权限概念的域** ⇒ 显式用例**已落地**：**403 / `PERMISSION_DENIED`** —— article **7** 条 · hot-api **2** 条 · task **1** 条；**401 / `UNAUTHENTICATED`** —— follow **20** 条 · article **17** 条 · task **7** 条 · hot-api **2** 条；**admin 域**（唯一的 operator 权限域）—— `AdminApiMvcPipelineTest` **16** 处 401 断言 ＋ `AdminApiJwtVerificationScopeTest` **6** 处（operatorId 严格校验形态已签字 `DRIFT-ADMINAPI-001`）⇒ **T-2.11 裁定**：**其余域无「权限」概念 ⇒ N/A**（是「不适用」，**不是「缺失」**） | 同上 ＋ 各域台账条目级状态 ＋ 各域失败路径矩阵模块级 `mvn test` | ✅ **满足**（2026-10-29 上调）<br>⚠️ **上调依据 = 两条待补项逐条有结论**：① **4 个域的失败路径矩阵已补齐**（⇒ 0 域无缺口）② **权限控制的显式用例已落地**（403/401 断言**逐域可数**）⇒ **不是靠 N/A 掉的**<br>⚠️ **证据强度分层（不许混写）**：失败路径矩阵 = **L3**（**只起 RAG 单侧**）⇒ 证明的是「**失败路径有明确行为**」；**不证明**「与 Go 逐字节一致」⇒ 后者由各域**双侧活体**探针承担 |
| **3** | 关键数据能够正确持久化，事务边界合理，无严重数据丢失/重复处理/状态错误 | ① **DDL 双侧活体逐列**：9 域**类型/长度 0 差异**；**`--indexes` 9 域逐域实跑**（2026-10-29 · T-3.1）⇒ 索引/唯一约束/触发器**不再是 `NOT CHECKED`** ② **源码 ↔ 活库**：**9 域实测全部一致**（`--check-source` · 仓里的 `.sql` 在干净临时库原样重放；**变异必红**已实测 ⇒「迁移改了没人重跑」当场变红）③ **事务边界**：真 PG 事务守门（`ArticleTransactionWiringTest` / `CommentRpcGoSemanticsGateTest` / `MessageTransactionWiringTest` / `AdminRpcTransactionWiringTest` / `AvatarTransactionWiringTest`）④ DDL 真源对拍（`run-message-ddl-parity.sh` 等） | `python3 tools/difftest/check-ddl-static.py --domain <域> --indexes --check-source` | ✅ **满足**（2026-10-29 上调）<br>⚠️ **上调依据 = 两处「证据缺口」已闭合**（不是「为了让矩阵变绿」）：① `--indexes` 未开 ⇒ 索引/约束/触发器**全未查** → 9 域逐域实跑完毕 ② 只比活库不比 `.sql` ⇒ 迁移改了没重跑不可见 → **`--check-source` 已补**（G-9）<br>⚠️ **残留项（9 域实测 · 已登记 · 全部为「RAG 更严/更宽松」的加强型 ⇒ 不产生数据丢失·重复处理·状态错误 ⇒ 不阻断）**：**实质残留合计 51 处** —— users **16**（可空性 1 ＋ `DEFAULT` 8 ＋ 索引 7）· message **7**（`DEFAULT` 6 ＋ 索引 1）· like **5**（额外索引 2 ＋ 额外 `CHECK` 2 ＋ 额外不可变触发器 1）· comment **21**（可空性 8 ＋ `DEFAULT` 8 ＋ 额外索引 2 ＋ 额外 `CHECK` 2 ＋ 额外触发器 1）· favorite **2**（额外 `CHECK`）· **article / follow / task 各 0**（8 维度逐项一致）· hot **N/A**（**不是「0 差异」**）；另 comment 整张 `report_record`（8 列 ⇒ **已签字** `SD-2026-10-24-CAND-02`）⇒ **按 §十 定级 = C 类 23 / D 类 28 / A 类 0** ⇒ 全部列入**交付物 5** 的暂缓项（按 §1.1 ⑥ 第 4 条不计入） |
| **4** | 核心失败路径具有明确行为，不无声吞掉关键异常或返回误导性成功 | ① **失败路径矩阵 0 域无缺口**（**227 条** · 真 Tomcat / 真 gRPC server）：follow **62** · article **104**（7 端点 × 8 类失败维度）· hot **28**（api 20 ＋ rpc 8）· task **33** ⇒ **每个端点 ≥1 条断言到错误码/状态码的失败路径** ② **变异 13/13 精确转红**（article 6 ＋ hot-api 5 ＋ task 2）⇒ **矩阵非假绿** ③ **错误码契约 9/9 域双侧逐字节**（`tools/errmsg-probe/<域>/run-parity.sh`）+ **OBS 段单列**（平台/库措辞差异 ⇒「差异消失即 exit 2」⇒ 有牙齿）④ **判据的自相矛盾已消除**（T-0.4）：原判据一边写「探针只覆盖 5 个域」、一边又写「`article` 域**没有**失败路径探针」⇒ **两条互斥** ⇒ 现按**实跑清单**改写 | `bash tools/semantic-probe/run-parity.sh` ＋ `bash tools/json-body-probe/run-parity.sh` ＋ `bash tools/errmsg-probe/<域>/run-parity.sh` ＋ 各域失败路径矩阵模块级 `mvn test` | ✅ **满足**（2026-10-29 上调）<br>⚠️ **上调依据 = 覆盖清单按实跑重写 + 0 域无矩阵 + 13/13 变异转红**（**不是「为了让矩阵变绿」**）<br>⚠️ **证据强度分层（不混写）**：失败路径矩阵 = **L3**（**只起 RAG 单侧** ⇒ **不得据此宣称「与 Go 逐字节一致」**）· 错误码 9/9 域 = **L1/L2**（双侧同时跑的**静态**对照）⇒ **两者都不是双侧活体** |
| **5** | 项目能够按照实际项目说明完成构建、配置和启动 | ✅ **2026-10-29 已落地**（R1·T-1.1~T-1.4 · commit `0bcd353`）：① **README 由 21 字节重写为 9,815 字节**的照做说明（真实端点 `/user/v1/login` ＋ 真实外部网络 `Sea-TryGo`）② **`.env.example` 新建**（**56 个变量**逐个列出）③ **`docker-compose.yaml` 补 `:?`** ⇒ **缺变量 ⇒ compose 直接拒绝渲染**（**实测** 缺变量 EXIT=1 / 设变量 EXIT=0）④ **`Infrastructure.md` 按现状订正**（⚠️ **真实路径在仓库根**，`docs/FileTree/Infrastructure.md` **从来不存在**） | 人工 + `docker compose config` ＋ `./manage.sh up` ＋ `curl` 真打端点 | 🟡 **部分满足**（2026-10-29 复判 · **原写「✅ 满足」是证据不足被当成满足**）<br>⚠️ **判据本身满足，但证据广度只覆盖 1/10 域** —— **实测（本轮）**：`docker-compose.yaml` 只有 **7 个 service**（postgres / redis / nacos / jaeger / prometheus ＋ **user-rpc / user-api**），`manage.sh:18` `APP_SVCS="user-rpc user-api"` ⇒ **「按 README 真起栈」这条证据链只证明 users 域的 api + rpc** ⇒ **其余 9 域的 api + rpc 未在「按 README 起栈」的形态下被实测过**（它们各自的**双侧活体**证据在别处已具备）<br>⇒ **按 §七 的 v2/v3 修订原则**：「**「存在测试」≠「业务行为正确」**；**「证据不足」必须显式标出，不得用邻近证据顶替**」⇒ 本条由 ✅ **改判为 🟡**（**性质 = 证据覆盖缺口，不是产品缺陷** ⇒ 补齐 9 域的起栈形态证据即可闭合，**不需要改产品代码**）<br>⚠️ **连带**：§8.2 **C-4** 行的「九条全绿」**不再成立**（实为 ✅ 8 / 🟡 1 / 🔴 0）⇒ §8.3「通过」的必要条件「**P0 九条全满足**」**不成立**<br>⚠️ **原下述依据（README / `.env.example` / compose `:?` / `Infrastructure.md`）全部仍成立且仍有效** —— **本条改的是「证据覆盖面」，不是「已做的整改」**<br>⚠️ **v7 上调依据 = 真起了一次栈并把端点打穿**（**不是「改了文档就算」**）：① **`./manage.sh up` ⇒ EXIT=0** —— 7 个容器全起（Sea-postgres / redis / nacos **healthy** ＋ jaeger / prometheus / user-rpc / user-api）② **`POST /user/v1/register` ⇒ HTTP 200**（`{"code":200,"msg":"OK","data":{"uid":"2115724539190054912"}}`）③ **`POST /user/v1/login` ⇒ HTTP 200** + 真实签名 JWT（167 字符）④ 带 token 打受保护端点 `/user/v1/get` ⇒ **HTTP 200** + 用户字段回显；**不带 token ⇒ HTTP 401**（`{"code":1006,"msg":"TOKEN已过期"}` ⇒ 错误码是 Go 契约里的那条）⑤ **数据真落库**：`psql -U sea -d sea_user` ⇒ **4 张表**（`users_user` / `users_user_avatar_history` / `admin_admin` / `admin_invite`）+ `users_user` 里 **1 行**（uid `2115724539190054912`）⇒ ⇒ **api → gRPC → rpc → PG 全链路真跑**<br>⚠️ **真起栈暴露并修掉 3 处 compose 缺陷**（commit `0b707b39`）—— 三处都是**新机器必然踩**的 ⇒ 不修则「第三方照 README 起栈」根本不成立：① **`dockerfile: Dockerfile` 与仓内文件名 `dockerfile`（全小写）不一致** ⇒ macOS 大小写不敏感能过、**Linux 必挂**（`dockerfile does not exist`）② **`cache_from`/`cache_to: type=local,src=~/.m2/repository` 三重不成立**（Dockerfile 内**无 maven 层**可缓存 · buildx local cache 布局要 `index.json` 而 maven 仓没有 · **`~` 不展开** ⇒ 实落仓内 `<cwd>/~/.m2/`）⇒ 且 user-rpc 与 user-api **两个 exporter 并发写同一 dest** ⇒ 互删 ingest 目录 ⇒ `rename tmp file … no such file or directory` ⇒ **build 整轮失败** ③ **PG18 挂载点**：`postgres:latest` 已解析到 **PostgreSQL 18.6**，其数据目录改为「按大版本分子目录」且镜像自带 `VOLUME /var/lib/postgresql` ⇒ 命名卷挂在旧的 `/var/lib/postgresql/data` ⇒ 官方 entrypoint **拒绝启动** ⇒ **新机器必然起不来** ⇒ 改挂 `/var/lib/postgresql`（新旧大版本都兼容） |
| **6** | 核心业务具有可执行的自动化测试，且测试断言真实业务结果 | **3294 条 / 0F / 0E / 4S**（52 模块，28:48 min · 2026-10-29 真跑） ⚠️ **【2026-10-31 订正 · 合并收口执行者】这是**运行期**读数（surefire 执行条数）⇒ ⇒ 与 §二 A-1 的 `tests` 列（**源声明方法数** = **3,226**）**两个口径、不可相减** ⇒ ⇒ 「覆盖面」看 A-1 · 「执行结果」看本处**；业务结果断言实例：`MessageApiMvcPipelineTest` 42 条 / `CommentApiContractTest` / `favorite` 4 个契约测试类 29 例 / `HotApiMvcPipelineTest` 等 | `mvn -o -B -DargLine=-Xmx2g clean test` ＋ surefire XML 求和 | ✅ **满足**（不变）<br>⚠️ **口径**：必须带 `clean`（否则 stale 产物同时伪造「红」与「条数」）<br>⚠️ **4S 的构成已定位（2026-10-29 从 surefire XML 反查）**：4 条 skipped **全部是 favorite 域的「双侧对拍」JUnit**（`FavoriteSqlStatementParityTest#sqlStatementParity` · `FavoriteCreateSuccessParityTest#createSuccessParity` · `FavoriteGrpcContractParityTest#grpcContractParity` · `FavoriteApiFullParityTest#apiFullParity`）⇒ 缺 `PROBE_GO_TARGET` / `PROBE_GO_TARGET_SUCCESS` / `PROBE_GO_API` 环境变量 ⇒ JUnit `Assumptions` 跳过 ⇒ 裸 `mvn test` 下**必跳** ⇒ **由 `tools/sql-statement-probe/favorite/run.sh:305` · `tools/grpc-contract-probe/favorite/run.sh:351`/`:392` · `tools/api-full-parity-probe/favorite/run.sh:425` 在带 Go 侧环境变量时单独驱动** ⇒ **不是漏跑、不是漏写**；⚠️ **但「0F/0E/4S」不得被读成「全绿」**（这 4 条在标准回归里**一次都不跑**）⇒ 逐条登记见 `docs/DRIFT/_INDEX.md`「搬移账目」表 |
| **7** | 项目具有清晰的主要模块职责，能够追踪核心调用链 | A-1 的 Go 原件 ↔ RAG 实现两列；10 域各有一份 `vs-common.md`（A1~A9 机制面轴） | `docs/DRIFT/_cross/component-coverage-matrix.md` | ✅ **满足**（不变） |
| **8** | 关键配置和敏感信息没有明显的不安全处理方式 | ✅ **2026-10-29 已闭合**（R1 + R3 两批）—— ① **JWT 签名密钥链**：**10 个 api/rpc 模块全部**去掉 yaml 明文默认值 + `@PostConstruct` **fail-closed 守卫**（判据 = `null` / 空串 / 全空白 / **未解析占位符字面量** `Pattern ^\$\{[^}]*\}$`）⇒ **三条路都拒绝启动**；报错含**配置键 + 环境变量名**<br>② **🔴 缺陷机理（比空密钥更危险）**：Spring Boot 4.0.8 的 `SpringConfigurationPropertySource` 返回**原始值**、`PropertySourcesPlaceholdersResolver` 用 `ignoreUnresolvablePlaceholders=true` ⇒ **缺变量时绑定的是字面量 `${USER_JWT_SECRET}` 而非 `null`** ⇒ 只判 `null‖isBlank()` 的守卫**挡不住** ⇒ **HMAC 密钥公开 ⇒ token 可伪造**<br>③ **守卫验证走真路径**：真 `SpringApplication` + 真 ConfigData（`spring.config.location=classpath:/application.yml`）⇒ 断言根因必须是守卫抛的 `IllegalStateException`（不是任意异常）<br>④ **变异必红**：4 个模块各 **4 条精确转红**（去掉占位符判据 ⇒ 只打掉「占位符」这一条，对照组仍绿）⇒ 非假绿<br>⑤ **部署面**：compose **6 处 `:?`**（缺变量 ⇒ 拒绝渲染，实测 EXIT=1）＋ `.env.example`（**56 变量**）＋ README（**9,815 B**）<br>⑥ `check-no-prod-secrets.py` ⇒ **EXIT=0**（未豁免命中 **0** · 已豁免 101 处逐条附理由/owner/删法）<br>⑦ **回归**：users 4 模块 **584** · message-api/follow-api **114/143** · like-api **104** · article-api **216** · comment-api **62** · favorite-api **154** ⇒ **全 0F / 0E**<br>⚠️ **残留（已登记 · B 类 · 不阻断）**：**8 个 rpc 模块**的**部署凭据默认值** —— 实测 **10 处**（`${POSTGRES_PASSWORD:password}` **× 9** ＋ `${MINIO_SECRET_KEY:minioadmin}` **× 1**；like/comment/follow/message/favorite/admin/task 各 1 · **article-rpc 3**）⇒ 已登记 **`S1-USERS-002`**（🟡 **B 类**）⇒ 按 §1.1 ⑥ 第 4 条不计入；⚠️ **归属订正**：落点在**各域自己的 rpc 模块**（域内 ⇒ 域侧可自修）⇒ **原写「归 G4 共享层」是错的**<br>⚠️ **本轮实测订正三处过期数字**（2026-10-29）：① 「**9 个 rpc 模块**」⇒ 实为 **8 个**（**user-rpc 无 `application.yml`（不直连 PG）· hot-rpc 无 `datasource`** ⇒ 本就不在清单）② 「`${*_AUTHORITY_TOKEN:}`」⇒ 全仓 **0 命中**（该形态**不存在**）⇒ 删除 ③ 「已登记 `F-6`/`A-5`」⇒ 交付物 5 **无此编号**，实为 **`S1-USERS-002`**<br>⚠️ **一条误报已排除**：某 lane 报「根级 compose 缺 `:?` / 无 `.env.example`」⇒ **其工作树里两样都在**（compose 6 处 `:?` · `.env.example` 6,626 B）⇒ **不构成欠账** | `docker compose config` + 空变量起栈实测 + 各模块 `mvn test` + 变异脚本 | ✅ **满足**（2026-10-29 由「不满足」上调）<br>⚠️ **上调依据 = 四项实跑**（守卫真路径断言 · 4×4 变异精确转红 · 10 模块回归 0F/0E · 密钥门 EXIT=0）⇒ **不是「改了代码就算」** |
| **9** | 已知严重缺陷、无法验证的关键能力和运行前提得到明确记录 | 38 份组件台账 + `docs/Intentional_Deviations/`（**60 条已签字 + 12 条未签字候选** · ⚠️ **2026-10-29 机器复算订正**：原写 48 / 18 ⇒ 现值 60 / 12）+ 本文件 §九 / §十一 | `python3 tools/check-ledger-tables.py` ⇒ EXIT=0 | ✅ **满足**（不变）<br>⚠️ **但**：共享索引 `docs/DRIFT/_INDEX.md` 与 `component-coverage-matrix.md` 对 `favorite` 域仍写「零台账 / 从未对照」，**已被 10-25 后的实测推翻** ⇒ 需更新（§九 G-11） |

> **P0 汇总（v8 · 2026-10-29 复判）**：**✅ 8 条**（#1 #2 #3 #4 #6 #7 #8 #9）· **🟡 1 条**（**#5** · 证据覆盖缺口 · 判据本身满足、整改已完成）· **🔴 0 条**· **⚫ 0 条** ⇒ 合计 **9 条**。
> ⚠️ **v8 与 v7 的差**：**#5 由 ✅ 改判 🟡** ⇒ ⇒ **「九条全满足」不成立** ⇒ §8.3「通过」的必要条件之一被破（详见 §八 C-4 与 §8.4 第 ② 条）。
> **v6 ⇒ v7 本轮上调 1 条**：**#5**（**按 README 真起一次栈**）—— `./manage.sh up` **EXIT=0** ＋ `register`/`login` 两个端点真打穿（HTTP 200 ＋ 真实签名 JWT）＋ 带/不带 token 的鉴权分支（200 / 401）＋ `psql` 直查 4 张表 + 1 行数据 ⇒ ⇒ **「项目能按说明构建、配置、启动」有活体证据了** ⇒ ⇒ **P0 九条全绿**（⚠️ 顺带修掉 compose **3 处**「新机器必然起不来」的缺陷 ⇒ commit `0b707b39`）。
> **v5 ⇒ v6 本轮上调 3 条**：**#1**（核心链路的双侧活体证据不可复算 ⇒ 已逐条消除）· **#2**（4 域失败路径矩阵 + 权限控制显式用例 ⇒ 已补齐）· **#4**（失败路径探针的域覆盖清单**自相矛盾** ⇒ 已按实跑重写）⇒ **三条都是「证据/覆盖」类** ⇒ **上调依据是可复算的实跑记录与变异转红，不是「改文档」**。
> **v4 ⇒ v5 上调 1 条**：**#8**（密钥链）—— 10 个 api/rpc 模块的 JWT 验签密钥**全部**去掉明文默认值 + **fail-closed 守卫**（含**未解析占位符字面量**这一条 ⇒ 堵住「缺变量时绑到字面量 ⇒ HMAC 密钥公开 ⇒ token 可伪造」）+ **4 个模块各 4 条变异精确转红** + 10 模块回归 0F/0E + `check-no-prod-secrets.py` EXIT=0 ⇒ ⇒ **P0 已无 🔴**。
> **v3 是 ✅ 3 / 🟡 4 / 🔴 2** ⇒ 自 v3 起共**上调 6 条**（**#1 / #2 / #3 / #4 / #5 / #8**）⇒ ⇒ **九条现已全绿（✅ 9 / 🟡 0 / 🔴 0 / ⚫ 0）**。
> ✅ **P0 九条已无 🟡 / 🔴**：上一版唯一的 🟡 是 **#5**（**按 README 真起一次栈**）⇒ 2026-10-29 实跑闭合（`./manage.sh up` EXIT=0 ＋ 端点真打穿）⇒ ⇒ **P0 全绿**。
> ⚠️ **口径提醒（三条 🟡→✅ 的证据强度分层，不许混写）**：#1 的双侧活体 = **L4**（两侧同时运行）· #2/#4 的失败路径矩阵 = **L3**（**只起 RAG 单侧**）⇒ 证明「失败路径有明确行为」，**不证明**「与 Go 逐字节一致」· #4 的错误码 9/9 域 = **L1/L2**（双侧**静态**对照）⇒ **三者不是同一档证据**。
> ⚠️ **v2 草稿此处写的是「✅ 4 条（#6 #7 #9 …）」且「下调了 3 条」—— 两处都算错**（列了 3 条却写 4 条；列了 4 项却写 3 条）⇒ 由 `check-phasegoal-consistency.py` 的 **P0 汇总** 检查抓出并订正。⇒ **该门禁继续生效**：本次汇总改动同样由它核对（`EXIT=0` 才算数）。

---

## 八、聚合判定规则与阶段一结论

### 8.1 组件 → 域（唯一聚合口径）

```
组件状态（§1.3 判据）：
  ✅ 达标   ⟸ R ≥ R3 ∧ 归属本组件的必相对齐项 = 0 ∧ 有可复算的判定命令
  🟡 待补   ⟸ R = R2
  🔴 未达标 ⟸ R 未评级（从未对照）∨ 归属本组件的必相对齐项 ≠ 0
  ⚫ 受阻   ⟸ 环境缺前置条件
  ⬜ 豁免   ⟸ 有明确理由的不适用

域状态 = MIN(域内各组件状态)，序：✅ > 🟡 > ⚫ > 🔴
跨组件残留（落点在共享层/别域）不计入组件 R，但计入域级聚合。
```

### 8.2 域 → 全仓

| 条件 | 判据 | 当前 |
|---|---|---|
| **C-1 核心域达标** | 9 个微服务域中**核心 6 域**（`favorite` `comment` `message` `task` `article` `users`）**全部 ✅ 达标** | ✅ **6/6**（favorite ✅ · comment ✅ · message ✅ · task ✅ · **article ✅** · users ✅）⇒ **S1-2 成立**<br>⚠️ **article 转 ✅ 的依据**（2026-10-29 · `a76ebfe2`）：`article-rpc` **R2 → R3** ⇒ ① **`S-5` 改判「范围外」**（§1.1 **R0** = Go 侧无对应件 ⇒ `security` 三域**不迁移** ⇒ 原写「⛔ 阻塞」是**错的**、实为**永不闭环**）② **`U-4′` 销号**（双侧活体 **3 场景逐字节** ＋ **两侧播种同一行** · **L4**）③ **`U-3`/`U-4` 闭合**（**真 Kafka** · **行为断言**）④ 归属本组件的**必相对齐项 = 0**（🟡 `U-9b` 是**取证缺口** ⇒ 按 §1.1 ⑥ 第 4 条不计入） |
| **C-2 无 🔴 组件** | 全仓 **0** 个 🔴 未达标组件 | ✅ **满足**（2026-10-29 R3 轮）—— 共享层 `service/common` 由 🔴 上调 **🟡**（R 档 **R2 → R3**）⇒ ⇒ **全仓 0 个 🔴 未达标组件**<br>⚠️ **依据三条**：① **真缺陷 `DRIFT-CF-018` —— ⚠️ 2026-10-29 复核订正：不是「整条已修」，而是「**共享层半边已修 · favorite 域半边未修**」**：共享层侧已落 Go-faithful 形态（**停止能力归调用方**：`Dispatcher.java:90/136/139` 的 javadoc 明写「DRIFT-CF-018（2026-10-29 修）」＋ 阻塞重载标 `@deprecated` ＋ 提供「调用方先造句柄」的重载）＋ **变异验证 3 组全部精确转红**（3/5 · 2/3 · 2/3）⇒ 非假绿；但 🔴 **`favorite` 域半边仍成立** —— `service/favorite/favorite-rpc/.../FavoriteFactDispatchScheduler.java:119` **仍调阻塞版 `Dispatcher.run(...)`** ⇒ `once=false` 时**句柄永远拿不到** ⇒ `@PreDestroy stop()` 拿到 null ⇒ **进程仍无法优雅关闭** ⇒ **落点在 favorite 域（G3）⇒ 已派单 R5-F7**<br>⚠️ **但它不阻阶段一**：`CF-018` 的「阻断阶段一」列 = **否**（P0 #1「核心业务链路能够真实运行」**仍成立** —— 常态运行不受影响）⇒ 按 §1.1 ⑥ 第 4 条**不计入** ② **23 条编号 + 5 项未编号逐条定级**（**A 类 0 · B 类 0 · C 类 21 · D 类 6 · 证据缺口 1** ⇒ 合计 **28 项**）⇒ **域内硬阻塞 = 0**（`DRIFT-COMMON` 族未修数 = 0）③ **405 例 / 0F / 0E / 0S**<br>⚠️ **仍非 ✅**：9 轴里 **A1 / A3 / A4 / A5 / A6 五格 🟡**（A6 本轮由 🔴 降为 🟡）⇒ **不阻断阶段一**，但也不能称「已对齐」 |
| **C-3 跨组件不变量** | 矩阵 C 的 **12 条（X-1 ~ X-12）中无 🔴** | ❌ **不满足**（2026-10-29 复判 · **R4 轮的「✅ 12 条无 🔴」是口径反**）—— **X-6 由 ✅ 改判 🔴**（见 §五 X-6 行）：双侧活体实测 **Go `outgoing_carrier=present_expired` / RAG `outgoing_carrier=present_expired`**（**【2026-10-30 二次订正】** 该键两侧已一致 ⇒ **共享层载体面闭合** ⇒ **`failed_fast`/`code` 仍漂移** ⇒ **那两格是探针产物**（RAG 半边 `DeadlineDownstreamProbe.java:86` 用 `NettyChannelBuilder` 自建裸 netty channel ⇒ **绕开 devh 装配** ＋ `:198` 显式 `CallOptions.DEFAULT`）⇒ ⇒ **真实残余 = 双侧活体证据缺口（探针测不到真实装配链）**，**不是域侧真漂移** ⇒ ⇒ **域侧出向 stub 全部走 devh channel**（域侧 main 代码 `ManagedChannelBuilder`/`NettyChannelBuilder` **零命中**）⇒ ⇒ devh `AbstractChannelFactory.createChannel(String,List,boolean)` 在 **`:95` `ClientInterceptors.interceptForward(channel, interceptors)`** ⇒ **全局客户端拦截器接在 channel 上** ⇒ ⇒ **凡建在 devh channel 上的 stub（哪怕不显式调 `applyTo`）都经过拦截器链** ⇒ ⇒ **域侧出向调用本来就继承请求 deadline** ⇒ ⇒ 真实残余 = **双侧活体证据缺口** ⇒ ⇒ 判据**仍不满足**（证据不足 ⇒ 不能判 ✅）） ⇒ **矩阵 C 出现 1 条 🔴**<br>🟢 **【2026-10-30 第三次订正 · 破的理由已收窄】** —— 那个「双侧活体证据缺口」**已补上**：探针 RAG 半边改走 **devh 生产装配路径** ⇒ ⇒ 实测 **Go = RAG = `failed_fast=true\|code=4\|outgoing_carrier=present_expired\|sql_carrier=present_expired`**（四键逐键相等）⇒ ⇒ **继承面 ✅ 已闭合**（台账 `E65` 本条已闭合）⇒ ⇒ ⇒ **本行仍 🔴 的唯一理由 = 「客户端硬上界 2000ms 未启用」**（= **另一条轴** · P2-T04 · 域侧 yml）⇒ ⇒ ⇒ 矩阵 C 仍是 **1 条 🔴**（X-6）<br>✅ **8 条**：X-1（真 PG 三进程并发 · L4）· X-3 · X-4 · **X-5（20/20 已复核）** · X-7（错误码 9/9 域）· X-9 · X-10（proto 10/10 域）· **X-12**（README 起栈实跑）<br>🟡 **3 条**（**均不阻断**）：X-2（DDL 残留 **51 处** · **A 类 0** · C 23 / D 28）· X-8（users/admin 两域**已签字偏离**）· X-11（残留 **B 类**部署凭据默认值 `S1-USERS-002`）<br>🔴 **1 条**：**X-6**（deadline 分层预算与传播 · **A 类** · 落点 `service/common` ⇒ G4 写权面）<br>⚠️ **历史**：本行原写「❌ 不满足 X-11 / X-12」已过期（X-11 硬阻断已消解 · X-12 已实跑闭合）；**v4 轮的「✅ 12 条无 🔴」同样是错的** —— 它把「探针跑通了」当成了「判据满足」⇒ **本轮按双侧活体的实际差值改判**|
| **C-4 P0 九条** | P0 无 🔴 | ✅ **满足**（**判据「P0 无 🔴」仍成立**）—— ⚠️ **但 P0 汇总不再是「✅ 9/9」**：实为 **✅ 8 / 🟡 1 / 🔴 0 / ⚫ 0**（**P0 #5 = 🟡** · 证据覆盖缺口 · 见 §七 #5）<br>⇒ ⇒ **C-4 本行判据成立**（无 🔴）· **但 §8.3「通过」的附加条件「P0 九条全满足」不成立**<br>⚠️ 本行原写「✅ 9 / 🟡 0 / 🔴 0（九条全绿 · v7）」已过期（2026-10-29 复判）|

### 8.3 全仓 → 阶段 1 结论（对齐 `docs/twoStage.md` §5.6 的四种）

| 结论 | 触发条件 | 当前 |
|---|---|---|
| **通过** | C-1 ∧ C-2 ∧ C-3 ∧ C-4 ∧ P0 九条全满足 | ❌ **不满足**（2026-10-29 复判）—— **两个必要条件同时不成立**：① **C-3 破** —— **X-6 = 🔴**（**【2026-10-30 第三次订正】** 继承面**已闭合**（探针改走 devh 生产装配路径 ⇒ 实测 Go = RAG 四键逐键相等 ⇒ 台账 `E65` 已闭合）⇒ ⇒ **仍 🔴 的唯一理由 = 「客户端硬上界 2000ms 未启用」**（另一条轴 · P2-T04）⇒ ⇒ 判据仍不满足）⇒ 「12 条无 🔴」不成立；② **P0 九条全满足不成立** —— **P0 #5 = 🟡**（证据只覆盖 users 域 1/10 ⇒ 「✅ 9/9」实为「✅ 8 / 🟡 1」）<br>✅ 仍成立的部分：C-1 **6/6** ✅ · C-2 **零 🔴** ✅ · C-4 **判据「P0 无 🔴」✅**<br>⚠️ **原写「✅ 满足（2026-10-29 · v7）—— 命中「通过」」已过期**|
| **有条件通过** | 存在**明确、有限**的例外，且**不破坏核心正确性、安全性、数据完整性** | ❌ **不适用**（2026-10-29 复判）—— **例外不是「可枚举的有限清单项」，而是「判据不满足」**：**X-6 破的是跨组件不变量的判据本身**（下游 deadline ≠ min(客户端 deadline, 服务端 timeout)）⇒ 它不是「允许存在并已登记的例外」，而是**验收判据未达标** ⇒ ⇒ 与 §5.6「**不能因为项目可以启动，就直接判定阶段 1 通过**」同理 ⇒ **不吸收到「有条件通过」**<br>⚠️ **共享层 28 项**（C 21 · D 6 · 证据缺口 1）与交付物 5 的 C 类暂缓项仍按 §1.1 ⑥ 第 4 条**不计入** ⇒ **它们不是本次的例外来源**；**本次的例外来源 = X-6（E65）＋ P0 #5 的证据覆盖缺口**|
| **未通过** | 存在破坏核心正确性 / 安全 / 数据完整性的未修项 | ✅ **满足**（2026-10-29 复判）—— **两类未达标项**：① **E65 / X-6 = 🔴 A 类**（出向 gRPC 不继承请求 deadline ⇒ 跨组件不变量判据不满足 ⇒ C-3 破）② **P0 #5 = 🟡 A 类**（证据覆盖缺口 ⇒ 「P0 九条全满足」不成立）<br>🟢 **【2026-10-30 第三次订正 · 未达标项的构成已变】** —— ① 的**构成**变了：**`E65` 本条已闭合**（继承面经双侧活体**逐键相等**证成）⇒ ⇒ **仍 🔴 A 类的是 X-6 判据的另一项**（「客户端硬上界 2000ms 未启用」· **另一条轴** · P2-T04）⇒ ⇒ ⇒ **C-3 仍破、阶段一仍 ❌ 未通过 ⇒ 本行结论不变**（但**破的理由已不是继承面**）<br>⚠️ **原写「❌ 不满足 —— 三类硬阻断全部消解」已过期**（该结论建立在「X-6 探针 PASS ⇒ 判据满足」的口径反之上）|
| **受阻** | 缺必要环境或证据 | ⚪ **不适用**（2026-10-29 复判）—— **环境仍然可用**（2026-10-29 **真起了一次栈**：7 容器全起 · postgres / redis / nacos **healthy**）⇒ **不是「受阻」**<br>⚠️ **但要区分两种「证据缺」**：**环境受阻**（基础设施起不来）⇒ 本轮**不适用**；**证据覆盖不足**（基础设施正常、但某个面的证据没跑到）⇒ **本轮就是这种**（P0 #5 的 9 域起栈证据）⇒ ⇒ **归「未通过」并注明缺哪份证据，不归「受阻」**|

### 8.4 ⭐ 阶段一准入结论（v5 · 2026-10-29）

> ## ❌ **阶段一未通过**
>
> **（2026-10-29 复判 · v4 轮的「✅ 阶段一通过」建立在「X-6 探针 PASS ⇒ 判据满足」的**口径反**之上 ⇒ 本轮按双侧活体的实际差值改判）**
>
> **不是**「受阻」（环境可用 —— 2026-10-29 **真起了一次栈**：7 容器全起 · postgres / redis / nacos **healthy** ⇒ **基础设施没问题**），
> **也不是**「有条件通过」（**「有条件通过」要求例外是「明确、有限、且不破坏核心正确性/安全/数据完整性」的清单项** —— 而 **X-6 破的是跨组件不变量的判据本身**（下游 deadline ≠ `min(客户端 deadline, 服务端 timeout)`）⇒ **那是「判据未达标」，不是「允许存在的例外」** ⇒ 与 §5.6「**不能因为项目可以启动，就直接判定阶段 1 通过**」同理）。
>
> **两类硬阻断 —— 仍未消解**（原写「三类硬阻断 —— 全部消解」是 v4 轮结论，其第 ③ 条的口径有误）：
> 1. ✅ **安全** —— ~~X-11 密钥链：空签名密钥可起栈（E4 实测）~~ ⇒ **P0 #8 已 ✅**（R1 + R3 两批）：**10 个 api/rpc 模块全部**去掉 yaml 明文默认值 + `@PostConstruct` **fail-closed 守卫**（判据 = `null` / 空串 / 全空白 / **未解析占位符字面量** ⇒ 三条路都拒绝启动）＋ **4 模块各 4 条变异精确转红** ＋ compose **6 处 `:?`**（缺变量 ⇒ 拒绝渲染，实测 EXIT=1）＋ `check-no-prod-secrets.py` **EXIT=0**（未豁免命中 **0**）⇒ ⇒ **空签名密钥已不可起栈**。
>    ⚠️ **残留 = B 类**（`S1-USERS-002`：**8 个 rpc 模块 / 10 处**部署凭据默认值）⇒ 按 §1.1 ⑥ 第 4 条**不计入**。
> 2. 🟡 **可构建性 —— 证据覆盖缺口（整改已完成，判据本身满足）** —— ~~X-12 项目说明：README 21 字节、无 `.env.example`~~ ⇒ **P0 #5 的整改已落地**（R1·T-1.1~T-1.4 ＋ **R4·T-4.7 实跑**）：README **21 B → 9,815 B** ＋ `.env.example`（**56 变量**）＋ compose **`:?`** fail-closed ＋ **`./manage.sh up` ⇒ EXIT=0**（7 容器全起）＋ **`POST /user/v1/register` / `/user/v1/login` ⇒ HTTP 200**（login 返真实签名 JWT · 167 字符）＋ 带 token 打 `/user/v1/get` ⇒ **200** / 不带 ⇒ **401**（`{"code":1006,"msg":"TOKEN已过期"}`）＋ **`psql -U sea -d sea_user` ⇒ 4 张表 + 1 行** ⇒ ⇒ **api → gRPC → rpc → PG 全链路真跑**。
>    ⚠️ **顺带修掉 compose 3 处「新机器必然起不来」的缺陷**（commit `0b707b39`）：① `dockerfile: Dockerfile` vs 仓内 `dockerfile`（小写）⇒ **Linux 必挂** ② `cache_from`/`cache_to`（`type=local,src=~/.m2/repository`）**三重不成立** ＋ 两 service **并发写同一 dest** ⇒ **build 整轮失败** ③ `postgres:latest` 已到 **PostgreSQL 18.6**，命名卷挂在旧的 `/var/lib/postgresql/data` ⇒ 官方 entrypoint **拒绝启动** ⇒ 改挂 `/var/lib/postgresql`。
> 3. 🔴 **业务正确性 —— 仍未消解**（v4 轮写「已消解」，其口径有误）：共享层 `service/common` **那 28 项逐条定级 ⇒ A 类硬阻塞 = 0**（**28 项未修 = 23 编号 + 5 未编号**，其中 **C 类 21 · D 类 6 · 证据缺口 1** ⇒ 按 §1.1 ⑥ 第 4 条**不计入 R3 ⑥**；`DRIFT-COMMON-005` 已修 · `DRIFT-SF-001` 已修 · **`DRIFT-CF-018` 共享层半边已修**（含变异 3 组精确转红）⇒ 未修 24 → 23）；`like-rpc` 的 `DRIFT-LIKE-003` **A/B 两段已闭环**，剩余 **`003-C`（暴露形态）已改判 C 类暂缓项**（11 字段登记见 [`docs/Stage1/05-差异与暂缓项清单.md`](Stage1/05-差异与暂缓项清单.md) `S1-LIKE-001`）⇒ 按 §1.1 ⑥ 第 4 条**不计入**；global `E60` **已修并销案**。
>    （⚠️ `like-common` **不再计入本条**：v3 复核发现它有**仓内 Go 派生的契约测试** ⇒ 「从未与 Go 对照」的判定**不成立** ⇒ 降为**证据缺口** G-17，见 §十 #18。）
>    🔴 **本轮订正（2026-10-29 复判）—— v4 轮漏了一条不在那 28 项里的 🔴**：
>    **`E65`（出向 gRPC 不继承请求 deadline）** —— 它落在 `service/common`，却**从未进入 `alignment-2026-10-23.md` §十三·一 的 25 条逐条定级表**（该表只有 CF / LOG / SF 三族）⇒ ⇒ **「A 类硬阻塞 = 0」这句话对 E65 不成立**。
>    **实测证据（2026-10-29 · 本轮实跑 `python3 tools/difftest/check-deadline-downstream.py` ⇒ EXIT=0）**：Go = `{'failed_fast': 'true', 'code': '4', 'outgoing_carrier': 'present_expired'}` · RAG = `{'failed_fast': 'false', 'code': '0', 'outgoing_carrier': 'absent'}` ⇒ **`outgoing_carrier` 两侧不等** ⇒ **RAG 出向 gRPC 不继承请求 deadline** ⇒ **X-6 判据不满足 ⇒ X-6 = 🔴 ⇒ C-3 破 ⇒ 阶段一不能判「通过」**。<br>>    🟢 **【2026-10-30 第三次订正 · 该证据已被更新】** —— 上面那组读数是**探针产物**（RAG 半边自搓裸 channel ⇒ 绕开 devh 装配）⇒ ⇒ 探针改走 **devh 生产装配路径**后实测 **Go = RAG = `{'failed_fast': 'true', 'code': '4', 'outgoing_carrier': 'present_expired', 'sql_carrier': 'present_expired'}`**（四键逐键相等）⇒ ⇒ **继承面已闭合**（台账 `E65` 已闭合）⇒ ⇒ ⇒ **X-6 仍 🔴 的唯一理由 = 「客户端硬上界 2000ms 未启用」**（`P2-T04` · 另一条轴）⇒ ⇒ ⇒ **C-3 仍破 ⇒ 阶段一仍不能判「通过」**。
>    ⇒ **E65 = 🔴 A 类**（阶段 1 准入阻断项 · 落点 `service/common` ⇒ G4 写权面）。
>    ⚠️ **为什么它不破 C-2**：§8.1 规定「**跨组件残留（落点在共享层/别域）不计入组件 R，但计入域级聚合**」⇒ 共享层**组件**仍是 🟡 ⇒ **C-2 仍成立**；破的是 **C-3**（矩阵 C 的跨组件不变量）。
>    ⚠️ **为什么它不算「有条件通过」的例外**：它不是「登记在案的有限例外」，而是**一条跨组件不变量的判据未达标** ⇒ §5.6 的「有条件通过」不适用。
>
> **⇒ 逐条对应 `docs/RoundPlan/_INDEX.md` §一 的 S1-1 ~ S1-6**（**每条带证据强度与可复算入口**）：
>
> | # | 判据 | 结论 | 证据强度 | 可复算入口 |
> |---|---|---|---|---|
> | **S1-1** | P0 九条（**九条全满足**） | 🟡 **部分满足** | **L4（局部）** —— P0 #5 的整改与 users 域实测均为 L4；⚠️ **其余 9 域的起栈形态证据未跑** ⇒ 证据强度停在「1/10 域」 | 本文件 §七 ＋ `docker-compose.yaml`（7 service）+ `manage.sh:18`（`APP_SVCS="user-rpc user-api"`） |
> | **S1-2** | C-1 核心 6 域全 ✅ | ✅ **满足**（**6/6**） | **L4**（各域**双侧活体探针已落仓可复算**） | 本文件 §8.2 ＋ `tools/api-full-parity-probe/**` |
> | **S1-3** | C-2 零 🔴 组件 | ✅ **满足** | **L1/L2 + 台账逐条定级**（共享层 **28 项逐条有分类** ⇒ **那 28 项的 A 类 0**）⚠️ **但 E65 不在这 28 项里** ⇒ 见 §8.4 第 ③ 条 | 本文件 §8.2 ＋ `docs/DRIFT/common/alignment-2026-10-23.md` §十三·一 ＋ §8.1（跨组件残留规则） |
> | **S1-4** | C-3 跨组件不变量 **12 条无 🔴** | ❌ **不满足**（**X-6 = 🔴**） | **L4（双侧活体）** —— **【2026-10-30 第三次订正】** X-6 的**继承面已闭合**（实测两侧四键逐键相等 ⇒ `outgoing_carrier`/`failed_fast`/`code` 全等）⇒ ⇒ **仍 🔴 = 客户端硬上界那一项**；其余 11 条为 L2/L4 混合 | 本文件 §8.2 ＋ §五 ＋ `python3 tools/difftest/check-deadline-downstream.py` |
> | **S1-5** | `twoStage §5.7` 冻结基线 + §十一 交付物 1~6 成文 | 🟡 **部分满足**（交付物 1~6 成文 ✅；**冻结基线已解除**） | **E1**（文档成文 + **机器核**：交付物 5 的 51 行 **11 字段**逐行可切分计数）⚠️ **原冻结建立在「✅ 阶段一通过」上 ⇒ 已解除** | 本文件 **§十·二** ＋ `docs/Stage1/` |
> | **S1-6** | 判定结论 = 「通过」 ＋ 守门全绿 | ❌ **不满足**（判定结论已改判为「❌ 未通过」） | **机器跑**（守门清单**逐个实跑取真实退出码**）⚠️ **守门全绿 ≠ 判据满足** —— 本轮 E65 的守门就**全绿**而漂移仍在 | 本文件 §8.4 ＋ §六 6.3 |
>
> ⚠️ **「未通过」的口径（不许含糊）**：① **P0 九条 ≠ ✅ 9/9** —— 实为 **✅ 8 / 🟡 1 / 🔴 0**（**P0 #5 = 🟡** 证据覆盖缺口）⇒ **「P0 九条全满足」这一必要条件不成立**；② **C-3 破** —— **X-6 = 🔴** ⇒ 「12 条无 🔴」不成立；③ 仍成立的部分 = **C-1 6/6 · C-2 零 🔴 · C-4 判据「P0 无 🔴」**；④ **残留项已定级的那部分仍照旧不计入**（共享层 **28 项** C 21 · D 6 · 证据缺口 1 ＋ DDL 残留 **51 处** C 23 · D 28 · **A 0** ＋ `S1-USERS-002` B 类 ＋ `DRIFT-CF-018` 的 favorite 域半边 C 类 ⇒ 按 §1.1 ⑥ 第 4 条一律不计入）⇒ **但它们不是本次的例外来源** —— 本次的例外来源是 **E65（🔴 A 类）＋ P0 #5 的证据覆盖缺口（🟡 A 类）**；⑤ **两类阻断的闭合路径**：**E65** ⇒ 共享层把 `GoStyleRequestDeadline` 绑到出向 gRPC 调用（**【2026-10-30 更新】** 这一半**已落码** ⇒ `RpcInterceptor.onHalfClose` 已绑 ⇒ `applyTo` 在 gRPC 路径**不再是 no-op**）＋ ⚠️ **各域裸 stub 改 `applyTo` 不是必要条件**（2026-10-30 二次订正：域侧出向 stub 全部走 devh channel ⇒ 全局拦截器接在 channel 上 ⇒ **本就继承请求 deadline** ⇒ 改了是**冗余但等价**）＋ **真修法 = 探针侧**：RAG 半边改走 devh channel 或显式接入出向拦截器 ⇒ ⇒ 让 `failed_fast`/`code` 变成**真实观测** ⇒ ⇒ **各域出向 client 逐个过探针**（双侧活体证据仍缺）；**P0 #5** ⇒ **补 9 域的「按 README 起栈」形态证据**（**不改产品代码**）⇒ ⇒ **「未修」≠「阶段一未通过」**。
> ⚠️ **「阶段一未通过」也不等于「工程成熟度审查完成」** —— **同样：本文件未做、也不预支阶段二** —— 生产级十二维度审查 / §9 故障矩阵 / 生产流量与 SLO / 灾备演练**均属阶段二**（`docs/twoStage.md` §六），**本轮未做、也不预支**。
> ⚠️ **下一步从哪开始**（2026-10-29 复判后更新）⇒ **见 `docs/RoundPlan/_INDEX.md`**（阶段一重构后的 7 轮计划）：**Round 1**（P0 #5 的 9 域起栈形态证据补齐 ⇒ **补证据，不改产品代码**）· **Round 2**（E65 / X-6 落码 ⇒ 共享层绑 deadline ＋ 各域出向 client 逐个过探针）· **Round 3**（本文件的判据状态复判 + 冻结基线重新冻结）· **Round 5**（台账逐条对齐：DRIFT / Pitfalls / Intentional_Deviations）· **Round 6**（收口回归）· **Round 7**（最终验收 + 重新冻结基线）。
> ⚠️ **原冻结基线已解除**（原冻结建立在「✅ 阶段一通过」结论上 ⇒ 该结论已被本轮推翻 ⇒ 冻结失效）⇒ **冻结与结论必须一致** ⇒ 见 §十·二 的解除记录。

---

## 九、证据缺口清单

> **四类分开**：**真实实现缺陷**（§11.1）· **证据不足**（本节）· **环境受阻**（§11.3）· **已签字偏离**（§11.4）。
> 本节 = **证据不足**类：**结论可能对，但今天不可复算 / 未取证**。

| 编号 | 缺口 | 影响的结论 | 证据强度 | 处置方向 | 是否阻断阶段一 |
|---|---|---|---|---|---|
| **G-1** | **`follow` 域 0 个落仓双侧对拍入口** —— 台账「52/52 逐字节一致」依赖 `/tmp/probe.sh` / `probe_data.sh` / `probe_ext.sh` / `cmp.sh` / `u3d_probe.sh` / `u3c_probe.sh` / `blackhole3.py`（**未进版本控制**）。台账自己写明「故障注入装置应从 `/tmp` 提升为仓库资产 —— **本轮未做**」 | `follow-api` / `follow-rpc` 的 R4 判定 | **E4** | ✅ **已闭合**（2026-10-29）—— 探针落仓 `tools/api-full-parity-probe/follow/`（**follow 域双侧活体**）⇒ **20 条「Go == RAG == 冻结期望值」三方逐字节**（M1~M5 全按预期 + **还原转绿**）⇒ 台账「52/52」降级为**历史声明** | ✅ **否**（已闭合） |
| **G-2** | **`like` 域 0 个落仓双侧对拍入口** —— `/tmp/likeprobe/{main.go,RagProbe.java,NullElem.java}` **已消失** | `like-api` 6 条 SEMANTIC MISMATCH 的**复算证据不可复算**；P0 #1 | **E4** | ✅ **已闭合**（2026-10-29）—— 探针落仓 `tools/api-full-parity-probe/like/`（阶段 A **9 case 逐字节** / 阶段 B DC **970 B sha256 相同** / 阶段 C E60 注入）⇒ **EXIT=0** + **3 变异精确转红** ⇒ **「已修」已被实测复核**（不凭台账断言） | ✅ **否**（已闭合） |
| **G-3** | ~~**`article` 域 api / rpc 无任何双侧活体对拍**~~ ⇒ **已解除**（2026-10-29） | `article` 域 R4；P0 #1/#4 | **L4** | ✅ **已闭合** —— ① **前提已变**：etcd 宿主口已映射（`49319→2379`）⇒ 「受阻」不再成立 ② 探针落仓 `tools/api-full-parity-probe/article/`（`run.sh` + `mutate-parity.sh` + `expected.txt` + README）⇒ **两侧同时起服务**（Go 真源栈 + RAG 栈）⇒ 同一请求比**字节** ③ **主线独立复跑 EXIT=0**：3 场景（`list_articles` / `get_article_exists` / `get_article_missing`）**Go == RAG == 冻结期望值**逐字节一致，**且两侧播种了同一行**（`status=2 PUBLISHED` · id `9007199254740993`）⇒ **P0 #1 的「播种过数据 + ≥1 条可比对场景」已满足** ④ **变异**：M2（外层键序）/ M3（冻结值）**精确转红**；M1 经主线手动复现亦 **exit 1 精确转红** ⇒ **探针非假绿** | ✅ **否**（已闭合） |
| **G-4** | ~~**`users-common` 的 176 条双侧活体证据在 `/tmp/commonprobe/**`，已消失**~~ ⇒ **已落仓**（2026-10-29） | `users-common` 的 R4 判定 | **L4** | ✅ **已闭合** —— 探针落仓 `tools/api-full-parity-probe/users-common/`（`run.sh` + `mutate-parity.sh` + `compare.py` + `expected.txt` + `go/{main,admin_main}.go` + `java/Probe.java`）⇒ **176 条「声明」变 260 条「可复算」**；**主线独立复跑 EXIT=0**：**218 条逐字节相同 / 25 条单侧专属 / 17 条已登记的两侧不同 / 0 失败** ⇒ **可复算** | ✅ **否**（已闭合） |
| **G-5** | ~~**`hot-common` 的对拍探针在 `/tmp`**（`f23fe570…`）⇒ 不可复算~~ ⇒ **已落仓**（2026-10-29 · G3 域侧） | `hot-common` 的 R4 判定 | **L4** | ✅ **已闭合** —— 两份探针落仓：`tools/errmsg-probe/hot/`（错误码 ⇒ 对拍 EXIT=0 · 变异 4/4 精确转红 ⇒ **A6 = L4**）＋ `tools/proto-wire-probe/hot/`（proto **消息体字节** ⇒ EXIT=0 · 变异 3/3 精确转红 ⇒ **A7 = L4**）⇒ 满足 §1.1 的 **R4 ⑦⑧** ⇒ **`hot-common` 的 R 判定由 R3 升 R4** | ✅ **否**（已闭合） |
| **G-6** | ~~**proto 落仓静态守门只覆盖 4/10**（user · admin · task · comment）⇒ `follow` / `like` / `hot` **连静态守门都没有**~~ ⇒ **已扩到 10 域**（2026-10-29） | proto 漂了**不会红** | E2 | ✅ **已闭合** —— `tools/difftest/check-proto-static.sh` 扩到 **10 域逐字节**（字段名 / 字段号 / message 名 / rpc 签名）＋ **变异必红** ⇒ `follow` / `like` / `hot` 等域**现在有静态守门**了<br>⚠️ **效力边界不变**：静态文本 diff **不覆盖**运行时 `optional` presence 语义 / 字段号硬编码 / Jackson 配置 ⇒ 那是 §九 **G-19**（取证缺口，**不降 R**） | ✅ **否**（已闭合） |
| **G-7** | **错误码双侧逐字节对拍只覆盖 2/9 域**（article + favorite） | 错误码契约的域覆盖 | E3 | 按域补 `errmsg-probe/<域>/run-parity.sh` | ✅ **否**（**已闭合** · 2026-10-29）—— **9/9 域已覆盖**：article（根级件）· favorite · hot · like · users · comment · follow · message · **task** ⇒ 每个域都有 `run-parity.sh`（双侧逐字节，sha256 相同）+ `mutate-parity.sh`（变异精确转红）⇒ **白名单可审**（逐条豁免 + 理由）· **OBS 段**（平台/库措辞差异，单列且「差异消失即 exit 2」⇒ 有牙齿）<br>⚠️ **退出码口径已统一**（2026-10-29）：`mutate-parity.sh` **0 = 全部精确转红 = 通过** · 1 = 守门失效 · 2 = 环境问题 ⇒ 此前并存过**三套**约定（三个独立 agent 各自提出过同一处不一致）⇒ 已统一并**逐域实跑复验**<br>⚠️ **task 域的特殊形状**：task **无 `-common`、无码值表** ⇒ 该域比的是 **gRPC 码 + description 文案**（4 层 43 条：RPCUID 4 · RPCBARE 5 · API 解析类 17 · API rpc 透传类 17）⇒ Go 侧直调真源（用「永远失败的 gorm.ConnPool」+「必然连不上的 redis Dialer」注入受控文案 ⇒ 走真实代码路径、失败原因可控）<br>⚠️ **证据强度**：各域为 **L1/L2（双侧同时跑的静态对照）** ⇒ **不是**双侧活体 ⇒ 不得据此宣称「已对齐」 |
| **G-8** | **`check-ddl-static.py` 默认不开 `--indexes`** ⇒ **索引 / 唯一约束 / 触发器全部未查**（§3.1 对照清单最后两项至今无机器核过） | P0 #3 | **E4** | ✅ **已闭合**（2026-10-29 · R3·T-3.1）—— 9 域**逐域**跑 `--indexes`（`check-ddl-static.py --domain <域> --indexes --check-source`）⇒ **索引 / 唯一约束 / 触发器不再是 `NOT CHECKED`**；差异按域登记（**加强型 / 已签字** ⇒ 不阻断） | ✅ **否**（已闭合 ⇒ **P0 #3 上调 ✅**） |
| **G-9** | **`check-ddl-static.py` 比的是「活库里实际存在的表结构」，不是仓库里的 `.sql` 文件** ⇒ 迁移文件改了但未重跑时，脚本仍显示「一致」 | P0 #3 · X-2 | E3 | ✅ **已闭合**（2026-10-29 · R3·T-3.2）⇒ 新增 **`--check-source`**：**把仓里的 `.sql` 在一个干净临时库里原样重放一遍**（**让 PG 自己解析**，不写 SQL 解析器 ⇒ 与生产 Flyway / SchemaInitializer 同一条路径）⇒ 再与活库逐项对比（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器）⇒ 「迁移改了没人重跑」**当场变红**；**变异必红**已实测（`favorite_item.target_id` 255→200 ⇒ 精确报 1 处）；**迁移清单改由目录扫描产生**（`--print-rag-sql`，`ddl-parity.sh` 复用同一产生点）⇒ **实测抓到并修掉**「`ddl-parity.sh` 写死清单漏了 favorite 域 `V7__align_favorite_outbox_time_columns_with_go.sql`」 | ✅ **否**（**已闭合**） |
| **G-10** | **`check-jar-executable.sh` 的「未构建 ⇒ 跳过且不计失败」缺口** —— 本轮实跑**只检查 5/20 模块**却返回绿 | X-5 | **E3** | ✅ **已闭合**（2026-10-29 · R3·T-3.5）—— `check-jar-executable.sh` 两条一起做：① **永远打印「已检查 N/M」**（读者不会把「没查」读成「查过且没问题」）② **未构建 ⇒ 判红**（除非显式 `--allow-unbuilt`）⇒ 假绿口子封死 | ✅ **否**（已闭合） |
| **G-11** | **共享索引过期** —— `docs/DRIFT/_INDEX.md` 与 `component-coverage-matrix.md` 对 `favorite` 域仍写「零台账 / 从未对照」，已被 10-25 后实测推翻；`component-coverage-matrix.md` 当前态表**有 39 行**（38 组件 + 1 共享层） | 读索引会误判现状 | **E1** | ✅ **已闭合**（2026-10-29）—— `docs/DRIFT/_INDEX.md` 的 `favorite` 行 + `docs/DRIFT/_cross/component-coverage-matrix.md` 的三行均已订正（「0 字节 / 从未对照」 ⇒ **已被实测推翻**）⇒ **favorite 域不是「整块空白」** | ✅ **否**（已闭合） |
| **G-12** | **5 份台账的「文件头核对日期 / 组件判定」已被正文推翻**（like/api.md、like/rpc.md、message/api.md、message/rpc.md、users/*） | 直接引用台账头会**低估现状** | **E1** | ✅ **已闭合**（2026-10-29 · R4·T-4.2 · G-12）—— **7 份台账头部各插入「当前态指针」块**（like/api · users/common · users/vs-common · users/user/api · users/user/rpc · users/admin/api · users/admin/rpc，各 **+5 行** · **deletions = 0**）⇒ 直接引用台账头不再低估现状 | ✅ **否**（已闭合） |
| **G-13** | ~~**3 域无独立失败路径矩阵**（`article` / `task` / `hot`）~~ ⇒ **全部闭合**（2026-10-29） | P0 #2 · #4 | **L3** | ✅ **已闭合（0 域无矩阵）** —— ① **follow**：G1 `1879635` 补 **62 条真 Tomcat** ② **article**：**104 条**（真 Tomcat `DEFINED_PORT` · 7 端点 × 8 类失败维度：认证 / 权限 / 参数校验 / 业务规则 / 下游不可用 / 下游内部错 / 边界值 / 路由层）③ **hot**：**20 条**（hot-api，真 Tomcat）＋ **8 条**（hot-rpc，真 gRPC server · 5 个方法的线上字节）④ **task**：**33 条**（1 端点 × 6 维度）⇒ 合计 **227 条**；**变异**：article 6 + hot-api 5 + task 2 = **13/13 精确转红**（红的方法集合必须与预期**完全相等** ⇒ 多红一条 = 判据间意外耦合）<br>⚠️ **证据强度 = L3/R3**：这些矩阵**只起 RAG 单侧、Go 真源一次没起** ⇒ **不是双侧活体** ⇒ **不得据此宣称「与 Go 逐字节一致」** ⇒ **P0 #2/#4 仍需靠双侧活体证据收口**，不由本条决定 | ✅ **否**（**矩阵缺口已闭合**；P0 #2/#4 的证据强度另计） |
| **G-14** | **`like` 域 8 条 Unresolved（U-01~U-08）台账未标关闭**（U-04 已注明闭环） | `like-api` 的取证完整性 | **E4 / E3 / E2** | ✅ **已闭合**（2026-10-29 · R3·T-3.7）—— `docs/DRIFT/like/api.md` §9.1 的 **8 条 U-01~U-08 逐条给结论**：**✅ 3**（U-02 / U-04 / U-07）· **🟡 4 取证缺口**（U-01 / U-03 / U-05 / U-08）· **🔴 1**（U-06 观测面：Go `devserver.StartAgent` 确被消费 ⇒ RAG actuator 挂业务端口 18887 且无 pprof ⇒ **不改变本组件阶段一判定**）⇒ 另 `DRIFT-LIKE-003-C` 已按 **C 类**登记（11 字段齐全） | ✅ **否**（已闭合） |
| **G-15** | **`hot-proto` 的台账内部矛盾** —— `hot/vs-common.md:59` 写「proto 契约无自动守门」，`:67` 写「已落码 `HotProtoContractTest`」 | `hot-proto` 的判定 | E1 | ✅ **已闭合**（G3 `50bb5f3` 回填；⚠️ 真实位置 = **`hot/vs-common.md`** ⇒ 与 G-18 同一处，已一并裁定） | ✅ **否**（**已闭合**） |
| **G-16** | **`service/common` 的「3 个 NOT EQUIVALENT」自相矛盾** —— 台账 `:36` 写「3 个」，`:645` 的枚举里却列了 **4 个包**（communityfact / logger / **observability** / snowflake）⇒ 实为「**3 个已正式判定 + 4 个实际持开放项**」 | 共享层缺口的基数 | **E3** | ✅ **已闭合**（2026-10-29）—— 基数订正为「**28 项未修（23 编号 + 5 未编号）· A 类 0**」（原「3 个 vs 4 个包」的自相矛盾消除）⇒ 与 §十一 D-06 同步 | ✅ **否**（已闭合） |
| **G-17** | **`like-common` 无专属台账** —— 但它**有**仓内 Go 派生的契约测试（`LikeErrorContractTest`：29 个码逐条 1:1 + 文案表 + 未知码回落 + `CodeError` 序列化）⇒ 「从未与 Go 对照」**不成立**；缺的只是**逐条台账记录** | `like-common` 的判定**可审计性** | E3 | 补 `docs/DRIFT/like/common.md`（逐条抄录 Go 出处与结论） | 🟡 **否**（**记录缺口**，非行为缺口） |
| **G-18** | **`hot-proto` 的判定记录位置不统一** —— 记录在 `docs/DRIFT/hot/vs-common.md:67`（`H-1` 已落码）而不在 `docs/DRIFT/hot/proto.md`；且 `hot/vs-common.md:59` 仍写「proto 契约无自动守门」⇒ **同文件内自相矛盾** | `hot-proto` 的判定可审计性 | E3 | ✅ **已闭合**（G3 `50bb5f3` 回填 ⇒ **裁定结果 = 以 `hot/vs-common.md` 为准**；`hot/proto.md` **不存在**，不是「漏写」） | ✅ **否**（**已闭合**） |
| **G-19** | **proto 的「消费方式」未取证**（`optional` presence / 代码里的字段号硬编码 / Jackson 配置）—— `docs/DRIFT/_cross/proto-verification.md:40-46` 的「三档回归网」只覆盖**文件层** ⇒ AGENTS.md §3.4 的效力边界（「复制忠实 ≠ 消费方式已验」）在 **10 个 proto 上全部成立** | 10 个 proto 组件的【PROTO 型】③⑤ | E2 | 按域补「生成码 presence / 消费点构造」断言（`ArticleProtoContractTest` 已示范） | 🟡 **否** |
| **G-20** | **`docs/DRIFT/_cross/proto-verification.md:45` 已过期** —— 仍写 `article`/`hot`/`like`/`message`/`comment`/`favorite`/`follow`/`task`「**脚本一个都没覆盖**」，但 `ArticleProtoContractTest` / `HotProtoContractTest` / `FavoriteProtoWireParityTest` / `check-surface-parity.py` **已覆盖其中 5 个** | 读跨域 proto 记录会**误判现状** | **E1** | ✅ **已闭合**（2026-10-29 · R4·T-4.2）—— `docs/DRIFT/_cross/proto-verification.md` 的「脚本一个都没覆盖」已按**现状 10 域**订正 | ✅ **否**（已闭合） |

---

## 十、变更记录（原结论 → 问题 → 修订后结论 → 依据）

> **v1 ⇒ v5 的全部修订**，逐条列明。**没有一条是为「让矩阵变绿」而改的** —— 方向有升有降（**#35 / #36 是本次复判新增的两条降级**）。
> **v2 ⇒ v3 的差异**：v2 是初稿；v3 是**机器自检后**的修订 —— 修正了 v2 对 `like-common` / `hot-proto` / `user-proto` / `admin-proto` 四处**评级方向相反**的错误（#18~#20），并把 §1.1「未评级」与 §1.3「✅ 判据」的定义补到可机械执行（#21）。
> **v3 ⇒ v5 的差异**：v4 = v3 + 机器自检后的评级订正；**v5 = v4 + 2026-10-29 复判的两条 A 类阻断改判**（#35 / #36）⇒ **结论由「✅ 阶段一通过」改为「❌ 阶段一未通过」** ⇒ **冻结基线随之解除**。
> **v2 ⇒ v3 的差异**：v2 是初稿；v3 是**机器自检后**的修订 —— 修正了 v2 对 `like-common` / `hot-proto` / `user-proto` / `admin-proto` 四处**评级方向相反**的错误（#18~#20），并把 §1.1「未评级」与 §1.3「✅ 判据」的定义补到可机械执行（#21）。

| # | 原结论（v1） | 发现的问题 | 修订后结论（v5） | 依据 |
|---|---|---|---|---|
| **1** | 38 组件矩阵中把 `service/common` 算作第 38 个组件 | `service/common` 是**跨服务共享层**，AGENTS.md §3.5 口径 **10 api + 10 rpc + 8 common + 10 proto = 38** | `service/common` **单列为第 39 行**，不计入 38 | `AGENTS.md` §3.5；`service/` 下 10 个一级目录；源台账当前态表 39 行（38 + 1 共享层） |
| **2** | A-1 的 `main/LOC` 用 `cat \| wc -l` 统计 ⇒ 28 个组件的行数偏小 | `cat \| wc -l` **少计无尾换行的文件** ⇒ 与逐文件 `splitlines()` 口径不一致 | 全部改为**逐文件 `splitlines()`**（口径写入表注） | 本轮重算：article-api 5332→**5355**、service/common 14768→**14804** 等 |
| **3** | P0 **#1** = ✅ | 同时又写「本机无 Kafka/Redis/Nacos ⇒ 无运行时证据」⇒ **自相矛盾**；且中间件**全部可用** | P0 #1 = 🟡 **部分满足**；G-6「环境受阻」整条**删除** | 实测：Kafka `59993` · Redis `57379` 等 8 个 · Nacos `8848` 全部可达；`Sea-etcd` 容器 Up 8 天（宿主 `2379` 未映射）⇒ **环境不是借口**。另 `like` 域事实投递链路 `UNRESOLVED` + 其活体证据已不可复算 |
| **4** | P0 **#2** = ✅，支撑是「每端点 ≥1 失败路径测试」 | **「存在失败路径处理」推不出「参数校验 / 业务规则 / 认证 / 权限四项都有效」**；且失败路径**域覆盖不全** | P0 #2 = 🟡 **部分满足**，并列出待补项（4 域失败路径矩阵 + 权限控制显式用例） | §七 P0 #2 行；G-13 |
| **5** | P0 **#3** = ✅ | `check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引/约束/触发器**全未查**；且它比的是**活库**不是仓库 `.sql` | P0 #3 = 🟡 **部分满足** | `check-ddl-static.py:932-935`（脚本自陈 `NOT CHECKED`）；G-8 / G-9 |
| **6** | P0 **#4** = ✅ | 失败路径探针**只覆盖 5 域**，`article`（失败路径最密集的域）**没有** | P0 #4 = 🟡 **部分满足** | §六 6.1 表 |
| **7** | `check-proto-static.sh` 被当作 8 个域的 proto 判定命令 | 它**只覆盖 user / admin / task** 三个 proto，且是**静态文本** diff | 只用于 3 个域；其余域改引 §六 的实际入口；**新增** `check-surface-parity.py` 承担契约面 | `check-proto-static.sh:30-38`、`:101-106` |
| **8** | `check-tomcat-limits.py` 被表述为「与 Go 上下限**逐值一致**」 | 它**只读 `application.yml` 的三个配置值**，不验证运行时行为 | 改述为「**配置值**一致」；运行时证据另引 hot 台账的真 Tomcat 门禁 | `check-tomcat-limits.py` docstring |
| **9** | X-5「产物可执行性 **10/10 ✅**」 | 本轮实跑显示 **15/20 模块「未构建」被 `continue` 跳过且不计失败** ⇒ 绿灯只覆盖 5/20 | X-5 = ⚠️ **本轮未复核**（本轮只查了 5/20）⇒ 新增 G-10 | `check-jar-executable.sh:56-59`；本轮实跑输出 |
| **10** | 多个守门被当作「证明组件满足语义/运行面」的依据 | 多数守门是**静态 / 单侧**检查；`check-deadline-verdict.py` **不产生业务结论**；`check-grpc-runtime-classpath.py` **baseline 内的违规不失败** | 新增 [§六](#六判定命令的实测覆盖范围) 逐条列出「能证明 / 不能证明」；矩阵 C 各行的判定命令均按 §六 重述 | §六 6.1 / 6.2 两表 |
| **11** | `hot-common` = **R4 / E4**；`favorite-proto` = **R4**；`follow-api`/`follow-rpc` = **R4\***；`users-common` = **R4** | ① `hot-common` 对拍探针在 `/tmp` ⇒ **不可复算**（R4 要求可复算）② `favorite-proto` **台账未见变异验证**（R4 要求变异）③ `follow` 域探针全在 `/tmp` ⇒ 降 R3/R2 ④ `users-common` 探针已消失 ⇒ 降 R3 ⑤ `like-common` 的降级理由**本身是错的** ⇒ 由 **#18** 单独处理 | 依次改为 **R3 / R3 / R3 / R2 / R3**（`like-common` 见 #18） | 各台账的探针位置与变异章节；§1.1 R4 判据 ⑦⑧；§九 G-1/G-2/G-4/G-5 |
| **12** | `comment-rpc` = R3；`message-rpc` = R3 | 二者**双侧活体 + 变异**均齐备且归属本组件的必对齐项已清零 ⇒ 满足 R4 | 升为 **R4**（D8/D9 落点在 `service/common` ⇒ 按 §1.1⑥ 归**跨组件残留**，阻塞**域**不阻塞组件） | `comment/rpc.md:244`、`:264`（D8/D9 落点）· `message/rpc.md:1170-1183`（UNRESOLVED 均为跨域） |
| **13** | `article-api` = R2 | 新增 `check-surface-parity.py` 后**契约面有可复算守门**；归属本组件的必对齐项 = 0 | 升为 **R3** | `article/api.md:886`（唯一未闭项 `UNRESOLVED-002` 属跨组件） |
| **14** | `like-proto` / `user-proto` / `admin-proto` = R3；`follow-proto` = R3 | ① `like-proto` / `follow-proto` **无字段级 / wire 级仓内断言**（只有 `check-surface-parity.py` 的 rpc 方法集合）⇒ R3 的 ⑤ 不满足 ② `user-proto` / `admin-proto` 的降级理由**不成立**（二者有 `check-proto-static.sh` 的**逐字节**字段名/字段号/message 名 diff）⇒ 由 **#20** 回调为 R3 | `like-proto` / `follow-proto` 降为 **R2**；`user-proto` / `admin-proto` **维持 R3**（见 #20） | `component-coverage-matrix.md:63`；`follow/proto.md:78`；`docs/DRIFT/_cross/proto-verification.md:44` |
| **15** | 阶段一结论 = 「❌ 不满足（X-11 属安全 ⇒ 不属有限例外）」 | 方向正确，但**依据不足**且与 P0 映射不自洽 | 明确为 **❌ 阶段一未通过**，并给出**三类硬阻断**与**最短路径 + 工作量估算（≈7~12 人日）** | §8.3 / §8.4 |
| **16** | 域级聚合 ✅ 3 域（favorite / task / users） | 重判后：`comment`/`message` 升 ✅，`users` 因两个 proto 降 🟡，`like` 因 `like-common` 从未对照降 🔴 | **✅ 4 域**（comment / favorite / message / task）· **🟡 4 域**（article / follow / hot / users）· **🔴 1 域**（like）· 共享层 🔴<br>⚠️ **本行已被 #18~#20 修订**（`like` 不再是 🔴、`users`/`hot` 升 ✅）⇒ **以 #22 为准** | §四 + §8.1 |
| **17** | 「契约面 / gRPC 方法集合」结论只存在于一次性脚本 | **不可复算**（本轮审查的核心教训） | **新增落仓守门** `tools/difftest/check-surface-parity.py` ⇒ HTTP **72 ≡ 72** · gRPC **71 ≡ 71**，两个签字偏离域单列 | 本次提交；脚本含**前缀断言**与**空解析报错**两条防自误报设计 |
| **18** | `like-common` = **未评级 🔴**（理由：「无专属台账 ⇒ 从未与 Go 对照」） | 该理由**与仓内证据矛盾** —— `service/like/like-common/src/test/java/…/LikeErrorContractTest.java` 把 **29 个码值 + 29 条文案 + 未知码回落 + `CodeError` 格式与 JSON 序列化**逐条钉死，期望值**抄自 Go `errmsg.go:37-67`/`:69-99`/`:101-107`**（javadoc 写明）⇒ **已对照过**。**「无台账」≠「从未对照」** | 改为 **R3 / E3 / ✅**；「无专属台账」降级为**记录缺口** ⇒ §九 G-17（**不阻断阶段一**） | `LikeErrorContractTest.java:28-29`（Go 出处）· `:77-83`（29 条计数）· `:103-125`（文案与回落） |
| **19** | `hot-proto` = **R2 / E1 / 🟡**（理由：「无专属台账」） | 同上 —— `service/hot/hot-rpc/.../proto/HotProtoContractTest.java` 比对「**生成码的描述符**」与「**由 `generate.py` 从 Go 真源生成并冻结**的基准」（真源 sha256 记录在案）⇒ **字段级断言已存在且在版本控制内** | 改为 **R3 / E3 / ✅**；「记录位置不统一」（在 `hot/vs-common.md` 而非 `hot/proto.md`）⇒ §九 G-18 | `HotProtoContractTest.java:38-45` · `service/hot/hot-rpc/src/test/resources/proto-contract/{hot-proto-contract.expected.txt,generate.py}`（**`git ls-files` 命中**）· `docs/DRIFT/hot/vs-common.md:67` |
| **20** | `user-proto` / `admin-proto` = **R2 / E2 / 🟡**（理由：「无专属台账 + 消费方式未验 ⇒ R3 不成立」） | 降级理由**只对「消费方式」成立**，而那是**取证缺口**（§1.1 ⑥ 明列为**不计入**必相对齐项）；而 R3 的 ⑤ 已由 `check-proto-static.sh` 的**逐字节**字段名/字段号/message 名/rpc 签名 diff 满足（本轮实跑 EXIT=0） | 改为 **R3 / E3 / ✅**；「消费方式未验」⇒ §九 G-19（取证缺口，**不降 R**） | `docs/DRIFT/_cross/proto-verification.md:19`·`:24`·`:44`（「有网」档）· `tools/difftest/check-proto-static.sh`（本轮 EXIT=0） |
| **21** | §1.1「未评级」= 无专属台账；§1.3「✅」= 有可复算的判定命令（**两句都不可机械判定**） | 「无台账」被当成「从未对照」⇒ **把「记录在哪」伪装成「行为没对齐」**；「有可复算的判定命令」没说清**命令要断言哪一层** ⇒ 契约面守门也能冒充语义断言 | §1.1 未评级改为「**既无台账、也无任何仓内可复算的对照产物**」；§1.3 增补**分类型口径**（api/rpc/common：纯契约面**不满足** ⑤；proto：字段级 diff **满足** ⑤，`optional`/字段号硬编码属取证缺口） | §1.1 / §1.3；`docs/DRIFT/_cross/proto-verification.md:37-38`（proto 文件层即行为层） |
| **22** | 域级聚合：✅ 4 域 / 🟡 4 域 / 🔴 1 域；C-1 = 4/6；§6.3 =「20 项」 | #18~#20 改变了 3 个组件的状态 ⇒ 域级与 C-1 必须重算；§6.3 的命令数实际是 **21**（v2 写 20 ⇒ 数错了） | 域级：**✅ 6 域**（comment · favorite · hot · message · task · users）· **🟡 3 域**（article · follow · like）· **🔴 0 个微服务域** ＋ 共享层 `service/common` **🔴**；C-1 = **5/6**；§6.3 = **22 项命令 / 14 行**，并补列本轮**未跑的 3 个**守门 | §四 + §8.1（唯一聚合口径）· §6.3 实跑记录 |
| **23** | §七 P0 汇总写「**✅ 4 条**（#6 #7 #9 …）」，且下一行写「**v2 下调了 3 条**（#1 #2 #3 #4）」 | **两处都算错**：列了 **3** 个编号却写「4 条」；列了 **4** 项却写「3 条」⇒ 4+4+2+0 = **10 ≠ 9**（P0 只有九条） | 订正为 **✅ 3 条**（#6 #7 #9）· **🟡 4 条**（#1 #2 #3 #4）· **🔴 2 条**（#5 #8）· **⚫ 0 条** ⇒ 合计 **9**；并写明「v3 下调了 **4** 条」 | 本轮**自检脚本的「P0 汇总」检查**（逐条读 §七 的状态列求和，再与声明比对）⇒ **机器抓出，非人工复查** |
| **24** | 本文件的四层状态、计数、聚合口径全靠**人工**互相对照 | 人工对 39 行 × 9 列的矩阵 + 4 层状态 ⇒ **必然漂移**；本轮已实证：v2 的 P0 汇总算术错误、v3 初稿的 §6.3 命令数错误 | **新增落仓守门** `tools/difftest/check-phasegoal-consistency.py`（**15 组检查**：表格列数 · A-1↔A-2 集合 · 38 组件构成 · R/E/状态取值域 · **R↔状态耦合** · **判定命令路径存在性** · **✅ ⇒ 有可复算命令** · B 卡↔A-2 逐个 · **域状态 = MIN 聚合** · C-1 · 编号连续性 · **P0 汇总** · 测试条数 · §6.3 命令数 · 无未来日期）⇒ **EXIT=0** | 本次提交；**该脚本已在本轮抓出 §23 与 §22 两处错误** ⇒ 证明「可机械执行」不是口号 |
| **25** | **G-9 未闭合**（只比活库不比仓里的 `.sql`）⇒ 「迁移改了没人重跑」不可见；且 `--indexes` 未开 ⇒ 索引/约束/触发器全未查 | ① 两处**证据缺口**（不是「结论错」）② **实测发现真漂移**：`tools/difftest/ddl-parity.sh` 的 RAG 侧迁移清单是**写死的** ⇒ favorite 域新增的 `V7__align_favorite_outbox_time_columns_with_go.sql` **不在清单里** ⇒ 那份活库**永远缺 V7 的效果**，而 Go↔活库 对拍**照样绿**（看不见） | ① `check-ddl-static.py` 新增 **`--check-source`**：**把仓里的 `.sql` 在一个干净临时库里原样重放**（**让 PG 自己解析** ⇒ 与生产 Flyway / SchemaInitializer 同一条路径）⇒ 再与活库逐项对比（表/列/列属性/可空性/DEFAULT/索引/约束/触发器）② 迁移清单改由**目录扫描**产生（`--print-rag-sql`，`ddl-parity.sh` 复用同一产生点）⇒ **清单落后于仓**从结构上不可能再发生 | **实测**：9 域源码↔活库**全部一致**（hot = N/A）· **变异必红**（`favorite_item.target_id` 255→200 ⇒ 精确报 1 处，差异数 2→3）· 变异已还原（sha256 逐字节回到变异前）· 写死清单漏 `V7` **已修** ⇒ **P0 #3 由 🟡 上调 ✅** |
| **26** | P0 #5 判「不满足」 | README 21 B / 无 `.env.example` / compose 缺 `:?` / `Infrastructure.md` 描述旧架构 —— **四项缺口** | R1 已补齐并**实跑**（README **9,815 B** · `.env.example` **56 变量** · compose 缺变量 **EXIT=1**）⇒ **由「不满足」上调为「待实跑起栈」**（🟡）<br>⚠️ **仍欠 `./manage.sh up` 真起一次栈** ⇒ 排在 R2 收口后（避免重建 `Sea-user-api` 干扰在跑的探针）⇒ **未实跑 ⇒ 不得写 ✅** | R1·T-1.1~T-1.4 · commit `0bcd353` |
| **27** | X-11「敏感配置链」= 🔴 未达标（10 处 yaml 带明文默认值） | P0 #8 已闭合（10 个 api/rpc 模块的 **JWT 验签密钥链**：去掉明文默认值 + **fail-closed 守卫** + **未解析占位符字面量**判据 + 4×4 变异精确转红 + `check-no-prod-secrets.py` EXIT=0）⇒ **矩阵 C 与 P0 口径不同步** | X-11 **🔴 → 🟡**：JWT 链**已闭合**；残留 = **9 个 rpc 模块的部署凭据默认值**（`${POSTGRES_PASSWORD:password}` / `${MINIO_SECRET_KEY:minioadmin}` / `${*_AUTHORITY_TOKEN:}`）⇒ 已登记 **`F-6`/`A-5`**（**C 类** ⇒ 按 §1.1 ⑥ 第 4 条**不计入**）⇒ ⚠️ **归属订正为域内**（原写「归 G4 共享层」是错的） | `docs/PhaseGoal-judge.md` §七 P0 #8 行 · `check-no-prod-secrets.py` EXIT=0 |
| **28** | X-7「错误码契约」= 🟡（只覆盖 **2/9** 域）· X-10「proto 契约」= 🟡（落仓静态守门只覆盖 **4/10** 域） | **G-7**（错误码 9/9 域落仓 + 变异精确转红 + 白名单可审 + 退出码口径统一）与 **G-6**（proto 静态守门扩到 10 域 + 变异必红）已闭合 ⇒ 矩阵 C 两行口径过期 | X-7 **🟡 → ✅**（9/9 域）· X-10 **🟡 → ✅**（10/10 域）<br>⚠️ **X-7 的证据强度 = L1/L2**（双侧同时跑的**静态**对照 ⇒ **不是双侧活体**）⇒ **不得据此宣称「已对齐」**；⚠️ **X-10 的效力边界不变**（`optional` presence / 字段号硬编码 / Jackson 配置 ⇒ §九 **G-19**，**不降 R**） | 各域 `tools/errmsg-probe/<域>/README.md` · `tools/difftest/check-proto-static.sh` |
| **29** | §九 G-1 / G-2 / G-8 / G-10 / G-11 / G-12 / G-14 / G-16 / G-20 仍记「🟡 否（未闭合）」 | 本轮已逐条闭合：① **G-1** follow 双侧探针落仓（20 条三方逐字节）② **G-2** like 双侧探针落仓（EXIT=0 + 3 变异）③ **G-8** 9 域 `--indexes` 实跑 ④ **G-10** jar 守门改为「未构建 ⇒ 判红」+ 永远打印「已检查 N/M」⑤ **G-11** 共享索引的 favorite 订正 ⑥ **G-12** 7 份台账补「当前态指针」⑦ **G-14** like U-01~U-08 逐条结论 ⑧ **G-16** 基数订正 ⑨ **G-20** `_cross/proto-verification.md` 订正 | **9 条销号**（各带证据强度与日期 2026-10-29）⇒ **仍开放**：**G-17**（`like-common` 无专属台账 = **记录缺口**，非行为缺口）· **G-19**（proto 消费方式取证 = **取证缺口**，允许延到 R5） | 各台账条目级最新状态 + 本轮提交 |
| **30** | §十一 D-01（空签名密钥可起栈）· D-02（README 21 B + 无 `.env.example`）仍列 🔴 | 两项已闭合但 §十一 未同步 | **D-01 / D-02 ⇒ ✅ 已闭合**（各带「怎么闭的 / 实跑证据 / 残留」）<br>⚠️ **D-02 残留**：**P0 #5 仍为 🟡** —— **尚欠「按 README 真起一次栈」（`./manage.sh up`）** ⇒ **未实跑 ⇒ 不得写 ✅** | P0 #8 / P0 #5 行 |
| **31** | §三 A-2 多行的「判定命令」仍写「探针在 `/tmp` ⇒ 仓内无双侧对拍入口 ⇒ R4 不成立」（follow-api · like-api · users-common · hot-common）· §五 X-2 仍写「默认不开 `--indexes` / `--check-source` ⇒ 全未查」 | 本轮探针陆续落仓 ⇒ 这些行的前提**已过时** | 本轮**逐行订正**（A-2 的 R/E/状态/权威来源/仍开放项 + X-2 的判据升级段）⇒ **每行都以「条目级最新状态」为准**（§1.5） | 各域台账 + `tools/` 落仓探针 |

| **32** | **P0 #5 = 🟡**（「按 README 真起一次栈」未实跑）· **P0 汇总 v6 = ✅8/🟡1** · **X-12 = 🟡** | **真起栈从未跑过** ⇒ 三处口径全部悬空 | ✅ **已全部闭合**（commit `0b707b39`）：**`./manage.sh up` ⇒ EXIT=0**（7 容器全起）＋ `register`/`login` **HTTP 200**（login 返真实签名 JWT）＋ 带/不带 token 的 **200 / 401** ＋ `psql` 直查 **4 张表 + 1 行** ⇒ ⇒ **P0 #5 ✅ · P0 汇总 v7 = ✅ 9 / 🟡 0 / 🔴 0 · X-12 ✅**<br>⚠️ **顺带修掉 compose 3 处「新机器必然起不来」的缺陷**（`dockerfile` 大小写 · `cache_from/cache_to` · **PG18 挂载点**） | 本轮实跑 ＋ commit `0b707b39` |
| **33** | §二 A-1 的 `tests` 列 = **2,860**（2026-10-28 逐组件快照）· §七 P0 #6 的「全量回归 2,860 条 / 42:41 min」 | 二者都是**旧快照**（且「全仓条数」与「A-1 求和」被混为一谈） | ✅ **已按 2026-10-29 实跑订正**：全量回归 **3,294 条 / 0F / 0E / 4S**（52 模块 · 28:48 min）⇒ **A-1 的 39 行 `tests` 列已逐行重算并回填**（脚本 `tools/doc-ref/refresh_a1_matrix.py` ⇒ **口径 = A-1 表注**，**文件数逐行与旧快照一致** ⇒ 差异即真实代码增长）⇒ ⇒ **一致性门第 13 组检查「A-1 求和 == 文档声明」复绿** | 本轮实跑 ＋ `tools/doc-ref/refresh_a1_matrix.py` |
| **34** | §五 C-3 写「❌ 不满足 X-11 / X-12」· §八 8.3 写「❌ 不满足」 | X-11 / X-12 的硬阻断已消解，但 **C-3 与 8.3 未同步** ⇒ 同一份文件里三个章节互相打架 | ✅ **已订正**：**C-3 = ✅ 满足**（12 条逐条无 🔴）· **8.3 = 「通过」** · **8.4 = 「✅ 阶段一通过」**（逐条对应 S1-1 ~ S1-6 · 每条带证据强度与可复算入口）⇒ **v3 ⇒ v4** | 本轮复判 |
| **35** | §七 P0 #5 = **✅ 满足**（「按 README 真起一次栈」已实跑）· §八 C-4 = 「P0 汇总 ✅ 9/9」· §十·二 冻结基线第 4 项同写「P0 = ✅ 9/9」 | **证据覆盖面不足被当成了判据满足** —— 「按 README 真起栈」这条证据链**只覆盖 users 域**：实测 `docker-compose.yaml` 只有 **7 个 service**（应用服务仅 `user-rpc` / `user-api`）＋ `manage.sh:18` `APP_SVCS="user-rpc user-api"` ⇒ **其余 9 域的 api + rpc 未在「按 README 起栈」的形态下被实测过** ⇒ 按 §七 的修订原则「**证据不足必须显式标出，不得用邻近证据顶替**」⇒ 不得写 ✅ | 🟡 **P0 #5 = 部分满足**（**性质 = 证据覆盖缺口，不是产品缺陷** ⇒ 补齐 9 域起栈形态证据即可闭合，**不改产品代码**）· §八 C-4 的汇总改为 **✅ 8 / 🟡 1 / 🔴 0 / ⚫ 0** · §十·二 冻结基线第 4 项同改 · ⇒ **「P0 九条全满足」不成立** | **证据（2026-10-29 本轮实测）**：`docker-compose.yaml`（7 个 service）＋ `manage.sh:18`；**判定命令**：`grep -c '^  [a-z-]*:' docker-compose.yaml` 与 `grep -n 'APP_SVCS=' manage.sh`；**闭合路径**：`docs/RoundPlan/round-01/README.md`（`P1-T01`~`P1-T05`） |
| **36** | §五 X-6 = **✅ 探针 PASS** ⇒ §八 C-3 = 「12 条无 🔴」⇒ §8.3 = 「通过」⇒ §8.4 = 「✅ 阶段一通过」 | **口径反** —— `check-deadline-downstream.py` 是**漂移形态门**：它 PASS 的含义是「**漂移形态与已登记的一致**」，**不是「判据满足」**；把 PASS 当成「已对齐」⇒ 整条 C-3 / 8.3 / 8.4 链都在一条错误的读法上 | 🔴 **X-6 = 不满足**（**E65 = A 类**）⇒ **C-3 破**（12 条中 1 条 🔴）⇒ §8.3「通过」不成立 ⇒ §8.4 = **「❌ 阶段一未通过」** ⇒ §十·二 冻结基线**解除**；**同时保留**：X-6 的**判据原文与判定命令一字未改** | **证据（2026-10-29 本轮实跑 `python3 tools/difftest/check-deadline-downstream.py` ⇒ EXIT=0）**：Go = `{'failed_fast': 'true', 'code': '4', 'outgoing_carrier': 'present_expired', 'sql_carrier': 'present_expired'}` · RAG = `{'failed_fast': 'false', 'code': '0', 'outgoing_carrier': 'absent', 'sql_carrier': 'present_expired'}` ⇒ **`outgoing_carrier` 两侧不等**<br>🟢 **【2026-10-30 第三次订正 · 本行那条「口径反」的前提已变】** —— 守门**现在**是**等值门**（漂移名单已清空 ⇒ 四键全部 parity）⇒ ⇒ **PASS 的含义已变成「两侧真的相等」** ⇒ ⇒ ⇒ 「把 PASS 当成『已对齐』」这个错法**已不成立**；⇒ ⇒ ⇒ **X-6 仍 🔴 的理由已换成「客户端硬上界 2000ms 未启用」**（继承面已闭合）；**台账**：`docs/DRIFT/global/_README.md` 的 `E65` + `docs/DRIFT/global/vs-common.md` ⇒ **A 类**（落点 `service/common` ⇒ G4 写权面）；**闭合路径**：`docs/RoundPlan/round-02/README.md`（`P2-T01`~`P2-T06`） |

---

## 十·二、🧊 阶段一冻结基线（`twoStage.md` §5.7 的 9 项 · 2026-10-29）

> **冻结日期**：2026-10-29　**判定结论**：✅ **阶段一通过**（§8.4）
> 🔴 **冻结已于 2026-10-29 复判时解除**（**原值保留为历史，不删改**）—— 原冻结的判定结论「✅ **阶段一通过**」已被 §8.4 的改判推翻（现为 **❌ 阶段一未通过** · 两条 A 类阻断：`E65`/X-6 与 `P0 #5` 证据覆盖缺口）⇒ **冻结与结论必须一致** ⇒ 冻结失效 ⇒ **下方 9 项全部标记为「已失效」**（**保留原文，不删改**）⇒ ⇒ **重新冻结以 Round 7 的最终验收结论为准**（见 §8.4 与 `docs/RoundPlan/round-07/README.md`）。
> ⚠️ **解除原因 / 解除日期 / 解除人**：① **原因** —— §8.4 的结论被 #35 / #36 两条改判推翻（详见 §十 变更记录）· ② **日期** —— 2026-10-29 · ③ **执行者** —— 阶段一复判轮（合并收口执行者 · 主线）。
> **基线版本标识**：分支 **`do-my-own-gh`** · 判定基线文件 = **本文件（v5）**
> **代码状态提交**：`0b707b39`（compose 三处缺陷修复 · P0 #5 的实跑依据）＋ 其后的文档收口提交（见 `git log`）
> ⚠️ **冻结的是「阶段一的判定结论」，不是「工程的成熟度」** —— §5.7 原文：「阶段 1 通过只代表简历展示目标已经达到，不代表工程成熟度审查已经完成。」

| # | §5.7 要求的冻结项 | 冻结内容（2026-10-29） |
|---|---|---|
| **1** | **实际代码状态及版本标识** | 分支 `do-my-own-gh`（主线）；**52 个 Maven 模块**（10 api + 10 rpc + 8 common + 10 proto ＋ 父 pom 等）；**测试类 480 个**；代码状态提交 `0b707b39` ＋ 文档收口提交（`git log --oneline`）；**Go 真源** = `Sea-RideTheWind_golang`（冻结只读 · 2026-10-18 起） |
| **2** | **构建和测试结果** | `mvn -o -B -DargLine=-Xmx2g clean verify` ⇒ **BUILD SUCCESS**（52 模块 · **28:48 min**）· **Tests 3,294 / Failures 0 / Errors 0 / Skipped 4**（**29 个含测模块**；逐模块条数 = A-1 的 39 行 `tests` 列，求和 **3,294**）；**27 个守门逐个实跑取真实退出码**（§六 6.3）⇒ **全绿** |
| **3** | **核心业务链路的验收结果** | **对外契约面**：HTTP 端点 **Go 72 ≡ RAG 72** · gRPC 方法 **Go 71 ≡ RAG 71**（10 域 · `check-surface-parity.py` EXIT=0）<br>**双侧活体（L4）**：article 3 场景（**两侧播种同一行**）· like 阶段 A 9 case + 阶段 B **970 B sha256** · follow **20 条三方逐字节** · users **39 步**（`unexpected=0` · 变异 **6/6 精确转红**）· users-common 176 → 260 · favorite-rpc **25 案 wire 层逐字节** · task **44 条全 MATCH** · comment **23 步** · message · hot 29 例<br>**失败路径矩阵 227 条**（follow 62 · article 104 · hot 28 · task 33）· 变异 **13/13 精确转红**<br>**端到端**：`./manage.sh up` **EXIT=0** ＋ `register`/`login` **HTTP 200 + 真实签名 JWT** ＋ 鉴权分支 **200 / 401** ＋ `psql` 直查 **4 张表 + 1 行** |
| **4** | **P0、P1、P2 的最终状态** | **P0 = ✅ 8 / 🟡 1 / 🔴 0 / ⚫ 0**（v8 汇总 · **P0 #5 = 🟡** 证据覆盖缺口 · **已失效** —— 本行是 2026-10-29 冻结时的历史快照）<br>**P1 = 🟡 部分满足**（维度 3 proto 消费方式取证 · 维度 7 容量/SLO · 维度 8 rpc 层指标出口）⇒ **P1 不阻阶段一**，归阶段二<br>**P2 = ⬜ 阶段一不适用**（`PhasedGoal.md` §2.1：生产流量 / SLO / 灾备演练**不自动阻止阶段 1**）⇒ 归阶段二建设项 |
| **5** | **全部已知缺陷和未验证风险** | **真缺陷（A 类）= 0**；**残留真缺陷 1 条** = `DRIFT-CF-018` 的 **favorite 域半边**（进程无法优雅关闭 ⇒ **P2/C 类** ⇒ 派单 **R5-F7**）<br>**证据缺口**：`G-19` proto 消费方式取证（派单 **R5-F4**）· `G-17` like-common 专属台账 · `U-01` 的 **DC → 第三仓** 一段（**仓外** ⇒ 本机不可核验）<br>**环境风险**：本机 Docker 默认镜像源拉大 blob 会 EOF（**换源可绕**）· `docker compose build` 与 host 端 mvn 共用 `~/.m2` 的并发写冲突（**已通过删掉无效 cache 配置消除**）<br>**共享面风险**：共享 Nacos 服务发现面会让**并发探针互相污染**（`PIT-GLOBAL-068` ⇒ 报 `INTERNAL`/`5001` 是**已知串扰**，不是缺陷 ⇒ 派单 **R5-F5**） |
| **6** | **完整的差异与暂缓项清单** | [`docs/Stage1/05-差异与暂缓项清单.md`](Stage1/05-差异与暂缓项清单.md) —— **51 条**（域级 11 ＋ 共享层 **25** ＋ 本轮 15），**每条 11 字段**（机器核：按**竖线**切分计数 = 11 ⇒ 全绿）；定级 **A 0 · B 1 · C 21 · D 6 · 证据缺口 1**<br>共享层条目级清单 = `docs/DRIFT/common/alignment-2026-10-23.md` §十三·一（**23 编号 + 5 未编号 = 28**）<br>DDL 残留 **51 处**（9 域 · **A 类 0**）= `docs/DRIFT/_cross/ddl-type-divergences.md` §四 |
| **7** | **阶段二任务及依赖** | `docs/twoStage.md` §六：**§6.1 重新建立最终改造范围**（含「**不得假定阶段一的暂缓清单已经包含所有缺口**」）· **§6.2 业务语义完整性审查**（十二维度）· §九 **故障矩阵**（生产流量 / SLO / 灾备演练）<br>**依赖**：阶段二以**阶段一冻结基线（本节）**为输入 ⇒ **先冻结、后阶段二**<br>**另**：R5 的 **9 张派工单**（`docs/RoundPlan/Round-5.md` §三）—— **其中 5 条 C/D 类 ⇒ 登记不修**；**R5-F1 / R5-F2**（B 类部署凭据默认值）与 **R5-F7**（favorite 半边）是**唯一需要改行为**的三张 |
| **8** | **尚未处理的原始业务行为** | Go 真源侧**范围外**的三域（`knowledge` / `points` / `security`）⇒ **不是待办，是决策**（AGENTS.md §一）<br>`like` 域 `DRIFT-LIKE-003` 的 **C 段（暴露形态）** ⇒ 已改判 **C 类暂缓**（`S1-LIKE-001`）⇒ **行为未对齐、已登记**<br>共享层 **28 项**中 **C 21 · D 6 · 证据缺口 1** ⇒ **行为未对齐、已逐条定级**<br>DDL 残留 **51 处**（C 23 · D 28）⇒ **行为未对齐、已定级**<br>`G-12 遗留 15 处`**引用指错`**（D 类 · 文档卫生 · 派单 R5-F6） |
| **9** | **下一次执行应从哪里开始** | **（已失效 —— 2026-10-29 复判后改由 `docs/RoundPlan/_INDEX.md` 的 7 轮计划承接）** **原第一优先**：`docs/RoundPlan/Round-5.md` §三 的 **R5-F1 / R5-F2**（各域取消 datasource 口令 fallback ⇒ B 类残留清零 · **域侧可自修**）与 **R5-F7**（favorite 域半边 ⇒ 进程可优雅关闭）<br>**第二优先**：**R5-F9**（task 域台账两处过期数字 · 机器可判）→ **R5-F3**（G4 登记 `DRIFT-USERS-LAT-001`）→ **R5-F5**（探针 Nacos 隔离）→ **R5-F4**（G-19 取证）→ **R5-F6**（G-12 遗留 15 处）<br>**随后**：**阶段二**（`docs/twoStage.md` §六）—— 先 **§6.1 重新建立最终改造范围**，**不得假定阶段一的暂缓清单已经包含所有缺口**<br>**判据**：每轮完成后 `docs/RoundPlan/_INDEX.md` §五 状态表按「该轮停止点的验收命令都实跑过且全绿」更新 |

---

## 十一、未解决问题清单（四类分开）

### 11.1 🔴 真实实现缺陷（代码/配置层面有确凿差异，**必须修**）

| 编号 | 问题 | 落点 | 影响 | 归属 |
|---|---|---|---|---|
| **D-01** | ~~**空签名密钥可起栈**~~ ⇒ ✅ **已闭合**（2026-10-29） | 10 个 api/rpc 的 `application.yml` + `docker-compose.yml` | **token 可伪造** ⇒ P0 #8 已 ✅ | ✅ **已闭合**：10 个 api/rpc 模块去掉明文默认值 + **fail-closed 守卫**（含**未解析占位符字面量** ⇒ 堵住「缺变量时绑到字面量 ⇒ HMAC 密钥公开 ⇒ token 可伪造」）+ 4 模块各 4 条变异精确转红 + compose **6 处 `:?`**（缺变量 ⇒ 拒绝渲染，实测 EXIT=1）+ `check-no-prod-secrets.py` EXIT=0<br>⚠️ **残留（已登记 · C 类 · 不阻断）**：9 个 rpc 模块的**部署凭据**默认值 ⇒ `F-6`/`A-5` ⇒ **归属订正为域内**（原写「归 G4」是错的） |
| **D-02** | ~~**README 21 字节 + 无 `.env.example` + `Infrastructure.md` 描述旧架构**~~ ⇒ ✅ **已闭合并由 🟡 上调**（2026-10-29） | 仓库根 | **无法按说明构建启动** ⇒ P0 #5 🔴 | ✅ **已闭合**：README **21 B → 9,815 B**（照做说明 · 真实端点 `/user/v1/login` + 真实外部网络 `Sea-TryGo`）· `.env.example` **新建（56 变量）** · compose 补 **`:?`**（**实测** 缺变量 EXIT=1 / 设变量 EXIT=0）· `Infrastructure.md` 按现状订正（⚠️ **真实路径在仓库根**，`docs/FileTree/Infrastructure.md` **从来不存在**）<br>⚠️ **P0 #5 仍为 🟡（2026-10-29 复判后）**：**这一条不是 D-02 未闭合** —— D-02 的四项缺口（README / `.env.example` / compose `:?` / `Infrastructure.md`）**已全部闭合且仍有效**；**🟡 的真实来源是「证据覆盖面」**：`./manage.sh up` 的应用服务只有 `user-rpc` / `user-api`（`manage.sh:18`）⇒ **其余 9 域未在「按 README 起栈」的形态下被实测过** ⇒ **性质 = 证据覆盖缺口（补证据即可闭合，不改产品代码）** ⇒ ⇒ **P0 #5 = 🟡 · A 类**（见 §十 变更记录 **#35**） |
| **D-03** | **`like-rpc` 事实投递链路是 6 个 4 行骨架**（`DRIFT-LIKE-003`） | `service/like/like-rpc` | ⚠️ **2026-10-29 订正**：台账原述「6 个 4 行骨架」**已实测过时** ⇒ **三段拆分**：**A 投递链路已闭环** · **B 权威读语义 Java 化已闭环** ⇒ **仅剩 C 暴露形态待拍板**（⇒ 「有意不碰」的理由已从「scope 过大」变为「**等部署形态拍板**」） | like 域（**待 Jason 拍板 `003-C`**） |
| **D-04** | **`like-rpc` 双失败时少传 error**（global `E60`，**真漂移**，待 like 域 owner 接手） | `service/like/like-rpc` | ✅ **已销案**（2026-10-29：`LikeTaskProgressPublisher.publish` 三段式分流 ⇒ 真日志 `error_code=5011` ⇒ 已修 + 已守门 + 已实跑 4/4 绿 ⇒ **E60 销案**） | like 域（**已完成**） |
| **D-05** | **`follow-rpc` 的 5 个 `follow_rpc_*` 指标无任何抓取出口**（Go 有 DevServer `/metrics`） | `service/follow/follow-rpc`（同款 5 处：favorite/message/like/user-user/user-admin） | 可观察差异 ⇒ `follow-rpc` 降 R2 | 用户 2026-10-24 定「本轮不落地」⇒ 需重新拍板 |
| **D-06** | **共享层 28 项未修**（`DRIFT-CF` 15 ＋ `DRIFT-LOG` 7 ＋ `DRIFT-SF` 2 = **24 条编号** · 本轮修 `DRIFT-CF-018` ⇒ **23 条未修** ＋ **5 项未编号**；**`DRIFT-COMMON` 已归零**：`-005` 已修 · `-006` 的代码已删）＋ 10 条外部尾巴 | `service/common` | ⚠️ **2026-10-29 R3 轮订正**：**28 项已逐条定级**（**A 类 0 · B 类 0 · C 类 21 · D 类 6 · 证据缺口 1**）⇒ **域内硬阻塞 = 0** ⇒ 按 §1.1 ⑥ 第 4 条（C 类阶段二暂缓项）**不计入**（Jason 2026-10-29 裁定）⇒ ⇒ **不再阻塞全仓**；**`DRIFT-CF-018`（`Dispatcher` 阻塞重载交不出句柄 ⇒ 进程停不下来）本轮已修**（含变异验证 3 组精确转红）；共享层 R 档 **R2 → R3**（状态 🟡，非 ✅）<br>⚠️ 残留 = **5 项未编号 ＋ 10 条外部尾巴** ＋ 28 项里的 **D 类 6 ＋ 证据缺口 1**（**均属阶段二 / 证据缺口**，不阻断） | **G4 共享层单写权** |
| **D-07** | **comment 域整张 `report_record` 表「仅 RAG 有」的 DDL**（`V5__create_comment_report_record.sql` = **8 列** ＋ 复合唯一索引 `idx_user_comment_report`） | comment 域迁移脚本 | DDL 两侧不对齐 | comment 域（**已签字** · `SD-2026-10-24-CAND-02` ⇒ **不是缺陷，不得再「修回去」**） |
| **D-08** | **`like` 域首轮 8 条错误断言 A~H 仍全部在位**（源码注释里的假断言） | `service/like/like-rpc` 源码注释 | 文档性缺陷（会误导后续读者） | like 域 |

> ⚠️ **本轮从本类移出 1 条**（v2 原 D-08「`like-common` 从未与 Go 对照」）—— 该判定被仓内证据推翻 ⇒ 改列为**证据缺口** G-17（**记录缺口**，非行为缺口）。**移出 ≠ 销号** ⇒ `like-common` 仍需补专属台账。

### 11.2 🟡 证据不足（结论可能对，但今天不可复算 / 未取证）

见 [§九 证据缺口清单](#九证据缺口清单) G-1 ~ G-20（**20 条**）。
**最关键 5 条**：G-1（follow 探针未落仓）· G-2（like 探针已消失）· G-3（article 零双侧）· G-4（users-common 探针已消失）· G-13（4 域无失败路径矩阵）。
**本轮新增 4 条（G-17~G-20）全部是「记录/取证缺口」，不改变任何组件的行为判定** —— 其中 G-17（`like-common` 无专属台账）**推翻了 v2 的 🔴 判定**（§十 #18）。

### 11.3 ⚫ 环境受阻（**本轮为零** —— 不要再用它当免责）

| 编号 | 事项 | 实际状态 | 解除条件 |
|---|---|---|---|
| **E-01** | `article` 域 Go 侧需 `etcd:2379` | `Sea-etcd` 容器 **Up 8 天（healthy）**，但**宿主 `2379` 未做端口映射** ⇒ 宿主直连不通 | **映射端口即可**（不是「环境不可得」） |
| **E-02** | Kafka / Redis / Nacos | ✅ **全部在跑且可达**（59993 / 57379 等 8 个 / 8848） | — |
| **E-03** | 生产环境 / 真实流量 ⇒ **L4 不可凭源码给** | 无 | 阶段 2 才要求 |

> ⚠️ **v1 的「本机无 Kafka/Redis/Nacos ⇒ 服务发现故障/实例下线/优雅停机无运行时证据」整条已删除** —— 与实测矛盾（见 §10 变更 #3）。

### 11.4 ⬜ 已签字偏离（**决策不是缺陷** —— 不重复质疑、也不得「顺手修回 Go 行为」）

| 编号族 | 条数 | 内容 | 依据 |
|---|---|---|---|
| `SD-` 族 | **40 条** | 含 HTTP path / method / 表名映射 · admin-api `OperatorId` 严格校验 · user-api `uid` 统一拒绝 · message-api `affected` 字符串 | `docs/Intentional_Deviations/_INDEX.md` |
| `N-x` 族 | **3 条**（N-1 / N-4 / N-5） | users/admin 域拍板 | `docs/Intentional_Deviations/users/admin/_shared.md` |
| `A/C/D-x` 族 | **5 条** | `service/common` 逐行审计后无法消除的偏差 | `docs/Intentional_Deviations/common/_signed-deviations.md` |
| **合计已签字** | **48 条** | | |
| `CAND` 族 + `like/api.md` 候选 | **12 条未签字**（⚠️ **2026-10-29 机器复算订正**：原写 18 ⇒ 现值 12；`CAND` 族 14 条里 **4 已签字 + 1 撤销** ⇒ 未签字 9 条） | 含 `CAND-16`（users 8 处 DEFAULT）· `CAND-17`（`users_user.status` 可空性）· `like` 域 3 条 | `docs/Intentional_Deviations/_INDEX.md` |
| **本轮新增待签字** | 2 条 | `DRIFT-HOTRPC-006`（已取证未签字）· `DRIFT-HOTAPI-031`（框架限制，候选 `CAND-10` 未签字） | hot 域台账 |

> ⚠️ **签字偏离是决策，不是缺陷** ⇒ 对拍看到它们变红，**先确认是不是签字项，不要当新回归去「修」**。

---

## 十二、命令清单（全部可复算 · 2026-10-28 实跑）

```bash
# ── 全量回归（基线推进后必须带 clean）──────────────────────────────
mvn -o -B -DargLine=-Xmx2g clean test

# ── 逐模块条数（权威读法 = surefire XML 求和）─────────────────────
python3 - <<'PY'
import glob,re
tot=0
for f in glob.glob('service/**/target/surefire-reports/TEST-*.xml',recursive=True):
    m=re.search(r'<testsuite[^>]*\btests="(\d+)"',open(f,encoding='utf-8',errors='replace').read(4000))
    if m: tot+=int(m.group(1))
print("TOTAL",tot)
PY

# ── ⭐ 对外契约面（本次新增落仓 · v1 的结论此前不可复算）──────────
python3 tools/difftest/check-surface-parity.py            # 全 10 域
python3 tools/difftest/check-surface-parity.py --domain article

# ── 守门全跑（退出码先落变量再打印；别用 `printf "$(cmd)" "$?"`）────
for g in tools/difftest/check-*.py tools/check-ledger-*.py tools/check-agents-budget.py; do
  out=$(python3 "$g" 2>&1); rc=$?
  printf "%-52s EXIT=%s\n" "$(basename "$g")" "$rc"
done

# ── ⭐ 本文件自身的内部一致性自检（矩阵 ↔ 聚合 ↔ 计数 ↔ 路径存在性）──
python3 tools/difftest/check-phasegoal-consistency.py

# ── ⭐ 由本文件生成/校验 per-lane 分摊矩阵（docs/PhaseGoal/g{1..4}.md）──
python3 tools/difftest/check-phasegoal-lanes.py --write   # 重新生成 4 份 lane 矩阵
python3 tools/difftest/check-phasegoal-lanes.py           # 只校验（不改文件）⇒ EXIT=0

# ── DDL 逐域（裸跑只查 users 域！索引/约束/触发器需 --indexes）────
python3 tools/difftest/check-ddl-static.py --domain <域>
python3 tools/difftest/check-ddl-static.py --domain <域> --indexes
# ── ⭐ G-9：加 --check-source ⇒ 另核「仓里的 .sql ↔ 活库」──────────
python3 tools/difftest/check-ddl-static.py --domain <域> --indexes --check-source
python3 tools/difftest/check-ddl-static.py --domain <域> --print-rag-sql  # 迁移清单（单一产生点）

# ── 双侧活体对拍（按域 · 覆盖范围见 §六）────────────────────────
GO_REPO="/Users/pr/Documents/java&golang/Sea-RideTheWind_golang" bash tools/errmsg-probe/run-parity.sh   # article 错误码
bash tools/errmsg-probe/favorite/run-parity.sh                 # favorite 错误码
bash tools/semantic-probe/run-parity.sh                        # message 解析语义
bash tools/json-body-probe/run-parity.sh                        # hot JSON body
bash tools/breaker-probe/run-parity.sh                          # shared-layer 熔断
bash tools/difftest/comment/run_diff.sh                         # comment 23 步矩阵
bash tools/difftest/run.sh                                     # users 双侧（diff.py + matrix.py）
bash tools/api-envelope-probe/favorite/run.sh                  # favorite 响应字节
bash tools/proto-wire-probe/favorite/run.sh                    # favorite wire 字节
bash tools/difftest/run-task-http-parity.sh                    # task HTTP 44 步
bash tools/difftest/run-task-grpc-xlang.sh                    # task 跨语言 gRPC
bash tools/difftest/run-task-kafka-e2e.sh                      # task Kafka 端到端
bash tools/difftest/run-hot-rpc-difftest.sh                    # hot-rpc gRPC 逐字节

# ── ⭐ 双侧活体探针（2026-10-29 落仓 · 各域私有目录 · 按域取）─────
bash tools/api-full-parity-probe/article/run.sh               # article 双侧（3 场景 + 两侧播种同一行）
bash tools/api-full-parity-probe/article/mutate-parity.sh     #   变异 ⇒ 精确转红
bash tools/api-full-parity-probe/like/run.sh                  # like 双侧（A 9 case / B 970 B / C E60）
bash tools/api-full-parity-probe/like/mutate-parity.sh        #   变异 ⇒ 精确转红
bash tools/api-full-parity-probe/follow/run.sh                # follow 双侧（20 条三方逐字节）
bash tools/api-full-parity-probe/users-common/run.sh          # users-common（176→260 可复算）
bash tools/api-full-parity-probe/users-common/mutate-parity.sh#   变异 ⇒ 精确转红
bash tools/api-full-parity-probe/favorite-rpc/run.sh          # favorite-rpc 整域（wire 层 25 案）
bash tools/api-full-parity-probe/favorite-rpc/mutate-parity.sh#   变异 ⇒ 精确转红
bash tools/errmsg-probe/<域>/run-parity.sh                    # 错误码逐字节（9 域：article/favorite/hot/
                                                          #   like/users/comment/follow/message/task）
bash tools/errmsg-probe/<域>/mutate-parity.sh                 #   退出码 0=全部精确转红=通过 1=守门失效 2=环境问题
bash tools/proto-wire-probe/hot/run.sh                        # hot proto 消息体 wire 字节

# ── ⭐ 产物可执行性（未构建 ⇒ 判红；永远打印「已检查 N/M」）──────
bash tools/difftest/check-jar-executable.sh                   # 需先 mvn package

# ── 域测试必须带 -am（否则解析 ~/.m2 过期 com.rag:common ⇒ 假红）──
mvn -B -o -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end -pl <mods> -am test

# ── R3 组件的「可复算判定命令」＝ 模块级测试（期望值带 Go 出处）────
mvn -o -B -DargLine=-Xmx2g -pl service/hot/hot-common   -am test   # HotErrorCodeContractTest（Go errmsg.go:34-46）
mvn -o -B -DargLine=-Xmx2g -pl service/users/users-common -am test  # JwtUtilParityTest / PasswordUtilGoCompatibilityTest
mvn -o -B -DargLine=-Xmx2g -pl service/like/like-common   -am test  # LikeErrorContractTest（29 码逐条 1:1）
mvn -o -B -DargLine=-Xmx2g -pl service/hot/hot-rpc test -Dtest=HotProtoContractTest   # 基准由 Go 真源生成
```

---

## 十三、与上游文件的关系

```
docs/PhasedGoal.md  §二  ─┐
                          ├─→ 准入线（语言描述）──→ 本文件 §一~§五（可判定矩阵）──→ §八 阶段一结论
docs/twoStage.md    §2.1 ─┘                                        ↑
                                                                   │
docs/twoStage.md    §8.1（E0–E5）───────────────────────────────→ §1.2
docs/twoStage.md    §5.3（差异与暂缓）─────────────────────────→ §八 末
docs/twoStage.md    §5.6（四种结论）───────────────────────────→ §8.3
docs/twoStage.md    §10 （阶段归属 A/B/C/D）────────────────────→ §十一
docs/twoStage.md    §9  （故障矩阵）────────────────────────────→ 本文件不覆盖（阶段 2）
```

> ⭐ **本文件 → per-lane 分摊矩阵（`docs/PhaseGoal/g{1..4}.md`）** —— 把「按域/组件」的基线
> **按 lane 归属（G1/G2/G3/G4，AGENTS.md §5.3d·§5.3f）** 切成 4 份 lane 视角的阶段一验收矩阵：
> 组件行 + 本域自修项 + **转派 G4 反馈单** + P0 分摊 + 矩阵 C 分摊 + 收口命令 + lane 判定。
> **机器生成、勿手改** ⇒ `python3 tools/difftest/check-phasegoal-lanes.py --write`（生成）/ `--check`（校验）。
> ⚠️ **lane 判定只覆盖组件层**；矩阵 C 与 P0 是**收口执行者**的判定 ⇒ **某 lane 绿 ≠ 阶段一绿**。

> ⚠️ **本文件不替代任何一份上游文件。** 若与 `docs/PhasedGoal.md` / `docs/twoStage.md` 冲突，
> **以上游为准**（准入线以 `PhasedGoal.md` §二 / `twoStage.md` §2.1 为准），并把冲突登记为一条待修项。
>
> ⚠️ **台账与本文件冲突时**：以**台账的条目级最新状态**为准（L0），并按 §1.5 更新本文件的引用。