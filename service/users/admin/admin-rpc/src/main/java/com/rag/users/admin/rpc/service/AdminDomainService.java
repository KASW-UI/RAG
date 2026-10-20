package com.rag.users.admin.rpc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rag.common.snowflake.Snowflake;
import com.rag.users.admin.rpc.config.AdminRpcProperties;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.AdminInviteEntity;
import com.rag.users.admin.rpc.domain.UserEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions;
import com.rag.users.admin.rpc.exception.AdminExceptions.InviteUnavailableException;
import com.rag.users.admin.rpc.exception.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.exception.AdminExceptions.UsernameTakenException;
import com.rag.users.admin.rpc.mapper.AdminInviteMapper;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.mapper.UserMapper;
import com.rag.users.admin.rpc.metrics.AdminRpcMetrics;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * admin-rpc 业务逻辑合并服务 —— 13 个用例合 1 个 @Service。
 *
 * 对应 Go 端 internal/logic/admin/ 的 13 个 Logic 文件：
 * <pre>
 *   loginlogic.go + logoutlogic.go + getselflogic.go + updateselflogic.go +
 *   createadminlogic.go + createinvitecodelogic.go + banuserlogic.go + unbanuserlogic.go +
 *   getuserlogic.go + getuserlistlogic.go + updateuserlogic.go + deleteuserlogic.go +
 *   resetuserpasswordlogic.go
 * </pre>
 *
 * <p>L4 实现机制差异（与 Go 端 Logic + DataModel 两层结构对比）：
 * <ul>
 *   <li>Go 用 13 个 Logic 类 + 1 个 DataModel + 1 个 ServiceContext 装配类（共 15 文件）</li>
 *   <li>Java 用 1 个 AdminDomainService（含 13 业务方法）+ 1 个 AdminCreateAtomicService
 *       （CreateAdmin 的 4 步原子事务，<b>必须独立成 Bean</b>，否则 @Transactional
 *       因 self-invocation 静默失效）+ 3 个 Mapper（BaseMapper 标准 CRUD）+ 直接注入依赖（无 ServiceContext）</li>
 * </ul>
 *
 * <p>业务错误统一抛 {@link StatusRuntimeException}，由 {@code AdminServiceGrpcImpl.invoke}
 * 统一捕获透传；任何非 {@code StatusRuntimeException} 兜底成 {@code Status.INTERNAL}。
 */
@Service
public class AdminDomainService {

    private static final Logger log = LoggerFactory.getLogger(AdminDomainService.class);

    /** 邀请码有效期 10 分钟，与 Go 端 inviteCodeTTL 常量一致 */
    private static final Duration INVITE_CODE_TTL = Duration.ofMinutes(10);

    /** 最大重试次数，与 Go 端 inviteCodeMaxRetry 常量一致 */
    private static final int INVITE_CODE_MAX_RETRY = 3;

    /** 邀请码前缀，与 Go 端 "ADM-" 字面量一致 */
    private static final String INVITE_CODE_PREFIX = "ADM-";

    /** 单次随机字节数（8 字节 → 16 字符 hex） */
    private static final int INVITE_CODE_RANDOM_BYTES = 8;

    /** 静态 SecureRandom 实例（线程安全），替代 Go 端 crypto/rand.Reader */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Redis blacklist key 字面量前缀（D-006：必须与 user-rpc 的 "user:jwt_blacklist:" 区分） */
    private static final String BLACKLIST_KEY_PREFIX = "admin:jwt_blacklist:";

    /** 默认 admin 用户名（与 Go 端 hardcoded "admin" 一致） */
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    /** 默认 admin 密码 fallback（Go 端 hardcoded "admin"，这里用 null 判断 fallback） */
    public static final String DEFAULT_ADMIN_PASSWORD_FALLBACK = "admin";

    private final AdminMapper adminMapper;
    private final AdminInviteMapper adminInviteMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AdminRpcProperties properties;
    private final AdminRpcMetrics metrics;
    /** CreateAdmin 的 4 步原子事务，独立 Bean 以保证 @Transactional 代理生效（见该类注释）。 */
    private final AdminCreateAtomicService createAtomicService;

