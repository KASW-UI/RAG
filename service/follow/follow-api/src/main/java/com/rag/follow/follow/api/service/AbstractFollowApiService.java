package com.rag.follow.follow.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.follow.follow.api.client.FollowRpcClient;
import com.rag.follow.follow.api.metrics.FollowApiMetrics;
import com.rag.follow.follow.proto.FollowServiceGrpc;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

/**
 * 8 个 Service 的公共骨架 —— 对应 Go 侧<b>每个 logic 文件都重复一遍</b>的那三段样板。
 *
 * <p><b>Go 侧的重复形态（8 个文件逐条相同）</b>：
 * <pre>
 * const route = "/follow/v1/xxx"
 * started := time.Now()
 * defer func() { metrics.ObserveRequest(route, started, err) }()
 *
 * userID, err := extractUserID(l.ctx)
 * if err != nil { metrics.ObserveReject(route, "user_id_missing"); return nil, err }
 * ...
 * rpcResp, rpcErr := l.svcCtx.FollowRpc.Xxx(l.ctx, ...)
 * if rpcErr != nil { return nil, codeFromRPCError(rpcErr) }
 * if rpcResp.Code != int32(Success) { return nil, NewErrCodeMsg(...) }
 * </pre>
 *
 * <p><b>把这三段收进基类是 L4（实现机制）层面的自由度</b>：Go 复制 8 份是 goctl 的生成风格，
 * 不是行为语义。反过来说，这三段<b>必须</b>由同一个机制承担，否则就会出现
 * 「某个端点漏了 reject 埋点」这类只在监控上可见的分叉。
 *
 * <p><b>⚠️ {@code invoke} 的 fail 判定与 Go 的具名返回值等价</b>：
 * Go 判的是 logic 方法<b>返回的 {@code err} 是否为 nil</b>，
 * 所以「返回了业务失败码」也是 {@code result="fail"}。
 * 本实现用 try/finally + 布尔标志表达同一时序：
 * 成功时置 {@code false}，任何异常路径（业务异常 / gRPC 异常 / NPE）都保持 {@code true}。
 */
public abstract class AbstractFollowApiService {

    /** Go 8 个 logic 里 {@code metrics.ObserveReject(route, "user_id_missing")} 的字面量。 */
    protected static final String REASON_USER_ID_MISSING = "user_id_missing";

    /** Go 两处归属校验里 {@code metrics.ObserveReject(route, "forbidden_scope")} 的字面量。 */
    protected static final String REASON_FORBIDDEN_SCOPE = "forbidden_scope";

    private final FollowRpcClient rpcClient;
    private final FollowApiSupport support;
    private final FollowApiMetrics metrics;

    protected AbstractFollowApiService(FollowRpcClient rpcClient,
                                       FollowApiSupport support,
                                       FollowApiMetrics metrics) {
        this.rpcClient = rpcClient;
        this.support = support;
        this.metrics = metrics;
    }

    /**
     * 包裹一次 logic 调用，落地 Go 的 {@code ObserveRequest} 计时与成败计数。
     *
     * @param route Go 各 logic 里的 {@code const route} 字面量
     * @param body  业务体（内含身份解析 / 归属校验 / rpc 调用 / 响应转换）
     * @param <T>   返回类型
     */
    protected <T> T invoke(String route, Supplier<T> body) {
        long started = System.nanoTime();
        boolean failed = true;
        try {
            T result = body.get();
            failed = false;
            return result;
        } finally {
            metrics.observeRequest(route, failed, System.nanoTime() - started);
        }
    }

    /**
     * 解析身份并在失败时补一条 {@code user_id_missing} 埋点。
     *
     * <p>顺序与 Go 一致：<b>先</b>记 reject，<b>后</b>返回 error
     * （{@code blocklogic.go:38-43} 的两行）—— 埋点在 return 之前，顺序不可颠倒。
     *
     * @throws BusinessException code = 5004（HTTP 恒为 200）
     */
    protected long requireIdentity(String route, HttpServletRequest httpRequest) {
        try {
            return support.userId(httpRequest);
        } catch (BusinessException e) {
            metrics.observeReject(route, REASON_USER_ID_MISSING);
            throw e;
        }
    }

    /**
     * 归属校验并在失败时补一条 {@code forbidden_scope} 埋点。
     *
     * <p>Go 出处：{@code getblocklistlogic.go:46-50} 与
     * {@code getrecommendationslogic.go:46-50} 各一份，{@code metrics.ObserveReject} 在前。
     *
     * @return 校验通过后的目标用户 id（必然等于 {@code currentUserID}）
     * @throws BusinessException code = 5005
     */
    protected long requireSelfScope(String route, long currentUserID, Long requestedUserID) {
        try {
            return support.requireSelfScope(currentUserID, requestedUserID);
        } catch (BusinessException e) {
            metrics.observeReject(route, REASON_FORBIDDEN_SCOPE);
            throw e;
        }
    }

    protected FollowServiceGrpc.FollowServiceBlockingStub rpc() {
        return rpcClient.followRpc();
    }

    protected FollowApiSupport support() {
        return support;
    }
}