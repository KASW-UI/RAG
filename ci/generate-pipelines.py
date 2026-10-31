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
# ⚠️ **git 资源的 uri / branch 走 Concourse pipeline var**，而不是写死：
#   原因：触发面验证必须用**真实 Concourse 服务端 + 真实 commit**做
#   （任务书第五阶段·3：「必须记录实际触发结果，不要只根据 YAML 推断」），
#   而验证需要一个**可推送的远端** ⇒ 指向本地演练仓（git://<host>:5012/gitd-rag.git）
#   ⇒ 于是 uri / branch 都得能按部署环境切换 ⇒ 走 var。
#   生产部署传 GitHub 地址（deploy.sh 的默认值），演练传本地演练仓。
GIT_URI_VAR = "((git_uri))"
GIT_BRANCH_VAR = "((git_branch))"
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

# surefire 默认的测试类 include 规则（**必须与 surefire 一致**，否则会误红）。
# 见 module_has_tests() 的说明。
SUREFIRE_INCLUDE_RE = re.compile(
    r"(^|/)(Test[^/]*|[^/]*Tests|[^/]*TestCase|[^/]*Test)\.java$")

# --- 契约门流水线（R5 / R6）--------------------------------------------------
# ⚠️ **R4 为什么不在这里**（2026-10-31 定）：
#   R4（Proto 契约）是**纯文本逐字节比对**，秒级 · 零额外依赖 ⇒ 属「快速门禁」
#   ⇒ 已放进**项目级静态守门阶段**（`ci/scripts/run-static-gates.sh`），
#     每条域流水线都跑 ⇒ 「改任一 proto ⇒ 相关流水线必跑此门」。
#   ⚠️ 契约漂移是**全局风险** ⇒ 不该只在某一条流水线里被看见。
#   （R5/R6 是分钟级 × 多域 ⇒ 属「成本较高、适合独立触发的契约门禁」⇒ 在这里。）
#
# ⚠️ **契约门不依赖 Go**：契约基准已一次性离线提取，固化在 `ci/contracts/` 下
#    （见 ci/contracts/README.md）⇒ 这些流水线**不需要 Go 工具链、
#    不需要 Go 仓在线可访问、不跑任何 go 命令**。
CONTRACT_PIPELINES = [
    ("rag-contract-errmsg", "R5 错误码契约一致性（Java 侧错误码 vs 仓内基准）"),
    ("rag-contract-schema", "R6 数据库 Schema 契约一致性（Flyway 迁移 vs 语义基准）"),
]

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
    ("report", "ci/tasks/report.yml", False,
     {"EXPECTED_MODULES": "{EXPECTED_MODULES}", "REGISTRY": "{REGISTRY}"}),
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


def module_has_tests(mod_dir: str) -> bool:
    """该模块目录下的 src/test/** 里是否有 .java 测试文件。

    ⚠️ **为什么只认 .java**：surefire 只编译/执行 .java 测试源；
    `src/test/resources/` 下的资源文件不产生任何报告
    ⇒ 把含 resources 的模块算成「应测模块」会误红。
    ⚠️ **为什么还要过 surefire 的 include 规则**：
    surefire 默认只跑 `**/Test*.java` / `**/*Test.java` / `**/*Tests.java` /
    `**/*TestCase.java`。若某模块只有 `*IT.java` 这类不匹配的命名
    ⇒ surefire 产出 0 份报告 ⇒ 把它算进「应测集合」会误红。
    ⇒ 这里用与 surefire 相同的 include 正则过滤，保证**不会误伤**。
    """
    test_dir = os.path.join(REPO_ROOT, mod_dir, "src", "test")
    if not os.path.isdir(test_dir):
        return False
    for dirpath, _dirnames, filenames in os.walk(test_dir):
        for name in filenames:
            if name.endswith(".java") and SUREFIRE_INCLUDE_RE.search("/" + name):
                return True
    return False


