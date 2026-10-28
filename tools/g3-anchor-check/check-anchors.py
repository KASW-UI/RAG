#!/usr/bin/env python3
"""Go 出处引用漂移守门（域内）—— RAG 注释里「Go: 文件:行号」是否还指得到那一行。

为什么需要它（AGENTS.md §3.3.2 / §3.1）：
    以「Go: …」措辞写在 javadoc / `@DisplayName` / 断言消息里的断言，是**高危项** ——
    它断言了一个事实，却**改一行断言都不会有任何测试变红** ⇒ 绿测试可能守着一个错的事实。
    漂移的四种形态（本守门只挡这四类，**零内容匹配 ⇒ 零误报**）：
      ① **文件指错**：行号对、文件错（最毒 —— 按图索骥「找不到」却不会怀疑出处本身）
      ② **行号越界**：那一行已经不存在（文件改了 / 行号写错 / 范围末尾越过 EOF）
      ③ **空行**：引用指到一行空白（多半是文件被截短/改版）
      ④ **版本写错**：引的是**另一份内容不同的副本**（如 `gorm@v1.31.2`，
         而真源 `go.mod` pin 的是 `v1.31.1`）⇒ 行号照样「在范围内」⇒ 别的判据抓不到

判据（零误报口径）：
    把 `文件:行号` 拿回真源读那一行 ⇒ **只要求「那一行存在且非空」**。
    ⚠️ 不做内容匹配：太严会大面积误报（同一段代码在不同版本/不同文件里都合理）。

覆盖的「真源根」（缺一个就会把「我解析不了」伪装成「引用是错的」）：
    · Go 真源仓（`Sea-RideTheWind_golang`）
    · Go **标准库**（`$GOROOT/src`）
    · **module cache**（go-zero / go-queue / go-redis / grpc / gorm / otel / pgx …，
      按真源 `go.mod` pin 的版本 ⇒ 与真源实际编译的那一份一致）
    · **RAG 自己的** 文件（`application.yml` / `db/migration/*.sql`）—— 有些注释指的是 RAG 侧文件

🔴 **输入根探针（`probe_roots()`）—— 头尾各跑一次**：
    · 头探针不过 ⇒ **exit 2**（**绝不**静默降级成「少查了几个根 ⇒ 数字看着更干净」）
    · 尾探针与头探针不一致 ⇒ **exit 2**（module cache 可见性**会间歇** ⇒ 本次报数不可复算）
    · 探针结果**随数字一起报出** ⇒ 同一指标在两种输入状态下是两个数

⚠️ 解析器必须**够宽**，否则会把「我解析不了」伪装成「引用是错的」：
    · 模块路径是**多段**的（`gorm.io/gorm` / `github.com/golang-jwt/jwt/v4`）
    · 引用里常**省掉 host 段**（`golang-jwt/jwt/v4` / `prometheus/client_golang`）
    · `@版本` 可能**粘在模块路径最后一段**（`gorm.io/gorm@v1.31.1/callbacks/create.go`）
    · `@版本` 也可能是**独立一段**（`v1.31.2/callbacks/update.go`）
    · `.../` 是「同上」省略前缀
    · 引用可能指 **RAG 自己的** yml / 迁移脚本

用法：
    python3 tools/g3-anchor-check/check-anchors.py                     # 扫 G3 四域
    python3 tools/g3-anchor-check/check-anchors.py --domains article    # 扫指定域
    python3 tools/g3-anchor-check/check-anchors.py --all                # 扫全部 10 域

退出码：0 = 无漂移；1 = 有待查项（行号越界 / 空行 / 文件找不到 / 版本写错）；
        2 = **输入不可见 / 自身故障**（探针不过 ⇒ 本次报数不可信 ⇒ 不出结论）。
"""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

RAG_ROOT = Path(__file__).resolve().parents[2]
GO_REPO = Path(os.environ.get("GO_REPO", "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"))
MODCACHE = Path(subprocess.run(["go", "env", "GOMODCACHE"], capture_output=True, text=True).stdout.strip()
                 or "/Users/pr/go/pkg/mod")
GOROOT = Path(subprocess.run(["go", "env", "GOROOT"], capture_output=True, text=True).stdout.strip())

