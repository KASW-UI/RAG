#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真 NUL 字节守门 —— 拦「文件被 git 当成二进制 ⇒ 三方合并静默丢内容 / 揉坏」。

🔴 **为什么需要这条守门（2026-10-28 实测）**
    git 对**含 NUL 字节**的文件按【二进制】处理 ⇒ ⇒ **三方合并不做文本合并**，
    只可能「取一边」或「逐块揉」⇒ ⇒ **而且【不报冲突】** ⇒ ⇒
    **「merge-tree 零冲突」推不出「内容完整」**。
    ⇒ 实测（RAG 仓 · G4 第 15 次全量收口）：
      ① `service/favorite/favorite-rpc/.../FavoriteDdlParityTest.java`（含 4 个 NUL）
         ⇒ 三方 blob = `9a0609fc` / G3 = `8648028b` / **合并树 = `6077498e`（第三版）**
         ⇒ G3「Testcontainers 宿主口钉口」的 **PG 那半边在合并树里被揉没了**；
      ② `docs/Pitfalls/favorite/rpc.md`（含 NUL）
         ⇒ G3 的 `PIT-FAV-RPC-040` **整条没进合并树**（40 行 → 39 条）；
      ③ `docs/DRIFT/users/admin/rpc.md`（含 2 个 NUL）
         ⇒ 本轮侥幸没丢（只有一边改 ⇒ 取那一边），但**只要第二个人也改它 ⇒ 立刻揉坏**。

🔴 **判据**：`git diff --numstat <A> <B> -- <file>` 输出 `-  -` ⇒ ⇒ git 已把它当二进制。

✅ **本守门拦什么**
    ① `docs/**`（台账 / 文档）⇒ **必须零 NUL**（文档没有「故意 NUL 夹具」这种正当理由；
       真字节只会被别人照抄 ⇒ 等于把「二进制」扩散出去 ⇒ 参见 U+FFFD 同款纪律：
       **损坏形态只能用可见转义写进会被别人照抄的文档**）；
    ② `service/**` · `tools/**`（源码 / 脚本）⇒ **白名单外零 NUL**
       （源码里可以有**故意**的 NUL 夹具，例如 Redis 复合 key 分隔符 ⇒
        但那只允许【写法等价】（Java 的 `'\0'`）+ 必须**显式登记**）。

⚠️ **白名单是【豁免】，不是【免检】**
    · 每条必须写清「为什么豁免 + 谁该修 + 修完怎么删」；
    · 文件已无 NUL / 已不存在 ⇒ 🟡 提示「白名单可清空」（**不放过**）；
    · NUL 数量变了 ⇒ 🟡 提示「内容又变了，请复核豁免是否仍成立」。

用法
----
    python3 tools/difftest/check-no-nul-bytes.py                   # 正常跑（EXIT=0/1）
    python3 tools/difftest/check-no-nul-bytes.py --print            # 只打印，不判红
    python3 tools/difftest/check-no-nul-bytes.py --root <dir>       # 指定扫描根（不给 git 用）
    python3 tools/difftest/check-no-nul-bytes.py --mutation-test    # 内置变异自测（EXIT=0 = 全命中）
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

NUL = b"\x00"

# ---------------------------------------------------------------- 扫描范围
SCAN_DIRS = ("docs", "service", "tools")

# 白名单：路径 → 豁免原因（**不可为空**；理由要写清「谁该修 + 修完怎么删」）
WHITELIST: dict[str, str] = {
    "service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/"
    "FavoriteDdlParityTest.java": (
        "G3 写权面：Redis 复合 key 的【故意测试夹具】（U+0000 作分隔符）⇒ "
        "运行时语义依赖真字节 ⇒ 等 G3 改成 Java 转义 '\\0'（U+0000，语义等价）后即可删除本条"
    ),
    "docs/DRIFT/users/admin/rpc.md": (
        "G1 写权面：台账记录了被观测对象的真实 NUL ⇒ "
        "等 G1 把台账里的真字节改成可见转义（U+0000）后即可删除本条"
    ),
}

SKIP_DIR_NAMES = {".git", "target", "node_modules", ".venv", "__pycache__", ".mvn", ".idea"}


# ---------------------------------------------------------------- 收集文件
def _git_files(root: str) -> list[str]:
    """仓库模式：git 已追踪 + 未跟踪（排除 .gitignore）⇒ 拦住「还没提交的新文件」。"""
    try:
        out = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
            cwd=root, capture_output=True, text=True, check=True,
        )
    except Exception:
        return []
    return [p for p in out.stdout.splitlines() if p]


def _walk_files(root: str) -> list[str]:
    """`--root` 模式：文件系统遍历（变异自测用，不是 git 仓）。"""
    found: list[str] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIR_NAMES]
        for fn in filenames:
            rel = os.path.relpath(os.path.join(dirpath, fn), root)
            found.append(rel.replace(os.sep, "/"))
    return found


def _in_scope(rel: str) -> bool:
    if rel == "AGENTS.md":
        return True
    return any(rel == d or rel.startswith(d + "/") for d in SCAN_DIRS)


