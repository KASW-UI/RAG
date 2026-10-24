package com.rag.comment.comment.common.errmsg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * comment 域错误码<b>文案与业务错误载体</b>的 1:1 守门测试。
 *
 * <p>期望值直接抄自 Go 真源
 * {@code /Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/comment/common/errmsg/errmsg.go}：
 * 文案表 {@code errmsg.go:67-95}、回退逻辑 {@code errmsg.go:97-103}、
 * 错误载体与工厂 {@code errmsg.go:10-31}。
 */
class CommentErrorMessagesContractTest {

    /** Go {@code errmsg.go:67-95} codeMsg 整张表，27 条 1:1。 */
    private static final Map<Integer, String> GO_TRUTH = new LinkedHashMap<>();

    static {
        // === 通用（Go errmsg.go:68-70）===
        GO_TRUTH.put(200, "OK");
        GO_TRUTH.put(500, "FAIL");
        GO_TRUTH.put(1015, "服务繁忙");
        // === 系统与基础设施（Go errmsg.go:71-80）===
        GO_TRUTH.put(5001, "系统内部错误");
        GO_TRUTH.put(5002, "更新数据库失败");
        GO_TRUTH.put(5003, "查询数据库失败");
        GO_TRUTH.put(5004, "数据库插入失败");
        GO_TRUTH.put(5005, "事务操作失败");
        GO_TRUTH.put(5006, "雪花ID生成失败");
        GO_TRUTH.put(5007, "JSON序列化失败");
        GO_TRUTH.put(5008, "JSON反序列化失败");
        GO_TRUTH.put(5009, "Kafka消息发送失败");
        GO_TRUTH.put(5010, "延时消息发送失败");
        // === 用户 / 令牌（Go errmsg.go:81-91）===
        GO_TRUTH.put(1001, "用户名已存在");
        GO_TRUTH.put(1002, "用户名或密码错误");
        GO_TRUTH.put(1003, "用户不存在");
        GO_TRUTH.put(1004, "TOKEN不存在");
        GO_TRUTH.put(1005, "TOKEN格式错误");
        GO_TRUTH.put(1006, "TOKEN已过期");
        GO_TRUTH.put(1007, "TOKEN刷新失败");
        GO_TRUTH.put(1008, "权限不足");
        GO_TRUTH.put(1009, "未登录");
        GO_TRUTH.put(1010, "已登录");
        GO_TRUTH.put(1011, "用户已被封禁");
        // === comment 专属（Go errmsg.go:92-93）===
        GO_TRUTH.put(1012, "评论区不存在");
        GO_TRUTH.put(1013, "评论不存在");
        // === 输入校验（Go errmsg.go:94）===
        GO_TRUTH.put(2001, "输入有误");
    }

    @Test
    @DisplayName("27 条文案与 Go 逐条 1:1")
    void everyMessageMatchesGoOneByOne() {
        assertEquals(27, CommentErrorMessages.MSG_BY_CODE.size());
        GO_TRUTH.forEach((code, msg) ->
                assertEquals(msg, CommentErrorMessages.getMsg(code), "code=" + code + " 的文案漂移了"));
    }

    @Test
    @DisplayName("comment 域两个专属文案锁定：「评论区不存在」≠「评论不存在」")
    void subjectAndCommentMessagesDiffer() {
        assertEquals("评论区不存在", CommentErrorMessages.getMsg(1012));
        assertEquals("评论不存在", CommentErrorMessages.getMsg(1013));
        // 边界：这两个文案只差 3 个字，最容易被"合并顺手改错"，单独钉一次
        assertFalse("评论区不存在".equals(CommentErrorMessages.getMsg(1013)));
    }

    @Test
    @DisplayName("未知码回退到 \"FAIL\"，不是 null、不是空串 —— 对齐 Go GetErrMsg")
    void unknownCodeFallsBackToFail() {
        assertEquals("FAIL", CommentErrorMessages.getMsg(999_999));
        assertEquals("FAIL", CommentErrorMessages.getMsg(-1));
        assertEquals("FAIL", CommentErrorMessages.getMsg(0));
        assertEquals("FAIL", CommentErrorMessages.getMsg(3));
        // 边界：500 本身是已知码，返回自己的 msg
        assertEquals("FAIL", CommentErrorMessages.getMsg(500));
        assertEquals("OK", CommentErrorMessages.getMsg(200));
    }

