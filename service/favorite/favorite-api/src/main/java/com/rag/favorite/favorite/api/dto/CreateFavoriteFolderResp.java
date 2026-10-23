package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:10-12}：
 * <pre>
 * type CreateFavoriteFolderResp struct {
 *     FolderId int64 `json:"folderId,string"`
 * }
 * </pre>
 *
 * <p><b>⚠️ 出参的 {@code ,string} 是「严格」的</b>：Go 的 {@code encoding/json} 对
 * {@code ,string} 标签的 int64，<b>序列化时一定写成 JSON 字符串</b>。
 * ⇒ 客户端拿到的必须是 {@code {"folderId":"123"}}，<b>不是</b> {@code {"folderId":123}}。
 * <p>与入参方向相反这件事常被搞混：<b>入参宽松（字符串与数字都收）、出参严格（只写字符串）</b>。
 * <p>实现方式与 admin-api 的 {@code UserInfo#getUid()} 同款：字段存 {@code Long}，
 * getter 返回 {@code String}，由 Jackson 决定写出的是字符串。
 */
public class CreateFavoriteFolderResp {

    @JsonProperty("folderId")
    private Long folderId;

    public CreateFavoriteFolderResp() {
    }

    public CreateFavoriteFolderResp(Long folderId) {
        this.folderId = folderId;
    }

    /** 出参为字符串（对齐 Go 的 {@code json:"folderId,string"}）。 */
    public String getFolderId() {
        return folderId == null ? null : folderId.toString();
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
}
