package com.rag.message.message.api.controller;

import com.rag.message.message.api.dto.AdminSendNotificationReq;
import com.rag.message.message.api.dto.AdminSendNotificationResp;
import com.rag.message.message.api.dto.AdminSendChatReq;
import com.rag.message.message.api.dto.ConversationListResp;
import com.rag.message.message.api.dto.ConversationMessagesReq;
import com.rag.message.message.api.dto.ConversationMessagesResp;
import com.rag.message.message.api.dto.EmptyReq;
import com.rag.message.message.api.dto.MarkConversationReadReq;
import com.rag.message.message.api.dto.MarkNotificationReq;
import com.rag.message.message.api.dto.NotificationListResp;
import com.rag.message.message.api.dto.PageReq;
import com.rag.message.message.api.dto.SendChatResp;
import com.rag.message.message.api.dto.UnreadSummaryResp;
import com.rag.message.message.api.dto.UserSendChatReq;
import com.rag.message.message.api.service.MessageAdminGetMessagesService;
import com.rag.message.message.api.service.MessageAdminListConversationsService;
import com.rag.message.message.api.service.MessageAdminMarkConversationReadService;
import com.rag.message.message.api.service.MessageAdminSendChatService;
import com.rag.message.message.api.service.MessageAdminSendNotificationService;
import com.rag.users.common.http.GoStyleRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧会话 / 通知端点 —— 对应 Go {@code routes.go} 第二段
 * {@code server.AddRoutes(..., rest.WithJwt(AdminAuth.AccessSecret), rest.WithPrefix("/message/v1/admin"))}。
 *
 * <p><b>5 个端点与 HTTP 方法逐条对齐</b>（Go {@code routes.go} 第二段）：
 * <pre>
 *   POST /message/v1/admin/conversations/list       ← ListAdminConversationsHandler
 *   POST /message/v1/admin/conversations/messages   ← GetAdminConversationMessagesHandler
 *   POST /message/v1/admin/conversations/read       ← MarkAdminConversationReadHandler
 *   POST /message/v1/admin/conversations/send       ← SendAdminChatMessageHandler
 *   POST /message/v1/admin/notifications/send       ← SendAdminNotificationHandler
 * </pre>
 *
 * <p><b>鉴权</b>：本前缀由 {@code MessageAdminJwtAuthInterceptor} 单独守卫
 * （user secret 在这里会被拒）。业务身份（操作人 id）由
 * {@code MessageAdmin*Service} 从 JWT claim 解析后作为 {@code SenderId}/{@code OperatorId} 下发，
 * <b>不</b>来自请求体 —— Go 侧同样只从 {@code ctx.Value("userId")} 取。
 *
 * <p><b>{@code @RequestBody(required = false)} 是必需写法，不是省事</b>：
 * Go 的 {@code handler.Entries()} 一定会调 {@code httpx.Parse}，
 * 非法 JSON 会让解析失败 → 本服务输出 {@code 200 + {code:5001}}。
 * 若声明成 {@code required = true}，Spring 会抛
 * {@code HttpMessageNotReadableException}（本服务同样映射到 5001，形状一致）；
 * 若<b>完全不声明</b> {@code @RequestBody}，Spring 根本不读 body，
 * 畸形 JSON 就会被静默忽略、请求照常成功 —— 那是凭空多出一条 Go 没有的路径。
 */
@RestController
@RequestMapping("/message/v1/admin")
public class MessageAdminController {

    private final MessageAdminListConversationsService adminListConversationsService;
    private final MessageAdminGetMessagesService adminGetMessagesService;
    private final MessageAdminMarkConversationReadService adminMarkConversationReadService;
    private final MessageAdminSendChatService adminSendChatService;
    private final MessageAdminSendNotificationService adminSendNotificationService;

    public MessageAdminController(MessageAdminListConversationsService adminListConversationsService,
                                  MessageAdminGetMessagesService adminGetMessagesService,
                                  MessageAdminMarkConversationReadService adminMarkConversationReadService,
                                  MessageAdminSendChatService adminSendChatService,
                                  MessageAdminSendNotificationService adminSendNotificationService) {
        this.adminListConversationsService = adminListConversationsService;
        this.adminGetMessagesService = adminGetMessagesService;
        this.adminMarkConversationReadService = adminMarkConversationReadService;
        this.adminSendChatService = adminSendChatService;
        this.adminSendNotificationService = adminSendNotificationService;
    }

    // ============== 会话 ==============

    /**
     * 会话列表 —— Go {@code types.go PageReq}（3 个字段全部 {@code ,optional}）。
     */
    @PostMapping("/conversations/list")
    public ConversationListResp listConversations(@RequestBody(required = false) PageReq req,
                                                  HttpServletRequest httpRequest) {
        return adminListConversationsService.listAdminConversations(zeroIfNull(req), httpRequest);
    }

    /**
     * 会话消息列表 —— Go {@code ConversationMessagesReq}，
     * {@code conversation_id} 必填且 {@code ,string}。
     */
    @PostMapping("/conversations/messages")
    public ConversationMessagesResp getConversationMessages(
            @RequestBody(required = false) ConversationMessagesReq req,
            HttpServletRequest httpRequest) {
        return adminGetMessagesService.getAdminConversationMessages(
                GoStyleRequest.normalize(req, ConversationMessagesReq::new), httpRequest);
    }

    /**
     * 标记会话已读 —— Go {@code MarkConversationReadReq}，{@code conversation_id} 必填。
     */
    @PostMapping("/conversations/read")
    public EmptyReq markConversationRead(@RequestBody(required = false) MarkConversationReadReq req,
                                         HttpServletRequest httpRequest) {
        return adminMarkConversationReadService.markAdminConversationRead(
                GoStyleRequest.normalize(req, MarkConversationReadReq::new), httpRequest);
    }

    /**
     * 管理员发私信 —— Go {@code AdminSendChatReq}，
     * {@code recipient_id} 与 {@code content} 必填。
     */
    @PostMapping("/conversations/send")
    public SendChatResp sendChat(@RequestBody(required = false) AdminSendChatReq req,
                                 HttpServletRequest httpRequest) {
        return adminSendChatService.sendAdminChatMessage(
                GoStyleRequest.normalize(req, AdminSendChatReq::new), httpRequest);
    }

    // ============== 通知 ==============

    /**
     * 管理员发通知 —— Go {@code AdminSendNotificationReq}，{@code title} / {@code content} 必填，
     * {@code extra} 需要 presence 语义（缺席 vs 显式 null 结局相反）。
     */
    @PostMapping("/notifications/send")
    public AdminSendNotificationResp sendNotification(
            @RequestBody(required = false) AdminSendNotificationReq req,
            HttpServletRequest httpRequest) {
        return adminSendNotificationService.sendAdminNotification(
                GoStyleRequest.normalize(req, AdminSendNotificationReq::new), httpRequest);
    }

    /**
     * 无必填字段的 DTO 归一化 —— 空 body 与 {@code {}} 在 Go 侧等价（零值 struct）。
     *
     * <p>{@code PageReq} 不实现 {@code GoStyleRequiredFields}（它零必填字段），
     * 因此不能走 {@link GoStyleRequest#normalize}，这里手写等价的零值归一。
     */
    private static PageReq zeroIfNull(PageReq req) {
        return req == null ? new PageReq() : req;
    }
}