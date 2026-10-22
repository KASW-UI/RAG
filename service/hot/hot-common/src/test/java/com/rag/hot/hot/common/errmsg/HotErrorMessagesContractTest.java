package com.rag.hot.hot.common.errmsg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * hot 域错误码<b>文案</b>契约测试 —— 真源
 * {@code Sea-RideTheWind_golang/service/hot/common/errmsg.go:48-61}（{@code codeMsg} 表）
 * 与 {@code errmsg.go:76-82}（{@code func GetErrMsg} 的回退逻辑）。
 *
 * <p><b>本测试断言的是「Go 的行为」，不是「Java 现在的行为」</b>：期望值直接抄自 Go 源码行。
 *
 * <p>文案与码值一样是 L1 跨服务契约：Go hot-api 的 {@code httpx.SetOkHandler} /
 * {@code httpx.SetErrorHandler}（{@code hotcenter.go:45-66}）把文案原样写进 envelope 的
 * {@code msg} 字段；且这批文案是<b>英文</b>，与 users / article 域的中文文案分属不同域的独立码表。
 */
class HotErrorMessagesContractTest {

    /**
     * Go {@code errmsg.go:49-61} 的 {@code codeMsg} 表逐条抄录（含大小写与空格，逐字节）。
     */
    private static final Map<Integer, String> GO_CODE_MSG = Map.ofEntries(
            Map.entry(200, "OK"),
            Map.entry(500, "FAIL"),
            Map.entry(1015, "service busy"),
            Map.entry(5001, "internal server error"),
            Map.entry(5002, "record not found"),
            Map.entry(5003, "invalid parameter"),
            Map.entry(5004, "unauthorized"),
            Map.entry(5005, "forbidden"),
            Map.entry(5006, "already exists"),
            Map.entry(5007, "database query failed"),
            Map.entry(5008, "database update failed"));

    @Test
    @DisplayName("11 条文案逐条等于 Go errmsg.go 的 codeMsg 值（含大小写与空格）")
    void everyMessageMatchesGoOneByOne() {
        assertEquals(11, GO_CODE_MSG.size());
        for (Map.Entry<Integer, String> expected : GO_CODE_MSG.entrySet()) {
            assertEquals(expected.getValue(), HotErrorMessages.getMsg(expected.getKey()),
                    "文案漂移：code=" + expected.getKey() + " 的文案与 Go errmsg.go 不一致");
        }
    }

    @Test
    @DisplayName("文案表与码常量表严格一一对应（没有只声明码却漏文案的，也没有只登记文案却没有码的）")
    void tableAndEnumAreInBijection() {
        Set<Integer> enumCodes = new HashSet<>();
        for (HotErrorCode ec : HotErrorCode.values()) {
            enumCodes.add(ec.getCode());
        }
        assertEquals(enumCodes, HotErrorMessages.MSG_BY_CODE.keySet(),
                "码常量表与文案表的键集合不一致");
    }

    @Test
    @DisplayName("文案表内顺序与 Go codeMsg 的书写顺序一致（LinkedHashMap 保序，便于逐行对照）")
    void tableOrderMatchesGoSourceOrder() {
        List<Integer> expectedOrder = List.of(200, 500, 1015, 5001, 5002, 5003, 5004, 5005, 5006, 5007, 5008);
        assertEquals(expectedOrder, new ArrayList<>(HotErrorMessages.MSG_BY_CODE.keySet()),
                "文案表的键顺序与 Go codeMsg 书写顺序不一致");
    }

    @Test
    @DisplayName("未知码回退为 \"FAIL\"，不是 null、不是空串、不是 code 的字符串（L2 本组件唯一分支）")
    void unknownCodeFallsBackToFail() {
        // Go errmsg.go:77-81：msg, ok := codeMsg[code]; if !ok { return codeMsg[Error] }
        // codeMsg[Error] 即 500 号的文案 "FAIL"。
        List<Integer> unknownCodes = List.of(
                0,                          // 零值
                -1,                         // 负数
                1,                          // 介于两个真实码之间
                999,                        // 1000~1014 空档
                1016,                       // 1015 与 2001 之间
                2001,                       // ⚠️ 真实存在于 like 域，hot 域必须回落
                5011,                       // ⚠️ 真实存在于 users 域（Redis 查询失败），hot 域必须回落
                5012,                       // ⚠️ 真实存在于 users 域（Redis 更新失败），hot 域必须回落
                1001,                       // ⚠️ 真实存在于 users/like 域（用户名已存在），hot 域必须回落
                5009,                       // 5008 之后
                Integer.MAX_VALUE,
                Integer.MIN_VALUE);

        for (int code : unknownCodes) {
            assertEquals("FAIL", HotErrorMessages.getMsg(code),
                    "未知码 " + code + " 必须回落为 \"FAIL\"（Go: codeMsg[Error]）");
        }
    }

