<!-- @new 2026-10-27 · `global-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G4 波次工作树（`align/g4-service-common`）** -->

# `global-common`（现 **G4 共享层波**）台账 —— 「共享层 ↔ 各域」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（⚠️ **global 是收口域（§3.5），不是微服务** ⇒ **没有自己的 `-common` 库**；本 lane 对应的就是 `service/common` 本身）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G4` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `global-common`（**原第 10/10 条**） |
| **现分支 / 工作树** | **`align/g4-service-common`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g4-service-common`<br>⚠️ 原 `align/global-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/common/`（`com.rag.common` 共享层：`observability` / `protection` / `http` / `persistence` / `response` / `communityfact` / `exception` / `logger` / `snowflake` / `spring`） |
| 域侧台账 | [`_README.md`](_README.md) · [`cross-domain.md`](cross-domain.md) · [`common-tails.md`](common-tails.md) · [`cycle-deps.md`](cycle-deps.md) · [`lanes/`](lanes/) |
| **写权** | 🔴 **`service/common/**` + `service/users/users-common/**` 的唯一写者**（G4 = 共享层波）＋ 本 lane 台账 `docs/DRIFT/global/vs-common.md`；⚠️ 旧的 `align/global` 全域 lane **已弃用**（Jason：一个工作树跨多域 ⇒ 粒度与操作内容不可控） |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| **E21** | `grpc-inprocess` 是 `test` scope ⇒ 运行时 artifact 消失 | `service/common`（各 rpc pom） | ✅ **已修**（L12/L13） | [`_README.md`](_README.md) |
| **E38②** | 业务层吞掉「SQL 被 deadline 取消」（机制层） | `service/common` | 🔴 **A 类 backlog** | [`_README.md`](_README.md) |
| **E46 / E62** | 超时 deadline 与毫秒精度 | `service/common` | ✅ **已修并合回主干** | [`_README.md`](_README.md) |
| **E65** | 出向 gRPC **不继承**请求 deadline（`applyTo` 是 no-op） | `service/common`（`RpcInterceptor`） | 🔴 **OPEN** | [`_README.md`](_README.md) |
| **E66** | Redis 写**不绑 deadline** | `service/common` | 🔴 **OPEN** | [`_README.md`](_README.md) |
| **E68 / E69 / E70** | 共享复核器的「实体归属」判据 + 「假 FAIL 机器」三处真缺陷 | 共享工具 | ✅ **已落** | [`_README.md`](_README.md) |
| **未修 27 条编号** | 5 个可比包 **3 个 `NOT EQUIVALENT`**（communityfact / logger / observability / snowflake；仅 `response` 逐条等价） | `service/common` | 🔴 **未修** ⇒ **其他域的尾巴挂在它身上** | [`alignment-2026-10-23.md`](../common/alignment-2026-10-23.md) |
| **X-6** | `DRIFT-HOTAPI-030`（Tomcat 参数个数口径）⇒ 共享层 6 个类 + 每域一行 yaml | `service/common` | ✅ **已落地**（第十四轮） | [`cross-domain.md`](cross-domain.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A2** | gRPC 拦截器链 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A3** | HTTP 保护链 | **本 lane 就是共享层本身** ⇒ 核「**10 个域**各自 yaml 的 `go-form-limit.mode` 与 Go 侧 `SetErrorHandler` 注册是否同向」＋ 6 个共享类 | ⬜ **待 lane 复核**（共享层已验 ⇒ 缺「10 域逐域真实请求」证据） | — | — |
| **A4** | 错误码 / envelope | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A5** | 端口治理 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A6** | errmsg 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A7** | proto 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A8** | DDL 时间列 / 类型 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A9** | trace / otel | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> **本 lane 就是共享层本身** ⇒ 「逐域 `go-form-limit.mode` ＋ Go 侧 `SetErrorHandler` 注册」的对照由本 lane 核：
> comment/follow/hot/like/message **5 域注册 ⇒ envelope**；article/task/user/admin **4 域未注册 ⇒ raw**；
> favorite **未注册 ＋ RAG 侧刻意 `off`** ⇒ 两侧都不过限 ⇒ 零触碰。
> ⇒ ⚠️ **共享层已验 ≠ 各域已验** ⇒ 本 lane 仍须**逐域跑一次真实请求**确认拒绝形状。

## 3. 共享层能自修的（**G4 直接修**；G1~G3 只登记 + 派单到这里）

- 🔴 **`service/common/**` + `service/users/users-common/**` 的唯一写权 owner**（AGENTS.md §5.3c·§5.3d·§5.3f）⇒ G1~G3 的派单**全部落到 G4**
- `GoStyleBreakerFilter` 注释订正 · `SqlTimeoutStatusTranslator` javadoc 归因 · `manifest_chain --no-merges` ⇒ 本 lane 自办

## 4. 需要 **G4** 处理的（**本 lane 就是 G4** ⇒ 直接自办）

- （无待派单）**本 lane 是收口方** ⇒ G1~G3 的派单在此汇聚 ⇒ 按「叶子文件」切 lane（`lanes/_PROTOCOL.md`）
- ⚠️ **G4 = 共享层常设执行者**（AGENTS.md §5.3e · 2026-10-27 02:23 Jason 定）：**随时接收** G1/G2/G3 的反馈单 ⇒ **执行落码** ⇒ 跑**全量 2411** ⇒ **回执**反馈方；**落码窗口常开**（不是「只在 G4 波激活」）⇒ G1~G3 期间**只读、只登记**

### 4.1 🆕 派单通道与回执规格（2026-10-27 02:4x · 与 G3 握手定）

⚠️ **前提**：各波次工作树的**台账文件在合并前互不可见**（G3 树里写的 `docs/DRIFT/<域>/vs-common.md`，G4 树里读不到）
⇒ **文件通道不是派单的前置条件，而是「事后核账」的凭证**。

| 轨 | 载体 | 作用 | 时点 |
|---|---|---|---|
| **实时轨** | 跨会话消息（`session send`） | **唯一能在合并前驱动 G4 落码的通道** ⇒ 反馈单 **7 项写全**（现象 / Go 侧 `文件:行号` / RAG 侧现状 / 证据强度 / 建议修法「两侧都应是 Y」/ 影响面 / 是否阻塞本波） | 波次进行中 |
| **正式轨** | 域侧 `docs/DRIFT/<域>/vs-common.md` 的「需转派」段 | **合并后的对账凭证** ⇒ 防「实时轨丢单、事后无痕迹」⇒ 两轨内容应一致 | 合并后 |
| **收口登记** | **本文件**（G4 唯一有写权的文档面） | 派单汇聚处 + 落码回执 | 持续 |

⚠️ **`global/_README.md` §七之二跨域桶 = `SHARED` 面** ⇒ 合并后由整合者统一更新，**G4 与各域都不写**
⇒ 派单不要指望落在那里。

**落码回执规格（固定 4 项）** —— 光给 commit 号，接收方无法判断影响面：

1. `git rev-parse HEAD`；
2. 改动文件清单（逐个 `文件:行号`）；
3. 触及的机制面轴（A1~A9 哪几条）+ 对应 Go 出处；
4. 回归口径与结果 —— **该字段本身必须写全**（🆕 2026-10-27 G3 要求）：
   **①** 跑没跑（`mvn -B clean verify` / 模块级 / 未跑）；**②** **未跑时必须把「未跑的理由」写进这一字段本身**
   （如「纯 javadoc 改动 ⇒ 零字节码变化 ⇒ 全量对本次改动无增量信息」）；
   **③** 跑了的话给出模块数 / 用例数 / failures / errors / skipped / 耗时；**④** 三门禁结果。
   ⇒ ⚠️ **理由不要只写在正文** —— 接收方核账时**直接看第 4 项** ⇒ 理由散落在正文里等于没写。

**大回归错峰**：G4 全量 = `mvn -B clean verify`（上轮 2411 条 / 21:38 min）⇒ **开始前 session send 知会对方**（四棵工作树 `target/` 独立不互踩，仅 CPU/磁盘互抢）。

**高引用度类的响应预期**：修法落在 9 域引用类（`GoStyleProtectionProperties` / `LoggerFactoryBean`）或 8 域引用类
（`GoStyleRouterErrorResponses` / `ObservabilityProperties` / `Response` / `RpcServerInterceptorFactory`）⇒ **编译面 = 16 个组件**
⇒ G4 先自跑全量确认编译面未炸，再回执 ⇒ **此类单比普通单慢一轮**。

**零重叠已实测**（核对日期 2026-10-27）—— 口径 = **源文件**（未构建工作树），用 `check-wave-lane-ownership.py` 自己的 `expand()` + `_rel()` 复算：
**结论 = G4 ∩ G3 = ∅（文件级零重叠）⇒ 跨树 merge 干净**。
⚠️ **不引用「展开 N 个文件」作为规模证据** —— 该数字**随工作树构建状态变化** ⇒ 不可复算 ⇒ 见 §4.2 建议项 1。

### 4.2 🆕 建议项登记（落点在共享面 ⇒ 需整合者或 Jason 点头）

> **性质**：本条落点 = `tools/difftest/**`（**无 lane 拥有 = 共享面**）⇒ **G4 不擅自改**
> ⇒ 按本 lane 写权登记在此 ⇒ 由整合者统一落码，或经 Jason 点头后由 G4 落码。
> **格式** = 反馈单 7 项（`_cross/cross-domain-coupling.md` §3.3 口径）。
>
> ✅ **2026-10-27 02:5x Jason 拍板：整合者合并时统一改** ⇒ **G4 不落码** ⇒ 本条**已登记待整合**。
> ⇒ 期间兜底 = **台账纪律**（引用可复算结论 + 口径 + 核对日期，**不引用「展开 N 个文件」**）。
> ⇒ ⚠️ **整合时请用「`expand()` 排除 `/target/`」这一种改法** —— **不要**用「加断言：白名单不得命中 target」
> ⇒ 那条断言上线即红（37/61 条 pattern 命中 target）⇒ 连带要改 G1/G2/G3 的白名单 = 越权。
> ⇒ 📌 **归属依据（2026-10-27 G3 指出）**：该文件**无 lane 拥有** ⇒ 按 `docs/Worktree.md` §三·1d「无主/共享面 = 合并后由整合者统一更新」
> ⇒ **本就有既有规则可依**，属**登记类**、不必另开一轮拍板。
> ⇒ ⏰ **落码时点**：若 Jason 想提前改（改动量 = `expand()` 一行过滤）⇒ **建议排在 G1~G3 收口之后**，
> 避免它们正在跑的守门中途变口径（**结论不变** —— G3 已实测：重叠/命中全部不变 ⇒ 期间跑旧版守门不会红）。

**建议项 1：`expand()` 不排除 `target/` ⇒ 守门打印的「白名单展开 N 个文件」不可复算**

| 字段 | 内容 |
|---|---|
| 1 现象 | `expand()` 用 `glob.glob(base + "/**/*", recursive=True)` ⇒ **不排除 `target/`** ⇒ 白名单 `<域>/<组件>/**` 会把**构建产物**一并计入 ⇒ 打印的展开数**随工作树构建状态变化** |
| 2 Go 侧出处 | N/A（工具自身缺陷，非行为漂移） |
| 3 RAG 侧现状 | `tools/difftest/check-wave-lane-ownership.py:243-248` |
| 4 证据强度 | **L3 静态实测**（同一脚本、两处运行、数字对拍） |
| 5 建议修法 | ⛔ **不要加「白名单不得命中 `target/`」这条断言** —— 实测 **37/61 条现有 pattern 命中 target** ⇒ 该断言**上线即 EXIT=1（红）** ⇒ 修它要改 **G1/G2/G3 的白名单** = 改别人的写权定义 ⇒ 不可行。<br>✅ **改用「`expand()` 统一排除 `/target/`」（一行过滤）」** ⇒ 实测：断言 **1/2/5 全绿**、断言 **4/6 不受影响**（独立判定 / 扫 `src/main/java`）、**无任何 lane 需改白名单**、**计数完全可复算**（主工作区 / G3 树 / G4 树三处源文件口径**同为 305/322/465/145**）<br>⇒ **两轨分离**：**计数不可复算 = 台账纪律**（引用结论 + 口径 + 核对日期，不引用数字）；**白名单命中 target = `expand()` 的过滤口径问题**（不是断言问题） |
| 6 影响面 | 守门本身（4 条 lane 全读它）· 断言 1（两两重叠）· 断言 5（白名单必须命中真实文件） |
| 7 是否阻塞本波 | **否** —— 当前结论不受影响（见下） |

