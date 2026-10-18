package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.GetUserReq;
import com.rag.users.admin.proto.GetUserResp;
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.model.User;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 验证 GetUser 关键语义：
 *   - 命中 → GetUserResp{User: UserInfo{...}, Found=default-false}（1:1 跟随 Go 端不设 Found 字段）
 *   - NotFound → StatusRuntimeException(NOT_FOUND)
 *   - 其他 DB 异常 → StatusRuntimeException(INTERNAL)
 */
@ExtendWith(MockitoExtension.class)
class GetUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    GetUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new GetUserLogic(svcCtx);
    }

    /** 命中：返回 UserInfo，Found 字段保持 Go 端默认 false */
    @Test
    void success_returnsUserInfoAndFoundDefaultFalse() {
        User u = userWith(12345L, "alice", "alice@example.com", 0L);
        when(dataModel.findOneUserByUid(12345L)).thenReturn(u);

        GetUserResp resp = logic.getUser(GetUserReq.newBuilder().setUid(12345L).build());

        UserInfo info = resp.getUser();
        assertEquals(12345L, info.getUid());
        assertEquals("alice", info.getUsername());
        assertEquals("alice@example.com", info.getEmail());
        assertEquals(0L, info.getStatus());
        // 关键：Go 端没设 Found 字段，proto3 默认 false，1:1 跟随
        assertFalse(resp.getFound(), "1:1 跟随 Go 端 Found=false（user-rpc 端是 D-003 决策 Found=true）");
    }

    /** 用户不存在 → StatusRuntimeException(NOT_FOUND) */
    @Test
    void userNotFound_throwsNotFound() {
        when(dataModel.findOneUserByUid(999L))
                .thenThrow(new NotFoundException("user not found by uid: 999"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.getUser(GetUserReq.newBuilder().setUid(999L).build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** DB 异常 → StatusRuntimeException(INTERNAL) */
    @Test
    void dbError_throwsInternal() {
        when(dataModel.findOneUserByUid(12345L))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.getUser(GetUserReq.newBuilder().setUid(12345L).build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    private static User userWith(long uid, String username, String email, long status) {
        User u = new User();
        u.setUid(uid);
        u.setUsername(username);
        u.setEmail(email);
        u.setStatus(status);
        Map<String, String> extra = new HashMap<>();
        extra.put("k", "v");
        u.setExtraInfo(extra);
        return u;
    }
}