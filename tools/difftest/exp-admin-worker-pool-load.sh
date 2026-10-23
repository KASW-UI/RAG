#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
#  exp-admin-worker-pool-load — worker-pool 饱和分支的可达性取证
# ════════════════════════════════════════════════════════════════════════
#
# 【要验什么】
#   UNRESOLVED-004：RAG 侧 worker-pool 饱和 → **裸 503 空 body** 这条分支
#                   在真实负载下到底可达吗？
#
# 【关键歧义：裸 503 有三个来源，必须区分开】
#   service/common 里有**三条**能产出「503 + 空 body」的路径：
#     ① HttpMiddleware 的 RejectedExecutionException（worker-pool 饱和）
#        → HttpMiddleware.java:296-315
#     ② GoStyleCpuSheddingFilter（Go 的 sheddinghandler 对应物）
#        → GoStyleCpuSheddingFilter.java:94（默认 enabled=true，threshold=900‰）
#     ③ GoStyleMaxConnsFilter（Go 的 maxconnshandler 对应物）
#        → GoStyleMaxConnsFilter.java:114（默认 maxConns=10000）
#   ⇒ **只看「出现裸 503」无法判断是哪个** ⇒ 本脚本做两轮对照：
#       第 1 轮：CPU shedding **开**（默认）
#       第 2 轮：CPU shedding **关**（--rag.protection.cpu-shedding.enabled=false）
#     若第 2 轮裸 503 = 0 ⇒ 池饱和分支**未被触发**。
#
# 【结构论证（与实测互相印证）】
#   Tomcat 的 max-threads（Boot 默认 200）**限制了同时执行 filter 链的请求数**；
#   HttpMiddleware 把业务丢进 worker 池后，**容器线程用 CountDownLatch.await 阻塞等待**
#   ⇒ 池内并发任务数 ≤ Tomcat 线程数 ≤ 200
#   ⇒ 而池的 max = 512（HttpMiddleware.MAX_HTTP_WORKERS）
#   ⇒ **AbortPolicy 那条分支在默认配置下不可达**（200 < 512）。
#
# 【实测数据（2026-10-24，admin-api 58889，RAG 侧单栈）】
#   ⚠️ **裸 503 的数不稳定** —— CPU shedding 是**自适应**的（GoStyleAdaptiveShedder
#      读**整机** CPU，threshold=900‰）⇒ 机器忙不忙直接决定裸 503 出不出、出多少。
#      实测同一份代码、同一个并发数，两次跑出来的裸 503 数分别是 100 和 0。
#      ⇒ **裸 503 不是池饱和的可靠信号**，判定要看「池实际并发峰值 vs 512」。
#   第 1 轮（CPU shedding 开，默认）某次实测：
#     300 并发 → 188×200(1015) + 12×200(1002) + **100×503 裸空 body**
#   第 2 轮（CPU shedding 关）：
#     300 并发 → 85×200(1002) + 52×200(1015) + 29×500(5001)
#                + 125×503(body='Request Timeout\n') + 9×200(body='Request Timeout\n')
#     ⇒ **裸 503 = 0**
#
#   ★ **决定性证据（与机器负载无关）**：
#     负载期间采样 `obs-http-worker` 线程数（= worker 池的实际并发）
#     ⇒ **实测峰值 200 / 201**
#     而池的 max = 512 ⇒ **离饱和阈值差 2.5 倍**
#     ⇒ **池饱和分支在默认配置下不可达**（结构解释见上：Tomcat 的 max-threads
#       限制了同时执行 filter 链的请求数，而容器线程是**阻塞等待** worker 的）
#
#   ⚠️ **别用「exec 线程号上界」当并发上限**：Tomcat 线程**只增不复用**，
#      线程号会随累计请求数一直涨（实测见过 390），与并发无关。
#      正确口径是**同一时刻不同 exec 线程的个数**。
#
# 用法：
#   bash tools/difftest/exp-admin-worker-pool-load.sh [并发数，默认 300]
# 退出码：0 = 结论成立（第 2 轮裸 503 = 0）；1 = 不成立（需重新分析）。
set -uo pipefail

N="${1:-300}"
RUN_DIR="${RUN_DIR:-/tmp/difftest}"
JAR="$RUN_DIR/bin/admin-api"
OUT_DIR="$RUN_DIR/logs"

export POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT=55432 POSTGRES_USER=admin POSTGRES_PASSWORD=difftest
export REDIS_ADDR=127.0.0.1 REDIS_PORT=56379
export NACOS_ADDR=127.0.0.1 NACOS_PORT=1
export POSTGRES_DB=rag_db SERVER_PORT=58889
export SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false

[[ -f "$JAR" ]] || { echo "缺少 ${JAR} —— 请先跑 tools/difftest/run.sh" >&2; exit 2; }
mkdir -p "$OUT_DIR"

LOADER="$RUN_DIR/worker-pool-load.py"
cat > "$LOADER" << 'PY'
import concurrent.futures, http.client, collections, time, json, sys
N = int(sys.argv[1])
BODY = json.dumps({"username": "admin", "password": "definitely-wrong-pw"})

def one(_):
    t0 = time.time()
    c = http.client.HTTPConnection("127.0.0.1", 58889, timeout=30)
    try:
        c.request("POST", "/admin/v1/login", body=BODY,
                  headers={"Content-Type": "application/json"})
        r = c.getresponse()
        return (r.status, r.read()[:20], time.time() - t0)
    except Exception as e:
        return ("ERR", str(e)[:20].encode(), time.time() - t0)
    finally:
        c.close()

t0 = time.time()
with concurrent.futures.ThreadPoolExecutor(max_workers=N) as ex:
    res = list(ex.map(one, range(N)))
wall = time.time() - t0
cnt = collections.Counter((s, b) for s, b, _ in res)
lat = sorted(d for _, _, d in res)
print("  并发 N=%d 墙钟=%.2fs" % (N, wall))
for (s, b), n in cnt.most_common():
    print("    %4d x status=%s body=%r" % (n, s, b))