# ---------------------------------------------------------------- 扫描
def scan(root: str, files: list[str]) -> tuple[list[dict], list[str]]:
    """返回 (命中列表, 扫描文件数)。命中项：{path, nul, size, whitelisted}"""
    hits: list[dict] = []
    scanned = 0
    for rel in sorted(files):
        if not _in_scope(rel):
            continue
        full = os.path.join(root, rel)
        if not os.path.isfile(full) or os.path.islink(full):
            continue
        try:
            with open(full, "rb") as fh:
                data = fh.read()
        except OSError:
            continue
        scanned += 1
        n = data.count(NUL)
        if n:
            hits.append({
                "path": rel,
                "nul": n,
                "size": len(data),
                "whitelisted": rel in WHITELIST,
            })
    return hits, scanned


# ---------------------------------------------------------------- 报告
def report(root: str, hits: list[dict], scanned: int, as_print: bool) -> list[str]:
    lines: list[str] = []
    bad = [h for h in hits if not h["whitelisted"]]
    exempted = [h for h in hits if h["whitelisted"]]
    stale: list[str] = []

    # 白名单里已被修掉 / 已不存在的条目 ⇒ 🟡 提示清空
    for path in WHITELIST:
        full = os.path.join(root, path)
        if not os.path.isfile(full):
            stale.append(f"⚠️ 白名单条目已不存在 ⇒ 可删除：{path}")
            continue
        with open(full, "rb") as fh:
            n = fh.read().count(NUL)
        if n == 0:
            stale.append(f"⚠️ 白名单条目已【无 NUL】⇒ 可删除（修完了）：{path}")
        elif path in {h["path"] for h in exempted}:
            listed = next(h for h in exempted if h["path"] == path)
            if listed["nul"] != n:
                stale.append(
                    f"⚠️ 白名单条目的 NUL 数量变了（登记 {listed['nul']} → 实测 {n}）"
                    f"⇒ 请复核豁免是否仍成立：{path}"
                )

    lines.append("真 NUL 字节守门（git 二进制化 ⇒ 合并静默丢内容 / 揉坏）")
    lines.append("=" * 60)
    lines.append(f"  扫描根: {root}")
    lines.append(f"  扫描文件: {scanned} 个（范围：docs/ · service/ · tools/ · AGENTS.md）")
    lines.append(f"  含真 NUL 的文件: {len(hits)} 个（豁免 {len(exempted)} · 未豁免 {len(bad)}）")
    # ⚠️ 【CI 汇总用的机器可读标记】—— 2026-10-31 新增（纯增量，不改判定）。
    #   格式约定见 check-no-prod-secrets.py 的同一处注释：
    #   行首 `[ci-exempt]`，字段 `key=value` ⇒ ci/scripts/run-static-gates.sh 消费。
    lines.append(f"[ci-exempt] check-no-nul-bytes.py exempted={len(exempted)} "
                 f"unexempted={len(bad)}")
    lines.append("-" * 60)

    for h in exempted:
        lines.append(f"⚠️ 已豁免 · {h['path']}  (NUL x{h['nul']}, {h['size']} B)")
        lines.append(f"     理由：{WHITELIST[h['path']]}")
    for h in bad:
        lines.append(f"🔴 未豁免 · {h['path']}  (NUL x{h['nul']}, {h['size']} B)")
    for s in stale:
        lines.append(s)

    lines.append("-" * 60)
    if bad:
        lines.append("❌ FAIL")
        lines.append("")
        lines.append("🔴 含真 NUL 的文件会让 git 按【二进制】处理 ⇒ 三方合并【不报冲突、只取一边/逐块揉】")
        lines.append("   ⇒ ⇒ 合并树会静默丢内容 ⇒ ⇒ 「merge-tree 零冲突」推不出「内容完整」。")
        lines.append("   ⇒ 修法：文档类 → 改成【可见转义】（U+0000 / \\0）；源码类 → 改【等价写法】（Java '\\0'）")
        lines.append("   ⇒ 自测判据：git diff --numstat <A> <B> -- <file>  输出 '-  -' ⇒ 已二进制化")
    else:
        lines.append("✅ PASS")
        lines.append("   · 扫描范围内没有未豁免的真 NUL 字节")
        if exempted:
            lines.append(f"   · 另有 {len(exempted)} 个【已豁免】（理由见上 ⇒ 修完即可删白名单）")
        if stale:
            lines.append("   · 🟡 白名单有可清理项（见上）")
    return lines


# ---------------------------------------------------------------- 变异自测
def _mutate(root: str, rel: str, where: str) -> str:
    """往指定文件注入 1 个真 NUL（副本目录内，不碰真实工作区）。"""
    full = os.path.join(root, rel)
    with open(full, "rb") as fh:
        data = fh.read()
    idx = data.find(where.encode()) + len(where.encode())
    with open(full, "wb") as fh:
        fh.write(data[:idx] + NUL + data[idx:])
    return rel


