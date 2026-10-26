<!-- @new 2026-10-27 · `article-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 commit e519073 -->

# `article-common` lane 台账 —— 「article ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/article/article-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（10 条 lane 清单 · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `article-common` lane 独占**（10 路并发 ⇒ 文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane | `article-common`（第 1/10 条） |
| 分支 / 工作树 | `align/article-common` / `/Users/pr/Documents/java&golang/RAG.worktrees/article-common` |
| 基点 | `e519073`（2026-10-27 · 合并 25/25 分支 + 全量回归 BUILD SUCCESS 之后） |
| 本域模块 | `service/article/{article-api, article-rpc, article-common, article-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/article/**` ＋ `docs/DRIFT/article/**`；🔴 **`service/common/**` 只读**（只有 `align/global-common` 能写） |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `DRIFT-HOTAPI-030` | Tomcat 数「**总参数个数**」vs Go 数「**非空值个数**」⇒ 2049~10000 段且非空值 ≤2048 时 **Go 200 / RAG 5001** | `service/common`（X-6 共享层） | ✅ **已落地**（global 第十四轮 · 6 个共享类 + 每域一行 yaml） | [`api.md`](api.md) |
| `DRIFT-ARTICLE-API-006` | Go 必填字段校验：`core/mapping` **只认 `,optional`**，`,omitempty` **不算** optional ⇒ Go 缺字段即 400 | `service/common` 解析层 | 🔴 **未修** | [`api.md`](api.md) |
| `S-6` | `SqlTimeoutStatusTranslator` 产出的 `DEADLINE_EXCEEDED` 被 `invoke` 的 `catch(RuntimeException)` 降级为 `UNKNOWN` ⇒ 业务码 5001 → 1015 | `service/common` | ✅ **已改**（纯 Mockito 证据） | [`rpc.md`](rpc.md) |
| （域侧 · 非编号） | `article-rpc` **缺 `baseline-version: 0`** ⇒ `baseline-on-migrate=true` + 非空 schema ⇒ `V1` 被整条跳过（起得来、首次查库才炸） | **域侧** `service/article/article-rpc/src/main/resources/application.yml` | 🔴 **未修** | — |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A2** | gRPC 拦截器链 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A3** | HTTP 保护链 | 本域 `application.yml` 的 `rag.http.go-form-limit.mode` = **raw** ＋ 共享层 6 个类 | ⬜ **待 lane 复核**（共享层已验 ⇒ 缺「本域真实请求」证据） | — | — |
| **A4** | 错误码 / envelope | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A5** | 端口治理 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A6** | errmsg 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A7** | proto 契约 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A8** | DDL 时间列 / 类型 | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |
| **A9** | trace / otel | ⬜ 待填 | ⬜ **待 lane 复核** | — | — |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> 本域 `go-form-limit.mode` = **raw**；Go 侧：**未注册**（真源 `service/article/api/` 无 `httpx.SetErrorHandler`）⇒ Go 侧解析失败 = **400 + Go 原文** ⇒ 与 RAG `mode=raw` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本 lane 直接修**，不要甩给 global）

- `article-rpc` 的 `baseline-version: 0`（**域侧 yaml ⇒ 本 lane 自修**；写法照抄 `message-rpc` / `like-rpc` 的既有注释块）
- `article-api` 的 A3 接线复核：`rag.http.go-form-limit.mode: raw` ⇒ 拒绝形状 = **400 + Go 原文**

## 4. 需转派 `global-common` 的（**本 lane 只登记：现象 + 证据 + 建议修法**）

- `DRIFT-ARTICLE-API-006`（Go 必填字段校验）⇒ 落点 `service/common` 解析层 ⇒ **派单 `global-common`**
- `S-6` 的 `SqlTimeoutStatusTranslator` javadoc 归因问题 ⇒ 同落点 ⇒ 随 `global-common` 一并订正

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 `global` 跨域桶**（`docs/DRIFT/global/_README.md` §七之二）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` ⇒ 合并后由整合者统一更新）
