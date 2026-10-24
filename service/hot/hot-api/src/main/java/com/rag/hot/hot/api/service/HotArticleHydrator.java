package com.rag.hot.hot.api.service;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.logger.WithArticleID;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.dto.HotArticleCard;
import com.rag.hot.hot.api.metrics.HotApiMetrics;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.proto.HotArticleItem;
import io.opentelemetry.context.Context;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 并发补全 + 过滤 —— 对应 Go {@code getHotArticlesLogic.go:91-170} 的
 * {@code hydrateArticles} 与 {@code buildHotArticleItem}。
 *
 * <h2>它拥有三件「其它地方放不下」的事</h2>
 * <ol>
 *   <li><b>并发窗口 = 8</b>（Go {@code :93} 的 {@code sem := make(chan struct{}, 8)}）</li>
 *   <li><b>4 条 skip 分支 + 各自的 metric</b>（Go {@code :126-154}）</li>
 *   <li><b>skip vs fatal 的不对称</b>：这里失败是 best-effort，hot-rpc 失败才是 fatal</li>
 * </ol>
 * 这三件事必须能脱离 HTTP 单独回归，所以本类不依赖任何 Web 层类型。
 *
 * <h2>并发模型：Go 的 goroutine + channel semaphore 的 Java 等价物</h2>
 * Go 侧为 100 个 item <b>各起一个 goroutine</b>，用容量 8 的 channel 做信号量
 * （{@code :96-110}），最后 {@code wg.Wait()} 汇合。
 * <p>Java 侧的对应做法：<b>共享线程池 + 每请求一个 {@link Semaphore}(8)</b>，
 * 且<b>先 acquire 再 submit</b>：
 * <pre>
 *   for (i = 0; i &lt; n; i++) {
 *       gate.acquire();                      // ← 在途任务数被本请求的窗口卡住
 *       futures[i] = pool.submit(() -&gt; { try { ... } finally { gate.release(); } });
 *   }
 * </pre>
 * 为什么是「每请求一个 semaphore」而不是「全局 8 线程的池」：
 * Go 的信号量是<b>每次调用新建</b>的 ⇒ 并发请求之间<b>互不占用</b>，
 * 每个请求都享有完整的 8 并发。用一个 8 线程的全局池会让 N 个并发请求共享 8 个槽位
 * （即「每个请求 8/N 并发」），那是可观察的延迟差异。
 * <p>为什么「先 acquire 再 submit」而不是「全部 submit，让任务自己 acquire」：
 * 后者会让 100 个<b>平台线程</b>阻塞在信号量上（Go 的 goroutine 是廉价的，线程不是）。
 * 先 acquire 保证了「每个请求最多 8 个线程真正在飞」。
 *
 * <h2>⚠️ 结果必须按下标保序</h2>
 * Go 写的是 {@code hydrated[idx] = ...}（{@code :108}）—— 结果落在<b>输入下标</b>上，
 * 与 goroutine 的完成顺序无关。Java 侧若用「先完成的先 add」，同样 4 条被 skip 的输入
 * 会产出不同的 rank 分配 ⇒ 可观察差异。因此按数组下标落位、最后按下标过滤。
 *
 * <h2>⚠️ best-effort：4 条 skip 分支都不冒泡</h2>
 * Go 侧这一层<b>没有任何</b> {@code return nil, err}：出错只是让该条为 nil。
 * {@code hydrateArticles} 的返回值 {@code []types.HotArticleItem} 里用 {@code nil} 表示 skip。
 */
@Service
public class HotArticleHydrator {

    /**
     * Go {@code getHotArticlesLogic.go:29} {@code hydrationConcurrency = 8}。
     */
    public static final int HYDRATION_CONCURRENCY = 8;

    private final ArticleRpcClient articleRpcClient;
    private final HotApiMetrics metrics;
    private final LoggerFactoryBean logger;
    private final Executor hydrationExecutor;

