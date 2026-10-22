#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-flyway-ledger.py —— 多模块共用一个 PG schema 时的 **Flyway 账本撞号**静态检查。

════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════
AGENTS.md §6.3「问题 1」在 2026-10-21 判定**用户域必须丢掉 Flyway**，根因是：

    `flyway_schema_history` 是 **schema 级**账本，一个 schema 只能有一本账。
    而 user-rpc 与 admin-rpc 必须连同一个库，两边的 V1/V2 版本号相同、内容不同，
    ⇒ 后启动的那个把自己的迁移判为「已应用」而**静默跳过**。

这个结论当时被 `tools/difftest/exp-drift015-flyway-flags.sh` 用真 jar + 真 PG 16
**实证证伪了所有绕法**（加 `validate-on-migrate=false` + `out-of-order=true`
不但没用，还会往账本里塞一条语义重复的记录，把 schema 永久钉在错误版本上）。

⚠️ **但同一条病根在另外三个模块原封不动地成立着**，脚本就是为此而生：
`spring.flyway.table` 从未被任何模块改过 ⇒ 三个 Flyway 实例共用**同一本账**。

════════════════════════════════════════════════════════════════════════
【本脚本查什么 / 不查什么】
════════════════════════════════════════════════════════════════════════
查：按 (数据库, schema) 把所有启用 Flyway 的模块分组，
    断言**同组内账本表名两两不同，且都不是 Flyway 默认值** `flyway_schema_history`。

不查：
  · **不启动任何服务、不连任何数据库** —— 纯静态。
  · **不验证 Flyway 是否真的按 `spring.flyway.table` 建表**。
    那一条由各模块的 `FlywayLedgerIsolationTest` 负责（见 AGENTS.md §3.3：
    「配置项写了 ≠ 框架机制生效」）。本脚本只保证**声明层**不撞。
  · **不检查迁移脚本内容的正确性**（列类型 / 索引 / NOT NULL 等由
    `check-ddl-static.py` 负责）。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
0 = 无撞号
1 = 有撞号（打印冲突组明细）
"""

import os
import re
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FLYWAY_DEFAULT_TABLE = "flyway_schema_history"


def find_config_files():
    """所有 service/**/src/main/resources/application*.y*ml。"""
    hits = []
    svc = os.path.join(ROOT, "service")
    for dirpath, _dirnames, filenames in os.walk(svc):
        if os.path.basename(dirpath) != "resources":
            continue
        if "main" not in dirpath.split(os.sep):
            continue
        for fn in filenames:
            if re.fullmatch(r"application.*\.ya?ml", fn):
                hits.append(os.path.join(dirpath, fn))
    return sorted(hits)


def module_name(path):
    """service/users/user/user-rpc/src/main/resources/application.yml -> users/user/user-rpc"""
    rel = os.path.relpath(path, ROOT)
    return rel.split(os.sep + "src" + os.sep)[0].replace(os.sep, "/")


def db_name_of(url):
    """jdbc:postgresql://host:5432/first_db?... -> first_db"""
    if not url:
        return None
    m = re.search(r"jdbc:postgresql://[^/]*/([^?;/]+)", str(url))
    return m.group(1) if m else None


def load(path):
    with open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh) or {}


def inspect_module(path):
    cfg = load(path)
    spring = cfg.get("spring") or {}
    flyway = spring.get("flyway") or {}
    if not flyway or flyway.get("enabled") is False:
        return None

    ds = spring.get("datasource") or {}
    db = db_name_of(ds.get("url"))
    schemas = flyway.get("schemas")
    if isinstance(schemas, list):
        schemas = ",".join(str(s) for s in schemas)
    schemas = schemas or "public"

    migration_dir = os.path.join(os.path.dirname(path), "db", "migration")
    migrations = []
    if os.path.isdir(migration_dir):
        migrations = sorted(f for f in os.listdir(migration_dir) if f.endswith(".sql"))

    return {
        "module": module_name(path),
        "config": os.path.relpath(path, ROOT),
        "db": db or "<未从 url 解出>",
        "schema": schemas,
        "table": flyway.get("table") or FLYWAY_DEFAULT_TABLE,
        "table_explicit": bool(flyway.get("table")),
        "migrations": migrations,
        "versions": sorted(
            m.split("__", 1)[0] for m in migrations if "__" in m
        ),
    }


def main():
    mods = [m for m in (inspect_module(p) for p in find_config_files()) if m]

    print("=" * 78)
    print("Flyway 账本隔离检查")
    print("=" * 78)
    if not mods:
        print("\n未发现启用 Flyway 的模块。\n")
        return 0

    print(f"\n扫描到 {len(mods)} 个启用 Flyway 的模块：\n")
    hdr = f"  {'模块':<24} {'库':<12} {'schema':<10} {'账本表':<34} 迁移"
    print(hdr)
    print("  " + "-" * (len(hdr) - 2))
    for m in sorted(mods, key=lambda x: (x["db"], x["schema"], x["module"])):
        mark = "" if m["table_explicit"] else "  ← 未显式声明"
        print(f"  {m['module']:<24} {m['db']:<12} {m['schema']:<10} "
              f"{m['table']:<34} {','.join(m['versions']) or '无'}{mark}")

    # 按 (库, schema) 分组
    groups = {}
    for m in mods:
        groups.setdefault((m["db"], m["schema"]), []).append(m)

    violations = []
    print()
    for (db, schema), members in sorted(groups.items()):
        if len(members) < 2:
            continue
        tables = {}
        for m in members:
            tables.setdefault(m["table"], []).append(m["module"])
        for table, owners in sorted(tables.items()):
            if len(owners) > 1:
                violations.append((db, schema, table, owners))

    print("=" * 78)
    if not violations:
        print("✅ 无撞号：每个 (库, schema) 内账本表名两两不同。")
        for (db, schema), members in sorted(groups.items()):
            if len(members) > 1:
                print(f"   · {db}/{schema}: "
                      + ", ".join(f"{m['module']}→{m['table']}" for m in members))
        print("=" * 78)
        print()
        return 0

    print("❌ 检出账本撞号 —— 后启动的模块会把自己的迁移静默跳过，或 checksum 失败：\n")
    for db, schema, table, owners in violations:
        print(f"  🔴 库={db}  schema={schema}")
        print(f"     共用账本 `{table}` 的模块：{', '.join(owners)}")
        # 把撞号双方的迁移版本号也打出来，便于判断严重程度
        for (d, s), members in groups.items():
            if (d, s) != (db, schema):
                continue
            for m in members:
                if m["module"] in owners:
                    print(f"       · {m['module']:<22} {','.join(m['versions']) or '无'}")
        print()
    print("  修法（二选一）：")
    print("   1. 给每个模块加 `spring.flyway.table: flyway_schema_history_<模块>`（推荐）")
    print("   2. 排全局唯一版本号段（需改迁移文件名，且 §6.3 已被推翻过一次）")
    print("   ⛔ 不要用 validate-on-migrate=false + out-of-order=true —— 已被")
    print("      tools/difftest/exp-drift015-flyway-flags.sh 实测证伪（会让账本更脏）。")
    print("=" * 78)
    print()
    return 1


if __name__ == "__main__":
    sys.exit(main())