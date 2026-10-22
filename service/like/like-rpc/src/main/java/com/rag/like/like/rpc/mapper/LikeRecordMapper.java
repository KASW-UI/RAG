package com.rag.like.like.rpc.mapper;

import com.rag.like.like.rpc.domain.LikeRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 点赞记录数据访问 —— 对应 Go 端 {@code service/like/rpc/internal/model/like.go} 的
 * {@code LikeRecordModel} 接口 + {@code ProcessLikeMessageBatch} / {@code applyLikeMessage} 里的裸 SQL。
 *
 * <p><b>为什么全部写显式 SQL 而不用 BaseMapper / LambdaQueryWrapper</b>：
 * Go 侧这几条查询的语义有一半在 GORM 的<b>隐式行为</b>里（软删除过滤、
 * {@code count(1) as count} 的分组语义、{@code id < cursor} 的倒序分页）。
 * 换成 Wrapper 后这些条件会被"看起来等价"地改写，其中最容易错的是：
 * <ul>
 *   <li>忘了 {@code delete_at IS NULL} ⇒ 已软删的行被算进点赞数；</li>
 *   <li>忘了 {@code state = 1} 与 {@code state IN (1,2)} 的<b>不同取值域</b>
 *       （计数路径要含点踩，分页路径只含点赞）。</li>
 * </ul>
 * 显式 SQL 把每条判据摆在明面上，也便于逐条对回 Go。
 *
 * <p>⚠️ 软删除：除 {@link #selectForUpdateIncludingDeleted} 外，所有查询都带
 * {@code delete_at IS NULL}（= GORM 默认作用域）。唯一例外是 {@code applyLikeMessage} 里
 * 刻意用 {@code Unscoped()} 读已软删行并据此报错 —— 见该方法注释。
 */
@Mapper
public interface LikeRecordMapper {

    // ==================== 读路径（对应 Go LikeRecordModel 的 Get* 方法） ====================

    /**
     * 对应 Go {@code GetTotalLikeCount}：
     * {@code WHERE author_id = ? AND state = 1} 计数。
     *
     * <p>这是 {@code GetUserTotalLike} 缓存未命中时的回源查询 —— 注意它统计的是
     * 「author_id = 用户」且 state=1 的行数，即该用户收到的点赞总数。
     */
    @Select("SELECT count(*) FROM like_record WHERE author_id = #{authorId} AND state = 1 AND delete_at IS NULL")
    long countActiveLikeByAuthor(@Param("authorId") long authorId);

    /**
     * 对应 Go {@code GetUserActiveLikeCount}：
     * {@code WHERE user_id = ? AND target_type = ? AND state = 1} 计数。
     *
     * <p>用于 {@code syncTaskProgress} 里算「我给出的赞」，
     * 与 {@code currentUserLikeTotal} 取 {@code max(Redis ZCARD, 本值)}。
     */
    @Select("""
            SELECT count(*) FROM like_record
            WHERE user_id = #{userId} AND target_type = #{targetType} AND state = 1 AND delete_at IS NULL
            """)
    long countActiveLikeByUserAndType(@Param("userId") long userId, @Param("targetType") String targetType);

    /**
     * 对应 Go {@code GetTargetActiveLikeCount}：
     * {@code WHERE target_type = ? AND target_id = ? AND state = 1} 计数。
     *
     * <p>用于 {@code currentArticleLikeTotal}，与 Redis Hash 里的计数取 max。
     */
    @Select("""
            SELECT count(*) FROM like_record
            WHERE target_type = #{targetType} AND target_id = #{targetId} AND state = 1 AND delete_at IS NULL
            """)
    long countActiveLikeByTarget(@Param("targetType") String targetType, @Param("targetId") String targetId);

    /**
     * 对应 Go {@code GetBatchLikeCount}：
     * {@code SELECT target_id, state, count(1) ... WHERE target_type = ? AND target_id IN (?) AND state IN (1,2) GROUP BY target_id, state}。
     *
     * <p>⚠️ {@code state IN (1,2)} 而非 {@code = 1}：批量计数<b>同时</b>要赞和踩，
     * 调用方再按 {@code counts[1]} / {@code counts[2]} 取。
     */
    @Select("""
            <script>
            SELECT target_id, state, count(1) AS cnt FROM like_record
            WHERE target_type = #{targetType} AND target_id IN
            <foreach item="id" collection="targetIds" open="(" separator="," close=")">#{id}</foreach>
              AND state IN (1, 2) AND delete_at IS NULL
            GROUP BY target_id, state
            </script>
            """)
    List<StateCountRow> selectStateCountsBatch(@Param("targetType") String targetType,
                                              @Param("targetIds") List<String> targetIds);

    /**
     * 对应 Go {@code GetUserBatchLikeState}：
     * {@code SELECT target_id, state ... WHERE user_id = ? AND target_type = ? AND target_id IN (?)}。
     *
     * <p>无 state 过滤 —— 该用户的记录至多一行（唯一键约束），返回值即状态。
     * <b>查不到的 id 在 Go 里取 map 零值 0 并写回 Redis 防穿透</b>，见 {@code LikeDomainService}。
     */
    @Select("""
            <script>
            SELECT target_id, state FROM like_record
            WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id IN
            <foreach item="id" collection="targetIds" open="(" separator="," close=")">#{id}</foreach>
              AND delete_at IS NULL
            </script>
            """)
    List<TargetStateRow> selectUserStatesBatch(@Param("userId") long userId,
                                               @Param("targetType") String targetType,
                                               @Param("targetIds") List<String> targetIds);

    /**
     * 对应 Go {@code GetUserLikeList}：
     * {@code WHERE user_id/target_type/state=1 [+ id < cursor] ORDER BY id DESC LIMIT n}。
     *
     * <p>⚠️ <b>cursor 是「打包后的 score」（{@code timestamp << 22}），不是 id</b>。
     * Go 侧 logic 把同一个 cursor 同时当作 ZSet 的 maxScore 与 DB 的 {@code id < cursor} 上界，
     * 两套刻度并不一致。这是 Go 的既有行为，<b>不修正</b> —— 改了会让翻页结果变化。
     */
    @Select("""
            <script>
            SELECT id, target_type, target_id, created_at FROM like_record
            WHERE user_id = #{userId} AND target_type = #{targetType} AND state = 1 AND delete_at IS NULL
            <if test="cursor != null and cursor > 0"> AND id &lt; #{cursor}</if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<UserLikeRow> selectUserLikeList(@Param("userId") long userId,
                                         @Param("targetType") String targetType,
                                         @Param("cursor") long cursor,
                                         @Param("limit") int limit);

    /**
     * 对应 Go {@code GetTargetLikerList}：
     * {@code WHERE target_type/target_id/state=1 [+ id < cursor] ORDER BY id DESC LIMIT n}。
     *
     * <p>⚠️ cursor 刻度问题同 {@link #selectUserLikeList}，见其注释。
     */
    @Select("""
            <script>
            SELECT id, user_id, created_at FROM like_record
            WHERE target_type = #{targetType} AND target_id = #{targetId} AND state = 1 AND delete_at IS NULL
            <if test="cursor != null and cursor > 0"> AND id &lt; #{cursor}</if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<TargetLikerRow> selectTargetLikerList(@Param("targetType") String targetType,
                                               @Param("targetId") String targetId,
                                               @Param("cursor") long cursor,
                                               @Param("limit") int limit);

    // ==================== 写路径（对应 Go applyLikeMessage） ====================

    /**
     * 对应 Go {@code tx.Clauses(clause.OnConflict{DoNothing: true}).Create(&seed)} ——
     * 插入 {@code state=0} 的种子行；已存在则什么都不做。
     *
     * <p>返回影响行数：0 表示唯一键冲突（这行以前就存在），非 0 表示本次新建。
     */
    @Insert("""
            INSERT INTO like_record (user_id, target_type, target_id, author_id, state, last_operation_id, created_at, updated_at)
            VALUES (#{userId}, #{targetType}, #{targetId}, #{authorId}, 0, 0, now(), now())
            ON CONFLICT DO NOTHING
            """)
    @Options(useGeneratedKeys = false)
    int insertSeedIfAbsent(@Param("userId") long userId,
                           @Param("targetType") String targetType,
                           @Param("targetId") String targetId,
                           @Param("authorId") long authorId);

    /**
     * 对应 Go {@code tx.Unscoped().Clauses(clause.Locking{Strength: "UPDATE"}).Where(...).First(&current)}。
     *
     * <p>⚠️ <b>唯一一条不带 {@code delete_at IS NULL} 的查询</b>：Go 用 {@code Unscoped()} 明确要读已软删的行，
     * 随后由 {@code if current.DeleteAt.Valid { return error }} 拒绝它。
     * 换成带过滤的查询会让软删行「查不到」，从而跳过那个错误分支、把软删行当成新行重建 —— 行为改变。
     *
     * <p>{@code FOR UPDATE} 是整条幂等链路（同一用户同一目标的并发消息串行化）的基石。
     *
     * @return 命中行；不存在返回 null（调用方须按「查不到」处理）
     */
    @Select("""
            SELECT id, user_id, target_type, target_id, author_id, state, last_operation_id,
                   created_at, updated_at, delete_at
            FROM like_record
            WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}
            FOR UPDATE
            """)
    LikeRecordEntity selectForUpdateIncludingDeleted(@Param("userId") long userId,
                                                     @Param("targetType") String targetType,
                                                     @Param("targetId") String targetId);

    /**
     * 对应 Go {@code tx.Model(&current).Updates(map[string]any{"state":…, "last_operation_id":…, "author_id":…})}。
     *
     * <p>注意<b>不</b>更新 {@code updated_at}：Go 的 {@code Updates} 用 map 形式，
     * GORM 的 {@code UpdatedAt} 自动更新时间只在 {@code Save}/{@code Updates(struct)} 路径触发，
     * map 形式不会带上。保持一致。
     */
    @Update("""
            UPDATE like_record
            SET state = #{state}, last_operation_id = #{lastOperationId}, author_id = #{authorId}
            WHERE id = #{id}
            """)
    int updateState(@Param("id") long id,
                    @Param("state") int state,
                    @Param("lastOperationId") long lastOperationId,
                    @Param("authorId") long authorId);

    // ==================== 结果行 ====================

    /** {@code selectStateCountsBatch} 的一行：某目标在某状态下有 N 条记录。 */
    class StateCountRow {
        /** target_id。 */
        public String targetId;
        /** 状态（1 点赞 / 2 点踩）。 */
        public int state;
        /** 该状态下的记录数。 */
        public long cnt;

        public String getTargetId() {
            return targetId;
        }

        public int getState() {
            return state;
        }

        public long getCnt() {
            return cnt;
        }
    }

    /** {@code selectUserStatesBatch} 的一行。 */
    class TargetStateRow {
        public String targetId;
        public int state;

        public String getTargetId() {
            return targetId;
        }

        public int getState() {
            return state;
        }
    }

    /** {@code selectUserLikeList} 的一行。 */
    class UserLikeRow {
        public Long id;
        public String targetType;
        public String targetId;
        public OffsetDateTime createdAt;

        public Long getId() {
            return id;
        }

        public String getTargetType() {
            return targetType;
        }

        public String getTargetId() {
            return targetId;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }
    }

    /** {@code selectTargetLikerList} 的一行。 */
    class TargetLikerRow {
        public Long id;
        public long userId;
        public OffsetDateTime createdAt;

        public Long getId() {
            return id;
        }

        public long getUserId() {
            return userId;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }
    }
}