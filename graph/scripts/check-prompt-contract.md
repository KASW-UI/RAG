---
node-type: gate
node-path: scripts/check-prompt-contract.py
node-status: byte-equal-vllm-cpp（机制通用）
---

# scripts/check-prompt-contract.py

> 原始路径：`scripts/check-prompt-contract.py`

## 作用

校验三个 prompts 合约文件的 frontmatter（`prompt-contract-version`、`role`、`## Task envelope`、method 标签、`## Required output`、`## Stop conditions`）。**版本化、版本号必填、method 标签 required/forbidden 必填**。

## 关系

引用：
- [[agents/prompts/operator]]
- [[agents/prompts/implementer]]
- [[agents/prompts/reviewer]]

被引用：
- [[githooks/pre-push]]（CHECKERS 数组）

## 用法

```bash
python3 scripts/check-prompt-contract.py
```

## 关键设计

合约通过 `prompt-contract-version` 锁版本——任何 prompt 文件改动必须 bump 版本。