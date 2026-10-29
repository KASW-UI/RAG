#!/usr/bin/env python3
"""扫描 **bash 3.2 变量名吞多字节字符** 这一类真 bug（macOS 自带 bash）。

**它是什么**
----------
`$VAR` 后面**紧跟**一个多字节字符（如中文全角括号 `）` `（`、全角逗号 `，`）时，
**macOS 自带 bash 3.2** 会把那个多字节字符**算进变量名** ⇒
变量名变成 `VAR\xef\xbc\x89` ⇒ 在 `set -u` 下立刻报
`VAR?: unbound variable` ⇒ **脚本在那一行崩掉**。

**为什么它特别阴**
-----------------
① **只在出错路径/收尾分支崩** —— 正常路径不碰那行 ⇒ 平时看不出来；
   一旦真出问题，报错信息**恰恰打不出来** ⇒ 守门**用错误的姿势失败**。
② **在 macOS 上才犯**（Linux 的 bash 5 会正确界定变量名边界）⇒
   **CI 与开发机行为不一致** ⇒ 「我这儿是绿的」。
③ **同源实例**：
   - `tools/difftest/check-jar-executable.sh:114` —— **20/20 全过却 EXIT=1**（假红），
     2026-10-29 R4 轮实测并修复。
   - `tools/difftest/check-proto-static.sh:158` —— 「文件缺失」错误路径崩掉，报错打不出来。
   - `service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh:127` —— 探针收尾行崩掉
     （**hot 域写权面** ⇒ 已派单 R5-F10）。

**为什么要有这个扫描器**
----------------------
这类 bug **不能靠 review 肉眼发现**（它长得像一句正常的 echo）⇒
⇒ 必须机器扫。而它**不是** `check-*.py` / `check-*.sh` ⇒
**不计入 `docs/PhaseGoal-judge.md` §六 6.3 的守门计数（27 个）** ⇒ 不动口径。

**用法**
----
    python3 tools/difftest/scan-bash-legacy-vars.py            # 全仓扫描
    python3 tools/difftest/scan-bash-legacy-vars.py --verbose  # 逐条打印

**退出码**
--------
* **0** = 命中 0 处 **活代码**（注释行不算）
* **1** = 命中 ≥1 处活代码 ⇒ **需要修**
* **2** = 输入不可得（仓根找不到）⇒ **硬失败，不静默降级**
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

# `$VAR` 后面紧跟非 ASCII 字节
PAT = re.compile(r"\$[A-Za-z_][A-Za-z0-9_]*(?=[^\x00-\x7F])")

# 注释行 / 纯 heredoc 说明行不算「活代码」—— 但**不能只看行首 `#`**，
# 因为脚本里常见「代码行尾部跟注释」；这里采取**保守**口径：
# 只要该行**去掉引号内的内容后**仍命中，就算活代码。
# 🔴 **只剥单引号字符串**（bash 里单引号内**不**展开变量）；
#    **双引号内照样展开** ⇒ 双引号里的 `$VAR` **必须保留**参与扫描。
#    （第一版这里把双引号也剥了 ⇒ 对 `echo "… $VAR）"` **全部漏报** ⇒
#      拿已知错例（hot 探针第 127 行）反测才抓出来 —— 判据自身先被反测打脸。）
SQUOTE_RE = re.compile(r"'[^']*'")


def is_comment(line: str) -> bool:
    s = line.lstrip()
    return s.startswith("#")


def scan_file(path: pathlib.Path) -> list[tuple[int, str]]:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return []
    hits: list[tuple[int, str]] = []
    in_heredoc = False
    heredoc_end = ""
    for i, raw in enumerate(text.splitlines(), 1):
        line = raw.rstrip("\n")
        if in_heredoc:
            if line.strip() == heredoc_end:
                in_heredoc = False
            continue
        m = re.search(r"<<-?\s*'([A-Za-z_][A-Za-z0-9_]*)'", line)
        if m:
            in_heredoc = True
            heredoc_end = m.group(1)
            continue
        if is_comment(line):
            continue
        # 去掉字符串字面量（里面的 `$VAR` 不是变量引用）
        stripped = SQUOTE_RE.sub("''", line)
        if PAT.search(stripped):
            hits.append((i, line.strip()))
    return hits


def main() -> int:
    if not (ROOT / "service").is_dir() or not (ROOT / "tools").is_dir():
        print("❌ 输入不可得：仓根结构异常（找不到 service/ 或 tools/）⇒ exit 2", file=sys.stderr)
        return 2

    verbose = "--verbose" in sys.argv
    total = 0
    for p in sorted(ROOT.rglob("*.sh")):
        rel = p.relative_to(ROOT)
        if any(part in ("target", "node_modules", ".git") for part in rel.parts):
            continue
        hits = scan_file(p)
        if not hits:
            continue
        total += len(hits)
        print(f"🔴 {rel} —— {len(hits)} 处")
        for i, line in hits:
            print(f"     {rel}:{i}: {line[:140]}")
            if not verbose:
                break  # 默认每文件只打第一处 ⇒ 输出不至于淹没
    print()
    if total:
        print(f"❌ 命中 {total} 处「裸 $VAR 后紧跟多字节字符」⇒ bash 3.2 上会 unbound variable")
        return 1
    print("✅ 0 处命中（bash 3.2 变量名边界安全）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