**实测数据（核对日期 2026-10-27）**：

| 口径 | G1 | G2 | G3 | G4 | 断言 1 |
|---|---|---|---|---|---|
| 主工作区 `RAG/`（target 文件 4485） | 1542 | 1482 | 2141 | **145** | PASS |
| G3 工作树（target 文件 0） | 305 | 322 | 465 | **145** | PASS |

⇒ **G4 恒 145**：其白名单只含 `service/common/src/**` + `service/users/users-common/src/**` + 2 个 `pom.xml`
⇒ **`target/` 在白名单外**（`service/common/target/` 无主）；**ACTIVE pattern 共 61 条，其中 37 条命中 target**
（G1 10/15 · G2 11/17 · G3 16/24 · **G4 0/5**）。
⇒ 🟢 **无现网假红**：实测「含 target / 不含 target」两种口径下**断言 1 均 PASS**，且当前 ACTIVE 白名单**无任何跨 lane 相交**
⇒ 假红需「白名单写成 `service/<域>/**` 这类宽形式」才触发 ⇒ **属潜在脆弱性，非当前缺陷**。
⇒ ⚠️ **本条已导致一次实际污染**（2026-10-27）：G4 台账首版曾写入「G3 展开 465 / G4 展开 145」作为规模证据
⇒ 由 G3 在落账前指出 ⇒ **已订正为「引用结论 + 口径 + 核对日期」**（见 §4.1 末条）⇒ **教训 = 跨树引用数字前先问「这个数字换个工作树还成立吗」**。

**两种改法的实测模拟**（核对日期 2026-10-27）：

| 改法 | 断言 1 | 断言 2 | 断言 5 | 断言 4/6 | 需改他树白名单？ | 可行 |
|---|---|---|---|---|---|---|
| ⛔ 加断言「白名单不得命中 target」 | — | — | — | — | **要**（37 条 pattern 命中 ⇒ 上线即红） | ❌ |
| ✅ `expand()` 排除 `/target/` | ✅ PASS | ✅ PASS | ✅ PASS | 不受影响 | **不要** | ✅ |

⇒ ✅ 改法②下的**可复算计数**（源文件口径）：**G1 305 · G2 322 · G3 465 · G4 145**
⇒ 主工作区（target 4485）/ G3 树（target 0）/ G4 树（target 0）**三处完全一致** ⇒ 这才是**可写进台账的数字口径**。
⇒ ⚠️ 改法②不动任何断言的判定结果，**但会改变守门打印的计数** ⇒ G1~G3 正在跑这个守门 ⇒ 落码时点需知会（不改则计数不可复算，风险为潜在）。

### 4.3 🆕 预告预登记（**G3 实时轨预告 · 非正式派单** · 落点已独立核准 · 核对日期 2026-10-27）

> **来源**：G3 跨会话消息（实时轨）预告 article 域 2 条待派单。**正式派单未到 ⇒ 本节只做落点核准，不落码。**
> ⚠️ **本节的存在意义 = 「派单前先核准落点，别照抄台账的落点列」**（AGENTS.md §3.1：只贴声明行不算证据）

#### 预告 1：`DRIFT-ARTICLE-API-006`（Go 必填字段校验）⇒ ⛔ **不进 G4 排期**

| 核查项 | 结论 |
|---|---|
| **编号 ↔ 主题是否对得上** | ❌ **对不上** —— 权威面 `docs/DRIFT/article/api.md:676-682` 的 `DRIFT-ARTICLE-API-006` = 「**8–10 MiB JSON body 的错误文案**（LOW，**2026-10-24 已确认不修**）」；而 `docs/DRIFT/article/vs-common.md:27` 把「**Go 必填字段校验**」挂在**同一编号**下 ⇒ 命中 `api.md:8-11` 自己警告的「**两套编号同形不同义**」 |
| **该机制是否还缺共享层实现** | ✅ **不缺** —— 契约 `GoStyleRequiredFields` 已落地于 `service/users/users-common/.../http/GoStyleRequiredFields.java:46-53`，带**三段 Go 出处**（`core/mapping/utils.go` `parseOption` · `unmarshaler.go:918-969` · `:1199`）＋ 运行时探针 `tools/difftest/probe-go-required-fields.py`（2026-10-21） |
| **article 域接入情况** | `CreateArticleReq implements GoStyleRequiredFields`（`service/article/article-api/.../dto/CreateArticleReq.java:57` + `:109`）⇒ **已接入**；`DeleteArticleReq.java:14` / `UpdateArticleReq.java:31` **显式不实现**（附了理由） |
| **落点复核结果** | ⇒ 若真残留，落点在 **`service/article/article-api/**`（DTO 层）= G3 自己的写权面** ⇒ 按 C3「域侧能自修的自修」⇒ **不甩给 G4** |
| **结论** | ⛔ **不进 G4 排期** —— ① 编号撞号（须先由 G3 订正编号/摘要）；② 落点疑在域侧 ⇒ 派单前请 G3 核实行号 + 明确落点 |

#### 预告 2：`S-6` 的 `SqlTimeoutStatusTranslator` **javadoc 归因** ⇒ ✅ **可进排期，落点已核准**

| 核查项 | 结论 |
|---|---|
| **落点** | ✅ `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java` 的 javadoc **「Go 出处」段（第 31-35 行）** ⇒ **G4 写权面内**（`service/common/**`） |
| **问题定性** | 该段以「**Go 出处**」措辞断言 Go 行为，**却一个 `文件:行号` 都没有** ⇒ 命中 AGENTS.md **§3.3.2**（以「Go: …」措辞写的断言是高危项，须满足 §3.3.1 三条，否则不得用该措辞） |
| **更深的发现** | 🟡 **结论对、归因措辞错** —— javadoc 写「Go 侧 ctx 到点后…**经 go-zero 的错误映射**成为 1015」⇒ 实测 Go 侧 **1015 不是框架映射、也不是 deadline 映射**：它是**各 api logic 显式 `switch st.Code()` 的 `default` 分支**产出（`service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48`，同族 13 个 admin logic ＋ `service/user/user/api/internal/logic/user/selectavatarlogic.go:60`）⇒ **落点被写偏了一层** |
| **Go 侧真源出处（已逐条核实，2026-10-27）** | ① `service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48` —— `switch st.Code()`(44) / `case codes.Internal:`(45) → `ErrorServerCommon`(46) / `default:`(47) → `errmsg.CodeServerBusy`(48)<br>② `service/user/common/errmsg/errmsg.go:6`（`CodeServerBusy = 1015`）＋ `:41`（`"服务繁忙"`）—— ⚠️ 路径是 **`errmsg/errmsg.go`（目录）**，不是 `errmsg.go`<br>③ `service/common/observability/observability.go:146-158` —— `IsTimeoutError` 是 Go 侧**唯一** `errors.Is(err, context.DeadlineExceeded)` 的公共处理点（`:150` `errors.Is` / `:153` `codes.DeadlineExceeded` / `:157` `netErr.Timeout()`），且**只做可观测性判定、不产码** |
| **RAG 侧出处（已核实）** | ① `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/advice/GlobalExceptionHandler.java:83`（`mapRPCStatusToCode`）＋ **`:90`（default → 1015）**<br>② `service/users/user/user-api/src/main/java/com/rag/users/user/api/advice/GlobalExceptionHandler.java:153` ＋ **`:162`（default → 1015）** |
| **建议修法（两侧都应是 Y）** | ① javadoc 的「Go 出处」段**逐条补上上述 `文件:行号`**（指向**消费点**而非声明点，§3.3.1-3）；② **改正归因措辞** —— 1015 来自 **api logic 的 `switch st.Code()` default 分支**，不是「go-zero 的错误映射」；③ 保留原结论（`DEADLINE_EXCEEDED` 未被单列 ⇒ 落 default ⇒ 1015 ⇒ **api 层无需改动**）—— 该结论经上表 Go/RAG 两侧行号**证实成立** |
| **证据强度** | **L1 源码对照**（Go 侧行号已逐条核实存在）＋ RAG 侧行号已核实 ⇒ **满足正式派单的「Go 侧行号必须真实存在」前置** |
| **落码影响面** | **纯 javadoc** ⇒ 零字节码变化 ⇒ 编译面不受影响；⚠️ 需在回执里说明「全量 2411 对纯 javadoc 改动无增量信息」的取舍，或按 C2 仍跑一次作为共享层复验窗口 |
| **是否阻塞本波** | **否** ⇒ 进 G4 排期（等正式派单到达后落码） |

### 4.4 来自 G3 的三条发现 · **G4 独立复核结果**（核对日期 2026-10-27）

> **来源**：G3 实时轨。**G4 逐条独立复核，未采信对方结论**（复核纪律：读消费点，不读快照）。

