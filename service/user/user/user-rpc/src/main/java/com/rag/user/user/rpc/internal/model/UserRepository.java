package com.rag.user.user.rpc.internal.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 对应 Go: internal/model/user.go 中的 type UserModel struct
 *
 * Go 端 UserModel 是手写 DAO,gorm.WithContext(ctx).Where(...).First(&user).Error 的样板,
 * 字段级更新、Insert、Delete 都自己封一遍。
 *
 * Java 端用 Spring Data JPA —— JpaRepository 自动给我们 CRUD(findById / save / deleteById),
 * 唯一需要手写的是按业务字段查询的 finder 和按业务字段删除的 @Modifying。
 *
 * 注意:JpaRepository 注入后是线程安全的,Logic 里通过 @Autowired 共享一个实例,
 * 这一点和 Go 的 *UserModel 由 ServiceContext 持有单例是同一种设计。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 对应 Go: UserModel.FindOneByUserName(ctx, username)
     * Spring Data 派生方法命名规则:findBy + 属性名 → 自动 WHERE username = ?
     */
    Optional<User> findByUsername(String username);

    /**
     * 对应 Go: UserModel.FindOneByUid(ctx, uid)
     */
    Optional<User> findByUid(Long uid);

    /**
     * 对应 Go: UserModel.DeleteUserByUid(ctx, uid) —— DELETE FROM users WHERE uid = ?
     * @Modifying + @Query 显式写 SQL,绕过 Spring Data 派生方法对 uid 的歧义解析(uid 是业务字段,不是主键)。
     */
    @Modifying
    @Query("DELETE FROM User u WHERE u.uid = :uid")
    int deleteByUid(@Param("uid") Long uid);
}