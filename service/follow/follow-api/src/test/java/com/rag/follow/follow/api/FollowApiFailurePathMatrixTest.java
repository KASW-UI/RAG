package com.rag.follow.follow.api;

import com.rag.follow.follow.api.client.FollowRpcClient;
import com.rag.follow.follow.api.support.RealTomcatPort;
import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.FollowServiceGrpc.FollowServiceBlockingStub;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.proto.UserListResp;
import com.rag.users.common.jwt.JwtUtil;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * follow-api 的<b>失败路径矩阵</b>（真 Tomcat · 8 端点 × 4 类失败）。
 *
 * <h2>为什么要有它（基线缺口 G-13）</h2>
 * <p>{@code docs/PhaseGoal-judge.md} 的 P0 #2 判据原文：
 * <pre>
 *   「存在失败路径处理」推不出「参数校验 / 业务规则 / 认证 / 权限四项都有效」；
 *   且失败路径**域覆盖不全**（article / follow / task / hot 四域无独立失败路径矩阵）
 * </pre>
 * ⇒ 本类就是 follow 域的那份「独立失败路径矩阵」：<b>8 个端点逐个</b>跑
 * <b>认证 / 权限 / 参数校验 / 业务规则</b>四类失败，外加<b>基础设施类</b>（rpc 异常映射）。
 *
 * <h2>为什么必须是<b>真 Tomcat</b>（不是 MockMvc）</h2>
 * <p>本模块原先的 <b>10 个测试类全部是 {@code webEnvironment = MOCK}</b> ⇒
 * <b>真 Tomcat 覆盖率是零</b>。而 MockMvc 有三处系统性地测不到：
 * <ol>
 *   <li><b>Content-Type 的容器归一化</b> —— 真 Tomcat 把 {@code text/plain; charset=utf-8}
 *       归一化成 {@code text/plain;charset=utf-8}（分号后无空格）
 *       ⇒ MockMvc 里断言「带空格」的写法<b>在 MockMvc 下会绿、在真容器下会红</b>
 *       ⇒ 这正是 DRIFT-024（{@code text/plain 400 丢 Content-Type}）当初漏网的机制；</li>
 *   <li><b>Content-Length</b> —— 由容器依据实际写入字节数计算，MockMvc 不参与；</li>
 *   <li><b>body 是否被容器补写</b> —— 405 这类「body 为 null」的响应，
 *       容器会不会自作主张补空 body 或补 Content-Type，只有真容器说了算。</li>
 * </ol>
 * ⇒ 本类用 {@code DEFINED_PORT} + <b>本域专用端口段</b>（{@link RealTomcatPort}）
 * + JDK HttpClient 读<b>原始</b>响应字节。
 *
 * <h2>矩阵的四个失败类（Go 出处逐条）</h2>
 * <table border="1">
 *   <caption>四类失败与 Go 侧判据</caption>
 *   <tr><th>类</th><th>Go 出处</th><th>可观察形态</th></tr>
 *   <tr>
 *     <td>认证</td>
 *     <td>{@code rest/handler/authhandler.go:106}（401 空 body）·
 *         {@code middleware/checkblacklistmiddleware.go:42-48}（401 两字段）·
 *         {@code logic/follow/helper.go:13-33}（claim 取不到 ⇒ <b>5004</b>）</td>
 *     <td>401 + 空 body / 401 + {@code {code:5004}} / <b>200</b> + {@code {code:5004}}</td>
 *   </tr>
 *   <tr>
 *     <td>权限</td>
 *     <td>{@code getblocklistlogic.go:45-50} 与 {@code getrecommendationslogic.go:45-50}
 *         （<b>只有这两个</b>有 Forbidden 分支）</td>
 *     <td>200 + {@code {code:5005,msg:"forbidden"}}，且 <b>RPC 完全不被调用</b></td>
 *   </tr>
 *   <tr>
 *     <td>参数校验</td>
 *     <td>{@code types/types.go:25-27}（{@code RelationActionReq.target_id} <b>无 optional ⇒ 必填</b>）
 *         vs {@code types/types.go:6-10}（{@code FollowListReq} 三字段<b>全 optional</b>）</td>
 *     <td>动作端点 200 + {@code {code:5001}}；list 端点按<b>零值</b>继续</td>
 *   </tr>
 *   <tr>
 *     <td>业务规则</td>
 *     <td>8 个 logic 里逐条相同的
 *         {@code if rpcResp.Code != int32(followcommon.Success) { return nil, followcommon.NewErrCodeMsg(int(rpcResp.Code), rpcResp.Msg) }}</td>
 *     <td>200 + <b>原样透传</b> rpc 的 code + msg（5201~5204 等）</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ 矩阵里最容易「顺手统一」的三处不对称（Go 侧就是不对称）</h2>
 * <ol>
 *   <li><b>归属校验只有 2 / 4 个 list 端点有</b> —— 关注列表与粉丝列表<b>公开可查</b>
 *       （{@code getfollowlistlogic.go:45} 与 {@code getfollowerlistlogic.go:45}
 *       <b>没有</b> Forbidden 分支），黑名单与推荐是私有的
 *       ⇒ 给 4 个端点统一加校验 = <b>新增拒绝行为</b>，会打破已有客户端。</li>
 *   <li><b>claim 取不到是 200 + 5004，不是 401</b> —— 验签层只验签名与 exp，
 *       claim 取值判定属于 logic 层（{@code helper.go:13-33}）。</li>
 *   <li><b>动作端点缺必填字段是 200 + 5001，不是 400</b> —— follow api 注册了
 *       {@code httpx.SetErrorHandler}（{@code followcenter.go:53}）⇒ 解析失败也被折叠成
 *       <b>HTTP 200 + 业务信封</b>；users 域两个 api <b>没注册</b> ⇒ 那边才是 400 + text/plain。</li>
 * </ol>
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类的<b>期望值全部抄自 Go 源码</b>（每条断言在 {@link Endpoint} 里带 Go {@code 文件:行号}）
 * ⇒ 这是 <b>RAG 侧的自动化断言</b>；本类<b>不</b>声称双侧活体对拍
 * ⇒ 证据强度按基线 §1.1 的 **R3**（有 Go 出处 + 有 RAG 侧自动化断言）计，
 * <b>不</b>按 R4（双侧活体 + 变异）计。
 */
