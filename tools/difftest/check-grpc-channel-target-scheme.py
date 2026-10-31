#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-grpc-channel-target-scheme.py —— 测试源码里手搓 gRPC channel 必须显式写 `dns:///` scheme。

════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════
**devh 把 Nacos discovery resolver 注册进 grpc-java 的「进程级全局」registry**
⇒ ⇒ 同一个 JVM 里跑过**任意一个** `@SpringBootTest` 之后
⇒ ⇒ 任何**无 scheme** 的 channel target 会被 discovery resolver 接管
⇒ ⇒ 把 `127.0.0.1:P` 当**服务名**去 Nacos 查 ⇒ 客户端拿 `UNAVAILABLE`
⇒ ⇒ 表现为「**单跑绿、同跑红**」（surefire 复用同一个 JVM ⇒ 跨测试类顺序依赖）。

机制底账（**javap 实测**，2026-10-31 · 合并收口执行者；不采信转述）：

  · `net/devh/boot/grpc/client/autoconfigure/GrpcClientAutoConfiguration`
      `grpcNameResolverRegistration(List<io.grpc.NameResolverProvider>)`
      → `new NameResolverRegistration(...)`
      → `io.grpc.NameResolverRegistry.getDefaultRegistry()`      ← **进程级全局**
      ⇒ `NameResolverRegistration.destroy()` 只在 **Spring 上下文关闭**时反注册
      ⇒ surefire 复用 JVM ⇒ 上下文缓存不关 ⇒ **注册一直在**
  · `net/devh/boot/grpc/client/nameresolver/DiscoveryClientResolverFactory`
      `getDefaultScheme()` → `"discovery"`（`ldc #39 // String discovery`）
      ⇒ 无显式 scheme 的 target 命中它 ⇒ 把 target 当 serviceId 去 Nacos 拉实例

实测日志原文（G3 lane · 2026-10-31 采集）：

  io.grpc.internal.ManagedChannelImpl :
    [Channel<11>: (127.0.0.1:22101)] Failed to resolve name.
    status=Status{code=UNAVAILABLE,
    description=Failed to update server list for 127.0.0.1:22101,
    cause=java.lang.RuntimeException: Can not get hosts from nacos server.
    serviceId: 127.0.0.1:22101}

⚠️ **这不是「Nacos 在不在线」的问题** —— 校验与解析都发生在**任何网络调用之前**。

⚠️ **本机制在本仓「早已认知」但「无机器守门」** ⇒ ⇒ 所以**必然漏改**：
  · 早前记录：`docs/DRIFT/global/lanes/L6-x5gate.md:60` / `L8-slowgate2.md:62`
    已把「channel target 必须显式写 `dns:///` scheme」列为「写测试样板 ⑥」；
  · 但那只是 **markdown 清单** ⇒ ⇒ 新写的 test 不会去查这份清单
    ⇒ ⇒ 2026-10-31 一次人工盘点就漏掉 **9 处 / 9 个文件**（跨 users / comment / hot / common 四域）
    ⇒ ⇒ **这才是要落机器守门的理由**（人工盘点必漏）。

════════════════════════════════════════════════════════════════════════
【判据】
════════════════════════════════════════════════════════════════════════
**扫描面** = `service/**/*.java`（含 `src/main` 与 `src/test`；主干两侧实测 0 例外命中）。

**违规** = 文件里出现一个**处于代码态**（不在注释里、不在字符串字面量里）的
`forAddress(` 标记。

  · `forAddress(` 是 grpc-java 里 channel builder 的**唯一**「按地址且不显式 scheme」的入口
    ⇒ ⇒ 命中它 ⇒ 造出来的 channel **一定**走默认 scheme ⇒ **一定**会被接管
  · 判**标记**而不是判 `ManagedChannelBuilder.forAddress(` 这个完整形态
    ⇒ ⇒ `io.grpc.ManagedChannelBuilder.forAddress(` / 换行拆开的 `.forAddress(` /
      `import static io.grpc.ManagedChannelBuilder.forAddress;` 之后的裸调用
      **都逃不掉**（这三种都是同一形态，历史上分三次漏改过）

