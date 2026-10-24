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
# 【2026-10-24 补：admin 入口 + charset 断言】（UNRESOLVED-002 / §5 A-4）
# ═════════════════════════════════════════════════════════════════════════
# 原版只有 user 域的两个端点、且只断言「Content-Type **含** text/plain」⇒
#   ① admin-api 侧**完全没覆盖**（users/_shared.md 的 DRIFT-024 修复在 admin 域没守门）；
#   ② 「含 text/plain」**看不见 charset 的有无** ⇒ DRIFT-ADMINAPI-011
#      （RAG 少 charset 参数）就是从这个缝里漏过去的。
# 本版补齐：
#   ① **admin 入口**（public 路径 `/admin/v1/create`，Go 侧 `/admincenter/v1/admin/create`）；
#   ② **charset 断言**：Content-Type 必须**含 charset=utf-8**（大小写不敏感、允许有/无空格）；
#   ③ **两侧同时打并比对**（Go 与 RAG 同一请求）⇒ 不只是「RAG 自己满足」，而是「两侧一致」。
#
# ⚠️ 为什么 charset 不能逐字节比对：
#   Go      : `text/plain; charset=utf-8`   （net/http.Error 逐字写死，含空格）
#   真 Tomcat: `text/plain;charset=utf-8`    （容器重渲染，去掉 `;` 后的空格）
#   ⇒ **字节不同但语义相同** ⇒ 本脚本**归一化后**比对（去空格 + 小写），
#      并单独把两侧原文都打出来供人工核对。
#
# ⚠️ 为什么 admin 只用 `/admin/v1/create`：
#   `/admin/v1/banuser` 是**受保护**路径 ⇒ 不带 JWT 会在鉴权层短路成 **401**，
#   根本走不到 text/plain 分支（DRIFT-ADMINAPI-011 的守门测试第一次跑就踩到了：
#   expected 400 but was 401）。⇒ 只用 public 路径。
#
# 退出码：0 = 所选域全部满足；1 = 有任一项不满足（即可作为 CI 守门）。
#
# ═════════════════════════════════════════════════════════════════════════
# 【分域执行】（2026-10-24 补）
# ═════════════════════════════════════════════════════════════════════════
# 用法：
#   bash tools/difftest/check-plaintext400-headers.sh          # 两个域都跑（默认 all）
#   bash tools/difftest/check-plaintext400-headers.sh admin    # 只跑 admin 域
#   bash tools/difftest/check-plaintext400-headers.sh user     # 只跑 user 域
#   bash tools/difftest/check-plaintext400-headers.sh task     # 只跑 task 域
#
# 为什么需要分域：本脚本是**共享资产**，同时守 user 域与 admin 域。
# 2026-10-24 实测：admin 域两侧**归一化后逐字一致**（✅），
# 而 user 域 RAG 侧**仍缺 charset**（`text/plain` vs Go 的 `text/plain; charset=utf-8`）
# —— 那是 **users/user 域**的账（DRIFT 记录见 docs/DRIFT/users/user/）。
# ⇒ 若不分域，admin 域的守门会被 user 域未修的差异拖红 ⇒ admin 域无法自助用本脚本当门禁。
# ⇒ 分域后：各域的退出码只反映**自己**的断言结果，差异依然可见（跑 all 时仍是红的）。
set -uo pipefail

SCOPE="${1:-all}"
case "$SCOPE" in
    all|admin|user|task) ;;
    *) echo "用法：$0 [all|admin|user|task]" >&2; exit 2 ;;
esac

RAG_USER="${RAG_USER:-http://127.0.0.1:59900}"
GO_USER="${GO_USER:-http://127.0.0.1:18888}"
RAG_ADMIN="${RAG_ADMIN:-http://127.0.0.1:58889}"
GO_ADMIN="${GO_ADMIN:-http://127.0.0.1:18889}"
RAG_TASK="${RAG_TASK:-http://127.0.0.1:58886}"
GO_TASK="${GO_TASK:-http://127.0.0.1:18886}"
fail=0

