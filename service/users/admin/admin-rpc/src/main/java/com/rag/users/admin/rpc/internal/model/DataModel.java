package com.rag.users.admin.rpc.internal.model;

import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access layer，对应 Go 端 internal/model/data_model.go 的 AdminModel struct（17 个 DB 方法）。
 *
 * Go 用 GORM + EntityManager.WithContext(ctx)；Java 用 JPA EntityManager 直接调 JPQL。
 *
 * 关键语义映射：
 *   - Go 的 sentinel error (ErrorNotFound) → Java 的 NotFoundException（抛异常）
 *   - Go 的 m.conn.WithContext(ctx).Transaction(fn) → Java 的 @Transactional（方法级声明）
 *   - Go 的 GORM Updates(newUser) 跳过零值字段 → Java 动态拼 JPQL，只更新非空字段
 *   - Go 的 tx.Clauses(clause.Locking{Strength: "UPDATE"}) → Java setLockMode(PESSIMISTIC_WRITE)
 *
 * 框架职责：
 *   - Go：gorm.DB 提供 Where / First / Create / Updates / Delete / Transaction 一站式
 *   - Java：JPA EntityManager 用 createQuery / persist / merge / executeUpdate 拆开
 *
 * 注：admin-rpc 持 Admin 表 + User 表 + AdminInvite 表三张表。User 表虽然由 user-rpc 主导，
 *     但 admin-rpc 也直接读写（BanUser / DeleteUser / ResetUserPassword / UpdateUser），
 *     所以本类覆盖三表。
 */
@Service
@Transactional  // DRIFT-NEW-010：JPA mutation（executeUpdate / persist / remove）必须在事务内
public class DataModel {

    @PersistenceContext
    private EntityManager em;

    // ====================== User 表操作 ======================

