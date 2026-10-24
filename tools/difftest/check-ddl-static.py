#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
§6.2.6 **E-3** 的静态半 —— Go 真源 ↔ RAG 的 **DDL 列级 + 约束级**对比。

════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════
`check-proto-static.sh` 比的是 proto 契约(字段号 / message 名 / RPC 签名),
但 §3.1 要求的对照清单里还写着「**数据库表结构 / 索引 / 唯一约束 / NOT NULL /
DEFAULT**」—— 这一项此前**没有任何脚本在查**,一直是靠人读 DDL 文件。

E-3 的运行时矩阵在 2026-10-21 撞出了它的第一个后果:
`long_5000` 边界用例下 Go 写入成功、RAG 返 5001,顺着查下去才发现
**Go 的字符串列是 `text`,RAG 的是 `varchar(255)`** ——
RAG 那 4 份 `db/*.sql` 是照着**旧 JPA @Column** 写的,不是照着 Go 的 GORM schema。

⇒ 运行时现象只是症状,根在 DDL;而 DDL 这一层此前没有「机器会看」的地方。

════════════════════════════════════════════════════════════════════════
【本脚本查什么 / 不查什么】
════════════════════════════════════════════════════════════════════════
查:两侧数据库的 `information_schema.columns`,按 AGENTS.md §3.4.1 的表名映射对齐后
    逐列比对 `data_type` 与 `character_maximum_length`;
    另有一节**补充对照**比 `is_nullable` 与 `column_default`
    （2026-10-24 新增 —— 原脚本把这两项标成 NOT CHECKED，现在补上）；
    `--indexes` 时再查 **索引 / 唯一约束**（pg_indexes + pg_constraint）——
    这是 §3.1 对照清单里**最后两项**从未被机器核过的东西
    （message 域 2026-10-24 第十轮补上）。

不查:**触发器 / CHECK 约束 / 外键 / 分区 / 存储过程** —— 本仓的 message 域
    两侧都没有这些对象，故不查；别的域若有，需要另开。

════════════════════════════════════════════════════════════════════════
【表名映射 —— 来自 AGENTS.md §3.4 的表名全映射,不是本脚本自己定的】
════════════════════════════════════════════════════════════════════════
    Go `users`                  ↔ RAG `users_user`
    Go `admins`                 ↔ RAG `admin_admin`
    Go `admin_invites`          ↔ RAG `admin_invite`
    Go `user_avatar_history`    ↔ RAG `users_user_avatar_history`

⚠️ admin 侧是**两端都改名**,不是一端改。写错会让脚本静默「列集合不一致」而不报错。

⚠️ **task 域不需要映射**：Go 的 `AutoMigrate`（service_context.go:47）与
    RAG 的 Flyway（V1__create_task_progress.sql / V2__create_user_like_count.sql）
    **两侧表名完全相同**（`task_progress` / `user_like_count`）⇒ 映射表为空。

⚠️ **message 域不需要映射**：Go 的 `AutoMigrate`（service/message/rpc/internal/model/db.go:44
    `AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})`）与
    RAG 的 Flyway（V1~V5）**两侧表名完全相同**
    （`message_notification` / `message_conversation` / `message_conversation_message`）
    ⇒ 映射表为空。⚠️ 写一个「看起来更规范」的映射进去 = 凭空制造列集合不一致。

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
    ./check-ddl-static.py                  # 默认 users 域（原行为不变）
    ./check-ddl-static.py --domain task    # task 域
    ./check-ddl-static.py --domain message # message 域
    ./check-ddl-static.py --domain task --container my-pg --go-db a --rag-db b

    --indexes            额外核对索引 / 唯一约束（pg_indexes + pg_constraint）
                         ⚠️ 默认**不开** —— 开了会让 users/task 域因为**已知**
                            历史差异直接红（它们的索引差异尚未逐条签字），
                            破坏别人已经在用的门禁语义。
    --allow-idx NAME,NAME  豁免名单：这些索引名相关的差异**不计为失败**
                         （用于「已签字的有意偏离」—— 例如 message 域的
                          V-021（索引名不同）与 V-022（列数不同））
                         ⚠️ 豁免只在 `--indexes` 打开时生效。

    ⚠️ message 域的基建是 `msgddl-pg`（不是 `difftest-postgres`），
       库名 `go_msg_db` / `rag_msg_db`，
       由 tools/difftest/run-message-ddl-parity.sh 拉起。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
    0  两侧列集合一致,且所有被核对的维度无差异（或差异全部命中豁免名单）
    1  存在差异
    2  列集合不一致 —— 通常是表名映射写错,或某一侧 schema 没初始化
