package com.rag.follow.follow.rpc.bootstrap;

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
 * follow-rpc 进程启动后注册到 Nacos —— 对应 Go 端
 * {@code service/follow/rpc/etc/follow.yaml:4-7} 的 etcd 注册：
 * <pre>
 * Etcd:
 *   Hosts: [etcd:2379]
 *   Key: follow.rpc
 * </pre>
 * Go 侧由 {@code zrpc.MustNewServer(...)} → {@code RpcServer.Start()} 自动完成。
 *
 * <p>{@code NacosServiceManager.registerInstance(serviceName, group, instance)} 替代它：
 * <ul>
 *   <li>{@code Name: follow.rpc} → {@code spring.application.name=follow-rpc}</li>
 *   <li>{@code Etcd.Key: follow.rpc} → {@code serviceName=follow-rpc}（服务发现名，与 follow-api
 *       的 client 配置 {@code grpc.client.follow-rpc.address: discovery://follow-rpc} 对应）</li>
 *   <li>{@code ListenOn: 0.0.0.0:8082} → {@code grpc.server.port=8082}</li>
 * </ul>
 *
 * <p>显式实现而不依赖 {@code spring-cloud-starter-alibaba-nacos-discovery} 的自动注册：
 * 后者默认依赖 Spring Web，强行用在纯 gRPC 服务上会把 web 容器启起来。
 * 本服务不带 web，走纯手动注册路径：
 * <ol>
 *   <li>{@code ApplicationReadyEvent} 后注册 —— 与 Go 端 {@code s.Start()} 后立即注册同序；</li>
 *   <li>容器关闭时反注册（{@code @PreDestroy}），防止重启残留 stale 节点。</li>
 * </ol>
 *
 * <p><b>⚠️ Bean 名必须显式指定</b>：{@code NacosServiceRegistryAutoConfiguration}
 * <b>也会注册一个名为 {@code nacosRegistration} 的 Bean</b>。若本类用裸 {@code @Component}，
 * 默认 Bean 名恰好相同 ⇒ 撞名 ⇒ Spring Boot 默认
 * {@code allow-bean-definition-overriding=false} ⇒ <b>上下文直接起不来</b>：
 * <pre>
 *   The bean 'nacosRegistration', defined in …NacosServiceRegistryAutoConfiguration.class,
 *   could not be registered. A bean with that name has already been defined in
 *   …/bootstrap/NacosRegistration.class and overriding is disabled.
 * </pre>
 * 同款 Bean 名已在 message-rpc / user-rpc / admin-rpc 上显式指定，本类沿用。
 *
 * <p><b>⚠️ 本类只有</b> {@code ApplicationReadyEvent} <b>时才注册</b> ——
 * 与 Go 的 {@code s.Start()} 一致（不是在 Bean 构造时就注册）。
 * 若提前到构造期注册，会出现「注册成功但端口还没 listen」的窗口，
 * 让 follow-api 在这个窗口里拿到一个连不上的地址。
 */
@Component("followRpcNacosRegistration") // ← 显式命名，避开 NacosServiceRegistryAutoConfiguration 的同名 Bean
public class NacosRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistration.class);

    /** 对应 Go yaml：Key: follow.rpc → 服务发现按这个名注册。 */
    private static final String SERVICE_NAME = "follow-rpc";

    private final NacosServiceManager nacosServiceManager;
    private final GrpcServerProperties grpcServerProperties;
    private final String localIp;
    private final int nacosServerPort;

    /**
     * 🆕 DRIFT-G1-001（2026-10-27 · G1 lane · 真启动 A/B 实测）：**注册用的 group 必须读配置**。
     *
     * <p><b>Go 出处</b>：{@code service/follow/rpc/etc/follow.yaml:4-7}
     * {@code Etcd: { Hosts: [...], Key: follow.rpc }} —— Go 侧的服务发现键是<b>配置项</b>。
     *
     * <p><b>修前</b>：本类把 group <b>写死</b>成 {@code "DEFAULT_GROUP"}
     * ⇒ {@code NACOS_GROUP} 对 follow-rpc <b>完全无效</b>；
     * 而 follow-api 的 yml 写的是 {@code group: ${NACOS_GROUP:DEFAULT_GROUP}}（<b>读配置</b>）
     * ⇒ ⇒ 部署一旦设了不同的 {@code NACOS_GROUP} ⇒ <b>rpc 注册到 A 组、api 去 B 组找</b>
     * ⇒ ⇒ <b>恒 "No servers found for follow-rpc" ⇒ 所有业务码塌成 1015</b>（实测）。
     *
     * <p><b>修后</b>：group 从 {@code spring.cloud.nacos.discovery.group} 读
     * （= 同一份 yml 里已有的那一行，<b>不需要新增配置项</b>）。
     * ⇒ <b>默认值零变化</b>：yml 里写的就是 {@code ${NACOS_GROUP:DEFAULT_GROUP}}
     * ⇒ 不设环境变量时解析结果与修前<b>逐字相同</b>。
     *
     * <p>⚠️ 同 user-rpc / admin-rpc：<b>配置项早就存在（{@code application.yml:97}）只是代码不消费</b>
     * ⇒ 与 {@code DRIFT-FOLLOW-004}（修的是 {@code NACOS_ADDR/PORT} 那一维）是<b>同族漏审</b>：
     * <b>「配置项存在」≠「配置项被消费」</b>。
     */
    private final String groupName;

    /** 当前已注册实例句柄（防止重复注册 / 用于 deregister）。 */
    private volatile Instance registeredInstance;

    public NacosRegistration(NacosServiceManager nacosServiceManager,
                             GrpcServerProperties grpcServerProperties,
                             @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
                             String nacosServerAddr,
                             @Value("${spring.cloud.nacos.discovery.group:DEFAULT_GROUP}")
                             String groupName) {
        this.nacosServiceManager = nacosServiceManager;
        this.grpcServerProperties = grpcServerProperties;
        this.groupName = groupName;
        // nacos server 地址可能是 "host" 或 "host:port"
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

    /** 对应 Go 端 {@code s.Start()} 后由 go-zero 触发的 etcd 注册。 */
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(grpcServerProperties.getPort());
            instance.setServiceName(SERVICE_NAME);
            // Nacos 2.x Instance 不含 groupName 字段：group 走 registerInstance(serviceName, group, instance) 第 2 个参数
            instance.setHealthy(true);
            instance.setWeight(1.0);
            instance.setEphemeral(true);

            nacosServiceManager.getNamingService()
                    .registerInstance(SERVICE_NAME, groupName, instance);
            this.registeredInstance = instance;
            LOG.info("NacosRegistration registered: service={} group={} ip={} port={} nacosPort={}",
                    SERVICE_NAME, groupName, localIp, grpcServerProperties.getPort(), nacosServerPort);
        } catch (NacosException e) {
            // ⚠️ 注册失败只记日志、不让进程退出 —— 与 Go 的 etcd 注册失败行为一致
            //    （go-zero 注册失败会打日志，server 本身照常 Start）。
            //    这让 follow-rpc 仍可被静态配置直连，只是不在服务发现里出现。
            LOG.error("NacosRegistration.register failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /** 容器关闭时反注册 —— 防止进程重启残留 stale 节点。 */
    @jakarta.annotation.PreDestroy
    public void deregister() {
        if (registeredInstance == null) {
            return;
        }
        try {
            nacosServiceManager.getNamingService()
                    .deregisterInstance(SERVICE_NAME, groupName,
                            registeredInstance.getIp(), registeredInstance.getPort());
            LOG.info("NacosRegistration deregistered: service={} group={} ip={} port={}",
                    SERVICE_NAME, groupName,
                    registeredInstance.getIp(), registeredInstance.getPort());
        } catch (NacosException e) {
            LOG.warn("NacosRegistration.deregister failed for {}: {}", SERVICE_NAME, e.getErrMsg(), e);
        }
    }

    /**
     * 对应 Go 侧注册用的本地 IP 解析：优先取第一个非 loopback 网卡的 IPv4。
     *
     * <p>Go 用 {@code net.InterfaceAddrs()}，Java 用 {@link NetworkInterface} 遍历。
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