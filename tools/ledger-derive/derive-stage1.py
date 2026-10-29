#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""derive-stage1.py —— 三目录台账 → **阶段一施工单派生层**（生成器 + 守门）

═══════════════════════════════════════════════════════════════════════
它回答什么问题
═══════════════════════════════════════════════════════════════════════

「`docs/DRIFT` + `docs/Intentional_Deviations` + `docs/Pitfalls` 这三本台账
（1183+ 条目 / 上千个编号），能不能直接当**阶段一施工单**用？」

**结论（本工具的立场）**：三目录是**事实与证据层**，**不是**施工单 ——
`docs/twoStage.md` §10 要求的 11 个字段里，三目录只自带其中一部分。
⇒ 本工具在**不改三目录一个字**的前提下，**派生**出一层：
`ID → 优先级 P0-P3 × 阶段归属 A-D × 是否阻断阶段一 → 对最终工程目标的影响
→ 排期指针 → 证据缺口`。

⚠️ **派生层不是「第二本台账」** —— 它**只投影**，不重新裁定：
每个派生值都带**规则号**（`rules` 列），每条都能回到**源文件:行号**（`source` 列）。
派生不出 ⇒ 写 `—`（待人工定级），**绝不猜**。

═══════════════════════════════════════════════════════════════════════
口径（全部写死在代码里 ⇒ 机器可复算）
═══════════════════════════════════════════════════════════════════════

**U1 · 条目（问题项）识别**
    位置候选 = 二~五级标题（去装饰符后**首个 token**）· 表格**首列**
    编号形态 = 宽正则 `^[A-Z][A-Za-z0-9]{0,14}(-[A-Za-z0-9]{1,24})*-\\d{1,4}[A-Za-z]?$`
    ⚠️ **不依赖前缀白名单** —— 前缀白名单会让整族条目隐形
       （实测旧 `ledger-claim-audit.py` 的 `ID_RE` 认不出 `MISMATCH-API-01` /
       `OP-04` / `T-6` / `N-1` / `CD-3` / `ADJ-01` … 共 **数百个唯一编号**）。
    排除 = `excluded-id-prefixes.txt`（**显式数据文件**，逐条带理由）
    必须 = 自身带**状态标记**（取值域按目录不同，见 U2）

**U2 · 状态归一**（取值域**按目录**限定，取值域外一律不认）
    DRIFT                  : ✅ 已一致 · 🟡 部分闭合 · 🔴 未闭合 · ⚫ 已签字偏离
    Pitfalls               : 仍生效 · 已闭环 · 已撤销 · 待核实 · 已失效
    Intentional_Deviations : ✅ 仍成立 · ⛔ 已失效 · ⏳ 未签字 · 待核实
    查找顺序 = **标题行优先** → 其次条目段

**U3 · 证据强度**（A/B/C 三档，**机器推导**，不采信自称）
    A 活体   = 条目段内点名了**已在活体台账实跑过**的探针
    B 静态   = 无活体探针，但有 `文件:行号` 指针（可复算的静态证据）
    C 无指针 = 两样都没有 ⇒ 该条结论**今天不可复算**

**U4 · 优先级 P0-P3**（`twoStage.md` §10 的严重程度）
    A 类阻断 ⇒ P0 ；🔴 / 仍生效 / ⏳ 未签字 ⇒ P1 ；🟡 / 待核实 ⇒ P2 ；其余 ⇒ P3

**U5 · 阶段归属 A-D**（`twoStage.md` §10）
    先取**权威定级表**（Stage1/05 §一·§三 · alignment §十三·一）
    → 再取**施工单章节**（_task-universe.md §一~§四）
    → 再取**已签字偏离**（IntDev ⇒ D，决策不是缺陷）
    → 都拿不到 ⇒ `—`（**待人工定级**，不猜）

**U6 · 是否阻断阶段一**：权威表的「阻断」列优先；否则 A⇒是 / 其它⇒否

**U7 · 对最终工程目标的影响**：C⇒影响（阶段 2 必须恢复）· D⇒不影响 ·
    A/B⇒间接 · `—`⇒未判定

**U8 · 排期指针**：
    ① `_task-universe.md` 的任务（**强链**：任务块里直接写了本条目编号，
       或任务块里的 `docs/DRIFT|Pitfalls|Intentional_Deviations/**` 指针**落在本条目段内**）
    ② `_unresolved.md` 的 BLK-xx / GAP-xx / ENV-xx / DEC-xx（同法反查）
    ③ 都没有 ⇒ **未排期**

**U9 · 证据缺口**：C⇒无可复算指针 · B+🔴⇒静态取证·缺活体 ·
    未定级⇒未定级⇒未排期 · 未排期⇒未排期 · 否则 —

═══════════════════════════════════════════════════════════════════════
产物
═══════════════════════════════════════════════════════════════════════

    docs/DRIFT/_cross/stage1-derivation.tsv   机器层：全量问题项 × 派生字段
    docs/DRIFT/_cross/stage1-derivation.md    人读层：口径 + 规则 + 全量表 +
                                               施工单视图 + 真缺口清单

═══════════════════════════════════════════════════════════════════════
用法
═══════════════════════════════════════════════════════════════════════

    python3 tools/ledger-derive/derive-stage1.py            # 重新生成产物
    python3 tools/ledger-derive/derive-stage1.py --check    # 守门：产物过期 ⇒ EXIT=1
    python3 tools/ledger-derive/derive-stage1.py --report   # 只打印统计，不写文件

