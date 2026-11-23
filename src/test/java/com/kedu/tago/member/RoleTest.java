package com.kedu.tago.member;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.common.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleTest {

    @Test
    @DisplayName("Role.from: 이름(대소문자·공백 무시)을 읽고, 없거나 모르는 값은 USER")
    void from() {
        assertEquals(Role.ADMIN, Role.from("ADMIN"));
        assertEquals(Role.ADMIN, Role.from(" admin "));
        assertEquals(Role.USER, Role.from("USER"));
        assertEquals(Role.USER, Role.from(null));
        assertEquals(Role.USER, Role.from(""));
        assertEquals(Role.USER, Role.from("guest"));
        assertEquals(Role.USER, Role.from("SUPERUSER"));
    }

    @Test
    @DisplayName("satisfies: ADMIN 은 모든 권한을 충족, USER 는 USER 만")
    void satisfies() {
        assertTrue(Role.ADMIN.satisfies(Role.ADMIN));
        assertTrue(Role.ADMIN.satisfies(Role.USER));
        assertTrue(Role.USER.satisfies(Role.USER));
        assertFalse(Role.USER.satisfies(Role.ADMIN));
    }

    @Test
    @DisplayName("JWT: 로그인 시 권한이 role 클레임에 담기고, 읽을 때 같은 값이 나온다")
    void jwtCarriesRole() {
        JwtUtil jwt = new JwtUtil(3_600_000L, "test-secret-test-secret-test-secret-0123");

        DecodedJWT adminToken = jwt.verifyToken(jwt.createToken("a", Role.ADMIN));
        DecodedJWT userToken = jwt.verifyToken(jwt.createToken("u", Role.USER));

        assertEquals("ADMIN", adminToken.getClaim("role").asString());
        assertEquals(Role.ADMIN, JwtUtil.roleOf(adminToken));
        assertEquals(Role.USER, JwtUtil.roleOf(userToken));
        assertEquals("a", adminToken.getSubject());
    }

    @Test
    @DisplayName("role 클레임이 없는 토큰은 USER")
    void tokenWithoutRoleIsUser() {
        DecodedJWT noRole = JWT.decode(JWT.create().withSubject("x").sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("s")));
        assertEquals(Role.USER, JwtUtil.roleOf(noRole));
    }
}