| # | G3 的发现 | 复核结论 | G4 的独立证据 |
|---|---|---|---|
| 1 | `article-rpc` / `favorite-rpc` 缺 `baseline-version: 0`；且 **AGENTS.md §6.3 问题 4 只点了 `favorite-rpc`，漏了 `article-rpc`** | ✅ **成立**（G3 主动更正了自己先前那条错报 —— 起因是把 `grep -c` 的**匹配行数**当成了**配置值**） | 全仓 **9 个** rpc 组件逐个实测：**已钉 0** = `comment-rpc:109` · `follow-rpc:76` · `like-rpc:78` · `message-rpc:102` · `task-rpc:76`（**5 个**）｜**有 flyway 段但缺** = **`article-rpc`** · **`favorite-rpc`**（**2 个** ⇒ 即真缺陷面）｜**N/A（无 flyway 段）** = `hot-rpc` · **`admin-rpc`**｜**`user-rpc` 用 `application.yaml`（非 `.yml`）且无 flyway 配置 ⇒ N/A**<br>⇒ ⚠️ G3 的表**漏了 `admin-rpc` 与 `user-rpc` 两行**（均为 N/A ⇒ **不影响结论**） |
| 2 | favorite 的 `go-form-limit.mode` 缺失是**共享层的有意设计** ⇒ **勿提「共享层加一档」的派单** | ✅ **成立** | `service/common/.../http/GoStyleFormLimitMode.java:41-49`（默认 `OFF` 的理由：favorite 不在 global 写权范围 ⇒ **共享层对它必须零影响**；若默认 `RAW` ⇒ favorite 的 2049 场景会从「5003」变「400 原文」= 对别人的域的未授权改动；**其它 9 个域必须显式声明**）＋ `tools/difftest/check-tomcat-limits.py:156-158`（**零触碰守卫**：favorite-api **必须没有**这一项）<br>实测 mode：`article-api` **raw** · `comment-api` **envelope** · `hot-api` **envelope** · `task-api` **raw** · `user-api` **raw** · `admin-api` **raw** · **`favorite-api` 无声明 ⇒ OFF（刻意）** |
| 3 | **A9 轴定义本身**（「span 属性 `audit.*`」）与两侧源码都不符 | ✅ **成立** —— ⚠️ 但 G3 给的按域分布我第一遍**在 RAG 侧查不到**（comment 的 `audit.*`、favorite 的 `biz.*` 均命中 0）⇒ 原因是**那些域特有属性在 Go 侧，不在 RAG 侧** | **共享层两侧都是 `sea.*`**：RAG `ObservabilityConstants.java:63+`（`sea.error.kind` / `sea.biz_code` / `sea.biz_msg` / `sea.timeout` / `sea.slow` / `sea.duration_ms`）＋ Go `service/common/observability/observability.go:24-29`（**同名同值**）<br>**域特有在 Go 侧**：comment = `audit.*`（`service/comment/rpc/internal/logic/update_subject_state_logic.go:53-56` · `create_comment_logic.go:56-59`）· favorite = `biz.*`（`service/favorite/api/internal/logic/favorite/delete_favorite_logic.go:43-44` 等 4 文件）· admin = `audit.*`（RAG 侧已落地 `AdminSpans.java:91-93`）<br>⇒ ⇒ **RAG 侧已落地的域特有属性只有 admin 一处** |

#### 建议项 2：**A9 轴定义措辞缺陷**（落点 = `docs/DRIFT/_cross/domain-common-lanes.md` = 共享面 ⇒ 整合者订正）

| 字段 | 内容 |
|---|---|
| 1 现象 | 总纲 A9 写「span 属性 `audit.*`」⇒ **既不完整也不准确**：① 共享层实际用 **`sea.*`**；② `audit.*` / `biz.*` 是**域特有**、且**按域不同**；③ RAG 侧目前**只落地 admin 一处** |
| 2 Go 侧出处 | `service/common/observability/observability.go:24-29`（`sea.*` 六项）· `service/comment/rpc/internal/logic/update_subject_state_logic.go:53-56`（`audit.*`）· `service/favorite/api/internal/logic/favorite/delete_favorite_logic.go:43-44`（`biz.*`） |
| 3 RAG 侧现状 | `service/common/.../observability/ObservabilityConstants.java:63+`（`sea.*`）· `service/users/admin/admin-rpc/.../observability/AdminSpans.java:91-93`（`audit.*`，**唯一已落地的域特有**） |
| 4 证据强度 | **L1 源码对照**（两侧行号已逐条核实） |
| 5 建议修法 | A9 轴改为**分层表述**：「**共享层通用属性 = `sea.*`（两侧一致）**」＋「**域特有属性按域各异**：comment = `audit.*`（Go 侧）· favorite = `biz.*`（Go 侧）· admin = `audit.*`（两侧）」⇒ 避免把**某一域的约定**写成全轴通则 |
| 6 影响面 | A9 轴的 10 份域台账（各域据此判「A9 已对齐」）· 只影响**判定口径**，不影响运行时 |
| 7 是否阻塞本波 | **否** ⇒ 登记待整合者订正 |

#### 建议项 3：**AGENTS.md §6.3 问题 4 漏记 `article-rpc`**（落点 = `AGENTS.md` = 共享面 ⇒ 整合者订正）

| 字段 | 内容 |
|---|---|
| 1 现象 | §6.3 问题 4 的「实施状态」只列了 **`favorite-rpc` 例外**（缺 `baseline-version: 0`）⇒ **漏了 `article-rpc`** ⇒ 共享面台账记载不完整 |
| 2 Go 侧出处 | N/A（配置层缺陷，非行为漂移） |
| 3 RAG 侧现状 | `service/article/article-rpc/src/main/resources/application.yml`（有 `flyway:` 段 + `baseline-on-migrate: true`，**无 `baseline-version`**）· `service/favorite/favorite-rpc/src/main/resources/application.yml`（同上） |
| 4 证据强度 | **L3 静态实测**（9 个 rpc 组件逐个 `grep -n`） |
| 5 建议修法 | §6.3 的例外清单补上 **`article-rpc`** ⇒ 两条（`article-rpc` + `favorite-rpc`）并列；⚠️ **这两条落点都在域内 yaml ⇒ 域侧自修**（G3 已认领 article 侧）⇒ **共享层只需订正「记载」，不需落码** |
| 6 影响面 | `AGENTS.md` §6.3 一处文字 · 各域台账引用该处时会误以为只有 favorite 有缺口 |
| 7 是否阻塞本波 | **否** ⇒ 登记待整合者订正 |

#### 🔴 **拒单判据：favorite 零影响**（G4 侧的硬线）

> **依据**：`GoStyleFormLimitMode.java:41-49` ＋ `check-tomcat-limits.py:156-158` ＋ AGENTS.md §5.3c。
> ⇒ **G4 收到任何「要求共享层碰 favorite」的派单 ⇒ 一律退回**，并回指上面三处出处。
> ⇒ favorite 域自己的问题（例：域特有 span 属性 `biz.*` 未落地）⇒ **favorite 域自修**，**共享层零改动**。

#### 🟡 **跨域线索（A9 域特有属性在 RAG 侧未落地）—— 属域侧，共享层零改动**

> **线索**：Go 侧 comment 埋 `audit.*`（`update_subject_state_logic.go:53-56`）· favorite 埋 `biz.*`（4 文件）；
> **RAG 侧实测**：comment-rpc 的 span 写入点**只有共享层 `sea.*`**（其测试 `CommentRpcSlowThresholdGateTest:233-245` 断言的是 `ATTR_SLOW`/`ATTR_DURATION` = `sea.*`）⇒ `audit.*` **未埋**；
> favorite 侧**无任何 `SetAttributes` 写入点** ⇒ `biz.*` **未埋**。
> ⚠️ RAG 侧 comment **有** `UpdateSubjectState` 对应功能（`UpdateSubjectStateReq` / `UpdateSubjectStateService` / `CommentController`）⇒ 不是「功能不存在」导致的省略。
> ⇒ **本会话不下「漂移」结论**（未验两侧 span 语义是否等价、也未查 `docs/Intentional_Deviations/` 是否有签字项）
> ⇒ **登记为线索，交 comment / favorite 域侧自查**；**共享层零改动**。

### 4.5 G3 提供的 L4 证据的**独立复核** + 两个新的共享面建议项（核对日期 2026-10-27）

> **背景**：G3 报「`check-ddl-static.py` 在 favorite 域报 `EXIT=1`（2 处 `now()` DEFAULT 差异）⇒ 是常驻容器旧快照 ⇒ **不是代码缺陷**，别照那条红去修」。
> **G4 复核：✅ 结论成立，且定位比 G3 说的更准。**

#### 复核：为什么那条红是**工作库滞后**（而不是代码缺陷）

| 环节 | 实测 |
|---|---|
| **V7 脚本本身** | `service/favorite/favorite-rpc/src/main/resources/db/migration/V7__align_favorite_outbox_time_columns_with_go.sql` ⇒ 标题即「**对齐 … 去掉 DEFAULT now()**」⇒ **代码侧已落地** |
| **活体工作库** | `ddl-favorite-pg / rag_favorite_db` 的 `favorite_fact_outbox.created_at` / `updated_at` **仍带 `now()`** ⇒ **停在 V7 之前** |
| **它是谁建的** | `tools/difftest/check-ddl-static.py` 的工作库（库名 `rag_<域>_db` / `go_<域>_db`，容器 `ddl-<域>-pg`；见该脚本 `:140/:317` 等）⇒ **由迁移脚本直接 psql 重放**、**不走 Flyway** ⇒ ⇒ **工作库是否最新，取决于谁最后重放过** ⇒ **滞后就假红** |
| **账本表** | 该库 **`flyway_schema_history%` 表 = 0 张** ⇒ 印证「不是 Flyway 建的」 |
| **权威面** | `tools/ddl-parity-probe/favorite/run.sh`（`:180-187` **每次新建 `ragdb` 并重放 V1..V7 全量**）⇒ **EXIT=0 才是权威** ⇒ 与 G3 一致 |

⇒ **判别式（写入台账，供下一个 reader 免走弯路）**：
**「`check-ddl-static.py` 报红」时，先用 `tools/ddl-parity-probe/<域>/run.sh` 复核** ⇒ 探针绿 = **工作库滞后**（刷新工作库）｜探针红 = **真漂移**（改代码）。

#### 🔴 **顺带实测：全部 `ddl-*` 容器的工作库都不是 Flyway 建的**

⇒ 9 个容器 × 2 库 = **18 个库，`flyway_schema_history%` 账本表全部 = 0 张**
⇒ ⇒ **「Flyway 账本隔离 / `baseline-version` / `baseline-on-migrate`」这一维度，在 DDL 对拍容器里从未被运行时验证**
⇒ ⚠️ **但不是缺口**：`tools/difftest/check-flyway-concurrent.sh` + `flyway-concurrent-verify.py` **走真 Flyway 路径**
（脚本 `:23-24` 自述校验「各域的表都建出来了、账本互不覆盖、**baseline 位置正确**」；`:238` 打印 `baseline=`）⇒ **该维度有独立的守门覆盖**。

#### 建议项 4：**`check-ddl-static.py` 的工作库滞后 ⇒ 假红**（落点 = `tools/difftest/**` = 共享面 ⇒ 整合者 / 使用者）

| 字段 | 内容 |
|---|---|
| 1 现象 | `check-ddl-static.py` 在 favorite 域报 `EXIT=1`（2 处 `now()` DEFAULT 差异）⇒ **看似代码缺陷，实为工作库滞后** ⇒ 任何域的工作库未刷新都会产生同类假红 |
| 2 Go 侧出处 | Go 侧 `favorite_fact_outbox` 的 `created_at` / `updated_at` **无** `DEFAULT now()`（V7 的对齐目标） |
| 3 RAG 侧现状 | V7 脚本**已落地**（`.../V7__align_favorite_outbox_time_columns_with_go.sql`）｜**工作库** `ddl-favorite-pg/rag_favorite_db` 仍带 `now()` ⇒ **落后于 V7**｜`tools/difftest/check-ddl-static.py:317`（`rag_favorite_db` 库名来源） |
| 4 证据强度 | **L4 活体实测**（容器内 `information_schema.columns` 实查 ＋ 迁移脚本实读 ＋ 探针实跑 `EXIT=0`） |
| 5 建议修法 | **刷新工作库**（重建 `rag_<域>_db` 并按该域 V1..Vn 全量重放）⇒ **不是改代码**；并把上面那条**判别式**写进 `check-ddl-static.py` 的输出（红时提示「先跑 `tools/ddl-parity-probe/<域>/run.sh` 复核」） |
| 6 影响面 | `check-ddl-static.py` 全域输出可信度（**9 域**）· 任何按它的红去改代码的人都会被误导 |
| 7 是否阻塞本波 | **否**（favorite 已由探针证一致） |

