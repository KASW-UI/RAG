package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.UploadAvatarReq;
import com.rag.user.user.proto.UploadAvatarResp;
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
 * 对应 Go: internal/logic/uploadavatarlogic.go
 *
 * 业务逻辑(严格 1:1 复刻 Go 的 UserModel.CreateAvatarHistory 事务):
 *   1. URL TrimSpace,空 → InvalidArgument
 *   2. 雪片生成 historyId
 *   3. 事务(go: gorm.Transaction):
 *      a. 查 user,不存在 → ErrorNotFound
 *      b. 旧 is_current=true → false
 *      c. INSERT user_avatar_history(is_current=true)
 *      d. UPDATE users.extra_info.avatar = 新 URL
 *   4. 返回 AvatarHistoryItem
 *
 * 关键点:
 *   - 整个事务包 @Transactional,出错回滚
 *   - Java 端 avatar 字段写到 users.extra_info(同时写 User.extraInfo["avatar"] 和 user_avatar_history)
 *     与 Go 端 user.ExtraInfo["avatar"] = avatarURL 完全一致
 *   - content_type 缺失时默认 "image/png"(对应 Go model.NormalizeAvatarContentType)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadAvatarLogic {

    private final UserRepository userRepository;
    private final UserAvatarHistoryRepository avatarHistoryRepository;
    private final SnowflakeGenerator snowflake;

    /**
     * 对应 Go: model.NormalizeAvatarContentType —— 缺省 "image/png",TrimSpace
     */
    private String normalizeContentType(String value) {
        if (value == null) return "image/png";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "image/png" : trimmed;
    }

    @Transactional
    public UploadAvatarResp uploadAvatar(UploadAvatarReq req) {
        // 1. URL TrimSpace + 非空
        String avatarUrl = AvatarHelpers.normalizeAvatarUrl(req.getAvatarUrl());
        if (avatarUrl.isEmpty()) {
            log.warn("upload avatar failed: avatar_url is required, uid={}", req.getUid());
            throw new BusinessException(ErrorCode.Code.AVATAR_URL_EMPTY);
        }

        // 2. 雪片生成 history_id
        long historySnowflakeId;
        try {
            historySnowflakeId = snowflake.nextId();
        } catch (Exception e) {
            log.error("upload avatar failed: snowflake id generate error", e);
            throw new BusinessException(ErrorCode.Code.SNOWFLAKE_ID_ERROR);
        }
        String historyId = String.valueOf(historySnowflakeId);

        // 3. 事务主体
        // 3a. 查 user
        User user = userRepository.findByUid(req.getUid())
                .orElseThrow(() -> {
                    log.warn("upload avatar failed: user not found, uid={}", req.getUid());
                    return new BusinessException(ErrorCode.Code.USER_NOT_FOUND);
                });

        // 3b. 旧 is_current=true → false
        avatarHistoryRepository.clearCurrentByUid(req.getUid());

        // 3c. INSERT history(is_current=true)
        UserAvatarHistory history = new UserAvatarHistory();
        history.setHistoryId(historyId);
        history.setUid(req.getUid());
        history.setAvatarUrl(avatarUrl);
        history.setContentType(normalizeContentType(req.getContentType()));
        history.setSizeBytes(req.getSizeBytes());
        history.setIsCurrent(true);
        avatarHistoryRepository.save(history);

        // 3d. UPDATE user.extra_info.avatar = 新 URL
        Map<String, String> extraInfo = user.getExtraInfo();
        if (extraInfo == null) {
            extraInfo = new HashMap<>();
        } else {
            // 防止 JPA 共享引用导致脏数据
            extraInfo = new HashMap<>(extraInfo);
        }
        extraInfo.put("avatar", avatarUrl);
        user.setExtraInfo(extraInfo);
        userRepository.save(user);

        log.info("upload avatar success, uid={}, history_id={}", req.getUid(), historyId);

        return UploadAvatarResp.newBuilder()
                .setAvatarUrl(avatarUrl)
                .setHistory(AvatarHelpers.toProto(history))
                .build();
    }
}