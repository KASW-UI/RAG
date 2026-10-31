#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R4 · Proto 接口契约一致性 —— Java 侧 CI 门。

【它守什么】
  Java 侧 `service/<域>/<域>-proto/src/main/proto/*.proto`（被测对象）
  ↔ `ci/contracts/proto/<域>.proto`（**契约基准**，权威）
  两侧归一化后**逐字节**比对。

【为什么是「基准」而不是 Go 仓】
  Go 真源（`Sea-RideTheWind_golang`）是**只读参考基准，不是 CI 的执行对象**
  （`ci/contracts/README.md` §一 办法 A/B 被禁）。本脚本**从不读 Go 仓**：
  契约已一次性离线提取成 `ci/contracts/proto/*.proto` 固化进 Java 仓。
  ⇒ CI 镜像**不需要 Go 工具链**，**不需要 Go 仓在线可访问**，**不跑任何 go 命令**。

【归一化是唯一实现】
  本模块的 `normalize_proto()` 是**全仓唯一的归一化实现**；
  `tools/difftest/check-proto-static.sh`（本地开发者跟 Go 现场比的那条路径）
  通过 `--normalize` 子命令**调用本函数**，不另存一份 ⇒ 两份实现不会漂移。

【失败语义（三态，退出码）—— 见 `ci/contracts/README.md` §四】
    0 = PASS    基准与 Java 侧逐字节一致
    1 = FAIL    检查执行了，发现 Java 侧与基准不一致（契约漂移）
    2 = BLOCKED 环境/依赖不满足，检查没做成（基准文件缺失 / Java 侧 proto 缺失目录 /
                 protoc 不可用而请求了语义层复核）
  ⚠️ **BLOCKED 一律不得计入 PASS**；本脚本在汇总里把三态分开计数。
  ⚠️ **优先级**：任一域 FAIL ⇒ 退出码 1（真实漂移优先暴露）；
     无 FAIL 但有 BLOCKED ⇒ 退出码 2；全 PASS ⇒ 0。

【用法】
    # CI 门（默认模式，零额外依赖：纯文本比对）
    python3 ci/scripts/check-proto-contract.py --baseline ci/contracts/proto

    # 追加 protoc 语义层复核（额外依赖：镜像内需有 protoc；缺 ⇒ BLOCKED(2)）
    python3 ci/scripts/check-proto-contract.py --baseline ci/contracts/proto --with-descriptor

    # 归一化原语（供 tools/difftest/check-proto-static.sh 调用）
    python3 ci/scripts/check-proto-contract.py --normalize <file>

    # 离线提取基准（⚠️ 需要 Go 仓；**CI 不得调用**，仅供人工重新提取时用）
    python3 ci/scripts/check-proto-contract.py --emit-baseline <go-proto-dir> <out-dir>
