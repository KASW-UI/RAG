package com.rag.users.admin.rpc.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * PostgreSQL <b>{@code text}</b> 列 ↔ {@code Map<String, String>} 的 TypeHandler。
 *
 * <p><b>为什么 {@link JsonbTypeHandler} 不能用于 {@code admin_admin.extra_info}</b>
 * —— 那个 handler 用 {@code ps.setObject(i, json, Types.OTHER)} 让 PG 按 jsonb 解释参数，
 * 这只对 **jsonb 列**成立。{@code admin_admin.extra_info} 在 Go 侧是 **text** 列
 * （{@code data_model.go:11} 的 {@code serializer:json} ⇒ GORM 按 String 建列，
 * 见本文件末尾「Go 出处」），而 jsonb 列比 text 列**多一套校验**，
 * 会拒收 {@code encoding/json} 能合法产出的内容。
 *
 * <p><b>这不是洁癖，有活体对拍证据（DRIFT-ARPC-004①，2026-10-23）</b>：
 * <pre>
 *   同一份请求体（extra_info 含 U+0000）：
 *     Go  → HTTP 200 {code:200}，行已落库
 *     RAG → HTTP 200 {code:5001}，无行，PG 报 22P02
 *            ERROR: unsupported Unicode escape sequence
 * </pre>
 * 根因：{@code encoding/json} 把 U+0000 转义成 6 个 ASCII 字符 {@code \u0000}
 * （**不是**裸 NUL 字节），而 jsonb 的 unicode escape 校验拒绝 {@code \u0000}；
 * text 列照单全收，与 Go 完全一致。
 *
 * <p><b>⚠️ 不要把本 handler 与 {@code JsonbTypeHandler} 合并</b>：
 * admin-rpc 里<b>另一张</b>表的同名列是 jsonb ——
 * {@code users_user.extra_info}（DDL 归属 users/user 域，
 * {@code users-common/.../01_create_user.sql:41} 仍是 {@code JSONB}），
 * 实体是 {@code domain/UserEntity}，走 {@link JsonbTypeHandler}。
 * 两个 handler 服务两张表，列类型不同 ⇒ 写参方式必须不同。
 *
 * <p><b>写</b>：{@code ps.setString}（text 列的原生绑定）。
 * <b>读</b>：{@code ResultSet.getString} 取回 JSON 文本，Jackson 解析。
 *
 * <p>配套要求：实体上的 {@code @TableName} 必须带 {@code autoResultMap = true}，
 * 否则 MyBatis-Plus 生成的 ResultMap 不采用字段级 TypeHandler（读回来恒 null）。
 *
 * <p><b>Go 出处（声明点 + 消费点）</b>：
 * <ul>
 *   <li>声明点 {@code service/user/admin/rpc/internal/model/data_model.go:11}：
 *       {@code ExtraInfo map[string]string `gorm:"column:extra_info;serializer:json"`}</li>
 *   <li>消费点 {@code gorm.io/gorm@v1.31.1/schema/field.go}：字段带 {@code SERIALIZER} tag 时
 *       {@code field.DataType} 被置为 {@code String} ⇒ 列按<b>字符串</b>建；
 *       {@code serializer/json} 的 {@code Value()} 返回 {@code json.Marshal} 的 <b>string</b></li>
 * </ul>
 */
@MappedTypes(Map.class)
public class JsonTextTypeHandler extends BaseTypeHandler<Map<String, String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TypeReference<Map<String, String>> type;

    public JsonTextTypeHandler(Class<?> ignored) {
        this(new TypeReference<Map<String, String>>() {});
    }

    public JsonTextTypeHandler(TypeReference<Map<String, String>> type) {
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, String> parameter,
                                    JdbcType jdbcType) throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static String toJson(Map<String, String> map) throws SQLException {
        if (map == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new SQLException("Failed to serialize Map to JSON", e);
        }
    }

    private Map<String, String> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new SQLException("Failed to deserialize JSON to Map: " + json, e);
        }
    }
}