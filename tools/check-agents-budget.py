#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-agents-budget.py —— AGENTS.md 的**体量守门**。

════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════
AGENTS.md 是本项目**唯一每个 agent 都会自动加载**的根政策文件，
而运行时把它截断到 **32,768 字节**（`local-runtime` 的 project-instructions 预算）。
超出的部分**永远进不了上下文** —— 不是「读起来长」，是**根本没被读到**。

2026-10-23 实测：瘦身前 373,323 B ⇒ **只有 8.9% 进入上下文**，91% 是死字。

本脚本把「AGENTS.md 必须全文可进上下文」变成**机器会看的断言**，
而不是靠每个 agent 自觉。配合 AGENTS.md §〇 顶部的「往哪写」约定使用。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
0 = 在预算内
1 = 超预算
"""

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AGENTS = os.path.join(ROOT, "AGENTS.md")
BUDGET = 32768  # 与运行时 local-runtime 的 project-instructions 预算一致


def main():
    size = os.path.getsize(AGENTS)
    text = open(AGENTS, encoding="utf-8").read()
    lines = text.count("\n") + 1

    bar_n = 40
    used = min(size, BUDGET) * bar_n // BUDGET
    bar = "█" * used + "░" * (bar_n - used)

    print("=" * 70)
    print("AGENTS.md 体量守门（注入上下文预算 32,768 B）")
    print("=" * 70)
    print(f"\n  AGENTS.md  {size:,} B / {lines:,} 行   [{bar}]  {size/BUDGET*100:5.1f}%")

    if size <= BUDGET:
        print(f"\n  ✅ 在预算内，余量 {BUDGET - size:,} B。全文可进上下文。")
        if BUDGET - size < 1024:
            print("  ⚠️ 余量不足 1 KB —— 下一次写修复记录就会顶破。"
                  "逐条记录请写 `docs/DRIFT/_cross/fix-round-<日期>.md`，"
                  "本文件只留结论 + 一行指针。")
        return 0

    over = size - BUDGET
    print(f"\n  ❌ 超预算 {over:,} B（{size/BUDGET*100:.1f}%）—— "
          f"**最末的 {over:,} B 永远进不了上下文**。")
    print("\n  修法（按 §〇 顶部「往哪写」约定）：")
    print("   1. 逐条漂移与修复记录 ⇒ docs/DRIFT/<域>/<模块>.md 或 docs/DRIFT/_cross/")
    print("   2. 踩坑与元教训     ⇒ docs/Pitfalls/")
    print("   3. 签字偏离         ⇒ docs/Intentional_Deviations/")
    print("   4. AGENTS.md 只留**结论 + 一行指针**，规则条文一条不减。")
    print("\n  搬完用：python3 tools/drift-move.py extract / verify / delete")
    print("=" * 70)
    return 1


def check_future_dates():
    """§6.2「核对日期必须由一次真实执行产出」——**未来日期的核对是不可能的**。

    2026-10-25 实测：另一个会话把 10-25 的实测全部标成了 **2026-10-26**，
    35 处、跨 14 个文件与 4 个档案，无人发现 —— 因为这条纪律**从来没有机器检查**。
    一个不存在的日期会让人把「未验证」的行读成「刚验证过」。
    """
    import datetime
    import glob
    import re

    today = datetime.date.today()
    hits = []
    for f in ["AGENTS.md"] + glob.glob(os.path.join(ROOT, "docs/**/*.md"), recursive=True):
        try:
            txt = open(f, encoding="utf-8").read()
        except OSError:
            continue
        for m in re.finditer(r"\b(20\d{2}-\d{2}-\d{2})\b", txt):
            try:
                d = datetime.date.fromisoformat(m.group(1))
            except ValueError:
                continue
            if d > today:
                ln = txt[:m.start()].count("\n") + 1
                hits.append((os.path.relpath(f, ROOT), ln, m.group(1)))
    if not hits:
        return 0
    print("=" * 70)
    print("🔴 未来日期检查（§6.2：核对日期必须由一次真实执行产出）")
    print("=" * 70)
    print(f"\n  今天 {today}，下列 {len(hits)} 处日期在**未来** —— 不可能已核对：\n")
    for f, ln, d in hits[:30]:
        print(f"    {f}:{ln}  {d}")
    if len(hits) > 30:
        print(f"    …… 另有 {len(hits)-30} 处")
    print("\n  ⇒ 这些条目的状态一律按 `UNKNOWN` 处理；先改日期，再谈结论。")
    print("=" * 70)
    return 1


if __name__ == "__main__":
    rc = main()
    rc |= check_future_dates()
    sys.exit(rc)