#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-surface-parity.py —— **对外契约面**的 Go ↔ RAG 双侧对照（静态、只读、无构建）。

═══════════════════════════════════════════════════════════════════════
【这个脚本补的是什么缺口】
═══════════════════════════════════════════════════════════════════════
`docs/PhaseGoal-judge.md` 的矩阵 A 要回答两个问题：

  ① **HTTP 契约面**：Go `routes.go` 声明的 (method, path) 集合，
     与 RAG Controller 的方法级映射集合，**是否逐条相等**？
  ② **gRPC 契约面**：Go `proto/<域>.proto` 的 `rpc` 方法集合，
     与 RAG `*ServiceGrpc.*ImplBase` 子类**实际覆写**的方法集合，
     是否逐条相等？

⇒ 这两个结论此前**只存在于一次性脚本里**（`/tmp` 下）⇒ **不可复算**
   （教训见 `docs/Pitfalls/_cross/` 与本文件 §「效力边界」第 3 条）。
⇒ 本脚本把判据**落仓** ⇒ 任何人、任何机器都能重跑。

═══════════════════════════════════════════════════════════════════════
【它检查什么 / 不检查什么】
═══════════════════════════════════════════════════════════════════════
查：
  · ① 的**集合**（方法 + 路径），路径做 `:id` ↔ `{id}` 归一化
  · ② 的**集合**（rpc 方法名，大小写归一）
  · ③ Go 侧 `rest.WithJwt` 分段的**端点条数**（供人工核对 RAG 侧保护面）

**不查**（明确声明，避免把「跑绿」读成「行为等价」）：
  · ❌ 请求体 / 响应体 / 错误码 / 事务 / 并发 —— 那是**语义面**，
        由各域台账 + 双侧活体探针负责，**本脚本一概不碰**。
  · ❌ RAG 侧**哪些路径真的被 JWT 保护** —— RAG 的保护面写在
        `JwtAuthInterceptor` 内部的白/黑名单里，**不是** Spring 的
        `addPathPatterns` ⇒ **本脚本只报 Go 侧的条数，不自动比对 RAG 侧**。
        ⇒ 用它**不能**得出「两侧鉴权面一致」的结论。
  · ❌ 端点的**参数校验 / 业务规则 / 权限** —— 本脚本只看路径与方法。

═══════════════════════════════════════════════════════════════════════
【效力边界（四条，越界即失效）】
═══════════════════════════════════════════════════════════════════════
1. **集合相等 ≠ 行为等价。** 路径与方法对上，只能说明「对外形状」没漂。
2. **Go 侧解析的是 `routes.go` 的字面量。** 若某域把路由拼在运行时
   （本仓 10 域**没有**这种写法），本脚本会漏 ⇒ 结论只对这 10 域成立。
3. **「跑绿」只证明脚本活着。** 若 `routes.go` 格式变了而脚本没跟上，
   解析结果会**变空** ⇒ 脚本必须**对空结果报错**，不能当成「两边都没端点 ⇒ 相等」。
   （本脚本对每个域都断言「解析到的端点数 > 0」。）
4. **已签字偏离不是失败。** `users` / `admin` 两域的路径前缀与 HTTP method
   与 Go 不同 —— 那是**经签字批准**的决策（见
   `docs/Intentional_Deviations/_cross/signed-deviations.md`）⇒ 本脚本
   **单列报告**、**不计入失败**。

═══════════════════════════════════════════════════════════════════════
【用法】
═══════════════════════════════════════════════════════════════════════
  python3 tools/difftest/check-surface-parity.py
  python3 tools/difftest/check-surface-parity.py --domain article     # 只查某域
  GO_REPO=/path/to/Sea-RideTheWind_golang python3 tools/difftest/check-surface-parity.py

退出码：0 = 范围内契约面逐条相等（已签字偏离域单列）；1 = 存在非签字差异或解析异常；
        2 = **用法/环境错误**（未知域名 · 找不到 Go 真源仓 · 一个域都没对照）。

🆕 2026-10-29 三处「静默跳过 = 假绿/假红」的修复：
  ① Go 真源定位：环境变量优先（**校验**）→ 否则**逐级向上搜索**
     （worktree 与主仓都命中同一份真源）→ 都找不到 ⇒ **退出码 2 + 显式报错**。
     旧默认路径在 worktree 里恒指错 ⇒ 「找不到 Go 仓」被当成「Go 侧 0 个端点」。
  ② `--domain` 域名白名单：**未知域名 ⇒ 退出码 2**（旧行为是「一个域都不跑 ⇒ EXIT=0」）。
     ⚠️ users 域的真名是 **`user` / `admin`**（不是 `users`）。
  ③ Go proto 缺失 ⇒ **报红**（旧行为是静默 `continue` ⇒ 该域 gRPC 面**没查**）。
