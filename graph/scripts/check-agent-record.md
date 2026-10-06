---
node-type: gate
node-path: scripts/check-agent-record.py
node-status: byte-equal-vllm-cpp（建议删除）
---

# scripts/check-agent-record.py

> 原始路径：`scripts/check-agent-record.py`

## 作用

vllm.cpp "mirror vLLM parity × 几百行 × 多维度" 工作流的"程序化钉死"——校验 10 个 canonical record 必须存在，每个矩阵行数精确，每行 5 个 anchor。

## 关系

被引用：
- ~~原本被 [[scripts/agent-preflight]] 引用（你已删）~~

## RAG 实际

⚠️ **RAG 单 session 业务项目不适用**——RAG 没有 30+ model arch × 80+ quant、没 mirror 上游、没 ROW-ID 抽象。

✅ 你已从 NAMED_CHECKERS 删。但**脚本文件还在**——下次授权建议直接 `rm scripts/check-agent-record.py`。

## RAG 化替代（不写）

如果你将来需要 RAG 自己的"功能状态一致性检查"，另写 `scripts/check-rag-record.py`（按 docs/modules.md + specs/ 形态）——**不是改这个**。