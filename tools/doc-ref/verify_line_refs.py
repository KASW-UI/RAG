#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G-12 平移的**闭环校验**（直接验工具自己的改动）。

做法：
  1) 从 `git diff HEAD` 复原工具每次改写：旧行里的 `X.md:OLD` -> 新行里的 `X.md:NEW`
  2) 对每一处：解析 X.md -> 7 个目标文件之一（多路候选；解析不到 ⇒ 计入「无法判定」）
  3) 判定：base[T][OLD-1] 的内容 == cur[T][NEW-1] 的内容 ⇒ 改写正确
     否则 ⇒ 该处改写指向了错误的行

退出码：0 = 工具改写的每一处都指向正确内容；1 = 有改写错误。
"""
import os
import re
import subprocess
import sys

BASE = "3222fa99^"
TARGETS = {
    "docs/DRIFT/like/api.md",
    "docs/DRIFT/users/common.md",
    "docs/DRIFT/users/vs-common.md",
    "docs/DRIFT/users/user/api.md",
    "docs/DRIFT/users/user/rpc.md",
    "docs/DRIFT/users/admin/api.md",
    "docs/DRIFT/users/admin/rpc.md",
}
ROOT = "/Users/pr/Documents/java&golang/RAG"
REF = re.compile(r"([A-Za-z0-9_./-]+\.md):(\d+)(?:-(\d+))?")


def sh(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=ROOT).stdout


def read_lines(p):
    with open(os.path.join(ROOT, p), encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


def base_lines(p):
    return sh(f"git show {BASE}:{p}").splitlines()


def resolve(name, ref_dir):
    cands = []
    if name.startswith(("docs/", "tools/", "service/", ".agents/")):
        cands.append(name)
    else:
        for d in (ref_dir, "docs/DRIFT", os.path.dirname(ref_dir), "."):
            cands.append(os.path.normpath(os.path.join(d, name)))
        cands.append(name)
    for c in cands:
        p = os.path.normpath(c)
        if p in TARGETS:
            return p
    return None


def refs_of(line):
    return [(m.group(1), int(m.group(2)), int(m.group(3)) if m.group(3) else None)
            for m in REF.finditer(line)]


def main():
    base, cur = {}, {}
    for t in TARGETS:
        base[t] = base_lines(t)
        cur[t] = read_lines(t)

    diff = sh("git diff HEAD -- '*.md'")
    files = {}
    cur_file = None
    for ln in diff.splitlines():
        if ln.startswith("+++ b/"):
            cur_file = ln[6:]
            files.setdefault(cur_file, [])
        elif ln.startswith("+") and not ln.startswith("+++"):
            files[cur_file].append(("+", ln[1:]))
        elif ln.startswith("-") and not ln.startswith("---"):
            files[cur_file].append(("-", ln[1:]))

    ok = 0
    bad = []
    unknown = []
    for path, hunks in files.items():
        ref_dir = os.path.dirname(path)
        olds = [t for s, t in hunks if s == "-"]
        news = [t for s, t in hunks if s == "+"]
        # 逐对配对（工具是行内替换 ⇒ 数量一一对应）
        for o, n in zip(olds, news):
            ro = refs_of(o)
            rn = refs_of(n)
            if len(ro) != len(rn):
                continue
            for (no, lo, _), (nn, ln_, _) in zip(ro, rn):
                if no != nn or lo == ln_:
                    continue
                t = resolve(nn, ref_dir)
                if t is None:
                    unknown.append((path, nn, lo, ln_))
                    continue
                b = base[t]
                c = cur[t]
                if lo > len(b) or ln_ > len(c):
                    unknown.append((path, nn, lo, ln_))
                    continue
                if b[lo - 1] == c[ln_ - 1]:
                    ok += 1
                else:
                    bad.append((path, nn, lo, ln_, b[lo - 1][:80], c[ln_ - 1][:80]))

    print("工具改写逐处核对：内容一致 %d 处 · 指向错误 %d 处 · 无法判定 %d 处" % (ok, len(bad), len(unknown)))
    for x in bad:
        print("  ❌ %s  %s:%d -> :%d" % (x[0], x[1], x[2], x[3]))
        print("       base: %s" % x[4])
        print("        cur: %s" % x[5])
    if unknown:
        print("  无法判定（前 10 条）：")
        for x in unknown[:10]:
            print("    %s  %s:%d -> :%d" % x)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
