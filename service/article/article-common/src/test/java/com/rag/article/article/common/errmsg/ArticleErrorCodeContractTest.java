package com.rag.article.article.common.errmsg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * article 域错误码契约测试 —— 真源 {@code Sea-RideTheWind_golang/service/article/common/errmsg/errmsg.go}。
 *
 * <p><b>本测试断言的是「Go 的行为」，不是「Java 现在的行为」</b>：每条期望值都直接抄自 Go 源码行，
 * 不允许「照着当前实现写断言」——那样只会把一个错误答案锁成绿色。
 *
 * <p>覆盖三条真实风险：
 * <ol>
 *   <li><b>码值 / 文案漂移</b>（L1 跨服务契约，改了客户端就解错码）；</li>
 *   <li><b>未知码回落被改坏</b>（L2 本组件唯一有分支的行为）；</li>
 *   <li><b>跨域同码不同义被「好心」抹平</b>（本组件最容易发生、且最难被发现的错误）。</li>
 * </ol>
 */
class ArticleErrorCodeContractTest {

    /**
     * Go {@code errmsg.go:3-19} 的 const 块逐条抄录，顺序与 Go 书写顺序一致。
     * 用 {@link ErrorCode} 枚举名而非裸 int 作为期望值的键，避免两边一起错却仍然自洽。
     */
    private static final Map<ErrorCode, Integer> GO_CONST_VALUES = Map.ofEntries(
            Map.entry(ErrorCode.SUCCESS, 200),
            Map.entry(ErrorCode.ERROR, 500),
            Map.entry(ErrorCode.CODE_SERVER_BUSY, 1015),
            Map.entry(ErrorCode.ERROR_ARTICLE_EXIST, 1001),
            Map.entry(ErrorCode.ERROR_ARTICLE_NONE, 1002),
            Map.entry(ErrorCode.ERROR_ARTICLE_PUBLISHING, 1003),
            Map.entry(ErrorCode.ERROR_ARTICLE_PUBLISH_FAILED, 1004),
            Map.entry(ErrorCode.ERROR_UNAUTHORIZED, 1005),
            Map.entry(ErrorCode.ERROR_ARTICLE_FORBIDDEN, 1006),
            Map.entry(ErrorCode.ERROR_SERVER_COMMON, 5001),
            Map.entry(ErrorCode.ERROR_DB_UPDATE, 5002),
            Map.entry(ErrorCode.ERROR_DB_SELECT, 5003),
            Map.entry(ErrorCode.ERROR_MINIO_UPLOAD, 5004),
            Map.entry(ErrorCode.ERROR_MINIO_DELETE, 5005),
            Map.entry(ErrorCode.ERROR_MINIO_DOWNLOAD, 5006));

    /** Go {@code errmsg.go:21-37} 的 {@code codeMsg} 表逐条抄录。 */
    private static final Map<ErrorCode, String> GO_CODE_MSG = Map.ofEntries(
            Map.entry(ErrorCode.SUCCESS, "OK"),
            Map.entry(ErrorCode.ERROR, "FAIL"),
            Map.entry(ErrorCode.CODE_SERVER_BUSY, "服务繁忙"),
            Map.entry(ErrorCode.ERROR_ARTICLE_EXIST, "文章已存在"),
            Map.entry(ErrorCode.ERROR_ARTICLE_NONE, "文章不存在"),
            Map.entry(ErrorCode.ERROR_ARTICLE_PUBLISHING, "文章处理中，请稍后查看"),
            Map.entry(ErrorCode.ERROR_ARTICLE_PUBLISH_FAILED, "文章发布失败"),
            Map.entry(ErrorCode.ERROR_UNAUTHORIZED, "请先登录"),
            Map.entry(ErrorCode.ERROR_ARTICLE_FORBIDDEN, "只能编辑或删除自己的文章"),
            Map.entry(ErrorCode.ERROR_SERVER_COMMON, "系统内部错误"),
            Map.entry(ErrorCode.ERROR_DB_UPDATE, "更新数据库失败"),
            Map.entry(ErrorCode.ERROR_DB_SELECT, "查询数据库失败"),
            Map.entry(ErrorCode.ERROR_MINIO_UPLOAD, "文件上传失败"),
            Map.entry(ErrorCode.ERROR_MINIO_DELETE, "文件删除失败"),
            Map.entry(ErrorCode.ERROR_MINIO_DOWNLOAD, "文件下载失败"));

