package com.rag.comment.comment.rpc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.rag.common.communityfact.Wire;
import com.rag.comment.comment.rpc.domain.CommentContentEntity;
import com.rag.comment.comment.rpc.domain.CommentIndexEntity;
import com.rag.comment.comment.rpc.mapper.CommentContentMapper;
import com.rag.comment.comment.rpc.mapper.CommentDomainFactOutboxMapper;
import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.comment.comment.rpc.service.CommentDeliveryValidator;
import com.rag.comment.comment.rpc.service.CommentFactDeliveryService;
import com.rag.comment.comment.rpc.support.RealContainerHostPort;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
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
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 真 PostgreSQL + 真 MyBatis + 真 Spring 事务上的 Go↔RAG 语义守门测试。
 *
 * <h2>覆盖的 7 条漂移</h2>
 * <table border="1">
 *   <caption>本类覆盖的 DRIFT 台账条目</caption>
 *   <tr><th>条目</th><th>守门断言</th></tr>
 *   <tr><td>MISMATCH-01</td><td>冻结信封非法 ⇒ {@code delivery_status=3} <b>落盘</b>（尽管方法抛错）</td></tr>
 *   <tr><td>MISMATCH-02</td><td>投递失败 ⇒ {@code delivery_status=2} 且 {@code retry_count=1} <b>落盘</b></td></tr>
 *   <tr><td>MISMATCH-03</td><td>{@code technical_received_at} = DC 回执的 {@code received_at}，≠ 本地投递时刻</td></tr>
 *   <tr><td>MISMATCH-04</td><td>载荷多一个未知字段 ⇒ BLOCKED（Go 的 DisallowUnknownFields）</td></tr>
 *   <tr><td>MISMATCH-05</td><td>载荷时间字段非 RFC3339 ⇒ BLOCKED</td></tr>
 *   <tr><td>MISMATCH-06</td><td>信封数值字段字符串化 ⇒ BLOCKED（Go 的 encoding/json 报错）</td></tr>
 *   <tr><td>MISMATCH-07</td><td>INSERT 的 {@code updated_at} = 入库时刻，≠ {@code created_at}（评论创建时刻）</td></tr>
 * </table>
 *
 * <h2>为什么必须是「真 PG + 真 MyBatis + 真事务」</h2>
 * <p>MISMATCH-01/02 的本质是<b>事务边界</b>：状态更新与异常抛出的先后关系。
 * 用 mock 数据层测不到任何东西（mock 的 mapper 不参与事务，UPDATE「永远成功」）。
 * 用 H2 也测不到：{@code SELECT … FOR UPDATE SKIP LOCKED}、jsonb、
 * {@code INSERT … ON CONFLICT … RETURNING} 在 H2 上不是同一套语义。
 * ⇒ 必须是真 PG。
 * <p>而且必须是<b>真 MyBatis + 真 DataSourceTransactionManager</b>：
 * 本类手工搭 {@link SqlSessionTemplate}（{@code SpringManagedTransactionFactory}）
 * ⇒ mapper 的 SQL 与 {@code CommentFactDeliveryService} 的事务跑在<b>同一条连接</b>上，
 * 与生产（Spring Boot + MyBatis-Plus）同构。
 * 若用 {@code SqlSessionFactory.openSession()} 直接开会话，
 * mapper 会拿到<b>另一条连接</b> ⇒ 事务形同虚设 ⇒ 测试必然假绿。
 *
 * <h2>为什么不用 {@code @SpringBootTest}</h2>
 * <p>同 {@link CommentSchemaPgGateTest} 的理由：本类只验「投递链路的语义」，
 * 不需要 Redis / Kafka / Nacos。走 Spring 上下文就得为了注入而保留那些自动配置
 * ⇒ 变成「全栈启动测试」，且掩盖了本类真正要验的东西。
 */
