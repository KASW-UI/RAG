package com.rag.comment.comment.rpc.mapper;

import com.rag.comment.comment.rpc.domain.CommentContentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论正文数据访问 —— 对应 Go 端 {@code service/comment/rpc/internal/model/comment.go:422-470}
 * 的 {@code BatchGetReplyContent}。
 *
 * <p><b>⚠️ 入参去重与排序都在 Go 的 model 层做，不在 SQL 里</b>：
 * comment.go:428-439 先滤掉 {@code id <= 0} 再按 map 去重，
 * comment.go:455-469 再<b>按传入顺序</b>重排结果。
 * 本方法只做「取集合」，顺序由 {@code CommentCacheService} 负责 ——
 * 因为 {@link #selectByIds} 同时服务于「Redis 未命中时的 DB 回源」，
 * 那条链路上还要按入参顺序拼回 {@code comment_content} 列表。
 *
 * <p><b>⚠️ 一个必须知道的行为（GetComment 的已知配对风险）</b>：
 * get_comment_logic.go:122-143 把 index 行与 content 行<b>按下标配对</b>，
 * 只防越界（{@code if idx >= len(contentRows) break}）。
 * 若某条评论缺 {@code comment_content} 行，content 列表会整体前移 ⇒ 内容与统计串位。
 * <b>这是 Go 的现状，本模块不修正</b>（修正会改变已有部署的可观察输出）。
 * 见 {@code CommentDomainService#getComment} 的同名注释。
 */
@Mapper
public interface CommentContentMapper {

    /**
     * 对应 Go {@code InsertCommentTx:66-77} 的 {@code tx.Create(content)}。
     *
     * <p>⚠️ <b>meta 显式带 jsonb 的 typeHandler</b>：MyBatis-Plus 自动生成的 insert 不保证
     * 带上 {@code @TableField(typeHandler=…)}，靠默认路径会把 jsonb 当 VARCHAR 绑定而报错。
     * 这里手写 SQL 把这件事钉死（同 {@code CommentDomainFactOutboxMapper#appendFact}）。
     *
     * <p>⚠️ 插入的 {@code created_at} / {@code updated_at} 由调用方显式给出
     * （来自 Kafka 消息里的 {@code create_time}），<b>不是</b> {@code CURRENT_TIMESTAMP} ——
     * Go 侧用的是 {@code time.Unix(msg.CreateTime, 0)}，即<b>评论创建时刻</b>而非入库时刻。
     */
    @Insert("""
            INSERT INTO comment_content (comment_id, content, meta, created_at, updated_at)
            VALUES (#{commentId}, #{content},
                    #{meta, typeHandler=com.rag.comment.comment.rpc.mapper.JsonbTypeHandler, jdbcType=OTHER},
                    #{createdAt}, #{updatedAt})
            """)
    int insert(CommentContentEntity content);

    /**
     * 对应 Go {@code BatchGetReplyContent}：
     * {@code Model(&CommentContent{}).Where("comment_id IN ?", filteredIDs).Find(&rows)}。
     *
     * <p>⚠️ Go 侧<b>无 ORDER BY</b> ⇒ 返回顺序不保证；「按入参顺序重排」是调用方的责任。
     *
     * <p>meta 列是 jsonb，走 {@code JsonbTypeHandler}（由实体的 {@code autoResultMap=true} 生效）。
     */
    @Select("""
            <script>
            SELECT * FROM comment_content WHERE comment_id IN
            <foreach item="cid" collection="ids" open="(" separator="," close=")">#{cid}</foreach>
            </script>
            """)
    List<CommentContentEntity> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 对应 Go {@code fact_authority.go:88} 的内容行计数：
     * {@code Model(&CommentContent{}).Where("comment_id = ?", commentID).Count(&contentCount)}。
     *
     * <p>用于权威读的「created 事实必须恰有 1 条正文」校验（fact_authority.go:91-93）：
     * {@code contentCount != 1 ⇒ 拒绝出具权威事实}。
     */
    @Select("SELECT count(*) FROM comment_content WHERE comment_id = #{commentId}")
    long countByCommentId(@Param("commentId") long commentId);
}