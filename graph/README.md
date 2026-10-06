---
node-type: index
graph-root: /Users/pr/Documents/java&golang/RAG/graph
---

# RAG 工作流文件节点关系图

> 此目录是 Obsidian vault 根目录。在 Obsidian 中打开此目录即可看到 graph view 自动渲染的所有 `[[link]]` 关系。

## 节点构成

按 RAG 项目工作流的"角色层"分组：

| 角色 | 节点数 | vault 路径 |
|---|---|---|
| 项目宪法 | 1 | `AGENTS.md` |
| orchestration scripts | 8 | `scripts/` |
| gate scripts | 8 | `scripts/` |
| supporting scripts | 4 | `scripts/` |
| prompt 合约 | 3 | `agents/prompts/` |
| spec / project-context / prose 指南 | 4 | `agents/specs/`, `agents/project-context/`, `agents/style/` |
| agent 状态与配置 | 8 | `agents/` |
| GitHub CI / template | 7 | `github/` |
| worktree hooks | 2 | `githooks/` |
| **总计** | **45 节点** | |

## 节点属性（YAML frontmatter）

每个节点的 frontmatter 包含：

- `node-type`: orchestration / gate / contract / state / guide / ci / template / hook / root / index
- `node-path`: 原始相对 RAG 根的路径
- `node-status`: active / byte-equal-vllm-cpp / RAG-化 / 缺文件

## 边的种类（Obsidian `[[link]]`）

- **引用**（←）：当前节点引用的目标节点
- **被引用**（→）：引用当前节点的目标节点
- **运行时调用**（→）：当前节点运行时调用目标节点
- **被调用**（←）：调用当前节点的是目标节点

## 用法

1. 在 Obsidian 中打开 `/Users/pr/Documents/java&golang/RAG/graph/` 作为 vault
2. 打开任意笔记，看 `[[link]]` 互相链
4. 切到 Graph view 看整体关系