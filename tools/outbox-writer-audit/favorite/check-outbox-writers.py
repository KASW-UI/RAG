#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
favorite 域「表写入方」盘点机检（V6 / V7 上线前置）。

背景
----
favorite 域有两张表在 **RAG 侧比 Go 侧更严**，任何**第二个写入方**都必须知道这件事：

  ① `favorite_fact_outbox` —— **V7** 去掉了 `created_at` / `updated_at` 的 `DEFAULT now()`
     （对齐 Go 侧：GORM 的 `autoCreateTime` 建的列**没有** DEFAULT）。
     ⇒ 直接写这张表、却没绑这两列 ⇒ **立刻 NOT NULL 违例**（硬失败，不静默）。

  ② `favorite_item` —— **V6** 多了一道 `target_revision scope` CHECK
     （`CHECK (target_revision IS NULL OR (target_type = 'article' AND target_revision <> ''))`）。
     ⇒ 写入方若写「非法修订号」⇒ **RAG 拒（SQLSTATE 23514）/ Go 接受** ⇒ 两侧分叉。
     ⇒ 这条**不需要改 RAG**（RAG 更安全），但**写入方必须知道**。

领域内的写入方已经全部适配（生产代码 + 测试裸 INSERT）。
但**域外**的写入方（人工 SQL / 报表 / 另一个服务 / 数据修复脚本 / 别的域的 mapper）
**无法从 favorite 域内部看见** ⇒ 只能靠盘点。

本工具做什么
------------
全仓扫出**所有**对这两张表的写操作站点，然后逐条判定：

  ① **归属**：是不是在 `favorite-rpc` 模块内？
     · 不在 ⇒ 🔴 **域外写入方** ⇒ 报告 + exit 1（上线前必须确认/改造）
  ② **绑列**（仅 outbox）：同一 INSERT 语句里有没有显式绑 `created_at` 与 `updated_at`？
     · 没绑 ⇒ 🔴 **V7 之后会立刻 NOT NULL 违例** ⇒ exit 1

🔴🔴🔴 本工具最容易被误用的地方：**「EXIT=0」不等于「可以放心上线」**
--------------------------------------------------------------------
字面量 SQL 扫描**看不见 ORM 写入路径**（MyBatis-Plus 的 `mapper.insert(entity)` 不含
`INSERT INTO` 字面量）⇒ 那样会出现**假绿**：某张表突然多了一个 ORM 写入方，工具却报 0 个问题。

⇒ 因此本工具**自己判定**每张表「有没有 ORM 写入路径」：
  · 找到 `interface XxxMapper extends BaseMapper<该表实体>` ⇒ **有** ORM 路径
    ⇒ 额外扫 `.insert(` / `.save(` / `.insertBatch(` 等调用点，并逐个登记；
  · 找不到 ⇒ 该表**只有显式 SQL 写入路径** ⇒ 字面量扫描即**全覆盖**（结论写进报告）。
⇒ 这条「有没有 ORM 路径」是**机器判定**的，不是人假设的。

判定口径
--------
  · 只管 **INSERT** —— 只有 INSERT 会插入新行，才会撞 NOT NULL；
    UPDATE 不受 DEFAULT 影响（UPDATE 不产生新行）⇒ **只登记不判失败**。
  · **DDL 迁移脚本**（`db/migration/**`）不是写入方。
  · **`tools/**` 下的写入**不是**部署产物（探针设施 / 运维实验脚本）⇒ 登记但不判失败。

用法
----
    python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py
    python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py --json
    python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py --repo-root /path/to/RAG

退出码：0 = 没有「域外写入方」且所有 INSERT 都绑了两列；1 = 有问题；2 = 工具自身出错。

