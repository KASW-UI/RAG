package com.rag.common.communityfact;

import com.rag.common.observability.ObservabilityConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 <b>共享层逐类契约守门 · communityfact 包</b>：给<b>此前零专属测试</b>的
 * {@code ProcessLogger} 补上行为断言，并顺带锁住 {@code ObservabilityConstants} 的常量值。
 *
 * <h2>Go 出处（逐条）</h2>
 * <ul>
 *   <li>{@code ProcessLogger} ← {@code service/common/communityfact/logging.go:43-49}
 *       （{@code NewProcessLogger} <b>不</b>调 {@code Validate}）
 *       ＋ {@code logging.go:52-57}（{@code ReplaceAttr} 改名）
 *       ＋ {@code logging.go:61-65}（{@code WithAttrs} 注入 6 个 metadata 键）
 *       ＋ {@code logging.go:29-39}（{@code Validate} 是<b>独立方法</b>，由<b>调用方</b>触发）；</li>
 *   <li>{@code ObservabilityConstants} ← {@code service/common/observability/observability.go:19-30}
 *       ＋ {@code :32-36}。</li>
 * </ul>
 *
 * <h2>⚠️ 证据强度</h2>
 * <p><b>单测级（L1 / E3）</b>：断言 Java 侧的键集合与常量值，<b>不是</b>双侧活体对拍。
 * <p>⚠️ 已知边界：<b>键序</b>（Go 的 slog 键序 vs Java 的 JSON 键序）属
 * {@code DRIFT-CF-011}（C 类 · 阶段二）⇒ ⇒ 本门<b>不</b>断言键序，只断言<b>键存在与值</b>。
 */
class ProcessLoggerContractGateTest {

    @Test
    @DisplayName("ProcessLogger：6 个 metadata 键必须全部出现在输出行里（Go logging.go:61-65 的 WithAttrs）")
    void processLoggerEmitsAllSixMetadataKeys() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProcessLogger.ProcessMetadata meta = new ProcessLogger.ProcessMetadata(
                "rtw-comment-fact-dispatch", "local", "0123456789abcdef0123456789abcdef01234567", "inst-1", "community");
        org.slf4j.Logger logger = ProcessLogger.newProcessLogger(out, meta);

        logger.atInfo()
                .addKeyValue("event", "community.fact_delivery.once")
                .addKeyValue("outcome", "succeeded")
                .addKeyValue("sent", true)
                .log("community fact delivery inspected");

        String line = out.toString(StandardCharsets.UTF_8);
        // Go: `logging.go:61-65` ⇒ WithAttrs 注入 service/environment/service_version/instance_id/component/log_source
        assertThat(line).contains("\"service\":\"rtw-comment-fact-dispatch\"");
        assertThat(line).contains("\"environment\":\"local\"");
        assertThat(line).contains("\"service_version\":\"0123456789abcdef0123456789abcdef01234567\"");
        assertThat(line).contains("\"instance_id\":\"inst-1\"");
        assertThat(line).contains("\"component\":\"community\"");
        assertThat(line).contains("\"log_source\":\"application\"");
        // 业务 KV 也必须在
        assertThat(line).contains("\"event\":\"community.fact_delivery.once\"");
    }

    @Test
    @DisplayName("ProcessLogger：metadata 缺字段时 validate() 必须抛（Go logging.go:29-39 的 Validate 是独立方法）")
    void processMetadataValidateRejectsIncompleteIdentity() {
        ProcessLogger.ProcessMetadata meta = new ProcessLogger.ProcessMetadata();
        meta.service = "svc";
        // 缺 environment / version / instance_id / component ⇒ 必须 fail-fast
        assertThatThrownBy(meta::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity incomplete");
    }

    @Test
    @DisplayName("ProcessLogger：environment 必须是四个合法值之一（Go logging.go:29-39）")
    void processMetadataValidateRejectsUnknownEnvironment() {
        ProcessLogger.ProcessMetadata meta = new ProcessLogger.ProcessMetadata(
                "svc", "prod", "0123456789abcdef0123456789abcdef01234567", "inst-1", "community");
        assertThatThrownBy(meta::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment invalid");
    }

    @Test
    @DisplayName("ObservabilityConstants：常量值必须与 Go 的 const 块逐条一致（observability.go:19-30 · :32-36）")
    void observabilityConstantsMatchGoConstBlock() {
        // Go: `SuccessBizCode = 200`
        assertThat(ObservabilityConstants.SUCCESS_BIZ_CODE).isEqualTo(200);
        // Go: `DefaultSlowThreshold = 500 * time.Millisecond`
        assertThat(ObservabilityConstants.DEFAULT_SLOW_THRESHOLD).isEqualTo(Duration.ofMillis(500));
        // Go: `AttrError = "error"` / `AttrErrorKind = "sea.error.kind"` / `AttrBizCode = "sea.biz_code"`
        //      / `AttrBizMsg = "sea.biz_msg"` / `AttrTimeout = "sea.timeout"` / `AttrSlow = "sea.slow"`
        //      / `AttrDuration = "sea.duration_ms"`
        assertThat(ObservabilityConstants.ATTR_ERROR).isEqualTo("error");
        assertThat(ObservabilityConstants.ATTR_ERROR_KIND).isEqualTo("sea.error.kind");
        assertThat(ObservabilityConstants.ATTR_BIZ_CODE).isEqualTo("sea.biz_code");
        assertThat(ObservabilityConstants.ATTR_BIZ_MSG).isEqualTo("sea.biz_msg");
        assertThat(ObservabilityConstants.ATTR_TIMEOUT).isEqualTo("sea.timeout");
        assertThat(ObservabilityConstants.ATTR_SLOW).isEqualTo("sea.slow");
        assertThat(ObservabilityConstants.ATTR_DURATION).isEqualTo("sea.duration_ms");
        // Go: `ErrorKindBusiness = "business"` / `ErrorKindSystem = "system"` / `ErrorKindTimeout = "timeout"`
        assertThat(ObservabilityConstants.ERROR_KIND_BUSINESS).isEqualTo("business");
        assertThat(ObservabilityConstants.ERROR_KIND_SYSTEM).isEqualTo("system");
        assertThat(ObservabilityConstants.ERROR_KIND_TIMEOUT).isEqualTo("timeout");
    }

    @Test
    @DisplayName("ObservabilityConstants：AttributeKey 的键名必须与字符串常量同源（防两套键名漂移）")
    void observabilityConstantsAttributeKeysShareTheSameNames() {
        // ⚠️ 若有人只改了 ATTR_SLOW 字符串而没改 AttributeKey ⇒ 观测面会写进两个不同的键
        assertThat(ObservabilityConstants.ATTR_SLOW_BOOL.getKey()).isEqualTo(ObservabilityConstants.ATTR_SLOW);
        assertThat(ObservabilityConstants.ATTR_TIMEOUT_BOOL.getKey()).isEqualTo(ObservabilityConstants.ATTR_TIMEOUT);
        assertThat(ObservabilityConstants.ATTR_DURATION_DOUBLE.getKey()).isEqualTo(ObservabilityConstants.ATTR_DURATION);
        assertThat(ObservabilityConstants.ATTR_BIZ_CODE_LONG.getKey()).isEqualTo(ObservabilityConstants.ATTR_BIZ_CODE);
        assertThat(ObservabilityConstants.ATTR_BIZ_MSG_STR.getKey()).isEqualTo(ObservabilityConstants.ATTR_BIZ_MSG);
    }
}
