---
node-type: ci
node-path: .github/workflows/ci.yml
node-status: byte-equal-vllm-cpp
---

# .github/workflows/ci.yml

> 原始路径：`.github/workflows/ci.yml`

## 作用

主 CI workflow——push / push_lane / workflow_dispatch 三道都跑。

## 关键设计

- push lane 用 LATEST-ONLY 取消（push 间互取消）
- "baseline lane" 每 4h 跑不被取消（避免 push 取消 baseline）
- diff-scoped vs tree-scoped jobs 分开
- concurrency group keyed on `github.ref + github.event_name`

## RAG 实际

⚠️ byte-equal vllm.cpp——**RAG 项目可能不需要 LATEST-ONLY / baseline lane 等复杂并发控制**。下次授权时扫一遍内部引用，不引 vllm.cpp 路径就保留，引就改。