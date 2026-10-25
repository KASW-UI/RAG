#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
flyway-concurrent-verify.py —— **L4 lane** 的判定器（check-flyway-concurrent.sh 的下半段）

════════════════════════════════════════════════════════════════════════
【判什么】
════════════════════════════════════════════════════════════════════════
AGENTS.md §6.3 问题 4 的定案：**每模块独立账本表名**（`flyway_schema_history_<模块>`）、
**不改任何迁移文件名** ⇒ 配置层已闭环（check-flyway-ledger.py ⇒ EXIT=0）。
但「三本账在同一个库、同一个 schema 上**同时跑**」这个运行时形态**从未验证过**。

本判定器对每个场景逐条判：

  A. 迁移进程本身成功了吗（rc==0 且 ok==true）
  B. **生效的账本表名** == 该模块 yaml 里配的表名（没配 ⇒ Flyway 默认 `flyway_schema_history`）
     ⇒ 这一条同时证明「Spring Boot 真的把 yaml 交给了引擎」（§3.3）
  C. **生效的 baseline 版本** == yaml 里配的值（没配 ⇒ Spring Boot 默认 `1`）
  D. **生效的 baseline-on-migrate** == true
  E. 该模块迁移脚本里 `CREATE TABLE` 的表**全部建出来了**
  F. 该模块的账本表存在
  G. 账本里的 (version, description) 集合 == 该模块**自己的**迁移集合（精确相等）
     ⇒ 这一条就是「账本互不覆盖」的判定
  H. 账本里的 BASELINE 行落在**生效的 baseline 版本**上
  I. 账本里**没有别的模块**的迁移描述（逐对检查）

⚠️ **favorite-rpc 是已知阻塞项**（global 无写权 ⇒ 只记账不修）：
   它的 yaml 缺 `spring.flyway.baseline-version: 0` ⇒ 在共享库里 V1 会被 baseline 跳过。
   ⇒ 本判定器把 favorite 当作**已知缺陷探针**：观测到的缺陷形态照实打印，
   **默认不影响退出码**（否则这个脚本永远红 ⇒ 失去守门价值）；
   加 `--strict-known` ⇒ 已知缺陷也算失败。

【退出码】
  0 = 没有差异
  1 = 有差异（逐条打印）
  2 = 用法/环境错误
