#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 mutate-hot.py —— U9 / D2：hot 域的**系统级变异**（证明测试不是假绿）
════════════════════════════════════════════════════════════════════════════

【为什么需要它】
  本仓 §3.3 有一条铁律：**「跑绿」不是「对」**。
  定向变异只证明「**我刚修的那几处**有守门」；它**不能**证明
  「这个组件的几十条测试整体是有效的」。
  ⇒ 本脚本对 hot 域各组件的**主干实现**逐点注入语义错误，
    每次都要求**至少有一条测试变红**，并记下**是哪条测试抓到的**。
  ⇒ 「抓到了」= 该测试真的在守这条语义；「没抓到」= 守门有缺口。

【覆盖的模块（2026-10-24 第九/十一轮）】
  hot-api    86 条  —— 第九轮做完（10 个变异点，10/10 全被抓住）
  hot-rpc    84 条  —— 第十一轮（本文件新增）
  hot-common 14 条  —— 第十一轮（本文件新增）

【纪律（与 P15 / P19 / P22 同源，踩过的坑不再踩）】
  1. **必须带 `-am`**：`mvn -pl <模块> test`（不带 -am）会解析到
     **~/.m2 里已安装的旧 jar** ⇒ 变异根本没进运行时 ⇒ **假绿**（P19）。
  2. **必须带 `-Dsurefire.failIfNoSpecifiedTests=false`**：
     `-DfailIfNoSpecifiedTests=false` **不生效**（P19），上游模块会因
     「没有匹配的测试」直接 BUILD FAILURE，看起来像「测试没跑」。
  3. **每个变异必须能在 finally 里还原**：用 `git checkout -- <file>`。
  4. **变异点必须唯一匹配**：old 片段在文件里出现 **≠ 1 次** ⇒ 直接判为
     「变异脚本失效」并跳过，**不硬改**（P15/P22）。
  5. ⚠️ **「没变红」先证伪「变异本身有效」**（P22）：本脚本判 GAP 之前，
     必须先想清楚「这段代码在新旧两种写法下**输出可能不同吗**」。
     Java 的 `Integer.parseInt(String)` 本就是十进制 ⇒ 那类改法是 **no-op**。

【用法】
  python3 tools/difftest/mutate-hot.py                     # 跑全部模块
  python3 tools/difftest/mutate-hot.py --module hot-rpc    # 只跑一个模块
  python3 tools/difftest/mutate-hot.py --only R07,HC02     # 只跑指定变异
  python3 tools/difftest/mutate-hot.py --list
