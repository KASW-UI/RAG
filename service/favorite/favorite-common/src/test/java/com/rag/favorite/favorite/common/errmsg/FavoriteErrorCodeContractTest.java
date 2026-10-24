package com.rag.favorite.favorite.common.errmsg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * favorite 域错误码<b>码值</b>契约测试 —— 真源
 * {@code Sea-RideTheWind_golang/service/favorite/common/errmsg.go:8-41} 的 const 块。
 *
 * <p><b>本测试断言的是「Go 的行为」，不是「Java 现在的行为」</b>：每条期望值都直接抄自 Go 源码行，
 * 不允许「照着当前实现写断言」——那只会把一个错误答案锁成绿色
 * （本仓库已吃过这个亏：AGENTS.md 记录的 DRIFT-MSGAPI-001/002/003
 * 三条漂移都曾被一条「断言了 RAG 错误行为」的绿测试锁死）。
 *
 * <p>覆盖四条真实风险：
 * <ol>
 *   <li><b>码值漂移</b>（L1 跨服务契约，改了客户端就解错码）；</li>
 *   <li><b>枚举重排 / 漏迁 / 擅自新增</b>；</li>
 *   <li><b>码值重复</b>（会让任何 code → 枚举 的反查静默丢码）；</li>
 *   <li><b>分组归属漂移</b>（{@code CodeServerBusy = 1015} 在 Go 源码里写在
 *       「通用错误码 5001-5099」注释下、却在 5001 之前，容易被"顺手归位"）。</li>
 * </ol>
 */
class FavoriteErrorCodeContractTest {

    /**
     * Go {@code errmsg.go:9-40} 的 const 块逐条抄录，顺序与 Go 书写顺序一致。
     * 用 {@link FavoriteErrorCode} 枚举名而非裸 int 作为期望值的键，避免两边一起错却仍然自洽。
     */
    private static final Map<FavoriteErrorCode, Integer> GO_CONST_VALUES = Map.ofEntries(
            // 通用（Go errmsg.go:9-10）
            Map.entry(FavoriteErrorCode.SUCCESS, 200),
            Map.entry(FavoriteErrorCode.ERROR, 500),
            // 通用错误码（Go errmsg.go:13-22）
            Map.entry(FavoriteErrorCode.CODE_SERVER_BUSY, 1015),
            Map.entry(FavoriteErrorCode.ERROR_SERVER_COMMON, 5001),
            Map.entry(FavoriteErrorCode.ERROR_NOT_FOUND, 5002),
            Map.entry(FavoriteErrorCode.ERROR_INVALID_PARAM, 5003),
            Map.entry(FavoriteErrorCode.ERROR_UNAUTHORIZED, 5004),
            Map.entry(FavoriteErrorCode.ERROR_FORBIDDEN, 5005),
            Map.entry(FavoriteErrorCode.ERROR_ALREADY_EXIST, 5006),
            Map.entry(FavoriteErrorCode.ERROR_DB_SELECT, 5007),
            Map.entry(FavoriteErrorCode.ERROR_DB_UPDATE, 5008),
            Map.entry(FavoriteErrorCode.ERROR_GENERATE_ID, 5009),
            // 用户模块 5100-5199（Go errmsg.go:25-29）
            Map.entry(FavoriteErrorCode.ERROR_USER_NOT_FOUND, 5101),
            Map.entry(FavoriteErrorCode.ERROR_USER_EXISTS, 5102),
            Map.entry(FavoriteErrorCode.ERROR_PASSWORD_INVALID, 5103),
            Map.entry(FavoriteErrorCode.ERROR_TOKEN_INVALID, 5104),
            Map.entry(FavoriteErrorCode.ERROR_TOKEN_EXPIRED, 5105),
            // 收藏夹模块 5200-5299（Go errmsg.go:32-34）
            Map.entry(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NOT_FOUND, 5201),
            Map.entry(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EMPTY, 5202),
            Map.entry(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EXISTS, 5203),
            // 收藏模块 5300-5399（Go errmsg.go:37-40）
            Map.entry(FavoriteErrorCode.ERROR_FAVORITE_NOT_FOUND, 5301),
            Map.entry(FavoriteErrorCode.ERROR_FAVORITE_ALREADY_EXIST, 5302),
            Map.entry(FavoriteErrorCode.ERROR_FAVORITE_TARGET_EMPTY, 5303),
            Map.entry(FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED, 5304));

    @Test
    @DisplayName("Go const 块恰好 24 个常量——多一个少一个都算契约变更")
    void thereAreExactlyTwentyFourCodes() {
        // Go errmsg.go:9-40 逐行计数共 24 行常量；漏迁或擅自新增都会让计数对不上。
        assertEquals(24, FavoriteErrorCode.values().length);
    }

