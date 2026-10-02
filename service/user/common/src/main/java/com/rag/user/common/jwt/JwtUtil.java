package com.rag.user.common.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

public final class JwtUtil {
    private JwtUtil() {}

    public static String getToken(String secretKey, long iat, long seconds, long userId) {
        Key key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setIssuedAt(new Date(iat * 1000L))
                .setExpiration(new Date((iat + seconds) * 1000L))
                .claim("userId", userId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}