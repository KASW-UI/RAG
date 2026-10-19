package com.rag.users.admin.rpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.users.admin.rpc.domain.AdminInviteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * admin_invite 表的 MyBatis-Plus Mapper。
 *
 * <p>对应 Go 端 internal/model/data_model.go 中 AdminModel 的 AdminInvite 相关方法
 * （insertAdminInvite / findAdminInviteByCodeForUpdate / updateAdminInviteUsed）。
 *
 * <p>L4 关键差异：
 * <ul>
 *   <li>标准 CRUD 由 {@code BaseMapper<AdminInviteEntity>} 提供</li>
 *   <li>{@code findAdminInviteByCodeForUpdate}（悲观锁 SELECT ... FOR UPDATE）写在
 *       Mapper 的 default 方法里，用 {@code @Select} + {@code @Transactional}</li>
 *   <li>{@code updateAdminInviteUsed}（标记 invite used）写在 Mapper 的 default 方法里，
 *       用 {@code @Update} + {@code @Transactional}</li>
 * </ul>
 */
@Mapper
public interface AdminInviteMapper extends BaseMapper<AdminInviteEntity> {
    // 标准 CRUD 由 BaseMapper 提供
    // 复杂方法（findAdminInviteByCodeForUpdate / updateAdminInviteUsedInTx）
    // 在 AdminDomainService.createAdminAtomic default 方法里编排。
}