⚠️ **注释 / 字符串字面量里的 `forAddress(` 不算违规** ——
  例：`UserApiGrpcNegotiationTest` 的注释「`forAddress(String,int)` 用默认 TLS 凭据」
  是在**解释**这个 API，不是在调用它 ⇒ ⇒ 判据必须做**词法级**剥离
  ⇒ ⇒ 否则守门要么假红、要么被迫给注释开豁免（豁免会掩盖真实调用点）。

**例外集 = 点位级**（不是文件级！）——
  ⚠️ **文件级豁免 = 永久白地**：一个文件一旦进了例外集，它**之后新增**的
     `forAddress(` 就再也抓不到 ⇒ ⇒ 棘轮白设
     ⇒ ⇒ 所以例外登记到「**文件 + 归一化代码片段**」这一层：
     同文件里**新形态**的违规仍然转红。
  ⚠️ 每条例外**必须写理由**（没理由 ⇒ `E3`）。
  ⚠️ **例外失效也要红**（`E2`）：文件里的那个点修好了 ⇒ 例外条目必须同步删
     ⇒ ⇒ 否则例外集会长成永久豁免。

════════════════════════════════════════════════════════════════════════
【为什么落在 `tools/difftest/` 而不是某个域的 test 目录】
════════════════════════════════════════════════════════════════════════
① 这是**跨域**的机制面（users / comment / hot / common …）⇒ 按 §5.3f「域侧能自修的
   域侧修，共享层缺陷转派 G4」⇒ **代码修法分给各域**（common 那 1 处归 G4）；
② 但「**不许再犯**」这件事必须**一处**说了算 ⇒ 放 `tools/difftest/`（机器口径：
   `tools/**` 无 lane 拥有 ⇒ 归合并收口执行者）。
③ `tools/difftest/*.java` 里的 7 处 `forAddress(` **不在扫描面** ——
   它们是**独立 `main()` 跑者**（无 Spring 上下文 ⇒ devh 永不注册 ⇒ 不会被接管）
   ⇒ ⇒ 排除它们是**按机制**排除，不是按「先例」排除。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
  0 = 通过（无未登记违规，且无失效例外）
  1 = 有违规 / 例外定义错 / 有失效例外（打印 `E1` / `E2` / `E3`）
  2 = 用法错误
  （`--mutation-test` 模式下：0 = 全部变异按预期被捕获；1 = 有变异未被捕获）

用法：
    python3 tools/difftest/check-grpc-channel-target-scheme.py
    python3 tools/difftest/check-grpc-channel-target-scheme.py --mutation-test
    python3 tools/difftest/check-grpc-channel-target-scheme.py --root <影子目录>
