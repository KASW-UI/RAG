package com.rag.users.admin.api.auth;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.config.AdminApiProperties;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.BearerTokenExtractor;
import com.rag.users.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * admin JWT <b>验签</b> —— 只做「解析 + 验签名 + 取 claim」，<b>不写响应、不查 Redis</b>。
 *
 * <h2>为什么从 {@code AdminJwtAuthInterceptor} 里搬出来</h2>
 *
 * <p>DRIFT-USERAPI-009②：Go 的 3s 预算<b>不覆盖</b> JWT 验签，RAG 的<b>覆盖</b>。
 * <p><b>Go 侧链序（真源，逐行核实 go-zero@v1.10.2）</b>：
 * <pre>
 *   rest/engine.go:101-113  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签在这里 append
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s) 在这之后 append
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go:38-43  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s)** → 黑名单 → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入预算**
 * </pre>
 *
 * <p>⚠️ <b>另一个前提不能漏</b>：Go 的 native {@code TimeoutHandler} 被
 * {@code observability.DisableNativeRestTimeout} 关掉了
 * （{@code service/user/admin/api/admincenter.go:34}）⇒ 3s <b>只</b>由 observability 提供
 * ⇒ 「验签在预算之外」才有意义（否则 native 那条 3s 还在，照样覆盖验签）。
 *
 * <p><b>Java 侧落点</b>：Servlet 的所有 {@code Filter} 都在 {@code DispatcherServlet} <b>外面</b>，
 * {@code HandlerInterceptor} 在<b>里面</b> ⇒ 要把验签移出 3s 预算，就必须把它搬进 Filter
 * ⇒ 见 {@link GoStyleJwtAuthFilter}。
 */
@Component
public class AdminJwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtSignatureVerifier.class);

    private final SecretKey key;

    public AdminJwtSignatureVerifier(AdminApiProperties properties) {
        // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
        //   Go 出处：service/user/common/jwt/jwt.go 的 `token.SignedString([]byte(secretKey))`
        //     —— Go 侧 golang-jwt 不校验 key 长度，15 字节 admin secret 能直接签。
        //   Java 表达差异：jjwt 0.12 强制 ≥32 字节（HS256 / RFC 7518），故构造器
        //     调 Keys.hmacShaKeyFor 时必须先补齐；本构造器与 JwtUtil.getToken 共用
        //     JwtUtil.padToHmacKeySize，强制签发与验签落在同一份 32 字节 key 上，
        //     否则 admin-api 在默认 15 字节密钥下 Bean 创建即抛 WeakKeyException。
        //   语义等价论证：padding 是 zero-fill，落到 HS256 上字节一致 → token 形状、
        //     claim、exp 全一致；Go ↔ RAG 仅在 key 长度这一非行为字段上收敛。
        this.key = Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(properties.getAdminAuth().getAccessSecret()));
    }

    /**
     * 验签 —— <b>不写响应</b>；失败时把异常装进 {@link AdminJwtVerificationResult} 交给调用方重抛。
     *
     * @param authHeader 请求的 {@code Authorization} 头（可为 null）
     * @param uri        请求路径（只用于日志）
     */
    public AdminJwtVerificationResult verify(String authHeader, String uri) {
        // ---------- 阶段 1a：Authorization 头缺失 ----------
        // Go 出处：rest/handler/authhandler.go:54-56 ParseToken 失败 ⇒ unauthorized(401)
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("protected admin path {} without Authorization header", uri);
            return AdminJwtVerificationResult.failed(new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()));
        }

        // Go 出处：golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12
        //   stripBearerPrefixFromTokenString：前缀【大小写不敏感】（strings.ToUpper 比较），
        //   且【不匹配时原样返回整串】而不是报错。
        // 与 user 侧共用 BearerTokenExtractor，避免两份实现各自演化出 DRIFT-010 那类缺陷。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("protected admin path {} with empty Bearer token", uri);
            return AdminJwtVerificationResult.failed(new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()));
        }

        long uid = -1L;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            Object claim = jws.getPayload().get("userId");
            if (claim instanceof Number) {
                uid = ((Number) claim).longValue();
            } else {
                // 🔴 admin 侧与 user 侧**刻意不同**：畸形 claim 在 admin 侧就是 **401**。
                //   （user 侧按 DRIFT-USERAPI-001 改成「不拒绝、交给 identity 层」；
                //     admin 侧保持既有语义 —— 本类**不**顺手统一两域。）
                log.warn("admin JWT claim userId missing or wrong type : {}",
                        claim == null ? "null" : claim.getClass());
                return AdminJwtVerificationResult.failed(new AuthenticationException(
                        ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                        ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()));
            }
        } catch (BusinessException e) {
            // 保持原样上抛（与修复前 catch (BusinessException e) { throw e; } 语义一致）
            return AdminJwtVerificationResult.failed(e);
        } catch (Throwable t) {
            // 签名错 / 过期 —— 与 Go 端 silent 行为一致（authhandler.go:56 → unauthorized → 401）
            log.warn("admin JWT verify failed : {}", t.getMessage());
            return AdminJwtVerificationResult.failed(new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()));
        }

        // 黑名单 key 用的 token —— Go 的 CheckBlacklistMiddleware 用的是【TrimPrefix 结果】，
        // 与验签用的 token 是**两套取法**（Go 自身如此，畸形 header 下会不同）。
        String trimmedToken = BearerTokenExtractor.trimBearerPrefix(authHeader);
        return AdminJwtVerificationResult.ok(uid, token, trimmedToken);
    }
}
