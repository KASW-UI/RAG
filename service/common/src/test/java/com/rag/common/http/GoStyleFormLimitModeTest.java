package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>模式语义的单测</b>：{@link GoStyleFormLimitMode} 的取值分派 + 三种模式下拦截器的行为。
 *
 * <h3>⚠️ 为什么这些是「单测」而不算 §3.3 的守门</h3>
 * <p>它们守的是<b>纯逻辑</b>（属性值 → 分派），不涉及框架机制 ⇒ Mockito/Mock 对象就够。
 * <br>而<b>框架机制</b>（闸门真的进了拦截器链、异常真的落到 advice、容器上限真的抬到 10001）
 * 由两条<b>真容器</b>守门负责：
 * <ul>
 *   <li>{@code GoStyleParamLimitGateTest}（raw 模式，真 Tomcat + 真 Spring MVC）；</li>
 *   <li>{@code GoStyleFormLimitEnvelopeGateTest}（envelope 模式，真 Tomcat + 真 Spring MVC + 真 advice）。</li>
 * </ul>
 *
 * <h3>🔴 为什么「off」必须真的<b>不装闸门</b>（但<b>不是</b>「什么都不做」）</h3>
 * <p>{@code favorite} 域<b>自己捕获</b> {@code httpx.Parse} 的错（Go 侧 7 个 handler 各自捕获
 * ⇒ 拒绝形状 = 域自定义信封 200 + {code:5003,…}）⇒ ⇒ <b>共享层装闸门反而会破坏形状</b>
 * ⇒ ⇒ 它的语义答案<b>就是</b> {@code OFF}（不是「将就」，是<b>语义正确</b>）
 * ⇒ ⇒ 缺省即正确 ⇒ <b>不装</b>闸门。
 * <br>⚠️ 但「off」<b>不等于</b>「不管容器上限」：**2026-10-27 起「抬容器上限」与「装不装闸门」已解耦**
 * ⇒ 容器上限<b>一律</b>抬到 10001（见 {@link GoStyleTomcatParameterLimitCustomizer}）
 * ⇒ ⇒ favorite 的「Tomcat 总参数值数 vs Go 非空值数」<b>差 1</b> 由此闭合
 * ⇒ ⇒ 守门见 {@link GoStyleParamLimitOffModeGateTest}。
 */
@DisplayName("🔴 form 值上限闸门的模式分派")
class GoStyleFormLimitModeTest {

    // ==================================================================
    // ① 属性值 → 模式
    // ==================================================================

    @Test
    @DisplayName("🔴 缺省 / 空串 ⇒ OFF（favorite 域走这条 ⇒ 它的语义答案就是 OFF ⇒ 缺省即正确）")
    void missingPropertyMeansOff() {
        assertEquals(GoStyleFormLimitMode.Mode.OFF, GoStyleFormLimitMode.parse(null));
        assertEquals(GoStyleFormLimitMode.Mode.OFF, GoStyleFormLimitMode.parse(""));
        assertEquals(GoStyleFormLimitMode.Mode.OFF, GoStyleFormLimitMode.parse("   "));
    }

    @Test
    @DisplayName("raw / envelope 两个取值 + 大小写不敏感")
    void knownValuesAreParsed() {
        assertEquals(GoStyleFormLimitMode.Mode.RAW, GoStyleFormLimitMode.parse("raw"));
        assertEquals(GoStyleFormLimitMode.Mode.RAW, GoStyleFormLimitMode.parse(" RAW "));
        assertEquals(GoStyleFormLimitMode.Mode.ENVELOPE, GoStyleFormLimitMode.parse("envelope"));
        assertEquals(GoStyleFormLimitMode.Mode.ENVELOPE, GoStyleFormLimitMode.parse("Envelope"));
    }

    @Test
    @DisplayName("🔴 非法取值必须**炸**（静默兜底 = 以为装上了、其实没装）")
    void illegalValueFailsFast() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GoStyleFormLimitMode.parse("raws"));
        assertTrue(e.getMessage().contains(GoStyleFormLimitMode.PROPERTY),
                "异常信息必须点名那个属性，便于排查；实际=" + e.getMessage());
    }

    // ==================================================================
    // ② 三种模式下拦截器的行为
    // ==================================================================

    @Test
    @DisplayName("🔴 OFF ⇒ 2049 个非空值也**放行**（闸门根本没装）")
    void offModePassesEverythingThrough() throws Exception {
        GoStyleFormValueLimitInterceptor interceptor = new GoStyleFormValueLimitInterceptor("off");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/probe");
        req.setQueryString(nonEmptyQuery(2049));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, null),
                "OFF 模式必须直接放行（返回 true），不写任何响应");
        assertEquals(200, resp.getStatus(), "OFF 模式不得写响应");
        assertEquals(0, resp.getContentAsByteArray().length, "OFF 模式不得写 body");
    }

    @Test
    @DisplayName("🔴 RAW ⇒ 2049 个非空值 ⇒ 400 + Go 的 http.Error 形状")
    void rawModeWritesGoShapedBadRequest() throws Exception {
        GoStyleFormValueLimitInterceptor interceptor = new GoStyleFormValueLimitInterceptor("raw");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/probe");
        req.setQueryString(nonEmptyQuery(2049));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(req, resp, null),
                "RAW 模式写完响应后必须返回 false（不再进 controller）");
        assertEquals(400, resp.getStatus(), "Go 的 http.Error 形状是 400");
        assertEquals("text/plain; charset=utf-8", resp.getHeader("Content-Type"));
        assertEquals("nosniff", resp.getHeader("X-Content-Type-Options"));
        assertEquals("too many form values, error: " + nonEmptyQuery(2049) + "\n",
                resp.getContentAsString(),
                "body 必须逐字等于 Go 的 fmt.Errorf(\"too many form values, error: %s\", r.Form.Encode()) + \"\\n\"");
    }

    @Test
    @DisplayName("🔴 ENVELOPE ⇒ 抛 GoStyleFormLimitExceededException（由域自己的兜底 handler 渲染）")
    void envelopeModeThrowsForDomainAdvice() {
        GoStyleFormValueLimitInterceptor interceptor = new GoStyleFormValueLimitInterceptor("envelope");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/probe");
        req.setQueryString(nonEmptyQuery(2049));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        GoStyleFormLimitExceededException e = assertThrows(GoStyleFormLimitExceededException.class,
                () -> interceptor.preHandle(req, resp, null));
        assertTrue(e.getGoMessage().startsWith("too many form values, error: "),
                "异常里带的应是 Go 的原文（仅用于日志，不进 wire）；实际=" + e.getGoMessage());
        assertEquals(0, resp.getContentAsByteArray().length,
                "ENVELOPE 模式**不写**响应（必须交给 advice 渲染）");
    }

    @Test
    @DisplayName("🔴 ENVELOPE ⇒ 恰好 2048 个非空值 ⇒ 放行")
    void envelopeModeAcceptsAtLimit() throws Exception {
        GoStyleFormValueLimitInterceptor interceptor = new GoStyleFormValueLimitInterceptor("envelope");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/probe");
        req.setQueryString(nonEmptyQuery(2048));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, null), "第 2048 个非空值仍在 Go 的上限之内");
    }

    @Test
    @DisplayName("🔴 非法模式 ⇒ 构造期就炸（不会装出一个半成品的闸门）")
    void illegalModeFailsAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new GoStyleFormValueLimitInterceptor("nope"));
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static String nonEmptyQuery(int n) {
        StringBuilder sb = new StringBuilder(n * 4);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("a=1");
        }
        return sb.toString();
    }
}
