import com.rag.common.http.GoStyleJsonBodyConverter;
import com.rag.hot.hot.api.form.GoFormValues;

import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

/**
 * RAG 侧「Go 是否读 body」探针 —— 走<b>项目真实实现</b>，不自己复刻判据。
 *
 * <h2>为什么这里必须用真实实现（AGENTS.md §3.3）</h2>
 * <p>被测的两件东西都是<b>生产代码</b>：
 * <ul>
 *   <li>{@code com.rag.common.http.GoStyleJsonBodyConverter#goReadsBody} ——
 *       「Go 何时读 body」这条规则的<b>唯一真源</b>；</li>
 *   <li>{@code com.rag.hot.hot.api.form.GoFormValues#requireParsableJsonBody} ——
 *       hot 域 api 的 JSON body 拒绝路径（Go 出处：{@code rest/httpx/requests.go:117-124,153-155}）。</li>
 * </ul>
 * ⇒ 探针只负责<b>提供输入</b>（头值 / 长度 / body），判定一律由生产代码给出。
 *
 * <h2>判定口径（与 Go 半边严格同形）</h2>
 * <pre>
 *   Go  : httpx.Parse(r, &amp;req) 返错 ⇒ REJECTED（读了 body 且解析失败）
 *   RAG : GoFormValues.requireParsableJsonBody(request) 抛异常 ⇒ REJECTED
 * </pre>
 *
 * <h2>⚠️ 输入侧的一个已知近似（已由另一条真容器守门补上）</h2>
 * <p>本探针用 {@code MockHttpServletRequest} 提供输入 ⇒ 它的 {@code getContentType()}
 * 返回<b>原样</b>设置值、{@code getContentLength()} 在未设 content 时返回 {@code -1}
 * ⇒ 与 Tomcat 的行为一致（Tomcat 侧由 {@code org.apache.coyote.Request#getContentType}
 * ⇒ {@code MessageBytes#toStringType()} ⇒ 只做字符集解码、<b>不做归一</b>）。
 * ⇒ 「Tomcat 真容器上头值大小写不被归一」这条由
 * {@code service/common} 的 {@code GoStyleJsonBodyRawHeaderTomcatTest}（真 Tomcat + 裸 socket）单独守门。
 *
 * <p>输出格式：{@code <用例名>|<判定>|<细节>} —— 用例名必须与 Go 半边<b>逐字一致</b>。
 */
public final class Probe {

    public static void main(String[] args) {
        // ── ① 判据矩阵：Content-Type 字面匹配（Go: strings.Contains，大小写敏感）──
        emit("J01 标准 JSON + 畸形 body", "application/json", "{".getBytes(StandardCharsets.UTF_8));
        emit("J02 大写 JSON（Application/JSON）+ 畸形 body", "Application/JSON", "{".getBytes(StandardCharsets.UTF_8));
        emit("J03 全大写 JSON（APPLICATION/JSON）+ 畸形 body", "APPLICATION/JSON", "{".getBytes(StandardCharsets.UTF_8));
        emit("J04 混合大小写（application/JSON）+ 畸形 body", "application/JSON", "{".getBytes(StandardCharsets.UTF_8));
        emit("J05 带 charset + 畸形 body", "application/json; charset=utf-8", "{".getBytes(StandardCharsets.UTF_8));
        emit("J06 无空格 charset + 畸形 body", "application/json;charset=utf-8", "{".getBytes(StandardCharsets.UTF_8));
        emit("J07 +json 后缀（application/vnd.api+json）+ 畸形 body", "application/vnd.api+json", "{".getBytes(StandardCharsets.UTF_8));
        emit("J08 非 JSON（text/plain）+ 畸形 body", "text/plain", "{".getBytes(StandardCharsets.UTF_8));
        emit("J09 无 Content-Type + 畸形 body", null, "{".getBytes(StandardCharsets.UTF_8));
        emit("J10 逗号多值头（application/json, application/xml）+ 畸形 body", "application/json, application/xml", "{".getBytes(StandardCharsets.UTF_8));
        emit("J11 畸形头内分号（application/json;;charset）+ 畸形 body", "application/json;;charset", "{".getBytes(StandardCharsets.UTF_8));

        // ── ② 判据矩阵：Content-Length ──
        emit("J12 Content-Length=0 + JSON 头", "application/json", new byte[0]);
        // J13 chunked：Go 侧 r.ContentLength == -1 ⇒ 不读 body
        //    ⇒ RAG 侧「没有 content」时 MockHttpServletRequest.getContentLength() 就是 -1
        emit("J13 chunked（Content-Length=-1）+ JSON 头 + 畸形 body", "application/json", null);

        // ── ③ 顶层形态（Go 只接受 JSON **对象**）──
        emit("J14 顶层数组 + JSON 头", "application/json", "[1,2]".getBytes(StandardCharsets.UTF_8));
        emit("J15 顶层 null + JSON 头", "application/json", "null".getBytes(StandardCharsets.UTF_8));
        emit("J16 顶层数字 + JSON 头", "application/json", "123".getBytes(StandardCharsets.UTF_8));
        emit("J17 顶层字符串 + JSON 头", "application/json", "\"x\"".getBytes(StandardCharsets.UTF_8));
        emit("J18 尾随垃圾（{\"a\":1} garbage）+ JSON 头", "application/json", "{\"a\":1} garbage".getBytes(StandardCharsets.UTF_8));
        emit("J19 合法对象 + JSON 头", "application/json", "{\"page\":9,\"page_size\":3}".getBytes(StandardCharsets.UTF_8));

        // ── ④ 🔴 Go 的 maxBodyLen = 8MiB 静默截断 ──
        StringBuilder brief = new StringBuilder();
        for (int i = 0; i < 9 * 1024 * 1024; i++) {
            brief.append('x');
        }
        emit("J20 9MiB 单个 JSON 对象 + JSON 头", "application/json",
                ("{\"brief\":\"" + brief + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private static void emit(String name, String contentType, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (contentType != null) {
            request.setContentType(contentType);
        }
        if (body != null) {
            request.setContent(body);
        }

        // 共享层唯一真源的判定（单独打出来，便于定位「是判据不同」还是「消费点不同」）
        boolean reads = GoStyleJsonBodyConverter.goReadsBody(
                request.getContentType(), request.getContentLengthLong());

        String verdict;
        String detail;
        try {
            GoFormValues.requireParsableJsonBody(request);
            verdict = "ACCEPTED";
            detail = "reads=" + reads;
        } catch (RuntimeException e) {
            verdict = "REJECTED";
            Throwable cause = e.getCause() == null ? e : e.getCause();
            detail = "reads=" + reads + " " + cause.getClass().getSimpleName();
        }
        System.out.println(name + "|" + verdict + "|" + detail);
    }
}
