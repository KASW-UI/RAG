package com.rag.task.task.rpc.mq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code TaskLogEvent} / {@code TaskArticleProgressEvent} 的解析语义。
 *
 * <h2>为什么这组测试最关键</h2>
 * <p>Go 与 Java 对 JSON 的<b>类型判定方向相反</b>：Jackson 2 默认会静默强转
 * （{@code {"cur":"5"}} → {@code 5}、{@code {"user_id":123}} → {@code "123"}），
 * 而 Go 的 {@code encoding/json} 在这两种情况下都<b>报错</b>。
 * 同一条消息在两侧得到相反结论 ⇒ 一侧丢弃、一侧当正常数据处理。
 * 下面的用例逐条锁住判定规则。
 *
 * <p>Go 出处：{@code initGoKa.go:64,87}（路由 / 文章计数）与
 * {@code article_sink.go:126-139}（{@code parseVal}）。
 */
class TaskEventParsingTest {

    /** 与生产代码同配置的 mapper（见 config/Jackson2ObjectMapperConfiguration）。 */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // ==================== TaskLogEvent：逐字段独立 ====================

    @Test
    @DisplayName("正常日志：user_id / article_id 按字符串取出")
    void parsesNormalLogEvent() {
        TaskLogEvent e = TaskLogEvent.parseLenient(MAPPER,
                "{\"ts\":\"2026-01-22T10:11:12.123Z\",\"service\":\"like-service\","
                        + "\"event_id\":\"e1\",\"user_id\":\"42\",\"trace_id\":\"t1\","
                        + "\"article_id\":\"9001\",\"msg\":\"liked\"}");

        assertEquals("42", e.userId());
        assertEquals("9001", e.articleId());
        assertEquals("like-service", e.service());
    }

    @Test
    @DisplayName("🔴 一个字段类型错**不阻断其它字段**（Go 一次 Unmarshal 里已解出的字段会保留）")
    void typeErrorInOneFieldDoesNotBlockOthers() {
        // user_id 是数字 ⇒ Go 报错且该字段保持 ""；但 article_id 已经解出来了
        TaskLogEvent e = TaskLogEvent.parseLenient(MAPPER,
                "{\"user_id\":123,\"article_id\":\"9001\"}");

        assertEquals("", e.userId(), "数字→string 在 Go 侧报错 ⇒ 零值");
        assertEquals("9001", e.articleId(), "★ 另一个字段必须照常解出（Go 的部分填充语义）");
    }

    @Test
    @DisplayName("未知字段被忽略（Go encoding/json 默认行为）")
    void unknownFieldsAreIgnored() {
        TaskLogEvent e = TaskLogEvent.parseLenient(MAPPER,
                "{\"user_id\":\"7\",\"unknown\":{\"a\":[1,2]},\"article_id\":\"8\"}");

        assertEquals("7", e.userId());
        assertEquals("8", e.articleId());
    }

    @Test
    @DisplayName("非法 JSON ⇒ 全零值，路由阶段仍会用空 key emit（Go 只 log 不 return）")
    void invalidJsonYieldsEmptyKeys() {
        TaskLogEvent e = TaskLogEvent.parseLenient(MAPPER, "not-json{");

        assertEquals("", e.userId());
        assertEquals("", e.articleId());
    }

    @Test
    @DisplayName("缺字段 / null ⇒ 零值且不算类型错误")
    void missingOrNullFieldsAreZero() {
        TaskLogEvent missing = TaskLogEvent.parseLenient(MAPPER, "{\"article_id\":\"3\"}");
        assertEquals("", missing.userId());
        assertEquals("3", missing.articleId());

        TaskLogEvent explicitNull = TaskLogEvent.parseLenient(MAPPER,
                "{\"user_id\":null,\"article_id\":null}");
        assertEquals("", explicitNull.userId());
        assertEquals("", explicitNull.articleId());
    }

    // ==================== parseVal：文章 sink 的三返回值 ====================

