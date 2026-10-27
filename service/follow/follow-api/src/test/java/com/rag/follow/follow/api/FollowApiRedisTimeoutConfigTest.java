package com.rag.follow.follow.api;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 🔴 follow-api 的 Redis 超时必须与 Go 真源的库默认值一致（**api 侧**）。
 *
 * <h2>本条闭合什么（2026-10-27 复核新发现）</h2>
 *
 * <p>follow-api 的 {@code application.yml} 原本写的是 {@code spring.data.redis.timeout: **2s**}
 * —— <b>无出处、无人审过</b>（台账里只登记成「待审」的 {@code DRIFT-USERAPI-015 附带}）。
 * 而 Go 侧的<b>有效</b>读命令超时是 **3s**（go-redis {@code ReadTimeout} 默认）⇒ 两侧不一致。
 * ⇒ 已对齐成 3s，并补上 {@code connect-timeout: 5s}（Go 的 {@code DialTimeout} 默认；
 * ⚠️ 2026-10-27 订正：此处原写「Lettuce 在未设时**回落到 timeout** ⇒ 不配就跟着变成 3s」
 * ——<b>那是错的</b>：Boot 4.0.8 {@code createClientOptions} 的字节码是
 * {@code if (connectTimeout != null) { … }} ⇒ null ⇒ 整段跳过 ⇒ 用 Lettuce 默认 <b>10s</b>
 * ⇒ 漂移方向是 10s vs 5s（修法与结论不变））。
 *
 * <h2>取值出处链（逐层到消费点）</h2>
 * <pre>
 *   Go service/follow/api/etc/followcenter.yaml
 *       BizRedis { Host=redis:6379 / Type=node / Pass="" } —— **没有任何 timeout 字段**
 *     → go-zero v1.10.2 core/stores/redis/redisclientmanager.go:32-39
 *       建 red.Options 时不传任何 timeout
 *     → github.com/redis/go-redis/v9 options.go 的 init()
 *         DialTimeout = 5s                                   （:329-330）
 *         ReadTimeout = 3 * time.Second                       （switch opt.ReadTimeout { case 0: … }）
 * </pre>
 *
 * <h2>🔴 一个会骗人的红鲱鱼（本条存在的主要理由之一）</h2>
 *
 * <p>go-zero {@code core/stores/redis/redis.go:27} 写着 {@code readWriteTimeout = 2 * time.Second} ——
 * <b>这正是原先那个 2s 的「看起来有出处」的来源</b>，但全文件只有 :293/:315 的 BLPop 用它
 * ⇒ 对 {@code ExistsCtx}（follow 的黑名单查询）它是**死常量** ⇒ <b>不能拿它当依据</b>。
 *
 * <h2>可观察后果（对齐后的形态）</h2>
 *
 * <p>Redis「连上但挂住」时：黑名单 EXISTS 挂到 **3s** ⇒ 与 HTTP 预算（{@code observability.http-timeout: 3s}）
 * 同刻耗尽 ⇒ **503**，与 Go / user-api / admin-api <b>同形</b>（Go 的 CLIENT PAUSE 实测 3.003s）。
 * ⇒ 改前的 2s 会让本域在 2s 就 fail-open ⇒ 返回 200 ⇒ <b>与 Go 不一致</b>。
 *
 * <h2>⚠️ 本测试的效力边界（不夸大证据强度）</h2>
 *
 * <p>① 第一条是<b>静态配置断言</b>：证明 yml 里写了这两个值，<b>不证明</b>「Boot 4 真的把它们
 * 绑定到了 Lettuce 客户端」；第二条用 Spring 真实的 {@code Binder} 走一遍 relaxed binding，
 * 证明「属性被绑定进 {@code DataRedisProperties}」。
 *
 * <p>② **socket 层那一截已由 {@link FollowApiRedisConnectTimeoutSocketTest} 收口**（2026-10-27）：
 * 那个类断言 Lettuce 客户端的 {@code socketOptions.connectTimeout == 5s}，
 * 并把连接指向黑洞 IP {@code 192.0.2.1} 实测失败耗时（实测 <b>5175ms</b>，落在 [4000ms, 9500ms)）
 * ⇒ **本类（文本层 + 绑定层）＋ 那条（消费层）合起来才是完整的证据链**；
 * 单看本类仍不能证明 socket 层。
 */
