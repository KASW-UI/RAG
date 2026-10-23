package com.rag.follow.follow.common.errmsg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * follow 域错误码<b>码值</b>契约测试 —— 真源
 * {@code Sea-RideTheWind_golang/service/follow/common/errmsg.go:33-53} 的 const 块。
 *
 * <p><b>本测试断言的是「Go 的行为」，不是「Java 现在的行为」</b>：每条期望值都直接抄自 Go 源码行，
 * 不允许「照着当前实现写断言」——那只会把一个错误答案锁成绿色
 * （本仓库已吃过这个亏：AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。
 */
class FollowErrorCodeContractTest {

    /**
     * Go {@code errmsg.go:34-52} 的 const 块逐条抄录，顺序与 Go 书写顺序一致。
     * 用 {@link FollowErrorCode} 枚举名而非裸 int 作为键，避免两边一起错却仍然自洽。
     */
    private static final Map<FollowErrorCode, Integer> GO_CONST_VALUES = Map.ofEntries(
            // 通用（Go errmsg.go:34-35）
            Map.entry(FollowErrorCode.SUCCESS, 200),
            Map.entry(FollowErrorCode.ERROR, 500),
            // 通用错误码（Go errmsg.go:37-48）
            Map.entry(FollowErrorCode.CODE_SERVER_BUSY, 1015),
            Map.entry(FollowErrorCode.ERROR_SERVER_COMMON, 5001),
            Map.entry(FollowErrorCode.ERROR_NOT_FOUND, 5002),
            Map.entry(FollowErrorCode.ERROR_INVALID_PARAM, 5003),
            Map.entry(FollowErrorCode.ERROR_UNAUTHORIZED, 5004),
            Map.entry(FollowErrorCode.ERROR_FORBIDDEN, 5005),
            Map.entry(FollowErrorCode.ERROR_ALREADY_EXIST, 5006),
            Map.entry(FollowErrorCode.ERROR_DB_SELECT, 5007),
            Map.entry(FollowErrorCode.ERROR_DB_UPDATE, 5008),
            // 用户模块 5100-5199（Go errmsg.go:47）
            Map.entry(FollowErrorCode.ERROR_USER_NOT_FOUND, 5101),
            // 关注域 5200-5299（Go errmsg.go:49-52）
            Map.entry(FollowErrorCode.ERROR_SELF_RELATION, 5201),
            Map.entry(FollowErrorCode.ERROR_RELATION_BLOCKED, 5202),
            Map.entry(FollowErrorCode.ERROR_FOLLOW_NOT_FOUND, 5203),
            Map.entry(FollowErrorCode.ERROR_BLOCK_NOT_FOUND, 5204));

    @Test
    @DisplayName("Go const 块恰好 16 个常量——多一个少一个都算契约变更")
    void thereAreExactlySixteenCodes() {
        // Go errmsg.go:34-52 逐行计数共 16 行常量；漏迁或擅自新增都会让计数对不上。
        assertEquals(16, FollowErrorCode.values().length);
    }

    @Test
    @DisplayName("每个常量的码值逐条等于 Go errmsg.go 的 const 值（L1 跨服务契约）")
    void everyCodeValueMatchesGoOneByOne() {
        GO_CONST_VALUES.forEach((constant, expectedCode) ->
                assertEquals(expectedCode.intValue(), constant.getCode(),
                        "码值漂移：" + constant.name() + " 必须等于 Go errmsg.go 的 const 值"));
    }

    @Test
    @DisplayName("枚举声明顺序与 Go const 块书写顺序一致，便于逐行 review")
    void declarationOrderMatchesGoSourceOrder() {
        assertEquals(
                List.of("SUCCESS", "ERROR",
                        "CODE_SERVER_BUSY",
                        "ERROR_SERVER_COMMON", "ERROR_NOT_FOUND", "ERROR_INVALID_PARAM",
                        "ERROR_UNAUTHORIZED", "ERROR_FORBIDDEN", "ERROR_ALREADY_EXIST",
                        "ERROR_DB_SELECT", "ERROR_DB_UPDATE",
                        "ERROR_USER_NOT_FOUND",
                        "ERROR_SELF_RELATION", "ERROR_RELATION_BLOCKED",
                        "ERROR_FOLLOW_NOT_FOUND", "ERROR_BLOCK_NOT_FOUND"),
                java.util.Arrays.stream(FollowErrorCode.values()).map(Enum::name).toList(),
                "枚举声明顺序必须与 Go const 块书写顺序一致；重排会让逐行 review 失效");
    }

    @Test
    @DisplayName("码值无重复——重复会让 code → 枚举 反查静默丢码")
    void codeValuesAreUnique() {
        Set<Integer> unique = new HashSet<>();
        for (FollowErrorCode constant : FollowErrorCode.values()) {
            assertTrue(unique.add(constant.getCode()),
                    "重复码值：" + constant.getCode() + "（" + constant.name() + "）");
        }
        assertEquals(FollowErrorCode.values().length, unique.size());
    }

    @Test
    @DisplayName("CodeServerBusy = 1015 排在 5001 之前——Go 源码就写在通用错误码分组的开头，不得\"顺手归位\"")
    void codeServerBusyStaysBeforeFiveThousandRange() {
        int busy = java.util.Arrays.stream(FollowErrorCode.values())
                .filter(c -> c.getCode() == 1015).findFirst().orElseThrow().ordinal();
        int serverCommon = java.util.Arrays.stream(FollowErrorCode.values())
                .filter(c -> c.getCode() == 5001).findFirst().orElseThrow().ordinal();
        assertTrue(busy < serverCommon,
                "Go errmsg.go:37 把 CodeServerBusy 写在 5001 之前；归位到 5001 之后即为无出处的重排");
    }

    @Test
    @DisplayName("getCode() 产出裸 int，可直接喂给 grpcError(grpcCode, bizCode) 与 envelope 的 code 字段")
    void getCodeYieldsPrimitiveIntForExistingConsumers() {
        // proto BaseResp.code 是 int32，Go 侧写作 int32(followcommon.Success)
        // ⇒ Java 侧必须能直接 put 到 int32 字段，不需要调用点自己转换。
        int raw = FollowErrorCode.SUCCESS.getCode();
        assertEquals(200, raw);
    }

    @Test
    @DisplayName("跨域同码不同义：follow 的 5001/5003/5004 不得被 favorite 域的中文含义覆盖")
    void followCodesKeepTheirOwnMeaningAgainstOtherDomains() {
        // favorite 域同码含义：5001="系统内部错误"、5003="参数错误"、5004="未登录或登录已失效"
        // follow 域（整表英文）：见 FollowErrorMessagesContractTest#messagesAreAllEnglish
        // 本测试钉住「码值集合」这一层不被别的域悄悄改写。
        assertEquals(5001, FollowErrorCode.ERROR_SERVER_COMMON.getCode());
        assertEquals(5003, FollowErrorCode.ERROR_INVALID_PARAM.getCode());
        assertEquals(5004, FollowErrorCode.ERROR_UNAUTHORIZED.getCode());
    }
}