    public AdminDomainService(AdminMapper adminMapper,
                              AdminInviteMapper adminInviteMapper,
                              UserMapper userMapper,
                              StringRedisTemplate stringRedisTemplate,
                              AdminRpcProperties properties,
                              AdminRpcMetrics metrics,
                              AdminCreateAtomicService createAtomicService) {
        this.adminMapper = adminMapper;
        this.adminInviteMapper = adminInviteMapper;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.createAtomicService = createAtomicService;
    }

    // ============== 1. Login ==============

    /**
     * 对应 Go 端 internal/logic/admin/loginlogic.go 的 LoginLogic.Login。
     *
     * <p>L2 业务语义（4 状态机）：
     * <ol>
     *   <li>FindOneAdminByUsername → NotFoundException / Exception / 命中</li>
     *   <li>PasswordUtil.check(admin.Password, in.Password) → wrong_answer / 命中</li>
     *   <li>metrics loginSuccess + LoginResp{Uid=admin.Uid}</li>
     * </ol>
     */
    public Long login(String username, String password) {
        AdminEntity admin;
        try {
            admin = findOneAdminByUsername(username);
        } catch (NotFoundException e) {
            metrics.getLoginUserNotFound().increment();
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            metrics.getLoginDbError().increment();
            log.error("admin login DB error, username={}", username, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        if (!PasswordUtil.check(password, admin.getPassword())) {
            metrics.getLoginWrongAnswer().increment();
            throw Status.UNAUTHENTICATED.withDescription("密码错误").asRuntimeException();
        }

        metrics.getLoginSuccess().increment();
        log.info("admin login success, username={}", username);
        return admin.getUid();
    }

    // ============== 2. Logout ==============

    /**
     * 对应 Go 端 internal/logic/admin/logoutlogic.go 的 LogoutLogic.Logout。
     *
     * <p>L2 业务语义（3 状态机）：
     * <ol>
     *   <li>校验 token 非空 → INVALID_ARGUMENT "Token不能为空"</li>
     *   <li>Redis Setex("admin:jwt_blacklist:%s", "banned", AdminAuth.AccessExpire)</li>
     *   <li>成功 → LogoutResp（Success 字段保持 proto3 默认 false）</li>
     * </ol>
     *
     * <p>D-007：TTL 必须等于 JWT 过期秒数，不能写死 24h。
     */
    public boolean logout(String token) {
        if (token == null || token.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("Token不能为空").asRuntimeException();
        }

        String key = BLACKLIST_KEY_PREFIX + token;
        long ttlSeconds = properties.getAdminAuth().getAccessExpire();
        try {
            stringRedisTemplate.opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("admin logout Redis setex failed", e);
            throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException();
        }
        return true;
    }

    // ============== 3. GetSelf ==============

    /**
     * 对应 Go 端 internal/logic/admin/getselflogic.go 的 GetSelfLogic.GetSelf。
     *
     * <p>L2 业务语义（2 状态机）：
     * <ol>
     *   <li>findOneAdminByUid → NotFoundException / Exception</li>
     *   <li>构造 AdminInfo{uid, username, email=stringValue(admin.Email), extra_info}</li>
     * </ol>
     */
    public AdminEntity getSelf(long uid) {
        AdminEntity admin;
        try {
            admin = findOneAdminByUid(uid);
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            log.error("admin getSelf DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }
        return admin;
    }

    // ============== 4. UpdateSelf ==============

    /**
     * 对应 Go 端 internal/logic/admin/updateselflogic.go 的 UpdateSelfLogic.UpdateSelf。
     *
     * <p>L2 业务语义（5 状态机）：
     * <ol>
     *   <li>findOneAdminByUid → NotFound / Internal</li>
     *   <li>if username 非空 → 查 username 冲突（exist.Uid != self.Uid → AlreadyExists）</li>
     *   <li>构造 toUpdate（跳零值；email nullable 用 optionalString）</li>
     *   <li>updateAdminByUid（LambdaUpdateWrapper 动态 set）</li>
     *   <li>重读 admin</li>
     * </ol>
     */
    public AdminEntity updateSelf(long uid,
                                  String username,
                                  String password,
                                  String email,
                                  Map<String, String> extraInfo) {
        AdminEntity self;
        try {
            self = findOneAdminByUid(uid);
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            log.error("admin updateSelf findOneAdminByUid DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        // username 冲突检查（仅在客户端显式传 username 时）
        if (username != null && !username.isEmpty()) {
            try {
                AdminEntity existing = findOneAdminByUsername(username);
                if (existing != null && !existing.getUid().equals(uid)) {
                    throw Status.ALREADY_EXISTS.withDescription("该用户名已被使用").asRuntimeException();
                }
            } catch (NotFoundException ignored) {
                // 未被占用，名字可用
            } catch (Exception e) {
                log.error("admin updateSelf findOneAdminByUsername DB error", e);
                throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
            }
        }

        // 动态 set：username / password / email / extra_info 任一非空才 set
        LambdaUpdateWrapper<AdminEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AdminEntity::getUid, uid);
        if (username != null && !username.isEmpty()) {
            wrapper.set(AdminEntity::getUsername, username);
        }
        if (password != null && !password.isEmpty()) {
            try {
                wrapper.set(AdminEntity::getPassword, PasswordUtil.encrypt(password));
            } catch (Exception e) {
                log.error("admin updateSelf PasswordUtil.encrypt failed", e);
                throw Status.INTERNAL.withDescription("密码加密失败").asRuntimeException();
            }
        }
        if (email != null && !email.trim().isEmpty()) {
            // UpdateSelf 是部分更新：proto3 未设置 email 时线上的默认值是空串 "",
            // 直接 wrapper.set(..., null) 会把列写成 SQL NULL —— 管理员只改密码就会
            // 静默丢掉邮箱。与参考侧 UpdateSelfLogic 的 `if (!req.getEmail().isEmpty())`
            // 以及本类 updateUser（第 700 行 `email != null && !email.isEmpty()`）保持一致：
            // 空串 / 纯空白一律视为"本次不修改该字段"。
            wrapper.set(AdminEntity::getEmail, email.trim());
        }
        if (extraInfo != null && !extraInfo.isEmpty()) {
            // 不能用 wrapper.set(AdminEntity::getExtraInfo, copy)：
            //   LambdaUpdateWrapper 的参数以 Object 形式塞进 ew.paramNameValuePairs，
            //   不走字段上的 @TableField(typeHandler=...)，MyBatis 只能按 javaType=Object
            //   猜 handler → 猜成 PG 的 hstore → "hstore 扩展尚未安装"。
            // 改为直接写 SQL 片段 + 显式 CAST，参数按 String 绑定再转 jsonb。
            wrapper.setSql("extra_info = CAST({0} AS jsonb)", toJson(extraInfo));
        }

        try {
            adminMapper.update(null, wrapper);
        } catch (Exception e) {
            log.error("admin updateSelf update DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }

        // 重读
        AdminEntity newAdmin;
        try {
            newAdmin = findOneAdminByUid(uid);
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            log.error("admin updateSelf re-findOneAdminByUid DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }
        return newAdmin;
    }

    // ============== 5. CreateAdmin ==============

    /**
     * 对应 Go 端 internal/logic/admin/createadminlogic.go 的 CreateAdminLogic.CreateAdmin。
     *
     * <p>L2 业务语义（5 状态机）：
     * <ol>
     *   <li>校验 inviteCode 非空 → PERMISSION_DENIED "管理员邀请码错误"</li>
     *   <li>trim username + inviteCode</li>
     *   <li>bcrypt password（事务外）+ Snowflake uid（事务外）</li>
     *   <li>DataModel.createAdminAtomic(...) 4 步事务（W1 查 invite 悲观锁 / W2 校验 invite 未用未过期 /
     *       W3 查 username 冲突 / W4 insert admin / W5 标记 invite used）</li>
     *   <li>错误映射：
     *      - InviteUnavailableException → PERMISSION_DENIED "管理员邀请码错误"
     *      - UsernameTakenException → ALREADY_EXISTS "用户名已存在"
     *      - 唯一冲突（race condition 兜底） → ALREADY_EXISTS "用户名已存在"
     *      - 其他 Exception → INTERNAL "DB添加失败"</li>
     *   <li>成功 → CreateAdminResp{Uid=uid}</li>
     * </ol>
     */
    public long createAdmin(String username, String password, String email,
                            Map<String, String> extraInfo, String inviteCode) {
        // inviteCode 非空
        String trimmedInvite = inviteCode == null ? "" : inviteCode.trim();
        if (trimmedInvite.isEmpty()) {
            throw Status.PERMISSION_DENIED.withDescription("管理员邀请码错误").asRuntimeException();
        }

        String trimmedUsername = username == null ? "" : username.trim();
        if (trimmedUsername.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("用户名不能为空").asRuntimeException();
        }

        // bcrypt password（事务外，避免延长事务持有时间）
        String passwordHash;
        try {
            passwordHash = PasswordUtil.encrypt(password);
        } catch (Exception e) {
            log.error("admin createAdmin PasswordUtil.encrypt failed", e);
            throw Status.INTERNAL.withDescription("DB添加失败").asRuntimeException();
        }

        // Snowflake uid（事务外）
        long uid;
        try {
            uid = Snowflake.nextId();
        } catch (Exception e) {
            log.error("admin createAdmin Snowflake.nextId failed", e);
            throw Status.INTERNAL.withDescription("DB添加失败").asRuntimeException();
        }

        // 4 步事务
        String optionalEmail = AdminHelpers.optionalString(email);
        Map<String, String> safeExtraInfo = (extraInfo == null || extraInfo.isEmpty())
                ? null
                : new HashMap<>(extraInfo);
        try {
            createAtomicService.createAdminAtomic(
                    trimmedUsername, trimmedInvite, passwordHash, optionalEmail, safeExtraInfo, uid);
        } catch (InviteUnavailableException e) {
            throw Status.PERMISSION_DENIED.withDescription("管理员邀请码错误").asRuntimeException();
        } catch (UsernameTakenException e) {
            throw Status.ALREADY_EXISTS.withDescription("用户名已存在").asRuntimeException();
        } catch (Exception e) {
            if (AdminExceptions.isUniqueViolation(e)) {
                throw Status.ALREADY_EXISTS.withDescription("用户名已存在").asRuntimeException();
            }
            log.error("admin createAdmin 4-step transaction failed", e);
            throw Status.INTERNAL.withDescription("DB添加失败").asRuntimeException();
        }

        log.info("admin createAdmin success, username={}", trimmedUsername);
        return uid;
    }

    /**
     * CreateAdmin 的 4 步原子事务已移到 {@link AdminCreateAtomicService}。
     *
     * <p>原先它作为本类的 {@code @Transactional} 方法、由 {@code createAdmin(...)} 同类直接调用，
     * 属于 self-invocation：Spring 的事务代理不拦截同类内部调用，
     * {@code @Transactional} <b>静默失效</b>，4 步退化成 4 条独立 auto-commit 语句。
     * 独立成 Bean 后由 {@code createAdmin} 跨 Bean 调用，代理才真正生效。
     *
     * <p>同时补上了 invite 行的 {@code SELECT ... FOR UPDATE} 悲观锁 —— 注释里写了"悲观锁"
     * 但查询语句并没有锁，并发下同一个邀请码可以被消费多次。
     */

    // ============== 6. CreateInviteCode ==============

    /**
     * 对应 Go 端 internal/logic/admin/createinvitecodelogic.go 的 CreateInviteCodeLogic.CreateInviteCode。
     *
     * <p>L2 业务语义（3 状态机）：
     * <ol>
     *   <li>findOneAdminByUid(operator_id) → NotFound / Internal</li>
     *   <li>循环重试最多 3 次：SecureRandom.nextBytes(8) → "ADM-" + HexFormat → insertAdminInvite；
     *      unique violation 继续；其他 DB 错 → Internal</li>
     *   <li>成功 → CreateInviteCodeResp{invite_code, expires_at=unix}</li>
     * </ol>
     */
    public InviteCodeResult createInviteCode(long operatorId) {
        // 校验 operator 是有效 admin
        try {
            findOneAdminByUid(operatorId);
        } catch (NotFoundException e) {
            metrics.getCreateInviteUserNotFound().increment();
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            metrics.getCreateInviteDbError().increment();
            log.error("admin createInviteCode findOneAdminByUid DB error, operator_id={}", operatorId, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(INVITE_CODE_TTL);

        for (int attempt = 0; attempt < INVITE_CODE_MAX_RETRY; attempt++) {
            String code;
            try {
                code = generateInviteCode();
            } catch (Exception e) {
                metrics.getCreateInviteInternalError().increment();
                log.error("admin createInviteCode SecureRandom failed", e);
                throw Status.INTERNAL.withDescription("管理员邀请码生成失败").asRuntimeException();
            }

            AdminInviteEntity invite = new AdminInviteEntity();
            invite.setCode(code);
            invite.setInviterUid(operatorId);
            invite.setExpiresAt(expiresAt);

            try {
                adminInviteMapper.insert(invite);
            } catch (Exception e) {
                if (AdminExceptions.isUniqueViolation(e)) {
                    // unique violation → 重试（不埋点，对应 Go 端 continue）
                    continue;
                }
                metrics.getCreateInviteDbError().increment();
                log.error("admin createInviteCode insert DB error", e);
                throw Status.INTERNAL.withDescription("DB写入失败").asRuntimeException();
            }

            metrics.getCreateInviteSuccess().increment();
            long expiresAtUnix = expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond();
            log.info("admin createInviteCode success, operator_id={}", operatorId);
            return new InviteCodeResult(code, expiresAtUnix);
        }

        // 重试超过 3 次仍 unique violation
        metrics.getCreateInviteInternalError().increment();
        throw Status.INTERNAL.withDescription("管理员邀请码生成失败").asRuntimeException();
    }

    private static String generateInviteCode() {
        byte[] buf = new byte[INVITE_CODE_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(buf);
        return INVITE_CODE_PREFIX + HexFormat.of().formatHex(buf).toUpperCase();
    }

    /** Invite code (string code + unix timestamp) —— 对应 Go 端 CreateInviteCodeResp 字段 */
    public record InviteCodeResult(String code, long expiresAt) {}

    // ============== 7. BanUser ==============

    /**
     * 对应 Go 端 internal/logic/admin/banuserlogic.go 的 BanUserLogic.BanUser。
     *
     * <p>L2 业务语义：把 user.status 改成 1（封禁）。
     */
    public boolean banUser(long uid, long operatorId) {
        int rows;
        try {
            LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(UserEntity::getUid, uid)
                    .set(UserEntity::getStatus, 1L);
            rows = userMapper.update(null, wrapper);
        } catch (Exception e) {
            metrics.getBanDbError().increment();
            log.error("admin banUser update DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }
        if (rows == 0) {
            metrics.getBanUserNotFound().increment();
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        }
        metrics.getBanSuccess().increment();
        log.info("admin banUser success, operator_id={} target_uid={}", operatorId, uid);
        return true;
    }

    // ============== 8. UnBanUser ==============

    /**
     * 对应 Go 端 internal/logic/admin/unbanuserlogic.go 的 UnBanUserLogic.UnBanUser。
     *
     * <p>L2 业务语义：把 user.status 改回 0（解封）。
     */
    public boolean unBanUser(long uid, long operatorId) {
        int rows;
        try {
            LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(UserEntity::getUid, uid)
                    .set(UserEntity::getStatus, 0L);
            rows = userMapper.update(null, wrapper);
        } catch (Exception e) {
            metrics.getUnbanDbError().increment();
            log.error("admin unBanUser update DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }
        if (rows == 0) {
            metrics.getUnbanUserNotFound().increment();
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        }
        metrics.getUnbanSuccess().increment();
        log.info("admin unBanUser success, operator_id={} target_uid={}", operatorId, uid);
        return true;
    }

    // ============== 9. GetUser ==============

    /**
     * 对应 Go 端 internal/logic/admin/getuserlogic.go 的 GetUserLogic.GetUser。
     *
     * <p>L2 业务语义（2 状态机）：findOneUserByUid → NotFound / Internal / 命中。
     *
     * <p>注意：admin-rpc UserInfo 比 user-rpc UserInfo 多一个 score 字段。
     * Go 端 getuserlogic.go 的 pb.UserInfo 构造没有设置 score 字段（proto3 默认 0），1:1 保留：
     * Java 端不显式 setScore（默认 0）。
     */
    public UserEntity getUser(long uid) {
        UserEntity user;
        try {
            user = findOneUserByUid(uid);
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            log.error("admin getUser DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }
        return user;
    }

    // ============== 10. GetUserList ==============

    /**
     * 对应 Go 端 internal/logic/admin/getuserlistlogic.go 的 GetUserListLogic.GetUserList。
     *
     * <p>L2 业务语义（2 状态机）：
     * <ol>
     *   <li>findUserListByKeyword(page, pageSize, keyword) → (users, total)</li>
     *   <li>逐行 User → pb.UserInfo 转换（不设 score）</li>
     * </ol>
     *
     * <p>keyword 非空时按 username/email LIKE 查询，否则全量。
     */
    public UserListResult getUserList(int page, int pageSize, String keyword) {
        if (page <= 0) page = 1;
        if (pageSize <= 0) pageSize = 20;

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        String pattern = hasKeyword ? "%" + keyword.trim() + "%" : null;

        // count
        long total;
        try {
            LambdaQueryWrapper<UserEntity> countWrapper = new LambdaQueryWrapper<>();
            if (hasKeyword) {
                countWrapper.and(w -> w.like(UserEntity::getUsername, pattern)
                        .or().like(UserEntity::getEmail, pattern));
            }
            Long totalLong = userMapper.selectCount(countWrapper);
            total = totalLong == null ? 0L : totalLong;
        } catch (Exception e) {
            log.error("admin getUserList count DB error", e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        // list
        List<UserEntity> rows;
        try {
            LambdaQueryWrapper<UserEntity> listWrapper = new LambdaQueryWrapper<>();
            if (hasKeyword) {
                listWrapper.and(w -> w.like(UserEntity::getUsername, pattern)
                        .or().like(UserEntity::getEmail, pattern));
            }
            listWrapper.orderByDesc(UserEntity::getId);
            listWrapper.last("LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize));
            rows = userMapper.selectList(listWrapper);
            if (rows == null) rows = Collections.emptyList();
        } catch (Exception e) {
            log.error("admin getUserList DB error", e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }
        return new UserListResult(rows, total);
    }

    /** GetUserList 返回值 record —— 对应 Go 端 ([]*User, int64) 二元组 */
    public record UserListResult(List<UserEntity> users, long total) {}

    // ============== 11. UpdateUser ==============

    /**
     * 对应 Go 端 internal/logic/admin/updateuserlogic.go 的 UpdateUserLogic.UpdateUser。
     *
     * <p>L2 业务语义（5 状态机）：
     * <ol>
     *   <li>findOneUserByUid → NotFound / Internal</li>
     *   <li>if username 非空 → 查 username 冲突（existUser.Uid != in.Uid → AlreadyExists）</li>
     *   <li>构造 toUpdate（跳零值；User.Email 非 null，用普通赋值）</li>
     *   <li>UpdateUserByUid（LambdaUpdateWrapper 动态 set）</li>
     *   <li>重读 user</li>
     * </ol>
     */
    public UserEntity updateUser(long uid, long operatorId,
                                 String username, String password, String email,
                                 Map<String, String> extraInfo) {
        // W2 查 user
        UserEntity target;
        try {
            target = findOneUserByUid(uid);
        } catch (NotFoundException e) {
            metrics.getUpdateUserUserNotFound().increment();
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            metrics.getUpdateUserDbError().increment();
            log.error("admin updateUser findOneUserByUid DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        // W3 username 冲突检查
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserEntity::getUid, uid);
        if (username != null && !username.isEmpty()) {
            UserEntity existing;
            try {
                existing = findOneUserByUsername(username);
            } catch (NotFoundException e) {
                existing = null;
            } catch (Exception e) {
                metrics.getUpdateUserDbError().increment();
                log.error("admin updateUser findOneUserByUsername DB error", e);
                throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
            }
            // D-004 1:1 保留：不排除自己
            if (existing != null && !existing.getUid().equals(uid)) {
                throw Status.ALREADY_EXISTS.withDescription("用户名已存在").asRuntimeException();
            }
            wrapper.set(UserEntity::getUsername, username);
        }

        // W4 bcrypt password
        if (password != null && !password.isEmpty()) {
            try {
                wrapper.set(UserEntity::getPassword, PasswordUtil.encrypt(password));
            } catch (Exception e) {
                metrics.getUpdateUserInternalError().increment();
                log.error("admin updateUser PasswordUtil.encrypt failed", e);
                throw Status.INTERNAL.withDescription("密码加密失败").asRuntimeException();
            }
        }
        // email 非 null（User 表定义）：不进 null，仅 trim
        if (email != null && !email.isEmpty()) {
            wrapper.set(UserEntity::getEmail, email);
        }
        if (extraInfo != null && !extraInfo.isEmpty()) {
            // 同 updateSelf：wrapper.set() 走 Object → 被当成 hstore，必须用 setSql + CAST
            wrapper.setSql("extra_info = CAST({0} AS jsonb)", toJson(extraInfo));
        }

        // W5 update
        try {
            userMapper.update(null, wrapper);
        } catch (Exception e) {
            metrics.getUpdateUserDbError().increment();
            log.error("admin updateUser update DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }

        // W6 重读
        UserEntity newUser;
        try {
            newUser = findOneUserByUid(uid);
        } catch (Exception e) {
            metrics.getUpdateUserDbError().increment();
            log.error("admin updateUser re-findOneUserByUid DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }
        metrics.getUpdateUserSuccess().increment();
        log.info("admin updateUser success, operator_id={} target_uid={}", operatorId, uid);
        return newUser;
    }

    // ============== 12. DeleteUser ==============

    /**
     * 对应 Go 端 internal/logic/admin/deleteuserlogic.go 的 DeleteUserLogic.DeleteUser。
     *
     * <p>L2 业务语义：deleteUserByUid → RowsAffected=0 抛 NotFound / 其它 DB 错抛 Internal / 成功 → resp{Success=true}。
     */
    public boolean deleteUser(long uid, long operatorId) {
        int rows;
        try {
            rows = userMapper.delete(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUid, uid));
        } catch (Exception e) {
            metrics.getDeleteUserDbError().increment();
            log.error("admin deleteUser DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }
        if (rows == 0) {
            metrics.getDeleteUserUserNotFound().increment();
            throw Status.NOT_FOUND.withDescription(String.format("用户%d不存在", uid)).asRuntimeException();
        }
        metrics.getDeleteUserSuccess().increment();
        log.info("admin deleteUser success, operator_id={} target_uid={}", operatorId, uid);
        return true;
    }

    // ============== 13. ResetUserPassword ==============

    /**
     * 对应 Go 端 internal/logic/admin/resetuserpasswordlogic.go 的 ResetUserPasswordLogic.ResetUserPassword。
     *
     * <p>L2 业务语义（5 状态机）：
     * <ol>
     *   <li>findOneUserByUid → NotFound / Internal</li>
     *   <li>bcrypt DefaultPassword → Internal</li>
     *   <li>updateUserPasswordByUid（LambdaUpdateWrapper set password）</li>
     *   <li>成功 → ResetUserPasswordResp{Success=true}</li>
     * </ol>
     *
     * <p>Go 端 UpdateUserPasswordByUid 不检查 RowsAffected（不会抛 NotFound），
     * 所以 ResetUserPassword 即使 user 在 W2 和 W4 之间被并发删了，仍会返 success。这是 Go 端的语义，1:1 保留。
     */
    public boolean resetUserPassword(long uid, long operatorId) {
        // W2 查 user
        try {
            findOneUserByUid(uid);
        } catch (NotFoundException e) {
            metrics.getResetPasswordUserNotFound().increment();
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            metrics.getResetPasswordDbError().increment();
            log.error("admin resetUserPassword findOneUserByUid DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        // W3 bcrypt DefaultPassword
        String defaultPassword = properties.getSystem().getDefaultPassword();
        if (defaultPassword == null || defaultPassword.isEmpty()) {
            metrics.getResetPasswordInternalError().increment();
            throw Status.INTERNAL.withDescription("密码加密失败").asRuntimeException();
        }
        String hash;
        try {
            hash = PasswordUtil.encrypt(defaultPassword);
        } catch (Exception e) {
            metrics.getResetPasswordInternalError().increment();
            log.error("admin resetUserPassword PasswordUtil.encrypt failed", e);
            throw Status.INTERNAL.withDescription("密码加密失败").asRuntimeException();
        }

        // W4 update（不检查 RowsAffected，1:1 跟随 Go 端语义）
        try {
            LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(UserEntity::getUid, uid).set(UserEntity::getPassword, hash);
            userMapper.update(null, wrapper);
        } catch (Exception e) {
            metrics.getResetPasswordDbError().increment();
            log.error("admin resetUserPassword update DB error, uid={}", uid, e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }

        metrics.getResetPasswordSuccess().increment();
        log.info("admin resetUserPassword success, operator_id={} target_uid={}", operatorId, uid);
        return true;
    }

    // ============== 内部 helper：单表查询（给 service 13 个方法共享）==============

    /**
     * 对应 Go 端 AdminModel.findOneAdminByUid。
     * 找不到 → 抛 NotFoundException。
     */
    private AdminEntity findOneAdminByUid(long uid) {
        AdminEntity admin = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUid, uid)
        );
        if (admin == null) {
            throw new NotFoundException("admin not found by uid: " + uid);
        }
        return admin;
    }

    /**
     * 对应 Go 端 AdminModel.findOneAdminByUsername。
     * 找不到 → 抛 NotFoundException。
     */
    private AdminEntity findOneAdminByUsername(String username) {
        AdminEntity admin = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUsername, username)
        );
        if (admin == null) {
            throw new NotFoundException("admin not found by username: " + username);
        }
        return admin;
    }

    /**
     * 对应 Go 端 AdminModel.findOneUserByUid。
     */
    private UserEntity findOneUserByUid(long uid) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUid, uid)
        );
        if (user == null) {
            throw new NotFoundException("user not found by uid: " + uid);
        }
        return user;
    }

    /**
     * 对应 Go 端 AdminModel.findOneUserByUsername。
     */
    private UserEntity findOneUserByUsername(String username) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username)
        );
        if (user == null) {
            throw new NotFoundException("user not found by username: " + username);
        }
        return user;
    }

    /**
     * Map → JSON 文本，供 {@code wrapper.setSql("extra_info = CAST({0} AS jsonb)", ...)} 使用。
     * 序列化失败按内部错误处理——extraInfo 是 Map<String,String>，正常不会失败。
     */
    private static String toJson(Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            log.error("admin extraInfo JSON serialize failed", e);
            throw Status.INTERNAL.withDescription("JSON序列化失败").asRuntimeException();
        }
    }
}
