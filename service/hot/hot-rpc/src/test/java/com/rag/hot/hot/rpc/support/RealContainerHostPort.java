package com.rag.hot.hot.rpc.support;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.util.function.Consumer;

/**
 * <p><b>hot 域 Testcontainers 宿主口段</b> —— 把「容器发布到宿主机的那个端口」
 * 从「Docker 随机分配」改成「<b>固定在本域专用段内</b>」。
 *
 * <h2>为什么必须钉</h2>
 * <p>Testcontainers <b>没有「宿主口段」这个概念</b>：它不传 {@code -p} ⇒ Docker 自己在
 * <b>OS 临时端口段</b>（macOS：{@code 49152–65535}）里挑一个空口发布出来
 * ⇒ ⇒ 那个口和<b>别的会话的容器发布口、别的进程临时占的口</b>落在<b>同一段</b>
 * ⇒ ⇒ 谁先占住谁拿到 ⇒ ⇒ 于是会出现「我连的是那个口，但回话的<strong>不是我的容器</strong>」。
 * <p>本仓已实测到该形态：{@code EOFException @ PGStream.receiveChar /
 * ConnectionFactoryImpl.enableSSL} ⇒ 读起来像「PG 挂了」，实则是「那个口上根本不是我要的 PG」
 * ⇒ 见 {@code docs/Pitfalls/article/rpc.md} 的 {@code PF-ARTICLE-RPC-016}。
 *
 * <h2>段是怎么分的（不许自己改）</h2>
 * <p>global 端口段表把 {@code 22940–22969} <b>整段</b>预分配给 <b>hot 域</b>
 * （G4 §4.10.58 的「Testcontainers 宿主口段」hot 子段）⇒ ⇒ <b>本域自用</b>
 * ⇒ ⇒ hot 域整段都在本模块内（{@code 22940–22969}）⇒ ⇒ 无需再按模块细分。
 * <ul>
 *   <li><b>宿主口段</b>（{@link #FIRST}–{@link #LAST}）= <b>Testcontainers 宿主口</b> ⇒ 本模块自用；</li>
 *   <li><b>脚手架段</b>（{@link #SCRATCH_FIRST}–{@link #SCRATCH_LAST}）= <b>只给守门自己用</b>
 *       ⇒ <b>与生产用的口不复用</b>。</li>
 * </ul>
 *
 * <h2>为什么「每个测试类一个专属口」</h2>
 * <p>同一模块内测试类<b>串行</b>跑（surefire 默认 1 fork、无 parallel 配置）
 * ⇒ ⇒ 理论上「全模块共用一个口」也能跑 ⇒ ⇒ 但那样会出现「上一个类刚停、下一个类立刻抢同一个口」的
 * <b>释放窗口</b> ⇒ ⇒ 撞上就是 <b>EADDRINUSE</b>（这次是<b>响亮的失败</b>，不是静默错容器
 * ⇒ ⇒ 但仍是抖动）⇒ ⇒ ⇒ <b>每个测试类一个专属口，全域不复用</b>。
 *
 * <h2>⚠️ 钉口机制（已实测，两条都成立）</h2>
 * <p>G4 台账 §4.10.58 明写：「显式绑口后 {@code getMappedPort()} 是否返回那个固定口」<b>未实测</b>
 * ⇒ ⇒ 域侧落地前必须先写探针 ⇒ ⇒ <b>本类的机制已由探针实测坐实</b>：
 * <ul>
 *   <li>机制 A（{@code withCreateContainerCmdModifier} + {@code HostConfig.withPortBindings}）
 *       ⇒ {@code getMappedPort()} <b>返回那个固定口</b> ✔ ⇒ <b>本类用它</b>；</li>
 *   <li>机制 B（子类暴露 {@code addFixedExposedPort}）⇒ 同样成立 ✔
 *       ⇒ ⇒ 留档说明「不止一条路可行」，避免后来人以为只有 A；</li>
 * </ul>
 */
public final class RealContainerHostPort {

    /** hot 域在 global 端口段表里分到的<b>整段</b>（G4 分配，30 口）。 */
    public static final int DOMAIN_SEGMENT_BEGIN = 22940;

    /** hot 域整段的上界。 */
    public static final int DOMAIN_SEGMENT_END = 22969;

    /** 本模块实际使用的 Testcontainers 宿主口段。 */
    public static final int FIRST = 22940;

    /** 本模块 Testcontainers 宿主口段的上界。 */
    public static final int LAST = 22959;

    /** 守门自己的脚手架段 ⇒ <b>与 {@link #FIRST}–{@link #LAST} 不复用</b>。 */
    public static final int SCRATCH_FIRST = 22960;

    /** 脚手架段上界。 */
    public static final int SCRATCH_LAST = 22969;

    /** OS 临时端口段下界（macOS / Linux 同值）—— Testcontainers 随机宿主口就落在这里。 */
    public static final int OS_EPHEMERAL_BEGIN = 49152;

    /** OS 临时端口段上界。 */
    public static final int OS_EPHEMERAL_END = 65535;

    // ── 每个「起容器」的测试类一个**专属**固定宿主口（不复用）────────────────────
    /** HotRedisLuaGateTest 的 Redis 宿主口。 */
    public static final int HOT_REDIS_LUA_GATE = 22940;

    /** HotKafkaConsumerGateTest 的 Redis 宿主口。 */
    public static final int HOT_KAFKA_CONSUMER_GATE = 22941;

