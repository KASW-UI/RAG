package com.rag.users.admin.rpc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.AdminInviteEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions.InviteUnavailableException;
import com.rag.users.admin.rpc.exception.AdminExceptions.UsernameTakenException;
import com.rag.users.admin.rpc.mapper.AdminInviteMapper;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * CreateAdmin 的 4 步原子事务 —— <b>刻意独立成 Bean</b>。
 *
 * <p><b>为什么必须独立成 Bean</b>：Spring 的 {@code @Transactional} 靠 AOP 代理生效，
 * 而代理<b>只拦截从容器外部进来的调用</b>。若这 4 步写在 {@code AdminDomainService}
 * 自己的 public 方法里、再由同类的 {@code createAdmin(...)} 直接调用（self-invocation），
 * 调用不经过代理，{@code @Transactional} 会<b>静默失效</b>——代码看着有事务，运行时是
 * auto-commit，异常不回滚。独立 Bean 后由 {@code AdminDomainService} 跨 Bean 调用，
 * 代理才会生效。
 *
 * <p>这也与参考实现一致：参考侧 {@code DataModel.createAdminAtomic} 是独立 Bean，
 * 由 {@code CreateAdminLogic} 跨 Bean 调用。
 *
 * <p><b>为什么要悲观锁</b>：邀请码的业务不变量是「一次性使用」。只靠"事务内读 used_at 再更新"
 * 在 PostgreSQL 默认的 READ COMMITTED 下仍有竞态 —— 两个并发事务可以同时读到未使用、
 * 都通过校验、都插入 admin，最后才各自更新 invite，后写者覆盖先写者。
 * 对 invite 行加 {@code SELECT ... FOR UPDATE} 后，并发调用会被串行化，
 * 第二个调用在拿到锁时会读到已被标记 used 的行并被 {@link InviteUnavailableException} 拒绝。
 */
@Service
public class AdminCreateAtomicService {

    private final AdminMapper adminMapper;
    private final AdminInviteMapper adminInviteMapper;

    public AdminCreateAtomicService(AdminMapper adminMapper, AdminInviteMapper adminInviteMapper) {
        this.adminMapper = adminMapper;
        this.adminInviteMapper = adminInviteMapper;
    }

    /**
     * 4 步原子事务：
     * <pre>
     *   W1 查 invite（SELECT ... FOR UPDATE by code）—— 不存在 / 已用 / 已过期 → InviteUnavailableException
     *   W2 查 username 冲突 —— 命中 → UsernameTakenException
     *   W3 insert admin —— 唯一冲突 → 原异常透传（由 caller 用 AdminExceptions.isUniqueViolation 识别）
     *   W4 标记 invite used
     * </pre>
     *
     * @param passwordHash <b>已哈希</b>的密码。生产路径请用
     *                     {@link #createAdminAtomic(String, String, String, String, Map, Long, boolean)}
     *                     传明文，由本类在事务内按 Go 步骤序哈希。
     * @return 生成的 admin uid
     */
    @Transactional
    public Long createAdminAtomic(String username, String inviteCode, String passwordHash,
                                  String email, Map<String, String> extraInfo, Long generatedUid) {
        return createAdminAtomic(username, inviteCode, passwordHash, email, extraInfo,
                generatedUid, false);
    }

