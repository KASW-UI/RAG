#
# Sea-RideTheWind_java — Spring Boot 服务镜像打包
#
# 策略：jar 在 host 上用 mvn 预先打好（manage.sh build 调用），
#       Dockerfile 只负责把 jar 复制进 runtime 镜像。理由：
#       Docker Desktop 在 macOS 上跑在 Linux VM，从 VM 里走 Maven Central
#       下依赖慢且不稳（USTC mirror 也会被 QPS 限流）。Host 端 mvn 直接走
#       宿主机网络 + 复用 ~/.m2/repository 缓存更稳。
#
# 构建流程：
#   1. mvn -B -ntp -pl service/users/user/${SERVICE_NAME} -am clean package -DskipTests
#   2. docker build --build-arg SERVICE_NAME=${SERVICE_NAME} -t sea/${SERVICE_NAME}:latest .
#
# 运行时端口（与 docker-compose.yaml 的 ports 对齐）：
#   - user-rpc:  49210 (gRPC) — 不暴露 HTTP / actuator
#   - user-api:  49900 (HTTP) — Spring Boot Web + actuator

# ===================== Runtime =====================
FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_NAME=user-rpc

WORKDIR /app

# jar 在 host 上 mvn 打完放这里，COPY 进来即可
COPY service/users/user/${SERVICE_NAME}/target/${SERVICE_NAME}-*.jar /app/app.jar

# 元数据：暴露端口仅声明用，实际绑定由 docker-compose.yaml 的 ports: 决定
EXPOSE 49210 49900

# 容器化 JVM 参数
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
