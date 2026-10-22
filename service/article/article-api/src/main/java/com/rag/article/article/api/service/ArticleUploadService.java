package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.UploadImageResp;
import com.rag.article.article.api.http.GoStyleBadRequestException;
import com.rag.article.article.proto.UploadFileRequest;
import com.rag.article.article.proto.UploadFileResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片上传 —— 对应 Go {@code logic/article/upload_image_logic.go}。
 *
 * <h2>🔴 本端点是本模块唯一<b>不走信封</b>的路径（成功与失败都是）</h2>
 *
 * <pre>
 *   Go: upload_image_handler.go:14-30
 *       file, header, err := r.FormFile("image")
 *       if err != nil { httpx.ErrorCtx(r.Context(), w, err); return }        // ← 400 text/plain
 *       defer file.Close()
 *       l := article.NewUploadImageLogic(r.Context(), svcCtx)
 *       resp, err := l.UploadImage(file, header)
 *       if err != nil { httpx.ErrorCtx(r.Context(), w, err) }                // ← 400 text/plain
 *       else          { httpx.OkJsonCtx(r.Context(), w, resp) }              // ← **裸** {"image_url":…}
 * </pre>
 *
 * <p>对照另外 6 个 handler：
 * <pre>
 *   create/delete/get/get_author/list/update_handler.go
 *       httpx.OkJson(w, &amp;response.Response{Code: code, Msg: …, Data: resp})  ← 显式手写信封
 * </pre>
 *
 * ⇒ 两处不同<b>不是笔误</b>，而是 article-api 没有注册
 * {@code httpx.SetOkHandler} / {@code httpx.SetErrorHandler}（{@code article.go} 全文 42 行）
 * 导致的结果：{@code OkJson} 不会自动包，成功路径就只能是裸结构体。
 *
 * <h2>⚠️ 为什么「上传时下游失败」也是 400 而不是 1015</h2>
 * <pre>
 *   Go: upload_image_logic.go:45-48
 *       rpcResp, err := l.svcCtx.ArticleRpc.UploadFile(...)
 *       if err != nil {
 *           logger.LogBusinessErr(l.ctx, errmsg.ErrorMinioUpload, fmt.Errorf("rpc call UploadFile failed: %w", err))
 *           return nil, err          // ← 返回**裸 err**，不是 (nil, errmsg.Code)
 *       }
 * </pre>
 * ⇒ 该 error 被 handler 交给 {@code httpx.ErrorCtx} ⇒ <b>400 text/plain</b>。
 * ⚠️ 这里的 {@code errmsg.ErrorMinioUpload}（5004）<b>只进日志，不进响应</b>。
 * 照「其他 logic 的模式」给它套一个 1015/5004 信封就是<b>新增行为</b>。
 *
 * <h2>⚠️ {@code io.ReadAll} 失败也是同一条 400</h2>
 * {@code upload_image_logic.go:35-39} 读文件失败同样 {@code return nil, err}。
 * Java 侧对应 {@link IOException}。
 */
@Service
public class ArticleUploadService {

    /** Go {@code r.FormFile("image")} 的 multipart 字段名 —— <b>L1 契约</b>，不可改。 */
    public static final String FORM_FIELD = "image";

    private final ArticleRpcClient articleRpcClient;

    public ArticleUploadService(ArticleRpcClient articleRpcClient) {
        this.articleRpcClient = articleRpcClient;
    }

    /**
     * @param file 已在 controller 层取到的 multipart 文件（取不到时 controller 直接抛
     *              {@code MissingServletRequestPartException} → 400 text/plain）
     * @return <b>裸</b>响应体（不进信封），由 {@code GlobalResponseAdvice} 对
     *         {@code ResponseEntity} 放行来保住这一点
     */
    public UploadImageResp uploadImage(MultipartFile file) {
        String filename = file.getOriginalFilename();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            // Go: io.ReadAll 失败 → return nil, err → httpx.ErrorCtx → 400
            throw new GoStyleBadRequestException(
                    "read uploaded file failed: " + e.getMessage(), e);
        }

        UploadFileResponse rpcResp;
        try {
            rpcResp = articleRpcClient.articleRpc().uploadFile(UploadFileRequest.newBuilder()
                    .setContent(com.google.protobuf.ByteString.copyFrom(content))
                    // ⚠️ Go 侧取的是 multipart 的原始 filename（header.Filename），不做清洗
                    .setFileName(filename == null ? "" : filename)
                    .build());
        } catch (StatusRuntimeException e) {
            // Go: rpc call UploadFile failed: %w → 裸 err → 400 text/plain
            // ⚠️ 业务码 errmsg.ErrorMinioUpload(5004) 在 Go 侧**只进日志**
            throw new GoStyleBadRequestException(
                    "rpc call UploadFile failed: " + e.getStatus(), e);
        }

        return new UploadImageResp(rpcResp.getFileUrl());
    }
}