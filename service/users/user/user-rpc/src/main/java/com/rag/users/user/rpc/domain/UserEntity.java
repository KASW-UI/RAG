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
     * <p>🔴 <b>{@code = 0L} 曾于 2026-10-23 被删掉，2026-10-23 当天又原样恢复。</b>
     * 下面是那次误删与恢复的完整依据，**保留是因为它正是等价性的实现载体，不是历史包袱**。
     *
     * <h3>Go 的语义：{@code status IS NULL} 读出来是 {@code 0}，<b>不报错</b></h3>
     * <p>声明点：{@code service/user/user/rpc/internal/model/data_model.go:11}
     * {@code Status int64 `gorm:"column:status;default:0"`}（<b>无 not null</b>，值类型非指针）。
     * <p><b>消费点</b>（三段，逐段都要看，缺一段就会得出相反结论）：
     * <ol>
     *   <li>{@code gorm.io/gorm@v1.31.2/schema/field.go:616-622} 给 int64 字段装的 setter：
     *       {@code case **int64: if data != nil && *data != nil { …SetInt(**data) }}
     *       —— <b>{@code *data == nil} 时什么都不做</b>，字段保持<b>零值 0</b>；</li>
     *   <li>{@code gorm@v1.31.2/scan.go:21} {@code prepareValues} 与
     *       {@code schema/field.go:1009} {@code NewValuePool} 都是
     *       {@code poolInitializer(reflect.PointerTo(field.IndirectFieldType))}
     *       ⇒ 传给 {@code rows.Scan} 的 dest 是 <b>{@code **int64}</b>，不是 {@code int64}；</li>
     *   <li>因此 {@code database/sql/convert.go:431-435} 走的是
     *       {@code case reflect.Pointer: if src == nil { dv.SetZero(); return nil }}
     *       ⇒ <b>不报错</b>。
     *       （{@code convert.go:438-440} 那条
     *       {@code case reflect.Int64: if src == nil { return "converting NULL to int64 is unsupported" }}
     *       <b>永远走不到</b>，因为 dest 的 kind 是 Pointer 而不是 Int64。）</li>
     * </ol>
     *
     * <h3>本类的机制与之<b>逐条同形</b></h3>
     * MyBatis 的 {@code DefaultResultSetHandler} 在
     * {@code value == null && !callSettersOnNulls} 时<b>跳过 setter</b>
     * （{@code callSettersOnNulls} 默认 {@code false}）
     * ⇒ 字段初始化值 {@code 0L} 留了下来 ⇒ {@code status IS NULL} 同样读成 <b>{@code 0L}</b>。
     *
     * <h3>⇒ 曾经那版「fail-closed」是<b>真的偏离</b></h3>
     * 2026-10-23 上午，本类被删掉 {@code = 0L}，并在
     * {@code UserDomainService#login} / {@code #getUser} 加了
     * 「status 为 null ⇒ 抛 INTERNAL」的守卫，理由写的是
     * 「{@code gorm@v1.31.2/scan.go:153 rows.Scan} 对 NULL → int64 直接返回错误」。
     * <b>该理由已被上面第 2/3 段与双侧活体对拍同时证伪</b>：
     * <pre>
     *   status IS NULL 打 Login ：Go 200 + token   RAG（误修后）5001「系统内部错误」
     *   status IS NULL 打 GetUser：Go 200（status 字段被 omitempty 省掉 ⇒ 即 0）
     *                            RAG（误修后）5001「系统内部错误」
     * </pre>
     * ⇒ <b>删掉 {@code = 0L} 才是不等价的那一侧</b>。现已恢复。
     *
     * <p>⚠️ <b>admin-rpc 的同名实体一直保留 {@code = 0L}</b>（与本类同形）——
     * 是<b>当时正确</b>的一侧，2026-10-23 上午被本组件的误修文档反向指为「同款缺陷」，
     * 该指认已撤销，见 {@code docs/DRIFT/users/user/rpc.md} 第三轮 §8。
     *
     * <p>⚠️ <b>删除/恢复初始化值都不改变写入语义</b>，两条写入路径都不依赖它：
     * <ul>
     *   <li><b>INSERT</b>：{@code UserDomainService.register} 显式
     *       {@code u.setStatus(0L)}，与 Go 的 {@code default:0} 语义一致；</li>
     *   <li><b>UPDATE</b>：{@code UserMapper#updateUserById} 用
     *       {@code LambdaUpdateWrapper.set(...)} <b>逐字段显式 set</b>，
     *       不走 {@code updateById(entity)}。</li>
     * </ul>
     */
    @TableField("status")
    private Long status = 0L;

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