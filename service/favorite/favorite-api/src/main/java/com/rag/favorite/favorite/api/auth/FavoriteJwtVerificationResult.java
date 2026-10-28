package com.rag.favorite.favorite.api.auth;

import io.jsonwebtoken.Claims;

/**
 * 一次 favorite JWT 验签的结果 —— <b>数据载体</b>，不含行为。
 *
 * <p><b>失败时携带的是「异常对象」而不是标志位</b> ⇒ interceptor 可以<b>原样重抛</b>
 * ⇒ {@code GlobalExceptionHandler} 仍然管得到 ⇒ <b>401 + 三字段信封</b>的形状逐字不变
 * （本域的 401 信封是<b>已签字的表达层差异</b>：{@code DEV-FAV-API-001}，
 * Go 侧 {@code authhandler.go:106-122} 的 {@code unauthorized()} 只 {@code WriteHeader(401)}
 * <b>不写任何字节</b>；RAG 侧写 {@code {code:5004, msg, data}}）。
 *
 * @param claims          验签通过时的 claim 集合（失败时为 {@code null}）
 * @param signatureToken  <b>验签用</b>的 token（Go {@code stripBearerPrefixFromTokenString} 的产物）
 * @param trimmedToken    <b>黑名单 key 用</b>的 token（Go {@code TrimPrefix} + {@code TrimSpace} 的产物）
 *                        —— 两者<b>可以不同</b>（Go 自身如此，见下方说明）
 * @param failure         验签失败时的异常（{@code null} = 验签通过）
 */
public record FavoriteJwtVerificationResult(
        Claims claims,
        String signatureToken,
        String trimmedToken,
        RuntimeException failure
) {

    /** 验签通过。 */
    public static FavoriteJwtVerificationResult ok(Claims claims, String signatureToken, String trimmedToken) {
        return new FavoriteJwtVerificationResult(claims, signatureToken, trimmedToken, null);
    }

    /** 验签失败 —— {@code failure} 必须非 null（会被 interceptor 原样重抛）。 */
    public static FavoriteJwtVerificationResult failed(RuntimeException failure) {
        return new FavoriteJwtVerificationResult(null, "", "", failure);
    }

    /** 验签是否失败。 */
    public boolean failed() {
        return failure != null;
    }
}
