#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 hot-rpc 双侧活体对拍（D1）用的**固定内容、固定顺序**的事件集。

【为什么事件必须"无桶冲突"】
  HeavyKeeper.Add 的分支（Go service/hot/heavykeeper/heavykeeper.go:84-111）：
      空桶            → 占位，count = weight
      指纹相同        → count += weight
      指纹不同(冲突)  → **以概率 decay^count 衰减**（第 98 行 rand.Float64()）
  ⇒ 一旦两个不同的 article 落到同一个桶，指纹不同就会走**概率衰减**分支。
  Go 用 math/rand 全局源、RAG 用 Java 的随机源 ⇒ **两侧的随机数序列不可能相同**
  ⇒ 冲突一旦发生，两侧的 sketch 必然发散，D1 就只能测出"随机噪声差异"，测不出语义差异。
  ⇒ 本脚本**在生成阶段就排除所有桶冲突**（同一行内列号互不相同），
     并且**排除指纹相同**的情况 ⇒ 概率衰减分支**不可达** ⇒ 两侧结果可逐字节比对。

【列号是怎么算的】（必须与 Go 逐字一致）
  Go  service/hot/heavykeeper/heavykeeper.go:185-190
      h := fnv.New64a(); h.Write([]byte(key)); h.Write([]byte{byte(seed)})
      return int(h.Sum64() % uint64(hk.width))
  ⇒ FNV-1a 64 over (key 的字节 ‖ 单字节 seed)，再对 width 取**无符号**余数。
  指纹 Go :178-182 = FNV-1a 32 over key。

【事件内容】Go service/hot/rpc/internal/mqs/types.go 只有两个字段
  {"article_id": "...", "type": "like|comment|coin|share"}
  ⚠️ 生产端**故意多发** user_id / timestamp 两个未知字段 ——
     两侧都忽略未知字段（Go: encoding/json 默认；RAG: @JsonIgnoreProperties(ignoreUnknown=true)），
     顺带把这条契约也验掉。

【权重】yaml Interaction.Weights: like=1 comment=2 coin=3 share=4
  ⇒ 因为无桶冲突，每篇文章的 HeavyKeeper 计数 == 它的**真实权重和**，
     可以据此**预测** hot:articles 的 score 与排序 ⇒ 比对时多一道独立断言。

用法:
  python3 tools/difftest/hot_rpc_events.py --out-dir /tmp/hot-rpc-difftest/events
产物:
  events.tsv   —— 每行 "<key>\t<value>"，供 kafka-console-producer.sh 消费
  manifest.json —— 预期 article_id 集合 + 预期权重和 + 预期排序（给栅栏/比对器用）
