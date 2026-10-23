#!/usr/bin/env bash
# 双端对拍编排脚本 —— 起基建 → 起 Go 栈 → 起 RAG 栈 → 跑对拍 → 收摊。
#
# 三条纪律（改脚本前先读）：
#   1. **绝不修改冻结的 Go 仓库**。Go 的运行时配置全部来自 tools/difftest/conf/go/*.yaml，
#      那是真源的**副本**；用 `-f` 指过去，真源一个字节都不动。
#   2. **Go 不写任何文件进仓库**。真源 yaml 的 `Log.Path: ../../../../log/...` 是相对 etc/ 的，
#      会直接落到冻结仓库里，所以副本已改成 `Mode: console`。
#   3. **两侧端口都必须独占，且不能碰本机既有服务**。实测踩过的三个坑：
#        · RAG 默认 admin-rpc 8081 / admin-api 8889 与 Go 相同 → 直接撞
#        · RAG 默认 user-rpc 49210 / user-api 49900 被本机 docker-compose 的
#          Sea-user-rpc / Sea-user-api 容器占着
#        · 8081 / 8889 上还跑着 **Sea-RideTheWind_java** 的进程（AGENTS.md §1
#          明确排除在 RAG 范围外的另一个实现）—— **绝不能去 kill 它**
#      所以两侧统一搬到 1xxxx / 5xxxx 两个私有段：
#        Go : user-rpc 19004  admin-rpc 18081  user-api 18888  admin-api 18889
#             message-rpc 18082                      (2026-10-22 纳入)
#        RAG: user-rpc 59210  admin-rpc 58081  user-api 59900  admin-api 58889
#             message-rpc 58210                      (2026-10-22 纳入)
#      RAG 侧通过环境变量覆盖：GRPC_SERVER_PORT / SERVER_PORT（Spring relaxed binding）。
#
# 【message 域（2026-10-22 纳入，AGENTS.md §6.6）】
#   message-rpc 是**纯 gRPC**、没有 HTTP 面，所以它进不了 diff.py 的 HTTP 对拍，
#   只能靠 grpc-message-dual-port-runner.java 单 stub 双端口打（见
#   exp-difftest-grpc-errors-message.sh）。这里只负责把两端的 message-rpc 拉起来。
#   端口段同样避开本机：Go 18082 / RAG 58210。
#
# 用法：
#   ./run.sh              # 完整跑（含构建）
#   ./run.sh --no-build   # 复用已有 jar 与 Go 二进制
#   ./run.sh --keep       # 跑完不收摊，便于手工 curl
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
RUN_DIR="${RUN_DIR:-/tmp/difftest}"
LOGS="$RUN_DIR/logs"

BUILD=1
KEEP=0
SERVE_ONLY=0
for a in "$@"; do
  case "$a" in
    --no-build) BUILD=0 ;;
    --keep)     KEEP=1 ;;
    # 起完两套栈就停下、不跑对拍、进程留在前台。
    # 专供 exp-*.sh 这类需要「栈已经在跑」的取证脚本复用编排逻辑
    # ——它们自己造输入、自己观测，不该各自复制一遍起栈代码。
    --serve-only) SERVE_ONLY=1 ;;
    *) echo "未知参数：$a（可选：--no-build / --keep / --serve-only）" >&2; exit 2 ;;
  esac
done

