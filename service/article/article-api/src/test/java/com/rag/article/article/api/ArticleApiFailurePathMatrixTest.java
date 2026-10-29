package com.rag.article.article.api;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.client.UserRpcClient;
import com.rag.article.article.api.config.ArticleApiProperties;
import com.rag.article.article.api.support.RealTomcatPort;
import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.CreateArticleResponse;
import com.rag.article.article.proto.DeleteArticleResponse;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.article.article.proto.UpdateArticleResponse;
import com.rag.article.article.proto.UploadFileResponse;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * article-api 的<b>失败路径矩阵</b>（真 Tomcat · 7 端点 × 8 类失败维度）。
 *
 * <h2>为什么要有它（基线缺口 G-13 · P0 #2）</h2>
 * <p>「存在失败路径处理」推不出「参数校验 / 业务规则 / 认证 / 权限 / 下游不可用 各项都有效」，
 * 且失败路径<b>域覆盖不全</b> ⇒ 本类就是 article 域的那份<b>独立失败路径矩阵</b>：
 * <b>7 个端点逐个</b>跑 8 类失败维度，每条断言都落到<b>具体的错误码 / HTTP 状态码</b>。
 *
 * <h2>为什么必须是<b>真 Tomcat</b>（不是 MockMvc）</h2>
 * <p>本模块此前的 8 个测试类<b>全是 {@code MOCK} / {@code RANDOM_PORT}</b>
 * ⇒ <b>真 Tomcat 覆盖率是零</b>。而 MockMvc 有三处系统性地测不到：
 * <ol>
 *   <li><b>Content-Type 的容器归一化</b> —— 真 Tomcat 把 {@code text/plain; charset=utf-8}
 *       归一化成 {@code text/plain;charset=utf-8}（分号后无空格）⇒ MockMvc 里断言
 *       「带空格」的写法<b>在 MockMvc 下会绿、在真容器下会红</b>；</li>
 *   <li><b>Content-Length</b> —— 由容器依据实际写入字节数计算，MockMvc 不参与；</li>
 *   <li><b>body 是否被容器补写</b> —— 404 / 405 这类「body 为 null」的响应，
 *       容器会不会自作主张补空 body 或补 Content-Type，只有真容器说了算。</li>
 * </ol>
 *
 * <h2>🔴 本域的三个<b>决定性不对称</b>（最容易「顺手统一」的地方）</h2>
 * <ol>
 *   <li><b>article-api <u>没有</u>注册 {@code httpx.SetErrorHandler}</b>
 *       （Go {@code service/article/api/article.go} 全文 42 行，一个字都没有）
 *       ⇒ <b>解析层失败 = 400 + text/plain + nosniff</b>，
 *       <b>不是</b> message / like / hot 三域的「200 + {code:5001}」；</li>
 *   <li><b>同一个 gRPC 码在 7 个端点映出 7 种不同的结果</b>：
 *       {@code PermissionDenied} 在 update/delete/get-author 是 <b>1006</b>，
 *       在 create/get/list 是 <b>1015</b>（落进 Go 的 {@code default} 分支），
 *       在 upload 是 <b>403 + text/plain</b>（走 go-zero 的 {@code CodeFromGrpcError}）；</li>
 *   <li><b>article-api 是四个 api 域里唯一用 {@code rest.WithCors()} 的</b>
 *       ⇒ <b>方法不匹配 = 404 空 body</b>（不是 405），
 *       而 hot / follow / message / like 都是 405 + {@code Allow}。</li>
 * </ol>
 *
 * <h2>矩阵的 8 类失败维度（Go 出处逐条）</h2>
 * <table border="1">
 *   <caption>失败维度与 Go 侧判据</caption>
 *   <tr><th>维度</th><th>Go 出处</th><th>可观察形态</th></tr>
 *   <tr>
 *     <td>认证</td>
 *     <td>{@code rest/handler/authhandler.go:106}（401 空 body）·
 *         {@code logic/article/helper.go:17-39}（claim 取不到 ⇒ <b>1005</b>）</td>
 *     <td>401 + 空 body（4 个受保护端点）／<b>200 + {code:1005}</b>（claim 缺失）</td>
 *   </tr>
 *   <tr>
 *     <td>权限</td>
 *     <td>{@code update_article_logic.go:65-67} · {@code delete_article_logic.go:64-66} ·
 *         {@code get_author_article_logic.go:60-62}（归属不符 ⇒ 1006）</td>
 *     <td>200 + {code:1006}，且<b>写路径的 RPC 完全不被调用</b></td>
 *   </tr>
 *   <tr>
 *     <td>参数校验</td>
 *     <td>{@code types/types.go:26-33}（title / content <b>无 optional ⇒ 必填</b>）·
 *         {@code types/types.go:74-83}（update 七字段<b>全 optional</b>）</td>
 *     <td>400 + text/plain + nosniff（缺席 / 类型不符 / 缺 multipart 部件）</td>
 *   </tr>
 *   <tr>
 *     <td>业务规则</td>
 *     <td>{@code create_article_logic.go:59-74}（AlreadyExists ⇒ 1001；
 *         FailedPrecondition ⇒ 1004 <b>带 data</b>；DeadlineExceeded ⇒ 1003 <b>带 data</b>）</td>
 *     <td>200 + {code:1001|1003|1004}；1003 / 1004 的 data 里<b>有 article_id</b></td>
 *   </tr>
 *   <tr>
 *     <td>下游不可用</td>
 *     <td>各 logic 的 {@code default} 分支 ⇒ {@code CodeServerBusy}（1015）；
 *         upload 走 {@code rest/internal/errcode/grpc.go:35-36}（Unavailable ⇒ 503）</td>
 *     <td>1015（6 个端点）／<b>503 + text/plain</b>（upload）</td>
 *   </tr>
 *   <tr>
 *     <td>下游内部错</td>
 *     <td>各 logic 的 {@code codes.Internal} 分支 ⇒ 5001</td>
 *     <td>5001（6 个端点）／<b>500 + text/plain</b>（upload）</td>
 *   </tr>
 *   <tr>
 *     <td>边界值</td>
 *     <td>{@code types/types.go:61-64}（page / page_size 有 default ⇒ <b>api 层不夹取</b>）·
 *         {@code core/mapping/utils.go:104-113}（bool 只认 1/0/true/false）</td>
 *     <td>空串标题<b>合法</b>；page=0 / page=-1 <b>原样透传</b>（不夹取）</td>
 *   </tr>
 *   <tr>
 *     <td>路由层</td>
 *     <td>{@code rest/router/patrouter.go:71-82} + {@code rest/internal/cors/handlers.go:44-48}</td>
 *     <td>404 两种形状（未注册路径 vs 方法不匹配）</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ 证据强度</h2>
 * <p>本类的期望值<b>全部抄自 Go 源码</b>（每条断言在 {@link Endpoint} 里带 Go {@code 文件:行号}）
 * ⇒ 这是 <b>RAG 侧的自动化断言</b>；本类<b>不</b>声称双侧活体对拍
 * ⇒ 证据强度按基线 §1.1 的 <b>R3</b>（有 Go 出处 + 有 RAG 侧自动化断言 + 变异必红）计，
 * <b>不</b>按 R4（双侧活体）计。
 */