"""
import argparse
import re
import subprocess
import sys

# ── 域预设 ─────────────────────────────────────────────────────────────
# 每个域一套：容器名 / 两个库名 / 表名映射 / 排除表 / 差异说明
DOMAINS = {
    "users": {
        # ⚠️ 2026-10-25（B3/X-7）订正：原写 `difftest-postgres` / `go_db` / `rag_db`
        #   ⇒ **实测该容器已不存在**（`docker ps` 里是 `uudifftest-postgres`），
        #   库名也带 `uu_` 前缀 ⇒ **原预设直接 FATAL，跑都跑不起来**。
        #   ⇒ 订正为实测存在的容器 + 库名。
        "container": "uudifftest-postgres",
        "go_db": "uu_go_db",
        "rag_db": "uu_rag_db",
        # ⚠️ 2026-10-25 订正：原写「users 域没有 Flyway 账本 ⇒ 不排除任何表」——
        #    那是 10-25「按模块独立账本表名」**之前**的假设。10-25 之后
        #    `spring.flyway.table: flyway_schema_history_<模块>` 落地，而**所有 rpc 模块
        #    共用同一个 `rag_db`** ⇒ message-rpc 的账本表 `flyway_schema_history_message`
        #    就落在 users 域检查所读的库里。
        #    ⇒ users 域必须**同样排除** `flyway_schema_history*`，否则「仅 RAG 有」
        #    会把列集合判成不一致（实测：2026-10-25 复现该假阳性）。
        #    排除用前缀匹配（loader 里是 `tbl == x or tbl.startswith(x)`）⇒ 一条就够。
        "exclude_tables": ("flyway_schema_history",),
        "table_map": {
            "users": "users_user",
            "admins": "admin_admin",
            "admin_invites": "admin_invite",
            "user_avatar_history": "users_user_avatar_history",
        },
        "note": ("E-3 根因 R3：Go 的 GORM 给 Go string 生成 text（无上限），"
                 "RAG 的 db/*.sql 照旧 JPA @Column 写成 varchar(255)。\n"
                 "⇒ 超过 255 字符的 username/email/history_id 在 Go 写成功、"
                 "在 RAG 被 PG 拒（22001）⇒ 5001/5003；反噬读路径时 RAG 报 1002/1003。"),
        "prepare_hint": "先起 ./tools/difftest/run.sh --no-build --keep --serve-only",
    },
    "task": {
        "container": "taskprobe-postgres",
        "go_db": "task_go_db",
        "rag_db": "task_rag_db",
        # ⚠️ task 域两侧表名相同 ⇒ 不需要映射（写错映射才会引入差异）
        "table_map": {},
        # ⚠️ Flyway 的**迁移账本**是 RAG 侧独有的产物（Go 侧用 AutoMigrate，没有账本表），
        #    不是业务表 ⇒ 必须排除，否则「仅 RAG 有」会把列集合判成不一致。
        #    task 域的账本表名见 AGENTS.md §6.3 问题 4：`flyway_schema_history_task`
        #    （每模块独立账本表名，message/like 是 `_message` / `_like` 后缀）。
        "exclude_tables": ("flyway_schema_history",),
        "note": ("task 域两侧 DDL 应当**逐列同型**：Go 的 AutoMigrate 对无 tag 的\n"
                 "  int64 ⇒ bigint、string ⇒ text、time.Time ⇒ timestamptz，\n"
                 "  RAG 的 V1/V2 迁移脚本就是照这条规则写的。\n"
                 "⇒ 本域出现任何差异都应视为**新漂移**，而不是「已知的历史包袱」。"),
        "prepare_hint": ("先起 tools/difftest/run-task-http-parity.sh（会拉起 taskprobe-postgres "
                         "并建好 task_go_db / task_rag_db）"),
    },
    "message": {
        # ⚠️ 2026-10-25（B3/X-7）订正：原写 `msgddl-pg` / `go_msg_db` / `rag_msg_db`
        #   ⇒ **实测容器名带下划线（`msg_ddl_pg`）、库名是 `go_db` / `rag_db`**，
        #   且该容器的 PG 超级用户是 `postgres`（**没有 admin 角色**）
        #   ⇒ 原预设三重失效（容器 / 库 / 用户）⇒ 全部按实测订正。
        "container": "msg_ddl_pg",
        "go_db": "go_db",
        "rag_db": "rag_db",
        "pg_user": "postgres",
        # ⚠️ message 域两侧表名相同（Go AutoMigrate 与 RAG Flyway 同名）⇒ 不需要映射
        "table_map": {},
        # ⚠️ RAG 侧有 Flyway 独立账本表 `flyway_schema_history_message`
        #    （AGENTS.md §6.3 问题 4 的定案），Go 侧用 AutoMigrate 没有账本表
        #    ⇒ 必须排除，否则「仅 RAG 有」会把列集合判成不一致。
        "exclude_tables": ("flyway_schema_history",),
        "note": ("message 域两侧 DDL 应当**逐列同型**：Go 的 AutoMigrate 对无 tag 的\n"
                 "  int64 ⇒ bigint、string ⇒ text、time.Time ⇒ timestamptz，\n"
                 "  RAG 的 V1~V5 迁移脚本就是照这条规则写的。\n"
                 "⇒ 本域出现任何差异都应视为**新漂移**，而不是「已知的历史包袱」。"),
        "prepare_hint": ("先起 tools/difftest/run-message-ddl-parity.sh"
                         "（会拉起 msg_ddl_pg 并建好 go_db / rag_db）"),
    },
    "comment": {
        # ✅ 2026-10-25（B3/X-7 扩域）：本域**已有现成的双侧库**（comment 域对拍环境）
        "container": "cdifftest-postgres",
        "go_db": "go_comment",
        "rag_db": "rag_comment",
        "table_map": {},
        # ⚠️ RAG 侧有 Flyway 独立账本表 `flyway_schema_history_comment`（AGENTS.md §6.3 问题 4）
        #    Go 侧用 AutoMigrate 没有账本表 ⇒ 必须排除，否则「仅 RAG 有」会把列集合判成不一致。
        "exclude_tables": ("flyway_schema_history",),
        "note": ("comment 域两侧表名相同（Go 的 TableName() 与 RAG 迁移脚本同名）⇒ 不需要映射。\n"
                 "  ⚠️ **已知的环境缺口（不是漂移）**：Go 侧的 `report_record` 表\n"
                 "  （`service/comment/rpc/internal/model/data_model.go:66` 的 `ReportRecord.TableName()`）\n"
                 "  **没有出现在 `go_comment` 库里** ⇒ 该表是**只被 RAG 侧 migrate 过**，\n"
                 "  两侧对照因此在 report_record 上缺 Go 侧样本 ⇒ 结论只覆盖其余 6 张表。"),
        "prepare_hint": "先起 tools/difftest/run.sh --no-build --keep --serve-only（会拉起 cdifftest-postgres）",
    },
}

# 列类型 / 长度
QUERY = (
    "select table_name || '.' || column_name || '|' || data_type || '|' || "
    "coalesce(character_maximum_length::text, '') "
    "from information_schema.columns where table_schema='public' order by 1;"
)

# NOT NULL / DEFAULT（2026-10-24 新增的补充对照）
QUERY_CONSTRAINT = (
    "select table_name || '.' || column_name || '|' || is_nullable || '|' || "
    "coalesce(column_default, '<NULL>') "
    "from information_schema.columns where table_schema='public' order by 1;"
)

# 索引（含唯一索引）—— --indexes 开关才查
QUERY_INDEX = (
    "select tablename || '.' || indexname || '|' || indexdef "
    "from pg_indexes where schemaname='public' order by 1;"
)

# 约束（主键 / 唯一约束 / 外键 / check）—— --indexes 开关才查
QUERY_CON = (
    "select conrelid::regclass::text || '|' || conname || '|' || contype::text || '|' || "
    "pg_get_constraintdef(oid) "
    "from pg_constraint where connamespace='public'::regnamespace order by 1;"
)

_INDEXDEF_RE = re.compile(
    r"^CREATE\s+(UNIQUE\s+)?INDEX\s+(\S+)\s+ON\s+\S+\s+USING\s+\w+\s+\((.*)\)\s*$")


def load(container, db, table_map, query, exclude_tables=(), user="admin"):
    out = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", user, "-d", db,
         "-t", "-A", "-c", query],
        capture_output=True, text=True, check=True).stdout
    cols = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        parts = line.strip().split("|")
        key = parts[0]
        tbl = key.split(".", 1)[0]
        # ⚠️ 排除表（如 Flyway 的迁移账本）：它是某一侧的**工具产物**，不是业务表
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        cols[table_map.get(tbl, tbl) + key[len(tbl):]] = tuple(parts[1:])
    return cols


def load_indexes(container, db, table_map, exclude_tables=(), user="admin"):
    """⇒ {索引名: (表名, 是否唯一, 列元组)}"""
    out = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", user, "-d", db,
         "-t", "-A", "-c", QUERY_INDEX],
        capture_output=True, text=True, check=True).stdout
    idx = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        key, defn = line.strip().split("|", 1)
        tbl, name = key.split(".", 1)
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        m = _INDEXDEF_RE.match(defn)
        if not m:
            continue
        unique = bool(m.group(1))
        cols = tuple(c.strip().split()[0].strip('"') for c in m.group(3).split(","))
        idx[name] = (table_map.get(tbl, tbl), unique, cols)
    return idx


def load_constraints(container, db, table_map, exclude_tables=(), user="admin"):
    """⇒ {(表名, 约束名): (contype, 定义)}"""
    out = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", user, "-d", db,
         "-t", "-A", "-c", QUERY_CON],
        capture_output=True, text=True, check=True).stdout
    cons = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        tbl, name, ctype, cdef = line.strip().split("|", 3)
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        cons[(table_map.get(tbl, tbl), name)] = (ctype, cdef)
    return cons


def compare_indexes(go_idx, rag_idx, allow):
    """⇒ (差异明细, 命中豁免的明细)

    差异分三类：
      ① 同名但定义不同（唯一性或列不同）
      ② 名字不同但「表 + 唯一性 + 列」完全等价 ⇒ **行为等价，仅名字不同**（不是缺失）
      ③ 真的只有一侧有（既没有同名项，也没有等价项）
    """
    diffs, waived = [], []
    for name in sorted(set(go_idx) & set(rag_idx)):
        g, r = go_idx[name], rag_idx[name]
        if g[1] != r[1] or g[2] != r[2]:
            diffs.append((name, g, r))
    go_only = set(go_idx) - set(rag_idx)
    rag_only = set(rag_idx) - set(go_idx)
    # 先把「等价但名字不同」配对起来，剩下的才是真的单边缺失
    paired_go, paired_rag = set(), set()
    for name in sorted(go_only):
        g = go_idx[name]
        twins = [n for n in sorted(rag_only) if rag_idx[n] == g]
        if twins:
            for t in twins:
                diffs.append((f"{name} ≡ {t}", g, rag_idx[t]))
                paired_go.add(name)
                paired_rag.add(t)
    for name in sorted(go_only - paired_go):
        diffs.append((f"{name}（仅 Go 有）", go_idx[name], None))
    for name in sorted(rag_only - paired_rag):
        diffs.append((f"{name}（仅 RAG 有）", None, rag_idx[name]))
    for d in diffs:
        names = {n for n in re.split(r"[ ≡（）]", d[0]) if n}
        if names & allow:
            waived.append(d)
    return diffs, waived


def main():
    ap = argparse.ArgumentParser(description="Go 真源 ↔ RAG 的 DDL 列级 + 约束级对比")
    ap.add_argument("--domain", default="users", choices=sorted(DOMAINS),
                    help="要对照的域（默认 users）")
    ap.add_argument("--container", default=None, help="覆盖 PG 容器名")
    ap.add_argument("--go-db", default=None, help="覆盖 Go 侧库名")
    ap.add_argument("--rag-db", default=None, help="覆盖 RAG 侧库名")
    ap.add_argument("--indexes", action="store_true",
                    help="额外核对索引 / 唯一约束（默认不开，见文件头说明）")
    ap.add_argument("--allow-idx", default="",
                    help="豁免名单：逗号分隔的索引名，相关差异不计为失败")
    ap.add_argument("--allow-default", default="",
                    help="豁免名单：逗号分隔的「表.列」键，"
                         "这些列的 DEFAULT 差异不计为失败")
    args = ap.parse_args()

    cfg = DOMAINS[args.domain]
    container = args.container or cfg["container"]
    go_db = args.go_db or cfg["go_db"]
    rag_db = args.rag_db or cfg["rag_db"]
    table_map = cfg["table_map"]
    exclude_tables = cfg.get("exclude_tables", ())
    # ⚠️ 不同容器的 PG 超级用户不一样（有的只有 postgres、有的有 admin）
    #   ⇒ 用户名必须**按域预设**走，不能写死。
    pg_user = cfg.get("pg_user", "admin")
    allow = {x.strip() for x in args.allow_idx.split(",") if x.strip()}
    allow_default = {x.strip() for x in args.allow_default.split(",") if x.strip()}

    try:
        go = load(container, go_db, table_map, QUERY, exclude_tables, pg_user)
        rag = load(container, rag_db, table_map, QUERY, exclude_tables, pg_user)
        go_c = load(container, go_db, table_map, QUERY_CONSTRAINT, exclude_tables, pg_user)
        rag_c = load(container, rag_db, table_map, QUERY_CONSTRAINT, exclude_tables, pg_user)
        go_i = load_indexes(container, go_db, table_map, exclude_tables, pg_user) if args.indexes else {}
        rag_i = load_indexes(container, rag_db, table_map, exclude_tables, pg_user) if args.indexes else {}
        go_k = load_constraints(container, go_db, table_map, exclude_tables, pg_user) if args.indexes else {}
        rag_k = load_constraints(container, rag_db, table_map, exclude_tables, pg_user) if args.indexes else {}
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"FATAL: 读不到 {container} 里的库 —— {e}\n{cfg['prepare_hint']}")
        return 2

    print(f"== 域: {args.domain}  容器: {container}  "
          f"Go库: {go_db}  RAG库: {rag_db} ==")

    only_go = sorted(set(go) - set(rag))
    only_rag = sorted(set(rag) - set(go))
    if only_go or only_rag:
        print(f"列集合不一致 —— 通常是表名映射写错或某一侧 schema 没初始化")
        for k in only_go:
            print(f"  仅 Go 有:  {k}")
        for k in only_rag:
            print(f"  仅 RAG 有:  {k}")
        return 2

    diffs = [(k, go[k], rag[k]) for k in sorted(go) if go[k] != rag[k]]

    print(f"DDL 列级对比  {go_db}={len(go)} 列  {rag_db}={len(rag)} 列  "
          f"（键集合一致 ✓）")
    print(f"类型/长度差异 {len(diffs)} 处：")
    for k, g, r in diffs:
        gs = f"{g[0]}({g[1]})" if g[1] else g[0]
        rs = f"{r[0]}({r[1]})" if r[1] else r[0]
        print(f"  {k:42s} go={gs:30s} rag={rs}")

    same = [k for k in sorted(go) if go[k] == rag[k]]
    print(f"\n逐字相同 {len(same)} 列。")

    # ---- 补充对照：NOT NULL / DEFAULT（2026-10-24 新增）----
    # ⚠️ 分成两节报，是因为两者的**可接受性完全不同**：
    #    可空性差异 = 能不能存 NULL ⇒ 直接影响写入行为，必须零容忍；
    #    DEFAULT 差异 = 「省略该列时填什么」⇒ 只在有 INSERT 省略该列时才发作。
    nullable_diffs, default_diffs = [], []
    for k in sorted(go_c):
        if k not in rag_c:
            nullable_diffs.append((k, go_c[k], ("<缺列>", "")))
            continue
        g_null, g_def = go_c[k]
        r_null, r_def = rag_c[k]
        if g_null != r_null:
            nullable_diffs.append((k, go_c[k], rag_c[k]))
        if g_def != r_def:
            default_diffs.append((k, go_c[k], rag_c[k]))
    print(f"\n补充对照 · NOT NULL（可空性）：差异 {len(nullable_diffs)} 处")
    for k, g, r in nullable_diffs:
        print(f"  {k:42s} go=(nullable={g[0]})  rag=(nullable={r[0]})")
    if not nullable_diffs:
        print("  （两侧完全一致 ✓）")
    print(f"\n补充对照 · DEFAULT：差异 {len(default_diffs)} 处")
    for k, g, r in default_diffs:
        waived = "  [已签字豁免]" if k in allow_default else ""
        print(f"  {k:42s} go={g[1]:24s} rag={r[1]:24s}{waived}")
    if not default_diffs:
        print("  （两侧完全一致 ✓）")

    failures = len(diffs) + len(nullable_diffs)
    failures += len([d for d in default_diffs if d[0] not in allow_default])

    # ---- 索引 / 唯一约束（--indexes 才查）----
    if args.indexes:
        i_diffs, i_waived = compare_indexes(go_i, rag_i, allow)
        print(f"\n对照 · 索引（pg_indexes）：Go {len(go_i)} 个  RAG {len(rag_i)} 个")
        if not i_diffs:
            print("  （两侧索引定义逐条一致 ✓）")
        for label, g, r in i_diffs:
            gs = f"unique={g[1]} cols={list(g[2])}" if g else "<无>"
            rs = f"unique={r[1]} cols={list(r[2])}" if r else "<无>"
            waived = "  [已签字豁免]" if label in {w[0] for w in i_waived} else ""
            print(f"  {label:64s} go={gs:34s} rag={rs}{waived}")
        if i_waived:
            print(f"  ⚠️ 其中 {len(i_waived)} 处命中豁免名单（--allow-idx）⇒ 不计失败")

        k_only_go = sorted(set(go_k) - set(rag_k))
        k_only_rag = sorted(set(rag_k) - set(go_k))
        k_same = sorted(set(go_k) & set(rag_k))
        print(f"\n对照 · 约束（pg_constraint）：两侧各 {len(go_k)}/{len(rag_k)} 行")
        print(f"  逐条一致 {len(k_same)} 行 ✓")
        for k in k_only_go:
            print(f"  仅 Go 有:  {k[0]}.{k[1]}  contype={go_k[k][0]}  {go_k[k][1]}")
        for k in k_only_rag:
            print(f"  仅 RAG 有: {k[0]}.{k[1]}  contype={rag_k[k][0]}  {rag_k[k][1]}")
        if not k_only_go and not k_only_rag:
            print("  （两侧约束集合完全一致 ✓）")

        failures += len(i_diffs) - len(i_waived)
        failures += len(k_only_go) + len(k_only_rag)
    else:
        print("\nNOT CHECKED（脚本没查，别把本脚本的结论读成「DDL 已一致」）："
              "索引 / 唯一约束。加 --indexes 可核。")

    if diffs:
        print()
        print(cfg["note"])
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
