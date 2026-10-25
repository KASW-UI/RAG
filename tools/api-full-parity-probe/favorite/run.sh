#!/usr/bin/env bash
# ============================================================================
# api **整域**双侧活体对拍探针 · favorite 域
# ----------------------------------------------------------------------------
# 补的缺口：docs/DRIFT/favorite/api.md §4 第一行「🔴 未做：双侧活体对拍（真 Go
# favorite-api vs RAG）」⇒ 本探针把这条变成「✅ 已验」。
#
# 验的层：**整个 HTTP 面** —— 路由 / method / JWT 中间件 / 黑名单中间件 /
#         **gRPC 往返** / 状态码 / 响应头。
# ⇒ 增量价值：favorite-api 的**业务码不在 gRPC status 里，而在 description 文案里**
#   （Go favoritecommon.BizCodeFromError 反查 ⇒ RAG FavoriteGrpcError.bizCodeFromError）
#   ⇒ 这条链路的端到端正确性此前只被 rpc 级对拍**间接**覆盖，api 级从未活体对过。
#
# 两侧怎么跑（4 个应用进程 + 2 个中间件）：
#   · Go  favorite-rpc  = 真源 service/favorite/rpc（逐字节复制件 + sha256 校验）
#   · Go  favorite-api  = 真源 service/favorite/api（逐字节复制件 + sha256 校验）
#     ⇒ 真 rest.MustNewServer（HTTP）+ 真 zrpc.MustNewServer（gRPC）+ 真 PG
#   · RAG favorite-rpc  = java -jar favorite-rpc.jar（真 Spring Boot 进程 + 真 Flyway + 真 PG）
#   · RAG favorite-api  = java -jar favorite-api.jar（真 Spring Boot 进程 + 真 Redis）
#
# 为什么**不需要** fake etcd / 内嵌 redis（2026-10-25 第十三轮已查清）：
#   · etcd  ⇒ go-zero zrpc/config.go:92-114 BuildTarget() 优先级 = Endpoints > Target > Etcd
#              ⇒ 探针 yaml 填 FavoriteRpc.Endpoints 即完全绕开
#   · Redis ⇒ 起一个真容器即可；且**两侧共用同一个** ⇒ 黑名单 key 在同一命名空间
#              ⇒ 「同一个被拉黑的 token」两侧必然查到同一条 ⇒ 顺带验了 key 拼法
#
# 用法：
#   bash tools/api-full-parity-probe/favorite/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/api-full-parity-probe/favorite/run.sh
#
# 退出码：0 = 两侧一致；1 = 有差异；2 = 环境问题 / 真源漂移。
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
# 同域的 rpc 级探针：它的 go/ 树里是**同一批真源文件**（rpc + common + article/user 的 pb）
# ⇒ 本探针直接复用，不在本域内维护第二份副本（避免同一批文件两份漂移）。
RPC_PROBE="${RAG_ROOT}/tools/grpc-contract-probe/favorite"

say() { printf '\033[1;36m[api-full-parity-probe]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }
die() { bad "$*"; exit 2; }

# ── 0. 定位 Go 真源 ────────────────────────────────────────────────────────
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4 5 6; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/favorite/api/favorite.go" ]; then
      (cd "${d}/Sea-RideTheWind_golang" && pwd); return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}
GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 6 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"
[ -f "${GO_REPO}/go.mod" ] || die "Go 真源没有 go.mod：${GO_REPO}/go.mod"
say "Go 真源：${GO_REPO}"

command -v go >/dev/null 2>&1 || die "没装 go toolchain"
command -v mvn >/dev/null 2>&1 || die "没装 mvn"
command -v docker >/dev/null 2>&1 || die "没装 docker（探针需要真 PG + 真 Redis）"
[ -d "${RPC_PROBE}" ] || die "找不到同域 rpc 级探针的复制件树：${RPC_PROBE}"

