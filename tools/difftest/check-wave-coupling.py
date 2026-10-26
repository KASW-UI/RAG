#!/usr/bin/env python3
"""守门：跨域耦合矩阵的快照必须与仓库现状一致（分波方案的事实底座）。

背景
----
`docs/DRIFT/_cross/cross-domain-coupling.md` 记录了「哪些域依赖哪些域」的**实测矩阵**，
并据此推出**分波方案**（§二）与**共享层写权规则**（§三）。

⚠️ **这份矩阵是分波方案的唯一依据** ⇒ 一旦有人新增/删除一条 gRPC 边或一个跨域 Kafka topic，
    而矩阵没跟着更新 ⇒ **分波方案就建立在过期事实上** ⇒ 必须转红。

⇒ 本守门做的是**漂移检测**：从仓库**重算**矩阵，与文档里的快照**逐项比对**。

查什么
------
1. **编译期依赖度数**（`pom.xml`）：`service/common` 被几个模块依赖、`service/users/users-common` 被几个依赖、
   其余 `<域>-common` 被几个依赖 ⇒ 用来判「谁是共享层、谁是私有库」。
2. **gRPC 契约边**（`@GrpcClient` 注解）：`<消费域> → <被依赖域集合>` ⇒ 方向可靠。
3. **跨域 Kafka topic**：只保留**出现在 ≥2 个域**里的 topic ⇒ 记录「哪些域参与」。
   ⚠️ **方向（谁生产谁消费）不由本守门判定** —— 生产/消费角色在源码里分处
   `*Topics` 配置类与 `@KafkaListener`，两者并非同一套声明 ⇒ 机械推断容易错。
   ⇒ 方向以文档里**人工核实过的 L3 证据**（附代码位置）为准，本守门只守「topic 集合 + 参与域」。
4. **`users-common` 逐类跨域引用**（**全限定名**扫描）⇒ 用来判定每个类该归共享层还是留 users 域。

⚠️ **第 4 项必须用全限定名**（如 `com.rag.users.common.jwt.JwtUtil`）：
   裸类名会撞各域同名类（`ErrorCode` / `SchemaInitializer` / `Response`）⇒ **假阳性**。

退出码
------
0 = 矩阵与仓库一致；1 = 有漂移（逐项点名）。

复现
----
python3 tools/difftest/check-wave-coupling.py          # 守门
python3 tools/difftest/check-wave-coupling.py --print  # 打印当前重算结果（用于更新文档快照）
"""

from __future__ import annotations

import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DOC = REPO / "docs/DRIFT/_cross/cross-domain-coupling.md"

SNAP_BEGIN = "<!-- wave-coupling-snapshot:begin -->"
SNAP_END = "<!-- wave-coupling-snapshot:end -->"

# 范围外的目标（AGENTS.md §一：Go 侧 knowledge/points/security 范围外 ⇒ 不是待办）
OUT_OF_SCOPE = {"security", "knowledge", "points"}


# ------------------------------------------------------------------ 工具
def domain_of(module: str) -> str:
    """`service/article/article-rpc` → `article`；`service/common` → `common`。"""
    parts = module.split("/")
    return parts[1] if len(parts) > 1 else module


def target_domain(target: str) -> str:
    """`@GrpcClient` 目标名 → 域。

    ⚠️ `user-rpc` / `admin-rpc` 都属于 **`users` 域**（两套 api/rpc），
    不能按前缀拆成 `user` / `admin` 两个域（那会让 users 域的出向依赖凭空消失）。
    """
    name = target[: -len("-rpc")] if target.endswith("-rpc") else target
    if name in ("user", "admin"):
        return "users"
    return name


def rel(p: str) -> str:
    try:
        return Path(p).resolve().relative_to(REPO).as_posix()
    except ValueError:
        return Path(p).as_posix()


# -------------------------------------------------- 1. 编译期依赖度数
def scan_pom_degrees() -> dict[str, list[str]]:
    """返回 `{artifactId: [依赖它的模块, ...]}`。"""
    rev: dict[str, set[str]] = defaultdict(set)
    for pom in glob.glob(str(REPO / "service/**/pom.xml"), recursive=True):
        module = rel(pom)[: -len("/pom.xml")]
        text = Path(pom).read_text(encoding="utf-8", errors="ignore")
        # 只取 <dependency> 块里的 groupId/artifactId 配对（跳过 <parent>）
        for block in re.findall(r"<dependency>(.*?)</dependency>", text, re.S):
            g = re.search(r"<groupId>([^<]+)</groupId>", block)
            a = re.search(r"<artifactId>([^<]+)</artifactId>", block)
            if g and a and "rag" in g.group(1):
                rev[a.group(1)].add(module)
    return {k: sorted(v) for k, v in sorted(rev.items())}


