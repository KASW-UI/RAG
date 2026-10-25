#!/usr/bin/env bash
# ============================================================================
# 探针基建守门网 · **PG 就绪等待** · favorite 域
# ----------------------------------------------------------------------------
# 目的：favorite 域有 **4 个探针**会起真 PG 容器
#      （authority-live / ddl-parity / grpc-contract / api-full-parity），
#      它们各自带一份 `wait_pg_tcp`（域内不共享 tools/ 根 ⇒ 4 份副本）。
#
#      这份守门网卡两件事：
#        ① **不能变成橡皮章**：`wait_pg_tcp` 必须**如实**判「没就绪」
#           ⇒ 否则探针在 PG 没起来时照样往下跑 ⇒ **假绿**（比假红更坏）。
#        ② **4 份副本不能漂移**：任一副本被改坏（永远返回 ready / 永远等满超时）
#           ⇒ 这里立刻响。
#
# 做法：**从每个 run.sh 原样 sed 出真函数**（不重写、不 mock），
#       然后跑 3 个场景 —— ① 容器不存在 ② 容器秒退 ③ 真 PG 容器。
#
# 场景期望：
#   ① 容器不存在   ⇒ 返回 1（**不得假就绪**）
#   ② 容器秒退     ⇒ 返回 1 且 **< 30s**（**不得白等满 180s 超时**）
#   ③ 真 PG 容器   ⇒ 返回 0（**真就绪必须能认出**）
#
# 背景与教训 ⇒ `docs/Pitfalls/favorite/rpc.md` PIT-FAV-RPC-020
# （「探针的『环境红』不是『被测代码红』」—— 等待过短 / 探活探错目标 ⇒ 假红）。
#
# 用法：bash tools/probe-gate/favorite/check-pg-readiness.sh
# 退出码：0 = 4 个探针的副本全部守规矩；1 = 有副本判错。
#
# ⚠️ **域作用域**：本文件只管 favorite 的 4 个探针。
#    别的域若有同款 `wait_pg_tcp`，需各自复制本守门网（⇒ 归 `global` 收口域登记）。
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say() { printf '\033[1;36m[probe-gate]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }

PROBES=(
  "authority-live-probe"
  "ddl-parity-probe"
  "grpc-contract-probe"
  "api-full-parity-probe"
)

FAILED=0
DEAD_C="fav-pg-gate-dead-$$"
LIVE_C="fav-pg-gate-live-$$"
cleanup() {
  docker rm -f "${DEAD_C}" "${LIVE_C}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ── 准备两个共用容器（场景 ② 的秒退容器 / 场景 ③ 的真 PG 容器）─────────────
docker run -d --rm --name "${DEAD_C}" postgres:16-alpine --this-flag-does-not-exist >/dev/null 2>&1
docker run -d --rm --name "${LIVE_C}" \
  -e POSTGRES_PASSWORD=probe -e POSTGRES_USER=postgres -p 0:5432 postgres:16-alpine >/dev/null 2>&1

for p in "${PROBES[@]}"; do
  SRC="${RAG_ROOT}/tools/${p}/favorite/run.sh"
  say "检查 ${p}"
  if [ ! -f "${SRC}" ]; then
    bad "${p}: 找不到 ${SRC}"; FAILED=$((FAILED+1)); continue
  fi
  # ⚠️ 原样抽真函数（不重写 ⇒ 测的就是探针实际跑的那份）
  FN="$(awk '/^wait_pg_tcp\(\) \{/,/^\}$/' "${SRC}")"
  if [ -z "${FN}" ]; then
    bad "${p}: run.sh 里没有 wait_pg_tcp（副本丢了 ⇒ 4 份已漂移）"
    FAILED=$((FAILED+1)); continue
  fi

  # 场景 ①：容器不存在
  PG_CONTAINER="fav-pg-gate-nonexistent-$$"
  eval "${FN}"; wait_pg_tcp 180; rc1=$?

  # 场景 ②：容器秒退
  PG_CONTAINER="${DEAD_C}"
  t0=$(date +%s)
  eval "${FN}"; wait_pg_tcp 180; rc2=$?
  t2=$(( $(date +%s) - t0 ))

  # 场景 ③：真 PG
  PG_CONTAINER="${LIVE_C}"
  t0=$(date +%s)
  eval "${FN}"; wait_pg_tcp 180; rc3=$?
  t3=$(( $(date +%s) - t0 ))

  # ── 判定 ────────────────────────────────────────────────────────────────
  if [ "${rc1}" != "1" ]; then
    bad "${p}: 容器不存在却返回 ${rc1} ⇒ **假就绪**（探针会带着没起来的 PG 往下跑 ⇒ 假绿）"
    FAILED=$((FAILED+1))
  else
    ok "${p}: 场景① 容器不存在 ⇒ 返回 1（不假就绪）"
  fi

  if [ "${rc2}" != "1" ]; then
    bad "${p}: 秒退容器却返回 ${rc2} ⇒ 误判就绪"
    FAILED=$((FAILED+1))
  elif [ "${t2}" -ge 30 ]; then
    bad "${p}: 秒退容器白等了 ${t2}s ⇒ 探针会卡住整个超时才判死"
    FAILED=$((FAILED+1))
  else
    ok "${p}: 场景② 容器秒退 ⇒ 返回 1（${t2}s，没白等满超时）"
  fi

  if [ "${rc3}" != "0" ]; then
    bad "${p}: 真 PG 容器却返回 ${rc3} ⇒ **把真就绪判成没就绪**（探针永远跑不起来）"
    FAILED=$((FAILED+1))
  else
    ok "${p}: 场景③ 真 PG ⇒ 返回 0（${t3}s，真就绪能认出）"
  fi
done

echo
if [ "${FAILED}" -eq 0 ]; then
  ok "PG 就绪守门网通过：${#PROBES[@]} 个探针的 wait_pg_tcp 副本全部守规矩"
  exit 0
fi
bad "PG 就绪守门网失败：${FAILED} 项"
exit 1
