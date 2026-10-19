package com.rag.users.user.rpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.exception.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.exception.UserExceptions.NotFoundException;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * users_user_avatar_history 表的 MyBatis-Plus Mapper。
 *
 * 对应 Go: service/user/model/user.go 的 UserAvatarHistory struct + UserAvatarHistoryModel 方法集。
 *
 * 框架职责:
 *   - 与 UserMapper 同套路
 *   - 取代了原 internal.model.DataModel 里 user_avatar_history 相关的 EntityManager 调用
 *   - 4 步原子（createAvatarHistory）和 5 步原子（selectAvatarHistory）用 Spring @Transactional
 *     保证 D-005 / D-015：必须原子；history 查询必须带 uid 条件（防越权）
 *
 * 注：本 mapper 上挂的 @Transactional 方法需要 Spring AOP 代理；
 *     MyBatis-Plus mapper 是接口，由 MybatisAutoConfiguration 注册的 SqlSessionTemplate 提供事务管理。
 */
@Mapper
public interface UserAvatarHistoryMapper extends BaseMapper<UserAvatarHistoryEntity> {

    /**
     * 对应 Go 端 ListAvatarHistory：
     *   limit <= 0 || limit > 30  → 默认 30（D-010：静默 clamp，不抛异常）
     *   ORDER BY create_time DESC
     *
     * 用 Java 端做 clamp（替代原 DataModel.listAvatarHistory 里的 if 逻辑）。
     */
    default List<UserAvatarHistoryEntity> listAvatarHistory(Long uid, int limit) {
        if (limit <= 0 || limit > 30) limit = 30;
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserAvatarHistoryEntity>()
                .eq("uid", uid)
                .orderByDesc("create_time")
                .last("LIMIT " + limit));
    }

    /**
     * 对应 Go 端 selectAvatarHistory W2：
     *   查 history（带 uid 隔离条件；不存在 → AvatarHistoryNotFoundException）
     */
    @Select("SELECT * FROM users_user_avatar_history WHERE uid = #{uid} AND history_id = #{historyId} LIMIT 1")
    UserAvatarHistoryEntity selectByUidAndHistoryIdRaw(
            @Param("uid") Long uid, @Param("historyId") String historyId);

    default UserAvatarHistoryEntity findOneByUidAndHistoryId(Long uid, String historyId) {
        UserAvatarHistoryEntity h = selectByUidAndHistoryIdRaw(uid, historyId);
        if (h == null) {
            throw new AvatarHistoryNotFoundException(
                    "avatar history not found: uid=" + uid + " historyId=" + historyId);
        }
        return h;
    }

    /**
     * UPDATE 旧 current → false（uid 范围内所有 is_current=true 的行）。
     * 对应 createAvatarHistory W2 和 selectAvatarHistory W3。
     */
    @Update("UPDATE users_user_avatar_history SET is_current = false, update_time = CURRENT_TIMESTAMP " +
            "WHERE uid = #{uid} AND is_current = true")
    int clearCurrentByUid(@Param("uid") Long uid);

    /**
     * UPDATE 目标 history → is_current=true。
     * 对应 selectAvatarHistory W4。
     */
    @Update("UPDATE users_user_avatar_history SET is_current = true, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int markCurrentById(@Param("id") Long id);

    /**
     * INSERT 新 history（is_current=true）。
     * 对应 createAvatarHistory W3。
     */
    @Insert("INSERT INTO users_user_avatar_history " +
            "(history_id, uid, avatar_url, content_type, size_bytes, is_current, create_time, update_time) " +
            "VALUES (#{historyId}, #{uid}, #{avatarUrl}, #{contentType}, #{sizeBytes}, #{isCurrent}, " +
            "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertHistory(UserAvatarHistoryEntity history);

    /**
     * UPDATE user.extra_info["avatar"] = avatarURL（D-014：双写 user 表的 JSON 字段）。
     * 对应 createAvatarHistory W4 / selectAvatarHistory W5。
     * 用 LambdaUpdateWrapper 在 extra_info 上合并键。
     *
     * 注意：MyBatis-Plus updateWithWrapper 对 Map<String, String> 字段的合并写入有限制，
     * 这里用原生 UPDATE 把 extra_info 当作 JSON 整体覆盖（GORM 端也是 merge 后覆盖）。
     */
    @Update("UPDATE users_user SET extra_info = #{extraInfoJson}, update_time = CURRENT_TIMESTAMP WHERE uid = #{uid}")
    int updateUserExtraInfo(@Param("uid") Long uid, @Param("extraInfoJson") String extraInfoJson);

    /**
     * 4 步原子事务，对应 Go 端 m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {...})。
     *
     * 这里把 @Transactional 放在 default 方法上 —— MyBatis-Plus mapper 接口走 JDK 动态代理，
     * Spring 的事务拦截对 default 方法同样生效（前提是 mapper bean 是被 Spring 管理的）。
     *
     * D-005：4 步必须原子，任何一步失败整体回滚。
     *
     * @param userMapper 用于双写 users.extra_info（D-014）
     */
    @org.springframework.transaction.annotation.Transactional
    default UserAvatarHistoryEntity createAvatarHistory(
            UserMapper userMapper,
            Long uid, String historyId, String avatarUrl, String contentType, Long sizeBytes) {
        // W1 查 user
        UserEntity user = userMapper.selectByUidRaw(uid);
        if (user == null) {
            throw new NotFoundException("user not found by uid: " + uid);
        }

        // W2 UPDATE 旧 current → false
        clearCurrentByUid(uid);

        // W3 INSERT 新 history
        UserAvatarHistoryEntity history = new UserAvatarHistoryEntity();
        history.setHistoryId(historyId);
        history.setUid(uid);
        history.setAvatarUrl(avatarUrl);
        history.setContentType(contentType);
        history.setSizeBytes(sizeBytes);
        history.setIsCurrent(true);
        insertHistory(history);

        // W4 UPDATE user.extra_info["avatar"] = avatarURL（D-014：双写）
        Map<String, String> extra = user.getExtraInfo();
        if (extra == null) extra = new HashMap<>();
        extra.put("avatar", avatarUrl);
        // 序列化为 JSON 字符串 —— JacksonTypeHandler 在 mapper 层做序列化，
        // 这里我们手动做一次以保证 JSON 内容正确（TypeHandler 是在 INSERT/UPDATE 时自动用的）
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra);
            updateUserExtraInfo(uid, json);
            // 把 user 实体的 extraInfo 也同步一下（保持内存与 DB 一致）
            user.setExtraInfo(extra);
            userMapper.updateById(user);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize extraInfo to JSON", e);
        }

        return history;
    }

    /**
     * 5 步原子事务，对应 Go 端 SelectAvatarHistory：
     *   W1 查 user（不存在 → NotFoundException）
     *   W2 查 history（带 uid 隔离；不存在 → AvatarHistoryNotFoundException）
     *   W3 UPDATE 旧 current → false
     *   W4 UPDATE 目标 history → is_current=true
     *   W5 UPDATE user.extra_info["avatar"] = history.AvatarURL（D-014）
     *
     * D-005 / D-015：必须原子；history 查询必须带 uid 条件（防越权取别人头像）。
     */
    @org.springframework.transaction.annotation.Transactional
    default UserAvatarHistoryEntity selectAvatarHistory(UserMapper userMapper, Long uid, String historyId) {
        // W1
        UserEntity user = userMapper.selectByUidRaw(uid);
        if (user == null) {
            throw new NotFoundException("user not found by uid: " + uid);
        }

        // W2
        UserAvatarHistoryEntity history = findOneByUidAndHistoryId(uid, historyId);

        // W3
        clearCurrentByUid(uid);

        // W4
        markCurrentById(history.getId());

        // W5
        Map<String, String> extra = user.getExtraInfo();
        if (extra == null) extra = new HashMap<>();
        extra.put("avatar", history.getAvatarUrl());
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra);
            updateUserExtraInfo(uid, json);
            user.setExtraInfo(extra);
            userMapper.updateById(user);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize extraInfo to JSON", e);
        }

        return history;
    }
}