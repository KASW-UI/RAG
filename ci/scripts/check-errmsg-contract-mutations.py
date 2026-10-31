#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-errmsg-contract-mutations.py —— **证明 R5 错误码契约门不是假绿**

================================================================================
【它证明什么】
  `ci/scripts/check-errmsg-contract.sh` 的判据在**故意改坏**之后会不会转红。

  ⚠️ 重点不在「会红」，而在 **红在哪**：
     · **红在目标断言**（而不是红在崩溃、不是红在别的原因）⇒ 退出码必须匹配，
       且输出里必须出现该变异对应的**特征标记**；
     · 「红在崩溃」和「随便红」都不算数 ⇒ 本脚本同时断言 **输出里没有 Traceback**。

================================================================================
【变异范围】
  ① **基准侧**（Go 权威段 / 差异段）—— 在真仓上就地改，**跑完按字节恢复**，
     恢复后校验 sha256 ⇒ 「变异脚本自己污染了真源」会被当场抓住。
  ② **Java 侧**（真实改一个错误码文案）—— 用**符号链接树**搭一个临时仓，
     只把目标模块换成真副本 ⇒ 其余模块仍走仓内真源（maven reactor 需要完整树）。

================================================================================
【退出码】
  0 = 全部变异都按预期转红（门禁有牙）
  1 = 有变异**没被抓到**（门禁有假绿）
  2 = 变异脚本自身环境不满足（maven 不可用 / 基准目录不存在等）
