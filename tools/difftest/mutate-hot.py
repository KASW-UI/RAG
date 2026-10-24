#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 mutate-hot.py —— U9 / D2：hot-api 的**系统级变异**（证明 84 条测试不是假绿）
════════════════════════════════════════════════════════════════════════════

【为什么需要它】
  本仓 §3.3 有一条铁律：**「跑绿」不是「对」**。
  定向变异只证明「**我刚修的那几处**有守门」；它**不能**证明
  「这个组件的 84 条测试整体是有效的」。
  ⇒ 本脚本对 hot-api 的**主干实现**逐点注入语义错误，
    每次都要求**至少有一条测试变红**，并记下**是哪条测试抓到的**。
  ⇒ 「抓到了」= 该测试真的在守这条语义；「没抓到」= 守门有缺口。

【纪律（与 P15 / P19 同源，踩过的坑不再踩）】
  1. **必须带 `-am`**：`mvn -pl service/hot/hot-api test`（不带 -am）会解析到
     **~/.m2 里已安装的旧 jar** ⇒ 变异根本没进运行时 ⇒ **假绿**（P19）。
  2. **必须带 `-Dsurefire.failIfNoSpecifiedTests=false`**：
     `-DfailIfNoSpecifiedTests=false` **不生效**（P19），上游模块会因
     「没有匹配的测试」直接 BUILD FAILURE，看起来像「测试没跑」。
  3. **每个变异必须能在 finally 里还原**：还原用 `git checkout -- <file>`
     （只碰工作树里本域的文件，不碰冻结的 Go 仓库）。
  4. **变异点必须唯一匹配**：old 片段在文件里出现 **≠ 1 次** ⇒ 直接判为
     「变异脚本失效」并跳过，**不硬改**（P15：变异没红的第一反应是「脚本错了」）。

【判据】
  · 变异后 **至少 1 个测试 failure/error** ⇒ 「守门有效 ✅」
  · 变异后 **全部通过** ⇒ 「守门缺口 🔴」（这是本脚本要找的东西）
  · 变异后 **编译失败** ⇒ 「该变异无效（改法破坏了编译）」⇒ 记为 SKIP，不算缺口

【用法】
  python3 tools/difftest/mutate-hot.py                 # 跑全部变异
  python3 tools/difftest/mutate-hot.py --only M07      # 只跑一个
  python3 tools/difftest/mutate-hot.py --list          # 只列变异清单
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MODULE = "service/hot/hot-api"
REPORTS = os.path.join(ROOT, MODULE, "target", "surefire-reports")

# ── 变异清单 ────────────────────────────────────────────────────────────────
# (id, 文件相对路径, old, new, 说明, Go 真源出处)
MUTATIONS = [
    ("M01", "service/HotRankPaginator.java",
     "if (pageSize > MAX_PAGE_SIZE) {",
     "if (pageSize > MAX_PAGE_SIZE + 1) {",
     "分页上限：把阈值放宽 1 ⇒ pageSize=51 时**不再**被截断到 50",
     "getHotArticlesLogic.go:185-187（pageSize > maxPageSize ⇒ 50）"),

    ("M02", "service/HotRankPaginator.java",
     "long total = ranked.size();",
     "long total = ranked.size() + 1;",
     "总数：响应里的 total 多算 1 ⇒ 响应体的 total 字段错位",
     "getHotArticlesLogic.go:78（total = int64(len(items))）"),

    ("M03", "service/HotRankPaginator.java",
     "int start = (page - 1) * pageSize;",
     "int start = page * pageSize;",
     "分页起点：少减一个 pageSize ⇒ 第 2 页起全部错位",
     "getHotArticlesLogic.go:197（start = (page-1)*pageSize）"),

    ("M04", "service/HotRankPaginator.java",
     "item.setRank(result.size() + 1);",
     "item.setRank(result.size());",
     "rank 稠密性：改成从 0 开始 ⇒ rank 序列整体错位",
     "getHotArticlesLogic.go:120（item.Rank = int32(len(result)+1)）"),

    ("M05", "service/HotRankPaginator.java",
     "            if (item == null) {\n                continue;\n            }\n",
     "",
     "补全失败的空位不再跳过 ⇒ nil 元素进了结果列表",
     "getHotArticlesLogic.go:116-118（if item == nil { continue }）"),

    ("M06", "service/HotRankPaginator.java",
     "if (range[0] == range[1]) {",
     "if (range[0] <= range[1]) {",
     "空页判定：range[0] < range[1] 时被误判成空页 ⇒ 非空页返回空列表",
     "getHotArticlesLogic.go:80（items[start:end]）"),

    ("M07", "form/GoFormValues.java",
     "return Integer.parseInt(v, 10);",
     "return Integer.parseInt(v, 8);",
     "十进制解析：radix 改成 8 ⇒ \"010\" 变成 8 而不是 10",
     "getHotArticlesLogic.go:172-176（strconv.ParseInt(str, 10, 32)）"),

    ("M08", "client/GoStyleGrpcCallDeadline.java",
     ": Math.min(DEFAULT_CALL_TIMEOUT_MILLIS, remainingBudgetMs);",
     ": DEFAULT_CALL_TIMEOUT_MILLIS;",
     "gRPC deadline：去掉「请求剩余预算」约束 ⇒ 单次调用可能超出 HTTP 总预算",
     "zrpc/config.go:31（Timeout default=2000）+ ctx 剩余预算取 min"),

    ("M09", "advice/GlobalResponseAdvice.java",
     "if (body == null || body instanceof Response) {",
     "if (body == null) {",
     "响应包装：不再识别「已成型的信封」⇒ 错误响应被套两层信封",
     "hotcenter.go:45-51（httpx.SetOkHandler 只包一层）"),

    ("M10", "service/HotArticleHydrator.java",
     "articleResp = articleRpcClient.getPublicArticle(hotItem.getArticleId(), remainingBudgetMs);",
     "articleResp = articleRpcClient.getPublicArticle(hotItem.getArticleId(), remainingBudgetMs + 1000);",
     "补全预算：给下游多要 1s ⇒ 单条补全可能突破 HTTP 总预算",
     "getHotArticlesLogic.go:132（用 ctx 的 deadline，不额外加预算）"),
]


