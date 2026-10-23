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
     *
     * <p><b>2026-10-23 更正</b>：本方法原 javadoc 写「用 {@code Optional} 包装显式表达"找不到"
     * 语义；调用方应 {@code .orElseThrow(...)}」—— <b>实现从来不是这样</b>：返回类型是
     * {@code UserEntity}，找不到时在方法内直接抛 {@link NotFoundException}。
     * 与 Go 的 {@code (user, err)} 返回二元组逐条同构：<b>「找不到」是 error，不是值</b>。
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
     *       {@code UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`}。</li>
     *   <li>原 RAG 实现的注释写的是「依赖 Postgres CURRENT_TIMESTAMP 在 row-level 触发」——
     *       这句话在事实层面不成立：普通 UPDATE 不含该列时，Postgres 不会触碰它；
     *       本仓既无 MetaObjectHandler（本模块零命中，message-rpc 才有），
     *       DDL 也只有 {@code DEFAULT CURRENT_TIMESTAMP}（仅 INSERT 时生效）、无 trigger。
     *       结果：RAG 的 update_time 自注册起永不推进，而 Go 会。</li>
     * </ul>
     *
     * <p>🔴 <b>2026-10-23（DRIFT-USERRPC-V-010 修复）：{@code update_time} 必须<b>无条件</b>进 SET</b>，
     * 哪怕一个业务字段都没改。
     * <p>Go 出处（<b>消费点</b>，不是声明点）：
     * {@code gorm.io/gorm@v1.31.1/callbacks/update.go:235-252}
     * <pre>
     * if !stmt.SkipHooks &amp;&amp; stmt.Schema != nil {
     *     for _, dbName := range stmt.Schema.DBNames {
     *         field := stmt.Schema.LookUpField(dbName)
     *         if field.AutoUpdateTime &gt; 0 &amp;&amp; value[field.Name] == nil &amp;&amp; value[field.DBName] == nil {
     *             if v, ok := selectColumns[field.DBName]; (ok &amp;&amp; v) || !ok {
     *                 now := stmt.DB.NowFunc()
     *                 assignValue(field, now)
     *                 set = append(set, clause.Assignment{Column: …, Value: now})   // ← 无条件进 SET
     * }}}}}}}
     * </pre>
     * ⇒ 即便所有业务字段都是零值，{@code autoUpdateTime} 字段<b>仍会被加进 SET</b>
     * ⇒ {@code ConvertToAssignments} 返回的 set 非空
     * ⇒ {@code callbacks/update.go:74-79} 的 {@code else { return }}（"空 set 就不发 UPDATE"）<b>不触发</b>
     * ⇒ <b>Go 总会发出一条只推进 update_time 的 UPDATE</b>。
     * <p>⚠️ <b>原实现（及其守门测试）把这条当成了「GORM 对全零值 struct 不发 UPDATE」—— 那是错的</b>：
     * 旧注释与旧用例 {@code updateUserById_noFields_isNoOp} 都写着「无字段变更 ⇒ 返回 0、不发 UPDATE」，
     * 而它的证据只有 <b>RAG 单侧</b> 的 mock 断言（{@code never()).update(...)}），
     * <b>没有任何一侧的真进程/真 PG 证据</b>。
     * <p>双侧活体对拍实测（2026-10-23 21:25，真进程 + 真 PG，UPDATE 请求体分别为
     * {@code {}} / {@code {"extra_info":{}}} / {@code {"email":…}}）：
     * <pre>
     *   请求体 {}                 Go: update_time 推进   RAG: 不推进   ← 漂移
     *   请求体 {"extra_info":{}} Go: update_time 推进   RAG: 不推进   ← 漂移
     *   请求体 {"email":…}       Go: update_time 推进   RAG: 推进     ✅
     * </pre>
     * ⇒ 修法：<b>删掉 {@code if (!any) return 0;} 的早退</b>，
     * 让 {@code update_time} 永远进 SET —— 于是发出的 SQL 与 Go 的
     * {@code UPDATE users SET update_time = ? WHERE uid = ?} 逐条同构
     * （Go 侧 {@code model/user.go:44-47} 的 {@code Updates(newUser)} 也是同一个动作）。
     * <p>⚠️ <b>为什么这条不是「无中生有」</b>：Go 的 {@code UpdateUserById} 无条件被调用
     * （{@code updateuserlogic.go:62}），它不判断「有没有要改的字段」。
     * RAG 侧早退属于<b>凭空加出来的优化</b>（无出处 idiomatization，§3.2）。
     */
    default int updateUserById(Long uid, UserEntity newUser) {
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserEntity::getUid, uid);
        // ⚠️ 只写「非空」的业务字段 —— 对齐 GORM Updates(struct) 跳过零值的语义。
        //    （注意：**update_time 不受这条约束**，见本方法末尾的 DRIFT-USERRPC-V-010 说明。）
        if (newUser.getUsername() != null && !newUser.getUsername().isEmpty()) {
            wrapper.set(UserEntity::getUsername, newUser.getUsername());
        }
        if (newUser.getPassword() != null && !newUser.getPassword().isEmpty()) {
            wrapper.set(UserEntity::getPassword, newUser.getPassword());
        }
        if (newUser.getEmail() != null && !newUser.getEmail().isEmpty()) {
            wrapper.set(UserEntity::getEmail, newUser.getEmail());
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
        }
        // 🔴 DRIFT-USERRPC-V-010：update_time **无条件**进 SET（对齐 GORM autoUpdateTime 的
        //   callbacks/update.go:238-252）。**不要**在这里写 `if (!any) return 0;` ——
        //   Go 对全零值 struct 同样会发一条只推进 update_time 的 UPDATE。
        //   （旧注释写「GORM 对全零值 struct 同样不发 UPDATE」，经 2026-10-23 双侧活体对拍证伪。）
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

    // ── 2026-10-23 删除（DRIFT-USERRPC-V-008）──
    // 原 selectCurrentByUid(@Select) 与 findCurrentAvatar(default 包装) 全仓零调用点，
    // 且 Go 侧无对应物：Go 的 W2 只是 UPDATE ... WHERE uid=? AND is_current=true 批量置 false，
    // 见 service/user/user/rpc/internal/model/user.go:80-84，**不读回当前头像行**。
    // ⇒ 留着的真实风险不是「多余」，而是它是一份**看起来该用**的查询：
    // 下一个 agent 实现「查当前头像」时会直接调它，而那条路径 Go 根本没有。
    // 删除遵循 §3.4.1 D-2 先例（零消费者的代码不能算已对齐）。
}