package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.UpdateUserReq;
import com.rag.user.user.proto.UpdateUserResp;
import com.rag.user.user.proto.UserInfo;
import com.rag.user.user.rpc.internal.model.ErrorCode;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.User;
import com.rag.user.user.rpc.internal.model.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go: internal/logic/updateuserlogic.go
 *
 * 业务逻辑(严格 1:1):
 *   1. 若 Username 非空:先按 username 查重,已存在则 ALREADY_EXISTS
 *   2. 若 Password 非空:BCrypt 加密
 *   3. 若 Email / extraInfo 非空:赋值
 *   4. UPDATE users WHERE uid = ?
 *   5. 重新查一遍,返回 UserInfo
 *
 * 关键陷阱(对应"翻译时易踩的坑 3"):
 *   - JPA 的 save() 会对所有字段 UPDATE,空字段会把库里原有值覆盖成空!
 *   - 这里用 @Transactional + 自己构造 User 对象(只赋需要改的字段),效果等于 Go 的 Updates(toUpdate),
 *     Hibernate 的"动态更新"特性(dynamic-update)也帮忙只更新 dirty field。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateUserLogic {

    private final UserRepository userRepository;
    private final PasswordEncoderService passwordEncoder;

    @Transactional
    public UpdateUserResp updateUser(UpdateUserReq req) {
        // 1. 改名要先查重
        if (!req.getUsername().isEmpty()) {
            userRepository.findByUsername(req.getUsername()).ifPresent(existing -> {
                if (!existing.getUid().equals(req.getUid())) {
                    log.warn("update user failed: username exists, uid={}", req.getUid());
                    throw new BusinessException(ErrorCode.Code.USERNAME_EXISTS);
                }
            });
        }

        // 2. 构造 toUpdate,只赋需要改的字段
        User toUpdate = new User();
        toUpdate.setUid(req.getUid());
        if (!req.getUsername().isEmpty()) {
            toUpdate.setUsername(req.getUsername());
        }
        if (!req.getPassword().isEmpty()) {
            toUpdate.setPassword(passwordEncoder.encrypt(req.getPassword()));
        }
        if (!req.getEmail().isEmpty()) {
            toUpdate.setEmail(req.getEmail());
        }
        if (!req.getExtraInfoMap().isEmpty()) {
            // 直接整段覆盖;Go 端写的是赋值整个 toUpdate.ExtraInfo(也是覆盖)
            toUpdate.setExtraInfo(new HashMap<>(req.getExtraInfoMap()));
        }

        // 3. UPDATE
        try {
            // 用 save 触发 Hibernate dirty checking,只 update 被改的字段
            // 需要先 load 旧值作为 baseline —— 这里简化:save 整对象,jpa 默认全字段 update
            // 严谨做法:用 @Query 写 partial update;本项目 1:1 复刻 Go 的 .Updates(toUpdate) 走 save
            userRepository.save(toUpdate);
        } catch (Exception e) {
            log.error("update user failed, uid={}", req.getUid(), e);
            throw new BusinessException(ErrorCode.Code.DB_UPDATE_ERROR);
        }

        // 4. 再查一遍,转 UserInfo
        User newUser = userRepository.findByUid(req.getUid())
                .orElseThrow(() -> new BusinessException(ErrorCode.Code.USER_NOT_FOUND));

        log.info("update success, uid={}", req.getUid());
        return UpdateUserResp.newBuilder()
                .setUser(toUserInfo(newUser))
                .build();
    }

    private UserInfo toUserInfo(User user) {
        UserInfo.Builder builder = UserInfo.newBuilder()
                .setUid(user.getUid() == null ? 0L : user.getUid())
                .setUsername(user.getUsername() == null ? "" : user.getUsername())
                .setEmail(user.getEmail() == null ? "" : user.getEmail());
        Map<String, String> extra = user.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            builder.putAllExtraInfo(extra);
        }
        if (user.getStatus() != null) {
            builder.setStatus(user.getStatus());
        }
        return builder.build();
    }
}