# ------------------------------------------------------- 2. gRPC 契约边
def scan_grpc_edges() -> dict[str, list[str]]:
    """返回 `{消费域: [被依赖域, ...]}`（自指已剔除）。"""
    edges: dict[str, set[str]] = defaultdict(set)
    for f in glob.glob(str(REPO / "service/**/src/main/java/**/*.java"), recursive=True):
        module = rel(f).split("/src/")[0]
        dom = domain_of(module)
        text = Path(f).read_text(encoding="utf-8", errors="ignore")
        for target in re.findall(r'@GrpcClient\(\s*(?:value\s*=\s*)?"([^"]+)"', text):
            tdom = target_domain(target)
            if tdom != dom:
                edges[dom].add(tdom)
    return {k: sorted(v) for k, v in sorted(edges.items())}


# ----------------------------------------------- 3. 跨域 Kafka topic
def _yaml_topics(module: str) -> set[str]:
    """模块 yml 里 `key: value` 且 key 以 `topic` 结尾 ⇒ value 是 topic 候选。"""
    out: set[str] = set()
    for yml in glob.glob(str(REPO / module / "src/main/resources/*.y*ml"), recursive=True):
        text = Path(yml).read_text(encoding="utf-8", errors="ignore")
        for key, value in re.findall(r"^\s*([A-Za-z0-9_.-]+)\s*:\s*([^\s#][^\n#]*?)\s*$", text, re.M):
            if key.lower().endswith("topic") and not value.startswith("${"):
                out.add(value.strip().strip("\"'"))
    return out


def scan_cross_domain_topics() -> dict[str, list[str]]:
    """返回 `{topic: [参与的域, ...]}`，只保留出现在 ≥2 个域的 topic。"""
    per_topic: dict[str, set[str]] = defaultdict(set)
    for pom in glob.glob(str(REPO / "service/**/pom.xml"), recursive=True):
        module = rel(pom)[: -len("/pom.xml")]
        dom = domain_of(module)
        # ① yml 声明
        for t in _yaml_topics(module):
            per_topic[t].add(dom)
        # ② Java 里 `@KafkaListener(topics = "字面量")`
        for f in glob.glob(str(REPO / module / "src/main/java/**/*.java"), recursive=True):
            text = Path(f).read_text(encoding="utf-8", errors="ignore")
            for t in re.findall(r'@KafkaListener\([^)]*?topics\s*=\s*"([^"]+)"', text, re.S):
                if not t.startswith("${"):
                    per_topic[t].add(dom)
    return {k: sorted(v) for k, v in sorted(per_topic.items()) if len(v) >= 2}


# ------------------------------------- 4. users-common 逐类跨域引用
def scan_users_common_classes() -> dict[str, list[str]]:
    """返回 `{类名: [引用它的域, ...]}`（**import 判定**）。

    🔴 **必须用 `import` 语句判定，不能全文正则**（2026-10-27 实测）：
    全文匹配会把 **javadoc 里的 `{@code com.rag.users.common.errmsg.ErrorCode}`** 当成真引用
    ⇒ `ErrorCode` 被误判成「common + users 共用」⇒ 实际它**只被 users 域引用**
    （`service/common` 只是注释里提到它）⇒ 误判会把 users 专用类错误地提级到共享层。
    """
    fqn: dict[str, str] = {}
    for f in glob.glob(str(REPO / "service/users/users-common/src/main/java/**/*.java"), recursive=True):
        text = Path(f).read_text(encoding="utf-8", errors="ignore")
        m = re.search(r"^package\s+([\w.]+);", text, re.M)
        if m:
            fqn[Path(f).stem] = f"{m.group(1)}.{Path(f).stem}"
    use: dict[str, set[str]] = defaultdict(set)
    for f in glob.glob(str(REPO / "service/**/src/**/*.java"), recursive=True):
        relp = rel(f)
        if "/users-common/" in relp:
            continue
        dom = domain_of(relp.split("/src/")[0])
        text = Path(f).read_text(encoding="utf-8", errors="ignore")
        imports = set(re.findall(r"^import\s+(?:static\s+)?([\w.]+)\s*;", text, re.M))
        for cls, full in fqn.items():
            if full in imports:
                use[cls].add(dom)
    return {k: sorted(v) for k, v in sorted(use.items())}


def scan_common_radiation() -> dict[str, list[str]]:
    """`service/common` 逐类的跨域引用域（**import 判定**）⇒ 共享层辐射度（§1.4）。"""
    fqn: dict[str, str] = {}
    for f in glob.glob(str(REPO / "service/common/src/main/java/**/*.java"), recursive=True):
        text = Path(f).read_text(encoding="utf-8", errors="ignore")
        m = re.search(r"^package\s+([\w.]+);", text, re.M)
        if m:
            fqn[Path(f).stem] = f"{m.group(1)}.{Path(f).stem}"
    use: dict[str, set[str]] = defaultdict(set)
    for f in glob.glob(str(REPO / "service/**/src/**/*.java"), recursive=True):
        relp = rel(f)
        if relp.startswith("service/common/"):
            continue
        dom = domain_of(relp.split("/src/")[0])
        text = Path(f).read_text(encoding="utf-8", errors="ignore")
        imports = set(re.findall(r"^import\s+(?:static\s+)?([\w.]+)\s*;", text, re.M))
        for cls, full in fqn.items():
            if full in imports:
                use[cls].add(dom)
    return {k: sorted(v) for k, v in sorted(use.items())}


