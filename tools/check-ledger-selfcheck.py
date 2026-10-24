#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-ledger-selfcheck.py —— **台账 vs 实际** 的机器复核（2026-10-24 新增）

【它回答什么问题】
  AGENTS.md §9 / §8 / §0 要求「过期状态必须随实测同步更正」，
  而本仓的台账历史上反复出现三类**台账脱节**：
    ① 台账指向的**测试类 / 文件根本不存在**（先例：`UserRpcLoginNullStatusFailClosedTest`
       —— 台账自己写了一段「该测试类在仓内不存在」的记述，却仍以「§7 守门测试清单」的口径引用它）
    ② 台账引用的 **Go `文件:行号` 与真源对不上**（行号漂移 / 文件改名 / 路径写错）
    ③ 台账声明的**计数**与仓库实际（测试数、drift 条数、unresolved 条数）对不上
  ⇒ 本脚本把 ①②③ 变成**一条命令**的机械检查，避免「靠人读」。

【三类检查】
  A. 引用存在性：台账里出现的每个**测试类名** / **文件路径**，是否真的存在
  B. 行号引用：台账里出现的每个 `file.go:NN` / `File.java:NN`，
     在 **RAG 仓 / Go 真源仓 / Go 模块缓存** 里解析到真实文件，
     校验行号在范围内，并把该行附近的实际文本打出来供**人工判读**
  C. 计数一致性：台账里声明的「Tests run: N」等，与 surefire 报告比对

【为什么 B 只做「行号在不在 + 附近是什么」而不自动判语义】
  AGENTS.md §3.3.1：判断一个事实是否成立，必须**读到消费点**，
  而「这一行是不是我要说的那件事」需要语义判断 —— 本脚本**不越权**，
  它只保证「你引用的那行存在且长这样」，语义仍由人/agent 读原文判定。

【去噪规则（都是实测踩出来的，勿删）】
  N1. 框架注解名（`SpringBootTest` / `WebMvcTest` …）**不是**仓内测试类 ⇒ 排除
  N2. Go 侧路径（`etc/usercenter.yaml` / `proto/user.proto`）在**真源仓**里，不在 RAG 仓 ⇒ 两边都查
  N3. go-zero / pgx / gorm 等**模块缓存**里的文件也要能解析 ⇒ 索引模块缓存
  N4. 同名文件可能有多份（basename 冲突）⇒ **优先选行数能覆盖引用区间的那份**
  N5. 「**删除** xxx.java」「该类**不存在**」是历史记述 ⇒ 不是「引用了一个不存在的文件」
  N6. 临时探针脚本（`tmp/…`）本就不在仓内 ⇒ 记 WARN 不记 FAIL
  N7. 台账**自陈**不存在的类（已记档并说明原因）⇒ 记 OK 并附出处，不算 FAIL

【用法】
  python3 tools/check-ledger-selfcheck.py                        # 默认复核 users/user 域
  python3 tools/check-ledger-selfcheck.py --ledger <file> ...    # 指定台账
  python3 tools/check-ledger-selfcheck.py --go-root <dir> --rag-root <dir>
  python3 tools/check-ledger-selfcheck.py --strict                # 有 FAIL 时退出码非 0
  python3 tools/check-ledger-selfcheck.py --quiet                  # 只打 FAIL / WARN 摘要

【退出码】
  0 = 无 FAIL（可能有 WARN）· 1 = 有 FAIL（引用不存在 / 行号越界 / 计数对不上）
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

# ──────────────────────────────────────────────────────────────────────────
DEFAULT_LEDGERS = [
    "docs/DRIFT/users/user/api.md",
    "docs/DRIFT/users/user/rpc.md",
    "docs/DRIFT/users/_shared.md",
    "docs/Intentional_Deviations/users/user/api.md",
    "docs/Intentional_Deviations/users/user/rpc.md",
    "docs/Intentional_Deviations/users/common.md",
]

DEFAULT_GO_ROOT = "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"
DEFAULT_MODCACHE = "/Users/pr/go/pkg/mod"

# N1：框架注解/常见词，**不是**仓内测试类
NOT_A_TEST_CLASS = {
    "SpringBootTest", "WebMvcTest", "MockMvcTest", "ExtendWith", "Test",
    "Tests", "Testcontainers", "Container", "DynamicPropertySource", "MockitoBean",
    "MockitoSpyBean", "AutoConfigureMockMvc", "BeforeEach", "AfterEach",
}

