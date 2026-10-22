package com.rag.article.article.api.controller;

import com.rag.article.article.api.dto.CreateArticleReq;
import com.rag.article.article.api.dto.CreateArticleResp;
import com.rag.article.article.api.dto.DeleteArticleReq;
import com.rag.article.article.api.dto.DeleteArticleResp;
import com.rag.article.article.api.dto.GetArticleResp;
import com.rag.article.article.api.dto.ListArticlesReq;
import com.rag.article.article.api.dto.ListArticlesResp;
import com.rag.article.article.api.dto.UpdateArticleReq;
import com.rag.article.article.api.dto.UpdateArticleResp;
import com.rag.article.article.api.dto.UploadImageResp;
import com.rag.article.article.api.service.ArticleApiSupport;
import com.rag.article.article.api.service.ArticleCreateService;
import com.rag.article.article.api.service.ArticleDeleteService;
import com.rag.article.article.api.service.ArticleGetAuthorService;
import com.rag.article.article.api.service.ArticleGetService;
import com.rag.article.article.api.service.ArticleListService;
import com.rag.article.article.api.service.ArticleUpdateService;
import com.rag.article.article.api.service.ArticleUploadService;
import com.rag.users.common.http.GoStyleRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * article-api 的 7 个端点 —— 对应 Go {@code internal/handler/routes.go} 的<b>三段</b>
 * {@code server.AddRoutes}。
 *
 * <h2>路径表逐条对齐（这是 L1 契约，改一个就是破坏兼容）</h2>
 * <pre>
 *   第一段（WithJwt）：
 *     POST   /v1/article           CreateArticle
 *     PUT    /v1/article/:id       UpdateArticle
 *     DELETE /v1/article/:id       DeleteArticle
 *     GET    /v1/me/article/:id    GetAuthorArticle
 *   第二段（**无** WithJwt）：
 *     GET    /v1/article/:id       GetArticle
 *     GET    /v1/articles          ListArticles
 *   第三段（**无** WithJwt，WithMaxBytes 10MB）：
 *     POST   /v1/upload            UploadImage
 * </pre>
 *
 * <h2>⚠️ Go 侧 7 个 handler「同质」，这里合并成一个 controller 是 L4（实现机制）</h2>
 * <p>Go 每个 handler 都是这同一段：
 * <pre>
 *   var req types.XxxReq
 *   if err := httpx.Parse(r, &amp;req); err != nil { httpx.ErrorCtx(r.Context(), w, err); return }
 *   l := article.NewXxxLogic(r.Context(), svcCtx)
 *   resp, code := l.Xxx(&amp;req)
 *   httpx.OkJson(w, &amp;response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp})
 * </pre>
 * ⇒ <b>差异只有 req 类型与 logic 方法名</b>。Java 侧按「一个 controller + 每端点一个 service」
 * 组织：路由表在一处可见、鉴权边界在一处可见，
 * 而「入参解释」（path/form/json 三条通道）在 Spring 里本来就是三种不同的绑定方式，
 * 硬要「同质」就得自己写一套解析 —— 那正是 {@code §13-E} 说的「已由框架承担的职责不要重造」。
 *
 * <h2>⚠️ {@code POST /v1/upload} <b>公开</b></h2>
 * Go 第三段 {@code AddRoutes} <b>没有</b> {@code rest.WithJwt}。
 * 「上传图片要登录」是一个凭直觉就会加上去的规则，但 Go 侧<b>没有</b>。
 * 由 {@code JwtAuthInterceptor} 的受保护表保证它不被拦截
 * （见该类注释「7 个端点里恰好 4 个要 JWT」）。
 *
 * <h2>⚠️ query / form 参数刻意声明成 {@link String}</h2>
 * {@code incr_view} / {@code page} / {@code page_size} / {@code sort_by} / {@code desc}
 * 的真值表与默认值规则由 {@link com.rag.article.article.api.form.GoFormValues} 承担；
 * 若让 Spring 直接转 boolean/int，{@code ?incr_view=1} 会从「true」变成 400。
 */
