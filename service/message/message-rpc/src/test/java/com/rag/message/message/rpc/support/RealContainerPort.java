package com.rag.message.message.rpc.support;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.GenericContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 🔴 **第二十一轮 · message 域的真容器宿主口段（固定口）** —— 全局段表 §4.10.19.3 / §4.10.39.2 分配的 **message 域子段**。
 *
 * <h2>为什么需要它（G4 §4.10.39 ① 的根因订正）</h2>
 * <p>Testcontainers 的**默认随机宿主口**落在 **OS 临时段 `49152–65535`**
 * （`sysctl net.inet.ip.portrange` 实测 `first=49152 last=65535` = Docker published 段）
 * ⇒ ⇒ 与 `tools/difftest/ddl-parity.sh` 的固定口 `55560–55569` **同属一个池子**
 * ⇒ ⇒ **真容器测试不得继续用随机口**（§4.10.19.3 段表把 `49152–65535` 标为**禁占**）。
 * <p>⇒ 全局段表把 `22700–22999` 划为「Testcontainers 宿主口段」，**按域分子段**：
 * <b>message 域 = `22760–22789`</b>（message `22760–22789` · like `22790–22819`）。
 *
 * <h2>🔴 形态（不是「把 PostgreSQLContainer 换成 FixedHostPortGenericContainer」）</h2>
 * <p>G4 §4.10.39 ② 已核实的边界①：**`PostgreSQLContainer` 不能直接换成 `FixedHostPortGenericContainer`**
 * （后者不带 PG 的初始化 / JDBC 支持）⇒ ⇒ 正确形态 = **原容器 ＋ `withCreateContainerCmdModifier(...)` 显式绑口**。
 * <p>⚠️ 而 G4 同格里**明确留了一个未实测项**：「**显式绑口后 `getMappedPort()` 是否返回那个固定口**」
 * ⇒ ⇒ **该问句由 `MessagePostgresFixedHostPortGateTest` 实测钉住**（实测结论：<b>会</b>返回那个固定口）⇒ ⇒ **不靠假设**。
 *
 * <h2>🔴 为什么端口必须「逐类写死」，不能用轮询分配</h2>
 * <p>Testcontainers 的 surefire **每个 fork 是一个独立 JVM** ⇒ ⇒ 任何「静态计数器轮询取端口」的写法
 * ⇒ **每个 fork 都从同一处重新开始** ⇒ ⇒ 两个并发 fork 会拿到**同一个端口** ⇒ ⇒ 撞车。
 * ⇒ ⇒ 所以端口是**逐个测试类的常量**（各测试类里的 `FIXED_HOST_PORT`）⇒ ⇒ 可 grep、可复算、无隐式状态。
 *
 * <h2>🔴 绑口的具体做法（先摘后加）</h2>
 * <p>Testcontainers 默认给容器端口绑一个**随机宿主口**；本方法把那条绑定**摘掉**、换成**固定宿主口**
 * ⇒ ⇒ 必须「先摘后加」⇒ ⇒ 只加不摘 ⇒ Docker 会同时看到两条同容器端口的绑定 ⇒ ⇒ 行为不确定。
 */
public final class RealContainerPort {

    /** message 域子段下界（全局段表 §4.10.19.3 / §4.10.39.2 分配）。 */
    public static final int SEGMENT_START = 22760;

    /** message 域子段上界。 */
    public static final int SEGMENT_END = 22789;

    /** PG 的容器端口。 */
    public static final int PG_CONTAINER_PORT = 5432;

    /** Redis 的容器端口。 */
    public static final int REDIS_CONTAINER_PORT = 6379;

    private RealContainerPort() {
    }

    /** 该宿主口是否落在本域段内（守门用它判「有没有跑到 OS 临时段去」）。 */
    public static boolean inSegment(int hostPort) {
        return hostPort >= SEGMENT_START && hostPort <= SEGMENT_END;
    }

    /**
     * 把容器钉到指定的**宿主口**。
     *
     * @param container     任何 Testcontainers 容器（PG / Redis 都行）
     * @param hostPort      宿主口（**必须**落在本域段内）
     * @param containerPort 容器内端口（PG 5432 / Redis 6379）
     */
    public static void withFixedHostPort(
            GenericContainer<?> container, int hostPort, int containerPort) {
        if (!inSegment(hostPort)) {
            throw new IllegalArgumentException(
                    "宿主口 " + hostPort + " **不在 message 域段 " + SEGMENT_START + "–" + SEGMENT_END
                            + " 内** ⇒ ⇒ 拒绝落码（全局段表 §4.10.19.3：49152–65535 是 OS 临时段，禁占）");
        }
        container.withCreateContainerCmdModifier(cmd -> {
            HostConfig hc = cmd.getHostConfig();
            List<PortBinding> bindings = new ArrayList<>();
            if (hc != null && hc.getPortBindings() != null) {
                for (Map.Entry<ExposedPort, Ports.Binding[]> e : hc.getPortBindings().getBindings().entrySet()) {
                    for (Ports.Binding b : e.getValue()) {
                        bindings.add(new PortBinding(b, e.getKey()));
                    }
                }
            }
            // 🔴 先摘掉容器端口的**随机**宿主口绑定（只加不摘 ⇒ Docker 会看到两条同容器端口的绑定）
            bindings.removeIf(pb -> pb.getExposedPort() != null
                    && pb.getExposedPort().getPort() == containerPort);
            // 再绑到固定宿主口
            bindings.add(new PortBinding(
                    Ports.Binding.bindPort(hostPort), ExposedPort.tcp(containerPort)));
            hc.withPortBindings(bindings);
        });
    }
}
