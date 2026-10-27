package com.rag.article.article.rpc.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * article 域的 <b>手写 span</b>（L-10 · G3 域侧自修 · 2026-10-28）。
 *
 * <h2>Go 出处（消费点，不是声明点）</h2>
 * <p>Go 侧 article-rpc 的 <b>6 个 logic</b> 各自手写一个 span，<b>2 个 consumer</b> 走共享层的
 * {@code service/common/observability/mq.go:12-27} {@code TraceConsumer}：
 * <pre>
 *   internal/logic/create_article_logic.go:43-48
 *       tracer := otel.Tracer("article-rpc")
 *       ctx, span := tracer.Start(l.ctx, "CreateArticle", trace.WithAttributes(
 *           attribute.String("author_id", in.AuthorId),
 *           attribute.String("title", in.Title),
 *       ))
 *       defer span.End()
 *
 *   internal/mqs/article_consumer.go:45
 *       return observability.TraceConsumer(ctx, "article-rpc", "ArticleConsumer.Consume",
 *                                         articleMessageAttrs("article_review", key), func(ctx) error { … })
 *
 *   internal/mqs/article_consumer.go:144-150
 *       func articleMessageAttrs(topic, key string) []attribute.KeyValue {
 *           attribute.String("messaging.system", "kafka"),
 *           attribute.String("messaging.destination", topic),
 *           attribute.String("messaging.operation", "consume"),
 *           attribute.String("messaging.message.key", key),
 *       }
 * </pre>
 *
 * <h2>🔴 与 comment 域的 {@code CommentSpans} <b>判据不同</b>，不能照抄</h2>
 * <p>comment 域用「<b>异常逃出时按业务码段判断一次</b>」（{@code CommentSpans#isSystemFailure}），
 * 因为 comment 的 Go 侧 {@code RecordError} 精确落在 {@code result="sys_fail"} 的分支上。
 * <p><b>article 不适用</b>：article 的 Go 侧<b>连业务失败也记</b>，而且有「<b>记了但不返回</b>」：
 * <table border="1">
 *   <caption>article 侧「Go 记不记」的三个反例（comment 域的码段判据在这里全判错）</caption>
 *   <tr><th>位置</th><th>Go 的行为</th><th>comment 域码段判据会得出</th></tr>
 *   <tr><td>{@code update_article_logic.go:61}</td>
 *       <td>NotFound ⇒ <b>RecordError</b>（业务失败也记）</td><td>「业务码 ⇒ 不记」⇒ <b>错</b></td></tr>
 *   <tr><td>{@code delete_article_logic.go:55 / :60}</td>
 *       <td>NotFound / PermissionDenied ⇒ <b>RecordError</b></td><td>同上 ⇒ <b>错</b></td></tr>
 *   <tr><td>{@code get_article_logic.go:73-75}</td>
 *       <td>IncrViewCount 失败 ⇒ <b>RecordError 但继续往下走</b></td>
 *       <td>「异常逃出时才记」⇒ 根本不会触发 ⇒ <b>错</b></td></tr>
 * </table>
 * ⇒ ⇒ 本类采用<b>「显式逐点记录」</b>：把 live {@link Span} 交回业务方法，
 * 在 Go 的<b>每一个</b> {@code RecordError} / {@code AddEvent} / {@code SetAttributes} 点显式调用。
 * ⇒ ⇒ 代价是「Go 记了但不返回」这种形状<b>天然可表达</b>（catch 里只记、不抛）。
 *
 * <h2>🔴 {@code RecordError} 的等价物 = {@code recordException}，<b>且都不改状态</b></h2>
 * <p>Go 侧 otel-go <b>v1.44.0</b>（真源 {@code go.mod:18-21}）的
 * {@code sdk/trace/span.go:631-655}：
 * <pre>
 * func (s *recordingSpan) RecordError(err error, opts ...trace.EventOption) {
 *     …
 *     opts = append(opts, trace.WithAttributes(
 *         semconv.ExceptionType(typeStr(err)),
 *         semconv.ExceptionMessage(err.Error()),
 *     ))
 *     …
 *     s.addEvent(semconv.ExceptionEventName, opts...)   // ← 只加事件，**不** SetStatus
 * }
 * </pre>
 * ⇒ 它<b>只</b>加一个名为 {@code exception} 的事件（带 {@code exception.type} /
 * {@code exception.message} / {@code exception.stacktrace}），
 * <b>不</b>把 span 状态改成 ERROR。
 * <p>Java 侧 {@code Span#recordException(Throwable)} <b>同样</b>只加 {@code exception} 事件、
 * <b>同样</b>不改状态 ⇒ ⇒ <b>等价</b>。
 * <p>⚠️ article 的 6 个 logic <b>没有任何一处</b>显式 {@code span.SetStatus(...)}
 * （实测 {@code SetStatus} 在 6 个 Go logic 里命中数 = 0）
 * ⇒ ⇒ <b>失败时 span 状态保持 UNSET</b> ⇒ 本类<b>不得</b>调 {@code setStatus}。
 * ⇒ ⇒ 与 comment 域的 {@code tracedWithErrorStatus} <b>不是</b>同一种形状。
 *
 * <h2>🔴 {@code messaging.destination} 是「逻辑名」，<b>不是</b> kafka topic</h2>
 * <p>Go 侧 kafka topic 其实是 {@code article-creation-topic}（真源 {@code etc/article.yaml:49}），
 * 而 {@code articleMessageAttrs} 的第一个参数传的是 {@code "article_review"} / {@code "article_sync_result"}
 * —— 那是<b>逻辑名</b> ⇒ ⇒ <b>属性值不能取 {@code record.topic()}</b>（那会变成 {@code article-creation-topic}）
 * ⇒ ⇒ 与真源不一致。
 * <p>⚠️ <b>别照抄 hot-rpc 的写法</b>：{@code HotEventConsumer} 用的是 {@code record.topic()}
 * ⇒ 那是<b>另一个域</b>的形状 ⇒ 本域必须用逻辑名。
 *
 * <h2>🔴 共享层那一半已经齐了 ⇒ 本类只补「域侧接线」</h2>
 * <p>共享层 {@code service/common/src/main/java/com/rag/common/observability/MqConsumer.java:71-123}
 * 的 {@code traceConsumer(...)} 与 Go 的 {@code TraceConsumer} <b>逐项同构</b>
 * （SpanKind=CONSUMER · attrs map · 错误分档 · {@code finally span.end()}）
 * ⇒ ⇒ <b>本域不需要动共享层</b>，只需把两个 consumer 的处理体包进去。
 * <p>⇒ ⇒ 且<b>共享层不替 {@code @KafkaListener} 自动建 span</b>
 * （实测 {@code service/common/src/main/java} 下 {@code @KafkaListener} / {@code ConsumerRecord} 命中数 = 0）
 * ⇒ ⇒ 这 2 个 consumer span 此前确实<b>全缺</b>。
 *
 * <h2>🔴 子 span 的父子关系与 Go 同构</h2>
 * <p>本类用 {@link GlobalOpenTelemetry#getTracer(String)}，与共享层用的是<b>同一个 API</b>
 * （Go 侧也是 {@code otel.Tracer(...)}）⇒ <b>article-rpc 模块内即可完成</b>，
 * 不需要改共享模块，也不需要改 pom。
 * <p>⇒ {@code spanBuilder(...).startSpan()}（隐式取 {@code Context.current()}）自动挂到
 * 共享层 {@code RpcInterceptor} 的 SERVER span 之下 ⇒
 * ⇔ Go 侧 {@code tracer.Start(l.ctx, …)} 用的 {@code l.ctx} 也带着 SERVER span ⇒ <b>同构</b>。
 *
 * <h2>L4 idiomatization（AGENTS.md §3.2 三段式）</h2>
 * <p><b>比 Go 多做了什么</b>：① Go 用 {@code ctx} 显式传递；Java 没有 ctx 参数这一说
 * ⇒ 用 {@link Scope} 模拟「span 进入当前上下文、退出时还原」，
 * 并用 {@link Function} 代替 Go 的「后续代码内联在同一个函数里」；
 * ② Go 的 {@code span.RecordError(err)} 是<b>逐点</b>调用；Java 的 {@code recordException} 也是逐点，
 * 但 Java 没有 Go 那种「把 err 变量一路带着走」的写法 ⇒ 本类用<b>显式的 {@link #record} 调用点</b>表达。
 * <p><b>语义等价论证</b>：Go 的 {@code defer span.End()} ⇔ 本类的 {@code finally}；
 * Go 的 {@code span.RecordError(err)} ⇔ 本类的 {@code span.recordException(e)}（见上：两者都只加事件、都不改状态）；
 * Go 的 {@code span.AddEvent("…")} ⇔ 本类的 {@code span.addEvent("…")}（<b>事件名逐字保留，含空格</b>）；
 * Go 的 {@code trace.WithAttributes(...)}（起始属性）⇔ 本类在 {@code startSpan()} 之后立刻
 * {@code setAttribute}（<b>导出结果相同</b>：OTel 不区分「起始属性」与「后续属性」）；
 * Go 的 {@code span.SetAttributes(...)}（后续属性）⇔ 本类的 {@code span.setAttribute(...)}。
 */
public final class ArticleSpans {

    /**
     * Go 出处：6 个 logic 与 2 个 consumer <b>全部</b>写死 {@code otel.Tracer("article-rpc")}
     * （{@code create_article_logic.go:43} · {@code get_article_logic.go:47} ·
     * {@code update_article_logic.go:47} · {@code delete_article_logic.go:41} ·
     * {@code list_articles_logic.go:31} · {@code upload_file_logic.go:41} ·
     * {@code article_consumer.go:45} · {@code article_sync_result_consumer.go:40}）。
     */
    public static final String TRACER_NAME = "article-rpc";

    // ---------- 6 个 logic span 名（Go：⚠️ **无域前缀**，逐字保留）----------

    /** Go 出处：{@code create_article_logic.go:44}。 */
    public static final String CREATE_ARTICLE = "CreateArticle";
    /** Go 出处：{@code get_article_logic.go:48}。 */
    public static final String GET_ARTICLE = "GetArticle";
    /** Go 出处：{@code update_article_logic.go:48}。 */
    public static final String UPDATE_ARTICLE = "UpdateArticle";
    /** Go 出处：{@code delete_article_logic.go:42}。 */
    public static final String DELETE_ARTICLE = "DeleteArticle";
    /** Go 出处：{@code list_articles_logic.go:32}。 */
    public static final String LIST_ARTICLES = "ListArticles";
    /** Go 出处：{@code upload_file_logic.go:42}。 */
    public static final String UPLOAD_FILE = "UploadFile";

    // ---------- 2 个 consumer span 名（Go：⚠️ **有类名前缀**，与 logic span 不同）----------

    /** Go 出处：{@code article_consumer.go:45} 的 span 名。 */
    public static final String CONSUMER_REVIEW = "ArticleConsumer.Consume";

    /** Go 出处：{@code article_sync_result_consumer.go:40} 的 span 名。 */
    public static final String CONSUMER_SYNC_RESULT = "ArticleSyncResultConsumer.Consume";

    /**
     * Go 出处：{@code article_consumer.go:45} 传的 <b>逻辑名</b>（{@code "article_review"}）。
     *
     * <p>⚠️ <b>不是 kafka topic</b>（真源 {@code etc/article.yaml:49} 的 topic 是
     * {@code article-creation-topic}）⇒ ⇒ 不可用 {@code record.topic()} 代替。
     */
    public static final String DESTINATION_REVIEW = "article_review";

    /**
     * Go 出处：{@code article_sync_result_consumer.go:40} 传的 <b>逻辑名</b>
     * （{@code "article_sync_result"}）。
     *
     * <p>⚠️ 同上，<b>不是</b> kafka topic {@code article-sync-results}。
     */
    public static final String DESTINATION_SYNC_RESULT = "article_sync_result";

    // ---------- 15 个 logic 属性名（Go：⚠️ **裸名**，无前缀）----------

    /** Go 出处：{@code create_article_logic.go:45}（String）。 */
    public static final String ATTR_AUTHOR_ID = "author_id";
    /** Go 出处：{@code create_article_logic.go:46}（String）。 */
    public static final String ATTR_TITLE = "title";
    /** Go 出处：{@code create_article_logic.go:57}（String，<b>后续</b>属性）。 */
    public static final String ATTR_ARTICLE_ID = "article_id";
    /** Go 出处：{@code get_article_logic.go:57 / :65}（<b>Bool</b>）。 */
    public static final String ATTR_ARTICLE_FOUND = "article_found";
    /** Go 出处：{@code list_articles_logic.go:33}（<b>Int64</b>）。 */
    public static final String ATTR_PAGE = "page";
    /** Go 出处：{@code list_articles_logic.go:34}（<b>Int64</b>）。 */
    public static final String ATTR_PAGE_SIZE = "page_size";
    /** Go 出处：{@code list_articles_logic.go:35}（String）。 */
    public static final String ATTR_SORT_BY = "sort_by";
    /** Go 出处：{@code list_articles_logic.go:36}（<b>Bool</b>）。 */
    public static final String ATTR_DESC = "desc";
    /** Go 出处：{@code list_articles_logic.go:51}（String，<b>条件设置</b>）。 */
    public static final String ATTR_MANUAL_TYPE_TAG = "manual_type_tag";
    /** Go 出处：{@code list_articles_logic.go:55}（String，<b>条件设置</b>）。 */
    public static final String ATTR_SECONDARY_TAG = "secondary_tag";
    /** Go 出处：{@code list_articles_logic.go:71}（<b>Int64</b>，<b>后续</b>属性）。 */
    public static final String ATTR_TOTAL_ARTICLES = "total_articles";
    /** Go 出处：{@code upload_file_logic.go:43}（String）。 */
    public static final String ATTR_FILE_NAME = "file_name";
    /** Go 出处：{@code upload_file_logic.go:53}（<b>Int64</b>，<b>后续</b>属性）。 */
    public static final String ATTR_FILE_ID = "file_id";
    /** Go 出处：{@code upload_file_logic.go:62}（String，<b>后续</b>属性）。 */
    public static final String ATTR_CONTENT_TYPE = "content_type";
    /** Go 出处：{@code upload_file_logic.go:62}（String，<b>后续</b>属性）。 */
    public static final String ATTR_OBJECT_NAME = "object_name";

    // ---------- 12 个事件名（Go：⚠️ **带空格**，逐字保留）----------

    /** Go 出处：{@code get_article_logic.go:53}。 */
    public static final String EV_START_FIND_ARTICLE = "start find article";
    /** Go 出处：{@code get_article_logic.go:64}。 */
    public static final String EV_FIND_ARTICLE_SUCCESS = "find article success";
    /** Go 出处：{@code get_article_logic.go:71}。 */
    public static final String EV_START_INCR_VIEW_COUNT = "start incr view count";
    /** Go 出处：{@code get_article_logic.go:77}。 */
    public static final String EV_INCR_VIEW_COUNT_SUCCESS = "incr view count success";
    /** Go 出处：{@code get_article_logic.go:82}。 */
    public static final String EV_START_GET_MINIO_OBJECT = "start get minio object";
    /** Go 出处：{@code get_article_logic.go:93}。 */
    public static final String EV_GET_MINIO_OBJECT_SUCCESS = "get minio object success";
    /** Go 出处：{@code get_article_logic.go:95}。 */
    public static final String EV_START_READ_MINIO_OBJECT = "start read minio object";
    /** Go 出处：{@code get_article_logic.go:102}。 */
    public static final String EV_READ_MINIO_OBJECT_SUCCESS = "read minio object success";
    /** Go 出处：{@code list_articles_logic.go:62}。 */
    public static final String EV_START_DB_LIST = "start db list";
    /** Go 出处：{@code list_articles_logic.go:70}。 */
    public static final String EV_DB_LIST_SUCCESS = "db list success";
    /** Go 出处：{@code upload_file_logic.go:66}。 */
    public static final String EV_START_UPLOAD_TO_MINIO = "start upload to minio";
    /** Go 出处：{@code upload_file_logic.go:82}。 */
    public static final String EV_UPLOAD_TO_MINIO_SUCCESS = "upload to minio success";

    // ---------- 4 个 messaging.* 属性名（Go: article_consumer.go:146-149）----------

    public static final String ATTR_MESSAGING_SYSTEM = "messaging.system";
    public static final String ATTR_MESSAGING_DESTINATION = "messaging.destination";
    public static final String ATTR_MESSAGING_OPERATION = "messaging.operation";
    public static final String ATTR_MESSAGING_MESSAGE_KEY = "messaging.message.key";

    /** Go 出处：{@code article_consumer.go:146} 写死 {@code "kafka"}。 */
    public static final String MESSAGING_SYSTEM_KAFKA = "kafka";

    /** Go 出处：{@code article_consumer.go:148} 写死 {@code "consume"}。 */
    public static final String MESSAGING_OPERATION_CONSUME = "consume";

    private ArticleSpans() {
        // 纯静态工具，禁止实例化
    }

    /**
     * 在一个<b>手写 logic span</b> 里执行 {@code body}。
     *
     * <p><b>失败时<b>不</b>改 span 状态</b>（Go 的 6 个 logic 里 {@code SetStatus} 命中数 = 0 ⇒ 状态恒 UNSET）。
     * <p><b>失败时也<b>不</b>自动 {@code recordException}</b> ⇒ 记录点由业务方法<b>显式</b>调用
     * {@link #record(Span, Throwable)} 决定（Go 的 {@code RecordError} 是逐点的，见类级说明）。
     *
     * @param spanName   6 个 logic span 名之一（{@link #CREATE_ARTICLE} 等）
     * @param attributes <b>起始属性</b>（对应 Go 的 {@code trace.WithAttributes(...)}）；
     *                   Go 在 {@code Start} 之后设置的属性用 {@code span.setAttribute(...)} 逐点调用
     * @param body       业务体；拿到的 {@link Span} 就是「live span」，供逐点记录
     */
    public static <T> T traced(String spanName, Consumer<Span> attributes, Function<Span, T> body) {
        Tracer tracer = GlobalOpenTelemetry.getTracer(TRACER_NAME);
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (attributes != null) {
                attributes.accept(span);
            }
            return body.apply(span);
        } finally {
            span.end();
        }
    }

    /**
     * 等价于 Go 的 {@code span.RecordError(err)}。
     *
     * <p>🔴 <b>只加事件、不改状态</b> —— 与 otel-go v1.44.0
     * {@code sdk/trace/span.go:631-655} 一致（见类级说明）。
     *
     * @param span live span；{@code null} 时静默跳过（便于「可选 span」的调用点）
     * @param err  要记的错误；{@code null} 时静默跳过（Go 侧 {@code RecordError(nil)} 也是 no-op）
     */
    public static void record(Span span, Throwable err) {
        if (span == null || err == null) {
            return;
        }
        span.recordException(err);
    }

    /**
     * 等价于 Go 的 {@code span.AddEvent(name)} —— <b>事件名逐字保留（含空格）</b>。
     *
     * @param span live span；{@code null} 时静默跳过
     * @param name 事件名（如 {@code "start find article"}）
     */
    public static void event(Span span, String name) {
        if (span == null || name == null) {
            return;
        }
        span.addEvent(name);
    }

    /**
     * Go 侧 {@code articleMessageAttrs(topic, key)} 的等价物（{@code article_consumer.go:144-150}）。
     *
     * <p>⚠️ <b>4 个属性，不是 5 个</b>：Go 侧<b>没有</b> {@code messaging.kafka.partition}
     * （{@code HotEventConsumer} 有那一个 ⇒ 那是别的域的形状 ⇒ <b>不可照抄</b>）。
     *
     * @param destination <b>逻辑名</b>（{@link #DESTINATION_REVIEW} 或 {@link #DESTINATION_SYNC_RESULT}），
     *                     <b>不是</b> kafka topic
     * @param key        kafka 消息 key；Go 侧是 {@code string}（不会为 nil）⇒ ⇒ 这里 null 归一成空串
     */
    public static Map<String, Object> consumerAttrs(String destination, String key) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ATTR_MESSAGING_SYSTEM, MESSAGING_SYSTEM_KAFKA);
        attrs.put(ATTR_MESSAGING_DESTINATION, destination);
        attrs.put(ATTR_MESSAGING_OPERATION, MESSAGING_OPERATION_CONSUME);
        attrs.put(ATTR_MESSAGING_MESSAGE_KEY, key == null ? "" : key);
        return attrs;
    }
}