ALL_DOMAINS = ["article", "comment", "favorite", "hot", "like", "message", "task", "users", "follow"]

# 真源 go.mod 里 pin 的版本 ⇒ 与真源实际编译的那一份一致（见真源 go.mod）
# ⚠️ 这是**版本核对的唯一真源**：引用里写的版本必须等于这里的 pin，否则它指到另一份副本。
PINNED = {
    "github.com/zeromicro/go-zero": "v1.10.2",
    "github.com/zeromicro/go-queue": "v1.2.2",
    "github.com/redis/go-redis/v9": "v9.20.0",
    "google.golang.org/grpc": "v1.81.1",
    "gorm.io/gorm": "v1.31.1",
    "go.opentelemetry.io/otel": "v1.44.0",
    "go.opentelemetry.io/otel/sdk": "v1.44.0",
    "go.opentelemetry.io/otel/trace": "v1.44.0",
    "github.com/golang-jwt/jwt/v4": "v4.5.2",
    "github.com/jackc/pgx/v5": "v5.10.0",
    "github.com/prometheus/client_golang": "v1.23.2",
    # 🆕 这两行是**假阳性簇的根因**（G4 反馈单 1 张 · 2026-10-29）：
    #    真源 `go.mod:27` / `:11` 明明 pin 着，但缺了它们 ⇒ 带版本前缀的路径解析不到
    #    ⇒ 回退**裸文件名匹配** ⇒ 撞上**同名但行数完全不同的另一个文件**
    #    ⇒ 报「行号越界 / 文件找不到」⇒ 实测 29/56 = 52% 是假阳性。
    #    ⚠️ 版本必须取真源 `go.mod` 的 pin —— module cache 里同一模块可能躺着多个版本
    #       （go-zero 就同时有 v1.10.0 / v1.10.2 / v1.10.3）⇒ 读错版本**同样是漂移**
    #       （同 `docs/Pitfalls/global/common-tails.md` PIT-GLOBAL-001）。
    "gorm.io/driver/postgres": "v1.6.0",   # 真源 go.mod:27
    "github.com/lovoo/goka": "v1.1.16",    # 真源 go.mod:11
}

EXTS = (".go", ".sql", ".yaml", ".yml")
REF_RE = re.compile(r"(?<![\w/.-])((?:[A-Za-z0-9_.~@-]+/)*[A-Za-z0-9_~@-]+\.(?:go|sql|yaml|yml)):(\d+)(?:\s*[-–]\s*(\d+))?")
VER_RE = re.compile(r"v\d+\.\d+\.\d+")


def module_variants(mod_path: str):
    """模块路径在注释里可能被写成多种形态 ⇒ 全部接受（**最长优先**匹配）。

    `github.com/golang-jwt/jwt/v4` ⇒
        `github.com/golang-jwt/jwt/v4` · `golang-jwt/jwt/v4` · `jwt/v4` · `jwt` · `v4`
    `gorm.io/gorm` ⇒
        `gorm.io/gorm` · `gorm`
    `github.com/redis/go-redis/v9` ⇒
        `github.com/redis/go-redis/v9` · `redis/go-redis/v9` · `go-redis/v9`
        · `github.com/redis/go-redis` · `redis/go-redis` · `go-redis` · `v9`

    ⚠️ 「去掉末尾大版本段」那一组（`go-redis` / `jwt` / `pgx`）是**必须**的：
        module cache 的目录名是「模块路径末段 + @版本」（`.../go-redis/v9@v9.20.0`），
        而注释里人写的往往是**仓库名**（`go-redis@v9.20.0/options.go`）
        ⇒ 不认这一组 ⇒ 解析不到 ⇒ 又把「解析不了」伪装成「引用是错的」。
    """
    segs = mod_path.split("/")
    groups = [segs]
    # 末尾大版本段（`/v9`）去掉再来一组 ⇒ 覆盖「按仓库名写」的形态
    if len(segs) > 1 and re.fullmatch(r"v\d+", segs[-1]):
        groups.append(segs[:-1])
    out = []
    for g in groups:
        for i in range(len(g)):
            out.append("/".join(g[i:]))
    # 去重 + 保序
    seen, uniq = set(), []
    for v in out:
        if v not in seen:
            seen.add(v)
            uniq.append(v)
    return uniq


