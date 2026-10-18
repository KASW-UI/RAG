package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.proto.UploadAvatarResp;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 D-012 / D-013 关键语义：
 *   D-012：content_type 空 → "image/png"（默认填充，不抛 InvalidArgument）
 *   D-013：history_id 是服务端 snowflake 生成的字符串（不是请求传入的）
 *
 * 注：D-005 事务原子性需要真实 Postgres（@Transactional + JPA）才能验证；
 *     这里只验证 input 归一化和 history_id 来源。
 */
@ExtendWith(MockitoExtension.class)
class UploadAvatarLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    ServiceContext svcCtx;
    UploadAvatarLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        logic = new UploadAvatarLogic(svcCtx);
    }

    @Test
    void emptyAvatarUrl_throwsInvalidArgument() {
        UploadAvatarReq req = UploadAvatarReq.newBuilder()
                .setUid(123L)
                .setAvatarUrl("")
                .setContentType("image/png")
                .build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.uploadAvatar(req));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }

    @Test
    void emptyContentType_defaultsToImagePng() {
        UserAvatarHistory hist = new UserAvatarHistory();
        hist.setHistoryId("100");
        hist.setUid(123L);
        hist.setAvatarUrl("https://cdn.example.com/avatar.png");
        hist.setContentType("image/png"); // 默认值
        hist.setSizeBytes(1024L);
        hist.setIsCurrent(true);
        when(dataModel.createAvatarHistory(anyLong(), anyString(), anyString(), eq("image/png"), any()))
                .thenReturn(hist);

        UploadAvatarReq req = UploadAvatarReq.newBuilder()
                .setUid(123L)
                .setAvatarUrl("https://cdn.example.com/avatar.png")
                // content_type 空
                .setSizeBytes(1024L)
                .build();

        UploadAvatarResp resp = logic.uploadAvatar(req);

        // D-012：content_type 默认 "image/png"
        verify(dataModel).createAvatarHistory(eq(123L), anyString(), eq("https://cdn.example.com/avatar.png"),
                eq("image/png"), eq(1024L));
        assertEquals("image/png", resp.getHistory().getContentType());
    }

    /** D-013：history_id 是服务端 snowflake 生成（不是请求传入），且 positive 之后创建 */
    @Test
    void historyIdIsServerGeneratedSnowflakeId() {
        UserAvatarHistory hist = new UserAvatarHistory();
        hist.setHistoryId("999");
        hist.setUid(123L);
        hist.setAvatarUrl("https://cdn.example.com/x.png");
        hist.setContentType("image/png");
        hist.setSizeBytes(1024L);
        hist.setIsCurrent(true);
        when(dataModel.createAvatarHistory(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(hist);

        UploadAvatarReq req = UploadAvatarReq.newBuilder()
                .setUid(123L)
                .setAvatarUrl("https://cdn.example.com/x.png")
                .setContentType("image/png")
                .setSizeBytes(1024L)
                .build();

        UploadAvatarResp resp = logic.uploadAvatar(req);

        // 捕获实际传入的 historyId
        ArgumentCaptor<String> historyIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataModel).createAvatarHistory(eq(123L), historyIdCaptor.capture(),
                anyString(), anyString(), any());
        String serverGeneratedId = historyIdCaptor.getValue();
        assertNotNull(serverGeneratedId);
        // snowflake ID 是 long 转字符串，应该是纯数字且 > 0
        long parsed = Long.parseLong(serverGeneratedId);
        assertEquals(true, parsed > 0, "D-013：history_id 必须为 snowflake 正整数");
    }

    @Test
    void userNotFound_throwsNotFound() {
        when(dataModel.createAvatarHistory(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new NotFoundException("user not found by uid: 404"));

        UploadAvatarReq req = UploadAvatarReq.newBuilder()
                .setUid(404L)
                .setAvatarUrl("https://cdn.example.com/x.png")
                .build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.uploadAvatar(req));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }
}