#!/bin/bash
B=http://127.0.0.1:18898
probe() {
  local name="$1"; shift
  printf "%-42s " "$name"
  curl -s -m 8 -o /tmp/_jb.$$ -w "%{http_code}" "$@" "$B$1" >/dev/null 2>&1
  :
}
row() {
  local name="$1"; shift
  local m="$1"; shift
  local p="$1"; shift
  printf "%-42s -> " "$name"
  curl -s -m 10 -X "$m" -D /tmp/_h.$$ -o /tmp/_b.$$ "$@" "$B$p" 2>/dev/null
  local code=$(head -1 /tmp/_h.$$ 2>/dev/null | awk '{print $2}')
  local ct=$(grep -i '^content-type:' /tmp/_h.$$ | head -1 | tr -d '\r' | cut -d' ' -f2-)
  local ns=$(grep -i '^x-content-type-options:' /tmp/_h.$$ | head -1 | tr -d '\r' | cut -d' ' -f2-)
  local al=$(grep -i '^allow:' /tmp/_h.$$ | head -1 | tr -d '\r' | cut -d' ' -f2-)
  local ac=$(grep -i '^access-control-allow-origin:' /tmp/_h.$$ | head -1 | tr -d '\r' | cut -d' ' -f2-)
  local bd=$(head -c 120 /tmp/_b.$$ 2>/dev/null | tr -d '\n')
  echo "$code ct=\"$ct\" nosniff=\"$ns\" allow=\"$al\" acao=\"$ac\" body=\"$bd\""
  rm -f /tmp/_h.$$ /tmp/_b.$$
}
row "registered GET public"        GET  /v1/article/abc
row "MISMATCH POST /v1/article/1"   POST /v1/article/1
row "MISMATCH PATCH /v1/articles"  PATCH /v1/articles
row "MISMATCH DELETE /v1/articles" DELETE /v1/articles
row "MISMATCH POST /v1/me/article/1" POST /v1/me/article/1
row "UNREGISTERED /v1/nope"        GET  /v1/nope
row "UNREGISTERED /nope"           GET  /nope
row "UNREGISTERED /v1/article/1/x" GET  /v1/article/1/extra
row "JWT no token POST /v1/article" POST /v1/article
row "JWT no token GET me"          GET  /v1/me/article/1
row "OPTIONS preflight PUT"        OPTIONS /v1/article/1 -H "Origin: http://x" -H "Access-Control-Request-Method: PUT"
row "OPTIONS unregistered"         OPTIONS /v1/nope -H "Origin: http://x"
row "trailing slash"               GET  /v1/articles/
row "query empty values"           GET  "/v1/articles?page=&sort_by="
row "query desc=1"                 GET  "/v1/articles?desc=1"
row "query desc=yes(bad)"          GET  "/v1/articles?desc=yes"
row "query page=010"               GET  "/v1/articles?page=010"
row "query page=abc"               GET  "/v1/articles?page=abc"
row "incr_view=1 (public)"         GET  "/v1/article/abc?incr_view=1"
row "incr_view=yes(bad)"           GET  "/v1/article/abc?incr_view=yes"
echo "--- 413 (20MB Content-Length) ---"
head -c 20971520 /dev/zero | tr '\0' ' ' > /tmp/_big.$$
row "413 protected (no token)"     POST /v1/article -H "Content-Type: application/json" --data-binary @/tmp/_big.$$
row "413 public GET"               GET  /v1/article/1 --data-binary @/tmp/_big.$$
row "413 upload"                   POST /v1/upload --data-binary @/tmp/_big.$$
rm -f /tmp/_big.$$
