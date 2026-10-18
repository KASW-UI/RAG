package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.CreateAdminReq;
import com.rag.users.admin.proto.CreateAdminResp;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 CreateAdmin 关键语义（4 步事务 + 错误码映射）：
 *
 *   触发场景                                                  → gRPC code
 *   ───────────────────────────────────────────────────────────────────────────
 *   inviteCode 为空                                           → PERMISSION_DENIED "管理员邀请码错误"
 *   inviteCode trim 后空                                       → PERMISSION_DENIED "管理员邀请码错误"
 *   DataModel.InviteUnavailableException (找不到 / 已用 / 过期) → PERMISSION_DENIED "管理员邀请码错误"
 *   DataModel.UsernameTakenException (事务内 username 已被占)  → ALREADY_EXISTS "用户名已存在"
 *   insert race condition (SQLState 23505)                    → ALREADY_EXISTS "用户名已存在"
 *   其他 DB 异常                                              → INTERNAL "DB添加失败"
 *
 *   全部走完 → resp{Uid=uid}
 */
@ExtendWith(MockitoExtension.class)
class CreateAdminLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    CreateAdminLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new CreateAdminLogic(svcCtx);
    }

    /** 命中：4 步事务成功，bcrypt 后的 hash 传入，uid 是 snowflake 生成 */
    @Test
    void success_callsCreateAdminAtomicWithBcryptHashAndUid() {
        // DataModel.createAdminAtomic 默认不抛 → 4 步成功
        CreateAdminResp resp = logic.createAdmin(CreateAdminReq.newBuilder()
                .setUsername("alice")
                .setPassword("plain_password")
                .setEmail("alice@example.com")
                .setInviteCode("ADM-ABCDEF1234567890")
                .build());

        // resp.Uid 是 snowflake 生成的正整数（不为 0）
        assertTrue(resp.getUid() > 0L, "Snowflake uid 必须 > 0");

        // DataModel.createAdminAtomic 被调一次，参数校验
        ArgumentCaptor<String> usernameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> inviteCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> uidCap = ArgumentCaptor.forClass(Long.class);
        verify(dataModel).createAdminAtomic(
                usernameCap.capture(),
                inviteCap.capture(),
                        hashCap.capture(),
                        emailCap.capture(),
                        any(),
                        uidCap.capture());

        assertEquals("alice", usernameCap.getValue(), "username 应该 trim 后传入");
        assertEquals("ADM-ABCDEF1234567890", inviteCap.getValue(), "inviteCode 应该 trim 后传入");
        // bcrypt 哈希：前缀 $2a$/$2b$，且能 match 明文
        String hash = hashCap.getValue();
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"), "bcrypt 哈希前缀应为 $2a$/$2b$");
        assertTrue(PasswordUtil.check("plain_password", hash), "hash 应能 match 明文 plain_password");
        assertEquals("alice@example.com", emailCap.getValue(), "email 透传（trim 不动）");
        assertEquals(resp.getUid(), uidCap.getValue(), "uid 与 resp.Uid 一致");
    }

    /** inviteCode 为空 → PERMISSION_DENIED，事务不进 */
    @Test
    void emptyInviteCode_throwsPermissionDenied() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createAdmin(CreateAdminReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("pw")
                        .setInviteCode("")
                        .build()));

        assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
        verify(dataModel, never()).createAdminAtomic(anyString(), anyString(), anyString(), any(), any(), anyLong());
    }

    /** inviteCode trim 后为空(只有空格)→ PERMISSION_DENIED */
    @Test
    void whitespaceInviteCode_trimsToEmptyAndThrowsPermissionDenied() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createAdmin(CreateAdminReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("pw")
                        .setInviteCode("   ")
                        .build()));

        assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
        verify(dataModel, never()).createAdminAtomic(anyString(), anyString(), anyString(), any(), any(), anyLong());
    }

    /** invite 不存在（DataModel 抛 InviteUnavailableException）→ PERMISSION_DENIED */
    @Test
    void inviteNotFound_throwsPermissionDenied() {
        doThrow(new DataModel.InviteUnavailableException("invite not found"))
                .when(dataModel).createAdminAtomic(anyString(), anyString(), anyString(), any(), any(), anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createAdmin(CreateAdminReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("pw")
                        .setInviteCode("ADM-INVALID")
                        .build()));

        assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
    }

    /** invite 已用 / 过期（DataModel 抛 InviteUnavailableException）→ PERMISSION_DENIED */
    @Test
    void inviteExpiredOrUsed_throwsPermissionDenied() {
        doThrow(new DataModel.InviteUnavailableException("invite expired or used"))
                .when(dataModel).createAdminAtomic(anyString(), anyString(), anyString(), any(), any(), anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createAdmin(CreateAdminReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("pw")
                        .setInviteCode("ADM-EXPIRED")
                        .build()));

        assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
    }

    /** username 已被占用（DataModel 抛 UsernameTakenException）→ ALREADY_EXISTS */
    @Test
    void usernameTaken_throwsAlreadyExists() {
        doThrow(new DataModel.UsernameTakenException("username already taken"))
                .when(dataModel).createAdminAtomic(anyString(), anyString(), anyString(), any(), any(), anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createAdmin(CreateAdminReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("pw")
                        .setInviteCode("ADM-OK")
                        .build()));

        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
    }

    /** insert admin 时撞 unique（race condition）→ ALREADY_EXISTS */
    @Test
    void insertRaceConditionUniqueViolation_throwsAlreadyExists() {
        doThrow(uniqueViolation())
                .when(dataModel).createAdminAtomic(anyString(), anyString(), anyString(), any(), any(), anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createAdmin(CreateAdminReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("pw")
                        .setInviteCode("ADM-OK")
                        .build()));

        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
    }

    /** 其他 DB 异常 → INTERNAL */
    @Test
    void dbError_throwsInternal() {
        doThrow(new RuntimeException("connection refused"))
                .when(dataModel).createAdminAtomic(anyString(), anyString(), anyString(), any(), any(), anyLong());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createAdmin(CreateAdminReq.newBuilder()
                        .setUsername("alice")
                        .setPassword("pw")
                        .setInviteCode("ADM-OK")
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    /** 构造 SQLState 23505 的 DataIntegrityViolationException，模拟 Postgres unique violation */
    private static DataIntegrityViolationException uniqueViolation() {
        SQLException sqlEx = new SQLException("duplicate key value violates unique constraint", "23505");
        return new DataIntegrityViolationException("insert admin failed (race condition)", sqlEx);
    }
}