package com.rag.comment.comment.rpc;

import com.rag.comment.comment.proto.CreateCommentReq;
import com.rag.comment.comment.proto.DeleteCommentReq;
import com.rag.comment.comment.proto.GetCommentReq;
import com.rag.comment.comment.proto.ManageCommentReq;
import com.rag.comment.comment.proto.ManageType;
import com.rag.comment.comment.proto.ReportCommentReq;
import com.rag.comment.comment.proto.ReportReason;
import com.rag.comment.comment.proto.UpdateSubjectStateReq;
import com.rag.comment.comment.rpc.service.CommentDomainService;
import com.rag.comment.comment.rpc.service.CommentSpans;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 DRIFT-COMMENT-C-3 守门 —— comment-rpc 的 <b>手写 span 必须真的被导出</b>，
 * 且带齐 Go 侧的 span 名 / tracer 名 / 属性名 / 属性值。
 *
 * <h2>为什么不能只写「结构断言」</h2>
 * <p>AGENTS.md §3.3：<b>不得凭注解 / 方法名称断定机制已启用</b>。
 * 断言「{@code CommentSpans.traced} 被调用了」只能证明<b>代码写成了这样</b>，
 * 证明不了<b>运行时真的产生了 span</b>。
 * ⇒ 本类走<b>装配层</b>：自己装一个 SDK ＋ 收集型 {@link SpanExporter}，
 * 让 {@code GlobalOpenTelemetry} 指向它，然后断言<b>导出的 span</b>。
 *
 * <h2>Go 出处（决定「有几个 span」，不是「有几个方法」）</h2>
 * <p>Go 的 comment-rpc <b>7 个 logic 里只有 6 个</b>手写了 span
 * （{@code create_comment_logic.go:41} · {@code delete_comment_logic.go:37} ·
 * {@code manage_comment_logic.go:37} · {@code report_comment_logic.go:37} ·
 * {@code update_subject_state_logic.go:37} · {@code get_comment_logic.go:52}）；
 * {@code like_comment_logic.go} <b>没有</b>任何 otel / span 代码（实测命中数 = 0）。
 * ⇒ 本类<b>双向</b>钉死：有 span 的名字必须出现、<b>没 span 的方法必须不产生</b>对应 span
 * （否则 trace 里的 comment span 数会从 6 变 7，与真源不符）。
 *
 * <h2>🔴 三条「Go 侧的不一致」必须逐字保留（本类逐条断言）</h2>
 * <ol>
 *   <li><b>tracer 名不统一</b>：5 个 {@code Action-*} 用 {@code "comment-rpc"}，
 *       而 {@code GetComment} 用 {@code "comment.rpc"}（<b>带点</b>）⇒ 断言 instrumentation scope 名。</li>
 *   <li><b>属性前缀不统一</b>：5 个 {@code Action-*} 用 {@code audit.*}，
 *       而 {@code GetComment} 的 6 个属性<b>无前缀</b>
 *       ⇒ 断言 {@code GetComment} 的 span <b>有</b> {@code target_id}、
 *       <b>没有</b> {@code audit.target_id}。</li>
 *   <li><b>状态语义不统一</b>：Go 的 {@code span.RecordError(err)}（otel-go v1.x）<b>只加异常事件、不改状态</b>；
 *       而 {@code get_comment_logic.go} 的 4 个错误分支<b>额外显式</b>
 *       {@code span.SetStatus(codes.Error, err.Error())}
 *       ⇒ ⇒ <b>5 个 {@code Action-*} 的业务失败：既无异常事件、状态也不是 ERROR</b>
 *       （Go 的 {@code RecordError} 精确落在 {@code result = "sys_fail"} 分支，业务失败一律不记）；
 *       <b>{@code GetComment} 的系统失败：有异常事件 ＋ 状态 ERROR</b>。</li>
 * </ol>
 *
 * <h2>为什么用「业务失败」来验那 5 个 Action-* span</h2>
 * <p>5 个 {@code Action-*} span 在 Go 侧的属性是在 logic 开头就设的（{@code :52-60} 区间），
 * <b>早于</b>入参校验 ⇒ 所以「校验失败」这条路上 span 与属性<b>照样存在</b>
 * ⇒ 用它当探针既能拿到 span，<b>又不需要写库</b>（不碰 DB ⇒ 不受 PG 数据状态影响）。
 * <br>⚠️ 同时它正好能验第 3 条：Go 侧这些分支是 {@code biz_fail} ⇒ <b>不记错误</b>。
 */
