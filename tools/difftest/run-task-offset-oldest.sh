#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════
# task 域 · Kafka「首次消费无位点 ⇒ 从队头读（earliest）」活体对拍
#
#   目的（**只做这一件事**）：
#     Go 真源的三个 goka processor 显式设置
#     cfg.Consumer.Offsets.Initial = sarama.OffsetOldest
#     （service/task/rpc/internal/Init/initGoKa.go:131, 173, 207）
#     ⇒ 「group 从未提交过位点」时从**队头**开始读。
#     RAG 侧对应的三个 @KafkaListener 用 properties = "auto-offset-reset=earliest"
#     （TaskRawLogRouter / TaskUserCountConsumer / TaskArticleCountConsumer）。
#
#     另外三个 kq 消费者**相反**，是 latest：
#       · Go：硬编码 Offset: "latest"
#         （initKafkaRaw.go:32 / initUserFilter.go:47 / initArticleFilter.go:40）
#       · RAG：properties = "auto-offset-reset=latest"
#         （TaskRawDiagnosticConsumer / UserLikeProgressSink /
#           ArticleLikeProgressSink）
#
#   ⇒ 本脚本证明**两件事**：
#     【正向】goka 路（earliest）在「group 从未提交过位点」时**确实读到了预灌的消息**；
#     【阴性对照】kq 路（latest）在同样条件下**确实没读到**预灌的消息
#       —— 没有这条阴性对照，earliest 那条就可能是假绿（探针无区分力）。
#
#   用法：
#     ./tools/difftest/run-task-offset-oldest.sh
#     KEEP_UP=1 ./tools/difftest/run-task-offset-oldest.sh   # 跑完不拆服务（容器保留）
#     GO_REPO=/path/to/Sea-RideTheWind_golang ./…             # 自定义真源路径
#
#   ⚠️ 与 run-task-kafka-e2e.sh 的**关键差别**：本脚本**先灌消息、后起消费者**。
#      E2E 是「先起消费者、后灌消息」⇒ kq 的 latest 起点落在灌消息之前 ⇒
#      两侧都收得到 ⇒ E2E **测不到** earliest / latest 的差别。
#      本脚本把预灌消息放在消费者启动**之前** ⇒ earliest 必须能吃到队头，
#      latest 必须跳过 ⇒ 两者行为**可区分**。
#
# ⚠️ 坑（全部固化在注释里，别随手改）：
#   继承自 run-task-kafka-e2e.sh 的 6 条：
#   ① 容器内 Kafka CLI 必须用 **INTERNAL 监听器 localhost:19092**
#   ② 重置 Kafka 必须**重建容器**（删 topic 不删 __consumer_offsets 里的已提交位点）
#   ③ 还必须移走 goka 的**本地 LevelDB 缓存**（否则计数从上次 emit 的值继续）
#   ④ Go 侧要 **CGO_ENABLED=0** 才能编
#   ⑤ RAG 侧**不是 fat jar**，要用 `java -cp <classes>:<deps>`
#   ⑥ 容器内 CLI 的 key.separator **不能用 `|`**（会被 shell 当管道）
#
#   本脚本**新增**的坑：
#   ⑦ **docker compose 必须显式 `-p taskprobe`**（本轮实测踩到）
#      ⇒ compose 的项目名默认由**文件所在目录名**推导 ⇒ 本文件在 tools/difftest/
#      ⇒ 项目名会叫 `difftest` ⇒ 与 **user 域**的 docker-compose.yml
#      （容器名 difftest-postgres / difftest-redis）**撞成同一个 project**
#      ⇒ `up -d` 会把 user 域的容器当同 project 的服务**直接 Recreate**（连数据卷一起没），
#      `down -v` 更狠 ⇒ **删掉 user 域的 PG 数据卷**。
#      ⇒ 本脚本所有 compose 调用都走 `-p taskprobe`（独立 project，互不干扰）。
#      ⚠️ compose 文件里现已另有 `name: taskprobe`（同值 ⇒ 不冲突），
#         本脚本仍显式带 `-p`：即便哪天 `name:` 被删掉，本脚本也不会误伤别的域。
#   ⑧ `$var` 紧跟多字节字符在 `set -euo pipefail` 下会硬失败 ⇒ 一律写 `${var}`
#      （tools/difftest/run.sh 曾因此返回过非零）
#   ⑨ kafka CLI 前缀必须写成**函数**（写成字符串变量再展开 ⇒
#      「把 /opt/kafka/bin 当可执行文件」⇒ **静默失败**，删/建 topic 两步都不生效）
#   ⑩ 生产者要读 stdin ⇒ `kafka()` 没有 `-i` ⇒ 另写一个 `kafka_in()`
#   ⑪ console-producer 灌**带 key** 的消息要 `parse.key=true` + `key.separator=,`
#      （值必须是**十进制 ASCII 的 int64**：goka codec.Int64 的格式，
#        goka/codec/codec.go:56 = []byte(strconv.FormatInt(intVal,10))）
#   ⑫ `set -e` 下 `grep -c` 命中 0 条返回 1 ⇒ 必须 `|| true`
#   ⑬ **预灌消息必须确认已落到 log**（log-end-offset 达标）才能起消费者
#      ⇒ 否则 kq 的 latest 起点可能落在预灌消息**之前** ⇒ 阴性对照失效
#   ⑭ goka 的 compacted table topic 里同一 key 有**多个版本** ⇒ 要取**最后一个值**
#      （读法同 parity-task-kafka.py:123-142）
#   ⑮ A6/A7 有**已知竞态**：kq 的 latest 起点在它 join group 时就定死
#      ⇒ 若 goka 抢先 emit，kq 会跳过 ⇒ A6/A7 失败（但 A1~A4 仍成立）
# ═════════════════════════════════════════════════════════════════════════
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
RAG_REPO=${RAG_REPO:-$(cd "$HERE/../.." && pwd)}
WORK=${WORK:-${TMPDIR:-/tmp}/task-offset-oldest}
KEEP_UP=${KEEP_UP:-0}
GO_REPO_CANDIDATE=${GO_REPO:-}

