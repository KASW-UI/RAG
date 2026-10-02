package com.rag.user.user.rpc.internal.model;

import io.grpc.Status;
import io.grpc.StatusException;

/**
 * 对应 Go: internal/model/vars.go
 *
 * Go 端的 vars.go 只是一堆 errors.New(...) 的"错误哨兵",错误码和 gRPC status 是 Logic 层手写
 * `status.Error(codes.NotFound, "用户不存在")` 拼出来的。Java 端把"业务错误 → gRPC status"的
 * 映射集中到一个 enum,避免 Logic 层散落 magic number。这是 Java 主流做法,也是面试常考点。
 *
 * 设计要点:
 *   1. enum 自带 gRPC Status 一一对应 → toException() 直接抛 StatusException 给 gRPC Server 序列化
 *   2. 内嵌 BusinessException:Logic 层不直接拼 status,抛这个异常,由 GrpcExceptionHandler 拦截后转 status
 *      (Go 端没有"异常"概念,Java 才有,所以这是 Java 端的工程化包装)
 */
public final class ErrorCode {

    private ErrorCode() {}

    public enum Code {
        /** 对应 Go: model.ErrorNotFound —— GetUser / DeleteUser / UploadAvatar 查不到用户 */
        USER_NOT_FOUND(Status.NOT_FOUND, "用户不存在"),

        /** 对应 Go: registerlogic / updatelogic 重名校验 */
        USERNAME_EXISTS(Status.ALREADY_EXISTS, "用户名已存在"),

        /** 对应 Go: model.ErrorAvatarHistoryNotFound —— SelectAvatarHistory */
        AVATAR_HISTORY_NOT_FOUND(Status.NOT_FOUND, "头像历史不存在"),

        DB_SELECT_ERROR(Status.INTERNAL, "DB查询失败"),
        DB_UPDATE_ERROR(Status.INTERNAL, "DB更新失败"),
        DB_INSERT_ERROR(Status.INTERNAL, "DB插入失败"),

        /** 对应 Go: cryptx.PasswordEncrypt 失败 */
        PASSWORD_ENCRYPT_ERROR(Status.INTERNAL, "密码生成失败"),

        /** 对应 Go: snowflake.GetID() 失败 */
        SNOWFLAKE_ID_ERROR(Status.INTERNAL, "uid生成失败"),

        /** 对应 Go: l.svcCtx.BizRedis.SetexCtx 失败 */
        REDIS_WRITE_ERROR(Status.INTERNAL, "Redis写入异常"),

        /** 对应 Go: LogoutLogic 里 in.Token == "" */
        TOKEN_EMPTY(Status.INVALID_ARGUMENT, "Token不能为空"),

        /** 对应 Go: SelectAvatarLogic 里 historyID == "" */
        HISTORY_ID_EMPTY(Status.INVALID_ARGUMENT, "history_id is required"),

        /** 对应 Go: UploadAvatarLogic 里 avatarURL == "" */
        AVATAR_URL_EMPTY(Status.INVALID_ARGUMENT, "avatar_url is required");

        private final Status grpcStatus;
        private final String message;

        Code(Status grpcStatus, String message) {
            this.grpcStatus = grpcStatus;
            this.message = message;
        }

        public Status grpcStatus() { return grpcStatus; }
        public String message() { return message; }

        /**
         * 直接转 io.grpc.StatusException,gRPC Server 框架会把它落到 wire 上。
         * 适用于不想经过 BusinessException 拦截、直接抛的场景(目前 Logic 都走异常路径)。
         */
        public StatusException toException() {
            return grpcStatus.withDescription(message).asException();
        }
    }

    /**
     * 对应 Go: 业务错误 —— Go 端用 errors.New + status.Error 组合,Java 端统一抛这个异常。
     * 在 UserServiceServer(对应 server/userserviceserver.go)里用 @GrpcExceptionHandler 拦截
     * 后再转 StatusException 返回给 client。
     */
    public static class BusinessException extends RuntimeException {
        private final Code code;

        public BusinessException(Code code) {
            super(code.message);
            this.code = code;
        }

        public BusinessException(Code code, String detail) {
            super(detail);
            this.code = code;
        }

        public Code getCode() {
            return code;
        }
    }
}