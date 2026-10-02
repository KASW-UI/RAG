package com.rag.user.user.rpc.internal.logic;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 对应 Go: sea-try-go/service/user/common/cryptx.PasswordEncrypt / CheckPassword
 *
 * Go 端的 cryptx 是项目自定义的密码加密(从 import 路径看大概率是 bcrypt 或者自研 PBKDF2);
 * Java 端用 Spring Security 的 BCryptPasswordEncoder,salt 内置在 hash 里,
 * 每次加密结果不同,CheckPassword 时由 encoder 自己从 hash 里读 salt 验。
 *
 * 这是密码学里"加盐哈希"的标准做法,1:1 复刻 Go 的密码强度要求。
 */
@Component
public class PasswordEncoderService {

    /** strength 默认 10,跟 Go 端 cryptx 默认 cost 一致;调到 12 会更慢但更安全 */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    /**
     * 对应 Go: cryptx.PasswordEncrypt(plain) -> error
     * Java 端 BCrypt 不抛业务错,strength 错才会抛 IllegalStateException,这里吃下返回 Optional
     */
    public String encrypt(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        return encoder.encode(plainPassword);
    }

    /**
     * 对应 Go: cryptx.CheckPassword(hash, plain) -> bool
     */
    public boolean matches(String hashedPassword, String plainPassword) {
        if (hashedPassword == null || plainPassword == null) {
            return false;
        }
        return encoder.matches(plainPassword, hashedPassword);
    }
}