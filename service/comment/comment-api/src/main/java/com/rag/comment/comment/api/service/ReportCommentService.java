package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.api.dto.ReportCommentReq;
import com.rag.comment.comment.api.dto.ReportCommentResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 举报评论 —— 对应 Go {@code api/internal/logic/comment/reportcommentlogic.go}。
 *
 * <p><b>⚠️ {@code reason} 必须用 {@code setReasonValue(int)}</b>：proto 里
 * {@code ReportReason reason = 5} 是<b>枚举</b>，而 Go 侧
 * {@code reportcommentlogic.go:46} 是 {@code Reason: pb.ReportReason(req.Reason)} 的
 * 纯转换，未定义值同样原样透传。用 {@code forNumber} 会把未知值（如 98）静默变成 0。
 * 推理见 {@link ManageCommentService} 类注释。
 */
@Service
public class ReportCommentService {

    private final CommentRpcClient commentRpcClient;
    private final CommentApiSupport support;

    public ReportCommentService(CommentRpcClient commentRpcClient, CommentApiSupport support) {
        this.commentRpcClient = commentRpcClient;
        this.support = support;
    }

    public ReportCommentResp report(HttpServletRequest request, ReportCommentReq body) {
        long uid = support.userId(request);

        com.rag.comment.comment.proto.ReportCommentResp rpcResp;
        try {
            rpcResp = commentRpcClient.commentRpc().reportComment(
                    com.rag.comment.comment.proto.ReportCommentReq.newBuilder()
                            .setUserId(uid)
                            .setCommentId(body.getCommentId())
                            .setTargetType(body.getTargetType())
                            .setTargetId(body.getTargetId())
                            // ⚠️ 必须是 *Value：理由见类注释
                            .setReasonValue(body.getReason())
                            .setDetail(body.getDetail())
                            .build());
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }

        ReportCommentResp resp = new ReportCommentResp();
        resp.setSuccess(rpcResp.getSuccess());
        return resp;
    }
}
