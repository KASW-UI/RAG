#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""审计 favorite 域 Java 源码里「以 Go 为断言措辞」的注释块是否带可核验的证据锚点。

政策出处：AGENTS.md §3.3.2 ——「任何以「Go: …」措辞出现的断言，必须满足 §3.3.1 的三条，
否则不得用该措辞，改成中性描述或直接删掉该断言」。
§3.3.1 要求：判断某个 Go 默认行为是否生效，必须读到**消费该 tag 的那段控制流**，
只贴 struct 定义行不算证据 ⇒ 断言必须能指到具体行。

本脚本把这条从「靠人读」变成「机器可验」。

--------------------------------------------------------------- 锚点怎么解析

锚点形如 `<路径片段>.go:<起>[-<止>]`。解析顺序：

  1. **Go 真源**（`Sea-RideTheWind_golang/`）—— 业务代码。
  2. **锁定版本的依赖** —— 真源 `go.mod` 里 pin 住的版本，在本机 module cache
     （`$(go env GOMODCACHE)`）里取对应目录。例如 go-zero v1.10.2 的
     `rest/handler/authhandler.go`。
     ★ 这条是关键：库里 `authhandler.go` 这类**裸文件名**不写全路径读者根本定位不到，
       而锚点定位不到 = 断言不可核验 = §3.3.2 的高危项。

--------------------------------------------------------------- 发现分类

门禁级（EXIT=1）：

  F1  缺锚点      断言块里一个 `*.go:NN` 都没有
  F2  锚点悬空    锚点里的 .go 文件在真源与锁定依赖里都找不到
  F3  行号越界    文件找到了，但锚点行号超出该文件行数（行号过期）
  F4  引用不符    块里 `<pre>` 引用的 Go 源码，落在锚点行段之外
                  （= 锚点指到了别的函数，是「锚点过期」最常见的形态）

提示级（EXIT=0，`--strict` 时也算失败）：

  I1  内联注释缺锚点   行注释里的 Go 断言（§3.3.2 字面只点名 javadoc / @DisplayName，
                       故只提示不拦）
  I2  锚点仅靠文件名定位  裸 basename 命中多个候选，读者无法唯一定位
  I3  外部库锚点未标版本   引用第三方库但没写版本，读者不知道按哪版核对

用法：
    python3 tools/assertion-audit/favorite/check-assertion-sources.py
    python3 tools/assertion-audit/favorite/check-assertion-sources.py --json
    python3 tools/assertion-audit/favorite/check-assertion-sources.py --strict

退出码：0 = 无门禁级发现；1 = 有门禁级发现；2 = 环境不满足（找不到真源 / 被扫目录）。
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# ---------------------------------------------------------------- 扫描范围

DEFAULT_SCAN_GLOBS = (
    "service/favorite/*/src/main/java",
    "service/favorite/*/src/test/java",
)

# ---------------------------------------------------------------- 断言措辞

# javadoc / @DisplayName 的广口径：任何拿 Go 真源当断言依据的措辞。
GO_ASSERTION_BROAD = re.compile(
    r"(?:Go\s*[:：])"                  # Go: xxx
    r"|(?:Go\s*侧)"                    # Go 侧
    r"|(?:Go\s*出处)"                  # Go 出处
    r"|(?:Go\s*的)"                    # Go 的
    r"|(?:Go\s*端)"                    # Go 端
    r"|(?:Go\s*行为)"                  # Go 行为
    r"|(?:Go\s*实现)"                  # Go 实现
    r"|(?:Go\s*对应)"                  # Go 对应
    r"|(?:Go\s*一致)"                  # Go 一致
    r"|(?:Go\s*一样)"                  # Go 一样
    r"|(?:Go\s*等价)"                  # Go 等价
    r"|(?:与\s*Go)"                    # 与 Go
    r"|(?:和\s*Go)"                    # 和 Go
    r"|(?:同\s*Go)"                    # 同 Go
    r"|(?:比\s*Go)"                    # 比 Go
    r"|(?:按\s*Go)"                    # 按 Go
    r"|(?:照\s*Go)"                    # 照 Go
    r"|(?:随\s*Go)"                    # 随 Go
    r"|(?:沿用\s*Go)"                  # 沿用 Go
    r"|(?:对齐\s*Go)"                  # 对齐 Go
)

