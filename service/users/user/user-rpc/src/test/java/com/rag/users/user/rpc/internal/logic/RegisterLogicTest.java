package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.CreateUserReq;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Register 关键分支：
 *   - 用户名已存在 → AlreadyExists（不调用 Insert）
 *   - 正常路径 → Insert 带正确的 bcrypt password + snowflake uid
 *
 * 注：bcrypt hash + snowflake ID 是真实调用（不 mock 静态工具），所以 password 必须
 *      PasswordUtil.check(plaintext, hash) 能验证、uid 必须 > 0。
 */
@ExtendWith(MockitoExtension.class)
class RegisterLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    RegisterLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new RegisterLogic(svcCtx, metrics);
    }

    @Test
    void usernameTaken_throwsAlreadyExists_doesNotInsert() {
        // FindOneByUserName 命中（用户已存在）
        User existing = new User();
        existing.setUid(99L);
        existing.setUsername("alice");
        when(dataModel.findOneByUserName("alice")).thenReturn(existing);

        CreateUserReq req = CreateUserReq.newBuilder()
                .setUsername("alice")
                .setPassword("any")
                .setEmail("a@b.c")
                .build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.register(req));
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        verify(dataModel, never()).insert(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void success_insertsWithBcryptPasswordAndSnowflakeUid() {
        // 用户名可用（FindOneByUserName 抛 NotFound）
        when(dataModel.findOneByUserName("newuser"))
                .thenThrow(new NotFoundException("not found"));

        CreateUserReq req = CreateUserReq.newBuilder()
                .setUsername("newuser")
                .setPassword("plainpw")
                .setEmail("n@e.w")
                .build();

        com.rag.users.user.proto.CreateUserResp resp = logic.register(req);

        assertNotNull(resp);
        long generatedUid = resp.getUid();
        assertEquals(true, generatedUid > 0, "uid 必须为 snowflake 正整数");

        // 验证 Insert 被调用，参数正确
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(dataModel).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("newuser", inserted.getUsername());
        assertEquals(generatedUid, inserted.getUid());
        assertEquals(0, inserted.getScore());
        assertEquals(0L, inserted.getStatus());
        assertEquals("n@e.w", inserted.getEmail());
        // password 必须 bcrypt，不能是明文
        assertEquals(false, "plainpw".equals(inserted.getPassword()),
                "password 必须 bcrypt 后写入");
        assertEquals(true,
                com.rag.users.common.cryptx.PasswordUtil.check("plainpw", inserted.getPassword()),
                "bcrypt hash 必须能被 PasswordUtil.check 验证");
    }
}