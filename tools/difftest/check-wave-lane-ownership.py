#!/usr/bin/env python3
"""守门：**波次 lane（G1~G4）的写权范围必须两两不重叠**，共享文件无人拥有，共享层只有 G4 能写。

背景
----
2026-10-27 Jason 定的执行形态：**4 棵工作树**（不是 10 条 `<域>-common` lane）——

| lane | 分支 / 工作树 | 覆盖 |
|---|---|---|
| **G1** | `align/g1-users-follow` | `users`（**不含 `users-common`**）+ `follow` |
| **G2** | `align/g2-message-like-task` | `message` + `like` + `task` |
| **G3** | `align/g3-article-comment-favorite-hot` | `article` + `comment` + `favorite` + `hot` |
| **G4** | `align/g4-service-common` | `service/common` + `service/users/users-common`（**共享层**） |

⇒ 并发能成立的前提只有一个：**每棵工作树只写自己白名单里的文件** ⇒ 合并时零冲突。

⚠️ **与 `check-global-lane-ownership.py` 的分工**：
那个守门管的是 **`global` 域内**按议题切的历史 lane（L1~L15，全部已合回 ⇒ 留档归档）；
**本守门管的是现在真正并发的 4 棵波次工作树** ⇒ 并发不变量的守门跟着工作树走。

⚠️ **为什么必须单独开一个守门**（2026-10-27 实测的假绿）：
旧守门的 `ACTIVE` 档登记的是 `global-common` ⇒ 2026-10-27 工作树重组后**那条分支已被删除**，
而守门**照样 EXIT=0** ⇒ **「登记的 lane 根本不存在」这种状态，恒绿**。
⇒ 本守门加了一条硬断言：**每条 ACTIVE lane 的分支必须真实存在**（断言 5）
⇒ 以后再出现「盯着一个已删分支」⇒ 直接转红。

查什么
------
1. **在跑的 lane 白名单两两不重叠** —— 用 glob 展开成**实际文件集合**后求交集。
   ⚠️ 必须展开成文件集合再比：光比 pattern 字符串会漏掉
   「`tools/difftest/` 下新脚本」这种**目录级**写法。
   ⚠️ 展开必须**统一成「仓库相对 + POSIX 分隔符」**（同源 `PIT-GLOBAL-067`：
   目录分支返回绝对路径、直接文件分支返回相对路径 ⇒ 两边永不相交 ⇒ 断言恒绿）。
2. **共享文件无人拥有** —— `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` /
   `docs/DRIFT/_cross/**` / `docs/Worktree.md` / `tools/difftest/README.md`
   ⇒ 它们由**合并收口执行者**在合并后统一更新（§5.3 同款纪律）。
   ⇒ ⚠️ **「合并收口执行者」是一个步骤，不是一个常驻会话** ⇒ lane 不该去找任何叫「整合者」的会话
   ⇒ 角色表与 lane 的正确动作 ⇒ `docs/Worktree.md` §1e。
3. **`favorite` 只归 G3** —— 其他 lane 不得声明 `service/favorite/**` 或 `docs/*/favorite/`。
4. **共享层只归 G4** —— 其他 lane 不得声明 `service/common/**` 或 `service/users/users-common/**`
   （AGENTS.md §5.3e·§5.3f：共享层写权唯一 + 域波期间只读）。
5. **每条白名单 pattern 必须命中真实文件** —— 路径写错（如 `service/users/user/src/**`，
   而该目录下其实是 `user-api`/`user-rpc`/`user-proto`）⇒ 展开 0 个文件 ⇒ **守门对这一面完全失明**。
6. **7 个真私有 `<域>-common` 无跨域引用** —— 别的域 import 了它 ⇒ 改那个库会炸掉工作树外的模块
   ⇒ 真私有必须真私有（不私有就该提级为共享层、归 G4）。
7. **每条 ACTIVE lane 的分支必须真实存在** —— 防「守门盯着已删分支」这种假绿。

退出码
------
0 = 全部通过；1 = 有重叠 / 共享文件被占用 / 越权 / 白名单路径落空 / 私有库被跨域引用 / lane 分支不存在。

复现
----
python3 tools/difftest/check-wave-lane-ownership.py
"""

from __future__ import annotations

