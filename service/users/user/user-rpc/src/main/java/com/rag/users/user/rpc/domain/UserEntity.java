package com.rag.users.user.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * users_user 表实体 —— MyBatis-Plus @TableName + JacksonTypeHandler。
 *
 * 对应 Go: service/user/model/user.go 的 User struct（GORM 表 users → HUMAN_ONLY 后表名 users_user）。
 *
 * 框架职责:
 *   - @TableName("users_user") 取代 JPA 的 @Table(name="users")
 *   - @TableId(type = IdType.ASSIGN_ID) 主键策略（雪花 ID）取代 JPA 的 @Id + @GeneratedValue
 *   - @TableField("snake_case") 显式映射列名（与 Go 端 gorm tag byte-for-byte 一致）
 *   - extra_info JSONB 由 JacksonTypeHandler 自动序列化（取代 Hibernate @JdbcTypeCode(SqlTypes.JSON)）
 *   - TypeHandler 全局 mybatis-plus.type-handlers-package 自动扫描，无需 @TableField(typeHandler=)
 */
@TableName("users_user")
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

    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
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