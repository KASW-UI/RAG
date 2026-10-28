#!/usr/bin/env bash
# manage.sh - Sea-RideTheWind_java user microservice lifecycle wrapper
# Bash 3.2 兼容（macOS 默认 bash 3.2）
#
# 子命令：
#   up        启动整个栈（postgres + redis + nacos + jaeger + prometheus + user-rpc + user-api）
#   down      停止并移除容器
#   restart   重建镜像并重启 user-rpc + user-api（infra 不重建）
#   logs      查看日志（默认全部；可指定 service 名：logs user-rpc）
#   status    列出容器状态
#   build     仅构建镜像（不启动）
#   clean     清理所有卷（包括 postgres 数据）

set -e

COMPOSE_FILE="docker-compose.yaml"
INFRA_SVCS="postgres redis nacos jaeger prometheus"
APP_SVCS="user-rpc user-api"
ALL_SVCS="$INFRA_SVCS $APP_SVCS"

log() { printf '\033[0;34m[manage]\033[0m %s\n' "$*"; }
err() { printf '\033[0;31m[manage]\033[0m %s\n' "$*" >&2; }
ok()  { printf '\033[0;32m[manage]\033[0m %s\n' "$*"; }

ensure_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        err "docker not found in PATH"
        exit 1
    fi
    if ! docker info >/dev/null 2>&1; then
        err "docker daemon not reachable"
        exit 1
    fi
}

ensure_env() {
    if [ ! -f .env ]; then
        err ".env not found at repo root"
        err "  至少需要 POSTGRES_PASSWORD / USER_JWT_SECRET 两个键"
        exit 1
    fi
    # 关键 env 必填校验
    for key in POSTGRES_PASSWORD USER_JWT_SECRET; do
        if ! grep -q "^${key}=" .env || grep "^${key}=" .env | grep -q "=$"; then
            err "  ${key} missing or empty in .env"
            exit 1
        fi
    done
}

# 在 host 上用 mvn 给单个 service 打包（jar 进 target/，Dockerfile 后续 COPY 进去）
# 必须 host 上有 JDK 21 + Maven 3.9+（Docker Desktop 走 Linux VM 拉依赖太慢）
mvn_package_one() {
    local svc="$1"
    log "mvn package  -> service/users/user/${svc} -am"
    if ! mvn -B -ntp -pl "service/users/user/${svc}" -am clean package -DskipTests; then
        err "mvn package failed for ${svc}"
        exit 1
    fi
    if [ ! -f "service/users/user/${svc}/target/${svc}-0.0.1-SNAPSHOT.jar" ]; then
        err "expected jar not found: service/users/user/${svc}/target/${svc}-0.0.1-SNAPSHOT.jar"
        exit 1
    fi
    ok "mvn package ok: ${svc}"
}

# 跑 mvn 给所有 app service 打包（user-rpc + user-api）
mvn_package_all() {
    for svc in $APP_SVCS; do
        mvn_package_one "$svc"
    done
}

cmd_up() {
    ensure_docker
    ensure_env
    log "Building user-rpc + user-api jars (host mvn, ~20s for incremental builds)..."
    mvn_package_all
    log "Building docker images for user-rpc + user-api..."
    docker compose -f "$COMPOSE_FILE" build $APP_SVCS
    log "Starting stack: $ALL_SVCS"
    docker compose -f "$COMPOSE_FILE" up -d $ALL_SVCS
    log "Waiting for postgres + nacos health..."
    sleep 8
    cmd_status
    echo
    ok "user microservice stack is up"
    echo "  user-api HTTP    : http://localhost:49900/user/v1/login"
    echo "  user-rpc gRPC    : localhost:49210 (via Nacos discovery://user-rpc)"
    echo "  prometheus       : http://localhost:49090"
    echo "  nacos console    : http://localhost:49848/nacos"
    echo "  jaeger UI        : http://localhost:49686"
    echo "  postgres         : localhost:49432 (user=${POSTGRES_USER:-admin} db=${POSTGRES_DB:-first_db})"
    echo "  redis            : localhost:49380"
}

cmd_down() {
    ensure_docker
    log "Stopping all containers..."
    docker compose -f "$COMPOSE_FILE" down
    ok "stack stopped"
}

cmd_restart() {
    ensure_docker
    ensure_env
    log "Repackaging jars + rebuilding images for user-rpc + user-api..."
    mvn_package_all
    docker compose -f "$COMPOSE_FILE" build $APP_SVCS
    docker compose -f "$COMPOSE_FILE" up -d --no-deps $APP_SVCS
    ok "app services restarted (infra unchanged)"
}

cmd_logs() {
    ensure_docker
    shift 2>/dev/null || true   # skip the subcommand name
    if [ $# -eq 0 ]; then
        docker compose -f "$COMPOSE_FILE" logs -f --tail=100
    else
        docker compose -f "$COMPOSE_FILE" logs -f --tail=100 "$@"
    fi
}

cmd_status() {
    ensure_docker
    docker compose -f "$COMPOSE_FILE" ps
}

cmd_build() {
    ensure_docker
    ensure_env
    log "Building jars (host mvn) + docker images for user-rpc + user-api..."
    mvn_package_all
    docker compose -f "$COMPOSE_FILE" build $APP_SVCS
    ok "images built"
}

cmd_clean() {
    ensure_docker
    log "Removing all containers + volumes (POSTGRES DATA WILL BE LOST)..."
    docker compose -f "$COMPOSE_FILE" down -v
    ok "cleaned"
}

cmd_help() {
    sed -n '2,11p' "$0"
    exit 0
}

case "${1:-help}" in
    up)        shift; cmd_up "$@" ;;
    down)      shift; cmd_down "$@" ;;
    restart)   shift; cmd_restart "$@" ;;
    logs)      shift; cmd_logs "$@" ;;
    status|ps) shift; cmd_status "$@" ;;
    build)     shift; cmd_build "$@" ;;
    clean)     shift; cmd_clean "$@" ;;
    help|-h|--help) cmd_help ;;
    *) err "unknown subcommand: $1"; cmd_help; exit 1 ;;
esac
