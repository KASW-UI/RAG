package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.GetUserReq;
import com.rag.user.user.proto.GetUserResp;
import com.rag.user.user.proto.UserInfo;
import com.rag.user.user.rpc.internal.model.ErrorCode;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.User;
import com.rag.user.user.rpc.internal.model.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 对应 Go: internal/logic/getuserlogic.go
 *
 * 关键点(1:1 复刻):
 *   - Go 在用户不存在时同时返回 {Found: false} 和 status.Error(NotFound),
 *     Java 端抛 BusinessException(USER_NOT_FOUND) → @GrpcAdvice 转 NotFound,
 *     Found=false 在异常路径下到不了 client,但代码里仍构造同款 Resp 保持语义对称。
 *   - UserInfo.Status 在 Go 是 *int64(用指针区分"字段缺失 vs 0"),
 *     Java 用 proto3 optional,builder.setStatus(long) 即使写 0 也会 set。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetUserLogic {

    private final UserRepository userRepository;

    public GetUserResp getUser(GetUserReq req) {
        User user = userRepository.findByUid(req.getUid()).orElse(null);

        if (user == null) {
            log.warn("get user failed: user not found, uid={}", req.getUid());
            // 1:1 复刻 Go: 返回 {Found: false} + status.Error(codes.NotFound)
            // Java 端只抛异常;Found=false 由 GrpcAdvice 拦截后不会落地,但语义等价
            throw new BusinessException(ErrorCode.Code.USER_NOT_FOUND);
        }

        log.info("search user success, uid={}", req.getUid());
        return GetUserResp.newBuilder()
                .setFound(true)
                .setUser(toUserInfo(user))
                .build();
    }

    /**
     * 对应 Go: getuserlogic.go 构造 UserInfo 的内联代码
     * 注意 Status 是 proto3 optional,builder.setStatus(0L) 仍会序列化(go 端 *int64 = &0 也是序列化)。
     */
    private UserInfo toUserInfo(User user) {
        UserInfo.Builder builder = UserInfo.newBuilder()
                .setUid(user.getUid() == null ? 0L : user.getUid())
                .setScore(user.getScore() == null ? 0 : user.getScore())
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