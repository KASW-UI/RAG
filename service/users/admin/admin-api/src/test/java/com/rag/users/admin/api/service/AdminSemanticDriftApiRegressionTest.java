package com.rag.users.admin.api.service;

import com.rag.common.exception.AuthenticationException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.config.AdminApiProperties;
import com.rag.users.admin.api.dto.GetUserListReq;
import com.rag.users.admin.api.dto.UpdateUserReq;
import com.rag.users.admin.api.interceptor.AdminJwtAuthInterceptor;
import com.rag.users.admin.api.auth.AdminJwtSignatureVerifier;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * Go↔RAG 语义一致性回归网 —— admin-api 侧（issue #71186443）。
 *
 * <p>覆盖：
 * <ul>
 *   <li><b>DRIFT-002</b> getuser 的 {@code found} 恒为 true（Go 在 api 层写死）</li>
 *   <li><b>DRIFT-003</b> update 端点 {@code NotFound → 1003}，不落 1015</li>
 *   <li><b>DRIFT-004</b> update / resetuserpassword / unbanuser 的 {@code Internal → 5003}</li>
 *   <li><b>DRIFT-007</b> 分页默认值在本层套用，显式 0 原样透传</li>
 *   <li><b>DRIFT-009</b> 黑名单检查先于 JWT 验签</li>
 *   <li><b>A-01 / A-02</b> 空 list 输出 null；AdminInfo.email 恒输出</li>
 * </ul>
 *
 * <p>每条断言都带 Go 出处，便于 review 时逐条核对。
 *
 * <h2>⚠️ 本类的 interceptor 用例现在走的是<b>兜底分支</b>（2026-10-27 · DRIFT-USERAPI-009② 推广）</h2>
 * <p>验签已搬进 {@code GoStyleJwtAuthFilter}（Servlet Filter，3s 预算之外）⇒
 * {@code AdminJwtAuthInterceptor} 改成<b>消费</b> Filter 放进 request attribute 的验签结果。
 * <p>本类用的是 <b>Mockito mock 的 request</b> ⇒ {@code getAttribute(...)} 返回 {@code null}
 * ⇒ interceptor 落进<b>兜底分支</b>（就地验签）。
 * ⇒ 该分支与 <b>DRIFT-USERAPI-009② 修复前逐字一致</b>（同一份 verifier、同一套断言）
 * ⇒ ⇒ <b>本类的全部行为断言依然有效、且仍然有效</b>（它们断言的是<b>行为</b>，不是<b>路径</b>）。
 * <p>⇒ <b>生产路径（Filter 路径）</b>由新增的 {@code AdminApiJwtVerificationScopeTest} 覆盖
 * （含「Filter 排在 3s 预算之前」与「interceptor 消费的是 Filter 的结果」两条）。
 */
class AdminSemanticDriftApiRegressionTest {

    private static final String SECRET = "test-secret-key-with-at-least-256-bits-of-entropy-12345";

