package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.AvatarHistoryItem;
import com.rag.user.user.proto.GetAvatarHistoryReq;
import com.rag.user.user.proto.GetAvatarHistoryResp;
import com.rag.user.user.rpc.internal.model.ErrorCode;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.UserAvatarHistory;
import com.rag.user.user.rpc.internal.model.UserAvatarHistoryRepository;
import com.rag.user.user.rpc.internal.model.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对应 Go: internal/logic/getavatarhistorylogic.go
 *
 * 业务逻辑(严格 1:1):
 *   1. 校验 user 存在
 *   2. ListAvatarHistory(uid, limit):
 *      - limit <= 0 或 > 30 强制 30
 *      - 按 create_time DESC
 *   3. model → proto 转 list
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetAvatarHistoryLogic {

    /** 对应 Go: limit <= 0 || limit > 30 时强制 30 */
    private static final int MAX_LIMIT = 30;

    private final UserRepository userRepository;
    private final UserAvatarHistoryRepository avatarHistoryRepository;

    public GetAvatarHistoryResp getAvatarHistory(GetAvatarHistoryReq req) {
        // 1. 校验 user 存在
        if (userRepository.findByUid(req.getUid()).isEmpty()) {
            log.warn("get avatar history failed: user not found, uid={}", req.getUid());
            throw new BusinessException(ErrorCode.Code.USER_NOT_FOUND);
        }

        // 2. 限流钳制
        int limit = req.getLimit();
        if (limit <= 0 || limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        Pageable pageable = PageRequest.of(0, limit);

        // 3. 查 + 转
        List<UserAvatarHistory> histories = avatarHistoryRepository
                .findByUidOrderByCreateTimeDesc(req.getUid(), pageable);

        List<AvatarHistoryItem> items = new ArrayList<>(histories.size());
        for (UserAvatarHistory h : histories) {
            items.add(AvatarHelpers.toProto(h));
        }

        return GetAvatarHistoryResp.newBuilder().addAllList(items).build();
    }
}