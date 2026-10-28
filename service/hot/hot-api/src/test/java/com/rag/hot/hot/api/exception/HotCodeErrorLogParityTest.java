package com.rag.hot.hot.api.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.api.metrics.HotApiMetrics;
import com.rag.hot.hot.api.service.HotArticleHydrator;
import com.rag.hot.hot.api.service.HotArticleQueryService;
import com.rag.hot.hot.api.dto.HotArticlesReq;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.common.errmsg.HotErrorMessages;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>「Go {@code (*CodeError).Error()} 的日志形态」</b>的守门测试
 * （{@code docs/DRIFT/hot/common.md} 边界 #2 的配套）。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/hot/common/errmsg.go:15-17} ——
 *       {@code func (e *CodeError) Error() string { return fmt.Sprintf("ErrCode:%d, Errmsg:%s", e.Code, e.Msg) }}；</li>
 *   <li>{@code service/common/logger/logger.go:118-123} ——
 *       日志的 {@code error_reason} 取 {@code err.Error()}；</li>
 *   <li>{@code service/hot/api/internal/logic/hot/getHotArticlesLogic.go:70-74} ——
 *       hot-rpc 返回空响应时：{@code nilRespErr := hotcommon.NewErrCode(5001)}；
 *       {@code logger.LogBusinessErr(l.ctx, 5001, nilRespErr)}。</li>
 *   <li>{@code service/hot/api/hotcenter.go:53-66} ——
 *       HTTP 信封是<b>逐字段复制</b>（{@code codeErr.Code} / {@code codeErr.Msg}），
 *       <b>不是</b> {@code Error()} 整串。</li>
 * </ul>
 *
 * <h3>⚠️ 为什么「日志」和「信封」要分成两个用例</h3>
 * <p>Go 侧这两个面用的是<b>同一个</b> {@code *CodeError}，但取值方式不同：
 * 日志取 {@code Error()} 整串、信封取 {@code Msg} 纯文案。
 * RAG 侧 {@code Throwable} 只有 {@code getMessage()} 一个出口
 * ⇒ 必须<b>显式分成两个面</b>，否则修一头坏一头：
 * <ul>
 *   <li>只让 {@code getMessage()} 返回整串 ⇒ <b>信封被污染</b>（响应体多一段前缀）⇒ 新漂移；</li>
 *   <li>只让 {@code getMessage()} 返回纯文案 ⇒ <b>日志少段前缀</b> ⇒ 老漂移还在。</li>
 * </ul>
 *
 * <h3>断言的是<b>真触发</b>的日志事件，不是内部字段</h3>
 * <p>本测试挂 logback 的 {@link ListAppender} 到<b>真实</b>的 logger 上，
 * 走完整的 {@code HotArticleQueryService} → {@code LoggerFactoryBean.logBusinessErr}
 * → {@code Logger.doLogBusinessErr} → slf4j 链路 ⇒ 断言的是<b>日志里实际会出现的字段</b>。
 * ⚠️ 只断言 {@code HotCodeError.getMessage()} 的返回值不够 ——
 * 那证明不了「日志链路真的用了它」。
 */
class HotCodeErrorLogParityTest {

    /** 日志 logger 名 = {@code spring.application.name}（{@code LoggerFactoryBean} 构造注入）。 */
    private static final String SERVICE_NAME = "hot-api";

    /** Go {@code errmsg.go:15-17} 的 {@code (*CodeError).Error()} 在 5001 上的逐字节形态。 */
    private static final String GO_ERROR_TEXT = "ErrCode:5001, Errmsg:internal server error";

    /** Go {@code GetErrMsg(5001)} —— 信封里的纯文案。 */
    private static final String GO_BIZ_MSG = "internal server error";

    private ch.qos.logback.classic.Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        logbackLogger = ctx.getLogger(SERVICE_NAME);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        if (logbackLogger != null && appender != null) {
            logbackLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("Go errmsg.go:15-17 —— HotCodeError.getMessage() 必须逐字节等于 (*CodeError).Error()")
    void errorTextMatchesGoByteForByte() {
        HotCodeError err = new HotCodeError(
                HotErrorCode.ERROR_SERVER_COMMON.getCode(),
                HotErrorMessages.getMsg(HotErrorCode.ERROR_SERVER_COMMON.getCode()));

        assertEquals(GO_ERROR_TEXT, err.getMessage(),
                "日志 error_reason 必须与 Go 的 (*CodeError).Error() 逐字节一致");
        assertEquals(GO_BIZ_MSG, err.getBizMsg(),
                "业务文案必须保持纯文案 —— 它要进 HTTP 信封");
    }

    @Test
    @DisplayName("Go getHotArticlesLogic.go:72-73 —— hot-rpc 返回空响应 ⇒ 记 business_error 且 error_reason 逐字节对齐")
    void nilHotResponseLogsGoShapedBusinessError() {
        HotRpcClient hotRpcClient = mock(HotRpcClient.class);
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(null);

        HotApiMetrics metrics = new HotApiMetrics(new SimpleMeterRegistry());
        HotArticleQueryService service = new HotArticleQueryService(
                hotRpcClient,
                mock(HotArticleHydrator.class),
                metrics,
                new LoggerFactoryBean(SERVICE_NAME));

        assertThrows(RuntimeException.class,
                () -> service.getHotArticles(new HotArticlesReq(1, 20)),
                "hot-rpc 返回空响应必须整包失败（Go :70-75 的 fatal 分支）");

        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size(),
                "应当<b>恰好一条</b>业务错误日志；此前该分支记的是 logInfo"
                        + "⇒ 级别与字段集都与 Go 不同");

        ILoggingEvent event = events.get(0);
        assertEquals(Level.ERROR, event.getLevel(),
                "Go 侧是 LogBusinessErr ⇒ error 级；info 级是漂移");
        assertEquals(GO_ERROR_TEXT, kv(event, "error_reason"),
                "error_reason 必须逐字节等于 Go 的 (*CodeError).Error()");
        assertEquals("5001", kv(event, "error_code"),
                "error_code 必须存在且为 5001（Go 的 LogBusinessErr 带 code 参数）");
    }

    @Test
    @DisplayName("Go hotcenter.go:53-66 —— 信封只取 Msg 纯文案，不得被 Error() 整串污染")
    void envelopeKeepsPlainBusinessMessage() {
        com.rag.hot.hot.api.advice.GlobalExceptionHandler handler =
                new com.rag.hot.hot.api.advice.GlobalExceptionHandler();
        HotCodeError err = new HotCodeError(
                HotErrorCode.ERROR_SERVER_COMMON.getCode(),
                HotErrorMessages.getMsg(HotErrorCode.ERROR_SERVER_COMMON.getCode()));

        var response = handler.onBusinessException(err);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(5001, response.getBody().getCode());
        assertEquals(GO_BIZ_MSG, response.getBody().getMsg(),
                "信封的 msg 必须是纯文案 —— Go 是逐字段复制 codeErr.Msg，不是 Error() 整串");
        assertTrue(!response.getBody().getMsg().contains("ErrCode:"),
                "信封里出现 ErrCode: 前缀 ⇒ 把日志串泄漏进了响应体");
    }

    // ---------------------------------------------------------------- helpers

    /** 取 slf4j key-value 对里的某个字段值。 */
    private static String kv(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(p -> key.equals(p.key))
                .map(p -> String.valueOf(p.value))
                .findFirst()
                .orElse(null);
    }
}
