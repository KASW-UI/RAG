package com.rag.users.admin.rpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.users.admin.rpc.domain.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * users_user 表的 MyBatis-Plus Mapper（admin-rpc 端独立持有）。
 *
 * <p>对应 Go 端 internal/model/data_model.go 中 AdminModel 的 User 相关方法
 * （findOneUserByUid / findOneUserByUsername / updateUserByUid / updateUserStatusByUid /
 *  updateUserPasswordByUid / deleteUserByUid / findUserListByKeyword）。
 *
 * <p>L4 关键差异：
 * <ul>
 *   <li>admin-rpc 与 user-rpc 共用同一张物理表（users_user），两侧 Entity 字段定义完全一致</li>
 *   <li>admin-rpc 端的 UserMapper 只写"管理员操作需要的方法"，user-rpc 端 UserMapper 写
 *       "用户自助操作需要的方法" —— 两边不重复实现所有 CRUD，按职责分工</li>
 * </ul>
 *
 * <p><b>本接口不声明任何方法</b>（2026-10-21 订正，见 AGENTS.md §6.2 P3）：
 * 原类注释声称分页查询 {@code findUserListByKeyword}「写在 Mapper 的 default 方法里，
 * 用 {@code @Select} + {@code @Transactional}」——<b>与代码不符</b>，且与本文件末尾的注释
 * <b>自相矛盾</b>。属 §3.2「有出处、出处指错位置」的文档级病灶。
 *
 * <p><b>方法真实归属</b>：分页查询与所有动态更新都在 {@code AdminDomainService} 里，
 * 用 {@code LambdaQueryWrapper} / {@code LambdaUpdateWrapper} 实现，
 * 与 user-rpc 端 {@code UserDomainService} 同款模式。事务边界在 Service 上。
 *
 * <p>dynamic set 部分（updateUserByUid 跳过零值）见本文件末尾注释与 {@code AdminDomainService}。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    // 动态更新 / 分页查询 / 单字段更新 在 AdminDomainService 里用 wrapper + @Update 实现。
}