#### 建议项 5：**`flyway-concurrent-verify.py` 的 `KNOWN_BLOCKED` 漏记 `article-rpc`**（落点 = `tools/difftest/**` = 共享面 ⇒ 整合者）

| 字段 | 内容 |
|---|---|
| 1 现象 | 该脚本的 `KNOWN_BLOCKED`（`:48-51`）**只登记了 `favorite`**（「缺 `spring.flyway.baseline-version: 0` ⇒ 共享库里 V1 被 baseline 跳过」）⇒ **同形的 `article-rpc` 未登记** ⇒ 已知缺陷清单**不完整** |
| 2 Go 侧出处 | N/A（配置层缺陷，非行为漂移） |
| 3 RAG 侧现状 | `service/article/article-rpc/src/main/resources/application.yml`（有 `flyway:` 段 + `baseline-on-migrate: true` + `table:` + `schemas:`，**无 `baseline-version`**）｜`service/favorite/favorite-rpc/src/main/resources/application.yml`（同形）｜⚠️ 该脚本对**所有**模块都会算出 `baseline_version`（缺省 = `"1"`，`:93-100`）⇒ **它看得见 article 的 `baseline=1`**，只是**已知缺陷账里没登记** |
| 4 证据强度 | **L3 静态实测**（9 个 rpc 组件逐个 `grep -n` ＋ 脚本 `:48-51` / `:93-100` 实读） |
| 5 建议修法 | `KNOWN_BLOCKED` 增补 `article-rpc`（与 `favorite` 同形同因）⇒ **只补账，不改代码**（两个 yaml 的修法都在**域侧**，域侧自修）；⚠️ 顺带**统一消息**：两个 rpc 的 yaml 注释块都写着「2026-10-25 实测修掉的真缺陷 · global 收口域 X-1」⇒ 建议域侧照抄同一段 |
| 6 影响面 | `check-flyway-concurrent.sh` 的已知缺陷账 ⇒ **AGENTS.md §6.3 问题 4 也只点了 favorite**（见建议项 3）⇒ 两处需同步订正 |
| 7 是否阻塞本波 | **否** ⇒ 登记待整合者订正 |

### 4.6 🆕 G2 派单三张（FB-01 / FB-02 / FB-03）—— **已落码**（核对日期 2026-10-27 · 基线 `align/g4-service-common` @ `182fc32`）

> **落点全部独立核准**（读消费点，不采信派单的「落点」列）；**影响面按域实测**（不采信「N 域全命中」）。
> ⚠️ **三张单的落码范围都比派单描述更窄** —— 理由逐条写在下表「本层实际做了什么」。

#### FB-01 · A3「路径归一」进共享层

| 项 | 结论 |
|---|---|
| 1 现象 | Go 的 router 对请求路径做 `path.Clean` ⇒ 路径变体命中同一条路由；RAG 侧原先**只有 article-api 有**（域内副本）⇒ 其余 9 个 api 缺这一层 |
| 2 Go 侧出处 | go-zero v1.10.2 `rest/router/patrouter.go:48`（注册侧 `cleanPath := path.Clean(reqPath)`）+ `:60`（匹配侧 `reqPath := path.Clean(r.URL.Path)`）—— 逐字核对过 |
| 3 RAG 侧现状 | `service/common/src/main/**` 下 `GoStylePathNormalizingFilter` 命中数 = **0**；全仓唯一实现在 `service/article/article-api/.../config/GoStylePathNormalizingFilter.java`（域内类） |
| 4 证据强度 | **L4（双侧活体）**：🟢 Go 侧 = /tmp 临时工程起**真 go-zero v1.10.2 rest server** + curl，5 个 target 全 200；RAG 侧 = **真 Tomcat + 裸 socket** 同 5 个 target 同解 ⇒ ⇒ 两侧比的是**同一个请求**（JDK HttpClient 会自己归一 URI ⇒ 必须裸 socket） |
| 5 建议修法 | **Y = 「所有 api 的请求路径都按 Go 的 `path.Clean` 归一后再匹配」** ⇒ 归一器落**共享层**（不得按域各写一份）⇒ 归一语义 = Go `path/clean.go` ⇒ 必须排在读路径的 Filter 之前 |
| 6 影响面 | 共享层 `service/common/http/*` ⇒ 被 10 个 api 全依赖；实际缺口 **9 个**（article 已有等价实现） |
| 7 是否阻塞本波 | **否** |

**本层实际做了什么（比派单描述窄）**：
- 新增 `service/common/http/GoStylePathNormalizingFilter`（**字节级逐字移植** Go 的 `Clean`）＋ `GoStylePathNormalizingAutoConfiguration`（**唯一注册点**，走 `AutoConfiguration.imports` ⇒ 与各 api 的 `@ComponentScan` 配置**完全解耦** ⇒ 「漏一个域就静默少一层」不可能发生）。
- ⚠️ **过渡期去重**（article 域内副本还在，属 G3 写权）：按 **Bean 名**（不是类型 —— 两份的类不同）判重；判据可靠（组件扫描在解析阶段注册定义、自动配置是延迟导入 ⇒ 求值时已存在）；且**万一失效 ⇒ 两个同名 Bean ⇒ 启动直接失败**（硬失败，不是静默失效）；且 **simple name 与 Bean 名都与旧副本一致** ⇒ article-api 的「精确 9 项 Filter 链序」守门**不需要任何改动**就继续成立（**实测 article-api 13 条全绿**）。
- 🟢 **顺手修掉旧版移植的 2 处偏差**（差分守门抓到的，不是派单提到的）：
  `"/"`（Go `"/"` vs 旧版 `""`）、`".."`/`"../a"`（Go 原样 vs 旧版丢点）⇒ 共享层按 Go 的**字节级算法**重写（写入位置 + 回退到上一个 `/`）⇒ 32 条 Go 实测基准的**差分守门**逐条钉死。
  ⇒ ⚠️ **rooted 根路径族在请求路径上可达** ⇒ article 的域内副本仍带旧偏差 ⇒ 删掉副本后自动消失（见下）。

**🔴 域侧协调项（不在本层写权）**：`service/article/article-api/.../config/GoStylePathNormalizingFilter.java` 应由 **G3 删除**（只删这一个文件，**不要**动 `ArticleApiAssemblyWiringTest`）⇒ 删后 article-api 自动改用共享层那份 ⇒ 链序守门仍成立。

#### FB-02 · A3 form 闸门的开关判据改成「语义可达性」

| 项 | 结论 |
|---|---|
| 1 现象 | 共享层把「`off` 的依据」写成「**favorite 域不在 global 的写权范围内**」⇒ 那是一条**与 Go 行为无关**的理由 ⇒ ① 回答不了「下一个域该开还是该关」② 漏声明的域**静默落到缺省 `off`**（而缺省只对「Go 侧不解析」的域才正确） |
| 2 Go 侧出处 | go-zero v1.10.2 `rest/httpx/requests.go`（`Parse → ParseForm → GetFormValues`）+ `rest/httpx/util.go:15`（`maxFormParamCount = 2048`，数**非空值**）+ `:48-53`（超限 ⇒ `too many form values` ⇒ Parse 失败）⇒ **只有 handler 真调 `httpx.Parse` 的域，`r.Form` 才被求值** ⇒ 两道上限才可达 |
| 3 RAG 侧现状 | `service/common/http/GoStyleFormLimitMode.java:41-49`（旧判据）；`GoStyleTomcatParameterLimitCustomizer.java:107-108` 与 `GoStyleFormValues.java:38`（同一套措辞的三处落点） |
| 4 证据强度 | **L1（两侧源码逐字对照）**；Go 侧命中数**实测**：task/api = **0**、article/api = 6（对照组）、favorite/api = 7、user/api = 8、admin/api = 13 |
| 5 建议修法 | **Y = 「闸门按『Go 侧是否真的解析 form/query』决定开否」** ⇒ 判据从「写权范围」扩成「语义可达性」 |
| 6 影响面 | 判据与 5 类都在 `service/common/http/*` ⇒ 被 10 个 api 全依赖 |
| 7 是否阻塞本波 | **否**；但**阻塞 task-api 的 A3 结案** |

**⚠️ 与派单描述不一致之处（本层独立核实的结论，必须同步给 G2）**：
- **task**：Go 侧 `httpx.Parse` 命中数 = **0** ⇒ 语义答案 = **`off`** ⇒ task 当前 yaml 的 `raw` 是一条**待修漂移**（**G2 域内自修**）。
- **favorite**：Go 侧命中 **7 处** ⇒ 语义答案 = **`raw`** ⇒ 而它实际缺省 `off` ⇒ **这是本次顺带挖出的一条真实漂移**（LOW：Go 400 + text/plain + Go 原文 vs RAG 400 + Tomcat HTML，且 Tomcat 数**参数总数含空值**、Go 数**非空值**）。
  ⇒ **本层处置**：**零行为改动** ⇒ 共享层只改判据表述 + 落一张**逐域取证表**；favorite 的偏离登记为**已知偏离**（含可观察差异）⇒ **不改 favorite**（零写权约束成立 ⇒ 本层的「favorite 零影响」拒单判据成立）。
- **user-api / admin-api**：Go 侧命中 8 / 13 处 ⇒ 语义答案 `raw` ⇒ 二者 yaml **已经是 `raw`** ⇒ 一致（**排除了「漏声明静默落到 off」在这两域发生过**）。

**本层实际做了什么（比派单描述窄）**：
- `GoStyleFormLimitMode`：判据改写 ＋ 新增 `SEMANTIC_REACHABILITY`（**10 个 api 域逐域取证表**，每行带 Go 侧证据）＋ `REGISTERED_DEVIATIONS`（登记 favorite）＋ 查询入口（未知域返回 `null`，不许瞎猜）。
- `GoStyleTomcatParameterLimitCustomizer` / `GoStyleFormValues`：把当判据用的「写权范围」措辞改成「语义可达性」，并点明 favorite 是**刻意保留**而非「已对齐」。
- 配套守门 6 条（`GoStyleFormLimitSemanticCriterionTest`）。

**🔴 建议项 6（🆕 · 落点 = `tools/difftest/**` = 共享面 ⇒ 整合者）**：
「**新增 `check-go-form-reachability.py`**：从 Go 真源逐域统计 `httpx.Parse` 命中数 ⇒ 与共享层 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 对照 ⇒ 让**取证书**与**判据表**不会各自漂移」。
⇒ 现状风险：共享层的表是**手工取证快照** ⇒ Go 侧哪天改了 handler，表不会自动跟着变 ⇒ ⇒ 与「写死数字不可复算」同类风险。
⇒ ⚠️ 另建议整合者把 `check-tomcat-limits.py` 的 `EXPECTED_FORM_LIMIT_MODE` 与共享层那张表**对齐**（现在两处各写一份 domain→mode ⇒ 会漂移）；⚠️ 该脚本当前把 favorite 列为「**禁止出现该属性**」（零触碰守卫）⇒ **那是对的**，别改。

#### FB-03 · A2 保护链的「链构成」只读入口

