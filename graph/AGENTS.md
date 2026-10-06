---
node-type: root
node-path: AGENTS.md
node-status: RAG-化（拷自 vllm.cpp，仍含占位符）
---

# AGENTS.md

> 原始路径：`AGENTS.md`（841 行）

## 作用

RAG 项目的项目宪法（policy 根）。所有 agent 必读。

## 章节构成

- §"Start here"（line 11）—— session 引导 5 步
- §"Every change starts from an issue"（line 65）—— issue 主原则
- §"Spec before code"（line 113）—— spec 8 section 必填
- §"How we write"（line 151）—— 写作风格入口
- §"How work gets done"（line 177）—— 4 步原则
- §"Gates"（line 306）—— gate 顺序
- §"Shared seams"（line 332）—— 共享缝
- §"Records"（line 392）—— 记录面
- §"Public documents"（line 440）—— 公共文档
- §"Work happens in a worktree"（line 513）—— worktree 纪律
- §"Landing work"（line 537）—— 合并收尾
- §"Commands"（line 655）—— 命令清单

## 关系

被所有节点引用：
- [[scripts/agent-start]] §"Start here" 引用
- [[scripts/agent-preflight]] §"Start here" 引用
- [[scripts/agent-role]] §"Start here" 引用
- [[scripts/agent-ready]] §"Start here" 引用
- [[scripts/agent-pr-body]] §"Start here" 引用
- [[scripts/agent-integration]] §"Start here" 引用
- [[scripts/now]] §"Start here" 引用
- [[scripts/check-agent-record]] §"How we write" 引用

引用以下节点：
- [[agents/NOW]]（§"Start here"）
- [[agents/specs/Spec-Template]]（§"Spec before code"）
- [[agents/developer-preferences]]（§"Start here"）
- [[agents/prompts/operator]]（§"How work gets done"）
- [[agents/prompts/implementer]]（§"How work gets done"）
- [[agents/prompts/reviewer]]（§"How work gets done"）
- [[agents/style/commit]]（§"How we write"）
- [[agents/style/prose]]（§"How we write"）
- [[agents/verification]]（AGENTS.md §"Gates"）
- [[agents/reachability]]（§"Shared seams"）
- [[agents/bugfixing]]（§"How we write"）

## 状态

⚠️ AGENTS.md 拷自 vllm.cpp，**仍含占位符**：
- 第 8 行 `[PROJECT_DESCRIPTION]`、`[PRIMARY_REFERENCE_DESCRIPTION]`
- 第 11-19 行 `[填写：...]`
- 第 275 行 `[PRIMARY_REFERENCE]`
- 第 564-644 行 8 段 `[填写：...]`（Frozen project constraints）

待清理（不属于本次 graph 节点，列入硬阻塞外）。