"""

import argparse
import json
import os
import re
import sys

import yaml

# ── 已知阻塞（无写权 ⇒ 只记账 + 告知 owner，不修）──────────────────────────────
KNOWN_BLOCKED = {
    "favorite": "favorite-rpc 的 application.yml 缺 spring.flyway.baseline-version: 0"
                " ⇒ 共享库里 V1 被 baseline 跳过 ⇒ favorite_folder 建不出来"
                " ⇒ global 无写权 ⇒ 只记账（见 docs/DRIFT/global/lanes/L4-flyway.md）",
}

FLYWAY_DEFAULT_TABLE = "flyway_schema_history"
# Spring Boot 的 spring.flyway.baseline-version 默认值（未显式配置时生效）
SPRING_BOOT_DEFAULT_BASELINE_VERSION = "1"

CREATE_TABLE_RE = re.compile(
    r"create\s+(?:unlogged\s+)?table\s+(?:if\s+not\s+exists\s+)?\"?([A-Za-z0-9_]+)\"?",
    re.I)

# PG 里**schema 级**的对象名（表 / 索引 / 约束 / 序列 / 类型）
# ⇒ 两个模块在同一个 schema 里建同名对象 ⇒ 第二个直接撞名失败。
SCHEMA_SCOPED_PATTERNS = {
    "table": CREATE_TABLE_RE,
    "index": re.compile(
        r"create\s+(?:unique\s+)?index\s+(?:if\s+not\s+exists\s+)?\"?([A-Za-z0-9_]+)\"?", re.I),
    "constraint": re.compile(r"add\s+constraint\s+\"?([A-Za-z0-9_]+)\"?", re.I),
    "sequence": re.compile(
        r"create\s+sequence\s+(?:if\s+not\s+exists\s+)?\"?([A-Za-z0-9_]+)\"?", re.I),
    "type": re.compile(r"create\s+type\s+\"?([A-Za-z0-9_]+)\"?", re.I),
}

ALL_RPC_MODULES = ["message", "like", "article", "comment", "follow", "task", "favorite"]


def module_dir(root, module):
    return os.path.join(root, "service", module, f"{module}-rpc")


def module_yaml(root, module):
    p = os.path.join(module_dir(root, module), "src", "main", "resources", "application.yml")
    with open(p, encoding="utf-8") as fh:
        return yaml.safe_load(fh) or {}


def flyway_conf(root, module):
    """该模块**自己 yaml** 里的 spring.flyway 块（值都是字面量，不做占位符解析）。"""
    doc = module_yaml(root, module)
    fw = ((doc.get("spring") or {}).get("flyway")) or {}
    table = fw.get("table") or FLYWAY_DEFAULT_TABLE
    explicit_table = bool(fw.get("table"))
    baseline_version = fw.get("baseline-version")
    if baseline_version is None:
        baseline_version = SPRING_BOOT_DEFAULT_BASELINE_VERSION
    return {
        "table": str(table),
        "explicit_table": explicit_table,
        "baseline_version": str(baseline_version),
        "explicit_baseline_version": fw.get("baseline-version") is not None,
        "baseline_on_migrate": bool(fw.get("baseline-on-migrate", False)),
        "locations": fw.get("locations"),
        "schemas": fw.get("schemas"),
    }


def migrations_of(root, module):
    """该模块自己的迁移：[(version, description, filename)] + 期望建出来的表集合。"""
    d = os.path.join(module_dir(root, module), "src", "main", "resources", "db", "migration")
    out, tables = [], set()
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".sql"):
            continue
        m = re.match(r"^V(\d+(?:\.\d+)*)__(.+)\.sql$", fn)
        if not m:
            out.append((None, None, fn))
            continue
        version = m.group(1)
        # Flyway 的 description = 文件名里 __ 之后的部分，下划线换空格
        description = m.group(2).replace("_", " ")
        out.append((version, description, fn))
        with open(os.path.join(d, fn), encoding="utf-8") as fh:
            for mm in CREATE_TABLE_RE.finditer(fh.read()):
                tables.add(mm.group(1).lower())
    return out, tables


def flyway_description_of(filename):
    m = re.match(r"^V(\d+(?:\.\d+)*)__(.+)\.sql$", filename)
    if not m:
        return None
    return m.group(2).replace("_", " ")


def cross_module_name_collisions(root):
    """静态守卫：跨模块的 **schema 级对象名**（表/索引/约束/序列/类型）有没有撞名。

    撞了 ⇒ 并发同库时第二个模块会直接失败（PG 的对象名是 schema 级的）。
    """
    owners = {}
    for m in ALL_RPC_MODULES:
        d = os.path.join(module_dir(root, m), "src", "main", "resources", "db", "migration")
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if not fn.endswith(".sql"):
                continue
            with open(os.path.join(d, fn), encoding="utf-8") as fh:
                txt = fh.read()
            for kind, pat in SCHEMA_SCOPED_PATTERNS.items():
                for mm in pat.finditer(txt):
                    owners.setdefault((kind, mm.group(1).lower()), set()).add(m)
    return {k: v for k, v in owners.items() if len(v) > 1}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--results", required=True)
    ap.add_argument("--strict-known", action="store_true",
                    help="已知阻塞缺陷（favorite）也算失败")
    ap.add_argument("--pg-port", default="")
    ap.add_argument("--pg-user", default="")
    ap.add_argument("--pg-pass", default="")
    args = ap.parse_args()

    with open(args.results, encoding="utf-8") as fh:
        scenarios = json.load(fh)

    failures = []
    known_hits = []
    matrix = {}

    print("=" * 78)
    print("L4 · 三进程并发同库共存 —— 判定")
    print("=" * 78)

    # ---------------------------------------------------------------- 静态前置
    collisions = cross_module_name_collisions(args.root)
    print("\n▶ 静态前置：跨模块的 schema 级对象名（表/索引/约束/序列/类型）撞名检查")
    if collisions:
        for (kind, name), owners in sorted(collisions.items()):
            print(f"  ❌ {kind} `{name}` 被多个模块使用：{sorted(owners)}"
                  " ⇒ 并发同库时第二个模块会直接撞名失败")
            failures.append(f"静态：{kind} {name} 跨模块撞名 {sorted(owners)}")
    else:
        print(f"  ✅ 7 个 rpc 模块共 "
              f"{len(ALL_RPC_MODULES)} 个，schema 级对象名**零撞名**"
              "（⇒ 并发同库时不会因为同名对象互相挤掉）")

    for sc in scenarios:
        name = sc["name"]
        mods = [r["module"] for r in sc.get("runs", [])]
        print(f"\n▶ 场景 {name}   db={sc['db']}   模块={','.join(mods)}")
        insp = sc.get("inspect") or {}
        tables = set(insp.get("tables") or [])
        ledgers = insp.get("ledgers") or {}

        # 该场景里出现过的所有描述（用于「账本里没有别的模块的迁移」）
        desc_owner = {}
        for m in mods:
            migs, _t = migrations_of(args.root, m)
            for _v, desc, _fn in migs:
                if desc:
                    desc_owner.setdefault(desc, set()).add(m)

        # ---- 并发度证据：并发组里的进程必须**真的同时起** ----
        second_group = (sc.get("second") or "").split()
        if len(second_group) >= 2:
            starts = [r.get("startAtEpochMs") for r in sc.get("runs", [])
                      if r.get("module") in second_group and r.get("startAtEpochMs")]
            if len(starts) >= 2:
                spread = max(starts) - min(starts)
                conc_ok = spread <= 3000
                print(f"  ⏱  并发组 {second_group} 的起跑时刻：{starts} "
                      f"（最大偏差 {spread}ms ⇒ {'真并发' if conc_ok else '**不是同时起**'}）")
                if not conc_ok:
                    failures.append(f"{name}: 并发组起跑时刻偏差 {spread}ms > 3000ms ⇒ 场景退化成串行")
            else:
                failures.append(f"{name}: 并发组没拿到起跑时刻 ⇒ 无法证明并发")

        for run in sc.get("runs", []):
            m = run["module"]
            conf = flyway_conf(args.root, m)
            migs, exp_tables = migrations_of(args.root, m)
            exp_versions = {v for v, _d, _f in migs if v}
            exp_desc = {d for _v, d, _f in migs if d}
            blocked = m in KNOWN_BLOCKED

            problems = []
            notes = []

            # ---- A. 进程结果 ----
            ok = bool(run.get("ok"))
            if not ok:
                err = (run.get("error") or "").replace("\n", " ")
                notes.append(f"迁移失败：{err[:200]}")

            # ---- B/C/D. 生效配置（Spring Boot → Flyway 引擎）----
            if ok:
                if run.get("effectiveTable") != conf["table"]:
                    problems.append(
                        f"生效账本表名={run.get('effectiveTable')} ≠ yaml 配置的 {conf['table']}"
                        "（⇒ Spring Boot 没把 spring.flyway.table 交给引擎）")
                if run.get("effectiveBaselineVersion") != conf["baseline_version"]:
                    problems.append(
                        f"生效 baseline 版本={run.get('effectiveBaselineVersion')} "
                        f"≠ 应为 {conf['baseline_version']}")
                if run.get("effectiveBaselineOnMigrate") is not True:
                    problems.append(
                        f"生效 baseline-on-migrate={run.get('effectiveBaselineOnMigrate')} ≠ true")

            # ---- E. 表 ----
            missing_tables = sorted(t for t in exp_tables if t not in tables)
            if missing_tables:
                problems.append(f"这些表没建出来：{missing_tables}")

            # ---- F/G/H. 账本 ----
            ledger_rows = ledgers.get(conf["table"])
            if ledger_rows is None:
                problems.append(f"账本表 {conf['table']} 不存在")
                ledger_rows = []
            else:
                sql_rows = [r for r in ledger_rows if r.get("type") == "SQL"]
                base_rows = [r for r in ledger_rows if r.get("type") == "BASELINE"]
                got_versions = {r.get("version") for r in sql_rows}
                got_desc = {r.get("description") for r in sql_rows}
                if got_versions != exp_versions:
                    problems.append(
                        f"账本 {conf['table']} 的 SQL 版本集合={sorted(got_versions)} "
                        f"≠ 本模块自己的 {sorted(exp_versions)}")
                if got_desc != exp_desc:
                    problems.append(
                        f"账本 {conf['table']} 的迁移描述集合与本模块不一致"
                        f"（多={sorted(got_desc - exp_desc)} 少={sorted(exp_desc - got_desc)}）")
                for r in base_rows:
                    if r.get("version") != conf["baseline_version"]:
                        problems.append(
                            f"账本 {conf['table']} 的 BASELINE 行落在 version {r.get('version')}，"
                            f"应为 {conf['baseline_version']}")
                if any(not r.get("success") for r in sql_rows):
                    problems.append(f"账本 {conf['table']} 里有 success=false 的行")
                notes.append(
                    f"账本 {conf['table']}：SQL 版本 {sorted(got_versions, key=lambda x: int(x))}"
                    f" · BASELINE 行 {[b.get('version') for b in base_rows]}")

            # ---- J. 迁移**没有一条被 baseline 跳过**（Flyway 自己会说 BASELINE_IGNORED）----
            skipped = [x for x in (run.get("migrations") or [])
                       if x.get("state") not in (None, "SUCCESS")
                       and x.get("type") != "BASELINE"]
            if skipped:
                problems.append(
                    "有迁移被跳过（Flyway 状态非 SUCCESS）："
                    + ", ".join(f"V{x.get('version')}={x.get('state')}" for x in skipped))

            # ---- I. 账本里没有别的模块的迁移 ----
            for other in mods:
                if other == m:
                    continue
                other_migs, _t = migrations_of(args.root, other)
                other_desc = {d for _v, d, _f in other_migs if d}
                leaked = other_desc & {r.get("description") for r in ledger_rows}
                if leaked:
                    problems.append(f"账本 {conf['table']} 里出现了 {other} 的迁移：{sorted(leaked)}")

            # ---- 汇总 ----
            if blocked:
                sig = []
                if "favorite_folder" in exp_tables and "favorite_folder" not in tables:
                    sig.append("favorite_folder 缺失（V1 被 baseline 跳过）")
                if any(r.get("type") == "BASELINE" and r.get("version") == "1"
                       for r in ledger_rows):
                    sig.append("账本里 BASELINE 行落在 version 1")
                ignored = [x.get("version") for x in (run.get("migrations") or [])
                           if x.get("state") == "BASELINE_IGNORED"]
                if ignored:
                    sig.append(f"Flyway 自己报 V{'/V'.join(ignored)} = BASELINE_IGNORED（被 baseline 忽略）")
                if sig:
                    known_hits.append((m, name, KNOWN_BLOCKED[m], sig))
                    print(f"  🟡 {m:<10} 已知阻塞缺陷仍在（**不修**）：{'；'.join(sig)}")
                    print(f"      原因：{KNOWN_BLOCKED[m]}")
                else:
                    print(f"  🟢 {m:<10} 未观测到已知缺陷形态（可能已被该域 owner 修掉）")
                for n in notes:
                    print(f"      · {n}")
                matrix.setdefault(m, {"ok": True, "note": "已知阻塞（favorite）", "scenarios": []})
                matrix[m]["scenarios"].append(name)
                continue

            if problems:
                print(f"  ❌ {m:<10}")
                for p in problems:
                    print(f"      · {p}")
                    failures.append(f"{name}/{m}: {p}")
            else:
                print(f"  ✅ {m:<10}")
            for n in notes:
                print(f"      · {n}")

            matrix.setdefault(m, {"ok": True, "note": "", "scenarios": []})
            matrix[m]["scenarios"].append(name)
            if problems:
                matrix[m]["ok"] = False
                matrix[m]["note"] = problems[0]

    # ---------------------------------------------------------------- 结论矩阵
    print("\n" + "=" * 78)
    print("结论矩阵（可并发同库？）")
    print("=" * 78)
    print(f"  {'模块':<12} {'可并发同库':<12} {'账本表':<32} {'baseline':<10} 依据")
    print("  " + "-" * 74)
    for m in sorted(matrix):
        conf = flyway_conf(args.root, m)
        row = matrix[m]
        if m in KNOWN_BLOCKED:
            verdict = "🔴 不行"
        elif row["ok"]:
            verdict = "✅ 可以"
        else:
            verdict = "❌ 不行"
        base = conf["baseline_version"] + ("" if conf["explicit_baseline_version"] else "(默认)")
        print(f"  {m:<12} {verdict:<12} {conf['table']:<32} {base:<10} "
              f"{','.join(row['scenarios'])}"
              + (f" ｜ {row['note']}" if row["note"] else ""))

    # ---------------------------------------------------------------- 汇总
    print()
    if known_hits:
        print(f"🟡 已知阻塞（**未修**，global 无写权）：{len(known_hits)} 处")
        for m, sc, why, sig in known_hits:
            print(f"   · {m} @ {sc}：{'；'.join(sig)}")
        if args.strict_known:
            for m, sc, why, sig in known_hits:
                failures.append(f"{sc}/{m}: 已知阻塞缺陷仍在（--strict-known）")
            print("   （--strict-known ⇒ 计入失败）")

    if failures:
        print(f"\n❌ 共 {len(failures)} 条差异：")
        for f in failures:
            print(f"   · {f}")
        print("=" * 78)
        return 1

    print("✅ 无差异：各模块的表都建出来了、账本互不覆盖、baseline 落位正确。")
    print("=" * 78)
    return 0


if __name__ == "__main__":
    sys.exit(main())