TMP="$(mktemp -d)"
PG_CONTAINER="fav-api-parity-pg-$$"
REDIS_CONTAINER="fav-api-parity-redis-$$"
PIDS=()
cleanup() {
  for p in "${PIDS[@]:-}"; do kill "$p" >/dev/null 2>&1; done
  for p in "${PIDS[@]:-}"; do wait "$p" >/dev/null 2>&1; done
  docker rm -f "${PG_CONTAINER}" "${REDIS_CONTAINER}" >/dev/null 2>&1 || true
  if [ "${KEEP_TMP:-0}" = "1" ]; then
    say "KEEP_TMP=1 ⇒ 临时目录保留在 ${TMP}"
  else
    rm -rf "${TMP}"
  fi
}
trap cleanup EXIT

# ── 0.1 端口分配（专用端口段 + 双绑定自检 ⇒ 绝不与别的会话撞）─────────────
# ⚠️ 2026-10-25 实测踩出来的坑（**不能**再用 OS 随机端口）：
#   OS 随机端口在**临时端口段 49152–65535**，而这正是 **Docker 随机发布容器端口**的段
#   ⇒ 本机多会话并行时，别的会话的容器随时可能在 `127.0.0.1:P` 上挂一个 PG / Redis；
#   ⇒ macOS/BSD 上 `127.0.0.1:P` 与 `0.0.0.0:P` **能共存**（都带 SO_REUSEADDR）
#      ⇒ 本探针的客户端连 `127.0.0.1:P` 会**命中更具体的那个**（实测那次打到了 PostgreSQL）
#      ⇒ 表现为「连上了但不是自己的服务」⇒ 探针结果**整个不可信**（而不只是报错）。
#   ⇒ 改用**专用端口段 21400–21599**（避开临时端口段），且**双绑定自检**：
#      `0.0.0.0:P` 与 `127.0.0.1:P` **都要能绑** ⇒ 任何第三方持有者都会被排除。
free_port() {
  python3 - "$@" <<'PY'
import socket, sys
FIRST, LAST = 21400, 21599
used = {int(x) for x in sys.argv[1:] if x.strip()}
for p in range(FIRST, LAST + 1):
    if p in used:
        continue
    socks = []
    try:
        for addr in (("0.0.0.0", p), ("127.0.0.1", p)):
            s = socket.socket()
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind(addr)
            s.listen(1)
            socks.append(s)
        print(p)
        break
    except OSError:
        for s in socks:
            s.close()
else:
    sys.exit("端口段 %d-%d 已占满（并行会话太多？）" % (FIRST, LAST))
PY
}
USED_PORTS=()
# ⚠️ macOS 自带 bash 3.2：`set -u` 下展开**空数组**会报 `unbound variable`
#    ⇒ 用 ${arr[@]+"${arr[@]}"} 这个 bash 3.2 安全的写法。
GO_RPC_PORT="$(free_port ${USED_PORTS[@]+"${USED_PORTS[@]}"})"; USED_PORTS+=("${GO_RPC_PORT}")
GO_API_PORT="$(free_port ${USED_PORTS[@]+"${USED_PORTS[@]}"})"; USED_PORTS+=("${GO_API_PORT}")
RAG_RPC_PORT="$(free_port ${USED_PORTS[@]+"${USED_PORTS[@]}"})"; USED_PORTS+=("${RAG_RPC_PORT}")
RAG_API_PORT="$(free_port ${USED_PORTS[@]+"${USED_PORTS[@]}"})"; USED_PORTS+=("${RAG_API_PORT}")
say "端口：Go rpc=${GO_RPC_PORT} api=${GO_API_PORT} · RAG rpc=${RAG_RPC_PORT} api=${RAG_API_PORT}"

wait_port() {  # $1=port $2=name $3=tries
  local port="$1" name="$2" tries="${3:-120}"
  for _ in $(seq 1 "$tries"); do
    if python3 -c "import socket,sys;s=socket.socket();s.settimeout(0.5);sys.exit(0 if s.connect_ex(('127.0.0.1',${port}))==0 else 1)"; then
      return 0
    fi
    sleep 1
  done
  echo "  ✗ ${name} 端口 ${port} 超时未就绪" >&2
  return 1
}

