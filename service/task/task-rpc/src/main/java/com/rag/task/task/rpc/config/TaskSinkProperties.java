package com.rag.task.task.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 两个 sink 的运行参数 —— 对应 Go 端两处<b>互相独立</b>的常量块：
 * <ul>
 *   <li>{@code internal/sink/like_sink.go:31-34, 47, 64-75}</li>
 *   <li>{@code internal/sink/article_sink.go:18-31, 76-87}</li>
 * </ul>
 *
 * <h2>🔴 为什么拆成 {@link Like} / {@link Article} 两个块而不是一个通用块</h2>
 * <p>Go 的两个 sink <b>故意（或至少事实上）是两套独立常量</b>：
 * taskId 不同（1902 / 190）、target 不同（5 / 3）、TTL 不同（90d / 24h、120d）、
 * init key 的拼法不同、done key 的段序不同。
 * 合成一个"通用 sink 参数"看着更整洁，但会让"两条路其实可以共用配置"这个<b>错误结论</b>
 * 写进类型里 —— 以后有人把两条路合并成一条 pipeline 时就没有阻力了。
 * 保持两块独立，是让这种合并必须先回到 Go 侧确认的成本闸门。
 *
 * <h2>⚠️ 文案常量不在这儿</h2>
 * <p>任务名 / 描述在 {@code TaskDefs}（对应 Go {@code Model.AllTaskDefs}），
 * 与本类<b>不是同一份真相</b>，理由见该类注释。
 */
@ConfigurationProperties(prefix = "task.definitions")
public class TaskSinkProperties {

    private final Like like = new Like();
    private final Article article = new Article();

    public Like getLike() {
        return like;
    }

    public Article getArticle() {
        return article;
    }

    /** 用户点赞任务（Go {@code like_sink.go}）。 */
    public static class Like {

        /** Go {@code like_sink.go:32} {@code taskID = int64(1902)}。 */
        private long taskId = 1902L;

        /** Go {@code like_sink.go:33} {@code target = 5}。达标判定与 DB 的 target 列都用它。 */
        private long target = 5L;

        /** Go {@code like_sink.go:188,284,339}：init / done / like:total 三处 TTL 均为 90 天。 */
        private long initTtlDays = 90L;

        /** Go {@code like_sink.go:70} {@code redisPipeMax = 5000}：Redis pipeline 分块上限。 */
        private int redisPipeMax = 5000;

        /** Go {@code like_sink.go:71} {@code pgBatchMax = 2000}：Postgres 批量 upsert 分块上限。 */
        private int pgBatchMax = 2000;

        /** Go {@code like_sink.go:69} {@code flushEvery = 1 * time.Second}：delta 刷盘周期。 */
        private long flushEveryMs = 1000L;

        public long getTaskId() {
            return taskId;
        }

        public void setTaskId(long taskId) {
            this.taskId = taskId;
        }

        public long getTarget() {
            return target;
        }

        public void setTarget(long target) {
            this.target = target;
        }

        public long getInitTtlDays() {
            return initTtlDays;
        }

        public void setInitTtlDays(long initTtlDays) {
            this.initTtlDays = initTtlDays;
        }

        public int getRedisPipeMax() {
            return redisPipeMax;
        }

        public void setRedisPipeMax(int redisPipeMax) {
            this.redisPipeMax = redisPipeMax;
        }

        public int getPgBatchMax() {
            return pgBatchMax;
        }

        public void setPgBatchMax(int pgBatchMax) {
            this.pgBatchMax = pgBatchMax;
        }

        public long getFlushEveryMs() {
            return flushEveryMs;
        }

        public void setFlushEveryMs(long flushEveryMs) {
            this.flushEveryMs = flushEveryMs;
        }
    }

    /** 文章获赞任务（Go {@code article_sink.go}）。 */
    public static class Article {

        /** Go {@code article_sink.go:20} {@code articleTaskID = 190}（源码注释：测试专用ID）。 */
        private long taskId = 190L;

        /** Go {@code article_sink.go:21} {@code articleTarget = int64(3)}。 */
        private long target = 3L;

        /** Go {@code article_sink.go:22} {@code articleDoneTTL = 90d + 30d}：done 标记与 like:cnt 的 TTL。 */
        private long doneTtlDays = 120L;

        /** Go {@code article_sink.go:23} {@code articleInitTTL = 24h}：init 标记 TTL（比点赞路短得多）。 */
        private long initTtlDays = 1L;

        /** Go {@code article_sink.go:25} {@code redisPipeMaxDefault = 5000}。 */
        private int redisPipeMax = 5000;

        /**
         * Go {@code article_sink.go:26} {@code pgBatchMaxDefault = 2000}。
         *
         * <p>⚠️ <b>当前无使用点</b>：文章 sink 的 {@code FlushPostgres} 在 Go 侧整段被注释
         * （{@code article_sink.go:176-178, 458-460}），
         * {@code flushOnce} 只做 lazyInit + FlushRedis。
         * 这里保留字段只为配置面与常量块对齐，Java 侧同样<b>不</b>实现该写路径。
         */
        private int pgBatchMax = 2000;

        /** Go {@code article_sink.go:24} {@code articleFlushEvery = 1 * time.Second}。 */
        private long flushEveryMs = 1000L;

        public long getTaskId() {
            return taskId;
        }

        public void setTaskId(long taskId) {
            this.taskId = taskId;
        }

        public long getTarget() {
            return target;
        }

        public void setTarget(long target) {
            this.target = target;
        }

        public long getDoneTtlDays() {
            return doneTtlDays;
        }

        public void setDoneTtlDays(long doneTtlDays) {
            this.doneTtlDays = doneTtlDays;
        }

        public long getInitTtlDays() {
            return initTtlDays;
        }

        public void setInitTtlDays(long initTtlDays) {
            this.initTtlDays = initTtlDays;
        }

        public int getRedisPipeMax() {
            return redisPipeMax;
        }

        public void setRedisPipeMax(int redisPipeMax) {
            this.redisPipeMax = redisPipeMax;
        }

        public int getPgBatchMax() {
            return pgBatchMax;
        }

        public void setPgBatchMax(int pgBatchMax) {
            this.pgBatchMax = pgBatchMax;
        }

        public long getFlushEveryMs() {
            return flushEveryMs;
        }

        public void setFlushEveryMs(long flushEveryMs) {
            this.flushEveryMs = flushEveryMs;
        }
    }
}