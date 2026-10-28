package com.rag.like.like.rpc.service;

import io.opentelemetry.api.trace.Span;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * like 域的 <b>span 属性埋点</b>（A9 轴 · trace/otel）。
 *
 * <h2>修的是什么</h2>
 * <p>Go 侧 like 域 <b>6 个 logic + 1 个 consumer</b> 往 span 上打属性，共 <b>13 个属性名</b>：
 * <pre>
 *   query.user_id / query.target_type / query.target_id / query.batch_size / query.cursor / query.page_size
 *   cache.hits / cache.misses / cache.status
 *   like.user_id / like.target_type / like.target_id / like.action_type
 *   messaging.system / messaging.destination / messaging.operation / messaging.message.key
 * </pre>
 * RAG 侧此前<b>一个都没有</b>（{@code service/like} 下各子模块的 {@code src/main} 内 {@code setAttribute} 零命中）
 * ⇒ 运维在 trace 里看不到 like 域的查询维度与缓存命中维度。
 *
 * <h2>🔴 与 {@code AdminSpans} 的关键区别：<b>本类不建 span</b></h2>
 * <p>admin 域的 Go 侧是 {@code ctx, span := tracer.Start(l.ctx, "Action-Admin-…")}
 * ⇒ <b>自己开一个子 span</b>，所以 RAG 侧照做一个子 span 是对齐的（{@code AdminSpans.traced}）。
 * <p>like 域<b>不是</b>：Go 侧是
 * <pre>
 *   span := trace.SpanFromContext(l.ctx)   // ← 取 ctx 里**已有**的 span，不新建
 *   span.SetAttributes(…)
 * </pre>
 * 那个「已有的 span」是 <b>go-zero 自带</b>的服务端 tracing span
 * （{@code zrpc/internal/serverinterceptors/tracinginterceptor.go:95-107} 的 {@code startSpan}
 * ⇒ {@code tr.Start(ctx, …)} ⇒ <b>span 被放进 ctx</b>）。
 * <p>⇒ <b>本类只往「当前 span」上加属性，绝不新建 span</b>：
 * 新建子 span 会让 trace 里多出一个 Go 没有的 span ⇒ 结构漂移（AGENTS.md §3）。
 *
 * <h2>L4 idiomatization（§3.2 三段式）</h2>
 * <p><b>比 Go 多做了什么</b>：Go 用 {@code ctx} 显式传递 span（{@code trace.SpanFromContext(ctx)}）；
 * Java 没有 ctx 参数这一说 ⇒ 用 {@link Span#current()} 取「当前线程的当前 span」。
 * <p><b>语义等价论证</b>：
 * <ul>
 *   <li>Go 的 {@code trace.SpanFromContext(ctx)}：ctx 里有 span ⇒ 返回它；<b>没有 ⇒ 返回 noop span</b>
 *       （{@code setAttribute} 是空操作）。</li>
 *   <li>Java 的 {@code Span.current()}：当前上下文有 span ⇒ 返回它；<b>没有 ⇒ 返回
 *       {@code Span.getInvalid()}（non-recording）</b>，其 {@code setAttribute} <b>同样是空操作</b>。</li>
 *   <li>⇒ 两边「无 span 时静默丢弃属性」的行为<b>逐条一致</b>。</li>
 * </ul>
 * <p><b>差异</b>：Go 读的是 <b>方法参数里的 ctx</b>，Java 读的是 <b>线程上下文</b>。
 * 在 gRPC 服务端，两者的载体必须由拦截器把 span 放进「当前上下文」才等价
 * ⇒ 该前提由共享层 {@code com.rag.common.observability.RpcInterceptor} 负责
 * （<b>当前形态下 handler 执行期 span 尚未成为 current ⇒ 本类的方法在真链上暂时是空操作</b>
 * ⇒ 已作为 <b>FB-04</b> 派给共享层常设执行者；域侧写法本身不依赖那次改动）。
 *
 * <h2>⚠️ 属性前缀不要照抄 {@code audit.*}</h2>
 * <p>{@code audit.*} 是 <b>admin/users 域专用</b>；like 域 Go 侧实际用的是
 * {@code query.*} / {@code cache.*} / {@code like.*} / {@code messaging.*}。
 */
public final class LikeSpans {

    // ---------------------------------------------------------------- query.*

    /** Go {@code attribute.Int64("query.user_id", in.UserId)}。 */
    public static final String ATTR_QUERY_USER_ID = "query.user_id";
    /** Go {@code attribute.String("query.target_type", in.TargetType)}。 */
    public static final String ATTR_QUERY_TARGET_TYPE = "query.target_type";
    /** Go {@code attribute.String("query.target_id", in.TargetId)}。 */
    public static final String ATTR_QUERY_TARGET_ID = "query.target_id";
    /** Go {@code attribute.Int("query.batch_size", len(in.TargetIds))}。 */
    public static final String ATTR_QUERY_BATCH_SIZE = "query.batch_size";
    /** Go {@code attribute.Int64("query.cursor", in.Cursor)}。 */
    public static final String ATTR_QUERY_CURSOR = "query.cursor";
    /** Go {@code attribute.Int64("query.page_size", int64(in.PageSize))}。 */
    public static final String ATTR_QUERY_PAGE_SIZE = "query.page_size";

    // ---------------------------------------------------------------- cache.*

    /** Go {@code attribute.Int("cache.hits", hits)}。 */
    public static final String ATTR_CACHE_HITS = "cache.hits";
    /** Go {@code attribute.Int("cache.misses", misses)}。 */
    public static final String ATTR_CACHE_MISSES = "cache.misses";
    /** Go {@code attribute.String("cache.status", "hit" | "miss")}。 */
    public static final String ATTR_CACHE_STATUS = "cache.status";

    /** Go 的 {@code cache.status} 命中取值。 */
    public static final String CACHE_STATUS_HIT = "hit";
    /** Go 的 {@code cache.status} 未命中取值。 */
    public static final String CACHE_STATUS_MISS = "miss";

    // ---------------------------------------------------------------- like.*

    /** Go {@code attribute.Int64("like.user_id", in.UserId)}。 */
    public static final String ATTR_LIKE_USER_ID = "like.user_id";
    /** Go {@code attribute.String("like.target_type", in.TargetType)}。 */
    public static final String ATTR_LIKE_TARGET_TYPE = "like.target_type";
    /** Go {@code attribute.String("like.target_id", in.TargetId)}。 */
    public static final String ATTR_LIKE_TARGET_ID = "like.target_id";
    /** Go {@code attribute.String("like.action_type", actionStr)}。 */
    public static final String ATTR_LIKE_ACTION_TYPE = "like.action_type";

    // ---------------------------------------------------------------- messaging.*

    /** Go {@code attribute.String("messaging.system", "kafka")}。 */
    public static final String ATTR_MESSAGING_SYSTEM = "messaging.system";
    /** Go {@code attribute.String("messaging.destination", topic)}。 */
    public static final String ATTR_MESSAGING_DESTINATION = "messaging.destination";
    /** Go {@code attribute.String("messaging.operation", "consume")}。 */
    public static final String ATTR_MESSAGING_OPERATION = "messaging.operation";
    /** Go {@code attribute.String("messaging.message.key", key)}。 */
    public static final String ATTR_MESSAGING_MESSAGE_KEY = "messaging.message.key";

    /**
     * Go {@code likemq.go:84} 里 {@code likeMessageAttrs("like-topic", key)} 的<b>字面量 topic</b>。
     *
     * <p>🔴 <b>注意这不是配置里的真 topic</b>（真 topic 是 {@code like_promotion_topic}，
     * 见 {@code like.yaml KafkaConf.Topic} 与 {@code mq.yaml Kafka.Topic}）。
     * Go 侧把字面量 {@code "like-topic"} 写死了 ⇒ 它的 span 上 {@code messaging.destination}
     * 永远是 {@code "like-topic"}。
     * <p>⇒ RAG <b>照抄这个字面量</b>：若「顺手改成真 topic」，RAG 的 trace 数据就会比 Go 多一条差异
     * （AGENTS.md §3：差异只允许出现在表达层）。
     */
    public static final String GO_MESSAGING_DESTINATION_LITERAL = "like-topic";

    /** Go {@code likemq.go:84} 的 tracer 名。 */
    public static final String MQ_TRACER_NAME = "like-mq";
    /** Go {@code likemq.go:84} 的 span 名。 */
    public static final String MQ_SPAN_NAME = "LikeUpdateService.Consume";

    private LikeSpans() {
    }

    // ---------------------------------------------------------------- 属性写入

    /** {@code query.user_id}（Go {@code attribute.Int64}）。 */
    public static void queryUserId(long userId) {
        attr(ATTR_QUERY_USER_ID, userId);
    }

    /** {@code query.target_type}（Go {@code attribute.String}）。 */
    public static void queryTargetType(String targetType) {
        attr(ATTR_QUERY_TARGET_TYPE, targetType);
    }

    /** {@code query.target_id}（Go {@code attribute.String}）。 */
    public static void queryTargetId(String targetId) {
        attr(ATTR_QUERY_TARGET_ID, targetId);
    }

    /** {@code query.batch_size}（Go {@code attribute.Int} ⇒ OTel Java 侧同为 long 型属性）。 */
    public static void queryBatchSize(int batchSize) {
        attr(ATTR_QUERY_BATCH_SIZE, (long) batchSize);
    }

    /** {@code query.cursor}（Go {@code attribute.Int64}）。 */
    public static void queryCursor(long cursor) {
        attr(ATTR_QUERY_CURSOR, cursor);
    }

    /** {@code query.page_size}（Go {@code attribute.Int64}）。 */
    public static void queryPageSize(long pageSize) {
        attr(ATTR_QUERY_PAGE_SIZE, pageSize);
    }

    /** {@code cache.hits}（Go {@code attribute.Int}）。 */
    public static void cacheHits(int hits) {
        attr(ATTR_CACHE_HITS, (long) hits);
    }

    /** {@code cache.misses}（Go {@code attribute.Int}）。 */
    public static void cacheMisses(int misses) {
        attr(ATTR_CACHE_MISSES, (long) misses);
    }

    /** {@code cache.status=hit}（Go {@code attribute.String("cache.status", "hit")}）。 */
    public static void cacheStatusHit() {
        attr(ATTR_CACHE_STATUS, CACHE_STATUS_HIT);
    }

    /** {@code cache.status=miss}（Go {@code attribute.String("cache.status", "miss")}）。 */
    public static void cacheStatusMiss() {
        attr(ATTR_CACHE_STATUS, CACHE_STATUS_MISS);
    }

    /** {@code like.user_id}（Go {@code attribute.Int64}）。 */
    public static void likeUserId(long userId) {
        attr(ATTR_LIKE_USER_ID, userId);
    }

    /** {@code like.target_type}（Go {@code attribute.String}）。 */
    public static void likeTargetType(String targetType) {
        attr(ATTR_LIKE_TARGET_TYPE, targetType);
    }

    /** {@code like.target_id}（Go {@code attribute.String}）。 */
    public static void likeTargetId(String targetId) {
        attr(ATTR_LIKE_TARGET_ID, targetId);
    }

    /** {@code like.action_type}（Go {@code attribute.String}，取值 like/unlike/dislike/undislike/unknown）。 */
    public static void likeActionType(String actionType) {
        attr(ATTR_LIKE_ACTION_TYPE, actionType);
    }

    /**
     * 构造 MQ 消费侧的 4 个属性 —— 对应 Go {@code likemq.go:137-143} 的 {@code likeMessageAttrs}。
     *
     * <p>Go 出处：
     * <pre>
     *   func likeMessageAttrs(topic, key string) []attribute.KeyValue {
     *       return []attribute.KeyValue{
     *           attribute.String("messaging.system", "kafka"),
     *           attribute.String("messaging.destination", topic),
     *           attribute.String("messaging.operation", "consume"),
     *           attribute.String("messaging.message.key", key),
     *       }
     *   }
     * </pre>
     * ⚠️ {@code topic} 传的是<b>字面量 {@code "like-topic"}</b>，不是配置里的真 topic
     * ⇒ 见 {@link #GO_MESSAGING_DESTINATION_LITERAL}。
     *
     * @param key 消费到的这条消息的 Kafka key（Go 侧 {@code Consume(ctx, key, val)} 的 {@code key} 参数）
     */
    public static Map<String, Object> messagingAttributes(String key) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ATTR_MESSAGING_SYSTEM, "kafka");
        attrs.put(ATTR_MESSAGING_DESTINATION, GO_MESSAGING_DESTINATION_LITERAL);
        attrs.put(ATTR_MESSAGING_OPERATION, "consume");
        attrs.put(ATTR_MESSAGING_MESSAGE_KEY, key);
        return attrs;
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 往「当前 span」上写一个属性。
     *
     * <p>🔴 <b>无当前 span 时必须是空操作</b>（不能抛异常、不能 NPE）：
     * Go 侧 {@code trace.SpanFromContext(ctx)} 在无 span 时返回 noop span，
     * 其 {@code SetAttributes} 同样是空操作 ⇒ 两侧「没有 span 就静默丢弃」的行为一致。
     * OTel Java 的 {@code Span.current()} 在无 span 时返回 {@code Span.getInvalid()}
     * （non-recording）⇒ {@code setAttribute} 对它是空操作 ✓。
     */
    private static void attr(String key, String value) {
        Span.current().setAttribute(key, value);
    }

    private static void attr(String key, long value) {
        Span.current().setAttribute(key, value);
    }
}
