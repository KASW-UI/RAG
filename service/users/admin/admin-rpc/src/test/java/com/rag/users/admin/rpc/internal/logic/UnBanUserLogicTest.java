package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.UnBanUserReq;
import com.rag.users.admin.proto.UnBanUserResp;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
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

/**
 * 验证 UnBanUser 关键语义（与 BanUser 对称）：
 *   - 命中 → resp{Success=true}，status=0
 *   - user 不存在 → NOT_FOUND
 *   - DB 失败 → INTERNAL
 */
@ExtendWith(MockitoExtension.class)
class UnBanUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    UnBanUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new UnBanUserLogic(svcCtx, metrics);
    }

    /** 命中：resp{Success=true}，status=0（解封） */
    @Test
    void success_writesStatusZero() {
        UnBanUserResp resp = logic.unBanUser(UnBanUserReq.newBuilder()
                .setUid(12345L)
                .setOperatorId(99L)
                .build());

        assertTrue(resp.getSuccess());

        ArgumentCaptor<Long> statusCaptor = ArgumentCaptor.forClass(Long.class);
        verify(dataModel).updateUserStatusByUid(org.mockito.ArgumentMatchers.eq(12345L), statusCaptor.capture());
        assertEquals(0L, statusCaptor.getValue(), "UnBanUser 必须写 status=0");
    }

    /** user 不存在 → NOT_FOUND */
    @Test
    void userNotFound_throwsNotFound() {
        doThrow(new NotFoundException("user not found by uid: 12345"))
                .when(dataModel).updateUserStatusByUid(org.mockito.ArgumentMatchers.eq(12345L), org.mockito.ArgumentMatchers.anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.unBanUser(UnBanUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** DB 失败 → INTERNAL */
    @Test
    void dbError_throwsInternal() {
        doThrow(new RuntimeException("connection refused"))
                .when(dataModel).updateUserStatusByUid(org.mockito.ArgumentMatchers.eq(12345L), org.mockito.ArgumentMatchers.anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.unBanUser(UnBanUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}