#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-grpc-runtime-classpath.py —— **devh gRPC client 的运行时 classpath 与服务发现地址**静态门禁。

════════════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════════════
2026-10-24 的 follow 域组件级对照里，`follow-rpc` 挖出**两条硬启动阻塞**，
而当时它的 **63 条测试全绿、`mvn package` 通过、Spring 上下文在测试里能起来**：

  ① `io.grpc:grpc-inprocess` 被写成 `<scope>test</scope>`
     ⇒ Maven「最近者优先」：直接声明压过 starter 的传递声明
     ⇒ **运行时 classpath 上这个 artifact 整个消失**
     ⇒ devh 的 `InProcessChannelFactory` 链接失败
     ⇒ `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`
     ⇒ `UserRpcClient` Bean 创建失败 ⇒ **整个服务起不来**（硬失败，不是静默失效）。

  ② gRPC client 地址写成 `discovery://user-rpc`（**两斜杠**）
     ⇒ devh 的 `DiscoveryClientResolverFactory#newNameResolver` 判的是
        `uri.getPath()`：非空、长度 > 1、以 "/" 开头
     ⇒ 两斜杠把服务名解析成了 authority，path 留空 ⇒ 抛
        `IllegalArgumentException: Incorrectly formatted target uri`
     ⇒ **整个服务起不来**。

⚠️ **两条都测不出来**，因为测试把生产地址覆盖成了 `static://127.0.0.1:1`
（见 `FollowRpcWiringTest` 的 `@SpringBootTest(properties=...)`），
而 `grpc-inprocess` 本来就在 test classpath 上
⇒ **生产 classpath 与生产地址都没被任何测试跑过**。

════════════════════════════════════════════════════════════════════════════════
【本脚本查什么 / 不查什么】
════════════════════════════════════════════════════════════════════════════════
查：
  A. 任何**声明了** `net.devh:grpc-client-spring-boot-starter` 的模块：
     `io.grpc:grpc-inprocess` **不得**是 `test` scope。
     （不声明则继承 starter 的 compile 传递依赖 —— 那是正确的，不算缺陷。）
  B. 所有 `application.yml` 里的 `address:`：
     以 `discovery:` 开头时**必须三斜杠**（`discovery:///service-name`）。
     `${VAR:默认值}` 占位符形式的**默认值**同样受检。

不查：
  · **不启动任何服务、不连任何数据库、不解析 Maven 依赖树** —— 纯静态（与
    `check-flyway-ledger.py` 同款定位：只保证声明层不踩坑）。
  · **不验证运行时 classpath 上真的有 grpc-inprocess**。
    那一条由真启动（`java -jar`）负责 —— 静态检查证明不了「artifact 真的在」，
    只证明「没人把它降级成 test scope」。
  · **不检查 address 是否指向一个真实存在的服务**（那是部署期的事）。

════════════════════════════════════════════════════════════════════════════════
【baseline 机制 —— 为什么不是「有问题就红」】
════════════════════════════════════════════════════════════════════════════════
本仓的这两类缺陷**不止 follow 域**（2026-10-24 全仓实测）：

  · `grpc-inprocess` 是 `test` scope：article-rpc / favorite-rpc / like-rpc / task-rpc（4 处）
  · `discovery:` 是两斜杠：article-rpc(2) / comment-rpc(2) / favorite-rpc(2) /
    like-rpc(1) / message-rpc(2)（9 处）

⚠️ 这些**属于别的域**，本域工作树**不得改动**（并发写会撞，见 docs/Worktree.md）
⇒ 所以本脚本采用 **baseline（已知未修清单）** 语义：

  · **不在 baseline 里的违规 ⇒ 退出码 1（硬失败）** —— 这才是门禁的真正价值：
    任何人新写出一处两斜杠 / test scope，CI 立刻红。
  · **在 baseline 里的违规 ⇒ 只打印、不失败** —— 域外待办不阻塞别人干活。
  · **baseline 里已修好的条目 ⇒ 打印「已过期」提示、不失败** —— 别的域修好之后
    需要有人来清理 baseline，提示会告诉清理人是谁、是哪一条。

⇒ **「门禁是绿的」≠「全仓没有缺陷」，而是「没有新缺陷」**。
baseline 里的每一条都对应一条待派单的跨域修复（见各域 DRIFT 台账）。

