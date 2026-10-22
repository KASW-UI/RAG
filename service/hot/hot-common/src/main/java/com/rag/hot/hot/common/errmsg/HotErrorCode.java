package com.rag.hot.hot.common.errmsg;

/**
 * hot 域业务错误码 —— 1:1 对应 Go 端 {@code service/hot/common/errmsg.go:33-46} 的 const 块。
 *
 * <p><b>枚举顺序即契约</b>：声明顺序与 Go 常量块的书写顺序逐条一致，
 * 由 {@code HotErrorCodeContractTest#declarationOrderMatchesGoSourceOrder} 锁死。
 * 调整顺序会让该断言失败，从而拦住「无意中的重排」。
 *
 * <p><b>这些数值是跨服务契约</b>：Go hot-api 侧 {@code hotcenter.go:45-66} 的
 * {@code httpx.SetOkHandler} / {@code httpx.SetErrorHandler} 把它们直接写进 HTTP envelope 的
 * {@code code} 字段，客户端按这个数解业务码。改任何一个数值都会让已发布的客户端解出错误的业务码。
 *
 * <p><b>⚠️ 禁止复用任何其它域的 {@code ErrorCode}</b>：hot 的 5002/5003/5004 与
 * {@code users-common} / {@code article-common} 的同号码<b>语义完全不同且语言不同</b>
 * （hot 是英文，users/article 是中文）：
 * <pre>
 *   码值  hot（本文档）              users / article
 *   5002  record not found          更新数据库失败
 *   5003  invalid parameter         查询数据库失败
 *   5004  unauthorized              数据库插入失败 / 文件上传失败
 * </pre>
 * 三方两两冲突，不存在「取一个通用码表」的选项。
 * 由 {@code HotErrorMessagesContractTest#hotCodesKeepTheirOwnMeaningAgainstOtherDomains} 钉死。
 *
 * <p><b>⚠️ 与 message 域的「巧合相同」不是许可复用</b>：message 域前 11 个码值与文案与本表
 * 逐条相同（两域的 Go errmsg 同源于同一模板），但它们是<b>两个独立域</b>，
 * message 域日后加码或改文案不得波及 hot，反之亦然 ⇒ 各自自持码表。
 *
 * <p><b>对应 Go 什么</b>：Go 里这些都是 {@code const int}；Java 这边用 enum 是为了让编译器在编译期
 * 就拦下拼错或漂移，字节码里仍然是 {@code public static final int}。
 *
 * <p><b>本类只管码，不管文案</b>：文案在 {@link HotErrorMessages}。拆开两者的判据见
 * {@code FileTree/hot/common.md §12.5}（与 message / like 域的共同做法）。
 */
public enum HotErrorCode {

    // ===== 通用（Go errmsg.go:34-37）=====

    /** Go {@code Success = 200}。成功回执的业务码，{@code httpx.SetOkHandler} 写入 envelope 的 code。 */
    SUCCESS(200),

    /** Go {@code Error = 500}。同时是 {@link HotErrorMessages#getMsg(int)} 未知码的兜底文案 {@code "FAIL"}。 */
    ERROR(500),

    /** Go {@code CodeServerBusy = 1015}。服务繁忙 / 兜底码。 */
    CODE_SERVER_BUSY(1015),

    // ===== 系统与基础设施（Go errmsg.go:38-45）=====

    /** Go {@code ErrorServerCommon = 5001}。hot-api 的错误兜底码。 */
    ERROR_SERVER_COMMON(5001),

    /** Go {@code ErrorNotFound = 5002}。记录不存在。 */
    ERROR_NOT_FOUND(5002),

    /** Go {@code ErrorInvalidParam = 5003}。参数非法。 */
    ERROR_INVALID_PARAM(5003),

    /** Go {@code ErrorUnauthorized = 5004}。未认证。 */
    ERROR_UNAUTHORIZED(5004),

    /** Go {@code ErrorForbidden = 5005}。无权限。 */
    ERROR_FORBIDDEN(5005),

    /** Go {@code ErrorAlreadyExist = 5006}。记录已存在。 */
    ERROR_ALREADY_EXIST(5006),

    /** Go {@code ErrorDbSelect = 5007}。数据库查询失败。 */
    ERROR_DB_SELECT(5007),

    /** Go {@code ErrorDbUpdate = 5008}。数据库更新失败。 */
    ERROR_DB_UPDATE(5008);

    private final int code;

    HotErrorCode(int code) {
        this.code = code;
    }

    /** 该枚举对应的 wire 数值（跨服务契约，改动即破坏兼容）。 */
    public int getCode() {
        return code;
    }
}
