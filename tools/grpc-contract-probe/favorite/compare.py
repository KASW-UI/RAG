#!/usr/bin/env python3
"""
gRPC 契约双侧活体对拍的**比对器** —— favorite 域（两个探针共用同一份逻辑）。

用法：
    python3 compare.py <expected.txt> <go.txt> <rag.txt> [标签]

判定模型（与 expected.txt 的注释一一对应）：
    SAME      —— 两侧**逐字节相同**（status code / description / 消息字节 / metadata key）
    SAME_CODE —— 两侧 **status code 相同**（语义等价），description 措辞允许不同
    OBS       —— 两侧**必须不同**（已登记的框架/库层差异）⇒ 变成一样反而是回归
    SKIP      —— 显式跳过，理由必须写在 expected*.txt 的注释里

⚠️ 三条判定的**优先级**（顺序不可换）：
    ① **先**比冻结值：Go 真源变了 ⇒ 这里立刻响（否则「两侧一起变了」会被当成一致）；
    ② 再按 kind 分派 SAME / SAME_CODE / OBS / SKIP；
    ③ OBS 段要求**两侧不同** ⇒ 变成一样是回归，不是修复。

退出码：0 = 一致；1 = 有差异。
"""

import os
import sys


def load(path):
    out = {}
    if not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t", 1)
        out[parts[0]] = parts[1] if len(parts) > 1 else ""
    return out


def _code(v):
    for part in v.split("|"):
        if part.startswith("code="):
            return part[len("code="):]
    return ""


def main():
    if len(sys.argv) < 4:
        print("用法：compare.py <expected.txt> <go.txt> <rag.txt> [标签]", file=sys.stderr)
        return 2

    exp_path, go_path, rag_path = sys.argv[1], sys.argv[2], sys.argv[3]
    label = sys.argv[4] if len(sys.argv) > 4 else "gRPC 级可观测契约"

    go = load(go_path)
    rag = load(rag_path)

    if not os.path.exists(exp_path):
        print("  ⚠️ %s 尚不存在 ⇒ 下面是首次运行的逐条实测（请人工分类成 SAME/OBS/SKIP 后冻结）" % exp_path)
        for cid in go:
            g = go.get(cid, "<缺>")
            r = rag.get(cid, "<缺>")
            mark = "SAME" if g == r else "DIFF"
            print("  [%s] %s" % (mark, cid))
            print("      Go : %s" % g)
            print("      RAG: %s" % r)
        return 0

    kinds = {}
    frozen = {}
    for line in open(exp_path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        cols = line.split("\t", 2)
        if len(cols) < 3:
            print("❌ %s 行格式错误：%s" % (exp_path, line))
            return 1
        kinds[cols[0]] = cols[1]
        frozen[cols[0]] = cols[2]

    problems = []
    same = same_code = obs = skip = 0
    for cid, kind in kinds.items():
        g, r = go.get(cid), rag.get(cid)
        if g is None or r is None:
            problems.append("  · %s —— 有一侧没跑这个 case" % cid)
            continue
        if kind == "SKIP":
            skip += 1
            continue
        if frozen[cid] != g:
            problems.append("  · %s —— Go 侧与冻结值不一致 ⇒ **Go 真源的行为变了**\n"
                           "      冻结: %s\n      实测: %s" % (cid, frozen[cid], g))
            continue
        if kind == "OBS":
            obs += 1
            if g == r:
                problems.append("  · %s —— OBS 段期望两侧不同，但变成一样了 ⇒ 请重评台账" % cid)
            continue
        if kind == "SAME_CODE":
            same_code += 1
            if _code(g) != _code(r):
                problems.append("  · %s —— 两侧 status code 不一致\n"
                               "      Go : %s\n      RAG: %s" % (cid, g, r))
            continue
        same += 1
        if g != r:
            problems.append("  · %s —— 两侧不一致\n      Go : %s\n      RAG: %s" % (cid, g, r))

    print("  SAME %d 条 / SAME_CODE %d 条 / OBS %d 条 / SKIP %d 条（共 %d 个 case）"
          % (same, same_code, obs, skip, len(kinds)))
    if problems:
        print("❌ 不一致：")
        print("\n".join(problems))
        return 1
    print("✅ %s两侧一致（SAME 全等；OBS 仍不同；SKIP 已登记理由）" % label)
    return 0


if __name__ == "__main__":
    sys.exit(main())
