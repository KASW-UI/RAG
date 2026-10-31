#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
**一次性、离线**的 DDL 语义基准提取器 ⇒ 生成 `ci/contracts/schema/<domain>.json`。

════════════════════════════════════════════════════════════════════════
【这个脚本是什么 / 不是什么】
════════════════════════════════════════════════════════════════════════
**是**：把「Go 真源的活体 schema」与「Java 侧 Flyway 迁移重放后的活体 schema」
      逐列对照，产出一份**机器可读的语义约束基准**（R6 契约基准）。

**不是**：CI 的一部分。⚠️ **CI 不得运行本脚本** ——
      它需要 Go 真源仓 + Go 工具链 + `gorm.io/driver/postgres` 的 module cache
      ⇒ 那正是 CI **禁止**依赖的东西（ci/contracts/README.md §一 办法 A/B 被否）。
      ⇒ 基准**固化**在 `ci/contracts/schema/` 后，CI 只跑
      `tools/difftest/check-ddl-static.py --rag-only --semantic-baseline <file>`
      （**纯 Java 侧**）。

════════════════════════════════════════════════════════════════════════
【为什么必须活体提取，而不是读 Go 源码推】
════════════════════════════════════════════════════════════════════════
Go 侧 schema **不是手写 SQL**，是 GORM `AutoMigrate` 运行时生成的 ⇒
**可空性 / DEFAULT / 索引名 / 整数位宽** 全由 `gorm.io/driver/postgres@v1.6.0`
的行为决定 ⇒ **读 struct tag 推不出来**（AGENTS.md §3.3.1：推断 struct tag ≠ 消费点证据）。
⇒ 本脚本复用 `tools/difftest/ddl-parity.sh` 建出来的**双侧活体**（那套环境由
   `ddl-parity.sh` 负责建，本脚本**只读**）。

════════════════════════════════════════════════════════════════════════
【四类差异怎么分 —— 本脚本的核心语义】
════════════════════════════════════════════════════════════════════════
① **必须保持的约束**（⇒ 进 `must_match`，不满足 ⇒ EXIT=1）
   列类型 / 长度（长度上限决定的行为）/ 可空性（必填列）/ 关键 DEFAULT /
   唯一约束（码值唯一性 + 唯一索引保护的实体不变量）。
② **Java 技术栈合理产生的实现差异**（⇒ **不进** `must_match`，只登记）
   索引**命名形态**（`idx_users_uid` vs `users_user_uid_key`）· 序列名 ·
   表名域前缀 · 唯一索引 ↔ 唯一约束的**载体** · IDENTITY ↔ serial。
③ **已确认允许的结构差异**（⇒ `approved_deviations`，**带签字编号 + 台账指针**）
④ **尚未确认的差异**（⇒ `unconfirmed`，**每次运行都必须显式报出来**）

②③ 两类都**不判失败**（不是业务漂移）；④ 类**必须被看见**。

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
    # 前置：先起双侧活体环境（**只跑一次**）
    bash tools/difftest/ddl-parity.sh all
    # 然后逐域提取（也可用 --all）
    python3 tools/difftest/extract-schema-baseline.py --domain users
    python3 tools/difftest/extract-schema-baseline.py --all --out-dir ci/contracts/schema

    --print-diff <domain>   只打印 Go↔Java 的逐条差异（人工核对用），不写文件
