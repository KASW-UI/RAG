---
node-type: orchestration
node-path: scripts/agent-integration.py
node-status: byte-equal-vllm-cpp
---

# scripts/agent-integration.py

> 原始路径：`scripts/agent-integration.py`

## 作用

main 整合前最后一道 gate——`--base upstream/main` 检查 base SHA、状态、merge 顺序。

## 关系

引用：
- [[AGENTS]] §"Commands"（line 664）

被引用：
- [[AGENTS]] §"Commands" line 664

## 状态

⚠️ 字节级一致 vllm.cpp——RAG 项目**不整合多 PR**（单 session 单 PR），**建议删除**或**改 RAG 化为本地用**。