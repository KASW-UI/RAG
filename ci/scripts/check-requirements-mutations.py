#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-requirements-mutations.py —— **证明「目标要求清单守门」不是假绿**

================================================================================
【为什么需要这个】
  `check-requirements.py` 是**结构 + 口径**守门 —— 它判的是「清单写得对不对、
  声明得实不实」。而这类门禁最容易出的事故是：**门禁自己坏了，却一直绿**。
  ⇒ 本脚本把清单**故意改坏**若干种，每次都要求守门**红在对应的规则上**
  ⇒ 守门「有牙」这件事本身 becomes 可复算的事实，而不是断言。

================================================================================
【纪律（照抄本仓既有做法）】
  ① 变异在**临时副本**上做 ⇒ **绝不碰仓里的真清单**；
  ② 每条变异都要断言**红在目标规则**（不是「随便红」⇒ 否则门禁的判据可能整体失效）；
  ③ 变异结束后**必须确认真清单字节未变**（哈希比对）⇒ 防止「变异脚本自己把真源改了」；
  ④ 退出码：0 = 全部变异都按预期转红；1 = 有变异**没被抓到**（门禁有假绿）；
     2 = 变异脚本自身环境不满足。

================================================================================
【用法】
  python3 ci/scripts/check-requirements-mutations.py
  python3 ci/scripts/check-requirements-mutations.py --json
================================================================================
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
GATE = ROOT / "ci" / "scripts" / "check-requirements.py"
REGISTER = ROOT / "ci" / "requirements" / "requirements.json"

# 一条「已签字」id + 一条「未签字」id + 一个不存在 id（台账回指的三种情形）
SIGNED_ID = "SD-2026-10-23-06"
UNSIGNED_ID = "SD-2026-10-24-CAND-05"
ABSENT_ID = "SD-NOT-EXIST-999"


def _sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _find(data: dict, rid: str) -> dict:
    for r in data["requirements"]:
        if r.get("requirement_id") == rid:
            return r
    raise KeyError(f"清单里没有 {rid}")


# ── 变异用例 ────────────────────────────────────────────────────────────────
#   (名称, 施加到清单上的变换, 期望守门报出的规则标记)
MUTATIONS = [
    ("M1 · 抹掉 Go 依据（反向定义的入口）",
     lambda d: _find(d, "REQ-PROTO-LIKE-001").__setitem__("go_basis", ""),
     "R3"),

    ("M2 · 状态取值越界（把缺口塞进取值域外）",
     lambda d: _find(d, "REQ-PROTO-LIKE-001").__setitem__("status", "MOSTLY_OK"),
     "R2"),

    ("M3 · **自行豁免**：APPROVED_DIFFERENCE 回指到**未签字**条目",
     lambda d: _find(d, "REQ-ERRMSG-LIKE-002")["deviation"].__setitem__("id", UNSIGNED_ID),
     "R6"),

    ("M4 · **自行豁免**：APPROVED_DIFFERENCE 回指到**不存在**的条目",
     lambda d: _find(d, "REQ-ERRMSG-LIKE-002")["deviation"].__setitem__("id", ABSENT_ID),
     "R6"),

    ("M5 · VERIFIED 声明失去可复算命令（空壳 PASS）",
     lambda d: _find(d, "REQ-PROTO-HOT-001").__setitem__("verify_command", ""),
     "R5"),

    ("M6 · VERIFIED 声明指向不存在的脚本",
     lambda d: _find(d, "REQ-PROTO-HOT-001").__setitem__(
         "verify_command", "python3 ci/scripts/does-not-exist.py"),
     "R5"),

    ("M7 · FAIL 条目抹掉理由（把已知不一致藏起来）",
     lambda d: _find(d, "REQ-ERRMSG-LIKE-001").pop("gap_note", None),
     "R7"),

    ("M8 · VERIFIED 要求挂到不存在的流水线（没人执行它）",
     lambda d: _find(d, "REQ-PROTO-HOT-001").__setitem__("pipeline", "rag-does-not-exist"),
     "R8"),

    ("M9 · 删掉一条覆盖某域的要求（基准在、要求不在 ⇒ 覆盖缺口）",
     lambda d: d["requirements"].remove(_find(d, "REQ-SCHEMA-LIKE-001")),
     "R10"),

    ("M10 · requirement_id 重复",
     lambda d: d["requirements"].append(copy.deepcopy(_find(d, "REQ-PROTO-HOT-001"))),
     "R1"),
]


