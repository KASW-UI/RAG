package com.rag.users.admin.rpc.internal.model;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * DataModel 抛出的"行不存在"哨兵异常 + 唯一冲突识别工具。
 *
 * 对应 Go 端 internal/model/vars.go + internal/model/helper.go：
 *   ErrorNotFound       = errors.New("record not found")
 *   IsUniqueViolation(err) — 检查 Postgres SQLState 23505
 *
 * Go 用 sentinel error + == 比较；Java 用异常子类 + instanceof 替换。
 * Logic 层捕获后映射到 gRPC code（NotFound / AlreadyExists 等）。
 *
 * 用 nested static class 而不是各自独立 .java 文件，是为了在不动文件树的前提下
 * 容纳两个异常类（Java 不允许多个 public class 共享文件）。
 * 与 user-rpc 端 UserExceptions.java 的组织方式一致。
 */
public final class AdminExceptions {

    private AdminExceptions() {}

    /**
     * 对应 Go 端 ErrorNotFound。
     * DataModel 查不到 Admin / AdminInvite / User 任一行时抛。
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 对应 Go 端 IsUniqueViolation —— Postgres SQLState 23505 识别。
     *
     * Go 端在 CreateAdminLogic.CreateAdmin 用 IsUniqueViolation 区分"用户名已存在"vs 其他 DB 错误，
     * 并在 servicecontext.ensureDefaultAdmin 用 IsUniqueViolation 兜底多实例并发启动时的 unique 冲突。
     * Java 侧由 Hibernate 把 unique violation 包装成 DataIntegrityViolationException，其 SQLState
     * 以 "23" 开头（23505 = unique_violation，23503 = foreign_key_violation，23514 = check_violation）。
     * CreateAdminLogic 用此方法做"用户名已存在"识别。
     */
    public static boolean isUniqueViolation(Throwable err) {
        if (err == null) {
            return false;
        }
        // Hibernate 通常把 SQLState 放在 DataIntegrityViolationException.getCause() 的 SQLException 上
        Throwable cur = err;
        while (cur != null) {
            if (cur instanceof java.sql.SQLException) {
                String state = ((java.sql.SQLException) cur).getSQLState();
                if (state != null && state.equals("23505")) {
                    return true;
                }
            }
            if (cur instanceof DataIntegrityViolationException) {
                // 继续向下找 SQLException
            }
            cur = cur.getCause();
        }
        return false;
    }
}