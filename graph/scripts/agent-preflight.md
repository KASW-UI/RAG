---
node-type: orchestration
node-path: scripts/agent-preflight.sh
node-status: DIFFERENT-vllm-cpp
---

# scripts/agent-preflight.sh

> 原始路径：`scripts/agent-preflight.sh`

## 作用

改动前 / commit 前必跑的"所有 gate 集合"。当前 RAG NAMED_CHECKERS 含 4 个：`check-commit-style.py check-commit-trailers.py check-now-current.py`（你已删 `check-agent-record.py`）。

## 关系

引用：
- [[scripts/check-commit-style]]
- [[scripts/check-commit-trailers]]
- [[scripts/check-now-current]]
- [[AGENTS]] §"Start here" line 27 / §"Commands" line 515

被引用：
- [[AGENTS]] §"Start here" line 27
- [[AGENTS]] §"Commands" line 515-516

## 用法

```bash
bash scripts/agent-preflight.sh
bash scripts/agent-preflight.sh --staged
```

## 关键修改（RAG 已做）

✅ 已从 NAMED_CHECKERS 删 `check-agent-record.py`（你前几轮做的）。