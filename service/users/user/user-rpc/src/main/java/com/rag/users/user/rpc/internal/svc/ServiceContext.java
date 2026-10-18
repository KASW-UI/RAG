package com.rag.users.user.rpc.internal.svc;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.rpc.internal.config.Config;
import com.rag.users.user.rpc.internal.model.DataModel;
import io.grpc.Status;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 装配 Config + DataModel + RedisTemplate + Tracer。
 * 对应 Go 端 internal/svc/servicecontext.go 的 svc.ServiceContext：
 *   type ServiceContext struct {
 *       Config    config.Config
 *       UserModel *model.UserModel
 *       BizRedis  *redis.Redis
 *   }
 *
 * <p>额外持有的 Tracer 用于 Logic 内起 OTel span（Go 端在 main 里通过 observability.NewUnaryServerInterceptor 注入；
 * Java 侧 grpc-spring-boot-starter 没自动装配 Tracer bean，这里显式从 GlobalOpenTelemetry 拿）。
 *
 * <p><b>DRIFT-018 修复（业务侧补救）</b>：{@link #checkDeadline()} 在每个 Logic 业务步骤前调；
 * 抛出 {@code DEADLINE_EXCEEDED} gRPC 异常让 handler 立即停，对齐 Go 端 ctx 取消语义。
 * 调用方（{@code UserServiceServer.invoke()}）已有的 {@code catch (StatusRuntimeException)}
 * 直接透传该异常给 client。
 * 当 {@link DeadlineState} 未注册 deadline（即业务模块未启用 RpcInterceptor）时，本方法为 no-op。
 */
@Service
public class ServiceContext {

    private final Config config;
    private final DataModel userModel;
    private final StringRedisTemplate bizRedis;
    private final Tracer tracer;
    /**
     * DRIFT-OBS-LOGIN 修复（与 user-api / admin-api 对齐）：注入 {@link LoggerFactoryBean}，
     * 三个 Logic（Login / Register / GetUser）通过 {@code svcCtx.getLogger()} 调 logger，
     * 1:1 复刻 Go 端 {@code logger.LogInfo / LogBusinessErr} 调用。
     */
    private final LoggerFactoryBean logger;

    public ServiceContext(Config config, DataModel userModel, StringRedisTemplate bizRedis,
                          LoggerFactoryBean logger) {
        this.config = config;
        this.userModel = userModel;
        this.bizRedis = bizRedis;
        // 对应 Go 端 otel.Tracer("user-rpc")
        this.tracer = GlobalOpenTelemetry.getTracer("user-rpc");
        this.logger = logger;
    }

    public Config getConfig() { return config; }
    public DataModel getUserModel() { return userModel; }
    public StringRedisTemplate getBizRedis() { return bizRedis; }
    public Tracer getTracer() { return tracer; }
    public LoggerFactoryBean getLogger() { return logger; }

    /**
     * DRIFT-018 修复：检查当前线程是否已超过 deadline；任一为 true 抛
     * {@code StatusRuntimeException(DEADLINE_EXCEEDED)}，被
     * {@code UserServiceServer.invoke()} 的 {@code catch (StatusRuntimeException)} 接住。
     *
     * <p>业务 Logic 在每次 DB / bcrypt / snowflake / 下游调用前调此方法。
     * 配合 {@link DeadlineState#markExpired(long)}（由 {@code RpcInterceptor} 调度）
     * 或 {@link Thread#interrupt()}（由 {@code RpcInterceptor.handle()} 路径的
     * {@code Future.cancel(true)} 触发），可让 handler 在 timeout 后立即感知并退出。
     *
     * <p>未注册 deadline 时为 no-op（业务不受影响）——这意味着本方法即使在没启用 RpcInterceptor
     * 的业务模块里也是安全的，调用零代价。
     */
    public void checkDeadline() {
        if (Thread.currentThread().isInterrupted() || DeadlineState.isExpired()) {
            throw Status.DEADLINE_EXCEEDED
                    .withDescription("rpc deadline reached")
                    .asRuntimeException();
        }
    }
}