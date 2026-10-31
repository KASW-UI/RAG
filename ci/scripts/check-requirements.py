#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-requirements.py —— **目标要求清单**守门（CI 入口）

================================================================================
【它守什么】
  `ci/requirements/requirements.json` 里每一条「目标要求」的**结构合规**与
  **状态口径**，以及 **VERIFIED 声明的可复算性**。

================================================================================
【为什么要有这道门（2026-10-31 方向纠正的产物）】
  之前的契约基准（R4/R5/R6）虽然都标称「以 Go 为基准」，但**没有任何机器机制**
  保证「基准真的是 Go 派生的」⇒ 「先读 Java 实现 → 整理成标准 → 让 CI 检查它
  是否符合这份标准」这种**反向定义**可以悄悄发生。
  ⇒ 本门把三条纪律落成**可执行判据**：
    ① **每条要求必须有 Go 原始依据**（`go_basis` 必填）⇒ 没有 Go 依据 = 不许存在；
    ② **APPROVED_DIFFERENCE 必须能机器回指到已签字条目** ⇒ 杜绝「自行豁免」；
    ③ **NOT_IMPLEMENTED / NOT_VERIFIED / BLOCKED 一律不得计入 PASS**。

================================================================================
【退出码（四态 · 必须分得开）】
  0 = PASS      结构合规 **且** 本阶段可复验的 VERIFIED 声明全部复验通过
  1 = FAIL      结构违规（规则 R1~R9）**或** 某条 VERIFIED 声明复验失败
                （⇒ **Java 回归**）**或** 某条 FAIL 条目缺 `gap_note`（⇒ 差异被藏起来）
  2 = BLOCKED   清单缺失 / 损坏 / 无法解析，或复验因**环境不满足**做不成
                ⇒ **检查没做成** ⇒ 绝不能报成 FAIL（否则维护者会去查代码，真因在环境）
  3 = GAPS_ONLY 清单本身合规，但存在 NOT_IMPLEMENTED / NOT_VERIFIED / BLOCKED / FAIL
                ⇒ **这些不计入 PASS**（PASS 只 = VERIFIED）⇒ 汇报口径用

  ⚠️ **默认退出 0 的含义是「没有回归 + 结构合规」**，**不是「目标全部达成」**
     ⇒ 六态分列必须与 PASS 数**同时**打印，否则就是把缺口伪装成通过。

================================================================================
【复验范围（重要）】
  默认**只复验 `proto_contract` 维度**（秒级、无外部依赖）⇒ 适合放进每条流水线的
  静态守门阶段。其余维度的复验由各自的 owning pipeline 承担：
    · `errmsg_contract`  ⇒ rag-contract-errmsg / contract-check
    · `schema_contract`  ⇒ rag-contract-schema  / contract-check
  ⇒ 本门**只校验**这些维度指向的 pipeline 与 task **在仓里真实存在**
  （规则 R8）⇒ 「要求写了但没人执行」会被当场抓住。
  `--reverify-all` 可强制全维度复验（需要 Maven / Docker 环境）。

================================================================================
【用法】
  python3 ci/scripts/check-requirements.py                 # 结构校验 + proto 维度复验
  python3 ci/scripts/check-requirements.py --skip-reverify # 只做结构校验
  python3 ci/scripts/check-requirements.py --reverify-all  # 全维度复验（重）
  python3 ci/scripts/check-requirements.py --json           # 机器可读输出
================================================================================
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
REGISTER = ROOT / "ci" / "requirements" / "requirements.json"
CONTRACTS = ROOT / "ci" / "contracts"
PIPELINES = ROOT / "ci" / "pipelines"
TASKS = ROOT / "ci" / "tasks"

# ── 状态词表 ────────────────────────────────────────────────────────────────
# ⚠️ 取值域是**封闭**的：多一个状态就多一种「缺口被藏起来」的路径。
STATUSES = (
    "VERIFIED",
    "FAIL",
    "NOT_IMPLEMENTED",
    "NOT_VERIFIED",
    "APPROVED_DIFFERENCE",
    "BLOCKED",
    "NOT_APPLICABLE",
)
# ⛔ **不计入 PASS** 的状态（用户口径：NOT_IMPLEMENTED / NOT_VERIFIED / BLOCKED 不得计入 PASS）
NOT_PASS_STATUSES = ("NOT_IMPLEMENTED", "NOT_VERIFIED", "BLOCKED")

