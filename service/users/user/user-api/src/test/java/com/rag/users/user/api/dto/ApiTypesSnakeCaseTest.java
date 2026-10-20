package com.rag.users.user.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DTO 序列化 wire 契约测试 —— 验证 snake_case 字段名 + uid 序列化为字符串。
 *
 * <p>对应 Go 端 internal/types/types.go 的 json tag 1:1：
 * <pre>
 *   uid → "uid"（Long.toString）
 *   extra_info → "extra_info"
 *   avatar_url → "avatar_url"
 *   content_type → "content_type"
 *   size_bytes → "size_bytes"
 *   is_current → "is_current"
 *   create_time → "create_time"
 *   history_id → "history_id"
 * </pre>
 */
class ApiTypesSnakeCaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registerReq_containsSnakeCase() throws Exception {
        Map<String, String> extra = new HashMap<>();
        extra.put("k", "v");
        RegisterReq req = new RegisterReq();
        req.setUsername("alice");
        req.setPassword("pwd");
        req.setEmail("a@b.c");
        req.setExtraInfo(extra);

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"username\":\"alice\""), json);
        assertTrue(json.contains("\"password\":\"pwd\""), json);
        assertTrue(json.contains("\"email\":\"a@b.c\""), json);
        assertTrue(json.contains("\"extra_info\":{\"k\":\"v\"}"), json);
    }

    @Test
    void registerResp_uidIsString() throws Exception {
        RegisterResp resp = new RegisterResp();
        resp.setUid(123456789012345L);   // Snowflake UID（超过 2^53）

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"uid\":\"123456789012345\""), json);
    }

    @Test
    void userInfo_uidIsStringAndOmitsAbsentFields() throws Exception {
        UserInfo ui = new UserInfo();
        ui.setUid(42L);
        ui.setUsername("bob");
        // email / extra_info 不设 → 应不出现（Go 侧是 json:",omitempty"）

        String json = mapper.writeValueAsString(ui);
        assertTrue(json.contains("\"uid\":\"42\""), json);
        assertTrue(json.contains("\"username\":\"bob\""), json);
        assertFalse(json.contains("email"), json);
        assertFalse(json.contains("extra_info"), json);
    }

    /**
     * DRIFT-005 回归：Go {@code types.UserInfo.Score uint32 `json:"score"`} 无 omitempty，
     * 且 get/update 两个 Logic 都不给 Score 赋值 ⇒ Go 的响应恒含 {@code "score":0}。
     * 之前本字段带 {@code @JsonInclude(NON_NULL)} 且从不赋值 ⇒ key 整个消失。
     */
    @Test
    void userInfo_scoreIsAlwaysPresentLikeGo() throws Exception {
        UserInfo ui = new UserInfo();
        ui.setUid(42L);
        ui.setUsername("bob");
        // score 不赋值

        String json = mapper.writeValueAsString(ui);
        assertTrue(json.contains("\"score\":0"),
                "score 必须恒存在且为 0（对齐 Go 的无 omitempty），实际 = " + json);
    }

    @Test
    void userInfo_scoreRoundTripsWhenSet() throws Exception {
        UserInfo ui = new UserInfo();
        ui.setUid(42L);
        ui.setScore(88L);

        String json = mapper.writeValueAsString(ui);
        assertTrue(json.contains("\"score\":88"), json);
    }

    @Test
    void avatarHistoryItem_containsAllSnakeCaseFields() throws Exception {
        AvatarHistoryItem item = new AvatarHistoryItem();
        item.setId("h-1");
        item.setAvatarUrl("https://x/y.png");
        item.setContentType("image/png");
        item.setSizeBytes(1024L);
        item.setCurrent(true);
        item.setCreateTime("2026-09-24T00:00:00Z");

        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"id\":\"h-1\""), json);
        assertTrue(json.contains("\"avatar_url\":\"https://x/y.png\""), json);
        assertTrue(json.contains("\"content_type\":\"image/png\""), json);
        assertTrue(json.contains("\"size_bytes\":1024"), json);
        assertTrue(json.contains("\"is_current\":true"), json);
        assertTrue(json.contains("\"create_time\":\"2026-09-24T00:00:00Z\""), json);
    }

    @Test
    void updateUserReq_omitsUnsetOptionalFields() throws Exception {
        UpdateUserReq req = new UpdateUserReq();
        req.setUsername("alice");
        // password / email / extra_info 不设 → 应不出现

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"username\":\"alice\""), json);
        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("email"), json);
        assertFalse(json.contains("extra_info"), json);
    }

    @Test
    void selectAvatarReq_historyIdSnakeCase() throws Exception {
        SelectAvatarReq req = new SelectAvatarReq();
        req.setHistoryId("h-1");

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"history_id\":\"h-1\""), json);
    }

    @Test
    void getUserResp_omitsUserWhenNull() throws Exception {
        GetUserResp resp = new GetUserResp();
        resp.setFound(false);
        // user 未设 → 应不出现

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"found\":false"), json);
        assertFalse(json.contains("\"user\""), json);
    }

    @Test
    void getUserResp_includesUserAndExtraInfoSnakeCase() throws Exception {
        UserInfo ui = new UserInfo();
        ui.setUid(42L);
        ui.setUsername("bob");

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("k", "v");

        GetUserResp resp = new GetUserResp();
        resp.setUser(ui);
        resp.setFound(true);

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"found\":true"), json);
        assertTrue(json.contains("\"user\":{"), json);
        assertTrue(json.contains("\"uid\":\"42\""), json);
        assertTrue(json.contains("\"username\":\"bob\""), json);
    }
}
