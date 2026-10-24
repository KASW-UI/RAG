package com.rag.favorite.favorite.rpc.mapper;

import com.rag.favorite.favorite.rpc.domain.FavoriteItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收藏条目数据访问 —— 对应 Go {@code service/favorite/rpc/internal/model/favorite.go:82-118}
 * 的 4 个 Find 方法，以及 {@code favorite_fact.go:283,321,330,373} 的事务内
 * INSERT / DELETE。
 *
 * <p>⚠️ AGENTS.md §4.1 已锁定的决策：Go 的单个 {@code FavoriteModel} 按实体拆成多个 Mapper。
 *
 * <p><b>「查不到」的约定</b>：单条查询返回 {@code null}，由
 * {@code FavoriteDomainService} 统一翻译成 {@code NotFound} / {@code AlreadyExists}
 * （Go 侧是 {@code model.ErrorNotFound} 哨兵在各 logic 分派）。
 *
 * <p>⚠️ <b>全部无 LIMIT</b>（Go 侧 {@code FindFavoritesByFolderId} 也没有分页，
 * favorite.go:82-92）⇒ 这里不加 {@code LIMIT}。
 */
@Mapper
public interface FavoriteItemMapper {

    /**
     * 对应 Go {@code FindFavoritesByFolderId}（favorite.go:82-92）：
     * <pre>Where("folder_id = ?", folderId).Order("create_time desc")</pre>
     *
     * <p>🔴 {@code ORDER BY create_time DESC} 是 L2 语义（条目列表展示顺序）。
     */
    @Select("""
            SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
                   target_revision, title, cover, create_time
            FROM favorite_item
            WHERE folder_id = #{folderId}
            ORDER BY create_time DESC
            """)
    List<FavoriteItemEntity> findByFolderId(@Param("folderId") long folderId);

    /** 对应 Go {@code FindFavoriteByFavoriteId}（favorite.go:94-104）。 */
    @Select("""
            SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
                   target_revision, title, cover, create_time
            FROM favorite_item
            WHERE favorite_id = #{favoriteId}
            LIMIT 1
            """)
    FavoriteItemEntity findByFavoriteId(@Param("favoriteId") long favoriteId);

    /**
     * 对应 Go {@code FindFavoriteByFolderTarget}（favorite.go:106-118）：
     * {@code Where("folder_id = ? AND target_id = ? AND target_type = ?")}。
     *
     * <p>🔴 三个条件缺一不可：{@code target_type} 参与 {@code uk_folder_target}
     * 唯一键，所以「同一文件夹里 article 123 与 post 123 是两条不同的收藏」。
     */
    @Select("""
            SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
                   target_revision, title, cover, create_time
            FROM favorite_item
            WHERE folder_id = #{folderId} AND target_id = #{targetId} AND target_type = #{targetType}
            LIMIT 1
            """)
    FavoriteItemEntity findByFolderTarget(@Param("folderId") long folderId,
                                          @Param("targetId") String targetId,
                                          @Param("targetType") String targetType);

    /**
     * 对应 Go {@code InsertFavorite} 事务内的 {@code tx.Create(item)}（favorite_fact.go:283）。
     *
     * <p>🔴 {@code target_revision} 写的是 <b>可为 null</b> 的原值
     * （{@code #{targetRevision}}，MyBatis 对 {@code null} 走
     * {@code setNull(..., Types.VARCHAR)}）。绝不能写成 {@code COALESCE(#{targetRevision}, '')} ——
     * 那是把「无修订」变成「有��空修订的修订」，见 {@code FavoriteItemEntity} 的类注释第 ① 条。
     *
     * <p>⚠️ 23505（唯一冲突）在此抛出，由 {@code FavoriteRpcSupport.isUniqueViolation}
     * 识别并翻译成 {@code AlreadyExists/5302}（create_favorite_logic.go:157-161）。
     */
    @Insert("""
            INSERT INTO favorite_item
                (favorite_id, folder_id, user_id, target_id, target_type, target_revision, title, cover)
            VALUES (#{favoriteId}, #{folderId}, #{userId}, #{targetId}, #{targetType},
                    #{targetRevision}, #{title}, #{cover})
            """)
    int insertItem(@Param("favoriteId") long favoriteId,
                   @Param("folderId") long folderId,
                   @Param("userId") long userId,
                   @Param("targetId") String targetId,
                   @Param("targetType") String targetType,
                   @Param("targetRevision") String targetRevision,
                   @Param("title") String title,
                   @Param("cover") String cover);

    /**
     * 对应 Go {@code DeleteFavoriteByFavoriteId} 事务内的
     * {@code tx.Where("favorite_id = ?", favoriteID).Delete(&FavoriteItem{})}（favorite_fact.go:330）。
     *
     * <p>⚠️ <b>物理删，不是软删</b>：删除后业务行消失，唯一的墓碑是 v2 retract 事实
     * （{@code favoriteAssertionSchema} 的 legacy 分支则<b>不留</b>事实，只删行）。
     */
    @Delete("DELETE FROM favorite_item WHERE favorite_id = #{favoriteId}")
    int deleteByFavoriteId(@Param("favoriteId") long favoriteId);

    /**
     * 对应 Go {@code DeleteFolderCascade} 事务内的
     * {@code tx.Where("folder_id = ?", folderID).Delete(&FavoriteItem{})}（favorite_fact.go:373）。
     *
     * <p>必须在「逐条写入 retract 事实」之后、「删 folder」之前调用。
     */
    @Delete("DELETE FROM favorite_item WHERE folder_id = #{folderId}")
    int deleteByFolderId(@Param("folderId") long folderId);

    /**
     * 🔴 对应 Go {@code DeleteFolderCascade} 里的
     * <pre>
     * tx.Clauses(clause.Locking{Strength: "UPDATE"}).
     *    Where("folder_id = ?", folderID).Order("favorite_id").Find(&items)
     * </pre>
     * （favorite_fact.go:353-355）。
     *
     * <p>三个要素一个都不能丢：
     * <ul>
     *   <li>{@code FOR UPDATE} —— 逐行悲观锁，保证同一收藏夹的并发删除不会
     *       各自算出不同的 retract 集合；</li>
     *   <li>{@code ORDER BY favorite_id} —— <b>确定性</b>的事件生成顺序。
     *       Go 显式排序就是为了让同一批事件的 {@code created_at}/{@code event_id}
     *       序列可复现；去掉 ORDER BY 就变成未定义的。</li>
     *   <li><b>无 LIMIT</b> —— 一次必须拿全，否则 retract 会缺行。</li>
     * </ul>
     */
    @Select("""
            SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
                   target_revision, title, cover, create_time
            FROM favorite_item
            WHERE folder_id = #{folderId}
            ORDER BY favorite_id
            FOR UPDATE
            """)
    List<FavoriteItemEntity> lockAllByFolderIdOrdered(@Param("folderId") long folderId);

    /**
     * 对应 Go {@code DeleteFavoriteByFavoriteId} 里的
     * <pre>tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("favorite_id = ?", favoriteID).First(&item)</pre>
     * （favorite_fact.go:306-307）。返回 {@code null} ⇒ Go 侧的
     * {@code gorm.ErrRecordNotFound} ⇒ 归一化为 {@code model.ErrorNotFound}。
     */
    @Select("""
            SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
                   target_revision, title, cover, create_time
            FROM favorite_item
            WHERE favorite_id = #{favoriteId}
            LIMIT 1
            FOR UPDATE
            """)
    FavoriteItemEntity lockByFavoriteId(@Param("favoriteId") long favoriteId);
}