# 内联注释窄口径：只认 §3.3.2 字面点名的「Go: …」措辞。
GO_ASSERTION_INLINE = re.compile(r"Go\s*[:：]")

ANCHOR_RE = re.compile(r"([A-Za-z0-9_.\-*/@]+?\.go)\s*[:：]\s*(\d+)(?:\s*[-–—~]\s*(\d+))?")

# ---------------------------------------------------------------- 注释块

class Block:
    __slots__ = ("path", "start_line", "text", "kind")

    def __init__(self, path: Path, start_line: int, text: str, kind: str):
        self.path = path
        self.start_line = start_line
        self.text = text
        self.kind = kind


def extract_blocks(path: Path) -> list[Block]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (UnicodeDecodeError, OSError):
        return []

    blocks: list[Block] = []
    i, n = 0, len(lines)
    while i < n:
        s = lines[i].strip()

        if s.startswith("/**"):
            buf, j = [lines[i]], i
            if not (s.endswith("*/") and s != "/**"):
                j = i + 1
                while j < n:
                    buf.append(lines[j])
                    if "*/" in lines[j]:
                        break
                    j += 1
            blocks.append(Block(path, i + 1, "\n".join(buf), "javadoc"))
            i = j + 1
            continue

        if s.startswith("/*"):
            buf, j = [lines[i]], i
            if not s.endswith("*/"):
                j = i + 1
                while j < n:
                    buf.append(lines[j])
                    if "*/" in lines[j]:
                        break
                    j += 1
            blocks.append(Block(path, i + 1, "\n".join(buf), "block"))
            i = j + 1
            continue

        if s.startswith("//"):
            buf, j = [], i
            while j < n and lines[j].strip().startswith("//"):
                buf.append(lines[j])
                j += 1
            blocks.append(Block(path, i + 1, "\n".join(buf), "line"))
            i = j
            continue

        i += 1
    return blocks


# ---------------------------------------------------------------- 源码索引

OWN_DOMAIN_MARK = "/service/favorite/"


def module_of(java_path: str) -> str:
    """从 Java 文件路径推出它属于 api / rpc / common 哪一侧。"""
    for m in ("favorite-api", "favorite-rpc", "favorite-common"):
        if m in java_path:
            return m.replace("favorite-", "")
    return ""


def prefer_own(cands: list[str], module: str = "") -> list[str]:
    """裸文件名/短路径多义时的消歧顺序：同域 → 同模块 → 原样。

    ★ 为什么需要「同模块」这一层：favorite 的 api 侧与 rpc 侧**有同名 logic 文件**
      （create_favorite_logic.go 等各一份）。只按「同域」筛仍剩两个候选 ⇒ 读者定位不到。
      按 Java 文件所属模块筛一次，就把「api 模块的注释 ⇒ api 侧文件」这条自然规律补上。
    """
    own = [r for r in cands if OWN_DOMAIN_MARK in "/" + r]
    if own and module:
        same_mod = [r for r in own if f"/service/favorite/{module}/" in "/" + r]
        if same_mod:
            return same_mod
    return own if own else cands


class SourceIndex:
    """一个 .go 文件集合：相对路径 -> 行数。"""

    def __init__(self, label: str):
        self.label = label
        self.files: dict[str, int] = {}     # rel path -> line count
        self.by_base: dict[str, list[str]] = {}

    def add_tree(self, root: Path, prefix: str = "") -> None:
        if not root.is_dir():
            return
        for p in root.rglob("*.go"):
            rel = (prefix + p.relative_to(root).as_posix()) if prefix else p.relative_to(root).as_posix()
            try:
                n = sum(1 for _ in p.open("rb"))
            except OSError:
                n = 0
            self.files[rel] = n
            self.by_base.setdefault(p.name, []).append(rel)

    def resolve(self, path_part: str, module: str = "") -> tuple[str, list[str]]:
        p = path_part.lstrip("./")
        if "*" in p or "?" in p:
            hits = [r for r in self.files if fnmatch.fnmatch(r, p)]
            if not hits:
                hits = [r for r in self.files if fnmatch.fnmatch(r, "*/" + p)]
            return ("glob", sorted(hits)) if hits else ("missing", [])
        if p in self.files:
            return "exact", [p]
        suf = prefer_own(sorted(r for r in self.files if r.endswith("/" + p)), module)
        if suf:
            return "suffix", suf
        # 只有「裸文件名」才允许按 basename 回退；带目录的锚点回退到同名文件是错的
        if "/" in p:
            return "missing", []
        base = prefer_own(sorted(self.by_base.get(p, [])), module)
        if base:
            return "basename", base
        return "missing", []

    def line_count(self, rel: str) -> int:
        return self.files.get(rel, 0)

    def read(self, rel: str) -> str:
        # files 里存的是相对路径，这里反推绝对路径由调用方保证
        raise NotImplementedError


