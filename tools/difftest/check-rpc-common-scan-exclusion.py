#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""rpc 域**不得扫 `com.rag.common`** + **servlet 形态必须配 `rag.side: rpc`** —— 跨域静态守门。

🔴 **为什么需要这条守门（2026-10-30 · G4 第九次订正 `6a7b1a2b` 的连带缺口）**
    共享层 `service/common` 里有 **13 个 api 侧 web Bean**，分两个**装配面**：

    | 装配面 | 成员 | 装配靠什么 |
    |---|---|---|
    | **自动配置面 3 个** | `GoStylePathNormalizingFilter` · `GoStyleJsonContentTypeAdvice` · `GoStyleRouteTemplateResolver` | `AutoConfiguration.imports` ⇒ **不靠组件扫描** ⇒ **rpc 域只要是 servlet 形态就会注册** |
    | **扫描面 10 个** | `GoStyleCpuSheddingFilter` · `GoStyleMaxConnsFilter` · `GoStyleMaxBytesFilter` · `GoStyleBreakerFilter` · `GoStyleTraceparentFilter` · `GoStyleTomcatParameterLimitCustomizer` · `HttpMiddlewareFilter` · `GoStyleFormValueLimitInterceptor` · `GoStyleFormLimitWebMvcConfig` · `GoStyleJsonBodyConverterConfig` | `@Component` / `@Configuration` ⇒ **靠组件扫描** |

    ⇒ ⇒ G4 已把 `rag.side=api` 的门（`com.rag.common.web.ConditionalOnApiSide` 元注解）
    挂到**全部 13 个** ⇒ ⇒ **自动配置面那 3 个已被护栏兜住**。
    ⇒ ⇒ **但扫描面那 10 个**（`@Component`）**只看 `scanBasePackages`** ⇒ ⇒
    **`rag.side=rpc` 救不了它们**（它们不看那个属性）⇒ ⇒
    **它们现在完全靠「rpc 域不扫 `com.rag.common`」这个域侧约定活着** ⇒ ⇒
    **哪天有人给某个 rpc 域加上 `scanBasePackages={"com.rag.common"}` ⇒ 那 10 个会一起活起来**。

🔴 **为什么「活了」就是真偏离（不是无害）**
    Go 侧的 rpc 服务**全部**是 `zrpc.MustNewServer` ⇒ ⇒ **一个 rest 中间件都没挂**
    （`rest.With*` 全在 api 侧的 `routes.go`）⇒ ⇒ 而上面 13 个全是 **api 侧**的
    ⇒ ⇒ 在 rpc 域注册它们 = **RAG 做了 Go 不会做的事** ⇒ ⇒ **真偏离**。
    ⚠️ **有杀伤力的两个**：
      · `GoStyleCpuSheddingFilter` —— CPU 超阈时**拒掉抓取请求** ⇒ 直接威胁「载体」这件事本身的目的；
      · `GoStyleTomcatParameterLimitCustomizer` —— **容器级** ⇒ 它管的正是 `DRIFT-HOTAPI-030` 那条轴。

✅ **本守门拦什么（两条断言）**
    ① **结构面**：任一 rpc 域的 `@SpringBootApplication` **带 `scanBasePackages`** 且其中含
       `com.rag.common` ⇒ 报红（⇒ 那 10 个扫描面 Bean 会活）。
       ⇒ ⚠️ 只认**真实注解参数**（**剥注释后**再读）⇒ ⇒ javadoc 里写
       「不能用 `scanBasePackages={"com.rag.common"}`」**不算违规**（那些域的注释里都有这句）。
    ② **形态面**：任一 rpc 域若是 **servlet 形态**（`pom.xml` 里有 `spring-boot-starter-web`
       ⇒ 有 servlet 容器 ⇒ 自动配置面那 3 个会注册）⇒ 则它的 `application.y*ml`
       **必须显式设 `rag.side: rpc`** ⇒ 否则 `matchIfMissing=true` ⇒ 默认落到 api 侧 ⇒ 报红。

