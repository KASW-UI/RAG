---
node-type: orchestration
node-path: scripts/agent-start.py
node-status: DIFFERENT-vllm-cpp（RAG 化过）
---

# scripts/agent-start.py

> 原始路径：`scripts/agent-start.py`

## 作用

session 引导第一命令。`scripts/agent-start.py` 让 agent 看到 welcome、按 `--intent operator|read-only` 进入流程。

## 关系

引用：
- [[AGENTS]] §"Start here"（line 11-46）
- [[.env]]（读取环境变量）
- [[agents/developer-preferences]]（读取开发者偏好）

被引用：
- [[AGENTS]] §"Start here"（line 13）

## 用法

```bash
python3 scripts/agent-start.py                    # 默认引导
python3 scripts/agent-start.py --intent operator # 直接进入 operator 模式
python3 scripts/agent-start.py --intent read-only
```

## 状态

✅ 已 RAG 化（不同 vllm.cpp 字节级一致）。