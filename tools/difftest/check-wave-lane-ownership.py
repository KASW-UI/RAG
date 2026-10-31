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
8. **域内探针目录（`tools/<kind>/<域>/`）的写权按域判** —— 🆕 2026-10-29（R0·T-0.5 选项 A 落码）
   ⇒ 落点口径统一为 `tools/<kind>/<域>/`（R0·T-0.6）
   ⇒ 「哪个 lane 能写哪个域的探针」按**域**判，不按目录判 ⇒ 一个域的探针散在多个 `<kind>/` 下也不越权。
   ⚠️ **这条是「按域授权」而不是「按目录授权」** ⇒ 判据必须**能识别同一个域出现在多个 `<kind>/` 下**
   ⇒ 只按目录判会漏判 ⇒ ⇒ 断言 8 用**域级集合求交**（而不是文件级 glob）。

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

# 10 个业务域 + 共享层（`common` 是 `service/common`；`users-common` 是共享层 users-common）
# ⚠️ 这张表是「**哪些 `<域>` 目录名算域级探针目录**」的判据 ⇒ 用来把
#    `tools/<kind>/go/`、`tools/<kind>/java/`、`tools/difftest/lib/`、`tools/difftest/conf/`
#    这类**探针种类目录 / 公共素材目录**排除在外（它们不属于任何域 ⇒ 归合并收口执行者）。
DOMAIN_SLUGS = {
    "users", "follow", "message", "like", "task",
    "article", "comment", "favorite", "hot",
    "common", "users-common",
}

# 🆕 **本波（R2）要求必须落地的域级探针目录** —— 授权 ≠ 必须产出。
#    这张表让「R2 说要落仓、但目录还不存在」这件事**机器可核**（而不是靠人记得）。
#    ⇒ R2 收口时逐条核销 ⇒ 全部落地后本表可清空。
PROBE_REQUIRED: dict[str, list[str]] = {
    "g1-users-follow": ["follow", "users-common"],          # G-1 / G-4
    "g2-message-like-task": ["like"],                        # G-2
}

