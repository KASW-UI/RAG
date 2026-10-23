package com.rag.task.task.rpc.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.observability.MqConsumer;
import com.rag.task.task.rpc.config.TaskSinkProperties;
import com.rag.task.task.rpc.domain.TaskProgressEntity;
import com.rag.task.task.rpc.service.TaskProgressWriter;
import com.rag.task.task.rpc.service.TaskRedisKeys;
import com.rag.task.task.rpc.service.TaskRewardPublisher;
import io.opentelemetry.context.Context;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rag.task.task.rpc.service.RedisPipelines;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文章获赞任务进度落库 —— 1:1 对应 Go 端 {@code internal/sink/article_sink.go}
 * 的 {@code ArticleLikeSinkConsumer}（整体）与 {@code Init.StartTaskKafkaArticleFilter}（装配）。
 *
 * <h2>数据流</h2>
 * <pre>
 * task.logs.filtered.Article ──Consume(key=articleId, value={"user_id":"…","cur":N})──▶
 *      delta[articleId] = (userId, max(cur))          ← ⚠️ key 是文章，不是用户
 *      每 1 秒 flushOnce：
 *        1) lazyInitTaskIfNeeded  SETNX task:inits:190:&lt;uid&gt;:&lt;articleId&gt; EX 24h
 *                               → HSET + PG 插 DO NOTHING
 *        2) FlushRedis             SET article:like:cnt:&lt;articleId&gt; EX 120d
 *                                 → total &gt;= 3 ? completeArticleLike : updateDoingTaskProgress
 * </pre>
 *
 * <h2>🔴 与用户点赞 sink 的五处差异（逐条都在 Go 里真实存在，不是风格差异）</h2>
 * <ol>
 *   <li><b>聚合的 key 是文章 ID，用户 ID 是载荷的一部分</b>
 *       （{@code delta map[int64]articleAgg}，{@code article_sink.go:56,66-69}）。
 *       用户点赞路聚合的 key 就是用户 ID。</li>
 *   <li><b>merge 时会连带更新 UserID</b>（{@code article_sink.go:116-120}）：
 *       {@code if d > cur.total { cur.total = d; cur.UserID = userID }} —— 相等时保留旧值。
 *       用户点赞路只改 max，没有这个连带更新。</li>
 *   <li><b>没有 flushPostgres</b>：Go 的 {@code FlushPostgres} 整段被注释
 *       （{@code article_sink.go:176-178}）⇒ 本类<b>不</b>写 {@code user_like_count}。</li>
 *   <li><b>init key 带 articleId 且 TTL 仅 24h</b>
 *       （{@code task:inits:<taskId>:<uid>:<articleId>}，{@code article_sink.go:255-258,23}）。</li>
 *   <li><b>完成标记按 userID 去重</b>（{@code task:done:<taskId>:<userId>}），
 *       Go 注释写明「每个人只能完成一次这个任务，所以选择 userID，而不是 articleID」。</li>
 * </ol>
 *
 * <h2>⚠️ 被刻意省略的两段死代码</h2>
 * <ul>
 *   <li>{@code markReconCandidates}（{@code article_sink.go:195-228}）——
 *       调用点在 {@code flushOnce} 里<b>被注释</b>（{@code :169}），
 *       它写的 {@code task:recon:active:190} ZSet 与 {@code article:owner} Hash <b>永不产生</b>。</li>
 *   <li>{@code FlushPostgres}（{@code article_sink.go:176-178,458-460}）—— 见上第 3 条。</li>
 * </ul>
 * 两段都不实现：它们在 Go 里就是不可达代码，Java 侧补上会变成<b>新增写入</b>
 * （凭空造出 Go 从未写过的 Redis key）。
 */