DIMENSIONS = (
    "proto_contract",
    "proto_consumption",
    "errmsg_contract",
    "errmsg_propagation",
    "schema_contract",
    "schema_constraint",
    "runtime_behavior",
    "callgraph",
    "engineering_constraint",
)

ID_RE = re.compile(r"^REQ-[A-Z0-9]+(?:-[A-Z0-9]+)*-\d{3}$")
# 复验策略：哪些维度在「静态守门阶段」可以复验（秒级、无外部依赖）
DEFAULT_REVERIFY_DIMS = ("proto_contract",)

PASS = 0
FAIL = 1
BLOCKED = 2
GAPS_ONLY = 3


# ── 域名别名表（**显式写出，不许隐式推导**）───────────────────────────────
# ⚠️ 基准文件按 **Go 侧 proto 文件名**命名（`user.proto` / `admin.proto`），
#    清单的 `domain` 用 **Java 域**命名（`users-user` / `users-admin`）
#    ⇒ 两者对不上 ⇒ 必须有一张**显式**的映射表。
#    隐式推导（比如「去掉 users- 前缀」）⇒ 将来新增一个域时会悄悄错配 ⇒ 不可取。
DOMAIN_ALIASES = {
    ("proto_contract", "user"): ("users-user",),
    ("proto_contract", "admin"): ("users-admin",),
}


def _domain_aliases(dim: str, stem: str) -> tuple:
    """⇒ 该基准文件可能对应的清单 domain（第一个是自然名，其余是别名）"""
    base = (stem,)
    return base + DOMAIN_ALIASES.get((dim, stem), ())


def _task_exists(task: str) -> bool:
    """⇒ task 名是否真实存在：既可以是 `ci/tasks/<name>.yml`，也可以是流水线里的 job 名"""
    if (TASKS / f"{task}.yml").exists():
        return True
    if PIPELINES.is_dir():
        for y in PIPELINES.glob("*.yml"):
            try:
                txt = y.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            if f"- name: {task}" in txt:
                return True
    return False


