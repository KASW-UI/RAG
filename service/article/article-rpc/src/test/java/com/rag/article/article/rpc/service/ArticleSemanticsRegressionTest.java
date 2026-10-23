package com.rag.article.article.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.UpdateArticleRequest;
import com.rag.article.article.rpc.client.ContentSecurityPort;
import com.rag.article.article.rpc.client.MessageRpcClient;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.exception.ArticleExceptions;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.metrics.ArticleRpcMetrics;
import com.rag.article.article.rpc.mq.ArticleReviewConsumer;
import com.rag.article.article.rpc.service.impl.ArticleServiceGrpcImpl;
import com.rag.common.spring.SnowflakeBean;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 本次组件级语义验证后补的<b>单元级回归网</b> —— 每条锁一个曾经真实存在的漂移。
 *
 * <p>🔴 <b>这些用例守的是「Go 的行为」，不是「RAG 现在的行为」</b>。
 * 上一版的 79 条用例里至少三处守的是错误答案（见
 * {@code component-semantic-verification/semantic-drifts.md} §「对既有 79 条测试的判定」），
 * 修复时会先撞上它们 —— 本类就是把那三条的错误断言改对之后的结果。
 *
 * <p>需要<b>真容器 / 真代理</b>才能证伪的那几条（S-1、S-2）不在本类，
 * 在 {@code ArticleTransactionWiringTest}（真 PostgreSQL）与
 * {@code ArticleSpringContextWiringTest}（真 Spring 上下文）。
 */
class ArticleSemanticsRegressionTest {

    // ==================================================================
    // S-6：StatusRuntimeException 必须透传，不能被降级成 UNKNOWN
    // ==================================================================

    @Test
    @DisplayName("🔴 S-6: 已经带 status 的异常原样透传（DEADLINE_EXCEEDED 不能变 UNKNOWN）")
    void statusRuntimeExceptionKeepsItsCode() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        StreamObserver<Object> observer = capturingObserver(captured);

        ArticleDomainService domain = mock(ArticleDomainService.class);
        when(domain.getArticle(any())).thenThrow(
                Status.DEADLINE_EXCEEDED.withDescription("SQL canceled: 57014").asRuntimeException());

        new ArticleServiceGrpcImpl(domain).getArticle(GetArticleRequest.newBuilder().setArticleId("a-1").build(),
                castObserver(observer));

