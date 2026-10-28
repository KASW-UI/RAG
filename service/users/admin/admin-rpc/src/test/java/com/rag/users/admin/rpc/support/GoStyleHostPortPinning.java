package com.rag.users.admin.rpc.support;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 🟢 **Testcontainers 宿主口钉段** —— 把本模块的容器宿主口**钉到 G4 分配的段内**
 * （users 域 <b>{@code 22700–22729}</b>），<b>挪出 OS 临时段 {@code 49152–65535}</b>。
 *
 * <h2>为什么必须钉</h2>
 * <p>本域台账 §34.4（**已订正**）的根因链：Testcontainers 每次起新容器抢的是
 * <b>OS 临时段 {@code 49152–65535}</b> 里的随机口 ⇒ ⇒ 而本机有<b>手工起的对拍环境容器</b>
 * （<code>tools/difftest/**</code> 与探针脚本，固定名、无 Testcontainers 标签 ⇒ Ryuk 管不到）
 * 长期占着该段里的口 ⇒ ⇒ <b>抢不到口 ⇒ 连到「别的那个」</b> ⇒ ⇒ 症状 =
 * <b>SSL 协商阶段被对端 EOF</b>（<code>EOFException</code> 出现在 <code>enableSSL</code>）。
 * ⇒ ⇒ 🔴 <b>订正后的准确表述</b>：错的只是「<b>谁</b>占的」（不是 Testcontainers 残留，是手工容器），
 * 对的是「<b>随机口落在 OS 临时段 ⇒ 撞口</b>」⇒ ⇒ <b>修法不变：把宿主口挪出该段</b>。
 *
 * <h2>为什么是「段内挑空闲口」而不是「写死一个口」</h2>
 * <ul>
 *   <li><b>写死一个口</b> ⇒ 该口若被上一次跑批的残留容器占住 ⇒ <b>硬失败</b>
 *       ⇒ 而本模块 surefire 设了 <code>TESTCONTAINERS_RYUK_DISABLED=true</code> ⇒ <b>Ryuk 不回收</b>
 *       ⇒ 残留可能跨轮存在 ⇒ ⇒ 写死口 = 把「偶发撞口」换成「确定性撞口」⇒ ⇒ <b>不可取</b>。</li>
 *   <li><b>段内挑空闲口</b> ⇒ 被占就换同段内下一个（<b>自愈</b>）⇒ ⇒ 且同段内仍能并行起多个容器。</li>
 * </ul>
 *
 * <h2>三条机制事实（已实测，见本域台账 §39）</h2>
 * <ol>
 *   <li><code>HostConfig.withPortBindings(List)</code> 是<b>整体替换</b> ⇒ ⇒ 必须「<b>剔除该容器口的旧绑定</b>
 *       ＋ <b>加固定绑定</b>」两步齐做 ⇒ ⇒ 否则同一个容器口会有两条绑定。</li>
 *   <li>modifier 运行时 <code>cmd.getExposedPorts()</code> <b>已经填充</b> ⇒ ⇒ 逐口钉可行。</li>
 *   <li>显式绑口后 <code>getMappedPort()</code> <b>返回那个固定口</b>（L4 真容器实测）⇒ ⇒
 *       <b>调用点零改动</b>：既有测试继续用 <code>getMappedPort()</code> / <code>getJdbcUrl()</code> 即可。</li>
 * </ol>
 *
 * <h2>用法（G4 §4.10.39 ② 规定的落点 = 域侧改自己的测试类 ⇒ 显式绑口）</h2>
 * <pre>
 *   new PostgreSQLContainer&lt;&gt;("postgres:16-alpine")
 *       .withDatabaseName("x").withUsername("rag").withPassword("rag")
 *       .withCreateContainerCmdModifier(cmd -&gt; pinContainerPort(cmd, 5432));   // ← 加这一行
 * </pre>
 */
public final class GoStyleHostPortPinning {

    /** G4 §4.10.39 ② 分配的 users 域宿主口段起点。 */
    public static final int SEGMENT_START = 22700;

    /** G4 §4.10.39 ② 分配的 users 域宿主口段终点。 */
    public static final int SEGMENT_END = 22729;

