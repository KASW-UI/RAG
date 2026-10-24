package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * uid 解析语义回归测试。
 *
 * <p>背景：6 个 Service 各自持有一份 {@code parseUid}，解析失败时返回 {@code 0L}
 * 而不报错。那会把「客户端传了非法 uid」静默降级成「查询 uid=0 的用户」，
 * 客户端拿到 {@code 1003 用户不存在} 而不是 {@code 1017 请求参数错误}，
 * 既误分类了错误类型，也掩盖了调用方的参数拼装 bug。
 *
 * <p>参考侧 {@code parseLongOrNull} → {@code LogicResult.fail(ERROR_REQUEST_PARAM)}
 * 就是抛 1017，这里恢复该语义并把实现收敛到 {@link AdminApiSupport#parseUid} 一份。
 */
class AdminUidParseTest {

    @Test
    @DisplayName("合法 uid 正常解析")
    void validUid_parses() {
        assertEquals(4242L, AdminApiSupport.parseUid("4242"));
    }

    @Test
    @DisplayName("null → 1017 请求参数错误（不再静默转 0）")
    void nullUid_throws1017() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> AdminApiSupport.parseUid(null));
        assertEquals(ErrorCode.ERROR_REQUEST_PARAM.getCode(), e.getCode());
        assertEquals(1017, e.getCode());
    }

    @Test
    @DisplayName("空串 → 1017 请求参数错误")
    void emptyUid_throws1017() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> AdminApiSupport.parseUid(""));
        assertEquals(1017, e.getCode());
    }

    @Test
    @DisplayName("非数字（'abc'）→ 1017 请求参数错误，而不是 1003 用户不存在")
    void nonNumericUid_throws1017() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> AdminApiSupport.parseUid("abc"));
        assertEquals(1017, e.getCode());
    }

    @Test
    @DisplayName("溢出串（超出 long 范围）→ 1017")
    void overflowingUid_throws1017() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> AdminApiSupport.parseUid("99999999999999999999999"));
        assertEquals(1017, e.getCode());
    }
}