@SpringBootTest(
        classes = ArticleApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ 必须关掉 Nacos 自动注册 —— DEFINED_PORT 真的会启动 webServer ⇒
                //   Spring Cloud Alibaba 会在 web server 启动阶段注册 Nacos，
                //   而 127.0.0.1:1 连不上 ⇒ 注册抛异常 ⇒ **整个上下文起不来**。
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ 让判据只取决于「本类要验的那条路径」：CPU 自适应降载命中时
                //   GoStyleCpuSheddingFilter 走 response.setStatus(503) 并**直接 return**
                //   ⇒ 裸 503、无 body、无信封 ⇒ 把本类要验的判据抢先盖掉（机器一忙就红在错的地方）。
                //   这是**测试专用**覆盖，不改生产默认值（默认 true 对齐 Go rest/config.go:22）。
                "rag.protection.cpu-shedding.enabled=false"
        })
class ArticleApiFailurePathMatrixTest {

    // ==================================================================
    // 模型
    // ==================================================================

    /** 端点对应的下游 rpc 方法 —— 用于打桩与「RPC 是否被调用」的判定。 */
    enum Rpc {
        CREATE, GET_PUBLIC, GET_AUTHOR, UPDATE, DELETE, LIST, UPLOAD
    }

    /**
     * 7 个端点 —— 与 Go {@code service/article/api/internal/handler/routes.go:19-73} 逐条对应。
     *
     * @param name    端点名（测试输出用）
     * @param method  HTTP 方法
     * @param path    完整路径（Go 的 {@code rest.WithPrefix("/v1")} + 路由 path）
     * @param jwt     是否在 Go 的<b>第一段</b>（带 {@code rest.WithJwt}）里
     * @param rpc     下游方法
     * @param goLogic Go 侧 logic 的 {@code 文件:行号}（本类的期望值出处）
     */
    record Endpoint(String name, String method, String path, boolean jwt, Rpc rpc, String goLogic) {
        @Override
        public String toString() {
            return name + " ｜ " + method + " " + path + " ｜ Go: " + goLogic;
        }
    }

