package com.rag.hot.hot.common.errmsg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * hot 域错误码<b>码值</b>契约测试 —— 真源
 * {@code Sea-RideTheWind_golang/service/hot/common/errmsg.go:33-46} 的 const 块。
 *
 * <p><b>本测试断言的是「Go 的行为」，不是「Java 现在的行为」</b>：每条期望值都直接抄自 Go 源码行，
 * 不允许「照着当前实现写断言」——那样只会把一个错误答案锁成绿色
 * （本仓库已吃过这个亏：AGENTS.md §message-api 记录的 DRIFT-MSGAPI-001/002/003
 * 三条漂移都曾被一条「断言了 RAG 错误行为」的绿测试锁死）。
 *
 * <p>覆盖三条真实风险：
 * <ol>
 *   <li><b>码值漂移</b>（L1 跨服务契约，改了客户端就解错码）；</li>
 *   <li><b>枚举重排 / 漏迁 / 擅自新增</b>（Go ↔ RAG 对照时表长与顺序对不上）；</li>
 *   <li><b>码值重复</b>（会让任何 code → 枚举 的反查静默丢码）。</li>
 * </ol>
 */
class HotErrorCodeContractTest {

    /**
     * Go {@code errmsg.go:34-46} 的 const 块逐条抄录，顺序与 Go 书写顺序一致。
     * 用 {@link HotErrorCode} 枚举名而非裸 int 作为期望值的键，避免两边一起错却仍然自洽。
     */
    private static final Map<HotErrorCode, Integer> GO_CONST_VALUES = Map.ofEntries(
            Map.entry(HotErrorCode.SUCCESS, 200),
            Map.entry(HotErrorCode.ERROR, 500),
            Map.entry(HotErrorCode.CODE_SERVER_BUSY, 1015),
            Map.entry(HotErrorCode.ERROR_SERVER_COMMON, 5001),
            Map.entry(HotErrorCode.ERROR_NOT_FOUND, 5002),
            Map.entry(HotErrorCode.ERROR_INVALID_PARAM, 5003),
            Map.entry(HotErrorCode.ERROR_UNAUTHORIZED, 5004),
            Map.entry(HotErrorCode.ERROR_FORBIDDEN, 5005),
            Map.entry(HotErrorCode.ERROR_ALREADY_EXIST, 5006),
            Map.entry(HotErrorCode.ERROR_DB_SELECT, 5007),
            Map.entry(HotErrorCode.ERROR_DB_UPDATE, 5008));

    @Test
    @DisplayName("Go const 块恰好 11 个常量——多一个少一个都算契约变更")
    void thereAreExactlyElevenCodes() {
        // Go errmsg.go:34-46 共 11 行常量；漏迁或擅自新增都会让计数对不上。
        assertEquals(11, HotErrorCode.values().length);
    }

    @Test
    @DisplayName("每个常量的码值逐条等于 Go errmsg.go 的 const 值（L1 跨服务契约）")
    void everyCodeValueMatchesGoOneByOne() {
        for (Map.Entry<HotErrorCode, Integer> expected : GO_CONST_VALUES.entrySet()) {
            assertEquals(expected.getValue().intValue(), expected.getKey().getCode(),
                    "码值漂移：" + expected.getKey() + " 的码值与 Go errmsg.go 不一致");
        }
    }

    @Test
    @DisplayName("枚举声明顺序与 Go const 块书写顺序一致，便于逐行 review")
    void declarationOrderMatchesGoSourceOrder() {
        // Go errmsg.go 的书写顺序：通用（200/500/1015）→ 系统与基础设施（5001-5008）。
        List<HotErrorCode> expectedOrder = List.of(
                HotErrorCode.SUCCESS,
                HotErrorCode.ERROR,
                HotErrorCode.CODE_SERVER_BUSY,
                HotErrorCode.ERROR_SERVER_COMMON,
                HotErrorCode.ERROR_NOT_FOUND,
                HotErrorCode.ERROR_INVALID_PARAM,
                HotErrorCode.ERROR_UNAUTHORIZED,
                HotErrorCode.ERROR_FORBIDDEN,
                HotErrorCode.ERROR_ALREADY_EXIST,
                HotErrorCode.ERROR_DB_SELECT,
                HotErrorCode.ERROR_DB_UPDATE);

        assertEquals(expectedOrder, List.of(HotErrorCode.values()),
                "枚举顺序与 Go 不一致；Go 侧 codeMsg 与 const 是分开的，顺序漂移会让逐行对照失效");
    }

    @Test
    @DisplayName("码值无重复——重复会让 code→枚举 反查静默丢码")
    void codeValuesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (HotErrorCode ec : HotErrorCode.values()) {
            assertTrue(seen.add(ec.getCode()),
                    "码值重复：" + ec + " 的码值 " + ec.getCode() + " 已被前面的常量占用");
        }
        assertEquals(11, seen.size());
    }

    @Test
    @DisplayName("getCode() 产出裸 int，可直接喂给 BusinessException / Logger.logBusinessErr")
    void getCodeYieldsPrimitiveIntForExistingConsumers() {
        // 依据（均为既有基础设施，本模块零生产依赖、不引用它们的类型，只对齐签名）：
        //   com.rag.common.exception.BusinessException(int code, String msg)
        //   com.rag.common.logger.Logger.logBusinessErr(ctx, int code, Throwable, LogOption...)
        // 两者的 code 形参都是 int —— 若本枚举拿不到裸 int，这两条既有链路就接不上。
        for (HotErrorCode ec : HotErrorCode.values()) {
            int code = ec.getCode();
            assertEquals(ec, HotErrorCode.valueOf(ec.name()));
            assertTrue(code > 0, ec + " 的码值必须是正数");
        }
    }
}
