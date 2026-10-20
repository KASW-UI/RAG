package com.rag.users.common.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtUtil} 的密钥长度不变量测试。
 *
 * <p><b>为什么必须有这个测试</b>：jjwt 0.12 的 {@code Keys.hmacShaKeyFor} 强制
 * key ≥ 256 bit，短密钥会抛 {@code WeakKeyException}。admin 侧的默认密钥
 * {@code yU-hCm)2*?NQmWG} 只有 15 字节——直接用原始字节签发必然失败。
 * {@code JwtUtil} 因此在内部把 secret zero-pad 到 32 字节。
 *
 * <p>这个 pad 一旦被删掉，编译不会报错、现有测试也全绿（因为 admin-api 的登录测试
 * 用了 55 字节的专用密钥），只在运行时炸。本测试把它钉死。
 */
class JwtUtilTest {

    /** admin 侧 application.yml 里的真实默认值，15 字节 < 32，必须能签。 */
    private static final String SHORT_ADMIN_SECRET = "yU-hCm)2*?NQmWG";

    /** user 侧 application.yml 里的默认值，36 字节 ≥ 32。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    @Test
    @DisplayName("短密钥（15B）能签发 —— zero-pad 生效，不会抛 WeakKeyException")
    void shortSecret_isPaddedAndSignsSuccessfully() {
        assertTrue(SHORT_ADMIN_SECRET.getBytes(StandardCharsets.UTF_8).length < 32,
                "前置条件：本用例针对的就是 <32 字节的密钥");

        String token = assertDoesNotThrow(() ->
                JwtUtil.getToken(SHORT_ADMIN_SECRET, 1_700_000_000L, 3600L, 42L));

        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    @DisplayName("长密钥（36B）行为不变")
    void longSecret_stillSigns() {
        String token = JwtUtil.getToken(USER_SECRET, 1_700_000_000L, 3600L, 7L);
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    @DisplayName("签发出的 token 能被同一份（同样补齐过的）密钥验签，userId claim 不丢")
    void token_verifiesWithSamePaddedKey() {
        // 用当前时间签发：jjwt 会校验 exp，固定的历史时间戳签出来的 token 一律过期。
        long now = System.currentTimeMillis() / 1000L;
        String token = JwtUtil.getToken(SHORT_ADMIN_SECRET, now, 3600L, 4242L);

        // 关键：验签端通过 JwtUtil.padToHmacKeySize（共用的 public 工具）拿到 key，
        // 而不是自己再实现一份 padding —— 这正是这次从 private 提成 public 的合约。
        Object claim = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(SHORT_ADMIN_SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId");

        assertEquals(4242L, ((Number) claim).longValue());
    }

    /**
     * 钉死签名与验签必须共用同一份 key 推导：
     * 一旦以后有人手贱在拦截器里 inline 了一份自己的 padding 实现（导致与 JwtUtil.getToken
     * 推导出的 key 不一致），同源 token 会在这里验签失败、构造期 throw。
     */
    @Test
    @DisplayName("padToHmacKeySize 是 public —— 跨模块共享签名/验签的单一来源")
    void padIsPublic_andIsTheSingleSourceOfKeyDerivation() {
        // 短密钥：zero-pad 补到 32
        assertEquals(32, JwtUtil.padToHmacKeySize(SHORT_ADMIN_SECRET).length);
        // 边界：空串也补到 32
        assertEquals(32, JwtUtil.padToHmacKeySize("").length);
        // ≥32 字节的密钥：返回原长，不截断
        assertEquals(USER_SECRET.getBytes(StandardCharsets.UTF_8).length,
                JwtUtil.padToHmacKeySize(USER_SECRET).length);
    }
}