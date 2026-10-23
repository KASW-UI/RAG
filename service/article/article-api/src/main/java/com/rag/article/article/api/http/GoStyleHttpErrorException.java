package com.rag.article.article.api.http;

import org.springframework.http.HttpStatus;

/**
 * Go {@code http.Error(w, err.Error(), <非 400 的状态码>)} 的载体。
 *
 * <h2>为什么需要它（与 {@link GoStyleBadRequestException} 的分工）</h2>
 * <p>两者都是「裸状态码 + text/plain」，区别只在<b>状态码</b>：
 * <table border="1">
 *   <caption>两个类型的分工</caption>
 *   <tr><th>类型</th><th>HTTP</th><th>对应 Go 分支</th></tr>
 *   <tr>
 *     <td>{@link GoStyleBadRequestException}</td>
 *     <td>恒 <b>400</b></td>
 *     <td>{@code doHandleError} 的 <b>else</b> 分支（{@code responses.go:156-157}）——
 *         <b>非</b> gRPC 错误</td>
 *   </tr>
 *   <tr>
 *     <td>本类型</td>
 *     <td><b>按 gRPC 码映射</b></td>
 *     <td>{@code doHandleError} 的 <b>IsGrpcError</b> 分支（{@code responses.go:150-153}）</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ 状态码不能靠业务码推导</h2>
 * <p>article 域的业务码（1001~1006 / 5001~5006）<b>全部</b>走「200 + 信封」，
 * 与本类型所在的「裸状态码」路径<b>互斥</b>。
 * <p>⇒ 本类型携带的是 <b>HTTP 语义</b>的状态码（500/503/504/429…），
 * 它<b>没有</b>任何 article 业务码 —— 因为 Go 侧这条路径压根不产生业务码。
 *
 * <h2>Go 出处</h2>
 * <pre>
 * // go-zero v1.10.2 · rest/httpx/responses.go:150-153
 * } else if errcode.IsGrpcError(err) {
 *     http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))
 * }
 * </pre>
 * 映射表见 {@link GoStyleGrpcStatusMapper}。
 */
public class GoStyleHttpErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public GoStyleHttpErrorException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** HTTP 状态码（Go {@code errcode.CodeFromGrpcError} 的映射结果）。 */
    public HttpStatus getStatus() {
        return status;
    }
}
