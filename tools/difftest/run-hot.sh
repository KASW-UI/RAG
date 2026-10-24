#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
#  run-hot.sh —— hot 域端到端双侧活体对拍的编排（U1 / D1）
# ═══════════════════════════════════════════════════════════════════════════
#
# 【与 tools/difftest/run.sh 的关系】
#   run.sh 是 users / message 域的编排，本脚本是 **hot 域专用**的。
#   为什么不并进 run.sh：hot 域对拍的结构不同 —— 它不需要 postgres / redis /
#   kafka / etcd / nacos，只需要「两个 api 进程 + 两个下游桩」。
#   并进去会让 users/message 域每次对拍都被 hot 域的步骤拖累。
#
# 【三条纪律（与 run.sh 同款，逐条照抄）】
#   1. **绝不修改冻结的 Go 仓库**。Go 的运行时配置全部来自
#      tools/difftest/conf/go/hot-api.yaml（真源副本），用 `-f` 指过去。
#   2. **Go 不写任何文件进仓库**。副本已把 `Log.Mode: file` 改成 `console`。
#   3. **两侧端口都必须独占，且不能碰本机既有服务**。
#      本机已被占用的（**绝不能 kill**）：Sea-* 容器占着 8081/8889/49210/49900，
#      `Sea-RideTheWind_java` 进程占着 8081/8889。
#      ⇒ 本脚本统一搬到 18xxx / 58xxx / 19xxx 三段私有段。
#
# 【端口分配】
#   Go  hot-api HTTP    18903   （真源 8893）
#   Go  hot-api DevSrv  19069   （真源 16067）
#   RAG hot-api HTTP    58903   （真源 8893 / RAG 默认 18897）
#   Go  侧下游桩 gRPC   19903
#   RAG 侧下游桩 gRPC   19904
#
# 【为什么两侧各起一个「同构」的桩，而不是共用一个】
#   共用一个桩分不清「谁发起的调用」。同构双桩各写各的调用日志 ⇒ 除了比 HTTP
#   响应，还能比**下游调用序列**（比了就知道两侧调的方法/参数/条数是否一致）。
#
# 【用法】
#   ./run-hot.sh              # 完整跑（含构建）
#   ./run-hot.sh --no-build   # 复用已有 jar / Go 二进制 / 桩 class
#   ./run-hot.sh --keep       # 跑完不收摊，便于手工 curl
#
# 【U5 保护层实验用的两个环境变量口】
#   GO_CONF=<yaml>          换 Go 侧配置副本（默认 conf/go/hot-api.yaml）。
#                           U5 用 conf/go/hot-api-lowmaxconns.yaml（MaxConns: 8）。
#   RAG_EXTRA_ARGS="..."    给 RAG 侧追加 Spring 参数（空格分隔）。
#                           U5 用 "--rag.protection.max-conns=8"（**必须与 Go 侧同值**）。
#   ⚠️ 这两个口**必须成对使用** —— 只改一侧就不是「同一个阈值」，
#      测出来的差异分不清是「闸门行为不同」还是「阈值不同」。
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
RUN_DIR="${RUN_DIR:-/tmp/hot-difftest}"
LOGS="$RUN_DIR/logs"
BUILD_DIR="$RUN_DIR/stub-classes"

GO_API_PORT=18903
RAG_API_PORT=58903
GO_STUB_PORT=19903
RAG_STUB_PORT=19904

MODE_FILE="$RUN_DIR/stub-mode"
GO_CALLS="$RUN_DIR/calls-go.log"
RAG_CALLS="$RUN_DIR/calls-rag.log"
# 计时取证用的**事件**日志（带时间戳）—— 与主调用日志分开，主日志要跨侧逐行比对
GO_EVENTS="$RUN_DIR/calls-go.log.events"
RAG_EVENTS="$RUN_DIR/calls-rag.log.events"

