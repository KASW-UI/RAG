#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
favorite 域台账自检（ledger self-check）——机器化回答两个问题：

  1. **台账是不是「最新」**：台账之间的章节指针能不能落到真实存在的小节？编号
     （DEV-FAV-* / PIT-FAV-*）的定义与引用是否闭合？
  2. **台账是不是「全」**：Go 侧 favorite 的每个非测试源文件，在台账里有没有落点？

依据 AGENTS.md §3.3.2（断言必须可核验）与 §5.3b（三档台账：每域必读 + 必写回）。

为什么要有这个工具
------------------
台账是**给人读**的，但「最新 / 全」是**可机检**的：
手写台账会积累三类静默缺陷 —— ① 跨文件章节引用指错（改标题号后没人发现）；
② 编号引用了但没登记；③ Go 侧新增了源文件但台账没跟上。
前两轮体检（第九 / 十轮）都是手工查，代价高且不可复跑 ⇒ 本工具把那套人工动作固化成守门。

误报处理（**工具自身的第一版必须先修再信**）
------------------------------------------
  · **散文里的路径不算引用**：只有 markdown 链接 ``[..](path.md) §x.y`` 才做文件级解析；
    正文里裸写的 ``DRIFT/favorite/rpc.md`` §5-U1`` 是「位置描述」，不判死链。
  · **外部引用目标也纳入索引**：根 ``AGENTS.md`` 与三个共享 ``_INDEX.md`` 是合法引用目标，
    按**真实标题**判定小节是否存在。
  · **生成代码不计入 RAG 侧覆盖**：``target/`` 下的 protoc 产物不是源文件。
  · **裸行号锚点（``:48-50``）只做提示**：表格里若有语境列（如同列已给文件名），
    裸行号是可接受的简写；只有**无语境**的裸行号才需要补全 —— 由人工判定。

退出码
------
  0 = 无门禁级问题（门禁级：死指针 / 编号未登记 / Go 源文件未落点）
  1 = 存在门禁级问题
  2 = 工具自身出错（路径不对等）