"""

from __future__ import annotations

import argparse
import difflib
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# ── 常量 ──────────────────────────────────────────────────────────────────

CONTRACT_VERSION = "proto-contract/v1"
CONTRACT_FROZEN_AT = "2026-10-31"

EXIT_PASS = 0
EXIT_FAIL = 1
EXIT_BLOCKED = 2

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_BASELINE_DIR = REPO_ROOT / "ci" / "contracts" / "proto"

# 侧特定的 option（表达层，AGENTS.md §3.4：proto 改动严格限定在 option 块与空白层面）
SIDECAR_OPTIONS = ("option go_package", "option java_package", "option java_multiple_files")

# ── 域表（唯一真值：域 ↔ Go 源 ↔ Java 侧 ↔ 基准文件 ↔ 钉住的 package）──
#
# `pkg`      = 基准文件里钉住的 proto package 值（= Java 侧取值）
# `go_pkg`   = Go 侧 package 值（两者不等 ⇒ 必须是**已签字偏离**，否则提取会报错）
# `ruling`   = 裁定依据（台账路径 / 签字编号）
DOMAINS = (
    {
        "key": "user",
        "go_proto": "user.proto",
        "java_rel": "service/users/user/user-proto/src/main/proto/users.proto",
        "pkg": "user",
        "go_pkg": "user",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/users/_shared.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "Go 侧 `user.proto` ⇒ RAG 侧改名 `users.proto`（AGENTS.md §3.4 唯一允许的改名）",
    },
    {
        "key": "admin",
        "go_proto": "admin.proto",
        "java_rel": "service/users/admin/admin-proto/src/main/proto/admin.proto",
        "pkg": "admin",
        "go_pkg": "admin",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/users/_shared.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "",
    },
    {
        "key": "task",
        "go_proto": "task.proto",
        "java_rel": "service/task/task-proto/src/main/proto/task.proto",
        "pkg": "task",
        "go_pkg": "task",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/task/proto.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "",
    },
    {
        "key": "comment",
        "go_proto": "comment.proto",
        "java_rel": "service/comment/comment-proto/src/main/proto/comment.proto",
        "pkg": "comment",
        "go_pkg": "pb",
        "pkg_why": "SD-2026-10-23-04 已签字偏离 · Go 侧的 pb 是遗留包名",
        "ruling": "SD-2026-10-23-04（docs/Intentional_Deviations/comment/proto.md）"
                  " · docs/DRIFT/comment/proto.md",
        "note": "proto `package` 由 Go 侧 `pb` 归一为 `comment`（**已签字**）",
    },
    {
        "key": "favorite",
        "go_proto": "favorite.proto",
        "java_rel": "service/favorite/favorite-proto/src/main/proto/favorite.proto",
        "pkg": "favorite",
        "go_pkg": "favorite",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/favorite/proto.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "",
    },
    {
        "key": "follow",
        "go_proto": "follow.proto",
        "java_rel": "service/follow/follow-proto/src/main/proto/follow.proto",
        "pkg": "follow",
        "go_pkg": "follow",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/follow/proto.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "",
    },
    {
        "key": "hot",
        "go_proto": "hot.proto",
        "java_rel": "service/hot/hot-proto/src/main/proto/hot.proto",
        "pkg": "hot",
        "go_pkg": "hot",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/hot/proto.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "",
    },
    {
        "key": "like",
        "go_proto": "like.proto",
        "java_rel": "service/like/like-proto/src/main/proto/like.proto",
        "pkg": "like",
        "go_pkg": "pb",
        "pkg_why": "SD-2026-10-23-05 已签字偏离 · Go 侧的 pb 是遗留包名",
        "ruling": "SD-2026-10-23-05（docs/Intentional_Deviations/like/proto.md）"
                  " · docs/DRIFT/like/proto.md",
        "note": "proto `package` 由 Go 侧 `pb` 归一为 `like`（**已签字**）",
    },
    {
        "key": "message",
        "go_proto": "message.proto",
        "java_rel": "service/message/message-proto/src/main/proto/message.proto",
        "pkg": "message",
        "go_pkg": "message",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/message/proto.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "",
    },
    {
        "key": "article",
        "go_proto": "article.proto",
        "java_rel": "service/article/article-proto/src/main/proto/article.proto",
        "pkg": "article",
        "go_pkg": "article",
        "pkg_why": "两侧逐字相同（Go 侧即此值）",
        "ruling": "docs/DRIFT/article/proto.md · docs/DRIFT/_cross/proto-verification.md",
        "note": "",
    },
)

BASELINE_HEADER_TEMPLATE = (
    "# 来源：Go 真源 proto/{go_proto}（{go_anchor}）\n"
    "# 契约基准版本：{version}（{frozen_at}）\n"
    "# 裁定依据：{ruling}\n"
    "# 说明：本文件是 R4 契约基准，CI 直接读取；改它 = 改契约，需 PR 审查。\n"
)


# ── 归一化（唯一实现）────────────────────────────────────────────────────

def normalize_proto(text: str, *, keep_package: bool = False,
                    strip_hash_header: bool = False) -> str:
    """把 .proto 文本归一化成「契约文本」。

    规则（顺序固定）：
      1. 删块注释 ``/* ... */``
      2. 删行注释 ``// ...``
      3. 删 ``option go_package`` / ``option java_package`` /
         ``option java_multiple_files`` 整行（表达层）
      4. 删 ``#`` 开头的行（**仅** 基准文件的元信息头；proto 无 ``#`` 语法）
      5. 删 ``package`` 行（**仅** ``keep_package=False``）
      6. 逐行：去首尾空白 → 折叠内部空白 → 丢空行

    ``keep_package=True`` 用于「基准 vs Java 侧」比对：此时 package 是**契约项**
    （proto package 决定 gRPC 方法路径前缀 ⇒ wire 层可见）⇒ 必须逐字节参与比对。

    ⚠️ 本函数**幂等**（normalize(normalize(x)) == normalize(x)）
       ⇒ 基准文件本身就是「归一化后的文本」，可被再次归一化而不变形。
    """
    src = text
    # 1) 块注释
    src = re.sub(r"/\*[\s\S]*?\*/", "", src)
    # 2) 行注释
    src = re.sub(r"//[^\n]*", "", src)
    # 3) 侧特定 option 整行
    for opt in SIDECAR_OPTIONS:
        src = re.sub(rf"^\s*{re.escape(opt)}[^\n]*\n", "", src, flags=re.M)
    # 4) 基准文件元信息头（'#' 开头）
    if strip_hash_header:
        src = re.sub(r"^\s*#[^\n]*\n", "", src, flags=re.M)
    # 5) package 行
    if not keep_package:
        src = re.sub(r"^\s*package\s+[A-Za-z0-9_.]+\s*;[^\n]*\n", "", src, flags=re.M)
    # 6) 逐行折叠
    out = []
    for line in src.splitlines():
        s = line.strip()
        if not s:
            continue
        s = re.sub(r"\s+", " ", s).rstrip("{; ")
        tail = line.rstrip()[-1:]
        if tail and tail in "{;":
            s += tail
        out.append(re.sub(r"\s+", " ", s))
    return "\n".join(out)


def read_text(path: Path) -> str:
    """读文件（**显式 utf-8**）。

    ⚠️ proto 源里含中文注释 ⇒ 用 locale 默认编码读会在 LANG=C 的镜像上抛
    UnicodeDecodeError ⇒ 这里显式指定 utf-8。
    """
    return path.read_text(encoding="utf-8")


def extract_package(normalized: str) -> str:
    """从**归一化后**的文本里取 package 值（没有 ⇒ 空串）。"""
    m = re.search(r"^package\s+([A-Za-z0-9_.]+)\s*;", normalized, flags=re.M)
    return m.group(1) if m else ""


# ── protoc 语义层（可选）─────────────────────────────────────────────────

def protoc_available() -> bool:
    return shutil.which("protoc") is not None


def descriptor_bytes(proto_text: str, filename: str) -> bytes:
    """用 protoc 把一段 proto 文本编译成 FileDescriptorSet 字节。

    ⚠️ 文件名是 descriptor 的一部分 ⇒ 两侧必须用**同一个 filename** 编译，
       否则字节比对会把「文件名不同」误判成语义差异。
    """
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        (tmp / filename).write_text(proto_text, encoding="utf-8")
        desc = tmp / "desc.bin"
        proc = subprocess.run(
            ["protoc", f"--proto_path={tmp}", f"--descriptor_set_out={desc}", filename],
            capture_output=True, text=True,
        )
        if proc.returncode != 0:
            raise RuntimeError(f"protoc 失败: {proc.stderr.strip()}")
        return desc.read_bytes()


def strip_option_lines(text: str) -> str:
    """只删 option 整行，其余（缩进 / 注释 / 空行）原样保留。

    用于「归一化是否损失语义」的复核：把**只删了 option** 的原文与
    **完全归一化**的文本分别编译成 descriptor ⇒ 两者必须逐字节相等
    ⇒ 证明归一化只动表达层，没动语义。
    """
    out = []
    for line in text.splitlines():
        if any(re.match(rf"^\s*{re.escape(opt)}\b", line) for opt in SIDECAR_OPTIONS):
            continue
        out.append(line)
    return "\n".join(out)


def strip_hash_header_lines(text: str) -> str:
    """删掉 ``#`` 开头的基准元信息头（proto 无 ``#`` 语法 ⇒ 不删 protoc 读不了）。"""
    return re.sub(r"^\s*#[^\n]*\n", "", text, flags=re.M)


