#!/usr/bin/env python3
"""Pitfalls 台账**状态字段覆盖率**核验（一次性核验工具 · **不是 `check-*` ⇒ 不计入 §6.3 的 27 个计数**）。

为什么需要它（AGENTS.md §3.3「跑绿 ≠ 对」）：
    「每条 Pitfall 有状态」这条判据，**必须能被机器复算**，否则「已补齐」只是一句人话。
    ⚠️ **口径必须与 `tools/check-ledger-count.py` 完全一致** ——
    之前用**宽正则**（`^#+ *P[A-Za-z0-9_-]*-[0-9]+`）数出 **380**，而守门的口径是 **336**
    ⇒ **同一批条目，两个口径 ⇒ 判据永远对不上** ⇒ 本工具**直接复用守门的识别口径**。

条目的识别（与 `tools/check-ledger-count.py` 同源）：
    标题 = `^#{2,4}\\s+(.+)$` ⇒ 去掉装饰字符（emoji 等）后
    以 `PIT` / `PF` / `P` 开头并跟编号段 ⇒ `^(?:PIT|PF|P)(?:-[A-Za-z0-9]+)*-\\d+\\b`

「有状态」的判定：条目的**正文段**（到下一条标题为止）里出现
    `仍生效` / `已闭环` / `已撤销` / `待核实` / `已失效`
**任一**标记 ⇒ 算「有状态」。

用法：
    python3 tools/difftest/ledger-status-coverage.py            # 报告 + 断言（严格）
    python3 tools/difftest/ledger-status-coverage.py --report   # 只报告，不判失败
    python3 tools/difftest/ledger-status-coverage.py --domain users   # 只看某域

退出码：0 = 覆盖率 100% 且 0 字节文件为 0（或 `--report` 模式恒 0）；1 = 不达标。
"""
from __future__ import annotations

import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
PITFALLS = ROOT / "docs/Pitfalls"

# ── 与 tools/check-ledger-count.py 同源的识别口径 ──
HEADING_RE = re.compile(r"^#{2,4}\s+(.+)$")
TOKEN_RE = re.compile(r"^(?:PIT|PF|P)(?:-[A-Za-z0-9]+)*-\d+\b")
DECOR = "🔴🟡✅❌⚫⚠️⬜ "

STATUS_MARKERS = ("仍生效", "已闭环", "已撤销", "待核实", "已失效")


def entries_of(path: pathlib.Path):
    """返回 [(行号, 标题, 正文)]；口径同守门。"""
    try:
        lines = path.read_text(encoding="utf-8").split("\n")
    except Exception:
        return []
    idx = []
    for i, l in enumerate(lines):
        m = HEADING_RE.match(l)
        if not m:
            continue
        title = m.group(1).strip().lstrip(DECOR).strip()
        if TOKEN_RE.match(title):
            idx.append((i, title))
    out = []
    for k, (i, t) in enumerate(idx):
        end = idx[k + 1][0] if k + 1 < len(idx) else len(lines)
        out.append((i + 1, t, "\n".join(lines[i:end])))
    return out


def main() -> int:
    report_only = "--report" in sys.argv
    domain = None
    if "--domain" in sys.argv:
        i = sys.argv.index("--domain")
        domain = sys.argv[i + 1] if i + 1 < len(sys.argv) else None

    total = 0
    with_status = 0
    empty_files = []
    per_dir = {}
    missing = []

    base = PITFALLS / domain if domain else PITFALLS
    for dp, _, fns in os.walk(base):
        for fn in sorted(fns):
            if not fn.endswith(".md"):
                continue
            p = pathlib.Path(dp) / fn
            rel = str(p.relative_to(ROOT))
            if p.stat().st_size == 0:
                empty_files.append(rel)
            es = entries_of(p)
            if not es:
                continue
            key = rel.split("/")[2] if rel.count("/") > 2 else "(root)"
            d = per_dir.setdefault(key, [0, 0])
            for ln, title, body in es:
                total += 1
                d[0] += 1
                if any(s in body for s in STATUS_MARKERS):
                    with_status += 1
                    d[1] += 1
                else:
                    missing.append((rel, ln, title))

    print("Pitfalls 台账 · 状态字段覆盖率")
    print("=" * 60)
    print(f"  条目总数（守门口径） = {total}")
    print(f"  带状态标记的条目      = {with_status}")
    print(f"  无状态标记的条目      = {len(missing)}"
          f"（缺失率 {len(missing) * 100 // max(total, 1)}%）")
    print(f"  0 字节坑位文件        = {len(empty_files)}")
    if per_dir:
        print("-" * 60)
        for k in sorted(per_dir):
            t, h = per_dir[k]
            flag = "✅" if h == t else "🔴"
            print(f"  {flag} {k:14s} {h}/{t}")
    if missing:
        print("-" * 60)
        print("  无状态条目（前 20）:")
        for x in missing[:20]:
            print(f"    {x[0]}:{x[1]}  {x[2][:60]}")
    if empty_files:
        print("-" * 60)
        print("  0 字节坑位文件:")
        for x in empty_files:
            print(f"    {x}")
    print("=" * 60)

    if report_only:
        return 0
    ok = True
    if total and with_status != total:
        print(f"❌ 状态覆盖率未达 100%：{with_status}/{total}")
        ok = False
    if empty_files:
        print(f"❌ 0 字节坑位文件未归零：{len(empty_files)} 个")
        ok = False
    if ok:
        print("✅ 状态字段覆盖率 100% ＋ 0 字节坑位文件为 0")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
