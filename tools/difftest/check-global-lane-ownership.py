#!/usr/bin/env python3
"""守门：`global` 多 lane 的写权范围必须**两两不重叠**，且共享文件**无人拥有**。

背景
----
`global` 收口域 2026-10-25 起拆成 lane 并行推进（协议见
`docs/DRIFT/global/lanes/_PROTOCOL.md`）。并行能成立的前提只有一个：
**每条 lane 只写自己白名单里的文件** ⇒ 合并时零冲突。

⚠️ 本守门查的是**写权范围**（白名单），不是「实际改了哪些文件」——
后者靠 git 的合并冲突兜底，前者才是需要守的**契约**。

⚠️ **2026-10-26 拆分：在跑 / 已合回 分开断言**
第二十七轮新开 6 条 lane（L6~L11），而**已合回**的 L1~L5 里，
L2 曾拥有 `user-rpc/src/test/**`、L1 曾拥有 `like-rpc/**` / `comment-rpc/**`
—— 与在跑的 L7 / L8 **白名单必然重叠**。
⇒ 但这**不是违规**：已合回的 lane **不再是并发方** ⇒ 重叠只在「在跑」的 lane 之间算违规。
⇒ 若不分档，守门会恒红 ⇒ 恒红的守门等于没有守门。

查什么
------
1. **在跑的 lane 白名单两两不重叠** —— 用 glob 展开成**实际文件集合**后求交集。
   ⚠️ 必须展开成文件集合再比：光比 pattern 字符串会漏掉
   「`tools/difftest/` 下新脚本」这种**目录级**写法（L10/L11 同写 `tools/difftest/`）。
2. **共享文件无人拥有** —— `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` /
   4 个规范台账 / `tools/difftest/README.md` 必须**不在任何 lane 的白名单里**
   ⇒ 它们由**合并收口执行者**在合并后统一更新（§5.3 同款纪律）。
   ⇒ ⚠️ **「合并收口执行者」是一个步骤，不是一个常驻会话** ⇒ lane 不该去找任何叫「整合者」的会话
   ⇒ 角色表与 lane 的正确动作 ⇒ `docs/Worktree.md` §1e。
3. **favorite 域零写权** —— `service/favorite/**` 与 `docs/*/favorite/` 不得出现在任何白名单。

⚠️ **2026-10-27：`global-common` 登记 ⇒ 首次点亮「在跑」分支 ⇒ 抓出一条假绿**
新登记的 `global-common`（`service/common/**` 的**唯一**写权 owner）让 `ACTIVE` 不再为空，
于是断言 1 的两两比较**第一次被执行** ⇒ 变异测试当场抓到：
`expand()` 的**目录分支返回绝对路径**、**直接文件分支返回相对路径** ⇒ 两边永不相交
⇒ 断言 1 与断言 2 **一直是假绿**（详见 [`docs/Pitfalls/global/cross-domain.md`](../../docs/Pitfalls/global/cross-domain.md)
的 `PIT-GLOBAL-067`）。
⇒ 修法 = 新增 `_rel()`，所有分支统一返回「仓库相对 + POSIX 分隔符」；
⇒ **改动后各 lane 的「白名单展开 N 个文件」计数一个都没变**（只改形态 ⇒ 无副作用）；
⇒ 变异 A（第二条 lane 直接写目录内文件）⇒ `EXIT=1` 且精确点名；
⇒ 变异 B（第二条 lane 用目录通配吃共享文件）⇒ `EXIT=1`（5 项违规）。

⚠️ **「新增登记」必须配一次变异** —— 否则新登记的可能只是一道**假绿灯**
（同源 `PIT-GLOBAL-046` / `PIT-GLOBAL-048`）。

退出码
------
0 = 全部通过；1 = 有重叠 / 有共享文件被占用 / 碰了 favorite。

复现
----
python3 tools/difftest/check-global-lane-ownership.py
"""

from __future__ import annotations

