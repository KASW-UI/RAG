package com.rag.users.user.rpc.exception;

/**
 * user-rpc 业务异常 sentinel —— @GrpcService 入口统一捕获后映射成 io.grpc.Status。
 *
 * 对应 Go: service/user/model/vars.go 的 sentinel error：
 *   ErrorNotFound              = errors.New("record not found")
 *   ErrorAvatarHistoryNotFound = errors.New("avatar history not found")
 *
 * Go 用 sentinel error + == 比较；Java 没有这个语义，用异常子类 + instanceof 替换。
 * Logic 层捕获后映射到 gRPC code（NotFound / AlreadyExists 等）。
 *
 * 框架职责:
 *   - 业务异常独立 package（不在 model/ 下）—— 体现"异常是跨切关注点，不是数据模型"
 *   - 取代了原 internal.model.UserExceptions（搬到了 exception 包）
 *   - @GrpcService 入口会捕获后映射成 Status.NOT_FOUND / Status.INVALID_ARGUMENT 等
 *   - private 构造器禁止实例化（只能抛出，不能 new 当数据用）
 */
public class UserExceptions {

    private UserExceptions() {}

    /** 对应 Go 端 ErrorNotFound：DataModel 查不到 user 时抛。 */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    /** 对应 Go 端 ErrorAvatarHistoryNotFound：SelectAvatar 查不到 history 时抛。 */
    public static class AvatarHistoryNotFoundException extends RuntimeException {
        public AvatarHistoryNotFoundException(String message) { super(message); }
    }

    /** 对应 Go 端 password 不匹配抛 ALREADY_EXISTS 的场景（Register 时密码生成失败 / Login 时密码错）。 */
    public static class PasswordMismatchException extends RuntimeException {
        public PasswordMismatchException(String message) { super(message); }
    }

    /** 对应 Go 端 RegisterLogic W1：用户名已存在 → Status.ALREADY_EXISTS。 */
    public static class UsernameExistsException extends RuntimeException {
        public UsernameExistsException(String message) { super(message); }
    }

    /** 对应 Go 端 LogoutLogic：用户 token 在黑名单中 → Status.PERMISSION_DENIED。 */
    public static class TokenBlacklistedException extends RuntimeException {
        public TokenBlacklistedException(String message) { super(message); }
    }
}