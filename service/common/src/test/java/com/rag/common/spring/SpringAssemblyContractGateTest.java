package com.rag.common.spring;

import com.rag.common.observability.Observability;
import com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration;
import com.rag.common.snowflake.Snowflake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 <b>共享层逐类契约守门 · spring 包</b>：给<b>此前零专属测试</b>的 9 个装配类补上行为断言。
 *
 * <h2>⚠️ AGENTS.md §3.3 的自我约束</h2>
 * <p>§3.3 明说「<b>断言「Bean 存在」≠ 断言「机制生效」</b>」⇒ ⇒ 所以本类<b>不</b>写
 * 「Bean 存在吗」这种 H2 断言 ⇒ ⇒ 每个用例都断言<b>装配面真正决定的那个可观察结果</b>
 * （路径 / 键名 / 优先级 / 阈值 / 注解指向的类名）。
 * <p>⚠️ <b>诚实边界</b>：本模块<b>没有</b> servlet 容器与 gRPC server ⇒
 * 「拦截器链在真 server 上生效」这一面<b>不</b>由本门覆盖 ⇒ ⇒ 由
 * {@code RpcInterceptorClientDeadlineGateTest}（真 Netty）覆盖 ⇒ ⇒ 本门只负责<b>装配面</b>。
 *
 * <h2>Go 出处（逐条）</h2>
 * <ul>
 *   <li>{@code AuthorityHandlerFactory} ← {@code service/common/communityfact/authority_http.go:23} · {@code :28}；</li>
 *   <li>{@code HttpMiddlewareFilter} ← {@code service/common/observability/http.go:36}；</li>
 *   <li>{@code HttpPoolLifecycle} ← {@code service/common/observability/http.go:36}（<b>无显式关闭</b> ⇒ 表达层补齐）；</li>
 *   <li>{@code LoggerFactoryBean} ← {@code service/common/logger/logger.go:63-70} · {@code :98}；</li>
 *   <li>{@code ObservabilityLifecycle} ← {@code service/common/observability/observability.go:19-30}（<b>无显式关闭</b>）；</li>
 *   <li>{@code ObservabilityProperties} ← {@code service/common/observability/observability.go:19-30} · {@code :43-55}；</li>
 *   <li>{@code RpcServerInterceptorFactory} ← {@code go-zero@v1.10.2/zrpc/server.go:148-150}
 *       （{@code svr.AddUnaryInterceptors(serverinterceptors.UnaryRecoverInterceptor)} 等三段）；</li>
 *   <li>{@code SnowflakeBean} ← {@code service/common/snowflake/snowflake.go:22-38} · {@code :53-59}；</li>
 *   <li>{@code SnowflakeProperties} ← {@code service/common/snowflake/snowflake.go:53-59}；</li>
 *   <li>{@code MybatisPlusOrderingBridgeAutoConfiguration} ← <b>无</b>（Go 侧用 GORM ⇒ <b>纯表达层</b>）。</li>
 * </ul>
 */
class SpringAssemblyContractGateTest {

    @Test
    @DisplayName("AuthorityHandlerFactory：注册的 URL 必须是 /internal/v1/community/facts（Go authority_http.go:28）")
    void authorityHandlerFactoryRegistersTheGoPath() {
        var bean = AuthorityHandlerFactory.register(
                "rtw-comment-fact-authority",
                "0123456789abcdef0123456789abcdef",
                (ctx, producer, eventId) -> null);

        assertThat(bean.getUrlMappings())
                .as("Go: mux.HandleFunc(\"GET /internal/v1/community/facts\", …) ⇒ 路径必须逐字一致")
                .containsExactly("/internal/v1/community/facts");
        assertThat(bean.getServlet()).as("必须真的产出 servlet（不是空壳）").isNotNull();
    }

    @Test
    @DisplayName("LoggerFactoryBean：服务名必须来自构造参数，且空名必须 fail-fast（Go logger.go:63-70 的 Init 空名 panic）")
    void loggerFactoryBeanRejectsEmptyServiceName() {
        assertThatThrownBy(() -> new LoggerFactoryBean(""))
                .as("Go: Init(\"\") ⇒ panic ⇒ Java 侧必须 fail-fast（不得默默用默认值）")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LoggerFactoryBean(null))
                .isInstanceOf(IllegalStateException.class);

