package com.rag.message.message.common.errmsg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code MessageErrorMessages} / {@code MessageErrorCode} 与 Go 真源的逐条对照测试。
 *
 * <p><b>Go 出处</b>：{@code Sea-RideTheWind_golang/service/message/common/errmsg.go}
 * 的 {@code const ( ... )} 块（:27-49）与 {@code var codeMsg = map[int]string}（:51-73）。
 *
 * <p><b>为什么这层必须有测试（2026-10-23 补建）</b>：本模块在补建之前
 * {@code src/test} 目录<b>根本不存在</b>，而 {@code message-api} 的
 * {@code MessageApiSupport#rpcError} javadoc 却写着「message-common 已有 focused tests」——
 * 一句<b>实测为假</b>的断言（§3.3.2）。错误码表是 wire 契约：
 * 任何一条 msg 改错字、任何一条 code 改数值，客户端拿到的 {@code code}/{@code msg} 就变了，
 * 而这种改动<b>编译得过、全量测试照样绿</b>。
 *
 * <p>⚠️ 本类只断言「表本身与 Go 一致」。<b>反查与映射</b>在 {@link MessageGrpcErrorTest}。
 */
class MessageErrorMessagesTest {

    /** Go errmsg.go:27-49 的 const 块，逐条（名称 → 数值）。 */
    @Test
    @DisplayName("错误码常量逐条等于 Go const 块（20 条，数值不可变）")
    void errorCodesMatchGoConstants() {
        assertEquals(200, MessageErrorCode.SUCCESS.getCode());
        assertEquals(500, MessageErrorCode.ERROR.getCode());
        assertEquals(1015, MessageErrorCode.CODE_SERVER_BUSY.getCode());
        assertEquals(5001, MessageErrorCode.ERROR_SERVER_COMMON.getCode());
        assertEquals(5002, MessageErrorCode.ERROR_NOT_FOUND.getCode());
        assertEquals(5003, MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        assertEquals(5004, MessageErrorCode.ERROR_UNAUTHORIZED.getCode());
        assertEquals(5005, MessageErrorCode.ERROR_FORBIDDEN.getCode());
        assertEquals(5006, MessageErrorCode.ERROR_ALREADY_EXIST.getCode());
        assertEquals(5007, MessageErrorCode.ERROR_DB_SELECT.getCode());
        assertEquals(5008, MessageErrorCode.ERROR_DB_UPDATE.getCode());
        assertEquals(5101, MessageErrorCode.ERROR_USER_NOT_FOUND.getCode());
        assertEquals(5102, MessageErrorCode.ERROR_ADMIN_NOT_FOUND.getCode());
        assertEquals(5201, MessageErrorCode.ERROR_SELF_CHAT.getCode());
        assertEquals(5202, MessageErrorCode.ERROR_CONVERSATION_MISS.getCode());
        assertEquals(5203, MessageErrorCode.ERROR_NOTIFICATION_MISS.getCode());
        assertEquals(5204, MessageErrorCode.ERROR_CHAT_PENDING.getCode());
        assertEquals(5205, MessageErrorCode.ERROR_ADMIN_ONLY_INIT.getCode());
        assertEquals(5206, MessageErrorCode.ERROR_FIRST_MSG_TOO_LONG.getCode());
        assertEquals(5207, MessageErrorCode.ERROR_UNSUPPORTED_ROLE.getCode());

        assertEquals(20, MessageErrorCode.values().length,
                "Go 侧 codeMsg 恰好 20 条（errmsg.go:52-72）；多一条就是多了一条 Go 没有的码");
    }

    /** Go errmsg.go:51-73 的 codeMsg map，逐条 code → msg。 */
    @Test
    @DisplayName("code → msg 逐条等于 Go codeMsg（文案逐字节，wire 可见）")
    void messagesMatchGoTable() {
        assertEquals("OK", MessageErrorMessages.getMsg(200));
        assertEquals("FAIL", MessageErrorMessages.getMsg(500));
        assertEquals("service busy", MessageErrorMessages.getMsg(1015));
        assertEquals("internal server error", MessageErrorMessages.getMsg(5001));
        assertEquals("record not found", MessageErrorMessages.getMsg(5002));
        assertEquals("invalid parameter", MessageErrorMessages.getMsg(5003));
        assertEquals("unauthorized", MessageErrorMessages.getMsg(5004));
        assertEquals("forbidden", MessageErrorMessages.getMsg(5005));
        assertEquals("already exists", MessageErrorMessages.getMsg(5006));
        assertEquals("database query failed", MessageErrorMessages.getMsg(5007));
        assertEquals("database update failed", MessageErrorMessages.getMsg(5008));
        assertEquals("user not found", MessageErrorMessages.getMsg(5101));
        assertEquals("admin not found", MessageErrorMessages.getMsg(5102));
        assertEquals("self chat is not allowed", MessageErrorMessages.getMsg(5201));
        assertEquals("conversation not found", MessageErrorMessages.getMsg(5202));
        assertEquals("notification not found", MessageErrorMessages.getMsg(5203));
        assertEquals("waiting for the other side to reply", MessageErrorMessages.getMsg(5204));
        assertEquals("only admin can initiate this conversation", MessageErrorMessages.getMsg(5205));
        assertEquals("first private message cannot exceed 50 characters", MessageErrorMessages.getMsg(5206));
        assertEquals("unsupported sender role", MessageErrorMessages.getMsg(5207));
    }

    /**
     * Go {@code GetErrMsg}：查不到就回 {@code codeMsg[Error]}，也就是 {@code "FAIL"}，
     * <b>不是 null、不是抛异常</b>。
     * <p>这条决定了「未知码」在 envelope 上的最终长相，落错了会让所有未知错误都变成空 msg。
     */
    @Test
    @DisplayName("未知 code 回落到 FAIL（Go GetErrMsg 的兜底，不是 null）")
    void unknownCodeFallsBackToFail() {
        assertEquals("FAIL", MessageErrorMessages.getMsg(0));
        assertEquals("FAIL", MessageErrorMessages.getMsg(-1));
        assertEquals("FAIL", MessageErrorMessages.getMsg(9999));
        // 边界：与已定义码相邻但未定义的整数
        assertEquals("FAIL", MessageErrorMessages.getMsg(5009));
        assertEquals("FAIL", MessageErrorMessages.getMsg(1016));
    }

    /**
     * 反查表 {@code bizCodeByMsg} 的前提是 <b>msg 全局唯一</b>。
     * <p>Go 侧是 {@code if _, exists := lookup[msg]; !exists} 的 first-write-wins，
     * 而 Go 的 map 遍历顺序<b>随机</b> ⇒ 一旦有重复 msg，Go 侧结果就是<b>不确定</b>的。
     * 本测试把「当前无重复」这个前提钉死：将来有人加了一条重复 msg，
     * 应当在这里变红并重新决策，而不是让两侧随机遇不同。
     */
    @Test
    @DisplayName("全部 msg 唯一（反查表 1:1 的前提；重复会让 Go 侧行为不确定）")
    void allMessagesAreUnique() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MessageErrorCode c : MessageErrorCode.values()) {
            String msg = MessageErrorMessages.getMsg(c.getCode());
            org.junit.jupiter.api.Assertions.assertTrue(seen.add(msg),
                    "msg 重复：" + c.name() + " 与前一条共用 \"" + msg + "\"，"
                            + "Go 的 bizCodeByMsg 对重复 msg 的取值依赖随机 map 顺序");
        }
        assertEquals(MessageErrorCode.values().length, seen.size());
    }

    /** {@link MessageErrorCode} 与常量表都要能被反射到数值（enum 的 getCode 是唯一入口）。 */
    @Test
    @DisplayName("getCode 是唯一数值出口，序列化按数值不按名称")
    void getCodeReturnsPrimitiveInt() throws Exception {
        var m = MessageErrorCode.class.getMethod("getCode");
        assertEquals(int.class, m.getReturnType());
    }
}