| 项 | 结论 |
|---|---|
| 1 现象 | 链构成由两个条件分支决定（`:65` shedding 需 `enabled && maxCpuPermille>0`；`:72` breaker 需 `enabled`）⇒ 域级守门无法**分别**断言 breaker / shedding ⇒ 只能「关掉降载换确定性」⇒ 断言被迫退化（名义上写 breaker/shedding，实际只证明了 breaker） |
| 2 Go 侧出处 | go-zero v1.10.2 `zrpc/server.go:157-159`（Breaker，默认 true）+ `:160-163`（Shedding，`CpuThreshold > 0` 才挂）⇒ Go 侧四个拦截器**无条件按序装上** |
| 3 RAG 侧现状 | `service/common/protection/GoStyleRpcProtectionChain.java:61-78` —— 链构成不可从外部查询 |
| 4 证据强度 | **L1**（两侧源码逐字对照） |
| 5 建议修法 | **Y = 「每一层是否在链上，可被域级守门直接断言」** ⇒ 共享层暴露**只读**的「链层构成」查询入口 ⇒ **零行为改动** |
| 6 影响面 | 共享层 `service/common/protection/*` ⇒ 被 9 个 rpc 域依赖 |
| 7 是否阻塞本波 | **否**（守门能力缺口） |

**本层实际做了什么**：
- `GoStyleRpcProtectionChain`：新增 `Layered`（**继承** `ServerInterceptor` ⇒ 匿名类可同时是「拦截器」+「带层名」；另立接口则匿名类**无法**实现）、`Composition` + `compose(props)`（**唯一**构成计算点，`build` 内部**消费**它 ⇒ 「配置推出的构成」与「实际构造出的链」不可能不一致）、`layerNames(chain)`（对**已构造出的链**的观测）。
- ⚠️ `build` 改为消费 `compose(props)` ⇒ **条件与顺序逐字不变** ⇒ 零行为改动。
- 配套守门 7 条（`GoStyleRpcProtectionChainCompositionTest`），含「四种开关组合下 compose() 与实际链**逐一吻合**」这条**单一来源**断言。

#### 三张单的**变异验证**记录（红测试必须红在目标断言上）

| 变异 | 结果 | 恢复后 |
|---|---|---|
| FB-01：撤掉归一层的自动配置注册 | **4 failures / 0 errors**（干净 `AssertionError`，红在 4 个路径变体那 4 条） | 全绿 |
| FB-02：把 `compose()` 与 `build()` 的条件拆成两份（制造不一致） | 由「compose 与实际链逐一吻合」那条断言抓住 | 全绿 |
| FB-03：摘掉降载层的 `Layered` 标记 | **5 failures / 0 errors**（干净 `AssertionError`，红在层名列表那 5 条） | 全绿 |

### 4.7 🆕 不需落码项（3 条）+ 一条**不重复派单**

| # | 事项 | 处置 | 落点 / 归属 |
|---|---|---|---|
| 1 | **A9 轴定义措辞缺陷**（轴定义应分层表述：共享层 `sea.*` 两侧一致；域特有按域：comment `audit.*`、favorite `biz.*`、admin `audit.*`） | **建议项 2**（§4.4）已登记 | `docs/DRIFT/_cross/domain-common-lanes.md` = 共享面 ⇒ 整合者订正 |
| 2 | **AGENTS.md §6.3 问题 4 漏记 `article-rpc`** | **建议项 3**（§4.4）已登记 | `AGENTS.md` = 共享面 ⇒ 整合者订正 |
| 3 | 🔴 **`DRIFT-HOTRPC-004` 的范围澄清** | **登记（防销错账）**：它闭的是 **`hot-api` 侧**，**不是 `hot-rpc` 侧** ⇒ ⇒ **别把它当「hot-rpc 侧已闭合」销掉**；另：权威面里「client breaker 是否真在链上」那条编号是 **`U4`**（`hot/api.md` §9 Unresolved 表），**不是** `DRIFT-HOTRPC-004`（那条是**另一条**：Redis 客户端重试） | 域台账（`docs/DRIFT/hot/**`）= 域侧 ⇒ 回执时已提醒对应 owner |
| 4 | **`E65`** | **不重复派单**：已是 global 台账的 OPEN 项 ⇒ 重复派会造出两个编号指同一件事 | 已在 global 台账 ⇒ 本 lane 不再登记 |

### 4.8 🆕 单 4（Jason 拍板）—— MyBatis-Plus 排序桥进**共享层主 jar**（核对日期 2026-10-27 · 基线 `align/g4-service-common` @ `a1a2e05`）

> **来源**：G3 的回执转达（Jason 拍板原方案 = **桥放主 jar + 各域删自己那份**）。

#### 4.8.1 🔴 本层先前的顾虑**不成立**（但**是我自己**的 grep 假阳性，不是对方的错）

| 项 | 结论 |
|---|---|
| 我先前的顾虑 | 「桥放主 jar ⇒ 会波及 16 个消费者的 MP 自动配置启用状态 ⇒ 是行为变更」 |
| **实测** | **有效 classpath 上有 mybatis-plus 的只有 9 个 rpc**（article / comment / favorite / follow / like / message / task / users-user / users-admin）；**其余 11 个**（10 个 api ＋ `hot-rpc`）**没有** ⇒ ⇒ 那 9 个**各自已有同向的域侧桥** ⇒ ⇒ 放主 jar 只是**多一个同向排序节点、无环、功能等价** ⇒ ⇒ **不改变任何消费者的启用状态** |
| ⚠️ **我错在哪** | 我用 `grep -rln "mybatis-plus" --include=pom.xml service/` 判定 ⇒ **命中了 `task-api/pom.xml` 里的注释文本**（「刻意**不**引入的：mybatis-plus / flyway —— task-api 不碰 DB」）⇒ ⇒ **把「注释里提到」当成了「依赖里有」** ⇒ ⇒ **假阳性** ⇒ ⇒ 权威口径是 `mvn dependency:list`（实测：task-api **无** mybatis）⇒ ⇒ 对方的实测是对的 |
| ⇒ 教训 | 「某依赖在不在 classpath 上」**必须**用 `mvn dependency:list` 判定；grep pom.xml 分不出「直接依赖 / `<exclusions>` 排除 / 注释提及」三种 ⇒ 与「javadoc 污染 grep 计数」「跨行数组注解单行正则必然 0 命中」同族 |

#### 4.8.2 本层落码：**用字符串形式** ⇒ 对「没有 MP」的消费者**结构性安全**

- 新增 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java`（**主 jar**）并登记进 `service/common` 的 `AutoConfiguration.imports`。
- 🔴 **关键取舍：排序注解写成 `afterName` / `beforeName`（字符串），不是类字面量**。
  Boot 4 的 `@AutoConfiguration` 有 `beforeName`/`afterName`
  （`@AliasFor(annotation = AutoConfigureBefore.class, attribute = "name")`）
  ⇒ ⇒ 类名只是**字符串** ⇒ ⇒ **目标类在不在 classpath 上都不影响本类的加载**
  ⇒ ⇒ 若写类字面量（域侧那 9 份的写法）⇒ 任何在**反射**层面读该注解的消费者
  ⇒ 抛 `TypeNotPresentException` ⇒ ⇒ **上下文直接起不来**。
  ⇒ ⇒ **本层这版比域侧那 9 份更稳**（域侧那 9 份能工作，只因为它们所在模块恰好都有 MP）。
- **顺带**：删掉单 1 探针原先自造的 **test scope** 那座桥（探针在 `service/common` 里、看不到域侧那 9 座，当时必须自造）
  ⇒ ⇒ 探针改为**复用主 jar 这座** ⇒ ⇒ **单 1 的守门从此直接验证共享层主 jar 的桥**（更强）。

#### 4.8.3 证据（两个方向都验）

| 场景 | 验证 | 结果 |
|---|---|---|
| **有 MP** | `MybatisPlusInterceptorChainGateTest`（真 PG + 真 mapper + 三层加码） | **3 / 3 绿** |
| **没有 MP** | article-api 的 `ArticleApiAssemblyWiringTest`（**真 Tomcat + 真应用上下文**；article-api classpath 上**没有** MP） | **13 / 13 绿** ⇒ ⇒ 桥在无 MP 消费者上**不炸上下文** |

#### 4.8.4 🔴 域侧协调项（**各域写权，本层不代删**）

- **9 个 rpc 组件各自的域侧桥**（例：`service/message/message-rpc/src/main/java/com/rag/message/message/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`）可删 ⇒ ⇒ 删后全仓只剩**共享层这一份**。
- ⚠️ **删除前置**：必须先让共享层这份（`a1a2e05`）**合进各域的树** ⇒ 否则删掉域侧那份 ⇒ 该域**彻底失去**排序桥 ⇒ MP 自动配置不生效。
- ⚠️ **共存期间无风险**：两份桥是**同向**排序边 ⇒ 排序图多一个同向节点、**无环、功能等价** ⇒ ⇒ 不改变任何行为 ⇒ ⇒ **删除不是「必须立刻」**，是「合并后清理」。
- ⚠️ **顺带发现（不在本层写权，也不在本单范围）**：`task-api` 的 pom 里**没有** MP（实测 `dependency:list`）⇒ ⇒ 它**本来就不需要**桥 ⇒ ⇒ 本单**没有**改变 task-api 的任何行为。

### 4.9 🆕 G1 的 7 张派单（2026-10-27）—— **逐条独立复核 + 本层处置**

> **来源**：G1 会话 `mvs_0aae96a903c14c8ea5f0fcc822f8df75`（2026-10-27 送单，声明「不需要额外确认回复，按 C2 自行排期落码 ⇒ 回执 4 项即可」）。
> ⚠️ **本节所有「前提成立与否」的判断都是本层独立复核的结论**，不是采信派单快照（AGENTS.md §3.3.1）。
> ⚠️ **MaxConns 那半边是已签字偏离** ⇒ 按 §5.3b **不重复质疑、也不「顺手修正回旧行为」**。

#### 4.9.1 总表：7 单逐条定性

| # | 派单条目 | 前提**成立**？ | 本层处置 | 落点（独立核准） |
|---|---|---|---|---|
| **1** | `DRIFT-USERAPI-013` / `DRIFT-FOLLOW-008` 同族：Go 每路由一个 latch + breaker 键用注册期模板（404 不得用 raw URI 兜底） | **半成立** | 🔴 **MaxConns 半边 = 已签字 `SD-2026-10-25-01` ⇒ 不修**；🟢 **breaker 键半边 = 真漂移 ⇒ 已落码** | `service/common/.../protection/GoStyleBreakerFilter.java` + 新增 `GoStyleRouteTemplateResolver` / `GoStyleRouteTemplateSource` / `GoStyleRouteResolution` / `GoStyleBreakerRouteTemplateAutoConfiguration` |
| **2** | `DRIFT-ARPC-010`（= **E66**）：Redis 写不绑 deadline | **成立** | 🟡 **本轮不落码 ⇒ 转立项**（路线已探明，但有一处**硬约束**，见 §4.9.4） | 共享层机制可行（零域侧改动），但落点涉及 `service/common` 的 lettuce 依赖 + 各域调用点语义 |
| **3** | `UNRESOLVED-ARPC-002`（= **E38②**）：有效期限 = min(客户端 deadline, 服务端 timeout) ⇒ `deadlineEpochMs` + 超时调度器延时两处都要改 | **❌ 前提已过期** | 🟢 **不落码**（① 第三十一轮已修；② 第四十六轮已否决原修法并窄化成 E65/E66） | 已在 `service/common/.../observability/RpcInterceptor.java`（`af416d9`） |
| **4** | `DRIFT-ADMINAPI-007`：503 响应 `Content-Type` charset 分号后空格 | **成立**（残差） | 🟢 **本轮不做 + 登记在案**（兄弟条 `DRIFT-MSGAPI-010` 用户已拍板「保持登记，本轮不做」⇒ 同类同先例 ⇒ **不升级为拍板题**） | 属 `admin-api` 域（`DRIFT-ADMINAPI-007`）⇒ 不在本层写权 |
| **5** | `UNRESOLVED-USERSCOMMON-003`：`GoStyleCoercion::applyTo` 的全局副作用 | **❌ 前提不成立** | 🟢 **不落码**（口径已过期；且已注册的 8 个 api **不会误伤**） | 台账口径订正属 **users 域台账写权** ⇒ 归 G1 |
| **6** | `DRIFT-USERRPC-V-002`：`users_user.status` NOT NULL 两侧不同 | **成立** | 🔴 **不落码 ⇒ 跨 lane 协调**（放开可空会**立刻激活 admin-rpc 的同款缺陷** ⇒ 必须与 admin 侧同改 ⇒ 那半边在 **G1 写权面**） | DDL 落点 `service/users/users-common/src/main/resources/db/01_create_user.sql:39`（**本层写权**）· 配套 `service/users/admin/admin-rpc/.../domain/UserEntity.java:60`（**G1 写权**） |
| **7** | `DRIFT-USERAPI-014` 跨域推广：Redis 断连 ⇒ fail-open（不排队等重连）⇒ 机制上移到 `service/common` | **成立** | 🟢 **已落码**（零域侧改动 ⇒ 覆盖所有用 Redis 的组件） | 新增 `service/common/.../redis/GoStyleRedisClientAutoConfiguration.java` |

#### 4.9.2 🔴 #1 的 MaxConns 半边 = **已签字偏离 ⇒ 不修**（§5.3b）

| 项 | 结论 |
|---|---|
| 派单说法 | 「Go 每路由一个 MaxConns latch」⇒ 落点 `service/common/.../protection/GoStyleMaxConnsFilter.java` |
| **独立复核** | Go 侧确实每路由一个：`rest/engine.go:99-101`（`bindRoute` ⇒ `route` 是**逐路由参数**）→ `rest/engine.go:131-132`（`buildChainWithNativeMiddlewares` ⇒ 逐路由调 `handler.MaxConnsHandler`）→ `rest/handler/maxconnshandler.go:12-20`（每次 `syncx.NewLimit(n)`） |
| **但** | 这条**已经签字**：`docs/Intentional_Deviations/global/common-tails.md:11-31` 的 **`SD-2026-10-25-01`**（2026-10-25 用户拍板，签字决定 = **保留 RAG 的全局闸门，不改成 per-route**）⇒ ⇒ **不修、不重复质疑、也不「顺手改回」** |
| ⚠️ 台账口径冲突（**共享面，本层不回写**） | `docs/DRIFT/_cross/domain-common-lanes.md:125` 仍把 `DRIFT-FOLLOW-008` 列为「🔴 未修」⇒ **已过期**（签字后应从 DRIFT 侧移除）⇒ ⇒ **登记给整合者**（该文件属共享面 ⇒ 本 lane 不写） |

#### 4.9.3 🟢 #1 的 breaker 键半边 = **真漂移 ⇒ 已落码**

**Go 出处（消费点，逐条核过）**：

| Go 事实 | 位置 | 对 RAG 的含义 |
|---|---|---|
| breaker 是**逐路由**建的 | `rest/engine.go:99-101` + `:131-132`（`handler.BreakerHandler(route.Method, route.Path, metrics)`） | 隔离单位 = 路由，不是具体 URI |
| breaker 的**名字** = `METHOD://route.Path` | `rest/handler/breakerhandler.go:15`（`breakerSeparator = "://"`）+ `:19`（`breaker.WithName(strings.Join([]string{method, path}, breakerSeparator))`） | 键必须是**注册期模板** |
| **404 不经过 breaker** | `rest/router/patrouter.go:59-78`（没匹配上 ⇒ `handleNotFound`）→ `rest/engine.go:244-268`（`notFoundHandler` 的链**只有 Trace + Log** ⇒ **没有 MaxConns / Breaker / Shedding**） | 未命中路由**不得**进 breaker |

