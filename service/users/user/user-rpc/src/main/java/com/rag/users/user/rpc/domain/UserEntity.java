package com.rag.users.user.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * users_user 表实体 —— MyBatis-Plus @TableName + JsonbTypeHandler。
 *
 * 对应 Go: service/user/model/user.go 的 User struct（GORM 表 users → HUMAN_ONLY 后表名 users_user）。
 *
 * 框架职责:
 *   - @TableName("users_user") 取代 JPA 的 @Table(name="users")
 *   - @TableId(type = IdType.ASSIGN_ID) 主键策略（雪花 ID）取代 JPA 的 @Id + @GeneratedValue
 *   - @TableField("snake_case") 显式映射列名（与 Go 端 gorm tag byte-for-byte 一致）
 *   - extra_info JSONB 由 JsonbTypeHandler 序列化（取代 Hibernate @JdbcTypeCode(SqlTypes.JSON)）
 *
 * autoResultMap = true 不能去掉：没有它，MyBatis-Plus 自己生成的 ResultMap
 * 不会采用 extra_info 字段上声明的 TypeHandler，读回来的 jsonb 会是 null。
 *
 * 用 JsonbTypeHandler 而不是 MP 自带的 JacksonTypeHandler：后者写参走 ps.setString，
 * JDBC 会绑成 varchar，Postgres 的 jsonb 列直接拒收
 * （"column extra_info is of type jsonb but expression is of type character varying"），
 * 导致 BaseMapper.insert / updateById 必现失败。
 */
@TableName(value = "users_user", autoResultMap = true)
public class UserEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("uid")
    private Long uid;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("email")
    private String email;

    /**
     * 对应 Go {@code Status int64 `gorm:"column:status;default:0"`}
     * （声明点 {@code service/user/user/rpc/internal/model/data_model.go:11}，<b>无 not null</b>）。
     *
     * <p>🔴 <b>此处曾写 {@code = 0L}，已删除（2026-10-23，DRIFT-USERRPC-V-001）。</b>
     *
     * <p><b>为什么那个默认值会造成 fail-open</b>（§3.3.1 的典型形态）：
     * 字段初始化值只在「setter 从未被调用」时才可见，而
     * MyBatis 的 {@code DefaultResultSetHandler} 在
     * {@code value == null && !callSettersOnNulls} 时<b>跳过 setter</b>
     * （{@code callSettersOnNulls} 默认 {@code false}）
     * ⇒ {@code status IS NULL} 的行读出来是 <b>{@code 0L}</b>，<b>不是 null</b>。
     *
     * <p>于是 Login 里的 {@code if (user.getStatus() != null && user.getStatus() == 1L)}
     * 判成「未封禁」⇒ <b>被封禁用户可以登录</b>。
     * 而同一份代码的 {@code getUser} 处写着
     * 「status=0 意为活跃，把 NULL 默认成活跃是朝错误方向的 fail-open」
     * —— <b>同一个字段，两条路径两个方向</b>。
     *
     * <p><b>Go 的语义不是「NULL 当 0」而是「NULL 读不出来」</b>：
     * {@code gorm@v1.31.2/scan.go:153} {@code rows.Scan(values...)} 对
     * 「NULL → int64」直接返回错误 ⇒ 整条 Login 走 db_error 支（INTERNAL）。
     * ⇒ Java 侧必须让 {@code null} <b>真的传上来</b>，由调用点显式判。
     *
     * <p>⚠️ <b>删除初始化值不会改变写入语义</b>，两条路径都不依赖它：
     * <ul>
     *   <li><b>INSERT</b>：{@code UserDomainService.register} 显式
     *       {@code u.setStatus(0L)}（:321），与 Go 的 {@code default:0} 语义一致；</li>
     *   <li><b>UPDATE</b>：{@code UserMapper#updateUserById} 用
     *       {@code LambdaUpdateWrapper.set(...)} <b>逐字段显式 set</b>，
     *       不走 {@code updateById(entity)} ⇒ 本来就与字段默认值无关。</li>
     * </ul>
     *
     * <p>⚠️ <b>admin-rpc 的同名实体仍有 {@code = 0L}</b>（同款缺陷类，属另一个组件），
     * 未在本轮一并改 —— 见台账的跨组件待办。
     */
    @TableField("status")
    private Long status;

    @TableField("score")
    private Integer score;

    @TableField(value = "extra_info", typeHandler = com.rag.users.user.rpc.mapper.JsonbTypeHandler.class)
    private Map<String, String> extraInfo;

    /**
     * 🔴 类型为 {@link OffsetDateTime} 而非 {@link LocalDateTime}（2026-10-23，DRIFT-USERRPC-V-004）。
     *
     * <p><b>Go 出处</b>：声明点 {@code service/user/user/rpc/internal/model/data_model.go:14-15}
     * 是 {@code time.Time} + {@code gorm:"column:create_time;autoCreateTime"}，<b>无 type tag</b>；
     * 消费点 {@code gorm.io/driver/postgres@v1.6.0/postgres.go:251-256} 的
     * {@code case schema.Time} 分支在 {@code field.Precision == 0} 时返回 {@code "timestamptz"}。
     *
     * <p><b>为什么必须是带偏移的类型</b>：Go 的 {@code time.Time} 自带 location，
     * {@code avatarhelpers.go:17-18} 的 {@code item.CreateTime.Format(time.RFC3339)}
     * 输出形如 {@code 2026-01-01T08:00:00+08:00}，<b>带偏移量</b>。
     * proto 字段是 {@code string create_time}，所以差异直接体现在 wire 上的<b>字符串内容</b>。
     * {@link LocalDateTime} 没有偏移量，格式化只能出 {@code 2026-01-01T08:00:00} ——
     * 不管后面怎么拼，都凑不出 Go 那个字符串。
     *
     * <p>⚠️ <b>与 DDL 必须同一提交</b>：列改 timestamptz 而实体仍是 LocalDateTime，
     * 会让 JVM 默认时区参与换算，把「列类型不一致」换成「值算错」——比现状更糟。
     * 见 {@code users-common/src/main/resources/db/01_create_user.sql:54-83}。
     */
    @TableField("create_time")
    private OffsetDateTime createTime;

    /**
     * 🔴 同 {@link #createTime}（DRIFT-USERRPC-V-004）。Go 声明点
     * {@code data_model.go:30-31}，消费点同为 {@code postgres.go:251-256}。
     */
    @TableField("update_time")
    private OffsetDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getStatus() { return status; }
    public void setStatus(Long status) { this.status = status; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Map<String, String> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
    public OffsetDateTime getCreateTime() { return createTime; }
    public void setCreateTime(OffsetDateTime createTime) { this.createTime = createTime; }
    public OffsetDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(OffsetDateTime updateTime) { this.updateTime = updateTime; }
}