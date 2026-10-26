#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
favorite 域台账自检（ledger self-check）——机器化回答三个问题：

  1. **台账是不是「最新」**：台账之间的章节指针能不能落到真实存在的小节？编号
     （DEV-FAV-* / PIT-FAV-*）的定义与引用是否闭合？
  2. **台账是不是「全」**：Go 侧 favorite 的每个非测试源文件，在台账里有没有落点？
  3. **台账里的路径出处理得对不对**：引用的仓内文件存不存在？Go 侧 `file.go:NN`
     出处能不能解析到真实文件、且行号在范围内？

依据 AGENTS.md §3.3.2（断言必须可核验）与 §5.3b（三档台账：每域必读 + 必写回）。

为什么要有这个工具
------------------
台账是**给人读**的，但「最新 / 全 / 路径可解析」是**可机检**的：
手写台账会积累四类静默缺陷 —— ① 跨文件章节引用指错（改标题号后没人发现）；
② 编号引用了但没登记；③ Go 侧新增了源文件但台账没跟上；
④ **路径写错/写得太简写**（改个文件名没人发现）。

⚠️ **第 ④ 类是 2026-10-26 才补上的**（第十八轮）：
当时借 `global` 域的跨域共享复核器（`tools/check-ledger-selfcheck.py`，global 单写权）
跑本域台账，**当场抓出 2 处路径写得太简写**（`schema/naming.go:89-90` 与
`metrics.go:22-27` ⇒ 机器按 basename 解析时前者**在真源与全部第三方库里都找不到**，
后者**跨库同名歧义**）⇒ 内容本身没错，但**机器核不动** ⇒ 修成完整路径。
⇒ 教训：**「内容对」不等于「可核验」** ⇒ 这一层必须进**本域自己的**门禁，
否则每次都要借别人的工具（而别人的工具还**没有 favorite 预设**）。