**RAG 侧修前状态**：`GoStyleBreakerFilter.routeKey` = `request.getMethod() + "://" + request.getRequestURI()` ⇒ ① 每个**具体 URI** 一个 breaker ⇒ ② 404 也进 breaker。

**可观察面（按域实测，不是推断）**：

| 组件 | 有无路径变量 | 后果 |
|---|---|---|
| **article-api** | **有** —— `ArticleController.java:137,152,167,181`（`/article/{id}`、`/me/article/{id}` 等 4 条）；Go 侧 `service/article/api/internal/handler/routes.go:28,33,38,51` 是模板 `/article/:id` | 🔴 **可观察**：Go 上 `/article/1` 持续 5xx 会把同模板的 `/article/2` 一起挡住，RAG 修前不会 |
| 其余 9 个 api | **零**路径变量（`grep 'Mapping("[^"]*{'` 只命中 article-api 1 个文件） | ⇒ 模板 == 具体 URI ⇒ **修前修后无差别** ⇒ 爆炸半径变化**只发生在 article 域** |

**本层落码**（4 个文件，全部在 `service/common/**`）：

1. `protection/GoStyleRouteResolution.java`（新）—— 三态结果（`TEMPLATE` / `NOT_MATCHED` / `UNKNOWN`）。⚠️ **刻意不引用任何 Spring MVC 类型** ⇒ 因为它会被 Filter 引用 ⇒ 而**没有 spring-webmvc 的进程（10 个 rpc）加载不了 MVC 类型** ⇒ `NoClassDefFoundError`。
2. `protection/GoStyleRouteTemplateSource.java`（新）—— 窄接口（同样零 MVC 类型）⇒ Filter 只依赖「能解析模板」这件事，**不依赖实现**。
3. `protection/GoStyleRouteTemplateResolver.java`（新）—— 实现：用 **Spring 自己的** `HandlerMapping.getHandler(request)`（= DispatcherServlet 走的同一条匹配路径 ⇒ **不重新实现路由匹配**）⇒ 读 `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`。
4. `protection/GoStyleBreakerRouteTemplateAutoConfiguration.java`（新）—— `@AutoConfiguration` + `@ConditionalOnClass(HandlerMapping.class)` + `@ConditionalOnWebApplication(SERVLET)`。

**⚠️ 三个刻意的设计决定**：

| 决定 | 为什么 |
|---|---|
| **三态返回**（`TEMPLATE` / `NOT_MATCHED` / `UNKNOWN`） | `UNKNOWN`（没有 HandlerMapping / 调用抛异常 / 属性缺失）⇒ 调用方**回落旧行为**（raw URI 分桶、照常进 breaker）⇒ ⇒ **绝不因为「解析失败」而悄悄关掉熔断保护** |
| **不直接让 Filter 依赖 resolver 类型** | 无 MVC 的进程会在**注入点解析 Bean 类型**时加载那个类 ⇒ `NoClassDefFoundError` ⇒ **硬失败** ⇒ 故抽出零 MVC 类型的接口 |
| **`@ConditionalOnMissingBean` 去重** | 本层两个 `@AutoConfiguration` 位于 `com.rag.common.**` ⇒ **10 个 api 的 `scanBasePackages` 含 `com.rag.common`** ⇒ 同一个类会**既被组件扫描、又被自动配置清单**各注册一次 ⇒ 而两者的 `@Bean` 方法名**相同** ⇒ 不同名冲突 ⇒ **启动直接失败**（Boot 默认不允许 bean 覆盖）⇒ ⇒ 必须在 `@Bean` 上加 `@ConditionalOnMissingBean`（与既有 `GoStylePathNormalizingAutoConfiguration` 同一套办法） |

**依赖处置**：`service/common/pom.xml` 新增 **`spring-webmvc`（provided）** ⇒ **provided 不传递** ⇒ **16 个消费者的 classpath 零变化** ⇒ 只有本来就带 Spring MVC 的 api 进程会用上。

#### 4.9.4 🟡 #2（E66）—— **本轮不落码 ⇒ 提请立项**（路线已探明 + 一处硬约束）

**路线（已探明，可行）**：`io.lettuce.core.TimeoutOptions.TimeoutSource` 是**抽象类**，
`public abstract long getTimeout(RedisCommand<?,?,?>)` ⇒ **逐命令动态求值** ⇒
⇒ 共享层可以返回一个「当前请求的剩余 deadline」⇒ **Redis 命令被 deadline 约束** ⇒ **零域侧改动**。

**🔴 但有一处硬约束（本轮实测字节码得出，**改法必须绕开它**）**：
Boot 4 的 `LettuceConnectionConfiguration.createClientOptions` 里的**顺序**是：

```
offset 146: ClientOptions$Builder.timeoutOptions(TimeoutOptions)      ← 先写「命令超时」
offset 151: ObjectProvider.orderedStream()                            ← 后应用 customizer
```

（`spring-boot-data-redis-4.0.8.jar` ⇒ `javap -c` 实测；timeout 来源 = `DataRedisProperties.getTimeout()`，
即 `spring.data.redis.timeout`。）⇒ ⇒ **customizer 里再调一次 `timeoutOptions(...)` 会整体覆盖运维配的命令超时** ⇒ ⇒ **那是新漂移** ⇒ ⇒ 实现时必须**先读回 `spring.data.redis.timeout`** 再包一层 source（无 deadline 时回落到该值）⇒ ⇒ 需要一轮专门实现。

**另两处待定（立项时要一起答）**：
1. **载体不统一**：rpc 路径用 `RpcInterceptor.DeadlineState`（epoch 毫秒，静态 `remainingMillis()`）· api 路径用 `GoStyleRequestDeadline`（`nanoTime` 纳秒）⇒ ⇒ 要么统一，要么 source 两处都读。
2. **无 deadline 时的回落值**：Go 侧无 ctx deadline 的 Redis 调用由 go-zero 自己的 `Timeout` 决定；RAG 侧今天靠 `spring.data.redis.timeout` ⇒ ⇒ 回落值必须**等于现状**，否则又是新漂移。

**为什么本轮不硬落**：E66 在 global 台账里本来的定性就是「**落点跨域 ⇒ 只记账 + 提请立项，实施需各域 owner 参与**」
（`docs/DRIFT/global/_README.md` §七之二十）⇒ ⇒ 且它还缺**双侧活体证据**（E66 现有证据强度 = 源码级，**未经双侧活体验证**）
⇒ ⇒ 按 AGENTS.md §3.3「凡写『与 Go 一致』必须有两侧同时跑的探针」，**先把机制落了但没有双侧证据**＝ 半个闭环 ⇒ ⇒ 本轮**只提交机制可行性结论 + 硬约束**。

#### 4.9.5 🟢 #3 = **前提已过期 ⇒ 不落码**（`af416d9` 已把两处都改好）

| 项 | 证据 |
|---|---|
| 派单要求 | 「`deadlineEpochMs` 计算 + 超时调度器延时**两处都要改**」 |
| **实际状态** | 🔴 **两处都已在共享层改好**（第三十一轮，`af416d9`）⇒ `git merge-base --is-ancestor af416d9 HEAD` ⇒ **YES**（是本分支祖先） |
| 逐行位置 | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:234`（`effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）· `:235-237`（`deadlineEpochMs`）· `:263`（超时调度器延时用 `effectiveTimeoutMs`）· `:592`/`:595`（`clientDeadlineRemainingMs()` 取 `io.grpc.Context.current().getDeadline()`） |
| ② 架构级那半边 | 🔴 **第四十六轮已否决原修法**（「handler 挪受控 executor + 超时 interrupt」）并把第③层窄化成 **E65（出向 gRPC 不继承 deadline）** + **E66（Redis 不感知 deadline）** ⇒ ⇒ **不在本单范围** |
| ⇒ 结论 | **不需要落码** ⇒ 回执里给出上面 5 个行号 + 提交号即可 |

