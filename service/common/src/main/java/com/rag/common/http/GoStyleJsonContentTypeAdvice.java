package com.rag.common.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 🔴🔴 **T-6**：让 <b>JSON 响应</b>的 {@code Content-Type} 与 Go 一致地带上 {@code charset=utf-8}。
 *
 * <h2>Go 出处（真源，逐行）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/responses.go:175-193（doWriteJson）
 *     w.Header().Set(ContentType, header.ContentTypeJson)   // ← 无条件覆盖
 *
 * go-zero@v1.10.2/rest/internal/header/headers.go:16-17
 *     ContentType     = "Content-Type"
 *     ContentTypeJson = "application/json; charset=utf-8"     // ← 分号后**有空格**
 * </pre>
 *
 * <h2>🔴 RAG 侧修前的形态（真 Tomcat 实测）</h2>
 * <pre>
 * 修前：Content-Type: application/json          ← **连 charset 都没有**
 * 修后：Content-Type: application/json;charset=utf-8
 * Go  ：Content-Type: application/json; charset=utf-8
 * </pre>
 *
 * <h2>⚠️ 为什么本类挂在 <b>ResponseBodyAdvice</b> 而不是转换器上</h2>
 * <ul>
 *   <li>共享层里那个 JSON 转换器（{@link GoStyleJsonBodyConverter}）<b>不是所有 api 都启用</b>：
 *       {@code task-api} 明确把它排除在组件扫描之外
 *       （{@code TaskApiApplication} 的 {@code @ComponentScan} exclude）⇒
 *       挂在它上面 ⇒ <b>task-api 不生效</b> ⇒ 不满足「改 1 处 ⇒ 10 个 api 全部生效」。</li>
 *   <li>Advice 是<b>按「响应体是不是 JSON」判定</b>的 ⇒ <b>与用哪个转换器无关</b>
 *       ⇒ 无论最终是共享的转换器、Boot 的转换器、还是 task-api 自己那个 ⇒ 都生效。</li>
 * </ul>
 *
 * <h2>🔴 为什么必须用「原始字符串」而不是 {@link MediaType}</h2>
 * <p>{@code MediaType.toString()} 产出的是 <b>{@code application/json;charset=utf-8}（无空格）</b>
 * ⇒ 与 Go 的 <b>有空格</b>形态<b>字节级不一致</b>。
 * ⇒ 本类写的是<b>原始字符串</b>（Go 的字面量）⇒ 意图在代码里<b>可见</b>，
 * 且万一哪天容器的归一化行为变了，就会<b>自动</b>回到与 Go 逐字节一致。
 *
 * <h2>⚠️⚠️ 但「分号后那个空格」在 Tomcat 11 上<b>拿不到</b>（实测坐实）</h2>
 * <pre>
 * org/apache/catalina/connector/Response.java（11.0.24）setContentType(String)
 *     String[] parsedType = MediaTypeCache.parse(type);
 *     if (parsedType == null) { coyoteResponse.setContentTypeNoCharset(type); return; }
 *     if (parsedType[1] == null) {          // 没有 charset
 *         coyoteResponse.setContentTypeNoCharset(parsedType[0]);
 *     } else {                               // 有 charset ⇒ **拆开存**
 *         coyoteResponse.setContentTypeNoCharset(parsedType[0]);
 *         coyoteResponse.setCharsetHolder(CharsetHolder.getInstance(parsedType[1]));
 *     }
 *
 * org/apache/coyote/Http11OutputBuffer.writeHeaders()
 *     headerBuffer.put(contentType);
 *     headerBuffer.put(Constants.SEMICOLON);    // ← ";"  **后面没有空格**
 *     headerBuffer.put(Constants.CHARSET);      // ← "charset="
 * </pre>
 * ⇒ 容器把 Content-Type <b>拆成 (type/subtype, charset) 两份存</b>，写出去时<b>重新拼</b>
 * ⇒ 线上恒为 <b>{@code type/subtype;charset=xxx}</b>（<b>无空格</b>）
 * ⇒ ⇒ <b>应用层无法产出 Go 那个带空格的形态</b>（{@code setContentType} /
 *      {@code setHeader} / {@code addHeader} 三条路都汇到同一处归一化）。
 * <p>⇒ 这条「差一个空格」的残余**属框架限制**（与 T-7「状态行缺 reason phrase」同族），
 * **已登记**在 global 台账，不在本类的职责内。
 * <p>⇒ ⚠️ **顺带订正一条旧结论**：{@code GoStyleRouterErrorResponses} 的类注释写着
 * 「直接写原始字符串是唯一能保证逐字节对上的做法」⇒ <b>该结论已被实测证伪</b>
 * （容器照样归一化）⇒ 那条注释已改写。
 *
 * <h2>判定规则（为什么不用「看 Content-Type」而是「看转换器」）</h2>
 * <p>两条取<b>或</b>：
 * <ol>
 *   <li><b>选中的是 Jackson 转换器</b> ⇒ 响应体就是 JSON ⇒ 应当带 charset；</li>
 *   <li><b>选中的 Content-Type 是 JSON</b> ⇒ 即使转换器不是 Jackson，body 仍是 JSON。</li>
 * </ol>
 * ⇒ 404 / 405 那条 {@code ResponseEntity<byte[]>}（{@code ByteArrayHttpMessageConverter}
 * ＋ 显式 {@code text/plain}）<b>两条都不满足</b> ⇒ <b>不会被误伤</b>。
 *
 * @see GoStyleJsonContentTypeCharsetTomcatTest 真 Tomcat 守门（裸 socket 逐字节比对）
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RestControllerAdvice
public class GoStyleJsonContentTypeAdvice implements ResponseBodyAdvice<Object> {

    /**
     * Go 的字面量 —— {@code rest/internal/header/headers.go:17}。
     *
     * <p>⚠️ <b>刻意写原始字符串</b>：{@code MediaType.toString()} 会把分号后的空格吃掉。
     */
    public static final String GO_JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    @Override
    public boolean supports(MethodParameter returnType,
                             Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null) {
            return body;
        }
        if (!writesJson(selectedConverterType) && !isJsonContentType(selectedContentType)) {
            return body;
        }
        // ⚠️ 用 set(String, String) 而不是 setContentType(MediaType)：
        //    后者会把值**解析成 MediaType 再序列化** ⇒ 分号后的空格被吃掉。
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, GO_JSON_CONTENT_TYPE);
        return body;
    }

    /** 选中的转换器是不是 Jackson 系 ⇒ 是则响应体就是 JSON。 */
    private static boolean writesJson(Class<? extends HttpMessageConverter<?>> converterType) {
        return converterType != null
                && JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    /** 选中的 Content-Type 是不是 JSON。 */
    private static boolean isJsonContentType(MediaType contentType) {
        return contentType != null
                && MediaType.APPLICATION_JSON.isCompatibleWith(contentType);
    }
}
