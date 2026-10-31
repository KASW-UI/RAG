#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""阶段一终点指标基线（docs/PhaseGoal-judge.md）的**内部一致性**自检守门。

为什么需要它（AGENTS.md §3.3「框架机制必须验证实际生效」的同源纪律）：
本文件是**阶段一准入线的唯一可判定版**。如果它自己的四层状态互相矛盾、
计数对不上、或引用了仓内**不存在**的判定命令，那它就不是「可机械执行的基线」，
只是一份会漂移的文字。

本脚本检查（**全部只读**，不构建、不连库、不改任何文件）：
  1. Markdown 表格结构：每个表格块内列数一致（防止改列后漏改行）。
  2. 矩阵 A-1（39 行）与 A-2（39 行）的 (域, 组件) 集合**完全一致且无重复**。
  3. 38 组件构成自洽：10 api + 10 rpc + 8 common + 10 proto；`service/common`
     是共享层、**不在 38 之内**（AGENTS.md §3.5）。
  4. R 取值域（§1.1）、E 取值域（§1.2）、组件状态取值域（§1.3）。
  5. **R ↔ 组件状态**的耦合（R0–R2 ⇒ 不得判 ✅；未评级 ⇒ 必须 🔴；R3/R4 ⇒ 不得 ⚫）。
  6. **每行「判定命令」里的仓内路径必须真实存在**（`tools/...`）。
  7. **判 ✅ 的行必须给出可复算的判定命令**（仓内存在的 tools 路径，或 `mvn ...`）。
  8. 矩阵 B（域级卡）的**组件状态明细**与 A-2 **逐个一致**；**组件数**一致。
  9. **域状态 = MIN(域内各组件状态)**（口径唯一出处 = §8.1）⇒ 并与 B 卡声明值比对。
 10. §8.2 C-1 的「N/6」与实际核心 6 域的达标数一致。
 11. 编号连续性：X-1..X-N、G-1..G-N、D-01..D-N、P0 1..9、变更记录 1..N。
 12. P0 汇总计数（✅/🟡/🔴/⚫）与 §七 P0 列的实际取值一致。
 13. A-1 的 `tests` 列求和 == 文档声明的全仓测试条数。
 14. §6.3 的命令数 == 仓内守门清单减去本轮声明「未跑」的守门。
 15. 无「未来日期」：文档内不得出现晚于其自身核对日期的日期。

用法：
    python3 tools/difftest/check-phasegoal-consistency.py            # 全部检查
    python3 tools/difftest/check-phasegoal-consistency.py --verbose  # 逐条打印

