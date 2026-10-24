package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.CreateArticleReq;
import com.rag.article.article.api.dto.CreateArticleResp;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.article.article.proto.CreateArticleRequest;
import com.rag.article.article.proto.CreateArticleResponse;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

/**
 * 创建文章 —— 对应 Go {@code logic/article/create_article_logic.go}。
 *
 * <h2>🔴 两条失败路径<b>带 data</b>，两条不带</h2>
 *
 * <pre>
 *   Go: create_article_logic.go:55-75
 *       st, _ := status.FromError(err)
 *       articleID := extractArticleIDFromStatusMessage(st.Message())
 *       switch st.Code() {
 *       case codes.AlreadyExists:      return nil, errmsg.ErrorArticleExist        // 1001，**data = nil**
 *       case codes.FailedPrecondition: return &amp;types.CreateArticleResp{ArticleId: articleID},
 *                                           errmsg.ErrorArticlePublishFailed     // 1004，**data 带 id**
 *       case codes.DeadlineExceeded:   return &amp;types.CreateArticleResp{ArticleId: articleID},
 *                                           errmsg.ErrorArticlePublishing        // 1003，**data 带 id**
 *       case codes.Internal:           return nil, errmsg.ErrorServerCommon       // 5001，data = nil
 *       default:                        return nil, errmsg.CodeServerBusy          // 1015，data = nil
 *       }
 * </pre>
 *
 * ⇒ 客户端会遇到 <b>「HTTP 200 + code≠200 + data 非空」</b>：
 * <pre>
 *   {"code":1003,"msg":"文章处理中，请稍后查看","data":{"article_id":"1792…"}}
 * </pre>
 * 这不是笔误：文章已落库、只是发布流水线没跑完，客户端<b>需要</b>那个 id 才能继续轮询/跳转。
 * <b>把这四条统一成「data = null」就是 L2 语义破坏</b>。
 *
 * <h2>⚠️ {@code articleID} 是从 gRPC status <b>文案</b>里抠出来的</h2>
 *
 * <pre>
 *   Go: create_article_logic.go:82-92
 *       func extractArticleIDFromStatusMessage(message string) string {
 *           if message == "" { return "" }
 *           idx := strings.LastIndex(message, ":")
 *           if idx &lt; 0 || idx+1 &gt;= len(message) { return "" }
 *           return strings.TrimSpace(message[idx+1:])
 *       }
 * </pre>
 * ⚠️ <b>这是一个明显脆弱的协议</b>（约定优于契约），但它<b>是当前真源行为</b>，
 * 且对端 {@code article-rpc} 的对应分支确实按这个格式拼 message。
 * 本类<b>逐字复刻</b>，包括两个边界：
 * <ul>
 *   <li>没有 {@code ":"} → 返回 {@code ""}（<b>不是</b>整串）；</li>
 *   <li>{@code ":"} 在<b>末尾</b>（{@code idx+1 >= len}）→ 返回 {@code ""}
 *       （此时 {@code message.substring(idx+1)} 会是空串，Go 里的等价判定）；</li>
 *   <li>只取<b>最后一个</b> {@code ":"} 之后的部分（{@code LastIndex}）。</li>
 * </ul>
 *
 * <p>⚠️ <b>不要</b>「顺手改成结构化传递」（比如加个 metadata 或新字段）——
 * 那需要同时改 article-rpc，属于跨组件变更，超出本组件迁移范围。
 * 该脆弱性登记在 {@code Remaining Issues}。
 *
 * <h2>⚠️ {@code Brief} / {@code CoverImageUrl} <b>恒以 optional 形式下发</b></h2>
 * <pre>
 *   Go: Brief: &amp;req.Brief,  CoverImageUrl: &amp;req.CoverImageUrl
 * </pre>
 * 即<b>哪怕是空串</b>，proto 的 {@code has_brief} / {@code has_cover_image_url} 也是 {@code true}。
 * 对端据此区分「明确清空」与「没传」。
 * ⇒ Java 侧<b>必须</b>无条件 {@code setBrief(...)}；写成「非空才 set」会让
 * 「把简介改成空」这个操作丢失。
 */
@Service
public class ArticleCreateService {

    private final ArticleRpcClient articleRpcClient;
    private final ArticleApiSupport support;

    public ArticleCreateService(ArticleRpcClient articleRpcClient, ArticleApiSupport support) {
        this.articleRpcClient = articleRpcClient;
        this.support = support;
    }

