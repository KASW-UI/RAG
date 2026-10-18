package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import com.rag.users.user.rpc.internal.config.Config;
import com.rag.users.user.rpc.internal.metrics.Metrics;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 验证 D-001 / D-002 关键语义：Login 三态返回（Status=0/1/2）。
 *
 * D-001：Status=1（用户不存在/密码错）和 Status=2（封禁）必须走 resp 字段，不能抛异常
 * D-002：用户不存在 + 密码错共用 Status=1（不区分）
 *
 * 框架对照：Go 端 LoginResp.Status=0/1/2 三个合法分支 + 业务日志区分（Java 端 metrics label 区分）
 */
@ExtendWith(MockitoExtension.class)
class LoginLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：DRIFT-OBS-LOGIN 让 Logic 通过 svcCtx.getLogger() 调 logger，
    // 这里需要 mock LoggerFactoryBean 让 ServiceContext 构造能拿到。
    // lenient: 让不调 logger 的 path 不报 UnnecessaryStubbingException。
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    LoginLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new LoginLogic(svcCtx, metrics);
    }

    /** D-002：用户不存在 → Status=1（不抛异常）*/
    @Test
    void userNotFound_returnsStatus1NotException() {
        when(dataModel.findOneByUserName("ghost"))
                .thenThrow(new NotFoundException("user not found by username: ghost"));

        LoginResp resp = logic.login(LoginReq.newBuilder()
                .setUsername("ghost")
                .setPassword("anypassword")
                .build());

        assertEquals(1L, resp.getStatus(), "user not found 应该返 Status=1，不是异常");
        assertEquals(0L, resp.getUid(), "Status != 0 时 uid 应为 0");
    }

    /** D-002：密码错 → Status=1（和用户不存在共用同一个状态码）*/
    @Test
    void wrongPassword_returnsStatus1NotException() {
        User u = userWith("alice", "correctpw", 0L);
        when(dataModel.findOneByUserName("alice")).thenReturn(u);

        LoginResp resp = logic.login(LoginReq.newBuilder()
                .setUsername("alice")
                .setPassword("wrongpw")
                .build());

        assertEquals(1L, resp.getStatus(), "密码错应该返 Status=1（与用户不存在共用）");
        assertEquals(0L, resp.getUid());
    }

    /** 用户被封禁（DB status=1）→ Status=2（不抛异常）*/
    @Test
    void bannedUser_returnsStatus2NotException() {
        User u = userWith("alice", "correctpw", 1L); // status=1 表示封禁
        when(dataModel.findOneByUserName("alice")).thenReturn(u);

        LoginResp resp = logic.login(LoginReq.newBuilder()
                .setUsername("alice")
                .setPassword("correctpw")
                .build());

        assertEquals(2L, resp.getStatus(), "封禁用户应该返 Status=2，不是异常");
        assertEquals(0L, resp.getUid());
    }

    /** 登录成功 → Status=0 + uid */
    @Test
    void success_returnsStatus0AndUid() {
        User u = userWith("alice", "correctpw", 0L);
        u.setUid(12345L);
        when(dataModel.findOneByUserName("alice")).thenReturn(u);

        LoginResp resp = logic.login(LoginReq.newBuilder()
                .setUsername("alice")
                .setPassword("correctpw")
                .build());

        assertEquals(0L, resp.getStatus());
        assertEquals(12345L, resp.getUid());
    }

    /** DB 异常（非 NotFoundException）→ Internal error（不是 LoginResp.Status）*/
    @Test
    void dbError_throwsInternalStatusRuntimeException() {
        when(dataModel.findOneByUserName("alice"))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.login(LoginReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("any")
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    private static User userWith(String username, String rawPassword, long status) {
        User u = new User();
        u.setUid(0L);
        u.setUsername(username);
        u.setPassword(PasswordUtil.encrypt(rawPassword));
        u.setStatus(status);
        u.setScore(0);
        u.setExtraInfo(new HashMap<>());
        return u;
    }
}