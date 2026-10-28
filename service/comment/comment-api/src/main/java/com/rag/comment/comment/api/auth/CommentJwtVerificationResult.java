package com.rag.comment.comment.api.auth;

import io.jsonwebtoken.Claims;

/**
 * 一次 comment JWT 验签的结果 —— <b>数据载体</b>，不含行为。
 *
 * <p><b>失败时携带的是「异常对象」而不是标志位</b> ⇒ interceptor 可以<b>原样重抛</b>
 * ⇒ {@code CommentGlobalExceptionHandler#handleAuthentication} 仍然管得到
 * ⇒ <b>401 + 空 body</b>的形状逐字不变（Go 侧 {@code authhandler.go:106-122} 的
 * {@code unauthorized()} 只 {@code WriteHeader(401)} <b>不写任何字节</b>）。
 *
 * @param claims   验签通过时的 claim 集合（失败时为 {@code null}）
 * @param failure  验签失败时的异常（{@code null} = 验签通过）
 */
public record CommentJwtVerificationResult(
        Claims claims,
        RuntimeException failure
) {

    /** 验签通过。 */
    public static CommentJwtVerificationResult ok(Claims claims) {
        return new CommentJwtVerificationResult(claims, null);
    }

    /** 验签失败 —— {@code failure} 必须非 null（会被 interceptor 原样重抛）。 */
    public static CommentJwtVerificationResult failed(RuntimeException failure) {
        return new CommentJwtVerificationResult(null, failure);
    }

    /** 验签是否失败。 */
    public boolean failed() {
        return failure != null;
    }
}
