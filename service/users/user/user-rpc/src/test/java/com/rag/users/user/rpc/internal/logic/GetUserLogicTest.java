package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.rpc.internal.config.Config;
import com.rag.users.user.rpc.internal.model.DataModel;
import com.rag.users.user.rpc.internal.model.User;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 验证 D-011 关键语义：
 *   D-011：成功时 UserInfo.Status 字段必须填（proto3 optional int64，非 nil），
 *          identity 解析依赖 hasStatus() && Status == 0 来判定 active。
 *
 * 验证 D-003：用户不存在 → NotFound 异常（Go 端 resp.Found=false 是 dead code，Java 端不构造）。
 */
@ExtendWith(MockitoExtension.class)
class GetUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    ServiceContext svcCtx;
    GetUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        logic = new GetUserLogic(svcCtx);
    }

    /** D-011：成功时 UserInfo.Status 必须被设置（proto hasStatus() 返回 true） */
    @Test
    void success_statusFieldIsPresent() {
        User u = userWith(123L, "alice", "a@b.c", 0L);
        when(dataModel.findOneByUid(123L)).thenReturn(u);

        GetUserResp resp = logic.getUser(GetUserReq.newBuilder().setUid(123L).build());

        assertEquals(true, resp.getFound());
        UserInfo info = resp.getUser();
        assertNotNull(info);
        assertEquals(123L, info.getUid());
        // D-011 关键：Status 必须被设置（不是默认 0），identity 解析依赖 hasStatus() && Status == 0
        assertTrue(info.hasStatus(), "D-011：UserInfo.Status 字段必须被设置，hasStatus() 应返 true");
        assertEquals(Long.valueOf(0L), info.getStatus(), "active user status 应为 0");
    }

    /** D-003：用户不存在 → NotFound 异常（不是 resp.Found=false + null） */
    @Test
    void notFound_throwsNotFoundException() {
        when(dataModel.findOneByUid(404L))
                .thenThrow(new NotFoundException("user not found by uid: 404"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.getUser(GetUserReq.newBuilder().setUid(404L).build()));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** DB 异常 → Internal */
    @Test
    void dbError_throwsInternal() {
        when(dataModel.findOneByUid(123L))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.getUser(GetUserReq.newBuilder().setUid(123L).build()));
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