"""
import argparse
import json
import os
import pathlib
import re
import subprocess
import sys

# ⚠️ 复用既有工具的查询与装载函数 ⇒ **不复制 SQL**，避免两份实现漂移
#   （文件名带 `-` ⇒ 不能用普通 import ⇒ 用 importlib 按路径加载）
import importlib.util  # noqa: E402

_cds_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "check-ddl-static.py")
_spec = importlib.util.spec_from_file_location("check_ddl_static", _cds_path)
cds = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cds)

REPO_ROOT = cds.REPO_ROOT
GO_TRUTH = os.environ.get(
    "GO_TRUTH_REPO", "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang")

# ── Go 侧 model 源码目录（每域）⇒ 用来取 `文件:行号` 出处 ────────────────────
GO_MODEL_DIRS = {
    "users": ["service/user/user/rpc/internal/model",
              "service/user/admin/rpc/internal/model"],
    "task": ["service/task/rpc/internal/Model"],
    "message": ["service/message/rpc/internal/model"],
    "comment": ["service/comment/rpc/internal/model"],
    "article": ["service/article/rpc/internal/model"],
    "like": ["service/like/rpc/internal/model"],
    "follow": ["service/follow/rpc/internal/model"],
    "favorite": ["service/favorite/rpc/internal/model"],
    "hot": [],
}

# ── 差异人工裁定表（**显式数据文件**，不解析散文 markdown）────────────────
#   理由同 tools/difftest/ddl-signed-exempt.txt：散文形态会随人手编辑漂移 ⇒
#   解析器一脆，要么误豁免（真差异被洗成绿），要么误报（已签字的报成新漂移）。
DEVIATIONS_FILE = pathlib.Path(__file__).with_name("schema-baseline-deviations.json")

# ── 维度口径 ────────────────────────────────────────────────────────────
#   「进 must_match」= 业务数据语义必须保持的约束
#   「不进 must_match」= 技术栈差异 / 已在别处裁决的维度
LEN_TYPES = {"character varying", "character", "varchar", "bpchar", "text"}

# ⚠️ 必须与 check-ddl-static.py 的 AUTO_GENERATED **逐字一致**
AUTO_GENERATED = "<AUTO_GENERATED>"


def _norm_default(val):
    """DEFAULT 归一化：序列名抹平（技术栈差异，不是语义差异）。"""
    if val is None:
        return None
    s = str(val).strip()
    if not s or s == "<NULL>":
        return None
    s = re.sub(r"nextval\('([^']+)'::regclass\)", "nextval('<SEQ>'::regclass)", s)
    return s


def _is_seq_default(val):
    return bool(val) and "nextval(" in str(val)


def psql(container, db, user, sql):
    return subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", user, "-d", db,
         "-t", "-A", "-c", sql],
        capture_output=True, text=True)


QUERY_FULL = (
    "select table_name || '.' || column_name || '|' || data_type || '|' || "
    "coalesce(character_maximum_length::text, '') || '|' || is_nullable || '|' || "
    "coalesce(column_default, '<NULL>') || '|' || coalesce(is_identity, 'NO') "
    "from information_schema.columns where table_schema='public' order by 1;")

QUERY_UNIQUE_CON = (
    "select conrelid::regclass::text || '|' || contype::text || '|' || "
    "pg_get_constraintdef(oid) "
    "from pg_constraint where connamespace='public'::regnamespace and contype='u' "
    "order by 1;")


def dump_side(container, db, table_map, pg_user, exclude_tables):
    """⇒ 活体 schema 的结构化快照（列 / 唯一约束 / 索引 / CHECK / 触发器）。"""
    out = {"columns": {}, "unique": set(), "indexes": {}, "checks": {}, "triggers": {}}

    p = psql(container, db, pg_user, QUERY_FULL)
    if p.returncode != 0:
        raise RuntimeError(f"读列失败 {container}/{db}: {p.stderr.strip()}")
    for line in p.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.strip().split("|")
        if len(parts) < 6:
            continue
        key, dtype, clen, nullable, default, identity = parts[:6]
        tbl = key.split(".", 1)[0]
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        jtbl = table_map.get(tbl, tbl)
        out["columns"][(jtbl, key.split(".", 1)[1])] = {
            "type": dtype,
            "length": int(clen) if clen else None,
            "nullable": nullable == "YES",
            "default": _norm_default(default),
            "identity": identity == "YES",
        }

    # 唯一约束（pg_constraint contype='u'）+ 唯一索引（pg_indexes）⇒
    # **归一成 (表, 列元组) 集合** ⇒ 载体不同（唯一索引 ↔ 唯一约束）不算差异
    p = psql(container, db, pg_user, QUERY_UNIQUE_CON)
    for line in p.stdout.splitlines():
        if not line.strip():
            continue
        tbl, ctype, cdef = line.strip().split("|", 2)
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        cols = cds._con_cols(cdef)
        if cols:
            out["unique"].add((table_map.get(tbl, tbl), cols))

    for name, (tbl, uniq, cols) in cds.load_indexes(
            container, db, table_map, exclude_tables, pg_user).items():
        if uniq:
            out["unique"].add((tbl, cols))
        else:
            out["indexes"][name] = (tbl, cols)

    p = psql(container, db, pg_user,
             "select conrelid::regclass::text || '|' || pg_get_constraintdef(oid) "
             "from pg_constraint where connamespace='public'::regnamespace "
             "and contype='c' order by 1;")
    for line in p.stdout.splitlines():
        if not line.strip():
            continue
        tbl, cdef = line.strip().split("|", 1)
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        out["checks"][(table_map.get(tbl, tbl), cdef)] = True

    for (tbl, name) in cds.load_triggers(
            container, db, table_map, exclude_tables, pg_user):
        out["triggers"][(tbl, name)] = True

    return out


# ── Go 源码出处扫描（`文件:行号`）─────────────────────────────────────────
def scan_go_sources(domain):
    """⇒ {go_table: {"struct":…, "table_name_line": "文件:行号", "columns": {col: "文件:行号"}}}"""
    files = []
    for d in GO_MODEL_DIRS.get(domain, []):
        ddir = os.path.join(GO_TRUTH, d)
        if not os.path.isdir(ddir):
            continue
        for f in sorted(os.listdir(ddir)):
            if f.endswith(".go") and not f.endswith("_test.go"):
                files.append(os.path.join(d, f))

    struct_defs = {}   # struct 名 ⇒ (文件, 行号, 字段列表[(行号, 字段名, tag)])
    table_of = {}      # struct 名 ⇒ (go_table, 文件:行号)
    for rel in files:
        path = os.path.join(GO_TRUTH, rel)
        lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
        i = 0
        while i < len(lines):
            line = lines[i]
            m = re.match(r"\s*type\s+(\w+)\s+struct\s*\{", line)
            if m:
                name = m.group(1)
                start = i + 1
                fields = []
                j = i + 1
                depth = 1
                while j < len(lines) and depth > 0:
                    l2 = lines[j]
                    depth += l2.count("{") - l2.count("}")
                    if depth <= 0:
                        break
                    fm = re.match(r"\s*(\w+)\s+([^\s`]+(?:\s+[^\s`]+)*?)\s*(`.*`)?\s*$",
                                  l2)
                    if fm and not l2.strip().startswith("//"):
                        fields.append((j + 1, fm.group(1), fm.group(3) or ""))
                    j += 1
                struct_defs[name] = (rel, start, fields)
                i = j
            else:
                m2 = re.match(r"\s*func\s+\(\w+\s+\*?(\w+)\)\s+TableName\(\)\s+string\s*\{",
                              line)
                if m2:
                    m3 = re.search(r'return\s+"([^"]+)"', line)
                    if m3:
                        table_of[m2.group(1)] = (m3.group(1), f"{rel}:{i + 1}")
            i += 1

    out = {}
    for struct, (gtbl, tline) in table_of.items():
        sd = struct_defs.get(struct)
        cols = {}
        if sd:
            rel, start, fields = sd
            for ln, fname, tag in fields:
                cm = re.search(r"column:(\w+)", tag)
                if cm:
                    cols[cm.group(1)] = f"{rel}:{ln}"
                else:
                    snake = re.sub(r"(?<!^)(?=[A-Z])", "_", fname).lower()
                    cols[snake] = f"{rel}:{ln}"
        out[gtbl] = {"struct": struct, "table_name_line": tline, "columns": cols}
    return out


def go_to_java_table(go_tbl, table_map):
    return table_map.get(go_tbl, go_tbl)


# ── 差异人工裁定表（显式数据文件）────────────────────────────────────────
def load_annotations(domain):
    """⇒ (逐条差异裁定表 {key: 条目}, 该域的「不进 must_match 的已知残留」清单)"""
    if not DEVIATIONS_FILE.exists():
        return {}, []
    data = json.loads(DEVIATIONS_FILE.read_text(encoding="utf-8"))
    dom = data.get(domain, {})
    return ({e["key"]: e for e in dom.get("deviations", [])},
            dom.get("known_residues", []))


def annot_key(table, column, dim):
    return f"{table}.{column}#{dim}"


def build_domain(domain, go, java, annotations):
    """⇒ 基准 JSON 的主体（tables / approved_deviations / unconfirmed）。"""
    table_map = cds.DOMAINS[domain]["table_map"]
    go_src = scan_go_sources(domain)

    # Go 表 → Java 表（反向映射，用于找 Go 侧对应物）
    java2go = {v: k for k, v in table_map.items()}

    tables = {}
    approved, unconfirmed = [], []

    for (jtbl, jcol), jv in sorted(java["columns"].items()):
        spec = tables.setdefault(jtbl, {
            "go_table": java2go.get(jtbl, jtbl),
            "columns": [],
            "unique_constraints": [],
        })
        # ⚠️ `dump_side` 已经把 Go 侧的表名映射成 Java 侧的名字 ⇒
        #    这里**不能再映射一次**（users 域会因此一个 Go 列都匹配不上）
        gtbl = java2go.get(jtbl, jtbl)
        gv = go["columns"].get((jtbl, jcol))
        gs = go_src.get(gtbl, {})

        entry = {
            "name": jcol,
            "type": jv["type"],
            "nullable": jv["nullable"],
            "must_match": ["type", "nullable"],
            "source": {"java": "RAG 侧 Flyway 迁移（活体重放）"},
        }
        if jv["length"] is not None:
            entry["max_length"] = jv["length"]
            entry["must_match"].append("length")
        if jv["identity"]:
            # ⚠️ IDENTITY 列的 column_default 在 PG 里是 NULL ⇒ 用**归一化哨兵**
            #    表达「自动生成」语义（与 serial 的 nextval 等价 ⇒ 载体差不算差异）
            entry["default"] = AUTO_GENERATED
            entry["identity"] = True
            entry["must_match"].append("default")
        elif jv["default"] is not None:
            entry["default"] = jv["default"]
            entry["must_match"].append("default")
        if gv is not None:
            entry["source"]["go"] = gs.get("columns", {}).get(
                jcol, f"{gtbl}（未在 Go model 源码里定位到该列）")
            entry["go_observed"] = {
                "type": gv["type"],
                "nullable": gv["nullable"],
                "default": gv["default"],
                "identity": gv["identity"],
            }
            if gv["length"] is not None:
                entry["go_observed"]["max_length"] = gv["length"]
        else:
            entry["source"]["go"] = None
            entry["evidence"] = "java-only"

        # ---- 逐维对照（Go ↔ Java）→ 归类 ---------------------------------
        if gv is not None:
            diffs = []
            if jv["type"] != gv["type"]:
                diffs.append(("type", gv["type"], jv["type"]))
            if (jv["length"] or None) != (gv["length"] or None):
                diffs.append(("length", gv["length"], jv["length"]))
            if jv["nullable"] != gv["nullable"]:
                diffs.append(("nullable", gv["nullable"], jv["nullable"]))
            jd, gd = jv["default"], gv["default"]
            if jd != gd:
                # IDENTITY ↔ serial：载体不同，语义相同（自动生成）⇒ 不算差异
                if not (jv["identity"] and _is_seq_default(gd)):
                    diffs.append(("default", gd, jd))
            for dim, gval, jval in diffs:
                key = annot_key(jtbl, jcol, dim)
                ann = annotations.get(key)
                if ann and ann.get("status") == "approved":
                    entry.setdefault("approved_deviation", []).append({
                        "dim": dim, "go_value": gval, "java_value": jval,
                        "deviation_id": ann.get("id"),
                        "reason": ann.get("reason"),
                        "ledger": ann.get("ledger"),
                    })
                    approved.append({
                        "id": ann.get("id"), "scope": f"{jtbl}.{jcol}",
                        "dim": dim, "go_value": gval, "java_value": jval,
                        "reason": ann.get("reason"), "ledger": ann.get("ledger"),
                    })
                elif ann and ann.get("status") == "unconfirmed":
                    entry.setdefault("unconfirmed", []).append({
                        "dim": dim, "go_value": gval, "java_value": jval,
                        "deviation_id": ann.get("id"),
                        "reason": ann.get("reason"),
                        "ledger": ann.get("ledger"),
                    })
                    unconfirmed.append({
                        "id": ann.get("id"), "scope": f"{jtbl}.{jcol}",
                        "dim": dim, "go_value": gval, "java_value": jval,
                        "reason": ann.get("reason"), "ledger": ann.get("ledger"),
                    })
                else:
                    entry.setdefault("unclassified_difference", []).append({
                        "dim": dim, "go_value": gval, "java_value": jval,
                        "note": "⚠️ **未裁定** —— 既无签字条目，也无未签字候选登记",
                    })
                    unconfirmed.append({
                        "id": None, "scope": f"{jtbl}.{jcol}", "dim": dim,
                        "go_value": gval, "java_value": jval,
                        "reason": "⚠️ **未裁定**：台账里既没有签字条目，也没有未签字候选登记",
                        "ledger": "docs/DRIFT/_cross/ddl-type-divergences.md",
                    })
        spec["columns"].append(entry)

    # ---- 唯一约束（(表, 列元组) 集合）--------------------------------------
    for (tbl, cols) in sorted(java["unique"]):
        spec = tables.setdefault(tbl, {
            "go_table": java2go.get(tbl, tbl), "columns": [], "unique_constraints": []})
        spec["unique_constraints"].append(list(cols))

    # ---- Go 侧独有的唯一约束（Java 缺 ⇒ 必须保持的约束缺失 ⇒ 硬失败）-------
    for (jtbl, cols) in sorted(go["unique"]):
        spec = tables.get(jtbl)
        if spec is None:
            continue
        if (jtbl, cols) not in java["unique"]:
            unconfirmed.append({
                "id": None, "scope": f"{jtbl}.{cols}", "dim": "unique_constraints",
                "go_value": list(cols), "java_value": None,
                "reason": "🔴 **Go 侧有、RAG 侧缺**的唯一约束 ⇒ 码值唯一性不变量缺失",
                "ledger": "docs/DRIFT/_cross/ddl-type-divergences.md",
            })

    # ---- 表级：Java 独有 / Go 独有 ----------------------------------------
    extra_tables = []
    go_tables = {k[0] for k in go["columns"]}
    java_tables = {k[0] for k in java["columns"]}
    for t in sorted(java_tables - go_tables):
        ann = annotations.get(f"table:{t}")
        extra_tables.append({
            "name": t,
            "go_counterpart": None,
            "reason": (ann or {}).get("reason", "⚠️ Java 侧独有表（Go 侧无对应）"),
            "deviation_id": (ann or {}).get("id"),
            "ledger": (ann or {}).get("ledger"),
            "column_evidence": "java-only（无 Go 侧样本 ⇒ 列级只固化 Java 现状）",
        })
        if ann and ann.get("status") == "approved":
            approved.append({
                "id": ann.get("id"), "scope": f"table {t}", "dim": "table_set",
                "go_value": None, "java_value": t,
                "reason": ann.get("reason"), "ledger": ann.get("ledger"),
            })
        else:
            unconfirmed.append({
                "id": (ann or {}).get("id"), "scope": f"table {t}", "dim": "table_set",
                "go_value": None, "java_value": t,
                "reason": (ann or {}).get("reason", "⚠️ Java 侧独有表（Go 侧无对应）"),
                "ledger": (ann or {}).get("ledger"),
            })
    for t in sorted(go_tables - java_tables):
        unconfirmed.append({
            "id": None, "scope": f"table {t}", "dim": "table_set",
            "go_value": t, "java_value": None,
            "reason": "🔴 **Go 侧有、RAG 侧缺表** ⇒ 表集合缺失",
            "ledger": "docs/DRIFT/_cross/ddl-type-divergences.md",
        })

    # ---- 非唯一索引（性能维度 ⇒ 不进 must_match，只登记）-------------------
    index_notes = []
    for name, (tbl, cols) in sorted(go["indexes"].items()):
        if (tbl, cols) in java["unique"]:
            continue
        if name in {n for (t, c), n in [] }:
            continue
        index_notes.append({
            "index": name, "table": tbl, "columns": list(cols),
            "side": "go",
            "note": "非唯一普通索引 ⇒ **性能维度**，不改变写入语义 ⇒ 不进 must_match",
        })
    for name, (tbl, cols) in sorted(java["indexes"].items()):
        index_notes.append({
            "index": name, "table": tbl, "columns": list(cols),
            "side": "java",
            "note": "非唯一普通索引 ⇒ **性能维度** ⇒ 不进 must_match",
        })

    return tables, extra_tables, approved, unconfirmed, index_notes


def main():
    ap = argparse.ArgumentParser(
        description="一次性离线提取 DDL 语义基准（**不是 CI 的一部分**）")
    ap.add_argument("--domain", default=None, choices=sorted(cds.DOMAINS))
    ap.add_argument("--all", action="store_true", help="9 个域全提（hot 域无表 ⇒ 产出 N/A 基准）")
    ap.add_argument("--out-dir", default=os.path.join(REPO_ROOT, "ci/contracts/schema"))
    ap.add_argument("--print-diff", default=None,
                    help="只打印该域的 Go↔Java 逐条差异（人工核对用），不写文件")
    args = ap.parse_args()

    domains = sorted(cds.DOMAINS) if args.all else [args.domain]
    if not domains:
        print("需要 --domain 或 --all", file=sys.stderr)
        return 2

    os.makedirs(args.out_dir, exist_ok=True)
    for domain in domains:
        cfg = cds.DOMAINS[domain]
        container = cfg["container"]
        go_db = cfg["go_db"]
        rag_db = cfg["rag_db"]
        pg_user = cfg.get("pg_user", "admin")
        exclude = cfg.get("exclude_tables", ())

        go = dump_side(container, go_db, cfg["table_map"], pg_user, exclude)
        java = dump_side(container, rag_db, cfg["table_map"], pg_user, exclude)

        annotations, known_residues = load_annotations(domain)
        tables, extra_tables, approved, unconfirmed, index_notes = build_domain(
            domain, go, java, annotations)

        if args.print_diff == domain:
            print(f"== {domain} · Go 侧列 {len(go['columns'])} / Java 侧列 {len(java['columns'])}")
            for t in sorted(tables):
                for c in tables[t]["columns"]:
                    u = c.get("unclassified_difference") or []
                    a = c.get("approved_deviation") or []
                    n = c.get("unconfirmed") or []
                    if not (u or a or n):
                        continue
                    print(f"  {t}.{c['name']}")
                    for x in u:
                        print(f"    ⚠️ 未裁定  {x['dim']}: go={x['go_value']!r} java={x['java_value']!r}")
                    for x in a:
                        print(f"    ✅ 已批准  {x['dim']}: go={x['go_value']!r} java={x['java_value']!r}  [{x['deviation_id']}]")
                    for x in n:
                        print(f"    ⏳ 未确认  {x['dim']}: go={x['go_value']!r} java={x['java_value']!r}  [{x['deviation_id']}]")
            return 0

        sqls = cds.discover_rag_sql(domain)
        baseline = {
            "domain": domain,
            "contract_version": "2026-10-31",
            "generated_by": "tools/difftest/extract-schema-baseline.py（一次性离线提取）",
            "source": {
                "go_truth_repo": "Sea-RideTheWind_golang（**只读**，真理之源）",
                "go_extraction": "GORM AutoMigrate 活体（gorm.io/driver/postgres v1.6.0）"
                                 "⇒ 索引名/可空性/DEFAULT 由驱动行为决定，读源码推不出来",
                "go_model_dirs": GO_MODEL_DIRS.get(domain, []),
                "java_extraction": "RAG 侧 Flyway / SchemaInitializer 迁移在干净 PG 上按序重放",
                "java_migration_dir": cfg.get("rag_sql_dir"),
                "java_migration_files": [os.path.relpath(s, REPO_ROOT) for s in sqls],
                "extracted_at": "2026-10-31",
            },
            "dimensions": {
                "compared": [
                    "column.type（列类型）",
                    "column.max_length（长度上限 ⇒ 决定超长写入是否被拒）",
                    "column.nullable（必填列）",
                    "column.default（关键 DEFAULT：Go 侧有默认值的列 + 有 DEFAULT 差异登记的列）",
                    "table.unique_constraints（唯一约束的**列元组集合** ⇒ 码值唯一性 + 唯一索引保护的实体不变量）",
                ],
                "skipped": [
                    {"dim": "index_naming",
                     "why": "索引**命名形态**是技术栈差异（Go `idx_users_uid` vs Java `users_user_uid_key`）"
                            "⇒ 唯一性判定 / 冲突行为完全相同 ⇒ 不判失败"},
                    {"dim": "index_non_unique_existence",
                     "why": "非唯一普通索引只影响查询计划（性能），不改变写入语义 ⇒ 不进 must_match"},
                    {"dim": "sequence_names",
                     "why": "序列名随表名走（Go `users_id_seq` vs Java `users_user_id_seq`）⇒ DEFAULT 比对前先归一化"},
                    {"dim": "unique_carrier",
                     "why": "唯一索引 ↔ 唯一约束的**载体**不同 ⇒ 归一成 (表, 列元组) 集合后比对"},
                    {"dim": "identity_vs_serial",
                     "why": "`GENERATED BY DEFAULT AS IDENTITY` ↔ `bigserial` ⇒ 载体不同、自动生成语义相同 ⇒ 不判失败"},
                    {"dim": "check_constraints",
                     "why": "CHECK 的定义文本形态差异大（表达式写法），逐字比会产生噪音 ⇒ 本基准**不覆盖**"
                            "⇒ 残留见 docs/DRIFT/_cross/ddl-type-divergences.md §四"},
                    {"dim": "triggers",
                     "why": "触发器函数体是 SQL 文本，逐字比不可判 ⇒ 本基准**不覆盖** ⇒ 同上"},
                    {"dim": "foreign_keys",
                     "why": "本仓两侧都没有外键 ⇒ 无对象可比"},
                ],
            },
            "tables": tables,
            "extra_tables": extra_tables,
            "approved_deviations": approved,
            "unconfirmed": unconfirmed,
            "known_residues_not_enforced": known_residues,
            "non_unique_indexes": index_notes,
        }
        out = os.path.join(args.out_dir, f"{domain}.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump(baseline, f, ensure_ascii=False, indent=2)
            f.write("\n")
        ncol = sum(len(t["columns"]) for t in tables.values())
        ncon = sum(len(t["unique_constraints"]) for t in tables.values())
        print(f"{domain:9s} 表 {len(tables):2d}  列 {ncol:3d}  唯一约束 {ncon:2d}  "
              f"已批准 {len(approved):2d}  未确认 {len(unconfirmed):2d}  → {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
