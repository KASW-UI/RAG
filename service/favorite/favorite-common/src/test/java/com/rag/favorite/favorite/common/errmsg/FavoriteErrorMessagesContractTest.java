package com.rag.favorite.favorite.common.errmsg;

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
 * favorite 域错误码<b>文案</b>契约测试 —— 真源
 * {@code Sea-RideTheWind_golang/service/favorite/common/errmsg.go:43-76}（{@code codeMsg} 表）
 * 与 {@code errmsg.go:91-97}（{@code func GetErrMsg} 的回退逻辑）。
 *
 * <p><b>本测试断言的是「Go 的行为」，不是「Java 现在的行为」</b>：期望值直接抄自 Go 源码行。
 *
 * <p>文案与码值一样是 L1 跨服务契约，且在本域<b>权重更高</b>：业务码在 wire 上是
 * 「藏在 description 文案里」的（见 {@link FavoriteGrpcError}），文案不只是给人看的提示，
 * 它<b>同时是业务码的传输载体</b>。文案改一个字，业务码就还原不出来了。
 */
class FavoriteErrorMessagesContractTest {

    /**
     * Go {@code errmsg.go:44-75} 的 {@code codeMsg} 表逐条抄录（含大小写、空格、英文/中文形态，逐字节）。
     */
    private static final Map<Integer, String> GO_CODE_MSG = Map.ofEntries(
            // 通用（Go errmsg.go:44-45）
            Map.entry(200, "OK"),
            Map.entry(500, "FAIL"),
            // 通用错误码（Go errmsg.go:48-57）
            Map.entry(1015, "服务繁忙"),
            Map.entry(5001, "系统内部错误"),
            Map.entry(5002, "not found"),
            Map.entry(5003, "参数错误"),
            Map.entry(5004, "未登录或登录已失效"),
            Map.entry(5005, "无权限操作"),
            Map.entry(5006, "记录已存在"),
            Map.entry(5007, "数据库查询失败"),
            Map.entry(5008, "数据库更新失败"),
            Map.entry(5009, "ID生成失败"),
            // 用户（Go errmsg.go:60-64）
            Map.entry(5101, "用户不存在"),
            Map.entry(5102, "用户已存在"),
            Map.entry(5103, "密码错误"),
            Map.entry(5104, "token无效"),
            Map.entry(5105, "token已过期"),
            // 收藏夹（Go errmsg.go:67-69）
            Map.entry(5201, "收藏夹不存在"),
            Map.entry(5202, "收藏夹名称不能为空"),
            Map.entry(5203, "收藏夹名称已存在"),
            // 收藏（Go errmsg.go:72-75）
            Map.entry(5301, "收藏记录不存在"),
            Map.entry(5302, "该内容已收藏"),
            Map.entry(5303, "收藏目标不能为空"),
            Map.entry(5304, "收藏暂不可撤回"));

    @Test
    @DisplayName("24 条文案逐条等于 Go errmsg.go 的 codeMsg 值（含大小写与中英文形态）")
    void everyMessageMatchesGoOneByOne() {
        assertEquals(24, GO_CODE_MSG.size());
        for (Map.Entry<Integer, String> expected : GO_CODE_MSG.entrySet()) {
            assertEquals(expected.getValue(), FavoriteErrorMessages.getMsg(expected.getKey()),
                    "文案漂移：code=" + expected.getKey() + " 的文案与 Go errmsg.go 不一致");
        }
    }

    @Test
    @DisplayName("文案表与码常量表严格一一对应（没有只声明码却漏文案的，也没有只登记文案却没有码的）")
    void tableAndEnumAreInBijection() {
        Set<Integer> enumCodes = new HashSet<>();
        for (FavoriteErrorCode ec : FavoriteErrorCode.values()) {
            enumCodes.add(ec.getCode());
        }
        assertEquals(enumCodes, FavoriteErrorMessages.MSG_BY_CODE.keySet(),
                "码常量表与文案表的键集合不一致");
    }

    @Test
    @DisplayName("文案表内顺序与 Go codeMsg 的书写顺序一致（LinkedHashMap 保序，便于逐行对照）")
    void tableOrderMatchesGoSourceOrder() {
        List<Integer> expectedOrder = List.of(
                200, 500,
                1015, 5001, 5002, 5003, 5004, 5005, 5006, 5007, 5008, 5009,
                5101, 5102, 5103, 5104, 5105,
                5201, 5202, 5203,
                5301, 5302, 5303, 5304);

        assertEquals(expectedOrder, new ArrayList<>(FavoriteErrorMessages.MSG_BY_CODE.keySet()),
                "文案表的键顺序与 Go codeMsg 书写顺序不一致");
    }

    @Test
    @DisplayName("未知码回退为 \"FAIL\"，不是 null、不是空串、不是 code 的字符串（L2 本组件唯一分支）")
    void unknownCodeFallsBackToFail() {
        // Go errmsg.go:91-97：msg, ok := codeMsg[code]; if !ok { return codeMsg[Error] }
        // codeMsg[Error] 即 500 号的文案 "FAIL"。
        // 触发路径真实存在：Go api 的 handler 直接把查表结果写进 envelope，
        // 见 api/internal/handler/favorite/get_favorite_folders_handler.go:31。
        List<Integer> unknownCodes = List.of(
                0,                          // 零值
                -1,                         // 负数
                1,                          // 介于两个真实码之间
                999,                        // 1000~1014 空档
                1016,                       // 1015 与 5001 之间
                5000,                       // 5001 之前
                5010,                       // 5009 之后、5101 之前
                5100,                       // 用户段起点
                5106,                       // 用户段末尾之后
                5200,                       // 收藏夹段起点
                5204,                       // 收藏夹段末尾之后
                5300,                       // 收藏段起点
                5305,                       // 收藏段末尾之后
                2001,                       // ⚠️ 真实存在于 like 域，favorite 域必须回落
                3001,                       // ⚠️ 真实存在于 like 域，favorite 域必须回落
                Integer.MAX_VALUE,
                Integer.MIN_VALUE);

        for (int code : unknownCodes) {
            assertEquals("FAIL", FavoriteErrorMessages.getMsg(code),
                    "未知码 " + code + " 必须回落为 \"FAIL\"（Go: codeMsg[Error]）");
        }
    }

