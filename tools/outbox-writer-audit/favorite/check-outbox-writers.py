#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
favorite 域「outbox 写入方」盘点机检（V7 上线前置）。

背景
----
`favorite_fact_outbox` 的 `created_at` / `updated_at` 两列在 **V7** 里被去掉了
`DEFAULT now()`（对齐 Go 侧：GORM 的 `autoCreateTime` 建的列**没有** DEFAULT）。

⇒ **任何直接写这张表、却没绑这两列的代码，会立刻 `NOT NULL` 违例**（硬失败，不静默）。
⇒ 领域内的写入方已经全部改绑（生产代码 `insertFact` + 6 处测试裸 INSERT），
   但**域外**的写入方（人工 SQL / 报表 / 另一个服务 / 数据修复脚本 / 别的域的 mapper）
   **无法从 favorite 域内部看见** ⇒ 只能靠盘点。

本工具做什么
------------
全仓扫出**所有** `INSERT INTO favorite_fact_outbox` 的站点，然后逐条判定：

  ① **归属**：是不是在 `favorite-rpc` 模块内？
     · 不在 ⇒ 🔴 **域外写入方** ⇒ 报告 + exit 1（上线前必须确认/改造）
  ② **绑列**：同一语句里有没有显式绑 `created_at` 与 `updated_at`？
     · 没绑 ⇒ 🔴 **V7 之后会立刻 NOT NULL 违例** ⇒ exit 1

判定口径
--------
  · 只管 **INSERT** —— 只有 INSERT 会插入新行，才会撞 NOT NULL；
    UPDATE 不受 DEFAULT 影响（UPDATE 不产生新行）。
  · `UPDATE` / `DELETE` 站点**只登记不判失败**（它们不触发 NOT NULL 违例），
    但会出现在报告里 ⇒ 供人工确认。
  · **DDL 迁移脚本**（`db/migration/**`）与**探针工具**（`tools/**`）不算写入方
    —— 前者是建表，后者是探针设施（seed.sql 由本探针自己用）。

用法
----
    python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py
    python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py --json
    python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py --repo-root /path/to/RAG

