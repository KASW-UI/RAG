package com.rag.favorite.favorite.rpc;

import io.grpc.NameResolver;
import io.grpc.SynchronizationContext;
import net.devh.boot.grpc.client.nameresolver.DiscoveryClientResolverFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * 🔴🔴 <b>生产 gRPC client 地址的守门测试</b> —— 锁住本模块生产 yaml 里
 * <b>全部</b> {@code grpc.client.*.address} 的写法。
 *
 * <h2>为什么需要这条测试（它挡住的是「服务根本起不来」这一类硬失败）</h2>
 * <p>本模块的生产地址原本写成 <b>两斜杠</b> {@code discovery://user-rpc} /
 * {@code discovery://article-rpc}，
 * 而 devh 的 URI 校验要求 <b>三斜杠</b> ⇒ 启动期抛 {@code IllegalArgumentException}
 * ⇒ <b>整个服务起不来</b>（真 fat jar 实测，2026-10-26 第十七轮）。
 * <p><b>为什么模块里原有的测试都没发现</b>：它们一律用
 * {@code @SpringBootTest(properties = "grpc.client.user-rpc.address=static://127.0.0.1:1")}
 * 把地址覆盖成了 static ⇒ <b>生产地址从未被任何测试跑过</b>。
 * 这正是 AGENTS.md §3.3 说的「<b>跑绿 ≠ 对</b>」——
 * 「测试全绿」与「服务能启动」之间隔着一个配置文件。
 * ⇒ 同型缺陷在别的域已出现 7 处（article / comment / like / message / follow …），
 * <b>本域是最后一处</b>。
 *
 * <h2>本类验的是<b>生产文件</b>，不是测试覆盖值</h2>
 * <p>断言直接读 {@code src/main/resources/application.yml}
 * （并单独核对 {@code target/classes} 里打包进去的那份是否一致），
 * <b>不经过任何 {@code @SpringBootTest(properties=...)} 覆盖</b>。
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
 *
 * <h2>判据出处（<b>字节码实测</b>，不是看文档猜的）</h2>
 * <p>{@code net/devh/boot/grpc/client/nameresolver/DiscoveryClientResolverFactory.class}
 * （devh {@code grpc-client-spring-boot-starter} **3.1.0.RELEASE**）的
 * {@code newNameResolver(URI, Args)} 反编译出来的判据是：
 * <pre>
 * if ("discovery".equals(uri.getScheme())) {
 *     String path = uri.getPath();
 *     if (path == null || path.length() &lt;= 1 || !path.startsWith("/")) {
 *         throw new IllegalArgumentException("Incorrectly formatted target uri; ...");
 *     }
 *     return newNameResolver(path.substring(1), args);   // ← 服务名 = path 去掉首斜杠
 * }
 * </pre>
 * ⇒ {@code discovery://user-rpc} 解析出 {@code path=""}（长度 0）⇒ **不合格**；
 * ⇒ {@code discovery:///user-rpc} 解析出 {@code path="/user-rpc"} ⇒ 合格，
 * 且服务名 = {@code "user-rpc"} ⇒ <b>注册到 Nacos 的名字一个字都没改</b>。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p><b>Go 出处</b>：{@code service/favorite/rpc/etc/favorite.yaml:37-41}
 * {@code UserRpc.Etcd.Key: user.rpc} 与 {@code :43-47}
 * {@code ArticleRpc.Etcd.Key: article.rpc} —— Go 用 etcd 做服务发现，键是
 * {@code user.rpc} / {@code article.rpc}；RAG 侧换成 Nacos，服务名是
 * {@code user-rpc} / {@code article-rpc}（= {@code spring.application.name} 的同族命名）。
 * <p><b>语义等价论证</b>：两侧都是「按<b>服务名</b>解析出目标地址」这一件事，
 * 只是注册中心不同（etcd vs Nacos）⇒ 表达式不同但行为同构。
 * <p>⚠️ 因此本条<b>不是</b>「与 Go 对齐」类的语义漂移修复，而是
 * 「<b>RAG 侧写法非法导致起不来</b>」的修复
 * ⇒ <b>不登记</b>为有意偏离，也<b>不</b>进 {@code docs/Intentional_Deviations/}。
 *
 * <h2>为什么要遍历「全部」client</h2>
 * <p>本模块 yaml 里每个 {@code grpc.client.*} 都有独立的地址 ⇒ 少查一个就是漏检。
 * ⇒ 本类用 {@link MethodSource} 把 <b>yaml 里实际存在的全部 client</b> 喂给参数化用例，
 * 再用一条独立用例断言「client 集合 == 守门清单」⇒
 * <b>新增 client 而忘了加守门</b>会立刻红。
 */
class FavoriteRpcProductionAddressTest {

    /** 本模块目录名 —— 用于在源码树里定位本模块自己的 application.yml。 */
    private static final String MODULE_DIR = "favorite-rpc";

    /**
     * 本模块生产 yaml 里<b>全部</b> grpc client ⇒ 期望地址（三斜杠）。
     *
     * <p>key = Nacos 上的服务名（= yaml 里的 client key）；
     * value = 期望的生产地址。
     */
    private static final Map<String, String> EXPECTED_CLIENTS = Map.of(
            "user-rpc", "discovery:///user-rpc",
            "article-rpc", "discovery:///article-rpc");

    @Test
    @DisplayName("生产 yaml 里的 grpc client 集合必须与守门清单一致（新增 client 必须同步进守门）")
    void productionDeclaresExactlyTheGuardedClients() throws IOException {
        assertEquals(EXPECTED_CLIENTS.keySet(), productionClientAddresses().keySet(),
                "生产 yaml 里的 grpc client 集合与守门清单不一致 —— "
                        + "新增 client 时必须同步在本测试里加一条，否则它**永远不被检查**。");
    }

    @ParameterizedTest(name = "grpc.client.{0}.address 必须是三斜杠")
    @MethodSource("productionClientNames")
    @DisplayName("生产 application.yml 的 grpc client 地址必须是 discovery:///<service-name>（三斜杠）")
    void productionAddressUsesThreeSlashes(String client) throws IOException {
        String address = productionClientAddresses().get(client);

        assertEquals(EXPECTED_CLIENTS.get(client), address,
                "生产 gRPC client 地址必须是 " + EXPECTED_CLIENTS.get(client) + "。"
                        + "两斜杠（discovery://" + client + "）会让 devh 判 uri.getPath() 长度为 0 ⇒ "
                        + "启动期抛 IllegalArgumentException ⇒ 整个服务起不来；"
                        + "而现有测试都把地址覆盖成 static://，所以这条永远测不到。");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productionClientNames")
    @DisplayName("生产地址必须能被 devh 真正的消费点接受（两斜杠写法必须被拒绝）")
    void devhNameResolverAcceptsProductionAddressAndRejectsTwoSlash(String client) throws IOException {
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

        String production = productionClientAddresses().get(client);

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("productionClientNames")
    @DisplayName("生产地址解析出的服务名必须与服务发现名一致")
    void productionAddressResolvesToExpectedServiceName(String client) throws IOException {
        String path = URI.create(productionClientAddresses().get(client)).getPath();

        assertEquals("/" + client, path,
                "devh 会把 path 去掉首斜杠当服务名 ⇒ 必须等于 Nacos 上的服务名 " + client
                        + "（Go 侧对应 etcd 键 user.rpc / article.rpc，"
                        + "service/favorite/rpc/etc/favorite.yaml:37-41 / :43-47）");
    }

    @Test
    @DisplayName("target/classes 里打包的 application.yml 必须与源码树一致（防止「构建产物是旧的」）")
    void packagedCopyMatchesSourceTree() throws IOException {
        Path source = locateModuleYml();
        Path packaged = source.getParent().getParent().getParent().getParent()
                .resolve("target").resolve("classes").resolve("application.yml");
        Assumptions.assumeTrue(Files.exists(packaged),
                "target/classes 里还没有 application.yml（本次没跑过构建）⇒ 跳过交叉核对");

        assertEquals(productionClientAddresses(), readClientAddresses(packaged),
                "target/classes 里打包的 application.yml 与源码树不一致 —— "
                        + "说明上一次构建产物是旧的，测试结论不可信");
    }

    // ==================================================================
    // 取生产配置
    // ==================================================================

    /** 参数化用例的数据源：<b>yaml 里实际存在的全部</b> grpc client 名。 */
    static Stream<String> productionClientNames() throws IOException {
        return productionClientAddresses().keySet().stream();
    }

    /** 读<b>生产</b> application.yml 里的全部 {@code grpc.client.*.address}。 */
    private static Map<String, String> productionClientAddresses() throws IOException {
        return readClientAddresses(locateModuleYml());
    }

    private static Map<String, String> readClientAddresses(Path yml) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", new FileSystemResource(yml));
        for (PropertySource<?> source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (!name.startsWith("grpc.client.") || !name.endsWith(".address")) {
                    continue;
                }
                Object value = source.getProperty(name);
                if (value == null) {
                    continue;
                }
                String client = name.substring("grpc.client.".length(), name.length() - ".address".length());
                out.put(client, value.toString());
            }
        }
        if (out.isEmpty()) {
            fail("application.yml 里没有 grpc.client.*.address —— 生产 client 无地址，服务起不来："
                    + yml);
        }
        return out;
    }

    /**
     * 向上找本模块的 {@code src/main/resources/application.yml}。
     *
     * <p>Surefire 的工作目录默认是模块目录（{@code ${basedir}}），
     * 但从仓库根跑 {@code mvn -pl} 时也可能不同 ⇒ 逐级向上找，
     * 且要求命中路径里带本模块名，避免误取别的模块的 application.yml。
     */
    private static Path locateModuleYml() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources")
                    .resolve("application.yml");
            if (Files.exists(candidate) && dir.toString().contains(MODULE_DIR)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "找不到本模块的 src/main/resources/application.yml（工作目录=" + Paths.get("").toAbsolutePath() + "）");
    }
}
