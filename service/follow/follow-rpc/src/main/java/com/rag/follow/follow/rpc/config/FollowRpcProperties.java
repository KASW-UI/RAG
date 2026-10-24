package com.rag.follow.follow.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * follow-rpc 的业务配置 —— 逐条对应 Go 端
 * {@code service/follow/rpc/internal/config/config.go:14-32} 的 {@code ListConf} / {@code RecommendConf}。
 *
 * <p><b>⚠️ 这 6 个数值全部参与业务分支，不是性能开关</b>：
 * 它们决定「列表默认返回几行」「limit 被截断到多少」「推荐展开几层」「每层扇出多少」，
 * 每一项都会改变 RPC 的可观察输出。改 yaml = 改行为，必须走 review。
 *
 * <p><b>为什么每个字段都带 {@link DefaultValue}</b>：
 * Go 侧这些默认值写在 <b>struct tag</b> 上（{@code `json:",default=20"`}），
 * 由 go-zero 的 {@code conf.MustLoad} 在 yaml 缺字段时应用 ——
 * 即「不配也有值」。Java 侧若只写 yaml 而不给默认值，
 * 一旦某个部署环境裁掉该段就会变成 {@code 0}，而 {@code 0} 在下游有<b>确定且不同</b>的含义：
 * <ul>
 *   <li>{@code defaultLimit = 0} ⇒ {@code normalizeListReq} 不再替换 limit ⇒ <b>不限行数</b></li>
 *   <li>{@code maxLimit = 0} ⇒ {@code if maxLimit > 0 && limit > maxLimit} 整条短路 ⇒ <b>不截断</b></li>
 *   <li>{@code maxDepth = 0} ⇒ 被 {@code clampRecommendDepth} 夹回 <b>3</b>（救回来了）</li>
 *   <li>{@code maxFanout = 0} ⇒ 被 {@code clampFanout} 夹回 <b>50</b>（救回来了）</li>
 * </ul>
 * 前两项是<b>静默的行为变化</b>（默认多返回 / 不截断），所以默认值必须内建，不能只靠 yaml。
 *
 * <p><b>yaml 前缀 {@code follow}</b>：与 {@code follow.rpc} / {@code FollowRpcProperties} 同源；
 * Spring Boot 2.0 之后不再支持 kebab-case ↔ camelCase 的松散绑定之外的别名，
 * 因此 yaml 里的 {@code default-limit} 与字段名 {@code defaultLimit} 是严格对应关系。
 */
@ConfigurationProperties(prefix = "follow")
public class FollowRpcProperties {

    /** 对应 Go {@code ListConf}（GetFollowList / GetBlockList / GetFollowerList 共用）。 */
    private final ListConf list;

    /** 对应 Go {@code RecommendConf}（只有 GetRecommendations 用）。 */
    private final RecommendConf recommend;

    public FollowRpcProperties(@DefaultValue ListConf list, @DefaultValue RecommendConf recommend) {
        this.list = list;
        this.recommend = recommend;
    }

    public ListConf getList() {
        return list;
    }

    public RecommendConf getRecommend() {
        return recommend;
    }

    /**
     * 对应 Go {@code type ListConf struct { DefaultLimit int32 `json:",default=20"`; MaxLimit int32 `json:",default=100"` }}。
     *
     * <p>两个字段<b>不校验</b>：Go 侧同样没有校验，0 / 负数会照原样流进
     * {@code normalizeListReq}（helper.go:37-56）并产生上面注释里描述的那套语义。
     * 在这里加 {@code @Min} 校验会把「不配 0」从「不限行数」变成「启动失败」—— 那是新增行为。
     */
    public static class ListConf {

        /** 对应 Go {@code DefaultLimit int32 `json:",default=20"`}。 */
        private final int defaultLimit;

        /** 对应 Go {@code MaxLimit int32 `json:",default=100"`}。 */
        private final int maxLimit;

        public ListConf(@DefaultValue("20") int defaultLimit, @DefaultValue("100") int maxLimit) {
            this.defaultLimit = defaultLimit;
            this.maxLimit = maxLimit;
        }

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }
    }

    /**
     * 对应 Go {@code type RecommendConf struct { DefaultLimit int32 `json:",default=20"`;
     * MaxLimit int32 `json:",default=50"`; MaxDepth int `json:",default=3"`; MaxFanout int `json:",default=50"` }}。
     *
     * <p>⚠️ {@code MaxLimit} 是 <b>50</b>，与 {@link ListConf#getMaxLimit()} 的 100 <b>不是同一个值</b>。
     * Go 侧它们分属两个 struct，推荐接口的天花板比列表接口低一半。
     */
    public static class RecommendConf {

        /** 对应 Go {@code DefaultLimit int32 `json:",default=20"`}。 */
        private final int defaultLimit;

        /** 对应 Go {@code MaxLimit int32 `json:",default=50"`}。 */
        private final int maxLimit;

        /** 对应 Go {@code MaxDepth int `json:",default=3"`}。 */
        private final int maxDepth;

        /** 对应 Go {@code MaxFanout int `json:",default=50"`}。 */
        private final int maxFanout;

        public RecommendConf(@DefaultValue("20") int defaultLimit,
                             @DefaultValue("50") int maxLimit,
                             @DefaultValue("3") int maxDepth,
                             @DefaultValue("50") int maxFanout) {
            this.defaultLimit = defaultLimit;
            this.maxLimit = maxLimit;
            this.maxDepth = maxDepth;
            this.maxFanout = maxFanout;
        }

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public int getMaxFanout() {
            return maxFanout;
        }
    }
}