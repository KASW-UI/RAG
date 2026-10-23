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

    // ── 2026-10-23 删除：以下三个异常类全仓零 throw 点，且**两个的 javadoc 出处是假的** ──
    //
    // 原类：PasswordMismatchException / UsernameExistsException / TokenBlacklistedException
    //
    // ① PasswordMismatchException 的 javadoc 写「对应 Go 端 password 不匹配抛 ALREADY_EXISTS」——
    //    **Go 恰好相反**，两处都不对：
    //      service/user/user/rpc/internal/logic/loginlogic.go:59-64
    //        correct := cryptx.CheckPassword(user.Password, in.Password)
    //        if !correct { ...; return &pb.LoginResp{Status: 1}, nil }   ← 返回 nil error，不是异常
    //      service/user/user/rpc/internal/logic/registerlogic.go:56-62
    //        truePassword, err := cryptx.PasswordEncrypt(in.Password)
    //        if err != nil { ...; return nil, status.Error(codes.Internal, "密码生成失败") }
    //                                                                    ↑ 是 Internal，不是 AlreadyExists
    //
    // ② TokenBlacklistedException 的 javadoc 写「对应 Go 端 LogoutLogic：token 在黑名单中
    //    → Status.PERMISSION_DENIED」—— **Go 的注销链里根本没有这个码**：
    //      service/user/user/api/internal/logic/user/logoutlogic.go:34-57（全文 58 行）
    //        只判 token := l.ctx.Value("jwt_token"); if token == nil {...}，之后一路直传 rpc
    //      service/user/user/rpc/internal/logic/logoutlogic.go:30-43（全文 44 行）
    //        只判 in.Token == ""，之后直接 Setex 进 Redis
    //    ⇒ PERMISSION_DENIED 在 api + rpc 两侧**均不存在**。
    //
    // ③ UsernameExistsException 的 javadoc 是**准确的**
    //    （registerlogic.go:48 `status.Error(codes.AlreadyExists, "用户名已存在")`），
    //    但 RAG 侧已直接抛 Status.ALREADY_EXISTS，该类同样零消费者。
    //
    // 为什么必须删而不是留着：
    //   §3.2 把「无出处的 idiomatization」列为不可 review。①②比「无出处」更贵 ——
    //   它们**指向了真实的 Go 文件、也真的编造了行号**，下一个 agent 会据此
    //   「恢复」两个 Go 从未有过的错误码，把潜伏的假结论扶正成行为回归。
    //
    // 保留的两个类（NotFoundException / AvatarHistoryNotFoundException）有真实 throw 点，
    // 见 UserDomainService。
}