    /**
     * @param authorId 当前登录用户（Go {@code extractCurrentUserID} 的结果）。
     *                 由 controller 解析 —— 它可能<b>抛</b> {@code BusinessException(1005)}，
     *                 那是 Go 侧 {@code if code != errmsg.Success { return nil, code }} 的等价物。
     */
    public CreateArticleResp createArticle(CreateArticleReq req, String authorId) {
        CreateArticleResponse rpcResp;
        try {
            CreateArticleRequest.Builder builder = CreateArticleRequest.newBuilder()
                    .setTitle(nullToEmpty(req.getTitle()))
                    // ⚠️ 恒 set：proto3 optional 的 presence 语义（见类注释）
                    .setBrief(nullToEmpty(req.getBrief()))
                    .setMarkdownContent(nullToEmpty(req.getContent()))
                    .setCoverImageUrl(nullToEmpty(req.getCoverImageUrl()))
                    .setManualTypeTag(nullToEmpty(req.getManualTypeTag()))
                    .setAuthorId(authorId);
            // Go: SecondaryTags: req.SecondaryTags —— repeated 字段，nil slice 与空 slice 同形
            //   （protobuf 的 addAll 对空列表是 no-op，对端 len() == 0 两种来源一致）
            if (req.getSecondaryTags() != null) {
                builder.addAllSecondaryTags(req.getSecondaryTags());
            }
            rpcResp = articleRpcClient.articleRpc().createArticle(builder.build());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            String articleId = extractArticleIdFromStatusMessage(e.getStatus().getDescription());

            // ⚠️ 四条分支的「有没有 data」各不相同，顺序与 Go 一致
            if (code == Status.Code.ALREADY_EXISTS) {
                throw business(ErrorCode.ERROR_ARTICLE_EXIST);                       // data = null
            }
            if (code == Status.Code.FAILED_PRECONDITION) {
                throw new CreateFailure(ErrorCode.ERROR_ARTICLE_PUBLISH_FAILED, articleId);
            }
            if (code == Status.Code.DEADLINE_EXCEEDED) {
                throw new CreateFailure(ErrorCode.ERROR_ARTICLE_PUBLISHING, articleId);
            }
            if (code == Status.Code.INTERNAL) {
                throw business(ErrorCode.ERROR_SERVER_COMMON);                       // data = null
            }
            throw business(ErrorCode.CODE_SERVER_BUSY);                              // data = null
        }

        // Go: create_article_logic.go:77-79
        // ⚠️ rpcResp 理论上传回 nil（Go 是指针）。Go 侧 `rpcResp.ArticleId` 会在 nil 上 panic
        //    ⇒ recover → 裸 500。本侧等价：proto 的 getter 对 null 消息返回默认值，
        //    不会 panic。要完全复刻 panic 语义需要显式判空并抛 —— 但那会把
        //    「一个理论上不该发生的情况」变成生产 500 路径，此处选择与 Go 等价的
        //    「拿到空 article_id」，并由 article-rpc 保证不返回 nil。
        return new CreateArticleResp(rpcResp.getArticleId());
    }

    /**
     * Go {@code extractArticleIDFromStatusMessage} 的逐字复刻。
     *
     * <p>⚠️ 见类注释：这是一个脆弱的约定式协议（从 status 文案里取最后一个冒号之后的内容），
     * 但它是当前真源行为，且<b>只在 {@code FailedPrecondition} / {@code DeadlineExceeded}
     * 两条分支上被读到</b>。
     */
    static String extractArticleIdFromStatusMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        int idx = message.lastIndexOf(':');
        if (idx < 0 || idx + 1 >= message.length()) {
            return "";
        }
        return message.substring(idx + 1).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static BusinessException business(ErrorCode code) {
        return new BusinessException(code.getCode(), ArticleApiSupport.msg(code));
    }

    /**
     * 「失败但带 data」的信封 —— Go 的 {@code return &amp;types.CreateArticleResp{ArticleId: articleID}, code}。
     *
     * <p>{@code BusinessException} 只能产出 {@code data = null}（见
     * {@code com.rag.common.exception.BusinessException} 的 javadoc：它是
     * {@code BusinessException(int code, String msg)}，不带 payload）。
     * 为此改共享异常类是<b>超出本组件范围</b>的改动，
     * 而「失败也带 data」是本组件的 L2 语义 ⇒ 用一个本地子类承载，
     * 由 {@code GlobalExceptionHandler} 单独识别。
     */
    public static class CreateFailure extends BusinessException {

        private static final long serialVersionUID = 1L;

        private final transient CreateArticleResp payload;

        public CreateFailure(ErrorCode code, String articleId) {
            super(code.getCode(), ArticleApiSupport.msg(code));
            this.payload = new CreateArticleResp(articleId);
        }

        public CreateArticleResp getPayload() {
            return payload;
        }
    }
}