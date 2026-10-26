package com.rag.favorite.favorite.common.errmsg;

/**
 * favorite 域业务错误码 —— 1:1 对应 Go 端
 * {@code Sea-RideTheWind_golang/service/favorite/common/errmsg.go:8-41} 的 const 块。
 *
 * <p><b>枚举顺序即契约</b>：声明顺序与 Go 常量块的书写顺序逐条一致（通用 → 通用错误码 5001-5099 →
 * 用户模块 5100-5199 → 收藏夹模块 5200-5299 → 收藏模块 5300-5399），由
 * {@code FavoriteErrorCodeContractTest#declarationOrderMatchesGoSourceOrder} 锁死。
 * 调整顺序会让该断言失败，从而拦住「无意中的重排」——Go 侧 const 块与 {@code codeMsg} 表是分开的，
 * 顺序漂移会让逐行 review 失效。
 *
 * <p><b>这些数值是跨服务契约（L1）</b>，三条消费链路：
 * <ol>
 *   <li>Go favorite-rpc 把 <b>标准</b> gRPC 码放进 {@code grpc-status}，把
 *       {@link FavoriteErrorMessages#getMsg(int)} 查出的文案放进 description；
 *       业务码本身靠文案反查还原，见 {@link FavoriteGrpcError}；</li>
 *   <li>Go favorite-api 的 logic 直接把码当返回值抛出，例如
 *       {@code api/internal/logic/favorite/create_favorite_folder_logic.go:39}
 *       （未登录 → 5004）、{@code api/internal/logic/favorite/create_favorite_folder_logic.go:61}（RPC 失败 → 5001）、
 *       {@code api/internal/logic/favorite/create_favorite_folder_logic.go:65}（成功 → 200）；</li>
 *   <li>Go favorite-api 的 handler 把码与文案原样写进 HTTP envelope 的 {@code code} / {@code msg} 字段，
 *       例如 {@code api/internal/handler/favorite/get_favorite_folders_handler.go:20-21} 与
 *       {@code api/internal/handler/favorite/get_favorite_folders_handler.go:31}。</li>
 * </ol>
 * 任何一个数值被改动，客户端解出的业务码就会错。
 *
 * <p>⚠️ <b>码值与 hot 域高度重叠但含义不同</b>：本域 5002 = {@code "not found"}、
 * 5003 = {@code "参数错误"}、5004 = {@code "未登录或登录已失效"}，
 * 而 hot 域同数值分别是 {@code "record not found"} / {@code "invalid parameter"} /
 * {@code "unauthorized"}。RAG 多个域在同一仓库内演进，按别的域的直觉「顺手修正」本域即为漂移。
 * 由 {@code FavoriteErrorMessagesContractTest} 钉死。
 *
 * <p>共 <b>24</b> 个（Go {@code errmsg.go:9-40} 逐行计数），
 * 由 {@code FavoriteErrorCodeContractTest#thereAreExactlyTwentyFourCodes} 按 Go 源码钉死。
 */
public enum FavoriteErrorCode {

    // ===== 通用（Go errmsg.go:9-10）=====
    /** Go {@code Success = 200}。也是 {@link FavoriteGrpcError#bizCodeFromError} 收到 {@code null} 时的返回值。 */
    SUCCESS(200),
    /** Go {@code Error = 500}。同时是 {@link FavoriteErrorMessages#getMsg(int)} 未知码的兜底文案 {@code "FAIL"}。 */
    ERROR(500),

    // ===== 通用错误码 5001-5099（Go errmsg.go:12-13）=====
    /** Go {@code CodeServerBusy = 1015}。**排在 5001 之前**，因为 Go 源码就写在通用错误码分组的开头。 */
    CODE_SERVER_BUSY(1015),

