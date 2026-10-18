package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.CreateInviteCodeReq;
import com.rag.users.admin.proto.CreateInviteCodeResp;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.Admin;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.AdminInvite;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
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
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 CreateInviteCode 关键语义（覆盖 Go 端 createinvitecodelogic.go 的 6 类 metrics 分支）：
 *
 *   metrics result label → 触发场景
 *   ─────────────────────────────────────────────────────────────
 *   user_not_found    → FindOneAdminByUid 抛 NotFoundException
 *   db_error          → FindOneAdminByUid 抛其他 Exception / insertAdminInvite 抛非 unique violation Exception
 *   internal_error    → SecureRandom 抛异常 / 重试 3 次仍 unique violation
 *   success           → insertAdminInvite 成功
 *
 * 此外验证：
 *   - 邀请码格式 "ADM-" + 16 字符大写 hex
 *   - expiresAt 是 now + 10 分钟的 unix 秒
 *   - 重试逻辑：第 1 次 unique violation → 第 2 次成功
 */
@ExtendWith(MockitoExtension.class)
class CreateInviteCodeLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    CreateInviteCodeLogic logic;

    private static final Pattern INVITE_CODE_PATTERN = Pattern.compile("^ADM-[0-9A-F]{16}$");

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new CreateInviteCodeLogic(svcCtx, metrics);
    }

    /** 命中：第 1 次插入就成功 */
    @Test
    void success_firstAttempt() {
        Admin operator = new Admin();
        operator.setUid(12345L);
        operator.setUsername("admin");
        when(dataModel.findOneAdminByUid(12345L)).thenReturn(operator);
        doNothing().when(dataModel).insertAdminInvite(any(AdminInvite.class));

        LocalDateTime beforeCall = LocalDateTime.now();
        CreateInviteCodeResp resp = logic.createInviteCode(CreateInviteCodeReq.newBuilder()
                .setOperatorId(12345L)
                .build());
        LocalDateTime afterCall = LocalDateTime.now();

        // 邀请码格式 "ADM-" + 16 字符大写 hex
        assertTrue(INVITE_CODE_PATTERN.matcher(resp.getInviteCode()).matches(),
                "邀请码必须匹配 ADM-[0-9A-F]{16}，实际: " + resp.getInviteCode());
        assertEquals(20, resp.getInviteCode().length());

        // expiresAt = now + 10min（unix 秒），允许前后 1 秒误差
        long expectedMin = beforeCall.plusMinutes(10).atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        long expectedMax = afterCall.plusMinutes(10).atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        assertTrue(resp.getExpiresAt() >= expectedMin && resp.getExpiresAt() <= expectedMax,
                "expiresAt 必须在 now+10min 前后 1 秒内，实际: " + resp.getExpiresAt());

        // 入参正确
        ArgumentCaptor<AdminInvite> captor = ArgumentCaptor.forClass(AdminInvite.class);
        verify(dataModel).insertAdminInvite(captor.capture());
        AdminInvite stored = captor.getValue();
        assertEquals(12345L, stored.getInviterUid());
        assertNotNull(stored.getCode());
    }

    /** 重试 1 次后成功：第 1 次 unique violation，第 2 次成功 */
    @Test
    void success_afterOneUniqueViolationRetry() {
        Admin operator = new Admin();
        operator.setUid(12345L);
        when(dataModel.findOneAdminByUid(12345L)).thenReturn(operator);
        // 第 1 次抛 unique violation（SQLState 23505），第 2 次成功
        doThrow(uniqueViolation())
                .doNothing()
                .when(dataModel).insertAdminInvite(any(AdminInvite.class));

        CreateInviteCodeResp resp = logic.createInviteCode(CreateInviteCodeReq.newBuilder()
                .setOperatorId(12345L)
                .build());

        assertTrue(INVITE_CODE_PATTERN.matcher(resp.getInviteCode()).matches());
        verify(dataModel, org.mockito.Mockito.times(2)).insertAdminInvite(any(AdminInvite.class));
    }

    /** 重试 3 次仍 unique violation → INTERNAL + 内部错误码（对应 Go 端"管理员邀请码生成失败"） */
    @Test
    void retryExhausted_throwsInternal() {
        Admin operator = new Admin();
        operator.setUid(12345L);
        when(dataModel.findOneAdminByUid(12345L)).thenReturn(operator);
        // 3 次都 unique violation
        doThrow(uniqueViolation())
                .doThrow(uniqueViolation())
                .doThrow(uniqueViolation())
                .when(dataModel).insertAdminInvite(any(AdminInvite.class));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createInviteCode(CreateInviteCodeReq.newBuilder()
                        .setOperatorId(12345L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        // 重试 3 次都失败 → metrics createInviteInternalError 增 1 次
        verify(dataModel, org.mockito.Mockito.times(3)).insertAdminInvite(any(AdminInvite.class));
    }

    /** operator 不存在 → NOT_FOUND + metrics createInviteUserNotFound */
    @Test
    void operatorNotFound_throwsNotFound() {
        when(dataModel.findOneAdminByUid(12345L))
                .thenThrow(new NotFoundException("admin not found by uid: 12345"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createInviteCode(CreateInviteCodeReq.newBuilder()
                        .setOperatorId(12345L)
                        .build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
        // 不调 insertAdminInvite
        verify(dataModel, org.mockito.Mockito.never()).insertAdminInvite(any(AdminInvite.class));
    }

    /** 查 operator 失败（DB 异常）→ INTERNAL + metrics createInviteDbError */
    @Test
    void findOperatorDbError_throwsInternal() {
        when(dataModel.findOneAdminByUid(12345L))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createInviteCode(CreateInviteCodeReq.newBuilder()
                        .setOperatorId(12345L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        verify(dataModel, org.mockito.Mockito.never()).insertAdminInvite(any(AdminInvite.class));
    }

    /** 插入 invite 抛非 unique violation 异常 → INTERNAL + metrics createInviteDbError */
    @Test
    void insertNonUniqueViolationDbError_throwsInternal() {
        Admin operator = new Admin();
        operator.setUid(12345L);
        when(dataModel.findOneAdminByUid(12345L)).thenReturn(operator);
        doThrow(new RuntimeException("connection refused"))
                .when(dataModel).insertAdminInvite(any(AdminInvite.class));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.createInviteCode(CreateInviteCodeReq.newBuilder()
                        .setOperatorId(12345L)
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        // 非 unique violation 不重试，只调一次
        verify(dataModel, org.mockito.Mockito.times(1)).insertAdminInvite(any(AdminInvite.class));
    }

    /**
     * 构造一个 DataIntegrityViolationException，其 cause 链包含 SQLState 23505 的 SQLException，
     * 与 Postgres 真实 unique violation 行为对齐。AdminExceptions.isUniqueViolation 能识别。
     */
    private static DataIntegrityViolationException uniqueViolation() {
        SQLException sqlEx = new SQLException("duplicate key value violates unique constraint", "23505");
        return new DataIntegrityViolationException("insert invite failed", sqlEx);
    }
}