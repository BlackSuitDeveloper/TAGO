package com.kedu.tago.common.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.member.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** JWT 인증 및 권한 검사 인터셉터 (검증된 id, role 을 request 속성으로 전달) */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AccessPolicy accessPolicy;
    private final TokenManagementService tokenManagementService;

    /** 공개 경로 통과, 그 외 토큰 검증 후 필요 권한 확인 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();

        // CORS Preflight 통과
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

        String rawToken = authHeader.substring(7).trim();

        // 1. 블랙리스트 검사 (로그아웃된 토큰 차단)
        if (tokenManagementService.isBlacklisted(rawToken)) {
            throw new ApiException(ErrorCode.INVALID_TOKEN, "로그아웃된 토큰입니다. 다시 로그인해 주세요.");
        }

        DecodedJWT decoded;
        try {
            decoded = jwtUtil.verifyToken(rawToken);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }

        // 2. 비밀번호 변경 / 회원탈퇴 등으로 인한 이전 토큰 무효화 검사
        if (tokenManagementService.isTokenInvalidated(decoded.getSubject(), decoded.getIssuedAt())) {
            throw new ApiException(ErrorCode.INVALID_TOKEN, "비밀번호가 변경되었거나 탈퇴된 계정입니다. 다시 로그인해 주세요.");
        }

        return decoded;
    }

    // 경로 규칙과 @RequireRole(메서드 > 클래스) 중 더 높은 권한
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
