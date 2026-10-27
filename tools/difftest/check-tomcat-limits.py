#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-tomcat-limits.py —— api 进程的**容器输入上限三旋钮**配置检查。

════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════
Go 侧与 RAG 侧对「请求能有多大」有三处**默认值不同**，而 Boot 4 / Tomcat 的默认值
**比 Go 更严** ⇒ 同一份请求，Go 正常处理、RAG 直接拒。

三个旋钮（**默认值取自 Boot 4.0.8 自己的配置元数据**，
`spring-boot-tomcat-4.0.8.jar` 与 `spring-boot-starter-tomcat-runtime-4.0.8.jar`
的 `META-INF/spring-configuration-metadata.json`）：

  ┌──────────────────────────────┬──────────────────────┬──────────────────────┐
  │ 旋钮                          │ Boot 4 默认           │ Go 侧（对齐目标）      │
  ├──────────────────────────────┼──────────────────────┼──────────────────────┤
  │ server.tomcat.                │ 无（空）              │ net/http **不校验**   │
  │   relaxed-query-chars        │                      │ query 里的 [ ]       │
  │                              │                      │ ⇒ 放行               │
  │                              │                      │                      │
  │ server.tomcat.                │ **1000**             │ go-zero              │
  │   max-parameter-count        │                      │ httpx/util.go:15     │
  │                              │                      │ maxFormParamCount    │
  │                              │                      │ = **2048**（应用层）  │
  │                              │                      │                      │
  │ server.                       │ **8KB**              │ net/http             │
  │   max-http-request-header-size│                     │ DefaultMaxHeaderBytes│
  │                              │                      │ = 1<<20 **+ 4096**   │
  │                              │                      │ = **1028KB**         │
  └──────────────────────────────┴──────────────────────┴──────────────────────┘

**可观察后果**（2026-10-25 双侧活体实测，见 `docs/DRIFT/hot/api.md` 的
`DRIFT-HOTAPI-021 / -022 / -023`）：

  ① `?page[]=5` —— Go **200**（`url.ParseRequestURI` 不校验 query，且 go-zero
     `httpx.GetFormValues` 明确支持方括号数组记法）；RAG **400 + Tomcat 的
     HTML 错误页**（435 B，`text/html`）—— **响应体形状都不同**，不是业务信封。
  ② form 值个数 —— Go 第 **2049** 个才被应用层拒（200 + `{code:5001}`）；
     RAG 第 **1001** 个就被 Tomcat 拒 ⇒ **1001~2048 这段区间 Go 成功 / RAG 5001**。
     ⚠️ 连带：RAG 自己按 Go 逐字实现的 2048 判据
     （`GoFormValues.requireFormValueCountWithinLimit`）在 GET query 上**不可达**
     —— Tomcat 总在 1000 就先拒了。
  ③ query 字节长度 —— Go >200000 字节仍不翻转；RAG **8056 字节**就 **400 + HTML
     错误页**。

**⚠️ ② 为什么配 2048 而不是「配成不限」**（2026-10-25 实测后的决策）：
Tomcat 的 `maxParameterCount` 数的是**总参数个数**（query + post），
而 go-zero 的 `maxFormParamCount` 数的是**非空值个数**
（`httpx/util.go:44-45` 空值直接 `continue`，不计数）——
**两者的语义不同**，所以：

  · 配 2048 ⇒ Tomcat 与应用层的判据在「值全非空」时**恰好同一阈值**（实测两侧
    首个翻转 n 都是 **2049**）；且对**没有**复刻应用层判据的另外 7 个 api 模块，
    Tomcat 这层就是唯一的上限 ⇒ 配 -1 等于**拆掉唯一的上限**。
  · 配 -1 ⇒ 会让「≥10001 段」这个 Go `net/url` 上限（Go 1.25 `url.go:1001`
    `defaultMaxParams = 10000`）在 RAG 侧**不再被覆盖** ⇒ 顶部反而多出一段
    新的行为差异。

⇒ 故取 **2048**：它是「在当前每个模块的应用层复刻程度下」的最小公倍数。
⚠️ 残余差异（**已量化的精确窗口**）见 `docs/DRIFT/hot/api.md` 的
`DRIFT-HOTAPI-030`：**总参数数 2049~10000 且非空值 ≤2048** 时 Go 200 / RAG 5001。

