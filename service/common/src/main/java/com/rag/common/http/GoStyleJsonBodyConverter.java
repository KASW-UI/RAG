package com.rag.common.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 复刻 go-zero {@code rest/httpx/requests.go:117-125,153-155} 的
 * <b>「什么时候才读请求体」</b>判据 —— 这是 415 漂移的真正根因。
 *
 * <h2>Go 侧的判据（逐字）</h2>
 *
 * <pre>
 *   func ParseJsonBody(r *http.Request, v any) error {
 *       if withJsonBody(r) {
 *           reader := io.LimitReader(r.Body, maxBodyLen)
 *           return mapping.UnmarshalJsonReader(reader, v)   // 真读 body
 *       }
 *       return mapping.UnmarshalJsonMap(nil, v)             // ⭐ 完全不看 body
 *   }
 *
 *   func withJsonBody(r *http.Request) bool {
 *       return r.ContentLength > 0 &amp;&amp; strings.Contains(r.Header.Get("Content-Type"), "application/json")
 *   }
 * </pre>
 *
 * <h2>⚠️ 最容易被误解的一点：Go 不是「宽松地接受非 JSON」，而是<b>无视整个请求体</b></h2>
 *
 * <p>判据不满足时 Go 走的是「用空 map 解析」，<b>不是</b>「勉强把 body 当 JSON 读」。
 * 可观察后果：
 * <pre>
 *   POST /like/v1/getlikecount
 *     Content-Type: text/plain
 *     body: {"target_type":"article","target_ids":["1"]}   ← 完全合法
 *   ⇒ Go: body 被丢弃 ⇒ target_ids 缺失 ⇒ 解析失败 ⇒ 200 + {"code":5001}
 *   ⇒ 「放宽 content type 就能兼容」是错的：那会把 Go 明确拒绝的请求变成成功。
 * </pre>
 *
 * <h2>为什么不能简单地「让 Spring 也吃非 JSON」</h2>
 *
 * <p>那样得到的是「解析 body」，与 Go 的「丢弃 body」在<b>必填字段齐全</b>时结果相反。
 * ⇒ 本类在<b>判据不满足</b>时直接返回 {@code null}，
 * 让上层 {@code GoStyleRequest.normalize(null, factory)} 产出零值 DTO 再跑必填校验 ——
 * 那正是 {@code UnmarshalJsonMap(nil, v)} 的等价物（实测：两侧的最终 code/msg 一致）。
 *
 * <h2>⚠️ 判据里的三个细节，每一个都会造成可观察差异</h2>
 *
 * <ol>
 *   <li><b>{@code strings.Contains(ct, "application/json")} 是字面子串匹配</b>，
 *       <b>不是</b> Spring 的媒体类型兼容判定。⇒ {@code application/vnd.api+json}
 *       在 Go 侧<b>不</b>包含该子串 ⇒ <b>body 被丢弃</b>；
 *       而 Spring 默认把它当 JSON 正常解析 ⇒ <b>这是一条独立于 415 的漂移</b>。</li>
 *   <li><b>{@code ContentLength > 0}</b>：chunked 请求在 Go 侧 {@code ContentLength == -1}
 *       ⇒ 判据为假 ⇒ <b>body 被丢弃</b>。与 MaxBytes 那条「chunked 绕过限制」同源，
 *       是同一个「Go 用 -1 表示未知」的连带后果。</li>
 *   <li><b>Content-Type 缺失</b>：同样判为假。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么必须复用 Boot 的 {@link JsonMapper} Bean</h2>
 *
 * <p>本项目各 api 模块通过 {@code JsonMapperBuilderCustomizer}（{@code LikeGoStyleCoercion} /
 * {@code MessageGoStyleCoercion}）建立了一批<b>已用探针双侧对拍过</b>的语义：
 * {@code options=1|2|3|4}、{@code ,string} 的双向接受等（DRIFT-LIKEAPI-001/002、DRIFT-MSGAPI-001/002）。
 * 若这里用 {@code JacksonJsonHttpMessageConverter} 的<b>无参构造器</b>，
 * 就会造出第二个<b>不带这些配置</b>的 mapper ⇒ 把已验证过的语义静默退回默认值。
 * ⇒ 只接受注入进来的 {@code JsonMapper}（由 Boot 用同一个 builder 构建），
 * <b>不提供</b>无参构造器：让「忘注入」变成编译错误，而不是一条隐蔽的行为回归。
 */
