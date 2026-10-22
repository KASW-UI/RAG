package com.rag.article.article.rpc.service.impl;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.CreateArticleRequest;
import com.rag.article.article.proto.CreateArticleResponse;
import com.rag.article.article.proto.DeleteArticleRequest;
import com.rag.article.article.proto.DeleteArticleResponse;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.article.article.proto.ListArticlesRequest;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.article.article.proto.UpdateArticleRequest;
import com.rag.article.article.proto.UpdateArticleResponse;
import com.rag.article.article.proto.UploadFileRequest;
import com.rag.article.article.proto.UploadFileResponse;
import com.rag.article.article.rpc.exception.ArticleExceptions;
import com.rag.article.article.rpc.service.ArticleDomainService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * ArticleService 的 gRPC 实现 —— 6 个 RPC 方法的 thin wrapper。
 *
 * <p>对应 Go {@code service/article/rpc/internal/server/article_service_server.go}
 * （goctl 生成的 {@code ArticleServiceServer} + svc 注入的 Logic）。
 *
 * <h2>🔴 {@link #invoke} 的异常分派是 L1 契约，不可简化</h2>
 * Go 的每个 logic 有<b>两类</b>返回：
 * <table border="1">
 *   <caption>两类返回 → 客户端可见的 gRPC code</caption>
 *   <tr><th>Go 返回</th><th>grpc-go 转换</th><th>api 侧映射</th><th>客户端业务码</th></tr>
 *   <tr><td>{@code status.Error(codes.NotFound, …)}</td><td>NOT_FOUND</td>
 *       <td>→ {@code ErrorArticleNone}</td><td><b>1002</b></td></tr>
 *   <tr><td>{@code status.Error(codes.PermissionDenied, …)}</td><td>PERMISSION_DENIED</td>
 *       <td>→ {@code ErrorArticleForbidden}</td><td><b>1006</b></td></tr>
 *   <tr><td>{@code status.Error(codes.Internal, …)}</td><td>INTERNAL</td>
 *       <td>→ {@code ErrorServerCommon}</td><td><b>5001</b></td></tr>
 *   <tr><td>{@code status.Error(codes.InvalidArgument, …)}</td><td>INVALID_ARGUMENT</td>
 *       <td>→ {@code default}</td><td><b>1015</b></td></tr>
 *   <tr><td><b>裸 {@code err}</b>（GORM/MinIO 原始错误）</td><td><b>UNKNOWN</b></td>
 *       <td>→ {@code default}</td><td><b>1015</b></td></tr>
 * </table>
 * ⇒ 最后两行的 code 都是 {@code default} 分支，但<b>必须分别保持</b>：
 * <ul>
 *   <li>{@link ArticleExceptions.Rpc} ⇒ 用它自带的 code 转发；</li>
 *   <li>{@link ArticleExceptions.Raw} ⇒ <b>映射成 {@code UNKNOWN}</b>，
 *       <b>不是</b> {@code INTERNAL}。若把裸错误也包成 INTERNAL，客户端业务码会从 <b>1015 变成 5001</b>。
 *   <li>其它 {@link StatusRuntimeException}（如 {@code SqlTimeoutStatusTranslator} 产出的
 *       {@code DEADLINE_EXCEEDED}）⇒ <b>原样透传其 code</b>，
 *       对应 grpc-go 的 {@code status.FromError}「已是 status 就保留 code」规则；
 *   <li>其余 {@link RuntimeException}（编程错误等）⇒ {@code UNKNOWN}，
 *       对应 Go 的「非 status error 一律 UNKNOWN」规则。</li>
 * </ul>
 * <p>把 {@link StatusRuntimeException} 单列，是因为它<b>已经带 status</b>：
 * 若并入最后一档，等于在 gRPC 边界把上游已经翻译好的 code 覆盖掉，
 * 那条翻译链（PG {@code 57014 query_canceled} → {@code DEADLINE_EXCEEDED}）就白做了。
 */
@GrpcService
public class ArticleServiceGrpcImpl extends ArticleServiceGrpc.ArticleServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleServiceGrpcImpl.class);

    private final ArticleDomainService domain;

    public ArticleServiceGrpcImpl(ArticleDomainService domain) {
        this.domain = domain;
    }

    @Override
    public void createArticle(CreateArticleRequest request,
                               StreamObserver<CreateArticleResponse> responseObserver) {
        invoke(() -> domain.createArticle(request), responseObserver);
    }

    @Override
    public void getArticle(GetArticleRequest request,
                           StreamObserver<GetArticleResponse> responseObserver) {
        invoke(() -> domain.getArticle(request), responseObserver);
    }

    @Override
    public void updateArticle(UpdateArticleRequest request,
                              StreamObserver<UpdateArticleResponse> responseObserver) {
        invoke(() -> domain.updateArticle(request), responseObserver);
    }

    @Override
    public void deleteArticle(DeleteArticleRequest request,
                              StreamObserver<DeleteArticleResponse> responseObserver) {
        invoke(() -> domain.deleteArticle(request), responseObserver);
    }

    @Override
    public void listArticles(ListArticlesRequest request,
                             StreamObserver<ListArticlesResponse> responseObserver) {
        invoke(() -> domain.listArticles(request), responseObserver);
    }

    @Override
    public void uploadFile(UploadFileRequest request,
                           StreamObserver<UploadFileResponse> responseObserver) {
        invoke(() -> domain.uploadFile(request), responseObserver);
    }

    /**
     * 把领域调用包成 {@link StreamObserver} 的 onNext/onCompleted/onError。
     *
     * <p>⚠️ <b>三类异常三条去向</b>，见类级表格：
     * {@link ArticleExceptions.Rpc} 按自带 code；{@link ArticleExceptions.Raw} 与其它
     * {@link RuntimeException} 走 {@code UNKNOWN}；{@link Error} 不捕获
     * （让容器线程的默认处理接管，与 grpc-java 默认行为一致）。
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (ArticleExceptions.Rpc e) {
            observer.onError(new StatusRuntimeException(
                    Status.fromCode(e.getCode()).withDescription(e.getMessage()).withCause(e)));
        } catch (StatusRuntimeException e) {
            // 🔴 **必须透传，不能改写成 UNKNOWN。**
            //
            // Go 出处：grpc-go 的 `status.FromError` 见到一个已是 status 的 error 会**保留**它的 code；
            // `SqlTimeoutStatusTranslator`（service/common/.../SqlTimeoutStatusTranslator.java:68）正是靠
            // 抛 `Status.DEADLINE_EXCEEDED` 来把 PG 的 `57014 query_canceled` 翻成正确 gRPC 码的。
            // 若本 catch 把 StatusRuntimeException 当成「普通 RuntimeException」改写成 UNKNOWN，
            // 那条翻译会被在这里**二次抹掉**：
            //   · 公开读路径（get_article_logic.go:133）Go 返回 INTERNAL ⇒ 业务码 ErrorServerCommon(5001)；
            //   · Java 被抹成 UNKNOWN ⇒ 落 api 的 default ⇒ CodeServerBusy(1015)。
            // 5001 与 1015 是两套告警口径（系统内部错误 vs 服务繁忙），混用会让 DB 抖动被误判为流量过载。
            //
            // ⚠️ 注意本 catch 必须在 catch (RuntimeException) **之前**（StatusRuntimeException 是它的子类），
            //    否则永远走不到这里。
            observer.onError(e);
        } catch (ArticleExceptions.Raw e) {
            // Go 侧裸 error ⇒ grpc-go 转成 Unknown ⇒ api 侧 default 分支 ⇒ 1015
            LOG.warn("article-rpc handler failed with a raw error (mapped to UNKNOWN): {}", e.getMessage());
            observer.onError(new StatusRuntimeException(
                    Status.UNKNOWN.withDescription(e.getMessage()).withCause(e)));
        } catch (RuntimeException e) {
            LOG.error("article-rpc handler failed", e);
            observer.onError(new StatusRuntimeException(
                    Status.UNKNOWN.withDescription(e.getMessage()).withCause(e)));
        }
    }
}