# ── 1. 校验真源复制件的 sha256（防真源漂移后探针还按老副本跑）──────────────
verify_copies() {  # $1=复制件根目录 $2=清单文件 $3=标签
  local root="$1" list="$2" tag="$3"
  [ -f "${list}" ] || die "缺复制件清单：${list}"
  local mismatch=0 rel src dst a b
  while IFS= read -r rel; do
    [ -z "${rel}" ] && continue
    case "${rel}" in \#*) continue ;; esac
    src="${GO_REPO}/${rel}"
    dst="${root}/${rel}"
    [ -f "${src}" ] || die "真源缺文件：${rel}"
    [ -f "${dst}" ] || die "复制件缺文件：${rel}"
    a="$(shasum -a 256 "${src}" | awk '{print $1}')"
    b="$(shasum -a 256 "${dst}" | awk '{print $1}')"
    if [ "${a}" != "${b}" ]; then
      bad "[${tag}] 复制件与真源不一致：${rel}"
      mismatch=1
    fi
  done < "${list}"
  [ "${mismatch}" -eq 0 ] || die "[${tag}] 复制件与真源不一致 ⇒ 请重新复制（不要手改复制件）"
  ok "[${tag}] $(grep -cvE '^\s*(#|$)' "${list}") 个复制件与真源逐字节一致"
}
say "校验真源复制件 sha256"
verify_copies "${HERE}/go" "${HERE}/go/filelist.txt" "api"
verify_copies "${RPC_PROBE}/go" "${RPC_PROBE}/go/filelist.txt" "rpc(复用)"

# ── 2. 组装临时 module（module 名沿用真源的 sea-try-go ⇒ import 路径零改动）──
say "组装临时 module（module=sea-try-go，go.mod/go.sum 整体继承真源）"
mkdir -p "${TMP}/src"
cp "${GO_REPO}/go.mod" "${GO_REPO}/go.sum" "${TMP}/src/"
# api 侧（本探针自己的复制件）
cp -R "${HERE}/go/service" "${TMP}/src/"
# rpc + common + article/user 的 pb（复用同域 rpc 级探针的复制件，避免同一批文件两份漂移）
cp -R "${RPC_PROBE}/go/service/favorite/rpc"    "${TMP}/src/service/favorite/"
cp -R "${RPC_PROBE}/go/service/favorite/common" "${TMP}/src/service/favorite/"
cp -R "${RPC_PROBE}/go/service/common"          "${TMP}/src/service/"
cp -R "${RPC_PROBE}/go/service/article"         "${TMP}/src/service/"
cp -R "${RPC_PROBE}/go/service/user"            "${TMP}/src/service/"
GOZERO_VER="$(awk '/^[[:space:]]*github\.com\/zeromicro\/go-zero v/ {print $2; exit}' "${GO_REPO}/go.mod")"
say "   真源 go.mod：go-zero ${GOZERO_VER}（不写死版本、不 tidy）"

# ── 3. 起真 PG（两个库：godb = Go 侧 / first_db = RAG 侧）+ 真 Redis ─────────
say "起 postgres:16-alpine 容器：${PG_CONTAINER}"
# ⚠️ PG 就绪等待三件套（2026-10-25 第十四轮修 —— 批跑时机器高负载 ⇒ **假红**）：
#   ① 必须探 **TCP**（-h 127.0.0.1）：PG 官方镜像初始化阶段的临时 server 只监听 unix socket
#      ⇒ 走 socket 探活会**假就绪**（然后 CREATE DATABASE 撞上"系统正在启动"而随机失败）。
#   ② 上限 **180s**（不是 60s）：机器高负载时 initdb 本身就要几十秒。
#   ③ 没就绪就**重建容器重来一次**：容器起不来（资源竞争 / 端口映射失败）时重来更稳。
wait_pg_tcp() {
  local tries="$1" i
  for i in $(seq 1 "${tries}"); do
    docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 && return 0
    # 容器自己退出了就别白等满超时（--rm ⇒ 退出会被删掉 ⇒ inspect 失败）
    [ "$(docker inspect -f '{{.State.Running}}' "${PG_CONTAINER}" 2>/dev/null || echo false)" = "true" ] || return 1
    sleep 1
  done
  return 1
}
PG_PORT=""
PG_READY=0
for attempt in 1 2; do
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
  if ! docker run -d --rm --name "${PG_CONTAINER}" \
      -e POSTGRES_PASSWORD=probe -e POSTGRES_USER=postgres \
      -p 0:5432 postgres:16-alpine >/dev/null; then
    say "   PG 容器第 ${attempt} 次启动失败 ⇒ 重来"
    continue
  fi
  PG_PORT="$(docker port "${PG_CONTAINER}" 5432/tcp | head -1 | sed 's/.*://')"
  if [ -z "${PG_PORT}" ]; then
    say "   第 ${attempt} 次拿不到 PG 映射端口 ⇒ 重来"
    continue
  fi
  if wait_pg_tcp 180; then PG_READY=1; break; fi
  say "   PG 第 ${attempt} 次 180s 内没就绪 ⇒ 重建容器重来"
