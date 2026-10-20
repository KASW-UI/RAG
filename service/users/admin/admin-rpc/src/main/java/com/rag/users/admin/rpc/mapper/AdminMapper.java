package com.rag.users.admin.rpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.users.admin.rpc.domain.AdminEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * admin_admin 表的 MyBatis-Plus Mapper。
 *
 * <p>对应 Go 端 internal/model/data_model.go 中 AdminModel 的 Admin 相关方法
 * （findOneAdminByUid / findOneAdminByUsername / insertAdmin / updateAdminByUid）。
 *
 * <p>L4 关键差异：
 * <ul>
 *   <li>Go 用 GORM 全自动；Java 用 MyBatis-Plus {@code BaseMapper<AdminEntity>} 提供 selectById /
 *       insert / updateById / deleteById 等标准 CRUD</li>
 *   <li>复杂方法（CreateAdminAtomic 4 步事务）写在 Mapper 的 {@code default} 方法里，
 *       用 {@code @Transactional} + 自定义 SQL（{@code @Select / @Insert / @Update}）实现；
 *       MyBatis-Plus 的 JDK 代理对 default 方法生效</li>
 *   <li>{@code findOneAdminByUsernameInTx} 是 default 方法里调用的辅助查询，专门用于事务内</li>
 * </ul>
 */
@Mapper
public interface AdminMapper extends BaseMapper<AdminEntity> {

    // 标准 CRUD 全部由 BaseMapper 提供：
    //   selectById(uid), insert(admin), updateById(admin), deleteById(id)
    // 业务特殊查询（findOneAdminByUsername / findOneAdminByUid 等）在 AdminDomainService 里
    // 通过 LambdaQueryWrapper 实现，不需要写在 Mapper 里。

    // 注：CreateAdminAtomic 4 步事务在 AdminCreateAtomicService.createAdminAtomic 里编排
    // 编排，调用 mapper 的 updateById / deleteById / 自定义 @Select / @Update。
}
