package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 图片上传响应 —— 对应 Go {@code types.go:83-85} 的 {@code UploadImageResp}。
 */
public class UploadImageResp {

    /**
     * ⚠️ <b>本响应不走信封</b>：Go {@code UploadImageHandler} 调的是
     * {@code httpx.OkJsonCtx(r.Context(), w, resp)}，直接写裸结构体，
     * 而<b>另外 6 个 handler</b> 调的是
     * {@code httpx.OkJson(w, &response.Response{...})}（显式包了信封）。
     *
     * <p>根因：article-api <b>没有</b> {@code httpx.SetOkHandler}，
     * 所以 {@code OkJson} 不会自动包；6 个 handler 是<b>手写</b>包的信封。
     * ⇒ {@code POST /v1/upload} 成功时输出的是 <b>{@code {"image_url":"…"}}</b>，
     * <b>不是</b> {@code {"code":200,"msg":"OK","data":{…}}}。
     *
     * <p>这属于 L1 跨服务契约（客户端按 {@code data.image_url} 与
     * {@code image_url} 两种形状各写了一处都会错）。
     * 实现侧靠 {@code GlobalResponseAdvice} 对 {@code ResponseEntity} 放行来保住它。
     */
    @JsonProperty("image_url")
    private String imageUrl;

    public UploadImageResp() {
    }

    public UploadImageResp(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
