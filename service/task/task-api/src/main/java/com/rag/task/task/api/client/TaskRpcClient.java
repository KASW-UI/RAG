package com.rag.task.task.api.client;

import com.rag.task.task.proto.TaskServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;


/**
 * task-rpc gRPC client —— 对应 Go {@code api/internal/svc/servicecontext.go:22-32}：
 *
 * <pre>
 *   type ServiceContext struct {
 *       Config  config.Config
 *       TaskCli taskpb.TaskServiceClient // 这里的名字以你 proto 生成的为准
 *   }
 *   func NewServiceContext(c config.Config) *ServiceContext {
 *       conn := zrpc.MustNewClient(c.TaskRpc)
 *       return &amp;ServiceContext{Config: c, TaskCli: taskpb.NewTaskServiceClient(conn.Conn())}
 *   }
 * </pre>
 *
 * <h2>框架接管了什么</h2>
 * <ul>
 *   <li>{@code @GrpcClient("task-rpc")} 由 devh grpc-client-spring-boot-starter 注入
 *       <b>阻塞</b> stub；寻址走 {@code application.yml} 的
 *       {@code grpc.client.task-rpc.address}。⇒ Go 的 {@code zrpc.MustNewClient} +
 *       {@code NewTaskServiceClient} 两层由框架与 {@code BlockingStub} 吸收。</li>
 *   <li>用 BlockingStub 而非 future stub：Go 侧 api→rpc 是<b>同步阻塞</b>调用
 *       （{@code gettasklogic.go:36} 直接拿返回值，没有 goroutine / errgroup），
 *       这一点属 L3（并发与失败传播语义），不是可随手换的风格差异。</li>
 *   <li><b>client 熔断无需本类承担</b>：Go 侧 {@code zrpc/internal/config.go:14}
 *       {@code ClientMiddlewaresConf.Breaker bool `json:",default=true"`}，
 *       而 {@code etc/task.yaml:5-7} <b>没有</b> {@code Middlewares} 段 ⇒ 默认<b>开启</b>；
 *       Java 侧由 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor}
 *       实现（扫到 {@code com.rag.common} 即生效）。<b>不要</b>在这里再加一个拦截器。</li>
 * </ul>
 *
 * <h2>🔴 2026-10-31（P2-T04 · G2 lane）：单次调用上界已下沉共享层</h2>
 * <p>原先本类自己算 {@code min(单次上界 2000ms, 请求剩余预算)} 并套到 stub 上
 * ⇒ ⇒ 那是<b>域侧的第二份上界实现</b>（副本）⇒ ⇒ 现在<b>删掉</b>：
 * <ul>
 *   <li><b>副本把上界写死在 {@code CallOptions} 里</b> ⇒ 共享层再对它取 min
 *       ⇒ ⇒ <b>min 只压小、不压大</b> ⇒ ⇒ 「想放宽上界」会被副本压回 2000ms
 *       ⇒ ⇒ 「上界可 env 覆盖」在<b>放宽方向</b>上被架空；</li>
 *   <li><b>共享层已在同一个位置做同样的事</b>：{@code GoStyleOutboundDeadlineInterceptor}
 *       带 devh 的 {@code @GrpcGlobalClientInterceptor} ⇒ ⇒ 装在<b>每个</b>出向 devh 调用上
 *       ⇒ ⇒ 与调用点无关 ⇒ ⇒ 其 {@code effectiveCallOptions} 取
 *       <b>min(入向剩余纳秒, 客户端硬上界 cap-millis, 调用点自带 deadline)</b>
 *       ⇒ ⇒ <b>入向预算的传播一并由它承担</b> ⇒ ⇒ 删掉后<b>传播不丢</b>
 *       （且它用<b>纳秒</b>精度，比原先域侧的毫秒精度更贴近 Go 的 ctx）。</li>
 * </ul>
 * <p>⇒ ⇒ <b>Go 出处（形态同构）</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
 * {@code Timeout int64 json:",default=2000"} ⇒ {@code go-zero@v1.10.2/zrpc/client.go:70-71}
 * {@code if c.Timeout > 0 { opts = append(opts, WithTimeout(2000ms)) }}
 * ⇒ ⇒ Go 把上界加在<b>client 的 CallOptions</b> 上（⇒ 每个出向调用都带）
 * ⇒ ⇒ 与共享层拦截器<b>同位置同语义</b>。
 * <p>⇒ ⇒ 本域只配参数：{@code rag.protection.outbound-deadline.cap-millis=2000}
 * （{@code service/task/task-api/src/main/resources/application.yml} 的 {@code rag.protection} 段）。
 *
 * <h2>⚠️ retry：Go 侧没有，禁止加</h2>
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go:24-35}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但本模块用的是前者）。
 * 加 retry 会把「一次失败 → 一次 text/plain 响应」变成「重试 N 次后仍失败」，
 * 可观察的<b>调用次数与延迟</b>都变了。
 *
 * <h2>⚠️ 2000ms 不是配置项，是 go-zero 的 struct tag 默认</h2>
 * <pre>
 *   zrpc/config.go:31          RpcClientConf.Timeout int64 `json:",default=2000"`
 *   zrpc/internal/config.go:15  ClientMiddlewaresConf.Timeout bool `json:",default=true"`
 *   zrpc/internal/clientinterceptors/timeoutinterceptor.go:25   context.WithTimeout(ctx, t)
 * </pre>
 * {@code etc/task.yaml} <b>一个字都没写 Timeout</b>，所以 2s 只能从 tag 默认推出来，
 * 照 yaml 是推不出来的（AGENTS.md §3.3.1 的典型形态）。
 *
 * <h2>为什么是 min(2s, 请求剩余预算) 而不是固定 2s</h2>
 * Go 的 {@code context.WithTimeout(ctx, 2s)} 是<b>嵌在已有 ctx 之上</b>的：
 * HTTP 层已由 {@code observability.NewHTTPMiddleware} 建了 3s deadline
 * （{@code service/common/observability/http.go:53}），gRPC 这层再嵌 2s ⇒ <b>先到者生效</b>。
 */
