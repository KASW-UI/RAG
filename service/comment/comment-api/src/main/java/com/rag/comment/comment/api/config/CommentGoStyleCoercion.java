package com.rag.comment.comment.api.config;

import com.rag.users.common.http.GoStyleCoercion;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * comment-api 专属的 Jackson 3 强转规则 = {@link GoStyleCoercion} 共享规则 + 本域补强。
 *
 * <p><b>⚠️ 为什么必须叠加、而不是改 {@link GoStyleCoercion}</b>：
 * 共享规则里「<b>String → 整数字段保持接受</b>」的豁免是 <b>users 域按真源实测</b>
 * 得到的结论（{@code {"uid":"1"}} 在 Go 侧被接受，因为 users 的 uid 字段带 {@code ,string}）。
 * comment 域<b>没有任何 {@code ,string} 字段</b> ——
 * {@code service/comment/api/internal/types/types.go} 的 6 个请求 DTO 共 28 个字段，
 * tag 一律是 {@code json:"xxx"}，无一个带 {@code stringOption}。
 * 那条豁免对 comment 不成立，而 AGENTS.md §1 明确「Go 已冻结，行为漂移只可能从 RAG 一侧发生」，
 * 改共享规则会连带改坏 user-api / admin-api / message-api 的已验证行为。
 * 因此只在本模块叠加，{@code GoStyleCoercion} 一行不动。
 *
 * <h3>补强规则 1：JSON 字符串<b>不进</b>整数字段</h3>
 *
 * <p>Go 出处与推导：
 * <pre>
 *   body: {"root_id":"123"}     // root_id 是 int64 json:"root_id"，无 ,string
 *   ⇒ fieldOpts.FromString == false
 *      （core/mapping/utils.go:376 parseOption 只在 option == stringOption 时置 true）
 *   ⇒ 走 unmarshaler.go:643-662 processFieldPrimitive：
 *        typeKind  := baseType.Kind()                  // Int64
 *        valueKind := reflect.ValueOf(mapValue).Kind()  // String
 *        if typeKind != valueKind → newTypeMismatchError ⇒ 解析失败
 * </pre>
 * 解析失败 ⇒ handler 调 {@code httpx.ErrorCtx} ⇒ 本服务已注册 {@code SetErrorHandler}
 * ⇒ <b>HTTP 200 + {code:5001, msg:"系统内部错误"}</b>。
 *
 * <p><b>为什么 Jackson 默认会放行</b>：Jackson 2/3 默认允许标量强转，
 * {@code {"root_id":"123"}} 会安静地变成 123。这是「不读 Go 源码也会自然写成这样」的高危漂移。
 *
 * <h3>补强规则 2：JSON 字符串<b>不进</b>布尔字段</h3>
 * <p>同一条 {@code processFieldPrimitive} 路径。当前 comment 请求 DTO 无布尔字段，
 * 保留该规则是为了与 message-api 的等价规则保持一致、并防止后续新增 DTO 时静默放宽。
 *
 * <h3>为什么这两条不会误伤</h3>
 * <p>两条规则都作用于<b>声明类型</b>为 {@code Integer}/{@code Boolean} 的 Java 字段。
 * 本模块所有数字字段用包装类型 {@code Long}/{@code Integer}，其
 * {@code LogicalType} 是 {@code Integer} ⇒ 规则 1 正常生效；
 * 而 comment 域<b>没有</b> {@code ,string} 字段（那类字段在 Java 侧会声明成 {@code Object}，
 * 查不到 LogicalType，会被这两条规则跳过），所以不存在「规则误伤 ,string 字段」的可能。
 */
public final class CommentGoStyleCoercion {

    private CommentGoStyleCoercion() {
        // 工具类，禁止 new
    }

    /**
     * 把共享规则 + comment 域补强施加到给定的 Jackson 3 mapper builder 上。
     *
     * <p>调用点：{@link WebMvcConfig#goStyleCoercionCustomizer()}，
     * 经 {@code JsonMapperBuilderCustomizer} 挂到 Spring MVC 实际使用的 mapper 上。
     * （Jackson 3 的 coercion 只能在 builder 上配，{@code ObjectMapper} 上没有该 API；
     *  Boot 4 的 {@code Jackson2ObjectMapperBuilderCustomizer} 也已随自动配置模块化消失。）
     *
     * <p><b>只动 MVC 这一个 mapper</b>：gRPC 用 protobuf marshaller，不走 Jackson。
     */
    public static void applyTo(JsonMapper.Builder builder) {
        // 共享规则：float 不进整数字段 + number/bool 不进 String 字段
        GoStyleCoercion.applyTo(builder);

        // 补强 1：JSON 字符串不进整数字段（comment 域无 ,string 字段，Go 一律拒绝）
        builder.withCoercionConfig(LogicalType.Integer, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));

        // 补强 2：JSON 字符串不进布尔字段
        builder.withCoercionConfig(LogicalType.Boolean, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}