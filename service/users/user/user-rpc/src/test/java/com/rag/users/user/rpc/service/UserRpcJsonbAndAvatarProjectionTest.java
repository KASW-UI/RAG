package com.rag.users.user.rpc.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * user-rpc 两个已确认缺陷的回归测试：
 * <ol>
 *   <li>{@code updateUserById} 写 jsonb 曾用 {@code wrapper.set(Entity::getExtraInfo, map)}，
 *       该参数以 Object 形式进 {@code ew.paramNameValuePairs}，<b>不走</b>实体上的
 *       {@code @TableField(typeHandler = JsonbTypeHandler.class)}，Postgres 对 jsonb 列直接拒收</li>
 *   <li>{@code selectAvatarHistory} 返回的是 UPDATE 之前读到的实体，
 *       响应里的 {@code is_current} 会是旧值，与刚落库的 true 自相矛盾</li>
 * </ol>
 */
class UserRpcJsonbAndAvatarProjectionTest {

    @BeforeAll
    static void initMyBatisPlusTableInfo() throws Exception {
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        java.lang.reflect.Method initMethod = TableInfoHelper.class.getDeclaredMethod(
                "initTableInfo", org.apache.ibatis.session.Configuration.class, String.class, Class.class);
        initMethod.setAccessible(true);
        initMethod.invoke(null, config, "com.rag.users.user.rpc.mapper.UserMapper", UserEntity.class);
        initMethod.invoke(null, config, "com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper",
                UserAvatarHistoryEntity.class);
    }

    // ================== 缺陷 1：extra_info jsonb 写入 ==================

    @Test
    @DisplayName("updateUserById 写 extra_info 必须走 CAST(... AS jsonb)，不能是普通 set 占位符")
    @SuppressWarnings({"unchecked", "unused"})
    void updateUserById_extraInfoUsesJsonbCast() {
        // CALLS_REAL_METHODS：让 interface 的 default 方法（updateUserById）真跑，
        // 只有 BaseMapper.update 这类抽象方法被替换掉。
        UserMapper mapper = Mockito.mock(UserMapper.class, Mockito.CALLS_REAL_METHODS);

        UserEntity patch = new UserEntity();
        patch.setExtraInfo(Map.of("avatar", "https://cdn/x.png"));

        mapper.updateUserById(1001L, patch);

        ArgumentCaptor<LambdaUpdateWrapper<UserEntity>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        Mockito.verify(mapper).update(Mockito.isNull(), captor.capture());

        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("CAST(") && sqlSet.toUpperCase().contains("JSONB"),
                "extra_info 必须以 setSql(CAST(... AS jsonb)) 写入。实际 SET = " + sqlSet);
    }

    @Test
    @DisplayName("extra_info 为空时不做任何写入（保持 D-017 部分更新语义）")
    @SuppressWarnings({"unchecked", "unused"})
    void updateUserById_noFields_isNoOp() {
        UserMapper mapper = Mockito.mock(UserMapper.class, Mockito.CALLS_REAL_METHODS);

        int affected = mapper.updateUserById(1001L, new UserEntity());

        assertEquals(0, affected, "没有任何字段变更时应直接返回 0，不发 UPDATE");
        Mockito.verify(mapper, Mockito.never()).update(Mockito.any(), Mockito.any());
    }

    // ================== 缺陷 2：SelectAvatar 响应 is_current 投影 ==================

    @Test
    @DisplayName("selectAvatarHistory 返回的实体 is_current 必须是 true（不能是更新前的旧值）")
    @SuppressWarnings({"unchecked", "unused"})
    void selectAvatarHistory_returnedEntityHasIsCurrentTrue() {
        UserMapper userMapper = Mockito.mock(UserMapper.class);
        UserEntity user = new UserEntity();
        user.setUid(1001L);
        user.setExtraInfo(new java.util.HashMap<>());
        Mockito.when(userMapper.selectByUidRaw(1001L)).thenReturn(user);

        // 这条 history 此前 is_current=false
        UserAvatarHistoryEntity history = new UserAvatarHistoryEntity();
        history.setId(77L);
        history.setHistoryId("H-77");
        history.setAvatarUrl("https://cdn/77.png");
        history.setIsCurrent(false);

        UserAvatarHistoryMapper mapper =
                Mockito.mock(UserAvatarHistoryMapper.class, Mockito.CALLS_REAL_METHODS);
        Mockito.when(mapper.selectByUidAndHistoryIdRaw(1001L, "H-77")).thenReturn(history);

        UserAvatarHistoryEntity returned = mapper.selectAvatarHistory(userMapper, 1001L, "H-77");

        assertTrue(returned.getIsCurrent(),
                "响应里的 is_current 必须回写为 true —— DB 已改，不回写就会出现响应与库不一致");
        // 同时确认确实发出了标记 current 的 UPDATE
        Mockito.verify(mapper).markCurrentById(77L);
        assertFalse(Boolean.FALSE.equals(returned.getIsCurrent()),
                "返回值不应仍是 false");
    }
}