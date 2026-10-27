#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""合并内容守门 —— 判「合并树的内容 = 各步合并的并集」，而不只是「零冲突」。

🔴 **为什么需要这条守门（2026-10-28 实测 · RAG 仓 G4 波末收口）**
    `git merge-tree --write-tree` 报【零冲突】**推不出【内容完整】**：
    git 把**含真 NUL 字节**的文件按【二进制】处理 ⇒ ⇒ 三方合并不做文本合并
    ⇒ ⇒ 只可能「取一边」或「逐块揉」⇒ ⇒ **而且【不报冲突】**。
    ⇒ 实测：`service/favorite/favorite-rpc/.../FavoriteDdlParityTest.java`
      ⇒ 三方 blob `9a0609fc` · G3 HEAD `8648028b` · **合并树 `6077498e`（第三版）**
      ⇒ G3「Testcontainers 宿主口钉口」的 **PG 那半边被揉没了**；
      `docs/Pitfalls/favorite/rpc.md` ⇒ G3 的 `PIT-FAV-RPC-040` **整条没进合并树**。

🔴 **本脚本按【合并序列】逐步验证**（而不是拿一个全局 base）
    ⚠️ **为什么必须逐步**：各 lane 之间存在**包含关系**（一方已并入另一方的提交）
       ⇒ ⇒ `git merge-base(ref1 ref2 ...)` 会偏向**包含者** ⇒ ⇒
       「谁改了这个文件」的判据会**整片误报**（实测第一版曾误报 72 条 ⇒ 差点冤枉 G3）。
       ⇒ ⇒ **每一步合并用【那一步自己的】merge-base** ⇒ 才是准确判据。

    每一步（ours=X, theirs=Y, result=R, base=merge-base(X,Y)）对每个文件 F：
      ① `o == t`（两边没差）          ⇒ ⇒ **R 必须 == o**
      ② 只有 X 改（o != b, t == b）   ⇒ ⇒ **R 必须 == o**
      ③ 只有 Y 改（o == b, t != b）   ⇒ ⇒ **R 必须 == t**
      ④ 两边都改                      ⇒ ⇒ 文本文件：合法三方合并 ⇒ **不判**
                                          ⇒ ⇒ **但若 F 含真 NUL ⇒ 🔴「静默揉坏 ⇒ 结果不可信」**
                                          ⇒ ⇒ **只对这类报警 ⇒ 零误报**

用法
----
    python3 tools/difftest/check-merge-tree-content.py <ref1> <ref2> [<ref3> ...]
        # 按给定顺序做 merge-tree（与实际构建合并树的方式一致）⇒ 逐步验证
        # 例：python3 tools/difftest/check-merge-tree-content.py \
        #        align/g1-users-follow align/g2-message-like-task \
        #        align/g3-article-comment-favorite-hot align/g4-service-common

    python3 tools/difftest/check-merge-tree-content.py --expect <merge-commit> <ref1> ...
        # 额外核对：脚本重建出的合并 commit 必须 == --expect 给的那棵
