package com.rag.comment.comment.common.errmsg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * comment 域错误码<b>数值与顺序</b>的 1:1 守门测试。
 *
 * <p><b>为什么这些值得锁死</b>：错误码数值同时出现在 HTTP envelope 与 gRPC 上，
 * 是跨服务对外契约；改任何一条都会让已发布的客户端解析失败。
 * 而这些值「看起来像是随便写的常量」，编译器与 IDE 都不会提醒有人改错了。
 *
 * <p>期望值直接抄自 Go 真源
 * {@code /Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/comment/common/errmsg/errmsg.go}：
 * 常量块 {@code errmsg.go:37-65}。
 *
 * <p>文案的 1:1 对照在 {@code CommentErrorMessagesContractTest}，两者分开是因为
 * 「码错了」与「文案错了」的排查路径不同。
 */
class CommentErrorCodeContractTest {

    /** Go {@code errmsg.go:37-65} const 块，27 条 1:1（含 Go 原注释里的中文说明）。 */
    private static final Map<Integer, String> GO_TRUTH = new LinkedHashMap<>();

    static {
        // === 通用（Go errmsg.go:38-40）===
        GO_TRUTH.put(200, "Success");
        GO_TRUTH.put(500, "Error");
        GO_TRUTH.put(1015, "CodeServerBusy");
        // === 系统与基础设施（Go errmsg.go:41-50）===
        GO_TRUTH.put(5001, "ErrorServerCommon");
        GO_TRUTH.put(5002, "ErrorDbUpdate");
        GO_TRUTH.put(5003, "ErrorDbSelect");
        GO_TRUTH.put(5004, "ErrorDbInsert");
        GO_TRUTH.put(5005, "ErrorDbTransaction");
        GO_TRUTH.put(5006, "ErrorSnowflakeID");
        GO_TRUTH.put(5007, "ErrorJsonMarshal");
        GO_TRUTH.put(5008, "ErrorJsonUnmarshal");
        GO_TRUTH.put(5009, "ErrorKafkaPush");
        GO_TRUTH.put(5010, "ErrorDelayMsg");
        // === 用户 / 令牌（Go errmsg.go:51-61）===
        GO_TRUTH.put(1001, "ErrorUserExist");
        GO_TRUTH.put(1002, "ErrorLoginWrong");
        GO_TRUTH.put(1003, "ErrorUserNotExist");
        GO_TRUTH.put(1004, "ErrorTokenNotExist");
        GO_TRUTH.put(1005, "ErrorTokenTypeWrong");
        GO_TRUTH.put(1006, "ErrorTokenRuntime");
        GO_TRUTH.put(1007, "ErrorTokenRefresh");
        GO_TRUTH.put(1008, "ErrorUserNoRight");
        GO_TRUTH.put(1009, "ErrorUserNoLogin");
        GO_TRUTH.put(1010, "ErrorUserLogined");
        GO_TRUTH.put(1011, "ErrorUserBanned");
        // === comment 专属（Go errmsg.go:62-63）===
        GO_TRUTH.put(1012, "ErrorSubjectNotExist");
        GO_TRUTH.put(1013, "ErrorCommentNotExist");
        // === 输入校验（Go errmsg.go:64）===
        GO_TRUTH.put(2001, "ErrorInputWrong");
    }

    /** 按 {@link CommentErrorCode} 的<b>声明顺序</b>取出 (code, 枚举名)，用于与 Go 逐条对照。 */
    private static Map<Integer, String> declaredConstants() {
        Map<Integer, String> actual = new LinkedHashMap<>();
        for (CommentErrorCode c : CommentErrorCode.values()) {
            assertNull(actual.put(c.getCode(), c.name()), "错误码重复：" + c.getCode());
        }
        return actual;
    }

    @Test
    @DisplayName("错误码总数是 27 —— 骨架注释若写别的数字一律以 Go 源码为准")
    void thereAreTwentySevenCodes() {
        assertEquals(27, GO_TRUTH.size());
        assertEquals(27, CommentErrorCode.values().length);
        assertEquals(27, declaredConstants().size());
    }

    @Test
    @DisplayName("27 个码的数值与顺序与 Go 逐条 1:1（不能增、不能改、不能漏、不能重排）")
    void everyCodeMatchesGoOneByOne() {
        assertEquals(new ArrayList<>(GO_TRUTH.keySet()), new ArrayList<>(declaredConstants().keySet()),
                "码值集合或顺序与 Go 不一致");
    }

    @Test
    @DisplayName("comment 域两个专属码 1012 / 1013 都在，且互不合并")
    void commentSpecificCodesAreNotMerged() {
        // 这两个码是 comment 域独有的（like 域没有），漏掉任何一个都会让
        // Go 的「评论区不存在 / 评论不存在」在 Java 侧退化成 1015 或 5001。
        assertEquals(1012, CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode());
        assertEquals(1013, CommentErrorCode.ERROR_COMMENT_NOT_EXIST.getCode());
        // 边界：subject 与 comment 是两个独立概念，绝不能共用一个码
        assertEquals(2, List.of(CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode(),
                CommentErrorCode.ERROR_COMMENT_NOT_EXIST.getCode()).stream().distinct().count());
    }

    @Test
    @DisplayName("comment-rpc 高频码锁定（改这些会打穿 6 个 RPC 的错误还原）")
    void frequentlyUsedRpcCodesArePinned() {
        // 逐条取自 Go 调用点
        assertEquals(1015, CommentErrorCode.CODE_SERVER_BUSY.getCode());
        assertEquals(5001, CommentErrorCode.ERROR_SERVER_COMMON.getCode());
        assertEquals(2001, CommentErrorCode.ERROR_INPUT_WRONG.getCode());
        assertEquals(5003, CommentErrorCode.ERROR_DB_SELECT.getCode());
        assertEquals(5002, CommentErrorCode.ERROR_DB_UPDATE.getCode());
        assertEquals(5004, CommentErrorCode.ERROR_DB_INSERT.getCode());
        assertEquals(5009, CommentErrorCode.ERROR_KAFKA_PUSH.getCode());
        assertEquals(5006, CommentErrorCode.ERROR_SNOWFLAKE_ID.getCode());
        assertEquals(1008, CommentErrorCode.ERROR_USER_NO_RIGHT.getCode());
        assertEquals(1006, CommentErrorCode.ERROR_TOKEN_RUNTIME.getCode());
    }

    @Test
    @DisplayName("码常量表与文案表严格一一对应（没有只声明码却漏文案的）")
    void everyCodeHasAMessage() {
        assertEquals(declaredConstants().keySet(), CommentErrorMessages.MSG_BY_CODE.keySet(),
                "有码没有文案，或有文案没有码");
    }
}