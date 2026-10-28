package com.rag.users.user.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 🔴 user-rpc 的 Redis 超时必须与 Go 真源的库默认值一致（<b>rpc 侧</b>）。
 *
 * <h2>本条闭合什么（2026-10-27 · G1 lane · DRIFT-USERRPC-V-014）</h2>
 *
 * <p>user-rpc 的 {@code application.yaml} 原本<b>只配了</b> {@code spring.data.redis.timeout: 3s}，
 * <b>没配 {@code connect-timeout}</b>。
 *
 * <h2>🔴 一处此前写错的机制（本类把它纠正过来）</h2>
 *
 * <p>本仓此前多处写「Lettuce 在 connect-timeout 未设时<b>回落到 timeout</b> ⇒ 会变成 3s」——
 * <b>那是错的</b>。Boot 4.0.8 的 {@code LettuceConnectionConfiguration.createClientOptions} 字节码是：
 * <pre>
 *   Duration connectTimeout = getProperties().getConnectTimeout();
 *   if (connectTimeout != null) {
 *       builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build());
 *   }
 * </pre>
 * ⇒ <b>null ⇒ 整段不执行</b> ⇒ 用 Lettuce 自己的默认
 * {@code io.lettuce.core.SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION = 10s}
 * ⇒ <b>不配的结果是 10s，不是 3s</b>（实测：摘掉这行 ⇒ 客户端 connectTimeout = PT10S）。
 * ⇒ 修法不变（显式配 5s），但**漂移的方向是 10s vs Go 的 5s**。
 *
 * <h2>取值出处链（逐层到消费点）</h2>
 * <pre>
 *   Go service/user/user/rpc/etc/user.yaml:17-20
 *       BizRedis 段 = Host / Type / Pass —— **没有任何 timeout 字段**
 *     → go-zero v1.10.2 core/stores/redis/redisclientmanager.go:32-39
 *       建 red.Options 时只传 Addr/User/Pass/DB/MaxRetries/MinIdleConns/TLSConfig
 *       ——**三个 timeout 一个都没传**
 *     → github.com/redis/go-redis/v9 options.go 的 init()
 *         DialTimeout = 5s                                   （:329-330）
 *         ReadTimeout = 3 * time.Second                       （switch opt.ReadTimeout { case 0: … }）
 * </pre>
 * ⇒ Go 的<b>连接</b>超时 <b>5s</b>、<b>读命令</b>超时 <b>3s</b>。
 *
 * <h2>⚠️ 为什么 user-rpc 的这两个值是<b>生效</b>的（不是死配置）</h2>
 * <p>user-rpc 的 Redis 只有一个用途：logout 写黑名单
 * （{@code UserDomainService.logout:905-930} ⇒ {@code bizRedis.opsForValue().set("user:jwt_blacklist:" + token, ...)}）
 * ⇒ 连接超时与命令超时都在这条路径上<b>直接决定客户端看到什么</b>
 * （详见 {@code DRIFT-USERRPC-V-014}：断连时命令排队到 commandTimeout 3s ≫ rpc-timeout 2s ⇒ 客户端看到 1015 而非 Go 的 INTERNAL）。
 *
 * <h2>🔴 一个会骗人的红鲱鱼</h2>
 * <p>go-zero {@code core/stores/redis/redis.go:27} 写着 {@code readWriteTimeout = 2 * time.Second}，
 * 但<b>全文件只有 :293/:315 的 BLPop 用它</b> ⇒ 对 {@code SetexCtx}（写黑名单）是**死常量**。
 * ⇒ 只看这一行会得出「Go 的 Redis 超时是 2s」的错误结论 ⇒ 本测试把两个值都钉死。
 *
 * <h2>⚠️ 本测试的效力边界（不夸大证据强度）</h2>
 *
 * <p>① 第一条是<b>静态配置断言</b>：证明 yml 里写了这两个值，<b>不证明</b>「Boot 4 真的把它们
 * 绑定到了 Lettuce 客户端」；第二条用 Spring 真实的 {@code Binder} 走一遍 relaxed binding，
 * 证明「属性被绑定进 {@code DataRedisProperties}」。
 * <p>② <b>消费层</b>（客户端里真的拿到 3s / 5s）由
 * {@link UserRpcRedisConnectTimeoutSocketTest} 收口 ⇒ <b>本类（文本层 + 绑定层）
 * ＋ 那条（消费层）合起来才是完整的证据链**；单看本类仍不能证明客户端拿到的值。
 */
class UserRpcRedisTimeoutConfigTest {

    private static Map<String, Object> load(String resource) {
        try (InputStream in = UserRpcRedisTimeoutConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
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
        Map<String, Object> root = load("application.yaml");

        assertEquals("3s", dig(root, "spring", "data", "redis", "timeout"),
                "读命令超时应为 3s（= go-redis ReadTimeout 默认）。"
                        + "若要改，必须同时确认 go-redis 版本没变 —— 这条断言就是防它被无声改掉的");

        assertEquals("5s", dig(root, "spring", "data", "redis", "connect-timeout"),
                "连接超时应为 5s（= go-redis DialTimeout 默认）。"
                        + "Boot 4 里它是 DataRedisProperties.connectTimeout 这个**独立字段**；"
                        + "不配**不会**回落到 timeout（Boot 4.0.8 的 createClientOptions 里 null ⇒ 整段跳过）"
                        + "⇒ 会用 Lettuce 自己的默认 **10s** ⇒ 与 Go 的 5s 不一致");
    }

    @Test
    @DisplayName("✅ Spring Boot 的 Binder 真把 connect-timeout 绑进 DataRedisProperties（不是只写在 yml 里）")
    void springBootBinderConsumesConnectTimeout() {
        MapConfigurationPropertySource src =
                new MapConfigurationPropertySource(flatten(load("application.yaml")));
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
