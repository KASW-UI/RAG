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
    逐列比对 `data_type` 与 `character_maximum_length`。

不查:索引 / 唯一约束 / NOT NULL / DEFAULT —— 这几项目前**已知差异**但需要单独取证
    (唯一约束两侧都靠 UNIQUE、NOT NULL 两侧都 NOT NULL,初步看是对齐的),
    留给下一笔。本脚本在结尾把它们标成 **NOT CHECKED**,
    以免「脚本全绿」被读成「DDL 已完全一致」。

════════════════════════════════════════════════════════════════════════
【表名映射 —— 来自 AGENTS.md §3.4 的表名全映射,不是本脚本自己定的】
════════════════════════════════════════════════════════════════════════
    Go `users`                  ↔ RAG `users_user`
    Go `admins`                 ↔ RAG `admin_admin`
    Go `admin_invites`          ↔ RAG `admin_invite`
    Go `user_avatar_history`    ↔ RAG `users_user_avatar_history`

⚠️ admin 侧是**两端都改名**,不是一端改。写错会让脚本静默「列集合不一致」而不报错。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
    0  两侧列集合一致,且所有列类型相同
    1  存在类型/长度差异(当前实际情况:35 列里 22 列不同)
    2  列集合不一致 —— 通常是表名映射写错,或某一侧 schema 没初始化
"""
import subprocess
import sys

# AGENTS.md §3.4 表名全映射
TABLE_MAP = {
    "users": "users_user",
    "admins": "admin_admin",
    "admin_invites": "admin_invite",
    "user_avatar_history": "users_user_avatar_history",
}

GO_DB, RAG_DB = "go_db", "rag_db"
PSQL = ["docker", "exec", "-i", "difftest-postgres", "psql", "-U", "admin", "-d"]

QUERY = (
    "select table_name || '.' || column_name || '|' || data_type || '|' || "
    "coalesce(character_maximum_length::text, '') "
    "from information_schema.columns where table_schema='public' order by 1;"
)


def load(db):
    out = subprocess.run(PSQL + [db, "-t", "-A", "-c", QUERY],
                         capture_output=True, text=True, check=True).stdout
    cols = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        key, dtype, length = line.strip().split("|")
        tbl, col = key.split(".", 1)
        cols[TABLE_MAP.get(tbl, tbl) + "." + col] = (dtype, length)
    return cols


def main():
    try:
        go, rag = load(GO_DB), load(RAG_DB)
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"FATAL: 读不到 difftest-postgres 里的库 —— {e}\n"
              f"先起 ./tools/difftest/run.sh --no-build --keep --serve-only")
        return 2

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

    print(f"DDL 列级对比  go_db={len(go)} 列  rag_db={len(rag)} 列  "
          f"（键集合一致 ✓）")
    print(f"类型/长度差异 {len(diffs)} 处：")
    for k, g, r in diffs:
        gs = f"{g[0]}({g[1]})" if g[1] else g[0]
        rs = f"{r[0]}({r[1]})" if r[1] else r[0]
        print(f"  {k:42s} go={gs:30s} rag={rs}")

    same = [k for k in sorted(go) if go[k] == rag[k]]
    print(f"\n逐字相同 {len(same)} 列。")
    print("NOT CHECKED（脚本没查，别把本脚本的结论读成「DDL 已一致」）："
          "索引 / 唯一约束 / NOT NULL / DEFAULT。")

    if diffs:
        print("\nE-3 根因 R3：Go 的 GORM 给 Go string 生成 text（无上限），"
              "RAG 的 db/*.sql 照旧 JPA @Column 写成 varchar(255)。")
        print("⇒ 超过 255 字符的 username/email/history_id 在 Go 写成功、"
              "在 RAG 被 PG 拒（22001）⇒ 5001/5003；反噬读路径时 RAG 报 1002/1003。")
    return 1 if diffs else 0


if __name__ == "__main__":
    sys.exit(main())