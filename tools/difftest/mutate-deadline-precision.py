#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""════════════════════════════════════════════════════════════════════════════
 mutate-deadline-precision.py —— E62 守门的**变异测试**（证明守门不是假绿）
════════════════════════════════════════════════════════════════════════════

【为什么需要它】
  §3.3 的铁律：**「跑绿」不是「对」**。
  E62 的守门（`service/common` 的 3 条新单测 + `service/comment/comment-rpc` 的真 PG 守门）
  **断言全绿**只说明「当前代码满足这些断言」，**不能**说明「这些断言真的在守」。
  ⇒ 只有**故意破坏 ⇒ 必须精确红在目标断言上**，才能证明判据非空转。

  🔴 **本条特别重要**：E62 本身就是**「守门绿、生产坏」**的产物
  —— 旧守门用 `DriverManagerDataSource`（**没有池**）⇒ 拿到的是 pgjdbc 实现类
  ⇒ 反射探测成功 ⇒ **守门绿**；而生产走 HikariCP ⇒ 代理类 ⇒ 探测必失败 ⇒ **生产坏**。
  ⇒ 所以**「守门用的 DataSource 是不是真池」本身就是必须被守住的判据**
  ⇒ ⇒ **M2 专门变异这一条**：把守门换回无池 ⇒ 必须立刻红。

【口径（PIT-GLOBAL-048 / PIT-GLOBAL-040 / PIT-GLOBAL-021 同源）】
  · 基线 = 守门 EXIT=0 且 0 条失败；
  · 变异后 = **新增失败的用例集合 == 预期集合**（**不是**「包含某条关键词」
    ⇒ 少红 = 覆盖缺口，多红 = 判据串味）；
  · **只认 surefire XML**（PIT-GLOBAL-021 ⇒ maven 的退出码不作数）；
  · 跑前**删掉旧 XML**（PIT-GLOBAL-018 三道闸的第一道）；
  · 还原后 **sha256 一致 + 重新 touch**（PIT-GLOBAL-037）。

【变异清单（第四十五轮 · 3 条）】
  M1 生产代码：**不剥池代理**（`unwrapDriverStatement` 的结果被丢弃）
     ⇒ 红：service/common 2 条 + comment-rpc 2 条（真 PG 守门也必须红）
  M2 守门自证：把真 PG 守门的 DataSource **换回 `DriverManagerDataSource`（无池）**
     ⇒ **这正是 E62 当初骗过自己的形态** ⇒ 必须红「必须真的走池化代理路径」那条
  M3 生产代码：毫秒路径**偷偷按秒级取整**（值仍从毫秒路径送出，但精度退化成秒级）
     ⇒ 红：comment-rpc 的「驱动上的超时必须 ≤ 剩余毫秒」那条
     ⇒ ⇒ **证明这条守门不是「只要设了超时就算过」**

