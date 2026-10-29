package com.rag.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>共享层逐类契约守门 · exception 包</b>：给<b>此前零专属测试</b>的 2 个类补上行为断言。
 *
 * <h2>Go 出处（逐条）</h2>
 * <ul>
 *   <li>{@code BusinessException} ← {@code service/common/response/response.go:3-7}
 *       （信封三字段 {@code code/msg/data}）＋ {@code service/message/common/errmsg.go:99}
 *       （{@code func BizCodeFromError(err error) int} ⇒ 业务码取值口径）；</li>
 *   <li>{@code AuthenticationException} ← {@code service/common/response/response.go:3-7}
 *       ＋ Go 侧「错误签名在读取 RPC 前即 401」的契约（{@code service/user/user/api/identity_contract.md:11}）。</li>
 * </ul>
 *
 * <h2>⚠️ 表达层说明（AGENTS.md §3.2）</h2>
 * <p>Go 侧用<b>双返回</b>（{@code return nil, errmsg.XXX}）表达失败，Java 侧用<b>异常</b>表达
 * ⇒ 这是<b>表达层 idiomatization</b> ⇒ ⇒ 本门断言的是<b>可观察形状等价</b>
 * （都最终变成 {@code code/msg/data} 三字段信封），<b>不是</b>「Java 必须像 Go 一样返回两个值」。
 *
 * <h2>⚠️ 证据强度</h2>
 * <p><b>单测级（L1 / E3）</b>：断言 Java 侧的形状契约，<b>不是</b>双侧活体对拍。
 */
class ExceptionContractGateTest {

    @Test
    @DisplayName("BusinessException：业务码必须原样保留，消息必须原样保留（Go 信封的 code/msg 两字段）")
    void businessExceptionKeepsCodeAndMessage() {
        BusinessException ex = new BusinessException(1001, "用户名已存在");
        // Go: `return nil, errmsg.ERROR_USER_EXIST` ⇒ 业务码是 int、提示语是 string
        assertThat(ex.getCode()).as("业务码原样保留").isEqualTo(1001);
        assertThat(ex.getMessage()).as("提示语原样保留").isEqualTo("用户名已存在");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("BusinessException：码与消息都不得被吞掉（异常处理器靠 getCode() 分派 HTTP 状态码）")
    void businessExceptionDoesNotLoseItsPayload() {
        BusinessException ex = new BusinessException(5003, "参数错误");
        assertThat(ex.getCode()).isEqualTo(5003);
        // ⚠️ 不得退化成 "java.lang.RuntimeException: 5003"（那会丢掉 msg ⇒ 域侧错误形状漂移）
        assertThat(ex.getMessage()).doesNotContain("java.lang.");
    }

    @Test
    @DisplayName("AuthenticationException：必须是 BusinessException 的子类（域侧 GlobalExceptionHandler 一条分支处理两类）")
    void authenticationExceptionIsABusinessException() {
        AuthenticationException ex = new AuthenticationException(401, "未授权");
        assertThat(ex).isInstanceOf(BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(401);
        assertThat(ex.getMessage()).isEqualTo("未授权");
    }

    @Test
    @DisplayName("AuthenticationException：必须能被单独识别（域侧要区分 401 与普通业务失败）")
    void authenticationExceptionIsDistinguishable() {
        AuthenticationException ex = new AuthenticationException(401, "未授权");
        assertThat(ex).isExactlyInstanceOf(AuthenticationException.class);
        assertThat(new BusinessException(401, "普通业务失败"))
                .as("同码但不同类型 ⇒ 域侧仍能区分「鉴权失败」与「业务失败」")
                .isNotInstanceOf(AuthenticationException.class);
    }
}
