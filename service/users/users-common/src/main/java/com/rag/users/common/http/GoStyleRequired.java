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
}