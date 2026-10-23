package com.rag.follow.follow.common.errmsg;

/**
 * follow 域业务错误码 —— 1:1 对应 Go 端
 * {@code Sea-RideTheWind_golang/service/follow/common/errmsg.go:33-53} 的 const 块。
 *
 * <p><b>枚举顺序即契约</b>：声明顺序与 Go 常量块的书写顺序逐条一致，
 * 由 {@code FollowErrorCodeContractTest#declarationOrderMatchesGoSourceOrder} 钉死。
 *
 * <p><b>这些数值是跨服务契约（L1）**，三条消费链路：
 * <ol>
 *   <li>Go follow-rpc 把<b>标准</b> gRPC 码放进 {@code grpc-status}、把
 *       {@link FollowErrorMessages#getMsg(int)} 查出的文案放进 description，
 *       业务码靠文案反查还原，见 {@link FollowGrpcError}；</li>
 *   <li>Go follow-api 的 logic 直接把码当返回值抛出，例如
 *       {@code api/internal/logic/follow/getrecommendationslogic.go:42}（未登录 → 5004）、
 *       {@code :49}（越权查他人 → 5005）、{@code :58-60}（RPC 失败 → BizCodeFromError 反查）、</li>
 *   <li>Go follow-api 的 {@code followcenter.go:53-66} 把 {@code *CodeError} 的
 *       {@code Code}/{@code Msg} 原样写进 HTTP envelope 的 {@code code}/{@code msg} 字段，
 *       且 <b>HTTP 状态恒 200</b>。</li>
 * </ol>
 * 任何一个数值被改动，客户端解出的业务码就会错。
 *
 * <p>⚠️ <b>码值与 hot 域几乎完全重叠但必须逐域独立核对</b>：follow 域的文案整表英文
 * （{@code "internal server error"} / {@code "invalid parameter"} / {@code "unauthorized"}），
 * 与 favorite 域同码不同义（favorite 的 5001 是 {@code "系统内部错误"}）。
 * RAG 多域同仓演进，按别的域的直觉「顺手修正」本域即为漂移。
 * 由 {@code FollowErrorMessagesContractTest} 逐条钉死。
 *
 * <p>共 <b>16</b> 个（Go {@code errmsg.go:34-52} 逐行计数），
 * 由 {@code FollowErrorCodeContractTest#thereAreExactlySixteenCodes} 按 Go 源码钉死。
 *
 * <p>⚠️ 其中 <b>4 个在 follow 域内没有调用点</b>：{@link #ERROR_NOT_FOUND}(5002)、
 * {@link #ERROR_ALREADY_EXIST}(5006)、{@link #ERROR_FOLLOW_NOT_FOUND}(5203)、
 * {@link #ERROR_BLOCK_NOT_FOUND}(5204)。
 * 全仓 grep 结果为 0 命中（其余 12 个共 87 处命中）。
 * 它们<b>仍必须保留</b>：① 表是跨服务契约的一部分，未知码要回落 {@code "FAIL"}，
 * 删掉会让「本域未用到的码」变成回落而非命中；② {@code BizCodeFromError} 的
 * {@code switch} 白名单会产出 5002 / 5006（见 {@link FollowGrpcError}），
 * 删掉枚举常量会让回落文案无处可查。
 */
public enum FollowErrorCode {

    // ===== 通用（Go errmsg.go:34-35）=====
    /** Go {@code Success = 200}。rpc 成功响应的 {@code BaseResp.code}，也是 {@link FollowGrpcError#bizCodeFromError} 收到 {@code null} 时的返回值。 */
    SUCCESS(200),
    /** Go {@code Error = 500}。同时是 {@link FollowErrorMessages#getMsg(int)} 未知码的兜底文案 {@code "FAIL"}。 */
    ERROR(500),

    // ===== 通用错误码 5001-5099（Go errmsg.go:37-48）=====
    /** Go {@code CodeServerBusy = 1015}。**排在 5001 之前**，因为 Go 源码就写在通用错误码分组的开头。 */
    CODE_SERVER_BUSY(1015),
    /** Go {@code ErrorServerCommon = 5001}。DB 故障 / 用户查询失败的兜底码，也是 api 侧未识别异常时的默认码。 */
    ERROR_SERVER_COMMON(5001),
    /** Go {@code ErrorNotFound = 5002}。⚠️ follow 域内无调用点，但会被 {@link FollowGrpcError} 的 {@code NOT_FOUND} 分支产出。 */
    ERROR_NOT_FOUND(5002),
    /** Go {@code ErrorInvalidParam = 5003}。入参校验失败（{@code rpc/internal/logic/helper.go:29,39}）。 */
    ERROR_INVALID_PARAM(5003),
    /** Go {@code ErrorUnauthorized = 5004}。ctx 里取不到 userId 时由 api 层产出（每个 logic 的第 42 行）。 */
    ERROR_UNAUTHORIZED(5004),
    /** Go {@code ErrorForbidden = 5005}。查询他人数据被拒（{@code getblocklistlogic.go:48-49}、{@code getrecommendationslogic.go:48-49}）。 */
    ERROR_FORBIDDEN(5005),
    /** Go {@code ErrorAlreadyExist = 5006}。⚠️ follow 域内无调用点，但会被 {@link FollowGrpcError} 的 {@code ALREADY_EXISTS} 分支产出。 */
    ERROR_ALREADY_EXIST(5006),
    /** Go {@code ErrorDbSelect = 5007}。读路径 DB 回源失败，follow-rpc 16 处。 */
    ERROR_DB_SELECT(5007),
    /** Go {@code ErrorDbUpdate = 5008}。写路径 DB 失败，follow-rpc 8 处。 */
    ERROR_DB_UPDATE(5008),

    // ===== 用户模块 5100-5199（Go errmsg.go:47）=====
    /** Go {@code ErrorUserNotFound = 5101}。目标用户不存在（{@code rpc/internal/logic/helper.go:86,91}）。 */
    ERROR_USER_NOT_FOUND(5101),

    // ===== 关注域 5200-5299（Go errmsg.go:49-52）=====
    /** Go {@code ErrorSelfRelation = 5201}。自己关注/拉黑自己（{@code rpc/internal/logic/helper.go:32}）。 */
    ERROR_SELF_RELATION(5201),
    /** Go {@code ErrorRelationBlocked = 5202}。存在拉黑关系时拒绝关注（{@code rpc/internal/logic/followlogic.go:55}）。 */
    ERROR_RELATION_BLOCKED(5202),
    /** Go {@code ErrorFollowNotFound = 5203}。⚠️ follow 域内无调用点。 */
    ERROR_FOLLOW_NOT_FOUND(5203),
    /** Go {@code ErrorBlockNotFound = 5204}。⚠️ follow 域内无调用点。 */
    ERROR_BLOCK_NOT_FOUND(5204);

    private final int code;

    FollowErrorCode(int code) {
        this.code = code;
    }

    /** 该枚举对应的 wire 数值（跨服务契约，改动即破坏兼容）。 */
    public int getCode() {
        return code;
    }
}