#!/usr/bin/env python3
"""变异测试 · 派生层「状态列」判据（2026-10-31 · 合并收口执行者）

**被测对象**：`tools/ledger-derive/derive-stage1.py` 的 `detect_status` ——
「表头有『状态』列 ⇒ 当前状态从那一列读（不再按整行取最早标记）」。

**为什么需要这道变异**（AGENTS.md §3.3.3 + 派工面纪律）：
  自造判据必须被**自己的**变异测试证伪过 ⇒ 否则「它有牙」只是一句断言。
  ⇒ 本脚本改**源台账**、重跑派生、比对目标行的 `status` ⇒ **判据失灵 ⇒ 必红**。

**测的四个断言**（含 1 条阴性对照）：
  C0 · **隔离树保真** —— 隔离树的派生规模必须与主干**逐项相等**
      （否则 = 读的不是同一棵树 ⇒ 后续断言全无意义 ⇒ 「零信号」）。
  M1 · **判据有牙** —— 把 E20 状态列的 `✅ 已修` 改成 `🔴 仍开`
      ⇒ 派生 `status` 必须从 `✅ 已一致` 翻成 `🔴 未闭合`
      ⇒ 证伪「判据其实没在读状态列」。
  M2 · **阴性对照** —— 只动 E20 的**条目格**（去掉 `🔴` 问题标签）· 状态列不动
      ⇒ 派生 `status` 必须**保持** `✅ 已一致`（**不变**）
      ⇒ 证伪「判据其实在读条目格」。
  M3 · **护栏有牙（防假绿）** —— 在 E20 状态列**末尾**追加一个 `🔴`
      ⇒ 派生 `status` 必须翻成 `🔴 未闭合`
      ⇒ 证伪「护栏是恒定保守 / 判据会把半闭合条目读成已一致」。

**隔离树做法**（避免在真仓落任何东西）：
  `docs/` + `tools/` 用**硬链接**整树复制（快、不占空间）⇒
  ⚠️ 硬链接 ⇒ **就地写会改到真仓文件** ⇒ 故：
    ① 待变异的源台账：先 `os.remove`（只删链接）再写新内容；
    ② 派生产物 `stage1-derivation.{tsv,md}`：先 `os.remove`（只删链接）
       ⇒ 派生脚本在隔离树里**新建** ⇒ 真仓产物零改动。

**退出码**：0 = 四个断言全部符合预期；1 = 有断言失配（判据没牙 / 判据误伤）。
"""
from __future__ import annotations

import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
DERIVE = ROOT / "tools" / "ledger-derive" / "derive-stage1.py"
TARGET_REL = pathlib.Path("docs/DRIFT/global/_README.md")
TARGET_ID = "E20"
TSV_REL = pathlib.Path("docs/DRIFT/_cross/stage1-derivation.tsv")
MD_REL = pathlib.Path("docs/DRIFT/_cross/stage1-derivation.md")


# ── 隔离树 ──────────────────────────────────────────────────────────────
def build_iso() -> pathlib.Path:
    iso = pathlib.Path(tempfile.mkdtemp(prefix="derive-statuscol-"))
    for sub in ("docs", "tools"):
        shutil.copytree(ROOT / sub, iso / sub, dirs_exist_ok=True,
                        copy_function=os.link)
    # ⚠️ 硬链接 ⇒ 派生产物必须**先删链接**再让脚本新建 ⇒ 否则真仓产物被改写
    for rel in (TSV_REL, MD_REL):
        p = iso / rel
        if p.exists():
            os.remove(p)
    return iso


def read_target(iso: pathlib.Path) -> str:
    p = iso / TARGET_REL
    text = p.read_text(encoding="utf-8")
    for line in text.split("\n"):
        if line.startswith(f"| **{TARGET_ID}** |"):
            return line
    raise SystemExit(f"❌ 隔离树里找不到 `{TARGET_ID}` 行")