public class GoStyleJsonBodyConverter extends JacksonJsonHttpMessageConverter {

    /** Go 的判据字面量，刻意不做媒体类型兼容判定。 */
    private static final String GO_JSON_CONTENT_TYPE_MARK = "application/json";

    /**
     * Go {@code rest/httpx/requests.go:21} 的 {@code maxBodyLen = 8 << 20}（8 MiB）。
     *
     * <h3>⚠️ 为什么它<b>不可配置</b></h3>
     * <p>Go 侧它是<b>框架常量</b>（{@code const} 块，{@code requests.go:17-22}），
     * 既不是 {@code RestConf} 的字段、也不在任何 yaml / struct tag 里
     * ⇒ 做成 RAG 侧的 property 等于<b>凭空多出 Go 没有的开关</b>
     * ⇒ 同 {@code maxMemory} 的处理方式：照抄，不给开关。
     *
     * <h3>⚠️ 它为什么必须存在（2026-10-25 第十六轮 · 双侧探针实测）</h3>
     * <pre>
     * rest/httpx/requests.go:117-124
     *     func ParseJsonBody(r *http.Request, v any) error {
     *         if withJsonBody(r) {
     *             reader := io.LimitReader(r.Body, maxBodyLen)   // ← 静默截断在 8 MiB
     *             return mapping.UnmarshalJsonReader(reader, v)
     *         }
     *         return mapping.UnmarshalJsonMap(nil, v)
     *     }
     * </pre>
     * ⇒ Go 对 JSON body 有两道闸：<b>① 只读前 8 MiB</b> · <b>② 命中判据才读</b>。
     * ⇒ RAG 侧原先只复刻了 ② ⇒ <b>① 缺失</b> ⇒ 一个 &gt; 8 MiB 的 JSON body
     * 在 Go 侧被<b>截断</b>（⇒ 解析器拿到半个文档 ⇒ 报错），
     * 在 RAG 侧却被<b>整个读进堆里</b>并成功解析 ⇒ 两侧行为相反。
     * ⇒ 实测证据：{@code tools/json-body-probe/} 的用例 J20（9 MiB 单个 JSON 对象）
     * ⇒ Go = REJECTED（错误详情长度 8388643 = 8 MiB + 3 ⇒ 证实截断点精确在 8 MiB）。
     *
     * <h3>⚠️ 端到端可达性（别把它写成「端到端漂移」）</h3>
     * <p>Go 的 {@code MaxBytes} <b>默认 1 MiB 且挂在引擎上全局生效</b>
     * （{@code rest/config.go:53} + {@code rest/engine.go:163-164}）
     * ⇒ 超过 1 MiB 的 body <b>先被 MaxBytes 拦成裸 413</b> ⇒ 根本走不到 {@code ParseJsonBody}
     * ⇒ ⇒ <b>本条是「解析层」对齐，不是「端到端」对齐</b>；
     * 端到端唯一可达的 8–10 MiB 段（article 的 MaxBytes = 10 MiB）已被它自己的
     * {@code GoStyleJsonBodyLimitFilter} 覆盖 ⇒ 详见
     * {@code docs/DRIFT/global/cross-domain.md} §二·十三 ③。
     */
    public static final int GO_MAX_BODY_LEN = 8 * 1024 * 1024;

