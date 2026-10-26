#!/usr/bin/env bash
# =============================================================================
# check-flyway-concurrent.sh —— **L4 lane**：三进程并发同库共存的真 PG 验证
# =============================================================================
#
# 【它验的是什么】
#   AGENTS.md §6.3 问题 4 的定案是「每模块独立账本表名 flyway_schema_history_<模块>，
#   不改任何迁移文件名」⇒ **配置层**已闭环（tools/difftest/check-flyway-ledger.py ⇒ EXIT=0）。
#   但配置层闭环 **≠** 运行时能共存 —— 「三本账能不能在同一个库、同一个 schema 上
#   同时跑」这个形态**从未验证过**。本脚本就是补这个洞。
#
# 【怎么验的（不是「跑一遍绿」）】
#   · **真 PG 16**（独立容器、独立网络、独立端口 ⇒ 不抢别的 lane 的 difftest-postgres）
#   · 每个模块跑**自己的 JVM**，起**真的 Spring Boot 上下文**
#     （只 import DataSource + Flyway 两个 auto-configuration ⇒ 不拉 Kafka/Nacos/Redis）
#     ⇒ 走的是 **Spring Boot 自己的 FlywayAutoConfiguration**
#     ⇒ 测的是「模块自己的 application.yml → Spring Boot → Flyway 引擎」这条**生产接线**
#   · 配置**不硬编码**：DSN 由各模块 yaml 里的 ${POSTGRES_HOST:...} 等占位符拼出来
#     （只把宿主/端口/库名指到本 lane 的临时容器 ⇒ 其余 query 参数保持模块原样）
#   · **并发栅栏**：一组里的所有进程等到**同一个墙钟瞬间**才起上下文 ⇒ 真并发
#
# 【退出码】
#   0 = 所有场景都没有差异（各域的表都建出来了、账本互不覆盖、baseline 位置正确）
#   1 = 有差异（某个域的表没建出来 / 账本被互相覆盖 / baseline 打错位置 / 迁移失败）
#   2 = 环境不满足（没有 docker / mvn / javac …）⇒ 不算「有差异」
#
# 【复现】
#   bash tools/difftest/check-flyway-concurrent.sh
#   bash tools/difftest/check-flyway-concurrent.sh --keep      # 保留容器便于现场看
# =============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PG_IMAGE="${L4_PG_IMAGE:-postgres:16-alpine}"
PG_USER="rag"
PG_PASS="rag"
KEEP=0
for a in "$@"; do
  case "$a" in
    --keep) KEEP=1 ;;
    -h|--help) sed -n '2,40p' "$0"; exit 0 ;;
    *) echo "unknown arg: $a" >&2; exit 2 ;;
  esac
done

# ---------------------------------------------------------------- 基础工具
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
log()    { printf '[L4] %s\n' "$*"; }
fail_env() { printf '[L4] ❌ 环境不满足：%s\n' "$*" >&2; exit 2; }

for bin in docker mvn javac java python3; do
  command -v "$bin" >/dev/null 2>&1 || fail_env "缺少命令：$bin"
done
docker info >/dev/null 2>&1 || fail_env "docker daemon 不可用"

TMP="$(mktemp -d /tmp/l4-flyway-XXXXXX)"
RUNID="$$"
CONTAINER="rag-l4-flyway-pg-${RUNID}"
NETWORK="rag-l4-flyway-net-${RUNID}"

cleanup() {
  rc=$?
  set +u
  if [ "$KEEP" = "1" ]; then
    log "KEEP=1 ⇒ 保留容器 ${CONTAINER}（网络 ${NETWORK}），临时目录 ${TMP}"
  else
    docker rm -f "$CONTAINER" >/dev/null 2>&1
    docker network rm "$NETWORK" >/dev/null 2>&1
    python3 - "$TMP" <<'PY' >/dev/null 2>&1
import shutil, sys
shutil.rmtree(sys.argv[1], ignore_errors=True)
PY
  fi
  exit $rc
}
trap cleanup EXIT INT TERM

mkdir -p "$TMP/runs" "$TMP/logs" "$TMP/driver"

