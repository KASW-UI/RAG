package com.rag.message.message.api.interceptor;

import com.rag.message.message.api.auth.MessageJwtSignatureVerifier;
import org.springframework.stereotype.Component;

/**
 * 管理员侧 JWT 鉴权拦截器 —— 挂 {@code /message/v1/admin/**}。
 *
 * <p><b>Go 出处</b>：{@code routes.go} 第二段
 * {@code server.AddRoutes([...5 个 admin 路由...], rest.WithJwt(serverCtx.Config.AdminAuth.AccessSecret),
 * rest.WithPrefix("/message/v1/admin"))}。
 *
 * <p><b>路径集合逐条对齐</b>：
 * <pre>
 *   POST /message/v1/admin/conversations/list
 *   POST /message/v1/admin/conversations/messages
 *   POST /message/v1/admin/conversations/read
 *   POST /message/v1/admin/conversations/send
 *   POST /message/v1/admin/notifications/send
 * </pre>
 *
 * <p><b>与 user 侧共用一份鉴权逻辑</b>（{@link AbstractMessageJwtAuthInterceptor}），
 * 但<b>密钥不同</b> —— 两段路由在 Go 侧也是两次独立的 {@code rest.WithJwt}。
 * 后果：拿 user token 打 admin 端点会验签失败 → <b>401</b>（Go 同样如此），
 * 反之亦然。这条「两套密钥互不通用」是 Go 的既有语义，<b>不要</b>为了方便合成一份密钥。
 *
 * <p><b>密钥补齐（N-5 留档）</b>：Go 的 admin secret 是 15 字节
 * （{@code yU-hCm)2*?NQmWG}），golang-jwt 不校验长度；jjwt 0.12 强制 ≥256 bit，
 * 故统一由 {@link MessageJwtSignatureVerifier} 用
 * {@code JwtUtil.padToHmacKeySize} zero-pad 到 32 字节。
 * 代价是 <b>Go 签的 admin token 在 RAG 验不过，反之亦然</b>（两套部署各自闭环）。
 * 这是运行环境约束，不是行为差异 —— token 的 payload / claim / 过期语义完全一致。
 *
 * <p><b>🔴 DRIFT-MESSAGEAPI-009②</b>：本类现在只负责<b>消费</b>验签结果与抛 401；
 * 验签本身在 {@link com.rag.message.message.api.auth.MessageJwtAuthFilter}
 * （{@code @Order(+6)}）里完成 ⇒ 验签在 3s 预算**之外**。
 */
@Component
public class MessageAdminJwtAuthInterceptor extends AbstractMessageJwtAuthInterceptor {

    public MessageAdminJwtAuthInterceptor(MessageJwtSignatureVerifier verifier) {
        super(MessageJwtSignatureVerifier.RouteGroup.ADMIN, verifier);
    }
}