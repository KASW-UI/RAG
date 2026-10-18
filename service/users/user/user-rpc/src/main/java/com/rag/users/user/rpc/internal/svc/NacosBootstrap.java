package com.rag.users.user.rpc.internal.svc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.cloud.nacos.NacosServiceManager;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * 显式注册 user-rpc 实例到 Nacos。
 *
 * <p>DRIFT-NEW-011 根因：user-rpc 是纯 gRPC 进程（无 Tomcat/Jetty Web Server），
 * Spring Cloud Commons 的 {@code NacosAutoServiceRegistration} 默认监听
 * {@code WebServerInitializedEvent} 触发自动注册 —— user-rpc 没发这个 event，
 * 所以启动时 Nacos client 装配完但不调 register，本地 API 调
 * {@code /v1/ns/instance/list} 永远返回 hosts=[]。
 *
 * <p>1:1 语义对齐 Go 端：Go user-rpc 启动时主动调
 * {@code NamingService.RegisterInstance(serviceName, groupName, instance)}
 * （不是依赖 WebServerInitializedEvent）。本类用同样路径：
 * {@link NacosServiceManager#getNamingService()} 拿到 {@link NamingService}，
 * 显式 {@code registerInstance(serviceName, groupName, instance)}。
 *
 * <p>与 admin-rpc 的 NacosBootstrap 行为完全一致（serviceName / port 之外无差异），
 *     本类是 user-rpc 端同款实现。
 */
@Component
public class NacosBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(NacosBootstrap.class);

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;

    private Instance registeredInstance;
    private String registeredServiceName;
    private String registeredGroupName;

    public NacosBootstrap(NacosServiceManager nacosServiceManager,
                          GrpcServerProperties grpcServerProperties) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        String serviceName = "user-rpc";
        String groupName = "USER_GROUP";
        int port = grpcServerProperties.getPort();

        try {
            Instance instance = new Instance();
            instance.setIp(resolveLocalIp());
            instance.setPort(port);
            instance.setHealthy(true);
            instance.setWeight(1.0);
            instance.setEphemeral(true);
            instance.setServiceName(serviceName);
            instance.setClusterName("DEFAULT");

            NamingService namingService = nacosServiceManager.getNamingService();
            namingService.registerInstance(serviceName, groupName, instance);

            this.registeredInstance = instance;
            this.registeredServiceName = serviceName;
            this.registeredGroupName = groupName;

            LOG.info("nacos registry, {} {} {}:{} register finished",
                    groupName, serviceName, instance.getIp(), port);
        } catch (NacosException | UnknownHostException e) {
            LOG.error("user-rpc nacos register FAILED service={} group={} port={}",
                    serviceName, groupName, port, e);
        }
    }

    @PreDestroy
    public void deregister() {
        if (registeredInstance == null) {
            return;
        }
        try {
            NamingService namingService = nacosServiceManager.getNamingService();
            namingService.deregisterInstance(
                    registeredServiceName, registeredGroupName, registeredInstance);
            LOG.info("nacos registry, {} {} deregister finished",
                    registeredGroupName, registeredServiceName);
        } catch (NacosException e) {
            LOG.warn("user-rpc nacos deregister FAILED", e);
        }
    }

    private static String resolveLocalIp() throws UnknownHostException {
        // DRIFT-NEW-012：InetAddress.getLocalHost() 在 macOS 上经常返回 127.0.0.1（loopback），
        // docker 容器跨 host 网络访问不到 host loopback。要走非 loopback 的 IPv4（en0/en1 NIC）。
        InetAddress candidate = null;
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni.isLoopback() || ni.isPointToPoint() || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        if (candidate == null) candidate = a;
                    }
                }
            }
        } catch (SocketException ignore) {
            // 退回 InetAddress.getLocalHost
        }
        return candidate != null ? candidate.getHostAddress() : InetAddress.getLocalHost().getHostAddress();
    }
}
