package com.rag.message.message.rpc;

import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * <b>DRIFT-MSG-V-015 的装配层守门</b>：gRPC server reflection 的暴露面必须与 Go 侧一致。
 *
 * <h3>这条为什么曾经是「注释说了假话」</h3>
 * <p>`application.yml` 原来的注释写着：
 * <blockquote>「Go 端 reflection 在 DevMode/TestMode 启用，Java 端由 devh starter 默认注册 reflection」</blockquote>
 * 这句话把「devh 的默认值是 true」说成了「与 Go 一致」。实际两边<b>相反</b>：
 *
 * <pre>
 *   Go   message.go:40-42
 *        if c.Mode == service.DevMode || c.Mode == service.TestMode { reflection.Register(grpcServer) }
 *        └─ c.Mode 来自内嵌的 zrpc.RpcServerConf，tag 是
 *           `json:",default=pro,options=dev|test|rt|pre|pro"`（go-zero@v1.10.2
 *           core/service/serviceconf.go:35）
 *        └─ etc/message.yaml **没有配 Mode** ⇒ 默认 "pro" ⇒ 分支不成立
 *        ⇒ 真源生产形态**不暴露** reflection
 *
 *   RAG  devh grpc-server-spring-boot-starter 3.1.0.RELEASE
 *        GrpcServerProperties.reflectionServiceEnabled 默认 **true**
 *        （spring-configuration-metadata.json: defaultValue=True）
 * </pre>
 *
 * <p>后果：RAG 侧 `grpcurl -plaintext localhost:8082 list` 能列出全部 9 个方法并下载 proto 描述符，
 * Go 侧同一命令连不上。
 *
 * <h3>本测试验证什么、不验证什么</h3>
 * <ul>
 *   <li><b>验证</b>：真 Spring 上下文里，`application.yml` 那一行确实绑定到了
 *       devh 的配置对象上（§3.3.3「断言 Bean 存在 ≠ 机制生效」的同类 —— 这里断言的是
 *       <b>配置值本身</b>，而不是「配置文件里有一行」）。</li>
 *   <li><b>不验证</b>：「reflection 服务真的没被注册到 ServerBuilder」属于 starter 内部行为，
 *       需要起真端口 + `grpcurl` 才能证。本轮<b>未</b>做端到端验证，
 *       已在台账按 §6.2 约定 2 标注证据边界。</li>
 * </ul>
 */
@SpringBootTest(
        classes = MessageRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 同 MessageTransactionWiringTest：devh 在 Bean 初始化期就建 channel，
                // 没有 Nacos 时 discovery:// 形式会让上下文起不来。
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:1"
        })
@Testcontainers
class MessageGrpcReflectionExposureWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_reflection_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private GrpcServerProperties grpcServerProperties;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("V-015：application.yml 必须把 reflection 关掉（否则比真源多暴露一整个反射面）")
    void reflectionIsDisabledLikeTheGoSource() {
        assertFalse(grpcServerProperties.isReflectionServiceEnabled(),
                "Go 真源在 Mode=pro（默认）下不注册 reflection；"
                        + "devh 默认 true，若这一行没绑上，RAG 会比真源多暴露 grpcurl 可读的 proto 描述符");
    }

    /**
     * 把「配置文件里写了」与「绑定到配置对象上」分开断言 ——
     * §3.3.3 的形态：yml 里那行被删掉/写错/被 profile 覆盖时，下面这行会先红，
     * 让人一眼看出是**配置没生效**，而不是「机制没实现」。
     */
    @Test
    @DisplayName("V-015：Environment 里能读到该键 ⇒ yml 那行确实被解析（不是靠默认值凑巧）")
    void propertyIsActuallyDeclaredInYaml() {
        String raw = environment.getProperty("grpc.server.reflection-service-enabled");
        assertEquals("false", raw,
                "期望 application.yml 显式声明 false；读到 null 说明这一行不存在，"
                        + "当前之所以关掉只是 starter 恰好不是默认值 —— 换 starter 版本就会静默回退");
    }
}