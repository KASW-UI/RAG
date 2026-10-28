#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生产密钥守门 —— 拦「真源 JWT 密钥明文进仓」。

🔴 **为什么需要这条守门（2026-10-28 实测）**
    真源（Go）的 JWT 默认密钥是**明文**写在 yaml 里的，RAG 侧照抄进 `application.yml`
    是「行为对齐」的必要动作 ⇒ 于是**生产密钥明文进了 git** ⇒ 一旦仓库外泄 = 密钥外泄
    （签发任意 admin / user token ⇒ 越权）。
    ⇒ 手工清一次不够：**必须有机器守门**，否则下一轮对齐 / 复制粘贴就会把它带回来。

🔴 **守门自己绝不能持有明文密钥**
    ⇒ 本脚本**只保存两个密钥的 SHA-256 十六进制**（不可逆）⇒ 脚本本身不是新的泄露面。
    ⇒ 脚本里也**不按明文搜**（明文只存在于写它的人脑子里，不在文件里）。
    ⇒ 命中判定 = 「候选 token 的 SHA-256」== 「钉住的 SHA-256」⇒ 零误报（哈希碰撞除外）。

✅ **本守门拦什么**
    ① 扫描范围内任一文件里，**连续出现**了这两个密钥之一（任意编码上下文：
       Java 字符串字面量 · YAML 标量 · properties 值 · Python/Go/Shell 里的引号串）⇒ 报红；
    ② 按**滑动窗口 + SHA-256**判定 ⇒ 不依赖「密钥长什么样」，只依赖「是不是那把钥匙」。

⚠️ **白名单是【豁免】，不是【免检】**
    · 每条必须写清「谁该修 + 为什么现在合法 + 修完怎么删」；
    · 条目已无命中 ⇒ 🟡 提示「可从白名单删除」（**不放过**）；
    · 🔴 **`service/users/users-common/**`（G4 写权面）永不可豁免** ——
      豁免机制不得掩盖共享层自己的回归。

用法
----
    python3 tools/difftest/check-no-prod-secrets.py                   # 正常跑（EXIT=0/1）
    python3 tools/difftest/check-no-prod-secrets.py --print            # 只打印，不判红
    python3 tools/difftest/check-no-prod-secrets.py --root <dir>       # 指定扫描根（不给 git 用）
    python3 tools/difftest/check-no-prod-secrets.py --mutation-test    # 内置变异自测（EXIT=0 = 全命中）
