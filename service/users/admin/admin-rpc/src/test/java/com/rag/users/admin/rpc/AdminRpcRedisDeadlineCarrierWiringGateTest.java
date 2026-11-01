package com.rag.users.admin.rpc;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.redis.GoStyleRedisDeadlineConnectionFactory;
import com.rag.users.admin.rpc.service.AdminDomainService;
import com.rag.users.admin.rpc.support.RealGrpcPort;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴🔴 <b>E66 销账的最后一格（users 域侧）：共享层那个 Redis 期限载体，在本域的<b>生产 Spring 容器</b>里到底装上了没有</b>。
 *
 * <h2>🔴 这条补的是哪一个缺口（为什么必须新写）</h2>
 * <p><b>E66</b>（＝ 本域的 {@code DRIFT-ARPC-010}，<b>同一个漂移、两个编号</b>，见
 * {@code docs/DRIFT/users/admin/rpc.md} §1.7 与 §4）的原判是：
 * <blockquote>
 * Go 侧 Redis 调用吃 ctx ⇒ deadline 到点缓存读写立即中止；
 * RAG 侧零 deadline 检查 ⇒ <b>写路径照写不误</b>。
 * </blockquote>
 * ⇒ ⇒ 两侧<b>不同形</b>。
 * <br>⇒ 共享层那半（G4 写权面）已落：{@code GoStyleRedisDeadline} ＋
 * {@code GoStyleRedisDeadlineConnectionFactory} ＋ {@code GoStyleRedisDeadlineAutoConfiguration}
 * （内含一个 {@code BeanPostProcessor}，把各 {@code RedisTemplate} 的 connection factory
 * 换成预算感知版）。
 * <br>⇒ 但<b>已落的证据只覆盖到「机制」那一层</b>：
 * <ul>
 *   <li>共享层自己的 {@code GoStyleRedisDeadlineLiveRedisWriteFaceGateTest}
 *       ⇒ 是<b>手工</b> {@code new GoStyleRedisDeadlineConnectionFactory(real, true)}
 *       ⇒ ⇒ 它测的是「<b>载体 + 工厂</b>的机制」，<b>不是</b>「<b>生产上下文里那个
 *       {@code RedisTemplate} 的 factory 真被换掉了</b>」；</li>
 *   <li>本域那条 {@code AdminLogoutDeadlineHandoffTest}
 *       ⇒ 用 Mockito <b>替换数据层</b> ⇒ ⇒ <b>不证</b>共享层代理的装配接线
 *       （本域台账 {@code docs/DRIFT/users/admin/rpc.md:3721} 明写这一条：
 *       「证据强度 = L3（静态 ＋ 域内单测；新测试用 Mockito 替换数据层 ⇒ <b>不证</b>共享层代理的装配接线）
 *       ⇒ ⇒ <b>真 Redis 活体验证待补</b>」）；</li>
 * </ul>
 * ⇒ ⇒ <b>本类补的就是这一格</b>。
 *
 * <h2>🔴 为什么必须用「生产上下文」（而不是最小 Redis 上下文）</h2>
 * <p>最小上下文（{@code @ImportAutoConfiguration(GoStyleRedisDeadlineAutoConfiguration.class)}）
 * 只能证「<b>手工 import 时</b>那个自动配置能工作」⇒ ⇒ <b>证不了</b>
 * 「<b>生产应用上下文</b>会自动捡起它」。
 * <br>⇒ 本类用 {@code classes = AdminRpcApplication.class}（<b>真的生产应用类</b>）
 * ⇒ ⇒ 该自动配置在 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 里登记 ⇒ ⇒ 由 {@code @SpringBootApplication} 的自动配置机制<b>自动</b>捡起
 * ⇒ ⇒ 本类断言的「容器里那个 template 的 factory 就是包装版」才是
 * <b>生产装配面</b>的证据，而不只是机制证据。
 * <br>⇒ ⚠️ <b>代价</b>：生产上下文要真 PG（{@code AdminSchemaBootstrap} ＋
 * {@code AdminSeederBootstrap} 都在 {@code @PostConstruct} 里摸库 ⇒ 绕不过）⇒ ⇒ 本类起 <b>2 个真容器</b>。
 *
 * <h2>🔴 四条判据（缺一条都不够）</h2>
 * <ol>
 *   <li><b>装配面</b>：容器里<b>有且只有一个</b> {@code StringRedisTemplate}
 *       ⇒ ⇒ 且它的 {@code getConnectionFactory()} <b>就是</b>
 *       {@link GoStyleRedisDeadlineConnectionFactory}
 *       ⇒ ⇒ 且包装器<b>包着真 factory</b>（不是包空气、也不是重复包装）
 *       ⇒ ⇒ 「只有一个」这条是<b>咬合</b>用的：{@code AdminDomainService} 是<b>按类型</b>注入
 *       {@code StringRedisTemplate} 的（{@code AdminDomainService.java:136}）
 *       ⇒ ⇒ 容器里若有两个，它拿到的<b>未必</b>是被包过的那个；</li>
 *   <li><b>耗尽 ⇒ 不下发</b>：绑一个<b>已过期</b>的载体 ⇒ {@code set} 必须抛
 *       {@link QueryTimeoutException} ⇒ ⇒ 且<b>读回来还是基线值</b>
 *       （⇒ 观测量是「<b>值有没有变</b>」，不是靠异常类型推断）；</li>
 *   <li><b>充足 ⇒ 写得进</b>（负控）：绑一个<b>未过期</b>的载体 ⇒ 同样的 {@code set} 必须成功
 *       ⇒ ⇒ 否则第 ② 条可能是「Redis 挂了 / 键名错了 / 序列化坏了」而不是「闸门生效」；</li>
 *   <li><b>生产写路径端到端</b>：走<b>真</b>的 {@code AdminDomainService.logout(token)}
 *       （＝ 本域 E66 的<b>本体</b>：{@code admin:jwt_blacklist:<token>} 那个写）
 *       ⇒ ⇒ 载体耗尽时 ⇒ <b>黑名单写不进去</b>（值不得出现）
 *       ⇒ ⇒ 且<b>客户端可见码</b> = gRPC {@code INTERNAL} ＋ 文案「注销失败,Redis写入异常」
 *       ⇒ ⇒ 与 Go 的 {@code codes.Internal} <b>逐字同形</b>。</li>
 * </ol>
 *
 * <h2>Go 出处与语义等价论证（行为层，逐条对应）</h2>
 * <ul>
 *   <li><b>Go 侧调用点带 ctx</b>：{@code service/user/admin/rpc/internal/logic/logoutlogic.go:38}
 *       {@code err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))}
 *       ⇒ ⇒ <b>ctx 一路传到 Redis 命令</b>；同文件 {@code :39-42} 写失败 ⇒
 *       {@code status.Error(codes.Internal, "注销失败,Redis写入异常")}；</li>
 *   <li><b>Go 侧「到点就不下发」</b>：go-redis v9.20.0 {@code internal/pool/pool.go:1098-1104}
 *       {@code ConnPool.waitTurn} 的 fast path <b>无条件先查 {@code ctx.Done()}</b>
 *       ⇒ ⇒ ctx 已过期 ⇒ <b>命令根本没发出去</b>（不是发出去再报错）；</li>
 *   <li><b>RAG 侧对应物</b>：共享层 {@code GoStyleRedisDeadline.assertNotExhausted}
 *       （{@code service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadline.java:167}）
 *       在 {@code GoStyleRedisDeadlineConnectionFactory.getConnection()}（{@code :58}）里被调
 *       ⇒ ⇒ <b>同样是在「取连接」这一步拦</b> ⇒ ⇒ 语义位置与 Go 侧同形；</li>
 *   <li><b>RAG 侧调用点</b>：{@code service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:290}
 *       {@code stringRedisTemplate.opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds))}
 *       （key 前缀 {@code AdminDomainService.java:118} = {@code "admin:jwt_blacklist:"}）
 *       ⇒ ⇒ 本类判据 ④ 断言的<b>正是它</b>；</li>
 *   <li><b>「没有载体」≠「耗尽」</b>：{@code GoStyleRedisDeadline.remainingNanos()} 在
 *       <b>没绑</b>时返回 {@code -1}（{@code GoStyleRequestDeadline.java:111-113}）
 *       ⇒ ⇒ 对应 Go 侧「ctx 没有 deadline」⇒ ⇒ <b>行为原样放行</b>
 *       ⇒ ⇒ 本类的负控（判据 ③）与「键入前先写基线」都依赖这条 ⇒ 不可省略。</li>
 * </ul>
 *
 * <h2>⚠️ 口径边界（别过度解读）</h2>
 * <ul>
 *   <li>本类需要 <b>docker</b> ⇒ ⇒ 无 docker 时整类 <b>skip</b>（记「skipped」，不记「通过」）；</li>
 *   <li>本类测的是 <b>RAG 侧单边</b> ⇒ ⇒ <b>不</b>重新做 Go 侧 ⇒ ⇒ 因此本类<b>不</b>主张
 *       「双侧活体对拍」，只主张「<b>RAG 侧在生产上下文 + 真 Redis 容器里确实装上了那个闸门，
 *       且行为与 Go 已登记基线同形</b>」⇒ ⇒ 双侧那一半由
 *       {@code tools/semantic-probe/redis-deadline/} 承担；</li>
 *   <li>本类<b>不外推</b>到别的域、也不外推到本域的别的组件 ⇒ ⇒ {@code user-rpc} /
 *       {@code user-api} / {@code admin-api} 三个组件的 Redis 用点<b>另有各自身份</b>
 *       ⇒ ⇒ 本类只覆盖 {@code admin-rpc}（＝ {@code DRIFT-ARPC-010} 的登记落点）；</li>
 *   <li>⚠️ <b>共享层那层代理只覆盖「经 {@code RedisTemplate} 的路径」</b> ——
 *       它换的是 {@code RedisTemplate} 里那个 factory 引用，<b>不是</b>换容器里的
 *       {@code RedisConnectionFactory} Bean ⇒ ⇒ 直接注入 factory 的代码拿到的仍是裸 factory
 *       ⇒ ⇒ 本域 {@code main} 代码对 {@code RedisConnectionFactory} <b>零</b>直接注入
 *       ⇒ ⇒ 本域无缺口（共享层 javadoc {@code GoStyleRedisDeadlineAutoConfiguration.java:32-45}
 *       已登记该边界）。</li>
 * </ul>
 */
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 🔴 专属上下文缓存键：Spring 的 context cache key **包含 properties**，
                //    但**不含 @DynamicPropertySource 的值** ⇒ ⇒ 必须用本域专属键，
                //    否则本类可能读到别的测试装配出来的 template（= 别的容器）。
                "rag.test.gate.context-key=admin-rpc-redis-deadline-carrier-wiring",
                // 绕过 Nacos（拒连比 DNS 失败更快，也不会让测试挂住）
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                // OTLP 指向不可达端点，只会在后台重试刷日志，关掉
                "management.otlp.tracing.export.enabled=false"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRpcRedisDeadlineCarrierWiringGateTest {

    /** 真 PostgreSQL 容器（生产上下文要摸库：{@code AdminSchemaBootstrap} + {@code AdminSeederBootstrap}）。 */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    /** 真 Redis 容器（宿主口钉在 users 域段 {@code 22700–22729}，与 PG 同段但不同口）。 */
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // ⚠️ Redis 宿主口**必须动态给**（容器映射端口运行期才知道）
        //    ⇒ ⇒ 它**不在** Spring 的 context cache key 里 ⇒ ⇒ 上面那个专属键是必需的。
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
        r.add("grpc.server.port", () -> String.valueOf(RealGrpcPort.pick()));
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** <b>生产容器里那个</b> {@code StringRedisTemplate}（= {@code AdminDomainService} 注入的那一个）。 */
    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AdminDomainService adminDomainService;

    // ------------------------------------------------------------------ 判据 ①

    @Test
    @DisplayName("① 装配面：生产上下文里那个 StringRedisTemplate 的 connection factory 就是共享层的期限载体（且有且只有一个）")
    void containerTemplateIsWrappedBySharedLayer() {
        Map<String, StringRedisTemplate> templates =
                context.getBeansOfType(StringRedisTemplate.class);

        assertEquals(1, templates.size(),
                "① 生产上下文里必须**有且只有一个** StringRedisTemplate"
                        + " —— `AdminDomainService` 是**按类型**注入它的"
                        + " ⇒ ⇒ 若有两个，它拿到的未必是被包过的那个 ⇒ ⇒ 「只有一个」是咬合用的。"
                        + " 实测：" + templates.keySet());

        StringRedisTemplate template = templates.values().iterator().next();
        RedisConnectionFactory factory = template.getConnectionFactory();

        assertInstanceOf(GoStyleRedisDeadlineConnectionFactory.class, factory,
                "① 共享层那个 BeanPostProcessor 必须**真的**把 `RedisTemplate` 的"
                        + " connection factory 换成了预算感知版"
                        + " —— 否则本域 admin `logout` 的 Redis 写**根本没有 deadline 闸门**"
                        + " ⇒ ⇒ E66 的「写路径照写不误」就仍然成立"
                        + "（本域台账 admin/rpc.md:3721 明写这一格「待真 Redis 活体验证」）。"
                        + " 实测 factory 类型：" + (factory == null ? "null" : factory.getClass().getName()));

        GoStyleRedisDeadlineConnectionFactory wrapper = (GoStyleRedisDeadlineConnectionFactory) factory;
        RedisConnectionFactory delegate = wrapper.delegate();

        assertNotNull(delegate, "① 包装器必须包着**真** factory（不是包空气）");
        assertFalse(delegate instanceof GoStyleRedisDeadlineConnectionFactory,
                "① 包装器里不能再套一层包装器（重复包装 ⇒ 说明装配面被跑了不止一次）");
        assertInstanceOf(LettuceConnectionFactory.class, delegate,
                "① 被包的必须是本仓实际用的 Lettuce factory（`spring-boot-starter-data-redis` 默认）");
    }

    // ------------------------------------------------------------------ 判据 ②③

    @Test
    @DisplayName("② 载体已耗尽 ⇒ 写命令根本没下发（读回来还是基线值）＋ ③ 载体充足 ⇒ 写得进（负控）")
    void exhaustedCarrierBlocksTheWriteWhileAliveCarrierWrites() {
        String key = "admin-rpc-redis-deadline-carrier-wiring";
        String baseline = "baseline";

        // 先写基线（此刻**没有**载体 ⇒ 「没有载体」≠「耗尽」⇒ 不受闸门影响）
        redis.opsForValue().set(key, baseline);

        // ---- ② 载体已耗尽 ⇒ 必须拦下，且**值没变**
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() - 1_000_000L)) {
            assertThrows(QueryTimeoutException.class, () -> redis.opsForValue().set(key, "overwritten"),
                    "② 载体已耗尽 ⇒ 必须抛 QueryTimeoutException"
                            + "（共享层 `GoStyleRedisDeadline.assertNotExhausted` 在**取连接**这一步拦）");
        }

        assertEquals(baseline, redis.opsForValue().get(key),
                "② 关键观测量：**读回来还是基线值** ⇒ ⇒ 命令**根本没下发**"
                        + "（不是发出去再报错）⇒ ⇒ 与 Go 侧「到点即止」同形");

        // ---- ③ 负控：载体充足 ⇒ 同一个 key 必须写得进
        //    ⇒ 否则第 ② 条可能只是「Redis 挂了 / 键名错了 / 序列化坏了」
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + 5_000_000_000L)) {
            redis.opsForValue().set(key, "written");
        }

        assertEquals("written", redis.opsForValue().get(key),
                "③ 负控：载体充足时必须**写得进** ⇒ ⇒ 第 ② 条的红是「闸门生效」而不是「环境坏了」");
    }

    // ------------------------------------------------------------------ 判据 ④

    @Test
    @DisplayName("④ 生产写路径端到端：载体耗尽时 admin logout 的黑名单**写不进去**，且客户端可见码 = INTERNAL「注销失败,Redis写入异常」")
    void productionLogoutWritePathIsBlockedWhenDeadlineExhausted() {
        String token = "e66-carrier-wiring-token";
        // key 前缀与 AdminDomainService.java:118 逐字一致
        String key = "admin:jwt_blacklist:" + token;

        // 前置：黑名单里**没有**这个 token
        assertFalse(Boolean.TRUE.equals(redis.hasKey(key)),
                "④ 前置校验：黑名单 key 初始必须不存在（实测 hasKey=" + redis.hasKey(key) + "）");

        StatusRuntimeException thrown;
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() - 1_000_000L)) {
            thrown = assertThrows(StatusRuntimeException.class,
                    () -> {
                        adminDomainService.logout(token);
                    },
                    "④ 载体耗尽时，admin `logout` 必须失败（Redis 写被共享层载体拦下）"
                            + " —— 否则就是 E66 的本体漂移「写路径照写不误」");
        }

        assertEquals(Status.Code.INTERNAL, thrown.getStatus().getCode(),
                "④ 客户端可见码必须是 gRPC INTERNAL"
                        + "（Go 侧 logoutlogic.go:39-42 = `status.Error(codes.Internal, \"注销失败,Redis写入异常\")`）"
                        + " ⇒ ⇒ 本域 `AdminDomainService.logout` 的 `catch (Exception e)` 分支"
                        + "（共享层抛的是 `QueryTimeoutException`，**不是** grpc 的 `StatusRuntimeException`"
                        + " ⇒ ⇒ 落进那个分支 ⇒ INTERNAL）"
                        + " 实测码：" + thrown.getStatus().getCode()
                        + " / 文案：" + thrown.getStatus().getDescription());

        assertEquals("注销失败,Redis写入异常", thrown.getStatus().getDescription(),
                "④ 文案必须与 Go 侧逐字一致（`logoutlogic.go:40`）");

        assertFalse(Boolean.TRUE.equals(redis.hasKey(key)),
                "④ 关键观测量：黑名单 key **不得出现** ⇒ ⇒ 命令**根本没下发**"
                        + " ⇒ ⇒ 与 Go 侧「ctx 到点 ⇒ SetexCtx 失败 ⇒ 不写」同形"
                        + "（Go 出处：logoutlogic.go:38 `SetexCtx(l.ctx, …)`）");
    }

    // ------------------------------------------------------------------ 辅助

    /** 把「实测到的形态」打成一行，便于台账引用**具体读数**而不是「断言通过」。 */
    @Test
    @DisplayName("⑤ 读数留痕：容器里 RedisTemplate 的 factory 形态（供台账引用实测值）")
    void printWiringFacts() {
        Map<String, StringRedisTemplate> templates = context.getBeansOfType(StringRedisTemplate.class);
        StringRedisTemplate template = templates.values().iterator().next();
        RedisConnectionFactory factory = template.getConnectionFactory();
        RedisConnectionFactory delegate = factory instanceof GoStyleRedisDeadlineConnectionFactory w
                ? w.delegate()
                : null;
        assertTrue(templates.size() == 1, "⑤ 与判据 ① 同源：必须恰好一个 template");
        System.out.printf(
                "[E66③ 装配面实测] admin-rpc StringRedisTemplate 数=%d · factory=%s · delegate=%s%n",
                templates.size(),
                factory == null ? "null" : factory.getClass().getName(),
                delegate == null ? "null" : delegate.getClass().getName());
    }
}
