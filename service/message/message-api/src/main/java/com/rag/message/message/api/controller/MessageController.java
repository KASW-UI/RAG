package com.rag.message.message.api.controller;

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
import com.rag.message.message.api.service.MessageGetMessagesService;
import com.rag.message.message.api.service.MessageGetUnreadSummaryService;
import com.rag.message.message.api.service.MessageListConversationsService;
import com.rag.message.message.api.service.MessageListNotificationsService;
import com.rag.message.message.api.service.MessageMarkAllNotificationsReadService;
import com.rag.message.message.api.service.MessageMarkConversationReadService;
import com.rag.message.message.api.service.MessageMarkNotificationReadService;
import com.rag.message.message.api.service.MessageSendUserChatService;
import com.rag.users.common.http.GoStyleRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧会话 / 通知端点 —— 对应 Go {@code routes.go} 第一段
 * {@code server.AddRoutes(..., rest.WithJwt(UserAuth.AccessSecret), rest.WithPrefix("/message/v1"))}。
 *
 * <p><b>8 个端点逐条对齐</b>（Go {@code routes.go} 第一段）：
 * <pre>
 *   POST /message/v1/conversations/list        ← ListConversationsHandler
 *   POST /message/v1/conversations/messages    ← GetConversationMessagesHandler
 *   POST /message/v1/conversations/read        ← MarkConversationReadHandler
 *   POST /message/v1/conversations/send        ← SendUserChatMessageHandler
 *   POST /message/v1/notifications/list        ← ListNotificationsHandler
 *   POST /message/v1/notifications/read        ← MarkNotificationReadHandler
 *   POST /message/v1/notifications/readall     ← MarkAllNotificationsReadHandler
 *   GET  /message/v1/unread                    ← GetUnreadSummaryHandler    ← 唯一的 GET
 * </pre>
 *
 * <p><b>GET /unread 的 body 处理</b>：Go 端 handler 仍然调 {@code httpx.Parse}，
 * 即便 {@code EmptyReq} 零字段；为了让 Java 端能复现「畸形 JSON 也会被拒」，
 * 这里显式声明 {@code @RequestBody(required = false) EmptyReq}，否则 Spring
 * 不读 body，等于凭空多一条 Go 没有的「畸形 JSON 仍成功」路径。
 */
@RestController
@RequestMapping("/message/v1")
public class MessageController {

    private final MessageListConversationsService listConversationsService;
    private final MessageGetMessagesService getMessagesService;
    private final MessageMarkConversationReadService markConversationReadService;
    private final MessageSendUserChatService sendUserChatService;
    private final MessageListNotificationsService listNotificationsService;
    private final MessageMarkNotificationReadService markNotificationReadService;
    private final MessageMarkAllNotificationsReadService markAllNotificationsReadService;
    private final MessageGetUnreadSummaryService getUnreadSummaryService;

    public MessageController(MessageListConversationsService listConversationsService,
                             MessageGetMessagesService getMessagesService,
                             MessageMarkConversationReadService markConversationReadService,
                             MessageSendUserChatService sendUserChatService,
                             MessageListNotificationsService listNotificationsService,
                             MessageMarkNotificationReadService markNotificationReadService,
                             MessageMarkAllNotificationsReadService markAllNotificationsReadService,
                             MessageGetUnreadSummaryService getUnreadSummaryService) {
        this.listConversationsService = listConversationsService;
        this.getMessagesService = getMessagesService;
        this.markConversationReadService = markConversationReadService;
        this.sendUserChatService = sendUserChatService;
        this.listNotificationsService = listNotificationsService;
        this.markNotificationReadService = markNotificationReadService;
        this.markAllNotificationsReadService = markAllNotificationsReadService;
        this.getUnreadSummaryService = getUnreadSummaryService;
    }

    // ============== 会话 ==============

    @PostMapping("/conversations/list")
    public ConversationListResp listConversations(@RequestBody(required = false) PageReq req,
                                                  HttpServletRequest httpRequest) {
        return listConversationsService.listConversations(zeroIfNull(req), httpRequest);
    }

    @PostMapping("/conversations/messages")
    public ConversationMessagesResp getConversationMessages(
            @RequestBody(required = false) ConversationMessagesReq req,
            HttpServletRequest httpRequest) {
        return getMessagesService.getConversationMessages(
                GoStyleRequest.normalize(req, ConversationMessagesReq::new), httpRequest);
    }

    @PostMapping("/conversations/read")
    public EmptyReq markConversationRead(@RequestBody(required = false) MarkConversationReadReq req,
                                         HttpServletRequest httpRequest) {
        return markConversationReadService.markConversationRead(
                GoStyleRequest.normalize(req, MarkConversationReadReq::new), httpRequest);
    }

    @PostMapping("/conversations/send")
    public SendChatResp sendChat(@RequestBody(required = false) UserSendChatReq req,
                                 HttpServletRequest httpRequest) {
        return sendUserChatService.sendUserChatMessage(
                GoStyleRequest.normalize(req, UserSendChatReq::new), httpRequest);
    }

    // ============== 通知 ==============

    @PostMapping("/notifications/list")
    public NotificationListResp listNotifications(@RequestBody(required = false) PageReq req,
                                                 HttpServletRequest httpRequest) {
        return listNotificationsService.listNotifications(zeroIfNull(req), httpRequest);
    }

    @PostMapping("/notifications/read")
    public EmptyReq markNotificationRead(@RequestBody(required = false) MarkNotificationReq req,
                                         HttpServletRequest httpRequest) {
        return markNotificationReadService.markNotificationRead(
                GoStyleRequest.normalize(req, MarkNotificationReq::new), httpRequest);
    }

    @PostMapping("/notifications/readall")
    public EmptyReq markAllNotificationsRead(
            @RequestBody(required = false) EmptyReq req,
            HttpServletRequest httpRequest) {
        // req 可能是 null（空 body），但 EmptyReq 零字段 → 零校验，结果无差异。
        // @RequestBody 仍然必须声明，否则畸形 body 不会被解析。
        return markAllNotificationsReadService.markAllNotificationsRead(httpRequest);
    }

    @GetMapping("/unread")
    public UnreadSummaryResp unread(
            @RequestBody(required = false) EmptyReq req,
            HttpServletRequest httpRequest) {
        return getUnreadSummaryService.getUnreadSummary(httpRequest);
    }

    private static PageReq zeroIfNull(PageReq req) {
        return req == null ? new PageReq() : req;
    }
}