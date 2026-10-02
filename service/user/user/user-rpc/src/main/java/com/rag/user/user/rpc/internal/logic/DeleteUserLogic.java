package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.DeleteUserReq;
import com.rag.user.user.proto.DeleteUserResp;
import com.rag.user.user.rpc.internal.model.ErrorCode;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对应 Go: internal/logic/deleteuserlogic.go
 *
 * 业务逻辑(严格 1:1):
 *   1. 按 uid 查 —— 不存在 → NotFound + Success=false
 *   2. DELETE users WHERE uid = ?
 *
 * 关键点:
 *   - Go 端在用户不存在时同时返回 {Success: false} 和 status.Error(NotFound),
 *     Java 抛 BusinessException;Success=false 字段在异常路径下到不了 client(同 GetUserLogic)
 *   - 删除成功后 Success=true
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteUserLogic {

    private final UserRepository userRepository;

    public DeleteUserResp deleteUser(DeleteUserReq req) {
        if (userRepository.findByUid(req.getUid()).isEmpty()) {
            log.warn("delete user failed: user not found, uid={}", req.getUid());
            throw new BusinessException(ErrorCode.Code.USER_NOT_FOUND);
        }

        try {
            userRepository.deleteByUid(req.getUid());
        } catch (Exception e) {
            log.error("delete user failed, uid={}", req.getUid(), e);
            throw new BusinessException(ErrorCode.Code.DB_UPDATE_ERROR);
        }

        log.info("delete success");
        return DeleteUserResp.newBuilder().setSuccess(true).build();
    }
}