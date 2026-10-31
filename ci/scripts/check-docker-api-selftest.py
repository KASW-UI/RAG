#!/usr/bin/env python3
# =============================================================================
# ci/scripts/check-docker-api-selftest.py —— docker_api 的**无依赖自测**
#
# 【守什么】
#   tools/difftest/docker_api.py 是 R6 Schema 契约门在内层容器里起 PG 的**唯一通道**
#   （嵌套构建容器没有 docker CLI ⇒ 只能走 Docker Engine API）。
#   ⇒ 它的 `image_exists` 一旦判错，R6 会把「镜像没拉过」报成「查镜像失败」。
#
# 【实测抓到的真实缺陷（2026-10-31 · R6 在 Concourse Task 内首跑）】
#   原实现：`GET /images/{name}/json` —— **把 tag 丢了**。
#   Docker daemon 会把**不带 tag**的引用解析成 **`:latest`** ⇒
#     `image_exists("postgres:16-alpine")` 实际问的是 `postgres:latest`
#     ⇒ 本机 registry 里明明有 `library/postgres:16-alpine`，
#       内层 daemon 却因为没有 `postgres:latest` 而返回 404
#     ⇒ R6 报「查镜像失败 ⇒ 全部域 BLOCKED」。
#   ⚠️ 同一个 bug 还有**第二个面**：若 `:latest` 恰好存在（如本地 docker 镜像），
#     `image_exists("postgres:16-alpine")` 会**假阳性**返回 True
#     ⇒ 跳过拉镜像 ⇒ 随后 `create_container` 报「起容器失败」。
#   ⇒ 两个面都必须被本门锁住。
#
# 【为什么是无依赖自测，而不是真起容器】
#   · 真起容器需要 daemon + 镜像 ⇒ 在 CI 的静态守门阶段没有这些条件；
#   · 而本 bug 的本质是「**请求的路径对不对**」⇒ 用一个**模拟 daemon 的解析规则**
#     的假 client 就能精确断言 ⇒ 纯标准库、秒级、可进静态守门。
#   ⚠️ 模拟规则必须**照抄 Docker 的真实语义**（裸名 ⇒ :latest）⇒ 否则测的是模拟器
#     不是被测物 ⇒ 所以这里把该规则写进注释并在断言里显式引用。
#
# 【退出码】0 = PASS / 1 = FAIL
# =============================================================================
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "difftest"))

import docker_api  # noqa: E402


class FakeDaemon:
    """模拟 Docker daemon 的 `GET /images/{ref}/json` 解析规则。

    Docker 的真实规则：
      · 引用里**不带 tag**（如 `postgres`）⇒ daemon 按 **`:latest`** 解析；
      · 引用带 tag（如 `postgres:16-alpine`）⇒ 按该 tag 解析；
      · 引用带端口的仓库前缀（如 `reg:5000/foo`）⇒ 端口不是 tag ⇒ 仍按 `:latest`。
    """

    def __init__(self, images):
        self.images = set(images)
        self.requested = []

    def resolve(self, ref):
        last = ref.rsplit("/", 1)[-1]
        if ":" in last:
            return ref
        return f"{ref}:latest"

    def get(self, ref):
        """⇒ (status, body)"""
        self.requested.append(ref)
        if self.resolve(ref) in self.images:
            return 200, b'{"Id":"sha256:x"}'
        return 404, b'{"message":"No such image: ' + ref.encode() + b'"}'


class FakeClient(docker_api.DockerClient):
    """只替换底层 HTTP；其余（image_exists 的逻辑）走被测真代码。"""

    def __init__(self, daemon):
        super().__init__(host="unix:///dev/null")  # 不会真连
        self.daemon = daemon

    def _request(self, method, path, body=None, raw=False):
        assert method == "GET" and path.startswith("/images/"), path
        ref = path[len("/images/"):-len("/json")]
        ref = docker_api.urllib.parse.unquote(ref)
        status, data = self.daemon.get(ref)
        if status >= 400:
            raise docker_api.DockerError(
                f"{method} {path} ⇒ HTTP {status}: "
                f"{data[:400].decode('utf-8', 'replace')}")
        return status, data