class FollowApiRedisTimeoutConfigTest {

    private static Map<String, Object> load(String resource) {
        try (InputStream in = FollowApiRedisTimeoutConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "找不到 classpath 资源: " + resource);
            Object root = new Yaml().load(in);
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) root;
            return m;
        } catch (Exception e) {
            throw new AssertionError("读 " + resource + " 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object dig(Map<String, Object> root, String... path) {
        Object cur = root;
        for (String k : path) {
            assertNotNull(cur, "路径断在 " + String.join(".", path) + " 的 " + k);
            cur = ((Map<String, Object>) cur).get(k);
        }
        return cur;
    }

    @Test
    @DisplayName("✅ 读命令超时 3s = go-redis 的 ReadTimeout 默认；连接超时 5s = DialTimeout 默认")
    void redisTimeoutsMatchGoRedisDefaults() {
        Map<String, Object> root = load("application.yml");

        assertEquals("3s", dig(root, "spring", "data", "redis", "timeout"),
                "读命令超时应为 3s（= go-redis ReadTimeout 默认）。"
                        + "⚠️ 原为 2s —— 那个数字来自 go-zero redis.go:27 的 readWriteTimeout，"
                        + "但它是死常量（只有 BLPop 用）⇒ 不能当依据。"
                        + "若要改，必须同时确认 go-redis 版本没变 —— 这条断言就是防它被无声改掉的");

        assertEquals("5s", dig(root, "spring", "data", "redis", "connect-timeout"),
                "连接超时应为 5s（= go-redis DialTimeout 默认）。"
                        + "Boot 4 里它是 DataRedisProperties.connectTimeout 这个**独立字段**；"
                        + "不配**不会**回落到 timeout（Boot 4 在 null 时整段跳过）"
                        + "⇒ 会用 Lettuce 默认 10s，与 Go 的 5s 不一致");
    }

    @Test
    @DisplayName("✅ Spring Boot 的 Binder 真把 connect-timeout 绑进 DataRedisProperties（不是只写在 yml 里）")
    void springBootBinderConsumesConnectTimeout() {
        MapConfigurationPropertySource src =
                new MapConfigurationPropertySource(flatten(load("application.yml")));
        DataRedisProperties props = new Binder(src)
                .bind("spring.data.redis", DataRedisProperties.class)
                .orElseThrow(() -> new AssertionError("spring.data.redis 绑不上 —— "
                        + "若属性名写错，Spring 会静默用默认值且不报错"));

        assertEquals(Duration.ofSeconds(5), props.getConnectTimeout(),
                "connect-timeout 必须被 relaxed binding 吃进 DataRedisProperties.connectTimeout。"
                        + "拿到 null 就说明属性名/前缀写错了，而 Spring 不会报错 —— 那正是本用例要挡的静默失效");
        assertEquals(Duration.ofSeconds(3), props.getTimeout(),
                "timeout 同理：读命令超时 3s（= go-redis ReadTimeout 默认）");
    }

    private static String resolvePlaceholder(String v) {
        if (!v.startsWith("${")) {
            return v;
        }
        int close = v.lastIndexOf('}');
        if (close < 0) {
            return v;
        }
        String body = v.substring(2, close);
        int colon = body.indexOf(':');
        return colon < 0 ? "" : body.substring(colon + 1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flatten(Map<String, Object> in) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        walk(in, "", out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Map<String, Object> in, String prefix, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : in.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                walk((Map<String, Object>) v, key, out);
            } else if (v != null) {
                out.put(key, resolvePlaceholder(String.valueOf(v)));
            }
        }
    }
}
