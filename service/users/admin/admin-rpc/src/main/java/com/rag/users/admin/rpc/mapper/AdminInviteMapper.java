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
 * </ul>
 *
 * <p><b>本接口不声明任何方法</b>（2026-10-21 订正，见 AGENTS.md §6.2 P3）：
 * 原类注释声称 {@code findAdminInviteByCodeForUpdate}（悲观锁 {@code SELECT ... FOR UPDATE}）
 * 与 {@code updateAdminInviteUsed} 都「写在 Mapper 的 default 方法里，用
 * {@code @Select} / {@code @Update} + {@code @Transactional}」——
 * <b>与代码不符</b>，且与本文件末尾的注释<b>自相矛盾</b>。
 *
 * <p><b>方法真实归属</b>：两个方法都在
 * {@code AdminCreateAtomicService.createAdminAtomic} 里，用
 * {@code LambdaQueryWrapper.last("FOR UPDATE")} 与 {@code LambdaUpdateWrapper} 实现，
 * {@code @Transactional} 挂在<b>该 Service</b> 上。
 *
 * <p><b>为什么这件事值得记下来</b>：悲观锁一旦「注释说在 Mapper、实际在 Service」，
 * review 时就会去 Mapper 里找锁而找不到，进而怀疑锁是不是没生效。
 * 锁本身是<b>实测生效</b>的（{@code AdminRpcContainerWiringTest} 在真实 PG 上验证过），
 * 错的只是归属描述 —— 但足以误导下一次审计。
 */
@Mapper
public interface AdminInviteMapper extends BaseMapper<AdminInviteEntity> {
    // 标准 CRUD 由 BaseMapper 提供
    // 复杂方法（findAdminInviteByCodeForUpdate / updateAdminInviteUsedInTx）
    // 在 AdminCreateAtomicService.createAdminAtomic 里编排（含 SELECT ... FOR UPDATE 悲观锁）。
}
