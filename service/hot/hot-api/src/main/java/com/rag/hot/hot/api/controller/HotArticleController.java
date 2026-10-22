package com.rag.hot.hot.api.controller;

import com.rag.hot.hot.api.dto.HotArticlesReq;
import com.rag.hot.hot.api.dto.HotArticlesResp;
import com.rag.hot.hot.api.form.GoFormValues;
import com.rag.hot.hot.api.service.HotArticleQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单端点 {@code GET /hot/v1/articles} —— 对应 Go
 * {@code api/internal/handler/routes.go:15-26} 的<b>唯一</b>路由表：
 *
 * <pre>
 *   server.AddRoutes(
 *       []rest.Route{
 *           {
 *               Method:  http.MethodGet,
 *               Path:    "/articles",
 *               Handler: hot.GetHotArticlesHandler(serverCtx),
 *           },
 *       },
 *       rest.WithPrefix("/hot/v1"),
 *   )
 * </pre>
 *
 * <h2>⚠️ 零鉴权：本类<b>刻意没有</b>任何 interceptor / SecurityFilter</h2>
 * Go 侧的 {@code AddRoutes} 只有 {@code rest.WithPrefix}，<b>没有</b>
 * {@code rest.WithJwt}、{@code rest.WithCors}、{@code rest.WithMaxBytes}。
 * ⇒ {@code GET /hot/v1/articles} 是<b>公开读</b>端点。
 * 引入 {@code JwtAuthInterceptor} 会让「匿名可拉热榜」变成「必须带 token」——
 * 这是 L2 级行为变更（不是「补一个缺失的安全措施」）。
 * ⇒ 本模块因此也<b>不需要</b> {@code WebMvcConfig}：Go 侧没有任何要注册的东西。
 *
 * <h2>⚠️ 两个 {@code @RequestParam} 都取<b>原始字符串</b>，不用 {@code int}</h2>
 * 写成 {@code int} 会让 Spring 用 {@code NumberUtils.parseNumber} 转换，
 * 与 go-zero 的 {@code strconv.ParseInt(str, 10, 32)} 有三条分歧，
 * 且 {@code ?page=}（空值）在 Go 是「取默认值」而 Spring 会抛类型不匹配。
 * 逐条依据见 {@link GoFormValues}。
 *
 * <h2>⚠️ query 键名是 {@code page_size}（snake_case）</h2>
 * Go tag 是 {@code form:"page_size,default=20"} ⇒ wire 上的键名带下划线。
 * <b>不要</b>按 Java 命名习惯写成 {@code pageSize}。
 */
@RestController
@RequestMapping("/hot/v1")
public class HotArticleController {

    /** Go {@code types.go:23} {@code form:"page,default=1"}。 */
    private static final String PARAM_PAGE = "page";

    /** Go {@code types.go:24} {@code form:"page_size,default=20"}。 */
    private static final String PARAM_PAGE_SIZE = "page_size";

    private final HotArticleQueryService hotArticleQueryService;

    public HotArticleController(HotArticleQueryService hotArticleQueryService) {
        this.hotArticleQueryService = hotArticleQueryService;
    }

    /**
     * {@code GET /hot/v1/articles} —— Go {@code getHotArticlesHandler.go:15-32} +
     * {@code getHotArticlesLogic.go:54-89}。
     *
     * <p>Go 的 handler 是「薄适配层」：Parse → Logic → {@code httpx.OkJsonCtx}，
     * <b>不做任何校验</b>（{@code page=-1} 会原样进 logic）。
     * 本类保持同样形状：只做解析与委托。
     */
    @GetMapping("/articles")
    public HotArticlesResp getHotArticles(
            @RequestParam(name = PARAM_PAGE, required = false) String rawPage,
            @RequestParam(name = PARAM_PAGE_SIZE, required = false) String rawPageSize) {
        HotArticlesReq req = new HotArticlesReq(
                GoFormValues.int32(rawPage, PARAM_PAGE, 1),
                GoFormValues.int32(rawPageSize, PARAM_PAGE_SIZE, 20));
        return hotArticleQueryService.getHotArticles(req);
    }
}