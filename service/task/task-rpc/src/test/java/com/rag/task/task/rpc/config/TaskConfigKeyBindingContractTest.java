package com.rag.task.task.rpc.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 守门测试：<b>task 域 yaml 的每一个键都必须有消费方</b>（配置键名不得静默失效）。
 *
 * <h2>它守的是什么</h2>
 * <p>2026-10-24 查出的第 6 条 rpc 漂移（DRIFT-TASK-RPC-006）就是这一类：
 * {@code @KafkaListener(properties = "auto-offset-reset=earliest")} 写成了<b>连字符</b>形式，
 * 而 Kafka 的合法配置名是<b>点分</b>的 {@code auto.offset.reset} ⇒ Kafka 静默忽略 ⇒
 * 「配置意图一致」是<b>假的</b>。见 {@code TaskKafkaOffsetResetContractTest}。
 *
 * <p>那一条是 Kafka 侧的键名。本测试守的是<b>同一个失效家族在 yaml 侧的形态</b>：
 * yaml 里写了一个键，但<b>没有任何东西会读它</b> ⇒ 运维改 yaml 不报错、也不生效。
 * 典型成因有三个：
 * <ol>
 *   <li><b>前缀写错层级</b>：本测试实测抓到 —— {@code spring.mybatis-plus.*} 是死键
 *       （MyBatis-Plus 的真实前缀是<b>顶层</b> {@code mybatis-plus.*}，
 *       见 {@code MybatisPlusProperties} 的 {@code @ConfigurationProperties(prefix = "mybatis-plus")}）；</li>
 *   <li><b>框架版本没有这个功能</b>：{@code otel.*} 在 Boot 4.0.8 下<b>完全没有自动配置</b>
 *       （Boot 3.2.5 的 actuator-autoconfigure 里还有 {@code OpenTelemetryAutoConfiguration}，
 *       Boot 4 把它整块拆走了）⇒ 键还在、没人读；</li>
 *   <li><b>依赖根本没引</b>：{@code spring.jpa.*} 在 task-rpc 下无 JPA 依赖 ⇒ 无人绑定。</li>
 * </ol>
 *
 * <h2>扫描方法：三源交叉</h2>
 * <p>一个 yaml 键要算「有消费方」，必须落在下面三类之一：
 * <ol>
 *   <li><b>框架元数据</b>：classpath 上任意 jar 的
 *       {@code META-INF/spring-configuration-metadata.json} 里出现该属性名
 *       （Boot 4 每个 starter 都带这个文件 ⇒ 这是框架键的权威清单）；</li>
 *   <li><b>自有前缀</b>：{@code @ConfigurationProperties(prefix = "...")} 声明的前缀
 *       （本域：{@code task.definitions} / {@code task.kafka}；跨域共享：
 *       {@code observability} / {@code rag.protection}）；</li>
 *   <li><b>{@code @Value} 占位符</b>：源码里 {@code @Value("${key}")} 引用过它。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么要显式登记白名单，而不是「扫到孤儿就忽略」</h2>
 * <p>本测试的<b>白名单是「已知孤儿键」的显式台账</b>，每一条都带出处与定性。
 * 新增 yaml 键若无人认领 ⇒ <b>测试变红</b> ⇒ 必须在白名单里显式登记才能过。
 * <b>静默放过 = 把「配置面看起来在管事、实际没在管事」重新引进来</b>（AGENTS.md §3.3.1）。
 *
 * <h2>阳性对照（必须有，否则本测试可能「什么都没扫到」而假绿）</h2>
 * <p>断言 2 用一个<b>故意不存在的键</b>证明扫描器确实有识别能力；
 * 断言 1 证明框架元数据确实被读到了（而不是因为路径写错而读到空集）。
 */
class TaskConfigKeyBindingContractTest {

    /** 本域自己的 {@code @ConfigurationProperties} 前缀。 */
    private static final List<String> OWN_PREFIXES = List.of(
            "task.definitions",   // TaskSinkProperties
            "task.kafka");        // TaskKafkaTopics

