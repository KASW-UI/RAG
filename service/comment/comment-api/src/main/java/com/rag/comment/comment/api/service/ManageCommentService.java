package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.api.dto.ManageCommentReq;
import com.rag.comment.comment.api.dto.ManageCommentResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 评论管理（置顶 / 精选）—— 对应 Go {@code api/internal/logic/comment/managecommentlogic.go}。
 *
 * <p><b>⚠️⚠️ {@code action_type} 必须用 {@code setActionTypeValue(int)}</b>。
 * 这是本模块最容易被「自然写成错」的一处：
 * <ul>
 *   <li>Go {@code managecommentlogic.go:46} 写的是
 *       {@code ActionType: pb.ManageType(req.ActionType)} ——
 *       <b>纯类型转换</b>，没有任何有效性检查，所以 proto3 开放枚举下
 *       <b>未定义值（如 99）也能原样发到下游</b>。</li>
 *   <li>Java 若写成 {@code setActionType(ManageType.forNumber(v))}，
 *       {@code forNumber(99)} 返回 {@code null} ⇒ 字段被当成缺省，
 *       线上实际发出 <b>0（UNKNOWN）</b> ⇒ <b>静默改写客户端意图</b>，
 *       而且不报任何错。</li>
 * </ul>
 * ⇒ 用 protobuf 为枚举字段生成的「原始数值」setter，它不做有效性过滤。
 * {@code ReportCommentService} / {@code UpdateSubjectStateService} 同理。
 */
@Service
public class ManageCommentService {

    private final CommentRpcClient commentRpcClient;
    private final CommentApiSupport support;

    public ManageCommentService(CommentRpcClient commentRpcClient, CommentApiSupport support) {
        this.commentRpcClient = commentRpcClient;
        this.support = support;
    }

    public ManageCommentResp manage(HttpServletRequest request, ManageCommentReq body) {
        long uid = support.userId(request);

        com.rag.comment.comment.proto.ManageCommentResp rpcResp;
        try {
            rpcResp = commentRpcClient.commentRpc().manageComment(
                    com.rag.comment.comment.proto.ManageCommentReq.newBuilder()
                            .setUserId(uid)
                            .setCommentId(body.getCommentId())
                            .setTargetType(body.getTargetType())
                            .setTargetId(body.getTargetId())
                            // ⚠️ 必须是 *Value：理由见类注释
                            .setActionTypeValue(body.getActionType())
                            .build());
        } catch (StatusRuntimeException e) {
            throw support.rpcError(e);
        }

        ManageCommentResp resp = new ManageCommentResp();
        resp.setSuccess(rpcResp.getSuccess());
        return resp;
    }
}
