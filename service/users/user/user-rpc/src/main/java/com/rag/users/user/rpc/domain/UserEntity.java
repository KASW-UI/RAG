package com.rag.users.user.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
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

    @TableField("status")
    private Long status = 0L;

    @TableField("score")
    private Integer score;

    @TableField(value = "extra_info", typeHandler = com.rag.users.user.rpc.mapper.JsonbTypeHandler.class)
    private Map<String, String> extraInfo;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

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
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}