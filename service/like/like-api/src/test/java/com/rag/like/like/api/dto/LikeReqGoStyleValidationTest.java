package com.rag.like.like.api.dto;

import com.rag.users.common.http.GoRequiredFieldException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * go-zero 解析层契约的 DTO 级测试 —— 锁住 {@code assertGoRequiredFields()} 的判定矩阵。
 *
 * <h3>为什么需要这一层，而不是只靠 MVC 端到端用例</h3>
 *
 * <p>端到端用例只能观察 <b>envelope</b>。但 Java 侧存在一类「<b>去掉校验后 envelope 不变</b>」的情形：
 * <pre>
 *   删掉 cursor 的必填校验 ⇒ req.getCursor() 返回 null
 *                        ⇒ protobuf setCursor(Long) 自动拆箱抛 NPE
 *                        ⇒ GlobalExceptionHandler.handleAny 兜底成 5001
 *   ⇒ 端到端看到的仍然是 {"code":5001}，且 rpc 同样没被调用，<b>完全不可区分</b>
 * </pre>
 * （这个洞实测踩过一次：先按「两个字段一起缺」写用例 → 删掉校验仍绿；
 * 改成只缺一个字段仍绿，因为 NPE 顶上来了。）
 *
 * <p>本类把规则钉在<b>它真正生效的那一层</b>（DTO 自身），从而：
 * <ul>
 *   <li>保住 Go 的语义（<b>标量与 Slice 都必填、options 取值域</b>，
 *       两者共用同一判据，只有错误文案不同）；</li>
 *   <li>让「去掉校验」这件事在测试里<b>必然变红</b>，而不是靠 NPE 巧合顶住；</li>
 *   <li>同时让实现不必依赖 NPE 这种「恰好同输出」的副作用。</li>
 * </ul>
 *
 * <p><b>Go 出处</b>（{@code go-zero@v1.10.2/core/mapping/unmarshaler.go:944-950}）：
 * <pre>
 *   switch typeKind {
 *   case reflect.Array, reflect.Map, reflect.Slice:
 *       if !opts.optional() { return u.processFieldNotFromString(... emptyMap ...) }
 *   default:                                 ← String / Int / Bool 等标量
 *       if !opts.optional() { return newInitError(fullName) }   ← field "x" is not set
 *   }
 * </pre>
 *
 * <p>🔴 <b>2026-10-23 修正错误断言（DRIFT-LIKEAPI-019）</b>：本段原先写的是
 * 「标量必填 / <b>Slice 永不计必填</b>」，且给上面那段 Go 代码标了 {@code ← 不报错}。
 * <b>两处都错</b>，且错得自相矛盾 —— 同一个文件的 59-73 行早已写对
 * （「{@code target_ids} 也是必填 —— Slice 不豁免」）：
 * <ol>
 *   <li>Slice 分支<b>不是</b> {@code return nil}，而是把 {@code emptyMap} 交给
 *       {@code processFieldNotFromString} → {@code default:} → {@code processFieldPrimitive}
 *       → {@code typeKind(Slice) ≠ valueKind(Map)} → {@code newTypeMismatchError}；</li>
 *   <li>⇒ 无 tag 的 Slice 与标量<b>同样必填</b>，只是<b>错误文案不同</b>
 *       （Slice 报 {@code type mismatch for field "target_ids"}，
 *       标量报 {@code field "target_ids" is not set}）。</li>
 * </ol>
 * ⚠️ 活体证据：Go 探针 B02（{@code {"target_type":"a"}}，缺 target_ids）
 * ⇒ {@code ERR type mismatch for field "target_ids"}；B04（{@code target_ids:[]}）⇒ {@code OK}。
 * ⇒ 这正是 <b>DRIFT-LIKEAPI-002</b> 的案发现场：照着上面那句错误注释「简化」Slice 校验，
 * 会把已修好的漂移原样改回来。
 */