    /**
     * 7 个端点的矩阵行 —— <b>顺序与 Go routes.go 一致</b>（第一段 4 个 + 第二段 2 个 + 第三段 1 个）。
     */
    static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("创建文章", "POST", "/v1/article", true, Rpc.CREATE,
                    "logic/article/create_article_logic.go:38-80"),
            new Endpoint("更新文章", "PUT", "/v1/article/1", true, Rpc.UPDATE,
                    "logic/article/update_article_logic.go:36-111"),
            new Endpoint("删除文章", "DELETE", "/v1/article/1", true, Rpc.DELETE,
                    "logic/article/delete_article_logic.go:35-90"),
            new Endpoint("作者读单篇", "GET", "/v1/me/article/1", true, Rpc.GET_AUTHOR,
                    "logic/article/get_author_article_logic.go:33-74"),
            new Endpoint("公开读单篇", "GET", "/v1/article/1", false, Rpc.GET_PUBLIC,
                    "logic/article/get_article_logic.go:35-80"),
            new Endpoint("公开列表", "GET", "/v1/articles", false, Rpc.LIST,
                    "logic/article/list_articles_logic.go:36-96"),
            new Endpoint("上传图片", "POST", "/v1/upload", false, Rpc.UPLOAD,
                    "logic/article/upload_image_logic.go:34-53"));

    static Stream<Endpoint> endpoints() {
        return ENDPOINTS.stream();
    }

    /** Go 第一段（带 {@code rest.WithJwt}）的 4 个端点。 */
    static Stream<Endpoint> protectedEndpoints() {
        return ENDPOINTS.stream().filter(Endpoint::jwt);
    }

    /** Go 第二段 + 第三段（<b>无</b> {@code WithJwt}）的 3 个端点。 */
    static Stream<Endpoint> publicEndpoints() {
        return ENDPOINTS.stream().filter(ep -> !ep.jwt());
    }

    // ==================================================================
    // 常量与装置
    // ==================================================================

    /**
     * 🔴 <b>密钥从容器里读，不写死在本类</b> ——
     * {@code ArticleApiProperties#getAuth()#getAccessSecret()} 读的是<b>真 yaml</b>
     * （{@code src/main/resources/application.yml} 的 {@code article-api.auth.access-secret}）。
     * <p>⚠️ 为什么不能写死常量：2026-10-29 上一版把 yaml 的 {@code auth:} 整层删掉了
     * ⇒ 绑定路径变成 {@code article-api.access-secret} ⇒ {@code auth.accessSecret} 恒 null
     * ⇒ <b>组件永远起不来</b>；而当时的守门测试用 {@code withPropertyValues(...)} 喂值
     * ⇒ <b>它从不读那份 yaml</b> ⇒ 测试全绿而组件起不来（跑绿 ≠ 对）。
     * ⇒ 本类从容器取真实生效值来签 token ⇒ yaml 一旦漂移，本类**当场红**。
     */
    @Autowired
    private ArticleApiProperties properties;

    private static final long EXPIRE_SEC = 3600L;
    /** 当前登录用户。 */
    private static final long CLAIM_UID = 31337L;
    /** 别人的用户 id（用于「归属不符」）。 */
    private static final long OTHER_UID = 4242L;

    // Go service/article/common/errmsg/errmsg.go:4-18 的常量
    private static final int CODE_SUCCESS = 200;
    private static final int CODE_ARTICLE_EXIST = 1001;
    private static final int CODE_ARTICLE_NONE = 1002;
    private static final int CODE_ARTICLE_PUBLISHING = 1003;
    private static final int CODE_ARTICLE_PUBLISH_FAILED = 1004;
    private static final int CODE_UNAUTHORIZED = 1005;
    private static final int CODE_ARTICLE_FORBIDDEN = 1006;
    private static final int CODE_SERVER_BUSY = 1015;
    private static final int CODE_SERVER_COMMON = 5001;

    @LocalServerPort
    private int port;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    @MockitoBean
    private UserRpcClient userRpcClient;

    private ArticleServiceGrpc.ArticleServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(ArticleServiceGrpc.ArticleServiceBlockingStub.class);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        // 作者昵称解析：**永不冒泡**（Go helper.go:71-103 的四条失败分支全部回落）
        // ⇒ 这里让 user-rpc 一律 NOT_FOUND ⇒ 昵称回落成「用户 <id>」，但请求本身成功。
        UserServiceGrpc.UserServiceBlockingStub userStub =
                Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        Mockito.when(userStub.getUser(any()))
                .thenThrow(Status.NOT_FOUND.asRuntimeException());
        Mockito.when(userRpcClient.userRpc()).thenReturn(userStub);
    }

    /**
     * <b>本类必须绑到本段专用端口段</b>（而不是 Spring 的 {@code RANDOM_PORT}）。
     *
     * <p>⚠️ {@code RANDOM_PORT} 挑的是 OS <b>临时端口段</b>（macOS：49152–65535），
     * 而<b>别的会话的 Docker 容器正好在那一段发布端口</b>
     * ⇒ 客户端可能把请求打到别人的服务 ⇒ <b>零字节即关</b>
     * ⇒ <b>看着像被测代码炸了，其实压根没打到</b>，而且<b>所有用例照样绿</b>。
     */
    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    // ==================================================================
    // 0. 端口段自检（本类不许退化成 RANDOM_PORT）
    // ==================================================================

    @Test
    @DisplayName("真容器必须绑到本域专用端口段 18141-18145（RANDOM_PORT 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段）")
    void bindsToDedicatedPortSegment() {
        assertTrue(port >= RealTomcatPort.FIRST && port <= RealTomcatPort.LAST,
                "Tomcat 实际绑定的端口必须在 " + RealTomcatPort.FIRST + "-" + RealTomcatPort.LAST
                        + " 内，实际=" + port
                        + "（若落在 49152-65535，说明 webEnvironment 被改回 RANDOM_PORT 了）");
    }

    // ==================================================================
    // 1. 认证维度
    // ==================================================================

    @ParameterizedTest(name = "[认证·无 token] {0}")
    @MethodSource("protectedEndpoints")
    @DisplayName("认证·无 Authorization ⇒ 401 + **空 body** + **无 Content-Type**（Go authhandler.go:106 unauthorized()）")
    void missingTokenIs401WithEmptyBody(Endpoint ep) throws Exception {
        HttpResponse<String> r = send(ep, null, "application/json", happyBody(ep));

        assertEquals(401, r.statusCode(), "Go: 无 token ⇒ 401");
        assertEquals("", r.body(), "Go 的 unauthorized() 只 WriteHeader、**不写任何字节** ⇒ body 必须为空");
        assertNull(r.headers().firstValue("Content-Type").orElse(null),
                "Go 的 unauthorized() 不设 Content-Type ⇒ 真容器也不应补。"
                        + "若这里非空，说明容器给空 body 补了头（那与 Go 不同）");
        verifyNoRpcCall(ep);
    }

    @ParameterizedTest(name = "[认证·签名无效] {0}")
    @MethodSource("protectedEndpoints")
    @DisplayName("认证·签名无效的 token ⇒ **恒** 401 + 空 body（Go authhandler.go:100-106 不区分失败原因）")
    void invalidSignatureIs401WithEmptyBody(Endpoint ep) throws Exception {
        // ⚠️ 用**另一个 secret** 签 ⇒ 验签必失败（Go 侧同样只走到 unauthorized()）
        String foreign = JwtUtil.getToken("00000000-0000-0000-0000-000000000000",
                System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);

        HttpResponse<String> r = send(ep, "Bearer " + foreign, "application/json", happyBody(ep));

        assertEquals(401, r.statusCode(), "Go: 验签失败 ⇒ 401");
        assertEquals("", r.body(), "Go 的 unauthorized() 不写 body");
        assertNull(r.headers().firstValue("Content-Type").orElse(null),
                "Go 的 unauthorized() 不设 Content-Type");
        verifyNoRpcCall(ep);
    }

    @ParameterizedTest(name = "[认证·claim 缺失] {0}")
    @MethodSource("protectedEndpoints")
    @DisplayName("认证·token 无 userId claim ⇒ **HTTP 200** + {code:1005,msg:\"请先登录\"}（不是 401；Go helper.go:17-39）")
    void tokenWithoutUserIdClaimIs200With1005(Endpoint ep) throws Exception {
        HttpResponse<String> r = send(ep, "Bearer " + tokenWithoutUserIdClaim(),
                "application/json", happyBody(ep));

        assertEquals(200, r.statusCode(),
                "Go: claim 取不到属于**业务未鉴权**（1005）⇒ HTTP 恒 200。"
                        + "若这里变成 401，就是把 logic 层的判定提到了鉴权层");
        assertErrorEnvelope(r, CODE_UNAUTHORIZED, "请先登录");
        verifyNoRpcCall(ep);
    }

    @ParameterizedTest(name = "[认证·公开端点不被拦] {0}")
    @MethodSource("publicEndpoints")
    @DisplayName("认证·公开端点（public get / list / upload）**无 token 也必须进业务**，不得 401（Go routes.go 第二、三段无 WithJwt）")
    void publicEndpointsAreNotBlockedByMissingToken(Endpoint ep) throws Exception {
        stubHappyPath(ep);

        HttpResponse<String> r = send(ep, null, "application/json", happyBody(ep));

        assertTrue(r.statusCode() != 401,
                "Go: 第二段（public get / list）与第三段（upload）**没有** rest.WithJwt ⇒ 不得 401，实际=" + r.statusCode());
        assertEquals(200, r.statusCode(), "Go: 公开端点无 token ⇒ 正常进业务 ⇒ HTTP 200");
    }

    // ==================================================================
    // 2. 权限维度
    // ==================================================================

    @ParameterizedTest(name = "[权限·归属不符] {0}")
    @MethodSource("endpoints")
    @DisplayName("权限·文章作者 ≠ 当前用户 ⇒ 200+{code:1006} 且**写路径 RPC 不被调用**（Go update:65 / delete:64 / get-author:60）")
    void ownershipMismatchMatrix(Endpoint ep) throws Exception {
        if (!hasOwnershipCheck(ep)) {
            // create / public get / list / upload 在 Go 侧**没有**归属校验分支
            // ⇒ 本端点不参与这条断言（不是「漏测」，是 Go 侧就没有这条规则）。
            return;
        }
        stubHappyPath(ep);
        // 让读路径返回一篇**别人写的**文章
        Mockito.reset(stub);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        when(stub.getArticle(any())).thenReturn(publishedArticle("999"));

        HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", happyBody(ep));

        assertEquals(200, r.statusCode(), "Go: 归属校验失败也是 HTTP 200 + 业务信封");
        assertErrorEnvelope(r, CODE_ARTICLE_FORBIDDEN, "只能编辑或删除自己的文章");
        // ⚠️ 关键：归属校验在**写路径之前**⇒ 写操作的 RPC 完全不该被调用
        verify(stub, never()).updateArticle(any());
        verify(stub, never()).deleteArticle(any());
    }

    @ParameterizedTest(name = "[权限·rpc PermissionDenied] {0}")
    @MethodSource("endpoints")
    @DisplayName("权限·rpc 返 PermissionDenied ⇒ **7 个端点 7 种结果**（1006 / 1015 / 403 text/plain）")
    void rpcPermissionDeniedMatrix(Endpoint ep) throws Exception {
        stubFirstCallPermissionDenied(ep);

        HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", happyBody(ep));

        switch (ep.rpc()) {
            case UPDATE, DELETE, GET_AUTHOR -> {
                // Go: update:54-55 / delete:55-56 / get-author:45-46 有 PermissionDenied 分支 ⇒ 1006
                assertEquals(200, r.statusCode());
                assertErrorEnvelope(r, CODE_ARTICLE_FORBIDDEN, "只能编辑或删除自己的文章");
            }
            case CREATE, GET_PUBLIC, LIST -> {
                // Go: create:59-74 / get:44-51 / list:51-56 **没有** PermissionDenied 分支
                //      ⇒ 落进 default ⇒ CodeServerBusy(1015)
                assertEquals(200, r.statusCode());
                assertErrorEnvelope(r, CODE_SERVER_BUSY, "服务繁忙");
            }
            case UPLOAD -> {
                // Go: upload handler 走 httpx.ErrorCtx ⇒ go-zero errcode.CodeFromGrpcError
                //      ⇒ PermissionDenied ⇒ http.StatusForbidden(403)
                assertEquals(403, r.statusCode(),
                        "Go: rest/internal/errcode/grpc.go:21-22 ⇒ PermissionDenied ⇒ 403");
                assertTextPlainWithNosniff(r);
                assertTrue(r.body().startsWith("rpc error: code = PermissionDenied desc = "),
                        "Go 的 http.Error 写的是 err.Error() ⇒ grpc-go 的 \"rpc error: code = X desc = Y\"，实际=" + r.body());
            }
        }
    }

    // ==================================================================
    // 3. 参数校验维度
    // ==================================================================

    @ParameterizedTest(name = "[参数校验·必填缺席] {0}")
    @MethodSource("endpoints")
    @DisplayName("参数校验·必填字段缺席 ⇒ **400 + text/plain + nosniff**（article-api 无 error handler ⇒ 不是 200+5001）")
    void missingRequiredFieldMatrix(Endpoint ep) throws Exception {
        switch (ep.rpc()) {
            case CREATE -> {
                // Go: types.go:26-33 ⇒ title / content 无 ,optional ⇒ 必填
                HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", "{}");
                assertEquals(400, r.statusCode(),
                        "Go: httpx.Parse 失败 ⇒ httpx.ErrorCtx ⇒ 400（article-api **没有** SetErrorHandler）");
                assertTextPlainWithNosniff(r);
                assertTrue(r.body().contains("field \"title\" is not set"),
                        "Go: core/mapping/unmarshaler.go:1199 newInitError ⇒ field %q is not set，实际=" + r.body());
                verifyNoRpcCall(ep);
            }
            case UPDATE -> {
                // Go: types.go:74-83 ⇒ update 的七个 JSON 字段**全部** ,optional
                // ⇒ 空 body 是**合法请求**（什么都不改）⇒ 不得 400
                stubHappyPath(ep);
                HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", "{}");
                assertEquals(200, r.statusCode(),
                        "Go: update 的七字段全 optional ⇒ 空 body 合法，不得 400");
                assertSuccessEnvelope(r);
            }
            case GET_PUBLIC -> {
                // Go: types.go:49 IncrView bool `form:"incr_view,optional"`
                // ⇒ 值必须是 1/0/true/false；其它值 ⇒ type mismatch ⇒ 400
                HttpResponse<String> r = send(ep, null, null, null, "?incr_view=maybe");
                assertEquals(400, r.statusCode(),
                        "Go: core/mapping/utils.go:104-113 convertTypeFromString ⇒ bool 类型不符 ⇒ 400");
                assertTextPlainWithNosniff(r);
                assertTrue(r.body().contains("type mismatch for field \"incr_view\""),
                        "Go: mapping/unmarshaler.go:1202 newTypeMismatchError ⇒ type mismatch for field %q，实际=" + r.body());
            }
            case LIST -> {
                // Go: types.go:61 Page int32 `form:"page,default=1"`
                // ⇒ strconv.ParseInt("abc",10,32) 失败 ⇒ 400
                HttpResponse<String> r = send(ep, null, null, null, "?page=abc");
                assertEquals(400, r.statusCode(),
                        "Go: strconv.ParseInt 失败 ⇒ 400");
                assertTextPlainWithNosniff(r);
                verifyNoRpcCall(ep);
            }
            case UPLOAD -> {
                // Go: upload_image_handler.go:16-20 ⇒ r.FormFile("image") 取不到 ⇒ httpx.ErrorCtx ⇒ 400
                HttpResponse<String> r = sendMultipart(ep, "file", "a.png", new byte[]{1, 2, 3});
                assertEquals(400, r.statusCode(),
                        "Go: multipart 字段名必须是 \"image\"；用别的名字 ⇒ FormFile 取不到 ⇒ 400");
                assertTextPlainWithNosniff(r);
                verifyNoRpcCall(ep);
            }
            case DELETE, GET_AUTHOR -> {
                // Go: types.go:39-45 DeleteArticleReq 只有 path id；GetArticleReq 的 IncrView
                // 在**作者读**路径上不声明（Go get_author_article_logic.go:38-40 只取 ArticleId）
                // ⇒ 这两个端点**没有** JSON / form 参数 ⇒ 无参数校验维度
                stubHappyPath(ep);
                HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", "{}");
                assertEquals(200, r.statusCode(), "Go: 这两个端点无入参校验 ⇒ 不得 400");
            }
        }
    }

    @ParameterizedTest(name = "[参数校验·显式 null] {0}")
    @MethodSource("endpoints")
    @DisplayName("参数校验·必填字段**显式 null** ⇒ 400 + 文案 \"mustn't be nil\"（与「缺席」是两条不同的失败）")
    void explicitNullRequiredFieldMatrix(Endpoint ep) throws Exception {
        if (ep.rpc() != Rpc.CREATE) {
            // 只有 create 有 JSON 必填字段；其余端点的必填语义在别的维度里
            return;
        }
        HttpResponse<String> r = send(ep, bearer(userToken()), "application/json",
                "{\"title\":null,\"content\":\"c\"}");

        assertEquals(400, r.statusCode());
        assertTextPlainWithNosniff(r);
        assertTrue(r.body().contains("field \"title\" mustn't be nil"),
                "Go: core/mapping/unmarshaler.go:856-862 ⇒ field %q mustn't be nil，实际=" + r.body());
    }

    // ==================================================================
    // 4. 业务规则维度（not-found / 重复冲突）
    // ==================================================================

    @ParameterizedTest(name = "[业务规则·not found] {0}")
    @MethodSource("endpoints")
    @DisplayName("业务规则·rpc 返 NotFound ⇒ 1002 / 1015 / 404 text/plain（**按端点各不相同**）")
    void rpcNotFoundMatrix(Endpoint ep) throws Exception {
        stubFirstCallNotFound(ep);

        HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", happyBody(ep));

        switch (ep.rpc()) {
            case UPDATE, DELETE, GET_AUTHOR, GET_PUBLIC -> {
                // Go: update:52-53 / delete:52-53 / get-author:44-45 / get:45-46 ⇒ ErrorArticleNone(1002)
                assertEquals(200, r.statusCode());
                assertErrorEnvelope(r, CODE_ARTICLE_NONE, "文章不存在");
            }
            case CREATE -> {
                // Go: create:59-74 **没有** NotFound 分支 ⇒ default ⇒ 1015
                assertEquals(200, r.statusCode());
                assertErrorEnvelope(r, CODE_SERVER_BUSY, "服务繁忙");
            }
            case LIST -> {
                // Go: list:51-56 **只有** Internal 分支 ⇒ NotFound 落 default ⇒ 1015
                assertEquals(200, r.statusCode());
                assertErrorEnvelope(r, CODE_SERVER_BUSY, "服务繁忙");
            }
            case UPLOAD -> {
                // Go: errcode/grpc.go:23-24 ⇒ NotFound ⇒ http.StatusNotFound(404)
                assertEquals(404, r.statusCode(),
                        "Go: rest/internal/errcode/grpc.go:23-24 ⇒ NotFound ⇒ 404");
                assertTextPlainWithNosniff(r);
            }
        }
    }

    @ParameterizedTest(name = "[业务规则·重复/冲突] {0}")
    @MethodSource("endpoints")
    @DisplayName("业务规则·create 撞车 ⇒ 1001（data=null）/ 1004（带 article_id）/ 1003（带 article_id）")
    void createConflictMatrix(Endpoint ep) throws Exception {
        if (ep.rpc() != Rpc.CREATE) {
            // Go 侧只有 create 有「重复 / 冲突」这一族业务码
            // （AlreadyExists / FailedPrecondition / DeadlineExceeded）
            return;
        }

        // ① AlreadyExists ⇒ 1001，data 为 null
        Mockito.reset(stub);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        when(stub.createArticle(any())).thenThrow(
                Status.ALREADY_EXISTS.withDescription("article already exists").asRuntimeException());
        HttpResponse<String> exist = send(ep, bearer(userToken()), "application/json", happyBody(ep));
        assertEquals(200, exist.statusCode());
        assertErrorEnvelope(exist, CODE_ARTICLE_EXIST, "文章已存在");

        // ② FailedPrecondition ⇒ 1004，**data 里带 article_id**（从 status message 最后一个冒号后取）
        Mockito.reset(stub);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        when(stub.createArticle(any())).thenThrow(Status.FAILED_PRECONDITION
                .withDescription("publish failed: art-9001").asRuntimeException());
        HttpResponse<String> failed = send(ep, bearer(userToken()), "application/json", happyBody(ep));
        assertEquals(200, failed.statusCode());
        assertErrorEnvelope(failed, CODE_ARTICLE_PUBLISH_FAILED, "文章发布失败");
        assertTrue(failed.body().contains("\"article_id\":\"art-9001\""),
                "Go: create_article_logic.go:62-65 返回 &types.CreateArticleResp{ArticleId: articleID} ⇒ data 非 null，实际=" + failed.body());

        // ③ DeadlineExceeded ⇒ 1003，**data 里带 article_id**
        Mockito.reset(stub);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        when(stub.createArticle(any())).thenThrow(Status.DEADLINE_EXCEEDED
                .withDescription("still publishing: art-9002").asRuntimeException());
        HttpResponse<String> publishing = send(ep, bearer(userToken()), "application/json", happyBody(ep));
        assertEquals(200, publishing.statusCode());
        assertErrorEnvelope(publishing, CODE_ARTICLE_PUBLISHING, "文章处理中，请稍后查看");
        assertTrue(publishing.body().contains("\"article_id\":\"art-9002\""),
                "Go: create_article_logic.go:66-69 ⇒ data 里带 article_id，实际=" + publishing.body());
    }

    @Test
    @DisplayName("业务规则·public 读到的文章**未发布** ⇒ 200+{code:1002}（Go get_article_logic.go:54-56 的安全闸门）")
    void nonPublishedArticleIsAlso1002() throws Exception {
        // Go: get_article_logic.go:54-56
        //   `if res == nil || res.Article == nil || res.Article.Status != PUBLISHED { return nil, 1002 }`
        // ⚠️ 选 1002（不存在）而不是「无权限」：对公开接口返回 403 会泄露「这个 id 确实存在」。
        Mockito.reset(stub);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        when(stub.getArticle(any())).thenReturn(GetArticleResponse.newBuilder()
                .setArticle(Article.newBuilder()
                        .setId("art-1").setTitle("draft").setAuthorId(String.valueOf(CLAIM_UID))
                        .setStatus(ArticleStatus.DRAFT))
                .build());

        HttpResponse<String> r = send(ENDPOINTS.get(4), bearer(userToken()), "application/json", "{}");

        assertEquals(200, r.statusCode());
        assertErrorEnvelope(r, CODE_ARTICLE_NONE, "文章不存在");
    }

    // ==================================================================
    // 5. 下游不可用 / 下游内部错
    // ==================================================================

    @ParameterizedTest(name = "[下游·Unavailable] {0}")
    @MethodSource("endpoints")
    @DisplayName("下游·rpc UNAVAILABLE ⇒ 1015（6 端点）/ **503 + text/plain**（upload）")
    void rpcUnavailableMatrix(Endpoint ep) throws Exception {
        stubFirstCallFailure(ep, Status.UNAVAILABLE.withDescription("transport is closing"));

        HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", happyBody(ep));

        if (ep.rpc() == Rpc.UPLOAD) {
            assertEquals(503, r.statusCode(),
                    "Go: rest/internal/errcode/grpc.go:35-36 ⇒ Unavailable ⇒ http.StatusServiceUnavailable");
            assertTextPlainWithNosniff(r);
            assertTrue(r.body().startsWith("rpc error: code = Unavailable desc = "),
                    "Go 的 http.Error 写 err.Error() ⇒ grpc-go 的 \"rpc error: code = X desc = Y\"，实际=" + r.body());
        } else {
            assertEquals(200, r.statusCode());
            assertErrorEnvelope(r, CODE_SERVER_BUSY, "服务繁忙");
        }
    }

    @ParameterizedTest(name = "[下游·Internal] {0}")
    @MethodSource("endpoints")
    @DisplayName("下游·rpc INTERNAL ⇒ 5001（6 端点）/ **500 + text/plain**（upload）")
    void rpcInternalMatrix(Endpoint ep) throws Exception {
        stubFirstCallFailure(ep, Status.INTERNAL.withDescription("boom"));

        HttpResponse<String> r = send(ep, bearer(userToken()), "application/json", happyBody(ep));

        if (ep.rpc() == Rpc.UPLOAD) {
            assertEquals(500, r.statusCode(),
                    "Go: rest/internal/errcode/grpc.go:31-32 ⇒ Internal ⇒ http.StatusInternalServerError");
            assertTextPlainWithNosniff(r);
        } else {
            assertEquals(200, r.statusCode());
            assertErrorEnvelope(r, CODE_SERVER_COMMON, "系统内部错误");
        }
    }

    // ==================================================================
    // 6. 边界值维度
    // ==================================================================

    @ParameterizedTest(name = "[边界·空串标题合法] {0}")
    @MethodSource("endpoints")
    @DisplayName("边界·create 的 title=\"\" ⇒ **合法**（Go 只判「是否缺席」，不判空串）⇒ 不得 400")
    void emptyStringTitleIsAccepted(Endpoint ep) throws Exception {
        if (ep.rpc() != Rpc.CREATE) {
            return;
        }
        stubHappyPath(ep);

        HttpResponse<String> r = send(ep, bearer(userToken()), "application/json",
                "{\"title\":\"\",\"content\":\"c\"}");

        assertEquals(200, r.statusCode(),
                "Go: core/mapping processFieldPrimitive 对 String 只判 null 不判空串 ⇒ 空串标题合法");
        assertSuccessEnvelope(r);
    }

    @ParameterizedTest(name = "[边界·page 不夹取] {0}")
    @MethodSource("endpoints")
    @DisplayName("边界·list 的 page/page_size **api 层不夹取**（page=0 / page=-1 原样透传给 rpc）")
    void listPageIsNotClampedAtApiLayer(Endpoint ep) throws Exception {
        if (ep.rpc() != Rpc.LIST) {
            return;
        }
        stubHappyPath(ep);

        HttpResponse<String> r = send(ep, null, null, null, "?page=0&page_size=0");

        assertEquals(200, r.statusCode());
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.rag.article.article.proto.ListArticlesRequest.class);
        verify(stub).listArticles(captor.capture());
        assertEquals(0, captor.getValue().getPage(),
                "Go: types.go:61 page 有 default=1，但**给了值就按值走** ⇒ api 层不得夹取成 1");
        assertEquals(0, captor.getValue().getPageSize(),
                "Go: types.go:62 page_size 同理");
    }

    @ParameterizedTest(name = "[边界·空值取默认] {0}")
    @MethodSource("endpoints")
    @DisplayName("边界·list 的 page=（空值）⇒ **取默认 1**（Go util.go:44-45 空值先被丢弃）")
    void listEmptyPageFallsBackToDefault(Endpoint ep) throws Exception {
        if (ep.rpc() != Rpc.LIST) {
            return;
        }
        stubHappyPath(ep);

        HttpResponse<String> r = send(ep, null, null, null, "?page=&page_size=");

        assertEquals(200, r.statusCode());
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.rag.article.article.proto.ListArticlesRequest.class);
        verify(stub).listArticles(captor.capture());
        assertEquals(1, captor.getValue().getPage(),
                "Go: rest/httpx/util.go:44-45 空值先被整个丢掉 ⇒ 该 key 不进 map ⇒ default=1");
        assertEquals(20, captor.getValue().getPageSize(),
                "Go: types.go:62 page_size default=20");
    }

    @ParameterizedTest(name = "[边界·incr_view 真值表] {0}")
    @MethodSource("endpoints")
    @DisplayName("边界·incr_view 只认 1/0/true/false（Go utils.go:106-113）⇒ 1 ⇒ true、0 ⇒ false")
    void incrViewTruthTable(Endpoint ep) throws Exception {
        if (ep.rpc() != Rpc.GET_PUBLIC) {
            return;
        }
        stubHappyPath(ep);

        send(ep, null, null, null, "?incr_view=1");
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.rag.article.article.proto.GetArticleRequest.class);
        verify(stub).getArticle(captor.capture());
        assertTrue(captor.getValue().getIncrView(), "Go: utils.go:107 \"1\" ⇒ true");
        assertTrue(captor.getValue().getPublicOnly(), "Go: get_article_logic.go:39 PublicOnly 恒 true");

        Mockito.reset(stub);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        when(stub.getArticle(any())).thenReturn(publishedArticle(String.valueOf(CLAIM_UID)));
        send(ep, null, null, null, "?incr_view=0");
        verify(stub).getArticle(captor.capture());
        assertTrue(!captor.getValue().getIncrView(), "Go: utils.go:110 \"0\" ⇒ false");
    }

    // ==================================================================
    // 7. 路由层维度（404 的两种形状）
    // ==================================================================

    @Test
    @DisplayName("路由·未注册路径 ⇒ 404 + \"404 page not found\\n\" + text/plain + nosniff（Go patrouter.go:73 / http.NotFound）")
    void unregisteredPathIsGoNotFound() throws Exception {
        HttpResponse<String> r = get("/v1/no-such-endpoint");

        assertEquals(404, r.statusCode());
        assertEquals("404 page not found\n", r.body(),
                "Go 的 http.Error + fmt.Fprintln ⇒ body 带尾随换行");
        assertTextPlainWithNosniff(r);
    }

    @Test
    @DisplayName("路由·**方法**不匹配 ⇒ **404 空 body + 无 Content-Type**（不是 405！Go cors.NotAllowedHandler）")
    void methodNotAllowedIs404EmptyBodyBecauseOfCors() throws Exception {
        // ⚠️ article-api 是四个 api 域里**唯一**用 rest.WithCors() 的
        //    （Go service/article/api/article.go:33）⇒ notAllowed 被 cors.NotAllowedHandler 接管
        //    ⇒ go-zero rest/internal/cors/handlers.go:44-48 ⇒ WriteHeader(404)，**不写 body**
        // ⇒ hot / follow / message / like 那边是 405 + Allow —— **不要照抄**
        HttpResponse<String> r = post("/v1/article/1", null, "application/json", "{}");

        assertEquals(404, r.statusCode(),
                "Go: cors.NotAllowedHandler ⇒ 404（不是 405）");
        assertEquals("", r.body(), "Go 的 cors.NotAllowedHandler 只 WriteHeader ⇒ body 为空");
        assertNull(r.headers().firstValue("Content-Type").orElse(null),
                "body 为空 ⇒ 容器不补 Content-Type（Go 侧也没有）");
        assertNull(r.headers().firstValue("Allow").orElse(null),
                "Go: notAllowed 被 CORS 接管 ⇒ **不写** Allow 头");
    }

    @Test
    @DisplayName("路由·404 / 405 都**不经过鉴权**（Go 的 bindRoute 只把 chain 挂在匹配上的路由上）")
    void routerErrorsBypassAuth() throws Exception {
        // ⚠️ 刻意不带 Authorization。若鉴权拦截器先跑，未注册路径会被遮蔽成 401。
        assertEquals(404, get("/v1/nope").statusCode(),
                "⚠️ Boot 的静态资源处理器匹配 /** ⇒ 未注册路径也拿到 handler ⇒ "
                        + "鉴权拦截器先跑会把 404 遮蔽成 401");
        // 反向对照：路径存在但没带 token ⇒ 必须仍是 401，不能被 404 分支吞掉
        assertEquals(401, post("/v1/article", null, "application/json", "{}").statusCode());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 每个端点的「合法请求体」。 */
    private static String happyBody(Endpoint ep) {
        return ep.rpc() == Rpc.CREATE ? "{\"title\":\"t\",\"content\":\"c\"}" : "{}";
    }

    private String userToken() {
        return JwtUtil.getToken(properties.getAuth().getAccessSecret(),
                System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    /** 构造一个<b>不带</b> userId claim 的 token（Go helper.go:17-39 ⇒ raw == nil ⇒ 1005）。 */
    private String tokenWithoutUserIdClaim() {
        return Jwts.builder()
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .claim("scope", "user")
                .signWith(Keys.hmacShaKeyFor(
                        JwtUtil.padToHmacKeySize(properties.getAuth().getAccessSecret())))
                .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** 该端点在 Go 侧有没有「归属校验」分支。 */
    private static boolean hasOwnershipCheck(Endpoint ep) {
        return ep.rpc() == Rpc.UPDATE || ep.rpc() == Rpc.DELETE || ep.rpc() == Rpc.GET_AUTHOR;
    }

    private HttpResponse<String> send(Endpoint ep, String authorization, String contentType, String body)
            throws Exception {
        return send(ep, authorization, contentType, body, null);
    }

    private HttpResponse<String> send(Endpoint ep, String authorization, String contentType, String body,
                                      String query) throws Exception {
        if (ep.rpc() == Rpc.UPLOAD) {
            // ⚠️ upload 端点是 multipart 通道 ⇒ 必须走 multipart 构造
            //    （Go: upload_image_handler.go:16 `r.FormFile("image")`）
            //    ⇒ 用 JSON body 打过去会先在 multipart 解析层就 400，测不到 rpc 分支。
            return sendMultipart(ep, "image", "a.png", new byte[]{1, 2, 3});
        }
        String url = "http://127.0.0.1:" + port + ep.path() + (query == null ? "" : query);
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(15));
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        if ("GET".equals(ep.method()) || "DELETE".equals(ep.method())) {
            b.method(ep.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            b.method(ep.method(), HttpRequest.BodyPublishers.ofString(
                    body == null ? "" : body, StandardCharsets.UTF_8));
        }
        return client().send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).GET().build();
        return client().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String authorization, String contentType, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return client().send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** multipart 上传 —— 用裸字节构造 body（JDK HttpClient 没有 multipart 便捷方法）。 */
    private HttpResponse<String> sendMultipart(Endpoint ep, String fieldName, String fileName, byte[] content)
            throws Exception {
        String boundary = "----ragboundary0123456789";
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        buf.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        buf.write(("Content-Disposition: form-data; name=\"" + fieldName
                + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        buf.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        buf.write(content);
        buf.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + ep.path()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buf.toByteArray()))
                .build();
        return client().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 断言<b>错误</b>信封 —— 用 JSON 解析而不是逐字节比对。
     *
     * <p>⚠️ 「顶层 code 必须等于期望值」这一条同时守住了 <b>DRIFT-FRAMEWORK-001</b>：
     * 若 envelope 被 {@code GlobalResponseAdvice} 多包一层，顶层 code 会变成 200。
     */
    private static void assertErrorEnvelope(HttpResponse<String> r, int expectedCode, String expectedMsg) {
        assertEquals(200, r.statusCode(), "Go: 业务失败恒 HTTP 200 + 信封");
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                "Go 的 httpx.WriteJson 写 Content-Type: application/json，实际 = "
                        + r.headers().firstValue("Content-Type").orElse("<无>"));
        String body = r.body();
        assertTrue(body.contains("\"code\":" + expectedCode),
                "信封的 code 必须是 " + expectedCode + "（顶层！被多包一层就会变成 200），实际 body = " + body);
        assertTrue(body.contains("\"msg\":\"" + expectedMsg + "\""),
                "信封的 msg 必须是 \"" + expectedMsg + "\"，实际 body = " + body);
    }

    /** 断言<b>成功</b>信封 —— 顶层 code/msg 为 200/OK。 */
    private static void assertSuccessEnvelope(HttpResponse<String> r) {
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                "Go 的 httpx.WriteJson 写 Content-Type: application/json");
        String body = r.body();
        assertTrue(body.contains("\"code\":" + CODE_SUCCESS),
                "成功信封的顶层 code 必须是 200，实际 body = " + body);
    }

    /**
     * 断言「Go 的 {@code http.Error} 形状」—— text/plain + nosniff。
     *
     * <p>⚠️ 比<b>解析后</b>的值：真 Tomcat 把 {@code "text/plain; charset=utf-8"} 归一化成
     * {@code "text/plain;charset=utf-8"}（分号后无空格）⇒ 按 RFC 9110 两者语义相同。
     */
    private static void assertTextPlainWithNosniff(HttpResponse<String> r) {
        String ct = r.headers().firstValue("Content-Type").orElse(null);
        assertNotNull(ct, "Go 的 http.Error 无条件写 Content-Type: text/plain; charset=utf-8");
        assertEquals("text/plain;charset=utf-8", ct.replace(" ", ""),
                "Go 的 http.Error 写死 text/plain + charset=utf-8；charset 整个丢掉或退化成 text/html 都是回归");
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(null),
                "net/http.Error 无条件写 X-Content-Type-Options: nosniff");
    }

    // ---------- 打桩 ----------

    private void stubHappyPath(Endpoint ep) {
        switch (ep.rpc()) {
            case CREATE -> when(stub.createArticle(any()))
                    .thenReturn(CreateArticleResponse.newBuilder().setArticleId("art-1").build());
            case GET_PUBLIC, GET_AUTHOR -> when(stub.getArticle(any()))
                    .thenReturn(publishedArticle(String.valueOf(CLAIM_UID)));
            case UPDATE -> {
                when(stub.getArticle(any())).thenReturn(publishedArticle(String.valueOf(CLAIM_UID)));
                when(stub.updateArticle(any()))
                        .thenReturn(UpdateArticleResponse.newBuilder().setSuccess(true).build());
            }
            case DELETE -> {
                when(stub.getArticle(any())).thenReturn(publishedArticle(String.valueOf(CLAIM_UID)));
                when(stub.deleteArticle(any()))
                        .thenReturn(DeleteArticleResponse.newBuilder().setSuccess(true).build());
            }
            case LIST -> when(stub.listArticles(any()))
                    .thenReturn(ListArticlesResponse.newBuilder()
                            .addArticles(publishedArticleProto(String.valueOf(CLAIM_UID)))
                            .setTotal(1).setPage(1).setPageSize(20).build());
            case UPLOAD -> when(stub.uploadFile(any()))
                    .thenReturn(UploadFileResponse.newBuilder()
                            .setFileUrl("http://minio/a.png").build());
        }
    }

    /** 该端点的**第一次** rpc 调用失败（Go 的 error 分支只看第一次调用的 status）。 */
    private void stubFirstCallFailure(Endpoint ep, Status status) {
        StatusRuntimeException e = status.asRuntimeException();
        switch (ep.rpc()) {
            case CREATE -> when(stub.createArticle(any())).thenThrow(e);
            case GET_PUBLIC, GET_AUTHOR, UPDATE, DELETE -> when(stub.getArticle(any())).thenThrow(e);
            case LIST -> when(stub.listArticles(any())).thenThrow(e);
            case UPLOAD -> when(stub.uploadFile(any())).thenThrow(e);
        }
    }

    private void stubFirstCallPermissionDenied(Endpoint ep) {
        stubFirstCallFailure(ep, Status.PERMISSION_DENIED.withDescription("not your article"));
    }

    private void stubFirstCallNotFound(Endpoint ep) {
        stubFirstCallFailure(ep, Status.NOT_FOUND.withDescription("no such article"));
    }

    /** 已发布的一篇文章（proto 消息体本身）。 */
    private static Article publishedArticleProto(String authorId) {
        return Article.newBuilder()
                .setId("art-1")
                .setTitle("t")
                .setAuthorId(authorId)
                .setStatus(ArticleStatus.PUBLISHED)
                .build();
    }

    /** 已发布的一篇文章（包成 {@code GetArticleResponse}）。 */
    private static GetArticleResponse publishedArticle(String authorId) {
        return GetArticleResponse.newBuilder().setArticle(publishedArticleProto(authorId)).build();
    }

    /** 断言该端点的 rpc **一次都没被调用**。 */
    private void verifyNoRpcCall(Endpoint ep) {
        switch (ep.rpc()) {
            case CREATE -> verify(stub, never()).createArticle(any());
            case GET_PUBLIC, GET_AUTHOR, UPDATE, DELETE -> verify(stub, never()).getArticle(any());
            case LIST -> verify(stub, never()).listArticles(any());
            case UPLOAD -> verify(stub, never()).uploadFile(any());
        }
    }
}
