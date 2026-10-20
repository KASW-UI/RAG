package com.rag.users.user.rpc.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

/**
 * PostgreSQL {@code jsonb} 列 ↔ {@code Map<String, String>} 的 TypeHandler。
 *
 * <p><b>为什么不能用 JacksonTypeHandler</b>：MP 自带的 {@code JacksonTypeHandler}
 * 写参时走的是 {@code ps.setString(...)}，JDBC 会把值绑成 {@code varchar}。
 * 而 {@code extra_info} 列是 {@code jsonb}，Postgres 直接拒绝：
 * <pre>
 *   ERROR: column "extra_info" is of type jsonb but expression is of type character varying
 *   建议：You will need to rewrite or cast the expression.
 * </pre>
 * 这个错误对 {@code BaseMapper.insert} / {@code updateById} 是<b>必现</b>的
 * ——即 register 带 extra_info、头像双写都会 100% 失败。
 *
 * <p>正确做法是用 {@code ps.setObject(i, json, Types.OTHER)}，让驱动把参数
 * 按 PG 的 unknown/jsonb 类型发过去。
 *
 * <p><b>读</b>：{@code ResultSet.getString()} 对 jsonb 列返回其 JSON 文本，Jackson 解析即可。
 *
 * <p>配套要求：实体上的 {@code @TableName} 必须带 {@code autoResultMap = true}，
 * 否则 MyBatis-Plus 生成的 ResultMap 不会采用字段级 TypeHandler（读回来仍是 null）。
 *
 * <p>注：admin-rpc 有自己的 UserEntity，同样的 jsonb 列，若走 MP 生成 SQL 会有相同问题，
 * 需要各自引入本 handler（或把它下沉到 service/common）。
 */
@MappedTypes(Map.class)
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TypeReference<Map<String, String>> type;

    public JsonbTypeHandler(Class<?> ignored) {
        this(new TypeReference<Map<String, String>>() {});
    }

    public JsonbTypeHandler(TypeReference<Map<String, String>> type) {
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, String> parameter,
                                    JdbcType jdbcType) throws SQLException {
        // 关键：Types.OTHER → PG 按 jsonb/unknown 解释，不会当成 varchar
        ps.setObject(i, toJson(parameter), Types.OTHER);
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