package com.rag.hot.hot.rpc.bootstrap;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 进程启动后注册到 Nacos —— 替代 Go {@code zrpc.MustNewServer(...)} 的 etcd 注册
 * （Go {@code etc/hot.yaml} 的 {@code Etcd.Key: hot.rpc}，由 zrpc 在 {@code Start()} 时注册）。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code Name: hot.rpc} / {@code Etcd.Key: hot.rpc} → {@code SERVICE_NAME = "hot-rpc"}</li>
 *   <li>{@code ListenOn: 0.0.0.0:8083} → {@code grpc.server.port}（devh 的 {@link GrpcServerProperties}）</li>
 * </ul>
 *
 * <p><b>为什么手动注册而不是 nacos-discovery 自动注册</b>：
 * {@code spring-cloud-starter-alibaba-nacos-discovery} 默认依赖 Spring Web，
 * 用在纯 gRPC 进程上会把 web 容器启起来。本服务<b>不带 web</b> ⇒ 走纯手动路径。
 *
 * <p>⚠️ <b>Bean 名必须显式指定（DRIFT-MSG-004 同款陷阱）</b>：
 * {@code NacosServiceRegistryAutoConfiguration} 也会注册一个名为 {@code nacosRegistration} 的 Bean。
 * 若用裸 {@code @Component}，默认 Bean 名恰好撞名 ⇒ Spring Boot 默认
 * {@code allow-bean-definition-overriding=false} ⇒ <b>上下文直接起不来</b>。
 *
 * <h2>注册失败 ⇒ 健康检查报 NOT_SERVING</h2>
 * <p>Go 侧注册 etcd 失败会 <b>{@code panic} 退出进程</b>（{@code zrpc/server.go:88-93}）
 * ⇒ 探针连不上。RAG 侧刻意<b>不崩进程</b>（用户 2026-10-24 拍板），
 * 但必须让<b>探针能看到失败</b>，否则就成了「注册不上却一切正常」的假活。
 * ⇒ 走 grpc 标准 health service（与 Go 同构，详见 {@link #markHealth} 的论证）。
 */
@Component("hotRpcNacosRegistration")
public class NacosRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistration.class);

    /** 对应 Go yaml {@code Etcd.Key: hot.rpc}。 */
    private static final String SERVICE_NAME = "hot-rpc";

    /** 与 message-rpc / user-rpc 保持一致，便于服务发现过滤。 */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;
    private final String localIp;
    private final int nacosServerPort;

    /**
     * gRPC 标准健康检查的状态管理器（由 devh starter 的
     * {@code GrpcHealthServiceAutoConfiguration} 提供，条件是
     * {@code grpc.server.health-service-enabled}，<b>默认 true</b>）。
     *
     * <p>用 {@link ObjectProvider} 而不是直接注入：若有人把这个开关关掉，bean 就不存在 ——
     * 直接注入会让<b>上下文根本起不来</b>（把一个可选开关变成硬依赖）；
     * 用 Provider 则降级为「注册失败无法被探针观测到」，并打 ERROR 留痕。
     */
    private final ObjectProvider<HealthStatusManager> healthStatusManagerProvider;

    /** 本进程的服务注册是否成功（测试观测用；也对应 Go 侧「进程还活着」这个事实）。 */
    private volatile boolean registrationHealthy;

    private volatile Instance registeredInstance;

    public NacosRegistration(NacosServiceManager nacosServiceManager,
                             GrpcServerProperties grpcServerProperties,
                             ObjectProvider<HealthStatusManager> healthStatusManagerProvider,
                             @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
                             String nacosServerAddr) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
        this.healthStatusManagerProvider = healthStatusManagerProvider;
        String[] parts = nacosServerAddr.split(":");
        this.nacosServerPort = parts.length > 1 ? Integer.parseInt(parts[1]) : 8848;
        String ip;
        try {
            ip = resolveLocalIp();
        } catch (UnknownHostException e) {
            ip = InetAddress.getLoopbackAddress().getHostAddress();
            LOG.warn("resolveLocalIp failed, fallback to loopback: {}", ip);
        }
        this.localIp = ip;
    }

    /** 对应 Go {@code zrpc} 在 {@code Start()} 后立刻注册。 */
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(grpcServerProperties.getPort());
            instance.setServiceName(SERVICE_NAME);
            instance.setHealthy(true);
            instance.setWeight(1.0);
            instance.setEphemeral(true);

            nacosServiceManager.getNamingService().registerInstance(SERVICE_NAME, DEFAULT_GROUP, instance);
            this.registeredInstance = instance;
            markHealth(ServingStatus.SERVING);
            LOG.info("NacosRegistration registered: service={} group={} ip={} port={} nacosPort={}",
                    SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort(), nacosServerPort);
        } catch (NacosException | RuntimeException e) {
            // ⚠️ catch RuntimeException 是**有出处的**，不是图省事：
            //   Go 侧 registerEtcd() 返回的是 error —— **任何**注册错误都导致 panic 退出，
            //   不只是「etcd 连不上」这一种（字节码见 zrpc/internal/rpcpubserver.go:48-53）。
            //   ⇒ RAG 侧对应地必须是「任何注册失败 ⇒ NOT_SERVING」，否则漏掉一类失败就变成假活。
            //   只 catch NacosException 会漏掉 NPE 这类运行时失败（曾实测：mock 未打桩时
            //   getNamingService() 返回 null ⇒ NPE 逃出监听器 ⇒ 启动日志出现未处理异常）。
            LOG.error("NacosRegistration.register failed for {}: {}", SERVICE_NAME, e.getMessage(), e);
            markHealth(ServingStatus.NOT_SERVING);
        }
    }

    /**
     * 把「服务注册是否成功」写进 <b>gRPC 标准健康检查</b>。
     *
     * <p><b>Go 出处</b>（逐条读到消费点）：
     * <ul>
     *   <li>{@code zrpc/internal/server.go:36-38} —— {@code h = health.NewServer()}
     *       （grpc-go 的标准 health server）；</li>
     *   <li>{@code zrpc/internal/rpcserver.go:61-63} ——
     *       {@code grpc_health_v1.RegisterHealthServer(server, s.health)} + {@code s.health.Resume()}。</li>
     *   <li>{@code zrpc/server.go:88-93} —— 注册失败时 {@code panic(err)} ⇒ <b>进程退出</b>
     *       ⇒ 探针连不上 ⇒ 探针失败。</li>
     * </ul>
     *
     * <p><b>语义等价论证</b>：两侧用的都是 <b>grpc 标准 health service</b>
     * （Go = {@code google.golang.org/grpc/health.Server}；
     * RAG = {@code io.grpc.protobuf.services.HealthStatusManager}，由 devh starter 的
     * {@code GrpcHealthServiceAutoConfiguration} 提供），
     * 且都<b>只登记整体状态</b>（service 名 = {@code ""}）：
     * <ul>
     *   <li>Go 侧 {@code health.NewServer()} 只在构造时放入 {@code statusMap[""] = SERVING}，
     *       {@code Resume()} 也只改这一条 ⇒ 查任何<b>具体</b>服务名一律 {@code NOT_FOUND}；</li>
     *   <li>Java 侧 {@code HealthServiceImpl} 的构造同样是 {@code statusMap[""] = SERVING}，
     *       且 {@code check} 是<b>精确匹配</b>（查不到 ⇒ {@code NOT_FOUND}，<b>不回落</b>到整体状态）。</li>
     * </ul>
     * ⇒ 本方法<b>只</b>设置 {@code SERVICE_NAME_ALL_SERVICES}（{@code ""}），
     * <b>刻意不</b>额外登记 {@code "hot-rpc"} 这个具体名 —— 否则会造出 Go 侧没有的
     * 「查具体服务名能拿到状态」这一行为。
     *
     * <p><b>已知差异（有意偏离，已签字 SD-HOT-RPC-004）</b>：
     * Go 的失败表现是<b>进程退出</b>（连接都断），RAG 是<b>进程存活但健康检查报 NOT_SERVING</b>
     * （连接仍在、gRPC 方法仍可调）。
     * ⇒ <b>可观察结果一致</b>（探针都失败），<b>机制不同</b>（连接失败 vs 状态失败）。
     * 这条差异是用户 2026-10-24 拍板的：<b>保持进程存活 + 补健康检查</b>，
     * 理由是「崩进程」不是想要的语义，而「注册不上却探针全绿」才是真问题。
     */
    private void markHealth(ServingStatus status) {
        this.registrationHealthy = status == ServingStatus.SERVING;
        HealthStatusManager manager = healthStatusManagerProvider.getIfAvailable();
        if (manager == null) {
            LOG.error("HealthStatusManager bean 缺失 ⇒ 注册状态无法被 gRPC 探针观测到"
                    + "（grpc.server.health-service-enabled 被关掉了？）");
            return;
        }
        manager.setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, status);
    }

    /** 本进程的服务注册是否成功（测试观测用）。 */
    boolean registrationHealthy() {
        return registrationHealthy;
    }

    /** 进程退出时反注册，避免残留 stale 节点。 */
    @PreDestroy
    public void deregister() {
        if (registeredInstance == null) {
            return;
        }
        try {
            nacosServiceManager.getNamingService()
                    .deregisterInstance(SERVICE_NAME, DEFAULT_GROUP,
                            registeredInstance.getIp(), registeredInstance.getPort());
            LOG.info("NacosRegistration deregistered: service={} ip={} port={}",
                    SERVICE_NAME, registeredInstance.getIp(), registeredInstance.getPort());
        } catch (NacosException e) {
            LOG.warn("NacosRegistration.deregister failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /** 对应 Go 的 {@code net.InterfaceAddrs()}：取第一个非 loopback 的 IPv4。 */
    private static String resolveLocalIp() throws UnknownHostException {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                while (ifaces.hasMoreElements()) {
                    NetworkInterface iface = ifaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp() || iface.isPointToPoint()) {
                        continue;
                    }
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException ignore) {
            // 落到 InetAddress.getLocalHost()
        }
        return InetAddress.getLocalHost().getHostAddress();
    }
}