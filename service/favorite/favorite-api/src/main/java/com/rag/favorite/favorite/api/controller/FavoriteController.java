package com.rag.favorite.favorite.api.controller;

import com.rag.favorite.favorite.api.dto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.api.dto.CreateFavoriteFolderResp;
import com.rag.favorite.favorite.api.dto.CreateFavoriteReq;
import com.rag.favorite.favorite.api.dto.CreateFavoriteResp;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteFolderResp;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteReq;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteResp;
import com.rag.favorite.favorite.api.dto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.api.dto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.api.dto.GetFavoritesByFolderResp;
import com.rag.favorite.favorite.api.dto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.api.dto.UpdateFavoriteFolderNameResp;
import com.rag.favorite.favorite.api.service.FavoriteApiSupport;
import com.rag.favorite.favorite.api.service.FavoriteCreateFolderService;
import com.rag.favorite.favorite.api.service.FavoriteCreateItemService;
import com.rag.favorite.favorite.api.service.FavoriteDeleteFolderService;
import com.rag.favorite.favorite.api.service.FavoriteDeleteItemService;
import com.rag.favorite.favorite.api.service.FavoriteListFoldersService;
import com.rag.favorite.favorite.api.service.FavoriteListItemsService;
import com.rag.favorite.favorite.api.service.FavoriteUpdateFolderNameService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * favorite-api HTTP gateway —— 7 个 REST 端点（与 Go 端 handler 1:1 对应）。
 *
 * <p><b>Go 出处</b>：{@code service/favorite/api/internal/handler/routes.go:15-60}
 * <pre>
 * server.AddRoutes(
 *     rest.WithMiddlewares([]rest.Middleware{serverCtx.CheckBlacklistMiddleware},
 *         []rest.Route{
 *             {Method: POST,   Path: "/folder/create", Handler: favorite.CreateFavoriteFolderHandler(serverCtx)},
 *             {Method: DELETE, Path: "/folder/delete", Handler: favorite.DeleteFavoriteFolderHandler(serverCtx)},
 *             {Method: GET,    Path: "/folder/list",   Handler: favorite.GetFavoriteFoldersHandler(serverCtx)},
 *             {Method: POST,   Path: "/folder/update", Handler: favorite.UpdateFavoriteFolderNameHandler(serverCtx)},
 *             {Method: POST,   Path: "/item/create",   Handler: favorite.CreateFavoriteHandler(serverCtx)},
 *             {Method: DELETE, Path: "/item/delete",   Handler: favorite.DeleteFavoriteHandler(serverCtx)},
 *             {Method: GET,    Path: "/item/list",     Handler: favorite.GetFavoritesByFolderHandler(serverCtx)},
 *         }...),
 *     rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *     rest.WithPrefix("/favorite/v1"))
 * </pre>
 *
 * <p><b>L1 外部契约（method + path 逐条对齐）</b>：
 * <table border="1">
 *   <caption>7 个端点</caption>
 *   <tr><th>Method</th><th>Path</th><th>入参通道</th><th>Service</th></tr>
 *   <tr><td>POST</td><td>/favorite/v1/folder/create</td><td>JSON body</td>
 *       <td>{@code FavoriteCreateFolderService}</td></tr>
 *   <tr><td>DELETE</td><td>/favorite/v1/folder/delete</td><td>JSON body</td>
 *       <td>{@code FavoriteDeleteFolderService}</td></tr>
 *   <tr><td>GET</td><td>/favorite/v1/folder/list</td><td>无</td>
 *       <td>{@code FavoriteListFoldersService}</td></tr>
 *   <tr><td>POST</td><td>/favorite/v1/folder/update</td><td>JSON body</td>
 *       <td>{@code FavoriteUpdateFolderNameService}</td></tr>
 *   <tr><td>POST</td><td>/favorite/v1/item/create</td><td>JSON body</td>
 *       <td>{@code FavoriteCreateItemService}</td></tr>
 *   <tr><td>DELETE</td><td>/favorite/v1/item/delete</td><td>JSON body</td>
 *       <td>{@code FavoriteDeleteItemService}</td></tr>
 *   <tr><td>GET</td><td>/favorite/v1/item/list</td><td><b>query string</b></td>
 *       <td>{@code FavoriteListItemsService}</td></tr>
 * </table>
 *
 * <p><b>⚠️ 只有 {@code GET /item/list} 走 query</b>：Go 的
 * {@code types.GetFavoritesByFolderReq} 的 tag 是 {@code form:"folderId,string"}，
 * 而 go-zero 的 {@code core/mapping} <b>按 tag 名决定通道</b> ——
 * 带 {@code form} tag 的字段只在 form 通道生效、带 {@code json} tag 的字段只在 json 通道生效
 * （{@code core/mapping/unmarshaler.go:569-573} 的 {@code usingDifferentKeys}）。
 * 其余 6 个端点的字段全是 {@code json} tag ⇒ 全部走 JSON body。
 *
 * <p><b>⚠️ DELETE 也带 JSON body</b>：Go 侧 {@code httpx.Parse} 对所有方法都跑
 * {@code ParseJsonBody}，与 HTTP method 无关（只有 Content-Type 与 Content-Length 参与判定）。
 */
