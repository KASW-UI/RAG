#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""数据源口令占位符**形态**守门 —— rpc 模块的 `POSTGRES_PASSWORD` 不得带默认值。

🔴 **为什么需要这条守门（2026-10-30 · G1 lane 发现，本层独立复核后接手）**
    Go 真源侧**全部** rpc yaml 的口令占位符都是**裸** `${POSTGRES_PASSWORD}`（**无默认值**），
    且 go-zero 的配置加载是 `loader([]byte(os.ExpandEnv(string(content))), v)`
    （`core/conf/config.go`）⇒ ⇒ **`os.ExpandEnv` 不认 `${VAR:default}` 语法** ⇒
    缺变量 ⇒ **展开成空串** ⇒ 连库认证失败 ⇒ ⇒ **Go 侧是 fail-fast**。
    ⇒ ⇒ 而 RAG 侧用 Spring 的 `${VAR:default}` 语义 ⇒ **缺变量时会用那个默认值连上去** ⇒
    ⇒ ⇒ **RAG 比 Go 松** ⇒ ⇒ **真漂移**（不是「更严的有意偏离」⇒ ⇒ **不需要签字**）。

    ⚠️ **本轮实测**（Go 工具链在位 ⇒ 直接跑）：
        裸   `${POSTGRES_PASSWORD}`        ⇒ `""`   （未设变量 ⇒ 空串 ⇒ fail-fast）
        带默认 `${POSTGRES_PASSWORD:pw}`  ⇒ `""`   ⇒ ⇒ **`os.ExpandEnv` 把它整个当成变量名查**
        已设  `${POSTGRES_PASSWORD}`        ⇒ `"s3cret"` ✔
    ⇒ ⇒ 所以**任何 `:default` 形态在 Go 侧都拿不到默认值** ⇒ ⇒ 形态判据成立。

✅ **本守门拦什么**
    凡 **rpc 模块**的 `src/main/resources/application.y*ml` 里出现
    **`${POSTGRES_PASSWORD:<默认值>}`**（带冒号默认值）⇒ 报红。
    ⇒ ⇒ 只拦**形态**，不拦明文 ⇒ ⇒ 与 `check-no-prod-secrets.py`（拦明文密钥）**互补不重叠**。

⚠️ **白名单是【豁免】，不是【免检】**
    · 每条必须写清「谁该修 + 为什么现在合法 + 修完怎么删」；
    · 条目已无命中 ⇒ 🟡 提示「可从白名单删除」（**不放过**）；
    · ⚠️ **白名单只写「已定位、有人负责、归期明确」的项** ⇒ ⇒ 任何**新出现**的违例一律报红。

⚠️ **枚举口径**（别踩 `PIT-SCAN-01` 的坑）
    rpc 模块的路径深度**不一致**（多数域 = `service/<域>/<域>-rpc/…`；
    users 域 = `service/users/<子域>/<子域>-rpc/…` ⇒ **多一层**）⇒
    ⇒ **本脚本按 git 文件清单 + 路径含 `/rpc/` 过滤**，**不用 `glob` 猜形状**。

用法
----
    python3 tools/difftest/check-datasource-password-form.py                # 正常跑（EXIT=0/1）
    python3 tools/difftest/check-datasource-password-form.py --print        # 只打印，不判红
    python3 tools/difftest/check-datasource-password-form.py --root <dir>   # 指定扫描根
    python3 tools/difftest/check-datasource-password-form.py --mutation-test  # 内置变异自测
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WHITELIST = os.path.join(REPO, "tools", "difftest", "conf", "datasource-password-whitelist.txt")

# `${POSTGRES_PASSWORD:<默认值>}` —— 冒号后有内容才算「带默认值」
DEFAULT_FORM_RE = re.compile(r"\$\{POSTGRES_PASSWORD:([^}]*)\}")
ANY_FORM_RE = re.compile(r"\$\{POSTGRES_PASSWORD[^}]*\}")


def _git_files(root: str) -> list[str]:
    try:
        r = subprocess.run(["git", "-C", root, "ls-files"], capture_output=True, text=True)
        if r.returncode == 0:
            return [x for x in r.stdout.split("\n") if x]
    except Exception:
        pass
    return []


def _walk_files(root: str) -> list[str]:
    out = []
    for dp, dns, fns in os.walk(root):
        dns[:] = [d for d in dns if d not in (".git", "target", "__pycache__")]
        for fn in fns:
            out.append(os.path.relpath(os.path.join(dp, fn), root))
    return out


