#!/usr/bin/env python3
"""🔴 **E48 守门**：客户端 deadline 档探针的**判定**与**接线**都必须真的有效

═══ 这道门禁守的是什么 ═══

`exp-grpc-admin-deadline-probe.sh` 的头部写着「exit 1 ⟺ 两侧差异 > 1s」，
但**第三十九轮之前**那段判定**从来没被实现**（脚本尾部是无条件 `exit 0`）
⇒ ⇒ **这个探针永远不会失败** ⇒ ⇒ 接进 `run.sh` 也只是「假闸门」。

⇒ 本门禁把四件事钉死（每条断言都有**稳定 ID** ⇒ 变异测试能精确比对红行集合）：
1. 🟢 **判定器**（`verdict-grpc-admin-deadline.py`）在 **fixture** 上给出正确的退出码
   （PASS / FAIL / INCONCLUSIVE 三条路都对）⇒ **不需要起栈 ⇒ CI 可跑**；
2. 🟢 **接线**：探针**必须**调用判定器并**透传**它的退出码（不得再有无条件 `exit 0`）；
3. 🟢 **编排**：`run.sh` 必须在 `diff.py` **之后**调闸门，且**必须透传**端口/目录/容器名前缀；
4. 🟢 **假探针 + bash lint**：证明「探针红了 ⇒ 入口红了 ⇒ run.sh 会红」，
   并拦住「`$VAR` 紧跟全角标点 ⇒ bash 并进变量名 ⇒ 脚本提前退出 ⇒ 门禁假绿」。

用法：python3 tools/difftest/check-deadline-verdict.py
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
VERDICT = os.path.join(HERE, "verdict-grpc-admin-deadline.py")
GATE = os.path.join(HERE, "run-deadline-gate.sh")
PROBE = os.path.join(HERE, "exp-grpc-admin-deadline-probe.sh")
RUNSH = os.path.join(HERE, "run.sh")

bad = 0


def ok(cid: str, msg: str) -> None:
    print(f"  ✅ [{cid}] {msg}")


def ng(cid: str, msg: str) -> None:
    global bad
    print(f"  ❌ [{cid}] {msg}")
    bad += 1


def run_verdict(args: list[str]) -> int:
    p = subprocess.run([sys.executable, VERDICT] + args, capture_output=True)
    return p.returncode


# ── ① 判定器 · fixture ────────────────────────────────────────────────
print("① 判定器在 fixture 上的退出码（PASS / FAIL / INCONCLUSIVE）")

rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "12",
                  "--go-long-dend", "-5", "--rag-long-dend", "3"])
ok("E48-01", "两侧一致 ⇒ PASS（退出码 0）") if rc == 0 else ng(
    "E48-01", f"两侧一致却退出 {rc}（应 0）")

rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "1700",
                  "--go-long-dend", "0", "--rag-long-dend", "0",
                  "--threshold-ms", "1000"])
ok("E48-02", "短档复现差异 ⇒ FAIL（退出码 1）") if rc == 1 else ng(
    "E48-02", f"短档复现差异却退出 {rc}（应 1）⇒ **假绿**")

# ⚠️ 这里**故意钉死**当前口径（严格大于）：差值正好等于阈值 ⇒ 不算差异 ⇒ PASS
rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "1000",
                  "--go-long-dend", "0", "--rag-long-dend", "0",
                  "--threshold-ms", "1000"])
ok("E48-03", "差值正好等于阈值 ⇒ PASS（当前口径是「严格大于」）") if rc == 0 else ng(
    "E48-03", f"等于阈值的口径变了（退出 {rc}）⇒ 判定语义被改，须同步改台账")

rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "10",
                  "--go-long-dend", "0", "--rag-long-dend", "1800",
                  "--threshold-ms", "1000"])
ok("E48-04", "对照组也不一致 ⇒ INCONCLUSIVE（退出码 2）") if rc == 2 else ng(
    "E48-04", f"对照组不一致却退出 {rc}（应 2）")

rc = run_verdict(["--go-short-dend", "", "--rag-short-dend", "10",
                  "--go-long-dend", "0", "--rag-long-dend", "0",
                  "--threshold-ms", "1000"])
ok("E48-05", "观测缺失 ⇒ INCONCLUSIVE（退出码 2）") if rc == 2 else ng(
    "E48-05", f"观测缺失却退出 {rc}（应 2）⇒ **假绿**")

# ── ①b 默认阈值的校准守卫（E48-34 · 第四十五轮）──────────────────────────
# 🔴 **阈值不是「判 PASS/FAIL」的，是「判『有没有回归』的」**：
#   旧默认 1000ms **大于** E62 回归信号（第四十一轮实测差 833ms）⇒ 833 < 1000
#   ⇒ ⇒ **E62 全坏了，探针照样判 PASS** ⇒ 阈值形同虚设。
# ⇒ 第四十五轮把默认按**实测值**重校准为 150ms ⇒ 并用**实测值**把它钉住：
#   · 噪声上界（第四十五轮实测短档两侧差 22ms）⇒ 必须判 PASS
#   · E62 回归信号（第四十一轮实测差 833ms）      ⇒ 必须判 FAIL
print("①b 默认阈值必须落在「实测噪声」与「实测 E62 回归信号」之间")
rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "22",
                  "--go-long-dend", "-9", "--rag-long-dend", "-8"])
ok("E48-34", "默认阈值：实测噪声 22ms ⇒ PASS（退出码 0）") if rc == 0 else ng(
    "E48-34", f"默认阈值把实测噪声 22ms 判成差异（退出 {rc}）⇒ 阈值过紧 ⇒ 探针会误报")

rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "833",
                  "--go-long-dend", "-9", "--rag-long-dend", "-8"])
ok("E48-35", "默认阈值：实测 E62 回归信号 833ms ⇒ FAIL（退出码 1）⇒ 探针抓得住 E62 回归") if rc == 1 else ng(
    "E48-35", f"默认阈值把实测 E62 回归信号 833ms 判成 PASS（退出 {rc}）⇒ **探针抓不住 E62 回归** ⇒ 阈值形同虚设")

# ── ② 探针接线 ────────────────────────────────────────────────────────
print("② 探针必须调用判定器并透传退出码（不得再有无条件 exit 0）")
probe_src = open(PROBE, encoding="utf-8").read()

# ⚠️ **E48-36：探针侧传的阈值也必须落在同一区间** ——
#   只守判定器的默认值是不够的：探针**显式**把阈值传给判定器
#   ⇒ 探针的默认值才是真正生效的那个 ⇒ 它松了，E48-35 照样绿。

# ⚠️ **E48-37：起栈前必须先清掉「上一轮遗留的栈进程」**（第四十五轮实测带出）
#   第四十五轮第二次跑探针时 exit 4（前提未成立）⇒ 症状是加锁时 PG 后端被换掉：
#     ① 探针的 cleanup 只杀 run.sh ⇒ 而 run.sh 带 --keep 时它的 trap **不收摊** ⇒ 栈永远残留；
#     ② 探针的「就绪」判据是**端口在监听** ⇒ **残留的旧栈也能满足**；
#     ③ 而 run.sh 第一步是 down -v + up -d（删卷重建 PG）⇒ 实验在环境重建中途开跑。
#   ⇒ 修法 = 起栈前按 **$RUN_DIR/bin/** 精确清掉残留栈 ⇒ 且**必须在起栈之前**（顺序也要钉）。
print("②b 探针起栈前必须清掉上一轮遗留的栈进程（否则就绪判定会被旧栈满足）")
_stale_pos = probe_src.find('pgrep -f "$RUN_DIR/bin/"')
_runsh_pos = probe_src.find('"$HERE/run.sh"')
if _stale_pos > 0 and _runsh_pos > 0 and _stale_pos < _runsh_pos:
    ok("E48-37", "探针在起栈**之前**清掉了上一轮遗留的栈进程（就绪判定不会再被旧栈满足）")
else:
    ng("E48-37", f"探针没有在起栈前清掉遗留栈进程（清理位置 {_stale_pos} / 起栈位置 {_runsh_pos}）"
                 f"⇒ 就绪判定会被旧栈满足 ⇒ 实验可能在环境重建中途开跑")
print("①c 探针传给判定器的阈值必须与判定器默认值一致，且同样落在实测校准区间")
_verdict_src = open(VERDICT, encoding="utf-8").read()
_m = re.search(r'--threshold-ms"[^\n]*?default=([0-9.]+)', _verdict_src)
_v_default = float(_m.group(1)) if _m else -1.0
_m2 = re.search(r'DEADLINE_VERDICT_THRESHOLD_MS:-([0-9]+)', probe_src)
_p_default = int(_m2.group(1)) if _m2 else -1
if 25 < _v_default < 833 and 25 < _p_default < 833 and _v_default == _p_default:
    ok("E48-36", f"探针与判定器的默认阈值一致且在实测校准区间内（均为 {_v_default:.0f}ms）")
else:
    ng("E48-36", f"阈值不一致或越界：判定器默认 {_v_default:.0f}ms · 探针默认 {_p_default}ms"
                 f"（须都在 (25, 833) 内且相等 ⇒ 否则探针抓不住 E62 回归）")
if "verdict-grpc-admin-deadline.py" in probe_src:
    ok("E48-06", "探针调用了判定器")
else:
    ng("E48-06", "探针**没有**调用判定器 ⇒ 判定仍是注释里的承诺")

if re.search(r"^\s*exit\s+0\s*$", probe_src, re.M):
    ng("E48-07", "探针里仍有**无条件 exit 0** ⇒ 探针永远不会红 ⇒ 假闸门")
else:
    ok("E48-07", "探针没有无条件 exit 0")

if re.search(r"exit\s+\$?\{?VERDICT_RC", probe_src):
    ok("E48-08", "探针透传了判定器的退出码")
else:
    ng("E48-08", "探针**没有**透传判定器退出码 ⇒ 探针红了也传不出去")

if 'PG_CONTAINER="${DIFTEST_CONTAINER_PREFIX:-difftest}-postgres"' in probe_src:
    ok("E48-09", "探针的 PG 容器名跟随 DIFTEST_CONTAINER_PREFIX（并行安全）")
else:
    ng("E48-09", "探针的容器名仍写死 ⇒ 并发实例会锁到别人的库")

if 'RUN_DIR="${RUN_DIR:-/tmp/difftest}"' in probe_src:
    ok("E48-10", "探针的 RUN_DIR 可覆盖（并行安全）")
else:
    ng("E48-10", "探针的 RUN_DIR 仍写死 ⇒ 并发实例会互踩日志/构建产物")

if 'PORT_OFFSET="${DIFTEST_PORT_OFFSET:-0}"' in probe_src and \
        'GO_ADMIN_PORT="${GO_ADMIN_PORT:-$((18081 + PORT_OFFSET))}"' in probe_src:
    ok("E48-23", "探针的端口**跟随 `DIFTEST_PORT_OFFSET`**（与 run.sh 同一套端口 ⇒ 不会连到别人的栈）")
else:
    ng("E48-23", "探针的端口**不跟随偏移** ⇒ 并发时探针会连到别人的栈 ⇒ 实验结论作废")

# ⚠️ **判据必须锚在「代码结构」上，不能锚在整个文件的字面量上**（PIT-GLOBAL-031）：
#    第四十一轮在探针头部补了「exit 3 = 栈未就绪」的说明 ⇒ 整文件搜 `exit 3` 仍命中
#    ⇒ E48-24 **变成恒真**（变异 M7 抓不到）⇒ 改为「READY 判定块里必须真的有 exit 3」。
if re.search(r"READY\s*-ne\s*1[\s\S]{0,400}?exit 3", probe_src):
    ok("E48-24", "探针在**栈未就绪时 fail-fast**（不再无条件打印「✓ 就绪」）")
else:
    ng("E48-24", "探针在栈未就绪时**不 fail-fast** ⇒ 会带着无效观测往下跑 ⇒ 假绿")

# ── ②b 仪器的「施加侧」必须与「观测侧」同容器，且前提必须自证 ─────────
# 🔴 **第四十一轮实测踩到**：探针的**施加侧**（加锁）写死了容器名 `difftest-postgres`，
#    而**观测侧**（采样 PG 日志）用的是 `$PG_CONTAINER` ⇒ 带前缀跑时两者指向**不同容器**
#    ⇒ 施加侧那个容器**根本不存在** ⇒ `docker exec` 立即失败（stderr 被丢进 /dev/null）
#    ⇒ **锁从未生效** ⇒ 查询没被阻塞 ⇒ **自然没有取消事件**
#    ⇒ 两侧都报「无取消事件」⇒ 判定器给 INCONCLUSIVE（不假绿，但**结论是误诊**）。
#    ⚠️ 探针头部**早就写对了这条警告** ⇒ 「注释里的警告 ≠ 代码里的约束」。
print("②b 施加侧（加锁）必须与观测侧同容器，且「锁生效」必须自证")
lock_cmds = re.findall(r"docker exec -i\s+(.+?)\s+psql", probe_src)
if lock_cmds and all("$PG_CONTAINER" in c for c in lock_cmds):
    ok("E48-25", f"锁命令的容器名跟随 $PG_CONTAINER（{len(lock_cmds)} 处）⇒ 施加侧与观测侧同容器")
else:
    ng("E48-25", f"锁命令的容器名**不是** $PG_CONTAINER（发现 {[c for c in lock_cmds if '$PG_CONTAINER' not in c]}）"
                 "⇒ 施加侧与观测侧指向不同容器 ⇒ 锁从未生效 ⇒ 观测全无效")

# 自证必须看**持有者**视角：pg_locks 里该表上 granted 的 AccessExclusiveLock。
# ⚠️ 不能用 pg_lock_count（它数的是**等锁会话**）⇒ 「没有等锁者」无法区分
#    「锁生效但查询还没来」与「锁压根没生效」。
has_granted_query = re.search(
    r"mode\s*=\s*'AccessExclusiveLock'\s+AND\s+l\.granted", probe_src) is not None
call_sites = re.findall(r"pg_lock_granted\s+\"", probe_src)
if has_granted_query and call_sites:
    ok("E48-26", f"探针对「锁生效」有**自证**（pg_locks 的 granted AccessExclusiveLock，调用点 {len(call_sites)} 处）")
else:
    ng("E48-26", "探针**没有**对「锁生效」自证（查 pg_locks 的 granted AccessExclusiveLock 并调用它）"
                 "⇒ 仪器坏了也没人知道 ⇒ 结论是误诊")

# 前提未成立 ⇒ **必须 fail-fast**：不能带着无效观测继续跑完四档再报 INCONCLUSIVE。
# ⚠️ 计数时必须**排除函数定义行**（`one_side() {`）⇒ 否则 4 次调用会被数成 5。
# ⚠️ **不能只搜「return 4」这个字面量**（第四十一轮实测踩到）：补完「表不存在」分支后，
#    文件里**有两处** `return 4` ⇒ 变异打在第一处时字面量仍在 ⇒ **判据恒真 ⇒ 假绿**
#    ⇒ 必须**锚到分支**：锁分支（granted 判定）与表分支各一处，且都必须是 4。
one_side_calls = re.findall(r"^one_side\s+\S+\s+\S+\s+\S+\s+\S+", probe_src, re.M)
guarded = [c for c in re.findall(r"^one_side[^\n]+", probe_src, re.M) if "|| exit $?" in c]
lock_fail = re.search(r"granted:-0.*?-lt 1.*?then(.*?)\n  fi", probe_src, re.S)
table_fail = re.search(r"无法加锁（实验前提未成立）\"; return (\d+)", probe_src)
precond_ok = (bool(lock_fail and "return 4" in lock_fail.group(1))
              and bool(table_fail and table_fail.group(1) == "4"))
if precond_ok and len(guarded) == len(one_side_calls) and len(one_side_calls) >= 4:
    ok("E48-27", f"前提未成立 ⇒ fail-fast（锁分支/表分支都 return 4；"
                 f"{len(guarded)}/{len(one_side_calls)} 处 one_side 都检查了返回值）")
else:
    ng("E48-27", f"前提未成立**没有** fail-fast（锁分支={bool(lock_fail and 'return 4' in lock_fail.group(1))} "
                 f"表分支={table_fail.group(1) if table_fail else None}；"
                 f"one_side 检查返回值 {len(guarded)}/{len(one_side_calls)}）⇒ 会带着无效观测跑完 ⇒ 结论误诊")

# 阴性观测的措辞：**「没观测到」有两种成因**（查询没到 PG / 查询到了但没被取消），
#    前提未成立时第三种（仪器坏了）⇒ **断言**其中一种就是误诊。
# ⚠️ **禁的是「断言式措辞」，不是「列举可能成因」** —— 正确写法是「① … ② …」这种列举，
#    它仍然含有「查询压根没到 PG」这几个字 ⇒ 判据必须看**有没有断言**（是/⇒/就是 + 该短语）。
# ⚠️ E48-28 只管**探针**、E48-29 只管**判定器** ⇒ 一条断言一个职责 ⇒ 变异能 1:1 归因。
MISDIAGNOSIS = r"(?:是|就是|⇒)\s*[「\"]?\s*查询(?:压根)?没到\s*PG"
old_misdiagnosis = re.findall(MISDIAGNOSIS, probe_src)
verdict_src = open(VERDICT, encoding="utf-8").read()
verdict_misdiagnosis = re.findall(MISDIAGNOSIS, verdict_src)
if not old_misdiagnosis and "前提" in probe_src:
    ok("E48-28", "探针的阴性观测措辞**区分了成因**（不再断言「查询压根没到 PG」⇒ 那是前提未成立时的误诊）")
else:
    ng("E48-28", f"探针的阴性观测仍在**断言**成因（{len(old_misdiagnosis)} 处）"
                 "⇒ 前提未成立时是**误诊** ⇒ 结论会把「仪器坏了」说成「查询没到 PG」")

if not verdict_misdiagnosis and "两种" in verdict_src and "没被取消" in verdict_src:
    ok("E48-29", "判定器的 INCONCLUSIVE 文案**不越权下结论**（它只看观测值 ⇒ 只能区分『没观测到』）")
else:
    ng("E48-29", "判定器仍在断言成因（它只看观测值 ⇒ 无权区分『查询没到 PG』与『没被取消』）")

# ── ③ 编排接线（静态）────────────────────────────────────────────────
print("③ run.sh 必须在 diff.py 之后调闸门，并透传端口/目录/容器名前缀")
run_src = open(RUNSH, encoding="utf-8").read()
i_diff = run_src.find('python3 "$HERE/diff.py"')
i_gate = run_src.find("run-deadline-gate.sh")
env_ids = [("GO_ADMIN_PORT=", "E48-13"), ("RAG_ADMIN_PORT=", "E48-14"),
           ("RUN_DIR=", "E48-15"), ("DIFTEST_CONTAINER_PREFIX=", "E48-16")]
# ⚠️ **三条分支都必须把 6 条断言全跑一遍**（变异 M3 实测踩到）：
#    若「闸门缺失」时直接短路 ⇒ 顺序与 4 条 env 断言**根本没跑** ⇒ 它们的判据
#    在「闸门被删」这个变异下**不产生任何红行** ⇒ **变异只红 1 条 ⇒ 覆盖缺口**。
if i_diff < 0:
    ng("E48-11", "run.sh 里找不到 diff.py 调用（脚本被改坏了？）")
    ng("E48-12", "无法判定闸门顺序（找不到 diff.py 调用）")
    for var, cid in env_ids:
        ng(cid, f"run.sh 没向闸门透传 {var.rstrip('=')} ⇒ 并发实例会串")
elif i_gate < 0:
    ng("E48-11", "run.sh **没有**调用 deadline 闸门 ⇒ 探针仍是「手工取证」形态")
    ng("E48-12", "闸门不在 run.sh 里 ⇒ 顺序无法满足")
    for var, cid in env_ids:
        ng(cid, f"run.sh 没向闸门透传 {var.rstrip('=')} ⇒ 并发实例会串")
else:
    ok("E48-11", "run.sh 调用了 deadline 闸门")
    if i_gate > i_diff:
        ok("E48-12", "闸门排在 diff.py **之后**（表锁不会挡住对拍）")
    else:
        ng("E48-12", "闸门排在 diff.py **之前** ⇒ 表锁会挡住对拍 ⇒ 实验互踩")
    for var, cid in env_ids:
        if var in run_src[max(0, i_gate - 400):i_gate + 200]:
            ok(cid, f"run.sh 向闸门透传了 {var.rstrip('=')}")
        else:
            ng(cid, f"run.sh 没向闸门透传 {var.rstrip('=')} ⇒ 并发实例会串")

# 🔴 **端口预检必须豁免「自己的」占用**（第四十一轮实测踩到 ⇒ 鸡生蛋 ⇒ 直接阻断真跑）：
#   `run.sh --keep` 留下的**同一个 compose project** 的容器仍持有 PG/Redis 端口
#   ⇒ 旧写法把它判成「别人先占了」⇒ 预检失败 ⇒ **同一个前缀永远跑不了第二次**
#   ⇒ 后果不是「报错」而是「**编排无法重复执行**」⇒ E48 闸门在 CI 上跑第二轮就挂。
#   ⚠️ 豁免必须**窄**：只豁免「占用者属于我们」（同 compose project 的容器 /
#      本 harness 的栈进程）⇒ 别人踩我们时照样拦。
own_branch = re.search(
    r'if \[\[ -n "\$own_container_ports".*?then(.*?)\n *fi', run_src, re.S)
proc_branch = re.search(
    r'if \[\[ "\$cmd" == \*"\$RUN_DIR/"\* \]\]; then(.*?)\n *fi', run_src, re.S)
own_ok = bool(own_branch and "continue" in own_branch.group(1)
              and "com.docker.compose.project=$COMPOSE_PROJECT" in run_src)
proc_ok = bool(proc_branch and "continue" in proc_branch.group(1))
if own_ok and proc_ok:
    ok("E48-30", "run.sh 的端口预检**豁免自己的占用**（同 compose project 的容器 + 本 harness 的栈进程）")
else:
    ng("E48-30", "run.sh 的端口预检**不豁免自己的占用**"
                 f"（容器口径={own_ok} 进程口径={proc_ok}）"
                 "⇒ `--keep` 之后同前缀永远跑不了第二次 ⇒ 编排无法重复执行")

# ── ④ 接线有效性（假探针变异）─────────────────────────────────────────
print("④ 假探针：探针红 ⇒ 入口红（不需要起栈）")
with tempfile.TemporaryDirectory() as td:
    stub = os.path.join(td, "stub-probe.sh")
    with open(stub, "w", encoding="utf-8") as f:
        f.write("#!/usr/bin/env bash\n"
                "echo '  (假探针) 模拟判定失败'\n"
                "exit 1\n")
    os.chmod(stub, 0o755)
    env = dict(os.environ)
    env["EXP_PROBE_STUB"] = stub
    env["GO_ADMIN_PORT"] = "18081"
    env["RAG_ADMIN_PORT"] = "58081"
    env["RUN_DIR"] = td
    env["DIFTEST_CONTAINER_PREFIX"] = "difftest"
    p = subprocess.run(["bash", GATE], capture_output=True, env=env, cwd=ROOT)
    out = p.stdout.decode("utf-8", errors="replace")
    err = p.stderr.decode("utf-8", errors="replace")
    # 🔴 **必须同时看「标记文本」**（第三十九轮实测踩到）：
    #    只看退出码 ⇒ **脚本因 `unbound variable` 提前退出也是 1** ⇒ **假绿**。
    if "假探针" in out and "unbound variable" not in err:
        ok("E48-17", "假探针的输出出现在闸门输出里（不是提前崩掉）")
    else:
        ng("E48-17", f"闸门输出里没有假探针的标记（stderr: {err.strip()[:120]}）⇒ 退出码是**意外**得来的")
    if p.returncode == 1:
        ok("E48-18", "假探针 exit 1 ⇒ 闸门 exit 1（退出码确实透传）")
    else:
        ng("E48-18", f"假探针 exit 1 ⇒ 闸门 exit {p.returncode} ⇒ **退出码没透传 ⇒ 假闸门**")

# ── ④b 第三步判据：探针「声称 PASS」必须交出观测证据 ──────────────────
# 🔴 **第四十四轮 · users/admin 域 owner 建议**（出处：AGENTS.md §3.3 2026-10-21 补
#    「判断有没有真生效不能看启动日志，唯一可靠的判据是**运行时证据**」）：
#    **「退出码 0」不等于「探针真跑过」** —— 脚本什么都没干就退 0 也是 0 ⇒ **假闸门**
#    ⇒ 闸门必须**在探针声称 PASS 时**要求它交出**两个 Δ 值**当证据。
print("④b 第三步判据：探针声称 PASS ⇒ 必须交出 Δ 证据（否则就是假闸门）")
gate_src = open(GATE, encoding="utf-8").read()
has_evidence_check = ("Δ_start=" in gate_src and "Δ_end=" in gate_src
                      and re.search(r"if \[\[ \$RC -eq 0 \]", gate_src) is not None)
if has_evidence_check:
    ok("E48-31", "闸门在探针**声称 PASS** 时要求 Δ 证据（静态：判据在 + 只在 RC=0 时生效）")
else:
    ng("E48-31", "闸门**没有**「探针声称 PASS ⇒ 必须交出 Δ 证据」的判据"
                 "⇒ 脚本什么都没干就退 0 也是 0 ⇒ **假闸门**")

with tempfile.TemporaryDirectory() as td:
    # 假探针①：退出码 0，但**不输出任何 Δ** ⇒ 闸门必须**判红**（证明第三步判据有牙）
    stub_no_ev = os.path.join(td, "stub-no-evidence.sh")
    with open(stub_no_ev, "w", encoding="utf-8") as f:
        f.write("#!/usr/bin/env bash\n"
                "echo '  (假探针) 什么都没干就退 0'\n"
                "exit 0\n")
    os.chmod(stub_no_ev, 0o755)
    # 假探针②：退出码 0，且**输出了 Δ** ⇒ 闸门必须**放行**（证明不误杀正常路径）
    stub_ok = os.path.join(td, "stub-with-evidence.sh")
    with open(stub_ok, "w", encoding="utf-8") as f:
        f.write("#!/usr/bin/env bash\n"
                "echo '  ⇒ 查询存活 Δ_start=+306ms   客户端返回后仍占用 PG Δ_end=+0ms   pid=218'\n"
                "exit 0\n")
    os.chmod(stub_ok, 0o755)
    base_env = dict(os.environ)
    base_env["GO_ADMIN_PORT"] = "18081"
    base_env["RAG_ADMIN_PORT"] = "58081"
    base_env["RUN_DIR"] = td
    base_env["DIFTEST_CONTAINER_PREFIX"] = "difftest"

    e1 = dict(base_env); e1["EXP_PROBE_STUB"] = stub_no_ev
    p1 = subprocess.run(["bash", GATE], capture_output=True, env=e1, cwd=ROOT)
    o1 = p1.stdout.decode("utf-8", errors="replace")
    if p1.returncode != 0 and "假闸门" in o1:
        ok("E48-32", "假探针 exit 0 但**无 Δ 证据** ⇒ 闸门**判红**（第三步判据有牙，不是摆设）")
    else:
        ng("E48-32", f"假探针 exit 0 且无 Δ 证据 ⇒ 闸门 exit {p1.returncode}（应非 0）"
                     "⇒ 「退出码 0」被当成了「探针真跑过」⇒ **假闸门**")

    e2 = dict(base_env); e2["EXP_PROBE_STUB"] = stub_ok
    p2 = subprocess.run(["bash", GATE], capture_output=True, env=e2, cwd=ROOT)
    o2 = p2.stdout.decode("utf-8", errors="replace")
    if p2.returncode == 0 and "交出了观测证据" in o2:
        ok("E48-33", "假探针 exit 0 且**有 Δ 证据** ⇒ 闸门**放行**（第三步判据不误杀正常路径）")
    else:
        ng("E48-33", f"假探针 exit 0 且有 Δ 证据 ⇒ 闸门 exit {p2.returncode}（应 0）"
                     "⇒ 第三步判据**误杀**了正常路径")

# ── ⑤ bash 陷阱 lint：$VAR 后面紧跟非 ASCII 字符 ─────────────────────
# 🔴 **本轮实测踩到**：wrapper 里写了 `$RC（0=PASS …）` ⇒ bash 把 `（` 并进变量名
#    ⇒ 变量名变成 `RC（` ⇒ `set -u` 下 **unbound variable** ⇒ 脚本**提前退出 1**
#    ⇒ ⇒ 「退出码透传」这条测试**因错误原因而绿**（假绿！）⇒ ⇒ 必须 lint。
print("⑤ bash lint：$VAR 后面紧跟非 ASCII 字符（bash 会把它并进变量名 ⇒ unbound variable）")
trap_re = re.compile(r"\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7f]")
lint_targets = [
    ("tools/difftest/run-deadline-gate.sh", GATE, "E48-19"),
    ("tools/difftest/exp-grpc-admin-deadline-probe.sh", PROBE, "E48-20"),
    ("tools/difftest/run.sh", RUNSH, "E48-21"),
]
lint_bad = 0
for label, path, cid in lint_targets:
    src = open(path, encoding="utf-8").read()
    hits = []
    for i, line in enumerate(src.split("\n"), 1):
        stripped = line.strip()
        if stripped.startswith("#"):
            continue          # 注释里的示例不算（探针头部就把这类当反例写着）
        for _m in trap_re.finditer(line):
            hits.append((i, stripped[:90]))
    if hits:
        lint_bad += len(hits)
        for i, t in hits:
            ng(cid, f"{label}:{i} `$VAR` 后紧跟非 ASCII ⇒ 会 unbound variable :: {t}")
    else:
        ok(cid, f"{label} 没有 `$VAR`+非 ASCII 的写法")
if lint_bad:
    ng("E48-22", f"共 {lint_bad} 处 bash 变量名陷阱（会让脚本提前退出 ⇒ 门禁假绿）")

print()
if bad:
    print(f"⇒ ❌ E48 守门：{bad} 处不通过")
    sys.exit(1)
print("⇒ ✅ E48 守门：判定与接线全部有效")
