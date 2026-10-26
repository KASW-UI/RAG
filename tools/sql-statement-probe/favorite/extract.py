#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SQL 语句层对拍 · **抽取器** —— 从 PG 服务端日志里把「两侧各自说了什么话」切出来。

输入：
  1. pg.log     —— PG 服务端日志（docker logs 的输出）
  2. schema.tsv —— 表结构快照（table_name / ordinal_position / column_name）
输出：
  extracted.json —— 每侧、每用例的语句序列（原始 SQL 文本，不做归一化）

═══════════════════════════════════════════════════════════════════════════════
🔴 本文件的每一处判据都来自**实测**，不是来自「PG 大概会这么记」：

  · PG 16 的 log_statement=all 对 **simple 协议**记 `LOG:  statement: <sql>`；
  · 对 **扩展协议**记 `LOG:  execute <名字>: <sql>`（名字是 pgx 的 stmtcache_<sha> /
    pgjdbc 的 S_1 / unnamed ⇒ **两侧名字不可比**，一律丢弃）；
  · **没有** parse / bind 行（实测：pgx 连跑 3 次只出 3 行 execute）⇒
    一条语句恰好一行 ⇒ **不会**重复计数；
  · 扩展协议额外跟一行 `DETAIL:  parameters: $1 = '0'` ⇒ 是**独立的日志记录**
    （有自己的前缀），不是上一行的续行 ⇒ 解析时按「有前缀的行 = 新记录」切；
  · **多行 SQL 会跨行**（实测：`SELECT a,\n b\n FROM t` 原样跨 4 行）⇒
    没有前缀的行是上一条记录的**续行** ⇒ 拼回去。

