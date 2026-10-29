#!/usr/bin/env bash
#
# 变异验证：证明 tools/api-full-parity-probe/favorite-rpc/run.sh **不是假绿**（AGENTS.md §3.3.3）。
#
# 这条链有三段，每一段都必须能被变异打红，否则探针就只是「自己跟自己比」：
#   ① Go 侧的真实字节（expected.txt 是从**真源**跑出来的冻结值）
#   ② RAG 侧的消息字节（wire 层：字段名 / 字段号 / 类型漂移）
#   ③ RAG 侧的业务语义（错误码 / description 文案）
#
# M1 打 ①：改 expected.txt 的一枚 hex ⇒ Go 侧与冻结值不同 ⇒ 探针必须转红
# M2 打 ②：把 RAG 侧 proto 的**字段号**改掉 ⇒ RAG 的 wire 字节与 Go 不同 ⇒ 探针必须转红
# M3 打 ③：把 RAG 侧的错误文案改掉 ⇒ description 与 Go 不同 ⇒ 探针必须转红
#
# ⚠️ **还原一律用「文件备份 + 复制回来」** —— 绝不用 `git checkout --`：
#    本 lane 的落码**还没提交**时，`git checkout` 会把它们一起回滚掉
#    ⇒ 变异跑完代码就不是原来的代码了。
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下 ⇒ 留着由系统清理。
#
# 退出码：0 = 全部变异精确转红 + 还原后转绿；1 = 有变异没转红。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
PROBE="${HERE}/run.sh"
EXPECTED="${HERE}/expected.txt"
PROTO_DIR="${RAG_ROOT}/service/favorite/favorite-proto/src/main/proto"
ERRSRC="${RAG_ROOT}/service/favorite/favorite-common/src/main/java/com/rag/favorite/favorite/common/errmsg/FavoriteErrorMessages.java"

cd "${RAG_ROOT}"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"


say() { printf '%s\n' "$*"; }

FAILED=0

run_probe() {
  bash "${PROBE}" >"${TMP_OUT}" 2>&1
  echo $?
}

backup()  { cp "$1" "${BAK_DIR}/$2"; }
restore() { cp "${BAK_DIR}/$2" "$1"; }

# 🔴 **退出时无条件还原**（含被 Ctrl-C / 崩掉的情况）：
#    M2/M3 改的是**产品文件**（favorite.proto / FavoriteErrorMessages.java）
#    ⇒ 万一脚本中途死掉而没还原 ⇒ 产品代码就留在变异态 ⇒ **比探针本身更危险**。
restore_all() {
  [ -f "${BAK_DIR}/expected.txt" ] && cp "${BAK_DIR}/expected.txt" "${EXPECTED}" 2>/dev/null
  [ -f "${BAK_DIR}/favorite.proto" ] && cp "${BAK_DIR}/favorite.proto" "${PROTO_DIR}/favorite.proto" 2>/dev/null
  [ -f "${BAK_DIR}/FavoriteErrorMessages.java" ] && cp "${BAK_DIR}/FavoriteErrorMessages.java" "${ERRSRC}" 2>/dev/null
  return 0
}
trap 'restore_all; rm -f "${TMP_OUT}"' EXIT INT TERM

mutate() {   # $1 = 文件  $2 = 旧串  $3 = 新串
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, "未命中: %r" % (old[:80],)
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

[ -f "${PROTO_DIR}/favorite.proto" ] || { echo "!! 找不到 ${PROTO_DIR}/favorite.proto"; exit 2; }
[ -f "${ERRSRC}" ] || { echo "!! 找不到 ${ERRSRC}"; exit 2; }

backup "${EXPECTED}" expected.txt
backup "${PROTO_DIR}/favorite.proto" favorite.proto
backup "${ERRSRC}" FavoriteErrorMessages.java

say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_probe)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -40 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# ─────────────────────────────────────────────────────────────
# M1：改冻结的 expected.txt 一枚 hex ⇒ Go 侧与冻结值不同 ⇒ 必须转红
#      ⇒ 证明 expected.txt 不是「谁都能写出来」的摆设
# ─────────────────────────────────────────────────────────────
say ""
say "== M1 冻结的 expected.txt 被改（Go 侧 wire 字节）⇒ 必须转红 =="
mutate "${EXPECTED}" '|FROZEN|OK||0a2c' '|FROZEN|OK||0a2d'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · Go 侧与冻结值不符）"
  grep -E "❌|!= expected" "${TMP_OUT}" | head -6 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针没在比冻结值"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${EXPECTED}" expected.txt

# ─────────────────────────────────────────────────────────────
# M2：改 RAG 侧 proto 的**字段号**（favorite_id: 1 → 7）
#      ⇒ RAG 的 wire 字节与 Go 不同 ⇒ 探针必须转红
#      ⇒ 证明「比的是 wire 字节」而不是「解析后字段看起来一样」
# ─────────────────────────────────────────────────────────────
say ""
say "== M2 RAG 侧 proto 字段号漂移（FavoriteItem.favorite_id 1 → 9）⇒ 必须转红 =="
# ⚠️ 字段号必须取**未被占用**的（FavoriteItem 占了 1..8 ⇒ 取 9）：
#    取 7 会与 `cover = 7` 撞号 ⇒ protoc 直接报错 ⇒ 构建失败 ⇒ 探针 exit 2
#    （2026-10-29 实测踩过：以为「探针看不见漂移」，其实是**变异本身非法**）。
mutate "${PROTO_DIR}/favorite.proto" 'message FavoriteItem {
  int64 favorite_id = 1;' 'message FavoriteItem {
  int64 favorite_id = 9;'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · wire 字节漂移被抓到）"
  grep -E "❌|!= expected" "${TMP_OUT}" | head -8 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见 wire 字节漂移"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${PROTO_DIR}/favorite.proto" favorite.proto

# ─────────────────────────────────────────────────────────────
# M3：改 RAG 侧的错误文案（5003 参数错误 → 参数错误X）
#      ⇒ description 与 Go 不同 ⇒ 探针必须转红
#      ⇒ 证明「description 逐字节」这条判据真有牙齿
#      （本域把业务码藏在 description 文案里 ⇒ 文案变形 = 业务码丢失）
# ─────────────────────────────────────────────────────────────
say ""
say "== M3 RAG 侧错误文案漂移（5003「参数错误」→「参数错误X」）⇒ 必须转红 =="
mutate "${ERRSRC}" 'ERROR_INVALID_PARAM.getCode(), "参数错误"' 'ERROR_INVALID_PARAM.getCode(), "参数错误X"'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · description 漂移被抓到）"
  grep -E "❌|!= expected" "${TMP_OUT}" | head -8 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见 description 漂移"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${ERRSRC}" FavoriteErrorMessages.java

say ""
say "== 还原后必须重新转绿 =="
RC="$(run_probe)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  tail -40 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 全部变异精确转红 + 还原后转绿 ⇒ 探针非假绿"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 探针是假绿"
exit 1
