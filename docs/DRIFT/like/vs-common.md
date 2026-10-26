<!-- @new 2026-10-27 · `like-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 commit e519073 -->

# `like-common` lane 台账 —— 「like ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/like/like-common`（⚠️ **该域尚无自己的 `common.md` 台账** ⇒ 本 lane 的 common 相关结论就落在本文件））
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（10 条 lane 清单 · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `like-common` lane 独占**（10 路并发 ⇒ 文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane | `like-common`（第 6/10 条） |
| 分支 / 工作树 | `align/like-common` / `/Users/pr/Documents/java&golang/RAG.worktrees/like-common` |
| 基点 | `e519073`（2026-10-27 · 合并 25/25 分支 + 全量回归 BUILD SUCCESS 之后） |
| 本域模块 | `service/like/{like-api, like-rpc, like-common, like-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) |
| **写权** | ✅ 可写**本域** `service/like/**` ＋ `docs/DRIFT/like/**`；🔴 **`service/common/**` 只读**（只有 `align/global-common` 能写） |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| 🔴 `DRIFT-LIKEAPI-015` | **`Content-Type` 大小写**判据（落点 `GoStyleJsonBodyConverter`）—— **6 个域共用** | `service/common` | 🔴 **未修** ⇒ **须单域串行**（一域改、全域受益 ⇒ 别 6 个域并行改同一文件） | [`api.md`](api.md) |
| `DRIFT-LIKEAPI-017` | 单次 RPC 超时：Go **固定 2000ms**（`zrpc/config.go:31`）vs RAG **取 HTTP 剩余预算** | `service/common`（客户端超时口径） | 🔴 **未修** | [`api.md`](api.md) |
| `DRIFT-LIKE-006` | 模块**从未做过 Spring 上下文加载测试**（无 `@SpringBootTest` / `ApplicationContextRunner`） | 域侧测试 | 🔴 部分过期（已新增 `LikeRpcSpringContextWiringTest`）⇒ **本 lane 复核** | [`rpc.md`](rpc.md) |
| `DRIFT-LIKE-013` | 「服务起不来」族（`DRIFT-LIKE-013`~`016`） | 域侧 / 共享层 | 🟡 见台账 | [`rpc.md`](rpc.md) |
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
> 本域 `go-form-limit.mode` = **envelope**；Go 侧：**已注册**（真源 `service/like/api/likecenter.go:48` `httpx.SetErrorHandler`）⇒ Go 侧 = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本 lane 直接修**，不要甩给 global）

- `like-api` 的 A3 接线复核：`mode=envelope`
- 本域**调用**共享层的方式（超时取值 / Content-Type 判据的**调用点**）⇒ 域侧可自修的先域侧修

## 4. 需转派 `global-common` 的（**本 lane 只登记：现象 + 证据 + 建议修法**）

- 🔴 `DRIFT-LIKEAPI-015` ⇒ 落点 `service/common` ⇒ **派单 `global-common`**；⚠️ **6 个域共用 ⇒ 本 lane 先起头、别 6 个域并行**
- `DRIFT-LIKEAPI-017` ⇒ 落点 `service/common` ⇒ 派单

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 `global` 跨域桶**（`docs/DRIFT/global/_README.md` §七之二）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` ⇒ 合并后由整合者统一更新）
