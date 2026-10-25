package com.rag.like.like.rpc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.rag.common.communityfact.Dispatcher;
import com.rag.common.communityfact.Wire;
import com.rag.like.like.rpc.domain.LikeDomainFactOutboxEntity;
import com.rag.like.like.rpc.mapper.LikeDomainFactOutboxMapper;
import com.rag.like.like.rpc.service.LikeDeliveryValidator;
import com.rag.like.like.rpc.service.LikeFactDispatchService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.context.Context;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴🔴 四条调度契约的**端到端**守门（真 HTTP 服务器当真 DC，**不 mock**）
 * —— 对应 Go 端 {@code service/like/rpc/cmd/fact-dispatch/main.go:67-79} 与
 * {@code service/common/communityfact/process.go:41-53}。
 *
 * <h2>为什么是「端到端」而不是「测共享层」</h2>
 * <p>四条契约由**共享层** {@link Dispatcher} 承担，而**接线**在 like 域
 * ⇒ 只测共享层只能证明「共享层对」，**证不出「like 侧接对了」**。
 * ⇒ 本类用 <b>like 域自己的 {@link LikeFactDispatchService}</b> ＋ <b>真的 HTTP 服务器</b>
 * 驱动共享层 ⇒ 端到端地证明四条契约在 like 这条链路上成立。
 *
 * <table border="1">
 *   <caption>四条契约与 Go 出处</caption>
 *   <tr><th>#</th><th>契约</th><th>Go 出处</th><th>本类怎么验</th></tr>
 *   <tr><td>①</td><td>投递端点 {@code POST /v1/events}</td>
 *       <td>Go {@code cmd/fact-dispatch/main.go:56}（{@code DC_PLATFORM_EVENT_URL}）</td>
 *       <td>真 HTTP 服务器记录<b>方法 / 路径 / 头 / body</b></td></tr>
 *   <tr><td>②</td><td>节奏 <b>1s</b></td>
 *       <td>Go {@code main.go:67} {@code time.NewTicker(time.Second)}</td>
 *       <td>第 17 条与第 16 条的投递时刻差 ∈ [0.8s, 3s]</td></tr>
 *   <tr><td>③</td><td>每轮 <b>16 条</b></td>
 *       <td>Go {@code main.go:74} {@code for i := 0; i &lt; 16; i++}</td>
 *       <td>前 16 条<b>连续</b>投递完，第 17 条<b>必须等下一轮</b>（时刻差 ≥ 0.8s）</td></tr>
 *   <tr><td>④</td><td><b>单条失败即 break</b></td>
 *       <td>Go {@code main.go:75-79}</td>
 *       <td>第 1 条投递失败 ⇒ <b>同轮内第 2 条一次都不许被尝试</b></td></tr>
 * </table>
 *
 * <h2>⚠️ 为什么 ④ 要单独用「毒丸事实」验</h2>
 * <p>共享层里「失败即 break」与「投递成功即继续」是<b>同一段循环</b>里的两个分支
 * ⇒ 想证明 break 真的生效，就必须造一条<b>永远失败</b>的事实，
 * 并断言<b>排在它后面的事实在同一轮里一次都没被尝试过</b>
 * （服务端<b>没收到</b>那条的请求 ⇒ 不是「发了但失败」，是「压根没发」）。
 */
