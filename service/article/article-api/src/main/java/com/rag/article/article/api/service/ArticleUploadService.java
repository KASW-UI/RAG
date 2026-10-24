package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.UploadImageResp;
import com.rag.article.article.api.http.GoStyleBadRequestException;
import com.rag.article.article.api.http.GoStyleGrpcStatusMapper;
import com.rag.article.article.api.http.GoStyleHttpErrorException;
import com.rag.article.article.proto.UploadFileRequest;
import com.rag.article.article.proto.UploadFileResponse;
import com.rag.common.protection.GoStyleCircuitOpenException;
import io.grpc.Status;
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
 * <h2>⚠️ 为什么「上传时下游失败」<b>不是</b> 1015 信封</h2>
 * <pre>
 *   Go: upload_image_logic.go:45-48
 *       rpcResp, err := l.svcCtx.ArticleRpc.UploadFile(...)
 *       if err != nil {
 *           logger.LogBusinessErr(l.ctx, errmsg.ErrorMinioUpload, fmt.Errorf("rpc call UploadFile failed: %w", err))
 *           return nil, err          // ← 返回**裸 err**，不是 (nil, errmsg.Code)
 *       }
 * </pre>
 * ⇒ 该 error 被 handler 交给 {@code httpx.ErrorCtx} ⇒ <b>裸 HTTP 状态码 + text/plain</b>，
 * <b>不是</b> 200 + 信封。{@code errmsg.ErrorMinioUpload}（5004）<b>只进日志，不进响应</b>。
 * 照「其他 logic 的模式」给它套一个 1015/5004 信封就是<b>新增行为</b>。
 *
 * <h2>🔴 裸状态码<b>不是</b>恒 400（DRIFT-ARTICLE-API-001 已修）</h2>
 * <pre>
 *   // go-zero v1.10.2 · rest/httpx/responses.go:142-159
 *   if handler == nil {                       // ← article-api **没有**注册 error handler
 *       if len(fns) > 0 { … }
 *       else if errcode.IsGrpcError(err) {    // ← ① 先问「是不是 gRPC 错误」
 *           http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))   // ← ② 按 gRPC 码映射
 *       } else {
 *           http.Error(w, err.Error(), http.StatusBadRequest)            // ← ③ 非 gRPC ⇒ 恒 400
 *       }
 *   }
 * </pre>
 * ⇒ <b>gRPC 状态错误</b>走 ② 分支，状态码由 gRPC 码决定：
 * {@code Unavailable⇒503}、{@code Internal⇒500}、{@code DeadlineExceeded⇒504}、
 * {@code ResourceExhausted⇒429}、{@code NotFound⇒404}、未知码⇒500。
 * <b>非</b> gRPC 错误（典型：熔断打开时的 {@code breaker.ErrServiceUnavailable}）走 ③ 分支 ⇒ 恒 400。
 *
 * <h2>⚠️ body 文案也要逐字对齐</h2>
 * Go 写的是 {@code err.Error()}，而 grpc-go 的 {@code *status.Error.Error()}
 * 转发到 {@code Status.String()} = {@code fmt.Sprintf("rpc error: code = %s desc = %s", …)}
 * ⇒ body 形如 {@code rpc error: code = Unavailable desc = minio down}。
 * ⚠️ Java 的 {@code StatusRuntimeException.getMessage()} 是 {@code UNAVAILABLE: minio down}，
 * <b>字面不同</b> ⇒ 必须走 {@link GoStyleGrpcStatusMapper#goErrorMessage} 复刻。
 *
 * <h2>⚠️ {@code io.ReadAll} 失败走的是<b>另一</b>分支</h2>
 * {@code upload_image_logic.go:35-39} 读文件失败同样 {@code return nil, err}，
 * 但那是<b>非</b> gRPC 错误 ⇒ 恒 400。Java 侧对应 {@link IOException}。
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
            // Go 分支 ②（responses.go:150-153）：err 是 gRPC 状态错误 ⇒ IsGrpcError 为真
            //   ⇒ http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))
            // ⇒ 状态码按 gRPC 码映射（Unavailable⇒503 / Internal⇒500 / DeadlineExceeded⇒504 …），
            //   body 复刻 grpc-go 的 "rpc error: code = X desc = Y"。
            // ⚠️ 业务码 errmsg.ErrorMinioUpload(5004) 在 Go 侧**只进日志**
            Status.Code code = e.getStatus().getCode();
            throw new GoStyleHttpErrorException(
                    GoStyleGrpcStatusMapper.httpStatusOf(code),
                    GoStyleGrpcStatusMapper.goErrorMessage(code, e.getStatus().getDescription()));
        } catch (GoStyleCircuitOpenException e) {
            // Go 分支 ③（responses.go:156-157）：**非** gRPC 错误 ⇒ 恒 400。
            // 典型来源：Go 侧 breaker 打开时返回的 breaker.ErrServiceUnavailable
            // = errors.New("circuit breaker is open")（core/breaker/breaker.go:19）——
            // 它是普通 error，不实现 GRPCStatus() ⇒ IsGrpcError 为假。
            // ⚠️ RAG 侧的 GoStyleCircuitOpenException 同样是**普通** RuntimeException
            //   （刻意不抛 StatusRuntimeException，理由见该类注释），
            //   若不在这里接住，会一路落到 handleAny ⇒ 裸 500，与 Go 的 400 不一致。
            throw new GoStyleBadRequestException(e.getMessage(), e);
        }

        return new UploadImageResp(rpcResp.getFileUrl());
    }
}