# ⚠️ ⑦ 独立 project 名：**必须**带 -p taskprobe，否则会误删 user 域的容器与数据卷
COMPOSE=(docker compose -p taskprobe -f "$HERE/docker-compose.task-kafka.yml")

KAFKA_BOOT=localhost:19092          # ① 容器内必须用 INTERNAL 监听器
PG_PORT=55433
REDIS_PORT=56380
KAFKA_PORT=59992
GO_REDIS_DB=0                       # Go 侧 LikeRedis.DB: 0
RAG_REDIS_DB=1                      # RAG 侧 --spring.data.redis.database=1

GO_TOPICS=(task.logs.raw task.logs.raw.User task.logs.raw.Article
           task.logs.filtered.User task.logs.filtered.Article)
RAG_TOPICS=(rag.task.logs.raw rag.task.logs.raw.User rag.task.logs.raw.Article
            rag.task.logs.filtered.User rag.task.logs.filtered.Article)

# ⚠️ 只移这两个：Go 侧 goka 里**带 Persist 的两个组**
#    （initGoKa.go:167 用户路 / :202 文章路）。
#    主组 consumer41 没有 goka.Persist ⇒ 没有存量计数 ⇒ 不必动（少动别人的目录）。
GOKA_CACHE_GROUPS=(task.rpc.consumer61 task.rpc.consumer71)
GOKA_CACHE_DIR=${GOKA_CACHE_DIR:-/private/tmp/goka/processor}

GO_GROUPS=(task.rpc.consumer31 task.rpc.consumer41 task.rpc.consumer61
           task.rpc.consumer71 task.rpc.consumer81 task.rpc.consumer101)
RAG_GROUPS=(rag.rpc.consumer31 rag.rpc.consumer41 rag.rpc.consumer61
            rag.rpc.consumer71 rag.rpc.consumer81 rag.rpc.consumer101)

# ═══════════════════════════════════════════════════════════════════════
# 工具函数
# ═══════════════════════════════════════════════════════════════════════
say() { printf '\n\033[1m==== %s ====\033[0m\n' "$*"; }
note() { printf '  %s\n' "$*"; }

# ⚠️ ⑨ kafka CLI 前缀必须写成**函数**（不能是字符串变量）
kafka() { docker exec taskprobe-kafka /opt/kafka/bin/"$@"; }
# ⚠️ ⑩ 生产者要读 stdin ⇒ 必须带 -i，kafka() 里没有 ⇒ 单独一个
kafka_in() { docker exec -i taskprobe-kafka /opt/kafka/bin/"$@"; }

# ⚠️ 宿主机没有 redis-cli 时退回容器内（容器内端口是 6379，**不能**再带 -p）
REDIS_CLI_MODE=host
command -v redis-cli >/dev/null 2>&1 || REDIS_CLI_MODE=container
redis() {
  if [ "${REDIS_CLI_MODE}" = "container" ]; then
    docker exec taskprobe-redis redis-cli "$@"
  else
    redis-cli -p "${REDIS_PORT}" "$@"
  fi
}

# 取 Redis 里的值；**不存在** ⇒ 打印 <absent>（而不是空串，避免与「值为空」混淆）
redis_get_or_absent() {
  local db="$1" key="$2" v
  v=$(redis -n "${db}" --raw GET "${key}" 2>/dev/null || true)
  if [ -z "${v}" ] || [ "${v}" = "(nil)" ]; then
    printf '<absent>'
  else
    printf '%s' "${v}"
  fi
}

# ⚠️ ⑭ 读 Go 侧 goka 的 compacted table topic ⇒ 该 key 的**最后一个值**
#    （同一 key 有多个版本 ⇒ 取最后一次出现）。
#    key.separator 用 `,`（**不能用 `|`**：会被 shell 当管道）。
#    参照 parity-task-kafka.py:123-142 的同款读法。
goka_table_value() {
  local topic="$1" key="$2" raw
  raw=$(docker exec taskprobe-kafka /opt/kafka/bin/kafka-console-consumer.sh \
          --bootstrap-server "${KAFKA_BOOT}" --topic "${topic}" --from-beginning \
          --timeout-ms 8000 --property print.key=true --property print.value=true \
          --property key.separator=, 2>/dev/null || true)
  printf '%s\n' "${raw}" | python3 -c '
import sys
want = sys.argv[1]
last = ""
for line in sys.stdin:
    line = line.rstrip("\n")
    if "," not in line:
        continue
    k, v = line.rsplit(",", 1)
    if k == want:
        last = v
sys.stdout.write(last)
' "${key}"
}

# topic 的 log-end-offset（= 已落 log 的消息总数，单分区）
topic_log_end() {
  local topic="$1" v
  # ⚠️ ⑫ 管道里 grep -v WARN 命中 0 行时返回 1 ⇒ pipefail 会让整条管道失败
  #    ⇒ 赋值语句失败会触发 errexit ⇒ 必须兜底
  v=$(kafka kafka-get-offsets.sh --bootstrap-server "${KAFKA_BOOT}" --topic "${topic}" 2>/dev/null \
        | grep -v WARN | awk -F: 'NF>=3 {s+=$NF} END {print s+0}' || true)
  printf '%s' "${v}"
}

# ⚠️ ⑬ 等 topic 里的消息**真的落到 log**（log-end-offset 达标）才继续
wait_topic_count() {
  local topic="$1" expect="$2" timeout="$3" i n=0
  for i in $(seq 1 "${timeout}"); do
    n=$(topic_log_end "${topic}")
    if [ "${n}" -ge "${expect}" ]; then
      note "✓ topic ${topic} 已落 log ${n} 条（期望 ≥ ${expect}）"
      return 0
    fi
    sleep 1
  done
  note "✗ topic ${topic} 在 ${timeout}s 内未达到 ${expect} 条（log-end-offset=${n}）"
  return 1
}

