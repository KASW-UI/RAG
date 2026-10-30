#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""════════════════════════════════════════════════════════════════════════════
 check-deadline-downstream.py —— E38② 重估探针的**门禁**（CI 入口）
════════════════════════════════════════════════════════════════════════════

【它守什么】
  跑 `tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh`
  （双侧活体对拍：Go 真源 vs RAG 生产类），并断言：

   ① **两侧都真的跑过** ⇒ 各产出 1 条 `PROBE|` 行
      （PIT-GLOBAL「退出码 0 ≠ 脚本真跑过」⇒ 必须自证）
   ② **parity 键**（failed_fast · code · outgoing_carrier · sql_carrier）两侧**相等**
      ⇒ 它证明「SQL 路径两侧同款」＋「**出向载体在位**」＋「**出向调用继承请求 deadline**」
      ⇒ ⇒ 漂移**不在**这四键上

【为什么现在是等值守门（2026-10-30 探针改造后）】
  这个探针原先是**漂移形态守门**：`failed_fast` / `code` 被登记成「**已知的漂移形态**」
  ⇒ 断言「Go=true / RAG=false」「Go=4 / RAG=0」。
  ⇒ ⇒ **那两格漂移的根因是探针自己的构造**（RAG 半边手搓裸 channel ⇒ 不走 devh 装配
  ⇒ ⇒ 全局客户端拦截器接不上 ⇒ ⇒ 出向调用拿不到请求 deadline）
  ⇒ ⇒ **不是域侧真漂移** ⇒ ⇒ 探针改走 **devh 自己的 `GrpcChannelFactory`**（生产装配路径）后
  ⇒ ⇒ 实测两侧**逐键相等** ⇒ ⇒ 两个键**移出 drift 名单** ⇒ ⇒ 全部落进「必须相等」的分支。
  ⇒ ⇒ **判据更强**（不是把期望值改成相等来蒙混 ⇒ 是**移出名单**）。

【与既有门禁的关系】
  · `check-deadline-verdict.py`   ⇒ 守 E48（探针 harness 自身）
  · `mutate-deadline-precision.py` ⇒ 守 E62 的守门非空转
  · `mutate-deadline-downstream.py` ⇒ 守**本探针**的判据非空转
  · **本脚本** ⇒ CI 入口

用法：python3 tools/difftest/check-deadline-downstream.py
退出码：0 = 通过；1 = 不通过
════════════════════════════════════════════════════════════════════════════
"""

import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
PROBE = 'tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh'

# ── 已登记的漂移形态（与探针脚本里的 EXPECTED_DRIFT 同源）──────────────────
# ⚠️ **2026-10-30（探针改造后）= 空**：四个键**全部**是 parity 键（两侧必须相等）。
#    沿革：① `outgoing_carrier` 由 drift 键升为 parity 键（G4 波次把出向载体绑进
#    `RpcInterceptor.onHalfClose`）② `failed_fast` / `code` 也由 drift 键升为 parity 键
#    —— 那两格漂移的**根因是探针自己的构造**（手搓裸 channel ⇒ 不走 devh 装配），
#    探针改走 devh 的生产装配路径后实测两侧逐键相等。
#    ⚠️ **不要**把这里改成「登记一条两侧相等的 drift」来蒙混 ⇒ 那样仍被当 drift 键判。
EXPECTED_DRIFT = {}
# ── 必须两侧相等的键 ─────────────────────────────────────────────────────
PARITY_KEYS = ('sql_carrier', 'outgoing_carrier', 'failed_fast', 'code')


def parse_side(out, label):
    """从 runner 的「原始 PROBE 行」里抽出某一侧的键值。"""
    kv = {}
    pat = re.compile(r'^\s+' + label + r'\s*\|\s*PROBE\|[^|]+\|(.*)$')
    for line in out.splitlines():
        m = pat.match(line)
        if not m:
            continue
        for part in m.group(1).split('|'):
            if '=' in part:
                k, v = part.split('=', 1)
                kv[k] = v
    return kv


def main():
    print('== 跑双侧活体探针（Go 真源 ↔ RAG 生产类）==')
    proc = subprocess.run(['bash', PROBE], cwd=ROOT, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    if proc.returncode != 0:
        print('❌ 探针 EXIT=%d ⇒ 漂移形态与已登记不一致（或探针没跑起来）' % proc.returncode)
        print(out[-6000:])
        return 1

    go = parse_side(out, 'Go')
    rag = parse_side(out, 'RAG')
    if not go or not rag:
        print('❌ 没有解析到两侧的 PROBE 行 ⇒ 有一侧没真的跑')
        print(out[-6000:])
        return 1

    print('   Go  : %s' % go)
    print('   RAG : %s' % rag)
    print()

    fails = []

    # ① parity 键：两侧必须相等
    for k in PARITY_KEYS:
        gv, rv = go.get(k), rag.get(k)
        if gv is None or rv is None:
            fails.append('%s: 有一侧缺这个键（Go=%s RAG=%s）' % (k, gv, rv))
            print('  ❌ %-18s Go=%s | RAG=%s  （两侧必须都有）' % (k, gv, rv))
        elif gv != rv:
            fails.append('%s: 两侧不一致 Go=%s ≠ RAG=%s' % (k, gv, rv))
            print('  ❌ %-18s Go=%s | RAG=%s  （这一键两侧必须相等）' % (k, gv, rv))
        else:
            print('  ✅ %-18s Go=%s | RAG=%s  （两侧一致）' % (k, gv, rv))

    # ② drift 键：必须是已登记的形态
    for k, (eg, er) in EXPECTED_DRIFT.items():
        gv, rv = go.get(k), rag.get(k)
        if gv != eg or rv != er:
            fails.append('%s: 漂移形态变了 —— 期望 Go=%s RAG=%s，实测 Go=%s RAG=%s'
                         % (k, eg, er, gv, rv))
            print('  ❌ %-18s Go=%s | RAG=%s  （期望 Go=%s RAG=%s）'
                  % (k, gv, rv, eg, er))
        else:
            print('  ✅ %-18s Go=%s | RAG=%s  （已登记的漂移形态）' % (k, gv, rv))

    print()
    if fails:
        print('⇒ ❌ %d 条不符合预期：' % len(fails))
        for f in fails:
            print('   ' + f)
        return 1
    print('⇒ ✅ 通过（parity 键两侧一致；drift 键形态与已登记一致）')
    print('   台账出处：docs/DRIFT/global/_README.md · E38② 重估（第四十六轮）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