    /** HotRankingQueryServiceRedisGateTest 的 Redis 宿主口。 */
    public static final int HOT_RANKING_QUERY_REDIS_GATE = 22942;

    /** 守门自己起容器用的口（脚手架段第一口）⇒ <b>不与任何测试类复用</b>。 */
    public static final int GATE_LIVE = SCRATCH_FIRST;

    /**
     * <b>登记册</b>：本模块**测试类**用的固定宿主口<b>常量名</b>全集
     * ⇒ ⇒ 守门拿它跟「源码里实际被引用的<b>名字</b>」做<b>守恒对账</b>
     * ⇒ ⇒ 新增测试类必须同时登记 ⇒ 不登记就会被守门抓住。
     *
     * <p>⚠️ <b>为什么按「名字」登记而不是按「值」</b>：段的边界常量
     * （{@link #FIRST} = 22940）与第一个登记口（值也是 22940）
     * <b>同值</b> ⇒ ⇒ 若按值反查名字，会把 {@code FIRST} 当成登记项
     * ⇒ ⇒ 守门对不上账 ⇒ ⇒ 只能按名字登记。
     */
    public static final String[] TEST_CLASS_PORT_NAMES = {
        "HOT_REDIS_LUA_GATE",
        "HOT_KAFKA_CONSUMER_GATE",
        "HOT_RANKING_QUERY_REDIS_GATE",
    };

    /**
     * 按名字取登记口的值。
     *
     * @param name 常量名（必须出现在 {@link #TEST_CLASS_PORT_NAMES} 里）
     * @return 该常量对应的宿主口
     */
    public static int portOf(String name) {
        for (String candidate : TEST_CLASS_PORT_NAMES) {
            if (candidate.equals(name)) {
                try {
                    return RealContainerHostPort.class.getField(name).getInt(null);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("读不到常量 " + name, e);
                }
            }
        }
        throw new IllegalArgumentException("常量名 " + name + " 不在登记册里");
    }

    private RealContainerHostPort() {
    }

    /**
     * 把容器端口 {@code 5432}（PG）钉到指定宿主口。
     *
     * @param hostPort 宿主口
     * @return 可直接挂给 {@code withCreateContainerCmdModifier} 的消费者
     */
    public static Consumer<CreateContainerCmd> bindPg(int hostPort) {
        return bindTo(hostPort, 5432);
    }

    /**
     * 把容器端口 {@code 6379}（Redis）钉到指定宿主口。
     *
     * @param hostPort 宿主口
     * @return 可直接挂给 {@code withCreateContainerCmdModifier} 的消费者
     */
    public static Consumer<CreateContainerCmd> bindRedis(int hostPort) {
        return bindTo(hostPort, 6379);
    }

    /**
     * 通用钉口。
     *
     * @param hostPort 宿主口
     * @param containerPort 容器内口
     * @return 可直接挂给 {@code withCreateContainerCmdModifier} 的消费者
     */
    public static Consumer<CreateContainerCmd> bindTo(int hostPort, int containerPort) {
        requireAllocatable(hostPort);
        return cmd -> {
            HostConfig hostConfig = cmd.getHostConfig();
            if (hostConfig == null) {
                hostConfig = new HostConfig();
                cmd.withHostConfig(hostConfig);
            }
            hostConfig.withPortBindings(new PortBinding(
                    Ports.Binding.bindPort(hostPort), new ExposedPort(containerPort)));
        };
    }

    /**
     * 钉口前的<b>前置校验</b>：越段 / 落进 OS 临时端口段 ⇒ 立刻抛（而不是等到跑测时才发现）。
     *
     * <p>⚠️ 这里校验的是<b>域段</b>（{@link #DOMAIN_SEGMENT_BEGIN}–{@link #DOMAIN_SEGMENT_END}）
     * ⇒ ⇒ <b>脚手架段也在允许范围内</b> ⇒ ⇒ 因为守门自己就要用它起容器
     * ⇒ ⇒ 「生产用的口不许踩脚手架段」这条<b>不是靠这里守的</b>，
     * 而是靠 {@code RealContainerHostPortTest} 的<b>白名单</b>：
     * 源码里只允许引用<b>登记册</b>里的常量 + 守门自己的 {@code GATE_LIVE}。
     *
     * @param hostPort 待校验的宿主口
     */
    public static void requireAllocatable(int hostPort) {
        if (hostPort < DOMAIN_SEGMENT_BEGIN || hostPort > DOMAIN_SEGMENT_END) {
            throw new IllegalArgumentException("宿主口 " + hostPort
                    + " 越出 hot 域段 " + DOMAIN_SEGMENT_BEGIN + "-" + DOMAIN_SEGMENT_END
                    + "（宿主口段 " + FIRST + "-" + LAST
                    + "，脚手架段 " + SCRATCH_FIRST + "-" + SCRATCH_LAST + " 只给守门用）");
        }
        if (hostPort >= OS_EPHEMERAL_BEGIN && hostPort <= OS_EPHEMERAL_END) {
            throw new IllegalArgumentException("宿主口 " + hostPort
                    + " 落在 OS 临时端口段 " + OS_EPHEMERAL_BEGIN + "-" + OS_EPHEMERAL_END
                    + " ⇒ 会与别的进程抢口 ⇒ 这正是本类要消灭的那类抖动");
        }
    }
}
