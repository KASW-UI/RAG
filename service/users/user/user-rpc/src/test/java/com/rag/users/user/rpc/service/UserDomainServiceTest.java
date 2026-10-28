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
import com.rag.users.user.proto.UserInfo;
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
import static org.mockito.Mockito.same;
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
        history.setCreateTime(java.time.OffsetDateTime.now());
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
        h1.setCreateTime(java.time.OffsetDateTime.now());
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

    /**
     * 🔴 DRIFT-USERRPC-V-012（2026-10-27 · G1 lane · 域侧自修）：<b>原始异常必须进日志</b>。
     *
     * <p><b>修前</b>：{@code logout} 的 Redis 写路径 {@code catch (Exception e)} 里只有
     * {@code throw Status.INTERNAL...asRuntimeException()} ⇒ 造出的 {@code StatusRuntimeException}
     * <b>cause=null</b> ⇒ 实测日志原文 {@code Status{code=INTERNAL, description=注销失败,Redis写入异常, cause=null}}
     * ⇒ 底层 Lettuce 异常（超时 / 连接失败）<b>完全看不见</b>
     * ⇒ 排障只看到「Redis 写入异常」，看不到「其实是超时」⇒ 被指向错误方向。
     *
     * <p><b>修法口径</b>：<b>只加日志，不改 status / description</b>
     * （改了反而与 Go 不一致 ⇒ wire 面零影响）。
     *
     * <p><b>Go / admin 侧依据</b>：{@code service/user/user/rpc/internal/logic/logoutlogic.go}
     * 的错误出口把原始 {@code err} 交给上层；
     * admin 域 {@code AdminDomainService.logout} 已有
     * {@code log.error("admin logout Redis setex failed", e)} ⇒ 原始异常进日志。
     *
     * <p><b>变异验证</b>：删掉 {@code UserDomainService.logout} 里那行
     * {@code logger.logBusinessErr(...)} ⇒ 本条红。
     */
    @Test
    void logout_redisError_logsOriginalCause_forAttribution() {
        RuntimeException redisEx = new RuntimeException("redis command timed out");
        doThrow(redisEx).when(valueOps)
                .set(anyString(), anyString(), any(java.time.Duration.class));

        assertThrows(StatusRuntimeException.class, () ->
                service.logout(LogoutReq.newBuilder().setToken("any.jwt.token").build()));

        // 原始异常必须**原样**进日志（不是它的 message 文本、不是一个新的异常）
        verify(logger, times(1)).logBusinessErr(
                any(), eq(ErrorCode.ERROR_DB_SELECT.getCode()), same(redisEx));
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

    // ==================================================================
    // 2026-10-21 Go → Spring Boot 语义一致性审计新增的回归用例
    // ==================================================================

    /**
     * 🔴 DRIFT-USERRPC-V-012 回归：{@code getUser} <b>不</b>再对 NULL status 抛 INTERNAL ——
     * 那道守卫的依据（"GORM 把 NULL 扫进 int64 会直接报错"）<b>已被证伪</b>。
     *
     * <p><b>Go 的真实语义</b>：{@code status IS NULL} 被<b>读成 0</b>、<b>不报错</b>。
     * 消费点三段（缺一段就会得出相反结论）：
     * <ol>
     *   <li>{@code gorm@v1.31.1/schema/field.go:616-622} 给 int64 装的 setter 是
     *       {@code case **int64: if data != nil && *data != nil { …SetInt(**data) }}
     *       ⇒ {@code *data == nil} 时<b>不动作</b>，字段留零值 0；</li>
     *   <li>{@code gorm@v1.31.1/scan.go:19} 与 {@code schema/field.go:1002} 传给
     *       {@code rows.Scan} 的 dest 是 <b>{@code **int64}</b>；</li>
     *   <li>{@code database/sql/convert.go:431-435}
     *       {@code case reflect.Pointer: if src == nil { dv.SetZero(); return nil }}
     *       ⇒ <b>不报错</b>（{@code convert.go:438} 那条 "unsupported" 因 kind 不符<b>永远走不到</b>）。</li>
     * </ol>
     * 双侧活体对拍（2026-10-23 21:41）：status IS NULL 打 GetUser，
     * Go 返回 200 且 {@code user} 里 <b>status 字段整个不出现</b>（JSON 的 omitempty，读到的值就是 0）。
     *
     * <p><b>为什么本用例改的是 mock 而不是真库</b>：本类是 Mockito 单测，
     * 构造不出 MyBatis 跳过 setter 的那条路径 ⇒ 真库版本见
     * {@code UserRpcLoginNullStatusReadsAsZeroTest#mapperMapsNullStatusToZeroLikeGo}。
     * 本用例负责另一件事：<b>锁住 {@code = 0L} 初始化值这个等价性载体本身</b>。
     *
     * <p>⚠️ 上一版本用例用 {@code user.setStatus(null)} 人为造出一个
     * <b>MyBatis 根本产生不了的状态</b>（跳过 setter ⇒ 字段留初始化值），
     * 再断言它抛 INTERNAL —— 那是在给一个不可达状态写期望值，
     * 并且把守卫的<b>假依据</b>固化成了回归网。已一并撤销。
     */
    @Test
    void getUser_nullStatusColumn_readsAsZeroLikeGo() {
        UserEntity user = new UserEntity();
        user.setUid(456L);
        user.setUsername("bob");
        // 不调 setStatus：DB 里 status 列为 NULL 时，MyBatis 的 callSettersOnNulls=false
        // 会**跳过 setter**，字段保留 UserEntity 的 `= 0L` 初始化值 —— 与 Go 同形。
        assertThat(user.getStatus())
                .as("UserEntity 的 `= 0L` 初始化值是 status-NULL 语义的载体，删掉它本用例立刻红")
                .isEqualTo(0L);
        when(userMapper.findOneByUid(456L)).thenReturn(user);

        UserInfo got = service.getUser(GetUserReq.newBuilder().setUid(456L).build()).getUser();

        assertThat(got.getStatus())
                .as("status IS NULL 必须像 Go 一样读成 0，而不是抛 INTERNAL「DB查询失败」")
                .isEqualTo(0L);
        assertThat(got.getUid()).isEqualTo(456L);
    }

    /**
     * 🔴 对照组：{@code status} 为 <b>非</b> NULL 时，值必须<b>原样</b>透传
     * （证明上一条不是把「恒返回 0」写成了断言）。
     */
    @Test
    void getUser_nonNullStatus_passesThroughUnchanged() {
        UserEntity user = new UserEntity();
        user.setUid(457L);
        user.setStatus(1L);
        when(userMapper.findOneByUid(457L)).thenReturn(user);

        assertThat(service.getUser(GetUserReq.newBuilder().setUid(457L).build()).getUser().getStatus())
                .as("status=1 必须原样返回，不能被 NULL→0 的语义吞掉")
                .isEqualTo(1L);
    }

    /**
     * DRIFT-009 回归：register 查库失败必须打 {@code db_error} 指标。
     *
     * <p>Go 出处 registerlogic.go:50-54：「非 ErrorNotFound」单独成一支，
     * {@code UserRegisterCount.WithLabelValues("db_error").Inc()} +
     * {@code LogBusinessErr(errmsg.ErrorDbSelect)} + {@code codes.Internal}。
     *
     * <p>修复前该分支缺失 ⇒ DB 抖动在本进程既无指标也无业务错误日志，完全静默。
     */
    @Test
    void register_dbError_incrementsRegisterDbErrorCounter() {
        when(userMapper.findOneByUserName("alice"))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.register(CreateUserReq.newBuilder().setUsername("alice").setPassword("p").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(e.getStatus().getDescription()).isEqualTo("DB查询失败");

        assertThat(metrics.getRegisterDbError().count())
                .as("register 查库失败必须计入 user_rpc_register_total{result=db_error}")
                .isEqualTo(1.0);
        assertThat(metrics.getRegisterFailExists().count())
                .as("查库失败不是重名，不得计入 fail_exists").isEqualTo(0.0);
        verify(logger).logBusinessErr(any(), eq(com.rag.users.common.errmsg.ErrorCode.ERROR_DB_SELECT.getCode()), any());
    }

    /** DRIFT-009 回归：updateUser 重名检查阶段的 DB 故障必须与 Go 同码 INTERNAL + ErrorDbSelect。 */
    @Test
    void updateUser_dbErrorOnUsernameCheck_throwsInternal() {
        when(userMapper.findOneByUserName("taken"))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                service.updateUser(UpdateUserReq.newBuilder().setUid(1L).setUsername("taken").build()));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(e.getStatus().getDescription()).isEqualTo("DB查询失败");
        verify(logger).logBusinessErr(any(), eq(com.rag.users.common.errmsg.ErrorCode.ERROR_DB_SELECT.getCode()), any());
    }

    /** DRIFT-009 回归：deleteUser W2 的 DB 故障必须打 ErrorDbSelect（Go 侧 deleteuserlogic.go:48-49）。 */
    @Test
    void deleteUser_dbError_logsErrorDbSelect() {
        when(userMapper.findOneByUid(321L)).thenThrow(new RuntimeException("connection refused"));

        assertThrows(StatusRuntimeException.class, () ->
                service.deleteUser(DeleteUserReq.newBuilder().setUid(321L).build()));

        verify(logger).logBusinessErr(any(), eq(com.rag.users.common.errmsg.ErrorCode.ERROR_DB_SELECT.getCode()), any());
    }

    /**
     * DRIFT-006 回归：头像历史的 {@code create_time} 不得带小数秒。
     *
     * <p>Go 出处 rpc/internal/logic/avatarhelpers.go:18
     * {@code item.CreateTime.Format(time.RFC3339)}。{@code time.RFC3339} 的布局是
     * {@code "2006-01-02T15:04:05Z07:00"}，<b>不含 {@code .999} 段</b>，
     * 而 {@code Time.Format} 只输出布局指定的字段 ⇒ <b>纳秒被整段丢弃</b>。
     *
     * <p>原 RAG 实现用 {@code Instant.toString()}，它在纳秒非零时<b>必带</b>小数部分；
     * Postgres {@code TIMESTAMP} 是微秒精度、{@code CURRENT_TIMESTAMP} 几乎总是非零
     * ⇒ 同一份数据两边渲染出的字符串必然不同。该字符串一路透传到 HTTP body。
     */
    @Test
    void avatarHistory_createTime_hasNoFractionalSeconds() {
        // 机器无关：强制进程时区，而不是依赖跑测机器的 TZ。
        // （渲染落在**进程本地时区** —— Go 的 time.Local 语义；实测：Go 进程 TZ=UTC → '…Z'）
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            UserAvatarHistoryEntity h = new UserAvatarHistoryEntity();
            h.setHistoryId("H-1");
            h.setAvatarUrl("https://cdn/x.png");
            // 2026-09-23T10:15:30.123456+08:00 —— 正是 timestamptz 在 PG 里的典型形态
            // （Go 侧 GORM autoCreateTime 用应用侧时钟写入，pgx 二进制编码到 UTC 微秒）
            h.setCreateTime(java.time.OffsetDateTime.of(
                    2026, 9, 23, 10, 15, 30, 123456000, java.time.ZoneOffset.ofHours(8)));

            String createTime = avatarDomainService.fromModel(h).getCreateTime();

            // 🔴 2026-10-23（DRIFT-USERRPC-V-004 可观察半边）：期望值已从 "…Z" 改为 "…+08:00"。
            //
            // ⚠️ **旧断言是错的**（它把"渲染恒为 Z"当成规格）：
            //   Go 出处 service/user/user/rpc/internal/logic/avatarhelpers.go:16-19
            //       `item.CreateTime.Format(time.RFC3339)` —— Go 的 time.Time **自带 location**，
            //       Format 打印的是该 location 的偏移量，非 UTC 时就是 "+08:00"。
            //   双侧活体对拍实测（2026-10-23 21:01，真进程 + 真 PG）：
            //       Go  : "2026-10-23T21:01:48+08:00"
            //       RAG : "2026-10-23T21:01:48Z"      ← 修复前
            //   ⇒ 旧实现 `ct.atOffset(ZoneOffset.UTC)` 把读到的时刻强行标成 UTC，
            //     在 JVM 默认时区非 UTC 时渲染出的是**另一个时刻的墙钟**（实测差 8 小时）。
            assertThat(createTime)
                    .as("必须与 Go 的 time.RFC3339 逐字符一致：秒精度 + **进程本地时区的偏移量**")
                    .isEqualTo("2026-09-23T10:15:30+08:00");
            assertThat(createTime).doesNotContain(".");
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    /**
     * 对照组 1：偏移量为 0 时输出 "Z"（Go 的 "Z07:00" 布局在零偏移时就是 Z）。
     * <p>证明上一条不是靠"总是带 +08:00"蒙对的。
     */
    @Test
    void avatarHistory_createTime_secondPrecision_whenNoNanos() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            UserAvatarHistoryEntity h = new UserAvatarHistoryEntity();
            h.setHistoryId("H-2");
            h.setCreateTime(java.time.OffsetDateTime.of(
                    2026, 9, 23, 10, 15, 30, 0, java.time.ZoneOffset.UTC));

            assertThat(avatarDomainService.fromModel(h).getCreateTime())
                    .isEqualTo("2026-09-23T10:15:30Z");
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    /**
     * 对照组 3：实体的偏移量是「非整小时」时，渲染必须落在**进程本地时区**、且保持同一绝对时刻。
     * <p>渲染的落点由进程时区决定（Go 的 time.Local），所以实体的偏移量<b>不参与</b>输出标签 ——
     * 这正是 Go 的行为：{@code time.Time} 被 pgx 解到 {@code time.Local} 后才 Format。
     */
    @Test
    void avatarHistory_createTime_keepsTheSameInstantInProcessZone() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            UserAvatarHistoryEntity h = new UserAvatarHistoryEntity();
            h.setHistoryId("H-2b");
            h.setCreateTime(java.time.OffsetDateTime.of(
                    2026, 9, 23, 10, 15, 30, 0, java.time.ZoneOffset.ofHoursMinutes(8, 30)));

            // 10:15:30+08:30 == 01:45:30Z == 09:45:30+08:00（本地时区）
            assertThat(avatarDomainService.fromModel(h).getCreateTime())
                    .isEqualTo("2026-09-23T09:45:30+08:00");
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    /** Go 出处 avatarhelpers.go:16-18：CreateTime 为零值时不设置该字段 ⇒ wire 上是空串。 */
    @Test
    void avatarHistory_createTime_emptyWhenNull() {
        UserAvatarHistoryEntity h = new UserAvatarHistoryEntity();
        h.setHistoryId("H-3");
        h.setCreateTime(null);

        assertThat(avatarDomainService.fromModel(h).getCreateTime()).isEmpty();
    }
}
