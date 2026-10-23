package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 对应 Go {@code api/internal/types/types.go:6-8}：
 * <pre>
 * type CreateFavoriteFolderReq struct {
 *     Name string `json:"name"`
 * }
 * </pre>
 *
 * <p><b>必填判定</b>：Go 的 tag <b>没有</b> {@code ,optional} ⇒ 必填
 * （go-zero 的 {@code core/mapping} 只认 {@code ,optional}，{@code ,omitempty} 不算）。
 * <p>实测（go-zero v1.10.2 真源探针，2026-10-23）：
 * <pre>
 *   {"name":"x"}   → OK
 *   {"name":""}    → OK          ← 空串是合法值（json 通道不判空串）
 *   {}             → field "name" is not set      ⇒ 5003
 *   {"name":null}  → field "name" mustn't be nil ⇒ 5003
 *   {"name":123}   → type mismatch for field "name", expect "string", actual "number" ⇒ 5003
 * </pre>
 *
 * <p><b>⚠️ 缺席 vs 显式 null</b>：Go 对这两种情况给出<b>不同</b>的文案
 * （{@code is not set} / {@code mustn't be nil}），但在本域<b>落到同一个出口</b>
 * —— handler 把任何 {@code httpx.Parse} 错误都写成 {@code {code:5003, msg:"参数错误"}}。
 * ⇒ 线上可观察的响应<b>完全一致</b>，故此处按 like-api 的既有做法用
 * {@link GoStyleRequired#field} 统一判 null，不再单独维护 presence 标志。
 */
public class CreateFavoriteFolderReq implements GoStyleRequiredFields {

    @JsonProperty("name")
    private String name;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("name", name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