bare = sum(n for (s, b), n in cnt.items() if s == 503 and b == b"")
print("  BARE_503=%d" % bare)
print("  延迟 p50=%.0fms p95=%.0fms max=%.0fms"
      % (lat[len(lat)//2]*1000, lat[int(len(lat)*0.95)]*1000, lat[-1]*1000))
PY

APID_PID=""

stop_admin_api() {
  if [[ -n "$APID_PID" ]]; then
    kill "$APID_PID" 2>/dev/null
    APID_PID=""
  fi
  for _ in $(seq 1 40); do
    lsof -nP -iTCP:58889 -sTCP:LISTEN >/dev/null 2>&1 || return 0
    sleep 1
  done
}

run_round() {  # label, extra-arg, out-file
  local label="$1" extra="$2" out="$3"
  echo "───────────────────────────────────────────────"
  echo "  轮次：${label}"
  stop_admin_api
  mkdir -p "$OUT_DIR"
  # shellcheck disable=SC2086
  nohup java -jar "$JAR" $extra \
    --grpc.client.admin-rpc.address=static://127.0.0.1:58081 \
    --grpc.client.admin-rpc.negotiation-type=plaintext \
    --grpc.client.user-rpc.address=static://127.0.0.1:59210 \
    --grpc.client.user-rpc.negotiation-type=plaintext \
    >"$OUT_DIR/exp-worker-pool.log" 2>&1 &
  APID_PID=$!

  local ok=0
  for _ in $(seq 1 60); do
    if lsof -nP -iTCP:58889 -sTCP:LISTEN >/dev/null 2>&1; then ok=1; break; fi
    sleep 1
  done
  if [[ $ok -ne 1 ]]; then echo "  admin-api 起不来"; return 1; fi

  local apid
  apid=$(lsof -nP -iTCP:58889 -sTCP:LISTEN -t | head -1)

  python3 "$LOADER" "$N" > "$out" 2>&1 &
  local loadpid=$!
  sleep 0.8
  # 负载期间采样两件事：
  #   ① Tomcat **并发**处理的请求线程数（**去重后的 exec 线程个数**）
  #   ② worker 池的**实际并发**（obs-http-worker 线程数）
  # ⚠️ **不要用「exec 线程号上界」当并发上限**：Tomcat 的线程**只增不复用**，
  #    线程号会随累计请求数一直涨（实测见过 390），与并发数无关。
  #    正确口径是**同一时刻不同 exec 线程的个数**。
  local maxw=0 maxt=0 nw nt
  for _ in $(seq 1 24); do
    nw=$(jcmd "$apid" Thread.print 2>/dev/null | grep -c '"obs-http-worker')
    nt=$(jcmd "$apid" Thread.print 2>/dev/null | grep -oE 'http-nio-[^"]*exec-[0-9]+' | sort -u | wc -l | tr -d ' ')
    [[ "$nw" -gt "$maxw" ]] && maxw=$nw
    [[ "$nt" -gt "$maxt" ]] && maxt=$nt
    sleep 0.3
  done
  wait $loadpid
  cat "$out" | sed 's/^/    /'
  echo "  Tomcat 并发处理线程峰值（去重 exec 线程数）= ${maxt}"
  echo "  obs-http-worker 峰值（= 池实际并发）= ${maxw}（池 max=512）"
  WORKER_PEAK="$maxw"
  TOMCAT_PEAK="$maxt"
}

echo "═══════════ worker-pool 饱和分支可达性取证（并发 ${N}） ═══════════"

OUT1="$OUT_DIR/exp-worker-pool-round1.txt"
OUT2="$OUT_DIR/exp-worker-pool-round2.txt"

WORKER_PEAK=0
TOMCAT_PEAK=0

run_round "第 1 轮：CPU shedding 开（默认）" "" "$OUT1"
PEAK1="$WORKER_PEAK"; TP1="$TOMCAT_PEAK"
run_round "第 2 轮：CPU shedding 关" "--rag.protection.cpu-shedding.enabled=false" "$OUT2"
PEAK2="$WORKER_PEAK"; TP2="$TOMCAT_PEAK"

stop_admin_api

B1=$(grep -oE 'BARE_503=[0-9]+' "$OUT1" | tail -1 | cut -d= -f2)
B2=$(grep -oE 'BARE_503=[0-9]+' "$OUT2" | tail -1 | cut -d= -f2)
MAXP=$(printf '%s\n%s\n' "$PEAK1" "$PEAK2" | sort -n | tail -1)
MAXT=$(printf '%s\n%s\n' "$TP1" "$TP2" | sort -n | tail -1)

echo
echo "══════════════════════════════════════════════"
echo "  第 1 轮裸 503 = ${B1}（CPU shedding 开）"
echo "  第 2 轮裸 503 = ${B2}（CPU shedding 关）"
echo "  Tomcat 并发处理线程峰值 = ${MAXT}"
echo "  worker 池实际并发峰值   = ${MAXP}（池 max=512）"
echo
if [[ "$MAXP" -gt 0 && "$MAXP" -lt 512 ]]; then
  echo "  ✓ 池实际并发峰值 ${MAXP} < 512 ⇒ **池饱和分支在默认配置下不可达**（直接实测）"
  echo "    ⇒ 结构性解释：Tomcat 的 max-threads（实测并发 ${MAXT}）限制了同时执行 filter 链的请求数，"
  echo "      而 HttpMiddleware 把业务丢进池后容器线程用 CountDownLatch.await 阻塞等待"
  echo "      ⇒ 池内并发任务数 ≤ Tomcat 线程数"
else
  echo "  ! 采样到的池并发峰值 = ${MAXP}，无法据此判定（采样窗口太短？）"
fi
echo
echo "  ⚠️ 关于「裸 503」的口径（**别把它当成池饱和的信号**）："
echo "    CPU shedding 是**自适应**的（GoStyleAdaptiveShedder，读整机 CPU，threshold=900‰）"
echo "    ⇒ 裸 503 **出不出、多少，取决于当时机器有多忙** ⇒ 它是负载相关的，不稳定。"
echo "    ⇒ 本脚本第 1 轮与第 2 轮的裸 503 数可能都是 0，也可能差很多。"
echo "    ⇒ **判定池饱和分支可达性的可靠依据是「池并发峰值 vs 512」，不是「裸 503 数」。**"
if [[ "$B2" == "0" ]]; then
  echo "  ✓ 第 2 轮裸 503 = 0"
  exit 0
else
  echo "  ✗ 第 2 轮仍有裸 503 ⇒ 需要重新分析"
  exit 1
fi
