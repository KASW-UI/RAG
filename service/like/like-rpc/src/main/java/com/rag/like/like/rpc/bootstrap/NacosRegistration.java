package com.rag.like.like.rpc.bootstrap;

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
 * like-rpc 启动后注册到 Nacos —— 对应 Go 端 {@code service/like/rpc/like.go} 里
 * {@code zrpc.MustNewServer(c.RpcServerConf, …)} 的隐式 etcd 注册。
 *
 * <p>Go {@code etc/like.yaml} 的 {@code Name: like.rpc} + {@code Etcd.Key: like.rpc}
 * ⇒ 服务发现名统一为 {@code like-rpc}。
 *
 * <p>显式手写注册而不依赖 {@code spring-cloud-starter-alibaba-nacos-discovery} 自动注册，
 * 原因与 message-rpc 同款：自动注册依赖 Spring Web，纯 gRPC 进程强行用会把 web 容器拉起来。
 *
 * <p><b>⚠️ Bean 名必须显式指定（DRIFT-MSG-004）</b>：
 * {@code spring-cloud-starter-alibaba-nacos-discovery} 的
 * {@code NacosServiceRegistryAutoConfiguration} <b>也会注册一个名为 {@code nacosRegistration} 的 Bean</b>。
 * 若本类用裸 {@code @Component}，默认 Bean 名恰好相同 ⇒ Spring Boot 默认
 * {@code allow-bean-definition-overriding=false} ⇒ <b>上下文直接起不来</b>。
 */
@Component("likeRpcNacosRegistration")
public class NacosRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistration.class);

    /** 对应 Go yaml {@code Etcd.Key: like.rpc} ⇒ 服务发现 / OTEL 都按这个名注册。 */
    private static final String SERVICE_NAME = "like-rpc";

    /** Spring Cloud Alibaba Nacos 默认 group。 */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;

    /** 对应 Go etcd 注册的 { ip, port }。 */
    private final String localIp;

    public NacosRegistration(NacosServiceManager nacosServiceManager,
                             GrpcServerProperties grpcServerProperties,
                             @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}") String nacosServerAddr) {
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
            // Nacos 2.x Instance 不含 groupName 字段：group 走 registerInstance 的第 2 个参数
            instance.setHealthy(true);
            instance.setWeight(1.0);
            instance.setEphemeral(true);

            nacosServiceManager.getNamingService()
                    .registerInstance(SERVICE_NAME, DEFAULT_GROUP, instance);
            LOG.info("NacosRegistration registered: service={} group={} ip={} port={}",
                    SERVICE_NAME, DEFAULT_GROUP, localIp, grpcServerProperties.getPort());
        } catch (NacosException e) {
            // ⚠️ 2026-10-23 修正错误断言 H：**结论对、机制说错了**。
            //  Go 侧服务注册确实会让**进程退出**，但走的不是 panic：
            //    like.go:34                      s := zrpc.MustNewServer(c.RpcServerConf, …)
            //    zrpc/server.go:23-27             server, err := NewServer(...); logx.Must(err); return server
            //    zrpc/server.go:42-46             HasEtcd ⇒ internal.NewRpcPubServer(…)；err ⇒ return nil, err
            //    core/logx/logs.go:232-242        Must(err) ⇒ 打印堆栈 ⇒ if ExitOnFatal.True() { os.Exit(1) }
            //    core/logx/vars.go:72             ExitOnFatal = syncx.ForAtomicBool(true)  ← 默认 true
            //  原文写的「panic」不准确（那是 ExitOnFatal 被设为 false 时的次生路径：
            //  此时 MustNewServer 会返回 nil server，调用方后续解引用才 panic）。
            //
            //  这里**有意偏离**：单 JVM 里 gRPC server 已经起来了，为一次注册失败杀掉整个进程
            //  会让「Nacos 抖动 = 全站点赞不可用」，比 Go 更糟。
            //  ⚠️ 代价（必须随本偏离一起读）：注册失败时本进程**不会**出现在服务发现里 ⇒
            //  调用方拿不到本节点，流量不会被打进来；只有已建立的连接与 in-flight 请求继续。
            //  ⇒ 这是「可用性优先于一致性」的取舍，不是「无差异」。
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