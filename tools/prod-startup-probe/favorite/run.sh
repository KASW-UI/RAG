#!/usr/bin/env bash
# ============================================================================
# 生产启动形态 · 真 fat jar 活体探针 · favorite 域
# ----------------------------------------------------------------------------
# 目的：证明「**生产配置原样**能不能真起得来」—— 这一层是
#   「@SpringBootTest 全绿」**永远证明不了**的（见 PIT-FAV-RPC-018）。
#
# 两侧都起**同一个真 fat jar**，用**生产 application.yml 原样**：
#   · **不传**任何 `--grpc.client.*.address` 覆盖 ⇒ client 地址就是生产值
#   · 只覆盖「环境相关」的三项：PG 连接 / Nacos 地址 / gRPC 监听端口
#     （端口要挑空闲的，且必须**双绑定自检** —— 见下方「端口纪律」）
#
# 两个 case：
#   A · 修前（两斜杠）：把两个 client 地址**按字节**改回 `discovery://<name>`
#       ⇒ 期望**启动即炸**（devh 的 URI 校验）
#   B · 修后（三斜杠）：**不传任何 client 覆盖** ⇒ 期望**真起来**
#       ⇒ 并用 lsof 核对端口真的 LISTEN
#   C · 修前（web 类型）：把 `spring.main.web-application-type` 覆盖成 `servlet`
#       ⇒ 期望**启动即炸**（Boot 判定成 SERVLET 但没有 servlet 容器）
#
# 为什么 A 要「用参数复现旧值」而不是「git 改回旧 yaml」：
#   几个 case 跑的是**同一个 jar**，唯一变量就是**那一个参数** ⇒ 差异**只能**来自该参数。
#   而参数值与修复前的生产值**逐字节相同**（= `discovery://user-rpc`）。
#
# 退出码：0 = A/C 炸且 B 起来（缺陷已闭）；1 = 不成立；2 = 环境问题。
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say() { printf '\033[1;36m[prod-startup-probe]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }
die() { bad "$*"; exit 2; }

cd "${RAG_ROOT}"

JAR="service/favorite/favorite-rpc/target/favorite-rpc-0.0.1-SNAPSHOT.jar"
[ -f "${JAR}" ] || die "找不到 ${JAR} ⇒ 先跑：mvn -o -pl service/favorite/favorite-rpc -am package -DskipTests"

TMP="$(mktemp -d)"
PG_CONTAINER="fav-prod-startup-probe-pg-$$"
cleanup() {
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
  if [ "${KEEP_TMP:-0}" = "1" ]; then
    say "KEEP_TMP=1 ⇒ 临时目录保留在 ${TMP}"
  else
    rm -rf "${TMP}"
  fi
}
trap cleanup EXIT

# ── 1. 真 PG ────────────────────────────────────────────────────────────────
say "起 postgres:16-alpine 容器：${PG_CONTAINER}"
docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
docker run -d --rm --name "${PG_CONTAINER}" \
  -e POSTGRES_PASSWORD=probe -e POSTGRES_USER=postgres -p 0:5432 postgres:16-alpine >/dev/null \
  || die "PG 容器起不来"
PG_PORT="$(docker port "${PG_CONTAINER}" 5432/tcp | head -1 | sed 's/.*://')"
[ -n "${PG_PORT}" ] || die "拿不到 PG 映射端口"
# ⚠️ 必须探 **TCP**（-h 127.0.0.1）：PG 官方镜像初始化阶段的临时 server 只监听 unix socket
#    ⇒ 走 socket 探活会**假就绪**（见 PIT-FAV-RPC-020）。
PG_READY=0
for _ in $(seq 1 180); do
  docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 && { PG_READY=1; break; }
  [ "$(docker inspect -f '{{.State.Running}}' "${PG_CONTAINER}" 2>/dev/null || echo false)" = "true" ] || break
  sleep 1
done
[ "${PG_READY}" = "1" ] || die "PG 180s 内没就绪"
for _ in $(seq 1 15); do
  docker exec "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -c "CREATE DATABASE first_db;" >/dev/null 2>&1 && break
  sleep 2
done
ok "PG 就绪（127.0.0.1:${PG_PORT}）· 库 first_db"

# ── 2. 端口纪律：专用段 + **双绑定自检** ─────────────────────────────────────
# ⚠️ macOS 上 `127.0.0.1:P` 会**抢占**通配地址 `0.0.0.0:P`
#    ⇒ 客户端连 127.0.0.1:P 会打到**别人**（见 PIT-FAV-API-008）
#    ⇒ 必须 `0.0.0.0:P` 与 `127.0.0.1:P` **都能绑**才算可用。
GRPC_PORT="$(python3 - <<'PYPORT'
import socket
for p in range(21300, 21400):
    socks = []
    try:
        for addr in (("0.0.0.0", p), ("127.0.0.1", p)):
            s = socket.socket()
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind(addr); s.listen(1); socks.append(s)
        print(p); break
    except OSError:
        for s in socks:
            s.close()
else:
    raise SystemExit("专用端口段 21300-21399 已占满")
PYPORT
)" || die "挑不到空闲端口"
ok "gRPC 监听端口 ${GRPC_PORT}（专用段 21300-21399 · 双绑定自检通过）"

