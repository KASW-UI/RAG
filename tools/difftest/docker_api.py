#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
**极简 Docker Engine API 客户端**（纯标准库，无第三方依赖）。

════════════════════════════════════════════════════════════════════════
【为什么需要它】
════════════════════════════════════════════════════════════════════════
CI 的**嵌套构建容器**里**没有 docker 客户端**（实测 2026-10-31：
`command -v docker` → MISSING，镜像 = `maven:3.9.11-eclipse-temurin-21`；
证据见 `ci/scripts/testcontainers-env.sh` 的「⚠️ 不能依赖 docker CLI」段）
⇒ 但那个容器**能**通过挂载进来的 unix socket 访问 Docker API
（`/var/run/docker.sock`，由 `ci/scripts/dind-run.sh` 的 `-v` 挂进去）。

⇒ 于是：**有 CLI 走 CLI（最省事），没有 CLI 走 Docker API over socket**。
⇒ 两条路的能力**对齐**：起容器 / 等就绪 / exec（含 stdin）/ 停容器 / 删容器 / 拉镜像。

⚠️ **本模块只服务 R6（schema 契约门）** —— 它**不读 Go 仓、不跑 Go 探针**。

【用法】
    from docker_api import DockerClient
    c = DockerClient()                       # 读 DOCKER_HOST 环境变量
    c.ping()                                 # daemon 可达？
    cid = c.create_container("pg", "postgres:16-alpine",
                             env={"POSTGRES_USER": "admin", ...})
    c.start_container(cid)
    rc, out, err = c.exec(cid, ["psql", "-U", "admin", "-d", "postgres", "-c", "select 1"])
