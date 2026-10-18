package com.rag.common.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Jackson 默认序列化：{@code data=null} 时输出 {@code "data":null}（对齐 Go encoding/json）。
 */
class ResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAllFieldsIncludingNullData() throws Exception {
        Response r = new Response();
        r.setCode(200);
        r.setMsg("ok");
        r.setData(null);
        String json = mapper.writeValueAsString(r);
        assertTrue(json.contains("\"code\":200"), json);
        assertTrue(json.contains("\"msg\":\"ok\""), json);
        assertTrue(json.contains("\"data\":null"), json);
    }

    @Test
    void serializesPayload() throws Exception {
        Response r = new Response(40101, "password wrong", null);
        String json = mapper.writeValueAsString(r);
        assertTrue(json.contains("\"code\":40101"), json);
        assertTrue(json.contains("\"msg\":\"password wrong\""), json);
        assertTrue(json.contains("\"data\":null"), json);
    }
}