package com.rag.hot.hot.api.form;

import com.rag.common.exception.BusinessException;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.common.errmsg.HotErrorMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GoFormValues} 的解析规则测试。
 *
 * <p><b>对照基准</b>：go-zero v1.10.2 的三处消费点
 * <ul>
 *   <li>{@code rest/httpx/util.go:44-45}：空值丢弃</li>
 *   <li>{@code core/mapping/utils.go:120-121}：{@code reflect.Int32 → strconv.ParseInt(str,10,32)}</li>
 *   <li>{@code hotcenter.go:53-66}：解析失败的出口是 200 + 5001</li>
 * </ul>
 */
class GoFormValuesTest {

    @Test
    @DisplayName("空值丢弃：null / 空串 都不进 map ⇒ 走 tag 默认值")
    void emptyValueFallsBackToDefault() {
        assertNull(GoFormValues.raw(null));
        assertNull(GoFormValues.raw(""));
        assertEquals(1, GoFormValues.int32(null, "page", 1));
        assertEquals(20, GoFormValues.int32("", "page_size", 20));
    }

    @Test
    @DisplayName("普通十进制按 base-10 解释")
    void parsesDecimal() {
        assertEquals(2, GoFormValues.int32("2", "page", 1));
        assertEquals(50, GoFormValues.int32("50", "page_size", 20));
        assertEquals(-5, GoFormValues.int32("-5", "page", 1));
        assertEquals(7, GoFormValues.int32("+7", "page", 1));
    }

    @Test
    @DisplayName("⚠️ 前导 0 是十进制不是八进制（Go 用 strconv.ParseInt(str,10,32)）")
    void leadingZeroIsDecimalNotOctal() {
        assertEquals(10, GoFormValues.int32("010", "page", 1),
                "?page=010 在 Go 是 10；Spring 默认的 Integer.decode 会给 8");
    }

    @Test
    @DisplayName("⚠️ 0x 前缀在 Go 是解析失败，不是 16")
    void hexPrefixIsRejected() {
        assertThrows(BusinessException.class, () -> GoFormValues.int32("0x10", "page", 1));
    }

    @ParameterizedTest(name = "page={0}")
    @ValueSource(strings = {"abc", "1.5", "1_0", "99999999999", "-99999999999", " 2"})
    @DisplayName("⚠️ 解析失败 → BusinessException(5001)，HTTP 仍为 200（不是 400）")
    void parseFailureMapsToGoErrorEnvelope(String raw) {
        BusinessException e = assertThrows(BusinessException.class,
                () -> GoFormValues.int32(raw, "page", 1));
        assertEquals(HotErrorCode.ERROR_SERVER_COMMON.getCode(), e.getCode());
        // ⚠️ msg 必须**恰好**等于 GetErrMsg(5001)；Go 的 parse 错误不是 *CodeError，
        //    走的是 SetErrorHandler 的 GetErrMsg 兜底，wire 上看不到任何解析细节。
        assertEquals("internal server error", e.getMessage());
        assertEquals(HotErrorMessages.getMsg(HotErrorCode.ERROR_SERVER_COMMON.getCode()), e.getMessage());
        // 细节只挂在 cause 上（日志可见，wire 不可见）
        assertEquals(true, e.getCause() instanceof NumberFormatException);
    }
}