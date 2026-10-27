#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SQL 语句层对拍 · **比对器** —— 比「两侧的语义签名」，不比「两侧的 SQL 文本」。

═══════════════════════════════════════════════════════════════════════════════
🔴 为什么**不**比 SQL 文本：
   GORM 与手写 SQL 的生成差异是**库级噪声**，不是 favorite 域的行为：
     · 占位符风格      $1 / ? / :name
     · 投影写法        SELECT *  vs  显式列清单
     · GORM 给 First 偷偷加的 ORDER BY <pk>
     · GORM 的 RETURNING "id"（自增代理键，域内从不用）
   ⇒ 逐条**归一到语义签名**再比：
       (语句类别, 主表, 写入列集, WHERE 列集, 锁子句, ORDER BY, LIMIT/OFFSET, JOIN 表集)
   ⇒ 只有**语义维度**的差异才判为差异；ORM 噪声单列一档。

🔴 差异分档：
   SEMANTIC      —— 语义差异，**必须人工核证**（可能是真缺陷）
   ORM-GENERIC   —— 库级生成差异（已知、可解释、不改）
   EQUIVALENT    —— 形式不同但**已证明等价**（如 ORDER BY 唯一键上的 pk）
═══════════════════════════════════════════════════════════════════════════════
"""

import json
import os
import re
import sys
from collections import OrderedDict

# ── 差异分档 ──
SEMANTIC = "SEMANTIC"
ORM_GENERIC = "ORM-GENERIC"
EQUIVALENT = "EQUIVALENT"
# 🔴 第二十三轮：**落库值不可判定**（有列集差异，但机械判不出值是否相同）
#   —— 不是「已证明的差异」，也不是「已证明的等价」⇒ **单独一档，不误判为失败**。
UNDECIDABLE_CLS = "UNDECIDABLE"

# 🔴 第二十三轮：人工判定的等价台账（全局，由 main() 装载）
#   (case_id, dim, table, column) → {anchor, reason, ok, why}
ADJ = OrderedDict()

# ════════════════════════════════════════════════════════════════════════════════
# 归一化
# ════════════════════════════════════════════════════════════════════════════════

STR_LIT_RE = re.compile(r"'(?:[^']|'')*'")
DOLLAR_PARAM_RE = re.compile(r"\$\d+")
NAMED_PARAM_RE = re.compile(r"(?<![:\w]):\w+")
WHITESPACE_RE = re.compile(r"\s+")


def normalize(sql):
    """把一条 SQL 归一成「可比形式」。

    ⚠️ 顺序很重要：先替换字符串字面量（否则字面量里的 ':' 会被当占位符），
       再替换占位符，最后压空白。
    """
    s = sql.strip()
    if s.endswith(";"):
        s = s[:-1]
    # 标识符双引号：两侧都可能带（PG 折成小写，去掉引号后两边才可比）
    s = s.replace('"', "")
    # 字符串字面量 → ?
    s = STR_LIT_RE.sub("?", s)
    # 占位符统一 → ?
    s = DOLLAR_PARAM_RE.sub("?", s)
    s = NAMED_PARAM_RE.sub("?", s)
    # 空白压平 + 小写（PG 对未加引号的标识符折成小写）
    s = WHITESPACE_RE.sub(" ", s).strip().lower()
    return s


# ════════════════════════════════════════════════════════════════════════════════
# 语义签名
# ════════════════════════════════════════════════════════════════════════════════

def split_top_level(text, seps=(" and ", " or ")):
    """按顶层 AND / OR 切分（尊重括号嵌套）。"""
    parts, depth, cur, i = [], 0, [], 0
    while i < len(text):
        ch = text[i]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if depth == 0:
            matched = None
            for sep in seps:
                if text[i:i + len(sep)] == sep:
                    matched = sep
                    break
            if matched:
                parts.append("".join(cur).strip())
                cur = []
                i += len(matched)
                continue
        cur.append(ch)
        i += 1
    parts.append("".join(cur).strip())
    return [p for p in parts if p]


def strip_parens(s):
    s = s.strip()
    while s.startswith("(") and s.endswith(")"):
        inner = s[1:-1]
        depth = 0
        ok = True
        for ch in inner:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth < 0:
                    ok = False
                    break
        if not ok or depth != 0:
            break
        s = inner.strip()
    return s


def extract_where_cols(where_text):
    """从 WHERE 子句里抽出「被比较的列」。

    ⚠️ 只抽**列名**（比较的左侧），不抽值 ⇒ 值是占位符/字面量，不进语义。
    ⚠️ 顶层 OR 的两侧都算（它们都是过滤条件）。
    """
    cols = set()
    for conjunct in split_top_level(where_text):
        c = strip_parens(conjunct)
        if not c:
            continue
        # 形如 `col = ?` / `col != ?` / `col in (…)` / `col like ?` / `col is null`
        m = re.match(r"^([a-z_][\w.]*)\s*(=|<>|!=|<|>|<=|>=|\bin\b|\blike\b|\bilike\b|\bis\b|\bnot\s+in\b)",
                     c)
        if m:
            cols.add(m.group(1).split(".")[-1])
            continue
        # 形如 `(a, b) in ((?,?),(?,?))` —— 取第一列
        m = re.match(r"^\(([^)]*)\)\s*(in|=)", c)
        if m:
            first = m.group(1).split(",")[0].strip()
            if re.match(r"^[a-z_][\w.]*$", first):
                cols.add(first.split(".")[-1])
    return sorted(cols)


def resolve_count(sql, params, keyword):
    """把 LIMIT / OFFSET 的**真实值**取出来。

    🔴 为什么必须解析：Go 侧 GORM 的 `First` 生成 `LIMIT $2`（值绑在 $2 上），
    RAG 侧手写 SQL 是 `LIMIT 1`（字面量）。
    不解析就会把「同一个意思」判成差异 ⇒ **假阳性** ⇒ 比对器不可信。
    ⇒ 用扩展协议 DETAIL 行里的绑定值把 $N 还原成数字。

    ⚠️ 若取不到绑定值（没 DETAIL / 值不是数字）⇒ 返回 "?"（表示「有个值但不知道」）
    ⇒ 与另一侧的 "?" 可比；与字面量数字**不可比** ⇒ 保守起见按「不可比」处理。
    """
    m = re.search(r"\b" + keyword + r"\s+(\$\d+)", sql, re.I)
    if m:
        num = m.group(1)[1:]
        val = (params or {}).get(num)
        if val is not None and re.fullmatch(r"\d+", val.strip()):
            return val.strip()
        return "?"
    m = re.search(r"\b" + keyword + r"\s+(\d+)", sql, re.I)
    if m:
        return m.group(1)
    return None


# ════════════════════════════════════════════════════════════════════════════════
# 🔴 2026-10-26 第二十三轮：**写入值**（写列集 ≠ 落库值）
#
# 「写列集不同」**不等于**「落库值不同」：少写一列时 DB 会用该列的 DEFAULT 补上
# （没有 DEFAULT 才是 NULL）⇒ 只比列集会把「值其实相同」误报成语义差异。
# 实测三例：technical_offset（DDL DEFAULT 0 ≡ Go 显式写的 0）·
#          technical_received_at / delivered_at（两侧都是 NULL）。
# ⇒ 判定必须落到**有效值**上：写了 ⇒ 取绑定的值；没写 ⇒ 取 DDL 的 DEFAULT / NULL。
# ════════════════════════════════════════════════════════════════════════════════

NULL_TOKEN = "<NULL>"
UNDECIDABLE = "<UNDECIDABLE>"
NOTNULL_MISSING = "<NOTNULL-MISSING>"

# 非确定性 DEFAULT ⇒ 两侧的值天然不同（但语义等价）⇒ **不可机械判定**
NONDET_DEFAULT_RE = re.compile(
    r"(?i)\b(now\(\)|clock_timestamp\(\)|nextval\(|random\(\)|gen_random_uuid\(\)"
    r"|uuid_generate_v4\(\)|current_timestamp|current_time|localtime|timeofday)")


def _paren_content(text, start):
    """start 指向 '(' ⇒ 返回 (括号内文本, 结束下标)。"""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:i], i + 1
    return None, len(text)


def _norm_value(v):
    """参数值 → 可比较的 token。"""
    if v is None:
        return NULL_TOKEN
    s = str(v)
    return "''" if s == "" else s


def _literal_token(expr):
    """SQL 字面量 / DEFAULT 表达式 → token。"""
    t = expr.strip()
    if t.upper() == "NULL":
        return NULL_TOKEN
    if t in ("''", '""'):
        return "''"
    # 去掉类型转换后缀（$4::jsonb / 'x'::text 之类）
    t = re.sub(r"::[\w\.\[\]\" ]+$", "", t).strip()
    return t


def _expr_token(expr, params):
    """一个 VALUES / SET 项 → token。"""
    e = expr.strip()
    m = re.match(r"^\$(\d+)(?:\s*::[\w\.\[\]\" ]+)?$", e)
    if m:
        pnum = m.group(1)
        if pnum not in params:
            return "<UNBOUND:%s>" % pnum
        return _norm_value(params[pnum])
    return _literal_token(e)


def _values_map(sql, cols, params):
    """INSERT 的 VALUES 列表 → {列: token}（按位置对应）。

    ⚠️ **必须传原始 SQL**：normalize() 会把 `$N` 改写成 `?`、把字符串字面量也改成 `?`
       ⇒ 在归一化后的 SQL 上做位置映射 ⇒ 参数**全丢**（实测：Go 侧每个值都成了 `?`）。
    ⚠️ 项数与列数不符 ⇒ **不猜**（返回空 ⇒ 上层判「不可判定」而不是「值不同」）。
    """
    m = re.search(r"(?i)\bvalues\s*", sql)
    if not m:
        return {}
    p = sql.find("(", m.end())
    if p < 0:
        return {}
    body, _ = _paren_content(sql, p)
    if body is None:
        return {}
    items = split_top_level(body, seps=(",",))
    if len(items) != len(cols):
        return {}
    out = {}
    for i, col in enumerate(cols):
        out[col] = _expr_token(items[i], params)
    return out


def _set_map(sql, params):
    """UPDATE 的 SET 段 → {列: token}（同样要传**原始** SQL）。"""
    m = re.search(r"(?i)\bset\s+(.*?)(\bwhere\b|\breturning\b|\border by\b|\blimit\b|$)",
                  sql, re.S)
    if not m:
        return {}
    out = {}
    for assign in split_top_level(m.group(1), seps=(",",)):
        mm = re.match(r"^([\w\.\"]+)\s*=\s*(.+)$", assign, re.S)
        if mm:
            col = mm.group(1).split(".")[-1].strip('"')
            out[col] = _expr_token(mm.group(2), params)
    return out


def default_token(col, table, schema_meta):
    """该列**没被写**时的有效值：DDL 的 DEFAULT / NULL / 「NOT NULL 且无 DEFAULT ⇒ 写不进去」。"""
    meta = (schema_meta.get(table) or {}).get(col) or {}
    d = meta.get("default")
    if d is None:
        if str(meta.get("nullable", "YES")).upper() == "NO":
            # NOT NULL 且无 DEFAULT ⇒ 省略这一列 ⇒ INSERT 会**报错**，不是「写成 NULL」
            return NOTNULL_MISSING
        return NULL_TOKEN
    if NONDET_DEFAULT_RE.search(d):
        return UNDECIDABLE
    return _literal_token(d)


def analyze_write_cols(g, r, schema_meta):
    """写列集差异 ⇒ 逐列判定「落库值是否真的不同」。

    返回 (equal, diff, undecidable)；每项 = (列, Go 值, RAG 值)。
    """
    gv = g.get("write_vals") or {}
    rv = r.get("write_vals") or {}
    g_cols = g.get("write_cols", [])
    r_cols = r.get("write_cols", [])
    only_go = [c for c in g_cols if c not in r_cols]
    only_rag = [c for c in r_cols if c not in g_cols]
    equal, diff, undec = [], [], []
    for col in only_go + only_rag:
        g_tok = gv.get(col)
        if g_tok is None:
            g_tok = default_token(col, g.get("table"), schema_meta)
        r_tok = rv.get(col)
        if r_tok is None:
            r_tok = default_token(col, r.get("table"), schema_meta)
        # 🔴 **不可判定**的情形：某侧有写列但**值没解出来**（如 VALUES 项数与列数不符）
        #   ⇒ 绝不能当成「值不同」（那是**猜**）⇒ 归入 undecidable（报出来让人看）。
        if (g.get("write_cols") and not gv) or (r.get("write_cols") and not rv):
            undec.append((col, g_tok, r_tok))
        elif UNDECIDABLE in (g_tok, r_tok):
            undec.append((col, g_tok, r_tok))
        elif g_tok == r_tok:
            equal.append((col, g_tok, r_tok))
        else:
            diff.append((col, g_tok, r_tok))
    return equal, diff, undec


# ════════════════════════════════════════════════════════════════════════════════
# 🔴 2026-10-26 第二十三轮：L1 响应形状的**归一化**
# ════════════════════════════════════════════════════════════════════════════════

# `c=` / `u=` 后是 **≥2 位**的数字 ⇒ 当作时间戳
L1_TS_RE = re.compile(r"([cu])=(\d{2,})")


def normalize_l1(text):
    """L1 形状里的时间戳按「**有值 / 无值**」分类。

    🔴 **为什么必须分类**：两侧是**两个独立进程** ⇒ 同一时刻的毫秒值**必然不同**
    ⇒ 精确相等**永远不成立** ⇒ 这个信号**恒红** ⇒ 判别不了「修没修」
    ⇒ 第二十轮起它就一直红着（3 例），但那 3 例里**真漂移与非漂移长得一样**。

    ⇒ 真正有语义差别的是「**有没有值**」：
      · Go 侧 GORM 的 autoCreateTime ⇒ 真实毫秒
      · RAG 侧若漏绑 ⇒ NULL ⇒ {@code unixMillis(null)} ⇒ 0
    ⇒ 规则：`c=` / `u=` 后是 **≥2 位的数字** ⇒ `<ts>`；是 `0` ⇒ 保持 `0`（= 没有值）。
    ⚠️ `n=1` 这类计数**不受影响**（正则要求 `c=` / `u=` 前缀）。
    """
    return L1_TS_RE.sub(lambda m: "%s=<ts>" % m.group(1), text)


# ════════════════════════════════════════════════════════════════════════════════
# 🔴 2026-10-26 第二十三轮：**人工判定的等价台账**（投影差异）
#
# 「Go 多投影了一列」**不必然**是行为差异 —— 取决于**下游用不用这一列**。
# 这件事**探针机械判不了**（它看不到应用代码）⇒ 不能让探针自己猜。
# ⇒ 走**带出处的显式登记**：每条都要指到 Go 真源的具体行，且**出处会被校验**
#    （文件不存在 / 行号越界 ⇒ 该条**作废** ⇒ 差异照旧报成 SEMANTIC）。
# ⇒ 这样「把红改成绿」必须**拿出证据**，而不是改个开关。
# ════════════════════════════════════════════════════════════════════════════════

EQUIV_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "equivalences.tsv")


def load_equivalences(path=None, go_root=None):
    """读等价台账 ⇒ {(case_id, dim, table, column): {anchor, reason, ok, why}}。"""
    p = path or EQUIV_FILE
    go_root = go_root or os.environ.get("GO_REPO", "")
    out = OrderedDict()
    if not os.path.exists(p):
        return out
    with open(p, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 6:
                parts = line.split("|")
            if len(parts) < 6:
                continue
            cid, dim, table, column, anchor, reason = [x.strip() for x in parts[:6]]
            ok, why = verify_anchor(go_root, anchor)
            out[(cid, dim, table, column)] = {
                "anchor": anchor, "reason": reason, "ok": ok, "why": why,
            }
    return out


def adj_lookup(case_id, dim, table, column):
    """查等价台账 ⇒ 先精确匹配用例，再退到 `*`（与用例无关的判定）。"""
    e = ADJ.get((case_id, dim, table, column))
    if e is None:
        e = ADJ.get(("*", dim, table, column))
    return e


def verify_anchor(go_root, anchor):
    """校验 Go 出处：文件存在 + 行号在范围内 ⇒ (ok, why)。"""
    if not anchor or ":" not in anchor:
        return False, "出处格式不对（要 <file>.go:<行号或行号区间>）"
    rel, _, rng = anchor.rpartition(":")
    if not rel.endswith(".go"):
        return False, "出处不是 .go 文件"
    if not go_root:
        return False, "未提供 Go 真源根目录（GO_REPO）⇒ 无法校验出处"
    path = os.path.join(go_root, rel)
    if not os.path.exists(path):
        return False, "出处文件不存在：%s" % rel
    n = sum(1 for _ in open(path, "r", encoding="utf-8", errors="replace"))
    lo = rng.split("-")[0].strip()
    try:
        lo_i = int(lo)
    except ValueError:
        return False, "出处行号不可解析：%s" % rng
    if lo_i < 1 or lo_i > n:
        return False, "出处行号越界（%s 共 %d 行）" % (lo, n)
    return True, "出处已校验（%s，共 %d 行）" % (rel, n)


def signature(sql, schema, params=None):
    """把一条 SQL 变成语义签名（dict）。"""
    s = normalize(sql)

    sig = {
        "kind": "OTHER",
        "table": None,
        "write_cols": [],
        "write_vals": {},
        "where_cols": [],
        "lock": None,
        "order_by": [],
        "limit": None,
        "offset": None,
        "joins": [],
        "returning": [],
        "projection": None,
    }

    # ── 语句类别 ──
    if s in ("begin", "begin transaction", "start transaction"):
        sig["kind"] = "TXN_BEGIN"
        return sig
    if s in ("commit", "end", "commit transaction"):
        sig["kind"] = "TXN_COMMIT"
        return sig
    if s.startswith("rollback"):
        sig["kind"] = "TXN_ROLLBACK"
        return sig
    if s.startswith("set "):
        sig["kind"] = "SESSION_SET"
        return sig
    if s.startswith("insert into"):
        sig["kind"] = "INSERT"
    elif s.startswith("update "):
        sig["kind"] = "UPDATE"
    elif s.startswith("delete from"):
        sig["kind"] = "DELETE"
    elif s.startswith("select"):
        sig["kind"] = "SELECT"
    elif s.startswith("with "):
        sig["kind"] = "CTE"
    else:
        sig["kind"] = "OTHER"

    # ── 主表 ──
    m = re.match(r"^insert into\s+([\w.\"]+)", s)
    if m:
        sig["table"] = m.group(1).split(".")[-1]
    m = re.match(r"^update\s+([\w.\"]+)", s)
    if m:
        sig["table"] = m.group(1).split(".")[-1]
    m = re.match(r"^delete from\s+([\w.\"]+)", s)
    if m:
        sig["table"] = m.group(1).split(".")[-1]
    m = re.search(r"\bfrom\s+([\w.\"]+)", s)
    if m and sig["table"] is None:
        sig["table"] = m.group(1).split(".")[-1]

    # ── JOIN 表集 ──
    sig["joins"] = sorted(set(re.findall(r"\bjoin\s+([\w.\"]+)", s)))

    # ── 投影（SELECT）──
    if sig["kind"] == "SELECT":
        m = re.match(r"^select\s+(.*?)\s+from\s", s, re.S)
        if m:
            proj = m.group(1).strip()
            sig["projection"] = [c.strip() for c in proj.split(",")] if proj != "*" else ["*"]
        else:
            sig["projection"] = []

    # ── 写入列集 ──
    if sig["kind"] == "INSERT":
        m = re.match(r"^insert into\s+[\w.]+\s*\(([^)]*)\)", s, re.S)
        if m:
            sig["write_cols"] = [c.strip() for c in m.group(1).split(",") if c.strip()]
            # 🔴 第二十三轮：同时抓**写入值**（否则只能比列集 ⇒ 分不清「值相同」）
            #   ⚠️ 传**原始** sql（normalize 把 $N 改成了 ?）
            sig["write_vals"] = _values_map(sql, sig["write_cols"], params or {})
    elif sig["kind"] == "UPDATE":
        m = re.search(r"\bset\s+(.*?)(\bwhere\b|$)", s, re.S)
        if m:
            cols = []
            for assign in split_top_level(m.group(1), seps=(",",)):
                assign = assign.strip()
                mm = re.match(r"^([\w.\"]+)\s*=", assign)
                if mm:
                    cols.append(mm.group(1).split(".")[-1])
            sig["write_cols"] = cols
            # ⚠️ 传**原始** sql（normalize 把 $N 改成了 ?）
            sig["write_vals"] = _set_map(sql, params or {})

    # ── WHERE ──
    m = re.search(r"\bwhere\b(.*?)(\border by\b|\blimit\b|\boffset\b|\bfor update\b|\bfor share\b|$)",
                  s, re.S)
    if m:
        sig["where_cols"] = extract_where_cols(m.group(1))

    # ── 锁子句 ──
    for lock in ("for no key update", "for key share", "for update", "for share"):
        if re.search(r"\b" + lock + r"\b", s):
            sig["lock"] = lock
            break

    # ── ORDER BY ──
    m = re.search(r"\border by\s+(.*?)(\blimit\b|\boffset\b|\bfor update\b|\bfor share\b|$)", s, re.S)
    if m:
        cols = []
        for item in m.group(1).split(","):
            item = item.strip()
            item = re.sub(r"\s+(asc|desc)$", "", item)
            cols.append(item.split(".")[-1])
        sig["order_by"] = cols

    # ── LIMIT / OFFSET（**用绑定值解析**，见 resolve_count 的说明）──
    sig["limit"] = resolve_count(sql, params, "limit")
    sig["offset"] = resolve_count(sql, params, "offset")

    # ── RETURNING ──
    m = re.search(r"\breturning\s+(.*?)(\bwhere\b|\border by\b|\blimit\b|$)", s, re.S)
    if m:
        sig["returning"] = [c.strip() for c in m.group(1).split(",") if c.strip()]

    return sig


# ════════════════════════════════════════════════════════════════════════════════
# 比对
# ════════════════════════════════════════════════════════════════════════════════

def expand_projection(proj, table, schema):
    """SELECT * ⇒ 展开成真实列清单（用表结构快照）。"""
    if proj == ["*"]:
        return list(schema.get(table, []))
    return list(proj)


def order_by_equivalent(go_sig, rag_sig, schema):
    """ORDER BY 差异是否「已证明等价」。

    判据：一侧是空、另一侧只按**主键**排序，且该语句的 WHERE **钉住了某个唯一键**
    ⇒ 加不加这个 ORDER BY 都只会取到同一行 ⇒ 等价（ORM-GENERIC）。
    """
    # ⚠️ 只有「**一侧有、一侧没有**」才可能是 GORM 的隐式 ORDER BY。
    #    两侧都有 / 两侧都没有 ⇒ 不是这类差异（下面的调用方已单独处理「都空」）。
    if bool(go_sig["order_by"]) == bool(rag_sig["order_by"]):
        return False
    with_order = go_sig if go_sig["order_by"] else rag_sig
    other = rag_sig if go_sig["order_by"] else go_sig
    table = with_order["table"]
    cols = schema.get("__pk__", {}).get(table)
    if not cols:
        return False
    if with_order["order_by"] != list(cols):
        return False
    # WHERE 必须钉住一个唯一键（否则 ORDER BY 会改变「取哪一行」）
    uniques = schema.get("__uniq__", {}).get(table, [])
    where = set(with_order["where_cols"])
    for uc in uniques:
        if set(uc).issubset(where):
            return True
    return False


# ════════════════════════════════════════════════════════════════════════════════
# 噪声剥离（**两侧对称**）
# ════════════════════════════════════════════════════════════════════════════════

def as_stmt(s):
    """兼容两种输入：裸 SQL 字符串 / {"sql":…, "params":…}。"""
    if isinstance(s, dict):
        return s.get("sql", ""), s.get("params", {}) or {}
    return s, {}


# 🔴 噪声清单（**不是** favorite 域的行为 ⇒ 不该进语义比对）：
#   ① `-- …`  —— GORM 的连接 ping（database/sql 新建连接时发一次）
#   ② `SHOW …` —— 会话查询
#   ③ `SET application_name = …` —— pgjdbc 的连接参数回显
NOISE_RULES = [
    (re.compile(r"^--"), "ORM 注释语句（GORM 的连接 ping）"),
    (re.compile(r"^show\s"), "会话查询"),
    (re.compile(r"^set\s+application_name"), "连接参数回显（pgjdbc）"),
]


def strip_noise(stmts):
    """剥掉噪声语句 ⇒ 返回 (保留的, 噪声清单)。"""
    kept, notes = [], []
    for s in stmts:
        sql, params = as_stmt(s)
        n = normalize(sql)
        why = None
        for pat, reason in NOISE_RULES:
            if pat.match(n):
                why = reason
                break
        if why:
            notes.append((sql.strip().replace("\n", " ")[:90], why))
            continue
        kept.append({"sql": sql, "params": params})
    return kept, notes


def is_txn_begin(s):
    n = normalize(as_stmt(s)[0])
    return n in ("begin", "begin transaction", "start transaction")


def is_txn_commit(s):
    n = normalize(as_stmt(s)[0])
    return n in ("commit", "end", "commit transaction")


def unwrap_single_stmt_txn(stmts):
    """拆掉「只包一条语句」的 BEGIN/COMMIT。

    🔴 **为什么**：GORM 默认给 Create/Update/Delete 套一层事务
       （`gorm.Config.SkipDefaultTransaction` 默认 false）
       ⇒ Go 侧的单条写是 `BEGIN → INSERT → COMMIT`，RAG 侧手写 mapper 是**自动提交**的单条 INSERT。
       ⚠️ 但**只包一条**的事务与自动提交在原子性上**等价** ⇒ 归 ORM-GENERIC。
    ⚠️ **包了两条以上**的事务**不能拆** —— 那才是真正的原子性边界 ⇒ 保留、参与比对。
    """
    out, notes = [], []
    i = 0
    while i < len(stmts):
        if (is_txn_begin(stmts[i]) and i + 2 < len(stmts) and is_txn_commit(stmts[i + 2])
                and not is_txn_begin(stmts[i + 1]) and not is_txn_commit(stmts[i + 1])):
            notes.append(("BEGIN … COMMIT",
                          "GORM 默认事务：BEGIN/COMMIT 只包了**一条**语句 ⇒ 与自动提交**原子性等价**"))
            # ⚠️ **必须保留中间那条语句**（只丢 BEGIN/COMMIT 两行）。
            #    早先写成 i += 3 把中间那条一起丢了 ⇒ Go 侧的 INSERT/UPDATE
            #    （正好在 GORM 默认事务里）凭空消失 ⇒ 报「RAG 多一条」⇒ **假阳性**。
            out.append(stmts[i + 1])
            i += 3
            continue
        out.append(stmts[i])
        i += 1
    return out, notes


def compare_case(case_id, go_sqls, rag_sqls, schema):
    """比一个用例的两侧语句序列 ⇒ 返回差异清单。"""
    diffs = []

    go_kept, go_noise = strip_noise(go_sqls)
    rag_kept, rag_noise = strip_noise(rag_sqls)
    go_kept, go_txn = unwrap_single_stmt_txn(go_kept)
    rag_kept, rag_txn = unwrap_single_stmt_txn(rag_kept)

    # ── 噪声与「已拆掉的默认事务」也要**留痕**（归 ORM-GENERIC 档）──
    # ⚠️ 留痕而不是静默丢弃：静默丢弃会让「比对器把差异藏起来了」看不出来。
    for side, notes in (("go", go_noise + go_txn), ("rag", rag_noise + rag_txn)):
        for what, why in notes:
            diffs.append({
                "dim": "noise",
                "go": what if side == "go" else "-",
                "rag": what if side == "rag" else "-",
                "cls": ORM_GENERIC,
                "note": "已剥离的%s噪声：%s" % (side.upper(), why),
            })

    go_sigs = [signature(s["sql"], schema, s["params"]) for s in go_kept]
    rag_sigs = [signature(s["sql"], schema, s["params"]) for s in rag_kept]

    # ── 条数不同 ──
    if len(go_sigs) != len(rag_sigs):
        diffs.append({
            "dim": "statement_count",
            "go": str(len(go_sigs)),
            "rag": str(len(rag_sigs)),
            "cls": SEMANTIC,
            "note": "两侧对数据库说的话**条数**不同 ⇒ 过程不同",
        })

    # ── 逐条对齐（按下标；条数不同时多出来的记在末尾）──
    for i in range(min(len(go_sigs), len(rag_sigs))):
        g, r = go_sigs[i], rag_sigs[i]
        for dim in ("kind", "table", "lock", "limit", "offset"):
            if g[dim] != r[dim]:
                diffs.append({
                    "dim": dim,
                    "idx": i,
                    "go": str(g[dim]),
                    "rag": str(r[dim]),
                    "cls": SEMANTIC,
                    "note": {
                        "kind": "语句类别不同",
                        "table": "主表不同",
                        "lock": "**行锁语义不同**（FOR UPDATE / FOR SHARE / 无）",
                        "limit": "LIMIT 不同",
                        "offset": "OFFSET 不同",
                    }[dim],
                })
        for dim in ("write_cols", "where_cols", "joins"):
            if sorted(g[dim]) != sorted(r[dim]):
                only_go = sorted(set(g[dim]) - set(r[dim]))
                only_rag = sorted(set(r[dim]) - set(g[dim]))
                if dim == "write_cols":
                    # 🔴 第二十三轮：列集不同**不等于**落库值不同 ⇒ 逐列算**有效值**
                    #   （写了 ⇒ 取绑定的值；没写 ⇒ 取 DDL 的 DEFAULT / NULL）
                    eq, df, un = analyze_write_cols(g, r, schema.get("__meta__", {}))
                    if df:
                        items = ["%s（Go=%s ｜ RAG=%s）" % (c, gt, rt) for c, gt, rt in df]
                        diffs.append({
                            "dim": dim,
                            "idx": i,
                            "go": ",".join(sorted(g[dim])) or "-",
                            "rag": ",".join(sorted(r[dim])) or "-",
                            "cls": SEMANTIC,
                            "note": "写入列集不同（仅 Go: %s ｜ 仅 RAG: %s）⇒ **落库值真的不同**：%s"
                                    % (",".join(only_go) or "无", ",".join(only_rag) or "无",
                                       "；".join(items)),
                        })
                    if un:
                        items = ["%s（Go=%s ｜ RAG=%s）" % (c, gt, rt) for c, gt, rt in un]
                        diffs.append({
                            "dim": dim,
                            "idx": i,
                            "go": ",".join(sorted(g[dim])) or "-",
                            "rag": ",".join(sorted(r[dim])) or "-",
                            "cls": UNDECIDABLE_CLS,
                            "note": "写入列集不同（仅 Go: %s ｜ 仅 RAG: %s）⇒ **落库值不可判定**"
                                    "（需人工核）：%s"
                                    % (",".join(only_go) or "无", ",".join(only_rag) or "无",
                                       "；".join(items)),
                        })
                    if not df and not un:
                        items = ["%s=%s" % (c, gt) for c, gt, rt in eq]
                        diffs.append({
                            "dim": dim,
                            "idx": i,
                            "go": ",".join(sorted(g[dim])) or "-",
                            "rag": ",".join(sorted(r[dim])) or "-",
                            "cls": EQUIVALENT,
                            "note": "写入列集不同（仅 Go: %s ｜ 仅 RAG: %s）⇒ **落库值逐列相同**"
                                    "（%s）⇒ 等价"
                                    % (",".join(only_go) or "无", ",".join(only_rag) or "无",
                                       "；".join(items) or "无差异列"),
                        })
                    continue
                diffs.append({
                    "dim": dim,
                    "idx": i,
                    "go": ",".join(sorted(g[dim])) or "-",
                    "rag": ",".join(sorted(r[dim])) or "-",
                    "cls": SEMANTIC,
                    "note": {
                        "where_cols": "过滤列集不同（仅 Go: %s ｜ 仅 RAG: %s）"
                                      % (",".join(only_go) or "无", ",".join(only_rag) or "无"),
                        "joins": "JOIN 的表集不同",
                    }[dim],
                })
        # ORDER BY
        if g["order_by"] != r["order_by"]:
            if order_by_equivalent(g, r, schema):
                diffs.append({
                    "dim": "order_by",
                    "idx": i,
                    "go": ",".join(g["order_by"]) or "-",
                    "rag": ",".join(r["order_by"]) or "-",
                    "cls": ORM_GENERIC,
                    "note": "GORM 的 First 隐式加 ORDER BY <主键>；"
                            "本语句的 WHERE 已钉住唯一键 ⇒ **等价**",
                })
            else:
                diffs.append({
                    "dim": "order_by",
                    "idx": i,
                    "go": ",".join(g["order_by"]) or "-",
                    "rag": ",".join(r["order_by"]) or "-",
                    "cls": SEMANTIC,
                    "note": "排序不同 ⇒ **返回顺序可能不同**（L1 可观察）",
                })
        # 投影
        if g["projection"] is not None and r["projection"] is not None:
            gp = expand_projection(g["projection"], g["table"], schema)
            rp = expand_projection(r["projection"], r["table"], schema)
            if sorted(gp) != sorted(rp):
                only_go = sorted(set(gp) - set(rp))
                only_rag = sorted(set(rp) - set(gp))
                # 🔴 第二十三轮：多投影一列**不必然**是行为差异（取决于下游用不用）
                #   ⇒ 走**带出处的显式登记**；出处校验不过 ⇒ 登记作废 ⇒ 仍报 SEMANTIC。
                adj_go = [(c, adj_lookup(case_id, "projection", g["table"], c))
                          for c in only_go]
                adj_rag = [(c, adj_lookup(case_id, "projection", r["table"], c))
                           for c in only_rag]
                all_adj = all(e and e.get("ok") for _c, e in adj_go + adj_rag)
                if all_adj and (adj_go or adj_rag):
                    why = "；".join("%s ⇒ %s（Go 出处 %s）"
                                    % (c, e["reason"], e["anchor"])
                                    for c, e in adj_go + adj_rag if e)
                    diffs.append({
                        "dim": "projection",
                        "idx": i,
                        "go": ",".join(gp),
                        "rag": ",".join(rp),
                        "cls": EQUIVALENT,
                        "note": "投影列集不同（仅 Go: %s ｜ 仅 RAG: %s）⇒ **已人工判定等价**：%s"
                                % (",".join(only_go) or "无", ",".join(only_rag) or "无", why),
                    })
                else:
                    bad = ["%s（%s）" % (c, e["why"] if e else "无登记")
                           for c, e in adj_go + adj_rag if not (e and e.get("ok"))]
                    diffs.append({
                        "dim": "projection",
                        "idx": i,
                        "go": ",".join(gp),
                        "rag": ",".join(rp),
                        "cls": SEMANTIC,
                        "note": "投影列集不同（仅 Go: %s ｜ 仅 RAG: %s）%s"
                                % (",".join(only_go) or "无", ",".join(only_rag) or "无",
                                   ("⇒ 未判定等价的列：%s" % "；".join(bad)) if bad else ""),
                    })
        # RETURNING
        if g["returning"] != r["returning"]:
            diffs.append({
                "dim": "returning",
                "idx": i,
                "go": ",".join(g["returning"]) or "-",
                "rag": ",".join(r["returning"]) or "-",
                "cls": ORM_GENERIC,
                "note": "RETURNING 子句不同（自增代理键，域内不使用）",
            })

    return diffs


# ════════════════════════════════════════════════════════════════════════════════
# 报告
# ════════════════════════════════════════════════════════════════════════════════

def kind_seq(sigs):
    short = {
        "TXN_BEGIN": "BEGIN", "TXN_COMMIT": "COMMIT", "TXN_ROLLBACK": "ROLLBACK",
        "SELECT": "SELECT", "INSERT": "INSERT", "UPDATE": "UPDATE", "DELETE": "DELETE",
        "SESSION_SET": "SET", "CTE": "CTE", "OTHER": "OTHER",
    }
    return " → ".join(short.get(s["kind"], s["kind"]) for s in sigs)


def render_report(data, cases_tsv, out_md):
    schema = data["schema"]
    cases = data["cases"]
    lines = []
    A = lines.append

    A("# SQL 语句层 · 双侧活体对拍报告（favorite 域）")
    A("")
    A("> 取证：**PG 服务端日志**（`log_statement=all`）⇒ 两侧**零改动**，看到的是真发出去的 SQL。")
    A("> 分流：`db` + `application_name`（Go 侧 `PGAPPNAME=go_side` / RAG 侧 JDBC `ApplicationName=rag_side`）。")
    A("> 分段：驱动每例前发 `SQLMARK::<side>::<case>::<seq>` ⇒ 逐用例切片。")
    A("")
    A("## 0. 采集统计")
    A("")
    st = data["stats"]
    A("| 项 | 值 |")
    A("|---|---|")
    A("| 日志行数 | %d |" % st["log_lines"])
    A("| 日志记录数 | %d |" % st["records"])
    A("| 语句记录数 | %d |" % st["statement_records"])
    A("| DETAIL 记录数（扩展协议参数） | %d |" % st["detail_records"])
    A("| marker 语句数 | %d |" % st["marker_statements"])
    A("| parse/bind 记录数（PG16 实测为 0） | %d |" % st["parse_or_bind_records"])
    for name in ("go", "rag"):
        s = data["sides"][name]
        A("| %s 侧语句数（db=%s, app=%s） | %d |" % (name, s["db"], s["app"], s["total_statements"]))
    A("")

    # 业务出口 + L1 响应形状
    outcomes = OrderedDict()
    l1_rows = []
    if cases_tsv:
        with open(cases_tsv, "r", encoding="utf-8") as fh:
            for line in fh:
                line = line.rstrip("\n")
                if not line.strip():
                    continue
                parts = line.split("\t")
                if len(parts) >= 3:
                    outcomes[parts[0]] = (parts[1], parts[2])
                if len(parts) >= 5 and parts[3] != "-":
                    l1_rows.append((parts[0], parts[3], parts[4]))
    if outcomes:
        A("## 1. 两侧业务出口（必须一致，否则 SQL 层不可比）")
        A("")
        A("| 用例 | Go | RAG |")
        A("|---|---|---|")
        for cid, (g, r) in outcomes.items():
            flag = "" if g == r else " 🔴 **不一致**"
            A("| `%s` | %s | %s%s |" % (cid, g, r, flag))
        A("")
    if l1_rows:
        A("## 1b. L1 响应形状（**屏蔽雪花 ID**；时间戳按「**有值 / 无值**」分类 ⇒ 跨进程可比）")
        A("")
        A("> 🔴 **为什么时间戳要分类**（第二十三轮改）：两侧是**两个独立进程** ⇒ 同一时刻的毫秒值")
        A("> **必然不同** ⇒ 精确相等**永远不成立** ⇒ 这个信号**恒红** ⇒ 判别不了「修没修」")
        A("> ⇒ 真正有语义差别的是「**有没有值**」：Go 侧 GORM 的 autoCreateTime 给真实毫秒；")
        A("> RAG 侧若漏绑 ⇒ NULL ⇒ `unixMillis(null)` ⇒ **0**。")
        A("> ⇒ 规则：`c=` / `u=` 后是 **≥2 位的数字** ⇒ `<ts>`；是 `0` ⇒ 保持 `0`（= 没有值）。")
        A("")
        A("| 用例 | Go | RAG | 一致 |")
        A("|---|---|---|---|")
        l1_diff = 0
        for cid, g, r in l1_rows:
            ng, nr = normalize_l1(g), normalize_l1(r)
            same = (ng == nr)
            if not same:
                l1_diff += 1
            verdict = "✅" if same else "🔴 **不同**（归一化：Go `%s` vs RAG `%s`）" % (ng, nr)
            A("| `%s` | %s | %s | %s |" % (cid, g, r, verdict))
        A("")
        A("⇒ **L1 响应不同：%d 例**（这是「SQL 层差异」对外可观察的直接证据）" % l1_diff)
        A("")

    # 逐用例
    A("## 2. 逐用例语句序列")
    A("")
    A("| 用例 | Go 条数 | RAG 条数 | Go 序列 | RAG 序列 | 差异 |")
    A("|---|---|---|---|---|---|")
    per_case_diffs = OrderedDict()
    for cid in sorted(cases.keys()):
        go_sqls = cases[cid].get("go", [])
        rag_sqls = cases[cid].get("rag", [])
        d = compare_case(cid, go_sqls, rag_sqls, schema)
        per_case_diffs[cid] = d
        sem = len([x for x in d if x["cls"] == SEMANTIC])
        og = len([x for x in d if x["cls"] == ORM_GENERIC])
        und = len([x for x in d if x["cls"] == UNDECIDABLE_CLS])
        # 表里显示的是**剥离噪声后**的条数 ⇒ 与差异明细的口径一致
        go_clean, _ = strip_noise(go_sqls)
        rag_clean, _ = strip_noise(rag_sqls)
        go_clean, _ = unwrap_single_stmt_txn(go_clean)
        rag_clean, _ = unwrap_single_stmt_txn(rag_clean)
        gs = kind_seq([signature(s["sql"], schema, s["params"]) for s in go_clean])
        rs = kind_seq([signature(s["sql"], schema, s["params"]) for s in rag_clean])
        mark = ""
        if sem:
            mark = "🔴 **%d 处语义差异**" % sem
        elif und:
            mark = "🟠 %d 处**落库值不可判定**（需人工核）" % und
        elif og:
            mark = "🟡 %d 处 ORM 噪声" % og
        else:
            mark = "✅ 一致"
        A("| `%s` | %d | %d | %s | %s | %s |" % (cid, len(go_clean), len(rag_clean), gs, rs, mark))
    A("")

    # 差异明细
    A("## 3. 差异明细")
    A("")
    total_sem = 0
    total_og = 0
    total_und = 0
    for cid, d in per_case_diffs.items():
        if not d:
            continue
        A("### `%s`" % cid)
        A("")
        A("| 维度 | Go | RAG | 分档 | 说明 |")
        A("|---|---|---|---|---|")
        for x in d:
            idx = x.get("idx")
            idxs = ("#%d " % idx) if idx is not None else ""
            A("| %s%s | `%s` | `%s` | %s | %s |"
              % (idxs, x["dim"], x["go"], x["rag"], x["cls"], x["note"]))
            if x["cls"] == SEMANTIC:
                total_sem += 1
            elif x["cls"] == UNDECIDABLE_CLS:
                total_und += 1
            else:
                total_og += 1
        A("")
    if total_sem == 0 and total_og == 0 and total_und == 0:
        A("（无差异）")
        A("")

    A("## 4. 结论")
    A("")
    A("- **语义差异（SEMANTIC）：%d 处**" % total_sem)
    A("- **落库值不可判定（UNDECIDABLE）：%d 处**" % total_und)
    A("- **ORM 生成噪声 / 已证明等价（ORM-GENERIC / EQUIVALENT）：%d 处**" % total_og)
    A("- **L1 响应不同：%d 例**" % l1_diff)
    A("")
    if total_sem:
        A("🔴 存在语义差异 ⇒ **必须逐条人工核证**（可能是真缺陷，也可能需要登记为有意偏离）。")
    else:
        A("✅ 两侧在**语句层**的语义签名一致（锁 / 事务边界 / WHERE / 写集合 / 投影 / 排序）。")
    if total_und:
        A("")
        A("🟠 有 **%d 处「落库值不可判定」** ⇒ 不是「已证明等价」，是**机械判不了** ⇒ 需人工核。" % total_und)
    if l1_diff:
        A("")
        A("🔴 **L1 响应已经不同** ⇒ 上面的 SQL 差异**对外可观察**，不是「内部实现细节」。")
    A("")

    # 附录：原始 SQL
    A("## 附录 A. 原始 SQL（逐用例）")
    A("")
    A("> ⚠️ 绑定参数（`params`）是**扩展协议 DETAIL 行**里的真实值 ⇒ 人工核证时一并看。")
    A("> 两侧的**雪花 ID 必然不同** ⇒ 参数值里的 ID 属正常，不是差异。")
    A("")
    for cid in sorted(cases.keys()):
        A("### `%s`" % cid)
        A("")
        for side in ("go", "rag"):
            A("**%s 侧**" % side.upper())
            A("")
            A("```sql")
            for i, s in enumerate(cases[cid].get(side, [])):
                sql, params = as_stmt(s)
                A("-- [%d]%s" % (i, ("  -- params: %s" % params) if params else ""))
                A(sql.rstrip())
            A("```")
            A("")

    with open(out_md, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return total_sem, total_og, l1_diff


# ════════════════════════════════════════════════════════════════════════════════
# 变异自检（防假绿）
# ════════════════════════════════════════════════════════════════════════════════

def _split_insert(sql):
    """INSERT 语句拆成部件（用于**保对齐**地删列/删值）。

    返回 (head, cols_text, mid, vals_text, tail) 或 None。
    ⚠️ 必须在**原串**上算好所有偏移再重组 ⇒ 否则两次替换的偏移会互相错位
       （实测踩过：拼出了 `...("create_time")) VALUES ($1,$2,$3,$$1, $2, $3, $4)G "id"`）。
    """
    m = re.match(r"(?is)^(?P<head>insert into\s+[\w.\"]+\s*\()"
                 r"(?P<cols>[^)]*)(?P<cmid>\)\s*values\s*\()"
                 r"(?P<vals>[^)]*)(?P<tail>\).*)$", sql)
    if not m:
        return None
    return (m.group("head"), m.group("cols"), m.group("cmid"),
            m.group("vals"), m.group("tail"))


def _rebuild_insert(parts, new_cols, new_items):
    head, _cols, cmid, _vals, tail = parts
    return (head + ", ".join(new_cols) + cmid + ", ".join(new_items) + tail)


def mutate(data, kind):
    """对抽取结果做**定向变异** ⇒ 期望比对器必须抓到（否则比对器是假绿）。

    ⚠️ 记录格式是 {"sql":…, "params":{…}} ⇒ 变异要**改 sql 并保留 params**。
    ⚠️ 目标用例**不能按字母序取第一个** —— 第一个用例可能根本没有锁或 INSERT
       ⇒ 变异「找不到目标」⇒ 自检变成空跑（看起来通过，其实什么都没验）。
       ⇒ 这里**遍历所有用例**，挑第一个**真的含有目标语句**的。
    """
    d = json.loads(json.dumps(data))
    cases = d["cases"]

    for cid in sorted(cases.keys()):
        go_list = cases[cid]["go"]
        if kind == "drop_lock":
            for i, rec in enumerate(go_list):
                if "for update" in rec["sql"].lower():
                    rec["sql"] = re.sub(r"(?i)\s*FOR UPDATE", "", rec["sql"])
                    return d, "lock", cid
        elif kind == "drop_write_col":
            # 🔴 **2026-10-26 第二十三轮改**：变异必须**保持对齐**。
            #   旧变异只删「列清单」的第一项、不删 VALUES ⇒ 列数与值数**错位**
            #   ⇒ 新版比对器（按**有效值**判定）会判成「不可判定」⇒ **抓不到** ⇒ 假绿。
            #   ⇒ 现在删**最后一列 + 它的值**（位置对应 ⇒ 剩余仍对齐）
            #   ⇒ 并且**挑一个「可空且无 DEFAULT」的末列**（⇒ 该列的有效值 = NULL，
            #      与对侧的真实值**必然不同** ⇒ 比对器必须报 SEMANTIC）。
            meta = d["schema"].get("__meta__", {})
            for i, rec in enumerate(go_list):
                sql = rec["sql"]
                if not sql.strip().lower().startswith("insert into"):
                    continue
                parts = _split_insert(sql)
                if not parts:
                    continue
                # ⚠️ Go 的列名带双引号（"technical_offset"）⇒ 查 DDL 元信息前要剥掉
                cols = [c.strip().strip('"') for c in parts[1].split(",") if c.strip()]
                items = [x for x in split_top_level(parts[3], seps=(",",)) if x.strip()]
                if len(cols) < 2 or len(items) != len(cols):
                    continue
                last = cols[-1]
                table_m = re.match(r"(?i)insert into\s+([\w.\"]+)", sql)
                table = table_m.group(1).split(".")[-1].strip('"') if table_m else ""
                col_meta = (meta.get(table) or {}).get(last) or {}
                if col_meta.get("default") is not None:
                    continue
                if str(col_meta.get("nullable", "YES")).upper() != "YES":
                    continue
                # 删末列 + 删末值（**保对齐** ⇒ 用整条重组，别做偏移手术）
                rec["sql"] = _rebuild_insert(parts, cols[:-1], items[:-1])
                return d, "write_cols", cid
        elif kind == "extra_stmt":
            go_list.append({"sql": "SELECT 1", "params": {}})
            return d, "statement_count", cid
        elif kind == "where_col":
            for i, rec in enumerate(go_list):
                if " where " in rec["sql"].lower():
                    rec["sql"] = re.sub(r"(?i)\bwhere\b(\s+)(\w+)\s*=", r"WHERE\1zzz_probe_col =",
                                        rec["sql"], count=1)
                    return d, "where_cols", cid
        elif kind == "noop_projection":
            # 🔴 **阴性对照**：只把 `SELECT *` 换成**等价的显式列清单**，
            #    **其余部分（WHERE / ORDER BY / LIMIT）一个字都不动**
            #    ⇒ 期望比对器**不得**报语义差异。
            for i, rec in enumerate(go_list):
                m = re.match(r"(?i)^(\s*)select\s+\*\s+from\s+([\w.\"]+)", rec["sql"])
                if not m:
                    continue
                table = m.group(2).replace('"', "")
                cols = d["schema"].get(table)
                if not cols:
                    continue
                rec["sql"] = re.sub(r"(?i)^(\s*)select\s+\*", r"\1SELECT " + ", ".join(cols),
                                    rec["sql"], count=1)
                return d, None, cid
        elif kind == "drop_write_col_with_default":
            # 🔴 **2026-10-26 第二十三轮新增的阴性对照**：
            #   删掉一个「**有确定性 DEFAULT**」的列（如 technical_offset 的 DEFAULT 0）
            #   ⇒ 该列在两侧的**有效值相同**（一侧显式写 0、另一侧由 DEFAULT 补 0）
            #   ⇒ 比对器**不得**报语义差异。
            #   ⇒ 它证明的是：「DDL 感知的等价判定**真的在工作**」，
            #      而不是「把差异全吃掉」（否则前面的阳性变异就毫无意义）。
            meta = d["schema"].get("__meta__", {})
            for i, rec in enumerate(go_list):
                sql = rec["sql"]
                if not sql.strip().lower().startswith("insert into"):
                    continue
                parts = _split_insert(sql)
                if not parts:
                    continue
                # ⚠️ Go 的列名带双引号（"technical_offset"）⇒ 查 DDL 元信息前要剥掉
                cols = [c.strip().strip('"') for c in parts[1].split(",") if c.strip()]
                table_m = re.match(r"(?i)insert into\s+([\w.\"]+)", sql)
                table = table_m.group(1).split(".")[-1].strip('"') if table_m else ""
                items = [x for x in split_top_level(parts[3], seps=(",",)) if x.strip()]
                if len(items) != len(cols):
                    continue
                hit = -1
                for idx, c in enumerate(cols):
                    cmeta = (meta.get(table) or {}).get(c) or {}
                    dflt = cmeta.get("default")
                    if dflt and not NONDET_DEFAULT_RE.search(str(dflt)):
                        hit = idx
                        break
                if hit < 0:
                    continue
                new_cols = cols[:hit] + cols[hit + 1:]
                new_items = items[:hit] + items[hit + 1:]
                rec["sql"] = _rebuild_insert(parts, new_cols, new_items)
                return d, None, cid
    return None, None, None


def run_mutation_test(data, schema):
    print("[mutation-test] 变异自检：每个变异都必须被抓到（阴性对照除外）")
    failures = []
    for kind, expect in (("drop_lock", "lock"),
                         ("drop_write_col", "write_cols"),
                         ("extra_stmt", "statement_count"),
                         ("where_col", "where_cols"),
                         ("noop_projection", None),
                         ("drop_write_col_with_default", None)):
        mutated, expect_dim, cid = mutate(data, kind)
        if mutated is None:
            print("  ⚠️ %-16s ⇒ 全部用例里都找不到可变异的语句（**自检没跑起来**）" % kind)
            failures.append((kind, "无可变异目标"))
            continue
        d = compare_case(cid, mutated["cases"][cid].get("go", []),
                         mutated["cases"][cid].get("rag", []), schema)
        sem = [x for x in d if x["cls"] == SEMANTIC]
        dims = set(x["dim"] for x in sem)
        if expect is None:
            if sem:
                print("  ❌ %-16s ⇒ 阴性对照**报了**语义差异：%s（用例 %s）"
                      % (kind, ",".join(sorted(dims)), cid))
                failures.append((kind, "阴性对照误报"))
            else:
                print("  ✅ %-16s ⇒ 阴性对照正确（用例 %s，只报 ORM 噪声 %d 处）"
                      % (kind, cid, len(d) - len(sem)))
        else:
            if expect_dim in dims:
                print("  ✅ %-16s ⇒ 抓到 %s 维度的语义差异（用例 %s）" % (kind, expect_dim, cid))
            else:
                print("  ❌ %-16s ⇒ **没抓到** %s 维度（实际抓到：%s，用例 %s）"
                      % (kind, expect_dim, ",".join(sorted(dims)) or "无", cid))
                failures.append((kind, "漏报"))
    return failures


# ════════════════════════════════════════════════════════════════════════════════

def main():
    global ADJ
    args = sys.argv[1:]
    if args and args[0] == "--mutation-test":
        if len(args) < 2:
            print("用法: compare.py --mutation-test <extracted.json>", file=sys.stderr)
            return 2
        with open(args[1], "r", encoding="utf-8") as fh:
            data = json.load(fh)
        ADJ = load_equivalences()
        failures = run_mutation_test(data, data["schema"])
        if failures:
            print("[mutation-test] ❌ %d 项失败" % len(failures))
            return 1
        print("[mutation-test] ✅ 全部通过")
        return 0

    if len(args) < 3:
        print("用法: compare.py <extracted.json> <cases.tsv> <report.md>", file=sys.stderr)
        return 2
    with open(args[0], "r", encoding="utf-8") as fh:
        data = json.load(fh)
    ADJ = load_equivalences()
    cases_tsv = args[1] if len(args) > 1 and args[1] else None
    out_md = args[2] if len(args) > 2 else "report.md"

    sem, og, l1 = render_report(data, cases_tsv, out_md)
    print("[compare] 语义差异 %d 处 / ORM 噪声与已证明等价 %d 处 / L1 响应不同 %d 例 ⇒ %s"
          % (sem, og, l1, out_md))
    # 🔴 第二十三轮：**UNDECIDABLE 不算失败**（不是「已证明的差异」）⇒ 但报告里显著列出
    return 1 if (sem or l1) else 0


if __name__ == "__main__":
    sys.exit(main())
