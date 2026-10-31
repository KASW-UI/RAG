# -*- coding: utf-8 -*-
"""守门：跨档「签字档计数」**活指针**必须等于机器实测值。

背景（2026-10-31 实证）
------------------------
`docs/Intentional_Deviations/_INDEX.md` 的权威表由 `ledger-deviation-count.py` 机器复算，
但**别的档里还有 17 处「现值 = 60 / 12」形态的活指针**（Stage1 验收表 / RoundPlan / judge /
DRIFT 索引 / IntDev 索引）⇒ ⇒ 它们不随权威表自动更新 ⇒ ⇒ 读者按这些指针取数会拿到**过期总数**。
本轮实测：权威表早已是 **62 已签字 / 13 未签字**，而跨档活指针仍写着 **60 / 12** ⇒ ⇒ 全部手工订正。

⇒ **本门把这类漂移变成机器可查**。

判定口径（五条，缺一不可）
------------------------
1. **只查「活指针」**：目标行必须带 **「现值」** 标记 ⇒ ⇒ 无标记的散落数字不查（那是叙述）。
2. **先剥历史段再取数**：同一行里 `原写 … ⇒` 之后、以及含「时点快照 / 该轮快照 / 该时点快照 /
   历史值」的片段是**历史值**，按本仓「原值一律保留」纪律**不得改动** ⇒ ⇒ 剥掉后再取数。
3. **剔除三类「不是总数」的数字**（**误报治理** —— 本轮逐处人工核对得出）：
   ① **增量**：`少算了 13 条` / `新增 1 条` ⇒ 是差值，不是总数；
   ② **族别分解式**：`该族共 15 条 = 4 已签字 + 10 未签字 + 1 撤销` ⇒ 算式里的每个数都不是总数；
   ③ **族作用域**：`CAND` 族 **10 条未签字** ⇒ 族内数，不是全仓总数。
4. **类型由邻近关键词判定**（已签字 / 未签字）；判不出来的 ⇒ **跳过并计数**（不猜）。
5. **口径唯一**：实测值取自 `tools/difftest/ledger-deviation-count.py`（**同一份机器复算**），
   本门**不另算一套**。

用法
----
    python3 tools/difftest/check-ledger-count-pointers.py            # 守门（EXIT=0 绿 / 1 红）
    python3 tools/difftest/check-ledger-count-pointers.py --report   # 只报告，不判红
"""
from __future__ import annotations

import importlib.util
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
COUNT_TOOL = ROOT / "tools" / "difftest" / "ledger-deviation-count.py"
DOCS = ROOT / "docs"


def _load_count_tool():
    spec = importlib.util.spec_from_file_location("devcount", COUNT_TOOL)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def measured() -> tuple[int, int]:
    """机器实测的 (已签字, 未签字) —— 与 `ledger-deviation-count.py` 同一口径。"""
    mod = _load_count_tool()
    counts: dict[str, int] = {}
    for e in mod._entries():
        kind = e.get("kind")
        if kind:
            counts[kind] = counts.get(kind, 0) + 1
    return counts.get("已签字", 0), counts.get("未签字", 0)


# ------------------------------------------------------------------ 剔除规则
# ① 历史：原写 X / Y ⇒ 之后的原值
_HIST_ORIG = re.compile(r"原写[^⇒|]{0,80}⇒")
# ① 历史：快照措辞
_HIST_MARK = ("时点快照", "该轮快照", "该时点快照", "历史值", "上一 phase", "原口径快照")
# ② 族别分解式：算式跨度（各数都不是总数）
_DECOMP = re.compile(r"[^|]{0,60}?已签字\s*\+\s*[^|]{0,30}?未签字[^|]{0,40}?撤销")
# ③ 增量措辞：数前面 12 字内出现这些词 ⇒ 是差值
_DELTA_WORDS = ("少算", "多算", "漏掉", "漏了", "新增", "增加", "减少", "多出", "差额", "差 ")
# ④ 族作用域：数前面 12 字内出现族标记 ⇒ 是族内数
_FAMILY_WORDS = ("族", "该族", "族内", "族共")