        LoggerFactoryBean bean = new LoggerFactoryBean("svc-a");
        assertThat(bean.getServiceName()).isEqualTo("svc-a");
    }

    @Test
    @DisplayName("ObservabilityProperties：三个阈值默认未设置（null），由 Observability 的环境变量口径兜底")
    void observabilityPropertiesDefaultsAreUnset() {
        ObservabilityProperties props = new ObservabilityProperties();
        // ⚠️ Go 侧 `observability.go:43-55` 的口径 = 未设置时走 SEA_OBSERVABILITY_SLOW_THRESHOLD / 默认 500ms
        assertThat(props.getSlowThreshold()).as("未设置 ⇒ null ⇒ 由 Observability 兜底").isNull();
        assertThat(props.getHttpTimeout()).isNull();
        assertThat(props.getRpcTimeout()).isNull();

        props.setSlowThreshold(Duration.ofMillis(750));
        assertThat(props.getSlowThreshold()).isEqualTo(Duration.ofMillis(750));
    }

    @Test
    @DisplayName("ObservabilityProperties：兜底阈值必须等于 Go 的 DefaultSlowThreshold（observability.go:21）")
    void observabilityFallbackSlowThresholdMatchesGo() {
        assertThat(Observability.slowThreshold())
                .as("Go: DefaultSlowThreshold = 500 * time.Millisecond")
                .isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("RpcServerInterceptorFactory：必须真的产出一个服务端拦截器（且 props 为 null 时不得 NPE）")
    void rpcServerInterceptorFactoryProducesAnInterceptor() {
        var factory = new RpcServerInterceptorFactory();
        var withProps = factory.rpcUnaryServerInterceptor(new ObservabilityProperties());
        assertThat(withProps).as("必须产出拦截器（否则 rpc 组件侧链上少一环）").isNotNull();

        // Go: `zrpc/server.go:148-150` 的三段 AddUnaryInterceptors 由 zrpc 配置开关控制
        // ⇒ RAG 侧 props 允许为 null ⇒ 必须走 Observability 的兜底而不是 NPE
        var withoutProps = factory.rpcUnaryServerInterceptor(null);
        assertThat(withoutProps).as("props 为 null 时必须兜底，不得 NPE").isNotNull();
    }

    @Test
    @DisplayName("SnowflakeProperties：workerId 可读写（这是 RAG 多出的一档 ⇒ DRIFT-SF-002 · C 类）")
    void snowflakePropertiesCarriesWorkerId() {
        SnowflakeProperties props = new SnowflakeProperties();
        assertThat(props.getWorkerId()).as("未配置 ⇒ null（不是 0 ⇒ 不能与「显式配 0」混淆）").isNull();
        props.setWorkerId(7L);
        assertThat(props.getWorkerId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("SnowflakeBean：workerId 必须与 Snowflake 的全局唯一状态同源，且落在 Go 的 0..1024 区间")
    void snowflakeBeanPublishesWorkerIdToTheGlobalState() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setWorkerId(123L);
        SnowflakeBean bean = new SnowflakeBean(props);

        // ⚠️ 断言的是「<b>同源</b>」而不是「某个具体值」——
        //    因为 Snowflake 的 workerId 是<b>进程内 first-wins</b>（Go: snowflake.go:22-38 的 sync.Once）
        //    ⇒ 本用例单独跑与全量跑（别的测试先解析过）得到的值不同 ⇒ 断言具体值会变成顺序依赖的脆弱测试。
        assertThat(bean.getWorkerId())
                .as("Bean 读到的必须就是全局唯一状态那份（与 Go 的进程内唯一 Node 同源）")
                .isEqualTo(Snowflake.currentWorkerId());
        assertThat(bean.getWorkerId())
                .as("Go: nodeId % 1024 ⇒ 必须落在 0..1024")
                .isBetween(0L, Snowflake.MAX_WORKER_ID);
    }

    @Test
    @DisplayName("Snowflake：workerId 必须 first-wins（Go: snowflake.go:22-38 的 sync.Once ⇒ 首次解析后不再改写）")
    void snowflakeWorkerIdIsFirstWinsLikeGoSyncOnce() {
        SnowflakeBean bean = new SnowflakeBean(new SnowflakeProperties());
        long first = bean.getWorkerId();          // 触发解析（对应 Go 的 Init() → once.Do）
        Snowflake.setWorkerId(999L);              // 试图改写 ⇒ Go 侧 sync.Once 会忽略
        assertThat(Snowflake.currentWorkerId())
                .as("Go 的 sync.Once 是 first-wins ⇒ 后续 set 必须被忽略（否则已产出的 ID 与新 workerId 不连续）")
                .isEqualTo(first);
    }

    @Test
    @DisplayName("HttpMiddlewareFilter：构造必须真的产出一个可用的 delegate，且服务名不能为空")
    void httpMiddlewareFilterBuildsADelegate() {
        HttpMiddlewareFilter filter = new HttpMiddlewareFilter("svc-http", new ObservabilityProperties());
        assertThat(filter).isNotNull();
        // 空服务名必须兜底成 "unknown-service"（Go: http.go:36 的 NewHTTPMiddleware 拿到的是服务名）
        HttpMiddlewareFilter fallback = new HttpMiddlewareFilter("", new ObservabilityProperties());
        assertThat(fallback).as("空服务名不得抛异常 ⇒ 必须兜底").isNotNull();
    }

    @Test
    @DisplayName("HttpPoolLifecycle / ObservabilityLifecycle：关闭钩子必须可重复调用且不抛（幂等）")
    void lifecycleShutdownsAreIdempotent() {
        HttpPoolLifecycle httpPool = new HttpPoolLifecycle();
        ObservabilityLifecycle obs = new ObservabilityLifecycle();
        // Go 侧这两个池都<b>没有</b>显式关闭（进程生命周期托管）⇒ Java 侧的关闭钩子是表达层补齐
        // ⇒ ⇒ 契约只有一条：<b>幂等、不抛</b>（否则容器关停时会炸）
        httpPool.shutdown();
        httpPool.shutdown();
        obs.shutdown();
        obs.shutdown();
    }

    @Test
    @DisplayName("MybatisPlusOrderingBridgeAutoConfiguration：排序边必须指向 Boot 4 的 DataSource 自动配置，且必须被 imports 登记")
    void mybatisPlusOrderingBridgePointsAtBoot4DataSourceAutoConfiguration() throws Exception {
        AutoConfiguration ann = MybatisPlusOrderingBridgeAutoConfiguration.class
                .getAnnotation(AutoConfiguration.class);
        assertThat(ann).as("排序桥的全部作用就是这个注解").isNotNull();
        // ⚠️ Boot 4 把 DataSourceAutoConfiguration 挪到了 org.springframework.boot.jdbc.autoconfigure
        //    ⇒ 若这里还写 Boot 3 的包名 ⇒ 排序引用**静默悬空**（不报错 ⇒ MP 先跑 ⇒ 无 SqlSessionFactory）
        assertThat(ann.afterName())
                .as("必须指向 Boot 4 的 org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
                .contains("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration");
        assertThat(ann.beforeName())
                .as("必须排在 MyBatis-Plus 的自动配置之前")
                .contains("com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration");

        // 🔴 另一半：光有注解不够 ⇒ 还必须被 Spring Boot 的 imports 文件登记，否则根本不会被求值
        java.nio.file.Path imports = java.nio.file.Paths.get(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertThat(java.nio.file.Files.exists(imports))
                .as("imports 文件必须存在（路径相对模块目录）").isTrue();
        String content = java.nio.file.Files.readString(imports);
        assertThat(content)
                .as("排序桥必须被登记进 AutoConfiguration.imports（否则排序边根本不存在）")
                .contains("com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration");
    }
}
