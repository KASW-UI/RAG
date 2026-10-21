package com.rag.users.common.http;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import java.util.Set;

/**
 * E-3 根因 **R1** 的修复规则：关掉 Jackson 的标量强转与浮点截断，对齐 go-zero 的解析层。
 *
 * <p><b>2026-10-22</b>。此前 22 个 api 端点的请求 DTO 全部走 Jackson 默认的宽松解析，
 * 而 Go 侧是 go-zero 的 {@code core/mapping} —— 两者对「JSON 标量塞进非标量字段」的
 * 判定完全相反。
 *
 * <p><b>Go 的真实判定（逐条实测，真进程 18889，不是推断）</b>：
 * <pre>
 *   String 字段：{"username":1}    → 400  type mismatch for field "username", expect "string", actual "number"
 *                {"username":1.5}  → 400  type mismatch for field "username", expect "string", actual "number"
 *                {"username":true} → 400  type mismatch for field "username"
 *                {"username":null} → 400  field "username" mustn't be nil
 *                {"username":[]}   → 400  type mismatch for field "username"
 *
 *   int64 字段：{"uid":1}     → 200（接受！）
 *              {"uid":"1"}   → 200（接受！先字符串化再 strconv.ParseInt）
 *              {"uid":1.0}   → 400  strconv.ParseInt: parsing "1.0": invalid syntax
 *              {"uid":1.5}   → 400  strconv.ParseInt: parsing "1.5": invalid syntax
 *              {"uid":true}  → 400  the value in map is not string, but bool
 *              {"uid":null}  → 400  field "uid" mustn't be nil
 * </pre>
 *
 * <p><b>由此得出两条规则</b>：
 * <ol>
 *   <li><b>数字 / 布尔不能进 String 字段</b> ⇒ {@link CoercionInputShape#Integer} /
 *       {@link CoercionInputShape#Float} / {@link CoercionInputShape#Boolean} 对
 *       {@link LogicalType#Textual} 一律 {@link CoercionAction#Fail}。</li>
 *   <li><b>浮点不能进整数字段，哪怕整数部分完整</b> ⇒ 关掉
 *       {@link DeserializationFeature#ACCEPT_FLOAT_AS_INT}。
 *       注意 {@code 1.0} 也必须拒：Go 侧字符串化后是 {@code "1.0"}，
 *       {@code strconv.ParseInt("1.0")} 直接失败 —— 所以这条<b>不是</b>
 *       「小数部分非零才拒」。</li>
 * </ol>
 *
 * <p><b>刻意不动的两条</b>：
 * <ul>
 *   <li><b>String → 整数字段保持接受</b>（{@code {"uid":"1"}}）。Go 侧
 *       {@code processNamedFieldWithValueFromString} 是「先 {@code convertToString}
 *       再 {@code strconv.ParseInt}」，字符串反而是它的正规入口。
 *       关掉它会把「两边都接受」的输入改成「只有一边接受」，是把 R1 修成新漂移。</li>
 *   <li><b>Array / Object → 任何字段</b>：Jackson 默认已经 Fail，无需配置。</li>
 * </ul>
 *
 * <p><b>失败后的响应形状</b>：Jackson 抛 {@code MismatchedInputException} →
 * Spring 包成 {@code HttpMessageNotReadableException} → api 层的
 * {@code handleUnreadableBody} → <b>HTTP 400 + text/plain</b>，
 * 与 Go 的 {@code http.Error(..., 400)} 同形状。
 * 文案不同属 §3.4.4 DRIFT-006 / §3.4.3 N-6 已确立的「形状对齐、文本不对齐」边界。
 *
 * ══════════════════════════════════════════════════════════════════════
 * <p><b>⚠️ 这里用的是 Jackson 3（{@code tools.jackson.*}），不是 Jackson 2</b>
 * ══════════════════════════════════════════════════════════════════════
 * <p>本项目是 <b>Spring Boot 4.0.8</b>，{@code spring-boot-starter-jackson} 带进来的是
 * {@code tools.jackson.core:jackson-databind:3.x}；而 {@code com.fasterxml.jackson.core:
 * jackson-databind:2.x} 是从 {@code com.rag:common} 间接来的<b>另一份</b>，MVC 根本不用它。
 *
 * <p><b>第一版就踩了这个坑</b>：按 Jackson 2 的写法去改
 * {@code MappingJackson2HttpMessageConverter.getObjectMapper()} ——
 * 编译通过、单测「装配断言」也能自己造一个 mapper 验证，
 * 但端点行为一条没变：{@code {"uid":1.5}} 依然返回 200。
 * 只有走真实 MVC 管线的<b>行为</b>用例能抓住它（AGENTS.md §3.3）。
 *
 * <p><b>第三个坑：规则只能<b>全局</b>生效，无法按类豁免（2026-10-22 实测）</b>。
 * 曾经想用 {@code withCoercionConfig(Class, ...)} 把「Go 走 form/query 通道的 DTO」
 * 排除掉（{@code GetUserListReq} / {@code GetUserReq}），
 * 结果<b>类级配置根本不生效</b>：独立小程序（jackson-databind 3.1.5）实测
 * <pre>
 *   设了 LogicalType.Textual=Fail + Kw 类 TryConvert  → Plain FAIL、Kw <b>也</b> FAIL
 *   只设 Plain 类 Fail（不设 LogicalType）           → Plain <b>居然成功</b>
 * </pre>
 * 即 Jackson 3 的类级 coercion 配置在这条路径上是**完全无效**的，
 * 只有 LogicalType 级的配置会被真正查到。
 * <b>⇒ 后果</b>：规则是全局的，于是它误伤了 {@code GetUserListReq.keyword} ——
 * 该字段在 Go 侧来自 query string（永远是字符串，{@code keyword=99999} 合法），
 * 而 RAG 因 A-03 偏离走 JSON body，{@code {"keyword":99999}} 被规则 1 拒掉。
 * 唯一的处理点落在 DTO 层：{@code GetUserListReq.setKeyword(Object)} 自己把标量
 * 字符串化。<b>全项目只有这一个字段需要</b>（另两个 form 通道 DTO 字段
 * {@code GetUserListReq.page/page_size} 是 Long，规则 2 在 Go 侧同样拒，无豁免必要）。
 *
 * <p>另一个坑：<b>Jackson 3 的 coercion 配置只能在 builder 上做</b>，
 * {@code ObjectMapper} 上<b>没有</b> {@code coercionConfigFor} / {@code getCoercionConfig}
 * （Jackson 2 有）。所以扩展点是 {@code MapperBuilder.withCoercionConfig}，
 * 由 api 模块注册 {@code JsonMapperBuilderCustomizer} 来触发。
 *
 * <p><b>为什么这里是纯静态工具、不是 {@code @Configuration}</b>：
 * {@code users-common} 是 user-rpc / admin-rpc / user-api / admin-api <b>四者共用</b>的模块，
 * 而 {@code admin-rpc} 是纯 gRPC 进程、刻意不引 servlet 与 Spring Boot 自动配置
 * （见 {@code AdminRpcApplication} 关于 {@code scanBasePackages} 的注释）。
 * 把 {@code @Configuration} + {@code JsonMapperBuilderCustomizer} 放进本模块，
 * 等于给 admin-rpc 强加一条它不需要的依赖。
 * 改成「本类只出规则、两个 api 模块各自注册 customizer」，本模块只需要 Jackson databind。
 */
public final class GoStyleCoercion {

    private GoStyleCoercion() {
    }

    /**
     * 把两条对齐规则施加到给定的 <b>Jackson 3</b> mapper builder 上。
     *
     * <p>调用方约定：只在 <b>api 层</b>（user-api / admin-api）的 MVC mapper 上调用。
     * gRPC 用 protobuf marshaller、MyBatis 与 {@code JsonbTypeHandler} 各持自己的
     * Jackson 2 mapper 实例，都与 go-zero 的 http 解析层无关，不该被这两条规则波及。
     *
     * @param builder Spring MVC 使用的 {@link JsonMapper.Builder}
     */
    public static void applyTo(JsonMapper.Builder builder) {
        // 规则 2：1.0 / 1.5 进 Long / int 一律拒（Go：strconv.ParseInt("1.0") 失败）
        builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // 规则 1：number / boolean 进 String 一律拒（Go：type mismatch）
        builder.withCoercionConfig(LogicalType.Textual, cfg -> cfg
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}