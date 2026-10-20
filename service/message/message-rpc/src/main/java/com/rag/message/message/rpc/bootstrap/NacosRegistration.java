package com.rag.message.message.rpc.bootstrap;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * message-rpc 进程启动后注册到 Nacos —— 对应 Go 端
 * {@code service/message/rpc/messageservice.go} 的 etcd 注册行为。
 *
 * <p>{@code NacosServiceManager.registerInstance(serviceName, group, instance)} 替代
 * {@code zrpc.MustNewServer(...).EtcdRegister}：
 * <ul>
 *   <li>{@code Name: message.rpc} → {@code spring.application.name=message-rpc}</li>
 *   <li>{@code Etcd.Key: message.rpc} → {@code serviceName=message-rpc}</li>
 *   <li>{@code ListenOn: 0.0.0.0:8082} → {@code GrpcServerProperties.port=8082}</li>
 * </ul>
 *
 * <p>这里显式实现而不是依赖 spring-cloud-starter-alibaba-nacos-discovery 自动注册，
 * 原因：{@code spring-cloud-starter-alibaba-nacos-discovery} 默认依赖 Spring Web，强行
 * 用在纯 gRPC 服务上会把 web 容器启起来。本服务不带 web，要走纯手动注册路径：
 * <ol>
 *   <li>{@code ApplicationReadyEvent} 后调 {@code register()} —— 跟 Go 端
 *       {@code zrpc.MustNewServer(...).Start()} 后立即注册一致的时序</li>
 *   <li>Spring 关闭时反注册（靠 {@code @PreDestroy} 钩子）</li>
 * </ol>
 *
 * <p>group 一律 {@code DEFAULT_GROUP}，与 message-api / user-api / follow-api 保持一致，
 * 便于服务发现过滤。
 */
@Component
public class NacosRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistration.class);

    /** 对应 Go yaml：Key: message.rpc → 服务发现 / OTEL 都按这个名注册。 */
    private static final String SERVICE_NAME = "message-rpc";

    /** Spring Cloud Alibaba Nacos 默认 group。 */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;

    /** 对应 Go etcd 注册的 { ip, port }。 */
    private final String localIp;

    private final int nacosServerPort;

    /** 当前已注册实例句柄（防止重复注册 / 用于 deregister）。 */
    private volatile Instance registeredInstance;

    /**
     * @param nacosServiceManager Spring Cloud Alibaba Nacos 客户端 Bean
     * @param grpcServerProperties devh grpc server 配置（含 port=8082）
     * @param nacosServerAddr Nacos server 地址（默认 127.0.0.1:8848）
     */
    public NacosRegistration(NacosServiceManager nacosServiceManager,
                             GrpcServerProperties grpcServerProperties,
                             @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}") String nacosServerAddr) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
        // 解析 nacos server 端口：可能是 "host" 或 "host:port"
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

    /**
     * Spring 启动完成后注册到 Nacos。
     *
     * <p>对应 Go 端 {@code s.Start()} 后立刻被 zrpc 调用 EtcdRegister。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(grpcServerProperties.getPort());
            instance.setServiceName(SERVICE_NAME);
            // Nacos 2.x Instance 不含 groupName 字段：group 走 registerInstance(serviceName, groupName, instance) 第 2 个参数。
            instance.setHealthy(true);
            instance.setWeight(1.0);
            instance.setEphemeral(true);

            nacosServiceManager.getNamingService()
                    .registerInstance(SERVICE_NAME, DEFAULT_GROUP, instance);
            this.registeredInstance = instance;
            LOG.info("NacosRegistration registered: service={} group={} ip={} port={}",
                    SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort());
        } catch (NacosException e) {
            LOG.error("NacosRegistration.register failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /**
     * Spring 容器关闭时反注册 —— 防止进程重启残留 stale 节点。
     */
    @jakarta.annotation.PreDestroy
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

    /**
     * 对应 Go 端 resolveLocalIp：优先取第一个非 loopback 网卡的 IPv4。
     *
     * <p>Go 用 {@code net.InterfaceAddrs()}，Java 用 {@link NetworkInterface} 遍历 enumerate。
     */
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
        } catch (java.net.SocketException ignore) {
            // fallthrough 到 InetAddress.getLocalHost
        }
        return InetAddress.getLocalHost().getHostAddress();
    }
}