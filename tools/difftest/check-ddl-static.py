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
    （message 域 2026-10-24 第十轮补上）；
    `--indexes` 时还查 **触发器**（pg_trigger，2026-10-25 L3/X-7 新增）——
    article / like / favorite 三个域的「不可变信封」语义**只由触发器承载**，
    不查它等于把最硬的那条约束漏在门外。
    `--indexes` 时还查 **触发器形式 + 触发器函数体的结构化语义属性**
    （2026-10-26 global 域 L10 新增）——
    ⚠️ **只比结构化属性，不逐字比 SQL 文本**：
      · 触发器形式：timing（BEFORE/AFTER/INSTEAD OF）/ 事件集 / 粒度（ROW|STATEMENT）
        / 是否 constraint trigger / 有无 WHEN 条件
      · 函数体属性：函数名 / 返回类型 / 参数列表 / volatility
        （VOLATILE|STABLE|IMMUTABLE）/ 是否 RETURNS trigger / 语言
    ⇒ 「写法不同但语义相同」（`IS DISTINCT FROM` vs `<>`、缩进、注释）**不会**被报成差异。
    ⇒ 函数体的**语义**（它到底在拒绝什么、Go 侧有没有**生产**对应物）
      **机器判不了** ⇒ 走人工逐条核对（台账：docs/DRIFT/global/lanes/L10-ddldeep.md §8.2）。

不查:**外键的引用目标（只查 contype + 定义文本）/ 分区 / 存储过程** —— 本仓
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
    --allow-default "表.列,..." 豁免名单：这些列的 DEFAULT 差异不计为失败

    ⚠️ message 域的基建是 `msgddl-pg`（不是 `difftest-postgres`），
       库名 `go_msg_db` / `rag_msg_db`，
       由 tools/difftest/run-message-ddl-parity.sh 拉起。

    ⚠️ **2026-10-26（global 域 L10）新增 2 域**：`task` / `message`。
       这两域原先指向 **B3 轮遗留**的容器（`taskprobe-postgres` / `msg_ddl_pg`
       —— 还能跑、退出码正常，但**不是本 runner 的产物**）
       ⇒ 现已改指向 **tools/difftest/ddl-parity.sh 统一拉起**的
          `ddl-task-pg` / `ddl-message-pg`（库名 `go_<域>_db` / `rag_<域>_db`）
       ⇒ 退出码语义与其它域统一（0/1/2/3）。
       ⇒ 一键复现：`bash tools/difftest/ddl-parity.sh task|message`。

    ⚠️ **2026-10-25（global 域 L3 / X-7）新增 5 域**：`article` / `like` / `hot` /
       `follow` / `favorite`。这 5 域的容器与库由 **tools/difftest/ddl-parity.sh**
       统一拉起（容器名 `ddl-<域>-pg`，库名 `go_<域>_db` / `rag_<域>_db`）⇒
       一键复现：`bash tools/difftest/ddl-parity.sh <域>`。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
    0  两侧列集合一致，且所有被核对的维度**无实质差异**
       （「名字不同但表 + 唯一性 + 列完全等价」**不算**差异 —— 见下）
    1  存在**实质差异**（类型/长度 / 可空性 / DEFAULT / 索引定义 / 单边索引 /
       单边约束 / 单边触发器 / 触发器形式 / 触发器函数体属性）
    2  **环境不满足**：读不到容器里的库、列集合不一致（表名映射写错或某一侧
       schema 没初始化）、预期业务表缺失（`expected_tables` 闸门）
    3  本域**不适用（N/A）**：两侧都没有业务表（如 hot 域 —— 它根本没有数据库）
       ⇒ 「两侧都是 0 张表」**不是**「DDL 已对齐」

