package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.DeleteUserReq;
import com.rag.users.admin.proto.DeleteUserResp;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 验证 DeleteUser 关键语义：
 *   - 命中 → resp{Success=true}
 *   - user 不存在（DataModel.deleteUserByUid 抛 NotFoundException）→ NOT_FOUND
 *   - DB 失败 → INTERNAL
 */
@ExtendWith(MockitoExtension.class)
class DeleteUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    DeleteUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new DeleteUserLogic(svcCtx, metrics);
    }

    /** 命中 → resp{Success=true} */
    @Test
    void success_returnsSuccessTrue() {
        DeleteUserResp resp = logic.deleteUser(DeleteUserReq.newBuilder()
                .setUid(12345L)
                .setOperatorId(99L)
                .build());

        assertTrue(resp.getSuccess());
        verify(dataModel).deleteUserByUid(12345L);
    }

    /** user 不存在 → NOT_FOUND，错误描述带 uid */
    @Test
    void userNotFound_throwsNotFound() {
        doThrow(new NotFoundException("user not found by uid: 12345"))
                .when(dataModel).deleteUserByUid(12345L);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.deleteUser(DeleteUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** DB 失败 → INTERNAL */
    @Test
    void dbError_throwsInternal() {
        doThrow(new RuntimeException("connection refused"))
                .when(dataModel).deleteUserByUid(12345L);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.deleteUser(DeleteUserReq.newBuilder()
                        .setUid(12345L)
                        .setOperatorId(99L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}