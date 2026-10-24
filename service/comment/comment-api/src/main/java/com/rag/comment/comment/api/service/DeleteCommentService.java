package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.api.dto.DeleteCommentReq;
import com.rag.comment.comment.api.dto.DeleteCommentResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 删评论 —— 对应 Go {@code api/internal/logic/comment/deletecommentlogic.go}。
 *
 * <p><b>⚠️ 响应里的 {@code success} 是字面量 {@code true}，不是 rpc 的返回值</b>：
 * Go {@code deletecommentlogic.go:57-60} 写的是
 * <pre>
 * return &types.DeleteCommentResp{
 *     Success:           true,                       // ← 字面量
 *     SubjectTotalCount: rpcResp.SubjectTotalCount,
 * }, nil
 * </pre>
 * 而 {@code pb.DeleteCommentResp} 里<b>本来就有</b> {@code bool success = 1}，
 * Go 却<b>没有</b>读取它。若 Java 侧写成 {@code resp.setSuccess(rpcResp.getSuccess())}，
 * 在 rpc 侧返回 {@code success=false} 的形态下两侧就会分叉。
 * 按 AGENTS.md §15，Go 的既有行为即便看起来是疏漏也原样保留。
 *
 * <p><b>授权不在本层</b>：Go proto 注释写明 {@code user_id} 是
 * 「用于检测是否为本人或 up 主操作防止出现安全问题」，该判定在 comment-rpc。
 * api 只负责把 token 里的身份如实传下去。
 */
@Service
public class DeleteCommentService {

    private final CommentRpcClient commentRpcClient;
    private final CommentApiSupport support;

    public DeleteCommentService(CommentRpcClient commentRpcClient, CommentApiSupport support) {
        this.commentRpcClient = commentRpcClient;
        this.support = support;
    }

    public DeleteCommentResp delete(HttpServletRequest request, DeleteCommentReq body) {
        long uid = support.userId(request);

        com.rag.comment.comment.proto.DeleteCommentResp rpcResp;
        try {
            rpcResp = commentRpcClient.commentRpc().deleteComment(
                    com.rag.comment.comment.proto.DeleteCommentReq.newBuilder()
                            .setUserId(uid)
                            .setCommentId(body.getCommentId())
                            .setTargetType(body.getTargetType())
                            .setTargetId(body.getTargetId())
                            .build());
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }

        DeleteCommentResp resp = new DeleteCommentResp();
        resp.setSuccess(true);
        resp.setSubjectTotalCount(rpcResp.getSubjectTotalCount());
        return resp;
    }
}