def _is_rpc_resource(rel: str) -> bool:
    """只管 **rpc 模块**的运行配置：`.../<域>-rpc/src/main/resources/application.y*ml`。

    ⚠️ **靠「模块目录名以 `-rpc` 结尾」判定**，**不用 `glob` 猜形状**
    （users 域多一层 ⇒ 猜形状必漏 ⇒ 那是 `PIT-SCAN-01`）。

    ⚠️ **踩过的坑**：第一版写的是「路径含 `/rpc/`」⇒ ⇒ **一个都没匹配上**
    （真实目录名是 `article-rpc` / `message-rpc` ⇒ **没有** `/rpc/` 这个段）
    ⇒ ⇒ 守门「扫了 0 份」却打印「✅ 全部同形」⇒ ⇒ **假绿** ⇒ ⇒
    是**内置变异自测**抓到的 ⇒ ⇒ ⇒ 所以「扫了 0 份 ⇒ 无法判定」必须单列一条。
    """
    rel = rel.replace("\\", "/")
    parts = rel.split("/")
    if "src" not in parts:
        return False
    i = parts.index("src")
    if parts[i + 1:i + 3] != ["main", "resources"]:
        return False
    if i < 1 or not parts[i - 1].endswith("-rpc"):
        return False
    name = parts[-1]
    return name.startswith("application.yml") or name.startswith("application.yaml")


def _module_of(rel: str) -> str:
    """文件 ⇒ 模块标识（用于白名单匹配与报表）。"""
    rel = rel.replace("\\", "/")
    parts = rel.split("/")
    try:
        i = parts.index("src")
    except ValueError:
        return rel
    return "/".join(parts[:i])