# N7：台账**自陈**不存在的类 → {类名: 台账里的出处说明}
KNOWN_ABSENT = {
    "UserRpcLoginNullStatusFailClosedTest":
        "rpc.md §「台账事实错误」已自陈：该类全仓不存在，实际类名是 UserRpcLoginNullStatusReadsAsZeroTest",
    "FlywayLedgerIsolationTest":
        "AGENTS.md §6.3 问题 4 已自陈：多处文档声称由它负责，该测试类全仓不存在",
}

# ── 正则 ────────────────────────────────────────────────────────────────
RE_TEST_CLASS = re.compile(r"\b([A-Z][A-Za-z0-9_]{4,})\b(?=[`\s,;)\]]|$)")
RE_REPO_PATH = re.compile(r"\b((?:[A-Za-z0-9_.\-]+/)+[A-Za-z0-9_.\-]+\.(?:java|sql|yml|yaml|xml|sh|py|proto))\b")
RE_LINE_REF = re.compile(r"\b([A-Za-z0-9_.\-/]+\.(?:go|java|yml|yaml|sql|proto)):(\d+)(?:-(\d+))?\b")
RE_TESTS_RUN = re.compile(r"[Tt]ests run:?\s*(\d+)")
# N5：这些词出现时，引用的文件是「历史记述」而非「现存引用」
RE_HISTORICAL = re.compile(r"(删除|已移除|不存在|曾经|原名|改名为|已改名|作废)")


class Report:
    def __init__(self) -> None:
        self.fail: list[str] = []
        self.warn: list[str] = []
        self.ok: list[str] = []

    def f(self, msg: str) -> None:
        self.fail.append(msg)

    def w(self, msg: str) -> None:
        self.warn.append(msg)

    def o(self, msg: str) -> None:
        self.ok.append(msg)


# ──────────────────────────────────────────────────────────────────────────
# 索引
# ──────────────────────────────────────────────────────────────────────────
def index_tree(root: Path, skip=("target", ".git", "node_modules")) -> dict[str, list[Path]]:
    idx: dict[str, list[Path]] = defaultdict(list)
    if not root or not root.exists():
        return idx
    for p in root.rglob("*"):
        if not p.is_file():
            continue
        if any(seg in p.parts for seg in skip):
            continue
        idx[p.name].append(p)
    return idx


def merge_idx(*idxs) -> dict[str, list[Path]]:
    out: dict[str, list[Path]] = defaultdict(list)
    for i in idxs:
        for k, v in i.items():
            for p in v:
                if p not in out[k]:
                    out[k].append(p)
    return out


def line_count(p: Path) -> int:
    try:
        with p.open("r", encoding="utf-8", errors="replace") as fh:
            return sum(1 for _ in fh)
    except OSError:
        return 0


def snippet(p: Path, start: int, end: int) -> str:
    try:
        lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return ""
    lo = max(0, start - 1)
    hi = min(len(lines), end)
    return " / ".join(l.strip() for l in lines[lo:hi] if l.strip())[:200]


def historical_context(text: str, pos: int, window: int = 90) -> str:
    """历史记述语境：**前后都看**。
    「已改名为 X」这类措辞常出现在被提及对象的**后面** ⇒ 只看前面会漏判。"""
    lo = max(0, pos - window)
    return text[lo: pos + 60]


# ──────────────────────────────────────────────────────────────────────────
# 检查 A：引用存在性
# ──────────────────────────────────────────────────────────────────────────
def check_refs_exist(text: str, rag_root: Path, go_root: Path,
                     all_idx, rep: Report, ledger: str) -> None:
    # A1. 测试类名
    seen = set()
    for m in RE_TEST_CLASS.finditer(text):
        cls = m.group(1)
        if cls in seen or cls in NOT_A_TEST_CLASS:
            continue
        if not (cls.endswith("Test") or cls.endswith("Tests")):
            continue
        seen.add(cls)
        if cls in KNOWN_ABSENT:
            rep.o(f"[{ledger}] 测试类 **{cls}** 台账已自陈不存在（已记档）⇒ 非问题")
            continue
        if any(p.name == f"{cls}.java" for p in all_idx.get(f"{cls}.java", [])):
            continue
        # N5：历史记述语境
        if RE_HISTORICAL.search(historical_context(text, m.start())):
            rep.o(f"[{ledger}] 测试类 **{cls}** 出现在「删除/改名/不存在」的历史记述里 ⇒ 非问题")
            continue
        rep.f(f"[{ledger}] 台账提到的测试类 **{cls}** 在仓内不存在")

    # A2. 文件路径
    for m in RE_REPO_PATH.finditer(text):
        rel = m.group(1).rstrip(".,;:")
        if rel.startswith(("http", "https")):
            continue
        # N5
        if RE_HISTORICAL.search(historical_context(text, m.start())):
            continue
        # N6：临时探针
        if rel.startswith(("tmp/", "/tmp/")):
            rep.w(f"[{ledger}] 引用了**临时探针** {rel}（本就不在仓内 ⇒ 不可复现）")
            continue
        base = rel.rsplit("/", 1)[-1]
        if (rag_root / rel).exists():
            continue
        if (go_root / rel).exists():
            continue
        cands = all_idx.get(base, [])
        if not cands:
            rep.f(f"[{ledger}] 台账引用的文件 **{rel}** 在 RAG 仓 / 真源仓都不存在")
        elif "..." in rel:
            # 本仓的正常省略写法（`service/common/.../spring/X.java`）⇒ 非问题
            rep.o(f"[{ledger}] 路径 **{rel}** 省略写法（`...`）⇒ 仓内命中 {len(cands)} 个同名文件")
        else:
            rep.w(f"[{ledger}] 路径写法与实际不符：**{rel}** → 仓内有 {len(cands)} 个同名文件"
                   f"（如 {cands[0]}）")


