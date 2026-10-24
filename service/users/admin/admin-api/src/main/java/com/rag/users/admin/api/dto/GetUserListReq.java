package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * admin getUserList 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetUserListReq。
 *
 * <p>字段：page（default 1）、page_size（default 20，wire: page_size）、keyword（<b>必填</b>）。
 *
 * <p><b>DRIFT-007（已修）· 为什么用包装类型 {@link Long} 而不是 {@code long}</b>：
 * Go 端的默认值来自 go-zero 的 struct tag {@code form:"page,default=1"}，
 * 而 tag 的 default <b>只在 query 参数缺失时</b>生效——显式传 {@code page=0} 是
 * <b>不会</b>被默认值覆盖的，会原样进到 rpc 层、再由
 * {@code offset=(page-1)*pageSize} 算出负数让 PG 报错。
 * <p>primitive {@code long} 的默认值是 0，Jackson 无法区分「客户端没传」与
 *「客户端传了 0」，两者都会被兜底成第 1 页。改成 {@link Long} 后 null == 未传，
 * 由 {@code AdminListUserService} 填默认值，语义才与 Go 的 tag 对齐。
 *
 * <p><b>DRIFT-016 · keyword 必填，但 page / page_size 不必填</b>：
 * Go 出处 types.go 的 tag 是 {@code form:"keyword,omitempty"}（{@code ,omitempty} ≠
 * {@code ,optional} ⇒ 必填），而 {@code page} / {@code page_size} 带
 * {@code default=} —— {@code unmarshaler.go:918} 的 {@code processNamedFieldWithoutValue}
 * 先判 {@code opts.getDefault()}，有 default 就填默认值直接返回，<b>不走必填分支</b>。
 * 实测：drop keyword ⇒ 400；缺 page / page_size ⇒ 200。
 *
 * <p><b>已知残留差异</b>：Go 的 {@code keyword} 走 <b>form 通道</b>，
 * 而 {@code rest/httpx/util.go:37-43} 的 {@code GetFormValues} 会把空值
 * （{@code len(v) == 0}）<b>整个丢弃</b>，所以 {@code keyword=} 退化成「缺席」⇒ 400。
 * RAG 的 keyword 在 JSON body 里，{@code keyword=""} 会被<b>接受</b>。
 * 两边都 400 还是接受取决于传输通道，这是 wire 契约偏离（§3.4.1）的可观察后果，
 * 刻意不为单个字段引入第二套空串规则（DRIFT-010 的教训：安全规则的第二份实现
 * 本身就是缺陷来源）。
 */
public class GetUserListReq implements GoStyleRequiredFields {

    /** 默认值 1 —— 对应 Go tag {@code form:"page,default=1"} */
    public static final long DEFAULT_PAGE = 1L;
    /** 默认值 20 —— 对应 Go tag {@code form:"page_size,default=20"} */
    public static final long DEFAULT_PAGE_SIZE = 20L;

    private Long page;
    @JsonProperty("page_size")
    private Long pageSize;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String keyword;

    @Override
    public void assertGoRequiredFields() {
        // page / page_size 带 default= ⇒ unmarshaler.go:918 填默认值即返回，不查必填
        GoStyleRequired.field("keyword", keyword);
    }

    public Long getPage() { return page; }
    public void setPage(Long page) { this.page = page; }

    @JsonProperty("page_size")
    public Long getPageSize() { return pageSize; }
    public void setPageSize(Long pageSize) { this.pageSize = pageSize; }

    public String getKeyword() { return keyword; }

    /**
     * <b>刻意收 {@code Object} 而不是 {@code String}</b>（E-3 根因 R1 的配套处理）。
     *
     * <p><b>为什么</b>：本字段在 Go 侧是 {@code form:"keyword,omitempty"}，
     * 值来自 <b>query string</b>，所以 {@code keyword=99999} 在 Go 侧就是字符串
     * {@code "99999"}，<b>完全合法</b>。而 RAG 因 §3.4.1 的 A-03 偏离把这个端点
     * 放到了 JSON body 上，客户端于是可以发 {@code {"keyword":99999}} ——
     * 若 setter 收 {@code String}，会被 R1 的「number→String 一律拒」规则挡成 400，
     * 而 Go 接受 ⇒ 等于把 R1 修成一条新漂移。
     *
     * <p><b>为什么不靠 Jackson 的类级配置豁免</b>：实测 Jackson 3.1.5 的
     * {@code withCoercionConfig(Class, ...)} <b>完全不生效</b>
     * （详见 {@code GoStyleCoercion} 类注释的「第三个坑」），
     * R1 的规则是全局的、没法只对某个类关掉，所以只能落到字段上。
     *
     * <p><b>语义边界</b>：这里做的正是 Go 在 form 通道上做的事 —— <b>把<b>标量</b>字符串化</b>。
     * query string 的值只能是标量，所以 array / object 在 Go 侧根本到不了这个字段
     * （真发过去 Go 会得到 {@code type mismatch for field "keyword"}）。
     * 因此非标量在这里<b>必须抛错</b>，不能 {@code String.valueOf} —— 那样会把
     * {@code {"keyword":{"a":1}}} 变成字符串 {@code "{a=1}"} 然后去 LIKE 匹配，
     * 是个静默的错误结果（E-3 矩阵实测抓到过这一条）。
     * {@code null} 原样保留，仍由 {@link #assertGoRequiredFields()} 判必填。
     *
     * <p>全项目<b>只有这一个字段</b>需要这样处理：另外两个 Go form 通道 DTO
     * （{@code GetUserListReq.page/page_size}、{@code GetUserReq.uid}）都是整数字段，
     * 规则 2 在 Go 侧同样拒（{@code strconv.ParseInt}），无豁免必要。
     */
    @JsonProperty("keyword")
    public void setKeyword(Object raw) {
        if (raw == null || raw instanceof String) {
            this.keyword = (String) raw;
        } else if (raw instanceof Number || raw instanceof Boolean) {
            this.keyword = String.valueOf(raw);
        } else {
            throw new IllegalArgumentException(
                    "keyword must be a scalar (Go form 通道只可能传标量)，实际收到 "
                            + raw.getClass().getName());
        }
    }

    /**
     * 取 page 并套用 Go tag 的 default：null（未传）→ 1；显式 0 / 负数<b>原样保留</b>。
     * 保留是为了让下游按 Go 的行为算出负 OFFSET 并报错，而不是静默返回首页。
     */
    public long pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    /** 同 {@link #pageOrDefault()}，default = 20。 */
    public long pageSizeOrDefault() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}