误报处理（**工具自身的第一版必须先修再信**）
------------------------------------------
  · **散文里的路径不算引用**：只有 markdown 链接 ``[..](path.md) §x.y`` 才做文件级解析；
    正文里裸写的 ``DRIFT/favorite/rpc.md` §5-U1`` 是「位置描述」，不判死链。
  · **外部引用目标也纳入索引**：根 ``AGENTS.md`` 与三个共享 ``_INDEX.md`` 是合法引用目标，
    按**真实标题**判定小节是否存在。
  · **生成代码不计入 RAG 侧覆盖**：``target/`` 下的 protoc 产物不是源文件。
  · **裸行号锚点（``:48-50``）只做提示**：表格里若有语境列（如同列已给文件名），
    裸行号是可接受的简写；只有**无语境**的裸行号才需要补全 —— 由人工判定。
  · **省略写法（``...``）不去噪成缺失**：台账故意省略中间层级（读者按前缀也能定位）
    ⇒ 但它**也不是精确路径** ⇒ 记提示级，不判死链（对标共享工具的 N8）。
  · **Go 侧路径按「真源仓 / module cache」两处解析**：台账里 `service/...` 开头的那批
    是 **Go 真源**的路径（真源仓也是 `service/` 布局）⇒ 必须在**真源仓**里找，
    不是在 RAG 仓里找。

退出码
------
  0 = 无门禁级问题（门禁级：死指针 / 编号未登记 / Go 源文件未落点 /
                     仓内路径缺失 / Go 锚点悬空）
  1 = 存在门禁级问题
  2 = 工具自身出错（路径不对等）

`--mutation-test`：**工具自证**（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）
------------------------------------------------------------------
  故意往台账文本里注入两类缺陷（一个必不存在的仓内路径 + 一个行号越界的 Go 锚点），
  要求**必须被抓到**；再要求「复原后 == 变异前」⇒ 抓不到 ⇒ EXIT=1。
  ⚠️ 这一条是 2026-10-26 补的：共享工具那边同期也发现了同型问题
  （阳性对照分支在非 task 分支上恒红 ⇒ 改成「复原后 == 变异前」）⇒ 同一个教训。
"""
import argparse
import json
import os
import re
import sys
from collections import defaultdict

LEDGER_DIRS = [
    "docs/DRIFT/favorite",
    "docs/Intentional_Deviations/favorite",
    "docs/Pitfalls/favorite",
]
EXTERNAL_REF_TARGETS = [
    "AGENTS.md",
    "docs/DRIFT/_INDEX.md",
    "docs/Intentional_Deviations/_INDEX.md",
    "docs/Pitfalls/_INDEX.md",
]
GO_ROOT_HINT = "Sea-RideTheWind_golang"
MODCACHE_HINT = os.path.join("go", "pkg", "mod")

SEC_RE = re.compile(r"§\s*(\d+(?:\.\d+)*)")
HEAD_NUM_RE = re.compile(r"^#{1,6}\s*(\d+(?:\.\d+)*)[.、\s]")
# 文件级引用：只有 markdown 链接后面的 §x.y 才做跨文件解析
LINK_REF_RE = re.compile(r"\[[^\]]*\]\(([^)]+\.md)\)[^\n]{0,40}?§\s*(\d+(?:\.\d+)*)")
BARE_REF_RE = re.compile(r"(?<![./\w])§\s*(\d+(?:\.\d+)*)")
# 编号：DEV-FAV-*（有意偏离）/ PIT-FAV-*（踩坑）
ID_RE = re.compile(r"\b(DEV-FAV-[A-Z]+-\d+|PIT-FAV-[A-Z]+-\d+)\b")
# 裸行号锚点：形如 `:48-50` / `:71`
NAKED_ANCHOR_RE = re.compile(r"`:(\d+(?:-\d+)?)`")

# ── 第 ⑤ 层 · 仓内路径引用 ────────────────────────────────────────────────
# 只认「仓根相对」的路径（service/ tools/ docs/ 开头）⇒ 散文里的相对路径不误判。
REPO_PATH_RE = re.compile(
    r"(?:service|tools|docs)/[A-Za-z0-9_./-]*[A-Za-z0-9_-]"
    r"\.(?:java|md|py|sh|yml|yaml|MD|json|txt|sql|proto)")

# ── 第 ⑥ 层 · Go 侧 file:line 锚点 ────────────────────────────────────────
GO_REF_RE = re.compile(r"`?([A-Za-z0-9_][A-Za-z0-9_./@-]*\.go):(\d+)(?:-(\d+))?`?")

# ── N5 · 台账**记述历史纠错** ⇒ 那条引用是「当时的错」，不是「现在的悬空引用」 ──
# ⚠️ **必须行级局部判定**（只看引用所在行与其 ±1 行）——
#    共享工具那一版是「取来源文件**前 2000 字**」⇒ 极宽 ⇒ 几乎所有引用都被去噪
#    （实测：178 条里 173 条被去噪）⇒ 那是**假阴性**，不能照抄。
#    本域的判据：叙述必须**贴着**那条引用（同一行或邻行）才生效。
N5_NARRATION_RE = re.compile(
    r"(越界|改正|订正|修正|已改|补成|应为|原写|原为|误写|误指|"
    r"不存在|已删除|已移除|不是悬空|历史记述|当时的错)")


# ==================================================================
# 基础工具
# ==================================================================
def find_repo_root(start):
    cur = os.path.abspath(start)
    while True:
        if os.path.isdir(os.path.join(cur, "docs", "DRIFT", "favorite")):
            return cur
        parent = os.path.dirname(cur)
        if parent == cur:
            return None
        cur = parent


def collect_ledger_files(root):
    files = []
    for d in LEDGER_DIRS:
        p = os.path.join(root, d)
        if not os.path.isdir(p):
            continue
        for f in sorted(os.listdir(p)):
            if f.endswith(".md"):
                files.append(os.path.join(d, f))
    return files


def real_sections(path):
    """文件里真实存在的小节号：标题里的编号 + 正文里被引用的 §号。"""
    secs = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = HEAD_NUM_RE.match(line)
            if m:
                secs.add(m.group(1))
            for s in SEC_RE.findall(line):
                secs.add(s)
    return secs


def heading_sections(path):
    """只看标题里真实存在的小节号（用于外部目标判定，严于 real_sections）。"""
    secs = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = HEAD_NUM_RE.match(line)
            if m:
                secs.add(m.group(1))
    return secs


def resolve_ref(root, from_file, hint):
    if not hint or hint.startswith("http"):
        return None
    cand = os.path.normpath(os.path.join(os.path.dirname(from_file), hint))
    if os.path.exists(os.path.join(root, cand)):
        return cand
    cand2 = os.path.normpath(hint)
    if os.path.exists(os.path.join(root, cand2)):
        return cand2
    return None


def find_go_root(root, explicit):
    cands = []
    if explicit:
        cands.append(explicit)
    parent = os.path.dirname(root)
    cands.append(os.path.join(parent, GO_ROOT_HINT))
    cands.append(os.path.join(parent, "..", GO_ROOT_HINT))
    for c in cands:
        c = os.path.abspath(c)
        if os.path.isdir(os.path.join(c, "service", "favorite")):
            return c
    return None


def find_modcache(explicit=None):
    if explicit:
        p = os.path.abspath(explicit)
        return p if os.path.isdir(p) else None
    home = os.path.expanduser("~")
    p = os.path.join(home, MODCACHE_HINT)
    return p if os.path.isdir(p) else None


def find_goroot_src(explicit=None):
    """Go **标准库**源码根（`<GOROOT>/src`）—— 台账里 `net/http/server.go` 这类引用指向它。

    ⚠️ 少了标准库 ⇒ 标准库的引用会被判成「文件找不到」⇒ **假阳性**。
    """
    cands = []
    if explicit:
        cands.append(explicit)
    if os.environ.get("GOROOT"):
        cands.append(os.path.join(os.environ["GOROOT"], "src"))
    home = os.path.expanduser("~")
    try:
        import glob
        cands.extend(sorted(glob.glob(os.path.join(home, "sdk", "go*", "src")), reverse=True))
        cands.append("/usr/local/go/src")
    except Exception:
        pass
    for c in cands:
        c = os.path.abspath(c)
        if os.path.isdir(os.path.join(c, "net", "http")):
            return c
    return None


# ==================================================================
# 第 ⑥ 层支撑：Go 文件索引（真源 + module cache）
# ==================================================================
def go_mod_modules(go_root):
    """从**真源 go.mod** 里解析 (module, version) ⇒ 版本取自真源，不是本地碰巧有的。"""
    out = []
    gomod = os.path.join(go_root, "go.mod")
    if not os.path.isfile(gomod):
        return out
    in_block = False
    with open(gomod, encoding="utf-8") as fh:
        for line in fh:
            s = line.strip()
            if s.startswith("require ("):
                in_block = True
                continue
            if in_block and s == ")":
                in_block = False
                continue
            target = None
            if in_block:
                target = s
            elif s.startswith("require "):
                target = s[len("require "):].strip()
            if not target or target.startswith("//"):
                continue
            m = re.match(r"^([\w./-]+)\s+(v[\w.+-]+)", target)
            if m:
                out.append((m.group(1), m.group(2)))
    return out


def build_go_index(go_root, modcache, goroot_src=None):
    """建「Go 文件名 → 候选」索引。

    - **真源**：`service/**` 下的 `*.go`（rel = `service/...`）
    - **第三方**：module cache 里按**真源 go.mod** 的版本命中到的那些库
      （rel = 库内相对路径，例如 `schema/naming.go`）
    - **标准库**：`<GOROOT>/src/**`（rel = 相对 `src` 的路径，例如 `net/http/server.go`）

    返回 `(index, libs)`；`index` = `{basename: [(lib, fullpath, rel), ...]}`。
    """
    index = defaultdict(list)
    libs = []

    if go_root:
        base = os.path.join(go_root, "service")
        for dp, _dirs, fns in os.walk(base):
            for fn in fns:
                if fn.endswith(".go"):
                    full = os.path.join(dp, fn)
                    rel = os.path.relpath(full, go_root)
                    index[fn].append(("真源", full, rel))
        libs.append("真源")

    if goroot_src:
        for dp, _dirs, fns in os.walk(goroot_src):
            for fn in fns:
                if fn.endswith(".go"):
                    full = os.path.join(dp, fn)
                    rel = os.path.relpath(full, goroot_src)
                    index[fn].append(("标准库", full, rel))
        libs.append("标准库")

    if modcache:
        for module, version in go_mod_modules(go_root) if go_root else []:
            lib_dir = os.path.join(modcache, f"{module}@{version}")
            if not os.path.isdir(lib_dir):
                continue
            lib = f"{module}@{version}"
            libs.append(lib)
            for dp, _dirs, fns in os.walk(lib_dir):
                for fn in fns:
                    if fn.endswith(".go"):
                        full = os.path.join(dp, fn)
                        rel = os.path.relpath(full, lib_dir)
                        index[fn].append((lib, full, rel))
    return index, libs


def _suffix_score(written, rel):
    """written 与候选 rel 的**末尾段**最长公共长度（越大越精确）。"""
    ws = written.split("/")
    rs = rel.split("/")
    n = 0
    for k in range(1, min(len(ws), len(rs)) + 1):
        if ws[-k:] == rs[-k:]:
            n = k
        else:
            break
    return n


def _line_count(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            return sum(1 for _ in fh)
    except OSError:
        return None


def check_go_refs(root, go_root, modcache, goroot_src, texts, index):
    """第 ⑥ 层：台账里 `file.go:NN` 出处能不能解析到真实文件且行号在范围内。

    解析顺序（**从精确到模糊**）：
      ① `module@version/...` ⇒ 直接在 module cache 里找（最精确）
      ② `service/...` ⇒ 在**真源仓**里找（Go 真源也是 `service/` 布局）
      ③ 其余 ⇒ 按**末尾段最长匹配**在索引里找
    ⇒ 匹配到多个库 ⇒ 记**提示级**「跨库同名歧义」（需要人工确认归属，不是死链）。

    **N5 去噪**：若引用**所在行或其邻行**在**记述历史纠错**（「越界」「改正」…）⇒
    那条引用是「当时的错」，不是「现在的悬空引用」⇒ 记提示级，不判死链。
    """
    gates, hints = [], []
    stats = defaultdict(int)
    seen = {}

    for f in sorted(texts):
        lines = texts[f].splitlines()
        for lineno, line in enumerate(lines, 1):
            for m in GO_REF_RE.finditer(line):
                path = m.group(1)
                s = int(m.group(2))
                e = int(m.group(3)) if m.group(3) else s
                seen.setdefault((path, s, e), set()).add((f, lineno))

    def narrated(f, lineno):
        """引用所在行 ±1 行里有没有「历史纠错」的叙述。"""
        lines = texts[f].splitlines()
        lo = max(0, lineno - 2)
        hi = min(len(lines), lineno + 1)
        return any(N5_NARRATION_RE.search(lines[i]) for i in range(lo, hi))

    for (path, s, e), locs in sorted(seen.items()):
        locs = sorted(locs)
        srcs = sorted({f for f, _ in locs})
        # N5 · 台账在记述历史纠错 ⇒ 不判死链
        if any(narrated(f, ln) for f, ln in locs):
            stats["narrated"] += 1
            hints.append(dict(kind="go-ref-narrated", file=srcs[0], line=0,
                              detail=f"{path}:{s}-{e} —— 台账在**记述历史纠错**（那时的错），非悬空引用"))
            continue

        cands = []
        direct = None

        if "@" in path:
            p = os.path.join(modcache, path) if modcache else None
            if p and os.path.isfile(p):
                direct = ("module-cache", p)
        if direct is None and path.startswith("service/") and go_root:
            p = os.path.join(go_root, path)
            if os.path.isfile(p):
                direct = ("真源", p)

        if direct is not None:
            n = _line_count(direct[1])
            if n is None:
                gates.append(dict(kind="go-ref-unreadable", file=srcs[0], line=0,
                                  detail=f"{path}:{s}-{e} 指向的文件读不出来：{direct[1]}"))
                continue
            if e > n:
                gates.append(dict(kind="go-ref-line-out-of-range", file=srcs[0], line=0,
                                  detail=f"{path}:{s}-{e} 行号超出文件范围（共 {n} 行）"))
                continue
            stats["ok"] += 1
            continue

        base = os.path.basename(path)
        raw = index.get(base, [])
        if not raw:
            gates.append(dict(kind="go-ref-missing", file=srcs[0], line=0,
                              detail=f"{path}:{s}-{e} —— 该 .go 文件在真源、标准库与全部第三方库里都找不到"))
            continue
        scored = [(lib, full, rel, _suffix_score(path, rel)) for lib, full, rel in raw]
        best = max(sc for _l, _f, _r, sc in scored)
        top = [(lib, full, rel) for lib, full, rel, sc in scored if sc == best]
        fits = []
        for lib, full, rel in top:
            n = _line_count(full)
            if n is not None and s <= n and e <= n:
                fits.append((lib, full, rel))
        if not fits:
            n = _line_count(top[0][1])
            gates.append(dict(kind="go-ref-line-out-of-range", file=srcs[0], line=0,
                              detail=f"{path}:{s}-{e} 行号超出文件范围（共 {n} 行）"
                                     f"（候选：{', '.join(sorted({x[0] for x in top}))}）"))
            continue
        libs_hit = sorted({x[0] for x in fits})
        if len(libs_hit) > 1:
            stats["ambiguous"] += 1
            hints.append(dict(kind="go-ref-ambiguous", file=srcs[0], line=0,
                              detail=f"{path}:{s}-{e} 同名文件跨库歧义（归属需人工确认）∈ {libs_hit}"))
        elif len(fits) < len(top):
            # 同名文件在**同一个库**里有多个候选，只有部分行号对得上 ⇒ 归属需人工确认
            stats["ambiguous"] += 1
            hints.append(dict(kind="go-ref-ambiguous", file=srcs[0], line=0,
                              detail=f"{path}:{s}-{e} 同名文件有多个候选（归属需人工确认）"
                                     f"：{[os.path.relpath(x[1], root) if x[1].startswith(root) else x[2] for x in top]}"))
        else:
            stats["ok"] += 1
    return gates, hints, dict(stats)


def check_repo_paths(root, go_root, texts):
    """第 ⑤ 层：台账里引用的**仓内路径**存不存在。

    ⚠️ 同一段路径在两个仓里都可能是合法的：
      · `service/favorite/favorite-rpc/...` ⇒ **RAG 仓**
      · `service/favorite/rpc/...` ⇒ **Go 真源仓**（真源也是 `service/` 布局）
    ⇒ 判「RAG 仓找不到 **且** 真源仓也找不到」才算缺失。
    """
    gates, hints = [], []
    stats = defaultdict(int)
    exact = {}
    ellipsis = {}

    for f in sorted(texts):
        for lineno, line in enumerate(texts[f].splitlines(), 1):
            for m in REPO_PATH_RE.finditer(line):
                p = m.group(0)
                if "..." in p:
                    stats["ellipsis"] += 1
                    ellipsis.setdefault(p, set()).add(f)
                else:
                    exact.setdefault(p, set()).add(f)

    for p, srcs in sorted(exact.items()):
        srcs = sorted(srcs)
        if os.path.exists(os.path.join(root, p)):
            stats["ok"] += 1
            continue
        if go_root and os.path.exists(os.path.join(go_root, p)):
            stats["go"] += 1
            continue
        gates.append(dict(kind="repo-path-missing", file=srcs[0], line=0,
                          detail=f"台账引用的仓内路径不存在（RAG 仓与真源仓都找不到）：{p}"))
    for p, srcs in sorted(ellipsis.items()):
        hints.append(dict(kind="repo-path-ellipsis", file=sorted(srcs)[0], line=0,
                          detail=f"省略写法（`...`）不是精确路径引用：{p}"))
    return gates, hints, dict(stats)



# ==================================================================
# 变异自证
# ==================================================================
def run_mutation_test(root, go_root, modcache, goroot_src, texts, index):
    """故意注入两类缺陷 ⇒ 必须被抓到；再要求「复原后 == 变异前」。"""
    problems = []
    if not texts:
        return ["台账为空，没法做变异自证"]

    first = sorted(texts)[0]
    base_repo_gates, _, _ = check_repo_paths(root, go_root, texts)
    base_go_gates, _, _ = check_go_refs(root, go_root, modcache, goroot_src, texts, index)

    # 变异 ① · 仓内路径：注入一个必不存在的路径
    mut1 = dict(texts)
    mut1[first] = texts[first] + "\n`service/favorite/__mut_missing_xyz__.java`\n"
    g1, _, _ = check_repo_paths(root, go_root, mut1)
    if not g1:
        problems.append("⑤ 层变异（注入不存在的仓内路径）**没被抓到** ⇒ 这一层是恒真的")

    # 变异 ② · Go 锚点：注入一个行号越界的锚点
    mut2 = dict(texts)
    mut2[first] = texts[first] + "\n`service/favorite/rpc/favorite.go:999999`\n"
    g2, _, _ = check_go_refs(root, go_root, modcache, goroot_src, mut2, index)
    if not g2:
        problems.append("⑥ 层变异（注入行号越界的 Go 锚点）**没被抓到** ⇒ 这一层是恒真的")

    # 复原后必须与变异前一致（不许「变异后反而更干净」）
    if check_repo_paths(root, go_root, texts)[0] != base_repo_gates:
        problems.append("⑤ 层在「复原后」的结果与「变异前」不一致 ⇒ 检查不具确定性")
    if check_go_refs(root, go_root, modcache, goroot_src, texts, index)[0] != base_go_gates:
        problems.append("⑥ 层在「复原后」的结果与「变异前」不一致 ⇒ 检查不具确定性")
    return problems


# ==================================================================
# main
# ==================================================================
def main():
    ap = argparse.ArgumentParser(description="favorite 域台账自检")
    ap.add_argument("--repo-root", default=os.getcwd(), help="RAG 工作树根目录")
    ap.add_argument("--go-root", default=None, help="Go 真源仓库路径")
    ap.add_argument("--modcache", default=None, help="Go module cache 路径（默认 ~/go/pkg/mod）")
    ap.add_argument("--goroot-src", default=None, help="Go 标准库源码根（默认自动找 GOROOT/src）")
    ap.add_argument("--mutation-test", action="store_true",
                    help="只跑变异自证（故意注入缺陷 ⇒ 必须被抓到）")
    ap.add_argument("--json", action="store_true", help="输出 JSON")
    args = ap.parse_args()

    root = find_repo_root(args.repo_root)
    if not root is None:
        root = os.path.abspath(root)
    if root is None:
        print("❌ 找不到 RAG 工作树根（期望 docs/DRIFT/favorite 存在）", file=sys.stderr)
        return 2

    ledger_files = collect_ledger_files(root)
    if not ledger_files:
        print("❌ 没找到 favorite 域台账", file=sys.stderr)
        return 2

    go_root = find_go_root(root, args.go_root)
    modcache = find_modcache(args.modcache)
    goroot_src = find_goroot_src(args.goroot_src)
    texts = {p: open(os.path.join(root, p), encoding="utf-8").read() for p in ledger_files}

    if args.mutation_test:
        index, libs = build_go_index(go_root, modcache, goroot_src)
        problems = run_mutation_test(root, go_root, modcache, goroot_src, texts, index)
        if problems:
            print("❌ 变异自证失败：")
            for p in problems:
                print(f"   · {p}")
            return 1
        print(f"✅ 变异自证通过：⑤ 仓内路径层、⑥ Go 锚点层**都能抓到注入的缺陷**"
              f"（索引库 {len(libs)} 个：{', '.join(libs[:6])}…）")
        return 0

    ref_files = list(ledger_files)
    missing_targets = []
    for e in EXTERNAL_REF_TARGETS:
        if os.path.exists(os.path.join(root, e)):
            ref_files.append(e)
        else:
            missing_targets.append(e)

    SEC = {p: real_sections(os.path.join(root, p)) for p in ref_files}
    HEAD = {p: heading_sections(os.path.join(root, p)) for p in ref_files}

    gates = []   # 门禁级
    hints = []   # 提示级

    # ---------- ① 章节指针 ----------
    stats = defaultdict(int)
    for path in ledger_files:
        for lineno, line in enumerate(texts[path].splitlines(), 1):
            spans = []
            for m in LINK_REF_RE.finditer(line):
                spans.append(m.span())
                hint, sec = m.group(1), m.group(2)
                stats["file_scoped"] += 1
                tgt = resolve_ref(root, path, hint)
                if tgt is None:
                    gates.append(dict(kind="dead-link", file=path, line=lineno,
                                      detail=f"链接目标不存在：{hint}"))
                    continue
                if tgt in HEAD:
                    ok = sec in HEAD[tgt]
                else:
                    ok = sec in SEC.get(tgt, set())
                if not ok:
                    gates.append(dict(kind="dead-section", file=path, line=lineno,
                                      detail=f"{tgt} 无小节 §{sec}"))
            for m in BARE_REF_RE.finditer(line):
                if any(s <= m.start() < e for s, e in spans):
                    continue
                sec = m.group(1)
                stats["bare"] += 1
                if sec not in SEC[path]:
                    gates.append(dict(kind="dead-section", file=path, line=lineno,
                                      detail=f"同文件无小节 §{sec}"))

    # ---------- ② 编号闭合 ----------
    ids = defaultdict(lambda: dict(refs=[], defs=[]))
    for path in ledger_files:
        for lineno, line in enumerate(texts[path].splitlines(), 1):
            for m in ID_RE.finditer(line):
                iid = m.group(1)
                ids[iid]["refs"].append((path, lineno))
                # 定义处：markdown 标题里带编号，或表格首列就是编号
                if re.match(r"^#{1,6}.*" + re.escape(iid), line):
                    ids[iid]["defs"].append((path, lineno))
                elif re.match(r"^\|?\s*\**" + re.escape(iid) + r"\**\s*\|", line):
                    ids[iid]["defs"].append((path, lineno))
    for iid, info in sorted(ids.items()):
        if not info["defs"]:
            gates.append(dict(kind="unregistered-id", file=info["refs"][0][0],
                              line=info["refs"][0][1],
                              detail=f"{iid} 被引用 {len(info['refs'])} 次但无定义处"))

    # ---------- ③ Go 侧源文件覆盖 ----------
    go_files = []
    go_uncovered = []
    if go_root is None:
        hints.append(dict(kind="go-root-missing",
                          detail=f"找不到 Go 真源（{GO_ROOT_HINT}），跳过覆盖检查"))
    else:
        for base, _dirs, files in os.walk(os.path.join(go_root, "service", "favorite")):
            for fn in files:
                if fn.endswith(".go") and not fn.endswith("_test.go"):
                    go_files.append(os.path.relpath(os.path.join(base, fn), go_root))
        corpus = "\n".join(texts[p] for p in ledger_files)
        for rel in sorted(go_files):
            bn = os.path.basename(rel)
            tail = rel.split("service/favorite/", 1)[1]
            if bn in corpus or tail in corpus or rel in corpus:
                continue
            go_uncovered.append(rel)
            gates.append(dict(kind="go-file-uncovered", file="(go)", line=0,
                              detail=f"Go 源文件在台账里没有落点：{rel}"))

    # ---------- ④ 裸行号锚点（提示级）----------
    naked = []
    for path in ledger_files:
        for lineno, line in enumerate(texts[path].splitlines(), 1):
            for m in NAKED_ANCHOR_RE.finditer(line):
                naked.append((path, lineno, m.group(0)))
                hints.append(dict(kind="naked-anchor", file=path, line=lineno,
                                  detail=f"裸行号锚点 {m.group(0)} —— 需人工确认有无语境列"))

    # ---------- ⑤ 仓内路径存在性 ----------
    path_gates, path_hints, path_stats = check_repo_paths(root, go_root, texts)
    gates.extend(path_gates)
    hints.extend(path_hints)

    # ---------- ⑥ Go 侧 file:line 锚点可解析 ----------
    index, libs = build_go_index(go_root, modcache, goroot_src)
    go_ref_gates, go_ref_hints, go_ref_stats = check_go_refs(
        root, go_root, modcache, goroot_src, texts, index)
    gates.extend(go_ref_gates)
    hints.extend(go_ref_hints)

    result = dict(
        repo_root=root,
        go_root=go_root,
        modcache=modcache,
        goroot_src=goroot_src,
        indexed_libs=libs,
        ledger_files=len(ledger_files),
        external_targets_missing=missing_targets,
        section_refs=dict(file_scoped=stats["file_scoped"], bare=stats["bare"]),
        ids_checked=len(ids),
        go_source_files=len(go_files),
        go_uncovered=go_uncovered,
        naked_anchors=len(naked),
        repo_paths=path_stats,
        go_refs=go_ref_stats,
        gates=gates,
        hints=hints,
    )

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if gates else 0

    print(f"favorite 域台账自检 · {root}")
    print(f"  台账文件：{len(ledger_files)} 份"
          + (f"（外部引用目标缺 {len(missing_targets)} 个：{missing_targets}）" if missing_targets else ""))
    print(f"  章节引用：文件级 {stats['file_scoped']} 处 / 裸引用 {stats['bare']} 处")
    print(f"  编号闭合：检查 {len(ids)} 个 DEV/PIT 编号")
    if go_root:
        print(f"  Go 源文件：{len(go_files)} 个（非测试），未落点 {len(go_uncovered)} 个")
    else:
        print("  Go 源文件：跳过（未找到 Go 真源）")
    print(f"  裸行号锚点（提示级）：{len(naked)} 处")
    print(f"  仓内路径：解析成功 {path_stats.get('ok', 0)} 处 / 命中真源仓 {path_stats.get('go', 0)} 处"
          f" / 省略写法 {path_stats.get('ellipsis', 0)} 处")
    print(f"  Go 锚点：可解析 {go_ref_stats.get('ok', 0)} 条 / 跨库歧义 {go_ref_stats.get('ambiguous', 0)} 条"
          f"（索引 {len(libs)} 个库）")
    print()
    if gates:
        print(f"❌ 门禁级问题 {len(gates)} 条：")
        for g in gates:
            print(f"   [{g['kind']}] {g['file']}:{g['line']} —— {g['detail']}")
    else:
        print("✅ 门禁级问题 0 条")
    if hints:
        # ⚠️ **按类型分组打印** —— 裸锚点动辄几十上百条，
        #    若平铺打印会把「跨库歧义」「省略写法」这些**新引入的**信号淹掉
        #    ⇒ 每一类给「总数 + 前若干条样本」。
        by_kind = defaultdict(list)
        for h in hints:
            by_kind[h["kind"]].append(h)
        print(f"\nℹ️  提示级 {len(hints)} 条（按类型分组；每类前 5 条样本）：")
        for kind in sorted(by_kind, key=lambda k: (-len(by_kind[k]), k)):
            items = by_kind[kind]
            print(f"   ── {kind} · {len(items)} 条")
            for h in items[:5]:
                print(f"      {h.get('file','')}:{h.get('line','')} —— {h['detail']}")
            if len(items) > 5:
                print(f"      …… 其余 {len(items)-5} 条省略")
    return 1 if gates else 0


if __name__ == "__main__":
    sys.exit(main())
