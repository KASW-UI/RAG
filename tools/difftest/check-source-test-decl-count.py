#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""judge §二 A-1 的 `tests` 列 = **源声明测试方法数**（环境无关口径）⇒ 逐行对账。

查什么
------
1. **A-1 的 `tests` 列 == 该模块 `src/test/java` 里的源声明测试方法数**（逐行）——
   ⚠️ **口径 = 方法声明数**：一个 `@ParameterizedTest` 算 **1**（不是 surefire 展开后的 N 条 case）。
2. **A-1 的 `tests` 列求和 == 文档声明的「全仓源声明测试方法数」**（§七 P0 #6 的声明行）。
3. **A-1 的行数 == 39**（38 组件 + 1 共享层），且每个组件都有源声明数可对账。

为什么必须机器守
----------------
⚠️ **旧口径（surefire `TEST-*.xml` 求和）是环境相关的**：
  - Docker 停 ⇒ 容器在 `@BeforeAll` 起不来 ⇒ **整类 N 个方法折叠成 1 条 error** ⇒ 读数**偏小**；
  - `@ParameterizedTest` / `@TestFactory` 被 surefire **展开成 N 条 case** ⇒ 读数**偏大**；
  - ⇒ ⇒ **同一份代码两次跑能出两个数** ⇒ ⇒ ⇒ **不能当「覆盖面」口径用**。
⇒ ⇒ 所以 A-1 改用**源声明数**（读源码 ⇒ 环境无关 ⇒ 可复算）⇒ ⇒ **而「文档里的数字与源码是否同步」必须机器查**
⇒ ⇒ 否则又回到「人工盘点 ⇒ 跨域必漏」那一条（见 `docs/Pitfalls/_cross/testing-pitfalls.md`）。

⚠️ **本守门自己也有「陈旧」风险**：它只对账 A-1 那一列 ⇒ ⇒ **A-1 忘了更新 ⇒ 本门就红** ⇒ ⇒
**这正是要的效果**（文档数字与源码脱节 ⇒ 立刻报红）⇒ ⇒ **而不是「守门也陈旧 ⇒ 一起错」**。

判据的边界（诚实声明）
----------------------
⚠️ **本门只验「A-1 列 == 源码声明数」** ⇒ ⇒ **不验「测试真的跑过了」** ⇒ ⇒
**「有 100 个 `@Test` 方法」≠「100 个断言成立」** ⇒ ⇒ **运行期证据仍以 surefire XML 为准**
（§七 P0 #6 的「全量回归 N 条 / 0F / 0E」是**运行期**读数，与本列**两个口径、不可相减**）。

跑法
----
    python3 tools/difftest/check-source-test-decl-count.py

退出码
------
0 = 通过（逐行一致 + 求和一致 + 行数 39）
1 = 有不一致（打印 `M1` / `M2` / `M3`）
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# ── 源声明的测试注解（一个参数化方法算 1 条）──────────────────────────────
TEST_ANNOTATIONS = (
    "Test",
    "ParameterizedTest",
    "RepeatedTest",
    "TestFactory",
    "TestTemplate",
)
_ANNOT_RE = re.compile(r"@(" + "|".join(TEST_ANNOTATIONS) + r")\b")

# ⚠️ **组件名 → 模块目录**（A-1 的 39 行里，`proto` 行无测试 ⇒ 值为 0）
COMPONENT_MODULE: dict[str, str] = {
    "article-api": "service/article/article-api",
    "article-rpc": "service/article/article-rpc",
    "article-common": "service/article/article-common",
    "comment-api": "service/comment/comment-api",
    "comment-rpc": "service/comment/comment-rpc",
    "comment-common": "service/comment/comment-common",
    "favorite-api": "service/favorite/favorite-api",
    "favorite-rpc": "service/favorite/favorite-rpc",
    "favorite-common": "service/favorite/favorite-common",
    "follow-api": "service/follow/follow-api",
    "follow-rpc": "service/follow/follow-rpc",
    "follow-common": "service/follow/follow-common",
    "hot-api": "service/hot/hot-api",
    "hot-rpc": "service/hot/hot-rpc",
    "hot-common": "service/hot/hot-common",
    "like-api": "service/like/like-api",
    "like-rpc": "service/like/like-rpc",
    "like-common": "service/like/like-common",
    "message-api": "service/message/message-api",
    "message-rpc": "service/message/message-rpc",
    "message-common": "service/message/message-common",
    "task-api": "service/task/task-api",
    "task-rpc": "service/task/task-rpc",
    "user-api": "service/users/user/user-api",
    "user-rpc": "service/users/user/user-rpc",
    "admin-api": "service/users/admin/admin-api",
    "admin-rpc": "service/users/admin/admin-rpc",
    "users-common": "service/users/users-common",
    "service/common": "service/common",
}

# ⚠️ **proto 行**（无 `src/test/java` ⇒ 源声明数必然 = 0）⇒ ⇒ 单独登记，避免「无映射」被当成漏判
PROTO_COMPONENTS = (
    "article-proto",
    "comment-proto",
    "favorite-proto",
    "follow-proto",
    "hot-proto",
    "like-proto",
    "message-proto",
    "task-proto",
    "user-proto",
    "admin-proto",
)

JUDGE = REPO / "docs/PhaseGoal-judge.md"
A1_START = "## 二、矩阵 A-1"
A1_ROWS_EXPECTED = 39


