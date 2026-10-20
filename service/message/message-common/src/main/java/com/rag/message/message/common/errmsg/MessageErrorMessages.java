package com.rag.message.message.common.errmsg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * message 服务业务错误码 → 文案 映射。
 *
 * <p>对应 Go 版 {@code service/message/common/errmsg.go} 的 {@code codeMsg} map +
 * {@code GetErrMsg(code int) string} 函数：
 * <ul>
 *   <li>Go 用 {@code map[int]string} 在程序启动时构造一次</li>
 *   <li>Java 用 {@code static final Map<Integer, String>} 在 static block 构造一次并 unmodifiable 包装</li>
 *   <li>{@link #getMsg(int)} 的语义与 Go {@code GetErrMsg} 完全一致：未知 code 返回
 *       {@code Error} 的 msg（即 "FAIL"），而不是 null</li>
 * </ul>
 *
 * <p>对应 Go 什么：Go 里这是个全局 {@code var codeMsg}，所有 Logic 都引用它；
 * Java 里改用不可变 Map 是因为 static final + 不可变包装能在并发读路径下省掉同步成本，
 * 也防止有人意外在运行时改它。
 *
 * <p>框架职责：
 * <ul>
 *   <li>作为业务侧唯一的"code → 给人看的提示语"真源；HTTP envelope 的 {@code msg} 字段、
 *       gRPC status 的 description 都从这里取</li>
 *   <li>不参与任何 i18n / 国际化逻辑；项目当前没有 i18n 需求，强行套 Spring MessageSource
 *       会引入配置文件、Locale 解析、fallback 链等与当前架构不匹配的复杂度</li>
 * </ul>
 */
public final class MessageErrorMessages {

    private MessageErrorMessages() {
        // 工具类，禁止 new
    }

    /**
     * code → msg 表，与 Go 端 {@code codeMsg} 1:1 对齐；
     * 顺序也按 Go 文件里的书写顺序，便于 review 时快速对照。
     *
     * <p>注意：所有 msg 都是唯一的，因此 {@link MessageGrpcError#BIZ_CODE_BY_MSG} 反查是
     * 1:1 的；如果以后新增项要保持 msg 唯一性，否则反查会拿到非确定性的 code。
     *
     * <p>可见性：package-private，对应 Go 里 {@code var codeMsg} 也是包级私有变量；
     * 同包的 {@link MessageGrpcError} 需要遍历它构建 msg → code 反查表，
     * 但对外业务模块只通过 {@link #getMsg(int)} 访问，不暴露原始 map。
     */
    static final Map<Integer, String> MSG_BY_CODE;

    static {
        Map<Integer, String> map = new HashMap<>();
        // === 通用 ===
        map.put(MessageErrorCode.SUCCESS.getCode(),               "OK");
        map.put(MessageErrorCode.ERROR.getCode(),                 "FAIL");
        map.put(MessageErrorCode.CODE_SERVER_BUSY.getCode(),      "service busy");
        map.put(MessageErrorCode.ERROR_SERVER_COMMON.getCode(),   "internal server error");
        map.put(MessageErrorCode.ERROR_NOT_FOUND.getCode(),       "record not found");
        map.put(MessageErrorCode.ERROR_INVALID_PARAM.getCode(),   "invalid parameter");
        map.put(MessageErrorCode.ERROR_UNAUTHORIZED.getCode(),    "unauthorized");
        map.put(MessageErrorCode.ERROR_FORBIDDEN.getCode(),       "forbidden");
        map.put(MessageErrorCode.ERROR_ALREADY_EXIST.getCode(),   "already exists");
        map.put(MessageErrorCode.ERROR_DB_SELECT.getCode(),       "database query failed");
        map.put(MessageErrorCode.ERROR_DB_UPDATE.getCode(),       "database update failed");
        // === 用户 / 管理员 ===
        map.put(MessageErrorCode.ERROR_USER_NOT_FOUND.getCode(),  "user not found");
        map.put(MessageErrorCode.ERROR_ADMIN_NOT_FOUND.getCode(), "admin not found");
        // === 消息 / 会话 / 通知 ===
        map.put(MessageErrorCode.ERROR_SELF_CHAT.getCode(),           "self chat is not allowed");
        map.put(MessageErrorCode.ERROR_CONVERSATION_MISS.getCode(),   "conversation not found");
        map.put(MessageErrorCode.ERROR_NOTIFICATION_MISS.getCode(),   "notification not found");
        map.put(MessageErrorCode.ERROR_CHAT_PENDING.getCode(),        "waiting for the other side to reply");
        map.put(MessageErrorCode.ERROR_ADMIN_ONLY_INIT.getCode(),     "only admin can initiate this conversation");
        map.put(MessageErrorCode.ERROR_FIRST_MSG_TOO_LONG.getCode(),  "first private message cannot exceed 50 characters");
        map.put(MessageErrorCode.ERROR_UNSUPPORTED_ROLE.getCode(),    "unsupported sender role");
        MSG_BY_CODE = Collections.unmodifiableMap(map);
    }

    /**
     * 对应 Go {@code GetErrMsg(code int) string}：
     * 给一个业务 code，返回该 code 对应的提示文案；
     * 找不到时返回 {@code Error}（500）的 msg，即 {@code "FAIL"}，与 Go fallback 一致。
     *
     * <p>本方法在多处被调用：RPC logic 构造 successBaseResp 时、{@code GRPCError} 构造
     * gRPC status description 时、API 层从 rpc envelope 复制 msg 时。
     */
    public static String getMsg(int code) {
        String msg = MSG_BY_CODE.get(code);
        if (msg != null) {
            return msg;
        }
        return MSG_BY_CODE.get(MessageErrorCode.ERROR.getCode());
    }
}