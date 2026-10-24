package com.rag.favorite.favorite.rpc.fact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.communityfact.Wire;
import com.rag.favorite.favorite.rpc.domain.FavoriteFactOutboxEntity;
import com.rag.favorite.favorite.rpc.domain.FavoriteItemEntity;
import com.rag.favorite.favorite.rpc.service.FavoriteAuthorityLookupService;
import com.rag.favorite.favorite.rpc.service.FavoriteFactEnvelopeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴🔴 权威读端点的 <b>响应体形状</b>：v1 三字段 vs v2 两字段。
 *
 * <h2>Go 出处</h2>
 * <pre>
 * // service/favorite/rpc/internal/model/favorite_authority.go:27-43
 * type FavoriteAuthorityFact struct {
 *     Event              FavoriteWireEvent    `json:"event"`
 *     SubjectRef         FavoriteSubjectRef   `json:"subject_ref"`   // ← 三字段
 *     PredecessorEventID string                 `json:"predecessor_event_id,omitempty"`
 *     TechnicalReceipt   FavoriteTechnicalReceipt `json:"technical_receipt"`
 *     SourceEventHash    string                 `json:"source_event_hash"`
 * }
 *
 * type FavoriteAuthorityFactV2 struct {
 *     Event              FavoriteWireEvent    `json:"event"`
 *     SubjectRef         FavoriteSubjectRefV2 `json:"subject_ref"`   // ← 两字段
 *     ...
 * }
 *
 * // favorite_fact.go:78-89
 * type FavoriteSubjectRef struct { AuthorityID, TenantID, SubjectID string }
 * type FavoriteSubjectRefV2 struct { Issuer, SubjectID string }      // ← 没有 tenant_id
 * </pre>
 *
 * <p>⇒ Go 的 v2 入口（favorite_authority.go:195-204）在 resolve 之后<b>把结果压扁</b>：
 * <pre>
 * return FavoriteAuthorityFactV2{Event: fact.Event,
 *     SubjectRef:         FavoriteSubjectRefV2{Issuer: fact.SubjectRef.AuthorityID, SubjectID: fact.SubjectRef.SubjectID},
 *     PredecessorEventID: fact.PredecessorEventID, TechnicalReceipt: fact.TechnicalReceipt,
 *     SourceEventHash:    fact.SourceEventHash}, nil
 * </pre>
 * ⇒ <b>{@code tenant_id} 被丢弃</b>、{@code authority_id} <b>改名</b>成 {@code issuer}。
 *
 * <h2>🔴 修复前 RAG 侧的行为（真 bug）</h2>
 * <p>{@code FavoriteAuthorityLookupService#authoritativeFactV2} 以前直接返回
 * {@code FactV1}（<b>三字段</b>）⇒ <b>v2 端点把 v1 的形状发给了下游</b>。
 * 而下游 binder（BTW）是<b>按字段名解析</b>的 ⇒ {@code issuer} 取不到、{@code tenant_id}
 * 多出来 ⇒ <b>解析失败</b>。
 * <p>⚠️ 这个 bug <b>不会被任何单测发现</b>：v1 端点的形状是对的，v2 端点只是「多了字段」，
 * 断言状态码 / 断言业务码都区分不出来 ⇒ 必须<b>逐字段断言响应体</b>。
 *
 * <h2>为什么用真 PG 而不是 mock</h2>
 * <p>本类要证明的是「<b>压扁这一步真的发生了</b>」，而压扁发生在
 * {@code resolve(...)} <b>之后</b> ⇒ 只有让<b>真的</b> {@code resolve} 跑通、
 * 再看<b>真的</b> HTTP 响应体，才能同时覆盖「service 有没有压扁」与
 * 「端点有没有用错形状」两件事。
 * <p>⇒ mock 掉 service 会让这两个问题里的第二个<b>永远测不到</b>（假绿）。
 *
 * <h2>fixture 怎么造</h2>
 * <p>用 {@code FavoriteFactEnvelopeFactory.build(item, version=1, "assert", now, schema)}
 * 造一条<b>信封 schema 为 1 或 2</b>、<b>聚合版本为 1</b>的 assert 事实。
 * <p>🔴 <b>「schema=2 的 assert」是合法状态</b>，不是造出来的怪东西：Go 的
 * {@code favoriteOutboxV2}（favorite_fact.go:164-198）写的就是 schema=2 的信封，
 * 而 assert 行用哪个 schema 由 {@code FavoriteAssertionSchemaResolver} 决定。
 * <p>⇒ 在这条行上：{@code aggregate_version == 1} ⇒ Go 走
 * {@code if row.AggregateVersion != 2} 那条分支（favorite_authority.go:225），
 * 而 <b>v2 端点要的正是 {@code expectedSchema == 2} 的压扁结果</b> ⇒ 一行即可覆盖 v2 形状。
 */
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
@SpringBootTest(
        classes = com.rag.favorite.favorite.rpc.FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FavoriteAuthorityV2ResponseShapeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("first_db")
                    .withUsername("admin")
                    .withPassword("password");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("favorite.fact.dispatch.enabled", () -> "false");
        registry.add("favorite.fact.authority.enabled", () -> "false");
        registry.add("favorite.fact.legacy-preflight.enabled", () -> "false");
    }

    /** Go {@code http.go:28-30} 要求 token ≥ 32 字节。 */
    private static final String TOKEN = "favorite-authority-test-token-0123456789";

    private static final String PRODUCER = "rtw.community.favorite";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private FavoriteAuthorityLookupService lookupService;

    @Autowired
    private FavoriteFactEnvelopeFactory envelopeFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanTables() {
        // TRUNCATE（不是 DELETE）：与 FavoriteAuthorityLookupTimeoutTest 同款清库姿势。
        jdbcTemplate.execute("TRUNCATE TABLE favorite_fact_outbox, favorite_item CASCADE");
    }

    // ==================== 用例 ====================

    @Test
    @Timeout(60)
    @DisplayName("🔴 v2 端点：subject_ref 恰好【两字段】issuer + subject_id，没有 tenant_id")
    void v2EndpointEmitsTwoFieldSubjectRef() throws Exception {
        // 信封 schema=2 的 assert 行 ⇒ 只有 v2 端点能读到它。
        String eventId = insertAcceptedAssert(9001L, 7001L, 1001L, "2001", 2);

        MockHttpServletResponse resp = call(2, eventId);

        assertEquals(200, resp.getStatus(),
                "v2 端点读 schema=2 的事实应当成功；body=[" + resp.getContentAsString() + "]");

        JsonNode body = JSON.readTree(resp.getContentAsString());
        JsonNode subjectRef = body.get("subject_ref");
        assertTrue(subjectRef != null && subjectRef.isObject(),
                "响应体必须有 subject_ref 对象；body=[" + resp.getContentAsString() + "]");

        // 🔴 逐字段：Go 的 FavoriteSubjectRefV2 只有 issuer + subject_id。
        assertEquals(new TreeSet<>(java.util.Set.of("issuer", "subject_id")),
                fieldNames(subjectRef),
                "v2 的 subject_ref 必须恰好是 {issuer, subject_id}；实际=" + fieldNames(subjectRef)
                        + "（多出 tenant_id 就是把 v1 的形状发给了下游 ⇒ 下游按字段名解析会失败）");
        assertEquals("rtw.identity", subjectRef.get("issuer").asText(),
                "Go 的 FavoriteSubjectRefV2.Issuer 来自 v1 的 AuthorityID");
        assertEquals("1001", subjectRef.get("subject_id").asText(),
                "Go 的 FavoriteSubjectRefV2.SubjectID 来自 v1 的 SubjectID");
        assertFalse(subjectRef.has("tenant_id"),
                "v2 的 subject_ref 不得带 tenant_id（Go: 'The legacy fixed platform slot "
                        + "is deliberately absent from the v2 EventSpec'）");

        // 其余 4 个顶层字段与 v1 同名同义。
        assertEquals(new TreeSet<>(java.util.Set.of("event", "source_event_hash", "subject_ref",
                        "technical_receipt")),
                fieldNames(body),
                "顶层字段集必须与 Go 的 FavoriteAuthorityFactV2 一致；实际=" + fieldNames(body));
        assertTrue(body.get("event") != null && body.get("event").isObject(), "必须有 event");
        assertTrue(body.get("technical_receipt") != null, "必须有 technical_receipt");
        assertEquals(64, body.get("source_event_hash").asText().length(),
                "source_event_hash 是 JCS+SHA256 的 64 位 hex");
        // assert 行不带 predecessor_event_id（Go: favorite_authority.go:173-175 + omitempty）。
        assertFalse(body.has("predecessor_event_id"),
                "assert 行不得带 predecessor_event_id（Go 的 omitempty 语义）");
    }

    @Test
    @Timeout(60)
    @DisplayName("✅ v1 端点：subject_ref 恰好【三字段】authority_id + tenant_id + subject_id")
    void v1EndpointEmitsThreeFieldSubjectRef() throws Exception {
        // 信封 schema=1 的 assert 行 ⇒ 只有 v1 端点能读到它。
        String eventId = insertAcceptedAssert(9002L, 7002L, 1002L, "2002", 1);

        MockHttpServletResponse resp = call(1, eventId);

        assertEquals(200, resp.getStatus(),
                "v1 端点读 schema=1 的事实应当成功；body=[" + resp.getContentAsString() + "]");

        JsonNode subjectRef = JSON.readTree(resp.getContentAsString()).get("subject_ref");
        assertEquals(new TreeSet<>(java.util.Set.of("authority_id", "subject_id", "tenant_id")),
                fieldNames(subjectRef),
                "v1 的 subject_ref 必须恰好是 {authority_id, tenant_id, subject_id}；实际="
                        + fieldNames(subjectRef));
        assertEquals("rtw.identity", subjectRef.get("authority_id").asText(),
                "Go 的 FavoriteSubjectRef.AuthorityID");
        assertEquals("platform", subjectRef.get("tenant_id").asText(),
                "Go 的 FavoriteSubjectRef.TenantID");
        assertEquals("1002", subjectRef.get("subject_id").asText(),
                "Go 的 FavoriteSubjectRef.SubjectID");
    }

    @Test
    @Timeout(60)
    @DisplayName("🔴 两个端点按【信封 schema】分域：v1 读不到 schema=2 的事实，反之亦然")
    void endpointsAreScopedByEnvelopeSchema() throws Exception {
        // Go: favorite_authority.go:220-223 —— fact.Event.SchemaVersion != expectedSchema ⇒ 不可用。
        String schema2Event = insertAcceptedAssert(9003L, 7003L, 1003L, "2003", 2);
        String schema1Event = insertAcceptedAssert(9004L, 7004L, 1004L, "2004", 1);

        assertEquals(404, call(1, schema2Event).getStatus(),
                "v1 端点（expectedSchema=1）读 schema=2 的事实应当 404");
        assertEquals(404, call(2, schema1Event).getStatus(),
                "v2 端点（expectedSchema=2）读 schema=1 的事实应当 404");
        assertEquals(200, call(2, schema2Event).getStatus(), "v2 端点读 schema=2 应当成功");
        assertEquals(200, call(1, schema1Event).getStatus(), "v1 端点读 schema=1 应当成功");
    }

    // ==================== fixture ====================

    /**
     * 造一条<b>已被 DC 接受</b>的 assert 事实（信封 schema 可选 1 / 2）+ 一条活着的收藏条目。
     *
     * <p>「活着的收藏条目」是必要的：Go 的 v1 路径（{@code row.AggregateVersion != 2}）
     * 会<b>独立核对业务行</b>（favorite_authority.go:228-240）⇒ 没有业务行就必须有墓碑，
     * 而墓碑又要求另造一条 retract 行 ⇒ 用「活着的条目」是<b>最短的一条路</b>。
     */
    private String insertAcceptedAssert(long favoriteId, long folderId, long userId,
                                        String targetId, int schema) throws Exception {
        Instant now = Instant.parse("2026-10-25T08:00:00Z");

        FavoriteItemEntity item = new FavoriteItemEntity();
        item.setFavoriteId(favoriteId);
        item.setFolderId(folderId);
        item.setUserId(userId);
        item.setTargetType("article");
        item.setTargetId(targetId);
        item.setTargetRevision("v1");

        FavoriteFactOutboxEntity row = envelopeFactory.build(item, 1L, "assert", now, schema);
        row.setStatus(Wire.DeliveryStatus.DELIVERY_ACCEPTED.code());
        row.setDeliveredAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        row.setTechnicalReceivedAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        row.setTechnicalReceiptId("rcpt-" + favoriteId);
        row.setTechnicalOffset(1L);
        row.setTechnicalInputHash(Wire.canonicalHash(row.getPayload().getBytes(StandardCharsets.UTF_8)));

        jdbcTemplate.update("""
                INSERT INTO favorite_fact_outbox
                    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
                     technical_receipt_id, technical_input_hash, technical_offset,
                     technical_received_at, delivered_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.getEventId(), row.getFavoriteId(), row.getAggregateVersion(), row.getPayload(),
                row.getStatus(), row.getRetryCount(), row.getTechnicalReceiptId(),
                row.getTechnicalInputHash(), row.getTechnicalOffset(),
                row.getTechnicalReceivedAt(), row.getDeliveredAt());

        // 🔴 JCS hash 必须是「**库里读回来**的那份字节」的 hash：
        //   payload 列是 jsonb ⇒ PG 会规范化存储 ⇒ 读回时已不是插入时的原始字节。
        //   权威读算的是 row.getPayload()（读回值）的 hash ⇒ 这里必须按读回值重算。
        //   ⚠️ 不可变触发器只护 event_id / favorite_id / aggregate_version / payload
        //   ⇒ 改 technical_input_hash 是允许的。
        String storedPayload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM favorite_fact_outbox WHERE event_id = ?",
                String.class, row.getEventId());
        jdbcTemplate.update("UPDATE favorite_fact_outbox SET technical_input_hash = ? WHERE event_id = ?",
                Wire.canonicalHash(storedPayload.getBytes(StandardCharsets.UTF_8)), row.getEventId());

        jdbcTemplate.update("""
                INSERT INTO favorite_item
                    (favorite_id, folder_id, user_id, target_id, target_type, target_revision, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                favoriteId, folderId, userId, targetId, "article", "v1",
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC));

        return row.getEventId();
    }

    private MockHttpServletResponse call(int version, String eventId) throws Exception {
        FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet servlet =
                new FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet(
                        TOKEN.getBytes(StandardCharsets.UTF_8), lookupService, version == 2);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/internal/v" + version + "/favorite/facts/" + PRODUCER + "/" + eventId);
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        servlet.doGet(req, resp);
        return resp;
    }

    private static TreeSet<String> fieldNames(JsonNode node) {
        TreeSet<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
