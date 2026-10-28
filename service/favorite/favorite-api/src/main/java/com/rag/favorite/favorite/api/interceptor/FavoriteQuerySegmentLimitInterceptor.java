package com.rag.favorite.favorite.api.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 🔴 favorite-api 的 **query 段数闸门** —— 逐字复刻 Go {@code net/url} 的两条 query 解析规则。
 *
 * <h2>守的是什么漂移</h2>
 * <p>Go 侧 favorite-api 的 <b>7/7 handler</b> 第一行都是
 * {@code httpx.Parse(r, &req)}（真源 {@code service/favorite/api/internal/handler/favorite/} 下
 * {@code create_favorite_folder_handler.go:18} · {@code create_favorite_handler.go:18} ·
 * {@code delete_favorite_folder_handler.go:18} · {@code delete_favorite_handler.go:18} ·
 * {@code get_favorite_folders_handler.go:18} · {@code get_favorites_by_folder_handler.go:18} ·
 * {@code update_favorite_folder_name_handler.go:18}）
 * ⇒ ⇒ {@code httpx.Parse} → {@code httpx.ParseForm}（go-zero v1.10.2
 * {@code rest/httpx/requests.go:57} / {@code rest/httpx/util.go:25}）
 * → {@code r.ParseForm()} → {@code url.ParseQuery(r.URL.RawQuery)}
 * ⇒ ⇒ <b>每一次请求都会先吃一遍 {@code net/url} 的 query 解析规则</b>。
 *
 * <h2>Go 的两条规则（真源 · 消费点，不是声明点）</h2>
 * <pre>
 *   Go 1.25 net/url/url.go:1019-1022
 *       func parseQuery(m Values, query string) (err error) {
 *           if !urlParamsWithinMax(strings.Count(query, "&amp;") + 1) {
 *               return errors.New("number of URL query parameters exceeded limit")
 *           }
 *           for query != "" {
 *               key, query, _ = strings.Cut(query, "&amp;")
 *               if strings.Contains(key, ";") {
 *                   err = fmt.Errorf("invalid semicolon separator in query")
 *                   continue
 *               }
 *               ...
 *
 *   net/url/url.go:998-1006
 *       const defaultMaxParams = 10000
 *       func urlParamsWithinMax(params int) bool {
 *           withinDefaultMax := params &lt;= defaultMaxParams
 *
 *   ⇒ ① 段数 = {@code strings.Count(rawQuery, "&amp;") + 1} ⇒ <b>&gt; 10000 即拒</b>
 *     （⇒ 放行 10000 段、第 10001 段报错）
 *   ⇒ ② rawQuery 里<b>任何位置</b>出现 {@code ';'} 即拒
 *     （检查发生在「段」上，且在 {@code key == ""} 判空<b>之前</b> ⇒ 空段也含 ';'）
 *     ⇒ ⚠️ 判的是<b>原始</b> query 串 ⇒ {@code %3B}（已转义）<b>不</b>触发
 * </pre>
 *
 * <h2>为什么 RAG 侧会漂移（不是「Tomcat 差一格」那么简单）</h2>
 * <ul>
 *   <li>共享层把 Tomcat 的容器上限<b>无条件</b>抬到 {@code 10001}
 *       （{@code GoStyleTomcatParameterLimitCustomizer#MAX_PARAMETER_COUNT}），
 *       理由写得很清楚：<b>「把判定权交还给应用层」</b>
 *       ⇒ ⇒ 本域（{@code rag.http.go-form-limit.mode = off} ⇒ 共享层<b>不装</b>闸门）
 *       就得<b>自己</b>装这道段数判据 —— 这正是共享层 javadoc 里
 *       「favorite 之所以停在 OFF，是因为它<b>语义上就该是 OFF</b>」的那一半。</li>
 *   <li>🔴 <b>而且漂移面比「差一格」更大</b>：本域只有 {@code GET /favorite/v1/item/list}
 *       会调 {@code request.getParameterMap()}（{@code FavoriteApiSupport#goFormFirstValue}）
 *       ⇒ ⇒ <b>另外 6 个 JSON body 端点根本不会让 Tomcat 解析 query</b>
 *       ⇒ ⇒ Tomcat 的容器上限<b>永远不触发</b> ⇒ ⇒ 那 6 个端点连 {@code 10002} 段都不拒
 *       ⇒ ⇒ <b>Go 侧是 7/7 都在第 10001 段拒</b>。</li>
 * </ul>
 *
 * <h2>本实现怎么做（表达层）</h2>
 * <ul>
 *   <li>判据取<b>原始</b> query 串（{@code request.getQueryString()}），
 *       <b>不</b>调 {@code getParameterMap()} ⇒ ⇒ ① 不会触发 Tomcat 的解析
 *       ⇒ ⇒ ② 不会与容器上限 {@code 10001} 纠缠 ⇒ ⇒ ③ 计数口径与 Go <b>逐字一致</b>
 *       （{@code Count("&amp;") + 1}，含空段）。</li>
 *   <li>拒的方式 = 抛 {@link IllegalArgumentException} ⇒ ⇒ 落到本域
 *       {@code GlobalExceptionHandler#handleIllegalArgument} ⇒ ⇒ {@code invalidParam()}
 *       ⇒ ⇒ <b>HTTP 200 + {"code":5003,"msg":"参数错误","data":null}</b>
 *       ⇒ ⇒ 与 Go 的 {@code httpx.OkJsonCtx(...ErrorInvalidParam)} <b>逐字同形</b>
 *       （Go 侧 {@code get_favorites_by_folder_handler.go:18-24}）。</li>
 *   <li><b>次序 = Go 的次序</b>：Go 的 {@code httpx.Parse} 在 <b>handler 内部</b>
 *       ⇒ ⇒ 解析<b>在鉴权之后</b> ⇒ ⇒ 本拦截器注册在 {@code order = 1}
 *       （鉴权拦截器 {@code FavoriteJwtAuthInterceptor} 是 {@code order = 0}）
 *       ⇒ ⇒ <b>无 token ⇒ 401 优先</b>（Go 同）⇒ ⇒ 不制造「先解析后鉴权」的偏离。</li>
 * </ul>
 *
 * <p><b>证据强度</b>：L1 真源逐行（Go 1.25.11 {@code net/url/url.go} + go-zero v1.10.2
 * {@code rest/httpx}）+ 本域真 Tomcat 门禁（{@code FavoriteApiRealTomcatGateTest} 的真容器 +
 * 真 JWT + 真 HTTP 客户端）。
 */
@Component
public class FavoriteQuerySegmentLimitInterceptor implements HandlerInterceptor {

    /**
     * Go 的 query 段数上限 —— {@code net/url/url.go:1001} {@code const defaultMaxParams = 10000}。
     *
     * <p>⚠️ <b>这是 Go 的常量，不是本实现自定的数</b> ⇒ ⇒ 守门断言必须独立写死 {@code 10000}
     * （不得引用本常量，否则守门会跟着实现一起漂 ⇒ 恒绿）。
     */
    static final int GO_MAX_QUERY_PARAMS = 10000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String rawQuery = request.getQueryString();
        if (rawQuery == null || rawQuery.isEmpty()) {
            // Go 侧 parseQuery 对空 query 走 `strings.Count("", "&") + 1 = 1` ⇒ ≤ 10000 ⇒ 放行
            return true;
        }

        // ① 段数上限（Go net/url/url.go:1020-1022）：`Count(rawQuery, "&") + 1 > 10000` 即拒
        int segments = countAmpersands(rawQuery) + 1;
        if (segments > GO_MAX_QUERY_PARAMS) {
            throw new IllegalArgumentException("number of URL query parameters exceeded limit");
        }

        // ② 分号分隔符（Go net/url/url.go:1026-1029）：rawQuery 任一处出现 ';' 即拒
        //    ⚠️ 判的是**原始**串 ⇒ '%3B'（已转义）不触发 ⇒ 与 Go 一致
        if (rawQuery.indexOf(';') >= 0) {
            throw new IllegalArgumentException("invalid semicolon separator in query");
        }

        return true;
    }

    /**
     * 数 query 串里的 {@code '&'} 个数 —— 逐字对应 Go 的 {@code strings.Count(query, "&")}。
     *
     * <p>⚠️ <b>不能用 {@code String.split("&").length}</b>：{@code split} 会丢弃尾部空段
     * （{@code "a=1&"} ⇒ 长度 1，而 {@code Count} = 1 ⇒ 段数 2）⇒ ⇒ 与 Go 的计数口径不一致
     * ⇒ ⇒ 这里手写计数。
     */
    private static int countAmpersands(String rawQuery) {
        int count = 0;
        for (int i = 0; i < rawQuery.length(); i++) {
            if (rawQuery.charAt(i) == '&') {
                count++;
            }
        }
        return count;
    }
}
