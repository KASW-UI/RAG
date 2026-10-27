package com.rag.users.user.rpc;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.rag.users.user.rpc.support.GoStyleHostPortPinning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **Testcontainers 宿主口「钉段」的第 0 步探针**（G4 §4.10.39 ② 明确要求：域侧落地时必须先写探针验证，不能照抄）。
 *
 * <p><b>为什么要有这一条</b>：
 * G4 在全局台账里分配了 Testcontainers 宿主口段 —— {@code users} 域 {@code 22700–22729}、
 * {@code follow} 域 {@code 22730–22759} —— 目的是让宿主口**不再落进 OS 临时段**
 * （{@code 49152–65535}：该段同时被出站连接的临时源口占用，实测当前被占 205/16384）。
 * 但 G4 同时留了两个**未实测**的边界，本类就是其中第二个的答案：
 * <b>「显式绑口后 {@code getMappedPort()} 是否返回那个固定口」</b>。
 *
 * <p><b>本类断言什么（全部是真运行证据，不是读声明）</b>：
 * <ol>
 *   <li>显式绑口后 {@code getMappedPort(5432)} **等于**我们指定的那个固定宿主口
 *       ⇒ ⇒ 「钉口」在 Testcontainers 2.0.5 上**真的生效**，而不是只写进了绑定记录。</li>
 *   <li>该宿主口**落在分配段内**（{@code 22700–22729}）⇒ ⇒ 段边界这条硬约束成立。</li>
 *   <li>用 {@code getJdbcUrl()} **真连一次并建表** ⇒ ⇒ 证明「端口不是只有绑定记录好看、实际不通」。</li>
 * </ol>
 *
 * <p><b>⚠️ 为什么必须用 {@code withCreateContainerCmdModifier} 而不是「换容器类型」</b>：
 * {@code FixedHostPortGenericContainer} 不带 PG 的初始化/JDBC 支持，换过去会把 PG 用例全废掉；
 * 而 {@code GenericContainer.addFixedExposedPort(int,int)} 是 {@code protected}
 * ⇒ 从域侧测试类<b>不可直接调用</b>（只能子类化绕开）⇒ ⇒ 公开且正交的路径只有
 * {@code withCreateContainerCmdModifier} —— 这也是 G4 §4.10.39 ② 指定的形态。
 *
 * <p><b>⚠️ 绑定的替换语义（读消费点得出，不是猜）</b>：
 * {@code HostConfig.withPortBindings(List)} 是<b>整体替换</b>而不是追加
 * ⇒ ⇒ 若只往里加一条固定绑定，默认那条随机绑定会与之并存（同一个容器口两条绑定）⇒ ⇒ 必须先
 * <b>剔除该容器口的旧绑定</b>，再放固定绑定；<b>其它容器口的绑定必须原样保留</b>
 * （本类只暴露 5432，但落地工具会按同一规则处理多口容器）。
 *
 * <p><b>无 Docker 时自动跳过</b>。
 */
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class UserRpcTestcontainersHostPortSegmentGateTest {

    /** G4 §4.10.39 ② 分配的 users 域宿主口段。 */
    static final int SEGMENT_START = GoStyleHostPortPinning.SEGMENT_START;
    static final int SEGMENT_END = GoStyleHostPortPinning.SEGMENT_END;

    /**
     * 在分配段内挑一个<b>当前空闲</b>的宿主口。
     *
     * <p>「占用要可自愈」：被占时<b>换同段内下一个</b>，而不是直接失败 ⇒ ⇒ 这也是
     * 「不把钉口写成端口写死」的实现形态 —— 同段内仍能并行起多个容器。
     *
     * <p>⚠️ <b>本类不再自带实现</b> ⇒ ⇒ 统一委托给 {@link GoStyleHostPortPinning}
     * ⇒ ⇒ 这样本探针测的**就是落地工具本体**，而不是一份会漂的副本。
     */
    static int pickFreeHostPortInSegment() {
        return GoStyleHostPortPinning.pickFreeHostPortInSegment();
    }

    /**
     * modifier 运行时**看到的**暴露口 —— 落地工具要靠它「逐口钉」（多口容器只钉一口 ⇒ 另一口会失去绑定
     * ⇒ 容器启动失败）⇒ ⇒ 必须证明它在 modifier 运行时**已经填充**。
     */
    private static final List<Integer> EXPOSED_PORTS_SEEN_BY_MODIFIER = new ArrayList<>();

    /**
     * 把指定容器口的宿主口**钉死**到 {@code hostPort}，并保留其它容器口的既有绑定。
     *
     * <p>⚠️ <b>绑定的替换语义（读消费点得出，不是猜）</b>：
     * {@code HostConfig.withPortBindings(List)} 是<b>整体替换</b>而不是追加
     * ⇒ ⇒ 若只往里加一条固定绑定，默认那条随机绑定会与之并存（同一个容器口两条绑定）⇒ ⇒ 必须先
     * <b>剔除该容器口的旧绑定</b>，再放固定绑定；<b>其它容器口的绑定必须原样保留</b>。
     *
     * <p>⇒ ⇒ 实现在 {@link GoStyleHostPortPinning#pinContainerPort}（本类只负责「记录 modifier 看到的暴露口」
     * 这条<b>探针专用</b>的观测，再委托给落地工具）。
     *
     * @param cmd           Testcontainers 组装好的 create 指令
     * @param hostPort      要钉的宿主口
     * @param containerPort 容器内口
     */
    static void pinHostPort(CreateContainerCmd cmd, int hostPort, int containerPort) {
        EXPOSED_PORTS_SEEN_BY_MODIFIER.clear();
        for (ExposedPort exposed : cmd.getExposedPorts()) {
            EXPOSED_PORTS_SEEN_BY_MODIFIER.add(exposed.getPort());
        }
        GoStyleHostPortPinning.pinContainerPort(cmd, containerPort, hostPort);
    }

    @Test
    @DisplayName("钉口探针：显式绑口后 getMappedPort() 必须返回那个固定口，且落在分配段内且真连得通")
    void pinnedHostPortIsWhatGetMappedPortReturns() throws Exception {
        int hostPort = pickFreeHostPortInSegment();

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("seg_probe")
                .withUsername("rag")
                .withPassword("rag")
                .withCreateContainerCmdModifier(cmd -> pinHostPort(cmd, hostPort, 5432))) {

            postgres.start();

            Integer mapped = postgres.getMappedPort(5432);
            assertEquals(hostPort, mapped,
                    "显式绑口后 getMappedPort() 必须返回那个固定宿主口（钉口不生效 ⇒ 一切免谈）");
            assertTrue(mapped >= SEGMENT_START && mapped <= SEGMENT_END,
                    "宿主口必须落在 G4 分配的段内，实际=" + mapped);
            // ⇒ ⇒ 落地前提：modifier 运行时必须**已经能看到暴露口**（否则「逐口钉」无从下手）
            assertTrue(EXPOSED_PORTS_SEEN_BY_MODIFIER.contains(5432),
                    "modifier 运行时 cmd.getExposedPorts() 必须已填充 5432，实际="
                            + EXPOSED_PORTS_SEEN_BY_MODIFIER);

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                // ⚠️ 断言写法踩过一次坑：**PG 的 JDBC 对 DDL 的 executeUpdate 返回 0**（不返回 1）
                // ⇒ ⇒ 第一次跑这里红的是「我的断言写错了」，不是「端口不通」⇒ ⇒ 改成「建表后能查回来」。
                statement.execute("CREATE TABLE seg_probe_t(id int)");
                try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM seg_probe_t")) {
                    assertTrue(rs.next(), "钉口必须真连得通 —— 建了表就要查得到");
                    assertEquals(0, rs.getInt(1));
                }
                // ⇒ ⇒ 再锁一条：**JDBC URL 必须指向那个固定宿主口**（否则「钉口」只是容器层的记录）
                assertTrue(postgres.getJdbcUrl().contains(":" + hostPort),
                        "JDBC URL 必须指向钉死的宿主口，实际=" + postgres.getJdbcUrl());
            }
        }
    }
}
