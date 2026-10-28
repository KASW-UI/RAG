package com.rag.users.user.api.auth;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.BearerTokenExtractor;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.config.UserApiProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * JWT <b>验签</b> —— 只做「解析 + 验签名 + 取 claim」，<b>不写响应、不查 Redis</b>。
 *
 * <h2>为什么从 {@code JwtAuthInterceptor} 里搬出来</h2>
 *
 * <p>DRIFT-USERAPI-009②：Go 的 3s 预算<b>不覆盖</b> JWT 验签，RAG 的<b>覆盖</b>。
 * <p><b>Go 侧链序（真源，逐行核实 go-zero@v1.10.2）</b>：
 * <pre>
 *   rest/engine.go:101-113  bindRoute：
 *     chn = ng.chain
 *     if chn == nil { chn = ng.buildChainWithNativeMiddlewares(...) }   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)                    // ← JWT 验签在这里 append
 *     for _, mw := range ng.middlewares { chn = chn.Append(...) }      // ← observability(3s) 在这之后 append
 *     handle = chn.ThenFunc(route.Handler)
 *
 *   rest/chain/chain.go:38-43  Append 是「加在请求流的最后」⇒ 越 Append 越靠内
 *   rest/chain/chain.go:78-84  Then = m0(m1(...mN(h))) ⇒ 索引 0 最外层、先执行
 *
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s)** → 黑名单 → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入预算**
 * </pre>
 *
 * <p>⚠️ <b>另一个前提不能漏</b>：Go 的 native {@code TimeoutHandler} 被
 * {@code observability.DisableNativeRestTimeout} 关掉了
 * （{@code service/user/user/api/usercenter.go:35}）⇒ 3s <b>只</b>由 observability 提供
 * ⇒ 「验签在预算之外」才有意义（否则 native 那条 3s 还在，照样覆盖验签）。
 *
 * <p><b>Java 侧落点</b>：Servlet 的所有 {@code Filter} 都在 {@code DispatcherServlet} <b>外面</b>，
 * {@code HandlerInterceptor} 在<b>里面</b> ⇒ 要把验签移出 3s 预算，就必须把它搬进 Filter
 * ⇒ 见 {@link GoStyleJwtAuthFilter}。
 */
