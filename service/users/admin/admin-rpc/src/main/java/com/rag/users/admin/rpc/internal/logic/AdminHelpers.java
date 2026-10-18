package com.rag.users.admin.rpc.internal.logic;

/**
 * 业务逻辑共享的字符串归一化工具。
 * 对应 Go 端 internal/logic/helper.go：
 *   func optionalString(value string) *string
 *   func stringValue(value *string) string
 *
 * 用法场景：
 *   - CreateAdminLogic 用 optionalString 转换 req.Email：trimSpace + 空字符串 → null
 *     （与 Go 端 *string 可空语义对齐）
 *   - GetSelfLogic / GetUserLogic 用 stringValue 把 *string 转默认空字符串
 *
 * Java 侧用包装类型 String 表示 nullable（null = 没值，"..." = 有值），与 Go 端 *string 语义对齐。
 * 与 user-rpc 端 AvatarHelpers.java 命名和组织方式保持一致。
 */
public final class AdminHelpers {

    private AdminHelpers() {}

    /**
     * 对应 Go 端 optionalString：
     *   trimmed := strings.TrimSpace(value)
     *   if trimmed == "" { return nil }
     *   return &trimmed
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
     *   if value == nil { return "" }
     *   return *value
     */
    public static String stringValue(String value) {
        return value == null ? "" : value;
    }
}