#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`docs/Intentional_Deviations/_INDEX.md` 的**签字数 / 候选数**必须可复算（**不是 `check-*` ⇒ 不计入 §6.3 的 27 守门计数**）。

先例：`tools/check-ledger-count.py`（Pitfalls 条目数）· `tools/difftest/ledger-status-coverage.py`（状态字段覆盖率）。

═══════════ 为什么要它 ═══════════

`docs/Intentional_Deviations/_INDEX.md` 头部有一张计数表：
**✅ 已签字 47 条** / **⏳ 未签字候选 17 条**。

⚠️ **它原先是手写快照**：口径写成**散文**（「一条 = 一个有独立编号的签字条目」＋ ①同号不同域各算一条
②同号跨文件转录同一件事只算一条 ③已撤销/已关闭的不计）⇒ **没有任何脚本能复算它**
⇒ 属本仓反复治理的同一类病：**不可复算的断言 = 假保证**。

⇒ 本工具把口径**写死成代码**，并把索引里那两行变成**由本工具生成**的产物。

═══════════ 口径（写死在代码里） ═══════════

1. **条目** = 二~四级标题；标题文本剥掉前导装饰（`*` / `` ` `` / `~~` / `🆕` / `⏳` / `·` / `—` 等）后
   **以编号 token 开头**。
   ⚠️ **只「引用」编号的标题不算条目**（如「与 `SD-USERAPI-002` 的关系」）⇒ 这一条是
   **重复计数的主要来源**，靠「必须以编号开头」把它排除。
2. **编号 token** = `SD-…` / `DEV-…` / `N-n` / `A-n` / `C-n` / `D-n`。
3. **状态只认「规范状态字段」**（**不猜**）—— 取值域：`✅ 仍成立` / `⛔ 已失效·已撤销` / `⏳ 未签字`。
   识别形态（按优先级）：
   ① 标题里的 `**状态（核对日期 YYYY-MM-DD）：X**` / `**状态**：X`；
   ② 正文里最后一个 `> **状态…**：X` / `| 状态 | X |` / `| **状态** | X |`。
   ⚠️ **正文里散落的「已撤销」「仍生效」等词不算状态字段** —— 那多半是**历史叙述或对别条的回指**
   （实测：按全文扫会把 5 条**活着的已签字条目**误判成「撤销」）。
4. **取不到规范状态字段 ⇒ 「缺状态字段」**（不是「未签字」，也不是「已签字」）⇒
   **计数不可信** ⇒ 工具会把它们单列，并给出该目录的**状态字段覆盖率**。
5. **转录去重**：标题里含「转录自」⇒ **不独立计数**（它自己声明是副本）⇒ 对应索引口径 ②。
6. **同号不同事各算一条**（口径 ①）⇒ **不做**跨文件的同号合并。

═══════════ 输出与退出码 ═══════════

  打印实测的「已签字 / 未签字 / 撤销 / 未定级」四个数 ＋ 逐条清单。
  `--strict` 下：与索引里的数不一致 ⇒ **EXIT=1**。
  `--fix`：**只改索引里那两行数**（原口径说明保留）。

复现
----
    python3 tools/difftest/ledger-deviation-count.py           # 报告
    python3 tools/difftest/ledger-deviation-count.py --strict  # 守门
    python3 tools/difftest/ledger-deviation-count.py --fix     # 回填索引
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "docs/Intentional_Deviations"
INDEX = BASE / "_INDEX.md"

HEADING_RE = re.compile(r"^(#{2,4})\s+(.+?)\s*$")
# 编号 token
ID_RE = re.compile(r"(?:SD|DEV)[-_][A-Za-z0-9_.\-]+|[NACD]-\d+")
# 前导装饰（标题里编号前面可能夹这些）
# ⚠️ **2026-10-29 修正**：`⏳` 是**候选条目的固定前缀**（`### ⏳ SD-2026-10-23-09（候选）：…`）
#    ⇒ 原正则漏了它 ⇒ like 域 4 条候选**整族失配**（⇒ 候选数被少算）。
LEAD_RE = re.compile(
    r"^[\s>*_`~·—–\-（）()#]*"
    r"(?:🆕|⏳|🏷|⚠️|📌|⛔|✅|❌|🟡)?"
    r"[\s>*_`~·—–\-（）()#]*"
    r"(?:~~)?`?"
)
REVOKE_RE = re.compile(r"⛔|已撤销|已失效|已关闭|❌|撤销记录")
CAND_RE = re.compile(r"⏳|候选|未签字|待签字|🏷")
SIGNED_RE = re.compile(r"✅|已签字|仍成立|用户拍板|留痕")
TRANSCRIPT_RE = re.compile(r"转录自|同族条目|同族\s*`")


