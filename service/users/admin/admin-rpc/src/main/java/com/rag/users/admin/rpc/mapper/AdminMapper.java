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
 * </ul>
 *
 * <p><b>本接口不声明任何方法</b>（2026-10-21 订正，见 AGENTS.md §6.2 P3）：
 * 原类注释声称「复杂方法（CreateAdminAtomic 4 步事务）写在 Mapper 的 {@code default} 方法里，
 * 用 {@code @Transactional} + 自定义 SQL（{@code @Select / @Insert / @Update}）实现」——
 * <b>与代码不符</b>，且与本文件末尾的注释<b>自相矛盾</b>。
 * 这属 §3.2「有出处、出处指错位置」的文档级病灶：会让后续 review 误判机制归属
 * （例如以为悲观锁 / 事务边界装在 Mapper 上，实际在 Service 层）。
 *
 * <p><b>方法真实归属</b>：
 * <table border="1">
 *   <caption>Go 方法 → RAG 实际位置</caption>
 *   <tr><th>逻辑</th><th>RAG 位置</th></tr>
 *   <tr><td>{@code findOneAdminByUsername}</td>
 *       <td>{@code AdminDomainService} 私有方法，{@code LambdaQueryWrapper} 实现</td></tr>
 *   <tr><td>{@code findOneAdminByUid}</td><td>同上</td></tr>
 *   <tr><td>{@code insertAdmin}</td><td>{@code adminMapper.insert(entity)}</td></tr>
 *   <tr><td>{@code updateAdminByUid}</td><td>{@code adminMapper.updateById(entity)}</td></tr>
 *   <tr><td>CreateAdmin 4 步事务编排</td>
 *       <td>{@code AdminCreateAtomicService.createAdminAtomic}；{@code @Transactional} 在该 Service 上</td></tr>
 * </table>
 */
@Mapper
public interface AdminMapper extends BaseMapper<AdminEntity> {

    // 标准 CRUD 全部由 BaseMapper 提供：
    //   selectById(uid), insert(admin), updateById(admin), deleteById(id)
    // 业务特殊查询（findOneAdminByUsername / findOneAdminByUid 等）在 AdminDomainService 里
    // 通过 LambdaQueryWrapper 实现，不需要写在 Mapper 里。

    // 注：CreateAdmin 4 步事务在 AdminCreateAtomicService.createAdminAtomic 里编排，
    //     事务注解在那个 Service 上，不在本接口上。
}
