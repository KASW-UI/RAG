---
node-type: state
node-path: .agents/NOW.md
node-status: RAG-化（你前几轮已改）
---

# .agents/NOW.md

> 原始路径：`.agents/NOW.md`

## 作用

RAG 项目的"single-Read resume"——当前 spec / current gate / next actions / protocol invariants。snapshot 不走 log。

## 4 节必填

- `## Current work`（Spec + State）
- `## Current gate`（preflight + push CI 状态）
- `## Next actions`（编号 0./1./2.）
- `## Protocol invariants`

## 状态

✅ 你已 RAG 化（4 节 / 50 行 / 200 chars）。

## 关系

被 [[scripts/check-now-current]] 校验。
被 [[githooks/pre-push]] CHECKERS 数组引用。
被 [[scripts/agent-start]] §"Start here" line 22 引用。