证据：tools/sql-statement-probe/favorite/README.md 的「实测记录」一节。
═══════════════════════════════════════════════════════════════════════════════
"""

import json
import re
import sys
from collections import OrderedDict

# ── 日志记录前缀：'2026-10-26 08:02:44.257 UTC [121] db=sp app=go_side LOG:  …' ──
# ⚠️ app 可能为空（`db=postgres app= LOG:`）⇒ 两个捕获组都用 \S*（可匹配空）。
REC_RE = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+) "
    r"(?P<tz>\S+) \[(?P<pid>\d+)\] "
    r"db=(?P<db>\S*) app=(?P<app>\S*) "
    r"(?P<msg>.*)$"
)

# ── 语句记录的三种前缀 ──
STMT_RE = re.compile(r"^LOG:\s+(?:statement|execute [^:]*|parse [^:]*|bind [^:]*):\s*(.*)$", re.S)
DETAIL_RE = re.compile(r"^DETAIL:.*", re.S)

# ── marker：SELECT 'SQLMARK::<side>::<case-id>::<seq>' ──
MARKER_RE = re.compile(r"SQLMARK::(?P<side>[a-z]+)::(?P<case>[A-Za-z0-9_\-]+)::(?P<seq>\d+)")

# ── 扩展协议的 DETAIL 行：parameters: $1 = '9001', $2 = '1' ──
# ⚠️ PG 的 DETAIL 行是**独立记录**（有自己的前缀），不是上一行的续行。
#    值里的单引号用 '' 转义 ⇒ 捕获组是 (?:[^']|'')*。
# 🔴 **2026-10-26 第二十三轮修**：原正则只匹配**带引号**的值 ⇒ PG 对 NULL 参数印的是
#    **不带引号的** `NULL`（`parameters: $10 = NULL`）⇒ **整条参数被丢掉** ⇒
#    探针**分不清「绑了 NULL」与「根本没提到这个参数」** ⇒ 静默的假阴性。
#    ⇒ 现在 NULL 也捕获（存成 Python 的 None）。
PARAM_RE = re.compile(r"\$(\d+) = (NULL|'(?:[^']|'')*')")

GO_SIDE = {"name": "go", "db": "godb", "app": "go_side"}
RAG_SIDE = {"name": "rag", "db": "first_db", "app": "rag_side"}
MARKER_APP = "probe_marker"


def parse_records(lines):
    """把日志切成「记录」：一条记录 = 一行带前缀的日志 + 其后若干无前缀的续行。"""
    records = []
    cur = None
    for raw in lines:
        m = REC_RE.match(raw)
        if m:
            if cur is not None:
                records.append(cur)
            cur = {
                "ts": m.group("ts"),
                "db": m.group("db"),
                "app": m.group("app"),
                "msg": m.group("msg"),
            }
        elif cur is not None:
            # 无前缀的行 = 上一条记录的续行（多行 SQL）
            cur["msg"] += "\n" + raw.rstrip("\n")
        else:
            continue
    if cur is not None:
        records.append(cur)
    return records


def classify(records):
    """把记录分成：语句 / 详情 / 其它。

    🔴 **扩展协议的参数值挂在 DETAIL 行上**（实测：pgx 的
       `LOG: execute <name>: SELECT …` 后面跟一行
       `DETAIL: parameters: $1 = '9001'`）⇒ 把 DETAIL **挂回**它前面那条语句上，
       否则 LIMIT/OFFSET 的真实值就丢了（`LIMIT $2` vs `LIMIT 1` 会误判成差异）。
    """
    stmts, details, others = [], [], []
    for i, r in enumerate(records):
        msg = r["msg"]
        m = STMT_RE.match(msg)
        if m:
            kind_prefix = msg.split(":", 1)[0]
            form = "statement"
            if "execute " in kind_prefix:
                form = "execute"
            elif "parse " in kind_prefix:
                form = "parse"
            elif "bind " in kind_prefix:
                form = "bind"
            stmts.append({
                "ts": r["ts"],
                "db": r["db"],
                "app": r["app"],
                "form": form,
                "sql": m.group(1).strip(),
                "params": {},
            })
        elif DETAIL_RE.match(msg):
            details.append(r)
            # 挂到**上一条**语句上（DETAIL 紧跟在其后）
            if stmts and "parameters:" in msg:
                tail = msg.split("parameters:", 1)[1]
                for pnum, pval in PARAM_RE.findall(tail):
                    if pval == "NULL":
                        # 🔴 NULL 是**真值**（不是「没提到」）⇒ 必须显式记 None
                        stmts[-1]["params"][pnum] = None
                    else:
                        stmts[-1]["params"][pnum] = pval[1:-1].replace("''", "'")
        else:
            others.append(r)
    return stmts, details, others


def load_schema(path):
    """schema.tsv → 结构快照。

    ⚠️ 不只是列清单 —— 还要**唯一键**：判定「ORDER BY 差异是否等价」时，
    需要知道 WHERE 是不是钉住了某个唯一键（否则加 ORDER BY 会改变取哪一行）。

    行格式（6 列，制表符分隔）：
        COL <table> <pos> '' '' <column>
        UC  <table> <pos> <constraint_name> <constraint_type> <column>
        UI  <table> <pos> <index_name> 'UNIQUE' <column>

    ⚠️ **UC 与 UI 必须都收**：`CREATE UNIQUE INDEX` 建的**不是约束**
       ⇒ `information_schema.table_constraints` 里看不到
       ⇒ 只查约束会漏掉 `uk_favorite_folder_folder_id` / `uk_folder_target`
       ⇒ 「WHERE 是否钉住唯一键」判不出来 ⇒ GORM 的 ORDER BY <pk> 会被误报成语义差异。

    返回：
        { "<table>": [col, …],          ← 列清单（按 pos）
          "__pk__":  { "<table>": [col, …] },
          "__uniq__": { "<table>": [[col, …], …] },
          "__meta__": { "<table>": { "<col>": {"default": …, "nullable": "YES"/"NO"} } } }

    🔴 **2026-10-26 第二十三轮新增 `__meta__`**（DEFAULT / NOT NULL）：
    「写列集不同」**不等于**「落库值不同」—— 少写一列时，DB 会用该列的 DEFAULT 补上
    （没有 DEFAULT 才是 NULL）⇒ 只比列集会把「值相同」误报成语义差异
    （实测三例：technical_offset 的 DEFAULT 0 与 Go 显式写的 0 相同；
      technical_received_at / delivered_at 两侧都是 NULL）。
    ⇒ 判定落库值是否真的不同，**必须**知道 DDL 的 DEFAULT。
    """
    schema = {}
    pk = {}
    uniq = OrderedDict()
    meta = OrderedDict()
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            parts = line.split("\t")
            if len(parts) < 6:
                continue
            kind, table, pos, cname, ctype, col = parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]
            if kind == "COL":
                schema.setdefault(table, []).append(col)
                # 🔴 第 4/5 槽在第二十三轮起携带 DDL 元信息（此前是空串）：
                #    第 4 槽 = column_default（无 DEFAULT 时是 <NODEFAULT>）
                #    第 5 槽 = is_nullable（YES / NO）
                default = None if cname in ("", "<NODEFAULT>") else cname
                meta.setdefault(table, OrderedDict())[col] = {
                    "default": default,
                    "nullable": ctype or "YES",
                }
            elif kind == "UC":
                if ctype == "PRIMARY KEY":
                    pk.setdefault(table, []).append(col)
                else:
                    # 多列唯一键按 constraint_name 归组
                    uniq.setdefault(table, OrderedDict()).setdefault(cname, []).append(col)
            elif kind == "UI":
                # 唯一索引：按 index_name 归组
                uniq.setdefault(table, OrderedDict()).setdefault(cname, []).append(col)
    schema["__pk__"] = pk
    schema["__uniq__"] = {t: list(d.values()) for t, d in uniq.items()}
    schema["__meta__"] = meta
    return schema


def main():
    if len(sys.argv) != 4:
        print("用法: extract.py <pg.log> <schema.tsv> <out.json>", file=sys.stderr)
        return 2
    pg_log, schema_tsv, out_json = sys.argv[1], sys.argv[2], sys.argv[3]

    with open(pg_log, "r", encoding="utf-8", errors="replace") as fh:
        lines = fh.read().split("\n")
    schema = load_schema(schema_tsv)

    records = parse_records(lines)
    stmts, details, others = classify(records)

    # ── ① 按「库 + application_name」分流 ─────────────────────────────────
    # ⚠️ 主判据是 **application_name**（两侧各自设置的）；
    #    db 是**兜底**：application_name 没生效时，至少还能靠库分开。
    #
    # 🔴 **「分流泄漏」只统计第一个 marker 之后的语句**：
    #    第一个 marker 之前本来就有**别的 application_name** 的语句 ——
    #    psql 灌种子（app=psql）、schemaprep 建表（app=空）、
    #    pgjdbc 的 `SET application_name` 回显（改 app 之前那一刻）——
    #    它们**发生在任何用例之前** ⇒ 对分段毫无影响 ⇒ 不算泄漏。
    #    把它们算进泄漏 ⇒ 每次跑都误报 ⇒ 告警就废了。
    first_marker_idx = len(stmts)
    for i, s in enumerate(stmts):
        if MARKER_RE.search(s["sql"]):
            first_marker_idx = i
            break

    sides = {}
    leaks = []
    for side in (GO_SIDE, RAG_SIDE):
        picked = [s for s in stmts if s["db"] == side["db"] and s["app"] == side["app"]]
        wrong_app = [s for i, s in enumerate(stmts)
                     if i > first_marker_idx
                     and s["db"] == side["db"] and s["app"] != side["app"]
                     and s["app"] != MARKER_APP
                     # ⚠️ `SET application_name = …` 是**连接参数回显**：
                     #    它在「改 app 之前」那一刻记录 ⇒ 记的是**旧** app 名
                     #    ⇒ 与两侧的 app 名都不匹配 ⇒ 但它**不是泄漏**。
                     and not re.match(r"(?i)^set\s+application_name", s["sql"].strip())]
        leaks.append({
            "side": side["name"],
            "db": side["db"],
            "expected_app": side["app"],
            "count": len(wrong_app),
            "samples": [s["sql"][:120] for s in wrong_app[:5]],
        })
        sides[side["name"]] = {"statements": picked, "markers": []}

    # ── ② marker：切段依据 ───────────────────────────────────────────────
    marker_stmts = [s for s in stmts if s["app"] == MARKER_APP and MARKER_RE.search(s["sql"])]
    # ⚠️ marker 记录的是**全局顺序**里第几条 ⇒ 靠它在各自桶里的下标切段。
    #    因此按全局顺序走一遍，维护每侧桶的长度（marker 自己**不计入**桶 ——
    #    marker 的 application_name 是 probe_marker，本来就不属于任何一侧）。
    per_side_seen = {GO_SIDE["name"]: 0, RAG_SIDE["name"]: 0}
    marker_index = []       # [(side, case_id, 该侧桶内起始下标), …] —— 按全局顺序
    for s in stmts:
        m = MARKER_RE.search(s["sql"])
        if m and m.group("side") in per_side_seen:
            side = m.group("side")
            marker_index.append((side, m.group("case"), per_side_seen[side]))
            continue
        # 只有属于某一侧的语句才推进那个侧的计数
        for side in (GO_SIDE, RAG_SIDE):
            if s["db"] == side["db"] and s["app"] == side["app"]:
                per_side_seen[side["name"]] += 1
                break

    # ── ③ 按 marker 切段 ─────────────────────────────────────────────────
    cases = {}
    for side in (GO_SIDE, RAG_SIDE):
        name = side["name"]
        bucket = sides[name]["statements"]
        marks = [(case_id, idx) for (sd, case_id, idx) in marker_index if sd == name]
        for i, (case_id, start) in enumerate(marks):
            end = marks[i + 1][1] if i + 1 < len(marks) else len(bucket)
            # ⚠️ 保留**整条记录**（sql + 绑定的参数值）⇒ 比对器才能把
            #    `LIMIT $2` 和 `LIMIT 1` 判成同一件事。
            cases.setdefault(case_id, {})[name] = [
                {"sql": s["sql"], "params": s.get("params", {})}
                for s in bucket[start:end]
            ]

    out = {
        "schema": schema,
        "cases": cases,
        "sides": {
            name: {
                "db": side["db"],
                "app": side["app"],
                "total_statements": len(sides[name]["statements"]),
                "markers": [c for (sd, c, _i) in marker_index if sd == name],
            }
            for name, side in (("go", GO_SIDE), ("rag", RAG_SIDE))
        },
        "stats": {
            "log_lines": len(lines),
            "records": len(records),
            "statement_records": len(stmts),
            "detail_records": len(details),
            "other_records": len(others),
            "marker_statements": len(marker_stmts),
            "parse_or_bind_records": len([s for s in stmts if s["form"] in ("parse", "bind")]),
        },
        "leaks": leaks,
    }
    with open(out_json, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=2)

    print("[extract] 记录 %d 条 / 语句 %d 条 / marker %d 条 / parse+bind %d 条"
          % (out["stats"]["records"], out["stats"]["statement_records"],
             out["stats"]["marker_statements"], out["stats"]["parse_or_bind_records"]))
    for name in ("go", "rag"):
        print("[extract] %s 侧：%d 条语句，%d 个用例段"
              % (name, out["sides"][name]["total_statements"], len(out["sides"][name]["markers"])))
    for leak in leaks:
        if leak["count"]:
            print("[extract] ⚠️ %s 侧有 %d 条**application_name 不符**的语句（分流可能失效）"
                  % (leak["side"], leak["count"]))
            for s in leak["samples"]:
                print("           %s" % s.replace("\n", " ")[:110])
    return 0


if __name__ == "__main__":
    sys.exit(main())
