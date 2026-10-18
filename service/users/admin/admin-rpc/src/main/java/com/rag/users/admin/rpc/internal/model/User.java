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
 * 对应 Go 端 internal/model/admin.go 的 User struct（GORM 表 users）。
 *
 * admin-rpc 持有 User 表虽然语义上奇怪（用户表通常归 user-rpc 管），但 Go 端 admin-rpc
 * 通过 internal/model/admin.go 同时持有 User struct，本质是把 admin-rpc 当作
 * "管理员对 user 表的 CRUD 网关"：BanUser / DeleteUser / ResetUserPassword /
 * UpdateUser 都是 admin-rpc 直接读写 users 表。1:1 保留 Go 端该定义。
 *
 * 表结构（与 Go 端 AutoMigrate 一致）：
 *   id          BIGSERIAL PRIMARY KEY
 *   uid         BIGINT NOT NULL UNIQUE
 *   username    VARCHAR UNIQUE
 *   password    VARCHAR
 *   email       VARCHAR UNIQUE                -- 非 null（与 Admin.Email nullable 不同，1:1 保留）
 *   status      BIGINT NOT NULL DEFAULT 0     -- 0=正常 1=封禁（BanUser / UnBanUser 改此列）
 *   score       INTEGER
 *   extra_info  JSONB
 *   create_time TIMESTAMP
 *   update_time TIMESTAMP
 *
 * 注：admin-rpc 端 User.java 与 user-rpc 端 User.java 字段定义必须完全一致，因为两者
 *     映射的是同一张物理表（users）。运行时同一行数据被两边共同读写。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private Long uid;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    // 非 null：与 Admin.Email nullable 不同，1:1 保留 Go 端 User 表定义
    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "status", nullable = false)
    private Long status = 0L;

    @Column(name = "score")
    private Integer score;

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