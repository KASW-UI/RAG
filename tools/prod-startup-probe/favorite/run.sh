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

# ── 炸法分类（**按失败签名**判，不按「日志里出现过什么词」）──────────────────
# ⚠️ **2026-10-30 活体对拍轮实测修正**：本项目已升 **Spring Boot 4.0.8**
#    ⇒ 「判定成 SERVLET 但没有 servlet 容器」这个失败在 Boot 4 下的**签名与 Boot 3 不同**：
#      Boot 3 ⇒ "ServletWebServerFactory bean defined ..."（缺容器）
#      Boot 4 ⇒ classpath 读 `ResponseBodyAdvice` 失败（共享层 `common` 里的
#                `GoStyleJsonContentTypeAdvice implements ResponseBodyAdvice`，
#                而 rpc 模块**故意不带 spring-webmvc** ⇒ 该接口的字节不存在）：
#                "class path resource [org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.class]
#                 cannot be opened because it does not exist"
#    ⇒ 旧分类器只认 Boot 3 的那一句 ⇒ 在 Boot 4 下**恒判 other** ⇒ 探针**假红**
#      （2026-10-30 实测 EXIT=1；而同轮 B-case 真启动成功 + 21300 LISTEN
#        ⇒ **产品无缺陷，是探针误报**）。
# ⇒ 修法：两种签名都认；**且只认失败签名** ⇒ 成功启动的日志不会被误判成 servlet。
# ⇒ 本函数自带 `--self-test`（拿已知正例 + **负例**反测）⇒ 判据本身可复算。
classify_log() {  # $1 = 日志路径 ⇒ 打印 uri / servlet / started / other
  local log="$1"
  if grep -q "Incorrectly formatted target uri" "${log}" 2>/dev/null; then
    printf 'uri'; return
  fi
  # ⚠️ **成功优先**：成功启动的日志里也可能出现 `ServletWebServerApplicationContext`
  #    （那是**正常的**上下文类名，不是失败签名）⇒ 必须先判 started。
  if grep -q "Started FavoriteRpcApplication" "${log}" 2>/dev/null; then
    printf 'started'; return
  fi
  if grep -q "ServletWebServerFactory bean defined" "${log}" 2>/dev/null \
     || grep -q "class path resource \[org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.class\] cannot be opened" "${log}" 2>/dev/null; then
    printf 'servlet'; return
  fi
  printf 'other'
}

# ── 判据 → 退出码的映射（**纯函数** ⇒ 可自检）───────────────────────────────
# 🔴🔴 **2026-10-30 教训（本轮真踩到了）**：分类器 `classify_log` **自检 6/6 全绿**，
#   但探针实际出口**仍然是错的** —— 重构时**忘了把分类结果写回 `LAST_KIND`**
#   ⇒ 三个 case 的 kind 全成 `other` ⇒ 探针报「A/B/C 不成立」。
#   ⇒ **自检只测了「判据函数」，没测「判据 → 退出码」的接线** ⇒ 探针可以
#     **自检全绿而出口是错的**（这正是「跑绿 ≠ 对」在**探针自己**身上的形态）。
# ⇒ 修法：把「日志 → (kind, rc)」整条决策路径收进**一个纯函数** `verdict_of_log`，
#   `run_case` **只调它**（不再自己拼 kind/rc）⇒ 自检对它**逐档**反测 ⇒ 接线错不了。
kind_to_rc() {  # $1 = kind ⇒ 0=起来 · 1=炸了 · 2=无结论/超时
  case "$1" in
    started)          printf '0' ;;
    uri|servlet)      printf '1' ;;
    *)                printf '2' ;;
  esac
}

verdict_of_log() {  # $1 = 日志路径 ⇒ 打印 "<kind> <rc>"
  local kind
  kind="$(classify_log "$1")"
  printf '%s %s' "${kind}" "$(kind_to_rc "${kind}")"
}

