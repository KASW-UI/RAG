package com.rag.comment.comment.common.errmsg;

/**
 * comment 域业务错误码 —— 1:1 对应 Go 端
 * {@code service/comment/common/errmsg/errmsg.go:37-65} 的 const 块。
 *
 * <p><b>枚举顺序即契约</b>：声明顺序与 Go 常量块的书写顺序逐条一致，
 * 由 {@code CommentErrorCodeContractTest#everyCodeMatchesGoOneByOne} 锁死。
 * 调整顺序会让该断言失败，从而拦住「无意中的重排」。
 *
 * <p><b>这些数值是跨服务契约</b>：Go comment-api 侧
 * {@code service/comment/api/internal/logic/comment/createcommentlogic.go:52-55} 做的是
 * <pre>
 * st, ok := status.FromError(err)
 * if ok { return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message()) }
 * </pre>
 * 即 comment-rpc 通过 gRPC 传回的码会被<b>原样</b>塞进 HTTP envelope 的 {@code code} 字段。
 * 改任何一个数值都会让已发布的客户端解出错误的业务码。
 *
 * <p>⚠️ 共 <b>27</b> 个。骨架注释与设计文档若写「28 / 29 个」一律以本类的
 * {@code values().length} 与 Go 源码为准（{@code CommentErrorCodeContractTest#thereAreTwentySevenCodes}）。
 *
 * <p><b>与 like / message 域的差异（不要互相"对齐"）</b>：本表末尾多出两个 comment 专属码
 * {@link #ERROR_SUBJECT_NOT_EXIST}(1012) 与 {@link #ERROR_COMMENT_NOT_EXIST}(1013)，
 * 且<b>没有</b> like 域的 {@code ErrorRedisSelect}/{@code ErrorRedisUpdate}、
 * {@code ErrorTypeTransfer}、{@code ErrorBuildOutbox}。
 * 三张表同名前缀但内容不同，任何「统一去重」都会改掉 wire 上的码值。
 */
public enum CommentErrorCode {

    // ===== 通用（Go errmsg.go:38-40）=====
    /** Go {@code Success = 200}。同时是 {@code commentservice.go:45-50} httpx OkHandler 的 envelope code。 */
    SUCCESS(200),
    /** Go {@code Error = 500}。同时是 {@link CommentErrorMessages#getMsg(int)} 未知码的兜底文案 {@code "FAIL"}。 */
    ERROR(500),
    /** Go {@code CodeServerBusy = 1015}。 */
    CODE_SERVER_BUSY(1015),

    // ===== 系统与基础设施（Go errmsg.go:41-50）=====
    /** Go {@code ErrorServerCommon = 5001}。comment-api 全部 logic 的<b>兜底码</b>
     *  （{@code createcommentlogic.go:56-58} 等 6 处在非 status 错误时走这里）。 */
    ERROR_SERVER_COMMON(5001),
    /** Go {@code ErrorDbUpdate = 5002}。comment-rpc 写路径 DB 失败（管理/点赞/删除/改主体状态）。 */
    ERROR_DB_UPDATE(5002),
    /** Go {@code ErrorDbSelect = 5003}。comment-rpc 读路径 DB 回源失败。 */
    ERROR_DB_SELECT(5003),
    /** Go {@code ErrorDbInsert = 5004}。举报落库失败（{@code report_comment_logic.go:91}）。 */
    ERROR_DB_INSERT(5004),
    /** Go {@code ErrorDbTransaction = 5005}。 */
    ERROR_DB_TRANSACTION(5005),
    /** Go {@code ErrorSnowflakeID = 5006}。建评论时雪花算法生成 ID 失败
     *  （{@code create_comment_logic.go:98}）。 */
    ERROR_SNOWFLAKE_ID(5006),
    /** Go {@code ErrorJsonMarshal = 5007}。事件载荷序列化失败（{@code create_comment_logic.go:122}）。 */
    ERROR_JSON_MARSHAL(5007),
    /** Go {@code ErrorJsonUnmarshal = 5008}。 */
    ERROR_JSON_UNMARSHAL(5008),
    /** Go {@code ErrorKafkaPush = 5009}。事实投递事件入 Kafka 失败（{@code create_comment_logic.go:130}）。 */
    ERROR_KAFKA_PUSH(5009),
    /** Go {@code ErrorDelayMsg = 5010}。 */
    ERROR_DELAY_MSG(5010),

    // ===== 用户 / 令牌（Go errmsg.go:51-61）=====
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
    /** Go {@code ErrorTokenRuntime = 1006}。comment-api 的 JWT 拦截器校验失败时使用
     *  （见 {@code api/internal/logic/comment/*} 的 errmsg.ErrorTokenRuntime 调用点）。 */
    ERROR_TOKEN_RUNTIME(1006),
    /** Go {@code ErrorTokenRefresh = 1007}。 */
    ERROR_TOKEN_REFRESH(1007),
    /** Go {@code ErrorUserNoRight = 1008}。<b>comment 域高频码</b>：非 up 主无法评论
     *  （{@code update_subject_state_logic.go:88}）、无权删除
     *  （{@code delete_comment_logic.go:100} 与 {@code :113}）。 */
    ERROR_USER_NO_RIGHT(1008),
    /** Go {@code ErrorUserNoLogin = 1009}。 */
    ERROR_USER_NO_LOGIN(1009),
    /** Go {@code ErrorUserLogined = 1010}。 */
    ERROR_USER_LOGINED(1010),
    /** Go {@code ErrorUserBanned = 1011}。 */
    ERROR_USER_BANNED(1011),

    // ===== comment 专属（Go errmsg.go:62-63）=====
    /** Go {@code ErrorSubjectNotExist = 1012}。评论区不存在。本域出现频次最高的码之一，
     *  覆盖建评论 / 点赞 / 删除 / 改主体状态四条链路。 */
    ERROR_SUBJECT_NOT_EXIST(1012),
    /** Go {@code ErrorCommentNotExist = 1013}。评论不存在。
     *  ⚠️ <b>切勿与 {@link #ERROR_SUBJECT_NOT_EXIST} 合并</b>：前者是评论行缺失，
     *  后者是评论区（subject）缺失，Go 侧两者是独立文案与独立码。 */
    ERROR_COMMENT_NOT_EXIST(1013),

    // ===== 输入校验（Go errmsg.go:64）=====
    /** Go {@code ErrorInputWrong = 2001}。comment 域最高频的码：所有入参非空校验分支
     *  （{@code create_comment_logic.go:66,73}、{@code delete_comment_logic.go:61}、
     *  {@code like_comment_logic.go:48}、{@code manage_comment_logic.go:62,84}、
     *  {@code report_comment_logic.go:64}、{@code update_subject_state_logic.go:63}）。 */
    ERROR_INPUT_WRONG(2001);

    private final int code;

    CommentErrorCode(int code) {
        this.code = code;
    }

    /** 该枚举对应的 wire 数值（跨服务契约，改动即破坏兼容）。 */
    public int getCode() {
        return code;
    }
}