class TruthIndex(SourceIndex):
    """Go 真源 + 锁定依赖，双段解析。"""

    def __init__(self, truth_root: Path, modcache: Path):
        super().__init__("truth")
        self.truth_root = truth_root
        self.add_tree(truth_root)
        self.dep_index = SourceIndex("deps")
        self.std_index = SourceIndex("stdlib")
        self.dep_roots: dict[str, Path] = {}
        self.dep_files: dict[str, Path] = {}   # rel path in dep -> abs path
        self._load_dependencies(modcache)
        self._load_stdlib()

    # ---- go.mod -> module cache
    def _load_dependencies(self, modcache: Path) -> None:
        gomod = self.truth_root / "go.mod"
        if not gomod.is_file() or not modcache.is_dir():
            return
        for line in gomod.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            m = re.match(r"^([\w./\-]+)\s+(v[\w.\-+]+)$", line)
            if not m:
                continue
            mod, ver = m.group(1), m.group(2)
            esc = re.sub(r"([A-Z])", lambda mm: "!" + mm.group(1).lower(), mod)
            d = modcache / f"{esc}@{ver}"
            if not d.is_dir():
                continue
            self.dep_roots[mod] = d
            for p in d.rglob("*.go"):
                rel = f"{mod}@{ver}/{p.relative_to(d).as_posix()}"
                try:
                    n = sum(1 for _ in p.open("rb"))
                except OSError:
                    n = 0
                self.dep_index.files[rel] = n
                self.dep_index.by_base.setdefault(p.name, []).append(rel)
                self.dep_files[rel] = p

    def _load_stdlib(self) -> None:
        """Go 标准库：$(go env GOROOT)/src。锚点形如 src/net/url/url.go:1001。"""
        try:
            out = subprocess.run(["go", "env", "GOROOT"], capture_output=True,
                                 text=True, timeout=30)
            goroot = out.stdout.strip()
        except (OSError, subprocess.SubprocessError):
            return
        if not goroot:
            return
        src = Path(goroot) / "src"
        if not src.is_dir():
            return
        self.std_index.add_tree(src)
        # 锚点常写成 "src/net/url/url.go"（相对 GOROOT）⇒ 同一棵树再以 src/ 前缀登记一份
        self.std_index.add_tree(src, prefix="src/")

    def resolve(self, path_part: str, module: str = "") -> tuple[str, list[str], str, str]:
        """返回 (status, candidates, source_label, resolved_rel)。"""
        # 锚点可能写成 "Sea-RideTheWind_golang/service/..." ⇒ 剥掉仓库目录前缀
        for pre in ("Sea-RideTheWind_golang/", "sea-try-go/"):
            if path_part.startswith(pre):
                path_part = path_part[len(pre):]
                break
        status, cands = super().resolve(path_part, module)
        if status != "missing":
            return status, cands, "真源", cands[0] if cands else ""
        status, cands = self.dep_index.resolve(path_part, module)
        if status != "missing":
            return status, cands, "依赖", cands[0] if cands else ""
        status, cands = self.std_index.resolve(path_part, module)
        if status != "missing":
            return status, cands, "标准库", cands[0] if cands else ""
        return "missing", [], "", ""

    def line_count(self, rel: str) -> int:
        if rel in self.files:
            return self.files[rel]
        return self.dep_index.files.get(rel, 0)

    def abs_path(self, rel: str) -> Path | None:
        if rel in self.dep_files:
            return self.dep_files[rel]
        return self.truth_root / rel


# ---------------------------------------------------------------- 源码比对

JAVA_ENTITIES = {
    "&lt;": "<", "&gt;": ">", "&amp;": "&", "&quot;": '"',
    "&#39;": "'", "&#064;": "@", "&nbsp;": " ", "{@code ": "", "}": "",
}