# ------------------------------------------------- 参考：surefire 用例数
def scan_surefire_counts() -> dict[str, int]:
    """各域测试用例数（**仅供参考**，不参与失败判定 —— 依赖 `target/` 是否已构建）。"""
    per: dict[str, int] = defaultdict(int)
    for f in glob.glob(str(REPO / "service/**/target/surefire-reports/*.xml"), recursive=True):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        module = rel(f).split("/target/")[0]
        per[domain_of(module)] += int(root.get("tests", 0))
    return dict(sorted(per.items()))


# ------------------------------------------------------------ 快照比对
def load_doc_snapshot() -> dict | None:
    if not DOC.is_file():
        return None
    text = DOC.read_text(encoding="utf-8")
    if SNAP_BEGIN not in text or SNAP_END not in text:
        return None
    block = text.split(SNAP_BEGIN, 1)[1].split(SNAP_END, 1)[0]
    m = re.search(r"```json\s*(\{.*?\})\s*```", block, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(1))
    except json.JSONDecodeError:
        return None


def current_snapshot() -> dict:
    degrees = scan_pom_degrees()
    # 只保留「跨模块共享」的库：`common` + `*-common`
    shared = {k: v for k, v in degrees.items() if k == "common" or k.endswith("-common")}
    return {
        "pom_dependents": {k: len(v) for k, v in shared.items()},
        "grpc_edges": scan_grpc_edges(),
        "cross_domain_topics": scan_cross_domain_topics(),
        "users_common_classes": scan_users_common_classes(),
        "common_radiation": scan_common_radiation(),
    }


def diff(expected: dict, actual: dict) -> list[str]:
    out: list[str] = []
    for key in (
        "pom_dependents",
        "grpc_edges",
        "cross_domain_topics",
        "users_common_classes",
        "common_radiation",
    ):
        e, a = expected.get(key, {}), actual.get(key, {})
        for k in sorted(set(e) | set(a)):
            if e.get(k) != a.get(k):
                out.append(
                    f"🔴 `{key}` 漂移 · `{k}`\n"
                    f"     文档快照: {e.get(k)}\n"
                    f"     仓库现状: {a.get(k)}"
                )
    return out


def main() -> int:
    argv = sys.argv[1:]
    actual = current_snapshot()
    surefire = scan_surefire_counts()

    if "--print" in argv:
        print(json.dumps(actual, ensure_ascii=False, indent=2, sort_keys=True))
        return 0

    print("跨域耦合矩阵漂移守门")
    print("=" * 60)
    print(f"  编译期共享库度数: {actual['pom_dependents']}")
    print(f"  gRPC 契约边: {len(actual['grpc_edges'])} 个域有出向依赖")
    print(f"  跨域 Kafka topic: {len(actual['cross_domain_topics'])} 个")
    print(f"  users-common 逐类跨域引用: {len(actual['users_common_classes'])} 类")
    print(f"  service/common 逐类辐射度: {len(actual['common_radiation'])} 类（被外部引用）")
    print("-" * 60)
    print("  参考 · surefire 用例数（不参与判定）:")
    for dom, n in surefire.items():
        print(f"    {dom:<10}{n:>5}")
    print(f"    {'合计':<10}{sum(surefire.values()):>5}")
    print("-" * 60)

    expected = load_doc_snapshot()
    if expected is None:
        print("❌ FAIL\n")
        print(f"🔴 文档快照缺失或不可解析：{rel(str(DOC))}")
        print("   ⇒ 在该文件里放一段标记包裹的 JSON 快照：")
        print(f"     {SNAP_BEGIN}")
        print("     ```json")
        print("     { ... }")
        print("     ```")
        print(f"     {SNAP_END}")
        print("   ⇒ 用 `python3 tools/difftest/check-wave-coupling.py --print` 生成当前值")
        return 1

    failures = diff(expected, actual)
    if failures:
        print("❌ FAIL\n")
        for f in failures:
            print(f)
            print()
        print(f"⇒ {len(failures)} 项漂移")
        print("⇒ 分波方案的事实底座已过期 ⇒ 先更新文档快照（--print）再谈分波")
        return 1

    print("✅ PASS")
    print("  · 编译期共享库度数与文档一致")
    print("  · gRPC 契约边与文档一致")
    print("  · 跨域 Kafka topic 与文档一致")
    print("  · users-common 逐类跨域引用与文档一致")
    print("  · service/common 逐类辐射度与文档一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
