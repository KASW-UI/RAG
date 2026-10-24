package com.rag.favorite.favorite.rpc.exception;

import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
import io.grpc.Status;

/**
 * favorite-rpc 异常集合 —— 承载 Go 侧两类完全不同的错误。
 *
 * <p>采用「持有式 + 嵌套静态类」而不是「每种异常一个文件」，与本项目
 * {@code like-rpc/exception/LikeExceptions.java}、{@code message-rpc/exception/MessageExceptions.java}
 * 保持同一约定。
 *
 * <h2>🔴 本域与 like 域的根本差异：必须同时持有 gRPC 码与业务码</h2>
 *
 * <p>Go 侧发出错误的唯一入口是
 * {@code favorite/common/errmsg.go:104-106}：
 * <pre>
 * func GRPCError(grpcCode codes.Code, bizCode int) error {
 *     return status.Error(grpcCode, GetErrMsg(bizCode))
 * }
 * </pre>
 * 即 wire 上是「<b>标准</b> gRPC 码 + 业务文案」，<b>业务码本身不上线</b>，
 * 只能靠 description 文案反查还原（见 {@code FavoriteGrpcError} 的类注释）。
 *
 * <p>⇒ Java 侧 {@link Rpc} <b>必须同时带 {@link Status.Code} 与 bizCode</b>：
 * 只带业务码就丢掉了 gRPC 码（客户端读 {@code grpc-status} 会拿到 UNKNOWN），
 * 只带 gRPC 码就丢掉了文案（而文案正是本域还原业务码的唯一载体）。
 *
 * <h2>不可从 Go 逻辑反推 gRPC 码</h2>
 *
 * <p>本域的 gRPC 码分布里有两个<b>非标准用法</b>，是 L1 冻结契约：
 * <ul>
 *   <li>{@code Unavailable} + 5001 —— {@code helper.go:115}（UserInfo 缺 status）
 *       与 {@code helper.go:155/165/169}（文章修订不可证）。刻意不是 {@code Internal}。</li>
 *   <li>{@code FailedPrecondition} + 5304 —— {@code delete_favorite_logic.go:86}、
 *       {@code delete_favorite_folder_logic.go:86}。这是 7 个 logic 里唯一用到的非常规码，
 *       且它<b>不在</b> {@code BizCodeFromError} 的 switch 白名单里
 *       ⇒ 文案丢失时只能降级成 1015。</li>
 * </ul>
 */
public final class FavoriteExceptions {

    private FavoriteExceptions() {
        // 异常工厂，禁止实例化
    }

    /**
     * 带 favorite gRPC 码 + 业务码的领域异常。
     *
     * <p>message 刻意采用与 {@code FavoriteGrpcError} description 同一套文案，
     * 便于全链路 grep；但 <b>gRPC 边界必须用 {@link #getMsg()} 而不是 {@link #getMessage()}</b>
     * —— 后者是 {@code ErrCode:X, GrpcCode:Y, Errmsg:Z} 的包装格式，
     * 传过去会得到「文案被二次包装」的 description，与 Go 不一致。
     */
    public static class Rpc extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final Status.Code grpcCode;
        private final int bizCode;

        /** <b>原始</b>文案（不含任何包装前缀），等于 {@code FavoriteErrorMessages.getMsg(bizCode)}。 */
        private final String msg;

        public Rpc(Status.Code grpcCode, int bizCode) {
            this(grpcCode, bizCode, FavoriteErrorMessages.getMsg(bizCode), null);
        }

        public Rpc(Status.Code grpcCode, int bizCode, Throwable cause) {
            this(grpcCode, bizCode, FavoriteErrorMessages.getMsg(bizCode), cause);
        }

        public Rpc(Status.Code grpcCode, int bizCode, String msg, Throwable cause) {
            super(String.format("ErrCode:%d, GrpcCode:%s, Errmsg:%s", bizCode, grpcCode, msg), cause);
            this.grpcCode = grpcCode;
            this.bizCode = bizCode;
            this.msg = msg;
        }

        /** 对应 Go {@code GRPCError} 的第一个参数，<b>原样透传</b>不做改写。 */
        public Status.Code getGrpcCode() {
            return grpcCode;
        }

        /** favorite 业务码，取值见 {@link FavoriteErrorCode}。 */
        public int getBizCode() {
            return bizCode;
        }