# Go 片段识别：必须带 Go 独有的写法，且不能是 Java 片段
GO_ONLY_HINT = re.compile(r":=|\bfunc\b|\bpackage\b|\bimport\b")
GO_JAVA_MARKER = re.compile(
    r"\bthis\.|\bnew\s+[A-Z]|\bpublic\s|\bprivate\s|\bvoid\s|@Override|\bimplements\b")
# 跨引用：方法级 javadoc 把证据放在类注释里时用「见类注释」指路
CROSS_REF = re.compile(r"见\s*(?:类注释|上方|上文|本类|下述|下面)|\{@link")

PRE_RE = re.compile(r"<pre>(.*?)</pre>", re.DOTALL)


def _norm_line(raw: str) -> str:
    for k, v in JAVA_ENTITIES.items():
        raw = raw.replace(k, v)
    raw = re.sub(r"^\s*\*+\s?", "", raw)
    raw = raw.replace("\t", "    ")
    return raw.strip()


def normalize_code_with_lines(text: str) -> list[tuple[int, str]]:
    """归一成 [(原始行号, 行文本)]：解实体、去注释前缀、tab→空格、去空行、去缩进。

    ⚠️ 必须带回原始行号：F4 判定「引用的 Go 源码落在锚点行段内」要用**文件真实行号**，
    去掉空行后行号会整体前移，用归一化下标比会全量误判。
    """
    out: list[tuple[int, str]] = []
    for i, raw in enumerate(text.splitlines(), 1):
        s = _norm_line(raw)
        if not s:
            continue
        out.append((i, s))
    return out


def quoted_go_sources(text: str) -> list[list[str]]:
    """抽出 javadoc 里 <pre>…</pre> 中形似 Go 源码的片段（按空行切段后逐段判定）。"""
    outs: list[list[str]] = []
    for m in PRE_RE.finditer(text):
        seg = m.group(1)
        for rs in re.split(r"\n[ \t*]*\n", seg):
            lines = [t for _, t in normalize_code_with_lines(rs)]
            if len(lines) < 2:
                continue
            joined = "\n".join(lines)
            if "\u2192" in joined or "\u2190" in joined:
                continue          # 探针结果表，不是源码
            if not GO_ONLY_HINT.search(joined):
                continue          # 不是 Go 片段
            if GO_JAVA_MARKER.search(joined):
                continue          # 是 Java 片段
            if any(ln.strip() == "..." for ln in lines):
                continue          # 省略号片段，不做逐字比对
            outs.append(lines)
    return outs


def find_in_file(flines: list[tuple[int, str]], q: list[str]) -> int:
    """在归一化后的文件行里找 q 首次出现的**原始行号**（1-based）；找不到返回 -1。"""
    texts = [t for _, t in flines]
    for i in range(len(texts) - len(q) + 1):
        if texts[i:i + len(q)] == q:
            return flines[i][0]
    return -1


# ---------------------------------------------------------------- 审计

