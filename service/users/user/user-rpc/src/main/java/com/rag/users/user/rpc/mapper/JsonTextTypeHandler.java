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
import java.util.Map;

/**
 * {@code users_user.extra_info}（<b>text</b> 列）↔ {@code Map<String, String>} 的 TypeHandler。
 *
 * <p>⚠️ <b>2026-10-23 改名（DRIFT-USERRPC-V-003）</b>：本类原名 {@code JsonbTypeHandler}，
 * 其存在的**全部理由**是「jsonb 列拒收 varchar 绑参」：
 * <pre>
 *   ps.setString(...) → varchar
 *   ERROR: column "extra_info" is of type jsonb but expression is of type character varying
 * </pre>
 * 既然 {@code extra_info} 已改回 {@code text}，**该理由连同它描述的报错都不复存在了**。
 * ⇒ 类名若仍叫 {@code Jsonb…}，就是在对下一个读代码的人撒谎
 * （与 {@code TokenBlacklistedException} 声称 Go 有 {@code PERMISSION_DENIED} 同型病灶），
 * 故一并改名。
 *
 * <p><b>Go 出处</b>：{@code service/user/user/rpc/internal/model/data_model.go:13}
 * {@code ExtraInfo map[string]string `gorm:"column:extra_info;serializer:json"`}。
 * 列类型是 {@code text}，由 {@code serializer:json ⇒ DataType=String}
 * （gorm@v1.31.1 {@code schema/field.go:195-200}）+ {@code Size 保持 0}
 * （{@code :330-340}，{@code reflect.Map} 不在默认分支里）
* ⇒ {@code DataTypeOf} 走 {@code field.Size > 0} 为假的分支返回 {@code text}
 * （gorm.io/driver/postgres@v1.6.0 {@code postgres.go:245-250}）。
 * 已由真源 {@code model.InitDB} 的活体建表语句实证（PG {@code log_statement}）。
 *
 * <p><b>L4 idiomatization</b>：Go 用 GORM 的 {@code serializer:json} 自动完成
 * 「Map ↔ 数据库文本」双向转换；Java 侧没有等价的声明式机制，
 * 故用 MyBatis 的 {@link BaseTypeHandler} 显式承担同一职责。
 *
 * <p><b>语义等价论证</b>（与 Go 逐条对齐）：
 * <ul>
 *   <li><b>写</b>：Go 的 serializer 走 {@code json.Marshal(map)}（键<b>字典序</b>）；
 *       本实现走 {@code writeValueAsString(map)}。⚠️ 两者键序规则在
 *       <b>长度不同的键</b>上不一致（Go 纯字典序 vs Jackson 保持 map 迭代序）。
 *       这是既有差异，<b>本次不改</b>：读出来都是同一个 map，wire 上不可观察
 *       （protobuf map 字段无序），改它属于另一件事。</li>
 *   <li><b>读</b>：Go 的 serializer 在 Unmarshal 失败时返回 error；
 *       本实现抛 {@link SQLException}，由 domain 层映射成 INTERNAL。行为同构。</li>
 *   <li><b>null / 空串</b>：两侧都把「空内容」读成 {@code null}，不是空 map。</li>
 * </ul>
 *
 * <p><b>为什么写参用 {@code ps.setString} 而不是 {@code ps.setObject(…, Types.OTHER)}</b>：
 * 列是 {@code text}，{@code setString} 直接就是对的绑定；{@code Types.OTHER} 会让驱动
 * 发送 {@code Oid.UNSPECIFIED} 让 PG 按上下文猜类型 —— 对 jsonb 是必需，对 text 是多余的。
 *
 * <p>配套要求：实体上的 {@code @TableName} 必须带 {@code autoResultMap = true}，
 * 否则 MyBatis-Plus 生成的 ResultMap 不会采用字段级 TypeHandler（读回来仍是 null）。
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
        // 列是 text ⇒ 直接 setString 即可（2026-10-23 前这里是 setObject(..., Types.OTHER)，
        // 那是为了迁就 jsonb 列；列类型改回 text 后它是多余的，且会掩盖类型错误）
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