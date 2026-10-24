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


if __name__ == "__main__":
    sys.exit(main())