# consumer group 的 CURRENT-OFFSET 之和（= 该 group 已经消费到的位置）
group_current_offset() {
  local g="$1" v
  # ⚠️ ⑫ grep/awk 管道里任一段返回非 0 都会让赋值失败 ⇒ 兜底
  v=$(kafka kafka-consumer-groups.sh --bootstrap-server "${KAFKA_BOOT}" --describe --group "${g}" 2>/dev/null \
        | grep -v WARN | awk -v g="${g}" '$1==g {s+=$4} END {print s+0}' || true)
  printf '%s' "${v}"
}

# ═══════════════════════════════════════════════════════════════════════
# 断言框架（所有实测值都要打印，不能只打 PASS/FAIL）
# ═══════════════════════════════════════════════════════════════════════
FAILURES=0
ASSERT_IDS=()
ASSERT_LABELS=()
ASSERT_VALUES=()
ASSERT_RESULTS=()

pass() { printf '  \033[32m✓ PASS\033[0m  %s\n' "$*"; }
fail() {
  FAILURES=$((FAILURES + 1))
  printf '  \033[31m✗ FAIL\033[0m  %s\n' "$*"
}

record() {
  # $1=编号 $2=观测点 $3=实测值 $4=PASS|FAIL
  ASSERT_IDS+=("$1")
  ASSERT_LABELS+=("$2")
  ASSERT_VALUES+=("$3")
  ASSERT_RESULTS+=("$4")
  if [ "$4" = "PASS" ]; then
    pass "[$1] $2 ⇒ 实测=${3}"
  else
    fail "[$1] $2 ⇒ 实测=${3}"
  fi
}

# 两侧同一 key 的值是否都等于期望；超时则把最后实测值放进 GO_OBS / RAG_OBS
GO_OBS=""
RAG_OBS=""
poll_pair_eq() {
  # $1=Go db $2=RAG db $3=key $4=期望 $5=超时秒
  local godb="$1" ragdb="$2" key="$3" expected="$4" timeout="$5"
  local deadline=$((SECONDS + timeout))
  GO_OBS=""
  RAG_OBS=""
  while :; do
    GO_OBS=$(redis_get_or_absent "${godb}" "${key}")
    RAG_OBS=$(redis_get_or_absent "${ragdb}" "${key}")
    if [ "${GO_OBS}" = "${expected}" ] && [ "${RAG_OBS}" = "${expected}" ]; then
      return 0
    fi
    if [ "${SECONDS}" -ge "${deadline}" ]; then
      return 1
    fi
    sleep 2
  done
}

# ═══════════════════════════════════════════════════════════════════════
say "0. 前置检查"
for c in docker go mvn java python3; do
  command -v "$c" >/dev/null 2>&1 || { note "✗ 缺命令：${c}"; exit 1; }
done

# ⚠️ Go 真源路径的推导**不能**假设「与 RAG 同级」：
#    worktree 的布局是 `<RAG.worktrees>/task`，与主仓 `<RAG>` 不同级。
# ⇒ 多候选搜索，取第一个含 service/task/rpc 的。
find_go_repo() {
  local cands=()
  [ -n "${GO_REPO:-}" ] && cands+=("$GO_REPO")
  cands+=("$RAG_REPO/../Sea-RideTheWind_golang")
  cands+=("$RAG_REPO/../../Sea-RideTheWind_golang")
  # worktree 的 git-common-dir 指向**主仓**的 .git ⇒ 两级 dirname 得到主仓的同级目录
  local common
  common=$(git -C "$RAG_REPO" rev-parse --git-common-dir 2>/dev/null) && \
    cands+=("$(cd "$(dirname "$common")/.." && pwd)/Sea-RideTheWind_golang")
  for c in "${cands[@]}"; do
    [ -d "$c/service/task/rpc" ] && { (cd "$c" && pwd); return 0; }
  done
  return 1
}

GO_REPO=$(GO_REPO="$GO_REPO_CANDIDATE" find_go_repo) \
  || { note "✗ 找不到 Go 真源（可用 GO_REPO=... 指定）"; exit 1; }
note "RAG_REPO = ${RAG_REPO}"
note "GO_REPO  = ${GO_REPO}"
note "WORK     = ${WORK}"
note "redis-cli = ${REDIS_CLI_MODE}"
mkdir -p "$WORK/bin" "$WORK/logs"

# ═══════════════════════════════════════════════════════════════════════
say "1. 编 Go 真源二进制（④ CGO_ENABLED=0）"
( cd "$GO_REPO" && CGO_ENABLED=0 go build -o "$WORK/bin/go-task-rpc" ./service/task/rpc ) \
  || { note "✗ Go 编译失败"; exit 1; }
note "✓ ${WORK}/bin/go-task-rpc"

# ═══════════════════════════════════════════════════════════════════════
say "2. 编 RAG 侧（⑤ 不是 fat jar ⇒ classes + 依赖 classpath）"
( cd "$RAG_REPO" && mvn -o -q -pl service/task/task-rpc -am compile -DskipTests ) \
  || { note "✗ RAG 编译失败"; exit 1; }
( cd "$RAG_REPO" && mvn -o -q -pl service/task/task-rpc dependency:build-classpath \
    -Dmdep.outputFile="$WORK/rag-cp.txt" -DincludeScope=runtime ) \
  || { note "✗ classpath 生成失败"; exit 1; }
[ -s "$WORK/rag-cp.txt" ] || { note "✗ classpath 文件为空：${WORK}/rag-cp.txt"; exit 1; }
RAG_CP="$RAG_REPO/service/task/task-rpc/target/classes:$(cat "$WORK/rag-cp.txt")"
note "✓ classpath $(wc -c < "$WORK/rag-cp.txt" | tr -d ' ') 字节"

