package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.GetUserListReq;
import com.rag.users.admin.proto.GetUserListResp;
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.model.DataModel.UserPage;
import com.rag.users.admin.rpc.internal.model.User;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 验证 GetUserList 关键语义：
 *   - 命中 → GetUserListResp{List, Total}
 *   - DB 异常 → StatusRuntimeException(INTERNAL)
 *   - 空列表也正常返回（Go 端 make([]*pb.UserInfo, 0)）
 */
@ExtendWith(MockitoExtension.class)
class GetUserListLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    GetUserListLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new GetUserListLogic(svcCtx);
    }

    /** 正常分页：2 行 + total=2 */
    @Test
    void success_returnsListAndTotal() {
        User u1 = userWith(100L, "alice", "a@example.com", 0L);
        User u2 = userWith(101L, "bob", "b@example.com", 1L);
        when(dataModel.findUserListByKeyword(1, 20, ""))
                .thenReturn(new UserPage(List.of(u1, u2), 2L));

        GetUserListResp resp = logic.getUserList(GetUserListReq.newBuilder()
                .setPage(1L)
                .setPageSize(20L)
                .setKeyword("")
                .build());

        assertEquals(2, resp.getListCount());
        assertEquals(2L, resp.getTotal());

        UserInfo first = resp.getList(0);
        assertEquals(100L, first.getUid());
        assertEquals("alice", first.getUsername());
        assertEquals(0L, first.getStatus());

        UserInfo second = resp.getList(1);
        assertEquals(101L, second.getUid());
        assertEquals("bob", second.getUsername());
        assertEquals(1L, second.getStatus());
    }

    /** 空列表：Go 端 make([]*pb.UserInfo, 0) → 0 行 + total=0 */
    @Test
    void emptyResult_returnsEmptyListAndZeroTotal() {
        when(dataModel.findUserListByKeyword(1, 20, "no_match_keyword"))
                .thenReturn(new UserPage(List.of(), 0L));

        GetUserListResp resp = logic.getUserList(GetUserListReq.newBuilder()
                .setPage(1L)
                .setPageSize(20L)
                .setKeyword("no_match_keyword")
                .build());

        assertEquals(0, resp.getListCount());
        assertEquals(0L, resp.getTotal());
    }

    /** DB 异常 → StatusRuntimeException(INTERNAL) */
    @Test
    void dbError_throwsInternal() {
        when(dataModel.findUserListByKeyword(1, 20, ""))
                .thenThrow(new RuntimeException("connection refused"));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.getUserList(GetUserListReq.newBuilder()
                        .setPage(1L)
                        .setPageSize(20L)
                        .setKeyword("")
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }

    private static User userWith(long uid, String username, String email, long status) {
        User u = new User();
        u.setUid(uid);
        u.setUsername(username);
        u.setEmail(email);
        u.setStatus(status);
        Map<String, String> extra = new HashMap<>();
        extra.put("k", "v");
        u.setExtraInfo(extra);
        return u;
    }
}