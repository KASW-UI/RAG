package com.rag.user.user.rpc.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go: internal/model/data_model.go 中的 type User struct
 *
 * 字段对齐 Go 端的 gorm tag:
 *   Id         uint64            `gorm:"primaryKey"`                          → @Id @GeneratedValue
 *   Uid        int64             `gorm:"column:uid;uniqueIndex;not null"`     → @Column(unique = true, nullable = false)
 *   Username   string            `gorm:"column:username;unique"`             → @Column(unique = true)
 *   Password   string            `gorm:"column:password"`                    → @Column(nullable = false)
 *   Email      string            `gorm:"column:email;unique"`                → @Column(unique = true)
 *   Status     int64             `gorm:"column:status;default:0"`            → @Column(nullable = false)
 *   Score      int32             `gorm:"column:score"`                       → @Column
 *   ExtraInfo  map[string]string `gorm:"column:extra_info;serializer:json"`  → @JdbcTypeCode(JSON), PostgreSQL JSONB
 *   create_time / update_time                                                       → 继承自 DataModel 基类
 */
@Entity
@Table(name = "users")
public class User extends DataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 业务主键,由 snowflake 算法生成,Go 端对应 snowflake.GetID() */
    @Column(name = "uid", unique = true, nullable = false)
    private Long uid;

    @Column(name = "username", unique = true)
    private String username;

    /** 已用 cryptx.PasswordEncrypt 加密后的密文 —— Java 端对应 BCryptPasswordEncoder 或 PBKDF2 */
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", unique = true)
    private String email;

    /** 0=active;1=banned(login.go 里判定 user.Status==1 表示封号) */
    @Column(name = "status", nullable = false)
    private Long status = 0L;

    @Column(name = "score")
    private Integer score = 0;

    /**
     * 对应 Go: gorm tag `serializer:json`,PostgreSQL 存为 JSONB
     * Hibernate 6 用 @JdbcTypeCode(SqlTypes.JSON) 直接 map Map<String,String> ↔ JSONB。
     * 当前 avatar 字段会写在这里(Go 的 CreateAvatarHistory 事务里有 user.ExtraInfo["avatar"] = url)。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_info", columnDefinition = "jsonb")
    private Map<String, String> extraInfo = new HashMap<>();

    // —— getter / setter ——

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
}