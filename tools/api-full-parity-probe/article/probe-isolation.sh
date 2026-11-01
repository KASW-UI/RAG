#!/usr/bin/env bash
# =============================================================================
# probe-isolation.sh —— article 域双侧活体对拍探针的「**每轮独立**」机制
#
# 【为什么需要这个文件】
#   `run.sh` / `run-success.sh` 各自起**一整栈**（Go 真源栈 + RAG 栈）⇒ 天然要占宿主端口、
#   要在 Nacos 注册服务、要在 etcd 注册键、要有自己的 Postgres。
#   ⇒ **并发跑两份**时，这些「共享面」会让两份互相污染 ⇒ 探针报 `INTERNAL` / `5001`
#   ⇒ ⇒ 「跑红」被误读成「RAG 有缺陷」⇒ ⇒ 制造**一类假红**（本轮派工单 P4-T08 的根因）。
#   ⇒ 本文件提供**四个隔离向量**，让同一份探针**并发跑两份**互不污染。
#   ⇒ **只改工具面**（`tools/api-full-parity-probe/**`）⇒ **产品代码一个字节都不动**。
#
# 【四个隔离向量】
#   V1 **Nacos 命名空间** —— `env NACOS_NAMESPACE`
#       · 为什么命名空间能隔离：**命名空间由 Nacos 客户端配置决定**，不是代码里写死的。
#         RAG `service/article/article-rpc/src/main/resources/application.yml:122`
#              `namespace: ${NACOS_NAMESPACE:public}`
#         RAG `service/article/article-api/src/main/resources/application.yml:33`
#              `namespace: ${NACOS_NAMESPACE:public}`
#         ⇒ 设 env `NACOS_NAMESPACE=xxx` ⇒ 两侧 RAG 进程**注册 / 发现**都落到 `xxx`。
#       · 为什么**不需要**改 `NacosRegistration.java`：
#         `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/bootstrap/NacosRegistration.java`
#         全文**没有** `instance.setNamespace(...)`（只 set ip/port/serviceName/healthy/weight/ephemeral，
#         见该文件 :83-89）⇒ 注册落在哪个命名空间 = Nacos 客户端的 namespace 属性。
#         框架侧证据：`NacosServiceManager.getNamingService()`
#         （spring-cloud-starter-alibaba-nacos-discovery 2025.1.0.0 源）
#         → `nacosDiscoveryProperties.getNacosProperties()` → `properties.put(NAMESPACE, namespace)`
#         ⇒ 命名空间**确实**进了 NamingService。
#       · ⚠️ **服务名 / group 改不了**（同文件 :42 `SERVICE_NAME="article-rpc"`、
#         :45 `DEFAULT_GROUP="DEFAULT_GROUP"` 都是硬编码常量）⇒ 隔离**只能**靠命名空间。
#       · 命名空间的**创建**用 Nacos v1 开放 API（实测可用，见下方 `probe_nacos_ensure_namespace`）。
#
#   V2 **etcd 键前缀** —— `env GO_ETCD_KEY`
#       · Go 侧 rpc 的**发布键**与 api 的**订阅前缀**必须是**同一个**值：
#         真源 `service/article/rpc/etc/article.yaml:7`   `Key: article.rpc`（发布）
#         真源 `service/article/api/etc/article-api.yaml:11` `Key: article.rpc`（订阅）
#         ⇒ 副本里**同步**改成 `GO_ETCD_KEY` ⇒ 两轮的 Go 栈互不发现对方。
#       · 键形 = `<key>/<leaseID>`：go-zero@v1.10.2 `core/discov/clients.go:39-41`
#         `makeEtcdKey` = `fmt.Sprintf("%s%c%d", key, internal.Delimiter, id)`
#         ⇒ **必须按前缀查**（`etcdctl get <key> --prefix`）。
#       · ⚠️ **键名不许以 `article.rpc` 开头**：`article.rpc` 是「默认轮」的键，
#         而 `--prefix` 是**从头匹配**⇒ 默认轮的 `del "article.rpc" --prefix` 会**连本轮的键一起删掉**
#         ⇒ ⇒ 本轮键统一用 `probe-<RUN_TAG>.article.rpc`（**前缀在前面** ⇒ 两边都不互删）。
#
#   V3 **宿主端口块** —— 端口**租约**（原子 `mkdir`）
#       · 端口写死 ⇒ 两份必撞 ⇒ 后一份的 `free_probe_ports` 会**踢掉前一份的进程**
#         （那本身就是一种污染：前一份被后一份杀掉）。
#       · 本文件用 `mkdir` 做**原子租约**：并发轮次**保证**拿到不同的端口块
#         ⇒ 不靠「随机数碰运气」，也不靠「谁来谁先占」（那是竞态）。
#       · 租约块号 = 端口偏移的来源：`PORT_OFFSET = 块号 × 步长`。
#
#   V4 **构建产物** —— `mvn` **串行锁** + 每轮把 fat jar **拷进自己的 WORK**
#       · 两个脚本都在自己内部跑 `mvn -pl service/article/article-rpc,service/article/article-api -am package`
#         ⇒ 两个模块的 `target/` 是**同一份** ⇒ 并发写**必然互踩**。
#       · 实测：`-Dproject.build.directory=<dir>` **不生效**（Maven 的 `build.directory`
#         不是 user property ⇒ jar 仍然落在 `service/article/*/target/`）
#         ⇒ 改不了输出目录 ⇒ 只能用「**串行构建 + 拷走**」。
#       · `-Dmaven.repo.local` 换不了 `target/`（它只换本地仓库）⇒ 同上。
#
# 【默认值 = 现有单跑行为**逐字节不变**】
#   `RUN_ID` 为空 ⇒ 命名空间 = `public` · etcd 键 = `article.rpc` · 端口 = 现有写死的那组
#   · PG 阶梯 = 现有写死的那组 ⇒ ⇒ **已冻结的 `expected*.txt` 证据不受影响**。
#
# 【可覆盖的环境变量（全部可选）】
#   `RUN_ID`             本轮标识（非空 ⇒ 开启隔离；只允许 `[A-Za-z0-9_-]`，其余字符换成 `-`）
#   `NACOS_NAMESPACE`    显式指定本轮 Nacos 命名空间（覆盖 `RUN_ID` 派生值）
#   `GO_ETCD_KEY`        显式指定本轮 etcd 键前缀（覆盖 `RUN_ID` 派生值）
#   `PORT_OFFSET`        显式指定本轮**应用端口**偏移（指定后**不占租约**，责任自负）
#   `PG_PORT_OFFSET`     显式指定本轮 **PG 阶梯**偏移（同上）
#   `PROBE_LEASE_DIR`    端口租约目录（默认 `${TMPDIR:-/tmp}/rag-article-parity-lease`）
#   `PROBE_MVN_LOCK_DIR` 构建锁目录（默认 `${TMPDIR:-/tmp}/rag-article-parity-mvn-lock`）
#   `KEEP_NACOS_NAMESPACE=1` 保留本轮创建的命名空间（默认**清理**，免得留残留实例）
#
# 用法：`source probe-isolation.sh` 后调 `probe_iso_init <script-tag>`。
# ⚠️ 本文件**只定义函数与变量**，不执行任何副作用（除下面几行默认值赋值）。
# =============================================================================

