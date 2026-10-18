package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.DeleteUserResp;
import com.rag.users.user.rpc.internal.config.Config;
import com.rag.users.user.rpc.internal.model.DataModel;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 D-003 关键语义：
 *   用户不存在 → NotFound 异常（Go 端 resp.Success=false 是 dead code，Java 端不构造）
 *   + 不会调用 deleteUserByUid
 */
@ExtendWith(MockitoExtension.class)
class DeleteUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    ServiceContext svcCtx;
    DeleteUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        logic = new DeleteUserLogic(svcCtx);
    }

    @Test
    void notFound_throwsNotFound_doesNotDelete() {
        when(dataModel.findOneByUid(404L))
                .thenThrow(new NotFoundException("user not found by uid: 404"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.deleteUser(DeleteUserReq.newBuilder().setUid(404L).build()));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());

        // D-003 衍生：findOneByUid 抛了 NotFound 后不能继续 delete
        verify(dataModel, never()).deleteUserByUid(eq(404L));
    }

    @Test
    void success_returnsSuccessTrue() {
        // findOneByUid 正常返 User（不抛）
        com.rag.users.user.rpc.internal.model.User u = new com.rag.users.user.rpc.internal.model.User();
        u.setUid(123L);
        when(dataModel.findOneByUid(123L)).thenReturn(u);

        DeleteUserResp resp = logic.deleteUser(DeleteUserReq.newBuilder().setUid(123L).build());
        assertEquals(true, resp.getSuccess());
        verify(dataModel).deleteUserByUid(123L);
    }

    @Test
    void dbError_throwsInternal() {
        when(dataModel.findOneByUid(123L))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.deleteUser(DeleteUserReq.newBuilder().setUid(123L).build()));
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}