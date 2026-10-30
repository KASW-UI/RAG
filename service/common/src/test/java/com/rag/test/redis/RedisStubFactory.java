package com.rag.test.redis;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>「会记录命令」的 stub Redis 连接工厂</b>（共享层守门用）。
 *
 * <h2>为什么不用 Mockito</h2>
 * <p>{@code RedisConnection} 上有 <b>274 个</b>命令 default 方法（javap 实测）⇒ 用 Mockito
 * 逐个 stub 不现实 ⇒ ⇒ 用 <b>JDK 动态代理</b> ⇒ ⇒ 只记「哪个方法被调过」。
 *
 * <h2>它证明的是什么</h2>
 * <p>它让守门能精确断言「<b>那条命令到底有没有被下发</b>」——
 * 这正是「Redis 感知请求预算」这一层<b>唯一</b>的可观察点。
 * ⇒ ⇒ 它<b>不</b>证明「与真实 Redis 的行为一致」（那需要双侧活体对拍 ⇒ 已登记为待办）。
 */
public final class RedisStubFactory {

    /** 被调用过的命令名（按调用顺序）。 */
    private static final List<String> RECORDED = Collections.synchronizedList(new ArrayList<>());

    private RedisStubFactory() {
    }

    /** 已被调用的命令名（快照）。 */
    public static List<String> recorded() {
        synchronized (RECORDED) {
            return new ArrayList<>(RECORDED);
        }
    }

    /** 清空记录。 */
    public static void reset() {
        RECORDED.clear();
    }

    /** 一个「会记录命令」的 stub 连接。 */
    public static RedisConnection stubConnection() {
        InvocationHandler handler = (proxy, method, args) -> {
            // ⚠️ Object 的方法必须**直接**处理 ⇒ 再 invoke 回代理会无限递归
            switch (method.getName()) {
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "stub-redis-connection";
                }
                default -> {
                    // fall through：记一笔命令
                }
            }
            RECORDED.add(method.getName());
            return null;
        };
        return (RedisConnection) Proxy.newProxyInstance(
                RedisConnection.class.getClassLoader(),
                new Class<?>[]{RedisConnection.class},
                handler);
    }

    /** stub factory：永远返回上面那个「会记录命令」的连接。 */
    public static RedisConnectionFactory stubFactory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getConnection" -> {
                            return stubConnection();
                        }
                        case "getConvertPipelineAndTxResults" -> {
                            return Boolean.FALSE;
                        }
                        case "translateExceptionIfPossible" -> {
                            // stub 的翻译策略 = 翻成标准 DataAccessException
                            // （真实 Lettuce 走 ExceptionTranslationStrategy）
                            return new org.springframework.dao.QueryTimeoutException("stub translate");
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "toString" -> {
                            return "stub-redis-connection-factory";
                        }
                        default -> {
                            return null;
                        }
                    }
                });
    }
}
