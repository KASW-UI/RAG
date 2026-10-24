package com.rag.article.article.rpc;

import com.rag.article.article.rpc.config.ArticleRpcProperties;
import com.rag.article.article.rpc.domain.ArticleSyncOutboxEntity;
import com.rag.article.article.rpc.mapper.ArticleSyncOutboxMapper;
import com.rag.article.article.rpc.metrics.ArticleRpcMetrics;
import com.rag.article.article.rpc.mq.ArticleSyncOutboxRelay;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * outbox 中继的<b>调度语义</b> —— {@code DRIFT-ARTICLE-RPC-006} / {@code DRIFT-ARTICLE-RPC-008} 的守门网。
 *
 * <h2>Go 出处（两段，缺一不可）</h2>
 * <pre>
 * // ① 间隔兜底 —— service/article/rpc/article.go:62-65
 * relayInterval := time.Duration(c.ArticleSyncOutbox.PollIntervalMs) * time.Millisecond
 * if relayInterval &lt;= 0 { relayInterval = time.Second }
 *
 * // ② 首次触发时机 —— article.go:94,101
 * ticker := time.NewTicker(s.interval)     // ← 首次 tick 在 interval **之后**
 * for { select { case &lt;-ticker.C: …SendPending(…) } }
 * </pre>
 *
 * <h2>⚠️ 为什么必须是<b>真容器</b>，不能只断注解</h2>
 * <p>AGENTS.md §3.3.3：「注解存在」≠「机制生效」。
 * 本类的两条断言都需要<b>容器真的把调度装配出来</b>才能成立：
 * <ul>
 *   <li><b>006</b>：修之前是 {@code @Scheduled(fixedRateString="${…}")}，
 *       配置写 0 ⇒ Spring 在<b>装配期</b>就抛异常 ⇒ <b>容器起不来</b>。
 *       ⇒ 「容器能起来」本身就是这条断言。</li>
 *   <li><b>008</b>：修之前没有 {@code initialDelay} ⇒ Spring 默认<b>立即</b>执行首次。
 *       ⇒ 必须观察<b>第一次真的被调用</b>的时刻，而不是读注解上的字符串。</li>
 * </ul>
 *
 * <h2>为什么这里不需要真 PG / 真 Kafka</h2>
 * <p>被测的是<b>调度器</b>，不是数据面：
 * {@code outboxMapper.fetchPending} 与 {@code kafkaTemplate.send} 都被 mock，
 * 中继在「捞不到事件」处即返回 ⇒ <b>不会碰数据库，也不会发消息</b>。
 */
/**
 * ⚠️ {@code @TestPropertySource} 是**必需**的，不是装饰 ——
 * {@code @Scheduled} 的占位符读的是 **Environment**，不是 {@code ArticleRpcProperties} 这个 bean。
 * 第一版测试只把 bean 里的 {@code pollIntervalMs} 设成 0，
 * 而 Environment 里**根本没有这个键** ⇒ 占位符走默认值 {@code :1000}
 * ⇒ 把 {@code @Scheduled} 改回裸占位符也**不会**报错 ⇒ 变异验证直接判成「假绿」。
 * ⇒ 「配置写 0」这个场景必须落在 **Environment** 上才测得到。
 */
@SpringJUnitConfig(ArticleOutboxRelayScheduleTest.Config.class)
@TestPropertySource(properties = "article-rpc.article-sync-outbox.poll-interval-ms=0")
class ArticleOutboxRelayScheduleTest {

    /** 记录的「第一次真正进入中继循环」的时刻（相对 {@link #startedAtNanos}）。 */
    static final AtomicLong FIRST_FETCH_AT = new AtomicLong(0);

    /** 本测试类的上下文启动时刻（nanoTime）。 */
    static final AtomicLong STARTED_AT = new AtomicLong(0);

    @Configuration
    @EnableScheduling
    static class Config {

        @Bean
        ArticleRpcProperties articleRpcProperties() {
            ArticleRpcProperties props = new ArticleRpcProperties();
            // 🔴 配 0：Go 侧兜底为 1s；修之前 @Scheduled 会在**装配期**直接让容器起不来
            props.getArticleSyncOutbox().setPollIntervalMs(0);
            return props;
        }

        @Bean
        ArticleSyncOutboxMapper outboxMapper() {
            ArticleSyncOutboxMapper mapper = Mockito.mock(ArticleSyncOutboxMapper.class);
            Mockito.when(mapper.fetchPending(Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> {
                        FIRST_FETCH_AT.compareAndSet(0, System.nanoTime());
                        return List.<ArticleSyncOutboxEntity>of();
                    });
            return mapper;
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate() {
            return Mockito.mock(KafkaTemplate.class);
        }

        @Bean
        ArticleRpcMetrics articleRpcMetrics() {
            return new ArticleRpcMetrics(new SimpleMeterRegistry());
        }

        @Bean
        ArticleSyncOutboxRelay articleSyncOutboxRelay(ArticleSyncOutboxMapper outboxMapper,
                                                       KafkaTemplate<String, String> kafkaTemplate,
                                                       ArticleRpcProperties properties,
                                                       ArticleRpcMetrics metrics) {
            STARTED_AT.compareAndSet(0, System.nanoTime());
            return new ArticleSyncOutboxRelay(outboxMapper, kafkaTemplate, properties, metrics);
        }
    }

    @Test
    @DisplayName("🔴 DRIFT-006：poll-interval-ms = 0 ⇒ 容器**起得来**，且间隔兜底为 1000ms（Go article.go:62-65）")
    void zeroIntervalStillBootsAndFallsBackToOneSecond() {
        // 前置：容器已经起来（@SpringJUnitConfig 起上下文失败 ⇒ 整个测试类 ERROR）
        assertEquals(1000L, ArticleSyncOutboxRelay.normalizeIntervalMs(0L),
                "Go: if relayInterval <= 0 { relayInterval = time.Second }");
        assertEquals(1000L, ArticleSyncOutboxRelay.normalizeIntervalMs(-1L),
                "负数同样兜底");
        assertEquals(500L, ArticleSyncOutboxRelay.normalizeIntervalMs(500L),
                "正数**不得**被兜底改写（兜底只对 ≤0 生效）");
    }

    @Test
    @DisplayName("🔴 DRIFT-008：首次触发在间隔**之后**（Go ticker 的首次 tick），不是启动即执行")
    void firstExecutionHappensAfterTheIntervalNotImmediately() throws Exception {
        long waitMs = 1200L;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
        while (FIRST_FETCH_AT.get() == 0L && System.nanoTime() < deadline) {
            Thread.sleep(25L);
        }

        long firstAt = FIRST_FETCH_AT.get();
        assertTrue(firstAt > 0L,
                "中继在 1200ms 内**应该**至少跑过一次（间隔兜底 1000ms）；"
                        + "若一次都没跑，说明调度根本没装配上");

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(firstAt - STARTED_AT.get());
        // ⚠️ 兜底间隔是 1000ms ⇒ 首次触发不得早于它（留 10% 的调度抖动余量）
        assertTrue(elapsedMs >= 900L,
                "Go 的 ticker 首次在 interval 之后触发（article.go:94）；"
                        + "RAG 若在启动瞬间就执行，说明 initialDelay 没生效，实际=" + elapsedMs + "ms");
    }
}
