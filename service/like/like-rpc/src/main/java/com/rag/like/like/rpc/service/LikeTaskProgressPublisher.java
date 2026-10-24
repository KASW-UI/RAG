package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.mq.LikeEventContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 推广任务进度推送 —— 1:1 对应 Go 端
 * {@code service/like/rpc/internal/logic/likeactionlogic.go:311-343} 的 {@code syncTaskProgress}。
 *
 * <h2>⚠️ 两个 pusher 在 Go 里是<b>可为 nil 的可选依赖</b></h2>
 *
 * <p>Go {@code servicecontext.go:50-56}：
 * <pre>
 * func newOptionalPusher(conf kq.KqConf) *kq.Pusher {
 *     if len(conf.Brokers) == 0 || conf.Topic == "" { return nil }
 *     return kq.NewPusher(conf.Brokers, conf.Topic)
 * }
 * </pre>
 * 随后 {@code syncTaskProgress} 里是 {@code if l.svcCtx.TaskUserPusher != nil { ... }} 这种
 * <b>运行期判空</b>。所以「没配 broker/topic ⇒ 这一整段推送被跳过」是 Go 的既定行为。
 *
 * <p>⇒ Java 侧<b>不能</b>用 {@code @ConditionalOnProperty} 把这个 Bean 做成「不存在」：
 * 那会让构造注入失败、整个上下文起不来。正确做法是保留 Bean、把
 * {@link LikeKafkaTopics#isTaskPushersEnabled()} 透传下来，让本类走与 Go 相同的「跳过」分支。
 *
 * <h2>失败语义</h2>
 *
 * <p>Go 侧两次 push 失败都只 {@code LogBusinessErr(... ErrorKafkaPush ...)}，不影响点赞 RPC 返回值。
 * 本实现同样<b>吞掉异常</b>。
 *
 * <h2>推送内容</h2>
 * <ul>
 *   <li>user 路：{@code PushWithKey(userId, totalLikesGiven)} —— <b>裸字符串</b>，key=userId、value=计数，
 *       <b>不是 JSON</b>；</li>
 *   <li>article 路：{@code PushWithKey(targetId, json(TaskArticleProgressMsg))}。</li>
 * </ul>
 */
@Component
public class LikeTaskProgressPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(LikeTaskProgressPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LikeKafkaTopics topics;
    private final ObjectMapper objectMapper;

    public LikeTaskProgressPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     LikeKafkaTopics topics,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.objectMapper = objectMapper;
    }

    /**
     * 对应 Go {@code syncTaskProgress(in, userListKey, articleLikes)}。
     *
     * @param userId        点赞者（user 路的 key）
     * @param authorId      作者；Go 侧 {@code in.AuthorId <= 0} 时<b>直接 return</b>，不推 article 路
     * @param targetId      目标（article 路的 key）
     * @param userLikesGiven 该用户给出的赞总数（= max(ZCARD, DB count)）
     * @param articleLikes  该目标的赞总数（= max(Redis Hash, DB count)）
     */
    public void publish(long userId, long authorId, String targetId,
                        long userLikesGiven, long articleLikes) {

        // ---------- 路 1：用户进度 ----------
        // Go: if l.svcCtx.TaskUserPusher != nil { … } else 整段跳过
        // ⚠️ 2026-10-23 修正：Go 的两路是**各自独立判空**
        // （servicecontext.go:43-44 分别 newOptionalPusher(TaskUserPusherConf / TaskArticlePusherConf)），
        // 此前这里两路共用一个 isTaskPushersEnabled()，丢失了粒度（DRIFT-LIKE-012）。
        if (topics.isTaskUserPusherEnabled()) {
            // Go: totalLikesGiven > 0 才推（0 不推，避免给下游发无意义的零进度）
            if (userLikesGiven > 0) {
                try {
                    kafkaTemplate.send(topics.getTaskUserTopic(),
                            String.valueOf(userId),
                            String.valueOf(userLikesGiven));
                } catch (Exception e) {
                    // Go: LogBusinessErr(..., ErrorKafkaPush, "push task user progress failed")
                    LOG.warn("push task user progress failed: userId={} err={}", userId, e.getMessage());
                }
            }
        }

        // ---------- 路 2：文章进度 ----------
        // Go: if l.svcCtx.TaskArticlePusher == nil || in.AuthorId <= 0 || articleLikes <= 0 { return }
        if (!topics.isTaskArticlePusherEnabled() || authorId <= 0 || articleLikes <= 0) {
            return;
        }

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(
                    new LikeEventContracts.TaskArticleProgressMsg(String.valueOf(authorId), articleLikes));
        } catch (Exception e) {
            // Go: LogBusinessErr(..., ErrorJsonMarshal, "marshal task article progress failed"); return
            LOG.warn("marshal task article progress failed: authorId={} err={}", authorId, e.getMessage());
            return;
        }

        try {
            kafkaTemplate.send(topics.getTaskArticleTopic(), targetId, payload);
        } catch (Exception e) {
            // Go: LogBusinessErr(..., ErrorKafkaPush, "push task article progress failed")
            LOG.warn("push task article progress failed: targetId={} err={}", targetId, e.getMessage());
        }
    }
}