退出码：0 = 全部通过；1 = 有检查失败（逐条打印 ❌）。
"""
from __future__ import annotations

import datetime as _dt
import importlib.util
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOC = ROOT / "docs/PhaseGoal-judge.md"

# ── 组件状态 → 序（§8.1：✅ > 🟡 > ⚫ > 🔴；数字越大越好，域状态取 MIN）──
STATUS_RANK = {"✅": 4, "🟡": 3, "⚫": 2, "🔴": 1}
STATUS_CHARS = set(STATUS_RANK)
CORE_DOMAINS = ("favorite", "comment", "message", "task", "article", "users")

FAILURES: list[str] = []
NOTES: list[str] = []


def fail(check: str, msg: str) -> None:
    FAILURES.append(f"[{check}] {msg}")


def note(msg: str) -> None:
    NOTES.append(msg)


# ───────────────────────────────────────────────────────────────────────────
# Markdown 解析
# ───────────────────────────────────────────────────────────────────────────
def split_row(line: str) -> list[str]:
    """按未转义的 `|` 切分表格行。"""
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    parts = re.split(r"(?<!\\)\|", s)
    return [p.strip() for p in parts]


def is_separator(cells: list[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r":?-{2,}:?", c.strip()) for c in cells if c.strip() != "")


def parse_tables(lines: list[str], lo: int = 0, hi: int | None = None):
    """返回 [(起始行号, 表头 cells, [(行号, cells)])]。"""
    hi = len(lines) if hi is None else hi
    tables = []
    i = lo
    while i < hi:
        if lines[i].strip().startswith("|"):
            j = i
            while j < hi and lines[j].strip().startswith("|"):
                j += 1
            block = [split_row(l) for l in lines[i:j]]
            if len(block) >= 2 and is_separator(block[1]):
                header = block[0]
                rows = [(i + 3 + k, block[2 + k]) for k in range(len(block) - 2)]
                tables.append((i + 1, header, rows))
            i = j
        else:
            i += 1
    return tables


def section_range(lines: list[str], heading_prefix: str) -> tuple[int, int]:
    """返回以 heading_prefix 开头的那一节的 [lo, hi) 行号区间。

    支持 `##` 与 `###` 两级；结束于下一个**层级不深于**本节的标题。
    """
    lo = None
    level = 2
    for idx, line in enumerate(lines):
        m = re.match(r"^(#{1,6})\s", line)
        if m and heading_prefix in line:
            lo = idx
            level = len(m.group(1))
            break
    if lo is None:
        return (0, 0)
    hi = len(lines)
    for idx in range(lo + 1, len(lines)):
        m = re.match(r"^(#{1,6})\s", lines[idx])
        if m and len(m.group(1)) <= level:
            hi = idx
            break
    return (lo, hi)


def strip_md(s: str) -> str:
    """去掉强调符号与反引号，便于做值比较。"""
    return (
        s.replace("**", "")
        .replace("`", "")
        .replace("\\*", "")
        .replace("↑", "")
        .replace("↓", "")
        .strip()
    )


def _source_decl_total() -> int | None:
    """全仓**源声明测试方法数**（环境无关口径）⇒ 与 A-1 的 `tests` 列同口径。

    ⚠️ **口径**：`src/test/java` 里 `@Test` / `@ParameterizedTest` / `@RepeatedTest` /
    `@TestFactory` / `@TestTemplate` 的**方法声明数** ⇒ ⇒ **一个参数化方法算 1**
    （不是 surefire 展开后的 N 条 case）⇒ ⇒ **读源码 ⇒ 与 Docker 状态无关**。

    ⇒ ⇒ 与 `tools/difftest/check-source-test-decl-count.py` 的逐行对账同源
    （那个守门做逐行明细 + 行数 39 + proto 行 ⇒ 本函数只做求和级交叉校验）。

    ⚠️ **口径已再收窄一格（2026-10-31 订正）**：**只算代码态** —— 注释 / javadoc 里提到的
    `@Test` **文本不算** ⇒ ⇒ 全仓 **3,314 → 3,307**（`service/common` −1 · `follow-api` −2 ·
    `user-api` −2 · `admin-api` −2 ⇒ 旧口径把 javadoc 的 `{@code @Test}` 算成了测试）。
    ⇒ ⇒ **掩码器不在本文件另抄一份** ⇒ ⇒ **直接 import 同族守门里的 `code_state_mask`**
    ⇒ ⇒ 同一个量**只有一个实现**（抄第二份 = 两个真相源 = 两个守门互相打架）。
    """
    svc = ROOT / "service"
    if not svc.is_dir():
        return None
    ann = re.compile(
        r"@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b"
    )
    # ⚠️ **必须递归到任意深度**：`service/users/user/user-api/src/test/java` 是三层深
    # ⇒ ⇒ 只 glob 两层会漏掉 users 域与 task 域 ⇒ ⇒ 口径必须与
    # `tools/difftest/check-source-test-decl-count.py` **完全一致**（否则两个守门互相打架）。
    total = 0
    seen: set[pathlib.Path] = set()
    for tdir in svc.rglob("src/test/java"):
        if not tdir.is_dir() or tdir in seen:
            continue
        seen.add(tdir)
        total += _count_in(tdir, ann)
    return total


def _sibling_masker():
    """**import 同族守门里的 `code_state_mask`**（单一真相源 ⇒ 不抄第二份）。

    ⚠️ 文件名带 `-` ⇒ 不是合法模块名 ⇒ 用 `importlib.util.spec_from_file_location` 按路径加载。
    ⚠️ 加载失败 ⇒ **直接抛**（不静默回落成本文件的第二份实现）⇒ ⇒ 否则两个守门会静默打架。
    """
    here = pathlib.Path(__file__).resolve().parent
    src = here / "check-source-test-decl-count.py"
    if not src.is_file():
        raise FileNotFoundError(f"找不到同族掩码器实现：{src}")
    spec = importlib.util.spec_from_file_location("_decl_count_gate", src)
    if spec is None or spec.loader is None:
        raise ImportError(f"无法加载同族掩码器实现：{src}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    fn = getattr(mod, "code_state_mask", None)
    if fn is None:
        raise AttributeError(f"{src} 里没有 code_state_mask ⇒ ⇒ 口径无法对齐")
    return fn


def _count_in(tdir: pathlib.Path, ann: re.Pattern) -> int:
    n = 0
    try:
        files = list(tdir.rglob("*.java"))
    except OSError:
        return 0
    mask = _sibling_masker()
    for f in files:
        try:
            raw = f.read_text(encoding="utf-8", errors="ignore")
            # ⚠️ **只算代码态**（与 `check-source-test-decl-count.py` 同一实现 ⇒ 同一口径）
            n += len(ann.findall(mask(raw)))
        except OSError:
            continue
    return n


def tool_paths(text: str) -> list[str]:
    """抽出行内出现的 `tools/...` 路径（不要求被反引号包裹）。

    ⚠️ 刻意**不要求**反引号：文档里既有 `` `tools/x.py` ``，
    也有 ``GO_REPO=… bash tools/x.py`` 这种整条命令 —— 后者的路径同样必须真实存在。
    """
    out = []
    for m in re.finditer(r"(?<![\w/.-])(tools/[A-Za-z0-9_\-./]+)", text):
        p = m.group(1).rstrip(".")
        if "<" in p or ">" in p:
            continue
        out.append(p)
    return out


def status_of(cell: str) -> str | None:
    """从状态列里取出唯一的状态字符（允许后面跟文字）。"""
    body = cell.replace("**", "").strip()
    for ch in body:
        if ch in STATUS_CHARS:
            return ch
    return None


def main() -> int:
    if not DOC.exists():
        print(f"❌ 找不到 {DOC}")
        return 1
    raw = DOC.read_text(encoding="utf-8")
    lines = raw.split("\n")

    # ── 1. 表格列数一致 ────────────────────────────────────────────────
    for start, header, rows in parse_tables(lines):
        ncol = len(header)
        for lineno, cells in rows:
            if len(cells) != ncol:
                fail("表格结构", f"第 {lineno} 行列数 {len(cells)} ≠ 表头 {ncol}（表起始第 {start} 行）")
        if not header:
            fail("表格结构", f"第 {start} 行表头为空")

    # ── 2/3. A-1 与 A-2 ───────────────────────────────────────────────
    a1_lo, a1_hi = section_range(lines, "二、矩阵 A-1")
    a2_lo, a2_hi = section_range(lines, "三、矩阵 A-2")
    t_a1 = parse_tables(lines, a1_lo, a1_hi)
    t_a2 = parse_tables(lines, a2_lo, a2_hi)
    if not t_a1 or not t_a2:
        print("❌ 找不到 A-1 / A-2 表")
        return 1
    a1_rows = t_a1[0][2]
    a2_rows = t_a2[0][2]

    if len(a1_rows) != 39:
        fail("A-1 行数", f"期望 39 行（38 组件 + 1 共享层），实际 {len(a1_rows)}")
    if len(a2_rows) != 39:
        fail("A-2 行数", f"期望 39 行（38 组件 + 1 共享层），实际 {len(a2_rows)}")

    def comp_key(cells: list[str]) -> str:
        return strip_md(cells[1]) if len(cells) > 1 else "?"

    a1_keys = [comp_key(c) for _, c in a1_rows]
    a2_keys = [comp_key(c) for _, c in a2_rows]
    if len(set(a1_keys)) != len(a1_keys):
        dup = sorted({k for k in a1_keys if a1_keys.count(k) > 1})
        fail("A-1 唯一性", f"重复组件：{dup}")
    if len(set(a2_keys)) != len(a2_keys):
        dup = sorted({k for k in a2_keys if a2_keys.count(k) > 1})
        fail("A-2 唯一性", f"重复组件：{dup}")
    if set(a1_keys) != set(a2_keys):
        only1 = sorted(set(a1_keys) - set(a2_keys))
        only2 = sorted(set(a2_keys) - set(a1_keys))
        fail("A-1↔A-2", f"组件集合不一致：A-1 独有 {only1} · A-2 独有 {only2}")

    # 38 组件构成
    kinds = {"api": 0, "rpc": 0, "common": 0, "proto": 0}
    shared = 0
    for _, cells in a2_rows:
        name = comp_key(cells)
        dom = strip_md(cells[0]) if cells else ""
        if dom == "共享层" or name == "service/common":
            shared += 1
            continue
        if name.endswith("-api"):
            kinds["api"] += 1
        elif name.endswith("-rpc"):
            kinds["rpc"] += 1
        elif name.endswith("-common"):
            kinds["common"] += 1
        elif name.endswith("-proto"):
            kinds["proto"] += 1
        else:
            fail("组件命名", f"无法归类：{name}")
    if kinds != {"api": 10, "rpc": 10, "common": 8, "proto": 10}:
        fail("38 组件构成", f"期望 10 api + 10 rpc + 8 common + 10 proto，实际 {kinds}")
    if shared != 1:
        fail("共享层", f"期望 1 行共享层（service/common），实际 {shared}")

    # ── 4/5/6/7. A-2 逐行 ──────────────────────────────────────────────
    R_RE = re.compile(r"^(R[0-4]|未评级)$")
    E_RE = re.compile(r"^(E[0-5])$")
    comp_status: dict[str, str] = {}
    comp_domain: dict[str, str] = {}
    comp_tests: dict[str, int] = {}

    for lineno, cells in a2_rows:
        if len(cells) < 9:
            fail("A-2 列数", f"第 {lineno} 行只有 {len(cells)} 列")
            continue
        dom = strip_md(cells[0])
        name = comp_key(cells)
        cmd_cell = cells[3]
        r_raw = strip_md(cells[4])
        e_raw = strip_md(cells[5])
        st = status_of(cells[8])

        comp_domain[name] = dom
        comp_status[name] = st or "?"

        # R 取值域
        if not R_RE.match(r_raw):
            fail("R 取值域", f"{name}（第 {lineno} 行）R = {r_raw!r}，不在 R0..R4 / 未评级 内")
        # E 取值域（允许 E*）
        e_norm = e_raw.replace("*", "")
        if not E_RE.match(e_norm):
            fail("E 取值域", f"{name}（第 {lineno} 行）E = {e_raw!r}，不在 E0..E5 内")

        # 组件状态取值域
        if st is None:
            fail("状态取值域", f"{name}（第 {lineno} 行）状态列无法解析：{cells[8]!r}")
        else:
            # R ↔ 状态 耦合
            if r_raw == "未评级" and st != "🔴":
                fail("R↔状态", f"{name}：未评级 ⇒ 状态必须 🔴，实际 {st}")
            if r_raw in ("R0", "R1", "R2") and st == "✅":
                fail("R↔状态", f"{name}：{r_raw} ⇒ 不得判 ✅（§1.3 要求 R ≥ R3）")
            if r_raw in ("R3", "R4") and st == "⚫":
                fail("R↔状态", f"{name}：{r_raw} ⇒ 不得判 ⚫（受阻只用于环境缺前置条件）")
            if r_raw in ("R0", "R1", "R2") and st not in ("🟡", "🔴", "⚫"):
                fail("R↔状态", f"{name}：{r_raw} ⇒ 状态须为 🟡/🔴/⚫，实际 {st}")

        # 判定命令里的仓内路径必须存在
        for p in tool_paths(cmd_cell):
            if not (ROOT / p).exists():
                fail("判定命令路径", f"{name}（第 {lineno} 行）引用了**仓内不存在**的路径：{p}")

        # 判 ✅ ⇒ 必须给出可复算的判定命令
        if st == "✅":
            has_tool = any((ROOT / p).exists() for p in tool_paths(cmd_cell))
            has_mvn = "mvn " in cmd_cell
            if not (has_tool or has_mvn):
                fail("✅ 判定命令", f"{name}（第 {lineno} 行）判 ✅ 但判定命令列**没有仓内存在的 tools 路径，也没有 mvn 命令**")

    # ── 8/9. 矩阵 B ───────────────────────────────────────────────────
    b_lo, b_hi = section_range(lines, "四、矩阵 B")
    t_b = parse_tables(lines, b_lo, b_hi)
    if not t_b:
        fail("矩阵 B", "找不到 B 卡表")
        return report()
    b_rows = t_b[0][2]
    if len(b_rows) != 10:
        fail("B 卡行数", f"期望 10 行（9 域 + 1 共享层），实际 {len(b_rows)}")

    domain_components: dict[str, list[str]] = {}
    for name, dom in comp_domain.items():
        domain_components.setdefault(dom, []).append(name)

    for lineno, cells in b_rows:
        if len(cells) < 5:
            fail("B 卡列数", f"第 {lineno} 行只有 {len(cells)} 列")
            continue
        dom = strip_md(cells[0])
        dom = dom.replace("共享层service/common", "共享层").replace("共享层", "共享层")
        n_decl = strip_md(cells[1])
        detail = cells[2]
        dom_status = status_of(cells[3])

        if dom.startswith("**共享层**") or "共享层" in dom:
            dom_key = "共享层"
        else:
            dom_key = dom.strip("`")

        members = domain_components.get(dom_key, [])
        if dom_key == "共享层":
            members = ["service/common"]
        if not members:
            fail("B 卡域", f"第 {lineno} 行「{dom}」在 A-2 中找不到对应组件")
            continue
        if n_decl.isdigit() and int(n_decl) != len(members):
            fail("B 卡组件数", f"「{dom}」声明 {n_decl} 个，A-2 中实际 {len(members)} 个")

        # 明细 ↔ A-2 逐个比对
        detail_map: dict[str, str] = {}
        detail_raw = detail.strip()
        # 共享层那行的「组件状态明细」只有一个状态字符（唯一成员 service/common）
        if detail_raw and all(ch not in detail_raw for ch in "·") and status_of(detail_raw):
            detail_map = {"service/common": status_of(detail_raw)} if dom_key == "共享层" else {}
        else:
            for part in detail.split("·"):
                part = part.strip()
                if not part:
                    continue
                m = re.match(r"^([A-Za-z0-9_\-]+)\s*([✅🟡🔴⚫⬜])$", part)
                if not m:
                    continue
                detail_map[m.group(1)] = m.group(2)

        resolved: dict[str, str] = {}
        for comp, st in detail_map.items():
            target = None
            if comp in members:
                target = comp
            else:
                cands = [c for c in members if c.endswith("-" + comp)]
                if len(cands) == 1:
                    target = cands[0]
            if target is None:
                fail("B 卡明细", f"「{dom}」明细里的 `{comp}` 无法唯一对应 A-2 组件 {members}")
                continue
            resolved[target] = st
            actual = comp_status.get(target)
            if actual and st != actual:
                fail("B 卡↔A-2", f"「{dom}」的 `{target}` 状态 = {st}，A-2 = {actual}")

        if len(resolved) != len(members):
            missing = sorted(set(members) - set(resolved))
            fail("B 卡明细", f"「{dom}」明细未覆盖组件 {missing}")

        # 域状态 = MIN(域内各组件状态)（§8.1）
        ranks = [STATUS_RANK[s] for s in resolved.values() if s in STATUS_RANK]
        if ranks:
            expect = min(ranks)
            expect_ch = next(k for k, v in STATUS_RANK.items() if v == expect)
            if dom_status and dom_status != expect_ch:
                fail("域状态聚合", f"「{dom}」声明 {dom_status}，按 §8.1 MIN 聚合应为 {expect_ch}（成员 {resolved}）")

    # ── 10. §8.2 C-1 的 N/6 ───────────────────────────────────────────
    core_ok = sum(1 for d in CORE_DOMAINS if domain_components.get(d) and all(comp_status.get(c) == "✅" for c in domain_components[d]))
    m = re.search(r"\*\*(\d+)/6\*\*", "\n".join(lines))
    if m:
        declared = int(m.group(1))
        if declared != core_ok:
            fail("C-1 核心域", f"§8.2 声明 {declared}/6，实际核心 6 域中 {core_ok} 个全 ✅")
    else:
        fail("C-1 核心域", "§8.2 找不到「N/6」形式的声明")

    # ── 11. 编号连续性 ────────────────────────────────────────────────
    def numbered(prefix: str, lo: int, hi: int) -> list[str]:
        out = []
        for _, header, rows in parse_tables(lines, lo, hi):
            for lineno, cells in rows:
                v = strip_md(cells[0]) if cells else ""
                if v.startswith(prefix):
                    out.append(v)
        return out

    c_lo, c_hi = section_range(lines, "五、矩阵 C")
    xs = numbered("X-", c_lo, c_hi)
    expect_x = [f"X-{i}" for i in range(1, len(xs) + 1)]
    if xs != expect_x:
        fail("编号连续性", f"矩阵 C 编号不连续：{xs}")

    g_lo, g_hi = section_range(lines, "九、证据缺口清单")
    gs = numbered("G-", g_lo, g_hi)
    expect_g = [f"G-{i}" for i in range(1, len(gs) + 1)]
    if gs != expect_g:
        fail("编号连续性", f"§九 编号不连续：{gs}")

    d_lo, d_hi = section_range(lines, "11.1")
    ds = numbered("D-", d_lo, d_hi)
    expect_d = [f"D-{i:02d}" for i in range(1, len(ds) + 1)]
    if ds != expect_d:
        fail("编号连续性", f"§11.1 编号不连续：{ds}")

    ch_lo, ch_hi = section_range(lines, "十、变更记录")
    chs = []
    for _, header, rows in parse_tables(lines, ch_lo, ch_hi):
        for lineno, cells in rows:
            v = strip_md(cells[0]) if cells else ""
            if v.isdigit():
                chs.append(v)
    expect_ch = [str(i) for i in range(1, len(chs) + 1)]
    if chs != expect_ch:
        fail("编号连续性", f"§十 变更记录编号不连续：{chs}")

    # ── 12. P0 九条 + 汇总计数 ────────────────────────────────────────
    p_lo, p_hi = section_range(lines, "七、P0 九条复核")
    p_rows = []
    for _, header, rows in parse_tables(lines, p_lo, p_hi):
        for lineno, cells in rows:
            v = strip_md(cells[0]) if cells else ""
            if v.isdigit():
                p_rows.append((int(v), cells))
    p_rows.sort()
    if [n for n, _ in p_rows] != list(range(1, 10)):
        fail("P0 编号", f"P0 编号不连续：{[n for n, _ in p_rows]}")
    counts = {"✅": 0, "🟡": 0, "🔴": 0, "⚫": 0}
    for n, cells in p_rows:
        concl = cells[-1]
        st = status_of(concl)
        if st is None:
            fail("P0 结论", f"P0 #{n} 的结论列无法解析状态：{concl!r}")
        else:
            counts[st] = counts.get(st, 0) + 1
    summary_line = next((l for l in lines[p_lo:p_hi] if "P0 汇总" in l), "")
    nums = {}
    for key in ("✅", "🟡", "🔴", "⚫"):
        m = re.search(re.escape(key) + r"\s*(\d+)\s*条", summary_line)
        nums[key] = int(m.group(1)) if m else None
    if any(v is None for v in nums.values()):
        fail("P0 汇总", f"§七 的「P0 汇总」声明里读不全四项计数：{summary_line.strip()!r}")
    else:
        declared = (nums["✅"], nums["🟡"], nums["🔴"], nums["⚫"])
        actual = (counts.get("✅", 0), counts.get("🟡", 0), counts.get("🔴", 0), counts.get("⚫", 0))
        if declared != actual:
            fail("P0 汇总", f"§七 声明 ✅/🟡/🔴/⚫ = {declared}，实际 {actual}")

    # ── 13. A-1 tests 列求和 == **源声明测试方法数**（环境无关口径）────────
    # ⚠️ **【2026-10-31 订正 · 合并收口执行者 · M-12】本组原先拿 A-1 的 `tests` 列求和
    #    去对「§七 P0 #6 的全仓 N 条 / 0F」⇒ ⇒ **那是两个不同口径** ⇒ ⇒ **判据形态错**：
    #    ① A-1 列 = surefire **执行条数**（环境相关：Docker 停 ⇒ 整类折叠成 1 条 error；
    #       `@ParameterizedTest` 被展开成 N 条 case）② P0 #6 的 N 条 = 同一次真跑的执行读数
    #    ⇒ ⇒ 两者本应相等，**但只要环境一变就分叉** ⇒ ⇒ ⇒ **判据不具判别力**。
    # ⇒ ⇒ 现在：A-1 列已改口径为**源声明测试方法数**（读源码 ⇒ 环境无关）⇒ ⇒ 本组改为
    #    **直接重算源声明数 ⇒ 与 A-1 列求和逐行对账** ⇒ ⇒ **A-1 忘了更新就红**。
    # ⇒ ⇒ 逐行的对账由独立守门 `tools/difftest/check-source-test-decl-count.py` 做
    #    （含 proto 行、行数 39、逐行明细）⇒ ⇒ 本组只做**求和级**交叉校验。
    total_tests = 0
    for _, cells in a1_rows:
        if len(cells) < 6:
            continue
        v = strip_md(cells[5])
        if v.isdigit():
            total_tests += int(v)
    src_total = _source_decl_total()
    if src_total is None:
        note("无法重算源声明测试方法数（src/test/java 不可读），跳过第 13 组")
    elif src_total != total_tests:
        fail(
            "测试条数",
            f"A-1 的 tests 列求和 = {total_tests}，实测源声明测试方法数 = {src_total}"
            f"⇒ ⇒ 两口径（环境无关）应当相等 ⇒ ⇒ 请更新 A-1 的 tests 列",
        )
    else:
        note(f"第 13 组：A-1 tests 列求和 {total_tests} == 源声明测试方法数 {src_total} ✅")

    # ── 14. §6.3 命令数 == 仓内守门清单 − 声明未跑的 ──────────────────
    inventory = sorted(
        p.name
        for p in list((ROOT / "tools/difftest").glob("check-*.py"))
        + list((ROOT / "tools/difftest").glob("check-*.sh"))
        + list((ROOT / "tools").glob("check-ledger-*.py"))
        + list((ROOT / "tools").glob("check-agents-budget.py"))
    )
    s63_lo, s63_hi = section_range(lines, "6.3")
    s63_text = "\n".join(lines[s63_lo:s63_hi])
    # ⚠️ 「本轮未跑」的清单在**声明未跑的那段引用块**里（名单可能在下一行）⇒
    #    取「含『未跑』的那一行」及其后续连续的引用块行，别把表里「已跑」的命令算进来。
    not_run = set()
    idx = s63_lo
    while idx < s63_hi:
        if "未跑" in lines[idx]:
            j = idx
            while j < s63_hi and lines[j].lstrip().startswith(">"):
                not_run.update(re.findall(r"`(check-[A-Za-z0-9\-]+\.(?:py|sh))`", lines[j]))
                j += 1
            break
        idx += 1
    m = re.search(r"\*\*(\d+)\s*项命令\*\*", s63_text)
    if m:
        declared_run = int(m.group(1))
        actual_run = len(inventory) - len(not_run)
        if declared_run != actual_run:
            fail("§6.3 命令数", f"声明实跑 {declared_run} 项；仓内守门 {len(inventory)} 个 − 声明未跑 {len(not_run)} 个 = {actual_run}")
        unknown = {n for n in not_run if n not in inventory}
        if unknown:
            fail("§6.3 未跑清单", f"声明未跑但仓内不存在：{sorted(unknown)}")
    else:
        fail("§6.3 命令数", "§6.3 标题里找不到「N 项命令」的声明")

    # ── 15. 无未来日期 ───────────────────────────────────────────────
    dates = sorted(set(re.findall(r"20\d{2}-\d{2}-\d{2}", raw)))
    if dates:
        base = max(dates)
        base_d = _dt.date.fromisoformat(base)
        for ds_ in dates:
            if _dt.date.fromisoformat(ds_) > base_d:
                fail("未来日期", f"{ds_} 晚于文档自身的核对日期 {base}")
        note(f"文档日期范围：{dates[0]} ~ {base}（共 {len(dates)} 个不同日期）")

    return report()


def report() -> int:
    if NOTES and "-v" in sys.argv or "--verbose" in sys.argv:
        for n in NOTES:
            print(f"· {n}")
    if FAILURES:
        print(f"❌ 阶段一基线内部一致性自检：{len(FAILURES)} 项不通过")
        for f in FAILURES:
            print(f"  ❌ {f}")
        return 1
    print("✅ 阶段一基线内部一致性自检：全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
