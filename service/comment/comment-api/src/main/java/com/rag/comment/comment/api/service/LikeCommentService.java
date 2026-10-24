package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.api.dto.LikeCommentReq;
import com.rag.comment.comment.api.dto.LikeCommentResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 点赞 / 点踩 —— 对应 Go {@code api/internal/logic/comment/likecommentlogic.go}。
 *
 * <p><b>⚠️ {@code action_type} 用 {@code setActionTypeValue(int)} 而非
 * {@code setActionType(ActionType)}</b>：
 * proto 里该字段声明为 <b>{@code int32 action_type = 5}</b>（<b>不是</b>枚举），
 * 所以 Java 生成代码没有 {@code setActionTypeValue} 这一对之外的枚举入口，
 * 直接 {@code setActionType(4)} 即为原样透传，proto3 开放语义天然成立。
 * 0=点赞 / 1=取消点赞 / 2=点踩 / 3=取消点踩（见 proto 注释）；
 * 范围校验归 comment-rpc，本层不预判。
 */
@Service
public class LikeCommentService {

    private final CommentRpcClient commentRpcClient;
    private final CommentApiSupport support;

    public LikeCommentService(CommentRpcClient commentRpcClient, CommentApiSupport support) {
        this.commentRpcClient = commentRpcClient;
        this.support = support;
    }

    public LikeCommentResp like(HttpServletRequest request, LikeCommentReq body) {
        long uid = support.userId(request);

        com.rag.comment.comment.proto.LikeCommentResp rpcResp;
        try {
            rpcResp = commentRpcClient.commentRpc().likeComment(
                    com.rag.comment.comment.proto.LikeCommentReq.newBuilder()
                            .setUserId(uid)
                            .setTargetType(body.getTargetType())
                            .setTargetId(body.getTargetId())
                            .setCommentId(body.getCommentId())
                            .setActionType(body.getActionType())
                            .build());
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }

        LikeCommentResp resp = new LikeCommentResp();
        resp.setSuccess(rpcResp.getSuccess());
        resp.setLikeCount(rpcResp.getLikeCount());
        return resp;
    }
}
