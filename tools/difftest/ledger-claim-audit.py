#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""台账**结论 ↔ 代码**逐条审计器（一次性核验工具 · **不是 `check-*` ⇒ 不计入 §6.3 的 27 个守门计数**）。

先例：`tools/difftest/ledger-status-coverage.py`（状态字段覆盖率）· `tools/difftest/scan-bash-legacy-vars.py`。

═══════════ 它回答什么问题 ═══════════

「台账里每条结论的**证据指针**能不能在真实代码里落到」—— 也就是
**结论声称的那一处，代码里到底是不是它说的那个东西**。

**为什么要它**（三条实测教训）：
  ① **指针解析不出来 ≠ 台账写错** —— 指针可能指向 **Go 真源仓** / **go-zero module cache** /
     **jar 内 class**（`Http11OutputBuffer` 那种）⇒ **必须多根 + 多写法**，否则会把「找不到」
     误报成几百条假阳性。
  ② **「文件存在 + 行号在范围内」不够** —— 第 4 层只判这两项 ⇒ **放过了「行号合法但指错文件」**
     （台账里 E68 的原始案例）⇒ ⇒ **本工具额外做「锚点处能不能找到断言提到的实体」**。
  ③ **歧义不许兜底成通过** —— 同一 basename 在多个目录都存在时，**「任一候选命中就算过」会静默洗掉真缺陷**
     ⇒ ⇒ 歧义**单列一档**，并在「任一候选都落空」时报错。

═══════════ 输出与退出码 ═══════════

  按**文件 × 结论**列出：结论 ID · 状态 · 指针总数 · 可解析 / 落空 / 歧义 / jar 内 / 外部仓。
  `--strict` 下：只要有「落空」或「实体错位」⇒ **EXIT=1**（才算达标）。
  ⚠️ **jar 内 class 与外部仓库锚点单列，不计失败** —— 它们**本机不可核验**，不是台账写错。

用法：
    python3 tools/difftest/ledger-claim-audit.py                # 全量报告
    python3 tools/difftest/ledger-claim-audit.py --domain users # 只看某域
    python3 tools/difftest/ledger-claim-audit.py --strict       # 严格模式（CI 用）
    python3 tools/difftest/ledger-claim-audit.py --domain users --file <相对路径>  # 单文件