import glob
import os
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# ---------------------------------------------------------------- lane 白名单
# ⚠️ 与 docs/DRIFT/global/lanes/*.md 的「写权范围」一一对应；改这里必须同步改那里。
# ⚠️ **在跑的 lane**（ACTIVE）：重叠只在这些之间算违规。
ACTIVE: dict[str, list[str]] = {
    # ⚠️ **2026-10-27 01:44 收口**：工作树重组为 **G1~G4 四棵波次工作树**
    #    （`align/g1-users-follow` / `align/g2-message-like-task` /
    #    `align/g3-article-comment-favorite-hot` / `align/g4-service-common`）
    #    ⇒ 原 `global-common` 那条 lane **已随工作树一起删除**（分支也不再存在）。
    # ⇒ **并发不变量的守门已迁到 `tools/difftest/check-wave-lane-ownership.py`**
    #    （那个守门管 G1~G4 的写权 + 「白名单路径必须命中真实文件」+ 「分支必须真实存在」）。
    # ⇒ **本守门退回为 `global` 域内历史 lane 的归档**（L1~L15 全部已合回）。
    # ⚠️ **「已删分支仍登记在 ACTIVE」＝ 恒绿假绿**（2026-10-27 实测踩到）：
    #    `global-common` 被删后本守门照样 EXIT=0 ⇒ 故新增的守门加了「分支必须真实存在」断言。
    # ---- 第二十七轮（2026-10-26）：6 条新 lane，按**模块**零重叠 ----
    # ---- 第二十八轮（2026-10-26）：4 条 lane，按**模块**零重叠 ----
}

