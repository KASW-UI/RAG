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
    private final boolean explicitNull;

    public GoRequiredFieldException(String field) {
        super("field \"" + field + "\" is not set");
        this.field = field;
        this.explicitNull = false;
    }

    private GoRequiredFieldException(String field, boolean explicitNull) {
        super("field \"" + field + "\" mustn't be nil");
        this.field = field;
        this.explicitNull = explicitNull;
    }

    /**
     * 「字段在 body 里显式出现、但值是 {@code null}」这一形态。
     *
     * <p><b>为什么必须是另一种文案</b>：Go 把「缺席」与「显式 null」当成两件事，
     * 错误文本也不同 ——
     * <pre>
     *   缺席        → unmarshaler.go:1199 newInitError        ⇒ field "x" is not set
     *   显式 null   → unmarshaler.go:856-862 processNamedFieldWithValue
     *                 if mapValue == nil { if optional {return nil}
     *                                          return fmt.Errorf("field %q mustn't be nil", key) }
     * </pre>
     * 一律复用 {@code is not set} 会把两个不同原因压成同一个错误文本。
     *
     * <p>实测（Go admin.login，真进程）：
     * <pre>
     *   {"username":null} → 400  field "username" mustn't be nil
     *   {"password":"x"}  → 400  field "username" is not set
     * </pre>
     *
     * @see GoStyleRequired#explicitNull
     */
    public static GoRequiredFieldException mustNotBeNil(String field) {
        return new GoRequiredFieldException(field, true);
    }

    /** 缺失字段的 JSON tag 名（不是 Java 字段名）。 */
    public String getField() {
        return field;
    }

    /**
     * 本异常是「显式 null」形态（{@code mustn't be nil}）还是「缺席」形态
     * （{@code is not set}）。供回归测试断言文案用的是哪一条分支。
     */
    public boolean isExplicitNull() {
        return explicitNull;
    }
}