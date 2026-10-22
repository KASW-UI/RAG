package com.rag.hot.hot.api.service;

import com.rag.hot.hot.api.dto.HotArticleCard;
import com.rag.hot.hot.api.dto.HotArticlesReq;
import com.rag.hot.hot.api.dto.HotArticlesResp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分页数值契约的<b>集中地</b> —— 对应 Go {@code getHotArticlesLogic.go:172-208} 的
 * {@code normalizePagination} / {@code paginate}，以及 {@code hydrateArticles}
 * 末段的 rank 重算（{@code :114-123}）。
 *
 * <h2>为什么独立成一个类</h2>
 * 这三段是本组件里<b>唯一不含 RPC、不含 IO、不含分支副作用</b>的代码，
 * 却是 L1 契约最密的地方（1 / 20 / 50 / 100 四个阈值 + int32 溢出 + 跨页稠密 rank）。
 * 它们留在 {@code HotArticleQueryService} 里就只能靠「跑一遍完整链路」来验证，
 * 而 {@code HotRankPaginatorTest} 可以把每条边界钉死，不需要任何 mock。
 *
 * <h2>⚠️ 三条最容易被「顺手优化」掉的语义</h2>
 *
 * <h3>1. {@code int32} 乘法溢出 → 返回空页，而不是报错、也不是算成大页码</h3>
 * <pre>
 *   Go: start := int((page - 1) * pageSize)      // ← 两个 int32 相乘，int32 结果
 *       if start &lt; 0 || int64(start) &gt;= total { return 0, 0 }
 * </pre>
 * {@code page=1000000, pageSize=50} 时 {@code (page-1)*pageSize} 在 int32 里溢出成负数
 * ⇒ 命中 {@code start < 0} ⇒ <b>空页</b>。
 * <p>Java 的 {@code int} 同为 32 位补码，溢出行为逐位相同 ⇒ 原样复现即可。
 * <b>但不要</b>把参数提升成 {@code long}：那样超长 page 会算出真实的巨大 offset，
 * 结果仍落在 {@code start >= total} 分支上（多数情况巧合一致），
 * 可一旦 {@code pageSize} 也大，{@code long} 与 int32 的分歧就会变成可观察差异。
 *
 * <h3>2. {@code rank} 是<b>过滤后</b>的稠密序号，且跨页连续</h3>
 * rank 在<b>分页之前</b>按「过滤后的下标 + 1」重算（Go {@code :119}），
 * 所以第 2 页第一条的 rank 是 2 而不是 1。
 * 由 Go 测试 {@code getHotArticlesLogic_test.go:131-147} 双向钉死
 * （第 1 页断言 rank==1、第 2 页断言 rank==2）。
 *
 * <h3>3. {@code total} 是过滤后的窗口大小，<b>不随分页参数变化</b></h3>
 * Go {@code :78} {@code total := int64(len(items))} 在分页之前算。
 */
public final class HotRankPaginator {

    /** Go {@code getHotArticlesLogic.go:25} {@code defaultPage = int32(1)}。 */
    public static final int DEFAULT_PAGE = 1;

    /** Go {@code :26} {@code defaultPageSize = int32(20)}。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Go {@code :27} {@code maxPageSize = int32(50)}。 */
    public static final int MAX_PAGE_SIZE = 50;

    /** Go {@code :28} {@code maxHotArticles = int32(100)}，即向 hot-rpc 请求的 TopK。 */
    public static final int MAX_HOT_ARTICLES = 100;

    /**
     * Go {@code :30} {@code rollingScope = "rolling"} —— 编译期常量，恒定值。
     */
    public static final String ROLLING_SCOPE = "rolling";

    private HotRankPaginator() {
    }

    /**
     * Go {@code normalizePagination(req)}（{@code getHotArticlesLogic.go:172-190}）的等价物。
     *
     * <p>Go 侧 {@code req} 永不为 nil（handler 声明的是值类型并取地址），
     * 但函数体里有 {@code if req != nil} 分支 ⇒ 这里<b>照样接受 null</b> 并按默认值处理，
     * 保持调用点不必判空。
     *
     * @return 长度为 2 的数组 {@code [page, pageSize]}
     */
    public static int[] normalize(HotArticlesReq req) {
        int page = DEFAULT_PAGE;
        int pageSize = DEFAULT_PAGE_SIZE;

        if (req != null) {
            if (req.getPage() > 0) {
                page = req.getPage();
            }
            if (req.getPageSize() > 0) {
                pageSize = req.getPageSize();
            }
        }

        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }

        return new int[]{page, pageSize};
    }

    /**
     * Go {@code paginate(total, page, pageSize)}（{@code getHotArticlesLogic.go:192-208}）。
     *
     * <p>⚠️ {@code start} 用 <b>int32 乘法</b>：{@code (page - 1) * pageSize}，
     * 溢出后为负 ⇒ 空页。见类注释「1」。
     *
     * @return 长度为 2 的数组 {@code [start, end]}；越界时为 {@code [0, 0]}
     */
    public static int[] paginate(long total, int page, int pageSize) {
        if (total <= 0) {
            return new int[]{0, 0};
        }

        int start = (page - 1) * pageSize;
        if (start < 0 || start >= total) {
            return new int[]{0, 0};
        }

        int end = start + pageSize;
        if (end > total) {
            end = (int) total;
        }

        return new int[]{start, end};
    }

    /**
     * Go {@code hydrateArticles} 末段的 rank 重算 + 「过滤 nil 后按下标保序」
     * （{@code getHotArticlesLogic.go:114-123}）：
     *
     * <pre>
     *   result := make([]types.HotArticleItem, 0, len(hydrated))
     *   for _, item := range hydrated {
     *       if item == nil { continue }
     *       item.Rank = int32(len(result) + 1)
     *       result = append(result, *item)
     *   }
     * </pre>
     *
     * <p>入参用 {@code null} 表示 Go 侧的 nil 元素（补全失败被 skip 的那些）。
     *
     * @return 稠密 rank 后的列表；入参全为 null 时返回<b>空列表</b>（不是 null）
     */
    public static List<HotArticleCard> assignRanks(List<HotArticleCard> hydrated) {
        List<HotArticleCard> result = new ArrayList<>(hydrated.size());
        for (HotArticleCard item : hydrated) {
            if (item == null) {
                continue;
            }
            item.setRank(result.size() + 1);
            result.add(item);
        }
        return result;
    }

    /**
     * 切片并组装响应 —— 对应 Go {@code getHotArticlesLogic.go:78-88} 的主干尾部。
     *
     * <p>⚠️ 返回的 {@code items} <b>永不为 null</b>：Go 的
     * {@code items[start:end]} 在空页时是非 nil 的空切片 ⇒ JSON 是 {@code []}。
     */
    public static HotArticlesResp slice(List<HotArticleCard> ranked, int page, int pageSize) {
        long total = ranked.size();
        int[] range = paginate(total, page, pageSize);
        List<HotArticleCard> paged;
        if (range[0] == range[1]) {
            paged = Collections.emptyList();
        } else {
            paged = new ArrayList<>(ranked.subList(range[0], range[1]));
        }
        return new HotArticlesResp(paged, total, page, pageSize, ROLLING_SCOPE);
    }
}