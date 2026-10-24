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
查：每个 `*-api` 模块的 `src/main/resources/application.yml` 是否配了三个旋钮，
    且**值**正确（不是「配了就行」）。

不查：
  · **不启动任何服务** —— 纯静态。
  · **不验证这些配置真的生效** —— 那是运行时后果，由
    `service/hot/hot-api` 的 `HotApiRealTomcatGateTest`（真 Tomcat）
    与 `tools/difftest/probe-hot-limits.py`（双侧活体）负责（§3.3）。
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
           f"{'③ header-size':<14} 结论")
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

        problems = []
        if chars != EXPECTED_RELAXED_QUERY_CHARS:
            problems.append("①")
        if mpc != EXPECTED_MAX_PARAM_COUNT:
            problems.append("②")
        if hsz != EXPECTED_MAX_HEADER_SIZE:
            problems.append("③")

        def mark(ok):
            return "✅" if ok else "❌ 缺/错"

        print(f"  {mod:<26} {mark(chars == EXPECTED_RELAXED_QUERY_CHARS):<20} "
              f"{mark(mpc == EXPECTED_MAX_PARAM_COUNT):<14} "
              f"{mark(hsz == EXPECTED_MAX_HEADER_SIZE):<14} "
              f"{'✅' if not problems else '🔴'}")

        if problems:
            violations.append((mod, yml_path, problems, {
                "chars": tomcat.get("relaxed-query-chars"),
                "mpc": mpc,
                "hsz": server.get("max-http-request-header-size"),
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
        print("     修法：在该模块 application.yml 的 server: 块下补 ——")
        print("       tomcat:")
        print('         relaxed-query-chars: ["[", "]"]')
        print(f"         max-parameter-count: {EXPECTED_MAX_PARAM_COUNT}")
        print("       max-http-request-header-size: 1028KB")
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