@Testcontainers
class CommentRpcGoSemanticsGateTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password")
            .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.COMMENT_RPC_GO_SEMANTICS_GATE));

    /** 本类独立账本（不与 {@code CommentSchemaPgGateTest} 的账本撞号）。 */
    private static final String LEDGER = "flyway_schema_history_comment_go_gate";

    private static final Logger LOG = LoggerFactory.getLogger(CommentRpcGoSemanticsGateTest.class);

    /**
     * 自建收集型 exporter —— <b>不引 {@code opentelemetry-sdk-testing}</b>
     * （只为测试引入它反而扩大依赖面；范式照抄同域 {@code CommentSpanWiringTest}）。
     */
    static final class CollectingExporter implements SpanExporter {
        final List<SpanData> spans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    private static final CollectingExporter EXPORTER = new CollectingExporter();

    /** Go 侧 {@code domain_fact.go:97} 的 {@code OccurredAt = EventTime} ⇒ 载荷与信封必须同一时刻。 */
    private static final String OCCURRED_AT = "2026-10-24T06:00:00Z";
    private static final String AVAILABLE_AT = "2026-10-24T06:00:01Z";

    /** Go 的 {@code commentFactProducer}（fact_delivery.go:18）。 */
    private static final String PRODUCER = "rtw.comment-rpc";
    /** Go 的 {@code commentFact.SchemaVersion}（domain_fact.go:99）。 */
    private static final String SCHEMA_VERSION = "rtw.community-fact.v1";
    private static final String EVENT_TYPE = "community.comment.created";

    private static JdbcTemplate jdbc;
    private static CommentDomainFactOutboxMapper outboxMapper;
    private static CommentIndexMapper indexMapper;
    private static CommentContentMapper contentMapper;
    private static CommentFactDeliveryService deliveryService;

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
        configuration.setEnvironment(new Environment("go-gate",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(CommentDomainFactOutboxMapper.class);
        configuration.addMapper(CommentIndexMapper.class);
        configuration.addMapper(CommentContentMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);

        outboxMapper = template.getMapper(CommentDomainFactOutboxMapper.class);
        indexMapper = template.getMapper(CommentIndexMapper.class);
        contentMapper = template.getMapper(CommentContentMapper.class);

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

        deliveryService = new CommentFactDeliveryService(
                outboxMapper,
                new CommentDeliveryValidator(sharedMapper),
                new DataSourceTransactionManager(dataSource));

        // 🔴 把 GlobalOpenTelemetry 指向本测试的收集器（范式照抄同域 CommentSpanWiringTest）。
        //    不装 SDK ⇒ GlobalOpenTelemetry.getTracer(...) 是 no-op ⇒ `Span.current()` 永远是 invalid
        //    ⇒ 下面的守门会「平凡通过」⇒ 装它是**守门能生效**的前提。
        //    ⚠️ 同域已有两个类这么装（CommentSpanWiringTest / CommentRpcSlowThresholdGateTest）
        //    ⇒ surefire 顺序执行 + 各自 setup/teardown ⇒ 不打架。
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                        .build())
                .build());
    }

    @AfterAll
    static void uninstallSdk() {
        GlobalOpenTelemetry.resetForTest();
    }

    // ====================================================================================
    // ③ span 契约：fact 投递链路**不产生** span 属性（与 Go 侧一致）
    //
    //    根因（逐条实测 · 2026-10-27 订正过一处早期误判）：
    //    ⚠️ 此前注记写「Go 侧 fact 投递链路根本不用 span」⇒ **不准确** ⇒ Go 侧**有**这个 span，
    //       但建在**共享层**且**不带属性**：
    //       `service/common/communityfact/process.go:26`
    //         `otel.Tracer("sea.rtw.communityfact").Start(ctx, "community.fact_delivery.dispatch")`
    //       （全文 69 行、**零** `SetAttributes`）；域内 `service/comment/rpc/internal/model/`
    //       整包 10 个非测试文件**零** span 命中 ⇒ Go 的 `DispatchFactOnce` 从不写 span 属性。
    //    · RAG 侧等价 span 也在**共享层**（`service/common/.../communityfact/Dispatcher.java:186`
    //      同名同 tracer）⇒ **span 拓扑两侧已对齐**。
    //    · RAG 域内那句 `Span.current().setAttribute` 为何**够不到**那个 span：
    //      共享层把 span 放进 **Context**（`Dispatcher.java:189` `ctx.with(span)`）而**从不 `makeCurrent`**，
    //      且 `dispatchFactOnce` 跑在**另一个线程**（`:172-176` `communityfact-dispatch` 单线程池 `:190` `supplyAsync`）
    //      ⇒ OTel 默认 `ThreadLocalContextStorage` 是**普通** `ThreadLocal`（**不继承**）
    //      ⇒ 投递线程上 `Span.current()` 恒为 **invalid span**（非 null）⇒ `if (span != null)` 恒真
    //      ⇒ `setAttribute` 落到 invalid span ⇒ **静默丢弃**（otel-java 的 invalid span 是 no-op）
    //      ⇒ 即「看起来在工作、实际永远不生效」⇒ 已删（2026-10-27），本类钉住不变量。
    //
    //    🔴 断言 ② 是**牙齿检查**：span 挂在投递**外面** ⇒ 若实现又写 `Span.current().setAttribute`
    //       ⇒ 那个 span 会带上属性 ⇒ 断言红 ⇒ ⇒ 「改回去必红、改完必绿」。
    //       ⚠️ 前提（已核）：`dispatchOnce` 自己不开 span，且它收到的 `Context.root()` 只是参数、
    //          **不会** makeCurrent ⇒ `Span.current()` 仍取决于线程作用域。
    // ====================================================================================

    @Test
    @DisplayName("🔴 装置活性：同线程挂的真 span **能**收到属性（否则下面那条会平凡通过）")
    void spanCollectorIsAliveOnThisThread() {
        Span probe = GlobalOpenTelemetry.getTracer("gate").spanBuilder("gate.probe").startSpan();
        try (Scope ignored = probe.makeCurrent()) {
            probe.setAttribute("gate.probe.attr", "v");
        } finally {
            probe.end();
        }
        assertThat(EXPORTER.spans)
                .as("观测装置必须活着：能导出的 span 上要真能看到人为写的属性")
                .anyMatch(s -> "gate.probe".equals(s.getName())
                        && s.getAttributes().asMap().containsKey(AttributeKey.stringKey("gate.probe.attr")));
    }

    @Test
    @DisplayName("🔴 span 契约：投递链路**不**向 span 写 comment.fact.* 属性（与 Go 侧零 span 属性一致）")
    void factDeliveryDoesNotWriteSpanAttributes() throws Exception {
        String aggregateId = "7101";
        insertValidFact(aggregateId);

        Span observed = GlobalOpenTelemetry.getTracer("gate").spanBuilder("gate.delivery").startSpan();
        try (Scope ignored = observed.makeCurrent()) {
            assertThat(deliveryService.dispatchOnce(Context.root(), acceptedSender("2026-10-24T06:00:09Z"), LOG))
                    .isTrue();
        } finally {
            observed.end();
        }

        SpanData exported = EXPORTER.spans.stream()
                .filter(s -> "gate.delivery".equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("gate.delivery span 未被导出 ⇒ 观测装置有问题"));
        assertThat(exported.getAttributes().asMap().keySet())
                .as("投递链路不得向 span 写 comment.fact.* 属性（Go 侧的 fact 投递 span 不带这两个属性）")
                .noneMatch(k -> k.getKey().startsWith("comment.fact."));
    }

    @Test
    @DisplayName("🔴 根因：不挂作用域时线程上 Span.current() 无效 ⇒ 原实现写不进去（它永远不生效的原因）")
    void currentSpanIsInvalidWithoutScope() {
        assertThat(Span.current().getSpanContext().isValid())
                .as("没有作用域时 Span.current() 必须是 invalid span ⇒ 这正是原实现写不进属性的原因")
                .isFalse();
    }

    @AfterEach
    void clean() {
        // 取件查询是「任意一条可投递行」⇒ 必须清干净，否则下一个用例会领到上一条。
        jdbc.update("DELETE FROM comment_domain_fact_outbox");
        jdbc.update("DELETE FROM comment_fact_stream");
        jdbc.update("DELETE FROM comment_index");
        jdbc.update("DELETE FROM comment_content");
    }

    // ====================================================================================
    // ① 正向对照组：合法冻结信封必须被正常投递并落技术回执
    //    （没有这一条，后面的「应当 BLOCKED」可能是因为夹具本身坏了而假绿）
    // ====================================================================================

    @Test
    @DisplayName("🔴 正向对照：合法冻结信封 ⇒ 投递成功 + 6 列技术元数据落盘")
    void validFrozenEnvelopeIsDeliveredAndAccepted() throws Exception {
        String aggregateId = "7001";
        insertValidFact(aggregateId);

        boolean sent = deliveryService.dispatchOnce(Context.root(), acceptedSender("2026-10-24T06:00:09Z"), LOG);

        assertThat(sent).isTrue();
        assertThat(deliveryStatus(aggregateId)).isEqualTo(1);      // DeliveryAccepted
        assertThat(jdbc.queryForObject(
                "SELECT technical_receipt_id FROM comment_domain_fact_outbox WHERE event_id = ?",
                String.class, eventId(aggregateId))).isEqualTo("rcpt-7001");
        assertThat(jdbc.queryForObject(
                "SELECT technical_offset FROM comment_domain_fact_outbox WHERE event_id = ?",
                Long.class, eventId(aggregateId))).isEqualTo(42L);
    }

    // ====================================================================================
    // ② MISMATCH-01：冻结信封非法 ⇒ DeliveryBlocked 必须落盘
    // ====================================================================================

    @Test
    @DisplayName("🔴 MISMATCH-01：信封非法 ⇒ delivery_status=3 落盘（抛错也必须落盘）")
    void blockedFrozenEnvelopeIsPersistedEvenThoughTheCallThrows() {
        String aggregateId = "7002";
        // producer 不匹配 ⇒ Go 的 validCommentDelivery（fact_delivery.go:23）判非法
        String payload = payloadJson(aggregateId, OCCURRED_AT, null);
        String envelope = envelopeJson(aggregateId, "1", payload)
                .replace("\"producer\":\"" + PRODUCER + "\"", "\"producer\":\"rtw.someone-else\"");
        insertFact(aggregateId, payload, envelope);

        assertThatThrownBy(() -> deliveryService.dispatchOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        // 🔴 这一行才是本测试的全部意义：Go 在 fact_delivery.go:79-83 是「UPDATE 后 Commit」。
        //    修复前这里用的是 @Transactional + RuntimeException ⇒ 回滚 ⇒ status 恒为 0。
        assertThat(deliveryStatus(aggregateId)).isEqualTo(3);
    }

    // ====================================================================================
    // ③ MISMATCH-02：投递失败 ⇒ DeliveryFailed + retry_count+1 必须落盘
    // ====================================================================================

    @Test
    @DisplayName("🔴 MISMATCH-02：投递失败 ⇒ delivery_status=2 且 retry_count=1 落盘")
    void failedDeliveryIsPersistedWithRetryCountEvenThoughTheCallThrows() {
        String aggregateId = "7003";
        insertValidFact(aggregateId);

        IllegalStateException cause = new IllegalStateException("DC endpoint refused the connection");
        assertThatThrownBy(() -> deliveryService.dispatchOnce(Context.root(), senderFailingWith(cause), LOG))
                .isInstanceOf(CommentFactDeliveryService.DeliveryDeferredException.class)
                // Go 的 fact_delivery.go:110 是 `return false, sendErr` ⇒ 原始错误必须带出来
                .hasCause(cause);

        assertThat(deliveryStatus(aggregateId)).isEqualTo(2);
        assertThat(retryCount(aggregateId)).isEqualTo(1);
    }

    // ====================================================================================
    // ④ MISMATCH-03：technical_received_at 必须是 DC 回执的 received_at
    // ====================================================================================

    @Test
    @DisplayName("🔴 MISMATCH-03：technical_received_at = DC 回执的 received_at（≠ 本地投递时刻）")
    void technicalReceivedAtComesFromTheDataCenterReceipt() throws Exception {
        String aggregateId = "7004";
        insertValidFact(aggregateId);
        // 一个刻意很早的时刻 ⇒ 若实现仍写本地 now，这一列就会与它相差数年
        String dcReceivedAt = "2020-01-02T03:04:05Z";

        deliveryService.dispatchOnce(Context.root(), acceptedSender(dcReceivedAt), LOG);

        Instant received = technicalReceivedAt(aggregateId);
        Instant delivered = deliveredAt(aggregateId);
        assertThat(received).isEqualTo(Instant.parse(dcReceivedAt));
        // delivered_at 是本地投递时刻 ⇒ 两列必须是两个不同的值
        assertThat(delivered).isNotEqualTo(received);
        assertThat(delivered).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
    }

    // ====================================================================================
    // ⑤ MISMATCH-04：载荷多一个未知字段 ⇒ BLOCKED
    // ====================================================================================

    @Test
    @DisplayName("🔴 MISMATCH-04：载荷多一个未知字段 ⇒ BLOCKED（Go 的 DisallowUnknownFields）")
    void payloadWithUnknownFieldIsBlocked() {
        String aggregateId = "7005";
        String payload = payloadJson(aggregateId, OCCURRED_AT, "tampered_extra_field");
        String envelope = envelopeJson(aggregateId, "1", payload);
        insertFact(aggregateId, payload, envelope);

        assertThatThrownBy(() -> deliveryService.dispatchOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus(aggregateId)).isEqualTo(3);
    }

    // ====================================================================================
    // ⑥ MISMATCH-05：载荷的三个时间字段必须是 RFC3339
    // ====================================================================================

    @Test
    @DisplayName("🔴 MISMATCH-05：载荷 event_time 非 RFC3339 ⇒ BLOCKED")
    void payloadWithMalformedEventTimeIsBlocked() {
        String aggregateId = "7006";
        // Go 侧 commentFact.EventTime 是 time.Time（domain_fact.go:77）
        // ⇒ encoding/json 解码非 RFC3339 直接报错 ⇒ StrictDecode 失败 ⇒ BLOCKED
        String payload = payloadJson(aggregateId, "not-a-timestamp", null);
        String envelope = envelopeJson(aggregateId, "1", payload);
        insertFact(aggregateId, payload, envelope);

        assertThatThrownBy(() -> deliveryService.dispatchOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus(aggregateId)).isEqualTo(3);
    }

    @Test
    @DisplayName("🔴 MISMATCH-05：载荷 available_at 非 RFC3339 ⇒ BLOCKED")
    void payloadWithMalformedAvailableAtIsBlocked() {
        String aggregateId = "7007";
        String payload = payloadJson(aggregateId, OCCURRED_AT, null)
                .replace("\"available_at\":\"" + AVAILABLE_AT + "\"", "\"available_at\":\"2026-10-24 06:00:01\"");
        String envelope = envelopeJson(aggregateId, "1", payload);
        insertFact(aggregateId, payload, envelope);

        assertThatThrownBy(() -> deliveryService.dispatchOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus(aggregateId)).isEqualTo(3);
    }

    // ====================================================================================
    // ⑦ MISMATCH-06：信封 / 载荷的标量类型必须精确匹配（Go 的 encoding/json 规则）
    // ====================================================================================

    @Test
    @DisplayName("🔴 MISMATCH-06：信封 schema_version 被字符串化 ⇒ BLOCKED")
    void envelopeWithStringifiedSchemaVersionIsBlocked() {
        String aggregateId = "7008";
        String payload = payloadJson(aggregateId, OCCURRED_AT, null);
        // Go 的 communityfact.Event.SchemaVersion 是 int ⇒ {"schema_version":"1"} 解码报错
        String envelope = envelopeJson(aggregateId, "\"1\"", payload);
        insertFact(aggregateId, payload, envelope);

        assertThatThrownBy(() -> deliveryService.dispatchOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus(aggregateId)).isEqualTo(3);
    }

    @Test
    @DisplayName("🔴 MISMATCH-06：载荷的字符串字段被数字化 ⇒ BLOCKED（Go 的 string 字段不收数字）")
    void payloadWithNumericStringFieldIsBlocked() {
        String aggregateId = "7009";
        // Go 的 commentFact.TargetType 是 string（domain_fact.go:65）
        // ⇒ JSON 数字解进 string 字段报错 → StrictDecode 失败 → BLOCKED。
        //
        // ⚠️ 为什么用 target_type 而不是 schema_version：
        //   若把 schema_version 改成数字，宽松解码会读成 "1"，
        //   而 "1" ≠ "rtw.community-fact.v1" ⇒ **照样 BLOCKED**，
        //   但原因是「版本号不匹配」而不是「类型不合法」
        //   ⇒ 那条断言即使没有强转禁令也会绿 ⇒ **是因错误原因而绿的假守卫**（§3.3）。
        //   target_type 数字化后宽松解码读成 "5"（非空 ⇒ 通过空白校验）
        //   ⇒ 整条事实会被**照常投递** ⇒ 这条断言才真正区分「有没有强转禁令」。
        String payload = payloadJson(aggregateId, OCCURRED_AT, null)
                .replace("\"target_type\":\"article\"", "\"target_type\":5");
        String envelope = envelopeJson(aggregateId, "1", payload);
        insertFact(aggregateId, payload, envelope);

        assertThatThrownBy(() -> deliveryService.dispatchOnce(Context.root(), mustNotBeCalled(), LOG))
                .isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);

        assertThat(deliveryStatus(aggregateId)).isEqualTo(3);
    }

    // ====================================================================================
    // ⑧ MISMATCH-07：INSERT 的 updated_at 必须是入库时刻，不是评论创建时刻
    // ====================================================================================

    @Test
    @DisplayName("🔴 MISMATCH-07：comment_index INSERT 的 updated_at = 入库时刻（≠ created_at）")
    void commentIndexInsertUsesInsertTimeForUpdatedAt() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        CommentIndexEntity index = new CommentIndexEntity();
        index.setId(7101L);
        index.setTargetType("article");
        index.setTargetId("a-7101");
        index.setUserId(7L);
        index.setRootId(0L);
        index.setParentId(0L);
        index.setState(0);
        index.setAttribute(0L);
        index.setCreatedAt(createdAt);

        indexMapper.insert(index);

        OffsetDateTime storedCreated = timestamp("comment_index", "id", 7101L, "created_at");
        OffsetDateTime storedUpdated = timestamp("comment_index", "id", 7101L, "updated_at");
        // created_at = 评论创建时刻（Kafka 的 create_time）
        assertThat(storedCreated.toInstant()).isEqualTo(createdAt.toInstant());
        // updated_at = 入库时刻 ⇒ 必须比 created_at 晚一个多小时（修复前两列相同）
        assertThat(storedUpdated.toInstant()).isAfter(createdAt.toInstant().plusSeconds(600));
        assertThat(storedUpdated.toInstant()).isBefore(Instant.now().plusSeconds(60));
    }

    @Test
    @DisplayName("🔴 MISMATCH-07：comment_content INSERT 的 updated_at = 入库时刻（≠ created_at）")
    void commentContentInsertUsesInsertTimeForUpdatedAt() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        CommentContentEntity content = new CommentContentEntity();
        content.setCommentId(7102L);
        content.setContent("gate-test-content");
        content.setMeta("{}");
        content.setCreatedAt(createdAt);

        contentMapper.insert(content);

        OffsetDateTime storedCreated = timestamp("comment_content", "comment_id", 7102L, "created_at");
        OffsetDateTime storedUpdated = timestamp("comment_content", "comment_id", 7102L, "updated_at");
        assertThat(storedCreated.toInstant()).isEqualTo(createdAt.toInstant());
        assertThat(storedUpdated.toInstant()).isAfter(createdAt.toInstant().plusSeconds(600));
        assertThat(storedUpdated.toInstant()).isBefore(Instant.now().plusSeconds(60));
    }

    // ====================================================================================
    // 夹具
    // ====================================================================================

    private static String eventId(String aggregateId) {
        return "rtw.comment." + aggregateId + ".created";
    }

    /**
     * 按 Go 的 {@code commentFact}（domain_fact.go:55-80）逐字段构造载荷 JSON。
     *
     * @param eventTime  {@code event_time} 的字面量（用于注入畸形值）
     * @param extraField 非 null 时追加一个<b>未知字段</b>（Go 的 DisallowUnknownFields 会因此失败）
     */
    private static String payloadJson(String aggregateId, String eventTime, String extraField) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schema_version\":\"").append(SCHEMA_VERSION).append("\"");
        sb.append(",\"event_id\":\"").append(eventId(aggregateId)).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(aggregateId).append("\"");
        // ⚠️ aggregate_version 刻意**不出现**：Go 的载荷里它必须是 nil（版本号的唯一权威在信封）
        sb.append(",\"operation_id\":\"").append(eventId(aggregateId)).append("\"");
        sb.append(",\"subject_ref\":\"rtw.identity/platform/7\"");
        sb.append(",\"target_type\":\"article\"");
        sb.append(",\"target_id\":\"a-").append(aggregateId).append("\"");
        sb.append(",\"revision_status\":\"unknown\"");
        sb.append(",\"operation\":\"create\"");
        sb.append(",\"source_ref\":\"rtw.comment/").append(aggregateId).append("\"");
        sb.append(",\"comment_id\":\"").append(aggregateId).append("\"");
        sb.append(",\"visibility_state\":0");
        sb.append(",\"search_evidence\":false");
        sb.append(",\"event_time\":\"").append(eventTime).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"available_at\":\"").append(AVAILABLE_AT).append("\"");
        if (extraField != null) {
            sb.append(",\"").append(extraField).append("\":\"injected\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 按 Go 的 {@code commentDeliveryEnvelope}（domain_fact.go:43-53）构造信封 JSON。
     *
     * @param schemaVersion {@code schema_version} 的<b>字面量</b>（用于注入字符串化的数值）
     */
    private static String envelopeJson(String aggregateId, String schemaVersion, String payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event_id\":\"").append(eventId(aggregateId)).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"schema_version\":").append(schemaVersion);
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(aggregateId).append("\"");
        sb.append(",\"aggregate_version\":1");
        sb.append(",\"operation_id\":\"").append(eventId(aggregateId)).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"payload\":").append(payload);
        sb.append("}");
        return sb.toString();
    }

    private static void insertValidFact(String aggregateId) {
        String payload = payloadJson(aggregateId, OCCURRED_AT, null);
        insertFact(aggregateId, payload, envelopeJson(aggregateId, "1", payload));
    }

    private static void insertFact(String aggregateId, String payload, String envelope) {
        // 走**生产同款**的写路径（显式 jsonb typeHandler），而不是另写一份 INSERT。
        outboxMapper.appendFact(eventId(aggregateId), EVENT_TYPE, payload, aggregateId, 1L, envelope);
    }

    private static int deliveryStatus(String aggregateId) {
        return jdbc.queryForObject(
                "SELECT delivery_status FROM comment_domain_fact_outbox WHERE event_id = ?",
                Integer.class, eventId(aggregateId));
    }

    private static int retryCount(String aggregateId) {
        return jdbc.queryForObject(
                "SELECT retry_count FROM comment_domain_fact_outbox WHERE event_id = ?",
                Integer.class, eventId(aggregateId));
    }

    private static Instant technicalReceivedAt(String aggregateId) {
        return timestamp("comment_domain_fact_outbox", "event_id", eventId(aggregateId), "technical_received_at")
                .toInstant();
    }

    private static Instant deliveredAt(String aggregateId) {
        return timestamp("comment_domain_fact_outbox", "event_id", eventId(aggregateId), "delivered_at").toInstant();
    }

    private static OffsetDateTime timestamp(String table, String keyColumn, Object key, String column) {
        OffsetDateTime value = jdbc.queryForObject(
                "SELECT " + column + " FROM " + table + " WHERE " + keyColumn + " = ?",
                OffsetDateTime.class, key);
        assertThat(value).as(table + "." + column).isNotNull();
        return value;
    }

    private static Wire.Sender acceptedSender(String receivedAt) {
        return (ctx, event, raw) -> {
            Wire.TechnicalReceipt receipt = new Wire.TechnicalReceipt();
            receipt.eventId = event.eventId;
            receipt.producer = event.producer;
            receipt.technicalStatus = "accepted";
            receipt.receiptId = "rcpt-" + event.aggregateId;
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
