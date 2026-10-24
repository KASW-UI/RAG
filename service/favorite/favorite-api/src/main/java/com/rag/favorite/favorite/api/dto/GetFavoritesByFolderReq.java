package com.rag.favorite.favorite.api.dto;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 对应 Go {@code api/internal/types/types.go:66-68}：
 * <pre>
 * type GetFavoritesByFolderReq struct {
 *     FolderId int64 `form:"folderId,string"`
 * }
 * </pre>
 *
 * <p><b>⚠️ 本 DTO 走的是 <b>form 通道</b>（query string），不是 JSON body</b> ——
 * 这是 favorite 域<b>唯一</b>一个 form 通道字段（其余 6 个端点全是 json tag）。
 *
 * <h3>为什么「tag 决定走哪个通道」</h3>
 * <p>Go 出处：{@code rest/httpx/requests.go:50-77} 的 {@code Parse} 依次跑
 * {@code ParsePath} → {@code ParseForm} → {@code ParseHeaders} → {@code ParseJsonBody}，
 * 每个通道用<b>各自的 tag 名</b>取 key（form 通道用 {@code "form"}，json 通道用 {@code "json"}）。
 * <p>而 {@code core/mapping/unmarshaler.go:569-573} 的 {@code processField} 第一句就是：
 * <pre>
 *   if usingDifferentKeys(u.key, field) { return nil }    // 该字段在本通道整体跳过
 * </pre>
 * {@code core/mapping/utils.go:589-597} 的 {@code usingDifferentKeys}：
 * <pre>
 *   if len(field.Tag) &gt; 0 { if _, ok := field.Tag.Lookup(key); !ok { return true } }
 * </pre>
 * ⇒ <b>字段只要带了「别的通道」的 tag，就在本通道被整体跳过</b>（连必填判定都不做）。
 * <p>所以：
 * <ul>
 *   <li>{@code folderId} 带 {@code form} tag ⇒ <b>只在 form 通道生效</b>
 *       ⇒ 值来自 query string；</li>
 *   <li>其余 6 个端点的字段带 {@code json} tag ⇒ <b>只在 json 通道生效</b>
 *       ⇒ 它们在 form 通道被跳过，所以「POST 请求带 query 参数」不会影响它们。</li>
 * </ul>
 *
 * <h3>form 通道的实测语义（go-zero v1.10.2 真源探针，2026-10-23）</h3>
 * <pre>
 *   ?folderId=123                 → OK（123）
 *   ?folderId=0                   → OK（0）
 *   ?folderId=abc                 → strconv.ParseInt: parsing "abc": invalid syntax ⇒ 5003
 *   ?folderId=1.5                 → strconv.ParseInt: parsing "1.5": invalid syntax ⇒ 5003
 *   ?folderId=1e2                 → strconv.ParseInt: parsing "1e2": invalid syntax ⇒ 5003
 *   ?folderId=                    → field "folderId" is not set ⇒ 5003
 *   ?folderid=123（大小写不同）    → field "folderId" is not set ⇒ 5003（key 大小写敏感）
 *   完全没有 query                → field "folderId" is not set ⇒ 5003
 * </pre>
 * <p>⚠️ <b>空值被整个丢掉</b>：Go 出处 {@code rest/httpx/util.go:37-43} 的
 * {@code GetFormValues} 里 {@code if len(v) == 0 { continue }} ⇒
 * {@code ?folderId=} 退化成「字段缺席」，而不是「值是空串」。
 */
public class GetFavoritesByFolderReq implements GoStyleRequiredFields {

    private Long folderId;

    /**
     * 从 query string 构造 —— 对应 Go 的 form 通道。
     *
     * <p><b>⚠️ 重复参数的「先滤空值、再取首值」两步不在这里</b>：那属于 go-zero 的
     * {@code GetFormValues} + {@code WithFromArray} 两段机制，已由
     * {@link com.rag.favorite.favorite.api.service.FavoriteApiSupport#goFormFirstValue}
     * 完整复刻。传入这里的正常只会是「首值」或 {@code null}（缺席）。
     * 下面的空串判断是<b>防御性冗余</b>，与上游口径一致。
     *
     * @param rawFolderId 清洗后的 {@code folderId} 首值（可能为 null）
     * @throws IllegalArgumentException 值不是合法的 int64（对应 Go 的 {@code strconv.ParseInt} 失败）
     */
    public static GetFavoritesByFolderReq fromForm(String rawFolderId) {
        GetFavoritesByFolderReq req = new GetFavoritesByFolderReq();
        // Go 的 GetFormValues 把空值整个丢掉 ⇒ 这里也把空串当「缺席」，
        // 而不是交给 ParseInt（那会给出 "invalid syntax"，与 Go 的错误文案不同）。
        if (rawFolderId != null && !rawFolderId.isEmpty()) {
            req.folderId = GoStyleStringInt64.parse(rawFolderId, "folderId");
        }
        return req;
    }

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("folderId", folderId);
    }

    public Long getFolderId() {
        return folderId;
    }
}
