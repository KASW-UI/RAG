package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleCoercion;
import com.rag.users.common.http.GoRequiredFieldException;
import com.rag.users.common.http.GoStyleStringInt64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * favorite-api 的<b>入参契约</b>测试 —— 把 Go 侧 {@code httpx.Parse} 的判定矩阵钉死。
 *
 * <p><b>矩阵的来源</b>：go-zero v1.10.2 真源探针（2026-10-23，对 {@code service/favorite/api/internal/types/types.go}
 * 的逐字同构 struct 直接调 {@code httpx.Parse}），不是从源码「推断」出来的。
 *
 * <p><b>为什么这些是 L1 而不是实现细节</b>：客户端按「能不能发这个请求」来写代码，
 * 而「{@code {"folderId":123}} 收不收」「{@code {"name":""}} 算不算空」
 * 这些判定直接决定客户端的请求构造方式。改掉任何一条都是契约变更。
 *
 * <p><b>本测试用 Jackson 3 + {@link GoStyleCoercion}</b>：与 MVC 运行时同一套规则
 * （生产环境的 mapper 由 {@code WebMvcConfig#goStyleCoercionCustomizer} 施加同样的 coercion），
 * 否则「单测里 Jackson 默认把 {@code {"name":123}} 强转成 "123"}」会与线上行为不一致 ——
 * 那正是 AGENTS.md §3.3 说的「测不到真实机制」。
 */
class FavoriteApiInputContractTest {

    /** 与生产环境同一套 coercion 规则（Jackson 3）。 */
    private static final JsonMapper MAPPER = buildMapper();

