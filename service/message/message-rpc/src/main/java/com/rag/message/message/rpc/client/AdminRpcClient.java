package com.rag.message.message.rpc.client;

import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.rpc.exception.MessageExceptions;
import com.rag.message.message.proto.SenderRole;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetUserListReq;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * admin-rpc gRPC client facade —— 对应 Go 端
 * {@code service/message/rpc/internal/svc/ServiceContext.go} 的 AdminRpc。
 *
 * <p>调用用途：
 * <ul>
 *   <li>{@link #loadProfile(SenderRole, long)}：admin 域 peer profile（ListConversations 时）</li>
 *   <li>{@link #collectBroadcastRecipients(long)}：广播通知收件人列表（{@code admin.GetUserList} 分页）</li>
 * </ul>
 *
 * <p>Go 端 {@code loadProfile(ctx, svcCtx, role=ADMIN, id)} —
 * 失败时返回 {@code "Admin {id}"} 兜底字符串，与 UserRpc 同模式。
 *
 * <p>Go 端 {@code collectBroadcastRecipients(ctx, svcCtx)} ——
 * 通过 {@code admin.GetUserList} 分页拉所有 user_id。RPC 抛错时向上抛内部错误，
 * 由 Logic 层统一映射为 INTERNAL + ErrorServerCommon。
 */
@Component
public class AdminRpcClient {

    private static final Logger LOG = LoggerFactory.getLogger(AdminRpcClient.class);

    private final AdminServiceGrpc.AdminServiceBlockingStub adminStub;

    public AdminRpcClient(@GrpcClient("admin-rpc") AdminServiceGrpc.AdminServiceBlockingStub adminStub) {
        this.adminStub = adminStub;
    }

    /**
     * 对应 Go 端 {@code loadProfile(role=ADMIN, id)}。
     * 失败 / 找不到 → 兜底 {@code ["Admin {id}", ""]}。
     */
    public String[] loadProfile(SenderRole role, long id) {
        if (role != SenderRole.ADMIN) {
            return new String[]{"Unknown " + id, ""};
        }
        if (id <= 0) {
            return new String[]{"Admin " + id, ""};
        }
        try {
            var resp = adminStub.getSelf(GetSelfReq.newBuilder().setUid(id).build());
            if (resp != null && resp.getAdmin() != null) {
                return new String[]{resp.getAdmin().getUsername(), resp.getAdmin().getEmail()};
            }
            return new String[]{"Admin " + id, ""};
        } catch (StatusRuntimeException sre) {
            return new String[]{"Admin " + id, ""};
        } catch (RuntimeException e) {
            LOG.warn("getSelf({}) failed: {}", id, e.getMessage());
            return new String[]{"Admin " + id, ""};
        }
    }

    /**
     * 对应 Go 端 {@code collectBroadcastRecipients(ctx, svcCtx) ([]int64, error)}：
     * 通过 admin.GetUserList 分页拉所有 user_id，作为广播通知的 recipients。
     *
     * <p>行为 1:1：
     * <ul>
     *   <li>pageSize 由调用方传入（从 {@link com.rag.message.message.rpc.config.MessageProperties.Broadcast#getPageSize()}）；≤0 兜底 200</li>
     *   <li>从 page=1 开始逐页拉；某页空 → 停止</li>
     *   <li>page 内累积 uid，去重（Go 端 dedupeRecipients 在 ServiceContext 中；Java 端用 {@link java.util.LinkedHashSet}）</li>
     *   <li>当前页返回数 &lt; pageSize → 最后一页，停止</li>
     *   <li>RPC 抛错 → 向上抛 {@link MessageExceptions.DbSelectException}（由 Logic 层 observeDbError + 统一映射为 INTERNAL + ErrorServerCommon）</li>
     * </ul>
     */
    public List<Long> collectBroadcastRecipients(long pageSize) {
        if (pageSize <= 0) {
            pageSize = 200L;
        }
        java.util.LinkedHashSet<Long> recipients = new java.util.LinkedHashSet<>();
        long page = 1L;
        try {
            while (true) {
                var resp = adminStub.getUserList(
                        GetUserListReq.newBuilder()
                                .setPage(page)
                                .setPageSize(pageSize)
                                .build());
                if (resp == null || resp.getListList().isEmpty()) {
                    break;
                }
                for (var item : resp.getListList()) {
                    if (item != null && item.getUid() > 0L) {
                        recipients.add(item.getUid());
                    }
                }
                if (resp.getListList().size() < pageSize) {
                    break;
                }
                page++;
            }
        } catch (StatusRuntimeException sre) {
            throw new MessageExceptions.DbSelectException(
                    "broadcast user list failed: page=" + page, sre);
        } catch (RuntimeException e) {
            throw new MessageExceptions.DbSelectException(
                    "broadcast user list failed: page=" + page, e);
        }
        return new ArrayList<>(recipients);
    }

    /** 业务错误码常量引用 —— 抑制 unused warning。 */
    @SuppressWarnings("unused")
    private static final int UNUSED_KEEP_REFERENCE = MessageErrorCode.SUCCESS.getCode();
}