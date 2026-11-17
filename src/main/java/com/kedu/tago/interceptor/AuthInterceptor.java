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

        if (request.getMethod().equalsIgnoreCase("options")) {
            return true;
        }

        if (uri.equals("/user") && method.equalsIgnoreCase("POST")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer")) {
            String token = authHeader.substring(7);
            DecodedJWT dToken = jwtUtil.verifyToken(token);
            String id = dToken.getSubject();
            request.setAttribute("id", id);

            return true;
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("undefined access");

            return false;
        }
    }
}