    private static JsonMapper buildMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        GoStyleCoercion.applyTo(builder);
        return builder.build();
    }

    // ==================================================================
    // POST /folder/create —— type CreateFavoriteFolderReq { Name string `json:"name"` }
    // ==================================================================

    @Test
    @DisplayName("POST /folder/create：{\"name\":\"x\"} → 接受")
    void createFolderAcceptsNormalName() {
        CreateFavoriteFolderReq req = MAPPER.readValue("{\"name\":\"x\"}", CreateFavoriteFolderReq.class);
        req.assertGoRequiredFields();
        assertEquals("x", req.getName());
    }

    @Test
    @DisplayName("POST /folder/create：{\"name\":\"\"} → 接受（空串是合法值，Go json 通道不判空串）")
    void createFolderAcceptsEmptyName() {
        CreateFavoriteFolderReq req = MAPPER.readValue("{\"name\":\"\"}", CreateFavoriteFolderReq.class);
        req.assertGoRequiredFields();
        assertEquals("", req.getName());
    }

    @Test
    @DisplayName("POST /folder/create：{} → field \"name\" is not set（⇒ 5003）")
    void createFolderRejectsMissingName() {
        CreateFavoriteFolderReq req = MAPPER.readValue("{}", CreateFavoriteFolderReq.class);
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("name", e.getField());
    }

    @Test
    @DisplayName("POST /folder/create：{\"name\":null} → 必填失败（⇒ 5003）")
    void createFolderRejectsNullName() {
        CreateFavoriteFolderReq req = MAPPER.readValue("{\"name\":null}", CreateFavoriteFolderReq.class);
        assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
    }

    @Test
    @DisplayName("POST /folder/create：{\"name\":123} → 拒绝（Go：type mismatch for field \"name\"）")
    void createFolderRejectsNumberForStringField() {
        // Go: type mismatch for field "name", expect "string", actual "number"
        assertThrows(Exception.class,
                () -> MAPPER.readValue("{\"name\":123}", CreateFavoriteFolderReq.class));
    }

    @Test
    @DisplayName("POST /folder/create：{\"name\":\"x\",\"extra\":1} → 接受（Go 忽略未声明字段）")
    void createFolderIgnoresUnknownFields() {
        CreateFavoriteFolderReq req = MAPPER.readValue("{\"name\":\"x\",\"extra\":1}", CreateFavoriteFolderReq.class);
        req.assertGoRequiredFields();
        assertEquals("x", req.getName());
    }

    // ==================================================================
    // ,string 的入参语义（JSON 字符串与数字都收）
    // ==================================================================

    @Test
    @DisplayName("DELETE /item/delete：{\"favoriteId\":\"123\"} → 123（字符串形态）")
    void deleteFavoriteAcceptsQuotedInt64() {
        DeleteFavoriteReq req = MAPPER.readValue("{\"favoriteId\":\"123\"}", DeleteFavoriteReq.class);
        req.assertGoRequiredFields();
        assertEquals(123L, req.getFavoriteId());
    }

    @Test
    @DisplayName("DELETE /item/delete：{\"favoriteId\":123} → 123（⚠️ 数字也收！Go 的 ,string 入参方向是宽松的）")
    void deleteFavoriteAcceptsNumericInt64() {
        DeleteFavoriteReq req = MAPPER.readValue("{\"favoriteId\":123}", DeleteFavoriteReq.class);
        req.assertGoRequiredFields();
        assertEquals(123L, req.getFavoriteId());
    }

    @Test
    @DisplayName("DELETE /item/delete：{\"favoriteId\":\"abc\"} → 拒绝（Go：strconv.ParseInt invalid syntax）")
    void deleteFavoriteRejectsNonNumericInt64() {
        // GoStyleStringInt64 抛 IllegalArgumentException，但 Jackson 会把 setter 里抛的异常
        // 包成 DatabindException —— 这条链正是 MVC 上「HttpMessageNotReadableException → 200 + 5003」的来源。
        tools.jackson.databind.DatabindException e = assertThrows(
                tools.jackson.databind.DatabindException.class,
                () -> MAPPER.readValue("{\"favoriteId\":\"abc\"}", DeleteFavoriteReq.class));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    @DisplayName("DELETE /item/delete：{\"favoriteId\":1.5} → 拒绝（Go：\"1.0\"/\"1.5\"/\"1e2\" 全部 ParseInt 失败）")
    void deleteFavoriteRejectsFractionalInt64() {
        tools.jackson.databind.DatabindException e = assertThrows(
                tools.jackson.databind.DatabindException.class,
                () -> MAPPER.readValue("{\"favoriteId\":1.5}", DeleteFavoriteReq.class));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    @DisplayName("DELETE /item/delete：{} → field \"favoriteId\" is not set（⇒ 5003）")
    void deleteFavoriteRejectsMissingId() {
        DeleteFavoriteReq req = MAPPER.readValue("{}", DeleteFavoriteReq.class);
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("favoriteId", e.getField());
    }

    @Test
    @DisplayName("DELETE /folder/delete：{\"folderId\":9} → 9（数字形态）")
    void deleteFolderAcceptsNumericInt64() {
        DeleteFavoriteFolderReq req = MAPPER.readValue("{\"folderId\":9}", DeleteFavoriteFolderReq.class);
        req.assertGoRequiredFields();
        assertEquals(9L, req.getFolderId());
    }

    // ==================================================================
    // POST /folder/update —— 两个字段都必填
    // ==================================================================

    @Test
    @DisplayName("POST /folder/update：{\"folderId\":\"1\",\"name\":\"x\"} → 接受")
    void updateFolderAcceptsBothFields() {
        UpdateFavoriteFolderNameReq req =
                MAPPER.readValue("{\"folderId\":\"1\",\"name\":\"x\"}", UpdateFavoriteFolderNameReq.class);
        req.assertGoRequiredFields();
        assertEquals(1L, req.getFolderId());
        assertEquals("x", req.getName());
    }

    @Test
    @DisplayName("POST /folder/update：{\"name\":\"x\"} → field \"folderId\" is not set（⇒ 5003）")
    void updateFolderRejectsMissingFolderId() {
        UpdateFavoriteFolderNameReq req =
                MAPPER.readValue("{\"name\":\"x\"}", UpdateFavoriteFolderNameReq.class);
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("folderId", e.getField());
    }

    @Test
    @DisplayName("POST /folder/update：{\"folderId\":\"1\"} → field \"name\" is not set（⇒ 5003）")
    void updateFolderRejectsMissingName() {
        UpdateFavoriteFolderNameReq req =
                MAPPER.readValue("{\"folderId\":\"1\"}", UpdateFavoriteFolderNameReq.class);
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("name", e.getField());
    }

    // ==================================================================
    // POST /item/create —— folderId/targetId/targetType 必填，title/cover 可选
    // ==================================================================

    @Test
    @DisplayName("POST /item/create：三个必填齐全 → 接受；title/cover 缺席即为空串")
    void createItemAcceptsRequiredFields() {
        CreateFavoriteReq req = MAPPER.readValue(
                "{\"folderId\":\"1\",\"targetId\":\"a\",\"targetType\":\"article\"}", CreateFavoriteReq.class);
        req.assertGoRequiredFields();
        assertEquals(1L, req.getFolderId());
        assertEquals("a", req.getTargetId());
        assertEquals("article", req.getTargetType());
        assertNull(req.getTitle());
        assertNull(req.getCover());
    }

    @Test
    @DisplayName("POST /item/create：缺 targetType → field \"targetType\" is not set（⇒ 5003）")
    void createItemRejectsMissingTargetType() {
        CreateFavoriteReq req = MAPPER.readValue(
                "{\"folderId\":\"1\",\"targetId\":\"a\"}", CreateFavoriteReq.class);
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("targetType", e.getField());
    }

    @Test
    @DisplayName("POST /item/create：缺 targetId → field \"targetId\" is not set（⇒ 5003）")
    void createItemRejectsMissingTargetId() {
        CreateFavoriteReq req = MAPPER.readValue(
                "{\"folderId\":\"1\",\"targetType\":\"article\"}", CreateFavoriteReq.class);
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("targetId", e.getField());
    }

    @Test
    @DisplayName("POST /item/create：title/cover 显式 null → 接受（Go 的 ,optional 允许显式 null）")
    void createItemAcceptsExplicitNullOnOptionalFields() {
        CreateFavoriteReq req = MAPPER.readValue(
                "{\"folderId\":\"1\",\"targetId\":\"a\",\"targetType\":\"article\",\"title\":null,\"cover\":null}",
                CreateFavoriteReq.class);
        req.assertGoRequiredFields();
        assertNull(req.getTitle());
        assertNull(req.getCover());
    }

    // ==================================================================
    // GET /item/list —— 唯一走 form 通道的端点
    // ==================================================================

    @Test
    @DisplayName("GET /item/list：?folderId=123 → 123")
    void listItemsAcceptsNumericFormValue() {
        GetFavoritesByFolderReq req = GetFavoritesByFolderReq.fromForm("123");
        req.assertGoRequiredFields();
        assertEquals(123L, req.getFolderId());
    }

    @Test
    @DisplayName("GET /item/list：?folderId=0 → 0（0 是合法值）")
    void listItemsAcceptsZeroFormValue() {
        GetFavoritesByFolderReq req = GetFavoritesByFolderReq.fromForm("0");
        req.assertGoRequiredFields();
        assertEquals(0L, req.getFolderId());
    }

    @Test
    @DisplayName("GET /item/list：?folderId=（空） → 字段缺席（Go：GetFormValues 把空值整个丢掉）⇒ 5003")
    void listItemsTreatsEmptyFormValueAsAbsent() {
        GetFavoritesByFolderReq req = GetFavoritesByFolderReq.fromForm("");
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("folderId", e.getField());
    }

    @Test
    @DisplayName("GET /item/list：无 query → field \"folderId\" is not set（⇒ 5003）")
    void listItemsRejectsAbsentFormValue() {
        GetFavoritesByFolderReq req = GetFavoritesByFolderReq.fromForm(null);
        GoRequiredFieldException e = assertThrows(GoRequiredFieldException.class, req::assertGoRequiredFields);
        assertEquals("folderId", e.getField());
    }

    @Test
    @DisplayName("GET /item/list：?folderId=abc → 拒绝（Go：strconv.ParseInt invalid syntax）")
    void listItemsRejectsNonNumericFormValue() {
        assertThrows(IllegalArgumentException.class, () -> GetFavoritesByFolderReq.fromForm("abc"));
    }

    @Test
    @DisplayName("GET /item/list：?folderId=1.5 → 拒绝（Go：\"1.5\" ParseInt 失败）")
    void listItemsRejectsFractionalFormValue() {
        assertThrows(IllegalArgumentException.class, () -> GetFavoritesByFolderReq.fromForm("1.5"));
    }

    // ==================================================================
    // GoStyleStringInt64 的边界（与 users-common 的共享实现同源）
    // ==================================================================

    @Test
    @DisplayName("GoStyleStringInt64：null → null（由必填判定接手）")
    void stringInt64PassesThroughNull() {
        assertNull(GoStyleStringInt64.parse(null, "folderId"));
    }

    @Test
    @DisplayName("GoStyleStringInt64：\"+123\" → 123（Go 的 ParseInt 接受前导 +）")
    void stringInt64AcceptsLeadingPlus() {
        assertEquals(123L, GoStyleStringInt64.parse("+123", "folderId"));
    }

    @Test
    @DisplayName("GoStyleStringInt64：\" 12\"（带空格）→ 拒绝（Go 的 ParseInt 不裁空白）")
    void stringInt64RejectsSurroundingSpaces() {
        assertThrows(IllegalArgumentException.class, () -> GoStyleStringInt64.parse(" 12", "folderId"));
    }

    @Test
    @DisplayName("GoStyleStringInt64：越界 BigInteger → 拒绝（Go：strconv.ParseInt range error）")
    void stringInt64RejectsOutOfRangeValue() {
        assertThrows(IllegalArgumentException.class,
                () -> GoStyleStringInt64.parse(new java.math.BigInteger("18446744073709551616"), "folderId"));
    }

    @Test
    @DisplayName("出参 folderId 序列化成【字符串】（Go 的 json:\"folderId,string\"）")
    void createFolderResponseSerializesFolderIdAsString() {
        CreateFavoriteFolderResp resp = new CreateFavoriteFolderResp(123L);
        assertEquals("{\"folderId\":\"123\"}", MAPPER.writeValueAsString(resp));
    }

    @Test
    @DisplayName("出参 favoriteId 序列化成【字符串】（Go 的 json:\"favoriteId,string\"）")
    void createFavoriteResponseSerializesFavoriteIdAsString() {
        assertEquals("{\"favoriteId\":\"456\"}",
                MAPPER.writeValueAsString(new CreateFavoriteResp(456L)));
    }

    @Test
    @DisplayName("出参 createdAt/updatedAt 是【数字】、title/cover 永远出现（Go 无 omitempty）")
    void folderAndItemResponsesKeepGoEncoding() {
        // Go types.go:42-47：folderId 是 string 形态，createdAt/updatedAt 是数字
        assertEquals("{\"folderId\":\"7\",\"name\":\"n\",\"createdAt\":1700000000000,\"updatedAt\":1700000000001}",
                MAPPER.writeValueAsString(new FavoriteFolder(7L, "n", 1700000000000L, 1700000000001L)));

        // Go types.go:49-57：favoriteId/folderId 是 string 形态，createdAt 是数字，
        // title/cover 即使为空串也【必须出现】（Go 的 ,optional 不是 encoding/json 的 omitempty）
        assertEquals("{\"favoriteId\":\"1\",\"folderId\":\"2\",\"targetId\":\"t\",\"targetType\":\"article\","
                        + "\"title\":\"\",\"cover\":\"\",\"createdAt\":1700000000000}",
                MAPPER.writeValueAsString(new FavoriteItem(1L, 2L, "t", "article", "", "", 1700000000000L)));
    }

    @Test
    @DisplayName("出参空集合是【[]】不是 null（Go 侧无条件 make）")
    void listResponsesSerializeEmptyArrayNotNull() {
        assertEquals("{\"folders\":[]}",
                MAPPER.writeValueAsString(new GetFavoriteFoldersResp(java.util.List.of())));
        assertEquals("{\"favorites\":[]}",
                MAPPER.writeValueAsString(new GetFavoritesByFolderResp(java.util.List.of())));
    }
}
