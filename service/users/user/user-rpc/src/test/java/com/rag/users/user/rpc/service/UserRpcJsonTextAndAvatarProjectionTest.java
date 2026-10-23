package com.rag.users.user.rpc.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import org.apache.ibatis.annotations.Update;
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
 *   <li>{@code updateUserById} 写 extra_info 曾用 {@code wrapper.set(Entity::getExtraInfo, map)}，
 *       该参数以 Object 形式进 {@code ew.paramNameValuePairs}，<b>不走</b>实体上的
 *       {@code @TableField(typeHandler = JsonTextTypeHandler.class)}，序列化结果不受控。
 *       <br>⚠️ <b>2026-10-23（DRIFT-USERRPC-V-003）本条的判据已整体反转</b>：
 *       原来要求 SQL 里<b>必须</b>出现 {@code CAST(... AS jsonb)}（那是为 jsonb 列被迫加的）；
 *       列改回 text 后 CAST 必须在<b>任何路径上都不出现</b> —— 它不会报错，
 *       只是悄悄把落库字节规范化一遍，与 Go 存的不一致。</li>
 *   <li>{@code selectAvatarHistory} 返回的是 UPDATE 之前读到的实体，
 *       响应里的 {@code is_current} 会是旧值，与刚落库的 true 自相矛盾</li>
 * </ol>
 */
class UserRpcJsonTextAndAvatarProjectionTest {

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

    // ================== 缺陷 1：extra_info 写入路径不得残留 CAST(... AS jsonb) ==================

    /**
     * 🔴 <b>DRIFT-USERRPC-V-003 修复后判据反转</b>：extra_info 是 text 列，
     * {@code updateUserById} 必须以 {@code setSql("extra_info = {0}", json)} 写入，
     * <b>SQL 片段里不得再出现 CAST 或 jsonb</b>。
     *
     * <p>为什么这条要在<b>mock 层</b>断言 CAST 的缺席，而把「字节是否被改写」
     * 交给真 PG 的 {@code UserRpcContainerWiringTest#extraInfoBytesAreStoredVerbatim}：
     * 两层各管一半。mock 层能精确定位「SQL 文本里有没有 CAST」，
     * 真 PG 层才能证伪「CAST 缺席 ⇒ 字节没被动过」这条推理本身没断。
     * 只有 mock 层 ⇒ 一段无害的 CAST 能过；只有 PG 层 ⇒ 失败信息看不出是哪条路径带进来的。
     */
    @Test
    @DisplayName("updateUserById 写 extra_info 不得残留 CAST(... AS jsonb)（text 列）")
    @SuppressWarnings({"unchecked", "unused"})
    void updateUserById_extraInfoHasNoJsonbCast() {
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
        String upper = sqlSet.toUpperCase();
        assertFalse(upper.contains("CAST("),
                "extra_info 是 text 列，CAST 不该再出现（它不报错但会规范化落库字节）。实际 SET = " + sqlSet);
        assertFalse(upper.contains("JSONB"),
                "extra_info 是 text 列，SQL 片段里不该再出现 jsonb。实际 SET = " + sqlSet);
        assertTrue(sqlSet.contains("extra_info = "),
                "extra_info 仍应被显式 set（不能退回 wrapper.set 的占位符路径）。实际 SET = " + sqlSet);
    }

    /**
     * 🔴 <b>补上面那条留下的缺口（2026-10-23 自查）</b>：
     * {@code UserAvatarHistoryMapper.updateUserExtraInfo} 是**另一条**写 extra_info 的路径
     * （头像双写 D-014），原先带着 {@code CAST(#{extraInfoJson} AS jsonb)}。
     *
     * <p><b>为什么上面那条 {@code extraInfoBytesAreStoredVerbatim} 抓不到它</b>：
     * 那条走的是 {@code BaseMapper.insert}；而这条走的是 {@code @Update} 注解里的
     * 固定 SQL。把 CAST 加回这条路径 ⇒ 语句<b>照样执行成功</b>（text 列接受 assignment cast）、
     * 端到端用例<b>照样全绿</b>，只有落库字节被悄悄改写。
     * ⇒ 这是一条<b>独立的、现有护网覆盖不到的</b>残留路径，必须单独钉。
     *
     * <p>本条是<b>纯注解反射断言</b>：{@code @Update} 的 SQL 是编译期常量字符串，
     * 直接读出来比对，**零容器、零数据库**，比端到端探针快一个数量级。
     */
    @Test
    @DisplayName("updateUserExtraInfo（头像双写）的 SQL 不得残留 CAST(... AS jsonb)")
    void updateUserExtraInfoSqlHasNoJsonbCast() throws NoSuchMethodException {
        Update ann = UserAvatarHistoryMapper.class
                .getMethod("updateUserExtraInfo", Long.class, String.class)
                .getAnnotation(Update.class);
        assertTrue(ann != null, "该方法应当还挂着 @Update 注解");
        String[] values = ann.value();
        String sql = String.join(" ", values);
        String upper = sql.toUpperCase();
        assertFalse(upper.contains("CAST("),
                "extra_info 是 text 列，头像双写这条路径不得再 CAST。实际 SQL = " + sql);
        assertFalse(upper.contains("JSONB"),
                "extra_info 是 text 列，头像双写这条 SQL 里不该再出现 jsonb。实际 SQL = " + sql);
        assertTrue(sql.contains("extra_info = "),
                "extra_info 仍应被显式赋值。实际 SQL = " + sql);
    }