    @Test
    @DisplayName("已知码取回自身文案：200→OK、500→FAIL（500 是命中而非回落）")
    void knownCodesReturnTheirOwnMessage() {
        assertEquals("OK", FavoriteErrorMessages.getMsg(200));
        assertEquals("FAIL", FavoriteErrorMessages.getMsg(500));
        assertEquals("服务繁忙", FavoriteErrorMessages.getMsg(1015));
        assertEquals("系统内部错误", FavoriteErrorMessages.getMsg(5001));
    }

    @Test
    @DisplayName("文案两两互不相同——这是 Go 端 bizCodeByMsg 能确定反查的前提")
    void messagesArePairwiseDistinct() {
        // Go errmsg.go:78-89 的 bizCodeByMsg 用「先到先得」处理重复文案，
        // 而 Go 的 map 遍历顺序随机 ⇒ 一旦有两条同文案，反查结果就是真随机。
        // 当前 24 条文案互不相同（Go 侧靠这一点让该分支实际不可达），
        // 这里把该前提钉死，防止日后加文案时无声破坏它。
        Set<String> seen = new HashSet<>();
        for (Map.Entry<Integer, String> entry : FavoriteErrorMessages.MSG_BY_CODE.entrySet()) {
            assertTrue(seen.add(entry.getValue()),
                    "文案重复：" + entry.getValue() + " 已被 code=" + entry.getKey() + " 占用");
        }
        assertEquals(24, seen.size());
    }

    @Test
    @DisplayName("跨域同码不同义：favorite 的 5002/5003/5004 不得被 hot / users / article 域的含义覆盖")
    void favoriteCodesKeepTheirOwnMeaningAgainstOtherDomains() {
        // 这三条数值在别的域里指向完全不同的故障类型。RAG 多个域在同一仓库内演进，
        // 一旦有人按 hot 域的直觉（5002=record not found / 5003=invalid parameter / 5004=unauthorized）
        // 去「修正」favorite，就会引入客户端可见的文案漂移。此处把差异显式钉死。
        assertNotEquals("record not found", FavoriteErrorMessages.getMsg(5002),
                "favorite 的 5002 必须是 Go 源码里的「not found」，不能被 hot 域的「record not found」覆盖");
        assertNotEquals("invalid parameter", FavoriteErrorMessages.getMsg(5003),
                "favorite 的 5003 必须是「参数错误」，不能被 hot 域的「invalid parameter」覆盖");
        assertNotEquals("unauthorized", FavoriteErrorMessages.getMsg(5004),
                "favorite 的 5004 必须是「未登录或登录已失效」，不能被 hot 域的「unauthorized」覆盖");
        assertNotEquals("更新数据库失败", FavoriteErrorMessages.getMsg(5002),
                "favorite 的 5002 不能是 users/article 域的「更新数据库失败」");

        // 同时确认 favorite 域确实登记了这三个码（防止上一步因为「查不到」而假通过）。
        assertEquals("not found", FavoriteErrorMessages.getMsg(5002));
        assertEquals("参数错误", FavoriteErrorMessages.getMsg(5003));
        assertEquals("未登录或登录已失效", FavoriteErrorMessages.getMsg(5004));
    }

    @Test
    @DisplayName("favorite 域文案是中文为主但 5002 是英文——不得被统一「补中文」")
    void messagesAreChineseExceptEnglishNotFound() {
        // 唯一含非 ASCII 之外形态的条目：errmsg.go:50 的 ErrorNotFound 是英文 "not found"。
        // hot 域是整表英文、users/article 域整表中文；favorite 是混合形态，
        // 按「本域应该是中文」的直觉补成「记录不存在」即为文案漂移。
        assertEquals("not found", FavoriteErrorMessages.getMsg(5002));
        assertTrue(FavoriteErrorMessages.getMsg(5003).chars().anyMatch(c -> c > 127),
                "5003 应是中文「参数错误」");
        assertTrue(FavoriteErrorMessages.getMsg(5304).chars().anyMatch(c -> c > 127),
                "5304 应是中文「收藏暂不可撤回」");

        // 除 5002 / 200 / 500 外，其余 21 条都必须含中文，防止有人把整表换成英文。
        // Go 源码里 favorite 域只有 200「OK」/ 500「FAIL」/ 5002「not found」三条是 ASCII。
        List<Integer> asciiOnlyCodes = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : FavoriteErrorMessages.MSG_BY_CODE.entrySet()) {
            if (entry.getValue().chars().allMatch(c -> c < 128)) {
                asciiOnlyCodes.add(entry.getKey());
                assertTrue(List.of(200, 500, 5002).contains(entry.getKey()),
                        "code=" + entry.getKey() + " 的文案是非 ASCII，但 Go 源码里它是 ASCII："
                                + entry.getValue());
            }
        }
        assertEquals(List.of(200, 500, 5002), asciiOnlyCodes,
                "Go 源码里 favorite 域只有 200/500/5002 三条文案是 ASCII；"
                        + "多出来的 ASCII 文案说明有人把中文文案换成了英文");
    }

    @Test
    @DisplayName("文案表不可被外部篡改")
    void msgTableIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> FavoriteErrorMessages.MSG_BY_CODE.put(12345, "篡改"));
    }
}