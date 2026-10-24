package com.rag.like.like.common.errmsg;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * like 域 code → msg 查表 —— 1:1 对应 Go 端
 * {@code service/like/common/errmsg/errmsg.go:69-99}（{@code var codeMsg}）
 * 与 {@code errmsg.go:101-107}（{@code func GetErrMsg}）。
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
 * like-api 任何拿到未知码的分支最终都会把 {@code "FAIL"} 呈现给用户。
 * 所以 {@link #getMsg(int)} 必须回退，<b>不能</b>返回 {@code null} 或空串。
 *
 * <p><b>键类型说明</b>：Go 用 {@code map[int]string}；这里用 {@code Map<Integer, String>}，
 * 写入顺序按 Go {@code codeMsg} 的书写顺序（{@link LinkedHashMap}），
 * 便于 review 时与 Go 源码逐行对照。
 */
public final class LikeErrorMessages {

    private LikeErrorMessages() {
        // 工具类，禁止 new
    }

    /** 对应 Go {@code codeMsg} 整张表（29 条）；对外只读。 */
    public static final Map<Integer, String> MSG_BY_CODE;

    static {
        Map<Integer, String> map = new LinkedHashMap<>();
        // === 通用（Go errmsg.go:38-40）===
        map.put(LikeErrorCode.SUCCESS.getCode(), "OK");
        map.put(LikeErrorCode.ERROR.getCode(), "FAIL");
        map.put(LikeErrorCode.CODE_SERVER_BUSY.getCode(), "服务繁忙");
        // === 系统与基础设施（Go errmsg.go:41-52）===
        map.put(LikeErrorCode.ERROR_SERVER_COMMON.getCode(), "系统内部错误");
        map.put(LikeErrorCode.ERROR_DB_UPDATE.getCode(), "更新数据库失败");
        map.put(LikeErrorCode.ERROR_DB_SELECT.getCode(), "查询数据库失败");
        map.put(LikeErrorCode.ERROR_DB_INSERT.getCode(), "数据库插入失败");
        map.put(LikeErrorCode.ERROR_DB_TRANSACTION.getCode(), "事务操作失败");
        map.put(LikeErrorCode.ERROR_SNOWFLAKE_ID.getCode(), "雪花ID生成失败");
        map.put(LikeErrorCode.ERROR_JSON_MARSHAL.getCode(), "JSON序列化失败");
        map.put(LikeErrorCode.ERROR_JSON_UNMARSHAL.getCode(), "JSON反序列化失败");
        map.put(LikeErrorCode.ERROR_KAFKA_PUSH.getCode(), "Kafka消息发送失败");
        map.put(LikeErrorCode.ERROR_DELAY_MSG.getCode(), "延时消息发送失败");
        map.put(LikeErrorCode.ERROR_REDIS_SELECT.getCode(), "Redis查询失败");
        map.put(LikeErrorCode.ERROR_REDIS_UPDATE.getCode(), "Redis更新失败");
        // === 用户 / 鉴权（Go errmsg.go:53-63）===
        map.put(LikeErrorCode.ERROR_USER_EXIST.getCode(), "用户名已存在");
        map.put(LikeErrorCode.ERROR_LOGIN_WRONG.getCode(), "用户名或密码错误");
        map.put(LikeErrorCode.ERROR_USER_NOT_EXIST.getCode(), "用户不存在");
        map.put(LikeErrorCode.ERROR_TOKEN_NOT_EXIST.getCode(), "TOKEN不存在");
        map.put(LikeErrorCode.ERROR_TOKEN_TYPE_WRONG.getCode(), "TOKEN格式错误");
        map.put(LikeErrorCode.ERROR_TOKEN_RUNTIME.getCode(), "TOKEN已过期");
        map.put(LikeErrorCode.ERROR_TOKEN_REFRESH.getCode(), "TOKEN刷新失败");
        map.put(LikeErrorCode.ERROR_USER_NO_RIGHT.getCode(), "权限不足");
        map.put(LikeErrorCode.ERROR_USER_NO_LOGIN.getCode(), "未登录");
        map.put(LikeErrorCode.ERROR_USER_LOGINED.getCode(), "已登录");
        map.put(LikeErrorCode.ERROR_USER_BANNED.getCode(), "用户已被封禁");
        // === 入参 / 类型（Go errmsg.go:64-65）===
        map.put(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), "输入有误");
        map.put(LikeErrorCode.ERROR_TYPE_TRANSFER.getCode(), "类型转换错误");
        // === Outbox（Go errmsg.go:66）===
        map.put(LikeErrorCode.ERROR_BUILD_OUTBOX.getCode(), "构建 Outbox 消息载荷失败");
        MSG_BY_CODE = Collections.unmodifiableMap(map);
    }

    /**
     * 对应 Go {@code func GetErrMsg(code int) string}（{@code errmsg.go:101-107}）。
     *
     * <p>未知码回退 {@link LikeErrorCode#ERROR} 的 {@code "FAIL"}，
     * 与 Go {@code if !ok { return codeMsg[Error] }} 同向。
     */
    public static String getMsg(int code) {
        String msg = MSG_BY_CODE.get(code);
        if (msg != null) {
            return msg;
        }
        return MSG_BY_CODE.get(LikeErrorCode.ERROR.getCode());
    }
}