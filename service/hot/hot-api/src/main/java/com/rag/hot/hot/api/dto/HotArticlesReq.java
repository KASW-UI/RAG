package com.rag.hot.hot.api.dto;

/**
 * 分页入参 —— 对应 Go {@code api/internal/types/types.go:22-25}：
 *
 * <pre>
 *   type HotArticlesReq struct {
 *       Page     int32 `form:"page,default=1"`
 *       PageSize int32 `form:"page_size,default=20"`
 *   }
 * </pre>
 *
 * <h2>本类承载的是 <b>form tag 那一层</b>默认值，不是 logic 那一层</h2>
 * Go 侧有<b>两层</b>默认值，缺一不可，语义不同：
 * <table border="1">
 *   <caption>两层默认值的分工</caption>
 *   <tr><th>层</th><th>Go 出处</th><th>规则</th><th>落点</th></tr>
 *   <tr>
 *     <td>① form tag</td>
 *     <td>{@code types.go:23-24} 的 {@code ,default=1} / {@code ,default=20}</td>
 *     <td>仅当 key <b>缺席或值为空</b>时生效（空值在
 *         {@code httpx/util.go:44-45} 被整个丢弃）</td>
 *     <td>{@link com.rag.hot.hot.api.form.GoFormValues} → 本类字段</td>
 *   </tr>
 *   <tr>
 *     <td>② logic</td>
 *     <td>{@code getHotArticlesLogic.go:172-190} 的 {@code normalizePagination}</td>
 *     <td>{@code page<=0 → 1}、{@code pageSize<=0 → 20}、{@code pageSize>50 → 50}</td>
 *     <td>{@link com.rag.hot.hot.api.service.HotRankPaginator}</td>
 *   </tr>
 * </table>
 * ⇒ 传 {@code ?page=0} 时：① 让它保持 0（它<b>在</b>），② 再把它归一化成 1。
 * 两层都写进 DTO 会让「归一化逻辑」无法脱离 Web 层测试，也让 DTO 不再等价于 Go 的 types。
 *
 * <h2>字段类型是 {@code int}（= Go 的 int32 位宽）</h2>
 * {@code HotRankPaginator#paginate} 里的 {@code start := int((page - 1) * pageSize)}
 * 在 Go 里是 <b>int32 乘法</b>，大 page 会溢出成负数从而返回空页。
 * Java 的 {@code int} 同样是 32 位补码，溢出行为逐位相同 ⇒ 该语义可以原样复现。
 * 用 {@code long} 会把这个可观察行为悄悄改掉（超出 int32 范围时 Go 返回空页、Java 会去查第 N 页）。
 */
public class HotArticlesReq {

    /** Go {@code form:"page,default=1"}。 */
    private int page;

    /** Go {@code form:"page_size,default=20"}。注意 query 里的键名是 snake_case。 */
    private int pageSize;

    public HotArticlesReq() {
    }

    public HotArticlesReq(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}