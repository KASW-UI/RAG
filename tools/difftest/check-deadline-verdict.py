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
                  "--go-long-dend", "0", "--rag-long-dend", "0"])
ok("E48-02", "短档复现差异 ⇒ FAIL（退出码 1）") if rc == 1 else ng(
    "E48-02", f"短档复现差异却退出 {rc}（应 1）⇒ **假绿**")

# ⚠️ 这里**故意钉死**当前口径（严格大于）：差值正好等于阈值 ⇒ 不算差异 ⇒ PASS
rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "1000",
                  "--go-long-dend", "0", "--rag-long-dend", "0"])
ok("E48-03", "差值正好等于阈值 ⇒ PASS（当前口径是「严格大于」）") if rc == 0 else ng(
    "E48-03", f"等于阈值的口径变了（退出 {rc}）⇒ 判定语义被改，须同步改台账")

rc = run_verdict(["--go-short-dend", "0", "--rag-short-dend", "10",
                  "--go-long-dend", "0", "--rag-long-dend", "1800"])
ok("E48-04", "对照组也不一致 ⇒ INCONCLUSIVE（退出码 2）") if rc == 2 else ng(
    "E48-04", f"对照组不一致却退出 {rc}（应 2）")

rc = run_verdict(["--go-short-dend", "", "--rag-short-dend", "10",
                  "--go-long-dend", "0", "--rag-long-dend", "0"])
ok("E48-05", "观测缺失 ⇒ INCONCLUSIVE（退出码 2）") if rc == 2 else ng(
    "E48-05", f"观测缺失却退出 {rc}（应 2）⇒ **假绿**")

# ── ② 探针接线 ────────────────────────────────────────────────────────
print("② 探针必须调用判定器并透传退出码（不得再有无条件 exit 0）")
probe_src = open(PROBE, encoding="utf-8").read()
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

if "exit 3" in probe_src and "READY" in probe_src:
    ok("E48-24", "探针在**栈未就绪时 fail-fast**（不再无条件打印「✓ 就绪」）")
else:
    ng("E48-24", "探针在栈未就绪时**不 fail-fast** ⇒ 会带着无效观测往下跑 ⇒ 假绿")

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