# ═══════════════════════════════════════════════════════════════════════
say "3. 起基建（postgres + redis + kafka）"
# ⚠️ 先拆干净：现存同名容器若是**手工 docker run** 建的（不是本 compose 管的），
#    compose 会直接报 name conflict；而 postgres 的数据卷也必须一起丢
#    （initdb-task.sql 只在**首次**初始化时跑，卷留着就永远补建不出 task_rag_db）。
# ⚠️ ⑦ 所有 compose 调用都带 -p taskprobe ⇒ 不会碰到 user 域的 difftest-* 容器/卷
"${COMPOSE[@]}" down -v >/dev/null 2>&1 || true
for c in taskprobe-postgres taskprobe-redis taskprobe-kafka; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
    docker stop "$c" >/dev/null 2>&1 || true
    docker rm "$c" >/dev/null 2>&1 || true
    note "✓ 移除同名遗留容器 ${c}"
  fi
done

"${COMPOSE[@]}" up -d
for i in $(seq 1 60); do
  st=$(docker inspect -f '{{.State.Health.Status}}' taskprobe-kafka 2>/dev/null || true)
  [ "$st" = "healthy" ] && { note "✓ Kafka healthy（${i} 次轮询）"; break; }
  sleep 2
done
"${COMPOSE[@]}" ps --format '  {{.Name}}\t{{.Status}}' || true

# ⚠️ postgres / redis 也等 health：initdb-task.sql 只在**首次**初始化时跑，
#    容器刚起来时 task_rag_db 可能还没建好 ⇒ 后面起服务会连不上库。
for c in taskprobe-postgres taskprobe-redis; do
  for i in $(seq 1 60); do
    st=$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null || true)
    [ "$st" = "healthy" ] && { note "✓ ${c} healthy（${i} 次轮询）"; break; }
    sleep 2
  done
done

# ═══════════════════════════════════════════════════════════════════════
say "4. 重置状态（② 重建 Kafka 容器 + ③ 移走 goka 本地缓存 + 清 Redis/PG）"
"${COMPOSE[@]}" up -d --force-recreate kafka >/dev/null
for i in $(seq 1 60); do
  st=$(docker inspect -f '{{.State.Health.Status}}' taskprobe-kafka 2>/dev/null || true)
  [ "$st" = "healthy" ] && break
  sleep 2
done
note "✓ Kafka 容器已重建（⇒ 无 topic、无已提交位点、无 consumer group）"

STAMP=$(date +%s)
for g in "${GOKA_CACHE_GROUPS[@]}"; do
  d="$GOKA_CACHE_DIR/$g"
  if [ -d "$d" ]; then
    # ⚠️ 用 mv 不用 rm：/private/tmp/goka 下可能有别的会话在用的 group
    mv "$d" "$GOKA_CACHE_DIR/${g}.stale.${STAMP}"
    note "✓ 移走 goka 本地缓存 ${g}"
  fi
done

redis -n "${GO_REDIS_DB}" FLUSHDB >/dev/null || true
redis -n "${RAG_REDIS_DB}" FLUSHDB >/dev/null || true
for db in task_go_db task_rag_db; do
  docker exec taskprobe-postgres psql -U admin -d "$db" -c \
    "TRUNCATE task_progress, user_like_count;" >/dev/null 2>&1 || true
done
note "✓ Redis db0/db1 已清、两张 PG 表已 TRUNCATE（表还不存在时跳过）"

note "--- 重建后的 Kafka 应为空（无 topic / 无 consumer group）---"
listing=$(kafka kafka-consumer-groups.sh --bootstrap-server "$KAFKA_BOOT" --list 2>/dev/null | grep -v WARN || true)
note "consumer groups = [${listing}]"
tl=$(kafka kafka-topics.sh --bootstrap-server "$KAFKA_BOOT" --list 2>/dev/null | grep -v WARN || true)
note "topics = [${tl}]"

# ═══════════════════════════════════════════════════════════════════════
say "5. 建 10 个 topic（脚本建，不让 goka 建 —— 下一步要往里预灌消息）"
create_topic() {
  local t="$1" out
  if out=$(kafka kafka-topics.sh --bootstrap-server "$KAFKA_BOOT" --create --topic "${t}" \
            --partitions 1 --replication-factor 1 2>&1); then
    note "✓ 创建 topic ${t}"
  elif printf '%s' "${out}" | grep -q 'already exists'; then
    note "· topic ${t} 已存在（跳过）"
  else
    note "✗ 创建 topic ${t} 失败：${out}"
    return 1
  fi
}
for t in "${GO_TOPICS[@]}" "${RAG_TOPICS[@]}"; do
  create_topic "${t}"
done

# ═══════════════════════════════════════════════════════════════════════
# ⚠️ ⑯ 端口不能写死 —— **别的域的并发会话可能正占着它**。
#    2026-10-24 实测：user 域的探针进程 `go-user-rpc` 正占着 19005
#    ⇒ 本脚本的 Go 侧 task-rpc 直接 `bind: address already in use` 退出。
#    ⇒ 绝不能去 kill 别人的进程（那是别的域的基建）⇒ 只能**自己换端口**。
port_busy() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

# 从 $1 起找一个空闲端口（最多往后找 60 个）
pick_free_port() {
    local base=$1 port
    for port in $(seq "$base" $((base + 60))); do
        if ! port_busy "$port"; then
            echo "$port"
            return 0
        fi
    done
    return 1
}

say "5b. 选空闲端口（⑯ 别的域可能占着默认端口）"
GO_PORT=$(pick_free_port 19005) || { note "x 19005~19065 找不到空闲端口"; exit 1; }
RAG_PORT=$(pick_free_port 59005) || { note "x 59005~59065 找不到空闲端口"; exit 1; }
note "Go 侧 gRPC : ${GO_PORT}（默认 19005）"
note "RAG 侧 gRPC: ${RAG_PORT}（默认 59005）"
if [ "${GO_PORT}" != "19005" ] || [ "${RAG_PORT}" != "59005" ]; then
    note "⚠️ 默认端口被占用（很可能是**别的域**的并发会话）⇒ 已自动改用上面的端口。"
    note "   ⇒ 绝不 kill 别人的进程。"
