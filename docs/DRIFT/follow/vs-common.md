<!-- @new 2026-10-27 · `follow-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G1 波次工作树（`align/g1-users-follow`）** -->

# `follow-common` lane 台账 —— 「follow ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/follow/follow-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G1` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `follow-common`（**原第 4/10 条**） |
| **现分支 / 工作树** | **`align/g1-users-follow`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow`<br>⚠️ 原 `align/follow-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/follow/{follow-api, follow-rpc, follow-common, follow-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/follow/**` ＋ `docs/DRIFT/follow/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| 🔴 `DRIFT-FOLLOW-008` | **MaxConns 闸门**：Go **每路由一个**（`rest/handler/maxconnshandler.go:12` 按 `route.Path` 建闸）vs RAG **全局一个** | `service/common`（`GoStyleMaxConnsFilter`） | 🔴 **未修**（域台账原文即写「派单」）⇒ **本 lane 的头号事项** | [`api.md`](api.md) |
| `DRIFT-FOLLOW-002` | 运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败 | `service/common`（pom 作用域） | ✅ **已修** | [`rpc.md`](rpc.md) |
| `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（X-6） | ✅ **已落地** | [`api.md`](api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A2** | gRPC 拦截器链 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A3** | HTTP 保护链 | 本域 `application.yml` 的 `rag.http.go-form-limit.mode` = **envelope** ＋ 共享层 6 个类 | ⬜ **待 lane 复核**（共享层已验 ⇒ 缺「本域真实请求」证据） | — | — |
| **A4** | 错误码 / envelope | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A5** | 端口治理 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A6** | errmsg 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A7** | proto 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A8** | DDL 时间列 / 类型 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A9** | trace / otel | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> 本域 `go-form-limit.mode` = **envelope**；Go 侧：**已注册**（真源 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler`）⇒ Go 侧 = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

- 本域**调用** `GoStyleMaxConnsFilter` 的**粒度参数**（若 RAG 侧只是传参口径问题 ⇒ 域侧可自修；**只有共享层本身实现错**才派单）
- `follow-api` 的 A3 接线复核：`mode=envelope`

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

- 🔴 `DRIFT-FOLLOW-008` ⇒ 落点 `service/common` ⇒ **派单 **G4****；⚠️ **与 users 域 `DRIFT-USERAPI-013` 同族** ⇒ 两条**一起**对账，别各修各的

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）