    /**
     * Go {@code io.LimitReader(r.Body, maxBodyLen)} 的等价物 —— <b>最多</b>读
     * {@link #GO_MAX_BODY_LEN} 字节。
     *
     * <h3>⚠️ 为什么不能简单用 {@code in.readNBytes(GO_MAX_BODY_LEN)} 代替</h3>
     * <p>两者<b>判定结果</b>相同，但<b>读取时机</b>不同，差别是实质性的：
     * <ul>
     *   <li>{@code readNBytes(n)} 会<b>一直读到 n 字节或 EOF</b> ⇒ 客户端「发了一个小 JSON
     *       然后停住」时，RAG 会<b>一直等</b>，Go 却早就返回了
     *       （Go 的 {@code json.Decoder.Decode} 只读它需要的那个值）。</li>
     *   <li>本包装是<b>流式</b>的：Jackson 边解析边从底层流取字节，
     *       解析完一个完整 JSON 值就停 ⇒ 与 Go 的行为同形。</li>
     * </ul>
     *
     * @param in 底层请求流（不可为 null）
     * @return 一个最多只从 {@code in} 取 {@link #GO_MAX_BODY_LEN} 字节的流
     */
    public static java.io.InputStream goLimitedBody(java.io.InputStream in) {
        return new GoLimitedInputStream(in, GO_MAX_BODY_LEN);
    }

    /**
     * {@link #goLimitedBody(java.io.InputStream)} 的实现 —— Go {@code io.LimitReader} 的 Java 等价物。
     *
     * <p>⚠️ <b>刻意不</b>实现 {@code mark/reset}：Go 的 {@code io.LimitReader} 同样不支持
     * {@code io.Seeker}；而 {@code java.io.InputStream} 的默认实现会抛
     * {@code UnsupportedOperationException} ⇒ 与 Go 的「不支持」同形。
     */
    private static final class GoLimitedInputStream extends java.io.InputStream {

        private final java.io.InputStream delegate;
        private long remaining;