def expected_test_modules(modules: list[str], dir_deps: dict) -> list[str]:
    """本次构建**应当产出 surefire 报告**的模块集合。

    = 「modules 的 -am 传递闭包」∩ 「含 src/test/** 且会被 surefire 命中的模块」

    ⚠️ **为什么必须显式算出来传给 report 步**（2026-10-31 变异实测坐实）：
      report 步只扫磁盘上**存在**的报告 ⇒ 「模块跑了但 0 条用例」与
      「模块整个没跑」都表现为「该模块不在结果表里」⇒ 汇总照样 EXIT=0
      ⇒ **假绿**。⇒ 必须由**依赖图**算出期望集合，才能判「该在而不在」。
    """
    closure: set[str] = set()
    stack = list(modules)
    while stack:
        cur = stack.pop()
        if cur in closure:
            continue
        closure.add(cur)
        for dep in dir_deps.get(cur, []):
            if dep not in closure:
                stack.append(dep)
    return sorted(m for m in closure if module_has_tests(m))


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
    # ⚠️ R8 加固（2026-10-31 审查发现）：**外部依赖模块**的祖先聚合 pom 也要纳入。
    #   原实现只算**本域模块**的祖先 ⇒ 漏了「上游域的聚合 pom」。
    #   实测漏掉的 3 处：service/users/pom.xml · service/users/user/pom.xml ·
    #   service/message/pom.xml（下游域的 paths 里没有它们）。
    #   ⇒ **潜在**漏触发（非当前缺陷）：这些祖先 pom 当前**没有**
    #     dependencyManagement / pluginManagement ⇒ 改它们今日无传导；
    #     但一旦有人往里加依赖管理，**下游域不会重跑 ⇒ 静默漏触发**。
    #   ⇒ 加固成本 = 每条流水线多 1~3 个精确文件路径 ⇒ 远低于漏触发的代价。
    #   ⚠️ 不是过度触发：`service/<域>/pom.xml` 是该域的**模块清单与依赖管理**，
    #     改它会改变下游 `-am` 构建出来的产物 ⇒ 下游**确实应该**重跑。
    for mod in list(modules) + sorted(external) + sorted(shared_used):
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


def read_task_config(rel_path: str) -> tuple[list[str], dict[str, str]]:
    """读一个 task 配置文件，返回 (内容行, task 自带的 params)。

    【为什么必须内联】
        Concourse 的 pipeline step **必须**带 `file:` 或 `config:` 二选一，
        不能引用一个独立的任务文件（实测 fly validate-pipeline 报
        "must specify either `file:` or `config:`"）。
        ⇒ 独立 task 文件只对 `fly execute -c ci/tasks/x.yml` 这种一次性执行有意义；
        ⇒ 进 pipeline 的形态由本生成器**内联**产生，
        而 task 文件本身是**唯一真源**（本函数读的就是它）⇒ 不会两份漂移。

    ⚠️ **params 段为什么被单独摘出来**（2026-10-31 修）：
        task 文件里的 `params:` 段承载**该 task 自己的默认值**
        （如 maven-build 的 IMAGE、static-gates 的 REGISTRY），
        而生成器要为**每个域**替换其中的一部分（MODULES / JAR_MODULES / …）。
        ⇒ 若生成器在 config 之后**再追加一个** `params:` 段，
          YAML 里就会出现**两个同名 `params` 键** ⇒ 依赖「后者覆盖前者」才正确
          （PyYAML 与 Go yaml.v2/v3 确实都是后者胜 ⇒ 实测行为正确）。
        ⇒ 但重复键是**歧义**：不同解析器/严格模式可能报错或取错。
        ⇒ 这里把 task 的 params 段**摘出来**，与生成器的值**合并**后
          只输出**一个** `params:` 段 ⇒ 消除重复键。
    """
    path = os.path.join(REPO_ROOT, rel_path)
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().splitlines()
    while lines and (not lines[0].strip() or lines[0].strip() == "---"):
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()

    # 摘出顶层 `params:` 段（格式受控：两空格缩进的 KEY: value）
    # ⚠️ **注释行必须跳过而不是「越界」**（2026-10-31 实测踩到）：
    #   maven-build.yml 的 params 段里每个参数上方都有注释行
    #   ⇒ 若把注释行当作「段的结束」，会**静默丢掉后面的参数**
    #   （实测丢了 IMAGE ⇒ 嵌套构建镜像无默认值 ⇒ 真 CI 会构建失败）。
    task_params: dict[str, str] = {}
    out: list[str] = []
    in_params = False
    for line in lines:
        if not in_params and line.rstrip() == "params:":
            in_params = True
            continue
        if in_params:
            if line.strip() == "":
                # 空行 ⇒ params 段结束（task 文件里 params 都在末尾）
                in_params = False
                out.append(line)
                continue
            if line.lstrip().startswith("#"):
                # 注释行 ⇒ 仍在 params 段内 ⇒ 跳过（**不能**当作越界）
                continue
            m = re.match(r"^  ([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$", line)
            if m:
                task_params[m.group(1)] = m.group(2).strip()
                continue
            # 不是 KEY: value 也不是注释 ⇒ 已越过 params 段
            in_params = False
        out.append(line)
    return out, task_params


