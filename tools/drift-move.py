#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
drift-move.py —— 按台账把 AGENTS.md 的行区间搬进 docs/，并从 AGENTS.md 中删除。

⚠️ 顺序不可颠倒：**先全部抽取并逐字节校验通过，才允许删除 AGENTS.md 的对应行。**
   任一目标文件校验失败即中止，AGENTS.md 保持原样。

用法：
    python3 tools/drift-move.py extract   # 只抽取+校验，不删 AGENTS.md
    python3 tools/drift-move.py delete    # 校验通过后删除 AGENTS.md 行区间
    python3 tools/drift-move.py verify    # 只校验
    python3 tools/drift-move.py backfill  # 登记/刷新各段的内容指纹（sha256）

🔴 **verify 的语义在 2026-10-30 改过（此前是假绿）**
   旧实现：用**当前** AGENTS.md 的 `lines[a-1:b]` 去目标文件里找同一段内容
   ⇒ ⇒ 但本表的 `src` 行区间指向**当时的** AGENTS.md 快照（2026-10-23，4093 行）
   ⇒ ⇒ 而 AGENTS.md 后来瘦身到几百行 ⇒ ⇒ **行区间已漂移** ⇒ ⇒
   ① 36 段切片取到**空串** ⇒ ⇒ `"" not in text` 恒为 False ⇒ ⇒ **平凡通过（假绿）**
   ② 4 段切片非空但目标文件里找不到 ⇒ ⇒ **假红**
   ⇒ ⇒ 合起来 = **对 40 段全部无有效检查力**（真校验 0 段）。
   严格源快照**已不可重建**（当时的未提交增量从未入库，现已流失 ⇒ 见 manifest 的
   `_src_anchor.effective_snapshot_reproducible`）⇒ ⇒ 「当初搬走的字节是否与源一致」
   **无法再逐字节复验** ⇒ ⇒ 降级为：
   **① 哨兵存在性**（目标文件里必须有 `<!-- @move AGENTS.md L<a>-L<b> · <label> -->`）
   **② 内容指纹**（哨兵之后的 `b-a+1` 行必须与登记的 sha256 一致）
   ⚠️ ② 只锁「搬移块自登记以来没被改过」，**不**证明它与已流失的源快照逐字节相同
   ⇒ ⇒ ⇒ **必须配变异测试**（改目标文件里的搬移块 ⇒ 守门必红），否则就是再造一个恒绿假绿
   ⇒ ⇒ 变异测试 = `tools/difftest/mutate-drift-move-verify.py`。