    /** 跨域共享前缀（由 service/common 提供，本域 yaml 消费）。 */
    private static final List<String> SHARED_PREFIXES = List.of(
            "observability",      // common 的 ObservabilityProperties
            "rag.protection");    // common 的 GoStyleProtectionProperties

    /** task-api 侧的 yaml（api 模块自己的资源，这里只做键名扫描，不做绑定）。 */
    private static final List<String> YAML_RESOURCES = List.of(
            "application.yml");

    /**
     * 已知孤儿键白名单 —— <b>每一条都是「实测确认无人消费」</b>，不是「懒得查」。
     *
     * <p>格式：{@code 键前缀 -> 原因}。
     * <ul>
     *   <li>{@code spring.mybatis-plus.} —— 🔴 <b>前缀层级写错</b>：MyBatis-Plus 的真实前缀是顶层
     *       {@code mybatis-plus.}（{@code MybatisPlusProperties} 的
     *       {@code @ConfigurationProperties(prefix = "mybatis-plus")}），
     *       写在 {@code spring.} 下 ⇒ 三个键全是死键。
     *       <b>影响</b>：{@code map-underscore-to-camel-case} 恰好等于 MyBatis-Plus 自己的默认
     *       （{@code MybatisConfiguration} 构造器里 {@code mapUnderscoreToCamelCase = true}），
     *       {@code global-config.banner=false} 只影响启动横幅，
     *       {@code db-config.id-type=INPUT} 在 task-rpc 无使用点（全部写显式 SQL，不用 BaseMapper）
     *       ⇒ <b>三处都无行为差异</b>，但配置面是死的。属<b>跨域共性</b>（全仓 8 个 rpc 模块同款）。</li>
     *   <li>{@code otel.} —— 🔴 <b>Boot 4 没有 OTel 自动配置</b>：Boot 4.0.8 全部 jar 里
     *       搜不到任何 {@code Otel*} 自动配置类（Boot 3.2.5 的 actuator-autoconfigure 里
     *       至少还有 {@code OpenTelemetryAutoConfiguration}），
     *       且本仓无任何 Java 代码读 {@code otel.*} ⇒ 四个键全无消费方。
     *       <b>与 Go 的差异</b>：Go 侧 {@code zrpc.MustNewServer} → {@code RpcServerConf}
     *       继承 {@code ServiceConf} → {@code serviceconf.go:67 trace.StartAgent(sc.Telemetry)}
     *       ⇒ <b>Go 确实导出 span 到 jaeger:4317</b>，Java 侧没有。
     *       属<b>跨域共性</b>（全仓 20 个 yaml 都有 otel 段）。</li>
     *   <li>{@code spring.jpa.} —— ⚠️ <b>本模块无 JPA 依赖</b>：task-rpc 用 MyBatis-Plus，
     *       pom 里没有 spring-boot-starter-data-jpa ⇒ {@code ddl-auto} 无人绑定。
     *       <b>无行为影响</b>（本就没有 JPA，不存在「自动建表」风险）。</li>
     * </ul>
     */
    private static final Map<String, String> KNOWN_ORPHAN_PREFIXES = new LinkedHashMap<>();