def derive(iso: pathlib.Path) -> dict[str, str]:
    """在隔离树里重跑派生 ⇒ 返回 {id: status}（取 global 域的 E20 行）。"""
    r = subprocess.run([sys.executable, str(iso / "tools/ledger-derive/derive-stage1.py")],
                       cwd=iso, capture_output=True, text=True)
    if r.returncode not in (0, 1):        # 1 = 有漂移/缺口，正常
        sys.stderr.write(r.stdout[-3000:] + "\n" + r.stderr[-3000:] + "\n")
        raise SystemExit(f"❌ 派生脚本异常退出：{r.returncode}")
    tsv = iso / TSV_REL
    if not tsv.exists():
        raise SystemExit("❌ 派生产物未生成")
    out: dict[str, str] = {}
    lines = tsv.read_text(encoding="utf-8").split("\n")
    hdr = lines[0].split("\t")
    ix, isx = hdr.index("id"), hdr.index("status")
    for line in lines[1:]:
        if not line.strip():
            continue
        cs = line.split("\t")
        if len(cs) > max(ix, isx) and cs[ix] == TARGET_ID:
            out[cs[ix]] = cs[isx]
    return out


def count_rows(iso: pathlib.Path) -> int:
    tsv = iso / TSV_REL
    return len([l for l in tsv.read_text(encoding="utf-8").split("\n") if l.strip()]) - 1


def mutate(iso: pathlib.Path, old: str, new: str) -> None:
    p = iso / TARGET_REL
    text = p.read_text(encoding="utf-8")
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"❌ 变异锚点命中 {n} 次（要求恰好 1 次）：{old[:60]!r}")
    os.remove(p)                          # ⚠️ 先删硬链接 ⇒ 写入不碰真仓
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> int:
    if not DERIVE.exists():
        sys.stderr.write("❌ 找不到 derive-stage1.py\n")
        return 1
    iso = build_iso()
    fails: list[str] = []
    try:
        # ── 基线 ──
        base = derive(iso)
        base_status = base.get(TARGET_ID, "")
        base_rows = count_rows(iso)
        main_rows = len([l for l in (ROOT / TSV_REL).read_text(encoding="utf-8").split("\n")
                         if l.strip()]) - 1
        print(f"· 基线：{TARGET_ID} status = {base_status!r} · 派生行数 = {base_rows}（主干 {main_rows}）")
        if base_rows != main_rows:
            fails.append(f"C0 隔离树保真失配：隔离树 {base_rows} 行 ≠ 主干 {main_rows} 行")
        if base_status != "✅ 已一致":
            fails.append(f"C0 基线状态不是「✅ 已一致」（实际 {base_status!r}）⇒ 判据没生效或已被改掉")

        # ── M1 判据有牙 ──
        iso2 = build_iso()
        try:
            mutate(iso2, "| ✅ **已修**（L12 修 5 处 + L13 修 2 处）",
                   "| 🔴 **仍开**（L12 修 5 处 + L13 修 2 处）")
            st = derive(iso2).get(TARGET_ID, "")
            print(f"· M1（状态列改 🔴）⇒ status = {st!r}")
            if st != "🔴 未闭合":
                fails.append(f"M1 失配：状态列改成 🔴 后派生仍是 {st!r} ⇒ 判据没在读状态列")
        finally:
            shutil.rmtree(iso2, ignore_errors=True)

        # ── M2 阴性对照（只动条目格） ──
        iso3 = build_iso()
        try:
            mutate(iso3, "| **E20** | 🔴 **N6**：", "| **E20** | **N6**：")
            st = derive(iso3).get(TARGET_ID, "")
            print(f"· M2（只动条目格）⇒ status = {st!r}")
            if st != base_status:
                fails.append(f"M2 失配：只动条目格却把状态改成了 {st!r}（应为 {base_status!r}）⇒ 判据在读条目格")
        finally:
            shutil.rmtree(iso3, ignore_errors=True)

        # ── M3 护栏有牙（末尾追加 🔴 ⇒ 必须回退 ⇒ 防假绿） ──
        iso4 = build_iso()
        try:
            mutate(iso4, "| ✅ **已修**（L12 修 5 处 + L13 修 2 处）",
                   "| ✅ **已修**（L12 修 5 处 + L13 修 2 处）⇒ 🔴 **末尾追加**")
            st = derive(iso4).get(TARGET_ID, "")
            print(f"· M3（状态列末尾追加 🔴）⇒ status = {st!r}")
            if st != "🔴 未闭合":
                fails.append(f"M3 失配：状态列以 🔴 收尾却仍读成 {st!r} ⇒ 护栏失灵 ⇒ 会有假绿")
        finally:
            shutil.rmtree(iso4, ignore_errors=True)
    finally:
        shutil.rmtree(iso, ignore_errors=True)

    print()
    if fails:
        for f in fails:
            print(f"❌ {f}")
        return 1
    print("✅ 四个断言全部符合预期（C0 保真 · M1 判据有牙 · M2 阴性对照不误判 · M3 护栏防假绿）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
