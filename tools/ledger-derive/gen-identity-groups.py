#!/usr/bin/env python3
"""由「同源判定」结果生成 `tools/ledger-derive/identity-groups.txt`（**节点级关系表**）。

⚠️ **为什么需要这张表**（2026-10-30 用户拍板「条目身份 = 编号 + 所属域目录」）：
   台账用**裸编号**（`E1` / `E2` / `L-4` / `T-6`…，**没有域前缀**）⇒ 同一个编号在不同域
   **通常**指的不是同一件事 ⇒ 派生层默认**按域拆开**（消除「一个域的定级被继承到另一个域」的串味）。
   ⇒ **但**有一批条目是**「同一件事的跨域登记 / 跨域别名登记」** ⇒ 拆开后同一件事会
   **被派两次单** / **一份读到旧快照（🔴）** ⇒ **虚增一条假缺口**。
   ⇒ 这批条目登记进**同源组** ⇒ **仍合并成一条**。

⚠️ **输入** = 人工逐条判定的结果
   [`docs/DRIFT/_cross/grading/_samesrc-W*.md`](../../../docs/DRIFT/_cross/grading/)
   ⇒ 判定三档：`同一件事` / `不同的事` / `无法判定`。
⚠️ **「无法判定」「不同的事」不进同源表** —— 派生层**不猜** ⇒ 那些编号保持拆开，
   并在派生层 §4.6「跨域同编号登记对照表」里露出。

⚠️ **本体位置怎么变成「节点」**（2026-10-30）：
   判定表第 5 列「本体位置」写的是 `路径:行号` ⇒ **机械反查**该行落在哪个条目段
   ⇒ 得到 `(编号, 所属域)` ⇒ **不需要人工再填一遍编号和域**（填一遍就会与判定表漂移）。
   ⚠️ **反查用派生层自己的 `line_index`**（`resolve_pointer`）⇒ **解析逻辑单一来源**
      ⇒ 不会出现「生成器与派生层对同一行号理解不一致」。
   ⚠️ **反查不到 ⇒ 不建边**（宁可拆开 ⇒ 在 §4.6 露出 ⇒ 也不误合并）。

⚠️ **不叫 `check-*.py`** —— `tools/difftest/check-*.py` 会被 judge §六 6.3 的 **27 条计数口径**
   算进去 ⇒ 新建会制造口径漂移。

用法：  python3 tools/ledger-derive/gen-identity-groups.py [--check]
        `--check` = 只校验现有表与判定结果一致（不一致 ⇒ EXIT=1），不写盘。
"""
from __future__ import annotations

import importlib.util
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRADING = ROOT / "docs/DRIFT/_cross/grading"
OUT = ROOT / "tools/ledger-derive/identity-groups.txt"
DERIVER = ROOT / "tools/ledger-derive/derive-stage1.py"

HEADER = """# 同源组（identity groups）—— 派生层条目身份的**例外清单**
#
# ⚠️ **默认口径（2026-10-30 用户拍板）**：条目身份 = **编号 + 所属域目录**
#    ⇒ 台账用**裸编号** ⇒ 同一个编号在不同域**通常**指的不是同一件事 ⇒ 一律**拆开**。
#    ⇒ 本文件是**例外**：这些**节点**登记的是**同一件事** ⇒ **仍合并成一条**
#    （否则同一件事被派两次单 / 一份读到旧快照 ⇒ **虚增假缺口**）。
#
# ⚠️ **本文件由 `tools/ledger-derive/gen-identity-groups.py` 从人工判定结果生成**
#    ⇒ 判定依据 = [`docs/DRIFT/_cross/grading/_samesrc-W*.md`](../../../docs/DRIFT/_cross/grading/)
#    ⇒ **「不同的事」「无法判定」不进本表**（派生层不猜 ⇒ 保持拆开 ⇒ 在派生层 §4.6 露出）。
#
# ⚠️ **节点 = （编号, 所属域）** —— 不是裸编号！
#    ⇒ 同一个编号在**组内**的域与**组外**的域**必须分开**（例如 `CF-001` 与 `DRIFT-CF-001`
#      在 `common` 域是同一件事 ⇒ 合并；但 `CF-001` 在别的域可能是**撞号** ⇒ 不合并）。
#
# ⚠️ **不得按「编号族」通配** —— 同一族里既有同源的也有撞号的
#    （实测 `T-3` 曾因扫描器切开复合编号而在 11 个域出现假登记 ⇒ 已修）。
#
# 格式：`<编号A>\t<域A>\t<编号B>\t<域B>\t<关系>\t<来源>`
#      关系 ∈ `同编号跨域`（A 与 B 同号）/ `别名同源`（A 与 B 不同号，长/短编号别名）
"""