        /** <b>原始</b>文案 —— gRPC 边界必须用这个，不能用 {@link #getMessage()}。 */
        public String getMsg() {
            return msg;
        }
    }

    // ==================== 与 Go 侧调用点一一对应的工厂 ====================

    /** {@code GRPCError(codes.InvalidArgument, ErrorInvalidParam=5003)}。 */
    public static Rpc invalidParam() {
        return new Rpc(Status.Code.INVALID_ARGUMENT, FavoriteErrorCode.ERROR_INVALID_PARAM.getCode());
    }

    /** {@code GRPCError(codes.InvalidArgument, ErrorFavoriteFolderNameEmpty=5202)}。 */
    public static Rpc folderNameEmpty() {
        return new Rpc(Status.Code.INVALID_ARGUMENT, FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EMPTY.getCode());
    }

    /** {@code GRPCError(codes.InvalidArgument, ErrorFavoriteTargetEmpty=5303)}。 */
    public static Rpc favoriteTargetEmpty() {
        return new Rpc(Status.Code.INVALID_ARGUMENT, FavoriteErrorCode.ERROR_FAVORITE_TARGET_EMPTY.getCode());
    }

    /** {@code GRPCError(codes.NotFound, ErrorFavoriteFolderNotFound=5201)}。 */
    public static Rpc folderNotFound() {
        return new Rpc(Status.Code.NOT_FOUND, FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NOT_FOUND.getCode());
    }

    /** {@code GRPCError(codes.NotFound, ErrorFavoriteNotFound=5301)}。 */
    public static Rpc favoriteNotFound() {
        return new Rpc(Status.Code.NOT_FOUND, FavoriteErrorCode.ERROR_FAVORITE_NOT_FOUND.getCode());
    }

    /** {@code GRPCError(codes.NotFound, ErrorUserNotFound=5101)}（ensureUserExists 的 1、3 分支）。 */
    public static Rpc userNotFound() {
        return new Rpc(Status.Code.NOT_FOUND, FavoriteErrorCode.ERROR_USER_NOT_FOUND.getCode());
    }

    /** {@code GRPCError(codes.NotFound, ErrorNotFound=5002)}（resolveArticleSnapshot 的 2、3 分支）。 */
    public static Rpc articleNotFound() {
        return new Rpc(Status.Code.NOT_FOUND, FavoriteErrorCode.ERROR_NOT_FOUND.getCode());
    }

    /** {@code GRPCError(codes.AlreadyExists, ErrorFavoriteFolderNameExists=5203)}。 */
    public static Rpc folderNameExists() {
        return new Rpc(Status.Code.ALREADY_EXISTS, FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EXISTS.getCode());
    }

    /** {@code GRPCError(codes.AlreadyExists, ErrorFavoriteAlreadyExist=5302)}。 */
    public static Rpc favoriteAlreadyExist() {
        return new Rpc(Status.Code.ALREADY_EXISTS, FavoriteErrorCode.ERROR_FAVORITE_ALREADY_EXIST.getCode());
    }

    /** {@code GRPCError(codes.PermissionDenied, ErrorForbidden=5005)}。 */
    public static Rpc forbidden() {
        return new Rpc(Status.Code.PERMISSION_DENIED, FavoriteErrorCode.ERROR_FORBIDDEN.getCode());
    }

    /**
     * 🔴 {@code GRPCError(codes.Unavailable, ErrorServerCommon=5001)} —— <b>刻意用 Unavailable</b>。
     *
     * <p>三处调用点（{@code helper.go:115,155,165,169}）：UserInfo 缺 status、文章非 PUBLISHED、
     * 修订号不可证 / publication_gap 不合规。
     * <b>不要"顺手改成" Internal</b>：客户端按 gRPC 码决定重试策略，
     * Unavailable（可重试）与 Internal（不可重试）在这几处是不同契约。
     */
    public static Rpc unavailableServerCommon() {
        return new Rpc(Status.Code.UNAVAILABLE, FavoriteErrorCode.ERROR_SERVER_COMMON.getCode());
    }

    /** {@code GRPCError(codes.Internal, ErrorServerCommon=5001)}（ensureUserExists 的第 2 分支）。 */
    public static Rpc internalServerCommon() {
        return new Rpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_SERVER_COMMON.getCode());
    }

    /** {@code GRPCError(codes.Internal, ErrorDbSelect=5007)} —— <b>只用于读路径</b>。 */
    public static Rpc dbSelect() {
        return new Rpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_SELECT.getCode());
    }

    /** {@code GRPCError(codes.Internal, ErrorDbUpdate=5008)} —— <b>只用于写路径</b>。 */
    public static Rpc dbUpdate() {
        return new Rpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_DB_UPDATE.getCode());
    }

    /** {@code GRPCError(codes.Internal, ErrorGenerateID=5009)}（两处 snowflake.GetID 失败）。 */
    public static Rpc generateId() {
        return new Rpc(Status.Code.INTERNAL, FavoriteErrorCode.ERROR_GENERATE_ID.getCode());
    }

    /**
     * 🔴 {@code GRPCError(codes.FailedPrecondition, ErrorFavoriteHistoryBlocked=5304)}。
     *
     * <p>由 {@link FactMigrationBlockedException} 在事务出口翻译而来。
     * 这是本域唯一一个<b>不在</b> {@code BizCodeFromError} switch 白名单里的码
     * ⇒ 客户端只能靠 description 文案「收藏暂不可撤回」反查还原 5304。
     */
    public static Rpc historyBlocked() {
        return new Rpc(Status.Code.FAILED_PRECONDITION, FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode());
    }

    // ==================== 模型层哨兵（不出现在 gRPC 上） ====================

    /**
     * 对应 Go {@code model/vars.go:6} {@code ErrorNotFound = errors.New("record not found")}。
     *
     * <p>Go 侧所有 {@code if err == gorm.ErrRecordNotFound { return nil, ErrorNotFound }}
     * 都在**模型层**把它归一化，然后由 logic 决定翻译成 5201 / 5301 / 重名 / 重复中的哪一个。
     * ⇒ 这是一个**内部**哨兵，绝不能直接上线。
     */
    public static class NotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NotFoundException(String what) {
            super("record not found: " + what);
        }
    }

    /**
     * 对应 Go {@code favorite_fact.go:22} {@code ErrFavoriteOwnerMismatch}。
     *
     * <p>出现在三个事务写内部的归属复核处（{@code favorite_fact.go:280,313,349}）。
     * 注意：它在 Go 侧<b>不会</b>被单独翻译成 {@code PermissionDenied} ——
     * 事务失败后 logic 看到的是普通 error ⇒ 输出 {@code Internal/5008}。
     * ⇒ 本类也<b>不</b>自带码翻译，保持原样。
     */
    public static class OwnerMismatchException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public OwnerMismatchException() {
            super("favorite owner mismatch");
        }
    }

    /**
     * 🔴 对应 Go {@code favorite_fact.go:23} {@code ErrFavoriteFactMigrationBlocked}。
     *
     * <p>由 {@code FavoriteAssertionSchemaResolver} 在两个分支（legacy 标记不符 / 冻结事件不符）抛出。
     * 是<b>唯一</b>会被 logic 翻译成 {@code FailedPrecondition/5304} 的模型层错误：
     * <pre>
     * if errors.Is(dbErr, model.ErrFavoriteFactMigrationBlocked) {
     *     return nil, favoritecommon.GRPCError(codes.FailedPrecondition, favoritecommon.ErrorFavoriteHistoryBlocked)
     * }
     * </pre>
     * （{@code delete_favorite_logic.go:84-87}、{@code delete_favorite_folder_logic.go:84-87}）
     *
     * <p>⚠️ 走 {@code TransactionTemplate} / {@code @Transactional} 时**必须配
     * {@code rollbackFor = Exception.class}**：它是 checked 语义之外的业务中断，
     * 若被误当成「可提交」，就会提交一条「业务行已删但撤回事实没写」的撕裂状态。
     */
    public static class FactMigrationBlockedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public FactMigrationBlockedException() {
            super("favorite fact history must be reconciled before retract");
        }
    }

    /**
     * 对应 Go {@code favorite_authority.go:22} {@code ErrFavoriteFactUnavailable}。
     *
     * <p>权威读路径的唯一「查不到 / 不可信」出口，被 authority servlet 翻译成 404。
     * 与 {@code common/communityfact/AuthorityHandler} 的
     * {@code AuthorityUnavailableException}（503）是**两件事**：本类 ⇒ 404，框架那个 ⇒ 503。
     * 本实现复用框架的 {@code AuthorityUnavailableException} 上线，故本类只在
     * 「事实确实不可用」时直接使用，语义已由调用方按 404 处理。
     */
    public static class FactUnavailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public FactUnavailableException() {
            super("favorite fact unavailable");
        }

        public FactUnavailableException(String detail) {
            super("favorite fact unavailable: " + detail);
        }
    }
}