    private AdminServiceGrpc.AdminServiceBlockingStub stub;
    private AdminRpcClient rpcClient;
    private AdminApiProperties properties;
    private AdminApiMetrics metrics;
    private AdminApiSupport support;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        rpcClient = Mockito.mock(AdminRpcClient.class);
        Mockito.when(rpcClient.adminRpc()).thenReturn(stub);
        properties = new AdminApiProperties();
        properties.getAdminAuth().setAccessSecret(SECRET);
        properties.getAdminAuth().setAccessExpire(2592000L);
        metrics = new AdminApiMetrics(new SimpleMeterRegistry());
        support = new AdminApiSupport();
        request = Mockito.mock(HttpServletRequest.class);
    }

    /** 模拟 interceptor 已写入的 userId attribute。 */
    private void withClaimedUid(long uid) {
        Mockito.when(request.getAttribute("userId")).thenReturn(Long.valueOf(uid));
    }

    // ==================================================================
    // DRIFT-002：getuser 的 found 恒为 true
    // ==================================================================

    /**
     * Go 出处：api/internal/logic/admin/getuserlogic.go:51-60 —— api 层构造
     * {@code types.GetUserResp{User: ..., Found: true}}。
     * 注意 Go 的 rpc 层确实没设 pb 的 found（默认 false），本测试正是防"照抄 rpc 层"这个坑。
     */
    @Test
    @DisplayName("DRIFT-002：getuser 成功时 found 必须为 true，即使 rpc 层没设该字段")
    void getUser_foundIsAlwaysTrueOnSuccess() {
        Mockito.when(stub.getUser(any())).thenReturn(
                com.rag.users.admin.proto.GetUserResp.newBuilder()
                        .setUser(com.rag.users.admin.proto.UserInfo.newBuilder()
                                .setUid(42L).setUsername("bob").build())
                        .build());   // 故意不 setFound —— 模拟 proto3 默认 false

        var req = new com.rag.users.admin.api.dto.GetUserReq();
        req.setUid(42L);

        var resp = new AdminGetUserService(rpcClient, support).getUser(req, request);

        assertTrue(resp.isFound(),
                "DRIFT-002 回归：found 透传了 rpc 的 false（Go 在 api 层写死 true）");
        assertNotNull(resp.getUser());
        assertEquals("42", resp.getUser().getUid());
    }

    // ==================================================================
    // DRIFT-003：update 端点补 NOT_FOUND
    // ==================================================================

    /**
     * Go 出处：api/internal/logic/admin/updatelogic.go:61-62 {@code case codes.NotFound: return nil, errmsg.ErrorUserNotExist}。
     * 本实现此前缺该 case ⇒ 落到 default 1015「服务繁忙」。
     */
    @Test
    @DisplayName("DRIFT-003：update 的 NotFound 映射成 1003，不是 1015")
    void updateSelf_notFoundMapsTo1003() {
        withClaimedUid(7L);
        Mockito.when(stub.updateSelf(any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        var req = new com.rag.users.admin.api.dto.UpdateSelfReq();
        req.setUsername("alice");

        var ex = assertThrows(com.rag.common.exception.BusinessException.class,
                () -> new AdminUpdateSelfService(rpcClient, support).updateSelf(req, request));

        assertEquals(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ex.getCode());
        assertNotEquals(ErrorCode.CODE_SERVER_BUSY.getCode(), ex.getCode(),
                "DRIFT-003 回归：NotFound 落到了 default 1015");
    }

    /** 对照：AlreadyExists 仍是 1001，不能被这次改动带偏。 */
    @Test
    @DisplayName("DRIFT-003 对照：update 的 AlreadyExists 仍是 1001")
    void updateSelf_alreadyExistsStillMapsTo1001() {
        withClaimedUid(7L);
        Mockito.when(stub.updateSelf(any()))
                .thenThrow(new StatusRuntimeException(Status.ALREADY_EXISTS));

        var req = new com.rag.users.admin.api.dto.UpdateSelfReq();
        var ex = assertThrows(com.rag.common.exception.BusinessException.class,
                () -> new AdminUpdateSelfService(rpcClient, support).updateSelf(req, request));

        assertEquals(ErrorCode.ERROR_USER_EXIST.getCode(), ex.getCode());
    }

    // ==================================================================
    // DRIFT-004：三个端点的 Internal → 5003
    // ==================================================================

    /**
     * Go 出处：updatelogic.go:63-64 {@code case codes.Internal: return nil, errmsg.ErrorDbSelect}。
     * Go 自身的 update / resetuserpassword / unbanuser 三处用 5003，其余端点用 5001。
     */
    @Test
    @DisplayName("DRIFT-004：update 的 Internal 映射成 5003（不是 5001）")
    void updateSelf_internalMapsTo5003() {
        withClaimedUid(7L);
        Mockito.when(stub.updateSelf(any()))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        var ex = assertThrows(com.rag.common.exception.BusinessException.class,
                () -> new AdminUpdateSelfService(rpcClient, support)
                        .updateSelf(new com.rag.users.admin.api.dto.UpdateSelfReq(), request));

        assertEquals(ErrorCode.ERROR_DB_SELECT.getCode(), ex.getCode());
        assertEquals(5003, ex.getCode());
    }

    @Test
    @DisplayName("DRIFT-004：resetuserpassword 的 Internal 映射成 5003（不是 5001）")
    void resetUserPassword_internalMapsTo5003() {
        withClaimedUid(7L);
        Mockito.when(stub.resetUserPassword(any()))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        var req = new com.rag.users.admin.api.dto.ResetUserPasswordReq();
        req.setUid(100L);

        var ex = assertThrows(com.rag.common.exception.BusinessException.class,
                () -> new AdminResetUserPasswordService(rpcClient, metrics)
                        .resetUserPassword(req, request));

        assertEquals(ErrorCode.ERROR_DB_SELECT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("DRIFT-004：unbanuser 的 Internal 映射成 5003（不是 5001）")
    void unBanUser_internalMapsTo5003() {
        withClaimedUid(7L);
        Mockito.when(stub.unBanUser(any()))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        var req = new com.rag.users.admin.api.dto.UnBanUserReq();
        req.setUid(100L);

        var ex = assertThrows(com.rag.common.exception.BusinessException.class,
                () -> new AdminUnBanUserService(rpcClient, metrics).unBanUser(req, request));

        assertEquals(ErrorCode.ERROR_DB_SELECT.getCode(), ex.getCode());
    }

    /**
     * 防回退：<b>banuser</b> 在 Go 侧是 5001，与 unbanuser 不同。
     * 本轮只改了 unbanuser，banuser 必须保持 5001 —— 这是最容易"顺手统一"的一处。
     */
    @Test
    @DisplayName("防回退：banuser 的 Internal 仍是 5001（Go 侧与 unbanuser 不一致）")
    void banUser_internalStays5001() {
        withClaimedUid(7L);
        Mockito.when(stub.banUser(any()))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        var req = new com.rag.users.admin.api.dto.BanUserReq();
        req.setUid(100L);

        var ex = assertThrows(com.rag.common.exception.BusinessException.class,
                () -> new AdminBanUserService(rpcClient, metrics).banUser(req, request));

        assertEquals(ErrorCode.ERROR_SERVER_COMMON.getCode(), ex.getCode());
        assertEquals(5001, ex.getCode());
    }

    // ==================================================================
    // DRIFT-007：分页默认值在本层套用
    // ==================================================================

    @Test
    @DisplayName("DRIFT-007：未传 page / page_size 时套用 Go tag 的 default=1 / 20")
    void getUserList_appliesDefaultsWhenAbsent() {
        Mockito.when(stub.getUserList(any())).thenReturn(
                com.rag.users.admin.proto.GetUserListResp.newBuilder().setTotal(0).build());

        new AdminListUserService(rpcClient, support)
                .getUserList(new GetUserListReq(), request);

        var captor = ArgumentCaptor.forClass(com.rag.users.admin.proto.GetUserListReq.class);
        Mockito.verify(stub).getUserList(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(20L, captor.getValue().getPageSize());
    }

    /**
     * 显式 0 <b>不</b>被 default 覆盖 —— 对应 go-zero struct tag default 只在参数缺失时生效。
     * 这条值会一路传到 admin-rpc，由它算出负 OFFSET 让 PG 报错（Go 的行为）。
     */
    @Test
    @DisplayName("DRIFT-007：显式 page=0 不被兜底，原样透传给 rpc")
    void getUserList_explicitZeroIsPreserved() {
        Mockito.when(stub.getUserList(any())).thenReturn(
                com.rag.users.admin.proto.GetUserListResp.newBuilder().setTotal(0).build());

        var req = new GetUserListReq();
        req.setPage(0L);
        req.setPageSize(0L);

        new AdminListUserService(rpcClient, support).getUserList(req, request);

        var captor = ArgumentCaptor.forClass(com.rag.users.admin.proto.GetUserListReq.class);
        Mockito.verify(stub).getUserList(captor.capture());
        assertEquals(0L, captor.getValue().getPage(),
                "DRIFT-007 回归：显式 page=0 被兜底成 1");
        assertEquals(0L, captor.getValue().getPageSize(),
                "DRIFT-007 回归：显式 page_size=0 被兜底成 20");
    }

    @Test
    @DisplayName("DRIFT-007：正常分页值原样透传")
    void getUserList_normalPagingForwarded() {
        Mockito.when(stub.getUserList(any())).thenReturn(
                com.rag.users.admin.proto.GetUserListResp.newBuilder().setTotal(0).build());

        var req = new GetUserListReq();
        req.setPage(3L);
        req.setPageSize(50L);
        req.setKeyword(" alice ");

        new AdminListUserService(rpcClient, support).getUserList(req, request);

        var captor = ArgumentCaptor.forClass(com.rag.users.admin.proto.GetUserListReq.class);
        Mockito.verify(stub).getUserList(captor.capture());
        assertEquals(3L, captor.getValue().getPage());
        assertEquals(50L, captor.getValue().getPageSize());
        assertEquals(" alice ", captor.getValue().getKeyword(),
                "DRIFT-006：keyword 不应在 api 层被 trim");
    }

    // ==================================================================
    // A-01 / A-02：wire 契约
    // ==================================================================

    /** Go 出处：getuserlistlogic.go:51-62 的 nil slice + types 的 `json:"list"`（无 omitempty）。 */
    @Test
    @DisplayName("A-01：空结果时 list 为 null，不是空数组")
    void getUserList_emptyResultYieldsNullList() {
        Mockito.when(stub.getUserList(any())).thenReturn(
                com.rag.users.admin.proto.GetUserListResp.newBuilder().setTotal(0).build());

        var resp = new AdminListUserService(rpcClient, support)
                .getUserList(new GetUserListReq(), request);

        assertNull(resp.getList(), "A-01 回归：空结果应是 null（对齐 Go 的 nil slice）");
        assertEquals(0L, resp.getTotal());
    }

    @Test
    @DisplayName("A-01：有数据时 list 是正常数组")
    void getUserList_nonEmptyResultYieldsArray() {
        Mockito.when(stub.getUserList(any())).thenReturn(
                com.rag.users.admin.proto.GetUserListResp.newBuilder()
                        .setTotal(1)
                        .addList(com.rag.users.admin.proto.UserInfo.newBuilder().setUid(42L))
                        .build());

        var resp = new AdminListUserService(rpcClient, support)
                .getUserList(new GetUserListReq(), request);

        assertNotNull(resp.getList());
        assertEquals(1, resp.getList().size());
    }

    /**
     * A-02：Go 端 {@code types.AdminInfo.Email} 的 tag 是 {@code `json:"email"`} —— 无 omitempty，
     * 邮箱为空时也要输出 {@code "email":""}。
     */
    @Test
    @DisplayName("A-02：AdminInfo.email 为空时仍被 set（序列化成 \"\" 而非字段消失）")
    void fromProto_adminInfoAlwaysSetsEmail() {
        var proto = com.rag.users.admin.proto.AdminInfo.newBuilder()
                .setUid(42L).setUsername("bob").setEmail("").build();

        var dto = support.fromProto(proto);

        assertNotNull(dto.getEmail(), "A-02 回归：空 email 被跳过，字段会整个消失");
        assertEquals("", dto.getEmail());
    }

    /**
     * A-02 的<b>另一半</b>：Go 端 {@code types.UserInfo.Email} 带 {@code omitempty}，
     * 空时应省略。两个 fromProto 对 email 的处理必须不同，不要顺手统一。
     */
    @Test
    @DisplayName("A-02 对照：UserInfo.email 为空时不 set（Go 的 UserInfo 带 omitempty）")
    void fromProto_userInfoSkipsEmptyEmail() {
        var proto = com.rag.users.admin.proto.UserInfo.newBuilder()
                .setUid(42L).setUsername("bob").setEmail("").build();

        var dto = support.fromProto(proto);

        assertNull(dto.getEmail(), "UserInfo.email 应保持 omitempty 语义");
    }

    // ==================================================================
    // DRIFT-009：验签先于黑名单（2026-10-21 按 Go 真实顺序回退，见 issue #71186893）
    // ==================================================================

    /**
     * DRIFT-009 的核心判据（<b>方向已回退</b>）：一个"签名无效但已在黑名单中"的 token。
     *
     * <p>Go 出处（三段）：{@code rest/engine.go:101-113} bindRoute 先 appendAuthHandler(JWT)
     * 再 {@code ThenFunc(route.Handler)}；{@code rest/chain/chain.go} 的 Then = {@code m0(m1(...mN(h))}
     * 使索引 0 最外层先执行；{@code rest/server.go:222-233} WithMiddleware 在构造期把黑名单包进
     * {@code route.Handler} —— 它是 {@code ThenFunc} 的参数，比 chain 任何元素都内层。
     * ⇒ 真实顺序：<b>Authorize(JWT) → CheckBlacklist</b>。
     *
     * <p>本测试此前的断言是"Redis 被查过"（锁的是相反方向）。现在锁 Go 的方向：
     * 验签先失败 ⇒ <b>黑名单一次都不查</b>，请求在第一阶段就被拒。
     */
    @Test
    @DisplayName("DRIFT-009：验签先于黑名单 —— 无效签名时黑名单一次都不查")
    void interceptor_checksBlacklistAfterSignatureVerification() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redis.hasKey(Mockito.anyString())).thenReturn(Boolean.TRUE);
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/getself");
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer not-a-valid-jwt");

        assertThrows(AuthenticationException.class, () -> interceptor.preHandle(request, null, new Object()));

        // Go：Authorize 先失败 ⇒ CheckBlacklistMiddleware 根本不会被调用
        Mockito.verify(redis, Mockito.never()).hasKey(Mockito.anyString());
        Mockito.verify(request, Mockito.never()).setAttribute(Mockito.eq("userId"), Mockito.any());
    }

    /** 签名无效 ⇒ 401，且在验签阶段就结束、从没进入黑名单阶段。 */
    @Test
    @DisplayName("DRIFT-009：无效签名 → 401，且未进入黑名单阶段")
    void interceptor_invalidSignatureRejectedBeforeBlacklistStage() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redis.hasKey(Mockito.anyString())).thenReturn(Boolean.FALSE);
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/getself");
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer not-a-valid-jwt");

        assertThrows(AuthenticationException.class, () -> interceptor.preHandle(request, null, new Object()));

        Mockito.verify(redis, Mockito.never()).hasKey(Mockito.anyString());
    }

    /**
     * DRIFT-010（安全）：黑名单命中但请求头带了非规范前缀时，<b>不得放行</b>。
     *
     * <p>Go 出处 golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12 ——
     * {@code stripBearerPrefixFromTokenString} 在前缀不匹配时<b>原样返回整串</b>，
     * 于是 {@code "XXBearer eyJ..."} 的验签 token 是整串（必然失败 → 401）。
     *
     * <p>此前 RAG 用 {@code split(" ", 2).parts[1]} 会取出 {@code "eyJ..."} 验签通过，
     * 再用整串查黑名单必然查不到 ⇒ 已注销 token 仍可用。本用例锁死修复后的行为。
     */
    @Test
    @DisplayName("DRIFT-010：XXBearer 前缀不能绕过黑名单（Go 验整串必失败）")
    void interceptor_nonBearerPrefixCannotBypassBlacklist() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redis.hasKey(Mockito.anyString())).thenReturn(Boolean.TRUE);
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/getself");
        // 一个语法合法的 token，外层套一个不是 "Bearer " 的前缀
        Mockito.when(request.getHeader("Authorization")).thenReturn("XXBearer " + realToken());

        assertThrows(AuthenticationException.class, () -> interceptor.preHandle(request, null, new Object()));

        // 关键：既没放行（没写 userId），也没拿剥出来的 token 去过黑名单
        Mockito.verify(request, Mockito.never()).setAttribute(Mockito.eq("userId"), Mockito.any());
    }

    /** Go 出处 oauth2.go:8 的 {@code strings.ToUpper(tok[0:7]) == "BEARER "}：前缀大小写不敏感。 */
    @Test
    @DisplayName("DRIFT-010：Bearer 前缀大小写不敏感，小写 bearer 应被接受")
    void interceptor_bearerPrefixIsCaseInsensitive() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redis.hasKey(Mockito.anyString())).thenReturn(Boolean.FALSE);
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/getself");
        Mockito.when(request.getHeader("Authorization")).thenReturn("bearer " + realToken());

        // 不抛异常 ⇒ 验签通过（小写前缀被剥掉）
        interceptor.preHandle(request, null, new Object());

        // 黑名单用的是大小写【敏感】的 TrimPrefix ⇒ 查的是未剥前缀的整串（Go 自身如此）
        Mockito.verify(redis).hasKey("admin:jwt_blacklist:bearer " + realToken());
        Mockito.verify(request).setAttribute("userId", Long.valueOf(1L));
    }

    /** 与 {@link #SECRET} 同源的、语法合法的 token，claim userId = 1。 */
    private static String realToken() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, 2592000L, 1L);
    }

    /** Go 出处：CheckBlacklistMiddleware 在 header 缺失 / token 为空时直接放行，不查 Redis。 */
    @Test
    @DisplayName("DRIFT-009：Authorization 缺失时不查 Redis（Go 的 blacklist 中间件直接 next）")
    void interceptor_skipsRedisWhenHeaderAbsent() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/getself");
        Mockito.when(request.getHeader("Authorization")).thenReturn(null);

        assertThrows(AuthenticationException.class, () -> interceptor.preHandle(request, null, new Object()));

        Mockito.verify(redis, Mockito.never()).hasKey(Mockito.anyString());
    }

    /** public 路径直通，不做任何鉴权。 */
    @Test
    @DisplayName("DRIFT-009：public 路径 /admin/v1/login 直接放行")
    void interceptor_publicPathsPassThrough() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/login");

        assertTrue(interceptor.preHandle(request, null, new Object()));
        Mockito.verifyNoInteractions(redis);
    }

    /** 正常路径：合法 token 通过，写入 userId 与 jwt_token 两个 attribute。 */
    @Test
    @DisplayName("防回退：合法 token 通过并写入 userId / jwt_token")
    void interceptor_validTokenWritesAttributes() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redis.hasKey(Mockito.anyString())).thenReturn(Boolean.FALSE);
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/getself");

        String token = JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, 2592000L, 4242L);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        assertTrue(interceptor.preHandle(request, null, new Object()));

        Mockito.verify(request).setAttribute("userId", Long.valueOf(4242L));
        Mockito.verify(request).setAttribute("jwt_token", token);
    }

    /** Redis 故障时 fail-open，与 Go 的 `if err == nil && exists` 一致。 */
    @Test
    @DisplayName("防回退：Redis 故障 fail-open，不阻塞业务（Go: err != nil ⇒ 放行）")
    void interceptor_redisFailureIsFailOpen() {
        var redis = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redis.hasKey(Mockito.anyString())).thenThrow(new RuntimeException("redis down"));
        var interceptor = new AdminJwtAuthInterceptor(new AdminJwtSignatureVerifier(properties), redis);
        Mockito.when(request.getRequestURI()).thenReturn("/admin/v1/getself");

        String token = JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, 2592000L, 1L);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        assertTrue(interceptor.preHandle(request, null, new Object()),
                "Redis 故障不应阻断业务（对齐 Go 的 fail-open）");
    }

    // ==================================================================
    // 防回退：uid 解析分类（DRIFT-005 签字保留 RAG 行为，锁住不被人顺手改回）
    // ==================================================================

    /**
     * DRIFT-005 已签字：Go 侧 form 绑定把非法 uid 静默变 0 ⇒ 报 1003；
     * RAG 保留 1017（Go 的 0 值路径是绑定副作用而非业务设计）。
     * <p>本测试的作用是<b>锁定现状</b>：任何"向 Go 对齐"的改动都必须先改 AGENTS.md §3.4 的签字条目。
     */
    @Test
    @DisplayName("防回退：uid 缺失走 1017（DRIFT-005 已签字保留，不得无声改回 1003）")
    void parseUid_missingIsRequestParamError() {
        var ex = assertThrows(com.rag.common.exception.BusinessException.class,
                () -> AdminApiSupport.parseUid(null));
        assertEquals(ErrorCode.ERROR_REQUEST_PARAM.getCode(), ex.getCode());
        assertEquals(1017, ex.getCode());
    }

    @Test
    @DisplayName("防回退：uid 非数字同样走 1017")
    void parseUid_nonNumericIsRequestParamError() {
        assertThrows(com.rag.common.exception.BusinessException.class,
                () -> AdminApiSupport.parseUid("abc"));
    }

    /** 合法 uid 正常解析，且容忍首尾空白（与既有实现一致）。 */
    @Test
    @DisplayName("防回退：合法 uid 正常解析")
    void parseUid_validPasses() {
        assertEquals(12345L, AdminApiSupport.parseUid("12345"));
        assertEquals(12345L, AdminApiSupport.parseUid("  12345  "));
    }

    /** UpdateUser 的 operatorId 来自 token 而非请求体（防伪造审计身份）。 */
    @Test
    @DisplayName("防回退：updateuser 的 operatorId 取自 token，不取请求体")
    void updateUser_operatorIdComesFromToken() {
        withClaimedUid(999L);
        Mockito.when(stub.updateUser(any())).thenReturn(
                com.rag.users.admin.proto.UpdateUserResp.newBuilder().setSuccess(true)
                        .setUser(com.rag.users.admin.proto.UserInfo.newBuilder().setUid(100L))
                        .build());

        var req = new UpdateUserReq();
        req.setUid(100L);

        new AdminUpdateUserService(rpcClient, metrics, support).updateUser(req, request);

        var captor = ArgumentCaptor.forClass(com.rag.users.admin.proto.UpdateUserReq.class);
        Mockito.verify(stub).updateUser(captor.capture());
        assertEquals(999L, captor.getValue().getOperatorId());
    }
}