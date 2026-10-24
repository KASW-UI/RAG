#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-grpc-interceptor-wiring.py —— gRPC 观测拦截器**接线完整性**静态检查。

════════════════════════════════════════════════════════════════════════
【为什么要有这个脚本】
════════════════════════════════════════════════════════════════════════
`service/common` 的 `RpcServerInterceptorFactory` 产出一个 `ServerInterceptor` Bean，
**但 grpc-spring-boot-starter 3.1.0.RELEASE 从不自动把它挂到 server 上**。

实测证据（对 `~/.m2` 里的 jar 直接 `javap`，2026-10-23 复核）：

    javap GrpcServerFactoryAutoConfiguration
      → shadedNettyGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer,
                                     List<GrpcServerConfigurer>)    ← 无 ServerInterceptor 形参
      → nettyGrpcServerFactory(…同上…)
      → inProcessGrpcServerFactory(…同上…)
    javap GrpcServerConfigurer
      → interface GrpcServerConfigurer extends Consumer<ServerBuilder<?>>

三个工厂方法**全都**只注入 `List<GrpcServerConfigurer>` ⇒ **唯一注册入口是
`GrpcServerConfigurer`**（在 `accept(ServerBuilder)` 里调 `server.intercept(...)`）。

⇒ 每个 rpc 进程必须配**两件**，缺任一件都**静默失效、不报错**：

    ① `@Import(RpcServerInterceptorFactory.class)`   —— 否则 Bean 根本不存在
    ② `@Bean GrpcServerConfigurer`                   —— 否则 Bean 存在但没人用它

代价远不止「少几个 span」：`interceptCall` / `onHalfClose` 从未执行 ⇒
  · gRPC 无 deadline 传播 ⇒ handler 线程上 `DeadlineState` 恒未设置
  · ⇒ 业务层的 `checkDeadline()` 守卫全部是死代码
  · ⇒ `DeadlineQueryTimeoutInjector` 因拿不到 DeadlineState 而**不给 SQL 设 queryTimeout**
  · ⇒ gRPC 无超时（客户端只能等 api 层 `http-timeout`）

**本脚本查的就是「② 漏了」这种形态** —— 2026-10-24 实测在 `hot-rpc` 上抓到一例：
它有 ①（`@Import` 齐了），**没有 ②** ⇒ 拦截器从未挂到 server 上。

════════════════════════════════════════════════════════════════════════
【本脚本查什么 / 不查什么】
════════════════════════════════════════════════════════════════════════
查：每个 rpc 进程的 `*Application.java` 是否**同时**具备
    ① `@Import(...RpcServerInterceptorFactory...)`
    ② 一个返回 `GrpcServerConfigurer` 的 `@Bean` 方法，且方法体里有 `.intercept(`

不查：
  · **不启动任何服务** —— 纯静态。
  · **不验证拦截器是否真的带上了配置的 timeout** —— 那是运行时后果，
    由 `service/common` 的 `RpcInterceptorTimeoutSpanTest`（真 OTel SDK 驱动真实拦截器）
    与各 rpc 模块自己的 `*GrpcInterceptorWiringTest` 负责（§3.3）。
  · **不检查 api 模块** —— api 是 HTTP 进程，不注册 gRPC 服务端拦截器。

⚠️ 静态检查保证**接线动作写在了源码里**，不等于**框架真的调用了它**。
两者都要有测试，缺一不可（§3.3：「断言 Bean 存在」≠「断言机制生效」）。

════════════════════════════════════════════════════════════════════════
【退出码】
════════════════════════════════════════════════════════════════════════
0 = 所有 rpc 进程接线完整
1 = 有 rpc 进程漏接线（打印明细与修法）
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SERVICE = os.path.join(ROOT, "service")


def find_rpc_modules():
    """返回 [(模块相对路径, Application.java 路径)]，只含 service/**/*-rpc。

    注意：`*Application.java` 位于 `<module>/src/main/java/...` **之下**，
    不在模块根目录 ⇒ 先定位 rpc 模块目录，再在模块内递归找。
    """
    hits = []
    for dirpath, dirnames, _filenames in os.walk(SERVICE):
        dirnames[:] = [d for d in dirnames if d not in ("target", "node_modules")]
        if not os.path.basename(dirpath).endswith("-rpc"):
            continue
        # 已定位到 rpc 模块目录，在其中递归找 Application 类（排除 test 源码）
        for sub, subdirs, subfiles in os.walk(dirpath):
            subdirs[:] = [d for d in subdirs if d not in ("target", "node_modules", "test")]
            for fn in subfiles:
                if fn.endswith("Application.java"):
                    hits.append((
                        os.path.relpath(dirpath, ROOT).replace(os.sep, "/"),
                        os.path.join(sub, fn),
                    ))
    return sorted(hits)


