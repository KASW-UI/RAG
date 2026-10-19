package com.rag.users.user.rpc.service;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.CreateUserResp;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.rpc.config.UserRpcProperties;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.exception.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.exception.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.metrics.UserRpcMetrics;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserDomainService 单元测试 —— 9 个用例方法覆盖。
 *
 * 用 Mockito mock UserMapper / UserAvatarHistoryMapper / RedisTemplate / Metrics，
 * 不依赖数据库。Go parity 用例重点覆盖：
 *   - Login 三态（success / user_not_found / password_error / banned）
 *   - Register 查重 + bcrypt + snowflake + insert
 *   - GetUser / UpdateUser / DeleteUser 正常路径 + NotFound 路径
 *   - Logout Redis Setex 成功 + token 为空失败
 *   - SelectAvatar / UploadAvatar / GetAvatarHistory 成功 + 各种 NotFound 路径
 */
class UserDomainServiceTest {

    private UserMapper userMapper;
    private UserAvatarHistoryMapper avatarHistoryMapper;
    private UserRpcMetrics metrics;
    private StringRedisTemplate bizRedis;
    private ValueOperations<String, String> valueOps;
    private LoggerFactoryBean logger;
    private UserRpcProperties properties;
    private AvatarDomainService avatarDomainService;
    private UserDomainService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        avatarHistoryMapper = mock(UserAvatarHistoryMapper.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metrics = new UserRpcMetrics(registry);
        bizRedis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(bizRedis.opsForValue()).thenReturn(valueOps);
        logger = mock(LoggerFactoryBean.class);
        properties = new UserRpcProperties();
        properties.getUserAuth().setAccessSecret("test-secret");
        properties.getUserAuth().setAccessExpire(2592000L);
        avatarDomainService = new AvatarDomainService();
        service = new UserDomainService(
                userMapper, avatarHistoryMapper, metrics, bizRedis, logger, properties, avatarDomainService);
    }

    // ============== login() ==============

    @Test
    void login_success_returnsUidAndStatusZero() {
        UserEntity user = new UserEntity();
        user.setUid(123L);
        user.setUsername("alice");
        user.setPassword(com.rag.users.common.cryptx.PasswordUtil.encrypt("password"));
        user.setStatus(0L);
        when(userMapper.findOneByUserName("alice")).thenReturn(user);

        LoginResp resp = service.login(LoginReq.newBuilder().setUsername("alice").setPassword("password").build());

        assertThat(resp.getUid()).isEqualTo(123L);
        assertThat(resp.getStatus()).isEqualTo(0L);
    }

    @Test
    void login_userNotFound_returnsStatusOne() {
        doThrow(new NotFoundException("user not found"))
                .when(userMapper).findOneByUserName("missing");

        LoginResp resp = service.login(LoginReq.newBuilder().setUsername("missing").setPassword("password").build());

        assertThat(resp.getStatus()).isEqualTo(1L);
        assertThat(resp.getUid()).isEqualTo(0L);
    }

    @Test
    void login_wrongPassword_returnsStatusOne() {
        UserEntity user = new UserEntity();
        user.setUid(123L);
        user.setUsername("alice");
        user.setPassword(com.rag.users.common.cryptx.PasswordUtil.encrypt("correct-password"));
        user.setStatus(0L);
        when(userMapper.findOneByUserName("alice")).thenReturn(user);

        LoginResp resp = service.login(LoginReq.newBuilder().setUsername("alice").setPassword("wrong-password").build());

        assertThat(resp.getStatus()).isEqualTo(1L);
        assertThat(resp.getUid()).isEqualTo(0L);
    }

    @Test
    void login_bannedUser_returnsStatusTwo() {
        UserEntity user = new UserEntity();
        user.setUid(123L);
        user.setUsername("alice");
        user.setPassword(com.rag.users.common.cryptx.PasswordUtil.encrypt("password"));
        user.setStatus(1L); // banned
        when(userMapper.findOneByUserName("alice")).thenReturn(user);

        LoginResp resp = service.login(LoginReq.newBuilder().setUsername("alice").setPassword("password").build());

        assertThat(resp.getStatus()).isEqualTo(2L);
        assertThat(resp.getUid()).isEqualTo(0L);
    }

    // ============== register() ==============