fi
# Go 侧端口来自 conf/go/task-rpc.yaml 的 `ListenOn` ⇒ 生成一份改过端口的副本
GO_YAML="$WORK/conf/task-rpc-probe.yaml"
mkdir -p "$(dirname "${GO_YAML}")"
sed "s#^ListenOn: .*#ListenOn: 0.0.0.0:${GO_PORT}#" "$HERE/conf/go/task-rpc.yaml" > "$GO_YAML"
note "✓ Go 侧配置副本：${GO_YAML}（ListenOn 改为 ${GO_PORT}）"

say "6. 停掉可能残留的旧进程"
# ⚠️ 只杀**本 WORK 目录**起的那些：别的域也起 task-rpc（甚至同名二进制）时不能误杀。
pkill -f "${WORK}/bin/go-task-rpc" 2>/dev/null || true
pkill -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null || true
sleep 2
pkill -9 -f "${WORK}/bin/go-task-rpc" 2>/dev/null || true
pkill -9 -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null || true
sleep 1
note "剩余进程数：$(pgrep -f 'go-task-rpc|TaskRpcApplication' | wc -l | tr -d ' ' || true)"

# ═══════════════════════════════════════════════════════════════════════
say "7. 【阶段 B】先灌消息，再起消费者 —— goka 路的输入"
cat > "$WORK/raw-batch.jsonl" <<'EOF'
{"ts":"2026-10-23T15:00:01.000Z","service":"like-service","event_id":"b01","user_id":"5001","trace_id":"tb1","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:02.000Z","service":"like-service","event_id":"b02","user_id":"5001","trace_id":"tb2","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:03.000Z","service":"like-service","event_id":"b03","user_id":"5001","trace_id":"tb3","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:04.000Z","service":"like-service","event_id":"b04","user_id":"5001","trace_id":"tb4","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:05.000Z","service":"like-service","event_id":"b05","user_id":"5001","trace_id":"tb5","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:06.000Z","service":"like-service","event_id":"b06","user_id":"5002","trace_id":"tb6","article_id":"9002","msg":"like"}
{"ts":"2026-10-23T15:00:07.000Z","service":"like-service","event_id":"b07","user_id":"5002","trace_id":"tb7","article_id":"9002","msg":"like"}
EOF
note "批次：user 5001 × 5（恰好达到 target=5 ⇒ 完成分支）、user 5002 × 2（doing 分支）"
note "每条消息的 article_id：5001→9001（5 条）、5002→9002（2 条）"
for t in task.logs.raw rag.task.logs.raw; do
  # ⚠️ ⑪ acks=1：确认消息已提交到 log 再往下走
  kafka_in kafka-console-producer.sh --bootstrap-server "$KAFKA_BOOT" --topic "${t}" \
    --producer-property acks=1 < "$WORK/raw-batch.jsonl"
  note "✓ 已灌入 ${t}（7 条 LogEvent JSON）"
done
# ⚠️ ⑬ 确认已落 log（否则 kq 的 latest 起点可能落在这些消息之前）
wait_topic_count task.logs.raw 7 30
wait_topic_count rag.task.logs.raw 7 30

say "8. 【阶段 B】预灌 kq 路的输入 —— 阴性对照的种子"
# ⚠️ ⑪ 值必须是**十进制 ASCII 的 int64**（goka codec.Int64 的格式）
#    ⇒ sink 的 strconv.ParseInt(val,10,64) 才能解析出 total=42
# ⚠️ 这条消息**故意**在消费者启动前就存在 ⇒ kq 的 latest 生效 ⇒ 它不会被消费
for t in task.logs.filtered.User rag.task.logs.filtered.User; do
  printf '7777,42\n' | kafka_in kafka-console-producer.sh --bootstrap-server "$KAFKA_BOOT" \
    --topic "${t}" --producer-property acks=1 \
    --property parse.key=true --property key.separator=,
  note "✓ 已灌入 ${t}：key=7777 value=42（十进制 ASCII）"
done
wait_topic_count task.logs.filtered.User 1 30
wait_topic_count rag.task.logs.filtered.User 1 30

# ═══════════════════════════════════════════════════════════════════════
say "9. 起 Go 真源 task-rpc (:${GO_PORT})"
( cd "$WORK" && nohup "$WORK/bin/go-task-rpc" \
      -f "$GO_YAML" > "$WORK/logs/go-task-rpc.log" 2>&1 & )
note "✓ 已起"

say "10. 起 RAG task-rpc (:${RAG_PORT})"
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_DB=task_rag_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=difftest \
REDIS_HOST=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
KAFKA_BROKERS=127.0.0.1:"$KAFKA_PORT" \
NACOS_ADDR=127.0.0.1:1 OTEL_EXPORTER_OTLP_ENDPOINT=127.0.0.1:1 \
nohup java -cp "$RAG_CP" com.rag.task.task.rpc.TaskRpcApplication \
    --grpc.server.port="${RAG_PORT}" \
    --spring.cloud.nacos.discovery.register-enabled=false \
    --spring.data.redis.database=1 \
    --task.kafka.in-topic=rag.task.logs.raw \
    --task.kafka.raw-user-topic=rag.task.logs.raw.User \
    --task.kafka.raw-article-topic=rag.task.logs.raw.Article \
    --task.kafka.out-user-topic=rag.task.logs.filtered.User \
    --task.kafka.out-article-topic=rag.task.logs.filtered.Article \
    --task.kafka.group-kafka-raw=rag.rpc.consumer31 \
    --task.kafka.group-goka=rag.rpc.consumer41 \
    --task.kafka.group-goka-user=rag.rpc.consumer61 \
    --task.kafka.group-goka-article=rag.rpc.consumer71 \
    --task.kafka.group-filter-user=rag.rpc.consumer81 \
    --task.kafka.group-kafka-filter=rag.rpc.consumer101 \
    > "$WORK/logs/rag-task-rpc.log" 2>&1 &
