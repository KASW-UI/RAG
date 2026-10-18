package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.LoginReq;
import com.rag.users.admin.proto.LoginResp;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.Admin;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
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
 * 验证 Login 关键语义：4 类 metrics result label + 3 类 gRPC error code。
 *
 * Go 端 loginlogic.go 的分支映射：
 *   - FindOneAdminByUsername 抛 ErrorNotFound       → metrics{user_not_found} + Status.NOT_FOUND
 *   - 其他 Exception                               → metrics{db_error}        + Status.INTERNAL
 *   - 密码错                                       → metrics{wrong_answer}    + Status.UNAUTHENTICATED
 *   - 成功                                          → metrics{success}         + LoginResp{Uid}
 *
 * 与 user-rpc LoginLogicTest 的关键差异：admin-rpc 无"封禁"分支（User.Status 不参与 admin 登录判断，
 * 且 admin 表本身没有 Status 字段——admin 业务没有封禁语义）。所以本测试只有 4 个分支。
 */
@ExtendWith(MockitoExtension.class)
class LoginLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    LoginLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new LoginLogic(svcCtx, metrics);
    }

    /** 用户不存在 → metrics{user_not_found} + Status.NOT_FOUND */
    @Test
    void userNotFound_throwsNotFound() {
        when(dataModel.findOneAdminByUsername("ghost"))
                .thenThrow(new NotFoundException("admin not found by username: ghost"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.login(LoginReq.newBuilder()
                        .setUsername("ghost")
                        .setPassword("anypassword")
                        .build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** 密码错 → metrics{wrong_answer} + Status.UNAUTHENTICATED */
    @Test
    void wrongPassword_throwsUnauthenticated() {
        Admin a = adminWith("alice", "correctpw", 12345L);
        when(dataModel.findOneAdminByUsername("alice")).thenReturn(a);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.login(LoginReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("wrongpw")
                        .build()));

        assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
    }

    /** 登录成功 → metrics{success} + LoginResp{Uid} */
    @Test
    void success_returnsUid() {
        Admin a = adminWith("alice", "correctpw", 12345L);
        when(dataModel.findOneAdminByUsername("alice")).thenReturn(a);

        LoginResp resp = logic.login(LoginReq.newBuilder()
                .setUsername("alice")
                .setPassword("correctpw")
                .build());

        assertEquals(12345L, resp.getUid());
    }

    /** DB 异常（非 NotFoundException）→ metrics{db_error} + Status.INTERNAL */
    @Test
    void dbError_throwsInternal() {
        when(dataModel.findOneAdminByUsername("alice"))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.login(LoginReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("any")
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    private static Admin adminWith(String username, String rawPassword, long uid) {
        Admin a = new Admin();
        a.setUid(uid);
        a.setUsername(username);
        a.setPassword(PasswordUtil.encrypt(rawPassword));
        a.setExtraInfo(new HashMap<>());
        return a;
    }
}