# (变体 → 模块路径)，按变体长度倒序 ⇒ 「最长优先」匹配
VARIANT_TO_MODULE = {}
for _m in PINNED:
    for _v in module_variants(_m):
        VARIANT_TO_MODULE.setdefault(_v, _m)
VARIANT_ORDER = sorted(VARIANT_TO_MODULE, key=lambda v: (-len(v.split("/")), -len(v)))


def walk(base: Path, exts=EXTS):
    out = []
    if not base.is_dir():
        return out
    for dp, dn, fn in os.walk(base):
        dn[:] = [d for d in dn if d not in (".git", "node_modules", "target", "out")]
        for f in fn:
            if f.endswith(exts):
                out.append(Path(dp) / f)
    return out


def module_root(mod: str):
    ver = PINNED.get(mod)
    if not ver:
        return None
    p = MODCACHE / f"{mod}@{ver}"
    return p if p.is_dir() else None


def build_index(domains):
    """⇒ (idx_by_basename, idx_by_module_alias, modver_of_file, n_files)

    `idx_by_module_alias`：module cache 里文件的「模块目录名 + 相对路径」别名
    （如 `sdk@v1.44.0/trace/span.go` ⇒ 别名 `sdk/trace/span.go`）⇒
    让引用写成**导入路径形态**（`sdk/trace/span.go:631-655`）时也能解析
    —— 不加它就会把「解析不了」伪装成「引用是错的」。
    `modver_of_file`：文件 → (模块路径, 版本) ⇒ 供**版本核对**用。
    """
    idx = {}
    alias = {}
    modver = {}
    roots = [GO_REPO, GOROOT / "src"]
    # (缓存目录, 模块路径) —— ⚠️ 缓存目录名是「模块路径末段 + @版本」，
    #    **不是**「完整模块路径 + @版本」⇒ 必须显式带着模块路径走，别靠目录名反推
    module_roots = []
    for mod in PINNED:
        r = module_root(mod)
        if r:
            module_roots.append((r, mod))
    files = []
    for r in roots:
        files += walk(r, exts=(".go",) if r == GOROOT / "src" else EXTS)
    for r, modpath in module_roots:
        mf = walk(r, exts=(".go",))
        files += mf
        modname = r.name.split("@")[0]
        for f in mf:
            alias[f"{modname}/{f.relative_to(r).as_posix()}"] = f
            modver[f] = (modpath, PINNED[modpath])
    # RAG 自己的文件（有些注释指的是 RAG 侧 yml / 迁移脚本）
    for d in domains:
        files += walk(RAG_ROOT / "service" / d)
    for f in files:
        idx.setdefault(f.name, []).append(f)
    return idx, alias, modver, len(files)


def split_ver(part):
    """`gorm@v1.31.1` → ('gorm', 'v1.31.1')；`v1.31.1` → (None, 'v1.31.1')；无版本 → (part, None)"""
    if "@" in part:
        head, _, tail = part.partition("@")
        if VER_RE.fullmatch(tail):
            return head, tail
    if VER_RE.fullmatch(part):
        return None, part
    return part, None


