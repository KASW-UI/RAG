#!/usr/bin/env python3
"""守门：**G4 的角色模型必须与 2026-10-27 02:23 Jason 定的「常设执行者」一致**，旧模型措辞不得回流。

背景
----
原文档把 G4 写成「**第 4 波、必须最后**」⇒ 配套两条：
「落码窗口只在 G4 波激活」+「G1~G3 期间改共享层 ⇒ **那波测试结论作废**」。

**2026-10-27 02:23 Jason 订正**：G4 的职责是「**随时接受其他三个工作树的反馈，G4 进行执行**」
⇒ G4 是**常设的共享层执行者**，**不在域波顺序里**（域波顺序仍是 G1→G2→G3）。
⇒ 「基线不可变 / 结论作废」这条**被撤销**：域波**收口回归在波次末尾跑** ⇒ 天然含 G4 至今的共享层改动
⇒ 唯一风险窗口 = **收口回归跑完 ～ 合并进主干之前** ⇒ 该窗口内共享层变了 ⇒ **重跑一次收口回归**。

⚠️ **为什么必须机器守**（本仓一贯纪律：*不可复算的断言 = 假保证*）：
角色模型散落在 **7 份权威文档**里（`AGENTS.md` + `docs/DRIFT/_INDEX.md` + 5 份 `docs/` 台账）。
纯靠人读 ⇒ 只要有人按旧记忆写回「G4 最后」，**没有任何机制会报红** ⇒ 文档会静默退回旧模型
⇒ 而旧模型会让域波**误以为**自己期间改共享层就作废 ⇒ 流水线被人为串行化。
⇒ 本守门把「角色模型」变成**可复算的断言**。

⚠️ **粒度纪律**（本守门第一版的实测教训）：
断言必须是**段落块级 / 规范锚点级**，不能只做**全文 grep** ——
第一版用「文件里含 `常设` 就算过」⇒ 变异把**规范锚点那行**改掉后**全文别处仍有该词** ⇒ 守门恒绿（假绿）。
⇒ 现在：
- **A2** 只看 `AGENTS.md` 的 **§5.3e 块**（`3e.` 起、`3f.`/`4.` 前）与 **§5.3d 块**；
- **A7** 只看**规范锚点** `G4 = 共享层常设执行者`（每份域台账各一处）；
- **A1** 是粗粒度的「模型已传播」体检，不承担变异捕获职责。

查什么
------
| 断言 ID | 内容 |
|---|---|
| **A0** | 7 份权威文档必须存在且非空（否则守门对这一面失明） |
| **A1** | 7 份权威文档**每一份**都必须含新模型标记（`常设` 或 `随时接收`）⇒ 模型已传播到位 |
| **A2** | `AGENTS.md` **§5.3e 块**必须含 `常设执行者` + `随时接收` + `并行`；**§5.3d 块**必须含 `G1→G2→G3` |
| **A3** | 权威文档不得出现 `G1→G2→G3→G4`（域波顺序里**没有 G4**） |
| **A4** | `cross-domain-coupling.md` 必须有 **C2**（含 `随时接收` + `落码窗口常开`）与 **C7**（含 `收口回归在波次末尾跑`），且 §二.2 必须有 `G4 不在域波顺序里` |
| **A5** | **退休措辞不得作为断言出现**：旧模型短语只允许出现在「引用并纠正它」的行里 |
| **A6** | `docs/Worktree.md` 顺序行必须写 `G1 → G2 → G3`，且不得写 `G1 → G2 → G3 → G4` |
| **A7** | 4 份域台账必须含**规范锚点** `G4 = 共享层常设执行者` |

退出码
------
0 = 全部通过；1 = 角色模型不一致 / 退休措辞回流 / 权威文档缺失。

复现
----
python3 tools/difftest/check-wave-role-model.py
python3 tools/difftest/check-wave-role-model.py --mutation-test
"""

from __future__ import annotations

import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# ---------------------------------------------------------------- 权威文档集合
# ⚠️ 与 `docs/DRIFT/_cross/cross-domain-coupling.md` §二·§三、`docs/Worktree.md` §二·§三·1d、
#    `docs/DRIFT/global/lanes/_PROTOCOL.md` §3.1、`docs/DRIFT/global/vs-common.md` §4 一一对应。
AUTH_DOCS = [
    "AGENTS.md",
    "docs/DRIFT/_INDEX.md",
    "docs/DRIFT/_cross/cross-domain-coupling.md",
    "docs/DRIFT/_cross/domain-common-lanes.md",
    "docs/DRIFT/global/lanes/_PROTOCOL.md",
    "docs/DRIFT/global/vs-common.md",
    "docs/Worktree.md",
]