    @Test
    @DisplayName("Go const 块恰好 15 个常量——多一个少一个都算契约变更")
    void thereAreExactlyFifteenCodes() {
        // Go errmsg.go:4-18 共 15 行常量；漏迁或擅自新增都会让计数对不上。
        assertEquals(15, ErrorCode.values().length);
    }

    @Test
    @DisplayName("每个常量的码值逐条等于 Go errmsg.go 的 const 值（L1 跨服务契约）")
    void everyCodeValueMatchesGoOneByOne() {
        for (Map.Entry<ErrorCode, Integer> expected : GO_CONST_VALUES.entrySet()) {
            assertEquals(expected.getValue().intValue(), expected.getKey().getCode(),
                    "码值漂移：" + expected.getKey() + " 的码值与 Go errmsg.go 不一致");
        }
    }

    @Test
    @DisplayName("每条文案逐条等于 Go errmsg.go 的 codeMsg 值，含中文标点")
    void everyMessageMatchesGoOneByOne() {
        for (Map.Entry<ErrorCode, String> expected : GO_CODE_MSG.entrySet()) {
            assertEquals(expected.getValue(), expected.getKey().getMsg(),
                    "文案漂移：" + expected.getKey() + " 的文案与 Go errmsg.go 不一致");
        }
    }

    @Test
    @DisplayName("枚举声明顺序与 Go const 块书写顺序一致，便于逐行 review")
    void declarationOrderMatchesGoSourceOrder() {
        // Go errmsg.go 的书写顺序：通用 → 文章业务 → 系统与基础设施。
        List<ErrorCode> expectedOrder = List.of(
                ErrorCode.SUCCESS,
                ErrorCode.ERROR,
                ErrorCode.CODE_SERVER_BUSY,
                ErrorCode.ERROR_ARTICLE_EXIST,
                ErrorCode.ERROR_ARTICLE_NONE,
                ErrorCode.ERROR_ARTICLE_PUBLISHING,
                ErrorCode.ERROR_ARTICLE_PUBLISH_FAILED,
                ErrorCode.ERROR_UNAUTHORIZED,
                ErrorCode.ERROR_ARTICLE_FORBIDDEN,
                ErrorCode.ERROR_SERVER_COMMON,
                ErrorCode.ERROR_DB_UPDATE,
                ErrorCode.ERROR_DB_SELECT,
                ErrorCode.ERROR_MINIO_UPLOAD,
                ErrorCode.ERROR_MINIO_DELETE,
                ErrorCode.ERROR_MINIO_DOWNLOAD);

        assertEquals(expectedOrder, List.of(ErrorCode.values()),
                "枚举顺序与 Go 不一致；Go 侧 codeMsg 与 const 是分开的，顺序漂移会让逐行对照失效");
    }

