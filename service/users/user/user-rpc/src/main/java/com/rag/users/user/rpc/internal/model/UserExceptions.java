package com.rag.users.user.rpc.internal.model;

/**
 * DataModel 抛出的"行不存在"哨兵异常。
 *
 * 对应 Go 端 internal/model/vars.go：
 *   ErrorNotFound              = errors.New("record not found")
 *   ErrorAvatarHistoryNotFound = errors.New("avatar history not found")
 *
 * Go 用 sentinel error + == 比较；Java 没有这个语义，用异常子类 + instanceof 替换。
 * Logic 层捕获后映射到 gRPC code（NotFound / AlreadyExists 等）。
 *
 * 用 nested static class 而不是各自独立 .java 文件，是为了在不动文件树的前提下
 * 容纳两个异常类（Java 不允许多个 public class 共享文件）。
 */
public final class UserExceptions {

    private UserExceptions() {}

    /** 对应 Go 端 ErrorNotFound：DataModel 查不到 user 时抛。 */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** 对应 Go 端 ErrorAvatarHistoryNotFound：SelectAvatar 查不到 history 时抛。 */
    public static class AvatarHistoryNotFoundException extends RuntimeException {
        public AvatarHistoryNotFoundException(String message) {
            super(message);
        }
    }
}