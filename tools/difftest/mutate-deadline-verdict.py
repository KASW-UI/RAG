#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""════════════════════════════════════════════════════════════════════════════
 mutate-deadline-verdict.py —— E48 守门的**变异测试**（证明守门不是假绿）
════════════════════════════════════════════════════════════════════════════

【为什么需要它】
  §3.3 的铁律：**「跑绿」不是「对」**。
  `check-deadline-verdict.py` 有 **30 条断言**（每条带稳定 ID）——
  但**断言全绿**只说明「当前代码满足这 30 条」，**不能**说明「这 30 条真的在守」。
  ⇒ 只有**故意破坏 ⇒ 必须精确红在目标断言上**，才能证明判据非空转。

【口径（PIT-GLOBAL-048 / PIT-GLOBAL-040 同源）】
  · 基线 = 守门 EXIT=0 且 0 条 ❌；
  · 变异后 = 守门 EXIT=1 且**新增红行集合 == 预期集合**
    （**不是**「包含某条关键词」⇒ 少红 = 覆盖缺口，多红 = 判据串味）；
  · 还原后 **sha256 一致 + 守门输出逐字节相同**（变异可逆）⇒ 并 `touch`（PIT-GLOBAL-037）。

【变异清单（第四十一轮 · 15 条）】
  M1 判定器 FAIL 分支失效 ⇒ E48-02
  M2 wrapper 不透传退出码 ⇒ E48-18
  M3 run.sh 删掉闸门调用 ⇒ E48-11~16
  M4 探针换回无条件 exit 0 ⇒ E48-07/08
  M5 wrapper 写 `$RC（`（bash 变量名陷阱）⇒ E48-17/19/22
  M6 探针端口不再跟随偏移 ⇒ E48-23
  M7 探针去掉「栈未就绪」fail-fast ⇒ E48-24
  M8 探针锁命令写死容器名（**第四十一轮真根因**）⇒ E48-25
  M9 探针自证去掉 `granted` 判据 ⇒ E48-26
  M10 探针锁分支不 fail-fast ⇒ E48-27
  M11 探针某处 `one_side` 不查返回值 ⇒ E48-27
  M12 探针阴性观测改回断言式 ⇒ E48-28
  M13 判定器文案改回断言式 ⇒ E48-29
  M14 run.sh 容器口径不豁免「自己的」占用 ⇒ E48-30
  M15 run.sh 进程口径不豁免「自己的」占用 ⇒ E48-30

【一条变异侧的纪律（第四十一轮当场踩到）】
  **变异必须打在「代码」上，不能打在「注释」上** ——
  探针头部注释里写了 `#     exit 3 = 栈未就绪…`，它排在代码 `    exit 3` **之前**
  ⇒ 变异打在注释上 ⇒ 文件的**可执行语义没变** ⇒ 守门当然还是绿
  ⇒ 那**不是**「守门假绿」，是「变异没变异」⇒ 本脚本**自检「锚点所在行不是注释行」**，
  命中就直接报错，不混过去（见 `MUTATIONS` 循环里的那一段）。

