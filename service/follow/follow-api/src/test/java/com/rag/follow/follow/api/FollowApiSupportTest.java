package com.rag.follow.follow.api;

import com.rag.common.exception.BusinessException;
import com.rag.follow.follow.api.interceptor.FollowJwtAuthInterceptor;
import com.rag.follow.follow.api.service.FollowApiSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FollowApiSupport} 的纯单元测试 —— 锁住 Go
 * {@code service/follow/api/internal/logic/follow/helper.go:13-47} 的两条纯函数。
 *
 * <p><b>为什么这两条要单独测、而不是只靠 MVC 测试</b>：
 * {@code extractUserID} 的<b>类型分支表</b>有 6 条（json.Number / string / int64 / int /
 * float64 / default），而真实 JWT 能产生的形态只有其中几种（jjwt 把 JSON 数字解析成
 * Integer/Long/BigInteger/Double）。<b>靠 MVC 只能覆盖到「自然发生」的那几种</b>，
 * 其余分支会被静默漏掉 —— 而其中任何一条判错都是<b>身份伪造</b>级别的问题
 * （把 Go 判 5004 的输入变成合法 uid）。
 */
class FollowApiSupportTest {

    private final FollowApiSupport support = new FollowApiSupport();

    private static MockHttpServletRequest requestWithClaim(Object claim) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (claim != null) {
            request.setAttribute(FollowJwtAuthInterceptor.CLAIM_ATTRIBUTE, claim);
        }
        return request;
    }

    // ================== extractUserID ==================

    @Test
    @DisplayName("claim 缺席 → 5004（不是 401：鉴权层已验过签名，Go 归为业务上的未鉴权）")
    void missingClaimIs5004() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(null)));
        assertEquals(5004, e.getCode());
        assertEquals("unauthorized", e.getMessage());
    }

    @Test
    @DisplayName("整型 claim（Integer / Long / Short / Byte）→ 原样取值")
    void integralClaimsAreAccepted() {
        assertEquals(123L, support.userId(requestWithClaim(123)));
        assertEquals(123L, support.userId(requestWithClaim(123L)));
        assertEquals(123L, support.userId(requestWithClaim((short) 123)));
        assertEquals(123L, support.userId(requestWithClaim((byte) 123)));
    }

    @Test
    @DisplayName("⚠️ 字符串 claim 被接受 —— Go helper.go:23 有 case string: strconv.ParseInt")
    void stringClaimIsAccepted() {
        // ⚠️ 这是 follow 域比 message 域宽的地方：message 的 UserIDFromContext 只接受 json.Number。
        assertEquals(123L, support.userId(requestWithClaim("123")));
        assertEquals(-5L, support.userId(requestWithClaim("-5")));
        assertEquals(123L, support.userId(requestWithClaim("+123")));
    }

    @Test
    @DisplayName("字符串 claim 非法 → 5004（Go: strconv.ParseInt 失败）")
    void illegalStringClaimIs5004() {
        for (String bad : new String[]{"", " 12", "1.5", "1e2", "abc", "9223372036854775808"}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> support.userId(requestWithClaim(bad)),
                    "claim \"" + bad + "\" 应当被判 5004");
            assertEquals(5004, e.getCode());
        }
    }

    @Test
    @DisplayName("浮点 claim → 5004（Go: json.Number(\"1.5\").Int64() 的 ParseInt 失败，不截断）")
    void floatingClaimIs5004() {
        // ⚠️ 不能像 Jackson 默认那样静默截断成 1 —— 那会把 Go 判 5004 的输入变成合法 uid。
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(1.5d))).getCode());
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(1.5f))).getCode());
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(new java.math.BigDecimal("1.5")))).getCode());
    }

    @Test
    @DisplayName("BigInteger 超出 int64 范围 → 5004（Go: strconv.ParseInt range error）")
    void bigIntegerOutOfRangeIs5004() {
        // 9223372036854775808 = 2^63，超出 int64 上界
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(
                        new java.math.BigInteger("9223372036854775808")))).getCode());
        // 边界内仍接受
        assertEquals(Long.MAX_VALUE, support.userId(
                requestWithClaim(new java.math.BigInteger("9223372036854775807"))));
    }

    @Test
    @DisplayName("布尔 / List / Map claim → 5004（Go 的 default 分支）")
    void unsupportedTypesAre5004() {
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(Boolean.TRUE))).getCode());
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(java.util.List.of(1L)))).getCode());
        assertEquals(5004, assertThrows(BusinessException.class,
                () -> support.userId(requestWithClaim(java.util.Map.of("a", 1L)))).getCode());
    }

    @Test
    @DisplayName("uid <= 0 不被拒绝 —— Go 侧同样放行，由 follow-rpc 判 5101")
    void nonPositiveUidIsPassedThrough() {
        assertEquals(0L, support.userId(requestWithClaim(0L)));
        assertEquals(-1L, support.userId(requestWithClaim(-1L)));
    }

    // ================== resolveUserID ==================

    @Test
    @DisplayName("resolveUserID：请求值 > 0 用请求值，否则用当前用户（Go helper.go:43-47）")
    void resolveUserIdFollowsGo() {
        assertEquals(9L, support.resolveUserId(1L, 9L));
        assertEquals(1L, support.resolveUserId(1L, null));
        assertEquals(1L, support.resolveUserId(1L, 0L));
        assertEquals(1L, support.resolveUserId(1L, -5L));
        // 请求值 == 当前用户 → 仍然是请求值（两侧一致，后续归属校验才放行）
        assertEquals(7L, support.resolveUserId(7L, 7L));
    }

    // ================== requireSelfScope ==================

    @Test
    @DisplayName("归属校验：查别人 → 5005 forbidden；查自己 / 不带参数 → 放行")
    void selfScopeGuard() {
        assertEquals(7L, support.requireSelfScope(7L, null));
        assertEquals(7L, support.requireSelfScope(7L, 0L));
        assertEquals(7L, support.requireSelfScope(7L, 7L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> support.requireSelfScope(7L, 8L));
        assertEquals(5005, e.getCode());
        assertEquals("forbidden", e.getMessage());
    }

    // ================== requireRpcSuccess ==================

    @Test
    @DisplayName("rpc 回包 code != 200 → 原样透传 code + msg（Go 的 NewErrCodeMsg，不查本地码表）")
    void rpcSuccessGuard() {
        support.requireRpcSuccess(200, "OK");

        BusinessException e = assertThrows(BusinessException.class,
                () -> support.requireRpcSuccess(5201, "self relation is not allowed"));
        assertEquals(5201, e.getCode());
        assertEquals("self relation is not allowed", e.getMessage());

        // msg 为空串也原样透传（Go 不做兜底替换）
        BusinessException emptyMsg = assertThrows(BusinessException.class,
                () -> support.requireRpcSuccess(5007, ""));
        assertEquals(5007, emptyMsg.getCode());
        assertEquals("", emptyMsg.getMessage());
    }
}