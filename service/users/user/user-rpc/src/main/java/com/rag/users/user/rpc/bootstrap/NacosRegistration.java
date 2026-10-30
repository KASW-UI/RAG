package com.rag.users.user.rpc.bootstrap;

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
 * 显式注册 user-rpc 实例到 Nacos。
 *
 * <p>DRIFT-NEW-011 根因：user-rpc 原先没有 web 容器 ⇒ Spring Cloud Commons 的 {@code NacosAutoServiceRegistration}
 * 默认监听 {@code WebServerInitializedEvent} 触发自动注册 —— 那时 user-rpc 不发这个 event ⇒ 启动时不调 register
 * ⇒ 本地 API 查实例列表永远 hosts=[]。
 *
 * <p>🔴 <b>2026-10-30 补载体后前提已变</b>：现在会发该 event ⇒ ⇒ 自动注册被触发，取的是 <b>web server 端口</b>（观测口 9094，非 gRPC 口 49210）
 * ⇒ ⇒ Nacos 出现错端口实例 ⇒ ⇒ gRPC 链路断（业务码塌 1015）⇒ ⇒ yml 必须写 {@code register-enabled: false}（详见 yml Nacos 段注释）。
 *
 * <p>1:1 语义对齐 Go 端：Go user-rpc 启动时主动调
 * {@code NamingService.RegisterInstance(serviceName, groupName, instance)}
 * （不是依赖 WebServerInitializedEvent）。本类用同样路径：
 * {@link NacosServiceManager#getNamingService()} 拿到 {@link NamingService}，
 * 显式 {@code registerInstance(serviceName, groupName, instance)}。
 *
 * <p>对应 Go: service/user/user/rpc/internal/svc/servicecontext.go 中的 etcd registration
 *   （Java 侧对齐到 Nacos dynamic discovery）。
 *
 * <p>框架职责:
 *   - Spring @EventListener(ApplicationReadyEvent) + @PreDestroy
 *   - 取代了原 internal.svc.NacosBootstrap
 *   - user-api 通过 discovery://user-rpc 发现本进程
 *   - 自定义 metadata: gRPC port（让 user-api 端能找到 49210 而不是默认 9004）
 *
 * <p><b>DRIFT-NEW-013</b>：bean 名必须显式指定，不能用类名默认推导。
 * {@code NacosServiceRegistryAutoConfiguration} 里有个 {@code @Bean nacosRegistration()}，
 * 而本类叫 {@code NacosRegistration} → {@code @Component} 默认 bean 名恰好也是
 * {@code nacosRegistration}，两者撞名 → {@code BeanDefinitionOverrideException}
 * → 整个应用上下文启动失败。保留类名（NacosRegistration 是 HUMAN_ONLY.MD 定的
 * bootstrap/ 布局），只把 bean 名错开。
 *
 * <p>参考侧（Sea-RideTheWind_java）这个类叫 {@code NacosBootstrap}，
 * bean 名 {@code nacosBootstrap} 不撞——是重构改名时踩到的。
 */
@Component("userRpcNacosRegistration")
public class NacosRegistration {

    private static final Logger log = LoggerFactory.getLogger(NacosRegistration.class);

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;

    /**
     * 🆕 DRIFT-G1-001（2026-10-27 · G1 lane · 真启动 A/B 实测）：**注册用的 group 必须读配置**。
     *
     * <p><b>Go 出处</b>：{@code service/user/user/rpc/etc/user.yaml:47}
     * {@code Etcd: { Key: user.rpc }} —— Go 侧的服务发现**键**（= 本仓的 group+serviceName 寻址维度）
     * 是<b>配置项</b>，部署时改 yaml 就换命名空间。
     *
     * <p><b>修前</b>：本类把 group <b>写死</b>成 {@code "USER_GROUP"}
     * ⇒ {@code NACOS_GROUP} 这个环境变量对 user-rpc <b>完全无效</b>
     * ⇒ 而 user-api 的 {@code application.yml} 写的是 {@code group: ${NACOS_GROUP:USER_GROUP}}（<b>读配置</b>）
     * ⇒ ⇒ 部署一旦设 {@code NACOS_GROUP=别的} ⇒ <b>rpc 注册到 A 组、api 去 B 组找</b>
     * ⇒ ⇒ <b>恒 "No servers found" ⇒ 所有业务码塌成 1015</b>。
     *
     * <p><b>修后</b>：group 从 {@code spring.cloud.nacos.discovery.group} 读
     * （= 同一份 yml 里已有的那一行，<b>不需要新增配置项</b>）。
     * ⇒ <b>默认值零变化</b>：yml 里写的就是 {@code ${NACOS_GROUP:USER_GROUP}}
     * ⇒ 不设环境变量时解析结果与修前<b>逐字相同</b>（仍是 {@code USER_GROUP}）。
     *
     * <p>⚠️ <b>本仓的 yml 早就写了这个属性</b>（{@code application.yaml:58}
     * {@code group: ${NACOS_GROUP:USER_GROUP}}）⇒ 之前是<b>配置存在但代码不消费</b>
     * ⇒ 与 {@code DRIFT-FOLLOW-004}（修的是 {@code NACOS_ADDR/PORT} 那一维）是<b>同族漏审</b>：
     * <b>「配置项存在」≠「配置项被消费」</b>。
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
        String serviceName = "user-rpc";
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
            log.error("user-rpc nacos register FAILED service={} group={} port={}",
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
            log.warn("user-rpc nacos deregister FAILED", e);
        }
    }

    /**
     * 解析本机非 loopback 的 IPv4 地址。
     * DRIFT-NEW-012：InetAddress.getLocalHost() 在 macOS 上经常返回 127.0.0.1（loopback），
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