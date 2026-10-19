package com.rag.users.user.api.dto;

/**
 * getAvatarHistory 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go（空 struct + query param limit）。
 *
 * uid 由 Interceptor 从 JWT claim 取出；
 * limit 由 Controller 从 query param 读，DRIFT-016 决策：硬编码 30（与 Go 端一致，不配置化）。
 *
 * 用户 limit 走 query param 还是 body，由 Controller 层决定（这里只承载 dto 字段）。
 */
public class GetAvatarHistoryReq {
}