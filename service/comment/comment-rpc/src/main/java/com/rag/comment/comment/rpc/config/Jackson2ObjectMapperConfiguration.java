package com.rag.comment.comment.rpc.config;

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
 * <h2>为什么必须显式提供（这不是「重复造轮子」）</h2>
 * <p>Spring Boot 4.0.8 把 Jackson 支持<b>拆成了独立模块</b>：只有引入
 * {@code org.springframework.boot:spring-boot-jackson} 才会装配 Jackson 自动配置，
 * 而且它自动配置出来的是 <b>Jackson 3</b>（{@code tools.jackson.databind}）。
 * 而本模块有构造注入 Jackson 2 的 {@code ObjectMapper} 的地方
 * （{@code CommentAuditListener} 解析 Kafka 消息、{@code CommentDomainFactService} 序列化事实载荷）。
 * 两套 Jackson 的包名不同、API 不通用，加 {@code spring-boot-jackson} 依赖<b>并不能</b>满足这些注入点，
 * 只会多引一份用不上的自动配置。本类只补上这一个 Bean 缺口，<b>不改任何序列化行为</b>。
 *
 * <p>⚠️ 实测（{@code docs/DRIFT/like/rpc.md} DRIFT-LIKE-013 / {@code hot-rpc} 同款）：
 * 缺这个 Bean 时启动报 {@code NoSuchBeanDefinitionException: ObjectMapper}
 * ⇒ <b>组件根本起不来</b>，而在此之前的测试全是全绿的。
 *
 * <h2>Go 出处与语义等价论证</h2>
 *
 * <p><b>Go 出处（反序列化方向）</b>：{@code service/comment/rpc/internal/mqs/audit_consumer.go:36}
 * <pre>
 * if err := json.Unmarshal([]byte(val), &amp;msg); err != nil {
 *     l.Errorf("parse comment kafka message failed: %v", err)
 *     return nil            // ← 解析失败 = 吞掉这条消息（提交位点）
 * }
 * </pre>
 * 这条出口是<b>可观察的</b>：解析失败的消息被 drop，不进库、不发通知。
 * 因此「什么算解析失败」必须与 Go 一致，否则 drop/retry 的分界就移了。
 *
 * <p><b>序列化方向</b>：{@code create_comment_logic.go:117} 的 {@code json.Marshal(msg)}，
 * 以及 {@code model/domain_fact.go:124,128} 的 {@code json.Marshal(fact)} /
 * {@code json.Marshal(envelope)}。
 *
 * <h2>为什么必须关掉 {@code FAIL_ON_UNKNOWN_PROPERTIES}</h2>
 * <p>Go 的 {@code encoding/json}（标准库）<b>默认忽略未知字段</b>，
 * 一条带多余字段的 Kafka 消息在 Go 侧<b>正常处理</b>；
 * 而 Jackson 2 默认会抛 {@code UnrecognizedPropertyException}
 * ⇒ Java 侧会走进「解析失败 ⇒ drop」分支，而 Go 侧照常消费
 * ⇒ <b>方向相反的漂移</b>。
 *
 * <h2>为什么还要关掉「标量强转」</h2>
 * <p>Jackson 2 默认会把 {@code {"comment_id":"123"}} 之外的形态静默转换，
 * 而 Go 的 {@code encoding/json} 对 number → string 是<b>报错</b>的：
 * <pre>
 * json: cannot unmarshal number into Go struct field ... of type string
 * </pre>
 * ⇒ 两侧对<b>同一条消息</b>的判定相反：Go 侧解析失败 ⇒ 该消息被 drop；
 * Java 侧解析成功 ⇒ 该消息被当成正常评论入库 ⇒ <b>方向相反的漂移</b>。
 *
 * <h2>⚠️ 序列化方向的转义差异<b>不由本类处理</b></h2>
 * <p>Go 的 {@code encoding/json} <b>开启</b> HTML 转义（{@code <} → {@code \u003c} 等），
 * Jackson 2 <b>关闭</b>。
 * ⇒ 该差异由 {@code CommentDomainFactService} 负责（在写入 outbox 之前做规范化），
 * <b>不在本类里改</b>：改本类会连带影响所有<b>反序列化</b>路径，而那部分 Go 与 Jackson 行为是一致的。
 */
@Configuration(proxyBeanMethods = false)
public class Jackson2ObjectMapperConfiguration {

    /**
     * 默认构造的 Jackson 2 mapper。
     *
     * <p>显式关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES} 与标量强转，依据见类级 javadoc。
     *
     * <p>⚠️ 刻意<b>不</b>关掉「String → 整数字段保持接受」：Go 侧
     * {@code CommentKafkaMsg} 的字符串化字段在反序列化时同样走字符串通道，
     * 一并关掉会把「两边都接受」的输入改成「只有一边接受」，是<b>把一条漂移换成另一条</b>。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 数字 / 浮点 / 布尔一律不得进 Textual 字段（Go 的 encoding/json 对此报错）
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // number → 布尔字段也必须 Fail：Go 的 encoding/json 同样报错。
                // 本域 wire 上没有 bool 字段，但规则是全字段成立的，先按全域配上。
                .withCoercionConfig(LogicalType.Boolean, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
                .build();
    }
}