def run_gate(register_path: pathlib.Path) -> tuple[int, str]:
    p = subprocess.run(
        [sys.executable, str(GATE), "--register", str(register_path),
         "--skip-reverify"],
        cwd=str(ROOT), capture_output=True, text=True, timeout=300)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def main() -> int:
    ap = argparse.ArgumentParser(description="要求清单守门的变异测试")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    if not REGISTER.exists():
        print(f"[mut] ❌ 清单不存在：{REGISTER} ⇒ 环境不满足", flush=True)
        return 2
    if not GATE.exists():
        print(f"[mut] ❌ 守门不存在：{GATE} ⇒ 环境不满足", flush=True)
        return 2

    before = _sha(REGISTER)
    base_rc, base_out = run_gate(REGISTER)

    results = []
    ok = True

    if not args.json:
        print("[mut] ============================================================")
        print("[mut] 目标要求清单守门 · 变异测试（**证明门禁不是假绿**）")
        print(f"[mut] 清单：{REGISTER.relative_to(ROOT)}")
        print(f"[mut] 基线（未变异）：退出码 {base_rc}"
              f"{'（✅ 结构合规）' if base_rc == 0 else '（⚠️ 基线就不合规 —— 变异读数不可信！）'}")
        if base_rc != 0:
            print("[mut]    ⇒ 基线不合规 ⇒ **先修基线**，否则「变异没被抓到」分不清是门禁有牙还是基线本来就红")
            print(base_out)
            return 2
        print("[mut] --------------------------------------------------------")

    for name, mutate, expect_rule in MUTATIONS:
        data = json.loads(REGISTER.read_text(encoding="utf-8"))
        try:
            mutate(data)
        except Exception as e:
            results.append({"name": name, "expect": expect_rule, "caught": False,
                            "detail": f"变异施加失败：{e}"})
            ok = False
            continue

        with tempfile.TemporaryDirectory(prefix="req-mut-") as td:
            tmp = pathlib.Path(td) / "requirements.json"
            tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
            rc, out = run_gate(tmp)

        # ⚠️ **红在目标断言**：退出码非 0 **不够** —— 守门自己崩了也是非 0。
        #    ⇒ 必须同时满足：① 输出里出现目标规则 ② 输出里**没有** Traceback。
        crashed = "Traceback (most recent call last)" in out
        caught = rc != 0 and expect_rule in out and not crashed
        results.append({"name": name, "expect": expect_rule, "caught": caught,
                        "exit": rc, "crashed": crashed,
                        "detail": ("**红在崩溃（不是红在规则）** ⇒ 守门自身有 bug"
                                   if crashed else _first_rule_hit(out, expect_rule))})
        if not caught:
            ok = False

    # ③ 真清单必须字节未变
    after = _sha(REGISTER)
    unchanged = before == after
    if not unchanged:
        ok = False

    if args.json:
        print(json.dumps({"ok": ok, "results": results, "register_unchanged": unchanged},
                         ensure_ascii=False, indent=2))
    else:
        for r in results:
            mark = "✅" if r["caught"] else "❌"
            print(f"[mut] {mark} {r['name']}")
            print(f"[mut]      期望规则 {r['expect']}｜实际退出码 {r.get('exit')}｜{r.get('detail', '')}")
        print("[mut] --------------------------------------------------------")
        n_ok = sum(1 for r in results if r["caught"])
        print(f"[mut] 变异抓取率：{n_ok}/{len(results)}")
        print(f"[mut] 真清单字节未变：{'✅ 是' if unchanged else '❌ 否（变异脚本污染了真源！）'}")
        print(f"[mut] ============================================================")

    return 0 if ok else 1


def _first_rule_hit(out: str, rule: str) -> str:
    for ln in out.splitlines():
        if f"· {rule} ·" in ln:
            return ln.strip()[:160]
    for ln in out.splitlines():
        if rule in ln:
            return ln.strip()[:160]
    return "（输出里没找到该规则的命中行）"


if __name__ == "__main__":
    sys.exit(main())