# ── 差异展示 ─────────────────────────────────────────────────────────────

def format_diff(baseline_lines: list[str], java_lines: list[str]) -> str:
    """逐条差异 + unified diff。"""
    parts: list[str] = []
    sm = difflib.SequenceMatcher(None, baseline_lines, java_lines)
    detail: list[str] = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            continue
        for k in range(i1, i2):
            detail.append(f"      - 基准第 {k + 1} 行: {baseline_lines[k]}")
        for k in range(j1, j2):
            detail.append(f"      + Java 第 {k + 1} 行: {java_lines[k]}")
    if detail:
        parts.append("      ── 逐条差异 ──")
        parts.extend(detail)
    udiff = list(difflib.unified_diff(
        baseline_lines, java_lines,
        fromfile="baseline（契约基准）", tofile="java（RAG 侧 .proto）", lineterm="",
    ))
    if udiff:
        parts.append("      ── unified diff ──")
        parts.extend("      " + l for l in udiff)
    return "\n".join(parts)


# ── 门：基准 vs Java 侧 ──────────────────────────────────────────────────

def check_baseline(baseline_dir: Path, root: Path, with_descriptor: bool) -> int:
    print("== R4 proto 契约对照（契约基准 vs Java 侧 .proto）==")
    print(f"   基准目录：{baseline_dir}")
    print(f"   Java 侧：{root}")

    if not baseline_dir.is_dir():
        print(f"  ✗ BLOCKED：基准目录不存在 ⇒ 检查没做成（不是 PASS）")
        print(f"    期望路径：{baseline_dir}")
        return EXIT_BLOCKED

    n_pass = n_fail = n_blocked = 0
    failures: list[str] = []

    for dom in DOMAINS:
        key = dom["key"]
        base_path = baseline_dir / f"{key}.proto"
        java_path = root / dom["java_rel"]
        print(f"== {key} ==")

        # --- 基准文件缺失 ⇒ BLOCKED（**不是 FAIL**：不是 Java 侧漂移，是门没做成）
        if not base_path.is_file():
            print(f"  ⚠ BLOCKED：契约基准缺失 ⇒ {key} 域**未检查**（不得计入 PASS）")
            print(f"    期望路径：{base_path}")
            n_blocked += 1
            failures.append(f"{key}: BLOCKED（基准缺失）")
            continue

        # --- Java 侧 proto 缺失 ⇒ FAIL（真的少了契约）
        if not java_path.is_file():
            print(f"  ✗ FAIL：Java 侧 proto 文件缺失（契约缺失）")
            print(f"    期望路径：{java_path}")
            n_fail += 1
            failures.append(f"{key}: FAIL（Java 侧 proto 缺失）")
            continue

        try:
            base_raw = read_text(base_path)
            java_raw = read_text(java_path)
        except OSError as exc:
            print(f"  ⚠ BLOCKED：文件不可读 ⇒ {exc}")
            n_blocked += 1
            failures.append(f"{key}: BLOCKED（不可读）")
            continue

        base_norm = normalize_proto(base_raw, keep_package=True, strip_hash_header=True)
        java_norm = normalize_proto(java_raw, keep_package=True)
        base_lines = base_norm.splitlines()
        java_lines = java_norm.splitlines()

        # --- package 钉住：基准里必须是裁定过的值
        base_pkg = extract_package(base_norm)
        java_pkg = extract_package(java_norm)
        if base_pkg != dom["pkg"]:
            print(f"  ✗ FAIL：基准里的 package = {base_pkg or '(读不到)'}，"
                  f"**应为 {dom['pkg']}**（{dom['pkg_why']}）")
            n_fail += 1
            failures.append(f"{key}: FAIL（基准 package 漂移）")
            continue

        if base_lines == java_lines:
            print(f"  ✓ {key}：契约逐字节一致（基准 {len(base_lines)} 行 / "
                  f"Java {len(java_lines)} 行）")
            print(f"  ✓ package 钉住值 = {base_pkg}（{dom['pkg_why']}）")
            n_pass += 1
        else:
            print(f"  ✗ FAIL：{key}：契约不一致（基准 {len(base_lines)} 行 / "
                  f"Java {len(java_lines)} 行）")
            print(format_diff(base_lines, java_lines))
            print(f"  ✓ package 钉住值 = {base_pkg}（{dom['pkg_why']}；"
                  f"Java 侧实测 = {java_pkg or '(读不到)'}）")
            n_fail += 1
            failures.append(f"{key}: FAIL（契约漂移）")

        # --- 可选：protoc 语义层复核（额外依赖 protoc）
        if with_descriptor:
            ok, msg = descriptor_cross_check(dom, base_raw, java_raw)
            if ok:
                print(f"  ✓ {key}：protoc 语义层复核通过（{msg}）")
            else:
                print(f"  ✗ FAIL：{key}：protoc 语义层复核失败（{msg}）")
                n_fail += 1
                failures.append(f"{key}: FAIL（protoc 语义层）")

    # --- 基准目录里出现未登记的 .proto ⇒ FAIL（否则它永远不会被检查 = 静默的洞）
    expected = {f"{d['key']}.proto" for d in DOMAINS}
    extra = sorted(p.name for p in baseline_dir.glob("*.proto") if p.name not in expected)
    if extra:
        print(f"  ✗ FAIL：基准目录存在**未登记**的 proto 文件：{', '.join(extra)}")
        print(f"    ⇒ 这些文件永远不会被检查 ⇒ 静默的洞；请登记进域表或从基准目录移除。")
        n_fail += 1
        failures.append(f"基准目录未登记文件: {', '.join(extra)}")

    print("== 汇总 ==")
    print(f"   PASS {n_pass} · FAIL {n_fail} · BLOCKED {n_blocked}"
          f"（域总数 {len(DOMAINS)}）")
    if failures:
        print("   失败/阻塞明细：")
        for f in failures:
            print(f"     - {f}")

    if n_fail:
        print("✗ R4 proto 契约门未通过（存在契约漂移）")
        return EXIT_FAIL
    if n_blocked:
        print("⚠ R4 proto 契约门 BLOCKED（有域未检查，不得计入 PASS）")
        return EXIT_BLOCKED
    print(f"✓ R4 proto 契约门通过：{len(DOMAINS)} 个域的契约逐字节一致")
    return EXIT_PASS


