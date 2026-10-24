package com.rag.like.like.rpc.config;

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
 * 而且它自动配置出来的是 <b>Jackson 3</b>（{@code tools.jackson.databind.json.JsonMapper}）。
 * 而本模块有 <b>6 处构造注入</b> Jackson 2 的
 * {@code com.fasterxml.jackson.databind.ObjectMapper}
 * （{@code LikeUpdateKafkaListener} / {@code LikeDomainService} /
 * {@code LikeMessageDigest} / {@code LikeDomainFactService} /
 * {@code LikeTaskProgressPublisher} / {@code LikeMessageIngestService}）——
 * 那份 databind 是从 {@code com.rag:common} 间接带进来的。
 *
 * <p>两套 Jackson 的包名不同、API 不通用（Jackson 3 在 {@code tools.jackson.databind}），
 * 所以<b>加 spring-boot-jackson 依赖并不能满足这里的注入点</b>，
 * 只会多引一份用不上的自动配置。本类只补上这一个 Bean 缺口，<b>不改任何序列化行为</b>。
 *
 * <p>⚠️ 实测（2026-10-23，{@code docs/DRIFT/like/rpc.md} DRIFT-LIKE-013）：
 * 缺这个 Bean 时启动报 {@code NoSuchBeanDefinitionException: ObjectMapper}
 * ⇒ <b>组件根本起不来</b>，而在此之前的测试全是全绿的。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p><b>Go 出处</b>：反序列化方向 {@code service/like/rpc/internal/mq/internal/mqs/likemq.go:86}
 * —— {@code if err := json.Unmarshal([]byte(val), &msg); err != nil { … }}。
 * 序列化方向则是 {@code service/like/rpc/internal/model/domain_fact.go:120,124} 与
 * {@code service/like/rpc/internal/mq/internal/mqs/likemq.go:49} 的 {@code json.Marshal}。
 *
 * <p><b>为什么必须关掉 {@code FAIL_ON_UNKNOWN_PROPERTIES}</b>：
 * Go 的 {@code encoding/json}（标准库）<b>默认忽略未知字段</b>，
 * 一条带多余字段的 Kafka 消息在 Go 侧<b>正常处理</b>；
 * 而 Jackson 2 默认会抛 {@code UnrecognizedPropertyException}，
 * 于是 Java 侧会走进「解析失败 ⇒ 丢弃消息」分支，
 * 而 Go 侧照常消费 ⇒ <b>方向相反的漂移</b>。
 *
 * <h2>⚠️ 序列化方向的转义差异<b>不由本类处理</b></h2>
 * <p>Go 的 {@code encoding/json} <b>开启</b> HTML 转义
 * （{@code <} → {@code \u003c} 等），Jackson 2 <b>关闭</b>；
 * 且 Go 会把字符串里的 U+2028/U+2029 转成 {@code \u2028}/{@code \u2029}，Jackson 2 不会。
 * ⇒ 该差异由本模块既有的 {@code GoCompatibleJson}（按 Go 规则重排 JsonNode 后再序列化）
 * 承担，<b>不在本类里改</b>：改本类会连带影响所有 <b>反序列化</b>路径，
 * 而那部分 Go 与 Jackson 行为是一致的。
 *
 * <p>未来若本模块开始收发带小数或超大整数的载荷，<b>先取证</b>再改本类：
 * 那属于可观察语义变更。
 *
 * @see com.rag.like.like.rpc.LikeRpcApplication 关于 {@code @Import} 精确引入的选择
 */
@Configuration(proxyBeanMethods = false)
public class Jackson2ObjectMapperConfiguration {

    /**
     * 默认构造的 Jackson 2 mapper。
     *
     * <p>显式关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES} 是<b>有意的</b>，
     * 依据见类级 javadoc 的「Go 出处与语义等价论证」。
     *
     * <h2>⚠️ 为什么还要关掉「标量强转」</h2>
     * <p>Jackson 2 默认会把 {@code {"msg_id":123}} <b>静默转成 {@code "123"}</b>；
     * 而 Go 的 {@code encoding/json} 对 number → string 是<b>报错</b>：
     * <pre>
     * json: cannot unmarshal number into Go struct field ... of type string
     * </pre>
     * ⇒ 两侧对<b>同一条消息</b>的判定相反：Go 侧解析失败 ⇒ 该消息被 drop；
     * Java 侧解析成功 ⇒ 该消息被当成正常事件处理 ⇒ <b>方向相反的漂移</b>。
     *
     * <p>规则与 {@code users-common} 的 {@code GoStyleCoercion} 规则 1 完全一致
     * （数字 / 浮点 / 布尔一律不得进 Textual 字段），
     * 只是这里是 <b>Jackson 2</b> 版：{@code GoStyleCoercion} 用的是 Jackson 3
     * （{@code tools.jackson}），本模块用的是 Jackson 2（{@code com.fasterxml.jackson}），
     * 两套 API 不通用 ⇒ 只能在此各写一份。
     *
     * <p>⚠️ <b>刻意不关掉「String → 整数字段保持接受」</b>：
     * Go 侧字符串化后走 {@code strconv.ParseInt}，字符串反而是它的正规入口；
     * 一并关掉会把「两边都接受」的输入改成「只有一边接受」，是<b>把一条漂移换成另一条</b>。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // 🔴 number → 布尔字段也必须 Fail（Go 的 encoding/json 对此报错）：
                //   json: cannot unmarshal number into Go struct field ... of type bool
                // 而 Jackson 2 默认接受并静默转换（1 ⇒ true）。
                // ⚠️ 本条在 message 域曾是**投放事故级**漂移：
                //   `AdminSendNotificationReq.broadcast` 决定「定向通知」还是「全量广播」，
                //   {"broadcast":1} ⇒ Go 拒绝 / Java 静默变成全量广播（DRIFT-MSGAPI-005）。
                //   Go 的规则是**全字段**成立的（`stringOption` 对 bool 无效），故这里全域配。
                .withCoercionConfig(LogicalType.Boolean, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
                .build();
    }
}