done
[ "${PG_READY}" = "1" ] || die "PG 没就绪（已重建容器重试 1 次）"
for db in godb first_db; do
  db_ok=0
  for _ in $(seq 1 15); do
    if docker exec "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -c "CREATE DATABASE ${db};" >/dev/null 2>&1; then
      db_ok=1; break
    fi
    sleep 2
  done
  [ "${db_ok}" = "1" ] || die "建库 ${db} 失败"
done
ok "PG 就绪（127.0.0.1:${PG_PORT}）· 两个库：godb（Go 侧）/ first_db（RAG 侧）"

say "起 redis:7-alpine 容器（**两侧共用** ⇒ 黑名单 key 在同一命名空间）：${REDIS_CONTAINER}"
docker run -d --rm --name "${REDIS_CONTAINER}" -p 0:6379 redis:7-alpine >/dev/null || die "Redis 容器启动失败"
REDIS_PORT="$(docker port "${REDIS_CONTAINER}" 6379/tcp | head -1 | sed 's/.*://')"
[ -n "${REDIS_PORT}" ] || die "拿不到 Redis 映射端口"
# ⚠️ 同 PG：上限 60s（不是 30s）+ 容器退出了就别白等（redis-cli ping 对已删容器恒失败）
for _ in $(seq 1 60); do
  docker exec "${REDIS_CONTAINER}" redis-cli ping 2>/dev/null | grep -q PONG && break
  [ "$(docker inspect -f '{{.State.Running}}' "${REDIS_CONTAINER}" 2>/dev/null || echo false)" = "true" ] || break
  sleep 1
done
docker exec "${REDIS_CONTAINER}" redis-cli ping 2>/dev/null | grep -q PONG || die "Redis 没就绪"
ok "Redis 就绪（127.0.0.1:${REDIS_PORT}）"

# ── 4. Go 侧：sql-first 建表（真源文档指定的部署顺序）──────────────────────
say "Go 侧建表（sql-first：AutoMigrate(老表) → 真源 001..004）"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    PG_HOST=127.0.0.1 PG_PORT="${PG_PORT}" PG_USER=postgres PG_PASSWORD=probe \
    PG_DB=godb PG_SSLMODE=disable \
    SQL_DIR="${TMP}/src/service/favorite/rpc/internal/model" \
    go run ./service/favorite/rpc/cmd/schemaprep ) > "${TMP}/schemaprep.log" 2>&1 \
  || { bad "Go 侧建表失败"; tail -25 "${TMP}/schemaprep.log"; exit 2; }
ok "Go 侧 schema 就绪"

# ── 5. 生成 JWT（Go 侧签 ⇒ 两侧拿到逐字节相同的 token）────────────────────
say "生成 JWT（Go 侧签发 ⇒ 与真源的 golang-jwt 完全同构）"
JWT_SECRET="ae0536f9-6450-4606-8e13-5a19ed505da0"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    PROBE_JWT_SECRET="${JWT_SECRET}" \
    go run ./service/favorite/api/cmd/token ) > "${TMP}/token.txt" 2> "${TMP}/token.err" \
  || { bad "JWT 生成失败"; cat "${TMP}/token.err"; exit 2; }
# shellcheck disable=SC1090
eval "$(grep -E '^[A-Z0-9_]+=' "${TMP}/token.txt")"
[ -n "${VALID_USER1:-}" ] || { bad "没拿到 token"; cat "${TMP}/token.txt"; exit 2; }
ok "token 就绪（VALID_USER1 / VALID_USER2 / EXPIRED / BADSIG）"

# ── 6. 起 Go 真源 favorite-rpc + favorite-api ──────────────────────────────
cat > "${TMP}/rpc.yaml" <<YAML
Name: favorite.rpc
ListenOn: 127.0.0.1:${GO_RPC_PORT}

