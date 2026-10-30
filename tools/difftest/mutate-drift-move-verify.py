#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mutate-drift-move-verify.py —— `tools/drift-move.py verify` 的**变异测试**。

⚠️ **为什么必须有这个脚本**：`drift-move.py` 的文件抬头自己写着 ——
  「⚠️ ② 只锁『搬移块自登记以来没被改过』…… ⇒ ⇒ **必须配变异测试**
   （改目标文件里的搬移块 ⇒ 守门必红），否则就是再造一个恒绿假绿」
⇒ ⇒ 本脚本就是那份承诺的兑现。

【它证明什么】
  ① **基线绿**：影子副本未被改动 ⇒ `verify` ⇒ `EXIT=0`；
  ② **变异 A 必红**：改动**某个搬移块内部**的一行 ⇒ `verify` ⇒ `EXIT≠0`
     （且报的是「内容指纹不符」，不是别的错）；
  ③ **变异 B 必红**：删掉**某个哨兵** ⇒ `verify` ⇒ `EXIT≠0`
     （且报的是「找不到标签哨兵」）；
  ④ **变异 C 必红**：改动**另一个文件**的搬移块 ⇒ 仍必红
     ⇒ ⇒ 证明判据**不是**写死在某一个文件/某一段上；
  ⑤ **不误报**：在搬移块**之外**追加内容 ⇒ 仍 `EXIT=0`
     ⇒ ⇒ 证明判据**只盯搬移块**，不会变成噪声源。

【口径】
  · 一律在**影子副本**上做（`--root`），**不动真仓**；
  · 每一步都用「真命令 + 真退出码」判定，**不解析输出文本当判据**；
  · 期望值写死在本脚本里 ⇒ ⇒ 改判据时**必须**同步改这里（否则脚本会红）。

用法：
    python3 tools/difftest/mutate-drift-move-verify.py
