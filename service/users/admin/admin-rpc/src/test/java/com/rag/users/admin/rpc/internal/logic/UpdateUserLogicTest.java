package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.UpdateUserReq;
import com.rag.users.admin.proto.UpdateUserResp;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 UpdateUser 关键语义：
 *   - 命中 → resp{Success=true, User=UserInfo}，UserInfo.Status 回填
 *   - user 不存在 → NOT_FOUND
 *   - username 被其他 user 占用 → ALREADY_EXISTS（D-004 不排除自己）
 *   - DB 失败 → INTERNAL
 */
@ExtendWith(MockitoExtension.class)
class UpdateUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    UpdateUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new UpdateUserLogic(svcCtx, metrics);
    }

    /** 命中：只改 username + password → success + UserInfo.username 是新值 */
    @Test
    void success_changesUsernameAndPassword() {
        User target = userWith(12345L, "alice", "alice@example.com", 0L);
        User updated = userWith(12345L, "alice_new", "alice@example.com", 0L);
        when(dataModel.findOneUserByUid(12345L))
                .thenReturn(target)
                .thenReturn(updated);
        when(dataModel.findOneUserByUsername("alice_new"))
                .thenThrow(new NotFoundException("not found"));

        UpdateUserResp resp = logic.updateUser(UpdateUserReq.newBuilder()
                .setUid(12345L)
                .setOperatorId(99L)
                .setUsername("alice_new")
                .setPassword("newpw")
                .build());

        assertTrue(resp.getSuccess());
        assertEquals("alice_new", resp.getUser().getUsername());
        assertEquals(12345L, resp.getUser().getUid());
        assertEquals(0L, resp.getUser().getStatus()); // D-011: status 非 null
        verify(dataModel).updateUserByUid(any(), any());
    }

    /** username 被其他 user 占用 → ALREADY_EXISTS，不调 update */
    @Test
    void usernameTaken_throwsAlreadyExists() {
        User target = userWith(12345L, "alice", "a@example.com", 0L);
        User other = userWith(99999L, "bob", "b@example.com", 0L);
        when(dataModel.findOneUserByUid(12345L)).thenReturn(target);
        when(dataModel.findOneUserByUsername("bob")).thenReturn(other);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.updateUser(UpdateUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .setUsername("bob")
                        .build()));

        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        verify(dataModel, never()).updateUserByUid(any(), any());
    }

    /** user 不存在 → NOT_FOUND */
    @Test
    void userNotFound_throwsNotFound() {
        when(dataModel.findOneUserByUid(12345L))
                .thenThrow(new NotFoundException("user not found"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.updateUser(UpdateUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .setUsername("alice")
                        .build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** DB 失败 → INTERNAL */
    @Test
    void dbError_throwsInternal() {
        when(dataModel.findOneUserByUid(12345L))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.updateUser(UpdateUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .setUsername("alice")
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    private static User userWith(long uid, String username, String email, long status) {
        User u = new User();
        u.setUid(uid);
        u.setUsername(username);
        u.setEmail(email);
        u.setStatus(status);
        u.setExtraInfo(new HashMap<>());
        return u;
    }
}