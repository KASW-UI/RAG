#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  users 域探针的**变异验证**（AGENTS.md §3.3.3：新增的每个探针都必须配一个
#  **能真正触发它**的变异，否则无法排除「假绿」）。
#
#  ⚠️ **四个变异都不改任何产品代码**：
#    ① M1 篡改**一侧的库**（运行期改一行数据）
#    ② M2 停掉**一侧的进程**（运行期 kill）
#    ③ M3 关掉**探针自己的 1xx 感知**（退回 Python urllib 的行为）
#    ④ M0 基线（无变异）
#    ⇒ 源码零改动 ⇒ 天然可还原 ⇒ **不会污染工作树**。
#
#  用法：bash tools/api-full-parity-probe/users/mutate-parity.sh
#  退出码：0 = 全部变异都按预期表现；1 = 有变异没按预期表现。
# ════════════════════════════════════════════════════════════════════════════
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${OUT_DIR:-/tmp/r4upprobe-mutate}"
mkdir -p "$OUT_DIR"

PG_CONTAINER="${UPPROBE_CONTAINER_PREFIX:-r4upprobe}-postgres"
RAG_USER_API_PORT="${UPPROBE_RAG_USER_API_PORT:-18219}"

PASS=0
FAIL=0

say()  { printf '%s\n' "$*"; }
step() { printf '\n── %s ──\n' "$*"; }

run_case() {   # name, expected_rc, must_contain_regex, env assignments...
  local name="$1" expected_rc="$2" must="$3"; shift 3
  local log="$OUT_DIR/${name}.log"
  local rc=0 attempt=0
  step "${name}"
  # ⚠️ **EXIT=2 = 环境问题**（本机负载高 ⇒ RAG 侧撞 2s gRPC deadline / 3s HTTP 超时）
  #    ⇒ **重试一次**（等负载降下来），**不**把它当成「变异没按预期表现」。
  while :; do
    attempt=$((attempt+1))
    ( env "$@" bash "$HERE/run.sh" --no-build ) >"$log" 2>&1
    rc=$?
    [[ $rc -eq 2 && $attempt -lt 2 ]] || break
    say "   ⚠️ EXIT=2（环境问题 · 负载过高）⇒ 等 30s 重试一次"
    sleep 30
  done
  local tail_line
  tail_line=$(grep -E "steps=|1xx_命中" "$log" | tail -2 | tr '\n' ' ')
  if [[ "$rc" != "$expected_rc" ]]; then
    say "   ❌ 期望 EXIT=${expected_rc}，实际 EXIT=${rc}"
    say "      ${tail_line}"
    FAIL=$((FAIL+1)); return 1
  fi
  if [[ -n "$must" ]] && ! grep -qE "$must" "$log"; then
    say "   ❌ 期望日志里出现 /${must}/，实际没有"
    say "      ${tail_line}"
    FAIL=$((FAIL+1)); return 1
  fi
  say "   ✅ EXIT=${rc}（期望 ${expected_rc}）· ${tail_line}"
  PASS=$((PASS+1)); return 0
}

say "════ users 域探针 · 变异验证 ════"

# ── M0 基线：无变异 ⇒ 必须绿 ───────────────────────────────────────────────
# ⚠️ 这一条同时是「探针真的能跑」的证明 ⇒ 若它不绿，后面三条无意义。
run_case "M0-baseline" 0 "unexpected=0" UPPROBE_CONTAINER_PREFIX="${UPPROBE_CONTAINER_PREFIX:-r4upprobe}"

# ── M1 篡改一侧的库 ⇒ 探针必须红，且**精确**指出被改的那一步 ───────────────
# 手法：把 RAG 侧 users_user 里 dt_user1 的 email 改成别的值
#   ⇒ 矩阵的 `user.get` 读到的 email 与 Go 侧不同 ⇒ 必须判 DIFF
#   ⇒ 而 `user.update`（紧随其后）会把两侧 email 都改成 u1-new@… ⇒ 后续步骤恢复相等
#   ⇒ ⇒ **期望：恰好 1 条 DIFF，且就是 user.get**
M1_CMD="docker exec -i ${PG_CONTAINER} psql -U admin -d rag_db -tAc \"UPDATE users_user SET email='tampered@example.com' WHERE username='dt_user1' AND email <> 'tampered@example.com';\""
run_case "M1-db-tamper" 1 "\[ DIFF \] user\.get " \
  "PROBE_MUTATE_AFTER=user.register" "PROBE_MUTATE_SIDE=rag" "PROBE_MUTATE_CMD=${M1_CMD}"

