package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.api.dto.CreateCommentReq;
import com.rag.comment.comment.api.dto.CreateCommentResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 建评论 —— 对应 Go {@code api/internal/logic/comment/createcommentlogic.go}。
 *
 * <p><b>执行顺序（与 Go 逐条一致）</b>：
 * <ol>
 *   <li>{@code extractUserID} → 失败即 <b>1006</b></li>
 *   <li>组装 RPC 请求（userId 来自 token，<b>不</b>来自 body）</li>
 *   <li>调 {@code CommentCli.CreateComment}</li>
 *   <li>错误：{@code status.FromError} ok ⇒ {@code NewErrCodeMsg(int(st.Code()), st.Message())}；
 *       否则 ⇒ {@code NewErrCode(ErrorServerCommon)}(5001)</li>
 *   <li>成功：原样映射 3 个字段</li>
 * </ol>
 *
 * <p><b>⚠️ 为什么 catch 只写 {@link StatusRuntimeException}</b>：
 * {@code CommentServiceBlockingStub} 声明的受检异常里<b>只有</b>它
 * （grpc-java 的阻塞 stub 不抛 {@code StatusException}），
 * 所以 catch 它既覆盖了全部真实可能，又不会留下「不可达的 catch 分支」这种编译期噪音。
 * Go 的 {@code if err != nil} 捕获一切，Java 无法逐字对应；本类的收窄 catch + 全局兜底
 * 共同复现同一结果：非 status 异常逃出 Service 后，被
 * {@code CommentGlobalExceptionHandler#handleAny} 渲染成 200 + 5001 ——
 * 与 Go 的兜底分支<b>同码同形状</b>。
 * 反之若写成宽 {@code catch (Exception)}，响应转换阶段的 {@code BusinessException}
 * 会被重包，引入新错误。
 */
@Service
public class CreateCommentService {

    private final CommentRpcClient commentRpcClient;
    private final CommentApiSupport support;

    public CreateCommentService(CommentRpcClient commentRpcClient, CommentApiSupport support) {
        this.commentRpcClient = commentRpcClient;
        this.support = support;
    }

    public CreateCommentResp create(HttpServletRequest request, CreateCommentReq body) {
        long uid = support.userId(request);

        com.rag.comment.comment.proto.CreateCommentResp rpcResp;
        try {
            rpcResp = commentRpcClient.commentRpc().createComment(
                    com.rag.comment.comment.proto.CreateCommentReq.newBuilder()
                            .setUserId(uid)
                            .setTargetType(body.getTargetType())
                            .setTargetId(body.getTargetId())
                            .setRootId(body.getRootId())
                            .setParentId(body.getParentId())
                            .setContent(body.getContent())
                            .setMeta(body.getMeta())
                            .build());
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }

        CreateCommentResp resp = new CreateCommentResp();
        resp.setId(rpcResp.getId());
        resp.setCreatedAt(rpcResp.getCreatedAt());
        resp.setSubjectCount(rpcResp.getSubjectCount());
        return resp;
    }
}
