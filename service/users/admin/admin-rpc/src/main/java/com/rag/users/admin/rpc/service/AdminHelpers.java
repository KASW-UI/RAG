package com.rag.users.admin.rpc.service;

/**
 * 业务逻辑共享的字符串归一化工具 —— 与 user-rpc 端 AvatarHelpers.java / user-api 端
 * AdminHelpers.java 同款位置。
 *
 * <p>对应 Go 端 internal/logic/helper.go：
 * <pre>
 *   func optionalString(value string) *string
 *   func stringValue(value *string) string
 * </pre>
 *
 * <p>Java 侧用包装类型 {@link String} 表示 nullable（{@code null} = 没值，{@code ""} = 有值），
 * 与 Go 端 {@code *string} 语义对齐。
 */
public final class AdminHelpers {

    private AdminHelpers() {}

    /**
     * 对应 Go 端 optionalString：
     * <pre>
     *   trimmed := strings.TrimSpace(value)
     *   if trimmed == "" { return nil }
     *   return &trimmed
     * </pre>
     */
    public static String optionalString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 对应 Go 端 stringValue：
     * <pre>
     *   if value == nil { return "" }
     *   return *value
     * </pre>
     */
    public static String stringValue(String value) {
        return value == null ? "" : value;
    }
}
