<!-- @new 2026-10-23 · task 域台账导航 · 由 `align/task` 工作树产出 -->

# task 域台账导航

> **基线 commit**：`8422116` · **工作树**：`RAG.worktrees/task`（分支 `align/task`）
> **首轮核对日期**：2026-10-23 · **Go 真源**：`Sea-RideTheWind_golang`（go-zero `v1.10.2`、goka `v1.1.16`）

| 组件 | 台账 | 首轮结论 | 证据强度 |
|---|---|---|---|
| `task-rpc` | [`rpc.md`](./rpc.md) | 🔴 NOT EQUIVALENT · 已修 3 条 | ✅ 真启动 + 真 PG 16 |
| `task-api` | [`api.md`](./api.md) | 🔴 NOT EQUIVALENT · 已修 2 条 | ✅ 真实 MVC 管线 · ⚠️ gRPC 边界 mock |
| `task-proto` | [`proto.md`](./proto.md) | ✅ 一致（消费方式本轮首次核对） | ✅ 双侧静态 diff + 真构建 |

**task 域没有 `-common`** —— Go 侧 `service/task` 下只有 `api` 与 `rpc`，**不是遗漏**。
⇒ 错误全部是标准 gRPC code（`INVALID_ARGUMENT` / `UNKNOWN`），不需要 like/message 那种业务码 ↔ gRPC code 转换类。

## 本轮改了什么（3 个 commit）

| commit | 内容 |
|---|---|
| `e5948c9` | 救活 `TaskProgressUpsertParityTest` 的 15 条静默 skip（Ryuk 未禁用） |
| `4241690` | `task.definitions` 四个 TTL 键是死配置（relaxed binding 键名对不上） |
| `6dc9b4c` | `TaskCountStore` 跨 group 串号 + 重启归零；`task-api` 端口回 Go 原值 18886；4 处 `Go:` 行号引用校正 |

**最终测试**：`task-rpc` 81 / `task-api` 78，**全部 0 failures / 0 errors / 0 skipped**。

## 读本域台账前必读

1. [`../../AGENTS.md`](../../../AGENTS.md) §3.1（Go 是唯一基准）/ §3.3（框架机制要运行证据）/ §3.5（**rpc 必须先于 api**）
2. 本域 [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md)
   —— 里面有 **4 项「不是偏离但极易被当成回归修回去」**的东西
3. 本域 [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md)
   —— 「跑绿」在本域曾经意味着「15 条真 PG 用例一条没跑」

## 共享索引未更新（按 `docs/Worktree.md` §3 留给合并后统一处理）

- `docs/DRIFT/_INDEX.md`（task 域行）
- `docs/DRIFT/_cross/component-coverage-matrix.md`（14 个零台账 → 12 个）
- `AGENTS.md` §0 / §3.5
- `docs/DRIFT/_MANIFEST.json`
