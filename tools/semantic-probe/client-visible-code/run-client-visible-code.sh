#!/usr/bin/env bash
#
# 客户端可见码「集合」Go 半边活体探针（AGENTS.md §3.3 规则 2 的第五个落地）。
#
# 定位
# ----
# 测「请求 deadline **已过期** ⇒ Go 侧客户端能看到哪些码」——
# 这不是「两侧必须相等」的等值守门，而是**决定 logout 修法的形状守门**：
#
#   · Go 的客户端可见码**集合 = {4 (DeadlineExceeded), 13 (Internal)}**
#     ⇒ ⇒ handler 级码（= 业务码 5001）**是可观察的**（窄窗口）
#     ⇒ ⇒ 「RAG 保留域内 checkDeadline() 守卫」⇒ RAG 的集合 = {4} = **Go 集合的真子集**
#     ⇒ ⇒ 「RAG 删掉守卫」⇒ RAG 的集合 = {4, 13} = **与 Go 同集合** ⇒ ⇒ 删掉才是同形。
#
#   · Go 的客户端可见码**集合 = {4}**
#     ⇒ ⇒ handler 级码**观察不到** ⇒ ⇒ 保留守卫即可（域侧零落码）。
#
# ⚠️ **这个数以前不可复算**：它曾在一次临时 module 里跑出来（N=20000 ⇒ 1015=19995 / 5001=5），
#    但**探针源码没落仓** ⇒ 「跑某条命令会得到 N」变成不可复算口径。
#    ⇒ ⇒ 本脚本 + go/main.go 就是把那一次**落进仓**。
#
# 退出码：0 = 形态与已登记一致（集合 = {4, 13}）；1 = 形态变了（集合不含 13 ⇒ 前提要重核）；2 = 环境不满足
#
# ══════════════════════════════════════════════════════════════════════════
# 三条纪律
# ① **Go 半边直接引用冻结仓**（`replace sea-try-go => <冻结仓>`）
#    ⇒ 不复制、不改写冻结仓一个字节 ⇒ 跑的就是真源。
#    ⚠️ 冻结仓路径是**机器相关**的 ⇒ 由本脚本在运行时生成 go.mod，
#       仓库里只放 main.go（不提交任何绝对路径）。
#
# ② **CGO_ENABLED=0** —— 本机没有 Xcode 头文件 ⇒ cgo 会报 `'stdlib.h' file not found`。
#
# ③ **漂移键的期望值必须能指到台账**（见下方 EXPECTED_FORM 的出处）。
#    没有台账出处的漂移键 = 无出处的断言 ⇒ 不许进来。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GO_TRUTH_REPO="${GO_TRUTH_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

GO_OUT="${TMP}/go.txt"
say() { printf '%s\n' "$*"; }

# ══════════════════════════════════════════════════════════════════════════
# 期望的漂移形态
#   key            | 期望值 | 一句话理由                                        | 台账出处
# ══════════════════════════════════════════════════════════════════════════
EXPECTED_FORM_FILE="${TMP}/expected-form.txt"
cat >"${EXPECTED_FORM_FILE}" <<'EOF'
fast_handler_code_set|4,13|Go 的 select 在 done 与 timeoutCtx.Done() 同时就绪时随机选 ⇒ 走 done 分支就返回 handler 的 codes.Internal ⇒ handler 级码**可观察**（窄窗口）|docs/DRIFT/_cross/ 收口域（E66 · logout 期限面）+ docs/Worktree.md §3-2 两种合并态
slow_handler_code_set|4|handler 跑满 50ms ⇒ 父 goroutine 先看到 timeoutCtx.Done() ⇒ 确定走超时分支 ⇒ 1015 是主体形态|same
EOF

# ══════════════════════════════════════════════════════════════════════════
# 1) 构建并跑 Go 半边（真源拦截器 + 真 ctx）
# ══════════════════════════════════════════════════════════════════════════
say "== 构建并跑 Go 半边（真源 observability.NewUnaryServerInterceptor + 真 ctx）=="
if [ ! -d "${GO_TRUTH_REPO}/service/common/observability" ]; then
  say "!! 找不到冻结仓的 service/common/observability：${GO_TRUTH_REPO}"
  say "!! 可用环境变量 GO_TRUTH_REPO 指向真源目录"
  exit 2
fi
if ! command -v go >/dev/null 2>&1; then
  say "!! 没有 Go toolchain ⇒ 本探针无法运行"
  exit 2
fi

GO_DIR="${TMP}/goprobe"
mkdir -p "${GO_DIR}"
cp "${HERE}/go/main.go" "${GO_DIR}/main.go"
cat >"${GO_DIR}/go.mod" <<EOF
module goprobe

go 1.25.11

require sea-try-go v0.0.0

replace sea-try-go => ${GO_TRUTH_REPO}
EOF

( cd "${GO_DIR}" && CGO_ENABLED=0 GOFLAGS=-mod=mod GOPROXY=off GOSUMDB=off GOPRIVATE='*' \
    go build -o probe . ) >"${TMP}/gobuild.log" 2>&1 || {
  say "❌ Go 半边**构建失败** ⇒ 探针没跑起来（不是「形态变了」，是「没跑」）"
  tail -30 "${TMP}/gobuild.log"
  exit 2
}
"${GO_DIR}/probe" >"${GO_OUT}" 2>"${TMP}/go.err" || {
  say "❌ Go 半边**运行失败**"
  tail -30 "${TMP}/go.err"
  exit 2
}
if ! grep -q "^PROBE|" "${GO_OUT}"; then
  say "❌ Go 半边没有产出 PROBE 行 ⇒ 探针没跑到"
  tail -30 "${TMP}/go.err"
  exit 2
fi

say ""
say "== 探针产出 =="
grep "^PROBE|" "${GO_OUT}" | sed 's/^/   /'
say ""

# ══════════════════════════════════════════════════════════════════════════
# 2) 判形状：fast_handler 的码集合必须含 4 与 13
# ══════════════════════════════════════════════════════════════════════════
fast_set="$(grep '^PROBE|fast_handler|code_set=' "${GO_OUT}" | sed 's/^.*code_set=//')"
slow_set="$(grep '^PROBE|slow_handler|code_set=' "${GO_OUT}" | sed 's/^.*code_set=//')"

say "== 形态判定 =="
say "   fast_handler 码集合 = ${fast_set}   （期望：4,13）"
say "   slow_handler 码集合 = ${slow_set}   （期望：4）"

status=0
if [ "${fast_set}" != "4,13" ]; then
  say "❌ **形态变了**：fast_handler 的客户端可见码集合 = ${fast_set}，不再是 {4,13}"
  say "   ⇒ ⇒ 「handler 级码（5001）可观察」这个前提**不再成立**"
  say "   ⇒ ⇒ 必须回到源头重核（不要直接把期望值改成新的数）"
  status=1
fi
if [ "${slow_set}" != "4" ]; then
  say "❌ **对照组也变了**：slow_handler 的码集合 = ${slow_set}，不再是 {4}"
  say "   ⇒ ⇒ 「1015 是主体形态」这个对照前提不成立 ⇒ ⇒ 先查探针本身"
  status=1
fi
if [ "${status}" -eq 0 ]; then
  say "✅ 形态与已登记一致：Go 侧客户端可见码集合 = {1015, 5001}（5001 是窄窗口）"
  say "   ⇒ ⇒ 「保留域侧守卫 ⇒ RAG 集合 = {1015} = Go 集合的真子集」⇒ ⇒ **删掉守卫才能同集合**"
fi
exit "${status}"
