#!/usr/bin/env python3
"""comment 域 proto 的**复制忠实度**守门脚本（域内自用，不进共享 tools/difftest/）。

为什么需要它
------------
AGENTS.md §3.4 说 proto 是**复制粘贴**而不是翻译 ⇒ 行为层（message 名 / 字段名 / 字段号 /
类型 / 枚举值 / service 与 rpc 签名）必须**逐条一致**，只有 option 块与 package 允许不同。

原先这条只是**一次性快照**（2026-10-24 首轮对照时人工 diff 过一次）⇒ 任何人对 proto 动过手，
**没有任何东西会报警**。本脚本把它变成**可持续结论**：改动 proto 后跑一次即可。

判定口径（与 docs/DRIFT/comment/proto.md §1 一致）
--------------------------------------------------
1. 剥掉 `option ...` 行与 `package ...;` 行 ⇒ **逐行 diff 必须为空**（行为层）；
2. `package` 必须**不等**（Go 是 `pb`，RAG 是 `comment`）—— 这是**已签字偏离**
   `SD-2026-10-23-04`，**不得**被"顺手改回去"；
3. RAG 侧**不得**残留 `option go_package`（那是 Go 专用项，Java 不消费）。

用法
----
    python3 tools/difftest/comment/check-proto-parity.py
    # 退出码：0 = 一致；1 = 不一致（会打印 diff）；2 = 文件缺失

⚠️ 本脚本**只校验文件本身**，**不校验消费方式**（§3.4 效力边界第 1 条）：
字段号有没有被硬编码、`optional` presence 语义、Jackson/protobuf 配置差异 —— 那些靠
`docs/DRIFT/comment/proto.md` 的台账与 `PbCommentServiceAliasGateTest` 守。
"""
import difflib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))

GO_PROTO = "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/proto/comment.proto"
RAG_PROTO = os.path.join(REPO, "service/comment/comment-proto/src/main/proto/comment.proto")

GO_PACKAGE = "pb"
RAG_PACKAGE = "comment"


def read(path: str) -> str:
    if not os.path.exists(path):
        print(f"❌ 文件不存在：{path}")
        sys.exit(2)
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def strip_option_and_package(text: str):
    """剥掉 option 行与 package 行 ⇒ 只留**行为层**。

    返回 (剥后的行列表, package 名, 是否含 go_package)
    """
    kept, package, has_go_package = [], None, False
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("option "):
            if "go_package" in stripped:
                has_go_package = True
            continue
        if stripped.startswith("package "):
            package = stripped[len("package "):].strip().rstrip(";")
            continue
        kept.append(line)
    return kept, package, has_go_package


def normalize_ws(lines):
    """**归一化空白**：去首尾缩进、丢空行。

    ⚠️ AGENTS.md §3.4 明确允许「**缩进与首尾空行统一**」⇒ 空白差异**不算**行为层差异。
    （Go 侧用 4 空格缩进、RAG 侧用 2 空格 —— 这正是被允许的那一类改动。）
    """
    return [ln.strip() for ln in lines if ln.strip()]


def only_whitespace_diff(a_lines, b_lines) -> bool:
    """两段文本的差异**是否全部**只是空白差异。"""
    return normalize_ws(a_lines) == normalize_ws(b_lines)


def main() -> int:
    go_text = read(GO_PROTO)
    rag_text = read(RAG_PROTO)

    go_lines, go_pkg, _ = strip_option_and_package(go_text)
    rag_lines, rag_pkg, rag_has_go_package = strip_option_and_package(rag_text)

    ok = True

    # ① 行为层逐行一致（**归一化空白后**比 —— §3.4 允许缩进与空行统一）
    go_norm = normalize_ws(go_lines)
    rag_norm = normalize_ws(rag_lines)
    if go_norm != rag_norm:
        ok = False
        print("🔴 行为层不一致（剥掉 option/package 并归一化空白后仍有差异）：")
        for d in difflib.unified_diff(go_norm, rag_norm,
                                      fromfile="go/comment.proto", tofile="rag/comment.proto", lineterm=""):
            print("   " + d)
    else:
        print(f"✅ 行为层逐行一致（归一化空白后各 {len(go_norm)} 行）")
        if go_lines != rag_lines:
            print(f"   ⓘ 原始文本有差异，但**全部只是空白/缩进/空行**"
                  f"（Go 4 空格 vs RAG 2 空格）⇒ §3.4 明确允许 ⇒ 不算行为层差异")

    # ② package 必须**不等**（已签字偏离 SD-2026-10-23-04）
    if go_pkg == rag_pkg:
        ok = False
        print(f"🔴 package 两侧相同（={go_pkg}）⇒ 已签字偏离 `SD-2026-10-23-04` 被推翻，**需重新征签**")
    else:
        print(f"✅ package 归一（Go `{go_pkg}` / RAG `{rag_pkg}`）⇒ 符合已签字偏离 `SD-2026-10-23-04`")

    # ③ RAG 侧不得残留 go_package
    if rag_has_go_package:
        ok = False
        print("🔴 RAG 侧残留 `option go_package` ⇒ 那是 Go 专用项，Java 不消费 ⇒ 应删")
    else:
        print("✅ RAG 侧无残留 `option go_package`")

    if ok:
        print("\n🟢 comment proto 复制忠实度检查通过")
        return 0
    print("\n🔴 comment proto 复制忠实度检查**未通过**")
    return 1


if __name__ == "__main__":
    sys.exit(main())