"""
import argparse
import json
import os
import re
import sys
from collections import defaultdict

LEDGER_DIRS = [
    "docs/DRIFT/favorite",
    "docs/Intentional_Deviations/favorite",
    "docs/Pitfalls/favorite",
]
EXTERNAL_REF_TARGETS = [
    "AGENTS.md",
    "docs/DRIFT/_INDEX.md",
    "docs/Intentional_Deviations/_INDEX.md",
    "docs/Pitfalls/_INDEX.md",
]
GO_ROOT_HINT = "Sea-RideTheWind_golang"

SEC_RE = re.compile(r"§\s*(\d+(?:\.\d+)*)")
HEAD_NUM_RE = re.compile(r"^#{1,6}\s*(\d+(?:\.\d+)*)[.、\s]")
# 文件级引用：只有 markdown 链接后面的 §x.y 才做跨文件解析
LINK_REF_RE = re.compile(r"\[[^\]]*\]\(([^)]+\.md)\)[^\n]{0,40}?§\s*(\d+(?:\.\d+)*)")
BARE_REF_RE = re.compile(r"(?<![./\w])§\s*(\d+(?:\.\d+)*)")
# 编号：DEV-FAV-*（有意偏离）/ PIT-FAV-*（踩坑）
ID_RE = re.compile(r"\b(DEV-FAV-[A-Z]+-\d+|PIT-FAV-[A-Z]+-\d+)\b")
# 裸行号锚点：形如 `:48-50` / `:71`
NAKED_ANCHOR_RE = re.compile(r"`:(\d+(?:-\d+)?)`")


def find_repo_root(start):
    cur = os.path.abspath(start)
    while True:
        if os.path.isdir(os.path.join(cur, "docs", "DRIFT", "favorite")):
            return cur
        parent = os.path.dirname(cur)
        if parent == cur:
            return None
        cur = parent


def collect_ledger_files(root):
    files = []
    for d in LEDGER_DIRS:
        p = os.path.join(root, d)
        if not os.path.isdir(p):
            continue
        for f in sorted(os.listdir(p)):
            if f.endswith(".md"):
                files.append(os.path.join(d, f))
    return files


def real_sections(path):
    """文件里真实存在的小节号：标题里的编号 + 正文里被引用的 §号。"""
    secs = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = HEAD_NUM_RE.match(line)
            if m:
                secs.add(m.group(1))
            for s in SEC_RE.findall(line):
                secs.add(s)
    return secs


def heading_sections(path):
    """只看标题里真实存在的小节号（用于外部目标判定，严于 real_sections）。"""
    secs = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = HEAD_NUM_RE.match(line)
            if m:
                secs.add(m.group(1))
    return secs


def resolve_ref(root, from_file, hint):
    if not hint or hint.startswith("http"):
        return None
    cand = os.path.normpath(os.path.join(os.path.dirname(from_file), hint))
    if os.path.exists(os.path.join(root, cand)):
        return cand
    cand2 = os.path.normpath(hint)
    if os.path.exists(os.path.join(root, cand2)):
        return cand2
    return None


def find_go_root(root, explicit):
    cands = []
    if explicit:
        cands.append(explicit)
    parent = os.path.dirname(root)
    cands.append(os.path.join(parent, GO_ROOT_HINT))
    cands.append(os.path.join(parent, "..", GO_ROOT_HINT))
    for c in cands:
        c = os.path.abspath(c)
        if os.path.isdir(os.path.join(c, "service", "favorite")):
            return c
    return None


def main():
    ap = argparse.ArgumentParser(description="favorite 域台账自检")
    ap.add_argument("--repo-root", default=os.getcwd(), help="RAG 工作树根目录")
    ap.add_argument("--go-root", default=None, help="Go 真源仓库路径")
    ap.add_argument("--json", action="store_true", help="输出 JSON")
    args = ap.parse_args()

    root = find_repo_root(args.repo_root)
    if not root is None:
        root = os.path.abspath(root)
    if root is None:
        print("❌ 找不到 RAG 工作树根（期望 docs/DRIFT/favorite 存在）", file=sys.stderr)
        return 2

    ledger_files = collect_ledger_files(root)
    if not ledger_files:
        print("❌ 没找到 favorite 域台账", file=sys.stderr)
        return 2

    ref_files = list(ledger_files)
    missing_targets = []
    for e in EXTERNAL_REF_TARGETS:
        if os.path.exists(os.path.join(root, e)):
            ref_files.append(e)
        else:
            missing_targets.append(e)

    SEC = {p: real_sections(os.path.join(root, p)) for p in ref_files}
    HEAD = {p: heading_sections(os.path.join(root, p)) for p in ref_files}

    gates = []   # 门禁级
    hints = []   # 提示级

    # ---------- ① 章节指针 ----------
    stats = defaultdict(int)
    for path in ledger_files:
        full = os.path.join(root, path)
        for lineno, line in enumerate(open(full, encoding="utf-8"), 1):
            spans = []
            for m in LINK_REF_RE.finditer(line):
                spans.append(m.span())
                hint, sec = m.group(1), m.group(2)
                stats["file_scoped"] += 1
                tgt = resolve_ref(root, path, hint)
                if tgt is None:
                    gates.append(dict(kind="dead-link", file=path, line=lineno,
                                      detail=f"链接目标不存在：{hint}"))
                    continue
                if tgt in HEAD:
                    ok = sec in HEAD[tgt]
                else:
                    ok = sec in SEC.get(tgt, set())
                if not ok:
                    gates.append(dict(kind="dead-section", file=path, line=lineno,
                                      detail=f"{tgt} 无小节 §{sec}"))
            for m in BARE_REF_RE.finditer(line):
                if any(s <= m.start() < e for s, e in spans):
                    continue
                sec = m.group(1)
                stats["bare"] += 1
                if sec not in SEC[path]:
                    gates.append(dict(kind="dead-section", file=path, line=lineno,
                                      detail=f"同文件无小节 §{sec}"))

    # ---------- ② 编号闭合 ----------
    ids = defaultdict(lambda: dict(refs=[], defs=[]))
    for path in ledger_files:
        full = os.path.join(root, path)
        for lineno, line in enumerate(open(full, encoding="utf-8"), 1):
            for m in ID_RE.finditer(line):
                iid = m.group(1)
                ids[iid]["refs"].append((path, lineno))
                # 定义处：markdown 标题里带编号，或表格首列就是编号
                if re.match(r"^#{1,6}.*" + re.escape(iid), line):
                    ids[iid]["defs"].append((path, lineno))
                elif re.match(r"^\|?\s*\**" + re.escape(iid) + r"\**\s*\|", line):
                    ids[iid]["defs"].append((path, lineno))
    for iid, info in sorted(ids.items()):
        if not info["defs"]:
            gates.append(dict(kind="unregistered-id", file=info["refs"][0][0],
                              line=info["refs"][0][1],
                              detail=f"{iid} 被引用 {len(info['refs'])} 次但无定义处"))

    # ---------- ③ Go 侧源文件覆盖 ----------
    go_root = find_go_root(root, args.go_root)
    go_files = []
    go_uncovered = []
    if go_root is None:
        hints.append(dict(kind="go-root-missing",
                          detail=f"找不到 Go 真源（{GO_ROOT_HINT}），跳过覆盖检查"))
    else:
        for base, _dirs, files in os.walk(os.path.join(go_root, "service", "favorite")):
            for fn in files:
                if fn.endswith(".go") and not fn.endswith("_test.go"):
                    go_files.append(os.path.relpath(os.path.join(base, fn), go_root))
        corpus = "\n".join(open(os.path.join(root, p), encoding="utf-8").read() for p in ledger_files)
        for rel in sorted(go_files):
            bn = os.path.basename(rel)
            tail = rel.split("service/favorite/", 1)[1]
            if bn in corpus or tail in corpus or rel in corpus:
                continue
            go_uncovered.append(rel)
            gates.append(dict(kind="go-file-uncovered", file="(go)", line=0,
                              detail=f"Go 源文件在台账里没有落点：{rel}"))

    # ---------- ④ 裸行号锚点（提示级）----------
    naked = []
    for path in ledger_files:
        full = os.path.join(root, path)
        for lineno, line in enumerate(open(full, encoding="utf-8"), 1):
            for m in NAKED_ANCHOR_RE.finditer(line):
                naked.append((path, lineno, m.group(0)))
                hints.append(dict(kind="naked-anchor", file=path, line=lineno,
                                  detail=f"裸行号锚点 {m.group(0)} —— 需人工确认有无语境列"))

    result = dict(
        repo_root=root,
        go_root=go_root,
        ledger_files=len(ledger_files),
        external_targets_missing=missing_targets,
        section_refs=dict(file_scoped=stats["file_scoped"], bare=stats["bare"]),
        ids_checked=len(ids),
        go_source_files=len(go_files),
        go_uncovered=go_uncovered,
        naked_anchors=len(naked),
        gates=gates,
        hints=hints,
    )

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if gates else 0

    print(f"favorite 域台账自检 · {root}")
    print(f"  台账文件：{len(ledger_files)} 份"
          + (f"（外部引用目标缺 {len(missing_targets)} 个：{missing_targets}）" if missing_targets else ""))
    print(f"  章节引用：文件级 {stats['file_scoped']} 处 / 裸引用 {stats['bare']} 处")
    print(f"  编号闭合：检查 {len(ids)} 个 DEV/PIT 编号")
    if go_root:
        print(f"  Go 源文件：{len(go_files)} 个（非测试），未落点 {len(go_uncovered)} 个")
    else:
        print("  Go 源文件：跳过（未找到 Go 真源）")
    print(f"  裸行号锚点（提示级）：{len(naked)} 处")
    print()
    if gates:
        print(f"❌ 门禁级问题 {len(gates)} 条：")
        for g in gates:
            print(f"   [{g['kind']}] {g['file']}:{g['line']} —— {g['detail']}")
    else:
        print("✅ 门禁级问题 0 条")
    if hints:
        print(f"\nℹ️  提示级 {len(hints)} 条（裸行号锚点需人工确认语境）：")
        for h in hints[:20]:
            print(f"   [{h['kind']}] {h.get('file','')}:{h.get('line','')} —— {h['detail']}")
        if len(hints) > 20:
            print(f"   …… 其余 {len(hints)-20} 条省略")
    return 1 if gates else 0


if __name__ == "__main__":
    sys.exit(main())