"""

from __future__ import annotations

import fnmatch
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# ---------------------------------------------------------------- 扫描范围
SCAN_DIRS = ("docs", "service", "tools")

# ---------------------------------------------------------------- 钉住的密钥（只存哈希）
# ⚠️ 这里**只有 SHA-256**，没有明文 ⇒ 脚本自身不构成泄露面。
#    sha256 = sha256(明文 UTF-8 字节)；length = 明文字节数（用于滑动窗口的候选长度）。
PINNED_SECRETS: list[dict] = [
    {
        "label": "admin-access-secret(15B)",
        "sha256": "6c725d4fa62637dbb64dfa98d64fe64ec66bce52d5e3b0695ea3765d8ac8ee77",
        "length": 15,
        "source": "Go service/user/admin/api/etc/admincenter.yaml:11 → AdminAuth.AccessSecret（15 字节）",
    },
    {
        "label": "user-access-secret(36B)",
        "sha256": "08416cdc6aaaa6554600c6ff6fa6f9167d51261dcb0269ee293a98a5c0c557cd",
        "length": 36,
        "source": "Go service/user/user/api/etc/usercenter.yaml:11 → UserAuth.AccessSecret（36 字节 UUID）",
    },
]

# ---------------------------------------------------------------- 白名单：glob → 理由/owner
# 结构：**glob → (为什么现在合法, 谁该修, 修完怎么删)**。
# 说明：域侧 lane 分工（AGENTS.md §5.3d）—— G1 = users(user+admin)+follow，G2 = message·like·task，
#       G3 = article·comment·favorite·hot。
DOMAIN_LANES = "G1(users/user·users/admin·follow) / G2(message·like·task) / G3(article·comment·favorite·hot)"

WHITELIST: list[dict] = [
    {
        "id": "W1-域侧默认配置",
        "globs": (
            "service/*/*/src/main/resources/application.yml",
            "service/*/*/src/main/resources/application.yaml",
        ),
        "reason": (
            "域侧默认密钥值本身。⚠️ **改默认值 = 改行为**（真源 Go 侧同值，行为以 Go 为准，AGENTS.md §3.1）"
            "⇒ 属域侧写权，G4 不得擅改。"
        ),
        "owner": f"域侧 lane：{DOMAIN_LANES}（主控已转派）",
        "how_to_drop": "域侧改成「环境变量注入 + 无默认明文」（两侧行为同步对齐）后即可删除本条",
    },
    {
        "id": "W2-域侧 main 源码里的引用",
        "globs": ("service/*/*/src/main/java/**",),
        "reason": "域侧 javadoc / 常量把真源默认密钥写了出来（事实引用，非本模块独有）。",
        "owner": f"域侧 lane：{DOMAIN_LANES}（行为中性，可自修）",
        "how_to_drop": "改成「默认 15 字节 / 36 字节 UUID」的形状描述（保留 yaml:11 出处）后即可删除本条",
    },
    {
        "id": "W3-域侧测试夹具",
        "globs": ("service/*/*/src/test/**",),
        "reason": "域侧测试复制了同一常量（行为中性，不影响运行行为，只影响泄露面）。",
        "owner": f"域侧 lane：{DOMAIN_LANES}（行为中性，可自修）",
        "how_to_drop": "换成【等长夹具】并显式钉住长度（15 / 36）后即可删除本条",
    },
    {
        "id": "W4-Go 侧镜像（对拍夹具）· 永久豁免",
        "globs": (
            "tools/difftest/conf/go/**",
            "tools/api-full-parity-probe/**",
            "tools/difftest/comment/conf/go-*.yaml",
        ),
        "reason": (
            "这些文件的**用途本身**就是「复刻真源 Go 侧配置」⇒ 对拍要用**同一把钥匙**才能验签/对拍 ⇒ "
            "改了它们就失去对拍意义 ⇒ **永久豁免，不删**。"
        ),
        "owner": "G4（tools 写权面）· 永久豁免",
        "how_to_drop": "不删（永久豁免）",
    },
    {
        "id": "W5-tools 对拍脚本里的密钥常量",
        "globs": (
            "tools/difftest/comment/diff_comment.py",
            "tools/difftest/exp-http-headers-message.sh",
        ),
        "reason": "对拍探针要用真源同款密钥才能验签（行为中性、但仍是明文持有）。",
        "owner": "G4（tools 写权面）· 本批次未授权改",
        "how_to_drop": "改成从环境变量读取（JWT_SECRET，由 CI/本地注入）后即可删除本条",
    },
    {
        "id": "W6-文档 / 台账里的事实引用",
        "globs": ("docs/**",),
        "reason": "台账与 FileTree 文档引用了真源默认密钥（事实记录）。",
        "owner": "主控（docs/DRIFT 按域分工）+ 各域 lane（docs/FileTree）",
        "how_to_drop": "改成「15 字节 / 36 字节 UUID」的形状描述（保留 yaml:11 出处）后即可删除本条",
    },
]

# 🔴 永不可豁免：G4 自己的写权面必须零命中 ⇒ 豁免机制不得掩盖共享层回归。
NEVER_EXEMPT_PREFIXES = ("service/users/users-common/",)

SKIP_DIR_NAMES = {".git", "target", "node_modules", ".venv", "__pycache__", ".mvn", ".idea"}

# 候选 token = 「可打印 ASCII 且不含引号」的极大连续串（密钥两者都满足 ⇒ 判定完备）
_TOKEN_RE = re.compile(rb'[^\x00-\x20\x7f-\xff"\'`]+')


# ---------------------------------------------------------------- 收集文件
def _git_files(root: str) -> list[str]:
    """仓库模式：git 已追踪 + 未跟踪（排除 .gitignore）⇒ 拦住「还没提交的新文件」。"""
    try:
        out = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
            cwd=root, capture_output=True, text=True, check=True,
        )
    except Exception:
        return []
    return [p for p in out.stdout.splitlines() if p]


def _walk_files(root: str) -> list[str]:
    """`--root` 模式：文件系统遍历（变异自测用，不是 git 仓）。"""
    found: list[str] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIR_NAMES]
        for fn in filenames:
            rel = os.path.relpath(os.path.join(dirpath, fn), root)
            found.append(rel.replace(os.sep, "/"))
    return found


def _in_scope(rel: str) -> bool:
    return any(rel == d or rel.startswith(d + "/") for d in SCAN_DIRS)


# ---------------------------------------------------------------- 白名单匹配
def _exempt_entry(rel: str) -> dict | None:
    """返回命中的白名单条目；`service/users/users-common/**` 永不可豁免。"""
    if any(rel.startswith(p) for p in NEVER_EXEMPT_PREFIXES):
        return None
    for entry in WHITELIST:
        for g in entry["globs"]:
            if fnmatch.fnmatchcase(rel, g):
                return entry
    return None


# ---------------------------------------------------------------- 扫描
def _scan_bytes(data: bytes, lengths: set[int], digests: dict[str, str]) -> list[tuple[int, str]]:
    """滑动窗口 + SHA-256 ⇒ 返回 [(offset, sha256), ...]（仅命中钉住哈希的窗口）。"""
    out: list[tuple[int, str]] = []
    for m in _TOKEN_RE.finditer(data):
        run = m.group()
        base = m.start()
        for length in lengths:
            if len(run) < length:
                continue
            for off in range(0, len(run) - length + 1):
                d = hashlib.sha256(run[off:off + length]).hexdigest()
                if d in digests:
                    out.append((base + off, d))
    return out


def scan(root: str, files: list[str], pins: list[dict] | None = None) -> tuple[list[dict], int]:
    """返回 (命中列表, 扫描文件数)。命中项：{path, label, offset, line, whitelisted, entry}"""
    pins = pins or PINNED_SECRETS
    digests = {p["sha256"]: p["label"] for p in pins}
    lengths = {p["length"] for p in pins}
    hits: list[dict] = []
    scanned = 0
    for rel in sorted(files):
        if not _in_scope(rel):
            continue
        full = os.path.join(root, rel)
        if not os.path.isfile(full) or os.path.islink(full):
            continue
        try:
            with open(full, "rb") as fh:
                data = fh.read()
        except OSError:
            continue
        scanned += 1
        for offset, digest in _scan_bytes(data, lengths, digests):
            entry = _exempt_entry(rel)
            line = data.count(b"\n", 0, offset) + 1
            hits.append({
                "path": rel,
                "label": digests[digest],
                "offset": offset,
                "line": line,
                "whitelisted": entry is not None,
                "entry": entry,
            })
    return hits, scanned


# ---------------------------------------------------------------- 报告
def report(root: str, hits: list[dict], scanned: int) -> list[str]:
    lines: list[str] = []
    bad = [h for h in hits if not h["whitelisted"]]
    exempted = [h for h in hits if h["whitelisted"]]
    stale: list[str] = []

    # 白名单条目已无命中 ⇒ 🟡 提示可清空（与 check-no-nul-bytes.py 同款纪律）
    hit_paths: dict[str, set[str]] = {}
    for h in hits:
        if h["entry"] is not None:
            hit_paths.setdefault(h["entry"]["id"], set()).add(h["path"])
    for entry in WHITELIST:
        if entry["id"] not in hit_paths:
            stale.append(f"⚠️ 白名单条目已无命中 ⇒ 可删除：{entry['id']}（{entry['globs'][0]}）")

    lines.append("生产密钥守门（真源 JWT 密钥明文进仓）")
    lines.append("=" * 60)
    lines.append(f"  扫描根: {root}")
    lines.append(f"  扫描文件: {scanned} 个（范围：docs/ · service/ · tools/）")
    lines.append(f"  命中: {len(hits)} 处（豁免 {len(exempted)} · 未豁免 {len(bad)}）")
    lines.append(f"  判定方式: 滑动窗口 + SHA-256（脚本内只存哈希 ⇒ 不持明文）")
    lines.append("-" * 60)

    for h in exempted:
        e = h["entry"]
        lines.append(f"⚠️ 已豁免 · {h['path']}:{h['line']}  [{h['label']}]")
        lines.append(f"     理由：{e['reason']}")
        lines.append(f"     owner：{e['owner']}")
        lines.append(f"     删法：{e['how_to_drop']}")
    for h in bad:
        lines.append(f"🔴 未豁免 · {h['path']}:{h['line']}  [{h['label']}]")
    for s in stale:
        lines.append(s)

    lines.append("-" * 60)
    if bad:
        lines.append("❌ FAIL")
        lines.append("")
        lines.append("🔴 真源 JWT 密钥明文进了仓 ⇒ 仓库外泄 = 密钥外泄（可签发任意 admin/user token）")
        lines.append("   ⇒ 修法：换成【等长夹具】（形状与真源一致）+ 显式钉住长度（15 / 36）")
        lines.append("   ⇒ ⚠️ **不要**改成别的长度：长度本身就是被测的不变量（jjwt 要求 ≥32 字节）")
    else:
        lines.append("✅ PASS")
        lines.append("   · 扫描范围内没有未豁免的生产密钥明文")
        if exempted:
            lines.append(f"   · 另有 {len(exempted)} 处【已豁免】（理由/owner/删法见上 ⇒ 修完即可删白名单）")
        if stale:
            lines.append("   · 🟡 白名单有可清理项（见上）")
    return lines


# ---------------------------------------------------------------- 变异自测
def _write(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)


def mutation_test() -> int:
    """变异自测：① 注入必须被抓 ② 白名单路径必须不红 ③ 干净文件必须不红。"""
    # 🔴 **绝不能用真密钥做变异** ⇒ 用一把合成密钥（只在本函数内存里出现）。
    synth = "fixture-mutation-secret-0123456789"
    synth_pin = {
        "label": "synthetic-mutation-secret",
        "sha256": hashlib.sha256(synth.encode("utf-8")).hexdigest(),
        "length": len(synth.encode("utf-8")),
        "source": "合成（仅用于本自测）",
    }
    pins = PINNED_SECRETS + [synth_pin]
    results: list[tuple[str, bool, str]] = []

    def run(name: str, build, expect_red: bool, expect_path: str | None) -> None:
        tmp = tempfile.mkdtemp(prefix="g4-secret-mut-")
        try:
            build(tmp)
            files = _walk_files(tmp)
            hits, _ = scan(tmp, files, pins)
            bad = [h for h in hits if not h["whitelisted"]]
            out = "\n".join(report(tmp, hits, len(files)))
            if expect_red:
                ok = bool(bad) and (expect_path is None or expect_path in out)
                detail = f"未豁免命中 {len(bad)} 处"
            else:
                ok = not bad
                detail = f"未豁免命中 {len(bad)} 处（期望 0）"
            results.append((name, ok, detail))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    # M1：扫描范围内的文件里注入合成密钥 ⇒ 必须红 + 点名该文件
    def b1(tmp: str) -> None:
        _write(os.path.join(tmp, "service/x/Leak.java"),
               "class Leak { String s = \"%s\"; }\n" % synth)
    run("M1 扫描范围内注入合成密钥 ⇒ 必须红 + 点名", b1, True, "service/x/Leak.java")

    # M2：同样内容放到【白名单路径】下 ⇒ 必须不红
    def b2(tmp: str) -> None:
        _write(os.path.join(tmp, "tools/difftest/conf/go/admin-api.yaml"),
               "Auth:\n  AccessSecret: \"%s\"\n" % synth)
    run("M2 白名单路径（Go 镜像）放同值 ⇒ 必须不红", b2, False, None)

    # M3：干净文件（不含密钥）⇒ 必须不红（无假阳性）
    def b3(tmp: str) -> None:
        _write(os.path.join(tmp, "service/x/Clean.java"),
               "class Clean { String s = \"not-a-secret-at-all-0123456789\"; }\n")
        _write(os.path.join(tmp, "docs/DRIFT/clean.md"), "# clean\n\n干净台账。\n")
    run("M3 干净文件 ⇒ 必须不红（无假阳性）", b3, False, None)

    print("生产密钥守门 · 内置变异自测")
    print("=" * 60)
    failed = 0
    for name, ok, detail in results:
        print(f"  {'✅' if ok else '❌'} {name}   ({detail})")
        if not ok:
            failed += 1
    print("-" * 60)
    if failed:
        print(f"❌ {failed} 项变异未被抓到 ⇒ 守门是假绿")
        return 1
    print(f"✅ {len(results)} 项变异全部符合预期")
    return 0


# ---------------------------------------------------------------- main
def main() -> int:
    args = sys.argv[1:]
    if "--mutation-test" in args:
        return mutation_test()

    root = REPO
    if "--root" in args:
        i = args.index("--root")
        if i + 1 >= len(args):
            print("--root 需要一个目录参数")
            return 2
        root = os.path.abspath(args[i + 1])

    files = _walk_files(root) if "--root" in args else _git_files(root)
    if not files:
        print(f"⚠️ 在 {root} 下没收集到任何文件 ⇒ 无法判定")
        return 2

    hits, scanned = scan(root, files)
    print("\n".join(report(root, hits, scanned)))
    if "--print" in args:
        return 0
    return 1 if any(not h["whitelisted"] for h in hits) else 0


if __name__ == "__main__":
    sys.exit(main())