Log:
  ServiceName: favorite-rpc
  Mode: console
  Encoding: plain

Postgres:
  Host: "127.0.0.1"
  Port: "${PG_PORT}"
  User: "postgres"
  Password: "probe"
  DBName: "godb"
  Mode: "disable"

# ⚠️ Endpoints 优先于 Etcd（zrpc/config.go:92-114 BuildTarget）⇒ 完全不需要 etcd
# ⚠️ 127.0.0.1:1 是**故意**的死地址：本探针要测「下游不可用」这条契约分支
UserRpc:
  Endpoints:
    - "127.0.0.1:1"

ArticleRpc:
  Endpoints:
    - "127.0.0.1:1"
YAML

cat > "${TMP}/api.yaml" <<YAML
Name: favorite-api
Host: 127.0.0.1
Port: ${GO_API_PORT}

Log:
  ServiceName: favorite-api
  Mode: console
  Encoding: plain

UserAuth:
  AccessSecret: ${JWT_SECRET}
  AccessExpire: 2592000

# 真 Redis 容器（两侧共用 ⇒ 黑名单 key 在同一命名空间）
BizRedis:
  Host: 127.0.0.1:${REDIS_PORT}
  Type: node
  Pass: ""

# ⚠️ Endpoints 优先于 Etcd ⇒ 完全不需要 etcd（与 rpc 侧同一个 favorite-rpc 进程）
FavoriteRpc:
  Endpoints:
    - "127.0.0.1:${GO_RPC_PORT}"
YAML

say "编译并启动 Go 真源 favorite-rpc / favorite-api"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    go build -o "${TMP}/favorite-rpc" ./service/favorite/rpc ) > "${TMP}/gobuild-rpc.log" 2>&1 \
  || { bad "Go rpc 编译失败"; tail -30 "${TMP}/gobuild-rpc.log"; exit 2; }
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    go build -o "${TMP}/favorite-api" ./service/favorite/api ) > "${TMP}/gobuild-api.log" 2>&1 \
  || { bad "Go api 编译失败"; tail -30 "${TMP}/gobuild-api.log"; exit 2; }
"${TMP}/favorite-rpc" -f "${TMP}/rpc.yaml" > "${TMP}/go-rpc.log" 2>&1 &
PIDS+=($!)
wait_port "${GO_RPC_PORT}" "Go favorite-rpc" 90 || { tail -30 "${TMP}/go-rpc.log"; exit 2; }
"${TMP}/favorite-api" -f "${TMP}/api.yaml" > "${TMP}/go-api.log" 2>&1 &
PIDS+=($!)
wait_port "${GO_API_PORT}" "Go favorite-api" 90 || { tail -30 "${TMP}/go-api.log"; exit 2; }
ok "Go 真源栈已就绪（rpc :${GO_RPC_PORT} / api :${GO_API_PORT}）"

# ── 7. 起 RAG 侧（真 Spring Boot 进程：java -jar 打出来的可部署产物）────────
say "构建 RAG 侧 fat jar（-am 必须带 ⇒ 不用过期上游产物；见 PIT-FAV-RPC-013）"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/favorite/favorite-api,service/favorite/favorite-rpc -am \
    package -Dmaven.test.skip=true ) > "${TMP}/mvn-package.log" 2>&1 \
  || { bad "RAG jar 构建失败"; tail -40 "${TMP}/mvn-package.log"; exit 2; }
RAG_RPC_JAR="${RAG_ROOT}/service/favorite/favorite-rpc/target/favorite-rpc-0.0.1-SNAPSHOT.jar"
RAG_API_JAR="${RAG_ROOT}/service/favorite/favorite-api/target/favorite-api-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_RPC_JAR}" ] || die "RAG rpc jar 不存在：${RAG_RPC_JAR}"
[ -f "${RAG_API_JAR}" ] || die "RAG api jar 不存在：${RAG_API_JAR}"

