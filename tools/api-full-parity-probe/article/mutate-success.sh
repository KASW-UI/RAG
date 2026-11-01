#!/usr/bin/env bash
# 变异验证：证明 `tools/api-full-parity-probe/article/run-success.sh` **不是假绿**（AGENTS.md §3.3.3）。
#
# 【为什么不并进 `mutate-parity.sh`】
#   `mutate-parity.sh` 的 **M1~M4** 已被 `docs/PhaseGoal-judge.md` §十二 引用为 article 域的
#   **既有变异证据** ⇒ 改它会动到已被引用的证据 ⇒ **本脚本独立成文件**，一个字节都不动它。
#
# 【这条链要证的三件事】
#   ① **不是自己跟自己**：M5 —— 改 `expected-success.txt` 一枚 hex
#      ⇒ Go 侧与冻结值不符 ⇒ 探针必须转红。
#   ② **比较面真的穿过 revision 分支**：M6 —— 改 rpc 层 revision 分支写进 `ext_info` 的键名
#      ⇒ RAG 侧 HTTP 输出漂移 ⇒ 探针必须转红。
#      ⇒ ⚠️ 这一条最关键：成功路径的判据是「响应里出现的是 **revision** 的字段」
#      ⇒ 若比较面压根没穿过 revision 分支，这个判据就是空的。
#   ③ **「4 个 revision 分支信号」有牙齿**：M7 —— 把正文从 `revision.getMarkdown()`
#      换成 `article.getContent()` ⇒ 正文不再是 revision 的 ⇒ S3 信号必须转红。
#
# 【还原一律用「文件备份 + 复制回来」】
#   绝不用 `git checkout --`：本 lane 的改动可能还没提交 ⇒ `git checkout` 会一起回滚掉。
#   还原后**逐文件核 sha256** ⇒ 还原没生效即 EXIT=1。
#
# 退出码：0 = 三条变异都精确转红 + 还原后转绿；1 = 有变异没转红（探针是假绿）；
#         2 = 环境问题导致本轮判定无效（**不等于假绿**）。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
PROBE="${HERE}/run-success.sh"
EXPECTED="${HERE}/expected-success.txt"
RPC_SRC="${RAG_ROOT}/service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticlePublicDomainService.java"

cd "${RAG_ROOT}"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫整体拦下 ⇒ 留给系统清理。
trap 'rm -f "${TMP_OUT}"; [ -f "${BAK_DIR}/rpcsrc.java" ] && cp "${BAK_DIR}/rpcsrc.java" "${RPC_SRC}"; [ -f "${BAK_DIR}/expected-success.txt" ] && cp "${BAK_DIR}/expected-success.txt" "${EXPECTED}"' EXIT

say() { printf '%s\n' "$*"; }

FAILED=0
ENV_BAD=0

# ⚠️ **exit 2 = 环境问题（不是「有差异」）** —— 判据来自 run-success.sh 的 `die()`。
run_probe() {
  bash "${PROBE}" >"${TMP_OUT}" 2>&1
  local rc=$?
  if [ "${rc}" = "2" ]; then
    say "   · 探针报 exit 2（环境问题，不是差异）⇒ 等 8s 让 Nacos 心跳超时后再试一次"
    sleep 8
    bash "${PROBE}" >"${TMP_OUT}" 2>&1
    rc=$?
  fi
  echo ${rc}
}

backup() { cp "$1" "${BAK_DIR}/$2"; }
restore() { cp "${BAK_DIR}/$2" "$1"; }

mutate() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, f"未命中: {old[:60]}"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

verify_sha() {
  local got
  got="$(shasum -a 256 "$1" | awk '{print $1}')"
  if [ "${got}" = "$2" ]; then
    say "   ✅ ${3} 已还原（sha256=${got:0:16}…）"
  else
    say "   ❌ ${3} 还原没生效（sha256=${got:0:16}… ≠ ${2:0:16}…）"
    FAILED=1
  fi
}

backup "${EXPECTED}"  expected-success.txt
backup "${RPC_SRC}"    rpcsrc.java
SHA_EXPECTED="$(shasum -a 256 "${EXPECTED}" | awk '{print $1}')"
SHA_RPC="$(shasum -a 256 "${RPC_SRC}" | awk '{print $1}')"