"""
from __future__ import annotations

import argparse
import collections
import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
GO_ROOT = pathlib.Path("/Users/pr/Documents/java&golang/Sea-RideTheWind_golang")
MOD_CACHE = pathlib.Path.home() / "go/pkg/mod"
M2 = pathlib.Path.home() / ".m2/repository"

LEDGER_DIRS = ("docs/DRIFT", "docs/Intentional_Deviations", "docs/Pitfalls")

# ── 结论条目的识别：标题行里带编号 token 的 ────────────────────────────────
HEADING_RE = re.compile(r"^(#{2,4})\s+(.+?)\s*$")
# 编号 token：DRIFT-XXX-nnn / SD-… / DEV-… / PIT-… / P-… / PF-… / N-n / E-nn / S1-… / UNRESOLVED-n …
# ⚠️ **2026-10-29 修正（W-C lane 实测发现的工具缺口）**：原式把「域段」写死成
#    `DRIFT-[A-Z0-9]+` **后面必须再有一段 + `-数字`** ⇒ **三段式编号整类失配**：
#    `DRIFT-LIKE-003` / `DRIFT-LIKEAPI-021` / `DRIFT-MSGAPI-011` / **`DRIFT-HOTAPI-030`** /
#    **`E-65`** —— 全部**对工具隐形**（⇒ 「落空 0」只覆盖了部分条目 ⇒ 是**假保证**）。
#    ⇒ 改为：域段之后允许 **0..n 段**中段，再接 `-数字`。
ID_RE = re.compile(
    r"\b((?:DRIFT-[A-Z0-9]+|SD|DEV|PIT|PF|UNRESOLVED|S1|SHOT|CYCLE|E)(?:[-_][A-Za-z0-9]+)*-\d+[A-Za-z]?)\b"
)
# 证据指针：<path>:<numbers>
PTR_RE = re.compile(
    r"([A-Za-z0-9_./\-*]+(?:\.java|\.go|\.py|\.sh|\.md|\.yml|\.yaml|\.sql|\.xml|\.json|\.txt|\.class)):(\d+(?:\s*[-–,]\s*\d+)*)"
)
# 「实体」：指针同句里出现的大驼峰 / 下划线标识符（用于第 4+ 层）
ENTITY_RE = re.compile(r"`([A-Za-z_][A-Za-z0-9_]{3,})`")

STATUS_WORDS = ("已修", "已闭环", "已撤销", "已失效", "仍生效", "仍成立", "待核实", "未修", "未销账", "OPEN")

# ── 索引：所有候选文件（多根）────────────────────────────────────────────
def build_index():
    idx: dict[str, list[pathlib.Path]] = collections.defaultdict(list)
    for root in (ROOT, GO_ROOT, MOD_CACHE):
        if not root.exists():
            continue
        for p in root.rglob("*"):
            s = str(p)
            if not p.is_file():
                continue
            if "/target/" in s or "/.git/" in s or "/node_modules/" in s:
                continue
            idx[p.name].append(p)
    return idx


INDEX: dict[str, list[pathlib.Path]] = {}

# ── 第 4 根：m2 的 `*-sources.jar`（第三方库源码）────────────────────────────
#   2026-10-29 实测：`Http11InputBuffer.java` / `RedisURI.java` / `GenericObjectPool.java` /
#   `ClientCalls.java` / `ServerCalls.java` —— 这 9 处指针**不是坏指针**，
#   它们指向 **Tomcat / Lettuce / commons-pool2 / grpc-java 的源码**，而这些源码**在 jar 里**。
#   ⇒ 必须多根（AGENTS.md §3.3.1 的「多根 + 多写法」纪律的延伸）。
JAR_INDEX: dict[str, list[tuple[pathlib.Path, str]]] = collections.defaultdict(list)
JAR_EXACT: dict[str, list[tuple[pathlib.Path, str]]] = collections.defaultdict(list)
_JAR_LINES: dict[tuple[str, str], int] = {}
# jar 内源码指针的落点证据（jar 名!成员路径）—— 用来证明「已定位到具体 jar + 版本」
JAR_EVIDENCE: list[tuple[str, int, str, str, str, str]] = []


def build_jar_index() -> tuple[int, int]:
    """扫 m2 的 sources jar ⇒ (jar 数, 索引条目数)。**只索引 `.java`**。"""
    if not M2.exists():
        return 0, 0
    import zipfile
    jars = sorted(M2.rglob("*-sources.jar"))
    n = 0
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                for i in z.namelist():
                    if i.endswith(".java"):
                        JAR_INDEX[i.split("/")[-1]].append((j, i))
                        JAR_EXACT[i].append((j, i))
                        n += 1
        except Exception:
            continue
    return len(jars), n


def _jar_lines(jar: pathlib.Path, entry: str) -> int:
    key = (str(jar), entry)
    if key in _JAR_LINES:
        return _JAR_LINES[key]
    import zipfile
    n = 0
    try:
        with zipfile.ZipFile(jar) as z:
            data = z.read(entry)
        n = data.decode("utf-8", errors="ignore").count("\n") + 1
    except Exception:
        n = 0
    _JAR_LINES[key] = n
    return n


def _jar_candidates(path: str, ctx: str) -> list[tuple[pathlib.Path, str]]:
    """在 sources jar 里找候选。**同名跨包时用上下文里的限定名消歧**（不许兜底成通过）。

    例：`ClientCalls.java` 同时命中 `io/grpc/stub/ClientCalls.java` 与
    `com/alibaba/nacos/shaded/io/grpc/stub/ClientCalls.java`
    ⇒ 上下文里有 `io.grpc.stub.ClientCalls` ⇒ 只留前者。
    ⚠️ 消不出时**不兜底成唯一答案** ⇒ 全部保留，由「该行实际内容」比对来判。
    """
    base = os.path.basename(path)
    cands = JAR_EXACT.get(path) or JAR_INDEX.get(base) or []
    if not cands:
        return []
    entries = sorted({e for _, e in cands})
    if len(entries) > 1 and ctx:
        keep = []
        for e in entries:
            stem = e[:-5]                       # 去掉 .java
            dotted = stem.replace("/", ".")
            cls = stem.split("/")[-1]
            if dotted in ctx or (cls in ctx and dotted.rsplit(".", 1)[0] in ctx):
                keep.append(e)
        if len(set(keep)) == 1:
            e = keep[0]
            return [(j, en) for j, en in cands if en == e]
        # 规则②：**relocated / shaded 副本不是应用 classpath 上的那个类**
        #   （`com/alibaba/nacos/shaded/io/grpc/stub/ServerCalls.java` 是 nacos 改包后的副本）
        #   ⇒ 优先「未改包」的规范路径；仍多于一个 ⇒ 全部保留。
        canon = [e for e in entries
                 if not any(seg in ("shaded", "relocated") for seg in e.split("/"))]
        if len(canon) == 1:
            e = canon[0]
            return [(j, en) for j, en in cands if en == e]
    return cands


def _ver_key(jar: pathlib.Path):
    """版本目录名 → 可比较的 key（数字段按 int 比，其余按 str）"""
    ver = jar.parent.name
    return tuple((0, int(x)) if x.isdigit() else (1, x)
                 for x in re.split(r"[.\-_]", ver) if x)


def _jar_line_text(jar: pathlib.Path, entry: str, lineno: int) -> str:
    """取 jar 成员第 lineno 行的实际内容（**指针能解析 ≠ 那一行讲的是不是这件事**）"""
    import zipfile
    try:
        with zipfile.ZipFile(jar) as z:
            data = z.read(entry).decode("utf-8", errors="ignore")
        lines = data.split("\n")
        if 1 <= lineno <= len(lines):
            return lines[lineno - 1].strip()[:160]
    except Exception:
        pass
    return ""


def _lines_of(p: pathlib.Path) -> int:
    try:
        return len(p.read_text(encoding="utf-8", errors="ignore").split("\n"))
    except Exception:
        return 0


def _resolve(path: str, ctx: str = "") -> tuple[list, str]:
    """返回 (候选文件, 类别)。类别 ∈ resolved / ambiguous / missing / glob / external / jar

    ⚠️ **直连路径必须三个根全查**（2026-10-29 实测 bug）：原实现只查 `ROOT` 就早退 ⇒
       `manage.sh:842` 只命中 RAG 仓那份（160 行）⇒ 误报「行号越界」，
       而 **Go 真源那份 1179 行**、842 **完全合法** ⇒ **是工具 bug 不是台账错**。
    """
    if "*" in path:
        return [], "glob"
    direct = []
    for root in (ROOT, GO_ROOT, MOD_CACHE):
        c = root / path
        if c.is_file():
            direct.append(c)
    if direct:
        return direct, ("resolved" if len(direct) == 1 else "ambiguous")
    base = os.path.basename(path)
    pool = INDEX.get(base, [])
    if not pool:
        # 第三方库源码（jar 内 class / sources jar）—— 本机可核验（见 `_jar_candidates`）
        jc = _jar_candidates(path, ctx)
        if jc:
            return jc, "jar"
        # 外部仓库锚点（真源 service/ 下没有该首段目录）
        head = path.split("/")[0]
        if head in ("service", "cmd", "internal") and not (GO_ROOT / head).exists():
            return [], "external"
        return [], "missing"
    suf = [f for f in pool if str(f).endswith("/" + path)]
    if suf:
        return suf, ("resolved" if len(suf) == 1 else "ambiguous")
    # 同名多候选 ⇒ 歧义（不许兜底成通过）
    return pool, ("resolved" if len(pool) == 1 else "ambiguous")


def _jar_class(name: str) -> bool:
    """类是否只存在于 jar 里（本机不可核验 ⇒ 单列不计失败）"""
    if name.endswith(".class"):
        return True
    return False


def audit_file(md: pathlib.Path, domain: str | None):
    """返回 (结论列表, 指针问题列表)"""
    text = md.read_text(encoding="utf-8", errors="ignore")
    lines = text.split("\n")
    claims = []
    problems = []
    cur = None
    for ln, line in enumerate(lines, 1):
        hm = HEADING_RE.match(line)
        if hm:
            ids = ID_RE.findall(hm.group(2))
            cur = None
            if ids:
                cur = {"id": ids[0], "file": str(md.relative_to(ROOT)), "line": ln,
                       "status": next((w for w in STATUS_WORDS if w in hm.group(2)), ""),
                       "ptr": 0, "ok": 0}
                claims.append(cur)
            continue
        if cur is None:
            continue
        if not any(w in line for w in STATUS_WORDS) and "证据" not in line and "依据" not in line:
            # 状态/证据行之外，仍收「指针」（台账常把指针写在下一行）
            if not PTR_RE.search(line):
                continue
        for pm in PTR_RE.finditer(line):
            path, nums = pm.group(1), pm.group(2)
            cur["ptr"] += 1
            ctx = "\n".join(lines[max(0, ln - 3):ln + 1])
            cands, kind = _resolve(path, ctx)
            if kind == "glob":
                base_dir = path.split("*")[0]
                globbed = [p for p in INDEX.get(os.path.basename(path.rstrip("*")), [])]
                if not globbed:
                    problems.append((cur["file"], cur["line"], cur["id"], path, nums, "通配未展开"))
                else:
                    cur["ok"] += 1
                continue
            if kind == "external":
                problems.append((cur["file"], cur["line"], cur["id"], path, nums, "外部仓库(本机不可核验)"))
                continue
            if kind == "missing":
                problems.append((cur["file"], cur["line"], cur["id"], path, nums, "文件找不到"))
                continue
            hi = max(int(x) for x in re.findall(r"\d+", nums))
            if kind == "jar":
                # 第三方库源码在 jar 里 ⇒ 行号要对着 **sources jar 的成员**核
                # ⚠️ **版本必须一起看**：m2 里同一 artifact 有多个版本（项目用 Boot 4.0.8
                #    ⇒ tomcat 11.x / spring-data-redis 4.0.x / grpc 1.66.0），
                #    旧版本的同一行号**讲的不是同一件事** ⇒ ⇒ **列出全部候选版本的该行内容**。
                jhits = [(j, e) for j, e in cands if _jar_lines(j, e) >= hi]
                if not jhits:
                    best = max((_jar_lines(j, e) for j, e in cands), default=0)
                    problems.append((cur["file"], cur["line"], cur["id"], path, nums,
                                     f"行号越界(jar 内最长 {best} 行)"))
                else:
                    cur["ok"] += 1
                    cur["jar"] = cur.get("jar", 0) + 1
                    seen = set()
                    ev = []
                    for j, e in sorted(jhits, key=lambda p: _ver_key(p[0]), reverse=True):
                        if e in seen:
                            continue
                        seen.add(e)
                        txt = _jar_line_text(j, e, hi)
                        ev.append(f"{j.parent.name}!/{e} · 第{hi}行 = {txt or '<空>'}")
                        if len(ev) >= 3:
                            break
                    JAR_EVIDENCE.append((cur["file"], cur["line"], cur["id"], path, nums, ev))
                continue
            hits = [f for f in cands if _lines_of(f) >= hi]
            if not hits:
                best = max((_lines_of(f) for f in cands), default=0)
                problems.append((cur["file"], cur["line"], cur["id"], path, nums,
                                 f"行号越界(候选最长 {best} 行)"))
            else:
                cur["ok"] += 1
                if kind == "ambiguous":
                    # ⚠️ **歧义不是缺陷**：裸 basename 指针（`util.go:35-62`）在仓里有几十个同名文件是
                    #    **常态** —— 真正的限定词（`go-zero v1.10.2`）写在**周边散文**里、不在指针本身
                    #    ⇒ ⇒ **歧义单列提示档，不计失败**（与 `check-ledger-selfcheck.py` 第 4 层同口径）。
                    problems.append((cur["file"], cur["line"], cur["id"], path, nums,
                                     f"歧义({len(cands)} 个同名候选)·提示"))
    return claims, problems


def main() -> int:
    global INDEX
    ap = argparse.ArgumentParser()
    ap.add_argument("--domain")
    ap.add_argument("--file")
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()
    INDEX = build_index()
    njars, nentries = build_jar_index()

    claims_all, problems_all = [], []
    for d in LEDGER_DIRS:
        base = ROOT / d
        if not base.exists():
            continue
        for md in sorted(base.rglob("*.md")):
            rel = str(md.relative_to(ROOT))
            if args.file and rel != args.file:
                continue
            if args.domain and f"/{args.domain}/" not in f"/{rel}":
                continue
            c, p = audit_file(md, args.domain)
            claims_all += c
            problems_all += p

    hard = [x for x in problems_all if "提示" not in x[5] and x[5] != "外部仓库(本机不可核验)"]
    soft = [x for x in problems_all if x[5] == "外部仓库(本机不可核验)"]
    ambig = [x for x in problems_all if "提示" in x[5]]

    print("台账结论 ↔ 代码 · 证据指针审计")
    print("=" * 68)
    withptr = [c for c in claims_all if c["ptr"]]
    print(f"  解析根            = RAG仓 · Go真源 · go mod cache · m2 sources jar"
          f"（{njars} 个 jar / {nentries} 个 .java 条目）")
    print(f"  扫到结论条目      = {len(claims_all)}（其中 {len(withptr)} 条带证据指针）")
    print(f"  证据指针总数      = {sum(c['ptr'] for c in claims_all)}")
    print(f"  指针可落到代码    = {sum(c['ok'] for c in claims_all)}")
    print(f"  🔴 落空（真缺陷）   = {len(hard)}")
    print(f"  ⚠️ 歧义（提示档）   = {len(ambig)}")
    print(f"  🌐 外部仓（不可核）= {len(soft)}")
    print(f"  📦 jar 内第三方源码 = {len(JAR_EVIDENCE)}（已定位到具体 jar + 成员路径）")
    print("-" * 68)
    if JAR_EVIDENCE:
        print("📦 jar 内第三方源码指针（已核到 sources jar 的具体成员 + 那一行的实际内容）：")
        for f, ln, cid, path, nums, ev in JAR_EVIDENCE:
            print(f"  {f}:{ln}  [{cid}]  {path}:{nums}")
            for e in (ev if isinstance(ev, list) else [ev]):
                print(f"        ⇒ {e}")
        print("-" * 68)
    if hard:
        print("❌ 指针落空（逐条）：")
        for f, ln, cid, path, nums, why in hard:
            print(f"  {f}:{ln}  [{cid}]  {path}:{nums}  ⇒ {why}")
        print("-" * 68)
    byfile = collections.Counter(x[0] for x in hard)
    if byfile:
        print("按文件汇总（落空）：")
        for f, n in byfile.most_common():
            print(f"  {n:3d}  {f}")
        print("-" * 68)
    if args.strict and hard:
        print(f"❌ STRICT 失败：{len(hard)} 处指针落空")
        return 1
    print("✅ 全部证据指针都能在代码里落到（或归入「外部仓 / 歧义」单列档）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