# ⚠️ **已合回**的 lane（CLOSED）：它们的写权已并入 `align/global` ⇒ 不是并发方
#    ⇒ 与在跑的 lane 重叠**不算违规**（但仍要守「共享文件无人拥有」与「favorite 零写权」）。
#    留档是为了让「声明」与「历史」可对照，不是为了参与重叠判定。
CLOSED: dict[str, list[str]] = {
    # ---- 2026-10-27：`global-common`（`<域>-common` lane 体系里的 global 那一条）----
    # ⚠️ 它曾登记在 `ACTIVE`，2026-10-27 01:44 **随工作树重组一起删除**
    #    （写权已迁到 `align/g4-service-common` ⇒ 见 `check-wave-lane-ownership.py`）。
    # ⚠️ 移入本档是因为**它曾经真实存在过**（不是「登记了但分支不存在」）。
    "global-common": [
        "service/common/pom.xml",
        "service/common/src/**",
        "docs/DRIFT/global/vs-common.md",
    ],
    # ---- 第二十八轮（2026-10-26）：4 条 lane，**已全部合回** ⇒ 移入本档 ----
    # ⚠️ 它们与在跑 lane 白名单**必然重叠**（同模块的 test 目录）⇒ 但**已合回的不是并发方**
    # ⚠️ 本轮修的是**生产启动阻塞**（不是守门）⇒ 白名单含 `src/main/resources` 与 `pom.xml`。
    # ⚠️ L12/L13/L14 各占互不相交的 **maven 模块**（按模块零重叠 ⇒ 不互踩 `target/`）。
    # ⚠️ L15 只碰 `tools/difftest/` 的**指定文件**（不是目录级）⇒ 不与任何 service lane 相撞。
    "L12-rpcboot-a": [
        "service/article/article-rpc/pom.xml",
        "service/article/article-rpc/src/main/resources/application.yml",
        "service/article/article-rpc/src/test/**",
        "service/comment/comment-rpc/src/main/resources/application.yml",
        "service/comment/comment-rpc/src/test/**",
        "service/like/like-rpc/pom.xml",
        "service/like/like-rpc/src/main/resources/application.yml",
        "service/like/like-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L12-rpcboot-article-comment-like.md",
    ],
    "L13-rpcboot-b": [
        "service/message/message-rpc/src/main/resources/application.yml",
        "service/message/message-rpc/src/test/**",
        "service/task/task-rpc/pom.xml",
        "service/task/task-rpc/src/main/resources/application.yml",
        "service/task/task-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L13-rpcboot-message-task.md",
    ],
    "L14-rpcboot-hot": [
        "service/hot/hot-rpc/pom.xml",
        "service/hot/hot-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L14-rpcboot-hot.md",
    ],
    "L15-rpcboot-tools": [
        "tools/difftest/check-jar-executable.sh",
        "tools/difftest/check-grpc-runtime-classpath.py",
        "tools/difftest/baselines/grpc-runtime-classpath.txt",
        "docs/DRIFT/global/lanes/L15-rpcboot-tools.md",
    ],
    # ---- 第二十七轮（2026-10-26）：6 条 lane，**已全部合回** ⇒ 移入本档 ----
    # ⚠️ 它们与第二十八轮在跑的 L12~L15 白名单**必然重叠**（同模块的 test 目录）
    #    ⇒ 但**已合回的不是并发方** ⇒ 重叠不算违规（与 L2~L5 同款）。
    # ⚠️ 「按模块零重叠」而不是纯按文件：同模块两个 lane 会互踩 `target/`。
    "L6-x5gate": [
        "service/users/admin/admin-rpc/src/test/**",
        "service/article/article-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L6-x5gate.md",
    ],
    "L7-slowgate": [
        "service/comment/comment-rpc/src/test/**",
        "service/like/like-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L7-slowgate.md",
    ],
    "L8-slowgate2": [
        "service/users/user/user-rpc/src/test/**",
        "service/message/message-rpc/src/test/**",
        "service/hot/hot-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L8-slowgate2.md",
    ],
    "L9-interrupt": [
        "service/task/task-rpc/src/test/**",
        "service/follow/follow-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L9-interrupt.md",
    ],
    "L10-ddldeep": [
        "tools/difftest/check-ddl-static.py",
        "tools/difftest/ddl*",
        "docs/DRIFT/global/lanes/L10-ddldeep.md",
    ],
    "L11-flywaydeep": [
        "tools/difftest/check-flyway-concurrent.sh",
        "tools/difftest/flyway*",
        "tools/difftest/check-flyway-ledger.py",
        "docs/DRIFT/global/lanes/L11-flywaydeep.md",
    ],
    # ⚠️ 2026-10-25 订正：L1 在 T-11 / T-13 两轮里把写权扩到了 `service/common`
    #    （observability / http / pom / resources / test）⇒ 当时**只记账没登记** ⇒
    #    这里补上，使「声明」与「实际」一致（否则守门给的是假保证）。
    "L1-delivery": [
        "service/like/like-rpc/**",
        "service/comment/comment-rpc/**",
        "service/common/communityfact/Dispatcher.java",
        "service/common/pom.xml",
        "service/common/src/main/java/com/rag/common/http/**",
        "service/common/src/main/java/com/rag/common/observability/**",
        "service/common/src/main/resources/**",
        "service/common/src/test/java/com/rag/common/http/**",
        "service/common/src/test/java/com/rag/common/observability/**",
        "docs/DRIFT/global/lanes/L1-delivery.md",
    ],
    # ⚠️ 2026-10-25 订正：L2 的 slow-threshold 守门原计划选 admin-rpc
    #    ⇒ admin-rpc 的 test 目录不在原白名单内 ⇒ 2026-10-25 补进白名单
    #    （admin-rpc 的**生产代码**仍不归 L2 ⇒ 只能加测试）。
    "L2-rpcgate": [
        "service/users/user/user-rpc/src/test/**",
        "service/users/admin/admin-rpc/src/test/**",
        "service/message/message-rpc/src/test/**",
        "service/hot/hot-rpc/src/test/**",
        "docs/DRIFT/global/lanes/L2-rpcgate.md",
    ],
    "L3-ddl": [
        "tools/difftest/check-ddl-static.py",
        "tools/difftest/ddl*",
        "docs/DRIFT/global/lanes/L3-ddl.md",
    ],
    "L4-flyway": [
        "tools/difftest/check-flyway-concurrent.sh",
        "tools/difftest/flyway*",
        "docs/DRIFT/global/lanes/L4-flyway.md",
    ],
    "L5-signoff": [
        "docs/Intentional_Deviations/global/**",
        "docs/Pitfalls/global/**",
        "docs/DRIFT/global/lanes/L5-signoff.md",
    ],
}

