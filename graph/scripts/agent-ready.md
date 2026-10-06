---
node-type: orchestration
node-path: scripts/agent-ready.py
node-status: byte-equal-vllm-cpp
---

# scripts/agent-ready.py

> 原始路径：`scripts/agent-ready.py`

## 作用

`ready-for-helper` 前置检查。检查当前 worktree 状态、是否准备好派 helper。

## 关系

引用：
- [[AGENTS]] §"Commands"（line 661）

被引用：
- [[AGENTS]] §"Start here" line 27

## 状态

⚠️ 字节级一致 vllm.cpp——RAG 单 session 不派 helper，**留着但无用**。下次授权时可考虑删除。