import glob
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# ---------------------------------------------------------------- 波次 lane 白名单
# ⚠️ 与 `docs/DRIFT/_cross/cross-domain-coupling.md` §二·§三、
#    `docs/DRIFT/global/lanes/_PROTOCOL.md` §3.1 的「在跑」表一一对应。
# ⚠️ **改这里必须同步改那里**，否则守门与台账会各说各话。
# ⚠️ 白名单刻意用 `src/**` + `pom.xml` 而不是模块目录通配 ⇒ 避免把 `target/`
#    的**构建产物**算进写权集合（同源 `PIT-GLOBAL-029`）。
ACTIVE: dict[str, list[str]] = {
    # ---- G1：users（不含 users-common）+ follow ----
    # ⚠️ `service/users/user/` 下**没有 `src/`** —— 它是聚合目录，
    #    底下是 `user-api/` / `user-rpc/` / `user-proto/` 三个模块（`admin` 同款）。
    #    写成 `service/users/user/src/**` ⇒ 命中 0 个真实文件 ⇒ **守门对 G1 半盲**（实测踩到）。
    "g1-users-follow": [
        "service/users/user/pom.xml",
        "service/users/user/user-api/**",
        "service/users/user/user-rpc/**",
        "service/users/user/user-proto/**",
        "service/users/admin/pom.xml",
        "service/users/admin/admin-api/**",
        "service/users/admin/admin-rpc/**",
        "service/users/admin/admin-proto/**",
        "service/follow/pom.xml",
        "service/follow/follow-api/**",
        "service/follow/follow-rpc/**",
        "service/follow/follow-common/**",
        "service/follow/follow-proto/**",
        "docs/DRIFT/users/**",
        "docs/DRIFT/follow/**",
    ],
    # ---- G2：message + like + task ----
    "g2-message-like-task": [
        "service/message/pom.xml",
        "service/message/message-api/**",
        "service/message/message-rpc/**",
        "service/message/message-common/**",
        "service/message/message-proto/**",
        "service/like/pom.xml",
        "service/like/like-api/**",
        "service/like/like-rpc/**",
        "service/like/like-common/**",
        "service/like/like-proto/**",
        "service/task/pom.xml",
        "service/task/task-api/**",
        "service/task/task-rpc/**",
        "service/task/task-proto/**",
        "docs/DRIFT/message/**",
        "docs/DRIFT/like/**",
        "docs/DRIFT/task/**",
    ],
    # ---- G3：article + comment + favorite + hot ----
    "g3-article-comment-favorite-hot": [
        "service/article/pom.xml",
        "service/article/article-api/**",
        "service/article/article-rpc/**",
        "service/article/article-common/**",
        "service/article/article-proto/**",
        "service/comment/pom.xml",
        "service/comment/comment-api/**",
        "service/comment/comment-rpc/**",
        "service/comment/comment-common/**",
        "service/comment/comment-proto/**",
        "service/favorite/pom.xml",
        "service/favorite/favorite-api/**",
        "service/favorite/favorite-rpc/**",
        "service/favorite/favorite-common/**",
        "service/favorite/favorite-proto/**",
        "service/hot/pom.xml",
        "service/hot/hot-api/**",
        "service/hot/hot-rpc/**",
        "service/hot/hot-common/**",
        "service/hot/hot-proto/**",
        "docs/DRIFT/article/**",
        "docs/DRIFT/comment/**",
        "docs/DRIFT/favorite/**",
        "docs/DRIFT/hot/**",
    ],
    # ---- G4：共享层（service/common + users-common）----
    # ⚠️ `users-common` 物理位置在 `service/users/` 里，但它**跨 6 个外域**（§5.3f）
    #    ⇒ 写权归共享层波，**不归 G1**。
    # 🆕 2026-10-29：`docs/DRIFT/common/**` 补进本 lane 的白名单。
    #    理由：那是**共享层自己的组件台账**（`service/common` 的 L0 权威来源，
    #    被 `docs/PhaseGoal-judge.md` §三 A-2 的「权威来源」列直接引用）
    #    ⇒ 按 AGENTS.md §5.3b「`docs/DRIFT/<域>/<组件>.md` ⇒ ✅ 该域工作树自己写」，
    #    而 `common` 域的 lane 就是 G4 ⇒ ⇒ **原先白名单漏了它**。
    #    ⚠️ 且它**不与任何 lane 的白名单重叠**（G1~G3 都没有 `docs/DRIFT/common/**`）
    #    ⇒ ⇒ 补进白名单**不产生写权冲突**，只是把**既成事实**写进机器守门。
    "g4-service-common": [
        "service/common/pom.xml",
        "service/common/src/**",
        "service/users/users-common/pom.xml",
        "service/users/users-common/src/**",
        "docs/DRIFT/global/vs-common.md",
        "docs/DRIFT/common/**",
    ],
}

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
    "docs/DRIFT/_cross/cross-domain-coupling.md",
    "docs/DRIFT/_cross/domain-common-lanes.md",
    "docs/DRIFT/global/_README.md",
    "docs/DRIFT/global/common-tails.md",
    "docs/DRIFT/global/cross-domain.md",
    "docs/DRIFT/global/cycle-deps.md",
    "docs/Worktree.md",
    "tools/difftest/README.md",
]