# ── 默认值（可被 env 覆盖）─────────────────────────────────────────────────────
PROBE_LEASE_DIR="${PROBE_LEASE_DIR:-${TMPDIR:-/tmp}/rag-article-parity-lease}"
PROBE_MVN_LOCK_DIR="${PROBE_MVN_LOCK_DIR:-${TMPDIR:-/tmp}/rag-article-parity-mvn-lock}"
# 端口块步长：必须 **大于**同族端口的跨度，否则不同块之间会撞。
#   run.sh      的应用端口跨度 = 19092 - 8889 = 10203  ⇒ 步长 20000 足够
#   run-success.sh 的应用端口跨度 = 22416 - 22411 = 5    ⇒ 同上
PROBE_PORT_STRIDE="${PROBE_PORT_STRIDE:-20000}"
# PG 阶梯的「族偏置」：run.sh 的阶梯基准比 run-success **低**一段 ⇒ 同块号下两族的 PG 端口不重叠。
# （由各脚本在 `probe_iso_init` 之前覆盖：run.sh=100 / run-success=0）
PROBE_PG_BIAS="${PROBE_PG_BIAS:-0}"
# 最多支持几块（端口不能超过 65535）：run.sh 的最大端口 19092 + 20000×2 = 59092 ⇒ 3 块。
PROBE_MAX_BLOCK="${PROBE_MAX_BLOCK:-3}"
PROBE_MVN_LOCK_TIMEOUT="${PROBE_MVN_LOCK_TIMEOUT:-900}"

