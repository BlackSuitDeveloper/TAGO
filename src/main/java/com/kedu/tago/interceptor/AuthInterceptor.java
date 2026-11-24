package com.kedu.tago.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.utils.JwtUtil;
import com.kedu.tago.config.AccessPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 요청의 인증/인가를 한 곳에서 처리한다. 어떤 요청이 공개인지, 관리자 전용인지는 AccessPolicy 가 정한다.
 *
 * <ol>
 *   <li>공개 요청은 통과</li>
 *   <li>그 밖에는 Authorization: Bearer 토큰 검증 (없으면 2000, 잘못되면 2001)</li>
 *   <li>/admin/** 은 관리자만 (아니면 2004)</li>
 *   <li>검증된 아이디를 request 속성 "id" 로 넘긴다 (컨트롤러에서 사용)</li>
 * </ol>
 *
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

        String memberId = authenticate(request);
        request.setAttribute("id", memberId);

        if (accessPolicy.isAdminOnly(path) && !accessPolicy.isAdmin(memberId)) {
            throw new ApiException(ErrorCode.ADMIN_ONLY);
        }
        return true;
    }

    private String authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(ErrorCode.LOGIN_REQUIRED);
        }
        try {
            DecodedJWT token = jwtUtil.verifyToken(authHeader.substring(7));
            return token.getSubject();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
    }
}
