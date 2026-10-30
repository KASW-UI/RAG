package com.rag.follow.follow.rpc.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * follow-rpc 真 Tomcat（Actuator 载体）测试的<b>端口选取</b>
 * （测试设施 ⇒ 只影响测试能不能稳定跑到被测代码，<b>不影响任何对外行为</b>）。
 *
 * <h2>为什么需要它</h2>
 * <p>follow-rpc <b>早就有了</b> servlet 载体（{@code spring-boot-starter-web}，
 * DRIFT-FOLLOW-006 · 2026-10-29 落地）⇒ ⇒ 它的 {@code /actuator/prometheus}
 * 是<b>真实可抓的</b>的 ⇒ ⇒ 但此前<b>没有任何守门</b>证明这件事
 * ⇒ ⇒ 「配了但抓不到 / 哪天载体掉了」都<b>没人拦得住</b>。
 * <p>⇒ ⇒ 本类给载体守门提供<b>归属确定</b>的端口。
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
 * <p>⇒ ⇒ 域级守门 {@code BlindPortGateTest}（① 源扫，位于 user-rpc，
 * 扫描面<b>含 follow-rpc</b>）<b>直接判这种写法违规</b>
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
 * <h2>段归属</h2>
 * <p>global 端口段表（{@code docs/DRIFT/global/vs-common.md} §4.10.19.3）把 {@code 22300–22399}
 * 预分配给 <b>follow 域</b> ⇒ ⇒ 本类取其中 {@code 22360–22364}（follow-rpc 的 Tomcat 子段）。
 * <p>⚠️ <b>与本模块 gRPC 子段、以及 follow-api 的子段都互不重叠</b>：
 * 本模块 {@code RealGrpcPort} 占 {@code 22320–22339}；follow-api 的 {@code RealTomcatPort}
 * 占 {@code 22340–22359} ⇒ ⇒ 本段从 {@code 22360} 起 ⇒ ⇒ 域级守门
 * {@code BlindPortGateTest} ② 的「子段互不重叠」那条会核到。
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b> ⇒ <b>不存在语义对照义务</b>。
 *
 * <h2>惯例来源</h2>
 * <p>users 域两个 rpc 的同名类（{@code service/users/user/user-rpc/src/test/.../support/RealTomcatPort.java}
 * 段 {@code 22290–22294} · {@code service/users/admin/admin-rpc/src/test/.../support/RealTomcatPort.java}
 * 段 {@code 22295–22299}）⇒ ⇒ 本类沿用其形状 ⇒ 域内形状一致。
 */
public final class RealTomcatPort {

    /** 本模块真 Tomcat 测试专用端口段下界（含）。 */
    public static final int FIRST = 22360;

    /** 本模块真 Tomcat 测试专用端口段上界（含）。 */
    public static final int LAST = 22364;

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
