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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper, times(1)).insert(captor.capture());
        UserEntity inserted = captor.getValue();
        assertThat(inserted.getUsername()).isEqualTo("newuser");
        assertThat(inserted.getUid()).isEqualTo(resp.getUid());
        assertThat(inserted.getScore()).isEqualTo(0);
        assertThat(inserted.getStatus()).isEqualTo(0L);
        // 密码必须 bcrypt，不能是明文
        assertThat(inserted.getPassword()).isNotEqualTo("password");
        assertThat(com.rag.users.common.cryptx.PasswordUtil.check("password", inserted.getPassword())).isTrue();
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
        // 对齐参考 RegisterLogicTest.usernameTaken_throwsAlreadyExists_doesNotInsert
        verify(userMapper, never()).insert(any(UserEntity.class));
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
        // D-011：Status 是 proto3 optional，必须显式设置（hasStatus()），identity 解析依赖它
        assertThat(resp.getUser().hasStatus()).isTrue();
        assertThat(resp.getUser().getStatus()).isEqualTo(0L);
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

        // D-017：只把「显式传入且非空」的字段交给 mapper，未传的必须保持 null
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper, times(1)).updateUserById(eq(789L), captor.capture());
        UserEntity passed = captor.getValue();
        assertThat(passed.getUsername()).isEqualTo("newname");
        assertThat(passed.getPassword()).isNull();
        assertThat(passed.getExtraInfo()).isNull();
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
        // D-003：查不到用户时不能继续删
        verify(userMapper, never()).deleteUserByUid(anyLong());
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
        // D-010：domain 层不做 clamp，limit 原样透传（clamp 在 mapper default 方法内）
        verify(avatarHistoryMapper, times(1)).listAvatarHistory(eq(1L), eq(10));
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

    // ==================================================================
    // 以下用例从参考实现 Sea-RideTheWind_java 的 9 个 LogicTest 补回。
    // 之前「9 个 Logic 合并成 1 个 UserDomainService」时，这批覆盖错误分支
    // 与 D-004 / D-010 / D-012 / D-013 / D-015 的断言被一并删除，
    // 导致 db_error 分支与各类输入归一化行为失去证据支撑。
    // ==================================================================

    // ============== DB 故障分支（db_error → INTERNAL）==============

    @Test
    void login_dbError_throwsInternal() {
        when(userMapper.findOneByUserName("alice")).thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.login(LoginReq.newBuilder().setUsername("alice").setPassword("x").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(e.getStatus().getDescription()).isEqualTo("DB查询失败");
    }

    @Test
    void getUser_dbError_throwsInternal() {
        when(userMapper.findOneByUid(456L)).thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.getUser(GetUserReq.newBuilder().setUid(456L).build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void deleteUser_dbError_throwsInternal() {
        when(userMapper.findOneByUid(321L)).thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.deleteUser(DeleteUserReq.newBuilder().setUid(321L).build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        // DB 查询失败时不能进入删除
        verify(userMapper, never()).deleteUserByUid(anyLong());
    }

    @Test
    void getAvatarHistory_listDbError_throwsInternal() {
        UserEntity user = new UserEntity();
        when(userMapper.findOneByUid(1L)).thenReturn(user);
        when(avatarHistoryMapper.listAvatarHistory(anyLong(), anyInt()))
                .thenThrow(new RuntimeException("db error"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.getAvatarHistory(GetAvatarHistoryReq.newBuilder().setUid(1L).setLimit(30).build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void logout_redisError_throwsInternal() {
        doThrow(new RuntimeException("redis down")).when(valueOps)
                .set(anyString(), anyString(), any(java.time.Duration.class));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.logout(LogoutReq.newBuilder().setToken("any.jwt.token").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(e.getStatus().getDescription()).isEqualTo("注销失败,Redis写入异常");
    }

    // ============== selectAvatar ==============

    @Test
    void selectAvatar_whitespaceHistoryId_throwsInvalidArgument() {
        assertThrows(StatusRuntimeException.class, () -> service.selectAvatar(
                SelectAvatarReq.newBuilder().setUid(1L).setHistoryId("   ").build()));

        // 归一化后为空 → 必须在碰 mapper 之前就拒绝
        verify(avatarHistoryMapper, never()).selectAvatarHistory(any(), anyLong(), anyString());
    }

    /** D-015：uid 必须一起下传给 mapper，SQL 才有 WHERE uid=? 的越权隔离 */
    @Test
    void selectAvatar_passesBothUidAndHistoryIdToMapper() {
        UserAvatarHistoryEntity hist = new UserAvatarHistoryEntity();
        hist.setId(7L);
        hist.setHistoryId("h-100");
        hist.setUid(1L);
        hist.setAvatarUrl("https://cdn.example.com/x.png");
        hist.setContentType("image/png");
        hist.setIsCurrent(true);
        when(avatarHistoryMapper.selectAvatarHistory(any(), eq(1L), eq("h-100"))).thenReturn(hist);

        service.selectAvatar(SelectAvatarReq.newBuilder().setUid(1L).setHistoryId("h-100").build());

        ArgumentCaptor<Long> uidCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> hidCaptor = ArgumentCaptor.forClass(String.class);
        verify(avatarHistoryMapper).selectAvatarHistory(any(), uidCaptor.capture(), hidCaptor.capture());
        assertThat(uidCaptor.getValue()).isEqualTo(1L);
        assertThat(hidCaptor.getValue()).isEqualTo("h-100");
    }

    @Test
    void selectAvatar_userNotFound_throwsNotFound() {
        when(avatarHistoryMapper.selectAvatarHistory(any(), eq(404L), anyString()))
                .thenThrow(new NotFoundException("user not found"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.selectAvatar(SelectAvatarReq.newBuilder().setUid(404L).setHistoryId("h-1").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(e.getStatus().getDescription()).isEqualTo("用户不存在");
    }

    // ============== updateUser（D-004 / D-017）==============

    @Test
    void updateUser_onlyPassword_carriesBcryptHash() {
        UserEntity fresh = new UserEntity();
        fresh.setUid(123L);
        fresh.setUsername("alice");
        fresh.setExtraInfo(new HashMap<>());
        when(userMapper.findOneByUid(123L)).thenReturn(fresh);

        service.updateUser(UpdateUserReq.newBuilder().setUid(123L).setPassword("newpw").build());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateUserById(eq(123L), captor.capture());
        UserEntity passed = captor.getValue();
        // D-017：密码必须 bcrypt，且只传 password 一个字段
        assertThat(passed.getPassword()).isNotEqualTo("newpw");
        assertThat(com.rag.users.common.cryptx.PasswordUtil.check("newpw", passed.getPassword())).isTrue();
        assertThat(passed.getUsername()).isNull();
        assertThat(passed.getEmail()).isNull();
        assertThat(passed.getExtraInfo()).isNull();
    }

    /** D-004 quirk：把自己的用户名改成现值也算重名（Go 端行为 1:1 保留） */
    @Test
    void updateUser_selfUsernameChange_throwsAlreadyExists() {
        UserEntity self = new UserEntity();
        self.setUid(123L);
        self.setUsername("alice");
        when(userMapper.findOneByUserName("alice")).thenReturn(self);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.updateUser(UpdateUserReq.newBuilder().setUid(123L).setUsername("alice").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        verify(userMapper, never()).updateUserById(anyLong(), any(UserEntity.class));
    }

    @Test
    void updateUser_usernameTakenByOther_throwsAlreadyExists() {
        UserEntity other = new UserEntity();
        other.setUid(456L);
        other.setUsername("alice");
        when(userMapper.findOneByUserName("alice")).thenReturn(other);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.updateUser(UpdateUserReq.newBuilder().setUid(123L).setUsername("alice").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        verify(userMapper, never()).updateUserById(anyLong(), any(UserEntity.class));
    }

    @Test
    void updateUser_userDeletedConcurrently_throwsNotFound() {
        doThrow(new NotFoundException("not found")).when(userMapper).findOneByUserName(anyString());
        when(userMapper.findOneByUid(123L)).thenThrow(new NotFoundException("user gone"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.updateUser(UpdateUserReq.newBuilder().setUid(123L).setUsername("newname").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ============== uploadAvatar（D-012 / D-013）==============

    /** D-012：content_type 为空 / 纯空白 → 默认 image/png（静默，不抛异常） */
    @Test
    void uploadAvatar_emptyContentType_defaultsToImagePng() {
        UserAvatarHistoryEntity hist = new UserAvatarHistoryEntity();
        hist.setId(9L);
        hist.setHistoryId("100");
        hist.setUid(1L);
        hist.setAvatarUrl("https://cdn.example.com/avatar.png");
        hist.setContentType("image/png");
        hist.setSizeBytes(1024L);
        hist.setIsCurrent(true);
        when(avatarHistoryMapper.createAvatarHistory(
                any(), eq(1L), anyString(), anyString(), eq("image/png"), eq(1024L))).thenReturn(hist);

        service.uploadAvatar(UploadAvatarReq.newBuilder()
                .setUid(1L).setAvatarUrl("https://cdn.example.com/avatar.png")
                .setSizeBytes(1024L).build());

        verify(avatarHistoryMapper).createAvatarHistory(
                any(), eq(1L), anyString(),
                eq("https://cdn.example.com/avatar.png"), eq("image/png"), eq(1024L));
    }

    /** D-013：history_id 由服务端 Snowflake 生成，不接受请求传入 */
    @Test
    void uploadAvatar_historyIdIsServerGeneratedSnowflakeId() {
        UserAvatarHistoryEntity hist = new UserAvatarHistoryEntity();
        hist.setId(10L);
        hist.setHistoryId("999");
        hist.setUid(1L);
        hist.setAvatarUrl("https://cdn.example.com/x.png");
        hist.setContentType("image/png");
        hist.setSizeBytes(1024L);
        hist.setIsCurrent(true);
        when(avatarHistoryMapper.createAvatarHistory(
                any(), anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(hist);

        service.uploadAvatar(UploadAvatarReq.newBuilder()
                .setUid(1L).setAvatarUrl("https://cdn.example.com/x.png")
                .setContentType("image/png").setSizeBytes(1024L).build());

        ArgumentCaptor<String> historyIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(avatarHistoryMapper).createAvatarHistory(
                any(), eq(1L), historyIdCaptor.capture(), anyString(), anyString(), any());
        String generated = historyIdCaptor.getValue();
        assertThat(generated).isNotNull();
        assertThat(Long.parseLong(generated)).isPositive();
    }

    @Test
    void uploadAvatar_userNotFound_throwsNotFound() {
        when(avatarHistoryMapper.createAvatarHistory(
                any(), anyLong(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new NotFoundException("user not found by uid: 404"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.uploadAvatar(UploadAvatarReq.newBuilder()
                        .setUid(404L).setAvatarUrl("https://cdn.example.com/x.png").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(e.getStatus().getDescription()).isEqualTo("用户不存在");
    }
}
