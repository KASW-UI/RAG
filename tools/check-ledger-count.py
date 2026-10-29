#!/usr/bin/env python3
"""守门：`docs/Pitfalls/_INDEX.md` 的**条目计数**必须与仓库现状一致（机器可复算）。

背景
----
`docs/Pitfalls/_INDEX.md` 的头部有一行「**全域坑位条目** | **N 条** / **M 份有内容的台账**（另 **K 份 0 字节**…）」。

⚠️ **它原先是手工快照** ⇒ 没有任何脚本能复算它 ⇒ 2026-10-27 实测发现：
  索引自称 **327 条 / 33 份**，而按任何自然口径都复现不出这个数
  （试过 8 种正则口径：314/23、423/31、250/18、185/14、15/1、114/9、1320/35 …）
  ⇒ **数字已经漂移**，而且**没人说得清它是怎么数出来的**
  ⇒ 这跟「守门登记了已不存在的对象」是同一类病：**不可复算的断言 = 假保证**。

⇒ 本工具把口径**写死成代码**（下面的 `TOKEN_RE` + `HEADING_RE`），
  并把 `docs/Pitfalls/_INDEX.md` 的那一行变成**由本工具生成**的产物
  ⇒ 以后任何人加一条坑位 ⇒ 跑 `--fix` ⇒ 数字自动跟上。

⚠️ **口径的边界（别误读数字）**：
- **条目** = 二~四级标题，标题文本（去掉 `` ` `` 与 `*`）以**编号 token** 开头
  （`PIT-…` / `PF-…` / `P-…`，且以 `-<数字>` 结尾）。
- **编号式台账** = 至少含一条上述标题的文件。
- **散文式台账** = 有内容但**没有**编号式标题的文件 ⇒ **不计入条数**（它的条目是散文，不是编号）。
- **0 字节坑位** = 空文件（占位待填）。

退出码
------
0 = 索引与仓库一致；1 = 漂移（逐项点名差多少）。

复现
----
python3 tools/check-ledger-count.py           # 守门
python3 tools/check-ledger-count.py --print   # 只打印实测值
python3 tools/check-ledger-count.py --fix     # 把索引那一行改成实测值
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
PITFALLS = REPO / "docs/Pitfalls"
INDEX = PITFALLS / "_INDEX.md"

# 二~四级标题
HEADING_RE = re.compile(r"^#{2,4}\s+(.+)$", re.M)
# 编号 token：PIT-…/PF-…/P-… 且以 -<数字> 结尾（允许 `PIT-LANE-05` / `P-ARTICLE-003`）
# ⚠️ **2026-10-29 订正**：本注释原写「允许 `PITFALL-TASK-01`」⇒ **假的** —— 该形态 `PIT` 后**不带 `-`**，
#    ⇒ 正则的 `(?:-[A-Za-z0-9]+)*` 匹配零段 ⇒ 整个 token 失配 ⇒ **这类标题对本工具是隐形的**。
#    ⇒ 编号**必须**用 `PIT-…` / `PF-…` / `P-…` 三种前缀之一（本轮已把仓内 28 条 `PITFALL-MSG-R-*` 归一为 `PIT-MESSAGE-*`）。
TOKEN_RE = re.compile(r"^(?:PIT|PF|P)(?:-[A-Za-z0-9]+)*-\d+\b")
# 索引里那一行的三处数字
IDX_ENTRIES_RE = re.compile(r"\*\*(\d+) 条\*\*")
IDX_FILES_RE = re.compile(r"\*\*(\d+) 份(?:有内容的|编号式)台账\*\*")
IDX_EMPTY_RE = re.compile(r"\*\*(\d+) 份 0 字节\*\*")


def _clean(h: str) -> str:
    return re.sub(r"[`*]", "", h).strip()


PREFIX_RE = re.compile(r"^(?:PIT|PF|P)(?:-[A-Za-z0-9]+)*-\d+\b")


def collect() -> dict:
    numbered_files: dict[str, int] = {}
    prefixes: dict[str, int] = {}
    prose_files: list[str] = []
    empty_files: list[str] = []
    for f in sorted(PITFALLS.rglob("*.md")):
        if f.name == "_INDEX.md":
            continue
        rel = f.relative_to(REPO).as_posix()
        if f.stat().st_size == 0:
            empty_files.append(rel)
            continue
        text = f.read_text(encoding="utf-8", errors="ignore")
        hits = [h for h in HEADING_RE.findall(text) if TOKEN_RE.match(_clean(h))]
        if hits:
            numbered_files[rel] = len(hits)
            for h in hits:
                tok = PREFIX_RE.match(_clean(h)).group(0)
                pre = re.sub(r"-\d+$", "", tok)
                prefixes[pre] = prefixes.get(pre, 0) + 1
        else:
            prose_files.append(rel)
    return {
        "entries": sum(numbered_files.values()),
        "numbered_files": len(numbered_files),
        "prose_files": len(prose_files),
        "empty_files": len(empty_files),
        "total_files": len(numbered_files) + len(prose_files) + len(empty_files),
        "prefixes": prefixes,
        "per_file": numbered_files,
        "prose_list": prose_files,
    }


def prefix_line(m: dict) -> str:
    """生成「编号前缀族」那一行（同样由工具生成）。"""
    ps = sorted(m["prefixes"])
    return (
        f"| **编号前缀族** | **{len(ps)} 个具体前缀**（实测 2026-10-27 · 口径同上）"
        f"：" + " · ".join(f"`{p}-`" for p in ps) + " |"
    )


def index_line(m: dict) -> str:
    """生成索引里那一行（**由工具生成** ⇒ 不再是手工快照）。"""
    return (
        f"| **全域坑位条目** | **{m['entries']} 条** / **{m['numbered_files']} 份编号式台账**"
        f"（另 **{m['prose_files']} 份散文式台账**用散文标题、不计入条数 · "
        f"**{m['empty_files']} 份 0 字节**坑位待填）"
        f"⇒ **口径由 `tools/check-ledger-count.py` 写死**（标题文本以 `PIT-`/`PF-`/`P-` 开头且以 `-<数字>` 结尾）"
        f"⇒ **数字是机器生成的，不是手抄的** |"
    )


def main() -> int:
    args = sys.argv[1:]
    m = collect()
    line = index_line(m)

    if "--print" in args:
        print(f"条目（编号式）: {m['entries']}")
        print(f"编号式台账: {m['numbered_files']} 份")
        print(f"散文式台账: {m['prose_files']} 份")
        print(f"0 字节坑位: {m['empty_files']} 份")
        print(f"合计 .md（不含 _INDEX.md）: {m['total_files']}")
        print()
        print("生成的索引行：")
        print(line)
        return 0

    if not INDEX.is_file():
        print(f"❌ 索引不存在：{INDEX}")
        return 1
    text = INDEX.read_text(encoding="utf-8")
    old_line = next(
        (l for l in text.split("\n") if l.startswith("| **全域坑位条目** |")),
        None,
    )
    if old_line is None:
        print("❌ 索引里找不到「| **全域坑位条目** |」那一行")
        return 1

    pline = prefix_line(m)
    old_pline = next(
        (l for l in text.split("\n") if l.startswith("| **编号前缀族** |")),
        None,
    )
    if "--fix" in args:
        new_text = text.replace(old_line, line, 1)
        if old_pline is not None:
            new_text = new_text.replace(old_pline, pline, 1)
        if new_text == text:
            print("✅ 已是最新，无需改动")
            return 0
        INDEX.write_text(new_text, encoding="utf-8")
        print(f"✅ 索引计数已更新：\n   旧：{old_line}\n   新：{line}")
        if old_pline is not None:
            print(f"\n✅ 前缀族行已更新：\n   旧：{old_pline}\n   新：{pline}")
        return 0

    # ---- 守门：逐项比对 ----
    failures: list[str] = []
    me = IDX_ENTRIES_RE.search(old_line)
    mf = IDX_FILES_RE.search(old_line)
    mk = IDX_EMPTY_RE.search(old_line)
    if not me:
        failures.append(f"🔴 索引行里**找不到「N 条」** ⇒ 口径已变 ⇒ 请跑 `--fix`")
    elif int(me.group(1)) != m["entries"]:
        failures.append(
            f"🔴 条目数漂移：索引称 **{me.group(1)} 条**，实测 **{m['entries']} 条**"
            f"（差 {m['entries'] - int(me.group(1)):+d}）⇒ 跑 `--fix` 订正"
        )
    if not mf:
        failures.append("🔴 索引行里**找不到「N 份编号式台账」** ⇒ 口径已变 ⇒ 请跑 `--fix`")
    elif int(mf.group(1)) != m["numbered_files"]:
        failures.append(
            f"🔴 台账份数漂移：索引称 **{mf.group(1)} 份**，实测 **{m['numbered_files']} 份编号式**"
            f"（差 {m['numbered_files'] - int(mf.group(1)):+d}）⇒ 跑 `--fix` 订正"
        )
    if old_pline is not None and old_pline != pline:
        failures.append(
            f"🔴 编号前缀族漂移：索引称「{old_pline[:40]}…」，实测 **{len(m['prefixes'])} 个前缀**"
            " ⇒ 跑 `--fix` 订正"
        )
    if not mk:
        failures.append("🔴 索引行里**找不到「N 份 0 字节」** ⇒ 口径已变 ⇒ 请跑 `--fix`")
    elif int(mk.group(1)) != m["empty_files"]:
        failures.append(
            f"🔴 0 字节份数漂移：索引称 **{mk.group(1)} 份**，实测 **{m['empty_files']} 份**"
            f"（差 {m['empty_files'] - int(mk.group(1)):+d}）⇒ 跑 `--fix` 订正"
        )

    print("台账计数守门")
    print("=" * 60)
    print(f"  条目（编号式）: {m['entries']}")
    print(f"  编号式台账: {m['numbered_files']} 份")
    print(f"  散文式台账: {m['prose_files']} 份")
    print(f"  0 字节坑位: {m['empty_files']} 份")
    print("-" * 60)
    if failures:
        print("❌ FAIL\n")
        for f in failures:
            print(f)
            print()
        return 1
    print("✅ PASS")
    print("  · 索引的条目计数与仓库现状一致（口径由本工具写死）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