FAILURES = []


def check(name, cond, detail=""):
    if cond:
        print(f"✅ {name}")
    else:
        print(f"❌ {name}" + (f" —— {detail}" if detail else ""))
        FAILURES.append(name)


def safe_exists(client, image):
    """调 image_exists 但**不抛** ⇒ (结果, 异常或 None)

    ⚠️ 纪律：被测物出问题时，测试必须**红在断言上**而不是红在 Traceback 上
       ⇒ 否则读数无法归因（「测试炸了」≠「判据抓到了缺陷」）。
    """
    try:
        return client.image_exists(image), None
    except Exception as e:  # noqa: BLE001 —— 任何异常都要变成可读的断言失败
        return None, e


def main() -> int:
    # 本机只有 postgres:16-alpine，**没有** postgres:latest
    daemon = FakeDaemon({"postgres:16-alpine", "reg:5000/foo:latest"})
    c = FakeClient(daemon)

    # --- 断言 1：tag 必须出现在请求里 ---------------------------------------
    r, err = safe_exists(c, "postgres:16-alpine")
    check("断言1 · 带 tag 的镜像 ⇒ 请求里必须带 tag",
          daemon.requested == ["postgres:16-alpine"],
          f"实际请求路径 = {daemon.requested}")
    check("断言1b · 已存在的带 tag 镜像 ⇒ 返回 True",
          r is True and err is None,
          f"实际 = {r!r}，异常 = {err!r}")

    # --- 断言 2：裸名 ⇒ **客户端显式**补全 :latest --------------------------
    # ⚠️ 修复后客户端不再依赖 daemon 的隐式解析 ⇒ 请求里就带显式 tag
    #   （语义与 daemon 的隐式规则一致，但**可被断言** ⇒ 不会静默跑偏）
    daemon.requested.clear()
    r, err = safe_exists(c, "postgres")
    check("断言2 · 裸名 ⇒ 客户端显式补 :latest ⇒ 不存在 ⇒ False",
          r is False and err is None and daemon.requested == ["postgres:latest"],
          f"实际 = {r!r}，请求 = {daemon.requested}，异常 = {err!r}")

    # --- 断言 3：404 必须返回 False，不能抛 ---------------------------------
    check("断言3 · 镜像不存在（404）⇒ 返回 False 而不是抛异常",
          r is False and err is None,
          f"实际 = {r!r}，异常 = {err!r}")

    # --- 断言 4：带端口的仓库前缀 ⇒ tag 仍是 latest -------------------------
    daemon.requested.clear()
    r, err = safe_exists(c, "reg:5000/foo")
    check("断言4 · 带端口的仓库前缀 ⇒ 按 :latest 查",
          r is True and err is None and daemon.requested == ["reg:5000/foo:latest"],
          f"实际 = {r!r}，请求 = {daemon.requested}，异常 = {err!r}")

    # --- 断言 5：非 404 的真错误仍然要抛（不能被吞成 False）-----------------
    class Boom(FakeClient):
        def _request(self, method, path, body=None, raw=False):
            raise docker_api.DockerError("GET /images/x/json ⇒ HTTP 500: boom")

    raised = False
    try:
        Boom(FakeDaemon([])).image_exists("postgres:16-alpine")
    except docker_api.DockerError:
        raised = True
    check("断言5 · 非 404 的 daemon 错误 ⇒ 必须抛出（不得吞成 False）", raised)

    print()
    if FAILURES:
        print(f"❌ docker_api 自测失败：{len(FAILURES)} 条断言未通过")
        for f in FAILURES:
            print(f"   · {f}")
        return 1
    print("✅ docker_api 自测全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
