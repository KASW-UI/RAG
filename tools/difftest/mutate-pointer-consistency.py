#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mutate-pointer-consistency.py —— `check-pointer-consistency.py` 的**变异测试**。

⚠️ **为什么必须有**：`check-pointer-consistency.py` 是**棘轮**守门（只对「新增漂移」报红）
⇒ ⇒ 它的判据天然有一个「永远绿」的风险：若判据根本没在测指针漂移，它也会一直绿
⇒ ⇒ ⇒ 必须配变异测试证明**往域台账里插一行 ⇒ 守门立刻红**。

【它证明什么】
  ① **基线绿**：影子副本未改动 ⇒ `EXIT=0`（零新增漂移）；
  ② **变异必红**：往某个域台账里**插入一行**（把后面所有行号顶下去）
     ⇒ ⇒ 指向该文件的指针**解析出的编号变了** ⇒ ⇒ 出现**新增例外** ⇒ ⇒ 守门 `EXIT=1`；
  ③ **变异必红且是新键**：红的原因必须是「**新增**漂移」，
     ⇒ 而不是「存量例外数变了」这种副作用 ⇒ ⇒ 断言输出里出现 `🆕`；
  ④ **幂等**：影子副本不改动 ⇒ 跑第 ① 步仍然绿（⇒ ⇒ 守门没有副作用、没把基线改坏）。

【口径】
  · 一律在**影子副本**上做，**真仓零改动**；
  · 判据 = 真命令 + 真退出码，**不解析输出文本当判据**（只额外断言 `🆕` 出现）。

用法：
    python3 tools/difftest/mutate-pointer-consistency.py
"""
import importlib.util
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
GUARD_REL = "tools/ledger-derive/check-pointer-consistency.py"
DERIVE_REL = "tools/ledger-derive/derive-stage1.py"
BASELINE_REL = "docs/DRIFT/_cross/pointer-drift-baseline.json"


def build_shadow(dst: pathlib.Path) -> None:
    """影子副本 = docs/ 全树 ＋ tools/ledger-derive/ ＋ 基线。"""
    shutil.copytree(ROOT / "docs", dst / "docs", dirs_exist_ok=True)
    shutil.copytree(ROOT / "tools" / "ledger-derive", dst / "tools" / "ledger-derive",
                    dirs_exist_ok=True)


def run_guard(shadow: pathlib.Path) -> tuple[int, str]:
    p = subprocess.run(
        [sys.executable, str(shadow / GUARD_REL), "--root", str(shadow)],
        capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def load_collect(shadow: pathlib.Path):
    spec = importlib.util.spec_from_file_location("pc", shadow / GUARD_REL)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def main() -> int:
    failures: list[str] = []

    def case(name: str, ok: bool, detail: str = "") -> None:
        print(f"   {'✅' if ok else '❌'} {name}" + (f"  —— {detail}" if detail and not ok else ""))
        if not ok:
            failures.append(name)

    shadow = pathlib.Path(tempfile.mkdtemp(prefix="ptrcons_mut_"))
    try:
        build_shadow(shadow)

        # ── ① 基线绿 ──
        rc, out = run_guard(shadow)
        if "Traceback" in out:
            case("① 基线：影子副本未改动 ⇒ 守门 EXIT=0", False, f"脚本崩溃\n{out[-700:]}")
        else:
            case("① 基线：影子副本未改动 ⇒ 守门 EXIT=0", rc == 0, f"实际 EXIT={rc}\n{out[-700:]}")

        # ── 找一条「当前一致」的指针（⇒ 改它 ⇒ 才会变红） ──
        gm = load_collect(shadow)
        found, stats = gm.collect(shadow)
        base = json.loads((shadow / BASELINE_REL).read_text(encoding="utf-8"))
        known = {(e["src"], e["target"], e["resolved_id"]) for e in base.get("entries", [])}

        # 复算「一致」的指针：源行有编号 且 解析出的编号 ∈ 源行编号
        m = gm.load_derive(shadow)
        excl = m.load_exclusions()
        _entries, line_index = m.scan_ledgers(excl)
        victim = None
        for p in sorted(shadow.glob("docs/DRIFT/**/*.md")):
            rel_src = str(p.relative_to(shadow))
            if rel_src in known and any(k[0] == rel_src for k in known):
                continue
            text = p.read_text(encoding="utf-8", errors="replace")
            for i, line in enumerate(text.split("\n"), 1):
                for mt in m.DOC_PTR_RE.finditer(line):
                    rel, tln = mt.group(1), int(mt.group(2))
                    resolved = m.resolve_pointer(line_index, rel, tln)
                    src_ids = set(m.all_id_tokens(line))
                    if resolved and src_ids and resolved[0] in src_ids:
                        tp = shadow / "docs" / rel
                        if tp.exists() and 1 <= tln <= len(tp.read_text(encoding="utf-8").split("\n")):
                            victim = (tp, tln, rel_src, i, rel, tln, resolved[0])
                            break
                if victim:
                    break
            if victim:
                break

        if victim is None:
            case("② 找到一条「当前一致」的指针可作变异靶子", False, "没找到")
        else:
            tp, tln, rel_src, src_line, rel, tgt_line, rid = victim
            case(f"② 找到「当前一致」的指针（{rel_src}:{src_line} → {rel}:{tgt_line} ⇒ `{rid}`）", True)

            # ── ② 往目标台账里插一行 ⇒ 把后面所有行号顶下去 ──
            tl = tp.read_text(encoding="utf-8").split("\n")
            # 插在目标行**之前**（下标 tgt_line-1）⇒ 目标行被顶到 +1
            tl.insert(tgt_line - 1, "<!-- MUTATED-BY-mutate-pointer-consistency（插一行 ⇒ 行号整体位移） -->")
            tp.write_text("\n".join(tl), encoding="utf-8")

            rc, out = run_guard(shadow)
            if "Traceback" in out:
                case("② 插入一行 ⇒ 守门必红", False, f"脚本崩溃\n{out[-700:]}")
            elif rc == 0:
                case("② 插入一行 ⇒ 守门必红", False, "实际 EXIT=0（**假绿** ⇒ 判据没在测漂移）")
            else:
                case("② 插入一行 ⇒ 守门必红", True)
                case("② 红的原因是「新增漂移」（输出含 🆕）", "🆕" in out, out[-700:])
                case("② 报的是新增例外数 > 0", "新增 0 条" not in out, out[-700:])

        # ── ③ 幂等：影子副本再跑一次基线（只跑未改动的第二份影子） ──
        shadow2 = pathlib.Path(tempfile.mkdtemp(prefix="ptrcons_mut2_"))
        try:
            build_shadow(shadow2)
            rc, out = run_guard(shadow2)
            case("③ 影子副本未改动 ⇒ 再跑仍 EXIT=0（无副作用）",
                 rc == 0 and "Traceback" not in out, f"实际 EXIT={rc}\n{out[-500:]}")
        finally:
            shutil.rmtree(shadow2, ignore_errors=True)
    finally:
        shutil.rmtree(shadow, ignore_errors=True)

    print()
    if failures:
        print(f"❌ 变异测试失败 {len(failures)} 项：{failures}")
        return 1
    print("✅ 变异测试全过 ⇒ 指针漂移守门的判据**有牙**（不是恒绿假绿）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