class LikeReqGoStyleValidationTest {

    // ==================== 标量必填 与 Slice 必填（同一判据，不同报错文案）====================

    @Test
    @DisplayName("⚠️ target_type 必填（标量）：缺席即抛")
    void scalarTargetTypeIsRequired() {
        GetLikeCountReq req = new GetLikeCountReq();
        GoRequiredFieldException e = assertThrows(
                GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertTrue(e.getMessage().contains("target_type"));
    }

    @Test
    @DisplayName("⚠️ target_ids 也是必填 —— Slice 不豁免（DRIFT-LIKEAPI-002 回归锁）")
    void targetIdsIsAlsoRequired() {
        // ⚠️ 本用例在 2026-10-22 **反向过**：原 DisplayName 写着「Go: Slice 永不计必填」，
        //   那个 Go 结论已被独立复核（活体探针）推翻。
        //   真实控制流（go-zero v1.10.2 core/mapping/unmarshaler.go:944-950）：
        //     case reflect.Array, reflect.Map, reflect.Slice:
        //         if !opts.optional() {
        //             return u.processFieldNotFromString(fieldType, value,
        //                        valueWithParent{value: emptyMap}, opts, fullName)   ← 不是 return nil
        //         }
        //   → 落到 default: → processFieldPrimitive，typeKind(Slice) ≠ valueKind(Map)
        //   → newTypeMismatchError ⇒ 「type mismatch for field \"target_ids\"」
        // 结论：**无 tag 的 Slice 与标量一样必填**，只是错误文案不同。
        //   两者在本服务都不可观测（文本不进 body，恒为 5001），故统一用
        //   GoRequiredFieldException 表达。
        GetLikeCountReq req = new GetLikeCountReq();
        req.setTargetType("article");
        GoRequiredFieldException e = assertThrows(
                GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertTrue(e.getMessage().contains("target_ids"));

        GetLikeStateReq stateReq = new GetLikeStateReq();
        stateReq.setTargetType("article");
        assertThrows(GoRequiredFieldException.class, stateReq::assertGoRequiredFields);
    }

    @Test
    @DisplayName("⚠️「必填」= 键必须在，不是数组非空 —— target_ids: [] 是合法值")
    void requiredMeansPresentNotNonEmptyList() {
        GetLikeCountReq emptyList = new GetLikeCountReq();
        emptyList.setTargetType("article");
        emptyList.setTargetIds(java.util.List.of());
        assertDoesNotThrow(emptyList::assertGoRequiredFields,
                "键存在即为满足；空数组是合法输入，会走到 rpc 的 2001 分支");
    }

    @Test
    @DisplayName("⚠️「必填」= 键必须在，不是值不能为 0 —— 空串与 0 都是合法值")
    void requiredMeansPresentNotNonZero() {
        GetLikeCountReq emptyString = new GetLikeCountReq();
        emptyString.setTargetType("");
        emptyString.setTargetIds(java.util.List.of("1"));
        assertDoesNotThrow(emptyString::assertGoRequiredFields, "Go 的 json 通道里空串是合法值");

        GetTargetLikerListReq zeros = new GetTargetLikerListReq();
        zeros.setTargetType("article");
        zeros.setTargetId("1");
        zeros.setCursor(0L);
        zeros.setPageSize(0);
        assertDoesNotThrow(zeros::assertGoRequiredFields);
    }

    // ==================== 逐字段隔离 ====================

    @Test
    @DisplayName("⚠️ cursor 单独缺席即抛（端到端测不到这一层，NPE 会顶替它）")
    void cursorAloneIsRequired() {
        GetTargetLikerListReq req = new GetTargetLikerListReq();
        req.setTargetType("article");
        req.setTargetId("1");
        req.setPageSize(20);            // 其余三个字段齐备，只缺 cursor
        GoRequiredFieldException e = assertThrows(
                GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertTrue(e.getMessage().contains("cursor"));
    }

    @Test
    @DisplayName("⚠️ page_size 单独缺席即抛")
    void pageSizeAloneIsRequired() {
        GetTargetLikerListReq req = new GetTargetLikerListReq();
        req.setTargetType("article");
        req.setTargetId("1");
        req.setCursor(0L);
        GoRequiredFieldException e = assertThrows(
                GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertTrue(e.getMessage().contains("page_size"));
    }

    @Test
    @DisplayName("GetUserLikeListReq 的 cursor / page_size 同样必填")
    void userLikeListPaginationIsRequired() {
        GetUserLikeListReq req = new GetUserLikeListReq();
        req.setTargetType("article");
        req.setCursor(0L);
        // 缺 page_size
        GoRequiredFieldException e = assertThrows(
                GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertTrue(e.getMessage().contains("page_size"));
    }

    @Test
    @DisplayName("⚠️ 零字段 DTO 永不抛（Go 的 getusertotallike）")
    void zeroFieldDtoNeverThrows() {
        assertDoesNotThrow(new GetUserTotalLikeReq()::assertGoRequiredFields);
    }

    // ==================== options=1|2|3|4 ====================

    @Test
    @DisplayName("⚠️ action_type 超出 options=1|2|3|4 即抛")
    void actionTypeMustBeInOptions() {
        for (long illegal : new long[]{0L, 5L, -1L, 100L}) {
            LikeActionReq req = validActionReq();
            req.setActionType(illegal);
            assertThrows(IllegalArgumentException.class, req::assertGoRequiredFields,
                    "action_type=" + illegal + " 应当被 options 拒绝");
        }
    }

    @Test
    @DisplayName("options 的四个合法值逐个放行")
    void actionTypeAcceptsAllFourOptions() {
        for (long legal : new long[]{1L, 2L, 3L, 4L}) {
            LikeActionReq req = validActionReq();
            req.setActionType(legal);
            assertDoesNotThrow(req::assertGoRequiredFields, "action_type=" + legal + " 应当被接受");
        }
    }

    @Test
    @DisplayName("action_type 缺席先按必填处理（不是 options 报错）")
    void actionTypeAbsentIsRequiredFieldError() {
        LikeActionReq req = new LikeActionReq();
        req.setTargetType("a");
        req.setTargetId("1");
        req.setAuthorId(1L);
        GoRequiredFieldException e = assertThrows(
                GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertTrue(e.getMessage().contains("action_type"));
    }

    // ==================== ,optional ====================

    @Test
    @DisplayName("⚠️ ,optional 字段缺席不抛：weight / client_ip / promotion_id")
    void optionalFieldsMayBeAbsent() {
        LikeActionReq req = validActionReq();
        req.setWeight(null);
        req.setClientIp(null);
        req.setPromotionId(null);
        assertDoesNotThrow(req::assertGoRequiredFields);
    }

    @Test
    @DisplayName("author_id 是 ,string 字段：两种输入形态都解析成同一个 long")
    void stringTaggedAuthorIdAcceptsBothShapes() {
        LikeActionReq fromString = validActionReq();
        fromString.setAuthorId("123");
        assertDoesNotThrow(fromString::assertGoRequiredFields);

        LikeActionReq fromNumber = validActionReq();
        fromNumber.setAuthorId(123);
        assertDoesNotThrow(fromNumber::assertGoRequiredFields);

        org.junit.jupiter.api.Assertions.assertEquals(123L, fromString.getAuthorId());
        org.junit.jupiter.api.Assertions.assertEquals(123L, fromNumber.getAuthorId());
    }

    @Test
    @DisplayName("⚠️ author_id 传浮点即拒（Go: strconv.ParseInt(\"1.5\") 失败）")
    void stringTaggedAuthorIdRejectsFloat() {
        LikeActionReq req = validActionReq();
        assertThrows(IllegalArgumentException.class, () -> req.setAuthorId(1.5));
    }

    @Test
    @DisplayName("author_id 缺席按必填处理（Go: field \"author_id\" is not set）")
    void authorIdAbsentIsRequired() {
        LikeActionReq req = new LikeActionReq();
        req.setTargetType("a");
        req.setTargetId("1");
        req.setActionType(1L);
        GoRequiredFieldException e = assertThrows(
                GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertTrue(e.getMessage().contains("author_id"));
    }

    private static LikeActionReq validActionReq() {
        LikeActionReq req = new LikeActionReq();
        req.setTargetType("article");
        req.setTargetId("1");
        req.setActionType(1L);
        req.setAuthorId(1L);
        return req;
    }

    @Test
    @DisplayName("int64 值域边界：±2^63 内收、±2^63 外拒（锁定 bitLength 判据的正确性）")
    void int64BoundaryHandlingIsCorrect() {
        // ⚠️ 本用例记录一次**误报**的纠正过程，别再照着旧说法改：
        //   一次独立复核曾断言「BigInteger.bitLength() 不含符号位 ⇒ -2^63 的 bitLength 是 64
        //   ⇒ 被 `>= 64` 误拒」，并把它记为 DRIFT-LIKEAPI-009。
        //   **那个前提是错的**：实测 Long.MIN_VALUE 的 bitLength 是 **63**，不是 64。
        //   ⇒ 原来的判据本就正确，不存在漂移。
        //   下面的断言把真实的不变量钉死：bitLength >= 64 ⟺ 越出 int64 值域。
        java.math.BigInteger min = java.math.BigInteger.valueOf(Long.MIN_VALUE);
        java.math.BigInteger max = java.math.BigInteger.valueOf(Long.MAX_VALUE);
        java.math.BigInteger hiOut = java.math.BigInteger.ONE.shiftLeft(63);            // 2^63
        java.math.BigInteger loOut = hiOut.negate().subtract(java.math.BigInteger.ONE);   // -2^63-1

        // 「去掉符号位」这条性质的直接体现：两端点都是 63，而不是 64
        assertEquals(63, min.bitLength(), "Long.MIN_VALUE 的 bitLength 是 63 —— 旧误报的前提搞反了");
        assertEquals(63, max.bitLength());
        // 越界值才是 64
        assertEquals(64, hiOut.bitLength());
        assertEquals(64, loOut.bitLength());

        // 值域内：两侧边界都必须被接受（Go: strconv.ParseInt 合法）
        for (java.math.BigInteger inRange : new java.math.BigInteger[]{min, max}) {
            LikeActionReq req = validActionReq();
            req.setAuthorId(inRange);
            assertDoesNotThrow(req::assertGoRequiredFields, "值域内应被接受：" + inRange);
            assertEquals(inRange.longValue(), req.getAuthorId());
        }

        // 值域外：必须被拒
        for (java.math.BigInteger outOfRange : new java.math.BigInteger[]{hiOut, loOut}) {
            LikeActionReq req = validActionReq();
            assertThrows(IllegalArgumentException.class, () -> req.setAuthorId(outOfRange),
                    "越出 int64 应被拒：" + outOfRange);
        }
    }

    @Test
    @DisplayName("⚠️ target_ids 显式 null 与缺席同判（两者在 Go 上都解析失败，只是文案不同）")
    void targetIdsExplicitNullIsAlsoRejected() {
        // Go 侧两种形态走的是同一段控制流：
        //   缺席   → processFieldNotFromString(emptyMap) → type mismatch
        //   显式null → processFieldWithValue: mapValue == nil → mustn't be nil
        // 两者最终都是「解析失败」，故本服务统一折叠成 5001（文本不进 body，不可见）。
        GetLikeCountReq req = new GetLikeCountReq();
        req.setTargetType("article");
        req.setTargetIds(null);
        assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
    }
}