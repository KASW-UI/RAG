package com.rag.comment.comment.api.interceptor;

import com.rag.users.common.jwt.JwtUtil;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>JWT 接受矩阵的守门网</b> —— 锁住 comment-api 的 token 接受/拒绝行为与 Go 真源一致。
 *
 * <h2>为什么要有这个</h2>
 * <p>本仓此前只有「正常 token 能过」「坏 token 被拒」这类<b>单点</b>断言，
 * 不知道 jjwt 与 golang-jwt 在<b>各种 token 形态</b>上的判别是否一致。
 * <p>⇒ 2026-10-24 用<b>双侧探针</b>（Go 侧 {@code /tmp/jwtprobe} 走 go-zero 的
 * {@code rest/token.TokenParser}；Java 侧走本拦截器的真实代码路径，
 * 两侧跑<b>字节完全相同</b>的 token）把 11 种形态逐条对拍。
 *
 * <h2>Go 侧的判据出处</h2>
 * <pre>
 *   golang-jwt/jwt/v4@v4.5.2
 *     map_claims.go:124-141  MapClaims.Valid() → VerifyExpiresAt / VerifyIssuedAt / VerifyNotBefore
 *     map_claims.go:37-43    VerifyExpiresAt：exp 缺失 ⇒ !req ⇒ 有效
 *     map_claims.go:63-85    VerifyIssuedAt：iat 缺失 ⇒ 有效；是数字 ⇒ now >= iat；
 *                            **其它类型 ⇒ 直接 return false（拒绝）**
 *     claims.go:250-255      verifyIat：now >= iat（**无时钟偏移容忍**）
 *     hmac.go:49-74         SigningMethodHMAC.Verify：**不限制 alg** ⇒ HS256/384/512 都能验
 *     map_claims.go:118-121  'none' 由 Parse 显式拒绝（"signature type is not allowed"）
 *   go-zero@v1.10.2
 *     rest/token/tokenparser.go:81-84  doParseToken → request.ParseFromRequest
 *     rest/token/tokenparser.go:127-129 newParser() = jwt.NewParser(jwt.WithJSONNumber())
 *                                  ⇒ **没有** WithValidMethods ⇒ 不限制 alg
 * </pre>
 *
 * <h2>探针实测的对照结果（2026-10-24）</h2>
 * <table border="1">
 *   <caption>11 种形态的双侧判定</caption>
 *   <tr><th>形态</th><th>Go</th><th>RAG</th><th>结论</th></tr>
 *   <tr><td>HS256 + exp + userId</td><td>接受</td><td>接受</td><td>✅</td></tr>
 *   <tr><td>HS256 + <b>缺 exp</b></td><td>接受</td><td>接受</td><td>✅（本轮<b>实测</b>，此前是未知项）</td></tr>
 *   <tr><td><b>HS384</b> + exp</td><td><b>接受</b></td><td><b>拒绝</b></td>
 *       <td>🔴 <b>差异</b>：jjwt 把 key 绑死在 HS256 ⇒ 报 WeakKeyException</td></tr>
 *   <tr><td><b>HS512</b> + exp</td><td><b>接受</b></td><td><b>拒绝</b></td>
 *       <td>🔴 <b>差异</b>：同上</td></tr>
 *   <tr><td>HS256 + <b>iat 在未来</b></td><td><b>拒绝</b></td><td><b>拒绝</b></td>
 *       <td>✅ <b>本轮修好</b>：此前 jjwt 不校验 iat ⇒ RAG <b>比 Go 松</b>（安全面更宽）</td></tr>
 *   <tr><td>HS256 + nbf 在未来</td><td>拒绝</td><td>拒绝</td><td>✅</td></tr>
 *   <tr><td>HS256 + 已过期</td><td>拒绝</td><td>拒绝</td><td>✅</td></tr>
 *   <tr><td>alg=none</td><td>拒绝</td><td>拒绝</td><td>✅</td></tr>
 *   <tr><td>HS256 + 签名错</td><td>拒绝</td><td>拒绝</td><td>✅</td></tr>
 *   <tr><td>HS256 + userId 是字符串</td><td>接受</td><td>接受</td><td>✅</td></tr>
 *   <tr><td>HS256 + userId 是小数</td><td>接受</td><td>接受</td><td>✅（<b>后续</b>由 logic 层判 1006）</td></tr>
 * </table>
 *
 * <h2>⚠️ HS384 / HS512 这两条：断言的是<b>RAG 当前的行为</b>，不是 Go 的</h2>
 * <p>jjwt 的 {@code verifyWith(key)} 把 key <b>绑死</b>在一个算法上
 * （{@code Keys.hmacShaKeyFor} 对 32 字节 ⇒ HmacSHA256）
 * ⇒ token 头写 HS384/HS512 时直接 {@code WeakKeyException}。
 * <p>⇒ 这两条已登记为<b>待签字偏离</b>（{@code SD-2026-10-24-CAND-07}，RAG 更严的一侧）。
 * ⚠️ <b>签字前本断言保持现状</b>；若将来决定对齐 Go（放宽），<b>这两条断言必须同步改</b>，
 * 否则测试会「因为错误的原因而绿」。
 *
 * <h2>🧬 变异实测</h2>
 * <p>把验签器里的 {@code iat} 校验整段删掉（= 修复前）
 * ⇒ {@link #issuedAtInTheFutureIsRejected()} 变红 ⇒ 守门网不是假绿。
 * <p>⚠️ 2026-10-27：{@code iat} 校验随验签一起搬进了
 * {@code auth/CommentJwtSignatureVerifier}（DRIFT-USERAPI-009②）⇒ 变异点同步位移。
 */
class CommentJwtTokenMatrixGateTest {

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    private CommentJwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        com.rag.comment.comment.api.config.CommentApiProperties props =
                new com.rag.comment.comment.api.config.CommentApiProperties();
        props.getUserAuth().setAccessSecret(SECRET);
        // ⚠️ 2026-10-27（DRIFT-USERAPI-009② 落码）：验签已从拦截器搬进
        //    `auth/CommentJwtSignatureVerifier`（由 `auth/GoStyleJwtAuthFilter` 在 3s 预算之外调用），
        //    拦截器改成**消费**验签结果 ⇒ 构造器改为注入 verifier。
        // ⇒ 本类直接 new 拦截器、**不经过 Filter** ⇒ 走的是拦截器的**兜底（inline）分支**
        //    ⇒ 顺带把「兜底路径」也纳入这张 token 矩阵的覆盖（不是缺陷，是额外收益）。
        interceptor = new CommentJwtAuthInterceptor(
                new com.rag.comment.comment.api.auth.CommentJwtSignatureVerifier(props));
    }

    // ====================================================================================
    // token 构造：与 /tmp/jwtprobe 的 Go 探针**同款字节构造**
    // ====================================================================================

    private static String b64(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * 手工拼 token —— 不用 jjwt 的 builder，是为了能指定<b>任意 HMAC 算法</b>
     * （jjwt 的 {@code signWith} 要求 key 长度匹配算法，造不出与 Go 字节一致的 HS384/HS512）。
     *
     * @param alg    HS256 / HS384 / HS512 / none
     * @param claims 键值对（按插入顺序序列化）
     */
    private static String token(String alg, Map<String, Object> claims) {
        String header = "{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}";
        StringBuilder payload = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : claims.entrySet()) {
            if (!first) {
                payload.append(',');
            }
            first = false;
            payload.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) {
                payload.append(v);
            } else {
                payload.append('"').append(v).append('"');
            }
        }
        payload.append('}');
        String signingString = b64(header.getBytes(StandardCharsets.UTF_8))
                + "." + b64(payload.toString().getBytes(StandardCharsets.UTF_8));
        if ("none".equals(alg)) {
            return signingString + ".";
        }
        String macAlg = "HS256".equals(alg) ? "HmacSHA256"
                : "HS384".equals(alg) ? "HmacSHA384" : "HmacSHA512";
        try {
            Mac mac = Mac.getInstance(macAlg);
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), macAlg));
            return signingString + "." + b64(mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> claims(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 跑一次拦截器：返回 true=放行，false=401。 */
    private boolean accepted(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/comment/v1/comment/list");
        request.addHeader("Authorization", "Bearer " + token);
        try {
            interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 最小控制器：只要让 handler 是 HandlerMethod（拦截器用它判「路由是否匹配上」）。 */
    static class NoopController {
        public void list() {
        }
    }

    /** 构造一次就复用（HandlerMethod 的构造是 checked 异常 ⇒ 放在静态初始化里吞掉）。 */
    private static final HandlerMethod HANDLER = buildHandler();

    private static HandlerMethod buildHandler() {
        try {
            return new HandlerMethod(new NoopController(), "list");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HandlerMethod handlerMethod() {
        return HANDLER;
    }

    private static long nowSec() {
        return Instant.now().getEpochSecond();
    }

    // ====================================================================================
    // 与 Go 一致的 8 条
    // ====================================================================================

    @Test
    @DisplayName("✅ HS256 + exp + userId ⇒ 接受（与 Go 一致）")
    void hs256NormalIsAccepted() {
        String t = token("HS256", claims("userId", 1001, "exp", nowSec() + 3600, "iat", nowSec()));
        assertTrue(accepted(t));
    }

    @Test
    @DisplayName("✅ HS256 + **缺 exp** ⇒ 接受（Go map_claims.go:37-43：exp 缺失 ⇒ !req ⇒ 有效）")
    void missingExpIsAccepted() {
        String t = token("HS256", claims("userId", 1001, "iat", nowSec()));
        assertTrue(accepted(t),
                "Go 的 VerifyExpiresAt(cmp, req=false) 在 exp 缺失时返回 true ⇒ 两侧都应接受");
    }

    @Test
    @DisplayName("✅ HS256 + **iat 在未来** ⇒ **拒绝**（Go claims.go:250-255：now >= iat）")
    void issuedAtInTheFutureIsRejected() {
        String t = token("HS256", claims("userId", 1001, "exp", nowSec() + 3600, "iat", nowSec() + 3600));
        assertEquals(false, accepted(t),
                "Go 的 MapClaims.Valid() 会因 iat 在未来而失败 ⇒ authhandler 走 401。"
                        + "⚠️ 修复前 jjwt **不校验 iat** ⇒ RAG 会接受 ⇒ 比 Go 松（安全面更宽）");
    }

    @Test
    @DisplayName("✅ HS256 + nbf 在未来 ⇒ 拒绝（与 Go 一致）")
    void notBeforeInTheFutureIsRejected() {
        String t = token("HS256", claims("userId", 1001, "exp", nowSec() + 3600,
                "iat", nowSec(), "nbf", nowSec() + 3600));
        assertEquals(false, accepted(t));
    }

    @Test
    @DisplayName("✅ HS256 + 已过期 ⇒ 拒绝（与 Go 一致）")
    void expiredIsRejected() {
        String t = token("HS256", claims("userId", 1001, "exp", nowSec() - 3600, "iat", nowSec() - 7200));
        assertEquals(false, accepted(t));
    }

    @Test
    @DisplayName("✅ alg=none ⇒ 拒绝（Go：'none' signature type is not allowed）")
    void noneAlgorithmIsRejected() {
        String t = token("none", claims("userId", 1001, "exp", nowSec() + 3600));
        assertEquals(false, accepted(t));
    }

    @Test
    @DisplayName("✅ HS256 + 签名错 ⇒ 拒绝（与 Go 一致）")
    void badSignatureIsRejected() throws Exception {
        String t = token("HS256", claims("userId", 1001, "exp", nowSec() + 3600));
        // ⚠️ 必须改**签名段中部**：base64 末位有 2 bit 是填充位，改末位可能解码出同样的字节
        String[] parts = t.split("\\.");
        char mid = parts[2].charAt(parts[2].length() / 2);
        parts[2] = parts[2].substring(0, parts[2].length() / 2)
                + (mid == 'A' ? 'B' : 'A')
                + parts[2].substring(parts[2].length() / 2 + 1);
        assertEquals(false, accepted(String.join(".", parts)));
    }

    @Test
    @DisplayName("✅ userId claim 是字符串 / 小数 ⇒ **鉴权层放行**（后续由 logic 层判 1006）")
    void nonNumericUserIdPassesAuthLayer() {
        assertTrue(accepted(token("HS256", claims("userId", "1001", "exp", nowSec() + 3600))));
        assertTrue(accepted(token("HS256", claims("userId", 1.5d, "exp", nowSec() + 3600))));
    }

    // ====================================================================================
    // 与 Go 不同的 2 条：已登记为待签字偏离（SD-2026-10-24-CAND-07）
    // ====================================================================================

    @Test
    @DisplayName("🔴 HS384 ⇒ RAG **拒绝**（Go 接受）—— 已登记为待签字偏离 CAND-07")
    void hs384IsRejectedByRag() {
        String t = token("HS384", claims("userId", 1001, "exp", nowSec() + 3600, "iat", nowSec()));
        assertEquals(false, accepted(t),
                "jjwt 把 key 绑死在 HS256（Keys.hmacShaKeyFor 对 32 字节）⇒ HS384 报 WeakKeyException。"
                        + "⚠️ 这是与 Go 的**已知差异**（Go hmac.go:49-74 不限制 alg）"
                        + " ⇒ 已登记 SD-2026-10-24-CAND-07；**签字前保持现状**。");
    }

    @Test
    @DisplayName("🔴 HS512 ⇒ RAG **拒绝**（Go 接受）—— 已登记为待签字偏离 CAND-07")
    void hs512IsRejectedByRag() {
        String t = token("HS512", claims("userId", 1001, "exp", nowSec() + 3600, "iat", nowSec()));
        assertEquals(false, accepted(t),
                "同上（CAND-07）。⚠️ 签字前保持现状；签字后本断言必须同步改。");
    }

    // ====================================================================================
    // 附带的等价性证明：padding 是空操作
    // ====================================================================================

    @Test
    @DisplayName("✅ 短 secret 的 zero-pad **不改变 HMAC 结果** ⇒ 与 Go 的签名天然互通")
    void paddingDoesNotChangeTheHmacKey() throws Exception {
        // Go 侧用原始 secret 字节做 HMAC key；RAG 侧用 zero-pad 到 32 字节。
        // ⚠️ HMAC 本来就把短 key 零填充到块长（64 字节）⇒ 两者**恒等**。
        // ⇒ 「jjwt 要求 key ≥ 32 字节」这个运行环境约束**不产生行为差异**。
        String shortSecret = "yU-hCm)2*?NQmWG";   // 15 字节
        byte[] raw = shortSecret.getBytes(StandardCharsets.UTF_8);
        byte[] padded = JwtUtil.padToHmacKeySize(shortSecret);
        assertEquals(32, padded.length);

        Mac mac1 = Mac.getInstance("HmacSHA256");
        mac1.init(new SecretKeySpec(raw, "HmacSHA256"));
        Mac mac2 = Mac.getInstance("HmacSHA256");
        mac2.init(new SecretKeySpec(padded, "HmacSHA256"));
        byte[] data = "comment-domain-padding-check".getBytes(StandardCharsets.UTF_8);
        assertTrue(java.util.Arrays.equals(mac1.doFinal(data), mac2.doFinal(data)),
                "短 secret 与 zero-pad 后的 secret 在 HMAC 下必须完全等价");
    }
}