"""

import argparse
import json
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

# ── 模块注册表：module_key → (maven -pl 路径, 主代码包根, 测试报告目录) ──────
MODULES = {
    "hot-api": ("service/hot/hot-api", "com/rag/hot/hot/api"),
    "hot-rpc": ("service/hot/hot-rpc", "com/rag/hot/hot/rpc"),
    "hot-common": ("service/hot/hot-common", "com/rag/hot/hot/common"),
}

# ── 变异清单 ────────────────────────────────────────────────────────────────
# (id, module_key, 相对包根的文件路径, old, new, 说明, Go 真源出处)
MUTATIONS = [
    # ─────────────────────────── hot-api（第九轮，10/10 全被抓住）───────────────────────────
    ("M01", "hot-api", "service/HotRankPaginator.java",
     "if (pageSize > MAX_PAGE_SIZE) {",
     "if (pageSize > MAX_PAGE_SIZE + 1) {",
     "分页上限：把阈值放宽 1 ⇒ pageSize=51 时**不再**被截断到 50",
     "getHotArticlesLogic.go:185-187（pageSize > maxPageSize ⇒ 50）"),

    ("M02", "hot-api", "service/HotRankPaginator.java",
     "long total = ranked.size();",
     "long total = ranked.size() + 1;",
     "总数：响应里的 total 多算 1 ⇒ 响应体的 total 字段错位",
     "getHotArticlesLogic.go:78（total = int64(len(items))）"),

    ("M03", "hot-api", "service/HotRankPaginator.java",
     "int start = (page - 1) * pageSize;",
     "int start = page * pageSize;",
     "分页起点：少减一个 pageSize ⇒ 第 2 页起全部错位",
     "getHotArticlesLogic.go:197（start = (page-1)*pageSize）"),

    ("M04", "hot-api", "service/HotRankPaginator.java",
     "item.setRank(result.size() + 1);",
     "item.setRank(result.size());",
     "rank 稠密性：改成从 0 开始 ⇒ rank 序列整体错位",
     "getHotArticlesLogic.go:120（item.Rank = int32(len(result)+1)）"),

    ("M05", "hot-api", "service/HotRankPaginator.java",
     "            if (item == null) {\n                continue;\n            }\n",
     "",
     "补全失败的空位不再跳过 ⇒ nil 元素进了结果列表",
     "getHotArticlesLogic.go:116-118（if item == nil { continue }）"),

    ("M06", "hot-api", "service/HotRankPaginator.java",
     "if (range[0] == range[1]) {",
     "if (range[0] <= range[1]) {",
     "空页判定：range[0] < range[1] 时被误判成空页 ⇒ 非空页返回空列表",
     "getHotArticlesLogic.go:80（items[start:end]）"),

    ("M07", "hot-api", "form/GoFormValues.java",
     "return Integer.parseInt(v, 10);",
     "return Integer.parseInt(v, 8);",
     "十进制解析：radix 改成 8 ⇒ \"010\" 变成 8 而不是 10",
     "getHotArticlesLogic.go:172-176（strconv.ParseInt(str, 10, 32)）"),

    ("M08", "hot-api", "client/GoStyleGrpcCallDeadline.java",
     ": Math.min(DEFAULT_CALL_TIMEOUT_MILLIS, remainingBudgetMs);",
     ": DEFAULT_CALL_TIMEOUT_MILLIS;",
     "gRPC deadline：去掉「请求剩余预算」约束 ⇒ 单次调用可能超出 HTTP 总预算",
     "zrpc/config.go:31（Timeout default=2000）+ ctx 剩余预算取 min"),

    ("M09", "hot-api", "advice/GlobalResponseAdvice.java",
     "if (body == null || body instanceof Response) {",
     "if (body == null) {",
     "响应包装：不再识别「已成型的信封」⇒ 错误响应被套两层信封",
     "hotcenter.go:45-51（httpx.SetOkHandler 只包一层）"),

    ("M10", "hot-api", "service/HotArticleHydrator.java",
     "articleResp = articleRpcClient.getPublicArticle(hotItem.getArticleId(), remainingBudgetMs);",
     "articleResp = articleRpcClient.getPublicArticle(hotItem.getArticleId(), remainingBudgetMs + 1000);",
     "补全预算：给下游多要 1s ⇒ 单条补全可能突破 HTTP 总预算",
     "getHotArticlesLogic.go:132（用 ctx 的 deadline，不额外加预算）"),

    ("M11", "hot-api", "service/HotArticleHydrator.java",
     "        long remainNanos = deadlineNanos - System.nanoTime();",
     "        long remainNanos = deadlineNanos;",
     "补全预算：把「绝对 deadline」误当成「剩余毫秒」原样传下去 ⇒ 请求 deadline 彻底失效（DRIFT-HOTAPI-029）",
     "Go 的 min(ctx.deadline, now+2s)：每个调用在**自己的发起时刻**重算"),

    # ─────────────────────────── hot-rpc（第十一轮）───────────────────────────
    ("R01", "hot-rpc", "domain/HeavyKeeper.java",
     "items.sort((a, b) -> Integer.compareUnsigned(b.count, a.count));",
     "items.sort((a, b) -> Integer.compare(b.count, a.count));",
     "Top-K 排序：uint32 无符号比较改成有符号 ⇒ count 超过 2^31 的条目排到末尾",
     "heavykeeper.go 的 uint32 计数语义（桶表 count 是 uint32）"),

    ("R02", "hot-rpc", "domain/HeavyKeeper.java",
     "        if (count == 0) {\n            return;\n        }",
     "        if (count < 0) {\n            return;\n        }",
     "Top-K 准入：count==0 的条目不再被拦 ⇒ 空计数条目进了榜",
     "heavykeeper.go 的 updateTopK（count==0 直接 return）"),

    ("R03", "hot-rpc", "domain/HeavyKeeper.java",
     "double decayProb = Math.pow(decay, unsignedToDouble(counts[row][col]));",
     "double decayProb = Math.pow(decay, counts[row][col]);",
     "桶冲突的衰减概率：按 uint32 无符号取指数改成有符号 ⇒ 概率算错",
     "heavykeeper.go 的 decay^count（count 是 uint32）"),

    ("R04", "hot-rpc", "service/HotRankingQueryService.java",
     "        if (k > MAX_TOP_K) {\n            k = MAX_TOP_K;\n        }",
     "        if (k > MAX_TOP_K) {\n            k = MAX_TOP_K + 1;\n        }",
     "TopK 上限：截断值 +1 ⇒ top_k=101 时多返回 1 条",
     "get_hot_articles_logic.go:38-40（k > 100 ⇒ 100）"),

    ("R05", "hot-rpc", "service/HotRankingQueryService.java",
     "        if (k <= 0) {\n            k = DEFAULT_TOP_K;\n        }",
     "        if (k < 0) {\n            k = DEFAULT_TOP_K;\n        }",
     "TopK 下界：k==0 不再回落默认值 ⇒ top_k=0 时返回空榜而非 20 条",
     "get_hot_articles_logic.go:35-37（k <= 0 ⇒ 20）"),

    ("R06", "hot-rpc", "mq/HotEventConsumer.java",
     "if (weight == null || weight <= 0) {",
     "if (weight == null) {",
     "事件校验：零权重 type 不再被 drop ⇒ 权重 0 的事件也计入热榜",
     "hot_handler.go:168 附近（weights[type] 缺失或 <= 0 ⇒ drop）"),

    ("R07", "hot-rpc", "mq/HotRedisSnapshotStore.java",
     "redis.call('EXPIRE', KEYS[2], 300)",
     "redis.call('EXPIRE', KEYS[2], 600)",
     "分片快照 TTL：Lua 里写死的 300 改成 600 ⇒ 分片存活期变了",
     "hot_handler.go:232（EXPIRE 300 写死在 Lua 里）"),

    ("R08", "hot-rpc", "mq/HotMergeScheduler.java",
     "private static final int MERGED_LIMIT = 100;",
     "private static final int MERGED_LIMIT = 101;",
     "合并结果截断：100 改成 101 ⇒ 合并榜多留 1 条",
     "hot_handler.go:383-385（merged[:100]）"),

    ("R09", "hot-rpc", "mq/HotMergeScheduler.java",
     "merged.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));",
     "merged.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));",
     "合并排序：由降序改成升序 ⇒ 合并后的热榜顺序整个反过来",
     "hot_handler.go:383 附近（按 score 降序）"),

    ("R10", "hot-rpc", "service/impl/HotServiceGrpcImpl.java",
     "observer.onError(Status.UNKNOWN",
     "observer.onError(Status.INTERNAL",
     "gRPC 错误映射：兜底分支由 UNKNOWN 改成 INTERNAL ⇒ 客户端看到的 code 变了",
     "hot_service_server.go 的裸 error ⇒ status.FromError ⇒ codes.Unknown"),

    # ─────────────────────────── hot-common（第十一轮）───────────────────────────
    ("HC01", "hot-common", "errmsg/HotErrorMessages.java",
     "map.put(HotErrorCode.SUCCESS.getCode(), \"OK\");",
     "map.put(HotErrorCode.SUCCESS.getCode(), \"ok\");",
     "成功文案：\"OK\" 改成 \"ok\" ⇒ 信封的 msg 字段漂移",
     "errmsg.go 的 codeMsg[Success]"),

    ("HC02", "hot-common", "errmsg/HotErrorMessages.java",
     "return MSG_BY_CODE.get(HotErrorCode.ERROR.getCode());",
     "return \"\";",
     "未知码回落：不再回落到 \"FAIL\" ⇒ 未知 code 的 msg 变成空串",
     "errmsg.go 的 `if !ok { return codeMsg[Error] }`"),

    ("HC03", "hot-common", "errmsg/HotErrorCode.java",
     "ERROR_SERVER_COMMON(5001),",
     "ERROR_SERVER_COMMON(5002),",
     "码值漂移：5001 改成 5002 ⇒ 业务错误码错位",
     "errmsg.go:38-45（ErrorServerCommon = 5001）"),

    ("HC04", "hot-common", "errmsg/HotErrorCode.java",
     "CODE_SERVER_BUSY(1015),",
     "CODE_SERVER_BUSY(1016),",
     "码值漂移：1015 改成 1016 ⇒ 服务忙码错位",
     "errmsg.go（CodeServerBusy = 1015）"),
]


def module_path(module_key):
    mpath, pkg_root = MODULES[module_key]
    return os.path.join(ROOT, mpath, "src", "main", "java", *pkg_root.split("/"))


def reports_dir(module_key):
    mpath, _ = MODULES[module_key]
    return os.path.join(ROOT, mpath, "target", "surefire-reports")


def sh(cmd, cwd=ROOT):
    return subprocess.run(cmd, shell=True, cwd=cwd, capture_output=True, text=True)


def run_tests(module_key):
    mpath, _ = MODULES[module_key]
    cmd = (f"mvn -o -pl {mpath} -am test "
           "-Dsurefire.failIfNoSpecifiedTests=false")
    r = sh(cmd)
    return r.returncode, (r.stdout + r.stderr)


def collect_failures(module_key):
    """从 surefire 报告里收集失败/错误的测试类。"""
    failed = []
    total = 0
    d = reports_dir(module_key)
    if not os.path.isdir(d):
        return failed, total
    for name in sorted(os.listdir(d)):
        if not name.endswith(".xml"):
            continue
        try:
            root = ET.parse(os.path.join(d, name)).getroot()
        except ET.ParseError:
            continue
        total += int(root.get("tests", 0))
        for tc in root.findall("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                failed.append(f"{root.get('name')}#{tc.get('name')}")
    return failed, total


# ⚠️⚠️ **还原方式必须是「备份文件」，不能是 `git checkout --`**（2026-10-24 实测踩过）
#
#   `git checkout -- <file>` 还原的是 **HEAD** ⇒ **会把该文件里【尚未提交】的真修复一起抹掉**。
#   实测：在修 DRIFT-HOTAPI-029（改了 HotArticleHydrator.java）之后跑本脚本的变异验证，
#   脚本用 `git checkout` 还原 ⇒ **修复被抹回 HEAD**，而**没有**被变异的
#   HotArticleQueryService.java 仍保留新调用 ⇒ 两个文件**口径不一致**
#   ⇒ 守门用例报出一个**看似业务 bug、实为工具副作用**的失败（记录到的「剩余预算」
#   是 2.7e15 —— 绝对 deadline 的纳秒值）。
#
#   ⇒ 本脚本改为：**变异前把原文备份到内存**，还原时**逐字写回**。
#   ⇒ 这样只回滚「本次变异」，不碰任何其它改动。
_BACKUPS = {}


def mutate(module_key, rel, old, new):
    path = os.path.join(module_path(module_key), rel)
    if not os.path.isfile(path):
        return False, f"文件不存在：{rel}"
    src = open(path, encoding="utf-8").read()
    n = src.count(old)
    if n != 1:
        return False, f"old 片段出现 {n} 次（要求恰好 1 次）⇒ 变异脚本失效"
    _BACKUPS[(module_key, rel)] = src
    open(path, "w", encoding="utf-8").write(src.replace(old, new, 1))
    return True, "ok"


def restore(module_key, rel):
    path = os.path.join(module_path(module_key), rel)
    backup = _BACKUPS.pop((module_key, rel), None)
    if backup is None:
        # 没有备份（例如变异没成功）⇒ **不动文件**，绝不回退到 HEAD
        print("       ⚠️ 无备份可还原 ⇒ 保持文件原样（绝不 git checkout）")
        return
    open(path, "w", encoding="utf-8").write(backup)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None, help="只跑指定变异 id（逗号分隔）")
    ap.add_argument("--module", default=None, choices=list(MODULES), help="只跑某个模块")
    ap.add_argument("--list", action="store_true", help="只列清单")
    args = ap.parse_args()

    if args.list:
        for m in MUTATIONS:
            print(f"{m[0]:<6} {m[1]:<12} {m[2]:<45} {m[5]}")
        return 0

    only = set(args.only.split(",")) if args.only else None
    mods = [args.module] if args.module else list(MODULES)

    # 基线：不带变异必须全绿（否则「变红」没有意义）
    print("==> [基线] 不带变异跑一次，确认全绿")
    base = {}
    for mk in mods:
        rc, out = run_tests(mk)
        failed, total = collect_failures(mk)
        base[mk] = total
        print(f"    {mk:<12} exit={rc} tests={total} failures={len(failed)}")
        if failed:
            print(f"    ❌ 基线就不绿，后续判定无效：")
            for f in failed[:10]:
                print(f"       {f}")
            return 2

    results = []
    for mid, mk, rel, old, new, desc, src in MUTATIONS:
        if only and mid not in only:
            continue
        if mk not in mods:
            continue
        print(f"\n==> {mid}  [{mk}]  {desc}")
        print(f"    出处：{src}")
        ok, why = mutate(mk, rel, old, new)
        if not ok:
            print(f"    ⚠️  跳过（{why}）")
            results.append({"id": mid, "module": mk, "verdict": "SKIP", "why": why,
                            "desc": desc})
            continue
        try:
            t0 = time.time()
            rc, out = run_tests(mk)
            failed, total = collect_failures(mk)
            dt = time.time() - t0
            if "COMPILATION ERROR" in out:
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
                print(f"       ⚠️ 先证伪「变异本身有效」（P22）再下结论")
            results.append({"id": mid, "module": mk, "verdict": verdict, "desc": desc,
                            "source": src, "failed": failed, "tests": total,
                            "seconds": round(dt, 1)})
        finally:
            restore(mk, rel)

    print("\n══════════ 汇总 ══════════")
    caught = [r for r in results if r["verdict"] == "CAUGHT"]
    gaps = [r for r in results if r["verdict"] == "GAP"]
    skipped = [r for r in results if r["verdict"] == "SKIP"]
    for r in results:
        mark = {"CAUGHT": "✅", "GAP": "🔴", "SKIP": "⏭️"}[r["verdict"]]
        print(f"  {mark} {r['id']:<6} [{r['module']}] {r['desc']}")
        if r.get("failed"):
            print(f"        抓到：{', '.join(r['failed'][:4])}"
                  + (" …" if len(r['failed']) > 4 else ""))
    print(f"\n  守门有效 {len(caught)} / 守门缺口 {len(gaps)} / 跳过 {len(skipped)}")
    out_dir = os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest")
    os.makedirs(out_dir, exist_ok=True)
    # ⚠️ 按模块分文件：第九轮 hot-api 的证据不能被第十一轮覆盖
    suffix = "-".join(mods) if len(mods) > 1 else mods[0]
    out_path = os.path.join(out_dir, f"u9-mutation-{suffix}.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({"baseline_tests": base, "results": results}, f,
                  ensure_ascii=False, indent=2)
    print(f"  明细：{out_path}")
    return 1 if gaps else 0


if __name__ == "__main__":
    sys.exit(main())