def _strip_history(line: str) -> str:
    out = _HIST_ORIG.sub("⇒", line)
    for mark in _HIST_MARK:
        idx = out.find(mark)
        while idx != -1:
            start = max(out.rfind("|", 0, idx), out.rfind("。", 0, idx), out.rfind("，", 0, idx)) + 1
            end = len(out)
            for sep in ("⇒", "。", "|"):
                p = out.find(sep, idx)
                if p != -1:
                    end = min(end, p)
            out = out[:start] + " " + out[end:]
            idx = out.find(mark)
    return out


def _strip_decompositions(line: str) -> str:
    return _DECOMP.sub(" ", line)


def _is_excluded(stripped: str, start: int) -> bool:
    """该数字是否属于「增量 / 族作用域」⇒ 不查。"""
    ctx = stripped[max(0, start - 14):start]
    if any(w in ctx for w in _DELTA_WORDS):
        return True
    if any(w in ctx for w in _FAMILY_WORDS):
        return True
    return False


# ------------------------------------------------------------------ 取活指针
_NUM_SIGNED = re.compile(r"(\d+)\s*条?\s*(?:\*\*)?已签字")
_NUM_CAND = re.compile(r"(\d+)\s*条?\s*(?:\*\*)?未签字(?:候选)?")
_PAIR = re.compile(r"现值见上方权威表\*{0,2}[（(]\s*(\d+)\s*/\s*(\d+)\s*[）)]")


def _claims_in_line(line: str) -> list[tuple[str, int, str]]:
    """⇒ [(类型, 数字, 原始片段)]；只取**全局总数**形态。"""
    if "现值" not in line:
        return []
    stripped = _strip_decompositions(_strip_history(line))
    out: list[tuple[str, int, str]] = []
    # 「现值见上方权威表**（62 / 13）**」
    for m in _PAIR.finditer(stripped):
        out.append(("已签字", int(m.group(1)), m.group(0).strip()))
        out.append(("未签字", int(m.group(2)), m.group(0).strip()))
    # 「N 条已签字」/「N 条未签字候选」
    for rx, kind in ((_NUM_SIGNED, "已签字"), (_NUM_CAND, "未签字")):
        for m in rx.finditer(stripped):
            if _is_excluded(stripped, m.start(1)):
                continue
            out.append((kind, int(m.group(1)), m.group(0).strip()))
    return out


def scan() -> tuple[list[dict], int]:
    signed, cand = measured()
    truth = {"已签字": signed, "未签字": cand}
    hits: list[dict] = []
    unknown = 0
    for p in sorted(DOCS.rglob("*.md")):
        try:
            lines = p.read_text(encoding="utf-8").split("\n")
        except Exception:
            continue
        for i, line in enumerate(lines, 1):
            claims = _claims_in_line(line)
            if not claims:
                continue
            for kind, val, frag in claims:
                if kind not in truth:
                    unknown += 1
                    continue
                if val != truth[kind]:
                    hits.append({
                        "file": p.relative_to(ROOT).as_posix(),
                        "line": i,
                        "kind": kind,
                        "claimed": val,
                        "measured": truth[kind],
                        "frag": frag,
                    })
    return hits, unknown


def main() -> int:
    report = "--report" in sys.argv
    signed, cand = measured()
    hits, unknown = scan()
    print("=" * 70)
    print("跨档「签字档计数」活指针 · 与机器实测对拍")
    print("=" * 70)
    print(f"  实测（ledger-deviation-count.py 口径）: 已签字 = {signed} · 未签字 = {cand}")
    if hits:
        print(f"  🔴 发现 {len(hits)} 处活指针与实测不符：")
        for h in hits:
            print(f"     {h['file']}:{h['line']}  {h['kind']} 声称 {h['claimed']} ⇒ 实测 {h['measured']}"
                  f"   〔{h['frag'][:40]}〕")
    else:
        print("  ✅ 全部活指针与实测一致")
    if unknown:
        print(f"  ⚠️ 无法判类型的片段：{unknown} 处（已跳过，不猜）")
    if report:
        return 0
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main())