# 端口是否可达 —— 用来让「某一域没起」变成 **SKIP** 而不是 **FAIL**。
# ⚠️ 只探 TCP 连通性，不发业务请求：发业务请求去判断「有没有起」会把
#    「起来但业务 400」误判成「没起」。
port_open() {
    local url="$1" host port
    host=$(printf '%s' "$url" | sed -E 's#^[a-z]+://([^:/]+).*#\1#')
    port=$(printf '%s' "$url" | sed -E 's#^[a-z]+://[^:/]+:?([0-9]*).*#\1#')
    [[ -z "$port" ]] && port=80
    (exec 3<>"/dev/tcp/$host/$port") >/dev/null 2>&1 && { exec 3<&- 2>/dev/null; exec 3>&- 2>/dev/null; return 0; }
    return 1
}

# 取响应头（一次性取全部字段：status / content-type / nosniff）
fetch() {  # url, body → 写三个全局变量：F_STATUS / F_CT / F_NOSNIFF
    local url="$1" data="$2" hdr body
    hdr=$(mktemp); body=$(mktemp)
    F_STATUS=$(curl -s -o "$body" -D "$hdr" -w '%{http_code}' \
            -X POST "$url" -H 'Content-Type: application/json' \
            -d "$data" --max-time 10 2>/dev/null || echo "000")
    F_CT=$(grep -i '^content-type:' "$hdr" | head -1 | tr -d '\r' | cut -d' ' -f2-)
    F_NOSNIFF=$(grep -i '^x-content-type-options:' "$hdr" | head -1 | tr -d '\r' | cut -d' ' -f2-)
    F_BODY=$(head -c 100 "$body" | tr '\n' ' ')
    rm -f "$hdr" "$body"
}

# 归一化 Content-Type：去空格 + 小写 ⇒ 让「Go 有空格、Tomcat 没空格」这种**渲染差异**不影响比对
norm_ct() { printf '%s' "${1:-}" | tr -d ' ' | tr 'A-Z' 'a-z'; }

# 断言：status=400 / Content-Type 含 text/plain / 含 charset=utf-8 / nosniff 存在
assert_400_headers() {  # label, status, ct, nosniff
    local label="$1" status="$2" ct="$3" nosniff="$4"
    local nct; nct=$(norm_ct "$ct")
    if [ "$status" != "400" ]; then
        echo "    ✗ 期望 status=400，实际 $status"; fail=1
    else
        echo "    ✓ status=400"
    fi
    case "$nct" in
        *text/plain*) echo "    ✓ Content-Type 含 text/plain" ;;
        *) echo "    ✗ 期望 Content-Type 含 text/plain，实际 '${ct:-<缺失>}'"; fail=1 ;;
    esac
    case "$nct" in
        *charset=utf-8*) echo "    ✓ Content-Type 含 charset=utf-8" ;;
        *) echo "    ✗ 期望 Content-Type 含 charset=utf-8，实际 '${ct:-<缺失>}'"; fail=1 ;;
    esac
    if [ "$nosniff" = "nosniff" ]; then
        echo "    ✓ X-Content-Type-Options = nosniff"
    else
        echo "    ✗ 期望 X-Content-Type-Options = nosniff，实际 '${nosniff:-<缺失>}'"; fail=1
    fi
}