say "启动 RAG favorite-rpc / favorite-api"
# 🔴🔴🔴 **两侧的 Nacos 处置方式不同**（2026-10-25 第十四轮实测踩出来）：
#
#   · **rpc 侧不能关 discovery** ⇒ --spring.cloud.nacos.discovery.enabled=false 会让
#     `FavoriteNacosRegistration`（bootstrap 包里的 @Component）拿不到
#     `NacosServiceManager` ⇒ **构造器硬依赖 ⇒ 上下文起不来**：
#       「No qualifying bean of type 'com.alibaba.cloud.nacos.NacosServiceManager' available」
#     ⇒ 改用「**指向死地址**」：--spring.cloud.nacos.discovery.server-addr=127.0.0.1:1
#       ⇒ 自动配置仍在（NacosServiceManager 存在）⇒ 注册在 ApplicationReadyEvent 时失败
#       ⇒ 类注释明写「注册失败不退出进程」⇒ 只打一条 ERROR 日志，服务照常起
#       （这也与 Go 侧「注册失败不 panic」的刻意偏离一致）
#   · **api 侧可以关 discovery** ⇒ api 没有同款硬依赖组件（走 spring-cloud 的自动注册）
#     ⇒ 但自动注册在 Nacos 不可达时会 failFast ⇒ **整个 api 起不来**
#       （NacosServiceRegistry.register ⇒ Client not connected
#         ⇒ Failed to start bean 'webServerStartStop'）
#     ⇒ api 直接 --spring.cloud.nacos.discovery.enabled=false（gRPC 走 static:// 直连）
#
# ⚠️ user-rpc / article-rpc 指向**死地址** ⇒ 走「下游不可用」这条契约分支（与 Go 侧同款）。
# ⚠️ --spring.main.web-application-type=none：**必须**显式写。
#    favorite-rpc 的 pom 只有 spring-boot-web-server + jakarta.servlet-api，
#    **没有** spring-boot-starter-web（无内嵌 Tomcat）⇒ Boot 默认按 SERVLET 起
#    ⇒ 找不到 ServletWebServerFactory ⇒ **进程起不来**：
#      「Web application could not be started as there was no
#       org.springframework.boot.web.server.servlet.ServletWebServerFactory bean defined」
#    ⚠️ 这个坑一直被 `@SpringBootTest(webEnvironment = NONE)` 掩盖着
#       （测试上下文强制 NONE ⇒ 永远走不到这条分支）
#       ⇒ 与 Go 侧「纯 gRPC、零 HTTP 端口」的形态对齐（见 DEV-FAV-RPC-004）。
env POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${PG_PORT}" POSTGRES_USER=postgres \
    POSTGRES_PASSWORD=probe POSTGRES_DB=first_db \
  java -jar "${RAG_RPC_JAR}" \
    --spring.main.web-application-type=none \
    --grpc.server.port="${RAG_RPC_PORT}" \
    --spring.cloud.nacos.discovery.server-addr=127.0.0.1:1 \
    --management.otlp.tracing.export.enabled=false \
    --grpc.client.user-rpc.address=static://127.0.0.1:1 \
    --grpc.client.article-rpc.address=static://127.0.0.1:1 \
    > "${TMP}/rag-rpc.log" 2>&1 &
PIDS+=($!)
wait_port "${RAG_RPC_PORT}" "RAG favorite-rpc" 180 || { tail -40 "${TMP}/rag-rpc.log"; exit 2; }
ok "RAG favorite-rpc 已就绪（:${RAG_RPC_PORT}）· 真 Flyway 已在 first_db 建表"

env REDIS_ADDR=127.0.0.1 REDIS_PORT="${REDIS_PORT}" REDIS_PASSWORD= \
  java -jar "${RAG_API_JAR}" \
    --server.port="${RAG_API_PORT}" \
    --spring.cloud.nacos.discovery.enabled=false \
    --management.otlp.tracing.export.enabled=false \
    --grpc.client.favorite-rpc.address="static://127.0.0.1:${RAG_RPC_PORT}" \
    --grpc.client.favorite-rpc.negotiation-type=plaintext \
    --favorite-api.user-auth.access-secret="${JWT_SECRET}" \
    > "${TMP}/rag-api.log" 2>&1 &
PIDS+=($!)
wait_port "${RAG_API_PORT}" "RAG favorite-api" 180 || { tail -40 "${TMP}/rag-api.log"; exit 2; }
ok "RAG favorite-api 已就绪（:${RAG_API_PORT}）"