    @Test
    @DisplayName("文案表不可被外部篡改")
    void msgTableIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> CommentErrorMessages.MSG_BY_CODE.put(12345, "篡改"));
    }

    @Test
    @DisplayName("getMessage() 与 Go Error() 逐字节相同")
    void codeErrorMessageFormatMatchesGo() {
        // Go: fmt.Sprintf("ErrCode:%d, Errmsg:%s", e.Code, e.Msg)
        assertEquals("ErrCode:5003, Errmsg:DB查询失败",
                new CommentCodeError(5003, "DB查询失败").getMessage());
        assertEquals("ErrCode:1012, Errmsg:评论区不存在",
                new CommentCodeError(1012, "评论区不存在").getMessage());
        assertEquals("ErrCode:1013, Errmsg:评论不存在",
                new CommentCodeError(1013, "评论不存在").getMessage());
    }

    @Test
    @DisplayName("newCode 查表、newCodeMsg 不查表 —— 这是 Go 的区分，必须保留")
    void factoriesDifferOnLookup() {
        // Go NewErrCode(5003) → msg = GetErrMsg(5003) = "查询数据库失败"
        assertEquals("查询数据库失败", CommentCodeError.newCode(5003).getMsg());
        // Go NewErrCodeMsg(5003, "DB查询失败") → msg 原样，不能被表覆盖
        assertEquals("DB查询失败", CommentCodeError.newCodeMsg(5003, "DB查询失败").getMsg());
        // comment-rpc 的真实用法：全部走 NewGrpcErr(code, 自定义 msg)，
        // 落到 api 层就是 NewErrCodeMsg(int(st.Code()), st.Message())（见 Go 各 logic:52-55）
        assertEquals("非up主无法评论", CommentCodeError.newCodeMsg(1008, "非up主无法评论").getMsg());
    }

    @Test
    @DisplayName("findIn 沿 cause 链回溯 —— 对齐 Go errors.As，包过一层的业务错误不能被降级成 5001")
    void findInUnwrapsCauseChain() {
        CommentCodeError original = CommentCodeError.newCode(1012);

        // 未包装：直接命中
        assertSame(original, CommentCodeError.findIn(original));

        // Go: fmt.Errorf("...: %w", err) 包一层后 errors.As 仍能还原 → Java 必须沿 getCause() 回溯
        RuntimeException wrapped = new RuntimeException("创建评论失败", original);
        assertSame(original, CommentCodeError.findIn(wrapped));

        // 包两层
        RuntimeException wrappedTwice = new RuntimeException("logic 失败", wrapped);
        assertSame(original, CommentCodeError.findIn(wrappedTwice));

        // 不是业务错误 → 返回 null，调用方据此回退 5001（Go 侧 code 保持 ErrorServerCommon）
        assertNull(CommentCodeError.findIn(new IllegalStateException("PG 连接断开")));
        assertNull(CommentCodeError.findIn(null));
    }

    @Test
    @DisplayName("序列化后只有 code / msg 两个字段，与 Go encoding/json 一致")
    void codeErrorSerialisesToExactlyTwoFieldsLikeGo() throws Exception {
        // Go: encoding/json 只序列化导出字段，输出 {"code":5003,"msg":"DB查询失败"}。
        // 若没有 @JsonAutoDetect(NONE)，Jackson 还会吐出 Throwable 继承来的
        // message / localizedMessage / cause / stackTrace —— Go 侧一个都没有。
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(
                mapper.writeValueAsString(new CommentCodeError(5003, "DB查询失败")));

        assertEquals(2, node.size(), "多出/少了字段：" + node);
        assertEquals(5003, node.get("code").asInt());
        assertEquals("DB查询失败", node.get("msg").asText());

        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        assertEquals(List.of("code", "msg"), fieldNames);
    }

    @Test
    @DisplayName("CommentCodeError 是 unchecked —— rpc logic 才能像 Go 返回值那样直接交出去")
    void codeErrorIsUnchecked() {
        assertTrue(RuntimeException.class.isAssignableFrom(CommentCodeError.class));
        CommentCodeError err = CommentCodeError.newCode(CommentErrorCode.ERROR_SERVER_COMMON.getCode());
        assertEquals(5001, err.getCode());
        assertNotNull(err.getMessage());
        assertFalse(err.getMessage().isEmpty());
    }

    @Test
    @DisplayName("CommentCodeError 包装自身不会造成 findIn 死循环")
    void findInTerminatesOnSelfCause() throws IOException {
        // Throwable.initCause 禁止自引用，这里确认构造路径本身不会埋雷
        CommentCodeError err = CommentCodeError.newCode(2001);
        assertSame(err, CommentCodeError.findIn(err));
        assertNull(err.getCause());
    }
}