================================================================================
"""
from __future__ import annotations

import argparse
import hashlib
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
GATE = ROOT / "ci" / "scripts" / "check-errmsg-contract.sh"
CONTRACTS = ROOT / "ci" / "contracts" / "errmsg"
PROBE = ROOT / "tools" / "errmsg-probe"

# 变异跑哪个域：
#   · article = **零差异**域 ⇒ 基准一改就必然与 Java 侧不符 ⇒ 适合测「Go 基准被改」
#   · like    = 含 pending 与 approved ⇒ 适合测「自证豁免」
DOM_MAIN = "article"
DOM_APPROVAL = "like"


def sha(p: pathlib.Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def run_gate(domains: str, repo_root: pathlib.Path | None = None) -> tuple[int, str]:
    env = dict(os.environ)
    if repo_root is not None:
        env["RAG_REPO_ROOT"] = str(repo_root)
    p = subprocess.run(["bash", str(GATE), *domains.split()],
                       cwd=str(ROOT), env=env, capture_output=True, text=True, timeout=3600)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def backup(path: pathlib.Path) -> tuple[pathlib.Path, str]:
    with tempfile.NamedTemporaryFile(suffix=path.suffix, delete=False) as tf:
        tmp = pathlib.Path(tf.name)
    shutil.copy2(path, tmp)
    return tmp, sha(path)


def restore(path: pathlib.Path, tmp: pathlib.Path, orig_sha: str) -> bool:
    shutil.copy2(tmp, path)
    try:
        tmp.unlink()
    except OSError:
        pass
    return sha(path) == orig_sha


# ── 基准文本工具 ────────────────────────────────────────────────────────────
def baseline_of(domain: str) -> pathlib.Path:
    return CONTRACTS / f"{domain}.txt"


def edit_baseline(domain: str, fn) -> tuple[pathlib.Path, pathlib.Path, str]:
    """⇒ (备份路径, 原文件, 原 sha256)"""
    p = baseline_of(domain)
    tmp, orig = backup(p)
    txt = p.read_text(encoding="utf-8")
    p.write_text(fn(txt), encoding="utf-8")
    return tmp, p, orig


def mutate_goconst_value(txt: str) -> str:
    """把第一个 GOCONST 的数值改掉 ⇒ Java 侧对不上 ⇒ 应判 FAIL"""
    out = []
    hit = False
    for ln in txt.splitlines(keepends=True):
        if not hit and ln.startswith("GOCONST|"):
            parts = ln.rstrip("\n").split("|")
            parts[1] = str(int(parts[1]) + 900000)
            ln = "|".join(parts) + "\n"
            hit = True
        out.append(ln)
    return "".join(out)


def mutate_drop_goconst(txt: str) -> str:
    """删掉一个 GOCONST ⇒ Java 侧多出该码 ⇒ 应判 FAIL"""
    out = []
    hit = False
    for ln in txt.splitlines(keepends=True):
        if not hit and ln.startswith("GOCONST|"):
            hit = True
            continue
        out.append(ln)
    return "".join(out)


def mutate_gotable_msg(txt: str) -> str:
    """改一个 GOTABLE 的文案 ⇒ 文案不一致 ⇒ 应判 FAIL"""
    out = []
    hit = False
    for ln in txt.splitlines(keepends=True):
        if not hit and ln.startswith("GOTABLE|"):
            parts = ln.rstrip("\n").split("|")
            parts[2] = parts[2] + "-漂移了"
            ln = "|".join(parts) + "\n"
            hit = True
        out.append(ln)
    return "".join(out)


def mutate_fallback(txt: str) -> str:
    """改 Go 回落文案 ⇒ Java 侧回落不一致 ⇒ 应判 FAIL"""
    out = []
    hit = False
    for ln in txt.splitlines(keepends=True):
        if not hit and ln.startswith("GOFALLBACK|"):
            parts = ln.rstrip("\n").split("|")
            parts[1] = "GO-FALLBACK-CHANGED"
            ln = "|".join(parts) + "\n"
            hit = True
        out.append(ln)
    return "".join(out)


def mutate_drop_authority(txt: str) -> str:
    """删掉 go-authority 段 ⇒ 基准格式损坏 ⇒ 应判 BLOCKED"""
    return re.sub(r"# >>> go-authority >>>[\s\S]*?(?=# >>> deviations >>>)", "", txt)


def mutate_break_deviation_fields(txt: str) -> str:
    """把一条 DEVIATION 的字段数改坏 ⇒ 基准格式损坏 ⇒ 应判 BLOCKED"""
    out = []
    hit = False
    for ln in txt.splitlines(keepends=True):
        if not hit and ln.startswith("DEVIATION|"):
            ln = "|".join(ln.rstrip("\n").split("|")[:4]) + "\n"
            hit = True
        out.append(ln)
    return "".join(out)


def mutate_pending_to_fake_approved(txt: str) -> str:
    """把一条 pending 改成 approved，依据 id 换成**台账里查不到**的编号
    ⇒ **自证豁免** ⇒ 应判 FAIL（不许自行豁免）"""
    out = []
    hit = False
    for ln in txt.splitlines(keepends=True):
        if not hit and ln.startswith("DEVIATION|") and "|pending|" in ln:
            parts = ln.rstrip("\n").split("|")
            parts[6] = "SD-MUTATION-NOT-EXIST-999"
            parts[7] = "docs/Intentional_Deviations/like/rpc.md"
            parts[5] = "approved"
            ln = "|".join(parts) + "\n"
            hit = True
        out.append(ln)
    return "".join(out)


def mutate_approved_wrong_ledger(txt: str) -> str:
    """把一条 approved 的依据文件改到 docs/Intentional_Deviations/ 之外
    ⇒ 依据不合法 ⇒ 应判 FAIL"""
    out = []
    hit = False
    for ln in txt.splitlines(keepends=True):
        if not hit and ln.startswith("DEVIATION|") and "|approved|" in ln:
            parts = ln.rstrip("\n").split("|")
            parts[7] = "docs/DRIFT/like/rpc.md"
            ln = "|".join(parts) + "\n"
            hit = True
        out.append(ln)
    return "".join(out)


# ── Java 侧变异：符号链接树 ─────────────────────────────────────────────────
def build_shadow_repo(dest: pathlib.Path, module_rel: str, mutate_java) -> pathlib.Path:
    """搭一个临时仓：除 `module_rel` 外全是符号链接，`module_rel` 是真副本。

    ⇒ maven reactor 需要完整目录树 ⇒ 用符号链接满足；
    ⇒ 只把要改的那一个模块换成真副本 ⇒ 其余仍指向仓内真源。
    """
    dest.mkdir(parents=True, exist_ok=True)
    for child in ROOT.iterdir():
        if child.name in (".git",):
            continue
        if child.name == "service":
            (dest / "service").mkdir()
            for svc in child.iterdir():
                if svc.name == module_rel.split("/")[1]:
                    continue
                (dest / "service" / svc.name).symlink_to(svc)
            # ⚠️ 域内**全部**模块都要在（maven 解析 service/<域>/pom.xml 时逐个读子模块）
            #    ⇒ 少一个就报「Child module ... could not read 1 project」⇒ BLOCKED
            dom = module_rel.split("/")[1]
            real_dom = child / dom
            (dest / "service" / dom).mkdir()
            for mod in real_dom.iterdir():
                (dest / "service" / dom / mod.name).symlink_to(mod)
            continue
        (dest / child.name).symlink_to(child)

    # 把目标模块换成真副本
    dom = module_rel.split("/")[1]
    name = module_rel.split("/")[2]
    link = dest / "service" / dom / name
    if link.is_symlink():
        link.unlink()
    shutil.copytree(ROOT / module_rel, link, symlinks=True,
                    ignore=shutil.ignore_patterns("target"))
    mutate_java(link)
    return dest


def mutate_java_errorcode(module_dir: pathlib.Path) -> bool:
    """改一个 Java 错误码文案 ⇒ 应判 FAIL

    ⚠️ **必须只改 `src/main/**`** —— 模块里还有 `*ErrorCodeContractTest.java`，
    rglob 的顺序可能先命中**测试文件** ⇒ 探针读的是 `target/classes`（主源码产物）
    ⇒ 改了测试文件 = **什么都没改** ⇒ 门禁当然绿 ⇒ 读数会被误判成「门禁有盲区」。
    """
    allj = [f for f in module_dir.rglob("*.java") if "/src/main/" in str(f)]
    cands = [f for f in allj if "ErrorCode" in f.name] or allj
    for f in cands:
        txt = f.read_text(encoding="utf-8")
        m = re.search(r'(\(\s*\d+\s*,\s*")([^"]+)(")', txt)
        if not m:
            continue
        new = txt[:m.start(2)] + m.group(2) + "-漂移了" + txt[m.end(2):]
        f.write_text(new, encoding="utf-8")
        return True
    return False


def probe_path(domain: str) -> pathlib.Path:
    """探针源码路径 —— **必须与检查器 spec_for() 的口径一致**。

    ⚠️ article 域的探针在 `tools/errmsg-probe/java/Probe.java`（**没有 article/ 子目录**）
    ⇒ 其它域才有 `tools/errmsg-probe/<域>/java/Probe.java`
    ⇒ 路径写错 ⇒ 变异脚本自己崩 ⇒ 读数丢失（本轮就踩过一次）。
    """
    cands = [PROBE / domain / "java" / "Probe.java", PROBE / "java" / "Probe.java"]
    for c in cands:
        if c.exists():
            return c
    raise FileNotFoundError(f"找不到 {domain} 域的探针源码（试过：{cands}）")


def mutate_probe_broken(domain: str) -> tuple[pathlib.Path, str, pathlib.Path]:
    """破坏探针源码 ⇒ javac 失败 ⇒ 应判 BLOCKED"""
    p = probe_path(domain)
    tmp, orig = backup(p)
    p.write_text("public class Probe { this is not java }\n", encoding="utf-8")
    return tmp, orig, p


# ── 变异用例 ────────────────────────────────────────────────────────────────
def run_baseline_mutations() -> list:
    results = []
    cases = [
        ("M1 · 改 Go 基准的一个码值", DOM_MAIN, mutate_goconst_value, 1, "FAIL"),
        ("M2 · 删掉 Go 基准的一个码（Java 侧多出码）", DOM_MAIN, mutate_drop_goconst, 1, "FAIL"),
        ("M3 · 改 Go 基准的一个文案", DOM_MAIN, mutate_gotable_msg, 1, "FAIL"),
        ("M4 · 改 Go 基准的回落文案", DOM_MAIN, mutate_fallback, 1, "FAIL"),
        ("M5 · 基准缺 go-authority 段（格式损坏）", DOM_MAIN, mutate_drop_authority, 2, "BLOCKED"),
        # ⚠️ 必须用**有 DEVIATION 的域**（article 域 0 条 ⇒ 变异无处下手 ⇒ 根本没生效）
        ("M6 · 基准的 DEVIATION 字段数损坏", DOM_APPROVAL, mutate_break_deviation_fields, 2, "BLOCKED"),
        ("M7 · **自行豁免**：pending → approved，依据 id 台账里查不到",
         DOM_APPROVAL, mutate_pending_to_fake_approved, 1, "FAIL"),
        ("M8 · approved 的依据文件不在 Intentional_Deviations 下",
         DOM_APPROVAL, mutate_approved_wrong_ledger, 1, "FAIL"),
    ]
    for name, dom, fn, expect_rc, expect_kind in cases:
        tmp, p, orig = edit_baseline(dom, fn)
        try:
            rc, out = run_gate(dom)
        finally:
            ok_restore = restore(p, tmp, orig)
        crashed = "Traceback (most recent call last)" in out or "syntax error" in out.lower() \
            and "unexpected" in out.lower()
        caught = rc == expect_rc and (expect_kind in out) and not crashed
        results.append({
            "name": name, "domain": dom, "expect_rc": expect_rc, "expect_kind": expect_kind,
            "actual_rc": rc, "caught": caught, "restored": ok_restore,
            "detail": _first_hit(out, expect_kind),
        })
    return results


def run_missing_baseline_mutation() -> list:
    """基准文件缺失 ⇒ BLOCKED"""
    dom = DOM_MAIN
    p = baseline_of(dom)
    tmp, orig = backup(p)
    p.unlink()
    try:
        rc, out = run_gate(dom)
    finally:
        shutil.copy2(tmp, p)
        try:
            tmp.unlink()
        except OSError:
            pass
        restored = sha(p) == orig
    caught = rc == 2 and "BLOCKED" in out
    return [{"name": "M9 · 基准文件缺失", "domain": dom, "expect_rc": 2, "expect_kind": "BLOCKED",
             "actual_rc": rc, "caught": caught, "restored": restored,
             "detail": _first_hit(out, "BLOCKED")}]


def run_broken_probe_mutation() -> list:
    """探针编译失败 ⇒ BLOCKED"""
    dom = DOM_MAIN
    tmp, orig, probe = mutate_probe_broken(dom)
    try:
        rc, out = run_gate(dom)
    finally:
        shutil.copy2(tmp, probe)
        try:
            tmp.unlink()
        except OSError:
            pass
        restored = sha(probe) == orig
    caught = rc == 2 and "BLOCKED" in out
    return [{"name": "M10 · 探针编译失败（环境/构建失败 ⇒ 不是 FAIL）", "domain": dom,
             "expect_rc": 2, "expect_kind": "BLOCKED", "actual_rc": rc, "caught": caught,
             "restored": restored, "detail": _first_hit(out, "BLOCKED")}]


def run_java_side_mutation() -> list:
    """Java 侧真的漂移 ⇒ FAIL（用符号链接树，只真改一个模块）"""
    module_rel = "service/article/article-common"
    with tempfile.TemporaryDirectory(prefix="r5-shadow-") as td:
        shadow = pathlib.Path(td) / "repo"
        try:
            build_shadow_repo(shadow, module_rel, lambda d: mutate_java_errorcode(d))
        except Exception as e:
            return [{"name": "M11 · Java 侧错误码文案漂移", "expect_rc": 1, "expect_kind": "FAIL",
                     "actual_rc": None, "caught": False, "restored": True,
                     "detail": f"影子仓搭建失败：{e}"}]
        rc, out = run_gate(DOM_MAIN, repo_root=shadow)
    caught = rc == 1 and "FAIL" in out
    return [{"name": "M11 · Java 侧错误码文案漂移（影子仓 · 只真改一个模块）",
             "domain": DOM_MAIN, "expect_rc": 1, "expect_kind": "FAIL", "actual_rc": rc,
             "caught": caught, "restored": True, "detail": _first_hit(out, "FAIL")}]


def _first_hit(out: str, kind: str) -> str:
    for ln in out.splitlines():
        if kind in ln:
            return ln.strip()[:170]
    return "（输出里没找到特征标记）"


def main() -> int:
    ap = argparse.ArgumentParser(description="R5 契约门的变异测试")
    ap.add_argument("--skip-java-side", action="store_true",
                    help="跳过需要影子仓的 Java 侧变异（较慢）")
    args = ap.parse_args()

    if not GATE.exists() or not CONTRACTS.is_dir():
        print("[mut] ❌ 环境不满足（门或基准目录不存在）⇒ EXIT=2")
        return 2

    # 基线：先跑一次未变异的，确认「基线本身是绿的」
    base_rc, base_out = run_gate(DOM_MAIN)
    print(f"[mut] 基线（{DOM_MAIN} 域，未变异）：退出码 {base_rc}")
    if base_rc != 0:
        print("[mut]    ⇒ 基线不是 0 ⇒ 变异读数不可信 ⇒ 先修基线")
        print(base_out[-3000:])
        return 2

    results = []

    def _safe(fn, label):
        """一组变异崩了 ⇒ 记一条失败，**不丢其它组的结果**"""
        try:
            return fn()
        except Exception as e:
            return [{"name": f"{label}（变异脚本自身异常）", "expect_rc": None,
                     "expect_kind": None, "actual_rc": None, "caught": False,
                     "restored": True, "detail": f"{type(e).__name__}: {e}"}]

    results += _safe(run_baseline_mutations, "基准侧变异组")
    results += _safe(run_missing_baseline_mutation, "基准缺失变异组")
    results += _safe(run_broken_probe_mutation, "探针破坏变异组")
    if not args.skip_java_side:
        results += _safe(run_java_side_mutation, "Java 侧变异组")

    ok = all(r["caught"] and r["restored"] for r in results)
    print("[mut] ============================================================")
    for r in results:
        mark = "✅" if r["caught"] else "❌"
        print(f"[mut] {mark} {r['name']}")
        print(f"[mut]      期望退出码 {r['expect_rc']}（{r['expect_kind']}）"
              f"｜实际 {r.get('actual_rc')}｜恢复={'✅' if r.get('restored') else '❌'}"
              f"｜{r.get('detail', '')}")
    n = sum(1 for r in results if r["caught"])
    print(f"[mut] 变异抓取率：{n}/{len(results)}")
    print(f"[mut] 真源字节恢复：{'✅ 全部恢复' if all(r['restored'] for r in results) else '❌ 有污染'}")
    print("[mut] ============================================================")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
