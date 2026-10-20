package com.rag.users.user.rpc.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.exception.UserExceptions.NotFoundException;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
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

    /**
     * 注意 extra_info（jsonb）的 TypeHandler：
     * 手写 {@code @Select} 必须显式带 {@code @Results} 才能把 jsonb 列正确读成 Map：
     * MyBatis 不认识 MyBatis-Plus 的 {@code @TableField}，实体字段上的 TypeHandler
     * 只对 MP 自己生成的 SQL 生效。不声明的话 extraInfo 会静默变成 null（不报错）。
     *
     * <p>TypeHandler 用 JsonbTypeHandler 而非 MP 自带的 JacksonTypeHandler：
     * 后者 setString 会把 jsonb 列绑成 varchar，Postgres 直接拒收。
     */
    @Select("SELECT * FROM users_user WHERE username = #{username} LIMIT 1")
    @Results({
            @Result(property = "extraInfo", column = "extra_info",
                    javaType = java.util.Map.class, typeHandler = JsonbTypeHandler.class)
    })
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
    @Results({
            @Result(property = "extraInfo", column = "extra_info",
                    javaType = java.util.Map.class, typeHandler = JsonbTypeHandler.class)
    })
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
     * <p><b>update_time 必须在 SET 子句里显式带上</b>（DRIFT-001 修复）：
     * <ul>
     *   <li>Go 出处：{@code service/user/user/rpc/internal/model/data_model.go:15}
     *       {@code UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`}。
     *       GORM 的 autoUpdateTime 会在每次 {@code Updates()} 时把该列写进 SET 子句。</li>
     *   <li>原 RAG 实现的注释写的是「依赖 Postgres CURRENT_TIMESTAMP 在 row-level 触发」——
     *       这句话在事实层面不成立：普通 UPDATE 不含该列时，Postgres 不会触碰它；
     *       本仓既无 MetaObjectHandler（本模块零命中，message-rpc 才有），
     *       Flyway DDL 也只有 {@code DEFAULT CURRENT_TIMESTAMP}（仅 INSERT 时生效）、无 trigger。
     *       结果：RAG 的 update_time 自注册起永不推进，而 Go 会。</li>
     *   <li>修法：与 extra_info 的 {@code setSql(CAST(...))} 同款，在 wrapper 上显式 setSql。
     *       放在 {@code any} 判定<b>之后</b>，保证「无字段变更则不发 UPDATE」这一 Go 语义不变
     *       （GORM 对全零值 struct 同样不发 UPDATE）。</li>
     * </ul>
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
            // ⚠️ 不能用 wrapper.set(UserEntity::getExtraInfo, map)：
            //    LambdaUpdateWrapper.set 把参数以 Object 形式塞进 ew.paramNameValuePairs，
            //    不走实体字段上的 @TableField(typeHandler = JsonbTypeHandler.class)，
            //    MyBatis 只能按 javaType=Object 猜 handler → 绑成 varchar / hstore，
            //    Postgres 对 jsonb 列直接拒收（"column extra_info is of type jsonb but
            //    expression is of type character varying"），UpdateUser 必现失败。
            //    正确做法与 admin-rpc 端 AdminDomainService 第 268/705 行同款：
            //    直接写 SQL 片段 + 显式 CAST，参数按 String 绑定再转 jsonb。
            //    （实体上的 typeHandler 只对 MP 自己生成的 insert/updateById 生效。）
            wrapper.setSql("extra_info = CAST({0} AS jsonb)", toJson(newUser.getExtraInfo()));
            any = true;
        }
        if (!any) return 0;
        // DRIFT-001：对齐 GORM autoUpdateTime —— 只有真的发出 UPDATE 时才推进 update_time
        wrapper.setSql("update_time = CURRENT_TIMESTAMP");
        return update(null, wrapper);
    }

    /**
     * Map → JSON 文本，供 {@code wrapper.setSql("extra_info = CAST({0} AS jsonb)", ...)} 使用。
     *
     * <p>刻意不在这里吞异常：序列化失败属于编程/数据错误，向上抛由 domain 层映射成
     * {@code Status.INTERNAL "JSON序列化失败"}，与 admin-rpc 端 {@code toJson} 行为一致。
     */
    private static String toJson(java.util.Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("extraInfo JSON serialize failed", e);
        }
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