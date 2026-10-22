package com.rag.message.message.api.config;

import com.rag.users.common.http.GoStyleCoercion;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * message-api 专属的 Jackson 3 强转规则 = {@link GoStyleCoercion} 共享规则 + <b>两条 message 域补强</b>。
 *
 * <p><b>⚠️ 为什么必须在本模块叠加、而不是改 {@link GoStyleCoercion}</b>：
 * {@code GoStyleCoercion} 是 users-common 的共享规则，它的第 3 条豁免
 * 「<b>String → 整数字段保持接受</b>」是 <b>users 域按真源实测得出的结论</b>
 * （{@code {"uid":"1"}} 在 Go 侧接受，因为 users 的 uid 字段带 {@code ,string}）。
 * 那条豁免对 users 成立，对 message <b>不成立</b> —— 而 AGENTS.md §1 明确
 * 「Go 已冻结，行为漂移只可能从 RAG 一侧发生」，改共享规则会连带把
 * user-api / admin-api 的已验证行为改坏。因此本类只叠加 message 自己的规则，
 * {@code GoStyleCoercion} 一行不动。
 *
 * <h3>补强规则 1：无 {@code ,string} 的整数字段<b>拒绝</b> JSON 字符串（DRIFT-MSGAPI-002）</h3>
 *
 * <p>Go 出处与推导：
 * <pre>
 *   body: {"offset":"10"}     // offset 是 int32 json:"offset,optional"，无 ,string
 *   ⇒ opts.FromString == false（core/mapping/utils.go:376 只在出现 stringOption 时置 true）
 *   ⇒ 走 processFieldNotFromString → processFieldPrimitive
 *      （core/mapping/unmarshaler.go:643-662）：
 *        typeKind := baseType.Kind()            // Int32
 *        valueKind := reflect.ValueOf(mapValue).Kind()  // String（JSON 字符串）
 *        if typeKind != valueKind → newTypeMismatchError ⇒ 解析失败
 * </pre>
 * 实测（v1.10.2 真源探针）：{@code {"offset":"10"} → "type mismatch for field \"offset\""}，
 * 且解析失败后 {@code offset} 仍为 0（未被写入）。
 *
 * <p><b>为什么 Jackson 默认会放行</b>：Jackson 2/3 默认允许标量强转，
 * {@code {"offset":"10"}} 会安静地变成 10。这是 §26 反事实检验里的典型
 * 「不看 Go 源码也会自然写成这样」的高危漂移。
 *
 * <h3>补强规则 2：无 {@code ,string} 的布尔字段<b>拒绝</b> JSON 字符串</h3>
 * <p>同一条 {@code processFieldPrimitive} 路径：{@code {"unread_only":"true"}} →
 * {@code "type mismatch for field \"unread_only\""}（实测）。
 *
 * <h3>为什么这两条对 {@code ,string} 字段<b>不适用</b>、也<b>不会</b>误伤它们</h3>
 * <p>{@code ,string} 字段的 setter 入参已改成 {@code Object}
 * （见 {@code GoStyleStringInt64}）—— 声明类型是 {@code Object} 就<b>没有</b>
 * 目标 {@code LogicalType}，这两条 {@code LogicalType.Integer/Boolean} 规则查不到它，
 * 数字与字符串都会原样递出，由 {@code GoStyleStringInt64} 逐条判定。
 * 两侧因此各走各的判据，互不干扰。
 */
public final class MessageGoStyleCoercion {

    private MessageGoStyleCoercion() {
        // 工具类，禁止 new
    }

    /**
     * 把共享规则 + message 域两条补强施加到给定的 Jackson 3 mapper builder 上。
     *
     * <p>调用点：{@link WebMvcConfig#goStyleCoercionCustomizer()}，
     * 经 {@code JsonMapperBuilderCustomizer} 挂到 Spring MVC 实际使用的 mapper 上。
     * （Jackson 3 的 coercion 只能在 builder 上配，{@code ObjectMapper} 上没有该 API。）
     */
    public static void applyTo(JsonMapper.Builder builder) {
        // 共享的两条：float 不进整数字段（ACCEPT_FLOAT_AS_INT）、number/bool 不进 String 字段
        GoStyleCoercion.applyTo(builder);

        // 补强 1：JSON 字符串不进整数字段（仅影响无 ,string 的字段）
        builder.withCoercionConfig(LogicalType.Integer, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));

        // 补强 2：JSON 字符串不进布尔字段
        builder.withCoercionConfig(LogicalType.Boolean, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}