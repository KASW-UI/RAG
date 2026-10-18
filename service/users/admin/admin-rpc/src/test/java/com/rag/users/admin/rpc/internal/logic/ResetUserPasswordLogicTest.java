package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.ResetUserPasswordReq;
import com.rag.users.admin.proto.ResetUserPasswordResp;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.model.User;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 ResetUserPassword 关键语义：
 *   - 命中 → resp{Success=true}，password 被 bcrypt 哈希后写库（不能是明文）
 *   - user 不存在 → NOT_FOUND
 *   - DB 失败 → INTERNAL
 *   - System.DefaultPassword 为空 → INTERNAL（fail-fast）
 */
@ExtendWith(MockitoExtension.class)
class ResetUserPasswordLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    ResetUserPasswordLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.getSystem().setDefaultPassword("123456");
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new ResetUserPasswordLogic(svcCtx, metrics);
    }

    /** 命中：调用 updateUserPasswordByUid，参数 hash 必须能 match 明文 "123456" */
    @Test
    void success_writesBcryptHash() {
        User u = new User();
        u.setUid(12345L);
        u.setUsername("alice");
        when(dataModel.findOneUserByUid(12345L)).thenReturn(u);

        ResetUserPasswordResp resp = logic.resetUserPassword(ResetUserPasswordReq.newBuilder()
                .setUid(12345L)
                .setOperatorId(99L)
                .build());

        assertTrue(resp.getSuccess());

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataModel).updateUserPasswordByUid(org.mockito.ArgumentMatchers.eq(12345L), hashCaptor.capture());

        // 关键：传入的 hash 必须是 bcrypt 哈希，不是明文 "123456"
        String hash = hashCaptor.getValue();
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"), "bcrypt 哈希前缀应为 $2a$/$2b$");
        assertTrue(PasswordUtil.check("123456", hash), "hash 应能 match 明文 123456");
    }

    /** user 不存在 → NOT_FOUND，不调 update */
    @Test
    void userNotFound_throwsNotFound() {
        when(dataModel.findOneUserByUid(12345L))
                .thenThrow(new NotFoundException("user not found"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.resetUserPassword(ResetUserPasswordReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** DB 失败 → INTERNAL */
    @Test
    void dbError_throwsInternal() {
        when(dataModel.findOneUserByUid(12345L))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.resetUserPassword(ResetUserPasswordReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    /** System.DefaultPassword 为空 → INTERNAL（业务约束 fail-fast） */
    @Test
    void defaultPasswordEmpty_throwsInternal() {
        config.getSystem().setDefaultPassword("");
        // 留空就不调 dataModel，直接抛 INTERNAL

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.resetUserPassword(ResetUserPasswordReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    /** UpdateUserPasswordByUid 抛 RuntimeException → INTERNAL */
    @Test
    void updateDbError_throwsInternal() {
        User u = new User();
        u.setUid(12345L);
        when(dataModel.findOneUserByUid(12345L)).thenReturn(u);
        doThrow(new RuntimeException("connection refused"))
                .when(dataModel).updateUserPasswordByUid(org.mockito.ArgumentMatchers.eq(12345L), org.mockito.ArgumentMatchers.anyString());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.resetUserPassword(ResetUserPasswordReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}