LANES = {**ACTIVE, **CLOSED}

# ---------------------------------------------------------------- 共享文件
# 这些**不属于任何 lane** ⇒ 只能由合并收口执行者在合并后统一更新。
# ⚠️ 原措辞「归整合者」已删（2026-10-27）：**「整合者」不是一个存在的角色** ⇒
#    它诱导 lane 去向上升级找一个不存在的人 ⇒ 改用可指向的「合并收口执行者」。
SHARED = [
    "AGENTS.md",
    "docs/DRIFT/_INDEX.md",
    "docs/Pitfalls/_INDEX.md",
    "docs/Intentional_Deviations/_INDEX.md",
    "docs/DRIFT/_MANIFEST.json",
    "docs/DRIFT/global/_README.md",
    "docs/DRIFT/global/common-tails.md",
    "docs/DRIFT/global/cross-domain.md",
    "docs/DRIFT/global/cycle-deps.md",
    "tools/difftest/README.md",
]

# ---------------------------------------------------------------- favorite 禁区
FORBIDDEN = [
    "service/favorite/**",
    "docs/DRIFT/favorite/**",
    "docs/Pitfalls/favorite/**",
    "docs/Intentional_Deviations/favorite/**",
]


def _rel(p: str) -> str:
    """把路径**归一成「仓库相对 + POSIX 分隔符」**。

    🔴 **为什么必须归一**（2026-10-27 实测缺陷）：
    本函数原先对**目录通配**返回**绝对路径**、对**直接文件**返回**相对路径**
    ⇒ 两边**永不相交** ⇒
      ① 断言 1「在跑 lane 白名单两两不重叠」对「一个写目录、另一个写目录内文件」**恒不报**；
      ② 断言 2「共享文件无人拥有」对**目录通配**的 lane **恒不报**。
    ⇒ 两条断言**一直是假绿** —— 只因 `ACTIVE` 长期为空、这条代码路径**从未被执行过**
    （2026-10-27 登记 `global-common` 才第一次点亮它 ⇒ 变异测试当场抓出）。
    ⇒ 修法：**所有分支统一返回仓库相对路径**。
    """
    try:
        return Path(p).resolve().relative_to(REPO).as_posix()
    except ValueError:
        # 不在仓库内（如 GOROOT 路径）⇒ 退回绝对路径的 POSIX 形态
        return Path(p).as_posix()


def expand(pattern: str) -> set[str]:
    """把白名单条目展开成**实际存在的文件集合**（**统一用仓库相对路径**）。

    ⚠️ 目录级写法（`a/b/**`）必须展开成文件集合才能比出重叠 ——
    「`tools/difftest/` 下新脚本」这种写法在 pattern 层面看不出跟谁撞。
    ⚠️ **返回值一律是仓库相对路径**（`_rel`）⇒ 不同分支之间才可比（见 `_rel` 的说明）。
    """
    if pattern.endswith("/**"):
        # `a/b/**` ⇒ 该目录下的**全部文件**（含子目录）
        base = str(REPO / pattern[:-3])
        hits = set(glob.glob(base + "/**/*", recursive=True))
        return {_rel(p) for p in hits if os.path.isfile(p)}

    if "**" in pattern:
        hits = set(glob.glob(str(REPO / pattern), recursive=True))
        return {_rel(p) for p in hits if os.path.isfile(p)}

    direct = REPO / pattern
    if direct.is_file():
        return {_rel(str(direct))}
    # 尚不存在的新文件（lane 将来会创建的）⇒ 展开同名前缀的兄弟文件
    # ⚠️ 用 glob 而不是 pathlib.glob：pattern 本身可能自带 `*`（如 `ddl*`），
    #    再拼一个 `*` 会变成 `ddl**` ⇒ 非法 glob（`**` 只能独占一个路径段）。
    if direct.parent.is_dir():
        hits = set(glob.glob(str(direct)))
        rel = {
            _rel(p)
            for p in hits
            if os.path.isfile(p)
        }
        return {_rel(str(direct))} | rel
    return {_rel(str(direct))}