def load_deriver():
    """加载派生层模块（**解析逻辑单一来源**）。"""
    spec = importlib.util.spec_from_file_location("ds_derive", DERIVER)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def read_judgements() -> dict[tuple[str, str], tuple[str, str, str]]:
    """`_samesrc-*.md` ⇒ `{(编号, 域): (判定, 本体位置列, 来源)}`。"""
    out: dict[tuple[str, str], tuple[str, str, str]] = {}
    for p in sorted(GRADING.glob("_samesrc-*.md")):
        lane = p.stem.replace("_samesrc-", "")
        for lineno, line in enumerate(p.read_text(encoding="utf-8").split("\n"), 1):
            s = line.strip()
            if not s.startswith("|"):
                continue
            cells = [c.strip() for c in s.strip("|").split("|")]
            if len(cells) < 5:
                continue
            ident = cells[0].strip().strip("*` ")
            dom = cells[1].strip().strip("*` ")
            verdict = cells[2].strip().strip("*` ")
            body = cells[4].strip()
            if not ident or ident == "编号" or set(ident) <= set("-: "):
                continue
            if verdict not in ("同一件事", "不同的事", "无法判定"):
                continue
            out[(ident, dom)] = (verdict, body, f"{lane}:{p.name}:{lineno}")
    return out


def build(mod, judgements):
    """⇒ (关系行, 反查不到的行, 别名行, 同域自环行, 编号登记索引)。"""
    _entries, line_index = mod.scan_ledgers(mod.load_exclusions())
    # 编号 → 实际登记位置（域, 文件, 行号）—— 供「未建边」条目自证
    reg_index: dict[str, list[tuple[str, str, int]]] = {}
    for _e in _entries:
        reg_index.setdefault(_e["id"], []).append((_e["domain"], _e["file"], _e["line"]))
    rels: list[tuple] = []
    unresolved: list[tuple] = []
    aliases: list[tuple] = []
    selfloops: list[tuple] = []
    seen: set[tuple[str, str, str, str]] = set()

    for (ident, dom), (verdict, body, src) in sorted(judgements.items()):
        if verdict != "同一件事":
            continue
        ptrs = list(mod.DOC_PTR_RE.finditer(body))
        if not ptrs:
            unresolved.append(((ident, dom), "第5列无「路径:行号」", src, body[:90]))
            continue
        found = []
        for m in ptrs:
            hit = mod.resolve_pointer(line_index, m.group(1), int(m.group(2)))
            if hit:
                found.append((hit, f"{m.group(1)}:{m.group(2)}"))
        if not found:
            unresolved.append(((ident, dom), "路径反查不到条目段",
                               src, "; ".join(f"{m.group(1)}:{m.group(2)}" for m in ptrs)[:90]))
            continue
        for _n, ((bid, bdom), ptr) in enumerate(found):
            key = (ident, dom, bid, bdom)
            if key in seen:
                continue
            seen.add(key)
            if (ident, dom) == (bid, bdom):
                # ⚠️ **自环** = 本体就是它自己 ⇒ 说明该编号在**同一域**被登记了两次
                #   ⇒ 派生层**本来就按域合并** ⇒ 不需要同源组条目
                #   ⇒ 但它暴露「同一域两处登记」⇒ 可能状态不一致 ⇒ 单列一档报告
                selfloops.append(((ident, dom), src, ptr))
                continue
            # ⚠️ **第 5 列的路径序位有语义**（2026-10-30 实测踩坑）：
            #   第 1 个 = **本体**；其余 = **引用方**（别的域对同一条目的引用）。
            #   ⚠️ 反例：`E38@global` 的第 5 列 = 「本体＝global `_README.md:388`；
            #      引用方＝users `user/rpc.md:832`／`admin/rpc.md:3699`／comment `vs-common.md:28`」
            #      ⇒ 其中 `:3699` 那一行**同时**被 E65 与 E38 引用 ⇒ 反查落到**另一个编号**
            #      ⇒ 若把它也当本体 ⇒ 建出 `E38@global ≡ E65@users` 这条**假边**
            #      ⇒ 合并了两件不相干的事。
            # ⇒ 规则：**第 1 个路径无条件建边**；其余路径**仅当反查编号 == 当前编号**才建边
            #   （同编号跨域 ⇒ 那确是「同一件事」）；**异编号只认第 1 个**（本体的定义位置）。
            if _n > 0 and bid != ident:
                continue
            kind = "同编号跨域" if bid == ident else "别名同源"
            rels.append((ident, dom, bid, bdom, kind, f"{src} ← {ptr}"))
            if bid != ident:
                aliases.append(((ident, dom), (bid, bdom), src))
    return rels, unresolved, aliases, selfloops, reg_index