"""

import argparse
import glob
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# ── Go 真源定位 ────────────────────────────────────────────────────────────
# 🔴 2026-10-29 修复（同一根因被 G1 报作 `S1-FOLLOW-008`②、G3 报作 `G4-10`）：
#   原默认值 = `os.path.join(os.path.dirname(REPO), "Sea-RideTheWind_golang")`
#   ⇒ 在 **worktree** 里 `REPO` = `.../RAG.worktrees/<lane>` ⇒ 拼出
#      `.../RAG.worktrees/Sea-RideTheWind_golang` ⇒ **不存在**
#   ⇒ ⇒ 脚本把「找不到 Go 仓」当成「Go 侧 0 个端点」⇒ **hot 域假红**，
#      且 `--domain <错名>` 时**一个域都不跑**却 EXIT=0 ⇒ **假绿**。
# ⇒ ⇒ 现在：环境变量优先（**校验**）→ 否则**逐级向上搜索** → 都找不到 ⇒ **显式报错退出**。
_GO_REPO_DIR_NAME = "Sea-RideTheWind_golang"
# 判据：必须同时含「Go 侧 api 的 routes.go」与「proto 目录」⇒ 才算 Go 真源仓。
_GO_REPO_MARKER = "service/article/api/internal/handler/routes.go"


def _looks_like_go_repo(path):
    return (os.path.isfile(os.path.join(path, _GO_REPO_MARKER))
            and os.path.isdir(os.path.join(path, "proto")))


def _resolve_go_repo():
    """定位 Go 真源仓 ⇒ 返回 (路径, 来源说明)。

    🔴 找不到 ⇒ **抛 FileNotFoundError**（由 main 显式报错退出）。
       **绝不允许**退化成「Go 侧 0 个端点」⇒ 那会把「没查」读成「一致」。
    """
    env = os.environ.get("GO_REPO")
    if env:
        if not _looks_like_go_repo(env):
            raise FileNotFoundError(
                f"GO_REPO 环境变量指向的目录**不是 Go 真源仓**：{env}\n"
                f"  判据：必须同时含 {_GO_REPO_MARKER} 与 proto/ 目录。\n"
                f"  ⇒ 请把它指向 Sea-RideTheWind_golang 的真实路径。")
        return env, "GO_REPO 环境变量"

    # 逐级向上搜索：worktree 与主仓都能命中同一份真源
    cur = os.path.dirname(REPO)
    seen = []
    for _ in range(8):
        candidates = [os.path.join(cur, _GO_REPO_DIR_NAME)]
        try:
            candidates += sorted(
                os.path.join(cur, d) for d in os.listdir(cur)
                if os.path.isdir(os.path.join(cur, d)) and d != _GO_REPO_DIR_NAME)
        except OSError:
            pass
        for cand in candidates:
            if _looks_like_go_repo(cand):
                return cand, f"向上搜索命中（自 {REPO} 起）"
        seen.append(cur)
        parent = os.path.dirname(cur)
        if parent == cur:
            break
        cur = parent
    raise FileNotFoundError(
        "**找不到 Go 真源仓**（Sea-RideTheWind_golang）⇒ 本脚本无法做对照。\n"
        f"  已搜索：{seen}\n"
        "  ⇒ 请显式指定：GO_REPO=/绝对路径/Sea-RideTheWind_golang python3 tools/difftest/"
        "check-surface-parity.py\n"
        "  ⚠️ **本脚本不得在找不到真源时给出绿灯** —— 那会把「没查」读成「一致」。")

# ── 域定义：Go 原件 ↔ RAG 模块 ────────────────────────────────────────────
# go_rpc  : Go proto 文件名（users 域的 user.proto 在 RAG 侧改名 users.proto）
# rag_impl: RAG 侧 `*ImplBase` 子类所在目录（glob）
DOMAINS = [
    # name,     go_routes,                              go_proto,    rag_api,                                  rag_rpc,                                  signed
    ("article",  "service/article/api/internal/handler/routes.go",  "article",  "service/article/article-api",  "service/article/article-rpc",  False),
    ("comment",  "service/comment/api/internal/handler/routes.go",  "comment",  "service/comment/comment-api",  "service/comment/comment-rpc",  False),
    ("favorite", "service/favorite/api/internal/handler/routes.go", "favorite", "service/favorite/favorite-api", "service/favorite/favorite-rpc", False),
    ("follow",   "service/follow/api/internal/handler/routes.go",   "follow",   "service/follow/follow-api",   "service/follow/follow-rpc",   False),
    ("hot",      "service/hot/api/internal/handler/routes.go",      "hot",      "service/hot/hot-api",      "service/hot/hot-rpc",      False),
    ("like",     "service/like/api/internal/handler/routes.go",     "like",     "service/like/like-api",     "service/like/like-rpc",     False),
    ("message",  "service/message/api/internal/handler/routes.go",  "message",  "service/message/message-api",  "service/message/message-rpc",  False),
    ("task",     "service/task/api/internal/handler/routes.go",     "task",     "service/task/task-api",     "service/task/task-rpc",     False),
    ("user",     "service/user/user/api/internal/handler/routes.go", "user",     "service/users/user/user-api", "service/users/user/user-rpc", True),
    ("admin",    "service/user/admin/api/internal/handler/routes.go", "admin",  "service/users/admin/admin-api", "service/users/admin/admin-rpc", True),
]

PATH_RE = re.compile(r":([A-Za-z_][A-Za-z0-9_]*)")


def norm_path(p):
    """`:id` 与 `{id}` 归一；去掉尾部斜杠。"""
    p = PATH_RE.sub(r"{\1}", p.strip())
    if len(p) > 1:
        p = p.rstrip("/")
    return p


# ── Go 侧：解析 routes.go ────────────────────────────────────────────────
def parse_go_routes(path):
    """返回 [(method, full_path, jwt_protected)]。

    结构：`server.AddRoutes([]rest.Route{ {Method: http.MethodX, Path: "..."} },
    rest.WithPrefix("/v1"), rest.WithJwt(...))`
    ⇒ 同一文件里可能有**多段** AddRoutes，**每段各有自己的 prefix / jwt**。
    """
    with open(path, encoding="utf-8") as fh:
        src = fh.read()
    out = []
    idx = 0
    while True:
        i = src.find("server.AddRoutes(", idx)
        if i < 0:
            break
        depth = 0
        j = i + len("server.AddRoutes(") - 1
        while j < len(src):
            if src[j] == "(":
                depth += 1
            elif src[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        block = src[i:j]
        idx = j
        pm = re.search(r'rest\.WithPrefix\("([^"]*)"\)', block)
        prefix = pm.group(1) if pm else ""
        jwt = "rest.WithJwt(" in block
        for m in re.finditer(
                r'Method:\s*http\.Method(\w+),\s*\n\s*Path:\s*"([^"]*)"', block):
            out.append((m.group(1).upper(), prefix + m.group(2), jwt))
    return out


# ── RAG 侧：解析 Controller ───────────────────────────────────────────────
def parse_rag_controllers(moddir):
    """返回 ([(method, full_path)], {controller 前缀集合})。

    ⚠️ **必须剥掉类级 `@RequestMapping`**（它是前缀，不是端点）——
    把它当端点会让每域虚多 1 条。

    ⚠️ **怎么找类级注解**：**取 `class` 声明之前最近的那个 `@RequestMapping`**。
    ❌ 不要用「注解后面 400 字符里有没有 `class `」这种启发式 ——
    类级注解与 `public class X` 之间**本来就有** `class ` 这个词
    ⇒ 该启发式永远判否 ⇒ **前缀被吞成空串** ⇒ 表现为「Go 有 `/v1` 前缀、RAG 没有」
    ⇒ 每一域都误报成「契约面不一致」。
    （这是本脚本 2026-10-28 首次落地时真实踩到的坑。）
    """
    out = []
    prefixes = set()
    for f in sorted(glob.glob(os.path.join(moddir, "src/main/java/**/*Controller.java"),
                              recursive=True)):
        with open(f, encoding="utf-8") as fh:
            src = fh.read()
        cm = re.search(r"\bclass\s+\w+", src)
        prefix = ""
        if cm:
            head = src[:cm.start()]
            ms = list(re.finditer(
                r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"', head))
            if ms:
                prefix = ms[-1].group(1)
        prefixes.add(prefix)
        for m in re.finditer(
                r'@(Get|Post|Put|Delete|Patch)Mapping(\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?', src):
            verb = m.group(1).upper()
            path = m.group(3) or ""
            out.append((verb, prefix + path))
    return out, prefixes


# ── Go 侧：解析 proto 的 rpc 方法 ─────────────────────────────────────────
def parse_go_proto_rpc(path):
    with open(path, encoding="utf-8") as fh:
        src = fh.read()
    svc = re.search(r"service\s+(\w+)\s*\{", src)
    body = src[svc.end():] if svc else src
    return svc.group(1) if svc else "?", [m.group(1) for m in
                                          re.finditer(r"rpc\s+(\w+)\s*\(", body)]


# ── RAG 侧：解析 `*ImplBase` 子类实际覆写的方法 ────────────────────────────
def parse_rag_impl_methods(moddir):
    out = set()
    for f in glob.glob(os.path.join(moddir, "src/main/java/**/*Impl*.java"), recursive=True):
        with open(f, encoding="utf-8") as fh:
            src = fh.read()
        if "ImplBase" not in src:
            continue
        for m in re.finditer(r"public\s+void\s+(\w+)\s*\(", src):
            out.add(m.group(1))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--domain", action="append",
                    help="只查指定域（可重复）")
    args = ap.parse_args()
    want = set(args.domain or [])

    # 🔴 2026-10-29：域名白名单校验 —— 未知名**必须**报错退出。
    #    旧行为：`want` 与任何域名都不匹配 ⇒ 循环体一次都不跑 ⇒ failures 为空
    #    ⇒ EXIT=0 ⇒ **假绿**（G1 报作「--domain users 用了错名，真名是 user/admin」）。
    known = {d[0] for d in DOMAINS}
    unknown = sorted(want - known)
    if unknown:
        print(f"✗ 未知域名：{unknown}", file=sys.stderr)
        print(f"  可用域名：{sorted(known)}", file=sys.stderr)
        print("  ⚠️ **静默跳过 = 假绿** ⇒ 查了 0 个域却 EXIT=0 ⇒ 必须报错退出。",
              file=sys.stderr)
        return 2

    # 🔴 2026-10-29：Go 真源定位 —— 找不到 ⇒ 显式报错退出（不得当成「0 个端点」）
    try:
        go_repo, go_repo_src = _resolve_go_repo()
    except FileNotFoundError as e:
        print(f"✗ {e}", file=sys.stderr)
        return 2

    print("=" * 78)
    print("对外契约面 Go ↔ RAG 对照（静态 · 只读 · 无构建）")
    print(f"  Go 真源: {go_repo}   ← {go_repo_src}")
    print(f"  RAG     : {REPO}")
    print("=" * 78)

    failures = []
    api_tot_go = api_tot_rag = 0
    rpc_tot_go = rpc_tot_rag = 0
    signed_rows = []
    checked_domains = []

    for name, go_routes_rel, go_proto, rag_api, rag_rpc, signed in DOMAINS:
        if want and name not in want:
            continue
        checked_domains.append(name)

        # ── ① HTTP 契约面 ──
        go_rp = os.path.join(go_repo, go_routes_rel)
        if not os.path.isfile(go_rp):
            print(f"\n[{name}] 🔴 Go routes.go 不存在: {go_rp}")
            failures.append(f"{name}: Go routes.go 缺失")
            continue
        go_routes = parse_go_routes(go_rp)
        rag_routes, rag_prefixes = parse_rag_controllers(os.path.join(REPO, rag_api))
        go_set = {(m, norm_path(p)) for m, p, _ in go_routes}
        rag_set = {(m, norm_path(p)) for m, p in rag_routes}
        jwt_n = sum(1 for _, _, j in go_routes if j)
        go_prefixes = {p for _, p, _ in go_routes}
        go_prefix_root = {p.rsplit("/", 2)[0] for p in go_prefixes} if go_prefixes else set()

        # ── 前缀断言（防「前缀被吞成空串」这类解析 bug 自误报）─────────
        # Go 侧各段的前缀 = 路径去掉最后一段后的部分；RAG 侧 = 类级 @RequestMapping
        if not signed and go_prefix_root and rag_prefixes:
            # Go 前缀可能带末段（/comment/v1 ⇒ 段是 comment/v1），取其首段比较
            go_head = sorted({p.split("/")[1] for p in go_prefixes if p.startswith("/")})
            rag_head = sorted({p.split("/")[1] for p in rag_prefixes if p.startswith("/")})
            if go_head and rag_head and go_head != rag_head:
                print(f"\n[{name}] 🔴 解析异常：Go 侧首段 {go_head} vs RAG 侧首段 {rag_head}"
                      f" ⇒ **前缀解析很可能出错了**，先修脚本再谈契约面")
                failures.append(f"{name}: 前缀解析异常（脚本 bug，不是契约差异）")
                continue

        if not go_set or not rag_set:
            # 效力边界第 3 条：解析结果为空必须报错，不能当成「相等」
            print(f"\n[{name}] 🔴 解析结果为空（Go={len(go_set)} / RAG={len(rag_set)}）"
                  f" ⇒ 脚本格式已失效，**不得**读作「两边都没端点 ⇒ 相等」")
            failures.append(f"{name}: 端点解析为空")
            continue

        only_go = sorted(go_set - rag_set)
        only_rag = sorted(rag_set - go_set)
        api_tot_go += len(go_set)
        api_tot_rag += len(rag_set)

        if signed and (only_go or only_rag):
            signed_rows.append((name, len(go_set), len(rag_set), len(only_go), len(only_rag)))
            print(f"\n[{name}] 🟡 已签字偏离域（**不计失败**）")
            print(f"    Go={len(go_set)} 端点 / RAG={len(rag_set)} 端点 / "
                  f"Go 独有={len(only_go)} / RAG 独有={len(only_rag)}")
            print(f"    依据: docs/Intentional_Deviations/_cross/signed-deviations.md")
        elif only_go or only_rag:
            print(f"\n[{name}] 🔴 契约面不一致")
            for m, p in only_go:
                print(f"    仅 Go 有 : {m:6s} {p}")
            for m, p in only_rag:
                print(f"    仅 RAG 有: {m:6s} {p}")
            failures.append(f"{name}: HTTP 契约面差异 {len(only_go)}+{len(only_rag)} 条")
        else:
            print(f"\n[{name}] ✅ HTTP 契约面逐条相等（{len(go_set)} 端点，"
                  f"Go 侧 JWT 保护 {jwt_n} 条）")

        # ── ② gRPC 契约面 ──
        gp = os.path.join(go_repo, "proto", go_proto + ".proto")
        if not os.path.isfile(gp):
            # 🔴 2026-10-29：旧行为是 `continue`（静默跳过 gRPC 面）⇒ 该域的 gRPC 面
            #    **根本没查**，却因为 HTTP 面已红而看不出来 ⇒ 属于「跳过被读成通过」。
            #    现在：**报红**，并明写「不是差异，是没查」。
            print(f"    🔴 Go proto 缺失 ⇒ **gRPC 面没查**（不是「相等」）：{gp}")
            failures.append(f"{name}: Go proto 缺失 ⇒ gRPC 契约面未对照")
            continue
        svc, go_rpc = parse_go_proto_rpc(gp)
        rag_rpc_set = parse_rag_impl_methods(os.path.join(REPO, rag_rpc))
        gn = {x.lower() for x in go_rpc}
        rn = {x.lower() for x in rag_rpc_set}
        rpc_tot_go += len(gn)
        rpc_tot_rag += len(rn)
        miss = sorted(gn - rn)
        extra = sorted(rn - gn)
        if miss or extra:
            print(f"    🔴 gRPC 方法集合不一致（Go {svc}={len(gn)} / RAG={len(rn)}）")
            if miss:
                print(f"       Go 有 RAG 无: {miss}")
            if extra:
                print(f"       RAG 有 Go 无: {extra}")
            failures.append(f"{name}: gRPC 方法集合差异")
        else:
            print(f"    ✅ gRPC 方法集合逐条相等（Go {svc}={len(gn)}）")

    print("\n" + "=" * 78)
    print(f"实际对照的域（{len(checked_domains)} 个）：{checked_domains}")
    print(f"合计：HTTP 端点 Go={api_tot_go} / RAG={api_tot_rag}  ·  "
          f"gRPC 方法 Go={rpc_tot_go} / RAG={rpc_tot_rag}")
    # 🔴 2026-10-29：查了 0 个域 ⇒ **不得**给绿灯（「没查」≠「一致」）
    if not checked_domains:
        print("\n✗ 一个域都没对照 ⇒ **不得**读作「契约面相等」", file=sys.stderr)
        return 2
    if signed_rows:
        print("已签字偏离域（单列，不计失败）：")
        for n, g, r, a, b in signed_rows:
            print(f"  {n}: Go={g} RAG={r}（差异 {a}+{b} 条 ⇒ 已签字）")
    print("=" * 78)

    if failures:
        print(f"\n✗ {len(failures)} 项不一致：")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("\n✓ 范围内对外契约面逐条相等（已签字偏离域单列）")
    print("  ⚠️ 本脚本**只覆盖契约面**（路径 / 方法 / rpc 方法名）。")
    print("     请求体 / 响应体 / 错误码 / 事务 / 并发 ⇒ **不在本脚本范围**，")
    print("     不得用它的绿灯推导「组件行为等价」。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