def sh(cmd, cwd=ROOT):
    return subprocess.run(cmd, shell=True, cwd=cwd, capture_output=True, text=True)


def run_tests():
    """跑 hot-api 全量测试（带 -am！）。返回 (exit_code, stdout_tail)。"""
    cmd = ("mvn -o -pl service/hot/hot-api -am test "
           "-Dsurefire.failIfNoSpecifiedTests=false")
    r = sh(cmd)
    return r.returncode, (r.stdout + r.stderr)


def collect_failures():
    """从 surefire 报告里收集失败/错误的测试类。"""
    failed = []
    total = 0
    if not os.path.isdir(REPORTS):
        return failed, total
    for name in sorted(os.listdir(REPORTS)):
        if not name.endswith(".xml"):
            continue
        try:
            root = ET.parse(os.path.join(REPORTS, name)).getroot()
        except ET.ParseError:
            continue
        total += int(root.get("tests", 0))
        for tc in root.findall("testcase"):
            bad = tc.find("failure") is not None or tc.find("error") is not None
            if bad:
                failed.append(f"{root.get('name')}#{tc.get('name')}")
    return failed, total


def mutate(mid, rel, old, new):
    """返回 (是否成功注入, 说明)。old 必须唯一匹配。"""
    path = os.path.join(ROOT, MODULE, "src", "main", "java", "com", "rag", "hot", "hot", "api", rel)
    if not os.path.isfile(path):
        return False, f"文件不存在：{rel}"
    src = open(path, encoding="utf-8").read()
    n = src.count(old)
    if n != 1:
        return False, f"old 片段出现 {n} 次（要求恰好 1 次）⇒ 变异脚本失效"
    open(path, "w", encoding="utf-8").write(src.replace(old, new, 1))
    return True, "ok"


def restore(rel):
    path = os.path.join(ROOT, MODULE, "src", "main", "java", "com", "rag", "hot", "hot", "api", rel)
    sh(f'git checkout -- "{path}"')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None, help="只跑某个变异 id")
    ap.add_argument("--list", action="store_true", help="只列清单")
    args = ap.parse_args()

    if args.list:
        for m in MUTATIONS:
            print(f"{m[0]}  {m[1]:<45} {m[4]}")
        return 0

    # 基线：不带变异必须全绿（否则「变红」没有意义）
    print("==> [基线] 不带变异跑一次，确认全绿")
    rc, out = run_tests()
    base_failed, base_total = collect_failures()
    print(f"    exit={rc} tests={base_total} failures={len(base_failed)}")
    if base_failed:
        print("    ❌ 基线就不绿，后续判定无效：")
        for f in base_failed[:10]:
            print(f"       {f}")
        return 2

    results = []
    only = set(args.only.split(",")) if args.only else None
    for mid, rel, old, new, desc, src in MUTATIONS:
        if only and mid not in only:
            continue
        print(f"\n==> {mid}  {desc}")
        print(f"    出处：{src}")
        ok, why = mutate(mid, rel, old, new)
        if not ok:
            print(f"    ⚠️  跳过（{why}）")
            results.append({"id": mid, "verdict": "SKIP", "why": why, "desc": desc})
            continue
        try:
            t0 = time.time()
            rc, out = run_tests()
            failed, total = collect_failures()
            dt = time.time() - t0
            if "COMPILATION ERROR" in out or "BUILD FAILURE" in out and not failed:
                # 编译失败 = 变异改法破坏了编译 ⇒ 不算守门缺口
                verdict = "SKIP"
                print(f"    ⏭️  编译失败（变异改法无效）{dt:.0f}s")
            elif failed:
                verdict = "CAUGHT"
                print(f"    ✅ 守门有效：{len(failed)} 条测试变红（{dt:.0f}s）")
                for f in failed[:6]:
                    print(f"       · {f}")
            else:
                verdict = "GAP"
                print(f"    🔴 守门缺口：变异后**全部通过**（{dt:.0f}s，tests={total}）")
            results.append({"id": mid, "verdict": verdict, "desc": desc,
                            "source": src, "failed": failed, "tests": total,
                            "seconds": round(dt, 1)})
        finally:
            restore(rel)

    # ── 汇总 ────────────────────────────────────────────────────────────────
    print("\n══════════ 汇总 ══════════")
    caught = [r for r in results if r["verdict"] == "CAUGHT"]
    gaps = [r for r in results if r["verdict"] == "GAP"]
    skipped = [r for r in results if r["verdict"] == "SKIP"]
    for r in results:
        mark = {"CAUGHT": "✅", "GAP": "🔴", "SKIP": "⏭️"}[r["verdict"]]
        print(f"  {mark} {r['id']}  {r['desc']}")
        if r.get("failed"):
            print(f"        抓到：{', '.join(r['failed'][:4])}"
                  + (" …" if len(r['failed']) > 4 else ""))
    print(f"\n  守门有效 {len(caught)} / 守门缺口 {len(gaps)} / 跳过 {len(skipped)}")
    out_path = os.path.join(os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest"),
                            "u9-mutation.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({"baseline_tests": base_total, "results": results}, f,
                  ensure_ascii=False, indent=2)
    print(f"  明细：{out_path}")
    return 1 if gaps else 0


if __name__ == "__main__":
    sys.exit(main())
