package com.rag.favorite.favorite.rpc.bootstrap;

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
 * favorite-rpc 启动后注册到 Nacos —— 对应 Go 端 {@code service/favorite/rpc/favorite.go:42} 里
 * {@code zrpc.MustNewServer(c.RpcServerConf, …)} 的隐式 etcd 注册。
 *
 * <p>Go {@code etc/favorite.yaml:32-35} 的 {@code Etcd.Key: favorite.rpc}
 * ⇒ 服务发现名统一为 {@code favorite-rpc}（与 {@code etc/favorite.yaml:1} 的
 * {@code Name: favorite.rpc} 一致）。
 *
 * <p>显式手写注册而不依赖 {@code spring-cloud-starter-alibaba-nacos-discovery} 自动注册，
 * 原因与 message-rpc / like-rpc 同款：自动注册依赖 Spring Web，
 * 纯 gRPC 进程强行用会把 web 容器拉起来（本服务只应监听 gRPC 8082）。
 *
 * <p><b>⚠️ Bean 名必须显式指定（DRIFT-MSG-004）</b>：
 * {@code spring-cloud-starter-alibaba-nacos-discovery} 的
 * {@code NacosServiceRegistryAutoConfiguration} <b>也会注册一个名为
 * {@code nacosRegistration} 的 Bean</b>。若本类用裸 {@code @Component}，
 * 默认 Bean 名恰好相同 ⇒ Spring Boot 默认
 * {@code allow-bean-definition-overriding=false} ⇒ <b>上下文直接起不来</b>。
 *
 * <p>⚠️ <b>注册失败不退出进程</b>（与 message-rpc / like-rpc 同款，是刻意的偏离）：
 * Go 的 {@code MustNewServer} 在 Etcd 注册失败时会 panic，但单 JVM 里 gRPC 已经起来了 ——
 * 为注册失败杀掉整个服务，会让「Nacos 抖动 = 全站点收藏不可用」，比 Go 更糟。
 */
@Component("favoriteRpcNacosRegistration")
public class FavoriteNacosRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteNacosRegistration.class);

    /** 对应 Go {@code etc/favorite.yaml:35} {@code Etcd.Key: favorite.rpc}。 */
    private static final String SERVICE_NAME = "favorite-rpc";

    /** Spring Cloud Alibaba Nacos 默认 group。 */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;
    private final String localIp;

    public FavoriteNacosRegistration(NacosServiceManager nacosServiceManager,
                                     GrpcServerProperties grpcServerProperties,
                                     @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
                                     String nacosServerAddr) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
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
     * Spring 启动完成后注册。
     *
     * <p>对应 Go 侧 {@code s.Start()} 后由 zrpc 立即 EtcdRegister 的时序：
     * 监听 {@link ApplicationReadyEvent} 而不是 {@code ContextRefreshedEvent}，
     * 确保 gRPC server 已经真正起来。
     */
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

            nacosServiceManager.getNamingService()
                    .registerInstance(SERVICE_NAME, DEFAULT_GROUP, instance);
            LOG.info("FavoriteNacosRegistration registered: service={} group={} ip={} port={}",
                    SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort());
        } catch (NacosException e) {
            LOG.error("FavoriteNacosRegistration.register failed for {}: {}",
                    SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /** 容器关闭时反注册，防止进程重启残留 stale 节点。 */
    @jakarta.annotation.PreDestroy
    public void deregister() {
        try {
            nacosServiceManager.getNamingService()
                    .deregisterInstance(SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort());
            LOG.info("FavoriteNacosRegistration deregistered: service={} ip={} port={}",
                    SERVICE_NAME, localIp, grpcServerProperties.getPort());
        } catch (NacosException e) {
            LOG.warn("FavoriteNacosRegistration.deregister failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /**
     * 对应 Go 侧 {@code net.InterfaceAddrs()} 取第一个非 loopback IPv4；
     * Java 用 {@link NetworkInterface} 遍历实现。
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
            // 落到 InetAddress.getLocalHost
        }
        return InetAddress.getLocalHost().getHostAddress();
    }
}