"""
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "drift-move.py"
MANIFEST_REL = "docs/DRIFT/_MANIFEST.json"


def build_shadow(dst_root: pathlib.Path) -> dict:
    """把 manifest ＋ 它引用的全部目标文件复制到影子根（只读真仓）。"""
    manifest = json.loads((ROOT / MANIFEST_REL).read_text(encoding="utf-8"))
    (dst_root / "docs" / "DRIFT").mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / MANIFEST_REL, dst_root / MANIFEST_REL)
    for e in manifest["entries"]:
        src = ROOT / e["dst"]
        dst = dst_root / e["dst"]
        dst.parent.mkdir(parents=True, exist_ok=True)
        if src.exists():
            shutil.copy2(src, dst)
    return manifest


def run_verify(shadow: pathlib.Path) -> tuple[int, str]:
    p = subprocess.run(
        [sys.executable, str(TOOL), "verify", "--root", str(shadow)],
        capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def expect_red(name: str, shadow: pathlib.Path, phrase: str, failures: list) -> None:
    """期望 verify **红**，且报的是指定的那句话。

    ⚠️ **「崩了」不等于「判据红」** —— 脚本抛异常时退出码也是非 0
    ⇒ ⇒ 若不把 traceback 单独判掉，一个「因为缺文件而崩」的影子副本
    ⇒ ⇒ 会被读成「守门成功抓到了漂移」⇒ ⇒ **恒绿假绿的近亲**。
    ⇒ ⇒ 这里显式区分三种结局：**崩溃** / **判据红** / **假绿**。
    """
    rc, out = run_verify(shadow)
    if "Traceback" in out:
        failures.append(name)
        print(f"   ❌ {name}  —— **脚本崩溃**（不是判据红）\n{out[-600:]}")
        return
    if rc == 0:
        failures.append(name)
        print(f"   ❌ {name}  —— 实际 EXIT=0（**假绿**）")
        return
    if phrase and phrase not in out:
        failures.append(name)
        print(f"   ❌ {name}  —— 红了，但报的不是「{phrase}」\n{out[-600:]}")
        return
    print(f"   ✅ {name}")


def mutate_block_line(path: pathlib.Path, label: str) -> bool:
    """改动某个搬移块**内部**的一行（找一个含 label 的哨兵，在它之后改一行非空内容）。"""
    lines = path.read_text(encoding="utf-8").split("\n")
    idx = -1
    for i, l in enumerate(lines):
        if l.strip().startswith("<!-- @move") and label in l:
            idx = i
            break
    if idx < 0:
        return False
    for j in range(idx + 1, min(idx + 12, len(lines))):
        if lines[j].strip():
            lines[j] = lines[j] + "  <!-- MUTATED-BY-mutate-drift-move-verify -->"
            path.write_text("\n".join(lines), encoding="utf-8")
            return True
    return False


def drop_sentinel(path: pathlib.Path, label: str) -> bool:
    """删掉某个哨兵行。"""
    lines = path.read_text(encoding="utf-8").split("\n")
    for i, l in enumerate(lines):
        if l.strip().startswith("<!-- @move") and label in l:
            del lines[i]
            path.write_text("\n".join(lines), encoding="utf-8")
            return True
    return False


def append_outside(path: pathlib.Path) -> None:
    """在文件**末尾**追加一行（搬移块之外）⇒ 判据应当**不**变红。"""
    with path.open("a", encoding="utf-8") as f:
        f.write("\n<!-- APPENDED-BY-mutate-drift-move-verify (块外) -->\n")


def main() -> int:
    failures = []

    def case(name: str, ok: bool, detail: str = "") -> None:
        mark = "✅" if ok else "❌"
        print(f"   {mark} {name}" + (f"  —— {detail}" if detail and not ok else ""))
        if not ok:
            failures.append(name)

    # ── ① 基线：影子副本未改动 ⇒ 必须绿 ──
    shadow = pathlib.Path(tempfile.mkdtemp(prefix="driftmove_mut_"))
    try:
        build_shadow(shadow)
        rc, out = run_verify(shadow)
        if "Traceback" in out:
            case("① 基线：影子副本未改动 ⇒ verify EXIT=0", False, f"脚本崩溃\n{out[-600:]}")
        else:
            case("① 基线：影子副本未改动 ⇒ verify EXIT=0", rc == 0, f"实际 EXIT={rc}\n{out[-800:]}")

        # ── ② 变异 A：改搬移块内部一行 ⇒ 必须红，且报「内容指纹不符」 ──
        man = json.loads((shadow / MANIFEST_REL).read_text(encoding="utf-8"))
        target = None
        for e in man["entries"]:
            p = shadow / e["dst"]
            if p.exists() and e["dst"].endswith("users/_shared.md"):
                target = (e, p)
                break
        if target is None:
            case("② 变异 A：找到可变的搬移块", False, "没找到目标段")
        else:
            e, p = target
            ok = mutate_block_line(p, e["label"])
            case("② 变异 A：改动搬移块内部一行", ok, "没能改动目标块")
            expect_red("② 变异 A ⇒ verify 必红（报「指纹不符」）", shadow, "指纹不符", failures)

        # ── ③ 变异 B：删哨兵 ⇒ 必须红，且报「找不到标签哨兵」 ──
        shadow2 = pathlib.Path(tempfile.mkdtemp(prefix="driftmove_mut_"))
        try:
            build_shadow(shadow2)
            man2 = json.loads((shadow2 / MANIFEST_REL).read_text(encoding="utf-8"))
            tgt2 = None
            for e in man2["entries"]:
                p = shadow2 / e["dst"]
                if p.exists() and e["dst"].endswith("hot/rpc.md"):
                    tgt2 = (e, p)
                    break
            if tgt2 is None:
                case("③ 变异 B：找到可删哨兵的段", False, "没找到目标段")
            else:
                e, p = tgt2
                ok = drop_sentinel(p, e["label"])
                case("③ 变异 B：删掉一个哨兵", ok, "没能删掉哨兵")
                expect_red("③ 变异 B ⇒ verify 必红（报「找不到标签哨兵」）", shadow2, "哨兵", failures)
        finally:
            shutil.rmtree(shadow2, ignore_errors=True)

        # ── ④ 变异 C：换另一个文件 ⇒ 仍必红（证明判据没写死在一个文件上） ──
        shadow3 = pathlib.Path(tempfile.mkdtemp(prefix="driftmove_mut_"))
        try:
            build_shadow(shadow3)
            man3 = json.loads((shadow3 / MANIFEST_REL).read_text(encoding="utf-8"))
            tgt3 = None
            for e in man3["entries"]:
                p = shadow3 / e["dst"]
                if p.exists() and e["dst"].endswith("like/rpc.md"):
                    tgt3 = (e, p)
                    break
            if tgt3 is None:
                case("④ 变异 C：找到可变的搬移块", False, "没找到目标段")
            else:
                e, p = tgt3
                ok = mutate_block_line(p, e["label"])
                case("④ 变异 C：改动另一个文件的搬移块", ok, "没能改动目标块")
                expect_red("④ 变异 C ⇒ verify 必红（换文件也必红）", shadow3, "指纹不符", failures)
        finally:
            shutil.rmtree(shadow3, ignore_errors=True)

        # ── ⑤ 块外追加 ⇒ 不得变红（判据只盯搬移块，不制造噪声） ──
        shadow4 = pathlib.Path(tempfile.mkdtemp(prefix="driftmove_mut_"))
        try:
            build_shadow(shadow4)
            append_outside(shadow4 / "docs/DRIFT/hot/common.md")
            rc, out = run_verify(shadow4)
            case("⑤ 块外追加内容 ⇒ verify 仍 EXIT=0（不误报）", rc == 0, f"实际 EXIT={rc}\n{out[-500:]}")
        finally:
            shutil.rmtree(shadow4, ignore_errors=True)
    finally:
        shutil.rmtree(shadow, ignore_errors=True)

    print()
    if failures:
        print(f"❌ 变异测试失败 {len(failures)} 项：{failures}")
        return 1
    print("✅ 变异测试全过 ⇒ `drift-move.py verify` 的判据**有牙**（不是恒绿假绿）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
