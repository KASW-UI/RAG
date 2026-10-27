package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 守门：<b>{@code mode: off} 必须是可用的</b>（G2 反馈单 FB-05 · A3 轴）。
 *
 * <h2>这颗雷长什么样</h2>
 *
 * <p>{@link GoStyleFormLimitMode} 的合法取值是 {@code off | raw | envelope}，
 * <b>文档、注释、各域 yaml 的说明里都写「{@code off} = 关闭闸门」</b>。
 * ⚠️ 但 <b>YAML 1.1 会把未加引号的 {@code off} 解析成布尔 {@code false}</b>
 * （SnakeYAML 沿用 YAML 1.1 的布尔字面量表）
 * ⇒ ⇒ {@code @Value} 拿到的是字符串 {@code "false"}
 * ⇒ ⇒ 原 {@code parse} 的合法值域里<b>没有</b> {@code "false"}
 * ⇒ ⇒ <b>构造期抛 {@link IllegalArgumentException} ⇒ 进程起不来</b>
 * ⇒ ⇒ <b>文档里那个合法取值，在 YAML 里写不出来</b>（除非加引号 {@code mode: "off"}）。
 *
 * <h2>本守门钉死三件事</h2>
 * <ol>
 *   <li><b>端到端的 YAML 事实</b>：用 <b>生产同款</b>的 {@link YamlPropertySourceLoader}
 *       解析<b>未加引号</b>的 {@code mode: off} ⇒ 断言拿到的<b>确实是布尔</b> {@code false}
 *       ⇒ ⚠️ <b>不能用裸 SnakeYAML 代替</b> —— 得测「<b>Boot 实际用哪个解析器</b>」这件事。</li>
 *   <li><b>「按文档写就能用」</b>：真 servlet 上下文 ＋ <b>真 yaml</b>（未加引号的 {@code mode: off}）
 *       ⇒ 断言<b>上下文起得来</b>（修前这里是 {@code BeanCreationException}）。</li>
 *   <li><b>没有放宽成兜底</b>：{@code parse("bogus")} / {@code parse("true")} / {@code parse("on")}
 *       ⇒ <b>仍然抛异常</b> ⇒ 证明「接受 {@code false} 这个别名」<b>不等于</b>「非法值一律当 OFF」。</li>
 * </ol>
 *
 * <h2>Go 侧出处</h2>
 * <p>Go 侧<b>没有这个开关</b>（{@code rest/config.go} 里没有 form-limit 旋钮；
 * {@code maxFormParamCount = 2048} 是 {@code rest/httpx/util.go:15} 的常量）
 * ⇒ ⇒ OFF 在 Go 侧的等价物就是「<b>什么都不做</b>」
 * ⇒ ⇒ RAG 的 OFF 语义本身是对的，<b>问题只出在「配置值 → 代码」的传递</b>。
 */
@DisplayName("form 闸门开关：YAML 里的 off 必须可用（FB-05 · YAML 1.1 布尔化）")
class GoStyleFormLimitModeYamlBooleanGateTest {

    private static final String PROPERTY = GoStyleFormLimitMode.PROPERTY;

    /** 一份**未加引号**的 {@code off} —— 正是「域侧按文档写」的形态。 */
    private static final String YAML_UNQUOTED_OFF = """
            rag:
              http:
                go-form-limit:
                  mode: off
            """;

    // ==================== ① 原语义不变 ====================

    @Test
    @DisplayName("① {@code off} / {@code raw} / {@code envelope} / 空值 的原语义<b>一字未改</b>")
    void originalValuesKeepTheirMeaning() {
        assertThat(GoStyleFormLimitMode.parse("off")).isEqualTo(GoStyleFormLimitMode.Mode.OFF);
        assertThat(GoStyleFormLimitMode.parse("raw")).isEqualTo(GoStyleFormLimitMode.Mode.RAW);
        assertThat(GoStyleFormLimitMode.parse("envelope")).isEqualTo(GoStyleFormLimitMode.Mode.ENVELOPE);
        assertThat(GoStyleFormLimitMode.parse(null)).isEqualTo(GoStyleFormLimitMode.Mode.OFF);
        assertThat(GoStyleFormLimitMode.parse("")).isEqualTo(GoStyleFormLimitMode.Mode.OFF);
        assertThat(GoStyleFormLimitMode.parse("   ")).isEqualTo(GoStyleFormLimitMode.Mode.OFF);
    }

