#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════════
#  redis-disconnect 探针的编排（Go 腿 ＋ 双侧对照）
# ═════════════════════════════════════════════════════════════════════════════
#
# 【它回答什么】
#   Redis 连接被**服务端优雅关闭**（docker stop = SIGTERM ⇒ FIN）之后，
#   客户端**第一条命令**多久失败？
#   · Go 腿：真源那套 Options ⇒ 预期「快速失败」（约 1.7s，含 go-redis 重试退避）
#   · RAG 腿：域内既有守门 HotRedisDisconnectFastRejectGateTest（hot）/ CommentRedisDisconnectFastRejectGateTest（comment）
#             ⇒ 共享层 `REJECT_COMMANDS` ⇒ 预期「1ms 立即拒单」
#   ⇒ ⇒ **两侧语义相同（都「快速失败」），量级不同** ⇒ ⇒ 本工具守的是**语义**，不是毫秒数
#
# 【三段式（同 tools/api-envelope-probe/favorite/run.sh 的形状）】
#   ① Go 腿输出（go run .）
#   ② 与**冻结的** expected.txt **逐字节 diff**（只 diff 归一化结论行 ⇒ 不含随机量）
#   ③ 再跑 Java 腿（域内既有守门）⇒ ⇒ 需 maven ⇒ ⇒ 可用 --go-only 跳过
#
# 【端口纪律】
#   · **每组各自取一个空闲端口**（容器还在占着 ⇒ 不能复用同一个）
#   · 取值范围 **20000–29000**（= **避开 OS 临时端口段 49152–65535**）
#     ⇒ ⇒ 那个口**不可能**被别的进程临时占走 ⇒ ⇒ 消灭「回应我的那个口上不是我的容器」那类 flake
#   · 取之前**先 bind 自检** ⇒ 占了就换下一个
#   · ⚠️ **绝不碰 `difftest5k-redis` / `cdifftest-redis` 等共享容器** ⇒ ⇒ 本脚本自己起、自己收
#
# 【用法】
#   bash run.sh --mode hot                     # 只跑 Go 腿（免 maven）
#   bash run.sh --mode comment                 # 只跑 Go 腿（免 maven）
#   bash run.sh --mode hot --full              # Go 腿 ＋ expected.txt diff ＋ Java 腿（要 maven）
# ═════════════════════════════════════════════════════════════════════════════
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../../../../.." && pwd)"   # RAG 仓根
GO_BIN="${GO_BIN:-/Users/pr/sdk/go1.25.11/bin/go}"

MODE="hot"
FULL=0
while [ $# -gt 0 ]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --full) FULL=1; shift ;;
    -h|--help) sed -n '2,40p' "$0"; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

case "$MODE" in
  hot|comment|zeroway) ;;
  *) echo "PROBE-FAIL unknown --mode=${MODE}（hot|comment|zeroway）" >&2; exit 2 ;;
esac

PORT_BEGIN=20000
PORT_END=29000

# ── 取一个空闲端口（bind 自检；避开 OS 临时端口段）────────────────────────────
pick_port() {
  local p
  for p in $(seq "$PORT_BEGIN" "$PORT_END"); do
    if ! lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "$p"; return 0
    fi
  done
  return 1
}

PORT="$(pick_port)" || { echo "PROBE-FAIL no free port in $PORT_BEGIN-$PORT_END" >&2; exit 2; }
CONTAINER="g3-redis-disconnect-$PORT"
OUT_DIR="${OUT_DIR:-/tmp/g3-redis-disconnect}"
OUT="${OUT:-$OUT_DIR/$MODE-$PORT.txt}"
mkdir -p "$OUT_DIR"

cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "▶ mode=$MODE port=$PORT container=$CONTAINER"

# ── 起 redis（**自己的**一次性容器 ⇒ 不碰任何共享容器）────────────────────────
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" -p "127.0.0.1:$PORT:6379" redis:7-alpine >/dev/null || {
  echo "PROBE-FAIL docker run failed" >&2; exit 2; }

# 等就绪（redis-cli PING ⇒ +PONG）
READY=0
for _ in $(seq 1 30); do
  if docker exec "$CONTAINER" redis-cli PING 2>/dev/null | grep -q PONG; then READY=1; break; fi
  sleep 0.5
done
[ "$READY" = 1 ] || { echo "PROBE-FAIL redis not ready" >&2; exit 2; }

# ── ① Go 腿 ─────────────────────────────────────────────────────────────────
cd "$HERE" || exit 2
GOFLAGS="-mod=mod" CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local \
  "$GO_BIN" run . -addr "127.0.0.1:$PORT" -container "$CONTAINER" -mode "$MODE" 2>&1 | tee "$OUT"

grep -q "^PROBE-VERDICT " "$OUT" || { echo "PROBE-FAIL no PROBE-VERDICT line" >&2; exit 2; }
grep -q "^PROBE-READY" "$OUT" || { echo "PROBE-FAIL no PROBE-READY line" >&2; exit 2; }

# ── ② 与冻结的 expected.txt 逐字节 diff（只比归一化结论行）────────────────────
EXPECTED="$HERE/expected.txt"
ACTUAL="$OUT_DIR/actual-$MODE.txt"
grep "^PROBE-VERDICT " "$OUT" > "$ACTUAL"
if [ -f "$EXPECTED" ]; then
  if diff -u <(grep "^PROBE-VERDICT mode=$MODE " "$EXPECTED") "$ACTUAL" > "$OUT_DIR/diff-$MODE.txt"; then
    echo "✅ VERDICT-MATCH mode=${MODE}（与 expected.txt 逐字节一致）"
  else
    echo "❌ VERDICT-DIFF mode=${MODE}（与 expected.txt 不一致 ⇒ 真源漂移或环境变了）"
    cat "$OUT_DIR/diff-$MODE.txt"
    exit 1
  fi
else
  echo "⚠️ expected.txt 不存在 ⇒ 跳过 diff（首次跑 ⇒ 用 out/actual-$MODE.txt 生成它）"
fi

# ── ③ Java 腿（域内既有守门；--full 才跑 ⇒ 因为要 maven）────────────────────
if [ "$FULL" = 1 ]; then
  case "$MODE" in
    hot) JAVA_GATE="HotRedisDisconnectFastRejectGateTest"; JAVA_MOD="service/hot/hot-rpc" ;;
    comment) JAVA_GATE="CommentRedisDisconnectFastRejectGateTest"; JAVA_MOD="service/comment/comment-rpc" ;;
    *) JAVA_GATE=""; JAVA_MOD="" ;;
  esac
  if [ -z "$JAVA_GATE" ]; then
    echo "⚠️ mode=$MODE 没有对应的域内 Java 腿守门 ⇒ 跳过"
  else
    # ⚠️ 一律用 ${VAR} 显式界定变量名边界：macOS bash 3.2 会把 $VAR 后紧跟的多字节字符
    #    （此处是全角括号）**算进变量名** ⇒ set -u 下报 unbound variable ⇒ 收尾行崩掉、报错打不出来。
    #    守门：python3 tools/difftest/scan-bash-legacy-vars.py ⇒ EXIT=0
    echo "▶ Java 腿：${JAVA_GATE}（${JAVA_MOD}）"
    ( cd "$REPO" && MAVEN_OPTS="-Xmx768m" /Users/pr/.sdkman/candidates/maven/current/bin/mvn -B -o \
        -pl "$JAVA_MOD" -am test \
        -Dtest="$JAVA_GATE" -Dsurefire.failIfNoSpecifiedTests=false -DargLine="-Xmx2g" )
  fi
fi

echo "✅ DONE mode=${MODE}（输出：${OUT}）"
