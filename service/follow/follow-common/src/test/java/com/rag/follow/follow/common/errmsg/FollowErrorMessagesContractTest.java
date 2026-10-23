package com.rag.follow.follow.common.errmsg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * follow 域 code → msg <b>文案</b>契约测试 —— 真源
 * {@code Sea-RideTheWind_golang/service/follow/common/errmsg.go:55-95}。
 */
class FollowErrorMessagesContractTest {

    /**
     * Go {@code codeMsg} 整表逐条抄录（{@code errmsg.go:56-73}）。
     * 用枚举名作键、裸 int 作值：值必须逐条抄自 Go，避免\"照着当前实现写断言\"。
     */
    private static final Map<FollowErrorCode, String> GO_MESSAGES = new LinkedHashMap<>();

    static {
        // 通用（Go errmsg.go:56-57）
        GO_MESSAGES.put(FollowErrorCode.SUCCESS, "OK");
        GO_MESSAGES.put(FollowErrorCode.ERROR, "FAIL");
        // 通用错误码（Go errmsg.go:59-67）
        GO_MESSAGES.put(FollowErrorCode.CODE_SERVER_BUSY, "service busy");
        GO_MESSAGES.put(FollowErrorCode.ERROR_SERVER_COMMON, "internal server error");
        GO_MESSAGES.put(FollowErrorCode.ERROR_NOT_FOUND, "record not found");
        GO_MESSAGES.put(FollowErrorCode.ERROR_INVALID_PARAM, "invalid parameter");
        GO_MESSAGES.put(FollowErrorCode.ERROR_UNAUTHORIZED, "unauthorized");
        GO_MESSAGES.put(FollowErrorCode.ERROR_FORBIDDEN, "forbidden");
        GO_MESSAGES.put(FollowErrorCode.ERROR_ALREADY_EXIST, "already exists");
        GO_MESSAGES.put(FollowErrorCode.ERROR_DB_SELECT, "database query failed");
        GO_MESSAGES.put(FollowErrorCode.ERROR_DB_UPDATE, "database update failed");
        // 用户（Go errmsg.go:69）
        GO_MESSAGES.put(FollowErrorCode.ERROR_USER_NOT_FOUND, "user not found");
        // 关注（Go errmsg.go:70-73）
        GO_MESSAGES.put(FollowErrorCode.ERROR_SELF_RELATION, "self relation is not allowed");
        GO_MESSAGES.put(FollowErrorCode.ERROR_RELATION_BLOCKED, "blocked relation exists");
        GO_MESSAGES.put(FollowErrorCode.ERROR_FOLLOW_NOT_FOUND, "follow relation not found");
        GO_MESSAGES.put(FollowErrorCode.ERROR_BLOCK_NOT_FOUND, "block relation not found");
    }

    @Test
    @DisplayName("16 条文案逐条等于 Go errmsg.go 的 codeMsg 值（含大小写与中英文形态）")
    void everyMessageMatchesGoOneByOne() {
        assertEquals(GO_MESSAGES.size(), FollowErrorMessages.MSG_BY_CODE.size());
        GO_MESSAGES.forEach((constant, expectedMsg) ->
                assertEquals(expectedMsg, FollowErrorMessages.getMsg(constant.getCode()),
                        "文案漂移：" + constant.name() + " 必须逐字节等于 Go codeMsg 的值"));
    }

    @Test
    @DisplayName("文案表与码常量表严格一一对应（没有只声明码却漏文案的，也没有只登记文案却没有码的）")
    void tableAndEnumAreInBijection() {
        assertEquals(FollowErrorCode.values().length, FollowErrorMessages.MSG_BY_CODE.size());
        for (FollowErrorCode constant : FollowErrorCode.values()) {
            assertTrue(FollowErrorMessages.MSG_BY_CODE.containsKey(constant.getCode()),
                    "缺文案：" + constant.name());
        }
    }

