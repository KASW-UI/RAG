package com.rag.hot.hot.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.api.dto.HotArticleCard;
import com.rag.hot.hot.api.dto.HotArticlesReq;
import com.rag.hot.hot.api.dto.HotArticlesResp;
import com.rag.hot.hot.api.metrics.HotApiMetrics;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.common.errmsg.HotErrorMessages;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 唯一业务用例的编排 —— 对应 Go {@code getHotArticlesLogic.go:54-89} 的 {@code GetHotArticles}。
 *
 * <h2>主干顺序（Go 逐行对照）</h2>
 * <pre>
 *   :55-58   defer metrics.ObserveRequest(route, started, err)     → try/finally + observeRequest
 *   :60      normalizePagination(req)                             → HotRankPaginator.normalize
 *   :62-69   HotRpc.GetHotArticles(TopK: 100) 失败 ⇒ FATAL       → observeRpcError + 5001
 *   :70-75   hotResp == nil                  ⇒ FATAL            → observeArticleSkip + 5001
 *   :77      hydrateArticles(hotResp.Items)                      → HotArticleHydrator.hydrate
 *   :78      total = len(items)                ← 过滤后、分页前
 *   :79-80   start,end := paginate(...); pagedItems := items[start:end]
 *   :82-88   组装 resp（Scope 恒 "rolling"）
 * </pre>
 *
 * <h2>⚠️ 本类最核心的一条：<b>fatal 与 best-effort 的不对称</b></h2>
 * 同一个「下游失败」概念，两层方向<b>相反</b>：
 * <table border="1">
 *   <caption>两层失败语义（不可统一）</caption>
 *   <tr><th>层</th><th>Go 分支</th><th>结果</th><th>HTTP</th></tr>
 *   <tr>
 *     <td>hot-rpc</td><td>{@code :65-69}</td>
 *     <td><b>fatal</b>：整包 5001</td><td>200 + {@code {code:5001,data:null}}</td>
 *   </tr>
 *   <tr>
 *     <td>article-rpc</td><td>{@code :137-142}（在 Hydrator 里）</td>
 *     <td><b>best-effort</b>：跳过该条，其余照常返回</td><td>200 + 正常信封</td>
 *   </tr>
 * </table>
 * 把二者统一（例如都抛异常）会让「article-rpc 抖动一次」从「少一条」变成「整包失败」——
 * 这是<b>可用性</b>级别的漂移，不只是错误码。
 *
 * <h2>⚠️ {@code hotResp == nil} 在 Java 侧的对应形态</h2>
 * Go 的 {@code rpcErr == nil && hotResp == nil} 是一个独立分支（{@code :70-75}）：
 * gRPC 不会返回 nil 响应，所以它本质是「防御性分支」。
 * protobuf-java 同样不返回 null，因此 {@code null} 在 Java 侧只可能来自 mock / 旁路实现。
 * <b>仍然保留该分支</b>：它带着一个<b>非空的 metric 副作用</b>
 * （{@code hot_api_article_skip_total{reason="hot_rpc_nil_response"}}），
 * 删掉会让「这段防御曾存在过」这一事实从指标侧消失。
 */
@Service
public class HotArticleQueryService {

    private final HotRpcClient hotRpcClient;
    private final HotArticleHydrator hydrator;
    private final HotApiMetrics metrics;
    private final LoggerFactoryBean logger;

    public HotArticleQueryService(HotRpcClient hotRpcClient,
                                  HotArticleHydrator hydrator,
                                  HotApiMetrics metrics,
                                  LoggerFactoryBean logger) {
        this.hotRpcClient = hotRpcClient;
        this.hydrator = hydrator;
        this.metrics = metrics;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code GetHotArticles}（{@code getHotArticlesLogic.go:54-89}）。
     *
     * @throws BusinessException 两条 fatal 分支 → 5001（HTTP 仍为 200）
     */
    public HotArticlesResp getHotArticles(HotArticlesReq req) {
        long startedNanos = System.nanoTime();
        boolean success = false;
        try {
            int[] pageAndSize = HotRankPaginator.normalize(req);
            int page = pageAndSize[0];
            int pageSize = pageAndSize[1];

            // 剩余预算在**请求线程**上取一次，显式传给下游 ——
            // Go 侧是 ctx 被 100 个 goroutine 隐式共享（getHotArticlesLogic.go:128）。
            long remainingBudgetMs = GoStyleRequestDeadline.remainingMillis();

            GetHotArticlesResponse hotResp = fetchTopK(remainingBudgetMs);

            List<HotArticleCard> hydrated = hydrator.hydrate(hotResp.getItemsList(), remainingBudgetMs);
            List<HotArticleCard> ranked = HotRankPaginator.assignRanks(hydrated);

            HotArticlesResp resp = HotRankPaginator.slice(ranked, page, pageSize);
            success = true;
            return resp;
        } finally {
            metrics.observeRequest(HotApiMetrics.ROUTE, startedNanos, success);
        }
    }

    /**
     * Go {@code :62-75} 的两条 fatal 分支。
     *
     * <p>⚠️ {@code TopK} 是<b>硬编码</b> {@link HotRankPaginator#MAX_HOT_ARTICLES}（Go {@code :63}），
     * 请求里的 {@code page} / {@code page_size} 影响不到它 ——
     * 窗口永远是 100 条，分页只在这 100 条的过滤结果上切。
     */
    private GetHotArticlesResponse fetchTopK(long remainingBudgetMs) {
        GetHotArticlesResponse hotResp;
        try {
            hotResp = hotRpcClient.getHotArticles(HotRankPaginator.MAX_HOT_ARTICLES, remainingBudgetMs);
        } catch (StatusRuntimeException e) {
            metrics.observeRpcError(HotApiMetrics.CALLEE_HOT_RPC);
            throw fatal(e);
        }

        // Go :70-75 —— 防御性分支：响应指针为 nil。
        if (hotResp == null) {
            metrics.observeArticleSkip(HotApiMetrics.SKIP_HOT_RPC_NIL_RESPONSE);
            throw fatal(null);
        }
        return hotResp;
    }

    /**
     * Go {@code hotcommon.NewErrCode(hotcommon.ErrorServerCommon)} 的等价物。
     *
     * <p>Go 的 {@code SetErrorHandler}（{@code hotcenter.go:53-66}）对 {@code *hotcommon.CodeError}
     * 取出 {@code Code=5001 / Msg=GetErrMsg(5001)="internal server error"}，返回 <b>HTTP 200</b>。
     * 两条 fatal 分支都走这一个出口 ⇒ 用 {@link BusinessException}
     * 携带 code/msg，由 {@code GlobalExceptionHandler} 产出同样的信封。
     */
    private com.rag.common.exception.BusinessException fatal(Throwable cause) {
        int code = HotErrorCode.ERROR_SERVER_COMMON.getCode();
        // Go :67/:73 都会记一条业务错误日志；日志失败不得改变响应（见 HotArticleHydrator 同款取舍）。
        try {
            if (cause != null) {
                logger.logBusinessErr(Context.current(), code, cause);
            } else {
                logger.logInfo(Context.current(),
                        "hot rpc returned a nil response for route " + HotApiMetrics.ROUTE);
            }
        } catch (RuntimeException loggingFailure) {
            // 刻意吞掉
        }
        return new BusinessException(code, HotErrorMessages.getMsg(code));
    }
}