"""

import argparse
import json
import os

WIDTH = 1000          # Go HeavyKeeper.Width
DEPTH = 5             # Go HeavyKeeper.Depth
FNV64_OFFSET = 14695981039346656037
FNV64_PRIME = 1099511628211
FNV32_OFFSET = 2166136261
FNV32_PRIME = 16777619
MASK64 = (1 << 64) - 1
MASK32 = (1 << 32) - 1

TYPES = ["like", "comment", "coin", "share"]
WEIGHTS = {"like": 1, "comment": 2, "coin": 3, "share": 4}


def fnv1a64(data: bytes) -> int:
    h = FNV64_OFFSET
    for b in data:
        h ^= b
        h = (h * FNV64_PRIME) & MASK64
    return h


def fnv1a32(data: bytes) -> int:
    h = FNV32_OFFSET
    for b in data:
        h ^= b
        h = (h * FNV32_PRIME) & MASK32
    return h


def bucket_of(key: str, seed: int) -> int:
    """Go heavykeeper.go:185-190 —— FNV-1a 64(key ‖ seed 字节) % width（无符号）。"""
    return fnv1a64(key.encode("utf-8") + bytes([seed & 0xFF])) % WIDTH


def fingerprint_of(key: str) -> int:
    """Go heavykeeper.go:178-182 —— FNV-1a 32(key)。"""
    return fnv1a32(key.encode("utf-8"))


def pick_ids(count: int, prefix: str = "art-") -> list:
    """贪心挑 count 个**行内列号互不相同 + 指纹互不相同**的 id。

    ⇒ 任意两个 id 在任意一行都不会落在同一个桶 ⇒ HeavyKeeper 的概率衰减分支不可达。
    """
    chosen = []
    used_cols = {seed: set() for seed in range(DEPTH)}
    used_fps = set()
    n = 0
    while len(chosen) < count:
        candidate = "%s%03d" % (prefix, n)
        n += 1
        cols = [bucket_of(candidate, seed) for seed in range(DEPTH)]
        if any(col in used_cols[seed] for seed, col in enumerate(cols)):
            continue
        fp = fingerprint_of(candidate)
        if fp in used_fps:
            continue
        for seed, col in enumerate(cols):
            used_cols[seed].add(col)
        used_fps.add(fp)
        chosen.append(candidate)
    return chosen


def build_events(ids: list) -> list:
    """固定内容、固定顺序的事件列表。

    文章 i 拿 (len(ids) - i) 条事件，type 按 TYPES[(i + j) % 4] 轮转
    ⇒ 4 种 type 全覆盖、同文章多 type（制造**热度差异**）、且总事件数 > Interaction.SyncEvery(100)
    ⇒ 必然触发两侧的快照阈值（Go hot_handler.go:179 / RAG HotEventConsumer.java:187）。
    """
    events = []
    for i, aid in enumerate(ids):
        n = len(ids) - i
        for j in range(n):
            etype = TYPES[(i + j) % len(TYPES)]
            value = {
                "article_id": aid,
                "type": etype,
                # 未知字段：两侧都必须忽略（Go encoding/json / RAG @JsonIgnoreProperties）
                "user_id": "u-%04d" % j,
                "timestamp": 1700000000 + j,
            }
            events.append((aid, etype, value))
    return events


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default="/tmp/hot-rpc-difftest/events")
    ap.add_argument("--articles", type=int, default=24)
    args = ap.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    ids = pick_ids(args.articles)
    events = build_events(ids)

    weight_sum = {aid: 0 for aid in ids}
    type_count = {t: 0 for t in TYPES}
    for aid, etype, _ in events:
        weight_sum[aid] += WEIGHTS[etype]
        type_count[etype] += 1

    # 预期排序：按权重和降序；**同分时按 id 逆字典序** ——
    # Redis 的 ZREVRANGE 在同分时返回 member 的逆字典序（ZRANGE 是字典序，ZREVRANGE 反过来）。
    grouped = {}
    for aid in ids:
        grouped.setdefault(weight_sum[aid], []).append(aid)
    expected_order = []
    for score in sorted(grouped, reverse=True):
        expected_order.extend(sorted(grouped[score], reverse=True))

    tsv_path = os.path.join(args.out_dir, "events.tsv")
    with open(tsv_path, "w", encoding="utf-8") as fh:
        for aid, _etype, value in events:
            fh.write("%s\t%s\n" % (aid, json.dumps(value, separators=(",", ":"), sort_keys=True)))

    manifest = {
        "topic": "article-hot-events",
        "articles": ids,
        "event_count": len(events),
        "type_counts": type_count,
        "weight_sum": weight_sum,
        "expected_order_by_weight": expected_order,
        "bucket_width": WIDTH,
        "bucket_depth": DEPTH,
        "no_collision": True,
        "note": "weight_sum == HeavyKeeper count（无桶冲突 ⇒ 概率衰减分支不可达）",
    }
    manifest_path = os.path.join(args.out_dir, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)

    # 自检：确认选出的 id 确实无冲突
    cols = {}
    for aid in ids:
        for seed in range(DEPTH):
            cols.setdefault(seed, []).append(bucket_of(aid, seed))
    for seed, col_list in cols.items():
        assert len(col_list) == len(set(col_list)), "seed %d 仍有列号冲突" % seed
    fps = [fingerprint_of(a) for a in ids]
    assert len(fps) == len(set(fps)), "指纹有重复"

    print("events file   : %s" % tsv_path)
    print("manifest file : %s" % manifest_path)
    print("articles      : %d" % len(ids))
    print("events        : %d  (types: %s)" % (len(events), type_count))
    print("syncEvery=100 ⇒ 事件数 %s 阈值" % ("超过" if len(events) > 100 else "**未超过**"))
    print("bucket collision check: OK (depth=%d, width=%d, 无同列/同指纹)" % (DEPTH, WIDTH))
    print("top-5 by weight: %s" % [(a, weight_sum[a]) for a in expected_order[:5]])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
