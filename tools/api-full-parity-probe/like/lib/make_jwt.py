#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""签发 like-api 用的 JWT（HS256）—— **两侧共用同一枚 token**。

为什么需要它
------------
Go 侧 `service/like/api/internal/handler/routes.go:49` 的 `rest.WithJwt(secret)`
⇒ `rest/handler/authhandler.go:45-83`：`handler.Authorize(secret)` 解析 token 后
把**所有非标准 claim 原样塞进 ctx**（`authhandler.go:72-79`）。
随后 4 个 logic 用 `l.ctx.Value("userId").(json.Number)` 取身份
（`getlikestatelogic.go:37` / `getuserlikelistlogic.go:37` /
 `getusertotallikelogic.go:37` / `likeactionlogic.go`）。

RAG 侧 `LikeJwtSignatureVerifier.verify(...)` 取的是**同一个 claim 名** `userId`
（`LikeJwtSignatureVerifier.java:113`）⇒ ⇒ **同一枚 token 必须同时被两侧接受**。

⚠️ `userId` 必须是 **JSON 数字**（不是字符串）
------------------------------------------------
- Go：`golang-jwt/jwt/v4@v4.5.2` 的 `MapClaims` 解码用 `UseNumber()`
  ⇒ JSON 数字 ⇒ `json.Number` ⇒ `.(json.Number)` 断言成功。
  字符串 claim（`"userId":"1001"`）⇒ 断言失败 ⇒ Go 返 **1006**。
- RAG：`LikeApiSupport.userId(...)` 只接受整型（Byte/Short/Integer/Long/BigInteger）
  ⇒ 字符串 ⇒ **1006**（`LikeApiSupport.java:95-126`）。
⇒ 两侧一致 ⇒ **数字**。

签名算法
--------
HS256，密钥 = **真源 yaml 里的原始字节**：
- Go：`jwt.ParseWithClaims(..., keyFunc)` ⇒ key = `[]byte(secret)`（`golang-jwt/jwt/v4`）。
- RAG：`Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(secret))`
  （`LikeJwtSignatureVerifier.java:78-80`）—— 本探针用的 secret 是 36 字节 UUID
  ⇒ **≥32 ⇒ padToHmacKeySize 是 no-op** ⇒ 两侧密钥字节完全相同。
"""

import argparse
import base64
import hashlib
import hmac
import json
import sys
import time


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def make_token(secret: str, user_id: int, ttl_seconds: int = 86400) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {"userId": user_id, "exp": int(time.time()) + ttl_seconds}
    signing_input = (
        b64url(json.dumps(header, separators=(",", ":"), sort_keys=True).encode("utf-8"))
        + "."
        + b64url(json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8"))
    )
    signature = hmac.new(
        secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256
    ).digest()
    return signing_input + "." + b64url(signature)


def main() -> int:
    parser = argparse.ArgumentParser(description="签发 like-api 用的 HS256 JWT")
    parser.add_argument("--secret", required=True, help="AccessSecret（真源 yaml 里的原值）")
    parser.add_argument("--user-id", type=int, required=True, help="userId claim（JSON 数字）")
    parser.add_argument("--ttl", type=int, default=86400, help="exp 余量（秒）")
    args = parser.parse_args()
    sys.stdout.write(make_token(args.secret, args.user_id, args.ttl))
    return 0


if __name__ == "__main__":
    sys.exit(main())