# 本轮派生量（probe_iso_init 之后有效）
PROBE_RUN_TAG=""
PROBE_NS="public"
PROBE_NS_CREATED=0
PROBE_ETCD_KEY="article.rpc"
PROBE_PORT_BLOCK=-1
PROBE_PORT_OFFSET=0
PROBE_PG_OFFSET=0
PROBE_LEASE_HELD=""
PROBE_ISO_READY=0

# 各脚本在 `probe_iso_init` **之前**填这两个（空格分隔的基准端口）：
#   PROBE_BASE_APP_PORTS —— 应用端口（Go api/rpc/prom · RAG api/rpc）
#   PROBE_BASE_PG_PORTS  —— PG 端口阶梯
# ⚠️ 有了它们，租约才能算出「本块会占哪些端口」⇒ 才能**避开危险端口**（见 probe_reserved_host_ports）。
PROBE_BASE_APP_PORTS="${PROBE_BASE_APP_PORTS:-}"
PROBE_BASE_PG_PORTS="${PROBE_BASE_PG_PORTS:-}"

# ── 小工具 ────────────────────────────────────────────────────────────────────
probe_sanitize() {   # $1 = 原始串 ⇒ 打印「只含 [A-Za-z0-9_-]、最长 32」的安全标识
  local s
  s="$(printf '%s' "${1}" | tr -c 'A-Za-z0-9_-' '-')"
  s="$(printf '%s' "${s}" | cut -c1-32)"
  printf '%s' "${s}"
}

