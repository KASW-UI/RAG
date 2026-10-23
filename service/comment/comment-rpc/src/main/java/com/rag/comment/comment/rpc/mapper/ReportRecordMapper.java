package com.rag.comment.comment.rpc.mapper;

import com.rag.comment.comment.rpc.domain.ReportRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 举报记录数据访问 —— 对应 Go 端 {@code service/comment/rpc/internal/model/comment.go:242-250}
 * 的 {@code InsertReport}。
 *
 * <p>Go 侧这一条<b>没有事务</b>（是 {@code comment.go:157,184,242} 三个无事务方法之一），
 * 只是一次 {@code INSERT … ON CONFLICT DO NOTHING}。
 */
@Mapper
public interface ReportRecordMapper {

    /**
     * 对应 Go {@code InsertReport}：
     * <pre>
     * err := m.conn.WithContext(ctx).Clauses(clause.OnConflict{DoNothing: true}).Create(report).Error
     * </pre>
     *
     * <p><b>⚠️ {@code ON CONFLICT DO NOTHING} 不带 Columns 子句，必须逐字保留。</b>
     * PG 在不带冲突目标时会退化为「任意唯一约束冲突即忽略」，
     * 命中的是 {@code idx_user_comment_report (user_id, comment_id)}
     * ⇒ 同一人重复举报被<b>静默吞掉</b>，而接口仍返回 {@code success = true}
     * （report_comment_logic.go:97-99）。
     *
     * <p>⚠️ 若这里补上 {@code ON CONFLICT (user_id, comment_id) DO NOTHING}，
     * 行为在当前 DDL 下<b>看起来等价</b>，但它会把「将来新增的其它唯一约束」从
     * 「静默忽略」变成「报错」—— 那是新增的可观察差异。
     *
     * <p><b>⚠️ 另一处 Go 的不对称</b>：{@code InsertReport} 只在
     * {@code err != nil} 时打点 {@code CommentPostgresErrorCounterMetric{type="insert"}}，
     * 冲突被吞掉时<b>既不报错也不打点</b>。Java 侧照此实现（返回 0 行不抛异常、不打点）。
     *
     * <p>⚠️ {@code reason} 落库为 int32 原值；proto 枚举的未知值用
     * {@code getReasonValue()} 取，{@code getReason()} 会返回 {@code null}。
     */
    @Insert("""
            INSERT INTO report_record (user_id, comment_id, target_type, target_id, reason, detail, created_at)
            VALUES (#{userId}, #{commentId}, #{targetType}, #{targetId}, #{reason}, #{detail},
                    CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """)
    int insertIgnoreConflict(@Param("userId") long userId,
                             @Param("commentId") long commentId,
                             @Param("targetType") String targetType,
                             @Param("targetId") String targetId,
                             @Param("reason") int reason,
                             @Param("detail") String detail);
}