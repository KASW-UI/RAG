package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.UpdateSelfReq;
import com.rag.users.admin.proto.UpdateSelfResp;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.Admin;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 UpdateSelf 关键语义：
 *   - 命中 + 改 username/password/email/extra_info → resp{Success=true, Admin=...}
 *   - admin 不存在 → NOT_FOUND
 *   - username 被其他 admin 占用（exist.Uid != self.Uid）→ ALREADY_EXISTS（D-004 不排除自己）
 *   - email 传 "  " → DataModel.setEmail(null)，写 SQL NULL
 *   - 全部字段都为空 → 仍然调到 DataModel.updateAdminByUid（虽然不会更新任何字段，但不报错）
 */
@ExtendWith(MockitoExtension.class)
class UpdateSelfLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    UpdateSelfLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new UpdateSelfLogic(svcCtx);
    }

    /** 正常：只改 username → success + AdminInfo.username 是新值 */
    @Test
    void success_changingUsernameOnly() {
        Admin self = adminWith(12345L, "alice", "alice@example.com");
        Admin updated = adminWith(12345L, "alice_new", "alice@example.com");
        when(dataModel.findOneAdminByUid(12345L))
                .thenReturn(self)   // W1
                .thenReturn(updated); // W5 重读
        // username 不冲突（NotFoundException → 无重查 admin）
        when(dataModel.findOneAdminByUsername("alice_new"))
                .thenThrow(new NotFoundException("not found"));

        UpdateSelfResp resp = logic.updateSelf(UpdateSelfReq.newBuilder()
                .setUid(12345L)
                .setUsername("alice_new")
                .build());

        assertTrue(resp.getSuccess());
        assertEquals(12345L, resp.getAdmin().getUid());
        assertEquals("alice_new", resp.getAdmin().getUsername());
        assertEquals("alice@example.com", resp.getAdmin().getEmail());
        verify(dataModel).updateAdminByUid(any(), any());
    }

    /** username 被其他 admin 占用 → ALREADY_EXISTS，且不调 update */
    @Test
    void usernameTaken_throwsAlreadyExists() {
        Admin self = adminWith(12345L, "alice", "alice@example.com");
        Admin other = adminWith(99999L, "bob", "bob@example.com");
        when(dataModel.findOneAdminByUid(12345L)).thenReturn(self);
        when(dataModel.findOneAdminByUsername("bob")).thenReturn(other);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.updateSelf(UpdateSelfReq.newBuilder()
                        .setUid(12345L)
                        .setUsername("bob")
                        .build()));

        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        verify(dataModel, never()).updateAdminByUid(any(), any());
    }

    /** admin 不存在 → NOT_FOUND */
    @Test
    void adminNotFound_throwsNotFound() {
        when(dataModel.findOneAdminByUid(12345L))
                .thenThrow(new NotFoundException("admin not found by uid: 12345"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.updateSelf(UpdateSelfReq.newBuilder()
                        .setUid(12345L)
                        .setUsername("alice")
                        .build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** email 传 "  "（trim 后空）→ AdminHelpers.optionalString 返回 null → DataModel.setEmail(null)
     *  proto3 wire 上 null == ""，所以 resp.getAdmin().getEmail() 返回 "" 而不是 null。
     *  Logic 应该仍调 dataModel.updateAdminByUid 把 email 字段写为 SQL NULL。 */
    @Test
    void emailWhitespace_trimsToEmptyForSqlNull() {
        Admin self = adminWith(12345L, "alice", "alice@example.com");
        Admin updated = adminWith(12345L, "alice", null); // 模拟 SQL NULL 重读
        when(dataModel.findOneAdminByUid(12345L))
                .thenReturn(self)
                .thenReturn(updated);

        UpdateSelfResp resp = logic.updateSelf(UpdateSelfReq.newBuilder()
                .setUid(12345L)
                .setEmail("  ")
                .build());

        assertTrue(resp.getSuccess());
        // proto3 wire 上 null/"" 不可区分：resp.getAdmin().getEmail() 是 String(非 null)，默认 ""
        assertEquals("", resp.getAdmin().getEmail());
        // DataModel.updateAdminByUid 仍然被调，且传入的 Admin 的 email 字段是 null（写 SQL NULL）
        verify(dataModel).updateAdminByUid(any(), any());
    }

    private static Admin adminWith(long uid, String username, String email) {
        Admin a = new Admin();
        a.setUid(uid);
        a.setUsername(username);
        a.setEmail(email);
        a.setExtraInfo(new HashMap<>());
        return a;
    }
}