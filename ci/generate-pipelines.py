#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ci/generate-pipelines.py —— 从 Maven 依赖图生成 Concourse pipeline YAML。

【为什么要有生成器】
    11 条流水线的触发面（git 资源的 ``paths``）必须**逐字对齐**本仓真实的
    跨模块编译期依赖。手工维护 11 份依赖矩阵 = 11 个会各自漂移的副本：
    某天新增一个「A 域依赖 B 域的 proto」，只有 A 的 pipeline 被改了，
    B 的 pipeline 就会漏掉「B 的 proto 变了 ⇒ A 也要重跑」这条影响
    —— 而这种漏项**不会报错**，只会在某次改动后悄悄失去保护。

    ⇒ 所以 paths 不手写，而是从 pom 反推；
    ⇒ 并且提供 ``--check``：poms 变了但生成物没变 ⇒ 退出码 1。

【paths 是怎么算出来的】
    对每个域 D：

      ① **本域路径**           D 自己的目录树
      ② **外部依赖路径**       D 的模块**编译期**依赖的、位于 D 之外的模块目录
                               （典型：跨域消费的 *-proto —— message-proto 被
                                 article-rpc / comment-rpc / like-rpc 消费）
      ③ **共享层路径**         service/common（20 个模块全依赖）
                               service/users/users-common（10 个模块依赖）
      ④ **聚合清单**           本域模块的祖先 pom（层级聚合器）
      ⑤ **工具链**             pom.xml / service/pom.xml / mvnw / .mvn/**
                              + ci/**（CI 自身脚本：改了它就得重跑，见 ROOT_GLOBS 注释）

    ①②③④⑤ 求并集 = 该流水线的触发面。

【共享模块怎么归类】
    service/common 与 service/users/users-common 被**多个域**消费 ⇒ 归共享层
    （不归任何一个域）。其余 <域>-common 只被本域消费 ⇒ 归本域。
    这条判据是按**谁在依赖它**算出来的，不是按目录名猜的。

用法：
    python3 ci/generate-pipelines.py            # 生成（就地覆盖 ci/pipelines/*.yml）
    python3 ci/generate-pipelines.py --check    # 校验已提交文件与依赖图一致
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "{http://maven.apache.org/POM/4.0.0}"
CI_DIR = os.path.join(REPO_ROOT, "ci")
PIPELINE_DIR = os.path.join(CI_DIR, "pipelines")

# --- 与 Concourse 侧一致的配置（改这里 ⇒ 11 条同步改） -----------------------
GIT_URI = "https://github.com/KASW-UI/RAG.git"
GIT_BRANCH = "do-my-own-gh"

# 镜像源变量名（Concourse pipeline var）。
#   值在部署时用 `fly set-pipeline -v registry=<host[:port]>` 注入：
#     · 公网镜像源   → docker.m.daocloud.io
#     · 本地 registry → <docker0 网关>:5000（带端口 ⇒ 内层 dockerd 按 HTTP 处理）
#   ⇒ 仓库里的 YAML 不写死任何机器相关的地址。
REGISTRY_VAR = "((registry))"

# Maven 镜像源变量名（Concourse pipeline var）。
#   值在部署时用 `fly set-pipeline -v maven_mirror=<url>` 注入。
#   留空 = 走默认 Maven Central；填内网 Nexus / 本地静态镜像则优先生效。
#   ⇒ 仓库里的 YAML 不写死任何机器相关的地址。
MAVEN_MIRROR_VAR = "((maven_mirror))"

# --- 共享层：被多个域消费的模块 ---------------------------------------------
SHARED_MODULES = ("service/common", "service/users/users-common")

# --- 域定义：(流水线名, 域根目录, 本域要构建的模块目录列表, 一句话职责) -------
DOMAINS = [
    ("rag-shared-common", None, list(SHARED_MODULES), "共享层（service/common + service/users/users-common）"),
    ("rag-users-user", "service/users/user",
     ["service/users/user/user-api", "service/users/user/user-rpc"], "users 域 · user（user-api + user-rpc）"),
    ("rag-users-admin", "service/users/admin",
     ["service/users/admin/admin-api", "service/users/admin/admin-rpc"], "users 域 · admin（admin-api + admin-rpc）"),
    ("rag-article", "service/article",
     ["service/article/article-api", "service/article/article-rpc"], "article 域（article-api + article-rpc）"),
    ("rag-comment", "service/comment",
     ["service/comment/comment-api", "service/comment/comment-rpc"], "comment 域（comment-api + comment-rpc）"),
    ("rag-favorite", "service/favorite",
     ["service/favorite/favorite-api", "service/favorite/favorite-rpc"], "favorite 域（favorite-api + favorite-rpc）"),
    ("rag-follow", "service/follow",
     ["service/follow/follow-api", "service/follow/follow-rpc"], "follow 域（follow-api + follow-rpc）"),
    ("rag-hot", "service/hot",
     ["service/hot/hot-api", "service/hot/hot-rpc"], "hot 域（hot-api + hot-rpc）"),
    ("rag-like", "service/like",
     ["service/like/like-api", "service/like/like-rpc"], "like 域（like-api + like-rpc）"),
    ("rag-message", "service/message",
     ["service/message/message-api", "service/message/message-rpc"], "message 域（message-api + message-rpc）"),
    ("rag-task", "service/task",
     ["service/task/task-api", "service/task/task-rpc"], "task 域（task-api + task-rpc）"),
]

ROOT_FILES = ("pom.xml", "service/pom.xml", "mvnw")
# ⚠️ ci/** 必须在触发面里，原因有两条（缺了都不会报错）：
#   ① 【正确性】ci/scripts/*.sh、ci/tasks/*.yml 是**流水线自己执行的代码** ——
#      改了它们却不让流水线重跑 ⇒ 变更要等下次碰巧触发才生效。
#   ② 【致命】git 资源的 paths 决定"取哪个 commit 的树"——
#      若 ci/** 不在 paths 里，检出版本会停在"最后一个改了业务代码的 commit"，
#      那个 commit 里**还没有 ci/ 目录** ⇒ task 里的 repo/ci/scripts/dind-run.sh
#      不存在 ⇒ 任务直接失败（实测：2026-10-30 本地演练首次运行即命中）。
ROOT_GLOBS = (".mvn/**", "ci/**")

# --- 流水线步骤模板 ---------------------------------------------------------
#   (task 名, task 配置文件, 是否 privileged, 参数模板)
#   参数模板里的 {MODULES} / {JAR_MODULES} 由各域替换。
#   privileged=true 的两个步骤是 DinD 形态（需要在内层起 dockerd）。
STEPS = [
    ("maven-build", "ci/tasks/maven-build.yml", True,
     {"MODULES": "{MODULES}", "REGISTRY": "{REGISTRY}", "MAVEN_MIRROR_URL": "{MAVEN_MIRROR}"}),
    ("maven-test", "ci/tasks/maven-test.yml", True,
     {"MODULES": "{MODULES}", "REGISTRY": "{REGISTRY}", "MAVEN_MIRROR_URL": "{MAVEN_MIRROR}"}),
    ("static-gates", "ci/tasks/static-gates.yml", False,
     {"JAR_MODULES": "{JAR_MODULES}", "REGISTRY": "{REGISTRY}"}),
    ("report", "ci/tasks/report.yml", False, {"REGISTRY": "{REGISTRY}"}),
]


# --------------------------------------------------------------------------- #
# pom 解析
# --------------------------------------------------------------------------- #
def parse_poms() -> tuple[dict, dict]:
    """返回 (artifactId -> 模块目录, 模块目录 -> 直接的 com.rag 依赖目录列表)。"""
    aid_to_dir: dict[str, str] = {}
    deps: dict[str, list[str]] = {}

    for dirpath, _dirnames, filenames in os.walk(os.path.join(REPO_ROOT, "service")):
        if "target" in dirpath.split(os.sep):
            continue
        if "pom.xml" not in filenames:
            continue
        rel = os.path.relpath(dirpath, REPO_ROOT)
        root = ET.parse(os.path.join(dirpath, "pom.xml")).getroot()
        aid_node = root.find(NS + "artifactId")
        if aid_node is None or not aid_node.text:
            continue
        aid = aid_node.text.strip()
        aid_to_dir[aid] = rel
        local = []
        for dep in root.findall(NS + "dependencies/" + NS + "dependency"):
            g = dep.find(NS + "groupId")
            a = dep.find(NS + "artifactId")
            if g is None or a is None or g.text.strip() != "com.rag":
                continue
            local.append(a.text.strip())
        deps[rel] = local

    # artifactId -> 目录，再把依赖名翻译成目录
    dir_deps: dict[str, list[str]] = {}
    for mod_dir, aids in deps.items():
        resolved = []
        for aid in aids:
            target = aid_to_dir.get(aid)
            if target and target != mod_dir:
                resolved.append(target)
        dir_deps[mod_dir] = sorted(set(resolved))
    return aid_to_dir, dir_deps


def ancestor_poms(mod_dir: str) -> list[str]:
    """从模块目录往上收集祖先聚合 pom（到 service/ 为止），排除模块自身。"""
    out = []
    parts = mod_dir.split("/")
    for i in range(len(parts) - 1, 0, -1):
        anc = "/".join(parts[:i])
        if anc == "service":
            break
        if os.path.exists(os.path.join(REPO_ROOT, anc, "pom.xml")):
            out.append(f"{anc}/pom.xml")
    return sorted(out)


# --------------------------------------------------------------------------- #
# 触发面计算
# --------------------------------------------------------------------------- #
def compute_paths(domain_root: str | None, modules: list[str], dir_deps: dict) -> tuple[list[str], dict]:
    """算出该流水线的 paths，并返回可读的解释（供 YAML 注释用）。"""
    if domain_root is None:
        own_roots = list(SHARED_MODULES)
        own = [f"{m}/**" for m in SHARED_MODULES]
    else:
        own_roots = [domain_root]
        own = [f"{domain_root}/**"]

    def _in_own(dep: str) -> bool:
        return any(dep == r or dep.startswith(r + "/") for r in own_roots)

    external: set[str] = set()
    for mod in modules:
        for dep in dir_deps.get(mod, []):
            if _in_own(dep) or dep in SHARED_MODULES:
                continue
            external.add(dep)

    shared_used: set[str] = set()
    for mod in modules:
        for dep in dir_deps.get(mod, []):
            if dep in SHARED_MODULES:
                shared_used.add(dep)

    extra_ancestors: set[str] = set()
    for mod in modules:
        for anc in ancestor_poms(mod):
            if not _in_own(anc[:-len("/pom.xml")]):
                extra_ancestors.add(anc)

    paths: list[str] = list(own)
    for dep in sorted(external):
        paths.append(f"{dep}/**")
    for dep in sorted(shared_used):
        paths.append(f"{dep}/**")
    for anc in sorted(extra_ancestors):
        paths.append(anc)
    paths.extend(ROOT_FILES)
    paths.extend(ROOT_GLOBS)

    # 去重 + 保序
    seen = set()
    ordered = []
    for p in paths:
        if p not in seen:
            seen.add(p)
            ordered.append(p)

    explain = {
        "own": own,
        "external": sorted(external),
        "shared": sorted(shared_used),
        "ancestors": sorted(extra_ancestors),
    }
    return ordered, explain


def read_task_config(rel_path: str) -> list[str]:
    """读一个 task 配置文件，去掉开头的 `---`，返回内容行（用于内联进 pipeline）。

    【为什么必须内联】
        Concourse 的 pipeline step **必须**带 `file:` 或 `config:` 二选一，
        不能引用一个独立的任务文件（实测 fly validate-pipeline 报
        "must specify either `file:` or `config:`"）。
        ⇒ 独立 task 文件只对 `fly execute -c ci/tasks/x.yml` 这种一次性执行有意义；
        ⇒ 进 pipeline 的形态由本生成器**内联**产生，
        而 task 文件本身是**唯一真源**（本函数读的就是它）⇒ 不会两份漂移。
    """
    path = os.path.join(REPO_ROOT, rel_path)
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().splitlines()
    while lines and (not lines[0].strip() or lines[0].strip() == "---"):
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    return lines


def render_pipeline(name: str, domain_root, modules, paths, explain, title: str) -> str:
    module_list = ",".join(modules)
    jar_list = " ".join(modules)
    lines: list[str] = []
    a = lines.append

    a("---")
    a("# =============================================================================")
    a(f"# ci/pipelines/{name}.yml —— {title}")
    a("#")
    a("# 【职责】" + title + "的构建、测试、静态守门与结果汇总。")
    a("#")
    a("# 【触发面】git 资源的 paths —— 只监听本流水线负责的路径：")
    for p in explain["own"]:
        a(f"#   · {p:<46} 本域")
    for p in explain["external"]:
        a(f"#   · {p + '/**':<46} 本域编译期消费的外部模块（改了必须重跑本域）")
    for p in explain["shared"]:
        a(f"#   · {p + '/**':<46} 共享层（多域依赖）")
    for p in explain["ancestors"]:
        a(f"#   · {p:<46} 聚合清单（模块增删）")
    a(f"#   · {'pom.xml / service/pom.xml':<46} 根 pom 与模块清单")
    a(f"#   · {'mvnw / .mvn/** / ci/**':<46} 构建工具链与 CI 自身脚本")
    a("#")
    a("# 【为什么带外部模块的路径】")
    a("#   proto / common 是**跨域编译期依赖**（依赖矩阵见 ci/README.md）。")
    a("#   只监听本域 ⇒ 「上游 proto 改了 ⇒ 本域会断」这类影响会被漏掉，")
    a("#   而漏项不会报错 ⇒ 只能靠把依赖显式写进触发面来兜住。")
    a("#")
    a("# 【执行阶段】")
    a("#   maven-build    构建（package -DskipTests）      ← privileged（DinD）")
    a("#   maven-test     测试（test）                      ← privileged（DinD）")
    a("#   static-gates   静态守门 + 产物级 jar 可执行性检查")
    a("#   report         汇总 surefire 报告（读 TEST-*.xml）")
    a("#")
    a("# 【生成说明】本文件由 ci/generate-pipelines.py 从 pom 依赖图生成，")
    a("#   task 配置内联自 ci/tasks/*.yml（唯一真源）。")
    a("#   改了 pom 的模块依赖后跑 `python3 ci/generate-pipelines.py`；")
    a("#   校验用 `python3 ci/generate-pipelines.py --check`。")
    a("# =============================================================================")
    a("")
    a("resources:")
    a("- name: repo")
    a("  type: git")
    a("  source:")
    a(f"    uri: {GIT_URI}")
    a(f"    branch: {GIT_BRANCH}")
    a("    paths:")
    for p in paths:
        a(f"    - {p}")
    a("")
    a("jobs:")
    a("- name: build-and-test")
    a("  plan:")
    a("  - get: repo")
    a("    trigger: true")

    for task_name, task_file, privileged, params in STEPS:
        a(f"  - task: {task_name}")
        if privileged:
            a("    privileged: true")
        a("    config:")
        for line in read_task_config(task_file):
            a(f"      {line}" if line.strip() else "")
        if params:
            a("    params:")
            for k, v in params.items():
                rendered = (v.replace("{MODULES}", module_list)
                            .replace("{JAR_MODULES}", jar_list)
                            .replace("{REGISTRY}", REGISTRY_VAR)
                            .replace("{MAVEN_MIRROR}", MAVEN_MIRROR_VAR))
                if rendered == "":
                    rendered = "''"
                a(f"      {k}: {rendered}")

    a("")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description="从 pom 依赖图生成 Concourse pipeline YAML")
    ap.add_argument("--check", action="store_true",
                    help="只校验已提交文件与依赖图是否一致（不一致则退出码 1）")
    args = ap.parse_args()

    _aid_to_dir, dir_deps = parse_poms()
    os.makedirs(PIPELINE_DIR, exist_ok=True)

    drift: list[str] = []
    for name, domain_root, modules, title in DOMAINS:
        paths, explain = compute_paths(domain_root, modules, dir_deps)
        content = render_pipeline(name, domain_root, modules, paths, explain, title)
        target = os.path.join(PIPELINE_DIR, f"{name}.yml")
        existing = None
        if os.path.exists(target):
            with open(target, encoding="utf-8") as fh:
                existing = fh.read()
        if args.check:
            if existing != content:
                drift.append(f"  · {name}.yml 与 pom 依赖图不一致")
        else:
            with open(target, "w", encoding="utf-8") as fh:
                fh.write(content)
            print(f"[gen] 写入 {os.path.relpath(target, REPO_ROOT)}（paths {len(paths)} 条）")

    if args.check:
        if drift:
            print("[gen] ❌ 生成物已漂移：")
            for d in drift:
                print(d)
            print("[gen]    修法：python3 ci/generate-pipelines.py")
            return 1
        print("[gen] ✅ 11 条 pipeline 的触发面与 pom 依赖图一致。")
        return 0

    print("[gen] 完成。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
