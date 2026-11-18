package com.kedu.tago.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.common.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 1. CORS Preflight 요청(OPTIONS) 통과
        if (method.equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        // 2. 회원가입(POST /member)은 토큰 없이 통과
        String servletPath = request.getServletPath();
        if ((servletPath.equals("/member") || uri.endsWith("/member")) && method.equalsIgnoreCase("POST")) {
            return true;
        }

        // 3. Authorization 헤더 검증
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                DecodedJWT dToken = jwtUtil.verifyToken(token);
                String id = dToken.getSubject();
                request.setAttribute("id", id); // 컨트롤러에서 사용할 수 있도록 id 세팅

                return true;
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"유효하지 않거나 만료된 토큰입니다.\"}");
                return false;
            }
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");
            return false;
        }
    }
}
