package com.kedu.tago.common.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.member.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 요청의 인증/인가를 한 곳에서 처리한다. 공개 경로와 경로 기준 권한은 AccessPolicy 가 정한다.
 *
 * <ol>
 *   <li>공개 요청은 통과</li>
 *   <li>그 밖에는 Authorization: Bearer 토큰 검증 (없으면 2000, 잘못되면 2001)</li>
 *   <li>필요 권한 확인: 경로 규칙(/admin/** = ADMIN)과 컨트롤러의 @RequireRole 중 더 높은 쪽.
 *       부족하면 관리자 전용은 2004, 그 밖은 2003</li>
 *   <li>검증된 아이디와 권한을 request 속성 "id", "role" 로 넘긴다 (컨트롤러에서 사용)</li>
 * </ol>
 *
 * 토큰에 담긴 권한은 로그인 시점의 값이다. 권한을 바꾸면 이미 발급된 토큰이 만료된 뒤(기본 1시간) 적용된다.
 * 실패는 ApiException 으로 던지고, 응답 형식은 GlobalExceptionHandler 가 만든다.
 * 리소스 소유자 검사(남의 예약 결제/취소 등)는 해당 서비스에서 한다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AccessPolicy accessPolicy;

    public AuthInterceptor(JwtUtil jwtUtil, AccessPolicy accessPolicy) {
        this.jwtUtil = jwtUtil;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();

        // CORS Preflight 요청(OPTIONS) 통과
        if (method.equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (accessPolicy.isPublic(method, path)) {
            return true;
        }

        DecodedJWT token = verify(request);
        Role role = JwtUtil.roleOf(token);
        request.setAttribute("id", token.getSubject());
        request.setAttribute("role", role);

        Role required = requiredRole(path, handler);
        if (!role.satisfies(required)) {
            throw new ApiException(required == Role.ADMIN ? ErrorCode.ADMIN_ONLY : ErrorCode.FORBIDDEN);
        }
        return true;
    }

    private DecodedJWT verify(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(ErrorCode.LOGIN_REQUIRED);
        }
        try {
            return jwtUtil.verifyToken(authHeader.substring(7));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
    }

    /** 경로 규칙과 @RequireRole(메서드 > 클래스) 중 더 높은 권한 */
    private Role requiredRole(String path, Object handler) {
        Role byPath = accessPolicy.requiredRole(path);
        Role byAnnotation = annotatedRole(handler);
        if (byPath == Role.ADMIN || byAnnotation == Role.ADMIN) {
            return Role.ADMIN;
        }
        return Role.USER;
    }

    private Role annotatedRole(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequireRole annotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireRole.class);
        }
        return annotation != null ? annotation.value() : null;
    }
}
