<!-- @new 2026-10-27 · `favorite-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G3 波次工作树（`align/g3-article-comment-favorite-hot`）** -->

# `favorite-common` lane 台账 —— 「favorite ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/favorite/favorite-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G3` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `favorite-common`（**原第 3/10 条**） |
| **现分支 / 工作树** | **`align/g3-article-comment-favorite-hot`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot`<br>⚠️ 原 `align/favorite-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/favorite/{favorite-api, favorite-rpc, favorite-common, favorite-proto}` |
| 域侧台账 | [`rpc.md`](rpc.md) · [`api.md`](api.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/favorite/**` ＋ `docs/DRIFT/favorite/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `E21` | `grpc-inprocess` 是 `test` scope ⇒ 运行时 classpath 上整个 artifact 消失 | `service/common`（pom 依赖作用域） | ✅ **已修**（global L12/L13 · 随 `4f839f9` 合入主干 ⇒ 真缺 = 0） | [`rpc.md`](rpc.md) |
| `E62` | 共享层 `DeadlineQueryTimeoutInjector` 的**毫秒精度** + 「deadline 已过 ⇒ 不执行 SQL」两处行为变化 | `service/common` | ✅ **已修并合回主干**；✅ **favorite 域零条依赖旧行为** | [`rpc.md`](rpc.md) |
| `E65` | 出向 gRPC 调用**不继承请求 deadline**（`RpcInterceptor` 只绑 `DeadlineState`、不绑 `GoStyleRequestDeadline` ⇒ `applyTo` 是 no-op） | `service/common` | 🔴 **OPEN** | [`rpc.md`](rpc.md) |
| `E68` | 「行号合法但指错文件」的错锚点判据（共享复核器第 4+ 层补判据） | 共享工具 | ✅ **已落**（`97a1fa8` / `0107845`）⇒ favorite 域**抓出 1 条**，已订正 | [`rpc.md`](rpc.md) |
| （域侧 · 非编号） | `favorite-rpc` **缺 `baseline-version: 0`** ⇒ 同 `article-rpc` 的基线缺陷（起得来、首次查库才炸） | **域侧** `service/favorite/favorite-rpc/src/main/resources/application.yml` | 🔴 **未修**（AGENTS.md §3.5 已点名 favorite 是例外） | — |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A2** | gRPC 拦截器链 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A3** | HTTP 保护链 | 本域 `application.yml` 的 `rag.http.go-form-limit.mode` = **off（缺省）** ＋ 共享层 6 个类 | ⬜ **待 lane 复核**（共享层已验 ⇒ 缺「本域真实请求」证据） | — | — |
| **A4** | 错误码 / envelope | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A5** | 端口治理 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A6** | errmsg 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A7** | proto 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A8** | DDL 时间列 / 类型 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A9** | trace / otel | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> 本域 `go-form-limit.mode` = **off（缺省）**；Go 侧：**未注册**（真源 `service/favorite/api/` 无 `httpx.SetErrorHandler`）⇒ Go 侧 = **400 + Go 原文**；⚠️ **RAG 侧 X-6 也刻意置 `off`** ⇒ **两侧都不过 form 限 ⇒ 零触碰** ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

- `favorite-rpc` 的 `baseline-version: 0`（**域侧 yaml ⇒ 本 lane 自修**）
- `favorite-api` 的 A3 复核：⚠️ **X-6 对 favorite 刻意 `off`** ⇒ 本域**不引入** form 限；本 lane 只需确认「共享层的 `off` 分支在本域确实不生效」

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

- `E65` ⇒ 落点 `service/common` ⇒ **派单 **G4****（favorite 是**发现方**，但修法不在本域）

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）