#### 4.9.6 🟢 #5 = **前提不成立 ⇒ 不落码**（口径已过期，且已注册的 8 个 api 不会误伤）

**派单的前提**：「通用版 `GoStyleCoercion::applyTo` 被 user-api / admin-api / **favorite-api** 三域注册 ⇒ 全局副作用是否波及非 users 域」。

**独立复核（实测）**：

| 项 | 实测 |
|---|---|
| 派单说的「三域」 | ❌ **不成立** ⇒ 实测**已注册 8 个 api**：article / comment / favorite / follow / like / message ＋ users 的 user-api / admin-api |
| 台账口径「通过 `JsonMapperBuilderCustomizer` **全局施加**」 | ❌ **已过期** ⇒ `GoStyleCoercion` 自身 javadoc `:96-103` 明写「**刻意不做 `@Configuration`**」（users-common 也被纯 gRPC 的 admin-rpc 用着）⇒ 改由**各 api 各自注册** |
| **未注册**的两个 api 是否漏了 | 🟢 **都不是漏**：**hot-api** ⇒ **零 `@RequestBody`**（规则不适用）；**task-api** ⇒ 有 `@RequestBody GetTaskReq`，但 Go 侧该类型带**自定义 `UnmarshalJSON`** ⇒ Go 侧**也不走**标准 mapping 规则 ⇒ RAG 已在 DTO 层手写镜像（`GetTaskReq.setId(Object)` / `normalize` / `numberLiteral`，对应 `service/task/api/internal/types/types.go:16-36`）⇒ ⇒ **规则不适用** |
| **会不会误伤**（真正的问点） | 🟢 **不会** ⇒ **Go 全仓只有 1 个自定义 `UnmarshalJSON`**（`service/task/api/internal/types/types.go:16`）⇒ ⇒ 凡注册了全局规则的 api，其 Go 侧请求类型**都走标准 mapping** ⇒ ⇒ 规则与 Go 逐条一致 |
| ⇒ 结论 | **无漂移，不落码**；台账那条的**问法**（「全局副作用」）需订正 ⇒ **该文件属 users 域台账写权 ⇒ 归 G1 改** |

#### 4.9.7 🔴 #6 = **真漂移，但本层不能单独改 ⇒ 跨 lane 协调**

| 项 | 证据 |
|---|---|
| 漂移成立 | Go：`service/user/user/rpc/internal/model/data_model.go:11` = `Status int64 \`gorm:"column:status;default:0"\`` ⇒ GORM **未标 `not null`** ⇒ 建出**可空**列；RAG：`service/users/users-common/src/main/resources/db/01_create_user.sql:39` = `status BIGINT NOT NULL DEFAULT 0` ⇒ 🔴 **可空性不同**（真库实证见 `docs/DRIFT/users/user/rpc.md:1518`） |
| **为什么不能单独改** | 🔴 `docs/DRIFT/users/user/rpc.md:1559` 明写：「**动它会立刻激活 admin-rpc 的同款缺陷**」—— `service/users/admin/admin-rpc/.../domain/UserEntity.java:60` 是 `private Long status = 0L;` ＋ MyBatis `callSettersOnNulls=false` ⇒ **NULL 读成 0** ⇒ admin 侧会把 NULL 当「活跃」⇒ ⇒ **必须与 admin 侧一起改** |
| **写权归属** | DDL（`service/users/users-common/**`）= **本层写权**；`admin-rpc` 的 `UserEntity` = **G1 写权** ⇒ ⇒ **跨 lane** |
| **可安全排序的两步方案**（建议给 G1） | **第 1 步（G1）**：先把 `admin-rpc` 的 `UserEntity.status` 改成能表达 NULL（此时 DDL 仍 NOT NULL ⇒ **不可能出现 NULL ⇒ 行为零变化**）→ **第 2 步（本层）**：放开 DDL 的 NOT NULL ⇒ ⇒ **中间态两侧都合法，不留半截** ⇒ ⇒ 可绕开「必须同一次提交」的物理约束 |
| ⚠️ **另注（这才是裁决题）** | 即便放开可空，**Go 侧读到 NULL status 会扫描报错**（GORM 把 NULL 扫进**非指针** `int64` ⇒ `converting NULL to int64 is unsupported`）⇒ 而 RAG 读成 0 ⇒ ⇒ **NULL 行上两侧行为仍不同** ⇒ ⇒ **这是「保留 NOT NULL（并把 V-001 按不可达降级）」vs「放开可空（V-001 变成实打实的安全缺陷）」的裁决** ⇒ ⇒ **需 Jason 拍板** ⇒ 本层**不擅自落码** |

#### 4.9.8 🟢 #4 = **本轮不做 + 登记在案**（不升级为拍板题）

- 事实：`DRIFT-ADMINAPI-007` = 503 响应的 `Content-Type` 应为 `text/plain; charset=utf-8`（分号后**有**空格）；`nosniff` 那半边**已修**，**只剩 charset 空格残差**。
- **先例**：兄弟条 `DRIFT-MSGAPI-010`（message-api 同款）用户已拍板「**保持登记，本轮不做**」⇒ ⇒ **同类同先例** ⇒ 本轮**不做 + 登记在案** ⇒ **不把登记类事项升级成「请你拍板」**（用户偏好）。
- 落点：`admin-api` 域 ⇒ **不在本层写权** ⇒ 也不由本层代改。

#### 4.9.9 🟢 #7 = **已落码**（零域侧改动 ⇒ 覆盖所有用 Redis 的组件）

**Go 出处（消费点）**：

| Go 事实 | 位置 |
|---|---|
| 黑名单查询失败 ⇒ **放行**（fail-open） | `service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35-36`（`if err == nil && exists` ⇒ **err != nil 不拦截**）· 同款另见 `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go` 与 `service/follow/api/internal/middleware/checkblacklistmiddleware.go`（⚠️ **三份文件 md5 各不相同**，但「err != nil ⇒ 放行」三条一致） |
| Redis 客户端**快速失败** | `core/stores/redis/redisclientmanager.go:18`（`maxRetries = 3`）· `:27-46`（`red.NewClient(&red.Options{Addr, MaxRetries: maxRetries, MinIdleConns: idleConns})` ⇒ **未设** `DialTimeout`/`ReadTimeout` ⇒ 用 go-redis v9 默认值 ⇒ 连接被拒时**立即失败** ⇒ 实测 ~1.7s） |

**本层落码**：新增 `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java`
⇒ 注册 `LettuceClientOptionsBuilderCustomizer` ⇒ `builder.disconnectedBehavior(REJECT_COMMANDS)`
⇒ **零域侧改动**即让**所有**用 Redis 的组件拿到 Go 的失败语义：

| 组件 | 修前 | 修后 |
|---|---|---|
| user-api | ✅ 已有域内配置（2026-10-24 第九轮修 `DRIFT-USERAPI-014`） | 不变 |
| **follow-api** | 🔴 断连 ⇒ 命令**挂起**等重连 ⇒ 撞超时预算 ⇒ **503**（Go 是 **200**） | ✅ 断连即拒单 ⇒ 快速失败 ⇒ fail-open |
| **favorite-api** | 🔴 同上 | ✅ 同上 |
| comment / like / hot / task 的 **rpc** | 🔴 缓存读写断连时挂起（Go 侧 `ExistsCtx`/`ZRevRangeWithScores`/`HGetAll` 全 ctx-aware ⇒ 快速失败） | ✅ 同上 |

**⚠️ 两个刻意的决定**：

1. **不碰 `timeoutOptions`** ⇒ 见 §4.9.4 的字节码证据 ⇒ 只改断连行为 ⇒ **运维配的 `spring.data.redis.timeout` 原样保留**。
2. **与 users 域既有那份并存 = 幂等** ⇒ 两份都设 `REJECT_COMMANDS` ⇒ Boot 会依次应用所有 customizer ⇒ **后覆盖前、值相同 ⇒ 结果相同** ⇒ ⇒ users 域那份可**合并后单独删除**（纯删除、零行为变化）⇒ **删除前置 = 共享层这份先合进各域的树**。

**依赖处置**：`service/common/pom.xml` 新增 **`lettuce-core` + `spring-boot-data-redis`（均 provided）** ⇒ **不传递** ⇒ 16 个消费者的 classpath 零变化 ⇒ 没有 Redis 的进程由 `@ConditionalOnClass` 挡住。

#### 4.9.10 本波落码清单 + 验证（核对日期 2026-10-27 · 基线 `align/g4-service-common` @ `138b6ef`）

| 工单 | 提交 | 落码文件（全部在 `service/common/**`） |
|---|---|---|
| **#7** | `1fb26b2` | `main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java`（新）· `src/test/java/com/rag/common/redis/GoStyleRedisClientOptionsGateTest.java`（新）· `pom.xml`（+`lettuce-core` / `spring-boot-data-redis`，均 provided）· `AutoConfiguration.imports`（+1 行） |
| **#1** | `138b6ef` | `main/java/com/rag/common/protection/GoStyleRouteResolution.java`（新）· `GoStyleRouteTemplateSource.java`（新）· `GoStyleRouteTemplateResolver.java`（新）· `GoStyleBreakerRouteTemplateAutoConfiguration.java`（新）· `GoStyleBreakerFilter.java`（改）· `src/test/java/com/rag/common/protection/GoStyleBreakerRouteTemplateGateTest.java`（新）· `pom.xml`（+`spring-webmvc`，provided）· `AutoConfiguration.imports`（+1 行） |

**守门（新增 6 条）+ 变异（3 条全精确命中）**：

| 守门 | 断言 | 变异 | 变异结果 |
|---|---|---|---|
| `GoStyleBreakerRouteTemplateGateTest#sameTemplateSharesOneBreaker` | 同模板的两个具体 URI **共用一个** breaker（真 Tomcat + 真 Spring MVC + 真 `@GetMapping("/x/{id}")`） | 键退回 raw `getRequestURI()` | 🔴 **1 failures**，红在**目标断言 ①**（另两条仍绿 ⇒ 精确命中） |
| `GoStyleBreakerRouteTemplateGateTest#unmatchedRouteDoesNotCreateBreaker` | 404 **不得**创建 breaker | 去掉「未命中跳过」分支 | 🔴 **1 failures**，红在**目标断言 ②** |
| `GoStyleBreakerRouteTemplateGateTest#everyRegisteredRouteResolvesToTemplate` | **每条**已注册路由都必须能解析出模板（防「解析失败 ⇒ 静默失去熔断保护」） | —— | —— |
| `GoStyleRedisClientOptionsGateTest#disconnectedBehaviorIsRejectCommands` | `ClientOptions.getDisconnectedBehavior() == REJECT_COMMANDS` | 退回 `DEFAULT` | 🔴 **1 failures**，红在**目标断言 ①** |
| `GoStyleRedisClientOptionsGateTest#customizerIsRegisteredExactlyOnce` | customizer **有且只有一份**（单一来源） | —— | —— |
| `GoStyleRedisClientOptionsGateTest#commandTimeoutIsNotClobbered` | `timeoutOptions` **未被**本层改动（运维配的 `spring.data.redis.timeout` 要保住） | —— | —— |