@RestController
@RequestMapping("/v1")
public class ArticleController {

    private final ArticleCreateService createService;
    private final ArticleUpdateService updateService;
    private final ArticleDeleteService deleteService;
    private final ArticleGetService getService;
    private final ArticleGetAuthorService getAuthorService;
    private final ArticleListService listService;
    private final ArticleUploadService uploadService;
    private final ArticleApiSupport support;

    public ArticleController(ArticleCreateService createService,
                             ArticleUpdateService updateService,
                             ArticleDeleteService deleteService,
                             ArticleGetService getService,
                             ArticleGetAuthorService getAuthorService,
                             ArticleListService listService,
                             ArticleUploadService uploadService,
                             ArticleApiSupport support) {
        this.createService = createService;
        this.updateService = updateService;
        this.deleteService = deleteService;
        this.getService = getService;
        this.getAuthorService = getAuthorService;
        this.listService = listService;
        this.uploadService = uploadService;
        this.support = support;
    }

    // ============== 第一段：JWT 保护 ==============

    /**
     * Go {@code CreateArticleHandler}。
     *
     * <p>{@code @RequestBody(required = false)} + {@link GoStyleRequest#normalize}：
     * Go 侧 {@code httpx.Parse} 在「Content-Length 为 0 或 Content-Type 非 JSON」时
     * 走 {@code mapping.UnmarshalJsonMap(nil, v)}，即<b>当空对象继续解析</b>。
     * {@code required = false} 让 Spring 同样不因空 body 报 400，
     * {@code normalize} 补一个零值 DTO 后交给必填校验
     * （此时 {@code title} / {@code content} 缺席 ⇒ {@code is not set} ⇒ 400）。
     * ⇒ 两侧对「空 body」得到<b>同一个</b> 400。
     */
    @PostMapping("/article")
    public CreateArticleResp createArticle(@RequestBody(required = false) CreateArticleReq req,
                                           HttpServletRequest httpRequest) {
        CreateArticleReq body = GoStyleRequest.normalize(req, CreateArticleReq::new);
        // ⚠️ 顺序与 Go 一致：先解析身份（失败 → 1005），再进业务
        return createService.createArticle(body, support.userId(httpRequest));
    }

    /**
     * Go {@code UpdateArticleHandler}。
     *
     * <p>⚠️ {@code articleId} 来自 path，<b>不</b>参与 JSON 绑定（见
     * {@link UpdateArticleReq} 的类注释）。
     */
    @PutMapping("/article/{id}")
    public UpdateArticleResp updateArticle(@PathVariable("id") String id,
                                           @RequestBody(required = false) UpdateArticleReq req,
                                           HttpServletRequest httpRequest) {
        // ⚠️ 这里**不能**用 GoStyleRequest.normalize：它要求 T extends GoStyleRequiredFields，
        //    而 UpdateArticleReq 刻意**不**实现该接口 —— Go 侧它的七个 JSON 字段
        //    全部是 ,optional，永远不会触发 newInitError（见 UpdateArticleReq 的类注释）。
        //    强行实现只会给后人留一个「这里也校验必填」的错觉。
        // 空 body 时补零值 DTO：Go 侧 withJsonBody 为假 → UnmarshalJsonMap(nil, v) → 零值 struct，
        // 于是「只带 URL 里的 id」是一次合法请求（什么都不改）。
        UpdateArticleReq body = (req == null) ? new UpdateArticleReq() : req;
        return updateService.updateArticle(id, body, support.userId(httpRequest));
    }

    /** Go {@code DeleteArticleHandler}。Go 侧 {@code DeleteArticleReq} 零校验，故无 normalize。 */
    @DeleteMapping("/article/{id}")
    public DeleteArticleResp deleteArticle(@PathVariable("id") String id,
                                           HttpServletRequest httpRequest) {
        return deleteService.deleteArticle(
                new DeleteArticleReq(id, support.userId(httpRequest)));
    }