def render(rels: list[tuple]) -> str:
    lines = [HEADER.rstrip("\n"), ""]
    for r in sorted(rels):
        lines.append("\t".join(r))
    return "\n".join(lines) + "\n"


def main() -> int:
    mod = load_deriver()
    judgements = read_judgements()
    rels, unresolved, aliases, selfloops, reg_index = build(mod, judgements)
    text = render(rels)

    if "--check" in sys.argv:
        cur = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if cur != text:
            print("🔴 identity-groups.txt 与判定结果不一致 ⇒ 跑 gen-identity-groups.py 重生成")
            print(f"   现表 {len(cur.splitlines())} 行 vs 应为 {len(text.splitlines())} 行")
            return 1
        print(f"✅ identity-groups.txt 与判定结果一致（{len(rels)} 条关系）")
        return 0

    OUT.write_text(text, encoding="utf-8")
    print(f"✅ 已生成 {OUT.relative_to(ROOT)}：{len(rels)} 条同源关系")
    print(f"   判定总行数 {len(judgements)} · 其中「同一件事」建关系 {len(rels)} 条")
    if unresolved:
        print(f"⚠️ 「同一件事」但**未建边** {len(unresolved)} 条（派生层保持拆开 ⇒ 在 §4.6 露出）：")
        for (ident, dom), why, src, body in unresolved:
            # ⚠️ **附上该编号的实际登记位置** —— 让读者能自己判断「这是缺陷还是本来就该拆开」。
            #   **「未建边」≠「有问题」**：常见两类**非缺陷**：
            #     ① **本体 = 自己**（该编号在**同一域**被登记两次）⇒ 派生层本就按域合并；
            #     ② **全仓唯一**（该编号只登记一次）⇒ 没有第二份可合并。
            where = reg_index.get(ident, [])
            locs = "；".join(f"{d}@{f}:{l}" for d, f, l in where[:4]) or "（派生层无登记）"
            print(f"    {ident}@{dom} · {why} · {src}")
            print(f"        第5列: {body}")
            print(f"        该编号的实际登记: {locs}")
    if aliases:
        print(f"⚠️ **别名同源**（不同编号 = 同一件事）{len(aliases)} 条 —— 需人工确认反查是否合理：")
        for a, b, src in aliases:
            print(f"    {a[0]}@{a[1]} ≡ {b[0]}@{b[1]}   ({src})")
    if selfloops:
        print(f"ℹ️ **同域重复登记**（本体 = 自己 ⇒ 派生层本就按域合并 ⇒ 不建边）{len(selfloops)} 条：")
        for node, src, ptr in selfloops:
            print(f"    {node[0]}@{node[1]}   ({src} ← {ptr})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
