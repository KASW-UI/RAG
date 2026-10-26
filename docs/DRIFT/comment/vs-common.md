<!-- @new 2026-10-27 · `comment-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27 -->

# `comment-common` lane 台账 —— 「comment ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/comment/comment-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（10 条 lane 清单 · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `comment-common` lane 独占**（10 路并发 ⇒ 文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane | `comment-common`（第 2/10 条） |
| 分支 / 工作树 | `align/comment-common` / `/Users/pr/Documents/java&golang/RAG.worktrees/comment-common` |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/comment/{comment-api, comment-rpc, comment-common, comment-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/comment/**` ＋ `docs/DRIFT/comment/**`；🔴 **`service/common/**` 只读**（只有 `align/global-common` 能写；⚠️ **旧的 `align/global` 全域 lane 已弃用**） |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `D8` / `D9` | = global 台账 **E65**（出向 gRPC 不继承请求 deadline）/ **E66**（Redis 写不绑 deadline） | `service/common`（`RpcInterceptor` / Redis 拦截器） | 🔴 **OPEN**（两侧同编号 ⇒ 销账别当两条） | [`rpc.md`](rpc.md) |
| `E30` / `E38` / `E46` / `E47` | **只读指针（归属 global）**：超时未继承客户端 deadline / 业务层吞掉「SQL 被 deadline 取消」等 | `service/common` | 🟡 **只读**（按**编号**指，不按行号） | [`rpc.md`](rpc.md) |
| `PIT-GLOBAL-051` | 与「SQL 被 deadline 取消」同族的踩坑记录 | `service/common` | 🟡 只读指针 | [`rpc.md`](rpc.md) |
| `SD-2026-10-24-CAND-08` | 保护层覆盖范围：**未注册路径**上的 MaxBytes（Go 的 chain 只在 `bindRoute` 内拼装 ⇒ 服务端从不返回 413） | — | ✅ **已签字偏离**（2026-10-24 用户 Jason）⇒ **不重复质疑** | [`api.md`](api.md) |
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
> 本域 `go-form-limit.mode` = **envelope**；Go 侧：**已注册**（真源 `service/comment/api/commentservice.go:47` `httpx.SetErrorHandler`）⇒ Go 侧解析失败 = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本 lane 直接修**，不要甩给 global）

- `comment-api` 的 A3 接线复核：`mode=envelope` ⇒ 拒绝形状 = **200 + 业务信封**（文案在**域自己的**错误码表里）
- 本域调用 `service/common` 的**调用方式 / 配置 / 断言 / 装配**问题 ⇒ 域侧自修（**不要甩给 global**）

## 4. 需转派 `global-common` 的（**本 lane 只登记：现象 + 证据 + 建议修法**）

- `D8`/`D9`（E65/E66）⇒ 落点 `service/common` ⇒ **派单 `global-common`**
- `E30`/`E38`/`E46`/`E47` ⇒ global 域内编号 ⇒ 本域**只读指针**，不重复登记

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 `global` 跨域桶**（`docs/DRIFT/global/_README.md` §七之二）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` ⇒ 合并后由整合者统一更新）
