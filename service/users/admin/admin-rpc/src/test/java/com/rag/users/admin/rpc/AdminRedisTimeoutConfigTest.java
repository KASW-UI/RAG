package com.rag.users.admin.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
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
}