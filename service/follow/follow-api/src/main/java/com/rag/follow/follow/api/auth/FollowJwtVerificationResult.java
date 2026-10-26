package com.rag.follow.follow.api.auth;

/**
 * 一次 follow JWT 验签的结果 —— <b>数据载体</b>，不含行为。
 *
 * <h2>⚠️ 与 user-api / admin-api 同名类型的差异（<b>刻意不同</b>）</h2>
 *
 * <p>follow 侧存进 request attribute 的不是 {@code long}，而是 claim 的<b>原始对象</b>
 * （{@code claims.get("userId")}，可能是 {@code Integer} / {@code Long} / {@code String} / {@code null}）。
 * <p>理由（见 {@code FollowJwtAuthInterceptor} 的类注释）：
 * Go 的 {@code authhandler.go:71-79} 把 claim <b>原样</b>塞进 ctx，
 * <b>取值判定属于业务层</b>（{@code logic/follow/helper.go:13-33} 的 {@code extractUserID}，
 * 失败返回 {@code ErrorUnauthorized} 5004 ⇒ HTTP 200 + {@code {code:5004}}）。
 * <p>⇒ 若在鉴权层把它变成 401，Go 返回 200 + 5004 的场景在 RAG 上会变成 401 ⇒ <b>状态码就错了</b>。
 * ⇒ ⇒ 本类型携带 {@code Object claim}（<b>不转 long</b>）。
 *
 * @param claim   claim {@code userId} 的<b>原始对象</b>（可能为 {@code null}）
 * @param failure 验签失败时的异常（{@code null} = 验签通过）
 */
public record FollowJwtVerificationResult(
        Object claim,
        RuntimeException failure
) {

    /** 验签通过（claim 可能为 {@code null} —— Go 侧照样放行）。 */
    public static FollowJwtVerificationResult ok(Object claim) {
        return new FollowJwtVerificationResult(claim, null);
    }

    /** 验签失败 —— {@code failure} 必须非 null（会被 interceptor 原样重抛）。 */
    public static FollowJwtVerificationResult failed(RuntimeException failure) {
        return new FollowJwtVerificationResult(null, failure);
    }

    /** 验签是否失败。 */
    public boolean failed() {
        return failure != null;
    }
}