@Component
public class JwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtSignatureVerifier.class);

    private final SecretKey key;

    public JwtSignatureVerifier(UserApiProperties properties) {
        // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
        //   Go 出处：service/user/common/jwt/jwt.go `token.SignedString([]byte(secretKey))`
        //     —— Go 侧 golang-jwt 不校验 key 长度，user secret 是 36 字节 UUID 已 ≥ 32 字节，
        //     走"返回 bytes 原长"分支，padding 是 no-op。
        //   Java 表达差异：jjwt 0.12 强制 ≥32 字节，所以即便 user secret 已 ≥32 仍统一调
        //     padToHmacKeySize —— 这样 admin 与 user 的 padding 逻辑收口在 JwtUtil 一份，
        //     将来若 user secret 也换成短密钥无需再改验签处。
        //   语义等价论证：bytes.length ≥ 32 时返回原 bytes，行为与直接 getBytes 一致；
        //     token 形状、claim、exp 全一致。Go ↔ RAG 无行为差异。
        this.key = Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
    }

    /**
     * 验签 —— <b>不写响应</b>；失败时把异常装进 {@link JwtVerificationResult} 交给调用方重抛。
     *
     * @param authHeader 请求的 {@code Authorization} 头（可为 null）
     * @param uri        请求路径（只用于日志）
     */
    public JwtVerificationResult verify(String authHeader, String uri) {
        // ---------- 阶段 1a：Authorization 头缺失 ----------
        // Go 出处：rest/handler/authhandler.go:54-56 ParseToken 失败 ⇒ unauthorized(401)
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("protected path {} without Authorization header", uri);
            return JwtVerificationResult.failed(new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            ));
        }

        // Go 出处：golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12
        //   stripBearerPrefixFromTokenString：前缀【大小写不敏感】（strings.ToUpper 比较），
        //   且【不匹配时原样返回整串】而不是报错。
        // 与 admin 侧共用 BearerTokenExtractor，避免两份实现各自演化出 DRIFT-010 那类缺陷。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("protected path {} with empty Bearer token", uri);
            return JwtVerificationResult.failed(new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            ));
        }

        long uid = -1L;
        boolean claimUsable = false;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            Object claim = jws.getPayload().get("userId");
            if (claim instanceof Number) {
                uid = ((Number) claim).longValue();
                claimUsable = true;
                // ⚠️ 这里<b>不</b>做 uid <= 0 的拒绝判断。
                // Go 出处：go-zero 的 rest.WithJwt 只验签名与 exp，不校验 claim 取值；
                // `uid > 0` 是业务规则，落在 identity 层
                // （identity/resolve.go:75-77 `if err != nil || uid <= 0 { return 0, ErrInvalidClaim }`）。
            } else {
                // 🔴 2026-10-23（DRIFT-USERAPI-001 已修）：**不在鉴权层拒绝畸形 claim**。
                //
                // Go 出处（消费点，不是声明点）：
                //   go-zero@v1.10.2 rest/handler/authhandler.go:54-81
                //       tok, err := parser.ParseToken(r, secret, …)   // 只管签名 + exp
                //       if err != nil { unauthorized(…); return }      // ← 只有这里 401
                //       if !tok.Valid { unauthorized(…); return }      // ← 只有这里 401
                //       claims, ok := tok.Claims.(jwt.MapClaims)
                //       for k, v := range claims {                    // ← **逐个 claim 塞进 ctx**
                //         switch k { case aud,exp,jti,iat,iss,nbf,sub: /* ignore */ default: ctx = WithValue(ctx,k,v) }
                //       }
                //       next.ServeHTTP(w, r.WithContext(ctx))          // ← 请求**到达 handler**
                //   ⇒ Go 对 claim 的「存在性」与「取值」**都不校验**，
                //     畸形 claim 的后果发生在业务层：
                //       identity/resolve.go:70-73  ClaimedUID: ctx.Value("userId").(json.Number) 失败 ⇒ ErrInvalidClaim
                //       api 层 handler → httpx.OkJson ⇒ **HTTP 200** + {"code":1006,…}
                //
                // ⚠️ 修复前本类在这里抛 AuthenticationException ⇒ HTTP 401。
                //   实测（双侧活体对拍，2026-10-23 21:12，真进程 + 真 PG + 真 Redis）：
                //       Go  : 200 {"code":1006,"msg":"TOKEN已过期","data":null}
                //       RAG : 401 {"code":1006,"msg":"TOKEN已过期","data":null}   ← 修复前
                //   ⇒ 业务码两侧相同（1006），**HTTP 状态码不同** ⇒ 语义漂移。
                //
                // ⚠️ **注意别与 N-1 混淆**：N-1 签字的是「401 的 body 形状统一成完整 envelope」，
                //   覆盖的是**真正的鉴权失败**（缺 Bearer / 签名错 / 过期 / 黑名单命中）。
                //   本条说的是「签名合法但 claim 畸形」——Go 侧**根本不 401**，所以不涉及 N-1。
                //
                // ⇒ 现在的行为：**不写 userId attribute、不抛异常**，继续走黑名单检查，
                //   让业务层（IdentityResolver.claimedUID）拒掉它 ⇒ 200 + 1006。
                //   黑名单检查仍会执行 —— Go 的链序是 JWT → 黑名单 → handler（authhandler.go:81 之后才到 handler），
                //   所以「畸形 claim + 已拉黑」在 Go 侧也是 401，本类同样先查黑名单 ⇒ 一致。
                log.warn("JWT claim userId missing or wrong type : {}",
                        claim == null ? "null" : claim.getClass());
            }
        } catch (BusinessException e) {
            // 保持原样上抛（与修复前 catch (BusinessException e) { throw e; } 语义一致）
            return JwtVerificationResult.failed(e);
        } catch (Throwable t) {
            // 签名错 / 过期 —— 与 Go 端 silent 行为一致（authhandler.go:56 → unauthorized → 401）
            log.warn("JWT verify failed : {}", t.getMessage());
            return JwtVerificationResult.failed(new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            ));
        }

        // 黑名单 key 用的 token —— Go 的 CheckBlacklistMiddleware 用的是【TrimPrefix 结果】，
        // 与验签用的 token 是**两套取法**（Go 自身如此，畸形 header 下会不同）。
        String trimmedToken = BearerTokenExtractor.trimBearerPrefix(authHeader);
        return JwtVerificationResult.ok(uid, claimUsable, token, trimmedToken);
    }
}
