#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-go-json-body-predicate.py —— 「Go 是否读 body」这条判据**只能有一份实现**。

════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════
Go 侧的规则是 go-zero v1.10.2 的 `rest/httpx/requests.go:153-155`：

    func withJsonBody(r *http.Request) bool {
        return r.ContentLength > 0 && strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson)
    }

⇒ **字面子串匹配 + 大小写敏感 + 原始头值**，三个特征缺一不可。

RAG 侧的唯一真源是
`service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java`
的 `goReadsBody(...)`。

🔴 **但 2026-10-25 实测发现仓库里有 3 份「复制品」**（favorite / hot / article
各自抄了一份 `contentType.contains("application/json")`）⇒ 判据有 3 份实现。

**为什么这是跨域风险（不是三个域各自的小事）**：
同一个请求，两侧判据若不同步，就会出现「**闸门说不读、解析器说要读**」：

    Content-Type: Application/JSON
      ├─ 闸门（某域的复制品）      ⇒ 「不是 JSON body」⇒ 不读
      └─ 解析器（共享层唯一真源）  ⇒ 「是 JSON body」⇒ 读

⇒ 而且**没有任何测试会红**（两边各自都"自洽"）⇒ 典型的静默漂移。

✅ 2026-10-25 第十六轮：hot / article 两份已改为**调用**唯一真源
   （`GoStyleJsonBodyConverter.goReadsBody(HttpServletRequest)`）；
   **只剩 favorite 一份**（用户重申 favorite 零权限 ⇒ 该份永久残留，只能记账不能改）。

⇒ 本脚本守的不变量就是：**实现这份判据的文件集合 == {唯一真源} ∪ {白名单残留}**。
   白名单里多一个文件 ⇒ EXIT 1（有人又抄了一份）。
   白名单里少一个文件 ⇒ 提示（多半是 favorite 域已自行收敛，可以清白名单了）。

════════════════════════════════════════════════════════════════════════
【扫描口径】
════════════════════════════════════════════════════════════════════════
只认**判据形态**：一次 `contains(...)` 调用，其参数是
  ① 字面量 `"application/json"`，或
  ② 共享层的常量 `GO_JSON_CONTENT_TYPE_MARK`。
⇒ 响应侧的 `resp.setContentType("application/json")`、出站请求的
  `.header("Content-Type", "application/json")` **不算**（那是别的语义）。

扫描前**先剥掉注释**（块注释 + 行注释）⇒ javadoc 里提到这个字面量不会误报。

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
    python3 tools/difftest/check-go-json-body-predicate.py
    ⇒ EXIT=0 通过；EXIT=1 有复制品（或真源不见了）
