package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.BanUserReq;
import com.rag.users.admin.proto.BanUserResp;
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
 * 验证 BanUser 关键语义：
 *   - 命中 → resp{Success=true}，updateUserStatusByUid 被调用，status=1
 *   - user 不存在（DataModel.updateUserStatusByUid 抛 NotFoundException）→ NOT_FOUND
 *   - DB 失败 → INTERNAL
 */
@ExtendWith(MockitoExtension.class)
class BanUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    BanUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new BanUserLogic(svcCtx, metrics);
    }

    /** 命中：resp{Success=true}，status=1（封禁） */
    @Test
    void success_writesStatusOne() {
        BanUserResp resp = logic.banUser(BanUserReq.newBuilder()
                .setUid(12345L)
                .setOperatorId(99L)
                .build());

        assertTrue(resp.getSuccess());

        ArgumentCaptor<Long> statusCaptor = ArgumentCaptor.forClass(Long.class);
        verify(dataModel).updateUserStatusByUid(org.mockito.ArgumentMatchers.eq(12345L), statusCaptor.capture());
        assertEquals(1L, statusCaptor.getValue(), "BanUser 必须写 status=1");
    }

    /** user 不存在 → NOT_FOUND */
    @Test
    void userNotFound_throwsNotFound() {
        doThrow(new NotFoundException("user not found by uid: 12345"))
                .when(dataModel).updateUserStatusByUid(org.mockito.ArgumentMatchers.eq(12345L), org.mockito.ArgumentMatchers.anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.banUser(BanUserReq.newBuilder()
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
                logic.banUser(BanUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}