BUILD=1
KEEP=0
SERVE_ONLY=0
# ⚠️ **只把 diff-hot.py 认识的参数传下去**（2026-10-24 实测踩过）：
#    脚本自己的开关（--no-build / --keep / --serve-only）若原样透传给 diff-hot.py，
#    它会因「unrecognized arguments」直接 exit 2 ⇒ **整轮对拍根本没跑**，
#    而外层只看到「EXIT=2」，很容易误读成「对拍失败」。
DIFF_ARGS=()
for a in "$@"; do
  case "$a" in
    --no-build) BUILD=0 ;;
    --keep)     KEEP=1 ;;
    # 起完两套栈就停下、不跑对拍、进程留在前台。
    # 专供 probe-hot-limits.py 这类「栈已经在跑」的取证脚本复用编排逻辑。
    --serve-only) SERVE_ONLY=1 ;;
    --only)     DIFF_ARGS+=("$a") ;;
    --only=*)   DIFF_ARGS+=("$a") ;;
    *) echo "未知参数：${a}（可选：--no-build / --keep / --serve-only / --only CASE）" >&2; exit 2 ;;
  esac
done

PIDS=()
cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    echo "[run-hot.sh] --keep：保留进程。手动收摊：kill ${PIDS[*]:-}"
    return
  fi
  echo "[run-hot.sh] 收摊…"
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT

# 探针必须拒绝 404 / 000 —— curl 拿到任何 HTTP 响应都 exit 0，
# 上一版把「打到别人服务上的 404」误判成「本服务就绪」。
wait_http() {  # url, name, tries
  local url=$1 name=$2 tries=${3:-90} code=""
  for _ in $(seq 1 "$tries"); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 5 "$url" 2>/dev/null) || code=""
    if [[ -n "$code" && "$code" != "000" && "$code" != "404" ]]; then
      echo "  OK   ${name} 就绪（HTTP ${code}）"; return 0
    fi
    sleep 1
  done
  echo "  FAIL ${name} 超时（${url}，最后状态码 ${code:-none}）"; return 1
}

# Spring 启动失败会打 "APPLICATION FAILED TO START"，而进程仍然退出 0，
# 不扫就会静默地把「没起来」当成「起来了」。
assert_started() {  # logfile, name
  if grep -qE 'APPLICATION FAILED TO START|was already in use|BeanCreationException' "$1" 2>/dev/null; then
    echo "  FAIL ${name} 启动失败："
    grep -E 'Description:|Port .* was already in use|APPLICATION FAILED' -A 2 "$1" | head -8 | sed 's/^/      /'
    return 1
  fi
}

mkdir -p "$RUN_DIR" "$LOGS" "$BUILD_DIR"

# ── [1/5] 构建 Go 栈 ───────────────────────────────────────────────────────
# 产物落 /tmp，**不进任何仓库**；真源仓库全程只读。
if [[ $BUILD -eq 1 ]]; then
  echo "==> [1/5] 构建 Go hot-api（CGO_ENABLED=0，产物落 $RUN_DIR/bin）"
  mkdir -p "$RUN_DIR/bin"
  ( cd "$GO_REPO" && CGO_ENABLED=0 go build -o "$RUN_DIR/bin/go-hot-api" ./service/hot/api )
  echo "  ✓ go-hot-api"
fi
[[ -x "$RUN_DIR/bin/go-hot-api" ]] || { echo "缺少 Go 二进制，去掉 --no-build"; exit 1; }

# ── [2/5] 构建 RAG 栈 ─────────────────────────────────────────────────────
if [[ $BUILD -eq 1 ]]; then
  echo "==> [2/5] 构建 RAG hot-api（含 hot-proto / article-proto / hot-common / common）"
  ( cd "$ROOT" && mvn -q -o -pl service/hot/hot-api -am -DskipTests package )
  for d in service/hot/hot-api service/hot/hot-common service/hot/hot-proto \
           service/article/article-proto service/common; do
    [[ -d "$ROOT/$d/target/classes" ]] || { echo "缺少 $ROOT/$d/target/classes"; exit 1; }
  done
fi

