package com.rag.users.common.http;

/**
 * go-zero 必填字段缺失 —— 由 {@link GoStyleRequiredFields#assertGoRequiredFields()} 抛出。
 *
 * <p><b>刻意不继承 {@code BusinessException}</b>：{@code BusinessException} 会被
 * {@code GlobalExceptionHandler#handleBusiness} 吃掉并渲染成
 * {@code HTTP 200 + {code,msg,data}} 的业务 envelope，而 Go 的必填校验走的是
 * {@code httpx.ErrorCtx} → {@code http.Error(w, err.Error(), 400)}，
 * 即 {@code HTTP 400 + text/plain}。两者形状完全不同，用继承会把 400 降级成 200。
 *
 * <p>AGENTS.md §3.4.2 N-1 已记录过这个教训：<b>层次由类型承载，不靠业务码推断</b>。
 *
 * @see GoStyleRequiredFields
 */
public class GoRequiredFieldException extends RuntimeException {

    private final String field;

    public GoRequiredFieldException(String field) {
        super("field \"" + field + "\" is not set");
        this.field = field;
    }

    /** 缺失字段的 JSON tag 名（不是 Java 字段名）。 */
    public String getField() {
        return field;
    }
}