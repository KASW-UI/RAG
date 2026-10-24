package com.rag.users.admin.rpc;

import com.rag.users.admin.rpc.service.AdminDomainService;
import com.rag.users.admin.rpc.service.AdminSpans;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 DRIFT-ARPC-006 守门 —— admin-rpc 的手写 span 必须<b>真的被导出</b>。
 *
 * <h2>为什么不能只写「结构断言」</h2>
 * <p>AGENTS.md §3.3：「<b>不得凭注解/方法名称断定机制已启用</b>」。
 * 断言「{@code AdminSpans.traced} 被调用了」只能证明<b>代码写成了这样</b>，
 * 证明不了<b>运行时真的产生了 span</b>。
 * ⇒ 本类走**装配层**：自己装一个 SDK + 收集型 {@link SpanExporter}，
 * 让 {@code GlobalOpenTelemetry} 指向它，然后断言<b>导出的 span</b>里
 * 有正确的名字与属性。
 *
 * <h2>Go 出处（决定「有几个 span」，不是「13 个」）</h2>
 * <p>Go 的 admin/rpc <b>13 个 logic 里只有 8 个</b>import 了 otel 并手写 span；
 * 另外 <b>5 个没有</b>：
 * {@code getselflogic.go} / {@code getuserlogic.go} / {@code getuserlistlogic.go} /
 * {@code logoutlogic.go} / {@code updateselflogic.go}。
 * ⇒ 台账此前写「Go 逐 Logic 打 audit 属性」会让人误以为是 13 个；
 * 真实是 <b>8 个 logic / 13 个 attribute</b>（13 是属性条数）。
 * 本类<b>双向</b>钉死：有 span 的名字必须出现、没 span 的方法<b>必须不产生</b>对应 span。
 *
 * <p>⚠️ 顺带证明 <b>N-4（签字偏离）确实落到了数据里</b>：
 * Go 的 {@code audit.operator_id} 恒为 0（读 {@code ctx["uid"]} 而 claim 名是 {@code userId}），
 * RAG 传<b>真实 uid</b>。补了 span 之后这个差异第一次真正出现在 trace 数据里
 * ⇒ 对拍/排障时两侧该属性不同，<b>属预期差异，不是回归</b>。
 */
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class AdminSpanWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_span_wiring_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * 自建收集型 exporter —— <b>不引 {@code opentelemetry-sdk-testing}</b>
     * （该包不在 admin-rpc 的依赖里，引入它只为测试反而扩大面）。
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

        void clear() {
            spans.clear();
        }
    }

    private static final CollectingExporter EXPORTER = new CollectingExporter();

    @BeforeAll
    static void installSdk() {
        // 🔴 关键：把 GlobalOpenTelemetry 指向本测试的收集器。
        // AdminSpans 用的是 GlobalOpenTelemetry.getTracer(...)，不装 SDK 就是 no-op。
        GlobalOpenTelemetry.resetForTest();
        // GlobalOpenTelemetry.set 要的是 OpenTelemetry，不是 SdkTracerProvider
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

    @Autowired private AdminDomainService domain;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final long USER_UID = 7701L;
    private static final long OPERATOR_ID = 8801L;

    private void seedUser() {
        jdbcTemplate.update("""
                INSERT INTO users_user (uid, username, password, email, status, score,
                                       extra_info, create_time, update_time)
                VALUES (?, 'span-probe-user', 'x', 'span@mail.test', 0, 0, '{}'::jsonb,
                        TIMESTAMPTZ '2020-01-01 00:00:00+00', TIMESTAMPTZ '2020-01-01 00:00:00+00')
                ON CONFLICT (uid) DO NOTHING
                """, USER_UID);
    }

    private static Optional<SpanData> findSpan(String name) {
        synchronized (EXPORTER.spans) {
            return EXPORTER.spans.stream().filter(s -> name.equals(s.getName())).findFirst();
        }
    }

    @Test
    @DisplayName("✅ resetUserPassword 真的导出 Action-Admin-ResetUserPassword + 两个 audit 属性")
    void resetUserPasswordProducesGoSpanWithAttributes() {
        seedUser();
        EXPORTER.clear();

        assertTrue(domain.resetUserPassword(USER_UID, OPERATOR_ID));

        SpanData span = findSpan(AdminSpans.RESET_USER_PASSWORD).orElseThrow(
                () -> new AssertionError("没有导出名为 " + AdminSpans.RESET_USER_PASSWORD
                        + " 的 span。实际导出的 = " + EXPORTER.spans.stream().map(SpanData::getName).toList()));

        assertEquals(AdminSpans.TRACER_NAME, span.getInstrumentationScopeInfo().getName(),
                "tracer 名必须是 admin-rpc（Go: otel.Tracer(\"admin-rpc\")）");

        assertEquals(OPERATOR_ID, span.getAttributes().get(AttributeKey.longKey(AdminSpans.ATTR_OPERATOR_ID)),
                "audit.operator_id 必须带上（Go updateuserlogic 等 4 个 logic 共用这两个属性）");
        assertEquals(USER_UID, span.getAttributes().get(AttributeKey.longKey(AdminSpans.ATTR_TARGET_UID)),
                "audit.target_uid 必须带上");

        assertTrue(span.hasEnded(), "span 必须已 end（Go 的 defer span.End()）");
    }

    @Test
    @DisplayName("✅ Go 没打 span 的方法，RAG 也不许凭空加（getUser 不产生 span）")
    void methodsWithoutGoSpanMustNotProduceOne() {
        seedUser();
        EXPORTER.clear();

        // Go 的 getuserlogic.go **没有** otel import、没有任何 span ⇒ RAG 也不该有
        assertNotNull(domain.getUser(USER_UID));

        // GetSelf 用的就是 getOneAdminByUsername 一类的读路径，同样无 span；
        // 这里断言「没有产生任何 Action-Admin-* span」
        boolean anyAdminSpan = EXPORTER.spans.stream()
                .anyMatch(s -> s.getName() != null && s.getName().startsWith("Action-Admin-"));
        assertFalse(anyAdminSpan,
                "Go 的 getuserlogic/getselflogic 本来就没有 span，RAG 不得凭空补 —— "
                        + "否则 trace 里的 admin span 数会从 Go 的 8 个变成 13 个。实际 = "
                        + EXPORTER.spans.stream().map(SpanData::getName).toList());
    }

    @Test
    @DisplayName("✅ Go 有 span 的 8 个方法，AdminSpans 常量与之逐条对应")
    void spanNameConstantsMatchGoInventory() {
        // Go 侧有 span 的 8 个 logic 及其 span 名（声明点见 AdminSpans 的 javadoc 表格）
        assertEquals("Action-Admin-Login", AdminSpans.LOGIN);
        assertEquals("Action-Admin-CreateAdmin", AdminSpans.CREATE_ADMIN);
        assertEquals("Action-Admin-CreateInviteCode", AdminSpans.CREATE_INVITE_CODE);
        assertEquals("Action-Admin-UpdateUser", AdminSpans.UPDATE_USER);
        assertEquals("Action-Admin-BanUser", AdminSpans.BAN_USER);
        assertEquals("Action-Admin-UnBanUser", AdminSpans.UNBAN_USER);
        assertEquals("Action-Admin-DeleteUser", AdminSpans.DELETE_USER);
        assertEquals("Action-Admin-ResetUserPassword", AdminSpans.RESET_USER_PASSWORD);
        assertEquals("admin-rpc", AdminSpans.TRACER_NAME);
    }
}