**本脚本查的就是「漏配」与「配错值」** —— 这三个旋钮落在**每个 api 模块自己的
`application.yml`** 里（**不是**共享模块）⇒ 改一个不会带动另外 9 个
⇒ 10 个模块里漏配 1 个，就会出现「有的域 200、有的域 400」的新不一致。

════════════════════════════════════════════════════════════════════════
【本脚本查什么 / 不查什么】
════════════════════════════════════════════════════════════════════════
查：每个 `*-api` 模块的 `src/main/resources/application.yml` 是否配了三个容器旋钮，
    且**值**正确（不是「配了就行」）；**外加第四个旋钮** —— form 值上限闸门的模式
    （`rag.http.go-form-limit.mode`）。

  ┌────────────────────────────────────┬───────────────────────────────────────┐
  │ 第四旋钮                            │ 语义                                   │
  ├────────────────────────────────────┼───────────────────────────────────────┤
  │ rag.http.go-form-limit.mode         │ off（缺省）/ raw / envelope           │
  │                                    │ = 「Go 侧该 api 有没有注册            │
  │                                    │   httpx.SetErrorHandler」              │
  │                                    │   raw      ⇒ 400 + text/plain + Go 原文│
  │                                    │   envelope ⇒ 200 + 业务信封            │
  │                                    │   off      ⇒ 不装闸门、也不改容器上限  │
  └────────────────────────────────────┴───────────────────────────────────────┘

  ⚠️ **favorite-api 必须没有这一项** ⇒ 它不在 global 的写权范围内（AGENTS.md §5.3c）
     ⇒ 共享层对它必须零影响 ⇒ 本脚本对它做**反向断言**（存在即红）。

不查：
  · **不启动任何服务** —— 纯静态。
  · **不验证这些配置真的生效** —— 那是运行时后果，由
    `service/common` 的 `GoStyleParamLimitGateTest`（raw，真 Tomcat + 真 Spring MVC）、
    `GoStyleFormLimitEnvelopeGateTest`（envelope，真 Tomcat + 真 MVC + 真 advice）
    与 `service/hot/hot-api` 的 `HotApiRealTomcatGateTest`（双侧活体）负责（§3.3）。
  · **不检查 rpc 模块** —— rpc 是纯 gRPC 进程，不跑 Tomcat。