say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_probe)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# ─────────────────────────────────────────────────────────────
# M5：改冻结的 expected-success.txt 一枚 hex ⇒ Go 侧与冻结值不同 ⇒ 必须转红
#      ⇒ 证明「不是自己跟自己」
# ─────────────────────────────────────────────────────────────
say ""
say "== M5 冻结的 expected-success.txt 被改 ⇒ 必须转红 =="
mutate "${EXPECTED}" 'CASE|get_article_success|7b22' 'CASE|get_article_success|7b23'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · Go 侧与冻结值不符）"
  grep -E "❌|!= 冻结期望值" "${TMP_OUT}" | head -6 | sed 's/^/   | /'
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 环境问题（exit 2 · 重试后仍不可用）⇒ **本轮判定无效**，不等于「探针没在比冻结值」"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 没按预期转红（exit 0 = Go 侧与冻结值仍一致）⇒ **探针没在比冻结值**"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${EXPECTED}" expected-success.txt

# ─────────────────────────────────────────────────────────────
# M6：改 rpc 层 revision 分支写进 ext_info 的键名 ⇒ RAG 侧输出漂移 ⇒ 必须转红
#      ⇒ 证明「比较面**真的穿过 revision 分支**」
# ─────────────────────────────────────────────────────────────
say ""
say "== M6 改 rpc 层 revision 分支的 ext_info 键名 ⇒ 必须转红 =="
mutate "${RPC_SRC}" 'view.extInfo.put("published_revision_id", revision.getRevisionId());' \
                   'view.extInfo.put("published_revision_id_MUT", revision.getRevisionId());'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · revision 分支漂移被抓到 ⇒ 比较面确实穿过 revision 分支）"
  grep -E "❌|!= RAG|S1 " "${TMP_OUT}" | head -8 | sed 's/^/   | /'
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 环境问题（exit 2 · 重试后仍不可用）⇒ **本轮判定无效**，不等于「探针没在比 rpc 层」"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 没按预期转红（exit 0 = 两侧仍一致）⇒ **探针没在比 rpc 层的 revision 分支**"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${RPC_SRC}" rpcsrc.java

# ─────────────────────────────────────────────────────────────
# M7：正文从 revision.getMarkdown() 换成 article.getContent()
#      ⇒ 正文不再是 revision 的 ⇒ S3 信号必须转红 ⇒ 证明「4 个信号有牙齿」
# ─────────────────────────────────────────────────────────────
say ""
say "== M7 正文不再取 revision.markdown ⇒ 必须转红 =="
mutate "${RPC_SRC}" 'markdown = revision.getMarkdown();' \
                   'markdown = article.getContent();'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · 正文漂移被抓到 ⇒ S3 信号有牙齿）"
  grep -E "❌|!= RAG|S3 " "${TMP_OUT}" | head -8 | sed 's/^/   | /'
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 环境问题（exit 2 · 重试后仍不可用）⇒ **本轮判定无效**，不等于「探针看不见正文漂移」"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 没按预期转红（exit 0 = 两侧仍一致）⇒ **探针的正文判据是空的**"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${RPC_SRC}" rpcsrc.java

say ""
say "== 还原后必须重新转绿 =="
RC="$(run_probe)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 还原后仍报 exit 2（环境问题）⇒ **本轮判定无效**，不等于「还原没生效」"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 还原后仍红（exit ${RC} = 两侧仍不一致）⇒ 还原没生效"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
say "== 还原核验：被变异的文件内容哈希必须回到变异前 =="
verify_sha "${EXPECTED}" "${SHA_EXPECTED}" "expected-success.txt"
verify_sha "${RPC_SRC}"   "${SHA_RPC}"      "article-rpc ArticlePublicDomainService.java"

say ""
if [ "${FAILED}" != "0" ]; then
  say "❌ 有变异没转红（两侧仍一致）⇒ 探针是假绿"
  exit 1
fi
if [ "${ENV_BAD}" != "0" ]; then
  say "⚠️ 有变异因**环境问题**（exit 2）未判定 ⇒ **本轮变异验证无效**（≠ 假绿）"
  exit 2
fi
say "✅ 三条变异全部精确转红 + 还原后转绿 ⇒ 成功路径探针非假绿"
exit 0
