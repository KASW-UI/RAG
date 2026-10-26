package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 对应 Go {@code api/internal/types/types.go:26-29}：
 * <pre>
 * type DeleteFavoriteFolderReq struct {
 *     FolderId int64 `json:"folderId,string"`
 * }
 * </pre>
 *
 * <p><b>{@code ,string} 的入参方向是宽松的</b>：JSON <b>字符串与数字都收</b>。
 * 实测（go-zero v1.10.2 真源探针）：
 * <pre>
 *   {"folderId":"9"}  → OK
 *   {"folderId":9}    → OK      ← 数字也收！
 *   {"folderId":"abc"}→ strconv.ParseInt: parsing "abc": invalid syntax ⇒ 5003
 *   {"folderId":1.5}  → strconv.ParseInt: parsing "1.5": invalid syntax ⇒ 5003
 *   {"folderId":null} → field "folderId" mustn't be nil ⇒ 5003
 *   {}                → field "folderId" is not set ⇒ 5003
 * </pre>
 * 解析由 {@link GoStyleStringInt64} 承担（setter 入参必须是 {@code Object}，
 * 让 Jackson 不做强转，见该类注释）。
 */
public class DeleteFavoriteFolderReq implements GoStyleRequiredFields {

    @JsonProperty("folderId")
    private Long folderId;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("folderId", folderId);
    }

    public Long getFolderId() {
        return folderId;
    }

    /**
     * <b>入参必须是 {@code Object}</b> —— Go 的 {@code ,string} 是「字符串与数字都收」
     * （{@code service/favorite/api/internal/types/types.go:27}），
     * 只有不声明目标类型、让 Jackson 原样递出，才能同时表达「两种都收」。
     */
    public void setFolderId(Object raw) {
        this.folderId = GoStyleStringInt64.parse(raw, "folderId");
    }
}