def load_whitelist(root: str) -> dict[str, str]:
    """白名单（显式数据文件）⇒ {模块标识: 理由}。"""
    path = os.path.join(root, "tools", "difftest", "conf", "datasource-password-whitelist.txt")
    if not os.path.exists(path):
        return {}
    out: dict[str, str] = {}
    for line in open(path, encoding="utf-8").read().split("\n"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        out[parts[0].strip()] = parts[1].strip()
    return out


def scan(root: str, files: list[str]) -> tuple[list[dict], int]:
    hits: list[dict] = []
    scanned = 0
    for rel in files:
        if not _is_rpc_resource(rel):
            continue
        p = os.path.join(root, rel)
        if not os.path.exists(p):
            continue
        scanned += 1
        try:
            text = open(p, encoding="utf-8", errors="replace").read()
        except Exception:
            continue
        forms = DEFAULT_FORM_RE.findall(text)
        if not forms:
            continue
        for m in DEFAULT_FORM_RE.finditer(text):
            line_no = text[:m.start()].count("\n") + 1
            hits.append({
                "file": rel,
                "module": _module_of(rel),
                "line": line_no,
                "form": m.group(0),
                "default": m.group(1),
            })
    return hits, scanned


def report(root: str, hits: list[dict], scanned: int, wl: dict[str, str]) -> list[str]:
    lines = ["数据源口令占位符形态守门 · rpc 模块的 POSTGRES_PASSWORD 不得带默认值"]
    lines.append("=" * 72)
    lines.append(f"扫描 rpc 模块运行配置 {scanned} 份 · 违例 {len(hits)} 处")
    if not hits:
        lines.append("✅ 全部 rpc 模块的占位符都是裸 `${POSTGRES_PASSWORD}`（与 Go 逐字同形）")
    by_module: dict[str, list[dict]] = {}
    for h in hits:
        by_module.setdefault(h["module"], []).append(h)
    for mod in sorted(by_module):
        why = wl.get(mod)
        mark = "⚠️ 白名单内（已知未修）" if why else "🔴 新违例"
        lines.append(f"{mark} · {mod}")
        if why:
            lines.append(f"     归属：{why}")
        for h in by_module[mod]:
            lines.append(f"     · {h['file']}:{h['line']}  `{h['form']}` ⇒ 默认值 `{h['default']}`")
    # 白名单里已无命中的 ⇒ 可剪枝
    hit_mods = set(by_module)
    stale = [m for m in wl if m not in hit_mods]
    if stale:
        lines.append("")
        lines.append("🟡 以下白名单条目**已无命中** ⇒ 可从白名单删除（不放过）：")
        for m in sorted(stale):
            lines.append(f"     · {m}")
    if hits:
        lines.append("")
        lines.append("⇒ ⇒ 修法：把 `${POSTGRES_PASSWORD:<默认值>}` 改成裸 `${POSTGRES_PASSWORD}`")
        lines.append("   （Go 侧 `os.ExpandEnv` 不认默认值语法 ⇒ 裸写法 = 缺变量即空串 = fail-fast = 与 Go 同形）")
    return lines


def mutation_test() -> int:
    """变异自测：① 带默认值 ⇒ 必须报红 ② 裸写法 ⇒ 必须不红 ③ 白名单 ⇒ 不红但要报。"""
    print("数据源口令形态守门 · 内置变异自测")
    print("=" * 60)
    results = []

    def case(name: str, ok: bool, detail: str = "") -> None:
        results.append((name, ok, detail))
        print(f"  {'✅' if ok else '❌'} {name}" + (f"   ({detail})" if detail else ""))

    with tempfile.TemporaryDirectory(prefix="dspw_mut_") as tmp:
        root = os.path.abspath(tmp)

        # ⓪ 扫 0 份 ⇒ 必须「无法判定」（不得报绿）⇒ ⇒ **真跑脚本验退出码**
        with tempfile.TemporaryDirectory(prefix="dspw_empty_") as empty:
            r = subprocess.run([sys.executable, os.path.abspath(__file__), "--root", empty],
                               capture_output=True, text=True)
            case("⓪ 空目录 ⇒ 扫 0 份 ⇒ 退出码 2（无法判定，不是绿）",
                 r.returncode == 2, f"实际 EXIT={r.returncode}\n{r.stdout[-400:]}")
            case("⓪ 空目录 ⇒ 输出里**不得**出现「✅ 全部 … 同形」",
                 "✅" not in r.stdout, r.stdout[-400:])

        def make(path: str, content: str) -> None:
            full = os.path.join(root, path)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            open(full, "w", encoding="utf-8").write(content)

        wl_rel = "tools/difftest/conf/datasource-password-whitelist.txt"
        make(wl_rel, "# 模块\t归属\n")

        # ① 带默认值 ⇒ 红
        make("service/demo/demo-rpc/src/main/resources/application.yml",
             "spring:\n  datasource:\n    password: ${POSTGRES_PASSWORD:password}\n")
        hits, _ = scan(root, _walk_files(root))
        case("① 带默认值 ⇒ 抓到违例", len(hits) == 1 and hits[0]["default"] == "password",
             f"抓到 {len(hits)}")

        # ② 裸写法 ⇒ 不红
        make("service/demo/demo-rpc/src/main/resources/application.yml",
             "spring:\n  datasource:\n    password: ${POSTGRES_PASSWORD}\n")
        hits, _ = scan(root, _walk_files(root))
        case("② 裸写法 ⇒ 不报", len(hits) == 0, f"报了 {len(hits)}")

        # ③ 白名单 ⇒ 不判红但要报出来
        make(wl_rel, "# 模块\t归属\nservice/demo/demo-rpc\tG3 面 · 载体波次内修\n")
        make("service/demo/demo-rpc/src/main/resources/application.yml",
             "spring:\n  datasource:\n    password: ${POSTGRES_PASSWORD:password}\n")
        hits, _ = scan(root, _walk_files(root))
        wl = load_whitelist(root)
        unknown = [h for h in hits if h["module"] not in wl]
        known = [h for h in hits if h["module"] in wl]
        case("③ 白名单内 ⇒ 不判红", len(unknown) == 0 and len(known) == 1,
             f"未知 {len(unknown)} / 已知 {len(known)}")

        # ④ 路径深度不同（users 域多一层）⇒ 也要抓到
        make("service/users/user/user-rpc/src/main/resources/application.yaml",
             "spring:\n  datasource:\n    password: ${POSTGRES_PASSWORD:password}\n")
        hits, _ = scan(root, _walk_files(root))
        case("④ 深度不一致的路径（users 域多一层）⇒ 抓到",
             any("users/user/user-rpc" in h["file"] for h in hits), f"共 {len(hits)}")

    failed = [n for n, ok, _ in results if not ok]
    print("-" * 60)
    if failed:
        print(f"❌ {len(failed)} 项变异未被抓到 ⇒ 守门是假绿")
        return 1
    print(f"✅ {len(results)} 项变异全部符合预期")
    return 0


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
    wl = load_whitelist(root)
    print("\n".join(report(root, hits, scanned, wl)))
    if scanned == 0:
        # ⚠️ **扫了 0 份 ⇒ 无法判定** ⇒ ⇒ **不得报绿**
        #    （第一版就是在这里给了「✅ 全部同形」，而它其实一份都没扫 ⇒ 假绿）
        print("⚠️ 扫到 0 份 rpc 模块运行配置 ⇒ **无法判定**（不是「通过」）")
        return 2
    if "--print" in args:
        return 0
    unknown = [h for h in hits if h["module"] not in wl]
    return 1 if unknown else 0


if __name__ == "__main__":
    sys.exit(main())
