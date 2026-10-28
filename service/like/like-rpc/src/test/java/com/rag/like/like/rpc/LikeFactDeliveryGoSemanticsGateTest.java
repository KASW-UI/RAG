package com.rag.like.like.rpc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.rag.common.communityfact.Wire;
import com.rag.like.like.rpc.domain.LikeDomainFactOutboxEntity;
import com.rag.like.like.rpc.mapper.LikeDomainFactOutboxMapper;
import com.rag.like.like.rpc.service.LikeDeliveryValidator;
import com.rag.like.like.rpc.service.LikeFactDispatchService;
import io.opentelemetry.context.Context;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.rag.like.like.rpc.support.RealContainerPort;

/**
 * 🔴 真 PostgreSQL + 真 MyBatis + 真 Spring 事务上的 Go↔RAG 语义守门测试
 * —— 对应 Go {@code service/like/rpc/internal/model/fact_delivery.go} 的
 * {@code DispatchFactOnce}（{@code :60-127}）与 {@code validLikeDelivery}（{@code :22-58}）。
 *
 * <h2>为什么必须是「真 PG + 真 MyBatis + 真事务」</h2>
 * <p>本类的本质是<b>事务边界</b>：状态更新与异常抛出的先后关系。
 * 用 mock 数据层测不到任何东西（mock 的 mapper 不参与事务，UPDATE「永远成功」）。
 * 用 H2 也测不到：{@code SELECT … FOR UPDATE SKIP LOCKED}、jsonb、
 * {@code INSERT … ON CONFLICT … RETURNING} 在 H2 上不是同一套语义。
 * ⇒ 必须是真 PG。
 * <p>而且必须是<b>真 MyBatis + 真 DataSourceTransactionManager</b>：
 * 本类手工搭 {@link SqlSessionTemplate}（{@code SpringManagedTransactionFactory}）
 * ⇒ mapper 的 SQL 与 {@link LikeFactDispatchService} 的事务跑在<b>同一条连接</b>上，与生产同构。
 * 若用 {@code SqlSessionFactory.openSession()} 直接开会话，mapper 会拿到<b>另一条连接</b>
 * ⇒ 事务形同虚设 ⇒ 测试必然假绿。
 *
 * <h2>🔴 本类同时是「like 域不得照抄 comment 域」的守门</h2>
 * <p>comment 域有一份同款实现（{@code CommentRpcGoSemanticsGateTest}），
 * 但<b>两域的 Go 判据不同</b>：comment 的载荷带 {@code comment_id} 且要求
 * 「载荷 {@code comment_id} == 信封 {@code aggregate_id}」；
 * <b>like 没有这条判据</b> —— like 的载荷 {@code aggregate_id}（= {@code targetType/targetID}）
 * 与信封 {@code aggregate_id}（= {@code like-state/{user}/{type}/{id}}）<b>本来就不相等</b>
 * （Go {@code domain_fact.go:91-96}）⇒ 照抄 comment 那份判据会把<b>合法事实判成 BLOCKED</b>。
 * ⇒ 本类的第 ② 条用例专门钉住这个差异。
 *
 * <h2>为什么不用 {@code @SpringBootTest}</h2>
 * <p>只验「投递链路的语义」，不需要 Redis / Kafka / Nacos ⇒ 走 Spring 上下文就变成「全栈启动测试」，
 * 且掩盖了本类真正要验的东西。
 */