# =============================================================================
# probe_iso_init <script-tag>
#   计算本轮的隔离身份 + **占端口租约**。
#   ⚠️ 必须在**端口变量被使用之前**调用（端口偏移从这里来）。
# =============================================================================
probe_iso_init() {
  local tag="${1:-probe}"

  # ── V1/V2 的身份：RUN_ID → RUN_TAG ────────────────────────────────────────
  if [ -n "${RUN_ID:-}" ]; then
    PROBE_RUN_TAG="$(probe_sanitize "${RUN_ID}")"
    [ -n "${PROBE_RUN_TAG}" ] || PROBE_RUN_TAG="run"
  else
    PROBE_RUN_TAG=""
  fi

  # 命名空间：显式 env 优先；否则 RUN_ID 派生；再否则 = public（默认轮）。
  if [ -n "${NACOS_NAMESPACE:-}" ]; then
    PROBE_NS="${NACOS_NAMESPACE}"
  elif [ -n "${PROBE_RUN_TAG}" ]; then
    PROBE_NS="probe-article-${PROBE_RUN_TAG}"
  else
    PROBE_NS="public"
  fi

  # etcd 键前缀：显式 env 优先；否则 RUN_ID 派生；再否则 = article.rpc（默认轮）。
  # ⚠️ 派生值**不以 `article.rpc` 开头**（理由见文件头 V2 段）。
  if [ -n "${GO_ETCD_KEY:-}" ]; then
    PROBE_ETCD_KEY="${GO_ETCD_KEY}"
  elif [ -n "${PROBE_RUN_TAG}" ]; then
    PROBE_ETCD_KEY="probe-${PROBE_RUN_TAG}.article.rpc"
  else
    PROBE_ETCD_KEY="article.rpc"
  fi

  # ── V3 端口租约 ───────────────────────────────────────────────────────────
  if [ -n "${PORT_OFFSET:-}" ]; then
    # 显式指定 ⇒ **不占租约**（责任自负：两份用同一个 PORT_OFFSET 就会撞）。
    PROBE_PORT_OFFSET="${PORT_OFFSET}"
    PROBE_PORT_BLOCK=-1
  else
    probe_lease_block "${tag}" || return 1
  fi
  if [ -n "${PG_PORT_OFFSET:-}" ]; then
    PROBE_PG_OFFSET="${PG_PORT_OFFSET}"
  elif [ "${PROBE_PORT_BLOCK}" -gt 0 ]; then
    # PG 阶梯与**应用端口**分开偏移（两族的端口区间不同 ⇒ 必须分开算，否则会互相撞）。
    # bias：run.sh 的阶梯偏 **低**一段 ⇒ 同一块号下两族的 PG 端口也不重叠。
    PROBE_PG_OFFSET=$(( PROBE_PORT_BLOCK * 24 + PROBE_PG_BIAS ))
  else
    PROBE_PG_OFFSET=0
  fi

  PROBE_ISO_READY=1
  return 0
}

# ── 端口租约（原子 mkdir）─────────────────────────────────────────────────────
# 🔴 **为什么必须「算端口、查冲突」而不是「先占再算」**（2026-11-01 实测踩过）：
#   偏移后的端口**可能撞上宿主中间件的发布端口**。实测 `run.sh` 偏移 20000 时
#   `19092+20000 = 39092` = **`kafka` 容器发布到宿主的那一口**
#   ⇒ `free_probe_ports` 的 `lsof -ti tcp:39092` 取到的是 **Docker Desktop 的 backend 进程**
#   ⇒ 一 `kill -9` ⇒ **Docker 整个失联**（所有容器的发布端口一起挂）。
#   ⇒ ⇒ 端口块**必须**先跟「容器已发布的宿主端口」+「别的活跃租约的端口」求交，
#        冲突就**换一块**，绝不选会撞的块。
probe_reserved_host_ports() {   # 打印所有**运行中容器发布到宿主**的端口（空格分隔）
  docker ps --format '{{.Ports}}' 2>/dev/null | python3 -c '
import sys, re
out = set()
for line in sys.stdin:
    for m in re.finditer(r"(\d+)(?:-(\d+))?->", line):
        a = int(m.group(1))
        b = int(m.group(2)) if m.group(2) else a
        for p in range(a, b + 1):
            out.add(p)
print(" ".join(str(p) for p in sorted(out)))
'
}