def normalize(rel: str):
    """→ (要匹配的后缀, 模块路径 or None, 引用里写的版本 or None)

    顺序**不能反**（踩过的坑）：
      ① 先按**模块前缀**最长优先匹配 —— 此时要把 `@版本` 从前缀的最后一段上剥掉
         （`gorm.io/gorm@v1.31.1/callbacks/create.go` ⇒ 前缀 `gorm.io/gorm` + 版本 `v1.31.1`）
      ② 再从**剩余段**里抽独立版本段（`v1.31.2/callbacks/update.go`）
    ⚠️ 反过来做 ⇒ `gorm.io/gorm@v1.31.1` 会被拆成「`gorm.io` + `gorm@版本`」⇒ 解析不到
      ⇒ ⇒ 又把「解析不了」伪装成「引用是错的」。
    """
    parts = rel.split("/")
    mod = None
    ver = None
    for variant in VARIANT_ORDER:
        vp = variant.split("/")
        if len(parts) < len(vp):
            continue
        # ⚠️ `@版本` 可能粘在**前缀区任意一段**上（`go-redis@v9.20.0/...` / `gorm.io/gorm@v1.31.1/...`）
        #    ⇒ 逐段剥掉版本再比
        cleaned, seg_ver, bad = [], None, False
        for p in parts[: len(vp)]:
            c, sv = split_ver(p)
            if c is None:
                bad = True
                break
            cleaned.append(c)
            if sv and seg_ver is None:
                seg_ver = sv
        if bad or cleaned != vp:
            continue
        mod = VARIANT_TO_MODULE[variant]
        ver = seg_ver
        parts = parts[len(vp):]
        break
    if mod is None:
        # ① 前缀没匹配上 ⇒ 看看是不是「版本独立成段」或「版本粘在第一段」
        for i, p in enumerate(parts):
            clean, seg_ver = split_ver(p)
            if seg_ver:
                ver = seg_ver
                if clean is None:
                    # 整段就是版本号（`v1.31.2/callbacks/update.go`）⇒ 整段去掉
                    parts = parts[:i] + parts[i + 1:]
                elif clean != p:
                    # 版本粘在段尾（`gorm@v1.31.1`）⇒ 只留段名
                    parts = parts[:i] + [clean] + parts[i + 1:]
                break
        # ② 版本**唯一** ⇒ 可反推模块（`v9.20.0/options.go` ⇒ go-redis/v9）
        #    ⚠️ 只有唯一命中才反推 ⇒ 多个模块同版本（otel 三件套都是 v1.44.0）时不敢猜
        if ver:
            hits = [m for m, pv in PINNED.items() if pv == ver]
            if len(hits) == 1:
                mod = hits[0]
    # `.../` 是「同上」省略前缀 ⇒ 只保留尾部
    while parts and parts[0] == "...":
        parts = parts[1:]
    if not parts:
        return None, mod, ver
    return "/".join(parts), mod, ver


def resolve(rel: str, idx, alias, modver):
    suffix, mod, ver = normalize(rel)
    if suffix is None:
        return [], "NO-SUCH-PATH", None
    # ⓿ 版本核对（**知道模块时**）：引用里写的那一版，是不是真源 go.mod 实际 pin 的那一版？
    #    ⇒ 不是 ⇒ 它指到**另一份内容不同的副本** ⇒ 行号照样「在范围内」⇒ 别的判据抓不到
    ver_note = None
    if ver and mod and PINNED.get(mod) and ver != PINNED[mod]:
        ver_note = f"引用写 {ver}，真源 go.mod pin 的是 {PINNED[mod]}"
    # ① 已知模块 ⇒ **先在模块根里解析**（别让同名文件劫持）
    #    ⚠️ 反例：`sdk/trace/span.go` ⇒ module cache 里 `otel/sdk/trace/span.go` 与
    #       `otel/trace/span.go` **同名** ⇒ 谁先谁说了算 ⇒ 必须模块根优先
    cands = []
    if mod:
        root = module_root(mod)
        if root:
            bucket = idx.get(suffix.split("/")[-1], [])
            cands = [f for f in bucket if str(f).startswith(str(root)) and str(f).endswith(suffix)]
    # ② module cache 的「模块目录名 + 相对路径」别名（导入路径形态）
    if not cands and suffix in alias:
        cands = [alias[suffix]]
    # ③ 中间带 `...` 的省略写法（`article-api/.../application.yml`）⇒ 只按文件名匹配
    elif not cands and "..." in suffix.split("/"):
        cands = idx.get(suffix.split("/")[-1], [])
    # ④ 兜底：按后缀在全部真源索引里找
    elif not cands:
        bucket = idx.get(suffix.split("/")[-1], [])
        cands = [f for f in bucket if str(f).replace("\\", "/").endswith(suffix)]
    if not cands:
        return [], "NO-SUCH-PATH", ver_note
    kind = "repo-absolute" if suffix.split("/")[0] in ("service", "cmd", "api", "proto", "doc") else "suffix"
    # ⓿-b 版本核对（**不知道模块时**）：看它实际落到了 module cache 的哪一版
    if ver and not ver_note:
        for c in cands:
            mv = modver.get(c)
            if mv and mv[1] != ver:
                ver_note = f"引用写 {ver}，实际解析到 {mv[0]}@{mv[1]}"
                break
    return cands, kind, ver_note


