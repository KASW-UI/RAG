#!/usr/bin/env bash
#
# favorite 域 **指标 result 标签**的语义锚定探针（Go 半边）。
#
# 定位：验证一条 **Go 语言语义** —— 「命名返回值 + defer」组合下，
#       `return nil, favoritecommon.GRPCError(...)` 会把 err 赋成那个错误
#       ⇒ defer 里的 ObserveRPC 记的是 **"fail"**。
#
#       2026-10-23 的台账把这里判成「Go 侧 21 处错误返回被记成 success」，
#       **本探针证伪该结论** ⇒ U5 撤销（Go 与 RAG 本来就一致）。
#
# ⚠️ 本探针**不是**双侧对拍：它验的是「Go 语言语义」这一层，
#    不是 favorite 的业务逻辑（那需要 DB / etcd / Kafka）。
#    RAG 侧的对偶守门是 `FavoriteRpcMetricsResultTagTest`
#    （断言 RAG 的每个失败分支都记 fail —— 真正的风险在「漏设 RESULT_FAIL」）。
#
# 用法：
#   bash tools/metrics-probe/favorite/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/metrics-probe/favorite/run.sh
#
# 退出码：0 = 语义如预期（错误路径记 fail）；2 = 环境问题 / 真源漂移。
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# Go 真源：默认**向上搜索**（主工作区与 worktree 两种布局的深度不同）。
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/favorite/rpc/internal/metrics/metrics.go" ]; then
      printf '%s' "${d}/Sea-RideTheWind_golang"
      return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}

GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源 —— 用 GO_REPO=... 指定"

GO_SRC="${GO_REPO}/service/favorite/rpc/internal/metrics/metrics.go"
[ -f "${GO_SRC}" ] || die "找不到 Go 真源：${GO_SRC}"

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say "== 1/3 从真源抽取 ObserveRPC 的 result 判定 =="
# ⚠️ 抽取真源里那 3 行判定（result := "success" / if err != nil / result = "fail"），
#    与「探针预期的那份」做 sha256 比对 ⇒ **真源改了本探针立刻响**，
#    避免探针变成「拿着自己的一份副本自证」。
sed -n '/^func ObserveRPC(/,/^}/p' "${GO_SRC}" \
  | grep -E 'result := "success"|if err != nil|result = "fail"' \
  | sed 's/^[[:space:]]*//' >"${TMP}/real.txt"
[ -s "${TMP}/real.txt" ] || die "没能从真源抽取到 ObserveRPC 的 result 判定（真源结构变了？）"

cat >"${TMP}/expected-fragment.txt" <<'EOF'
result := "success"
if err != nil {
result = "fail"
EOF

REAL_SHA="$(shasum -a 256 "${TMP}/real.txt" | awk '{print $1}')"
EXPECTED_SHA="$(shasum -a 256 "${TMP}/expected-fragment.txt" | awk '{print $1}')"
say "   真源判定片段 sha256 = ${REAL_SHA}"
if [ "${REAL_SHA}" != "${EXPECTED_SHA}" ]; then
  say ""
  say "❌ 真源的 ObserveRPC result 判定与本探针预期不一致 —— 真源已变，请重新评估 U5："
  diff -u "${TMP}/expected-fragment.txt" "${TMP}/real.txt" || true
  exit 2
fi
say "   ✓ 与本探针预期一致"

say "== 2/3 跑 Go 侧（最小复刻：命名返回值 + defer）=="
# ⚠️ 在临时模块里跑（零外部依赖 ⇒ 不需要 go.sum、不需要 module cache）。
mkdir -p "${TMP}/src"
cp "${HERE}/go/main.go" "${TMP}/src/main.go"
printf 'module favoritemetricsprobe\n\ngo 1.25\n' >"${TMP}/src/go.mod"
( cd "${TMP}/src" && GOPROXY=off GOTOOLCHAIN=local go run . ) >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"
cat "${TMP}/go.txt"

say "== 3/3 判定 =="
# ⚠️ 期望：
#   错误路径（return-stmt-only / assigned-first）⇒ fail
#   成功路径（success / nil-nil）              ⇒ success
EXPECTED_OUT="${TMP}/expected-out.txt"
cat >"${EXPECTED_OUT}" <<'EOF'
RESULT|return-stmt-only|fail
RESULT|assigned-first|fail
RESULT|success|success
RESULT|nil-nil|success
EOF

if diff -u "${EXPECTED_OUT}" "${TMP}/go.txt" >"${TMP}/diff.txt"; then
  say ""
  say "✅ Go 语义如预期：错误路径记 fail（⇒ U5 撤销，Go 与 RAG 本来就一致）"
  exit 0
fi

say ""
say "❌ 与预期不符，diff（- = 预期 / + = 实测）："
cat "${TMP}/diff.txt"
exit 1
