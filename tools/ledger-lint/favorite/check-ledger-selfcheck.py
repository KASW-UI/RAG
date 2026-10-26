#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
favorite 域台账自检（ledger self-check）——机器化回答三个问题：

  1. **台账是不是「最新」**：台账之间的章节指针能不能落到真实存在的小节？编号
     （DEV-FAV-* / PIT-FAV-*）的定义与引用是否闭合？
  2. **台账是不是「全」**：Go 侧 favorite 的每个非测试源文件，在台账里有没有落点？
  3. **台账里的路径出处理得对不对**：引用的仓内文件存不存在？Go 侧 `file.go:NN`
     出处能不能解析到真实文件、且行号在范围内？**台账断言的符号是不是真的在那个锚点文件里？**

依据 AGENTS.md §3.3.2（断言必须可核验）与 §5.3b（三档台账：每域必读 + 必写回）。

分七层
------
  ① 章节指针（死指针 / 死小节）   ② 编号闭合（DEV/PIT 未登记）
  ③ Go 侧源文件覆盖               ④ 裸行号锚点（提示级）
  ⑤ 仓内路径存在性               ⑥ Go 侧 file:line 锚点可解析
  ⑦ **锚点文件里找不到台账断言的实体**（2026-10-26 · 第二十九轮新增）

扫描范围
--------
  ①~④ 只扫**三档台账**（`docs/{DRIFT,Intentional_Deviations,Pitfalls}/favorite/`）
  ⑤~⑦ 扫**三档台账 + 本域自有的文件树文档**（`EXTRA_SCOPED_FILES`）
  ⚠️ 文件树文档**不进** ①~④ —— 那四层是台账专属口径（编号 / 章节号）。

为什么要有这个工具
------------------
台账是**给人读**的，但「最新 / 全 / 路径可解析 / 断言归属」是**可机检**的：
手写台账会积累四类静默缺陷 —— ① 跨文件章节引用指错（改标题号后没人发现）；
② 编号引用了但没登记；③ Go 侧新增了源文件但台账没跟上；
④ **路径写错/写得太简写**（改个文件名没人发现）；
⑤ **锚点指错文件**（台账说「这里是符号 X」，而 X 其实在别的文件里）。

⚠️ **第 ④ 类是 2026-10-26 才补上的**（第十八轮）：
当时借 `global` 域的跨域共享复核器（`tools/check-ledger-selfcheck.py`，global 单写权）
跑本域台账，**当场抓出 2 处路径写得太简写**（`schema/naming.go:89-90` 与
`metrics.go:22-27` ⇒ 机器按 basename 解析时前者**在真源与全部第三方库里都找不到**，
后者**跨库同名歧义**）⇒ 内容本身没错，但**机器核不动** ⇒ 修成完整路径。
⇒ 教训：**「内容对」不等于「可核验」** ⇒ 这一层必须进**本域自己的**门禁，
否则每次都要借别人的工具（而别人的工具还**没有 favorite 预设**）。

⚠️ **第 ⑤ 类是 2026-10-26 才补上的**（第二十九轮）：
`global` 域在 `0107845` 把「锚点文件里找不到台账断言的实体」做成了**全仓可跑的
补充判据**（`--entity-all`），跑出来**全仓唯一一条命中在 favorite 域**：
`docs/FileTree/favorite/rpc.md` 断言「`FavoriteAuthorityLookupService` **实现 `AuthorityLookup`**」
并把锚点写成 `favorite_authority.go:74-282` —— 而 Go 侧 `AuthorityLookup` 声明在
`service/common/communityfact/authority_http.go:18`（**like / comment 在用，favorite 零引用**）
⇒ **锚点文件里本来就没有这个符号**。
⇒ **`global` 那份工具是共享面（global 单写权）⇒ 本域不能改它**
⇒ 只能把判据**搬进本域自己的门禁**（第 ⑦ 层），并把本域的文件树文档纳入扫描范围。
⚠️ **「搬进来」不等于「照抄」** —— 搬的过程中改了 3 处（见函数注释），
其中最关键的一处：`global` 的变异自证是「**写盘 → 跑 → 还原**」
⇒ 本域改成**纯内存**（`_local_window_at` 从 `texts` 取，不再读盘）⇒ 变异自证零副作用。

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
                     仓内路径缺失 / Go 锚点悬空 / **锚点文件里找不到台账断言的实体**）
  1 = 存在门禁级问题
  2 = 工具自身出错（路径不对等）