人工确认清单 ⇒ `tools/outbox-writer-audit/favorite/CHECKLIST.md`
"""
import argparse
import json
import os
import re
import sys
from collections import defaultdict

# ── 表登记表：每张表一张「卡」───────────────────────────────────────────────
TABLES = {
    "favorite_fact_outbox": {
        # 本域生产代码唯一归属模块
        "domain_module": "service/favorite/favorite-rpc",
        # V7 之后没有 DEFAULT ⇒ INSERT 必须显式绑定这两列
        "required_columns": ("created_at", "updated_at"),
        "why": "V7 去掉了 DEFAULT now() ⇒ 不绑这两列 ⇒ 立刻 NOT NULL 违例（硬失败）",
    },
    "favorite_item": {
        "domain_module": "service/favorite/favorite-rpc",
        # 没有 NOT NULL 类问题；风险是 V6 的额外 CHECK
        "required_columns": (),
        "why": "V6 多了一道 target_revision scope CHECK（DEV-FAV-RPC-007）⇒ "
               "第二个写入方写「非法修订号」时 RAG 拒(23514) / Go 接受 ⇒ 分叉"
               "（RAG 更严，不需要改 RAG，但写入方必须知道）",
    },
}

# 扫描的文件后缀
SCAN_SUFFIXES = (".java", ".xml", ".sql", ".kt", ".groovy", ".py", ".sh", ".js", ".ts")

# 构建产物 / 版本库 / 依赖目录：不是写入方
EXCLUDED_DIR_PREFIXES = (
    "target/",                 # 构建产物
    ".git/",
    "node_modules/",
)
# 文档：不是写入方
EXCLUDED_DIR_NAMES = ("docs",)
# 探针设施：登记但不判失败（不是部署产物）
PROBE_DIR_PREFIX = "tools/"

# DDL 迁移脚本：建表 / 改表，不是写入方
DDL_MARKERS = ("CREATE TABLE", "ALTER TABLE", "DROP TABLE", "CREATE INDEX", "CREATE TRIGGER")

# ── ORM 路径判定用的正则 ───────────────────────────────────────────────────
RE_TABLE_NAME = re.compile(r'@TableName\(\s*(?:value\s*=\s*)?["\'`](\w+)["\'`]')
RE_MAPPER_EXTENDS = re.compile(
    r'interface\s+(\w+)\b[^{]*?extends\s+BaseMapper<\s*(\w+)\s*>', re.DOTALL)
RE_TABLE_FIELD_FILL = re.compile(r'@TableField\([^)]*?fill\s*=\s*FieldFill\.(\w+)', re.DOTALL)
RE_ORM_INSERT_CALL = re.compile(
    r'\b(\w*[Mm]apper\w*)\s*\.\s*(insert|insertBatch|insertSelective|insertOrUpdate|save|saveBatch)\s*\(')


def is_excluded(rel):
    rel = rel.replace(os.sep, "/")
    return any(rel.startswith(p) for p in EXCLUDED_DIR_PREFIXES)


def is_probe_infra(rel):
    return rel.replace(os.sep, "/").startswith(PROBE_DIR_PREFIX)


def is_ddl(text):
    up = text.upper()
    return any(m in up for m in DDL_MARKERS)


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


def in_domain(rel, table):
    """是否属于该表的本域归属模块。"""
    rel = rel.replace(os.sep, "/")
    return rel.startswith(TABLES[table]["domain_module"] + "/")


# ── 扫描 ────────────────────────────────────────────────────────────────────

def iter_source_files(root):
    """产出 (rel_path, text)。排除构建产物/文档；tools/ 保留（要登记）。"""
    out = []
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs
                   if d not in (".git", "target", "node_modules") and d not in EXCLUDED_DIR_NAMES]
        for fn in sorted(files):
            if not fn.endswith(SCAN_SUFFIXES):
                continue
            path = os.path.join(base, fn)
            rel = os.path.relpath(path, root).replace(os.sep, "/")
            if is_excluded(rel):
                continue
            try:
                text = open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            out.append((rel, text))
    return out


def find_sql_sites(files, table):
    """字面量 SQL 写站点。"""
    sites = []
    for rel, text in files:
        if table not in text:
            continue
        lines = text.split("\n")
        for i, line in enumerate(lines, 1):
            m = re.search(
                r"\b(INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+[\"`]?" + table + r"\b",
                line, re.IGNORECASE)
            if not m:
                continue
            op = m.group(1).upper()
            stmt = "\n".join(lines[i - 1: i + 25])   # SQL 常量可能跨行
            sites.append(dict(
                path="sql", file=rel, line=i,
                op="INSERT" if op.startswith("INSERT") else ("UPDATE" if op == "UPDATE" else "DELETE"),
                stmt=stmt, is_ddl=is_ddl(stmt),
                module=module_of(rel), kind=kind_of(rel),
                in_domain=in_domain(rel, table),
                probe_infra=is_probe_infra(rel),
            ))
    return sites


def find_orm_path(files, table):
    """判定该表**有没有** MyBatis-Plus ORM 写入路径，并返回相关 mapper / 实体信息。

    返回 dict：
      has_orm   —— 有没有 BaseMapper<实体> 的写入路径
      entity    —— 实体类名
      entity_file
      mappers   —— mapper 接口名列表
      autofilled_columns —— 实体里带 @TableField(fill=...) 的列名（无法从注解直接拿列名，
                            这里返回 {字段名: fill 模式}）
    """
    entity = None
    entity_file = None
    for rel, text in files:
        m = RE_TABLE_NAME.search(text)
        if m and m.group(1) == table:
            entity = re.search(r'public\s+class\s+(\w+)', text)
            entity = entity.group(1) if entity else None
            entity_file = rel
            break
    if not entity:
        return dict(has_orm=False, entity=None, entity_file=None, mappers=[],
                    autofill={})

    mappers = []
    for rel, text in files:
        for m in RE_MAPPER_EXTENDS.finditer(text):
            if m.group(2) == entity:
                mappers.append((m.group(1), rel))
    if not mappers:
        return dict(has_orm=False, entity=entity, entity_file=entity_file, mappers=[],
                    autofill={})

    # 实体里哪些字段带 @TableField(fill = ...) —— MyBatis-Plus 会自动填这些列
    autofill = {}
    if entity_file:
        text = dict(files)[entity_file]
        # 逐个字段块扫：@TableField(...) ... private <T> <name>;
        for fm in re.finditer(
                r'@TableField\(([^)]*)\)\s*(?:private|public)\s+[\w<>\[\], .]+\s+(\w+)\s*;',
                text, re.DOTALL):
            fill = RE_TABLE_FIELD_FILL.search("@TableField(" + fm.group(1) + ")")
            if fill:
                autofill[fm.group(2)] = fill.group(1)

    return dict(has_orm=True, entity=entity, entity_file=entity_file,
                mappers=[m[0] for m in mappers], autofill=autofill)


def find_orm_sites(files, mapper_names, table):
    """ORM 写入调用点（mapper.insert / save 等）。

    ⚠️ 变量名与 mapper 接口名不一定同名 ⇒ 用「变量名里含接口名（忽略大小写）」做映射；
    映射不上时**仍然登记**（宁可多报，不漏报）⇒ 标注 mapper=unknown。
    """
    sites = []
    lowered = [(m, m.lower()) for m in mapper_names]
    for rel, text in files:
        lines = text.split("\n")
        for i, line in enumerate(lines, 1):
            for m in RE_ORM_INSERT_CALL.finditer(line):
                var = m.group(1)
                matched = None
                for name, lname in lowered:
                    if lname in var.lower() or var.lower() in lname:
                        matched = name
                        break
                sites.append(dict(
                    path="orm", file=rel, line=i,
                    op="INSERT", mapper=matched or "unknown",
                    module=module_of(rel), kind=kind_of(rel),
                    in_domain=in_domain(rel, table),
                    probe_infra=is_probe_infra(rel),
                    snippet=line.strip()[:160],
                ))
    return sites


def binds_required_columns(stmt, required):
    """该 INSERT 语句有没有显式绑定 required 列。

    ⚠️ 只看**列清单**部分（INSERT INTO t ( ... ) VALUES）—— 否则 WHERE 里的同名列会误判。
    """
    if not required:
        return None
    m = re.search(r"INSERT\s+INTO\s+[\"`]?(\w+)[\"`]?\s*\(([^)]*)\)",
                  stmt, re.IGNORECASE | re.DOTALL)
    if not m:
        return None      # 形态特殊（INSERT ... SELECT 之类）⇒ 交人工
    cols = {c.strip().strip('"`').lower() for c in m.group(2).split(",") if c.strip()}
    return all(c in cols for c in required)


# ── 主流程 ──────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(description="favorite 域表写入方盘点（outbox / item）")
    ap.add_argument("--repo-root", default=os.getcwd())
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    root = os.path.abspath(args.repo_root)
    if not os.path.isdir(root):
        print(f"❌ 仓库根不存在：{root}", file=sys.stderr)
        return 2

    files = iter_source_files(root)
    problems = []
    report = {}

    for table, spec in TABLES.items():
        sql_sites = find_sql_sites(files, table)
        orm_info = find_orm_path(files, table)
        orm_sites = (find_orm_sites(files, orm_info["mappers"], table)
                     if orm_info["has_orm"] else [])

        inserts = [s for s in sql_sites if s["op"] == "INSERT" and not s["is_ddl"]]
        others = [s for s in sql_sites if s["op"] != "INSERT" and not s["is_ddl"]]
        ddl = [s for s in sql_sites if s["is_ddl"]]

        table_problems = []
        for s in inserts:
            if s["probe_infra"]:
                continue          # 探针设施不是部署产物 ⇒ 只登记
            if not s["in_domain"]:
                table_problems.append(dict(
                    kind="out-of-domain-writer",
                    table=table, file=s["file"], line=s["line"], path="sql",
                    detail=f"**域外写入方**（{s['module']} · {s['kind']}）⇒ {spec['why']}"))
            else:
                bound = binds_required_columns(s["stmt"], spec["required_columns"])
                if bound is False:
                    table_problems.append(dict(
                        kind="missing-time-columns", table=table,
                        file=s["file"], line=s["line"], path="sql",
                        detail=f"INSERT 没显式绑 {' / '.join(spec['required_columns'])} ⇒ "
                               f"V7 之后立刻 NOT NULL 违例"))
                elif bound is None and spec["required_columns"]:
                    table_problems.append(dict(
                        kind="unparsed-statement", table=table,
                        file=s["file"], line=s["line"], path="sql",
                        detail="无法解析列清单（形态特殊）⇒ 需人工确认是否绑了两列"))

        for s in orm_sites:
            if s["probe_infra"]:
                continue
            if not s["in_domain"]:
                table_problems.append(dict(
                    kind="out-of-domain-writer",
                    table=table, file=s["file"], line=s["line"], path="orm",
                    detail=f"**域外 ORM 写入方**（{s['module']} · {s['kind']} · "
                           f"mapper={s['mapper']}）⇒ {spec['why']}"))

        problems.extend(table_problems)
        report[table] = dict(
            why=spec["why"],
            required_columns=list(spec["required_columns"]),
            orm_path=orm_info,
            insert_sites=len(inserts),
            other_write_sites=len(others),
            ddl_mentions=len(ddl),
            inserts=[dict(file=s["file"], line=s["line"], module=s["module"], kind=s["kind"],
                          in_domain=s["in_domain"], probe_infra=s["probe_infra"],
                          binds_required_columns=binds_required_columns(s["stmt"], spec["required_columns"]))
                     for s in inserts],
            orm_sites=[dict(file=s["file"], line=s["line"], module=s["module"], kind=s["kind"],
                            in_domain=s["in_domain"], probe_infra=s["probe_infra"],
                            mapper=s["mapper"], snippet=s["snippet"])
                       for s in orm_sites],
            other_writes=[dict(file=s["file"], line=s["line"], op=s["op"], module=s["module"])
                          for s in others],
            problems=table_problems,
        )

    result = dict(
        repo_root=root,
        scanned_files=len(files),
        tables=report,
        problems=problems,
    )

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if problems else 0

    print(f"favorite 域表写入方盘点 · {root}")
    print(f"  扫描文件：{len(files)} 个")
    print()
    for table, r in report.items():
        print(f"  【{table}】")
        print(f"    风险：{r['why']}")
        orm = r["orm_path"]
        if orm["has_orm"]:
            print(f"    写入路径形态：🔴 **存在 ORM 写入路径**（{orm['entity']} ← "
                  f"{' / '.join(orm['mappers'])}）⇒ 字面量扫描**不覆盖** ⇒ 已额外扫 ORM 调用点")
        else:
            print(f"    写入路径形态：✅ **无 ORM 写入路径**（该表只有显式 SQL 写入）"
                  f"⇒ 字面量扫描即**全覆盖**")
        if orm["autofill"]:
            print(f"    MyBatis-Plus 自动填充字段：{orm['autofill']}")
        print(f"    INSERT 站点：{r['insert_sites']} 个 / 其它写操作：{r['other_write_sites']} 个 "
              f"/ DDL 提及：{r['ddl_mentions']} 个（不算写入方）")
        for s in r["inserts"]:
            if s["probe_infra"]:
                flag, note = "🧪", "探针设施（非部署产物）"
            elif s["in_domain"]:
                flag = "✅"
                note = "域内"
            else:
                flag, note = "🔴", "域外"
            bind = s["binds_required_columns"]
            bind_txt = "" if not r["required_columns"] else {
                True: "已绑两列", False: "❌ 没绑两列", None: "⚠️ 形态需人工确认"}[bind]
            print(f"      {flag} {s['file']}:{s['line']}  [{s['module']} · {s['kind']} · {note}]  {bind_txt}")
        for s in r["orm_sites"]:
            flag = "🧪" if s["probe_infra"] else ("✅" if s["in_domain"] else "🔴")
            print(f"      {flag} {s['file']}:{s['line']}  [ORM · {s['module']} · {s['kind']} · "
                  f"mapper={s['mapper']}]  {s['snippet']}")
        if r["other_writes"]:
            print("      其它写操作（只登记，不判失败 —— UPDATE 不触发 NOT NULL 违例）：")
            for s in r["other_writes"]:
                print(f"        · {s['file']}:{s['line']}  [{s['op']} · {s['module']}]")
        print()

    print("  覆盖范围：")
    print(f"    · 扫描：{len(files)} 个文件（后缀 {' '.join(SCAN_SUFFIXES)}）")
    print(f"    · 排除：target/（构建产物）· .git/ · node_modules/ · docs/（文档）")
    print(f"    · tools/ 下的写入**登记但不判失败**（探针设施不是部署产物）")
    print()
    print("  🔴 机器看不见的（**上线前必须人工确认一次** ⇒ 清单见")
    print("     tools/outbox-writer-audit/favorite/CHECKLIST.md）：")
    print("     · 人工 psql / 运维一次性命令")
    print("     · 报表 / BI 工具")
    print("     · 数据修复 / 迁移脚本（仓库外）")
    print("     · 另一个仓库 / 另一个服务")
    print("     · DBA 侧的存储过程 / 触发器")
    print()

    if problems:
        print(f"❌ 发现 {len(problems)} 个问题：")
        for p in problems:
            print(f"   [{p['kind']}] {p['table']} {p['file']}:{p['line']} —— {p['detail']}")
        print()
        print("   ⇒ V7 上线前必须清零（见 docs/DRIFT/favorite/rpc.md §13.6）。")
        return 1

    print("✅ 没有域外写入方，且所有 INSERT 都显式绑了 created_at / updated_at")
    print("   ⇒ V6 / V7 的破坏性变更在**本仓范围内**已闭合；")
    print("   ⚠️ **域外**（人工 SQL / 报表 / 另一个服务 / 数据修复脚本）**不在本工具的可见范围**")
    print("      ⇒ 上线前仍需按 CHECKLIST.md **人工确认一次**。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
