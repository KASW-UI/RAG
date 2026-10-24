#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
phase-B.py —— 按台账把 AGENTS.md 的行区间搬进 docs/，并从 AGENTS.md 中删除。

⚠️ 顺序不可颠倒：**先全部抽取并逐字节校验通过，才允许删除 AGENTS.md 的对应行。**
   任一目标文件校验失败即中止，AGENTS.md 保持原样。

用法：
    python3 tools/drift-move.py extract   # 只抽取+校验，不删 AGENTS.md
    python3 tools/drift-move.py delete    # 校验通过后删除 AGENTS.md 行区间
    python3 tools/drift-move.py verify    # 只校验
"""
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
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


def do_verify(manifest, lines):
    """独立复验：重新按台账比对磁盘上的目标文件。"""
    ok = True
    for dst, entries in targets(manifest).items():
        path = os.path.join(ROOT, dst)
        text = open(path, encoding="utf-8").read()
        for e in entries:
            a, b = e["src"]
            block = "\n".join(lines[a - 1:b])
            if block not in text:
                ok = False
                print(f"  ❌ {dst}: 源区间 L{a}-L{b} 的内容未在目标文件中找到")
    return ok


def do_delete(manifest, lines):
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
    lines = read_agents()
    n = len(manifest["entries"])

    if mode == "extract":
        print(f"【抽取】{n} 段 → docs/DRIFT + docs/Pitfalls\n")
        ok = do_extract(manifest, lines)
        print()
        print("✅ 抽取并校验通过" if ok else "❌ 有校验失败，未删除任何内容")
        return 0 if ok else 1

    if mode == "verify":
        print(f"【校验】{n} 段\n")
        ok = do_verify(manifest, lines)
        print("✅ 全部段的内容都能在目标文件中逐字节找到" if ok else "❌ 有缺失")
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