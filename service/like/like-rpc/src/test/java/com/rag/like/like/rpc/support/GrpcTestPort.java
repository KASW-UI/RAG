package com.rag.like.like.rpc.support;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * 🔴 **rpc 测试的 gRPC server 端口分配器**（第二十四轮 · 修 {@code BindException: Address already in use}）
 *
 * <h2>修的是什么</h2>
 * 本仓所有 rpc 测试都写 {@code "grpc.server.port=0"} ⇒ ⇒ 监听口由
 * <b>操作系统临时段</b>（macOS 实测 {@code net.inet.ip.portrange} = {@code 49152–65535}）里挑一个。
 * <p>
 * 而<b>本机 Docker Desktop 发布的端口就落在同一段</b>（实测 {@code docker ps}：
 * {@code Sea-kafka} 发布 {@code 0.0.0.0:49992->9093}、{@code ddl-*-pg} 发布 {@code 55560–55569} 等）
 * ⇒ ⇒ 两边在<b>同一个端口池</b>里抢位。
 *
 * <h2>现象（第二十四轮实测 · 收口回归 BUILD FAILURE）</h2>
 * {@code mvn -o -B -am -pl <9 模块> clean test} ⇒ {@code message-rpc} 的
 * {@code MessageIsReadLambdaWiringTest} <b>3 条用例全 error</b>：
 * <pre>
 * Failed to load ApplicationContext
 *   → Failed to start bean 'shadedNettyGrpcServerLifecycle'
 *   → java.net.BindException: Address already in use   （/0.0.0.0:49992）
 * </pre>
 * ⇒ <b>该 fork 里的第一个 gRPC server 监听就撞上了 Docker 发布口</b> ⇒ ⇒ 整批 context 起不来
 * ⇒ ⇒ 表现为「3 条用例 error」，看起来像被测行为坏了，<b>其实是被测环境起不来</b>。
 *
 * <h2>为什么不能只靠「重跑一次」</h2>
 * 撞口是<b>概率事件</b>（临时段里被 Docker 占着的那几十个口）⇒ ⇒ 收口口径必须<b>可复跑</b>
 * ⇒ ⇒ 端口必须由本类<b>探测</b>出来，而不是交给操作系统盲选。
 *
 * <h2>为什么用「探测 + 二次确认」而不是「固定口」</h2>
 * <ul>
 *   <li><b>固定口不可行</b>：G4 的波末全量（52 模块）与各域收口回归<b>会并发</b>跑同一批 rpc 模块
 *       ⇒ ⇒ 写死端口会让<b>两棵树互相撞口</b>（这正是本仓「固定口」约定只用于
 *       <b>Testcontainers 宿主口</b>、且按域分子段的原因）。</li>
 *   <li><b>随机选口不可行</b>：{@code ServerSocket(0)} 选出来的口<b>可能正是 Docker 发布口</b>
 *       ⇒ ⇒ 必须<b>显式再绑一次做确认</b>：绑不上 ⇒ 说明这个口此刻被占 ⇒ 换一个。</li>
 * </ul>
 *
 * <h2>本类的语义</h2>
 * 返回一个<b>此刻真的绑得上</b>的端口（{@code ServerSocket} 显式 bind 验证过）⇒ ⇒
 * gRPC server 随后绑定它时不会撞口。
 * <p>
 * ⚠️ 残留窗口：探测关闭到 gRPC 真正 bind 之间存在极小的 TOCTOU 窗口 ⇒
 * 理论上有被别人抢走的可能 ⇒ 但该窗口远小于「操作系统盲选撞 Docker 口」的概率。
 *
 * <h2>关联的守门</h2>
 * 各域的 {@code *RpcArtifactClasspathGateTest} / 段位守门见同目录其它类；
 * 本类本身是<b>被测环境的一部分</b>，不是断言 ⇒ ⇒ 它<b>不引入新的断言口径</b>。
 */
public final class GrpcTestPort {

    /** 探测次数上限（撞口是概率事件 ⇒ 给足重试）。 */
    private static final int MAX_ATTEMPTS = 64;

    private GrpcTestPort() {
    }

    /**
     * 挑一个<b>此刻绑得上</b>的空闲端口。
     *
     * @return 可直接写进 {@code grpc.server.port} 的端口号
     * @throws IllegalStateException 连续 {@value #MAX_ATTEMPTS} 次都没挑到可绑端口
     */
    public static int freePort() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                port = socket.getLocalPort();
            } catch (IOException e) {
                // 连「要一个临时口」都失败 ⇒ 继续重试
                continue;
            }
            // 🔴 二次确认：显式把这个口绑一次。
            //   ServerSocket(0) 选出来的口**可能正是 Docker 发布口**
            //   （临时段与 Docker published 段同池）⇒ ⇒ 绑不上就换一个。
            //   ⚠️ 上面那个 socket 必须已经关闭（try-with-resources 已关）⇒ 否则这里必然绑不上。
            try (ServerSocket probe = new ServerSocket()) {
                probe.setReuseAddress(true);
                probe.bind(new InetSocketAddress(port));
            } catch (BindException e) {
                // 这个口此刻被别人占着（典型：Docker 发布的口）⇒ 换下一个
                continue;
            } catch (IOException e) {
                continue;
            }
            return port;
        }
        throw new IllegalStateException(
                "连续 " + MAX_ATTEMPTS + " 次都没能挑到一个绑得上的空闲端口 ⇒ 临时端口池可能已耗尽");
    }
}
