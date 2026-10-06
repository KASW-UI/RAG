---
node-type: contract
node-path: .agents/prompts/implementer.md
node-status: DIFFERENT-vllm-cpp（已 RAG 化）frontmatter 已修
---

# .agents/prompts/implementer.md

> 原始路径：`.agents/prompts/implementer.md`

## 作用

implementer 角色合约。`role=implementer`、method 标签 `IMP-TEST-FIRST / IMP-VERIFY / IMP-MUTATE / IMP-SCOPE(forbidden) / IMP-EVIDENCE`。

## method 标签

| method | 含义 |
|---|---|
| `IMP-TEST-FIRST` | 先写最小失败测试、跑红 |
| `IMP-VERIFY` | 跑每个声明的验证，exit 0 或 proven baseline |
| `IMP-MUTATE` | delete/invert 测试声称的行为，要求 focused suite fail，再恢复 |
| `IMP-SCOPE` (forbidden) | 不改 Authority 之外的文件 / 行为 |

## 关系

被 [[AGENTS]] §"How work gets done" 引用。
被 [[scripts/check-prompt-contract]] 校验。