def read(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def has_factory_import(src):
    """① 是否 @Import 了 RpcServerInterceptorFactory。"""
    if "RpcServerInterceptorFactory" not in src:
        return False
    # 只认真正的 @Import 列表里出现，排除仅 import 语句 / javadoc 提及
    for m in re.finditer(r"@Import\s*\((.*?)\)", src, re.S):
        if "RpcServerInterceptorFactory" in m.group(1):
            return True
    return False


def has_configurer(src):
    """② 是否声明了返回 GrpcServerConfigurer 的 @Bean，且方法体里有 .intercept(。

    判据分两层，缺一不可：
      · 出现 `GrpcServerConfigurer` 作为返回类型（`public GrpcServerConfigurer xxx(`）
      · 且该方法体内出现 `.intercept(`
    """
    if "GrpcServerConfigurer" not in src:
        return False
    # 找形如 `GrpcServerConfigurer name(...) { ... }` 的方法体
    for m in re.finditer(
        r"GrpcServerConfigurer\s+\w+\s*\([^)]*\)\s*\{(.*?)\n\s*\}",
        src,
        re.S,
    ):
        if ".intercept(" in m.group(1):
            return True
    # 兜底：形如 `return server -> server.intercept(x);` 的单行 lambda
    if re.search(r"->\s*server\s*\.\s*intercept\s*\(", src):
        return True
    return False


def main():
    mods = find_rpc_modules()
    print("=" * 78)
    print("gRPC 拦截器接线检查（① @Import + ② GrpcServerConfigurer.intercept）")
    print("=" * 78)

    if not mods:
        print("\n未发现 rpc 模块。\n")
        return 0

    print(f"\n扫描到 {len(mods)} 个 rpc 进程：\n")
    hdr = f"  {'模块':<26} {'① @Import':<12} {'② Configurer':<14} 结论"
    print(hdr)
    print("  " + "-" * (len(hdr) - 2))

    violations = []
    for mod, app_path in mods:
        src = read(app_path)
        imp = has_factory_import(src)
        cfg = has_configurer(src)
        if imp and cfg:
            verdict = "✅"
        else:
            verdict = "🔴"
            violations.append((mod, app_path, imp, cfg))
        print(f"  {mod:<26} {'✅' if imp else '❌ 缺':<12} "
              f"{'✅' if cfg else '❌ 缺':<14} {verdict}")

    if not violations:
        print("\n✅ 所有 rpc 进程接线完整：拦截器既被 @Import 产出，也被 Configurer 挂到 server。")
        print("=" * 78)
        print()
        return 0

    print("\n❌ 检出接线不完整的 rpc 进程 —— 拦截器不会被框架使用，且**不报错**：\n")
    for mod, app_path, imp, cfg in violations:
        print(f"  🔴 {mod}")
        print(f"     文件：{os.path.relpath(app_path, ROOT)}")
        if not imp:
            print("     缺 ①：@Import 里没有 RpcServerInterceptorFactory")
            print("        ⇒ ServerInterceptor Bean 根本不存在")
        if not cfg:
            print("     缺 ②：没有 @Bean GrpcServerConfigurer … server.intercept(...)")
            print("        ⇒ Bean 存在，但 starter 从不自动挂到 server 上（H2 形态）")
        missing = []
        if not imp:
            missing.append("@Import(RpcServerInterceptorFactory.class)")
        if not cfg:
            missing.append("@Bean GrpcServerConfigurer { server -> server.intercept(interceptor); }")
        print(f"     修法：在 {os.path.basename(app_path)} 里补上 —— {' + '.join(missing)}")
        print("     ⚠️ 光补代码不够：还要配一条断言「server.intercept(...) 真被调用」的测试")
        print("        （§3.3：断言 Bean 存在 ≠ 断言机制生效）")
        print()

    print("  Go 侧对应物（每个 rpc 服务都显式注册，无一例外）：")
    print("    service/user/user/rpc/user.go:43 · service/user/admin/rpc/admin.go:43")
    print("    service/article/rpc/article.go:51 · service/task/rpc/task.go:44")
    print("    service/hot/rpc/hot.go:42 · service/like/rpc/like.go:41")
    print("    service/message/rpc/message.go:44 · service/comment/rpc/comment.go:54")
    print("    service/follow/rpc/follow.go:44 · service/favorite/rpc/favorite.go:49")
    print("=" * 78)
    print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