@SpringBootTest(
        classes = CommentRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 🔴 **固定端口，不用 port=0**：port=0 会让 OS 从临时端口段（macOS 49152–65535）里挑，
                //    而那是 **Docker 随机发布容器端口用的同一段** ⇒ 别的会话的容器先占住 ⇒ EADDRINUSE
                //    ⇒ 整个测试类启动失败（表现为「偶尔红、换个时间又绿」的抖动）。
                //    21500–21599 段是 comment-rpc 的测试专用段（favorite 域占 21300–21399 / 21400–21499）。
                "grpc.server.port=21500",
                // 指到必然拒连的地址：NacosRegistration 捕获 NacosException 只记日志不中断启动。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）⇒ 必须给静态地址。
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                // OTLP 指向不可达端点，只会在后台重试刷日志，关掉（本类自装收集型 exporter）。
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka：consumer 会持续重试连接，但不影响上下文启动与本类断言。
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连（127.0.0.1:1 立即拒连）。
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                // 让判据**只**取决于「span 有没有真的产生」，不被 CPU 自适应降载抢先返回。
                "rag.protection.cpu-shedding.enabled=false"
        })
@Testcontainers
class CommentSpanWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("comment_rpc_span_wiring_it")
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
     * （引入它只为测试反而扩大依赖面）。
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
        // CommentSpans 用的是 GlobalOpenTelemetry.getTracer(...)，不装 SDK 就是 no-op（永远导不出 span）。
        // ⚠️ 共享层的 GoStyleOtelAgent.install 也会 GlobalOpenTelemetry.set(...)，
        //    但它**捕获** IllegalStateException（Go: startAgent 失败只 logx.Error、继续跑）⇒ 两边不打架。
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

    @Autowired private CommentDomainService domain;

    private static Optional<SpanData> findSpan(String name) {
        synchronized (EXPORTER.spans) {
            return EXPORTER.spans.stream().filter(s -> name.equals(s.getName())).findFirst();
        }
    }

    private static List<String> exportedSpanNames() {
        synchronized (EXPORTER.spans) {
            return EXPORTER.spans.stream().map(SpanData::getName).toList();
        }
    }

    // ====================================================================================
    // ① 5 个 Action-Comment-* span：名字 / tracer 名 / audit.* 属性 / 「业务失败不记错误」
    // ====================================================================================

    @Test
    @DisplayName("✅ createComment 真的导出 Action-Comment-Create（tracer=comment-rpc）＋ 5 个 audit.* 属性")
    void createCommentExportsGoSpanWithFiveAuditAttributes() {
        EXPORTER.clear();

        // 🔴 用「业务失败」当探针：Go 的属性在 logic 开头就设了（早于校验）⇒ span 与属性照样存在 ⇒ 不需要写库。
        assertThrows(RuntimeException.class, () -> domain.createComment(CreateCommentReq.newBuilder()
                        .setUserId(1101L)
                        .setTargetType("article")
                        .setTargetId("")                       // ← 故意为空 ⇒ 触发 Go :63 的 biz_fail 分支
                        .setRootId(2202L)
                        .setParentId(3303L)
                        .build()),
                "目标为空 ⇒ 必须失败（探针前提）");

        SpanData span = findSpan(CommentSpans.CREATE).orElseThrow(
                () -> new AssertionError("没有导出名为 " + CommentSpans.CREATE + " 的 span。实际导出的 = "
                        + exportedSpanNames()));

        assertEquals("comment-rpc", span.getInstrumentationScopeInfo().getName(),
                "Go: create_comment_logic.go:41 ⇒ otel.Tracer(\"comment-rpc\")");
        assertEquals(1101L, longAttr(span, CommentSpans.ATTR_OPERATOR_ID));
        assertEquals("article", stringAttr(span, CommentSpans.ATTR_TARGET_TYPE));
        assertEquals("", stringAttr(span, CommentSpans.ATTR_TARGET_ID));
        assertEquals(2202L, longAttr(span, CommentSpans.ATTR_ROOT_ID));
        assertEquals(3303L, longAttr(span, CommentSpans.ATTR_PARENT_ID));

        // 🔴 Go 的 RecordError 只落在 result="sys_fail" 分支 ⇒ 业务失败**不记错误、状态也不是 ERROR**
        assertTrue(span.getEvents().isEmpty(),
                "业务失败（biz_fail）在 Go 侧不 RecordError ⇒ 不该有异常事件。实际事件 = " + span.getEvents());
        assertEquals(io.opentelemetry.api.trace.StatusCode.UNSET, span.getStatus().getStatusCode(),
                "Go 的 RecordError 不改状态（otel-go v1.x）⇒ 状态应保持 UNSET");
    }

    @Test
    @DisplayName("✅ deleteComment 真的导出 Action-Comment-Delete ＋ 4 个 audit.* 属性")
    void deleteCommentExportsGoSpanWithFourAuditAttributes() {
        EXPORTER.clear();

        assertThrows(RuntimeException.class, () -> domain.deleteComment(DeleteCommentReq.newBuilder()
                        .setUserId(4404L)
                        .setCommentId(0L)                       // ← 故意为 0 ⇒ Go :58 的 biz_fail 分支
                        .setTargetType("article")
                        .setTargetId("t-1")
                        .build()),
                "评论 ID 为空 ⇒ 必须失败（探针前提）");

        SpanData span = findSpan(CommentSpans.DELETE).orElseThrow(
                () -> new AssertionError("没有导出名为 " + CommentSpans.DELETE + " 的 span。实际导出的 = "
                        + exportedSpanNames()));

        assertEquals("comment-rpc", span.getInstrumentationScopeInfo().getName());
        assertEquals(4404L, longAttr(span, CommentSpans.ATTR_OPERATOR_ID));
        assertEquals(0L, longAttr(span, CommentSpans.ATTR_COMMENT_ID));
        assertEquals("article", stringAttr(span, CommentSpans.ATTR_TARGET_TYPE));
        assertEquals("t-1", stringAttr(span, CommentSpans.ATTR_TARGET_ID));
        assertTrue(span.getEvents().isEmpty(), "业务失败在 Go 侧不 RecordError");
    }

    @Test
    @DisplayName("✅ manageComment 真的导出 Action-Comment-Manage ＋ audit.action_type＝枚举的 proto 值名")
    void manageCommentExportsGoSpanWithEnumValueName() {
        EXPORTER.clear();

        assertThrows(RuntimeException.class, () -> domain.manageComment(ManageCommentReq.newBuilder()
                        .setUserId(5505L)
                        .setCommentId(0L)                       // ← 故意为 0 ⇒ Go :59 的 biz_fail 分支
                        .setTargetType("article")
                        .setTargetId("t-1")
                        .setActionType(ManageType.MANAGE_PIN)
                        .build()),
                "评论 ID 为空 ⇒ 必须失败（探针前提）");

        SpanData span = findSpan(CommentSpans.MANAGE).orElseThrow(
                () -> new AssertionError("没有导出名为 " + CommentSpans.MANAGE + " 的 span。实际导出的 = "
                        + exportedSpanNames()));

        assertEquals("comment-rpc", span.getInstrumentationScopeInfo().getName());
        assertEquals(5505L, longAttr(span, CommentSpans.ATTR_OPERATOR_ID));
        assertEquals(0L, longAttr(span, CommentSpans.ATTR_COMMENT_ID));
        // 🔴 Go: manage_comment_logic.go:54 ⇒ attribute.String("audit.action_type", in.ActionType.String())
        //    ⇒ 值是**枚举的 proto 值名**（"MANAGE_PIN"），不是 Java 常量名、也不是数字。
        assertEquals("MANAGE_PIN", stringAttr(span, CommentSpans.ATTR_ACTION_TYPE));
        assertEquals("article", stringAttr(span, CommentSpans.ATTR_TARGET_TYPE));
        assertEquals("t-1", stringAttr(span, CommentSpans.ATTR_TARGET_ID));
    }

    @Test
    @DisplayName("✅ reportComment 真的导出 Action-Comment-Report ＋ audit.reason＝枚举的 proto 值名")
    void reportCommentExportsGoSpanWithReasonValueName() {
        EXPORTER.clear();

        assertThrows(RuntimeException.class, () -> domain.reportComment(ReportCommentReq.newBuilder()
                        .setUserId(6606L)
                        .setCommentId(0L)                       // ← 故意为 0 ⇒ Go :62 的 biz_fail 分支
                        .setTargetType("article")
                        .setTargetId("t-1")
                        .setReason(ReportReason.REASON_SPAM_ADVERT)
                        .build()),
                "评论 ID 为空 ⇒ 必须失败（探针前提）");

        SpanData span = findSpan(CommentSpans.REPORT).orElseThrow(
                () -> new AssertionError("没有导出名为 " + CommentSpans.REPORT + " 的 span。实际导出的 = "
                        + exportedSpanNames()));

        assertEquals("comment-rpc", span.getInstrumentationScopeInfo().getName());
        assertEquals(6606L, longAttr(span, CommentSpans.ATTR_OPERATOR_ID));
        assertEquals(0L, longAttr(span, CommentSpans.ATTR_COMMENT_ID));
        // 🔴 Go: report_comment_logic.go:57 ⇒ attribute.String("audit.reason", in.Reason.String())
        assertEquals("REASON_SPAM_ADVERT", stringAttr(span, CommentSpans.ATTR_REASON));
        assertEquals("article", stringAttr(span, CommentSpans.ATTR_TARGET_TYPE));
        assertEquals("t-1", stringAttr(span, CommentSpans.ATTR_TARGET_ID));
    }

    @Test
    @DisplayName("✅ updateSubjectState 真的导出 Action-Comment-UpdateSubjectState ＋ audit.target_state＝枚举的【编号】")
    void updateSubjectStateExportsGoSpanWithStateNumber() {
        EXPORTER.clear();

        assertThrows(RuntimeException.class, () -> domain.updateSubjectState(UpdateSubjectStateReq.newBuilder()
                        .setUserId(7707L)
                        .setTargetType("")                     // ← 故意为空 ⇒ Go :61 的 biz_fail 分支
                        .setTargetId("t-1")
                        .setState(com.rag.comment.comment.proto.SubjectState.SUBJECT_CLOSED)
                        .build()),
                "目标为空 ⇒ 必须失败（探针前提）");

        SpanData span = findSpan(CommentSpans.UPDATE_SUBJECT_STATE).orElseThrow(
                () -> new AssertionError("没有导出名为 " + CommentSpans.UPDATE_SUBJECT_STATE + " 的 span。实际导出的 = "
                        + exportedSpanNames()));

        assertEquals("comment-rpc", span.getInstrumentationScopeInfo().getName());
        assertEquals(7707L, longAttr(span, CommentSpans.ATTR_OPERATOR_ID));
        assertEquals("", stringAttr(span, CommentSpans.ATTR_TARGET_TYPE));
        assertEquals("t-1", stringAttr(span, CommentSpans.ATTR_TARGET_ID));
        // 🔴 Go: update_subject_state_logic.go:56 ⇒ attribute.Int64("audit.target_state", int64(in.State))
        //    ⇒ 值是**枚举的编号**（SUBJECT_CLOSED = 1），**不是**名字。
        assertEquals(1L, longAttr(span, CommentSpans.ATTR_TARGET_STATE),
                "audit.target_state 必须是枚举编号（int64(in.State)），不是名字");
    }

    // ====================================================================================
    // ② GetComment：tracer 名带点 + 6 个无前缀属性 + 系统失败才有异常事件与 ERROR 状态
    // ====================================================================================

    @Test
    @DisplayName("✅ getComment 真的导出 GetComment（tracer=comment.rpc，带点）＋ 6 个【无前缀】属性 ＋ 系统失败 ⇒ 异常事件 + ERROR 状态")
    void getCommentExportsGoSpanWithUnprefixedAttributes() {
        EXPORTER.clear();

        // 评论区不存在 ⇒ Go 的 loadSubject 失败分支（get_comment_logic.go:85-86）⇒ RecordError + SetStatus(Error)
        assertThrows(RuntimeException.class, () -> domain.getComment(GetCommentReq.newBuilder()
                        .setTargetType("post")                   // ← 不是 "article" ⇒ 回落分支直接返回 null，不去打 RPC
                        .setTargetId("span-probe-missing-subject")
                        .setRootId(8808L)
                        .setPage(3)
                        .setPageSize(25)
                        .setSortType(7)
                        .build()),
                "评论区不存在 ⇒ 必须失败（探针前提）");

        SpanData span = findSpan(CommentSpans.GET_COMMENT).orElseThrow(
                () -> new AssertionError("没有导出名为 " + CommentSpans.GET_COMMENT + " 的 span。实际导出的 = "
                        + exportedSpanNames()));

        // 🔴 Go: get_comment_logic.go:52 ⇒ otel.Tracer("comment.rpc") —— **带点**，与那 5 个不同
        assertEquals("comment.rpc", span.getInstrumentationScopeInfo().getName(),
                "Go 的 GetComment 用的是另一个 tracer 名（带点）⇒ 不得被「统一」成 comment-rpc");

        assertEquals("span-probe-missing-subject", stringAttr(span, CommentSpans.ATTR_LIST_TARGET_ID));
        assertEquals(8808L, longAttr(span, CommentSpans.ATTR_LIST_ROOT_ID));
        // ⚠️ Go :72-73 记的是**原始入参**（in.Page / in.PageSize），不是归一后的值
        assertEquals(3L, longAttr(span, CommentSpans.ATTR_LIST_PAGE));
        assertEquals(25L, longAttr(span, CommentSpans.ATTR_LIST_PAGE_SIZE));
        assertEquals("post", stringAttr(span, CommentSpans.ATTR_LIST_TARGET_TYPE));
        assertEquals(7L, longAttr(span, CommentSpans.ATTR_LIST_SORT_TYPE));

        // 🔴 判别式：这 6 个属性**没有 audit. 前缀** ⇒ span 里不该出现 audit.target_id
        assertTrue(span.getAttributes().asMap().keySet().stream()
                        .noneMatch(k -> k.getKey().startsWith("audit.")),
                "GetComment 的 6 个属性在 Go 侧**没有 audit. 前缀** ⇒ span 里不该出现任何 audit.* 属性。实际 = "
                        + span.getAttributes());

        // 🔴 Go 的这 4 个错误分支额外显式 SetStatus(Error) ⇒ 状态必须是 ERROR，且有异常事件
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.getStatus().getStatusCode(),
                "Go: get_comment_logic.go:86 ⇒ span.SetStatus(codes.Error, err.Error()) ⇒ 状态必须是 ERROR");
        assertTrue(span.getEvents().stream().anyMatch(e -> "exception".equals(e.getName())),
                "Go 的系统失败分支会 RecordError ⇒ 必须有 exception 事件。实际事件 = " + span.getEvents());
    }

    // ====================================================================================
    // ③ 双向钉死：likeComment **不产生** span（Go 侧没有 ⇒ 不许凭空加）
    // ====================================================================================

    @Test
    @DisplayName("✅ likeComment 【不产生】任何 span —— Go 的 like_comment_logic.go 根本没有 otel 代码")
    void likeCommentMustNotProduceSpan() {
        EXPORTER.clear();

        assertThrows(RuntimeException.class, () -> domain.likeComment(
                        com.rag.comment.comment.proto.LikeCommentReq.newBuilder()
                                .setUserId(9909L)
                                .setCommentId(0L)
                                .build()),
                "评论 ID 为空 ⇒ 必须失败（探针前提）");

        List<String> names = exportedSpanNames();
        assertTrue(names.stream().noneMatch(n -> n.contains("Like")),
                "Go 的 like_comment_logic.go 没有 tracer.Start ⇒ 不该产生任何含 Like 的 span。实际导出的 = " + names);
    }

    // ====================================================================================

    private static Long longAttr(SpanData span, String key) {
        Long v = span.getAttributes().get(AttributeKey.longKey(key));
        assertNotNull(v, "span " + span.getName() + " 缺少属性 " + key
                + "（实际属性 = " + span.getAttributes() + "）");
        return v;
    }

    private static String stringAttr(SpanData span, String key) {
        String v = span.getAttributes().get(AttributeKey.stringKey(key));
        assertNotNull(v, "span " + span.getName() + " 缺少属性 " + key
                + "（实际属性 = " + span.getAttributes() + "）");
        return v;
    }
}
