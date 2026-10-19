package com.rag.users.user.api.dto;

/**
 * logout 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 LogoutReq（空 struct）。
 *
 * token 不在 body 里 —— 由 JwtAuthInterceptor 从 Authorization Header 解析后写入
 * request attribute "jwt_token"，LogoutService 从那里取（与 Go 端 ctx.Value("jwt_token") 等价）。
 */
public class LogoutReq {
}