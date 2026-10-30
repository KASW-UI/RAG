package com.rag.users.admin.rpc.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * admin-rpc 真 Tomcat（Actuator 载体）测试的<b>端口选取</b>（测试设施 ⇒ 只影响测试能不能稳定跑到被测代码，<b>不影响任何对外行为</b>）。
 *
 * <h2>为什么不能用 Spring 的 {@code RANDOM_PORT}</h2>
 * <p>{@code RANDOM_PORT} = 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑一个
 * ⇒ ⇒ <b>Docker 随机发布容器端口用的就是同一段</b>
 * ⇒ ⇒ 本机是<b>多会话并行</b>的机器 ⇒ ⇒ 盲选口可能<b>正好落在别的容器已发布的口号上</b>。
 *
 * <p>⚠️ <b>危害形态是「静默被抢」，不是「起不来」</b>（2026-10-28 本机实测，非推理）：
 * 邻居占着<b>更具体的地址</b> {@code 127.0.0.1:P} ⇒ macOS/BSD 上 Tomcat 的<b>通配绑定照样成功</b>
 * ⇒ ⇒ 启动日志照样打「started on port P」⇒ 但客户端连 {@code 127.0.0.1:P} 会<b>命中更具体的那个</b>
 * ⇒ ⇒ 请求打到了邻居 ⇒ 对端协议不匹配
 * ⇒ ⇒ <b>看起来像「被测代码炸了」，其实压根没被请求到</b>。
 *
 * <p>⇒ ⇒ 域级守门 {@code BlindPortGateTest}（① 源扫）<b>直接判这种写法违规</b>
 * ⇒ ⇒ 本类是本模块的唯一合法出口之一。
 *
 * <h2>本类怎么消除它</h2>
 * <ol>
 *   <li><b>自己挑端口</b>：从<b>本域专用端口段</b>（{@link #FIRST}–{@link #LAST}）里挑，
 *       <b>避开 OS 临时端口段</b> ⇒ ⇒ 与 Docker 随机发布端口<b>不同段</b>；</li>
 *   <li><b>挑之前先在两个地址上 bind 自检</b>：{@code 0.0.0.0:P} 与 {@code 127.0.0.1:P}
 *       都可绑才算空闲（macOS 上二者<b>可以共存</b>，都带 {@code SO_REUSEADDR}
 *       ⇒ 只探一个会漏）⇒ ⇒ 交出去的是<b>确定归我们自己</b>的端口。</li>
 * </ol>
 *
 * <h2>怎么用</h2>
 * <p>{@code @SpringBootTest(webEnvironment = DEFINED_PORT)} ＋
 * {@code @DynamicPropertySource} 里 {@code registry.add("server.port", RealTomcatPort::pick)}
 * ⇒ ⇒ 测试不再让 OS 盲选 ⇒ 端口归属确定。
 *
 * <h2>段归属</h2>
 * <p>global 端口段表（{@code docs/DRIFT/global/vs-common.md} §4.10.19.3）把 {@code 22200–22299}
 * 预分配给 <b>users 域</b> ⇒ ⇒ 本类取其中 {@code 22295–22299}（admin-rpc 子段）。
 * <p>⚠️ **本子段是从「守门脚手架段」切出来的**：原脚手架段 = {@code 22280–22289}
 * （{@code BlindPortGateTest} ⑤ 的临时占口用）⇒ ⇒ 本段占用 {@code 22295–22299}
 * ⇒ ⇒ 脚手架段**实际可用区间收窄为 {@code 22280–22289}**。
 * ⇒ ⇒ 该收窄**属共享层段表**（G4 写权面）⇒ ⇒ **已登记并转派**，本类只按已分配号取，
 * <b>不改共享层台账</b>。
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b> ⇒ <b>不存在语义对照义务</b>。
 *
 * <h2>惯例来源</h2>
 * <p>user-api 的同名类（{@code service/users/user/user-api/src/test/.../support/RealTomcatPort.java}，
 * 段 {@code 22210–22219}）⇒ ⇒ 本类沿用其形状 ⇒ 域内形状一致。
 */
public final class RealTomcatPort {

    /** 本模块真 Tomcat 测试专用端口段下界（含）。 */
    public static final int FIRST = 22295;

    /** 本模块真 Tomcat 测试专用端口段上界（含）。 */
    public static final int LAST = 22299;

    /** 单次 bind 自检的超时（bind 是本地的，给 1s 足够）。 */
    private static final int BIND_TIMEOUT_MS = 1000;

    private RealTomcatPort() {
    }

    /**
     * 挑一个<b>确实可绑</b>的端口。
     *
     * @return 端口号（一定在 {@link #FIRST}–{@link #LAST} 内）
     * @throws IllegalStateException 整段都被占满时
     */
    public static int pick() {
        for (int port = FIRST; port <= LAST; port++) {
            if (isFree(port)) {
                return port;
            }
        }
        throw new IllegalStateException(
                "真 Tomcat 端口段 " + FIRST + "-" + LAST + " 已被占满"
                        + "（本机并行会话太多？）⇒ 换一段端口再试");
    }

    /**
     * 该端口能不能<b>同时</b>满足两件事：{@code 0.0.0.0:P} 可绑 + {@code 127.0.0.1:P} 可绑。
     *
     * <p>📌 <b>包级可见</b>：域级守门测试直接调它断言「被占 ⇒ 判定为不可用」。
     *
     * @param port 端口号
     * @return {@code true} = 两个地址都能绑（= 没有第三方持有者）
     */
    static boolean isFree(int port) {
        try (ServerSocket wildcard = new ServerSocket();
             ServerSocket specific = new ServerSocket()) {
            wildcard.setReuseAddress(true);
            wildcard.bind(new InetSocketAddress(port), 1);
            specific.setReuseAddress(true);
            specific.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