# ---------------------------------------------------------------- 场景定义
# 格式：  名称 | 先单独起的组(可空) | 之后并发起的组
#   · 「先单独起的组」= 空 ⇒ 全组同时起（真并发形态）
#   · 「之后并发起的组」= 至少两个模块 ⇒ 才有并发可言
SCENARIOS=(
  "S1-concurrent-triple||message like article"
  "S2-article-first|article|message like"
  "S3-message-first|message|like article"
  "S4-v1-starter-triple||comment follow task"
  # ⚠️ S5：favorite 是**已知阻塞缺陷**（yaml 缺 baseline-version: 0）⇒ 零触碰，只观测。
  #    形态必须是「**别的域先建过表**」才确定性地复现（空库首发时它自己就是第一个 ⇒ 看不到缺陷）
  #    ⇒ like 先单独起（把 public schema 弄非空），favorite 与 message 再并发。
  "S5-favorite-probe|like|favorite message"
)

MODULES="message like article comment follow task favorite"

# ---------------------------------------------------------------- classpath
CP_CACHE="$TMP/cp.txt"
: > "$CP_CACHE"
cp_for() {  # $1 = 模块名 ⇒ 打印该模块的 classpath（含自己的 resources）
  local m="$1"
  local out="$TMP/cp-$m.txt"
  if [ ! -f "$out" ]; then
    log "解析 classpath：service/${m}/${m}-rpc"
    ( cd "$ROOT" && mvn -o -q -pl "service/$m/$m-rpc" dependency:build-classpath \
        -Dmdep.outputFile="$out" -DincludeScope=runtime ) \
      || fail_env "mvn dependency:build-classpath 失败：$m"
  fi
  printf '%s:%s' "$(cat "$out")" "$ROOT/service/$m/$m-rpc/src/main/resources"
}

# 先解析一个模块的 classpath，用来编译驱动
FIRST_CP="$(cp_for message)"
# 预热全部模块的 classpath（并发子进程里直接读缓存 ⇒ 不会抢同一个文件）
for m in $MODULES; do
  cp_for "$m" >/dev/null
done
log "编译驱动 tools/difftest/flyway-concurrent-boot.java"
javac -cp "$FIRST_CP" -d "$TMP/driver" "$ROOT/tools/difftest/flyway-concurrent-boot.java" \
  || fail_env "驱动编译失败"

# ---------------------------------------------------------------- 起 PG
log "起独立 PG 16 容器：${CONTAINER}（网络 ${NETWORK}）"
docker network create "$NETWORK" >/dev/null || fail_env "docker network create 失败"
docker run -d --name "$CONTAINER" --network "$NETWORK" \
  -e POSTGRES_USER="$PG_USER" -e POSTGRES_PASSWORD="$PG_PASS" -e POSTGRES_DB=postgres \
  -p 127.0.0.1::5432 "$PG_IMAGE" >/dev/null || fail_env "docker run 失败"

PORT=""
for _ in $(seq 1 60); do
  PORT="$(docker port "$CONTAINER" 5432/tcp 2>/dev/null | head -1 | sed 's/.*://')"
  if [ -n "$PORT" ] && docker exec "$CONTAINER" pg_isready -U "$PG_USER" >/dev/null 2>&1; then
    break
  fi
  PORT=""
  sleep 1
done
[ -n "$PORT" ] || fail_env "PG 容器没就绪"
log "PG 16 就绪：127.0.0.1:$PORT"

# ---------------------------------------------------------------- 运行一组
# run_group <db> <module...>
#   全部进程等到**同一个墙钟瞬间**再起 Spring 上下文 ⇒ 真并发
run_group() {
  local db="$1"; shift
  local mods=("$@")
  local barrier=$(( $(now_ms) + 6000 ))
  local pids=""
  local m
  for m in "${mods[@]}"; do
    (
      POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PORT" POSTGRES_DB="$db" \
      POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASS" \
      java -cp "$TMP/driver:$(cp_for "$m")" FlywayConcurrentBoot migrate \
        --module="$m" --start-at="$barrier" --out="$TMP/runs/$m.json" \
        >"$TMP/logs/$m.out" 2>"$TMP/logs/$m.err"
      echo "$?" > "$TMP/runs/$m.rc"
    ) &
    pids="$pids $!"
  done
  local p
  for p in $pids; do wait "$p"; done
}

