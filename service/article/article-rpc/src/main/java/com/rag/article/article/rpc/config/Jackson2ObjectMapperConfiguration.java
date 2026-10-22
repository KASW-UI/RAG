package com.rag.article.article.rpc.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为本模块提供 <b>Jackson 2</b> 的 {@link ObjectMapper} Bean。
 *
 * <h2>为什么必须显式提供（这不是"重复造轮子"）</h2>
 * <p>Spring Boot 4.0.8 把 Jackson 支持<b>拆成了独立模块</b>：只有引入
 * {@code org.springframework.boot:spring-boot-jackson} 才会装配 Jackson 自动配置，
 * 而且它自动配置出来的是 <b>Jackson 3</b>（{@code tools.jackson.databind.json.JsonMapper}）。
 * 而本模块（与 {@code message-rpc} / {@code like-rpc} 一致）注入的是
 * <b>Jackson 2</b> 的 {@code com.fasterxml.jackson.databind.ObjectMapper} ——
 * 那份 databind 是从 {@code com.rag:common} 间接带进来的。
 *
 * <p>两套 Jackson 的包名不同、API 不通用（Jackson 3 在 {@code tools.jackson.databind}），
 * 所以<b>加 spring-boot-jackson 依赖并不能满足这里的注入点</b>，
 * 只会多引一份用不上的自动配置。本类只补上这一个 Bean 缺口，不改任何序列化行为。
 *
 * <p>⚠️ <b>本项目的同类缺口（不属于本模块范围，但值得知道）</b>：
 * {@code like-rpc} 有 6 处构造注入 {@code ObjectMapper}，同样没有该 Bean 也没有
 * {@code spring-boot-jackson} 依赖 —— 那边的上下文同样起不来。
 * 本模块自给自足，不依赖别人先修。
 *
 * <h2>⚠️ 为什么刻意保持默认配置（不开启任何 feature 开关）</h2>
 * <p>Go 侧的 {@code core/jsonx/json.go:74-77} 开了 {@code UseNumber}，
 * 那是为 <b>float 精度</b>服务的。本模块的三个 JSON 面里
 * <b>一个浮点字段都没有</b>（review 消息 / sync 结果 / outbox payload 全是 int64 + String），
 * 所以 {@code USE_BIG_DECIMAL_FOR_FLOATS} 在这里<b>没有任何可观察效果</b>。
 * 开启它只会制造一个"看起来在对齐 Go"的假动作，故不加。
 *
 * <p>未来若本模块开始收发带小数或超大整数的载荷，<b>先取证</b>再改本类：
 * 那属于可观察语义变更（message-api 的 DRIFT-MSGAPI-001/002 就是这个方向的反面教材）。
 *
 * @see com.rag.article.article.rpc.ArticleRpcApplication 关于 {@code @Import} 精确引入的选择
 */
@Configuration(proxyBeanMethods = false)
public class Jackson2ObjectMapperConfiguration {

    /**
     * 默认构造的 Jackson 2 mapper。
     *
     * <p>显式关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES} 是<b>有意的</b>：
     * Go 的 {@code encoding/json} 同样<b>忽略</b>未知字段，而 Jackson 2 默认会抛
     * {@code UnrecognizedPropertyException}。若沿用默认值，Kafka 上一条带
     * 多余字段的消息会让两个消费者走进"解析失败 ⇒ 丢弃"分支 ——
     * <b>Go 侧会正常处理，Java 侧会丢消息</b>，这是一条方向相反的漂移。
     *
     * <p>此判据在 message 域已实测确认并锁死，见 {@code ArticleSyncEventTypesContractTest} 的对拍。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}