"""
import pathlib
import re
import sys

# ── 唯一真源（判据的**实现**所在处）────────────────────────────────────────
TRUTH_SOURCE = "service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java"

# ── 已知的、**允许残留**的复制品 ───────────────────────────────────────────
# ⚠️ favorite 是用户明确划出的零权限域（2026-10-25 重申「你没有 favorite 的所有权限」）
#    ⇒ 这一份**改不了**，只能记账 + 守门。
#    ⚠️ 它当前取的是原始头值 ⇒ 判定仍与 Go 一致 ⇒ **当前行为无偏差**；
#       风险仅在于「未来有人把它改成 MediaType 解析」⇒ 那正是本脚本要抓的。
ALLOWED_RESIDUE = {
    "service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/controller/FavoriteController.java":
        "favorite 域零权限（2026-10-25 用户重申）⇒ 永久残留；修法交 favorite 域 owner",
}

# 判据形态：contains(<字面量 | 共享常量>)
PREDICATE_RE = re.compile(
    r"contains\s*\(\s*(?:\"application/json\"|GO_JSON_CONTENT_TYPE_MARK)\s*\)"
)


def strip_comments(src: str) -> str:
    """剥掉块注释与行注释 ⇒ javadoc / 注释里的字面量不参与判定。"""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    return src


def main() -> int:
    root = pathlib.Path(__file__).resolve().parents[2]
    hits: dict[str, list[int]] = {}

    for java_root in root.glob("service/**/src/main/java"):
        if not java_root.is_dir():
            continue
        for f in java_root.rglob("*.java"):
            try:
                src = f.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            code = strip_comments(src)
            lines = [i for i, line in enumerate(code.splitlines(), 1)
                     if PREDICATE_RE.search(line)]
            if lines:
                rel = str(f.relative_to(root))
                hits[rel] = lines

    # ① 真源必须在
    if TRUTH_SOURCE not in hits:
        print("=" * 78)
        print("  ✗ 「Go 是否读 body」判据的**唯一真源不见了**")
        print("=" * 78)
        print(f"  期望存在：{TRUTH_SOURCE}")
        print(f"  实际扫到：{sorted(hits) if hits else '（一个都没有）'}")
        print("  ⇒ 共享层可能被重构/改名 ⇒ 请同步更新本脚本的 TRUTH_SOURCE，")
        print("    并确认新位置仍然只实现这一份判据。")
        print()
        return 1

    # ② 真源之外只允许白名单里的残留
    unexpected = {f: v for f, v in hits.items()
                  if f != TRUTH_SOURCE and f not in ALLOWED_RESIDUE}
    stale = [f for f in ALLOWED_RESIDUE if f not in hits]

    if unexpected:
        print("=" * 78)
        print("  ✗ 发现新的「Go 是否读 body」判据**复制品**")
        print("=" * 78)
        for f, lines in sorted(unexpected.items()):
            print(f"  {f}")
            print(f"      行号：{lines}")
        print()
        print("  ⇒ 这条判据**只能有一份实现**（共享层唯一真源）。理由：")
        print("     判据一旦有多份，'闸门说不读 / 解析器说要读' 就会发生，")
        print("     而那种自相矛盾**没有任何测试会红**（各自都自洽）。")
        print()
        print("  ⇒ 修法：把该处改成调用唯一真源")
        print(f"       {TRUTH_SOURCE}")
        print("         GoStyleJsonBodyConverter.goReadsBody(request)")
        print("    （判据与「怎么从请求里取头值/长度」一起收口 ⇒ 连取输入都不会分叉）")
        print()
        print("  ⇒ 唯一真源与 Go 的对应关系：")
        print("     go-zero v1.10.2 rest/httpx/requests.go:153-155 withJsonBody")
        print("     = ContentLength > 0 && strings.Contains(原始 Content-Type, \"application/json\")")
        print("     ⚠️ 字面子串匹配 ⇒ 大小写敏感 ⇒ 'Application/JSON' 不命中 ⇒ Go 不读 body")
        print("=" * 78)
        print()
        return 1

    print("=" * 78)
    print("  ✓ 「Go 是否读 body」判据：只有一份实现（共享层唯一真源）")
    print("=" * 78)
    print(f"  唯一真源：{TRUTH_SOURCE}")
    print(f"            行号：{hits[TRUTH_SOURCE]}")
    if stale:
        print()
        print("  ℹ️ 白名单里以下文件已**不再**实现这份判据（多半是各域已自行收敛）：")
        for f in stale:
            print(f"     {f}")
            print(f"     原由：{ALLOWED_RESIDUE[f]}")
        print("     ⇒ 可以把它们从 ALLOWED_RESIDUE 里清掉了。")
    else:
        print()
        print(f"  已知残留 {len(ALLOWED_RESIDUE)} 处（favorite 域零权限 ⇒ 永久残留）：")
        for f, why in ALLOWED_RESIDUE.items():
            print(f"     {f}")
            print(f"       行号：{hits.get(f, [])}")
            print(f"       原因：{why}")
        print("     ⇒ 该份当前取**原始头值** ⇒ 判定仍与 Go 一致 ⇒ 当前行为无偏差；")
        print("       风险仅在于「未来有人把它改成 MediaType 解析」⇒ 那正是本脚本要抓的。")
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
