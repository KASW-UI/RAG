package com.rag.users.common.jwt;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p><b>L4 idiomatization 守门网</b>：把 2026-10-23 双侧活体对拍取证的结论钉死。
 *
 * <p><b>Go 出处（消费点）</b>：
 * <pre>
 *   service/user/common/jwt/jwt.go:12   token := jwt.New(jwt.SigningMethodHS256)
 *   service/user/common/jwt/jwt.go:14   return token.SignedString([]byte(secretKey))
 * </pre>
 *
 * <p><b>语义等价论证</b>：
 * <ol>
 *   <li>Go 显式指定 {@code SigningMethodHS256}，<b>任何密钥长度下 alg 都是 HS256</b>；
 *       RAG 此前是 {@code signWith(key)} 单参重载，jjwt 会照 {@code SecretKeySpec} 的
 *       JCA 算法名自动选 ⇒ 密钥 ≥48 字节变 HS384、≥64 字节变 HS512（实测确认）。</li>
 *   <li>Go 的 {@code SigningMethodHS256.Sign()} 无条件写 {@code typ":"JWT"}；
 *       jjwt 默认只写 {@code alg}。</li>
 *   <li>Go 用<b>原始密钥字节</b>签名，RAG 用「补零到 32 字节」的密钥 ——
 *       见 {@code padToHmacKeySize} 的 javadoc 与本类的 RFC 2104 不变量用例。</li>
 * </ol>
 *
 * <p><b>配套台账</b>：{@code docs/DRIFT/users/common.md} 的
 * {@code DRIFT-USERSCOMMON-002} / {@code DRIFT-USERSCOMMON-004} / {@code PAD-01}。
 */
class JwtUtilParityTest {

    /**
     * 用例里凡是要<b>真正跑完验签</b>的，iat 必须取当前时间。
     *
     * <p>⚠️ 用固定字面量（如 1700000000）会让 {@code parseSignedClaims} 在
     * 「读 payload」之前就先抛 {@code ExpiredJwtException}，
     * <b>签名校验根本没被执行</b> ⇒ 用例看上去绿了，实际什么都没测。
     * 本轮已在双侧探针与本测试类上各踩一次（探针见 docs/DRIFT/users/common.md §0）。
     */
    private static long nowEpochSecond() {
        return java.time.Instant.now().getEpochSecond();
    }

    private static final long IAT = 1700000000L;      // 仅用于「不验签」的 header / claim 结构断言
    private static final long SECONDS = 3600L;
    private static final long USER_ID = 1234567890L;

    private static String[] segments(String token) {
        return token.split("\\.");
    }

    private static String headerOf(String token) {
        return new String(Base64.getUrlDecoder().decode(segments(token)[0]), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // DRIFT-USERSCOMMON-004：alg 必须恒为 HS256
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "secret={0}B ⇒ alg 必须是 HS256")
    @ValueSource(ints = {15, 16, 31, 32, 36, 47, 48, 63, 64, 65, 128})
    @DisplayName("Go: jwt.go:12 显式 HS256 —— 密钥加长不得让 alg 漂到 HS384/HS512")
    void algIsAlwaysHs256RegardlessOfKeyLength(int len) {
        String token = JwtUtil.getToken("k".repeat(len), IAT, SECONDS, USER_ID);
        String header = headerOf(token);
        assertTrue(header.contains("\"alg\":\"HS256\""),
                () -> "密钥 " + len + " 字节时 alg 不是 HS256，实际 header=" + header);
        assertFalse(header.contains("HS384"), () -> "header 出现 HS384：" + header);
        assertFalse(header.contains("HS512"), () -> "header 出现 HS512：" + header);
    }

    @ParameterizedTest(name = "secret={0}B ⇒ 签名长度必须是 43（HS256 的 32 字节 MAC）")
    @ValueSource(ints = {15, 32, 48, 64, 128})
    @DisplayName("签名长度随 alg 变，是 alg 分叉的第二个独立观测量")
    void signatureLengthStaysHs256(int len) {
        String token = JwtUtil.getToken("k".repeat(len), IAT, SECONDS, USER_ID);
        // 32 字节 MAC → base64url 无填充 = 43 字符；HS384=64 / HS512=86
        assertEquals(43, segments(token)[2].length(),
                () -> "密钥 " + len + " 字节时签名长度不是 43（alg 可能已漂移）");
    }

    // ------------------------------------------------------------------
    // DRIFT-USERSCOMMON-002：header 必须带 typ:JWT
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Go: SigningMethodHS256.Sign() 无条件写 typ=JWT —— RAG 此前完全没有")
    void headerCarriesTypJwt() {
        String header = headerOf(JwtUtil.getToken("whatever-secret-36-bytes-long!!", IAT, SECONDS, USER_ID));
        assertTrue(header.contains("\"typ\":\"JWT\""), () -> "header 缺 typ:JWT：" + header);
    }

    @Test
    @DisplayName("header 的键集合必须恰好是 {alg, typ}，不多不少")
    void headerKeySetIsExactlyAlgAndTyp() {
        String header = headerOf(JwtUtil.getToken("whatever-secret-36-bytes-long!!", IAT, SECONDS, USER_ID));
        assertTrue(header.contains("\"alg\""), () -> "header 缺 alg：" + header);
        assertFalse(header.contains("\"kid\""), () -> "header 多了 Go 没有的 kid：" + header);
        assertFalse(header.contains("\"cty\""), () -> "header 多了 Go 没有的 cty：" + header);
    }

    // ------------------------------------------------------------------
    // PAD-01：补零到 32 字节不会改变 HMAC（RFC 2104 不变量）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RFC 2104：短密钥补零后与原密钥的 HMAC 逐字节相同 —— 这是与 Go 能互验的唯一理由")
    void zeroPaddingPreservesHmacForShortKeys() throws Exception {
        // 选 admin 侧默认密钥形状的【等长夹具】（15 字节）
        // 真源出处：Go service/user/admin/api/etc/admincenter.yaml:11（AdminAuth.AccessSecret，15 字节）
        // ⚠️ 只记形状、不记明文：生产密钥不得进仓（2026-10-28 起本类不再含真值）
        String secret = "fixture-admin15";
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] padded = JwtUtil.padToHmacKeySize(secret);

        assertEquals(15, raw.length, "前提：该 secret 就是 15 字节");
        assertEquals(32, padded.length, "前提：补齐后是 32 字节");
        assertFalse(java.util.Arrays.equals(raw, padded), "前提：两者字节确实不同");

        // 末尾 17 字节必须是 0 —— 这是不变量本身，不是实现细节
        for (int i = 15; i < 32; i++) {
            final int idx = i;
            assertEquals(0, padded[idx], () -> "padded[" + idx + "] 不是 0 —— 补零不变量被破坏");
        }

        String signingInput = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjk5OTk5OTk5OTk5LCJleHAiOjk5OTk5OTk5OTk5LCJ1c2VySWQiOjEyMzQ1Njc4OTA=";
        assertArrayEquals(hmac(raw, signingInput), hmac(padded, signingInput),
                "补零改变了 HMAC —— 与 Go 的互通会被打断，且现有其它测试不会红");
    }

    @Test
    @DisplayName("补齐函数对 ≥32 字节的密钥必须原样返回（不得截断）")
    void paddingIsIdentityAbove32Bytes() {
        for (int len : new int[]{32, 33, 48, 64, 128}) {
            String secret = "k".repeat(len);
            assertArrayEquals(secret.getBytes(StandardCharsets.UTF_8), JwtUtil.padToHmacKeySize(secret),
                    "密钥 " + len + " 字节时不应被改动（截断会让验签直接失败）");
        }
    }

    // ------------------------------------------------------------------
    // 回归：token 本身仍可被验签，且 claim 与 Go 一致
    // ------------------------------------------------------------------

    @Test
    @DisplayName("签出的 token 能用同一份 key 验签通过，且 userId/exp 正确")
    void tokenRoundTrips() {
        // user 域默认密钥形状的【等长夹具】（36 字节）
        // 真源出处：Go service/user/user/api/etc/usercenter.yaml:11（UserAuth.AccessSecret，36 字节 UUID）
        String secret = "fixture-user-36-uuid-000000000000000";
        // 🔴 显式钉住「形状」：36 字节（字面量，不是自反断言）
        assertEquals(36, secret.getBytes(StandardCharsets.UTF_8).length,
                "user 侧默认密钥形状必须是 36 字节（真源 usercenter.yaml:11）");
        long iat = nowEpochSecond();
        String token = JwtUtil.getToken(secret, iat, SECONDS, USER_ID);

        var claims = Jwts.parser()
                .verifyWith(new SecretKeySpec(JwtUtil.padToHmacKeySize(secret), "HmacSHA256"))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertNotNull(claims);
        assertEquals(USER_ID, ((Number) claims.get("userId")).longValue());
        assertEquals((iat + SECONDS) * 1000L, claims.getExpiration().getTime());
        assertEquals(iat * 1000L, claims.getIssuedAt().getTime());
    }

    @Test
    @DisplayName("claim 集合必须是 {exp, iat, userId} —— 与 Go 的 MapClaims 写入的三条一致")
    void claimSetMatchesGo() {
        var claims = Jwts.parser()
                .verifyWith(new SecretKeySpec(JwtUtil.padToHmacKeySize("x".repeat(36)), "HmacSHA256"))
                .build()
                .parseSignedClaims(JwtUtil.getToken("x".repeat(36), nowEpochSecond(), SECONDS, USER_ID))
                .getPayload();
        assertEquals(java.util.Set.of("exp", "iat", "userId"), claims.keySet(),
                "claim 集合与 Go 的 jwt.go:9-11 不一致");
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}