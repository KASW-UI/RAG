package com.rag.users.admin.rpc.internal.svc;

import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.model.DataModel;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 装配 Config + DataModel + RedisTemplate + Tracer。
 * 对应 Go 端 internal/svc/servicecontext.go 的 svc.ServiceContext：
 *   type ServiceContext struct {
 *       Config     config.Config
 *       AdminModel *model.AdminModel
 *       BizRedis   *redis.Redis
 *   }
 *
 * 额外持有的 Tracer 用于 Logic 内起 OTel span（Go 端在 main 里通过 observability.NewUnaryServerInterceptor 注入；
 * Java 侧 grpc-spring-boot-starter 没自动装配 Tracer bean，这里显式从 GlobalOpenTelemetry 拿）。
 *
 * 注：Go 端 ServiceContext 包含一个业务方法 ensureDefaultAdmin（启动期创建默认 admin），
 *     Java 侧把这段业务下沉到独立的 AdminBootstrap（@EventListener(ApplicationReadyEvent)），
 *     ServiceContext 保持纯依赖注入角色。
 */
@Service
public class ServiceContext {

    private final Config config;
    private final DataModel dataModel;
    private final StringRedisTemplate bizRedis;
    private final Tracer tracer;

    public ServiceContext(Config config, DataModel dataModel, StringRedisTemplate bizRedis) {
        this.config = config;
        this.dataModel = dataModel;
        this.bizRedis = bizRedis;
        // 对应 Go 端 otel.Tracer("admin-rpc")
        this.tracer = GlobalOpenTelemetry.getTracer("admin-rpc");
    }

    public Config getConfig() { return config; }
    public DataModel getDataModel() { return dataModel; }
    public StringRedisTemplate getBizRedis() { return bizRedis; }
    public Tracer getTracer() { return tracer; }
}