# 双侧对打同一个请求
probe_pair() {  # label, rag_url, go_url, body
    local label="$1" rag_url="$2" go_url="$3" data="$4"

    fetch "$rag_url" "$data"
    local rag_status="$F_STATUS" rag_ct="$F_CT" rag_ns="$F_NOSNIFF" rag_body="$F_BODY"
    fetch "$go_url" "$data"
    local go_status="$F_STATUS" go_ct="$F_CT" go_ns="$F_NOSNIFF"

    echo "───────────────────────────────────────────────"
    echo "  $label"
    echo "    [RAG] status=$rag_status  Content-Type='${rag_ct:-<缺失>}'  nosniff='${rag_ns:-<缺失>}'"
    echo "    [Go ] status=$go_status  Content-Type='${go_ct:-<缺失>}'  nosniff='${go_ns:-<缺失>}'"
    echo "    [RAG body] $rag_body"

    echo "    ── RAG 侧断言 ──"
    assert_400_headers "$label (RAG)" "$rag_status" "$rag_ct" "$rag_ns"

    echo "    ── Go 侧（真源，判定其自身合规） ──"
    assert_400_headers "$label (Go)" "$go_status" "$go_ct" "$go_ns"

    echo "    ── 两侧一致性 ──"
    if [ "$(norm_ct "$rag_ct")" = "$(norm_ct "$go_ct")" ]; then
        echo "    ✓ 两侧 Content-Type 归一化后一致"
    else
        echo "    ✗ 两侧 Content-Type 不一致：RAG='${rag_ct}' vs Go='${go_ct}'"; fail=1
    fi
}

echo "════════════ E-1 真实 Tomcat 响应头守门（scope=${SCOPE}） ════════════"
echo "    RAG user  = $RAG_USER"
echo "    Go  user  = $GO_USER"
echo "    RAG admin = $RAG_ADMIN"
echo "    Go  admin = $GO_ADMIN"

if [[ "$SCOPE" == "all" || "$SCOPE" == "user" ]]; then
    # ⚠️ 域栈没起 ⇒ SKIP 而非 FAIL：某个域的差异不该拖红其它域的守门。
    if port_open "$RAG_USER"; then
        probe_pair "[user] register 缺 email（GoRequiredFieldException 分支）" \
            "$RAG_USER/user/v1/register" "$GO_USER/usercenter/v1/user/register" \
            '{"username":"e1hdr","password":"pw123456"}'

        probe_pair "[user] register email 类型错（数字塞进 string 字段）" \
            "$RAG_USER/user/v1/register" "$GO_USER/usercenter/v1/user/register" \
            '{"username":"e2hdr","password":"pw123456","email":123}'
    else
        echo "    [SKIP] user 域：$RAG_USER 不可达（该域栈未起）"
    fi
fi

if [[ "$SCOPE" == "all" || "$SCOPE" == "admin" ]]; then
    if port_open "$RAG_ADMIN"; then
        probe_pair "[admin] create 缺 username（public 路径 ⇒ 400 text/plain）" \
            "$RAG_ADMIN/admin/v1/create" "$GO_ADMIN/admincenter/v1/admin/create" \
            '{}'

        probe_pair "[admin] create email 类型错" \
            "$RAG_ADMIN/admin/v1/create" "$GO_ADMIN/admincenter/v1/admin/create" \
            '{"username":"e4hdr","password":"pw123456","email":123}'
    else
        echo "    [SKIP] admin 域：$RAG_ADMIN 不可达（该域栈未起）"
    fi
fi

if [[ "$SCOPE" == "all" || "$SCOPE" == "task" ]]; then
    # ⚠️ task-api 的 400 分支（畸形 JSON / 空 body）**不经过 task-rpc**
    #    ⇒ 只起 task-api 就能守（DRIFT-TASK-API-004）。
    if port_open "$RAG_TASK"; then
        probe_pair "[task] get 畸形 JSON" \
            "$RAG_TASK/taskcenter/v1/task/get" "$GO_TASK/taskcenter/v1/task/get" \
            '{not json'

        probe_pair "[task] get 空 body" \
            "$RAG_TASK/taskcenter/v1/task/get" "$GO_TASK/taskcenter/v1/task/get" \
            ''
    else
        echo "    [SKIP] task 域：$RAG_TASK 不可达（task-api 未起）"
    fi
fi

echo "════════════════════════════════════════════════"
if [ "$fail" = "0" ]; then
    echo "结果（scope=${SCOPE}）：全部满足 ✅"
    exit 0
else
    echo "结果（scope=${SCOPE}）：存在不满足项 ❌ —— text/plain 400 的响应头仍有问题（status / charset / nosniff）"
    exit 1
fi
