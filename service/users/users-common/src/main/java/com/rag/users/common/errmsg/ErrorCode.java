package com.rag.users.common.errmsg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum ErrorCode {
    SUCCESS(200, "OK"),
    ERROR(500, "FAIL"),
    CODE_SERVER_BUSY(1015, "服务繁忙"),
    ERROR_SERVER_COMMON(5001, "系统内部错误"),
    ERROR_DB_UPDATE(5002, "更新数据库失败"),
    ERROR_DB_SELECT(5003, "查询数据库失败"),
    ERROR_DB_INSERT(5004, "数据库插入失败"),
    ERROR_DB_TRANSACTION(5005, "事务操作失败"),
    ERROR_SNOWFLAKE_ID(5006, "雪花ID生成失败"),
    ERROR_JSON_MARSHAL(5007, "JSON序列化失败"),
    ERROR_JSON_UNMARSHAL(5008, "JSON反序列化失败"),
    ERROR_KAFKA_PUSH(5009, "Kafka消息发送失败"),
    ERROR_DELAY_MSG(5010, "延时消息发送失败"),
    ERROR_USER_EXIST(1001, "用户名已存在"),
    ERROR_LOGIN_WRONG(1002, "用户名或密码错误"),
    ERROR_USER_NOT_EXIST(1003, "用户不存在"),
    ERROR_TOKEN_NOT_EXIST(1004, "TOKEN不存在"),
    ERROR_TOKEN_TYPE_WRONG(1005, "TOKEN格式错误"),
    ERROR_TOKEN_RUNTIME(1006, "TOKEN已过期"),
    ERROR_TOKEN_REFRESH(1007, "TOKEN刷新失败"),
    ERROR_USER_NO_RIGHT(1008, "权限不足"),
    ERROR_USER_NO_LOGIN(1009, "未登录"),
    ERROR_USER_LOGINED(1010, "已登录"),
    ERROR_USER_BANNED(1011, "用户已被封禁"),
    ERROR_REDIS_UPDATE(1012, "Redis更新失败"),
    ERROR_ADMIN_INVITE_CODE_WRONG(1016, "管理员邀请码错误"),
    ERROR_REQUEST_PARAM(1017, "请求参数错误"),
    ERROR_AVATAR_HISTORY_NOT_EXIST(1018, "头像历史不存在"),
    ERROR_POINTS_INSUFFICIENT(3001, "积分余量不足"),
    ERROR_POINTS_RETRY_EXCEEDED(3002, "重试次数超限"),
    ERROR_POINTS_TIMEOUT(3003, "积分处理超时");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code; this.msg = msg;
    }

    /**
     * 返回枚举对应的业务码 int 值。
     *
     * <p>对应 Go 端 {@code errmsg} 包用 int 传递错误码的契约；
     * 用户侧（如 {@code LoginLogic}、{@code LogicResult}）需要把 enum 还原成 int 写入 envelope.code /
     * 传给 {@code Logger.logBusinessErr(ctx, code, err)}。
     *
     * <p>补这个 getter 的原因：user-api / admin-api 的 Logic 已经按 {@code ErrorCode.X.getCode()}
     * 写了，但 ErrorCode 原本只暴露了 {@code getMsg(int)} 静态方法，{@code code} 字段是 private 没有
     * getter —— 导致所有 Logic 编译不过。这个方法是 admin-api / user-api 编译通过的最小必要补丁。
     */
    public int getCode() {
        return code;
    }

    /** code -> ErrorCode 缓存，避免每次 values() 新建数组、O(n) 遍历 */
    private static final Map<Integer, ErrorCode> CODE_MAP;

    static {
        /* 这里是因为是 ‘错误码 → 提示语’ 用HashMap
            如果是EnumMap，就是‘提示语 → 错误码’ */
        Map<Integer, ErrorCode> map = new HashMap<>();

        for (ErrorCode ec : values()) {
            map.put(ec.code, ec);
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    /** 根据 code 找枚举，找不到返回 ERROR（不再返回 null） */
    public static ErrorCode of(int code) {
        return CODE_MAP.getOrDefault(code, ERROR);
    }

    /** 根据 code 找提示，找不到返回默认提示 */
    public static String getMsg(int code) {
        return of(code).msg;
    }
}