def audit(root: Path, truth_root: Path, modcache: Path, globs: tuple[str, ...]) -> dict:
    idx = TruthIndex(truth_root, modcache)

    dirs: list[Path] = []
    for g in globs:
        dirs.extend(sorted(root.glob(g)))
    dirs = sorted({d for d in dirs if d.is_dir()})

    findings: list[dict] = []
    stats = {
        "files_scanned": 0, "blocks_total": 0, "assertion_blocks": 0,
        "anchors_total": 0, "anchors_truth": 0, "anchors_dep": 0, "anchors_std": 0,
        "anchors_bare_base": 0, "anchors_missing": 0, "anchors_oob": 0,
        "quotes_checked": 0, "quotes_ok": 0, "quotes_mismatch": 0, "quotes_notfound": 0,
    }

    for d in dirs:
        for java in sorted(d.rglob("*.java")):
            stats["files_scanned"] += 1
            rel_java = java.relative_to(root).as_posix()
            for b in extract_blocks(java):
                stats["blocks_total"] += 1
                broad = b.kind in ("javadoc", "block")
                marker = GO_ASSERTION_BROAD if broad else GO_ASSERTION_INLINE
                if not marker.search(b.text):
                    continue
                stats["assertion_blocks"] += 1
                anch = ANCHOR_RE.finditer(b.text)
                anchors = [(m.group(1), int(m.group(2)),
                            int(m.group(3)) if m.group(3) else int(m.group(2)))
                           for m in anch]
                stats["anchors_total"] += len(anchors)
                quote = first_meaningful_line(b.text)

                if not anchors:
                    if CROSS_REF.search(b.text):
                        continue          # 显式指路到类注释/其它方法，由那里承载锚点
                    findings.append({
                        "class": "F1" if broad else "I1",
                        "severity": "gate" if broad else "info",
                        "file": rel_java, "line": b.start_line, "kind": b.kind,
                        "msg": "Go 断言块内没有任何 `*.go:NN` 锚点（§3.3.1 要求可指到具体行）",
                        "excerpt": quote,
                    })
                    continue

                resolved: list[tuple[str, int, int, str, str]] = []  # file, s, e, label, rel
                for (f, s, e) in anchors:
                    status, cands, label, rel = idx.resolve(f, module_of(rel_java))
                    if status == "missing":
                        stats["anchors_missing"] += 1
                        findings.append({
                            "class": "F2", "severity": "gate",
                            "file": rel_java, "line": b.start_line, "kind": b.kind,
                            "msg": f"锚点悬空：`{f}:{s}` 在 Go 真源与锁定依赖里都找不到该文件",
                            "excerpt": quote,
                        })
                        continue
                    if label == "真源":
                        stats["anchors_truth"] += 1
                    elif label == "依赖":
                        stats["anchors_dep"] += 1
                    else:
                        stats["anchors_std"] += 1
                    ambiguous = len(cands) > 1
                    if ambiguous:
                        # 真问题：同名文件存在于多处 ⇒ 读者无法唯一定位
                        stats["anchors_bare_base"] += 1
                        findings.append({
                            "class": "I2", "severity": "info",
                            "file": rel_java, "line": b.start_line, "kind": b.kind,
                            "msg": (f"锚点 `{f}` 不能唯一定位（候选 {len(cands)} 个："
                                    f"{', '.join(cands[:3])}）⇒ 读者无法唯一定位"),
                            "excerpt": quote,
                        })
                        continue        # 多候选时不判行号/引用，避免误判
                    if status == "basename":
                        # 可推断但没写全：读者需知道「同域 → 同模块 → basename」的消歧约定
                        stats["anchors_bare_base"] += 1
                        findings.append({
                            "class": "I4", "severity": "info",
                            "file": rel_java, "line": b.start_line, "kind": b.kind,
                            "msg": (f"锚点 `{f}` 只写了文件名（消歧后唯一："
                                    f"{cands[0] if cands else '?'}）⇒ 建议补全目录"),
                            "excerpt": quote,
                        })
                    # ---- F3：行号越界
                    lc = idx.line_count(rel)
                    if lc and e > lc:
                        stats["anchors_oob"] += 1
                        findings.append({
                            "class": "F3", "severity": "gate",
                            "file": rel_java, "line": b.start_line, "kind": b.kind,
                            "msg": (f"行号越界：`{f}:{s}-{e}`，但该文件只有 {lc} 行"
                                    f"（{label}: {rel}）"),
                            "excerpt": quote,
                        })
                        continue
                    resolved.append((f, s, e, label, rel))

                # ---- F4：<pre> 引用的 Go 源码必须落在锚点行段内
                for q in quoted_go_sources(b.text):
                    stats["quotes_checked"] += 1
                    hit = False
                    outside = None
                    for (f, a_s, a_e, label, rel) in resolved:
                        abs_p = idx.abs_path(rel)
                        if abs_p is None:
                            continue
                        try:
                            flines = normalize_code_with_lines(
                                abs_p.read_text(encoding="utf-8"))
                        except (OSError, UnicodeDecodeError):
                            continue
                        pos = find_in_file(flines, q)
                        if pos == -1:
                            continue
                        if a_s - 1 <= pos <= a_e + 2:
                            hit = True
                            break
                        if outside is None:
                            outside = (f, a_s, a_e, label, rel, pos)
                    if hit:
                        stats["quotes_ok"] += 1
                    elif outside:
                        stats["quotes_mismatch"] += 1
                        f, a_s, a_e, label, rel, pos = outside
                        findings.append({
                            "class": "F4", "severity": "gate",
                            "file": rel_java, "line": b.start_line, "kind": b.kind,
                            "msg": (f"锚点 `{f}:{a_s}-{a_e}` 指向的 {label} 文件里，"
                                    f"该 Go 源码实际在第 {pos} 行起 ⇒ 锚点指到了别处"),
                            "excerpt": first_go_line("\n".join(q)),
                        })
                    else:
                        stats["quotes_notfound"] += 1
                        findings.append({
                            "class": "I3", "severity": "info",
                            "file": rel_java, "line": b.start_line, "kind": b.kind,
                            "msg": "块内 <pre> 引用的 Go 源码在锚点文件里找不到（可能是节选或改写）",
                            "excerpt": first_go_line("\n".join(q)),
                        })

    gate = [f for f in findings if f["severity"] == "gate"]
    info = [f for f in findings if f["severity"] == "info"]
    return {"stats": stats, "gate": gate, "info": info, "dirs": len(dirs)}


