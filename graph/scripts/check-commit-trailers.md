---
node-type: gate
node-path: scripts/check-commit-trailers.py
node-status: DIFFERENT-vllm-cpp
---

# scripts/check-commit-trailers.py

> 原始路径：`scripts/check-commit-trailers.py`

## 作用

校验 commit message / landed message body 必须含 trailer 块（Following-Agents-Protocol / AI-Assisted / Assisted-by）。**禁止** AI `Co-Authored-By` 或 `Signed-off-by`。

## 关系

引用：
- 无

被引用：
- [[scripts/agent-preflight]]（NAMED_CHECKERS）

## 用法

```bash
python3 scripts/check-commit-trailers.py --range HEAD~5..HEAD
python3 scripts/check-commit-trailers.py --message-file <file>
```