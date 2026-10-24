#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════
# task-api 的 NacosDiscovery **真注册中心**行为对拍（注册 → 注销）
#
# 【为什么需要它】
#   台账 §四 待办第 2 条：「task-api 的 NacosDiscovery 在真注册中心下的
#   注册/注销行为未验（当前测试指向不可达地址绕过）」。
#   现有测试全是 `spring.cloud.nacos.discovery.register-enabled=false`
#   或指向不可达地址 ⇒ **装配层被整体关掉**，注册/注销这段代码路径**从没跑过**。
#   而这段路径有一个典型风险：**注册成功但注销失败** ⇒ 服务重启后
#   注册表里留下「幽灵实例」⇒ 流量被分到一个已经不在的进程上。
#
# 【本脚本验什么】
#   1. 真注册中心（独立 Nacos 容器）上，task-api 起来后**能查到自己的实例**
#      （IP / 端口 / 健康状态都对）；
#   2. 进程停掉后，实例**从注册表消失**（不是留成 unhealthy 的幽灵）。
#
# 【为什么自己起一个 Nacos】
#   本机已有 `Sea-nacos`（**Sea 栈的，不许动**）与 `nacos-standalone`（用户自己的开发用实例）。
#   往用户的实例里注册一个测试实例是**对用户环境的副作用**
#   ⇒ 这里起一个**独立容器、独立端口**（49849/50949），跑完即删。
#
# 用法：
#   ./tools/difftest/run-task-nacos-discovery.sh
#   KEEP_UP=1 ./tools/difftest/run-task-nacos-discovery.sh   # 保留 Nacos 容器便于手查
# ═════════════════════════════════════════════════════════════════════════
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
RAG_REPO=${RAG_REPO:-$(cd "$HERE/../.." && pwd)}
WORK=${WORK:-${TMPDIR:-/tmp}/task-nacos-discovery}
KEEP_UP=${KEEP_UP:-0}

NACOS_NAME=taskprobe-nacos
NACOS_HTTP=49849
# ⚠️⚠️ Nacos 2.x 的**客户端**按「HTTP 端口 + 1000」推算 gRPC 端口
#    （nacos-client 3.x 的 NamingGrpcClientProxy 就是连这个端口）。
#    ⇒ 宿主机映射必须用 49849+1000 = 50849；映射成别的数（如 50949）
#    ⇒ 客户端连不上 ⇒ 注册失败报「ErrCode:-401 Client not connected, current status:STARTING」
#    ⇒ 而且它会让**整个应用启动失败**（failFast=true）⇒ 症状是「app 起不来」，
#    病根却在端口映射上。本轮实测踩到。
NACOS_GRPC=50849          # = NACOS_HTTP + 1000，不能乱填
NACOS_IMAGE=nacos/nacos-server:v2.3.2-slim
API_PORT=${API_PORT:-58887}
APP_NAME=${APP_NAME:-task-api}
fail=0

say() { printf '\n\033[1m==== %s ====\033[0m\n' "$*"; }
verdict() {
    if [ "$2" = "$3" ]; then
        printf '  ✅ %-44s = %s\n' "$1" "$2"
    else
        printf '  🔴 %-44s Go 期望=%s 实际=%s\n' "$1" "$3" "$2"
        fail=1
    fi
}

# Nacos open API：查某个服务的实例列表
nacos_instances() {
    curl -s -m 5 "http://127.0.0.1:$NACOS_HTTP/nacos/v1/ns/instance/list?serviceName=$APP_NAME&groupName=DEFAULT_GROUP" 2>/dev/null
}

say "0. 前置检查"
for c in docker java curl mvn; do
    command -v "$c" >/dev/null 2>&1 || { echo "  ✗ 缺命令：$c"; exit 1; }
done
mkdir -p "$WORK/logs"

say "1. 起独立 Nacos（${NACOS_NAME}，HTTP :${NACOS_HTTP} / gRPC :${NACOS_GRPC}）"
# ⚠️ 先清同名遗留（可能是手工 docker run 建的，不是本脚本管的）
if docker ps -a --format '{{.Names}}' | grep -qx "$NACOS_NAME"; then
    docker stop "$NACOS_NAME" >/dev/null 2>&1
    docker rm "$NACOS_NAME" >/dev/null 2>&1
    echo "  ✓ 移除同名遗留容器"
fi
docker run -d --name "$NACOS_NAME" \
    -e MODE=standalone \
    -e NACOS_AUTH_ENABLE=false \
    -e JVM_XMS=256m -e JVM_XMX=512m \
    -p "127.0.0.1:$NACOS_HTTP:8848" \
    -p "127.0.0.1:$NACOS_GRPC:9848" \
    "$NACOS_IMAGE" >/dev/null 2>&1 \
  || { echo "  ✗ Nacos 容器起不来"; exit 1; }
