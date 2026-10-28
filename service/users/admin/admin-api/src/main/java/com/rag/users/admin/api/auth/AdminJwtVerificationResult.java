package com.rag.users.admin.api.auth;

/**
 * 一次 admin JWT 验签的结果 —— <b>数据载体</b>，不含行为。
 *
 * <h2>与 user-api 同名类型的差异（<b>刻意不同</b>）</h2>
 *
 * <p>user-api 的 {@code JwtVerificationResult} 带 {@code claimUsable} 标志位，
 * 因为 <b>user 侧畸形 claim 不拒绝</b>（Go 的 {@code rest.WithJwt} 只验签名 + exp ⇒
 * 畸形 claim 的后果发生在业务层 ⇒ HTTP 200 + 业务码 1006，见 DRIFT-USERAPI-001）。
 * <p>而 <b>admin 侧畸形 claim 是 401</b>（见 {@code AdminJwtAuthInterceptor} 的既有语义）
 * ⇒ ⇒ admin 的失败**只有一种形态**（要抛异常）⇒ ⇒ 不需要标志位：
 * {@code failure == null} ⇔ 验签通过且 claim 可用。
 *
 * @param uid            claim {@code userId} 的值（claim 不可用时为 {@code -1}，且此时 {@code failure != null}）
 * @param signatureToken <b>验签用</b>的 token（Go {@code stripBearerPrefixFromTokenString} 的产物）
 * @param trimmedToken   <b>黑名单 key 用</b>的 token（Go {@code TrimPrefix} 的产物）—— 两者<b>可以不同</b>（Go 自身如此）
 * @param failure        验签失败时的异常（{@code null} = 验签通过）
 */
public record AdminJwtVerificationResult(
        long uid,
        String signatureToken,
        String trimmedToken,
        RuntimeException failure
) {

    /** 验签通过。 */
    public static AdminJwtVerificationResult ok(long uid, String signatureToken, String trimmedToken) {
        return new AdminJwtVerificationResult(uid, signatureToken, trimmedToken, null);
    }

    /** 验签失败 —— {@code failure} 必须非 null（会被 interceptor 原样重抛）。 */
    public static AdminJwtVerificationResult failed(RuntimeException failure) {
        return new AdminJwtVerificationResult(-1L, "", "", failure);
    }

    /** 验签是否失败。 */
    public boolean failed() {
        return failure != null;
    }
}
