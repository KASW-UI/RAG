package com.rag.favorite.favorite.rpc.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * jsonb 列 ↔ {@link String} 的 TypeHandler —— 对应 Go 侧 {@code favorite_item} / outbox 的
 * {@code jsonb} 列（favorite_fact.go:64 {@code Payload string `gorm:"type:jsonb;not null"`}）。
 *
 * <p><b>为什么用 {@code String} 而不是 POJO</b>：见
 * {@code FavoriteFactOutboxEntity#getPayload()} 的类注释 —— 冻结信封的
 * {@code input_hash} 是对<b>原始字节</b>做 JCS+SHA256 后与 DC 回执比对的，
 * 只有原样存取才能让「投递出去的」「权威读重新哈希的」「DC 算 hash 的」三者同源。
 *
 * <p><b>为什么不用 MyBatis-Plus 自带的 JacksonTypeHandler</b>：它按目标 Java 类型推断
 * PG 列类型，会把 {@code json} 与 {@code jsonb} 混为一谈。显式声明 {@code jsonb}
 * 才不会在灰度期读写出错类型。
 *
 * <p><b>写入前不校验 JSON 合法性</b>：Go 侧也不校验（payload 由 {@code json.Marshal} 产生，
 * 一定合法）。若上层传了非法 JSON，让 PG 在插入时报错，而不是在这里静默吞掉。
 */
@MappedTypes(String.class)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    /** PG 的 jsonb 类型名。 */
    public static final String PG_JSONB = "jsonb";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        // PGobject 让 pgjdbc 走 PGobject 路径，把类型显式声明为 jsonb，
        // 否则按 VARCHAR 绑定，jsonb 列会因类型不匹配报错。
        PGobject jsonb = new PGobject();
        jsonb.setType(PG_JSONB);
        jsonb.setValue(parameter);
        ps.setObject(i, jsonb);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // PG 对 jsonb 返回 PGobject；getString 直接拿到文本形式，交给上层做规范化。
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}