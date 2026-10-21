package com.rag.users.common.http;

/**
 * go-zero 风格的「必填字段」校验契约（DRIFT-016）。
 *
 * <p><b>规则是什么</b>：go-zero 的 {@code core/mapping} 只认 tag 里的 {@code ,optional}，
 * <b>{@code ,omitempty} 不是 {@code ,optional}</b>；而带 {@code ,default=} 的字段在缺席时
 * 会被填默认值、同样不报必填错误。所以判据是：
 *
 * <pre>
 *   字段必填  ⟺  tag 不含 {@code ,optional}
 *                 AND tag 不含 {@code ,default=}
 *                 AND 字段类型是<b>标量</b>（Map / Slice / Array 永不计必填）
 * </pre>
 *
 * <p><b>Go 出处（三段，逐行核对过，非推断）</b>：
 * <ol>
 *   <li>{@code core/mapping/utils.go} 的 {@code parseOption} 里<b>只有</b>
 *       {@code optionalOption = "optional"} 与 {@code optionalOptionWithType}
 *       会置 {@code Optional = true}；{@code omitempty} 不在选项表里，被静默忽略。</li>
 *   <li>{@code core/mapping/unmarshaler.go:918-969} 的 {@code processNamedFieldWithoutValue}：
 *       先判 {@code opts.getDefault()}（有 default ⇒ 填默认值返回，不报错）；
 *       {@code Array/Map/Slice} 走 {@code processFieldNotFromString}（<b>不报错</b>）；
 *       只有 {@code default:} 分支（String / Int / Bool 等标量）在 {@code !opts.optional()}
 *       时走 {@code newInitError(fullName)} ⇒ {@code field %q is not set}。</li>
 *   <li>{@code core/mapping/unmarshaler.go:1199} 的 {@code newInitError} 产出
 *       {@code fmt.Errorf("field %q is not set", name)}。</li>
 * </ol>
 *
 * <p><b>实测证据</b>（{@code tools/difftest/probe-go-required-fields.py}，2026-10-21，真进程连 18888/18889）：
 * <pre>
 *   user.register  drop email  → 400  field "email" is not set      ← email tag 是 ,omitempty，仍必填
 *   user.register  drop extra_info → 200                              ← map 字段，缺席不报错
 *   user.register  真空 body   → 400  field "username" is not set
 *   user.register  {}          → 400  field "username" is not set
 *   user.register  email=""    → 200                                  ← json 通道空串是合法值
 *   user.register  email=null  → 400  field "email" mustn't be nil
 *   admin.getuserlist 缺 page / page_size → 200                       ← 有 default=，填默认值
 *   admin.getuserlist keyword=""     → 400  field "keyword" is not set ← form 通道空串被丢弃
 * </pre>
 *
 * <p><b>为什么抽成接口而不是用反射</b>：反射读字段名是<b>隐式</b>约定 ——
 * 字段一改名，编译器不会报错，校验会静默失效（AGENTS.md §3.3「框架机制必须验证实际生效」
 * 的同一类病灶）。每个 DTO 显式写出自己的必填字段，改名即编译失败。
 */
public interface GoStyleRequiredFields {

    /**
     * 断言本 DTO 的 go-zero 必填字段全部已设置。
     *
     * @throws GoRequiredFieldException 任一必填字段缺失
     */
    void assertGoRequiredFields();
}