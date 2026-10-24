package com.rag.like.like.common.errmsg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * like 域错误码契约的 1:1 守门测试。
 *
 * <p><b>为什么这些值得锁死</b>：错误码数值与文案同时出现在 HTTP envelope 与 gRPC 上，
 * 是跨服务对外契约；改任何一条都会让已发布的客户端解析失败。
 * 而这些值「看起来像是随便写的常量」，编译器与 IDE 都不会提醒有人改错了。
 *
 * <p>期望值直接抄自 Go 真源
 * {@code /Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/like/common/errmsg/errmsg.go}：
 * 常量块 {@code errmsg.go:37-67}、文案表 {@code errmsg.go:69-99}、回退逻辑 {@code errmsg.go:101-107}。
 */
class LikeErrorContractTest {

    /** Go {@code errmsg.go:37-67} const 块 + {@code errmsg.go:69-99} codeMsg，29 条 1:1。 */
    private static final Map<Integer, String> GO_TRUTH = new LinkedHashMap<>();

    static {
        GO_TRUTH.put(200, "OK");
        GO_TRUTH.put(500, "FAIL");
        GO_TRUTH.put(1015, "服务繁忙");
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
        GO_TRUTH.put(5011, "Redis查询失败");
        GO_TRUTH.put(5012, "Redis更新失败");
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
        GO_TRUTH.put(2001, "输入有误");
        GO_TRUTH.put(2002, "类型转换错误");
        GO_TRUTH.put(3001, "构建 Outbox 消息载荷失败");
    }

    /** 按 {@code LikeErrorCode} 的<b>声明顺序</b>取出 (code, 枚举名)，用于与 Go 逐条对照。 */
    private static Map<Integer, String> declaredConstants() {
        Map<Integer, String> actual = new LinkedHashMap<>();
        for (LikeErrorCode c : LikeErrorCode.values()) {
            assertNull(actual.put(c.getCode(), c.name()), "错误码重复：" + c.getCode());
        }
        return actual;
    }

    @Test
    @DisplayName("错误码总数是 29 —— 设计文档写的「30 个」是笔误")
    void thereAreTwentyNineCodes() {
        assertEquals(29, declaredConstants().size(),
                "Go errmsg.go:37-67 只有 29 个常量；FileTree/like/api.md 与骨架注释写的「30 个」与源码不符");
        assertEquals(29, GO_TRUTH.size());
        assertEquals(29, LikeErrorCode.values().length);
    }

    @Test
    @DisplayName("29 个码的数值与顺序与 Go 逐条 1:1（不能增、不能改、不能漏、不能重排）")
    void everyCodeMatchesGoOneByOne() {
        Map<Integer, String> actual = declaredConstants();
        // 逐条比对，且顺序必须一致（枚举声明顺序即书写顺序契约）
        assertEquals(new ArrayList<>(GO_TRUTH.keySet()), new ArrayList<>(actual.keySet()),
                "码值集合或顺序与 Go 不一致");
    }

    @Test
    @DisplayName("高频兜底码的取值锁定（改这三个会打穿 api 层的错误还原）")
    void frequentlyUsedFallbackCodesArePinned() {
        assertEquals(1015, LikeErrorCode.CODE_SERVER_BUSY.getCode());
        assertEquals(5001, LikeErrorCode.ERROR_SERVER_COMMON.getCode());
        assertEquals(1006, LikeErrorCode.ERROR_TOKEN_RUNTIME.getCode());
    }

    @Test
    @DisplayName("29 条文案与 Go 逐条 1:1")
    void everyMessageMatchesGoOneByOne() {
        assertEquals(29, LikeErrorMessages.MSG_BY_CODE.size());
        GO_TRUTH.forEach((code, msg) ->
                assertEquals(msg, LikeErrorMessages.getMsg(code), "code=" + code + " 的文案漂移了"));
    }

    @Test
    @DisplayName("文案表与码常量表严格一一对应（没有只声明码却漏文案的）")
    void everyCodeHasAMessage() throws Exception {
        assertEquals(declaredConstants().keySet(), LikeErrorMessages.MSG_BY_CODE.keySet());
    }

    @Test
    @DisplayName("未知码回退到 \"FAIL\"，不是 null、不是空串 —— 对齐 Go GetErrMsg")
    void unknownCodeFallsBackToFail() {
        assertEquals("FAIL", LikeErrorMessages.getMsg(999_999));
        assertEquals("FAIL", LikeErrorMessages.getMsg(-1));
        assertEquals("FAIL", LikeErrorMessages.getMsg(0));
        // 边界：500 本身是已知码，返回自己的 msg
        assertEquals("FAIL", LikeErrorMessages.getMsg(500));
        assertEquals("OK", LikeErrorMessages.getMsg(200));
    }

    @Test
    @DisplayName("文案表不可被外部篡改")
    void msgTableIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> LikeErrorMessages.MSG_BY_CODE.put(12345, "篡改"));
    }

    @Test
    @DisplayName("CodeError.getMessage() 与 Go Error() 逐字节相同")
    void codeErrorMessageFormatMatchesGo() {
        assertEquals("ErrCode:5003, Errmsg:DB查询失败",
                new CodeError(5003, "DB查询失败").getMessage());
        assertEquals("ErrCode:5001, Errmsg:系统内部错误",
                new CodeError(5001, "系统内部错误").getMessage());
    }

    @Test
    @DisplayName("newCode 查表、newCodeMsg 不查表 —— 这是 Go 的区分，必须保留")
    void factoriesDifferOnLookup() {
        // Go NewErrCode(5003) → msg = GetErrMsg(5003) = "查询数据库失败"
        assertEquals("查询数据库失败", CodeError.newCode(5003).getMsg());
        // Go NewErrCodeMsg(5003, "DB查询失败") → msg 原样，不能被表覆盖
        assertEquals("DB查询失败", CodeError.newCodeMsg(5003, "DB查询失败").getMsg());
    }

    @Test
    @DisplayName("CodeError 序列化后只有 code / msg 两个字段，与 Go encoding/json 一致")
    void codeErrorSerialisesToExactlyTwoFieldsLikeGo() throws Exception {
        // Go: encoding/json 只序列化导出字段，输出 {"code":5003,"msg":"DB查询失败"}。
        // 若没有 @JsonAutoDetect(NONE)，Jackson 还会吐出 Throwable 继承来的
        // message / localizedMessage / cause / stackTrace —— Go 侧一个都没有。
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(
                mapper.writeValueAsString(new CodeError(5003, "DB查询失败")));

        assertEquals(2, node.size(), "多出/少了字段：" + node);
        assertEquals(5003, node.get("code").asInt());
        assertEquals("DB查询失败", node.get("msg").asText());

        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        assertEquals(List.of("code", "msg"), fieldNames);
    }

    @Test
    @DisplayName("CodeError 是 unchecked —— rpc logic 才能像 Go 返回值那样直接交出去")
    void codeErrorIsUnchecked() {
        assertTrue(RuntimeException.class.isAssignableFrom(CodeError.class));
        CodeError err = CodeError.newCode(LikeErrorCode.ERROR_SERVER_COMMON.getCode());
        assertEquals(5001, err.getCode());
        assertNotNull(err.getMessage());
        assertFalse(err.getMessage().isEmpty());
    }
}