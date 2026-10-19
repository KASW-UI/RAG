package com.rag.users.user.api.dto;

/**
 * deleteUser 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 DeleteUserReq（空 struct）。
 *
 * uid 由 Interceptor 从 JWT claim 取出。
 */
public class DeleteUserReq {
}