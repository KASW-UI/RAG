package com.rag.message.message.rpc;

import io.grpc.NameResolver;
import io.grpc.SynchronizationContext;
import net.devh.boot.grpc.client.nameresolver.DiscoveryClientResolverFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * <b>生产 gRPC client 地址的守门测试</b> —— 锁住 {@code grpc.client.user-rpc.address}
 * 与 {@code grpc.client.admin-rpc.address} 两处写法。
 *
 * <h2>为什么需要这条测试（它挡住的是「服务根本起不来」这一类硬失败）</h2>
 * <p>本模块的 {@code application.yml} 此前把两个 client 地址都写成
 * {@code discovery://user-rpc} / {@code discovery://admin-rpc}（<b>两斜杠</b>）
 * ⇒ devh 判 {@code uri.getPath()} 长度为 0 ⇒ {@code IllegalArgumentException}
 * ⇒ <b>整个服务起不来</b>。
 *
 * <p><b>为什么本模块的测试没发现</b>：本模块的测试都用
 * {@code @SpringBootTest(properties = "grpc.client.X.address=static://127.0.0.1:1")}
 * 把地址覆盖成了 static ⇒ <b>生产地址从未被任何测试跑过</b>。
 * 这正是 AGENTS.md §3.3 说的「<b>跑绿 ≠ 对</b>」——
 * 「测试全绿」与「服务能启动」之间隔着一个配置文件。
 *
 * <h2>本类验的是<b>生产文件</b>，不是测试覆盖值</h2>
 * <p>断言直接读 {@code src/main/resources/application.yml}（并顺带核对
 * {@code target/classes} 里打包进去的那份是否一致），
 * <b>不经过任何 {@code @SpringBootTest(properties=...)} 覆盖</b>。
 *
 * <h2>🔴 为什么<b>两个</b> client 都必须查</h2>
 * <p>本模块有 <b>两个</b> client（{@code user-rpc} / {@code admin-rpc}），
 * 而 <b>users 域在 RAG 侧确有 {@code user-rpc} 与 {@code admin-rpc} 两个独立进程</b>
 * ⇒ 两处都必须写成三斜杠。
 * <p>守门测试若只查其中一个 ⇒ <b>另一个漏了也照样绿</b> ⇒ 那就是漏检。
 * ⇒ 本类用 {@link #EXPECTED_CLIENTS} 把两个 client 都列出来，
 * <b>遍历</b>逐个断言（逐个调 devh 的真实消费点）。
 *
 * <h2>为什么还要真的调 devh 的 {@code newNameResolver}</h2>
 * <p>光断言「字符串是三斜杠」只证明<b>我们自己写对了</b>，
 * 不证明<b>devh 接受</b>。本类直接调用 devh 真正的消费点
 * {@link DiscoveryClientResolverFactory#newNameResolver}：
 * <ul>
 *   <li>生产地址 ⇒ <b>不抛</b>异常（否则服务起不来）；</li>
 *   <li>两斜杠写法 ⇒ <b>必须抛</b> {@link IllegalArgumentException}
 *       （证明这条守门测试真的在守，而不是恒真）。</li>
 * </ul>
 * 判据出处（devh 3.1.0.RELEASE）：
 * {@code net/devh/boot/grpc/client/nameresolver/DiscoveryClientResolverFactory.java}
 * 的 {@code newNameResolver} —— 它取 {@code targetUri.getPath()}，
 * 要求<b>非 null、长度 > 1、以 "/" 开头</b>。
 *
 * <h2>⚠️ 本条<b>不是</b>「与 Go 对齐」</h2>
 * <p>改地址<b>不改变服务发现名</b>：devh 取 {@code path.substring(1)}
 * ⇒ 仍是 {@code user-rpc} / {@code admin-rpc}。
 * <p>两侧的<b>注册中心本来就不同</b>（Go 侧用 etcd key {@code user.rpc} / {@code admin.rpc}，
 * RAG 侧用 Nacos）⇒ 那是<b>已签字的有意偏离</b>，不在本条范围。
 * <p>本条修的是「<b>RAG 侧写法非法导致起不来</b>」——
 * 硬约束来自 devh 的 URI 格式校验，与 Go 无关。
 */
class MessageRpcProductionAddressTest {

    /** 生产 client 的服务名 —— 必须与 users 域两个进程在 Nacos 上的服务名一致。 */
    private static final String EXPECTED_SERVICE_NAME_USER = "user-rpc";

    private static final String EXPECTED_SERVICE_NAME_ADMIN = "admin-rpc";

    /**
     * 本模块<b>全部</b>生产 client —— 逐个断言，<b>缺一个就是漏检</b>。
     *
     * <p>期望地址 = {@code discovery:///} + 服务名（三斜杠）。
     */
    private static final Map<String, String> EXPECTED_CLIENTS = Map.of(
            EXPECTED_SERVICE_NAME_USER, "discovery:///" + EXPECTED_SERVICE_NAME_USER,
            EXPECTED_SERVICE_NAME_ADMIN, "discovery:///" + EXPECTED_SERVICE_NAME_ADMIN);

    @Test
    @DisplayName("生产 application.yml 的两个 grpc client 地址都必须��� discovery:///（三斜杠）")
    void productionAddressUsesThreeSlashes() throws IOException {
        for (Map.Entry<String, String> expected : EXPECTED_CLIENTS.entrySet()) {
            String client = expected.getKey();
            String address = productionAddress(client);

            assertEquals(expected.getValue(), address,
                    "生产 gRPC client 地址 grpc.client." + client + ".address 必须是 "
                            + expected.getValue() + "。"
                            + "两斜杠（discovery://" + client + "）会让 devh 判 uri.getPath() 长度为 0 ⇒ "
                            + "启动期抛 IllegalArgumentException ⇒ 整个服务起不来；"
                            + "而现有测试都把地址覆盖成 static://，所以这条永远测不到。"
                            + "⚠️ 本模块有 user-rpc 与 admin-rpc **两个** client ⇒ 两者都必须改。");
        }
    }

    @Test
    @DisplayName("两个生产地址都必须能被 devh 真正的消费点接受（两斜杠写法必须被拒绝）")
    void devhNameResolverAcceptsProductionAddressAndRejectsTwoSlash() throws IOException {
        DiscoveryClientResolverFactory factory =
                new DiscoveryClientResolverFactory(mock(DiscoveryClient.class));
        NameResolver.Args args = NameResolver.Args.newBuilder()
                .setDefaultPort(50051)
                .setProxyDetector(target -> null)
                .setSynchronizationContext(new SynchronizationContext((d, e) -> { }))
                // ServiceConfigParser 是**抽象类**（不是函数接口），必须匿名子类
                .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                    @Override
                    public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> rawServiceConfig) {
                        return null;
                    }
                })
                .build();

        for (Map.Entry<String, String> expected : EXPECTED_CLIENTS.entrySet()) {
            String client = expected.getKey();
            String production = productionAddress(client);

            assertDoesNotThrow(
                    () -> factory.newNameResolver(URI.create(production), args),
                    "生产地址 " + production + " 必须能被 devh 的 DiscoveryClientResolverFactory 接受"
                            + "（它取 uri.getPath()，要求非 null、长度>1、以 '/' 开头）");

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> factory.newNameResolver(URI.create("discovery://" + client), args),
                    "两斜杠写法必须被 devh 拒绝 —— 这条断言失败说明本守门测试已经恒真，失去意义");
            assertTrue(thrown.getMessage().contains("Incorrectly formatted target uri"),
                    "拒绝原因应是 URI 格式错误，实际：" + thrown.getMessage());
        }
    }

    @Test
    @DisplayName("两个生产地址解析出的服务名必须分别是 user-rpc / admin-rpc（与 Nacos 注册名一致）")
    void productionAddressResolvesToExpectedServiceName() throws IOException {
        for (Map.Entry<String, String> expected : EXPECTED_CLIENTS.entrySet()) {
            String client = expected.getKey();
            String path = URI.create(productionAddress(client)).getPath();

            assertEquals("/" + client, path,
                    "devh 会把 path 去掉首斜杠当服务名 ⇒ 必须等于 Nacos 上的服务名 " + client
                            + "（users 域在 RAG 侧确有 " + client + " 这个独立进程）");
        }
    }

    @Test
    @DisplayName("本模块的 application.yml 里不得残留任何两斜杠 discovery 地址")
    void noTwoSlashDiscoveryAddressRemains() throws IOException {
        Path yml = locateModuleYml();
        List<String> lines = Files.readAllLines(yml);
        for (int i = 0; i < lines.size(); i++) {
            // ⚠️ **必须先剥掉 YAML 注释** —— 本文件的说明性注释里就写着
            //    `discovery://user-rpc`（**反面示例**）⇒ 不剥注释会把注释当配置扫，
            //    断言必然红，且红得莫名其妙（这是本测试第一版的真实失败）。
            //    YAML 规则：`#` 出现在**行首**或**前面是空白**时才开始注释。
            String line = stripYamlComment(lines.get(i));
            int idx = line.indexOf("discovery://");
            if (idx < 0) {
                continue;
            }
            // 三斜杠的写法里，"discovery://" 后面紧跟的仍是 "/" ⇒ 跳过
            String rest = line.substring(idx + "discovery://".length());
            if (rest.startsWith("/")) {
                continue;
            }
            fail("application.yml:" + (i + 1) + " 仍有两斜杠的 discovery 地址 ⇒ "
                    + "devh 启动期会抛 IllegalArgumentException ⇒ 整个服务起不来 ⇒ " + line.trim());
        }
    }

    /**
     * 剥掉 YAML 行内注释 —— 只认「行首的 {@code #}」与「前面是空白的 {@code #}」。
     *
     * <p>⚠️ 不能简单地按第一个 {@code #} 截断：值里可能带 {@code #}
     * （本文件的值里没有，但规则本身不该假设）。
     */
    private static String stripYamlComment(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#")) {
            return "";
        }
        for (int i = 1; i < line.length(); i++) {
            if (line.charAt(i) == '#' && Character.isWhitespace(line.charAt(i - 1))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    // ==================================================================
    // 取生产配置
    // ==================================================================

    /**
     * 读<b>生产</b> application.yml 里的 {@code grpc.client.<client>.address}。
     *
     * <p>刻意不走 {@code ClassPathResource("application.yml")}：classpath 上可能同时存在
     * 多个模块的 application.yml（依赖 jar 里的 + 本模块的），命中哪个不确定 ⇒
     * 这里按<b>源码树路径</b>定位本模块的那份，并用 {@code target/classes} 里打包的副本交叉核对。
     */
    private static String productionAddress(String client) throws IOException {
        Path yml = locateModuleYml();
        String address = readAddress(new FileSystemResource(yml), client);

        Path packaged = yml.getParent().getParent().getParent().getParent()
                .resolve("target").resolve("classes").resolve("application.yml");
        if (Files.exists(packaged)) {
            String packagedAddress = readAddress(new FileSystemResource(packaged), client);
            assertEquals(address, packagedAddress,
                    "target/classes 里打包的 application.yml 与源码树不一致 —— "
                            + "说明上一次构建产物是旧的，测试结论不可信");
        }
        return address;
    }

    private static String readAddress(org.springframework.core.io.Resource resource, String client)
            throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", resource);
        String key = "grpc.client." + client + ".address";
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(key);
            if (value != null) {
                return value.toString();
            }
        }
        fail("application.yml 里没有 " + key + " —— 生产 client 无地址，服务起不来");
        return null;
    }

    /**
     * 向上找本模块的 {@code src/main/resources/application.yml}。
     *
     * <p>Surefire 的工作目录默认是模块目录（{@code ${basedir}}），
     * 但从仓库根跑 {@code mvn -pl} 时也可能不同 ⇒ 逐级向上找，
     * 且要求命中路径里带 {@code message-rpc}，避免误取别的模块。
     */
    private static Path locateModuleYml() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources")
                    .resolve("application.yml");
            if (Files.exists(candidate) && dir.toString().contains("message-rpc")) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "找不到 message-rpc 的 src/main/resources/application.yml（工作目录："
                        + Paths.get("").toAbsolutePath() + "）");
    }
}