        assertNotNull(captured.get(), "必须把错误交给 observer，而不是吞掉");
        assertInstanceOf(StatusRuntimeException.class, captured.get());
        StatusRuntimeException sre = (StatusRuntimeException) captured.get();
        assertEquals(Status.Code.DEADLINE_EXCEEDED, sre.getStatus().getCode(),
                "SqlTimeoutStatusTranslator 辛苦翻出来的 DEADLINE_EXCEEDED 被在 gRPC 边界抹成了 UNKNOWN。"
                        + "Go 侧公开读路径的 DB 失败是 codes.Internal ⇒ 业务码 5001；"
                        + "改成 UNKNOWN 后落 api 的 default ⇒ 业务码 1015，两套告警口径混用");
    }

    @Test
    @DisplayName("S-6: 非 status 的 RuntimeException 仍然一律 UNKNOWN（不要把这条也改掉）")
    void plainRuntimeExceptionStillMapsToUnknown() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        ArticleDomainService domain = mock(ArticleDomainService.class);
        when(domain.getArticle(any())).thenThrow(new IllegalStateException("bug"));

        new ArticleServiceGrpcImpl(domain).getArticle(GetArticleRequest.newBuilder().setArticleId("a-1").build(),
                castObserver(capturingObserver(captured)));

        assertEquals(Status.Code.UNKNOWN,
                ((StatusRuntimeException) captured.get()).getStatus().getCode(),
                "Go 的「非 status error 一律 UNKNOWN」规则必须保留 —— 对应 grpc-go status.FromError 的 else 分支");
    }

    @Test
    @DisplayName("S-6: ArticleExceptions.Rpc 仍按自带 code 转发（NOT_FOUND 不受影响）")
    void rpcExceptionKeepsItsCode() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        ArticleDomainService domain = mock(ArticleDomainService.class);
        when(domain.getArticle(any())).thenThrow(ArticleExceptions.articleNotFound());

        new ArticleServiceGrpcImpl(domain).getArticle(GetArticleRequest.newBuilder().setArticleId("a-1").build(),
                castObserver(capturingObserver(captured)));

        assertEquals(Status.Code.NOT_FOUND,
                ((StatusRuntimeException) captured.get()).getStatus().getCode());
        assertEquals("article not found",
                ((StatusRuntimeException) captured.get()).getStatus().getDescription());
    }

    // ==================================================================
    // O-1：Update / Delete 的 not-found 文案要与 Go 逐字一致
    // ==================================================================

    @Test
    @DisplayName("O-1: update 一个不存在的 id ⇒ UNKNOWN + \"record not found\"（Go gorm.ErrRecordNotFound 的字面量）")
    void updateMissingArticleUsesGoWording() {
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        when(articleMapper.selectById("nope")).thenReturn(null);

        AtomicReference<Throwable> captured = new AtomicReference<>();
        ArticleServiceGrpcImpl impl = new ArticleServiceGrpcImpl(
                newDomainService(articleMapper, mock(ArticleAtomicService.class),
                        mock(ArticlePublicDomainService.class)));

        impl.updateArticle(UpdateArticleRequest.newBuilder().setArticleId("nope").build(),
                castObserver(capturingObserver(captured)));

        assertNotNull(captured.get());
        // Go 侧 model/article.go:83-86 直接返回 GORM 的 error，其 Error() 字面量就是这句
        // ⚠️ 断言的是 **gRPC description**（上线后客户端看到的字段），
        //    不是 Throwable.getMessage()（它会自动前缀 "UNKNOWN: "）。
        assertEquals("record not found",
                ((StatusRuntimeException) captured.get()).getStatus().getDescription(),
                "Go 侧是 gorm.ErrRecordNotFound.Error()；此前这里写的是 \"article not found: <id>\"，"
                        + "既与 Go 不一致，又把请求里的 id 泄进了错误文案");
        assertEquals(Status.Code.UNKNOWN,
                ((StatusRuntimeException) captured.get()).getStatus().getCode(),
                "必须是 UNKNOWN（Go 的裸 error 经 grpc-go 转后的结果），"
                        + "不是 NOT_FOUND（articleNotFound()）—— Go 的 update/delete 这两处根本没走 NotFound 分支");
    }

    // ==================================================================
    // O-3：DB 失败不得记成 MinIO 错误
    // ==================================================================

    @Test
    @DisplayName("O-3: 作者视角计阅读量失败 ⇒ 仍返回成功，且不记 minio_request_errors_total")
    void incrViewFailureIsSilentAndDoesNotTouchMinioMetrics() {
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ArticleEntity article = new ArticleEntity();
        article.setId("a-1");
        article.setTitle("t");
        article.setStatus(3);
        article.setViewCount(5);
        article.setContent("a-1.md");
        when(articleMapper.selectById("a-1")).thenReturn(article);
        when(articleMapper.incrViewCount("a-1")).thenThrow(new RuntimeException("db down"));

        PrometheusMeterRegistry registry = prometheusRegistry();
        ArticleRpcMetrics metrics = new ArticleRpcMetrics(registry);

        ArticleDomainService domain = newDomainService(articleMapper, mock(ArticleAtomicService.class),
                mock(ArticlePublicDomainService.class), metrics);

        var resp = domain.getArticle(GetArticleRequest.newBuilder()
                .setArticleId("a-1").setIncrView(true).build());

        // best-effort：Go get_article_logic.go:70-78 失败只记日志，仍返回成功
        assertNotNull(resp, "DB 计数失败不得让整个请求失败（Go 是 best-effort）");
        assertEquals(6, resp.getArticle().getViewCount(), "内存里仍 +1（与 Go article.ViewCount++ 一致）");
        assertEquals(0.0, counterValue(registry, "article_service_minio_request_errors", "operation", "get"),
                0.0, "这是一次 **DB 写失败**，不是 MinIO 失败。"
                        + "记到 minio_request_errors_total{get} 会让「对象存储是否故障」这个信号失真，"
                        + "而 Go 侧这条路径该计数恒为 0");
    }

    // ==================================================================
    // O-2：Prometheus 指标全名必须与 Go 逐条一致
    // ==================================================================

    @Test
    @DisplayName("O-2: 6 个指标的 Prometheus 全名与 Go metrics.go 逐条一致")
    void prometheusNamesMatchGoExactly() {
        PrometheusMeterRegistry registry = prometheusRegistry();
        ArticleRpcMetrics metrics = new ArticleRpcMetrics(registry);

        metrics.article("create");
        metrics.articleStatus("reviewing");
        metrics.fileUpload("markdown");
        metrics.minioError("get");
        metrics.kafkaPushError("article_review");
        metrics.observeMinio("put", 12_000_000L);

        List<String> exported = registry.scrape().lines()
                .filter(l -> l.startsWith("# TYPE"))
                .map(l -> l.split("\\s+")[2])
                .distinct()
                .sorted()
                .toList();

        // Go 侧 prometheus.Namespace = "article_service"（metrics.go:6）是**指标名的前缀**
        List<String> expected = new ArrayList<>(List.of(
                "article_service_article_total",
                "article_service_article_status_total",
                "article_service_file_upload_total",
                "article_service_kafka_push_errors_total",
                "article_service_minio_request_errors_total",
                "article_service_minio_request_duration_seconds"));
        java.util.Collections.sort(expected);   // Prometheus 的 TYPE 行本身按字典序输出

        assertTrue(exported.containsAll(expected),
                "Go 的 6 个全名必须全部存在。实际输出\n" + String.join("\n", exported)
                        + "\nPrometheus 抓的是**全名**，任何差异都会让既有 dashboard / 告警静默失效"
                        + "（不报错，只是没有数据）。Micrometer 不提供前缀机制，"
                        + "它按 Meter.Type 补后缀（Counter→_total / Timer→_seconds），"
                        + "所以 meter name 必须写掉这个后缀；此前写的是 article_total / "
                        + "minio_request_duration_seconds，实际输出 article_total_total / "
                        + "minio_request_duration_seconds_seconds —— 6 个全部改名。");

        // 多出来的只能是 Micrometer 额外生成的时钟子系列（新增的 series，不影响 Go 名的查询）
        List<String> extra = new ArrayList<>(exported);
        extra.removeAll(expected);
        assertEquals(List.of("article_service_minio_request_duration_seconds_max"), extra,
                "除 Go 的 6 个全名之外，只允许 Micrometer 时钟的 _max 族表达，"
                        + "其它多出来的序列都是未登记的新增量");
    }

    @Test
    @DisplayName("O-2: article_status_total 仍然只有 reviewing（不许补 published/rejected）")
    void articleStatusMetricStaysIncomplete() {
        PrometheusMeterRegistry registry = prometheusRegistry();
        ArticleRpcMetrics metrics = new ArticleRpcMetrics(registry);

        metrics.articleStatus("reviewing");

        assertEquals(1.0, counterValue(registry, "article_service_article_status", "status", "reviewing"), 0.0);
        assertNull(registry.find("article_service_article_status")
                        .tag("status", "published").counter(),
                "Go 侧 published / rejected 没有任何 +1 的调用点（metrics.go:27 只是注释），"
                        + "补全属于「新增 Go 侧不存在的埋点」");
    }

    // ==================================================================
    // U-7：直方图的**分桶边界**必须与 Go 逐条一致（此前只对了指标名）
    // ==================================================================

    /**
     * 🔴 U-7 —— MinIO 耗时的直方图<b>分桶边界</b>必须与 Go 的 8 个桶逐条一致。
     *
     * <h2>Go 出处（消费点）</h2>
     * <pre>
     * service/article/rpc/metrics/metrics.go:41-47
     *   MinioRequestDuration = prometheus.NewHistogramVec(
     *     prometheus.HistogramOpts{
     *       Namespace: Namespace, Name: "minio_request_duration_seconds",
     *       Buckets:   []float64{0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1},
     *     }, []string{"operation"})
     * </pre>
     * ⇒ Go 的分桶边界**就是这 8 个**（外加 Prometheus 经典直方图固有的 {@code +Inf}）。
     *
     * <h2>⚠️ 此前为什么"看起来是对的"</h2>
     * {@code ArticleRpcMetrics} 同时写了
     * {@code .publishPercentileHistogram()} 与 {@code .serviceLevelObjectives(...)}，
     * 注释还断言"换算后数值一致，因此两侧的分桶边界相同"。
     * <br>🔴 <b>这个断言是错的</b>：Micrometer 的
     * {@code DistributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles)}
     * （micrometer-core 1.16.7）取的是<b>并集</b>：
     * <pre>
     * if (percentileHistogram != null &amp;&amp; percentileHistogram &amp;&amp; supportsAggregablePercentiles) {
     *     buckets.addAll(PercentileHistogramBuckets.buckets(this));   // 默认百分位桶
     *     buckets.add(minimumExpectedValue);
     *     buckets.add(maximumExpectedValue);
     * }
     * if (serviceLevelObjectives != null) { for (...) buckets.add(sloBoundary); }  // ← 只是**再加**
     * </pre>
     * ⇒ {@code publishPercentileHistogram()} <b>不是</b>"用 SLO 桶"，
     * 而是"<b>额外</b>塞进一整套默认百分位桶" ⇒ 实际桶数远多于 Go 的 8 个。
     *
     * <h2>为什么这条必须用<b>真抓取</b>断言</h2>
     * 桶集是 Micrometer 内部算出来的（{@code DistributionStatisticConfig} ⇒
     * {@code PercentileHistogramBuckets} ⇒ 注册表的 exposition），
     * <b>源码层面推不出最终结果</b> ⇒ 必须抓一次真实的 Prometheus 文本。
     */
    @Test
    @DisplayName("🔴 U-7: MinIO 耗时的直方图分桶边界必须与 Go 的 8 个桶逐条一致")
    void minioHistogramBucketsMatchGoExactly() {
        PrometheusMeterRegistry registry = prometheusRegistry();
        ArticleRpcMetrics metrics = new ArticleRpcMetrics(registry);

        metrics.observeMinio("put", 12_000_000L);   // 12ms ⇒ 落在 0.01 与 0.025 之间

        List<String> bucketLines = registry.scrape().lines()
                .filter(l -> l.startsWith("article_service_minio_request_duration_seconds_bucket"))
                .toList();

        List<Double> actual = new ArrayList<>();
        for (String line : bucketLines) {
            int at = line.indexOf("le=\"");
            String le = line.substring(at + 4, line.indexOf('"', at + 4));
            // ⚠️ Prometheus 的 +Inf 桶标签是 "+Inf"，Double.parseDouble 不认
            actual.add("+Inf".equals(le) ? Double.POSITIVE_INFINITY : Double.parseDouble(le));
        }
        java.util.Collections.sort(actual);

        // Go 侧 8 个桶 + Prometheus 经典直方图固有的 +Inf
        List<Double> expected = new ArrayList<>(List.of(
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, Double.POSITIVE_INFINITY));

        assertEquals(expected, actual,
                "分桶边界必须与 Go metrics.go:46 逐条一致（8 个桶 + 固有的 +Inf）。"
                        + "多出来的桶会让 histogram_quantile 的结果与迁移前不同，"
                        + "也会让这条指标的 series 基数翻几倍");
    }

    // ==================================================================
    // S-5：content security 端口必须能表达 Go 的 !success 分支
    // ==================================================================

    @Test
    @DisplayName("🔴 S-5: security 返回 success=false ⇒ 抛错重投，绝不当成「不是广告」放行")
    void securityFailureIsNeverTreatedAsNotAd() {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-1");
        article.setAuthorId("1001");
        article.setStatus(3);
        article.setContent("a-1.md");
        article.setExtInfo(new java.util.LinkedHashMap<>(java.util.Map.of("pending_sync_reason", "create")));

        ContentSecurityPort failing = new ContentSecurityPort() {
            @Override
            public TextAuditResult sanitizeText(String text) {
                // Go article_consumer.go:171-173：调用成功但 Success=false ⇒ return err
                return new TextAuditResult(false, false, 0f, null, "model overloaded");
            }

            @Override
            public ImageAuditResult detectImageAd(String imageDataUri) {
                return new ImageAuditResult(false, false, 0f);
            }
        };
        ArticleMapper articleMapper = mock(ArticleMapper.class);

        ArticleReviewConsumer consumer = new ArticleReviewConsumer(new ObjectMapper(), articleMapper,
                mock(ArticleAtomicService.class), mock(com.rag.article.article.rpc.client.ArticleObjectStorageClient.class),
                failing, new ArticleRpcMetrics(new SimpleMeterRegistry()), mock(SnowflakeBean.class));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> invokeAudit(consumer, article, "# body", "1001"),
                "security 返回 success=false 时必须抛错（⇒ 整篇重投），"
                        + "不得当作 isAd=false 放行 —— 那是 fail-open");
        assertTrue(e.getMessage().contains("model overloaded"),
                "错误文案应带上服务返回的 errorMessage（Go 同）");

        assertEquals(3, article.getStatus(),
                "抛错时状态必须仍是 REVIEWING：不得改成 REJECTED（那是「内容违规」，不是「服务故障」）");
    }

    @Test
    @DisplayName("S-5: 图片审核返回 success=false ⇒ 抛错重投，不跳过这张图继续审下一张")
    void imageSecurityFailureIsNeverSkipped() {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-1");
        article.setAuthorId("1001");
        article.setStatus(3);
        article.setContent("a-1.md");
        article.setExtInfo(new java.util.LinkedHashMap<>());

        AtomicReference<String> uri = new AtomicReference<>();
        ContentSecurityPort failing = new ContentSecurityPort() {
            @Override
            public TextAuditResult sanitizeText(String text) {
                return new TextAuditResult(true, false, 0f, null, null);
            }

            @Override
            public ImageAuditResult detectImageAd(String imageDataUri) {
                uri.set(imageDataUri);
                return new ImageAuditResult(false, false, 0f);   // Go :263-265 ⇒ error
            }
        };
        var storage = mock(com.rag.article.article.rpc.client.ArticleObjectStorageClient.class);
        try {
            // buildImageDataUri 会依次调 storage.get(...) 与 storage.readAll(...)，两个都要桩
            org.mockito.Mockito.doReturn(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}))
                    .when(storage).get(anyString());
            org.mockito.Mockito.doReturn(new byte[]{1, 2, 3}).when(storage).readAll(any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        ArticleReviewConsumer consumer = new ArticleReviewConsumer(new ObjectMapper(),
                mock(ArticleMapper.class), mock(ArticleAtomicService.class), storage,
                failing, new ArticleRpcMetrics(new SimpleMeterRegistry()), mock(SnowflakeBean.class));

        assertThrows(RuntimeException.class,
                () -> invokeAudit(consumer, article, "![x](http://h/articles/images/1.png)", "1001"),
                "Go :263-265 的 !resp.GetSuccess() 分支必须存在，否则会静默跳过这张图继续审下一张");
        // Go article_consumer.go:244-250：.png → image/png；其余（含 .webp 以外）一律 image/jpeg
        assertEquals("data:image/png;base64,AQID", uri.get(),
                "顺带确认 dataURI 构造仍与 Go 一致（.png → image/png）");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private static PrometheusMeterRegistry prometheusRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    /** {@code tags} 为 key/value 交替序列，与 Micrometer {@code Search#tag(String, String)} 对应。 */
    private static double counterValue(PrometheusMeterRegistry registry, String name, String... tags) {
        io.micrometer.core.instrument.search.Search search = registry.find(name);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            search = search.tag(tags[i], tags[i + 1]);
        }
        io.micrometer.core.instrument.Counter c = search.counter();
        return c == null ? 0.0 : c.count();
    }

    private static StreamObserver<Object> capturingObserver(AtomicReference<Throwable> sink) {
        return new StreamObserver<>() {
            @Override
            public void onNext(Object value) {
            }

            @Override
            public void onError(Throwable t) {
                sink.set(t);
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> StreamObserver<T> castObserver(StreamObserver<Object> o) {
        return (StreamObserver<T>) o;
    }

    private static ArticleDomainService newDomainService(ArticleMapper mapper,
                                                          ArticleAtomicService atomic,
                                                          ArticlePublicDomainService publicService) {
        return newDomainService(mapper, atomic, publicService, new ArticleRpcMetrics(new SimpleMeterRegistry()));
    }

    private static ArticleDomainService newDomainService(ArticleMapper mapper,
                                                          ArticleAtomicService atomic,
                                                          ArticlePublicDomainService publicService,
                                                          ArticleRpcMetrics metrics) {
        return new ArticleDomainService(mapper,
                mock(com.rag.article.article.rpc.client.ArticleObjectStorageClient.class),
                atomic, publicService, metrics,
                mock(SnowflakeBean.class),
                new com.rag.article.article.rpc.config.ArticleRpcProperties(),
                mock(org.springframework.kafka.core.KafkaTemplate.class));
    }


    /**
     * {@code auditArticle} 是 package-private（刻意如此），本类在 {@code service} 包下，
     * 故用反射调一次。生产代码不可见性不改 —— 它本来就不该是 public。
     */
    private static void invokeAudit(ArticleReviewConsumer consumer, ArticleEntity article,
                                   String markdown, String authorId) {
        try {
            java.lang.reflect.Method m =
                    ArticleReviewConsumer.class.getDeclaredMethod("auditArticle",
                            ArticleEntity.class, String.class, String.class);
            m.setAccessible(true);
            m.invoke(consumer, article, markdown, authorId);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("auditArticle 方法签名变了？", e);
        }
    }

    /** 让 {@code Meter} / {@code ArrayList} 的 import 不被误删（保持 import 区稳定）。 */
    @Test
    @DisplayName("(占位) import 稳定性自检")
    void importSanity() {
        assertTrue(new ArrayList<String>().isEmpty());
        assertNotNull(Meter.Type.COUNTER);
    }
}