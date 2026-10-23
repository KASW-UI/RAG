package com.rag.task.task.rpc.mq;

import com.rag.task.task.rpc.config.TaskKafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户点赞计数 —— 1:1 对应 Go 端 {@code internal/Init/initGoKa.go:72-82} 的
 * {@code processUserCount} 与 {@code startTaskUserGoKa} 的 goka group
 * （{@code initGoKa.go:164-169}）。
 *
 * <h2>Go 的组定义</h2>
 * <pre>
 * goka.DefineGroup(
 *     goka.Group(groupTaskUser),                                          // task.rpc.consumer61
 *     goka.Input(goka.Stream(rawTopicTaskUser), new(codec.Bytes), processUserCount),
 *     goka.Persist(new(codec.Int64)),                                     // ← 每 key 本地累加器
 *     goka.Output(goka.Stream(outTopicTaskUser), new(codec.Int64)),       // task.logs.filtered.User
 * )
 * </pre>
 *
 * <h2>🔴 value 是<b>十进制字符串</b>，不是 8 字节二进制</h2>
 * <p>这一条曾经是本迁移最危险的猜测点。goka 的 {@code codec.Int64} 实现是
 * {@code []byte(strconv.FormatInt(intVal, 10))}（{@code goka/codec/codec.go:56}）——
 * <b>它编码成十进制 ASCII，不是网络字节序的 int64</b>。
 * 若按"goka 是 table-store、应该是二进制"去实现，下游 sink 的
 * {@code strconv.ParseInt(val, 10, 64)} 会全部失败。
 *
 * <p>另一侧同样重要：消费端 go-queue 的 {@code Consume(ctx, key, val string)}
 * 用的是 {@code string(msg.Value)}（{@code go-queue/kq/queue.go:230}），
 * <b>没有任何反序列化器</b>。所以 application.yml 里
 * {@code value-deserializer} 必须是 {@code StringDeserializer}，用 ByteArray 会改变行为。
 *
 * <h2>🔴 与 like-rpc 共用同一个输出 topic</h2>
 * <p>{@code task.logs.filtered.User} 同时被 Go/Java 的 like-rpc 用
 * {@code PushWithKey(userId, decimalString)} 生产（{@code likeactionlogic.go:317-320}）。
 * <b>两边的 value 形状恰好都是十进制字符串</b>，所以能共存。
 * 若把本条改成二进制，like-rpc 推来的进度会全部退化成 1（sink 解析失败回落默认值）。
 */
@Component
public class TaskUserCountConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TaskUserCountConsumer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TaskKafkaTopics topics;
    private final TaskCountStore countStore;

    public TaskUserCountConsumer(KafkaTemplate<String, String> kafkaTemplate,
                                 TaskKafkaTopics topics,
                                 TaskCountStore countStore) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.countStore = countStore;
    }

    /**
     * 按 key 累加计数，并把新计数 emit 到 filtered.User。
     *
     * <p>⚠️ <b>无失败分支</b>：Go 的 {@code processUserCount} 唯一会出错的是
     * {@code v.(int64)} 的类型断言，而 goka 用 {@code codec.Int64} 保证了类型 ⇒ 实际不会触发。
     * 所以本方法<b>不主动抛异常</b>（位点照常提交），与 Go 一致。
     *
     * <p>⚠️ 唯一的例外是 <b>计数器存储本身</b>不可用（Redis 挂）——那种情况下
     * {@link TaskCountStore#increment} 会抛，位点不提交、该条重投，
     * 对应 Go 侧 goka store 写失败时的退避重投。
     */
    @KafkaListener(id = "task-goka-user-count",
            topics = "${task.kafka.raw-user-topic}",
            groupId = "${task.kafka.group-goka-user}",
            // ⚠️ goka 硬编码 OffsetOldest（initGoKa.go:173），与 kq 那三处的 "latest" 相反
            properties = "auto-offset-reset=earliest",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record) {
        String key = record.key() == null ? "" : record.key();

        // Go: cur := ctx.Value()（首条为 nil ⇒ 0）; cur++; ctx.SetValue(cur)
        // ⚠️ group 作为命名空间传入：Go 侧两个 goka processor 各有独立 store
        //    （goka processor.go:82 按 group 名分 LevelDB 目录），见 TaskCountStore 类注释。
        long cur = countStore.increment(topics.getGroupGokaUser(), key);

        // Go: ctx.Emit(goka.Stream(outTopicTaskUser), ctx.Key(), cur)
        try {
            kafkaTemplate.send(topics.getOutUserTopic(), key, String.valueOf(cur));
        } catch (Exception e) {
            // goka 的 ctx.Emit 不返回错误；失败只有 goka 自己重试。
            // 这里同样只记日志、不抛异常 ⇒ 位点照常提交，与 Go 一致。
            LOG.warn("emit task user count failed: key={} cur={} err={}", key, cur, e.getMessage());
        }
    }
}