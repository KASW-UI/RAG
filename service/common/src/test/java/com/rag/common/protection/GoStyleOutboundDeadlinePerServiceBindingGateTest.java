package com.rag.common.protection;

import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：「按下游 gRPC 服务全名覆盖」这条参数，在<b>真实生产 yml</b> 上真的绑得上。</b>
 *
 * <h2>🔴 它补的是哪一个洞（2026-10-31 · 合并收口执行者 · 代 G4 落 per-client 上界）</h2>
 * <p>{@link GoStyleOutboundDeadlinePerServiceCapGateTest} 证明了<b>共享层这一个入口</b>
 * 真按 per-service 封顶 ⇒ 但它用的是 {@code SpringApplicationBuilder.properties(...)}
 * 喂的<b>扁平属性名</b> ⇒ ⇒ <b>生产路径是 YAML</b> ⇒ ⇒ 两者虽同形，<b>不经同一条解析链</b>。
 * <p>⚠️ <b>本门要钉的那个具体风险 = relaxed binding 对 map 键的归一</b>：
 * 键 {@code article.ArticleService} 里同时有<b>点号</b>（在 Spring 的属性名里 = 层级分隔符）
 * 与<b>大写字母</b>（relaxed binding 习惯小写）⇒ ⇒ 若绑定时被归一成
 * {@code article.articleservice}（或被拆成两层）⇒ ⇒ <b>运行时的
 * {@code MethodDescriptor#getServiceName()}（= {@code article.ArticleService}）就查不到</b>
 * ⇒ ⇒ <b>参数配了、机制在跑、但永远不命中</b> ⇒ ⇒ 那是<b>纯假绿</b>。
 *
 * <h2>本门的三条断言（缺一即判红）</h2>
 * <ol>
 *   <li><b>真实 yml → 真实属性类</b>：加载生产文件
 *       {@code service/article/article-api/src/main/resources/application.yml}
 *       （<b>不是</b>内联片段）⇒ 用 Spring 自己的 {@link YamlPropertySourceLoader} 解析
 *       ⇒ 用 Spring 自己的 {@link Binder} 绑到 {@link GoStyleProtectionProperties}
 *       ⇒ ⇒ 断言 {@code capMillisByService} <b>恰好</b>是那两条、键<b>逐字</b>一致、值一致；</li>
 *   <li><b>组件级仍然没配</b>：{@code capMillis == 0}
 *       ⇒ ⇒ 断言「组件级一个值表达不了 per-direction 真值」这条纪律
 *       <b>没有被后人悄悄破坏</b>（配了 2000 ⇒ article-rpc 方向被改严 35 倍）；</li>
 *   <li><b>键 = proto 的真实服务全名</b>：从<b>仓内真实 .proto</b> 里抽
 *       {@code package X;} + {@code service Y} ⇒ 算出全名 {@code X.Y}
 *       ⇒ ⇒ 断言配置的每个键都<b>确实存在</b>于那些真实全名里
 *       ⇒ ⇒ 关掉「凭记忆手写一个键名」的洞。</li>
 * </ol>
 *
 * <h2>⚠️ 诚实边界（本门<b>不</b>证明什么）</h2>
 * <ul>
 *   <li>本门证明<b>参数绑得上、键名对得上</b> ⇒ ⇒ <b>不</b>证明「每个域都配了」
 *       （那是域侧 yml 的事 ⇒ 域 lane 的面）；</li>
 *   <li>本门证明<b>共享层这一个组件</b> ⇒ ⇒ <b>不</b>证明「所有出向调用都真的走了这个拦截器」
 *       （那由 per-service 守门的形态①/② + devh 装配面负责）；</li>
 *   <li>本门只跑 <b>{@code article-api}</b> 一个组件 ⇒ ⇒ 其余域若要用同一条参数，
 *       需各自的绑定面（本门是<b>模板</b>，不是<b>全域覆盖</b>）。</li>
 * </ul>
 */
class GoStyleOutboundDeadlinePerServiceBindingGateTest {

    /** 被验证的生产配置（<b>真实文件</b>，不是内联片段）。 */
    private static final String ARTICLE_API_YML =
            "service/article/article-api/src/main/resources/application.yml";

    /** 仓内真实 proto（用来交叉核「服务全名」）。 */
    private static final String ARTICLE_PROTO = "service/article/article-proto/src/main/proto/article.proto";
    private static final String USER_PROTO = "service/users/user/user-proto/src/main/proto/users.proto";

    /** 组件级 {@code cap-millis} 的「未配置」值（= 0 ⇒ 不启用上界）。 */
    private static final long CAP_MILLIS_UNSET = 0L;

    /** article-rpc 方向的真值（Go {@code ArticleRpcConf.Timeout: 70000}）。 */
    private static final long ARTICLE_RPC_CAP_MS = 70_000L;

    /** user-rpc 方向的真值（Go {@code UserRpcConf} 段无 Timeout ⇒ zrpc 默认 2000）。 */
    private static final long USER_RPC_CAP_MS = 2_000L;

    /** proto 的 {@code package X;}。 */
    private static final Pattern PROTO_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");

    /** proto 的 {@code service Y}（无花括号，靠行尾定界）。 */
    private static final Pattern PROTO_SERVICE = Pattern.compile("(?m)^\\s*service\\s+([A-Za-z0-9_]+)\\s*\\{");

    // ────────────────────────────────────────────────────────────────────────────
    // 断言①：真实 yml → 真实属性类 ⇒ 键逐字、值一致、恰好两条
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 真实 article-api yml 绑到真实属性类 ⇒ cap-millis-by-service 恰好两条、键逐字一致（不被 relaxed binding 归一）")
    void realYamlBindsWithExactKeys() {
        GoStyleProtectionProperties props = bindArticleApi();

        Map<String, Long> byService = props.getOutboundDeadline().getCapMillisByService();

        assertThat(byService)
                .as("🔴 per-service 覆盖表没绑上 ⇒ ⇒ **参数配了但不生效**（纯假绿）⇒ ⇒ "
                        + "键里同时有点号与大写 ⇒ ⇒ relaxed binding 把它归一了 ⇒ "
                        + "运行时 `MethodDescriptor#getServiceName()` 永远查不到")
                .containsExactly(
                        Map.entry("article.ArticleService", ARTICLE_RPC_CAP_MS),
                        Map.entry("user.UserService", USER_RPC_CAP_MS));

        // ⚠️ 归一化的两种典型形态各点一次名（⇒ 失败时能一眼看出是哪一种）
        assertThat(byService)
                .as("🔴 键被小写化了 ⇒ ⇒ 运行时查不到（运行时取的是 grpc-java 的原始全名，含大写）")
                .doesNotContainKey("article.articleservice");
        assertThat(byService)
                .as("🔴 键被拆成了层级（点号当分隔符 ⇒ package 与 service 变成两个 map 条目）")
                .doesNotContainKey("article");
        assertThat(byService)
                .as("🔴 键被去掉了 package 前缀")
                .doesNotContainKey("ArticleService");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 断言②：组件级仍然没配（纪律不被后人悄悄破坏）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("② 组件级 cap-millis 仍然未配置（=0）⇒ 组件级一个值表达不了 per-direction 真值这条纪律没被破坏")
    void componentLevelCapIsStillUnset() {
        GoStyleProtectionProperties props = bindArticleApi();

        assertThat(props.getOutboundDeadline().getCapMillis())
                .as("🔴 组件级 `cap-millis` 不该被配上 ⇒ ⇒ 配 2000 会把 article-rpc 方向从 70s **改严 35 倍**；"
                        + "配 70000 会把 user-rpc 方向从 2s **放宽 35 倍** ⇒ ⇒ **两个值都不对**")
                .isEqualTo(CAP_MILLIS_UNSET);
        assertThat(props.getOutboundDeadline().isEnabled())
                .as("出向 deadline 传播应当是开着的（Go 侧 zrpc TimeoutInterceptor 同形）")
                .isTrue();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 断言③：键 = proto 的真实服务全名（不是凭记忆手写的）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("③ 配置的每个键都真实存在于仓内 proto 的服务全名里（package.Service）")
    void configuredKeysAreRealProtoServiceFullNames() {
        Set<String> realFullNames = new LinkedHashSet<>();
        realFullNames.addAll(protoServiceFullNames(ARTICLE_PROTO));
        realFullNames.addAll(protoServiceFullNames(USER_PROTO));

        assertThat(realFullNames)
                .as("🔴 连仓内 proto 的服务全名都抽不出来 ⇒ ⇒ 判据本身失效（不是被测物坏了）")
                .contains("article.ArticleService", "user.UserService");

        GoStyleProtectionProperties props = bindArticleApi();
        for (String key : props.getOutboundDeadline().getCapMillisByService().keySet()) {
            assertThat(realFullNames)
                    .as("🔴 配置的键 `%s` 在仓内 proto 里**没有**对应的服务全名 ⇒ ⇒ "
                            + "运行时 `MethodDescriptor#getServiceName()` 永远不会等于它 ⇒ ⇒ 这一条配置是死的",
                            key)
                    .contains(key);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 断言④：运行时的取名方式 = 全名（证明「键 = 全名」这个选择不是随手起的）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("④ grpc-java 的 MethodDescriptor#getServiceName() 返回的正是 package.Service 全名 ⇒ 键的取法对得上")
    void grpcJavaServiceNameIsTheFullName() {
        MethodDescriptor<byte[], byte[]> method = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("article.ArticleService/CreateArticle")
                .setRequestMarshaller(new NoopMarshaller())
                .setResponseMarshaller(new NoopMarshaller())
                .build();

        assertThat(method.getServiceName())
                .as("🔴 grpc-java 取到的服务名不是 `package.Service` 全名 ⇒ ⇒ "
                        + "那 per-service 覆盖表的键就必须另取一个值（本实现取全名）")
                .isEqualTo("article.ArticleService");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 工具
    // ────────────────────────────────────────────────────────────────────────────

    /** 用 Spring 自己的解析链加载**真实生产 yml**并绑到真实属性类。 */
    private static GoStyleProtectionProperties bindArticleApi() {
        Path yml = repoRoot().resolve(ARTICLE_API_YML);
        assertThat(Files.isReadable(yml))
                .as("🔴 生产配置文件不见了 ⇒ ⇒ 本门判据失效（仓根不对？）")
                .isTrue();

        List<PropertySource<?>> sources = new ArrayList<>();
        try {
            sources.addAll(new YamlPropertySourceLoader()
                    .load(ARTICLE_API_YML, new ByteArrayResource(read(yml).getBytes(StandardCharsets.UTF_8))));
        } catch (IOException e) {
            throw new IllegalStateException("生产 yml 解析失败 ⇒ ⇒ 判据本身失效", e);
        }

        GoStyleProtectionProperties bound = new Binder(ConfigurationPropertySources.from(sources))
                .bind("rag.protection", Bindable.of(GoStyleProtectionProperties.class))
                .orElseGet(GoStyleProtectionProperties::new);
        return bound;
    }

    /** 从仓内真实 .proto 里抽出 {@code package.Service} 全名集合。 */
    private static Set<String> protoServiceFullNames(String relPath) {
        Path proto = repoRoot().resolve(relPath);
        assertThat(Files.isReadable(proto))
                .as("🔴 proto 不可读（%s）⇒ ⇒ 判据本身失效", relPath)
                .isTrue();
        String text = read(proto);

        Matcher pkg = PROTO_PACKAGE.matcher(text);
        assertThat(pkg.find())
                .as("🔴 proto 里找不到 package 声明（%s）⇒ ⇒ 判据本身失效", relPath)
                .isTrue();
        String packageName = pkg.group(1);

        Set<String> out = new LinkedHashSet<>();
        Matcher svc = PROTO_SERVICE.matcher(text);
        while (svc.find()) {
            out.add(packageName + "." + svc.group(1));
        }
        return out;
    }

    /** 仓根：向上找带 {@code service/common/pom.xml} 的那一层（不依赖 surefire 的工作目录）。 */
    private static Path repoRoot() {
        Path base = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 6 && base != null; up++) {
            if (Files.isReadable(base.resolve("service/common/pom.xml"))) {
                return base;
            }
            base = base.getParent();
        }
        throw new IllegalStateException("找不到仓根（向上 6 层都没见到 service/common/pom.xml）");
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("文件不可读：" + p, e);
        }
    }

    /** 只用于构造 {@link MethodDescriptor} 的占位编解码器（本门不真的发请求）。 */
    private static final class NoopMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public java.io.InputStream stream(byte[] value) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        @Override
        public byte[] parse(java.io.InputStream stream) {
            return new byte[0];
        }
    }
}
