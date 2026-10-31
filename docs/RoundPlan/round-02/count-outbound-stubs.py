#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""出向 stub 全表计数（P2-T01 · 交付物）。

用法
----
    python3 docs/RoundPlan/round-02/count-outbound-stubs.py
        ⇒ 只读计数 + 自检（打印 Δ_start / Δ_end · 三态计数 · 零上界清单）· **不写盘**
    python3 docs/RoundPlan/round-02/count-outbound-stubs.py --write-inventory
        ⇒ 额外把落点表写到 `outbound-stub-inventory.md`
    python3 docs/RoundPlan/round-02/count-outbound-stubs.py --check-inventory
        ⇒ 校验落点表与本次扫描**逐字节一致**（不写盘；不一致 ⇒ exit 1）
    python3 docs/RoundPlan/round-02/count-outbound-stubs.py --assert-no-zero-deadline
        ⇒ 「零上界类数」必须 = 0，否则 exit 1

⚠️ 判据纪律（本脚本自己的口径，写在这里以便复核）
----------------------------------------------------
1. **扫描面** = `service/**/src/main/java/**.java`；「出向 stub 提供者」= 满足任一：
   ① 有 `@GrpcClient("...")` 注入点 ② 有 public 方法返回 `*Stub`
   （按 P2-T01「失败处理」：拿不准 ⇒ **保守包含**）
2. **代码态** = 剥掉块注释 / 行注释 / 字符串字面量 ⇒ ⇒ **判据看代码，不看文本**
   （注释里写「withDeadlineAfter」不算数）
3. **三态**（形态轴 · 互斥且穷尽）：
   ① **走统一载体** —— 出向路径上出现 `GoStyleRequestDeadline.applyTo(` 或
      `GoStyleGrpcCallDeadline.apply(`
   ② **手搓** —— 类内自己出现 `remainingMillis(` / `remainingNanos(` / `remainingBudgetMs`
      且自己调 `withDeadlineAfter(`
   ③ **裸 stub** —— 两者皆无
   ⚠️ **形态轴 ≠ 上界轴**：一个类可以是「形态①」而**仍然没有硬上界**
   （`applyTo` 只注入**入向**预算，不含 2s 客户端上界）
4. **上界轴**（独立于形态）：硬上界有三个可能来源，逐个判
   (a) **本地 cap** —— 出向路径上的 `withDeadlineAfter` 实参链里出现 `Math.min(`
       或等价三元 min（⇒ 把一个**常量/配置**压进去 ⇒ 有上界）
       ⚠️ `Math.max(1L, budget)` **不是**上界（它只保下限）⇒ 不算
   (b) **载体自带 cap** —— 载体类里出现 (a) 的形态
   (c) **共享层 cap** —— 模块 yml 有 `rag.protection.outbound-deadline.cap-millis`
   ⇒ ⇒ **零上界** = (a)(b)(c) 全无
5. **Go 对照**（真源 = Go 仓）—— RAG 模块 → Go yaml 目录的映射是**显式表**（见 GO_YAML_MAP）；
   客户端名 → Go 的 `XxxRpcConf:` 段；该段的 `Timeout:` 显式值，
   没有 ⇒ 走 `go-zero zrpc/config.go:31` 的默认 **2000ms**
