---
node-type: template
node-path: .github/pull_request_template.md
node-status: byte-equal-vllm-cpp
---

# .github/pull_request_template.md

> 原始路径：`.github/pull_request_template.md`

## 作用

PR body 模板——当 GitHub UI 开 PR 时自动套用。**body 直接成为 landed commit message**（仓库设 `squash_merge_commit_message = PR_BODY`）。

## 关键字段

- `## Row`：ROW-ID 字段
- `## Before starting`：issue search / PR shape / 当前状态
- `## What changed`：一段话
- `## Evidence`：preflight + tests named 场景
- `## Speed claims`：性能声明
- `## Honest gaps`：未做的事
- 末尾 `Following-Agents-Protocol / AI-Assisted / Assisted-by` trailer

## 关系

被 [[scripts/agent-pr-body]] 读。
PR body 是 [[githooks/pre-push]] 触发后由 CI 校验。