# ── M2 停掉一侧的进程 ⇒ 探针必须红（不能「一侧挂了还报绿」）────────────────
# ⚠️ **必须用 `kill -9`**：2026-10-29 实测踩过 —— SIGTERM 触发 Spring Boot 的
#    **优雅停机**（`spring.lifecycle.timeout-per-shutdown-phase` 默认 30s）⇒
#    JVM 还会活好几秒 ⇒ 矩阵后续步骤**照样答得上** ⇒ 探针**误报绿**（第一次跑就是）。
#    ⇒ ⇒ 只有 SIGKILL 能让监听口**立刻**消失。
M2_CMD="p=\$(lsof -nP -iTCP:${RAG_USER_API_PORT} -sTCP:LISTEN -t 2>/dev/null | head -1); if [ -n \"\$p\" ]; then kill -9 \$p; echo killed=\$p; else echo 'NO LISTENER'; fi"
run_case "M2-kill-rag-api" 1 "transport error" \
  "PROBE_MUTATE_AFTER=user.get" "PROBE_MUTATE_SIDE=rag" "PROBE_MUTATE_CMD=${M2_CMD}"

# ── M3 让 RAG 侧变慢（必然命中 1xx）────────────────────────────────────────
# 手法：给 RAG 侧的 users_user 加一把 ACCESS EXCLUSIVE 锁并持 1.2s
#   ⇒ RAG 侧的下一个请求（`user.register.dup` → SELECT users_user）**阻塞 ~1.2s**
#   ⇒ **必然 > 500ms** ⇒ 必然命中共享层的 `103 Early Hints` 探针字节
#   ⇒ 且 **1.2s < users 域的 HTTP 超时 3s** ⇒ 最终响应**仍然是正常的 200**
#      （⇒ 锁本身不会造成任何差异 ⇒ 差异只可能来自「1xx 怎么处理」）
M3_LOCK="nohup docker exec -i ${PG_CONTAINER} psql -U admin -d rag_db -tAc \"BEGIN; LOCK TABLE users_user IN ACCESS EXCLUSIVE MODE; SELECT pg_sleep(2.0); COMMIT;\" >/dev/null 2>&1 & sleep 0.6; echo locked"
run_case "M3a-slow-but-1xx-ok" 0 "unexpected=0" \
  "PROBE_MUTATE_AFTER=user.register" "PROBE_MUTATE_SIDE=rag" "PROBE_MUTATE_CMD=${M3_LOCK}"

# ── M4 同上，但**关掉探针的 1xx 感知** ⇒ 必须红 ──────────────────────────────
# ⚠️ 这一对（M3a / M4）是本探针最关键的证据：
#    同样的「慢请求」⇒ M3a 绿 / M4 红 ⇒ ⇒ **差异只来自「1xx 怎么处理」**
#    ⇒ ⇒ 证明「探针变绿**确实是** RFC 9110 感知带来的」，而不是碰巧；
#    ⇒ 同时证明「1xx 不是被当成『允许任意差异』」：
#        关掉感知 + 不许重试（PROBE_MAX_RETRY=0）⇒ 1xx 直接判红。
run_case "M4-slow-and-1xx-blind" 1 "DIFF" \
  "PROBE_MUTATE_AFTER=user.register" "PROBE_MUTATE_SIDE=rag" "PROBE_MUTATE_CMD=${M3_LOCK}" \
  "PROBE_DISABLE_1XX_SKIP=1" "PROBE_MAX_RETRY=0"

# ── M5 还原后转绿：所有变异跑完，再跑一次基线 ⇒ 必须绿 ──────────────────────
# ⚠️ 四个变异**全是运行期**的（改一行库 / kill 一个进程 / 加一把锁 / 切一个 env）
#    ⇒ 源码零改动 ⇒ 下一轮 `down -v` 重建的库就是**干净的**
#    ⇒ 这一条把「还原后转绿」**显式**跑出来，而不是靠推断。
run_case "M5-restored" 0 "unexpected=0" UPPROBE_CONTAINER_PREFIX="${UPPROBE_CONTAINER_PREFIX:-r4upprobe}"

say ""
say "════ 结果：PASS=${PASS}  FAIL=${FAIL} ════"
[[ $FAIL -eq 0 ]] || exit 1
exit 0
