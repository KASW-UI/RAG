package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.rpc.internal.config.Config;
import com.rag.users.user.rpc.internal.model.DataModel;
import com.rag.users.user.rpc.internal.model.UserAvatarHistory;
import com.rag.users.user.rpc.internal.model.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 D-015 关键语义：
 *   D-015：history 查询必须带 uid 隔离条件（防止越权取别人头像）。
 *          DataModel.selectAvatarHistory 内部用 WHERE uid=? AND history_id=?
 *          验证 Logic 把 (uid, historyId) 都正确传过去（具体隔离由 DataModel SQL 保证）。
 */
@ExtendWith(MockitoExtension.class)
class SelectAvatarLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    ServiceContext svcCtx;
    SelectAvatarLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        logic = new SelectAvatarLogic(svcCtx);
    }

    @Test
    void emptyHistoryId_throwsInvalidArgument() {
        SelectAvatarReq req = SelectAvatarReq.newBuilder().setUid(123L).setHistoryId("").build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.selectAvatar(req));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }

    @Test
    void whitespaceHistoryId_throwsInvalidArgument() {
        SelectAvatarReq req = SelectAvatarReq.newBuilder().setUid(123L).setHistoryId("   ").build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.selectAvatar(req));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }

    /** D-015 关键：Logic 必须把 uid 和 historyId 都传给 DataModel，DataModel 内部 SQL 加 uid 隔离 */
    @Test
    void passesBothUidAndHistoryIdToDataModel() {
        UserAvatarHistory hist = new UserAvatarHistory();
        hist.setHistoryId("h-100");
        hist.setUid(123L);
        hist.setAvatarUrl("https://cdn.example.com/x.png");
        hist.setContentType("image/png");
        hist.setIsCurrent(true);
        when(dataModel.selectAvatarHistory(123L, "h-100")).thenReturn(hist);

        SelectAvatarReq req = SelectAvatarReq.newBuilder()
                .setUid(123L)
                .setHistoryId("h-100")
                .build();

        logic.selectAvatar(req);

        // 验证 Logic 确实把 (uid=123, historyId="h-100") 传给 DataModel
        ArgumentCaptor<Long> uidCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> hidCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataModel).selectAvatarHistory(uidCaptor.capture(), hidCaptor.capture());
        assertEquals(123L, uidCaptor.getValue(), "D-015：Logic 必须传 uid 给 DataModel（DataModel SQL 才有 WHERE uid=?）");
        assertEquals("h-100", hidCaptor.getValue());
    }

    @Test
    void historyNotFound_throwsNotFound() {
        when(dataModel.selectAvatarHistory(123L, "missing"))
                .thenThrow(new AvatarHistoryNotFoundException("avatar history not found"));

        SelectAvatarReq req = SelectAvatarReq.newBuilder().setUid(123L).setHistoryId("missing").build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.selectAvatar(req));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    @Test
    void userNotFound_throwsNotFound() {
        when(dataModel.selectAvatarHistory(404L, "h-1"))
                .thenThrow(new NotFoundException("user not found"));

        SelectAvatarReq req = SelectAvatarReq.newBuilder().setUid(404L).setHistoryId("h-1").build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.selectAvatar(req));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }
}