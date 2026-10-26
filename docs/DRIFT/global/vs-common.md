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
4. 回归口径与结果（模块数 / 用例数 / failures / errors / skipped / 耗时）。

**大回归错峰**：G4 全量 = `mvn -B clean verify`（上轮 2411 条 / 21:38 min）⇒ **开始前 session send 知会对方**（四棵工作树 `target/` 独立不互踩，仅 CPU/磁盘互抢）。

**高引用度类的响应预期**：修法落在 9 域引用类（`GoStyleProtectionProperties` / `LoggerFactoryBean`）或 8 域引用类
（`GoStyleRouterErrorResponses` / `ObservabilityProperties` / `Response` / `RpcServerInterceptorFactory`）⇒ **编译面 = 16 个组件**
⇒ G4 先自跑全量确认编译面未炸，再回执 ⇒ **此类单比普通单慢一轮**。

**零重叠已实测**（2026-10-27）：用 `check-wave-lane-ownership.py` 自己的 `expand()` + `_rel()` 口径复算
⇒ **G4 展开 145 个文件 / G3 展开 465 个文件 / 交集 = 0** ⇒ 跨树 merge 干净。

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）