@Testcontainers
class LikeFactDispatchContractGateTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password");

    /** 本类独立账本。 */
    private static final String LEDGER = "flyway_schema_history_like_contract_gate";

    private static final Logger LOG = LoggerFactory.getLogger(LikeFactDispatchContractGateTest.class);

    private static final String PRODUCER = "rtw.like-mq";
    private static final String SCHEMA_VERSION = "rtw.community-fact.v1";
    private static final String EVENT_TYPE = "community.target.interaction";
    private static final String PAYLOAD_AGGREGATE_ID = "article/42";
    private static final String SUBJECT_REF = "rtw.identity/platform/9001";
    private static final String OCCURRED_AT = "2026-10-25T06:00:00Z";
    private static final String AVAILABLE_AT = "2026-10-25T06:00:01Z";
    /** Go 要求 {@code len(token) >= 32}（{@code main.go:58}）。 */
    private static final String TOKEN = "t".repeat(32);

    private static JdbcTemplate jdbc;
    private static LikeDomainFactOutboxMapper outboxMapper;
    private static LikeFactDispatchService deliveryService;

    private static HttpServer dcServer;
    private static final List<Recorded> received = Collections.synchronizedList(new ArrayList<>());
    /** 「毒丸」事件 id：只有它会让假 DC 回 500（null = 全部回 201）。 */
    private static volatile String poisonEventId;
    private static final AtomicReference<Dispatcher.DispatcherHandle> handleRef = new AtomicReference<>();

    @BeforeAll
    static void setUp() {
        String url = PG.getJdbcUrl() + "?TimeZone=Asia/Shanghai";
        Flyway.configure()
                .dataSource(url, PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .table(LEDGER)
                .baselineOnMigrate(true)
                .load().migrate();

        DataSource dataSource = new DriverManagerDataSource(url, PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("like-contract-gate",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(LikeDomainFactOutboxMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        outboxMapper = template.getMapper(LikeDomainFactOutboxMapper.class);

        ObjectMapper sharedMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Boolean, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
                .build();

        deliveryService = new LikeFactDispatchService(
                outboxMapper,
                new LikeDeliveryValidator(sharedMapper),
                new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void startFakeDataCenter() throws IOException {
        received.clear();
        poisonEventId = null;
        jdbc.update("DELETE FROM like_domain_fact_outbox");
        jdbc.update("DELETE FROM like_fact_stream");
        dcServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        dcServer.createContext("/v1/events", LikeFactDispatchContractGateTest::handleEvent);
        dcServer.start();
    }

    @AfterEach
    void stopEverything() {
        Dispatcher.DispatcherHandle handle = handleRef.getAndSet(null);
        if (handle != null) {
            handle.stop();
        }
        if (dcServer != null) {
            dcServer.stop(0);
            dcServer = null;
        }
    }

    // ====================================================================================
    // ① 端点契约：POST /v1/events ＋ 发的是**信封列的原始字节**
    // ====================================================================================

    @Test
    @DisplayName("🔴 契约①：投递端点 = POST /v1/events，Authorization = Bearer <token>，body = **信封列的原始字节**")
    void endpointContractIsPostV1EventsCarryingTheEnvelopeColumnBytes() throws Exception {
        insertValidFact("msg-01");
        LikeDomainFactOutboxEntity row = outboxMapper.selectByEventId(eventId("msg-01"));

        Wire.Sender sender = Wire.DCEventSender.create(endpoint(), TOKEN,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        assertThat(deliveryService.dispatchFactOnce(Context.root(), sender, LOG)).isTrue();

        assertThat(received).hasSize(1);
        Recorded rec = received.get(0);
        assertThat(rec.method).isEqualTo("POST");
        assertThat(rec.path).isEqualTo("/v1/events");
        assertThat(rec.authorization).isEqualTo("Bearer " + TOKEN);
        assertThat(rec.contentType).isEqualTo("application/json");
        // 🔴 Go: fact_delivery.go:99 `sender.Send(ctx, event, json.RawMessage(*row.DeliveryEnvelope))`
        //    ⇒ 发的是**信封列**的字节，不是「解码后再序列化」的字节
        //    ⇒ 否则 DC 算出的 input_hash 与权威读重算的 input_hash 必然不同。
        assertThat(new String(rec.body, StandardCharsets.UTF_8))
                .as("发出去的必须是出件箱行里那列信封的原始字节")
                .isEqualTo(row.getDeliveryEnvelope());
    }

    // ====================================================================================
    // ②③ 节奏 1s ＋ 每轮 16 条
    // ====================================================================================

    @Test
    @DisplayName("🔴 契约②③：每轮最多 16 条（Go main.go:74），节奏 1s（Go main.go:67）")
    void dispatcherDeliversSixteenPerRoundWithOneSecondTick() throws Exception {
        for (int i = 1; i <= 17; i++) {
            insertValidFact(String.format("msg-%02d", i));
        }
        startDispatcher();

        // 前 16 条：本轮内连续投完
        awaitTrue(() -> received.size() >= 16, Duration.ofSeconds(10));
        // 第 17 条必须等**下一轮** ⇒ 它与第 16 条之间至少隔一个 tick（≈1s）
        awaitTrue(() -> received.size() >= 17, Duration.ofSeconds(15));

        Instant sixteenth = received.get(15).at;
        Instant seventeenth = received.get(16).at;
        long gapMillis = seventhGapMillis(sixteenth, seventeenth);

        assertThat(gapMillis)
                .as("第 16 条与第 17 条之间必须隔一个 tick（Go main.go:67 的 1s）")
                .isGreaterThanOrEqualTo(800L);
        assertThat(gapMillis)
                .as("tick 不是 1s 就是漂了（Go main.go:67 time.NewTicker(time.Second)）")
                .isLessThan(5000L);
        assertThat(received).hasSizeGreaterThanOrEqualTo(17);
    }

    // ====================================================================================
    // ④ 单条失败即 break
    // ====================================================================================

    @Test
    @DisplayName("🔴 契约④：单条投递失败 ⇒ 内层批次立即 break（Go main.go:75-79）⇒ 同一轮里**只试一次**")
    void failedDeliveryBreaksTheInnerBatchImmediately() throws Exception {
        // msg-01 = 毒丸（DC 永远回 500）；msg-02 = 正常
        insertValidFact("msg-01");
        insertValidFact("msg-02");
        poisonEventId = eventId("msg-01");
        startDispatcher();

        // 🔴🔴 判别点是「**同一轮内试了几次**」，不是「后面那条有没有被试」
        //
        // Go process.go:41-53 的内层循环是
        //     for i := 0; i < 16; i++ { sent, err := dispatch(); if err != nil { …; break } … }
        // ⇒ break 打掉的是**这一轮剩下的 15 次机会** ⇒ 毒丸每 tick 只被试 1 次。
        //
        // ⚠️ 为什么「msg-02 一次都没被试」这条断言**测不出** break：
        //    毒丸失败后 delivery_status=2（仍可领）且 created_at 最老
        //    ⇒ 下一轮 claim 又拿到它 ⇒ 无论 break 在不在，msg-02 都被饿死
        //    ⇒ 那条断言对两种实现**都成立**（本次变异测试实测踩过：M14 去掉 break 后用例照样绿）。
        //
        // ⇒ 改成**固定窗口内数毒丸的请求数**：
        //    有 break ⇒ ≈ tick 数（3.5s ⇒ 4 次上下）；
        //    无 break ⇒ 每 tick 连打 16 次（3.5s ⇒ 50+ 次）。
        //    阈值 8：两种实现差着一个数量级 ⇒ 不受机器快慢影响。
        Thread.sleep(3500L);
        int poisonRequests = requestCountFor(eventId("msg-01"));
        assertThat(poisonRequests)
                .as("失败即 break ⇒ 同一轮只试一次 ⇒ 3.5s 窗口内毒丸请求数应≈tick 数（≤8）；"
                        + "去掉 break 则是每 tick 连打 16 次（3.5s ⇒ 50+ 次）")
                .isLessThanOrEqualTo(8);
        assertThat(poisonRequests)
                .as("窗口内毒丸至少被试过一次（否则下面那些断言全是空转）")
                .isGreaterThanOrEqualTo(1);

        assertThat(deliveryStatus("msg-02"))
                .as("毒丸失败后同轮 break ⇒ msg-02 不该被尝试过（应仍是 pending=0）")
                .isEqualTo(0);
        assertThat(requestCountFor(eventId("msg-02")))
                .as("msg-02 的请求一次都不许发到 DC（不是「发了失败」，是「压根没发」）")
                .isZero();
        assertThat(deliveryStatus("msg-01"))
                .as("毒丸应落 failed=2")
                .isEqualTo(2);
        assertThat(retryCount("msg-01")).isGreaterThanOrEqualTo(1);
    }

    // ====================================================================================
    // 夹具
    // ====================================================================================

    /** 真 HTTP 端的「DC」：记录请求，对毒丸回 500，其余回 201 ＋ 合法技术回执。 */
    private static void handleEvent(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String text = new String(body, StandardCharsets.UTF_8);
        received.add(new Recorded(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body,
                extractEventId(text),
                Instant.now()));

        boolean poison = poisonEventId != null && poisonEventId.equals(extractEventId(text));
        int status = poison ? 500 : 201;
        byte[] response = receiptJson(extractEventId(text)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static String receiptJson(String eventId) {
        return "{\"event_id\":\"" + eventId + "\""
                + ",\"producer\":\"" + PRODUCER + "\""
                + ",\"technical_status\":\"accepted\""
                + ",\"receipt_id\":\"rcpt-1\""
                + ",\"input_hash\":\"" + "a".repeat(64) + "\""
                + ",\"offset\":1"
                + ",\"received_at\":\"2026-10-25T06:00:09Z\"}";
    }

    private static String extractEventId(String envelopeJson) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                // ⚠️ PG 的 jsonb 文本形态是 {"key": "value"} ⇒ 冒号后**有空格** ⇒ 正则必须容空格
                .compile("\"event_id\"\\s*:\\s*\"([^\"]+)\"").matcher(envelopeJson);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static long seventhGapMillis(Instant sixteenth, Instant seventeenth) {
        return java.time.Duration.between(sixteenth, seventeenth).toMillis();
    }

    private static int requestCountFor(String eventId) {
        synchronized (received) {
            int count = 0;
            for (Recorded rec : received) {
                if (eventId.equals(rec.eventId)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static String endpoint() {
        return "http://127.0.0.1:" + dcServer.getAddress().getPort() + "/v1/events";
    }

    /** 在后台线程驱动共享层的 {@link Dispatcher}（它本身是阻塞式 ⇒ 必须放独立线程）。 */
    private static void startDispatcher() {
        Thread runner = new Thread(() -> {
            Wire.Sender sender = Wire.DCEventSender.create(endpoint(), TOKEN,
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
            Dispatcher.DispatcherHandle handle = Dispatcher.run(
                    Context.current(), deliveryService::dispatchFactOnce, sender, LOG, false, false);
            handleRef.set(handle);
        }, "like-contract-dispatcher");
        runner.setDaemon(true);
        runner.start();
    }

    /**
     * 插入一条合法事实。
     *
     * <p>⚠️ <b>每条事实用各自的 {@code aggregate_id}</b>：
     * ① 表上有 {@code (aggregate_id, fact_version)} 唯一约束 ⇒ 同一 aggregate 下同版本会撞键；
     * ② 同 aggregate 会触发<b>前序屏障</b>（低版本未 ACCEPTED ⇒ 高版本领不到）
     *    ⇒ 「单条失败即 break」那条用例就会<b>因为屏障</b>而不是<b>因为 break</b>而通过 ⇒ 测错了东西。
     */
    private static void insertValidFact(String msgId) {
        String aggregateId = "like-state/9001/article/" + msgId;
        String payload = payloadJson(msgId);
        String envelope = envelopeJson(msgId, aggregateId, payload, 1L);
        outboxMapper.insertFact(eventId(msgId), EVENT_TYPE, payload, aggregateId, 1L, envelope);
    }

    private static String eventId(String msgId) {
        return "rtw.like." + msgId;
    }

    private static String payloadJson(String msgId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schema_version\":\"").append(SCHEMA_VERSION).append("\"");
        sb.append(",\"event_id\":\"").append(eventId(msgId)).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(PAYLOAD_AGGREGATE_ID).append("\"");
        sb.append(",\"aggregate_version\":null");
        sb.append(",\"operation_id\":\"").append(msgId).append("\"");
        sb.append(",\"subject_ref\":\"").append(SUBJECT_REF).append("\"");
        sb.append(",\"target_type\":\"article\"");
        sb.append(",\"target_id\":\"42\"");
        sb.append(",\"target_revision\":null");
        sb.append(",\"revision_status\":\"unknown\"");
        sb.append(",\"operation\":\"like\"");
        sb.append(",\"source_ref\":\"").append(eventId(msgId)).append("\"");
        sb.append(",\"old_state\":0");
        sb.append(",\"new_state\":1");
        sb.append(",\"event_time\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"available_at\":\"").append(AVAILABLE_AT).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String envelopeJson(String msgId, String aggregateId, String payload, long aggregateVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event_id\":\"").append(eventId(msgId)).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"schema_version\":1");
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(aggregateId).append("\"");
        sb.append(",\"aggregate_version\":").append(aggregateVersion);
        sb.append(",\"operation_id\":\"").append(msgId).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"payload\":").append(payload);
        sb.append("}");
        return sb.toString();
    }

    private static int deliveryStatus(String msgId) {
        Integer status = jdbc.queryForObject(
                "SELECT delivery_status FROM like_domain_fact_outbox WHERE event_id = ?",
                Integer.class, eventId(msgId));
        return status == null ? -1 : status;
    }

    private static int retryCount(String msgId) {
        Integer count = jdbc.queryForObject(
                "SELECT retry_count FROM like_domain_fact_outbox WHERE event_id = ?",
                Integer.class, eventId(msgId));
        return count == null ? -1 : count;
    }

    private static void awaitTrue(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        // 🔴 超时必须**炸**，不能静默返回。
        //    否则「等条件成立」退化成「等到了就等、等不到也无所谓」
        //    ⇒ 后面那些依赖该条件的断言会在条件根本没发生时**空转通过**
        //    （本轮实测踩过：④ 用例的 awaitTrue 超时后不报错，
        //      而「msg-02 请求数 == 0」在什么都没发生的情况下**恒真**）。
        throw new AssertionError("条件在 " + timeout.toMillis() + "ms 内未成立");
    }

    /** 一次被 DC 收到的请求。 */
    private static final class Recorded {

        final String method;
        final String path;
        final String authorization;
        final String contentType;
        final byte[] body;
        final String eventId;
        final Instant at;

        Recorded(String method, String path, String authorization, String contentType,
                 byte[] body, String eventId, Instant at) {
            this.method = method;
            this.path = path;
            this.authorization = authorization;
            this.contentType = contentType;
            this.body = body;
            this.eventId = eventId;
            this.at = at;
        }
    }
}