@SpringBootTest(
        classes = FollowApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ 必须关掉 Nacos：DEFINED_PORT 真的会启动 webServer ⇒ naming client
                //   随即注册并在 STARTING 态报「Client not connected」而让启动失败（实测）。
                //   MOCK 环境不启容器，所以不需要这一行 —— 两种环境的 property 集合不可互相照抄。
                "spring.cloud.nacos.discovery.enabled=false",
                "grpc.client.follow-rpc.address=static://127.0.0.1:65535",
                "grpc.client.follow-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FollowApiFailurePathMatrixTest {

    // ==================================================================
    // 模型
    // ==================================================================

    /** 归属校验规则 —— 对应各 logic 里「有没有 {@code queryUserID != currentUserID} 的 Forbidden 分支」。 */
    enum Scope {
        /** 动作端点：目标<b>就是</b>别人，Go 侧没有归属校验分支。 */
        NONE,
        /** 查别人 ⇒ 5005 forbidden，且 RPC 完全不被调用。 */
        SELF_ONLY,
        /** 公开端点：Go 侧没有 Forbidden 分支 ⇒ 查别人被允许。 */
        PUBLIC
    }

    /** 端点对应的下游 rpc 方法 —— 用于打桩与「RPC 是否被调用」的判定。 */
    enum Rpc {
        BLOCK, BLOCK_LIST, FOLLOW, FOLLOW_LIST, FOLLOWER_LIST, RECOMMENDATIONS, UNBLOCK, UNFOLLOW
    }

    /**
     * 8 个端点 —— 与 Go {@code service/follow/api/internal/handler/routes.go:20-63} 逐条对应。
     *
     * @param path     完整路径（Go 的 {@code rest.WithPrefix("/follow/v1")} + 路由 path）
     * @param rpc      下游方法
     * @param action   是否「动作端点」（{@code RelationActionReq} ⇒ {@code target_id} 必填）
     * @param scope    归属校验规则
     * @param goLogic  Go 侧 logic 的 {@code 文件:行号}（本类的期望值出处）
     */
    record Endpoint(String path, Rpc rpc, boolean action, Scope scope, String goLogic) {
        @Override
        public String toString() {
            return path + " ｜ Go: " + goLogic;
        }
    }

    /**
     * 8 个端点的矩阵行 —— <b>顺序与 Go routes.go 一致</b>，便于逐条对照。
     *
     * <p>⚠️ 归属校验那一列（{@code scope}）的取值<b>逐条来自 Go 源码</b>：
     * <ul>
     *   <li>{@code getblocklistlogic.go:45-50} ⇒ {@link Scope#SELF_ONLY}；</li>
     *   <li>{@code getrecommendationslogic.go:45-50} ⇒ {@link Scope#SELF_ONLY}；</li>
     *   <li>{@code getfollowlistlogic.go:45} ⇒ <b>没有</b> Forbidden 分支 ⇒ {@link Scope#PUBLIC}；</li>
     *   <li>{@code getfollowerlistlogic.go:45} ⇒ <b>没有</b> Forbidden 分支 ⇒ {@link Scope#PUBLIC}；</li>
     *   <li>4 个动作端点 ⇒ 目标就是别人，<b>没有</b>归属校验 ⇒ {@link Scope#NONE}。</li>
     * </ul>
     */
    static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("/follow/v1/block", Rpc.BLOCK, true, Scope.NONE,
                    "logic/follow/blocklogic.go:31"),
            new Endpoint("/follow/v1/block/list", Rpc.BLOCK_LIST, false, Scope.SELF_ONLY,
                    "logic/follow/getblocklistlogic.go:31"),
            new Endpoint("/follow/v1/follow", Rpc.FOLLOW, true, Scope.NONE,
                    "logic/follow/followlogic.go:31"),
            new Endpoint("/follow/v1/follow/list", Rpc.FOLLOW_LIST, false, Scope.PUBLIC,
                    "logic/follow/getfollowlistlogic.go:31"),
            new Endpoint("/follow/v1/follower/list", Rpc.FOLLOWER_LIST, false, Scope.PUBLIC,
                    "logic/follow/getfollowerlistlogic.go:31"),
            new Endpoint("/follow/v1/recommendations", Rpc.RECOMMENDATIONS, false, Scope.SELF_ONLY,
                    "logic/follow/getrecommendationslogic.go:31"),
            new Endpoint("/follow/v1/unblock", Rpc.UNBLOCK, true, Scope.NONE,
                    "logic/follow/unblocklogic.go:31"),
            new Endpoint("/follow/v1/unfollow", Rpc.UNFOLLOW, true, Scope.NONE,
                    "logic/follow/unfollowlogic.go:31"));

    static Stream<Endpoint> endpoints() {
        return ENDPOINTS.stream();
    }

    // ==================================================================
    // 常量与装置
    // ==================================================================

    /** 与 application.yml 的 {@code follow-api.user-auth.access-secret} 同一份。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;
    private static final long OTHER_UID = 4242L;

    /** Go {@code errmsg.go:34} {@code Success = 200}。 */
    private static final int CODE_SUCCESS = 200;
    /** Go {@code errmsg.go:38} {@code ErrorServerCommon = 5001}。 */
    private static final int CODE_SERVER_COMMON = 5001;
    /** Go {@code errmsg.go:41} {@code ErrorUnauthorized = 5004}。 */
    private static final int CODE_UNAUTHORIZED = 5004;
    /** Go {@code errmsg.go:42} {@code ErrorForbidden = 5005}。 */
    private static final int CODE_FORBIDDEN = 5005;
    /** Go {@code errmsg.go:37} {@code CodeServerBusy = 1015}。 */
    private static final int CODE_SERVER_BUSY = 1015;

    @LocalServerPort
    private int port;

    @MockitoBean
    private FollowRpcClient rpcClient;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private FollowServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(FollowServiceBlockingStub.class);
        Mockito.when(rpcClient.followRpc()).thenReturn(stub);
        // 默认：token 不在黑名单里（Go: err == nil && exists ⇒ exists 为 false ⇒ 放行）
        Mockito.when(redisTemplate.hasKey(Mockito.anyString())).thenReturn(Boolean.FALSE);
    }

    /**
     * <b>本类必须绑到本域专用端口段</b>（而不是 Spring 的 {@code RANDOM_PORT}）。
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
    @DisplayName("真容器必须绑到本域专用端口段 22340-22359（RANDOM_PORT 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段）")
    void bindsToDedicatedPortSegment() {
        assertTrue(port >= RealTomcatPort.FIRST && port <= RealTomcatPort.LAST,
                "Tomcat 实际绑定的端口必须在 " + RealTomcatPort.FIRST + "-" + RealTomcatPort.LAST
                        + " 内，实际=" + port
                        + "（若落在 49152-65535，说明 webEnvironment 被改回 RANDOM_PORT 了）");
    }

    // ==================================================================
    // 1. 认证类失败（8 端点 × 3 种形态）
    // ==================================================================

    @ParameterizedTest(name = "[认证·无 token] {0}")
    @MethodSource("endpoints")
    @DisplayName("认证·无 Authorization ⇒ 401 + **空 body** + **无 Content-Type**（Go authhandler.go:106 unauthorized()）")
    void missingTokenIs401WithEmptyBody(Endpoint ep) throws Exception {
        HttpResponse<String> r = post(ep, null, "application/json", "{}");

        assertEquals(401, r.statusCode(), "Go: 无 token ⇒ 401");
        assertEquals("", r.body(), "Go 的 unauthorized() 只 WriteHeader、**不写任何字节** ⇒ body 必须为空");
        assertNull(r.headers().firstValue("Content-Type").orElse(null),
                "Go 的 unauthorized() 不设 Content-Type ⇒ 真容器也不应补。"
                        + "若这里非空，说明容器给空 body 补了头（那与 Go 不同）");
    }

    @ParameterizedTest(name = "[认证·黑名单] {0}")
    @MethodSource("endpoints")
    @DisplayName("认证·命中黑名单 ⇒ 401 + Go 的**两字段** body + 逐字节 Content-Type（checkblacklistmiddleware.go:42-48）")
    void blacklistedTokenIs401WithGoTwoFieldBody(Endpoint ep) throws Exception {
        Mockito.when(redisTemplate.hasKey(Mockito.anyString())).thenReturn(Boolean.TRUE);

        HttpResponse<String> r = post(ep, bearer(userToken()), "application/json", "{}");

        assertEquals(401, r.statusCode(), "Go: 命中黑名单 ⇒ 401");
        assertEquals("{\"code\":" + CODE_UNAUTHORIZED + ",\"msg\":\"unauthorized\"}", r.body(),
                "⚠️ Go 写的是**两字段**对象（没有 data 字段）⇒ 被 GlobalResponseAdvice 包一层就变三字段。"
                        + "⚠️ 本条是**逐字节**断言（不是 JSONPath）⇒ 字段顺序错也会红");
        assertEquals("application/json;charset=utf-8",
                r.headers().firstValue("Content-Type").orElse(null),
                "Go 写死字面量 application/json;charset=utf-8（分号后**无空格**）");
    }

    @ParameterizedTest(name = "[认证·claim 缺失] {0}")
    @MethodSource("endpoints")
    @DisplayName("认证·token 无 userId claim ⇒ **HTTP 200** + {code:5004}（不是 401；Go helper.go:13-33）")
    void tokenWithoutUserIdClaimIs200With5004(Endpoint ep) throws Exception {
        // ⚠️ body 必须**合法**：Go 的 handler 先 httpx.Parse（失败即 5001），
        //    再进 logic 做 extractUserID ⇒ 要验的是「claim 取不到」这一条，不是「解析失败」。
        HttpResponse<String> r = post(ep, bearer(tokenWithoutUserIdClaim()),
                "application/json", happyBody(ep));

        assertEquals(200, r.statusCode(),
                "Go: claim 取不到属于**业务未鉴权**（5004）⇒ HTTP 恒 200。"
                        + "若这里变成 401，就是把 logic 层的判定提到了鉴权层");
        assertErrorEnvelope(r, CODE_UNAUTHORIZED, "unauthorized");
        verifyRpcNotCalled(ep);
    }

    // ==================================================================
    // 2. 权限类失败（归属校验）
    // ==================================================================

    @ParameterizedTest(name = "[权限·查别人] {0}")
    @MethodSource("endpoints")
    @DisplayName("权限·查别人 ⇒ SELF_ONLY 端点 200+{code:5005} 且 RPC 未调用；PUBLIC/动作端点放行（Go 逐条 logic）")
    void scopeCheckMatrix(Endpoint ep) throws Exception {
        stubSuccess(ep);

        HttpResponse<String> r = post(ep, bearer(userToken()), "application/json", otherUserBody(ep));

        assertEquals(200, r.statusCode(), "Go: 归属校验失败也是 HTTP 200 + 业务信封");
        switch (ep.scope()) {
            case SELF_ONLY -> {
                assertErrorEnvelope(r, CODE_FORBIDDEN, "forbidden");
                verifyRpcNotCalled(ep);
            }
            case PUBLIC, NONE -> {
                assertSuccessEnvelope(r);
                verifyRpcCalled(ep);
            }
        }
    }

    // ==================================================================
    // 3. 参数校验类失败
    // ==================================================================

    @ParameterizedTest(name = "[参数校验·缺必填] {0}")
    @MethodSource("endpoints")
    @DisplayName("参数校验·缺必填 ⇒ 动作端点 200+{code:5001}（RPC 未调用）；list 端点按零值继续（Go types.go）")
    void requiredFieldMissingMatrix(Endpoint ep) throws Exception {
        stubSuccess(ep);

        HttpResponse<String> r = post(ep, bearer(userToken()), "application/json", "{}");

        assertEquals(200, r.statusCode(),
                "Go: follow api 注册了 httpx.SetErrorHandler（followcenter.go:53）⇒ "
                        + "**解析失败也是 200 + 业务信封**，不是 400（users 域两个 api 才是 400）");
        if (ep.action()) {
            assertErrorEnvelope(r, CODE_SERVER_COMMON, "internal server error");
            verifyRpcNotCalled(ep);
        } else {
            // FollowListReq 三字段全 optional ⇒ Go 的 UnmarshalJsonMap(nil, v) 通过 ⇒ 零值继续
            assertSuccessEnvelope(r);
            verifyRpcCalled(ep);
        }
    }

    // ==================================================================
    // 4. 业务规则类失败（rpc 回包的业务码原样透传）
    // ==================================================================

    @ParameterizedTest(name = "[业务规则·rpc 业务码] {0}")
    @MethodSource("endpoints")
    @DisplayName("业务规则·rpc 回包 code!=200 ⇒ **原样透传** code + msg（Go 8 个 logic 的 NewErrCodeMsg）")
    void rpcBusinessCodeIsPropagatedVerbatim(Endpoint ep) throws Exception {
        stubBizCode(ep, 5201, "self relation is not allowed");

        HttpResponse<String> r = post(ep, bearer(userToken()), "application/json", happyBody(ep));

        assertEquals(200, r.statusCode());
        assertErrorEnvelope(r, 5201, "self relation is not allowed");
    }

    // ==================================================================
    // 5. 基础设施类失败（rpc 异常 ⇒ 按 gRPC code 映射）
    // ==================================================================

    @ParameterizedTest(name = "[基础设施·rpc 异常] {0}")
    @MethodSource("endpoints")
    @DisplayName("基础设施·rpc 抛 UNAVAILABLE ⇒ 200 + {code:1015,msg:\"service busy\"}（Go BizCodeFromError）")
    void rpcUnavailableBecomes1015(Endpoint ep) throws Exception {
        stubRpcFailure(ep, Status.UNAVAILABLE.withDescription("transport is closing").asRuntimeException());

        HttpResponse<String> r = post(ep, bearer(userToken()), "application/json", happyBody(ep));

        assertEquals(200, r.statusCode());
        assertErrorEnvelope(r, CODE_SERVER_BUSY, "service busy");
    }

    // ==================================================================
    // 6. 路由层字节级（404 / 405）
    // ==================================================================

    @Test
    @DisplayName("404：body 逐字节等于 \"404 page not found\\n\"，带 text/plain + nosniff（Go patrouter.go:73,93-97）")
    void notFoundMatchesGoBytes() throws Exception {
        HttpResponse<String> r = get("/follow/v1/no-such-endpoint");

        assertEquals(404, r.statusCode());
        assertEquals("404 page not found\n", r.body(),
                "Go 是 http.Error + fmt.Fprintln ⇒ body 带尾随换行、全长 20 字节");
        String ctRaw = r.headers().firstValue("content-type").orElse(null);
        assertNotNull(ctRaw, "Go 的 http.Error 无条件写 Content-Type: text/plain; charset=utf-8");
        // ⚠️ 比**解析后**的值：真 Tomcat 把 "text/plain; charset=utf-8" 归一化成
        //    "text/plain;charset=utf-8"（分号后无空格）⇒ 按 RFC 9110 两者语义相同。
        assertEquals("text/plain;charset=utf-8", ctRaw.replace(" ", ""),
                "Go 的 http.Error 写死 text/plain + charset=utf-8；charset 整个丢掉或退化成 text/html 都是回归");
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(null),
                "net/http.Error 无条件写 X-Content-Type-Options: nosniff");
        // ⚠️ **不写死 20**：body = "404 page not found\n" 是 18 + 1 = **19** 字节。
        //    共享层 GoStyleRouterErrorResponses 的 javadoc 与 like 域的 DisplayName 都写「20 字节」
        //    ⇒ 那两处**注释差 1**（断言本身是按实际长度比的，所以它们一直绿）。
        //    本域写**按实际 body 字节数**比（与 like-api 的断言同法）⇒ 不复述那个错数。
        assertEquals(r.body().getBytes(StandardCharsets.UTF_8).length,
                Integer.parseInt(r.headers().firstValue("Content-Length").orElse("-1")),
                "Content-Length 必须等于实际写入字节数，否则分块编码/截断会让客户端读错");
        assertTrue(r.body().endsWith("\n"), "Go 用 Fprintln ⇒ body 以换行结尾");
    }

    @Test
    @DisplayName("405：裸状态码 + Allow 头，**无 body、无 Content-Type**（与 404 形状相反；Go patrouter.go:79-82）")
    void methodNotAllowedIsBareStatusWithAllowHeader() throws Exception {
        HttpResponse<String> r = get("/follow/v1/follow");

        assertEquals(405, r.statusCode());
        assertEquals("POST", r.headers().firstValue("Allow").orElse(null),
                "Go patrouter.go:79-82 会写 Allow；follow api 的 8 个端点全是 POST");
        assertEquals("", r.body(), "Go 侧只有 WriteHeader、没有 Fprintln ⇒ body 必须为空");
        assertNull(r.headers().firstValue("Content-Type").orElse(null),
                "Go 侧 405 从未设置 Content-Type；body 为空时容器也不应补");
    }

    @Test
    @DisplayName("404 / 405 都**不经过鉴权**（Go 的 bindRoute 只把 chain 挂在匹配上的路由上）")
    void routerErrorsBypassAuth() throws Exception {
        // ⚠️ 刻意不带 Authorization。Go 侧 patrouter.go:73,79 的 404/405 分支不进 chain
        //    ⇒ 不经 auth。若 RAG 侧返回 401，就是把「路由没匹配上」伪装成「你没登录」。
        assertEquals(404, get("/follow/v1/nope").statusCode(),
                "⚠️ Boot 的静态资源处理器匹配 /** ⇒ 未注册路径也拿到 handler ⇒ "
                        + "鉴权拦截器先跑会把 404 遮蔽成 401（DRIFT-USERAPI 同款，2026-10-22 修过）");

        // 反向对照：路径存在但没带 token ⇒ 必须仍是 401，不能被 404/405 分支吞掉
        assertEquals(401, post(ENDPOINTS.get(2), null, "application/json", "{}").statusCode());
    }

    // ==================================================================
    // 7. 顺序与 fail-open
    // ==================================================================

    @Test
    @DisplayName("拦截器顺序：JWT 在外、黑名单在内 ⇒ **签名无效**的 token 恒 401 空 body（黑名单永不查询）")
    void jwtAuthRunsBeforeBlacklist() throws Exception {
        // Go: rest/engine.go:101-116 ⇒ chain 顺序是 Authorize(JWT) → CheckBlacklist → handler
        String foreignToken = JwtUtil.getToken("00000000-0000-0000-0000-000000000000",
                System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);

        HttpResponse<String> r = post(ENDPOINTS.get(2), bearer(foreignToken), "application/json", "{}");

        assertEquals(401, r.statusCode());
        assertEquals("", r.body(),
                "Go: Authorize 先失败 ⇒ 401 空 body；若顺序反了会得到黑名单那条两字段 body");
        verify(redisTemplate, never()).hasKey(Mockito.anyString());
    }

    @Test
    @DisplayName("Redis 故障 ⇒ **fail-open** 放行（Go: err != nil ⇒ 条件为假 ⇒ 放行），不返回 401")
    void redisFailureFailsOpen() throws Exception {
        Mockito.when(redisTemplate.hasKey(Mockito.anyString()))
                .thenThrow(new RuntimeException("redis down"));
        stubSuccess(ENDPOINTS.get(3));

        HttpResponse<String> r = post(ENDPOINTS.get(3), bearer(userToken()), "application/json", "{}");

        assertEquals(200, r.statusCode(), "Redis 挂掉不得拦住任何人");
        assertSuccessEnvelope(r);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 动作端点：合法的 target_id；list 端点：空 body（三个字段全 optional）。 */
    private static String happyBody(Endpoint ep) {
        return ep.action() ? "{\"target_id\":\"999\"}" : "{}";
    }

    /** 「查别人」的 body：动作端点的 target 就是别人；list 端点显式传别人的 user_id。 */
    private static String otherUserBody(Endpoint ep) {
        return ep.action()
                ? "{\"target_id\":\"" + OTHER_UID + "\"}"
                : "{\"user_id\":\"" + OTHER_UID + "\"}";
    }

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    /** 构造一个<b>不带</b> userId claim 的 token（Go helper.go:15 ⇒ raw == nil ⇒ 5004）。 */
    private static String tokenWithoutUserIdClaim() {
        return Jwts.builder()
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .claim("scope", "user")
                .signWith(Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(USER_SECRET)))
                .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private HttpResponse<String> post(Endpoint ep, String authorization, String contentType, String body)
            throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + ep.path()))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 断言<b>错误</b>信封 —— <b>用 JSON 解析</b>而不是逐字节比对。
     *
     * <p>⚠️ <b>为什么不逐字节</b>：envelope 的<b>字段顺序</b>由 Jackson 的属性发现顺序决定
     * （{@code Response} 用 Lombok 生成 getter ⇒ 属性来自 getter ⇒ 顺序不是语言保证），
     * 而 Go 侧 {@code service/common/response.Response} 的顺序是<b>结构体声明序</b>
     * （Code, Msg, Data）⇒ 逐字节比对会得到一条「红了也不代表有错」的断言。
     * ⇒ 本方法断言<b>语义</b>（code / msg / data），
     * 而「黑名单那条两字段 body」另有<b>逐字节</b>断言（那条例外是拦截器手写字符串，顺序是确定的）。
     *
     * <p>⚠️ 「顶层 code 必须等于期望值」这一条同时守住了 <b>DRIFT-FRAMEWORK-001</b>：
     * 若 envelope 被 {@code GlobalResponseAdvice} 多包一层，顶层 code 会变成 200。
     *
     * <p>⚠️ <b>{@code data} 必须为 null</b>：Go 侧
     * {@code httpx.SetErrorHandler} 产出的是 {@code &response.Response{Code: code, Msg: msg, Data: nil}}
     * ⇒ 错误信封恒为三字段且 data 为 null。
     */
    private static void assertErrorEnvelope(HttpResponse<String> r, int expectedCode, String expectedMsg) {
        assertEnvelopeCommon(r, expectedCode, expectedMsg);
        assertTrue(r.body().contains("\"data\":null"),
                "错误信封的 data 必须是 null（Go: response.Response{Data: nil}），实际 body = " + r.body());
    }

    /**
     * 断言<b>成功</b>信封 —— 顶层 code/msg 为 200/OK，且 {@code data} **非 null**。
     *
     * <p>⚠️ 成功信封的 {@code data} 是<b>载荷</b>（不是 null）⇒ 不能复用错误信封的判据。
     */
    private static void assertSuccessEnvelope(HttpResponse<String> r) {
        assertEnvelopeCommon(r, CODE_SUCCESS, "OK");
        assertTrue(!r.body().contains("\"data\":null"),
                "成功信封的 data 是载荷，不该是 null；实际 body = " + r.body());
    }

    /** 信封的公共部分：状态码 / Content-Type / 顶层 code / 顶层 msg。 */
    private static void assertEnvelopeCommon(HttpResponse<String> r, int expectedCode, String expectedMsg) {
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                "Go 的 httpx.WriteJson 写 Content-Type: application/json，实际 = "
                        + r.headers().firstValue("Content-Type").orElse("<无>"));

        String body = r.body();
        assertTrue(body.contains("\"code\":" + expectedCode),
                "信封的 code 必须是 " + expectedCode + "（顶层！被多包一层就会变成 200），实际 body = " + body);
        assertTrue(body.contains("\"msg\":\"" + expectedMsg + "\""),
                "信封的 msg 必须是 \"" + expectedMsg + "\"，实际 body = " + body);
    }

    // ---------- 打桩 ----------

    private void stubSuccess(Endpoint ep) {
        switch (ep.rpc()) {
            case BLOCK -> when(stub.block(any())).thenReturn(BaseResp.newBuilder().setCode(200).setMsg("OK").build());
            case BLOCK_LIST -> when(stub.getBlockList(any())).thenReturn(okUserList());
            case FOLLOW -> when(stub.follow(any())).thenReturn(BaseResp.newBuilder().setCode(200).setMsg("OK").build());
            case FOLLOW_LIST -> when(stub.getFollowList(any())).thenReturn(okUserList());
            case FOLLOWER_LIST -> when(stub.getFollowerList(any())).thenReturn(okUserList());
            case RECOMMENDATIONS -> when(stub.getRecommendations(any()))
                    .thenReturn(RecommendResp.newBuilder().setCode(200).setMsg("OK").build());
            case UNBLOCK -> when(stub.unblock(any())).thenReturn(BaseResp.newBuilder().setCode(200).setMsg("OK").build());
            case UNFOLLOW -> when(stub.unfollow(any())).thenReturn(BaseResp.newBuilder().setCode(200).setMsg("OK").build());
        }
    }

    private void stubBizCode(Endpoint ep, int code, String msg) {
        switch (ep.rpc()) {
            case BLOCK -> when(stub.block(any())).thenReturn(BaseResp.newBuilder().setCode(code).setMsg(msg).build());
            case BLOCK_LIST -> when(stub.getBlockList(any())).thenReturn(
                    UserListResp.newBuilder().setCode(code).setMsg(msg).build());
            case FOLLOW -> when(stub.follow(any())).thenReturn(BaseResp.newBuilder().setCode(code).setMsg(msg).build());
            case FOLLOW_LIST -> when(stub.getFollowList(any())).thenReturn(
                    UserListResp.newBuilder().setCode(code).setMsg(msg).build());
            case FOLLOWER_LIST -> when(stub.getFollowerList(any())).thenReturn(
                    UserListResp.newBuilder().setCode(code).setMsg(msg).build());
            case RECOMMENDATIONS -> when(stub.getRecommendations(any())).thenReturn(
                    RecommendResp.newBuilder().setCode(code).setMsg(msg).build());
            case UNBLOCK -> when(stub.unblock(any())).thenReturn(BaseResp.newBuilder().setCode(code).setMsg(msg).build());
            case UNFOLLOW -> when(stub.unfollow(any())).thenReturn(BaseResp.newBuilder().setCode(code).setMsg(msg).build());
        }
    }

    private void stubRpcFailure(Endpoint ep, StatusRuntimeException e) {
        switch (ep.rpc()) {
            case BLOCK -> when(stub.block(any())).thenThrow(e);
            case BLOCK_LIST -> when(stub.getBlockList(any())).thenThrow(e);
            case FOLLOW -> when(stub.follow(any())).thenThrow(e);
            case FOLLOW_LIST -> when(stub.getFollowList(any())).thenThrow(e);
            case FOLLOWER_LIST -> when(stub.getFollowerList(any())).thenThrow(e);
            case RECOMMENDATIONS -> when(stub.getRecommendations(any())).thenThrow(e);
            case UNBLOCK -> when(stub.unblock(any())).thenThrow(e);
            case UNFOLLOW -> when(stub.unfollow(any())).thenThrow(e);
        }
    }

    private void verifyRpcNotCalled(Endpoint ep) {
        switch (ep.rpc()) {
            case BLOCK -> verify(stub, never()).block(any());
            case BLOCK_LIST -> verify(stub, never()).getBlockList(any());
            case FOLLOW -> verify(stub, never()).follow(any());
            case FOLLOW_LIST -> verify(stub, never()).getFollowList(any());
            case FOLLOWER_LIST -> verify(stub, never()).getFollowerList(any());
            case RECOMMENDATIONS -> verify(stub, never()).getRecommendations(any());
            case UNBLOCK -> verify(stub, never()).unblock(any());
            case UNFOLLOW -> verify(stub, never()).unfollow(any());
        }
    }

    private void verifyRpcCalled(Endpoint ep) {
        switch (ep.rpc()) {
            case BLOCK -> verify(stub).block(any());
            case BLOCK_LIST -> verify(stub).getBlockList(any());
            case FOLLOW -> verify(stub).follow(any());
            case FOLLOW_LIST -> verify(stub).getFollowList(any());
            case FOLLOWER_LIST -> verify(stub).getFollowerList(any());
            case RECOMMENDATIONS -> verify(stub).getRecommendations(any());
            case UNBLOCK -> verify(stub).unblock(any());
            case UNFOLLOW -> verify(stub).unfollow(any());
        }
    }

    private static UserListResp okUserList() {
        return UserListResp.newBuilder().setCode(200).setMsg("OK").build();
    }
}
