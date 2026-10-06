---
node-type: hook
node-path: .githooks/pre-push
node-status: byte-equal-vllm-cpp
---

# .githooks/pre-push

> 原始路径：`.githooks/pre-push`

## 作用

git pre-push hook——`git push` 时自动跑 CHECKERS 数组里的 3 个 checker。

## CHECKERS

```
check-prompt-contract.py
check-now-current.py
check-readme-structure.py
```

每个 checker 必须存在 `scripts/<name>`——否则拒绝 push（**vllm.cpp #1779 防御**）。

## 关键设计

- 在 pushed commit 的 worktree 上跑（不是 dirty checkout）
- 通过 `git worktree add --detach` 临时物化
- 跑完移除
- 任一 checker fail → 拒绝 push

## RAG 启用

```bash
git config core.hooksPath .githooks    # 一次性
git push --no-verify                  # 单次 bypass
```

## 关系

引用：
- [[scripts/check-prompt-contract]]
- [[scripts/check-now-current]]
- [[scripts/check-readme-structure]]

被引用：
- 无显式 — 通过 `git push` 触发