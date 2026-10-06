---
node-type: gate
node-path: scripts/check-conflict-markers.py
node-status: byte-equal-vllm-cpp
---

# scripts/check-conflict-markers.py

> 原始路径：`scripts/check-conflict-markers.py`

## 作用

扫所有 tracked text 检查 merge conflict markers（`<<<<<<<` / `=======` / `>>>>>>>`）。**机制通用**。

## 关系

被引用：
- [[AGENTS]] 是 implicit（CI 一般会跑）

## 用法

```bash
python3 scripts/check-conflict-markers.py
```