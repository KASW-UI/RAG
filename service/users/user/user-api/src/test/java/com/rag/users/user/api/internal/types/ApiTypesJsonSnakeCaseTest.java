package com.rag.users.user.api.internal.types;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 锁定 L1 wire JSON 名（snake_case）对齐 Go 端。
 *
 * <p>Go {@code types.go} 给所有多词字段写的 json tag 是 snake_case：
 * <pre>
 *   json:"extra_info" / json:"avatar_url" / json:"content_type"
 *   json:"size_bytes" / json:"is_current" / json:"create_time"
 *   json:"history_id" / json:"uid,string"
 * </pre>
 *
 * <p>Jackson 默认按 bean getter 推断属性名（{@code isCurrent()} → {@code current}，
 * {@code sizeBytes()} → {@code sizeBytes}），必须显式 {@code @JsonProperty}
 * 才能输出 snake_case。本测试用例确保修复后实际 wire JSON 与 Go 一致。
 */
class ApiTypesJsonSnakeCaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void avatarHistoryItem_wireKeysAreSnakeCase() throws Exception {
        ApiTypes.AvatarHistoryItem item = new ApiTypes.AvatarHistoryItem();
        item.setId("h-001");
        item.setAvatarUrl("https://example.com/a.png");
        item.setContentType("image/png");
        item.setSizeBytes(12345L);
        item.setCurrent(true);
        item.setCreateTime("2026-10-09T00:00:00Z");

        String json = mapper.writeValueAsString(item);

        assertTrue(json.contains("\"id\":\"h-001\""), "id key: " + json);
        assertTrue(json.contains("\"avatar_url\":\"https://example.com/a.png\""), "avatar_url key: " + json);
        assertTrue(json.contains("\"content_type\":\"image/png\""), "content_type key: " + json);
        assertTrue(json.contains("\"size_bytes\":12345"), "size_bytes key: " + json);
        assertTrue(json.contains("\"is_current\":true"), "is_current key: " + json);
        assertTrue(json.contains("\"create_time\":\"2026-10-09T00:00:00Z\""), "create_time key: " + json);

        // 关键反向断言：旧 camelCase 不能出现
        assertFalse(json.contains("\"avatarUrl\""), "must not contain camelCase avatarUrl: " + json);
        assertFalse(json.contains("\"contentType\""), "must not contain camelCase contentType: " + json);
        assertFalse(json.contains("\"sizeBytes\""), "must not contain camelCase sizeBytes: " + json);
        assertFalse(json.contains("\"isCurrent\":"), "must not contain camelCase isCurrent: " + json);
        assertFalse(json.contains("\"createTime\""), "must not contain camelCase createTime: " + json);
    }

    @Test
    void userInfo_extraInfoUsesSnakeCase() throws Exception {
        ApiTypes.UserInfo u = new ApiTypes.UserInfo();
        u.setUid(9123L);
        u.setUsername("member");
        u.setEmail("a@b.com");
        u.setExtraInfo(java.util.Map.of("k", "v"));
        String json = mapper.writeValueAsString(u);

        // uid 必须序列化为字符串（与 Go json:"uid,string" 对齐）
        assertTrue(json.contains("\"uid\":\"9123\""), "uid as string: " + json);
        assertTrue(json.contains("\"extra_info\":{"), "extra_info key: " + json);
        assertFalse(json.contains("\"extraInfo\""), "must not contain camelCase extraInfo: " + json);
    }

    @Test
    void uploadAvatarReq_avatarlUrlAndSizeBytesAreSnakeCase() throws Exception {
        ApiTypes.UploadAvatarReq req = new ApiTypes.UploadAvatarReq();
        req.setAvatarUrl("https://example.com/x.png");
        req.setContentType("image/png");
        req.setSizeBytes(99L);
        String json = mapper.writeValueAsString(req);

        assertTrue(json.contains("\"avatar_url\":\"https://example.com/x.png\""), "avatar_url key: " + json);
        assertTrue(json.contains("\"content_type\":\"image/png\""), "content_type key: " + json);
        assertTrue(json.contains("\"size_bytes\":99"), "size_bytes key: " + json);
        assertFalse(json.contains("\"avatarUrl\""), "must not contain camelCase avatarUrl: " + json);
    }

    @Test
    void selectAvatarReq_historyIdIsSnakeCase() throws Exception {
        ApiTypes.SelectAvatarReq req = new ApiTypes.SelectAvatarReq();
        req.setHistoryId("h-42");
        String json = mapper.writeValueAsString(req);

        assertTrue(json.contains("\"history_id\":\"h-42\""), "history_id key: " + json);
        assertFalse(json.contains("\"historyId\""), "must not contain camelCase historyId: " + json);
    }

    @Test
    void createUserReq_extraInfoIsSnakeCase() throws Exception {
        ApiTypes.CreateUserReq req = new ApiTypes.CreateUserReq();
        req.setUsername("u");
        req.setPassword("p");
        req.setExtraInfo(java.util.Map.of("k", "v"));
        String json = mapper.writeValueAsString(req);

        assertTrue(json.contains("\"extra_info\":{"), "extra_info key: " + json);
        assertFalse(json.contains("\"extraInfo\""), "must not contain camelCase extraInfo: " + json);
    }
}