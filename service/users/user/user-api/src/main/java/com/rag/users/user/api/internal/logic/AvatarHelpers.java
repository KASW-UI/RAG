package com.rag.users.user.api.internal.logic;

import com.rag.users.user.api.internal.identity.IdentityResolver;
import com.rag.users.user.api.internal.types.ApiTypes;
import com.rag.users.user.proto.AvatarHistoryItem;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 对应 Go 端 internal/logic/user/avatarhelpers.go（17 行，两个工具函数）。
 *
 * <p>L4 实现机制差异：Go 端是 package-level helper 函数；Java 侧是 {@code @Component} 单例
 * 让 {@link UploadAvatarLogic}/{@link GetAvatarHistoryLogic}/{@link SelectAvatarLogic} 注入调用。
 *
 * <p>本类承担两个不同的业务责任 —— L2 业务语义对齐：
 * <ol>
 *   <li>{@link #currentUserID(HttpServletRequest)} —— 对应 Go {@code currentUserID(ctx)}；
 *       从 request attribute "userId" 取正整数 UID。
 *       行为差异（对齐）：Go 端 ctx.Value("userId").(json.Number).Int64()，
 *       Java 端 request.getAttribute("userId").({@link Long})。
 *       失败时抛 {@link IdentityResolver.ErrInvalidClaimException}（与 Go {@code ErrInvalidClaim} 对齐），
 *       由调用方（Logic）映射为 {@code ErrorCode.ErrorTokenRuntime = 1006}。</li>
 *
 *   <li>{@link #avatarHistoryItemFromPB(AvatarHistoryItem)} —— 对应 Go
 *       {@code avatarHistoryItemFromPB(*pb.AvatarHistoryItem) types.AvatarHistoryItem}；
 *       6 个字段逐个赋值。Go 端 nil 入参返回空 struct（字段为零值）；
 *       Java 端 null 入参也返回 {@link ApiTypes.AvatarHistoryItem} 空实例（字段为零值）。
 *       这点对齐（L1 wire 契约要求非空项字段值与 Go 一致）。</li>
 * </ol>
 */
@org.springframework.stereotype.Component
public class AvatarHelpers {

    /**
     * 对应 Go {@code func currentUserID(ctx context.Context) (int64, error)}。
     *
     * <p>委托 {@link IdentityResolver#claimedUID(HttpServletRequest)} 实现 —— Go 端是单点逻辑，
     * Java 侧保持单点逻辑（不重复实现 claim 校验，避免两份校验代码漂移）。
     *
     * <p>失败时抛 {@link IdentityResolver.ErrInvalidClaimException}，
     * 由 Logic 在 catch 里映射为 {@code ErrorTokenRuntime}。
     */
    public long currentUserID(HttpServletRequest request) {
        return IdentityResolver.claimedUID(request);
    }

    /**
     * 对应 Go {@code func avatarHistoryItemFromPB(item *pb.AvatarHistoryItem) types.AvatarHistoryItem}。
     *
     * <p>字段逐个复制（1:1 对齐）：
     * <pre>
     *   pb.Id          → dto.Id
     *   pb.AvatarUrl   → dto.AvatarUrl
     *   pb.ContentType → dto.ContentType
     *   pb.SizeBytes   → dto.SizeBytes
     *   pb.IsCurrent   → dto.IsCurrent
     *   pb.CreateTime  → dto.CreateTime
     * </pre>
     *
     * <p>Go 端 nil 入参 → 返回空 struct；Java 端 null 入参 → 返回空 {@link ApiTypes.AvatarHistoryItem}。
     */
    public ApiTypes.AvatarHistoryItem avatarHistoryItemFromPB(AvatarHistoryItem pb) {
        ApiTypes.AvatarHistoryItem dto = new ApiTypes.AvatarHistoryItem();
        if (pb == null) {
            return dto;   // 对齐 Go: nil → 零值
        }
        dto.setId(pb.getId());
        dto.setAvatarUrl(pb.getAvatarUrl());
        dto.setContentType(pb.getContentType());
        dto.setSizeBytes(pb.getSizeBytes());
        dto.setCurrent(pb.getIsCurrent());
        dto.setCreateTime(pb.getCreateTime());
        return dto;
    }
}