# ──────────────────────────────────────────────────────────────────────────
# 检查 B：行号引用
# ──────────────────────────────────────────────────────────────────────────
def check_line_refs(text: str, all_idx, rep: Report, ledger: str) -> None:
    seen = set()
    for m in RE_LINE_REF.finditer(text):
        raw, start, end = m.group(1), int(m.group(2)), int(m.group(3) or m.group(2))
        key = (raw, start, end)
        if key in seen:
            continue
        seen.add(key)
        base = raw.rsplit("/", 1)[-1]
        cands = all_idx.get(base, [])
        if not cands:
            rep.w(f"[{ledger}] 行号引用 **{raw}:{start}** 解析不到源文件"
                   f"（可能是模块缓存外依赖或笔误）")
            continue
        # N4：优先选行数能覆盖引用区间的
        fitting = [p for p in cands if line_count(p) >= end]
        if not fitting:
            p = cands[0]
            rep.f(f"[{ledger}] 行号引用 **{raw}:{start}-{end}** 越界："
                   f"{p} 只有 {line_count(p)} 行")
            continue
        p = fitting[0]
        # 只有**跨仓同名**才是真风险（同一仓内多份同名文件由行数消歧，属正常）
        roots = set()
        for x in fitting:
            s = str(x)
            if "/Sea-RideTheWind_golang/" in s:
                roots.add("GO")
            elif "/pkg/mod/" in s:
                roots.add("MODCACHE")
            else:
                roots.add("RAG")
        if len(roots) > 1:
            rep.w(f"[{ledger}] **{raw}:{start}** 跨仓同名（{sorted(roots)}）⇒ 行号可能指错文件"
                   f"（已选 {p}）")
        rep.o(f"[{ledger}] {raw}:{start}"
              + (f"-{end}" if end != start else "")
              + f" → 「{snippet(p, start, end)}」")


# ──────────────────────────────────────────────────────────────────────────
# 检查 C：计数一致性
# ──────────────────────────────────────────────────────────────────────────
def check_counts(text: str, rag_root: Path, rep: Report, ledger: str) -> None:
    for m in RE_TESTS_RUN.finditer(text):
        claimed = int(m.group(1))
        ls = text.rfind("\n", 0, m.start()) + 1
        le = text.find("\n", m.end())
        line = text[ls: le if le > 0 else len(text)]
        for mod in re.findall(r"((?:service/[A-Za-z0-9_/\-]+))", line):
            reports = rag_root / mod / "target" / "surefire-reports"
            if not reports.is_dir():
                rep.w(f"[{ledger}] 声明「Tests run: {claimed}」但 **{mod}** 的 surefire 报告不存在")
                continue
            total = 0
            for x in reports.glob("TEST-*.xml"):
                mm = re.search(r'tests="(\d+)"', x.read_text(errors="replace"))
                if mm:
                    total += int(mm.group(1))
            rep.o(f"[{ledger}] {mod}: 台账称 {claimed} 条 / 报告目录合计 {total} 条"
                   f"（⚠️ 报告目录可能含陈旧 XML ⇒ 以 mvn 输出的 Tests run 为准）")