def main() -> int:
    expanded_active = {lane: set() for lane in ACTIVE}
    for lane, patterns in ACTIVE.items():
        for pat in patterns:
            expanded_active[lane] |= expand(pat)

    expanded_closed = {lane: set() for lane in CLOSED}
    for lane, patterns in CLOSED.items():
        for pat in patterns:
            expanded_closed[lane] |= expand(pat)

    expanded = {**expanded_active, **expanded_closed}
    failures: list[str] = []

    # ---- 1. 在跑的 lane 两两不重叠 ----------------------------------------
    # ⚠️ 已合回的 lane **不参与**（它们的写权已并入 align/global，不是并发方）。
    names = list(ACTIVE)
    for i, a in enumerate(names):
        for b in names[i + 1 :]:
            overlap = sorted(expanded_active[a] & expanded_active[b])
            if overlap:
                failures.append(
                    f"🔴 写权重叠：`{a}` ∩ `{b}` = {len(overlap)} 个文件\n"
                    + "\n".join(f"     · {p}" for p in overlap[:10])
                    + ("\n     …" if len(overlap) > 10 else "")
                )

    # ---- 2. 共享文件无人拥有 -------------------------------------------
    for lane in list(ACTIVE) + list(CLOSED):
        for shared in SHARED:
            if shared in expanded[lane]:
                failures.append(
                    f"🔴 共享文件被 lane 占用：`{lane}` 声明了 `{shared}`"
                    " ⇒ 共享文件只能由合并收口执行者在合并后统一更新"
                )

    # ---- 3. favorite 零写权 --------------------------------------------
    for lane in list(ACTIVE) + list(CLOSED):
        for pat in LANES[lane]:
            norm = pat.rstrip("/*")
            for forbid in FORBIDDEN:
                f = forbid.rstrip("/*")
                if norm == f or norm.startswith(f + "/") or f.startswith(norm + "/"):
                    failures.append(
                        f"🔴 favorite 禁区被占用：`{lane}` 声明了 `{pat}`"
                        f"（命中 `{forbid}`）⇒ favorite 域对 global 零写权"
                    )

    # ---- 输出 -----------------------------------------------------------
    print("global 多 lane 写权守门")
    print("=" * 60)
    for lane in ACTIVE:
        print(f"  {lane:<16} [在跑]   白名单展开 {len(expanded_active[lane]):>4} 个文件")
    for lane in CLOSED:
        print(f"  {lane:<16} [已合回] 白名单展开 {len(expanded_closed[lane]):>4} 个文件")
    print("-" * 60)

    if failures:
        print("❌ FAIL\n")
        for f in failures:
            print(f)
            print()
        print(f"⇒ {len(failures)} 项违规")
        return 1

    print("✅ PASS")
    if not ACTIVE:
        print("  · ⚠️ **本守门当前没有在跑 lane** ⇒ 断言 1「两两不重叠」**空跑**")
        print("    ⇒ 并发不变量的守门在 `tools/difftest/check-wave-lane-ownership.py`（G1~G4）")
    else:
        print(f"  · 在跑的 {len(ACTIVE)} 条 lane 写权范围两两不重叠")
    print(f"  · 已合回的 {len(CLOSED)} 条 lane 单列归档（不参与重叠判定）")
    print(f"  · {len(SHARED)} 个共享文件无人拥有（归合并收口执行者）")
    print("  · favorite 域零写权")
    return 0


if __name__ == "__main__":
    sys.exit(main())
