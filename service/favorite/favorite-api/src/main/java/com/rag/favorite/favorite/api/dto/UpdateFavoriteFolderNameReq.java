package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 对应 Go {@code api/internal/types/types.go:74-77}：
 * <pre>
 * type UpdateFavoriteFolderNameReq struct {
 *     FolderId int64  `json:"folderId,string"`
 *     Name     string `json:"name"`
 * }
 * </pre>
 *
 * <p><b>两个字段都必填</b>（都没有 {@code ,optional}）。实测：
 * <pre>
 *   {"folderId":"1","name":"x"} → OK
 *   {"folderId":1,"name":"x"}   → OK      ← ,string 收数字
 *   {"name":"x"}                → field "folderId" is not set ⇒ 5003
 *   {"folderId":"1"}            → field "name" is not set      ⇒ 5003
 *   {"folderId":"1","name":""}  → OK        ← 空串是合法值
 * </pre>
 */
public class UpdateFavoriteFolderNameReq implements GoStyleRequiredFields {

    @JsonProperty("folderId")
    private Long folderId;

    @JsonProperty("name")
    private String name;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("folderId", folderId);
        GoStyleRequired.field("name", name);
    }

    public Long getFolderId() {
        return folderId;
    }

    /**
     * <b>入参必须是 {@code Object}</b> —— 见 {@link DeleteFavoriteFolderReq#setFolderId(Object)}。
     */
    public void setFolderId(Object raw) {
        this.folderId = GoStyleStringInt64.parse(raw, "folderId");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
