---
node-type: contract
node-path: .agents/prompts/operator.md
node-status: DIFFERENT-vllm-cpp（已 RAG 化）frontmatter 已修
---

# .agents/prompts/operator.md

> 原始路径：`.agents/prompts/operator.md`

## 作用

operator 角色合约。`role=foo bar`、method 标签 `OP-DELEGATE / OP-CONTINUE / OP-VERIFY / OP-REVIEW / OP-DISPOSITION / OP-REPAIR(forbidden) / OP-EVIDENCE`。

## method 标签

| method | 含义 |
|---|---|
| `OP-DELEGATE` | 委派实现 / 修复给 fresh implementer |
| `OP-CONTINUE` | 对 reviewer FAIL 派 fresh implementer 修 → 派 fresh reviewer 重审，重复到 PASS |
| `OP-VERIFY` | 自跑声称的验证（不信 implementer 报告）|
| `OP-REVIEW` | 派 fresh reviewer 做独立静态审 + scratch mutate |
| `OP-DISPOSITION` | in-session merge verified PR / close obsolete PR |
| `OP-REPAIR` (forbidden) | operator 不在协调 context 修 find |

## 关系

被 [[AGENTS]] §"How work gets done" 引用。
被 [[scripts/check-prompt-contract]] 校验。