package com.rag.message.message.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * message-rpc 业务指标 —— 对应 Go 端
 * {@code service/message/rpc/internal/metrics/metrics.go} 的 prometheus 注册器：
 *
 * <pre>
 * RPCRequestCounter          "message_rpc_request_total"                labels=[method, result]
 * RPCRequestDuration         "message_rpc_request_duration_seconds"     labels=[method]
 * NotificationActionCounter  "message_rpc_notification_action_total"    labels=[kind, result]
 * ChatActionCounter          "message_rpc_chat_action_total"            labels=[action, result]
 * DBErrorCounter             "message_rpc_db_error_total"               labels=[action, type]
 * </pre>
 *
 * <p>Spring Boot 项目用 Micrometer 代替 prometheus client_golang，命名一一对应：
 * 1:1 复刻 Prometheus 标签与 metrics 名，迁移完成后 Prometheus 抓取端不需要改配置。
 *
 * <p>指标语义保持：
 * <ul>
 *   <li>{@code result="success"} / {@code "fail"} 与 Go 一致</li>
 *   <li>{@code kind} 在 notification_action 里枚举：枚举值来自 {@code pb.NotificationKind}
 *       的小写名（{@code admin_broadcast} / {@code admin_notice} / {@code article_comment} /
 *       {@code comment_reply} / {@code article_published} / {@code article_liked}）</li>
 *   <li>{@code action} 在 chat_action 里是 {@code "send"}</li>
 *   <li>{@code type} 在 db_error 里是 SQL 类型短名：
 *       {@code broadcast_user_list} / {@code insert} / {@code list} / {@code mark_read} /
 *       {@code mark_all_read} / {@code notification} / {@code conversation} /
 *       {@code find} / {@code list_messages} / {@code count_unread} /
 *       {@code find_conversation} / {@code find_by_pair} / {@code create_conversation} /
 *       {@code create_pending_conversation} / {@code append_message}</li>
 * </ul>
 */
@Component
public class MessageMetrics {

    /** 对应 Go {@code metric: message_rpc_request_total labels: [method, result]}。 */
    private static final String RPC_REQUEST_TOTAL = "message_rpc_request_total";

    /** 对应 Go {@code metric: message_rpc_request_duration_seconds labels: [method]}。 */
    private static final String RPC_REQUEST_DURATION = "message_rpc_request_duration_seconds";

    /** 对应 Go {@code metric: message_rpc_notification_action_total labels: [kind, result]}。 */
    private static final String NOTIFICATION_ACTION_TOTAL = "message_rpc_notification_action_total";

    /** 对应 Go {@code metric: message_rpc_chat_action_total labels: [action, result]}。 */
    private static final String CHAT_ACTION_TOTAL = "message_rpc_chat_action_total";

    /** 对应 Go {@code metric: message_rpc_db_error_total labels: [action, type]}。 */
    private static final String DB_ERROR_TOTAL = "message_rpc_db_error_total";

    private final MeterRegistry registry;

    /** 缓存已注册的 timer（按 method name 缓存），避免每次 observeRPC 都重新 query。 */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    public MessageMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 对应 Go 端 {@code ObserveRPC(method, started, err)}：
     * <ul>
     *   <li>Counter +1（method + result 标签）</li>
     *   <li>Timer 记录耗时（method 标签）</li>
     * </ul>
     *
     * <p>{@code err == null} → {@code result="success"}，否则 {@code result="fail"}。
     */
    public void observeRpc(String method, long startedAtNanos, Throwable err) {
        String resultLabel = (err == null) ? "success" : "fail";
        Counter.builder(RPC_REQUEST_TOTAL)
                .description("Total number of message rpc requests")
                .tag("method", method)
                .tag("result", resultLabel)
                .register(registry)
                .increment();

        Timer timer = timerCache.computeIfAbsent(method, m ->
                Timer.builder(RPC_REQUEST_DURATION)
                        .description("Message rpc request duration in seconds")
                        .tag("method", m)
                        .publishPercentileHistogram()
                        .register(registry));
        timer.record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    /**
     * 对应 Go 端 {@code ObserveNotification(kind, err)}：
     * 用于 {@code SendNotification} 写操作的 success/fail 计数。
     *
     * <p>{@code kind} 是 {@code pb.NotificationKind} 的 {@code String()} 小写形式。
     */
    public void observeNotification(String kind, Throwable err) {
        String resultLabel = (err == null) ? "success" : "fail";
        Counter.builder(NOTIFICATION_ACTION_TOTAL)
                .description("Total number of notification actions")
                .tag("kind", kind)
                .tag("result", resultLabel)
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go 端 {@code ObserveChat(action, err)}：
     * 用于 {@code SendChatMessage} 写操作的 success/fail 计数。
     *
     * <p>{@code action} 是语义分类；Go 端 chat_action 只用 {@code "send"}。
     */
    public void observeChat(String action, Throwable err) {
        String resultLabel = (err == null) ? "success" : "fail";
        Counter.builder(CHAT_ACTION_TOTAL)
                .description("Total number of chat actions")
                .tag("action", action)
                .tag("result", resultLabel)
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go 端 {@code ObserveDBError(action, type)}：数据库错误计数。
     */
    public void observeDbError(String action, String type) {
        Counter.builder(DB_ERROR_TOTAL)
                .description("Total number of message rpc database errors")
                .tag("action", action)
                .tag("type", type)
                .register(registry)
                .increment();
    }
}