    /**
     * Go {@code GetAuthorArticleHandler}。
     *
     * <p>⚠️ <b>不声明</b> {@code incr_view}：Go 的 {@code GetAuthorArticleLogic}
     * 构造请求时没有 {@code IncrView} 字段（恒 false）。
     * 本端点<b>复用</b> {@code GetArticleReq} 这个 Go struct（它<b>有</b>该字段），
     * 但作者读的 handler 忽略它。让签名里没有它，差异就从类型上消失。
     */
    @GetMapping("/me/article/{id}")
    public GetArticleResp getAuthorArticle(@PathVariable("id") String id,
                                           HttpServletRequest httpRequest) {
        return getAuthorService.getAuthorArticle(id, support.userId(httpRequest));
    }

    // ============== 第二段：公开（无 JWT） ==============

    /**
     * Go {@code GetArticleHandler}（公开读详情）。
     *
     * @param incrView <b>原始</b> query 值，交给 {@code GoFormValues.bool} 按 Go 规则解释
     */
    @GetMapping("/article/{id}")
    public GetArticleResp getArticle(@PathVariable("id") String id,
                                     @RequestParam(name = "incr_view", required = false) String incrView) {
        return getService.getArticle(id, incrView);
    }

    /**
     * Go {@code ListArticlesHandler}（公开列表）。
     *
     * <p>⚠️ 八个参数<b>全部</b>声明成 {@link String} 并交给 {@link ListArticlesReq} 构造器，
     * 因为 Go 的默认值与真值表规则（空值丢弃 / {@code 1,0} / base-10）由那一处统一处理。
     * 这里<b>不</b>用 {@code @RequestParam(defaultValue = "1")} ——
     * 那会让「默认值」这件事散到 4 个注解上，且 {@code desc} 的 {@code "1"} 就变成 400。
     */
    @GetMapping("/articles")
    public ListArticlesResp listArticles(
            @RequestParam(name = "manual_type_tag", required = false) String manualTypeTag,
            @RequestParam(name = "secondary_tag", required = false) String secondaryTag,
            @RequestParam(name = "related_game_id", required = false) String relatedGameId,
            @RequestParam(name = "author_id", required = false) String authorId,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "desc", required = false) String desc) {
        return listService.listArticles(new ListArticlesReq(
                manualTypeTag, secondaryTag, relatedGameId, authorId,
                page, pageSize, sortBy, desc));
    }

    // ============== 第三段：公开 multipart ==============

    /**
     * Go {@code UploadImageHandler} —— <b>本模块唯一不走信封的端点</b>。
     *
     * <p>返回 {@link ResponseEntity} 是<b>刻意</b>的：{@code GlobalResponseAdvice}
     * 对 {@code ResponseEntity} 放行（不包信封），从而复现 Go
     * {@code httpx.OkJsonCtx(ctx, w, resp)} 的裸 {@code {"image_url":…}}。
     * 若改成直接返回 {@link UploadImageResp}，advice 会把它包成
     * {@code {"code":200,…,"data":{…}}} —— 与 Go 不一致。
     *
     * <p>⚠️ {@code required = true}：Go 侧 {@code r.FormFile("image")} 取不到就
     * {@code httpx.ErrorCtx} ⇒ 400 text/plain。Spring 的
     * {@code MissingServletRequestPartException} 被
     * {@code GlobalExceptionHandler#handleParseFailure} 映到同一个 400 text/plain。
     *
     * <p>⚠️ multipart 字段名固定为 {@code "image"}（见 {@code ArticleUploadService.FORM_FIELD}），
     * 这是 L1 契约。
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadImageResp> uploadImage(
            @RequestParam(ArticleUploadService.FORM_FIELD) MultipartFile image) {
        return ResponseEntity.ok(uploadService.uploadImage(image));
    }
}