⚠️ **枚举口径**（别踩 `PIT-SCAN-01` 的坑）
    rpc 模块的路径深度**不一致**（多数域 = `service/<域>/<域>-rpc/…`；
    users 域 = `service/users/<子域>/<子域>-rpc/…` ⇒ **多一层**）⇒
    ⇒ **本脚本按 git 文件清单 + 路径含 `/rpc/` 过滤**，**不用 `glob` 猜形状**。

⚠️ **与既有守门的关系**
    · 与 G3 的**域内运行期守门**（`ArticleRpcSharedLayerWebBeanGateTest` 等）**互补不重叠**：
      那些是**运行期**（容器里有没有那些 Bean）· 本脚本是**静态**（结构面有没有被打开）；
    · 与 `check-wave-lane-ownership.py` **不重叠**：那个管**写权**，本脚本管**装配面**。

用法
----
    python3 tools/difftest/check-rpc-common-scan-exclusion.py                # 正常跑（EXIT=0/1）
    python3 tools/difftest/check-rpc-common-scan-exclusion.py --print        # 只打印，不判红
    python3 tools/difftest/check-rpc-common-scan-exclusion.py --mutation-test  # 内置变异自测
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WHITELIST = os.path.join(REPO, "tools", "difftest", "conf", "rpc-common-scan-whitelist.txt")

# ---------------------------------------------------------------- 判据用的正则

# `@SpringBootApplication` 上的 `scanBasePackages` —— 可能写成
#   @SpringBootApplication(scanBasePackages = {"com.rag.common"})
#   @SpringBootApplication({"com.rag.common"})
#   @SpringBootApplication(scanBasePackages = "com.rag.common")
SCAN_ANNOTATION_RE = re.compile(r"@SpringBootApplication\s*(\(([^)]*)\))?", re.S)
SCAN_PKG_RE = re.compile(r"com\.rag\.common")