# ---------------------------------------------------------------- 排他归属
# 某些面**只有一个 lane 能声明**；其余 lane 声明即越权。
EXCLUSIVE: list[tuple[str, list[str], str]] = [
    (
        "g3-article-comment-favorite-hot",
        ["service/favorite/**", "docs/DRIFT/favorite/**", "docs/Pitfalls/favorite/**"],
        "favorite 域只归 G3",
    ),
    (
        "g4-service-common",
        [
            "service/common/**",
            "service/users/users-common/**",
        ],
        "共享层（`service/common` + `users-common`）写权唯一，只归 G4",
    ),
]


# ---------------------------------------------------------------- 跨域引用扫描
def scan_private_common_cross_refs() -> list[tuple[str, str, str]]:
    """扫描「**别的域**引用了本域 `<域>-common` 的类」⇒ 跨工作树撞车的编译期耦合。

    🔴 **必须用 import 判定**（同源 `PIT-LANE-05`）：
    全文正则会把 javadoc 里的 `{@code com.rag.xxx.common.Yyy}` 当成真引用。
    ⇒ 只认 `^import\s+(?:static\s+)?([\w.]+)\s*;`。

    ⚠️ 只扫 **7 个真私有** `<域>-common`（排除 `service/common` 与 `users-common`
    —— 这两个**本来就是共享层**，跨域引用是设计意图）。
    """
    hits: set[tuple[str, str, str]] = set()
    for mod in sorted(glob.glob(str(REPO / "service/*/*-common"))):
        if not Path(mod, "pom.xml").is_file():
            continue
        if mod.endswith("/users-common"):
            continue
        owner = Path(mod).resolve().relative_to(REPO).as_posix().split("/")[1]
        fqns: set[str] = set()
        for f in glob.glob(str(Path(mod) / "src/main/java/**/*.java"), recursive=True):
            text = Path(f).read_text(encoding="utf-8", errors="ignore")
            m = re.search(r"^package\s+([\w.]+);", text, re.M)
            if m:
                fqns.add(f"{m.group(1)}.{Path(f).stem}")
        if not fqns:
            continue
        for f in glob.glob(str(REPO / "service/**/src/**/*.java"), recursive=True):
            rp = _rel(f)
            if rp.startswith(Path(mod).resolve().relative_to(REPO).as_posix() + "/"):
                continue
            src_mod = rp.split("/src/")[0]
            src_dom = src_mod.split("/")[1]
            if src_dom == owner:
                continue
            text = Path(f).read_text(encoding="utf-8", errors="ignore")
            imports = set(re.findall(r"^import\s+(?:static\s+)?([\w.]+)\s*;", text, re.M))
            for full in imports & fqns:
                hits.add((owner, src_mod, full))
    return sorted(hits)


def _rel(p: str) -> str:
    """把路径**归一成「仓库相对 + POSIX 分隔符」**。

    🔴 **必须归一**（同源 `PIT-GLOBAL-067`）：目录分支与直接文件分支若返回不同形态，
    两边**永不相交** ⇒ 断言 1（重叠）与断言 2（共享文件）**一直是假绿**。
    """
    try:
        return Path(p).resolve().relative_to(REPO).as_posix()
    except ValueError:
        return Path(p).as_posix()


def expand(pattern: str) -> set[str]:
    """把白名单条目展开成**实际存在的文件集合**（**统一用仓库相对路径**）。"""
    if pattern.endswith("/**"):
        base = str(REPO / pattern[:-3])
        hits = set(glob.glob(base + "/**/*", recursive=True))
        return {_rel(p) for p in hits if os.path.isfile(p)}

    if "**" in pattern:
        hits = set(glob.glob(str(REPO / pattern), recursive=True))
        return {_rel(p) for p in hits if os.path.isfile(p)}

    direct = REPO / pattern
    if direct.is_file():
        return {_rel(str(direct))}
    if direct.parent.is_dir():
        hits = glob.glob(str(direct))
        return {_rel(str(direct))} | {
            _rel(p) for p in hits if os.path.isfile(p)
        }
    return {_rel(str(direct))}


