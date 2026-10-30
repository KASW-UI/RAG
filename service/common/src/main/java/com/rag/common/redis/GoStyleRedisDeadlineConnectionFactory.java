package com.rag.common.redis;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisClusterConnection;

/**
 * <b>把「请求预算」接到 {@link RedisConnectionFactory} 上（域侧零改动的那一环）。</b>
 *
 * <h2>装配链</h2>
 * <pre>
 * RedisTemplate / StringRedisTemplate（各域注入的那两个 Bean）
 *   └─ getConnection() ──▶ GoStyleRedisDeadlineConnectionFactory（本类）
 *          └─ delegate.getConnection() ──▶ 真实连接
 *          └─ GoStyleRedisDeadlineConnection.wrap(...) ──▶ 预算感知代理
 * </pre>
 * ⇒ ⇒ **各域的注入点、调用点一行都不用改** ⇒ 命令照样发出去，只是
 * <b>「请求预算已耗尽」时不再发出去</b>。
 *
 * <h2>为什么不包装集群 / 哨兵连接</h2>
 * <p>本仓未使用 Redis 集群 / 哨兵 ⇒ ⇒ 这两个方法<b>直接透传</b>（不包装 ⇒ 也不误伤）。
 */
public class GoStyleRedisDeadlineConnectionFactory implements RedisConnectionFactory {

    private final RedisConnectionFactory delegate;
    private final boolean enabled;

    public GoStyleRedisDeadlineConnectionFactory(RedisConnectionFactory delegate, boolean enabled) {
        this.delegate = delegate;
        this.enabled = enabled;
    }

    /** 包装前的真实 factory（供守门断言「确实是包出来的」）。 */
    public RedisConnectionFactory delegate() {
        return delegate;
    }

    @Override
    public RedisConnection getConnection() {
        RedisConnection connection = delegate.getConnection();
        return enabled ? GoStyleRedisDeadlineConnection.wrap(connection) : connection;
    }

    @Override
    public RedisClusterConnection getClusterConnection() {
        // ⚠️ 不包装：集群面本仓未使用（包装器只保证单机连接的语义）
        return delegate.getClusterConnection();
    }

    @Override
    public RedisSentinelConnection getSentinelConnection() {
        return delegate.getSentinelConnection();
    }

    @Override
    public boolean getConvertPipelineAndTxResults() {
        return delegate.getConvertPipelineAndTxResults();
    }

    /**
     * 异常翻译：**原样委托**给真实 factory。
     *
     * <p>⚠️ <b>为什么必须委托而不是自己实现</b>：{@code RedisConnectionFactory} 继承了
     * {@code PersistenceExceptionTranslator} ⇒ 真实实现（Lettuce）会走
     * {@code ExceptionTranslationStrategy} 把底层异常翻成 {@code DataAccessException}
     * ⇒ ⇒ 若本层自己实现 ⇒ 域侧看到的异常类型会<b>与生产不一致</b>
     * ⇒ ⇒ 委托 ⇒ 翻译行为**逐字保持生产形态**。
     */
    @Override
    public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        return delegate.translateExceptionIfPossible(ex);
    }
}