**回归口径**：

| 范围 | 结果 |
|---|---|
| `service/common` 模块 | **298 条 / 0 failures / 0 errors**（修前 292 ⇒ **净增 6**）· BUILD SUCCESS |
| 三门禁 | `check-wave-lane-ownership` **EXIT=0** · `check-wave-role-model` **EXIT=0** · `check-wave-coupling` **EXIT=0** |
| **全量 `mvn -B clean verify`** | ✅ **BUILD SUCCESS / EXIT=0** · **52 个模块** · **2448 条**（基线 2442 + 本波新增 6）· **failures 0 / errors 0 / skipped 4** · **23:04 min** · 基线 `align/g4-service-common` @ `fc783fb`（代码态 `63079ed`）<br>⚠️ **中间跑过 3 次非绿，原因逐条查清并登记**：① 第三次（`138bac3`）= comment-rpc 一条**既有时序 flake**（见 §4.9.12）⇒ 单独复跑通过；② 第四次 = **EXIT=137（被系统 SIGKILL）** ⇒ 根因是**另一个 lane 同时跑 maven** ⇒ **不是代码问题**（见 §4.9.13）⇒ ⇒ 第五次独占重跑 ⇒ **全绿** |
| **写权零越权** | 本波改动文件**全部**在 `service/common/**` ＋ 本文件 ⇒ **未触碰** `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/DRIFT/global/_README.md` / `docs/Worktree.md` / `tools/difftest/**` |

#### 4.9.11 🔴 **需回写给整合者 / G1 的共享面口径订正**（本 lane 不写那些文件）

| 文件 | 需订正的内容 | 订正方向 |
|---|---|---|
| `docs/DRIFT/_cross/domain-common-lanes.md:125` | `DRIFT-FOLLOW-008` 仍列为「🔴 **未修**」 | ⬆️ **已过期** ⇒ 2026-10-25 用户签字（`SD-2026-10-25-01`）后应从 DRIFT 侧移除 |
| `docs/DRIFT/users/common.md` §6 | `UNRESOLVED-USERSCOMMON-003` 的问法（「**全局副作用**是否波及非 users 域」） | ⬆️ **前提已过期**（不是全局施加，是各 api 各自注册）⇒ 且实测**已注册的 8 个 api 不会误伤** ⇒ 可直接结案（**该文件属 users 域台账写权 ⇒ 归 G1 改**） |
| `docs/DRIFT/users/user/rpc.md` §`DRIFT-USERRPC-V-002` | 「🔴 仍成立，但被卡」 | 仍是**成立**的 ⇒ 但「卡点」的**形状**已可细化 ⇒ 见 §4.9.7 的两步方案 + ⚠️ 「Go 侧读 NULL 会扫描报错 ⇒ 两侧在 NULL 行上仍不同 ⇒ **需 Jason 拍板**」 |

#### 4.9.12 🟡 全量回归里撞到一条**既有**时序 flake（**不是本波引入** · 属 comment 域写权 ⇒ 已通知 G3）

**现象**（第三次全量回归 · 基线 `138b6ef`）：**2448 条 / failures=1 / errors=0 / skipped=4** ⇒ 唯一失败：

| 项 | 内容 |
|---|---|
| 用例 | `service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcRuntimeBootGateTest#realFatJarProcessBootsAndListens` |
| 失败断言 | 「**日志里没有 devh 的『gRPC Server started』**」 |
| 🔴 **同一用例的另一个断言已通过** | 「**真 fat jar 起不来（gRPC 端口没有在监听）**」⇒ **端口确实在监听** ⇒ 进程**真的起来了** |
| 🔴 **classpath 也没问题** | 同用例第三条 `assertFalse(output.contains("NoClassDefFoundError"))` **通过** ⇒ ⇒ **本波新增的两个自动配置在「无 spring-webmvc 的进程」里没有炸**（这恰好是本次最该验的那条风险） |
| 根因形态 | 该用例在**端口探活成功就立刻 `process.destroyForcibly()`**（源码 `:107-119`）⇒ 日志 drain 线程（`:103-105`）可能还没读到那行 ⇒ **断言与日志读取存在竞态** |
| 单独复跑 | ✅ **通过**（实测：`mvn -o -pl service/comment/comment-rpc -am test -Dtest=CommentRpcRuntimeBootGateTest` ⇒ 1/1 绿） |
| 前两次全量 | ✅ 同样用例在基线 `433eac3` 的两次全量（各 2442 条 / 0 failures）**都过** ⇒ ⇒ **不是本波引入** |
| ⇒ 处置 | **该文件属 comment 域写权 ⇒ 本 lane 不代改** ⇒ 已按「共享层变更通知」把 flake 形态与建议修法发给 **G3**（不催、不代改）；本条登记在案 |

⚠️ **为什么这条值得单列**：它同时是**一次意外收获** —— 本波给 `service/common` 的
`AutoConfiguration.imports` 加了 2 行（其中 1 行引用 **只有 api 进程才有**的 `spring-webmvc` 类型）
⇒ 而 comment-rpc 是**纯 gRPC 进程**（没有 spring-webmvc）⇒ ⇒ 「自动配置清单里出现一个
本进程没有的类，会不会 `NoClassDefFoundError`」这个风险 ⇒ **由这条用例的真 fat jar 启动实测排除了**
（`@ConditionalOnClass` 的 ASM 元数据判定在 Boot 4 上确实安全）。

#### 4.9.13 🟡 **大回归错峰不是形式主义**：并发跑全量会被系统 **OOM-kill**（实测 EXIT=137）

| 项 | 实测 |
|---|---|
| 现象 | 第四次全量回归 **EXIT=137**（SIGKILL）⇒ 死在 **article-rpc** 的测试阶段 ⇒ **失败前 0 条断言失败** |
| 🔴 **根因** | **另一个 lane 同时在跑 maven**（G1 的 `g1-users-follow` 工作树，05:23 起跑 `*RedisConnectTimeoutSocketTest`）⇒ 两个回归同时抢 CPU/内存 ⇒ **article-rpc 的测试 JVM 被 macOS OOM-kill** |
| 排除「是我的代码」 | ① article-rpc 在**前三次**全量里都过（2442/2442/2448 条）；② 本波改动与 article-rpc **无交集**（它不扫 `com.rag.common`、无 Redis、无 spring-webmvc ⇒ 我的两个自动配置对它全部惰性）；③ 失败前 **0 条断言失败**；④ 该模块单独复跑通过 |
| 环境证据 | 机器 24 GB；`docker stats` 显示多个常驻栈（kafka ~980 MiB / nacos ~964 MiB / …）⇒ 基线占用已经不低 ⇒ 再叠两个并发回归就过线 |
| ⇒ 处置 | 等对方跑完再重跑（**第五次全量 ⇒ 见下表**）⇒ 并已按 AGENTS.md §4.1「**大回归错峰：开始前 session send 知会对方**」**知会 G1/G2/G3** |
| ⇒ 教训 | 「大回归错峰」这条约定**有真实的失败形态**（不是「慢一点」，是**被杀**）⇒ ⇒ **并发跑全量会被 SIGKILL，而不是报错** ⇒ ⇒ 排查全量回归红/中断时，**第一件事是看是不是被别人挤死的**（`EXIT=137` / `EXIT=143`），而不是先怀疑代码 |

#### 4.9.14 🟢 **G1 的 #3 订正**（状态描述撤回）＋ 顺手订正 `tools/semantic-probe/README.md` 的过期数字

**① #3 的订正 —— 与本层先前的独立复核**结论一致** ⇒ #3 仍**不落码****

| 项 | 内容 |
|---|---|
| G1 撤回的说法 | 「RAG 侧 `deadlineEpochMs = now + timeout.toMillis()` ⇒ 只算服务端自己的 2s ⇒ 客户端 deadline 完全不参与」⇒ **过期快照**，G1 已主动撤回 |
| 本层先前的独立复核（落码前做的） | 同结论：① 第三十一轮已修（`af416d9`，是我分支祖先）⇒ `RpcInterceptor.java:234`（`effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）· `:235-237`（`deadlineEpochMs`）· `:263`（超时调度器延时）· `:592`/`:595`（取 `io.grpc.Context.current().getDeadline()`）；② 架构级第四十六轮已否决原修法 ⇒ 窄化成 E65/E66 |
| **本层自己复跑双侧探针**（不采信转述） | `bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0** ⇒ `client_deadline_ctx` **一致** ⇒ **E38② 的第 ① 层确认已闭环**；另 1 条 `timeout_telemetry` 是**已签字的表述差异**（C-1，驼峰 vs 全大写）⇒ 不计入失败 |
| ⇒ 结论 | **#3 不落码**（我没动 `RpcInterceptor`）⇒ 「① 已闭环 / ② 未做且已另立项」这条表述**要写进各域台账**（AGENTS.md 与域台账里「E38② 未闭合 / ① 不销本条」的措辞**过期**） |
| ⚠️ 采纳 G1 的元教训 | 「**落码任何一条前，先跑一遍对应探针复核状态**，别照抄台账」⇒ 本层本轮 7 单里，**#3 已按此复跑探针**；**其余 6 单的落点与状态判定也都不是照抄台账**（逐条读 Go 消费点 + 逐域实测影响面） |

**② `tools/semantic-probe/README.md` 的过期数字 —— 已订正**（G1 报备 ⇒ G4 落码）

| 项 | 内容 |
|---|---|
| 过期内容 | 「当前 **23/23** 一致」「不过滤计数会变成 **24 vs 23**」「## 用例清单（**23 例**）」⇒ 而脚本实跑是 **56 例** |
| 本层独立核实 | `bash tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0**，末行 **「总计 56 例：一致 54，不一致 0，已签字偏离 2」** ⇒ ⇒ 旧 23 例 = 原有 6 组（6+6+2+3+2+4）⇒ 新增 5 组 = A(7)+B(10)+C(8)+D(6)+X(2)=33 ⇒ **23+33 = 56** ✔ |
| 落码 | `2231398`（**纯文档 ⇒ 零字节码变化 ⇒ 全量回归结果不受影响**）⇒ 三处数字改正 + **补齐 A/B/C/D/X 五组**（内容逐条取自脚本实跑输出 ⇒ 可复算）+ 新增「**怎么复核这个数字**」小节（写明这四个数是脚本实跑出来的、文档与脚本不一致时以脚本为准） |
| 顺带查清一件 | **A6 在探针源码（`go/` 与 `java/` 两半）里根本不存在** ⇒ 是**编号留坑**，**不是**有用例被静默丢掉 ⇒ 已写进 README（免得后人以为漏了） |
| 写权 | `tools/semantic-probe/README.md` **不在**整合者专属共享面清单里（那份只有 `tools/difftest/README.md`）⇒ 且本目录是**共享层的双侧探针** ⇒ 归 G4；三门禁复跑 ⇒ **EXIT=0/0/0** |

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [x] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）—— ⚠️ **`_README.md` §七之二属共享面 ⇒ 本 lane 不写**（合并后由整合者统一更新）⇒ **G3 两张 + G2 三张 + G1 七张共 12 张已登记在 §4.3~§4.9**（G1 七张的逐条定性见 §4.9.1）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）
