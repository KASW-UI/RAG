---
node-type: supporting
node-path: scripts/ready-for-helper.py
node-status: byte-equal-vllm-cpp
---

# scripts/ready-for-helper.py

> 原始路径：`scripts/ready-for-helper.py`

## 作用

检查当前 worktree 状态是否可派 helper session——vllm.cpp "派 helper 子代理"前置检查。

## RAG 实际

⚠️ RAG 单 session 不派 helper——脚本不变但无用。建议删除。