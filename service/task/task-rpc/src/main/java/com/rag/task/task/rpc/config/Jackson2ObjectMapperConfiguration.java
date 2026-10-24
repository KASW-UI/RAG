package com.rag.task.task.rpc.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为本模块提供 <b>Jackson 2</b> 的 {@link ObjectMapper} Bean。
 *
 * <h2>为什么必须显式提供（不是重复造轮子）</h2>
 * <p>Spring Boot 4.0.8 把 Jackson 支持拆成独立模块 {@code spring-boot-jackson}，
 * 而且自动配置出来的是 <b>Jackson 3</b>（{@code tools.jackson.databind}）。
 * 本模块有三处构造注入 Jackson 2 的 {@code com.fasterxml.jackson.databind.ObjectMapper}
 * （{@code TaskRawLogRouter} / {@code TaskArticleCountConsumer} / {@code TaskRewardPublisher} 的序列化），
 * 那份 databind 是从 {@code com.rag:common} 间接带进来的。
 * 两套 Jackson 包名不同、API 不通用 ⇒ 加 spring-boot-jackson 依赖并不能满足这里的注入点。
 *
 * <p>⚠️ 缺这个 Bean 时启动报 {@code NoSuchBeanDefinitionException: ObjectMapper}
 * ⇒ <b>组件根本起不来</b>，而纯 Mockito 单测照样全绿
 * （like-rpc 的同款问题见 {@code docs/DRIFT/like/rpc.md} DRIFT-LIKE-013）。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p><b>Go 出处</b>：{@code service/task/rpc/internal/Init/initGoKa.go:64} 与
 * {@code internal/sink/article_sink.go:131} 的 {@code json.Unmarshal}，
 * 以及 {@code internal/Init/initGoKa.go:102}、{@code internal/reward/producer.go:20} 侧的写方向。
 *
 * <p><b>为什么必须关掉 {@code FAIL_ON_UNKNOWN_PROPERTIES}</b>：
 * Go 的 {@code encoding/json} 标准库<b>默认忽略未知字段</b>，
 * 一条带多余字段的 Kafka 消息在 Go 侧<b>照常被解析</b>；
 * Jackson 2 默认抛 {@code UnrecognizedPropertyException} ⇒ Java 侧会走进
 * 「解析失败」分支，而 Go 侧继续 ⇒ <b>方向相反的漂移</b>。
 *
 * <p><b>为什么还要关掉「标量强转」</b>：
 * Jackson 2 默认会把 {@code {"user_id":123}} 静默转成 {@code "123"}；
 * Go 对 number → string 是<b>报错</b>（{@code json: cannot unmarshal number ... of type string}），
 * 于是一条消息在 Go 侧解析失败（⇒ 走「空 user_id」分支），在 Java 侧被当成正常值处理。
 * 规则与 like-rpc / article-rpc 的同名配置逐条一致。
 *
 * <p>⚠️ 刻意<b>不</b>关掉「String → 整数字段保持接受」：
 * Go 侧字符串化后走 {@code strconv.ParseInt}，字符串是它的正规入口
 * （如 {@code article_sink.go:135} 的 {@code strconv.ParseInt(v.UserID, 10, 64)}）。
 */
@Configuration(proxyBeanMethods = false)
public class Jackson2ObjectMapperConfiguration {

    /**
     * 默认构造的 Jackson 2 mapper。
     *
     * <p>⚠️ 本 Bean 是 {@code ObjectMapper} 类型的唯一注入点，
     * <b>不提供任何本模块专用的定制</b>；需要 Go 风格转义的场景由调用方自己处理。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
    }
}