"""
import hashlib
import json
import os
import sys


def _resolve_root():
    """仓根：可用 `DRIFT_MOVE_ROOT` 环境变量或 `--root <dir>` 覆盖（变异测试要用影子副本）。"""
    for i, a in enumerate(sys.argv):
        if a == "--root" and i + 1 < len(sys.argv):
            return os.path.abspath(sys.argv[i + 1])
    env = os.environ.get("DRIFT_MOVE_ROOT")
    if env:
        return os.path.abspath(env)
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


ROOT = _resolve_root()
# ⚠️ **git 历史永远取自真仓**（工具自己所在的仓库），**不随 `--root` 漂移**。
#    理由：`--root` / `DRIFT_MOVE_ROOT` 是给**变异测试**用的「影子副本」——
#    影子副本**不是 git 仓**（没有 .git）⇒ ⇒ 若 git 也跟着影子走，
#    历史锚段（`sha256_from = git:<commit>:<path>`）就会**读不出来** ⇒ ⇒ verify 假红。
#    ⇒ ⇒ 内容面取影子、历史面取真仓，两者是**两件不同的事**。
GIT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AGENTS = os.path.join(ROOT, "AGENTS.md")
MANIFEST = os.path.join(ROOT, "docs/DRIFT/_MANIFEST.json")
MARK, SEP = "<!-- @move", "<!-- @sep -->"


def is_marker(line):
    return line.startswith(MARK) or line.startswith(SEP)


def read_agents():
    return open(AGENTS, encoding="utf-8").read().split("\n")


def targets(manifest):
    """按目标文件分组，保持台账顺序。"""
    out = {}
    for e in manifest["entries"]:
        out.setdefault(e["dst"], []).append(e)
    return out


def do_extract(manifest, lines):
    ok = True
    for dst, entries in targets(manifest).items():
        path = os.path.join(ROOT, dst)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        # 已有内容保留（本文件可能已有 @author 段落或历史搬入）
        prior = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
        chunks, blocks, skipped = [], [], 0
        for e in entries:
            a, b = e["src"]
            assert 1 <= a <= b <= len(lines), f"区间越界 {e}"
            block = "\n".join(lines[a - 1:b])
            sentinel = f"{MARK} AGENTS.md L{a}-L{b} · {e['label']} -->"
            # 幂等：该段若已在本文件中，跳过（避免重跑时重复追加）
            if sentinel in prior or any(sentinel in c for c in chunks):
                skipped += 1
                continue
            chunks.append(sentinel)
            chunks.append(block)
            blocks.append(block)
        if not chunks:
            print(f"  ➖ {dst:38} {len(entries):2d} 段  全部已存在，跳过")
            continue
        body = "\n".join(chunks) + "\n"
        head = (prior if prior.endswith("\n") or not prior else prior + "\n")
        open(path, "w", encoding="utf-8").write(head + body)

        # 校验：剥离本文件里所有哨兵后，本次的块必须与源区间逐字节一致
        got = open(path, encoding="utf-8").read().split("\n")
        tail = "\n".join(got[-len(body.split("\n")):])
        rebuilt = "\n".join(l for l in tail.split("\n") if not is_marker(l))
        expected = "\n".join(blocks) + "\n"
        same = rebuilt == expected
        ok &= same
        print(f"  {'✅' if same else '❌'} {dst:38} {len(entries):2d} 段  {len(body.encode()):7,d} B")
        if not same:
            print(f"       期望 {len(expected.encode()):,}B / 实得 {len(rebuilt.encode()):,}B")
    return ok


def sentinel_of(e):
    """该段在目标文件里的哨兵（与 do_extract 写出的完全同形）。"""
    a, b = e["src"]
    return f"{MARK} AGENTS.md L{a}-L{b} · {e['label']} -->"


def fingerprint(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def find_sentinel(lines, label, alias=None):
    """按**标签**找哨兵（**不要求行号一致**）。

    ⇒ ⇒ 为什么不用行号：实测 40 段里有 5 段的哨兵**行号被改过**
    （范围平移，但标签没变）⇒ ⇒ 行号是**不稳定**的身份，标签才是。
    ⇒ ⇒ `alias` = **手写哨兵**的真实措辞（manifest 的 `sentinel_label`）：
       manifest 的 `label` 是**从当时的 AGENTS.md 小节标题自动抽出来的**，
       而目标文件里的哨兵**可能是人手写的** ⇒ ⇒ 措辞对不上（实测 1 段）
       ⇒ ⇒ 用 `sentinel_label` 显式登记那行哨兵的真实文本 ⇒ ⇒ **不靠猜**。
    ⇒ ⇒ 找不到 ⇒ 返回 -1。
    """
    labels = [label] + ([alias] if alias else [])
    for i, l in enumerate(lines):
        s = l.strip()
        if not (s.startswith(MARK) and s.endswith("-->")):
            continue
        if any(f"· {x} -->" in s for x in labels):
            return i
    # ⚠️ 回退：**手写**哨兵可能没有 `· ` 前缀（实测：`AGENTS.md §「…」` 里 `AGENTS.md` 后面是**空格**）
    #    ⇒ ⇒ 别名按「`{alias} -->`」再找一次 ⇒ ⇒ 只对**显式登记的别名**生效，
    #    避免「label 是别的 label 的后缀」造成误匹配。
    if alias:
        for i, l in enumerate(lines):
            s = l.strip()
            if s.startswith(MARK) and s.endswith("-->") and f"{alias} -->" in s:
                return i
    return -1


def seg_block(lines, e):
    """该段在给定文件里的搬移块 ⇒ 返回 (块文本, 取法) 或 (None, None)。

    ⇒ ⇒ **三级回退（按顺序）**：
      ① 哨兵 + **登记行数**（`sha256_lines`）—— 常态（已登记指纹的段）
      ② 哨兵 + **声明行数**（`b-a+1`）—— 尚未登记指纹时
      ③ 哨兵 → **文件末尾** —— **就地编辑口径**：搬移后被改过 ⇒ 登记行数与搬移时对不上
         ⇒ ⇒ 块取不出来 ⇒ ⇒ **接受现状**（登记当前实际内容）
    ⇒ ⇒ ⚠️ **backfill 与 verify 必须走同一个函数** ⇒ ⇒ 否则会出现
       「登记时取到 A 段、校验时取到 B 段」的**静默错位**（指纹永远对不上，
       或者更糟：两边各自取到一段「看起来一样」的内容）。
    ⇒ ⇒ 返回值里的「取法」只用于**打印**，不参与判据。
    """
    label = e["label"]
    alias = e.get("sentinel_label")
    idx = find_sentinel(lines, label, alias)
    if idx < 0:
        return None, None
    a, b = e["src"]
    for n, how in ((e.get("sha256_lines"), "① 登记行数"),
                   (b - a + 1, "② 声明行数")):
        if not n:
            continue
        blk = block_after(lines, idx, n)
        if blk is not None:
            return blk, how
    blk = "\n".join(lines[idx + 1:])
    return blk, "③ 就地编辑（哨兵→文件末尾）"


def block_after(lines, idx, n):
    """哨兵之后的 n 行（= 搬移块本体）。

    ⇒ ⇒ **用行数而不是「到下一个标记为止」** ⇒ ⇒ 块内若含 `<!-- @sep -->` 也不会被截断。
    """
    if n <= 0 or idx < 0 or idx + 1 + n > len(lines):
        return None
    return "\n".join(lines[idx + 1: idx + 1 + n])


def moved_block(dst_lines, e):
    """目标文件里该段的搬移块（**与 backfill 同源** ⇒ 见 `seg_block`）。"""
    blk, _how = seg_block(dst_lines, e)
    return blk


def git_blob(commit, path):
    """读某个 commit 下的文件内容（找不到 ⇒ None）。"""
    import subprocess
    try:
        r = subprocess.run(["git", "-C", GIT_ROOT, "show", f"{commit}:{path}"],
                           capture_output=True, text=True)
    except Exception:
        return None
    if r.returncode != 0:
        return None
    return r.stdout


def find_in_history(e):
    """该段的搬移块在 **git 历史**里还能不能找到（目标文件已被后续轮次重写时用）。

    ⇒ ⇒ 扫该目标文件的历史提交，找**最新的**一个仍带该标签哨兵的版本。
    ⇒ ⇒ 返回 (commit, blob_text) 或 (None, None)。
    """
    import subprocess
    try:
        r = subprocess.run(["git", "-C", GIT_ROOT, "log", "--format=%H", "--", e["dst"]],
                           capture_output=True, text=True)
    except Exception:
        return None, None
    for commit in r.stdout.split():
        blob = git_blob(commit, e["dst"])
        if blob is None:
            continue
        lines = blob.split("\n")
        if find_sentinel(lines, e["label"], e.get("sentinel_label")) >= 0:
            return commit, blob
    return None, None


def do_verify(manifest, lines):
    """独立复验：**哨兵存在性 ＋ 内容指纹**（**不读**当前 AGENTS.md）。

    ⚠️ 为什么不再用「当前 AGENTS.md 的 lines[a-1:b]」⇒ 见文件抬头 §verify 的语义。
    ⇒ ⇒ 两条取证路径：
      · `sha256_from == "current"` ⇒ ⇒ 在**当前**目标文件里按标签定位 ⇒ ⇒ 取 `sha256_lines` 行 ⇒ ⇒ 比对指纹
      · `sha256_from == "git:<commit>:<path>"` ⇒ ⇒ 该段已被后续轮次**重写**⇒ ⇒ 内容校验锚在 **git 历史**里
    """
    ok = True
    total = 0
    checked = 0
    anchored = 0
    for dst, entries in targets(manifest).items():
        path = os.path.join(ROOT, dst)
        total += len(entries)
        dst_lines = []
        if os.path.exists(path):
            dst_lines = open(path, encoding="utf-8").read().split("\n")
        for e in entries:
            a, b = e["src"]
            want = e.get("sha256")
            if not want:
                ok = False
                print(f"  ❌ {dst}: L{a}-L{b} 没有登记内容指纹 ⇒ 先跑 `drift-move.py backfill`")
                continue
            src_from = e.get("sha256_from", "current")
            if src_from == "current":
                if not dst_lines:
                    ok = False
                    print(f"  ❌ {dst}: 目标文件不存在")
                    continue
                blk = moved_block(dst_lines, e)
                if blk is None:
                    ok = False
                    print(f"  ❌ {dst}: 找不到标签哨兵（L{a}-L{b} · {e['label']}）")
                    continue
                got = fingerprint(blk)
                if got != want:
                    ok = False
                    print(f"  ❌ {dst}: L{a}-L{b} 的搬移块内容指纹不符"
                          f"（登记 {want[:12]}… / 实得 {got[:12]}…）")
                    continue
                checked += 1
            else:
                # 内容锚在 git 历史（当前目标文件已被后续轮次重写该段）
                _, _, gpath = src_from.split(":", 2)
                commit = src_from.split(":", 2)[1]
                blob = git_blob(commit, gpath)
                if blob is None:
                    ok = False
                    print(f"  ❌ {dst}: 历史锚点读不出来（{src_from}）")
                    continue
                blines = blob.split("\n")
                blk, _how = seg_block(blines, e)
                if blk is None:
                    ok = False
                    print(f"  ❌ {dst}: 历史锚点里找不到标签哨兵（{src_from}）")
                    continue
                got = fingerprint(blk)
                if got != want:
                    ok = False
                    print(f"  ❌ {dst}: L{a}-L{b} 的历史搬移块指纹不符"
                          f"（登记 {want[:12]}… / 实得 {got[:12]}…）")
                    continue
                anchored += 1
    print(f"   实际校验 {checked}/{total} 段在当前目标文件 ＋ {anchored}/{total} 段锚在 git 历史"
          f"（哨兵 ＋ 内容指纹 sha256）")
    return ok


def do_backfill(manifest):
    """登记/刷新各段的内容指纹（sha256）。

    ⚠️ **这一步只证明「搬移块自此刻起没被改过」**，**不**证明它与已流失的源快照一致
    ⇒ ⇒ 严格源快照不可重建（manifest 的 `_src_anchor.effective_snapshot_reproducible` = False）
    ⇒ ⇒ 所以指纹必须配**变异测试**才有意义（否则就是恒绿假绿）。
    """
    changed = 0
    current = 0
    history = 0
    for dst, entries in targets(manifest).items():
        path = os.path.join(ROOT, dst)
        dst_lines = []
        if os.path.exists(path):
            dst_lines = open(path, encoding="utf-8").read().split("\n")
        for e in entries:
            a, b = e["src"]
            blk = None
            how = ""
            src_from = "current"
            if dst_lines:
                blk, how = seg_block(dst_lines, e)
            if blk is None:
                commit, blob = find_in_history(e)
                if blob is None:
                    print(f"  ❌ {dst}: 当前文件与 git 历史都找不到标签哨兵"
                          f"（L{a}-L{b} · {e['label']}）⇒ 不能登记指纹")
                    return False
                blines = blob.split("\n")
                blk, how = seg_block(blines, e)
                if blk is None:
                    print(f"  ❌ {dst}: 历史里哨兵在但块取不出来（{commit}）")
                    return False
                src_from = f"git:{commit}:{dst}"
                history += 1
            else:
                current += 1
                if how.startswith("③"):
                    print(f"  ⚠️ {dst}: L{a}-L{b}（{e['label'][:30]}）**就地编辑** ⇒ "
                          f"登记行数与搬移时对不上 ⇒ 按**当前实际内容**登记指纹（接受现状）")
            fp = fingerprint(blk)
            if e.get("sha256") != fp or e.get("sha256_from") != src_from:
                e["sha256"] = fp
                e["sha256_lines"] = blk.count("\n") + 1
                e["sha256_from"] = src_from
                changed += 1
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"✅ 指纹登记完成（{changed} 段更新 / 共 {len(manifest['entries'])} 段；"
          f"当前文件 {current} 段 ＋ git 历史 {history} 段）")
    return True


def do_delete(manifest, lines):
    max_src = max(b for _, b in manifest["entries"])
    if max_src > len(lines):
        print(f"❌ 拒绝删除：本表的 `src` 行区间指向**当时的** AGENTS.md 快照（最大行号 {max_src}），")
        print(f"   而当前 AGENTS.md 只有 {len(lines)} 行 ⇒ ⇒ 源快照已失效 ⇒ ⇒ 按行号删会**删错行**")
        print(f"   ⇒ ⇒ 删除模式只在「本表就是当前 AGENTS.md 的行区间表」时可用")
        return False
    iv = sorted(tuple(e["src"]) for e in manifest["entries"])
    ov = [(a, b, c, d) for (a, b), (c, d) in zip(iv, iv[1:]) if c <= b]
    if ov:
        print(f"❌ 区间重叠，拒绝删除：{ov}")
        return False
    drop = set()
    for a, b in iv:
        drop.update(range(a, b + 1))
    kept = [l for i, l in enumerate(lines, 1) if i not in drop]
    before = len("\n".join(lines).encode("utf-8"))
    after = len("\n".join(kept).encode("utf-8"))
    open(AGENTS, "w", encoding="utf-8").write("\n".join(kept))
    print(f"✅ 删除 {len(drop):,} 行（{len(iv)} 段）")
    print(f"   AGENTS.md  {before:,} B → {after:,} B  (减 {before - after:,} B，剩 {after/1024:.1f} KB)")
    return True


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "verify"
    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    n = len(manifest["entries"])

    # ⚠️ **verify / backfill 不读 AGENTS.md** ⇒ ⇒ 影子副本（变异测试用）只需
    #    manifest ＋ 目标文件 ⇒ ⇒ 否则这两条模式会因「影子根里没有 AGENTS.md」而崩。
    lines = None
    if mode in ("extract", "delete"):
        lines = read_agents()

    if mode == "extract":
        print(f"【抽取】{n} 段 → docs/DRIFT + docs/Pitfalls\n")
        ok = do_extract(manifest, lines)
        print()
        print("✅ 抽取并校验通过" if ok else "❌ 有校验失败，未删除任何内容")
        return 0 if ok else 1

    if mode == "verify":
        print(f"【校验】{n} 段（哨兵 ＋ 内容指纹）\n")
        ok = do_verify(manifest, lines)
        print("✅ 全部段：哨兵在位 ＋ 内容指纹一致" if ok else "❌ 有缺失/漂移")
        return 0 if ok else 1

    if mode == "backfill":
        print(f"【登记指纹】{n} 段\n")
        ok = do_backfill(manifest)
        return 0 if ok else 1

    if mode == "delete":
        print(f"【删除前校验】{n} 段\n")
        if not do_verify(manifest, lines):
            print("\n❌ 校验未通过 —— 中止，AGENTS.md 未改动")
            return 1
        print("✅ 校验通过，开始删除\n")
        return 0 if do_delete(manifest, lines) else 1

    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())