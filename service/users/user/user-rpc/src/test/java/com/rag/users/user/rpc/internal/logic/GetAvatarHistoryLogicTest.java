package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.rpc.internal.config.Config;
import com.rag.users.user.rpc.internal.model.DataModel;
import com.rag.users.user.rpc.internal.model.UserAvatarHistory;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 D-010 关键语义：limit 直接透传到 DataModel.listAvatarHistory，
 * 实际 clamp 到 [1, 30] 是 DataModel 内部逻辑（D-010：静默 clamp）。
 *
 * 注：本测试不直接验证 clamp 行为（那是 DataModel 的事），只验证 Logic 把 limit 透传了。
 *       DataModel 层的 clamp 测试需要真实 DB，留给集成测试。
 */
@ExtendWith(MockitoExtension.class)
class GetAvatarHistoryLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    ServiceContext svcCtx;
    GetAvatarHistoryLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        logic = new GetAvatarHistoryLogic(svcCtx);
    }

    @Test
    void limit_passedThroughToDataModel() {
        when(dataModel.findOneByUid(123L)).thenReturn(new com.rag.users.user.rpc.internal.model.User());
        when(dataModel.listAvatarHistory(eq(123L), anyInt())).thenReturn(Collections.emptyList());

        GetAvatarHistoryReq req = GetAvatarHistoryReq.newBuilder()
                .setUid(123L)
                .setLimit(10)
                .build();

        logic.getAvatarHistory(req);

        // Logic 不应该对 limit 做预 clamp，直接透传
        verify(dataModel).listAvatarHistory(123L, 10);
    }

    @Test
    void userNotFound_throwsNotFound() {
        when(dataModel.findOneByUid(404L))
                .thenThrow(new NotFoundException("user not found"));

        GetAvatarHistoryReq req = GetAvatarHistoryReq.newBuilder().setUid(404L).build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.getAvatarHistory(req));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    @Test
    void listDbError_throwsInternal() {
        when(dataModel.findOneByUid(123L)).thenReturn(new com.rag.users.user.rpc.internal.model.User());
        when(dataModel.listAvatarHistory(anyLong(), anyInt()))
                .thenThrow(new RuntimeException("db error"));

        GetAvatarHistoryReq req = GetAvatarHistoryReq.newBuilder().setUid(123L).setLimit(30).build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.getAvatarHistory(req));
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}