⚠️ **「命名形态差」不计为失败**（2026-10-25 改）：索引/约束**名字不同、但
   表 + 唯一性 + 列（约束则是 contype + 定义）完全等价** ⇒ PG 层面的唯一性判定、
   冲突行为、外键行为完全相同 ⇒ 语义影响为零。这类差异会被单独列在
   「语义等价（仅命名形态差）」一节里，**不算失败**。
   ⇒ 改这条之前，task 域会因「主键名不同」被误判成有差异（实测 EXIT=1）。
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
        # ✅ 2026-10-25（L3/X-7）订正：原写 `uudifftest-postgres` / `uu_go_db` / `uu_rag_db`
        #    —— 那套库的 **Go 侧只有 users + user_avatar_history**（admin 的两张表没被
        #    AutoMigrate）⇒ 每次跑都撞「列集合不一致」。
        #    ⇒ 改指向 tools/difftest/ddl-parity.sh 统一拉起的 `ddl-users-pg`
        #    （探针同时跑 user/rpc 与 admin/rpc 两个包 ⇒ Go 侧 4 张表齐全）。
        "container": "ddl-users-pg",
        "go_db": "go_users_db",
        "rag_db": "rag_users_db",
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
        # ⚠️ 环境闸门（2026-10-25 L3/X-7 新增）：预期两侧都有的业务表（**映射后**的名字）。
        #    ⚠️ Go 侧的 admin 两张表由**另一个包**产出
        #    （service/user/admin/rpc/internal/model/db.go:15 AutoMigrate(&Admin{},&AdminInvite{},&User{})）
        #    ⇒ 探针必须同时跑 user/rpc 与 admin/rpc 两个包，否则这里会报「Go 侧缺 admin_admin」。
        "expected_tables": ("users_user", "admin_admin", "admin_invite",
                            "users_user_avatar_history"),
        "note": ("E-3 根因 R3：Go 的 GORM 给 Go string 生成 text（无上限），"
                 "RAG 的 db/*.sql 照旧 JPA @Column 写成 varchar(255)。\n"
                 "⇒ 超过 255 字符的 username/email/history_id 在 Go 写成功、"
                 "在 RAG 被 PG 拒（22001）⇒ 5001/5003；反噬读路径时 RAG 报 1002/1003。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh users",
    },
    "task": {
        # ✅ 2026-10-26（global 域 L10）订正：原写 `taskprobe-postgres` / `task_go_db`
        #   / `task_rag_db` ⇒ 那套环境是 **B3 轮遗留**的（由
        #   tools/difftest/run-task-http-parity.sh 拉起，**不是本 runner 的产物**）
        #   ⇒ 退出码虽然正常，但「能跑」≠「是统一 runner 产出的」。
        #   ⇒ 改指向 tools/difftest/ddl-parity.sh 统一拉起的 `ddl-task-pg`
        #   （探针复刻真源 service/task/rpc/internal/svc/service_context.go:47
        #     的 `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})`）。
        "container": "ddl-task-pg",
        "go_db": "go_task_db",
        "rag_db": "rag_task_db",
        # ⚠️ task 域两侧表名相同 ⇒ 不需要映射（写错映射才会引入差异）
        "table_map": {},
        "expected_tables": ("task_progress", "user_like_count"),
        # ⚠️ Flyway 的**迁移账本**是 RAG 侧独有的产物（Go 侧用 AutoMigrate，没有账本表），
        #    不是业务表 ⇒ 必须排除，否则「仅 RAG 有」会把列集合判成不一致。
        #    task 域的账本表名见 AGENTS.md §6.3 问题 4：`flyway_schema_history_task`
        #    （每模块独立账本表名，message/like 是 `_message` / `_like` 后缀）。
        "exclude_tables": ("flyway_schema_history",),
        "note": ("task 域两侧 DDL 应当**逐列同型**：Go 的 AutoMigrate 对无 tag 的\n"
                 "  int64 ⇒ bigint、string ⇒ text、time.Time ⇒ timestamptz，\n"
                 "  RAG 的 V1/V2 迁移脚本就是照这条规则写的。\n"
                 "⇒ 本域出现任何差异都应视为**新漂移**，而不是「已知的历史包袱」。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh task",
    },
    "message": {
        # ✅ 2026-10-26（global 域 L10）订正：原写 `msg_ddl_pg` / `go_db` / `rag_db`
        #   + `pg_user=postgres` ⇒ 那套环境是 **B3 轮遗留**的（由
        #   tools/difftest/run-message-ddl-parity.sh 拉起，**不是本 runner 的产物**）
        #   ⇒ 退出码虽然正常，但「能跑」≠「是统一 runner 产出的」。
        #   ⇒ 改指向 tools/difftest/ddl-parity.sh 统一拉起的 `ddl-message-pg`
        #   （探针走真源自己的 model.InitDB(DBConf)，即
        #     service/message/rpc/internal/model/db.go:44 的
        #     `AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})`）。
        "container": "ddl-message-pg",
        "go_db": "go_message_db",
        "rag_db": "rag_message_db",
        # ⚠️ ddl-parity.sh 统一用 PG_USER=admin 起容器 ⇒ 用户名跟着走
        "pg_user": "admin",
        # ⚠️ message 域两侧表名相同（Go AutoMigrate 与 RAG Flyway 同名）⇒ 不需要映射
        "table_map": {},
        "expected_tables": ("message_notification", "message_conversation",
                            "message_conversation_message"),
        # ⚠️ RAG 侧有 Flyway 独立账本表 `flyway_schema_history_message`
        #    （AGENTS.md §6.3 问题 4 的定案），Go 侧用 AutoMigrate 没有账本表
        #    ⇒ 必须排除，否则「仅 RAG 有」会把列集合判成不一致。
        "exclude_tables": ("flyway_schema_history",),
        "note": ("message 域两侧 DDL 应当**逐列同型**：Go 的 AutoMigrate 对无 tag 的\n"
                 "  int64 ⇒ bigint、string ⇒ text、time.Time ⇒ timestamptz，\n"
                 "  RAG 的 V1~V5 迁移脚本就是照这条规则写的。\n"
                 "⇒ 本域出现任何差异都应视为**新漂移**，而不是「已知的历史包袱」。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh message",
    },
    "comment": {
        # ✅ 2026-10-25（B3/X-7 扩域）：本域**已有现成的双侧库**（comment 域对拍环境）
        # ✅ 2026-10-25（L3/X-7）订正：原写 `cdifftest-postgres` / `go_comment` / `rag_comment`
        #    ⇒ 换成 tools/difftest/ddl-parity.sh 统一拉起的 `ddl-comment-pg`（可一键复现）。
        "container": "ddl-comment-pg",
        "go_db": "go_comment_db",
        "rag_db": "rag_comment_db",
        "table_map": {},
        # ⚠️ report_record **只在 RAG 侧有**：Go 的 comment/rpc db.go:15 的 AutoMigrate
        #    列表里**没有** ReportRecord，全仓也没有任何 .sql 建它
        #    ⇒ Go 的 InsertReport（model/comment.go:242）运行时会报 relation does not exist。
        #    ⇒ 这是**真差异（RAG 多一张表）**，不是环境缺口 ⇒ 不进 expected_tables。
        "expected_tables": ("subject", "comment_index", "comment_content",
                            "comment_like", "comment_domain_fact_outbox",
                            "comment_fact_stream"),
        # ⚠️ RAG 侧有 Flyway 独立账本表 `flyway_schema_history_comment`（AGENTS.md §6.3 问题 4）
        #    Go 侧用 AutoMigrate 没有账本表 ⇒ 必须排除，否则「仅 RAG 有」会把列集合判成不一致。
        "exclude_tables": ("flyway_schema_history",),
        "note": ("comment 域两侧表名相同（Go 的 TableName() 与 RAG 迁移脚本同名）⇒ 不需要映射。\n"
                 "  ⚠️ **已知的环境缺口（不是漂移）**：Go 侧的 `report_record` 表\n"
                 "  （`service/comment/rpc/internal/model/data_model.go:66` 的 `ReportRecord.TableName()`）\n"
                 "  **没有出现在 `go_comment` 库里** ⇒ 该表是**只被 RAG 侧 migrate 过**，\n"
                 "  两侧对照因此在 report_record 上缺 Go 侧样本 ⇒ 结论只覆盖其余 6 张表。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh comment",
    },
    # ══════════════════════════════════════════════════════════════════
    # 2026-10-25（global 域 L3 / X-7）新增 5 域
    # ══════════════════════════════════════════════════════════════════
    "article": {
        # ✅ 2026-10-25 新增域预设（L3/X-7 扩域）：容器由 tools/difftest/ddl-parity.sh 拉起。
        "container": "ddl-article-pg",
        "go_db": "go_article_db",
        "rag_db": "rag_article_db",
        "pg_user": "admin",
        # ✅ article 域**两侧表名完全相同**（Go 的 NamingStrategy{SingularTable:true} + TableName()
        #    ⇒ article / article_sync_outbox / article_revision / article_publication /
        #      article_domain_outbox；RAG 的 V8/V9/V10 同名）⇒ 不需要映射。
        #    ⚠️ 写一个「看起来更规范」的映射进去 = 凭空制造列集合不一致。
        "table_map": {},
        "exclude_tables": ("flyway_schema_history",),
        # 环境闸门：预期两侧都有的业务表。少一张 ⇒ 迁移没跑全 / 探针没跑 ⇒ 退出码 2。
        "expected_tables": ("article", "article_sync_outbox", "article_revision",
                            "article_publication", "article_domain_outbox"),
        "note": ("article 域两侧 DDL 应当**逐列同型**：Go 的 AutoMigrate 产出 article /\n"
                 "  article_sync_outbox，002_community_revision.sql 产出 article_revision /\n"
                 "  article_publication / article_domain_outbox；RAG 的 V8/V9/V10 逐条对齐。\n"
                 "⇒ 本域出现任何差异都应视为**新漂移**。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh article",
    },
    "like": {
        "container": "ddl-like-pg",
        "go_db": "go_like_db",
        "rag_db": "rag_like_db",
        "pg_user": "admin",
        # ✅ like 域两侧表名完全相同（Go 的 TableName() ⇒ like_record / like_consume_inbox /
        #    like_outbox_event / like_domain_fact_outbox / like_fact_stream）⇒ 不需要映射。
        "table_map": {},
        "exclude_tables": ("flyway_schema_history",),
        "expected_tables": ("like_record", "like_consume_inbox", "like_outbox_event",
                            "like_domain_fact_outbox", "like_fact_stream"),
        "note": ("like 域：Go 的 AutoMigrate（5 张表）+ migrations/001~003 手写 SQL；\n"
                 "  RAG 的 Flyway V1~V6。⇒ 本域出现任何差异都应视为**新漂移**。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh like",
    },
    "hot": {
        "container": "ddl-hot-pg",
        "go_db": "go_hot_db",
        "rag_db": "rag_hot_db",
        "pg_user": "admin",
        "table_map": {},
        "exclude_tables": ("flyway_schema_history",),
        # ⚠️ hot 域**两侧都没有业务表** ⇒ 本维度**不适用（N/A）**，不是「已对齐」。
        #    Go 侧：service/hot 全域无 gorm / 无 AutoMigrate（只有 Redis + Kafka）；
        #    RAG 侧：service/hot/hot-rpc 无 db/migration 目录、无 Mapper/Entity。
        "expected_tables": (),
        "note": ("hot 域**没有数据库**：Go 侧 service/hot 全域无 gorm（只有 Redis + Kafka），\n"
                 "  RAG 侧 hot-rpc 无 migration 目录 ⇒ DDL 这一维度对本域**不适用（N/A）**。\n"
                 "  ⚠️ 「两侧都是 0 张表」不等于「DDL 已对齐」—— 结论是「本维度无对象可比」。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh hot",
    },
    "follow": {
        "container": "ddl-follow-pg",
        "go_db": "go_follow_db",
        "rag_db": "rag_follow_db",
        "pg_user": "admin",
        # ✅ follow 域两侧表名完全相同（follow_relation / block_relation）⇒ 不需要映射。
        "table_map": {},
        "exclude_tables": ("flyway_schema_history",),
        "expected_tables": ("follow_relation", "block_relation"),
        "note": ("follow 域：Go 的 AutoMigrate(&FollowRelation{}, &BlockRelation{}) vs\n"
                 "  RAG 的 Flyway V1/V2 ⇒ 本域出现任何差异都应视为**新漂移**。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh follow",
    },
    "favorite": {
        "container": "ddl-favorite-pg",
        "go_db": "go_favorite_db",
        "rag_db": "rag_favorite_db",
        "pg_user": "admin",
        # ✅ favorite 域两侧表名完全相同（favorite_folder / favorite_item /
        #    favorite_fact_outbox / favorite_legacy_fact_marker）⇒ 不需要映射。
        "table_map": {},
        "exclude_tables": ("flyway_schema_history",),
        "expected_tables": ("favorite_folder", "favorite_item",
                            "favorite_fact_outbox", "favorite_legacy_fact_marker"),
        "note": ("favorite 域：Go 的 AutoMigrate(3 表) + 001~004 手写 SQL vs RAG 的 Flyway V1~V6。\n"
                 "  ⚠️ favorite 是**只读域**（域零写权）⇒ 本域差异**只登记、不修**。"),
        "prepare_hint": "先起 tools/difftest/ddl-parity.sh favorite",
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

# 触发器 —— --indexes 开关才查（2026-10-25 L3/X-7 新增）
# ⚠️ 只比「表 + 触发器名 + 事件 + 时机」；**不比函数体**（函数体是 SQL 文本，
#    逐字比对会把「写法不同但语义相同」也报成差异 ⇒ 反而制造噪音）。
QUERY_TRIGGER = (
    "select tgrelid::regclass::text || '|' || tgname || '|' || "
    "pg_get_triggerdef(oid) "
    "from pg_trigger where not tgisinternal and tgrelid in "
    "(select oid from pg_class where relnamespace='public'::regnamespace) order by 1;"
)

# 触发器**函数体**的结构化语义属性 —— --indexes 开关才查（2026-10-26 global 域 L10 新增）
# ⚠️ **只比结构化语义属性，不逐字比 SQL 文本**：
#    函数体是 SQL 源码，「写法不同但语义相同」（`IS DISTINCT FROM` vs `<>`、缩进、
#    注释、字符串字面量）逐字比对**全部**会被报成差异 ⇒ 反而制造噪音。
#    ⇒ 机器只比这 6 个属性：函数名 / 返回类型 / 参数列表 /
#      volatility（VOLATILE|STABLE|IMMUTABLE）/ 是否 RETURNS trigger / 语言。
#    ⇒ 函数体的**语义**（它到底在拒绝什么、Go 侧有没有**生产**对应物）
#      **机器判不了**（「两个语义相同的函数体」不可判定）⇒ 走**人工逐条核对**。
QUERY_TRIGGER_FUNC = (
    "select t.tgrelid::regclass::text || '|' || t.tgname || '|' || p.proname || '|' || "
    "p.prorettype::regtype::text || '|' || "
    "replace(coalesce(pg_get_function_arguments(p.oid), ''), '|', '') || '|' || "
    "case p.provolatile when 'i' then 'IMMUTABLE' when 's' then 'STABLE' "
    "else 'VOLATILE' end || '|' || "
    "case when p.prorettype = 'trigger'::regtype then 'trigger' else 'other' end || '|' || "
    "l.lanname "
    "from pg_trigger t "
    "join pg_proc p on p.oid = t.tgfoid "
    "join pg_language l on l.oid = p.prolang "
    "where not t.tgisinternal and t.tgrelid in "
    "(select oid from pg_class where relnamespace='public'::regnamespace) order by 1;"
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


def load_triggers(container, db, table_map, exclude_tables=(), user="admin"):
    """⇒ {(表名, 触发器名): 触发器定义}"""
    out = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", user, "-d", db,
         "-t", "-A", "-c", QUERY_TRIGGER],
        capture_output=True, text=True, check=True).stdout
    trig = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        tbl, name, tdef = line.strip().split("|", 2)
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        trig[(table_map.get(tbl, tbl), name)] = tdef
    return trig


def load_trigger_funcs(container, db, table_map, exclude_tables=(), user="admin"):
    """⇒ {(表名, 触发器名): (函数名, 返回类型, 参数列表, volatility, 是否trigger, 语言)}

    ⚠️ 这是**函数体的结构化语义属性**，**不是函数体文本** ——
    逐字比 SQL 文本会把「写法不同但语义相同」报成差异（见 QUERY_TRIGGER_FUNC 注释）。
    """
    out = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", user, "-d", db,
         "-t", "-A", "-c", QUERY_TRIGGER_FUNC],
        capture_output=True, text=True, check=True).stdout
    funcs = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        parts = line.strip().split("|")
        if len(parts) != 8:
            continue
        tbl, name = parts[0], parts[1]
        if any(tbl == x or tbl.startswith(x) for x in exclude_tables):
            continue
        funcs[(table_map.get(tbl, tbl), name)] = tuple(parts[2:])
    return funcs


_TRIGGER_FORM_FIELDS = ("timing", "events", "粒度", "constraint", "WHEN")


def parse_trigger_form(tdef):
    """从 `pg_get_triggerdef` 的文本里抽**结构化语义属性**（不比文本本身）。

    ⚠️ 文本里含 schema 限定名（`public.xxx`）与函数名 ⇒ 逐字比会把
       「表名映射」「函数改名」误报成差异 ⇒ 只取这 5 个**语义**属性：
       timing（BEFORE/AFTER/INSTEAD OF）· 事件集 · 粒度（ROW|STATEMENT）
       · 是否 constraint trigger · 有无 WHEN 条件。
    """
    s = " ".join(tdef.split())
    up = s.upper()
    timing = "?"
    for t in ("INSTEAD OF", "BEFORE", "AFTER"):
        if t in up:
            timing = t
            break
    events = ()
    if timing != "?":
        m = re.search(re.escape(timing) + r"\s+(.*?)\s+ON\s+", s, re.I | re.S)
        if m:
            events = tuple(sorted(
                e.strip().upper()
                for e in re.split(r"\s+OR\s+", m.group(1), flags=re.I)
                if e.strip().upper() in ("INSERT", "UPDATE", "DELETE", "TRUNCATE")))
    m = re.search(r"FOR EACH (ROW|STATEMENT)", s, re.I)
    level = m.group(1).upper() if m else "?"
    return (timing, events, level,
            "CREATE CONSTRAINT TRIGGER" in up,
            bool(re.search(r"\bWHEN\s*\(", s, re.I)))


def compare_trigger_funcs(go_t, rag_t, go_tf, rag_tf):
    """⇒ (形式实质差异, 函数属性实质差异, 单边触发器的属性明细)

    ⚠️ **单边触发器不在这里计失败** —— 单边触发器已由「触发器集合」维度计过一次
       ⇒ 这里再计一次就是**重复计数**（会污染退出码语义）。
       单边的只**打印**（让人看见它长什么样），供人工核对用。

    「形式」= timing / 事件集 / 粒度 / constraint / WHEN；
    「函数属性」= 函数名 / 返回类型 / 参数列表 / volatility / 是否 trigger / 语言。
    """
    form_diffs, func_diffs, one_sided = [], [], []
    for k in sorted(set(go_t) & set(rag_t)):
        gf, rf = parse_trigger_form(go_t[k]), parse_trigger_form(rag_t[k])
        if gf != rf:
            detail = "; ".join(
                f"{_TRIGGER_FORM_FIELDS[i]} (go={gf[i]} rag={rf[i]})"
                for i in range(len(gf)) if gf[i] != rf[i])
            form_diffs.append((f"{k[0]}.{k[1]}", gf, rf, detail))
        g, r = go_tf.get(k), rag_tf.get(k)
        if g is None or r is None:
            # 触发器在、但函数属性查不到 ⇒ 环境异常（不该发生）⇒ 记成单边
            one_sided.append((k, g, r))
            continue
        if g != r:
            fields = ("函数名", "返回类型", "参数列表", "volatility", "是否RETURNS trigger", "语言")
            detail = "; ".join(
                f"{fields[i]} (go={g[i]} rag={r[i]})"
                for i in range(min(len(g), len(r))) if g[i] != r[i])
            func_diffs.append((f"{k[0]}.{k[1]}", g, r, detail))
    for k in sorted(set(go_t) - set(rag_t)):
        one_sided.append((k, go_tf.get(k), None))
    for k in sorted(set(rag_t) - set(go_t)):
        one_sided.append((k, None, rag_tf.get(k)))
    return form_diffs, func_diffs, one_sided


def _fmt_func_attrs(attrs):
    if attrs is None:
        return "<无>"
    names = ("fn", "rettype", "args", "volatility", "is_trigger", "lang")
    parts = []
    for n, v in zip(names, attrs):
        parts.append(f"{n}={v if v != '' else '<无>'}")
    return " ".join(parts)


def compare_constraints(go_k, rag_k, go_i=None, rag_i=None):
    """⇒ (实质差异, 语义等价的命名差)

    **语义等价** = 同表 + 同 contype + 同定义文本，只有名字不同
    （典型：`<表>_pkey` ↔ `pk_<表>`）⇒ PG 层面的唯一性判定 / 冲突行为 /
    外键行为完全相同 ⇒ 不算差异。

    **载体不同也算等价**（2026-10-25 加）：**唯一索引 vs 唯一约束** ——
    Go 侧 `CREATE UNIQUE INDEX uk_x` 与 RAG 侧 `CONSTRAINT uk_x UNIQUE (...)`
    在 PG 里的**唯一性判定、23505 报错、ON CONFLICT 仲裁完全相同**
    ⇒ 只有「能不能写 `ON CONFLICT ON CONSTRAINT uk_x`」这一个语法差别。
    ⇒ 判据：单边多出来的 contype='u' 约束，在另一侧能找到一个**同名、同列、
       unique=True** 的索引 ⇒ 判为等价。

    **实质差异** = 只有一侧有（且另一侧没有等价项），或两侧同名但定义不同。
    """
    diffs, equiv = [], []
    go_i = go_i or {}
    rag_i = rag_i or {}
    only_go = sorted(set(go_k) - set(rag_k))
    only_rag = sorted(set(rag_k) - set(go_k))
    # 先把「同表 + 同 contype + 同定义」的配对起来 —— 剩下的才是真的单边缺失
    paired_go, paired_rag = set(), set()
    for k in only_go:
        g = go_k[k]
        twins = [r for r in only_rag
                 if r not in paired_rag and rag_k[r][0] == g[0] and rag_k[r][1] == g[1]]
        if twins:
            t = twins[0]
            equiv.append((f"{k[0]}.{k[1]} ≡ {t[0]}.{t[1]}", g, rag_k[t]))
            paired_go.add(k)
            paired_rag.add(t)
    # 「唯一索引 ↔ 唯一约束」载体不同 ⇒ 等价
    # ⚠️ 名字**也可以不同**（例：Go 的 `idx_admins_uid` 唯一索引 ↔ RAG 的
    #    `admin_admin_uid_key` 唯一约束）⇒ 判据是「同表 + 同列 + 另一侧 unique 索引」。
    for k in only_go:
        if k in paired_go or go_k[k][0] != 'u':
            continue
        cand = [n for n, (tbl, uniq, cols) in rag_i.items()
                if uniq and tbl == k[0] and _con_cols(go_k[k][1]) == cols]
        if cand:
            equiv.append((f"{k[0]}.{k[1]} ≡ {cand[0]}（唯一索引 ↔ 唯一约束，载体不同）",
                          go_k[k], ("u", f"UNIQUE {list(rag_i[cand[0]][2])}")))
            paired_go.add(k)
    for k in only_rag:
        if k in paired_rag or rag_k[k][0] != 'u':
            continue
        cand = [n for n, (tbl, uniq, cols) in go_i.items()
                if uniq and tbl == k[0] and _con_cols(rag_k[k][1]) == cols]
        if cand:
            equiv.append((f"{k[0]}.{k[1]} ≡ {cand[0]}（唯一约束 ↔ 唯一索引，载体不同）",
                          ("u", f"UNIQUE {list(go_i[cand[0]][2])}"), rag_k[k]))
            paired_rag.add(k)
    for k in only_go:
        if k not in paired_go:
            diffs.append((f"{k[0]}.{k[1]}（仅 Go 有）", go_k[k], None))
    for k in only_rag:
        if k not in paired_rag:
            diffs.append((f"{k[0]}.{k[1]}（仅 RAG 有）", None, rag_k[k]))
    # 同名但定义不同 ⇒ 实质差异
    for k in sorted(set(go_k) & set(rag_k)):
        if go_k[k] != rag_k[k]:
            diffs.append((f"{k[0]}.{k[1]}（同名定义不同）", go_k[k], rag_k[k]))
    return diffs, equiv


_SEQ_RE = re.compile(r"nextval\('([^']+)'::regclass\)")


def _norm_seq(condef):
    """把 DEFAULT 里的序列名抹平 ⇒ 只剩「是不是本表主键序列」这一个语义"""
    if condef is None:
        return None
    return _SEQ_RE.sub("nextval('<SEQ>'::regclass)", condef)


def _con_cols(condef):
    """从 pg_get_constraintdef 的文本里抽出列名元组（仅处理 UNIQUE (...)）"""
    m = re.search(r"UNIQUE\s*\((.*?)\)", condef or "", re.S)
    if not m:
        return None
    return tuple(c.strip().split()[0].strip('"') for c in m.group(1).split(","))


def compare_indexes(go_idx, rag_idx, allow):
    """⇒ (实质差异, 命中豁免的明细, 语义等价的命名差)

    差异分三类：
      ① 同名但定义不同（唯一性或列不同）—— **实质差异**
      ② 名字不同但「表 + 唯一性 + 列」完全等价 ⇒ **行为等价，仅名字不同**（不是缺失）
      ③ 真的只有一侧有（既没有同名项，也没有等价项）—— **实质差异**
    """
    diffs, waived, equiv = [], [], []
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
                equiv.append((f"{name} ≡ {t}", g, rag_idx[t]))
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
    return diffs, waived, equiv


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
        go_t = load_triggers(container, go_db, table_map, exclude_tables, pg_user) if args.indexes else {}
        rag_t = load_triggers(container, rag_db, table_map, exclude_tables, pg_user) if args.indexes else {}
        go_tf = load_trigger_funcs(container, go_db, table_map, exclude_tables, pg_user) if args.indexes else {}
        rag_tf = load_trigger_funcs(container, rag_db, table_map, exclude_tables, pg_user) if args.indexes else {}
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"FATAL: 读不到 {container} 里的库 —— {e}\n{cfg['prepare_hint']}")
        return 2

    print(f"== 域: {args.domain}  容器: {container}  "
          f"Go库: {go_db}  RAG库: {rag_db} ==")

    # ---- 环境闸门 ①：本域是否根本不适用（N/A）----------------------------
    # ⚠️ 「两侧都是 0 张表」**不是**「DDL 已对齐」—— 那只说明这个域没有数据库
    #    （hot 域就是这种：Go 侧无 gorm，RAG 侧无 migration）。
    #    ⇒ 单独给退出码 3，别让 EXIT=0 冒充「已对齐」。
    expected = cfg.get("expected_tables")
    go_tables = {k.split(".", 1)[0] for k in go}
    rag_tables = {k.split(".", 1)[0] for k in rag}
    if expected is not None and not expected and not go_tables and not rag_tables:
        print("⚠️ 本域两侧都没有业务表 ⇒ **DDL 维度不适用（N/A）**，不是「已对齐」。")
        print(cfg["note"])
        return 3

    # ---- 环境闸门 ②：预期业务表是否都在（少一张 = 环境没搭好，不是漂移）----
    if expected:
        missing_go = sorted(set(expected) - go_tables)
        missing_rag = sorted(set(expected) - rag_tables)
        if missing_go or missing_rag:
            print("FATAL: 预期业务表缺失 —— 环境没搭好（迁移没跑全 / 探针没跑），"
                  "**不是漂移**")
            for t in missing_go:
                print(f"  Go 侧缺:  {t}")
            for t in missing_rag:
                print(f"  RAG 侧缺:  {t}")
            print(cfg["prepare_hint"])
            return 2

    only_go = sorted(set(go) - set(rag))
    only_rag = sorted(set(rag) - set(go))
    if only_go or only_rag:
        print("列集合不一致 —— 三种可能，**别默认当成漂移**："
              "① 表名映射写错 ② 某一侧 schema 没初始化 ③ 某一侧**真的多/少一张表**（真漂移）")
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
    nullable_diffs, default_diffs, seq_diffs = [], [], []
    for k in sorted(go_c):
        if k not in rag_c:
            nullable_diffs.append((k, go_c[k], ("<缺列>", "")))
            continue
        g_null, g_def = go_c[k]
        r_null, r_def = rag_c[k]
        if g_null != r_null:
            nullable_diffs.append((k, go_c[k], rag_c[k]))
        if g_def != r_def:
            # ⚠️ **序列名映射的副产品不算真差**：两侧表名不同 ⇒ PG 建的序列名也不同
            #    （Go `users_id_seq` ↔ RAG `users_user_id_seq`）⇒ DEFAULT 文本必然不同，
            #    但两者都指向「本表主键的下一值」⇒ 语义完全等价。
            if _norm_seq(g_def) == _norm_seq(r_def):
                seq_diffs.append((k, go_c[k], rag_c[k]))
            else:
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
    if seq_diffs:
        print(f"  序列名映射的副产品（**非真差**，不计失败）{len(seq_diffs)} 处：")
        for k, g, r in seq_diffs:
            print(f"    {k:40s} go={g[1]:30s} rag={r[1]}")

    failures = len(diffs) + len(nullable_diffs)
    failures += len([d for d in default_diffs if d[0] not in allow_default])

    # ---- 索引 / 唯一约束 / 触发器（--indexes 才查）----
    if args.indexes:
        i_diffs, i_waived, i_equiv = compare_indexes(go_i, rag_i, allow)
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
        if i_equiv:
            print(f"  语义等价（仅命名形态差，**不计失败**）{len(i_equiv)} 处：")
            for label, g, r in i_equiv:
                gs = f"unique={g[1]} cols={list(g[2])}"
                rs = f"unique={r[1]} cols={list(r[2])}"
                print(f"    {label:62s} go={gs:34s} rag={rs}")

        k_diffs, k_equiv = compare_constraints(go_k, rag_k, go_i, rag_i)
        k_same = sorted(set(go_k) & set(rag_k))
        print(f"\n对照 · 约束（pg_constraint）：两侧各 {len(go_k)}/{len(rag_k)} 行")
        print(f"  逐条一致 {len(k_same)} 行 ✓")
        for label, g, r in k_diffs:
            if g:
                print(f"  实质差异: {label}  contype={g[0]}  {g[1]}")
            else:
                print(f"  实质差异: {label}  contype={r[0]}  {r[1]}")
        if not k_diffs:
            print("  （两侧约束集合完全一致 ✓）")
        if k_equiv:
            print(f"  语义等价（仅命名形态差，**不计失败**）{len(k_equiv)} 处：")
            for label, g, r in k_equiv:
                print(f"    {label:62s} contype={g[0]}  {g[1]}")

        t_only_go = sorted(set(go_t) - set(rag_t))
        t_only_rag = sorted(set(rag_t) - set(go_t))
        t_same = sorted(set(go_t) & set(rag_t))
        print(f"\n对照 · 触发器（pg_trigger，额外维度）：两侧各 {len(go_t)}/{len(rag_t)} 个")
        print(f"  同名同表 {len(t_same)} 个 ✓")
        for k in t_only_go:
            print(f"  仅 Go 有:  {k[0]}.{k[1]}  {go_t[k]}")
        for k in t_only_rag:
            print(f"  仅 RAG 有: {k[0]}.{k[1]}  {rag_t[k]}")
        if not t_only_go and not t_only_rag:
            print("  （两侧触发器集合完全一致 ✓）")
        failures += len(t_only_go) + len(t_only_rag)

        # ---- 触发器形式 + 触发器函数体的**结构化语义属性**（2026-10-26 L10 新增）----
        # ⚠️ **不比 SQL 文本**：函数体逐字比会把「写法不同但语义相同」报成差异。
        #    机器只比「形式」与「函数属性」⇒ 语义核对仍需**人工逐条**（台账 §8.2）。
        form_diffs, func_diffs, one_sided = compare_trigger_funcs(
            go_t, rag_t, go_tf, rag_tf)
        paired = len(set(go_t) & set(rag_t))
        print(f"\n对照 · 触发器形式（pg_get_triggerdef → 结构化属性，不比文本）："
              f"两侧各 {len(go_t)}/{len(rag_t)} 个，**可比对 {paired} 个**")
        if form_diffs:
            for label, g, r, detail in form_diffs:
                print(f"  实质差异: {label}")
                print(f"      go  = {g}")
                print(f"      rag = {r}")
                print(f"      ⇒ {detail}")
        elif paired:
            print(f"  （{paired} 个可比对的触发器形式逐条一致 ✓）")
        else:
            print("  ⚠️ 两侧**没有可成对比对的触发器** ⇒ 本维度**什么都没比**"
                  "（不是「已对齐」）")

        print(f"\n对照 · 触发器函数体（pg_proc → 结构化语义属性，**不比 SQL 文本**）："
              f"两侧各 {len(go_tf)}/{len(rag_tf)} 个，**可比对 {paired} 个**")
        if func_diffs:
            for label, g, r, detail in func_diffs:
                print(f"  实质差异: {label}")
                print(f"      go  = {_fmt_func_attrs(g)}")
                print(f"      rag = {_fmt_func_attrs(r)}")
                print(f"      ⇒ {detail}")
        elif paired:
            print(f"  （{paired} 个可比对的函数属性逐条一致 ✓）")
        else:
            print("  ⚠️ 两侧**没有可成对比对的触发器函数** ⇒ 本维度**什么都没比**"
                  "（不是「已对齐」）")
        for k in sorted(set(go_tf) & set(rag_tf)):
            print(f"  ✓ {k[0]}.{k[1]}")
            print(f"      go  = {_fmt_func_attrs(go_tf[k])}")
            print(f"      rag = {_fmt_func_attrs(rag_tf[k])}")
        if one_sided:
            print("  单边触发器的函数属性（**不在此计失败** —— 已由上面的"
                  "「触发器集合」维度计过）：")
            for k, g, r in one_sided:
                print(f"    · {k[0]}.{k[1]}")
                if g is not None:
                    print(f"        go  = {_fmt_func_attrs(g)}")
                if r is not None:
                    print(f"        rag = {_fmt_func_attrs(r)}")
        failures += len(form_diffs) + len(func_diffs)
        # ⚠️ 实质差异才计失败：索引的「仅一侧有 / 同名定义不同」+ 约束的「仅一侧有 /
        #    同名定义不同」⇒ 计；「语义等价（命名形态差 / 唯一索引↔唯一约束）」⇒ 不计。
        failures += len(i_diffs) - len(i_waived)
        failures += len(k_diffs)
    else:
        print("\nNOT CHECKED（脚本没查，别把本脚本的结论读成「DDL 已一致」）："
              "索引 / 唯一约束 / 约束 / 触发器 / 触发器形式 / 触发器函数体属性。"
              "加 --indexes 可核。")

    if diffs:
        print()
        print(cfg["note"])
    print(f"\n== 实质差异合计 {failures} 处 ⇒ 退出码 {1 if failures else 0} ==")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