# 必须含**规范锚点**的文档（锚点本身被改 ⇒ 该文档的角色描述失效 ⇒ A7 转红）
ANCHOR_DOCS = [
    "docs/DRIFT/_cross/cross-domain-coupling.md",
    "docs/DRIFT/_cross/domain-common-lanes.md",
    "docs/DRIFT/global/lanes/_PROTOCOL.md",
    "docs/DRIFT/global/vs-common.md",
]
CANONICAL_ANCHOR = "G4 = 共享层常设执行者"

# ---------------------------------------------------------------- 退休措辞（旧模型）
# ⚠️ 这些字符串**只允许**出现在「引用旧模型并纠正它」的行里 —— 那类行带 `EXEMPT` 标记。
RETIRED = [
    "G4 必须最后",
    "G4 必须在 G3 之后",
    "G4 最后",
    "G1 → G2 → G3 → G4",
    "G1→G2→G3→G4",
    "基线不可变",
    "只在 G4 波激活",
    "单独成波",
    "测试结论作废",
    "结论全部作废",
]

# 「订正语境」标记：命中退休措辞的**行**若同时含这些词 ⇒ 判定为「在引用旧模型并纠正它」⇒ 放行。
# ⚠️ 这张表只兜 A5；**A1/A2/A4/A6/A7 的正向断言才是主力** ⇒ 豁免放宽不会让守门失去作用。
EXEMPT = ["撤销", "旧模型", "订正", "不是", "不再", "不需要", "是错的", "错（"]

# 新模型标记（粗粒度传播体检用）
MARKERS = ["常设", "随时接收"]


def load_docs() -> dict[str, list[str]]:
    """读权威文档 → {路径: 行列表}；文件缺失 ⇒ 空列表（由 A0 报红）。"""
    docs: dict[str, list[str]] = {}
    for rel in AUTH_DOCS:
        p = REPO / rel
        docs[rel] = p.read_text(encoding="utf-8").splitlines() if p.is_file() else []
    return docs


def block(lines: list[str], start, stop) -> list[str]:
    """取「从第一行满足 `start` 起、到第一行满足 `stop` 前（不含）」的行块。"""
    out: list[str] = []
    started = False
    for ln in lines:
        if not started:
            if start(ln):
                started = True
                out.append(ln)
            continue
        if stop(ln):
            break
        out.append(ln)
    return out


def _has(lines: list[str], needle: str) -> bool:
    return any(needle in ln for ln in lines)