    public User findOneUserByUid(Long uid) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.uid = :uid", User.class)
                    .setParameter("uid", uid)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("user not found by uid: " + uid);
        }
    }

    public User findOneUserByUsername(String username) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :u", User.class)
                    .setParameter("u", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("user not found by username: " + username);
        }
    }

    public void deleteUserByUid(Long uid) {
        // 对应 Go 端 m.conn.WithContext(ctx).Where("uid = ?", uid).Delete(&User{})：
        //   result.RowsAffected == 0 → ErrorNotFound
        int rows = em.createQuery("DELETE FROM User u WHERE u.uid = :uid")
                .setParameter("uid", uid)
                .executeUpdate();
        if (rows == 0) {
            throw new NotFoundException("user not found by uid: " + uid);
        }
    }

    /**
     * 对应 Go 端 m.conn.WithContext(ctx).Model(&User{}).Where("uid = ?", uid).Updates(newUser)。
     * GORM 的 Updates 默认跳过零值（"" / 0 / false），等价于 Java 只把"显式传入且非空"的字段加入 UPDATE。
     */
    public void updateUserByUid(Long uid, User newUser) {
        StringBuilder jpql = new StringBuilder("UPDATE User u SET u.uid = u.uid");
        boolean hasUsername = newUser.getUsername() != null && !newUser.getUsername().isEmpty();
        boolean hasPassword = newUser.getPassword() != null && !newUser.getPassword().isEmpty();
        boolean hasEmail = newUser.getEmail() != null && !newUser.getEmail().isEmpty();
        boolean hasExtraInfo = newUser.getExtraInfo() != null;

        if (hasUsername) jpql.append(", u.username = :username");
        if (hasPassword) jpql.append(", u.password = :password");
        if (hasEmail) jpql.append(", u.email = :email");
        if (hasExtraInfo) jpql.append(", u.extraInfo = :extraInfo");
        jpql.append(" WHERE u.uid = :uid");

        var query = em.createQuery(jpql.toString()).setParameter("uid", uid);
        if (hasUsername) query.setParameter("username", newUser.getUsername());
        if (hasPassword) query.setParameter("password", newUser.getPassword());
        if (hasEmail) query.setParameter("email", newUser.getEmail());
        if (hasExtraInfo) query.setParameter("extraInfo", newUser.getExtraInfo());
        query.executeUpdate();
    }

    public void updateUserStatusByUid(Long uid, Long status) {
        // 对应 Go 端 m.conn.WithContext(ctx).Model(&User{}).Where("uid = ?", uid).Update("status", status)：
        //   result.RowsAffected == 0 → ErrorNotFound
        // BanUser / UnBanUser 业务代码会捕获 ErrorNotFound 转 metrics ban+user_not_found + NOT_FOUND gRPC。
        int rows = em.createQuery("UPDATE User u SET u.status = :status WHERE u.uid = :uid")
                .setParameter("status", status)
                .setParameter("uid", uid)
                .executeUpdate();
        if (rows == 0) {
            throw new NotFoundException("user not found by uid: " + uid);
        }
    }

    public void updateUserPasswordByUid(Long uid, String newPassword) {
        em.createQuery("UPDATE User u SET u.password = :pw WHERE u.uid = :uid")
                .setParameter("pw", newPassword)
                .setParameter("uid", uid)
                .executeUpdate();
    }

    /**
     * 对应 Go 端 FindUserListByKeyword：keyword 非空时按 username/email LIKE，否则全量。
     * 返回 (users, total)：分页 + 总数。
     */
    public UserPage findUserListByKeyword(int page, int pageSize, String keyword) {
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        String pattern = hasKeyword ? "%" + keyword.trim() + "%" : null;

        // count
        Long total;
        if (hasKeyword) {
            total = em.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE u.username LIKE :p OR u.email LIKE :p", Long.class)
                    .setParameter("p", pattern)
                    .getSingleResult();
        } else {
            total = em.createQuery("SELECT COUNT(u) FROM User u", Long.class)
                    .getSingleResult();
        }

        // list
        var listJpql = new StringBuilder("SELECT u FROM User u");
        if (hasKeyword) listJpql.append(" WHERE u.username LIKE :p OR u.email LIKE :p");
        listJpql.append(" ORDER BY u.id DESC");

        var query = em.createQuery(listJpql.toString(), User.class);
        if (hasKeyword) query.setParameter("p", pattern);
        query.setFirstResult((page - 1) * pageSize);
        query.setMaxResults(pageSize);
        List<User> rows = query.getResultList();

        return new UserPage(rows, total);
    }

    /**
     * User 表分页结果包装，对应 Go 端 ([]*User, int64, error) 三返回值。
     */
    public record UserPage(List<User> users, long total) {}

    // ====================== Admin 表操作 ======================

    public Admin findOneAdminByUid(Long uid) {
        try {
            return em.createQuery("SELECT a FROM Admin a WHERE a.uid = :uid", Admin.class)
                    .setParameter("uid", uid)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("admin not found by uid: " + uid);
        }
    }

    public Admin findOneAdminByUsername(String username) {
        try {
            return em.createQuery("SELECT a FROM Admin a WHERE a.username = :u", Admin.class)
                    .setParameter("u", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("admin not found by username: " + username);
        }
    }

    public void updateAdminByUid(Long uid, Admin newAdmin) {
        StringBuilder jpql = new StringBuilder("UPDATE Admin a SET a.uid = a.uid");
        boolean hasUsername = newAdmin.getUsername() != null && !newAdmin.getUsername().isEmpty();
        boolean hasPassword = newAdmin.getPassword() != null && !newAdmin.getPassword().isEmpty();
        // 关键差异：Admin.Email 是 *string（nullable），Go 端 GORM 把 nil pointer 当 zero-value pointer 写 SQL NULL。
        // Java 侧要 1:1 复刻"传 null → SQL NULL"语义，所以 hasEmail 不用 isEmpty 判断；
        // 只要 Logic 显式调用了 setEmail(null) 就视为"显式设置"，写 SQL NULL。
        // Logic 在 UpdateSelf 用 AdminHelpers.optionalString("  ") → null 与 trim 等回对齐。
        boolean hasEmail = newAdmin.getEmail() != null;
        boolean hasExtraInfo = newAdmin.getExtraInfo() != null;

        if (hasUsername) jpql.append(", a.username = :username");
        if (hasPassword) jpql.append(", a.password = :password");
        if (hasEmail) jpql.append(", a.email = :email");
        if (hasExtraInfo) jpql.append(", a.extraInfo = :extraInfo");
        jpql.append(" WHERE a.uid = :uid");

        var query = em.createQuery(jpql.toString()).setParameter("uid", uid);
        if (hasUsername) query.setParameter("username", newAdmin.getUsername());
        if (hasPassword) query.setParameter("password", newAdmin.getPassword());
        if (hasEmail) query.setParameter("email", newAdmin.getEmail());
        if (hasExtraInfo) query.setParameter("extraInfo", newAdmin.getExtraInfo());
        query.executeUpdate();
    }

    /**
     * 对应 Go 端 InsertOneAdmin：单独调用（非事务回滚）。
     * Go 端在 CreateInviteCodeLogic.InsertOneAdminInvite 失败重试，CreateAdminLogic 的
     * 4 步事务用 InsertOneAdminInTx（不同方法名）区分。
     */
    public void insertAdmin(Admin admin) {
        em.persist(admin);
    }

    // ====================== AdminInvite 表操作 ======================

    public void insertAdminInvite(AdminInvite invite) {
        em.persist(invite);
    }

    // ====================== 事务内部方法（@Transactional 加入现有事务）======================

    /**
     * 对应 Go 端 FindOneAdminByUsernameTx(tx, username)。
     * Logic 层进入 @Transactional 上下文后调用此方法，自动加入现有事务。
     */
    public Admin findOneAdminByUsernameInTx(String username) {
        try {
            return em.createQuery("SELECT a FROM Admin a WHERE a.username = :u", Admin.class)
                    .setParameter("u", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("admin not found by username: " + username);
        }
    }

    /**
     * 对应 Go 端 InsertOneAdminTx(tx, admin)。
     */
    public void insertAdminInTx(Admin admin) {
        em.persist(admin);
    }

    /**
     * 对应 Go 端 FindAdminInviteByCodeForUpdate(tx, code) + clause.Locking{Strength: "UPDATE"}。
     * Java 侧 setLockMode(PESSIMISTIC_WRITE) 等价 SELECT ... FOR UPDATE。
     */
    public AdminInvite findAdminInviteByCodeForUpdate(String code) {
        try {
            return em.createQuery("SELECT i FROM AdminInvite i WHERE i.code = :c", AdminInvite.class)
                    .setParameter("c", code)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("admin_invite not found by code: " + code);
        }
    }

    /**
     * 对应 Go 端 UpdateAdminInviteUsedTx(tx, inviteID, usedByUid, usedAt)。
     */
    public void updateAdminInviteUsedInTx(Long inviteId, Long usedByUid, LocalDateTime usedAt) {
        em.createQuery("UPDATE AdminInvite i SET i.usedByUid = :uid, i.usedAt = :at WHERE i.id = :id")
                .setParameter("uid", usedByUid)
                .setParameter("at", usedAt)
                .setParameter("id", inviteId)
                .executeUpdate();
    }

    // ====================== 4 步事务方法（CreateAdminAtomic）======================

    /**
     * 对应 Go 端 CreateAdminLogic.CreateAdmin 的 4 步事务：
     *   W1 查 invite (悲观锁) —— 不存在 / 已用 / 已过期 → PermissionDenied
     *   W2 查 username 冲突 —— 命中 → AlreadyExists
     *   W3 InsertOneAdmin —— 唯一冲突 → AlreadyExists（DaoExceptions.isUniqueViolation）
     *   W4 UpdateAdminInviteUsed —— 标记 invite 已用
     *
     * 整段必须原子：对应 Go 端 m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {...})
     * Java 侧用 @Transactional 包整个 4 步，任一抛异常 → 整体回滚。
     *
     * 返回生成的 admin uid。
     */
    @Transactional
    public Long createAdminAtomic(String username, String inviteCode, String passwordHash,
                                  String email, Map<String, String> extraInfo, Long generatedUid) {
        // W1 查 invite（悲观锁）—— 必须在事务内做
        AdminInvite invite = findAdminInviteByCodeForUpdate(inviteCode);
        LocalDateTime now = LocalDateTime.now();
        if (invite.getUsedAt() != null || !invite.getExpiresAt().isAfter(now)) {
            throw new InviteUnavailableException("invite code expired or used");
        }

        // W2 查 username 冲突（事务内）
        try {
            Admin existing = findOneAdminByUsernameInTx(username);
            // 命中 = 已被占用
            throw new UsernameTakenException("admin already exists with username: " + username);
        } catch (NotFoundException e) {
            // 没找到 = 名字可用
        }

        // W3 插入 admin（事务内）
        Admin admin = new Admin();
        admin.setUid(generatedUid);
        admin.setUsername(username);
        admin.setPassword(passwordHash);
        admin.setEmail(email);  // nullable，传 null 表示不填
        if (extraInfo != null && !extraInfo.isEmpty()) {
            admin.setExtraInfo(new HashMap<>(extraInfo));
        }
        try {
            insertAdminInTx(admin);
        } catch (Exception e) {
            // unique violation (SQLState 23505) 已由 AdminExceptions.isUniqueViolation 识别
            // Logic 层捕获后转 ALREADY_EXISTS；此处直接抛原异常
            throw e;
        }

        // W4 标记 invite used（事务内）
        updateAdminInviteUsedInTx(invite.getId(), generatedUid, now);

        return generatedUid;
    }

    /**
     * 内部异常：邀请码无效（不存在 / 已用 / 已过期）。
     * Logic 层捕获后转 StatusRuntimeException(PERMISSION_DENIED)。
     */
    public static class InviteUnavailableException extends RuntimeException {
        public InviteUnavailableException(String message) { super(message); }
    }

    /**
     * 内部异常：用户名已被占用。
     * Logic 层捕获后转 StatusRuntimeException(ALREADY_EXISTS)。
     */
    public static class UsernameTakenException extends RuntimeException {
        public UsernameTakenException(String message) { super(message); }
    }
}