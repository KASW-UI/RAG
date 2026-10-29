#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""**user.register 落库对照** —— Go 真源 ↔ RAG 的「HTTP 响应」与「库里的行」**两侧同时**核对。

═══════════════════════════════════════════════════════════════════════════════
【为什么需要这个脚本（它回答的是哪一题）】
═══════════════════════════════════════════════════════════════════════════════

上一轮（2026-10-29 之前）留下的一个**未归因**疑点：

  「RAG 的 `user.register` 实际没落库 —— 该 step 第二次『重复注册』才成功
    ⇒ 说明第一次注册真的失败」

**本脚本把这个问题变成可复算的判据**：
  1. 两侧各注册一个**专用**用户名（`dt_probe_landed`）⇒ **不碰**矩阵用的 `dt_user1`；
  2. 直接查**两侧各自的库**：
       Go  → go_db.users（RAG 侧是 rag_db.users_user，表名差异是已签字偏离 A-03）
       RAG → rag_db.users_user
  3. 断言：**两侧都恰好 1 行**，且 **HTTP 响应说成功 ⇒ 库里就有行**。

⇒ 三种结论都能被本脚本**区分开**：
  * 两侧都落库 ⇒ **不是缺陷**（A/B 都不成立）；
  * 只有一侧落库 ⇒ **真缺陷**（A）；
  * 两侧都没落库 ⇒ 环境/编排问题（B）。

⚠️ **表名差异不是缺陷**：`tools/difftest/initdb.sql` 的注释 + AGENTS.md §3.4 A-03
   —— Go 用 GORM AutoMigrate 建 `users`，RAG 建 `users_user` ⇒ **物理上无法共用 schema**
   ⇒ 本脚本查的是**两张语义相同的表**，比较的是**行内容**。