def descriptor_cross_check(dom: dict, base_raw: str, java_raw: str) -> tuple[bool, str]:
    """protoc 语义层复核（可选层）。

    两件事：
      D1 语义等价：descriptor(基准) 与 descriptor(Java 侧) **逐字节相等**
         ⇒ 文本比对没有漏掉语义差异（字段号 / 类型 / label / service 方法…）。
      D2 归一化无损：descriptor(原文仅删 option) 与 descriptor(完全归一化)
         **逐字节相等** ⇒ 归一化只动表达层（注释 / 空白 / option），没动语义。

    ⚠️ D1 需要两侧 package 相同 ⇒ 基准里已钉住裁定后的 package 值
       （like / comment 的 `pb` → 域名 归一在**提取阶段**完成）。
    """
    key = dom["key"]
    filename = f"{key}.proto"
    try:
        base_norm = normalize_proto(base_raw, keep_package=True, strip_hash_header=True)
        java_norm = normalize_proto(java_raw, keep_package=True)
        d_base = descriptor_bytes(base_norm, filename)
        d_java = descriptor_bytes(java_norm, filename)
        if d_base != d_java:
            return False, "基准与 Java 侧的 protoc descriptor 不相等"
        # D2：把原文的 package 换成裁定值后，只删 option（+ 基准元信息头），再与归一化结果比对
        base_src = replace_package_line(
            strip_option_lines(strip_hash_header_lines(base_raw)), dom["pkg"])
        d_base_src = descriptor_bytes(base_src, filename)
        if d_base_src != d_base:
            return False, "归一化**有损**（基准侧：原文仅删 option 与归一化结果语义不等）"
        java_src = strip_option_lines(java_raw)
        d_java_src = descriptor_bytes(java_src, filename)
        if d_java_src != d_java:
            return False, "归一化**有损**（Java 侧：原文仅删 option 与归一化结果语义不等）"
    except RuntimeError as exc:
        return False, str(exc)
    return True, "D1 语义等价 + D2 归一化无损"