def mutation_test() -> int:
    """变异自测：每条变异都必须被守门【抓到】⇒ 抓不到 = 守门是假绿 ⇒ EXIT=1。"""
    results: list[tuple[str, bool, str]] = []

    def run_case(name: str, build, expect_path: str | None, expect_mentions: bool) -> None:
        tmp = tempfile.mkdtemp(prefix="g4-nul-mut-")
        try:
            build(tmp)
            res = scan(tmp, _walk_files(tmp))
            hits, _ = res
            out = "\n".join(report(tmp, hits, len(_walk_files(tmp)), False))
            bad = [h for h in hits if not h["whitelisted"]]
            ok = bool(bad) and (not expect_path or expect_path in out)
            if expect_mentions and expect_path and expect_path not in out:
                ok = False
            results.append((name, ok, f"未豁免命中 {len(bad)} 个"))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def build_clean(tmp: str) -> None:
        os.makedirs(os.path.join(tmp, "docs/DRIFT"), exist_ok=True)
        os.makedirs(os.path.join(tmp, "service/x"), exist_ok=True)
        with open(os.path.join(tmp, "docs/DRIFT/clean.md"), "w", encoding="utf-8") as fh:
            fh.write("# clean\n\n这是一份干净台账。\n")
        with open(os.path.join(tmp, "service/x/Clean.java"), "w", encoding="utf-8") as fh:
            fh.write("class Clean { void run() {} }\n")

    # M1：文档里注入 1 个 NUL ⇒ 必须红且点名该文件
    def b1(tmp: str) -> None:
        build_clean(tmp)
        _mutate(tmp, "docs/DRIFT/clean.md", "干净台账")
    run_case("M1 文档注入真 NUL ⇒ 必须红 + 点名", b1, "docs/DRIFT/clean.md", True)

    # M2：源码里注入 1 个 NUL ⇒ 必须红且点名该文件
    def b2(tmp: str) -> None:
        build_clean(tmp)
        _mutate(tmp, "service/x/Clean.java", "class Clean")
    run_case("M2 源码注入真 NUL ⇒ 必须红 + 点名", b2, "service/x/Clean.java", True)

    # M3：豁免表里的路径不再豁免 ⇒ 必须红（模拟「白名单被误清 / 失效」）
    tmp = tempfile.mkdtemp(prefix="g4-nul-mut-")
    try:
        target = ("service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/"
                  "FavoriteDdlParityTest.java")
        os.makedirs(os.path.dirname(os.path.join(tmp, target)), exist_ok=True)
        with open(os.path.join(tmp, target), "wb") as fh:
            fh.write(b"class FavoriteDdlParityTest { byte[] k = {'a', " + NUL + b", 'b'}; }\n")
        saved = dict(WHITELIST)
        WHITELIST.clear()
        try:
            fl = _walk_files(tmp)
            hits, _ = scan(tmp, fl)
            bad = [h for h in hits if not h["whitelisted"]]
            out = "\n".join(report(tmp, hits, len(fl), False))
            ok = bool(bad) and target in out
            results.append(("M3 白名单失效 ⇒ 该文件必须红 + 点名", ok, f"未豁免命中 {len(bad)} 个"))
        finally:
            WHITELIST.clear()
            WHITELIST.update(saved)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    # M4：白名单条目已无 NUL ⇒ 必须给出「可清空」提示
    tmp = tempfile.mkdtemp(prefix="g4-nul-mut-")
    try:
        target = "docs/DRIFT/users/admin/rpc.md"
        os.makedirs(os.path.dirname(os.path.join(tmp, target)), exist_ok=True)
        with open(os.path.join(tmp, target), "w", encoding="utf-8") as fh:
            fh.write("# 没有 NUL 的台账\n")
        fl = _walk_files(tmp)
        hits, _ = scan(tmp, fl)
        out = "\n".join(report(tmp, hits, len(fl), False))
        ok = ("已【无 NUL】" in out)
        results.append(("M4 白名单已失效 ⇒ 必须提示可清空", ok, "提示缺失"))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    print("真 NUL 字节守门 · 内置变异自测")
    print("=" * 60)
    failed = 0
    for name, ok, detail in results:
        print(f"  {'✅' if ok else '❌'} {name}   ({detail})")
        if not ok:
            failed += 1
    print("-" * 60)
    if failed:
        print(f"❌ {failed} 项变异未被抓到 ⇒ 守门是假绿")
        return 1
    print(f"✅ {len(results)} 项变异全部被抓到")
    return 0


# ---------------------------------------------------------------- main
def main() -> int:
    args = sys.argv[1:]
    if "--mutation-test" in args:
        return mutation_test()

    root = REPO
    if "--root" in args:
        i = args.index("--root")
        if i + 1 >= len(args):
            print("--root 需要一个目录参数")
            return 2
        root = os.path.abspath(args[i + 1])

    files = _walk_files(root) if "--root" in args else _git_files(root)
    if not files:
        print(f"⚠️ 在 {root} 下没收集到任何文件 ⇒ 无法判定")
        return 2

    hits, scanned = scan(root, files)
    lines = report(root, hits, scanned, "--print" in args)
    print("\n".join(lines))
    if "--print" in args:
        return 0
    return 1 if any(not h["whitelisted"] for h in hits) else 0


if __name__ == "__main__":
    sys.exit(main())
