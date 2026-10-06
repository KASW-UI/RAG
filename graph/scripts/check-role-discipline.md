---
node-type: gate
node-path: scripts/check-role-discipline.py
node-status: DIFFERENT-vllm-cpp
---

# scripts/check-role-discipline.py

> 原始路径：`scripts/check-role-discipline.py`

## 作用

强制 "feature code 必须通过 reviewed `row/*` PR 落地，不允许直接 push main"——vllm.cpp 的"角色纪律"。

## 关系

被引用：
- 由 CI 在 pre-push / push 跑

## RAG 实际

⚠️ RAG 单 session 不需要"operator / sub-agent / 开发者"区分——脚本改 RAG 化但不强制：

- `ROLE_DISCIPLINE_SINCE = None` → REPORT-ONLY
- `WORKTREE_DISCIPLINE_SINCE = None` → REPORT-ONLY
- `FEATURE_PREFIXES = ("service/", "src/main/", "src/test/", ...)`
- `ROW_BRANCH = re.compile(r"(feat|fix|chore)/[a-z0-9_.-]+")`

或直接删，靠 [[AGENTS]] §"Landing work" 自然约束。