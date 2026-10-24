package com.rag.task.task.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code GetTaskReq} 的解析层契约 —— 对应 Go {@code api/internal/types/types.go:14-54}。
 *
 * <h2>为什么用 Jackson <b>3</b>（{@code tools.jackson}）而不是 Jackson 2</h2>
 * Spring Boot 4（Spring Framework 7）的 MVC 边界用的是 Jackson 3
 * （依据 {@code users/users-common/.../http/GoStyleCoercion.java:62-66}、
 * {@code common/http/GoStyleJsonBodyConverter.java} 的 {@code tools.jackson.databind.json.JsonMapper}）。
 * 这里必须用<b>同一个</b>去反序列化：{@code {"id":1.5} 绑定成什么类型}
 * 是 Jackson 的数值绑定策略决定的，用另一个 mapper 测就测不到线上会发生什么。
 */
class GetTaskReqParsingTest {

    /** 与 MVC 边界同一个 mapper 家族（Boot 4 默认配置）。 */
    private final JsonMapper mapper = JsonMapper.builder().build();

    private GetTaskReq parse(String json) {
        return mapper.readValue(json, GetTaskReq.class);
    }

    private long uidOf(String json) {
        return parse(json).userIdOrThrow();
    }

    // ============== ① UnmarshalJSON：接受 string 或 number ==============

    @Test
    @DisplayName("Go types.go:24 —— JSON 字符串 \"123\" 被接受并 TrimSpace")
    void jsonStringIsAccepted() {
        assertEquals(123L, uidOf("{\"id\":\"123\"}"));
        assertEquals(123L, uidOf("{\"id\":\"  123  \"}"), "TrimSpace 必须在归一化时发生（types.go:25）");
    }

    @Test
    @DisplayName("Go types.go:29-33 —— JSON 数字 123 被接受（这条最容易被\"收紧\"成只收字符串）")
    void jsonNumberIsAccepted() {
        assertEquals(123L, uidOf("{\"id\":123}"));
    }

    @Test
    @DisplayName("Go types.go:25 —— 字符串里的空白被去掉，且只去两端")
    void stringIsTrimmed() {
        assertEquals(42L, uidOf("{\"id\":\" 42 \"}"));
    }

    @ParameterizedTest(name = "Go types.go:18-21 —— {0} 落成空串，随后被判 user_id invalid")
    @ValueSource(strings = {"{\"id\":null}", "{}", "{\"other\":1}"})
    void nullAndMissingCollapseToEmpty(String json) {
        GetTaskReq req = parse(json);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, req::userIdOrThrow);
        assertEquals(GetTaskReq.USER_ID_INVALID, e.getMessage());
    }

    @ParameterizedTest(name = "Go types.go:35 —— {0} 既不是 string 也不是 number")
    @ValueSource(strings = {"{\"id\":true}", "{\"id\":[1,2]}", "{\"id\":{\"a\":1}}"})
    void nonScalarIsRejectedWithGoText(String json) {
        // ⚠️ 报错发生在**反序列化时**（Go 的 UnmarshalJSON 返回 error ⇒ Decode 失败），
        //    不是延迟到 userIdOrThrow()。文案必须是 types.go:35 那一句。
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> parse(json),
                "json = " + json + " ⇒ 反序列化必须直接失败（Go: decoder.Decode 返回 error）");

        GetTaskReq.BadIdTypeException cause = findBadIdType(thrown);
        assertEquals(GetTaskReq.ID_MUST_BE_STRING_OR_INTEGER, cause.getMessage());
    }

    /**
     * Jackson 会把 setter 抛出的异常包一层（包几层取决于版本），所以沿 cause 链找。
     * <p>断言「链上有它」而不是「抛出的就是它」——后者会把测试焊死在 Jackson 的包装层数上。
     * <p>顺带保证 {@code advice/GlobalExceptionHandler#findGoText} 用同一套找法能捞到 Go 原文。
     */
    private static GetTaskReq.BadIdTypeException findBadIdType(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof GetTaskReq.BadIdTypeException bad) {
                return bad;
            }
        }
        throw new AssertionError("cause chain has no BadIdTypeException", e);
    }

    // ============== ② Int64：正整数校验 ==============

    @ParameterizedTest(name = "Go types.go:40-47 —— {0} 被判 user_id invalid")
    @CsvSource({
            "'{\"id\":\"\"}'",         // 空串
            "'{\"id\":\"   \"}'",      // 全空白
            "'{\"id\":\"abc\"}'",      // 非数字
            "'{\"id\":\"12abc\"}'",    // 部分数字
            "'{\"id\":\"1.5\"}'",      // 字符串形式的浮点，ParseInt 拒
            "'{\"id\":\"1.0\"}'",      // ⚠️ 数值上是整数，ParseInt 仍拒（Go types.go:44）
            "'{\"id\":0}'",            // 零
            "'{\"id\":\"0\"}'",
            "'{\"id\":-1}'",           // 负数
            "'{\"id\":\"-1\"}'",
            "'{\"id\":\"9223372036854775808\"}'" // 超 int64 ⇒ ParseInt range error
    })
    void invalidUserIdYieldsGoText(String json) {
        GetTaskReq req = parse(json);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, req::userIdOrThrow);
        assertEquals(GetTaskReq.USER_ID_INVALID, e.getMessage());
    }

    @Test
    @DisplayName("⚠️ Go types.go:29-33 + :44 —— JSON 浮点被**接受成文本**再被 ParseInt 拒，文案是 user_id invalid")
    void jsonFloatIsNotIdMustBeStringOrInteger() {
        // 这是本类最容易做错的一条：若在归一化时把浮点直接拒掉，文案会变成
        // "id must be a string or integer"，与 Go 的 "user_id invalid" 不同 ⇒ 可观察漂移。
        // Go 侧 {"id":1.5} 走的是 json.Number 分支（types.go:29-33）⇒ 存下 "1.5"
        // ⇒ 再由 Int64() 的 ParseInt 拒 ⇒ user_id invalid。
        for (String json : new String[]{"{\"id\":1.5}", "{\"id\":1.0}", "{\"id\":1e2}"}) {
            GetTaskReq req = parse(json);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, req::userIdOrThrow);
            assertEquals(GetTaskReq.USER_ID_INVALID, e.getMessage(), "json = " + json);
        }
    }

    @Test
    @DisplayName("Go types.go:44 —— strconv.ParseInt 接受前导 '+'，Java 侧一致")
    void leadingPlusIsAcceptedLikeGo() {
        assertEquals(123L, uidOf("{\"id\":\"+123\"}"));
    }

    @Test
    @DisplayName("Go gettaskhandler.go:19 —— 未知字段被静默忽略（无 DisallowUnknownFields）")
    void unknownFieldsAreIgnored() {
        assertEquals(7L, uidOf("{\"id\":\"7\",\"zzz\":123,\"nested\":{\"a\":[1]}}"));
    }
}