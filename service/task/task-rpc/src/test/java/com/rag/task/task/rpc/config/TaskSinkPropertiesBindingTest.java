package com.rag.task.task.rpc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「{@code task.definitions.*} 这段 yaml 到底有没有绑上」的实测。
 *
 * <h2>为什么需要这个测试（背景）</h2>
 * <p>{@code application.yml} 把 Go 侧写死在代码里的常量提到了配置面，并写明理由：
 * <blockquote>
 * 提到 yaml 只是为了让「改这个值」必须显式可见，而不是藏在代码魔数里。
 * （{@code application.yml:184-185}）
 * </blockquote>
 * 而 {@link TaskSinkProperties} 的字段名曾是 {@code initTtlDays} / {@code doneTtlDays}
 * （类型 {@code long}），yaml 里的键却是 {@code init-ttl} / {@code done-ttl}（值 {@code 90d}）。
 * <p>Spring Boot 的 relaxed binding 要求「配置键去掉分隔符后 == JavaBean 属性名去掉分隔符」，
 * {@code initttl} ≠ {@code initttldays} ⇒ <b>键名对不上</b>；
 * 又因为 {@code @ConfigurationProperties} 的 {@code ignoreUnknownFields} 默认 {@code true}，
 * 对不上的键<b>不会报错、只是被丢掉</b>。
 * ⇒ 症状是「yaml 里改了数值，运行时不报错、也不变」——
 * 正是 AGENTS.md §3.3.1 点名的「配置面看起来在管事、实际没在管事」。
 *
 * <h2>本测试断言两件事</h2>
 * <ol>
 *   <li><b>生效值 == Go 常量</b> —— 不论绑定是否成功，当前默认值都必须等于 Go 的硬编码，
 *       否则 TTL 本身就是漂的（Go 出处见各 assert 注释）；</li>
 *   <li><b>配置面是「活」的</b> —— 用文档里写的键名覆盖后 bean 必须真的变。
 *       这条是本测试存在的主要理由。</li>
 * </ol>
 *
 * <h2>Go 出处（断言 1 的依据）</h2>
 * <ul>
 *   <li>点赞路 init / done / like:total TTL = {@code 90*24h} —— {@code like_sink.go:188,284,339}</li>
 *   <li>文章路 init TTL = {@code articleInitTTL = 24h} —— {@code article_sink.go:23}</li>
 *   <li>文章路 done / like:cnt TTL = {@code articleDoneTTL = 90*24h + 30*24h = 120d} —— {@code article_sink.go:22}</li>
 * </ul>
 */
class TaskSinkPropertiesBindingTest {

    /** 加载真实的 application.yml（不是测试专用的最小 yml），并可叠加更高优先级的覆盖项。 */
    private static Binder binderWithOverride(Map<String, Object> override) {
        try {
            List<PropertySource<?>> loaded =
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
            MutablePropertySources sources = new MutablePropertySources();
            if (override != null) {
                // addFirst ⇒ 优先级高于 application.yml，模拟「运维改了 yaml」
                sources.addFirst(new MapPropertySource("override", override));
            }
            loaded.forEach(sources::addLast);
            return new Binder(ConfigurationPropertySources.from(sources));
        } catch (Exception e) {
            throw new IllegalStateException("load application.yml failed", e);
        }
    }

    private static TaskSinkProperties bind(Binder binder) {
        return binder.bind("task.definitions", TaskSinkProperties.class).orElseGet(TaskSinkProperties::new);
    }

    @Test
    @DisplayName("断言1 · 生效值等于 Go 硬编码常量（TTL / target / taskId / 分块阈值）")
    void effectiveValuesMatchGoConstants() {
        TaskSinkProperties p = bind(binderWithOverride(null));

        // Go like_sink.go:32-33  const ( taskID = int64(1902); target = 5 )
        assertEquals(1902L, p.getLike().getTaskId());
        assertEquals(5L, p.getLike().getTarget());
        // Go article_sink.go:20-21  articleTaskID = 190; articleTarget = int64(3)
        assertEquals(190L, p.getArticle().getTaskId());
        assertEquals(3L, p.getArticle().getTarget());

        // Go like_sink.go:188,284,339 —— 90*24h
        assertEquals(Duration.ofDays(90), p.getLike().getInitTtl());
        // Go article_sink.go:23 —— articleInitTTL = 24h == Duration.ofDays(1)
        assertEquals(Duration.ofHours(24), p.getArticle().getInitTtl());
        // Go article_sink.go:22 —— articleDoneTTL = 90*24h + 30*24h
        assertEquals(Duration.ofDays(120), p.getArticle().getDoneTtl());

        // Go like_sink.go:70-71 / article_sink.go:25-26 —— redisPipeMax 5000 / pgBatchMax 2000
        assertEquals(5000, p.getLike().getRedisPipeMax());
        assertEquals(2000, p.getLike().getPgBatchMax());
        assertEquals(5000, p.getArticle().getRedisPipeMax());
        assertEquals(2000, p.getArticle().getPgBatchMax());

        // Go like_sink.go:69 / article_sink.go:24 —— flushEvery = 1 * time.Second
        assertEquals(1000L, p.getLike().getFlushEveryMs());
        assertEquals(1000L, p.getArticle().getFlushEveryMs());
    }

    @Test
    @DisplayName("阳性对照 · 键名对得上的键必须绑得上（证明本探针有识别能力，不是空过）")
    void positiveControlKeyThatDoesMatchTheFieldNameBinds() {
        // task.definitions.like.redis-pipe-max → TaskSinkProperties.Like#redisPipeMax
        // 去掉分隔符后 init 键 == JavaBean 属性名，这是 relaxed binding 的**正常**情形。
        // 若这条也绑不上，说明是本探针/本 Binder 的用法有问题，而不是配置键名的问题。
        TaskSinkProperties p = bind(binderWithOverride(Map.of("task.definitions.like.redis-pipe-max", "1234")));
        assertEquals(1234, p.getLike().getRedisPipeMax(),
                "阳性对照失败：连键名一致的配置都绑不上 ⇒ 结论不能归因于 init-ttl 的键名不匹配");
    }

    @Test
    @DisplayName("断言2 · 配置面是活的：yaml 里改 init-ttl，bean 必须真的跟着变")
    void yamlOverrideActuallyBinds() {
        // 用 application.yml 里**实际写的那个键名** init-ttl 覆盖（值带单位，与 yaml 写法一致）。
        // 若绑定成功，article 的 init-ttl 应变成 7 天。
        TaskSinkProperties p = bind(binderWithOverride(Map.of("task.definitions.article.init-ttl", "7d")));

        assertEquals(Duration.ofDays(7), p.getArticle().getInitTtl(),
                "yaml 的 init-ttl 没有绑到 TaskSinkProperties —— 配置面是死的");
    }
}