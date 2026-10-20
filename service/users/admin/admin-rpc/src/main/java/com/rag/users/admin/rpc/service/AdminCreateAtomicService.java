package com.rag.users.admin.rpc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.AdminInviteEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions.InviteUnavailableException;
import com.rag.users.admin.rpc.exception.AdminExceptions.UsernameTakenException;
import com.rag.users.admin.rpc.mapper.AdminInviteMapper;
import com.rag.users.admin.rpc.mapper.AdminMapper;
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
     * @return 生成的 admin uid
     */
    @Transactional
    public Long createAdminAtomic(String username, String inviteCode, String passwordHash,
                                  String email, Map<String, String> extraInfo, Long generatedUid) {
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

        // W3 插入 admin（事务内）
        AdminEntity admin = new AdminEntity();
        admin.setUid(generatedUid);
        admin.setUsername(username);
        admin.setPassword(passwordHash);
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