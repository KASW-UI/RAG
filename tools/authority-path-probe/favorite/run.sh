#!/usr/bin/env bash
# run.sh —— 权威读端点的 **path 形态 + 错误响应** 双侧对拍。
#
# 两侧：
#   · Go 侧  = tools/authority-path-probe/favorite/go/main.go
#              （只用标准库复刻 http.ServeMux + http.Error/NotFound/Redirect 的行为）
#   · Java 侧 = service/favorite/favorite-rpc 的 FavoriteAuthorityPathParityTest
#
# 判定：
#   ① Go 侧输出必须与 expected.txt **逐行相同** ⇒ Go 真源的行为没变；
#      变了 ⇒ 本探针 exit 1 ⇒ 提醒同步更新 expected.txt **与** Java 侧断言。
#   ② Java 侧断言必须全绿 ⇒ RAG 侧与 Go 的矩阵一致。
#
# 退出码：0 = 两侧一致；1 = 不一致；2 = 环境问题（缺 Go toolchain / 缺 mvn）。
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "== 1/3 跑 Go 侧（标准库复刻 ServeMux）=="
if ! command -v go >/dev/null 2>&1; then
    echo "❌ 找不到 go toolchain（Go 真源探针需要它）" >&2
    exit 2
fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
ACTUAL="$TMP/go.txt"
# ⚠️ go.mod **不入库**（与 errmsg-probe / metrics-probe 同约定）：
#    运行时在临时目录生成，避免仓里留一个会被误改 / 误 ignore 的 go.mod。
mkdir -p "$TMP/src"
cp "$SCRIPT_DIR/go/main.go" "$TMP/src/main.go"
printf 'module favoriteauthoritypathprobe\n\ngo 1.25\n' >"$TMP/src/go.mod"
( cd "$TMP/src" && GOPROXY=off GOTOOLCHAIN=local go run . ) > "$ACTUAL" \
  || { echo "❌ Go 侧运行失败" >&2; exit 2; }
cat "$ACTUAL"

echo
echo "== 2/3 与 expected.txt 逐行比对 =="
if diff -u "$SCRIPT_DIR/expected.txt" "$ACTUAL"; then
    echo "✅ Go 侧行为与 expected.txt 一致（Go 真源未变）"
else
    echo "❌ Go 侧行为变了 ⇒ 同步更新 expected.txt 与 FavoriteAuthorityPathParityTest" >&2
    exit 1
fi

echo
echo "== 3/3 跑 Java 侧断言（FavoriteAuthorityPathParityTest）=="
if ! command -v mvn >/dev/null 2>&1; then
    echo "❌ 找不到 mvn" >&2
    exit 2
fi
( cd "$ROOT" && mvn -o -q -pl service/favorite/favorite-rpc test \
    -Dtest='FavoriteAuthorityPathParityTest' -DfailIfNoTests=false ) || {
    echo "❌ Java 侧断言不通过 ⇒ RAG 与 Go 的矩阵不一致" >&2
    exit 1
}

echo
echo "✅ 双侧逐条一致（12 条 path/错误响应矩阵 + 少一段 path 的 404）"
exit 0
