package com.rag.users.admin.rpc.bootstrap;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * 显式注册 admin-rpc 实例到 Nacos。
 *
 * <p>DRIFT-NEW-011 根因：admin-rpc 原先没有 web 容器 ⇒ Spring Cloud Commons 的 {@code NacosAutoServiceRegistration}
 * 默认监听 {@code WebServerInitializedEvent} 触发自动注册 —— 那时 admin-rpc 不发这个 event ⇒ 启动时不调 register。
 *
 * <p>🔴 <b>2026-10-30 补载体后前提已变</b>：现在会发该 event ⇒ ⇒ 自动注册被触发，取的是 <b>web server 端口</b>（观测口 9097，非 gRPC 口 8081）
 * ⇒ ⇒ Nacos 出现错端口实例 ⇒ ⇒ gRPC 链路断（业务码塌 1015）⇒ ⇒ yml 必须写 {@code register-enabled: false}（详见 yml Nacos 段注释）。
 *
 * <p>1:1 语义对齐 Go 端：Go admin-rpc 启动时主动调
 * {@code NamingService.RegisterInstance(serviceName, groupName, instance)}。
 * 本类用同样路径：{@link NacosServiceManager#getNamingService()} 拿到
 * {@link NamingService}，显式 {@code registerInstance}。
 */
/**
 * bean 名必须显式错开：NacosServiceRegistryAutoConfiguration 里有个
 * {@code @Bean nacosRegistration()}，与本类的默认 bean 名（类名首字母小写）撞名 →
 * BeanDefinitionOverrideException → 应用启动失败。
 * （user-rpc 同款问题，见该模块 NacosRegistration 的注释。）
 */
@Component("adminRpcNacosRegistration")
public class NacosRegistration {

    private static final Logger log = LoggerFactory.getLogger(NacosRegistration.class);

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;

    /**
     * 🆕 DRIFT-G1-001（2026-10-27 · G1 lane · 真启动 A/B 实测）：**注册用的 group 必须读配置**。
     *
     * <p><b>Go 出处</b>：{@code service/user/admin/rpc/etc/admin.yaml} 的 {@code Etcd.Key}
     * —— Go 侧的服务发现键是<b>配置项</b>，部署时改 yaml 就换命名空间。
     *
     * <p><b>修前</b>：本类把 group <b>写死</b>成 {@code "USER_GROUP"}
     * ⇒ {@code NACOS_GROUP} 对 admin-rpc <b>完全无效</b>；
     * 而 admin-api 的 yml 写的是 {@code group: ${NACOS_GROUP:USER_GROUP}}（<b>读配置</b>）
     * ⇒ ⇒ 一旦部署设了不同的 {@code NACOS_GROUP} ⇒ <b>rpc 注册到 A 组、api 去 B 组找</b>
     * ⇒ ⇒ <b>admin-api 直接进程退出</b>（实测：{@code No servers found for admin-rpc}）。
     *
     * <p><b>修后</b>：group 从 {@code spring.cloud.nacos.discovery.group} 读
     * （= 同一份 yml 里已有的那一行，<b>不需要新增配置项</b>）。
     * ⇒ <b>默认值零变化</b>：yml 里写的就是 {@code ${NACOS_GROUP:USER_GROUP}}
     * ⇒ 不设环境变量时解析结果与修前<b>逐字相同</b>。
     *
     * <p>⚠️ 同 user-rpc：<b>配置项早就存在（{@code application.yml:75}）只是代码不消费</b>
     * ⇒ 「配置项存在」≠「配置项被消费」。
     */
    private final String groupName;

    private Instance registeredInstance;
    private String registeredServiceName;
    private String registeredGroupName;

    public NacosRegistration(NacosServiceManager nacosServiceManager,
                             GrpcServerProperties grpcServerProperties,
                             @Value("${spring.cloud.nacos.discovery.group:USER_GROUP}")
                             String groupName) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
        this.groupName = groupName;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        String serviceName = "admin-rpc";
        String groupName = this.groupName;
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

            log.info("nacos registry, {} {} {}:{} register finished",
                    groupName, serviceName, instance.getIp(), port);
        } catch (NacosException | UnknownHostException e) {
            log.error("admin-rpc nacos register FAILED service={} group={} port={}",
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
            log.info("nacos registry, {} {} deregister finished",
                    registeredGroupName, registeredServiceName);
        } catch (NacosException e) {
            log.warn("admin-rpc nacos deregister FAILED", e);
        }
    }

    /**
     * DRIFT-NEW-012：{@code InetAddress.getLocalHost()} 在 macOS 上经常返回 127.0.0.1（loopback），
     * docker 容器跨 host 网络访问不到 host loopback。要走非 loopback 的 IPv4（en0/en1 NIC）。
     */
    private static String resolveLocalIp() throws UnknownHostException {
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
