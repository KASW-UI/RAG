#!/usr/bin/env bash
# 真实 Tomcat 下断言 text/plain 400 的响应头 —— E-1 收口的端到端守门脚本。
#
# ═════════════════════════════════════════════════════════════════════════
# 【为什么不能用 MockMvc 测这条】
# ═════════════════════════════════════════════════════════════════════════
# 本缺陷**只在真实 Tomcat 下出现**：MockMvc 的 MockHttpServletResponse 在
# `setHeader` / `commit` 时序上与 Tomcat 不同，MockMvc 里 Content-Type 完好
# （已用 AdminApiPlainText400HeaderProbeTest 实测：MockMvc 返回
#   `Content-Type = text/plain;charset=UTF-8` + `X-Content-Type-Options = nosniff`），
# 而真实栈 curl 出来是**空**。
#
# ⇒ 任何用 MockMvc / standaloneSetup 写的断言都会**假绿**。
# 这正是 AGENTS.md §3.4.2「N-1 的二次坍塌」同款陷阱：
# 层次没对齐时，单独那一层全绿就够骗过 CI。
#
# ═════════════════════════════════════════════════════════════════════════
# 【本脚本断言什么】
# ═════════════════════════════════════════════════════════════════════════
# 对两个 RAG 端点各打一次「缺必填字段」请求（应得 400 + text/plain），断言：
#   1) 状态码 = 400
#   2) Content-Type 含 text/plain            ← 本次修复的标的
#   3) X-Content-Type-Options = nosniff      ← 同上
# 并与 Go 真源同一请求的响应头做对照打印。
#
# ⚠️ 2026-10-24 新增 **task 域**（DRIFT-TASK-API-004 的 400 响应头对照）：
#    task-api 的 400 分支（畸形 JSON / 空 body）**不经过 task-rpc**
#    ⇒ 只起 task-api 就能守，不需要把 rpc 也拉起来。
#    端口/地址全部可用环境变量覆盖（见下），默认值对应对拍编排。
#
# 退出码：0 = 全部满足；1 = 有任一项不满足（即可作为 CI 守门）。
set -uo pipefail

RAG_USER="${RAG_USER:-http://127.0.0.1:59900}"
GO_USER="${GO_USER:-http://127.0.0.1:18888}"
RAG_TASK="${RAG_TASK:-http://127.0.0.1:58886}"
GO_TASK="${GO_TASK:-http://127.0.0.1:18886}"
fail=0

# 端口是否可达 —— 用来让「某一域没起」变成 **SKIP** 而不是 **FAIL**。
# ⚠️ 只探 TCP 连通性，不发业务请求：发业务请求去判断「有没有起」会把
#    「起来但业务 400」误判成「没起」。
port_open() {
    local url="$1"
    local host port
    host=$(printf '%s' "$url" | sed -E 's#^https?://([^:/]+).*#\1#')
    port=$(printf '%s' "$url" | sed -E 's#^https?://[^:/]+:?([0-9]*).*#\1#')
    [ -n "$port" ] || port=80
    (exec 3<>"/dev/tcp/$host/$port") 2>/dev/null && { exec 3<&- 3>&-; return 0; }
    return 1
}

probe() {
    local label="$1" url="$2" data="$3"
    local hdr body status
    hdr=$(mktemp); body=$(mktemp)
    status=$(curl -s -o "$body" -D "$hdr" -w '%{http_code}' \
            -X POST "$url" -H 'Content-Type: application/json' \
            -d "$data" --max-time 10 2>/dev/null || echo "000")

    local ct nosniff
    ct=$(grep -i '^content-type:' "$hdr" | head -1 | tr -d '\r' | cut -d' ' -f2-)
    nosniff=$(grep -i '^x-content-type-options:' "$hdr" | head -1 | tr -d '\r' | cut -d' ' -f2-)

    echo "───────────────────────────────────────────────"
    echo "  $label"
    echo "    status                  = $status"
    echo "    Content-Type            = ${ct:-<缺失>}"
    echo "    X-Content-Type-Options  = ${nosniff:-<缺失>}"
    echo "    body                    = $(head -c 100 "$body" | tr '\n' ' ')"

    if [ "$status" != "400" ]; then
        echo "    ✗ 期望 status=400"; fail=1
    fi
    case "$ct" in
        *text/plain*) echo "    ✓ Content-Type 含 text/plain" ;;
        *) echo "    ✗ 期望 Content-Type 含 text/plain"; fail=1 ;;
    esac
    if [ "$nosniff" = "nosniff" ]; then
        echo "    ✓ X-Content-Type-Options = nosniff"
    else
        echo "    ✗ 期望 X-Content-Type-Options = nosniff"; fail=1
    fi

    rm -f "$hdr" "$body"
}

echo "════════════ E-1 真实 Tomcat 响应头守门 ════════════"

if port_open "$RAG_USER"; then
    probe "[RAG] user.register 缺 email（GoRequiredFieldException 分支）" \
          "$RAG_USER/user/v1/register" '{"username":"e1hdr","password":"pw123456"}'

    probe "[RAG] user.register email 类型错（数字塞进 string 字段）" \
          "$RAG_USER/user/v1/register" '{"username":"e2hdr","password":"pw123456","email":123}'
else
    echo "───────────────────────────────────────────────"
    echo "  [SKIP] user 域：$RAG_USER 不可达（该域栈未起）"
fi

# ---- task 域（2026-10-24 新增）----
# ⚠️ task-api 的 400 分支（畸形 JSON / 空 body）**不经过 task-rpc**
#    ⇒ 只起 task-api 就能守。
echo "───────────────────────────────────────────────"
echo "  task 域（DRIFT-TASK-API-004 的 400 响应头对照）"
if port_open "$RAG_TASK"; then
    probe "[RAG] task.get 畸形 JSON" \
          "$RAG_TASK/taskcenter/v1/task/get" '{not json'

    probe "[RAG] task.get 空 body" \
          "$RAG_TASK/taskcenter/v1/task/get" ''
else
    echo "    [SKIP] $RAG_TASK 不可达（task-api 未起）"
fi

echo "───────────────────────────────────────────────"
echo "[参考] Go 真源同一请求的响应头（人工对照用，不参与判定）"
if port_open "$GO_USER"; then
    curl -s -i -X POST "$GO_USER/usercenter/v1/user/register" \
         -H 'Content-Type: application/json' \
         -d '{"username":"e3hdr","password":"pw123456"}' --max-time 10 2>/dev/null | head -8
else
    echo "  [SKIP] $GO_USER 不可达（Go user-api 未起）"
fi
if port_open "$GO_TASK"; then
    echo "───────────────────────────────────────────────"
    curl -s -i -X POST "$GO_TASK/taskcenter/v1/task/get" \
         -H 'Content-Type: application/json' \
         -d '{not json' --max-time 10 2>/dev/null | head -8
else
    echo "  [SKIP] $GO_TASK 不可达（Go task-api 未起）"
fi

echo "════════════════════════════════════════════════"
if [ "$fail" = "0" ]; then
    echo "结果：全部满足 ✅"
    exit 0
else
    echo "结果：存在不满足项 ❌ —— text/plain 400 的响应头仍然丢失"
    exit 1
fi