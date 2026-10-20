package com.rag.message.message.rpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.message.message.rpc.domain.NotificationEntity;

/**
 * message_notification 表 Mapper 接口。
 *
 * <p>Phase 0 空骨架：仅 extends BaseMapper&lt;NotificationEntity&gt;，方法由 MyBatis-Plus 默认提供。
 * <br>Phase 1 补 {@code List&lt;NotificationEntity&gt; selectByRecipient(...)} 等业务方法。
 */
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
}