def first_meaningful_line(text: str) -> str:
    for raw in text.splitlines():
        s = raw.strip()
        s = re.sub(r"^/\*\*|^\*/|^\*|^//", "", s).strip()
        if s:
            return s[:150]
    return ""


def first_go_line(q: str) -> str:
    for ln in q.split("\n"):
        if ln.strip():
            return ln.strip()[:150]
    return ""


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description="审计 Go 断言注释块的文件:行号锚点")
    here = Path(__file__).resolve()
    default_root = here.parents[3]
    ap.add_argument("--root", default=str(default_root))
    ap.add_argument("--go-root", default=os.environ.get("GO_TRUTH_ROOT", ""))
    ap.add_argument("--modcache", default=os.environ.get("GOMODCACHE", ""))
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--strict", action="store_true", help="提示级（I*）也算失败")
    args = ap.parse_args()

    root = Path(args.root).resolve()
    go_root = Path(args.go_root).resolve() if args.go_root else (root.parent / "Sea-RideTheWind_golang")
    if not go_root.is_dir():
        alt = root.parent.parent / "Sea-RideTheWind_golang"
        if alt.is_dir():
            go_root = alt
    if not go_root.is_dir():
        print(f"[FAIL] 找不到 Go 真源：{go_root}", file=sys.stderr)
        return 2

    if args.modcache:
        modcache = Path(args.modcache).resolve()
    else:
        try:
            out = subprocess.run(["go", "env", "GOMODCACHE"], capture_output=True,
                                 text=True, timeout=30)
            modcache = Path(out.stdout.strip()) if out.returncode == 0 else Path.home() / "go/pkg/mod"
        except (OSError, subprocess.SubprocessError):
            modcache = Path.home() / "go/pkg/mod"

    result = audit(root, go_root, modcache, DEFAULT_SCAN_GLOBS)
    st = result["stats"]

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if result["gate"] else 0
    else:
        print(f"RAG root : {root}")
        print(f"Go  root : {go_root}")
        print(f"modcache : {modcache}")
        print(f"扫描目录 : {result['dirs']} 个 / java 文件 {st['files_scanned']} 个")
        print(f"注释块   : {st['blocks_total']}（其中含 Go 断言 {st['assertion_blocks']}）")
        print(f"锚点     : 共 {st['anchors_total']} —— 真源 {st['anchors_truth']} / "
              f"依赖 {st['anchors_dep']} / 悬空 {st['anchors_missing']} / "
              f"行号越界 {st['anchors_oob']}")
        print(f"引用源码 : 检查 {st['quotes_checked']} 段 —— 命中 {st['quotes_ok']} / "
              f"不匹配 {st['quotes_mismatch']}")
        print()
        for f in result["gate"]:
            print(f"[{f['class']}] {f['file']}:{f['line']}  {f['msg']}")
            if f.get("excerpt"):
                print(f"        {f['excerpt']}")
        if result["info"]:
            print(f"--- 提示级 {len(result['info'])} 条（非门禁） ---")
            for f in result["info"]:
                print(f"[{f['class']}] {f['file']}:{f['line']}  {f['msg']}")
                if f.get("excerpt"):
                    print(f"        {f['excerpt']}")

    if result["gate"] or (args.strict and result["info"]):
        print(f"\n[FAIL] 门禁级 {len(result['gate'])} 条"
              + (f"，提示级 {len(result['info'])} 条" if result["info"] else ""))
        return 1
    print("\n[OK] 无门禁级发现：Go 断言块均带可解析的 `文件:行号` 锚点，"
          "行号在文件范围内，且引用的 Go 源码落在锚点行段内。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
