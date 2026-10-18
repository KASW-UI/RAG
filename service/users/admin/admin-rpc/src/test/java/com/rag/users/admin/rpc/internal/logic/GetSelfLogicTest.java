package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetSelfResp;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 验证 GetSelf 关键语义：
 *   - 命中 → GetSelfResp{Admin: AdminInfo{uid, username, email, extra_info}}
 *   - email 用 AdminHelpers.stringValue 转 nullable null → ""（与 Go 端 *string → string 语义对齐）
 *   - admin 不存在 → StatusRuntimeException(NOT_FOUND)
 *   - DB 异常 → StatusRuntimeException(INTERNAL)
 */
@ExtendWith(MockitoExtension.class)
class GetSelfLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    GetSelfLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new GetSelfLogic(svcCtx);
    }

    /** 命中：admin 有 email → AdminInfo.email 有值 */
    @Test
    void successWithEmail_returnsAdminInfo() {
        Admin a = adminWith(12345L, "admin", "admin@example.com");
        when(dataModel.findOneAdminByUid(12345L)).thenReturn(a);

        GetSelfResp resp = logic.getSelf(GetSelfReq.newBuilder().setUid(12345L).build());

        AdminInfo info = resp.getAdmin();
        assertEquals(12345L, info.getUid());
        assertEquals("admin", info.getUsername());
        assertEquals("admin@example.com", info.getEmail());
    }

    /** email 为 null（Go *string 语义）：Go stringValue() → ""，1:1 跟随 */
    @Test
    void nullEmail_returnsEmptyString() {
        Admin a = adminWithNullEmail(12345L, "admin_no_email");
        when(dataModel.findOneAdminByUid(12345L)).thenReturn(a);

        GetSelfResp resp = logic.getSelf(GetSelfReq.newBuilder().setUid(12345L).build());

        // Go: stringValue(nil) == "" ; Java: AdminHelpers.stringValue(null) == ""
        assertEquals("", resp.getAdmin().getEmail());
    }

    /** admin 不存在 → StatusRuntimeException(NOT_FOUND) */
    @Test
    void adminNotFound_throwsNotFound() {
        when(dataModel.findOneAdminByUid(999L))
                .thenThrow(new NotFoundException("admin not found by uid: 999"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.getSelf(GetSelfReq.newBuilder().setUid(999L).build()));

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    /** DB 异常 → StatusRuntimeException(INTERNAL) */
    @Test
    void dbError_throwsInternal() {
        when(dataModel.findOneAdminByUid(12345L))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.getSelf(GetSelfReq.newBuilder().setUid(12345L).build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    private static Admin adminWith(long uid, String username, String email) {
        Admin a = new Admin();
        a.setUid(uid);
        a.setUsername(username);
        a.setEmail(email);
        Map<String, String> extra = new HashMap<>();
        extra.put("k", "v");
        a.setExtraInfo(extra);
        return a;
    }

    private static Admin adminWithNullEmail(long uid, String username) {
        Admin a = new Admin();
        a.setUid(uid);
        a.setUsername(username);
        a.setEmail(null); // *string nil 场景，对应 Go *string nil
        Map<String, String> extra = new HashMap<>();
        extra.put("k", "v");
        a.setExtraInfo(extra);
        return a;
    }
}