def branch_exists(name: str) -> bool:
    """分支是否真实存在（本地或远端）—— 防「守门盯着已删分支」的假绿。"""
    for ref in (f"refs/heads/{name}", f"refs/remotes/upstream/{name}", f"refs/remotes/origin/{name}"):
        r = subprocess.run(
            ["git", "show-ref", "--verify", "--quiet", ref],
            cwd=REPO,
            capture_output=True,
        )
        if r.returncode == 0:
            return True
    return False


def main() -> int:
    expanded = {lane: set() for lane in ACTIVE}
    for lane, patterns in ACTIVE.items():
        for pat in patterns:
            expanded[lane] |= expand(pat)

    failures: list[str] = []

    # ---- 1. 在跑的 lane 两两不重叠 ----------------------------------------
    names = list(ACTIVE)
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
                    " ⇒ 共享文件只能由合并收口执行者在合并后统一更新"
                )

    # ---- 3/4. 排他归属 --------------------------------------------------
    for owner, owned, why in EXCLUSIVE:
        for lane in names:
            if lane == owner:
                continue
            for pat in ACTIVE[lane]:
                norm = pat.rstrip("/*")
                for o in owned:
                    f = o.rstrip("/*")
                    if norm == f or norm.startswith(f + "/") or f.startswith(norm + "/"):
                        failures.append(
                            f"🔴 越权：`{lane}` 声明了 `{pat}`（命中 `{o}`）⇒ {why}"
                        )

    # ---- 5. 每条白名单 pattern 必须命中真实文件（防「路径写错 ⇒ 守门失明」）---
    for lane in names:
        for pat in ACTIVE[lane]:
            hits = expand(pat)
            real = [h for h in hits if os.path.isfile(REPO / h)]
            if not real:
                failures.append(
                    f"🔴 白名单路径落空：`{lane}` 声明的 `{pat}` **命中 0 个真实文件**"
                    " ⇒ 守门对这一面**完全失明**（实测踩到：`service/users/user/src/**`"
                    " —— `service/users/user/` 下根本没有 `src/`，它是 `user-api`/`user-rpc`/`user-proto`）"
                )

    # ---- 6. 7 个真私有 `<域>-common` 无跨域引用 -------------------------
    for owner, src_mod, full in scan_private_common_cross_refs():
        failures.append(
            f"🔴 跨域引用私有库：`{owner}-common` 的 `{full}` 被 **`{src_mod}`** import"
            " ⇒ 该模块不属于该域的工作树 ⇒ 改那个 `<域>-common` 会**炸掉工作树外的模块**"
            " ⇒ 私有库必须真私有（否则应提级为共享层，归 G4）"
        )

    # ---- 7. 每条 ACTIVE lane 的分支必须真实存在 -------------------------
    for lane in names:
        branch = f"align/{lane}"
        if not branch_exists(branch):
            failures.append(
                f"🔴 lane `{lane}` 的分支 `{branch}` **不存在**（本地与两个远端都没有）"
                " ⇒ 守门正在盯一个不存在的 lane ⇒ 断言恒绿（假绿）"
                " ⇒ 要么建分支，要么把它从 `ACTIVE` 移走"
            )

    # ---- 输出 -----------------------------------------------------------
    print("波次 lane 写权守门（G1~G4）")
    print("=" * 60)
    for lane in names:
        print(f"  {lane:<32} [在跑] 白名单展开 {len(expanded[lane]):>4} 个文件")
    print("-" * 60)

    if failures:
        print("❌ FAIL\n")
        for f in failures:
            print(f)
            print()
        print(f"⇒ {len(failures)} 项违规")
        return 1

    print("✅ PASS")
    print(f"  · {len(ACTIVE)} 棵波次工作树写权范围两两不重叠")
    print(f"  · {len(SHARED)} 个共享文件无人拥有（归合并收口执行者）")
    print("  · favorite 只归 G3")
    print("  · 共享层只归 G4")
    print("  · 7 个真私有 `<域>-common` 无跨域引用")
    print("  · 每条 lane 的白名单路径都命中真实文件")
    print("  · 每条 lane 的分支都真实存在")
    return 0


if __name__ == "__main__":
    sys.exit(main())