# 规范状态字段（**只认这些形态**，不猜）
#   标题：`**状态（核对日期 2026-10-29）：✅ 仍成立**` / `**状态**：✅ 仍成立`
#   正文：`> **状态（…）**：X` / `| 状态 | X |` / `| **状态** | X |`
STATUS_FIELD_TITLE_RE = re.compile(
    r"\*\*状态(?:（[^）]*）|\([^)]*\))?\s*[：:]\s*(?P<v>[^*|]+)")
STATUS_FIELD_BODY_RE = re.compile(
    r"^\s*(?:>\s*)?\|?\s*\*{0,2}状态\*{0,2}\s*(?:（[^）]*）|\([^)]*\))?\s*[：|]\s*"
    r"(?P<v>[^|]+?)\s*(?:\||\*{0,2}\s*$)",
    re.M)
# **订正格式**（本 phase 规约 §三·1 规定的写法）：`⚠️ 2026-10-29 真实性复核：原判 X ⇒ 现行态 = Y`
# ⇒ 这**本身就是规范状态来源**（而且是**最新的**订正 ⇒ 优先级高于旧字段）
CURRENT_STATE_RE = re.compile(r"现行态\s*=\s*(?P<v>[^。\n|]+)")
# lanes 实测使用的另一种写法：`> **签字复核（核对日期 2026-10-29）**：**仍成立 ⇒ 签字有效**`
#                              `> ⚠️ **2026-10-29 真实性复核（W-B lane）**：…**仍成立**`
RECHECK_RE = re.compile(
    r"(?:签字复核|真实性复核)[^。\n]{0,80}?[：:]\s*\*{0,2}(?P<v>[^（。\n*|]{2,40})")
# 取值 → 分类
VALUE_MAP = [
    ("已失效", "撤销/失效"), ("已撤销", "撤销/失效"), ("已关闭", "撤销/失效"), ("撤销", "撤销/失效"),
    ("未签字", "未签字"), ("待签字", "未签字"), ("候选", "未签字"),
    ("仍成立", "已签字"), ("已签字", "已签字"), ("仍生效", "已签字"),
]


def _value_kind(v: str) -> str | None:
    for key, kind in VALUE_MAP:
        if key in v:
            return kind
    return None


def _classify(title: str, seg: str) -> tuple[str, str]:
    """返回 (分类, 规范状态字段原文)。**只认规范状态来源**。"""
    # ① **订正格式**（最新，优先级最高）
    hits = list(CURRENT_STATE_RE.finditer(seg))
    for m in reversed(hits):
        k = _value_kind(m.group("v"))
        if k:
            return k, m.group(0).strip()
    # ①' **复核句式**（lanes 实测用法）
    hits = list(RECHECK_RE.finditer(seg))
    for m in reversed(hits):
        k = _value_kind(m.group("v"))
        if k:
            return k, m.group(0).strip()[:120]
    # ② 标题里的规范状态字段
    m = STATUS_FIELD_TITLE_RE.search(title)
    if m:
        k = _value_kind(m.group("v"))
        if k:
            return k, m.group(0)
    # ③ 正文里最后一个规范状态字段
    hits = list(STATUS_FIELD_BODY_RE.finditer(seg))
    for m in reversed(hits):          # 最后一个 = 最新（订正是追加在后面的）
        k = _value_kind(m.group("v"))
        if k:
            return k, m.group(0).strip()
    return "未定级", ""