"""

import argparse
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import http1xx                                                    # noqa: E402

# 两侧的注册端点（路径差异 = 已签字偏离 A-03）
GO_REGISTER_PATH = "/usercenter/v1/user/register"
RAG_REGISTER_PATH = "/user/v1/register"

# 表名差异 = 已签字偏离 A-03（Go: GORM AutoMigrate → users；RAG: 自己的 DDL → users_user）
TABLES = {
    "go":  ("go_db", "users"),
    "rag": ("rag_db", "users_user"),
}


def register(base_url, path, username, email, password="pw123456", extra_info=None):
    body = {"username": username, "password": password, "email": email}
    if extra_info is not None:
        body["extra_info"] = extra_info
    res = http1xx.request("POST", base_url.rstrip("/") + path, body=body, timeout=30.0)
    parsed = None
    try:
        parsed = json.loads(res.text) if res.text.strip() else None
    except Exception:
        parsed = None
    return res, parsed


def psql(pg_container, db, sql):
    """在 PG 容器里跑一条查询，返回行列表（list[dict]）。"""
    out = subprocess.run(
        ["docker", "exec", "-i", pg_container, "psql", "-U", "admin", "-d", db,
         "-tAF", "\t", "-c", sql],
        capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("psql 失败（db=%s）：%s" % (db, out.stderr.strip()))
    rows = []
    for line in out.stdout.splitlines():
        if line.strip() == "":
            continue
        rows.append(line.split("\t"))
    return rows


def warmup(base_url, group="user", rounds=3):
    """预热：把「冷启动首请求」消化掉（**只读**路径：登录一个不存在的用户）。

    ⚠️ **为什么必须有**（2026-10-29 实测）：
      本脚本的注册是**打到 RAG 侧的第一个业务请求** ⇒ 冷启动（JIT + 首个 gRPC
      channel + 首个 DB 连接池 + mapper 初始化）⇒ 实测**耗时 ≥ 3s** ⇒ 撞上共享层
      `HttpMiddleware` 的 **HTTP 超时分支** ⇒ 客户端收到 **503 "Request Timeout"**
      （Go 侧的 `httpTimeoutBody = "Request Timeout"`）⇒
      **而此时那一行其实已经写进库了** ⇒ ⇒ 看起来像「注册失败」，实际是「超时但已落库」。
      ⇒ ⇒ **必须先预热**，否则本脚本测的是「冷启动延迟」而不是「注册是否落库」。
    ⚠️ **必须用「不污染矩阵」的路径**：登录不存在的用户 ⇒ **不写库**。
    """
    path = "/user/v1/login" if group == "user" else "/admin/v1/login"
    hits = []
    for i in range(max(1, rounds)):
        res = http1xx.request("POST", base_url.rstrip("/") + path,
                               body={"username": "__probe_warmup_no_such_user__",
                                     "password": "__probe_warmup__"},
                               timeout=30.0)
        if res.interim:
            hits.append((i, list(res.interim)))
            continue
        break
    return hits


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--go-url", default="http://127.0.0.1:18215")
    ap.add_argument("--rag-url", default="http://127.0.0.1:18219")
    ap.add_argument("--pg-container", default="r4upprobe-postgres")
    ap.add_argument("--username", default="probe_landed_user")
    ap.add_argument("--email", default="landed@example.com")
    ap.add_argument("--extra-info", default='{"city":"上海"}')
    ap.add_argument("--no-extra-info", action="store_true",
                    help="注册**不带** extra_info（用来区分「extra_info 是不是元凶」）")
    ap.add_argument("--no-warmup", action="store_true")
    args = ap.parse_args()

    extra = None if args.no_extra_info else json.loads(args.extra_info)

    print("=" * 78)
    print("user.register 落库对照 —— HTTP 响应 vs 库里的行")
    print("=" * 78)
    print("用户：%s / 邮箱：%s / extra_info=%s"
          % (args.username, args.email, "不带" if extra is None else extra))
    # ⚠️ **用户名绝不能以 `dt_` 开头**：矩阵里的 `admin.getuserlist.keyword`
    #    用 keyword=`dt_`（LIKE '%dt_%'）⇒ 本脚本造的账号会被那个查询捞进去
    #    ⇒ 多出来的行 ⇒ uid 没被归一化 ⇒ **矩阵假红**（2026-10-29 实测踩过）。
    if args.username.startswith("dt_"):
        print("!! 用户名不能以 'dt_' 开头（会污染矩阵的 keyword='dt_' 查询）")
        return 2

    if not args.no_warmup:
        print("\n-- 预热（只读路径；命中 1xx 的轮次照实计数）--")
        for side, base in (("go", args.go_url), ("rag", args.rag_url)):
            hits = warmup(base)
            print("   %-4s 命中1xx=%s" % (side, hits if hits else "无"))


    results = {}
    for side, base, path in (("go", args.go_url, GO_REGISTER_PATH),
                             ("rag", args.rag_url, RAG_REGISTER_PATH)):
        res, parsed = register(base, path, args.username, args.email, extra_info=extra)
        results[side] = (res, parsed)
        code = (parsed or {}).get("code") if isinstance(parsed, dict) else None
        print("\n-- %s 侧 --" % side)
        print("   HTTP %s  interim1xx=%s" % (res.status, res.interim or "无"))
        print("   body  : %s" % (res.text[:300] if res.text else "(空)"))
        print("   业务码: %r" % (code,))

    print("\n-- 库里的行 --")
    row_counts = {}
    for side in ("go", "rag"):
        db, table = TABLES[side]
        rows = psql(args.pg_container, db,
                    "select uid, username, email, coalesce(extra_info,'<NULL>'), "
                    "left(password, 7) from %s where username = '%s';" % (table, args.username))
        row_counts[side] = len(rows)
        print("   %-4s %s.%s → %d 行" % (side, db, table, len(rows)))
        for r in rows:
            print("        uid=%s username=%s email=%s extra_info=%s password=%s…" % tuple(r))

    # ── 判定 ──────────────────────────────────────────────────────────────
    problems = []
    for side in ("go", "rag"):
        res, parsed = results[side]
        http_ok = (res.status == 200 and isinstance(parsed, dict) and parsed.get("code") == 200)
        landed = row_counts[side] == 1
        print("\n   %-4s：HTTP 说成功=%s / 落库=%s（%d 行）"
              % (side, http_ok, landed, row_counts[side]))
        if http_ok and not landed:
            problems.append("%s 侧：HTTP 返回 200 但库里没有行 ⇒ **注册没落库**" % side)
        if landed and not http_ok:
            problems.append("%s 侧：库里有行但 HTTP 没返回成功（status=%s body=%s）"
                            % (side, res.status, (res.text or "")[:120]))
        if row_counts[side] > 1:
            problems.append("%s 侧：库里出现 %d 行（同用户名重复）" % (side, row_counts[side]))

    print("\n" + "-" * 78)
    if problems:
        print("❌ 落库对照未通过：")
        for p in problems:
            print("   - %s" % p)
        return 1
    print("✅ 落库对照通过：两侧 HTTP 成功且各恰好 1 行 ⇒ **register 落库正常，不是缺陷**")
    return 0


if __name__ == "__main__":
    sys.exit(main())
