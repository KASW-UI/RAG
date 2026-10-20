package com.rag.message.message.rpc.exception;

/**
 * message-rpc 业务异常聚合 —— 对应镜像 Go 端没有显式异常类（GORM 错误直接走 err 透传），
 * Java 端把 data_model 调用失败统一包成 unchecked exception，方便上层 {@link com.rag.message.message.rpc.service.MessageDomainService}
 * 用 try/finally 包装 observeDbError。
 *
 * <p>使用场景：
 * <ul>
 *   <li>{@link DbSelectException}：SELECT 失败（找不到 / SQL 错 / 连接错）</li>
 *   <li>{@link DbUpdateException}：INSERT / UPDATE 失败</li>
 *   <li>{@link TransactionRollbackException}：事务回滚后需要上抛业务错误</li>
 *   <li>{@link DatabaseException}：兜底父类</li>
 * </ul>
 */
public final class MessageExceptions {

    private MessageExceptions() {
        // utility
    }

    /**
     * 数据库 SELECT 失败 —— 对应 Go 端 sql.ErrNoRows 之外的 error。
     * 调用方在 catch 后 observeDbError(action, type) 再 throw。
     */
    public static class DbSelectException extends RuntimeException {
        public DbSelectException(String msg) { super(msg); }
        public DbSelectException(String msg, Throwable cause) { super(msg, cause); }
    }

    /**
     * 数据库 INSERT / UPDATE 失败。
     */
    public static class DbUpdateException extends RuntimeException {
        public DbUpdateException(String msg) { super(msg); }
        public DbUpdateException(String msg, Throwable cause) { super(msg, cause); }
    }

    /**
     * 事务回滚异常 —— 当事务中抛出业务错误、需要让外层感知时使用。
     */
    public static class TransactionRollbackException extends RuntimeException {
        public TransactionRollbackException(String msg) { super(msg); }
        public TransactionRollbackException(String msg, Throwable cause) { super(msg, cause); }
    }

    /**
     * 兜底父类 —— 在 catch (Exception) 路径上转抛。
     */
    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String msg) { super(msg); }
        public DatabaseException(String msg, Throwable cause) { super(msg, cause); }
    }
}