package com.rag.comment.comment.common.errmsg;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * comment 域 code → msg 查表 —— 1:1 对应 Go 端
 * {@code service/comment/common/errmsg/errmsg.go:67-95}（{@code var codeMsg}）
 * 与 {@code errmsg.go:97-103}（{@code func GetErrMsg}）。
 *
 * <p><b>Go 端行为（必须保留）</b>：
 * <pre>
 * func GetErrMsg(code int) string {
 *     msg, ok := codeMsg[code]
 *     if !ok { return codeMsg[Error] }   // ← 未知码回退到 "FAIL"，不是空串、也不是 code 本身
 *     return msg
 * }
 * </pre>
 * 「未知码返回 {@code "FAIL"}」是客户端可观察的：
 * comment-api 正常路径的 envelope 走 {@code commentservice.go:45-50} 的 OkHandler
 * （{@code Msg: errmsg.GetErrMsg(errmsg.Success)}），错误路径走
 * {@code commentservice.go:51-58} 的 ErrorHandler；后者在错误<b>不是</b> {@code *CodeError}
 * 时会用 {@code errmsg.GetErrMsg(errmsg.ErrorServerCommon)} 兜底。
 * 所以 {@link #getMsg(int)} 必须回退，<b>不能</b>返回 {@code null} 或空串。
 *
 * <p><b>键类型说明</b>：Go 用 {@code map[int]string}；这里用 {@code Map<Integer, String>}，
 * 写入顺序按 Go {@code codeMsg} 的书写顺序（{@link LinkedHashMap}），
 * 便于 review 时与 Go 源码逐行对照。
 */
public final class CommentErrorMessages {

    private CommentErrorMessages() {
        // 工具类，禁止 new
    }

    /** 对应 Go {@code codeMsg} 整张表（27 条）；对外只读。 */
    public static final Map<Integer, String> MSG_BY_CODE;

    static {
        Map<Integer, String> map = new LinkedHashMap<>();
        // === 通用（Go errmsg.go:68-70）===
        map.put(CommentErrorCode.SUCCESS.getCode(), "OK");
        map.put(CommentErrorCode.ERROR.getCode(), "FAIL");
        map.put(CommentErrorCode.CODE_SERVER_BUSY.getCode(), "服务繁忙");
        // === 系统与基础设施（Go errmsg.go:71-80）===
        map.put(CommentErrorCode.ERROR_SERVER_COMMON.getCode(), "系统内部错误");
        map.put(CommentErrorCode.ERROR_DB_UPDATE.getCode(), "更新数据库失败");
        map.put(CommentErrorCode.ERROR_DB_SELECT.getCode(), "查询数据库失败");
        map.put(CommentErrorCode.ERROR_DB_INSERT.getCode(), "数据库插入失败");
        map.put(CommentErrorCode.ERROR_DB_TRANSACTION.getCode(), "事务操作失败");
        map.put(CommentErrorCode.ERROR_SNOWFLAKE_ID.getCode(), "雪花ID生成失败");
        map.put(CommentErrorCode.ERROR_JSON_MARSHAL.getCode(), "JSON序列化失败");
        map.put(CommentErrorCode.ERROR_JSON_UNMARSHAL.getCode(), "JSON反序列化失败");
        map.put(CommentErrorCode.ERROR_KAFKA_PUSH.getCode(), "Kafka消息发送失败");
        map.put(CommentErrorCode.ERROR_DELAY_MSG.getCode(), "延时消息发送失败");
        // === 用户 / 令牌（Go errmsg.go:81-91）===
        map.put(CommentErrorCode.ERROR_USER_EXIST.getCode(), "用户名已存在");
        map.put(CommentErrorCode.ERROR_LOGIN_WRONG.getCode(), "用户名或密码错误");
        map.put(CommentErrorCode.ERROR_USER_NOT_EXIST.getCode(), "用户不存在");
        map.put(CommentErrorCode.ERROR_TOKEN_NOT_EXIST.getCode(), "TOKEN不存在");
        map.put(CommentErrorCode.ERROR_TOKEN_TYPE_WRONG.getCode(), "TOKEN格式错误");
        map.put(CommentErrorCode.ERROR_TOKEN_RUNTIME.getCode(), "TOKEN已过期");
        map.put(CommentErrorCode.ERROR_TOKEN_REFRESH.getCode(), "TOKEN刷新失败");
        map.put(CommentErrorCode.ERROR_USER_NO_RIGHT.getCode(), "权限不足");
        map.put(CommentErrorCode.ERROR_USER_NO_LOGIN.getCode(), "未登录");
        map.put(CommentErrorCode.ERROR_USER_LOGINED.getCode(), "已登录");
        map.put(CommentErrorCode.ERROR_USER_BANNED.getCode(), "用户已被封禁");
        // === comment 专属（Go errmsg.go:92-93）===
        map.put(CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode(), "评论区不存在");
        map.put(CommentErrorCode.ERROR_COMMENT_NOT_EXIST.getCode(), "评论不存在");
        // === 输入校验（Go errmsg.go:94）===
        map.put(CommentErrorCode.ERROR_INPUT_WRONG.getCode(), "输入有误");
        MSG_BY_CODE = Collections.unmodifiableMap(map);
    }

    /**
     * 对应 Go {@code func GetErrMsg(code int) string}（{@code errmsg.go:97-103}）。
     *
     * <p>未知码回退 {@link CommentErrorCode#ERROR} 的 {@code "FAIL"}，
     * 与 Go {@code if !ok { return codeMsg[Error] }} 同向。
     */
    public static String getMsg(int code) {
        String msg = MSG_BY_CODE.get(code);
        if (msg != null) {
            return msg;
        }
        return MSG_BY_CODE.get(CommentErrorCode.ERROR.getCode());
    }
}