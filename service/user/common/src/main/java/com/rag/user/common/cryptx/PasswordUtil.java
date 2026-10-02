package com.rag.user.common.cryptx;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordUtil {
    private static final int STRENGTH = 10;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(STRENGTH);

    private PasswordUtil() {}

    public static String encrypt(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean check(String rawPassword, String dbHash) {
        return ENCODER.matches(rawPassword, dbHash);
    }
}