    // ==================== ② 新别名 ====================

    @Test
    @DisplayName("② {@code false}（YAML 1.1 里 off 的布尔形态）必须与 {@code off} <b>同义</b>")
    void yamlBooleanFalseIsAcceptedAsOff() {
        assertThat(GoStyleFormLimitMode.parse("false"))
                .as("YAML 1.1 把未加引号的 off 解析成布尔 false ⇒ parse 必须认它")
                .isEqualTo(GoStyleFormLimitMode.Mode.OFF);
        // YAML 1.1 的布尔字面量表里还有 False / FALSE ⇒ parse 已 toLowerCase ⇒ 同样要认
        assertThat(GoStyleFormLimitMode.parse("False")).isEqualTo(GoStyleFormLimitMode.Mode.OFF);
        assertThat(GoStyleFormLimitMode.parse("FALSE")).isEqualTo(GoStyleFormLimitMode.Mode.OFF);
    }

    // ==================== ③ YAML 端到端事实 ====================

    @Test
    @DisplayName("③ <b>生产同款</b>的 YAML loader 把未加引号的 {@code off} 变成<b>布尔</b> false")
    void productionYamlLoaderBooleansOff() throws Exception {
        Object raw = loadYamlValue(YAML_UNQUOTED_OFF);

        assertThat(raw)
                .as("必须用 Spring/Boot 实际的 YAML loader 解析（不能拿裸 SnakeYAML 代替）"
                        + " ⇒ 断言的是「Boot 实际用哪个解析器」这件事")
                .isInstanceOf(Boolean.class)
                .as("YAML 1.1 把未加引号的 off 解析成布尔 ⇒ 拿到的是 Boolean 而不是 String")
                .isEqualTo(Boolean.FALSE);

        assertThat(GoStyleFormLimitMode.parse(String.valueOf(raw)))
                .as("⇒ 而 @Value 注入 String 时拿到的就是 \"false\" ⇒ parse 必须认它")
                .isEqualTo(GoStyleFormLimitMode.Mode.OFF);
    }

    // ==================== ④ 真启动：按文档写就能起来 ====================

    @Test
    @DisplayName("④ 真 servlet 上下文 ＋ 真 yaml（未加引号的 {@code mode: off}）⇒ <b>起得来</b>")
    void realContextStartsWithUnquotedOff() throws Exception {
        // ⚠️ 先在 lambda 外把 yaml 解析好（loader 会抛 checked exception，不能在 lambda 里传）
        PropertySource<?> yamlSource = yamlSource(YAML_UNQUOTED_OFF);
        new WebApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources()
                        .addFirst(yamlSource))
                .withUserConfiguration(GoStyleFormValueLimitInterceptor.class)
                .run(ctx -> assertThat(ctx)
                        .as("域侧按文档写 `mode: off` ⇒ 上下文必须起得来"
                                + "（修前：构造期 parse 抛 IllegalArgumentException ⇒ BeanCreationException"
                                + " ⇒ 进程起不来 ⇒ 零证据 ⇒ 全域静默失效）")
                        .hasNotFailed());
    }

    // ==================== ⑤ 没有放宽成兜底 ====================

    @Test
    @DisplayName("⑤ <b>非法值仍然炸</b> ⇒ 证明「接受 false 别名」≠「任意非法值当 OFF」")
    void illegalValuesStillThrow() {
        for (String illegal : List.of("bogus", "true", "on", "yes", "OFF1", "no", "n")) {
            assertThatThrownBy(() -> GoStyleFormLimitMode.parse(illegal))
                    .as("非法值 %s 必须抛异常 —— 若兜底成 OFF ⇒ 该域会**静默失去闸门**"
                            + "（本类 javadoc 明确反对「静默兜底 = 以为装上了其实没装」）", illegal)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(GoStyleFormLimitMode.PROPERTY);
        }
    }

    // ==================== 辅助 ====================

    /** 用**生产同款**的 {@link YamlPropertySourceLoader} 解析一段 yaml 文本。 */
    private static PropertySource<?> yamlSource(String yaml) throws Exception {
        return yamlPropertySources(yaml).get(0);
    }

    private static Object loadYamlValue(String yaml) throws Exception {
        return yamlPropertySources(yaml).get(0).getProperty(PROPERTY);
    }

    private static List<PropertySource<?>> yamlPropertySources(String yaml) throws Exception {
        return new YamlPropertySourceLoader()
                .load("gate-test", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
