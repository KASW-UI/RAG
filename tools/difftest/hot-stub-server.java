package tools.difftest;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import com.rag.hot.hot.proto.HotServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  HotStubServer —— hot 域端到端对拍的**下游桩**
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 【它替代的是什么】
 *   hot-api 的请求链路是三段：
 *       HTTP → hot-rpc.GetHotArticles(TopK=100) → article-rpc.GetArticle(每条一个)
 *   若要让「两侧 hot-api 的 HTTP 响应逐字节相同」这件事有判别力，下游必须
 *   **两侧完全一致**。真起 Go hot-rpc + RAG hot-rpc 会引入两个新的不确定源：
 *     ① 两侧 Kafka 消费进度不可控 ⇒ 热榜内容与顺序可能天然不同；
 *     ② 两侧 Redis 快照写入时机不同 ⇒ 同一时刻读到的榜单可能不同。
 *   那不是 hot-api 的漂移，是「对拍环境」的漂移。
 *
 *   ⇒ 本桩把下游**钉死**：两侧各起一个**同构**的桩（配置完全相同），
 *     返回**固定**的热榜与文章数据 ⇒ 任何字节差异都 100% 归因于 hot-api 本身。
 *
 * 【为什么是「同构双桩」而不是「一个共用桩」】
 *   共用一个桩无法区分「哪一侧发起了调用」。同构双桩各写各的调用日志 ⇒
 *   除了比 HTTP 响应体，还能比**下游调用序列**（比了就知道两侧调的
 *   方法、参数、条数是否一致 —— 例如两侧都跳过空 article_id 的那条）。
 *
 * 【为什么用 Java 写而不是 Go】
 *   RAG 侧已有 protoc 生成的 `HotServiceGrpc` / `ArticleServiceGrpc`，
 *   而 Go 侧的 proto 与 RAG 侧**逐字节同源**（§3.4：proto 是复制不是翻译，
 *   `check-proto-static.sh` 校验字段号 / message 名 / RPC 签名）⇒ 同一个桩
 *   服务两侧，且 Go 侧 zrpc client 不校验 proto 版本 ⇒ wire 层完全兼容。
 *
 * 【⚠️ 不进 Maven module 的原因】
 *   与 `grpc-dual-port-runner.java` / `probe-jdbc-cancel.java` 同款：
 *   「跑一次、留档、退出」的取证程序，不进业务 module、不污染 RAG 代码、
 *   不被回归网引用、不参与 `mvn verify`。用 javac 单文件编译 + `java -cp` 跑。
 *
 * 【行为开关：模式文件】
 *   行为由 `-Dstub.mode.file` 指向的**文本文件**驱动，**每次调用都重读**。
 *   ⇒ 对拍脚本可以在**不重启任何服务**的前提下切换下游行为
 *     （fatal / best-effort / 空榜 / 超时…），两侧服务全程在线。
 *   文件内容是单个 token，见 {@link #MODE_*} 常量。
 *
 * 【调用日志】
 *   每次下游调用追加一行到 `-Dstub.call.log`：
 *       GetHotArticles top_k=100
 *       GetArticle article_id=a-1 public_only=true incr_view=false
 *       GetArticle article_id=a-4 -> ERROR(INTERNAL)
 *   ⚠️ 补全是**并发**的（Go 8 个 goroutine / Java 每请求 Semaphore(8)）
 *      ⇒ 日志顺序天然不确定 ⇒ 对拍时按**多重集**（排序后）比较，不按顺序比。
 *
 * 【用法】
 *   javac -d <out> -cp "<hot-proto-classes>:<article-proto-classes>:<libs>" hot-stub-server.java
 *   java  -cp "<out>:..." -Dstub.port=19903 -Dstub.mode.file=/tmp/... -Dstub.call.log=/tmp/... \
 *         tools.difftest.HotStubServer
 */
// ⚠️ 包级私有（不是 public）：javac 要求 public 顶层类与文件名同名，
//    而本仓约定取证程序用 kebab-case 文件名（grpc-dual-port-runner.java 同款）。
final class HotStubServer {

    // ── 模式 token（模式文件里的单个词）────────────────────────────────────────
    /** 默认：5 条热榜；a-1/a-2/a-3 补全成功，a-4 补全**报错**（best-effort skip），a-5 空 id（不调用下游）。 */
    public static final String MODE_NORMAL = "normal";
    /** 5 条热榜；a-1..a-4 **全部**补全成功（与 normal 的差别 = 「总数减 1」的对照）。 */
    public static final String MODE_ALL_OK = "all-ok";
    /** hot-rpc 直接报错 ⇒ 触发 Go 的 fatal 分支（整包 5001）。 */
    public static final String MODE_HOT_RPC_ERROR = "hot-rpc-error";
    /** hot-rpc 返回 0 条 ⇒ 空榜（items:[] / total:0）。 */
    public static final String MODE_HOT_RPC_EMPTY = "hot-rpc-empty";
    /** article-rpc 对**所有** id 报错 ⇒ 全部被 skip ⇒ 仍然空榜（但走的是 best-effort 路径）。 */
    public static final String MODE_ARTICLE_ERROR = "article-error";
    /** article-rpc 返回「没有 article」的响应 ⇒ 走 skip 分支③。 */
    public static final String MODE_ARTICLE_MISSING = "article-missing";
    /** article-rpc 返回 DRAFT（非 PUBLISHED）⇒ 走 skip 分支④。 */
    public static final String MODE_ARTICLE_DRAFT = "article-draft";
    /** 20 条热榜 + article-rpc 睡 30s ⇒ 把请求拖过 HTTP 3s 超时 ⇒ 触发 503 分支。 */
    public static final String MODE_ARTICLE_SLOW = "article-slow";

    /** 慢模式下返回的热榜条数 —— 8 并发 × 2s gRPC deadline ⇒ 需 > 8 条才能撑过 3s HTTP 超时。 */
    private static final int SLOW_ITEM_COUNT = 20;

    /** 慢模式下 article-rpc 的睡眠时长（远大于 3s HTTP 超时与 2s gRPC deadline）。 */
    private static final long SLOW_SLEEP_MS = 30_000L;

    private static final Path MODE_FILE;
    private static final Path CALL_LOG;
    /**
     * 带时间戳的**事件**日志。
     *
     * <p>⚠️ <b>为什么与 CALL_LOG 分开</b>：CALL_LOG 要被两侧**逐行比对**（跨侧调用序列），
     * 里面**不能**带时间戳（两侧时间必然不同）⇒ 计时证据另开一份。
     */
    private static final Path EVENT_LOG;
    private static final Object CALL_LOG_LOCK = new Object();

    static {
        MODE_FILE = Paths.get(System.getProperty("stub.mode.file", "/tmp/hot-difftest/stub-mode"));
        CALL_LOG = Paths.get(System.getProperty("stub.call.log", "/tmp/hot-difftest/calls.log"));
        EVENT_LOG = Paths.get(System.getProperty("stub.event.log",
                System.getProperty("stub.call.log", "/tmp/hot-difftest/calls.log") + ".events"));
    }

    private HotStubServer() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("stub.port", 19903);
        Server server = ServerBuilder.forPort(port)
                .addService(new HotService())
                .addService(new ArticleService())
                .build()
                .start();
        System.out.println("READY hot-stub-server on 127.0.0.1:" + port
                + " mode-file=" + MODE_FILE + " call-log=" + CALL_LOG);
        System.out.flush();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdownNow();
            try {
                server.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        server.awaitTermination();
    }

    // ── 模式读取（每次调用都重读 ⇒ 无需重启即可切行为）───────────────────────────
    private static String mode() {
        try {
            String raw = Files.readString(MODE_FILE, StandardCharsets.UTF_8).trim();
            return raw.isEmpty() ? MODE_NORMAL : raw;
        } catch (IOException e) {
            return MODE_NORMAL;
        }
    }

    private static void record(String line) {
        synchronized (CALL_LOG_LOCK) {
            try {
                Files.writeString(CALL_LOG, line + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // 取证辅助程序：写日志失败不得影响桩的应答
            }
        }
    }

    /**
     * 记一条**带时间戳**的事件（用于计时取证，如「客户端断连后 gRPC 何时被取消」）。
     *
     * <p>格式：{@code <epochMillis>\t<事件描述>}。
     */
    private static void event(String line) {
        synchronized (CALL_LOG_LOCK) {
            try {
                Files.writeString(EVENT_LOG, System.currentTimeMillis() + "\t" + line + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // 同上
            }
        }
    }

    // ── hot-rpc 桩 ───────────────────────────────────────────────────────────
    /**
     * {@code hot.HotService} —— 只实现 {@code GetHotArticles}（hot-api 唯一调用的那个）。
     *
     * <p>⚠️ {@code TopK} 是**真实生效**的：返回条数 = {@code min(topK, 可用条数)}。
     * 这不是装饰 —— 若某一侧把 TopK 写错（比如传 20 而不是 100），
     * 返回条数就会不同 ⇒ HTTP 响应的 {@code total} 立刻不同 ⇒ 对拍能抓到。
     */
    private static final class HotService extends HotServiceGrpc.HotServiceImplBase {

        @Override
        public void getHotArticles(GetHotArticlesRequest request,
                                   StreamObserver<GetHotArticlesResponse> observer) {
            int topK = request.getTopK();
            record("GetHotArticles top_k=" + topK);
            String m = mode();

            if (MODE_HOT_RPC_ERROR.equals(m)) {
                observer.onError(Status.UNAVAILABLE
                        .withDescription("stub: hot-rpc forced failure").asRuntimeException());
                return;
            }
            if (MODE_HOT_RPC_EMPTY.equals(m)) {
                observer.onNext(GetHotArticlesResponse.getDefaultInstance());
                observer.onCompleted();
                return;
            }

            List<HotArticleItem> items = new ArrayList<>();
            if (MODE_ARTICLE_SLOW.equals(m)) {
                // 慢模式：条数刻意 > 并发窗口 8 ⇒ 3 批 × 2s gRPC deadline > 3s HTTP 超时
                for (int i = 1; i <= SLOW_ITEM_COUNT; i++) {
                    items.add(HotArticleItem.newBuilder()
                            .setArticleId(String.format("slow-%02d", i))
                            .setHotScore(1000 + i)
                            .build());
                }
            } else {
                // 固定 5 条。⚠️ 顺序是**契约的一部分**（Go 按输入下标落位、按序过滤）
                items.add(item("a-1", -1));   // uint32 最大值 4294967295
                items.add(item("a-2", 0));
                items.add(item("a-3", 123456));
                items.add(item("a-4", 7));
                items.add(item("", 9));        // 空 article_id ⇒ 两侧都在**调下游之前**跳过
            }

            // ⚠️ TopK 真实生效：返回条数 = min(topK, 可用条数)
            GetHotArticlesResponse.Builder resp = GetHotArticlesResponse.newBuilder();
            int added = 0;
            for (HotArticleItem it : items) {
                if (added >= topK) {
                    break;
                }
                resp.addItems(it);
                added++;
            }
            observer.onNext(resp.build());
            observer.onCompleted();
        }

        private static HotArticleItem item(String articleId, int hotScore) {
            return HotArticleItem.newBuilder()
                    .setArticleId(articleId)
                    .setHotScore(hotScore)   // ⚠️ uint32 在 protobuf-java 里是有符号 int
                    .build();
        }
    }

    // ── article-rpc 桩 ───────────────────────────────────────────────────────
    /**
     * {@code article.ArticleService} —— 只实现 {@code GetArticle}（hot-api 唯一调用的那个）。
     *
     * <p>返回的 {@code Article} 的字段值刻意覆盖了台账 CASE-15 系列的每条序列化边界：
     * <ul>
     *   <li>{@code a-1}：{@code hot_score = 4294967295}（uint32 上界）＋ 标题含
     *       {@code <b>} / {@code &} / 双引号 / 反斜杠 / 换行 ⇒ 测 HTML 转义与转义字符；</li>
     *   <li>{@code a-2}：{@code secondary_tags} 为空 ⇒ Go 输出 {@code null}、Java 必须也输出
     *       {@code null}（不是 {@code []}）；{@code hot_score = 0}；</li>
     *   <li>{@code a-3}：单元素 {@code secondary_tags}；</li>
     *   <li>{@code a-4}：默认模式下**报错**（best-effort skip）；</li>
     * </ul>
     */
    private static final class ArticleService extends ArticleServiceGrpc.ArticleServiceImplBase {

        @Override
        public void getArticle(GetArticleRequest request,
                               StreamObserver<GetArticleResponse> observer) {
            String id = request.getArticleId();
            String m = mode();
            boolean publicOnly = request.getPublicOnly();
            boolean incrView = request.getIncrView();

            if (MODE_ARTICLE_SLOW.equals(m)) {
                record("GetArticle article_id=" + id + " public_only=" + publicOnly
                        + " incr_view=" + incrView + " -> SLOW");
                // ── 计时取证：这次调用**何时**被取消？────────────────────────────────
                // 用途：判定「客户端断连 ⇒ 在飞的 gRPC 是否被取消」（DRIFT-HOTAPI 的 D3）。
                //   · Go 侧：HTTP 请求 ctx 被取消 ⇒ ctx 一路传到 zrpc ⇒ gRPC 立刻取消
                //            ⇒ 事件时间 ≈ 客户端断开的那一刻；
                //   · RAG 侧：Servlet 断连**不**取消在飞的阻塞 gRPC ⇒ 只能等到 2s 调用
                //            deadline 才结束 ⇒ 事件时间 ≈ deadline。
                // ⇒ **时间戳**就能把两者分开（不能只看「有没有被取消」——
                //    两边最终都会被取消，只是**原因与时刻**不同）。
                final java.util.concurrent.atomic.AtomicBoolean cancelled =
                        new java.util.concurrent.atomic.AtomicBoolean(false);
                io.grpc.Context rpcCtx = io.grpc.Context.current();
                // ⚠️ grpc-java 的签名是 addListener(Context.CancellationListener, Executor)，
                //    且 CancellationListener 的方法名是 cancelled(Context) —— **带一个 Context 参数**
                rpcCtx.addListener((io.grpc.Context.CancellationListener) (ctxOnCancel) -> {
                    if (cancelled.compareAndSet(false, true)) {
                        event("ARTICLE_CALL_CANCELLED id=" + id);
                    }
                }, Runnable::run);
                event("ARTICLE_CALL_ENTER id=" + id);
                try {
                    Thread.sleep(SLOW_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (cancelled.get()) {
                    // 已被取消：再写响应会抛异常（grpc-java 对已取消的 call 禁止 send）
                    event("ARTICLE_CALL_ABORTED id=" + id);
                    return;
                }
                event("ARTICLE_CALL_EXIT id=" + id);
                observer.onNext(GetArticleResponse.newBuilder()
                        .setArticle(articleOf(id, ArticleStatus.PUBLISHED)).build());
                observer.onCompleted();
                return;
            }

            if (MODE_ARTICLE_ERROR.equals(m) || (MODE_NORMAL.equals(m) && "a-4".equals(id))) {
                record("GetArticle article_id=" + id + " public_only=" + publicOnly
                        + " incr_view=" + incrView + " -> ERROR(INTERNAL)");
                observer.onError(Status.INTERNAL
                        .withDescription("stub: article-rpc forced failure").asRuntimeException());
                return;
            }

            if (MODE_ARTICLE_MISSING.equals(m)) {
                record("GetArticle article_id=" + id + " public_only=" + publicOnly
                        + " incr_view=" + incrView + " -> MISSING");
                observer.onNext(GetArticleResponse.getDefaultInstance());
                observer.onCompleted();
                return;
            }

            record("GetArticle article_id=" + id + " public_only=" + publicOnly
                    + " incr_view=" + incrView + " -> OK");

            ArticleStatus status = MODE_ARTICLE_DRAFT.equals(m)
                    ? ArticleStatus.DRAFT
                    : ArticleStatus.PUBLISHED;
            observer.onNext(GetArticleResponse.newBuilder()
                    .setArticle(articleOf(id, status))
                    .build());
            observer.onCompleted();
        }

        /**
         * 构造文章 —— ⚠️ {@code id} 用<b>请求里的</b> id 回填，
         * 因为 Go 的 hot-api 取的是 {@code article.GetId()}（不是 hot-rpc 那个 id）。
         */
        private static Article articleOf(String id, ArticleStatus status) {
            Article.Builder b = Article.newBuilder()
                    .setId(id)
                    .setTitle(titleOf(id))
                    .setBrief(briefOf(id))
                    .setMarkdownContent("正文 " + id)
                    .setCoverImageUrl("https://cdn.example.com/" + id + ".png?w=100&h=200")
                    .setManualTypeTag("评测")
                    .setAuthorId("u-" + id)
                    .setCreateTime(1700000000000L + Math.abs(id.hashCode()))
                    .setUpdateTime(1700000009999L)
                    .setStatus(status)
                    .setViewCount(11)
                    .setLikeCount(22)
                    .setCommentCount(33)
                    .setShareCount(44);
            switch (id) {
                case "a-1":
                    b.addSecondaryTags("alpha").addSecondaryTags("beta");
                    break;
                case "a-2":
                    // ⚠️ 刻意**不加** secondary_tags ⇒ 空 repeated
                    //    ⇒ Go 的 nil slice ⇒ JSON null；Java 必须显式还原成 null
                    break;
                case "a-3":
                    b.addSecondaryTags("gamma");
                    break;
                default:
                    b.addSecondaryTags("delta");
                    break;
            }
            return b.build();
        }

        private static String titleOf(String id) {
            switch (id) {
                // ⚠️ 含 HTML 标签、& 、双引号、反斜杠、换行 —— 测 JSON 转义
                case "a-1":
                    return "第一篇 <b>加粗</b> & \"引号\" \\ 反斜杠";
                case "a-2":
                    return "第二篇";
                case "a-3":
                    return "第三篇 \"引号\" 与 反斜杠 \\ 和换行\n第二行";
                default:
                    return "第四篇";
            }
        }

        private static String briefOf(String id) {
            switch (id) {
                case "a-1":
                    return "摘要 & <i>斜体</i>";
                case "a-2":
                    return "";
                case "a-3":
                    return "第三条摘要";
                default:
                    return "第四条摘要";
            }
        }
    }
}
