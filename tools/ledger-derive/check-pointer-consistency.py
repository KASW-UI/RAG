#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-pointer-consistency.py —— **指针漂移棘轮守门**（派生层的输入完整性）。

══════ 背景（2026-10-30 · G4 lane 转来的发现，本层独立复核后接手）══════

`docs/RoundPlan/_unresolved.md` 与 `docs/DRIFT/_cross/grading/*` 里有大量
**硬编码 `文件:行号` 指针**指向各域台账 ⇒ ⇒ 派生层用这些指针做**反向链接**
⇒ ⇒ 而解析是**纯行号算术**（`derive-stage1.py:resolve_pointer`）：

    ① 精确行优先：`start == end == line_no` ⇒ 该表格行**自己**的编号
    ② 回落：条目段范围 `start <= line_no <= end`

⇒ ⇒ **任何 lane 在自己域台账里插一行 ⇒ 指针就偏一格 ⇒ 解析到别的条目**
⇒ ⇒ 「排期指针 / 证据缺口组」的成员就变了 ⇒ ⇒ **派工面被静默改掉**。

══════ 本守门怎么判（**不靠快照**，但用**例外集棘轮**）══════

**「意图」在源侧，不在目标侧。** ⚠️ 曾试过「目标行自己写的编号 vs 解析出的编号」
⇒ ⇒ **那是恒等式**（两者都从同一行算 ⇒ 永远相等 ⇒ 测不出任何东西）⇒ ⇒ 弃用。

⇒ ⇒ 真正能判的是**源侧那句话**：多数指针所在的**那一行**同时写明了它指向哪个编号
⇒ ⇒ 判据 = **「解析出的编号」应当出现在「指针所在那一行」写下的编号集合里**。

实测（2026-10-30 · 本仓全仓）：
  · 指针总数（`DOC_PTR_RE` 口径，与派生层**同一个**正则）= **3308**
  · 源行**有编号**（可判意图）= **2791**（**84.4%**）
  · ⇒ 其中解析结果与源句**一致** = **2512**（**90.0%**）
  · ⇒ **不一致** = **279**（10.0%）⇒ ⇒ 这批是**漂移候选**（其中也混着
    「源行另提他号」这种语义正常的 ⇒ ⇒ 见下）
  · 解析不出条目（指向非条目段）= **387** · 源行无编号 = **130**

⚠️ **为什么不直接拿 279 当「红」**：它们里混着语义正常的情况 ⇒ ⇒ 直接当门禁
   = **一上线就红 279 条** ⇒ ⇒ 等于没人看 ⇒ ⇒ ⇒ **改成棘轮**：

   **基线 = 当前这批例外**（`docs/DRIFT/_cross/pointer-drift-baseline.json`）
   ⇒ ⇒ **只有「新增」的漂移才红**；已知的 279 条是**存量**，不刷屏；
   ⇒ ⇒ 存量被修掉时**报出来**（可以剪枝）⇒ ⇒ ⇒ 棘轮只紧不松。

══════ 用法 ══════

    python3 tools/ledger-derive/check-pointer-consistency.py            # 守门（新增漂移 ⇒ EXIT=1）
    python3 tools/ledger-derive/check-pointer-consistency.py --report   # 只打印，不判
    python3 tools/ledger-derive/check-pointer-consistency.py --baseline # 重写基线（人工确认后）
    python3 tools/ledger-derive/check-pointer-consistency.py --root DIR  # 影子副本（变异测试用）

⚠️ **判据的鉴别力由变异测试保证**：`tools/difftest/mutate-pointer-consistency.py`
   ⇒ 往域台账里插一行 ⇒ 守门必须红。
