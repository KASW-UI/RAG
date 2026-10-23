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
     *
     * <p>🔴 <b>2026-10-23（DRIFT-USERRPC-V-009）：create_time / update_time 必须由应用侧绑定，不能用 {@code CURRENT_TIMESTAMP}</b>。
     * <p>Go 出处：{@code service/user/user/rpc/internal/model/user.go:86-95}
     * <pre>
     *   history = UserAvatarHistory{ …, IsCurrent: true }
     *   if err := tx.Create(&history).Error; err != nil { return err }
     * </pre>
     * GORM 的 {@code autoCreateTime / autoUpdateTime} 会在 <b>Create 之前</b>把这两个字段
     * 写成应用侧时钟（{@code gorm.io/gorm/callbacks/create.go} 的 {@code ConvertToCreateValues}
     * 用 {@code stmt.DB.NowFunc()}），并把它们<b>随 INSERT 一起写进库里</b>；
     * 由于传进去的是 {@code &history}（指针），<b>同一个结构体被回填</b> ⇒
     * {@code avatarHistoryItemFromModel(&history)} 拿到的是<b>真实时刻</b>，
     * 于是 {@code UploadAvatarResp.history.create_time} 是一个非空 RFC3339 字符串。
     * <p>修复前 RAG 用 {@code CURRENT_TIMESTAMP}（PG 时钟）写库，但<b>实体对象没被回填</b>
     * ⇒ 返回的实体 {@code createTime == null} ⇒ {@code fromModel} 不 set ⇒
     * <b>wire 上 create_time 是空串</b>。
     * <p>实测（双侧活体对拍，2026-10-23 21:01）：
     * <pre>
     *   Go  : "2026-10-23T21:01:48+08:00"
     *   RAG : ""                              ← 修复前
     * </pre>
     * <p>⇒ 修法与 Go 同构：<b>应用侧时钟写库 + 同一个实体回填</b>，
     * 保证「库里的值」与「响应里的值」是同一时刻（Go 也是如此）。
     */
    @Insert("INSERT INTO users_user_avatar_history " +
            "(history_id, uid, avatar_url, content_type, size_bytes, is_current, create_time, update_time) " +
            "VALUES (#{historyId}, #{uid}, #{avatarUrl}, #{contentType}, #{sizeBytes}, #{isCurrent}, " +
            "#{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertHistory(UserAvatarHistoryEntity history);

    /**
     * UPDATE user.extra_info["avatar"] = avatarURL（D-014：双写 user 表的 JSON 字段）。
     * 对应 createAvatarHistory W4 / selectAvatarHistory W5。
     *
     * <p>⚠️ {@code CAST(... AS jsonb)} 不能省：参数是 Java String（JDBC 绑成 varchar），
     * 而列类型是 jsonb，Postgres 会直接拒绝：
     * {@code ERROR: column "extra_info" is of type jsonb but expression is of type character varying}。
     * 这个错误发生在 4/5 步原子事务的最后一步，会让 uploadAvatar / selectAvatar 直接返 INTERNAL。
     */
    @Update("UPDATE users_user SET extra_info = CAST(#{extraInfoJson} AS jsonb), " +
            "update_time = CURRENT_TIMESTAMP WHERE uid = #{uid}")
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
        // 🔴 DRIFT-USERRPC-V-009：应用侧时钟写库 + 同一实体回填（Go 的 GORM autoCreateTime/autoUpdateTime 同构）。
        //   create_time 是**唯一 wire 可见**的时间字段（proto AvatarHistoryItem.create_time），
        //   修复前实体不回填 ⇒ 响应里 create_time 是空串。
        //   Go 侧 GORM 在 Create 之前把两个字段都设成 stmt.DB.NowFunc()（应用侧时钟），
        //   这里同样用**同一个** now 赋给 create/update_time —— Go 的 autoCreateTime 与
        //   autoUpdateTime 在 Create 路径上取的是同一个 curTime。
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        history.setCreateTime(now);
        history.setUpdateTime(now);
        insertHistory(history);

        // W4 UPDATE user.extra_info["avatar"] = avatarURL（D-014：双写）
        Map<String, String> extra = user.getExtraInfo();
        if (extra == null) extra = new HashMap<>();
        extra.put("avatar", avatarUrl);
        // 序列化成 JSON 字符串交给 updateUserExtraInfo（SQL 里 CAST 成 jsonb）
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra);
            updateUserExtraInfo(uid, json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize extraInfo to JSON", e);
        }
        // 同步内存副本，保持与 DB 一致（user 是局部变量，仅为可读性）
        user.setExtraInfo(extra);
        // 注意：这里<b>不能</b>再调 userMapper.updateById(user) 做「第二次写入」。
        //   1. updateUserExtraInfo 已经把同一份数据写进去了，重复写没有意义；
        //   2. MyBatis-Plus 生成的 updateById 走 @TableField(typeHandler=JacksonTypeHandler)，
        //      而 JacksonTypeHandler 是用 setString 绑参的，Postgres 会拒绝
        //      varchar → jsonb：ERROR: column "extra_info" is of type jsonb but expression
        //      is of type character varying → 整个事务回滚，uploadAvatar 永远失败。

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
        // ⚠️ 必须同步回写内存实体：W4 的 UPDATE 只改 DB，不改这里持有的对象。
        //    若不写回，返回给调用方的 SelectAvatarResp.history.is_current 会是
        //    "更新前" 的旧值（通常是 false），与刚落库的 is_current=true 自相矛盾，
        //    而且紧接着调 GetAvatarHistory 又会读到 true。
        //    参考侧 DataModel.selectAvatarHistory 是先 history.setIsCurrent(true)
        //    再返回同一个托管实体，所以响应恒为 true。
        history.setIsCurrent(true);

        // W5
        Map<String, String> extra = user.getExtraInfo();
        if (extra == null) extra = new HashMap<>();
        extra.put("avatar", history.getAvatarUrl());
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra);
            updateUserExtraInfo(uid, json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize extraInfo to JSON", e);
        }
        // 同 createAvatarHistory：不再补一次 updateById（理由见该方法内注释）

        return history;
    }
}