用法：python3 tools/difftest/mutate-deadline-precision.py
════════════════════════════════════════════════════════════════════════════
"""

import hashlib
import io
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))

INJECTOR = 'service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java'
GATE_RPC = 'service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentSqlDeadlinePrecisionGateTest.java'

# ── 两条守门命令 ─────────────────────────────────────────────────────────────
CMDS = [
    (['-pl', 'service/common'],
     'DeadlineQueryTimeoutInjectorTest',
     'service/common/target/surefire-reports'),
    (['-pl', 'service/comment/comment-rpc', '-am'],
     'CommentSqlDeadlinePrecisionGateTest',
     'service/comment/comment-rpc/target/surefire-reports'),
]

# ── 变异清单：(ID, 说明, 文件, 旧串, 新串, 期望失败的用例集合) ─────────────────
UNIT = 'DeadlineQueryTimeoutInjectorTest'
RPC = 'CommentSqlDeadlinePrecisionGateTest'

MUTATIONS = [
    ('M1 生产代码：不剥池代理（E62 的修法被撤掉）',
     INJECTOR,
     '        Statement target = unwrapDriverStatement(statement);',
     '        Statement target = statement;',
     {UNIT: {'pooledProxyIsUnwrappedBeforeMillisecondProbe',
             'unwrapChainTerminatesWhenDelegateReturnsItself'},
      RPC: {'pooledPathAppliesMillisecondTimeoutOnDriverStatement',
            'inFlightQueryIsCanceledAtDeadline'}}),

    ('M2 守门自证：把真 PG 守门换回无池（DriverManagerDataSource）—— E62 当初骗过自己的形态',
     GATE_RPC,
     '        dataSource = new HikariDataSource(hikari);',
     '        dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, PG.getUsername(), PG.getPassword());',
     {RPC: {'pooledPathAppliesMillisecondTimeoutOnDriverStatement'}}),

    ('M3 生产代码：毫秒路径偷偷按秒级取整（精度退化成秒级，但仍走毫秒方法）',
     INJECTOR,
     '            method.get().invoke(target, millis);',
     '            method.get().invoke(target, Math.ceil(millis / 1000.0) * 1000L);',
     {UNIT: {'millisecondRemainingIsPassedThroughVerbatim',
             'unwrapChainTerminatesWhenDelegateReturnsItself',
             'pooledProxyIsUnwrappedBeforeMillisecondProbe'},
      RPC: {'pooledPathAppliesMillisecondTimeoutOnDriverStatement',
            'inFlightQueryIsCanceledAtDeadline'}}),
]


def sha(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


def run_gates():
    """跑两条守门 ⇒ 返回 {测试类名: 失败用例集合}（**只认 surefire XML**）。"""
    failures = {}
    for extra, test_name, reports_dir in CMDS:
        # PIT-GLOBAL-018 第一道：跑前删掉旧 XML（避免历史残留污染判定）
        if os.path.isdir(reports_dir):
            for fn in os.listdir(reports_dir):
                if fn.endswith('.xml'):
                    os.unlink(os.path.join(reports_dir, fn))
        cmd = ['mvn', '-o', '-q'] + extra + [
            'test', '-Dtest=' + test_name,
            '-DfailIfNoTests=false', '-Dsurefire.failIfNoSpecifiedTests=false',
            '-Dmaven.test.failure.ignore=true']
        proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
        out = proc.stdout + proc.stderr
        if 'COMPILATION ERROR' in out:
            print('❌ 编译错误（变异打在不该打的地方？）\n' + out[-2000:])
            return None
        if not os.path.isdir(reports_dir):
            print('❌ 没有 surefire 报告目录：' + reports_dir)
            return None
        for fn in sorted(os.listdir(reports_dir)):
            if not fn.startswith('TEST-') or not fn.endswith('.xml'):
                continue
            try:
                root = ET.parse(os.path.join(reports_dir, fn)).getroot()
            except ET.ParseError as e:
                print('❌ surefire XML 解析失败：' + fn + ' ⇒ ' + str(e))
                return None
            cls = (root.get('name') or '').rsplit('.', 1)[-1]
            bad = set()
            for case in root.iter('testcase'):
                if case.find('failure') is not None or case.find('error') is not None:
                    bad.add(case.get('name'))
            failures[cls] = failures.get(cls, set()) | bad
    return failures


def main():
    print('── 基线：两条守门必须全绿 ──')
    base = run_gates()
    if base is None:
        return 1
    total = sum(len(v) for v in base.values())
    if total != 0:
        print('❌ 基线不干净（已有 %d 条失败）⇒ 先把基线修绿再谈变异' % total)
        for k, v in base.items():
            for name in sorted(v):
                print('   %s#%s' % (k, name))
        return 1
    print('✅ 基线：0 条失败\n')

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
            got = run_gates()
        finally:
            io.open(path, 'w', encoding='utf-8').write(original)
            # PIT-GLOBAL-037：还原后必须 touch（否则 maven 可能用旧产物、不重编译）
            os.utime(path, None)

        if got is None:
            print('❌ %s：变异后守门跑不起来' % mid)
            bad += 1
            continue

        after_sha = sha(path)
        if after_sha != before_sha:
            print('❌ %s：还原后 sha256 不一致 ⇒ 变异不可逆' % mid)
            bad += 1
            continue

        exp = {}
        for cls, names in expected.items():
            exp[cls] = names
        actual = {k: v for k, v in got.items() if v}
        if actual == exp:
            detail = ' · '.join('%s#%s' % (c.split('.')[-1], n)
                                for c in sorted(actual) for n in sorted(actual[c]))
            print('✅ %s ⇒ 被精确抓到（%s）' % (desc, detail))
        else:
            print('❌ %s：期望红 %s，实际红 %s' % (desc, exp, actual))
            bad += 1

    print()
    if bad:
        print('⇒ ❌ 变异测试失败（%d 条）' % bad)
        return 1
    print('⇒ ✅ 变异测试全部精确命中（E62 的两条守门非空转）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
