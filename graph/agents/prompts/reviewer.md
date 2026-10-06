---
node-type: contract
node-path: .agents/prompts/reviewer.md
node-status: DIFFERENT-vllm-cpp（已 RAG 化）frontmatter 已修
---

# .agents/prompts/reviewer.md

> 原始路径：`.agents/prompts/reviewer.md`

## 作用

reviewer 角色合约。`role=reviewer`、method 标签 `REV-STATIC / REV-MUTATION / REV-FULL-GATE / REV-NO-REPAIR(forbidden) / REV-WORKTREE(forbidden) / REV-FINDINGS`。

## method 标签

| method | 含义 |
|---|---|
| `REV-STATIC` | 独立静态审需求 / 计划 / diff / 周边代码 |
| `REV-MUTATION` | 在 scratch copy 里 delete/invert 测试 pin 的行为 |
| `REV-FULL-GATE` | 在未改 commit 上跑一次完整 gate |
| `REV-NO-REPAIR` (forbidden) | 不修 finding |
| `REV-WORKTREE` (forbidden) | 不动 reviewed worktree / index / HEAD / branch |

## 关系

被 [[AGENTS]] §"How work gets done" 引用。
被 [[scripts/check-prompt-contract]] 校验。