@Component
public class ArticleLikeProgressSink {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleLikeProgressSink.class);

    /** OTEL span 的 destination —— Go {@code article_sink.go:94} 写死的逻辑名。 */
    private static final String SPAN_DESTINATION = "article_like_sink";

    private final StringRedisTemplate redis;
    private final TaskProgressWriter progressWriter;
    private final TaskRewardPublisher rewardPublisher;
    private final TaskSinkProperties properties;
    private final ObjectMapper objectMapper;

    private final long taskId;
    private final long target;
    private final Duration doneTtl;
    private final Duration initTtl;

    /**
     * 内存聚合 —— 对应 Go {@code delta map[int64]articleAgg}。
     * key 是<b>文章 ID</b>，value 是 {@code (用户ID, 获赞数)}。
     */
    private final Map<Long, ArticleAgg> delta = new ConcurrentHashMap<>();

    public ArticleLikeProgressSink(StringRedisTemplate redis,
                                   TaskProgressWriter progressWriter,
                                   TaskRewardPublisher rewardPublisher,
                                   TaskSinkProperties properties,
                                   ObjectMapper objectMapper) {
        this.redis = redis;
        this.progressWriter = progressWriter;
        this.rewardPublisher = rewardPublisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.taskId = properties.getArticle().getTaskId();
        this.target = properties.getArticle().getTarget();
        this.doneTtl = Duration.ofDays(properties.getArticle().getDoneTtlDays());
        this.initTtl = Duration.ofDays(properties.getArticle().getInitTtlDays());
    }

    /** Go {@code articleAgg}：{@code UserID int64; total int64}。 */
    record ArticleAgg(long userId, long total) {
    }

    // ==================== 消费 ====================

    /**
     * 消费一条文章点赞进度 —— 对应 Go {@code article_sink.go:93-124} 的 {@code Consume}，
     * 整体包在 {@code observability.TraceConsumer} 里。
     *
     * <p>⚠️ 抛/不抛的边界与 Go 逐条一致：
     * <ul>
     *   <li>key 解析失败 ⇒ Go {@code return err} ⇒ 不提交位点 ⇒ 这里<b>抛异常</b>；</li>
     *   <li>{@code articleID == 0} ⇒ Go {@code return err}，而此时 <b>err 是 nil</b>
     *       （{@code article_sink.go:99-101} 的 {@code if err != nil || articleID == 0 { return err }}）
     *       ⇒ 不提交位点但也不算错误 ⇒ 这里<b>抛异常</b>且带明确的 null-error 语义；</li>
     *   <li>载荷解析失败 / userID 为 0 ⇒ Go {@code return nil} ⇒ <b>提交位点</b> ⇒ 这里正常返回。</li>
     * </ul>
     */
    @KafkaListener(id = "task-article-progress-sink",
            topics = "${task.kafka.out-article-topic}",
            groupId = "${task.kafka.group-kafka-filter}",
            // ⚠️ 文章 sink 用的是 GroupKafkaFilter（task.rpc.consumer101），
            //    **不是** GroupFilterArticle（task.rpc.consumer91，Go 侧无读取点）。
            //    见 TaskKafkaTopics 的类注释。
            // ⚠️ kq 硬编码 Offset: "latest"（initArticleFilter.go:40）
            properties = "auto-offset-reset=latest",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record) {
        String key = record.key() == null ? "" : record.key();
        String value = record.value();

        Throwable err = MqConsumer.traceConsumer(
                Context.root(), "task-rpc", "ArticleLikeSinkConsumer.Consume",
                TaskTraceAttributes.message(SPAN_DESTINATION, key),
                ctx -> {
                    consumeBody(key, value);
                    return null;
                });

        if (err != null) {
            throw asRuntime(err);
        }
    }

    private void consumeBody(String key, String value) {
        // Go: if key == "" { return nil } ⇒ 提交位点
        if (key.isEmpty()) {
            return;
        }

        // Go: articleID, err := strconv.ParseInt(key, 10, 64); if err != nil || articleID == 0 { return err }
        // ⚠️ articleID == 0 时 Go 返回的 err 是 nil，但 kq 的判断是 `err != nil` 才跳过提交，
        //    所以**仍然会 CommitMessages** ⇒ 该条被"安静地丢弃"，不是重投。
        //    Java 侧同样：正常返回（提交位点），不抛异常。
        long articleId = parseLongOrThrow(key);

        // Go 侧 articleID == 0 时是在 parseVal **之前**返回的（article_sink.go:99-101），
        // ⇒ 该消息既不进 delta，也已经确认了位点。
        // ⚠️ 若漏掉这一步，key="0" 的消息会把 (uid,cur) 挂到 delta[0] 上，
        //    并在 flush 时对 articleId=0 执行 completeArticleLike。
        if (articleId == 0L) {
            return;
        }

        // Go: userID, d, ok := parseVal(value); if !ok || userID == 0 || d == 0 { return nil }
        TaskArticleProgressEvent.Parsed parsed = TaskArticleProgressEvent.parse(objectMapper, value);
        if (!parsed.ok() || parsed.userId() == 0L || parsed.cur() == 0L) {
            return;
        }

        long userId = parsed.userId();
        long d = parsed.cur();

        // Go: article_sink.go:106-122
        //   cur, exits := c.delta[articleID]
        //   if !exits { c.delta[articleID] = articleAgg{UserID: userID, total: d}; return }
        //   if d > cur.total { cur.total = d; cur.UserID = userID; c.delta[articleID] = cur }
        // ⚠️ 注意「相等时不更新」与「连带更新 UserID」两处细节
        delta.merge(articleId, new ArticleAgg(userId, d),
                (existing, incoming) -> incoming.total() > existing.total() ? incoming : existing);
    }

    // ==================== 定时刷盘 ====================

    /**
     * 每秒刷一次 —— 对应 Go {@code article_sink.go:141-156} 的 ticker loop。
     *
     * <p>⚠️ Go 的 loop 同样忽略 flushOnce 的返回值（{@code _ = c.flushOnce(ctx)}）。
     */
    @Scheduled(fixedRateString = "${task.definitions.article.flush-every-ms:1000}")
    public void flushTick() {
        try {
            flushOnce();
        } catch (Exception e) {
            LOG.error("article like progress sink flush failed (delta for this batch is dropped, "
                    + "same as Go's swap-then-ignore behaviour)", e);
        }
    }

    /** 容器关闭前最后刷一次 —— 对应 Go {@code article_sink.go:147-149} 的 {@code ctx.Done()} 分支。 */
    @PreDestroy
    public void flushOnShutdown() {
        try {
            flushOnce();
            LOG.info("article like progress sink final flush done, remaining delta={}", delta.size());
        } catch (Exception e) {
            LOG.error("article like progress sink final flush failed", e);
        }
    }

    /**
     * 刷一批 —— 对应 Go {@code article_sink.go:158-181} 的 {@code flushOnce}。
     *
     * <p>⚠️ Go 只有<b>两步</b>：lazyInit + FlushRedis。没有第三步（用户路有 flushPostgres）。
     * <p>⚠️ Go 的 FlushRedis 失败会额外 {@code logx.Errorf(...)} 再 return（同用户路）。
     */
    void flushOnce() {
        Map<Long, ArticleAgg> batch = swap();
        if (batch.isEmpty()) {
            return;
        }

        Throwable err = MqConsumer.traceConsumer(
                Context.root(), "task-rpc", "ArticleLikeSinkConsumer.Flush",
                TaskTraceAttributes.flush(SPAN_DESTINATION, batch.size()),
                ctx -> {
                    lazyInitTaskIfNeeded(batch);
                    flushRedis(batch);
                    return null;
                });

        if (err != null) {
            throw asRuntime(err);
        }
    }

    private Map<Long, ArticleAgg> swap() {
        if (delta.isEmpty()) {
            return Map.of();
        }
        Map<Long, ArticleAgg> batch = new HashMap<>(delta);
        delta.clear();
        return batch;
    }

    // ==================== 第一步：懒初始化 ====================

    /**
     * 对应 Go {@code article_sink.go:230-328} 的 {@code lazyInitTaskIfNeeded}。
     *
     * <p>⚠️ 与用户路的四处差别：
     * <ol>
     *   <li>init key 是 {@code task:inits:<taskId>:<uid>:<articleId>}（<b>inits 复数、taskId 在前、
     *       带 articleId</b>）；</li>
     *   <li>TTL 是 24 小时（用户路 90 天）；</li>
     *   <li>{@code UserID == 0} 的条目<b>直接跳过</b>（{@code article_sink.go:252-254}）；</li>
     *   <li>HSET 的 key 是 {@code task:progress:<taskId>:<uid>:<articleId>}。</li>
     * </ol>
     */
    private void lazyInitTaskIfNeeded(Map<Long, ArticleAgg> batch) {
        // ---- 步骤 1：SETNX 闸门 ----
        // ⚠️ Go 的 items 切片**可能比 batch 小**（UserID==0 的被 skip）。
        //    但 Go 随后是 `for _, it := range items` 按 items 的下标去 map 里取 userID，
        //    而 SETNX 的结果是**按 items 自己的下标**对齐的 —— 也就是说 Go 靠 items 的顺序
        //    与 exec 顺序一致来配对。本实现用 (articleId,userId) 元组显式配对，语义等价。
        List<InitCandidate> candidates = new ArrayList<>(batch.size());
        for (Map.Entry<Long, ArticleAgg> e : batch.entrySet()) {
            if (e.getValue().userId() == 0L) {
                continue;   // Go: if ag.UserID == 0 { continue }
            }
            candidates.add(new InitCandidate(e.getKey(), e.getValue().userId()));
        }
        if (candidates.isEmpty()) {
            return;
        }

        List<Boolean> setNxResults = new ArrayList<>(candidates.size());
        for (List<InitCandidate> chunk : chunked(candidates, properties.getArticle().getRedisPipeMax())) {
            setNxResults.addAll(pipelineSetNx(chunk));
        }

        List<InitCandidate> newOnes = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (i < setNxResults.size() && Boolean.TRUE.equals(setNxResults.get(i))) {
                newOnes.add(candidates.get(i));
            }
        }
        if (newOnes.isEmpty()) {
            return;   // Go: if len(newOnes) == 0 { return nil }
        }

        // ---- 步骤 2a：HSET 进度 Hash ----
        long now = Instant.now().getEpochSecond();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("status", "doing");
        fields.put("progress", "0");
        fields.put("target", String.valueOf(target));
        fields.put("createAt", String.valueOf(now));
        fields.put("updateAt", String.valueOf(now));

        RedisPipelines.execQuietly(redis, ops -> {
            for (InitCandidate c : newOnes) {
                ops.opsForHash().putAll(
                        TaskRedisKeys.articleTaskProgress(taskId, c.userId(), c.articleId()), fields);
            }
        });

        // ---- 步骤 2b：PG 插一条初始行（DO NOTHING）----
        List<TaskProgressEntity> records = new ArrayList<>(newOnes.size());
        for (InitCandidate c : newOnes) {
            TaskProgressEntity e = new TaskProgressEntity();
            e.setUserId(c.userId());
            e.setTaskId(taskId);
            e.setStatus("doing");
            e.setProgress(0L);
            e.setTarget(target);
            records.add(e);
        }
        progressWriter.lazyInit(records);
    }

    private record InitCandidate(long articleId, long userId) {
    }

    /** 批量 {@code SETNX ... "1" EX 24h}，返回与入参同序的布尔结果（理由同用户点赞 sink）。 */
    private List<Boolean> pipelineSetNx(List<InitCandidate> chunk) {
        return RedisPipelines.toBooleans(RedisPipelines.exec(redis, ops -> {
            for (InitCandidate c : chunk) {
                ops.opsForValue().setIfAbsent(
                        TaskRedisKeys.articleTaskInit(taskId, c.userId(), c.articleId()), "1", initTtl);
            }
        }));
    }

    // ==================== 第二步：Redis 计数 + 进度推进 ====================

    /**
     * 对应 Go {@code article_sink.go:330-383} 的 {@code FlushRedis}。
     *
     * <p>⚠️ 与用户路同款结构：每个分块 Exec 成功后跑一遍<b>整批</b>的推进，
     * 且内层循环遍历整批（{@code article_sink.go:352-358}）。
     */
    private void flushRedis(Map<Long, ArticleAgg> batch) {
        int max = properties.getArticle().getRedisPipeMax();
        int n = 0;
        for (Map.Entry<Long, ArticleAgg> e : batch.entrySet()) {
            ArticleAgg agg = e.getValue();
            // Go: if ag.UserID == 0 || ag.total <= 0 { continue }
            if (agg.userId() == 0L || agg.total() <= 0L) {
                continue;
            }
            redis.opsForValue().set(TaskRedisKeys.articleLikeCount(e.getKey()),
                    String.valueOf(agg.total()), doneTtl);
            n++;
            if (n >= max) {
                advanceProgress(batch);
                n = 0;
            }
        }
        if (n > 0) {
            advanceProgress(batch);
        }
    }

    /** 推进整批进度 —— 对应 Go {@code FlushRedis} 里 exec 成功后的 for 循环。 */
    private void advanceProgress(Map<Long, ArticleAgg> batch) {
        for (Map.Entry<Long, ArticleAgg> e : batch.entrySet()) {
            ArticleAgg agg = e.getValue();
            if (agg.userId() == 0L || agg.total() <= 0L) {
                continue;
            }
            try {
                if (agg.total() >= target) {
                    completeArticleLike(agg.userId(), e.getKey(), agg.total());
                } else {
                    updateDoingTaskProgress(agg.userId(), agg.total());
                }
            } catch (Exception ex) {
                // ⚠️ Go: `_ = c.completeArticleLike(...)` / `_ = c.updateDoingTaskProgress(...)`
                LOG.warn("advance article like task progress failed (ignored, same as Go): "
                        + "articleId={} uid={} total={} err={}",
                        e.getKey(), agg.userId(), agg.total(), ex.getMessage());
            }
        }
    }

    /**
     * 推进"进行中"的进度 —— 对应 Go {@code article_sink.go:385-419}。
     *
     * <p>⚠️ 注意<b>入参只有 userID 与 progress</b>（没有 articleId），
     * 因为 PG 的冲突键是 {@code (user_id, task_id)}，与文章无关。
     * 这也意味着<b>同一用户的不同文章会合并成同一条进度</b>（取 GREATEST）——
     * Go 的语义如此，不改。
     */
    private void updateDoingTaskProgress(long userId, long progress) {
        progressWriter.updateDoing(userId, taskId, target, progress);
        invalidateTaskCache(userId);
    }

    /**
     * 标记完成 —— 对应 Go {@code article_sink.go:425-456} 的 {@code completeArticleLike}。
     *
     * <p>⚠️ 与用户路的四处差别：
     * <ol>
     *   <li>done key 是 {@code task:done:<taskId>:<userId>}（<b>taskId 在前</b>），TTL 120 天；</li>
     *   <li>Hash key <b>带 articleId</b>；</li>
     *   <li><b>HSET 的错误被忽略</b>（Go 是 {@code _, _ = c.rdb.HSet(...)}），
     *       用户路是检查 error；</li>
     *   <li>奖励分值传的是 <b>5</b>（{@code article_sink.go:447}），
     *       而该任务自己的 target 是 3 —— 两者<b>刻意不联动</b>。</li>
     * </ol>
     */
    private void completeArticleLike(long userId, long articleId, long total) {
        // Go: doneKey := "task:done:" + articleTaskID + ":" + userID
        //     ⚠️ 注释：每个人只能完成一次这个任务，所以选择 userID，而不是 articleID
        Boolean setNx = redis.opsForValue()
                .setIfAbsent(TaskRedisKeys.articleTaskDone(taskId, userId), "1", doneTtl);
        // Go: ok, err := SetNX(...); if err != nil || !ok { return err }
        if (!Boolean.TRUE.equals(setNx)) {
            return;
        }

        long nowUnix = Instant.now().getEpochSecond();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("status", "done");
        fields.put("doneAt", String.valueOf(nowUnix));
        fields.put("progress", String.valueOf(target));
        fields.put("updateAt", String.valueOf(nowUnix));
        // ⚠️ Go: _, _ = c.rdb.HSet(...) ⇒ 错误被丢弃，用户路是检查后 return
        try {
            redis.opsForHash().putAll(TaskRedisKeys.articleTaskProgress(taskId, userId, articleId), fields);
        } catch (Exception e) {
            LOG.warn("write article task progress hash failed (Go ignores this error): "
                    + "uid={} articleId={} err={}", userId, articleId, e.getMessage());
        }

        // ⚠️ Go: reward.NewEvent(strconv.Itoa(int(userID)), strconv.Itoa(articleTaskID), 5)
        //    ⚠️ 分值是 5，不是 target(3)
        rewardPublisher.enqueue(String.valueOf(userId), String.valueOf(taskId), 5L);

        progressWriter.markDone(userId, taskId, target);
        invalidateTaskCache(userId);
    }

    /** 删除 {@code task:progress:<uid>} 缓存 —— 对应 Go {@code article_sink.go:421-423}。 */
    private void invalidateTaskCache(long userId) {
        redis.delete(TaskRedisKeys.userProgressCache(userId));
    }

    /** 仅用于测试断言聚合行为。 */
    Map<Long, ArticleAgg> snapshotDelta() {
        return new HashMap<>(delta);
    }

    private static <T> List<List<T>> chunked(List<T> src, int size) {
        List<List<T>> out = new ArrayList<>();
        if (size <= 0) {
            return out;
        }
        for (int i = 0; i < src.size(); i += size) {
            out.add(src.subList(i, Math.min(src.size(), i + size)));
        }
        return out;
    }

    private static RuntimeException asRuntime(Throwable t) {
        if (t instanceof RuntimeException re) {
            return re;
        }
        return new IllegalStateException(t);
    }

    /** Go {@code strconv.ParseInt(key, 10, 64)}，失败即返回 error。 */
    private static long parseLongOrThrow(String key) {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid key: " + key, e);
        }
    }
}