    @Test
    @DisplayName("没有业务字段变更时仍必须发出 UPDATE —— Go 的 autoUpdateTime 无条件进 SET")
    @SuppressWarnings({"unchecked", "unused"})
    void updateUserById_noBusinessFields_stillAdvancesUpdateTime() {
        // 🔴 2026-10-23（DRIFT-USERRPC-V-010）：**旧断言是错的**。
        //
        // 旧用例名 `updateUserById_noFields_isNoOp` 断言「没有任何字段变更时应直接返回 0，不发 UPDATE」，
        // 依据是旧注释里那句「GORM 对全零值 struct 同样不发 UPDATE」。
        // ⚠️ 那句注释**没有 Go 出处**，而且被 2026-10-23 的**双侧活体对拍**证伪：
        //
        //   Go 出处（消费点）gorm.io/gorm@v1.31.1/callbacks/update.go:235-252
        //       if field.AutoUpdateTime > 0 && value[field.Name] == nil && value[field.DBName] == nil {
        //           now := stmt.DB.NowFunc()
        //           assignValue(field, now)
        //           set = append(set, clause.Assignment{Column: …, Value: now})   // ← 无条件进 SET
        //       }
        //   ⇒ 即便所有业务字段都是零值，autoUpdateTime 字段**仍会被加进 SET**
        //   ⇒ ConvertToAssignments 返回的 set 非空
        //   ⇒ callbacks/update.go:74-79 的 `else { return }`（"空 set 就不发 UPDATE"）**不触发**
        //   ⇒ **Go 总会发出一条只推进 update_time 的 UPDATE**。
        //
        //   双侧活体对拍实测（2026-10-23 21:25，真进程 + 真 PG）：
        //       请求体 {}                  Go: update_time 推进   RAG(修复前): 不推进
        //       请求体 {"extra_info":{}}  Go: update_time 推进   RAG(修复前): 不推进
        //
        // ⇒ 旧断言「never().update(...)」是一条**把错误答案锁死**的守卫（§3.3.3「绿测试可能是错误答案的守卫」）。
        UserMapper mapper = Mockito.mock(UserMapper.class, Mockito.CALLS_REAL_METHODS);

        mapper.updateUserById(1001L, new UserEntity());

        ArgumentCaptor<LambdaUpdateWrapper<UserEntity>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        Mockito.verify(mapper).update(Mockito.isNull(), captor.capture());

        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("update_time"),
                "即使没有业务字段变更，也必须发出只推进 update_time 的 UPDATE"
                        + "（对齐 GORM autoUpdateTime 的无条件 SET）。实际 SET = " + sqlSet);
        assertTrue(sqlSet.contains("CURRENT_TIMESTAMP"),
                "update_time 应由 CURRENT_TIMESTAMP 推进，实际 SET = " + sqlSet);
        // 反向断言：确实**没有**任何业务列被写
        assertFalse(sqlSet.contains("username"), "不该写 username，实际 SET = " + sqlSet);
        assertFalse(sqlSet.contains("password"), "不该写 password，实际 SET = " + sqlSet);
        assertFalse(sqlSet.contains("email"), "不该写 email，实际 SET = " + sqlSet);
        assertFalse(sqlSet.contains("extra_info"), "不该写 extra_info，实际 SET = " + sqlSet);
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

    // ================== DRIFT-001：update_time 必须被推进 ==================

    /**
     * DRIFT-001 回归：只要真的发出了 UPDATE，SET 子句里就<b>必须</b>含 update_time。
     *
     * <p>Go 出处 {@code model/data_model.go:15}：
     * {@code UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`}，
     * GORM 的 autoUpdateTime 会在每次 {@code Updates()} 时把该列写进 SET。
     *
     * <p>修复前 RAG 的 SET 只有 Username/Password/Email/extra_info，{@code update_time}
     * <b>自注册起永不推进</b>。原注释写的是「依赖 Postgres CURRENT_TIMESTAMP 在 row-level 触发」——
     * 这在事实层面不成立：普通 UPDATE 不含该列时 Postgres 不会触碰它；
     * 本仓既无 MetaObjectHandler（本模块零命中）、DDL 也只有 INSERT 时生效的 DEFAULT、无 trigger。
     */
    @Test
    @DisplayName("updateUserById 发出 UPDATE 时必须把 update_time 放进 SET（对齐 GORM autoUpdateTime）")
    @SuppressWarnings({"unchecked", "unused"})
    void updateUserById_advancesUpdateTime() {
        UserMapper mapper = Mockito.mock(UserMapper.class, Mockito.CALLS_REAL_METHODS);

        UserEntity patch = new UserEntity();
        patch.setEmail("new@example.com");
        patch.setExtraInfo(Map.of("avatar", "https://cdn/x.png"));

        mapper.updateUserById(1001L, patch);

        ArgumentCaptor<LambdaUpdateWrapper<UserEntity>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        Mockito.verify(mapper).update(Mockito.isNull(), captor.capture());

        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("update_time"),
                "SET 子句必须含 update_time，否则 update_time 自插入起永不推进。实际 SET = " + sqlSet);
        assertTrue(sqlSet.contains("CURRENT_TIMESTAMP"),
                "update_time 应由 CURRENT_TIMESTAMP 推进，实际 SET = " + sqlSet);
    }
}
