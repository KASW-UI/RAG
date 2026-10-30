#!/usr/bin/env bash
#
# Redis 期限「写面」**双侧活体对拍**（E66 的 Go 侧那半 · 2026-10-31 · 合并收口执行者）。
#
# 定位
# ----
# 测「请求 deadline **已耗尽** ⇒ 命令**根本没下发**到 Redis」——
# **观测量 = 读回来还是基线值**（不是靠异常类型推断）⇒ ⇒ 两侧用**同一个观测量**。
#
#   · Go 半边 = `go/main.go`（**go-redis/v9**，版本从**冻结仓 go.mod 钉死**）
#     ⇒ 跑的是真源用的**同一套依赖** ⇒ 调用形态 = 真源那一行
#       （`service/comment/rpc/internal/cache/comment_subject_cache.go:84` 的
#        `c.rdb.Get(ctx, …)` / `:103` 的 `c.rdb.Setex(ctx, …)`）
#     ⇒ 「命令下发前」由 go-redis 的 `internal/pool.waitTurn(ctx)` 查 `ctx.Done()` 把关
#   · RAG 半边 = `java/RedisDeadlineProbe.java`（**生产类**：
#     `GoStyleRedisDeadlineConnectionFactory` ＋ `GoStyleRequestDeadline` ＋ 真 Lettuce）
#
# ⚠️ **为什么不是「读源码就算」**：E66 原判是 Go 侧「靠读源码 + 真源探针」、
#    RAG 侧「靠 JUnit 守门」⇒ ⇒ **两侧没有同一个观测量**⇒ ⇒ 「同形」没有活体证据
#    ⇒ ⇒ 本探针把两侧**拉齐到同一个观测量**（同一份 PROBE 行格式）⇒ ⇒ 才能逐键对拍。
#
# 退出码
# ------
#   0 = 两侧逐键相等，且两侧形态都符合已登记的 Go 真源基线
#   1 = 对拍破（两侧不等）或形态与基线不符
#   2 = 环境不满足（Docker 起不来 / Redis 连不上 / 依赖装不上）
#
# ══════════════════════════════════════════════════════════════════════════
# 三条纪律
# ① **Go 半边离线构建**（`GOPROXY=off`）⇒ 用**冻结仓的 `go.sum`** 校验依赖
#    ⇒ 仓库里**不提交任何绝对路径**（`go.mod` 由本脚本在运行时生成）
# ② **取退出码先落变量**（`out=$(cmd 2>&1); rc=$?`）⇒ 不用管道里的退出码
# ③ **两侧产出不足 ⇒ 退出码 2**（那是「没跑」，不是「形态变了」）
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
GO_TRUTH_REPO="${GO_TRUTH_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

say() { printf '%s\n' "$*"; }

TMP="$(mktemp -d)"
REDIS_NAME="rag-e66-redis-$$"
REDIS_PORT=""
cleanup() {
  if [ -n "${REDIS_NAME}" ]; then
    docker rm -f "${REDIS_NAME}" >/dev/null 2>&1 || true
  fi
  rm -rf "${TMP}"
}
trap cleanup EXIT

# ── ① 起一个真 Redis（Testcontainers 的等价物 · 但不进 JUnit 流水线）──────────
if ! command -v docker >/dev/null 2>&1; then
  say "❌ 环境不满足：没有 docker ⇒ 本探针跑不了（不是「形态变了」）"
  exit 2
fi
REDIS_PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
if ! docker run -d --rm --name "${REDIS_NAME}" -p "127.0.0.1:${REDIS_PORT}:6379" redis:7-alpine \
      >"${TMP}/docker.log" 2>&1; then
  say "❌ 环境不满足：Redis 容器起不来 ⇒"
  head -20 "${TMP}/docker.log"
  exit 2
fi