**退出码**：0 = 通过（`--check` 下 = 产物与源台账同步）；1 = 漂移 / 缺口；2 = 环境问题。
"""

from __future__ import annotations

import argparse
import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOL_DIR = pathlib.Path(__file__).resolve().parent
OUT_DIR = ROOT / "docs/DRIFT/_cross"
TSV_PATH = OUT_DIR / "stage1-derivation.tsv"
MD_PATH = OUT_DIR / "stage1-derivation.md"
EXCLUDE_FILE = TOOL_DIR / "excluded-id-prefixes.txt"

LEDGER_DIRS = ("DRIFT", "Pitfalls", "Intentional_Deviations")

# ⚠️ **自噬防护**：派生层自己的产物**不得**被当作输入扫描。
#   实测（未防护时）：`docs/DRIFT/_cross/stage1-derivation.md` 被当成普通台账扫进去
#   ⇒ **凭空多出 1262 条「条目」** ⇒ 计数虚高、且 `--check` 模式会**自我否定**
#   （产物一改 ⇒ 源也变了 ⇒ 永远不同步 ⇒ 守门恒红）。
#   ⇒ 产物路径必须**显式排除**，且这个排除要**写在代码里**（不是靠「生成后再过滤」）。
SELF_ARTIFACTS = ("docs/DRIFT/_cross/stage1-derivation.md",)

# ⚠️ **2026-10-30 扩展** —— 「显式定级叠加层」目录也必须挡：
#   `docs/DRIFT/_cross/grading/*.md` 是**本轮人工定级**的落地处（U5-O 的输入），
#   它**长得像台账**（Markdown 表格、编号列）⇒ 若被 `scan_ledgers` 扫进去
#   ⇒ 凭空多出「条目」⇒ 计数虚高；被 `scan_grading_tables` 扫进去
#   ⇒ 会把自己的定级当成「台账已写的定级」⇒ **自证循环**。
#   ⇒ 两个扫描器**都要挡**（同族教训：自噬防护漏一个扫描器就等于没防护）。
SELF_ARTIFACT_DIRS = ("docs/DRIFT/_cross/grading/",)

# ── U5-O · 显式定级叠加层（人工本轮定级的落地处） ──────────────────────────
GRADING_DIR = ROOT / "docs/DRIFT/_cross/grading"

# ⚠️ **U5-O 保护字段** —— 这些字段一旦有「显式定级」，就**不许**被无定级的登记覆盖。
#   依据见 `derive()` 合并段的注释：同一编号在**汇总文件**与**主文件**两处登记时，
#   汇总文件那条日期更新就会把主文件的 U5-O 定级**静默覆盖**掉。
OV_FIELDS = ("stage", "blocking", "prio", "gap", "_stage_src", "_closed", "basis")

# ── U2 状态取值域（**按目录**限定） ───────────────────────────────────────
STATUS_SETS = {
    "DRIFT": {
        "✅": "✅ 已一致",
        "🟡": "🟡 部分闭合",
        "🔴": "🔴 未闭合",
        "⚫": "⚫ 已签字偏离",
    },
    "Pitfalls": {
        "仍生效": "仍生效",
        "已闭环": "已闭环",
        "已撤销": "已撤销",
        "已失效": "已失效",
        "待核实": "待核实",
    },
    "Intentional_Deviations": {
        "仍成立": "✅ 仍成立",
        "已失效": "⛔ 已失效",
        "已撤销": "⛔ 已撤销",
        "未签字": "⏳ 未签字",
        "待核实": "待核实",
    },
}
# 优先级映射（U4）：状态 → 优先级
PRIO_BY_STATUS = {
    "🔴 未闭合": "P1",
    "仍生效": "P1",
    "⏳ 未签字": "P1",
    "🟡 部分闭合": "P2",
    "待核实": "P2",
    "✅ 已一致": "P3",
    "⚫ 已签字偏离": "P3",
    "✅ 仍成立": "P3",
    "已闭环": "P3",
    "已撤销": "P3",
    "⛔ 已失效": "P3",
    "⛔ 已撤销": "P3",
}

# ── U1 编号形态（宽口径，不依赖前缀白名单） ────────────────────────────────
# U1 条目编号形态（**两种形态**）
#   ① 连字符形态 `DRIFT-MSG-001` / `MISMATCH-API-01` / `CD-3`
#   ② **字母+数字**形态 `E60` / `E65`（无连字符；旧 `ID_RE` 对它隐形）
ID_RE = re.compile(
    r"^(?:[A-Z][A-Za-z0-9]{0,14}(?:-[A-Za-z0-9]{1,24})*-\d{1,4}[A-Za-z]?"
    r"|E\d{1,3}[A-Za-z]?)$"
)
# 段内任意位置的编号 token（用于「任务块里写了本条目编号」与「标题里编号不在首位」）
# ⚠️ **两种形态都要收**：
#    ① 连字符形态 `DRIFT-MSG-001` / `S1-FOLLOW-001` / `MISMATCH-API-01` / `CD-3`
#    ② **字母+数字**形态 `E60` / `E65` / `E66`（无连字符！）
#    ⇒ 仓内既有的 `ledger-claim-audit.py` 的 `ID_RE` **只认 ①** ⇒ **`E60`/`E65`/`E66`
#      对它隐形**（而 `E65` 正是共享层那条 **A 类硬阻塞**）⇒ 同族的「盲区」问题。
ID_TOKEN_RE = re.compile(
    r"\b((?:[A-Z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*-\d+[A-Za-z]?)"
    r"|(?:E\d{1,3}[A-Za-z]?)(?![-\w]))\b"
)
# 证据指针 `路径:行号`（U3 的 B 档判据）
PTR_RE = re.compile(
    r"(?:[A-Za-z0-9_./\-*@]+(?:\.java|\.go|\.py|\.sh|\.md|\.yml|\.yaml|\.sql|\.xml|\.json|\.txt)):"
    r"(\d+(?:\s*[-–,]\s*\d+)*)"
)
# 工具引用（U3 的 A 档判据）
TOOLREF_RE = re.compile(r"tools/[A-Za-z0-9_./-]+\.(?:sh|py)")

DECOR = "🔴🟡🟢✅⚫❌⛔⏳🏷⚪⬜🟥⚠️❗️ "

# ���─ U5 权威定级来源 ───────────────────────────────────────────────────────
STAGE1_05 = ROOT / "docs/Stage1/05-差异与暂缓项清单.md"
ALIGNMENT = ROOT / "docs/DRIFT/common/alignment-2026-10-23.md"
TASK_UNIVERSE = ROOT / "docs/RoundPlan/_task-universe.md"
UNRESOLVED = ROOT / "docs/RoundPlan/_unresolved.md"
LIVE_LEDGER = ROOT / "docs/DRIFT/_cross/live-parity-2026-10-30.md"


def clean_heading(text: str) -> str:
    t = text.strip().lstrip("*`~ ")
    while t and t[0] in DECOR:
        t = t[1:].lstrip()
    return t.strip()


def first_token(text: str) -> str | None:
    t = clean_heading(text)
    m = re.match(r"^([A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*)", t)
    return m.group(1) if m else None


def load_exclusions() -> dict[str, str]:
    """U1 排除清单（**显式数据文件** ⇒ 可复核、可改、不藏在代码里）。"""
    out: dict[str, str] = {}
    if not EXCLUDE_FILE.exists():
        return out
    for line in EXCLUDE_FILE.read_text(encoding="utf-8").split("\n"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        out[parts[0].strip()] = parts[1].strip()
    return out


# ════════════════════════════════════════════════════════════════════════
# U1 · 条目扫描
# ════════════════════════════════════════════════════════════════════════

KEEP_OUT_RE = re.compile(r"阶段归属定级附录")


def compute_keepouts(lines: list[str]) -> list[tuple[int, int]]:
    """**派生层维护的小节**（回写附录）⇒ 其内容**不参与**条目识别与分段。

    ⚠️ **为什么需要**（2026-10-30 定级回写时暴露）：台账末尾要追加一节
    「阶段归属定级附录」，把 U5-O 的定级结论**同步回本域台账**（AGENTS.md §5.3b
    「产出必写回同名的本域子目录」）。但那一节**长得像台账**（编号 + 表格）⇒
      ① 其表格行会被当成**新条目** ⇒ 凭空多出条目（自噬）；
      ② 其内容会落进**前一个候选的段落**（段尾延伸到文件末尾）
         ⇒ `entry_stage_decl` 会从附录里读到**别的编号**的类
         ⇒ 把那一条的归属**改错**（比自噬更危险：它污染的是真实结论）。
    ⇒ 处置 = **保留区（keepout）**：该小节既不产生候选，也**截断前一段**。

    ⚠️ **标题必须带「附录」二字** —— 台账里本来就有「阶段归属」小节，
       那是**要被读的**（U5-f 的来源）⇒ 不能一刀切屏蔽。
    """
    heads = []
    for i, l in enumerate(lines):
        m = re.match(r"^\s{0,3}(#{2,5})\s+(.+?)\s*$", l)
        if m:
            heads.append((i, len(m.group(1)), m.group(2)))
    out: list[tuple[int, int]] = []
    for idx, (i, lvl, txt) in enumerate(heads):
        if not KEEP_OUT_RE.search(txt):
            continue
        end = len(lines)
        for j, lvl2, _ in heads[idx + 1:]:
            if lvl2 <= lvl:
                end = j
                break
        out.append((i, end))
    return out


def _in_keepout(line_no: int, keepouts: list[tuple[int, int]]) -> bool:
    return any(a <= line_no < b for a, b in keepouts)


def scan_ledgers(exclusions: dict[str, str]):
    """返回 (entries, line_index)。line_index: 仓内相对路径 -> [(start, end, id)]"""
    entries = []
    line_index: dict[str, list[tuple[int, int, str]]] = collections.defaultdict(list)
    docs = ROOT / "docs"
    for p in sorted(docs.rglob("*.md")):
        rel = p.relative_to(docs)
        if rel.parts[0] not in LEDGER_DIRS:
            continue
        if f"docs/{rel}" in SELF_ARTIFACTS:
            continue          # ⚠️ 自噬防护（见 SELF_ARTIFACTS 注释）
        if any(f"docs/{rel}".startswith(d) for d in SELF_ARTIFACT_DIRS):
            continue          # ⚠️ 自噬防护 · 目录级（U5-O 叠加层）
        dom = rel.parts[1] if len(rel.parts) > 1 else "_root"
        lines = p.read_text(encoding="utf-8", errors="ignore").split("\n")
        keepouts = compute_keepouts(lines)
        # 候选起点
        cands: list[tuple[int, str, str]] = []   # (行号0基, id, unit)
        heading_lines: list[int] = []
        for i, l in enumerate(lines):
            if _in_keepout(i, keepouts):
                continue          # ⚠️ 保留区（派生层维护的小节）⇒ 不产生候选
            m = re.match(r"^\s{0,3}(#{2,5})\s+(.+?)\s*$", l)
            if m:
                heading_lines.append(i)
                t = first_token(m.group(2))
                if not (t and ID_RE.match(t)):
                    # ⚠️ **标题里的编号不一定在首位** —— 台账大量写成
                    #    `## 2026-10-29 · G2 收工轮 · 🔴→✅ **E59 已修**：…`
                    #    ⇒ 首个 token 是日期（不是编号）⇒ 只认首 token 会**整条漏掉**。
                    # ⇒ 退化：扫标题全文取**第一个编号 token**。
                    hm = ID_TOKEN_RE.search(m.group(2))
                    if hm and ID_RE.match(hm.group(0)):
                        t = hm.group(0)
                if t and ID_RE.match(t):
                    cands.append((i, t, "heading"))
                continue
            mm = re.match(r"^\s*\|\s*\*{0,2}`?([A-Za-z][A-Za-z0-9_.\-]*)`?\*{0,2}\s*\|", l)
            if mm:
                t = first_token(mm.group(1))
                if t and ID_RE.match(t):
                    cands.append((i, t, "tablerow"))
        # 分段：**互不重叠**（每个候选的段到**下一个候选**为止）
        # ⚠️ **为什么不重叠**：重叠会让同一个 🔴 被「标题段」和「表格行段」**各数一次**
        #    ⇒ 条目数与状态分布被**重复计数**放大（实测会把 DRIFT 的 🔴 数虚增一倍）。
        #    ⇒ 代价 = 标题的正文本若夹着表格行会被截断 ⇒ 状态查找改为「**标题行优先**」。
        for k, (i, ident, unit) in enumerate(cands):
            nxtc = [c[0] for c in cands if c[0] > i]
            end = nxtc[0] if nxtc else len(lines)
            # ⚠️ **保留区截断** —— 派生层维护的小节（回写附录）既不产生候选，
            #    也**不许落进前一个条目的段落** ⇒ 否则 `entry_stage_decl` 会从附录里
            #    读到**别的编号**的类 ⇒ 把那一条的归属改错（比自噬更危险）。
            for a, _b in keepouts:
                if i < a < end:
                    end = a
                    break
            seg = "\n".join(lines[i:end])
            title = lines[i].strip()
            if unit == "tablerow":
                # ⚠️ **表格行的段 = 该行本身** —— 不能跨到下一候选。
                #   实测反例：`comment/vs-common.md:226` 的 `C-9` 行本身**没有状态标记**
                #   （「真源侧缺陷 · 记录，不修 RAG」），但它后面 20 行散文里有 🔴
                #   ⇒ 段跨过去 ⇒ 一条**已修**的条目被读成 🔴 未闭合 ⇒ **虚增真缺口**。
                seg = title
            line_index[str(rel)].append((i + 1, end, ident))
            excl = next((why for pre, why in exclusions.items() if ident.startswith(pre)), None)
            # ⚠️ **条目自己的「标题/首格」里也可能写着它的定级** ——
            #   实测（2026-10-30）：`### \`S1-FOLLOW-004\` 🟡 **B 类** · …`
            #   ⇒ 定级在**标题行**里 ⇒ 段内「阶段归属/定级」关键词扫不到
            #   ⇒ 域侧台账**已经写好的 B 类定级被派生层读成「未定级」**
            #   ⇒ 报成「真缺口」⇒ **假的**。
            #   ⇒ 读法：**标题型**读标题行；**表格行型**读**第 2 格**（表型各异，
            #     整行扫会把「引用别的条目/任务的类」误读成本条的类）。
            if unit == "heading":
                head_class = extract_class(title)
            else:
                _c = _cells(title)
                head_class = class_from_cell(_c[1]) if len(_c) > 1 else ""
            entries.append({
                "id": ident, "dir": rel.parts[0], "domain": dom,
                "file": str(rel), "line": i + 1, "unit": unit,
                "title": title[:200], "seg": seg, "excluded": excl,
                "head_class": head_class,
            })
    return entries, line_index


# ════════════════════════════════════════════════════════════════════════
# U3 · 活体探针台账
# ════════════════════════════════════════════════════════════════════════

def load_judge_closed() -> set[str]:
    """U2-2 · `docs/PhaseGoal-judge.md` 里**已闭合 / 已解除 / 已销案**的编号集合。

    ⚠️ **为什么 judge 优先**：`PhaseGoal-judge.md` §1.5 写明「**四层状态只认一个来源**」，
    它是阶段一终点的**唯一权威**。而域台账里**历史章节的标题仍带旧状态标记** ——
    实测 `docs/DRIFT/article/api.md:2692` 的历史小节标题仍写
    「🔴 **真正没闭的那一条**：双侧活体失败路径对拍 = 0 条（基线 §九 G-3）」
    而 judge §九 的 `G-3` **已于 2026-10-29 闭合** ⇒ 只读台账会把它读成未闭合
    ⇒ **虚增一条 A 类真缺口**。
    ⇒ 判据 = judge 的行文本里出现「已闭合 / 已解除 / 已销案」。
    """
    out: set[str] = set()
    judge = ROOT / "docs/PhaseGoal-judge.md"
    if not judge.exists():
        return out
    for line in judge.read_text(encoding="utf-8").split("\n"):
        if not line.lstrip().startswith("|"):
            continue
        cs = _cells(line)
        if not cs:
            continue
        row = " | ".join(cs)
        if not any(w in row for w in ("已闭合", "已解除", "已销案")):
            continue
        for tok in ID_TOKEN_RE.findall(cs[0]):
            out.add(tok)
    return out


def load_live_probes() -> dict[str, str]:
    """解析 `live-parity-2026-10-30.md` §二 的探针运行台账 ⇒ {探针路径: 退出码}"""
    out: dict[str, str] = {}
    if not LIVE_LEDGER.exists():
        return out
    in_sec = False
    for line in LIVE_LEDGER.read_text(encoding="utf-8").split("\n"):
        if line.startswith("## "):
            in_sec = line.startswith("## 二、")
            continue
        if not in_sec:
            continue
        m = re.match(r"^\|\s*`([^`]+)`\s*\|\s*\*{0,2}(\d+)\*{0,2}\s*\|", line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


# ════════════════════════════════════════════════════════════════════════
# U5/U8 · 施工单 + 未解决清单
# ════════════════════════════════════════════════════════════════════════

def _link_ids_by_pointer(text: str, line_index) -> set[str]:
    """任务块里的 `docs/DRIFT|Pitfalls|Intentional_Deviations/**` 指针 ⇒ 落在哪个条目段内"""
    ids: set[str] = set()
    for m in re.finditer(r"docs/(DRIFT|Pitfalls|Intentional_Deviations)/[A-Za-z0-9_./\-]+\.md:(\d+)", text):
        rel = m.group(0).split(":")[0]
        ln = int(m.group(2))
        for start, end, ident in line_index.get(rel, []):
            if start <= ln <= end:
                ids.add(ident)
                break
    return ids


def load_tasks(line_index) -> dict[str, dict]:
    """`_task-universe.md` ⇒ {任务ID: {class, ids, title}}

    ⚠️ **任务类必须读「阶段一影响」字段，不能读章节标题** ——
    §一 的标题写「**A 类 · 阻断阶段一（2 条）**」但**节内实放 11 条**任务
    （`P1-T01`~`P1-T05` ＋ `P2-T01`~`P2-T06`）⇒ 按章节归类会把
    **`P2-T02` / `P2-T06`（② 缺验收证据）**误判成 **A 类** ⇒
    **虚增阶段一阻断项**（⇒ 直接影响「阶段一能不能过」的结论）。
    ⇒ 逐条块读 `| **阶段一影响** | … |`；宽表读**同名列**。
    """
    text = TASK_UNIVERSE.read_text(encoding="utf-8")
    lines = text.split("\n")
    tasks: dict[str, dict] = {}
    # ── 逐条块（### P1-T01 · 标题） ──
    cur = None
    blocks: list[tuple[str, str, int, int]] = []
    for i, l in enumerate(lines):
        hm = re.match(r"^###\s+(P\d-T\d+)\s*·\s*(.+)$", l)
        if hm:
            if cur:
                blocks.append((cur[0], cur[1], cur[2], i))
            cur = (hm.group(1), hm.group(2), i)
            continue
        wm = re.match(r"^\|\s*\*{0,2}`?(P\d-T\d+)`?\*{0,2}\s*\|", l)
        if wm:
            if cur:
                blocks.append((cur[0], cur[1], cur[2], i))
                cur = None
            blocks.append((wm.group(1), "", i, i + 1))
    if cur:
        blocks.append((cur[0], cur[1], cur[2], len(lines)))

    for tid, title, s, e in blocks:
        seg = "\n".join(lines[s:e])
        cls = ""
        if title:
            im = re.search(r"\|\s*\*\*阶段一影响\*\*\s*\|\s*(.*?)\s*\|", seg)
            if im:
                cls = impact_to_class(im.group(1))
        ids = set(ID_TOKEN_RE.findall(seg))
        ids |= _link_ids_by_pointer(seg, line_index)
        tasks[tid] = {"id": tid, "title": title[:120], "class": cls,
                      "ids": ids, "seg": seg}

    # ── 宽表（§二~§五）：找表头里的「阶段一影响」列 ──
    hdr_idx = -1
    for i, l in enumerate(lines):
        cs = _cells(l)
        if cs and cs[0].strip().strip("*` ") == "计划 ID":
            for k, c in enumerate(cs):
                if "阶段一影响" in c:
                    hdr_idx = k
            break
    if hdr_idx >= 0:
        i = 0
        while i < len(lines):
            cs = _cells(lines[i])
            if cs and re.match(r"^P\d-T\d+$", cs[0].strip().strip("*` ")):
                tid = cs[0].strip().strip("*` ")
                if hdr_idx < len(cs):
                    c = impact_to_class(cs[hdr_idx])
                    if tid in tasks:
                        if not tasks[tid]["class"]:
                            tasks[tid]["class"] = c
                        tasks[tid]["seg"] += "\n" + lines[i]
                        tasks[tid]["ids"] |= set(ID_TOKEN_RE.findall(lines[i]))
            i += 1
    return tasks


# 「阶段一影响」取值 ⇒ 阶段归属（`twoStage.md` §10）
def impact_to_class(text: str) -> str:
    """`_task-universe.md` 的「阶段一影响」⇒ A/B/C/D。

    口径（§〇 表里写明的 6 档）⇒ 映射：
      ① 阻断阶段一      ⇒ **A**（阶段 1 准入阻断项）
      ② 缺验收证据      ⇒ **B**（阶段 1 竞争力补强项）
      ③ 环境受阻        ⇒ **—**（不是缺陷，是环境）
      ④ 允许暂缓        ⇒ **C**（阶段 2 最终工程建设项）
      ⑤ 仅为执行约束    ⇒ **B**
      ⑥ 不属于阶段一    ⇒ **D**
    """
    t = text.strip()
    for mark, cls in (("①", "A"), ("②", "B"), ("③", "—"), ("④", "C"), ("⑤", "B"), ("⑥", "D")):
        if t.startswith(mark):
            return cls
    if "阻断阶段一" in t or "阻断阶段一" in t:
        return "A"
    if "缺验收证据" in t:
        return "B"
    if "环境受阻" in t:
        return "—"
    if "允许暂缓" in t:
        return "C"
    if "执行约束" in t:
        return "B"
    if "不属于阶段一" in t:
        return "D"
    return ""


def load_unresolved(line_index) -> dict[str, dict]:
    """`_unresolved.md` ⇒ {项ID: {class, ids}}"""
    text = UNRESOLVED.read_text(encoding="utf-8")
    lines = text.split("\n")
    cls = ""
    out: dict[str, dict] = {}
    cur = None
    sec_map = {"1. 阶段一阻断项": "A", "2. 证据缺口": "B", "3. 环境或权限受阻项": "—", "4. 等待用户裁定的事项": "—"}
    for i, l in enumerate(lines):
        if l.startswith("## "):
            cls = ""
            for k, v in sec_map.items():
                if k in l:
                    cls = v
        m = re.match(r"^\|\s*\*{0,2}`?(BLK-\d+|GAP-\d+|ENV-\d+|DEC-\d+)`?\*{0,2}\s*\|", l)
        if m:
            out[m.group(1)] = {"id": m.group(1), "class": cls, "seg": l}
    for k, v in out.items():
        v["ids"] = set(ID_TOKEN_RE.findall(v["seg"]))
        v["ids"] |= _link_ids_by_pointer(v["seg"], line_index)
    return out


# ════════════════════════════════════════════════════════════════════════
# U5 · 权威定级表
# ════════════════════════════════════════════════════════════════════════

def load_shared_grading() -> dict[str, dict]:
    """`alignment-2026-10-23.md` §十三·一 的 25 行定级表 ⇒ {编号: {stage, blocking, evidence}}"""
    out: dict[str, dict] = {}
    if not ALIGNMENT.exists():
        return out
    lines = ALIGNMENT.read_text(encoding="utf-8").split("\n")
    in_sec = False
    for l in lines:
        if l.startswith("### 十三·一"):
            in_sec = True
            continue
        if in_sec and l.startswith("### ") and not l.startswith("### 十三·一"):
            in_sec = False
        if not in_sec:
            continue
        if not l.startswith("|"):
            continue
        cells = [c.strip() for c in l.strip().strip("|").split("|")]
        if len(cells) < 11:
            continue
        ident = cells[0].strip("*` ")
        if not re.match(r"^(CF|LOG|SF)-\d+$", ident):
            continue
        blocking = cells[7].strip()
        stage = cells[8].strip()
        evidence = cells[10].strip()
        st = ""
        m = re.match(r"^\*{0,2}([ABCD])\*{0,2}", stage)
        if m:
            st = m.group(1)
        blk = "是" if blocking.startswith("**是") or blocking.startswith("是") else "否"
        out[ident] = {"stage": st, "blocking": blk, "evidence": evidence[:40],
                      "src": "docs/DRIFT/common/alignment-2026-10-23.md §十三·一"}
    return out


def load_grading_overlay() -> dict[str, dict]:
    """U5-O · **显式定级叠加层** ⇒ {编号: {stage, prio, blocking, basis, date, src}}

    ⚠️ **为什么需要这一层（而不是把定级写回各域台账）**：
      台账里绝大多数条目是**表格行型**（实测 129 条待定级里 **71 条是表格行**），
      而表格行的「段」在扫描器里 = **该行本身**（见 `scan_ledgers` 注释）⇒
      想给它挂定级，只有三条路：
        ① 把定级**塞进单元格** ⇒ 但表格通常**已有「阶段归属」列** ⇒
           U5-0（表格定级）**优先于** U5-f（条目正文声明）⇒ **塞了也不生效**；
        ② 改**表格结构**（加一列）⇒ 破坏原台账、且各域表型不一 ⇒ 不可取；
        ③ **另立叠加层** ⇒ 不动原表、零破坏、可逐条回源。⇒ **选 ③**。
    ⚠️ **叠加层的效力 = 人工本轮显式定级** ⇒ 优先级**最高**（高于 U5-0 的台账表格），
      理由：台账里的「阶段归属」列是**历史**判定；本轮是**逐条与 Go 源码对照**后的重判
      （用户 2026-10-30 拍板：「逐个和 golang 版本的对比」）⇒ 新的应压过旧的。
    ⚠️ **格式**：Markdown 表格，列 = 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期。
      「依据」列必须写 **Go 侧 `文件:行号` + RAG 侧 `文件:行号` + 结论**
      ⇒ 没有双侧行号的行**视为无效**（否则「定级」又变成无出处的断言）。
    """
    out: dict[str, dict] = {}
    if not GRADING_DIR.exists():
        return out
    for p in sorted(GRADING_DIR.glob("*.md")):
        if p.name.startswith("_"):
            continue          # ⚠️ `_` 前缀 = 说明文件（格式规范/示例）⇒ **不是数据**
        rel = p.relative_to(ROOT)
        for ln, line in enumerate(p.read_text(encoding="utf-8").split("\n"), 1):
            if not line.lstrip().startswith("|"):
                continue
            cs = _cells(line)
            if len(cs) < 5:
                continue
            ident = cs[0].strip().strip("*` ").strip()
            if not ident or ident in ("编号", "---"):
                continue
            if ident.startswith("-"):        # 分隔行
                continue
            stage = class_from_cell(cs[1])
            if stage not in ("A", "B", "C", "D"):
                continue                      # ⚠️ 无效归属 ⇒ 整行丢弃（不猜）
            prio = cs[2].strip().strip("*` ").split()[0] if cs[2].strip() else ""
            if prio not in ("P0", "P1", "P2", "P3"):
                prio = ""
            blk = cs[3].strip().strip("*` ")
            if blk.startswith("不") or "不阻断" in blk:
                blocking = "否"
            elif blk.startswith("否"):
                # ⚠️ **裸「否」也要显式解析** —— 否则 `blocking` 落空 ⇒ 靠
                #   「A 类 ⇒ 是 / 其余 ⇒ 否」的回落规则才得出同样的值
                #   ⇒ **正确结果来自兜底而非判据**（脆弱：回落规则一改就变）。
                blocking = "否"
            elif blk.startswith("是"):
                blocking = "是"
            else:
                blocking = ""
            basis = cs[4].strip()
            date = cs[5].strip() if len(cs) > 5 else ""
            # ⚠️ **依据必须含双侧 `文件:行号`** —— 否则「定级」是无出处的断言
            #   ⚠️ **Go 侧必须是 `.go`；RAG 侧不限扩展名** ——
            #      实测（2026-10-30 W4 轮）：RAG 侧指针常指向
            #      `application.yml:53` / `AutoConfiguration.imports:4` /
            #      `docker-compose.yaml:15` / `README.md:89` ⇒ 若限定 `.java`
            #      ⇒ **6 条定级被整行丢弃**（数字看着正常，内容全错）。
            #      ⇒ RAG 侧用「任意扩展名」正则，但**必须排除 `.go`**
            #      （否则 Go 侧行号会被误当成 RAG 侧 ⇒ 两侧实际只写了一边）。
            has_go = re.search(r"[\w./\-]+\.go:\d+", basis)
            has_rag = re.search(r"[\w./\-]+\.(?!go\b)[A-Za-z]{1,10}:\d+", basis)
            if not (has_go and has_rag):
                continue
            # ⚠️ **结论三态单独抽字段** —— **不许靠全文搜**。
            #   实测坑（2026-10-30）：依据列原被截断到 400 字符，而结论词在**末尾**
            #   ⇒ 71 条定级里只有 9 条被判成「差异已消失」⇒ **静默漏判 22+ 条**。
            #   ⇒ 判据 = 依据里的**唯一**三态词（实测 71/71 条都恰好命中一个）。
            verdict = ""
            _hits = [(t, basis.rfind(t)) for t in ("差异仍成立", "差异已消失", "无法证实")
                     if t in basis]
            if _hits:
                verdict = max(_hits, key=lambda x: x[1])[0]
            rec = {"stage": stage, "prio": prio, "blocking": blocking,
                   "basis": basis[:2000], "verdict": verdict, "date": date,
                   "src": f"{rel}:{ln}"}
            old = out.get(ident)
            if old is None or (rec["date"], rec["src"]) > (old["date"], old["src"]):
                out[ident] = rec
    return out


def load_stage1_05() -> dict[str, dict]:
    """`Stage1/05` §一（域级）与 §三（本轮新增）⇒ {编号: {stage, blocking}}"""
    out: dict[str, dict] = {}
    if not STAGE1_05.exists():
        return out
    lines = STAGE1_05.read_text(encoding="utf-8").split("\n")
    i = 0
    while i < len(lines):
        l = lines[i]
        m = re.match(r"^#{3,4}\s+`?([A-Z][A-Za-z0-9-]*-\d+)`?\s*(.*)$", l)
        if m:
            ident = m.group(1)
            rest = m.group(2)
            stage = ""
            sm = re.search(r"([ABCD])\s*类", rest)
            if sm:
                stage = sm.group(1)
            blocking = ""
            # 往后找字段 8
            for j in range(i, min(i + 40, len(lines))):
                bm = re.match(r"^\|\s*8\s*\|\s*(.*)$", lines[j])
                if bm:
                    blocking = "是" if bm.group(1).strip().startswith("**是") or bm.group(1).strip().startswith("是") else "否"
                    break
            if ident not in out:
                out[ident] = {"stage": stage, "blocking": blocking,
                              "src": f"docs/Stage1/05-差异与暂缓项清单.md:{i+1}"}
        i += 1
    return out


# ════════════════════════════════════════════════════════════════════════
# U10 · **台账里已经存在的「施工单行」** —— §10 十一项表 + 「阶段归属」列表
# ════════════════════════════════════════════════════════════════════════
#
# 为什么这是**最高价值**的一路抽取：
#   `twoStage.md` §10 要求的 11 个字段，**台账里已经有一批条目用规范格式写满了**
#   （`| 1 | 唯一编号 |` … `| 11 | 当前处理状态 |`）⇒ 那**就是**施工单行，只是
#   **散落在各域台账里、没有索引** ⇒ 本派生层把它们**抽出来、编上号、指到源位置**。
#   ⇒ 它同时解决了两件事：
#     ① 「阶段归属 A-D」的覆盖率（从 10% 量级提到实际水平）
#     ② 「阶段 2 恢复方案 / 阶段 2 测试场景」这两个此前**只有 2% 覆盖**的字段
#
# 两种形态：
#   **Q1 · 十一项表** = 行首是序号 1~11、第二格是规范字段名的表
#   **Q2 · 阶段归属列表** = 表头含「阶段归属」列（或独立的「归属」列）的表

# §10 的 11 个字段名（`twoStage.md:941-975`）—— **归一化后按前缀匹配**
F10N = ["唯一编号", "问题类型和优先级", "阶段归属", "涉及文件与实际证据", "触发场景",
        "当前行为与预期行为", "对简历展示准入的影响", "对最终工程目标的影响",
        "修复方向", "验收条件和测试方案", "当前处理状态"]
# §5.3 的 11 个字段名（`twoStage.md:359-379`）
F53N = ["唯一编号", "原始项目中的文件", "原始行为", "阶段1当前行为", "具体差异",
        "暂缓", "对业务正确性", "是否阻断阶段1", "阶段2的恢复或替换方案",
        "阶段2的测试场景", "当前证据及剩余证据缺口"]

CLASS_RE = re.compile(r"([ABCD])\s*类")
PRIO_RE = re.compile(r"\b(P[0-3])\b")
NEG_WORDS = ("不是", "非", "不属于", "没有", "无需", "无")


def norm_field(name: str) -> str:
    return re.sub(r"[\s*/、，,·:：]+", "", name.strip().strip("*` ").replace("**", ""))


def field_kind(name: str) -> str:
    """字段名 → 属于 §10 表（F10）还是 §5.3 表（F53）；不认识的返回 ""。

    ⚠️ **必须按「归一化后的前缀」判**，不能精确等值 —— 台账里同一字段名有多种写法
    （`原始项目中的文件、方法或业务契约` vs `原始项目中的文件 / 方法 / 业务契约`）。
    """
    n = norm_field(name)
    for pref, kind in _FIELD_TABLE:
        if n.startswith(pref):
            return kind
    return ""


# (归一化前缀, 表型) —— **顺序敏感**（长前缀在前）
_FIELD_TABLE = [
    ("原始项目中的文件", "F53"), ("阶段1当前行为", "F53"), ("阶段2的恢复或替换方案", "F53"),
    ("阶段2的测试场景", "F53"), ("当前证据及剩余证据缺口", "F53"), ("是否阻断阶段1", "F53"),
    ("对业务正确性", "F53"), ("暂缓", "F53"), ("原始行为", "F53"), ("具体差异", "F53"),
    ("阻断阶段一", "F53"),
    ("问题类型和优先级", "F10"), ("阶段归属", "F10"), ("涉及文件与实际证据", "F10"),
    ("触发场景", "F10"), ("当前行为与预期行为", "F10"), ("对简历展示准入的影响", "F10"),
    ("对最终工程目标的影响", "F10"), ("修复方向", "F10"), ("验收条件和测试方案", "F10"),
    ("当前处理状态", "F10"), ("唯一编号", "F10"),
]


def extract_class(text: str) -> str:
    """从一段文本里取**归属类**（A/B/C/D）—— **排除否定式**（「不是 A 类」）。

    ⚠️ **否定式必须排除**：台账里大量写「… ⇒ **不是 A 类** ⇒ … ⇒ **D 类**」，
    若按「第一个出现的类」取，会把一条 **D 类**读成 **A 类** ⇒ **直接导致阶段一误判**。
    ⇒ 取**第一个没有被否定词修饰**的类。
    """
    best = None
    for m in CLASS_RE.finditer(text):
        pre = text[max(0, m.start() - 6):m.start()]
        if any(w in pre for w in NEG_WORDS):
            continue
        if best is None or m.start() < best[0]:
            best = (m.start(), m.group(1))
    return best[1] if best else ""


def class_from_cell(cell: str) -> str:
    """「归属」列的取值 ⇒ A/B/C/D。

    ⚠️ **列里的写法常常不带「类」字** —— 如 `**A**（阶段一准入阻断项 ⇒ 已处理）`
    ⇒ 只按 `X 类` 匹配会**整行漏掉**（实测 `SF-001` / `LOG-003` / `DRIFT-CF-004`
    三条都因此没进定级表 ⇒ **它们的状态也就没被定级表覆盖**）。
    ⇒ 列内**先按「首字符是 A/B/C/D」取**（带否定词保护），再退回 `extract_class`。
    """
    c = cell.strip().lstrip("*` ")
    m = re.match(r"^([ABCD])(?![A-Za-z0-9])", c)
    if m:
        pre = cell.strip().lstrip("*` ")[:max(0, m.start() - 6)]
        if not any(w in pre for w in NEG_WORDS):
            return m.group(1)
    return extract_class(cell)


def _cells(line: str) -> list[str]:
    s = line.strip()
    if not s.startswith("|"):
        return []
    return [c.strip() for c in s.strip("|").split("|")]


def scan_grading_tables():
    """扫全仓台账 ⇒ {编号: {stage, prio, final_goal, blocking, evidence, source, kind}}

    **两种表形态**：
      **Q1 · 十一项表** —— 行首序号 1~11、第二格是 §10 或 §5.3 的规范字段名
      **Q2 · 阶段归属列表** —— 表头含「阶段归属」列（或独立的「归属」列）
    ⚠️ **§10 与 §5.3 的字段位置不同**：
      §10 ⇒ 字段 3 = 阶段归属 · 字段 8 = 对最终工程目标的影响
      §5.3 ⇒ 字段 8 = 是否阻断阶段 1 · 字段 9/10 = 阶段 2 恢复方案 / 测试场景
      ⇒ **不能拿同一套字段号去读两张表**（否则把「是否阻断」读成「对最终目标的影响」）。
    """
    out: dict[str, dict] = {}
    docs = ROOT / "docs"
    roots = ["DRIFT", "Pitfalls", "Intentional_Deviations", "Stage1"]
    for p in sorted(docs.rglob("*.md")):
        rel = p.relative_to(docs)
        if rel.parts[0] not in roots:
            continue
        if f"docs/{rel}" in SELF_ARTIFACTS:
            continue          # ⚠️ 自噬防护（**两个扫描器都要挡** —— 见 SELF_ARTIFACTS 注释）
        if any(f"docs/{rel}".startswith(d) for d in SELF_ARTIFACT_DIRS):
            continue          # ⚠️ 自噬防护 · 目录级（**两个扫描器都要挡**）
        lines = p.read_text(encoding="utf-8", errors="ignore").split("\n")
        keepouts = compute_keepouts(lines)
        heading = ""
        i = 0
        while i < len(lines):
            if _in_keepout(i, keepouts):
                i += 1
                continue          # ⚠️ 保留区（派生层维护的小节）⇒ 不读它的表格
            hm = re.match(r"^\s{0,3}#{2,5}\s+(.+?)\s*$", lines[i])
            if hm:
                heading = hm.group(1)
                i += 1
                continue
            if lines[i].lstrip().startswith("|"):
                j = i
                tbl = []
                while j < len(lines) and lines[j].lstrip().startswith("|"):
                    tbl.append((j + 1, lines[j]))
                    j += 1
                _consume_q1(tbl, rel, heading, out)
                _consume_q2(tbl, rel, heading, out)
                i = j
                continue
            i += 1
    return out


def _consume_q1(tbl, rel, heading, out):
    """Q1 · 十一项表

    ⚠️ **按「字段名」判，不按「表型」判** —— 台账里存在**混型表**：
    字段 1~4 用 §10 的名字、字段 8~11 用 §5.3 的名字（各域台账自己拼的）。
    ⇒ 若按「整张表是 §10 还是 §5.3」二选一 ⇒ 会把「是否阻断阶段 1」读成
       「对最终工程目标的影响」⇒ **字段语义错位**。
    ⇒ 每个字段**各自**按名字归位。
    """
    fields: dict[int, tuple[int, str, str]] = {}
    for ln, line in tbl:
        cs = _cells(line)
        if len(cs) < 3:
            continue
        m = re.match(r"^(\d{1,2})$", cs[0].strip().strip("*` "))
        if not m:
            continue
        n = int(m.group(1))
        fk = field_kind(cs[1])
        if not fk:
            continue
        if n not in fields:
            fields[n] = (ln, cs[2], fk)
    if len(fields) < 4 or 1 not in fields:
        return
    idln, idtext, _ = fields[1]
    ids = ID_TOKEN_RE.findall(idtext)
    if not ids:
        return
    ident = ids[0]
    stage = ""
    prio = ""
    final_goal = ""
    blocking = ""
    evidence = ""
    status_hint = ""
    s2_restore = ""
    s2_test = ""
    for n, (_ln, val, _k) in fields.items():
        if n == 2:
            pm = PRIO_RE.search(val)
            if pm:
                prio = pm.group(1)
        elif n == 3 and not stage:
            stage = extract_class(val)
        elif n == 4 and not evidence:
            evidence = val[:120]
        elif n == 8:
            if "最终工程目标" in val or "最终工程" in val:
                final_goal = val[:120]
            else:
                blocking = "是" if (val.strip().startswith("**是") or val.strip().startswith("是")) else "否"
                if "已修" in val or "已闭合" in val or "已关闭" in val:
                    status_hint = "已修"
                elif "未修" in val or "仍开放" in val:
                    status_hint = "未修"
        elif n == 9:
            s2_restore = val[:110]
        elif n == 10:
            s2_test = val[:110]
        elif n == 11 and not evidence:
            evidence = val[:120]
    if not final_goal and (s2_restore or s2_test):
        final_goal = (s2_restore + " ｜ " + s2_test).strip(" ｜")
    if not stage:
        stage = extract_class(heading)
    prev = out.get(ident)
    rec = {"stage": stage, "prio": prio, "final_goal": final_goal,
           "blocking": blocking, "evidence": evidence, "status_hint": status_hint,
           "source": f"{rel}:{idln}", "kind": "十一项表"}
    if prev is None:
        out[ident] = rec
    else:
        for k in rec:
            if not prev.get(k) and rec[k]:
                prev[k] = rec[k]
        prev["source"] = prev["source"] + " ｜ " + rec["source"]


def _consume_q2(tbl, rel, heading, out):
    """Q2 · 「阶段归属」列表：表头含该列"""
    header = None
    for ln, line in tbl:
        cs = _cells(line)
        if not cs:
            continue
        joined = " ".join(cs)
        if "阶段归属" in joined or any(c.strip().strip("*` ") in ("归属", "阻断阶段一", "是否阻断") for c in cs):
            header = (ln, cs)
            break
    if not header:
        return
    hcells = header[1]
    idx_stage = idx_blk = idx_prio = -1
    for i, c in enumerate(hcells):
        cc = c.strip().strip("*` ").replace("**", "").strip()
        if cc in ("阶段归属", "归属"):
            idx_stage = i
        elif cc.startswith("阻断阶段一") or cc.startswith("是否阻断") or cc == "阻断":
            idx_blk = i
        elif cc in ("优先级", "严重度"):
            idx_prio = i
    if idx_stage < 0:
        return
    for ln, line in tbl:
        if ln <= header[0]:
            continue
        cs = _cells(line)
        if len(cs) <= idx_stage:
            continue
        row = " | ".join(cs)
        if set("".join(cs).strip()) <= set("-: "):
            continue
        ids = ID_TOKEN_RE.findall(row)
        if not ids:
            continue
        ident = ids[0]
        stage = class_from_cell(cs[idx_stage])
        if not stage:
            continue
        prio = ""
        if idx_prio >= 0 and idx_prio < len(cs):
            pm = PRIO_RE.search(cs[idx_prio])
            prio = pm.group(1) if pm else ""
        blocking = ""
        status_hint = ""
        if idx_blk >= 0 and idx_blk < len(cs):
            v = cs[idx_blk].strip()
            blocking = "否" if (v.startswith("否") or v.startswith("❌") or "不阻断" in v) else "是"
            if "已修" in v or "已闭合" in v or "已关闭" in v:
                status_hint = "已修"
            elif "未修" in v or "仍开放" in v:
                status_hint = "未修"
        prev = out.get(ident)
        rec = {"stage": stage, "prio": prio, "final_goal": "", "blocking": blocking,
               "evidence": "", "status_hint": status_hint, "source": f"{rel}:{ln}", "kind": "阶段归属列表"}
        if prev is None:
            out[ident] = rec
        elif not prev.get("stage") or (prev.get("kind") == "阶段归属列表" and not prev.get("blocking")):
            prev.update({k: v for k, v in rec.items() if v or not prev.get(k)})


# ════════════════════════════════════════════════════════════════════════
# 派生
# ════════════════════════════════════════════════════════════════════════

DATE_RE = re.compile(r"(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})")
# 「现行态 = X」—— 2026-10-30 真实性复核轮**逐条追加**的显式现行态声明
CURRENT_RE = re.compile(r"现行态\s*=\s*[*`]*\s*([^，。；、（()]{1,12})")
# 迁移记号：`🔴→✅` / `🟡 → ✅` / `🔴->✅` ⇒ 终态取**右边**
TRANSIT_RE = re.compile(r"(✅|🟡|🔴|⚫)\s*(?:→|->|➔|=>)\s*(✅|🟡|🔴|⚫)")


def normalize_status_token(tok: str, dirname: str) -> str:
    """把「现行态 = X」的 X 归一到本目录的状态取值域。"""
    t = tok.strip().strip("*` ")
    if not t or t.startswith("上列"):
        return ""
    if t.startswith("✅"):
        return "✅ 仍成立" if dirname == "Intentional_Deviations" else "✅ 已一致"
    if t.startswith("🟡"):
        return "🟡 部分闭合"
    if t.startswith("🔴"):
        return "🔴 未闭合"
    if t.startswith("⚫"):
        return "⚫ 已签字偏离"
    if t.startswith("仍未修") or t.startswith("未修") or t.startswith("仍开放"):
        return "🔴 未闭合"
    if t.startswith("仍成立"):
        return "✅ 仍成立" if dirname == "Intentional_Deviations" else "✅ 已一致"
    if t.startswith("仍生效"):
        return "仍生效"
    if t.startswith("已闭环") or t.startswith("已关闭") or t.startswith("已修"):
        return "已闭环"
    if t.startswith("已撤销"):
        return "已撤销"
    if t.startswith("已失效"):
        return "已失效"
    if t.startswith("未签字"):
        return "⏳ 未签字"
    if t.startswith("待核实"):
        return "待核实"
    return ""


def entry_date(entry) -> str:
    """U2-b · 条目的**最新核对日期**（ISO）—— 同一编号多态时用它选「最新状态」。

    ⚠️ **必须按日期取最新，不能按严重度取最严重**（`PhaseGoal-judge.md` §1.5 L0）。
    找不到日期 ⇒ 返回空串（⇒ 退化为「文件顺序取最后一条」）。
    """
    best = ""
    for m in DATE_RE.finditer(entry["seg"]):
        y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if not (1 <= mo <= 12 and 1 <= d <= 31):
            continue
        iso = f"{y:04d}-{mo:02d}-{d:02d}"
        if iso > best:
            best = iso
    return best


def detect_status(entry) -> str | None:
    """U2 · 状态归一 —— **按「现行态」显式声明优先**，其次**按出现位置取最早**的标记。

    ⚠️ **为什么「现行态 = X」优先**：2026-10-30 真实性复核轮在**每条**后面追加了
    「⚠️ 2026-10-30 真实性复核：原判 X ⇒ **现行态 = Y**」——
    而**行内的状态列常常是更早的快照** ⇒ 只看状态列会把「已改判」的条目读成旧状态。
    ⇒ 实测反例：`SF-001` 的定级行里写着「🔴 **修复前**：`Snowflake.java:242` …」
      ⇒ 按标记扫会读成 🔴 未闭合，而它**早已修好**（阻断列自己写着「是 ⇒ 已修」）。

    ⚠️ **为什么按位置**：标题形如 `DRIFT-X-001 —— 修前 🔴，修后 ✅` 时，
    「按取值域字典序取第一个」会命中 ✅ ⇒ **把一条已修的条目读成已一致**。
    ⇒ 取**出现位置最早**的标记；同位置时取更长的键（`已失效` vs `已闭环`）。
    """
    marks = STATUS_SETS[entry["dir"]]

    # ⓪ **迁移记号** `🔴→✅` / `🟡 → ✅` —— 终态是**右边**那个
    #    ⚠️ 台账里极常见：`🔴→✅ **销案**`（E60 · 2026-10-29）
    #    ⇒ 若按「取最早出现的标记」会取到 🔴 ⇒ **把已销案的条目读成未闭合**
    #      ⇒ **虚增真缺口**。⇒ 必须先看迁移记号的**终态**。
    for text in (entry["title"], entry["seg"][:400]):
        m = TRANSIT_RE.search(text)
        if m:
            st = normalize_status_token(m.group(2), entry["dir"])
            if st:
                return st

    # ① 显式「现行态 = X」
    for m in CURRENT_RE.finditer(entry["seg"]):
        st = normalize_status_token(m.group(1), entry["dir"])
        if st:
            return st

    def earliest(text: str):
        best = None
        for k in marks:
            pos = text.find(k)
            if pos < 0:
                continue
            if best is None or pos < best[0] or (pos == best[0] and len(k) > len(best[1])):
                best = (pos, k)
        return best[1] if best else None

    k = earliest(entry["title"])
    if k:
        return marks[k]
    k = earliest(entry["seg"])
    if k:
        return marks[k]
    return None


PRIO_DECL_RE = re.compile(r"优先级\s*\**\s*(P[0-3])")


def entry_prio_decl(entry) -> str:
    """U4-1 · 条目自己正文里的优先级声明（如「优先级 **P2**」）⇒ P0-P3"""
    m = PRIO_DECL_RE.search(entry["seg"][:1200])
    return m.group(1) if m else ""


def entry_stage_decl(entry) -> tuple[str, str]:
    """U5-f · 条目**自己段落里**的归属声明 ⇒ (stage, 依据)

    ⚠️ **为什么必须读条目自己的段落**：台账里大量条目在**自己的**正文里写着
    「**阶段归属（`twoStage.md` §10）**：**A 类**（P0 #8）…」
    「**定级**：**C 类（阶段 2 最终工程建设项）** —— 理由：…」
    ⇒ 那才是**这一条**的归属。
    ⚠️ **为什么不能用「任务类」代替**：施工单任务 `P1-T01`（A 类 · 全服务起栈）
    在「关联项」里提到 `DRIFT-HOTAPI-030` ⇒ **任务类 ≠ 条目归属**
    ⇒ 拿任务类去填条目归属 = **范畴错误** ⇒ 会把一条 C 类差异虚报成「A 类阻断」。
    ⇒ 只在**含「阶段归属 / 定级」关键词**的行里取类（避开「A 类 backlog」这类随口提及）。
    """
    for line in entry["seg"].split("\n"):
        if "阶段归属" not in line and "定级" not in line:
            continue
        st = extract_class(line)
        if st:
            return st, line.strip()[:90]
    # ⚠️ **兜底：条目自己的标题 / 表格首格**（域侧台账常把定级直接写在标题里）
    st = entry.get("head_class", "")
    if st:
        return st, entry["title"][:90]
    return "", ""


def entry_blocking_decl(entry) -> str:
    """U5-g · 条目自己正文里的「阻断阶段 1」声明 ⇒ 是 / 否

    ⚠️ **先把 `*` 与空格剥掉再判** —— 台账里常写成 `**不阻断**阶段一`
    （粗体只包住「不阻断」三个字）⇒ 直接子串匹配会**漏判** ⇒ 阻断标志回落到
    「A 类 ⇒ 是」⇒ **把一条明确不阻断的条目报成阻断**。
    """
    seg = re.sub(r"[*`\s]", "", entry["seg"])
    if ("不阻断阶段1" in seg or "不阻断阶段一" in seg or "不计入阶段一" in seg
            or "不阻断阶段1准入" in seg):
        return "否"
    if ("阻断阶段1" in seg or "阻断阶段一" in seg):
        return "是"
    return ""


def status_authority(entry) -> int:
    """U2-c · 该条登记的「状态权威度」—— 同日期时用它挑更权威的那条。

    3 = 有「现行态 = X」显式声明（最权威）
    2 = 有迁移记号 `🔴→✅`（**终态**才是当前状态）
    1 = 只有状态列/标记
    ⚠️ **为什么需要**：同一日期的两次登记里，**旧章节**可能仍写着 🔴、
    **新章节**写着「🔴→✅ 销案」⇒ 只比日期会**取到旧那条** ⇒ 虚增真缺口。
    """
    if CURRENT_RE.search(entry["seg"]):
        return 3
    if TRANSIT_RE.search(entry["title"]) or TRANSIT_RE.search(entry["seg"][:400]):
        return 2
    return 1


def detect_evidence(entry, live_probes: dict[str, str], all_tools: set[str]) -> tuple[str, str]:
    """U3 ⇒ (档位, 依据)"""
    refs = set()
    for m in TOOLREF_RE.finditer(entry["seg"]):
        ref = m.group(0).rstrip(".,;:`）)】]、")
        if ref in all_tools:
            refs.add(ref)
    hit_live = sorted(r for r in refs if r in live_probes)
    if hit_live:
        # ⚠️ **A 档必须带上探针的真实退出码** —— 「有活体证据」≠「结论被证实」：
        #    实测：A 档引用的探针里 **23 条指向 EXIT=2（环境问题 ⇒ 结论不可用）**
        #    **12 + 11 条指向 EXIT=1（不一致）** ⇒ 只写「A 活体」会让人以为这条已被证实。
        #    ⇒ 值形如 `A 活体 · 红(1)` / `A 活体 · 环境(2)` / `A 活体 · 绿(0)` / `A 活体 · 混(0/1/2)`。
        codes = sorted({live_probes[r] for r in hit_live})
        if len(codes) == 1:
            verdict = {"0": "绿", "1": "红", "2": "环境", "4": "码4"}.get(codes[0], codes[0])
            tag = f"{verdict}({codes[0]})"
        else:
            tag = "混(" + "/".join(codes) + ")"
        return f"A 活体 · {tag}", "探针 " + ",".join(hit_live[:2]) + ("…" if len(hit_live) > 2 else "")
    nptr = len(PTR_RE.findall(entry["seg"]))
    if nptr:
        return "B 静态", f"{nptr} 个可复算指针"
    if refs:
        return "B 静态", "工具引用 " + ",".join(sorted(refs)[:2])
    return "C 无指针", "无可复算指针"


def derive(entries, live_probes, tasks, unresolved, shared_grading, stage1_05, all_tools, grading,
           judge_closed=frozenset(), overlay=None):
    # 任务 → 条目 反查表
    task_of: dict[str, list[str]] = collections.defaultdict(list)
    for tid, t in tasks.items():
        for ident in t["ids"]:
            task_of[ident].append(tid)
    unres_of: dict[str, list[str]] = collections.defaultdict(list)
    for uid, u in unresolved.items():
        for ident in u["ids"]:
            unres_of[ident].append(uid)

    rows = []
    for e in entries:
        ident = e["id"]
        status = detect_status(e)
        # ⚠️ **只派生「问题项」** —— 无状态标记的**明细子行**（分类明细 / 测试矩阵的补充列）
        #    与**排除清单**命中的族（`CASE-*`）**都不进派生表**
        #    ⇒ 否则「A 组/B 组」这类分类明细会冒充「问题项」，把施工单撑成 1000+ 行。
        if status is None or e["excluded"]:
            continue
        evid, evid_src = detect_evidence(e, live_probes, all_tools)
        rules = ["U1", "U2", "U3"]
        # U2-2 · judge 判「已闭合」⇒ 覆盖台账历史章节的旧标记
        if ident in judge_closed and status in ("🔴 未闭合", "🟡 部分闭合", "仍生效", "待核实"):
            status = "✅ 已一致"
            rules.append("U2-2")
        _date = entry_date(e)
        _auth = status_authority(e)
        stage = ""
        blocking = ""
        stage_src = ""
        explicit_prio = ""
        explicit_final = ""
        explicit_evidence = ""
        # ⚠️ **U5-O · 显式定级叠加层 = 最高优先级**（人工本轮逐条 Go 对照后的重判）
        #   ⇒ 压过 U5-0（台账表格里的历史「阶段归属」列）—— 台账那列是**历史**判定，
        #     本轮是**回源核对**后的判定 ⇒ 新的应压过旧的。
        #   ⇒ 但 **status 不改** —— 定级不改变事实状态（🔴 仍是 🔴，除非有人真的修）。
        ov = overlay.get(ident)
        ov_prio = ""
        closed = False
        if ov:
            stage = ov["stage"]
            blocking = ov["blocking"]
            stage_src = f"{ov['src']}（U5-O 显式定级 · {ov['date'] or '无日期'}）"
            ov_prio = ov["prio"]
            rules.append("U5-O")
            # ⚠️ **「差异已消失」= 已闭合 · 无待实现项** ⇒ 它**不属于任何阶段**。
            #   实测（W1 轮）：16 条里 15 条是「台账还写 🔴 未闭合，但逐条回源后差异已不存在」。
            #   ⇒ 若把它们按 D 类塞进「阶段 2 承接」清单 ⇒ **阶段二工作量被虚增**
            #   ⇒ 那份清单会变成**假的待办**（同族教训：口径错位 ⇒ 数字最漂亮但内容是假的）。
            if ov.get("verdict") == "差异已消失":
                closed = True
                rules.append("U2-4")
        # U5-0：**台账里已经写好的施工单行**（十一项表 / 阶段归属列表）⇒ **最高优先级**
        g = grading.get(ident)
        if g and not stage:
            stage = g.get("stage", "")
            blocking = g.get("blocking", "")
            hint = g.get("status_hint", "")
            if hint:
                hs = ("已闭环" if e["dir"] == "Pitfalls" else
                      "✅ 仍成立" if e["dir"] == "Intentional_Deviations" else "✅ 已一致") if hint == "已修" else \
                     ("仍生效" if e["dir"] == "Pitfalls" else
                      "⏳ 未签字" if e["dir"] == "Intentional_Deviations" else "🔴 未闭合")
                status = hs
                rules.append("U2-0")
            explicit_prio = g.get("prio", "")
            explicit_final = g.get("final_goal", "")
            explicit_evidence = g.get("evidence", "")
            stage_src = f"{g['source']}（{g['kind']}）"
            rules.append("U5-0")
        # U5：权威定级表优先（**U5-O 已经定级 ⇒ 后面几路都不覆盖**）
        if not stage and ident in shared_grading:
            g = shared_grading[ident]
            stage = g["stage"]; blocking = g["blocking"]; stage_src = g["src"]
            rules.append("U5a")
        elif not stage and ident in stage1_05:
            g = stage1_05[ident]
            stage = g["stage"]; blocking = g["blocking"]; stage_src = g["src"]
            rules.append("U5b")
        elif not stage and e["dir"] == "Intentional_Deviations" and status in ("✅ 仍成立", "⛔ 已失效", "⛔ 已撤销"):
            stage = "D"
            stage_src = "docs/Intentional_Deviations/（已签字偏离 ⇒ 决策不是缺陷）"
            rules.append("U5d")
        if not stage:
            st, why = entry_stage_decl(e)
            if st:
                stage = st
                stage_src = why
                rules.append("U5f")
        if not stage and e["dir"] == "Pitfalls":
            # ⚠️ **U5-P · Pitfalls 条目 = 元教训 ⇒ 阶段归属「不适用」**
            #   理由：§10 的 A-D 是给「**问题**」分的（缺陷 / 风险 / 待恢复语义 / 确认不实现），
            #   而坑位是「**教训**」——「测试全绿也起不来」不是一条待修的问题，
            #   而是「下次怎么防」。⇒ 给它硬分 A-D 是**范畴错误**。
            #   ⇒ 若某条坑位**指向**一个具体行为差异 ⇒ 那个差异在 **DRIFT 侧**定级，
            #     坑位本身只承担「防」的角色（可由派生链回指）。
            stage = "N/A（不适用）"
            stage_src = "docs/Pitfalls/**（元教训 ⇒ 不是「问题」，不参与 A-D 定级）"
            rules.append("U5P")
        if not stage:
            stage = "—"
            stage_src = "（未派生 · 待人工定级）"
            rules.append("U5e")
        # U6
        if not blocking:
            decl = entry_blocking_decl(e)
            if decl:
                blocking = decl
                rules.append("U6g")
            else:
                blocking = "是" if stage == "A" else "否"
                rules.append("U6a")
        else:
            rules.append("U6b")
        # U4
        if ov_prio:
            prio = ov_prio
            rules.append("U4-O")
        elif explicit_prio:
            prio = explicit_prio
            rules.append("U4-0")
        elif entry_prio_decl(e):
            prio = entry_prio_decl(e)
            rules.append("U4-1")
        elif stage == "A":
            prio = "P0"
            rules.append("U4a")
        elif status in PRIO_BY_STATUS:
            prio = PRIO_BY_STATUS[status]
            rules.append("U4b")
        else:
            prio = "P3"
            rules.append("U4c")
        # U7
        if explicit_final:
            final_goal = "台账已写 · " + explicit_final[:60]
            rules.append("U7-0")
        elif stage == "C":
            final_goal = "影响 · 阶段 2 必须恢复"
        elif stage == "D":
            final_goal = "不影响 · 经分析确认不实现"
        elif stage in ("A", "B"):
            final_goal = "间接 · 阶段 1 闭合后才有阶段 2"
        else:
            final_goal = "未判定"
        rules.append("U7")
        # U8
        tids = sorted(task_of.get(ident, []))
        uids = sorted(unres_of.get(ident, []))
        if tids and uids:
            schedule = ",".join(tids) + " ｜ " + ",".join(uids)
            rules.append("U8a")
        elif tids:
            schedule = ",".join(tids)
            rules.append("U8a")
        elif uids:
            schedule = ",".join(uids)
            rules.append("U8b")
        else:
            schedule = "未排期"
            rules.append("U8c")
        # U9
        if closed:
            gap = "已闭合 · 差异已消失 ⇒ 无待实现项"
        elif evid.startswith("C"):
            gap = "无可复算指针 · 结论不可复算"
        elif evid.startswith("B") and status == "🔴 未闭合":
            gap = "静态取证 · 缺活体对拍"
        elif stage == "—":
            gap = "未定级 ⇒ 未排期"
        elif schedule == "未排期":
            gap = "已定性 · 未排期"
        else:
            gap = "—"
        rules.append("U9")
        rows.append({
            "id": ident, "dir": e["dir"], "domain": e["domain"],
            "source": f"{e['file']}:{e['line']}", "unit": e["unit"],
            "status": status or "（无状态标记）",
            "evidence": evid, "prio": prio, "stage": stage,
            "blocking": blocking, "final_goal": final_goal,
            "schedule": schedule, "gap": gap,
            "rules": ",".join(sorted(set(rules))),
            "title": e["title"].replace("\t", " ")[:160],
            "_closed": closed,
            "_ov": "U5-O" in rules,
            "basis": (overlay.get(ident) or {}).get("basis", ""),
            "_excluded": e["excluded"] or "",
            "_date": _date,
            "_auth": _auth,
            "_stage_src": stage_src,
            "_evidence_src": evid_src,
        })
    # 同一 ID 多处登记 ⇒ 合并成一行（源位置全列）
    merged: dict[str, dict] = {}
    for r in rows:
        k = r["id"]
        if k not in merged:
            merged[k] = dict(r)
            merged[k]["_sources"] = [r["source"]]
            merged[k]["_date"] = r["_date"]
            merged[k]["_statuses"] = [r["status"]]
            merged[k]["_reg"] = 1
        else:
            merged[k]["_sources"].append(r["source"])
            merged[k]["_statuses"].append(r["status"])
            merged[k]["_reg"] += 1
            # ⚠️ **取「最新核对日期」的那一条，不是取「最严重」的那一条**。
            #   依据：`docs/PhaseGoal-judge.md` §1.5 L0「状态判据 = 以**条目级最新状态**为准
            #   （旧快照不覆盖后续证据）」。
            #   ⇒ 反例（实测）：`DRIFT-HOTAPI-030` 有 7 处登记，早期是 🔴、最新是 🟡 部分闭合
            #      ⇒ 「取最严重」会把它读成 🔴 未闭合 ⇒ **阶段一结论被拉偏**。
            # ⚠️ **已签字偏离优先于「🔴 未闭合」** ——
            #    同一编号常在 DRIFT（差异本体）与 IntDev（签字档）两处登记；
            #    若取 DRIFT 的 🔴 ⇒ 一条**已签字的决策**会进「真缺口」清单
            #    ⇒ **与「签字偏离不是缺陷」的纪律冲突**（AGENTS.md §0）。
            if (r["dir"] == "Intentional_Deviations"
                    and r["status"] in ("✅ 仍成立", "⛔ 已失效", "⛔ 已撤销")
                    and merged[k]["status"] in ("🔴 未闭合", "⏳ 未签字")):
                merged[k]["status"] = r["status"]
                merged[k]["rules"] = ",".join(sorted(set(merged[k]["rules"].split(",")) | {"U2-1"}))
            if r["_date"] == merged[k]["_date"] and r["_auth"] > merged[k]["_auth"]:
                for f in ("status", "evidence", "prio", "stage", "blocking",
                          "final_goal", "schedule", "gap", "_stage_src",
                          "_evidence_src", "rules"):
                    # ⚠️ **U5-O 保护（2026-10-30）** —— **显式定级不被无定级的登记覆盖**。
                    #   实测成因：同一编号常在**汇总文件**（如 `DRIFT/global/_README.md`）
                    #   与**条目的主文件**两处登记；U5-O 只写在主文件那条 ⇒
                    #   若汇总文件那条的核对日期更新 ⇒ 合并会把它覆盖回去
                    #   ⇒ **人工定级静默失效**（数字变好看，内容全错）。
                    if f in OV_FIELDS and merged[k].get("_ov") and not r.get("_ov"):
                        continue
                    merged[k][f] = r[f]
                merged[k]["_ov"] = merged[k].get("_ov", False) or r.get("_ov", False)
            if r["_date"] > merged[k]["_date"]:
                for f in ("status", "evidence", "prio", "stage", "blocking",
                          "final_goal", "schedule", "gap", "_stage_src",
                          "_evidence_src", "_date", "rules", "_auth", "_closed"):
                    # ⚠️ 同上：U5-O 保护 —— 日期更新的**无定级**登记不得覆盖显式定级
                    if f in OV_FIELDS and merged[k].get("_ov") and not r.get("_ov"):
                        continue
                    merged[k][f] = r[f]
                merged[k]["_ov"] = merged[k].get("_ov", False) or r.get("_ov", False)
            elif r["_date"] == merged[k]["_date"] and merged[k]["stage"] == "—" and r["stage"] != "—":
                merged[k]["stage"] = r["stage"]
                merged[k]["blocking"] = r["blocking"]
                merged[k]["_stage_src"] = r["_stage_src"]
                merged[k]["final_goal"] = r["final_goal"]
                merged[k]["rules"] = r["rules"]
    out = list(merged.values())
    # ⚠️ **U5-O 第二阶段合并（2026-10-30）** —— **显式定级独立于「日期 / 权威度」生效**。
    #   实测成因：同一编号常在**汇总文件**与**主文件**两处登记，**两条的核对日期不同**
    #   ⇒ 第一阶段（取最新日期）会把**带 U5-O 的那一条整个挤掉**
    #   ⇒ **人工定级静默失效**（实测：W1/W3/W5/W7 合计 71 条定级，
    #      第一阶段之后只剩少数几条命中 ⇒ 「已闭合」数从应有的 ~30 掉到 9）。
    #   ⇒ U5-O 是**本轮显式裁定**，日期必然最新 ⇒ 它**必须**独立于日期胜出。
    #   ⇒ 若同编号有多条 U5-O ⇒ 取「日期最新 + 权威度最高」的那条。
    ov_best: dict[str, dict] = {}
    for r in rows:
        if not r.get("_ov"):
            continue
        k = r["id"]
        cur = ov_best.get(k)
        if cur is None or (r["_date"], r["_auth"]) > (cur["_date"], cur["_auth"]):
            ov_best[k] = r
    for k, r in ov_best.items():
        if k not in merged:
            continue
        for f in ("stage", "prio", "blocking", "gap", "_stage_src", "_closed", "final_goal", "basis"):
            merged[k][f] = r[f]
        merged[k]["rules"] = ",".join(sorted(set(merged[k]["rules"].split(","))
                                            | set(r["rules"].split(","))))
        merged[k]["_ov"] = True
    for r in out:
        r["source"] = ";".join(r.pop("_sources")[:3])
        r.pop("_date", None)
        sts = r.pop("_statuses", [])
        reg = r.pop("_reg", 1)
        uniq = sorted(set(sts))
        r["_reg"] = reg
        r["_conflict"] = len(uniq)
        r["_uniq_status"] = uniq
    return out, task_of, unres_of


# ════════════════════════════════════════════════════════════════════════
# 输出
# ════════════════════════════════════════════════════════════════════════

TSV_COLS = ["id", "dir", "domain", "source", "unit", "status", "evidence",
            "prio", "stage", "blocking", "final_goal", "schedule", "gap",
            "rules", "title", "basis"]

STAGE_ORDER = {"A": 0, "B": 1, "C": 2, "D": 3, "—": 4, "N/A（不适用）": 5}
PRIO_ORDER = {"P0": 0, "P1": 1, "P2": 2, "P3": 3}


def sort_rows(rows):
    return sorted(rows, key=lambda r: (STAGE_ORDER.get(r["stage"], 9),
                                       PRIO_ORDER.get(r["prio"], 9),
                                       r["domain"], r["id"]))


def render_tsv(rows) -> str:
    lines = ["\t".join(TSV_COLS)]
    for r in sort_rows(rows):
        lines.append("\t".join(str(r.get(c, "")).replace("\t", " ") for c in TSV_COLS))
    return "\n".join(lines) + "\n"


def render_md(rows, entries, tasks, unresolved, stats) -> str:
    L: list[str] = []
    a = L.append
    idx = {r["id"]: r for r in rows}
    # ⚠️ **2026-10-30 口径修正 · 🟡 部分闭合 计入「开放」** ——
    #   原口径只认 🔴/仍生效/⏳ ⇒ **🟡「部分闭合」被排除在「开放」之外**
    #   ⇒ 实测后果：**55 条 🟡 条目（其中 50 条未排期）**被统计成「未定级且状态开放 = 0」
    #   ⇒ **掩盖了 50 条仍有剩余工作量的条目**（数字漂亮、内容是假的）。
    #   ⇒ 判据依据：🟡 = 「修了一部分，**还剩一部分**」⇒ **仍有待实现项** ⇒ 属开放。
    #   ⇒ 仍不计入开放的：✅ 已一致 / ⚫ 已签字偏离（决策不是缺陷）/ N/A 元教训。
    open_states = ("🔴 未闭合", "🟡 部分闭合", "仍生效", "⏳ 未签字")

    a("# 阶段一施工单派生层 · 三目录台账 → 施工单（2026-10-30）")
    a("")
    a("> **本文件是「派生层」** —— 每个值都**从** `docs/DRIFT` / `docs/Intentional_Deviations` /")
    a("> `docs/Pitfalls` **投影**而来，**不重新裁定任何结论**（每个派生值都带 `rules` 规则号）。")
    a("> **机器层** = [`stage1-derivation.tsv`](stage1-derivation.tsv)（全量问题项 × 16 列）")
    a("> **生成器 + 守门** = [`tools/ledger-derive/derive-stage1.py`](../../../tools/ledger-derive/derive-stage1.py)")
    a("> （`--check` 模式：源台账改了而本层没重生成 ⇒ **EXIT=1**）")
    a("> **规范来源** = [`docs/twoStage.md`](../../twoStage.md) §10（11 字段 · P0-P3 × A-D）＋ §5.3（暂缓项 11 字段）")
    a("")
    a("---")
    a("")
    a("## 一、口径（机器可复算）")
    a("")
    a("### 1.1 一句话结论")
    a("")
    a(f"> **三目录**有 **{stats['n_problem_ids']}** 个带状态的问题项，")
    a(f"> 其中只有 **{stats['n_staged']}** 个（**{stats['n_staged_pct']}%**）带阶段归属，")
    a(f"> **{stats['n_unstaged_open']}** 条是**开放状态**（🔴 未闭合 / 仍生效 / ⏳ 未签字）却**没有阶段归属** ——")
    a("> **它们既不在施工单里，也没有明确推给阶段 2**。这就是「台账不能当施工单」的量化原因。")
    a(f"> 证据面上：有活体证据的 **{stats['n_a_total']}** 条里，**只有 "
      f"{stats['a_sub'].get('A 活体 · 绿(0)', 0)} 条**的活体证据是**绿**（支持当前结论），")
    a(f"> 其余 **{stats['n_a_total'] - stats['a_sub'].get('A 活体 · 绿(0)', 0)}** 条的证据本身是"
      f"**红 / 环境问题 / 混合** ⇒ **「有活体证据」≠「结论被证实」**。")
    a("> 本派生层把这几条数字**变成可复算的断言**，并把它们排成**可开工的清单**（§四）。")
    a("")
    a("### 1.2 覆盖面（旧口径的盲区）")
    a("")
    a("| 事实 | 数字 | 依据 |")
    a("|---|---|---|")
    a(f"| 三目录条目（**宽口径**扫描） | **{stats['n_entries']}** 条 / **{stats['n_ids']}** 个唯一编号 | U1 |")
    a(f"| 其中**问题项**（自身带状态标记） | **{stats['n_problems']}** 条 / **{stats['n_problem_ids']}** 个唯一编号 | U1 + U2 |")
    a(f"| 旧口径（`ledger-claim-audit.py` 的 `ID_RE` 前缀白名单）能认出 | **{stats['n_old_recognized']}** / {stats['n_problem_ids']} | ⚠️ **漏 {stats['n_old_missed']} 个唯一编号** |")
    a(f"| 施工单任务（`_task-universe.md`） | {stats['n_tasks']} 条 | §〇 口径 |")
    a(f"| 施工单 ↔ 台账条目**强链** | **{stats['n_linked_ids']}** 个条目被回指 | U8 |")
    a("")
    a("> ⚠️ **上一轮报给用户的「1183 条目 / 707 唯一 ID」是**旧口径**（前缀白名单）**的数字**")
    a(f"> ⇒ 本派生层用**宽口径**重扫 ⇒ **{stats['n_problems']} 条问题项 / {stats['n_problem_ids']} 个唯一编号**。")
    a("> **差额不是「多算了」** —— 是旧口径**认不出**整族编号（`MISMATCH-API-*` / `OP-*` / `T-*` / `N-*` /")
    a("> `CD-*` / `ADJ-*` / `A-*` / `B-*` / `C-*` / **`E60` 这类「字母+数字」形态** …）。")
    a("> ⇒ **「落空 0」这类结论的覆盖面小于表面**（同族：弱判据把真缺陷洗成 0）。")
    a("")
    a("### 1.3 U1 · 条目（问题项）识别")
    a("")
    a("```")
    a("位置候选 = 二~五级标题（首个 token；**不在首位时扫全文取第一个编号**）· 表格首列")
    a("编号形态 = ^[A-Z][A-Za-z0-9]{0,14}(-[A-Za-z0-9]{1,24})*-\\d{1,4}[A-Za-z]?$  |  ^E\\d{1,3}$")
    a("排除     = tools/ledger-derive/excluded-id-prefixes.txt（显式数据文件，逐条带理由）")
    a("必须     = 自身带状态标记（取值域按目录限定，见 U2）")
    a("分段     = 互不重叠（每个候选的段到下一个候选为止）—— 否则同一个 🔴 会被数两次")
    a("```")
    a("")
    a("### 1.4 派生规则表（R1~R8，每条都能复核）")
    a("")
    a("| 规则 | 派生什么 | 判据 | 依据 |")
    a("|---|---|---|---|")
    a("| **U2 状态** | 处理状态 | ①「现行态 = X」显式声明优先 ②标题行 ③条目段；取值域**按目录**限定 | 三目录各自的取值域约定 + `PhaseGoal-judge.md` §1.5 L0 |")
    a("| **U3 证据强度** | A 活体 / B 静态 / C 无指针 | 段内点名**已在活体台账实跑过**的探针 ⇒ A；否则有 `文件:行号` ⇒ B；否则 C | `live-parity-2026-10-30.md` §二 |")
    a("| **U4 优先级** | P0-P3 | 台账已写 ⇒ 用台账的；否则 A 类⇒P0 · 🔴/仍生效/⏳未签字⇒P1 · 🟡/待核实⇒P2 · 其余⇒P3 | `twoStage.md` §10 |")
    a("| **U5 阶段归属** | A-D | ①台账里的**十一项表** ②共享层定级表 ③`Stage1/05` ④已签字偏离⇒D ⑤条目**自己正文**的归属声明 | `twoStage.md` §10 |")
    a("| **U5-O 显式定级叠加层** | A-D | `docs/DRIFT/_cross/grading/*.md` 里**逐条与 Go 源码对照后**的人工定级 ⇒ **优先级最高**（压过 U5-0 的历史表格判定）· **依据列必须含 Go + RAG 双侧 `文件:行号`**，否则整行丢弃 | `docs/DRIFT/_cross/grading/` |")
    a("| **U6 是否阻断** | 是/否 | ①权威表的「阻断」列 ②条目正文自己的「不阻断阶段 1」声明 ③A⇒是 | `Stage1/05` 字段 8 |")
    a("| **U7 最终目标影响** | 影响/不影响/间接/未判定 | 台账已写 ⇒ 用台账的；否则 C⇒影响 · D⇒不影响 · A/B⇒间接 | `twoStage.md` §10 |")
    a("| **U8 排期指针** | 任务 ID ｜ BLK/GAP/ENV/DEC-xx | 任务块里**直接写了本条目编号**，或任务块的**台账指针落在本条目段内** | `_task-universe.md` + `_unresolved.md` |")
    a("| **U9 证据缺口** | 缺口描述 | C⇒无可复算指针 · B+🔴⇒静态取证·缺活体 · 未定级⇒未定级⇒未排期 · 未排期⇒未排期 | — |")
    a("")
    a("### 1.5 排除清单（`excluded-id-prefixes.txt`）")
    a("")
    a("| 前缀 | 理由 |")
    a("|---|---|")
    for pre, why in sorted(load_exclusions().items()):
        a(f"| `{pre}` | {why} |")
    a("")
    a("---")
    a("")
    a("## 二、施工单总表（**能直接开工的部分**）")
    a("")
    a("> 本节把三处已有的施工单**并成一张表**：")
    a("> ① `_task-universe.md` 的 **37 条任务**（计划任务 ID）· ② `_unresolved.md` 的 **14 条未解决项**")
    a("> ③ 派生层里**已定级但未排期**的台账条目 ⇒ **凡要动手的，这里都能查到去向**。")
    a("")
    a("### 2.1 计划任务（`_task-universe.md`）→ 覆盖的台账条目")
    a("")
    a("| 计划 ID | 标题 | 阶段类 | 阶段一影响 | 覆盖的台账条目（派生链） |")
    a("|---|---|---|---|---|")
    for tid in sorted(tasks, key=lambda t: (t[0], t)):
        t = tasks[tid]
        linked = sorted(x for x in t["ids"] if x in idx)
        imp = ""
        m = re.search(r"\|\s*\*\*阶段一影响\*\*\s*\|\s*(.*?)\s*\|", t["seg"])
        if m:
            imp = m.group(1)[:46]
        elif t["seg"]:
            im = re.search(r"\|\s*阶段一影响\s*\|\s*([^|]{0,46})", t["seg"])
            imp = im.group(1).strip() if im else ""
        a(f"| `{tid}` | {t['title'][:44]} | **{t['class'] or '—'}** | {imp} | "
          f"{(' · '.join('`' + x + '`' for x in linked)) if linked else '（未回指台账条目）'} |")
    a("")
    a("### 2.2 未解决项（`_unresolved.md`）")
    a("")
    a("| 编号 | 阶段类 | 影响的台账条目 |")
    a("|---|---|---|")
    for uid in sorted(unresolved):
        u = unresolved[uid]
        linked = sorted(x for x in u["ids"] if x in idx)
        a(f"| `{uid}` | {u['class'] or '—'} | {(' · '.join('`' + x + '`' for x in linked)) if linked else '（未回指台账条目）'} |")
    a("")
    a("---")
    a("")
    a("## 三、阶段归属分布")
    a("")
    a("| 归属 | 条目数 | 含义 |")
    a("|---|---|---|")
    for st, mean in (("A", "阶段 1 准入阻断项（不处理就无法通过简历项目验收）"),
                     ("B", "阶段 1 竞争力补强项（技术亮点与验证闭环）"),
                     ("C", "阶段 2 最终工程建设项（业务语义恢复 / 生产工程机制）"),
                     ("D", "经分析确认可不实现或不适用（须有明确理由）"),
                     ("—", "**未派生 —— 待人工定级**（不是「不重要」，是「还没定级」）")):
        a(f"| **{st}** | {stats['stage_counts'].get(st, 0)} | {mean} |")
    a("")
    a("| 优先级 | 条目数 |")
    a("|---|---|")
    for pr in ("P0", "P1", "P2", "P3"):
        a(f"| {pr} | {stats['prio_counts'].get(pr, 0)} |")
    a("")
    a("---")
    a("")
    a("## 四、真缺口（**可开工清单**）")
    a("")
    a("> 判据 = **状态开放**（🔴 未闭合 / 仍生效 / ⏳ 未签字）**且** `排期指针 = 未排期`")
    a("> ⇒ **不是「没发现」，是「发现了但没进施工单」** —— 旧口径下它们**不可见**。")
    a("")
    g_open = [r for r in rows if r["status"] in open_states and r["schedule"] == "未排期"]
    g_closed = [r for r in g_open if r.get("_closed")]
    g_open_nc = [r for r in g_open if not r.get("_closed")]
    g_ab = [r for r in g_open_nc if r["stage"] in ("A", "B")]
    g_cd = [r for r in g_open_nc if r["stage"] in ("C", "D")]
    g_unstaged = [r for r in g_open_nc if r["stage"] == "—"]
    a(f"**合计 {len(g_open)} 条** —— ① **A/B 类**（阶段 1 范围内）**{len(g_ab)}** 条")
    a(f"· ② C/D 类（**阶段 2 承接**，§5.3 暂缓项清单已登记）**{len(g_cd)}** 条")
    a(f"· ③ 未定级 **{len(g_unstaged)}** 条")
    a(f"· ④ **已闭合**（逐条 Go 对照后「**差异已消失**」⇒ **无待实现项**）**{len(g_closed)}** 条")
    a("")
    a("> ⚠️ **第 ④ 档必须与 C/D 分开** —— 实测（W1/W5 轮）：台账里大量条目仍写 🔴 未闭合，")
    a("> 但逐条回源后**差异早已不存在**（只是台账没销账）⇒ 它们**不是**「阶段 2 承接」，")
    a("> 而是**已闭合、无待实现项** ⇒ 混进阶段二工作量清单会把那份清单变成**假待办**。")
    a("> ⇒ 本档的处置 = **回写台账销账**（状态订正），**不进任何阶段的工作量**。")
    a("")
    a("### 4.1 ① A/B 类 · 开放且未排期（**真缺口 —— 阶段 1 范围内的漏排**）")
    a("")
    a("> **A 类 = 阶段 1 准入阻断项**（不处理就无法通过简历项目验收）·")
    a("> **B 类 = 阶段 1 竞争力补强项**。")
    a("> 两条都**不在** `_task-universe.md` 的 37 条任务里 ⇒ **施工单漏了它们**。")
    a("")
    a("| 归属 | 优先级 | ID | 域 | 状态 | 证据 | 证据缺口 | 源位置 |")
    a("|---|---|---|---|---|---|---|---|")
    for r in sort_rows(g_ab):
        a(f"| {r['stage']} | {r['prio']} | `{r['id']}` | {r['domain']} | {r['status']} | "
          f"{r['evidence']} | {r['gap']} | `{r['source']}` |")
    a("")
    a(f"> C/D 类 **{len(g_cd)}** 条另有去向（阶段 2 恢复方案已在各条台账的 §5.3 十一项表里）")
    a("> ⇒ **不计入阶段一判定**（`PhaseGoal-judge.md` §1.1 ⑥ 第 4 条）⇒ 不列。")
    a("")
    a(f"### 4.4 ④ 已闭合（**逐条 Go 对照后差异已消失 · 无待实现项**）")
    a("")
    a("> **不是「阶段 2 承接」** —— 是台账**没销账**。处置 = **回写台账订正状态**。")
    a("")
    a("| 归属 | ID | 域 | 台账状态 | 源位置 |")
    a("|---|---|---|---|---|")
    for r in sort_rows(g_closed):
        a(f"| {r['stage']} | `{r['id']}` | {r['domain']} | {r['status']} | `{r['source']}` |")
    a("")
    a("### 4.2 ③ 未定级（**需人工定级才能进施工单**）")
    a("")
    a("> 派生层**不猜**归属 ⇒ 这一档必须人工定级。")
    a("> 排序 = **优先级 × 证据强度**（P1 且证据 C = 既危险又不可复算 ⇒ 排最前）。")
    a("")
    a("| 优先级 | 证据 | ID | 域 | 状态 | 源位置 |")
    a("|---|---|---|---|---|---|")
    for r in sort_rows(g_unstaged):
        a(f"| {r['prio']} | {r['evidence']} | `{r['id']}` | {r['domain']} | {r['status']} | `{r['source']}` |")
    a("")
    a("### 4.5 同编号多态（**同一编号在多处登记、状态不一致**）")
    a("")
    a("> 这是派生层**刻意不替人做决定**的地方：同一编号在多个文件登记，状态可能不同。")
    a("> 合并口径 = **取最新核对日期的那条**（`PhaseGoal-judge.md` §1.5 L0）；")
    a("> 冲突条数 = **登记处状态取值不同**的编号数 ⇒ **需人工归一**。")
    a("")
    a("| ID | 登记处数 | 出现过的状态 | 采纳的状态 | 源位置 |")
    a("|---|---|---|---|---|")
    conflicts = [r for r in rows if r["_conflict"] > 1]
    conflicts.sort(key=lambda r: (-r["_conflict"], r["id"]))
    for r in conflicts[:60]:
        a(f"| `{r['id']}` | {r['_reg']} | {' / '.join(r['_uniq_status'])} | {r['status']} | `{r['source']}` |")
    a("")
    a(f"> 共 **{len(conflicts)}** 个编号存在状态冲突（此处列前 60）。")
    a("")
    a("---")
    a("")
    a("## 五、证据强度分层（**「已改」≠「已验证」**）")
    a("")
    a("| 档 | 含义 | 条目数 |")
    a("|---|---|---|")
    for k, v in (("A 活体", "段内点名了**已在活体台账实跑过**的探针（**带探针的真实退出码**）"),
                 ("B 静态", "有可复算的 `文件:行号` 指针，但无活体探针"),
                 ("C 无指针", "两样都没有 ⇒ 结论今天不可复算")):
        a(f"| **{k}** | {v} | {stats['evid_counts'].get(k, 0)} |")
    a("")
    a("### 5.1 A 档内部再分（**「有活体证据」≠「结论被证实」**）")
    a("")
    a("> ⚠️ **A 档带的是探针的真实退出码** —— 因为实测发现：**A 档引用的探针里**")
    a("> 有指向 **EXIT=2（环境问题 ⇒ 结论不可用）**的，也有指向 **EXIT=1（不一致）**的。")
    a("> ⇒ **只写「A 活体」会让人以为这条已被证实** ⇒ 必须把退出码带出来。")
    a("> ⚠️ 且「**红**」**≠ 有缺陷** —— 红分三种（已签字偏离 / 已登记残留 / 真问题），")
    a("> 逐条定性见 [`live-parity-2026-10-30.md`](live-parity-2026-10-30.md) §0 与 §四。")
    a("")
    a("| A 档细分 | 含义 | 条目数 |")
    a("|---|---|---|")
    for k, v in sorted(stats["a_sub"].items()):
        mean = {
            "A 活体 · 绿(0)": "探针断言**全部成立** ⇒ 这条的活体证据**支持**当前结论",
            "A 活体 · 红(1)": "探针判定**不一致** ⇒ 当前结论**未被证实**（红 ≠ 有缺陷）",
            "A 活体 · 环境(2)": "探针判定「**环境问题**」⇒ 结论**不可用**",
            "A 活体 · 混(0/1)": "多个探针退出码**不一致**（有的绿有的红）⇒ 需逐条看",
            "A 活体 · 混(0/1/2)": "同上（还有退出码 2）⇒ 需逐条看",
            "A 活体 · 混(0/2)": "同上（绿 + 环境问题）⇒ 需逐条看",
            "A 活体 · 混(0/4)": "同上（绿 + 退出码 4）⇒ 需逐条看",
        }.get(k, "")
        a(f"| **{k}** | {mean} | {v} |")
    a("")
    a(f"> ⇒ **真正「活体坐实」（绿）的只有 "
      f"{stats['a_sub'].get('A 活体 · 绿(0)', 0)} 条**（"
      f"{stats['a_sub'].get('A 活体 · 绿(0)', 0) * 100 // max(len(rows), 1)}%）；")
    a(f"> 其余 **{stats['n_a_total'] - stats['a_sub'].get('A 活体 · 绿(0)', 0)}** 条 A 档的证据本身是"
      f"**红 / 环境问题 / 混合** ⇒ **不可当成「已坐实」**。")
    a("")
    a("---")
    a("")
    a("### 5.2 证据边界声明（**C 档 = 今天不可复算**）")
    a("")
    a(f"> **本节是「效力边界」的正式声明** —— 用户 2026-10-30 拍板：**C 档条目"
      f"（{stats['evid_counts'].get('C 无指针', 0)} 条）本轮不做活体取证，"
      f"接受其静态证据**。声明如下：")
    a(">")
    a("> 1. **C 档条目的状态 = 台账自述**，不是本轮可复算的结论。")
    a(">    ⇒ 任何引用 C 档条目作为**前提**的结论，必须标注「前提未回源」。")
    a("> 2. **C 档条目不得单独构成「已证实」** —— 它只是「被登记过」，不是「被核对过」。")
    a(">    ⇒ 与 AGENTS.md §3.3.3 第 ② 条同源：**指针能解析 ≠ 结论为真**。")
    a("> 3. **C 档条目的阶段归属（本轮 U5-O 定级）只解决「排不排期」，不解决「对不对」。**")
    a(">    ⇒ 定为 A/B 只表示「阶段一要处理」，**不表示已证明它成立**。")
    a("> 4. **本声明不等于「C 档条目已被否定」** —— 它们仍是**待回源**状态，")
    a(">    且**回源是逐条与 Go 源码对照**（Q1 拍板口径），不是抽样。")
    a("> 5. **已签字偏离（`Intentional_Deviations`）不受本声明影响** —— 它们的效力来自**签字**，")
    a(">    不来自「今天能不能复算」（AGENTS.md §0 第 1 条）。")
    a(">")
    a("> ⇒ **可复算性分级见 §五 表**；本声明只界定**效力**，不改任何条目的状态。")
    a("")
    a("---")
    a("")
    a(f"## 六、全量派生表（{len(rows)} 条问题项）")
    a("")
    a("> 机读请用 [`stage1-derivation.tsv`](stage1-derivation.tsv)（同一次生成，16 列）。")
    a("")
    a("| ID | 目录 | 域 | 状态 | 证据 | 优先级 | 归属 | 阻断 | 最终目标 | 排期 | 缺口 | 源位置 | 依据（Go ↔ RAG） |")
    a("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in sort_rows(rows):
        _b = (r.get("basis") or "").replace("|", "／").replace("\n", " ")
        _b = (_b[:150] + "…") if len(_b) > 151 else _b
        a(f"| `{r['id']}` | {r['dir'][:4]} | {r['domain']} | {r['status']} | {r['evidence']} | "
          f"{r['prio']} | {r['stage']} | {r['blocking']} | {r['final_goal']} | {r['schedule']} | "
          f"{r['gap']} | `{r['source']}` | {_b or '—'} |")
    a("")
    a("---")
    a("")
    a("## 七、已知盲区（**这一层的效力边界**）")
    a("")
    a("1. **派生层不重新裁定任何结论** —— 台账说「已修」就是「已修」（在该证据强度下）。")
    a("   **「跑绿 ≠ 对」** 在这一层同样成立：U3 的 A 档只证明**那条探针的断言**成立，")
    a("   **不证明本条目结论的全量成立**。")
    a("2. **U5 拿不到定级 ⇒ 写 `—`** —— 不是「不重要」，是**还没定级**。")
    a(f"   本轮有 **{stats['n_unstaged']}** 条属这一档 ⇒ 它们**不进施工单**，除非先定级。")
    a("3. **U8 的强链只认「指针落在条目段内」或「任务块里直接写了编号」** ——")
    a("   **既没写编号也没给指针**的语义关联 ⇒ **不可见**（这是本层的固有上限）。")
    a("4. **`CASE-*` 被排除** ⇒ 它们是**测试场景矩阵行**（证据层），不是差异条目；")
    a("   差异条目会引用 `CASE-*` 作为自己的证据。")
    a("5. **「阶段归属」与「优先级」不可互推** —— `twoStage.md` §10 明写「两者不能互相替代」。")
    a("   本层用 U4/U5 两条**独立**规则算，交叉不符时**两个都保留**，不强行对齐。")
    a("6. **「未排期」≠「不重要」** —— 它只说明**当前没有任何任务/未解决项指向它**。")
    a("7. **本层**不读**产品代码** ⇒ 它是**台账的投影**，不是代码的事实；")
    a("   「指针能解析」也不等于「结论为真」（同 AGENTS.md §3.3.3 第 ② 条）。")
    a("8. 🆕 **U8 认不出「表行编号」** —— 实测（U5-O 轮）：`global/vs-common.md` 里的表行编号")
    a("   （`B-10` / `B-11` / `G4-A4-05` / `G4-A4-07`）**与任务的「原始编号」不是一一对应**")
    a("   （`_task-universe.md` 的 `P2-T01` 写的是 `E65` / `X-6` / `P0 #4`，不含 `B-10`）")
    a("   ⇒ 派生层会判它们「**未排期**」⇒ **报进真缺口清单**。")
    a("   ⚠️ **这不等于它们没排期** —— 它们可能是某个任务的**改造面内条目**")
    a("   ⇒ **本层不猜**（不按语义关联去连任务）⇒ 处置 = **在 `_unresolved.md` 登记歧义 + 人工确认**。")
    a("   ⇒ 与第 3 条同源：**「不可见」≠「不存在」**。")
    a("9. 🆕 **「已闭合」档 ≠ 台账已销账** —— §4.4 的条目在台账里**仍写 🔴 未闭合**，")
    a("   只是逐条回源后**差异早已不存在**。本层**不改状态**（投影不裁定）")
    a("   ⇒ 状态订正由**回写台账**完成（追加「阶段归属定级附录」，保留区机制不产生自噬）。")
    a("")
    a("---")
    a("")
    a("## 八、复现")
    a("")
    a("```bash")
    a("cd \"$(git rev-parse --show-toplevel)\"")
    a("python3 tools/ledger-derive/derive-stage1.py            # 重新生成")
    a("python3 tools/ledger-derive/derive-stage1.py --check    # 守门：产物过期 ⇒ EXIT=1")
    a("python3 tools/ledger-derive/derive-stage1.py --report   # 只打印统计")
    a("```")
    a("")
    return "\n".join(L) + "\n"


# ════════════════════════════════════════════════════════════════════════

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="守门模式：产物与源台账不同步 ⇒ EXIT=1")
    ap.add_argument("--report", action="store_true", help="只打印统计，不写文件")
    args = ap.parse_args()

    exclusions = load_exclusions()
    entries, line_index = scan_ledgers(exclusions)
    live = load_live_probes()
    tasks = load_tasks(line_index)
    unresolved = load_unresolved(line_index)
    shared = load_shared_grading()
    s105 = load_stage1_05()
    grading = scan_grading_tables()
    judge_closed = load_judge_closed()
    overlay = load_grading_overlay()
    all_tools = {str(p.relative_to(ROOT)) for p in (ROOT / "tools").rglob("*")
                 if p.is_file() and p.suffix in (".sh", ".py")}

    problems = [e for e in entries if detect_status(e) and not e["excluded"]]
    rows, task_of, unres_of = derive(entries, live, tasks, unresolved, shared, s105, all_tools,
                                    grading, judge_closed, overlay)

    evid_counts = collections.Counter(r["evidence"] for r in rows)
    stats = {
        "n_entries": len(entries),
        "n_ids": len({e["id"] for e in entries}),
        "n_problems": len(problems),
        "n_problem_ids": len({e["id"] for e in problems}),
        "n_detail": len(entries) - len(problems),
        "n_tasks": len(tasks),
        "n_linked_ids": len(task_of),
        "evid_counts": evid_counts,
    }
    # 旧口径能认出多少
    old = re.compile(
        r"^(?:DRIFT-[A-Z0-9]+|SD|DEV|PIT|PF|UNRESOLVED|S1|SHOT|CYCLE|E)(?:[-_][A-Za-z0-9]+)*-\d+[A-Za-z]?$")
    pids = {e["id"] for e in problems}
    stats["n_old_recognized"] = len({i for i in pids if old.match(i)})
    stats["n_old_missed"] = stats["n_problem_ids"] - stats["n_old_recognized"]
    stats["a_sub"] = collections.Counter(r["evidence"] for r in rows if r["evidence"].startswith("A"))
    stats["n_a_total"] = sum(v for k, v in stats["a_sub"].items())
    stats["stage_counts"] = collections.Counter(r["stage"] for r in rows)
    stats["prio_counts"] = collections.Counter(r["prio"] for r in rows)
    stats["n_staged"] = stats["n_problem_ids"] - stats["stage_counts"].get("—", 0) \
        - stats["stage_counts"].get("N/A（不适用）", 0)
    stats["n_staged_pct"] = (stats["n_staged"] * 100 // max(stats["n_problem_ids"], 1))
    stats["n_unstaged"] = stats["stage_counts"].get("—", 0)
    stats["n_meta"] = stats["stage_counts"].get("N/A（不适用）", 0)
    open_states = ("🔴 未闭合", "🟡 部分闭合", "仍生效", "⏳ 未签字")
    stats["n_unstaged_open"] = sum(1 for r in rows if r["stage"] == "—" and r["status"] in open_states)
    stats["n_to_grade"] = stats["n_unstaged_open"]
    stats["n_conflict"] = sum(1 for r in rows if r.get("_conflict", 1) > 1)
    _op = ("🔴 未闭合", "🟡 部分闭合", "仍生效", "⏳ 未签字")
    stats["n_gap_ab"] = sum(1 for r in rows if r["status"] in _op and r["schedule"] == "未排期"
                             and r["stage"] in ("A", "B") and not r.get("_closed"))
    stats["n_gap_cd"] = sum(1 for r in rows if r["status"] in _op and r["schedule"] == "未排期"
                             and r["stage"] in ("C", "D") and not r.get("_closed"))
    stats["n_closed"] = sum(1 for r in rows if r.get("_closed"))
    stats["n_overlay"] = len(overlay)
    stats["n_overlay_used"] = sum(1 for r in rows if "U5-O" in r["rules"])

    print("── 派生层统计 ──")
    print(f"  条目（宽口径扫描）      {stats['n_entries']:5d}   唯一 {stats['n_ids']:5d}")
    print(f"  问题项（带状态标记）    {stats['n_problems']:5d}   唯一 {stats['n_problem_ids']:5d}")
    print(f"  旧口径认得出            {stats['n_old_recognized']:5d}   漏 {stats['n_old_missed']:5d}")
    print(f"  施工单任务              {stats['n_tasks']:5d}")
    print(f"  任务↔条目强链条目数    {stats['n_linked_ids']:5d}")
    print(f"  证据强度                " + " · ".join(f"{k} {v}" for k, v in sorted(evid_counts.items())))
    stc = collections.Counter(r["stage"] for r in rows)
    print(f"  阶段归属                " + " · ".join(f"{k} {v}" for k, v in sorted(stc.items())))
    unc = sum(1 for r in rows if r["schedule"] == "未排期")
    print(f"  未排期                  {unc:5d}")
    print(f"  已定级                  {stats['n_staged']:5d} / {stats['n_problem_ids']} ({stats['n_staged_pct']}%)")
    print(f"  未定级且状态开放        {stats['n_unstaged_open']:5d}   ← 「发现了但没进施工单」")
    print(f"  同编号状态冲突          {stats['n_conflict']:5d}")
    print(f"  真缺口 A/B 类未排期     {stats['n_gap_ab']:5d}   ← 阶段一范围内的漏排")
    print(f"  真缺口 C/D 类未排期     {stats['n_gap_cd']:5d}   （阶段 2 承接，不计阶段一）")
    print(f"  已闭合（差异已消失）     {stats['n_closed']:5d}   ← 台账没销账 · 无待实现项")
    print(f"  U5-O 显式定级叠加层      {stats['n_overlay']:5d} 条登记 · "
          f"{stats['n_overlay_used']:5d} 条命中派生表")

    if args.report:
        return 0

    tsv = render_tsv(rows)
    md = render_md(rows, entries, tasks, unresolved, stats)
    if args.check:
        problems_found = []
        if not TSV_PATH.exists():
            problems_found.append(f"产物缺失：{TSV_PATH.relative_to(ROOT)}")
        elif TSV_PATH.read_text(encoding="utf-8") != tsv:
            problems_found.append(f"产物过期：{TSV_PATH.relative_to(ROOT)} 与源台账不同步")
        if not MD_PATH.exists():
            problems_found.append(f"产物缺失：{MD_PATH.relative_to(ROOT)}")
        elif MD_PATH.read_text(encoding="utf-8") != md:
            problems_found.append(f"产物过期：{MD_PATH.relative_to(ROOT)} 与源台账不同步")
        if problems_found:
            print("\n🔴 派生层守门 FAIL")
            for p in problems_found:
                print(f"   - {p}")
            print("   ⇒ 修法：python3 tools/ledger-derive/derive-stage1.py")
            return 1
        print("\n✅ 派生层守门 PASS（产物与源台账同步）")
        return 0

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    TSV_PATH.write_text(tsv, encoding="utf-8")
    MD_PATH.write_text(md, encoding="utf-8")
    print(f"\n✅ 已生成：\n   {TSV_PATH.relative_to(ROOT)}  ({len(tsv.encode('utf-8')):,} B)\n"
          f"   {MD_PATH.relative_to(ROOT)}  ({len(md.encode('utf-8')):,} B)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
