package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.api.dto.UpdateSubjectStateReq;
import com.rag.comment.comment.api.dto.UpdateSubjectStateResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 改评论区状态 —— 对应 Go {@code api/internal/logic/comment/updatesubjectstatelogic.go}。
 *
 * <p><b>⚠️ {@code state} 必须用 {@code setStateValue(int)}</b>：proto 里
 * {@code SubjectState state = 4} 是枚举，Go 侧
 * {@code updatesubjectstatelogic.go:44} 为纯转换。推理见 {@link ManageCommentService}。
 *
 * <p><b>授权不在本层</b>：proto 注释写明「只有 up 主或管理员才有权关闭评论区」，
 * 该判定在 comment-rpc（其 {@code ErrorUserNoRight}=1008 是本域高频码）。
 * api 层<b>不</b>预判，否则会把 rpc 的码换掉。
 */
@Service
public class UpdateSubjectStateService {

    private final CommentRpcClient commentRpcClient;
    private final CommentApiSupport support;

    public UpdateSubjectStateService(CommentRpcClient commentRpcClient, CommentApiSupport support) {
        this.commentRpcClient = commentRpcClient;
        this.support = support;
    }

    public UpdateSubjectStateResp updateState(HttpServletRequest request, UpdateSubjectStateReq body) {
        long uid = support.userId(request);

        com.rag.comment.comment.proto.UpdateSubjectStateResp rpcResp;
        try {
            rpcResp = commentRpcClient.commentRpc().updateSubjectState(
                    com.rag.comment.comment.proto.UpdateSubjectStateReq.newBuilder()
                            .setUserId(uid)
                            .setTargetType(body.getTargetType())
                            .setTargetId(body.getTargetId())
                            // ⚠️ 必须是 *Value：理由见类注释
                            .setStateValue(body.getState())
                            .build());
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }

        UpdateSubjectStateResp resp = new UpdateSubjectStateResp();
        resp.setSuccess(rpcResp.getSuccess());
        return resp;
    }
}
