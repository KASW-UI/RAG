package com.rag.user.common.errmsg;

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

    ErrorCode(int code, String msg) { this.code = code; this.msg = msg; }

    public int getCode() { return code; }
    public String getMsg() { return msg; }

    public static String getMsg(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) return ec.msg;
        }
        return ERROR.msg;
    }

    public static ErrorCode of(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) return ec;
        }
        return null;
    }
}