def render_pipeline(name: str, domain_root, modules, paths, explain, title: str,
                   expected_modules: list[str]) -> str:
    module_list = ",".join(modules)
    # 【EXPECTED_MODULES：本次应产出 surefire 报告的模块集合】
    #   = 「modules 的 -am 传递闭包」∩「含 src/test/** 且会被 surefire 命中的模块」
    #   ⇒ report 步据此判「模块该在而不在」与「跑了但 0 条用例」两种真问题
    #     （否则这两种都表现为「不在表里」⇒ 汇总照样 EXIT=0 ⇒ 假绿）。
    expected_list = ",".join(expected_modules)
    # 【JAR_MODULES 只给 api/rpc 模块】——
    #   产物级守门 tools/difftest/check-jar-executable.sh 查的是
    #   「**可执行**的 Spring Boot fat jar」⇒ 只对 *-api / *-rpc 成立。
    #   service/common、service/users/users-common 是 **library 模块** ⇒
    #   按该脚本自己的规矩（脚本头注释：*"service/common 刻意不在枚举里"*）
    #   **不得**给它们加 repackage ⇒ 也不能拿它们去查产物可执行性。
    #   ⇒ 共享层流水线没有可执行 jar ⇒ JAR_MODULES 留空
    #     ⇒ run-static-gates.sh 会跳过该门（不是"通过"，是"不适用"）。
    jar_list = " ".join(m for m in modules if m.rsplit("/", 1)[-1].endswith(("-api", "-rpc")))
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
    a("# 【本次应产出报告的模块集合】（由 pom 依赖图算出 ⇒ report 步据此判")
    a("#   「模块该在而不在」与「跑了但 0 条用例」—— 缺了这两条判定，")
    a("#   报告步只扫磁盘上存在的报告 ⇒ 两种真问题都表现为「不在表里」⇒ 假绿）")
    for m in expected_modules:
        a(f"#   · {m}")
    if not expected_modules:
        a("#   · （本流水线无含测试的模块）")
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
    a(f"    uri: {GIT_URI_VAR}")
    a(f"    branch: {GIT_BRANCH_VAR}")
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
        config_lines, task_params = read_task_config(task_file)
        for line in config_lines:
            a(f"      {line}" if line.strip() else "")
        # ⚠️ 只输出**一个** params 段 = task 自带默认值 ⊕ 生成器按域替换的值。
        #   （task 里的 params 段已在 read_task_config 里摘出，不在 config 里出现。）
        merged: dict[str, str] = dict(task_params)
        for k, v in params.items():
            merged[k] = (v.replace("{MODULES}", module_list)
                         .replace("{JAR_MODULES}", jar_list)
                         .replace("{EXPECTED_MODULES}", expected_list)
                         .replace("{REGISTRY}", REGISTRY_VAR)
                         .replace("{MAVEN_MIRROR}", MAVEN_MIRROR_VAR))
        if merged:
            a("    params:")
            for k, v in merged.items():
                rendered = v if v != "" else "''"
                a(f"      {k}: {rendered}")

    a("")
    return "\n".join(lines)


def contract_paths(kind: str, aid_to_dir: dict) -> list[str]:
    """算契约门流水线的触发面。

    ⚠️ **粒度依据 = Maven 模块图**（与 11 条日常流水线同一纪律），
    不是「Go 目录结构」，也不是「拍脑袋写路径」。
    """
    mods = sorted(aid_to_dir.values())
    paths: list[str] = []
    if kind == "errmsg":
        # 所有含 `src/main/java/**/errmsg/` 的模块（错误码定义所在处）
        # ⚠️ **必须用相对路径**：os.walk 返回绝对路径 ⇒ 直接拼进 paths 会把
        #    开发者机器的绝对路径写进流水线 ⇒ CI 上永远匹配不到（静默不触发）。
        for m in mods:
            for dp, _dn, _fn in os.walk(os.path.join(REPO_ROOT, m, "src", "main", "java")):
                if os.path.basename(dp) == "errmsg":
                    rel = os.path.relpath(dp, REPO_ROOT)
                    paths.append(f"{rel}/**")
                    break
        # 探针本体（Java 半边）也是被测代码的一部分
        paths.append("tools/errmsg-probe/**")
        # ⚠️ **task 域是异形**：它没有 `<域>-common`（Go 侧同样没有，不是遗漏），
        #   它的「错误语义」不是码值表，而是 **gRPC status + description +
        #   HTTP 状态 + text/plain body** 四层 ⇒ 被测代码散在 task-api 的
        #   controller / advice / client 与 task-rpc 里 ⇒ 必须单独纳入触发面，
        #   否则「改了 task 的 HTTP 错误语义 ⇒ R5 门不跑 ⇒ 漂移无人发现」。
        for m in mods:
            if m.startswith("service/task/"):
                paths.append(f"{m}/**")
    elif kind == "schema":
        # 所有含迁移脚本的模块
        for m in mods:
            for sub in ("db/migration", "db"):
                d = os.path.join(REPO_ROOT, m, "src", "main", "resources", sub)
                if os.path.isdir(d) and any(f.endswith(".sql") for f in os.listdir(d)):
                    paths.append(f"{m}/src/main/resources/{sub}/**")
                    break
    # 基准本身变了 ⇒ 必须重跑（否则门在拿旧基准比）
    paths.append(f"ci/contracts/{kind}/**")
    paths.extend(ROOT_FILES)
    paths.extend(ROOT_GLOBS)
    seen, ordered = set(), []
    for p in paths:
        if p not in seen:
            seen.add(p)
            ordered.append(p)
    return ordered


