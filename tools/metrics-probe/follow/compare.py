#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
follow 域 **指标抓取面**双侧 series 集合比对器。

输入：Go 侧 DevServer `/metrics` 文本 + RAG 侧 `/actuator/prometheus` 文本
      + 一份**冻结的期望差异**（expected-diff.txt）

比的是什么（**只比 series 集合，不比直方图的值**）：
  - series = 指标名 + **标签集合**（标签顺序归一化；`le` 按**浮点数值**归一化）
  - 直方图的 `_bucket` **值**取决于真实耗时 ⇒ 两侧必然不同 ⇒ **不比值**；
    `_sum` 同理；`_count` 比（观测次数 = 请求次数 ⇒ 应相等）。
  - 计数型（`request_total` / `relation_action_total` / `db_error_total`）
    与 gauge（`recommendation_size`）**比值** ⇒ 两侧收到同一批请求 ⇒ 必须相等。

退出码：
  0 = 实际差异与冻结期望**逐条一致**（守门通过）
  1 = 实际差异与冻结期望**不一致**（守门失效）
  2 = 输入不可解析（环境问题 ⇒ 判定无效）
"""
import os
import re
import sys
import collections

PREFIX = "follow_rpc_"
# 计数型 / gauge 型：**值必须两侧相等**（同一批请求 ⇒ 同一批计数）
VALUE_COMPARED = (
    "follow_rpc_request_total",
    "follow_rpc_relation_action_total",
    "follow_rpc_db_error_total",
    "follow_rpc_recommendation_size",
    "follow_rpc_request_duration_seconds_count",
)


def die_env(msg):
    sys.stderr.write("!! %s\n" % msg)
    sys.exit(2)


def norm_le(v):
    """`le` 归一化：+Inf 保留；其余按浮点数值归一化（'1' 与 '1.0' 视为同一个桶）。"""
    if v == "+Inf":
        return "+Inf"
    try:
        f = float(v)
    except ValueError:
        return v
    if f == int(f) and abs(f) < 1e15:
        return str(int(f))
    return repr(f)


def parse(path):
    """⇒ {series_key: value_or_None}；series_key = name{k=v,k=v}（标签已排序）"""
    out = {}
    if not os.path.isfile(path):
        die_env("找不到指标文件：%s" % path)
    with open(path, encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            if not line.startswith(PREFIX):
                continue
            m = re.match(r"^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+(.*)$", line)
            if not m:
                die_env("无法解析的指标行（%s）：%r" % (path, line))
            name = m.group(1)
            labels = m.group(2) or ""
            pairs = []
            for lm in re.finditer(r'([a-zA-Z_][a-zA-Z0-9_]*)="([^"]*)"', labels):
                k, v = lm.group(1), lm.group(2)
                if k == "le":
                    v = norm_le(v)
                pairs.append("%s=%s" % (k, v))
            key = name + "{" + ",".join(sorted(pairs)) + "}"
            raw_val = m.group(3).strip()
            try:
                val = float(raw_val)
            except ValueError:
                die_env("无法解析的指标值（%s）：%r" % (path, line))
            out[key] = val
    return out


def load_expected(path):
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as fh:
        return sorted(l.strip() for l in fh if l.strip() and not l.startswith("#"))


def main():
    if len(sys.argv) < 3:
        die_env("用法：compare.py <go-metrics> <rag-metrics> [expected-diff]")
    go_path, rag_path = sys.argv[1], sys.argv[2]
    exp_path = sys.argv[3] if len(sys.argv) > 3 else None

    go = parse(go_path)
    rag = parse(rag_path)
    if not go:
        die_env("Go 侧没抓到任何 %s* series（抓取出口坏了？）" % PREFIX)
    if not rag:
        die_env("RAG 侧没抓到任何 %s* series（抓取出口坏了？）" % PREFIX)

    only_go = sorted(set(go) - set(rag))
    only_rag = sorted(set(rag) - set(go))

    # ── 值比对（只对确定性计数型 / gauge 型）────────────────────────────
    value_diff = []
    for key in sorted(set(go) & set(rag)):
        name = key.split("{")[0]
        if name not in VALUE_COMPARED:
            continue
        if abs(go[key] - rag[key]) > 1e-9:
            value_diff.append("VALUE|%s|go=%s|rag=%s" % (key, go[key], rag[key]))

    actual = only_go + only_rag + value_diff

    def fam(counter, keys):
        c = collections.Counter()
        for k in keys:
            c[k.split("{")[0]] += 1
        return c

    fg, fr = fam(None, go), fam(None, rag)
    print("== 指标族行数 ==")
    for k in sorted(set(fg) | set(fr)):
        flag = "OK " if fg.get(k, 0) == fr.get(k, 0) else "DIFF"
        print("  [%s] %-45s go=%-4d rag=%-4d" % (flag, k, fg.get(k, 0), fr.get(k, 0)))
    print("== series 集合 ==")
    print("  go=%d  rag=%d  仅go=%d  仅rag=%d  值差异=%d"
          % (len(go), len(rag), len(only_go), len(only_rag), len(value_diff)))

    if actual:
        print("== 实际差异 ==")
        for x in actual:
            print("  " + x)

    if exp_path is None:
        print("!! 未提供期望差异文件 ⇒ 只报告不判定")
        sys.exit(2)

    expected = load_expected(exp_path)
    if expected is None:
        die_env("找不到期望差异文件：%s" % exp_path)

    if sorted(actual) == sorted(expected):
        print("== 判定 ==")
        print("  实际差异与冻结期望**逐条一致**（%d 条）⇒ 守门通过" % len(expected))
        sys.exit(0)

    print("== 判定 ==")
    print("  ❌ 实际差异与冻结期望**不一致**（期望 %d 条 / 实际 %d 条）" % (len(expected), len(actual)))
    for x in sorted(set(expected) - set(actual)):
        print("  - 期望有、实际无：%s" % x)
    for x in sorted(set(actual) - set(expected)):
        print("  + 实际有、期望无：%s" % x)
    sys.exit(1)


if __name__ == "__main__":
    main()
