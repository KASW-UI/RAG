package com.rag.users.user.rpc.internal.model;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.UserExceptions.AvatarHistoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access layer，对应 Go 端 internal/model/data_model.go 的 UserModel struct。
 *
 * Go 用 GORM；Java 用 JPA EntityManager 直接调 JPQL。
 *
 * 关键语义映射：
 *   - Go 的 sentinel error（ErrorNotFound / ErrorAvatarHistoryNotFound）
 *     → Java 的 NotFoundException / AvatarHistoryNotFoundException（抛异常）
 *   - Go 的 m.conn.WithContext(ctx).Transaction(...)
 *     → Java 的 @Transactional（方法级声明）
 *   - Go 的 GORM Updates(newUser) 跳过零值字段
 *     → Java 的 updateUserById 显式只构造非空字段（见方法内）
 *
 * 框架职责：
 *   - Go：gorm.DB 提供 Where / First / Create / Updates / Delete / Transaction 一站式
 *   - Java：JPA EntityManager 用 createQuery / persist / merge / executeUpdate 拆开
 */
@Service
public class DataModel {

    @PersistenceContext
    private EntityManager em;

    // ============== 用户表操作（对应 Go UserModel 的同名方法）==============

    public User findOneByUserName(String username) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :u", User.class)
                    .setParameter("u", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("user not found by username: " + username);
        }
    }

    public User findOneByUid(Long uid) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.uid = :uid", User.class)
                    .setParameter("uid", uid)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("user not found by uid: " + uid);
        }
    }

    /**
     * 对应 Go 端 m.conn.WithContext(ctx).Create(user).Error。
     * Hibernate 自动维护 createTime / updateTime（如未手动设置）。
     */
    public void insert(User user) {
        em.persist(user);
    }

    public void deleteUserByUid(Long uid) {
        em.createQuery("DELETE FROM User u WHERE u.uid = :uid")
                .setParameter("uid", uid)
                .executeUpdate();
    }

    /**
     * 对应 Go 端 GORM 的 Updates(newUser)——GORM 默认跳过零值（""/0/false），
     * 等价于 Java 端只把"显式传入且非空"的字段加入 UPDATE 子句。
     *
     * D-017 决策点：必须只更新 Username/Password/Email/ExtraInfo 四列。
     * Java 用动态 JPQL 拼接实现 GORM 的"跳零值"行为。
     */
    @Transactional
    public void updateUserById(Long uid, User newUser) {
        StringBuilder jpql = new StringBuilder("UPDATE User u SET u.uid = u.uid");
        boolean hasUsername = newUser.getUsername() != null && !newUser.getUsername().isEmpty();
        boolean hasPassword = newUser.getPassword() != null && !newUser.getPassword().isEmpty();
        boolean hasEmail = newUser.getEmail() != null && !newUser.getEmail().isEmpty();
        boolean hasExtraInfo = newUser.getExtraInfo() != null;

        if (hasUsername) jpql.append(", u.username = :username");
        if (hasPassword) jpql.append(", u.password = :password");
        if (hasEmail)    jpql.append(", u.email = :email");
        if (hasExtraInfo) jpql.append(", u.extraInfo = :extraInfo");
        jpql.append(" WHERE u.uid = :uid");

        var query = em.createQuery(jpql.toString()).setParameter("uid", uid);
        if (hasUsername) query.setParameter("username", newUser.getUsername());
        if (hasPassword) query.setParameter("password", newUser.getPassword());
        if (hasEmail)    query.setParameter("email", newUser.getEmail());
        if (hasExtraInfo) query.setParameter("extraInfo", newUser.getExtraInfo());
        query.executeUpdate();
    }

    // ============== 头像历史表操作 ==============

    /**
     * 对应 Go 端 ListAvatarHistory：
     *   limit <= 0 || limit > 30  → 默认 30（D-010：静默 clamp，不抛异常）
     *   ORDER BY create_time DESC
     */
    public List<UserAvatarHistory> listAvatarHistory(Long uid, int limit) {
        if (limit <= 0 || limit > 30) limit = 30;
        return em.createQuery(
                        "SELECT h FROM UserAvatarHistory h WHERE h.uid = :uid ORDER BY h.createTime DESC",
                        UserAvatarHistory.class)
                .setParameter("uid", uid)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * 4 步原子事务，对应 Go 端 m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {...})：
     *   W1 查 user（不存在 → NotFoundException）
     *   W2 UPDATE 旧 current → false（uid 范围内所有 is_current=true 的行）
     *   W3 INSERT 新 history（is_current=true）
     *   W4 UPDATE user.extra_info["avatar"] = avatarURL（D-014：双写 user 表的 JSON 字段）
     *
     * D-005：4 步必须原子，任何一步失败整体回滚。
     */
    @Transactional
    public UserAvatarHistory createAvatarHistory(
            Long uid, String historyId, String avatarUrl, String contentType, Long sizeBytes) {
        User user = findOneByUid(uid);

        em.createQuery("UPDATE UserAvatarHistory h SET h.isCurrent = false " +
                        "WHERE h.uid = :uid AND h.isCurrent = true")
                .setParameter("uid", uid)
                .executeUpdate();

        UserAvatarHistory history = new UserAvatarHistory();
        history.setHistoryId(historyId);
        history.setUid(uid);
        history.setAvatarUrl(avatarUrl);
        history.setContentType(contentType);
        history.setSizeBytes(sizeBytes);
        history.setIsCurrent(true);
        em.persist(history);

        Map<String, String> extra = user.getExtraInfo();
        if (extra == null) {
            extra = new HashMap<>();
            user.setExtraInfo(extra);
        }
        extra.put("avatar", avatarUrl);
        em.merge(user);

        return history;
    }

    /**
     * 5 步原子事务，对应 Go 端 SelectAvatarHistory：
     *   W1 查 user（不存在 → NotFoundException，对应 Go ErrorNotFound）
     *   W2 查 history（带 uid 隔离条件；不存在 → AvatarHistoryNotFoundException，对应 Go ErrorAvatarHistoryNotFound）
     *   W3 UPDATE 旧 current → false
     *   W4 UPDATE 目标 history → is_current=true
     *   W5 UPDATE user.extra_info["avatar"] = history.AvatarURL（D-014）
     *
     * D-005 / D-015：必须原子；history 查询必须带 uid 条件（防越权取别人头像）。
     */
    @Transactional
    public UserAvatarHistory selectAvatarHistory(Long uid, String historyId) {
        User user = findOneByUid(uid);

        UserAvatarHistory history;
        try {
            history = em.createQuery(
                            "SELECT h FROM UserAvatarHistory h " +
                            "WHERE h.uid = :uid AND h.historyId = :hid",
                            UserAvatarHistory.class)
                    .setParameter("uid", uid)
                    .setParameter("hid", historyId)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new AvatarHistoryNotFoundException(
                    "avatar history not found: uid=" + uid + " historyId=" + historyId);
        }

        em.createQuery("UPDATE UserAvatarHistory h SET h.isCurrent = false " +
                        "WHERE h.uid = :uid AND h.isCurrent = true")
                .setParameter("uid", uid)
                .executeUpdate();

        history.setIsCurrent(true);
        em.merge(history);

        Map<String, String> extra = user.getExtraInfo();
        if (extra == null) {
            extra = new HashMap<>();
            user.setExtraInfo(extra);
        }
        extra.put("avatar", history.getAvatarUrl());
        em.merge(user);

        return history;
    }
}