# ── 8. 灌种子（两侧同一份 ⇒ 读到同一批行）────────────────────────────────
say "灌种子到两侧的库"
for db in godb first_db; do
  docker exec -i "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -d "${db}" -v ON_ERROR_STOP=1 \
    -f - < "${RPC_PROBE}/seed.sql" > "${TMP}/seed-${db}.log" 2>&1 \
    || { bad "seed ${db} 失败"; cat "${TMP}/seed-${db}.log"; exit 2; }
done
ok "两侧种子就绪"

# ── 9. 拉黑一个 token（黑名单对照组）──────────────────────────────────────
# ⚠️ 必须有「被拉黑的 token」对照组，否则 a06 的 401 无意义：
#    「两侧都返回 401」有两种可能 ——（a）真的查到了黑名单；（b）根本没在查。
#    ⇒ 对照组 = 同一个 token 在黑名单里 ⇒ 两侧都 401；正常 token ⇒ 两侧都放行。
say "把一个 token 写进共用 Redis 的黑名单（对照组）"
BLACKLIST_TOKEN="$(cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    PROBE_JWT_SECRET="${JWT_SECRET}" go run ./service/favorite/api/cmd/token | grep '^VALID_USER1=' | cut -d= -f2-)"
docker exec "${REDIS_CONTAINER}" redis-cli SET "user:jwt_blacklist:${BLACKLIST_TOKEN}" 1 >/dev/null
BL_COUNT="$(docker exec "${REDIS_CONTAINER}" redis-cli exists "user:jwt_blacklist:${BLACKLIST_TOKEN}" | tr -d '\r')"
[ "${BL_COUNT}" = "1" ] || die "黑名单 key 没写进去（exists=${BL_COUNT}）⇒ 对照组无意义"
ok "黑名单对照 token 就绪（Redis exists=1）"

# ── 10. 跑 Java 侧探针（同一个 HTTP 客户端双打两侧）───────────────────────
say "跑 Java 侧探针（同一个 HttpClient 打两侧）"
# ⚠️ 这里用 export + 子 shell，**不能**写成 `VAR=x ( … )` ——
#    `env`/变量前缀只能直接修饰一条**简单命令**，而 `( … )` 是复合命令 ⇒ bash 直接语法报错。
export PROBE_GO_API="http://127.0.0.1:${GO_API_PORT}/favorite/v1"
export PROBE_RAG_API="http://127.0.0.1:${RAG_API_PORT}/favorite/v1"
export PROBE_TOKEN_VALID_USER1="${VALID_USER1}"
export PROBE_TOKEN_VALID_USER2="${VALID_USER2}"
export PROBE_TOKEN_EXPIRED="${EXPIRED}"
export PROBE_TOKEN_BADSIG="${BADSIG}"
export PROBE_TOKEN_BLACKLISTED="${BLACKLIST_TOKEN}"
export PROBE_OUT_GO="${TMP}/go.txt"
export PROBE_OUT_RAG="${TMP}/rag.txt"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/favorite/favorite-api -am test \
    -Dtest=FavoriteApiFullParityTest -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false ) > "${TMP}/mvn-test.log" 2>&1
MVN_RC=$?
if [ "${MVN_RC}" -ne 0 ]; then
  bad "Java 侧 FavoriteApiFullParityTest 失败（exit ${MVN_RC}）"
  grep -E 'ERROR|FAIL|expected|AssertionError|Exception' "${TMP}/mvn-test.log" | head -40
  echo "---- mvn-test.log 尾部 ----"
  tail -60 "${TMP}/mvn-test.log"
  exit 1
fi
[ -s "${TMP}/go.txt" ] || die "Java 侧没有产出 Go 侧结果文件"
[ -s "${TMP}/rag.txt" ] || die "Java 侧没有产出 RAG 侧结果文件"
ok "两侧各产出 $(wc -l < "${TMP}/go.txt" | tr -d ' ') 个 case"

# ── 11. 逐条比对 ───────────────────────────────────────────────────────────
say "逐条比对（Go 侧 vs 冻结值 vs RAG 侧）"
python3 - "${HERE}/expected.txt" "${TMP}/go.txt" "${TMP}/rag.txt" <<'PY'
import sys, os

