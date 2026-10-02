package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.SelectAvatarReq;
import com.rag.user.user.proto.SelectAvatarResp;
import com.rag.user.user.rpc.internal.model.ErrorCode;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.User;
import com.rag.user.user.rpc.internal.model.UserAvatarHistory;
import com.rag.user.user.rpc.internal.model.UserAvatarHistoryRepository;
import com.rag.user.user.rpc.internal.model.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go: internal/logic/selectavatarlogic.go
 *
 * 业务逻辑(严格 1:1 复刻 Go 的 UserModel.SelectAvatarHistory 事务):
 *   1. historyId TrimSpace,空 → InvalidArgument
 *   2. 事务:
 *      a. 查 user,不存在 → ErrorNotFound
 *      b. 查 history(uid + historyId),不存在 → ErrorAvatarHistoryNotFound
 *      c. 旧 is_current=true → false
 *      d. 指定 history_id → is_current=true
 *      e. UPDATE user.extra_info.avatar = history.avatar_url
 *   3. 返回 avatar_url + AvatarHistoryItem
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelectAvatarLogic {

    private final UserRepository userRepository;
    private final UserAvatarHistoryRepository avatarHistoryRepository;

    @Transactional
    public SelectAvatarResp selectAvatar(SelectAvatarReq req) {
        // 1. historyId TrimSpace + 非空
        String historyId = req.getHistoryId();
        if (historyId != null) {
            historyId = historyId.trim();
        }
        if (historyId == null || historyId.isEmpty()) {
            log.warn("select avatar failed: history_id is required, uid={}", req.getUid());
            throw new BusinessException(ErrorCode.Code.HISTORY_ID_EMPTY);
        }

        // 2a. 查 user
        User user = userRepository.findByUid(req.getUid())
                .orElseThrow(() -> {
                    log.warn("select avatar failed: user not found, uid={}", req.getUid());
                    return new BusinessException(ErrorCode.Code.USER_NOT_FOUND);
                });

        // 2b. 查 history
        UserAvatarHistory history = avatarHistoryRepository
                .findByUidAndHistoryId(req.getUid(), historyId)
                .orElseThrow(() -> {
                    log.warn("select avatar failed: avatar history not found, uid={}, history_id={}",
                            req.getUid(), historyId);
                    return new BusinessException(ErrorCode.Code.AVATAR_HISTORY_NOT_FOUND);
                });

        // 2c. 旧 is_current=true → false
        avatarHistoryRepository.clearCurrentByUid(req.getUid());

        // 2d. 指定 history_id → is_current=true
        avatarHistoryRepository.setCurrentByUidAndHistoryId(req.getUid(), historyId);

        // 2e. UPDATE user.extra_info.avatar = history.avatar_url
        Map<String, String> extraInfo = user.getExtraInfo();
        if (extraInfo == null) {
            extraInfo = new HashMap<>();
        } else {
            extraInfo = new HashMap<>(extraInfo);
        }
        extraInfo.put("avatar", history.getAvatarUrl());
        user.setExtraInfo(extraInfo);
        userRepository.save(user);

        history.setIsCurrent(true);

        log.info("select avatar success, uid={}, history_id={}", req.getUid(), historyId);
        return SelectAvatarResp.newBuilder()
                .setAvatarUrl(history.getAvatarUrl())
                .setHistory(AvatarHelpers.toProto(history))
                .build();
    }
}