# 等就绪（**轮询真端口** ⇒ 不靠 sleep 猜）
ready=0
for _ in $(seq 1 60); do
  if (exec 3<>"/dev/tcp/127.0.0.1/${REDIS_PORT}") 2>/dev/null; then
    exec 3<&- 2>/dev/null || true
    exec 3>&- 2>/dev/null || true
    ready=1
    break
  fi
  sleep 0.5
done
if [ "${ready}" != "1" ]; then
  say "❌ 环境不满足：Redis 端口 ${REDIS_PORT} 60 次轮询仍未就绪"
  exit 2
fi
say "✔ 真 Redis 已起：127.0.0.1:${REDIS_PORT}（镜像 redis:7-alpine）"

# ── ② Go 半边：离线构建（go.mod 运行时生成 + 冻结仓的 go.sum）──────────────
GO_DIR="${TMP}/go"
mkdir -p "${GO_DIR}"
cp "${HERE}/go/main.go" "${GO_DIR}/main.go"
GO_VERSION="$(grep -m1 '^go ' "${GO_TRUTH_REPO}/go.mod" | awk '{print $2}')"
REDIS_VERSION="$(grep -m1 'github.com/redis/go-redis/v9 ' "${GO_TRUTH_REPO}/go.mod" | awk '{print $2}')"
if [ -z "${GO_VERSION}" ] || [ -z "${REDIS_VERSION}" ]; then
  say "❌ 环境不满足：读不到冻结仓的 go / go-redis 版本"
  exit 2
fi
cat >"${GO_DIR}/go.mod" <<EOF
module goprobe

go ${GO_VERSION}

require github.com/redis/go-redis/v9 ${REDIS_VERSION}
EOF
cp "${GO_TRUTH_REPO}/go.sum" "${GO_DIR}/go.sum"

( cd "${GO_DIR}" && CGO_ENABLED=0 GOFLAGS=-mod=mod GOPROXY=off GOSUMDB=off GOPRIVATE='*' \
    go build -o probe . ) >"${TMP}/gobuild.log" 2>&1 || {
  say "❌ Go 半边**构建失败** ⇒ 探针没跑起来（不是「形态变了」）"
  head -30 "${TMP}/gobuild.log"
  exit 2
}

# ── ③ RAG 半边：编译**生产类** + 真 Lettuce ───────────────────────────────
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common compile >"${TMP}/mvn-compile.log" 2>&1 || {
  say "❌ RAG 半边**编译失败** ⇒ 探针没跑起来"
  head -30 "${TMP}/mvn-compile.log"
  exit 2
}
CP_FILE="${TMP}/cp.txt"
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common dependency:build-classpath \
    -Dmdep.outputFile="${CP_FILE}" >"${TMP}/mvn-cp.log" 2>&1 || {
  say "❌ 依赖 classpath 解析失败"
  head -30 "${TMP}/mvn-cp.log"
  exit 2
}
CP="${RAG_ROOT}/service/common/target/classes:$(cat "${CP_FILE}")"
mkdir -p "${TMP}/classes"
javac -nowarn -cp "${CP}" -d "${TMP}/classes" "${HERE}/java/RedisDeadlineProbe.java" \
    2>"${TMP}/javac.log" || {
  say "❌ RAG 半边**javac 失败**"
  head -30 "${TMP}/javac.log"
  exit 2
}

# ── ④ 跑两侧 ─────────────────────────────────────────────────────────────
GO_OUT="${TMP}/go.out"
out="$(cd "${GO_DIR}" && REDIS_ADDR="127.0.0.1:${REDIS_PORT}" ./probe 2>&1)"; rc=$?
printf '%s\n' "${out}" >"${GO_OUT}"
if [ "${rc}" -ne 0 ]; then
  say "❌ Go 半边退出码 ${rc} ⇒ 环境不满足（不是「形态变了」）"
  head -20 "${GO_OUT}"
  exit 2
fi
go_lines="$(grep -c '^PROBE|' "${GO_OUT}" || true)"
if [ "${go_lines}" -lt 2 ]; then
  say "❌ Go 半边只产出 ${go_lines} 条 PROBE 行（应为 2：耗尽 + 负控）⇒ 「退出码 0」不能当「跑过了」"
  head -20 "${GO_OUT}"
  exit 2
