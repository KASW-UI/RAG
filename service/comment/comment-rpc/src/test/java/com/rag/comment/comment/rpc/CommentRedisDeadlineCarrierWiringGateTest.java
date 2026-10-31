package com.rag.comment.comment.rpc;

import com.rag.comment.comment.rpc.support.RealContainerHostPort;
import com.rag.comment.comment.rpc.support.RealGrpcPort;
import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.redis.GoStyleRedisDeadlineConnectionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴🔴 <b>E66 销账的最后一格：共享层那个 Redis 期限载体，在<b>本域的真实容器</b>里到底装上了没有</b>。
 *
 * <h2>🔴 这条补的是哪一个缺口（为什么必须新写）</h2>
 * <p>E66 原判是「Go 侧 Redis 调用吃 ctx ⇒ deadline 到点缓存读写立即中止；RAG 侧零 deadline 检查
 * ⇒ <b>写路径照写不误</b>」⇒ ⇒ 两侧<b>不同形</b>。
 * <br>⇒ 共享层那半（G4）已落：{@code GoStyleRedisDeadline} ＋
 * {@code GoStyleRedisDeadlineConnectionFactory} ＋
 * {@code GoStyleRedisDeadlineAutoConfiguration}（内含一个
 * {@code BeanPostProcessor}，把各 {@code RedisTemplate} 的 connection factory
 * 换成预算感知版）。
 * <br>⇒ 但<b>已落的证据只覆盖到「机制」那一层</b>：
 * <ul>
 *   <li>共享层自己的 {@code GoStyleRedisDeadlineLiveRedisWriteFaceGateTest}
 *       ⇒ 是<b>手工</b> {@code new GoStyleRedisDeadlineConnectionFactory(real, true)}
 *       ⇒ ⇒ 它测的是「<b>载体 + 工厂</b>的机制」，<b>不是</b>「<b>BPP 在真实 Spring 容器里
 *       把 {@code RedisTemplate} 的 factory 换掉了</b>」；</li>
 *   <li>2026-10-31 新探针 {@code tools/semantic-probe/redis-deadline/}（合并收口执行者）
 *       ⇒ 两侧 PROBE 行逐键相等 ⇒ ⇒ 它证的是「<b>两侧的写面行为同形</b>」，
 *       <b>但它的 RAG 半边同样是直接 new 那个包装 factory</b>
 *       ⇒ ⇒ <b>装配面那一格仍然只有静态论证</b>。</li>
 * </ul>
 * ⇒ ⇒ **本类补的就是这一格**：起 comment-rpc 的<b>真 Spring 容器</b> ＋ <b>真 Redis 容器</b>
 * ⇒ ⇒ 断言<b>生产容器里那个 {@code StringRedisTemplate}</b>（= {@code CommentCacheService}
 * 按类型注入的那一个）的 connection factory <b>真的就是</b>包装版
 * ⇒ ⇒ 并且<b>用它</b>做一次真写：载体耗尽 ⇒ 写不下；载体充足 ⇒ 写得进。
 *
 * <h2>🔴 为什么「用容器里那个 template」是关键（而不是手工造一个）</h2>
 * <p>手工造 template ⇒ 走的是「我主动 new 了包装 factory」⇒ ⇒ 只证机制。
 * <br>⇒ 用<b>容器里那个</b> ⇒ 才证「<b>装配面</b>真的把它包上了」
 * ⇒ ⇒ 这是 E66 从「机制已证」走到「<b>两侧真的同形</b>」的最后一格。
 *
 * <h2>🔴 四条判据（缺一条都不够）</h2>
 * <ol>
 *   <li><b>装配面</b>：容器里<b>有且只有一个</b> {@code StringRedisTemplate}
 *       ⇒ ⇒ 且它的 {@code getConnectionFactory()} <b>就是</b>
 *       {@link GoStyleRedisDeadlineConnectionFactory}
 *       ⇒ ⇒ 「只有一个」这条是<b>咬合</b>用的：{@code CommentCacheService} 是<b>按类型</b>注入
 *       {@code StringRedisTemplate} 的 ⇒ ⇒ 容器里若有两个，它拿到的<b>未必</b>是被包过的那个；</li>
 *   <li><b>耗尽 ⇒ 不下发</b>：绑一个<b>已过期</b>的载体 ⇒ {@code set} 必须抛
 *       {@link QueryTimeoutException} ⇒ ⇒ 且<b>读回来还是基线值</b>
 *       （⇒ 观测量是「<b>值有没有变</b>」，不是靠异常类型推断）;</li>
 *   <li><b>充足 ⇒ 写得进</b>（负控）：绑一个<b>未过期</b>的载体 ⇒ 同样的 {@code set} 必须成功
 *       ⇒ ⇒ 否则第 ② 条可能是「Redis 挂了 / 键名错了 / 序列化坏了」而不是「闸门生效」。</li>
 *   <li><b>事务绑定路径也拦得住</b>：{@code redis.execute(SessionCallback)} ⇒ 连接被<b>事务绑定</b>
 *       ⇒ ⇒ <b>不再走</b>「建连之前」那道检查 ⇒ ⇒ 只有<b>每命令一次</b>的检查能兜住
 *       ⇒ ⇒ 而本域 {@code CommentCacheService.java:429}/{@code :433} 与 {@code :524}/{@code :528}
 *       <b>两条写路径正是这个形态</b> ⇒ ⇒ 不测这一条 ⇒ 台账那句「写路径不再照写不误」<b>少一半证据</b>。</li>
 * </ol>
 *
 * <h2>🔴 覆盖面：共享层那层代理到底拦哪些方法（javap 实测，不是推理）</h2>
 * <p>共享层的判定 = 「方法的<b>声明类</b>是不是
 * {@code org.springframework.data.redis.connection.DefaultedRedisConnection}」。
 * <br>⇒ 用 <b>javap 读本仓实际用的 jar</b>（{@code spring-data-redis 4.0.7}，
 * 由 {@code mvn -o -pl service/common dependency:list} 实测得到，不是猜的）：
 * <ul>
 *   <li>{@code get(byte[])} / {@code set(byte[], byte[])} / {@code mGet(byte[]...)}
 *       / {@code del(byte[]...)} / {@code scan(ScanOptions)}
 *       ⇒ <b>全部</b>由 {@code DefaultedRedisConnection} <b>重声明</b> ⇒ ⇒ <b>全部命中</b>拦截；</li>
 *   <li>{@code close} / {@code isQueueing} / {@code openPipeline} / {@code getRedisCommands}
 *       ⇒ 声明在 {@code RedisConnection} 自身 ⇒ ⇒ <b>不拦截</b>（生命周期/访问器，避免破坏事务回滚与 pipeline 收尾）。</li>
 * </ul>
 * <p>⇒ ⇒ 本域 {@code CommentCacheService.java} 的<b>全部</b>缓存读写
 * （{@code :250} {@code get} · {@code :279} {@code set} · {@code :331} {@code set} ·
 * {@code :341} {@code get} · {@code :376} {@code multiGet} · {@code :429}/{@code :433}
 * {@code SessionCallback} 里的 {@code set} · {@code :479} {@code multiGet} ·
 * {@code :524}/{@code :528} {@code SessionCallback} 里的 {@code set} ·
 * {@code :574} {@code scan} · {@code :619} {@code delete}）
 * <b>都在拦截面内</b>。
 * <br>⚠️ <b>诚实边界</b>：<b>pipeline 面不覆盖</b>（命令在 {@code closePipeline()} 时才下发，
 * 而它不拦截）—— 本域<b>零 pipeline 写</b>（两处多键写走的是 {@code SessionCallback}）⇒ ⇒ 本域无缺口。
 *
 * <h2>Go 出处与语义等价论证（行为层，逐条对应）</h2>
 * <ul>
 *   <li><b>Go 侧调用点带 ctx</b>：{@code service/comment/rpc/internal/cache/comment_subject_cache.go:76}
 *       （{@code GetSubjectCache(ctx context.Context, subjectID string)}）＋ {@code :84}
 *       （{@code c.rdb.Get(ctx, SubjectKey(subjectID))}）＋ {@code :103}
 *       （{@code SetSubjectCache(ctx, …)}）⇒ ⇒ <b>ctx 一路传到 Redis 命令</b>；</li>
 *   <li><b>Go 侧「到点就不下发」</b>：go-redis 在<b>借连接前</b>查 {@code ctx.Done()}
 *       ⇒ ⇒ ctx 已过期 ⇒ <b>命令根本没发出去</b>（不是发出去再报错）
 *       ⇒ ⇒ 2026-10-31 双侧活体探针 {@code tools/semantic-probe/redis-deadline/}
 *       实测两侧逐键相等：{@code form=exhausted|write_applied=false|err_is_deadline=true|value_after=baseline}；</li>
 *   <li><b>RAG 侧对应物</b>：共享层 {@code GoStyleRedisDeadline.assertNotExhausted}
 *       （{@code service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadline.java:167}）
 *       在 {@code GoStyleRedisDeadlineConnectionFactory.getConnection()} 里被调
 *       ⇒ ⇒ <b>同样是在「取连接」这一步拦</b> ⇒ ⇒ 语义位置与 Go 侧同形；</li>
 *   <li><b>RAG 侧调用点</b>：{@code service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentCacheService.java:88}
 *       注入 {@code StringRedisTemplate} ⇒ ⇒ 本类断言的<b>正是它拿到的那一个</b>。</li>
 * </ul>
 *
 * <h2>⚠️ 口径边界（别过度解读）</h2>
 * <ul>
 *   <li>本类需要 <b>docker</b> ⇒ ⇒ 无 docker 时整类 <b>skip</b>（记「skipped」，不记「通过」）；</li>
 *   <li>本类测的是 <b>RAG 侧单边</b> ⇒ ⇒ <b>不</b>重新做 Go 侧 ⇒ ⇒ 因此本类<b>不</b>主张
 *       「双侧活体对拍」，只主张「<b>RAG 侧在真实容器里确实装上了那个闸门，且行为与 Go 已登记基线同形</b>」
 *       ⇒ ⇒ 双侧那一半由 {@code tools/semantic-probe/redis-deadline/} 承担；</li>
 *   <li>本类<b>不外推</b>到别的域 ⇒ ⇒ 每个域的上下文不同 ⇒ ⇒ <b>每个域自己跑一遍</b>才是证据。</li>
 * </ul>
 */