"""

from __future__ import annotations

import os
import subprocess
import sys

NUL = b"\x00"


def git(repo: str, *args: str) -> str:
    out = subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} 失败：{out.stderr.strip()}")
    return out.stdout


def tree_blobs(repo: str, treeish: str) -> dict[str, str]:
    """一次 ls-tree 拿到整棵树的 path -> blob sha（比逐个 cat-file 快几个数量级）。"""
    mapping: dict[str, str] = {}
    for line in git(repo, "ls-tree", "-r", treeish).splitlines():
        meta, path = line.split("\t", 1)
        parts = meta.split()
        if len(parts) >= 3 and parts[1] == "blob":
            mapping[path] = parts[2]
    return mapping


def blob_bytes(repo: str, sha: str) -> bytes:
    return subprocess.run(["git", "-C", repo, "cat-file", "blob", sha],
                          capture_output=True).stdout


def merge_base(repo: str, a: str, b: str) -> str:
    return git(repo, "merge-base", a, b).strip().splitlines()[0].strip()


def merge_tree(repo: str, ours: str, theirs: str) -> tuple[str, str]:
    """返回 (合并树 sha, 合并 commit sha)。冲突时 merge-tree 会输出多行 ⇒ 这里不猜。"""
    env = dict(os.environ)
    env.update({"GIT_AUTHOR_NAME": "merge-check", "GIT_AUTHOR_EMAIL": "m@local",
                "GIT_COMMITTER_NAME": "merge-check", "GIT_COMMITTER_EMAIL": "m@local"})
    raw = subprocess.run(["git", "-C", repo, "merge-tree", "--write-tree", ours, theirs],
                         capture_output=True, text=True, env=env)
    lines = [l for l in raw.stdout.splitlines() if l.strip()]
    if not lines:
        raise SystemExit(f"merge-tree {ours} {theirs} 无输出：{raw.stderr.strip()}")
    tree = lines[0]
    if len(lines) > 1:
        # 有冲突信息 ⇒ 树里会带冲突标记 ⇒ 不能用于验证「并集」
        raise SystemExit(f"merge-tree {ours} {theirs} **有冲突** ⇒ 本守门不适用（先解冲突）")
    commit = subprocess.run(
        ["git", "-C", repo, "commit-tree", tree, "-p", ours, "-p", theirs, "-m", "merge-check"],
        capture_output=True, text=True, env=env).stdout.strip()
    return tree, commit


def check_step(repo: str, ours: str, theirs: str, result_tree: str) -> tuple[list[str], list[str]]:
    """验证一步合并的内容。返回 (问题列表, 不可信列表)。"""
    base = merge_base(repo, ours, theirs)
    bt = tree_blobs(repo, base)
    ot = tree_blobs(repo, ours)
    tt = tree_blobs(repo, theirs)
    rt = tree_blobs(repo, result_tree)

    problems: list[str] = []
    untrusted: list[str] = []

    for path in sorted(set(bt) | set(ot) | set(tt) | set(rt)):
        b = bt.get(path)
        o = ot.get(path)
        t = tt.get(path)
        r = rt.get(path)
        if o == t:
            if r != o:
                problems.append(f"凭空改动 · {path}\n     两边相同（{str(o)[:12]}）⇒ 合并树却是 {str(r)[:12]}")
        elif t == b:
            if r != o:
                problems.append(f"丢失改动 · {path}\n     只有 {ours} 改了（期望 {str(o)[:12]}）⇒ 合并树是 {str(r)[:12]}")
        elif o == b:
            if r != t:
                problems.append(f"丢失改动 · {path}\n     只有 {theirs} 改了（期望 {str(t)[:12]}）⇒ 合并树是 {str(r)[:12]}")
        else:
            # 两边都改 ⇒ 文本文件合法三方合并；含真 NUL ⇒ git 当二进制 ⇒ 静默揉坏
            if r is not None and NUL in blob_bytes(repo, r):
                untrusted.append(
                    f"静默揉坏（不可信）· {path}\n"
                    f"     两边都改（{ours} vs {theirs}）且合并结果含真 NUL ⇒ git 按二进制处理 "
                    f"⇒ 三方合并只取一边/逐块揉 ⇒ 结果不可信\n"
                    f"     修法：把真 NUL 改成可见转义（文档）或等价写法（源码）"
                    f"⇒ 见 tools/difftest/check-no-nul-bytes.py")
    return problems, untrusted


def main() -> int:
    argv = sys.argv[1:]
    expect = None
    if "--expect" in argv:
        i = argv.index("--expect")
        expect = argv[i + 1]
        del argv[i:i + 2]
    refs = [a for a in argv if not a.startswith("--")]
    if len(refs) < 2:
        print(__doc__)
        return 2

    repo = subprocess.run(["git", "rev-parse", "--show-toplevel"],
                           capture_output=True, text=True).stdout.strip()

    print("合并内容守门（逐步验证：每一步的合并结果 = 那一侧的改动没丢）")
    print("=" * 68)
    print(f"  仓库: {repo}")
    print(f"  合并序列: {' → '.join(refs)}")

    ours = refs[0]
    merged_commit = None
    all_problems: list[str] = []
    all_untrusted: list[str] = []
    for theirs in refs[1:]:
        tree, merged_commit = merge_tree(repo, ours, theirs)
        problems, untrusted = check_step(repo, ours, theirs, tree)
        all_problems += [f"[{ours[:12]} ⊕ {theirs[:12]}] {p}" for p in problems]
        all_untrusted += [f"[{ours[:12]} ⊕ {theirs[:12]}] {p}" for p in untrusted]
        print(f"  步: {ours[:12]} ⊕ {theirs[:12]} ⇒ {tree[:12]}   问题 {len(problems)} · 不可信 {len(untrusted)}")
        ours = merged_commit

    print(f"  重建出的合并 commit: {merged_commit}")
    if expect:
        same = subprocess.run(["git", "-C", repo, "diff", "--stat", expect, merged_commit],
                              capture_output=True, text=True).stdout.strip()
        if same:
            print(f"🔴 与 --expect 的那棵【不一致】：\n{same}")
            all_problems.append(f"重建结果与 --expect {expect[:12]} 不一致 ⇒ 两次合并方式不同")
        else:
            print(f"  ✅ 与 --expect {expect[:12]} 一致")
    print("-" * 68)

    for p in all_problems:
        print("🔴 " + p)
    for p in all_untrusted:
        print("🔴 " + p)

    print("-" * 68)
    if all_problems or all_untrusted:
        print(f"❌ FAIL（{len(all_problems)} 条内容不符 + {len(all_untrusted)} 条不可信）")
        print("")
        print("🔴 「merge-tree 零冲突」**推不出**「内容完整」⇒ ⇒ 合并预演必须叠这一条判据。")
        return 1
    print("✅ PASS（每一步的合并结果 = 两边改动都在；无含真 NUL 的多方修改文件）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
