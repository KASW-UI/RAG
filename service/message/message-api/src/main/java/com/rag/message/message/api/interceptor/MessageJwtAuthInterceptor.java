package com.rag.message.message.api.interceptor;

import com.rag.message.message.api.auth.MessageJwtSignatureVerifier;
import org.springframework.stereotype.Component;

/**
 * 用户侧 JWT 鉴权拦截器 —— 挂 {@code /message/v1/**}（不含 {@code /admin/**}）。
 *
 * <p><b>Go 出处</b>：{@code routes.go} 第一段
 * {@code server.AddRoutes([...8 个 user 路由...], rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 * rest.WithPrefix("/message/v1"))}。
 *
 * <p><b>路径集合逐条对齐</b>（Go 侧 8 个，全部在本拦截器覆盖范围内）：
 * <pre>
 *   POST /message/v1/conversations/list
 *   POST /message/v1/conversations/messages
 *   POST /message/v1/conversations/read
 *   POST /message/v1/conversations/send
 *   POST /message/v1/notifications/list
 *   POST /message/v1/notifications/read
 *   POST /message/v1/notifications/readall
 *   GET  /message/v1/unread            ← 唯一非 POST 的端点
 * </pre>
 * 与 Go 的差别只有两点，都是表达层：
 * <ol>
 *   <li>Go 用 {@code rest.WithPrefix} + 相对 path 拼出完整路径，Spring 用
 *       {@code @RequestMapping("/message/v1")} + 类内相对 path；</li>
 *   <li>Go 的 path 匹配由 go-zero router 做，Spring 由 DispatcherServlet 做。
 *       两者都是「前缀 + 精确段」匹配，{@code /message/v1/admin/conversations/list}
 *       在 Go 侧<b>不</b>匹配第一段的 {@code /conversations/list}（prefix 是完整的
 *       {@code /message/v1} + 相对 path），所以 user 拦截器必须显式 exclude admin 前缀 ——
 *       见 {@code WebMvcConfig}。</li>
 * </ol>
 *
 * <p><b>密钥补齐</b>：Go 侧 user secret 是 36 字节 UUID（≥32），jjwt 也要求 ≥32 字节，
 * 因此 {@code JwtUtil.padToHmacKeySize} 对它其实是 no-op；统一调用是为了与 admin 侧共用同一份
 * 逻辑（admin secret 只有 15 字节，必须补齐）。详见 N-5 留档。
 *
 * <p><b>🔴 DRIFT-MESSAGEAPI-009②</b>：本类现在只负责<b>消费</b>验签结果与抛 401；
 * 验签本身在 {@link com.rag.message.message.api.auth.MessageJwtAuthFilter}
 * （{@code @Order(+6)}）里完成 ⇒ 验签在 3s 预算**之外**。
 */
@Component
public class MessageJwtAuthInterceptor extends AbstractMessageJwtAuthInterceptor {

    public MessageJwtAuthInterceptor(MessageJwtSignatureVerifier verifier) {
        super(MessageJwtSignatureVerifier.RouteGroup.USER, verifier);
    }
}