用法：python3 tools/difftest/mutate-deadline-verdict.py
════════════════════════════════════════════════════════════════════════════
"""

import hashlib
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
GATE = 'tools/difftest/check-deadline-verdict.py'

PROBE = 'tools/difftest/exp-grpc-admin-deadline-probe.sh'
VERDICT = 'tools/difftest/verdict-grpc-admin-deadline.py'
GATE_SH = 'tools/difftest/run-deadline-gate.sh'
RUNSH = 'tools/difftest/run.sh'

MUTATIONS = [
    ('M1 判定器：FAIL 分支永不触发',
     VERDICT,
     'if short_diff > threshold_ms:', 'if False:',
     # ⚠️ **第四十五轮起多了 E48-35**：它断言「833ms ⇒ FAIL」⇒ 依赖的正是这条 FAIL 分支
     #    ⇒ FAIL 分支一坏 ⇒ 它也该红 ⇒ **加强不是退化**
     {'E48-02', 'E48-35'}),
    ('M2 wrapper：退出码不透传',
     GATE_SH,
     'exit $RC', 'exit 0',
     # ⚠️ **第四十四轮起多了 E48-32**：`exit 0` ⇒ 「无 Δ 证据的假探针」也能过闸门
     #    ⇒ 第三步判据把这条**原判据覆盖不到的漏洞**补上了 ⇒ 预期红行集合随之变
     {'E48-18', 'E48-32'}),
    ('M3 run.sh：闸门调用被删',
     RUNSH,
     '"$HERE/run-deadline-gate.sh"', 'true',
     {'E48-11', 'E48-12', 'E48-13', 'E48-14', 'E48-15', 'E48-16'}),
    ('M4 探针：判定被换回无条件 exit 0',
     PROBE,
     'exit $VERDICT_RC', 'exit 0',
     {'E48-07', 'E48-08'}),
    ('M6 探针：端口不再跟随偏移',
     PROBE,
     'GO_ADMIN_PORT="${GO_ADMIN_PORT:-$((18081 + PORT_OFFSET))}"',
     'GO_ADMIN_PORT="${GO_ADMIN_PORT:-18081}"',
     {'E48-23'}),
    ('M7 探针：去掉「栈未就绪」fail-fast',
     PROBE,
     '    exit 3\n  fi', '    :\n  fi',
     {'E48-24'}),
    ('M5 wrapper：$RC 后紧跟全角括号（bash 变量名陷阱）',
     GATE_SH,
     '${RC}（0=PASS', '$RC（0=PASS',
     # ⚠️ 同样多了 E48-33：wrapper 被这个陷阱打断 ⇒ 「有 Δ 证据」那条也不再放行
     {'E48-17', 'E48-19', 'E48-22', 'E48-33'}),

    # ── 第四十一轮新增 ────────────────────────────────────────────────
    ('M8 探针：锁命令写死容器名（第四十一轮真根因）',
     PROBE,
     'docker exec -i "$PG_CONTAINER" psql -U "$PG_U" -d "$db" -qAt',
     'docker exec -i difftest-postgres psql -U "$PG_U" -d "$db" -qAt',
     {'E48-25'}),
    ('M9 探针：自证去掉 granted 判据（退化成数等锁会话）',
     PROBE,
     "AND l.mode='AccessExclusiveLock' AND l.granted",
     "AND l.mode='AccessExclusiveLock'",
     {'E48-26'}),
    ('M10 探针：锁未生效不 fail-fast（锁分支的 return 4 → return 0）',
     PROBE,
     'LOCK_PIDS="${LOCK_PIDS/$lock_pid/}"\n    return 4',
     'LOCK_PIDS="${LOCK_PIDS/$lock_pid/}"\n    return 0',
     {'E48-27'}),
    ('M11 探针：某处 one_side 不检查返回值',
     PROBE,
     'one_side rag rag_db admin_admin "$DEADLINE_SHORT" || exit $?',
     'one_side rag rag_db admin_admin "$DEADLINE_SHORT"',
     {'E48-27'}),
    ('M12 探针：阴性观测改回断言式措辞（把误诊请回来）',
     PROBE,
     '    echo "       ⇒ 两种可能：① 查询压根没发到这张表（查了别的表）② 查询到了但没被取消（超时没生效）"',
     '    echo "       （不是「查询没被取消」，是「查询压根没到 PG」）"',
     {'E48-28'}),
    ('M13 判定器：INCONCLUSIVE 文案改回断言式',
     VERDICT,
     '"       ① 查询压根没到 PG  ② 查询到了但没被取消（超时没生效）",',
     '"       ⇒ 查询压根没到 PG",',
     {'E48-29'}),
    ('M14 run.sh：容器口径不再豁免「自己的」占用',
     RUNSH,
     'compose project=${COMPOSE_PROJECT}）⇒ 复用，不算冲突" >&2\n        continue',
     'compose project=${COMPOSE_PROJECT}）⇒ 复用，不算冲突" >&2\n        busy=1',
     {'E48-30'}),
    ('M15 run.sh：进程口径不再豁免「自己的」占用',
     RUNSH,
     '栈进程占用（${cmd}）⇒ 复用，不算冲突" >&2\n        continue',
     '栈进程占用（${cmd}）⇒ 复用，不算冲突" >&2\n        busy=1',
     {'E48-30'}),

    # ── 第四十四轮新增（第三步判据）──────────────────────────────────
    ('M16 闸门：第三步判据永不生效（探针退 0 也不查证据）',
     GATE_SH,
     'if [[ $RC -eq 0 ]]; then',
     'if false; then',
     # ⚠️ 多了 E48-33：证据块不跑 ⇒ 「交出了观测证据」的标记也不会打印
     {'E48-31', 'E48-32', 'E48-33'}),
    # ── 第四十五轮新增（阈值重校准）──────────────────────────────────
    ('M18 判定器：默认阈值退回 1000（大于实测 E62 回归信号 833ms ⇒ 探针形同虚设）',
     VERDICT,
     'default=150.0', 'default=1000.0',
     # ⚠️ **也多了 E48-36**：判定器默认改了 ⇒ 与探针默认值**不再一致** ⇒ E48-36 同样该红
     #    ⇒ 这是「加强不是退化」（同第四十四轮 M2/M5 的形态）
     {'E48-35', 'E48-36'}),
    ('M20 探针：起栈前不再清掉上一轮遗留的栈进程（就绪判定会被旧栈满足）',
     PROBE,
     '  STALE_PIDS="$(pgrep -f "$RUN_DIR/bin/" 2>/dev/null || true)"',
     '  STALE_PIDS=""',
     {'E48-37'}),
    # ⚠️ **【2026-10-31 订正 · 合并收口执行者】锚点已重定基**：原锚点
    #   `DEADLINE_VERDICT_THRESHOLD_MS:-150` 在探针里**命中 2 处**（一行是 echo 的
    #   「阈值 = … ms」提示、一行是真正生效的 `--threshold-ms` 实参）
    #   ⇒ ⇒ 旧锚点**不满足变异脚本自身的前置条件**（`count(old)==1`）
    #   ⇒ ⇒ 该条变异**跑不起来** ⇒ 「变异转红」这个证据拿不到
    #   ⇒ ⇒ 改成**带上下文**的那一行（只命中 1 处 ⇒ 且**更精确**：只改实参、不碰 echo）
    ('M19 探针：默认阈值退回 1000（探针显式传阈值 ⇒ 真正生效的是它）',
     PROBE,
     '  --threshold-ms   "${DEADLINE_VERDICT_THRESHOLD_MS:-150}"',
     '  --threshold-ms   "${DEADLINE_VERDICT_THRESHOLD_MS:-1000}"',
     {'E48-36'}),

    ('M17 闸门：Δ 证据的匹配串写错（永远匹配不到）',
     GATE_SH,
     "grep -c 'Δ_end=' \"$GATE_OUT\"",
     "grep -c 'Δ_endXX=' \"$GATE_OUT\"",
     {'E48-31', 'E48-33'}),
]


def sha(path):
    return hashlib.sha256(open(path, 'rb').read()).hexdigest()


def run_gate():
    p = subprocess.run([sys.executable, GATE], capture_output=True, cwd=ROOT)
    out = p.stdout.decode('utf-8', errors='replace')
    reds = set()
    for line in out.split('\n'):
        m = re.search(r'❌ \[(E48-\d+)\]', line)
        if m:
            reds.add(m.group(1))
    return p.returncode, reds, out


def main():
    os.chdir(ROOT)
    rc, reds, out = run_gate()
    if rc != 0 or reds:
        print('❌ 基线不干净 ⇒ 变异测试无意义')
        print(out)
        return 1
    print(f'✅ 基线：守门 EXIT=0，0 条红')

    bad = 0
    for label, rel, old, new, expect in MUTATIONS:
        path = os.path.join(ROOT, rel)
        orig = io.open(path, encoding='utf-8').read()
        backup = tempfile.mktemp(suffix='.bak')
        shutil.copyfile(path, backup)
        try:
            assert old in orig, f'{rel} 里找不到待变异串：{old!r}'
            # 🔴 **变异必须打在「代码」上，不能打在「注释」上**（第四十一轮实测踩到）：
            #    探针头部注释 `#     exit 3 = 栈未就绪…` 排在代码 `    exit 3` **之前**
            #    ⇒ 变异打在注释上 ⇒ 文件的**可执行语义没变** ⇒ 守门当然还是绿
            #    ⇒ 这**不是**「守门假绿」，是「变异没变异」⇒ 必须当场报错，不能混过去。
            # ⚠️ 判定必须看**锚点起始位置所在行**（多行锚点时不能只看「子串首次出现的行」）。
            idx = orig.find(old)
            line_start = orig.rfind('\n', 0, idx) + 1
            line_end = orig.find('\n', idx)
            hit_line = orig[line_start:line_end if line_end != -1 else len(orig)]
            if hit_line.lstrip().startswith('#'):
                print(f'  ❌ 变异「{label}」打在**注释行**上（{hit_line.strip()[:70]}）⇒ 等于没变异')
                bad += 1
                continue
            io.open(path, 'w', encoding='utf-8').write(orig.replace(old, new, 1))
            mrc, mreds, mout = run_gate()
            if mrc == 0:
                print(f'  ❌ 变异「{label}」**没被抓到** ⇒ 这一条判据是假绿')
                bad += 1
                continue
            got = mreds - reds
            if got == expect:
                print(f'  ✅ 变异「{label}」⇒ 被精确抓到（新增红行 {len(got)} 条，与预期一致）')
            else:
                print(f'  ❌ 变异「{label}」红在**非预期**的断言上')
                print(f'      预期：{sorted(expect)}')
                print(f'      实际：{sorted(got)}')
                bad += 1
        finally:
            shutil.copyfile(backup, path)
            os.utime(path, None)          # PIT-GLOBAL-037：还原后必须 touch
            os.unlink(backup)
        # 还原校验：sha256 一致 + 守门输出逐字节相同
        if sha(path) != hashlib.sha256(orig.encode('utf-8')).hexdigest():
            print(f'  ❌ 变异「{label}」还原后 sha256 不一致 ⇒ 变异没还原干净')
            bad += 1
            continue
        rrc, rreds, rout = run_gate()
        if rrc != 0 or rreds or rout != out:
            print(f'  ❌ 变异「{label}」还原后守门输出与基线不一致 ⇒ 变异可逆性被破坏')
            bad += 1

    print()
    print(f'⇒ 共 {len(MUTATIONS)} 条变异')
    print('✅ 变异测试全部精确命中' if not bad else f'❌ 变异测试有 {bad} 处没命中')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
