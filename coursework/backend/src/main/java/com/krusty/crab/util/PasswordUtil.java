package com.krusty.crab.util;

import com.krusty.crab.exception.PasswordException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordUtil {
    
    private static final PasswordEncoder encoder = new BCryptPasswordEncoder();
    
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new PasswordException("Password cannot be null or empty");
        }
        try {
            return encoder.encode(rawPassword);
        } catch (Exception e) {
            throw new PasswordException("Failed to encode password", e);
        }
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, encodedPassword);
        } catch (Exception e) {
            throw new PasswordException("Failed to verify password", e);
        }
    }
}

