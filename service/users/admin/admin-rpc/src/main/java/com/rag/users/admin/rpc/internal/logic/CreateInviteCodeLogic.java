package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.CreateInviteCodeReq;
import com.rag.users.admin.proto.CreateInviteCodeResp;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.AdminInvite;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * 对应 Go 端 internal/logic/createinvitecodelogic.go：
 *   func (l *CreateInviteCodeLogic) CreateInviteCode(in *pb.CreateInviteCodeReq) (*pb.CreateInviteCodeResp, error)
 *
 * <p>为已存在的 operator（admin）生成一个新的 invite code，前缀 "ADM-" + 8 字节 hex 大写（16 字符），
 * 写 admin_invites 表，TTL 10 分钟，code 唯一约束冲突时重试最多 3 次。
 *
 * <p>Works（W1~W3）：
 *   W1 OTel span "Action-Admin-CreateInviteCode"，属性 audit.operator_id
 *   W2 FindOneAdminByUid(operator_id) → NotFound（metrics createinvite+user_not_found）/ Internal（db_error）
 *   W3 expiresAt = now + 10min
 *   W4 循环重试最多 3 次：
 *      - SecureRandom.nextBytes(8) → "ADM-" + HexFormat.of().formatHex(buf).toUpperCase()
 *      - InsertOneAdminInvite
 *      - 成功 → metrics createinvite+success → resp{invite_code, expires_at=unix}
 *      - unique violation（AdminExceptions.isUniqueViolation）→ 不埋点，continue
 *      - 其他 DB 错误 → metrics createinvite+db_error → INTERNAL
 *   W5 重试 3 次仍 unique violation → metrics createinvite+internal_error → INTERNAL
 *
 * <p>关键 Decision：
 *   - 邀请码格式：前缀 "ADM-" + 16 字符 hex（大写），总 20 字符
 *   - TTL 10 分钟（Go 端常量 inviteCodeTTL）
 *   - 最大重试 3 次（Go 端常量 inviteCodeMaxRetry）
 *   - 随机源：SecureRandom（密码学安全），与 Go crypto/rand 等价
 *   - unique violation 用 AdminExceptions.isUniqueViolation 识别（DataIntegrityViolationException + SQLState 23505）
 */
@Service
public class CreateInviteCodeLogic {

    /** 邀请码有效期 10 分钟，与 Go 端 inviteCodeTTL 常量一致 */
    private static final Duration INVITE_CODE_TTL = Duration.ofMinutes(10);

    /** 最大重试次数，与 Go 端 inviteCodeMaxRetry 常量一致 */
    private static final int INVITE_CODE_MAX_RETRY = 3;

    /** 邀请码前缀，与 Go 端 "ADM-" 字面量一致 */
    private static final String INVITE_CODE_PREFIX = "ADM-";

    /** 单次随机字节数（8 字节 → 16 字符 hex） */
    private static final int INVITE_CODE_RANDOM_BYTES = 8;

    /** 静态 SecureRandom 实例（线程安全），替代 Go 端 crypto/rand.Reader */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public CreateInviteCodeLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
    }

    public CreateInviteCodeResp createInviteCode(CreateInviteCodeReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-CreateInviteCode")
                .setAttribute("audit.operator_id", req.getOperatorId())
                .startSpan();
        try {
            // W2 校验 operator 是有效 admin
            try {
                svcCtx.getDataModel().findOneAdminByUid(req.getOperatorId());
            } catch (NotFoundException e) {
                metrics.getCreateInviteUserNotFound().increment();
                throw Status.NOT_FOUND
                        .withDescription("用户不存在")
                        .asRuntimeException();
            } catch (Exception e) {
                metrics.getCreateInviteDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB查询失败")
                        .asRuntimeException();
            }

            // W3 expiresAt = now + 10min
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plus(INVITE_CODE_TTL);

            // W4 重试循环
            for (int attempt = 0; attempt < INVITE_CODE_MAX_RETRY; attempt++) {
                // 生成 8 字节随机 hex（前缀 "ADM-" + 大写 16 字符 hex）
                String code;
                try {
                    code = generateInviteCode();
                } catch (Exception e) {
                    metrics.getCreateInviteInternalError().increment();
                    span.recordException(e);
                    throw Status.INTERNAL
                            .withDescription("管理员邀请码生成失败")
                            .asRuntimeException();
                }

                // 插入 invite
                AdminInvite invite = new AdminInvite();
                invite.setCode(code);
                invite.setInviterUid(req.getOperatorId());
                invite.setExpiresAt(expiresAt);

                try {
                    svcCtx.getDataModel().insertAdminInvite(invite);
                } catch (Exception e) {
                    if (AdminExceptions.isUniqueViolation(e)) {
                        // unique violation → 重试（不埋点，对应 Go 端 continue）
                        continue;
                    }
                    // 其他 DB 错误
                    metrics.getCreateInviteDbError().increment();
                    span.recordException(e);
                    throw Status.INTERNAL
                            .withDescription("DB写入失败")
                            .asRuntimeException();
                }

                // 成功
                metrics.getCreateInviteSuccess().increment();
                long expiresAtUnix = expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond();
                return CreateInviteCodeResp.newBuilder()
                        .setInviteCode(code)
                        .setExpiresAt(expiresAtUnix)
                        .build();
            }

            // W5 重试超过 3 次仍 unique violation
            metrics.getCreateInviteInternalError().increment();
            RuntimeException retryExhausted = new RuntimeException("invite code collision retry exceeded");
            span.recordException(retryExhausted);
            throw Status.INTERNAL
                    .withDescription("管理员邀请码生成失败")
                    .asRuntimeException();
        } finally {
            span.end();
        }
    }

    /**
     * 对应 Go 端 generateInviteCode：
     *   buf := make([]byte, 8)
     *   _, err := rand.Read(buf)
     *   return "ADM-" + strings.ToUpper(hex.EncodeToString(buf)), err
     *
     * Java 侧用 SecureRandom（密码学安全，等价 Go crypto/rand）+ HexFormat.of().formatHex。
     */
    private static String generateInviteCode() {
        byte[] buf = new byte[INVITE_CODE_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(buf);
        return INVITE_CODE_PREFIX + HexFormat.of().formatHex(buf).toUpperCase();
    }
}