════════════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════════════
0 = 无新增违规（baseline 里的已知未修项不计入失败）
1 = 有**不在 baseline 里**的违规（逐条打印：文件、行号、当前值、应为的值）
2 = 用法错误

【用法】
════════════════════════════════════════════════════════════════════════════════
  python3 tools/difftest/check-grpc-runtime-classpath.py            # 门禁（读 baseline）
  python3 tools/difftest/check-grpc-runtime-classpath.py --strict   # 一律失败，不读 baseline
  python3 tools/difftest/check-grpc-runtime-classpath.py --update-baseline
  python3 tools/difftest/check-grpc-runtime-classpath.py --root /path/to/other/tree
"""

import argparse
import os
import re
import sys

# devh client starter 的 artifactId —— 用它判定「这个模块用 devh gRPC client」
DEVH_CLIENT = "grpc-client-spring-boot-starter"
GRPC_INPROCESS = "grpc-inprocess"

DISCOVERY_PREFIX = "discovery:"

DEFAULT_BASELINE_REL = os.path.join("tools", "difftest", "baselines", "grpc-runtime-classpath.txt")

# `address:` 的取值：允许带引号、行尾注释；值里不含空白与引号
RE_ADDRESS = re.compile(r'^\s*address:\s*["\']?([^"\'\s#]+)["\']?\s*(#.*)?$')
# `${VAR:默认值}` —— 取默认值出来一起受检
RE_PLACEHOLDER = re.compile(r'^\$\{[^:}]*:(.*)\}$')


def iter_poms(root):
    service_dir = os.path.join(root, "service")
    for dirpath, _dirnames, filenames in os.walk(service_dir):
        for fn in filenames:
            if fn == "pom.xml":
                yield os.path.join(dirpath, fn)


def iter_ymls(root):
    service_dir = os.path.join(root, "service")
    for dirpath, _dirnames, filenames in os.walk(service_dir):
        for fn in filenames:
            if fn in ("application.yml", "application.yaml"):
                yield os.path.join(dirpath, fn)


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def rel(path, root):
    return os.path.relpath(path, root)


def resolve_placeholder(value):
    """`${VAR:discovery:///x}` → `discovery:///x`；非占位符原样返回。"""
    m = RE_PLACEHOLDER.match(value)
    return m.group(1) if m else value


class Violation(object):
    def __init__(self, key, where, detail, fix):
        self.key = key          # baseline 里的稳定键
        self.where = where      # 展示用位置（文件:行）
        self.detail = detail    # 展示用说明
        self.fix = fix          # 修法

    def __str__(self):
        return "{}: {}（修法：{}）".format(self.where, self.detail, self.fix)


def check_inprocess_scope(root):
    """规则 A：用了 devh client starter 的模块，grpc-inprocess 不得是 test scope。"""
    out = []
    for pom in iter_poms(root):
        text = read(pom)
        if DEVH_CLIENT not in text:
            continue
        for block in re.findall(r"<dependency>(.*?)</dependency>", text, re.S):
            if "<artifactId>{}</artifactId>".format(GRPC_INPROCESS) not in block:
                continue
            m_scope = re.search(r"<scope>([^<]+)</scope>", block)
            scope = m_scope.group(1).strip() if m_scope else "compile（未写 scope）"
            if scope.startswith("test"):
                path = rel(pom, root)
                out.append(Violation(
                    key="{}|{}|grpc-inprocess".format(path, "pom"),
                    where=path,
                    detail="`{}` 的 scope 是 `{}` ⇒ 运行时 classpath 上没有它 ⇒ "
                           "devh 的 InProcessChannelFactory 链接失败 ⇒ 服务起不来".format(
                               GRPC_INPROCESS, scope),
                    fix="去掉这个 <scope>test</scope>，或整段显式声明改 compile",
                ))
    return out


def check_discovery_address(root):
    """规则 B：`discovery:` 地址必须三斜杠。"""
    out = []
    for yml in iter_ymls(root):
        path = rel(yml, root)
        for idx, line in enumerate(read(yml).splitlines(), start=1):
            m = RE_ADDRESS.match(line)
            if not m:
                continue
            raw = m.group(1).strip()
            addr = resolve_placeholder(raw)
            if not addr.startswith(DISCOVERY_PREFIX):
                continue
            rest = addr[len(DISCOVERY_PREFIX):]
            # ⚠️ 判定口径：**必须是三个斜杠**。
            #    discovery:///name → 去掉前缀后是 "///name"（3 个斜杠）
            #    discovery://name  → 去掉前缀后是 "//name"  （2 个斜杠，**不合格**）
            #    ⚠️ 早先版本写成 `rest.startswith("//")` —— 两斜杠**也**满足 ⇒ 漏判。
            if rest.startswith("///"):
                continue
            slash_count = len(rest) - len(rest.lstrip("/"))
            out.append(Violation(
                key="{}|yml|{}".format(path, addr),
                where="{}:{}".format(path, idx),
                detail="address `{}` 只有 {} 个斜杠 ⇒ devh 判 uri.getPath() 长度为 0 ⇒ "
                       "抛 IllegalArgumentException ⇒ 服务起不来".format(addr, slash_count),
                fix="写成 discovery:///service-name（三个斜杠）",
            ))
    return out


def load_baseline(path):
    if not path or not os.path.exists(path):
        return set()
    keys = set()
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            keys.add(line)
    return keys


def write_baseline(path, keys):
    directory = os.path.dirname(path)
    if directory and not os.path.isdir(directory):
        os.makedirs(directory)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("# 本文件是 check-grpc-runtime-classpath.py 的「已知未修」清单。\n")
        fh.write("# 每行一条，键格式：<文件相对路径>|<规则>|<值>。\n")
        fh.write("# 键在这里的违规**只打印、不失败**；不在这里的违规 ⇒ 退出码 1。\n")
        fh.write("# 别的域修好对应条目后，请顺手从本文件删掉那一行（脚本会提示「已过期」）。\n")
        for k in sorted(keys):
            fh.write(k + "\n")


def main():
    parser = argparse.ArgumentParser(description="devh gRPC client 运行时 classpath / 服务发现地址静态门禁")
    default_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    parser.add_argument("--root", default=default_root, help="仓库根目录（默认：脚本所在仓库）")
    parser.add_argument("--baseline", default=None,
                        help="baseline 文件路径（默认：<root>/{})".format(DEFAULT_BASELINE_REL))
    parser.add_argument("--strict", action="store_true",
                        help="不读 baseline，所有违规都算失败")
    parser.add_argument("--update-baseline", action="store_true",
                        help="用当前扫描结果重写 baseline")
    parser.add_argument("--no-baseline", action="store_true",
                        help="不读也不写 baseline（等价于 --strict，只是不改文件）")
    args = parser.parse_args()

    root = os.path.abspath(args.root)
    if not os.path.isdir(os.path.join(root, "service")):
        print("❌ --root 下没有 service/ 目录：{}".format(root), file=sys.stderr)
        return 2

    baseline_path = args.baseline
    if baseline_path is None:
        baseline_path = os.path.join(root, DEFAULT_BASELINE_REL)

    violations = check_inprocess_scope(root) + check_discovery_address(root)

    if args.update_baseline:
        write_baseline(baseline_path, set(v.key for v in violations))
        print("📝 已更新 baseline：{}（{} 条）".format(baseline_path, len(violations)))
        return 0

    baseline = set() if (args.strict or args.no_baseline) else load_baseline(baseline_path)

    known = [v for v in violations if v.key in baseline]
    fresh = [v for v in violations if v.key not in baseline]
    stale = sorted(k for k in baseline if k not in set(v.key for v in violations))

    if fresh:
        print("❌ 发现 {} 处**未登记**违规（门禁失败）：\n".format(len(fresh)))
        for v in fresh:
            print("  · {}".format(v))
        print("\n退出码 1")
        return 1

    print("✅ 无新增违规：")
    print("  · 用了 devh grpc-client-starter 的模块，grpc-inprocess 均未降级成 test scope")
    print("  · 所有 `discovery:` 形式的 gRPC client 地址均为三斜杠")

    if known:
        print("\n⚠️ 已知未修（登记在 baseline，不计失败）——{} 处：".format(len(known)))
        for v in known:
            print("  · {}".format(v))
        print("   ⇒ 这些属于**其它域**，修完请从 baseline 删掉对应行（脚本会提示「已过期」）。")

    if stale:
        print("\nℹ️ baseline 里已失效（对应问题已被修复，请清理 baseline）——{} 条：".format(len(stale)))
        for k in stale:
            print("  · {}".format(k))

    print("\n退出码 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
