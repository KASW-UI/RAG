package com.rag.like.like.api.dto;

import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 对应 Go {@code api/internal/types/types.go:49-50}：
 * <pre>
 * type GetUserTotalLikeReq struct {
 * }
 * </pre>
 *
 * <p><b>零字段 DTO ⇒ 没有任何必填校验</b>。这是本模块<b>唯一</b>一个
 * 「空 body / 任意 body 都能解析成功」的请求类型。
 *
 * <p>⚠️ <b>它仍然保留 {@code @RequestBody(required = false)}</b>：
 * Go 侧的 handler <b>照样调 {@code httpx.Parse}</b>（{@code getusertotallikehandler.go:17-20}），
 * 只是这个 struct 没有字段所以不会失败。为了让 Java 端能复现
 * 「畸形 JSON 仍会被拒」，不能让 Spring 跳过 body 读取。
 */
public class GetUserTotalLikeReq implements GoStyleRequiredFields {

    @Override
    public void assertGoRequiredFields() {
        // 零字段：无必填项
    }
}