def probe_roots():
    """🔴 **输入根探针** —— 每个真源根取一个已知文件，探不到就**硬失败**。

    为什么必须硬失败（而不是「跳过这个根继续跑」）：
        `build_index` 里 `module_root(mod)` 返回 `None` 时该模块被**静默跳过**
        ⇒ 带版本前缀的路径解析不到 ⇒ 回退裸文件名匹配
        ⇒ **撞上同名但行数完全不同的另一个文件** ⇒ 报「行号越界 / 文件找不到」
        ⇒ **假阳性**（实测 29/56 = 52%）⇒ ⇒ **「我解析不了」被伪装成「引用是错的」**。
        ⚠️ 本函数**曾经只写在 docstring 里**（= 「注解写了 ≠ 机制生效」）
           ⇒ 现补上实现：探针不过 ⇒ **exit 2**，**绝不静默降级成「少查了几个根」**。

    为什么探针要跑**两次**（头 + 尾）：
        module cache 的可见性**会间歇**（同一路径 5 分钟内反复「可读 → 不可读 → 可读」）
        ⇒ 只探一次 ⇒ **同一份代码两次跑出两个结论** ⇒ 数字不可复算
        ⇒ 尾探针发现「扫描期间可见性变了」⇒ 同样 **exit 2**（本次报数不可信）。

    ⇒ **探针结果随数字一起报出** —— 同一指标在两种输入状态下是两个数。
    """
    probe = []

    def add(name, ok, detail):
        probe.append((name, bool(ok), detail))

    add(f"Go 真源仓 {GO_REPO}", (GO_REPO / "go.mod").is_file(), "go.mod")
    src = GOROOT / "src"
    add(f"Go 标准库 {src}", src.is_dir(), "src/")
    for mod in sorted(PINNED):
        root = module_root(mod)
        if root is None:
            add(f"module {mod}@{PINNED[mod]}", False, f"目录不可见：{MODCACHE}/{mod}@{PINNED[mod]}")
            continue
        n = sum(1 for _ in root.rglob("*.go"))
        add(f"module {mod}@{PINNED[mod]}", n > 0, f"{n} 个 .go")
    return probe


