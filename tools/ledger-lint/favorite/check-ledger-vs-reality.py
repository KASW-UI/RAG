#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
favorite 域 · **「台账 vs 实际」复核**（ledger vs reality）
=========================================================

它管的是另一件事：

  · `check-ledger-selfcheck.py` 管「**台账之间**」—— 章节指针 / 编号闭合 / Go 源文件落点；
  · `check-assertion-sources.py` 管「**注释里的 Go 断言**」—— 锚点能不能解析、行号在不在范围内；
  · **本工具**管「**台账说的**」⇄「**仓库实际的**」—— 台账正文里那些**事实断言**
    （测试计数 / 已签字条数 / 跨域清单的条数 / 探针清单 / 端口段 / 共享面零改动）
    **是不是还成立**。

为什么要单独做这一层
--------------------
台账里最危险的断言不是「指针指错」（那个有自检兜底），而是**「数字」**：

  · 「6 个测试类 / 89 例全绿」—— 代码加了测试类、例子加了用例 ⇒ **数字过期**，
    而台账读起来完全通顺 ⇒ **没有任何工具会响**；
  · 「13 个别域模块缺 grpc-inprocess」—— 别的域修了几个 ⇒ **条数过期**；
  · 「全仓 7 个域 / 14 个测试类用 RANDOM_PORT」—— 别的域改了 ⇒ **条数过期**。

2026-10-25 第十五轮盘点**当场**抓到一处（`api.md` 写「6 类 / 89 例」，实际已是 8 类 / 94 例）
⇒ 与 `rpc.md` §16「台账体检」是同款缺陷，只是**发生在数字上**。

做法（与其它工具的关键区别）
---------------------------
**断言从台账正文里抽**（不把「真相」写死在工具里）
⇒ 再**独立**从仓库算实际值 ⇒ 逐条比对。
⇒ 所以台账一旦改了说法，工具会**跟着改口径**，而不是拿旧真相去硬套。

门禁 / 提示的分级
-----------------
**门禁级** = 实际值**无歧义**（文件数、定义数、pom 扫描、git diff、常量）⇒ 不一致即红；
**提示级** = 实际值**有歧义**（用例数：源码 `@Test` 注解 ≠ surefire 报告
              —— 注释里的示例代码、`@Testcontainers` 误匹配都会让两者不等）
           ⇒ **只报差异不判死**（依据：`docs/Pitfalls/_cross/testing-pitfalls.md`
              的「陈旧 surefire 报告静默抬高测试计数」一条）。

用法
----
    python3 tools/ledger-lint/favorite/check-ledger-vs-reality.py

退出码
------
    0 = 无门禁级不一致（提示级差异不算失败）
    1 = 存在门禁级不一致
    2 = 工具自身出错