# ── 3. 两个 case 共用的启动参数 ────────────────────────────────────────────
# ⚠️ 这里**只**覆盖「环境相关」的三项（PG / Nacos / 监听端口）。
#    **绝不**覆盖 `--grpc.client.*.address` ⇒ 那个必须是生产 yaml 的原值。
COMMON=(
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:${PG_PORT}/first_db?TimeZone=Asia/Shanghai"
  --spring.datasource.username=postgres
  --spring.datasource.password=probe
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:1
  --management.otlp.tracing.export.enabled=false
  --grpc.server.port="${GRPC_PORT}"
)

# 起一次，等它给出结论（起来了 / 炸了 / 超时）。
# 返回：0 = 起来了；1 = 炸了；2 = 超时（没结论）。
# 副作用：把判定出的「炸法」写进全局变量 LAST_KIND（uri / servlet / other）。
run_case() {
  local tag="$1"; shift
  local log="${TMP}/${tag}.log"
  say "起一次：${tag}"
  # ⚠️ 必须用 `exec`：`( cd X && java ... ) &` 起的是**子 shell**，
  #    `kill $!` 杀的是**子 shell**，java 会变成**孤儿进程**继续占着端口 ⇒ 下一个 case 被端口占用干扰。
  #    `exec` 让 java **替换**子 shell ⇒ `$!` 就是 java 的 pid ⇒ kill 干净。
  ( cd "${RAG_ROOT}" && exec java -jar "${JAR}" "${COMMON[@]}" "$@" > "${log}" 2>&1 ) &
  local pid=$!
  for _ in $(seq 1 120); do
    grep -qE "Started FavoriteRpcApplication|APPLICATION FAILED TO START" "${log}" 2>/dev/null && break
    kill -0 "${pid}" 2>/dev/null || break
    sleep 1
  done
  sleep 2

  LAST_KIND="other"
  if grep -q "Incorrectly formatted target uri" "${log}" 2>/dev/null; then
    LAST_KIND="uri"
    bad "${tag}：**启动即炸**（devh URI 校验 · 两斜杠）"
    grep -m1 -A2 "Caused by: java.lang.IllegalArgumentException: Incorrectly formatted target uri" "${log}" | head -3 | sed 's/^/     /'
    kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
    wait_port_free
    return 1
  fi
  if grep -q "ServletWebServerFactory bean defined" "${log}" 2>/dev/null; then
    LAST_KIND="servlet"
    bad "${tag}：**启动即炸**（Boot 判定成 SERVLET 但没有 servlet 容器）"
    grep -m1 -B2 -A3 "ServletWebServerFactory bean defined" "${log}" | head -8 | sed 's/^/     /'
    kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
    wait_port_free
    return 1
  fi
  if grep -q "Started FavoriteRpcApplication" "${log}" 2>/dev/null; then
    LAST_KIND="started"
    ok "${tag}：**启动成功**"
    grep -E "gRPC Server started|Started FavoriteRpcApplication" "${log}" | head -2 | sed 's/^/     /'
    lsof -nP -iTCP:"${GRPC_PORT}" -sTCP:LISTEN 2>/dev/null | head -3 | sed 's/^/     /'
    kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
    wait_port_free
    return 0
  fi
  bad "${tag}：120s 内既没起来也没报已知错误 ⇒ 看 ${TMP}/${tag}.log"
  tail -30 "${log}" | sed 's/^/     /'
  kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
  wait_port_free
  return 2
}