    // ===== 通用错误码 5001-5099（Go errmsg.go:14-22）=====
    /** Go {@code ErrorServerCommon = 5001}。favorite-api 的 logic 在下游 RPC 失败时返回它。 */
    ERROR_SERVER_COMMON(5001),
    /** Go {@code ErrorNotFound = 5002}。文案是英文 {@code "not found"}，不是中文「记录不存在」。 */
    ERROR_NOT_FOUND(5002),
    /** Go {@code ErrorInvalidParam = 5003}。favorite 域最常返回的码：入参解析失败。 */
    ERROR_INVALID_PARAM(5003),
    /** Go {@code ErrorUnauthorized = 5004}。文案含「或」字：{@code "未登录或登录已失效"}。 */
    ERROR_UNAUTHORIZED(5004),
    /** Go {@code ErrorForbidden = 5005}。收藏归属校验失败时返回（他人收藏夹）。 */
    ERROR_FORBIDDEN(5005),
    /** Go {@code ErrorAlreadyExist = 5006}。收藏夹重名 / 收藏重复。 */
    ERROR_ALREADY_EXIST(5006),
    /** Go {@code ErrorDbSelect = 5007}。读路径 DB 回源失败的唯一码。 */
    ERROR_DB_SELECT(5007),
    /** Go {@code ErrorDbUpdate = 5008}。写路径 DB 失败的唯一码。 */
    ERROR_DB_UPDATE(5008),
    /** Go {@code ErrorGenerateID = 5009}。雪花 ID 生成失败。 */
    ERROR_GENERATE_ID(5009),

    // ===== 用户模块 5100-5199（Go errmsg.go:24-29）=====
    /** Go {@code ErrorUserNotFound = 5101}。 */
    ERROR_USER_NOT_FOUND(5101),
    /** Go {@code ErrorUserExists = 5102}。 */
    ERROR_USER_EXISTS(5102),
    /** Go {@code ErrorPasswordInvalid = 5103}。 */
    ERROR_PASSWORD_INVALID(5103),
    /** Go {@code ErrorTokenInvalid = 5104}。 */
    ERROR_TOKEN_INVALID(5104),
    /** Go {@code ErrorTokenExpired = 5105}。 */
    ERROR_TOKEN_EXPIRED(5105),

    // ===== 收藏夹模块 5200-5299（Go errmsg.go:31-34）=====
    /** Go {@code ErrorFavoriteFolderNotFound = 5201}。 */
    ERROR_FAVORITE_FOLDER_NOT_FOUND(5201),
    /** Go {@code ErrorFavoriteFolderNameEmpty = 5202}。 */
    ERROR_FAVORITE_FOLDER_NAME_EMPTY(5202),
    /** Go {@code ErrorFavoriteFolderNameExists = 5203}。 */
    ERROR_FAVORITE_FOLDER_NAME_EXISTS(5203),

    // ===== 收藏模块 5300-5399（Go errmsg.go:36-40）=====
    /** Go {@code ErrorFavoriteNotFound = 5301}。 */
    ERROR_FAVORITE_NOT_FOUND(5301),
    /** Go {@code ErrorFavoriteAlreadyExist = 5302}。 */
    ERROR_FAVORITE_ALREADY_EXIST(5302),
    /** Go {@code ErrorFavoriteTargetEmpty = 5303}。 */
    ERROR_FAVORITE_TARGET_EMPTY(5303),
    /**
     * Go {@code ErrorFavoriteHistoryBlocked = 5304}。
     *
     * <p><b>这个码决定了整个组件的形状</b>：它<b>不是</b>标准 gRPC 码，
     * Go 侧靠 {@code GRPCError(codes.FailedPrecondition, 5304)} 发出
     * （{@code rpc/internal/logic/delete_favorite_logic.go:86} 与
     * {@code rpc/internal/logic/delete_favorite_folder_logic.go:86}），
     * wire 上只有 {@code grpc-status: FAILED_PRECONDITION} + description {@code "收藏暂不可撤回"}。
     * 而 {@code FAILED_PRECONDITION} <b>不在</b> Go {@code BizCodeFromError} 的 switch 白名单里
     * （{@code errmsg.go:122-136}）⇒ 还原 5304 <b>只能</b>靠 description 的文案反查。
     * Go 端测试把这条钉死（{@code rpc/internal/server/favorite_fact_rpc_test.go:54-55}）。
     */
    ERROR_FAVORITE_HISTORY_BLOCKED(5304);

    private final int code;

    FavoriteErrorCode(int code) {
        this.code = code;
    }

    /** 该枚举对应的 wire 数值（跨服务契约，改动即破坏兼容）。 */
    public int getCode() {
        return code;
    }
}