package com.rag.users.common.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

public final class JwtUtil {
    private JwtUtil() {}

    public static String getToken(String secretKey,
                                  long iat,
                                  long seconds,
                                  long userId) {
        Key key = Keys.hmacShaKeyFor(padToHmacKeySize(secretKey));
        return Jwts.builder()
                .issuedAt(new Date(iat * 1000L))
                .expiration(new Date((iat + seconds) * 1000L))
                .claim("userId", userId)
                .signWith(key)
                .compact();
    }

    /**
     * 把 secret 字符串补齐到 ≥32 字节（HS256 要求 key ≥256 bits），供 {@code Keys.hmacShaKeyFor} 使用。
     *
     * <p><b>为什么是 public</b>：jjwt 0.12 强制要求 key ≥ 256 bit，而 admin 侧的默认密钥
     * {@code yU-hCm)2*?NQmWG} 只有 15 字节。任一调用方（{@code JwtUtil.getToken} 签发、
     * 各 api 模块的 JWT 拦截器/过滤器验签）<b>必须共用同一份补齐过的 key</b>，否则：
     * <ul>
     *   <li>签发端用未补齐的 key → {@code WeakKeyException}</li>
     *   <li>验签端用未补齐的 key → Bean 创建时抛 {@code WeakKeyException} → admin-api 起不来</li>
     *   <li>两侧各自实现 padding → 补齐长度不一致 → 一侧签的 token 另一侧验不过</li>
     * </ul>
     *
     * <p><b>对应 Go</b>：Go 侧 golang-jwt 不强制 key 长度，Go 的 15 字节 admin secret
     * 能直接签；Java 侧的 zero-pad 是 jjwt 强制要求的<b>纯运行环境约束</b>，不属于
     * 行为差异。RAG 实测已证实该改动只在 admin-api 起不来和登录签发失败两个表面
     * 可见，token 本身的 payload、claim、过期行为与 Go 完全一致。
     *
     * <p><b>曾经是 private 并被删过</b>，属于回归。修复路径见原 docstring 删除前的版本。
     */
    public static byte[] padToHmacKeySize(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) {
            return bytes;
        }
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        // 剩余字节已经是 0（Java byte[] 默认初始化为 0）
        return padded;
    }
}