def evaluate(docs: dict[str, list[str]]) -> list[tuple[str, str]]:
    """跑全部断言 → 返回 [(断言 ID, 失败信息)]；空列表 = 全绿。"""
    fails: list[tuple[str, str]] = []

    def fail(aid: str, msg: str) -> None:
        fails.append((aid, msg))

    # ---- A0 权威文档必须存在 ------------------------------------------------
    for rel in AUTH_DOCS:
        if not docs.get(rel):
            fail("A0", f"权威文档缺失或为空：`{rel}` ⇒ 角色模型无处校验 ⇒ 守门对这一面失明")

    # ---- A1 新模型标记必须传播到每一份权威文档 ------------------------------
    for rel in AUTH_DOCS:
        lines = docs.get(rel) or []
        if lines and not any(m in ln for ln in lines for m in MARKERS):
            fail(
                "A1",
                f"`{rel}` **没有新模型标记**（{' / '.join(MARKERS)}）"
                " ⇒ 该文档仍按旧模型（G4 最后 / 落码窗口只在 G4 波激活）描述角色"
                " ⇒ 读到它的人会把域波误串行化",
            )

    # ---- A2 AGENTS.md 的 §5.3d / §5.3e 两块 -------------------------------
    ag = docs.get("AGENTS.md") or []
    if ag:
        e_block = block(
            ag,
            lambda ln: ln.startswith("3e."),
            lambda ln: ln.startswith("3f.") or ln.startswith("4."),
        )
        if not e_block:
            fail("A2", "`AGENTS.md` 找不到 §5.3e 块（以 `3e.` 起）⇒ 断言落空")
        else:
            for need in ("常设执行者", "随时接收", "并行"):
                if not _has(e_block, need):
                    fail(
                        "A2",
                        f"`AGENTS.md` **§5.3e 块**缺 `{need}`"
                        " ⇒ 该块未把 G4 写成「常设的共享层执行者」",
                    )
        d_block = block(ag, lambda ln: ln.startswith("3d."), lambda ln: ln.startswith("3e."))
        if not d_block:
            fail("A2", "`AGENTS.md` 找不到 §5.3d 块（以 `3d.` 起）⇒ 断言落空")
        elif not _has(d_block, "G1→G2→G3"):
            fail("A2", "`AGENTS.md` **§5.3d 块**缺 `G1→G2→G3` ⇒ 域波顺序未写明")

    # ---- A3 域波顺序里不得含 G4 -------------------------------------------
    for rel in AUTH_DOCS:
        for i, ln in enumerate(docs.get(rel) or [], 1):
            if "G1→G2→G3→G4" in ln and not any(e in ln for e in EXEMPT):
                fail("A3", f"`{rel}:{i}` 出现 `G1→G2→G3→G4` ⇒ 域波顺序里**没有 G4**（G4 是常设执行者）")

    # ---- A4 规则表 C2 / C7 必须存在 ----------------------------------------
    cc = docs.get("docs/DRIFT/_cross/cross-domain-coupling.md") or []
    if cc:
        c2 = [ln for ln in cc if ln.strip().startswith("| **C2**")]
        if not c2:
            fail("A4", "`cross-domain-coupling.md` 缺 **C2** 行（共享层规则的第 2 条）")
        else:
            for need in ("随时接收", "落码窗口常开"):
                if not any(need in ln for ln in c2):
                    fail("A4", f"`cross-domain-coupling.md` 的 **C2** 行缺 `{need}` ⇒ C2 未表达「常设执行者」")
        c7 = [ln for ln in cc if ln.strip().startswith("| **C7**")]
        if not c7:
            fail("A4", "`cross-domain-coupling.md` 缺 **C7** 行（收口回归口径）")
        elif not any("收口回归在波次末尾跑" in ln for ln in c7):
            fail("A4", "`cross-domain-coupling.md` 的 **C7** 行缺 `收口回归在波次末尾跑`")
        if not _has(cc, "不在域波顺序里"):
            fail("A4", "`cross-domain-coupling.md` §二.2 缺 `不在域波顺序里`")

    # ---- A5 退休措辞不得作为断言回流 ----------------------------------------
    for rel in AUTH_DOCS:
        for i, ln in enumerate(docs.get(rel) or [], 1):
            hit = [r for r in RETIRED if r in ln]
            if hit and not any(e in ln for e in EXEMPT):
                fail(
                    "A5",
                    f"`{rel}:{i}` 出现退休措辞 {hit}"
                    " ⇒ 旧模型（G4 最后 / 落码窗口只在 G4 波激活 / 结论作废）回流"
                    " ⇒ 该行须带订正语境标记，或直接删掉",
                )

    # ---- A6 Worktree.md 顺序行 ------------------------------------------------
    wt = docs.get("docs/Worktree.md") or []
    if wt:
        if not _has(wt, "G1 → G2 → G3"):
            fail("A6", "`docs/Worktree.md` 缺 `G1 → G2 → G3` ⇒ 顺序行未按新模型写")
        if _has(wt, "G1 → G2 → G3 → G4"):
            fail("A6", "`docs/Worktree.md` 出现 `G1 → G2 → G3 → G4` ⇒ 域波顺序里**没有 G4**")

    # ---- A7 规范锚点必须逐份存在 --------------------------------------------
    for rel in ANCHOR_DOCS:
        lines = docs.get(rel) or []
        if lines and not _has(lines, CANONICAL_ANCHOR):
            fail(
                "A7",
                f"`{rel}` 缺**规范锚点** `{CANONICAL_ANCHOR}`"
                " ⇒ 该台账没有把 G4 写成常设执行者（锚点被改 ⇒ 角色描述失效）",
            )

    return fails