        GoLimitedInputStream(java.io.InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws java.io.IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int b = delegate.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int n = delegate.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        @Override
        public long skip(long n) throws java.io.IOException {
            if (remaining <= 0L) {
                return 0L;
            }
            long skipped = delegate.skip(Math.min(n, remaining));
            remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws java.io.IOException {
            return (int) Math.min(delegate.available(), remaining);
        }

        @Override
        public void close() throws java.io.IOException {
            delegate.close();
        }
    }

    public GoStyleJsonBodyConverter(JsonMapper mapper) {
        super(mapper);
        // 认领所有媒体类型，让 Spring 不再在到达转换器之前就抛 415。
        // ⚠️ 这必须配合 WebMvcConfigurers 里的「插到 Boot 的 Jackson 转换器之前」，
        //    否则它排在后面，永远不会被选中。
        setSupportedMediaTypes(List.of(MediaType.ALL));
    }

    /**
     * Go 是否会读这个请求体 —— 逐条对齐 {@code withJsonBody}。
     *
     * @param contentType <b>原始</b> Content-Type 头值（可为 null / 空）。
     *                   ⚠️ <b>必须传客户端发来的原始字符串</b>，<b>不要</b>传
     *                   {@code MediaType.toString()} —— 那会把 type/subtype <b>小写化</b>
     *                   ⇒ 判定从 Go 的大小写<b>敏感</b>变成<b>不敏感</b>（2026-10-25 T-1 修复）。
     *                   正确取法：{@code HttpHeaders#getFirst(HttpHeaders#CONTENT_TYPE)}
     *                   或 Servlet 的 {@code request.getHeader("Content-Type")} /
     *                   {@code request.getContentType()}。
     * @param contentLength {@code HttpInputMessage} 报告的 Content-Length；
     *                      chunked 时 Spring 给 -1，与 Go 的语义一致
     *
     * <p><b>为什么是 public</b>：它是「Go 何时读 body」这条规则的<b>唯一真源</b>，
     * 且 {@code goReadsBody} 与 {@link #read} 必须永远一致 ⇒ 让业务模块的测试能直接断言
     * 它的边界，而不是复制一份判据到测试里（复制品必然与实现漂移）。
     *
     * <p>⚠️ <b>2026-10-25 补记：仓库里曾存在 3 份「复制品」</b>（favorite / hot / article 各自抄了一份
     * {@code contentType.contains("application/json")}）—— 它们当时取的是<b>原始头值</b>
     * ⇒ 判定与 Go 一致，但**没有调用本方法** ⇒ 是一枚定时炸弹：
     * 哪天有人把其中一份改成「用 MediaType 解析」，RAG 就会**内部自相矛盾**
     * （闸门说不读、解析器说要读）。⇒ 详见
     * {@code docs/DRIFT/global/cross-domain.md} X-9。
     *
     * <p>✅ <b>2026-10-25 第十六轮收敛</b>：hot 与 article 两份已改为<b>调用</b>本类的
     * {@link #goReadsBody(jakarta.servlet.http.HttpServletRequest)} ⇒ 判据与取输入都只剩一份实现。
     * ⇒ <b>只剩 favorite 一份</b>（用户重申 favorite 零权限 ⇒ 该份<b>永久残留</b>，只能记账不能改）。
     * <br>⇒ 残留的那一份仍然取原始头值 ⇒ 判定仍与 Go 一致 ⇒ <b>当前行为无偏差</b>；
     * 风险仅在于「未来有人把它改成 MediaType 解析」⇒ 由
     * {@code tools/difftest/check-go-json-body-predicate.py} 守门（favorite 列入白名单并写明原因）。
     */
    public static boolean goReadsBody(String contentType, long contentLength) {
        return contentLength > 0L
                && contentType != null
                && contentType.contains(GO_JSON_CONTENT_TYPE_MARK);
    }

    /**
     * {@link #goReadsBody(String, long)} 的 <b>servlet 版</b> —— 连「怎么从请求里取这两个输入」
     * 一起收进唯一真源。
     *
     * <h2>为什么连取输入这一步也要收进来（2026-10-25 第十六轮）</h2>
     *
     * <p>之前各域是自己取输入、自己判的（favorite / hot / article 各抄一份）⇒ 三处都可能取错。
     * 真正的坑在<b>取头值</b>这一步，而不是判据本身：
     * <ul>
     *   <li>取 {@code request.getContentType()} ⇒ <b>原始</b>头值 ⇒ 大小写敏感 ⇒ 与 Go 同形 ✔</li>
     *   <li>取 {@code request.getHeader("Content-Type")} ⇒ 同样是原始头值 ✔</li>
     *   <li>取 {@code MediaType.parseMediaType(...).toString()} ⇒ <b>已被小写化</b> ⇒
     *       判定变成大小写<b>不</b>敏感 ⇒ 与 Go <b>相反</b> ❌</li>
     * </ul>
     *
     * <h3>⚠️ {@code getContentType()} 在 Tomcat 上确实是原始值（逐行实测，非推测）</h3>
     * <pre>
     * org/apache/catalina/connector/Request.java:1081-1083
     *     public String getContentType() { return coyoteRequest.getContentType(); }
     * org/apache/coyote/Request.java:694-700
     *     public String getContentType() { contentType(); … return contentTypeMB.toStringType(); }
     * org/apache/tomcat/util/buf/MessageBytes.java:214-229
     *     public String toStringType() { … case T_BYTES: setString(byteC.toString()); … }
     * org/apache/tomcat/util/buf/ByteChunk.java:602-609
     *     public String toString() { return toString(REPLACE, REPLACE); }   // ← 只做字符集解码
     * </pre>
     * ⇒ 全链<b>没有</b>任何小写化 / MIME 归一 ⇒ {@code Content-Type: Application/JSON}
     * 到达应用层时<b>仍是</b> {@code Application/JSON} ⇒ {@code contains("application/json")}
     * 判定为假 ⇒ 与 Go 的 {@code strings.Contains}（同样大小写敏感）<b>同形</b>。
     * <br>⇒ ⚠️ 真容器上的这条事实另有<b>真 Tomcat 守门</b>（裸 socket 逐字发头）：
     * {@code service/common} 的 {@code GoStyleJsonBodyRawHeaderTomcatTest}。
     *
     * <h3>⚠️ 为什么用 {@code getContentLengthLong()} 而不是 {@code getContentLength()}</h3>
     * <pre>
     * org/apache/coyote/Request.java:664-671
     *     public int getContentLength() {
     *         long length = getContentLengthLong();
     *         if (length &lt; Integer.MAX_VALUE) { return (int) length; }
     *         return -1;                       // ← ⚠️ 超 2GiB 变成 -1
     *     }
     * </pre>
     * ⇒ Go 的 {@code r.ContentLength} 是 <b>int64</b>，2GiB 以上仍是正数 ⇒ 命中
     * {@code r.ContentLength > 0} ⇒ <b>读 body</b>；
     * ⇒ 而 Java 侧取 int 版会拿到 {@code -1} ⇒ 判据为假 ⇒ <b>不读</b> ⇒ 与 Go 相反。
     * ⇒ 必须取 {@code getContentLengthLong()}（{@code Request.java:999-1001}）。
     *
     * @param request servlet 请求（{@code null} 视为「不读」）
     * @return Go 是否会读这个请求体
     */
    public static boolean goReadsBody(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        return goReadsBody(request.getContentType(), request.getContentLengthLong());
    }

    /**
     * Spring 7 / Jackson 3 的签名是 {@code read(ResolvableType, HttpInputMessage, Map)}
     * （Jackson 2 时代是 {@code read(Type, Class, HttpInputMessage)}）。
     * ⚠️ 这里用 <b>编译期实测</b>的签名（{@code javap} {@code AbstractJacksonHttpMessageConverter}），
     * 不是照抄旧版教程 —— 写错签名会直接编译失败，反而是好事。
     */
    @Override
    public Object read(org.springframework.core.ResolvableType type, HttpInputMessage inputMessage,
                       java.util.Map<String, Object> hints)
            throws IOException, HttpMessageNotReadableException {

        // ⚠️ 2026-10-25（T-1 修复）：这里必须取**原始头值**，不能取 `getContentType().toString()`。
        //
        //   Go: `strings.Contains(r.Header.Get("Content-Type"), "application/json")`
        //       `r.Header.Get` 返回的是**客户端发来的原始字符串** ⇒ `strings.Contains`
        //       **大小写敏感** ⇒ `Content-Type: Application/JSON` ⇒ Go **不读 body**。
        //
        //   而 `HttpHeaders.getContentType()` 会把头**解析成 MediaType**，
        //   其 `toString()` 已把 type/subtype **小写化** ⇒ `contains` 变成大小写**不**敏感
        //   ⇒ `Application/JSON` 在 RAG 侧会被**当成 JSON 读进去** ⇒ 与 Go 相反。
        //
        //   ⇒ 改用 `getFirst(CONTENT_TYPE)` 取原始值 ⇒ 判定回到与 Go 同形。
        //
        //   ⚠️ **「判定」这一层修好之后，「解析」那一层还差一步**：
        //   {@code super.read(...)} 内部仍会调 {@code getHeaders().getContentType()}
        //   做 MediaType 解析 ⇒ **畸形头（如 {@code application/json, application/xml}）会抛
        //   {@code InvalidMediaTypeException}**，而 Go 只做字面 Contains、不会抛。
        //   ⇒ 下方的 {@link SanitizedJsonContentTypeInputMessage} 就是补这一步的。
        String contentType = inputMessage.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        long contentLength = inputMessage.getHeaders().getContentLength();

        if (!goReadsBody(contentType, contentLength)) {
            // Go: mapping.UnmarshalJsonMap(nil, v) —— **不看 body**
            // ⚠️ 刻意**不**消费请求流：Go 侧同样不读，body 留在那里由容器处理。
            //    这里读了反而会让下游（如 MaxBytes 之后的日志/审计）看到空 body。
            return null;
        }
        // ⚠️ 2026-10-25（T-1 残余 ① 修复）：Go 的 ParseJsonBody 一旦判定命中，就
        //   **只做字面 Contains，然后照常读 body** —— 它**从不解析 MIME**：
        //
        //     if withJsonBody(r) {
        //         reader := io.LimitReader(r.Body, maxBodyLen)
        //         return mapping.UnmarshalJsonReader(reader, v)   // ← 不看 Content-Type
        //     }
        //
        //   而 Spring 的解析路径**一定会**把 Content-Type 解析成 MediaType
        //   （getJavaType / charset）⇒ 畸形头会抛 InvalidMediaTypeException（unchecked）
        //   ⇒ 请求 500 ⇒ 与 Go 相反。
        //
        //   ⇒ 办法：把 message **包一层**，让 Spring 看到一个**已净化的** Content-Type，
        //     而**判定**仍然用原始值（上面那行）⇒ 两层各归各：
        //       判定层 = Go 的字面 Contains（原始值）
        //       解析层 = 一个一定能被 Spring 解析的 application/json
        //
        //   ⚠️ 顺带把 charset 也去掉：Go 的 encoding/json **只认 UTF-8**（无 charset 概念）
        //   ⇒ 客户端写 `charset=gbk` 时 Go 解析失败（非法 UTF-8）⇒ 去掉 charset 后
        //   Jackson 按 UTF-8 读 ⇒ 与 Go 同形。
        return super.read(type, new SanitizedJsonContentTypeInputMessage(inputMessage), hints);
    }

    /**
     * 把 {@link HttpInputMessage} 的 {@code Content-Type} 换成<b>已净化的</b> {@code application/json}
     * （<b>不带 charset</b>），其余头与 body 原样透传。
     *
     * <p>⚠️ <b>为什么不能让 Spring 看见客户端的原始 Content-Type</b>：Spring 的解析路径一定会
     * 把它当 MIME 解析 ⇒ 畸形头抛 {@code InvalidMediaTypeException} ⇒ 请求 500；
     * 而 Go 只做字面 {@code Contains}、**从不解析 MIME** ⇒ 不会抛。
     *
     * <p>⚠️ <b>为什么去掉 charset</b>：Go 的 {@code encoding/json} 只认 UTF-8
     * ⇒ 客户端声明 {@code charset=gbk} 时 Go 因非法 UTF-8 而**解析失败**；
     * 若把 charset 透传给 Spring，Jackson 会按 GBK 解码 ⇒ 变成「成功」⇒ 与 Go 相反。
     * ⇒ 去掉 charset ⇒ Jackson 按 UTF-8 读 ⇒ 与 Go 同形。
     *
     * <p>⚠️ <b>残余</b>：Jackson 3 会按 BOM/内容**自动探测** UTF-8/16/32 ⇒ 带 BOM 的 UTF-16
     * 仍能被 RAG 解析，而 Go 会拒绝 ⇒ 一条极窄的残余，已登记在
     * {@code docs/DRIFT/global/common-tails.md}「T-1 残余 ②」。
     */
    private static final class SanitizedJsonContentTypeInputMessage implements HttpInputMessage {
        private final HttpInputMessage delegate;
        private final HttpHeaders headers;

        SanitizedJsonContentTypeInputMessage(HttpInputMessage delegate) {
            this.delegate = delegate;
            HttpHeaders original = delegate.getHeaders();
            HttpHeaders copy = new HttpHeaders();
            original.forEach((name, values) -> {
                if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                    copy.put(name, values);
                }
            });
            // ⚠️ 刻意**不带 charset**（理由见类注释）
            copy.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            long len = original.getContentLength();
            if (len >= 0L) {
                copy.setContentLength(len);
            }
            this.headers = copy;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            // ⚠️ 2026-10-25（第十六轮）：这里必须加 Go 的 maxBodyLen 上限。
            //
            //   Go: rest/httpx/requests.go:119
            //       reader := io.LimitReader(r.Body, maxBodyLen)   // maxBodyLen = 8 << 20
            //
            //   ⇒ Go 对 JSON body **只读前 8 MiB** ⇒ 一个 > 8 MiB 的 JSON body
            //     在 Go 侧被**静默截断** ⇒ 解析器拿到半个文档 ⇒ 报错（域自己的错误形状）。
            //   ⇒ 而 RAG 原先**整个读进堆里**并成功解析 ⇒ 两侧行为相反。
            //
            //   ⇒ 实测证据：tools/json-body-probe 的用例 J20（9 MiB 单个 JSON 对象）
            //     ⇒ Go REJECTED（错误详情长度 8388643 = 8 MiB + 3 ⇒ 截断点精确在 8 MiB）
            //     ⇒ RAG 原先 ACCEPTED。
            //
            //   ⇒ 这里用 goLimitedBody（流式）而不是 readNBytes：Go 的 json.Decoder
            //     只读它需要的那个值，不会为一个小 JSON 等满 8 MiB（详见该方法的注释）。
            return GoStyleJsonBodyConverter.goLimitedBody(delegate.getBody());
        }
    }

