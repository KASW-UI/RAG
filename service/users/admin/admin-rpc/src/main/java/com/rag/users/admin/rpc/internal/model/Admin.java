package com.rag.users.admin.rpc.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对应 Go 端 internal/model/admin.go 的 Admin struct（GORM 表 admins）。
 *
 * 表结构（与 Go 端 AutoMigrate 一致）：
 *   id          BIGSERIAL PRIMARY KEY
 *   uid         BIGINT NOT NULL UNIQUE
 *   username    VARCHAR UNIQUE
 *   password    VARCHAR
 *   email       VARCHAR UNIQUE           -- nullable（与 User.Email 非 null 不同，1:1 保留 Go 端定义）
 *   extra_info  JSONB
 *   create_time TIMESTAMP
 *   update_time TIMESTAMP
 *
 * 列名（byte）严格对齐 Go 端 gorm tag；map 类型用 Hibernate 6 的 JSON 支持序列化到 JSONB。
 *
 * 注：admin-rpc 持 Admin 表 + User 表 + AdminInvite 表。User 表虽然主要由 user-rpc 管理，
 *     但 admin-rpc 也直接读写（BanUser / DeleteUser / ResetUserPassword / UpdateUser），
 *     所以 User.java 也在 admin-rpc 模型层实装，与 user-rpc 端 User.java 字段定义完全一致。
 */
@Entity
@Table(name = "admins")
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private Long uid;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "password_hash")
    private String password;

    // nullable：Go 端用 *string 表示 optional；Java 端用包装类型 String，不写 nullable=false
    @Column(name = "email", unique = true)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_info", columnDefinition = "jsonb")
    private Map<String, String> extraInfo;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
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

    public Map<String, String> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}