@RestController
@RequestMapping("/favorite/v1")
public class FavoriteController {

    private final FavoriteCreateFolderService createFolderService;
    private final FavoriteDeleteFolderService deleteFolderService;
    private final FavoriteListFoldersService listFoldersService;
    private final FavoriteUpdateFolderNameService updateFolderNameService;
    private final FavoriteCreateItemService createItemService;
    private final FavoriteDeleteItemService deleteItemService;
    private final FavoriteListItemsService listItemsService;
    /**
     * Spring 托管的 Jackson 3 mapper —— 用于复刻 Go 对空 struct 请求体的校验
     * （{@link #assertGoEmptyStructBody}）。
     *
     * <p><b>为什么注入而不是 new 一个</b>：mapper 的配置来源（GoStyleCoercion 等
     * {@code JsonMapperBuilderCustomizer}）都在容器里，自己 new 会绕过配置；
     * 而且 mapper 是重量级对象，应当作为单例共享。
     */
    private final ObjectMapper objectMapper;

    public FavoriteController(FavoriteCreateFolderService createFolderService,
                              FavoriteDeleteFolderService deleteFolderService,
                              FavoriteListFoldersService listFoldersService,
                              FavoriteUpdateFolderNameService updateFolderNameService,
                              FavoriteCreateItemService createItemService,
                              FavoriteDeleteItemService deleteItemService,
                              FavoriteListItemsService listItemsService,
                              ObjectMapper objectMapper) {
        this.createFolderService = createFolderService;
        this.deleteFolderService = deleteFolderService;
        this.listFoldersService = listFoldersService;
        this.updateFolderNameService = updateFolderNameService;
        this.createItemService = createItemService;
        this.deleteItemService = deleteItemService;
        this.listItemsService = listItemsService;
        this.objectMapper = objectMapper;
    }

    // ============== 收藏夹 ==============

    @PostMapping("/folder/create")
    public CreateFavoriteFolderResp createFavoriteFolder(@RequestBody(required = false) CreateFavoriteFolderReq req,
                                                          HttpServletRequest httpRequest) {
        return createFolderService.createFavoriteFolder(
                com.rag.users.common.http.GoStyleRequest.normalize(req, CreateFavoriteFolderReq::new), httpRequest);
    }

    @DeleteMapping("/folder/delete")
    public DeleteFavoriteFolderResp deleteFavoriteFolder(@RequestBody(required = false) DeleteFavoriteFolderReq req,
                                                          HttpServletRequest httpRequest) {
        return deleteFolderService.deleteFavoriteFolder(
                com.rag.users.common.http.GoStyleRequest.normalize(req, DeleteFavoriteFolderReq::new), httpRequest);
    }

    @GetMapping("/folder/list")
    public GetFavoriteFoldersResp getFavoriteFolders(HttpServletRequest httpRequest) {
        // ⚠️ 看起来这个端点「不需要入参」，但 Go 仍然会【解析并校验请求体】——
        //    httpx.Parse 无条件跑 ParseJsonBody（rest/httpx/requests.go:65-67，在
        //    `if kind != Array && kind != Slice` 之外），且 httpx.ParseJsonBody 先判
        //    `withJsonBody(r) = ContentLength>0 && Content-Type 含 "application/json"`。
        //    body 非法时 handler 落 ErrorInvalidParam ⇒ HTTP 200 + 5003。
        //    本端点没声明 @RequestBody ⇒ Tomcat 不会去读 body ⇒ 不补这一步就会把
        //    非法 body 的请求从 5003 放过成 200（可观察的 L1 差异）。
        assertGoEmptyStructBody(httpRequest);
        return listFoldersService.getFavoriteFolders(httpRequest);
    }

    @PostMapping("/folder/update")
    public UpdateFavoriteFolderNameResp updateFavoriteFolderName(
            @RequestBody(required = false) UpdateFavoriteFolderNameReq req,
            HttpServletRequest httpRequest) {
        return updateFolderNameService.updateFavoriteFolderName(
                com.rag.users.common.http.GoStyleRequest.normalize(req, UpdateFavoriteFolderNameReq::new),
                httpRequest);
    }

    // ============== 收藏 ==============

    @PostMapping("/item/create")
    public CreateFavoriteResp createFavorite(@RequestBody(required = false) CreateFavoriteReq req,
                                             HttpServletRequest httpRequest) {
        return createItemService.createFavorite(
                com.rag.users.common.http.GoStyleRequest.normalize(req, CreateFavoriteReq::new), httpRequest);
    }

    @DeleteMapping("/item/delete")
    public DeleteFavoriteResp deleteFavorite(@RequestBody(required = false) DeleteFavoriteReq req,
                                             HttpServletRequest httpRequest) {
        return deleteItemService.deleteFavorite(
                com.rag.users.common.http.GoStyleRequest.normalize(req, DeleteFavoriteReq::new), httpRequest);
    }