note "✓ 已起"

sleep 8
if ! pgrep -f "${WORK}/bin/go-task-rpc" >/dev/null 2>&1; then
  note "✗ Go 真源进程已退出，日志尾部："
  tail -n 40 "$WORK/logs/go-task-rpc.log" || true
  exit 1
fi
if ! pgrep -f 'com.rag.task.task.rpc.TaskRpcApplication' >/dev/null 2>&1; then
  note "✗ RAG 进程已退出，日志尾部："
  tail -n 40 "$WORK/logs/rag-task-rpc.log" || true
  exit 1
fi
note "✓ 两侧进程都在"

# ═══════════════════════════════════════════════════════════════════════
say "11. 等两侧 12 个 consumer group 全部就位"
# ⚠️ 等待条件必须包含 **kq 的 consumer group**，不能只看 goka：
#   · Go 侧 6 个 group 里有 3 个是 goka（有 "setup generation" 那行日志）、
#     3 个是 kq（**没有**那行日志 ⇒ 只等 goka 会漏）；
#   · kq 的 Offset 是**硬编码 "latest"**（initUserFilter.go:47 / initArticleFilter.go:40）
#     ⇒ 它在 join group 时把起点定在**当时**的队尾 ⇒ 若消息灌得太早，
#     它会从队尾之后开始 ⇒ **一条都收不到**。
groups_ready=0
for i in $(seq 1 60); do
  listing=$(kafka kafka-consumer-groups.sh --bootstrap-server "$KAFKA_BOOT" --list 2>/dev/null | grep -v WARN || true)
  missing=""
  for g in "${GO_GROUPS[@]}" "${RAG_GROUPS[@]}"; do
    if printf '%s\n' "${listing}" | grep -qx "${g}"; then :; else missing="${missing} ${g}"; fi
  done
  go_err=$(grep -c 'error occurred\|local cache' "$WORK/logs/go-task-rpc.log" 2>/dev/null || true)
  go_err=${go_err:-0}
  note "[${i}] 缺失 group:${missing:- 无}  go_errors=${go_err}"
  if [ -z "${missing}" ] && [ "${go_err}" -eq 0 ]; then
    sleep 3      # grace period：让 kq 完成 offset 解析并把起点定在当时的队尾
    groups_ready=1
    note "✓ 两侧 12 个 group 全部就位（+3s grace period）"
    break
  fi
  sleep 2
done
if [ "${groups_ready}" -ne 1 ]; then
  note "✗ 12 个 group 未能在 120s 内全部就位"
  note "--- Go 日志尾部 ---"; tail -n 40 "$WORK/logs/go-task-rpc.log" || true
  note "--- RAG 日志尾部 ---"; tail -n 40 "$WORK/logs/rag-task-rpc.log" || true
  exit 1
fi

# ═══════════════════════════════════════════════════════════════════════
say "12. 等 sink 的 1 秒 flush 落库"
for i in $(seq 1 15); do
  sleep 2
  n=$(redis -n "${RAG_REDIS_DB}" --scan | grep -c '^like:total:' || true); n=${n:-0}
  m=$(redis -n "${GO_REDIS_DB}" --scan | grep -c '^like:total:' || true); m=${m:-0}
  note "[${i}] like:total 数量 Go=${m} RAG=${n}"
  if [ "${m}" -ge 2 ] && [ "${n}" -ge 2 ]; then
    note "✓ 两侧都已有 like:total"
    break
  fi
done
note "（这一步只是「落库了没」的体检；真正的判定在阶段 C 的断言里 ——"
note "  like:total 可能因为下面的 A6/A7 竞态而少于 2，不在此处判死。）"

# ═══════════════════════════════════════════════════════════════════════
say "13. 【阶段 C】断言"

# ---- A1：Go goka 用户路的 compacted table topic，key=5001 的最后一个值 ----
A1=$(goka_table_value task.rpc.consumer61-table 5001)
note "Go topic task.rpc.consumer61-table 全量（key,value）："
kafka kafka-console-consumer.sh --bootstrap-server "$KAFKA_BOOT" \
  --topic task.rpc.consumer61-table --from-beginning --timeout-ms 3000 \
  --property print.key=true --property print.value=true --property key.separator=, \
  2>/dev/null | grep -v WARN | sed 's/^/    /' || true
if [ "${A1}" = "5" ]; then
  record A1 "Go goka 用户路 table topic task.rpc.consumer61-table key=5001" "${A1}" PASS
else
  record A1 "Go goka 用户路 table topic task.rpc.consumer61-table key=5001" "${A1}" FAIL
fi

# ---- A2：RAG goka 用户路的 Redis 计数 ----
A2=$(redis_get_or_absent "${RAG_REDIS_DB}" "task:goka:count:rag.rpc.consumer61:5001")
if [ "${A2}" = "5" ]; then
  record A2 "RAG goka 用户路 Redis task:goka:count:rag.rpc.consumer61:5001" "${A2}" PASS
else
  record A2 "RAG goka 用户路 Redis task:goka:count:rag.rpc.consumer61:5001" "${A2}" FAIL
fi

# ---- A3：Go goka 文章路的 compacted table topic，key=9001 的最后一个值 ----
A3=$(goka_table_value task.rpc.consumer71-table 9001)
note "Go topic task.rpc.consumer71-table 全量（key,value）："
kafka kafka-console-consumer.sh --bootstrap-server "$KAFKA_BOOT" \
  --topic task.rpc.consumer71-table --from-beginning --timeout-ms 3000 \
  --property print.key=true --property print.value=true --property key.separator=, \
  2>/dev/null | grep -v WARN | sed 's/^/    /' || true
