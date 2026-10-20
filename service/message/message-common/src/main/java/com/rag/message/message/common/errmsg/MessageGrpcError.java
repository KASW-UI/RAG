package com.rag.message.message.common.errmsg;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * message 服务的错误承载与 gRPC 错误转换工具。
 *
 * <p>对应 Go 版 {@code service/message/common/errmsg.go} 的下半部分：
 * {@code CodeError} 结构体 + {@code NewErrCode} / {@code NewErrCodeMsg} 工厂 +
 * {@code GRPCError} / {@code BizCodeFromError} 转换函数。
 *
 * <p>对应 Go 什么：
 * <ul>
 *   <li>Go 的 {@code *CodeError}（满足 {@code error} 接口的 struct）→
 *       Java 的 {@link CodeError}（{@link RuntimeException}），用 code + msg 字段携带业务错</li>
 *   <li>Go 的 {@code status.Error(codes.X, bizMsg)}（返回 {@code *status.Status}，满足 error）→
 *       Java 的 {@link #grpcError(Status.Code, int)} 返回 {@link StatusException}，
 *       由 gRPC server runtime 把它再转回 wire 上的 status（gRPC code 在 header，
 *       description 在 message 字段），跟 Go 的传输语义一致</li>
 *   <li>Go 的 {@code BizCodeFromError} → Java 的 {@link #bizCodeFromError(Throwable)}，
 *       行为对照表见下</li>
 * </ul>
 *
 * <p>框架职责：
 * <ul>
 *   <li>{@link CodeError} 是 API/HTTP 路径上的业务错误载体；
 *       业务 code 走 HTTP envelope 的 {@code code} 字段，msg 走 {@code msg} 字段
 *       （保留 Go {@code json:"code"} / {@code json:"msg"} 标签的字段名）</li>
 *   <li>{@link #grpcError} / {@link #bizCodeFromError} 是 RPC 路径上的错误双向转换：
 *       业务侧用 biz code，调 gRPC 时变成 gRPC code + biz msg 进 wire；
 *       收 gRPC 响应时反向恢复成 biz code，让 logger / 业务侧只看一个数值</li>
 *   <li>这两个职责没有 Spring 现成方案：Spring 的 {@code ResponseStatusException} /
 *       {@code ProblemDetail} 都是 HTTP 错误模型，不承载 gRPC code；
 *       io.grpc 是项目现有依赖（父 pom 统一管理），直接用</li>
 * </ul>
 */
public final class MessageGrpcError {

    private MessageGrpcError() {
        // 工具类，禁止 new
    }

    /**
     * 对应 Go {@code type CodeError struct{ Code int; Msg string }} 的 Java 版本：
     * 携带业务码 + 业务文案 的 unchecked 异常，保留 Go 端 JSON 标签的字段名（code / msg），
     * 让 API 层未来用 Jackson 序列化时输出与 Go 一致的 JSON key。
     *
     * <p>{@link #getMessage()} 输出与 Go {@code Error()} 字符串完全相同：
     * {@code "ErrCode:XXX, Errmsg:YYY"}，便于 grep。
     *
     * <p>Jackson 注解说明：
     * <ul>
     *   <li>{@link JsonAutoDetect#getterVisibility()} 关成 NONE，把
     *       {@link Throwable} 继承的 {@code getMessage()} / {@code getLocalizedMessage()}
     *       / {@code getCause()} / {@code getStackTrace()} 全部屏蔽掉，
     *       避免序列化出 Go 端不存在的 {@code message} / {@code localizedMessage} /
     *       {@code cause} / {@code stackTrace} 字段（Go 的
     *       {@code encoding/json} 只序列化导出字段，{@code Message()} 是方法不是字段，
     *       不会被序列化）</li>
     *   <li>{@link JsonProperty} 只挂在我们真正要暴露的两个 getter 上，
     *       显式钉死 JSON key = {@code code} / {@code msg}</li>
     * </ul>
     *
     * <p>为什么不复用 Spring 的 {@code ResponseStatusException} /
     * {@code ErrorResponseException}：它们绑死 HTTP status，跟我们走 gRPC + 业务 code
     * 双轨的契约不匹配；强行套会导致业务 code 被 HTTP status 替代，破坏 L1 外部契约。
     */
    @JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static final class CodeError extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int code;
        private final String msg;

        public CodeError(int code, String msg) {
            super(String.format("ErrCode:%d, Errmsg:%s", code, msg));
            this.code = code;
            this.msg = msg;
        }

        /**
         * 对应 Go 字段 {@code Code int `json:"code"`}。
         */
        @JsonProperty("code")
        public int getCode() {
            return code;
        }

        /**
         * 对应 Go 字段 {@code Msg string `json:"msg"`}。
         */
        @JsonProperty("msg")
        public String getMsg() {
            return msg;
        }
    }

    /**
     * 对应 Go {@code NewErrCode(code int) error}：
     * 用 {@code getMsg(code)} 查表得到 msg，构造一个 {@link CodeError}。
     */
    public static CodeError newCode(int code) {
        return new CodeError(code, MessageErrorMessages.getMsg(code));
    }

    /**
     * 对应 Go {@code NewErrCodeMsg(code int, msg string) error}：
     * 直接使用传入的 msg，不查表。
     */
    public static CodeError newCodeMsg(int code, String msg) {
        return new CodeError(code, msg);
    }

    /**
     * 对应 Go {@code GRPCError(grpcCode codes.Code, bizCode int) error}：
     * 用 {@code bizCode} 查表得到 msg，塞进 gRPC status 的 description，
     * 再以给定的 gRPC code 构造 {@link StatusException}。
     *
     * <p>这是 Go 服务之间约定俗成的"业务码藏在 gRPC status 文案里"的 wire 格式：
     * 客户端拿到 gRPC error 后调 {@link #bizCodeFromError(Throwable)}，
     * 先用 description 反查回业务码，反查失败再按 gRPC code class 兜底。
     * 之所以这么设计是历史原因（Go 端用 message 而非 details 携带业务码），
     * 属于 L3 行为相关技术语义，必须保持。
     */
    public static StatusException grpcError(Status.Code grpcCode, int bizCode) {
        return Status.fromCode(grpcCode)
                .withDescription(MessageErrorMessages.getMsg(bizCode))
                .asException();
    }

    /**
     * msg → code 反查表，对应 Go 的
     * <pre>
     * var bizCodeByMsg = func() map[string]int {
     *     lookup := make(map[int]string, len(codeMsg))
     *     for code, msg := range codeMsg {
     *         if code == Success || code == Error { continue }
     *         if _, exists := lookup[msg]; !exists { lookup[msg] = code }
     *     }
     *     return lookup
     * }()
     * </pre>
     *
     * <p>对应行为：
     * <ul>
     *   <li>排除 {@link MessageErrorCode#SUCCESS} / {@link MessageErrorCode#ERROR}，
     *       因为它们的 msg（"OK" / "FAIL"）过于泛化，反查会乱套</li>
     *   <li>first-write-wins 去重（Go 用 {@code if _, exists := lookup[msg]; !exists}）；
     *       当前 message 服务的所有 msg 都是唯一的，所以实际结果是 1:1 反查；
     *       保留去重逻辑防止以后加重复项时反查行为漂移</li>
     * </ul>
     */
    private static final Map<String, Integer> BIZ_CODE_BY_MSG;

    static {
        Map<String, Integer> reverse = new HashMap<>();
        for (Map.Entry<Integer, String> entry : MessageErrorMessages.MSG_BY_CODE.entrySet()) {
            int code = entry.getKey();
            if (code == MessageErrorCode.SUCCESS.getCode()
                    || code == MessageErrorCode.ERROR.getCode()) {
                continue;
            }
            reverse.putIfAbsent(entry.getValue(), code);
        }
        BIZ_CODE_BY_MSG = Collections.unmodifiableMap(reverse);
    }

    /**
     * 对应 Go {@code BizCodeFromError(err error) int}：
     * 从 gRPC / 业务 err 中还原业务码。
     *
     * <p>对应行为（与 Go 行为对照表）：
     * <ul>
     *   <li>{@code err == null} → {@link MessageErrorCode#SUCCESS}（200）</li>
     *   <li>{@link StatusException} / {@link StatusRuntimeException} →
     *       先用 status description 反查 {@link #BIZ_CODE_BY_MSG}；
     *       查不到再按 status code class 兜底映射（与 Go switch 块一致）</li>
     *   <li>{@link CodeError} → 返回它的 {@code code} 字段</li>
     *   <li>其它任意 {@link Throwable} → {@link MessageErrorCode#CODE_SERVER_BUSY}（1015）</li>
     * </ul>
     *
     * <p>顺序重要：先识别 status（因为 status 也可能裹着业务码），
     * 再识别 CodeError（API 侧在拿到 RPC 错误后会用 {@link #newCode} 包一层），最后兜底。
     */
    public static int bizCodeFromError(Throwable err) {
        if (err == null) {
            return MessageErrorCode.SUCCESS.getCode();
        }

        Status status = extractStatus(err);
        if (status != null) {
            Integer code = BIZ_CODE_BY_MSG.get(status.getDescription());
            if (code != null) {
                return code;
            }
            return mapStatusCodeToBizCode(status.getCode());
        }

        if (err instanceof CodeError) {
            return ((CodeError) err).getCode();
        }

        return MessageErrorCode.CODE_SERVER_BUSY.getCode();
    }

    /**
     * 对应 Go {@code BizCodeFromError} 末尾的 switch：
     * 当 status description 没在反查表里时，按 gRPC code class 兜底映射到
     * message 服务的业务码。其它未列出的 gRPC code 一律返回 {@link MessageErrorCode#CODE_SERVER_BUSY}。
     */
    private static int mapStatusCodeToBizCode(Status.Code grpcCode) {
        switch (grpcCode) {
            case INVALID_ARGUMENT:
                return MessageErrorCode.ERROR_INVALID_PARAM.getCode();
            case UNAUTHENTICATED:
                return MessageErrorCode.ERROR_UNAUTHORIZED.getCode();
            case PERMISSION_DENIED:
                return MessageErrorCode.ERROR_FORBIDDEN.getCode();
            case ALREADY_EXISTS:
                return MessageErrorCode.ERROR_ALREADY_EXIST.getCode();
            case NOT_FOUND:
                return MessageErrorCode.ERROR_NOT_FOUND.getCode();
            case INTERNAL:
                return MessageErrorCode.ERROR_SERVER_COMMON.getCode();
            default:
                return MessageErrorCode.CODE_SERVER_BUSY.getCode();
        }
    }

    /**
     * 同时识别 checked 的 {@link StatusException} 和 unchecked 的
     * {@link StatusRuntimeException} —— gRPC client stub 经常抛后者，
     * 而手写 server 端一般抛前者，所以两端都要兜住。
     *
     * <p>返回 {@code null} 表示这个 throwable 不是 gRPC status error，
     * 调用方会进入 CodeError / 其它异常分支。
     */
    private static Status extractStatus(Throwable err) {
        if (err instanceof StatusException) {
            return ((StatusException) err).getStatus();
        }
        if (err instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) err).getStatus();
        }
        return null;
    }
}