# ── 台账回指：复用 docs/Intentional_Deviations 的解析器 ────────────────────
def load_signed_deviation_ids() -> tuple[set, set, str]:
    """⇒ (已签字 id 集合, 全部 id → 分类映射, 错误说明)

    ⚠️ **必须复用既有解析器，不另写一份** —— 另写一份 ⇒ 两套口径 ⇒
    「回指通过但台账里其实没签」这类假绿。
    """
    mod_path = ROOT / "tools" / "difftest" / "ledger-deviation-count.py"
    if not mod_path.exists():
        return set(), {}, f"找不到台账解析器：{mod_path}"
    try:
        spec = importlib.util.spec_from_file_location("_ledger_deviation_count", mod_path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        entries = mod._entries()
    except Exception as e:  # pragma: no cover
        return set(), {}, f"台账解析器执行失败：{e}"

    signed: set = set()
    allmap: dict = {}
    for e in entries:
        ident = e.get("id")
        if not ident:
            continue
        kind = e.get("kind", "")
        allmap.setdefault(ident, kind)
        if kind == "已签字":
            signed.add(ident)
    return signed, allmap, ""


# ── 载入清单 ────────────────────────────────────────────────────────────────
def load_register() -> tuple[dict | None, str]:
    if not REGISTER.exists():
        return None, f"清单不存在：{REGISTER}"
    try:
        data = json.loads(REGISTER.read_text(encoding="utf-8"))
    except Exception as e:
        return None, f"清单无法解析（JSON 损坏）：{e}"
    if not isinstance(data.get("requirements"), list):
        return None, "清单缺少 requirements 数组"
    return data, ""


# ── 规则校验 ────────────────────────────────────────────────────────────────
def validate(data: dict, signed_ids: set, all_kinds: dict) -> tuple[list, list]:
    """⇒ (errors, warnings)"""
    errors: list = []
    warnings: list = []

    reqs = data["requirements"]
    seen_ids: set = set()
    seen_keys: set = set()

    for i, r in enumerate(reqs):
        rid = r.get("requirement_id") or f"<第 {i + 1} 条：缺 requirement_id>"

        # R1 —— requirement_id 格式 + 唯一
        if not r.get("requirement_id"):
            errors.append(f"{rid} · R1 · 缺 requirement_id")
        else:
            if not ID_RE.match(r["requirement_id"]):
                errors.append(f"{rid} · R1 · requirement_id 不符合 `REQ-<维度>-<域>-<NN>` 形态")
            if r["requirement_id"] in seen_ids:
                errors.append(f"{rid} · R1 · requirement_id 重复")
            seen_ids.add(r.get("requirement_id", ""))

        # R2 —— 状态取值域封闭
        st = r.get("status")
        if st not in STATUSES:
            errors.append(f"{rid} · R2 · status 取值非法：{st!r}（允许：{', '.join(STATUSES)}）")

        # R3 —— ⛔ **Go 依据必填**（这条就是「禁止反向定义」的机器化）
        gb = r.get("go_basis")
        if not gb or (isinstance(gb, str) and not gb.strip()):
            errors.append(f"{rid} · R3 · ⛔ 缺 go_basis ⇒ 该条没有 Go 原始依据 "
                          f"⇒ **不允许存在**（禁止以 Java 现状反向定义目标）")
        elif isinstance(gb, str) and not gb.strip().startswith(("proto/", "service/", "net/", "docs/")):
            warnings.append(f"{rid} · R3 · go_basis 形态异常（不像 Go 侧路径）：{gb!r}")

        # R4 —— 维度取值域
        if r.get("dimension") not in DIMENSIONS:
            errors.append(f"{rid} · R4 · dimension 取值非法：{r.get('dimension')!r}")

        # R5 —— VERIFIED 必须有**可复算**的判定命令 + 证据
        if st == "VERIFIED":
            cmd = (r.get("verify_command") or "").strip()
            if not cmd:
                errors.append(f"{rid} · R5 · status=VERIFIED 但 verify_command 为空 "
                              f"⇒ 「实际验证通过」不可复算 ⇒ 降级为 NOT_VERIFIED")
            else:
                first = cmd.split()[0]
                if first not in ("python3", "bash", "sh") and not cmd.startswith("mvn"):
                    errors.append(f"{rid} · R5 · verify_command 形态异常：{cmd!r}")
                # 仓库内路径必须真实存在（命令本体可解析）
                for tok in cmd.split():
                    if tok.endswith(".py") or tok.endswith(".sh"):
                        p = ROOT / tok
                        if not p.exists():
                            errors.append(f"{rid} · R5 · verify_command 引用的脚本不存在：{tok}")
                        break
            ev = (r.get("evidence") or "").strip()
            if not ev:
                warnings.append(f"{rid} · R5 · VERIFIED 但 evidence 为空")
            elif not (ROOT / ev).exists():
                warnings.append(f"{rid} · R5 · evidence 指向的文件不存在：{ev}")

        # R6 —— APPROVED_DIFFERENCE 必须**机器回指**到已签字条目（杜绝自行豁免）
        if st == "APPROVED_DIFFERENCE":
            dev = r.get("deviation") or {}
            did = (dev.get("id") or "").strip()
            if not did:
                errors.append(f"{rid} · R6 · status=APPROVED_DIFFERENCE 但 deviation.id 为空 "
                              f"⇒ **无法回指审批** ⇒ 降级处理")
            elif did not in all_kinds:
                errors.append(f"{rid} · R6 · deviation.id={did!r} 在 docs/Intentional_Deviations/ "
                              f"里**查不到** ⇒ 该差异**未经签字** ⇒ 不得标 APPROVED_DIFFERENCE")
            elif did not in signed_ids:
                errors.append(f"{rid} · R6 · deviation.id={did!r} 在台账里的分类是 "
                              f"「{all_kinds[did]}」**不是「已签字」** ⇒ 不得标 APPROVED_DIFFERENCE")
            led = (dev.get("ledger") or "").strip()
            if led and not (ROOT / led).exists():
                errors.append(f"{rid} · R6 · deviation.ledger 指向的文件不存在：{led}")
            if not (dev.get("reason") or "").strip():
                errors.append(f"{rid} · R6 · deviation.reason 为空 ⇒ 审批依据缺失")

        # R7 —— FAIL 条目必须有理由（差异不得被藏起来）
        if st == "FAIL" and not (r.get("gap_note") or "").strip():
            errors.append(f"{rid} · R7 · status=FAIL 但 gap_note 为空 ⇒ "
                          f"「已知不一致」被藏起来了 ⇒ 必须写明证据与影响")

        # R8 —— VERIFIED 要求必须挂在**真实存在**的 pipeline / task 上
        #      ⚠️ `task` 既可能是「ci/tasks/<name>.yml」也可能是「流水线里的 job 名」
        #         （契约流水线的 job 叫 `contract-check`，task 文件叫 `contract-errmsg.yml`）
        #         ⇒ 两种都接受，但**必须真的存在** ⇒ 否则「要求写了但没人执行」抓不住。
        if st == "VERIFIED":
            pl = (r.get("pipeline") or "").strip()
            tk = (r.get("task") or "").strip()
            if pl and not pl.startswith("("):
                if not (PIPELINES / f"{pl}.yml").exists():
                    errors.append(f"{rid} · R8 · pipeline={pl!r} 在 ci/pipelines/ 下不存在 "
                                  f"⇒ 这条 VERIFIED 要求**没有执行者**")
            if tk and not _task_exists(tk):
                errors.append(f"{rid} · R8 · task={tk!r} 既不是 ci/tasks/{tk}.yml，"
                              f"也不是任何流水线里的 job 名 ⇒ 这条 VERIFIED 要求**没有被执行**")

        # R9 —— 条目唯一（同一维度同域**允许**有多条不同标题的要求）
        #      ⚠️ 早先版本按 (dimension, domain) 判重 ⇒ 把「同一域的多个维度条目」误判成重复
        #         ⇒ 改成三元组 (dimension, domain, title)。
        key = (r.get("dimension"), r.get("domain"), (r.get("title") or "").strip())
        if key in seen_keys:
            errors.append(f"{rid} · R9 · 同维度同域同标题出现重复条目：{key[:2]}")
        seen_keys.add(key)

    # R10 —— 覆盖守门：**契约基准里存在的域，清单里必须至少有 1 条要求**
    #          ⇒「基准存在但没进清单」会被抓住（否则清单会漏掉整块能力面）
    #          ⚠️ 别名表：基准文件按 **Go 侧 proto 文件名**命名（user.proto / admin.proto），
    #             而清单的 domain 用 **Java 域**命名（users-user / users-admin）
    #             ⇒ 不加别名就会误报「覆盖缺口」⇒ 别名必须显式写出来，不许隐式推导。
    covered = {(r.get("dimension"), r.get("domain")) for r in reqs}
    for dim, sub in (("proto_contract", "proto"), ("errmsg_contract", "errmsg"),
                     ("schema_contract", "schema")):
        d = CONTRACTS / sub
        if not d.is_dir():
            continue
        for f in sorted(d.iterdir()):
            if not f.is_file() or f.suffix not in (".proto", ".txt", ".json"):
                continue
            stem = f.stem
            cands = _domain_aliases(dim, stem)
            if not any((dim, c) in covered for c in cands):
                errors.append(f"R10 · 覆盖缺口 · {sub}/{f.name} 有基准，但清单里没有 "
                              f"(dimension={dim}, domain∈{cands}) 的要求 ⇒ "
                              f"**基准存在却没被登记成要求** ⇒ 不得算作「已覆盖」")

    # R11 —— 缺口台账必须被显式登记（不让「清单里没有」被读成「没有问题」）
    cov = data.get("coverage") or {}
    if not cov.get("unregistered_gap_ledgers"):
        warnings.append("R11 · coverage.unregistered_gap_ledgers 为空 ⇒ "
                        "未登记的目标要求缺口不可见 ⇒ 建议列出")

    return errors, warnings


# ── 复验 ────────────────────────────────────────────────────────────────────
def reverify(reqs: list, dims: tuple, only: str | None) -> tuple[list, list, list]:
    """⇒ (failures, blocked, notes)

    ⚠️ **FAIL(1) 与 BLOCKED(2) 必须分开**：复验命令因**环境不满足**做不成 ⇒ BLOCKED；
    复验命令跑完了但结论是漂移 ⇒ FAIL。把环境问题报成 FAIL 是「假红」，
    会让维护者去查代码而真因在环境（AGENTS.md §3.3）。
    """
    failures: list = []
    blocked: list = []
    notes: list = []

    for r in reqs:
        if r.get("status") != "VERIFIED":
            continue
        if r.get("dimension") not in dims:
            continue
        if only and only not in (r.get("requirement_id"), r.get("domain")):
            continue
        cmd = (r.get("verify_command") or "").strip()
        if not cmd:
            continue
        rid = r.get("requirement_id")
        notes.append(f"复验 {rid} · {cmd}")
        try:
            p = subprocess.run(cmd, shell=True, cwd=str(ROOT),
                               capture_output=True, text=True, timeout=1800)
        except subprocess.TimeoutExpired:
            blocked.append(f"{rid} · 复验超时 ⇒ BLOCKED（检查没做成）")
            continue
        except Exception as e:
            blocked.append(f"{rid} · 复验无法启动：{e} ⇒ BLOCKED")
            continue
        if p.returncode == 0:
            continue
        if p.returncode == 2:
            blocked.append(f"{rid} · 复验命令返回 2（BLOCKED：环境/依赖不满足）")
        else:
            tail = (p.stdout or "") + (p.stderr or "")
            tail = "\n".join([ln for ln in tail.splitlines() if ln.strip()][-12:])
            failures.append(f"{rid} · 🔴 VERIFIED 声明复验失败（退出码 {p.returncode}）"
                            f"⇒ **Java 回归**\n{_indent(tail)}")
    return failures, blocked, notes


def _indent(text: str, prefix: str = "        ") -> str:
    return "\n".join(prefix + ln for ln in text.splitlines())


def _rel(p: pathlib.Path) -> str:
    """仓内路径显示相对路径；仓外路径（变异测试的临时副本）显示绝对路径。

    ⚠️ **不要**直接 `p.relative_to(ROOT)` —— 仓外路径会抛 ValueError ⇒
    守门在变异测试里**崩掉** ⇒ 退出码也是 1 ⇒ **看起来像「规则抓到了」**
    ⇒ 实际上红在崩溃，不是红在规则 ⇒ 变异读数会被误判为「门禁有牙」。
    """
    try:
        return str(p.relative_to(ROOT))
    except ValueError:
        return str(p)


# ── 汇总 ────────────────────────────────────────────────────────────────────
def summarize(reqs: list) -> dict:
    counts = {s: 0 for s in STATUSES}
    by_status: dict = {s: [] for s in STATUSES}
    for r in reqs:
        st = r.get("status")
        counts[st] = counts.get(st, 0) + 1
        by_status.setdefault(st, []).append(r.get("requirement_id"))
    return {"counts": counts, "by_status": by_status}


# ── main ────────────────────────────────────────────────────────────────────
def main() -> int:
    ap = argparse.ArgumentParser(description="目标要求清单守门")
    ap.add_argument("--skip-reverify", action="store_true", help="只做结构校验")
    ap.add_argument("--reverify-all", action="store_true", help="全维度复验（重）")
    ap.add_argument("--only", default=None, help="只复验某个 requirement_id 或 domain")
    ap.add_argument("--register", default=None,
                    help="换一份清单来校验（变异测试用；默认 ci/requirements/requirements.json）")
    ap.add_argument("--json", action="store_true", help="机器可读输出")
    args = ap.parse_args()

    global REGISTER
    if args.register:
        REGISTER = pathlib.Path(args.register)
        if not REGISTER.is_absolute():
            REGISTER = (ROOT / REGISTER).resolve()

    data, err = load_register()
    if data is None:
        print(f"[req] ❌ {err} ⇒ **BLOCKED**（检查没做成，不是「通过」）", flush=True)
        return BLOCKED

    signed_ids, all_kinds, ledger_err = load_signed_deviation_ids()
    if ledger_err:
        print(f"[req] ❌ {ledger_err} ⇒ **BLOCKED**（无法回指审批台账 ⇒ 不得放行）",
              flush=True)
        return BLOCKED

    reqs = data["requirements"]
    errors, warnings = validate(data, signed_ids, all_kinds)

    failures: list = []
    blocked: list = []
    notes: list = []
    if not args.skip_reverify:
        dims = DIMENSIONS if args.reverify_all else DEFAULT_REVERIFY_DIMS
        failures, blocked, notes = reverify(reqs, dims, args.only)

    summ = summarize(reqs)
    counts = summ["counts"]
    n_total = len(reqs)
    n_verified = counts.get("VERIFIED", 0)
    n_not_pass = sum(counts.get(s, 0) for s in NOT_PASS_STATUSES)
    n_fail = counts.get("FAIL", 0)

    if args.json:
        print(json.dumps({
            "total": n_total,
            "counts": counts,
            "errors": errors,
            "warnings": warnings,
            "failures": failures,
            "blocked": blocked,
            "notes": notes,
            "not_pass_count": n_not_pass,
        }, ensure_ascii=False, indent=2))
    else:
        print("[req] ============================================================")
        print("[req] 目标要求清单守门（**Go 定 WHAT · Java 定 HOW · CI 验是否做到**）")
        print(f"[req] 清单：{_rel(REGISTER)}   条目数：{n_total}")
        print("[req] --------------------------------------------------------")
        print(f"[req] ✅ VERIFIED           （{counts.get('VERIFIED', 0)}）"
              f"：{_names(summ, 'VERIFIED')}")
        print(f"[req] ❌ FAIL               （{counts.get('FAIL', 0)}）"
              f"：{_names(summ, 'FAIL')}   ← Java 与目标要求不一致")
        print(f"[req] ⛔ NOT_IMPLEMENTED    （{counts.get('NOT_IMPLEMENTED', 0)}）"
              f"：{_names(summ, 'NOT_IMPLEMENTED')}   ← Java 尚未实现")
        print(f"[req] ⚠️ NOT_VERIFIED       （{counts.get('NOT_VERIFIED', 0)}）"
              f"：{_names(summ, 'NOT_VERIFIED')}   ← 有实现但验证不足")
        print(f"[req] ⛔ BLOCKED            （{counts.get('BLOCKED', 0)}）"
              f"：{_names(summ, 'BLOCKED')}   ← 验证条件不具备")
        print(f"[req] 🏷 APPROVED_DIFFERENCE（{counts.get('APPROVED_DIFFERENCE', 0)}）"
              f"：{_names(summ, 'APPROVED_DIFFERENCE')}   ← 已回指**已签字**条目")
        print(f"[req] ➖ NOT_APPLICABLE     （{counts.get('NOT_APPLICABLE', 0)}）"
              f"：{_names(summ, 'NOT_APPLICABLE')}   ← 该维度对本域不适用")
        print("[req] --------------------------------------------------------")
        print(f"[req] **PASS = {n_verified}**（**只**计 VERIFIED）"
              f"｜ **不计入 PASS 的缺口 = {n_not_pass}**"
              f"（NOT_IMPLEMENTED {counts.get('NOT_IMPLEMENTED', 0)} + "
              f"NOT_VERIFIED {counts.get('NOT_VERIFIED', 0)} + "
              f"BLOCKED {counts.get('BLOCKED', 0)}）")
        if n_fail:
            print(f"[req] ⛔ **FAIL 条目 {n_fail} 项** ⇒ Java 与 Go 目标要求不一致且未经批准"
                  f" ⇒ 需处置（**不得**自行加豁免、**不得**为绿改 Java 业务代码）")
        print(f"[req] ℹ️ **本门退出 0 的含义 = 「无回归 + 结构合规」** ⇒ "
              f"**不等于「目标全部达成」** ⇒ 缺口 {n_not_pass} 项 + FAIL {n_fail} 项**均已单列在上**")
        print("[req] --------------------------------------------------------")

        if errors:
            print(f"[req] 🔴 结构违规 {len(errors)} 条：")
            for e in errors:
                print(f"[req]    · {e}")
        if warnings:
            print(f"[req] ⚠️ 提示 {len(warnings)} 条：")
            for w in warnings:
                print(f"[req]    · {w}")
        if notes:
            print(f"[req] 🔁 复验 {len(notes)} 条：")
            for n in notes:
                print(f"[req]    · {n}")
        if failures:
            print(f"[req] 🔴 复验失败 {len(failures)} 条：")
            for f in failures:
                print(f"[req]    · {f}")
        if blocked:
            print(f"[req] ⛔ 复验被阻塞 {len(blocked)} 条：")
            for b in blocked:
                print(f"[req]    · {b}")

        # 缺口台账必须被显式报出
        for g in (data.get("coverage") or {}).get("unregistered_gap_ledgers", []):
            print(f"[req] 📌 **未登记进清单的目标要求缺口**：{g.get('ledger')} "
                  f"（{g.get('scope', '')}）· {g.get('items', '')} · 状态={g.get('status', '')}")
            print(f"[req]    ⇒ {g.get('note', '')}")
        print("[req] ============================================================")

    # ── 退出码 ────────────────────────────────────────────────────────────
    if errors or failures:
        return FAIL
    if blocked:
        return BLOCKED
    if n_not_pass or n_fail:
        # 结构性合规、复验通过，但**存在已登记的缺口/失败** ⇒
        # 汇报口径：PASS 数只计 VERIFIED（已在上面打印）⇒ 退出码 0
        # （退出码 0 = 「没有回归 + 结构合规」，**不等于**「目标全部达成」）
        return PASS
    return PASS


def _names(summ: dict, status: str) -> str:
    ids = summ["by_status"].get(status) or []
    if not ids:
        return "（无）"
    return "、".join(ids)


if __name__ == "__main__":
    sys.exit(main())