def strip_md(s: str) -> str:
    """去 markdown 强调/反引号（只去首尾的成对符号，不动内容）。"""
    s = s.strip()
    while len(s) >= 2 and (
        (s[0] == "`" and s[-1] == "`")
        or (s[0] == "*" and s[-1] == "*")
        or (s[0] == "**" and s[-1] == "**")
    ):
        s = s[1:-1].strip()
    return s


def source_decl_count(module_rel: str) -> int:
    """该模块 `src/test/java` 里的**源声明测试方法数**（参数化算 1）。"""
    tdir = REPO / module_rel / "src/test/java"
    if not tdir.is_dir():
        return 0
    n = 0
    for f in tdir.rglob("*.java"):
        try:
            n += len(_ANNOT_RE.findall(f.read_text(encoding="utf-8", errors="ignore")))
        except OSError:
            continue
    return n


def split_row(line: str) -> list[str]:
    return [c.strip() for c in line.strip().strip("|").split("|")]


def a1_rows() -> list[list[str]]:
    lines = JUDGE.read_text(encoding="utf-8").split("\n")
    lo = None
    for i, l in enumerate(lines):
        if l.startswith(A1_START):
            lo = i
            break
    if lo is None:
        return []
    rows: list[list[str]] = []
    for l in lines[lo + 1:]:
        if l.startswith("## "):
            break
        if not l.startswith("|"):
            continue
        c = split_row(l)
        if len(c) < 6:
            continue
        if set("".join(c)) <= set("-|: "):
            continue
        if c[0].startswith("域"):
            continue
        rows.append(c)
    return rows


def main() -> int:
    codes: list[str] = []

    if not JUDGE.exists():
        print(f"❌ 找不到 {JUDGE}")
        return 1

    rows = a1_rows()
    print("=" * 78)
    print("judge §二 A-1 `tests` 列 vs 源声明测试方法数（环境无关口径）")
    print("=" * 78)
    print("  口径：src/test/java 里 @Test / @ParameterizedTest / @RepeatedTest /")
    print("        @TestFactory / @TestTemplate 的**方法声明数**（参数化算 1）")
    print("  ⚠️ 与 surefire 执行读数**两个口径、不可相减**（后者会被 Docker 状态")
    print("     与参数化展开双向扭曲 ⇒ 环境相关）")
    print()

    if len(rows) != A1_ROWS_EXPECTED:
        codes.append("M3")
        print(f"🔴 [M3] A-1 行数 = {len(rows)}，期望 {A1_ROWS_EXPECTED}（38 组件 + 1 共享层）")
    else:
        print(f"  A-1 行数 = {len(rows)} ✅")

    mismatches: list[tuple[str, int, int]] = []
    total_col = 0
    total_src = 0
    checked = 0

    for c in rows:
        comp = strip_md(c[1])
        col_raw = strip_md(c[5])
        module = COMPONENT_MODULE.get(comp)
        if module is None:
            if comp in PROTO_COMPONENTS:
                # proto 行：源声明必然 0 ⇒ 列值必须是 0
                if col_raw.isdigit() and int(col_raw) == 0:
                    total_col += 0
                    print(f"  · {comp:20s} 列=   0 源=   0 ✅（proto 行 · 无测试）")
                else:
                    codes.append("M1")
                    print(f"🔴 [M1] {comp:20s} 列={col_raw} 源=   0（proto 行应为 0）")
                continue
            codes.append("M3")
            print(f"🔴 [M3] 组件 `{comp}` 无模块映射 ⇒ ⇒ 本门对它失明 ⇒ 请补 COMPONENT_MODULE")
            continue

        src = source_decl_count(module)
        total_src += src
        if not col_raw.isdigit():
            codes.append("M1")
            print(f"🔴 [M1] {comp:20s} 列=`{col_raw}` 不是数字")
            continue
        col = int(col_raw)
        total_col += col
        checked += 1
        if col == src:
            print(f"  · {comp:20s} 列={col:>5d} 源={src:>5d} ✅")
        else:
            codes.append("M1")
            mismatches.append((comp, col, src))
            print(f"🔴 [M1] {comp:20s} 列={col:>5d} 源={src:>5d}  ← 不一致（差 {src - col:+d}）")

    print()
    print(f"  参与对账的组件 = {checked} · A-1 列求和 = {total_col} · 源声明求和 = {total_src}")

    # ── M2：A-1 求和 vs 文档声明的「全仓源声明测试方法数」────────────────
    raw = JUDGE.read_text(encoding="utf-8")
    m = re.search(r"\*\*全仓源声明测试方法数\*\*\s*[=:]\s*\*\*([\d,]+)\*\*", raw)
    if m:
        declared = int(m.group(1).replace(",", ""))
        if declared != total_src:
            codes.append("M2")
            print(f"🔴 [M2] 文档声明「全仓源声明测试方法数」= {declared}，实测 = {total_src}")
        else:
            print(f"  ✅ 文档声明「全仓源声明测试方法数」= {declared} 与实测一致")
    else:
        print("  ⚠️ 未找到「全仓源声明测试方法数」声明 ⇒ 跳过 M2（§七 P0 #6 的声明行）")

    print()
    if codes:
        print(f"❌ 失败：{sorted(set(codes))}")
        if mismatches:
            print(f"   不一致 {len(mismatches)} 行 ⇒ ⇒ 请把 A-1 的 `tests` 列更新为源声明数")
        return 1
    print("✅ A-1 的 `tests` 列与源码逐行一致（环境无关口径）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