    @Test
    @DisplayName("已知码取回自身文案：200→OK、500→FAIL（500 是命中而非回落）")
    void knownCodesReturnTheirOwnMessage() {
        assertEquals("OK", HotErrorMessages.getMsg(200));
        assertEquals("FAIL", HotErrorMessages.getMsg(500));
        assertEquals("service busy", HotErrorMessages.getMsg(1015));
        assertEquals("internal server error", HotErrorMessages.getMsg(5001));
    }

    @Test
    @DisplayName("文案两两互不相同——这是 Go 端 bizCodeByMsg 能确定反查的前提")
    void messagesArePairwiseDistinct() {
        // Go errmsg.go:63-74 的 bizCodeByMsg 用「先到先得」处理重复文案，
        // 而 Go 的 map 遍历顺序随机 ⇒ 一旦有两条同文案，反查结果就是真随机。
        // 当前 11 条文案互不相同（Go 侧靠这一点让该分支实际不可达），
        // 这里把该前提钉死，防止日后加文案时无声破坏它。
        Set<String> seen = new HashSet<>();
        for (Map.Entry<Integer, String> entry : HotErrorMessages.MSG_BY_CODE.entrySet()) {
            assertTrue(seen.add(entry.getValue()),
                    "文案重复：" + entry.getValue() + " 已被 code=" + entry.getKey() + " 占用");
        }
        assertEquals(11, seen.size());
    }

    @Test
    @DisplayName("hot 域文案是英文——不得被 users / article 域的中文文案覆盖")
    void messagesAreEnglishLikeGoSource() {
        for (Map.Entry<Integer, String> entry : HotErrorMessages.MSG_BY_CODE.entrySet()) {
            String msg = entry.getValue();
            assertTrue(msg.chars().allMatch(c -> c < 128),
                    "code=" + entry.getKey() + " 的文案含非 ASCII 字符：" + msg
                            + "。hot 域文案在 Go 侧是英文，被换成中文即为文案漂移");
        }
    }

    @Test
    @DisplayName("跨域同码不同义：hot 的 5002/5003/5004 不得被 users / article 域的含义覆盖")
    void hotCodesKeepTheirOwnMeaningAgainstOtherDomains() {
        // 这三条数值在 users / article 域里指向完全不同的故障类型。RAG 多个域在同一仓库内演进，
        // 一旦有人按「users 域 5003 = 查询数据库失败」的直觉去「修正」hot，就会引入
        // 客户端可见的错误码语义错位。此处把差异显式钉死。
        assertNotEquals("更新数据库失败", HotErrorMessages.getMsg(5002),
                "hot 的 5002 必须是「record not found」，不能是 users/article 域的「更新数据库失败」");
        assertNotEquals("查询数据库失败", HotErrorMessages.getMsg(5003),
                "hot 的 5003 必须是「invalid parameter」，不能是 users/article 域的「查询数据库失败」");
        assertNotEquals("数据库插入失败", HotErrorMessages.getMsg(5004),
                "hot 的 5004 必须是「unauthorized」，不能是 users 域的「数据库插入失败」");
        assertNotEquals("文件上传失败", HotErrorMessages.getMsg(5004),
                "hot 的 5004 必须是「unauthorized」，不能是 article 域的「文件上传失败」");

        // 同时确认 hot 域确实登记了这三个码（防止上一步因为「查不到」而假通过）。
        assertEquals("record not found", HotErrorMessages.getMsg(5002));
        assertEquals("invalid parameter", HotErrorMessages.getMsg(5003));
        assertEquals("unauthorized", HotErrorMessages.getMsg(5004));
    }

    @Test
    @DisplayName("文案表不可被外部篡改")
    void msgTableIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> HotErrorMessages.MSG_BY_CODE.put(12345, "篡改"));
    }
}