"""
import argparse
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

# ── 本域的「共享面」定义（AGENTS.md §5.3）───────────────────────────────
SHARED_SURFACE = (
    "docs/DRIFT/_INDEX.md",
    "docs/DRIFT/_MANIFEST.json",
    "docs/Intentional_Deviations/_INDEX.md",
    "docs/Pitfalls/_INDEX.md",
    "AGENTS.md",
    "docs/DRIFT/_cross/",
    "service/common/",
)

DRIFT = "docs/DRIFT/favorite"
DEVIATIONS = "docs/Intentional_Deviations/favorite"

results = []  # (level, ok, name, claim, actual)


def record(level, ok, name, claim, actual):
    results.append((level, ok, name, claim, actual))


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def grep1(text, pattern, what):
    """在文本里找第一个匹配；找不到 ⇒ 明确报「台账里找不到这条断言」。
    ⚠️ 必带 re.M —— 台账断言几乎都在**行首**（表格行），没有 MULTILINE 时 `^` 只匹配整个文件开头。
    """
    m = re.search(pattern, text, re.M)
    if not m:
        return None, f"台账里找不到断言：{what}"
    return m, None


def git(root, *args):
    return subprocess.run(("git",) + args, cwd=root, capture_output=True, text=True).stdout


# ══════════════════════════════════════════════════════════════════════════
# ① favorite-api 测试**类**数
# ══════════════════════════════════════════════════════════════════════════
def check_test_classes(root):
    md = read(os.path.join(root, DRIFT, "api.md"))
    m, err = grep1(md, r"\*\*(\d+) 个测试类", "「N 个测试类」")
    if err:
        record("门禁", False, "favorite-api 测试类数", "—", err)
        return
    claim = int(m.group(1))
    test_root = os.path.join(root, "service/favorite/favorite-api/src/test")
    actual = sum(1 for _r, _d, fs in os.walk(test_root) for f in fs if f.endswith("Test.java"))
    record("门禁", claim == actual, "favorite-api 测试类数",
           f"台账 §0：{claim} 个测试类", f"仓库实际：{actual} 个（*Test.java）")


# ══════════════════════════════════════════════════════════════════════════
# ② favorite-api **用例数**（有歧义 ⇒ 提示级）
# ══════════════════════════════════════════════════════════════════════════
def check_test_cases(root):
    md = read(os.path.join(root, DRIFT, "api.md"))
    # ⚠️ 必须锚定 §0 摘要的「| 测试 |」那一行 —— 否则会匹配到**旧章节**里的同名口径
    #    （§6 的第十二轮记录里也写着「89 例全绿」⇒ 抽错行 ⇒ 报一个假的差异）
    m, err = grep1(md, r"^\| 测试 \| \*\*(\d+) 个测试类 / (\d+) 例全绿\*\*", "§0 的「| 测试 |」行")
    if err:
        record("提示", False, "favorite-api 用例数", "—", err)
        return
    claim = int(m.group(2))

    # 实际值①：surefire XML 求和（先判报告是不是比源码旧 —— 陈旧报告会抬高计数）
    rep_dir = os.path.join(root, "service/favorite/favorite-api/target/surefire-reports")
    src_root = os.path.join(root, "service/favorite/favorite-api/src/test")
    surefire_total, stale = 0, []
    if os.path.isdir(rep_dir):
        newest_src = max((os.path.getmtime(os.path.join(r, f))
                           for r, _d, fs in os.walk(src_root) for f in fs), default=0)
        for name in os.listdir(rep_dir):
            if not name.startswith("TEST-") or not name.endswith(".xml"):
                continue
            p = os.path.join(rep_dir, name)
            try:
                root_el = ET.parse(p).getroot()
                surefire_total += int(root_el.get("tests") or 0)
            except Exception:
                continue
            if os.path.getmtime(p) < newest_src:
                stale.append(name)

    # 实际值②：源码 @Test 注解数（剥注释；但注释里也可能**有**示例代码 ⇒ 仍会偏）
    def strip_comments(src):
        src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
        src = re.sub(r"//[^\n]*", "", src)
        return src

    ann = 0
    for r, _d, fs in os.walk(src_root):
        for f in fs:
            if f.endswith(".java"):
                ann += len(re.findall(r"@Test\b", strip_comments(read(os.path.join(r, f)))))

    detail = f"surefire 求和={surefire_total} · 源码 @Test 注解={ann}"
    if stale:
        detail += f" · ⚠️ {len(stale)} 份报告比源码旧（陈旧报告会抬高计数 ⇒ 已排除）"
        surefire_total = None
    record("提示", claim in (surefire_total, ann), "favorite-api 用例数",
           f"台账 §0：{claim} 例", detail)


# ══════════════════════════════════════════════════════════════════════════
# ③ 已签字偏离条数
# ══════════════════════════════════════════════════════════════════════════
def check_signed_deviations(root):
    md = read(os.path.join(root, DRIFT, "rpc.md"))
    m, err = grep1(md, r"\*\*已签字偏离\*\*\s*\|\s*\*\*(\d+)\*\*\s*条", "§18.4「已签字偏离 N 条」")
    if err:
        record("门禁", False, "已签字偏离条数", "—", err)
        return
    claim = int(m.group(1))
    actual = set()
    d = os.path.join(root, DEVIATIONS)
    for f in os.listdir(d):
        if f.endswith(".md"):
            for mm in re.finditer(r"^#{2,4}\s+(DEV-FAV-[A-Z]+-\d+)", read(os.path.join(d, f)), re.M):
                actual.add(mm.group(1))
    record("门禁", claim == len(actual), "已签字偏离条数",
           f"台账 §18.4：{claim} 条", f"仓库实际：{len(actual)} 条（{len(actual)} 个 DEV-FAV-* 定义）")


# ══════════════════════════════════════════════════════════════════════════
# ④ 🏷 候选条数（应为 0）
# ══════════════════════════════════════════════════════════════════════════
def check_candidates(root):
    d = os.path.join(root, DEVIATIONS)
    pending = []
    for f in os.listdir(d):
        if not f.endswith(".md"):
            continue
        txt = read(os.path.join(d, f))
        # 「🏷 候选」出现在某个 DEV-FAV 条目标题下、且该条目没标「已签字」⇒ 仍是候选
        for blk in re.split(r"^#{2,4}\s+", txt, flags=re.M)[1:]:
            title = blk.splitlines()[0]
            if "DEV-FAV-" not in title:
                continue
            if "🏷" in title and "已签字" not in title:
                pending.append(title.strip())
    md = read(os.path.join(root, DRIFT, "rpc.md"))
    claim_zero = "本域已无 🏷 候选" in md or "本域已无 🏷 候选" in read(os.path.join(root, DRIFT, "api.md"))
    ok = claim_zero and not pending
    record("门禁", ok, "🏷 候选条数",
           "台账：本域已无 🏷 候选", f"仓库实际：{len(pending)} 条" + (f" ⇒ {pending}" if pending else ""))


# ══════════════════════════════════════════════════════════════════════════
# ⑤ 跨域：运行时缺 grpc-inprocess 的模块数
# ══════════════════════════════════════════════════════════════════════════
def check_grpc_inprocess(root):
    md = read(os.path.join(root, DRIFT, "rpc.md"))
    # ⚠️ 数字前后的 **加粗** 与前缀措辞**不该影响抽取** ⇒ 正则只锚「可执行应用模块真缺…N 个」
    m, err = grep1(md, r"可执行应用模块真缺[^\n]{0,12}?(\d+)\s*个", "§22.3「可执行应用模块真缺 = N 个」")
    if err:
        record("门禁", False, "grpc-inprocess 缺失模块数", "—", err)
        return
    claim = int(m.group(1))

    # ⚠️ 判据（2026-10-25 第十五轮实测钉死，见 rpc.md §24.4）：
    #    **真缺 ⟺ 本模块直接声明了 grpc-inprocess 且 scope=test**
    #      ⇒ 直接声明会**覆盖**传递来的 compile（maven「最近者优先」）⇒ 运行时没有；
    #    **「没声明」不是缺陷** ⇒ devh starter 的 inprocess 是 compile ⇒ 传递下来就有
    #      （实测：改前的 favorite-api「没声明」⇒ dependency:tree = compile ⇒ 本来就不缺）。
    #    ⚠️ 各域的 `-common` 是**库**不是可执行模块 ⇒ 不计入「可执行应用模块」。
    bad_lib, bad_app = [], []
    svc = os.path.join(root, "service")
    for dirpath, _d, fs in os.walk(svc):
        if "pom.xml" not in fs:
            continue
        pom = read(os.path.join(dirpath, "pom.xml"))
        rel = os.path.relpath(dirpath, root)
        for blk in re.findall(r"<dependency>(.*?)</dependency>", pom, re.S):
            if "grpc-inprocess" not in blk and "<artifactId>inprocess</artifactId>" not in blk:
                continue
            if "<scope>test</scope>" in blk:
                (bad_lib if rel.endswith("-common") else bad_app).append(rel)
            break
    record("门禁", claim == len(bad_app), "grpc-inprocess 缺失（可执行应用模块）",
           f"台账 §22.3：{claim} 个", f"仓库实际：{len(bad_app)} 个 ⇒ {sorted(bad_app)}"
           + (f"；另有 {len(bad_lib)} 个 `-common` 库（不算可执行模块）：{sorted(bad_lib)}" if bad_lib else ""))


# ══════════════════════════════════════════════════════════════════════════
# ⑥ 跨域：全仓 RANDOM_PORT 的域数 / 测试类数
# ══════════════════════════════════════════════════════════════════════════
def strip_java_comments(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    return src


def check_random_port(root):
    md = read(os.path.join(root, DRIFT, "api.md"))
    m, err = grep1(md, r"(\d+)\s*个域\s*/\s*(\d+)\s*个测试类", "§9.5「N 个域 / M 个测试类」")
    if err:
        record("门禁", False, "RANDOM_PORT 暴露面", "—", err)
        return
    claim_domains, claim_files = int(m.group(1)), int(m.group(2))

    files = []
    for dirpath, _d, fs in os.walk(os.path.join(root, "service")):
        for f in fs:
            if f.endswith(".java"):
                p = os.path.join(dirpath, f)
                # ⚠️ 剥注释后计数 ⇒ 只在 javadoc 里提到 RANDOM_PORT 的**支撑类**不算
                #    （本域的 RealTomcatPort / RealTomcatPortTest 就是这种）
                if re.search(r"\bRANDOM_PORT\b", strip_java_comments(read(p))):
                    files.append(os.path.relpath(p, root))
    domains = {f.split("/")[1] for f in files}   # service/<域>/...
    record("门禁", claim_domains == len(domains) and claim_files == len(files),
           "RANDOM_PORT 暴露面",
           f"台账 §9.5：{claim_domains} 个域 / {claim_files} 个测试类",
           f"仓库实际：{len(domains)} 个域 / {len(files)} 个测试类")


# ══════════════════════════════════════════════════════════════════════════
# ⑦ 端口段声明（Java 侧 / 探针侧）
# ══════════════════════════════════════════════════════════════════════════
def check_port_ranges(root):
    md = read(os.path.join(root, DRIFT, "api.md"))
    # ⑦-1 Java 侧：台账写「21300–21399」⇒ 代码常量
    m, err = grep1(md, r"自选专用端口段\s*(\d+)[–-](\d+)", "§9.3「自选专用端口段 21300–21399」")
    if err:
        record("门禁", False, "端口段（Java 侧）", "—", err)
    else:
        claim = (int(m.group(1)), int(m.group(2)))
        src = read(os.path.join(root, "service/favorite/favorite-api/src/test/java/com/rag/favorite/"
                                   "favorite/api/support/RealTomcatPort.java"))
        first = re.search(r"FIRST\s*=\s*(\d+)", src)
        last = re.search(r"LAST\s*=\s*(\d+)", src)
        actual = (int(first.group(1)), int(last.group(1))) if first and last else None
        record("门禁", actual == claim, "端口段（Java 侧）",
               f"台账 §9.3：{claim[0]}–{claim[1]}", f"代码常量：{actual}")

    # ⑦-2 探针侧：台账写「21400–21599」⇒ 两个探针里的 python
    m2, err2 = grep1(md, r"同样改成\*\*专用端口段\s*(\d+)[–-](\d+)", "§9.3 探针侧「专用端口段 21400–21599」")
    if err2:
        record("门禁", False, "端口段（探针侧）", "—", err2)
        return
    claim = (int(m2.group(1)), int(m2.group(2)))
    for probe in ("api-full-parity-probe", "grpc-contract-probe"):
        p = os.path.join(root, "tools", probe, "favorite", "run.sh")
        if not os.path.isfile(p):
            record("门禁", False, f"端口段（探针侧 · {probe}）", "—", "探针文件不存在")
            continue
        txt = read(p)
        f1 = re.search(r"FIRST,\s*LAST\s*=\s*(\d+),\s*(\d+)", txt)
        actual = (int(f1.group(1)), int(f1.group(2))) if f1 else None
        record("门禁", actual == claim, f"端口段（探针侧 · {probe}）",
               f"台账 §9.3：{claim[0]}–{claim[1]}", f"探针实际：{actual}")


# ══════════════════════════════════════════════════════════════════════════
# ⑧ 台账引用的探针目录必须真实存在
# ══════════════════════════════════════════════════════════════════════════
def check_probe_dirs(root):
    missing = []
    checked = 0
    for f in ("api.md", "rpc.md"):
        txt = read(os.path.join(root, DRIFT, f))
        for mm in re.finditer(r"tools/([a-z0-9-]+-probe)/favorite/", txt):
            rel = f"tools/{mm.group(1)}/favorite"
            checked += 1
            if not os.path.isdir(os.path.join(root, rel)):
                missing.append(rel)
    record("门禁", not missing, "台账引用的探针目录",
           f"台账引用了 {checked} 处探针目录", f"不存在：{sorted(set(missing))}" if missing else "全部存在")


# ══════════════════════════════════════════════════════════════════════════
# ⑨ 「共享面零改动」声明 —— 用 git 验
# ══════════════════════════════════════════════════════════════════════════
def check_shared_surface(root):
    base = git(root, "merge-base", "HEAD", "do-my-own-gh").strip()
    if not base:
        record("门禁", False, "共享面零改动", "—", "找不到与 do-my-own-gh 的 merge-base")
        return
    changed = [l for l in git(root, "diff", "--name-only", f"{base}..HEAD").splitlines() if l]
    touched = sorted({c for c in changed
                      if any(c == s or c.startswith(s) for s in SHARED_SURFACE)})
    record("门禁", not touched, "共享面零改动",
           f"台账 §22.6 / §23：共享面零改动（基线 {base[:7]}）",
           f"实际改动 {len(changed)} 个文件，其中共享面：{touched}" if touched
           else f"实际改动 {len(changed)} 个文件，共享面 0 个 ✅")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=None)
    args = ap.parse_args()

    root = args.root or os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                     "..", "..", ".."))
    if not os.path.isdir(os.path.join(root, "docs", "DRIFT", "favorite")):
        print(f"❌ 找不到 favorite 域台账目录：{root}", file=sys.stderr)
        return 2

    for fn in (check_test_classes, check_test_cases, check_signed_deviations,
               check_candidates, check_grpc_inprocess, check_random_port,
               check_port_ranges, check_probe_dirs, check_shared_surface):
        try:
            fn(root)
        except Exception as e:  # 工具自身出错 ⇒ 不静默放过
            record("门禁", False, fn.__name__, "—", f"工具执行异常：{e!r}")

    green, red, hint = "✅", "❌", "🟡"
    print("favorite 域 · 「台账 vs 实际」复核")
    print(f"根目录：{root}\n")
    gate_fail = 0
    for level, ok, name, claim, actual in results:
        mark = green if ok else (red if level == "门禁" else hint)
        if not ok and level == "门禁":
            gate_fail += 1
        print(f"  {mark} [{level}] {name}")
        print(f"        台账说：{claim}")
        print(f"        实际是：{actual}")

    print()
    if gate_fail:
        print(f"❌ 门禁级不一致 {gate_fail} 条")
        return 1
    hints = sum(1 for lvl, ok, *_ in results if not ok and lvl == "提示")
    print(f"✅ 门禁级 0 条不一致" + (f"（提示级 {hints} 条差异 —— 不判死）" if hints else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