    static {
        KNOWN_ORPHAN_PREFIXES.put("spring.mybatis-plus.",
                "前缀层级写错（MyBatis-Plus 真实前缀是顶层 mybatis-plus.）⇒ 三个键全是死键；"
                        + "三处均无行为差异（map-underscore 恰等于框架默认、banner 只影响启动横幅、"
                        + "id-type 在本模块无使用点）；跨域共性，全仓 8 个 rpc 模块同款");
        KNOWN_ORPHAN_PREFIXES.put("otel.",
                "Boot 4.0.8 无任何 OTel 自动配置类（Boot 3.2.5 尚有 OpenTelemetryAutoConfiguration）"
                        + "且本仓无代码读 otel.* ⇒ 四个键全无消费方；"
                        + "Go 侧 zrpc→ServiceConf→serviceconf.go:67 trace.StartAgent 确实导出 span ⇒ 存在观测差异；"
                        + "跨域共性，全仓 20 个 yaml 都有 otel 段");
        KNOWN_ORPHAN_PREFIXES.put("spring.jpa.",
                "task-rpc 无 spring-boot-starter-data-jpa 依赖 ⇒ ddl-auto 无人绑定；"
                        + "无行为影响（本就没有 JPA，不存在自动建表风险）");
        KNOWN_ORPHAN_PREFIXES.put("task.reward.group-name",
                "Go 侧 reward.GroupName 是**写死的常量**（reward/type.go:10 GroupName = \"task-reward-group\"），"
                        + "Go yaml 里根本没有这个字段；且唯一消费点 Worker 未启动（task.go:35 StartInitReward 被注释、"
                        + "initReward.go 整文件是注释）⇒ **两侧都是死配置**。RAG 保留它只为配置面完整，"
                        + "与 Go 一致（Go 是「没有」，RAG 是「有但不生效」⇒ 不改变任何行为）");
    }

    /** {@code @Value("${key}")} 的匹配式。 */
    private static final Pattern VALUE_PATTERN = Pattern.compile("@Value\\(\\s*\"\\$\\{([^:}]+)");

    /**
     * <b>map 型</b>框架属性的前缀 —— 它们的子键是运行时的任意 map key，
     * 元数据里不会逐个列出（Boot 4 的 metadata 只列<b>父节点</b>，如 {@code logging.level}）
     * ⇒ 必须显式登记，否则会被误判成孤儿。
     *
     * <p>依据：
     * <ul>
     *   <li>{@code spring-boot-4.0.8.jar} 的 metadata 里有 {@code logging.level}
     *       （无任何 {@code logging.level.xxx} 子条目）；</li>
     *   <li>{@code spring-boot-micrometer-metrics-4.0.8.jar} 的 metadata 里有
     *       {@code management.metrics.tags}（同样无子条目）；</li>
     *   <li>{@code spring-boot-kafka-4.0.8.jar} 的 {@code spring.kafka.admin.properties}
     *       是 {@code Map<String,String>}。</li>
     * </ul>
     */
    private static final List<String> MAP_VALUED_METADATA_PREFIXES = List.of(
            // Boot 4 的 metadata 只列父节点，子键任意
            "logging.level",
            "management.metrics.tags",
            // map 型属性
            "spring.kafka.admin.properties",
            "spring.kafka.producer.properties",
            "spring.kafka.consumer.properties",
            "spring.kafka.listener.properties",
            "spring.kafka.streams.properties");

    // ------------------------------------------------------------------
    // 断言 1 · 框架元数据确实被读到（阳性对照：证明扫描器不是空跑）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("阳性对照 · 框架元数据必须真的被读到（否则下面的孤儿键判定全是空跑）")
    void frameworkMetadataIsActuallyRead() throws IOException {
        Set<String> metadata = loadFrameworkMetadata();

        assertTrue(metadata.size() > 500,
                "框架元数据只读到 " + metadata.size() + " 个属性名 ⇒ 扫描器多半没读到 jar 内的 "
                        + "spring-configuration-metadata.json，本测试会假绿");
        assertTrue(metadata.contains("spring.kafka.consumer.auto-offset-reset"),
                "元数据里必须有 spring.kafka.consumer.auto-offset-reset（Boot 4 的 KafkaProperties）");
        assertTrue(metadata.contains("mybatis-plus.configuration.map-underscore-to-camel-case"),
                "元数据里必须有 mybatis-plus.configuration.map-underscore-to-camel-case"
                        + " ⇒ 证明 MyBatis-Plus 的前缀是**顶层** mybatis-plus. 而非 spring.mybatis-plus.");
        // ⚠️ 不能断言 multipart 键：task-rpc **无 web 依赖** ⇒ classpath 上没有
        // spring-boot-servlet ⇒ 那份元数据根本不在（那是 task-api 侧的键，task-api 有自己的测试）。
        assertFalse(metadata.contains("spring.mvc.servlet.multipart.enabled"),
                "Boot 4 里 multipart 的正确键名是 spring.servlet.multipart.enabled；"
                        + "若元数据里出现 mvc. 形态，说明 Boot 版本判断错了");
    }