`--mutation-test`：**工具自证**（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）
------------------------------------------------------------------
  故意往台账文本里注入**三类缺陷**（一个必不存在的仓内路径 + 一个行号越界的 Go 锚点
  + 一个「断言了锚点文件里没有的符号」），要求**必须被抓到**；再要求「复原后 == 变异前」
  ⇒ 抓不到 ⇒ EXIT=1。
  ⚠️ 这一条是 2026-10-26 补的：共享工具那边同期也发现了同型问题
  （阳性对照分支在非 task 分支上恒红 ⇒ 改成「复原后 == 变异前」）⇒ 同一个教训。
  ⚠️ **2026-10-26 第二十九轮再加一条「阴性对照」**：第 ⑦ 层注入一个**符号确实在**锚点文件里
  的样本（`parseAuthorityID`）⇒ **必须正确地不报** ⇒ 否则说明判据太宽（假阳性）。
  ⚠️ **全部变异都在内存里做**（`_local_window_at` 从 `texts` 取，不再读盘）
  ⇒ 不碰磁盘 ⇒ 中途崩了也不会留下变异过的台账。
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
# ⚠️ **本域自有、但不是「三档台账」的文档**（2026-10-26 · 第二十九轮新增）
#   `docs/FileTree/favorite/rpc.md` 是 **favorite 域的文件树文档**（域内资产），
#   上面那份 `AuthorityLookup` 错锚点就出在它里面。
#   ⇒ **只对 ⑤⑥⑦ 三层生效**（路径存在性 / Go 锚点可解析 / 实体归属），
#     **不进 ①②③④**（章节指针 / 编号闭合 / Go 源文件覆盖 / 裸行号）
#     —— 那四层是**台账专属**口径（编号 `DEV-FAV-*` / `PIT-FAV-*`、章节号 §x.y），
#     拿它们去扫一份文件树文档只会产出噪音。
#   ⚠️ **只加 `rpc.md`，不加 `api.md`** —— `api.md` 属并发会话（不碰）。
EXTRA_SCOPED_FILES = [
    "docs/FileTree/favorite/rpc.md",
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

# ── 第 ⑦ 层 · 锚点文件里**找不到**台账断言提到的那个实体 ──────────────────
# ⚠️ **这一层是 2026-10-26（第二十九轮）从 `global` 域的共享复核器搬进来的**
#    （`tools/check-ledger-selfcheck.py` 是 **global 单写权**的共享面 ⇒ 本域不能改它
#     ⇒ 只能**搬进本域自己的门禁**）。
#    触发它的真实案例：`docs/FileTree/favorite/rpc.md` 断言「`FavoriteAuthorityLookupService`
#    实现 `AuthorityLookup`」并把锚点写成 `favorite_authority.go:74-282`
#    ⇒ 而 Go 侧 `AuthorityLookup` 声明在 `service/common/communityfact/authority_http.go:18`
#    （**like / comment 在用，favorite 零引用**）⇒ **锚点文件里本来就没有这个符号**。
#
# 🔴 **判据必须收紧，否则假阳性 ~90%（global 的实测教训，整段照搬）**：
#   只在「候选实体是 Go 真源里真实**声明过**、且**声明处唯一**」时才参与判定。
#   ⇒ Java 类名 / 非代码词 / 字段名 **自动出局**（不是 Go 声明 ⇒ 不归这一层管）。
_ENTITY_STOPWORDS = frozenset('''
    ctx err nil func return error string int int64 int32 bool byte rune
    true false new make len cap copy append panic recover defer go select
    case default switch range var const type interface struct map chan
    import package break continue else fallthrough goto package init
    timeout cancel done chan errs req resp res data buf msg value result
    size count index idx key name path file line num total start end
'''.split())
# 🔴 **必须是有辨识度的符号** —— 口径：小写段 ≥2 字母 + 紧接一个大写字母
#   （`GetTaskHandler` ✔ · `appendAuthHandler` ✔ · `HTTP` ✘ · `enabled` ✘）
_DISTINCTIVE_RE = re.compile(r'^[A-Za-z_][a-z]{2,}[A-Z][A-Za-z0-9]*$')
# 🔴 **台账必须「断言这个符号在这个文件里」才参与判定** —— 否则「一句话里同时出现
#    多个锚点和多个符号、且它们分属不同文件」的**清单式写法**会持续假阳性。
_CONTAINMENT_MARK = re.compile(
    r'(实现|处的|里的|中的|声明|定义|定义在|写在|位于)')

# ── N5 · 台账**记述历史纠错** ⇒ 那条引用是「当时的错」，不是「现在的悬空引用」 ──
# ⚠️ **必须行级局部判定**（只看引用所在行与其 ±1 行）——
#    共享工具那一版是「取来源文件**前 2000 字**」⇒ 极宽 ⇒ 几乎所有引用都被去噪
#    （实测：178 条里 173 条被去噪）⇒ 那是**假阴性**，不能照抄。
#    本域的判据：叙述必须**贴着**那条引用（同一行或邻行）才生效。
N5_NARRATION_RE = re.compile(
    r"(越界|改正|订正|修正|已改|补成|应为|原写|原为|误写|误指|"
    r"不存在|已删除|已移除|不是悬空|历史记述|当时的错|修前)")
# ⚠️ **第 ⑤ 层（仓内路径）的同型去噪**（2026-10-26 · 第二十九轮补）
#   台账会**引用自己修之前的写法**当证据（例：「§38.4 修前是
#   `like-rpc/service/LikeDomainService.java` ⇒ 已补全为仓内全路径」）
#   ⇒ 那条路径**当时就存在**（它是**历史记述**，不是**活指针**）
#   ⇒ ⇒ 与 ⑥ 层的 N5 同理：**判提示级，不判死链**。
#   ⚠️ **必须行级局部判定**（同一行或邻行有叙述词才生效）——
#      否则文件里任意一处叙述词会把**整个文件**的路径都去噪（实测假阴性）。
PATH_NARRATION_RE = N5_NARRATION_RE


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

    @return (gates, hints, stats, resolved)
      `resolved` = [(路径, 起行, 止行, [候选文件], [来源文件], {来源文件: 行号集合})]
      ⇒ **第 ⑦ 层（实体归属判据）的输入**（2026-10-26 新增）。
    """
    gates, hints = [], []
    stats = defaultdict(int)
    seen = {}
    resolved = []

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
        # 🔴🔴 **但「历史记述」只该影响「悬不悬空」那套计数，不该让第 ⑦ 层的补充判据失去覆盖**
        #    （2026-10-26 · 第二十九轮实测踩到 ⇒ 已修）：
        #    同一个锚点 `favorite_authority.go:74-282`
        #      在**台账里**是「历史记述」（我写 §38 时引用了它）
        #      在**FileTree 文档里**是**活的错断言**（`实现 AuthorityLookup`）
        #    ⇒ 旧写法「任一位置被叙述 ⇒ 整条跳过」⇒ **第 ⑦ 层被误杀**（假阴性）
        #    ⇒ ⇒ **按位置拆开**：叙述的位置判提示级；**活的位置照常进 resolved**
        #    （与 `global` 那边「噪声引用也要继续解析文件」是同一条原则）
        live = [(f, ln) for f, ln in locs if not narrated(f, ln)]
        narr = [(f, ln) for f, ln in locs if narrated(f, ln)]
        if narr:
            stats["narrated"] += 1
            hints.append(dict(kind="go-ref-narrated", file=narr[0][0], line=narr[0][1],
                              detail=f"{path}:{s}-{e} —— 台账在**记述历史纠错**（那时的错），非悬空引用"))
            if not live:
                continue
            locs = live
            srcs = sorted({f for f, _ in locs})

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
            resolved.append((path, s, e, [direct[1]], srcs, _hits_of(locs)))
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
        resolved.append((path, s, e, [x[1] for x in top], srcs, _hits_of(locs)))
    return gates, hints, dict(stats), resolved


def _hits_of(locs):
    """`[(文件, 行号), …]` ⇒ `{文件: {行号, …}}`（第 ⑦ 层取窗口要用）。"""
    out = defaultdict(set)
    for f, ln in locs:
        out[f].add(ln)
    return dict(out)


def _read_text(path):
    """读文件（容错）—— 第 ⑦ 层要读真源文件与台账文件。"""
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except OSError:
        return ""


# ==================================================================
# 第 ⑦ 层 · 锚点文件里**找不到**台账断言提到的那个实体
# ==================================================================
def _local_window_at(texts, sf, ln, path, s, e, width=45):
    """取**某一个出现点**处的窗口（±width 字符）。

    🔴 **必须「按出现点」取，不能把所有出现点的窗口拼在一起**
       （2026-10-26 · 第二十九轮实测踩到 ⇒ 已修）：
       同一个锚点会**同时**出现在「**错断言**的那一行」与「**讲这个锚点的历史**的那几行」
       ⇒ 拼起来之后，候选集是**并集** ⇒ 「**全部缺失**才报」这条规则
       会被**别处的正确候选**顶掉 ⇒ ⇒ **真缺陷被误杀**（假阴性）。
       ⇒ ⇒ **逐个出现点独立判定**：某一行自己断言的符号不在锚点文件里 ⇒ 那行就该报。

    ⚠️ **不用整行** —— 台账单元格一行可达 10 KB
    ⇒ 整行做候选提取会引入大量无关符号 ⇒ 判据永远不触发。
    ⚠️ **窗口必须窄（45 字符，实测校准）** —— 台账里常出现
    「`a.go:55` 的 X | Y | **消费点**：`Z(...)`」这种**一行多事实**的表格行
    ⇒ 窗口宽 ⇒ 把**别处**的符号也算成「锚点处的实体」⇒ 假阳性。
    ⚠️ **从内存里的 `texts` 取，不读盘**（2026-10-26 改）—— `global` 那版是
    「写盘 → 跑 → 还原」⇒ 变异自证**会碰磁盘**（中途崩了就留下变异过的台账）。
    本域改成纯内存 ⇒ 变异自证零副作用，且与 ⑤⑥ 两层的变异写法一致。
    """
    lines = texts.get(sf, "").splitlines()
    if ln - 1 >= len(lines):
        return ""
    line = lines[ln - 1]
    pat = re.compile(re.escape(path) + r':(\d+)(?:-(\d+))?')
    for m in pat.finditer(line):
        ms = int(m.group(1))
        me = int(m.group(2)) if m.group(2) else ms
        if (ms, me) != (s, e):
            continue
        lo = max(0, m.start() - width)
        hi = min(len(line), m.end() + width)
        return line[lo:hi]
    return ""


def _entity_candidates(text):
    """从台账窗口里提取「断言提到的实体」候选（Go 符号名）。

    ⇒ 只认**反引号包裹**的片段（那是台账引用代码/符号的惯用写法）
    ⇒ 🔴 **带点（`pkg.Symbol`）的片段一律跳过** ——
      实测：台账常写「`a.go:95` 的原生 `TimeoutHandler` 被 `observability.DisableNativeRestTimeout`」
      ⇒ 带点的那个**明确属于另一个包** ⇒ 它是**跨文件对照**，不是「锚点处的实体」。
    ⇒ 片段里若含 `:` `/` `\\` ⇒ 是路径 / file:line / URL ⇒ 跳过
    ⇒ ⚠️ **只保留「台账断言了包含关系」的候选** —— 窗口里出现一个符号
      ≠ 台账说它在这个锚点文件里。
    """
    cands = []
    for span_m in re.finditer(r'`([^`]+)`', text):
        span = span_m.group(1)
        if ':' in span or '/' in span or '\\' in span:
            continue
        if '.' in span:
            continue
        if span.endswith(('.go', '.java', '.md', '.json', '.yml', '.yaml', '.xml')):
            continue
        for m in re.finditer(r'[A-Za-z_][A-Za-z0-9_]*', span):
            seg = m.group(0)
            if len(seg) < 4 or seg in _ENTITY_STOPWORDS:
                continue
            if not _DISTINCTIVE_RE.match(seg):
                continue
            # 🔴 **必须用绝对位置** —— `m.start()` 是**片段内**偏移，
            #    直接拿去切 `text` ⇒ 切到的是**窗口开头** ⇒ 判据恒不成立（global 实测踩过）
            pos = span_m.start(1) + m.start()
            pre = text[max(0, pos - 24):pos]
            if not _CONTAINMENT_MARK.search(pre):
                continue
            if seg not in cands:
                cands.append(seg)
    return cands


def _symbol_index(go_root, limit_dirs=('service',)):
    """真源里的「声明符号 → 文件列表」索引（**懒建**：只有真的要用才建）。

    ⇒ 只收**声明处**的符号（func / type / 单行 var / const），
      不是全文所有标识符 ⇒ 索引小、且「这个符号在哪个文件声明过」才有意义。
    """
    idx = {}
    decl = re.compile(
        r'^\s*(?:func\s+(?:\([^)]*\)\s*)?|type\s+|var\s+|const\s+)([A-Za-z_][A-Za-z0-9_]*)',
        re.M)
    for sub in limit_dirs:
        base = os.path.join(go_root, sub)
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if not fn.endswith('.go'):
                    continue
                full = os.path.join(dp, fn)
                rel = os.path.relpath(full, go_root)
                for m in decl.finditer(_read_text(full)):
                    idx.setdefault(m.group(1), set()).add(rel)
    return idx


def check_entity_anchor(root, texts, resolved, go_root):
    """第 ⑦ 层：台账断言「锚点文件里有符号 X」，但**那个文件里找不到 X**。

    🔴 **收紧后的判据**（第一版太宽 ⇒ 假阳性 ~90% ⇒ 不可用）：
       只在**候选实体是 Go 真源里真实「声明过」且「声明处唯一」**时才参与判定。
       ⇒ 于是这些**自动出局**（不是 Go 声明 ⇒ 不归这一层管）：
         · **Java 类名**（交叉栈台账里「Go 侧 X ⇔ Java 侧 Y」的对照写法极常见）；
         · **非代码词**（`XADD` / `yaml` / `Java` / `Boot` / `OTel`）；
         · **字段名 / 局部变量名**。
       ⇒ 判据收紧成：**「台账说这个锚点处是符号 X；X 在真源里被声明过（且声明处唯一）；
         但 X 不在这个锚点文件里」⇒ 锚点很可能指错了文件。**

    ⚠️ **本域口径与 `global` 不同（实测）**：本域台账多用「表格列式」写法
       （符号在左列、锚点在中列 ⇒ **相隔几十上百字符**）⇒ 45 字符窗口取不到候选；
       本域真正能命中的是「**断言式**」写法（`… 实现 \`Symbol\` …` 紧邻锚点）。
       ⇒ ⇒ **本层在本域台账上覆盖有限**，但它覆盖到的**都是真缺陷**
       （实测唯一命中 = `docs/FileTree/favorite/rpc.md` 的 `AuthorityLookup` 套错类型）。
       ⇒ 为此**额外统计「覆盖率」**并在覆盖为 0 时出提示级信号（见 `no-candidate`）。

    @param resolved  [(路径, 起行, 止行, [候选文件路径], [台账文件], {台账文件: 行号集合})]
    @return (gates, hints, stats)
    """
    gates, hints = [], []
    stats = defaultdict(int)
    sym_idx = None
    for path, s, e, files, srcs, hits in resolved:
        stats["anchors"] += 1
        if not files:
            continue
        blob = None
        for sf in sorted(hits):
            for ln in sorted(hits[sf]):
                stats["locations"] += 1
                win = _local_window_at(texts, sf, ln, path, s, e)
                if not win:
                    continue
                stats["window"] += 1
                cands = _entity_candidates(win)
                if not cands:
                    continue
                stats["candidates"] += 1
                if sym_idx is None:
                    sym_idx = _symbol_index(go_root)
                # ── 只保留「真源里真实声明过、且声明处唯一」的候选 ──
                strong = [c for c in cands if len(sym_idx.get(c, ())) == 1]
                if not strong:
                    stats["no-strong-candidate"] += 1
                    continue
                if blob is None:
                    blob = '\n'.join(_read_text(f) for f in files)
                missing = [c for c in strong if c not in blob]
                # ⚠️ **必须「本行的候选全部缺失」才报** —— 只要有一个候选在文件里
                #    ⇒ 这一行的锚点大概率是对的
                if not missing or len(missing) != len(strong):
                    stats["ok"] += 1
                    continue
                where = ['%s（实际在 %s）' % (c, sorted(sym_idx[c])[0]) for c in missing]
                stats["missing"] += 1
                gates.append(dict(kind="entity-not-in-anchor-file", file=sf, line=ln,
                                  detail=f"{path}:{s}-{e} —— 锚点文件里**找不到**该行断言的实体："
                                         f"{', '.join(where)}"))
    if stats["anchors"] and not stats["candidates"]:
        hints.append(dict(kind="entity-layer-inert", file="(本域)",
                          detail=f"第 ⑦ 层在 {stats['anchors']} 个锚点 / "
                                 f"{stats.get('locations', 0)} 个出现点上**一个候选都没提取到**"
                                 f" ⇒ 候选口径只覆盖「符号紧邻锚点 + 带包含措辞」的写法"
                                 f"（本域台账多用表格列式写法 ⇒ 覆盖有限）—— "
                                 f"⚠️ **这一层在本域几乎是惰性的**，别把它当「已对齐」的证据"))
    return gates, hints, dict(stats)


def check_repo_paths(root, go_root, texts):
    """第 ⑤ 层：台账里引用的**仓内路径**存不存在。

    ⚠️ 同一段路径在两个仓里都可能是合法的：
      · `service/favorite/favorite-rpc/...` ⇒ **RAG 仓**
      · `service/favorite/rpc/...` ⇒ **Go 真源仓**（真源也是 `service/` 布局）
    ⇒ 判「RAG 仓找不到 **且** 真源仓也找不到」才算缺失。

    ⚠️ **N5 同型去噪**（2026-10-26 · 第二十九轮）：台账会**引用自己修之前的写法**
    当证据 ⇒ 那是**历史记述**，不是**活指针** ⇒ 判提示级（`repo-path-narrated`），
    不判死链 ⇒ **行级局部判定**（同一行或邻行有叙述词才生效）。
    """
    gates, hints = [], []
    stats = defaultdict(int)
    exact = {}
    ellipsis = {}

    for f in sorted(texts):
        lines = texts[f].splitlines()
        for lineno, line in enumerate(lines, 1):
            for m in REPO_PATH_RE.finditer(line):
                p = m.group(0)
                if "..." in p:
                    stats["ellipsis"] += 1
                    ellipsis.setdefault(p, set()).add(f)
                else:
                    exact.setdefault(p, set()).add((f, lineno))

    def narrated(f, lineno):
        """引用所在行 ±1 行里有没有「历史纠错」的叙述（同 ⑥ 层的 N5）。"""
        lines = texts[f].splitlines()
        lo = max(0, lineno - 2)
        hi = min(len(lines), lineno + 1)
        return any(PATH_NARRATION_RE.search(lines[i]) for i in range(lo, hi))

    for p, locs in sorted(exact.items()):
        locs = sorted(locs)
        srcs = sorted({f for f, _ in locs})
        live = [(f, ln) for f, ln in locs if not narrated(f, ln)]
        if not live:
            stats["narrated"] += 1
            hints.append(dict(kind="repo-path-narrated", file=srcs[0], line=0,
                              detail=f"{p} —— 台账在**记述历史纠错**（那时的错），非悬空引用"))
            continue
        if os.path.exists(os.path.join(root, p)):
            stats["ok"] += 1
            continue
        if go_root and os.path.exists(os.path.join(go_root, p)):
            stats["go"] += 1
            continue
        gates.append(dict(kind="repo-path-missing", file=live[0][0], line=live[0][1],
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
    base_go_gates, _, _, _ = check_go_refs(root, go_root, modcache, goroot_src, texts, index)

    # 变异 ① · 仓内路径：注入一个必不存在的路径
    #   ⚠️ **必须隔两个空行**（2026-10-26 补）—— 第 ⑤ 层新增了「N5 同型去噪」
    #   （引用所在行 ±1 行有叙述词 ⇒ 判历史记述）⇒ 若注入行紧挨着台账最后一行，
    #   而最后一行含叙述词 ⇒ **注入会被去噪** ⇒ 变异自证**恒红**（假的自证）。
    mut1 = dict(texts)
    mut1[first] = texts[first] + "\n\n\n`service/favorite/__mut_missing_xyz__.java`\n"
    g1, _, _ = check_repo_paths(root, go_root, mut1)
    if not g1:
        problems.append("⑤ 层变异（注入不存在的仓内路径）**没被抓到** ⇒ 这一层是恒真的")

    # 变异 ② · Go 锚点：注入一个行号越界的锚点（同样隔两个空行 ⇒ 不被 N5 去噪）
    mut2 = dict(texts)
    mut2[first] = texts[first] + "\n\n\n`service/favorite/rpc/favorite.go:999999`\n"
    g2, _, _, _ = check_go_refs(root, go_root, modcache, goroot_src, mut2, index)
    if not g2:
        problems.append("⑥ 层变异（注入行号越界的 Go 锚点）**没被抓到** ⇒ 这一层是恒真的")

    # 变异 ③ · 第 ⑦ 层实体归属：断言一个「真源里声明过、但锚点文件里没有」的符号
    #   `AuthorityLookup` 声明在 `service/common/communityfact/authority_http.go:18`
    #   （like / comment 在用，**favorite 零引用**）⇒ 把它断言在
    #   `service/favorite/rpc/internal/model/favorite_authority.go` 处 ⇒ **必须被抓到**。
    mut3 = dict(texts)
    mut3[first] = texts[first] + (
        "\n| 🔴 变异样本 | `service/favorite/rpc/internal/model/favorite_authority.go:74-94`"
        " 处的 `AuthorityLookup` | — | — |\n")
    g3, _, _, res3 = check_go_refs(root, go_root, modcache, goroot_src, mut3, index)
    if g3:
        # 锚点本身必须先能解析（否则测的不是第 ⑦ 层）
        problems.append("⑥ 层对变异样本报错 ⇒ 第 ⑦ 层的样本选错了（锚点本身不该悬空）")
    else:
        g3e, _, _ = check_entity_anchor(root, mut3, res3, go_root)
        if not any("AuthorityLookup" in g["detail"] for g in g3e):
            problems.append("⑦ 层变异（断言锚点文件里没有的实体）**没被抓到** ⇒ 这一层是恒真的")

    # 变异 ④ · 第 ⑦ 层**反向对照**：符号**确实在**锚点文件里 ⇒ **不得**报
    #   （判据太宽 = 假阳性 ⇒ 必须有阴性对照钉住）
    mut4 = dict(texts)
    mut4[first] = texts[first] + (
        "\n| 🔴 阴性对照 | `service/favorite/rpc/internal/model/favorite_authority.go:74-94`"
        " 处的 `parseAuthorityID` | — | — |\n")
    _, _, _, res4 = check_go_refs(root, go_root, modcache, goroot_src, mut4, index)
    g4e, _, _ = check_entity_anchor(root, mut4, res4, go_root)
    if any("parseAuthorityID" in g["detail"] for g in g4e):
        problems.append("⑦ 层阴性对照（符号**就在**锚点文件里）**误报了** ⇒ 判据太宽（假阳性）")

    # 变异 ⑤ · 第 ⑦ 层**「叙述位 vs 活位」交互**（2026-10-26 第二十九轮实测踩到）
    #   同一个锚点在**另一个台账文件**里被写成「历史记述」（含叙述词 ⇒ N5 去噪）
    #   而在**本文件**里是**活的错断言** ⇒ ⇒ 必须**仍然报出来**
    #   （旧写法「任一位置被叙述 ⇒ 整条跳过」⇒ 会**误杀** ⇒ 假阴性）
    others = [f for f in sorted(texts) if f != first]
    if not others:
        problems.append("⑦ 层变异 ⑤ 没法做（只有一个台账文件）")
    else:
        other = others[0]
        mut5 = dict(texts)
        mut5[other] = texts[other] + (
            "\n| 修前 | `service/favorite/rpc/internal/model/favorite_authority.go:74-94`"
            " 处的 `AuthorityLookup` | — | — |\n")
        mut5[first] = texts[first] + (
            "\n| 🔴 变异样本⑤ | `service/favorite/rpc/internal/model/favorite_authority.go:74-94`"
            " 处的 `AuthorityLookup` | — | — |\n")
        _, _, _, res5 = check_go_refs(root, go_root, modcache, goroot_src, mut5, index)
        g5e, _, _ = check_entity_anchor(root, mut5, res5, go_root)
        if not any("AuthorityLookup" in g["detail"] for g in g5e):
            problems.append("⑦ 层变异 ⑤（同一锚点在别处是「历史记述」）**没被抓到**"
                            " ⇒ 第 ⑥ 层的 N5 去噪把第 ⑦ 层**误杀**了（假阴性）")

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

    # ⚠️ **第 ⑤⑥⑦ 层的「本域全量」范围** = 三档台账 + 本域自有的文件树文档
    #   （`EXTRA_SCOPED_FILES`，见该常量处的说明：只对这三层生效）
    scoped_texts = dict(texts)
    extra_present = []
    for p in EXTRA_SCOPED_FILES:
        if os.path.exists(os.path.join(root, p)):
            scoped_texts[p] = open(os.path.join(root, p), encoding="utf-8").read()
            extra_present.append(p)
        else:
            extra_present.append(p + "（缺失）")

    if args.mutation_test:
        index, libs = build_go_index(go_root, modcache, goroot_src)
        problems = run_mutation_test(root, go_root, modcache, goroot_src, scoped_texts, index)
        if problems:
            print("❌ 变异自证失败：")
            for p in problems:
                print(f"   · {p}")
            return 1
        print(f"✅ 变异自证通过：⑤ 仓内路径层、⑥ Go 锚点层、⑦ 实体归属层"
              f"**都能抓到注入的缺陷**（且 ⑦ 层的阴性对照**正确地不报**）"
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
    path_gates, path_hints, path_stats = check_repo_paths(root, go_root, scoped_texts)
    gates.extend(path_gates)
    hints.extend(path_hints)

    # ---------- ⑥ Go 侧 file:line 锚点可解析 ----------
    index, libs = build_go_index(go_root, modcache, goroot_src)
    go_ref_gates, go_ref_hints, go_ref_stats, resolved = check_go_refs(
        root, go_root, modcache, goroot_src, scoped_texts, index)
    gates.extend(go_ref_gates)
    hints.extend(go_ref_hints)

    # ---------- ⑦ 锚点文件里找不到「台账断言的实体」 ----------
    entity_hints, entity_stats = [], {}
    if go_root:
        entity_gates, entity_hints, entity_stats = check_entity_anchor(
            root, scoped_texts, resolved, go_root)
        gates.extend(entity_gates)
        hints.extend(entity_hints)
    else:
        hints.append(dict(kind="go-root-missing",
                          detail="找不到 Go 真源 ⇒ 跳过第 ⑦ 层（实体归属判据）"))

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
        entity=entity_stats,
        scoped_extra_files=extra_present,
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
    print(f"  第 ⑦ 层实体归属：缺失 {entity_stats.get('missing', 0)} 条 / "
          f"判定通过 {entity_stats.get('ok', 0)} 条 / "
          f"覆盖锚点 {entity_stats.get('anchors', 0)} 个 / 出现点 {entity_stats.get('locations', 0)} 个"
          f"（取到窗口 {entity_stats.get('window', 0)} / 取到候选 {entity_stats.get('candidates', 0)}）")
    print(f"  扫描范围：三档台账 {len(ledger_files)} 份 + 本域文件树文档 "
          + ", ".join(extra_present))
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
