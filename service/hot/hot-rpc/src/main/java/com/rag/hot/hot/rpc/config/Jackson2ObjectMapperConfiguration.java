package com.rag.hot.hot.rpc.config;

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
 * 而本模块注入的是 <b>Jackson 2</b> 的 {@code com.fasterxml.jackson.databind.ObjectMapper}
 * （{@code HotEventConsumer} 的构造参数）—— 那份 databind 是从 {@code com.rag:common}
 * 间接带进来的。
 *
 * <p>两套 Jackson 的包名不同、API 不通用（Jackson 3 在 {@code tools.jackson.databind}），
 * 所以<b>加 spring-boot-jackson 依赖并不能满足这里的注入点</b>，
 * 只会多引一份用不上的自动配置。本类只补上这一个 Bean 缺口，<b>不改任何序列化行为</b>。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p><b>Go 出处</b>：{@code service/hot/rpc/internal/mqs/hot_handler.go:152} ——
 * {@code if err := json.Unmarshal([]byte(value), &event); err != nil { … }}，
 * 目标是 {@code service/hot/rpc/internal/mqs/types.go:5-8} 的
 * {@code ArticleHotEvent}（只有 {@code article_id} / {@code type} 两个 string 字段）。
 *
 * <p><b>为什么必须关掉 {@code FAIL_ON_UNKNOWN_PROPERTIES}</b>：
 * Go 的 {@code encoding/json}（标准库，非 go-zero 的 jsonx）<b>默认忽略未知字段</b>，
 * 一条带多余字段的 Kafka 消息在 Go 侧<b>正常处理</b>；
 * 而 Jackson 2 默认会抛 {@code UnrecognizedPropertyException}，
 * 于是 Java 侧会走进「解析失败 ⇒ 丢弃消息」分支，
 * 而 Go 侧照常消费 ⇒ <b>方向相反的漂移</b>。
 * 该判据在 message 域已实测确认并锁死
 * （见 {@code ArticleSyncEventTypesContractTest} 的对拍），
 * 本模块是同一形态（Kafka 消息 ⇒ 标准库 {@code encoding/json}），
 * 故照同一方向处理。
 *
 * <h2>⚠️ 为什么刻意保持默认配置（不开启任何 feature 开关）</h2>
 * <p>Go 侧的 {@code core/jsonx/json.go:74-77} 开了 {@code UseNumber}，
 * 但那是 <b>go-zero 的 jsonx</b>；<b>本模块用的是标准库 {@code encoding/json}</b>
 * （见上），<b>没有</b> {@code UseNumber}。
 * 且本模块唯一反序列化的目标 {@code ArticleHotEvent}
 * <b>两个字段都是 string、零浮点</b>，
 * 所以 {@code USE_BIG_DECIMAL_FOR_FLOATS} 在这里<b>没有任何可观察效果</b>。
 * 开启它只会制造一个「看起来在对齐 Go」的假动作，故不加。
 *
 * <p>未来若本模块开始收发带小数或超大整数的载荷，<b>先取证</b>再改本类：
 * 那属于可观察语义变更。
 *
 * @see com.rag.hot.hot.rpc.HotRpcApplication 关于 {@code @Import} 精确引入的选择
 */
@Configuration(proxyBeanMethods = false)
public class Jackson2ObjectMapperConfiguration {

    /**
     * 默认构造的 Jackson 2 mapper。
     *
     * <p>显式关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES} 是<b>有意的</b>，
     * 依据见类级 javadoc 的「Go 出处与语义等价论证」。
     *
     * <h2>⚠️ 为什么还要关掉「标量强转」（2026-10-23 实测新增）</h2>
     * <p>本模块此前<b>没有配置任何 coercion 规则</b>，于是 Jackson 2 的默认行为
     * 把 {@code {"article_id":123}} <b>静默转成 {@code "123"}</b>。
     * 而 Go 的 {@code encoding/json} 对 number → string 是<b>报错</b>：
     * <pre>
     * json: cannot unmarshal number into Go struct field ArticleHotEvent.article_id of type string
     * </pre>
     * ⇒ 两侧对<b>同一条消息</b>的判定相反：Go 侧解析失败 ⇒ 该消息被 drop；
     * Java 侧解析成功 ⇒ 该消息被当成正常事件处理 ⇒ <b>方向相反的漂移</b>
     * （热榜权重会被一条本该丢弃的消息污染）。
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
