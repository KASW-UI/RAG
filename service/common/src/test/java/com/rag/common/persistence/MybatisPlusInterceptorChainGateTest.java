package com.rag.common.persistence;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.common.persistence.probe.MpProbeItem;
import com.rag.common.persistence.probe.MpProbeItemMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>🔴 守门：{@code @Import} 的 MyBatis 拦截器<b>真的进了 MyBatis-Plus 的执行链</b>。</b>
 *
 * <h2>这条守门钉住的是什么（问题来源）</h2>
 * <p><b>G3 派单（2026-10-27 · 反馈单 7 项齐全）</b>：
 * {@link DeadlineQueryTimeoutInjector} 在 10 个 rpc 组件里都是靠
 * {@code @Import(...)} 引入的，而这条链生效的前提是
 * 「<b>MyBatis-Plus 自动配置会收集容器内的 {@code Interceptor[]} Bean</b>」
 * ⇒ 但<b>全仓没有任何一条测试证明过这个前提</b>：
 * 域内两条看似相关的测试<b>都绕开</b>了 Spring 与 MyBatis-Plus 自动配置
 * （手工 {@code new Configuration()} + 手工 {@code addInterceptor(...)}）
 * ⇒ ⇒ 「注解存在 ≠ 机制生效」（AGENTS.md §3.3.3）的形态。
 *
 * <h2>Go 出处（真源仓库，逐行核对过）</h2>
 * <pre>
 * // service/common/observability/rpc.go:38
 * timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
 * </pre>
 * ⇒ Go 侧 ctx deadline ⇒ GORM 的 {@code QueryContext}/{@code ExecContext} ⇒ PG 取消 SQL，
 * 是<b>框架级保证</b>；RAG 侧靠 {@code Statement.setQueryTimeout} 达到同形语义
 * ⇒ ⇒ <b>这条机制一旦没生效，RAG 侧的 SQL 取消能力就是假的</b>（而两侧都不报错）。
 *
 * <h2>本守门分三层，逐层加码</h2>
 * <ol>
 *   <li><b>「收集」层</b>：{@code MybatisPlusAutoConfiguration} 的
 *       {@code interceptors} 字段里<b>确实有</b>本拦截器
 *       ⇒ 证明 MP 真的从容器里收集了 {@code Interceptor[]} Bean
 *       （实测前提：{@code javap} 3.5.17 的
 *       {@code MybatisPlusAutoConfiguration} 构造器收
 *       {@code ObjectProvider<org.apache.ibatis.plugin.Interceptor[]>}）。</li>
 *   <li><b>「装载」层</b>：{@code SqlSessionFactory.getConfiguration().getInterceptors()}
 *       里<b>确实有</b>本拦截器 ⇒ 证明它被 {@code setPlugins(...)} 装进了 MyBatis。</li>
 *   <li><b>「执行」层（行为级 · 真 PG）</b>：绑一个<b>已过期</b>的 deadline 后跑一条
 *       <b>真 mapper + 真 PG</b> 的查询 ⇒ 必须以 {@code 57014} / {@code DEADLINE_EXCEEDED} 失败
 *       ⇒ <b>只有当拦截器真的在执行链上时才可能发生</b>
 *       ⇒ ⇒ 这是「真进了 MyBatis-Plus 链」的<b>行为证据</b>，不是结构推断。</li>
 * </ol>
 *
 * <h2>为什么第 ③ 层能当行为证据</h2>
 * <p>{@link DeadlineQueryTimeoutInjector} 拦的是 {@code StatementHandler.prepare}
 * ⇒ 它在 {@code prepare} 之后、<b>语句执行之前</b>抛 {@code SQLTimeoutException(SQLState=57014)}
 * ⇒ ⇒ 那条 SQL <b>根本没发到 PG</b>（{@code prepare} ≠ {@code execute}）
 * ⇒ ⇒ 「无 deadline ⇒ 查得到」与「已过期 deadline ⇒ 57014」这一对差异
 * <b>只可能</b>由链上的本拦截器造成 ⇒ ⇒ 干净的因果证据。
 *
 * <h2>为什么用真 PostgreSQL 而不是 H2</h2>
 * <p>拦截器打的是 <b>JDBC 的 {@code Statement.setQueryTimeout}</b> 与 <b>SQLState 57014</b>
 * ⇒ ⇒ H2 没有这套语义 ⇒ 换 H2 等于把要验证的东西换没了（同各域 PG 守门的理由）。
 */
