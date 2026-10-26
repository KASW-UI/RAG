#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""════════════════════════════════════════════════════════════════════════════
 mutate-deadline-downstream.py —— E38② 重估探针的**变异测试**（证明探针非假绿）
════════════════════════════════════════════════════════════════════════════

【为什么需要它】
  §3.3 的铁律：**「跑绿」不是「对」**。
  本探针是**漂移形态守门**（不是等值守门）⇒ 它断言的是
  「Go=present_expired / RAG=absent」这种**已登记的漂移形态**。
  ⇒ 如果它是个恒真的探针（比如从没真的跑、或者 key 写错），它也会一直绿。
  ⇒ 只有**故意改变形态 ⇒ 必须精确红在目标键上**，才能证明判据非空转。

  🔴 **本条与既有两类变异都不同**：
     · `mutate-deadline-precision.py` 变异的是 **JUnit 断言**（认 surefire XML）；
     · `mutate-deadline-verdict.py` 变异的是 **Python 判定器**；
     · 本脚本变异的是 **双侧活体探针的判据** ⇒ 守门命令是 shell 脚本，
       「红在哪」由脚本打印的 `      ❌ <key>` 行判定。

【口径（PIT-GLOBAL-048 / 040 / 021 同源）】
  · 基线 = 探针 EXIT=0；
  · 变异后 = **红的键集合 == 预期集合**（少红 = 覆盖缺口，多红 = 判据串味）；
  · 跑前确认探针**真的跑过**（两侧都产出 PROBE 行）—— PIT-GLOBAL「退出码 0 ≠ 真跑过」；
  · 还原后 **sha256 一致 + 重新 touch**（PIT-GLOBAL-037）。

【变异清单（第四十六轮 · 2 条）】
  M1 **生产代码**：在 `RpcInterceptor.onHalfClose` 里绑定 `GoStyleRequestDeadline`
     （= 候选修法的第一半：让出向调用拿得到预算）
     ⇒ 必须红在 `outgoing_carrier`（absent → present_expired）
     ⇒ ⇒ **证明这条键真的在观测「载体有没有绑定」**，不是写死的
  M2 **探针自身**：给 RAG 半边的下游调用加一个 deadline
     ⇒ 必须红在 `failed_fast` + `code`
     ⇒ ⇒ **证明行为键不是恒定值**（否则 M1 红了也可能只是键写错）