# ── 分类器自检（**拿已知错例反测**：自制判据的漏报比误报更难发现）──────────
self_test() {
  local d fails=0 got
  d="$(mktemp -d)"
  printf 'Caused by: java.lang.IllegalArgumentException: Incorrectly formatted target uri; expected: %s\n' \
    "'discovery:[//]/<service-name>'" > "${d}/uri.log"
  printf 'APPLICATION FAILED TO START\nServletWebServerFactory bean defined but missing\n' > "${d}/servlet3.log"
  printf 'Caused by: java.io.FileNotFoundException: class path resource [org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.class] cannot be opened because it does not exist\n' > "${d}/servlet4.log"
  printf 'Started FavoriteRpcApplication in 1.85 seconds\n' > "${d}/started.log"
  printf 'APPLICATION FAILED TO START\nCaused by: java.lang.NullPointerException: unrelated failure\n' > "${d}/other.log"
  printf 'Started FavoriteRpcApplication in 1.85 seconds\n\tat org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.refresh\n' > "${d}/started_servlet_ctx.log"

  # ⚠️ 这里测的是**整条决策路径**（日志 → kind → rc），**不是**只测 classify_log
  #   ⇒ 才抓得住「kind 算对了、rc 没接上」这一类接线错。
  check() {  # $1 = 日志名  $2 = 期望 kind  $3 = 期望 rc
    local v kind rc
    v="$(verdict_of_log "${d}/$1")"
    kind="${v%% *}"; rc="${v##* }"
    if [ "${kind}" = "$2" ] && [ "${rc}" = "$3" ]; then
      printf '  ✅ %-22s ⇒ kind=%-8s rc=%s\n' "$1" "${kind}" "${rc}"
    else
      printf '  ❌ %-22s ⇒ 期望 kind=%s rc=%s 实得 kind=%s rc=%s\n' "$1" "$2" "$3" "${kind}" "${rc}"; fails=1
    fi
  }
  check uri.log                uri      1
  check servlet3.log           servlet  1
  check servlet4.log           servlet  1
  check started.log            started  0
  check other.log              other    2
  check started_servlet_ctx.log started  0
  if [ "${fails}" -eq 0 ]; then
    printf '  ✅ 决策路径自检 6/6 通过（含 kind → rc 的接线）\n'
  else
    printf '  ❌ 决策路径自检有反例 ⇒ 判据/接线本身不可信\n'
  fi
  return "${fails}"
}

# ── 分类器自检入口（**不起服务、不碰 DB**，秒级）────────────────────────────
# ⚠️ 为什么单独开一个入口：判据本身如果错了，整个探针的退出码就**没有意义**
#   ⇒ 「跑绿 ≠ 对」在**探针自己**身上同样成立（AGENTS.md §3.3.3）。
if [ "${1:-}" = "--self-test" ]; then
  printf '== prod-startup-probe 分类器自检 ==\n'
  self_test
  exit $?
fi

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
# ⚠️ **2026-10-30 结构调整**：本函数**只负责「起进程 + 打印 + 落日志」**，
#   **不参与最终判定** ⇒ 最终判定直接读 `${TMP}/<tag>.log` 重算（见 §7）。
#   ⇒ 这样「判定」只有**一条**分类路径（`verdict_of_log`）⇒ 不存在「这里算对了、
#     那边没接上」的第二条路径。
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

  local v; v="$(verdict_of_log "${log}")"
  local kind="${v%% *}" rc="${v##* }"
  case "${kind}" in
    uri)
      bad "${tag}：**启动即炸**（devh URI 校验 · 两斜杠）"
      grep -m1 -A2 "Caused by: java.lang.IllegalArgumentException: Incorrectly formatted target uri" "${log}" | head -3 | sed 's/^/     /'
      kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
      wait_port_free
      return "${rc}"
      ;;
    servlet)
      bad "${tag}：**启动即炸**（Boot 判定成 SERVLET 但没有 servlet 容器）"
      grep -m1 -B2 -A3 -E "ServletWebServerFactory bean defined|ResponseBodyAdvice.class" "${log}" | head -8 | sed 's/^/     /'
      kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
      wait_port_free
      return "${rc}"
      ;;
    started)
      ok "${tag}：**启动成功**"
      grep -E "gRPC Server started|Started FavoriteRpcApplication" "${log}" | head -2 | sed 's/^/     /'
      lsof -nP -iTCP:"${GRPC_PORT}" -sTCP:LISTEN 2>/dev/null | head -3 | sed 's/^/     /'
      kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
      wait_port_free
      return "${rc}"
      ;;
  esac
  bad "${tag}：120s 内既没起来也没报已知错误 ⇒ 看 ${TMP}/${tag}.log"
  tail -30 "${log}" | sed 's/^/     /'
  kill "${pid}" >/dev/null 2>&1; wait "${pid}" 2>/dev/null
  wait_port_free
  return "${rc}"
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