if [ "${A3}" = "5" ]; then
  record A3 "Go goka 文章路 table topic task.rpc.consumer71-table key=9001" "${A3}" PASS
else
  record A3 "Go goka 文章路 table topic task.rpc.consumer71-table key=9001" "${A3}" FAIL
fi

# ---- A4：RAG goka 文章路的 Redis 计数 ----
A4=$(redis_get_or_absent "${RAG_REDIS_DB}" "task:goka:count:rag.rpc.consumer71:9001")
if [ "${A4}" = "5" ]; then
  record A4 "RAG goka 文章路 Redis task:goka:count:rag.rpc.consumer71:9001" "${A4}" PASS
else
  record A4 "RAG goka 文章路 Redis task:goka:count:rag.rpc.consumer71:9001" "${A4}" FAIL
fi

# ---- 诊断：关键 consumer group 的位点（阴性对照的**直接证据**）----
say "13b. 关键 consumer group 的位点（阴性对照的直接证据）"
for g in task.rpc.consumer61 task.rpc.consumer81 rag.rpc.consumer61 rag.rpc.consumer81; do
  note "--- group ${g}（CURRENT-OFFSET 之和 = $(group_current_offset "${g}")）---"
  kafka kafka-consumer-groups.sh --bootstrap-server "${KAFKA_BOOT}" --describe --group "${g}" \
    2>/dev/null | grep -v WARN | sed 's/^/    /' || true
done
note "--- topic log-end-offset ---"
for t in task.logs.filtered.User rag.task.logs.filtered.User task.logs.raw rag.task.logs.raw; do
  note "  ${t} = $(topic_log_end "${t}")"
done
note "（预灌的 7777=42 落在 filtered.User 的 offset 0；"
note "  kq 的 CURRENT-OFFSET > 0 ⇒ 它的起点在 join group 时的队尾 ⇒ **跳过了**那条。）"

# ---- A5：阴性对照 —— kq（latest）必须**跳过**预灌的 7777=42 ----
A5_GO=$(redis_get_or_absent "${GO_REDIS_DB}" "like:total:7777")
A5_RAG=$(redis_get_or_absent "${RAG_REDIS_DB}" "like:total:7777")
note "Go  db0 like:total:7777 = ${A5_GO}"
note "RAG db1 like:total:7777 = ${A5_RAG}"
if [ "${A5_GO}" = "<absent>" ] && [ "${A5_RAG}" = "<absent>" ]; then
  record A5 "阴性对照：kq(latest) 两侧 like:total:7777 均不存在" "Go=${A5_GO} RAG=${A5_RAG}" PASS
else
  record A5 "阴性对照：kq(latest) 两侧 like:total:7777 均不存在" "Go=${A5_GO} RAG=${A5_RAG}" FAIL
  note "⚠️ 说明：latest 没生效（或预灌消息没落到 offset 0），"
  note "   但这**同时**意味着 A8 的阳性对照会失败 ⇒ kq 路整体没按 latest 走。"
fi

# ---- A6 / A7 已移到【阶段 D】----
# ⚠️ ⑮ 原来的 A6/A7 放在这里会踩一个**必然踩中的竞态**：
#    kq 消费者的 latest 起点在它 join group 的那一刻定死。
#    本脚本是「先灌消息、再起消费者」⇒ goka 一 join 就把预灌的消息 emit 到 filtered.*
#    ⇒ 若 goka 比 kq 先 join，kq 的起点就在这些消息**之后** ⇒ 跳过 ⇒ A6/A7 必失败。
#    ⚠️ 而这个失败**与 earliest 无关**（A1~A4 才是 earliest 的判据）⇒ 放这里只会制造噪声。
# ⇒ 改法：A6/A7 挪到阶段 D，用「**消费者全部就位之后**再灌的 raw 消息」驱动端到端 ——
#    那样 kq 必然消费得到（消息产生于它 join 之后）⇒ **无竞态**。
#    ⭐ 而且期望值因此变得更强：like:total:5001 = 10（预灌 5 条 + 后灌 5 条）
#    ⇒ **这个 10 本身就是「预灌那 5 条也被计入了」的端到端证据**。

# ---- 诊断：把 4 个关键 group 的位点打出来（人工复核用）----
say "13c. 关键 consumer group 的位点（A6/A7 之后的最终态）"
for g in task.rpc.consumer61 task.rpc.consumer81 rag.rpc.consumer61 rag.rpc.consumer81; do
  note "--- group ${g}（CURRENT-OFFSET 之和 = $(group_current_offset "${g}")）---"
  kafka kafka-consumer-groups.sh --bootstrap-server "$KAFKA_BOOT" --describe --group "${g}" \
    2>/dev/null | grep -v WARN | sed 's/^/    /' || true
done
note "--- topic log-end-offset ---"
for t in task.logs.filtered.User rag.task.logs.filtered.User task.logs.raw rag.task.logs.raw; do
  note "  ${t} = $(topic_log_end "${t}")"
done

# ═══════════════════════════════════════════════════════════════════════
say "14. 【阶段 D】消费者就位之后再投递 —— 端到端链 + 阳性对照"
# ⚠️ 这一段的所有投递都发生在「两侧 12 个 group 全部就位」之后
#    ⇒ kq 的 latest 起点已经定在**当时**的队尾 ⇒ 这些消息必然被消费 ⇒ **无竞态**。

# ---- 14a. 阳性对照：直接往 kq 的输入 topic 投一条 ----
for t in task.logs.filtered.User rag.task.logs.filtered.User; do
  printf '8888,42\n' | kafka_in kafka-console-producer.sh --bootstrap-server "$KAFKA_BOOT" \
    --topic "${t}" --producer-property acks=1 \
    --property parse.key=true --property key.separator=,
  note "✓ 已灌入 ${t}：key=8888 value=42"
