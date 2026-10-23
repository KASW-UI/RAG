#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  exp-http-headers-message — message 域 **HTTP 响应头**的双侧逐条对照
# ════════════════════════════════════════════════════════════════════════════
#
# 【为什么必须有这一层】
#   AGENTS.md §3.3 的命题：**「代码里写了」≠「客户端收到」**。
#   `DRIFT-MSGAPI-010`（Content-Type 缺 charset）此前只有**转换器级**证据，
#   `UNRESOLVED-003`（真容器下响应头的完整形状）从未取证。
#   而 MockMvc 测不到这件事：`ContentTypeResultMatcher` 用
#   `expected.isCompatibleWith(actual)` ⇒ `application/json` 与
#   `application/json;charset=utf-8` **互相 compatible** ⇒ 对 charset 完全不敏感。
#   ⇒ 只能**真起容器 + curl dump 全部响应头**。
#
# 【本脚本验什么】
#   4 条路径各 dump 一次两侧的**全部响应头**并逐条对照：
#     ① 401（无 JWT）—— Go: rest/handler/authhandler.go:106 只 WriteHeader(401)、**空 body**
#     ② 404（未知路径）
#     ③ 405（方法不匹配：GET 打一个 POST 路由）
#     ④ 415（Content-Type 不支持）
#   另含 ⑤ 正常 envelope 响应的响应头（带 JWT）。
#
# 【为什么「正常响应」需要 JWT】
#   Go 侧 `rest.WithJwt(UserAuth.AccessSecret)` 覆盖整个 /message/v1 分组；
#   RAG 侧 `MessageJwtAuthInterceptor` 同理 ⇒ 无 token 一律 401。
#   ⇒ 要看「正常响应」的头，必须带一个**两侧都认**的 token。
#   ⚠️ 本脚本用 python3 现签 HS256（secret 取两侧配置里同一个值）。
#   ⚠️ 若签发失败（依赖缺失等），该档**跳过并明确标注**，不静默当成「一致」。
#
# 【端口】
#   默认跟 difftest 的**默认**段一致（Go 18892 / RAG 8892）；
#   并行实例请用 GO_MESSAGE_API_PORT / RAG_MESSAGE_API_PORT 覆盖。
#
# 【用法】
#   ./exp-http-headers-message.sh
#   GO_MESSAGE_API_PORT=20892 RAG_MESSAGE_API_PORT=60992 ./exp-http-headers-message.sh
# ════════════════════════════════════════════════════════════════════════════

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

GO_API="${GO_MESSAGE_API_PORT:-18892}"
RAG_API="${RAG_MESSAGE_API_PORT:-8892}"
GO_BASE="http://127.0.0.1:${GO_API}"
RAG_BASE="http://127.0.0.1:${RAG_API}"

# 两侧配置里的同一份 secret（Go etc/messagecenter.yaml 与 RAG application.yml 一致）
JWT_SECRET="${MESSAGE_JWT_SECRET:-ae0536f9-6450-4606-8e13-5a19ed505da0}"

OUT_DIR="${OUT_DIR:-/tmp/difftest-msg-headers}"
mkdir -p "$OUT_DIR"

fails=0
skips=0

# ── 工具：把响应头规范化（去掉逐次变化的 Date / Server / 长度无关项）────────
normalize() {   # file
  awk 'BEGIN{IGNORECASE=1}
       /^\r?$/ {next}
       { line=$0; gsub(/\r/, "", line) }
       # Date 每次都不同；Server 可能带版本号；Content-Length 随 body 变
       line ~ /^Date:/            {next}
       line ~ /^Server:/          {next}
       line ~ /^Content-Length:/  {next}
       { print tolower(line) }
  ' "$1" | sort
}

dump_case() {  # label, method, path, content_type, body
  local label="$1" method="$2" path="$3" ctype="$4" body="$5"
  local g="$OUT_DIR/$label.go.h" r="$OUT_DIR/$label.rag.h"
  local gb="$OUT_DIR/$label.go.body" rb="$OUT_DIR/$label.rag.body"

  local args=(-s -o "$gb" -D "$g" -X "$method" --max-time 10)
  [[ -n "$ctype" ]] && args+=(-H "Content-Type: $ctype")
  [[ -n "$body"   ]] && args+=(--data "$body")
  curl "${args[@]}" "$GO_BASE$path"  >/dev/null 2>&1 || true

  args=(-s -o "$rb" -D "$r" -X "$method" --max-time 10)
  [[ -n "$ctype" ]] && args+=(-H "Content-Type: $ctype")
  [[ -n "$body"   ]] && args+=(--data "$body")
  curl "${args[@]}" "$RAG_BASE$path" >/dev/null 2>&1 || true

  local gs rs
  gs="$(head -1 "$g" 2>/dev/null | tr -d '\r')"
  rs="$(head -1 "$r" 2>/dev/null | tr -d '\r')"

  normalize "$g" > "$g.n"
  normalize "$r" > "$r.n"

  local diffout
  diffout="$(diff "$g.n" "$r.n" 2>&1)"

  if [[ -z "$diffout" ]]; then
    printf '  ✔ %-34s go=%-28s rag=%-28s 响应头逐条一致\n' "$label" "${gs:-?}" "${rs:-?}"
  else
    fails=$((fails+1))
    printf '  ✗ %-34s go=%-28s rag=%-28s 响应头有差异：\n' "$label" "${gs:-?}" "${rs:-?}"
    echo "$diffout" | sed 's/^/        /'
  fi
}

