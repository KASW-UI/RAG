---
node-type: orchestration
node-path: scripts/agent-role.py
node-status: DIFFERENT-vllm-cpp
---

# scripts/agent-role.py

> 原始路径：`scripts/agent-role.py`

## 作用

声明角色（`claim operator | claim read-only`）。RAG 单 session 项目只用这两个。

## 关系

引用：
- [[AGENTS]] §"Start here" line 16（claim operator / read-only）

被引用：
- [[AGENTS]] §"Start here" line 16
- [[scripts/agent-start]] 内部调用

## 用法

```bash
python3 scripts/agent-role.py show
python3 scripts/agent-role.py claim operator
```