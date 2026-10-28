package com.rag.message.message.rpc.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 **第二十一轮 · message 域真容器「固定宿主口」机制的守门** —— G4 §4.10.39 ② 明确要求的「**域侧落地前必须先写一条探针验证，不能照抄**」。
 *
 * <h2>🔴 本类钉住的那个未实测项</h2>
 * <p>G4 §4.10.39 ② 的机制可行性格里**明写**：
 * <blockquote>
 * ⚠️ <b>本层未实测的两层</b>：① <code>PostgreSQLContainer</code> <b>不能直接换成</b>
 * <code>FixedHostPortGenericContainer</code>（后者不带 PG 的初始化/JDBC 支持）⇒ ⇒ 正确形态应是
 * <code>PostgreSQLContainer</code> ＋ <code>withCreateContainerCmdModifier(...)</code> 显式绑口；
 * ② <b>「显式绑口后 <code>getMappedPort()</code> 是否返回那个固定口」未实测</b> ⇒ ⇒ ⇒
 * <b>域侧落地时必须先写一条探针验证，不能照抄</b>
 * </blockquote>
 * ⇒ ⇒ **本类 = 那条探针**（且**留在仓里**当守门，不是临时脚本）⇒ ⇒ 断言三条：
 * <ol>
 *   <li><b>① {@code getMappedPort(5432)} 必须返回那个固定口</b>（不是随机口、不是 0）—— 这是 G4 的原问句；</li>
 *   <li><b>② 那个固定宿主口必须真的在监听</b>（从宿主机 TCP 连得上）—— 证明「绑口」不是纸面配置；</li>
 *   <li><b>③ 必须真的能通过那个固定口连上 PG 并查出一条数据</b> —— 证明「那个口上回应我的确实是 PG」
 *       （G4 §4.10.39 ③ 的诊断判据：「<b>PG 连接失败先问那个端口上回应我的到底是不是 PG</b>」）。</li>
 * </ol>
 *
 * <h2>🔴 ③ 为什么要单独测「是不是 PG」</h2>
 * <p>G4 §4.10.39 ③ 采纳的跨 lane 判据：<b>「PG 连接失败」先问「那个端口上回应我的到底是不是 PG」</b>
 * ⇒ ⇒ {@code EOFException} 出现在 {@code enableSSL} ⇒ ⇒ 强烈指向「对端不是我预期的服务」
 * ⇒ ⇒ **不要**直接读成「PG 挂了」⇒ ⇒ 否则会去查一个根本没坏的东西。
 * ⇒ ⇒ 本类因此**显式**做一次真查询（{@code SELECT 1}）⇒ ⇒ 把「对端是 PG」这件事**变成断言**而不是推断。
 */
@Testcontainers
@DisplayName("message-rpc：真容器固定宿主口机制（映射口 == 固定口 · 固定口真在监听 · 固定口上确实是 PG）")
class MessagePostgresFixedHostPortGateTest {

    /**
     * 本守门自己占的固定宿主口（message 域段 `22820–22849` 内的**独占**一个）。
     *
     * <p>⚠️ **为什么用写死的常量、而不是「找一个空闲口」**：
     * ① 「找空闲口」= 回到 Testcontainers 的随机口 ⇒ ⇒ 恰好是本轮要消除的东西；
     * ② 端口必须**可 grep、可复算** ⇒ ⇒ 任何门禁要核「这个口在段内」都必须有一个确定的数。
     */
    static final int FIXED_HOST_PORT = 22769;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_fixed_port_it")
                    .withUsername("rag")
                    .withPassword("rag");

    static {
        RealContainerPort.withFixedHostPort(
                POSTGRES, FIXED_HOST_PORT, RealContainerPort.PG_CONTAINER_PORT);
    }

    @Test
    @DisplayName("① 显式绑口后 getMappedPort(5432) 必须【返回那个固定口】⇒ 不是随机口（G4 §4.10.39 ② 的原问句）")
    void mappedPortIsTheFixedHostPort() {
        int mapped = POSTGRES.getMappedPort(RealContainerPort.PG_CONTAINER_PORT);

        System.out.printf("PROBE fixed-port mapped=%d expected=%d host=%s%n",
                mapped, FIXED_HOST_PORT, POSTGRES.getHost());

        assertThat(mapped)
                .as("🔴 显式绑口后，Testcontainers 的映射口必须就是那个固定宿主口"
                        + " ⇒ ⇒ 若返回别的数 ⇒ 说明「显式绑口」没生效（Testcontainers 仍按随机口走）"
                        + " ⇒ ⇒ 那整个「固定宿主口」方案就是**假生效**的")
                .isEqualTo(FIXED_HOST_PORT);

        assertThat(mapped)
                .as("🔴 固定口必须落在 message 域段内（全局段表 §4.10.19.3：49152–65535 是 OS 临时段，禁占）")
                .isBetween(RealContainerPort.SEGMENT_START, RealContainerPort.SEGMENT_END);
    }

    @Test
    @DisplayName("② 那个固定宿主口必须【真的在监听】⇒ 证明「绑口」不是纸面配置")
    void fixedHostPortIsActuallyListening() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", FIXED_HOST_PORT), 3_000);
            assertThat(socket.isConnected())
                    .as("🔴 固定宿主口 %d 必须真的能连上（不是「映射口对、但那个口上没人」）", FIXED_HOST_PORT)
                    .isTrue();
        } catch (java.io.IOException e) {
            throw new AssertionError("固定宿主口 " + FIXED_HOST_PORT + " 连不上 ⇒ 绑口没生效", e);
        }
    }

    @Test
    @DisplayName("③ 固定口上【确实是一个 PG】⇒ SELECT 1 必须成功 ⇒ 把「对端是 PG」变成断言而不是推断")
    void fixedHostPortReallySpeaksPostgres() {
        String url = "jdbc:postgresql://127.0.0.1:" + FIXED_HOST_PORT + "/" + POSTGRES.getDatabaseName();
        try (Connection conn = DriverManager.getConnection(
                url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            assertThat(rs.next()).as("SELECT 1 必须返回一行").isTrue();
            assertThat(rs.getInt(1)).as("SELECT 1 的值必须是 1").isEqualTo(1);
            System.out.printf("PROBE fixed-port jdbc ok url=%s%n", url);
        } catch (Exception e) {
            throw new AssertionError("固定宿主口 " + FIXED_HOST_PORT
                    + " 上连不上 / 不是 PG ⇒ ⇒ ⚠️ 先问「那个口上回应我的到底是不是 PG」，别直接读成「PG 挂了」", e);
        }
    }
}
