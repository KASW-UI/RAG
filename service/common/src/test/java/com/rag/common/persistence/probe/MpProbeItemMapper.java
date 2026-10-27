package com.rag.common.persistence.probe;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 守门用的最小 Mapper（<b>只存在于 test 源码</b>）。
 *
 * <p>它继承 {@link BaseMapper} ⇒ ⇒ 走的是 <b>MyBatis-Plus 自己生成的 SQL</b>
 * ⇒ ⇒ 守门跑的是「真 mapper + 真 PG」，不是手写 SQL 的替身。
 */
public interface MpProbeItemMapper extends BaseMapper<MpProbeItem> {
}
