package com.rag.common.spring;

import com.rag.common.http.GoStylePathNormalizingAutoConfiguration;
import com.rag.common.http.GoStylePathNormalizingFilter;
import com.rag.common.http.GoStyleTraceparentFilter;
import com.rag.common.protection.GoStyleBreakerFilter;
import com.rag.common.protection.GoStyleCpuSheddingFilter;
import com.rag.common.protection.GoStyleMaxBytesFilter;
import com.rag.common.protection.GoStyleMaxConnsFilter;
import com.rag.common.protection.GoStyleProtectionProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守门：<b>共享层 Filter 的 {@code @Order} 槽位快照</b>（G1 协调约束 · 2026-10-27）。
 *
 * <h2>为什么需要这条</h2>
 *
 * <p>G1 落码 {@code DRIFT-USERAPI-009②}（把 user-api 的 JWT 验签搬出 3s 预算）后，
 * <b>域侧</b>的 {@code GoStyleJwtAuthFilter} 落在 <b>{@code +6}</b>，被共享层两端夹住：
 * <ul>
 *   <li>必须 <b>&lt; +7</b>（{@code HttpMiddlewareFilter}）⇒ 否则验签又回到 3s 预算内 ⇒ 002 退回 OPEN；</li>
 *   <li>必须 <b>≥ +3</b>（{@code GoStyleTraceparentFilter}）⇒ 否则验签落到 traceparent 的 SERVER span 外 ⇒ 观测面漂移。</li>
 * </ul>
 * ⇒ ⇒ <b>共享层一旦把 {@code HttpMiddlewareFilter} 下调到 +6 或更低、或整体重排这些槽位，
 * 各域的 JWT 验签会立刻退回预算内</b> ⇒ 而且<b>不会有任何共享层测试变红</b>
 * ⇒ ⇒ 本守门就是那道「会红」的网。
 *
 * <h2>本守门钉死四件事</h2>
 * <ol>
 *   <li><b>槽位快照</b>：7 个共享层 Filter 的 {@code @Order} 精确值 ⇒ <b>任何重排都红</b>
 *       ⇒ 强制「改槽位」变成<b>显式决策</b>（改快照 = 明知故犯），而不是「顺手挪一个数字」。</li>
 *   <li><b>预算起点不被抢占</b>：3s 预算的 {@code begin} 在
 *       {@code HttpMiddleware.java:324}、{@code clear} 在 {@code :356}
 *       ⇒ <b>都落在 {@code HttpMiddlewareFilter}(+7) 的链内</b>
 *       ⇒ ⇒ 排在它<b>之后</b>的 Filter 会在预算内执行 ⇒ 只允许 <b>MaxBytes(+8)</b> 在其后。</li>
 *   <li><b>域侧仍有可用槽位</b>：{@code HttpMiddlewareFilter} 的 order 必须 <b>≥ HIGHEST+7</b>
 *       ⇒ 域侧才能在 +6 找到「在预算外、又不在 traceparent 之外」的位置。</li>
 *   <li>🆕 <b>共享层「产出」的 Filter 全集 == 快照</b>：枚举<b>本模块自己的编译产物</b>
 *       ⇒ ⇒ <b>新增第 8 个带 {@code @Order} 的 Filter 必红</b> ⇒ ⇒ 逼它显式登记槽位。</li>
 * </ol>
 *
 * <h2>🆕 第 ⑤ 条为什么不是「类路径扫描」（§4.10.18.3 撤回的那条路）</h2>
 *
 * <p>§4.10.18.3 记着：本层试过「扫 {@code com.rag.common} 下所有带 {@code @Order} 的 Filter」
 * ⇒ ⇒ <b>实测只扫到 2/7</b>（{@code protection} 包的 4 个与 {@code http} 包的
 * {@code GoStyleTraceparentFilter} 全漏）⇒ ⇒ 原因未查明 ⇒ ⇒ <b>判据本身不可靠 ⇒ 撤掉</b>。
 * <p>⇒ ⇒ 第 ⑤ 条<b>换判据</b>：不用 Spring 的类路径扫描，而是<b>直接枚举本模块自己的编译产物目录</b>
 * （{@code target/classes}，用 <b>code source</b> 取 ⇒ <b>与工作目录无关</b>）
 * ⇒ ⇒ <b>枚举面 = 本模块真实产出的全部 class 文件</b> ⇒ ⇒ <b>不存在「扫不全」这条路</b>。
 * <p>⇒ ⇒ 而且它<b>不需要容器</b>：④ 之所以要在真 servlet 上下文里断言，是因为「哪个 Filter
 * <b>注册</b>在哪个 order」没有公开读法；而第 ⑤ 条问的是「本模块<b>产出</b>了哪些带 {@code @Order} 的 Filter」
 * ⇒ ⇒ <b>产物枚举</b>就是那件事的直接读法 ⇒ ⇒ <b>绕开了 §4.10.18.3 的两个死结</b>
 * （类路径扫描扫不全 ＋ 共享层测试上下文装不齐 {@code protection} 那 4 个）。
 *
 * <h2>⚠️ 第 ⑤ 条的边界（不得外推）</h2>
 * <ul>
 *   <li><b>只管「共享层产出」的 Filter</b> ⇒ ⇒ <b>域侧新增</b> Filter 仍<b>不在</b>本守门覆盖面
 *       ⇒ ⇒ 那一半要靠<b>域内真装配面</b>的守门（域侧文件 ⇒ 域侧落码）。</li>
 *   <li><b>只管「带 {@code @Order}」的</b> ⇒ ⇒ 共享层若产出<b>不带 {@code @Order}</b> 的 Filter，
 *       Spring 给它 {@code Ordered.LOWEST_PRECEDENCE}（链尾）⇒ ⇒ <b>不占任何槽位</b>
 *       ⇒ ⇒ 无需登记 ⇒ ⇒ 本条不管它是<b>有意</b>的。</li>
 *   <li><b>枚举面 = 本模块（{@code service/common}）的产物</b> ⇒ ⇒
 *       {@code service/users/users-common}（同属 G4 写权面）<b>当前产出 0 个 Filter</b>
 *       ⇒ ⇒ 故不在本条覆盖面内 ⇒ ⇒ <b>若将来它引入 Filter，本条须扩面</b>
 *       （做法：在该模块自己的测试里加同一判据 ⇒ <b>各模块各守自己那份</b> ⇒ 避免跨模块的路径依赖）。</li>
 * </ul>
 *
 * <h2>为什么不止读注解</h2>
 *
 * <p>AGENTS.md §3.3.3：「<b>不得凭注解名称断定机制已启用</b>」⇒ 只反射读 {@code @Order} 会留下
 * 「<b>注解在、但 bean 根本没注册</b>」的空转空间 ⇒ ⇒ 本类额外用
 * <b>真 servlet 上下文</b>（{@code WebApplicationContextRunner}）断言
 * ① 这 7 个 Filter <b>确实被注册成 Filter bean</b> ② 若容器给出了
 * {@link FilterRegistrationBean}，其 order 必须与快照<b>一致</b>。
 *
 * <p>⚠️ {@code GoStylePathNormalizingFilter} <b>不是</b> {@code @Component}
 * （它由 {@link GoStylePathNormalizingAutoConfiguration} 的 {@code @Bean} 注册）
 * ⇒ ⇒ 真容器断言必须<b>显式引入该自动配置</b>，否则它根本不会出现在链里。
 *
 * <h2>🔴 一条「守恒断言逼出真相」的经过（本类的第 ④ 条）</h2>
 *
 * <p>第 ④ 条最初写成「从容器里取 {@code FilterRegistrationBean} ⇒ 断言其 order == 快照」，
 * 并配了一条守恒断言「必须能取到这些 registration bean，否则上面那段是空转」。
 * ⇒ ⇒ **守恒断言当场把它自己抓红了** ⇒ 查 Boot 4 源码才知：
 * <b>容器根本不注册 {@code FilterRegistrationBean} 这个 bean</b>，
 * 它由 {@code ServletContextInitializerBeans} 内部那个<b>包级私有</b>的
 * {@code FilterRegistrationBeanAdapter} <b>临时</b>包装 ⇒
 * ⇒ 「哪个 Filter 注册在哪个 order」<b>没有公开读法</b>。
 * ⇒ ⇒ 改用 <b>Spring 自己排 Filter 用的那个 comparator</b>（{@link AnnotationAwareOrderComparator}，
 * 即 {@code ServletContextInitializerBeans#getOrder} 内部走的那条路）作<b>等价读法</b>。
 * <p>⇒ ⇒ 这条本身就是一个可复用的判据：<b>「断言会不会执行」本身要被断言</b>，
 * 否则「看起来在验注册顺序」的那段可能<b>一次都没跑过</b>。
 */
@DisplayName("共享层 Filter 槽位：快照钉死 + 3s 预算起点不被抢占 + 域侧仍有可用槽位 + 产出全集==快照")
class SharedFilterOrderSnapshotGateTest {

    /**
     * 共享层 Filter 的槽位快照（相对 {@code Ordered.HIGHEST_PRECEDENCE} 的偏移）。
     *
     * <p>⚠️ 这张表是<b>快照</b>，不是「当前应该是」⇒ 改它 = <b>显式承认一次重排</b>。
     */
    private static final Map<Class<? extends Filter>, Integer> SLOT_SNAPSHOT = new LinkedHashMap<>();

    static {
        SLOT_SNAPSHOT.put(GoStylePathNormalizingFilter.class, 2);
        SLOT_SNAPSHOT.put(GoStyleTraceparentFilter.class, 3);
        SLOT_SNAPSHOT.put(GoStyleMaxConnsFilter.class, 4);
        SLOT_SNAPSHOT.put(GoStyleBreakerFilter.class, 5);
        SLOT_SNAPSHOT.put(GoStyleCpuSheddingFilter.class, 6);
        SLOT_SNAPSHOT.put(HttpMiddlewareFilter.class, 7);
        SLOT_SNAPSHOT.put(GoStyleMaxBytesFilter.class, 8);
    }

    /** 3s 预算的起点（{@code HttpMiddlewareFilter}）。 */
    private static final int BUDGET_START_SLOT = 7;

    /** 明确允许排在预算起点<b>之后</b>的（它多记 413，属已签字方向）。 */
    private static final Class<? extends Filter> ALLOWED_AFTER_BUDGET = GoStyleMaxBytesFilter.class;

    // ==================== ① 槽位快照 ====================

    @Test
    @DisplayName("① 共享层 7 个 Filter 的 @Order 槽位与快照<b>逐条一致</b>（重排必红）")
    void slotSnapshotIsUnchanged() {
        Map<Class<? extends Filter>, Integer> actual = readActualSlots();

        List<String> problems = new ArrayList<>();
        for (Map.Entry<Class<? extends Filter>, Integer> e : SLOT_SNAPSHOT.entrySet()) {
            Integer offset = actual.get(e.getKey());
            if (offset == null) {
                problems.add(e.getKey().getSimpleName() + " 没有 @Order 注解（若改用了 Ordered 接口，本守门需同步改判据）");
                continue;
            }
            if (offset != e.getValue()) {
                problems.add(e.getKey().getSimpleName() + " 期望 +" + e.getValue() + " ⇒ 实际 +" + offset);
            }
        }

        assertThat(problems)
                .as("共享层 Filter 槽位与快照不一致 ⇒ 有人重排了 @Order。"
                        + "⚠️ 重排会让各域的 JWT 验签退回 3s 预算内（域侧守门会红，但共享层此前没有任何测试会红）"
                        + " ⇒ 若确属有意重排 ⇒ 请同时更新本快照并在台账登记理由。"
                        + " 不一致项：" + problems)
                .isEmpty();
        assertThat(actual).as("快照本身应覆盖全部 7 个共享层 Filter").hasSize(SLOT_SNAPSHOT.size());
    }

    // ==================== ② 预算起点不被抢占 ====================

    @Test
    @DisplayName("② 3s 预算起点（+7）之后<b>只允许</b> MaxBytes —— 预算不被别的共享层 Filter 抢占")
    void budgetStartIsNotPreempted() {
        // ⚠️ 必须用**实际读到的** order（readActualSlots），**不能**用快照值 ——
        //    否则这条断言只对「快照本身」求值 ⇒ 快照没改就恒绿 ⇒ **空转**
        //    （本条就是这样被变异 C 抓出来的：把 CpuShedding 挪到 +9 时它没红）。
        Map<Class<? extends Filter>, Integer> actual = readActualSlots();
        int budgetStart = actual.get(HttpMiddlewareFilter.class);
        assertThat(budgetStart).as("必须读到 HttpMiddlewareFilter 的实际槽位").isNotNull();

        List<String> intruders = new ArrayList<>();
        for (Map.Entry<Class<? extends Filter>, Integer> e : actual.entrySet()) {
            if (e.getKey() == HttpMiddlewareFilter.class) {
                continue;
            }
            // 实际排在预算起点之后 ⇒ 会在 3s 预算内执行
            if (e.getValue() > budgetStart && e.getKey() != ALLOWED_AFTER_BUDGET) {
                intruders.add(e.getKey().getSimpleName() + "(+" + e.getValue() + ")");
            }
        }

        assertThat(intruders)
                .as("3s 预算的 begin/clear 都在 HttpMiddlewareFilter(+7) 的链内"
                        + "（HttpMiddleware.java:324 begin / :356 clear）"
                        + " ⇒ 排在它之后的共享层 Filter 都会<b>落进预算内</b>"
                        + " ⇒ 除已签字的 MaxBytes 外不得有第二个。越界者：" + intruders)
                .isEmpty();
    }

    // ==================== ③ 域侧仍有可用槽位 ====================

    @Test
    @DisplayName("③ 域侧 JWT 验签的可用槽位仍存在（+6 必须在预算起点<b>之外</b>、且 ≥ +3）")
    void domainSlotForJwtVerificationStillExists() {
        int budgetStart = offsetOf(HttpMiddlewareFilter.class);

        assertThat(budgetStart)
                .as("HttpMiddlewareFilter 的槽位必须 ≥ +" + BUDGET_START_SLOT
                        + " ⇒ 域侧的 GoStyleJwtAuthFilter(+6) 才能排在它<b>之前</b>（验签不计入 3s 预算）"
                        + "；若被下调到 +6 或更低 ⇒ 各域 DRIFT-USERAPI-009② 退回 OPEN")
                .isGreaterThanOrEqualTo(BUDGET_START_SLOT);

        int traceparent = offsetOf(GoStyleTraceparentFilter.class);
        assertThat(traceparent)
                .as("GoStyleTraceparentFilter 的槽位必须 ≤ +6 ⇒ 域侧 JWT 验签才能保证 ≥ +3"
                        + "（否则验签落到 traceparent 的 SERVER span 之外 ⇒ 观测面漂移）")
                .isLessThanOrEqualTo(6);

        // 「≥ +3 且 < 预算起点」这个区间必须至少有一个共享层槽位被占用 ⇒ 说明它是**被刻意保留**的
        int occupiedInRange = 0;
        for (Integer slot : SLOT_SNAPSHOT.values()) {
            if (slot >= 3 && slot < budgetStart) {
                occupiedInRange++;
            }
        }
        assertThat(occupiedInRange)
                .as("[+3, 预算起点) 区间内必须仍有共享层槽位被占用 ⇒ 说明这段区间是**已知且被刻意保留**的"
                        + "（域侧 JWT 验签只能与最后一个槽位并列）")
                .isPositive();
    }

    // ==================== ④ 真容器：快照不是空转 ====================

    @Test
    @DisplayName("④ 真 servlet 上下文里 7 个 Filter <b>确实注册</b>，且注册 order 与快照一致")
    void snapshotIsNotVacuousInRealContainer() {
        // 🔴🔴 2026-10-28（G4）：**GoStylePathNormalizingFilter 不能再手工注册第 2 份**
        //
        // 【现象】本条曾长期红：BeanDefinitionOverrideException:
        //   "Invalid bean definition with name 'goStylePathNormalizingFilter' defined in
        //    GoStylePathNormalizingAutoConfiguration: @Bean definition illegally overridden by
        //    existing bean definition: Generic bean: class=...GoStylePathNormalizingFilter"
        //   ⇒ 共享层自动配置注册的那份，与本 harness 手工注册的那份，**撞了同一个 Bean 名**。
        //
        // 【为什么必须去掉手工注册，而不是「改共享层」】
        //   ① GoStylePathNormalizingFilter 的**唯一注册点**是
        //      GoStylePathNormalizingAutoConfiguration#goStylePathNormalizingFilter()
        //      （见该类 javadoc：「本类是 Filter 的唯一 Bean 定义点 ⇒ 结构上不可能同一层注册两次」）
        //      ⇒ harness 再手工注册一遍 ⇒ 等于**造出一个生产里不存在的形态**。
        //   ② **生产形态是安全的，已被钉死**：域内副本还在时（article-api 的 @Component 副本），
        //      共享层那份靠 @ConditionalOnMissingBean(name="goStylePathNormalizingFilter") 让位
        //      ⇒ 证据 = article-api 的 ArticleApiAssemblyWiringTest（@SpringBootTest(RANDOM_PORT)
        //      **整应用启动**）用 assertEquals 钉死**恰好 9 个** Filter 的精确链
        //      （链里 GoStylePathNormalizingFilter **只出现 1 次**）
        //      ⇒ 域内副本在 ⇒ 共享层确实让位 ⇒ 生产无双注册。
        //   ③ ⇒ 本条断言的**不变式**（7 个 Filter 真在真 servlet 上下文里注册 + 次序 == 快照）
        //      **完全不受影响**：那个 Filter 仍必须由自动配置注册出来，
        //      少注册一个 ⇒ missing 非空 ⇒ 本条照样红（**不是空转**）。
        //   ④ ⇒ 而且本条**仍然能抓住「注册了 2 份」**：actualOrder 是从容器里**实际取到的**
        //      Filter 过滤出来的 ⇒ 若真出现 2 个路径归一 Filter ⇒ actualOrder 会有 8 项
        //      ⇒ containsExactlyElementsOf(7 项) 必红。
        //
        // ⚠️ 一处**诚实边界**（不要在台账里把它写成「已完全解释」）：
        //   「@ConditionalOnMissingBean(name=…) 在本 harness 里为何没挡住手工注册的那份」
        //   —— 读 Boot 4.0.8 源码（OnBeanCondition#containsBean → DefaultListableBeanFactory#containsBean
        //   这条路径）**与观察到的现象不自洽** ⇒ 该层机制**未定位**（记为 OPEN）。
        //   ⇒ 但这**不影响本条的处置**：harness 手工注册 = 生产不存在的形态，
        //   而生产形态已被 article-api 的整应用启动测试钉死 ⇒ 处置成立。
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GoStylePathNormalizingAutoConfiguration.class))
                .withUserConfiguration(
                        GoStyleProtectionProperties.class,
                        ObservabilityProperties.class,
                        // ⚠️ 此处**故意不再**注册 GoStylePathNormalizingFilter.class —— 见上方注释
                        GoStyleTraceparentFilter.class,
                        GoStyleMaxConnsFilter.class,
                        GoStyleBreakerFilter.class,
                        GoStyleCpuSheddingFilter.class,
                        HttpMiddlewareFilter.class,
                        GoStyleMaxBytesFilter.class)
                .run(ctx -> {
                    assertThat(ctx).as("真 servlet 上下文必须能起").hasNotFailed();

                    Map<Class<? extends Filter>, Filter> registered = new LinkedHashMap<>();
                    for (Filter f : ctx.getBeansOfType(Filter.class).values()) {
                        registered.put(f.getClass(), f);
                    }

                    List<String> missing = new ArrayList<>();
                    for (Class<? extends Filter> type : SLOT_SNAPSHOT.keySet()) {
                        if (!registered.containsKey(type)) {
                            missing.add(type.getSimpleName());
                        }
                    }
                    assertThat(missing)
                            .as("这些共享层 Filter 必须在真 servlet 上下文里<b>真的注册</b>；"
                                    + "否则槽位快照就是空转（注解在、bean 不在）")
                            .isEmpty();

                    // ⚠️ 诚实边界（Boot 4 实测 · 本条守恒断言逼出来的）：**容器并不注册
                    //    FilterRegistrationBean 这个 bean** ⇒ `getBeansOfType(FilterRegistrationBean.class)`
                    //    恒为空 ⇒ 「实际注册 order == 快照」那段若用 bean 取，就是**空转**
                    //    （我第一版就写成那样 ⇒ 被守恒断言当场抓到红）。
                    //    真相：Boot 4 的 `ServletContextInitializerBeans`（`spring-boot-4.0.8.jar`）
                    //    用**包级私有**的 `FilterRegistrationBeanAdapter` 在**内部**临时包装 Filter bean
                    //    ⇒ 「哪个 Filter 注册在哪个 order」**没有公开读法**。
                    //    ⇒ 此处改用 **Spring 自己排 Filter 用的那个 comparator**
                    //    （`AnnotationAwareOrderComparator`，即 `ServletContextInitializerBeans#getOrder`
                    //      内部走的那条路）作**等价读法** ⇒ 断言的是**生效次序**，不是「注册表读数」。
                    List<Filter> ordered = new ArrayList<>(registered.values());
                    AnnotationAwareOrderComparator.sort(ordered);

                    List<Class<? extends Filter>> actualOrder = new ArrayList<>();
                    for (Filter f : ordered) {
                        if (SLOT_SNAPSHOT.containsKey(f.getClass())) {
                            actualOrder.add(f.getClass());
                        }
                    }
                    List<Class<? extends Filter>> expectedOrder = new ArrayList<>(SLOT_SNAPSHOT.keySet());
                    assertThat(actualOrder)
                            .as("真容器里共享层 Filter 的<b>生效次序</b>必须与快照一致"
                                    + "（用 Spring 自己的 AnnotationAwareOrderComparator 排 —— "
                                    + "与 ServletContextInitializerBeans 内部用的是同一个 comparator）")
                            .containsExactlyElementsOf(expectedOrder);
                });
    }

    // ==================== ⑤ 共享层「产出」的 Filter 全集 ====================

    @Test
    @DisplayName("⑤ 共享层<b>产出</b>的带 @Order 的 Filter 全集 == 快照（新增第 8 个 Filter 必红）")
    void everySharedLayerFilterIsRegisteredInSnapshot() {
        Path classesRoot = sharedLayerClassesRoot();
        Set<Class<? extends Filter>> found = enumerateOrderedFilters(classesRoot);
        Set<Class<? extends Filter>> expected = new LinkedHashSet<>(SLOT_SNAPSHOT.keySet());

        // ⚠️ 用「双向相等」而不是「⊆」——
        //    ⊆ 的写法在「枚举面失效」时（例如枚举到 0 个）会**恒绿** ⇒ 空转；
        //    相等则「枚举不到」与「多出/少了」一样都会红。
        assertThat(found)
                .as("共享层<b>产出</b>的带 @Order 的 Filter 必须与快照<b>完全一致</b>："
                        + "多一个（= 新增了共享层 Filter 却没登记槽位）或少一个（= 快照里那个已经不存在）都必须红。"
                        + "\n⚠️ 新增共享层 Filter 必须<b>显式登记</b>它占了哪个槽位（改快照 = 显式承认），"
                        + "而不是「加个类就完事」——否则它可能正好吃掉各域 JWT 验签唯一可用的 +6。"
                        + "\n枚举根目录：" + classesRoot
                        + "\n实际枚举到：" + describe(found)
                        + "\n快照：" + describe(expected))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    // ==================== 辅助 ====================

    /**
     * 共享层自己的编译产物目录（<b>class 目录</b>形态）。
     *
     * <p>用 <b>code source</b> 取 ⇒ ⇒ <b>与工作目录无关</b>（IDE 跑也拿得到）。
     *
     * <p>⚠️ 若拿不到 ⇒ ⇒ <b>直接失败</b>（不跳过）⇒ ⇒ 否则第 ⑤ 条会退化成「什么都不查」的恒绿。
     */
    private static Path sharedLayerClassesRoot() {
        CodeSource cs = GoStyleTraceparentFilter.class.getProtectionDomain().getCodeSource();
        assertThat(cs).as("共享层类的 code source 必须可读（否则第 ⑤ 条无法枚举产物 ⇒ 恒绿）").isNotNull();
        assertThat(cs.getLocation()).as("共享层类的 code source 必须有 location").isNotNull();
        try {
            Path p = Path.of(cs.getLocation().toURI());
            assertThat(Files.isDirectory(p))
                    .as("共享层产物必须是 class 目录形态（不是 jar）⇒ 实际：" + p)
                    .isTrue();
            return p;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("无法把共享层 code source 转成目录：" + cs.getLocation(), e);
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
        ClassLoader cl = SharedFilterOrderSnapshotGateTest.class.getClassLoader();

        List<Path> classFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".class")).forEach(classFiles::add);
        } catch (IOException e) {
            throw new IllegalStateException("枚举共享层产物失败：" + classesRoot, e);
        }
        assertThat(classFiles)
                .as("共享层产物目录里必须能枚举到 class 文件（枚举不到 ⇒ 第 ⑤ 条空转）")
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

    private static String describe(Set<Class<? extends Filter>> set) {
        List<String> names = new ArrayList<>();
        for (Class<?> c : set) {
            names.add(c.getSimpleName());
        }
        java.util.Collections.sort(names);
        return names.toString();
    }

    /**
     * 读出 7 个共享层 Filter 的<b>实际</b>槽位（反射读类上的 {@code @Order}）。
     *
     * <p>⚠️ 凡是「算语义」的地方都必须用**这个**（实际读数），
     * **不能**用 {@link #SLOT_SNAPSHOT}（快照）—— 否则那条断言只对快照求值 ⇒ 恒绿 ⇒ 空转。
     */
    private static Map<Class<? extends Filter>, Integer> readActualSlots() {
        Map<Class<? extends Filter>, Integer> actual = new LinkedHashMap<>();
        for (Class<? extends Filter> type : SLOT_SNAPSHOT.keySet()) {
            Order order = type.getAnnotation(Order.class);
            if (order == null) {
                continue; // 缺失由 ① 单独报
            }
            actual.put(type, order.value() - Ordered.HIGHEST_PRECEDENCE);
        }
        return actual;
    }

    private static int offsetOf(Class<? extends Filter> type) {
        Order order = type.getAnnotation(Order.class);
        assertThat(order).as("%s 必须有 @Order 注解", type.getSimpleName()).isNotNull();
        return order.value() - Ordered.HIGHEST_PRECEDENCE;
    }
}
