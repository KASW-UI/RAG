#!/usr/bin/env python3
"""生成 `tools/ledger-derive/overlay-scope.txt` —— **U5-O 定级的适用域**。

⚠️ **为什么需要这张表**（2026-10-30 用户拍板「条目身份 = 编号 + 所属域目录」）：
   台账用**裸编号**（`E1` / `E2` / `L-4` / `T-6`…，无域前缀）⇒ 同一个编号在不同域
   **通常**指的不是同一件事 ⇒ 派生层把条目**按域拆开** ⇒ 于是**覆盖层也必须按域限定**，
   否则一个域的定级会套到另一个域的同名条目上（**串味**）。

⚠️ **这张表怎么来的 —— 不是猜的**：
   派工清单 [`docs/DRIFT/_cross/grading/_lanes.json`](../../../docs/DRIFT/_cross/grading/_lanes.json)
   逐条记录了「**哪一条台账条目**派给**哪个 lane**去定级」⇒ 那条定级的适用域
   = 那条台账条目的**所属域** ⇒ **机械可复算，零猜测**。

⚠️ **不叫 `check-*.py`** —— `tools/difftest/check-*.py` 会被 judge §六 6.3 的
   **27 条计数口径**算进去 ⇒ 新建会制造口径漂移（AGENTS.md §6.3 问题 4 的同族坑）。

用法：  python3 tools/ledger-derive/gen-overlay-scope.py [--check]
        `--check` = 只校验现有表与派工清单一致（不一致 ⇒ EXIT=1），不写盘。
"""
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LANES = ROOT / "docs/DRIFT/_cross/grading/_lanes.json"
OUT = ROOT / "tools/ledger-derive/overlay-scope.txt"

HEADER = """# 自动生成 —— 由派工清单（lane briefs）推出：每个 U5-O 定级**对应哪个域**的台账条目。
# ⚠️ 这不是猜的：派工清单逐条记录了「哪一条台账条目派给哪个 lane 定级」
#    ⇒ 该定级的适用域 = 那条条目的所属域。
# ⚠️ 手工订正请改本文件并注明理由（生成器 = tools/ledger-derive/gen-overlay-scope.py）。
"""


def build() -> set[tuple[str, str]]:
    """派工清单 ⇒ **{(编号, 域)}**（**节点级**）。

    ⚠️ **为什么必须是节点级而不是「编号 → 域集合」**（2026-10-30 派第 4 轮时暴露）：
      同一个编号**可能在两个域各有一条**（实测第 4 轮的 `G4-4` = article 域一条 +
      favorite 域一条 ⇒ 第 3 轮人工判定为「**不同的事**」⇒ 归一后**各判各的**）。
      ⇒ 若按编号聚成域集合 ⇒ **一个域的定级会套到另一个域** ⇒ **串味**。
      ⇒ ⇒ 每行 = **一个节点 (编号, 域)**；同一编号多域 ⇒ **多行**（不是一个逗号分隔的集合）。
    """
    data = json.loads(LANES.read_text(encoding="utf-8"))
    out: set[tuple[str, str]] = set()
    for lane in sorted(data):
        for it in data[lane]["条目"]:
            rel = it["source"].split(";")[0].rsplit(":", 1)[0]
            parts = rel.split("/")
            dom = parts[1] if len(parts) > 1 else "_root"
            out.add((it["id"], dom))
    return out


def render(scope: set[tuple[str, str]]) -> str:
    lines = [HEADER.rstrip("\n"), ""]
    for i, d in sorted(scope):
        lines.append(f"{i}\t{d}")
    return "\n".join(lines) + "\n"


def main() -> int:
    scope = build()
    text = render(scope)
    if "--check" in sys.argv:
        cur = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if cur != text:
            print("🔴 overlay-scope.txt 与派工清单不一致 ⇒ 跑 gen-overlay-scope.py 重生成")
            return 1
        print(f"✅ overlay-scope.txt 与派工清单一致（{len(scope)} 个节点）")
        return 0
    OUT.write_text(text, encoding="utf-8")
    ids = {i for i, _ in scope}
    multi = {i for i in ids if sum(1 for j, _ in scope if j == i) > 1}
    print(f"✅ 已生成 {OUT.relative_to(ROOT)}：{len(scope)} 个节点 / {len(ids)} 个编号"
          f"（跨多域的 {len(multi)} 个）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
