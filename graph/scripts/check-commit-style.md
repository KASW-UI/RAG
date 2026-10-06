---
node-type: gate
node-path: scripts/check-commit-style.py
node-status: DIFFERENT-vllm-cpp
---

# scripts/check-commit-style.py

> 原始路径：`scripts/check-commit-style.py`

## 作用

校验 commit subject / body 风格（subject 不超过 N chars、有 body 等）。CI 跑时传 commit range。

## 关系

被引用：
- [[scripts/agent-preflight]]（NAMED_CHECKERS）

## 用法

```bash
python3 scripts/check-commit-style.py --range HEAD~5..HEAD
```