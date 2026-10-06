---
node-type: supporting
node-path: scripts/now.py
node-status: byte-equal-vllm-cpp
---

# scripts/now.py

> 原始路径：`scripts/now.py`

## 作用

拼出"哪些 row 是 SPIKE/ACTIVE、它们的 claim 和 PR、下一步"——从 matrices/claims/specs 的 `## Now` 渲染。

## 关系

引用：
- [[AGENTS]] §"Commands" line 514（不在 Commands 节但 §"Start here" line 22 引用）

被引用：
- [[AGENTS]] §"Start here" line 22

## RAG 实际

⚠️ RAG 单 spec 项目——脚本不变但**渲染结果总是空**（没 matrices、没 claims、没 specs）。下次授权建议删除。