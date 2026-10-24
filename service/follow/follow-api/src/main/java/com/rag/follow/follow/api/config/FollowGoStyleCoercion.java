package com.rag.follow.follow.api.config;

import com.rag.users.common.http.GoStyleCoercion;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * follow-api 专属的 Jackson 3 强转规则 = {@link GoStyleCoercion} 共享规则 + <b>一条 follow 域补强</b>。
 *
 * <p><b>⚠️ 为什么必须在本模块叠加、而不是改 {@link GoStyleCoercion}</b>：
 * {@code GoStyleCoercion} 是 users-common 的共享规则，它的第 3 条豁免
 * 「<b>String → 整数字段保持接受</b>」是 <b>users 域按真源实测得出的结论</b>
 * （{@code {"uid":"1"}} 在 Go 侧接受，因为 users 的 uid 字段带 {@code ,string}）。
 * 那条豁免对 users 成立，对 follow <b>不成立</b> —— 而 AGENTS.md §1 明确
 * 「Go 已冻结，行为漂移只只能从 RAG 一侧发生」。改共享规则会连带把
 * user-api / admin-api 的已验证行为改坏。因此本类只叠加 follow 自己的规则，
 * {@code GoStyleCoercion} 一行不动。
 *
 * <h3>补强规则：无 {@code ,string} 的整数字段<b>拒绝</b> JSON 字符串</h3>
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
 * 实测（v1.10.2 真源探针，见 message 域同款结论 DRIFT-MSGAPI-002）：
 * {@code {"offset":"10"} → "type mismatch for field \"offset\""}，
 * 且解析失败后 {@code offset} 仍为 0（未被写入）。
 *
 * <p><b>为什么 Jackson 默认会放行</b>：Jackson 3 默认允许标量强转，
 * {@code {"offset":"10"}} 会安静地变成 10。这是「不看 Go 源码也会自然写成这样」
 * 的高危漂移。
 *
 * <p><b>本域受这条规则影响的字段恰好是两个</b>：
 * {@code FollowListReq.offset} 与 {@code FollowListReq.limit}
 * （两个 list 端点共用该 DTO；四个 action 端点的 {@code target_id} 带 {@code ,string} 不受影响）。
 *
 * <h3>为什么这条对 {@code ,string} 字段<b>不适用</b>、也<b>不会</b>误伤它们</h3>
 * <p>{@code ,string} 字段（{@code user_id} / {@code target_id}）的 setter 入参已改成
 * {@code Object}（见 {@code GoStyleStringInt64}）—— 声明类型是 {@code Object} 就<b>没有</b>
 * 目标 {@code LogicalType}，这条规则查不到它，数字与字符串都会原样递出，
 * 由 {@code GoStyleStringInt64} 逐条判定。两侧因此各走各的判据，互不干扰。
 *
 * <h3>为什么<b>不</b>补 message 域的「字符串/数字 → 布尔」两条</h3>
 * <p>{@code MessageGoStyleCoercion} 另加了 String→Boolean 与 Integer→Boolean 两条 Fail。
 * follow 域的<b>请求</b> DTO 里<b>没有任何布尔字段</b>：
 * {@code FollowListReq}（user_id/offset/limit）与
 * {@code RelationActionReq}（target_id）全是整数字段；
 * 唯一的布尔是<b>响应</b>侧的 {@code RelationActionResp.success}，
 * 而它永远由服务端写死 {@code true}，不存在「客户端送进来」的路径。
 * ⇒ 那两条规则在本模块<b>永不触发</b>，加进来属于「配置了但没消费者」的死配置
 * （AGENTS.md §3.3 同一类病灶的温和版），故不加。
 */
public final class FollowGoStyleCoercion {

    private FollowGoStyleCoercion() {
        // 工具类，禁止 new
    }

    /**
     * 把共享规则 + follow 域那条补强施加到给定的 Jackson 3 mapper builder 上。
     *
     * <p>调用点：{@link WebMvcConfig#goStyleCoercionCustomizer()}，
     * 经 {@code JsonMapperBuilderCustomizer} 挂到 Spring MVC 实际使用的 mapper 上。
     * （Jackson 3 的 coercion 只能在 builder 上配，{@code ObjectMapper} 上没有该 API。）
     */
    public static void applyTo(JsonMapper.Builder builder) {
        // 共享的两条：float 不进整数字段（ACCEPT_FLOAT_AS_INT）、number/bool 不进 String 字段
        GoStyleCoercion.applyTo(builder);

        // follow 域补强：JSON 字符串不进整数字段（仅影响无 ,string 的 offset / limit）
        builder.withCoercionConfig(LogicalType.Integer, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}