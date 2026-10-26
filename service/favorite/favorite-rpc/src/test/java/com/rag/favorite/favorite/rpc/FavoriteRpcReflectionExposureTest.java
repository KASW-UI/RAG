package com.rag.favorite.favorite.rpc;

import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 🔴🔴 <b>生产 gRPC reflection 暴露面的守门测试</b>。
 *
 * <h2>它挡的是什么</h2>
 * <p>RAG 侧原先<b>没有</b>关掉 gRPC server reflection ⇒ 生产端口上多暴露一整个反射面
 * （<code>grpcurl -plaintext &lt;host&gt;:8082 list</code> 能列出<b>全部</b>方法
 * 并下载 proto 描述符），而 Go 真源在生产形态下**不暴露**。
 * ⇒ 这是一条<b>真源更严、RAG 更松</b>的漂移（本域<b>漏配</b>，其余 4 个 rpc 域已修：
 * user-rpc / admin-rpc / message-rpc / hot-rpc）。
 *
 * <h2>Go 出处与消费点判定</h2>
 * <pre>
 * service/favorite/rpc/favorite.go:45-47
 *     if c.Mode == service.DevMode || c.Mode == service.TestMode {
 *         reflection.Register(grpcServer)
 *     }
 * </pre>
 * <p>{@code c.Mode} 来自内嵌的 {@code zrpc.RpcServerConf → service.ServiceConf}，
 * 其 tag 是 {@code json:",default=pro,options=dev|test|rt|pre|pro"}
 * （go-zero v1.10.2 {@code core/service/serviceconf.go:35}），
 * 而 {@code service/favorite/rpc/etc/favorite.yaml} <b>没有配顶层 {@code Mode}</b>
 * ⇒ 取默认值 {@code "pro"} ⇒ <b>分支不成立</b> ⇒ <b>真源生产形态不注册 reflection</b>。
 *
 * <h2>为什么必须<b>显式</b>写 {@code false}</h2>
 * <p>devh 侧默认是<b>开着</b>的，两个证据都在本测试里被断言：
 * <ul>
 *   <li>{@link GrpcServerProperties#isReflectionServiceEnabled()} 的<b>默认值</b>是 {@code true}；</li>
 *   <li>{@code GrpcReflectionServiceAutoConfiguration} 上是
 *       {@code @ConditionalOnProperty(prefix="grpc.server", name="reflection-service-enabled",
 *       matchIfMissing=true)} ⇒ <b>不写这一行就是开着</b>。</li>
 * </ul>
 *
 * <h2>本类验的是<b>生产文件</b>，且<b>真绑一次</b></h2>
 * <p>断言直接读 {@code src/main/resources/application.yml}（并单独核对 {@code target/classes} 里
 * 打包进去的那份），<b>不经过任何 {@code @SpringBootTest} 覆盖</b>。
 * <p>并且用 Spring 的 {@link Binder} 把那个键<b>真的绑到 devh 的配置对象上</b>
 * ⇒ 键名写错 / 写成 {@code reflect-service-enabled} 之类 ⇒ 绑定后仍是默认的 {@code true} ⇒ <b>转红</b>。
 * ⇒ 这正是 AGENTS.md §3.3.3「断言『配置文件里有一行』≠『机制生效』」的同型要求。
 */
class FavoriteRpcReflectionExposureTest {

    /** 本模块目录名 —— 用于在源码树里定位本模块自己的 application.yml。 */
    private static final String MODULE_DIR = "favorite-rpc";

    /** devh 的配置键（必须与 {@code GrpcReflectionServiceAutoConfiguration} 上的 prefix/name 一致）。 */
    private static final String REFLECTION_KEY = "grpc.server.reflection-service-enabled";

    @Test
    @DisplayName("生产 application.yml 必须显式声明 reflection-service-enabled: false")
    void productionExplicitlyDisablesReflection() throws IOException {
        Object raw = readProperty(locateModuleYml(), REFLECTION_KEY);

        assertEquals(Boolean.FALSE, raw,
                "生产 yaml 必须显式写 `" + REFLECTION_KEY + ": false` —— "
                        + "devh 的 GrpcServerProperties 默认 reflectionServiceEnabled=true，"
                        + "且 GrpcReflectionServiceAutoConfiguration 上是 matchIfMissing=true "
                        + "⇒ **不写这一行就是开着** ⇒ 生产端口会多暴露一整个反射面，"
                        + "而 Go 真源在 Mode=pro（默认）下**不注册** reflection"
                        + "（service/favorite/rpc/favorite.go:45-47）。");
    }

    @Test
    @DisplayName("devh 的默认值是 true ⇒ 上面那一行是承重的（不是凑数）")
    void devhDefaultIsEnabledSoTheExplicitFalseIsLoadBearing() {
        assertTrue(new GrpcServerProperties().isReflectionServiceEnabled(),
                "devh GrpcServerProperties.reflectionServiceEnabled 的默认值变了 —— "
                        + "若 devh 已经改成默认关闭，那 yaml 里那行就不再承重，"
                        + "本守门测试的前提需要重新核对（别让本测试变成橡皮章）。");
    }

    @Test
    @DisplayName("那个键必须能真的绑到 devh 的配置对象上（键名写错 ⇒ 绑完仍是 true ⇒ 转红）")
    void reflectionKeyActuallyBindsToDevhProperties() {
        GrpcServerProperties bound = new Binder(new MapConfigurationPropertySource(
                        Map.of(REFLECTION_KEY, "false")))
                .bind("grpc.server", GrpcServerProperties.class)
                .orElseGet(GrpcServerProperties::new);

        assertFalse(bound.isReflectionServiceEnabled(),
                "`" + REFLECTION_KEY + "` 没有绑到 devh 的 reflectionServiceEnabled 上 —— "
                        + "键名或前缀写错了 ⇒ 生产反射面仍然是开的。"
                        + "（这正是 AGENTS.md §3.3.3「配置文件里有一行 ≠ 机制生效」）");
    }

    @Test
    @DisplayName("target/classes 里打包的 application.yml 必须与源码树一致（防止「构建产物是旧的」）")
    void packagedCopyMatchesSourceTree() throws IOException {
        Path source = locateModuleYml();
        Path packaged = source.getParent().getParent().getParent().getParent()
                .resolve("target").resolve("classes").resolve("application.yml");
        Assumptions.assumeTrue(Files.exists(packaged),
                "target/classes 里还没有 application.yml（本次没跑过构建）⇒ 跳过交叉核对");

        assertEquals(readProperty(source, REFLECTION_KEY), readProperty(packaged, REFLECTION_KEY),
                "target/classes 里打包的 application.yml 与源码树不一致 —— "
                        + "说明上一次构建产物是旧的，测试结论不可信");
    }

    // ==================================================================
    // 取生产配置
    // ==================================================================

    /** 从 yaml 里取一个键的**原始值**（保留 Boolean 类型，不做字符串化）。 */
    private static Object readProperty(Path yml, String key) throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", new FileSystemResource(yml));
        for (PropertySource<?> source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (name.equals(key)) {
                    return source.getProperty(name);
                }
            }
        }
        fail("生产 application.yml 里没有 " + key + " ⇒ " + yml);
        return null;
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
