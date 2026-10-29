#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G-12 行号平移 —— v5（最终版）。

语义：**基线坐标 → 当前坐标**。G-12 提交 3222fa99 给 7 份台账各加了 5 行「当前态指针」块，
把后面所有行号往下推；仓内其它文档对这些台账的行号引用因此过期。

判定与改写：
  ① 目标解析用 **markdown 约定**（相对引用文件所在目录 → 仓根 → docs/DRIFT → docs/DRIFT/users），
     且必须解析到 G-12 的 7 个目标文件之一才参与平移
     ⇒ 根治旧版「裸 `rpc.md` 被后缀匹配到 users/admin/rpc.md」的误配
  ② 落点优先用 **git diff 的行身份映射**（base 行 → current 行，hunk 头解析）
     兜底用 **全文件内容搜索**（旧版 ±40 行窗口太窄 ⇒ like/api.md 的 +71 一律「找不到」）
  ③ 基线行内容在当前文件**多处命中**（空行 / 重复表头）⇒ **不改写**，登记待人工
  ④ **始终以 HEAD 快照为计算输入** ⇒ 工具可重复执行（第二次跑得到同一批改写，不会二次平移）

用法：python3 fix_g12_v5.py [--apply]
"""
import io
import os
import re
import subprocess
import sys

ROOT = "/Users/pr/Documents/java&golang/RAG"
BASE = "3222fa99^"
SNAP = "HEAD"
G12_TARGETS = [
    "docs/DRIFT/like/api.md",
    "docs/DRIFT/users/common.md",
    "docs/DRIFT/users/vs-common.md",
    "docs/DRIFT/users/user/api.md",
    "docs/DRIFT/users/user/rpc.md",
    "docs/DRIFT/users/admin/api.md",
    "docs/DRIFT/users/admin/rpc.md",
]
REF = re.compile(r"([A-Za-z0-9_./-]+\.md):(\d+)(?:-(\d+))?")


def sh(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=ROOT).stdout


def git_lines(rev, path):
    return sh(f"git show {rev}:{path}").splitlines()


def work_lines(path):
    with open(os.path.join(ROOT, path), encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


def line_identity(base_path, cur_path):
    """base 行 -> current 行 的行身份映射（git diff hunk 头解析）。"""
    d = sh(f"git diff --unified=0 {BASE} -- {cur_path}")
    fwd = {}
    for ln in d.splitlines():
        if not ln.startswith("@@"):
            continue
        m = re.match(r"@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@", ln)
        if not m:
            continue
        a = int(m.group(1)); b = int(m.group(2) or 1)
        c = int(m.group(3)); e = int(m.group(4) or 1)
        for k in range(min(b, e)):
            fwd[a + k] = c + k
    return fwd


def resolve(name, ref_dir):
    if name.startswith(("docs/", "tools/", "service/", ".agents/")):
        cands = [os.path.normpath(name)]
    else:
        cands = []
        for d in (ref_dir, "docs/DRIFT/users", "docs/DRIFT", ".", os.path.dirname(ref_dir)):
            cands.append(os.path.normpath(os.path.join(d, name)))
        cands.append(os.path.normpath(name))
    ok = []
    for c in cands:
        if c.endswith(".md") and os.path.isfile(os.path.join(ROOT, c)) and c not in ok:
            ok.append(c)
    return ok[0] if ok else None


def main():
    base = {t: git_lines(BASE, t) for t in G12_TARGETS}
    # 目标文件内容也取 **HEAD 快照** ⇒ 工具完全确定、可重复（读工作树会因上次改写而漂）
    cur = {t: git_lines(SNAP, t) for t in G12_TARGETS}
    fwd = {t: line_identity(t, t) for t in G12_TARGETS}

    md_files = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in (".git", "target", "node_modules")]
        for fn in filenames:
            if fn.endswith(".md"):
                md_files.append(os.path.join(dirpath, fn))

    changed, report, manual = {}, [], []
    for fp in sorted(md_files):
        rel = os.path.relpath(fp, ROOT)
        ref_dir = os.path.dirname(rel)
        snap = git_lines(SNAP, rel)          # 计算输入 = HEAD 快照
        work = work_lines(rel)               # 落盘对象 = 工作树
        if len(snap) != len(work):
            continue
        touched = {}
        for lineno, line in enumerate(snap, 1):
            for mm in REF.finditer(line):
                rawpath, n1 = mm.group(1), int(mm.group(2))
                n2 = int(mm.group(3)) if mm.group(3) else None
                T = resolve(rawpath, ref_dir)
                if T is None or T not in G12_TARGETS:
                    continue
                if n1 > len(base[T]):
                    manual.append((rel, lineno, mm.group(0), T, "基线行号越界"))
                    continue
                span = (n2 - n1) if n2 else 0
                want = base[T][n1 - 1]
                # ②a 行身份映射优先
                p = fwd[T].get(n1)
                if not (p and p <= len(cur[T]) and cur[T][p - 1] == want):
                    p = None
                if p is None:
                    # ②b 全文件内容搜索兜底
                    hits = [i + 1 for i, l in enumerate(cur[T]) if l == want]
                    if span:
                        want_end = base[T][n2 - 1] if n2 and n2 <= len(base[T]) else None
                        if want_end is not None:
                            hits = [h for h in hits
                                    if h - 1 + span < len(cur[T]) and cur[T][h - 1 + span] == want_end]
                    if not hits:
                        manual.append((rel, lineno, mm.group(0), T, "基线内容在当前文件已不存在"))
                        continue
                    if len(hits) > 1:
                        manual.append((rel, lineno, mm.group(0), T,
                                       "内容多处命中 %s ⇒ 不改写" % hits[:3]))
                        continue
                    p = hits[0]
                if p == n1:
                    continue
                new_ref = "%s:%d" % (rawpath, p) + ("-%d" % (p + span) if span else "")
                touched[(lineno, mm.group(0))] = new_ref
                report.append((rel, lineno, mm.group(0), new_ref, T))
        if touched:
            changed[rel] = (work, touched)

    print("=== 待改写 %d 处，涉及 %d 个文件 ===" % (len(report), len(changed)))
    for r in report:
        print("  %-52s L%-6d %-40s -> %s" % (r[0], r[1], r[2], r[3]))
    if manual:
        print("\n=== ⚠️ 需人工判定 %d 处（不改写）===" % len(manual))
        for m in manual:
            print("  %-52s L%-6d %-38s [%s] %s" % (m[0], m[1], m[2], m[3], m[4]))

    if "--apply" not in sys.argv:
        print("\n(仅预览；加 --apply 才落盘)")
        return 1 if manual else 0
    for rel, (lines, touched) in changed.items():
        per_line = {}
        for (lineno, oldref), newref in touched.items():
            per_line.setdefault(lineno, []).append((oldref, newref))
        for lineno, pairs in per_line.items():
            s = lines[lineno - 1]
            for oldref, newref in pairs:
                s = s.replace(oldref, newref)
            lines[lineno - 1] = s
        io.open(os.path.join(ROOT, rel), "w", encoding="utf-8").write("\n".join(lines))
        print("已改写 %s（%d 处）" % (rel, len(touched)))
    return 1 if manual else 0


if __name__ == "__main__":
    sys.exit(main())