    @Test
    @DisplayName("每个常量的码值逐条等于 Go errmsg.go 的 const 值（L1 跨服务契约）")
    void everyCodeValueMatchesGoOneByOne() {
        for (Map.Entry<FavoriteErrorCode, Integer> expected : GO_CONST_VALUES.entrySet()) {
            assertEquals(expected.getValue().intValue(), expected.getKey().getCode(),
                    "码值漂移：" + expected.getKey() + " 的码值与 Go errmsg.go 不一致");
        }
    }

    @Test
    @DisplayName("枚举声明顺序与 Go const 块书写顺序一致，便于逐行 review")
    void declarationOrderMatchesGoSourceOrder() {
        // Go errmsg.go 的书写顺序：通用（200/500）→ 通用错误码（1015 起，**1015 在 5001 之前**）
        // → 用户（5101-5105）→ 收藏夹（5201-5203）→ 收藏（5301-5304）。
        // 特别注意 1015：它在 Go 源码里位于「通用错误码」注释块的第一行，数值却是 1015 而非 500x。
        List<FavoriteErrorCode> expectedOrder = List.of(
                FavoriteErrorCode.SUCCESS,
                FavoriteErrorCode.ERROR,
                FavoriteErrorCode.CODE_SERVER_BUSY,
                FavoriteErrorCode.ERROR_SERVER_COMMON,
                FavoriteErrorCode.ERROR_NOT_FOUND,
                FavoriteErrorCode.ERROR_INVALID_PARAM,
                FavoriteErrorCode.ERROR_UNAUTHORIZED,
                FavoriteErrorCode.ERROR_FORBIDDEN,
                FavoriteErrorCode.ERROR_ALREADY_EXIST,
                FavoriteErrorCode.ERROR_DB_SELECT,
                FavoriteErrorCode.ERROR_DB_UPDATE,
                FavoriteErrorCode.ERROR_GENERATE_ID,
                FavoriteErrorCode.ERROR_USER_NOT_FOUND,
                FavoriteErrorCode.ERROR_USER_EXISTS,
                FavoriteErrorCode.ERROR_PASSWORD_INVALID,
                FavoriteErrorCode.ERROR_TOKEN_INVALID,
                FavoriteErrorCode.ERROR_TOKEN_EXPIRED,
                FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NOT_FOUND,
                FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EMPTY,
                FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EXISTS,
                FavoriteErrorCode.ERROR_FAVORITE_NOT_FOUND,
                FavoriteErrorCode.ERROR_FAVORITE_ALREADY_EXIST,
                FavoriteErrorCode.ERROR_FAVORITE_TARGET_EMPTY,
                FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED);

        assertEquals(expectedOrder, List.of(FavoriteErrorCode.values()),
                "枚举顺序与 Go 不一致；Go 侧 const 与 codeMsg 是分开的，顺序漂移会让逐行对照失效");
    }

    @Test
    @DisplayName("码值无重复——重复会让 code→枚举 反查静默丢码")
    void codeValuesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (FavoriteErrorCode ec : FavoriteErrorCode.values()) {
            assertTrue(seen.add(ec.getCode()),
                    "码值重复：" + ec + " 的码值 " + ec.getCode() + " 已被前面的常量占用");
        }
        assertEquals(24, seen.size());
    }

    @Test
    @DisplayName("getCode() 产出裸 int，可直接喂给 grpcError(grpcCode, bizCode) 与 envelope 的 code 字段")
    void getCodeYieldsPrimitiveIntForExistingConsumers() {
        // 依据（均为 Go 侧既有链路 / 本仓既有基础设施，本模块零反射引用它们的类型，只对齐签名）：
        //   Go rpc: GRPCError(grpcCode codes.Code, bizCode int)   —— bizCode 形参是 int
        //   Go api : 返回 (*types.XResp, int)，int 即业务码 —— 例如
        //          api/internal/logic/favorite/create_favorite_folder_logic.go:65 返回 Success
        //   com.rag.common.exception.BusinessException(int code, String msg)
        // 两处都要求裸 int；若本枚举拿不到 int，这两条既有链路就接不上。
        for (FavoriteErrorCode ec : FavoriteErrorCode.values()) {
            int code = ec.getCode();
            assertEquals(ec, FavoriteErrorCode.valueOf(ec.name()));
            assertTrue(code > 0, ec + " 的码值必须是正数");
        }
    }

    @Test
    @DisplayName("跨域同码不同义：favorite 的 1015 不得被 users / article 域的编号段覆盖")
    void favoriteKeepsItsOwnNumberingAgainstOtherDomains() {
        // favorite 域的 1015 = 「服务繁忙」；users / article / like 域在 1001-1015 段另有含义
        // （例如 like 的 1011 = 用户封禁、users 的 1008 = 无权限）。
        // RAG 多个域在同一仓库内演进，按别的域的直觉「顺手补齐 1001-1015 段」即为漂移。
        assertEquals(1015, FavoriteErrorCode.CODE_SERVER_BUSY.getCode());
        assertEquals("服务繁忙", FavoriteErrorMessages.getMsg(1015),
                "favorite 的 1015 必须是「服务繁忙」，不能被别的域的 1015 含义覆盖");
    }
}