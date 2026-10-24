package com.rag.message.message.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * message-rpc 业务配置 —— 对应 Go 端 {@code service/message/rpc/internal/config/config.go}：
 *
 * <pre>
 * type Config struct {
 *     zrpc.RpcServerConf
 *     Postgres  Postgres      // 由 spring.datasource.* 接管
 *     UserRpc   zrpc.RpcClientConf   // 由 grpc.client.user-rpc 接管
 *     AdminRpc  zrpc.RpcClientConf   // 由 grpc.client.admin-rpc 接管
 *     List      ListConf
 *     Broadcast BroadcastConf
 * }
 *
 * type ListConf struct {
 *     DefaultLimit int32 `json:",default=20"`
 *     MaxLimit     int32 `json:",default=100"`
 * }
 * type BroadcastConf struct {
 *     PageSize int64 `json:",default=200"`
 * }
 * </pre>
 *
 * <p>字段 1:1 对齐 —— Go int32 对 Java {@code int}；Go int64 对 Java {@code long}。
 *
 * <p>Go 的 RpcServerConf / Postgres / UserRpc / AdminRpc 由 Spring Boot application.yaml 直接接管
 * （grpc.server / spring.datasource / grpc.client.user-rpc / grpc.client.admin-rpc），
 * 所以这里只承载 List + Broadcast 两块 message 服务专属业务配置。
 *
 * <p>绑定规则：用 {@code @ConfigurationProperties("message")} + {@code @Component} 把
 * application.yaml 里 {@code message.list.*} / {@code message.broadcast.*} 注入进来。
 * Logic 层通过构造器注入，从 {@link #getList()} / {@link #getBroadcast()} 读 default/max/PageSize
 * 阈值（与 Go 端 {@code normalizeLimit} + {@code collectBroadcastRecipients} 行为 1:1）。
 */
@Component
@ConfigurationProperties(prefix = "message")
public class MessageProperties {

    /**
     * 列表查询（ListNotifications / ListConversations / GetConversationMessages）的 limit 兜底配置。
     * 对应 Go {@code ListConf}：{@code DefaultLimit = 20}, {@code MaxLimit = 100}。
     */
    private List list = new List();

    /**
     * 广播发送通知的 PageSize 阈值。
     * 对应 Go {@code BroadcastConf}：{@code PageSize = 200}。
     */
    private Broadcast broadcast = new Broadcast();

    public List getList() { return list; }
    public void setList(List list) { this.list = list; }
    public Broadcast getBroadcast() { return broadcast; }
    public void setBroadcast(Broadcast broadcast) { this.broadcast = broadcast; }

    /** 对应 Go {@code ListConf}。 */
    public static class List {

        /** 对应 Go {@code DefaultLimit int32 json:",default=20"}。 */
        private int defaultLimit = 20;

        /** 对应 Go {@code MaxLimit int32 json:",default=100"}。 */
        private int maxLimit = 100;

        public int getDefaultLimit() { return defaultLimit; }
        public void setDefaultLimit(int defaultLimit) { this.defaultLimit = defaultLimit; }
        public int getMaxLimit() { return maxLimit; }
        public void setMaxLimit(int maxLimit) { this.maxLimit = maxLimit; }
    }

    /** 对应 Go {@code BroadcastConf}。 */
    public static class Broadcast {

        /**
         * 对应 Go {@code PageSize int64 json:",default=200"}：
         * 广播发送通知时分页拉取 admin.GetUserList 的页大小。
         */
        private long pageSize = 200;

        public long getPageSize() { return pageSize; }
        public void setPageSize(long pageSize) { this.pageSize = pageSize; }
    }
}