    @Test
    @DisplayName("码值无重复——重复会让 code→枚举 反查静默丢码")
    void codeValuesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode ec : ErrorCode.values()) {
            assertTrue(seen.add(ec.getCode()),
                    "码值重复：" + ec + " 的码值 " + ec.getCode() + " 已被前面的常量占用");
        }
        assertEquals(15, seen.size());
    }

    @Test
    @DisplayName("未知码回落为 \"FAIL\"，不是 null、不是空串、不是 code 的字符串（L2 唯一分支）")
    void unknownCodeFallsBackToFail() {
        // Go errmsg.go:39-45：msg, ok := codeMsg[code]; if !ok { return codeMsg[Error] }
        // codeMsg[Error] 即 500 号的文案 "FAIL"。
        List<Integer> unknownCodes = List.of(
                0,                          // 零值
                -1,                         // 负数
                1,                          // 介于两个真实码之间
                999,                        // 1000~1014 空档
                1016,                       // 1015 与 2001 之间
                2001,                       // ⚠️ 真实存在于 users/like 域，article 域必须回落
                5013,                       // 5006 之后
                5007,                       // ⚠️ 真实存在于 users 域（JSON序列化失败）
                Integer.MAX_VALUE,
                Integer.MIN_VALUE);

        for (int code : unknownCodes) {
            assertEquals("FAIL", ErrorCode.getMsg(code),
                    "未知码 " + code + " 必须回落为 \"FAIL\"（Go: codeMsg[Error]）");
            assertSame(ErrorCode.ERROR, ErrorCode.of(code),
                    "未知码 " + code + " 必须回落到 ERROR 枚举，不得返回 null");
        }
    }

    @Test
    @DisplayName("getMsg(int) 对每个已登记码都能取回自身文案（查表与枚举不自洽会漏文案）")
    void getMsgRoundTripsForEveryRegisteredCode() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertEquals(ec.getMsg(), ErrorCode.getMsg(ec.getCode()),
                    ec + "：getMsg(int) 与实例 getMsg() 结果不一致");
        }
    }

    @Test
    @DisplayName("getCode() 产出裸 int，可直接喂给 BusinessException / Logger.logBusinessErr")
    void getCodeYieldsPrimitiveIntForExistingConsumers() {
        // 依据：com.rag.common.exception.BusinessException(int, String) 与
        //       com.rag.common.logger.Logger.logBusinessErr(ctx, int code, Throwable, LogOption...)
        // 两者的 code 形参都是 int —— 若本类只提供枚举而拿不到 int，这两条既有链路就接不上。
        for (ErrorCode ec : ErrorCode.values()) {
            int code = ec.getCode();
            assertEquals(ec, ErrorCode.of(code));
            assertNotNull(ErrorCode.getMsg(code));
        }
    }

    /**
     * article 与 users / like 是<b>不同域但共用同一段 int 值空间</b>，
     * 且 article 的 15 个码<b>全部</b>都能在 users 域找到同号。
     *
     * <p>其中 <b>6 个是同源模板、两侧文案相同</b>（200 / 500 / 1015 / 5001 / 5002 / 5003），
     * <b>9 个是同号不同义</b> —— 后者才是危险区：按「users 域 5005 = 事务操作失败」的直觉去
     * 「修正」article，就会把客户端可见的文案改错。
     *
     * <p>⇒ 这里把 <b>9 个</b>同号不同义的码逐条钉死（早前版本只钉了 5 个，漏了 1001~1004）。
     * 期望值全部写成<b>字面量</b>而不是引用 users 模块的当前值 ——
     * users 日后改文案不应连带改掉本断言（article 的真源是 Go，与 users 无关）。
     */
    @Test
    @DisplayName("跨域同码不同义：article 的 9 个同号不同义码不得被改成 users/like 域的含义")
    void codesDoNotCollideWithUsersDomainMeaning() {
        // (article 码值, article 自己的文案, users/like 域同号的确切含义)
        record Collision(int code, String articleMsg, String usersMsg) {
        }
        List<Collision> collisions = List.of(
                new Collision(1001, "文章已存在", "用户名已存在"),
                new Collision(1002, "文章不存在", "用户名或密码错误"),
                new Collision(1003, "文章处理中，请稍后查看", "用户不存在"),
                new Collision(1004, "文章发布失败", "TOKEN不存在"),
                new Collision(1005, "请先登录", "TOKEN格式错误"),
                new Collision(1006, "只能编辑或删除自己的文章", "TOKEN已过期"),
                new Collision(5004, "文件上传失败", "数据库插入失败"),
                new Collision(5005, "文件删除失败", "事务操作失败"),
                new Collision(5006, "文件下载失败", "雪花ID生成失败"));

        for (Collision c : collisions) {
            assertAll(
                    () -> assertEquals(c.articleMsg(), ErrorCode.getMsg(c.code()),
                            "article 的 " + c.code() + " 必须是「" + c.articleMsg() + "」"),
                    () -> assertNotEquals(c.usersMsg(), ErrorCode.getMsg(c.code()),
                            "article 的 " + c.code() + " 不能被 users/like 域的含义覆盖成「"
                                    + c.usersMsg() + "」"),
                    () -> assertNotEquals(c.usersMsg(), ErrorCode.of(c.code()).getMsg(),
                            "article 的 " + c.code() + " 不能被 users/like 域的含义覆盖成「"
                                    + c.usersMsg() + "」"));
        }
    }

    /**
     * 与 {@link #codesDoNotCollideWithUsersDomainMeaning()} 互补：另外 <b>6 个</b>码
     * 在 users / like 域是<b>同源模板、两侧文案本来就相同</b>。
     *
     * <p>把它们也钉死，是为了防止反向操作 —— 有人看到「5004 = 文件上传失败」与别域不同，
     * 就「顺手统一」成「数据库插入失败」。那同样是漂移：article 的真源是 Go，
     * Go 侧这两个码的含义就是不同的。
     */
    @Test
    @DisplayName("跨域同号同义的 6 个码：article 保持模板文案，不得被「统一」成别的说法")
    void sharedTemplateCodesKeepTheirTemplateWording() {
        Map<Integer, String> shared = Map.of(
                200, "OK",
                500, "FAIL",
                1015, "服务繁忙",
                5001, "系统内部错误",
                5002, "更新数据库失败",
                5003, "查询数据库失败");

        for (Map.Entry<Integer, String> e : shared.entrySet()) {
            assertEquals(e.getValue(), ErrorCode.getMsg(e.getKey()),
                    "article 的 " + e.getKey() + " 是同源模板码，文案必须是「" + e.getValue() + "」");
        }
    }

    /**
     * Go 侧 {@code const} 块（{@code errmsg.go:3-19}）与 {@code codeMsg} 表（{@code errmsg.go:21-37}）
     * 是<b>两个独立结构</b>；Java 侧把二者合并成了一个枚举。
     *
     * <p>合并本身不改变可观察行为，但<b>合并动作是可能丢东西的</b>：漏掉一个 const、
     * 或漏掉一条 table entry，在「表 = 枚举的 msg 字段」的单结构下都看不出来。
     *
     * <p>⇒ 这里把 Go 的<b>两个结构分别抄录成裸 int 集合</b>（刻意不共用同一份字面量），
     * 断言二者相等，再断言 Java 枚举的码集合与二者都相等 ⇒ 「合并没丢东西」被钉死。
     *
     * <p>⚠️ <b>本测试不是 Go 一致性的锚</b>：任何纯 Java 侧测试对 Go 都只能靠「人工抄录」建立联系。
     * 真正的锚是 {@code tools/errmsg-probe/run-parity.sh} —— 它跑真源 errmsg.go 与真实 ErrorCode，
     * 双侧逐字节比对。本测试的职责是<b>回归守门</b>：改动 Java 枚举时立刻变红。
     */
    @Test
    @DisplayName("Go 的 const 集合 ≡ Go 的 codeMsg 键集合 ≡ Java 枚举的码集合（合并成单枚举没丢东西）")
    void goConstSetEqualsGoCodeMsgSetEqualsJavaEnumSet() {
        // Go errmsg.go:3-19 的 const 块 —— 裸 int 抄录。
        Set<Integer> goConstCodes = Set.of(
                200, 500, 1015,
                1001, 1002, 1003, 1004, 1005, 1006,
                5001, 5002, 5003, 5004, 5005, 5006);

        // Go errmsg.go:21-37 的 codeMsg 表的键 —— 裸 int 抄录（**独立抄一遍，不复用上面的字面量**）。
        Set<Integer> goCodeMsgKeys = Set.of(
                200, 500, 1015,
                1001, 1002, 1003, 1004, 1005, 1006,
                5001, 5002, 5003, 5004, 5005, 5006);

        assertEquals(15, goConstCodes.size(), "Go const 块抄录出来应有 15 个码");
        assertEquals(15, goCodeMsgKeys.size(), "Go codeMsg 表抄录出来应有 15 个键");
        assertEquals(goConstCodes, goCodeMsgKeys,
                "Go 侧 const 块与 codeMsg 表登记的码集合不相等 —— 真源自身的不变量被破坏"
                        + "（Go 的 api 层会因此在信封里意外回落成 \"FAIL\"）");

        Set<Integer> javaEnumCodes = new HashSet<>();
        for (ErrorCode ec : ErrorCode.values()) {
            javaEnumCodes.add(ec.getCode());
        }
        assertEquals(goConstCodes, javaEnumCodes,
                "Java 枚举的码集合与 Go 不一致 —— 合并成单枚举时漏迁/多迁了码");
    }

    @Test
    @DisplayName("15 条文案两两互异")
    void messagesAreMutuallyDistinct() {
        // ⚠️ 为什么 article 域也要守这条：Go 侧的 article 包**没有**反查（无 bizCodeByMsg），
        // 所以「文案重复」在 Go 侧不构成正确性前提。但它一旦发生，
        // 后续任何按文案反推码的排障规则、告警规则都会变成「先到先得」的随机行为。
        // 守它是为了让「两条文案相同」必须是一个**显式决策**，而不是一次复制粘贴的意外。
        Map<String, ErrorCode> byMsg = new LinkedHashMap<>();
        for (ErrorCode ec : ErrorCode.values()) {
            ErrorCode prev = byMsg.put(ec.getMsg(), ec);
            assertNull(prev, "文案重复：" + ec.getMsg() + " 同时用于 " + prev + " 与 " + ec);
        }
        assertEquals(15, byMsg.size());
    }

    @Test
    @DisplayName("每个码都同时有枚举常量与非空白文案（无「有码无文案」的空洞）")
    void everyCodeHasBothAConstantAndANonBlankMessage() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertNotNull(ec.getMsg(), ec + " 的文案为 null —— Go 侧 codeMsg 的值都非空");
            assertFalse(ec.getMsg().isBlank(), ec + " 的文案是空白 —— Go 侧 codeMsg 的值都非空白");
        }
    }

    @Test
    @DisplayName("枚举不可变：values() 每次返回新数组，of() 返回同一实例，getMsg() 是纯函数")
    void errorCodeIsImmutableAndSideEffectFree() {
        // Go 侧 codeMsg 是包级 var，理论上可被同包代码改写；Java 侧刻意用 final 字段 +
        // 不可变 Map 杜绝这种可能 —— 这是**表达层加固**，不改变可观察行为。
        ErrorCode[] first = ErrorCode.values();
        first[0] = ErrorCode.ERROR;                 // 试图篡改返回的数组
        assertSame(ErrorCode.SUCCESS, ErrorCode.values()[0],
                "values() 必须返回防御性副本，调用方改写它不得影响后续调用");

        assertSame(ErrorCode.of(1002), ErrorCode.of(1002),
                "of() 必须对同一码返回同一实例（不得每次新建）");
        assertSame(ErrorCode.ERROR, ErrorCode.of(12345),
                "of() 对未登记码必须回落到同一个 ERROR 实例");

        String m1 = ErrorCode.getMsg(1002);
        String m2 = ErrorCode.getMsg(1002);
        assertEquals(m1, m2, "getMsg() 必须是纯函数（两次调用结果必须相等）");
    }

    @Test
    @DisplayName("article 域不搬 users/like 域独有的码")
    void doesNotCarryOverOtherDomainsExclusiveCodes() {
        // users 域独有 5011/5012（Redis 查询/更新失败）、1001（用户名已存在）等。
        // article 的 errmsg.go 没有就是没有——照抄「看起来通用」的错误码会让
        // 告警与文案在两个域之间产生歧义。
        for (int code : new int[]{5011, 5012}) {
            assertEquals("FAIL", ErrorCode.getMsg(code),
                    "code " + code + " 不属于 article 域，必须回落 \"FAIL\"");
        }
        // 1001 在 article 域有独立含义（文章已存在），不是「用户名已存在」。
        assertEquals("文章已存在", ErrorCode.getMsg(1001));
        assertNotEquals("用户名已存在", ErrorCode.getMsg(1001));
    }
}