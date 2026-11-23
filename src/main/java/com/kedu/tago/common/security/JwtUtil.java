package com.kedu.tago.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.kedu.tago.member.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/** JWT 발급·검증 유틸 */
@Component
public class JwtUtil {

    private Long expiration;
    private String secret;

    private JWTVerifier jwt;
    private Algorithm algo;

    public JwtUtil(
            @Value("${jwt.expiration}") Long expiration,
            @Value("${jwt.secret}") String secret) {
        this.expiration = expiration;
        this.secret = secret;

        this.algo = Algorithm.HMAC256(secret);
        this.jwt = JWT.require(algo).build();
    }

    /** JWT 발급 */
    public String createToken(String id, Role role) {
        return JWT
                .create()
                .withSubject(id)
                .withClaim("role", role.name())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expiration))
                .sign(this.algo);
    }

    /** 토큰의 권한 (없거나 모르는 값이면 USER) */
    public static Role roleOf(DecodedJWT token) {
        return Role.from(token.getClaim("role").asString());
    }

    /** JWT 검증 */
    public DecodedJWT verifyToken(String token) {
        return jwt.verify(token);
    }
}
