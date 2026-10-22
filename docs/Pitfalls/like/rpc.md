<!-- @move AGENTS.md L1841-L1855 · §3.3.3 Boot 4 把 Kafka 自动配置搬出 autoconfigure ⇒ @KafkaListener 静默失效 -->
Spring Boot 4 把 Kafka 自动配置**整体搬出了 `spring-boot-autoconfigure`**
（实测 `spring-boot-autoconfigure-4.0.8.jar` 内 Kafka 相关类数量 = **0**），
改由独立模块 **`org.springframework.boot:spring-boot-kafka`** 提供，包名也从
`org.springframework.boot.autoconfigure.kafka.*` 移到 `org.springframework.boot.kafka.autoconfigure.*`。

⇒ 只引 `org.springframework.kafka:spring-kafka` 时：
**没有 `ConsumerFactory` Bean、没有 `KafkaListenerAnnotationBeanPostProcessor`**
⇒ `@KafkaListener` 被**完全静默忽略**：不报错、上下文照常启动、容器正常起来、**一行消息都不消费**。

这比 §3.3 已有的 H2（`@Component` ≠ 自动注册，表现为 Bean 不存在）**更难发现**：
H2 至少还有「类存在但 Bean 不存在」这条线索；本条是**连类都不在 classpath 上**，
而源码里那个 `@KafkaListener` 看起来完全正常。

**强制**：
1. 凡用 `@KafkaListener`，必须有一条**装配层**测试断言 listener **真的进了容器的 registry**
