---
node-type: orchestration
node-path: scripts/agent-issue-index.py
node-status: DIFFERENT-vllm-cpp
---

# scripts/agent-issue-index.py

> 原始路径：`scripts/agent-issue-index.py`

## 作用

把 `gh issue list` 渲染成 untracked snapshot，让记录面 gate 在 GitHub 不可达时也能跑。

## 关系

引用：
- [[AGENTS]] §"Every change starts from an issue" line 94（`scripts/agent-issue-index.py --refresh`）

被引用：
- [[AGENTS]] §"Every change starts from an issue" line 94

## 用法

```bash
python3 scripts/agent-issue-index.py --refresh
```

## 状态

⚠️ RAG 化过但**没用上**——RAG 项目当前 `gh issue list` 不用于此目的。