    @Test
    @DisplayName("文案表内顺序与 Go codeMsg 的书写顺序一致（LinkedHashMap 保序，便于逐行对照）")
    void tableOrderMatchesGoSourceOrder() {
        assertEquals(
                List.of("OK", "FAIL",
                        "service busy", "internal server error", "record not found",
                        "invalid parameter", "unauthorized", "forbidden", "already exists",
                        "database query failed", "database update failed",
                        "user not found",
                        "self relation is not allowed", "blocked relation exists",
                        "follow relation not found", "block relation not found"),
                new ArrayList<>(FollowErrorMessages.MSG_BY_CODE.values()),
                "文案表顺序必须与 Go codeMsg 书写顺序一致");
    }

    @Test
    @DisplayName("未知码回退为 \"FAIL\"，不是 null、不是空串、不是 code 的字符串（L2 本组件唯一分支）")
    void unknownCodeFallsBackToFail() {
        // Go: GetErrMsg 未命中 ⇒ return codeMsg[Error] ⇒ "FAIL"
        // ⚠️ 走的是跨域撞车样本：这些码值在别的域存在，本域未登记
        for (int code : new int[]{1016, 1017, 2001, 2002, 3001, 5009, 5010, 5011, 5012, 5102, 5103, 5205}) {
            assertEquals("FAIL", FollowErrorMessages.getMsg(code), "未知码必须回退 \"FAIL\"：" + code);
        }
        assertEquals("FAIL", FollowErrorMessages.getMsg(0));
        assertEquals("FAIL", FollowErrorMessages.getMsg(-1));
        assertEquals("FAIL", FollowErrorMessages.getMsg(Integer.MAX_VALUE));
        assertEquals("FAIL", FollowErrorMessages.getMsg(Integer.MIN_VALUE));
    }

    @Test
    @DisplayName("已知码取回自身文案：200→OK、500→FAIL（500 是命中而非回落）")
    void knownCodesReturnTheirOwnMessage() {
        assertEquals("OK", FollowErrorMessages.getMsg(200));
        assertEquals("FAIL", FollowErrorMessages.getMsg(500));
    }

    @Test
    @DisplayName("文案两两互不相同——这是 Go 端 bizCodeByMsg 能确定反查的前提")
    void messagesArePairwiseDistinct() {
        Set<String> unique = new HashSet<>();
        List<String> dup = new ArrayList<>();
        for (String msg : FollowErrorMessages.MSG_BY_CODE.values()) {
            if (!unique.add(msg)) {
                dup.add(msg);
            }
        }
        assertEquals(List.of(), dup,
                "重复文案会让 Go 的 bizCodeByMsg 反查结果随机（Go map 遍历顺序不保证）");
    }

    @Test
    @DisplayName("follow 域整表英文——不得按邻近 favorite 域的\"中文为主\"形态补中文")
    void messagesAreAllEnglish() {
        for (Map.Entry<Integer, String> entry : FollowErrorMessages.MSG_BY_CODE.entrySet()) {
            String msg = entry.getValue();
            // 全部 16 条都应为 ASCII：Go 源码里没有一条含非 ASCII 字符
            assertTrue(msg.chars().allMatch(c -> c < 128),
                    "文案 " + entry.getKey() + " 含非 ASCII 字符，与 Go 整表英文形态不符：" + msg);
        }
    }