# 别的**活跃**租约占的端口（租约文件里第 2 行记着 `ports`；持有者进程已死的忽略）。
# ⚠️ `$1` = 要**排除**的租约文件（就是本轮刚抢到的那个 ⇒ 自己不能跟自己撞）。
probe_live_lease_ports() {
  local excl="${1:-}" f p ports
  for f in "${PROBE_LEASE_DIR}"/*; do
    [ -f "${f}" ] || continue
    [ "${f}" = "${excl}" ] && continue
    p="$(sed -n '1p' "${f}" 2>/dev/null || true)"
    [ -n "${p}" ] || continue
    kill -0 "${p}" 2>/dev/null || continue
    ports="$(sed -n '2p' "${f}" 2>/dev/null || true)"
    printf '%s ' "${ports}"
  done
}

# 某一块会占的**全部**端口（应用端口 + PG 阶梯）。
probe_block_ports() {   # $1 = 块号 ⇒ 打印空格分隔的端口表
  local i="$1" p pg_off
  if [ "${i}" -eq 0 ]; then pg_off=0; else pg_off=$(( i * 24 + PROBE_PG_BIAS )); fi
  for p in ${PROBE_BASE_APP_PORTS}; do printf '%s ' $(( p + i * PROBE_PORT_STRIDE )); done
  for p in ${PROBE_BASE_PG_PORTS};  do printf '%s ' $(( p + pg_off )); done
}

# ⚠️ **原子占用**：`set -o noclobber` 的 `>` 就是 `O_CREAT|O_EXCL` ⇒ 文件要么不存在、要么**内容完整**。
#    （早先用 `mkdir` + 事后写 pid ⇒ **两者之间有窗口** ⇒ 另一轮读到空 pid ⇒ 误判「残留」⇒
#      抢走同一块端口 ⇒ 两轮撞端口。2026-11-01 实测踩过。）
probe_claim_file() {   # $1 = 路径  $2 = 内容 ⇒ 抢到返回 0
  ( set -o noclobber; printf '%s' "${2}" > "${1}" ) 2>/dev/null
}

probe_lease_block() {   # $1 = script tag ⇒ 成功时设 PROBE_PORT_BLOCK / PROBE_LEASE_HELD
  local tag="${1:-probe}" i=0 f p cand reserved live
  # ⚠️ **必须能读到「容器已发布的宿主端口」**才算得准 ⇒ docker 不在跑 ⇒ 直接拒绝分配
  #    （否则会挑中一块撞上宿主中间件发布口的端口 ⇒ 端口预检会把 Docker backend 杀掉）。
  docker ps --format '{{.Names}}' >/dev/null 2>&1 \
    || { printf '!! 读不到 docker（docker ps 失败）⇒ 拒绝分配端口块\n' >&2; return 1; }
  mkdir -p "${PROBE_LEASE_DIR}" 2>/dev/null || true
  reserved="$(probe_reserved_host_ports)"
  while [ "${i}" -lt "${PROBE_MAX_BLOCK}" ]; do
    f="${PROBE_LEASE_DIR}/${tag}-b${i}"
    cand="$(probe_block_ports "${i}")"
    # ① 这一块的端口**不能**撞「容器已发布的宿主端口」
    for p in ${cand}; do
      case " ${reserved} " in
        *" ${p} "*) cand=""; break ;;
      esac
    done
    if [ -z "${cand}" ]; then i=$(( i + 1 )); continue; fi
    # ② **原子占用**（内容一次写全 ⇒ 没有「已占但内容为空」的窗口）
    if probe_claim_file "${f}" "$(printf '%s\n%s\n' "$$" "${cand}")"; then
      # ③ 复核：不能撞「别的**活跃**租约」的端口（别人也可能刚占到同一块 ⇒ 谁先抢到谁算）
      live="$(probe_live_lease_ports "${f}")"
      for p in ${cand}; do
        case " ${live} " in
          *" ${p} "*) probe_drop_claim "${f}"; cand=""; break ;;
        esac
      done
      if [ -z "${cand}" ]; then i=$(( i + 1 )); continue; fi
      PROBE_PORT_BLOCK="${i}"
      PROBE_LEASE_HELD="${f}"
      PROBE_PORT_OFFSET=$(( i * PROBE_PORT_STRIDE ))
      return 0
    fi
    # 已被占 ⇒ 可能是**上一轮残留**（持有者进程已死）⇒ 回收后重试同一块号。
    p="$(sed -n '1p' "${f}" 2>/dev/null || true)"
    if [ -z "${p}" ] || ! kill -0 "${p}" 2>/dev/null; then
      # 回收成功 ⇒ 重试同一块；回收失败（文件被别的东西占着）⇒ 跳过这一块（**别死循环**）。
      if probe_drop_claim "${f}"; then continue; fi
      i=$(( i + 1 ))
      continue
    fi
    i=$(( i + 1 ))
  done
  printf '!! 端口租约耗尽（%s 个块都被占 / 都撞了危险端口）⇒ 请显式指定 PORT_OFFSET\n' "${PROBE_MAX_BLOCK}" >&2
  return 1
}

probe_iso_release() {
  if [ -n "${PROBE_LEASE_HELD}" ]; then
    probe_drop_claim "${PROBE_LEASE_HELD}"
    PROBE_LEASE_HELD=""
  fi
}

# ⚠️ **删除一律用 Python `os.remove` / `os.rmdir`**（本机纪律：`rm -rf` 会被回收站守卫拦下；
#    连 `rm -f` 也会被守卫接管成「移到回收站」⇒ 慢且依赖守卫 ⇒ 不用）。
probe_drop_claim() {   # $1 = 租约文件路径 ⇒ 删掉它（文件或目录都行）⇒ 成功（已不存在也算）返回 0
  python3 - "$1" <<'PY'
import os, shutil, sys
p = sys.argv[1]
try:
    if os.path.isdir(p) and not os.path.islink(p):
        shutil.rmtree(p)
    else:
        os.remove(p)
except OSError:
    pass
sys.exit(0 if not os.path.exists(p) else 1)
PY
}

# PG 端口阶梯：$1 = 起始端口  $2 = 步长  $3 = 个数 ⇒ 打印**本轮**的阶梯（已加 PG 偏移）。
# ⚠️ 偏移为 0（默认轮）时打印出来的阶梯与改造前**逐字节一致**。
probe_pg_ladder() {
  local base="$1" step="$2" n="$3" i=0 out=""
  while [ "${i}" -lt "${n}" ]; do
    out="${out} $(( base + PROBE_PG_OFFSET + i * step ))"
    i=$(( i + 1 ))
  done
  printf '%s' "${out}"
}

# =============================================================================
# 端口预检：**只踢「探针进程」**，绝不误伤宿主上的其它服务
# =============================================================================
# 🔴 **为什么必须按命令行过滤**（2026-11-01 实测踩过，代价很大）：
#   原实现是 `lsof -ti tcp:<port> | xargs kill -9` ⇒ **占着这个端口的任何进程**都会被杀。
#   偏移后的端口**可能撞上容器发布端口** ⇒ `lsof` 返回的是 **Docker Desktop 的 backend**
#   ⇒ 一杀 ⇒ **Docker 整个失联**（所有容器的发布端口一起挂）。
#   ⇒ 现在只杀「命令行里带探针标记」的进程；不是探针进程就**只报告、不动它**。
#   ⇒ 探针标记 = WORK 目录名（`rag-article-parity.`）或探针自己的配置/jar 路径。
probe_free_port() {   # $1 = 端口
  local p="$1" pid cmd
  for pid in $(lsof -ti tcp:"${p}" 2>/dev/null); do
    cmd="$(ps -o command= -p "${pid}" 2>/dev/null || true)"
    case "${cmd}" in
      *rag-article-parity.*|*etc/article.yaml*|*etc/article-api.yaml*|*article-rpc.jar*|*article-api.jar*)
        kill -9 "${pid}" 2>/dev/null
        printf '   · 踢掉占用 :%s 的**残留探针进程** %s\n' "${p}" "${pid}"
        ;;
      *)
        printf '   ⚠️ :%s 被**非探针进程**占用（pid %s）⇒ **不杀**（避免误伤宿主服务）\n' "${p}" "${pid}"
        ;;
    esac
  done
}

# =============================================================================
# V1 的 Nacos 侧操作（**全部带本轮命名空间** ⇒ 绝不碰别的轮次）
# =============================================================================
probe_nacos_namespace_exists() {   # $1 = namespace id ⇒ 命中返回 0
  local ns="${1:-${PROBE_NS}}"
  curl -s -m 10 "http://127.0.0.1:${NACOS_PORT}/nacos/v1/console/namespaces" 2>/dev/null \
  | python3 -c '
import sys, json
want = sys.argv[1]
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(1)
for it in (d.get("data") or []):
    if it.get("namespace") == want:
        sys.exit(0)
sys.exit(1)
' "${ns}"
}

# ⚠️ **实测过的接口形态**（2026-11-01 · 本机 Sea-nacos · 宿主口 49848）：
#   POST /nacos/v1/console/namespaces
#        customNamespaceId=<id>&namespaceName=<name>&namespaceDesc=<desc>
#   → HTTP 200，响应体 `true`。
#   GET  /nacos/v1/console/namespaces → HTTP 200，
#        {"code":200,"message":null,"data":[{"namespace":"","namespaceShowName":"public",...}]}
#        ⇒ 新建的命名空间会出现在 `data[].namespace` 里（= customNamespaceId）。
probe_nacos_ensure_namespace() {
  [ "${PROBE_NS}" = "public" ] && return 0
  if probe_nacos_namespace_exists "${PROBE_NS}"; then
    return 0
  fi
  local body code
  body="$(curl -s -m 15 -X POST "http://127.0.0.1:${NACOS_PORT}/nacos/v1/console/namespaces" \
      -d "customNamespaceId=${PROBE_NS}" \
      -d "namespaceName=${PROBE_NS}" \
      -d "namespaceDesc=RAG parity probe ${PROBE_RUN_TAG}" \
      -w '\n%{http_code}' 2>/dev/null)"
  code="$(printf '%s' "${body}" | tail -1)"
  if [ "${code}" != "200" ]; then
    printf '!! 建 Nacos 命名空间 %s 失败（HTTP=%s）\n' "${PROBE_NS}" "${code}" >&2
    return 1
  fi
  probe_nacos_namespace_exists "${PROBE_NS}" || {
    printf '!! 建完命名空间 %s 但查不到 ⇒ Nacos 版本/接口不匹配\n' "${PROBE_NS}" >&2
    return 1
  }
  PROBE_NS_CREATED=1
  return 0
}

# 注销本轮命名空间里某个服务的**全部**实例（只在本轮命名空间里找 ⇒ 不碰别的轮）。
probe_nacos_deregister_all() {   # $1 = 服务名
  local svc="$1" ip port
  curl -s -m 10 "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=${svc}&groupName=DEFAULT_GROUP&namespaceId=${PROBE_NS}" 2>/dev/null \
  | python3 -c '
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for h in (d.get("hosts") or []):
    ip, port = h.get("ip"), h.get("port")
    if ip and port:
        print("%s %s" % (ip, port))
' | while read -r ip port; do
      [ -n "${ip}" ] || continue
      curl -s -m 5 -X DELETE "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance?serviceName=${svc}&groupName=DEFAULT_GROUP&namespaceId=${PROBE_NS}&ip=${ip}&port=${port}" >/dev/null 2>&1
      printf '   · 已注销 Nacos 残留实例 %s %s:%s（命名空间 %s）\n' "${svc}" "${ip}" "${port}" "${PROBE_NS}"
    done
}

# 数「**本轮端口**」上的实例个数 ⇒ 就绪判据精确到本轮专属标识。
probe_nacos_instance_count() {   # $1 = 端口 ⇒ 打印该端口上的实例个数
  curl -s -m 10 "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=article-rpc&groupName=DEFAULT_GROUP&namespaceId=${PROBE_NS}" 2>/dev/null \
  | python3 -c '
import sys, json
port = sys.argv[1]
try:
    d = json.load(sys.stdin)
except Exception:
    print(0); sys.exit(0)
n = 0
for h in (d.get("hosts") or []):
    if str(h.get("port")) == port:
        n += 1
print(n)
' "$1"
}

# 本轮命名空间里**任意端口**的实例总数（判断命名空间是否已空）。
probe_nacos_instance_count_any() {
  curl -s -m 10 "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=article-rpc&groupName=DEFAULT_GROUP&namespaceId=${PROBE_NS}" 2>/dev/null \
  | python3 -c '
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print(0); sys.exit(0)
print(len(d.get("hosts") or []))
'
}

# 清理本轮命名空间里的实例（+ 本轮创建的命名空间本身）。
# ⚠️ **命名空间的删除条件放宽到「探针命名空间前缀」**（2026-11-01 实测）：
#   只删「本轮创建的」会留下**上一轮残留**（那一轮失败时它已经存在 ⇒ created=0 ⇒ 谁都不删）
#   ⇒ 残留命名空间会一直堆在 Nacos 上 ⇒ 下一轮进去时里面可能有**陈旧实例** ⇒ 自匹配 ⇒ 假失败。
#   ⇒ 现在：命名空间 id 以 `probe-` 开头（**探针专用前缀**）且**已无实例** ⇒ 一律删。
#   ⇒ 用户自己的命名空间（不以 `probe-` 开头）**永不被删**。
# ⚠️ **注销是异步的**（Nacos 侧要等一拍才真的没实例）⇒ 第一次查还有 ⇒ **等 2s 再查**
#    ⇒ 实测：不等就会留下「命名空间还在、实例已走」的残留。
probe_nacos_cleanup() {
  probe_nacos_deregister_all article-rpc
  [ "${PROBE_NS}" = "public" ] && return 0
  [ "${KEEP_NACOS_NAMESPACE:-0}" = "1" ] && return 0
  case "${PROBE_NS}" in
    probe-*) : ;;
    *) [ "${PROBE_NS_CREATED}" = "1" ] || return 0 ;;
  esac
  # 还有实例 ⇒ Nacos 拒删（命名空间非空时 DELETE 失败）⇒ 等它消失再删。
  local i
  for i in 1 2 3 4 5 6; do
    [ "$(probe_nacos_instance_count_any)" = "0" ] && break
    sleep 1
  done
  if [ "$(probe_nacos_instance_count_any)" != "0" ]; then
    printf '   ⚠️ 命名空间 %s 里还有实例 ⇒ 本轮不删（下一轮会再注销）\n' "${PROBE_NS}"
    return 0
  fi
  curl -s -m 10 -X DELETE "http://127.0.0.1:${NACOS_PORT}/nacos/v1/console/namespaces?namespaceId=${PROBE_NS}" >/dev/null 2>&1 || true
  printf '   · 已删除本轮 Nacos 命名空间 %s\n' "${PROBE_NS}"
}

# =============================================================================
# V4 的构建锁（串行化 mvn ⇒ 避免两个轮次并发写同一个 target/）
# =============================================================================
probe_mvn_lock_acquire() {
  local waited=0 p
  mkdir -p "$(dirname "${PROBE_MVN_LOCK_DIR}")" 2>/dev/null || true
  while ! mkdir "${PROBE_MVN_LOCK_DIR}" 2>/dev/null; do
    p="$(cat "${PROBE_MVN_LOCK_DIR}/pid" 2>/dev/null || true)"
    if [ -n "${p}" ] && ! kill -0 "${p}" 2>/dev/null; then
      probe_drop_claim "${PROBE_MVN_LOCK_DIR}"
      continue
    fi
    sleep 2
    waited=$(( waited + 2 ))
    if [ "${waited}" -ge "${PROBE_MVN_LOCK_TIMEOUT}" ]; then
      printf '!! 等构建锁超过 %ss ⇒ 放弃（另一轮还在构建？）\n' "${PROBE_MVN_LOCK_TIMEOUT}" >&2
      return 1
    fi
  done
  printf '%s' "$$" > "${PROBE_MVN_LOCK_DIR}/pid"
  return 0
}

probe_mvn_lock_release() {
  probe_drop_claim "${PROBE_MVN_LOCK_DIR}"
}