# ---------------------------------------------------------------- 变异验证
MUTATIONS = [
    (
        "M1 AGENTS.md §5.3e 退回「共享层波」",
        "AGENTS.md",
        "G4 = 共享层常设执行者",
        "G4 = 共享层波",
        {"A2", "A7"},
    ),
    (
        "M2 AGENTS.md §5.3d 写回「G4 最后」",
        "AGENTS.md",
        "**域波顺序 G1→G2→G3**",
        "**域波顺序 G1→G2→G3→G4**，**G4 最后**",
        {"A2", "A3", "A5"},
    ),
    (
        "M3 cross-domain-coupling.md 删掉 C2 行",
        "docs/DRIFT/_cross/cross-domain-coupling.md",
        "| **C2** |",
        "| **C2-removed** |",
        {"A4"},
    ),
    (
        "M4 Worktree.md 写回旧顺序",
        "docs/Worktree.md",
        "⚠️ **域波顺序**：**G1 → G2 → G3**（契约 DAG 定序）。",
        "⚠️ **顺序不可乱**：**G1 → G2 → G3 → G4**。",
        {"A5", "A6"},
    ),
    (
        "M5 domain-common-lanes.md 抹掉规范锚点",
        "docs/DRIFT/_cross/domain-common-lanes.md",
        "G4 = 共享层常设执行者",
        "G4 = 共享层波",
        {"A7"},
    ),
    (
        "M6 _PROTOCOL.md 抹掉规范锚点",
        "docs/DRIFT/global/lanes/_PROTOCOL.md",
        "G4 = 共享层常设执行者",
        "G4 = 共享层波",
        {"A7"},
    ),
    (
        "M7 cross-domain-coupling.md 抹掉「G4 不在域波顺序里」",
        "docs/DRIFT/_cross/cross-domain-coupling.md",
        "不在域波顺序里",
        "G4 必须最后",
        {"A4", "A5"},
    ),
]


def run_mutation_test() -> int:
    base = load_docs()
    fails = evaluate(base)
    print("角色模型守门 · 变异验证")
    print("=" * 60)
    if fails:
        print("❌ 基线不绿 ⇒ 先修文档再谈变异")
        for aid, msg in fails:
            print(f"  [{aid}] {msg}")
        return 1
    print("  基线绿（0 项违规）")
    print("-" * 60)

    bad = 0
    for label, rel, old, new, expect in MUTATIONS:
        docs = {k: list(v) for k, v in base.items()}
        text = "\n".join(docs[rel])
        if old not in text:
            print(f"  ❌ {label} ⇒ 变异锚点未命中（守门脚本与文档脱节）")
            bad += 1
            continue
        # ⚠️ **全量替换**：只替换首个命中 ⇒ 同锚点的其它处仍在 ⇒ 守门仍绿 ⇒ 假绿（实测踩到）
        docs[rel] = text.replace(old, new).splitlines()
        got = {aid for aid, _ in evaluate(docs)}
        if not (got & expect):
            print(f"  ❌ {label} ⇒ 期望 {sorted(expect)} 转红，实际 {sorted(got) or '全绿'}")
            bad += 1
        else:
            print(f"  ✅ {label} ⇒ 转红 {sorted(got & expect)}")
    print("-" * 60)
    if bad:
        print(f"❌ {bad}/{len(MUTATIONS)} 条变异未被捕获 ⇒ 守门有假绿")
        return 1
    print(f"✅ PASS（{len(MUTATIONS)}/{len(MUTATIONS)} 条变异全部被捕获）")
    return 0


def main(argv: list[str]) -> int:
    if "--mutation-test" in argv:
        return run_mutation_test()

    docs = load_docs()
    fails = evaluate(docs)

    print("波次角色模型守门（G4 = 常设的共享层执行者）")
    print("=" * 60)
    for rel in AUTH_DOCS:
        lines = docs.get(rel) or []
        mark = "✅" if lines and any(m in ln for ln in lines for m in MARKERS) else "🔴"
        print(f"  {mark} {rel}")
    print("-" * 60)

    if fails:
        print("❌ FAIL\n")
        for aid, msg in fails:
            print(f"  [{aid}] {msg}")
            print()
        print(f"⇒ {len(fails)} 项违规")
        return 1

    print("✅ PASS")
    print(f"  · {len(AUTH_DOCS)} 份权威文档全部含新模型标记（常设 / 随时接收）")
    print("  · AGENTS.md §5.3e = 常设执行者；§5.3d 域波顺序 = G1→G2→G3（不含 G4）")
    print("  · cross-domain-coupling.md 规则表含 C2（常设执行者）与 C7（收口回归口径）")
    print("  · 4 份域台账均含规范锚点 `G4 = 共享层常设执行者`")
    print("  · 退休措辞（G4 最后 / 单独成波 / 基线不可变 / 结论作废）未回流")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