退出码：0 = 没有「域外写入方」且所有 INSERT 都绑了两列；1 = 有问题；2 = 工具自身出错。
"""
import argparse
import json
import os
import re
import sys
from collections import defaultdict

TABLE = "favorite_fact_outbox"
WRITE_SQL = re.compile(
    r"\b(INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+[\"`]?" + TABLE + r"\b",
    re.IGNORECASE,
)
# 需要显式绑定的两列（V7 之后没有 DEFAULT ⇒ 不绑就硬失败）
REQUIRED_COLUMNS = ("created_at", "updated_at")

# 扫描的文件后缀
SCAN_SUFFIXES = (".java", ".xml", ".sql", ".kt", ".groovy", ".py", ".sh", ".js", ".ts")

# 不算「写入方」的目录（相对仓库根）
EXCLUDED_DIR_PREFIXES = (
    "target/",                 # 构建产物
    "tools/",                  # 探针设施（seed.sql 是探针自己用的）
    "docs/",                   # 文档
    ".git/",
    "node_modules/",
)

# DDL 迁移脚本：建表 / 改表，不是写入方
DDL_MARKERS = ("CREATE TABLE", "ALTER TABLE", "DROP TABLE", "CREATE INDEX", "CREATE TRIGGER")


def is_excluded(rel):
    rel = rel.replace(os.sep, "/")
    return any(rel.startswith(p) for p in EXCLUDED_DIR_PREFIXES)


def is_ddl(text):
    up = text.upper()
    return any(m in up for m in DDL_MARKERS)


def in_favorite_rpc(rel):
    """是否属于 favorite-rpc 模块（本域）。"""
    rel = rel.replace(os.sep, "/")
    return rel.startswith("service/favorite/favorite-rpc/")


def module_of(rel):
    parts = rel.replace(os.sep, "/").split("/")
    if len(parts) >= 3 and parts[0] == "service":
        return "/".join(parts[:3])
    return parts[0] if parts else "?"


def kind_of(rel):
    rel = rel.replace(os.sep, "/")
    if "/src/test/" in rel:
        return "测试"
    if "/src/main/" in rel:
        return "生产代码"
    return "其它"


def find_sites(root):
    """扫出所有对 TABLE 的写操作站点。"""
    sites = []
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in (".git", "target", "node_modules", "tools", "docs")]
        for fn in files:
            if not fn.endswith(SCAN_SUFFIXES):
                continue
            path = os.path.join(base, fn)
            rel = os.path.relpath(path, root)
            if is_excluded(rel):
                continue
            try:
                text = open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            if TABLE not in text:
                continue
            lines = text.split("\n")
            for i, line in enumerate(lines, 1):
                m = WRITE_SQL.search(line)
                if not m:
                    continue
                op = m.group(1).upper()
                # 收集该语句的完整文本（SQL 常量可能跨行）：往后看 25 行
                stmt_lines = lines[i - 1: i + 25]
                stmt = "\n".join(stmt_lines)
                sites.append(dict(
                    file=rel.replace(os.sep, "/"),
                    line=i,
                    op="INSERT" if op.startswith("INSERT") else ("UPDATE" if op == "UPDATE" else "DELETE"),
                    stmt=stmt,
                    is_ddl=is_ddl(stmt),
                    module=module_of(rel),
                    kind=kind_of(rel),
                    in_favorite_rpc=in_favorite_rpc(rel),
                ))
    return sites


def binds_required_columns(stmt):
    """该 INSERT 语句有没有显式绑定 created_at / updated_at。

    ⚠️ 只看**列清单**部分（INSERT INTO t ( ... ) VALUES）—— 否则 WHERE 里的同名列会误判。
    """
    m = re.search(r"INSERT\s+INTO\s+[\"`]?" + TABLE + r"[\"`]?\s*\(([^)]*)\)",
                  stmt, re.IGNORECASE | re.DOTALL)
    if not m:
        return None  # 形态特殊（INSERT ... SELECT 之类）⇒ 交人工
    cols = {c.strip().strip('"`').lower() for c in m.group(1).split(",") if c.strip()}
    return all(c in cols for c in REQUIRED_COLUMNS)


def main():
    ap = argparse.ArgumentParser(description="favorite 域 outbox 写入方盘点")
    ap.add_argument("--repo-root", default=os.getcwd())
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    root = os.path.abspath(args.repo_root)
    if not os.path.isdir(root):
        print(f"❌ 仓库根不存在：{root}", file=sys.stderr)
        return 2

    sites = find_sites(root)
    inserts = [s for s in sites if s["op"] == "INSERT" and not s["is_ddl"]]
    others = [s for s in sites if s["op"] != "INSERT" and not s["is_ddl"]]
    ddl = [s for s in sites if s["is_ddl"]]

    problems = []
    for s in inserts:
        if not s["in_favorite_rpc"]:
            problems.append(dict(
                kind="out-of-domain-writer",
                file=s["file"], line=s["line"],
                detail=f"**域外写入方**（{s['module']} · {s['kind']}）⇒ V7 之后若不绑 "
                       f"{' / '.join(REQUIRED_COLUMNS)} ⇒ 立刻 NOT NULL 违例"))
        else:
            bound = binds_required_columns(s["stmt"])
            if bound is False:
                problems.append(dict(
                    kind="missing-time-columns",
                    file=s["file"], line=s["line"],
                    detail=f"INSERT 没显式绑 {' / '.join(REQUIRED_COLUMNS)} ⇒ "
                           f"V7 之后立刻 NOT NULL 违例"))
            elif bound is None:
                problems.append(dict(
                    kind="unparsed-statement",
                    file=s["file"], line=s["line"],
                    detail="无法解析列清单（形态特殊）⇒ 需人工确认是否绑了两列"))

    result = dict(
        repo_root=root,
        table=TABLE,
        insert_sites=len(inserts),
        other_write_sites=len(others),
        ddl_mentions=len(ddl),
        inserts=[dict(file=s["file"], line=s["line"], module=s["module"], kind=s["kind"],
                      in_favorite_rpc=s["in_favorite_rpc"],
                      binds_time_columns=binds_required_columns(s["stmt"]))
                 for s in inserts],
        other_writes=[dict(file=s["file"], line=s["line"], op=s["op"], module=s["module"])
                      for s in others],
        problems=problems,
    )

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if problems else 0

    print(f"favorite 域 outbox 写入方盘点 · {root}")
    print(f"  表：{TABLE}")
    print(f"  INSERT 站点：{len(inserts)} 个 / 其它写操作（UPDATE / DELETE）：{len(others)} 个 "
          f"/ DDL 提及：{len(ddl)} 个（不算写入方）")
    print()
    print("  INSERT 站点明细：")
    for s in inserts:
        flag = "✅" if s["in_favorite_rpc"] else "🔴"
        bind = binds_required_columns(s["stmt"])
        bind_txt = {True: "已绑两列", False: "❌ 没绑两列", None: "⚠️ 形态需人工确认"}[bind]
        print(f"    {flag} {s['file']}:{s['line']}  [{s['module']} · {s['kind']}]  {bind_txt}")
    if others:
        print()
        print("  其它写操作（只登记，不判失败 —— UPDATE 不触发 NOT NULL 违例）：")
        for s in others:
            print(f"    · {s['file']}:{s['line']}  [{s['op']} · {s['module']}]")
    print()
    if problems:
        print(f"❌ 发现 {len(problems)} 个问题：")
        for p in problems:
            print(f"   [{p['kind']}] {p['file']}:{p['line']} —— {p['detail']}")
        print()
        print("   ⇒ V7 上线前必须清零（见 docs/DRIFT/favorite/rpc.md §13.6）。")
        return 1
    print("✅ 没有域外写入方，且所有 INSERT 都显式绑了 created_at / updated_at")
    print("   ⇒ V7 的破坏性变更在本仓范围内已闭合；**域外**（人工 SQL / 报表 / 另一个服务 /")
    print("      数据修复脚本）**不在本工具的可见范围** ⇒ 上线前仍需人工确认一次。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