# ── [3/5] 编译下游桩 ──────────────────────────────────────────────────────
# 桩同时提供 hot.HotService 与 article.ArticleService（Go / RAG 两侧都指它）。
# 用 RAG 侧 protoc 生成的类：RAG 的 proto 与 Go 侧**逐字节同源**（§3.4）⇒ wire 兼容。
#
# ⚠️ **RAG 侧用 `java -cp` 启动，不 `java -jar`**：
#   `service/hot/hot-api/pom.xml` **没有** spring-boot-maven-plugin ⇒ `package` 产出的
#   是普通 jar，没有 Main-Class ⇒ `java -jar` 报「没有主清单属性」。
#   （这不是 hot 域独有：article-api / comment-api / follow-api / like-api / message-api /
#     task-api 六个 api 模块同样没有；只有 favorite-api / user-api / admin-api 有。）
#   ⇒ 对拍工具不该依赖一次生产构建改动 ⇒ 这里直接用「各模块 target/classes + 依赖 classpath」
#     启动。**新鲜编出的 classes 放在 classpath 最前**，避免 ~/.m2 里的旧 jar 抢在前面
#     （`package` 不 install，dependency:build-classpath 解析到的是 ~/.m2 里的版本）。
if [[ $BUILD -eq 1 ]]; then
  echo "==> [3/5] 编译下游桩（HotService + ArticleService）+ 计算 classpath"
  LIBCP_FILE="$RUN_DIR/libcp.txt"
  ( cd "$ROOT" && mvn -q -o -pl service/hot/hot-api \
      dependency:build-classpath -Dmdep.outputFile="$LIBCP_FILE" >/dev/null 2>&1 ) \
    || { echo "  FAIL dependency:build-classpath 失败"; exit 1; }
  LIBCP="$(cat "$LIBCP_FILE")"
  PROTO_CP="$ROOT/service/hot/hot-proto/target/classes:$ROOT/service/article/article-proto/target/classes"
  APP_CP="$ROOT/service/hot/hot-api/target/classes:$ROOT/service/hot/hot-common/target/classes:$PROTO_CP:$ROOT/service/common/target/classes:$LIBCP"
  javac -encoding UTF-8 -d "$BUILD_DIR" -cp "$PROTO_CP:$LIBCP" "$HERE/hot-stub-server.java" \
    || { echo "  FAIL 桩编译失败"; exit 1; }
  printf '%s' "$PROTO_CP" > "$RUN_DIR/proto-cp.txt"
  printf '%s' "$LIBCP"    > "$RUN_DIR/libcp.txt.saved"
  printf '%s' "$APP_CP"   > "$RUN_DIR/app-cp.txt"
fi
PROTO_CP="$(cat "$RUN_DIR/proto-cp.txt" 2>/dev/null)"
LIBCP="$(cat "$RUN_DIR/libcp.txt.saved" 2>/dev/null)"
APP_CP="$(cat "$RUN_DIR/app-cp.txt" 2>/dev/null)"
[[ -n "$PROTO_CP" && -n "$LIBCP" && -n "$APP_CP" ]] \
  || { echo "缺少 classpath，先不带 --no-build 跑一次"; exit 1; }

# ── [4/5] 起桩 + 起两侧 api ───────────────────────────────────────────────
echo "==> [4/5] 起下游桩 + 两侧 hot-api"
: > "$MODE_FILE"; echo "normal" > "$MODE_FILE"
: > "$GO_CALLS"; : > "$RAG_CALLS"
: > "$GO_EVENTS"; : > "$RAG_EVENTS"

STUB_CP="$BUILD_DIR:$PROTO_CP:$LIBCP"
java -cp "$STUB_CP" \
  -Dstub.port="$GO_STUB_PORT" \
  -Dstub.mode.file="$MODE_FILE" \
  -Dstub.call.log="$GO_CALLS" \
  -Dstub.event.log="$GO_EVENTS" \
  tools.difftest.HotStubServer >"$LOGS/stub-go.log" 2>&1 & PIDS+=($!)
java -cp "$STUB_CP" \
  -Dstub.port="$RAG_STUB_PORT" \
  -Dstub.mode.file="$MODE_FILE" \
  -Dstub.call.log="$RAG_CALLS" \
  -Dstub.event.log="$RAG_EVENTS" \
  tools.difftest.HotStubServer >"$LOGS/stub-rag.log" 2>&1 & PIDS+=($!)