    /**
     * 🔴 <b>响应侧一律不认领</b>（2026-10-22 实测加的，代价是一条 admin-api 回归）。
     *
     * <p><b>为什么必须写这一条</b>：构造函数里的 {@code setSupportedMediaTypes(ALL)}
     * 对<b>读写两侧同时生效</b>。只覆写 {@code canRead} 是不够的 ——
     * 实测 admin-api 因此回归 4 条：它的 {@code GlobalExceptionHandler} 返回
     * {@code ResponseEntity<String>} + {@code Content-Type: text/plain}
     * （Go 的 {@code http.Error} 语义，DRIFT-016），本转换器抢在 {@code StringHttpMessageConverter}
     * 之前把它<b>按 JSON 字符串写出</b> ⇒ body 从
     * {@code field "uid" is not set} 变成 {@code "field \"uid\" is not set\n"}。
     *
     * <p>⚠️ 现象极具迷惑性：<b>状态码与 Content-Type 都对</b>（仍是 400 + text/plain），
     * 只有 body 被 JSON 转义了 —— 看响应头会以为"没问题"。
     * ⇒ 与 §3.3 同款：<b>响应对了不代表 body 对</b>。
     *
     * <p>本类<b>只</b>需要改读侧行为，写侧一律交回 Spring 的默认转换器。
     */
    @Override
    public boolean canWrite(org.springframework.core.ResolvableType type, Class<?> clazz,
                            MediaType mediaType) {
        return false;
    }