    @Test
    void register_success_returnsNewUid() {
        when(userMapper.findOneByUserName("newuser"))
                .thenThrow(new NotFoundException("not found"));

        CreateUserResp resp = service.register(
                CreateUserReq.newBuilder().setUsername("newuser").setPassword("password").build());

        assertThat(resp.getUid()).isGreaterThan(0L);
        verify(userMapper, times(1)).insert(any(UserEntity.class));
    }

    @Test
    void register_usernameExists_throwsAlreadyExists() {
        UserEntity existing = new UserEntity();
        existing.setUsername("existing");
        when(userMapper.findOneByUserName("existing")).thenReturn(existing);

        assertThatThrownBy(() -> service.register(
                CreateUserReq.newBuilder().setUsername("existing").setPassword("password").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("用户名已存在");
    }

    // ============== getUser() ==============

    @Test
    void getUser_success_returnsUserInfo() {
        UserEntity user = new UserEntity();
        user.setUid(456L);
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        user.setStatus(0L);
        user.setExtraInfo(new HashMap<>());
        when(userMapper.findOneByUid(456L)).thenReturn(user);

        GetUserResp resp = service.getUser(GetUserReq.newBuilder().setUid(456L).build());

        assertThat(resp.getFound()).isTrue();
        assertThat(resp.getUser().getUid()).isEqualTo(456L);
        assertThat(resp.getUser().getUsername()).isEqualTo("bob");
        assertThat(resp.getUser().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void getUser_notFound_throwsNotFound() {
        doThrow(new NotFoundException("not found")).when(userMapper).findOneByUid(999L);

        assertThatThrownBy(() -> service.getUser(GetUserReq.newBuilder().setUid(999L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    // ============== updateUser() ==============

    @Test
    void updateUser_partialUpdate_onlyWritesNonEmptyFields() {
        UserEntity fresh = new UserEntity();
        fresh.setUid(789L);
        fresh.setUsername("newname");
        fresh.setEmail("new@example.com");
        fresh.setExtraInfo(new HashMap<>());
        doThrow(new NotFoundException("username taken check"))
                .when(userMapper).findOneByUserName(anyString());
        when(userMapper.findOneByUid(789L)).thenReturn(fresh);

        UpdateUserReq req = UpdateUserReq.newBuilder()
                .setUid(789L)
                .setUsername("newname")
                .setEmail("new@example.com")
                .build();

        var resp = service.updateUser(req);
        assertThat(resp.getUser().getUsername()).isEqualTo("newname");
        verify(userMapper, times(1)).updateUserById(anyLong(), any(UserEntity.class));
    }

    // ============== deleteUser() ==============

    @Test
    void deleteUser_success() {
        UserEntity user = new UserEntity();
        when(userMapper.findOneByUid(321L)).thenReturn(user);
        when(userMapper.deleteUserByUid(321L)).thenReturn(1);

        var resp = service.deleteUser(DeleteUserReq.newBuilder().setUid(321L).build());
        assertThat(resp.getSuccess()).isTrue();
    }

    @Test
    void deleteUser_notFound_throwsNotFound() {
        doThrow(new NotFoundException("not found")).when(userMapper).findOneByUid(999L);

        assertThatThrownBy(() -> service.deleteUser(DeleteUserReq.newBuilder().setUid(999L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    // ============== logout() ==============

    @Test
    void logout_writesRedisWithCorrectKeyAndTtl() {
        var resp = service.logout(LogoutReq.newBuilder().setToken("jwt-abc").build());
        assertThat(resp).isNotNull();
        verify(valueOps, times(1)).set(
                org.mockito.ArgumentMatchers.eq("user:jwt_blacklist:jwt-abc"),
                org.mockito.ArgumentMatchers.eq("banned"),
                org.mockito.ArgumentMatchers.eq(java.time.Duration.ofSeconds(2592000L)));
    }

    @Test
    void logout_emptyToken_throwsInvalidArgument() {
        assertThatThrownBy(() -> service.logout(LogoutReq.newBuilder().setToken("").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Token不能为空");
    }

    // ============== selectAvatar() ==============

    @Test
    void selectAvatar_emptyHistoryId_throwsInvalidArgument() {
        assertThatThrownBy(() -> service.selectAvatar(SelectAvatarReq.newBuilder().setUid(1L).setHistoryId("").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("history_id is required");
    }

    @Test
    void selectAvatar_historyNotFound_throwsNotFound() {
        UserEntity user = new UserEntity();
        when(userMapper.findOneByUid(1L)).thenReturn(user);
        when(avatarHistoryMapper.selectAvatarHistory(any(), anyLong(), anyString()))
                .thenThrow(new AvatarHistoryNotFoundException("history not found"));

        assertThatThrownBy(() -> service.selectAvatar(
                SelectAvatarReq.newBuilder().setUid(1L).setHistoryId("history-1").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("头像历史不存在");
    }

    // ============== uploadAvatar() ==============

    @Test
    void uploadAvatar_emptyUrl_throwsInvalidArgument() {
        assertThatThrownBy(() -> service.uploadAvatar(UploadAvatarReq.newBuilder().setUid(1L).setAvatarUrl("").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("avatar_url is required");
    }

    @Test
    void uploadAvatar_success_returnsHistory() {
        UserAvatarHistoryEntity history = new UserAvatarHistoryEntity();
        history.setId(100L);
        history.setHistoryId("snowflake-id");
        history.setUid(1L);
        history.setAvatarUrl("http://example.com/avatar.png");
        history.setContentType("image/png");
        history.setSizeBytes(1024L);
        history.setIsCurrent(true);
        history.setCreateTime(LocalDateTime.now());
        when(avatarHistoryMapper.createAvatarHistory(any(), anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(history);

        var resp = service.uploadAvatar(UploadAvatarReq.newBuilder()
                .setUid(1L).setAvatarUrl("http://example.com/avatar.png")
                .setContentType("image/png").setSizeBytes(1024L).build());

        assertThat(resp.getAvatarUrl()).isEqualTo("http://example.com/avatar.png");
        assertThat(resp.getHistory().getId()).isEqualTo("snowflake-id");
        assertThat(resp.getHistory().getIsCurrent()).isTrue();
    }

    // ============== getAvatarHistory() ==============

    @Test
    void getAvatarHistory_success_returnsList() {
        UserEntity user = new UserEntity();
        when(userMapper.findOneByUid(1L)).thenReturn(user);
        UserAvatarHistoryEntity h1 = new UserAvatarHistoryEntity();
        h1.setHistoryId("h-1");
        h1.setUid(1L);
        h1.setAvatarUrl("http://a/1.png");
        h1.setCreateTime(LocalDateTime.now());
        // req.getLimit() == 10 直接传入 mapper；mapper 内部 default 方法再做 clamp，
        // 但这里测的是 UserDomainService → mapper 的调用，所以按 (uid=1, limit=10) mock
        when(avatarHistoryMapper.listAvatarHistory(1L, 10))
                .thenReturn(java.util.Collections.singletonList(h1));

        var resp = service.getAvatarHistory(GetAvatarHistoryReq.newBuilder().setUid(1L).setLimit(10).build());
        assertThat(resp.getListList()).hasSize(1);
        assertThat(resp.getList(0).getId()).isEqualTo("h-1");
    }

    @Test
    void getAvatarHistory_userNotFound_throwsNotFound() {
        doThrow(new NotFoundException("not found")).when(userMapper).findOneByUid(999L);

        assertThatThrownBy(() -> service.getAvatarHistory(GetAvatarHistoryReq.newBuilder().setUid(999L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    // ============== metrics 验证 ==============

    @Test
    void login_successIncrementsLoginSuccessCounter() {
        UserEntity user = new UserEntity();
        user.setUid(1L);
        user.setPassword(com.rag.users.common.cryptx.PasswordUtil.encrypt("p"));
        user.setStatus(0L);
        when(userMapper.findOneByUserName("alice")).thenReturn(user);

        service.login(LoginReq.newBuilder().setUsername("alice").setPassword("p").build());

        Counter c = metrics.getLoginSuccess();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void login_userNotFoundIncrementsLoginUserNotFoundCounter() {
        doThrow(new NotFoundException("not found"))
                .when(userMapper).findOneByUserName("missing");

        service.login(LoginReq.newBuilder().setUsername("missing").setPassword("p").build());

        Counter c = metrics.getLoginUserNotFound();
        assertThat(c.count()).isEqualTo(1.0);
    }
}