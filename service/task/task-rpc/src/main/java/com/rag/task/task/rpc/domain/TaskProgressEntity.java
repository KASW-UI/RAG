package com.rag.task.task.rpc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 用户任务进度 —— 对应 Go 端 {@code internal/Model/taskModel.go:28-41} 的 {@code TaskProgress}。
 *
 * <p><b>为什么本实体在本模块几乎不被使用</b>：
 * Go 的写路径<b>没有</b>用 GORM 的 {@code Model.TaskProgress}，
 * 而是在两个 sink 里各自定义了一个只含 4~5 个字段的局部结构体
 * （{@code sink/like_sink.go:36-42} 的 {@code UserTaskProgress}、
 * {@code sink/article_sink.go:33-45} 的 {@code ArticleLikeTaskProgress}），
 * 因为 upsert 的列集与 DO UPDATE 赋值必须逐条对齐 GORM 的
 * {@code clause.OnConflict} 语义，手写 SQL 更直接。
 * Java 侧同理：写入一律走 {@code TaskProgressMapper} 的显式 SQL，
 * 本实体只承载<b>读路径</b>（{@code GetTask} 的回源查询）
 * 与 {@code TaskSchemaGate} 的存在性说明。
 *
 * <p>⚠️ 列类型按 GORM AutoMigrate 的规则对齐，别按直觉改成 Integer / Timestamp：
 * int64 ⇒ BIGINT、string ⇒ TEXT、time.Time ⇒ TIMESTAMPTZ。见 {@code V1__create_task_progress.sql}。
 *
 * <p>⚠️ {@code userId} + {@code taskId} 是<b>复合主键</b>（Go 两个字段都带 {@code primary_key}），
 * 所以这里两个字段都标 {@link TableId}，MyBatis-Plus 会把它们作为联合主键处理。
 */
@TableName("task_progress")
public class TaskProgressEntity {

    /** Go {@code UserID int64} ⇒ BIGINT。 */
    @TableId(value = "user_id")
    private Long userId;

    /** Go {@code TaskID int64} ⇒ BIGINT。 */
    @TableId(value = "task_id")
    private Long taskId;

    /** Go {@code Status string} ⇒ TEXT；取值 {@code doing} / {@code done}。 */
    private String status;

    /** Go {@code Progress int64} ⇒ BIGINT。 */
    private Long progress;

    /** Go {@code Target int64} ⇒ BIGINT。注意 <b>GetTask 不用这一列</b>（见 TaskDomainService）。 */
    private Long target;

    /** Go {@code DoneAt *time.Time} ⇒ TIMESTAMPTZ，可空。 */
    private OffsetDateTime doneAt;

    /** Go {@code CreatedAt time.Time} ⇒ TIMESTAMPTZ。 */
    private OffsetDateTime createdAt;

    /** Go {@code UpdatedAt time.Time} ⇒ TIMESTAMPTZ。 */
    private OffsetDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProgress() {
        return progress;
    }

    public void setProgress(Long progress) {
        this.progress = progress;
    }

    public Long getTarget() {
        return target;
    }

    public void setTarget(Long target) {
        this.target = target;
    }

    public OffsetDateTime getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(OffsetDateTime doneAt) {
        this.doneAt = doneAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}