PG_PORT=55432
REDIS_PORT=56379
# ⚠️ **POSTGRES_ADDR 与 POSTGRES_HOST 都要导出**（2026-10-24 实测修）
#
#   全仓的 DSN 写法有**两套约定**，本脚本原先只导出了 `POSTGRES_ADDR`：
#     · POSTGRES_ADDR ：admin-rpc、comment-rpc
#     · POSTGRES_HOST ：message-rpc / article-rpc / like-rpc / task-rpc /
#                       follow-rpc / favorite-rpc
#   ⇒ 缺 `POSTGRES_HOST` 的模块会**回落到 yml 里的默认值 `postgres`**
#     （那是 docker-compose 里的服务名，宿主机上解析不了）
#     ⇒ 表现为：`UnknownHostException: postgres` → Flyway 连不上库 →
#       `flywayInitializer` 失败 → 整个上下文起不来。
#   实测（2026-10-24）：message-rpc 就是这样起不来的，
#   且 `run.sh` 的 `assert_started` 会因此**提前退出**，把整个对拍编排打断。
#   ⇒ 两个都导出，值相同 ⇒ 对两套约定都成立。
export POSTGRES_ADDR=127.0.0.1 POSTGRES_HOST=127.0.0.1 \
       POSTGRES_PORT=$PG_PORT \
       POSTGRES_USER=admin POSTGRES_PASSWORD=difftest \
       REDIS_ADDR=127.0.0.1 REDIS_PORT=$REDIS_PORT \
       NACOS_ADDR=127.0.0.1 NACOS_PORT=1

# 关掉 Nacos 自动注册这件事只发生在 [4/5] 里的两个 api 启动命令上（用 env 前缀传）。
# NACOS_PORT=1 是故意的：对拍环境没有 Nacos，让 rpc 侧的自定义 NacosRegistration
# 立刻失败、而不是卡着等重连。
#
# ⚠️ rpc 模块必须保持注册逻辑可装配：
#   user-rpc / admin-rpc 里的 NacosRegistration 是自定义 @Component，
#   它构造注入 NacosServiceManager bean；把 spring.cloud.nacos.discovery.enabled 关掉
#   会让这个 bean 装配失败、上下文起不来。它们的注册失败被 Java 侧 try/catch 吞掉
#   （只打 ERROR 日志、不 rethrow），进程照常提供服务 —— 这是实测行为，参见
#   service/users/user/user-rpc/.../bootstrap/NacosRegistration.java 的 register()。
#
# ⚠️ bash 陷阱（已踩过一次，日志里只留了一行 "command not found: ..."）：
#   不要写成  VAR=x $SOME_VAR java -jar ...。
#   bash 在**解析期**就决定命令的赋值前缀边界，`$SOME_VAR` 展开出来的
#   `NAME=value` 语法上不是赋值词，于是被当成命令名去执行，直接 command not found。
#   正确写法是用 `env NAME=value java -jar ...`（见 [4/5]）。

PIDS=()
cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    echo "[run.sh] --keep：保留容器与进程。手动收摊："
    echo "  docker compose -f $HERE/docker-compose.yml down -v"
    echo "  kill ${PIDS[*]:-}"
    return
  fi
  echo "[run.sh] 收摊…"
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  docker compose -f "$HERE/docker-compose.yml" down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

# 探针必须拒绝 404：`curl` 拿到任何 HTTP 响应都 exit 0，上一版把
# 「打到别人服务上的 404」误判成「本服务就绪」，把整个对拍结果变成了噪声。
wait_http() {  # url, name, tries
  local url=$1 name=$2 tries=${3:-90} code=""
  for _ in $(seq 1 "$tries"); do
    # curl 失败时它自己会输出 000；绝不能再 `|| echo 000`，否则拼成 000000
    # 被 `!= 000` 判成「就绪」—— 上一版正是这么把「连不上」骗过去的。
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 2 "$url" 2>/dev/null) || code=""
    if [[ -n "$code" && "$code" != "000" && "$code" != "404" ]]; then
      echo "  OK   ${name} 就绪（HTTP ${code}）"; return 0
    fi
    sleep 1
  done
  echo "  FAIL ${name} 超时（${url}，最后状态码 ${code:-none}）"; return 1
}