exp_path, go_path, rag_path = sys.argv[1], sys.argv[2], sys.argv[3]


def load(p):
    out = {}
    if not os.path.exists(p):
        return out
    for line in open(p, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t", 1)
        out[parts[0]] = parts[1] if len(parts) > 1 else ""
    return out


def fields(v):
    """把渲染串拆成字段：status / ct / body / biz / hkeys。"""
    out = {}
    for part in v.split("|"):
        if "=" in part:
            k, val = part.split("=", 1)
            out[k] = val
    return out


go = load(go_path)
rag = load(rag_path)

if not os.path.exists(exp_path):
    print("  ⚠️ expected.txt 尚不存在 ⇒ 下面是首次运行的逐条实测（请人工分类后冻结）")
    for cid in go:
        g, r = go.get(cid, "<缺>"), rag.get(cid, "<缺>")
        print(f"  [{'SAME' if g == r else 'DIFF'}] {cid}")
        print(f"      Go : {g}")
        print(f"      RAG: {r}")
    sys.exit(0)

kinds, frozen = {}, {}
for line in open(exp_path, encoding="utf-8"):
    line = line.rstrip("\n")
    if not line.strip() or line.lstrip().startswith("#"):
        continue
    cols = line.split("\t", 2)
    if len(cols) < 3:
        print(f"❌ expected.txt 行格式错误：{line}")
        sys.exit(1)
    kinds[cols[0]] = cols[1]
    frozen[cols[0]] = cols[2]

problems = []
same = same_code = same_body = same_status = obs = skip = 0
for cid, kind in kinds.items():
    g, r = go.get(cid), rag.get(cid)
    if g is None or r is None:
        problems.append(f"  · {cid} —— 有一侧没跑这个 case")
        continue
    if kind == "SKIP":
        skip += 1
        continue
    if frozen[cid] != g:
        problems.append(f"  · {cid} —— Go 侧与冻结值不一致 ⇒ **Go 真源的 api 契约变了**\n"
                       f"      冻结: {frozen[cid]}\n"
                       f"      实测: {g}")
        continue
    fg, fr = fields(g), fields(r)
    if fg.get("status") != fr.get("status"):
        problems.append(f"  · {cid} —— HTTP 状态码不一致\n"
                       f"      Go : {g}\n"
                       f"      RAG: {r}")
        continue
    if kind == "SAME":
        same += 1
        if g != r:
            problems.append(f"  · {cid} —— 两侧不一致\n      Go : {g}\n      RAG: {r}")
        continue
    if kind == "SAME_CODE":
        same_code += 1
        if fg.get("biz") != fr.get("biz"):
            problems.append(f"  · {cid} —— 业务码不一致\n"
                           f"      Go : {g}\n      RAG: {r}")
        continue
    if kind == "SAME_BODY":
        same_body += 1
        if fg.get("body") != fr.get("body"):
            problems.append(f"  · {cid} —— body 不一致（Content-Type 允许不同）\n"
                           f"      Go : {g}\n      RAG: {r}")
        continue
    if kind == "SAME_STATUS":
        same_status += 1
        continue
    if kind == "OBS":
        obs += 1
        if g == r:
            problems.append(f"  · {cid} —— OBS 段期望两侧不同，但变成一样了 ⇒ 请重评台账")
        continue
    problems.append(f"  · {cid} —— 未知的档位「{kind}」")

print(f"  SAME {same} 条 / SAME_CODE {same_code} 条 / SAME_BODY {same_body} 条 / "
      f"SAME_STATUS {same_status} 条 / OBS {obs} 条 / SKIP {skip} 条（共 {len(kinds)} 个 case）")
if problems:
    print("❌ 不一致：")
    print("\n".join(problems))
    sys.exit(1)
print("✅ api 整域两侧一致"
      "（SAME 全等；SAME_CODE 业务码等；SAME_BODY body 等；SAME_STATUS 仅状态码等；OBS 仍不同）")
PY
RC=$?
[ "${RC}" -eq 0 ] || exit 1

echo
ok "api 整域双侧活体对拍通过"
echo "   证据边界见 tools/api-full-parity-probe/favorite/README.md"
exit 0
