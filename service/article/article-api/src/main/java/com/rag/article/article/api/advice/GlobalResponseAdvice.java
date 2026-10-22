package com.rag.article.article.api.advice;

import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.common.response.Response;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 成功路径的信封包装 —— 对应 Go 各 handler 里<b>手写</b>的那一行
 * {@code httpx.OkJson(w, &response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp})}。
 *
 * <h2>为什么是 advice 而不是每个 service 返回 {@code Response}</h2>
 * <pre>
 *   create_article_handler.go:27-31   httpx.OkJson(w, &response.Response{Code: code, Msg: ..., Data: resp})
 *   get_article_handler.go:27-31      同上
 *   update_article_handler.go:28-32   同上
 *   delete_article_handler.go:28-32   同上
 *   get_author_article_handler.go:28  同上（单行写法）
 *   list_articles_handler.go:28-32   同上
 * </pre>
 * ⇒ Go 侧是「<b>6 份逐字重复</b>」的同一段代码，且每个 handler <b>都</b>要手写一遍
 * （漏写一个就少包一层信封）。Java 侧把这件事收进 advice：
 * service 只管返回业务对象或抛 {@code BusinessException}，
 * 信封由本类统一产出。
 *
 * <p><b>L1 wire 契约</b>：成功路径恒为
 * {@code HTTP 200 + {"code":200,"msg":"OK","data":<controller 返回值>}}，
 * <b>只包一层</b>。失败路径由 {@link GlobalExceptionHandler} 直接产出完整 envelope，
 * <b>不经过本类</b>。
 *
 * <h2>⚠️ 必须放行的分支（少一条就会产生假绿 / 假红）</h2>
 * <ol>
 *   <li><b>{@code body == null}</b>：{@code @ExceptionHandler} 返回的无 body 响应
 *       （本模块的 401 空 body、404/405、text/plain 400）若被包成
 *       {@code {"code":200,"msg":"OK","data":null}}，就等于凭空造出一个业务码，
 *       把「鉴权失败」伪装成「成功但无数据」。AGENTS.md §3.3 的 DRIFT-FRAMEWORK-001
 *       就是同款形态：{@code HttpEntityMethodProcessor} 会查 advice chain，
 *       连 {@code @ExceptionHandler} 的 {@code ResponseEntity} 也在链上。</li>
 *   <li><b>{@code body instanceof Response}</b>：错误路径的 envelope 已经成型，
 *       再包一层会出现 {@code {"code":200,"msg":"OK","data":{"code":1002,…}}}。
 *       （本模块当前没有这么用的地方，属于防御性留痕。）</li>
 *   <li><b>{@code body instanceof ResponseEntity}</b>：<b>这一条是真实契约，不是防御</b> ——
 *       {@code POST /v1/upload} 在 Go 侧<b>不走信封</b>（{@code UploadImageHandler} 调
 *       {@code httpx.OkJsonCtx(ctx, w, resp)} 直接写裸结构体，而另外 6 个 handler
 *       显式包了信封）。见 {@code UploadImageResp} 的类注释。</li>
 * </ol>
 *
 * <h2>⚠️ 只包 JSON</h2>
 * <p>Go 的 {@code httpx.OkJson}（{@code httpx/responses.go:114-119}）无条件
 * {@code w.Header().Set(ContentType, header.ContentTypeJson)}。
 * 本类的 {@code supports} 因此返回 {@code true}（不挑媒体类型），
 * 但对 {@code byte[]} / {@code String} 这类已被容器当成非 JSON 的 body 不做包装 ——
 * 它们在本模块只出现在 error handler 路径上，已被上面第 1 条的 null 判断拦掉。
 */
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // ── 放行 1：空 body（401 / 404 / 405 / 裸 500） ──
        if (body == null) {
            return null;
        }
        // ── 放行 2：已经是 envelope ──
        if (body instanceof Response) {
            return body;
        }
        // ── 放行 3：控制器方法声明返回 ResponseEntity ──
        //    ⚠️ POST /v1/upload 的裸响应走这里。Go 侧 UploadImageHandler 调
        //    httpx.OkJsonCtx(ctx, w, resp) 直接写裸结构体，而另外 6 个 handler 显式包了信封。
        //    ⚠️ **必须看 returnType，不能看 body**：
        //    HttpEntityMethodProcessor 会先把 ResponseEntity **拆开**再交给 advice，
        //    到这里 body 已经是 UploadImageResp 本身，`body instanceof ResponseEntity` 永远为假。
        //    唯一可靠的判据是「这个 handler 方法自己声明了要返回 ResponseEntity」。
        if (ResponseEntity.class.isAssignableFrom(returnType.getParameterType())) {
            return body;
        }
        // ── 放行 4：裸 payload 形态 ──
        //    ⚠️ 这一条是**实测踩出来的**，不是预防性代码：
        //    GlobalExceptionHandler 的 400 text/plain 分支返回 ResponseEntity<byte[]>，
        //    同样会被 HttpEntityMethodProcessor 拆开，byte[] 落到这里。
        //    若不拦，它会被包成 {"code":200,"msg":"OK","data":[B@1234]}
        //    ——「解析失败」凭空变成「成功」+ Base64 乱码，且后续序列化再抛异常变成 500。
        //    （第一次实现漏了这条，5 条断言 400 text/plain 的用例全部失败。）
        if (body instanceof byte[] || body instanceof String
                || body instanceof org.springframework.core.io.Resource) {
            return body;
        }
        // ── 放行 5：非 JSON 的选定媒体类型 ──
        //    Go 侧 httpx.OkJson（httpx/responses.go:114-119）无条件写
        //    Content-Type: application/json；text/plain 的 body 一律来自
        //    Go 的 http.Error（裸状态码 + 裸文案），不属于信封语义。
        if (selectedContentType != null
                && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        return new Response(ErrorCode.SUCCESS.getCode(), ErrorCode.getMsg(ErrorCode.SUCCESS.getCode()), body);
    }
}