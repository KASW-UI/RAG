package com.rag.article.article.rpc.bootstrap;

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
 * article-rpc 启动后注册到 Nacos —— 对应 Go 端 {@code article.go:44-49}
 * 里 {@code zrpc.MustNewServer(c.RpcServerConf, …)} 的隐式 etcd 注册。
 *
 * <p>Go {@code etc/article.yaml} 的 {@code Name: article.rpc} + {@code Etcd.Key: article.rpc}
 * ⇒ 服务发现名统一为 {@code article-rpc}。
 *
 * <p><b>显式手写注册</b>（不依赖自动注册）的原因与 message-rpc / like-rpc 同款：
 * 自动注册依赖 Spring Web，纯 gRPC 进程强行使用会把 web 容器拉起来。
 *
 * <p><b>⚠️ Bean 名必须显式指定（DRIFT-MSG-004）</b>：
 * {@code spring-cloud-starter-alibaba-nacos-discovery} 的
 * {@code NacosServiceRegistryAutoConfiguration} <b>也会注册一个名为 {@code nacosRegistration} 的 Bean</b>。
 * 若本类用裸 {@code @Component}，默认 Bean 名恰好相同 ⇒ Spring Boot 默认
 * {@code allow-bean-definition-overriding=false} ⇒ <b>上下文直接起不来</b>。
 * message-rpc 踩过这个坑，本类照抄修复后的写法。
 */
@Component("articleRpcNacosRegistration")
public class NacosRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistration.class);

    /** 对应 Go yaml {@code Etcd.Key: article.rpc}。 */
    private static final String SERVICE_NAME = "article-rpc";

    /** Spring Cloud Alibaba Nacos 默认 group。 */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;
    private final String localIp;

    public NacosRegistration(NacosServiceManager nacosServiceManager,
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
     * <p>对应 Go 侧 {@code s.Start()} 之后 zrpc 立即 EtcdRegister 的时序：
     * 监听 {@link ApplicationReadyEvent} 而非 {@code ContextRefreshedEvent}，
     * 确保 gRPC server 已经真正起来。
     *
     * <p>⚠️ <b>注册失败只记日志、不阻止启动</b>（与 like-rpc 同款）：
     * Go 侧 etcd 注册失败会让 {@code MustNewServer} panic；
     * Java 单 JVM 里 gRPC 已经在监听，为一次注册失败杀掉整个进程，
     * 会让「Nacos 抖动 = 全站文章服务不可用」——比 Go 更糟。
     * 这是<b>刻意记录在案的偏离</b>。
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
            LOG.info("NacosRegistration registered: service={} group={} ip={} port={}",
                    SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort());
        } catch (NacosException e) {
            LOG.error("NacosRegistration.register failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /** 容器关闭时反注册，防止进程重启残留 stale 节点。 */
    @jakarta.annotation.PreDestroy
    public void deregister() {
        try {
            nacosServiceManager.getNamingService()
                    .deregisterInstance(SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort());
            LOG.info("NacosRegistration deregistered: service={} ip={} port={}",
                    SERVICE_NAME, localIp, grpcServerProperties.getPort());
        } catch (NacosException e) {
            LOG.warn("NacosRegistration.deregister failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /** 对应 Go 侧取第一个非 loopback IPv4；Java 用 {@link NetworkInterface} 遍历实现。 */
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