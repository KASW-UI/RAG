package com.rag.common.protection;

/**
 * 路由模板解析的<b>三态结果</b>。
 *
 * <p>⚠️ <b>刻意不引用任何 Spring MVC 类型</b>：本类型会被
 * {@link GoStyleBreakerFilter}（Filter，在所有 servlet 进程里都有）引用
 * ⇒ 若它间接牵出 {@code org.springframework.web.servlet.*}，
 * 那么<b>没有 spring-webmvc 的进程</b>（10 个 rpc 组件）在加载 Filter 时就会
 * {@code NoClassDefFoundError} ⇒ <b>硬失败</b>。
 *
 * @param kind    三态之一
 * @param template 注册期路由模板；仅 {@link Kind#TEMPLATE} 时非空
 */
public record GoStyleRouteResolution(Kind kind, String template) {

    /** 三态。 */
    public enum Kind {
        /** 确定匹配到路由，模板可用。 */
        TEMPLATE,
        /** 确定<b>没有</b>任何路由匹配（Go 侧的 404 / 405 ⇒ 不进 breaker）。 */
        NOT_MATCHED,
        /** 判不出来（调用方必须<b>回落旧行为</b>，绝不静默关掉熔断保护）。 */
        UNKNOWN
    }

    public static GoStyleRouteResolution template(String template) {
        return new GoStyleRouteResolution(Kind.TEMPLATE, template);
    }

    /** 确定未命中。 */
    public static final GoStyleRouteResolution NOT_MATCHED =
            new GoStyleRouteResolution(Kind.NOT_MATCHED, null);

    /** 判不出来。 */
    public static final GoStyleRouteResolution UNKNOWN =
            new GoStyleRouteResolution(Kind.UNKNOWN, null);
}
