package com.rag.task.task.rpc.bootstrap;

import com.alibaba.cloud.nacos.NacosServiceManager;
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
import java.util.Enumeration;

/**
 * task-rpc 启动后注册到 Nacos —— 对应 Go 端 {@code service/task/rpc/task.go:37} 里
 * {@code zrpc.MustNewServer(c.RpcServerConf, …)} 的隐式 etcd 注册。
 *
 * <p>Go {@code etc/task.yaml} 的 {@code Name: task.rpc} + {@code Etcd.Key: task.rpc}
 * ⇒ 服务发现名统一为 {@code task-rpc}。
 *
 * <p>显式手写注册而不依赖 {@code spring-cloud-starter-alibaba-nacos-discovery} 自动注册，
 * 原因与 message-rpc / like-rpc 同款：自动注册依赖 Spring Web，
 * 纯 gRPC 进程强行用会把 web 容器拉起来。
 *
 * <p><b>⚠️ Bean 名必须显式指定（DRIFT-MSG-004）</b>：
 * {@code NacosServiceRegistryAutoConfiguration} <b>也会注册一个名为
 * {@code nacosRegistration} 的 Bean</b>。若本类用裸 {@code @Component}，
 * 默认 Bean 名恰好相同 ⇒ Spring Boot 默认
 * {@code allow-bean-definition-overriding=false} ⇒ <b>上下文直接起不来</b>。
 */
@Component("taskRpcNacosRegistration")
public class NacosRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistration.class);

    /** 对应 Go yaml {@code Etcd.Key: task.rpc}。 */
    private static final String SERVICE_NAME = "task-rpc";

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;
    private final String defaultGroup;

    /** 对应 Go etcd 注册的 { ip, port }。 */
    private final String localIp;

    public NacosRegistration(NacosServiceManager nacosServiceManager,
                             GrpcServerProperties grpcServerProperties,
                             @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
                             String nacosServerAddr,
                             @Value("${spring.cloud.nacos.discovery.group:DEFAULT_GROUP}")
                             String defaultGroup) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
        this.defaultGroup = defaultGroup;
        this.localIp = resolveLocalIp();
        LOG.info("task-rpc nacos registration target: addr={} service={} group={} ip={} port={}",
                nacosServerAddr, SERVICE_NAME, defaultGroup, this.localIp, grpcServerProperties.getPort());
    }

    /**
     * Spring 启动完成后注册。
     *
     * <p>对应 Go 侧 {@code s.Start()} 后由 zrpc 立即 EtcdRegister 的时序：
     * 监听 {@link ApplicationReadyEvent} 而不是 {@code ContextRefreshedEvent}，
     * 确保 gRPC server 与 Kafka listener 都已真正起来。
     *
     * <p>⚠️ <b>注册失败不阻止启动</b>：Nacos 不可用时本进程仍能提供 gRPC 服务
     * （只是<b>没有人能发现它</b>）。这与 Go 的行为一致 ——
     * go-zero 的 etcd 注册失败会 panic，但那是另一个失败面；
     * 这里选择"注册失败只告警"，避免配置中心抖动导致业务进程批量重启。
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
                    .registerInstance(SERVICE_NAME, defaultGroup, instance);
            LOG.info("task-rpc registered to nacos: {}:{} (group={})",
                    localIp, grpcServerProperties.getPort(), defaultGroup);
        } catch (Exception e) {
            LOG.error("task-rpc nacos registration failed (process keeps serving gRPC, "
                    + "but no caller can discover it): {}", e.getMessage(), e);
        }
    }

    /**
     * 解析本机出口 IP —— 与 like-rpc / message-rpc 同款实现。
     *
     * <p>⚠️ {@code NetworkInterface.getNetworkInterfaces()} / {@code getInetAddresses()}
     * 声明的是 {@code SocketException}（checked），不是 {@code UnknownHostException}。
     * 这里一并 catch：解析不出来就回落到 {@link InetAddress#getLoopbackAddress()}，
     * 而不是让构造器抛异常 —— 构造器抛异常会导致<b>上下文起不来</b>，
     * 而「拿不到出口 IP」不该阻止一个纯 gRPC 进程启动（只是注册出去的地址不理想）。
     */
    private static String resolveLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    // 跳过 IPv6（Go 侧注册的是 v4 地址；含 ':' 即 v6）
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            // NetworkInterface 相关 API 抛的是 SocketException，这里统一回落
            return InetAddress.getLoopbackAddress().getHostAddress();
        }
    }
}