    /**
     * 查询收藏夹下的收藏 —— <b>form 通道</b>（query string）。
     *
     * <p>Go 出处：{@code types.GetFavoritesByFolderReq} 的 tag 是 {@code form:"folderId,string"}
     * （{@code service/favorite/api/internal/types/types.go:67}）。
     * <p>⚠️ {@code required = false}：Go 侧「参数缺席」走的是
     * {@code field "folderId" is not set}（⇒ 5003），<b>不是</b> 400 ——
     * 缺席与「值非法」在本域落到<b>同一个出口</b>（200 + 5003），
     * 所以这里让 null 正常进来，由 DTO 的必填判定统一处理。
     */
    @GetMapping("/item/list")
    public GetFavoritesByFolderResp getFavoritesByFolder(HttpServletRequest httpRequest) {
        // ⚠️ 必须自己取原始多值参数，不能用 `@RequestParam(name="folderId") String`：
        //    Spring 对重复参数会逗号拼接成 "123,456"（⇒ ParseInt 失败 ⇒ 5003），
        //    而 Go 是「先滤空值再取首值」⇒ 123 ⇒ 200。详见 FavoriteApiSupport#goFormFirstValue。
        GetFavoritesByFolderReq req =
                GetFavoritesByFolderReq.fromForm(FavoriteApiSupport.goFormFirstValue(httpRequest, "folderId"));
        req.assertGoRequiredFields();
        return listItemsService.getFavoritesByFolder(req, httpRequest);
    }

    /**
     * 复刻 Go 对<b>空 struct 请求体</b>的解析 —— 见 {@link #getFavoriteFolders} 的调用点注释。
     *
     * <p><b>Go 出处</b>：{@code go-zero@v1.10.2/rest/httpx/requests.go:117-124} 的 {@code ParseJsonBody}
     * <pre>
     *   func ParseJsonBody(r *http.Request, v any) error {
     *       if withJsonBody(r) {
     *           reader := io.LimitReader(r.Body, maxBodyLen)
     *           return mapping.UnmarshalJsonReader(reader, v)
     *       }
     *       return mapping.UnmarshalJsonMap(nil, v)
     *   }
     * </pre>
     * 配合 {@code go-zero@v1.10.2/rest/httpx/requests.go:153-155} 的
     * {@code withJsonBody = r.ContentLength &gt; 0 &amp;&amp; strings.Contains(CT, "application/json")}。
     *
     * <p><b>真源探针实测（go-zero v1.10.2 + 逐字空 struct）</b>：
     * <pre>
     *   body={}              → 200
     *   body={"a":1}         → 200   （空 struct 忽略未知字段）
     *   body=null            → 5003  （unsupported type on setting field value）
     *   body=123 / 1.5 / true / "str" → 5003（同上）
     *   body=[] / [1,2]      → 5003  （type mismatch）
     *   body={               → 5003  （unexpected EOF）
     *   body={ CT=text/plain } → 200 （withJsonBody 为 false，body 压根不读）
     *   body={ CT=Application/JSON } → 200（strings.Contains 大小写敏感 ⇒ 判 false）
     * </pre>
     * ⇒ 规则收敛成一句：<b>只有「根是 JSON object」才算通过</b>。这与「用 Jackson 反序列化进
     * 一个空 DTO」等价，但只判根节点类型更直白，也避免把 DTO 字段规则卷进来。
     *
     * @throws IllegalArgumentException ⇒ {@code GlobalExceptionHandler#handleIllegalArgument}
     *                                   ⇒ HTTP 200 + {@code {code:5003, msg:"参数错误"}}
     */
    private void assertGoEmptyStructBody(HttpServletRequest httpRequest) {
        // ⚠️ 用 getContentLengthLong()：chunked 传输时返回 -1，与 Go 的 r.ContentLength = -1
        //    一样落到 `> 0` 为 false ⇒ 不读 body。语义一致。
        if (httpRequest.getContentLengthLong() <= 0) {
            return;
        }
        // Go 用 strings.Contains（大小写敏感），不是 MIME 解析 —— 别用 MediaType.parseMediaType。
        String contentType = httpRequest.getHeader("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            return;
        }
        JsonNode root;
        try (InputStream in = httpRequest.getInputStream()) {
            root = objectMapper.readTree(in);
        } catch (JacksonException e) {
            // ⚠️ Jackson 3 把 JacksonException 改成【unchecked】（extends RuntimeException，
            //    不再继承 IOException）—— 写 `catch (IOException)` 接不住，
            //    实测 `{` 会以 UnexpectedEndOfInputException 逃出去落到兜底分支 ⇒ 500。
            //    Go: jsonx.UnmarshalFromReader 的语法错误（unexpected EOF）⇒ Parse 报错 ⇒ 5003（go-zero@v1.10.2/core/jsonx/json.go:63）
            throw new IllegalArgumentException("malformed json body", e);
        } catch (IOException e) {
            // 流本身读不动（连接中断等）
            throw new IllegalArgumentException("unreadable json body", e);
        }
        // null / 标量 / 数组在 Go 侧都落在 "unsupported type on setting field value" 或
        // "type mismatch" ⇒ 同样 5003。只有 object 通过。
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("body is not a json object");
        }
    }
}
