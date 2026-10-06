---
node-type: orchestration
node-path: scripts/agent-pr-body.py
node-status: byte-equal-vllm-cpp
---

# scripts/agent-pr-body.py

> 原始路径：`scripts/agent-pr-body.py`

## 作用

合并前读 PR body——因为 squash 后 PR body 直接成为 commit message。读一遍防止 trailer 错或链缺失。

## 关系

引用：
- [[AGENTS]] §"Commands" line 663

被引用：
- [[AGENTS]] §"Commands" line 663

## 用法

```bash
python3 scripts/agent-pr-body.py --pr <N>
```

## 状态

✅ RAG 用得上——PR body = commit message 必须检查。