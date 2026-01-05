package com.krusty.crab.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.krusty.crab.security.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilTest {

    @Test
    void generateToken_extractsClaims_andValidates() {
        JwtUtil jwtUtil = buildJwtUtil(3_600_000L);

        String token = jwtUtil.generateToken("user@example.com", UserType.CLIENT, 12, "ROLE_USER");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user@example.com");
        assertThat(jwtUtil.extractUserType(token)).isEqualTo(UserType.CLIENT);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(12);
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_USER");
        assertThat(jwtUtil.validateToken(token, "user@example.com")).isTrue();
        assertThat(jwtUtil.validateToken(token)).isTrue();

        String tokenWithoutRole = jwtUtil.generateToken("staff", UserType.EMPLOYEE, 7, null);
        assertThat(jwtUtil.extractRole(tokenWithoutRole)).isNull();
    }

    @Test
    void validateToken_returnsFalseForExpiredOrInvalidTokens() {
        JwtUtil jwtUtil = buildJwtUtil(-1_000L);

        String expiredToken = jwtUtil.generateToken("expired", UserType.CLIENT, 1, null);
        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();

        assertThat(jwtUtil.validateToken("not.a.jwt")).isFalse();
    }

    private JwtUtil buildJwtUtil(long expirationMillis) {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", expirationMillis);
        return jwtUtil;
    }
}
