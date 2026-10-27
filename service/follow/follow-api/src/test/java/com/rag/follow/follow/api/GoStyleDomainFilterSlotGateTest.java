package com.rag.follow.follow.api;

import com.rag.follow.follow.api.auth.GoStyleJwtAuthFilter;
import com.rag.follow.follow.api.client.FollowRpcClient;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🟢 <b>G1 域侧 Filter 槽位守门</b> —— 闭合共享层台账 G4 §4.10.21 明确留下的<b>「域侧那一半」</b>。
 *
 * <h2>为什么是「域侧那一半」</h2>
 * <p>共享层 G4 的 {@code SharedFilterOrderSnapshotGateTest}（{@code service/common/src/test/java/
 * com/rag/common/spring/SharedFilterOrderSnapshotGateTest.java}）已经闭合了<b>共享层那一半</b>：
 * 共享层「产出」的带 {@code @Order} 的 Filter 全集 == 它自己的槽位快照 ⇒ 共享层新增第 8 个 Filter 必红。
 * <p>但那份守门的<b>枚举面 = {@code service/common} 本模块的编译产物</b> ⇒ ⇒ 它<b>看不见域侧产出的 Filter</b>
 * ⇒ ⇒ 「<b>域侧新增 Filter ⇒ 不得吃掉共享层的 +4/+5/+6</b>」这条约束<b>至今没有任何机器保障</b>
 * ⇒ ⇒ 缺口落在<b>域侧文件</b> ⇒ ⇒ <b>域侧落码</b>（共享层台账 §4.10.21.6 的边界 + §4.10.18.3 的「可行落法方向 ① 域内」）。
 *
 * <h2>本门禁的四条断言</h2>
 * <ol>
 *   <li><b>守恒面（核心）</b>：本模块<b>产出</b>的带 {@code @Order} 的 Filter 全集 == <b>域侧登记清单</b>
 *       （<b>双向相等</b>，不是 ⊆）⇒ ⇒ <b>域侧新增任何一个 Filter 必红</b> ⇒ ⇒ 必须<b>显式登记</b>它占了哪个槽位。</li>
 *   <li><b>槽位面</b>：登记的每个 Filter 的<b>实际</b>槽位 == 登记槽位（读<b>实际</b> {@code @Order}，不是快照）。</li>
 *   <li><b>排他面</b>：域侧 Filter <b>不得占用</b>共享层的独占功能槽位 <b>+4（MaxConns）/ +5（Breaker）</b>
 *       —— 那两个 Filter <b>都写响应</b> ⇒ 同槽 ⇒ 相对次序由容器注册序决定 ⇒ <b>不可确定</b>。</li>
 *   <li><b>真装配面</b>：登记的每个域侧 Filter <b>真的被挂进容器装配出的链</b>，且排在 <b>3s 预算起点</b>
 *       （{@code HttpMiddlewareFilter}）<b>之前</b> ⇒ ⇒ 「验签在 3s 预算之外」这条不变式对<b>每一个</b>域侧 Filter 成立。</li>
 * </ol>
 *
 * <h2>槽位口径与权威表</h2>
 * <p>共享层的权威槽位表在共享层台账 G4 §4.10.16.1（<b>7 个</b>共享层 Filter，逐条带 {@code @Order} 注解行号）：
 * <pre>
 *   +2 PathNormalizing · +3 Traceparent · +4 MaxConns · +5 Breaker
 *   +6 CpuShedding · +7 HttpMiddleware（3s 预算起算点）· +8 MaxBytes
 * </pre>
 * ⇒ ⇒ <b>域侧可用区间 = 「[+3, 预算起点)」的最后一个槽位（= +6）</b>（共享层台账 §4.10.16.2 的三条合并后推理）：
 * <b>①</b> {@code Traceparent(+3)} 必须 ≤ +6 ⇒ 域侧必须 ≥ +3（否则验签落到 SERVER span 外 ⇒ 观测面漂移）；
 * <b>②</b> +2 不可用（与 {@code PathNormalizing} 同槽 ⇒ 相对次序由注册序决定 ⇒ 非确定）；
 * <b>③</b> +7 = 预算起点 ⇒ 不可用（否则验签落进 3s 预算 ⇒ 「413 压过 401」的不变式会变）。
 * ⇒ ⇒ 域侧与共享层<b>必然同槽</b> ⇒ ⇒ 同槽的相对次序<b>不可确定</b> ⇒ ⇒ 「并列 +6 安全」的唯一前提是
 * <b>域侧那个 Filter 绝不写响应</b>（401 仍由 interceptor 抛）⇒ ⇒ 本域 {@code GoStyleJwtAuthFilter}
 * 只把验签结果放进 request attribute，<b>不写响应</b> ⇒ ⇒ 并列成立。
 *
 * <h2>为什么域侧要「各守自己那份」</h2>
 * <p>共享层台账 §4.10.21.6 的处方：<b>「各模块各守自己那份 ⇒ 避免跨模块的路径依赖」</b>
 * ⇒ ⇒ 本类只枚举<b>本模块自己的编译产物</b> ⇒ ⇒ 三个 api 各有一份（user-api / admin-api / follow-api）。
 *
 * <h2>⚠️ 为什么按 <b>bean 名</b> 定位共享层的 Filter，而不像共享层那样 import</h2>
 * <p>本域的生产代码与测试代码对共享层 Filter <b>零 import</b>（只按 bean 名定位）⇒ ⇒
 * <b>共享层辐射度的事实底座</b>（{@code tools/difftest/check-wave-coupling.py} 的 {@code common_radiation}，
 * 按 import 统计）<b>保持不变</b> ⇒ ⇒ 不制造跨 lane 的快照漂移（那张快照归整合者，本 lane 无写权）。
 *
 * <h2>🔴 变异验证（本类不是假绿）</h2>
 * <pre>
 *   变异 A（产品侧 ·「多一个」）：在 follow-api 新增第 2 个带 @Order 的 Filter
 *         ⇒ ① 红（产出全集 ≠ 登记清单），红在目标断言上
 *   变异 B（产品侧 ·「换槽位」）：把 GoStyleJwtAuthFilter 的 @Order 改成 +4
 *         ⇒ ② 红（实际槽位 ≠ 登记槽位）且 ③ 红（占用了共享层独占槽位）
 *   变异 C（产品侧 ·「摘注册」）：摘掉 GoStyleJwtAuthFilter 的 @Component
 *         ⇒ ④ 红（bean 在、链里没有 ⇒ 根本没跑）
 * </pre>
 *
 * @see com.rag.follow.follow.api.FollowApiJwtVerificationScopeTest
 *      —— 同一不变式的<b>行为层 + 单个 Filter 的次序层</b>；本类补的是<b>「域侧再新增一个 Filter」的守恒面</b>。
 */
