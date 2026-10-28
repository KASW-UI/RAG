#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
go_repo.py —— **Go 真源仓定位的共享实现**（供 `tools/difftest/*.py` 与 `tools/**/run*.sh` 共用）。

═══════════════════════════════════════════════════════════════════════
【为什么要有这个共享模块（2026-10-29 · G4 lane）】
═══════════════════════════════════════════════════════════════════════
同一个根因**被三个 lane 独立报出**：

  · G1 报作 `S1-FOLLOW-008`②
  · G2 报作「`check-surface-parity.py` 在工作树里假红」
  · G3 报作 `G4-10`，且 **G3 本轮新写的探针初版也踩了同一个坑**

⇒ ⇒ 🔴 **教训**：**逐个脚本修 = 下一个新写的探针还会重犯** ⇒
      ⇒ **修法必须落在「共享解析函数」上**，让新脚本**默认**走对的路。

═══════════════════════════════════════════════════════════════════════
【原始 bug 的形态】
═══════════════════════════════════════════════════════════════════════
旧默认路径 = `<RAG 的同级兄弟目录>/Sea-RideTheWind_golang`
⇒ 在**主工作区**（`.../java&golang/RAG`）里正确
⇒ 在**工作树**（`.../java&golang/RAG.worktrees/<lane>`）里恒指错
   ⇒ 拼出 `.../RAG.worktrees/Sea-RideTheWind_golang` ⇒ **不存在**
⇒ ⇒ 「找不到 Go 仓」被当成「**Go 侧 0 个端点**」⇒ **假红**，
      且 `--domain <错名>` 时**一个域都不跑**却 EXIT=0 ⇒ **假绿**。

═══════════════════════════════════════════════════════════════════════
【本模块的判定口径（三条，shell 版 `tools/difftest/lib/go-repo.sh` 同款）】
═══════════════════════════════════════════════════════════════════════
① **环境变量优先**（`GO_REPO`）⇒ 但**必须校验**它确实像 Go 真源仓
   ⇒ 校验不过 ⇒ **显式报错**（不是「忽略它、继续用默认值」——
      那会让用户以为自己指定生效了）。
② **逐级向上搜索** ⇒ 从「RAG 仓库所在目录」起，最多 8 层
   ⇒ 每层先看 `Sea-RideTheWind_golang`（标准布局），
     再看该层的**其它子目录**（worktree 布局：`RAG.worktrees/<lane>`
     的同级就是主仓 ⇒ 命中的是 `RAG` 的父目录那层）。
③ **找不到 ⇒ 抛 `FileNotFoundError`**（Python）/ **退出码 2**（shell）
   ⇒ ⇒ **绝不允许**退化成「Go 侧 0 个端点」。

⚠️ **判定标志只用「与域无关」的结构性文件**（`go.mod` + `proto/` + `service/`）
   ⇒ ⇒ **不能**用某个域专属文件（如 `service/article/.../routes.go`）当标志
   ⇒ 否则「只跑 article 域的脚本」与「只跑 favorite 域的脚本」判据不同
   ⇒ ⇒ 又变成**同一个仓、两个结论**（这正是 §「原始 bug 的形态」的同族陷阱）。
"""

import os

#: Go 真源仓的目录名（标准布局）。
GO_REPO_DIR_NAME = "Sea-RideTheWind_golang"

#: 🔴 **域无关**的结构性标志：三个都要有 ⇒ 才算 Go 真源仓。
#: （不能用域专属文件当标志 —— 见模块 docstring 的 §「判定口径」末尾。）
GO_REPO_MARKERS = (
    "go.mod",       # Go 模块根
    "proto",        # proto 目录（10 个域的 .proto 都在这）
    "service",      # 10 个微服务的源码根
)

#: 向上搜索的最大层数（worktree 布局需要 2~3 层，留足余量）。
MAX_PARENT_LEVELS = 8


def looks_like_go_repo(path):
    """`path` 是否像 Go 真源仓 ⇒ 三个结构性标志都要在。"""
    if not path:
        return False
    return all(os.path.exists(os.path.join(path, marker)) for marker in GO_REPO_MARKERS)


def _search_from(start_dir):
    """自 `start_dir` 起逐级向上搜索 Go 真源仓 ⇒ 返回路径或 None。"""
    cur = os.path.abspath(start_dir)
    for _ in range(MAX_PARENT_LEVELS):
        # 先看标准布局：<cur>/Sea-RideTheWind_golang
        standard = os.path.join(cur, GO_REPO_DIR_NAME)
        if looks_like_go_repo(standard):
            return standard
        # 再看该层的其它子目录（worktree 布局：RAG 与主仓同级）
        #   🔴 **必须要求目录名精确等于 `Sea-RideTheWind_golang`**
        #   ⇒ 否则会命中「名字里含这串的**别的**副本」（实测：`/tmp/g1_go_probe_v1`）
        #   ⇒ ⇒ 那不是本项目的真源 ⇒ ⇒ 拿它做对照 = **对着另一份代码判一致性** = 假绿。
        try:
            for entry in sorted(os.listdir(cur)):
                if entry != GO_REPO_DIR_NAME:
                    continue
                cand = os.path.join(cur, entry)
                if os.path.isdir(cand) and looks_like_go_repo(cand):
                    return cand
        except OSError:
            pass
        parent = os.path.dirname(cur)
        if parent == cur:
            break
        cur = parent
    return None


def resolve_go_repo(repo_root=None, env=None):
    """定位 Go 真源仓 ⇒ 返回 (路径, 来源说明)。

    🔴 **找不到 ⇒ 抛 `FileNotFoundError`** ⇒ 由调用方显式报错退出。
       **绝不允许**退化成「Go 侧 0 个端点」⇒ 那会把「没查」读成「一致」。

    :param repo_root: RAG 仓库根目录（默认按本文件位置推导）
    :param env: 环境变量字典（默认 `os.environ`）
    :return: (go_repo_path, source_description)
    """
    if repo_root is None:
        # tools/difftest/go_repo.py ⇒ 上溯两级 = <repo>/tools ⇒ 再上一级 = <repo>
        repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if env is None:
        env = os.environ

    raw = env.get("GO_REPO")
    if raw:
        if not looks_like_go_repo(raw):
            raise FileNotFoundError(
                f"GO_REPO 环境变量指向的目录**不是 Go 真源仓**：{raw}\n"
                f"  判据：必须同时含 {', '.join(GO_REPO_MARKERS)}。\n"
                f"  ⇒ 请把它指向 {GO_REPO_DIR_NAME} 的真实路径。")
        return raw, "GO_REPO 环境变量"

    hit = _search_from(os.path.dirname(os.path.abspath(repo_root)))
    if hit:
        return hit, f"向上搜索命中（自 {repo_root} 起）"

    raise FileNotFoundError(
        f"**找不到 Go 真源仓**（{GO_REPO_DIR_NAME}）⇒ 无法做双侧对照。\n"
        f"  搜索起点：{repo_root}\n"
        f"  搜索层数：{MAX_PARENT_LEVELS}\n"
        "  ⇒ 请显式指定：GO_REPO=/绝对路径/"
        f"{GO_REPO_DIR_NAME}\n"
        "  ⚠️ **不得在找不到真源时给出绿灯** —— 那会把「没查」读成「一致」。")