    @Test
    @DisplayName("文案表不可被外部篡改")
    void msgTableIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> FollowErrorMessages.MSG_BY_CODE.put(9999, "hacked"));
    }

    @Test
    @DisplayName("FollowCodeError 序列化后只有 code / msg 两个键，且值逐字节等于 Go 的 json 标签输出")
    void codeErrorSerializesToExactlyCodeAndMsg() throws Exception {
        // Go: CodeError{Code int `json:"code"`; Msg string `json:"msg"`}
        // ⚠️ Go 的 Error() 是方法不是字段 ⇒ 不会进 JSON；
        //    Java 的 Throwable.getMessage() 默认是可见 getter ⇒ 必须被 @JsonAutoDetect 关掉，
        //    否则会多出 message / cause / stackTrace 三个 Go 侧不存在的键。
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(FollowCodeError.newCode(FollowErrorCode.ERROR_DB_SELECT.getCode()));

        Map<?, ?> parsed = mapper.readValue(json, Map.class);
        assertEquals(Set.of("code", "msg"), parsed.keySet(),
                "JSON 键集合必须恰好 {code, msg}；多余的键在 Go 侧不存在");
        assertEquals(5007, parsed.get("code"));
        assertEquals("database query failed", parsed.get("msg"));
        // 精确逐字串：键顺序由声明顺序决定
        assertEquals("{\"code\":5007,\"msg\":\"database query failed\"}", json);
    }

    @Test
    @DisplayName("newCode 走查表、newCodeMsg 用传入值——Go 两条构造路径的行为差异")
    void twoConstructorPathsMatchGo() {
        // Go: NewErrCode(code) ⇒ Msg = GetErrMsg(code)
        FollowCodeError viaTable = FollowCodeError.newCode(FollowErrorCode.ERROR_USER_NOT_FOUND.getCode());
        assertEquals(5101, viaTable.getCode());
        assertEquals("user not found", viaTable.getMsg());

        // Go: NewErrCodeMsg(code, msg) ⇒ Msg = 传入值，【不】走查表
        FollowCodeError viaMsg = FollowCodeError.newCodeMsg(5202, "blocked relation exists");
        assertEquals(5202, viaMsg.getCode());
        assertEquals("blocked relation exists", viaMsg.getMsg());

        // ⚠️ 走查表路径遇未知码必须回退 "FAIL"，而 newCodeMsg 原样透传
        assertEquals("FAIL", FollowCodeError.newCode(9999).getMsg());
        assertEquals("custom text", FollowCodeError.newCodeMsg(9999, "custom text").getMsg());
    }

    @Test
    @DisplayName("getMessage() 与 Go 的 Error() 逐字相同：\"ErrCode:%d, Errmsg:%s\"（运维 grep 日志依赖）")
    void errorTextMatchesGoFormat() {
        // Go: fmt.Sprintf("ErrCode:%d, Errmsg:%s", e.Code, e.Msg)
        assertEquals("ErrCode:5007, Errmsg:database query failed",
                FollowCodeError.newCode(5007).getMessage());
        assertEquals("ErrCode:5201, Errmsg:self relation is not allowed",
                FollowCodeError.newCode(5201).getMessage());
        assertNotNull(FollowCodeError.newCodeMsg(9999, "x").getMessage());
    }

    @Test
    @DisplayName("FollowCodeError 是 unchecked——Go 的 error 无 checked/unchecked 之分，不给调用点加 throws 负担")
    void codeErrorIsUnchecked() {
        // 该断言在编译期即生效：下面这行不需要 try/catch、不需要 throws。
        FollowCodeError thrown = FollowCodeError.newCode(FollowErrorCode.ERROR_UNAUTHORIZED.getCode());
        assertTrue(thrown instanceof RuntimeException);
        // 并且能被 api 层的 errors.As 等价物沿 cause 链找到（Go: followcenter.go:57）
        RuntimeException wrapper = new RuntimeException("wrapped", thrown);
        FollowCodeError found = null;
        for (Throwable t = wrapper; t != null; t = t.getCause()) {
            if (t instanceof FollowCodeError candidate) {
                found = candidate;
                break;
            }
        }
        assertNotNull(found, "api 层必须能沿 cause 链找到 CodeError（Go 用的是 errors.As）");
        assertEquals(5004, found.getCode());
        // 顺带钉住：数组/枚举顺序无关的防回归——全 16 个常量都能被反查回自身
        assertEquals(16, Arrays.stream(FollowErrorCode.values())
                .map(FollowErrorCode::getCode).distinct().count());
    }
}