fi

RAG_OUT="${TMP}/rag.out"
out="$(REDIS_ADDR="127.0.0.1:${REDIS_PORT}" java -cp "${TMP}/classes:${CP}" RedisDeadlineProbe 2>&1)"; rc=$?
printf '%s\n' "${out}" >"${RAG_OUT}"
if [ "${rc}" -ne 0 ]; then
  say "❌ RAG 半边退出码 ${rc} ⇒ 环境不满足（不是「形态变了」）"
  head -20 "${RAG_OUT}"
  exit 2
fi
rag_lines="$(grep -c '^PROBE|' "${RAG_OUT}" || true)"
if [ "${rag_lines}" -lt 2 ]; then
  say "❌ RAG 半边只产出 ${rag_lines} 条 PROBE 行（应为 2）⇒ 「退出码 0」不能当「跑过了」"
  head -20 "${RAG_OUT}"
  exit 2
fi

say "   Go  : $(grep '^PROBE|' "${GO_OUT}" | tr '\n' ' ')"
say "   RAG : $(grep '^PROBE|' "${RAG_OUT}" | tr '\n' ' ')"
say ""

# ── ⑤ 逐键对拍（parity） + 形态基线（Go 真源那一侧的已登记形态）─────────────
python3 - "$GO_OUT" "$RAG_OUT" <<'PY'
import sys

PARITY_KEYS = ('write_applied', 'err_is_deadline', 'value_after')
# ⚠️ Go 真源那一侧的**已登记形态**（出处 = tools/semantic-probe/redis-deadline/go/main.go 的 javadoc
#    + docs/DRIFT/_cross/grading/W6.md 的 E66 行 + service/common/.../GoStyleRedisDeadlineLiveRedisWriteFaceGateTest 的 javadoc）
EXPECTED = {
    'exhausted': {'write_applied': 'false', 'err_is_deadline': 'true', 'value_after': 'baseline'},
    'alive':     {'write_applied': 'true',  'err_is_deadline': 'false'},
}


def parse(path):
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line.startswith('PROBE|'):
                continue
            parts = line.split('|')
            kv = {}
            for p in parts[2:]:
                if '=' in p:
                    k, v = p.split('=', 1)
                    kv[k] = v
            form = kv.get('form')
            if form:
                out[form] = kv
    return out


go = parse(sys.argv[1])
rag = parse(sys.argv[2])
fails = []

for form in sorted(set(go) | set(rag)):
    if form not in go or form not in rag:
        fails.append('形态 %s：有一侧没产出（Go=%s RAG=%s）' % (form, form in go, form in rag))
        continue
    for k in PARITY_KEYS:
        gv, rv = go[form].get(k), rag[form].get(k)
        if gv is None or rv is None:
            fails.append('%s.%s：有一侧缺这个键（Go=%s RAG=%s）' % (form, k, gv, rv))
        elif gv != rv:
            fails.append('%s.%s：Go=%s ≠ RAG=%s' % (form, k, gv, rv))
    # 形态基线（两侧都必须是 Go 真源那一侧的形态）
    exp = EXPECTED.get(form)
    if exp:
        for k, want in exp.items():
            for side, d in (('Go', go[form]), ('RAG', rag[form])):
                got = d.get(k)
                if got != want:
                    fails.append('%s.%s：%s=%s ≠ 已登记基线 %s' % (form, k, side, got, want))

if fails:
    print('🔴 **双侧对拍破**（%d 项）：' % len(fails))
    for f in fails:
        print('   ❌ %s' % f)
    sys.exit(1)

print('✅ 双侧逐键相等 ＋ 形态与 Go 真源已登记基线一致')
print('   ⇒ ⇒ 「请求 deadline 已耗尽 ⇒ 命令根本没下发到 Redis」两侧**同形**（活体证据）')
sys.exit(0)
PY