    /**
     * 生产路径入口：传<b>明文</b>密码，哈希在事务内、W2 之后完成。
     *
     * <p><b>R5 修复（2026-10-22，用户签字「完全对齐 Go」）</b>：
     * 修复前 {@code AdminDomainService.createAdmin} 在<b>事务外</b>先做 bcrypt，
     * 于是 bcrypt 的 72 字节上限会抢在 invite / username 检查之前抛错，
     * 把本该报「邀请码错误(1016)」「用户名已存在(1001)」的请求报成 5001。
     *
     * <p><b>Go 出处</b>：{@code service/user/admin/rpc/internal/logic/createadminlogic.go}
     * 的事务闭包内步骤序为
     * <pre>
     *   FindAdminInviteByCodeForUpdate → not found/过期 → PermissionDenied
     *   FindOneAdminByUsernameTx        → 命中         → AlreadyExists
     *   cryptx.PasswordEncrypt          → &gt;72 字节    → err
     *   snowflake.GetID
     *   InsertOneAdminTx
     *   UpdateAdminInviteUsedTx
     * </pre>
     * 本方法逐条镜像该顺序：W1 → W2 → <b>bcrypt</b> → insert → 标记已用。
     *
     * <p><b>实测证据</b>（{@code tools/difftest/r5-order-probe.py}，全新 cluster）：
     * <pre>
     *   B 有效邀请码 + 超长密码 + 用户名已存在   Go=1001 / 修复前 RAG=5001
     *   C 无效邀请码 + 超长密码                 Go=1016 / 修复前 RAG=5001
     *   A 有效邀请码 + 超长密码 + 新用户名       Go=5001 / RAG=5001（本就一致）
     * </pre>
     *
     * <p><b>为什么哈希必须在事务内（代价已知并接受）</b>：bcrypt cost=10 约 60~100ms
     * （Go 用 {@code bcrypt.DefaultCost}=10，RAG 用 {@code STRENGTH}=10，同量级），
     * 会延长 invite 行 {@code SELECT ... FOR UPDATE} 的持锁时间。
     * 这是<b>刻意选择</b>：Go 本身就是这个顺序，而该锁只锁 invite 单行，
     * 换来的是「错误码归属」与真源逐条一致。
     *
     * <p>此前本方法的 javadoc 写着「bcrypt password（事务外，避免延长事务持有时间）」——
     * 那条注释描述的是一个<b>未经 Go 出处论证</b>的优化，属 §3.2「无出处 idiomatization」，
     * 本次按用户裁决撤销。
     *
     * @param password 明文密码（仅当 {@code passwordIsPlaintext} 为 true 时才会被哈希）
     */
    @Transactional
    public Long createAdminAtomic(String username, String inviteCode, String password,
                                  String email, Map<String, String> extraInfo, Long generatedUid,
                                  boolean passwordIsPlaintext) {
        // W1 查 invite：悲观锁 SELECT ... FOR UPDATE —— 必须在事务内做，
        // 锁到本事务结束，保证同一邀请码不会被两个并发事务同时消费。
        AdminInviteEntity invite = adminInviteMapper.selectOne(
                new LambdaQueryWrapper<AdminInviteEntity>()
                        .eq(AdminInviteEntity::getCode, inviteCode)
                        .last("FOR UPDATE")
        );
        if (invite == null) {
            throw new InviteUnavailableException("invite code not found: " + inviteCode);
        }
        LocalDateTime now = LocalDateTime.now();
        if (invite.getUsedAt() != null || !invite.getExpiresAt().isAfter(now)) {
            throw new InviteUnavailableException("invite code expired or used: " + inviteCode);
        }

        // W2 查 username 冲突（事务内）
        AdminEntity existing = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUsername, username)
        );
        if (existing != null) {
            throw new UsernameTakenException("admin already exists with username: " + username);
        }

        // R5（2026-10-22，用户签字「完全对齐 Go」）：bcrypt 必须在 W1/W2 之后。
        //
        // 两端 bcrypt 实现相同（Go bcrypt.DefaultCost=10 / RAG PasswordUtil STRENGTH=10），
        // 都拒 >72 字节的密码；分歧<b>纯粹来自步骤顺序</b>：
        // 修复前 RAG 在事务外先哈希 → 72 字节上限抢在 invite / username 检查之前抛错，
        // 把本该报 1016 / 1001 的请求报成 5001。
        // Go 出处：createadminlogic.go 事务闭包内 PasswordEncrypt 在
        //           FindAdminInviteByCodeForUpdate 与 FindOneAdminByUsernameTx 之后。
        String effectiveHash = password;
        if (passwordIsPlaintext) {
            try {
                effectiveHash = PasswordUtil.encrypt(password);
            } catch (StatusRuntimeException e) {
                // B-1：DEADLINE_EXCEEDED 必须上抛，不能被改写成 INTERNAL（对齐 Go 的 1015）
                throw e;
            } catch (Exception e) {
                // bcrypt 对 >72 字节抛 IllegalArgumentException。
                // Go 侧这里同样是裸 err → 由 CreateAdminLogic 外层兜成 Internal(1015)，
                // 这里保持同一归属：冒泡给 AdminDomainService 的 db_error 分支。
                throw e;
            }
        }

        // W3 插入 admin（事务内）
        AdminEntity admin = new AdminEntity();
        admin.setUid(generatedUid);
        admin.setUsername(username);
        admin.setPassword(effectiveHash);
        admin.setEmail(email);   // nullable，null 表示不填
        if (extraInfo != null && !extraInfo.isEmpty()) {
            admin.setExtraInfo(new HashMap<>(extraInfo));
        }
        adminMapper.insert(admin);

        // W4 标记 invite used（事务内）
        LambdaUpdateWrapper<AdminInviteEntity> inviteWrapper = new LambdaUpdateWrapper<>();
        inviteWrapper.eq(AdminInviteEntity::getId, invite.getId())
                .set(AdminInviteEntity::getUsedByUid, generatedUid)
                .set(AdminInviteEntity::getUsedAt, now);
        adminInviteMapper.update(null, inviteWrapper);

        return generatedUid;
    }
}