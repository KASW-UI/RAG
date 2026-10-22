package com.rag.like.like.common.errmsg;

/**
 * like 域业务错误码 —— 1:1 对应 Go 端 {@code service/like/common/errmsg/errmsg.go:37-67} 的 const 块。
 *
 * <p><b>枚举顺序即契约</b>：声明顺序与 Go 常量块、文案表 {@code errmsg.go:69-99} 的书写顺序逐条一致，
 * 由 {@code LikeErrorContractTest#everyCodeMatchesGoOneByOne} 锁死。
 * 调整顺序会让该断言失败，从而拦住「无意中的重排」。
 *
 * <p><b>这些数值是跨服务契约</b>：Go like-api 侧 {@code likeactionlogic.go:63-65} 做的是
 * {@code errmsg.NewErrCodeMsg(int(st.Code()), st.Message())}，
 * 即 RPC 返回的码会被<b>原样</b>塞进 HTTP envelope 的 {@code code} 字段。
 * 改任何一个数值都会让已发布的客户端解出错误的业务码。
 *
 * <p>⚠️ 共 <b>29</b> 个。设计文档与骨架注释写的「30 个」是笔误，
 * {@code LikeErrorContractTest#thereAreTwentyNineCodes} 已按 Go 源码钉死。
 */
public enum LikeErrorCode {

    // ===== 通用（Go errmsg.go:38-40）=====
    /** Go {@code Success = 200}。 */
    SUCCESS(200),
    /** Go {@code Error = 500}。同时是 {@link LikeErrorMessages#getMsg(int)} 未知码的兜底文案 {@code "FAIL"}。 */
    ERROR(500),
    /** Go {@code CodeServerBusy = 1015}。也是本域 RPC 侧无法归类时的默认兜底码。 */
    CODE_SERVER_BUSY(1015),

    // ===== 系统与基础设施（Go errmsg.go:41-51）=====
    /** Go {@code ErrorServerCommon = 5001}。 */
    ERROR_SERVER_COMMON(5001),
    /** Go {@code ErrorDbUpdate = 5002}。 */
    ERROR_DB_UPDATE(5002),
    /** Go {@code ErrorDbSelect = 5003}。like 域读路径 DB 回源失败的唯一码。 */
    ERROR_DB_SELECT(5003),
    /** Go {@code ErrorDbInsert = 5004}。 */
    ERROR_DB_INSERT(5004),
    /** Go {@code ErrorDbTransaction = 5005}。 */
    ERROR_DB_TRANSACTION(5005),
    /** Go {@code ErrorSnowflakeID = 5006}。 */
    ERROR_SNOWFLAKE_ID(5006),
    /** Go {@code ErrorJsonMarshal = 5007}。 */
    ERROR_JSON_MARSHAL(5007),
    /** Go {@code ErrorJsonUnmarshal = 5008}。 */
    ERROR_JSON_UNMARSHAL(5008),
    /** Go {@code ErrorKafkaPush = 5009}。 */
    ERROR_KAFKA_PUSH(5009),
    /** Go {@code ErrorDelayMsg = 5010}。 */
    ERROR_DELAY_MSG(5010),
    /** Go {@code ErrorRedisSelect = 5011}。 */
    ERROR_REDIS_SELECT(5011),
    /** Go {@code ErrorRedisUpdate = 5012}。 */
    ERROR_REDIS_UPDATE(5012),

    // ===== 用户 / 令牌（Go errmsg.go:53-62）=====
    /** Go {@code ErrorUserExist = 1001}。 */
    ERROR_USER_EXIST(1001),
    /** Go {@code ErrorLoginWrong = 1002}。 */
    ERROR_LOGIN_WRONG(1002),
    /** Go {@code ErrorUserNotExist = 1003}。 */
    ERROR_USER_NOT_EXIST(1003),
    /** Go {@code ErrorTokenNotExist = 1004}。 */
    ERROR_TOKEN_NOT_EXIST(1004),
    /** Go {@code ErrorTokenTypeWrong = 1005}。 */
    ERROR_TOKEN_TYPE_WRONG(1005),
    /** Go {@code ErrorTokenRuntime = 1006}。 */
    ERROR_TOKEN_RUNTIME(1006),
    /** Go {@code ErrorTokenRefresh = 1007}。 */
    ERROR_TOKEN_REFRESH(1007),
    /** Go {@code ErrorUserNoRight = 1008}。 */
    ERROR_USER_NO_RIGHT(1008),
    /** Go {@code ErrorUserNoLogin = 1009}。 */
    ERROR_USER_NO_LOGIN(1009),
    /** Go {@code ErrorUserLogined = 1010}。 */
    ERROR_USER_LOGINED(1010),
    /** Go {@code ErrorUserBanned = 1011}。 */
    ERROR_USER_BANNED(1011),

    // ===== 输入与类型（Go errmsg.go:63-64）=====
    /** Go {@code ErrorInputWrong = 2001}。like 域最常返回的码：参数校验失败。 */
    ERROR_INPUT_WRONG(2001),
    /** Go {@code ErrorTypeTransfer = 2002}。 */
    ERROR_TYPE_TRANSFER(2002),

    // ===== Outbox（Go errmsg.go:65）=====
    /** Go {@code ErrorBuildOutbox = 3001}。 */
    ERROR_BUILD_OUTBOX(3001);

    private final int code;

    LikeErrorCode(int code) {
        this.code = code;
    }

    /** 该枚举对应的 wire 数值（跨服务契约，改动即破坏兼容）。 */
    public int getCode() {
        return code;
    }
}