# ---------------------------------------------------------------- 域内探针写权（按域判 · T-0.5 选项 A）
# 🆕 2026-10-29（R0·T-0.5 裁定：选项 A）——
#   机器守门的任何 lane 白名单都不含 `tools/**` ⇒ 按机器口径 `tools/**` 无 lane 拥有
#   ⇒ 但 R2 要求「域内探针落仓」⇒ 若不给 lane 授权 ⇒ 所有探针都得串行落在主线 ⇒ R2 直接变慢。
#   ⇒ 裁定：**域内探针目录 `tools/<kind>/<域>/` 归对应域 lane**（**按域授权**）。
#
# ⚠️ **为什么必须「按域」而不是「按目录」**：
#   同一个域的探针天然会散在多个 `<kind>/` 下（例：`favorite` 域同时有
#   `tools/api-envelope-probe/favorite/` + `tools/errmsg-probe/favorite/` + `tools/proto-wire-probe/favorite/`）
#   ⇒ 若按目录授权 ⇒ 每加一个 `<kind>/` 就要再改一次白名单 ⇒ 迟早漏 ⇒ ⇒ **按域**。
#
# ⚠️ **本表是「授权」，不是「归属」**：探针目录本身是**机器口径**（T-0.6 统一落点）。
#   域级台账（`docs/DRIFT/<域>/**`）的写权**仍然**走上面的 `ACTIVE` 白名单 ⇒ 两张表互不重叠。
PROBE_OWNERS: dict[str, list[str]] = {
    # ---- G1：users（不含 users-common）+ follow ----
    "g1-users-follow": ["users", "follow"],
    # ---- G2：message + like + task ----
    "g2-message-like-task": ["message", "like", "task"],
    # ---- G3：article + comment + favorite + hot ----
    "g3-article-comment-favorite-hot": ["article", "comment", "favorite", "hot"],
    # ---- G4：共享层（common + users-common）----
    "g4-service-common": ["common", "users-common"],
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
    # ---- 🆕 2026-11-01 收官轮 · 「阶段一判定基线」整族（原先**无主**）----
    # ⚠️ **为什么这 5 个文件原先无主** —— 四条 lane 白名单里含 `PhaseGoal` 的条目 = **0 条**
    #    ⇒ ⇒ `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/g{1..4}.md` **既不属于任何 lane，
    #    也不在 `SHARED` 里** ⇒ ⇒ **没有任何写权门能拦**（G1 与 G4 两棵 lane 各自独立发现此洞）。
    # ⚠️ **为什么不能归给某个 lane** —— 这 4 份 `g<N>.md` 的文件头自述：
    #    「⚠️ **本文件是机器生成的 —— 不要手改**」＋「生成器 `check-phasegoal-lanes.py --write`」
    #    ＋「**唯一数据源**：`docs/PhaseGoal-judge.md`」⇒ ⇒ 它们是 **judge 的派生物**，
    #    **不是各 lane 的面** ⇒ ⇒ 归 lane 就会有人手改 ⇒ 立刻与基线分叉 ⇒ 门禁红。
    # ⇒ ⇒ 落法 = 归**合并收口执行者**（与其它 `SHARED` 面同一条）。
    "docs/PhaseGoal-judge.md",
    "docs/PhaseGoal/g1.md",
    "docs/PhaseGoal/g2.md",
    "docs/PhaseGoal/g3.md",
    "docs/PhaseGoal/g4.md",
    # ---- 🆕 2026-11-01 收官轮 · 「派生层与定级层」整族（原先**无主**）----
    # ⚠️ `docs/DRIFT/_cross/**` 按 AGENTS.md §5.3 本来就是**合并收口执行者**的写权面
    #    （派生层产物 `stage1-derivation.{md,tsv}` ＋ 定级层 `grading/*.md` ＋ 各种 `_cross` 台账）
    #    ⇒ ⇒ 但它**原先不在 `SHARED` 里** ⇒ ⇒ 实测被新加的断言 9 判成「**无主面**」
    #    ⇒ ⇒ 现在显式登记 ⇒ ⇒ lane 碰它时报「**越权写共享面**」（比「无主面」更准）。
    "docs/DRIFT/_cross/**",
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


def lane_context_present(lanes) -> list[str]:
    """当前检出里**能看到的 ACTIVE lane 分支**（`align/<lane>`）。

    ⚠️ **为什么需要它**（2026-10-31 实测发现）：
        第 7 条检查（每条 ACTIVE lane 的分支必须真实存在）依赖 `git show-ref`
        ⇒ 而 **Concourse 的 git resource 检出的是单个 commit（detached HEAD）**，
          **不带任何分支 ref、也不带远端 ref** ⇒ `branch_exists()` 恒为 False
          ⇒ 该条**恒红** ⇒ 这道门**根本进不了 CI**（浅克隆/单分支 checkout 场景）。
        ⇒ 但「恒红」不是正确处置：那个环境里**本来就没有 lane 上下文**，
          第 7 条判的是「守门盯着一个不存在的 lane」—— 无上下文时它**不适用**，
          不是「失败」。
        ⇒ 口径：
          · **一条 ACTIVE lane 的分支都看不到** ⇒ 第 7 条 = NOT_RUN（跳过并说明）
          · **看得到至少一条** ⇒ 逐条检查（缺的仍报红，那才是真问题：分支被删了）
    ⚠️ **只认 ACTIVE 里的 lane**：`align/ci-concourse` 这类**普通分支**
      （不是波次 lane）**不算** lane 上下文 —— 否则一条无关分支就会
      把「无上下文」误判成「有上下文」⇒ 第 7 条又变成恒红。
    """
    seen = []
    for lane in lanes:
        for ref in (f"refs/heads/align/{lane}",
                    f"refs/remotes/upstream/align/{lane}",
                    f"refs/remotes/origin/align/{lane}"):
            r = subprocess.run(
                ["git", "show-ref", "--verify", "--quiet", ref],
                cwd=REPO, capture_output=True,
            )
            if r.returncode == 0:
                seen.append(ref)
                break
    return seen


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
    # ⚠️ **必须按「展开后的文件集合」比**（2026-11-01 修）—— 原式拿 `SHARED` 的**原始字符串**
    #    去撞 `expanded[lane]`（展开后的**文件路径**集合）⇒ ⇒ 一旦 `SHARED` 里放了 **glob 形式**
    #    （如 `docs/DRIFT/_cross/**`）⇒ **永远撞不上** ⇒ ⇒ 断言 2 对 glob 形式**恒绿**（假绿）。
    shared_expanded_2: set[str] = set()
    for pat in SHARED:
        shared_expanded_2 |= expand(pat)
    for lane in names:
        clash = sorted(expanded[lane] & shared_expanded_2)
        if clash:
            failures.append(
                f"🔴 共享文件被 lane 占用：`{lane}` 的白名单命中了 {len(clash)} 个共享文件"
                " ⇒ 共享文件只能由合并收口执行者在合并后统一更新\n"
                + "\n".join(f"     · {p}" for p in clash[:10])
                + ("\n     …" if len(clash) > 10 else "")
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
    # ⚠️ **口径（2026-10-31 修）**：无 lane 上下文（CI 的单 commit detached 检出）
    #   ⇒ 本条**不适用**（NOT_RUN）⇒ 不计入失败，但**必须打印出来**，
    #   绝不能让「跳过」看起来像「查过且通过」。
    visible_lane_refs = lane_context_present(names)
    if not visible_lane_refs:
        print("ℹ️ 第 7 条（lane 分支存在性）**不适用**：当前检出里没有任何 lane 分支 ref")
        print("   （Concourse git resource 是单 commit detached HEAD ⇒ 无 lane 上下文）")
        print("   ⇒ 本条记为 NOT_RUN，不计入失败；其余 6 条照常判定。")
    else:
        print(f"ℹ️ 可见 lane 分支 ref {len(visible_lane_refs)} 条 ⇒ 逐条检查存在性")
        for lane in names:
            branch = f"align/{lane}"
            if not branch_exists(branch):
                failures.append(
                    f"🔴 lane `{lane}` 的分支 `{branch}` **不存在**（本地与两个远端都没有）"
                    " ⇒ 守门正在盯一个不存在的 lane ⇒ 断言恒绿（假绿）"
                    " ⇒ 要么建分支，要么把它从 `ACTIVE` 移走"
                )

    # ---- 8. 域内探针写权（按域判 · T-0.5 选项 A）------------------------
    # 8a. 同一个域不得被两个 lane 同时声明 ⇒ 否则两棵工作树会同时写同一批探针文件 ⇒ 合并必冲突。
    seen_domains: dict[str, str] = {}
    for lane, doms in PROBE_OWNERS.items():
        for dom in doms:
            if dom in seen_domains:
                failures.append(
                    f"🔴 域内探针写权重叠：域 `{dom}` 同时被 `{seen_domains[dom]}` 与 `{lane}` 声明"
                    " ⇒ 两个 lane 会同时写同一批探针 ⇒ 合并必冲突"
                )
            else:
                seen_domains[dom] = lane

    # 8b. 仓里现存的 `tools/<kind>/<域>/` 目录，其 `<域>` 必须**有主**（否则＝无人拥有 ⇒ 越权写入无门可拦）
    #     ⇒ 这是「补上机器口径里缺失的那一块」的关键断言。
    #     ⚠️ 目录名允许带层级后缀（`favorite-rpc` / `favorite-api`）⇒ 先按**最长匹配**归到域根。
    def domain_root_of(dir_name: str) -> str | None:
        for d in sorted(DOMAIN_SLUGS, key=len, reverse=True):
            if dir_name == d or dir_name.startswith(d + "-"):
                return d
        return None

    for kind_dir in sorted(p for p in (REPO / "tools").iterdir() if p.is_dir()):
        for dom_dir in sorted(p for p in kind_dir.iterdir() if p.is_dir()):
            dom = domain_root_of(dom_dir.name)
            if dom is None:
                continue  # 不是域级探针目录（探针种类目录 / 公共素材目录）⇒ 归合并收口执行者
            if dom not in seen_domains:
                failures.append(
                    f"🔴 域内探针目录无人拥有：`{dom_dir.relative_to(REPO)}`"
                    f" ⇒ 域 `{dom}` 不在 `PROBE_OWNERS` 任何 lane 的授权里"
                    " ⇒ 写它没有任何守门能拦 ⇒ 要么给对应 lane 授权，要么把目录挪走"
                )

    # 8c. 本波（R2）**要求必须落地**的域级探针目录，缺一个就红 ⇒
    #     「R2 说要落仓」这件事机器可核，而不是靠人记得。
    for lane, doms in PROBE_REQUIRED.items():
        for dom in doms:
            if not any((REPO / "tools").glob(f"*/{dom}")):
                failures.append(
                    f"🔴 本波要求的域级探针目录缺失：lane `{lane}` 的域 `{dom}`"
                    f"（`tools/*/{dom}/`）在仓里**一个都没有**"
                    f" ⇒ R2 的该项还没落仓（判据见 `docs/RoundPlan/Round-2.md`）"
                )

    # ---- 9. 🆕 无主面检测（2026-11-01 收官轮 · G1 与 G4 各自独立发现同一洞）------
    # ⚠️ **本条补的是上面所有断言都盖不住的盲区**：前面 1~8 条判的都是
    #    「**声明**层面的冲突」（白名单两两重叠 / 声明了 SHARED / 排他面被占），
    #    ⇒ ⇒ **「某个 lane 实际写了一个谁都不拥有的文件」这件事，一条都判不到**。
    # ⚠️ **为什么它是真洞**：2026-11-01 实测 —— 四条 lane 白名单里含 `PhaseGoal` 的条目 = **0 条**
    #    ⇒ ⇒ `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/g{1..4}.md` **无主**
    #    ⇒ ⇒ 实测有 **4 笔不同范围的 commit**（文件集规模 = 1 / 8 / 12 / 7 个）改写过它
    #    ⇒ ⇒ 而这些 commit 的 **author 字段全部相同** ⇒ ⇒ **「是谁改的」不可考** ⇒ ⇒ 没人管。
    #    ⇒ ⇒ 归 `SHARED`（= 合并收口执行者专写）后，至少**归属变成可查的**，
    #    且任何 lane 再声明它 ⇒ 立刻被断言 2 拦下。
    #
    # ⚠️ **判据**：对每棵**看得到 ref 的** lane，取它**尚未合入主干的 commit**实际改过的
    #    `docs/**` 文件 ⇒ 逐个判归属：
    #      ① 命中**该 lane 自己**的白名单 ⇒ 正常；
    #      ② 命中**别的 lane** 的白名单 ⇒ 🔴 越权写别域的面；
    #      ③ 命中 `SHARED` ⇒ 🔴 越权写共享面（共享面只由合并收口执行者更新）；
    #      ④ **谁都不命中** ⇒ 🔴 **无主面**（本条要抓的就是这个）。
    # ⚠️ **口径写死**：① 只看**未合入主干**的 commit ⇒ 用 **`main..ref`（单边）**
    #    ⇒ `git log` 的 `A...B` 是**对称差集**（两侧独有都算）⇒ **不是本条要的**；
    #    ② 只看 `docs/**`（代码面由 `EXCLUSIVE` + 白名单两两不重叠管）；
    #    ③ 取的是「**该 lane 自己的 commit 改过的文件**」⇒ 与「判某 lane 改了什么」用
    #    **三点 diff** 是**两个不同的量**（后者 = merge-base→branch 的内容差集）
    #    ⇒ ⇒ 本条用前者（因为要抓的是「这棵 lane 动手写了什么」）。
    main_ref = None
    for cand in ("refs/heads/do-my-own-gh", "refs/remotes/upstream/do-my-own-gh",
                 "refs/remotes/origin/do-my-own-gh"):
        r = subprocess.run(["git", "show-ref", "--verify", "--quiet", cand],
                           cwd=REPO, capture_output=True)
        if r.returncode == 0:
            main_ref = cand
            break
    ownerless_checked = 0
    if main_ref is None:
        print("ℹ️ 第 9 条（无主面检测）**不适用**：找不到主干 ref ⇒ 无法取「未合入主干的 commit」")
        print("   ⇒ 本条记为 NOT_RUN，不计入失败。")
    else:
        shared_expanded: set[str] = set()
        for pat in SHARED:
            shared_expanded |= expand(pat)
        for lane in names:
            for ref in (f"refs/heads/align/{lane}",
                        f"refs/remotes/upstream/align/{lane}",
                        f"refs/remotes/origin/align/{lane}"):
                r = subprocess.run(["git", "show-ref", "--verify", "--quiet", ref],
                                   cwd=REPO, capture_output=True)
                if r.returncode != 0:
                    continue
                r = subprocess.run(
                    ["git", "log", f"{main_ref}..{ref}", "--name-only", "--format=",
                     "--diff-filter=AM"],
                    cwd=REPO, capture_output=True, text=True,
                )
                touched = sorted({ln.strip() for ln in r.stdout.splitlines()
                                  if ln.strip().startswith("docs/")})
                if not touched:
                    break
                ownerless_checked += len(touched)
                for fpath in touched:
                    if fpath in expanded[lane]:
                        continue                      # ① 自己域内 ⇒ 正常
                    others = [o for o in names if o != lane and fpath in expanded[o]]
                    if others:
                        failures.append(
                            f"🔴 越权写别域的面：lane `{lane}` 实际改了 `{fpath}`"
                            f" ⇒ 该文件属于 `{others[0]}` 的白名单"
                        )
                    elif fpath in shared_expanded:
                        failures.append(
                            f"🔴 越权写共享面：lane `{lane}` 实际改了 `{fpath}`"
                            " ⇒ 共享面只由合并收口执行者在合并后统一更新"
                        )
                    else:
                        failures.append(
                            f"🔴 **无主面**：lane `{lane}` 实际改了 `{fpath}`"
                            " ⇒ 该文件**既不在任何 lane 的白名单、也不在 `SHARED`**"
                            " ⇒ ⇒ 没有任何写权门能拦它 ⇒ 要么补进该 lane 的白名单"
                            " ⇒ 要么归 `SHARED`（合并收口执行者专写）"
                        )
                break

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
    # ⚠️ 第 7 条的结论必须**如实反映**它到底跑没跑 ——
    #   跳过时**不能**打印「每条 lane 的分支都真实存在」：
    #   那会让读日志的人以为这条查过了 ⇒ **零命中被当成通过**（假绿表述）。
    if visible_lane_refs:
        print("  · 每条 lane 的分支都真实存在")
    else:
        print("  · ⚠️ lane 分支存在性：**NOT_RUN**（无 lane 上下文 ⇒ 本条不适用）")
    print(f"  · 域内探针写权按域判（{len(seen_domains)} 个域 · {len(PROBE_REQUIRED)} 个域本波要求落仓）")
    # ⚠️ 第 9 条（无主面检测）的结论也必须**如实反映**它到底跑没跑 ——
    #   「没跑」不能打印成「查过且通过」⇒ 零命中被当成通过（假绿表述）。
    if main_ref is None:
        print("  · ⚠️ 无主面检测：**NOT_RUN**（找不到主干 ref ⇒ 本条不适用）")
    else:
        print(f"  · 无主面检测：已判定 {ownerless_checked} 个 lane 实际改过的 `docs/**` 文件")
    return 0


if __name__ == "__main__":
    sys.exit(main())
