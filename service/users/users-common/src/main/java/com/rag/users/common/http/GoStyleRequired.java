package com.rag.users.common.http;

/**
 * 单个字段的必填判定 —— DTO 在 {@link GoStyleRequiredFields#assertGoRequiredFields()} 里逐字段调用。
 *
 * <p><b>只判 {@code null}，不判空串</b>：这是逐字对齐 Go 的 json 通道行为。
 * 实测（probe-go-required-fields.py，2026-10-21）：
 * <pre>
 *   user.register  email=""  → 200（注册成功）
 *   user.register  email=null → 400
 * </pre>
 * 空串在 Go 的 json 通道里是<b>合法值</b>（{@code unmarshaler.go} 的
 * {@code processFieldPrimitive} → {@code fillWithSameType} 直接写入 ""，不做必填检查）。
 *
 * <p><b>form 通道的空串会被拒</b>，那是另一条路径：
 * {@code rest/httpx/util.go:37-43} 的 {@code GetFormValues} 在构造 params 时
 * {@code if len(v) == 0 { continue }} 把空值<b>整个丢掉</b>，于是 {@code keyword=} 退化成
 * 「字段缺席」⇒ 同样是 400 {@code field "keyword" is not set}。
 * RAG 全部走 json body，因此这条 form 通道语义不适用（见 AGENTS.md §6.2.5 DRIFT-016
 * 留档的 OBSERVABLE DIFFERENCE 一行）。
 */
public final class GoStyleRequired {

    private GoStyleRequired() {
    }

    /**
     * @param name  JSON tag 名（用于错误文本，与 Go 的 fullName 同名）
     * @param value 字段当前值
     * @throws GoRequiredFieldException {@code value == null}
     */
    public static void field(String name, Object value) {
        if (value == null) {
            throw new GoRequiredFieldException(name);
        }
    }

    /**
     * <b>「显式 null」判定（E-3 根因 R2，2026-10-22）</b>：只在该字段<b>出现在 body 里
     * 且值为 null</b> 时报错；<b>缺席不算</b>。
     *
     * <p><b>为什么 map 字段需要它、标量字段不需要</b>：
     * {@link #field} 只看「值是不是 null」，对标量够用 —— 缺席时 Jackson 根本不调 setter，
     * 值留在 null，于是「缺席」和「显式 null」都落到同一个分支上，
     * 而 Go 对这两种情况<b>本来就都报错</b>，只是文案不同。
     * 但对 map 字段两者<b>结局相反</b>：
     * <pre>
     *   Go 缺席 extra_info   → processNamedFieldWithoutValue → Map 分支 → 200
     *   Go extra_info: null  → processNamedFieldWithValue    → mustn't be nil → 400
     * </pre>
     * 只看值的话，「缺席」会被误判成 null 而错杀。所以 DTO 必须额外告诉本方法
     * 「setter 有没有被调过」—— Jackson <b>只在字段真的出现在 JSON 里时才调 setter</b>，
     * 这就是 presence 标志的来源。
     *
     * <p><b>实测（Go user.register，真进程 18888）</b>：
     * <pre>
     *   {...,"extra_info":null} → 400  field "extra_info" mustn't be nil
     *   {...}  缺 extra_info     → 200（注册成功）
     * </pre>
     *
     * @param name   JSON tag 名
     * @param present setter 是否被调用过（= 该 key 是否出现在 JSON 里）
     * @param value  字段当前值
     * @throws GoRequiredFieldException {@code present && value == null}
     * @see GoRequiredFieldException#mustNotBeNil
     */
    public static void explicitNull(String name, boolean present, Object value) {
        if (present && value == null) {
            throw GoRequiredFieldException.mustNotBeNil(name);
        }
    }
}