---
node-type: orchestration
node-path: scripts/agent-onboard.py
node-status: byte-equal-vllm-cpp
---

# scripts/agent-onboard.py

> 原始路径：`scripts/agent-onboard.py`

## 作用

`.env` 和 `.agents/developer-preferences.md` 的"探测 + 记录" 脚本。**只报告 + 写入给定值，不自动填**。

## 关系

引用：
- [[AGENTS]] §"Start here" line 40（`scripts/agent-onboard.py --env-set KEY=VALUE`）
- [[.env]]（写入 .env）

被引用：
- [[AGENTS]] §"Start here" line 40

## 用法

```bash
python3 scripts/agent-onboard.py --probe
python3 scripts/agent-onboard.py --probe --json
python3 scripts/agent-onboard.py --env-set KEY=VALUE
```

## 关键设计

`--env-set` **永不发明任何东西**——KEY 必须在 `.env.example` 里声明过。