def _entries() -> list[dict]:
    out = []
    for p in sorted(BASE.rglob("*.md")):
        if p.name == "_INDEX.md":
            continue
        lines = p.read_text(encoding="utf-8", errors="ignore").split("\n")
        # 先取全部标题（判定条目边界）
        heads = [(i, HEADING_RE.match(l).group(2))
                 for i, l in enumerate(lines) if HEADING_RE.match(l)]
        head_idx = [i for i, _ in heads]
        for k, (i, title) in enumerate(heads):
            body = LEAD_RE.sub("", title).strip().lstrip("~~").lstrip("`")
            mm = ID_RE.match(body)
            if not mm:
                continue
            ident = mm.group(0)
            # 条目正文段 = 标题行 → 下一个标题行
            end = head_idx[k + 1] if k + 1 < len(head_idx) else len(lines)
            seg = "\n".join(lines[i:end])
            kind, field = _classify(title, seg)
            if TRANSCRIPT_RE.search(title):
                kind = "转录（不独立计数）"
            out.append({
                "file": str(p.relative_to(ROOT)),
                "line": i + 1,
                "id": ident,
                "title": title,
                "kind": kind,
                "field": field,
                "seg": seg,
            })
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true")
    ap.add_argument("--fix", action="store_true")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()

    entries = _entries()
    counts: dict[str, int] = {}
    for e in entries:
        counts[e["kind"]] = counts.get(e["kind"], 0) + 1

    signed = counts.get("已签字", 0)
    cand = counts.get("未签字", 0)
    revoked = counts.get("撤销/失效", 0)
    transcript = counts.get("转录（不独立计数）", 0)
    unknown = counts.get("未定级", 0)

    print("Intentional_Deviations · 签字数 / 候选数复算")
    print("=" * 68)
    print(f"  条目总数（标题以编号开头）= {len(entries)}")
    print(f"  ✅ 已签字             = {signed}")
    print(f"  ⏳ 未签字（候选）       = {cand}")
    print(f"  ⛔ 撤销 / 失效（不计）   = {revoked}")
    print(f"  🔁 转录（不独立计数）    = {transcript}")
    print(f"  ⚠️ 未定级（**缺状态字段**）= {unknown}")
    if unknown:
        print(f"  📊 状态字段覆盖率      = {(len(entries)-unknown)/len(entries)*100:.1f}%"
              f"（{len(entries)-unknown}/{len(entries)}）")
    print("-" * 68)

    if unknown:
        print(f"❌ 缺状态字段的条目（{unknown} 条）—— 状态取值域："
              "`✅ 仍成立` / `⛔ 已失效·已撤销` / `⏳ 未签字`：")
        for e in entries:
            if e["kind"] == "未定级":
                print(f"  {e['file']}:{e['line']}  {e['id']:26s} {e['title'][:82]}")
        print("-" * 68)

    if args.list:
        for k in ("未定级", "撤销/失效", "未签字", "已签字", "转录（不独立计数）"):
            sel = [e for e in entries if e["kind"] == k]
            if not sel:
                continue
            print(f"【{k}】{len(sel)} 条")
            for e in sel:
                print(f"  {e['file']}:{e['line']}  {e['id']:26s} {e['title'][:88]}")
            print("-" * 68)

    # 与索引比对
    txt = INDEX.read_text(encoding="utf-8")
    m_signed = re.search(r"\*\*✅ 已签字合计\*\*\s*\|\s*\*\*(\d+) 条\*\*", txt)
    m_cand = re.search(r"\*\*⏳ 未签字候选合计\*\*\s*\|\s*\*\*(\d+) 条\*\*", txt)
    idx_signed = int(m_signed.group(1)) if m_signed else None
    idx_cand = int(m_cand.group(1)) if m_cand else None
    print(f"  索引自称：已签字 = {idx_signed} · 未签字 = {idx_cand}")
    print(f"  实测     ：已签字 = {signed} · 未签字 = {cand}")

    if args.fix:
        if idx_signed is not None and idx_cand is not None:
            new = txt
            new = re.sub(r"(\*\*✅ 已签字合计\*\*\s*\|\s*\*\*)(\d+)( 条\*\*)",
                         rf"\g<1>{signed}\g<3>", new, count=1)
            new = re.sub(r"(\*\*⏳ 未签字候选合计\*\*\s*\|\s*\*\*)(\d+)( 条\*\*)",
                         rf"\g<1>{cand}\g<3>", new, count=1)
            if new != txt:
                INDEX.write_text(new, encoding="utf-8")
                print("✅ 索引两行已回填")
            else:
                print("ℹ️ 索引已是实测值")

    if args.strict:
        if idx_signed != signed or idx_cand != cand:
            print(f"❌ STRICT 失败：索引（{idx_signed}/{idx_cand}）≠ 实测（{signed}/{cand}）")
            return 1
        print("✅ 索引计数与实测一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