"""
import http.client
import json
import os
import shutil
import socket
import struct
import subprocess
import time
import urllib.parse


class DockerError(RuntimeError):
    pass


# ── unix socket / tcp 两种传输 ────────────────────────────────────────────
class _UnixHTTPConnection(http.client.HTTPConnection):
    """把 HTTP 连到 unix socket 上（Docker API 的标准访问方式）。"""

    def __init__(self, socket_path, timeout=120):
        super().__init__("localhost", timeout=timeout)
        self.socket_path = socket_path

    def connect(self):
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.settimeout(self.timeout)
        s.connect(self.socket_path)
        self.sock = s


def _decode_multiplexed(data: bytes):
    """Docker 的非 TTY exec 输出是**多路复用帧**：[type(1), 0,0,0, size(4 BE)] + payload。

    ⇒ 拆成 (stdout, stderr)；裸流（无帧头）⇒ 全部算 stdout。
    """
    out, err = bytearray(), bytearray()
    i = 0
    if len(data) >= 8 and data[1:4] == b"\x00\x00\x00":
        while i + 8 <= len(data):
            stype = data[i]
            size = struct.unpack(">I", data[i + 4:i + 8])[0]
            payload = data[i + 8:i + 8 + size]
            if len(payload) < size:
                break
            (out if stype == 1 else err).extend(payload)
            i += 8 + size
        if i == 0:
            return data.decode("utf-8", "replace"), ""
        return out.decode("utf-8", "replace"), err.decode("utf-8", "replace")
    return data.decode("utf-8", "replace"), ""


class DockerClient:
    """⇒ Docker Engine API 的最小可用子集。"""

    def __init__(self, host=None, timeout=180):
        self.host = host or os.environ.get("DOCKER_HOST") or \
            "unix:///var/run/docker.sock"
        self.timeout = timeout
        if self.host.startswith("unix://"):
            self._sock_path = self.host[len("unix://"):]
            self._tcp = None
        else:
            # tcp://host:port （含 https:// 变体）
            h = self.host
            if h.startswith("tcp://"):
                h = "http://" + h[len("tcp://"):]
            elif h.startswith("http://") or h.startswith("https://"):
                pass
            else:
                h = "http://" + h
            u = urllib.parse.urlparse(h)
            self._tcp = (u.hostname or "localhost", u.port or 2375)
            self._sock_path = None

    # ---- 底层 HTTP -------------------------------------------------------
    def _conn(self):
        if self._sock_path:
            return _UnixHTTPConnection(self._sock_path, timeout=self.timeout)
        return http.client.HTTPConnection(self._tcp[0], self._tcp[1],
                                          timeout=self.timeout)

    def _request(self, method, path, body=None, raw=False):
        """⇒ (status, bytes)；非 2xx ⇒ 抛 DockerError（带 daemon 的错误文本）。"""
        conn = self._conn()
        try:
            headers = {"Content-Type": "application/json"}
            payload = None
            if body is not None:
                payload = body if isinstance(body, bytes) else \
                    json.dumps(body).encode("utf-8")
                headers["Content-Length"] = str(len(payload))
            conn.request(method, path, body=payload, headers=headers)
            resp = conn.getresponse()
            data = resp.read()
            if raw:
                return resp.status, data
            if resp.status >= 400:
                raise DockerError(
                    f"{method} {path} ⇒ HTTP {resp.status}: "
                    f"{data[:400].decode('utf-8', 'replace')}")
            return resp.status, data
        finally:
            try:
                conn.close()
            except Exception:
                pass

    def _json(self, method, path, body=None):
        _, data = self._request(method, path, body)
        if not data:
            return {}
        try:
            return json.loads(data.decode("utf-8"))
        except ValueError:
            return {"_raw": data.decode("utf-8", "replace")}

    # ---- 基础能力 --------------------------------------------------------
    def ping(self):
        try:
            status, _ = self._request("GET", "/_ping")
            return 200 <= status < 300
        except (OSError, http.client.HTTPException):
            return False

    def image_exists(self, image):
        name, tag = _split_image(image)
        _, data = self._request("GET", f"/images/{name}/json")
        return True

    def pull_image(self, image, timeout=600):
        """⇒ (ok, message) —— 拉镜像（daemon 的进度流读完为止）。"""
        name, tag = _split_image(image)
        try:
            status, data = self._request(
                "POST", f"/images/create?fromImage={urllib.parse.quote(name)}"
                        f"&tag={urllib.parse.quote(tag)}")
        except DockerError as e:
            return False, str(e)
        text = data.decode("utf-8", "replace")
        if '"error"' in text or '"errorDetail"' in text:
            return False, text[-400:]
        return True, f"已就绪（{image}）"

    def create_container(self, name, image, env=None, cmd=None, user=None):
        body = {
            "Image": image,
            "Env": [f"{k}={v}" for k, v in (env or {}).items()],
            "Tty": False,
            "OpenStdin": False,
        }
        if cmd:
            body["Cmd"] = cmd
        if user:
            body["User"] = user
        res = self._json("POST", f"/containers/create?name={urllib.parse.quote(name)}",
                         body)
        return res.get("Id")

    def start_container(self, cid):
        self._request("POST", f"/containers/{cid}/start")

    def stop_container(self, cid, timeout=5):
        try:
            self._request("POST", f"/containers/{cid}/stop?t={timeout}")
        except DockerError:
            pass

    def remove_container(self, cid, force=True, wait=True, timeout=30):
        """删容器（**默认等它真的消失**）。

        ⚠️ **为什么要等**：DELETE 返回后，容器**可能**仍在「停止中」⇒
           立刻用同一个名字 create ⇒ 409 Conflict（"is already in use"）⇒
           重建失败 ⇒ 域被判 BLOCKED（实测 2026-10-31 的域间自愈就是这么坏的）。
        ⇒ 这里轮询到 GET 返回 404 为止。
        """
        try:
            self._request("DELETE",
                          f"/containers/{cid}?force={'true' if force else 'false'}")
        except DockerError:
            pass   # 404 / 409 都容忍 ⇒ 下面用「还在不在」判定
        if not wait:
            return True
        deadline = time.time() + timeout
        while time.time() < deadline:
            if not self.container_exists(cid):
                return True
            time.sleep(0.3)
        return False

    def container_exists(self, name):
        try:
            self._request("GET", f"/containers/{name}/json")
            return True
        except DockerError:
            return False

    # ---- exec ------------------------------------------------------------
    def exec(self, cid, argv, stdin_data=None, env=None, timeout=None):
        """⇒ (returncode, stdout, stderr)"""
        body = {
            "AttachStdout": True,
            "AttachStderr": True,
            "Cmd": list(argv),
            "Tty": False,
        }
        if env:
            body["Env"] = [f"{k}={v}" for k, v in env.items()]
        if stdin_data is not None:
            body["AttachStdin"] = True
        res = self._json("POST", f"/containers/{cid}/exec", body)
        exec_id = res.get("Id")
        if not exec_id:
            raise DockerError(f"exec 创建失败: {res}")

        start_body = {"Detach": False, "Tty": False}
        if stdin_data is not None:
            start_body["AttachStdin"] = True

        old_timeout = self.timeout
        if timeout:
            self.timeout = timeout
        try:
            # ⚠️ 带 stdin 的 exec 会**劫持**连接（raw stream）⇒ 不能走普通 response 解析
            if stdin_data is not None:
                return self._exec_start_hijack(exec_id, start_body, stdin_data)

            conn = self._conn()
            try:
                payload = json.dumps(start_body).encode("utf-8")
                conn.request("POST", f"/exec/{exec_id}/start", body=payload,
                             headers={"Content-Type": "application/json",
                                      "Content-Length": str(len(payload))})
                resp = conn.getresponse()
                data = resp.read()
            finally:
                try:
                    conn.close()
                except Exception:
                    pass
        finally:
            self.timeout = old_timeout
        out, err = _decode_multiplexed(data)
        # exec 的退出码要单独查（输出流里没有）
        info = self._json("GET", f"/exec/{exec_id}/json")
        rc = info.get("ExitCode", 0)
        return rc, out, err

    def _exec_start_hijack(self, exec_id, start_body, stdin_data):
        conn = self._conn()
        try:
            payload = json.dumps(start_body).encode("utf-8")
            conn.putrequest("POST", f"/exec/{exec_id}/start")
            conn.putheader("Content-Type", "application/json")
            conn.putheader("Content-Length", str(len(payload)))
            conn.endheaders()
            conn.send(payload)
            resp = conn.getresponse()
            # 写 stdin（**不**多路复用）⇒ 半关闭写端 ⇒ 对端看到 EOF
            try:
                conn.sock.sendall(stdin_data)
                conn.sock.shutdown(socket.SHUT_WR)
            except OSError:
                pass
            data = resp.read()
        finally:
            try:
                conn.close()
            except Exception:
                pass
        out, err = _decode_multiplexed(data)
        info = self._json("GET", f"/exec/{exec_id}/json")
        return info.get("ExitCode", 0), out, err

    # ---- 便利：等 PG 就绪 --------------------------------------------------
    def container_logs(self, cid, tail=300):
        """⇒ 容器日志文本（stdout+stderr）"""
        try:
            _, data = self._request(
                "GET", f"/containers/{cid}/logs?stdout=1&stderr=1&tail={tail}")
        except DockerError:
            return ""
        out, err = _decode_multiplexed(data)
        return out + err

    def wait_postgres(self, cid, user="admin", db="postgres", timeout=90):
        """⇒ (ok, message) —— 判据：**两条同时成立**（见下）。

        ⚠️ **为什么不能只判 `pg_isready` 成功**（实测踩到）：
           postgres 镜像的 entrypoint 在 initdb 阶段会**临时起一个只监听 socket
           的服务**跑初始化脚本 ⇒ `pg_isready`（走 socket）**那时也会成功**
           ⇒ 单次/连续成功会把**临时服务**误判成「真服务已就绪」
           ⇒ 随后 entrypoint 停掉临时服务、exec 真服务 ⇒ 这中间的窗口里
              任何 exec 都会报「the database system is shut down」。
           （同样的坑记在 `tools/difftest/ddl-parity.sh` 的就绪判据注释里。）

        ⇒ 判据：
           ① 日志出现 **"PostgreSQL init process complete"**
              （这条出现在**临时服务已停**之后）；
           ② `pg_isready` **连续 2 次**成功。
        """
        deadline = time.time() + timeout
        consec = 0
        last = ""
        while time.time() < deadline:
            logs = self.container_logs(cid)
            if "PostgreSQL init process complete" not in logs:
                time.sleep(1)
                continue
            rc, out, err = self.exec(cid, ["pg_isready", "-U", user, "-d", db])
            if rc == 0:
                consec += 1
                if consec >= 2:
                    return True, "PG ready"
            else:
                consec = 0
                last = (err or out).strip()[:200]
            time.sleep(1)
        return False, f"PG {timeout}s 内没就绪：{last}"


def _split_image(image):
    """⇒ (name, tag) —— `postgres:16-alpine` ⇒ ("postgres", "16-alpine")"""
    if "@" in image:
        name, _, digest = image.partition("@")
        return name, digest
    if ":" in image and "/" not in image.split(":")[-1]:
        name, _, tag = image.rpartition(":")
        return name, tag
    return image, "latest"


# ── CLI 优先、API 兜底的统一入口 ─────────────────────────────────────────
def docker_exec(container, argv, stdin_data=None, timeout=180):
    """⇒ (returncode, stdout, stderr)

    ⚠️ **优先用 docker CLI**（本机开发、既有门禁都是这个路径 ⇒ 行为不变）；
       **没有 CLI 时**改走 Docker API over socket（CI 嵌套容器形态）。
    """
    cli = shutil.which("docker")
    if cli:
        cmd = [cli, "exec", "-i", container] + list(argv)
        # ⚠️ CLI 路径走 text 模式 ⇒ 传 bytes 时先解码（两边语义一致）
        payload = stdin_data
        if isinstance(payload, bytes):
            payload = payload.decode("utf-8", errors="replace")
        try:
            p = subprocess.run(cmd, input=payload, capture_output=True,
                               text=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            return 124, "", f"docker exec 超时（{timeout}s）"
        return p.returncode, p.stdout, p.stderr
    c = DockerClient()
    payload = stdin_data.encode("utf-8") if isinstance(stdin_data, str) else stdin_data
    return c.exec(container, argv, stdin_data=payload, timeout=timeout)
