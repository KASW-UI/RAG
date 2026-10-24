package com.rag.users.admin.rpc.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * admin-rpc 业务 sentinel 异常 + gRPC Status 映射工具。
 *
 * <p>对应 Go 端 internal/model/vars.go + internal/model/helper.go：
 * <pre>
 *   ErrorNotFound       = errors.New("record not found")
 *   IsUniqueViolation(err) — 检查 Postgres SQLState 23505
 * </pre>
 *
 * <p>Go 用 sentinel error + {@code ==} 比较；Java 用异常子类 + {@code instanceof} 替换。
 * Logic 层捕获后映射到 gRPC {@link StatusRuntimeException}（NOT_FOUND / ALREADY_EXISTS / INTERNAL）。
 *
 * <p>{@code @GrpcService} 入口（AdminServiceGrpcImpl.invoke）统一捕获后透传，
 * 任何非 {@link StatusRuntimeException} 兜底成 {@code Status.INTERNAL}。
 *
 * <p>与 user-rpc 端 UserExceptions.java 同款组织（nested static class + 一个工具方法）。
 */
public final class AdminExceptions {

    private AdminExceptions() {}

    /**
     * 对应 Go 端 ErrorNotFound。
     * Mapper / LambdaQueryWrapper 查不到 Admin / AdminInvite / User 任一行时抛。
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 内部异常：邀请码无效（不存在 / 已用 / 已过期）。
     * Logic 层捕获后转 {@link Status#PERMISSION_DENIED}。
     */
    public static class InviteUnavailableException extends RuntimeException {
        public InviteUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * 内部异常：用户名已被占用。
     * Logic 层捕获后转 {@link Status#ALREADY_EXISTS}。
     */
    public static class UsernameTakenException extends RuntimeException {
        public UsernameTakenException(String message) {
            super(message);
        }
    }

    /**
     * 对应 Go 端 IsUniqueViolation —— Postgres SQLState 23505 识别。
     *
     * <p>Go 端在 CreateAdminLogic.CreateAdmin 用 IsUniqueViolation 区分"用户名已存在"vs 其他 DB 错误，
     * 并在 AdminSeederBootstrap 用 IsUniqueViolation 兜底多实例并发启动时的 unique 冲突。
     * Java 侧由 MyBatis / Hibernate 把 unique violation 包装成 {@link DataIntegrityViolationException}，
     * 其 SQLState 以 "23" 开头（23505 = unique_violation，23503 = foreign_key_violation，23514 = check_violation）。
     */
    public static boolean isUniqueViolation(Throwable err) {
        if (err == null) {
            return false;
        }
        Throwable cur = err;
        while (cur != null) {
            if (cur instanceof java.sql.SQLException) {
                String state = ((java.sql.SQLException) cur).getSQLState();
                if (state != null && state.equals("23505")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }
}
