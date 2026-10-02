package com.rag.user.user.rpc.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 对应 Go: internal/model/data_model.go
 *
 * Go 端 data_model.go 里 User 和 UserAvatarHistory **各自独立定义** create_time / update_time 字段,
 * 两个 struct 没有任何继承关系,平铺在一个 .go 文件里。
 *
 * Java 端按 JPA 规范,把两个 Entity 都用到的"审计时间戳"抽到 @MappedSuperclass,让子类继承,
 * 这是 JPA 的标准做法(类似 Hibernate 的 AbstractAuditingEntity)。
 *
 * 注意:这是 Java 端的工程化抽象,**不是 1:1 翻译**——Go 里没有这个基类。
 * 写在这里是为了:① Logic 层 @PrePersist / @PreUpdate 不重复 ② 字段命名统一 ③ 面试能展开讲 JPA 生命周期回调。
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class DataModel {

    /**
     * 对应 Go: gorm tag `autoCreateTime` —— INSERT 时由 Postgres DEFAULT now() 自动写入
     * Java 端用 Spring Data JPA 的 @CreatedDate,需要再在 Application 上加 @EnableJpaAuditing 才会生效。
     */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createTime;

    /**
     * 对应 Go: gorm tag `autoUpdateTime` —— UPDATE 时自动刷新
     */
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private Instant updateTime;

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }
}