@Component
public class TaskRpcClient {

    /**
     * Go {@code zrpc/config.go:31} 的 {@code json:",default=2000"} → 2000ms。
     *
     * <p>⚠️ <b>2026-10-31（P2-T04 · G2 lane）：本常量不再是运行时上界</b> ——
     * 运行时上界由共享层的 {@code GoStyleOutboundDeadlineInterceptor} 承担
     * （本域只配参数 {@code rag.protection.outbound-deadline.cap-millis=2000}）。
     * ⇒ ⇒ <b>本常量仍在</b>：它是<b>断言用的期望值</b>（守门核对「yml 绑出的值 == 本常量 == 2000」）。
     */
    static final long DEFAULT_CALL_TIMEOUT_MILLIS = 2000L;

    @GrpcClient("task-rpc")
    private TaskServiceGrpc.TaskServiceBlockingStub taskServiceBlockingStub;

    /**
     * 取用户任务进度 —— 对应 Go {@code gettasklogic.go:36-38}：
     *
     * <pre>
     *   rpcResp, err := l.svcCtx.TaskCli.GetTask(l.ctx, &amp;taskpb.GetTaskReq{UserId: uid})
     * </pre>
     *
     * <p>⚠️ <b>不吞异常、不做任何 status → 业务码转换</b>：Go 侧是
     * {@code if err != nil { return nil, err }} 原样透传（{@code gettasklogic.go:39-41}），
     * 由 {@code httpx.ErrorCtx} → {@code errcode.CodeFromGrpcError} 决定 HTTP 状态码。
     * 对比 like-api 的 {@code errmsg.NewErrCodeMsg(int(st.Code()), ...)}——
     * 那是<b>另一个域</b>的做法，照抄过来就是漂移。
     *
     * @param userId 已通过正整数校验的 uid（{@code GetTaskReq#userIdOrThrow()}）
     * @return task-rpc 的响应；<b>可能为 null</b>（见 {@code TaskGetService} 的处理）
     */
    public com.rag.task.task.proto.GetTaskResp getTask(long userId) {
        com.rag.task.task.proto.GetTaskReq request = com.rag.task.task.proto.GetTaskReq.newBuilder()
                .setUserId(userId)
                .build();
        // ⚠️ 刻意**不再**在这里套 deadline ⇒ 上界与入向传播都由共享层拦截器承担
        return taskServiceBlockingStub.getTask(request);
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    TaskServiceGrpc.TaskServiceBlockingStub rawStub() {
        return taskServiceBlockingStub;
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    void rawStubField(TaskServiceGrpc.TaskServiceBlockingStub stub) {
        this.taskServiceBlockingStub = stub;
    }
}