# ── 签一个两侧都认的 JWT（HS256，claim: userId）────────────────────────
mint_token() {
  python3 - "$JWT_SECRET" <<'PY' 2>/dev/null || true
import base64, hashlib, hmac, json, sys, time
secret = sys.argv[1].encode()
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b'=').decode()
hdr = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(',',':')).encode())
now = int(time.time())
payload = b64(json.dumps({"userId":1,"iat":now,"exp":now+3600}, separators=(',',':')).encode())
signing = f"{hdr}.{payload}".encode()
sig = b64(hmac.new(secret, signing, hashlib.sha256).digest())
print(f"{hdr}.{payload}.{sig}")
PY
}

TOKEN="$(mint_token)"

echo "== message 域 HTTP 响应头双侧对照 =="
echo "   Go  message-api = $GO_BASE"
echo "   RAG message-api = $RAG_BASE"
[[ -z "$TOKEN" ]] && echo "   ⚠️ JWT 签发失败（依赖缺失？）⇒ 正常响应那一档会跳过"
echo

dump_case "401-未带token"        GET  "/message/v1/unread"                  "" ""
dump_case "404-未知路径"         GET  "/message/v1/no-such-endpoint"        "" ""
dump_case "405-方法不匹配"       GET  "/message/v1/notifications/list"      "" ""
dump_case "415-ContentType不对"  POST "/message/v1/notifications/list"      "text/plain" '{"user_id":1}'

if [[ -n "$TOKEN" ]]; then
  for label_path in "200-unread:/message/v1/unread" "200-notiflist:/message/v1/notifications/list"; do
    label="${label_path%%:*}"; path="${label_path##*:}"
    g="$OUT_DIR/$label.go.h"; r="$OUT_DIR/$label.rag.h"
    gb="$OUT_DIR/$label.go.body"; rb="$OUT_DIR/$label.rag.body"
    if [[ "$label" == "200-unread" ]]; then
      curl -s -o "$gb" -D "$g" --max-time 10 -H "Authorization: Bearer $TOKEN" "$GO_BASE$path"  >/dev/null 2>&1 || true
      curl -s -o "$rb" -D "$r" --max-time 10 -H "Authorization: Bearer $TOKEN" "$RAG_BASE$path" >/dev/null 2>&1 || true
    else
      curl -s -o "$gb" -D "$g" --max-time 10 -X POST -H "Authorization: Bearer $TOKEN" \
           -H "Content-Type: application/json" --data '{"user_id":1}' "$GO_BASE$path" >/dev/null 2>&1 || true
      curl -s -o "$rb" -D "$r" --max-time 10 -X POST -H "Authorization: Bearer $TOKEN" \
           -H "Content-Type: application/json" --data '{"user_id":1}' "$RAG_BASE$path" >/dev/null 2>&1 || true
    fi
    normalize "$g" > "$g.n"; normalize "$r" > "$r.n"
    gs="$(head -1 "$g" 2>/dev/null | tr -d '\r')"; rs="$(head -1 "$r" 2>/dev/null | tr -d '\r')"
    d="$(diff "$g.n" "$r.n" 2>&1)"
    if [[ -z "$d" ]]; then
      printf '  ✔ %-34s go=%-28s rag=%-28s 响应头逐条一致\n' "$label" "${gs:-?}" "${rs:-?}"
    else
      fails=$((fails+1))
      printf '  ✗ %-34s go=%-28s rag=%-28s 响应头有差异：\n' "$label" "${gs:-?}" "${rs:-?}"
      echo "$d" | sed 's/^/        /'
    fi
    echo "        go  body: $(head -c 200 "$gb" 2>/dev/null)"
    echo "        rag body: $(head -c 200 "$rb" 2>/dev/null)"
  done
else
  skips=$((skips+1))
  echo "  - 200-正常响应（带JWT）  ** 跳过 **（JWT 签发失败，不静默当成一致）"
fi

echo
echo "失败档数：$fails    跳过档数：$skips"
echo "产物目录：$OUT_DIR"
exit $(( fails > 0 ? 1 : 0 ))
