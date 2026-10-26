<!-- @new 2026-10-27 · `message-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27 -->

# `message-common` lane 台账 —— 「message ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/message/message-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（10 条 lane 清单 · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `message-common` lane 独占**（10 路并发 ⇒ 文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane | `message-common`（第 7/10 条） |
| 分支 / 工作树 | `align/message-common` / `/Users/pr/Documents/java&golang/RAG.worktrees/message-common` |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/message/{message-api, message-rpc, message-common, message-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/message/**` ＋ `docs/DRIFT/message/**`；🔴 **`service/common/**` 只读**（只有 `align/global-common` 能写；⚠️ **旧的 `align/global` 全域 lane 已弃用**） |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `DRIFT-MSG-V-016` | go-zero 框架级三个 unary 拦截器（recover / breaker / CPU shedding） | `service/common` | ✅ **已全处置**（recover 2026-10-23 ＋ breaker/shedding 2026-10-24） | [`rpc.md`](rpc.md) |
| `DRIFT-MSG-V-025` | 熔断 / 降载的**指标**：Go `core/load/sheddingstat.go:37/42/47` 三计数 vs RAG 原先缺三计数 | `service/common` | ✅ **已修**（第九轮 · `GoStyleSheddingStat.java` 已落码） | [`rpc.md`](rpc.md) |
| `DRIFT-MSG-V-024` | DB / 未知异常：Go 保留 `UNKNOWN` vs RAG 转 `INTERNAL` ⇒ 熔断判定分岔 | `service/common` | ✅ **已修**（第八轮 · rpc 兜底码改回） | [`rpc.md`](rpc.md) |
| 🔴 `DRIFT-MSGAPI-010` | 成功 / 错误响应的 `Content-Type` 缺 `charset`（**影响全部 10 个 api**） | `service/common` | 🔴 **OPEN**（2026-10-24 用户拍板「保持登记，本轮不做」） | [`api.md`](api.md) |
| 🔴 `DRIFT-MSGAPI-011` | 状态行缺 reason phrase | `service/common` | 🔴 **OPEN**（同上） | [`api.md`](api.md) |
| 🔴 `DRIFT-MSGAPI-013` | 缺 `Traceparent` | `service/common` | 🔴 **OPEN**（同上） | [`api.md`](api.md) |
| `DRIFT-MSG-V-029` | `like-rpc` 有同款 Flyway baseline 缺陷（**别的域，本轮不改**） | **别的域** `like-rpc` | 🟡 **留给 like 域自己的 session** | [`rpc.md`](rpc.md) |

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
> 本域 `go-form-limit.mode` = **envelope**；Go 侧：**已注册**（真源 `service/message/api/messagecenter.go:53` `httpx.SetErrorHandler`）⇒ Go 侧 = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本 lane 直接修**，不要甩给 global）

- `message-api` 的 A3 接线复核：`mode=envelope`
- 本域对 `Traceparent` / reason phrase 的**消费方式** ⇒ 域侧可自修的先域侧修

## 4. 需转派 `global-common` 的（**本 lane 只登记：现象 + 证据 + 建议修法**）

- 🔴 `DRIFT-MSGAPI-010` / `-011` / `-013` ⇒ 全部落点 `service/common` ⇒ **三条一起派单 `global-common`**（同批落地，别分三次）
- `DRIFT-MSG-V-029` ⇒ **通知 like 域**（不是本域写权）

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 `global` 跨域桶**（`docs/DRIFT/global/_README.md` §七之二）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` ⇒ 合并后由整合者统一更新）
