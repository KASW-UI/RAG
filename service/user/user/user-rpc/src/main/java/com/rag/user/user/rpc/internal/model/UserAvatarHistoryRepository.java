package com.rag.user.user.rpc.internal.model;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 对应 Go: internal/model/user.go 中 UserModel 的 avatar 相关方法
 *   - CreateAvatarHistory:事务 = 旧 is_current=false + 插新 + user.extra_info.avatar 更新
 *   - SelectAvatarHistory:事务 = 旧 is_current=false + 指定 history_id=true + user.extra_info.avatar 更新
 *   - ListAvatarHistory:limit<=0 或 >30 强制 30,按 create_time DESC
 *
 * Java 端:JpaRepository 提供基础 CRUD,事务用 @Transactional 在 Logic / 自定义方法上标注;
 * 三个 "is_current = false / true" 的 update 写成 @Modifying 方法,
 * Logic 端用 @Transactional 包住"旧置 false + 插新 + 改 user.extra_info"三步,
 * 这是和 Go 的 gorm.Transaction 块一一对应的写法。
 */
public interface UserAvatarHistoryRepository extends JpaRepository<UserAvatarHistory, Long> {

    /**
     * 对应 Go: UserModel.ListAvatarHistory(ctx, uid, limit)
     * Go 端 limit<=0 或 >30 强制 30;Java 端 limit 校验在 Logic 层做(保持职责清晰)
     */
    List<UserAvatarHistory> findByUidOrderByCreateTimeDesc(Long uid, Pageable pageable);

    /**
     * 对应 Go: tx.Where("uid = ? AND is_current = ?", uid, true).Update("is_current", false)
     */
    @Modifying
    @Query("UPDATE UserAvatarHistory h SET h.isCurrent = false WHERE h.uid = :uid AND h.isCurrent = true")
    int clearCurrentByUid(@Param("uid") Long uid);

    /**
     * 对应 Go: tx.Model(&UserAvatarHistory{}).Where("uid = ? AND history_id = ?", ...).Update("is_current", true)
     */
    @Modifying
    @Query("UPDATE UserAvatarHistory h SET h.isCurrent = true WHERE h.uid = :uid AND h.historyId = :historyId")
    int setCurrentByUidAndHistoryId(@Param("uid") Long uid, @Param("historyId") String historyId);

    /**
     * 对应 Go: tx.Where("uid = ? AND history_id = ?", uid, historyID).First(&history)
     */
    Optional<UserAvatarHistory> findByUidAndHistoryId(Long uid, String historyId);
}