"""
from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
GO_ROOT = pathlib.Path("/Users/pr/Documents/java&golang/Sea-RideTheWind_golang")

SERVICE = ROOT / "service"
INVENTORY = pathlib.Path(__file__).resolve().parent / "outbound-stub-inventory.md"

# ── RAG 模块 → Go yaml 目录（显式映射 · 逐条可复算）──
#   key = RAG 模块目录（相对 service/）· value = Go yaml 目录（相对 Go 仓）
GO_YAML_MAP = {
    "article/article-api": "service/article/api/etc",
    "article/article-rpc": "service/article/rpc/etc",
    "comment/comment-api": "service/comment/api/etc",
    "comment/comment-rpc": "service/comment/rpc/etc",
    "favorite/favorite-api": "service/favorite/api/etc",
    "favorite/favorite-rpc": "service/favorite/rpc/etc",
    "follow/follow-api": "service/follow/api/etc",
    "follow/follow-rpc": "service/follow/rpc/etc",
    "hot/hot-api": "service/hot/api/etc",
    "hot/hot-rpc": "service/hot/rpc/etc",
    "like/like-api": "service/like/api/etc",
    "like/like-rpc": "service/like/rpc/etc",
    "message/message-api": "service/message/api/etc",
    "message/message-rpc": "service/message/rpc/etc",
    "task/task-api": "service/task/api/etc",
    "task/task-rpc": "service/task/rpc/etc",
    "users/user/user-api": "service/user/user/api/etc",
    "users/user/user-rpc": "service/user/user/rpc/etc",
    "users/admin/admin-api": "service/user/admin/api/etc",
    "users/admin/admin-rpc": "service/user/admin/rpc/etc",
}

GO_DEFAULT_TIMEOUT_MS = 2000
GO_TIMEOUT_ANCHOR = "go-zero@v1.10.0/zrpc/config.go:31"

CAP_YML_BLOCK = "outbound-deadline:"
CAP_YML_KEY = "cap-millis:"

RE_BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
RE_LINE_COMMENT = re.compile(r"//[^\n]*")
RE_STRING = re.compile(r'"(?:\\.|[^"\\])*"')
RE_CHAR = re.compile(r"'(?:\\.|[^'\\])*'")

RE_GRPC_CLIENT = re.compile(r"@GrpcClient\s*\(\s*(?:value\s*=\s*)?\"([^\"]+)\"")
RE_PUBLIC_STUB_METHOD = re.compile(
    r"public\s+(?:static\s+)?(?:final\s+)?[\w.<>,\s\[\]]*?\b\w*Stub\s+(\w+)\s*\(")
RE_PUBLIC_METHOD = re.compile(r"public\s+(?:static\s+)?(?:final\s+)?[\w.<>,\s\[\]]*?\b(\w+)\s*\(")
RE_CLASS = re.compile(r"\b(?:final\s+|abstract\s+)?class\s+(\w+)")

RE_APPLY_TO = re.compile(r"GoStyleRequestDeadline\s*\.\s*applyTo\s*\(")
RE_CARRIER_APPLY = re.compile(r"\bGoStyleGrpcCallDeadline\s*\.\s*apply\s*\(")
RE_REMAINING = re.compile(r"remainingMillis\s*\(|remainingNanos\s*\(|remainingBudgetMs")
RE_WITH_DEADLINE = re.compile(r"withDeadlineAfter\s*\(")
RE_MIN_CAP = re.compile(r"Math\s*\.\s*min\s*\(|min\s*\(\s*\w*[Cc]ap")
RE_MAX_ONLY = re.compile(r"Math\s*\.\s*max\s*\(")
RE_CLIENT_FIELD = re.compile(r"@GrpcClient\s*\(\s*(?:value\s*=\s*)?\"([^\"]+)\"")
RE_YAML_SECTION = re.compile(r"^([A-Za-z][A-Za-z0-9]*Conf):\s*$")
RE_YAML_TIMEOUT = re.compile(r"^\s*Timeout\s*:\s*(\d+)\s*$")


def strip_comments(text: str) -> str:
    text = RE_BLOCK_COMMENT.sub(" ", text)
    return RE_LINE_COMMENT.sub(" ", text)


def code_only(text: str) -> str:
    text = strip_comments(text)
    text = RE_STRING.sub('""', text)
    return RE_CHAR.sub("''", text)


def module_of(path: pathlib.Path) -> str:
    """模块名 = `service/` 与 `src/` 之间的那段路径（如 `article/article-api` · `users/user/user-api`）。

    ⚠️ 踩过的坑：早先按「service/ 后固定两层」取 ⇒ `service/users/user/user-api`
       被截成 `users/user` ⇒ 拼出的 yml 路径不存在 ⇒ **该模块的 `cap-millis` 探测全部落空**
       ⇒ ⇒ 一度把两个已配 cap 的 api 模块误判成「零上界」⇒ ⇒ **判据的输入路径错了，
       输出就会自信地错**。
    """
    parts = path.parts
    i = parts.index("service")
    rest = parts[i + 1:]
    if "src" in rest:
        rest = rest[: rest.index("src")]
    return "/".join(rest)


def module_yml(module: str) -> pathlib.Path | None:
    p = SERVICE / module / "src/main/resources/application.yml"
    return p if p.exists() else None


def yml_cap_millis(module: str):
    """返回 (cap_millis 原始文本, 行号)；无 ⇒ (None, None)。"""
    p = module_yml(module)
    if p is None:
        return None, None
    lines = p.read_text(encoding="utf-8", errors="replace").split("\n")
    in_block = False
    block_indent = 0
    for i, ln in enumerate(lines, 1):
        stripped = ln.strip()
        indent = len(ln) - len(ln.lstrip())
        if stripped == CAP_YML_BLOCK:
            in_block = True
            block_indent = indent
            continue
        if in_block:
            if stripped and indent <= block_indent:
                break                       # 出了该块
            if stripped.startswith(CAP_YML_KEY):
                return stripped[len(CAP_YML_KEY):].strip(), i
    return None, None


def go_client_timeout(module: str, client_name: str):
    """Go 侧该客户端的 Timeout（毫秒）+ 出处。返回 (value, anchor_text)。"""
    rel_dir = GO_YAML_MAP.get(module)
    if not rel_dir:
        return None, "（RAG 模块未登记 Go yaml 映射 ⇒ 未核对）"
    d = GO_ROOT / rel_dir
    if not d.is_dir():
        return None, f"（Go 目录 {rel_dir} 不存在 ⇒ 未核对）"
    section = "".join(part.capitalize() for part in client_name.split("-")) + "Conf"
    # ⚠️ 踩过的坑：曾拼成 `ArticleRpc` + `RpcConf` = `ArticleRpcRpcConf` ⇒ 永远找不到段
    #   ⇒ **静默回落到默认 2000ms** ⇒ 把 Go 侧显式的 70000 读成了 2000 ⇒ 方向反了。
    #   ⇒ 现在段名 = 客户端名首字母大写 + `Conf`（`article-rpc` → `ArticleRpcConf`）。
    for yml in sorted(d.glob("*.yaml")):
        lines = yml.read_text(encoding="utf-8", errors="replace").split("\n")
        cur = None
        for i, ln in enumerate(lines, 1):
            m = RE_YAML_SECTION.match(ln)
            if m:
                cur = (m.group(1), i)
                continue
            if cur and cur[0] == section:
                t = RE_YAML_TIMEOUT.match(ln)
                if t:
                    return int(t.group(1)), f"{yml.relative_to(GO_ROOT)}:{i}"
    return GO_DEFAULT_TIMEOUT_MS, f"{GO_TIMEOUT_ANCHOR}（Go yaml 无 `{section}` 段 ⇒ 走默认）"


class Provider:
    pass


def scan() -> list[Provider]:
    files = sorted(SERVICE.rglob("src/main/java/**/*.java"))
    # 模块内 class 名 → 文件（用于解析载体类）
    class_index: dict[tuple[str, str], pathlib.Path] = {}
    raw_cache: dict[pathlib.Path, str] = {}
    code_cache: dict[pathlib.Path, str] = {}
    for p in files:
        raw = p.read_text(encoding="utf-8", errors="replace")
        raw_cache[p] = raw
        code_cache[p] = code_only(raw)
        for cm in RE_CLASS.finditer(code_cache[p]):
            class_index[(module_of(p), cm.group(1))] = p

    # 全部文件的代码态（用于数调用点）
    all_code = {p: code_cache[p] for p in files}

    out: list[Provider] = []
    for p in files:
        raw = raw_cache[p]
        code = code_cache[p]
        mod = module_of(p)
        if mod == "common":
            continue
        clients = RE_GRPC_CLIENT.findall(strip_comments(raw))
        stub_methods = RE_PUBLIC_STUB_METHOD.findall(code)
        if not clients and not stub_methods:
            continue

        pr = Provider()
        pr.module = mod
        pr.path = p
        pr.rel = str(p.relative_to(ROOT))
        pr.clients = clients
        pr.code = code
        pr.raw = raw
        pr.line = 1
        for i, ln in enumerate(raw.split("\n"), 1):
            if RE_GRPC_CLIENT.search(ln):
                pr.line = i
                break

        # ── 形态轴（三态 · 互斥穷尽）──
        carrier_name = None
        m_carrier = RE_CARRIER_APPLY.search(code)
        if m_carrier:
            carrier_name = "GoStyleGrpcCallDeadline"
        uses_apply_to = bool(RE_APPLY_TO.search(code))
        uses_manual = bool(RE_REMAINING.search(code))
        has_wd = bool(RE_WITH_DEADLINE.search(code))
        if uses_apply_to or carrier_name:
            pr.form = "① 走统一载体"
        elif uses_manual and has_wd:
            pr.form = "② 手搓 remaining"
        else:
            pr.form = "③ 裸 stub"

        # ── 上界轴 ──
        # (a) 本地 cap：withDeadlineAfter 的实参链里出现 min(...)
        pr.local_cap = bool(has_wd and RE_MIN_CAP.search(code))
        # (b) 载体自带 cap
        pr.carrier_cap = False
        pr.carrier = carrier_name
        if carrier_name:
            cf = class_index.get((mod, carrier_name))
            if cf is not None:
                pr.carrier_cap = bool(RE_WITH_DEADLINE.search(code_cache[cf])
                                     and RE_MIN_CAP.search(code_cache[cf]))
                pr.carrier_rel = str(cf.relative_to(ROOT))
        # (c) 共享层 cap
        cap_txt, cap_line = yml_cap_millis(mod)
        pr.shared_cap = cap_txt is not None
        pr.shared_cap_txt = cap_txt
        pr.shared_cap_line = cap_line

        pr.has_cap = pr.local_cap or pr.carrier_cap or pr.shared_cap
        pr.zero = not pr.has_cap

        # ── 出向 API 方法名 + 调用点 ──
        #   口径 = 台账里「N 个出向调用点全部经 `userRpc()`」那个 N
        #   ⇒ 只数**别的文件**里的 `.m(`；provider 自身文件内的调用不算
        #   ⚠️ 两个坑（都会让计数虚高）：
        #     ① **同名方法跨域串味** —— follow-rpc / favorite-rpc 都有 `UserRpcClient.userRpc()`
        #        ⇒ 只按方法名匹配会把两个域的调用点合到一起 ⇒ ⇒ 必须再要求
        #        「该文件里出现了本 provider 的**类名**」。
        #     ② **Object 方法误命中** —— `public boolean equals(Object)` 也是 public 方法
        #        ⇒ `.equals(` 在全仓到处都是 ⇒ ⇒ 显式排除 Object 的方法名。
        OBJECT_METHODS = {"equals", "hashCode", "toString", "clone", "finalize",
                          "getClass", "notify", "notifyAll", "wait"}
        LIFECYCLE_METHODS = {"afterPropertiesSet", "destroy", "init", "close", "start", "stop"}
        cand = (set(stub_methods) | {
            m for m in RE_PUBLIC_METHOD.findall(code)
            if RE_WITH_DEADLINE.search(code) or RE_APPLY_TO.search(code)
            or RE_CARRIER_APPLY.search(code)
        }) - OBJECT_METHODS - LIFECYCLE_METHODS - {"if", "for", "while", "switch",
                                                   "catch", "return", "new"}
        cls_name = RE_CLASS.search(code)
        pr.class_name = cls_name.group(1) if cls_name else p.stem
        cand.discard(pr.class_name)          # 构造器名不是出向 API
        pr.methods = sorted(cand)
        pr.call_sites = []
        for m in pr.methods:
            rx = re.compile(r"\.\s*" + re.escape(m) + r"\s*\(")
            rx_cls = re.compile(r"\b" + re.escape(pr.class_name) + r"\b")
            for q, qc in all_code.items():
                if q == p:
                    continue          # ⚠️ provider 自身文件不算「调用点」
                if module_of(q) != mod:
                    continue          # ⚠️ **同模块限定** —— 见下方「同名类跨域串味」
                if not rx_cls.search(qc):
                    continue          # ⚠️ 该文件根本没提到本 provider ⇒ 不计
                for i, ln in enumerate(qc.split("\n"), 1):
                    if rx.search(ln):
                        pr.call_sites.append((str(q.relative_to(ROOT)), i, m))
        # ── Go 对照 ──
        pr.go = []
        for c in pr.clients:
            val, anchor = go_client_timeout(mod, c)
            pr.go.append((c, val, anchor))
        out.append(pr)
    return out


def fingerprint(providers) -> str:
    h = hashlib.sha256()
    for pr in sorted(providers, key=lambda x: x.rel):
        h.update(f"{pr.rel}|{pr.form}|{pr.has_cap}|{pr.clients}|{pr.call_sites}\n".encode())
    return h.hexdigest()[:16]


def counts(providers):
    tri = {"①": 0, "②": 0, "③": 0}
    for pr in providers:
        tri[pr.form[0]] += 1
    return tri


def render_inventory(providers, tri, total, zero) -> str:
    L = []
    A = L.append
    A("# 出向 stub 全表（P2-T01 落点表）")
    A("")
    A("> **本表由脚本生成** ⇒ **不要手改** ⇒ 改完请跑")
    A("> `python3 docs/RoundPlan/round-02/count-outbound-stubs.py --write-inventory` 重生成。")
    A("> 生成器与口径见同目录 [`count-outbound-stubs.py`](count-outbound-stubs.py) 的模块 docstring。")
    A("")
    A(f"- **出向 stub 提供者** = **{total} 个类**")
    A(f"- **三态分布** = ① 走统一载体 **{tri['①']}** · ② 手搓 remaining **{tri['②']}** · "
      f"③ 裸 stub **{tri['③']}**（自检：①+②+③ == 总数 ⇒ "
      f"{'✅' if tri['①'] + tri['②'] + tri['③'] == total else '❌'}）")
    A(f"- **零上界类** = **{len(zero)}**"
      + (f"（{' · '.join(z['rel'] + ':' + str(z['line']) for z in zero)}）" if zero else ""))
    A("- ⚠️ **形态轴 ≠ 上界轴**：形态①（走 `applyTo`）**只注入入向预算**，"
      "**不含** Go 的 2s 客户端上界 ⇒ ⇒ 一个类可以「有载体」却**零上界**。")
    A("")
    A("## 一、全表")
    A("")
    A("| # | 模块 | 文件:行号 | `@GrpcClient` 目标 | 形态 | 硬上界来源 | 硬上界? | 调用点数 | Go 对照（Timeout） |")
    A("|---|---|---|---|---|---|---|---|---|")
    for i, pr in enumerate(sorted(providers, key=lambda x: x.rel), 1):
        go = " · ".join(f"`{c}` = **{v}ms**（{a}）" for c, v, a in pr.go) or "—"
        srcs = []
        if pr.local_cap:
            srcs.append("本地 min cap")
        if pr.carrier_cap:
            srcs.append(f"载体 `{pr.carrier}` 自带 min cap")
        if pr.shared_cap:
            srcs.append(f"共享层 cap-millis={pr.shared_cap_txt}")
        A(f"| {i} | `{pr.module}` | `{pr.rel}:{pr.line}` | "
          f"{' · '.join('`' + c + '`' for c in pr.clients) or '—'} | {pr.form} | "
          f"{' · '.join(srcs) or '**无**'} | {'✅' if pr.has_cap else '🔴 否'} | "
          f"{len(pr.call_sites)} | {go} |")
    A("")
    A("## 二、零上界类清单（硬上界三个来源全无）")
    A("")
    if not zero:
        A("**（无）** ⇒ 全仓出向 stub 提供者均有硬上界。")
    else:
        A("| 文件:行号 | 模块 | 形态 | Go 侧真值 | 差 |")
        A("|---|---|---|---|---|")
        for z in zero:
            A(f"| `{z['rel']}:{z['line']}` | `{z['module']}` | {z['form']} | "
              f"{z['go']} | {z['gap']} |")
    A("")
    A("## 三、Go 对照口径")
    A("")
    A(f"- Go 侧客户端硬上界的**默认出处** = `{GO_TIMEOUT_ANCHOR}`"
      f"（`json:\",default={GO_DEFAULT_TIMEOUT_MS}\"`）")
    A("- Go yaml 里**显式**写了 `Timeout:` 的，按显式值算"
      "（实测只有 `article-api.yaml` 的 `ArticleRpcConf: Timeout: 70000` 是显式值）")
    A("- RAG 模块 → Go yaml 目录的映射是脚本里的**显式表**（`GO_YAML_MAP`）⇒ 可逐条复算")
    A("")
    return "\n".join(L)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--assert-no-zero-deadline", action="store_true")
    ap.add_argument("--write-inventory", action="store_true")
    ap.add_argument("--check-inventory", action="store_true")
    args = ap.parse_args()

    providers = scan()
    start_n = len(providers)
    start_fp = fingerprint(providers)

    providers2 = scan()
    end_n = len(providers2)
    end_fp = fingerprint(providers2)

    tri = counts(providers)
    total = len(providers)
    zero = []
    for pr in providers:
        if pr.zero:
            go_txt = " · ".join(f"`{c}` = {v}ms（{a}）" for c, v, a in pr.go) or "—"
            gap = ("Go 侧有客户端硬上界（显式或默认 2000ms）⇒ RAG 侧无 ⇒ **少了一次 min**"
                   if pr.go else "—")
            zero.append({"rel": pr.rel, "line": pr.line, "module": pr.module,
                         "form": pr.form, "go": go_txt, "gap": gap})

    print("── 出向 stub 全表计数（P2-T01）────────────────────────────")
    print(f"Δ_start = {start_n}")
    print(f"Δ_end   = {end_n}")
    print(f"扫描指纹 start = {start_fp} / end = {end_fp} ⇒ "
          f"{'✅ 两次扫描一致（扫描面稳定）' if start_fp == end_fp and start_n == end_n else '❌ 两次扫描不一致'}")
    print()
    print(f"出向 stub 提供者总数 = {total}")
    print(f"三态分布：① 走统一载体 = {tri['①']} · ② 手搓 remaining = {tri['②']} · "
          f"③ 裸 stub = {tri['③']}")
    sum3 = tri["①"] + tri["②"] + tri["③"]
    print(f"自检：三态之和 {sum3} == 总数 {total} ⇒ "
          f"{'✅' if sum3 == total else '❌（分类不完备 ⇒ 不可信）'}")
    print()
    print("逐类明细：")
    for pr in sorted(providers, key=lambda x: x.rel):
        srcs = []
        if pr.local_cap:
            srcs.append("本地min")
        if pr.carrier_cap:
            srcs.append(f"载体{pr.carrier}")
        if pr.shared_cap:
            srcs.append(f"共享{pr.shared_cap_txt}")
        go = " · ".join(f"{c}={v}ms" for c, v, a in pr.go)
        print(f"  {'🔴' if pr.zero else '✅'} {pr.form[0]} {pr.rel}:{pr.line} "
              f"| 上界来源={','.join(srcs) or '无'} | 调用点={len(pr.call_sites)} | Go {go}")
    print()
    print(f"零上界类数 = {len(zero)}")
    for z in zero:
        print(f"  🔴 {z['rel']}:{z['line']}  ({z['module']} · {z['form']})")
    print()

    rc = 0
    if args.write_inventory:
        content = render_inventory(providers, tri, total, zero)
        INVENTORY.write_text(content, encoding="utf-8")
        print(f"✅ 已写 {INVENTORY.relative_to(ROOT)}（{len(content)} 字节）")
    if args.check_inventory:
        if not INVENTORY.exists():
            print(f"❌ 落点表不存在：{INVENTORY}")
            rc = 1
        else:
            content = render_inventory(providers, tri, total, zero)
            cur = INVENTORY.read_text(encoding="utf-8")
            if cur == content:
                print(f"✅ 落点表与本次扫描逐字节一致：{INVENTORY.relative_to(ROOT)}")
            else:
                print(f"❌ 落点表与本次扫描**不一致** ⇒ 跑 --write-inventory 重生成"
                      f"（{INVENTORY.relative_to(ROOT)}）")
                rc = 1
    if args.assert_no_zero_deadline and zero:
        print(f"❌ --assert-no-zero-deadline：零上界类数 = {len(zero)}（须为 0）⇒ exit 1")
        rc = 1
    elif args.assert_no_zero_deadline:
        print("✅ --assert-no-zero-deadline：零上界类数 = 0")
    if sum3 != total:
        rc = 1
    return rc


if __name__ == "__main__":
    sys.exit(main())