用法：python3 tools/difftest/mutate-deadline-downstream.py
════════════════════════════════════════════════════════════════════════════
"""

import hashlib
import io
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))

PROBE = 'tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh'
RPC_INTERCEPTOR = 'service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java'
PROBE_JAVA = 'tools/semantic-probe/deadline-downstream/java/DeadlineDownstreamProbe.java'

# ── 变异清单：(ID, 说明, 文件, 旧串, 新串, 期望红的键集合) ─────────────────
MUTATIONS = [
    ('M1 生产代码：在 gRPC 路径绑定 GoStyleRequestDeadline（候选修法的第一半）',
     RPC_INTERCEPTOR,
     '                        try (DeadlineState.Scope ignoredScope = DeadlineState.bindToCurrent(deadlineEpochMs)) {',
     '                        GoStyleRequestDeadline.begin(System.nanoTime()\n'
     '                                + Math.max(0L, deadlineEpochMs - System.currentTimeMillis()) * 1_000_000L);\n'
     '                        try (DeadlineState.Scope ignoredScope = DeadlineState.bindToCurrent(deadlineEpochMs)) {',
     {'outgoing_carrier'}),

    ('M2 探针自身：给 RAG 半边的下游调用加 deadline（证明行为键不是恒定值）',
     PROBE_JAVA,
     '                ClientCalls.blockingUnaryCall(downstreamChannel, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);',
     '                ClientCalls.blockingUnaryCall(downstreamChannel, HANG_METHOD,\n'
     '                        CallOptions.DEFAULT.withDeadlineAfter(100, TimeUnit.MILLISECONDS), new byte[0]);',
     {'failed_fast', 'code'}),
]


def sha(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


def run_probe():
    """跑探针 ⇒ 返回 (exit_code, stdout)。"""
    proc = subprocess.run(['bash', PROBE], cwd=ROOT, capture_output=True, text=True)
    return proc.returncode, proc.stdout + proc.stderr


def parse_red_keys(out):
    """从探针输出里抽出「红的键」集合（行首是 6 个空格 + ❌）。"""
    keys = set()
    for line in out.splitlines():
        m = re.match(r'^\s+❌\s+([A-Za-z_][A-Za-z0-9_]*)\s', line)
        if m:
            keys.add(m.group(1))
    return keys


def parse_probe_lines(out):
    """抽出探针真正跑出来的 PROBE 行数（Go 半边 + RAG 半边）。

    ⚠️ runner 打印时带了缩进与 `Go |` / `RAG |` 前缀
    ⇒ **不能**用 startswith 判定 ⇒ 按「行内含 `PROBE|`」计数。
    """
    return len([l for l in out.splitlines() if 'PROBE|' in l])


def main():
    print('── 基线：探针必须 EXIT=0，且两侧都真的产出了 PROBE 行 ──')
    code, out = run_probe()
    if code != 0:
        print('❌ 基线不绿（EXIT=%d）⇒ 先把基线修好再谈变异' % code)
        print(out[-4000:])
        return 1
    produced = parse_probe_lines(out)
    if produced < 2:
        print('❌ 探针只产出 %d 条 PROBE 行（应为 2：Go 半边 + RAG 半边）'
              ' ⇒ 有一侧没真的跑 ⇒ 「退出码 0」不能当「跑过了」' % produced)
        print(out[-4000:])
        return 1
    print('✅ 基线绿，探针产出 %d 条 PROBE 行（两侧都真跑过）\n' % produced)

    bad = 0
    for desc, rel, old, new, expected in MUTATIONS:
        path = os.path.join(ROOT, rel)
        original = io.open(path, encoding='utf-8').read()
        mid = desc.split('（')[0].split('：')[0].strip()
        if original.count(old) != 1:
            print('❌ %s：锚点命中 %d 处（应为 1）⇒ 变异没变异'
                  % (mid, original.count(old)))
            bad += 1
            continue
        before_sha = sha(path)
        try:
            io.open(path, 'w', encoding='utf-8').write(original.replace(old, new, 1))
            got_code, got_out = run_probe()
        finally:
            io.open(path, 'w', encoding='utf-8').write(original)
            # PIT-GLOBAL-037：还原后必须 touch（否则 maven 可能不重编译，跑的还是变异字节码）
            os.utime(path, None)

        if got_code == 0:
            print('❌ %s：变异后探针仍然绿 ⇒ 判据空转' % mid)
            bad += 1
            continue

        after_sha = sha(path)
        if after_sha != before_sha:
            print('❌ %s：还原后 sha256 不一致 ⇒ 变异不可逆' % mid)
            bad += 1
            continue

        produced = parse_probe_lines(got_out)
        if produced < 2:
            print('❌ %s：变异后有一侧没真跑（PROBE 行 %d 条）⇒ 不能判定' % (mid, produced))
            bad += 1
            continue

        actual = parse_red_keys(got_out)
        if actual == expected:
            print('✅ %s ⇒ 被精确抓到（红在 %s）' % (desc, '、'.join(sorted(actual))))
        else:
            print('❌ %s：期望红 %s，实际红 %s' % (desc, sorted(expected), sorted(actual)))
            bad += 1

    print()
    if bad:
        print('⇒ ❌ 变异测试失败（%d 条）' % bad)
        return 1
    print('⇒ ✅ 变异测试全部精确命中（E38② 重估探针的判据非空转）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