@SpringBootTest(
        classes = FollowApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // FollowRpcClient 整体被 @MockitoBean 替换；这个 channel bean 不会被用到
                // ⚠️ DRIFT-G1-002 之后 follow-api 开了 immediate-connect-timeout
                //    ⇒ 不 mock 掉 FollowRpcClient 的话上下文**起不来**
                "grpc.client.follow-rpc.address=static://127.0.0.1:65535",
                "grpc.client.follow-rpc.negotiation-type=plaintext",
                "rag.protection.max-bytes=1024",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@DisplayName("G1 域侧 Filter 槽位守门（follow-api）：域侧产出的带 @Order 的 Filter 全集 == 域侧登记清单")
class GoStyleDomainFilterSlotGateTest {

    /**
     * 域侧登记清单 —— 本域允许出现的「带 {@code @Order} 的 Filter」及其槽位。
     *
     * <p><b>这张表就是「域侧新增 Filter ⇒ 必红」的落点</b>：新增一个 Filter 而不登记 ⇒ ① 红；
     * 登记了却把槽位改成共享层独占的 +4/+5 ⇒ ③ 红。
     * <p>⚠️ <b>改这张表 = 明知故犯</b>（与共享层改 {@code SLOT_SNAPSHOT} 同款语义）：
     * 必须同时想清楚「它与同槽的共享层 Filter 的相对次序不可确定 ⇒ 它自己不写响应吗」。
     */
    private static final Map<Class<? extends Filter>, Integer> DOMAIN_SLOT_REGISTRY = new LinkedHashMap<>();

    static {
        // 域侧唯一的有序 Filter：JWT 验签（@Order(+6)，与共享层 CpuShedding(+6) 并列）
        DOMAIN_SLOT_REGISTRY.put(GoStyleJwtAuthFilter.class, 6);
    }

    /**
     * 共享层的<b>独占功能槽位</b>，域侧不得占用。
     *
     * <p>+4 = {@code GoStyleMaxConnsFilter}（连接数闸门）· +5 = {@code GoStyleBreakerFilter}（熔断）
     * ⇒ ⇒ <b>这两个都写响应</b>（闸门拒绝 / 熔断短路）⇒ ⇒ 域侧若与它们同槽 ⇒ 相对次序由容器注册序决定
     * ⇒ ⇒ 「先拒连还是先验签」「先熔断还是先验签」<b>不可确定</b> ⇒ ⇒ 行为分叉。
     */
    private static final Set<Integer> SHARED_EXCLUSIVE_SLOTS = Set.of(4, 5);

    /**
     * 3s 预算起算点的 <b>bean 名</b>（共享层 {@code HttpMiddlewareFilter}）。
     *
     * <p>⚠️ 按 bean 名定位（而不是 import 那个类）⇒ 不新增域侧对共享层的编译期依赖
     * ⇒ 共享层辐射度快照（{@code common_radiation}）保持不变（见类注释）。
     */
    private static final String BUDGET_FILTER_BEAN_NAME = "httpMiddlewareFilter";

    @Autowired
    private ApplicationContext ctx;

    /** DRIFT-G1-002 之后必须 mock，否则上下文起不来（见类注释）。 */
    @MockitoBean
    private FollowRpcClient followRpcClient;

    /** 黑名单查询打桩：{@code hasKey → null}（= 未拉黑）⇒ 毫秒级返回。 */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    // ==================================================================
    // ① 守恒面（核心）：域侧产出的带 @Order 的 Filter 全集 == 登记清单
    // ==================================================================

    @Test
    @DisplayName("① 域侧「产出」的带 @Order 的 Filter 全集 == 域侧登记清单（域侧新增 Filter 必红）")
    void everyDomainProducedFilterIsRegisteredInTheDomainRegistry() {
        Path classesRoot = domainClassesRoot();
        Set<Class<? extends Filter>> found = enumerateOrderedFilters(classesRoot);
        Set<Class<? extends Filter>> expected = new LinkedHashSet<>(DOMAIN_SLOT_REGISTRY.keySet());

        // ⚠️ 用「双向相等」而不是「⊆」——
        //    ⊆ 在「枚举面失效」时（例如枚举到 0 个）会恒绿 ⇒ 空转；
        //    相等则「枚举不到」与「多出/少了」一样都会红。
        assertThat(found)
                .as("域侧产出的带 @Order 的 Filter 必须与域侧登记清单<b>完全一致</b>："
                        + "多一个（= 新增了域侧 Filter 却没登记槽位）或少一个（= 登记的那个已经不存在）都必须红。"
                        + "\n⚠️ 域侧新增 Filter 必须<b>显式登记</b>它占了哪个槽位（改登记清单 = 显式承认），"
                        + "而不是「加个类就完事」——否则它可能正好吃掉共享层 +4/+5/+6 的槽位。"
                        + "\n枚举根目录：" + classesRoot
                        + "\n实际枚举到：" + describe(found)
                        + "\n登记清单：" + describe(expected))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    // ==================================================================
    // ② 槽位面：实际槽位 == 登记槽位
    // ==================================================================

    @Test
    @DisplayName("② 每个域侧 Filter 的<b>实际</b>槽位 == 登记槽位（读实际 @Order，不是快照）")
    void domainFilterSlotsMatchTheRegistry() {
        for (Map.Entry<Class<? extends Filter>, Integer> e : DOMAIN_SLOT_REGISTRY.entrySet()) {
            Order order = e.getKey().getAnnotation(Order.class);
            assertThat(order)
                    .as("%s 必须有 @Order 注解（没有 ⇒ Spring 给它 LOWEST_PRECEDENCE ⇒ 落链尾 ⇒ 不占槽位）",
                            e.getKey().getSimpleName())
                    .isNotNull();
            assertThat(order.value() - Ordered.HIGHEST_PRECEDENCE)
                    .as("%s 的<b>实际</b>槽位必须等于登记槽位 %d（改槽位必须同时改登记清单 = 显式决策）",
                            e.getKey().getSimpleName(), e.getValue())
                    .isEqualTo(e.getValue());
        }
    }

    // ==================================================================
    // ③ 排他面：不得占用共享层独占槽位 +4/+5
    // ==================================================================

    @Test
    @DisplayName("③ 域侧 Filter 不得占用共享层的独占槽位 +4（MaxConns）/ +5（Breaker）")
    void domainFiltersDoNotOccupySharedExclusiveSlots() {
        for (Class<? extends Filter> type : DOMAIN_SLOT_REGISTRY.keySet()) {
            Order order = type.getAnnotation(Order.class);
            assertThat(order).as("%s 必须有 @Order 注解", type.getSimpleName()).isNotNull();
            int actualSlot = order.value() - Ordered.HIGHEST_PRECEDENCE;

            assertThat(SHARED_EXCLUSIVE_SLOTS)
                    .as("%s 占用了共享层的独占槽位 %d ⇒ 它与共享层那个 Filter <b>同槽</b> ⇒ "
                            + "相对次序由容器注册序决定 ⇒ <b>不可确定</b>；而共享层那两个"
                            + "（MaxConns 连接闸门 / Breaker 熔断）<b>都写响应</b> ⇒ 行为分叉。"
                            + "\n⚠️ 若确实需要这个槽位 ⇒ 必须先证明「同槽时行为与次序无关」。",
                            type.getSimpleName(), actualSlot)
                    .doesNotContain(actualSlot);
        }
    }

    // ==================================================================
    // ④ 真装配面：真的注册进了容器装配出的链，且排在 3s 预算起点之前
    // ==================================================================

    @Test
    @DisplayName("④ 真装配面：域侧 Filter 真的挂进了容器装配出的链，且排在 3s 预算起点之前")
    void domainFiltersAreReallyAssembledBeforeTheBudgetStart() {
        List<ServletContextInitializer> filters = assembledFilters();
        int budgetIdx = indexOfFilter(filters, ctx.getBean(BUDGET_FILTER_BEAN_NAME, Filter.class));
        assertThat(budgetIdx)
                .as("3s 预算 Filter（HttpMiddlewareFilter）不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);

        for (Class<? extends Filter> type : DOMAIN_SLOT_REGISTRY.keySet()) {
            Filter bean = ctx.getBeanProvider(type).getIfAvailable();
            assertThat(bean)
                    .as("%s 没有注册进 Spring 上下文（bean 不存在 ⇒ 它根本没跑 ⇒ 本门禁的其余断言全部空转）",
                            type.getSimpleName())
                    .isNotNull();
            int idx = indexOfFilter(filters, bean);
            assertThat(idx)
                    .as("%s 没有被挂进容器装配出的 Filter 链（bean 在、链里没有 ⇒ 它根本没跑 ⇒ 断言全部空转）",
                            type.getSimpleName())
                    .isGreaterThanOrEqualTo(0);
            assertThat(idx)
                    .as("%s 必须排在 3s 预算起点（HttpMiddlewareFilter）<b>之前</b>，否则它落进 3s 预算内。"
                            + "\n⚠️ 权威槽位表：+7 = 预算起点 ⇒ 域侧必须 < +7。",
                            type.getSimpleName())
                    .isLessThan(budgetIdx);
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /**
     * 本模块自己的编译产物目录（<b>class 目录</b>形态）。
     *
     * <p>用 <b>code source</b> 取 ⇒ ⇒ <b>与工作目录无关</b>（IDE 跑也拿得到）。
     * <p>⚠️ 若拿不到 ⇒ ⇒ <b>直接失败</b>（不跳过）⇒ ⇒ 否则第 ① 条会退化成「什么都不查」的恒绿。
     * <p>⚠️ 枚举面 = {@code target/classes} ⇒ 若曾删过源文件而没 {@code mvn clean}，
     * <b>残留的旧 .class 会被枚举到</b> ⇒ 第 ① 条会红 ⇒ ⇒ 这是安全方向（宁可红不可漏）。
     */
    private static Path domainClassesRoot() {
        CodeSource cs = GoStyleJwtAuthFilter.class.getProtectionDomain().getCodeSource();
        assertThat(cs).as("域侧 Filter 类的 code source 必须可读（否则第 ① 条无法枚举产物 ⇒ 恒绿）").isNotNull();
        assertThat(cs.getLocation()).as("域侧 Filter 类的 code source 必须有 location").isNotNull();
        try {
            Path p = Path.of(cs.getLocation().toURI());
            assertThat(Files.isDirectory(p))
                    .as("域侧产物必须是 class 目录形态（不是 jar）⇒ 实际：" + p)
                    .isTrue();
            return p;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("无法把域侧 code source 转成目录：" + cs.getLocation(), e);
        }
    }

    /**
     * 枚举产物目录里<b>所有</b>「实现了 {@code jakarta.servlet.Filter} 且带 {@code @Order}」的类。
     *
     * <p>⚠️ <b>装载失败就跳过</b>是安全的：真要实现 {@code Filter} 的类，其超类与接口
     * 必然在本模块的测试 classpath 上 ⇒ ⇒ <b>装载得起来</b> ⇒ ⇒ 跳过的那批不可能是 Filter。
     */
    @SuppressWarnings("unchecked")
    private static Set<Class<? extends Filter>> enumerateOrderedFilters(Path classesRoot) {
        Set<Class<? extends Filter>> found = new LinkedHashSet<>();
        ClassLoader cl = GoStyleDomainFilterSlotGateTest.class.getClassLoader();

        List<Path> classFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".class")).forEach(classFiles::add);
        } catch (IOException e) {
            throw new IllegalStateException("枚举域侧产物失败：" + classesRoot, e);
        }
        assertThat(classFiles)
                .as("域侧产物目录里必须能枚举到 class 文件（枚举不到 ⇒ 第 ① 条空转）")
                .isNotEmpty();

        for (Path cf : classFiles) {
            String rel = classesRoot.relativize(cf).toString();
            String className = rel.substring(0, rel.length() - ".class".length())
                    .replace(File.separatorChar, '.');
            Class<?> c;
            try {
                c = Class.forName(className, false, cl);
            } catch (Throwable t) {
                continue; // 装载不了的类不可能是本模块的 Filter（见本方法 javadoc）
            }
            if (!Filter.class.isAssignableFrom(c)) {
                continue;
            }
            if (c.getAnnotation(Order.class) == null) {
                continue; // 不带 @Order ⇒ Spring 给它 LOWEST_PRECEDENCE（链尾）⇒ 不占槽位
            }
            found.add((Class<? extends Filter>) c);
        }
        return found;
    }

    /**
     * 取<b>容器实际装配出</b>的 Filter 顺序。
     *
     * <p>⚠️ 为什么不用「读两个 {@code @Order} 注解比大小」：
     * 注解只是 Spring 排序的<b>依据</b>，装配结果才是运行时事实。
     * {@code ServletContextInitializerBeans} 就是 Boot 在启动时用来装配 Filter 的那个类
     * ⇒ 它的排序输出 = 容器里的真实顺序。
     */
    private List<ServletContextInitializer> assembledFilters() {
        return new ServletContextInitializerBeans(ctx).stream()
                .filter(i -> i instanceof FilterRegistrationBean<?>)
                .toList();
    }

    private static int indexOfFilter(List<ServletContextInitializer> filters, Filter target) {
        for (int i = 0; i < filters.size(); i++) {
            ServletContextInitializer init = filters.get(i);
            if (init instanceof FilterRegistrationBean<?> frb && frb.getFilter() == target) {
                return i;
            }
        }
        return -1;
    }

    private static String describe(Set<Class<? extends Filter>> set) {
        List<String> names = new ArrayList<>();
        for (Class<?> c : set) {
            names.add(c.getSimpleName());
        }
        Collections.sort(names);
        return names.toString();
    }
}