def render_contract_pipeline(name: str, title: str, paths: list[str],
                             task_name: str, task_file: str,
                             privileged: bool, params: dict) -> str:
    lines: list[str] = []
    a = lines.append
    a("---")
    a("# =============================================================================")
    a(f"# ci/pipelines/{name}.yml —— {title}")
    a("#")
    a("# 【职责】" + title + "。")
    a("#")
    a("# 【与 11 条日常流水线的关系】")
    a("#   日常流水线负责「Java 能不能构建、测试、产出可执行 jar、静态规则过不过」；")
    a("#   本条负责「Java 的**契约**有没有漂移」—— 两者判的是不同的事，**不合并**。")
    a("#   ⚠️ 独立成一条而不是塞进 11 条：R5/R6 是分钟级 × 多域 ⇒")
    a("#     无条件放进日常流水线会把日常 CI 拖到不可接受。")
    a("#")
    a("# 【不需要 Go】")
    a("#   Go 真源是**只读语义参考基准，不是 CI 的执行对象**：")
    a("#   契约已一次性离线提取，固化在 ci/contracts/ 下 ⇒")
    a("#   本流水线**不拉 Go 仓、不装 Go 工具链、不跑任何 go 命令**。")
    a("#")
    a("# 【触发面】")
    for p in paths:
        a(f"#   · {p}")
    a("#")
    a("# 【生成说明】本文件由 ci/generate-pipelines.py 生成，")
    a("#   task 配置内联自 ci/tasks/*.yml（唯一真源）。")
    a("# =============================================================================")
    a("")
    a("resources:")
    a("- name: repo")
    a("  type: git")
    a("  source:")
    a(f"    uri: {GIT_URI_VAR}")
    a(f"    branch: {GIT_BRANCH_VAR}")
    a("    paths:")
    for p in paths:
        a(f"    - {p}")
    a("")
    a("jobs:")
    a("- name: contract-check")
    a("  plan:")
    a("  - get: repo")
    a("    trigger: true")
    a(f"  - task: {task_name}")
    if privileged:
        a("    privileged: true")
    a("    config:")
    config_lines, task_params = read_task_config(task_file)
    for line in config_lines:
        a(f"      {line}" if line.strip() else "")
    merged: dict[str, str] = dict(task_params)
    for k, v in params.items():
        merged[k] = (v.replace("{REGISTRY}", REGISTRY_VAR)
                     .replace("{MAVEN_MIRROR}", MAVEN_MIRROR_VAR))
    if merged:
        a("    params:")
        for k, v in merged.items():
            rendered = v if v != "" else "''"
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
    checked_contracts = 0
    for name, domain_root, modules, title in DOMAINS:
        paths, explain = compute_paths(domain_root, modules, dir_deps)
        expected = expected_test_modules(modules, dir_deps)
        content = render_pipeline(name, domain_root, modules, paths, explain, title,
                                  expected)
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

    # --- 契约门流水线（R4 / R5 / R6）-----------------------------------------
    for name, title in CONTRACT_PIPELINES:
        kind = name.replace("rag-contract-", "")
        task_name = f"contract-{kind}"
        task_file = f"ci/tasks/contract-{kind}.yml"
        if not os.path.exists(os.path.join(REPO_ROOT, task_file)):
            print(f"[gen] ⏭  跳过 {name}：{task_file} 尚未存在")
            continue
        paths = contract_paths(kind, _aid_to_dir)
        checked_contracts += 1
        privileged = kind in ("errmsg", "schema")
        content = render_contract_pipeline(
            name, title, paths, task_name, task_file, privileged,
            {"REGISTRY": "{REGISTRY}", "MAVEN_MIRROR_URL": "{MAVEN_MIRROR}"},
        )
        target = os.path.join(PIPELINE_DIR, f"{name}.yml")
        existing = None
        if os.path.exists(target):
            with open(target, encoding="utf-8") as fh:
                existing = fh.read()
        if args.check:
            # ⚠️ 契约流水线的 task 尚未存在 ⇒ **没有真源可比** ⇒ 跳过（不报漂移）。
            #   （否则「先把检查器做完、pipeline 后补」这种正常的中间态会恒红。）
            #   ⚠️ 一旦 task 文件存在 ⇒ 生成物不一致**必须**报出来。
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
        print(f"[gen] ✅ {len(DOMAINS)} 条日常 pipeline + {checked_contracts} 条契约 pipeline "
              "的触发面与 pom 依赖图一致。")
        return 0

    print("[gen] 完成。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
