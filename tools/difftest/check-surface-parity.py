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
  GO_REPO=/path/to/Sea-RideTheWind_golang python3 tools/difftest/check-surface-parity.py
  python3 tools/difftest/check-surface-parity.py --domain article     # 只查某域

退出码：0 = 范围内契约面逐条相等（已签字偏离域单列）；1 = 存在非签字差异或解析异常。
"""

import argparse
import glob
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GO_REPO = os.environ.get(
    "GO_REPO", os.path.join(os.path.dirname(REPO), "Sea-RideTheWind_golang"))

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

    print("=" * 78)
    print("对外契约面 Go ↔ RAG 对照（静态 · 只读 · 无构建）")
    print(f"  Go 真源: {GO_REPO}")
    print(f"  RAG     : {REPO}")
    print("=" * 78)

    failures = []
    api_tot_go = api_tot_rag = 0
    rpc_tot_go = rpc_tot_rag = 0
    signed_rows = []

    for name, go_routes_rel, go_proto, rag_api, rag_rpc, signed in DOMAINS:
        if want and name not in want:
            continue

        # ── ① HTTP 契约面 ──
        go_rp = os.path.join(GO_REPO, go_routes_rel)
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
        gp = os.path.join(GO_REPO, "proto", go_proto + ".proto")
        if not os.path.isfile(gp):
            print(f"    ⚠️ Go proto 缺失，跳过 gRPC 面: {gp}")
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
    print(f"合计：HTTP 端点 Go={api_tot_go} / RAG={api_tot_rag}  ·  "
          f"gRPC 方法 Go={rpc_tot_go} / RAG={rpc_tot_rag}")
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
