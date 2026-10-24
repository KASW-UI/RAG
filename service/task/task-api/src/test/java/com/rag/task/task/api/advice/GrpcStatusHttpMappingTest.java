package com.rag.task.task.api.advice;

import com.rag.common.protection.GoStyleCircuitOpenException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * gRPC 状态码 → HTTP 状态码的映射 —— 对应 go-zero v1.10.2
 * {@code rest/internal/errcode/grpc.go:12-42} 的 {@code CodeFromGrpcError}。
 *
 * <h2>为什么这条映射必须<b>逐分支</b>锁死</h2>
 * 它决定「task-rpc 挂了，客户端看到什么状态码」。且它是<b>本域独有</b>的一套：
 * <ul>
 *   <li>本模块<b>没有</b>注册 error handler ⇒ 走的是 HTTP 状态码 + text/plain，
 *       而不是别处的「200 + 业务码信封」；</li>
 *   <li>别处（如 user-api 的 {@code mapRPCStatusToCode}）那套是 5 分支<b>业务码</b>映射，
 *       产出 {@code {code:1003}} —— <b>形状与来源都不同</b>，照抄即漂移
 *       （AGENTS.md §3.1：不要拿另一个域的实测结论当对照基准）。</li>
 * </ul>
 *
 * <h2>body 文本也必须锁</h2>
 * Go 的 body 是 {@code err.Error()}，即 grpc-go 的
 * {@code "rpc error: code = <CamelCase> desc = <message>"}。
 * grpc-java 的 {@code StatusRuntimeException} <b>没有</b>覆写 {@code getMessage()}，
 * 直接用会拿到 {@code io.grpc.Status: Status{code=..., ...}} —— 格式完全不同，
 * 而 body 是客户端可见字节。
 */
class GrpcStatusHttpMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ResponseEntity<byte[]> invokeRuntime(Status status) {
        return handler.handleGrpcStatus(status.asRuntimeException());
    }

    private ResponseEntity<byte[]> invokeChecked(Status status) {
        return handler.handleGrpcStatus(new StatusException(status));
    }

    @ParameterizedTest(name = "Go errcode/grpc.go —— {0} ⇒ HTTP {1}")
    @CsvSource({
            "OK,                   200",
            "INVALID_ARGUMENT,     400",
            "FAILED_PRECONDITION,  400",
            "OUT_OF_RANGE,         400",
            "UNAUTHENTICATED,      401",
            "PERMISSION_DENIED,    403",
            "NOT_FOUND,            404",
            "CANCELLED,            408",
            "ALREADY_EXISTS,       409",
            "ABORTED,              409",
            "RESOURCE_EXHAUSTED,   429",
            "INTERNAL,             500",
            "DATA_LOSS,            500",
            "UNKNOWN,              500",
            "UNIMPLEMENTED,        501",
            "UNAVAILABLE,          503",
            "DEADLINE_EXCEEDED,    504",
    })
    @DisplayName("Go errcode/grpc.go:12-42 —— 17 个分支逐条对上（含三处分组合并）")
    void statusCodeMappingMatchesGo(String grpcCode, int expectedHttp) {
        Status status = Status.fromCode(Status.Code.valueOf(grpcCode))
                .withDescription("d");

        assertEquals(HttpStatus.valueOf(expectedHttp), invokeRuntime(status).getStatusCode(),
                "grpc " + grpcCode);
        // 两条异常类型走同一个判据（Go 的 IsGrpcError / Java 的异常类型）
        assertEquals(HttpStatus.valueOf(expectedHttp), invokeChecked(status).getStatusCode(),
                "StatusException 也必须一致，grpc " + grpcCode);
    }

    @Test
    @DisplayName("Go status/status.go —— body 是 grpc-go 的 err.Error() 文本 + 尾随 \\n")
    void bodyIsGoStatusText() {
        ResponseEntity<byte[]> resp = invokeRuntime(
                Status.INVALID_ARGUMENT.withDescription("user_id invalid"));

        assertEquals("rpc error: code = InvalidArgument desc = user_id invalid\n",
                new String(resp.getBody(), StandardCharsets.UTF_8));
        assertEquals("text/plain; charset=utf-8", resp.getHeaders().getFirst("Content-Type"));
        assertEquals("nosniff", resp.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    @DisplayName("Go status/status.go —— 码名用 grpc-go 的 CamelCase，不是 grpc-java 的 SCREAMING_SNAKE")
    void codeNameIsCamelCaseNotScreamingSnake() {
        assertEquals("rpc error: code = DeadlineExceeded desc = context deadline exceeded\n",
                body(invokeRuntime(Status.DEADLINE_EXCEEDED.withDescription("context deadline exceeded"))));
        assertEquals("rpc error: code = ResourceExhausted desc = x\n",
                body(invokeRuntime(Status.RESOURCE_EXHAUSTED.withDescription("x"))));
    }

    @Test
    @DisplayName("Go status/status.go —— description 为空时仍写 \"desc = \"，不省略")
    void emptyDescriptionStillWritesDescSegment() {
        // grpc-go 的 fmt.Sprintf 无条件写 "desc = %s"，desc 为空就是尾巴上一个空格。
        // 少写这个空格同样是可观察差异。
        assertEquals("rpc error: code = Unknown desc = \n",
                body(invokeRuntime(Status.UNKNOWN)));
    }

    @Test
    @DisplayName("Go get_task_logic.go:44 —— rpc 侧 InvalidArgument 的 body 逐字节对上")
    void rpcSideInvalidArgumentTextMatches() {
        // task-rpc 的 uid<=0 分支抛 status.Error(codes.InvalidArgument, "user_id invalid")
        // ⇒ 到 api 侧的 body 就是这一行。
        assertEquals("rpc error: code = InvalidArgument desc = user_id invalid\n",
                body(invokeRuntime(Status.INVALID_ARGUMENT.withDescription("user_id invalid"))));
    }

    @Test
    @DisplayName("Go breaker.go:19 + responses.go:152 —— 熔断打开是**普通 error** ⇒ 400，不是 503")
    void circuitOpenIsBadRequestNotServiceUnavailable() {
        // ⚠️ 这是最容易想当然的地方：熔断「听起来」该是 503。
        //   但 Go 的 breaker.ErrServiceUnavailable 是 errors.New(...)，
        //   IsGrpcError 为 false ⇒ 走 http.Error(w, ..., http.StatusBadRequest) ⇒ 400。
        ResponseEntity<byte[]> resp =
                handler.handleCircuitOpen(new GoStyleCircuitOpenException("circuit breaker is open"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("circuit breaker is open\n", body(resp));
    }

    @Test
    @DisplayName("Go responses.go:151-153 —— 兜底是 **400**，不是 500")
    void fallbackIsBadRequestNotInternalServerError() {
        ResponseEntity<byte[]> resp =
                handler.handleAny(new IllegalArgumentException("user_id invalid"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertArrayEquals("user_id invalid\n".getBytes(StandardCharsets.UTF_8), resp.getBody());
    }

    private static String body(ResponseEntity<byte[]> resp) {
        return new String(resp.getBody(), StandardCharsets.UTF_8);
    }
}