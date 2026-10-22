package com.rag.like.like.api.config;

import com.rag.users.common.http.GoStyleCoercion;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * like-api 专属的 Jackson 3 强转规则 = {@link GoStyleCoercion} 共享规则 + <b>两条 like 域补强</b>。
 *
 * <p><b>⚠️ 为什么必须在本模块叠加、而不是改 {@link GoStyleCoercion}</b>：
 * {@code GoStyleCoercion} 是 users-common 的共享规则，它<b>刻意不配置</b>
 * {@code LogicalType.Integer} 的 String 入参 —— 那条「String → 整数字段保持接受」
 * 是 <b>users 域按真源实测</b>得出的结论（users 的 uid 字段带 {@code ,string}）。
 * 改共享规则会连带把 user-api / admin-api 的已验证行为改坏
 * （AGENTS.md §1：Go 已冻结，行为漂移只可能从 RAG 一侧发生）。
 * 本类只叠加 like 域自己的规则，{@code GoStyleCoercion} 一行不动。
 *
 * <h3>补强 1：<b>无</b> {@code ,string} 的整数字段拒绝 JSON 字符串</h3>
 *
 * <p>Go 出处与推导（{@code core/mapping/unmarshaler.go:643-662} 的 {@code processFieldPrimitive}）：
 * <pre>
 *   body: {"action_type":"1"}    // action_type 是 int64，无 ,string
 *   ⇒ opts.FromString == false（core/mapping/utils.go:372-385 的 parseOption
 *      只在出现 stringOption 时才置 true）
 *   ⇒ 走 processFieldNotFromString → processFieldPrimitive：
 *        typeKind  := Deref(fieldType).Kind()          // Int64
 *        valueKind := reflect.TypeOf(mapValue).Kind()   // String（JSON 字符串）
 *        if typeKind != valueKind → newTypeMismatchError ⇒ 解析失败
 * </pre>
 *
 * <p><b>为什么 Jackson 默认会放行</b>：Jackson 2/3 默认允许标量强转，
 * {@code {"action_type":"1"}} 会安静地变成 1，解析<b>成功</b>。
 * 这是「不看 Go 源码也会自然写成这样」的高危漂移 —— 且方向是
 * <b>Go 拒绝、RAG 接受</b>，比反过来更难发现（多放行不会报错）。
 *
 * <p>⚠️ <b>这条规则不能误伤 {@code ,string} 字段</b>：{@code LikeActionReq.authorId}
 * 的 setter 入参声明为 {@code Object}（见 {@code GoStyleStringInt64} 的说明），
 * 没有目标 {@code LogicalType}，这条规则查不到它，数字与字符串都会原样递出，
 * 由 {@code GoStyleStringInt64} 逐条判定。两侧各走各的判据，互不干扰。
 *
 * <h3>补强 2：浮点字段拒绝 JSON 字符串</h3>
 * <p>同一条 {@code processFieldPrimitive} 路径：{@code {"weight":"1.5"}} 的
 * {@code typeKind}(Float64) ≠ {@code valueKind}(String) ⇒ Go 报 type mismatch。
 * Jackson 默认对 {@code String → Double/Float} 是 {@code TryConvert}，会把
 * {@code "1.5"} 安静转成 1.5。
 *
 * <p><b>注意这条与共享规则「ACCEPT_FLOAT_AS_INT 关闭」是两件事</b>：
 * 那条管「浮点 JSON 数字 → 整数字段」，本条管「JSON 字符串 → 浮点字段」，
 * 方向相反、字段类型也不同，不能互相替代。
 *
 * <p><b>注册点必须是 builder</b>：Spring Boot 4（Spring Framework 7）用的是 Jackson 3
 * （{@code tools.jackson.*}），coercion 只能在 {@code JsonMapper.Builder} 上配，
 * {@code ObjectMapper} 上没有该 API。扩展点是 {@link org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer}。
 *
 * <p><b>只动 MVC 这一个 mapper</b>：gRPC 用 protobuf marshaller，不走 Jackson。
 */
public final class LikeGoStyleCoercion {

    private LikeGoStyleCoercion() {
        // 工具类，禁止 new
    }

    /**
     * 把共享规则 + like 域两条补强施加到给定的 Jackson 3 mapper builder 上。
     *
     * <p>调用点：{@link WebMvcConfig#goStyleCoercionCustomizer()}。
     */
    public static void applyTo(JsonMapper.Builder builder) {
        // 共享的两条：float 不进整数字段（ACCEPT_FLOAT_AS_INT）、number/bool 不进 String 字段
        GoStyleCoercion.applyTo(builder);

        // 补强 1：JSON 字符串不进整数字段（只影响无 ,string 的字段）
        builder.withCoercionConfig(LogicalType.Integer, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));

        // 补强 2：JSON 字符串不进浮点字段
        builder.withCoercionConfig(LogicalType.Float, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}