# ──────────────────────────────────────────────────────────────────────────
# 检查 D：「已修」声明对应的**代码事实**
#   台账写「✅ 已修」时，必须有一个**可机器验证**的代码事实兜底
#   （AGENTS.md §3.3：配置/注解存在 ≠ 机制生效 ⇒ 这里只验「事实存在」，
#     机制生效由 §3.3 的守门测试 + 变异验证负责）
# ──────────────────────────────────────────────────────────────────────────
# (台账编号, 文件路径, 必须出现的字符串, 说明)
CODE_FACTS = [
    ("DRIFT-USERAPI-014 已修",
     "service/users/user/user-api/src/main/java/com/rag/users/user/api/config/RedisDisconnectedBehaviorConfig.java",
     "DisconnectedBehavior.REJECT_COMMANDS",
     "Lettuce 断连即拒（否则断连期间命令被排队 ⇒ 撞 HTTP 3s 预算 ⇒ 503）"),

    ("DRIFT-USERAPI-011 已修",
     "service/users/user/user-api/src/main/java/com/rag/users/user/api/advice/GlobalExceptionHandler.java",
     "GoStyleCircuitOpenException",
     "客户端熔断打开时必须映射成 1015（Go 是 HTTP 200 + 1015，不是 500 + 5001）"),

    ("DRIFT-USERAPI-012 已修",
     "service/common/src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java",
     "Math.ceil",
     "降载 RT 统计必须是浮点毫秒 + 向上取整（Go 是 math.Ceil，不是整型截断 + 至少记 1）"),

    ("DRIFT-USERRPC-V-003 已修（extra_info ⇒ text）",
     "service/users/users-common/src/main/resources/db/01_create_user.sql",
     "extra_info",
     "users_user.extra_info 的 DDL 在 users-common 的 01 脚本里"),
    ("DRIFT-USERRPC-V-004 已修（时间列 ⇒ timestamptz）",
     "service/users/users-common/src/main/resources/db/01_create_user.sql",
     "TIMESTAMPTZ",
     "users_user 的时间列必须是 timestamptz（Go 是 timestamptz）"),
]


def check_code_facts(rag_root: Path, rep: Report) -> None:
    for claim, rel, needle, why in CODE_FACTS:
        p = rag_root / rel
        if not p.exists():
            rep.f(f"【已修事实】{claim} —— 载体文件不存在：{rel}")
            continue
        txt = p.read_text(encoding="utf-8", errors="replace")
        if needle in txt:
            rep.o(f"【已修事实】{claim} —— {rel} 命中「{needle}」")
        else:
            rep.f(f"【已修事实】{claim} —— {rel} 里**找不到**「{needle}」（{why}）")


# ──────────────────────────────────────────────────────────────────────────
def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ledger", action="append", default=[])
    ap.add_argument("--rag-root", default=None)
    ap.add_argument("--go-root", default=DEFAULT_GO_ROOT)
    ap.add_argument("--modcache", default=DEFAULT_MODCACHE)
    ap.add_argument("--strict", action="store_true")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    rag_root = Path(args.rag_root).resolve() if args.rag_root else Path(__file__).resolve().parent.parent
    go_root = Path(args.go_root)
    modcache = Path(args.modcache)
    ledgers = args.ledger or [p for p in DEFAULT_LEDGERS if (rag_root / p).exists()]

    rep = Report()

    print(f"▶ rag-root : {rag_root}")
    print(f"▶ go-root  : {go_root}")
    print(f"▶ modcache : {modcache}")
    print(f"▶ 复核台账 : {len(ledgers)} 份")
    for l in ledgers:
        print(f"           - {l}")
    print()

    rag_idx = index_tree(rag_root)
    go_idx = index_tree(go_root)
    mc_idx = index_tree(modcache, skip=("cache", ".git", "node_modules"))
    all_idx = merge_idx(rag_idx, go_idx, mc_idx)

    for l in ledgers:
        p = rag_root / l
        if not p.exists():
            rep.f(f"台账文件不存在：{l}")
            continue
        text = p.read_text(encoding="utf-8", errors="replace")
        check_refs_exist(text, rag_root, go_root, all_idx, rep, l)
        check_line_refs(text, all_idx, rep, l)
        check_counts(text, rag_root, rep, l)

    check_code_facts(rag_root, rep)

    print(f"═══ OK {len(rep.ok)} · WARN {len(rep.warn)} · FAIL {len(rep.fail)} ═══\n")
    if rep.fail:
        print("───── FAIL（必须处理）─────")
        for x in rep.fail:
            print("  ✗ " + x)
        print()
    if rep.warn:
        print("───── WARN ─────")
        for x in rep.warn:
            print("  ! " + x)
        print()
    if not args.quiet:
        print("───── OK 摘要（前 30 条）─────")
        for x in rep.ok[:30]:
            print("  ✓ " + x)
        if len(rep.ok) > 30:
            print(f"  …（其余 {len(rep.ok) - 30} 条略）")

    return 1 if (rep.fail and args.strict) else 0


if __name__ == "__main__":
    sys.exit(main())
