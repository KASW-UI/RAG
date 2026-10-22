package com.rag.hot.hot.rpc.bootstrap;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private volatile Instance registeredInstance;

    public NacosRegistration(NacosServiceManager nacosServiceManager,
                             GrpcServerProperties grpcServerProperties,
                             @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
                             String nacosServerAddr) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
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
            LOG.info("NacosRegistration registered: service={} group={} ip={} port={} nacosPort={}",
                    SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort(), nacosServerPort);
        } catch (NacosException e) {
            LOG.error("NacosRegistration.register failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
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