    /** OS 临时段（随机出向口默认落这里）—— **本段必须与之错开**。 */
    public static final int OS_EPHEMERAL_START = 49152;

    /** OS 临时段终点。 */
    public static final int OS_EPHEMERAL_END = 65535;

    private GoStyleHostPortPinning() {
    }

    /**
     * 在分配段内挑一个<b>当前空闲</b>的宿主口。
     *
     * <p>「占用要可自愈」：被占时<b>换同段内下一个</b>，而不是直接失败。
     *
     * <p>⚠️ <b>残留的竞态</b>（如实记录，不掩饰）：本方法「探端口」与 Docker 真正「绑端口」之间有一个
     * 极小的窗口 ⇒ ⇒ 若同一瞬间<b>另一个 JVM</b> 也挑中了同一个口 ⇒ ⇒ 后绑的那个会
     * <b>硬失败</b>（<code>bind: address already in use</code>）⇒ ⇒ 这是<b>大声失败</b>而不是静默走偏
     * ⇒ ⇒ 且下一轮会自动挑到别的口 ⇒ ⇒ 残留面 = 「两个 JVM 恰好同时挑中同一个空闲口」。
     */
    public static int pickFreeHostPortInSegment() {
        for (int p = SEGMENT_START; p <= SEGMENT_END; p++) {
            try (ServerSocket probe = new ServerSocket(p)) {
                probe.setReuseAddress(false);
                return p;
            } catch (IOException taken) {
                // 该口已被占 ⇒ 换同段内下一个（自愈）
            }
        }
        throw new IllegalStateException("分配段 " + SEGMENT_START + "-" + SEGMENT_END + " 已无空闲宿主口");
    }

    /**
     * 把指定<b>容器口</b>的宿主口<b>钉死</b>到分配段内的一个空闲口，并保留其它容器口的既有绑定。
     *
     * <p>⚠️ <b>只能钉一个容器口</b>：多口容器若只钉一口 ⇒ 另一口会失去绑定 ⇒ 容器启动失败。
     * ⇒ ⇒ 本域的容器都是<b>单口</b>（PG=5432 / Redis=6379）⇒ ⇒ 每次只钉一个。
     *
     * @param cmd          Testcontainers 组装好的 create 指令
     * @param containerPort 容器内口（5432 = PostgreSQL · 6379 = Redis）
     */
    public static void pinContainerPort(CreateContainerCmd cmd, int containerPort) {
        pinContainerPort(cmd, containerPort, pickFreeHostPortInSegment());
    }

    /**
     * 把指定<b>容器口</b>的宿主口<b>钉死</b>到<b>给定的</b>宿主口，并保留其它容器口的既有绑定。
     *
     * <p>与 {@link #pinContainerPort(CreateContainerCmd, int)} 的区别 = <b>宿主口由调用方指定</b>
     * ⇒ ⇒ 守门需要「先挑口、再断言 <code>getMappedPort()</code> 就是那个口」时用这个重载。
     *
     * @param cmd          Testcontainers 组装好的 create 指令
     * @param containerPort 容器内口（5432 = PostgreSQL · 6379 = Redis）
     * @param hostPort     要钉死的宿主口（必须在 {@link #SEGMENT_START}–{@link #SEGMENT_END} 内）
     */
    public static void pinContainerPort(CreateContainerCmd cmd, int containerPort, int hostPort) {
        HostConfig hostConfig = cmd.getHostConfig();
        if (hostConfig == null) {
            hostConfig = new HostConfig();
            cmd.withHostConfig(hostConfig);
        }
        List<PortBinding> merged = new ArrayList<>();
        Ports existing = hostConfig.getPortBindings();
        if (existing != null) {
            for (Map.Entry<ExposedPort, Ports.Binding[]> entry : existing.getBindings().entrySet()) {
                if (entry.getKey().getPort() == containerPort) {
                    // 剔除该容器口的默认随机绑定（否则同一个容器口会有两条绑定）
                    continue;
                }
                for (Ports.Binding binding : entry.getValue()) {
                    merged.add(new PortBinding(binding, entry.getKey()));
                }
            }
        }
        merged.add(new PortBinding(Ports.Binding.bindPort(hostPort), ExposedPort.tcp(containerPort)));
        hostConfig.withPortBindings(merged);
    }
}
