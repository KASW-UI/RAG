package com.rag.favorite.favorite.rpc.mapper;

import com.rag.favorite.favorite.rpc.domain.FavoriteFolderEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 收藏夹数据访问 —— 对应 Go {@code service/favorite/rpc/internal/model/favorite.go:33-80}
 * 的 5 个方法（{@code InsertFolder} / {@code FindFoldersByUserId} / {@code FindFolderByFolderId}
 * / {@code FindFolderByUserIdAndName} / {@code UpdateFolderNameByFolderId}）。
 *
 * <p>⚠️ AGENTS.md §4.1 已锁定的决策：Go 的单个 {@code FavoriteModel} 按实体拆成多个 Mapper。
 *
 * <p><b>「查不到」的约定</b>：Go 侧所有 Find* 方法把 {@code gorm.ErrRecordNotFound}
 * 归一化成 {@code model.ErrorNotFound}（internal/model/favorite.go:55,69,101,115），由 logic 决定
 * 翻译成哪个业务码。⇒ 本 Mapper 的单条查询<b>返回 {@code null}</b>，
 * 由 {@code FavoriteDomainService} 统一翻译（避免每处各写一遍判空）。
 *
 * <p>⚠️ <b>全部无 LIMIT</b>（Go 侧 internal/model/favorite.go:37-47 的 {@code FindFoldersByUserId}
 * 也没有分页）⇒ 这里<b>不加</b> {@code LIMIT}。加了就改变了可观察行为。
 */
@Mapper
public interface FavoriteFolderMapper {

    /**
     * 对应 Go {@code InsertFolder}（internal/model/favorite.go:33-35）：
     * {@code m.conn.WithContext(ctx).Create(folder).Error}。
     *
     * <p>{@code id} 是 bigserial 自增代理键，<b>不写入</b>（Go 的 struct 里
     * {@code Id uint64} 零值 0 也是由 PG 分配）。
     *
     * <p>⚠️ {@code create_time} / {@code update_time} 由 PG 的 {@code now()} 生成，
     * 与 Go 的 {@code autoCreateTime} / {@code autoUpdateTime}（GORM 在 INSERT 时填
     * Go 侧的 {@code time.Now()}）<b>时区语义一致</b>：都是 timestamptz 的绝对时刻。
     */
    @Insert("""
            INSERT INTO favorite_folder (folder_id, user_id, name)
            VALUES (#{folderId}, #{userId}, #{name})
            """)
    int insertFolder(@Param("folderId") long folderId,
                     @Param("userId") long userId,
                     @Param("name") String name);

    /**
     * 对应 Go {@code FindFoldersByUserId}（internal/model/favorite.go:37-47）：
     * <pre>Where("user_id = ?", userId).Order("create_time desc")</pre>
     *
     * <p>🔴 <b>{@code ORDER BY create_time DESC} 是 L2 语义</b>：收藏夹列表的展示顺序。
     * 去掉它，列表顺序就变成未定义的（PG 不保证无 ORDER BY 的返回序）。
     */
    @Select("""
            SELECT id, folder_id, user_id, name, create_time, update_time
            FROM favorite_folder
            WHERE user_id = #{userId}
            ORDER BY create_time DESC
            """)
    List<FavoriteFolderEntity> findByUserId(@Param("userId") long userId);

    /** 对应 Go {@code FindFolderByFolderId}（internal/model/favorite.go:49-59）：{@code Where("folder_id = ?").First()}。 */
    @Select("""
            SELECT id, folder_id, user_id, name, create_time, update_time
            FROM favorite_folder
            WHERE folder_id = #{folderId}
            LIMIT 1
            """)
    FavoriteFolderEntity findByFolderId(@Param("folderId") long folderId);

    /**
     * 🔴 对应 Go {@code InsertFavorite}（favorite_fact.go:276-277）与
     * {@code DeleteFolderCascade}（favorite_fact.go:342-343）里的
     * <pre>tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("folder_id = ?", …).First(&amp;folder)</pre>
     *
     * <p>⚠️ <b>必须带 {@code FOR UPDATE}</b>：它是「同一收藏夹的并发创建 / 并发删除」的锁点。
     * 缺它的后果（Go 注释 {@code favorite_fact.go:267-269} 强调的就是这一点）：
     * 两个并发 {@code CreateFavorite} 可能都通过事务外的归属校验，
     * 各自算出「文件夹里还没有这个目标」，最后靠唯一键让其中一个失败 ——
     * 那个失败是 <b>23505</b>，被翻译成 {@code AlreadyExists/5302}，
     * 看起来对，但中间态是两个事务都持有同一文件夹的写意向。
     */
    @Select("""
            SELECT id, folder_id, user_id, name, create_time, update_time
            FROM favorite_folder
            WHERE folder_id = #{folderId}
            LIMIT 1
            FOR UPDATE
            """)
    FavoriteFolderEntity findByFolderIdForUpdate(@Param("folderId") long folderId);

    /** 对应 Go {@code FindFolderByUserIdAndName}（internal/model/favorite.go:61-73）。 */
    @Select("""
            SELECT id, folder_id, user_id, name, create_time, update_time
            FROM favorite_folder
            WHERE user_id = #{userId} AND name = #{name}
            LIMIT 1
            """)
    FavoriteFolderEntity findByUserIdAndName(@Param("userId") long userId,
                                             @Param("name") String name);

    /**
     * 🔴 对应 Go {@code UpdateFolderNameByFolderId}（internal/model/favorite.go:75-80）。
     *
     * <p><b>只更新 name 一列</b>，<b>刻意不碰 {@code update_time}</b>。
     *
     * <p>⚠️ 这不是遗漏：Go 的 {@code Update("name", name)} 也只写 name。
     * 但 GORM 的 {@code Update} 会同时刷新 {@code autoUpdateTime} 字段，
     * Java 侧若照直觉加 {@code update_time = now()} 就会让
     * {@code GetFavoriteFolders} 返回的 {@code updated_at} 变化 ——
     * 那是 <b>对外可观察</b>的差异（helper.go:70 把 UpdateTime.UnixMilli() 放进响应）。
     */
    @Update("""
            UPDATE favorite_folder
            SET name = #{name}
            WHERE folder_id = #{folderId}
            """)
    int updateNameByFolderId(@Param("folderId") long folderId, @Param("name") String name);

    /**
     * 🔴 对应 Go {@code DeleteFolderCascade} 的最后一步（favorite_fact.go:376）：
     * {@code tx.Where("folder_id = ?", folderID).Delete(&FavoriteFolder{}).Error}。
     *
     * <p><b>在本事务内最后一步</b>，前面已经删完全部 {@code favorite_item}。
     * 顺序不可交换：先删 folder 会让 items 变成孤儿。
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM favorite_folder WHERE folder_id = #{folderId}")
    int deleteByFolderId(@Param("folderId") long folderId);
}