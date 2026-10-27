package com.rag.common.persistence.probe;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 守门用的最小实体（<b>只存在于 test 源码</b>）。
 *
 * <p>它存在的唯一理由：让守门能跑一条<b>真 SQL</b>（真 MyBatis-Plus 生成的 SQL、
 * 真 PostgreSQL 执行），从而证明拦截器<b>真的在执行链上</b>，而不只是「Bean 存在」。
 */
@TableName("mp_probe_item")
public class MpProbeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