def replace_package_line(text: str, pkg: str) -> str:
    """把第一处 ``package X;`` 换成 ``package <pkg>;``（没有则原样返回）。"""
    return re.sub(r"^(\s*package\s+)[A-Za-z0-9_.]+(\s*;)",
                  lambda m: f"{m.group(1)}{pkg}{m.group(2)}",
                  text, count=1, flags=re.M)


# ── 离线提取（需 Go 仓；CI 不得调用）──────────────────────────────────────

def emit_baseline(go_proto_dir: Path, out_dir: Path) -> int:
    print("⚠️ 离线提取模式：需要本地可访问的 Go 真源 proto 目录。")
    print("   本命令**只应由人工在本地执行**，产出走 PR 审查；")
    print("   **CI 不得调用**（CI 只读已固化的基准文件）。")
    # 🔴 硬闸：CI 环境下**拒绝**执行（Go 仓不是 CI 的执行对象；CI 不得依赖 Go 仓在线可访问）
    if os.environ.get("CI", "").strip().lower() in ("true", "1", "yes"):
        if os.environ.get("R4_ALLOW_OFFLINE_EXTRACT") != "1":
            print("  ✗ BLOCKED：检测到 CI 环境 ⇒ **拒绝**执行离线提取。")
            print("    （Go 仓不是 CI 的执行对象；CI 不得拉 Go 仓、不得跑 go 命令。）")
            print("    本地人工提取时请确保 CI 未设置，或显式 R4_ALLOW_OFFLINE_EXTRACT=1。")
            return EXIT_BLOCKED
    if not go_proto_dir.is_dir():
        print(f"  ✗ BLOCKED：Go proto 目录不存在：{go_proto_dir}")
        return EXIT_BLOCKED

    out_dir.mkdir(parents=True, exist_ok=True)
    n_ok = 0
    for dom in DOMAINS:
        key = dom["key"]
        go_path = go_proto_dir / dom["go_proto"]
        if not go_path.is_file():
            print(f"  ✗ {key}：Go 侧 proto 缺失：{go_path}")
            return EXIT_FAIL
        go_raw = read_text(go_path)
        # Go 侧 package 值必须与裁定一致（like / comment 是**已签字**的 `pb`）
        go_pkg = extract_package(normalize_proto(go_raw, keep_package=True))
        if go_pkg != dom["go_pkg"]:
            print(f"  ✗ {key}：Go 侧 package = {go_pkg or '(读不到)'}，"
                  f"与裁定不符（期望 {dom['go_pkg']}）⇒ 停止提取")
            return EXIT_FAIL

        norm = normalize_proto(go_raw, keep_package=True)
        norm = replace_package_line(norm, dom["pkg"])

        pkg_line = next((ln for ln in go_raw.splitlines()
                         if re.match(r"^\s*package\s", ln)), "")
        anchor_line = next((i for i, ln in enumerate(go_raw.splitlines(), 1)
                            if re.match(r"^\s*package\s", ln)), 1)
        header = BASELINE_HEADER_TEMPLATE.format(
            go_proto=dom["go_proto"],
            go_anchor=f"{dom['go_proto']}:{anchor_line}",
            version=CONTRACT_VERSION,
            frozen_at=CONTRACT_FROZEN_AT,
            ruling=dom["ruling"],
        )
        extra = ""
        if dom["note"]:
            extra = f"#   ↳ {dom['note']}\n"
        if dom["go_pkg"] != dom["pkg"]:
            extra += (f"#   ↳ Go 侧 package 行原文：{pkg_line.strip()} ⇒ "
                      f"基准取裁定值 `package {dom['pkg']};`（{dom['pkg_why']}）\n")
        else:
            extra += f"#   ↳ Go 侧 package 行原文：{pkg_line.strip()}\n"

        target = out_dir / f"{key}.proto"
        target.write_text(header + extra + "\n" + norm + "\n", encoding="utf-8")
        print(f"  ✓ {key} → {target}（{len(norm.splitlines())} 行契约文本）")
        n_ok += 1
    print(f"✓ 已提取 {n_ok} 个域的契约基准 → {out_dir}")
    return EXIT_PASS


