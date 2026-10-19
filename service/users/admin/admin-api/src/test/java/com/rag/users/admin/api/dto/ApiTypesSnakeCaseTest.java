package com.rag.users.admin.api.dto;

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
 *   invite_code → "invite_code"
 *   extra_info → "extra_info"
 *   page_size → "page_size"
 *   expires_at → "expires_at"
 *   invite_code / page_size / extra_info / expires_at 等多词字段全 snake_case
 * </pre>
 */
class ApiTypesSnakeCaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loginReq_containsFields() throws Exception {
        LoginReq req = new LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"username\":\"alice\""), json);
        assertTrue(json.contains("\"password\":\"pwd\""), json);
    }

    @Test
    void createAdminReq_containsSnakeCase() throws Exception {
        Map<String, String> extra = new HashMap<>();
        extra.put("k", "v");

        CreateAdminReq req = new CreateAdminReq();
        req.setUsername("alice");
        req.setPassword("pwd");
        req.setEmail("a@b.c");
        req.setInviteCode("ADM-DEADBEEF");
        req.setExtraInfo(extra);

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"username\":\"alice\""), json);
        assertTrue(json.contains("\"password\":\"pwd\""), json);
        assertTrue(json.contains("\"email\":\"a@b.c\""), json);
        assertTrue(json.contains("\"invite_code\":\"ADM-DEADBEEF\""), json);
        assertTrue(json.contains("\"extra_info\":{\"k\":\"v\"}"), json);
    }

    @Test
    void createAdminResp_uidIsString() throws Exception {
        CreateAdminResp resp = new CreateAdminResp();
        resp.setUid(123456789012345L);   // Snowflake UID

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"uid\":\"123456789012345\""), json);
    }

    @Test
    void createInviteCodeResp_containsSnakeCase() throws Exception {
        CreateInviteCodeResp resp = new CreateInviteCodeResp();
        resp.setInviteCode("ADM-ABCDEF0123456789");
        resp.setExpiresAt(1893456000L);

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"invite_code\":\"ADM-ABCDEF0123456789\""), json);
        assertTrue(json.contains("\"expires_at\":1893456000"), json);
    }

    @Test
    void adminInfo_uidIsStringAndOmitsAbsentFields() throws Exception {
        AdminInfo ai = new AdminInfo();
        ai.setUid(42L);
        ai.setUsername("bob");
        // email / extra_info 不设 → 应不出现

        String json = mapper.writeValueAsString(ai);
        assertTrue(json.contains("\"uid\":\"42\""), json);
        assertTrue(json.contains("\"username\":\"bob\""), json);
        assertFalse(json.contains("email"), json);
        assertFalse(json.contains("extra_info"), json);
    }

    @Test
    void adminInfo_extraInfoSnakeCase() throws Exception {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("k", "v");

        AdminInfo ai = new AdminInfo();
        ai.setUid(42L);
        ai.setExtraInfo(extra);

        String json = mapper.writeValueAsString(ai);
        assertTrue(json.contains("\"extra_info\":{\"k\":\"v\"}"), json);
    }

    @Test
    void userInfo_uidIsStringAndIncludesStatus() throws Exception {
        UserInfo ui = new UserInfo();
        ui.setUid(42L);
        ui.setStatus(0L);
        ui.setUsername("bob");
        // email / extra_info 不设 → 应不出现

        String json = mapper.writeValueAsString(ui);
        assertTrue(json.contains("\"uid\":\"42\""), json);
        assertTrue(json.contains("\"status\":0"), json);
        assertTrue(json.contains("\"username\":\"bob\""), json);
        assertFalse(json.contains("email"), json);
        assertFalse(json.contains("extra_info"), json);
    }

    @Test
    void updateUserReq_omitsUnsetOptionalFields() throws Exception {
        UpdateUserReq req = new UpdateUserReq();
        req.setUid(100L);
        req.setUsername("alice");
        // password / email / extra_info 不设 → 应不出现

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"uid\":\"100\""), json);
        assertTrue(json.contains("\"username\":\"alice\""), json);
        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("email"), json);
        assertFalse(json.contains("extra_info"), json);
    }

    @Test
    void updateSelfReq_omitsUnsetOptionalFields() throws Exception {
        UpdateSelfReq req = new UpdateSelfReq();
        req.setUsername("alice");

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"username\":\"alice\""), json);
        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("email"), json);
        assertFalse(json.contains("extra_info"), json);
    }

    @Test
    void getUserReq_uidSnakeCase() throws Exception {
        GetUserReq req = new GetUserReq();
        req.setUid(12345L);

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"uid\":\"12345\""), json);
    }

    @Test
    void resetUserPasswordReq_uidSnakeCase() throws Exception {
        ResetUserPasswordReq req = new ResetUserPasswordReq();
        req.setUid(67890L);

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"uid\":\"67890\""), json);
    }

    @Test
    void getUserListReq_pageSizeSnakeCase() throws Exception {
        GetUserListReq req = new GetUserListReq();
        req.setPage(2L);
        req.setPageSize(50L);
        req.setKeyword("alice");

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"page\":2"), json);
        assertTrue(json.contains("\"page_size\":50"), json);
        assertTrue(json.contains("\"keyword\":\"alice\""), json);
    }

    @Test
    void getUserListResp_omitsListWhenNull() throws Exception {
        GetUserListResp resp = new GetUserListResp();
        resp.setTotal(0L);
        // list 未设 → 应不出现

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"total\":0"), json);
        assertFalse(json.contains("\"list\""), json);
    }

    @Test
    void getUserResp_includesUserWithStatusAndExtraInfo() throws Exception {
        UserInfo ui = new UserInfo();
        ui.setUid(42L);
        ui.setStatus(0L);
        ui.setUsername("bob");

        GetUserResp resp = new GetUserResp();
        resp.setUser(ui);

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"user\":{"), json);
        assertTrue(json.contains("\"uid\":\"42\""), json);
        assertTrue(json.contains("\"status\":0"), json);
        assertTrue(json.contains("\"username\":\"bob\""), json);
    }

    @Test
    void updateUserResp_includesSuccessAndUser() throws Exception {
        UserInfo ui = new UserInfo();
        ui.setUid(42L);

        UpdateUserResp resp = new UpdateUserResp();
        resp.setSuccess(true);
        resp.setUser(ui);

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"success\":true"), json);
        assertTrue(json.contains("\"user\":{"), json);
        assertTrue(json.contains("\"uid\":\"42\""), json);
    }

    @Test
    void banUserResp_containsSuccess() throws Exception {
        BanUserResp resp = new BanUserResp();
        resp.setSuccess(true);

        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"success\":true"), json);
    }
}