# 启动后立即扫日志：Spring 启动失败会打 "APPLICATION FAILED TO START"，
# 而进程仍然退出 0，不扫就会静默地把「没起来」当成「起来了」。
assert_started() {  # logfile, name
  local name=$2
  if grep -qE 'APPLICATION FAILED TO START|was already in use|BeanCreationException' "$1" 2>/dev/null; then
    echo "  FAIL ${name} 启动失败："; grep -E 'Description:|Port .* was already in use|APPLICATION FAILED' -A 2 "$1" | head -8 | sed 's/^/      /'
    return 1
  fi
}

mkdir -p "$RUN_DIR" "$LOGS"

echo "==> [1/5] 基建（postgres + redis；刻意不起 etcd / Nacos / jaeger）"
docker compose -f "$HERE/docker-compose.yml" down -v >/dev/null 2>&1 || true
docker compose -f "$HERE/docker-compose.yml" up -d
until docker exec difftest-postgres pg_isready -U admin -d go_db >/dev/null 2>&1; do sleep 1; done
until docker exec difftest-redis redis-cli ping >/dev/null 2>&1; do sleep 1; done
# initdb.sql 只在数据目录为空时执行；如果上一轮残留了旧卷，这三个库必须都在，
# 否则 RAG 会连到一个不存在/表结构不对的库上，报错还长得像业务 bug。
for db in go_db rag_db; do
  until docker exec difftest-postgres psql -U admin -lqt 2>/dev/null | cut -d'|' -f1 | grep -qw "$db"; do sleep 1; done
done
echo "  ✓ postgres(go_db,rag_db) + redis"

echo "==> [2/5] 构建 Go 栈（CGO_ENABLED=0，产物落 /tmp，不进任何仓库）"
if [[ $BUILD -eq 1 ]]; then
  mkdir -p "$RUN_DIR/bin"
  ( cd "$GO_REPO" \
    && CGO_ENABLED=0 go build -o "$RUN_DIR/bin/go-user-rpc"  ./service/user/user/rpc \
    && CGO_ENABLED=0 go build -o "$RUN_DIR/bin/go-user-api"  ./service/user/user/api \
    && CGO_ENABLED=0 go build -o "$RUN_DIR/bin/go-admin-rpc" ./service/user/admin/rpc \
    && CGO_ENABLED=0 go build -o "$RUN_DIR/bin/go-admin-api" ./service/user/admin/api \
    && CGO_ENABLED=0 go build -o "$RUN_DIR/bin/go-message-rpc" ./service/message/rpc )
fi

