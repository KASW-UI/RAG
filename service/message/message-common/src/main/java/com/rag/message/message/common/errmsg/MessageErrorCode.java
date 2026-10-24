package com.rag.message.message.common.errmsg;

/**
 * message 服务业务错误码常量。
 *
 * <p>对应 Go 版 {@code service/message/common/errmsg.go} 的 {@code const ( ... )} 块。
 * 每个枚举常量绑定一个 int 值，与 Go 端常量字面量 1:1 对齐；
 * HTTP / gRPC 客户端会以这个 int 写回 {@code code} 字段，因此数值不能变。
 *
 * <p>对应 Go 什么：Go 里这些都是 {@code const int}；
 * Java 这边用 enum 是为了让编译器在编译期就拦下拼错或漂移，
 * 字节码里也仍然是 {@code public static final int}。
 * 框架职责：错误码是一个数值契约，由本枚举统一持有；任何业务模块
 * （RPC logic、API handler、metrics 标签）只能引用枚举名，不允许再硬编码 int。
 *
 * <p>错误码分段（与 Go 一致，跨服务对外契约，不能跨段混用）：
 * <ul>
 *   <li>200 / 500：通用成功 / 失败回执</li>
 *   <li>1015：服务繁忙（兜底码）</li>
 *   <li>5001-5099：通用错误（系统内部、参数、鉴权、DB 等）</li>
 *   <li>5100-5199：用户 / 管理员模块</li>
 *   <li>5200-5299：消息 / 会话 / 通知模块</li>
 * </ul>
 */
public enum MessageErrorCode {

    /** 对应 Go {@code Success = 200}：RPC 成功回执的业务码。 */
    SUCCESS(200),

    /** 对应 Go {@code Error = 500}：通用失败回执（不作为细粒度错误码使用）。 */
    ERROR(500),

    /** 对应 Go {@code CodeServerBusy = 1015}：服务繁忙 / 兜底码。
     *  {@code BizCodeFromError} 无法识别 err 类型时返回的就是它。 */
    CODE_SERVER_BUSY(1015),

    /** 对应 Go {@code ErrorServerCommon = 5001}：系统内部错误。 */
    ERROR_SERVER_COMMON(5001),

    /** 对应 Go {@code ErrorNotFound = 5002}：记录不存在。 */
    ERROR_NOT_FOUND(5002),

    /** 对应 Go {@code ErrorInvalidParam = 5003}：参数错误。
     *  gRPC code {@code InvalidArgument} 的默认映射目标。 */
    ERROR_INVALID_PARAM(5003),

    /** 对应 Go {@code ErrorUnauthorized = 5004}：未鉴权。
     *  gRPC code {@code Unauthenticated} 的默认映射目标。 */
    ERROR_UNAUTHORIZED(5004),

    /** 对应 Go {@code ErrorForbidden = 5005}：无权限。
     *  gRPC code {@code PermissionDenied} 的默认映射目标。 */
    ERROR_FORBIDDEN(5005),

    /** 对应 Go {@code ErrorAlreadyExist = 5006}：记录已存在。
     *  gRPC code {@code AlreadyExists} 的默认映射目标。 */
    ERROR_ALREADY_EXIST(5006),

    /** 对应 Go {@code ErrorDbSelect = 5007}：数据库查询失败。 */
    ERROR_DB_SELECT(5007),

    /** 对应 Go {@code ErrorDbUpdate = 5008}：数据库更新失败。 */
    ERROR_DB_UPDATE(5008),

    /** 对应 Go {@code ErrorUserNotFound = 5101}：用户不存在。 */
    ERROR_USER_NOT_FOUND(5101),

    /** 对应 Go {@code ErrorAdminNotFound = 5102}：管理员不存在。 */
    ERROR_ADMIN_NOT_FOUND(5102),

    /** 对应 Go {@code ErrorSelfChat = 5201}：不允许和自己发起会话。 */
    ERROR_SELF_CHAT(5201),

    /** 对应 Go {@code ErrorConversationMiss = 5202}：会话不存在。 */
    ERROR_CONVERSATION_MISS(5202),

    /** 对应 Go {@code ErrorNotificationMiss = 5203}：通知不存在。 */
    ERROR_NOTIFICATION_MISS(5203),

    /** 对应 Go {@code ErrorChatPending = 5204}：会话处于 PENDING，等待对方回复。 */
    ERROR_CHAT_PENDING(5204),

    /** 对应 Go {@code ErrorAdminOnlyInit = 5205}：只有管理员能初始化该会话。 */
    ERROR_ADMIN_ONLY_INIT(5205),

    /** 对应 Go {@code ErrorFirstMsgTooLong = 5206}：首条私聊消息不能超过 50 字符。 */
    ERROR_FIRST_MSG_TOO_LONG(5206),

    /** 对应 Go {@code ErrorUnsupportedRole = 5207}：不支持的发送者角色。 */
    ERROR_UNSUPPORTED_ROLE(5207);

    private final int code;

    MessageErrorCode(int code) {
        this.code = code;
    }

    /**
     * 对应 Go 端把每个常量当 int 传递的契约——
     * Logic 层会把这个 int 写进响应 envelope 的 {@code code} 字段、
     * 或者传给 logger 的 {@code bizCode} 参数。
     */
    public int getCode() {
        return code;
    }
}