"""
from __future__ import annotations

import re
import shutil
import sys
import tempfile
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

SCAN_GLOB_ROOT = "service"
TOKEN = "forAddress("

FIX_HINT = (
    'ManagedChannelBuilder.forAddress("127.0.0.1", port)'
    '  ⇒  ManagedChannelBuilder.forTarget("dns:///127.0.0.1:" + port)'
    "\n             ⇒ 显式 scheme 直接选定 grpc-java 自带的 DNS resolver ⇒ 不受 devh 注册影响。"
)

# ---------------------------------------------------------------- 例外集（点位级）
# 结构：{ 相对路径: [ (归一化代码片段, 理由), ... ] }
# ⚠️ 片段 = 该违规行的**代码态**文本（剥注释、**保留字符串字面量**、空白归一化）
#    ⇒ 它同时是「匹配键」与「人类可读的豁免对象」
# ⚠️ 修好一处就删一条 ⇒ 否则触发 `E2`（例外已失效）
EXCEPTIONS: dict[str, list[tuple[str, str]]] = {
}


# ---------------------------------------------------------------- 词法级处理
def _lex(text: str, keep_strings: bool) -> str:
    """Java 源码词法扫描：把注释（可选地也把字符串字面量）替换成等长空格。

    ⇒ `keep_strings=False` ⇒ 只留**代码态**字符 ⇒ 用于「是否违规」判定
    ⇒ `keep_strings=True`  ⇒ 只剥注释、保留字面量 ⇒ 用于「取可读片段」
    ⚠️ 词法状态机：NORMAL / LINE_COMMENT / BLOCK_COMMENT / STRING / CHAR
       ⚠️ 字符串里的 `//` 不能当注释起手（URL！）⇒ 必须先判字符串态。
    """
    out = list(text)
    i = 0
    n = len(text)
    state = "NORMAL"
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "NORMAL":
            if ch == "/" and nxt == "/":
                state = "LINE_COMMENT"
                out[i] = out[i + 1] = " "
                i += 2
                continue
            if ch == "/" and nxt == "*":
                state = "BLOCK_COMMENT"
                out[i] = out[i + 1] = " "
                i += 2
                continue
            if ch == '"':
                state = "STRING"
                if not keep_strings:
                    out[i] = " "
                i += 1
                continue
            if ch == "'":
                state = "CHAR"
                if not keep_strings:
                    out[i] = " "
                i += 1
                continue
            i += 1
            continue
        if state == "LINE_COMMENT":
            if ch == "\n":
                state = "NORMAL"
            else:
                out[i] = " "
            i += 1
            continue
        if state == "BLOCK_COMMENT":
            if ch == "*" and nxt == "/":
                out[i] = out[i + 1] = " "
                i += 2
                state = "NORMAL"
                continue
            if ch != "\n":
                out[i] = " "
            i += 1
            continue
        # STRING / CHAR
        quote = '"' if state == "STRING" else "'"
        if ch == "\\":
            if not keep_strings:
                out[i] = " "
                if i + 1 < n and text[i + 1] != "\n":
                    out[i + 1] = " "
                i += 2
            else:
                i += 2
            continue
        if ch == quote:
            if not keep_strings:
                out[i] = " "
            state = "NORMAL"
            i += 1
            continue
        if not keep_strings and ch != "\n":
            out[i] = " "
        i += 1
    return "".join(out)


def code_only(text: str) -> str:
    """只保留代码态字符（注释与字面量都被剥成空格）。"""
    return _lex(text, keep_strings=False)


def strip_comments(text: str) -> str:
    """只剥注释，保留字符串字面量（用于取可读片段）。"""
    return _lex(text, keep_strings=True)


def normalize(line: str) -> str:
    """空白归一化（片段比对键）。"""
    return re.sub(r"\s+", " ", line).strip()


# ---------------------------------------------------------------- 扫描
def scan(root: Path) -> tuple[dict[str, list[tuple[int, str]]], set[str]]:
    """返回 ({相对路径: [(行号, 归一化片段)]}, 扫描到的 java 文件集合)。"""
    hits: dict[str, list[tuple[int, str]]] = {}
    seen: set[str] = set()
    base = root / SCAN_GLOB_ROOT
    if not base.is_dir():
        return hits, seen
    for path in sorted(base.rglob("*.java")):
        # ⚠️ 跳过构建产物副本（上一次构建的旧副本会被当源码扫 ⇒ 同一违规算两遍 ⇒
        #    键永远匹配不上 ⇒ 门禁恒红 —— 教训 PIT-GLOBAL-029）
        if "target" in path.parts:
            continue
        rel = path.relative_to(root).as_posix()
        seen.add(rel)
        try:
            raw = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        code_lines = code_only(raw).splitlines()
        readable_lines = strip_comments(raw).splitlines()
        for idx, line in enumerate(code_lines, start=1):
            if TOKEN in line:
                snippet = (
                    normalize(readable_lines[idx - 1])
                    if idx - 1 < len(readable_lines)
                    else normalize(line)
                )
                hits.setdefault(rel, []).append((idx, snippet))
    return hits, seen


def evaluate(root: Path) -> list[tuple[str, str]]:
    """返回 [(错误码, 说明)]；空列表 = 通过。"""
    fails: list[tuple[str, str]] = []
    hits, seen = scan(root)

    # E1：未登记的违例
    for rel in sorted(hits):
        allowed = Counter(snip for snip, _ in EXCEPTIONS.get(rel, []))
        actual = Counter(snip for _, snip in hits[rel])
        for snip, cnt in actual.items():
            leftover = cnt - allowed.get(snip, 0)
            if leftover > 0:
                lines = [str(ln) for ln, s in hits[rel] if s == snip]
                fails.append((
                    "E1",
                    f"{rel}:{','.join(lines)} —— 未登记的 `forAddress(` 违规"
                    f"（该形态出现 {cnt} 次，例外只放行 {allowed.get(snip, 0)} 次）"
                    f"⇒ 片段：{snip!r}",
                ))

    # E2：例外已失效（登记的片段已不在 ⇒ 条目须删）
    for rel, entries in sorted(EXCEPTIONS.items()):
        present = Counter(snip for _, snip in hits.get(rel, []))
        for snip, _ in entries:
            if present.get(snip, 0) <= 0:
                fails.append((
                    "E2",
                    f"{rel} —— 例外已失效（登记的片段已不在该文件里）⇒ 请删掉这条例外"
                    f" ⇒ 片段：{snip!r}",
                ))

    # E3：例外集本身不合法
    for rel, entries in sorted(EXCEPTIONS.items()):
        if rel not in seen:
            fails.append((
                "E3",
                f"{rel} —— 例外条目指向的文件不在扫描面里"
                f"（路径写错 ⇒ 这条豁免等于不存在）",
            ))
        for snip, reason in entries:
            if not reason.strip():
                fails.append(("E3", f"{rel} —— 例外条目没有写理由 ⇒ {snip!r}"))
    return fails


# ---------------------------------------------------------------- 变异测试
def _copy_scan_tree(src: Path, dst: Path) -> None:
    """把扫描面（service/**/*.java，排除 target/）复制到影子目录。"""
    base = src / SCAN_GLOB_ROOT
    for path in sorted(base.rglob("*.java")):
        if "target" in path.parts:
            continue
        rel = path.relative_to(src)
        target = dst / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)


# 变异锚点：① 一个**已登记**的文件（测点位级精度 ⇒ 同文件新形态仍须转红）
#           ② 一个**未登记**的文件（测基本检出）
EXCEPTED_FILE = (
    "service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/client/"
    "CommentOutboundDeadlineCarrierGateTest.java"
)
UNEXCEPTED_FILE = (
    "service/like/like-rpc/src/test/java/com/rag/like/like/rpc/"
    "LikeRpcGrpcInterceptorWiringTest.java"
)

MUTATIONS: list[tuple[str, str, str, str]] = [
    (
        "M1 已登记文件里新增**同文件不同形态**的调用点 ⇒ 必须转红 E1（点位级精度）",
        "E1",
        EXCEPTED_FILE,
        "\n        // MUTATION-M1\n"
        '        ManagedChannelBuilder.forAddress("127.0.0.1", 9999).usePlaintext().build();\n',
    ),
    (
        "M2 未登记文件里新增调用点 ⇒ 必须转红 E1",
        "E1",
        UNEXCEPTED_FILE,
        "\n        // MUTATION-M2\n"
        '        ManagedChannelBuilder.forAddress("127.0.0.1", 9999).usePlaintext().build();\n',
    ),
    (
        "M3 同形字样放进注释 ⇒ 必须**不**报违规",
        "",
        EXCEPTED_FILE,
        '\n        // MUTATION-M3 forAddress("127.0.0.1", 1)\n',
    ),
    (
        "M4 同形字样放进字符串字面量 ⇒ 必须**不**报违规",
        "",
        EXCEPTED_FILE,
        '\n        String s = "forAddress(127.0.0.1, 1)";\n',
    ),
]


def run_mutation_test() -> int:
    print("gRPC channel target scheme 守门 · 变异验证")
    print("=" * 68)

    base_fails = evaluate(REPO_ROOT)
    if base_fails:
        print("❌ 基线不绿 ⇒ 先修代码/例外集再谈变异")
        for code, msg in base_fails:
            print(f"  [{code}] {msg}")
        return 1
    print("  基线绿（0 项违规）")
    print("-" * 68)

    bad = 0
    for label, expect, rel, inject in MUTATIONS:
        shadow = Path(tempfile.mkdtemp(prefix="grpc-target-scheme-"))
        try:
            _copy_scan_tree(REPO_ROOT, shadow)
            target = shadow / rel
            if not target.exists():
                print(f"  ❌ {label} ⇒ 变异锚点未命中（守门脚本与源码脱节）")
                bad += 1
                continue
            with target.open("a", encoding="utf-8") as fh:
                fh.write(inject)
            fails = evaluate(shadow)
            codes = {c for c, _ in fails}
            if expect == "":
                if codes & {"E1", "E2", "E3"}:
                    print(f"  ❌ {label} ⇒ 期望不报违规，实际报了 {sorted(codes)}")
                    bad += 1
                else:
                    print(f"  ✅ {label} ⇒ 未误报（判据不把注释/字面量当违规）")
            else:
                if expect not in codes:
                    print(f"  ❌ {label} ⇒ 期望 {expect} 转红，实际 {sorted(codes) or '全绿'}")
                    bad += 1
                else:
                    print(f"  ✅ {label} ⇒ 转红 {sorted(codes)}")
        finally:
            shutil.rmtree(shadow, ignore_errors=True)

    # M5：例外失效检测 ⇒ 把某例外点的片段改掉 ⇒ 必须报 E2
    shadow = Path(tempfile.mkdtemp(prefix="grpc-target-scheme-"))
    try:
        _copy_scan_tree(REPO_ROOT, shadow)
        rel = next(k for k in EXCEPTIONS if k.startswith("service/hot/hot-api/"))
        p = shadow / rel
        text = p.read_text(encoding="utf-8")
        p.write_text(text.replace("forAddress(", "forTargetX("), encoding="utf-8")
        fails = evaluate(shadow)
        codes = {c for c, _ in fails}
        if "E2" not in codes:
            print(f"  ❌ M5 例外失效检测 ⇒ 期望 E2 转红，实际 {sorted(codes) or '全绿'}")
            bad += 1
        else:
            print("  ✅ M5 例外失效检测 ⇒ 转红 ['E2']")
    finally:
        shutil.rmtree(shadow, ignore_errors=True)

    print("-" * 68)
    if bad:
        print(f"❌ {bad} 条变异未被按预期捕获 ⇒ 守门有假绿/假红")
        return 1
    print(f"✅ PASS（{len(MUTATIONS) + 1}/{len(MUTATIONS) + 1} 条变异全部按预期被捕获）")
    return 0


# ---------------------------------------------------------------- main
def main(argv: list[str]) -> int:
    if "--mutation-test" in argv:
        return run_mutation_test()

    root = REPO_ROOT
    if "--root" in argv:
        try:
            idx = argv.index("--root")
            root = Path(argv[idx + 1]).resolve()
        except (IndexError, ValueError):
            print("用法：--root <目录>", file=sys.stderr)
            return 2

    fails = evaluate(root)

    print("gRPC channel target scheme 守门（测试源码手搓 channel 必须显式 `dns:///`）")
    print("=" * 68)
    print("  机制：devh 把 Nacos discovery resolver 注册进 grpc-java **进程级全局**")
    print("        NameResolverRegistry ⇒ 无 scheme 的 target 被接管去 Nacos 查服务名")
    print("        ⇒ UNAVAILABLE ⇒ 表现为「单跑绿、同跑红」（surefire 复用 JVM）")
    print()
    hits, seen = scan(root)
    print(f"  扫描面：{SCAN_GLOB_ROOT}/**/*.java（排除 target/）· 共 {len(seen)} 个文件")
    print(f"  例外集：{sum(len(v) for v in EXCEPTIONS.values())} 个点位"
          f"（分布在 {len(EXCEPTIONS)} 个文件 · 点位级 · 修好即删）")
    print()

    if fails:
        for code, msg in fails:
            print(f"  🔴 [{code}] {msg}")
        print()
        print("  修法（把「按地址」改成「按 target + 显式 scheme」）：")
        print(f"    {FIX_HINT}")
        print()
        print("  Go 侧对应物：Go 的 grpc client 不走服务发现 resolver（直连 target），")
        print("              ⇒ RAG 侧凡是要「直连某个地址」的地方，都必须显式写 `dns:///`。")
        print("=" * 68)
        return 1

    print("  ✅ 无未登记违规 · 无失效例外")
    for rel in sorted(hits):
        for ln, snip in hits[rel]:
            print(f"     · {rel}:{ln}（已登记为例外）")
    print("=" * 68)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
