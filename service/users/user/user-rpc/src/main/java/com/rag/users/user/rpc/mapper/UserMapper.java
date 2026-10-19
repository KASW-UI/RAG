package com.rag.users.user.rpc.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.exception.UserExceptions.NotFoundException;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * users_user 表的 MyBatis-Plus Mapper。
 *
 * 对应 Go: service/user/model/user.go 的 User struct + UserModel 方法集（GORM）。
 *
 * 框架职责:
 *   - @Mapper（org.apache.ibatis）告诉 MyBatis-Plus 这是一个 mapper 接口
 *   - extends BaseMapper<UserEntity> 自动获得 selectById / insert / updateById / deleteById / selectList(...) 等 CRUD
 *   - 自定义 @Select / @Update / @Delete 注解方法一对一对应原 DataModel JPQL（保持 Go parity 命名）
 *   - 取代了原 internal.model.DataModel 里散落的 EntityManager 调用
 *
 * NotFoundException 抛出在 mapper 层（D-001）：调用方 catch 后映射成 gRPC Status.NOT_FOUND / Status.INTERNAL。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM users_user WHERE username = #{username} LIMIT 1")
    UserEntity selectByUsernameRaw(@Param("username") String username);

    /**
     * 对应 Go 端 UserModel.FindOneByUserName。
     * 用 Optional 包装显式表达"找不到"语义；调用方应 .orElseThrow(() -> new NotFoundException(...))
     */
    default UserEntity findOneByUserName(String username) {
        UserEntity u = selectByUsernameRaw(username);
        if (u == null) throw new NotFoundException("user not found by username: " + username);
        return u;
    }

    @Select("SELECT * FROM users_user WHERE uid = #{uid} LIMIT 1")
    UserEntity selectByUidRaw(@Param("uid") Long uid);

    default UserEntity findOneByUid(Long uid) {
        UserEntity u = selectByUidRaw(uid);
        if (u == null) throw new NotFoundException("user not found by uid: " + uid);
        return u;
    }

    @Delete("DELETE FROM users_user WHERE uid = #{uid}")
    int deleteUserByUid(@Param("uid") Long uid);

    /**
     * 对应 Go 端 GORM 的 Updates(newUser)——GORM 默认跳过零值（""/0/false），
     * 等价于 Java 端只把"显式传入且非空"的字段加入 UPDATE 子句。
     *
     * D-017 决策点：必须只更新 Username/Password/Email/ExtraInfo 四列。
     * 用 MyBatis-Plus LambdaUpdateWrapper 动态拼 set 子句（取代原 DataModel 的动态 JPQL）。
     *
     * ⚠️ 注意：调用方需保证 updateTime 在 DB 端自动维护（Flyway DDL 有 DEFAULT CURRENT_TIMESTAMP，
     *          且 MyBatis-Plus 默认 fill = UPDATE 时填充 update_time，需在 MetaObjectHandler 配置；
     *          当前实现依赖 Postgres CURRENT_TIMESTAMP 在 row-level 触发）
     */
    default int updateUserById(Long uid, UserEntity newUser) {
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserEntity::getUid, uid);
        boolean any = false;
        if (newUser.getUsername() != null && !newUser.getUsername().isEmpty()) {
            wrapper.set(UserEntity::getUsername, newUser.getUsername());
            any = true;
        }
        if (newUser.getPassword() != null && !newUser.getPassword().isEmpty()) {
            wrapper.set(UserEntity::getPassword, newUser.getPassword());
            any = true;
        }
        if (newUser.getEmail() != null && !newUser.getEmail().isEmpty()) {
            wrapper.set(UserEntity::getEmail, newUser.getEmail());
            any = true;
        }
        if (newUser.getExtraInfo() != null) {
            wrapper.set(UserEntity::getExtraInfo, newUser.getExtraInfo());
            any = true;
        }
        if (!any) return 0;
        return update(null, wrapper);
    }

    /**
     * 找出 user 的当前头像（is_current=true 且 uid 匹配）。
     * 对应原 DataModel.createAvatarHistory W2 步骤（UPDATE 旧 current → false）。
     */
    @Select("SELECT * FROM users_user_avatar_history WHERE uid = #{uid} AND is_current = true LIMIT 1")
    com.rag.users.user.rpc.domain.UserAvatarHistoryEntity selectCurrentByUid(
            @Param("uid") Long uid);

    default Optional<com.rag.users.user.rpc.domain.UserAvatarHistoryEntity> findCurrentAvatar(Long uid) {
        return Optional.ofNullable(selectCurrentByUid(uid));
    }
}