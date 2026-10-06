---
node-type: gate
node-path: scripts/check-now-current.py
node-status: RAG-化（你前几轮已改）
---

# scripts/check-now-current.py

> 原始路径：`scripts/check-now-current.py`

## 作用

校验 `.agents/NOW.md` 是"snapshot, not log"——stamp 必填、4 节必填（Current work / Current gate / Next actions / Protocol invariants）、单 entry ≤ 200 chars、≤ 50 行。

## 关系

引用：
- [[agents/NOW]]
- [[githooks/pre-push]]（pre-push 跑它）

被引用：
- [[githooks/pre-push]]（CHECKERS 数组）
- [[scripts/agent-preflight]]（NAMED_CHECKERS）

## 关键改动

✅ 你已 RAG 化：
- `MAX_LINES = 50`（从 100）
- `MAX_ENTRY_CHARS = 200`（从 400）
- `REQUIRED_HEADINGS` 改为 4 节
- `MAX_CHARS` 不引入（保留 vllm.cpp 演化判断）
- 顶部 "Adaptations vs upstream" 注释