<!-- @new 2026-10-27 · `users-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G1 波次工作树（`align/g1-users-follow`）** -->

# `users-common` lane 台账 —— 「users ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/users/users-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G1` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `users-common`（**原第 9/10 条**） |
| **现分支 / 工作树** | **`align/g1-users-follow`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow`<br>⚠️ 原 `align/users-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/users/{user/{user-api, user-rpc, user-proto}, admin/{admin-api, admin-rpc, admin-proto}, users-common}` |
| 域侧台账 | [`_shared.md`](_shared.md) · [`user/api.md`](user/api.md) · [`user/rpc.md`](user/rpc.md) · [`admin/api.md`](admin/api.md) · [`admin/rpc.md`](admin/rpc.md) · [`admin/_shared.md`](admin/_shared.md) |
| **写权** | ✅ 可写**本域** `service/users/{user,admin}/**` ＋ `docs/DRIFT/users/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；🔴 **`service/users/users-common/**` 也归 G4**（跨 6 个外域 ⇒ 第二个共享层）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| 🔴 `DRIFT-ARPC-010` = **E66** | Redis 写**不绑 deadline**（admin `logout` 的 Redis 写） | `service/common`（Redis 调用路径） | 🔴 **OPEN**（两侧同编号 ⇒ 销账别当两条） | [`admin/api.md`](admin/api.md) |
| 🔴 `UNRESOLVED-ARPC-002` = **E38②** | 超时是否 **min 客户端 deadline**（Go `context.WithTimeout(parent,2s)` 继承 vs RAG 只算自己 2s）⇒ 定性为 **CONFIRMED SEMANTIC MISMATCH** | `service/common` | 🔴 **未闭合** ⇒ 修法在共享层 | [`admin/rpc.md`](admin/rpc.md) |
| 🔴 `DRIFT-USERAPI-013` | 过载保护：**MaxConns 闸门粒度** + **breaker 键空间**（Go key = 注册期 `route.Path` 模板 ⇒ 基数 = 路由数；RAG = raw `getRequestURI()` ⇒ 基数 = 参数取值数）+ 注释与实现矛盾 | `service/common` | 🔴 **待拍板**（域台账原文）；⚠️ **与 follow 域 `DRIFT-FOLLOW-008` 同族** | [`user/api.md`](user/api.md) |
| 🟡 `DRIFT-ADMINAPI-007` | 503 超时响应缺 `X-Content-Type-Options: nosniff` | `service/common` | 🟡 **半修** —— `nosniff` **已落地**（`HttpMiddleware.java:692`）⇒ 原条自动消失；**charset 分号后空格残差仍在** ⇒ 需重开一条 | [`admin/api.md`](admin/api.md) |
| `DRIFT-ARPC-006` | OTel `audit.*` span 属性（`operator_id` 不恒为 0） | `service/common`（otel 埋点） | ✅ **已修** | [`admin/_shared.md`](admin/_shared.md) |
| 🔴 `UNRESOLVED-USERSCOMMON-003` | `http/GoStyleCoercion` 的**全局副作用**是否波及非 users 域（通过 `JsonMapperBuilderCustomizer` 全局施加） | `service/common` | 🔴 **未核** —— 本轮只审了 users 域 ⇒ **需在 message/like/article 各打一次带标量的请求矩阵** | [`common.md`](common.md) |
| `DRIFT-USERRPC-V-007` | 超时 description 逐字一致（`context deadline exceeded` vs `rpc timeout after 2000ms`） | `service/common` | ✅ **已合回主干 `4f839f9`** ⇒ 双侧活体复跑一致 | [`user/api.md`](user/api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A2** | gRPC 拦截器链 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A3** | HTTP 保护链 | 本域 `application.yml` 的 `rag.http.go-form-limit.mode` = **raw（user-api ＋ admin-api 各一行）** ＋ 共享层 6 个类 | ⬜ **待 lane 复核**（共享层已验 ⇒ 缺「本域真实请求」证据） | — | — |
| **A4** | 错误码 / envelope | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A5** | 端口治理 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A6** | errmsg 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A7** | proto 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A8** | DDL 时间列 / 类型 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A9** | trace / otel | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> 本域 `go-form-limit.mode` = **raw（user-api ＋ admin-api 各一行）**；Go 侧：**未注册**（真源 `service/user/api/` 与 `service/admin/api/` 均无 `httpx.SetErrorHandler`）⇒ Go 侧 = **400 + Go 原文** ⇒ 与 RAG `mode=raw` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

- `user-api` / `admin-api` 的 A3 接线复核：各一行 `mode=raw` ⇒ 拒绝形状 = **400 + Go 原文**
- 本域**调用**共享层的方式（`operator_id` 传值 / `GoStyleCoercion` 的**调用点**）⇒ 域侧可自修的先域侧修

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

- 🔴 `DRIFT-ARPC-010`（E66）· `UNRESOLVED-ARPC-002`（E38②）· `DRIFT-USERAPI-013` ⇒ **全部落点 `service/common`** ⇒ **派单 **G4****；⚠️ `DRIFT-USERAPI-013` 与 **follow 域 `DRIFT-FOLLOW-008` 同族** ⇒ 两条一起对账
- 🔴 `UNRESOLVED-USERSCOMMON-003` ⇒ 落点 `service/common` ⇒ 派单；⚠️ 它的**验证**跨域（message/like/article 各打一次矩阵）⇒ 本 lane 登记 + 协调

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）

---

## 6. 🆕 **G1 ⇄ G4 配合范围**（常驻 · 2026-10-27 02:4x 记）

> ⚠️ 本节 = **操作口径**（谁写哪 · 怎么派 · 派单长什么样）⇒ **不替代** AGENTS.md §5.3d·§5.3e 与
> [`cross-domain-coupling.md`](../_cross/cross-domain-coupling.md) §三 **C1~C7**（那两份是规范，本节是落地版）。

### 6.1 写权边界（机器守门可证：`tools/difftest/check-wave-lane-ownership.py` ⇒ EXIT=0）

| 面 | G1（`align/g1-users-follow`） | G4（`align/g4-service-common`） |
|---|---|---|
| `service/users/{user,admin}/**`（api/rpc/proto） | ✅ **可写** | 🔴 只读 |
| `service/follow/**`（api/rpc/common/proto） | ✅ **可写** | 🔴 只读 |
| `service/common/**` | 🔴 **只读** | ✅ **可写（唯一）** |
| `service/users/users-common/**` | 🔴 **只读** | ✅ **可写（唯一）** |
| `docs/DRIFT/users/**` · `docs/DRIFT/follow/**` | ✅ 可写 | 🔴 只读 |
| `docs/DRIFT/global/vs-common.md` | 🔴 只读 | ✅ 可写 |
| `AGENTS.md` · 三个 `_INDEX.md` · `_MANIFEST.json` · `docs/DRIFT/_cross/**` · `docs/Worktree.md` · `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` | 🔴 **无人拥有** | 🔴 **无人拥有** ⇒ **合并后由整合者统一更新** |

> ⚠️ **`users-common` 的物理位置在 `service/users/` 下面，但写权归 G4** —— §5.3f：**共享判据 = 依赖度数，不是目录位置** ⇒
> 「目录在我名下」≠「我能写」。
>
> ✅ **裁定：`users-common` 整库归 G4（2026-10-27 Jason）** —— 守门 `tools/difftest/check-wave-lane-ownership.py`
> 的排他清单把 `service/users/users-common/src/**` **整库**判给 G4 ⇒ **这个口径是对的** ⇒
> §5.3f 那句「3 个 users 专用类（`ErrorCode` · `PasswordUtil` · `SchemaInitializer`）留 users 域」
> **在本面上不适用** ⇒ 🔴 **G1 一律不改 `users-common` 里的任何类（含这 3 个）** ⇒ 要改就**走派单**。
> ⚠️ 该裁定**尚未回写 `AGENTS.md` §5.3f**（共享面无人拥有 ＋ 余量 82 B ⇒ 域侧不许写）
> ⇒ **待整合者合并时统一订正** ⇒ 在那之前，**以守门口径为准**。

### 6.2 G1 → G4：派单（**反馈单 7 项**，缺项不进排期）

**触发**：G1 对账中根因落在 `service/common/**` 或 `service/users/users-common/**`。
**不触发**：域侧能自修的（**本域的调用方式 / 配置 / 断言 / 装配**）⇒ G1 自修、**不甩单**（C3）。

| # | 反馈单项 | 要求 |
|---|---|---|
| 1 | 现象 | 可复现的观察，不是结论 |
| 2 | Go 侧 `文件:行号` | 指向**消费点**，不是声明点（AGENTS.md §3.3.1） |
| 3 | RAG 侧现状 | `文件:行号` + 现状描述 |
| 4 | 证据强度 | **L1 源码级 / L3 活体对拍 / L4 真容器** ⇒ **只跑 RAG 的探针不算数** |
| 5 | 建议修法 | 口径写成「**两侧都应是 Y**」（不是「RAG 改成 X」） |
| 6 | 影响面 | 跨几个域 / 哪 16 个组件依赖它 |
| 7 | 是否阻塞本波 | 阻塞 / 不阻塞 |

**落点 = 双轨**（G1 与 G4 合并前**互相看不见对方的文件** ⇒ 实时通道只能是会话消息）：

1. **实时**：`session send` 给 G4 会话；
2. **正式**：按各自写权写台账 —— G1 写**本域** `docs/DRIFT/users/vs-common.md`（本节 §4）＋
   [`docs/DRIFT/follow/vs-common.md`](../follow/vs-common.md)；G4 写 `docs/DRIFT/global/vs-common.md`
   ⇒ **合并后整合者才看得见对方写了什么**。

**⚠️ 编号纪律（派单必须带全前缀）**：`UNRESOLVED-ARPC-002`（= E38②）**≠** `DRIFT-ARPC-002`
（`UpdateUserResp.success` 的 wire 差异，已修）⇒ 只写「ARPC-002」会让 G4 **销错账**。

### 6.3 G4 → G1：共享层变更回流

G4 落码 ⇒ 共享层行为变 ⇒ **G1 这域的 A1~A9 结论要复核**（重点：A1 超时/deadline · A3 HTTP 保护链 · A4 错误码/envelope）。
**C7 唯一风险窗口 = 收口回归跑完 ～ 合并进主干之前** ⇒ 该窗口内共享层变了 ⇒ **重跑一次收口回归**
（域波记录写明收口回归对应的 `git rev-parse HEAD`）。

### 6.4 G1 待转派 G4 的条目（**落点已按 §6.1 逐条核准** · 状态带核对日期）

| 编号 | 摘要 | 落点 | 归属 | 状态（2026-10-27 核） |
|---|---|---|---|---|
| `DRIFT-USERAPI-013` | 过载保护：MaxConns 闸门粒度 + breaker 键空间 + 注释与实现矛盾 | `service/common`（`GoStyleMaxConnsFilter` / breaker） | 🔴 **G4** | 🔴 待拍板 · **与 `DRIFT-FOLLOW-008` 同族 ⇒ 两条一起对账** |
| `DRIFT-ARPC-010`（= **E66**） | Redis 写不绑 deadline（admin `logout` 的 Redis 写） | `service/common`（Redis 调用路径） | 🔴 **G4** | 🔴 OPEN（两侧同编号 ⇒ 销账别当两条） |
| `UNRESOLVED-ARPC-002`（= **E38②**） | 超时是否 min 客户端 deadline | `service/common`（`RpcInterceptor` 的 `deadlineEpochMs`） | 🔴 **G4** | 🔴 未闭合 · Jason 已拍板：① 纯 deadline 计算下一轮做 ② 架构级另立项 ⇒ **① 不销本条** |
| `UNRESOLVED-USERSCOMMON-003` | `GoStyleCoercion` 规则是否波及非 users 域 | 🟡 **拆两半**（见 §6.4.1） | 🟡 **域侧 + G4 各一半** | 🔴 未核 · **口径已订正**（2026-10-27） |
| `DRIFT-ADMINAPI-007` | 503 响应 `Content-Type` charset 分号后空格残差 | `service/common` | 🔴 **G4** | 🟡 半修（`nosniff` 已落地 ⇒ 原条消失）⇒ **需重开一条** |

> ⚠️ **状态带核对日期**（AGENTS.md §6）⇒ 复核后就地更新 + 改日期。

#### 6.4.1 🆕 `UNRESOLVED-USERSCOMMON-003` 的口径订正（2026-10-27 实测）

**台账原措辞**：「`http/GoStyleCoercion` 的**全局副作用**是否波及非 users 域（通过 `JsonMapperBuilderCustomizer` 全局施加）」⇒ 落点 `service/common`。

**实测**（8 个 api 域逐个读自己的 `WebMvcConfig`）：

| 域 | 注册的 customizer | 变体在哪 |
|---|---|---|
| `user-api` | `GoStyleCoercion::applyTo` | `users-common`（**通用版**） |
| `admin-api` | `GoStyleCoercion::applyTo` | `users-common`（**通用版**） |
| `favorite-api` | `GoStyleCoercion::applyTo` | `users-common`（**通用版** · ⚠️ **外域直接复用**） |
| `article-api` | `ArticleGoStyleCoercion::applyTo` | 本域私有 |
| `comment-api` | `CommentGoStyleCoercion::applyTo` | 本域私有 |
| `follow-api` | `FollowGoStyleCoercion::applyTo` | 本域私有 |
| `like-api` | `LikeGoStyleCoercion::applyTo` | 本域私有 |
| `message-api` | `MessageGoStyleCoercion::applyTo` | 本域私有 |

⇒ **订正两点**：

1. **不是「全局施加」** —— `JsonMapperBuilderCustomizer` 是 **Spring 容器级 Bean**（**进程内**作用域）⇒
   各域是**独立 Spring Boot 进程** ⇒ **一个域注册它，不可能波及另一个域的进程** ⇒ 原措辞的「全局副作用」**机制上不成立**；
2. **真正跨域的是「类复用」** —— `users-common` 的**通用版** `GoStyleCoercion` 被 **user-api / admin-api / favorite-api**
   三个域注册，其中 **favorite 是外域**（它复用的正是 users 的通用版 ⇒ 规则一改，favorite 的解析语义跟着变）。

⇒ **落点因此拆两半**（这就是「域侧能自修的自修」的典型，别整条甩出去）：

| 半边 | 落点 | 归属 | 动作 |
|---|---|---|---|
| ① **哪个域注册哪个变体**（含 favorite 复用 users 通用版要不要改） | 各域自己的 `service/<域>/<域>-api/**/WebMvcConfig.java` | ✅ **域侧** | 各域自己决定（C3） |
| ② **通用版 `GoStyleCoercion::applyTo` 的规则内容** | `service/users/users-common/**` | 🔴 **G4** | 派单 |

⇒ ⚠️ **「验证跨域」的那部分**（各打一次带标量的请求矩阵）**G1 登记 + 协调 G2/G3**，
但**别把它整条塞进派单** —— 矩阵是域侧行为，落点不在共享层。
