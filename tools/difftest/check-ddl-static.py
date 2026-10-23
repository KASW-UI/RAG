#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
§6.2.6 **E-3** 的静态半 —— Go 真源 ↔ RAG 的 **DDL 列级**对比。

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
    （2026-10-24 新增 —— 原脚本把这两项标成 NOT CHECKED，现在补上）。

不查:索引 / 唯一约束（主键/唯一索引要另查 pg_index / pg_constraint）。
    这两项**仍然不查**，并在结尾标 NOT CHECKED —— 别把本脚本的结论读成
    「DDL 已完全一致」。

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

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
    ./check-ddl-static.py                  # 默认 users 域（原行为不变）
    ./check-ddl-static.py --domain task    # task 域
    ./check-ddl-static.py --domain task --container my-pg --go-db a --rag-db b

    ⚠️ task 域的基建是 `taskprobe-postgres`（不是 `difftest-postgres`），
       库名 `task_go_db` / `task_rag_db`，
       由 tools/difftest/run-task-http-parity.sh 或 run-task-kafka-e2e.sh 拉起。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
    0  两侧列集合一致,且所有列类型相同
    1  存在类型/长度差异(当前实际情况:users 域 35 列里 22 列不同)
    2  列集合不一致 —— 通常是表名映射写错,或某一侧 schema 没初始化
"""
import argparse
import subprocess
import sys

# ── 域预设 ─────────────────────────────────────────────────────────────
# 每个域一套：容器名 / 两个库名 / 表名映射 / 差异说明
DOMAINS = {
    "users": {
        "container": "difftest-postgres",
        "go_db": "go_db",
        "rag_db": "rag_db",
        # users 域是 users/admin 两套 schema，没有 Flyway 账本 ⇒ 不排除任何表
        "exclude_tables": (),
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


def load(container, db, table_map, query, exclude_tables=()):
    out = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", "admin", "-d", db,
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


def main():
    ap = argparse.ArgumentParser(description="Go 真源 ↔ RAG 的 DDL 列级对比")
    ap.add_argument("--domain", default="users", choices=sorted(DOMAINS),
                    help="要对照的域（默认 users）")
    ap.add_argument("--container", default=None, help="覆盖 PG 容器名")
    ap.add_argument("--go-db", default=None, help="覆盖 Go 侧库名")
    ap.add_argument("--rag-db", default=None, help="覆盖 RAG 侧库名")
    args = ap.parse_args()

    cfg = DOMAINS[args.domain]
    container = args.container or cfg["container"]
    go_db = args.go_db or cfg["go_db"]
    rag_db = args.rag_db or cfg["rag_db"]
    table_map = cfg["table_map"]
    exclude_tables = cfg.get("exclude_tables", ())

    try:
        go = load(container, go_db, table_map, QUERY, exclude_tables)
        rag = load(container, rag_db, table_map, QUERY, exclude_tables)
        go_c = load(container, go_db, table_map, QUERY_CONSTRAINT, exclude_tables)
        rag_c = load(container, rag_db, table_map, QUERY_CONSTRAINT, exclude_tables)
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
            print(f"  仅 RAG 有: {k}")
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
    c_diffs = []
    for k in sorted(go_c):
        if k not in rag_c:
            c_diffs.append((k, go_c[k], ("<缺列>", "")))
        elif go_c[k] != rag_c[k]:
            c_diffs.append((k, go_c[k], rag_c[k]))
    print(f"\n补充对照 · NOT NULL / DEFAULT：差异 {len(c_diffs)} 处")
    for k, g, r in c_diffs:
        g_null, g_def = g
        r_null, r_def = r
        print(f"  {k:42s} go=(nullable={g_null}, default={g_def})  "
              f"rag=(nullable={r_null}, default={r_def})")
    if not c_diffs:
        print("  （两侧完全一致 ✓）")

    print("\nNOT CHECKED（脚本没查，别把本脚本的结论读成「DDL 已一致」）："
          "索引 / 唯一约束。")

    if diffs:
        print()
        print(cfg["note"])
    return 1 if diffs else 0


if __name__ == "__main__":
    sys.exit(main())