@SpringBootTest(
        classes = CommentRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 🔴 专属上下文缓存键：Spring 的 context cache key **包含 properties**，
                //    但**不含 @DynamicPropertySource 的值** ⇒ ⇒ 必须用本域专属键，
                //    否则本类可能读到别的测试装配出来的 template。
                "rag.test.gate.context-key=comment-redis-deadline-carrier-wiring",
                // 绕过 Nacos
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                "management.otlp.tracing.export.enabled=false",
                // 绕过两个出向 gRPC 客户端（devh 在 Bean 初始化期就建 channel ⇒ 不覆盖则上下文起不来）
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                // 绕过 Kafka（真 Redis 在 @DynamicPropertySource 里给）
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 PG：数据源指向不可达端口 ⇒ 任何摸库都**快速失败**（不挂起）
                "POSTGRES_HOST=127.0.0.1",
                "POSTGRES_PORT=1",
                // ⚠️ 本测试与 DB 无关 ⇒ 排除 Flyway（否则上下文启动时会去建表 ⇒ 需要真 PG）。
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@Testcontainers
@DisplayName("comment-rpc：共享层的 Redis 期限载体在本域**真实容器**里确实装上了（E66 销账的最后一格）")
class CommentRedisDeadlineCarrierWiringGateTest {

    /**
     * 真 Redis 容器（宿主口走本域<b>测试类段</b>的一个**专属**固定口 ⇒ 不与任何测试类复用，
     * 也不落 OS 临时端口段 ⇒ 与 Docker 发布口不同段）。
     *
     * <p>⚠️ **为什么不能用脚手架段的 {@code GATE_LIVE_REDIS}**：那是<b>守门自己</b>起容器用的口
     * ⇒ ⇒ {@code RealContainerHostPortTest} 的「每个口恰好被引用一次」守恒断言会判红
     * ⇒ ⇒ 测试类必须落在**测试类段**并在 {@code RealContainerHostPort} 的登记册里登记。
     */
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCreateContainerCmdModifier(
                    RealContainerHostPort.bindRedis(
                            RealContainerHostPort.COMMENT_REDIS_DEADLINE_CARRIER_WIRING));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // ⚠️ Redis 宿主口**必须动态给**（容器映射端口运行期才知道）
        //    ⇒ ⇒ 它**不在** Spring 的 context cache key 里 ⇒ ⇒ 上面那个专属键是必需的。
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("grpc.server.port", RealGrpcPort::pick);
    }

    /** <b>生产容器里那个</b> {@code StringRedisTemplate}（= {@code CommentCacheService} 注入的那一个）。 */
    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ApplicationContext context;

    // ------------------------------------------------------------------ 判据 ①

    @Test
    @DisplayName("① 装配面：容器里那个 StringRedisTemplate 的 connection factory 就是包装版（且有且只有一个）")
    void containerTemplateIsWrappedBySharedLayer() {
        Map<String, StringRedisTemplate> templates =
                context.getBeansOfType(StringRedisTemplate.class);

        assertThat(templates)
                .as("① 容器里必须**有且只有一个** StringRedisTemplate"
                        + " —— `CommentCacheService` 是**按类型**注入它的"
                        + " ⇒ ⇒ 若有两个，它拿到的未必是被包过的那个 ⇒ ⇒ 「只有一个」是咬合用的")
                .hasSize(1);

        StringRedisTemplate template = templates.values().iterator().next();
        assertThat(template.getConnectionFactory())
                .as("① 共享层那个 BeanPostProcessor 必须**真的**把 `RedisTemplate` 的"
                        + " connection factory 换成了预算感知版"
                        + " —— 否则本域所有经 RedisTemplate 的缓存读写**根本没有 deadline 闸门**"
                        + " ⇒ ⇒ E66 的「照写不误」就仍然成立")
                .isInstanceOf(GoStyleRedisDeadlineConnectionFactory.class);
    }

    // ------------------------------------------------------------------ 判据 ②③

    @Test
    @DisplayName("② 载体已耗尽 ⇒ 写命令根本没下发（读回来还是基线值）＋ ③ 载体充足 ⇒ 写得进（负控）")
    void exhaustedCarrierBlocksTheWriteWhileAliveCarrierWrites() {
        String key = "comment-deadline-carrier-wiring";
        String baseline = "baseline";

        // 先写基线（此刻**没有**载体 ⇒ 不受闸门影响）
        redis.opsForValue().set(key, baseline);

        // ---- ② 载体已耗尽 ⇒ 必须拦下，且**值没变**
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() - 1_000_000L)) {
            assertThatThrownBy(() -> redis.opsForValue().set(key, "overwritten"))
                    .as("② 载体已耗尽 ⇒ 必须抛 QueryTimeoutException"
                            + "（共享层 `GoStyleRedisDeadline.assertNotExhausted` 在**取连接**这一步拦）")
                    .isInstanceOf(QueryTimeoutException.class);
        }

        assertThat(redis.opsForValue().get(key))
                .as("② 关键观测量：**读回来还是基线值** ⇒ ⇒ 命令**根本没下发**"
                        + "（不是发出去再报错）⇒ ⇒ 与 Go 侧「到点即止」同形")
                .isEqualTo(baseline);

        // ---- ③ 负控：载体充足 ⇒ 同一个 key 必须写得进
        //    ⇒ 否则第 ② 条可能只是「Redis 挂了 / 键名错了 / 序列化坏了」
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + 5_000_000_000L)) {
            redis.opsForValue().set(key, "written");
        }

        assertThat(redis.opsForValue().get(key))
                .as("③ 负控：载体充足时必须**写得进** ⇒ ⇒ 第 ② 条的红是「闸门生效」而不是「环境坏了」")
                .isEqualTo("written");
    }

    // ------------------------------------------------------------------ 判据 ④

    @Test
    @DisplayName("④ 事务绑定路径（redis.execute(SessionCallback)）：载体耗尽 ⇒ 内层写命令**也**被拦（值不得变）")
    void exhaustedCarrierAlsoBlocksSessionCallbackWrites() {
        String key = "comment-deadline-carrier-wiring-tx";
        String baseline = "baseline";

        redis.opsForValue().set(key, baseline);

        // 🔴 为什么必须单独测这一条（不是「重复测一遍」）：
        //   `redis.execute(SessionCallback)` ⇒ 连接被**事务绑定** ⇒ 复用同一条连接
        //   ⇒ ⇒ **不再走** `getConnection()` 里那道「建连之前」的检查
        //   ⇒ ⇒ 只有**每命令一次**的检查（`GoStyleRedisDeadlineConnection`）能兜住
        //   ⇒ ⇒ 而本域 `CommentCacheService.java:429`/`:433` 与 `:524`/`:528`
        //       **两条写路径正是这个形态** ⇒ ⇒ 不测这一条 = 台账那句「写路径不再照写不误」
        //       **少一半证据**。
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() - 1_000_000L)) {
            assertThatThrownBy(() -> redis.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) {
                    operations.opsForValue().set(key, "overwritten");
                    return null;
                }
            }))
                    .as("④ 事务绑定路径**不走**「建连之前」那道检查 ⇒ ⇒ 必须靠**每命令一次**的检查兜住"
                            + " ⇒ ⇒ 与 Go 侧「每条命令都过一次 waitTurn」同形")
                    .isInstanceOf(QueryTimeoutException.class);
        }

        assertThat(redis.opsForValue().get(key))
                .as("④ 关键观测量：值**没变** ⇒ ⇒ 事务绑定路径下的内层写命令**根本没下发**")
                .isEqualTo(baseline);
    }
}