done
if poll_pair_eq "${GO_REDIS_DB}" "${RAG_REDIS_DB}" "like:total:8888" "42" 60; then
  record A8 "阳性对照：kq(latest) 路本身正常 —— 两侧 like:total:8888 = 42" "Go=${GO_OBS} RAG=${RAG_OBS}" PASS
else
  record A8 "阳性对照：kq(latest) 路本身正常 —— 两侧 like:total:8888 = 42" "Go=${GO_OBS} RAG=${RAG_OBS}" FAIL
  note "⚠️ 这说明 A5 的「不存在」**不是**因为 kq 坏了 —— 若这一条也失败，"
  note "   那就是 kq 路整体坏了（先查 sink / Redis / topic 名）。"
fi

# ---- 14b. 再往 goka 的输入 topic 灌一批（user 5001 × 5、user 5002 × 2）----
#    ⇒ 走完整链路：raw → goka 路由 → goka 计数 → filtered → kq sink → Redis
#    ⭐ 期望值 = **预灌的 5 条 + 后灌的 5 条 = 10**（5002 同理 = 4）
#    ⇒ 「10」这个数字本身就是**预灌那批也被计入了**的端到端证据
#       （若 earliest 没生效，计数只会是 5）。
cat > "$WORK/raw-batch-2.jsonl" <<'EOF'
{"ts":"2026-10-23T16:00:01.000Z","service":"like-service","event_id":"c01","user_id":"5001","trace_id":"tc1","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T16:00:02.000Z","service":"like-service","event_id":"c02","user_id":"5001","trace_id":"tc2","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T16:00:03.000Z","service":"like-service","event_id":"c03","user_id":"5001","trace_id":"tc3","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T16:00:04.000Z","service":"like-service","event_id":"c04","user_id":"5001","trace_id":"tc4","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T16:00:05.000Z","service":"like-service","event_id":"c05","user_id":"5001","trace_id":"tc5","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T16:00:06.000Z","service":"like-service","event_id":"c06","user_id":"5002","trace_id":"tc6","article_id":"9002","msg":"like"}
{"ts":"2026-10-23T16:00:07.000Z","service":"like-service","event_id":"c07","user_id":"5002","trace_id":"tc7","article_id":"9002","msg":"like"}
EOF
for t in task.logs.raw rag.task.logs.raw; do
  kafka_in kafka-console-producer.sh --bootstrap-server "$KAFKA_BOOT" --topic "${t}" \
    --producer-property acks=1 < "$WORK/raw-batch-2.jsonl"
  note "✓ 已灌入 ${t}：user 5001 × 5、user 5002 × 2"
done

# ---- 14c. 端到端断言 ----
if poll_pair_eq "${GO_REDIS_DB}" "${RAG_REDIS_DB}" "like:total:5001" "10" 90; then
  record A6 "端到端 like:total:5001 = 10（预灌 5 + 后灌 5 ⇒ 预灌那批也被计入）" "Go=${GO_OBS} RAG=${RAG_OBS}" PASS
else
  record A6 "端到端 like:total:5001 = 10（预灌 5 + 后灌 5 ⇒ 预灌那批也被计入）" "Go=${GO_OBS} RAG=${RAG_OBS}" FAIL
  note "⚠️ 若实测是 5 ⇒ **预灌的那 5 条没被计入** ⇒ earliest 没生效（或它被 kq 跳过了）。"
  note "   ⚠️ 若实测是 0 ⇒ 整条链路没通（先查 sink / Redis / topic 名）。"
fi
if poll_pair_eq "${GO_REDIS_DB}" "${RAG_REDIS_DB}" "like:total:5002" "4" 90; then
  record A7 "端到端 like:total:5002 = 4（doing 分支，预灌 2 + 后灌 2）" "Go=${GO_OBS} RAG=${RAG_OBS}" PASS
else
  record A7 "端到端 like:total:5002 = 4（doing 分支，预灌 2 + 后灌 2）" "Go=${GO_OBS} RAG=${RAG_OBS}" FAIL
  note "⚠️ 同 A6：实测 2 ⇒ 预灌那批没被计入；实测 0 ⇒ 整条链路没通。"
fi

# ═══════════════════════════════════════════════════════════════════════
say "15. 断言汇总"
if [ "${#ASSERT_IDS[@]}" -gt 0 ]; then
  for idx in $(seq 1 "${#ASSERT_IDS[@]}"); do
    j=$((idx - 1))
    printf '  %-4s %-62s 实测=%-24s %s\n' \
      "${ASSERT_IDS[$j]}" "${ASSERT_LABELS[$j]}" "${ASSERT_VALUES[$j]}" "${ASSERT_RESULTS[$j]}"
  done
fi
note "失败项数：${FAILURES}"

# ═══════════════════════════════════════════════════════════════════════
say "16. 收摊"
if [ "${KEEP_UP}" = "1" ]; then
  note "KEEP_UP=1 ⇒ 服务与容器保留（日志：${WORK}/logs/）"
else
  # ⚠️ 只停**本次**起的两个进程；容器按本仓其它 task 脚本的约定保留
  #    （taskprobe-postgres / taskprobe-redis / taskprobe-kafka 留给后续脚本用）。
  # ⚠️ 绝不动 Sea-* / userprobe-* / difftest-* 这些别的域的容器与进程。
  pkill -f "${WORK}/bin/go-task-rpc" 2>/dev/null || true
  pkill -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null || true
  sleep 3
  note "✓ 两侧进程已停（容器保留；日志在 ${WORK}/logs/）"
fi

if [ "${FAILURES}" -gt 0 ]; then
  printf '\n\033[31m❌ %s 条断言失败\033[0m\n' "${FAILURES}"
  exit 1
fi
printf '\n\033[32m✅ 全部断言通过（earliest 正向 + latest 阴性对照 + 阳性对照）\033[0m\n'
exit 0