    // ------------------------------------------------------------------
    // 断言 2 · 扫描器确实能识别孤儿键（阳性对照）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("阳性对照 · 扫描器必须能识别「无人认领的键」")
    void scannerCanDetectOrphanKeys() {
        Set<String> metadata = loadFrameworkMetadataUnchecked();
        Set<String> owned = ownedKeyPrefixes();

        // 一个**故意不存在**的键 ⇒ 必须被判为孤儿。
        assertFalse(isClaimed("spring.kafka.consumer.this-key-does-not-exist", metadata, owned),
                "扫描器把不存在的键也判成「有消费方」⇒ 扫描器无识别能力，本测试全是假绿");
        // 一个**故意写错层级**的键（真键 + 错误前缀）⇒ 同样必须是孤儿。
        assertFalse(isClaimed("spring.mybatis-plus.configuration.map-underscore-to-camel-case",
                        metadata, owned),
                "扫描器把 spring.mybatis-plus.* 判成有消费方 ⇒ 层级错位的键会静默溜过");
        // 对照组：一个**真实存在**的键必须被判为有消费方。
        assertTrue(isClaimed("spring.kafka.consumer.auto-offset-reset", metadata, owned),
                "扫描器把真实存在的框架键判成孤儿 ⇒ 扫描器逻辑反了");
    }

    // ------------------------------------------------------------------
    // 断言 3 · 主断言：task-rpc 的 yaml 不得有「未登记的孤儿键」
    // ------------------------------------------------------------------

    @Test
    @DisplayName("主断言 · task-rpc 的 application.yml 不得出现「未登记的孤儿键」")
    void taskRpcYamlHasNoUnregisteredOrphanKeys() throws IOException {
        Map<String, Object> flat = flattenYaml(loadYaml("application.yml"));
        Set<String> metadata = loadFrameworkMetadata();
        Set<String> owned = ownedKeyPrefixes();

        Set<String> unregistered = new TreeSet<>();
        for (String key : flat.keySet()) {
            if (isClaimed(key, metadata, owned)) {
                continue;
            }
            if (matchesKnownOrphan(key)) {
                continue;
            }
            unregistered.add(key);
        }

        assertTrue(unregistered.isEmpty(),
                "task-rpc/application.yml 出现「无人认领且未登记」的键：" + unregistered
                        + "\n  ⇒ 这些键改了不会生效（配置面看起来在管事、实际没在管事，AGENTS.md §3.3.1）。"
                        + "\n  ⇒ 修法二选一：① 改对键名/层级；② 若确实无人消费（跨域共性），"
                        + "在本测试的 KNOWN_ORPHAN_PREFIXES 里显式登记并写明原因。");
    }

    // ------------------------------------------------------------------
    // 断言 4 · 白名单本身要「窄」：登记的前缀必须真的对得上孤儿键
    // ------------------------------------------------------------------

    @Test
    @DisplayName("白名单纪律 · 每条登记的前缀都必须真的盖住至少一个孤儿键（防止白名单写成空壳）")
    void knownOrphanRegistryIsNotVacuous() throws IOException {
        Map<String, Object> flat = flattenYaml(loadYaml("application.yml"));
        Set<String> metadata = loadFrameworkMetadata();
        Set<String> owned = ownedKeyPrefixes();

        for (Map.Entry<String, String> entry : KNOWN_ORPHAN_PREFIXES.entrySet()) {
            String registered = entry.getKey();
            List<String> covered = flat.keySet().stream()
                    .filter(k -> !isClaimed(k, metadata, owned))
                    .filter(k -> matchesKnownOrphan(k))
                    .filter(k -> registered.endsWith(".")
                            ? k.startsWith(registered)
                            : k.equals(registered))
                    .toList();
            assertFalse(covered.isEmpty(),
                    "白名单项 '" + registered + "' 一个孤儿键都没盖到 ⇒ 要么孤儿键已消失（该删这条登记），"
                            + "要么登记键写错了。本模块的孤儿键清单：" + orphanKeys(flat, metadata, owned));
            assertFalse(entry.getValue().isBlank(),
                    "白名单项 '" + registered + "' 必须写明原因（空原因 = 没查过）");
        }
    }