⚠️ 静态检查保证**配置写对了**，不等于**框架真的采用了它**。
两者都要有测试，缺一不可（§3.3）。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
0 = 所有 api 模块的三个旋钮都配好且值正确
1 = 有 api 模块漏配或配错（打印明细与修法）
"""

import os
import re
import sys

try:
    import yaml
except ImportError:  # pragma: no cover
    print("❌ 需要 PyYAML：pip3 install pyyaml")
    sys.exit(2)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SERVICE = os.path.join(ROOT, "service")

# 期望值（Go 侧语义）—— 见上表的「Go 侧（对齐目标）」列
EXPECTED_MAX_PARAM_COUNT = 2048
EXPECTED_RELAXED_QUERY_CHARS = {"[", "]"}
# ⚠️ **不是 1 MiB，而是 1 MiB + 4 KiB**：
#   Go 的**真实**读上限 = DefaultMaxHeaderBytes(1<<20) + 4096
#   （net/http/server.go:941 `return int64(s.maxHeaderBytes()) + 4096 // bufio slop`）
#   ⇒ 2026-10-25 双侧活体实测：Go 在 query 长 1052536 字节处翻转（431），
#     而配 1MB 的 RAG 在 1048440 字节就翻转（400）—— 差的正好是这 4096 字节。
EXPECTED_MAX_HEADER_SIZE = 1024 * 1024 + 4096  # 1 MiB + 4 KiB = 1028 KB

# Boot 4.0.8 的默认值（用于报错时说明「不配会怎样」）
BOOT_DEFAULT_MAX_PARAM_COUNT = 1000
BOOT_DEFAULT_MAX_HEADER_SIZE = 8 * 1024  # 8KB

# ============================================================================
# 第四个旋钮：form 值上限闸门的**模式**（2026-10-25 第十四轮 · global X-6）
# ============================================================================
# 值的语义 = 「Go 侧该 api 有没有注册 httpx.SetErrorHandler」（逐域取证）：
#   raw      ⇒ 没注册 ⇒ http.Error(w, err.Error(), 400) ⇒ 400 + text/plain + Go 原文
#   envelope ⇒ 注册了 ⇒ 业务信封 ⇒ 200 + {code:5001, 该域自己的文案, data:null}
#   off      ⇒ Go 侧该域**根本不解析 form** ⇒ 两道上限**不可达** ⇒ 语义答案就是 off
#              （task/api/** 的 httpx.Parse / ParseForm / GetFormValues 命中数 = 0）
#
# 🔴🔴 期望值**从共享层真源派生**，本脚本**不再持有副本**（2026-10-27 订正 · G2 FB-S1）
# ----------------------------------------------------------------------------
# 为什么必须派生：本脚本此前抄了一份**手写副本** ⇒ ⇒ **副本与真源漂移**：
#   task-api 的副本写 "raw"，而真源 `GoStyleFormLimitMode.java:237` 写 `Mode.OFF`
#   ⇒ ⇒ 守门红 ⇒ ⇒ **守门会把下一个人推向「把 task-api 的 yaml 改回 raw」**
#   ⇒ ⇒ 重新引入误拒（task 域这个上限在 Go 侧根本不可达）
# ⇒ ⇒ 真源 javadoc 明写「判据**只有一份**，且**可被机器核对**」
# ⇒ ⇒ 副本 = 违背该设计的**第二份判据** ⇒ ⇒ **删副本，改为读真源**
# ⇒ ⇒ **解析失败一律终止**（绝不静默回退到任何副本）——
#   静默回退 = 「以为在核对真源、其实在核对一份可能过期的副本」= §3.3 同款静默失效
FORM_LIMIT_MODE_PROPERTY = "rag.http.go-form-limit.mode"
GO_STYLE_FORM_LIMIT_MODE_JAVA = (
    "service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java"
)

# 🔴 favorite 域**必须**没有这一项（零触碰守卫）
#   ⚠️ favorite 在**真源表里是 off**（域自捕获 parse 错 ⇒ 共享层不装闸门），
#   但「零触碰」口径要求它的 yaml 里**根本不能出现这个属性**
#   ⇒ ⇒ 「语义答案」与「边界口径」是**两件不同的事** ⇒ ⇒ 两个集合分开维护
FORBIDDEN_FORM_LIMIT_MODE_MODULES = {
    "service/favorite/favorite-api",
}

# 一个域的 yaml 怎么写才与「语义答案」一致 —— **逐条对齐 Java 的 GoStyleFormLimitMode.parse**：
#   parse(null) ⇒ OFF · parse("off") ⇒ OFF · parse("false") ⇒ OFF（YAML 1.1 布尔形态）
#   parse("raw") ⇒ RAW · parse("envelope") ⇒ ENVELOPE
# ⇒ ⇒ 语义答案 = off ⇒ 合法写法 = 不写 / "off" / "false"（三者行为**逐字节相同**）
# ⇒ ⇒ 语义答案 = raw|envelope ⇒ 该属性**是承重的** ⇒ 必须逐字写对
_ACCEPTABLE_BY_SEMANTIC = {
    "off": (None, "off", "false"),
    "raw": ("raw",),
    "envelope": ("envelope",),
}


def derive_expected_form_limit_mode():
    """从共享层真源派生 ④ 旋钮的期望表。

    🔴 **唯一真源** = `GoStyleFormLimitMode.SEMANTIC_REACHABILITY`（判据只有一份）。
    🔴 失败一律 `SystemExit`（**不**静默回退）—— 理由见上面注释。
    """
    path = os.path.join(ROOT, GO_STYLE_FORM_LIMIT_MODE_JAVA)
    if not os.path.isfile(path):
        raise SystemExit(
            f"🔴 找不到共享层真源 {GO_STYLE_FORM_LIMIT_MODE_JAVA}\n"
            f"   ⇒ 终止（**不**静默回退到任何副本）"
        )
    with open(path, encoding="utf-8") as fh:
        src = fh.read()
    entries = re.findall(
        r'new\s+DomainReachability\(\s*"([^"]+)"\s*,\s*(?:true|false)\s*,\s*'
        r'(?:true|false)\s*,\s*Mode\.(\w+)\s*,',
        src,
    )
    if not entries:
        raise SystemExit(
            f"🔴 真源 {GO_STYLE_FORM_LIMIT_MODE_JAVA} 里**没解析出任何** "
            f"DomainReachability 条目\n"
            f"   ⇒ 正则失效或真源被改坏 ⇒ 终止（**不**静默回退）"
        )
    return {module: mode.lower() for module, mode in entries}


EXPECTED_FORM_LIMIT_MODE = derive_expected_form_limit_mode()


def find_api_modules():
    """返回 [(模块相对路径, application.yml 路径)]，只含 service/**/*-api。

    ⚠️ `application.yml` 在 `<module>/src/main/resources/` 之下，**不在模块根目录**
    ⇒ 直接按标准路径找，不在模块根里翻文件名。
    """
    hits = []
    for dirpath, dirnames, _filenames in os.walk(SERVICE):
        dirnames[:] = [d for d in dirnames if d not in ("target", "node_modules")]
        if not os.path.basename(dirpath).endswith("-api"):
            continue
        res = os.path.join(dirpath, "src", "main", "resources")
        for fn in ("application.yml", "application.yaml"):
            candidate = os.path.join(res, fn)
            if os.path.isfile(candidate):
                hits.append((
                    os.path.relpath(dirpath, ROOT).replace(os.sep, "/"),
                    candidate,
                ))
                break
    return sorted(hits)


def normalize_chars(value):
    """把 relaxed-query-chars 的两种写法归一成字符集合。

    Boot 的类型是 `List<Character>`，YAML 里可以写字符串 `"[,]"`（Boot 绑定时按逗号切）
    或列表 `["[", "]"]` ⇒ 两种写法等价，本脚本一视同仁。
    """
    if value is None:
        return None
    if isinstance(value, str):
        return {c for c in value.replace(" ", "") if c}
    if isinstance(value, (list, tuple)):
        out = set()
        for item in value:
            if isinstance(item, str):
                out.update(c for c in item if c)
            else:
                out.add(str(item))
        return out
    return None


def normalize_size(value):
    """把 '1MB' / '1M' / 1048576 归一成字节数；无法解析返回 None。"""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return int(value)
    s = str(value).strip().upper()
    units = {"B": 1, "KB": 1024, "K": 1024, "MB": 1024 ** 2, "M": 1024 ** 2, "GB": 1024 ** 3, "G": 1024 ** 3}
    for suffix in ("KB", "MB", "GB", "B", "K", "M", "G"):
        if s.endswith(suffix):
            num = s[: -len(suffix)].strip()
            try:
                return int(float(num) * units[suffix])
            except ValueError:
                return None
    try:
        return int(float(s))
    except ValueError:
        return None


def main():
    mods = find_api_modules()
    print("=" * 78)
    print("api 容器输入上限三旋钮检查（与 Go 侧对齐）")
    print("=" * 78)

    if not mods:
        print("\n未发现 api 模块。\n")
        return 0

    print(f"\n扫描到 {len(mods)} 个 api 进程：\n")
    hdr = (f"  {'模块':<26} {'① relaxed-chars':<20} {'② max-param':<14} "
           f"{'③ header-size':<14} {'④ form-gate':<14} 结论")
    print(hdr)
    print("  " + "-" * (len(hdr) - 2))

    violations = []
    for mod, yml_path in mods:
        with open(yml_path, encoding="utf-8") as fh:
            try:
                doc = yaml.safe_load(fh) or {}
            except yaml.YAMLError as e:
                print(f"  {mod:<26} ❌ YAML 解析失败：{e}")
                violations.append((mod, yml_path, ["YAML 解析失败"]))
                continue

        server = doc.get("server") or {}
        tomcat = server.get("tomcat") or {}

        chars = normalize_chars(tomcat.get("relaxed-query-chars"))
        mpc = tomcat.get("max-parameter-count")
        hsz = normalize_size(server.get("max-http-request-header-size"))

        # ④ form 值上限闸门模式：rag.http.go-form-limit.mode
        rag = doc.get("rag") or {}
        http = rag.get("http") or {}
        gate = http.get("go-form-limit") or {}
        mode = gate.get("mode")
        expected_mode = EXPECTED_FORM_LIMIT_MODE.get(mod)
        unregistered = False
        if mod in FORBIDDEN_FORM_LIMIT_MODE_MODULES:
            # 🔴 favorite：这一项**必须不存在**（零触碰守卫）
            gate_ok = mode is None
        elif expected_mode is None:
            # 🔴 该 api 模块**没在共享层真源登记** ⇒ ④ 这一格**根本没有判据**
            #    ⚠️ 旧实现：expected_mode=None ⇒ gate_ok = (mode == None)
            #    ⇒ ⇒ **新域只要不写这个属性就静默通过** ⇒ 盲区 ⇒ 现在显式判红
            gate_ok = False
            unregistered = True
        else:
            gate_ok = mode in _ACCEPTABLE_BY_SEMANTIC[expected_mode]

        problems = []
        if chars != EXPECTED_RELAXED_QUERY_CHARS:
            problems.append("①")
        if mpc != EXPECTED_MAX_PARAM_COUNT:
            problems.append("②")
        if hsz != EXPECTED_MAX_HEADER_SIZE:
            problems.append("③")
        if not gate_ok:
            problems.append("④")

        def mark(ok):
            return "✅" if ok else "❌ 缺/错"

        print(f"  {mod:<26} {mark(chars == EXPECTED_RELAXED_QUERY_CHARS):<20} "
              f"{mark(mpc == EXPECTED_MAX_PARAM_COUNT):<14} "
              f"{mark(hsz == EXPECTED_MAX_HEADER_SIZE):<14} "
              f"{mark(gate_ok):<14} "
              f"{'✅' if not problems else '🔴'}")

        if problems:
            violations.append((mod, yml_path, problems, {
                "chars": tomcat.get("relaxed-query-chars"),
                "mpc": mpc,
                "hsz": server.get("max-http-request-header-size"),
                "mode": mode,
                "expected_mode": expected_mode,
            }))

    if not violations:
        print("\n✅ 所有 api 进程的三个旋钮都配好且值正确 —— 输入上限与 Go 侧一致。")
        print("=" * 78)
        print()
        return 0

    print("\n❌ 检出三旋钮未对齐的 api 进程 —— 同一份请求，Go 放行而 RAG 被拒：\n")
    for item in violations:
        mod, yml_path, problems = item[0], item[1], item[2]
        actual = item[3] if len(item) > 3 else {}
        print(f"  🔴 {mod}")
        print(f"     文件：{os.path.relpath(yml_path, ROOT)}")
        if "①" in problems:
            print(f"     缺 ① relaxed-query-chars：实际 {actual.get('chars')!r}，"
                  f"应为 '[' 与 ']'")
            print("        ⇒ `?page[]=5` 这类方括号数组记法会被 Tomcat 拒 ⇒ 400 + HTML 错误页")
            print("        （Go 的 net/http 不校验 query，且 go-zero 明确支持该记法）")
        if "②" in problems:
            print(f"     缺 ② max-parameter-count：实际 {actual.get('mpc')!r}，"
                  f"应为 {EXPECTED_MAX_PARAM_COUNT}")
            print(f"        ⇒ Boot 4 默认 {BOOT_DEFAULT_MAX_PARAM_COUNT}（Tomcat 层先拒）"
                  f"⇒ 1001~2048 区间 Go 成功 / RAG 5001")
            print("        ⇒ 且 RAG 自己实现的 2048 判据在 GET query 上**不可达**")
        if "③" in problems:
            print(f"     缺 ③ max-http-request-header-size：实际 {actual.get('hsz')!r}，"
                  f"应为 1028KB（1 MiB + 4 KiB）")
            print(f"        ⇒ Boot 4 默认 8KB ⇒ 8 KiB~1 MiB 的 query："
                  f"Go 处理 / RAG 400 + HTML 错误页")
            print("        ⇒ ⚠️ 写 1MB 也不对：Go 的真实上限是 1 MiB **+ 4096**"
                  "（net/http/server.go:941 'bufio slop'）")
        if "④" in problems:
            if mod in FORBIDDEN_FORM_LIMIT_MODE_MODULES:
                print(f"     🔴 ④ {FORM_LIMIT_MODE_PROPERTY}：**这一项必须不存在**"
                      f"（实际 {actual.get('mode')!r}）")
                print("        ⇒ favorite 域不在 global 的写权范围内（AGENTS.md §5.3c）")
                print("        ⇒ 共享层对它必须**零影响**：既不改容器上限、也不装闸门")
            else:
                exp = actual.get("expected_mode")
                if exp is None:
                    # 🔴 「该域未登记」—— **不是**「期望值是 None」
                    #    ⚠️ 旧实现此处打印「应为 None」⇒ 读的人会以为期望值真的
                    #       是 None ⇒ **两个语义必须分开说**（G2 FB-S1 附带瑕疵）
                    print(f"     🔴 ④ {FORM_LIMIT_MODE_PROPERTY}：**该域未在共享层真源登记**")
                    print(f"        ⇒ 实际 {actual.get('mode')!r}，但**没有期望值可比**")
                    print(f"        ⇒ 请在 {GO_STYLE_FORM_LIMIT_MODE_JAVA} 的 "
                          f"`SEMANTIC_REACHABILITY` 里补该域（按 Go 侧取证后再填）")
                    print("        ⇒ ⚠️ 这里**不是**「期望值为 None」——"
                          "「没登记」与「期望 None」是两件事")
                else:
                    print(f"     🔴 ④ {FORM_LIMIT_MODE_PROPERTY} 不匹配：实际 "
                          f"{actual.get('mode')!r}，应为 {exp!r}")
                    print(f"        ⇒ 合法写法 = {_ACCEPTABLE_BY_SEMANTIC[exp]!r}"
                          + ("　（⚠️ `off` **必须带引号** ⇒ YAML 1.1 会把未加引号的 "
                             "`off` 解析成布尔 false）" if exp == "off" else ""))
                    print(f"        ⇒ 真源 = {GO_STYLE_FORM_LIMIT_MODE_JAVA} 的 "
                          f"`SEMANTIC_REACHABILITY`（判据只有这一份）")
                    print("        ⇒ raw      = Go 未注册 httpx.SetErrorHandler"
                          " ⇒ 400 + text/plain + Go 原文")
                    print("        ⇒ envelope = Go 注册了 httpx.SetErrorHandler"
                          " ⇒ 200 + {code:5001, 该域自己的文案, data:null}")
                    print("        ⇒ off      = Go 侧该域**根本不解析 form**"
                          " ⇒ 两道上限不可达 ⇒ 不装闸门")
                    print("        ⇒ 缺它 ⇒ 容器上限不会被抬到 10001 ⇒ Tomcat 会先于应用层拒绝")
                    print("          （且拒绝形状是 400 + HTML 错误页 ⇒ 与 Go 不同）")
        print("     修法：在该模块 application.yml 的 rag: 块下补 ——")
        print("       http:")
        print("         go-form-limit:")
        exp_hint = actual.get("expected_mode") or "raw"
        if exp_hint == "off":
            # ⚠️ 必须带引号：YAML 1.1（SnakeYAML）把**未加引号的** off 解析成布尔 false
            #    ⇒ 域侧未加引号时，共享层拿到的是 "false"
            #    （G2 反馈单 FB-05 的 L4 实测；Java 侧 parse 已接受 "false" ⇒ 语义不炸，
            #      但**显式加引号**才是与文档一致、且不受 YAML 版本影响的形式）
            print('           mode: "off"      # ⚠️ 必须带引号（YAML 1.1：未加引号的 off ⇒ 布尔 false）')
        else:
            print(f"           mode: {exp_hint}")
        print("     ⚠️ 光补配置不够：还要配一条断言「这些旋钮真的生效」的测试")
        print("        （§3.3：配置写对了 ≠ 框架采用了它）")
        print()

    print("  Go 侧对应物：")
    print("    ① net/url.ParseRequestURI 不校验 query（实测 err=nil）")
    print("       + go-zero rest/httpx/util.go 的 GetFormValues 支持方括号数组记法")
    print("    ② go-zero rest/httpx/util.go:15  maxFormParamCount = 2048")
    print("       ⚠️ 它数的是**非空值**个数（:44-45 空值 continue），"
          "与 Tomcat 的**总参数个数**语义不同")
    print("    ③ net/http DefaultMaxHeaderBytes = 1 << 20，"
          "**+ 4096 bufio slop**（server.go:941）⇒ 1028KB")
    print("=" * 78)
    print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