if [[ $BUILD -eq 1 ]]; then
  echo "==> [2b/5] 构建 RAG 栈"
  ( cd "$ROOT" && mvn -q -o -DskipTests package )
  # jar 统一收进 $RUN_DIR/bin，让下面的存在性检查和启动逻辑与 Go 二进制同构
  for m in user/user-rpc:user-rpc user/user-api:user-api \
           admin/admin-rpc:admin-rpc admin/admin-api:admin-api \
           message/message-rpc:message-rpc; do
    src="$ROOT/service/users/${m%%:*}/target/${m##*:}-0.0.1-SNAPSHOT.jar"
    # message 域在 service/ 下面而不是 service/users/ 下面，单独算一次路径
    [[ "$m" == message/* ]] && src="$ROOT/service/${m%%:*}/target/${m##*:}-0.0.1-SNAPSHOT.jar"
    [[ -f "$src" ]] || { echo "缺少 jar：$src"; exit 1; }
    cp "$src" "$RUN_DIR/bin/${m##*:}"
  done
fi

for j in go-user-rpc go-user-api go-admin-rpc go-admin-api go-message-rpc \
         user-rpc user-api admin-rpc admin-api message-rpc; do
  [[ -x "$RUN_DIR/bin/$j" || -f "$RUN_DIR/bin/$j" ]] \
    || { echo "缺少产物 $j，请去掉 --no-build"; exit 1; }
done

echo "==> [3/5] 启动 Go 栈（配置来自 conf/go/*.yaml 副本，Etcd 已移除、Endpoints 直连）"
POSTGRES_DB=go_db "$RUN_DIR/bin/go-user-rpc"  -f "$HERE/conf/go/user-rpc.yaml"  >"$LOGS/go-user-rpc.log" 2>&1 & PIDS+=($!)
POSTGRES_DB=go_db "$RUN_DIR/bin/go-admin-rpc" -f "$HERE/conf/go/admin-rpc.yaml" >"$LOGS/go-admin-rpc.log" 2>&1 & PIDS+=($!)
sleep 3
# message-rpc 必须等 user-rpc / admin-rpc 起来之后再起：
# Go 侧 NewServiceContext 里 `zrpc.MustNewClient(c.UserRpc)` 默认 NonBlock=false
# ⇒ 走 grpc.WithBlock()，下游没监听时会一直阻塞拨号。副本已把 Etcd 换成
# Endpoints 直连（conf/go/message-rpc.yaml），但直连同样要等对端在听。
POSTGRES_DB=go_db "$RUN_DIR/bin/go-message-rpc" -f "$HERE/conf/go/message-rpc.yaml" >"$LOGS/go-message-rpc.log" 2>&1 & PIDS+=($!)
sleep 2
assert_started "$LOGS/go-message-rpc.log" "go message-rpc" || true
POSTGRES_DB=go_db "$RUN_DIR/bin/go-user-api"  -f "$HERE/conf/go/user-api.yaml"  >"$LOGS/go-user-api.log" 2>&1 & PIDS+=($!)
POSTGRES_DB=go_db "$RUN_DIR/bin/go-admin-api" -f "$HERE/conf/go/admin-api.yaml" >"$LOGS/go-admin-api.log" 2>&1 & PIDS+=($!)
assert_started "$LOGS/go-user-api.log"  "go user-api"  || true
assert_started "$LOGS/go-admin-api.log" "go admin-api" || true
wait_http "http://127.0.0.1:18888/usercenter/v1/user/login" "go user-api" 60
wait_http "http://127.0.0.1:18889/admincenter/v1/admin/login" "go admin-api" 60

echo "==> [4/5] 启动 RAG 栈（独占端口 59210/58081/59900/58889；gRPC 走 static:// 绕开 Nacos）"
# GRPC_SERVER_PORT / SERVER_PORT 用 Spring relaxed binding 覆盖 yml 里的默认值，
# 避开本机 Sea-* 容器占着的 49210/49900，以及 Sea-RideTheWind_java 占着的 8081/8889。
#
# 两侧都**不用 Flyway**（2026-10-21，DRIFT-NEW-015）：
#   flyway_schema_history 是 schema 级账本，而两个模块的迁移版本号撞车、内容不同，
#   两个进程又必须连同一个库（admin-rpc 也读写 users_user）⇒ 后启动的必然出事。
#   现在两边都在 @PostConstruct 里把 users-common 的 4 份 db/*.sql 幂等重放一遍，
#   全部 CREATE ... IF NOT EXISTS，无账本、无版本号，**谁先启动都行**。
#   ⇒ 这里**不需要**任何手工 DDL、不需要关 Flyway、不需要 sleep 等迁移。
#
# POSTGRES_DB 两侧同为 rag_db：admin-rpc 必须能看见 users_user，
# 否则 getuser/banuser/unbanuser/resetuserpassword/updateuser/delete 全返回「用户不存在」。
#
# 【B-1 静态注入开关】EXP_RAG_QUERY_TIMEOUT=2 时给 RAG 的 rpc 侧注入
# mybatis-plus.configuration.default-statement-timeout（单位：秒）。
#
# ⚠️ **2026-10-21 起，本开关已不是生产方案**，生产方案是代码内的
#    `DeadlineQueryTimeoutInjector`（按 DeadlineState.remainingMillis() 逐请求注入，
#    见 issue #71187874）。保留本开关只作**对照/兜底**：
#    拦截器在 `StatementHandler.prepare` 返回之后调 `setQueryTimeout`，
#    **会覆盖** MyBatis 在 prepare 里按 configuration 设的值
#    ⇒ 正常情况下这个 env 开关在 RAG 侧不产生任何效果。
#    只有当拦截器没被注册时，它才兜住底 —— 这也是判断「拦截器到底生效没有」的
#    一个副证：两组都开，若结果与基线无差别，说明拦截器在起作用。
#
# ⚠️ 前缀必须是 mybatis-plus，不能写 mybatis（§3.3「不报错的隐式失效」）：
#   MyBatis-Plus 的 @ConfigurationProperties 前缀是 "mybatis-plus"
#   （javap: MybatisPlusProperties，字段 configuration 的类型是
#     MybatisPlusProperties$CoreConfiguration，继承 MyBatis Configuration，
#     其上有 defaultStatementTimeout: Integer 的 getter+setter，绑定链路通）。
#   而**没有任何** @ConfigurationProperties(prefix="mybatis")。
#   Spring Boot 对未知的命令行属性是【静默接受】的 —— 不打 warn、不报错、
#   启动照常成功。第一版实验就栽在这：写成 --mybatis.configuration.*，
#   注入点日志照打、jar 照起、PG 查询照旧存活 4.5s，**效果恒为零**。
#   ⇒ 判断「开关有没有真生效」不能看启动日志，只能看运行时证据
#     （本实验就是看 PG 侧有没有 57014 query_canceled）。
QTO_ARG=()
if [[ -n "${EXP_RAG_QUERY_TIMEOUT:-}" ]]; then
  QTO_ARG=(--mybatis-plus.configuration.default-statement-timeout="$EXP_RAG_QUERY_TIMEOUT")
  echo "  · B-1 对照：rpc 侧静态注入 default-statement-timeout=${EXP_RAG_QUERY_TIMEOUT}s"
  echo "    （生产方案是代码内拦截器逐请求注入，本开关只作兜底/对照）"
fi

POSTGRES_DB=rag_db GRPC_SERVER_PORT=59210 \
  java -jar "$RUN_DIR/bin/user-rpc"  ${QTO_ARG[@]+"${QTO_ARG[@]}"} >"$LOGS/rag-user-rpc.log" 2>&1 & PIDS+=($!)
POSTGRES_DB=rag_db GRPC_SERVER_PORT=58081 \
  java -jar "$RUN_DIR/bin/admin-rpc" ${QTO_ARG[@]+"${QTO_ARG[@]}"} >"$LOGS/rag-admin-rpc.log" 2>&1 & PIDS+=($!)
sleep 20
#
# gRPC 下游地址用 `--` 命令行参数而不是环境变量：
#   grpc.client.user-rpc.address 的 map key 含连字符，环境变量形态
#   （GRPC_CLIENT_USER_RPC_ADDRESS / GRPC_CLIENT_USER-RPC_ADDRESS）在 Spring Boot 的
#   relaxed binding 下归属不确定；命令行参数直接按属性名绑定，没有歧义。
#   上一版用环境变量写的，服务确实起来了但无从判断地址到底有没有生效。
#
# register-enabled=false 的作用：关掉 spring-cloud 的 NacosAutoServiceRegistration。
# 不关的话，两个 api 进程都会死在
#   Failed to start bean 'webServerStartStop'
#   Caused by: ErrCode:-401, ErrMsg:Client not connected, current status:STARTING
# ——注册失败在 WebServerInitializedEvent 里抛出、直接取消上下文初始化。
# 这与 Go 侧「Endpoints 短路免 etcd」是同一个思路的两面：对拍要的是业务语义，
# 不是服务发现本身，两侧都摘掉发现层才是对称的。
# ⚠️ negotiation-type=plaintext 是**必要的 harness 固定值，不是在掩盖问题**：
#   devh grpc-client-spring-boot-starter 3.1.0.RELEASE 的
#   grpc.client.GLOBAL.negotiation-type 默认值是 TLS
#   （证据：jar 内 META-INF/spring-configuration-metadata.json 的 defaultValue）。
#   而 RAG 的 user-rpc / admin-rpc 都是 plain gRPC，所以客户端必须显式 plaintext。
#   admin-api/application.yml 里已经有这一行（注释标 DRIFT-NEW-008），
#   但 **user-api/application.yml 的 grpc.client.user-rpc 漏了**，
#   于是 user-api 发的是 TLS ClientHello，user-rpc 直接回
#     Http2Exception: HTTP/2 client preface string missing or corrupt.
#     Hex dump: 16030301...        ← 0x16=TLS handshake, 0x03 0x03=SSLv3/TLS record
#   表现为所有经过 user-rpc 的业务码 1015 服务繁忙。
#   这是**对拍抓出来的真实配置缺陷**，不是对拍环境特有的问题（默认值在哪儿都一样）。
#   修法是给 user-api/application.yml 补 negotiation-type: plaintext；
#   在那之前 harness 显式钉住，否则整个 user 域没法对拍。
POSTGRES_DB=rag_db SERVER_PORT=59900 \
  env SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
  java -jar "$RUN_DIR/bin/user-api" \
  --grpc.client.user-rpc.address=static://127.0.0.1:59210 \
  --grpc.client.user-rpc.negotiation-type=plaintext \
  >"$LOGS/rag-user-api.log" 2>&1 & PIDS+=($!)
# ⚠️⚠️ **admin-api 必须在 admin-rpc 就绪之后才起**（2026-10-24，DRIFT-ADMINAPI-009 修复后）
#
#   RAG 侧 admin-api 启动时会做「3s 内连不上 admin-rpc ⇒ 启动失败」的检查
#   （对齐 Go 的 zrpc.MustNewClient —— service/user/admin/api/internal/svc/servicecontext.go:27，
#    go-zero zrpc/internal/client.go:18 dialTimeout=3s）。
#   ⇒ **启动顺序从「无所谓」变成了硬约束**：
#   修之前 admin-rpc 是懒连接，admin-api 先起后起都行；
#   修之后 admin-rpc 必须先 ready，否则 admin-api 直接起不来 ⇒ 整个对拍编排崩。
#
#   Go 侧本来就有这个约束（admin-api 用 MustNewClient），
#   它的顺序（line 167 起 admin-rpc → line 177 起 admin-api）一直是对的。
sleep 5
assert_started "$LOGS/rag-user-rpc.log"  "rag user-rpc"
assert_started "$LOGS/rag-admin-rpc.log" "rag admin-rpc"
assert_started "$LOGS/rag-user-api.log"  "rag user-api"

POSTGRES_DB=rag_db SERVER_PORT=58889 \
  env SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
  java -jar "$RUN_DIR/bin/admin-api" \
  --grpc.client.admin-rpc.address=static://127.0.0.1:58081 \
  --grpc.client.admin-rpc.negotiation-type=plaintext \
  --grpc.client.user-rpc.address=static://127.0.0.1:59210 \
  --grpc.client.user-rpc.negotiation-type=plaintext \
  >"$LOGS/rag-admin-api.log" 2>&1 & PIDS+=($!)
sleep 5
assert_started "$LOGS/rag-admin-api.log" "rag admin-api"
wait_http "http://127.0.0.1:59900/user/v1/login"  "rag user-api" 120
wait_http "http://127.0.0.1:58889/admin/v1/login"  "rag admin-api" 120

# ── message 域（2026-10-22 纳入，AGENTS.md §6.6）────────────────────────
# gRPC 下游同样用 -- 命令行参数（理由见上：map key 含连字符，env 形态的
# relaxed binding 归属不确定）。negotiation-type=plaintext 是必要的 harness
# 固定值：devh client 的默认值是 TLS，而两端 message-rpc 都是 plain gRPC。
#
# ⚠️ **不能用 spring.cloud.nacos.discovery.enabled=false**：message-rpc 的
#    bootstrap/NacosRegistration 是自定义 @Component、构造注入 NacosServiceManager，
#    关掉 starter 会让它装配失败、上下文直接起不来。沿用 register-enabled=false
#    即可：只关自动注册，starter 的 bean 还在。NACOS_PORT=1 让注册立刻失败并被
#    register() 的 try/catch 吞掉，进程照常服务。
POSTGRES_DB=rag_db GRPC_SERVER_PORT=58210 \
  java -jar "$RUN_DIR/bin/message-rpc" \
  --grpc.client.user-rpc.address=static://127.0.0.1:59210 \
  --grpc.client.user-rpc.negotiation-type=plaintext \
  --grpc.client.admin-rpc.address=static://127.0.0.1:58081 \
  --grpc.client.admin-rpc.negotiation-type=plaintext \
  >"$LOGS/rag-message-rpc.log" 2>&1 & PIDS+=($!)
sleep 15
assert_started "$LOGS/rag-message-rpc.log" "rag message-rpc"

# 「进程起来了但 schema 是空的」是一类静默失败：RPC 服务照常监听，
# 只有真正打 SQL 时才炸，而那时候报错长得像业务 bug。
# 上一版 user-rpc 就是这样：Flyway 认为 schema 已是 v3、跳过自己的迁移、
# users_user 根本不存在，却照样打出 "Started UserRpcApplication"。
assert_tables() {  # db, table, label
  local n
  n=$(docker exec difftest-postgres psql -U admin -d "$1" -tAc \
      "select count(*) from information_schema.tables where table_schema='public' and table_name='$2';" 2>/dev/null | tr -d '[:space:]')
  if [[ "$n" != "1" ]]; then
    echo "  FAIL ${3}：表 ${1}.${2} 不存在"
    echo "        RAG 侧查 Flyway 账本（看迁移是不是被跳过了）："
    docker exec difftest-postgres psql -U admin -d "$1" -tAc \
      "select version||' | '||description||' | success='||success from flyway_schema_history order by installed_rank;" 2>/dev/null | sed 's/^/        /' || true
    echo "        实际建出来的表："
    docker exec difftest-postgres psql -U admin -d "$1" -tAc \
      "select tablename from pg_tables where schemaname='public' order by 1;" 2>/dev/null | sed 's/^/        /' || true
    echo "        Go 侧查自己的启动日志（AutoMigrate 失败会直接 log.Fatalln 退出）："
    echo "          $LOGS/go-${3#rag-}-rpc.log"
    return 1
  fi
  echo "  OK   ${3} ${1}.${2}"
}
echo "==> [4b/5] 校验 Flyway 真的建了表（不只看进程活着）"
assert_tables rag_db  users_user                     "rag user-rpc"
assert_tables rag_db  users_user_avatar_history      "rag user-rpc"
assert_tables rag_db  admin_admin                     "rag admin-rpc"
assert_tables rag_db  admin_invite                    "rag admin-rpc"
assert_tables go_db       users                      "go user-rpc"
assert_tables rag_db  message_notification            "rag message-rpc"
assert_tables rag_db  message_conversation             "rag message-rpc"
assert_tables rag_db  message_conversation_message     "rag message-rpc"
assert_tables go_db       message_notification          "go message-rpc"
assert_tables go_db       message_conversation           "go message-rpc"
assert_tables go_db       message_conversation_message   "go message-rpc"

if [[ $SERVE_ONLY -eq 1 ]]; then
  echo "==> [5/5] --serve-only：跳过对拍，两套栈保持运行（PID ${PIDS[*]}）"
  echo "     实验脚本用完请 kill 上述 PID 或 Ctrl-C 本脚本。"
  echo "日志：$LOGS"
  # 前台等待；被 kill / Ctrl-C 时 trap cleanup 收摊
  wait
  exit 0
fi

echo "==> [5/5] 对拍"
set +e
python3 "$HERE/diff.py" "$@"
RC=$?
set -e
echo
echo "日志：$LOGS"
exit $RC