def strip_comments(text: str) -> str:
    """剥掉块注释与行注释 ⇒ ⇒ javadoc 里写的 `scanBasePackages={"com.rag.common"}` 不算真注解。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


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


def _list_files(root: str) -> list[str]:
    files = _git_files(root)
    return files if files else _walk_files(root)


def _is_rpc_module(rel: str) -> bool:
    """路径里含「`-rpc` 那一段目录」⇒ 属某个 rpc 模块。

    ⚠️ **不能按固定深度取 `-2`** —— `service/comment/comment-rpc/src/main/resources/application.yml`
    的 `-2` 是 `resources` ⇒ ⇒ 会漏掉**所有**资源文件 ⇒ ⇒ 形态面断言会全域「找不到 yml」。
    ⇒ ⇒ 判据 = 路径分段里存在一段以 `-rpc` 结尾 **且** 它前面一段是 `service`。
    """
    parts = rel.split("/")
    if not parts or parts[0] != "service":
        return False
    return any(p.endswith("-rpc") for p in parts[1:])


def _module_of(rel: str) -> str:
    """`service/<域>/<域>-rpc/...` ⇒ `<域>-rpc`；`service/users/<子>/<子>-rpc/...` ⇒ `<子>-rpc`。

    ⚠️ **按「含 `-rpc` 的那一段」取**，而不是固定 `-2` ⇒ ⇒ 根级文件（`README.md` 等）
    段数不足时返回空串（**不得抛 IndexError**）。
    """
    parts = rel.split("/")
    for p in parts:
        if p.endswith("-rpc"):
            return p
    return ""


def find_rpc_modules(root: str) -> list[str]:
    mods = set()
    for rel in _list_files(root):
        if _is_rpc_module(rel):
            mods.add(_module_of(rel))
    return sorted(mods)


# ---------------------------------------------------------------- 断言 ①：结构面

def scan_scan_base_packages(root: str) -> list[tuple[str, str, int]]:
    """返回 [(模块, Application 文件相对路径, 行号)]：真注解里扫了 `com.rag.common` 的 rpc 域。"""
    hits: list[tuple[str, str, int]] = []
    for rel in _list_files(root):
        if not _is_rpc_module(rel):
            continue
        if not rel.endswith("Application.java"):
            continue
        full = os.path.join(root, rel)
        try:
            raw = open(full, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        # 剥注释后逐行找 ⇒ 行号仍对应原文（剥注释不改行数）
        cleaned = strip_comments(raw)
        raw_lines = raw.splitlines()
        clean_lines = cleaned.splitlines()
        for i, line in enumerate(clean_lines):
            if "@SpringBootApplication" not in line:
                continue
            # 注解参数可能跨行 ⇒ 从这一行往后拼 3 行
            chunk = " ".join(clean_lines[i:i + 4])
            m = SCAN_ANNOTATION_RE.search(chunk)
            if not m:
                continue
            args = m.group(2) or ""
            if SCAN_PKG_RE.search(args):
                hits.append((_module_of(rel), rel, i + 1))
                break
        del raw_lines
    return hits


# ---------------------------------------------------------------- 断言 ②：形态面

WEB_STARTER_RE = re.compile(r"spring-boot-starter-web\b")

# 「rag.side = rpc」的**两种 YAML 写法**都要认（实测：G3 用嵌套形态，G1 派单口径是平铺形态）：
#   ① 平铺：`rag.side: rpc`
#   ② 嵌套：`rag:` + 缩进一级的 `side: rpc`
# ⇒ ⇒ 只认一种 ⇒ 另一种会被**静默漏判** ⇒ ⇒ 等于守门对那一半失明。
RAG_SIDE_FLAT_RE = re.compile(r"^\s*rag\.side\s*:\s*(\S+)\s*$", re.M)
RAG_NESTED_BLOCK_RE = re.compile(r"^rag\s*:\s*$(.*?)(?=^\S|\Z)", re.M | re.S)
RAG_SIDE_NESTED_RE = re.compile(r"^\s+side\s*:\s*(\S+)\s*$", re.M)


def _module_has_web_starter(root: str, module: str) -> tuple[bool, str]:
    """模块 pom.xml 里有没有 `spring-boot-starter-web`（⇒ 有 servlet 容器）。

    ⇒ **按「pom.xml 所在目录名 == 模块名」匹配**（users 域多一层 ⇒ 不能按固定深度猜）。
    """
    for rel in _list_files(root):
        if not rel.endswith("pom.xml"):
            continue
        parts = rel.split("/")
        if len(parts) < 2 or parts[-2] != module:
            continue
        try:
            txt = open(os.path.join(root, rel), encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        if WEB_STARTER_RE.search(txt):
            return True, rel
    return False, ""


def _module_yml(root: str, module: str) -> str | None:
    """模块的 `src/main/resources/application.y*ml` ⇒ 相对路径。

    ⚠️ **users 域多一层**（`service/users/user/user-rpc/…`）⇒ ⇒ **不能按固定深度找** ⇒
    ⇒ 按「**含 `-rpc` 那一段的目录** + 其下的 `src/main/resources`」定位。
    """
    for rel in _list_files(root):
        if not _is_rpc_module(rel):
            continue
        if _module_of(rel) != module:
            continue
        if rel.endswith("application.yml") or rel.endswith("application.yaml"):
            return rel
    return None


WEB_TYPE_NONE_RE = re.compile(r"^\s*web-application-type\s*:\s*none\s*$", re.M)


def _is_servlet_shape(root: str, module: str) -> tuple[bool, str]:
    """该 rpc 域是不是 **servlet 形态**（⇒ 自动配置面那 3 个会注册）。

    ⚠️ **判据两个条件都要**（实测踩坑）：
      · **有** `spring-boot-starter-web`（⇒ 容器在 classpath 上）**且**
      · **没有** `spring.main.web-application-type: none`（⇒ 否则 Boot 判定为非 web 应用，
        `AutoConfiguration.imports` 里的 servlet 条件全部不成立 ⇒ 自动配置面那 3 个**不注册**）。
    ⇒ ⇒ 只看「有没有 web starter」会把 `web-application-type: none` 的域**误判成 servlet 形态**
    ⇒ ⇒ 那 5 个域（comment / favorite 等）在主干上正是 none 形态。
    """
    has_web, pom = _module_has_web_starter(root, module)
    if not has_web:
        return False, ""
    yml = _module_yml(root, module)
    if yml is None:
        return True, f"{pom}（有 web starter，但找不到 application.yml ⇒ 形态判定不了）"
    try:
        txt = open(os.path.join(root, yml), encoding="utf-8", errors="replace").read()
    except OSError:
        return True, f"{pom}（yml 读不出来）"
    if WEB_TYPE_NONE_RE.search(txt):
        return False, ""
    return True, yml


def scan_rag_side(root: str) -> list[tuple[str, str, str]]:
    """返回 [(模块, 参照文件, 现状)]：servlet 形态但没配 `rag.side: rpc` 的 rpc 域。"""
    problems: list[tuple[str, str, str]] = []
    for module in find_rpc_modules(root):
        is_servlet, ref = _is_servlet_shape(root, module)
        if not is_servlet:
            continue  # 非 servlet 形态 ⇒ 自动配置面那 3 个不会注册 ⇒ 本断言不适用
        yml = _module_yml(root, module)
        if yml is None:
            problems.append((module, ref, "servlet 形态但找不到 application.yml"))
            continue
        try:
            txt = open(os.path.join(root, yml), encoding="utf-8", errors="replace").read()
        except OSError:
            problems.append((module, yml, "yml 读不出来"))
            continue
        m = RAG_SIDE_FLAT_RE.search(txt)
        if m:
            value = m.group(1).strip().strip('"\'')
        else:
            value = None
            for block in RAG_NESTED_BLOCK_RE.findall(txt):
                mm = RAG_SIDE_NESTED_RE.search(block)
                if mm:
                    value = mm.group(1).strip().strip('"\'')
                    break
        if value is None:
            problems.append((module, yml, "未设 `rag.side`（默认 = api 侧 ⇒ 会挂 Go 的 api 侧中间件）"))
        elif value != "rpc":
            problems.append((module, yml, f"`rag.side: {value}` ⇒ 不是 `rpc`"))
    return problems


# ---------------------------------------------------------------- 主流程

def check(root: str) -> tuple[list[tuple[str, str, int]], list[tuple[str, str, str]]]:
    return scan_scan_base_packages(root), scan_rag_side(root)


def _load_whitelist(root: str) -> dict[str, str]:
    """读白名单：`{模块标识: 归属说明}`。文件不存在 ⇒ 空（⇒ 任何违例都红）。"""
    path = os.path.join(root, "tools", "difftest", "conf", "rpc-common-scan-whitelist.txt")
    if not os.path.exists(path):
        return {}
    out: dict[str, str] = {}
    for line in open(path, encoding="utf-8", errors="replace"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t", 1)
        key = parts[0].strip()
        if key:
            out[key] = parts[1].strip() if len(parts) > 1 else ""
    return out


def run(root: str, do_print: bool) -> int:
    mods = find_rpc_modules(root)
    struct_hits, shape_hits = check(root)
    wl = _load_whitelist(root)

    print("== rpc 域「不扫 com.rag.common」+「servlet 形态必须配 rag.side: rpc」 ==")
    print(f"   rpc 模块 {len(mods)} 个：{', '.join(mods)}")
    print()

    if do_print:
        for m, f, ln in struct_hits:
            print(f"   [结构面] {m} ⇒ {f}:{ln} 的 @SpringBootApplication 扫了 com.rag.common")
        for m, f, why in shape_hits:
            print(f"   [形态面] {m} ⇒ {f} ⇒ {why}")
        print(f"\n   结构面违规 {len(struct_hits)} 条 · 形态面违规 {len(shape_hits)} 条")
        return 0

    ok = True
    if struct_hits:
        ok = False
        print("❌ 结构面违规（扫描面 10 个 api 侧 web Bean 会在 rpc 域注册 ⇒ 真偏离）：")
        for m, f, ln in struct_hits:
            print(f"   · {m} ⇒ {f}:{ln} ⇒ @SpringBootApplication 扫了 com.rag.common")
        print("   ⇒ ⇒ Go 的 rpc 侧是 zrpc.MustNewServer ⇒ 一个 rest 中间件都没挂 ⇒ 这是真偏离。")
        print("   ⇒ ⇒ 修法：去掉 scanBasePackages ⇒ common 的 Bean 一律 @Import 精确引入。")
    else:
        print("✅ 结构面：10 个 rpc 域**都没有**扫 com.rag.common ⇒ 扫描面那 10 个 api 侧 web Bean 不会注册")

    if shape_hits:
        real = [(m, f, w) for m, f, w in shape_hits if m not in wl]
        waived = [(m, f, w) for m, f, w in shape_hits if m in wl]
        if waived:
            print(f"⚠️ 形态面：{len(waived)} 条在白名单里（**豁免，不是免检**）：")
            for m, f, w in waived:
                print(f"   · {m} ⇒ {f} ⇒ {w}")
                print(f"       归属：{wl[m]}")
        if real:
            ok = False
            print("❌ 形态面违规（servlet 形态的 rpc 域必须显式 `rag.side: rpc`）：")
            for m, f, why in real:
                print(f"   · {m} ⇒ {f} ⇒ {why}")
            print("   ⇒ ⇒ 自动配置面那 3 个（GoStylePathNormalizingFilter / GoStyleJsonContentTypeAdvice /")
            print("      GoStyleRouteTemplateResolver）不靠扫描 ⇒ servlet 形态就会注册 ⇒ 必须由 rag.side 挡。")
            print("   ⇒ ⇒ 修法：在该域 application.yml 加一行 `rag.side: rpc`（平铺或嵌套两种写法都认）。")
        else:
            print("✅ 形态面：除白名单外，servlet 形态的 rpc 域都已显式配 `rag.side: rpc`")

    # 白名单条目已无命中 ⇒ 提示可剪枝
    hit_mods = {m for m, _f, _w in shape_hits}
    stale = [k for k in wl if k not in hit_mods]
    if stale:
        print()
        print("🟡 白名单里这些条目**已无命中** ⇒ 可从白名单删除：")
        for k in stale:
            print(f"   · {k}")

    print()
    if ok:
        print("⇒ ✅ 通过")
        return 0
    print("⇒ ❌ 不通过")
    return 1


# ---------------------------------------------------------------- 内置变异自测

def _mutate(root: str, rel: str, old: str, new: str) -> str:
    """在影子副本里改一个串 ⇒ 返回影子根。

    ⚠️ **影子 = HEAD ＋ 工作区增量**（不是纯 `git archive HEAD`）——
    否则**新增文件不在影子里**（本轮新增的白名单文件就是这种）⇒ ⇒ 影子里的
    follow-rpc 会从「白名单豁免」变成「真违规」⇒ ⇒ 变异会**因错误的原因红**
    ⇒ ⇒ 守门的变异测试会得出「判据有效」的假结论。
    ⇒ ⇒ 这里把 `git status --porcelain` 里列出的改动文件（排除 `target/`）覆盖进影子。
    """
    shadow = tempfile.mkdtemp(prefix="rpcscan_mut_")
    r = subprocess.run(
        f'git -C "{root}" archive HEAD | tar -x -C "{shadow}"',
        shell=True, capture_output=True)
    if r.returncode != 0:
        raise RuntimeError("影子副本创建失败")

    # 覆盖工作区增量（含未跟踪的新文件）
    st = subprocess.run(["git", "-C", root, "status", "--porcelain"],
                        capture_output=True, text=True)
    for line in st.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ")[-1].strip()
        if not path or path.startswith("target/") or "/target/" in path:
            continue
        src = os.path.join(root, path)
        dst = os.path.join(shadow, path)
        if os.path.isfile(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)

    full = os.path.join(shadow, rel)
    txt = open(full, encoding="utf-8").read()
    if old not in txt:
        raise RuntimeError(f"变异锚点未命中：{rel} :: {old!r}")
    open(full, "w", encoding="utf-8").write(txt.replace(old, new, 1))
    return shadow


MUTATIONS = [
    (
        "M1 结构面：给某个 rpc 域的 @SpringBootApplication 加 scanBasePackages={\"com.rag.common\"}",
        "service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/UserRpcApplication.java",
        "@SpringBootApplication // 等价于 @SpringBootConfiguration、@EnableAutoConfiguration、@ComponentScan 三者的组合",
        "@SpringBootApplication(scanBasePackages = {\"com.rag.common\"}) // 等价于 @SpringBootConfiguration、@EnableAutoConfiguration、@ComponentScan 三者的组合",
        "struct",
    ),
    (
        "M2 形态面：给某个 servlet 形态的 rpc 域 yml 写上 `rag.side: api`（**不是 rpc** ⇒ 应当被抓住）",
        "service/follow/follow-rpc/src/main/resources/application.yml",
        "grpc:",
        "rag:\n  side: api\ngrpc:",
        "shape",
    ),
    (
        "M3 形态面：把一个**非** servlet 形态的 rpc 域（`web-application-type: none`）改成 servlet 形态"
        " ⇒ 它没有 `rag.side` ⇒ 必须被抓住（且它**不在白名单** ⇒ 测的是真分支，不是豁免分支）",
        "service/comment/comment-rpc/src/main/resources/application.yml",
        "    web-application-type: none",
        "    web-application-type: servlet",
        "shape",
    ),
]


def mutation_test(root: str) -> int:
    print("── 基线：未变异必须绿 ──")
    if run(root, False) != 0:
        print("❌ 基线就不绿 ⇒ 变异自测无从谈起")
        return 1
    print("✅ 基线绿")
    print()

    failures = []
    for entry in MUTATIONS:
        # 每条 5 元组：(说明, 文件, 旧串, 新串, 面)
        desc, rel, old, new, kind = entry
        mid = desc.split("：", 1)[0].split(" ", 1)[0]
        try:
            shadow = _mutate(root, rel, old, new)
        except Exception as exc:  # noqa: BLE001
            print(f"❌ {mid}：{desc} ⇒ 变异准备失败：{exc}")
            failures.append(mid)
            continue
        struct_hits, shape_hits = check(shadow)
        caught = bool(struct_hits) if kind == "struct" else bool(shape_hits)
        if caught:
            print(f"✅ {mid}：{desc} ⇒ 被精确抓到（{kind} 面红）")
        else:
            print(f"❌ {mid}：{desc} ⇒ 变异后仍绿 ⇒ 判据空转")
            failures.append(mid)

    print()
    if failures:
        print(f"⇒ ❌ 变异测试失败（{len(failures)} 条）")
        return 1
    print("⇒ ✅ 变异测试全部精确命中（本守门的判据非空转）")
    return 0


def main() -> int:
    args = sys.argv[1:]
    root = REPO
    for i, a in enumerate(args):
        if a == "--root" and i + 1 < len(args):
            root = os.path.abspath(args[i + 1])
    if "--mutation-test" in args:
        return mutation_test(root)
    return run(root, "--print" in args)


if __name__ == "__main__":
    sys.exit(main())
