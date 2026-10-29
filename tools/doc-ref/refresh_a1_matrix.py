#!/usr/bin/env python3
"""重算并回填 `docs/PhaseGoal-judge.md` 的 **矩阵 A-1**（定位与规模）。

为什么需要它
------------
A-1 的 `tests` 列是 **2026-10-28 的逐组件快照**（39 行求和 2860）。
2026-10-29 全量 `mvn clean verify` 的真实值是 **3294 条**（29 个含测模块）。
`tools/difftest/check-phasegoal-consistency.py` 的第 13 组检查
（`A-1 的 tests 列求和 == 文档声明的全仓测试条数`）会因此报红。

口径（**照抄 A-1 表注，不自创**）
--------------------------------
* `main/LOC` = `src/main/java` 下 **`.java` 文件数 / 逐文件 `splitlines()` 行数之和**
  （**不是** `cat | wc -l` —— 后者少计无尾换行的文件）。
* `tests` = **surefire `TEST-*.xml` 逐 `testsuite` 属性求和**（权威读法），
  本脚本直接读 `target/surefire-reports/TEST-*.xml` 求和，**不**从 `reg_counts.txt` 转抄。

用法
----
    python3 tools/doc-ref/refresh_a1_matrix.py            # 预览（只打印差异）
    python3 tools/doc-ref/refresh_a1_matrix.py --apply    # 回填 A-1 的两列

退出码：0 = 无差异或已回填成功；1 = 有差异且未 `--apply`；2 = 输入不可得（硬失败）。
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOC = ROOT / "docs/PhaseGoal-judge.md"

# A-1 的 39 行 ⇒ 组件 → 模块路径（`None` = proto 模块，无 `src/main/java` 与测试）
COMPONENTS: list[tuple[str, str, str | None]] = [
    ("article-api", "service/article/article-api", "service/article/article-api"),
    ("article-rpc", "service/article/article-rpc", "service/article/article-rpc"),
    ("article-common", "service/article/article-common", "service/article/article-common"),
    ("article-proto", "service/article/article-proto", None),
    ("comment-api", "service/comment/comment-api", "service/comment/comment-api"),
    ("comment-rpc", "service/comment/comment-rpc", "service/comment/comment-rpc"),
    ("comment-common", "service/comment/comment-common", "service/comment/comment-common"),
    ("comment-proto", "service/comment/comment-proto", None),
    ("favorite-api", "service/favorite/favorite-api", "service/favorite/favorite-api"),
    ("favorite-rpc", "service/favorite/favorite-rpc", "service/favorite/favorite-rpc"),
    ("favorite-common", "service/favorite/favorite-common", "service/favorite/favorite-common"),
    ("favorite-proto", "service/favorite/favorite-proto", None),
    ("follow-api", "service/follow/follow-api", "service/follow/follow-api"),
    ("follow-rpc", "service/follow/follow-rpc", "service/follow/follow-rpc"),
    ("follow-common", "service/follow/follow-common", "service/follow/follow-common"),
    ("follow-proto", "service/follow/follow-proto", None),
    ("hot-api", "service/hot/hot-api", "service/hot/hot-api"),
    ("hot-rpc", "service/hot/hot-rpc", "service/hot/hot-rpc"),
    ("hot-common", "service/hot/hot-common", "service/hot/hot-common"),
    ("hot-proto", "service/hot/hot-proto", None),
    ("like-api", "service/like/like-api", "service/like/like-api"),
    ("like-rpc", "service/like/like-rpc", "service/like/like-rpc"),
    ("like-common", "service/like/like-common", "service/like/like-common"),
    ("like-proto", "service/like/like-proto", None),
    ("message-api", "service/message/message-api", "service/message/message-api"),
    ("message-rpc", "service/message/message-rpc", "service/message/message-rpc"),
    ("message-common", "service/message/message-common", "service/message/message-common"),
    ("message-proto", "service/message/message-proto", None),
    ("task-api", "service/task/task-api", "service/task/task-api"),
    ("task-rpc", "service/task/task-rpc", "service/task/task-rpc"),
    ("task-proto", "service/task/task-proto", None),
    ("user-api", "service/users/user/user-api", "service/users/user/user-api"),
    ("user-rpc", "service/users/user/user-rpc", "service/users/user/user-rpc"),
    ("admin-api", "service/users/admin/admin-api", "service/users/admin/admin-api"),
    ("admin-rpc", "service/users/admin/admin-rpc", "service/users/admin/admin-rpc"),
    ("users-common", "service/users/users-common", "service/users/users-common"),
    ("user-proto", "service/users/user/user-proto", None),
    ("admin-proto", "service/users/admin/admin-proto", None),
    ("service/common", "service/common", "service/common"),
]


def count_loc(module: str) -> tuple[int, int] | None:
    """返回 (文件数, 行数之和)；模块目录不存在 ⇒ None。"""
    base = ROOT / module / "src/main/java"
    if not base.is_dir():
        return None
    files = sorted(base.rglob("*.java"))
    lines = 0
    for f in files:
        with f.open("r", encoding="utf-8", errors="replace") as fh:
            lines += len(fh.read().splitlines())
    return len(files), lines


def count_tests(module: str) -> tuple[int, int, int, int]:
    """返回 (tests, failures, errors, skipped)；无 surefire 报告 ⇒ 全 0。"""
    rep = ROOT / module / "target/surefire-reports"
    if not rep.is_dir():
        return 0, 0, 0, 0
    t = f = e = s = 0
    for x in sorted(rep.glob("TEST-*.xml")):
        try:
            root = ET.parse(x).getroot()
        except ET.ParseError:
            continue
        t += int(root.get("tests", 0))
        f += int(root.get("failures", 0))
        e += int(root.get("errors", 0))
        s += int(root.get("skipped", 0))
    return t, f, e, s


def fmt_loc(v: tuple[int, int] | None) -> str:
    return "—（生成）" if v is None else f"{v[0]} / {v[1]:,}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="把新值写回 A-1 的两列")
    args = ap.parse_args()

    raw = DOC.read_text(encoding="utf-8")
    lines = raw.split("\n")

    # A-1 表 = 「## 二、矩阵 A-1」之后的第一张表
    try:
        start = next(i for i, l in enumerate(lines) if l.startswith("## 二、矩阵 A-1"))
    except StopIteration:
        print("❌ 找不到 A-1 小节", file=sys.stderr)
        return 2
    tbl_start = next(i for i in range(start, len(lines)) if lines[i].startswith("| 域 | 组件 |"))
    tbl_end = next(i for i in range(tbl_start + 1, len(lines)) if not lines[i].startswith("|"))

    # 行序 ⇒ 组件名（第 3 列）
    rows: list[tuple[int, str, str, str]] = []  # (行号, 组件, 旧 main/LOC, 旧 tests)
    for i in range(tbl_start + 2, tbl_end):
        cells = [c.strip() for c in lines[i].split("|")]
        if len(cells) < 8:
            continue
        rows.append((i, cells[2].strip("`"), cells[5], cells[6]))

    if len(rows) != len(COMPONENTS):
        print(f"❌ A-1 行数 {len(rows)} ≠ 脚本登记的组件数 {len(COMPONENTS)} ⇒ 组件清单过期，abort", file=sys.stderr)
        return 2

    # 输入可见性探针（硬失败，不静默降级）
    probes = [c for _, c, _, _ in rows if c != "—（生成）"]
    missing = [c for c in probes if not (ROOT / dict((x[0], x[1]) for x in COMPONENTS)[c] / "target").exists()]
    if missing:
        print(f"❌ 输入不可得：{len(missing)} 个组件没有 target/ ⇒ 未构建 ⇒ 不做半截更新（exit 2）", file=sys.stderr)
        for c in missing[:8]:
            print(f"     - {c}", file=sys.stderr)
        return 2

    changed = 0
    new_rows: list[str] = []
    tot_tests = tot_f = tot_e = tot_s = 0
    for (i, comp, old_loc, old_tests), (_, name, module) in zip(rows, COMPONENTS):
        loc = count_loc(module) if module else None
        t, f, e, s = count_tests(module) if module else (0, 0, 0, 0)
        tot_tests += t
        tot_f += f
        tot_e += e
        tot_s += s
        new_loc = fmt_loc(loc)
        new_tests = str(t)
        if new_loc != old_loc or new_tests != old_tests:
            changed += 1
            print(f"  ~ {comp:16s} LOC {old_loc:>14s} -> {new_loc:<16s} tests {old_tests:>5s} -> {new_tests}")
        new_rows.append(f"{comp}\t{new_loc}\t{new_tests}")

    print(f"\nA-1 求和：tests = {tot_tests}（Failures {tot_f} / Errors {tot_e} / Skipped {tot_s}）· 变动的行 {changed}/{len(rows)}")

    if not changed:
        print("✅ 无差异，无需回填")
        return 0
    if not args.apply:
        print("⚠️ 有差异但未 --apply ⇒ 未落盘（exit 1）")
        return 1

    # 回填：只改 A-1 表的两列 + 表头的核对日期
    for (i, comp, _, _), row in zip(rows, new_rows):
        _, new_loc, new_tests = row.split("\t")
        cells = lines[i].split("|")
        cells[5] = f" {new_loc} "
        cells[6] = f" {new_tests} "
        lines[i] = "|".join(cells)
    for i in range(start, tbl_start):
        lines[i] = re.sub(r"（39 行 = 38 组件 ＋ 1 共享层 · \d{4}-\d{2}-\d{2} 实测）",
                          "（39 行 = 38 组件 ＋ 1 共享层 · 2026-10-29 实测）", lines[i])
    DOC.write_text("\n".join(lines), encoding="utf-8")
    print("✅ 已回填 A-1 的 main/LOC 与 tests 两列 + 表头日期")
    return 0


if __name__ == "__main__":
    sys.exit(main())