@SpringBootTest(
        classes = MybatisPlusProbeApplication.class,
        properties = {
                "management.otlp.tracing.export.enabled=false",
                "spring.datasource.hikari.maximum-pool-size=4"
        })
@Testcontainers
class MybatisPlusInterceptorChainGateTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mp_probe")
            .withUsername("probe")
            .withPassword("probe");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MpProbeItemMapper mapper;

    @BeforeAll
    static void createTable() throws SQLException {
        try (Connection c = PG.createConnection("");
             java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS mp_probe_item ("
                    + "id BIGSERIAL PRIMARY KEY, name VARCHAR(64) NOT NULL)");
        }
    }

    @Test
    @DisplayName("① MyBatis-Plus 的自动配置**确实收集**了容器里的 Interceptor[] Bean")
    void mybatisPlusCollectsInterceptorBeans() throws Exception {
        MybatisPlusAutoConfiguration mp = ctx.getBean(MybatisPlusAutoConfiguration.class);

        Field field = MybatisPlusAutoConfiguration.class.getDeclaredField("interceptors");
        field.setAccessible(true);
        Interceptor[] collected = (Interceptor[]) field.get(mp);

        assertThat(collected)
                .as("MP 的自动配置必须从容器里收集到 Interceptor[]；"
                        + "收集不到 ⇒ @Import 的 DeadlineQueryTimeoutInjector 形同虚设 ⇒ "
                        + "RAG 侧的 SQL 取消能力是假的（而两侧都不报错）")
                .anyMatch(i -> i instanceof DeadlineQueryTimeoutInjector);
    }

    @Test
    @DisplayName("② 收集到的拦截器**真的被装进** SqlSessionFactory 的 MyBatis 配置")
    void interceptorIsInstalledIntoMybatisConfiguration() {
        assertThat(sqlSessionFactory.getConfiguration().getInterceptors())
                .as("拦截器必须在 MyBatis 的插件列表里；"
                        + "不在 ⇒ 它不会被 Executor 包装 ⇒ 拦截逻辑一次都不执行")
                .anyMatch(i -> i instanceof DeadlineQueryTimeoutInjector);
    }

    @Test
    @DisplayName("③ 行为级：绑一个已过期 deadline ⇒ 真 mapper + 真 PG 的查询以 57014/DEADLINE_EXCEEDED 失败")
    void expiredDeadlineMakesRealQueryFailWith57014() {
        // 前置：无 deadline ⇒ 同一条查询**成功**（证明表/Mapper/SQL 通道本身是好的）
        MpProbeItem item = new MpProbeItem();
        item.setName("probe-" + System.nanoTime());
        mapper.insert(item);
        assertThat(mapper.selectById(item.getId()))
                .as("前置：无 deadline 时必须查得到 ⇒ 下面的失败才可归因于拦截器")
                .isNotNull();

        // 正题：已过期 deadline ⇒ 拦截器在 prepare 之后、执行之前抛 57014
        long alreadyExpired = System.currentTimeMillis() - 1_000L;
        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(alreadyExpired)) {
            assertThatThrownBy(() -> mapper.selectById(item.getId()))
                    .as("绑了已过期 deadline 后，这条查询必须以 57014 / DEADLINE_EXCEEDED 失败；"
                            + "若它成功了 ⇒ 拦截器**不在**执行链上 ⇒ MP 并没有真的收集它")
                    .satisfies(ex -> assertThat(isCanceled(ex))
                            .as("异常链里必须能找到 SQLState=57014 的 SQLException，"
                                    + "或 DEADLINE_EXCEEDED 的 gRPC 状态异常；实际=" + ex)
                            .isTrue());
        }
    }

    /**
     * 异常链里找「SQL 被 deadline 取消」的两种可能形态：
     * <ul>
     *   <li>{@link SqlTimeoutStatusTranslator} 在链上 ⇒ 被翻成 gRPC {@code DEADLINE_EXCEEDED}；</li>
     *   <li>翻译器不在链上 ⇒ 原始的 {@code SQLTimeoutException(SQLState=57014)}。</li>
     * </ul>
     * ⇒ 两者都算「拦截器确实执行了」。
     */
    private static boolean isCanceled(Throwable ex) {
        for (Throwable c = ex; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql && "57014".equals(sql.getSQLState())) {
                return true;
            }
            if (c instanceof StatusRuntimeException sre
                    && sre.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }
}