def print_probe(probe):
    print("输入根探针（不可见 ⇒ 本次报数不可信 ⇒ exit 2）：")
    for name, ok, detail in probe:
        print(f"  {'✅' if ok else '❌'} {name}  {detail}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--domains", nargs="*", default=["article", "comment", "favorite", "hot"])
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()
    domains = ALL_DOMAINS if args.all else args.domains

    probe = probe_roots()
    print_probe(probe)
    bad = [p for p in probe if not p[1]]
    if bad:
        print(f"\n❌ 输入根探针失败 {len(bad)} 项 ⇒ **输入不可见 ⇒ 本次报数不可信** ⇒ 停（exit 2）")
        for name, _, detail in bad:
            print(f"   · {name}  {detail}")
        return 2
    print()

    rfiles = []
    for d in domains:
        for pat in ("**/src/main/java/**/*.java", "**/src/test/java/**/*.java"):
            rfiles.extend(sorted((RAG_ROOT / "service" / d).glob(pat)))
    rfiles = sorted(set(rfiles))
    if not rfiles:
        print("❌ 一份 java 都没扫到 ⇒ 路径/参数错了 ⇒ 停（别把 0 命中读成「干净」）")
        return 2

    raw = []
    for f in rfiles:
        text = f.read_text(encoding="utf-8", errors="ignore")
        for m in REF_RE.finditer(text):
            raw.append((f, text, m))

    idx, alias, modver, n_index = build_index(domains)
    stats = {"OK": 0, "AMBIGUOUS": 0, "LINE_OOB": 0, "FILE_MISSING": 0, "EMPTY_LINE": 0, "VERSION_MISMATCH": 0}
    problems = []

    for f, text, m in raw:
        rel, n1 = m.group(1), int(m.group(2))
        # ⚠️ 区间组可能不参与（`foo.go:12`）⇒ 必须显式处理 None，否则 int(None) 直接崩
        n2 = int(m.group(3)) if m.group(3) else n1
        line_no = text[: m.start()].count("\n") + 1
        # 出处缺文件名（`xxx_test.go:47` 被写成 `_test.go:47`）⇒ 不可核 ⇒ 单列一类
        if rel.split("/")[-1].startswith("_"):
            stats["FILE_MISSING"] += 1
            problems.append(("FILE_MISSING", f, line_no, rel, n1, n2, "缺文件名", ""))
            continue
        cands, kind, ver_note = resolve(rel, idx, alias, modver)
        if ver_note:
            # 版本写错 ⇒ 引用指到**另一份内容不同的副本** ⇒ 单看行号发现不了 ⇒ 单列一类
            stats["VERSION_MISMATCH"] += 1
            problems.append(("VERSION_MISMATCH", f, line_no, rel, n1, n2, kind, ver_note))
        if not cands:
            stats["FILE_MISSING"] += 1
            problems.append(("FILE_MISSING", f, line_no, rel, n1, n2, kind, ""))
            continue
        ok_any, oob, empty = False, [], []
        for c in cands:
            try:
                lines = c.read_text(encoding="utf-8", errors="ignore").splitlines()
            except OSError:
                continue
            if n1 > len(lines) or n2 > len(lines):
                oob.append((c, len(lines)))
                continue
            if not "".join(lines[n1 - 1: n2]).strip():
                empty.append((c, n1))
            else:
                ok_any = True
        if ok_any:
            stats["AMBIGUOUS" if len(cands) > 1 else "OK"] += 1
            continue
        if empty and not oob:
            stats["EMPTY_LINE"] += 1
            problems.append(("EMPTY_LINE", f, line_no, rel, n1, n2, kind, str(empty[0][0])))
        elif oob:
            stats["LINE_OOB"] += 1
            problems.append(("LINE_OOB", f, line_no, rel, n1, n2, kind,
                             "; ".join(f"{c} 只有 {ln} 行" for c, ln in oob)))

    print(f"域：{', '.join(domains)}")
    print(f"RAG java 文件 {len(rfiles)} 份 · 引用 {len(raw)} 条 · 真源索引 {n_index} 个文件")
    print(f"  OK={stats['OK']}  AMBIGUOUS={stats['AMBIGUOUS']}（同名多文件，机器判不了，需人读）"
          f"  LINE_OOB={stats['LINE_OOB']}  FILE_MISSING={stats['FILE_MISSING']}"
          f"  EMPTY_LINE={stats['EMPTY_LINE']}  VERSION_MISMATCH={stats['VERSION_MISMATCH']}")

    # 🔴 尾探针：扫描期间输入可见性变了 ⇒ **本次报数不可复算** ⇒ 与头探针不一致 ⇒ exit 2
    probe2 = probe_roots()
    drift = [(a[0], a[1], b[1]) for a, b in zip(probe, probe2) if a[1] != b[1]]
    if drift:
        print()
        print("❌ **扫描期间输入根可见性发生变化** ⇒ 本次报数不可信（不可复算）⇒ 停（exit 2）：")
        for name, was, now in drift:
            print(f"   · {name}  头探针={'可见' if was else '不可见'} → 尾探针={'可见' if now else '不可见'}")
        return 2
    print("  尾探针：与头探针一致 ✅（输入在扫描期间未变 ⇒ 本次报数可复算）")

    if problems:
        print()
        for kind in ("LINE_OOB", "EMPTY_LINE", "VERSION_MISMATCH", "FILE_MISSING"):
            lst = [p for p in problems if p[0] == kind]
            if not lst:
                continue
            print(f"--- {kind} （{len(lst)} 条）---")
            for _, f, ln, rel, n1, n2, kd, extra in lst:
                print(f"  {f.relative_to(RAG_ROOT)}:{ln}  {rel}:{n1}" + (f"-{n2}" if n2 != n1 else "") + f"  [{kd}] {extra}")
        print()
        return 1
    print("\n✅ 无「行号越界 / 空行 / 版本写错 / 文件找不到 / 出处缺文件名」")
    return 0


if __name__ == "__main__":
    sys.exit(main())
