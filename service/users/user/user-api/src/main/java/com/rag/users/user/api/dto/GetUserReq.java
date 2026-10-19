package com.rag.users.user.api.dto;

/**
 * getUser 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 GetUserReq（空 struct）。
 *
 * uid 不在 body 里 —— 由 JwtAuthInterceptor 从 JWT claim "userId" 取出，写入
 * request attribute "userId"，GetUserService 从那里取（与 Go 端 ctx.Value("userId") 等价）。
 */
public class GetUserReq {
}