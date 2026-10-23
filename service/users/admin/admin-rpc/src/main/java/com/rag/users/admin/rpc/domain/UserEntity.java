package com.rag.users.admin.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * user user 表的 MyBatis-Plus 实体（admin-rpc 端独立持有）。
 *
 * <p>对应 Go 端 internal/model/admin.go 的 User struct（GORM 默认表名 user_user，
 * HUMAN_ONLY.MD 2026-10-22 改名为 users_user）。
 *
 * <p>admin-rpc 持 User 表虽然语义上奇怪（用户表通常归 user-rpc 管），但 Go 端 admin-rpc
 * 通过 internal/model/admin.go 同时持有 User struct，本质是把 admin-rpc 当作
 * "管理员对 user 表的 CRUD 网关"：BanUser / DeleteUser / ResetUserPassword / UpdateUser
 * 都是 admin-rpc 直接读写 users_user 表。
 *
 * <p>L1 表结构（权威来源＝共用 DDL {@code users-common/src/main/resources/db/01_create_user.sql}；
 * 与 user-rpc 端 UserEntity 共用同一张物理表）：
 * <pre>
 *   id          BIGSERIAL PRIMARY KEY
 *   uid         BIGINT NOT NULL UNIQUE
 *   username    TEXT UNIQUE
 *   password    TEXT
 *   email       TEXT UNIQUE                      -- 非 null（与 Admin.Email nullable 不同）
 *   status      BIGINT NOT NULL DEFAULT 0        -- 0=正常 1=封禁（BanUser / UnBanUser 改此列）
 *   score       INTEGER
 *   extra_info  JSONB
 *   create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 *   update_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>⚠️ 本块原注释写 {@code VARCHAR(255)} 与 {@code TIMESTAMP}，两者<b>都已被修掉</b>：
 * 2026-10-22（E-3 根因 R3）按 Go 的 GORM AutoMigrate 改成 {@code TEXT}（无长度上限），
 * 2026-10-23（DRIFT-USERRPC-V-004）按 Go 的 {@code postgres.go:251-256} 改成 {@code TIMESTAMPTZ}。
 * 「L1 与 DDL 严格对齐」的说法已恢复为真 —— 此前它恰好是不成立的那一版。
 *
 * <p>L4 实现机制差异：与 user-rpc 端 UserEntity 字段类型逐条一致（同一张物理表两侧共同读写），
 * 但<b>仍有一处未对齐</b>：本类 {@code status = 0L} 字段初始化值未删，
 * user-rpc 端已于 2026-10-23 删除（DRIFT-USERRPC-V-001）。见 {@link #status} 的说明。
 * </p>
 *
 * <p>⚠️ <b>2026-10-23 更正</b>：上面那句「未对齐」已被双侧活体对拍<b>证伪</b> ——
 * Go 侧把 {@code status IS NULL} 读成 <b>0</b>（不是「读不出来」），
 * 所以<b>本类的 {@code = 0L} 才与 Go 一致</b>，而 user-rpc 端删掉它 + fail-closed
 * 才是偏离。详见 {@link #status} 的完整论证。
 */
@Data
@TableName(value = "users_user", autoResultMap = true)
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("uid")
    private Long uid;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("email")
    private String email;

    /**
     * 🔴 <b>这里的 {@code = 0L} 不是缺陷</b>（2026-10-23 双侧活体对拍证伪了此前的判断）。
     *
     * <p><b>此前台账的说法</b>：{@code users/user/rpc.md} 的 DRIFT-USERRPC-V-001 写
     * 「Go 的语义是『NULL 读不出来』⇒ Java 侧必须让 null 真传上来」，并把 admin-rpc
     * 标成「同款缺陷，未修」。
     *
     * <p><b>证伪过程（两侧同时跑的探针）</b>：
     * <ul>
     *   <li><b>Go 侧机制</b>（{@code gorm.io/gorm@v1.31.1/scan.go:19} 的 {@code prepareValues}）：
     *       {@code values[idx] = reflect.New(reflect.PointerTo(field.FieldType)).Interface()}
     *       ⇒ 对 {@code int64} 字段造出来的是 <b>{@code **int64}</b>（<b>双</b>指针），
     *       不是 {@code *int64}；{@code database/sql} 的 {@code convertAssign} 对
     *       「dest 是指针、src 为 nil」走 {@code case reflect.Pointer: if src == nil
     *       { dv.SetZero(); return nil }} ⇒ <b>不报错</b>；GORM 的 int setter 随后
     *       把字段留在<b>零值 0</b>。⇒ <b>Go 把 NULL 读成 0，且不报错。</b></li>
     *   <li><b>Go 侧活体</b>：2026-10-23 在 {@code tools/difftest} 的两套真栈上，
     *       往 {@code go_db.users} 插一行 {@code status IS NULL}，打 Go admin-api
     *       {@code GET /admincenter/v1/admin/getuser?uid=…}
     *       ⇒ {@code {"code":200,…,"user":{…,"status":0}}}；同一时刻打 RAG admin-api
     *       ⇒ 同样是 {@code "status":0}。<b>两侧一致。</b></li>
     * </ul>
     *
     * <p><b>⇒ 本字段的 {@code = 0L} 与 Go 等价，<b>不要</b>删</b>：
     * 删掉它会让本组件读出 {@code null}（而 Go 读出 0），
     * 在「users_user.status 可空」的部署形态下就是<b>新的漂移</b>。
     * ⚠️ 反向提醒：{@code users/user} 域在 2026-10-23 把同名字段的 {@code = 0L} 删掉、
     * 并加了「status 为 null ⇒ fail-closed 抛 INTERNAL」的守卫
     * （{@code UserDomainService#login}）—— 那一条<b>与 Go 的实际行为相反</b>，
     * 已作为跨组件问题上报（见 {@code docs/DRIFT/users/admin/rpc.md} §11）。
     *
     * <p><b>守门</b>：{@code AdminRpcGoWireParityTest#nullStatusColumnReadsAsZeroLikeGo}
     * （真 PG）把本行为钉死。
     */
    @TableField("status")
    private Long status = 0L;

    @TableField("score")
    private Integer score;

    @TableField(value = "extra_info", typeHandler = com.rag.users.admin.rpc.mapper.JsonbTypeHandler.class)
    private Map<String, String> extraInfo;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime createTime;

    /**
     * 🔴 类型为 {@link OffsetDateTime} 而非 {@link LocalDateTime}（2026-10-23，DRIFT-USERRPC-V-004）。
     *
     * <p><b>Go 出处</b>：声明点 {@code service/user/admin/rpc/internal/model/data_model.go:12-13、27-28、44-45}
     * 共 3 组 {@code time.Time} + {@code gorm:"column:…;auto…Time"}，<b>无 type tag</b>；
     * 消费点 {@code gorm.io/driver/postgres@v1.6.0/postgres.go:251-256} 的
     * {@code case schema.Time} 在 {@code field.Precision == 0} 时返回 {@code "timestamptz"}。
     *
     * <p>与 user-rpc 端同名实体<b>共用同一张物理表</b>（两侧都建 {@code users_user}，
     * 见 §6.3 问题 1）⇒ 本字段类型必须与 user-rpc 端一致，否则同一行数据两侧读出的
     * 时间字符串不同，属跨组件漂移。
     *
     * <p>⚠️ <b>与 DDL 必须同一提交</b>：列改 timestamptz 而实体仍是 LocalDateTime，
     * 会让 JVM 默认时区参与换算，把「列类型不一致」换成「值算错」。
     * 见 {@code users-common/src/main/resources/db/01_create_user.sql:54-83}。
     */
    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime updateTime;
}