@Testcontainers
class LikeFactDeliveryGoSemanticsGateTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password");
/**
     * 本类的真容器**固定宿主口**（like 域段 22790–22819 内 · 全局段表 §4.10.19.3 / §4.10.39.2 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 **OS 临时段 49152–65535**
     * （= Docker published 段）⇒ ⇒ 与 {@code tools/difftest/ddl-parity.sh} 的固定口 {@code 55560–55569}
     * 同属一个池子 ⇒ ⇒ 全局段表把该段标为**禁占**。
     *
     * <p>🔴 为什么端口写死在本类里、而不是轮询分配：Testcontainers 的 surefire **每个 fork 一个独立 JVM**
     * ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一个端口 ⇒ ⇒ 撞车。
     */
    private static final int FIXED_HOST_PORT = 22794;
    static {
        RealContainerPort.withFixedHostPort(PG, FIXED_HOST_PORT,
                RealContainerPort.PG_CONTAINER_PORT);
    }

    /** 本类独立账本（不与其他门禁的账本撞号）。 */
    private static final String LEDGER = "flyway_schema_history_like_fact_gate";

    private static final Logger LOG = LoggerFactory.getLogger(LikeFactDeliveryGoSemanticsGateTest.class);

    /** Go {@code fact_delivery.go:17} 的 {@code const likeFactProducer = "rtw.like-mq"}。 */
    private static final String PRODUCER = "rtw.like-mq";
    /** Go {@code domain_fact.go:90} 的 {@code SchemaVersion: "rtw.community-fact.v1"}。 */
    private static final String SCHEMA_VERSION = "rtw.community-fact.v1";
    /** Go {@code domain_fact.go:91} 的 {@code EventType: "community.target.interaction"}。 */
    private static final String EVENT_TYPE = "community.target.interaction";

    /** Go 的载荷 {@code aggregate_id} = {@code targetType + "/" + targetID}。 */
    private static final String PAYLOAD_AGGREGATE_ID = "article/42";
    /** Go 的信封 {@code aggregate_id} = {@code like-state/{userID}/{targetType}/{targetID}}（streamID）。 */
    private static final String ENVELOPE_AGGREGATE_ID = "like-state/9001/article/42";
    private static final String SUBJECT_REF = "rtw.identity/platform/9001";
    private static final String MSG_ID = "msg-1";

    private static final String OCCURRED_AT = "2026-10-25T06:00:00Z";
    private static final String AVAILABLE_AT = "2026-10-25T06:00:01Z";

    private static JdbcTemplate jdbc;
    private static LikeDomainFactOutboxMapper outboxMapper;
    private static LikeFactDispatchService deliveryService;

    @BeforeAll
    static void setUp() {
        String url = PG.getJdbcUrl() + "?TimeZone=Asia/Shanghai";
        Flyway flyway = Flyway.configure()
                .dataSource(url, PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .table(LEDGER)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        DataSource dataSource = new DriverManagerDataSource(url, PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        // --- 真 MyBatis：与生产同构的「mapUnderscoreToCamelCase」+ Spring 事务感知的会话模板
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("like-fact-gate",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(LikeDomainFactOutboxMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        outboxMapper = template.getMapper(LikeDomainFactOutboxMapper.class);

        // 生产里注入的是 Jackson2ObjectMapperConfiguration 那个共享 Bean ⇒ 这里照抄它的配置。
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

    @AfterEach
    void clean() {
        // 取件查询是「任意一条可投递行」⇒ 必须清干净，否则下一个用例会领到上一条。
        jdbc.update("DELETE FROM like_domain_fact_outbox");
        jdbc.update("DELETE FROM like_fact_stream");
    }

    // ====================================================================================
    // ① 正向对照组：合法冻结信封必须被正常投递并落技术回执
    //    （没有这一条，后面的「应当 BLOCKED」可能是因为夹具本身坏了而假绿）
    // ====================================================================================

    @Test
    @DisplayName("🔴 正向对照：合法冻结信封 ⇒ 投递成功 + 技术元数据落盘")
    void validFrozenEnvelopeIsDeliveredAndAccepted() throws Exception {
        insertValidFact();

        boolean sent = deliveryService.dispatchFactOnce(Context.root(), acceptedSender("2026-10-25T06:00:09Z"), LOG);

        assertThat(sent).isTrue();
        assertThat(deliveryStatus()).isEqualTo(1);      // DeliveryAccepted
        assertThat(jdbc.queryForObject(
                "SELECT technical_receipt_id FROM like_domain_fact_outbox WHERE event_id = ?",
                String.class, eventId())).isEqualTo("rcpt-1");
        assertThat(jdbc.queryForObject(
                "SELECT technical_offset FROM like_domain_fact_outbox WHERE event_id = ?",
                Long.class, eventId())).isEqualTo(42L);
    }

    // ====================================================================================
    // ② 🔴 like 专属：载荷 aggregate_id ≠ 信封 aggregate_id（Go 本来就允许）⇒ 仍须投递成功
    //    这是「like 不得照抄 comment 判据」的守门：comment 要求载荷 comment_id == 信封 aggregate_id，
    //    like 若照抄 ⇒ 合法事实会被判成 BLOCKED（功能自毁）。
    // ====================================================================================

    @Test
    @DisplayName("🔴 like 专属：载荷 aggregate_id ≠ 信封 aggregate_id ⇒ **仍然合法**（Go 的 like 判据没有这一条）")
    void payloadAggregateIdMayDifferFromEnvelopeAggregateId() throws Exception {
        assertThat(PAYLOAD_AGGREGATE_ID).isNotEqualTo(ENVELOPE_AGGREGATE_ID);
        insertValidFact();

        boolean sent = deliveryService.dispatchFactOnce(Context.root(), acceptedSender("2026-10-25T06:00:09Z"), LOG);

        assertThat(sent).isTrue();
        assertThat(deliveryStatus()).isEqualTo(1);
    }

    // ====================================================================================
    // ③ 冻结信封非法（producer 不匹配）⇒ BLOCKED 必须落盘
    // ====================================================================================

    @Test
    @DisplayName("🔴 信封非法（producer 不匹配）⇒ delivery_status=3 落盘（抛错也必须落盘）")
    void blockedFrozenEnvelopeIsPersistedEvenThoughTheCallThrows() {
        String payload = payloadJson(OCCURRED_AT, AVAILABLE_AT, null, null);
        String envelope = envelopeJson("1", payload)
                .replace("\"producer\":\"" + PRODUCER + "\"", "\"producer\":\"rtw.someone-else\"");
        insertFact(payload, envelope);

        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        // 🔴 这一行才是本测试的全部意义：Go 在 fact_delivery.go:78-88 是「UPDATE 后 Commit」。
        assertThat(deliveryStatus()).isEqualTo(3);
    }

    // ====================================================================================
    // ④ 投递失败 ⇒ DeliveryFailed + retry_count+1 必须落盘
    // ====================================================================================

    @Test
    @DisplayName("🔴 投递失败 ⇒ delivery_status=2 且 retry_count=1 落盘（原始错误必须带出来）")
    void failedDeliveryIsPersistedWithRetryCountEvenThoughTheCallThrows() {
        insertValidFact();

        IllegalStateException cause = new IllegalStateException("DC endpoint refused the connection");
        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), senderFailingWith(cause), LOG))
                .isInstanceOf(LikeFactDispatchService.DeliveryDeferredException.class)
                // Go 的 fact_delivery.go:109 是 `return false, sendErr` ⇒ 原始错误必须带出来
                .hasCause(cause);

        assertThat(deliveryStatus()).isEqualTo(2);
        assertThat(retryCount()).isEqualTo(1);
    }

    // ====================================================================================
    // ⑤ technical_received_at 必须是 DC 回执的 received_at（≠ 本地投递时刻）
    // ====================================================================================

    @Test
    @DisplayName("🔴 technical_received_at = DC 回执的 received_at；delivered_at = 本地投递时刻（Go 的应用时钟）")
    void technicalReceivedAtComesFromTheDataCenterReceipt() throws Exception {
        insertValidFact();
        // 一个刻意很早的时刻 ⇒ 若实现仍写本地 now，这一列就会与它相差数年
        String dcReceivedAt = "2020-01-02T03:04:05Z";

        deliveryService.dispatchFactOnce(Context.root(), acceptedSender(dcReceivedAt), LOG);

        Instant received = technicalReceivedAt();
        Instant delivered = deliveredAt();
        assertThat(received).isEqualTo(Instant.parse(dcReceivedAt));
        // delivered_at 是本地投递时刻 ⇒ 两列必须是两个不同的值
        assertThat(delivered).isNotEqualTo(received);
        assertThat(delivered).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
    }

    // ====================================================================================
    // ⑤' delivered_at 必须是「投递之后」的时刻（Go 的应用时钟在 send 之后取）
    // ====================================================================================

    @Test
    @DisplayName("🔴 delivered_at 取的是**投递之后**的应用时钟（Go fact_delivery.go:111 在 send 之后取 now()）")
    void deliveredAtIsTakenAfterTheSendNotAtTransactionStart() throws Exception {
        insertValidFact();
        Instant sendStarted = Instant.now();

        // 一个会阻塞 2 秒的 sender：把「投递耗时」拉出来
        Wire.Sender slowSender = (ctx, event, raw) -> {
            Thread.sleep(2000);
            return acceptedSender("2026-10-25T06:00:09Z").send(ctx, event, raw);
        };

        deliveryService.dispatchFactOnce(Context.root(), slowSender, LOG);

        Instant delivered = deliveredAt();
        // 🔴 若 delivered_at 取的是**数据库**的 now()，它就是 PG 的 transaction_timestamp()
        //    （= 事务开始时刻，早于 send）⇒ 会落在 sendStarted 附近 ⇒ 这里必然红。
        //    Go 是**应用时钟**且在 send 之后取（fact_delivery.go:111，send 在 :92）
        //    ⇒ 必须明显晚于 send 的开始时刻。
        assertThat(delivered).isAfter(sendStarted.plusSeconds(1));
    }

    // ====================================================================================
    // ⑥ 载荷多一个未知字段 ⇒ BLOCKED（Go 的 DisallowUnknownFields）
    // ====================================================================================

    @Test
    @DisplayName("🔴 载荷多一个未知字段 ⇒ BLOCKED（Go 的 DisallowUnknownFields）")
    void payloadWithUnknownFieldIsBlocked() {
        String payload = payloadJson(OCCURRED_AT, AVAILABLE_AT, "tampered_extra_field", null);
        insertFact(payload, envelopeJson("1", payload));

        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus()).isEqualTo(3);
    }

    // ====================================================================================
    // ⑦⑧ 载荷的三个时间字段必须是 RFC3339（Go 的 time.Time 解码要求）
    // ====================================================================================

    @Test
    @DisplayName("🔴 载荷 event_time 非 RFC3339 ⇒ BLOCKED")
    void payloadWithMalformedEventTimeIsBlocked() {
        // Go 侧 likeFact.EventTime 是 time.Time（domain_fact.go:71）
        // ⇒ encoding/json 解码非 RFC3339 直接报错 ⇒ StrictDecode 失败 ⇒ BLOCKED
        String payload = payloadJson("not-a-timestamp", AVAILABLE_AT, null, null);
        insertFact(payload, envelopeJson("1", payload));

        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus()).isEqualTo(3);
    }

    @Test
    @DisplayName("🔴 载荷 available_at 非 RFC3339 ⇒ BLOCKED")
    void payloadWithMalformedAvailableAtIsBlocked() {
        String payload = payloadJson(OCCURRED_AT, "2026-10-25 06:00:01", null, null);
        insertFact(payload, envelopeJson("1", payload));

        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus()).isEqualTo(3);
    }

    // ====================================================================================
    // ⑨⑩ 数值字段被字符串化 ⇒ BLOCKED（Go 的 encoding/json 报错）
    // ====================================================================================

    @Test
    @DisplayName("🔴 信封 schema_version 被字符串化 ⇒ BLOCKED")
    void envelopeWithStringifiedSchemaVersionIsBlocked() {
        String payload = payloadJson(OCCURRED_AT, AVAILABLE_AT, null, null);
        insertFact(payload, envelopeJson("\"1\"", payload));

        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus()).isEqualTo(3);
    }

    @Test
    @DisplayName("🔴 载荷的字符串字段被数字化 ⇒ BLOCKED（Go 的 string 字段不收数字）")
    void payloadWithNumericStringFieldIsBlocked() {
        String payload = payloadJson(OCCURRED_AT, AVAILABLE_AT, null, null)
                .replace("\"subject_ref\":\"" + SUBJECT_REF + "\"", "\"subject_ref\":9001");
        insertFact(payload, envelopeJson("1", payload));

        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus()).isEqualTo(3);
    }

    // ====================================================================================
    // ⑪ like 专属：载荷 aggregate_version 必须为 null（Go 的 `payload.AggregateVersion != nil`）
    // ====================================================================================

    @Test
    @DisplayName("🔴 like 专属：载荷 aggregate_version 非 null ⇒ BLOCKED（版本号的唯一权威在信封）")
    void payloadWithAggregateVersionIsBlocked() {
        String payload = payloadJson(OCCURRED_AT, AVAILABLE_AT, null, "7");
        insertFact(payload, envelopeJson("1", payload));

        assertThatThrownBy(() -> deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus()).isEqualTo(3);
    }

    // ====================================================================================
    // ⑫ 队列空 ⇒ 返回 false 且**不抛错**（Go 的 ErrRecordNotFound ⇒ (false, nil)）
    // ====================================================================================

    @Test
    @DisplayName("✅ outbox 为空 ⇒ 返回 false 且不抛错（队列空不是错误）")
    void emptyOutboxReturnsFalseWithoutError() throws Exception {
        boolean sent = deliveryService.dispatchFactOnce(Context.root(), mustNotBeCalled(), LOG);
        assertThat(sent).isFalse();
    }

    // ====================================================================================
    // ⑬ 前序屏障：同 aggregate 的高版本事实必须等低版本先被 ACCEPTED
    //     （Go fact_delivery.go:65-68 的 NOT EXISTS 子句）
    // ====================================================================================

    @Test
    @DisplayName("🔴 前序屏障：同 aggregate_id 的版本 2 在版本 1 未 ACCEPTED 前**领不到**")
    void laterVersionIsBlockedByUnacceptedPredecessor() throws Exception {
        // 版本 1：合法
        String payload1 = payloadJson("msg-1", OCCURRED_AT, AVAILABLE_AT, null, null);
        insertFactWithVersion("msg-1", payload1, envelopeJson("msg-1", "1", payload1, 1L), 1L);
        // 版本 2：同样合法，但它的前序（版本 1）还没被接受
        // ⚠️ event_id 是**主键** ⇒ 两条事实必须用不同 msgId（Go：eventID = "rtw.like." + msgId）
        // ⚠️ 信封的 aggregate_version 必须跟着 fact_version 走（Go domain_fact.go:127/:135 同一个 stream.Version）
        String payload2 = payloadJson("msg-2", OCCURRED_AT, AVAILABLE_AT, null, null);
        insertFactWithVersion("msg-2", payload2, envelopeJson("msg-2", "1", payload2, 2L), 2L);

        // 🔴🔴 把版本 2 的 created_at **提前**（比版本 1 早 1 分钟）
        //    ⇒ 取件排序（ORDER BY created_at, event_id）**先看到版本 2**
        //    ⇒ 此时**只有前序屏障**能挡住它。
        //
        //    ⚠️ 为什么必须这么拧一下（本轮变异测试实测踩过：M9 去掉 NOT EXISTS 后用例照样绿）：
        //    两条事实是**先后插入**的 ⇒ 版本 1 的 created_at 天然更早
        //    ⇒ 排序本来就先看到版本 1 ⇒ 拿掉屏障结果一模一样 ⇒ **用例是空转的**
        //    ⇒ 守门形同虚设。
        //    现实场景也支持这个拧法：人工迁移 / 时钟漂移都可能让高版本事实**先于**低版本落库，
        //    这时「按版本序投递」就**只能**靠前序屏障保证，不能靠 created_at 排序。
        jdbc.update("UPDATE like_domain_fact_outbox SET created_at = now() - interval '1 minute' "
                + "WHERE event_id = ?", eventId("msg-2"));

        // 夹具自检：排序必须**真的**先看到版本 2（否则下面那条断言证明不了屏障在起作用）
        assertThat(firstByClaimOrder())
                .as("夹具自检：created_at 调整后，取件排序必须先看到版本 2，"
                        + "否则本用例测的是排序而不是前序屏障")
                .isEqualTo(eventId("msg-2"));

        // 先领一次 ⇒ 只能领到版本 1（版本 2 被前序屏障挡住）
        boolean first = deliveryService.dispatchFactOnce(Context.root(), acceptedSender("2026-10-25T06:00:09Z"), LOG);
        assertThat(first).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT fact_version FROM like_domain_fact_outbox WHERE delivery_status = 1",
                Long.class)).isEqualTo(1L);

        // 版本 1 已 ACCEPTED ⇒ 版本 2 现在可领
        boolean second = deliveryService.dispatchFactOnce(Context.root(), acceptedSender("2026-10-25T06:00:09Z"), LOG);
        assertThat(second).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT fact_version FROM like_domain_fact_outbox WHERE delivery_status = 1 AND fact_version = 2",
                Long.class)).isEqualTo(2L);
    }

    // ====================================================================================
    // 夹具
    // ====================================================================================

    private static String eventId() {
        return eventId(MSG_ID);
    }

    private static String eventId(String msgId) {
        return "rtw.like." + msgId;
    }

    /**
     * 按 Go 的 {@code likeFact}（{@code domain_fact.go:54-74}）构造载荷 JSON —— <b>19 个字段</b>。
     *
     * @param extraField 额外注入的未知字段名（测 Go 的 DisallowUnknownFields）
     * @param aggregateVersion {@code aggregate_version} 的字面量（{@code null} 表示 Go 的 nil）
     */
    private static String payloadJson(String eventTime, String availableAt,
                                       String extraField, String aggregateVersion) {
        return payloadJson(MSG_ID, eventTime, availableAt, extraField, aggregateVersion);
    }

    /**
     * @param msgId 事实的 msgId（Go {@code domain_fact.go:84}：{@code eventID = "rtw.like." + msgId}）
     *               ⚠️ {@code event_id} 是<b>主键</b> ⇒ 同一条流上的不同版本必须用不同 msgId
     */
    private static String payloadJson(String msgId, String eventTime, String availableAt,
                                       String extraField, String aggregateVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schema_version\":\"").append(SCHEMA_VERSION).append("\"");
        sb.append(",\"event_id\":\"").append(eventId(msgId)).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(PAYLOAD_AGGREGATE_ID).append("\"");
        sb.append(",\"aggregate_version\":").append(aggregateVersion);
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
        sb.append(",\"event_time\":\"").append(eventTime).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"available_at\":\"").append(availableAt).append("\"");
        if (extraField != null) {
            sb.append(",\"").append(extraField).append("\":\"injected\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 按 Go 的 {@code likeDeliveryEnvelope}（{@code domain_fact.go:97-104}）构造信封 JSON。
     *
     * @param schemaVersion {@code schema_version} 的<b>字面量</b>（用于注入字符串化的数值）
     */
    private static String envelopeJson(String schemaVersion, String payload) {
        return envelopeJson(MSG_ID, schemaVersion, payload, 1L);
    }

    /**
     * 🔴 {@code aggregateVersion} <b>必须等于该行的 {@code fact_version}</b>：
     * <pre>
     * Go: domain_fact.go:124-129  AggregateVersion: stream.Version   （信封）
     * Go: domain_fact.go:133-136  FactVersion:     &amp;stream.Version （行）
     * Go: fact_delivery.go:27     event.AggregateVersion != *row.FactVersion ⇒ BLOCKED
     * </pre>
     * ⇒ 两者取<b>同一个</b> {@code stream.Version}，不是两个独立来源。
     */
    private static String envelopeJson(String msgId, String schemaVersion, String payload, long aggregateVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event_id\":\"").append(eventId(msgId)).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"schema_version\":").append(schemaVersion);
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(ENVELOPE_AGGREGATE_ID).append("\"");
        sb.append(",\"aggregate_version\":").append(aggregateVersion);
        sb.append(",\"operation_id\":\"").append(msgId).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"payload\":").append(payload);
        sb.append("}");
        return sb.toString();
    }

    private static void insertValidFact() {
        String payload = payloadJson(OCCURRED_AT, AVAILABLE_AT, null, null);
        insertFact(payload, envelopeJson("1", payload));
    }

    private static void insertFact(String payload, String envelope) {
        insertFactWithVersion(MSG_ID, payload, envelope, 1L);
    }

    private static void insertFactWithVersion(String msgId, String payload, String envelope, long factVersion) {
        // 🔴 夹具自检：信封的 aggregate_version 必须等于行的 fact_version。
        //    Go: domain_fact.go:127（信封）与 :135（行）取的是**同一个** stream.Version；
        //    fact_delivery.go:27 会因两者不等而判 BLOCKED。
        //    ⇒ 没有这条自检，夹具一旦写死版本号，看到的就是「合法事实被判非法」，
        //    第一反应会误判成实现有 bug（本次就踩过这个坑）。
        assertThat(envelopeAggregateVersion(envelope)).isEqualTo(factVersion);
        // 走**生产同款**的写路径（mapper 里的 ::jsonb 显式转换），而不是另写一份 INSERT。
        outboxMapper.insertFact(eventId(msgId), EVENT_TYPE, payload, ENVELOPE_AGGREGATE_ID, factVersion, envelope);
    }

    /** 取信封里的 {@code aggregate_version}（夹具自检用）。 */
    private static long envelopeAggregateVersion(String envelope) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"aggregate_version\":(-?\\d+)")
                .matcher(envelope);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    /**
     * 取件排序（{@code ORDER BY created_at, event_id}）下的第一条 event_id。
     *
     * <p>⚠️ <b>只用于夹具自检</b>，不参与被测逻辑 ——
     * 它回答的是「如果把前序屏障拿掉，调度器**会先领到谁**」，
     * 用来证明本用例真的在测屏障，而不是在测排序。
     */
    private static String firstByClaimOrder() {
        return jdbc.queryForObject(
                "SELECT event_id FROM like_domain_fact_outbox ORDER BY created_at, event_id LIMIT 1",
                String.class);
    }

    private static int deliveryStatus() {
        return jdbc.queryForObject(
                "SELECT delivery_status FROM like_domain_fact_outbox WHERE event_id = ?",
                Integer.class, eventId());
    }

    private static int retryCount() {
        return jdbc.queryForObject(
                "SELECT retry_count FROM like_domain_fact_outbox WHERE event_id = ?",
                Integer.class, eventId());
    }

    private static Instant technicalReceivedAt() {
        return jdbc.queryForObject(
                "SELECT technical_received_at FROM like_domain_fact_outbox WHERE event_id = ?",
                OffsetDateTime.class, eventId()).toInstant();
    }

    private static Instant deliveredAt() {
        return jdbc.queryForObject(
                "SELECT delivered_at FROM like_domain_fact_outbox WHERE event_id = ?",
                OffsetDateTime.class, eventId()).toInstant();
    }

    private static Wire.Sender acceptedSender(String receivedAt) {
        return (ctx, event, raw) -> {
            Wire.TechnicalReceipt receipt = new Wire.TechnicalReceipt();
            receipt.eventId = event.eventId;
            receipt.producer = event.producer;
            receipt.technicalStatus = "accepted";
            receipt.receiptId = "rcpt-1";
            receipt.inputHash = "a".repeat(64);
            receipt.offset = 42L;
            receipt.receivedAt = receivedAt;
            return receipt;
        };
    }

    private static Wire.Sender senderFailingWith(Exception cause) {
        return (ctx, event, raw) -> {
            throw cause;
        };
    }

    private static Wire.Sender mustNotBeCalled() {
        return (ctx, event, raw) -> {
            throw new AssertionError("sender must not be called: the frozen envelope should have been blocked");
        };
    }
}
