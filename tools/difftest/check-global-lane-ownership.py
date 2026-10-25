#!/usr/bin/env python3
"""守门：`global` 多 lane 的写权范围必须**两两不重叠**，且共享文件**无人拥有**。

背景
----
`global` 收口域 2026-10-25 起拆成 5 条 lane 并行推进（协议见
`docs/DRIFT/global/lanes/_PROTOCOL.md`）。并行能成立的前提只有一个：
**每条 lane 只写自己白名单里的文件** ⇒ 合并时零冲突。

⚠️ 本守门查的是**写权范围**（白名单），不是「实际改了哪些文件」——
后者靠 git 的合并冲突兜底，前者才是需要守的**契约**。

查什么
------
1. **白名单两两不重叠** —— 用 glob 展开成**实际文件集合**后求交集。
   ⚠️ 必须展开成文件集合再比：光比 pattern 字符串会漏掉
   「`tools/difftest/` 下新脚本」这种**目录级**写法（L3/L4 同写 `tools/difftest/`）。
2. **共享文件无人拥有** —— `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` /
   4 个规范台账 / `tools/difftest/README.md` 必须**不在任何 lane 的白名单里**
   ⇒ 它们由**整合者**在合并后统一更新（§5.3 同款纪律）。
3. **favorite 域零写权** —— `service/favorite/**` 与 `docs/*/favorite/` 不得出现在任何白名单。

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
LANES: dict[str, list[str]] = {
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

# ---------------------------------------------------------------- 共享文件
# 这些**不属于任何 lane** ⇒ 只能由整合者在合并后统一更新。
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


def expand(pattern: str) -> set[str]:
    """把白名单条目展开成**实际存在的文件集合**。

    ⚠️ 目录级写法（`a/b/**`）必须展开成文件集合才能比出重叠 ——
    「`tools/difftest/` 下新脚本」这种写法在 pattern 层面看不出跟谁撞。
    """
    if pattern.endswith("/**"):
        # `a/b/**` ⇒ 该目录下的**全部文件**（含子目录）
        base = str(REPO / pattern[:-3])
        hits = set(glob.glob(base + "/**/*", recursive=True))
        return {p for p in hits if os.path.isfile(p)}

    if "**" in pattern:
        hits = set(glob.glob(str(REPO / pattern), recursive=True))
        return {p for p in hits if os.path.isfile(p)}

    direct = REPO / pattern
    if direct.is_file():
        return {pattern}
    # 尚不存在的新文件（lane 将来会创建的）⇒ 展开同名前缀的兄弟文件
    # ⚠️ 用 glob 而不是 pathlib.glob：pattern 本身可能自带 `*`（如 `ddl*`），
    #    再拼一个 `*` 会变成 `ddl**` ⇒ 非法 glob（`**` 只能独占一个路径段）。
    if direct.parent.is_dir():
        hits = set(glob.glob(str(direct)))
        rel = {
            str(Path(p).relative_to(REPO))
            for p in hits
            if os.path.isfile(p)
        }
        return {pattern} | rel
    return {pattern}


def main() -> int:
    expanded = {lane: set() for lane in LANES}
    for lane, patterns in LANES.items():
        for pat in patterns:
            expanded[lane] |= expand(pat)

    failures: list[str] = []

    # ---- 1. 两两不重叠 --------------------------------------------------
    names = list(LANES)
    for i, a in enumerate(names):
        for b in names[i + 1 :]:
            overlap = sorted(expanded[a] & expanded[b])
            if overlap:
                failures.append(
                    f"🔴 写权重叠：`{a}` ∩ `{b}` = {len(overlap)} 个文件\n"
                    + "\n".join(f"     · {p}" for p in overlap[:10])
                    + ("\n     …" if len(overlap) > 10 else "")
                )

    # ---- 2. 共享文件无人拥有 -------------------------------------------
    for lane in names:
        for shared in SHARED:
            if shared in expanded[lane]:
                failures.append(
                    f"🔴 共享文件被 lane 占用：`{lane}` 声明了 `{shared}`"
                    " ⇒ 共享文件只能由整合者在合并后统一更新"
                )

    # ---- 3. favorite 零写权 --------------------------------------------
    for lane in names:
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
    for lane in names:
        print(f"  {lane:<14} 白名单展开 {len(expanded[lane]):>4} 个文件")
    print("-" * 60)

    if failures:
        print("❌ FAIL\n")
        for f in failures:
            print(f)
            print()
        print(f"⇒ {len(failures)} 项违规")
        return 1

    print("✅ PASS")
    print("  · 5 条 lane 的写权范围两两不重叠")
    print("  · 10 个共享文件无人拥有（归整合者）")
    print("  · favorite 域零写权")
    return 0


if __name__ == "__main__":
    sys.exit(main())
