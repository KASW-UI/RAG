#!/usr/bin/env bash
# =============================================================================
# Round 1 / P1-T05 · 「20 个服务在服务」三判据验收
# =============================================================================
# 判据（三条同时成立才算「在服务」，缺一条即失败）：
#   ① Nacos 服务名可查（namespace=public）—— 注册名 ≠ 模块名时以本表为准
#   ② 监听端口 LISTEN（api = HTTP 口 · rpc = gRPC 口）
#   ③ /actuator/health = 200
#
# ⚠️ **4 个服务没有 ③**（**已实测确认，不是漏测**，逐条理由见右列）：
#   · comment-rpc：`spring.main.web-application-type: none` ⇒ 纯 gRPC 进程 ⇒ 无 HTTP 口
#   · task-rpc  ：**无 `spring-boot-starter-web` 依赖** ⇒ 不起 HTTP 口（actuator 依赖在，但无载体）
#   · task-api  ：**无 `spring-boot-starter-actuator` 依赖** ⇒ 无 /actuator/**
#   · user-rpc  ：**compose 容器只发布 gRPC 口**（`ports: "49210:49210"`）⇒ actuator 9094 未对宿主发布
#   ⇒ 这 4 个的判据降为 ①②（**并在矩阵 §4 留证**）
#
# ⚠️ **不得用「进程活着」当在服务**（PIT-GLOBAL-038 / round-01 §5.5 第 10 条）。
# ⚠️ 端口取自 `.env.host`（若存在）⇒ 与**本次实际运行**一致；未设置时用本表默认值
#    （默认值 = yml 里的默认值 ⇒ 即 20 个服务的**标准端口表**）。
#
# 用法：bash docs/RoundPlan/round-01/verify-all-services.sh
# =============================================================================
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
[ -f .env.host ] && { set -a && . ./.env.host && set +a; }
NACOS="http://127.0.0.1:${NACOS_PORT:-49848}"

# service | Nacos 注册名 | 监听口变量:默认值 | 健康口变量:默认值（"NA:原因" = 无健康端点）
TABLE="
user-api|user-api|USER_API_PORT:49900|SELF
user-rpc|user-rpc|USER_RPC_PORT:49210|NA:compose 只发布 gRPC 口
admin-api|admin-api|ADMIN_API_PORT:8890|SELF
admin-rpc|admin-rpc|ADMIN_RPC_PORT:8081|ADMIN_RPC_MANAGEMENT_PORT:9097
message-api|message-api|MESSAGE_API_PORT:8892|SELF
message-rpc|message-rpc|MESSAGE_RPC_PORT:8086|MESSAGE_RPC_MANAGEMENT_PORT:16067
like-api|likecenter|LIKE_API_PORT:18887|SELF
like-rpc|like-rpc|LIKE_RPC_PORT:8085|LIKE_RPC_MANAGEMENT_PORT:9098
task-api|task-api|TASK_API_PORT:18886|NA:无 actuator 依赖
task-rpc|task-rpc|TASK_RPC_PORT:9005|NA:无 web starter ⇒ 无 HTTP 口
article-api|article-api|ARTICLE_API_PORT:8889|SELF
article-rpc|article-rpc|ARTICLE_RPC_PORT:9001|ARTICLE_RPC_MANAGEMENT_PORT:9092
comment-api|comment-api|COMMENT_API_PORT:8888|SELF
comment-rpc|comment-rpc|COMMENT_RPC_PORT:9002|NA:web-application-type=none
favorite-api|favorite-api|FAVORITE_API_PORT:18893|SELF
favorite-rpc|favorite-rpc|FAVORITE_RPC_PORT:8082|FAVORITE_RPC_MANAGEMENT_PORT:16062
follow-api|follow-api|FOLLOW_API_PORT:8891|SELF
follow-rpc|follow-rpc|FOLLOW_RPC_PORT:8083|FOLLOW_RPC_MANAGEMENT_PORT:16065
hot-api|hot-api|HOT_API_PORT:18897|SELF
hot-rpc|hot-rpc|HOT_RPC_GRPC_PORT:8084|HOT_RPC_MANAGEMENT_PORT:19091
"

resolve() {  # $1 = "VAR:default"
  local var="${1%%:*}" def="${1##*:}"
  local v="${!var:-}"
  [ -z "$v" ] && v="$def"
  echo "$v"
}

nacos_count() {
  curl -s --max-time 5 "$NACOS/nacos/v1/ns/instance/list?serviceName=$1&namespaceId=public" 2>/dev/null \
    | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin); print(len(d.get("hosts",[])))
except Exception:
    print(0)' 2>/dev/null || echo 0
}

printf "%-13s %-11s %-6s %-8s %-9s %-7s %s\n" "服务" "Nacos名" "端口" "①Nacos" "②LISTEN" "③health" "结论"
printf "%-13s %-11s %-6s %-8s %-9s %-7s %s\n" "-------------" "-----------" "------" "--------" "---------" "-------" "----"
PASS=0; FAIL=0
while IFS='|' read -r svc name lvar hvar; do
  [ -z "${svc:-}" ] && continue
  port=$(resolve "$lvar")
  n=$(nacos_count "$name")
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then l=Y; else l=N; fi
  case "$hvar" in
    NA:*) h="N/A"; hc="-"; why="${hvar#NA:}" ;;
    SELF) hp="$port"; hc=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$hp/actuator/health" 2>/dev/null)
         [ "$hc" = "200" ] && h=Y || h=N; why="" ;;
    *)    hp=$(resolve "$hvar"); hc=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$hp/actuator/health" 2>/dev/null)
         [ "$hc" = "200" ] && h=Y || h=N; why="" ;;
  esac
  if [ "$n" -ge 1 ] && [ "$l" = "Y" ] && { [ "$h" = "Y" ] || [ "$h" = "N/A" ]; }; then
    r=PASS; PASS=$((PASS+1))
  else
    r=FAIL; FAIL=$((FAIL+1))
  fi
  printf "%-13s %-11s %-6s %-8s %-9s %-7s %-6s %s\n" "$svc" "$name" "$port" "n=$n" "$l" "$hc" "$r" "$why"
done <<< "$TABLE"

echo
echo "PASS=$PASS  FAIL=$FAIL  TOTAL=$((PASS+FAIL))"
[ "$FAIL" -eq 0 ] || exit 1
exit 0
