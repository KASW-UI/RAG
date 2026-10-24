package com.rag.users.admin.rpc;

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
 * 🔴 UNRESOLVED-ARPC-003 守门 —— admin-rpc 的 Redis 超时必须与 Go 真源的库默认值一致。
 *
 * <h2>为什么这是个「测试」而不是「注释」</h2>
 *
 * <p>2026-10-23 定性时读出的链条（逐层到消费点）：
 * <pre>
 *   Go service/user/admin/rpc/etc/admin.yaml
 *       BizRedis 段**没有任何** timeout 字段
 *     → go-zero v1.10.2 core/stores/redis/redisclientmanager.go:32-39
 *       建 red.Options 时只传 Addr/User/Pass/DB/MaxRetries/MinIdleConns/TLSConfig
 *       ——**三个 timeout 一个都没传**
 *     → github.com/redis/go-redis/v9 options.go 的 init()
 *         DialTimeout = 5s                                   （:329-330）
 *         ReadTimeout = 3 * time.Second                       （switch opt.ReadTimeout { case 0: … }）
 * </pre>
 * ⇒ Go 的读命令超时 **3s**、连接超时 **5s**。
 *
 * <p>本模块原先只配了 {@code spring.data.redis.timeout: 3s}，**读超时碰巧对上**，
 * 但 Lettuce 在 {@code connect-timeout} 未设时会<b>回落到 timeout</b>
 * ⇒ 连接超时跟着变成 3s，与 Go 的 5s 不一致。已补 {@code connect-timeout: 5s}。
 *
 * <h2>🔴 一个会骗人的红鲱鱼（本条存在的主要理由之一）</h2>
 *
 * <p>go-zero {@code core/stores/redis/redis.go:27} 写着：
 * <pre>
 *   readWriteTimeout     = 2 * time.Second
 * </pre>
 * <b>全文件只有这一处 —— 声明了、从未被使用（死常量）。</b>
 * 只看这一行会得出「Go 的 Redis 超时是 2s」的错误结论。
 * ⇒ 真正生效的值在 go-redis 的 {@code init()} 里（Read 3s / Dial 5s）。
 * 这是 AGENTS.md §3.3.1「必须读到消费点，只贴声明行不算证据」的原型案例，
 * 也是本测试把两个值都钉死、而不只钉一个「大概 3 秒」的原因。
 *
 * <h2>⚠️ 本测试的效力边界（不夸大证据强度）</h2>
 *
 * <p>它是<b>静态配置断言</b>：证明 <b>yml 里写了这两个值</b>，
 * <b>不证明</b>「Boot 4 真的把它们绑定到了 Lettuce 客户端」。
 * 后者需要运行期证据（例如把连接指向黑洞 IP、实测失败耗时落在 5s 而非 3s），
 * 本轮<b>未做</b> ⇒ 记为 UNRESOLVED，见
 * {@code docs/DRIFT/users/admin/rpc.md}「2026-10-23 定性轮 §2.4」。
 */
class AdminRedisTimeoutConfigTest {

    private static Map<String, Object> load(String resource) {
        try (InputStream in = AdminRedisTimeoutConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
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

        // spring.data.redis.timeout → 3s
        // 出处：go-redis/v9 options.go init() 的 `switch opt.ReadTimeout { case 0: opt.ReadTimeout = 3*time.Second }`
        assertEquals("3s", dig(root, "spring", "data", "redis", "timeout"),
                "读命令超时应为 3s（= go-redis ReadTimeout 默认）。"
                        + "若要改，必须同时确认 go-redis 版本没变 —— 这条断言就是防它被无声改掉的");

        // spring.data.redis.connect-timeout → 5s（Boot 4 的独立属性）
        // 出处：go-redis/v9 options.go init() 的 `if opt.DialTimeout == 0 { opt.DialTimeout = 5*time.Second }`
        assertEquals("5s", dig(root, "spring", "data", "redis", "connect-timeout"),
                "连接超时应为 5s（= go-redis DialTimeout 默认）。"
                        + "Boot 4 里它是 DataRedisProperties.connectTimeout 这个**独立字段**；"
                        + "不配就会回落到 timeout=3s，与 Go 不一致");
    }

    /**
     * 🔴 比上一条更强一档：**证明 Spring Boot 的 Binder 真的把这个属性吃进去了。**
     *
     * <p>上一条只证明「yml 里有这个键」，<b>不证明</b>属性名写对、
     * relaxed binding 规则匹配得上、值能被塞进 {@link DataRedisProperties#setConnectTimeout}。
     * 这三件事任何一件错了，Spring 都<b>静默</b>保持默认值、不报错 ——
     * 正是 §3.3「不得凭配置名称断定机制已启用」的形态。
     *
     * <p>本条用 Spring Boot 真实的 {@code Binder} 走一遍 relaxed binding，
     * 然后断言 {@code connectTimeout} 拿到了 5s、{@code timeout} 拿到了 3s。
     *
     * <p>⚠️ <b>仍然不是全部</b>：它证明「属性被绑定进配置 POJO」，
     * <b>不证明</b>「Lettuce 客户端在 socket 层真的按这个值超时」。
     * 后者是 Lettuce 的内部行为，要拿到运行期证据只能指向一个黑洞地址测失败耗时，
     * 本轮<b>未做</b> ⇒ 该尾巴仍记 UNRESOLVED。
     */
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

    /**
     * 把 {@code ${VAR:default}} 取成 default（与 Spring 的行为一致）。
     * yml 里 host/port/password 都是这种占位符形态，不解析就没法绑成 int。
     */
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

    /** {@code a.b.c=v} 打平成 {@code {a.b.c=v}}，供 Spring 的 PropertySource 使用。 */
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