    // ------------------------------------------------------------------
    // 断言 5 · 自有前缀的键必须真的能被 @ConfigurationProperties 认领
    // ------------------------------------------------------------------

    @Test
    @DisplayName("自有前缀 · task.* 的每个键都必须在 @ConfigurationProperties 类上找得到")
    void ownPrefixKeysAreAllClaimedByPropertiesClasses() throws IOException {
        Map<String, Object> flat = flattenYaml(loadYaml("application.yml"));

        Set<String> ownKeys = flat.keySet().stream()
                .filter(k -> OWN_PREFIXES.stream().anyMatch(p -> k.startsWith(p + ".")))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertFalse(ownKeys.isEmpty(), "没扫到任何 task.* 键 ⇒ yaml 路径或前缀表错了，本测试会假绿");

        // 逐个自有前缀，用 Spring 自己的 Binder 验「这个键能被认领吗」。
        for (String prefix : OWN_PREFIXES) {
            Class<?> target = propertiesClassFor(prefix);
            assertTrue(target != null, "前缀 " + prefix + " 没有对应的 @ConfigurationProperties 类");

            for (String key : ownKeys) {
                if (!key.startsWith(prefix + ".")) {
                    continue;
                }
                String sub = key.substring(prefix.length() + 1);
                // 用真实 Binder 判定：能否从该前缀绑出这个键。
                boolean bound = canBind(prefix, target, sub);
                assertTrue(bound,
                        "键 " + key + " 在 yaml 里存在，但绑不到 " + target.getSimpleName()
                                + " ⇒ 键名与属性名对不上（relaxed binding 不匹配）⇒ 静默丢弃。"
                                + "历史上 TaskSinkProperties.initTtlDays ↔ init-ttl 就是这一类。");
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 找出所有孤儿键（供失败信息与白名单纪律断言使用）。 */
    private static List<String> orphanKeys(Map<String, Object> flat, Set<String> metadata, Set<String> owned) {
        return flat.keySet().stream()
                .filter(k -> !isClaimed(k, metadata, owned))
                .sorted()
                .toList();
    }

    /**
     * 一个键是否「有消费方」。
     *
     * <p>判定顺序：自有前缀 → 框架元数据（按段边界匹配，支持 {@code x.y.*} 形态）→ @Value 占位符。
     */
    private static boolean isClaimed(String key, Set<String> metadata, Set<String> owned) {
        for (String prefix : owned) {
            if (key.equals(prefix) || key.startsWith(prefix + ".")) {
                return true;
            }
        }
        if (claimedByMetadata(key, metadata)) {
            return true;
        }
        return VALUE_PLACEHOLDERS.contains(key);
    }

    /** 框架元数据匹配：按段边界，支持 {@code a.b.*} 这种通配形态。 */
    /**
     * 框架元数据匹配：<b>精确名</b>，或命中显式登记的「map 型前缀」。
     *
     * <p>⚠️ <b>不能用「分段前缀」匹配</b>：元数据里 {@code spring.kafka.consumer} 这种<b>父节点</b>
     * 并不存在（Boot 只列叶子属性名），若按「逐段前缀命中即放行」，
     * 任何 {@code spring.kafka.consumer.<随便什么>} 都会被判成「有消费方」
     * ⇒ 扫描器形同虚设（这正是本测试第一版的 bug，已由阳性对照抓住）。
     *
     * <p>真正的「任意子键」只出现在 <b>map 型属性</b>上（如
     * {@code spring.kafka.admin.properties} 是 {@code Map<String,String>}），
     * 那类必须<b>显式登记</b>，不能靠猜。
     */
    private static boolean claimedByMetadata(String key, Set<String> metadata) {
        if (metadata.contains(key)) {
            return true;
        }
        for (String mapPrefix : MAP_VALUED_METADATA_PREFIXES) {
            if (key.startsWith(mapPrefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否命中已登记的孤儿键。
     *
     * <p>登记项有两种语义：
     * <ul>
     *   <li><b>前缀</b>（以 {@code "."} 结尾）：盖住该前缀下的所有键
     *       （如 {@code otel.}、{@code spring.mybatis-plus.}）；</li>
     *   <li><b>精确键</b>（不以 {@code "."} 结尾）：只盖住那一个键
     *       （如 {@code task.reward.group-name}）。</li>
     * </ul>
     */
    private static boolean matchesKnownOrphan(String key) {
        for (String registered : KNOWN_ORPHAN_PREFIXES.keySet()) {
            if (registered.endsWith(".")) {
                if (key.startsWith(registered)) {
                    return true;
                }
            } else if (key.equals(registered)) {
                return true;
            }
        }
        return false;
    }

    /** 自有前缀（含跨域共享前缀）集合。 */
    private static Set<String> ownedKeyPrefixes() {
        Set<String> s = new LinkedHashSet<>();
        s.addAll(OWN_PREFIXES);
        s.addAll(SHARED_PREFIXES);
        return s;
    }

    /** 前缀 → 对应的 {@code @ConfigurationProperties} 类。 */
    private static Class<?> propertiesClassFor(String prefix) {
        return switch (prefix) {
            case "task.definitions" -> TaskSinkProperties.class;
            case "task.kafka" -> TaskKafkaTopics.class;
            default -> null;
        };
    }

    /**
     * 用真实 Binder 判定「这个 yaml 键有没有被绑到目标类的某个属性上」。
     *
     * <p>⚠️ <b>不能</b>用 {@code binder.bind(prefix + "." + subKey, Target.class)}：
     * 那条路径要求 {@code Target} 自身有<b>扁平的</b>属性名，而本域的属性是<b>嵌套</b>的
     * （{@code TaskSinkProperties.like.initTtl}）⇒ 那样判必然全部「绑不上」（本测试第一版的 bug）。
     *
     * <p>正确做法：{@code binder.bind(prefix, Target.class, handler)}，
     * 用 {@link org.springframework.boot.context.properties.bind.BoundPropertiesTrackingBindHandler}
     * 记录<b>真正被绑定的属性名</b> ⇒ 再拿 yaml 的键去比对。
     */
    private static boolean canBind(String prefix, Class<?> target, String subKey) {
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"));
            org.springframework.core.env.MutablePropertySources sources =
                    new org.springframework.core.env.MutablePropertySources();
            loaded.forEach(sources::addLast);
            org.springframework.boot.context.properties.bind.Binder binder =
                    new org.springframework.boot.context.properties.bind.Binder(
                            org.springframework.boot.context.properties.source.ConfigurationPropertySources.from(sources));

            Set<String> bound = new LinkedHashSet<>();
            org.springframework.boot.context.properties.bind.BoundPropertiesTrackingBindHandler handler =
                    new org.springframework.boot.context.properties.bind.BoundPropertiesTrackingBindHandler(
                            (org.springframework.boot.context.properties.source.ConfigurationProperty p)
                                    -> bound.add(p.getName().toString()));
            binder.bind(prefix,
                    org.springframework.boot.context.properties.bind.Bindable.of(target),
                    handler);

            // bound 里是完整属性名（如 task.definitions.like.init-ttl）
            // yaml 键也是完整属性名 ⇒ 直接比对（同时容忍大小写/连字符差异）
            String normalizedYamlKey = normalize(subKey);
            for (String b : bound) {
                String relative = b.startsWith(prefix + ".") ? b.substring(prefix.length() + 1) : b;
                if (normalize(relative).equals(normalizedYamlKey)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 归一化属性名：去分隔符 + 小写（对应 relaxed binding 的比较口径）。 */
    private static String normalize(String key) {
        return key.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 读 yaml 并压平成一维键。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> flattenYaml(Map<String, Object> nested) {
        Map<String, Object> out = new LinkedHashMap<>();
        flatten("", nested, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Object node, Map<String, Object> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(e.getKey())
                        : prefix + "." + e.getKey();
                flatten(key, e.getValue(), out);
            }
        } else {
            out.put(prefix, node);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String resource) throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource(resource));
        Map<String, Object> merged = new LinkedHashMap<>();
        for (PropertySource<?> ps : loaded) {
            merged.putAll((Map<String, Object>) ps.getSource());
        }
        return merged;
    }

    /**
     * 读 classpath 上所有 jar 的 {@code META-INF/spring-configuration-metadata.json}。
     *
     * <p>用 {@code ClassLoader.getResources} 枚举 —— 这样既覆盖普通 jar，也覆盖
     * Spring Boot 的 fat jar / nested jar 布局。
     */
    private static Set<String> loadFrameworkMetadata() {
        Set<String> names = new LinkedHashSet<>();
        try {
            Enumeration<java.net.URL> urls = TaskConfigKeyBindingContractTest.class.getClassLoader()
                    .getResources("META-INF/spring-configuration-metadata.json");
            while (urls.hasMoreElements()) {
                java.net.URL url = urls.nextElement();
                if ("jar".equals(url.getProtocol())) {
                    readFromJar(url.getPath(), names);
                } else if ("file".equals(url.getProtocol())) {
                    readFromFile(Paths.get(url.getPath()), names);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取框架配置元数据失败", e);
        }
        return names;
    }

    /** 读 jar 内的元数据。 */
    private static void readFromJar(String urlPath, Set<String> names) {
        int bang = urlPath.indexOf("!");
        String jarPath = urlPath.substring(0, bang < 0 ? urlPath.length() : bang);
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring("file:".length());
        }
        try (JarFile jar = new JarFile(jarPath)) {
            try (InputStream in = jar.getInputStream(jar.getEntry("META-INF/spring-configuration-metadata.json"))) {
                collect(in, names);
            }
        } catch (Exception ignored) {
            // 某些条目不可读（如嵌套 jar 路径）⇒ 跳过，不影响其余来源
        }
    }

    /** 读目录形式的元数据。 */
    private static void readFromFile(Path file, Set<String> names) {
        try {
            collect(Files.newInputStream(file), names);
        } catch (Exception ignored) {
            // 同上
        }
    }

    /** 从 JSON 文本里抽属性名（不引 Jackson，避免额外依赖；用轻量正则）。 */
    private static void collect(InputStream in, Set<String> names) throws IOException {
        String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        while (m.find()) {
            names.add(m.group(1));
        }
    }

    /** 不抛异常的元数据读取（供纯逻辑断言用）。 */
    private static Set<String> loadFrameworkMetadataUnchecked() {
        try {
            return loadFrameworkMetadata();
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    /** 源码里所有 {@code @Value("${key}")} 的 key。 */
    private static final Set<String> VALUE_PLACEHOLDERS = scanValuePlaceholders();

    private static Set<String> scanValuePlaceholders() {
        Set<String> keys = new LinkedHashSet<>();
        Path srcRoot = findSourceRoot();
        if (srcRoot == null) {
            return keys;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(srcRoot)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    Matcher m = VALUE_PATTERN.matcher(Files.readString(p));
                    while (m.find()) {
                        keys.add(m.group(1));
                    }
                } catch (IOException ignored) {
                    // 读不到就跳过
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
        return keys;
    }

    /** 定位 task-rpc 的源码根（用于扫 {@code @Value}）。 */
    private static Path findSourceRoot() {
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && cur != null; i++) {
            Path candidate = cur.resolve("src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        return null;
    }

    /** 未使用但保留：便于将来扩展 api 模块的扫描。 */
    @SuppressWarnings("unused")
    private static List<String> yamlResources() {
        return YAML_RESOURCES;
    }
}