# 等监听端口真正释放 ⇒ 下一个 case 不会撞「端口被上一个 case 占着」。
wait_port_free() {
  for _ in $(seq 1 30); do
    lsof -nP -iTCP:"${GRPC_PORT}" -sTCP:LISTEN >/dev/null 2>&1 || return 0
    sleep 1
  done
  bad "端口 ${GRPC_PORT} 30s 内没释放（上一个 case 的进程可能没死干净）"
  return 1
}

# ── 4. A · 修前（两斜杠）──────────────────────────────────────────────────
say "A · 修前（把 client 地址按字节改回两斜杠）"
run_case "A-before" \
  --grpc.client.user-rpc.address=discovery://user-rpc \
  --grpc.client.article-rpc.address=discovery://article-rpc
A_RC=$?
A_KIND="${LAST_KIND}"

# ── 5. B · 修后（生产 yaml 原样）───────────────────────────────────────────
say "B · 修后（**不传任何 --grpc.client.* 覆盖**）"
run_case "B-after"
B_RC=$?
B_KIND="${LAST_KIND}"

# ── 6. C · 修前（web 类型）────────────────────────────────────────────────
# ⚠️ 这一条是 **2026-10-26 A/B 途中撞出来的**（比两斜杠更靠前）：
#    classpath 上有 jakarta.servlet ⇒ Boot 判定本进程是 SERVLET web app
#    ⇒ 但本服务没有 servlet 容器 ⇒ 启动即死。
#    修法是 yaml 里的 `spring.main.web-application-type: none`。
#    这里用 `--spring.main.web-application-type=servlet` 复现「**没有那一行**」的效果
#    （无该行时 Boot 由 deduceFromClasspath() 判定 ⇒ 同样是 SERVLET）。
say "C · 修前（把 web-application-type 覆盖回 servlet）"
run_case "C-before" --spring.main.web-application-type=servlet
C_RC=$?
C_KIND="${LAST_KIND}"

# ── 7. 判定 ────────────────────────────────────────────────────────────────
echo
FAILED=""
[ "${A_RC}" -eq 1 ] && [ "${A_KIND}" = "uri" ] || FAILED="A（期望「炸·uri」，实际 rc=${A_RC} kind=${A_KIND}）"
[ "${C_RC}" -eq 1 ] && [ "${C_KIND}" = "servlet" ] || FAILED="${FAILED} C（期望「炸·servlet」，实际 rc=${C_RC} kind=${C_KIND}）"
[ "${B_RC}" -eq 0 ] && [ "${B_KIND}" = "started" ] || FAILED="${FAILED} B（期望「起来」，实际 rc=${B_RC} kind=${B_KIND}）"

if [ -z "${FAILED}" ]; then
  ok "A/B/C 成立：两斜杠 ⇒ 炸(URI) ；web 类型 ⇒ 炸(Servlet) ；生产 yaml 原样 ⇒ 真起来"
  exit 0
fi
bad "A/B/C 不成立 ⇒ ${FAILED}"
exit 1