sleep 3
grep -q READY "$LOGS/stub-go.log"  || { echo "  FAIL Go 侧桩没起来"; cat "$LOGS/stub-go.log"; exit 1; }
grep -q READY "$LOGS/stub-rag.log" || { echo "  FAIL RAG 侧桩没起来"; cat "$LOGS/stub-rag.log"; exit 1; }
echo "  ✓ 两个桩就绪（$GO_STUB_PORT / ${RAG_STUB_PORT}）"

# Go hot-api：配置指向 conf/go/hot-api.yaml 副本（Endpoints 直连 Go 侧桩）
# ⚠️ GO_CONF 可换副本（U5 用 lowmaxconns 版本）；默认仍是生产形态副本。
GO_CONF="${GO_CONF:-$HERE/conf/go/hot-api.yaml}"
"$RUN_DIR/bin/go-hot-api" -f "$GO_CONF" >"$LOGS/go-hot-api.log" 2>&1 & PIDS+=($!)

# RAG hot-api：
#   · HOT_API_PORT —— application.yml 的 server.port 用的就是 ${HOT_API_PORT:18897}
#   · --grpc.client.*.address=static:// —— 摘掉服务发现层（与 Go 侧 Endpoints 直连对称）
#   · SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false —— 关掉自动注册
#     （⚠️ 不能用 spring.cloud.nacos.discovery.enabled=false：会让 discovery 客户端
#       装配失败；register-enabled=false 只关注册、starter 的 bean 还在）
#   · NACOS_ADDR / OTEL_ADDR —— 指到不可达的本机端口，让「连不上」快速失败，
#     而不是等 DNS 超时（对拍环境**刻意不起** nacos / jaeger）
HOT_API_PORT=$RAG_API_PORT \
NACOS_ADDR=127.0.0.1:1 \
OTEL_ADDR=127.0.0.1 \
OTLP_PORT=1 \
SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
  java -cp "$APP_CP" com.rag.hot.hot.api.HotApiApplication \
  --grpc.client.hot-rpc.address="static://127.0.0.1:$RAG_STUB_PORT" \
  --grpc.client.article-rpc.address="static://127.0.0.1:$RAG_STUB_PORT" \
  ${RAG_EXTRA_ARGS:-} \
  >"$LOGS/rag-hot-api.log" 2>&1 & PIDS+=($!)

sleep 5
assert_started "$LOGS/go-hot-api.log"  "go hot-api"
assert_started "$LOGS/rag-hot-api.log" "rag hot-api"
wait_http "http://127.0.0.1:$GO_API_PORT/hot/v1/articles"  "go hot-api"  60
wait_http "http://127.0.0.1:$RAG_API_PORT/hot/v1/articles" "rag hot-api" 120

if [[ $SERVE_ONLY -eq 1 ]]; then
  echo "==> [5/5] --serve-only：跳过对拍，两套栈保持运行（PID ${PIDS[*]}）"
  echo "     探针脚本用完请 kill 上述 PID 或 Ctrl-C 本脚本。"
  echo "日志：$LOGS"
  wait
  exit 0
fi

# ── [5/5] 对拍 ────────────────────────────────────────────────────────────
echo "==> [5/5] 对拍"
set +e
GO_HOT_API_BASE="http://127.0.0.1:$GO_API_PORT" \
RAG_HOT_API_BASE="http://127.0.0.1:$RAG_API_PORT" \
HOT_DIFFTEST_DIR="$RUN_DIR" \
python3 "$HERE/diff-hot.py" ${DIFF_ARGS[@]+"${DIFF_ARGS[@]}"}
RC=$?
set -e

# 纪律 1 的自检：整个流程结束后，冻结的 Go 仓库**必须**没有 tracked 改动。
if [[ -d "$GO_REPO/.git" ]]; then
  GO_DIRTY="$(cd "$GO_REPO" && git status --porcelain --untracked-files=no)"
  if [[ -n "$GO_DIRTY" ]]; then
    echo
    echo "  ⚠️ 冻结的 Go 仓库出现 tracked 改动（违反对拍纪律 1）："
    echo "$GO_DIRTY" | sed 's/^/      /'
    RC=1
  else
    echo
    echo "  ✓ 冻结的 Go 仓库 tracked diff 为空（纪律 1 守住）"
  fi
fi

echo
echo "日志：$LOGS"
exit $RC