    public HotArticleHydrator(ArticleRpcClient articleRpcClient,
                              HotApiMetrics metrics,
                              LoggerFactoryBean logger,
                              @Qualifier("hotHydrationExecutor") Executor hydrationExecutor) {
        this.articleRpcClient = articleRpcClient;
        this.metrics = metrics;
        this.logger = logger;
        this.hydrationExecutor = hydrationExecutor;
    }

    /**
     * 对 hot-rpc 返回的 TopK 做并发补全 —— 对应 Go {@code hydrateArticles}（{@code :91-124}）。
     *
     * @param hotItems         Go {@code hotResp.GetItems()}；元素可为 null（对应 Go 的 nil item）
     * @param deadlineNanos    请求的<b>绝对</b> deadline（{@link System#nanoTime()} 时基）；
     *                         {@code -1} 表示本次请求<b>没有</b> HTTP 超时预算
     *                         （对应 Go「没装 TimeoutHandler ⇒ ctx 上没有 deadline」）
     * @return 与输入<b>等长</b>的列表，元素为 {@code null} 表示该条被 skip；
     *         rank <b>尚未</b>计算（由 {@link HotRankPaginator#assignRanks} 统一做）
     */
    public List<HotArticleCard> hydrate(List<HotArticleItem> hotItems, long deadlineNanos) {
        if (hotItems == null || hotItems.isEmpty()) {
            return new ArrayList<>();
        }

        int n = hotItems.size();
        HotArticleCard[] hydrated = new HotArticleCard[n];
        Semaphore gate = new Semaphore(HYDRATION_CONCURRENCY);

        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final HotArticleItem hotItem = hotItems.get(i);
            try {
                gate.acquire();
            } catch (InterruptedException e) {
                // Go 侧对应情形是 ctx 被取消；此处保留中断标志并把剩余位置补 nil，
                // 让本请求按「部分补全」结束而不是整体失败。
                Thread.currentThread().interrupt();
                break;
            }
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    hydrated[idx] = buildHotArticleItem(hotItem, deadlineNanos);
                } finally {
                    gate.release();
                }
            }, hydrationExecutor);
        }

        try {
            CompletableFuture.allOf(nonNull(futures)).join();
        } catch (RuntimeException e) {
            // Go 侧的 wg.Wait() 不会失败（每个 goroutine 内部自己吞掉了所有错误）。
            // Java 侧理论上到不了这里（任务体不抛），但真到了也不能让整包 500：
            // best-effort 的语义是「补不全也给结果」，不是「补全失败就报错」。
            Thread.currentThread().interrupt();
        }

        List<HotArticleCard> ordered = new ArrayList<>(n);
        for (HotArticleCard card : hydrated) {
            ordered.add(card);
        }
        return ordered;
    }

    /**
     * 单条补全 —— 对应 Go {@code buildHotArticleItem}（{@code getHotArticlesLogic.go:126-170}）。
     *
     * @param deadlineNanos 请求的<b>绝对</b> deadline（nanoTime 时基）；{@code -1} = 无预算
     * @return 成功时的卡片；<b>任何</b> skip 分支都返回 {@code null}
     */
    HotArticleCard buildHotArticleItem(HotArticleItem hotItem, long deadlineNanos) {
        // 分支 ①：Go :127-130 —— nil item 或空 article_id
        if (hotItem == null || hotItem.getArticleId().isEmpty()) {
            metrics.observeArticleSkip(HotApiMetrics.SKIP_EMPTY_ARTICLE_ID);
            return null;
        }

        GetArticleResponse articleResp;
        try {
            articleResp = articleRpcClient.getPublicArticle(
                    hotItem.getArticleId(), remainingBudgetMs(deadlineNanos));
        } catch (RuntimeException e) {
            // 分支 ②：Go :137-142 —— RPC 报错。skip 该条 + 两条 metric + 业务错误日志。
            // ⚠️ 捕获 **RuntimeException** 而不是只捕 StatusRuntimeException：
            //    Go 的判据是 `rpcErr != nil`（client 返回的任何 error 都算），
            //    只捕 grpc 状态异常会让「超时 / 被 breaker 拒绝 / 序列化失败」等
            //    非 StatusRuntimeException 的失败直接冒泡，把 best-effort 变成整包 500。
            metrics.observeRpcError(HotApiMetrics.CALLEE_ARTICLE_RPC);
            metrics.observeArticleSkip(HotApiMetrics.SKIP_ARTICLE_RPC_ERROR);
            logQuietly(() -> logger.logBusinessErr(Context.current(),
                    HotErrorCode.ERROR_SERVER_COMMON.getCode(), e,
                    WithArticleID.of(hotItem.getArticleId())));
            return null;
        }

        // 分支 ③：Go :143-147 —— resp 或 article 为 nil
        if (articleResp == null || !articleResp.hasArticle()) {
            metrics.observeArticleSkip(HotApiMetrics.SKIP_ARTICLE_MISSING);
            logQuietly(() -> logger.logInfo(Context.current(),
                    "skip hot article because article detail is missing",
                    WithArticleID.of(hotItem.getArticleId())));
            return null;
        }

        Article article = articleResp.getArticle();
        // 分支 ④：Go :150-154 —— 状态不是 PUBLISHED
        // ⚠️ 这里读的是 article-rpc **公开投影**（public_only=true 的 frozen revision）的状态，
        //    不是可变源行。源行在 REVIEWING 时公开投影仍是 PUBLISHED，
        //    因此改稿中的文章不会从热榜里消失（Go _test.go:47-60 锁住这条）。
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            metrics.observeArticleSkip(HotApiMetrics.SKIP_ARTICLE_NOT_PUBLISHED);
            logQuietly(() -> logger.logInfo(Context.current(),
                    "skip hot article because article is not published",
                    WithArticleID.of(article.getId())));
            return null;
        }

        // 成功：HotScore 取自 hot-rpc（Go :158），其余 11 个字段取自 article 公开投影。
        // ⚠️ ArticleId 取的是 article.GetId()（Go :157），不是 hotItem.GetArticleId()。
        HotArticleCard card = new HotArticleCard();
        card.setArticleId(article.getId());
        // Go 的类型是 uint32，JSON 写无符号十进制；protobuf-java 把它映射成有符号 int
        // ⇒ 必须转回无符号，否则 ≥2^31 的热度分会写成负数（详见 HotArticleCard 类注释）。
        card.setHotScore(Integer.toUnsignedLong(hotItem.getHotScore()));
        card.setTitle(article.getTitle());
        card.setBrief(article.getBrief());
        card.setCoverImageUrl(article.getCoverImageUrl());
        card.setAuthorId(article.getAuthorId());
        card.setCreateTime(article.getCreateTime());
        card.setViewCount(article.getViewCount());
        card.setLikeCount(article.getLikeCount());
        card.setCommentCount(article.getCommentCount());
        card.setManualTypeTag(article.getManualTypeTag());
        // ⚠️ Go 侧 proto3 repeated 字段在无元素时是 nil slice ⇒ JSON 输出 null 而不是 []。
        //    protobuf-java 永远返回非 null 列表，必须显式还原成 null。
        card.setSecondaryTags(article.getSecondaryTagsCount() == 0
                ? null
                : new ArrayList<>(article.getSecondaryTagsList()));
        return card;
    }

    /**
     * L4 idiomatization：把请求的<b>绝对</b> deadline 折算成「这一次调用还剩多少毫秒」。
     *
     * <p><b>Go 出处（三段，缺一不可）</b>：
     * <pre>
     * // ① service/common/observability/http.go:56-57 —— 请求级**绝对** deadline
     * ctx, cancel := context.WithTimeout(r.Context(), timeout)
     * req := r.WithContext(ctx)
     *
     * // ② service/hot/api/internal/logic/hot/getHotArticlesLogic.go:132 —— 每次调用都带 ctx
     * articleResp, rpcErr := l.svcCtx.ArticleRpc.GetArticle(l.ctx, &amp;articleservice.GetArticleRequest{...})
     *
     * // ③ go-zero zrpc/client.go:70-71 + zrpc/internal/client.go —— 每次调用的实际 deadline
     * //    = min(ctx 的绝对 deadline, 发起时刻 + 2s)
     * </pre>
     *
     * <p><b>语义等价论证</b>：
     * <ol>
     *   <li>Go 的 ctx 上挂的是<b>绝对</b> deadline，被 100 个 goroutine <b>共享</b> ⇒
     *       <b>每一次</b>调用都在<b>自己的发起时刻</b>重新算「还能活多久」：
     *       {@code min(ctx.deadline, now+2s)}。</li>
     *   <li>本实现把<b>绝对</b> deadline 传进每个补全任务，并在<b>调用点</b>折算剩余毫秒 ⇒
     *       交给 {@code GoStyleGrpcCallDeadline} 的预算 = {@code min(2s, deadline-now)}
     *       ⇒ 实际 deadline = {@code now + min(2s, deadline-now)} = {@code min(now+2s, deadline)}
     *       ⇒ <b>与 Go 的公式逐字相同</b>。</li>
     *   <li>⚠️ <b>本轮之前这里是错的</b>：曾把「请求开始时取一次的剩余毫秒<b>快照</b>」传给所有补全 ⇒
     *       后发的补全拿到的是<b>全新的 2s</b>，<b>不被请求的绝对 deadline 卡住</b>。
     *       ⇒ 活体实测（U5-OPEN-1 隔离实验 E1）：Go 侧第 3 批补全死于 t≈2.98s（被请求 deadline 卡住），
     *       RAG 侧死于 t≈4.00s（自己的 2s）⇒ <b>差 1.03s</b>。
     *       ⇒ 连带后果：这两条补全的失败（DEADLINE_EXCEEDED）落进 client breaker 的
     *       <b>不同时间桶</b> ⇒ 熔断打开时机与 Go 不同 ⇒ 5xx 累积速率不同。</li>
     * </ol>
     *
     * <p>⚠️ <b>为什么参数是「绝对 deadline」而不是「剩余毫秒」</b>：
     * 剩余毫秒是<b>某一时刻</b>的量，传递它必然要「在某处取一次」⇒ 取一次就等于<b>冻结</b>。
     * Go 侧之所以没有这个问题，是因为 ctx 上挂的是<b>绝对</b> deadline。
     *
     * <p>⚠️ <b>精度</b>：本仓能拿到的只是 {@code GoStyleRequestDeadline#remainingMillis()}
     * （毫秒级、向下取整）⇒ 由它重建的绝对 deadline 比真实值<b>最多早 1ms</b>
     * ⇒ 即 RAG 这一侧<b>更保守</b>（不会比 Go 多活）。
     */
    static long remainingBudgetMs(long deadlineNanos) {
        if (deadlineNanos < 0L) {
            // 无 HTTP 超时预算 ⇒ 对应 Go「ctx 上没有 deadline」⇒ 只剩 zrpc 的 2s
            return -1L;
        }
        long remainNanos = deadlineNanos - System.nanoTime();
        if (remainNanos <= 0L) {
            // 预算已耗尽 ⇒ Go 侧 ctx 已到期，下游调用**立即失败**
            return 0L;
        }
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainNanos);
    }

    /**
     * 日志失败不得改变响应 —— Go 的 logger 不会让请求失败，
     * Java 侧任何一次日志异常都不该把「skip」变成 500
     * （与 {@code ArticleAuthorResolver} 的同款取舍）。
     */
    private void logQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException loggingFailure) {
            // 刻意吞掉
        }
    }

    /**
     * 剔除中断后未提交的空槽 —— {@code CompletableFuture.allOf} 不接受 null 元素。
     * 被剔除的位置在 {@code hydrated} 里保持 null，即「该条被 skip」，与 best-effort 语义一致。
     */
    private static CompletableFuture<Void>[] nonNull(CompletableFuture<Void>[] futures) {
        List<CompletableFuture<Void>> live = new ArrayList<>(futures.length);
        for (CompletableFuture<Void> future : futures) {
            if (future != null) {
                live.add(future);
            }
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] result = new CompletableFuture[live.size()];
        return live.toArray(result);
    }
}