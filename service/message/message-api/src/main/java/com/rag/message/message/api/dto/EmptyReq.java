package com.rag.message.message.api.dto;

/**
 * 空请求 / 空响应 —— 对应 Go {@code types.go} 的 {@code type EmptyReq struct{}}。
 *
 * <p><b>双向用途</b>（Go 侧同一个类型就是这样用的）：
 * <table border="1">
 *   <caption>EmptyReq 的两个方向</caption>
 *   <tr><th>方向</th><th>Go 用法</th><th>本 DTO</th></tr>
 *   <tr>
 *     <td>请求</td>
 *     <td>{@code GetUnreadSummaryHandler} / {@code MarkAllNotificationsReadHandler}
 *         都 {@code var req types.EmptyReq}</td>
 *     <td>Controller 的 {@code @RequestBody(required = false)} 入参</td>
 *   </tr>
 *   <tr>
 *     <td>响应</td>
 *     <td>{@code return &types.EmptyReq{}, nil}（markConversationRead /
 *         markNotificationRead / markAllNotificationsRead 三个端点）</td>
 *     <td>Service 的返回值 → 被包成 {@code data:{}}</td>
 *   </tr>
 * </table>
 *
 * <p><b>注意响应是 {@code {}} 而不是 {@code null}</b>：Go 返回的是非 nil 的空 struct 指针，
 * {@code encoding/json} 序列化成 {@code {}}。若 Java 侧返回 {@code null}，
 * 客户端会看到 {@code "data":null} —— 契约就变了。
 *
 * <p>本类<b>零字段 ⇒ 零必填校验</b>，因此不实现 {@code GoStyleRequiredFields}：
 * 没有字段可断言，实现了也是空转。
 */
public class EmptyReq {
}