"""
import importlib.util
import json
import os
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINE_REL = "docs/DRIFT/_cross/pointer-drift-baseline.json"
DERIVE_REL = "tools/ledger-derive/derive-stage1.py"


def load_derive(root: pathlib.Path):
    """载入派生层脚本（**复用它的解析函数** ⇒ ⇒ 判据与产物同源）。"""
    spec = importlib.util.spec_from_file_location("derive", root / DERIVE_REL)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    m.ROOT = root
    return m


def collect(root: pathlib.Path):
    """扫全仓指针 ⇒ 返回 (例外集, 统计)。

    例外 = 「指针所在行的编号集合」非空 **且** 「解析出的编号」不在其中。
    键 = (源文件相对路径, 目标文件相对 docs/ 的路径, 解析出的编号)
    ⇒ ⇒ **不含行号** ⇒ ⇒ 源文件整体移位**不会**造成假新增。
    """
    m = load_derive(root)
    excl = m.load_exclusions()
    _entries, line_index = m.scan_ledgers(excl)

    cache: dict[str, list[str]] = {}

    def target_lines(rel: str) -> list[str]:
        if rel not in cache:
            f = root / "docs" / rel
            cache[rel] = (f.read_text(encoding="utf-8", errors="replace").split("\n")
                          if f.exists() else [])
        return cache[rel]

    stats = {"指针总数": 0, "源行有编号": 0, "一致": 0, "不一致": 0,
             "解析不出条目": 0, "源行无编号": 0}
    found: dict[tuple[str, str, str], dict] = {}

    for p in sorted(root.glob("docs/**/*.md")):
        rel_src = str(p.relative_to(root))
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        for i, line in enumerate(text.split("\n"), 1):
            for mt in m.DOC_PTR_RE.finditer(line):
                stats["指针总数"] += 1
                rel = mt.group(1)
                tln = int(mt.group(2))
                resolved = m.resolve_pointer(line_index, rel, tln)
                src_ids = set(m.all_id_tokens(line))
                if resolved is None:
                    stats["解析不出条目"] += 1
                    continue
                if not src_ids:
                    stats["源行无编号"] += 1
                    continue
                stats["源行有编号"] += 1
                rid = resolved[0]
                if rid in src_ids:
                    stats["一致"] += 1
                    continue
                stats["不一致"] += 1
                key = (rel_src, rel, rid)
                info = found.setdefault(key, {
                    "src": rel_src, "src_line": i, "target": rel,
                    "target_line": tln, "resolved_id": rid,
                    "src_ids": sorted(src_ids)[:6],
                    "target_ids": sorted(set(m.all_id_tokens(
                        target_lines(rel)[tln - 1])))[:6] if 0 < tln <= len(target_lines(rel)) else [],
                })
    return found, stats


def main() -> int:
    args = sys.argv[1:]
    root = ROOT
    for i, a in enumerate(args):
        if a == "--root" and i + 1 < len(args):
            root = pathlib.Path(args[i + 1]).resolve()
    base_file = root / BASELINE_REL

    found, stats = collect(root)
    print("【指针一致性 · 派生层输入完整性】")
    print(f"   指针总数 {stats['指针总数']}（`DOC_PTR_RE` 口径 · 与派生层同一个正则）")
    print(f"   源行有编号 {stats['源行有编号']}（可判意图）· 一致 {stats['一致']} · 不一致 {stats['不一致']}")
    print(f"   解析不出条目 {stats['解析不出条目']} · 源行无编号 {stats['源行无编号']}")

    keys = sorted(found.keys())
    if "--baseline" in args:
        payload = {
            "_comment": "指针漂移**例外集**基线（棘轮）—— 只登记**已知**的例外；"
                        "守门只对**新增**的例外报红。生成器：tools/ledger-derive/check-pointer-consistency.py --baseline",
            "_口径": "键 = (源文件, 目标文件, 解析出的编号) ⇒ **不含行号** ⇒ 源文件移位不造成假新增",
            "entries": [found[k] for k in keys],
        }
        base_file.parent.mkdir(parents=True, exist_ok=True)
        base_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"✅ 基线已写入 {len(keys)} 条例外 → {BASELINE_REL}")
        return 0

    known = set()
    if base_file.exists():
        try:
            for e in json.loads(base_file.read_text(encoding="utf-8")).get("entries", []):
                known.add((e["src"], e["target"], e["resolved_id"]))
        except Exception as exc:
            print(f"❌ 基线读不出来（{exc}）⇒ ⇒ 先跑 --baseline")
            return 1
    else:
        print(f"❌ 基线文件不存在：{BASELINE_REL} ⇒ ⇒ 先跑 --baseline")
        return 1

    new = [k for k in keys if k not in known]
    fixed = [k for k in known if k not in set(keys)]
    print(f"   存量例外 {len(known)} 条 · 当前例外 {len(keys)} 条 ⇒ ⇒ **新增 {len(new)} 条** · 已消失 {len(fixed)} 条")

    if "--report" in args:
        for k in new:
            e = found[k]
            print(f"   🆕 {e['src']}:{e['src_line']} → {e['target']}:{e['target_line']}"
                  f" 解析出 `{e['resolved_id']}`，而该行写的是 {e['src_ids']}")
        return 0

    if new:
        print(f"\n🔴 **新增 {len(new)} 条指针漂移**（源句写的编号 ≠ 解析出的编号）⇒ ⇒ 派工面的「排期指针 / 证据缺口组」成员已被改掉")
        for k in new[:40]:
            e = found[k]
            print(f"   🆕 {e['src']}:{e['src_line']} → {e['target']}:{e['target_line']}"
                  f" 解析出 `{e['resolved_id']}`，而该行写的是 {e['src_ids']}")
        if len(new) > 40:
            print(f"   …（其余 {len(new) - 40} 条略）")
        print(f"   ⇒ ⇒ 修法：把指针的行号改到**当前**正确的行（目标行自己写着哪个编号，就指哪一行）")
        print(f"   ⇒ ⇒ 或：若该行确实另指别处 ⇒ 则说明**意图**变了 ⇒ 改源句的编号，别改行号")
        return 1
    if fixed:
        print(f"   🆙 有 {len(fixed)} 条存量例外**已消失** ⇒ ⇒ 可剪枝（跑 --baseline 前请人工确认）")
    print("✅ 无新增指针漂移")
    return 0


if __name__ == "__main__":
    sys.exit(main())