# ── 5. B · 修后（生产 yaml 原样）───────────────────────────────────────────
say "B · 修后（**不传任何 --grpc.client.* 覆盖**）"
run_case "B-after"

# ── 6. C · 修前（web 类型）────────────────────────────────────────────────
# ⚠️ 这一条是 **2026-10-26 A/B 途中撞出来的**（比两斜杠更靠前）：
#    classpath 上有 jakarta.servlet ⇒ Boot 判定本进程是 SERVLET web app
#    ⇒ 但本服务没有 servlet 容器 ⇒ 启动即死。
#    修法是 yaml 里的 `spring.main.web-application-type: none`。
#    这里用 `--spring.main.web-application-type=servlet` 复现「**没有那一行**」的效果
#    （无该行时 Boot 由 deduceFromClasspath() 判定 ⇒ 同样是 SERVLET）。
say "C · 修前（把 web-application-type 覆盖回 servlet）"
run_case "C-before" --spring.main.web-application-type=servlet

# ── 7. 判定 ────────────────────────────────────────────────────────────────
# 🔴🔴 **2026-10-30 教训的根治**：判定**直接由日志文件重新推导**，
#   **不再经由 `run_case` 的返回值 / 全局 `LAST_KIND` 传递**。
#   ⇒ 上一版就是栽在「分类算对了、但没写回全局」这一根接线上 ⇒
#     **自检全绿而出口全错**。
#   ⇒ 现在 `run_case` 只负责「起进程 + 打印 + 落日志」，**不参与判定**；
#     判定只认 `verdict_of_log <日志文件>` 这一条**纯函数**路径 ⇒
#     **没有中间状态可丢** ⇒ 同类 bug 结构上不可能再发生。
#     （自检覆盖的正是这条路径 —— 见 `self_test`。）
echo
FAILED=""
vA="$(verdict_of_log "${TMP}/A-before.log")"; A_KIND="${vA%% *}"; A_RC="${vA##* }"
vB="$(verdict_of_log "${TMP}/B-after.log")";  B_KIND="${vB%% *}"; B_RC="${vB##* }"
vC="$(verdict_of_log "${TMP}/C-before.log")"; C_KIND="${vC%% *}"; C_RC="${vC##* }"
[ "${A_RC}" -eq 1 ] && [ "${A_KIND}" = "uri" ] || FAILED="A（期望「炸·uri」，实际 rc=${A_RC} kind=${A_KIND}）"
[ "${C_RC}" -eq 1 ] && [ "${C_KIND}" = "servlet" ] || FAILED="${FAILED} C（期望「炸·servlet」，实际 rc=${C_RC} kind=${C_KIND}）"
[ "${B_RC}" -eq 0 ] && [ "${B_KIND}" = "started" ] || FAILED="${FAILED} B（期望「起来」，实际 rc=${B_RC} kind=${B_KIND}）"

if [ -z "${FAILED}" ]; then
  ok "A/B/C 成立：两斜杠 ⇒ 炸(URI) ；web 类型 ⇒ 炸(Servlet) ；生产 yaml 原样 ⇒ 真起来"
  exit 0
fi
bad "A/B/C 不成立 ⇒ ${FAILED}"
exit 1
