---
node-type: gate
node-path: scripts/check-readme-structure.py
node-status: RAG-化（你前几轮已改）
---

# scripts/check-readme-structure.py

> 原始路径：`scripts/check-readme-structure.py`

## 作用

校验 README 是 landing page——9 个必填 H2 section（What is it / Features / Architecture / Requirements / Build / Configuration / Usage / API / Contributing）、无 em-dash、链 CONTRIBUTING.md。

## 关系

引用：
- [[README]]（README.md）

被引用：
- [[githooks/pre-push]]（CHECKERS 数组）

## 关键改动

✅ 你已 RAG 化：
- `REQUIRED_SECTIONS` 改为 RAG 9 节
- `MAX_PARAGRAPH_CHARS = 900`（沿用 vllm.cpp）
- `MAX_CELL_CHARS = 220`（沿用 vllm.cpp）
- 注释改为 "Why these 9 (not vllm.md's 7)"