# ---------------------------------------------------------------- 跑场景
RESULTS="$TMP/results.json"
echo "[]" > "$RESULTS"

SCEN_JSON=""
for spec in "${SCENARIOS[@]}"; do
  name="$(echo "$spec" | cut -d'|' -f1)"
  first="$(echo "$spec" | cut -d'|' -f2)"
  second="$(echo "$spec" | cut -d'|' -f3)"
  db="l4_${name//-/_}"

  log "──────────────────────────────────────────────────────────"
  log "场景 $name   db=$db"
  log "  先起：${first:-（无）}    后并发：$second"

  docker exec "$CONTAINER" psql -U "$PG_USER" -d postgres -q -c "CREATE DATABASE \"$db\"" \
    || fail_env "建库失败：$db"

  if [ -n "$first" ]; then
    for m in $first; do
      log "  · 单独起 $m …"
      run_group "$db" "$m"
    done
  fi

  log "  · 并发起：$second"
  run_group "$db" $second

  # 落一份 inspect（表 / 索引 / 每本账本的行）
  java -cp "$TMP/driver:$FIRST_CP" FlywayConcurrentBoot inspect \
    --url="jdbc:postgresql://127.0.0.1:$PORT/$db" --user="$PG_USER" --pass="$PG_PASS" \
    --out="$TMP/inspect-$name.json" || fail_env "inspect 失败：$name"

  # 汇总本场景
  python3 - "$RESULTS" "$name" "$db" "$first" "$second" "$TMP" <<'PY'
import json, os, sys
results_path, name, db, first, second, tmp = sys.argv[1:7]
runs = []
for m in (first + " " + second).split():
    f = os.path.join(tmp, "runs", m + ".json")
    rc_f = os.path.join(tmp, "runs", m + ".rc")
    rec = {"module": m}
    if os.path.exists(f):
        with open(f, encoding="utf-8") as fh:
            rec.update(json.load(fh))
    rec["rc"] = int(open(rc_f).read().strip()) if os.path.exists(rc_f) else None
    runs.append(rec)
insp_f = os.path.join(tmp, "inspect-%s.json" % name)
inspect = {}
if os.path.exists(insp_f):
    with open(insp_f, encoding="utf-8") as fh:
        inspect = json.load(fh)
with open(results_path, encoding="utf-8") as fh:
    data = json.load(fh)
data.append({"name": name, "db": db, "first": first, "second": second,
             "runs": runs, "inspect": inspect})
with open(results_path, "w", encoding="utf-8") as fh:
    json.dump(data, fh, ensure_ascii=False, indent=1)
PY

  # 打印每个模块的迁移结果（一行一个）
  for m in $(echo "$first $second"); do
    if [ -f "$TMP/runs/$m.json" ]; then
      python3 - "$TMP/runs/$m.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
migs = d.get("migrations") or []
sql = [x for x in migs if x.get("type") == "SQL"]
base = [x for x in migs if x.get("type") == "BASELINE"]
if d.get("ok"):
    print("    ✅ %-10s ok  账本=%-32s baseline=%-3s SQL=%d 条  BASELINE行=%s  用时=%sms"
          % (d.get("module"), d.get("effectiveTable"), d.get("effectiveBaselineVersion"),
             len(sql), [b.get("version") for b in base], d.get("durationMs")))
else:
    print("    ❌ %-10s FAILED  %s" % (d.get("module"), (d.get("error") or "")[:220].replace("\n", " ")))
PY
    fi
  done
  SCEN_JSON="$SCEN_JSON $name"
done

# ---------------------------------------------------------------- 校验
log "──────────────────────────────────────────────────────────"
log "跑校验（tools/difftest/flyway-concurrent-verify.py）"
python3 "$ROOT/tools/difftest/flyway-concurrent-verify.py" \
  --root "$ROOT" --results "$RESULTS" --pg-port "$PORT" --pg-user "$PG_USER" --pg-pass "$PG_PASS"
VERIFY_RC=$?

log "──────────────────────────────────────────────────────────"
if [ "$VERIFY_RC" = "0" ]; then
  log "✅ 结论：所有场景都没有差异（三本账互不覆盖、各域的表都建出来了）"
else
  log "❌ 结论：有差异 ⇒ 见上面的逐条判定"
fi
exit "$VERIFY_RC"
