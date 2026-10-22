package com.rag.like.like.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * like 域 Kafka 主题 —— 对应 Go 端各处 {@code PusherConf} / {@code KqConf} 的 Topic 字段。
 *
 * <p>⚠️ <b>「生产与消费同一个 topic」</b>是 Go 的现状，不是笔误：
 * <ul>
 *   <li>生产者：{@code like.yaml} 的 {@code KafkaConf.Topic: like_promotion_topic}
 *       （{@code LikeAction} 推 {@code KafkaLikeMsg}）；</li>
 *   <li>消费者：{@code mq.yaml} 的 {@code Kafka.Topic: like_promotion_topic}
 *       （group=like-update-group，offset=first）。</li>
 * </ul>
 * 两个 topic 值一致，很多写法（尤其 MyBatis/配置生成工具）会误判成配置冲突而"修正"其中一个，
 * 这里显式说明以防被改。
 *
 * <p><b>四个主题分属三条互不相干的下游链路</b>，不要合并：
 * <table border="1">
 *   <caption>主题归属</caption>
 *   <tr><th>主题</th><th>Go 出处</th><th>用途</th></tr>
 *   <tr><td>{@code like_promotion_topic}</td><td>{@code KafkaConf.Topic} + {@code mq.yaml Kafka.Topic}</td>
 *       <td>本域自产自消：写路径推事件、消费路径落库</td></tr>
 *   <tr><td>{@code article-hot-events}</td><td>{@code mq.yaml HotEventPusherConf.Topic}</td>
 *       <td>outbox relay 推给<b>热点排名系统</b></td></tr>
 *   <tr><td>{@code task.logs.filtered.User}</td><td>{@code TaskUserPusherConf.Topic}</td>
 *       <td>推给<b>任务/推广结算系统</b>（可选，未配置则整段跳过）</td></tr>
 *   <tr><td>{@code task.logs.filtered.Article}</td><td>{@code TaskArticlePusherConf.Topic}</td>
 *       <td>同上，另一路</td></tr>
 * </table>
 */
@ConfigurationProperties(prefix = "like.kafka")
public class LikeKafkaTopics {

    /** Go {@code KafkaConf.Topic}，同时是 Go {@code mq.yaml} 的消费 Topic。 */
    private String likeTopic = "like_promotion_topic";

    /** Go {@code TaskUserPusherConf.Topic}。 */
    private String taskUserTopic = "task.logs.filtered.User";

    /** Go {@code TaskArticlePusherConf.Topic}。 */
    private String taskArticleTopic = "task.logs.filtered.Article";

    /** Go {@code mq.yaml HotEventPusherConf.Topic}。 */
    private String hotEventTopic = "article-hot-events";

    /**
     * 两个 Task*Pusher 是否启用 —— 对应 Go {@code newOptionalPusher}：
     *
     * <pre>
     * func newOptionalPusher(conf kq.KqConf) *kq.Pusher {
     *     if len(conf.Brokers) == 0 || conf.Topic == "" { return nil }
     *     return kq.NewPusher(conf.Brokers, conf.Topic)
     * }
     * </pre>
     *
     * <p>Go 是<b>运行期判空</b>（指针为 nil 则整段逻辑跳过），
     * 所以这里<b>不能</b>用 {@code @ConditionalOnProperty} 把 Bean 做成「不存在」——
     * 那会让 {@code LikeTaskProgressPublisher} 拿不到可选依赖而启动失败。
     * 正确做法是保留 Bean、把开关透传下去，让业务代码走 Go 相同的 nil 分支。
     */
    private boolean taskPushersEnabled = true;

    public String getLikeTopic() {
        return likeTopic;
    }

    public void setLikeTopic(String likeTopic) {
        this.likeTopic = likeTopic;
    }

    public String getTaskUserTopic() {
        return taskUserTopic;
    }

    public void setTaskUserTopic(String taskUserTopic) {
        this.taskUserTopic = taskUserTopic;
    }

    public String getTaskArticleTopic() {
        return taskArticleTopic;
    }

    public void setTaskArticleTopic(String taskArticleTopic) {
        this.taskArticleTopic = taskArticleTopic;
    }

    public String getHotEventTopic() {
        return hotEventTopic;
    }

    public void setHotEventTopic(String hotEventTopic) {
        this.hotEventTopic = hotEventTopic;
    }

    public boolean isTaskPushersEnabled() {
        return taskPushersEnabled;
    }

    public void setTaskPushersEnabled(boolean taskPushersEnabled) {
        this.taskPushersEnabled = taskPushersEnabled;
    }
}