    @ParameterizedTest(name = "value={0} ⇒ userId={1}, cur={2}, ok={3}")
    @CsvSource(delimiter = '|', value = {
            "'{\"user_id\":\"1902\",\"cur\":3}'   | 1902 | 3 | true",
            "'{\"user_id\":\"1902\",\"cur\":0}'   | 1902 | 1 | true",
            "'{\"user_id\":\"1902\"}'            | 1902 | 1 | true",
            "'{\"cur\":5}'                        | 0    | 5 | false",
            "'{\"user_id\":\"abc\",\"cur\":5}'     | 0    | 5 | false",
            "'{\"user_id\":\"\",\"cur\":5}'        | 0    | 5 | false",
            "'{\"user_id\":null,\"cur\":5}'        | 0    | 5 | false",
    })
    @DisplayName("parseVal 的判定表（Go article_sink.go:126-139）")
    void parseValTable(String value, long expectedUid, long expectedCur, boolean expectedOk) {
        TaskArticleProgressEvent.Parsed p = TaskArticleProgressEvent.parse(MAPPER, value);

        assertEquals(expectedUid, p.userId(), "userId");
        assertEquals(expectedCur, p.cur(), "cur");
        assertEquals(expectedOk, p.ok(), "ok");
    }

    @Test
    @DisplayName("空字符串 ⇒ 直接丢弃（Go: if val == \"\" { return 0,0,false }）")
    void emptyValueIsDropped() {
        assertFalse(TaskArticleProgressEvent.parse(MAPPER, "").ok());
        assertFalse(TaskArticleProgressEvent.parse(MAPPER, null).ok());
    }

    @Test
    @DisplayName("🔴 cur 是字符串 ⇒ 丢弃（Go 把 string 写进 int64 字段会报错）")
    void stringCurIsRejectedUnlikeJacksonDefault() {
        TaskArticleProgressEvent.Parsed p =
                TaskArticleProgressEvent.parse(MAPPER, "{\"user_id\":\"1902\",\"cur\":\"5\"}");

        // ⚠️ Jackson 默认会静默转成 5 ⇒ ok=true。Go 会报错 ⇒ ok=false。
        //    这是本类存在的核心断言：两侧判定方向一致。
        assertFalse(p.ok(), "string→int 在 Go 侧是错误，解析失败 ⇒ 丢弃");
    }

    @Test
    @DisplayName("🔴 cur 是小数 ⇒ 丢弃（Go 报 cannot unmarshal number 3.7 into int64）")
    void floatCurIsRejected() {
        assertFalse(TaskArticleProgressEvent.parse(MAPPER, "{\"user_id\":\"1902\",\"cur\":3.7}").ok());
    }

    @Test
    @DisplayName("🔴 user_id 是数字 ⇒ 丢弃（Go 报 cannot unmarshal number into string）")
    void numericUserIdIsRejected() {
        assertFalse(TaskArticleProgressEvent.parse(MAPPER, "{\"user_id\":1902,\"cur\":3}").ok());
    }

    @Test
    @DisplayName("非法 JSON ⇒ 丢弃（Go: unmarshal 失败 ⇒ return 0,0,false）")
    void invalidJsonIsDropped() {
        assertFalse(TaskArticleProgressEvent.parse(MAPPER, "{oops").ok());
    }

    @Test
    @DisplayName("负数 cur 照常透传（Go 不校验正负）")
    void negativeCurPassesThrough() {
        TaskArticleProgressEvent.Parsed p =
                TaskArticleProgressEvent.parse(MAPPER, "{\"user_id\":\"5\",\"cur\":-2}");

        assertTrue(p.ok());
        assertEquals(5L, p.userId());
        assertEquals(-2L, p.cur(), "Go 不对 cur 做符号校验");
    }

    // ==================== 写出方向：JSON 形状是契约 ====================

    @Test
    @DisplayName("序列化形状逐字对齐 Go：{\"user_id\":\"1902\",\"cur\":3}（字段名与顺序）")
    void serializationShapeMatchesGo() {
        String json = new TaskArticleProgressEvent("1902", 3L).toJson(MAPPER);

        assertEquals("{\"user_id\":\"1902\",\"cur\":3}", json);
    }

    @Test
    @DisplayName("user_id 恒为字符串、cur 恒为数字（与 like-rpc 的 TaskArticleProgressMsg 同形状）")
    void serializationTypesMatchGo() {
        String json = new TaskArticleProgressEvent("42", 7L).toJson(MAPPER);

        assertTrue(json.contains("\"user_id\":\"42\""), json);
        assertTrue(json.contains("\"cur\":7"), json);
        assertFalse(json.contains("\"cur\":\"7\""), "cur 必须是裸数字");
    }

    @Test
    @DisplayName("写出后能被自己的解析器读回来（跨仓库生产者 like-rpc 也用同一形状）")
    void roundTrip() {
        String json = new TaskArticleProgressEvent("1902", 3L).toJson(MAPPER);
        TaskArticleProgressEvent.Parsed p = TaskArticleProgressEvent.parse(MAPPER, json);

        assertEquals(1902L, p.userId());
        assertEquals(3L, p.cur());
        assertTrue(p.ok());
    }
}