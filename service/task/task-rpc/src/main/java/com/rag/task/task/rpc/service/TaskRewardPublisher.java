package com.rag.task.task.rpc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * 任务完成奖励事件发布 —— 1:1 对应 Go 端 {@code internal/reward/producer.go}
 * 的 {@code Product.Enqueue} 与 {@code internal/reward/type.go} 的 {@code NewEvent}。
 *
 * <h2>⚠️ 本模块只写不读；消费方在 Go 侧<b>没有启动</b></h2>
 * <p>{@code reward.Worker}（{@code worker.go}）与 {@code Init.StartInitReward}
 * （{@code initReward.go}）<b>整个文件都是注释</b>，
 * {@code task.go:35} 的 {@code go Init.StartInitReward(ctx)} 调用也被注释了 ——
 * 因为它依赖的 {@code PointsClient} 在 {@code service_context.go:18-19} 就被注释掉了。
 * ⇒ 线上 {@code stream:task:reward} 里只有堆积的 XADD 记录，<b>没有任何消费者</b>。
 * Java 侧刻意<b>不</b>补这个消费者：那是新增行为（会引入对 points 域的依赖），
 * 而 points 域<b>不在本项目的范围内</b>（AGENTS.md §1）。
 *
 * <h2>⚠️ 幂等 ID 是 {@code uid + taskID} 的<b>加法</b>，不是拼接</h2>
 * <p>Go {@code NewEvent}（{@code type.go:25}）：
 * <pre>RewardID: uid + taskID</pre>
 * 例如 uid=1/taskID=1902 ⇒ 1903；uid=1903/taskID=1 也 ⇒ 1904 但数值相同的情形存在。
 * 这<b>不是</b>无碰撞的幂等键，但它是 Go 的既定行为 ——
 * 改成字符串拼接会让 reward_id 数值变化，属于可观察契约变更。
 *
 * <h2>字段名是 L1 契约</h2>
 * <p>{@code producer.go:23-27} 的 map key 即 Stream 里的字段名：
 * {@code reward_id} / {@code uid} / {@code task_id} / {@code ts} / {@code score}。
 * ⚠️ 注意写出的是 {@code score}，而读取方 {@code parseRedisEvent}（{@code worker.go:116-121}）
 * 读的是 {@code add_score} —— <b>两端对不上</b>，读出来永远是 0。
 * 这是 Go 的现状；虽然 Worker 没启动，但字段名照抄不抄错。
 */
@Component
public class TaskRewardPublisher {

    private final StringRedisTemplate redis;
    private final String streamKey;

    public TaskRewardPublisher(StringRedisTemplate redis,
                               @Value("${task.reward.stream-key:stream:task:reward}") String streamKey) {
        this.redis = redis;
        this.streamKey = streamKey;
    }

    /**
     * 对应 Go {@code reward.Product.Enqueue}。
     *
     * <p>⚠️ <b>返回值语义相反，别照抄</b>：Go 的 sink 是
     * {@code if err := c.rewardProduct.Enqueue(ctx, ev); err != nil { return err }}
     * —— <b>失败会让整个 complete 流程提前返回</b>
     * （于是 {@code markTaskDoneInDB} 与缓存失效都不会执行）。
     * 本方法必须<b>把异常抛给调用方</b>，否则会新增一条 Go 没有的"失败仍继续"路径。
     *
     * @param uid      用户 ID（Go 侧是 string，内部 {@code strconv.ParseInt}）
     * @param taskId   任务 ID
     * @param addScore 奖励分值 —— ⚠️ 两个 sink 都传 <b>5</b>：
     *                 点赞路 {@code like_sink.go:355} 传 5；
     *                 文章路 {@code article_sink.go:447} 对 taskId=190 也传 <b>5</b>，
     *                 而该任务自己的 target 是 3。两者<b>刻意不联动</b>，照抄。
     */
    public void enqueue(String uid, String taskId, long addScore) {
        long parsedUid = parseLongOrZero(uid);
        long parsedTaskId = parseLongOrZero(taskId);
        long rewardId = parsedUid + parsedTaskId;   // ⚠️ Go 是加法，见类注释
        long ts = Instant.now().getEpochSecond();    // Go: time.Now().Unix()

        Map<String, String> fields = Map.of(
                "reward_id", String.valueOf(rewardId),
                "uid", String.valueOf(parsedUid),
                "task_id", String.valueOf(parsedTaskId),
                "ts", String.valueOf(ts),
                "score", String.valueOf(addScore));

        redis.opsForStream().add(MapRecord.create(streamKey, fields));
    }

    /**
     * Go {@code strconv.ParseInt(_uid, 10, 64)} 且<b>丢弃 error</b>（{@code uid, _ := ...}）
     * ⇒ 非法输入静默变成 0。
     */
    private static long parseLongOrZero(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 仅用于诊断：当前配置的 stream key。 */
    public String streamKey() {
        return streamKey;
    }
}