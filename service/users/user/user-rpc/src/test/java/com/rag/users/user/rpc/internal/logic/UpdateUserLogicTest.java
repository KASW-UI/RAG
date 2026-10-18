package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UpdateUserResp;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 D-004 / D-017 关键语义：
 *   D-004：Username 重名检查不排除自己——把自己改成现值也会 AlreadyExists（Go 端 quirk，1:1 保留）
 *   D-017：DB UPDATE 只写"非空字段"——用动态 JPQL 跳过空 Username/Password/Email/ExtraInfo
 *
 * 框架对照：Go 端 GORM 的 Updates(newUser) 默认跳过零值字段；
 *          Java 端用 DataModel.updateUserById 动态拼接 JPQL 复刻这个行为。
 */
@ExtendWith(MockitoExtension.class)
class UpdateUserLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    ServiceContext svcCtx;
    UpdateUserLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        logic = new UpdateUserLogic(svcCtx);
    }

    /** D-017：只改 username → toUpdate 里只有 username 字段非空 */
    @Test
    void onlyUsername_updateCarriesOnlyUsernameField() {
        // 用户存在，不重名
        when(dataModel.findOneByUserName("newname"))
                .thenThrow(new NotFoundException("not found"));
        // 重读：返回原始 user（验证 resp 内容）
        User fresh = userWith(123L, "newname", "oldhash", "old@example.com");
        when(dataModel.findOneByUid(123L)).thenReturn(fresh);

        UpdateUserReq req = UpdateUserReq.newBuilder()
                .setUid(123L)
                .setUsername("newname")
                // password / email / extraInfo 都不设
                .build();

        UpdateUserResp resp = logic.updateUser(req);

        // 验证只传了 username，其他字段都应该是 null（动态 JPQL 拼接）
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(dataModel).updateUserById(eq(123L), captor.capture());
        User passed = captor.getValue();
        assertEquals("newname", passed.getUsername());
        assertEquals(null, passed.getPassword(), "Password 必须未设（toUpdate 中保持 null）");
        assertEquals(null, passed.getEmail(), "Email 必须未设");
        assertEquals(null, passed.getExtraInfo(), "ExtraInfo 必须未设");

        // resp 包含重读后的 user
        assertEquals(123L, resp.getUser().getUid());
        assertEquals("newname", resp.getUser().getUsername());
    }

    /** D-017：只改 password → toUpdate 里 Password 是 bcrypt 后的 hash */
    @Test
    void onlyPassword_updateCarriesBcryptHash() {
        // 不查重名（username 空，不查）
        User fresh = userWith(123L, "alice", "oldhash", "a@b.c");
        when(dataModel.findOneByUid(123L)).thenReturn(fresh);

        UpdateUserReq req = UpdateUserReq.newBuilder()
                .setUid(123L)
                .setPassword("newpw")
                .build();

        logic.updateUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(dataModel).updateUserById(eq(123L), captor.capture());
        User passed = captor.getValue();
        // Password 必须被 bcrypt，不能是明文
        assertNotEquals("newpw", passed.getPassword(), "Password 必须 bcrypt 后传入，不能是明文");
        assertTrue(PasswordUtil.check("newpw", passed.getPassword()),
                "Password 必须能被 PasswordUtil.check('newpw', ...) 验证为正确");
        assertEquals(null, passed.getUsername());
        assertEquals(null, passed.getEmail());
        assertEquals(null, passed.getExtraInfo());
    }

    /** D-004 quirk：把自己用户名改成自己现值 → AlreadyExists（不排除自己）*/
    @Test
    void selfUsernameChange_throwsAlreadyExists() {
        // FindOneByUserName 命中（即使是用户自己）
        User self = userWith(123L, "alice", "hash", "a@b.c");
        when(dataModel.findOneByUserName("alice")).thenReturn(self);

        UpdateUserReq req = UpdateUserReq.newBuilder()
                .setUid(123L)
                .setUsername("alice")
                .build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.updateUser(req));
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode(),
                "D-004 quirk：把自己用户名改成现值应返 AlreadyExists");

        // D-017：没走到 update
        verify(dataModel, never()).updateUserById(any(Long.class), any(User.class));
    }

    /** 用户不存在（重名冲突） → AlreadyExists */
    @Test
    void usernameTakenByOther_throwsAlreadyExists() {
        User other = userWith(456L, "alice", "hash", "x@y.z");
        when(dataModel.findOneByUserName("alice")).thenReturn(other);

        UpdateUserReq req = UpdateUserReq.newBuilder()
                .setUid(123L)
                .setUsername("alice")
                .build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.updateUser(req));
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        verify(dataModel, never()).updateUserById(any(Long.class), any(User.class));
    }

    /** 重读时用户已被并发删 → NotFound */
    @Test
    void userDeletedConcurrently_throwsNotFound() {
        when(dataModel.findOneByUserName("newname"))
                .thenThrow(new NotFoundException("not found"));
        // update 成功
        // 但重读时返 NotFound
        when(dataModel.findOneByUid(123L))
                .thenThrow(new NotFoundException("user gone"));

        UpdateUserReq req = UpdateUserReq.newBuilder()
                .setUid(123L)
                .setUsername("newname")
                .build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.updateUser(req));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    private static User userWith(long uid, String username, String passwordHash, String email) {
        User u = new User();
        u.setUid(uid);
        u.setUsername(username);
        u.setPassword(passwordHash);
        u.setEmail(email);
        u.setStatus(0L);
        u.setExtraInfo(new HashMap<>());
        return u;
    }
}