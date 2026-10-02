package com.rag.user.user.rpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 对应 Go: user.go 中的 func main()
 * - Go 在 main() 里手动 logx.MustSetup / NewServiceContext / zrpc.MustNewServer / s.Start()
 * - Java 全部交给 Spring Boot + grpc-spring-boot-starter 自动完成:
 *     * 配置加载 → application.yaml
 *     * 依赖注入 → @Autowired / 构造器注入
 *     * gRPC Server 监听 → @GrpcService 注解 + grpc.server.port
 *     * Nacos 注册 → @EnableDiscoveryClient
 * - 唯一仍保留的入口:SpringApplication.run() 启动 Spring 上下文
 */
@SpringBootApplication
@EnableDiscoveryClient                  // 对应 Go:Etcd 注册,Java 走 Nacos
@ConfigurationPropertiesScan            // 扫描 @ConfigurationProperties,自动注册 Config.java
public class UserRpcApplication {

    public static void main(String[] args) {
        // 对应 Go: s.Start()
        // Go 是阻塞在 grpcServer.Serve();Spring Boot 的 run() 也是阻塞,本质一致
        SpringApplication.run(UserRpcApplication.class, args);
    }
}