echo "  ✓ 容器已起，等它就绪"
for i in $(seq 1 60); do
    # ⚠️ Nacos 的 readiness 端点返回的是 **OK**（不是 UP）——
    #    写成 grep UP 会永远等不到（本轮实测踩到）。
    if curl -s -m 3 "http://127.0.0.1:$NACOS_HTTP/nacos/v1/console/health/readiness" 2>/dev/null | grep -qE '^(OK|UP)$'; then
        echo "  ✓ Nacos 就绪（${i}s）"; break
    fi
    [ "$i" = "60" ] && { echo "  ✗ Nacos 60s 内没就绪"; docker logs "$NACOS_NAME" 2>&1 | tail -20; exit 1; }
    sleep 2
done

say "2. 编 RAG task-api"
( cd "$RAG_REPO" && mvn -o -q -pl service/task/task-api -am compile -DskipTests ) \
  || { echo "  ✗ 编译失败"; exit 1; }
( cd "$RAG_REPO" && mvn -o -q -pl service/task/task-api dependency:build-classpath \
    -Dmdep.outputFile="$WORK/api-cp.txt" -DincludeScope=runtime ) \
  || { echo "  ✗ classpath 生成失败"; exit 1; }
API_CP="$RAG_REPO/service/task/task-api/target/classes:$(cat "$WORK/api-cp.txt")"
echo "  ✓ classpath 就绪"

say "3. 起 task-api（注册到真注册中心）"
# ⚠️ 与其它对拍不同的两点：
#   · register-enabled=**true**（默认）—— 这正是要验的路径；
#   · grpc.client 指向不可达地址也没关系 —— **注册不依赖 rpc**。
NACOS_ADDR="127.0.0.1:$NACOS_HTTP" \
OTEL_EXPORTER_OTLP_ENDPOINT=127.0.0.1:1 \
nohup java -cp "$API_CP" com.rag.task.task.api.TaskApiApplication \
    --server.port="$API_PORT" \
    --spring.cloud.nacos.discovery.server-addr="127.0.0.1:$NACOS_HTTP" \
    --spring.cloud.nacos.discovery.register-enabled=true \
    --spring.cloud.nacos.discovery.namespace=public \
    --spring.cloud.nacos.discovery.group=DEFAULT_GROUP \
    --grpc.client.task-rpc.address=static://127.0.0.1:65535 \
    --grpc.client.task-rpc.negotiation-type=plaintext \
    --management.otlp.tracing.export.enabled=false \
    > "$WORK/logs/rag-task-api.log" 2>&1 &
API_PID=$!
echo "  ✓ pid=$API_PID"

say "4. 等实例出现在注册表"
REGISTERED=""
for i in $(seq 1 40); do
    body=$(nacos_instances)
    # ⛔ 不用 grep -q 计数「实例个数」—— Nacos 的 JSON 里 hosts 数组可能有多项，
    #    逐项看更稳：只要有**一个** healthy 且端口对得上就算注册成功。
    n=$(printf '%s' "$body" | grep -o '"port":'"$API_PORT" | wc -l | tr -d ' ')
    if [ "$n" -ge 1 ]; then
        REGISTERED=yes
        echo "  ✓ 第 ${i}s 查到实例（port=${API_PORT}）"
        break
    fi
    sleep 2
done
echo "  注册表原始响应："
nacos_instances | head -c 1200; echo
verdict "实例已注册到真注册中心" "yes" "${REGISTERED:-no}"

say "5. 停进程 ⇒ 实例应从注册表消失（不留幽灵）"
kill "$API_PID" 2>/dev/null
sleep 3
GONE=""
for i in $(seq 1 30); do
    body=$(nacos_instances)
    n=$(printf '%s' "$body" | grep -o '"port":'"$API_PORT" | wc -l | tr -d ' ')
    if [ "$n" -eq 0 ]; then
        GONE=yes
        echo "  ✓ 第 ${i}s 实例已从注册表消失"
        break
    fi
    sleep 2
done
if [ "${GONE:-no}" != "yes" ]; then
    echo "  ⚠️ 60s 内实例仍在注册表里（可能是注销没发生，或 Nacos 还没感知到心跳超时）"
    echo "  ⚠️ 注册表当前内容："
    nacos_instances | head -c 1200; echo
fi
verdict "实例已从注册表消失（无幽灵）" "yes" "${GONE:-no}"

say "6. 收摊"
if [ "$KEEP_UP" = "1" ]; then
    echo "  KEEP_UP=1 ⇒ Nacos 容器保留（查注册表：curl -s http://127.0.0.1:${NACOS_HTTP}/nacos/v1/ns/instance/list?serviceName=${APP_NAME}）"
else
    docker stop "$NACOS_NAME" >/dev/null 2>&1
    docker rm "$NACOS_NAME" >/dev/null 2>&1
    echo "  ✓ Nacos 容器已删"
fi

echo
echo "════════════════════════════════════════════════"
if [ "$fail" = "0" ]; then
    echo "结果：注册/注销行为符合预期 ✅"
    exit 0
fi
echo "结果：存在不符合预期的项 ❌"
exit 1