    /**
     * ⚠️ <b>这一条是上面那条的转发，不要只留其中之一。</b>
     *
     * <p>2026-10-22 实测踩过：先前只覆写了 <b>两参</b> {@code canWrite(Class, MediaType)}，
     * admin-api <b>仍然回归</b> —— 因为 {@code SmartHttpMessageConverter} 里
     * {@code canWrite(ResolvableType, Class, MediaType)} 是<b>抽象</b>方法、
     * 两参版只是 {@code default} 委托给<b>它</b>，而 {@code MessageWriter} 调的正是那个抽象方法。
     * {@code AbstractSmartHttpMessageConverter} 的两参实现体（javap 实测）第一件事就是
     * {@code ResolvableType.forClass(clazz)} 然后 {@code invokevirtual canWrite(ResolvableType,...)}。
     * ⇒ 覆写「委托的那一环」等于没覆写。
     * <p>同理 {@code canRead} 必须覆写<b>带 ResolvableType 的那一个</b>（本类已经如此）。
     */
    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    /**
     * 无条件认领所有媒体类型：把「要不要读 body」的判断<b>全部交给 {@link #read}</b>。
     *
     * <p>若不在这里放开，Spring 会在<b>选择转换器阶段</b>就因媒体类型不匹配而抛
     * {@code HttpMediaTypeNotSupportedException}（415），根本到不了 {@link #read}。
     */
    @Override
    public boolean canRead(org.springframework.core.ResolvableType type, MediaType mediaType) {
        return true;
    }
}