# ── 入口 ──────────────────────────────────────────────────────────────────

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description="R4 · Proto 接口契约一致性检查（Java 侧）")
    ap.add_argument("--baseline", metavar="DIR",
                    help="契约基准目录（默认 ci/contracts/proto）")
    ap.add_argument("--with-descriptor", action="store_true",
                    help="追加 protoc 语义层复核（额外依赖：镜像内需有 protoc）")
    ap.add_argument("--normalize", metavar="FILE",
                    help="只打印归一化后的契约文本（归一化原语）")
    ap.add_argument("--emit-baseline", nargs=2, metavar=("GO_PROTO_DIR", "OUT_DIR"),
                    help="离线从 Go 真源提取契约基准（需 Go 仓；CI 不得调用）")
    ap.add_argument("--root", metavar="PATH", default=str(REPO_ROOT),
                    help="Java 仓根目录（默认按脚本位置推导）")
    args = ap.parse_args(argv)

    if args.emit_baseline:
        return emit_baseline(Path(args.emit_baseline[0]), Path(args.emit_baseline[1]))

    if args.normalize:
        p = Path(args.normalize)
        if not p.is_file():
            print(f"✗ 文件不存在：{p}", file=sys.stderr)
            return EXIT_BLOCKED
        # 归一化原语：与 check-proto-static.sh 共用同一实现（该脚本按需调用）
        print(normalize_proto(read_text(p)))
        return EXIT_PASS

    baseline_dir = Path(args.baseline) if args.baseline else DEFAULT_BASELINE_DIR
    root = Path(args.root)

    if args.with_descriptor and not protoc_available():
        print("⚠ BLOCKED：请求了 protoc 语义层复核，但镜像内没有 protoc ⇒ "
              "该层检查没做成（不得计入 PASS）")
        print("   ⇒ 请在 CI 镜像里安装 protoc，或去掉 --with-descriptor "
              "（纯文本比对层不需要 protoc）。")
        return EXIT_BLOCKED

    return check_baseline(baseline_dir, root, args.with_descriptor)


if __name__ == "__main__":
    sys.exit(main())
