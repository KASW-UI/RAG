#!/usr/bin/env python3
"""守门：`AGENTS.md` 与 `docs/**/*.md` 的**链接与锚点必须可达**。

背景
----
2026-10-27 实测：`AGENTS.md` + `docs/` 共 **1042** 个 markdown 链接
⇒ **23 个文件链接失效** ＋ **13 个标题锚点失效**（修前）。
其中：
- 1 个是本会话自己引入的（`global/lanes/_PROTOCOL.md` 的 `../../../_cross/` 少算一级）；
- 其余是**长期存在**的坏链（`../../../../tools/` 多算一级 · `users/user/x.md` 写成了
  `users/users/user/x.md` · `../../_shared.md` 少算一级 ……）。

⚠️ **为什么这条要机器守**：AGENTS.md 是**每个 agent 自动加载**的根政策文件 ⇒
一条坏链 = **每个 agent 都会走错路**，而且**肉眼看不出来**（链接文字是好的，路径是错的）。
⇒ 同源「不可复算的断言 = 假保证」：链接坏没坏，**必须机器判**。

查什么
------
1. **文件链接可达**：`[文字](相对路径)` ⇒ 目标文件必须存在（`http(s)`/`mailto:` 跳过）。
2. **标题锚点可达**：`[文字](文件.md#锚点)` ⇒ 目标文件里必须真有那个标题。
   - 锚点按 **GitHub 的 slug 规则**算：lower-case ⇒ 去标点（保留字母/数字/`-`/`_`/emoji）⇒ 空格转 `-`。
   - `#L67-L78` 这种**行号锚点**是 GitHub 合法语法 ⇒ 跳过（无法静态判定）。
3. **占位/错字链接**：`[..](path.md)` / `[text](h)` 这类**同目录不存在的短路径**单独点名。

退出码
------
0 = 全部可达；1 = 有失效（逐条点名文件 + 链接）。

复现
----
python3 tools/check-doc-links.py
python3 tools/check-doc-links.py --verbose   # 额外打印统计
"""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
LINK_RE = re.compile(r"\[([^\]]*)\]\(([^)]+)\)")
HEADING_RE = re.compile(r"^#{1,6}\s+(.+)$", re.M)


def slug(heading: str) -> str:
    """GitHub 的 heading slug 规则（近似）。"""
    out: list[str] = []
    for ch in heading.strip().lower():
        if ch in "-_ ":
            out.append(ch)
            continue
        cat = unicodedata.category(ch)
        if cat[0] in "LN" or cat == "So":  # 字母/数字/组合记号/emoji
            out.append(ch)
    return "".join(out).strip().replace(" ", "-")


def _clean(h: str) -> str:
    return re.sub(r"[`*]", "", h).strip()


def anchors_of(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    out: set[str] = set()
    for h in HEADING_RE.findall(text):
        out.add(slug(h))
        out.add(slug(_clean(h)))
    return out


def target_files() -> list[Path]:
    return [REPO / "AGENTS.md"] + sorted((REPO / "docs").rglob("*.md"))


def main() -> int:
    verbose = "--verbose" in sys.argv[1:]
    files = target_files()
    bad_file: list[tuple[str, str]] = []
    bad_anchor: list[tuple[str, str]] = []
    n_links = n_anchor = 0

    for f in files:
        text = f.read_text(encoding="utf-8", errors="ignore")
        for _label, target in LINK_RE.findall(text):
            if target.startswith(("http://", "https://", "mailto:")):
                continue
            n_links += 1
            path, _, frag = target.partition("#")
            resolved = f.resolve() if not path else (
                (f.parent / path).resolve() if not path.startswith("/") else Path(path)
            )
            if path and not resolved.exists():
                bad_file.append((f.relative_to(REPO).as_posix(), target))
                continue
            if not frag or resolved.suffix.lower() != ".md":
                continue
            if re.match(r"^L\d+", frag):  # GitHub 行号锚点 ⇒ 静态不可判
                continue
            n_anchor += 1
            if slug(frag) not in anchors_of(resolved):
                bad_anchor.append((f.relative_to(REPO).as_posix(), target))

    print("文档链接守门")
    print("=" * 60)
    print(f"  扫描文件: {len(files)} 份（AGENTS.md + docs/**.md）")
    print(f"  markdown 链接: {n_links} 个（其中带标题锚点 {n_anchor} 个）")
    print("-" * 60)
    if verbose:
        for f in sorted(files):
            print(f"    {f.relative_to(REPO).as_posix()}")
    if bad_file or bad_anchor:
        print("❌ FAIL\n")
        for f, t in bad_file:
            print(f"🔴 文件链接失效：`{f}` → `{t}`")
        for f, t in bad_anchor:
            print(f"🔴 锚点失效：`{f}` → `{t}`")
        print()
        print(f"⇒ {len(bad_file) + len(bad_anchor)} 项失效")
        return 1
    print("✅ PASS")
    print("  · 全部文件链接可达")
    print("  · 全部标题锚点命中真实标题（GitHub slug 规则）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
