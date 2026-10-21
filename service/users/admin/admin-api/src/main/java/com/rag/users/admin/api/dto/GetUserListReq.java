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
    public void setKeyword(String keyword) { this.keyword = keyword; }

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